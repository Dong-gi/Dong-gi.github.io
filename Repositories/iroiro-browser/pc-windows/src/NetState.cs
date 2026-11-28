// 읽기만 하는 망 살피기 — 핫스팟 어댑터와 그 Wi-Fi 이름, 폰 프록시의 응답, TUN 어댑터, 떠 있는 tun2proxy.
// 모두 막는 호출이다 — 화면 스레드에서 부르지 않는다(Controller·StatusForm 이 일꾼 스레드에서).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Management;
using System.Runtime.InteropServices;
using System.Text;

namespace WebTether
{
    public sealed class HotspotInfo
    {
        public string AdapterName;
        /// <summary>어댑터의 GUID(NetworkInterface.Id — "{…}"). Wi-Fi 끊김 알림이 이 어댑터의 것인지 본다.</summary>
        public string AdapterId;
        public string Address;
        public int IfIndex;
        /// <summary>그 어댑터가 붙은 Wi-Fi 의 이름(SSID). 얻지 못하면 null 이고 까닭은 SsidError.</summary>
        public string Ssid;
        public string SsidError;
        /// <summary>이름을 어디서 얻었나 — WifiName.FromWlan(Wi-Fi API) 또는 FromNlm(네트워크 목록의 이름).</summary>
        public string SsidSource;
        /// <summary>
        /// 192.168.49.x 어댑터가 둘 이상인데 192.168.49.1 로 가는 길(GetBestInterface)이 이 어댑터가 아니면 그 까닭 — 이름을 검사한 어댑터와
        /// 실제로 트래픽이 가는 어댑터가 다르다. 묻지 않고 연결하지 않는다(StatusText.Refusal).
        /// </summary>
        public string RouteMismatch;

        /// <summary>이름이 접두어로 시작하는가(대소문자를 가린다 — SSID 는 바이트다). 이름을 모르거나 접두어가 비면 거짓.</summary>
        public bool SsidStartsWith(string prefix)
        {
            return Ssid != null && !string.IsNullOrEmpty(prefix) && Ssid.StartsWith(prefix, StringComparison.Ordinal);
        }
    }

    /// <summary>
    /// 어댑터가 지금 붙은 Wi-Fi 의 이름 — Native Wifi API(WlanQueryInterface, wlan_intf_opcode_current_connection)로 읽기만 한다.
    /// 어댑터는 NetworkInterface.Id(GUID)로 가리킨다. netsh 의 출력은 언어마다 다르고 개인 정보(BSSID 등)가 많아 쓰지 않는다.
    /// </summary>
    public static class WifiSsid
    {
        [DllImport("wlanapi.dll")]
        static extern uint WlanOpenHandle(uint clientVersion, IntPtr reserved, out uint negotiatedVersion, out IntPtr handle);
        [DllImport("wlanapi.dll")]
        static extern uint WlanCloseHandle(IntPtr handle, IntPtr reserved);
        [DllImport("wlanapi.dll")]
        static extern uint WlanQueryInterface(IntPtr handle, ref Guid interfaceGuid, int opCode, IntPtr reserved,
                                              out int dataSize, out IntPtr data, IntPtr opcodeValueType);
        [DllImport("wlanapi.dll")]
        static extern void WlanFreeMemory(IntPtr memory);

        const int OpInterfaceState = 6;      // wlan_intf_opcode_interface_state — 위치 권한과 상관없다(이 PC 에서 확인)
        const int OpCurrentConnection = 7;   // wlan_intf_opcode_current_connection
        const int StateConnected = 1;        // wlan_interface_state_connected

