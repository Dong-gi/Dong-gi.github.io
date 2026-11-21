// 읽기만 하는 망 살피기 — 핫스팟 어댑터, 폰 프록시의 응답, TUN 어댑터, 떠 있는 tun2proxy.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

namespace WebTether
{
    public sealed class HotspotInfo
    {
        public string AdapterName;
        public string Address;
        public int IfIndex;
    }

    public sealed class ProbeResult
    {
        public bool Ok;
        public string Detail;
    }

    public static class NetState
    {
        /// <summary>폰 핫스팟(Wi-Fi Direct 그룹)의 손님 주소 192.168.49.2–254 를 가진, 켜진 어댑터.</summary>
        public static HotspotInfo FindHotspot()
        {
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
                    return new HotspotInfo { AdapterName = ni.Name, Address = ua.Address.ToString(), IfIndex = idx };
                }
            }
            return null;
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
