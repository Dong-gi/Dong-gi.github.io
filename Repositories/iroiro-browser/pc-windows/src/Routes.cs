// tun2proxy --setup 이 남기는 것과 그 걷기.
//
// tun2proxy 0.8.7(tproxy-config 7.0.9, src/windows.rs)의 --setup 은 윈도에서:
//   ipconfig /flushdns
//   route add 0.0.0.0 mask 0.0.0.0 10.0.0.1 metric 6        (TUN 의 게이트웨이 — 영속이 아니다)
//   --bypass 마다 route add <cidr> <원래 게이트웨이> metric 1  (프록시 주소가 사설이면 따로 더하지 않는다)
//   TUN 어댑터의 DNS 를 10.0.0.1 로
// 정상 종료(Ctrl+C)면 그 반대로 걷는데, 그때 기본 경로를 **모두** 지우고(route delete 0.0.0.0 mask 0.0.0.0)
// 원래 게이트웨이로 metric 200 을 다시 넣는다. 강제로 끝내면 그 걷기가 돌지 않고, 걷는 도중에 끝내면
// 기본 경로가 하나도 없을 수 있다. 상태 파일은 쓰지 않는다(unsafe-state-file 기능이 꺼져 있다).
//
// 그래서 도우미가 걷는 것:
//   1. TUN 어댑터에 걸린 기본 경로(다음 홉 10.0.0.1) — 어댑터가 사라지면 함께 사라지지만 남았을 때를 위해
//   2. 우리가 넘긴 --bypass 경로(다음 홉이 연결 전의 기본 게이트웨이인 것만)
//   3. 연결 전에 있던 기본 경로가 하나도 없으면 그것을 metric 200 으로 되살린다(tun2proxy 가 하는 그대로)
// 다른 tun2proxy 가 떠 있으면 아무것도 건드리지 않는다.
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;

namespace WebTether
{
    public sealed class RouteRow
    {
        public uint Dest, Mask, NextHop, IfIndex, Metric;
        /// <summary>그 어댑터의 GUID(NetworkInterface.Id — 모르면 빈 글). 번호는 어댑터가 사라지고 다시 생기면 다른 어댑터에 돌아갈 수 있다.</summary>
        public string AdapterId = "";

        public string DestText { get { return Ip(Dest); } }
        public string MaskText { get { return Ip(Mask); } }
        public string NextHopText { get { return Ip(NextHop); } }
        public bool IsDefault { get { return Dest == 0 && Mask == 0; } }

        public static string Ip(uint netOrder)
        {
            return new IPAddress(BitConverter.GetBytes(netOrder)).ToString();
        }

        public static uint Parse(string ip)
        {
            return BitConverter.ToUInt32(IPAddress.Parse(ip).GetAddressBytes(), 0);
        }

        public override string ToString()
        {
            return DestText + "/" + MaskText + " via " + NextHopText + " if " + IfIndex + " metric " + Metric;
        }
    }

    public enum RouteActionKind { Delete, AddDefault }

    public sealed class RouteAction
    {
        public RouteActionKind Kind;
        public string Dest, Mask, Gateway;
        public uint IfIndex;
        public int Metric;
        public string Why;

        /// <summary>route.exe 의 인자(System32 의 것을 온전한 경로로 부른다).</summary>
        public List<string> RouteArgs()
        {
            var a = new List<string>();
            if (Kind == RouteActionKind.Delete)
            {
                // IF 가 없으면 route.exe 는 같은 대상·마스크·게이트웨이의 경로를 **모든 어댑터에서** 지운다
                // (집 공유기가 10.0.0.1 이면 그 기본 경로까지). 계획이 고른 그 줄의 어댑터로 좁힌다.
                a.Add("delete"); a.Add(Dest); a.Add("mask"); a.Add(Mask); a.Add(Gateway);
                if (IfIndex != 0) { a.Add("if"); a.Add(IfIndex.ToString(CultureInfo.InvariantCulture)); }
            }
            else
            {
                a.Add("add"); a.Add(Dest); a.Add("mask"); a.Add(Mask); a.Add(Gateway);
                a.Add("metric"); a.Add(Metric.ToString(CultureInfo.InvariantCulture));
                a.Add("if"); a.Add(IfIndex.ToString(CultureInfo.InvariantCulture));
            }
            return a;
        }