        /// <summary>
        /// current_connection 이 거절(5)됐을 때 — interface_state 의 답(코드·상태)으로 그 거절을 '위치 권한 때문에 이름만 못 읽는 연결된
        /// Wi-Fi' 로 볼지 정한다(시험한다). Wi-Fi 어댑터가 아니거나(1168·87) 연결되어 있지 않거나 상태를 못 읽으면 거절이 아니다(이름 모름).
        /// </summary>
        public static bool DeniedOnConnectedWlan(uint stateRc, int state, out string error)
        {
            if (stateRc == 1168 || stateRc == 87) { error = "Wi-Fi 어댑터가 아님"; return false; }
            if (stateRc != 0) { error = "Wi-Fi 상태를 읽지 못함(코드 " + stateRc + ")"; return false; }
            if (state != StateConnected) { error = "Wi-Fi 에 연결되어 있지 않음(상태 " + state + ")"; return false; }
            error = "Wi-Fi 이름을 읽을 권한이 없음(위치 꺼짐)";
            return true;
        }
        // WLAN_CONNECTION_ATTRIBUTES: isState(4) · wlanConnectionMode(4) · strProfileName(WCHAR 256 = 512) · 그다음
        // WLAN_ASSOCIATION_ATTRIBUTES 의 첫 칸 DOT11_SSID { ULONG uSSIDLength; UCHAR ucSSID[32]; }.
        public const int SsidLengthOffset = 520;
        public const int SsidBytesOffset = 524;
        public const int MinSize = SsidBytesOffset + 32;

        /// <summary>WLAN_CONNECTION_ATTRIBUTES 의 바이트에서 SSID 를 읽는다(시험한다). 못 읽으면 null 과 까닭.</summary>
        public static string Parse(byte[] buf, int size, out string error)
        {
            error = null;
            if (buf == null || size < MinSize || buf.Length < MinSize) { error = "Wi-Fi 연결 정보가 짧음"; return null; }
            int state = BitConverter.ToInt32(buf, 0);
            if (state != StateConnected) { error = "Wi-Fi 에 연결되어 있지 않음(상태 " + state + ")"; return null; }
            uint len = BitConverter.ToUInt32(buf, SsidLengthOffset);
            if (len > 32) { error = "SSID 길이가 틀림(" + len + ")"; return null; }
            if (len == 0) { error = "SSID 가 비어 있음"; return null; }
            // 안드로이드의 Wi-Fi Direct 이름은 UTF-8 이다. 틀린 바이트는 U+FFFD 로(접두어의 ASCII 는 그대로 견줄 수 있다).
            return new UTF8Encoding(false, false).GetString(buf, SsidBytesOffset, (int)len);
        }

        /// <summary>어댑터(NetworkInterface.Id)가 붙은 Wi-Fi 의 SSID. 유선·Wi-Fi 가 아닌 어댑터·서비스 꺼짐·API 실패는 null 과 까닭.</summary>
        public static string Query(string adapterId, out string error)
        {
            bool denied;
            return Query(adapterId, out error, out denied);
        }

