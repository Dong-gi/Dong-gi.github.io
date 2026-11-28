// 폰으로 가는 길이 끊기는 것을 빨리 알아채기 — 경로 고리 감지(tun2proxy 의 출력)와 Wi-Fi 끊김 알림(Native Wifi API).
//
// Wi-Fi 링크가 내려가면 Wi-Fi 의 on-link 경로(192.168.49.0/24)를 쓸 수 없어 폰(192.168.49.1)으로 가는 길이 TUN 의 기본 경로가 된다.
// tun2proxy 0.8.7 은 바깥 연결을 인터페이스에 묶지 않으므로(TcpStream::connect 그대로) 제 폰 연결이 제 TUN 으로 돌아와 새 세션이 되고,
// 그 세션이 또 폰으로 연결을 열어 불어난다(11-28 사용자 기기 — 12:15:16·12:16:25 의 Wi-Fi 끊김과 시각이 맞았다).
using System;
using System.Globalization;
using System.Net;
using System.Runtime.InteropServices;

namespace WebTether
{
    /// <summary>
    /// tun2proxy 의 'Beginning' 줄에서 목적지가 프록시(폰) 주소인 세션을 찾는다. 줄 꼴은 0.8.7 의 것 그대로만 받는다 —
    /// env_logger 0.11 의 기본 머리 '[<UTC 초까지> INFO  tun2proxy] '(때·5칸으로 채운 수준·대상, 대상은 lib.rs 의 모듈 경로) 바로 뒤에
    /// 'Beginning #<번호> <TCP|UDP> <출발> -> <목적지>'(lib.rs 의 log::info!("Beginning {session_info}"), SessionInfo 의 꼴 '#{id} {protocol} {src} -> {dst}')
    /// 이고 그 뒤에 아무것도 없는 줄. 주소는 점 넷의 십진 IPv4(칸마다 0–255, 앞의 0 없음)와 대괄호 IPv6 만, 포트는 1–65535(앞의 0 없음).
    /// 포트는 견주지 않는다 — TUN 으로 폰 주소에 가는 세션은 어느 포트든(1080 의 고리, UDP 묶음의 포트, 시스템 프록시의 8282) on-link
    /// 경로가 빠졌다는 뜻이다(그 경로가 있으면 /24 가 TUN 의 /0 을 이긴다).
    /// </summary>
    public static class LoopGuard
    {
        const string Tail = " INFO  tun2proxy] Beginning #";

        /// <summary>프록시 주소로 가는 세션이면 '프로토콜 목적지'(로그에 적을 것), 아니면 null. 프록시 주소가 IP 글자가 아니면 늘 null.</summary>
        public static string Match(string line, string proxyHost)
        {
            if (string.IsNullOrEmpty(line) || string.IsNullOrEmpty(proxyHost)) return null;
            if (line[0] != '[') return null;
            int t = line.IndexOf(Tail, StringComparison.Ordinal);
            if (t < 0 || !IsTimestamp(line.Substring(1, t - 1))) return null;
            var parts = line.Substring(t + Tail.Length).Split(' ');
            if (parts.Length != 5 || parts[3] != "->") return null;
            if (!IsDecimal(parts[0], 20)) return null;
            if (parts[1] != "TCP" && parts[1] != "UDP") return null;
            IPAddress src, dst, proxy;
            if (!TryEndpoint(parts[2], out src) || !TryEndpoint(parts[4], out dst)) return null;
            if (!TryAddress(proxyHost.Trim(), out proxy)) return null;
            return dst.Equals(proxy) ? parts[1] + " " + parts[4] : null;
        }

        /// <summary>env_logger 의 humantime 초 단위 때 — '2026-11-28T03:15:16Z'.</summary>
        static bool IsTimestamp(string s)
        {
            const string Shape = "dddd-dd-ddTdd:dd:ddZ";
            if (s.Length != Shape.Length) return false;
            for (int i = 0; i < s.Length; i++)
            {
                if (Shape[i] == 'd') { if (s[i] < '0' || s[i] > '9') return false; }
                else if (s[i] != Shape[i]) return false;
            }
            return true;
        }

