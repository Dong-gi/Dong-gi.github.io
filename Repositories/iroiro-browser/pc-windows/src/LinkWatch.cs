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
    /// tun2proxy 의 'Beginning #n TCP 10.0.0.33:52298 -> 192.168.49.1:1080' 줄(0.8.7 — lib.rs 의 log::info!("Beginning {session_info}"),
    /// SessionInfo 의 꼴 '#{id} {protocol} {src} -> {dst}')에서 목적지가 프록시(폰) 주소인 세션을 찾는다. 줄 앞의 env_logger 머리는 보지 않는다.
    /// 포트는 보지 않는다 — TUN 으로 폰 주소에 가는 세션은 어느 포트든(1080 의 고리, UDP 묶음의 포트, 시스템 프록시의 8282) on-link 경로가
    /// 빠졌다는 뜻이다(그 경로가 있으면 /24 가 TUN 의 /0 을 이긴다).
    /// </summary>
    public static class LoopGuard
    {
        const string Head = "Beginning #";

        /// <summary>프록시 주소로 가는 세션이면 '프로토콜 목적지'(로그에 적을 것), 아니면 null. 프록시 주소가 IP 글자가 아니면 늘 null.</summary>
        public static string Match(string line, string proxyHost)
        {
            if (string.IsNullOrEmpty(line) || string.IsNullOrEmpty(proxyHost)) return null;
            int i = line.IndexOf(Head, StringComparison.Ordinal);
            if (i < 0) return null;
            var parts = line.Substring(i + Head.Length).Trim().Split(' ');
            if (parts.Length < 5 || parts[3] != "->") return null;
            uint id;
            if (!uint.TryParse(parts[0], NumberStyles.None, CultureInfo.InvariantCulture, out id)) return null;
            if (parts[1] != "TCP" && parts[1] != "UDP") return null;
            IPAddress proxy, dst;
            if (!IPAddress.TryParse(proxyHost.Trim().Trim('[', ']'), out proxy)) return null;
            if (!TryHost(parts[4], out dst)) return null;
            if (dst.IsIPv4MappedToIPv6) dst = dst.MapToIPv4();
            if (proxy.IsIPv4MappedToIPv6) proxy = proxy.MapToIPv4();
            return dst.Equals(proxy) ? parts[1] + " " + parts[4] : null;
        }

        /// <summary>'a.b.c.d:port' 또는 '[v6]:port' 의 주소.</summary>
        static bool TryHost(string endpoint, out IPAddress ip)
        {
            ip = null;
            string host;
            if (endpoint.StartsWith("[", StringComparison.Ordinal))
            {
                int close = endpoint.IndexOf(']');
                if (close < 0) return false;
                host = endpoint.Substring(1, close - 1);
            }
            else
            {
                int colon = endpoint.LastIndexOf(':');
                if (colon <= 0) return false;
                host = endpoint.Substring(0, colon);
            }
            return IPAddress.TryParse(host, out ip);
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