        /// <summary>denied: ERROR_ACCESS_DENIED — Windows 11 24H2 부터 위치 권한이 없으면 이 호출이 거절된다(관리자도 — 문서에 예외가 없다).</summary>
        public static string Query(string adapterId, out string error, out bool denied)
        {
            error = null;
            denied = false;
            Guid g;
            if (!Guid.TryParse(adapterId ?? "", out g)) { error = "어댑터 번호를 읽지 못함"; return null; }
            IntPtr h = IntPtr.Zero;
            try
            {
                uint ver;
                uint rc = WlanOpenHandle(2, IntPtr.Zero, out ver, out h);
                if (rc != 0)
                {
                    h = IntPtr.Zero;
                    error = rc == 1062 ? "WLAN 서비스가 꺼져 있음" : "Wi-Fi API 를 열지 못함(코드 " + rc + ")";
                    return null;
                }
                int size;
                IntPtr data;
                rc = WlanQueryInterface(h, ref g, OpCurrentConnection, IntPtr.Zero, out size, out data, IntPtr.Zero);
                if (rc != 0)
                {
                    if (rc == 1168 || rc == 87) error = "Wi-Fi 어댑터가 아님";
                    else if (rc == 5023) error = "Wi-Fi 에 연결되어 있지 않음";
                    else if (rc == 5)
                    {
                        // 24H2 의 위치 검사는 어댑터를 보기 전에 한다 — 유선·TUN·없는 GUID 도 5 였다(이 PC). 그대로 '거절' 로 넘기면
                        // 유선 어댑터에도 네트워크 목록 이름(유선은 DNS 접미사로 짓는다)으로 물러섰다. 위치와 상관없는 interface_state 로
                        // Wi-Fi 어댑터이고 연결되어 있는지를 따로 본다(검토 11-21).
                        int ssize;
                        IntPtr sdata;
                        uint src = WlanQueryInterface(h, ref g, OpInterfaceState, IntPtr.Zero, out ssize, out sdata, IntPtr.Zero);
                        int st = -1;
                        if (src == 0 && sdata != IntPtr.Zero)
                        {
                            try { if (ssize >= 4) st = Marshal.ReadInt32(sdata); }
                            finally { WlanFreeMemory(sdata); }
                        }
                        denied = DeniedOnConnectedWlan(src, st, out error);
                    }
                    else error = "Wi-Fi 연결 정보를 읽지 못함(코드 " + rc + ")";
                    return null;
                }
                try
                {
                    if (data == IntPtr.Zero || size <= 0) { error = "Wi-Fi 연결 정보가 비어 있음"; return null; }
                    var buf = new byte[Math.Min(size, 4096)];
                    Marshal.Copy(data, buf, 0, buf.Length);
                    return Parse(buf, buf.Length, out error);
                }
                finally { if (data != IntPtr.Zero) WlanFreeMemory(data); }
            }
            catch (DllNotFoundException) { error = "Wi-Fi API(wlanapi.dll)가 없음"; return null; }
            catch (EntryPointNotFoundException) { error = "Wi-Fi API 가 없음"; return null; }
            finally
            {
                if (h != IntPtr.Zero) WlanCloseHandle(h, IntPtr.Zero);
            }
        }
    }

    public sealed class ProbeResult
    {
        public bool Ok;
        public string Detail;
    }

    /// <summary>
    /// 어댑터가 붙은 Wi-Fi 의 이름을 얻는 차례. 먼저 Wi-Fi API(SSID 그대로). Windows 11 24H2 부터 위치 권한이 없으면 그 호출이
    /// 거절되므로(netsh 도 같다 — 이 PC 에서 확인) 그때만 네트워크 목록(NLM — WMI MSFT_NetConnectionProfile)의 이름을 쓴다:
    /// Windows 가 Wi-Fi 에 처음 붙을 때 SSID 로 짓는 이름이라 접두어 견주기에는 같다(같은 이름이 또 생기면 뒤에 ' 2' 가 붙을 뿐 —
    /// 앞머리는 그대로). 유선·Wi-Fi 가 아닌 어댑터·서비스 꺼짐은 물러서지 않는다(이름 모름).
    /// 어댑터마다 10초 기억한다 — 1초마다 Wi-Fi API 를 부르면 위치가 켜진 PC 에서 '위치 사용 중' 표시가 늘 뜬다(문서).
    /// </summary>
    public static class WifiName
    {
        public const string FromWlan = "Wi-Fi API";
        public const string FromNlm = "네트워크 목록 이름";
        public const int CacheMs = 10000;

        sealed class Entry
        {
            public DateTime At;
            public string Name, Error, Source;
        }

        static readonly Dictionary<string, Entry> cache = new Dictionary<string, Entry>();

        /// <summary>
        /// 기억을 모두 버린다 — 연결을 정하는 자리(Controller.Attempt)는 늘 새로 본다. 10초 기억을 그대로 쓰면 핫스팟이 사라진 직후 같은
        /// 어댑터가 남의 DIRECT 망(같은 192.168.49.x)에 붙었을 때 옛 이름(DIRECT-UT-…)으로 저절로 연결했다(검토 11-21).
        /// </summary>
        public static void Forget()
        {
            lock (cache) cache.Clear();
        }