        static bool IsDecimal(string s, int maxLen)
        {
            if (s.Length == 0 || s.Length > maxLen) return false;
            foreach (var ch in s) if (ch < '0' || ch > '9') return false;
            return true;
        }

        /// <summary>'a.b.c.d:port' 또는 '[v6]:port'.</summary>
        static bool TryEndpoint(string endpoint, out IPAddress ip)
        {
            ip = null;
            string host, port;
            if (endpoint.StartsWith("[", StringComparison.Ordinal))
            {
                int close = endpoint.IndexOf("]:", StringComparison.Ordinal);
                if (close < 0) return false;
                host = endpoint.Substring(1, close - 1);
                port = endpoint.Substring(close + 2);
                if (!TryV6(host, out ip)) return false;
            }
            else
            {
                int colon = endpoint.LastIndexOf(':');
                if (colon <= 0) return false;
                host = endpoint.Substring(0, colon);
                port = endpoint.Substring(colon + 1);
                if (!TryV4(host, out ip)) return false;
            }
            int n;
            return IsDecimal(port, 5) && port[0] != '0' && int.TryParse(port, NumberStyles.None, CultureInfo.InvariantCulture, out n) && n <= 65535;
        }

        /// <summary>프록시 주소 — 점 넷의 십진 IPv4 또는(대괄호가 있든 없든) IPv6.</summary>
        static bool TryAddress(string s, out IPAddress ip)
        {
            if (TryV4(s, out ip)) return true;
            if (s.StartsWith("[", StringComparison.Ordinal) && s.EndsWith("]", StringComparison.Ordinal)) s = s.Substring(1, s.Length - 2);
            return TryV6(s, out ip);
        }

        /// <summary>점 넷의 십진 IPv4 만 — 칸마다 0–255, 앞의 0 없음(IPAddress.TryParse 는 '3232248065'·'0xC0.0xA8.0x31.0x01'·'192.168.049.1' 도 받는다).</summary>
        public static bool TryV4(string s, out IPAddress ip)
        {
            ip = null;
            var parts = s.Split('.');
            if (parts.Length != 4) return false;
            var b = new byte[4];
            for (int i = 0; i < 4; i++)
            {
                var p = parts[i];
                if (!IsDecimal(p, 3) || (p.Length > 1 && p[0] == '0')) return false;
                int v = int.Parse(p, NumberStyles.None, CultureInfo.InvariantCulture);
                if (v > 255) return false;
                b[i] = (byte)v;
            }
            ip = new IPAddress(b);
            return true;
        }

        /// <summary>IPv6 — 16진 숫자·콜론·(끝에 IPv4 를 실은 꼴의) 점만, 범위(%)는 받지 않는다.</summary>
        static bool TryV6(string s, out IPAddress ip)
        {
            ip = null;
            if (s.Length < 2 || s.IndexOf(':') < 0) return false;
            foreach (var ch in s)
            {
                bool ok = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F') || ch == ':' || ch == '.';
                if (!ok) return false;
            }
            IPAddress a;
            if (!IPAddress.TryParse(s, out a) || a.AddressFamily != System.Net.Sockets.AddressFamily.InterNetworkV6) return false;
            ip = a;
            return true;
        }
    }

    /// <summary>
    /// Wi-Fi 연결이 끊기는 알림 — WlanRegisterNotification(ACM·MSM). 받는 것은 읽기만 하는 알림이다(아무것도 바꾸지 않는다).
    /// 값은 Windows SDK 10.0.26100 의 l2cmn.h·wlanapi.h 에서 확인했다.
    /// </summary>
    public static class WlanEvents
    {
        public const int SourceAcm = 0x08;   // L2_NOTIFICATION_SOURCE_WLAN_ACM
        public const int SourceMsm = 0x10;   // L2_NOTIFICATION_SOURCE_WLAN_MSM