        public override string ToString() { return "route " + string.Join(" ", RouteArgs().ToArray()) + "  (" + Why + ")"; }
    }

    /// <summary>연결할 때 적어 두는 것 — 도우미가 죽었다 다시 떠도 걷을 수 있게.</summary>
    public sealed class ConnectionState
    {
        public string TunName = "";
        public List<RouteRow> DefaultsBefore = new List<RouteRow>();
        public List<string> Bypass = new List<string>();
        /// <summary>넣기 전에 이미 있던 우회 경로(사용자가 둔 것) — 걷을 때 지우지 않는다.</summary>
        public List<string> KeptBypass = new List<string>();

        public string Serialize()
        {
            var sb = new StringBuilder();
            sb.Append("tun=").Append(TunName).Append('\n');
            foreach (var r in DefaultsBefore)
            {
                sb.Append("default=").Append(r.NextHopText).Append(' ').Append(r.IfIndex).Append(' ').Append(r.Metric);
                if (IsAdapterId(r.AdapterId)) sb.Append(' ').Append(r.AdapterId);
                sb.Append('\n');
            }
            foreach (var b in Bypass) sb.Append("bypass=").Append(b).Append('\n');
            foreach (var b in KeptBypass) sb.Append("kept=").Append(b).Append('\n');
            return sb.ToString();
        }

        public static ConnectionState Parse(string text)
        {
            var st = new ConnectionState();
            foreach (var raw in text.Split('\n'))
            {
                var line = raw.TrimEnd('\r');
                int eq = line.IndexOf('=');
                if (eq <= 0) continue;
                string k = line.Substring(0, eq), v = line.Substring(eq + 1);
                if (k == "tun") st.TunName = v;
                else if (k == "bypass" && Settings.IsCidr(v)) st.Bypass.Add(v);
                else if (k == "kept" && Settings.IsCidr(v)) st.KeptBypass.Add(v);
                else if (k == "default")
                {
                    var p = v.Split(' ');
                    uint idx, metric;
                    // 넷째 칸(어댑터 GUID)은 이 판부터 — 옛 상태 파일은 셋이다.
                    if ((p.Length == 3 || (p.Length == 4 && IsAdapterId(p[3]))) && Settings.IsIPv4(p[0]) &&
                        uint.TryParse(p[1], NumberStyles.None, CultureInfo.InvariantCulture, out idx) &&
                        uint.TryParse(p[2], NumberStyles.None, CultureInfo.InvariantCulture, out metric))
                        st.DefaultsBefore.Add(new RouteRow { NextHop = RouteRow.Parse(p[0]), IfIndex = idx, Metric = metric, AdapterId = p.Length == 4 ? p[3] : "" });
                }
            }
            return st;
        }

        /// <summary>어댑터 GUID 의 꼴({…} — NetworkInterface.Id 가 주는 것)인가.</summary>
        public static bool IsAdapterId(string s)
        {
            Guid g;
            return !string.IsNullOrEmpty(s) && s.Length == 38 && s[0] == '{' && Guid.TryParseExact(s, "B", out g);
        }

        public static ConnectionState Load(string path)
        {
            try { return File.Exists(path) ? Parse(File.ReadAllText(path, Encoding.UTF8)) : null; }
            catch (IOException) { return null; }
            catch (UnauthorizedAccessException) { return null; }
        }

        public void Save(string path) { Save(path, null); }

        public void Save(string path, string root)
        {
            SafeFs.WriteAtomic(path, Serialize(), root);
        }
    }

    public static class Routes
    {
        public const string TunGateway = "10.0.0.1"; // tproxy-config 의 TUN_GATEWAY

        [StructLayout(LayoutKind.Sequential)]
        struct MIB_IPFORWARDROW
        {
            public uint dwForwardDest, dwForwardMask, dwForwardPolicy, dwForwardNextHop, dwForwardIfIndex,
                dwForwardType, dwForwardProto, dwForwardAge, dwForwardNextHopAS,
                dwForwardMetric1, dwForwardMetric2, dwForwardMetric3, dwForwardMetric4, dwForwardMetric5;
        }