        /// <summary>기억한 어댑터 수(시험).</summary>
        public static int CachedCount { get { lock (cache) return cache.Count; } }

        /// <summary>네트워크 목록 질의의 시한 — WMI 가 멈추면 이 질의가 끝나지 않아 1초 점검(겹치지 않게 건너뛴다)이 영영 서고 연결 시도가 바쁜 채 남았다.</summary>
        public static readonly TimeSpan NlmTimeout = TimeSpan.FromSeconds(5);

        public static string Get(string adapterId, int ifIndex, out string error, out string source)
        {
            var key = (adapterId ?? "") + "|" + ifIndex;
            lock (cache)
            {
                Entry e;
                if (cache.TryGetValue(key, out e) && (DateTime.UtcNow - e.At).TotalMilliseconds < CacheMs && e.At <= DateTime.UtcNow)
                {
                    error = e.Error;
                    source = e.Source;
                    return e.Name;
                }
            }
            string werr;
            bool denied;
            var ssid = WifiSsid.Query(adapterId, out werr, out denied);
            var name = Resolve(ssid, werr, denied, () => NlmName(ifIndex), out error, out source);
            lock (cache)
            {
                if (cache.Count > 32) cache.Clear();
                cache[key] = new Entry { At = DateTime.UtcNow, Name = name, Error = error, Source = source };
            }
            return name;
        }

        /// <summary>차례만(시험한다) — Wi-Fi API 의 답이 있으면 그것, 위치 권한으로 거절됐을 때만 NLM 이름, 그 밖은 이름 모름.</summary>
        public static string Resolve(string wlanSsid, string wlanError, bool wlanDenied, Func<string> nlm, out string error, out string source)
        {
            error = null;
            source = null;
            if (wlanSsid != null) { source = FromWlan; return wlanSsid; }
            if (!wlanDenied) { error = wlanError ?? "까닭 모름"; return null; }
            string n = null;
            try { n = nlm(); }
            catch (Exception ex) { error = wlanError + " · 네트워크 목록도 읽지 못함(" + ex.Message + ")"; return null; }
            if (string.IsNullOrEmpty(n)) { error = wlanError + " · 네트워크 목록에 이름 없음"; return null; }
            source = FromNlm;
            return n;
        }

        /// <summary>네트워크 목록 질의 — 연결·열거 모두 NlmTimeout 의 시한(넘으면 던진다 → 이름 모름). 시험이 시한을 본다.</summary>
        public static ManagementObjectSearcher NlmSearcher(int ifIndex)
        {
            var q = new ObjectQuery("SELECT Name, InterfaceIndex FROM MSFT_NetConnectionProfile WHERE InterfaceIndex = " + ifIndex.ToString(System.Globalization.CultureInfo.InvariantCulture));
            var scope = new ManagementScope(@"root\StandardCimv2", new ConnectionOptions { Timeout = NlmTimeout });
            var opts = new EnumerationOptions { Timeout = NlmTimeout, ReturnImmediately = true, Rewindable = false };
            return new ManagementObjectSearcher(scope, q, opts);
        }

        /// <summary>그 어댑터(IPv4 인덱스)의 네트워크 목록 이름 — 읽기만(WMI). 없으면 null.</summary>
        public static string NlmName(int ifIndex)
        {
            if (ifIndex < 0) return null;
            using (var s = NlmSearcher(ifIndex))
            using (var all = s.Get())
            {
                foreach (ManagementObject o in all)
                {
                    using (o)
                    {
                        var n = o["Name"] as string;
                        if (!string.IsNullOrEmpty(n)) return n;
                    }
                }
            }
            return null;
        }
    }