        /// <summary>링크가 내려가는(내려간) 알림이면 그 글, 아니면 null(순수 — 시험한다).</summary>
        public static string GoingDown(int source, int code)
        {
            if (source == SourceAcm)
            {
                if (code == 20) return "Wi-Fi 연결이 끊기는 중";          // wlan_notification_acm_disconnecting
                if (code == 21) return "Wi-Fi 연결이 끊김";               // wlan_notification_acm_disconnected
                if (code == 14) return "Wi-Fi 어댑터가 사라짐";           // wlan_notification_acm_interface_removal
            }
            else if (source == SourceMsm)
            {
                if (code == 9) return "Wi-Fi 연결을 푸는 중";             // wlan_notification_msm_disassociating
                if (code == 10) return "Wi-Fi 연결이 끊김";               // wlan_notification_msm_disconnected
                if (code == 13) return "Wi-Fi 어댑터가 사라짐";           // wlan_notification_msm_adapter_removal
            }
            return null;
        }

        /// <summary>알림을 받기 시작한다 — onDown(어댑터 GUID, 글)은 Native Wifi 의 스레드에서 불린다(받는 쪽이 UI 로 넘긴다). 못 하면 던진다.</summary>
        public static IDisposable Watch(Action<Guid, string> onDown)
        {
            return new Watcher(onDown);
        }

        sealed class Watcher : IDisposable
        {
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate void Callback(IntPtr data, IntPtr context);

            [DllImport("wlanapi.dll")]
            static extern uint WlanOpenHandle(uint clientVersion, IntPtr reserved, out uint negotiatedVersion, out IntPtr handle);
            [DllImport("wlanapi.dll")]
            static extern uint WlanCloseHandle(IntPtr handle, IntPtr reserved);
            [DllImport("wlanapi.dll")]
            static extern uint WlanRegisterNotification(IntPtr handle, uint source, bool ignoreDuplicate, Callback callback,
                                                        IntPtr context, IntPtr reserved, IntPtr previousSource);

            readonly Action<Guid, string> onDown;
            readonly Callback callback; // 붙잡아 둔다 — 놓치면 GC 가 거둔 대리자를 네이티브가 부른다
            IntPtr handle;

            public Watcher(Action<Guid, string> onDown)
            {
                this.onDown = onDown;
                callback = OnNotification;
                uint ver;
                uint rc = WlanOpenHandle(2, IntPtr.Zero, out ver, out handle);
                if (rc != 0) throw new InvalidOperationException("WlanOpenHandle " + rc);
                rc = WlanRegisterNotification(handle, (uint)(SourceAcm | SourceMsm), true, callback, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
                if (rc != 0)
                {
                    WlanCloseHandle(handle, IntPtr.Zero);
                    handle = IntPtr.Zero;
                    throw new InvalidOperationException("WlanRegisterNotification " + rc);
                }
            }

            void OnNotification(IntPtr data, IntPtr context)
            {
                // 네이티브 스레드 — 무엇도 밖으로 던지지 않는다(던지면 프로세스가 죽는다).
                try
                {
                    if (data == IntPtr.Zero) return;
                    int source = Marshal.ReadInt32(data, 0);
                    int code = Marshal.ReadInt32(data, 4);
                    var what = GoingDown(source, code);
                    if (what == null) return;
                    var guid = (Guid)Marshal.PtrToStructure(new IntPtr(data.ToInt64() + 8), typeof(Guid));
                    onDown(guid, what);
                }
                catch (Exception) { }
            }

            public void Dispose()
            {
                if (handle == IntPtr.Zero) return;
                WlanRegisterNotification(handle, 0, true, null, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
                WlanCloseHandle(handle, IntPtr.Zero);
                handle = IntPtr.Zero;
            }
        }
    }
}