        [DllImport("iphlpapi.dll")]
        static extern int GetIpForwardTable(IntPtr table, ref int size, bool order);

        /// <summary>IPv4 경로 표를 읽는다(읽기만).</summary>
        public static List<RouteRow> ReadTable()
        {
            var list = new List<RouteRow>();
            int size = 0;
            GetIpForwardTable(IntPtr.Zero, ref size, false);
            if (size <= 0) return list;
            var buf = Marshal.AllocHGlobal(size);
            try
            {
                if (GetIpForwardTable(buf, ref size, false) != 0) return list;
                int n = Marshal.ReadInt32(buf);
                int rowSize = Marshal.SizeOf(typeof(MIB_IPFORWARDROW));
                for (int i = 0; i < n; i++)
                {
                    var row = (MIB_IPFORWARDROW)Marshal.PtrToStructure(new IntPtr(buf.ToInt64() + 4 + i * rowSize), typeof(MIB_IPFORWARDROW));
                    list.Add(new RouteRow
                    {
                        Dest = row.dwForwardDest,
                        Mask = row.dwForwardMask,
                        NextHop = row.dwForwardNextHop,
                        IfIndex = row.dwForwardIfIndex,
                        Metric = row.dwForwardMetric1,
                    });
                }
            }
            finally { Marshal.FreeHGlobal(buf); }
            return list;
        }

        /// <summary>TUN 밖의 기본 경로들(연결 전에 적어 둔다).</summary>
        public static List<RouteRow> DefaultsOutside(List<RouteRow> table, int tunIfIndex)
        {
            var r = new List<RouteRow>();
            foreach (var row in table)
                if (row.IsDefault && (tunIfIndex < 0 || row.IfIndex != (uint)tunIfIndex) && row.NextHop != 0) r.Add(row);
            return r;
        }

        /// <summary>우회 목록 가운데 지금 표에 이미 있는 것(같은 대상·마스크, 다음 홉이 연결 전 기본 게이트웨이) — 연결 전에 적어 둔다.</summary>
        public static List<string> ExistingBypass(List<RouteRow> table, List<RouteRow> defaults, List<string> bypass)
        {
            var kept = new List<string>();
            var gws = new HashSet<uint>();
            foreach (var d in defaults) gws.Add(d.NextHop);
            foreach (var b in bypass)
            {
                string net, mask;
                CidrToMask(b, out net, out mask);
                uint n = RouteRow.Parse(net), m = RouteRow.Parse(mask);
                foreach (var r in table)
                    if (r.Dest == n && r.Mask == m && gws.Contains(r.NextHop)) { kept.Add(b); break; }
            }
            return kept;
        }

        static void CidrToMask(string cidr, out string net, out string mask)
        {
            int slash = cidr.IndexOf('/');
            int len = slash < 0 ? 32 : int.Parse(cidr.Substring(slash + 1), CultureInfo.InvariantCulture);
            var ip = slash < 0 ? cidr : cidr.Substring(0, slash);
            uint m = len == 0 ? 0u : uint.MaxValue << (32 - len);
            var mb = new[] { (byte)(m >> 24), (byte)(m >> 16), (byte)(m >> 8), (byte)m };
            var ib = IPAddress.Parse(ip).GetAddressBytes();
            for (int i = 0; i < 4; i++) ib[i] &= mb[i];
            net = new IPAddress(ib).ToString();
            mask = new IPAddress(mb).ToString();
        }

        /// <summary>
        /// 걷을 것을 정한다(순수 함수 — 시험한다).
        /// tunIfIndex: 지금 TUN 어댑터의 번호(없으면 -1). liveIfIndexes: 지금 있는 어댑터 번호들.
        /// otherTun2proxy: 걷지 말아야 할 tun2proxy 가 떠 있는가(Controller 가 정한다).
        /// liveIds: 지금 있는 어댑터 번호 → GUID. 적어 둔 기본 경로에 GUID 가 있으면 그 번호의 지금 어댑터가 같은 GUID 일 때만 되살린다.
        /// </summary>
        public static List<RouteAction> CleanupPlan(List<RouteRow> table, ConnectionState state, int tunIfIndex,
                                                    ICollection<uint> liveIfIndexes, bool otherTun2proxy, List<string> notes)
        {
            return CleanupPlan(table, state, tunIfIndex, liveIfIndexes, null, otherTun2proxy, notes);
        }