    public static class NetState
    {
        /// <summary>
        /// 폰 핫스팟(Wi-Fi Direct 그룹)의 손님 주소 192.168.49.2–254 를 가진, 켜진 어댑터와 그 Wi-Fi 이름(SSID).
        /// 여럿이면 이름이 접두어로 시작하는 것을 먼저, 없으면 처음 것(이름이 다르거나 모르는 채 — 자동 연결을 할지는 Controller 가 정한다).
        /// 안드로이드의 Wi-Fi Direct 그룹 오너는 모두 192.168.49.1 이라 주소만으로는 남의 DIRECT 망과 가르지 못한다. 막는 호출 — 일꾼 스레드에서.
        /// </summary>
        public static HotspotInfo FindHotspot(string prefix)
        {
            int candidates;
            var h = FindHotspotCore(prefix, out candidates);
            if (h != null && h.RouteMismatch == null && candidates >= 2)
                h.RouteMismatch = RouteCheck(h.IfIndex, BestInterfaceToPhone);
            return h;
        }

        [DllImport("iphlpapi.dll")]
        static extern uint GetBestInterface(uint destAddr, out uint bestIfIndex);

        /// <summary>192.168.49.1 로 갈 어댑터 번호(못 얻으면 -1) — 읽기만.</summary>
        public static int BestInterfaceToPhone()
        {
            uint idx;
            var dest = BitConverter.ToUInt32(new byte[] { 192, 168, 49, 1 }, 0);
            return GetBestInterface(dest, out idx) == 0 ? (int)idx : -1;
        }

        /// <summary>이름을 검사한 어댑터(ifIndex)와 192.168.49.1 로 가는 어댑터가 같으면 null, 다르거나 모르면 까닭(순수 — 시험한다).</summary>
        public static string RouteCheck(int ifIndex, Func<int> best)
        {
            int b;
            try { b = best(); } catch (Exception) { b = -1; }
            if (b < 0) return "192.168.49.x 어댑터가 여럿인데 192.168.49.1 로 가는 길을 확인하지 못함";
            if (ifIndex < 0 || b != ifIndex) return "192.168.49.x 어댑터가 여럿이고 192.168.49.1 로 가는 길이 이름을 확인한 어댑터가 아님(" + b + ")";
            return null;
        }

        static HotspotInfo FindHotspotCore(string prefix, out int candidates)
        {
            HotspotInfo first = null;
            candidates = 0;
            foreach (var ni in SafeInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                IPInterfaceProperties props;
                try { props = ni.GetIPProperties(); } catch (NetworkInformationException) { continue; }
                foreach (var ua in props.UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (!IsHotspotGuest(ua.Address)) continue;
                    int idx = -1;
                    try { idx = props.GetIPv4Properties().Index; } catch (NetworkInformationException) { }
                    candidates++;
                    var h = new HotspotInfo { AdapterName = ni.Name, AdapterId = ni.Id, Address = ua.Address.ToString(), IfIndex = idx };
                    string err, src;
                    h.Ssid = WifiName.Get(ni.Id, idx, out err, out src);
                    h.SsidError = h.Ssid == null ? (err ?? "까닭 모름") : null;
                    h.SsidSource = src;
                    // 접두어에 맞는 것을 찾아도 끝까지 센다 — 192.168.49.x 어댑터가 몇인지 알아야 길을 견줄지 정한다.
                    if (h.SsidStartsWith(prefix) && (first == null || !first.SsidStartsWith(prefix))) first = h;
                    else if (first == null) first = h;
                    break;
                }
            }
            return first;
        }

        public static bool IsHotspotGuest(IPAddress a)
        {
            var b = a.GetAddressBytes();
            return b.Length == 4 && b[0] == 192 && b[1] == 168 && b[2] == 49 && b[3] >= 2 && b[3] <= 254;
        }

        static NetworkInterface[] SafeInterfaces()
        {
            try { return NetworkInterface.GetAllNetworkInterfaces(); }
            catch (NetworkInformationException) { return new NetworkInterface[0]; }
        }

        public static NetworkInterface FindAdapter(string name)
        {
            foreach (var ni in SafeInterfaces())
                if (string.Equals(ni.Name, name, StringComparison.OrdinalIgnoreCase)) return ni;
            return null;
        }

        public static int IfIndexOf(string name)
        {
            var ni = FindAdapter(name);
            if (ni == null) return -1;
            try { return ni.GetIPProperties().GetIPv4Properties().Index; }
            catch (Exception) { return -1; }
        }

        /// <summary>
        /// SOCKS5: 인사 05 01 00 에 05 00 이 오는가. HTTP: 막힌 목적지로의 CONNECT 에 HTTP/1.x 답이 오는가
        /// (폰의 HTTP 프록시는 루프백 목적지를 403 으로 거절한다 — 아무 데도 잇지 않는다).
        /// </summary>
        public static ProbeResult Probe(string host, int port, ProxyKind kind, int timeoutMs)
        {
            try
            {
                using (var s = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp))
                {
                    s.NoDelay = true;
                    var ar = s.BeginConnect(host, port, null, null);
                    if (!ar.AsyncWaitHandle.WaitOne(timeoutMs))
                    {
                        return new ProbeResult { Detail = "연결 시간 초과" };
                    }
                    s.EndConnect(ar);
                    s.ReceiveTimeout = timeoutMs;
                    s.SendTimeout = timeoutMs;
                    if (kind == ProxyKind.Socks5)
                    {
                        s.Send(new byte[] { 5, 1, 0 });
                        var buf = new byte[2];
                        int n = ReadFull(s, buf);
                        if (n < 2) return new ProbeResult { Detail = "답이 없음" };
                        if (buf[0] == 5 && buf[1] == 0) return new ProbeResult { Ok = true, Detail = "SOCKS5 응답" };
                        return new ProbeResult { Detail = string.Format("뜻밖의 답 {0:x2} {1:x2}", buf[0], buf[1]) };
                    }
                    else
                    {
                        s.Send(Encoding.ASCII.GetBytes("CONNECT 127.0.0.1:9 HTTP/1.1\r\nHost: 127.0.0.1:9\r\n\r\n"));
                        var buf = new byte[8];
                        int n = ReadFull(s, buf);
                        var head = Encoding.ASCII.GetString(buf, 0, n);
                        if (head.StartsWith("HTTP/1.")) return new ProbeResult { Ok = true, Detail = "HTTP 응답" };
                        return new ProbeResult { Detail = n == 0 ? "답이 없음" : "HTTP 가 아닌 답" };
                    }
                }
            }
            catch (SocketException ex)
            {
                return new ProbeResult { Detail = SocketText(ex.SocketErrorCode) };
            }
            catch (Exception ex)
            {
                return new ProbeResult { Detail = ex.Message };
            }
        }

        static int ReadFull(Socket s, byte[] buf)
        {
            int got = 0;
            while (got < buf.Length)
            {
                int n = s.Receive(buf, got, buf.Length - got, SocketFlags.None);
                if (n <= 0) break;
                got += n;
            }
            return got;
        }

        static string SocketText(SocketError e)
        {
            switch (e)
            {
                case SocketError.ConnectionRefused: return "연결 거부";
                case SocketError.TimedOut: return "시간 초과";
                case SocketError.HostUnreachable:
                case SocketError.NetworkUnreachable: return "닿을 수 없음";
                case SocketError.HostNotFound: return "이름을 찾지 못함";
                default: return e.ToString();
            }
        }

        /// <summary>이름이 tun2proxy 로 시작하는 프로세스(이 도우미의 자식도 든다).</summary>
        public static List<int> Tun2proxyPids()
        {
            var list = new List<int>();
            foreach (var p in Process.GetProcesses())
            {
                try
                {
                    if (p.ProcessName.StartsWith("tun2proxy", StringComparison.OrdinalIgnoreCase)) list.Add(p.Id);
                }
                catch (InvalidOperationException) { }
                finally { p.Dispose(); }
            }
            return list;
        }
    }
}