        public static List<RouteAction> CleanupPlan(List<RouteRow> table, ConnectionState state, int tunIfIndex,
                                                    ICollection<uint> liveIfIndexes, IDictionary<uint, string> liveIds,
                                                    bool otherTun2proxy, List<string> notes)
        {
            var acts = new List<RouteAction>();
            if (otherTun2proxy)
            {
                if (notes != null) notes.Add("tun2proxy 가 떠 있어 경로를 건드리지 않습니다");
                return acts;
            }
            uint tunGw = RouteRow.Parse(TunGateway);

            // 1. TUN 어댑터에 걸린 기본 경로
            if (tunIfIndex >= 0)
            {
                foreach (var r in table)
                    if (r.IsDefault && r.IfIndex == (uint)tunIfIndex && r.NextHop == tunGw)
                        acts.Add(new RouteAction { Kind = RouteActionKind.Delete, Dest = "0.0.0.0", Mask = "0.0.0.0", Gateway = TunGateway, IfIndex = r.IfIndex, Why = "TUN 의 기본 경로" });
            }
            if (state == null) return acts;

            // 2. 우리가 넘긴 --bypass 경로 — 넣기 전에 이미 있던 것(사용자가 둔 같은 경로)은 빼고
            var gws = new HashSet<uint>();
            foreach (var d in state.DefaultsBefore) gws.Add(d.NextHop);
            foreach (var b in state.Bypass)
            {
                if (state.KeptBypass.Contains(b))
                {
                    if (notes != null) notes.Add("우회 경로 " + b + " 는 연결 전부터 있던 것이라 지우지 않습니다");
                    continue;
                }
                string net, mask;
                CidrToMask(b, out net, out mask);
                uint n = RouteRow.Parse(net), m = RouteRow.Parse(mask);
                foreach (var r in table)
                    if (r.Dest == n && r.Mask == m && gws.Contains(r.NextHop))
                        acts.Add(new RouteAction { Kind = RouteActionKind.Delete, Dest = net, Mask = mask, Gateway = r.NextHopText, IfIndex = r.IfIndex, Why = "우회 경로 " + b });
            }

            // 3. 기본 경로가 TUN 밖에 하나도 없으면 연결 전의 것을 되살린다
            bool anyOutside = false;
            foreach (var r in table)
                if (r.IsDefault && (tunIfIndex < 0 || r.IfIndex != (uint)tunIfIndex) && r.NextHop != tunGw) { anyOutside = true; break; }
            if (!anyOutside)
            {
                foreach (var d in state.DefaultsBefore)
                {
                    if (liveIfIndexes != null && !liveIfIndexes.Contains(d.IfIndex))
                    {
                        if (notes != null) notes.Add("연결 전 기본 경로의 어댑터(" + d.IfIndex + ")가 없어 되살리지 않습니다");
                        continue;
                    }
                    if (ConnectionState.IsAdapterId(d.AdapterId) && liveIds != null)
                    {
                        string now;
                        if (!liveIds.TryGetValue(d.IfIndex, out now) || !string.Equals(now, d.AdapterId, StringComparison.OrdinalIgnoreCase))
                        {
                            if (notes != null) notes.Add("어댑터 번호 " + d.IfIndex + " 가 연결 전과 다른 어댑터라 되살리지 않습니다");
                            continue;
                        }
                    }
                    acts.Add(new RouteAction
                    {
                        Kind = RouteActionKind.AddDefault, Dest = "0.0.0.0", Mask = "0.0.0.0",
                        Gateway = d.NextHopText, IfIndex = d.IfIndex, Metric = 200, Why = "사라진 기본 경로"
                    });
                    break; // 하나면 족하다
                }
            }
            return acts;
        }
    }
}
