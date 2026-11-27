// 설정 — 한 줄 한 키(key=value, UTF-8). 값에 줄바꿈이 들어갈 수 없고 아는 키만 읽는다.
// 기본값은 예전 windows-wifi.bat 이 박아 두던 값이다.
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace WebTether
{
    public enum ProxyKind { Socks5, Http }

    public sealed class Settings
    {
        public const string DefaultHost = "192.168.49.1";
        public const int DefaultSocksPort = 1080;
        public const int DefaultHttpPort = 8282;
        public const string DefaultDnsAddr = "1.0.0.1";
        public const string DefaultDnsMode = "direct";
        public const string DefaultTunName = "USBTether";
        public const string DefaultVerbosity = "info";
        public const int DefaultReconnectInterval = 10;
        public const int DefaultReconnectMax = 20;
        /// <summary>폰 앱이 붙이는 SSID 앞머리 — 이것으로 시작하는 Wi-Fi 에서만 저절로 연결한다.</summary>
        public const string DefaultSsidPrefix = "DIRECT-UT-";

        // tun2proxy 0.8.7 의 --help 에서 읽은 값들.
        public static readonly string[] DnsModes = { "direct", "over-tcp", "virtual" };
        public static readonly string[] Verbosities = { "off", "error", "warn", "info", "debug", "trace" };

        public string ProxyHost = DefaultHost;
        public int ProxyPort = DefaultSocksPort;
        public ProxyKind Kind = ProxyKind.Socks5;
        public string DnsAddr = DefaultDnsAddr;
        public string DnsMode = DefaultDnsMode;
        public string TunName = DefaultTunName;
        public bool Setup = true;
        public string Bypass = "";
        public string Verbosity = DefaultVerbosity;
        public bool Reconnect = true;
        public int ReconnectInterval = DefaultReconnectInterval;
        public int ReconnectMax = DefaultReconnectMax;
        public string WifiProfile = "";
        public string SsidPrefix = DefaultSsidPrefix;
        public bool UpdateCheck = true;
        public UpdateInterval UpdateEvery = UpdateInterval.Weekly;

        public static readonly string[] IntervalNames = { "daily", "weekly", "monthly" };

        /// <summary>걷은 기능의 키(데이터 한도 알림 — 2026-11-21, 시작 시 실행 — 11-27 서비스로) — 옛 설정 파일에 남아 있어도 문제로 적지 않고 버린다.</summary>
        public static readonly string[] RemovedKeys = { "limit_on", "limit_day_gb", "limit_month_gb", "autostart" };

        public static int DefaultPortFor(ProxyKind k) { return k == ProxyKind.Http ? DefaultHttpPort : DefaultSocksPort; }

        public Settings Clone() { return (Settings)MemberwiseClone(); }

        // ---- 검사 ----

        static readonly Regex HostName = new Regex(@"^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
        static readonly Regex TunPattern = new Regex(@"^[A-Za-z0-9_-]{1,32}$");

        public static bool IsIPv4(string s)
        {
            if (s == null) return false;
            var parts = s.Split('.');
            if (parts.Length != 4) return false;
            foreach (var p in parts)
            {
                if (p.Length == 0 || p.Length > 3) return false;
                foreach (var c in p) if (c < '0' || c > '9') return false;
                if (p.Length > 1 && p[0] == '0') return false;
                if (int.Parse(p, CultureInfo.InvariantCulture) > 255) return false;
            }
            return true;
        }

        /// <summary>
        /// 폰(Wi-Fi Direct 그룹 오너)의 주소 — 점 네 개의 십진 IPv4 이고 192.168.49.0/24 안(.1–.254). 프록시 주소는 이것만 받는다(검토 11-27:
        /// 긴 이름·비슷한 글자(192.168.49.l)가 확인 창에서 옛 값처럼 보였다). 예전 설정 파일의 값이 이 밖이면 읽을 때 그 키를 버리고 기본값을 쓴다.
        /// </summary>
        public static bool IsPhoneAddress(string s)
        {
            if (!IsIPv4(s) || !s.StartsWith("192.168.49.", StringComparison.Ordinal)) return false;
            int last = int.Parse(s.Substring(11), CultureInfo.InvariantCulture);
            return last >= 1 && last <= 254;
        }

        /// <summary>우회 목록의 한 칸이 너무 넓지 않은가 — 접두 길이 8 이상이고 첫 칸이 0 이 아님(0.0.0.0/1 같은 것으로 터널을 통째로 비켜 가지 않게).</summary>
        public static bool IsNarrowBypass(string s)
        {
            if (!IsCidr(s) || s.StartsWith("0.", StringComparison.Ordinal)) return false;
            int slash = s.IndexOf('/');
            return slash < 0 || int.Parse(s.Substring(slash + 1), CultureInfo.InvariantCulture) >= 8;
        }

        public static bool IsHost(string s)
        {
            if (string.IsNullOrEmpty(s)) return false;
            if (IsIPv4(s)) return true;
            // 숫자와 점만이면 잘못 쓴 IPv4 로 본다(예: 192.168.49.256).
            if (Regex.IsMatch(s, @"^[0-9.]+$")) return false;
            return HostName.IsMatch(s);
        }

        public static bool IsCidr(string s)
        {
            if (string.IsNullOrEmpty(s)) return false;
            int slash = s.IndexOf('/');
            if (slash < 0) return IsIPv4(s);
            string ip = s.Substring(0, slash), pre = s.Substring(slash + 1);
            if (!IsIPv4(ip) || pre.Length == 0 || pre.Length > 2) return false;
            foreach (var c in pre) if (c < '0' || c > '9') return false;
            int n = int.Parse(pre, CultureInfo.InvariantCulture);
            return n >= 0 && n <= 32;
        }

        /// <summary>
        /// 우회 목록의 저장 꼴 하나 — 칸을 갈라(쉼표·빈칸·쌍반점·탭) ", " 로 다시 잇는다(검토 11-27 7차: 제안은 날 글의 길이를, 다시 읽기는 ", " 로
        /// 이은 글의 길이를 보아 1000자 가까운 목록이 다시 읽을 때 버려졌다). Apply·제안의 길이 검사·파일 읽기가 모두 이 꼴을 쓴다 — 확인 창에 보이는
        /// 것이 저장하는 것이고, 저장한 것을 다시 읽으면 글이 바뀌지 않는다.
        /// </summary>
        public static string NormalizeBypass(string s) { return string.Join(", ", SplitBypass(s).ToArray()); }

        public static List<string> SplitBypass(string s)
        {
            var list = new List<string>();
            if (s == null) return list;
            foreach (var part in s.Split(new[] { ',', ' ', ';', '\t' }, StringSplitOptions.RemoveEmptyEntries))
                list.Add(part.Trim());
            return list;
        }

        // Wi-Fi 프로필 이름은 netsh 의 name="…" 안에 들어간다 — 따옴표와 제어 글자를 막는다.
        public static bool IsWifiProfile(string s)
        {
            if (s == null) return false;
            if (s.Length == 0) return true;
            if (s.Length > 64 || s.Trim().Length != s.Length) return false;
            foreach (var c in s) if (c == '"' || char.IsControl(c)) return false;
            return true;
        }

        /// <summary>SSID 앞머리 — 비지 않고, 앞뒤 빈칸 없이, 출력 가능한 ASCII 로 32바이트(SSID 의 상한)까지.</summary>
        public static bool IsSsidPrefix(string s)
        {
            if (string.IsNullOrEmpty(s) || s.Trim().Length != s.Length) return false;
            // ASCII 만(검토 11-27 — Wi-Fi Direct 의 SSID 는 `DIRECT-` + 영숫자로 시작한다, 비슷한 글자로 확인 창을 속이지 않게).
            foreach (var c in s) if (c < 0x20 || c > 0x7E) return false;
            try { return new UTF8Encoding(false, true).GetByteCount(s) <= 32; }
            catch (ArgumentException) { return false; } // 짝 없는 서로게이트
        }

        /// <summary>문제가 있으면 (칸 이름, 까닭) 목록을 돌려준다. 비면 통과.</summary>
        public List<KeyValuePair<string, string>> Validate()
        {
            var e = new List<KeyValuePair<string, string>>();
            Action<string, string> add = (k, m) => e.Add(new KeyValuePair<string, string>(k, m));
            if (!IsPhoneAddress(ProxyHost)) add("proxy_host", "프록시 주소는 폰의 192.168.49.x 입니다.");
            if (ProxyPort < 1 || ProxyPort > 65535) add("proxy_port", "포트는 1–65535 입니다.");
            if (!IsIPv4(DnsAddr)) add("dns_addr", "DNS 서버는 IPv4 주소로 적습니다.");
            if (Array.IndexOf(DnsModes, DnsMode) < 0) add("dns_mode", "DNS 방식을 고르십시오.");
            if (!TunPattern.IsMatch(TunName ?? "")) add("tun_name", "TUN 이름은 영문·숫자·_- 32자까지입니다.");
            foreach (var b in SplitBypass(Bypass))
            {
                if (!IsCidr(b)) { add("bypass", "우회 목록의 '" + b + "' 가 IPv4 나 IPv4/길이가 아닙니다."); break; }
                if (!IsNarrowBypass(b)) { add("bypass", "우회 목록의 '" + b + "' 가 너무 넓습니다(/8 이상, 0. 으로 시작하지 않게)."); break; }
            }
            if (Array.IndexOf(Verbosities, Verbosity) < 0) add("verbosity", "로그 수준을 고르십시오.");
            if (ReconnectInterval < 3 || ReconnectInterval > 600) add("reconnect_interval", "다시 연결 간격은 3–600초입니다.");
            if (ReconnectMax < 1 || ReconnectMax > 1000) add("reconnect_max", "다시 연결 횟수는 1–1000 입니다.");
            if (!IsWifiProfile(WifiProfile)) add("wifi_profile", "Wi-Fi 프로필 이름에 따옴표·제어 글자를 쓸 수 없습니다(64자까지).");
            if (!IsSsidPrefix(SsidPrefix)) add("ssid_prefix", "Wi-Fi 이름 앞머리는 영문·숫자·기호 32자까지입니다(비우거나 앞뒤 빈칸 없이).");
            return e;
        }

        // ---- 파일 ----

        static string B(bool v) { return v ? "1" : "0"; }

        /// <summary>아는 키 — 파일과 파이프(set_*)가 같은 차례로 쓴다.</summary>
        public static readonly string[] Keys =
        {
            "proxy_host", "proxy_port", "proxy_type", "dns_addr", "dns_mode", "tun_name", "setup", "bypass", "verbosity",
            "reconnect", "reconnect_interval", "reconnect_max", "wifi_profile", "ssid_prefix", "update_check", "update_interval",
        };

        /// <summary>(키, 값) — 파일·파이프에 쓰는 꼴.</summary>
        public List<KeyValuePair<string, string>> Pairs()
        {
            var r = new List<KeyValuePair<string, string>>();
            Action<string, string> w = (k, v) => r.Add(new KeyValuePair<string, string>(k, v));
            w("proxy_host", ProxyHost);
            w("proxy_port", ProxyPort.ToString(CultureInfo.InvariantCulture));
            w("proxy_type", Kind == ProxyKind.Http ? "http" : "socks5");
            w("dns_addr", DnsAddr);
            w("dns_mode", DnsMode);
            w("tun_name", TunName);
            w("setup", B(Setup));
            w("bypass", Bypass);
            w("verbosity", Verbosity);
            w("reconnect", B(Reconnect));
            w("reconnect_interval", ReconnectInterval.ToString(CultureInfo.InvariantCulture));
            w("reconnect_max", ReconnectMax.ToString(CultureInfo.InvariantCulture));
            w("wifi_profile", WifiProfile);
            w("ssid_prefix", SsidPrefix);
            w("update_check", B(UpdateCheck));
            w("update_interval", IntervalNames[(int)UpdateEvery]);
            return r;
        }

        public string Serialize()
        {
            var sb = new StringBuilder();
            sb.Append("# WebTether settings — key=value, one per line\n");
            foreach (var kv in Pairs())
            {
                if (kv.Value.IndexOf('\n') >= 0 || kv.Value.IndexOf('\r') >= 0) throw new InvalidOperationException("newline in " + kv.Key);
                sb.Append(kv.Key).Append('=').Append(kv.Value).Append('\n');
            }
            return sb.ToString();
        }

        /// <summary>값 하나의 상한 — 확인 창이 자르지 않고 줄을 바꿔 다 보이는 길이. 우회 목록은 여러 /16 을 적을 수 있게 넉넉히.</summary>
        public static int MaxValueLength(string key) { return key == "bypass" ? 1000 : 200; }

        /// <summary>값에 쓸 수 없는 글자 — 확인 창의 꼴(‘값’ → ‘값’, ⟨U+XXXX⟩)을 흉내 내지 못하게.</summary>
        const string ReservedChars = "\u2192\u27E8\u27E9\u2018\u2019";

        /// <summary>
        /// 값 규칙 — **파일 읽기(Parse)와 제안(FromProposal)이 같은 함수**를 쓴다(검토 11-27: 둘이 달라 옛 파일 값이면 트레이가 지금 설정을 읽지도
        /// 못했다). 길이(MaxValueLength), 보이지 않는 글자(제어·서식·줄 나눔), U+0020 밖의 빈칸, 앞뒤·겹친 빈칸, 확인 창의 기호(→ ⟨ ⟩ ‘ ’),
        /// Wi-Fi 프로필의 한 낱말 안에 라틴과 키릴·그리스·전각이 섞인 것. 통과하면 null, 아니면 짧은 까닭.
        /// </summary>
        public static string CheckValue(string key, string v)
        {
            if (v == null) return "값 없음";
            if (v.Length > MaxValueLength(key)) return "너무 긴 값";
            foreach (var c in v)
            {
                var cat = char.GetUnicodeCategory(c);
                if (cat == UnicodeCategory.Control || cat == UnicodeCategory.Format || cat == UnicodeCategory.LineSeparator ||
                    cat == UnicodeCategory.ParagraphSeparator) return "보이지 않는 글자";
                if (cat == UnicodeCategory.SpaceSeparator && c != ' ') return "보통 빈칸이 아닌 빈칸";
                if (ReservedChars.IndexOf(c) >= 0) return "쓸 수 없는 글자(→ ⟨ ⟩ ‘ ’)";
            }
            if (v.Length > 0 && (v[0] == ' ' || v[v.Length - 1] == ' ')) return "앞뒤 빈칸";
            if (v.IndexOf("  ", StringComparison.Ordinal) >= 0) return "겹친 빈칸";
            if (key == "wifi_profile" && MixedScript(v)) return "한 낱말에 라틴과 다른 글자(키릴·그리스·전각)가 섞임";
            return null;
        }

        /// <summary>한 낱말(빈칸으로 가른 것) 안에 라틴(ASCII 영숫자·라틴 확장)과 키릴·그리스·전각(FF00–FFEF)이 함께 있는가.</summary>
        public static bool MixedScript(string v)
        {
            foreach (var word in v.Split(' '))
            {
                bool latin = false, other = false;
                foreach (var c in word)
                {
                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 0x00C0 && c <= 0x024F)) latin = true;
                    else if ((c >= 0x0370 && c <= 0x03FF) || (c >= 0x0400 && c <= 0x052F) || (c >= 0xFF00 && c <= 0xFFEF)) other = true;
                }
                if (latin && other) return true;
            }
            return false;
        }

        /// <summary>파일의 우회 목록 — 틀리거나 넓은 칸만 빼고 남긴 것을 저장 꼴(NormalizeBypass)로 다시 잇는다(뺀 것은 problems 에).</summary>
        static string FilterBypass(string v, List<string> problems, out bool cut)
        {
            var keep = new List<string>();
            var dropped = new List<string>();
            foreach (var part in SplitBypass(v))
                if (IsCidr(part) && IsNarrowBypass(part)) keep.Add(part); else dropped.Add(part);
            cut = dropped.Count > 0;
            if (cut) Note(problems, "우회 목록에서 뺌: " + Short(string.Join(" ", dropped.ToArray())));
            return string.Join(", ", keep.ToArray());
        }

        /// <summary>
        /// 파이프로 온 제안(set_키 → 값)을 엄격하게 읽는다 — 아는 키가 **모두 한 번씩** 있어야 하고, 값 하나라도 값 규칙(CheckValue — 파일 읽기와
        /// 같다)·읽기·검사에 걸리면 null 과 까닭(파일 읽기와 달리 틀린 키만 버리지 않는다 — 사용자가 확인 창에서 본 것과 다른 것이 적용되지 않게).
        /// </summary>
        public static Settings FromProposal(IDictionary<string, string> req, string prefix, out string error)
        {
            error = null;
            var s = new Settings();
            foreach (var k in Keys)
            {
                string v;
                if (!req.TryGetValue(prefix + k, out v)) { error = "빠진 값: " + k; return null; }
                var why = CheckValue(k, v);
                // 우회 목록은 저장 꼴로도 본다 — 길이는 저장하고 다시 읽을 글로(", " 로 이으면 날 글보다 길어질 수 있다).
                if (why == null && k == "bypass") why = CheckValue(k, NormalizeBypass(v));
                if (why != null) { error = k + " — " + why; return null; }
                if (!s.Apply(k, v)) { error = "읽지 못한 값: " + k; return null; }
            }
            foreach (var key in req.Keys)
                if (key.StartsWith(prefix, StringComparison.Ordinal) && Array.IndexOf(Keys, key.Substring(prefix.Length)) < 0)
                {
                    error = "모르는 키: " + key;
                    return null;
                }
            var errs = s.Validate();
            if (errs.Count > 0) { error = errs[0].Key + " — " + errs[0].Value; return null; }
            return s;
        }

        /// <summary>확인 창에 보이는 이름.</summary>
        public static string Label(string key)
        {
            switch (key)
            {
                case "proxy_host": return "프록시 주소";
                case "proxy_port": return "프록시 포트";
                case "proxy_type": return "프록시 종류";
                case "dns_addr": return "DNS 서버";
                case "dns_mode": return "DNS 방식";
                case "tun_name": return "TUN 이름";
                case "setup": return "경로·DNS 자동 설정";
                case "bypass": return "우회 목록";
                case "verbosity": return "로그 수준";
                case "reconnect": return "끊기면 다시 연결";
                case "reconnect_interval": return "다시 연결 간격(초)";
                case "reconnect_max": return "다시 연결 횟수 상한";
                case "wifi_profile": return "Wi-Fi 프로필";
                case "ssid_prefix": return "자동 연결 Wi-Fi";
                case "update_check": return "업데이트 확인";
                case "update_interval": return "확인 간격";
                default: return key;
            }
        }

        /// <summary>읽지 못한 줄은 기본값으로 두고 problems 에 적는다. defaulted 에는 기본값으로 돌린(우회 목록은 칸을 뺀) 아는 키를.</summary>
        public static Settings Parse(string text, List<string> problems, List<string> defaulted = null)
        {
            var s = new Settings();
            if (text == null) return s;
            foreach (var raw in text.Split('\n'))
            {
                var line = raw.TrimEnd('\r');
                if (line.Length == 0 || line[0] == '#') continue;
                int eq = line.IndexOf('=');
                if (eq <= 0) { Note(problems, "줄을 읽지 못함: " + Short(line)); continue; }
                string k = line.Substring(0, eq).Trim(), v = line.Substring(eq + 1);
                bool known = Array.IndexOf(Keys, k) >= 0;
                if (Array.IndexOf(RemovedKeys, k) >= 0) continue;
                // 제안과 같은 값 규칙 — 걸리면 그 키를 버리고 기본값(우회 목록은 틀리거나 넓은 칸만 뺀다).
                bool cut = false;
                if (k == "bypass") v = FilterBypass(v, problems, out cut);
                var why = CheckValue(k, v);
                if (why != null) { Note(problems, "값을 버림: " + k + " (" + why + ")"); if (known) Mark(defaulted, k, true); continue; }
                var t = s.Clone();
                if (!t.Apply(k, v)) { Note(problems, "값을 버림: " + k); if (known) Mark(defaulted, k, true); continue; }
                var errs = t.Validate();
                bool bad = false;
                foreach (var er in errs) if (er.Key == k) { bad = true; break; }
                if (bad) { Note(problems, "값을 버림: " + k); Mark(defaulted, k, true); continue; }
                s = t;
                Mark(defaulted, k, cut); // 뒤 줄이 같은 키를 바로 적었으면 지운다
            }
            return s;
        }

        static void Note(List<string> p, string m) { if (p != null) p.Add(m); }

        static void Mark(List<string> d, string k, bool on)
        {
            if (d == null) return;
            d.Remove(k);
            if (on) d.Add(k);
        }

        /// <summary>파일 읽기에서 기본값으로 돌린 키(쉼표로 이은 것) → 설정 창의 한 줄. 아는 키만, 없으면 null.</summary>
        public static string DefaultedNotice(string keys)
        {
            if (string.IsNullOrEmpty(keys) || keys.Length > 400) return null;
            var labels = new List<string>();
            foreach (var k in keys.Split(','))
                if (Array.IndexOf(Keys, k) >= 0 && !labels.Contains(Label(k))) labels.Add(Label(k));
            return labels.Count == 0 ? null : "설정 일부를 기본값으로 돌렸습니다: " + string.Join(", ", labels.ToArray());
        }
        static string Short(string s) { return s.Length > 40 ? s.Substring(0, 40) + "…" : s; }

        static bool TryInt(string v, out int r) { return int.TryParse(v.Trim(), NumberStyles.None, CultureInfo.InvariantCulture, out r); }
        static bool TryBool(string v, out bool r)
        {
            v = v.Trim();
            if (v == "1") { r = true; return true; }
            if (v == "0") { r = false; return true; }
            r = false; return false;
        }

        public bool Apply(string k, string v)
        {
            int i; bool b;
            switch (k)
            {
                case "proxy_host": ProxyHost = v.Trim(); return true;
                case "proxy_port": if (!TryInt(v, out i)) return false; ProxyPort = i; return true;
                case "proxy_type":
                    if (v.Trim() == "socks5") Kind = ProxyKind.Socks5;
                    else if (v.Trim() == "http") Kind = ProxyKind.Http;
                    else return false;
                    return true;
                case "dns_addr": DnsAddr = v.Trim(); return true;
                case "dns_mode": DnsMode = v.Trim(); return true;
                case "tun_name": TunName = v.Trim(); return true;
                case "setup": if (!TryBool(v, out b)) return false; Setup = b; return true;
                case "bypass": Bypass = NormalizeBypass(v); return true;
                case "verbosity": Verbosity = v.Trim(); return true;
                case "reconnect": if (!TryBool(v, out b)) return false; Reconnect = b; return true;
                case "reconnect_interval": if (!TryInt(v, out i)) return false; ReconnectInterval = i; return true;
                case "reconnect_max": if (!TryInt(v, out i)) return false; ReconnectMax = i; return true;
                case "wifi_profile": WifiProfile = v; return true;
                case "ssid_prefix": SsidPrefix = v; return true;
                case "update_check": if (!TryBool(v, out b)) return false; UpdateCheck = b; return true;
                case "update_interval":
                    i = Array.IndexOf(IntervalNames, v.Trim());
                    if (i < 0) return false;
                    UpdateEvery = (UpdateInterval)i;
                    return true;
                default: return false;
            }
        }

        public static Settings Load(string path, List<string> problems, List<string> defaulted = null)
        {
            if (!File.Exists(path)) return new Settings();
            try
            {
                var fi = new FileInfo(path);
                if (fi.Length > 64 * 1024) { Note(problems, "설정 파일이 너무 큽니다 — 기본값을 씁니다."); return new Settings(); }
                return Parse(File.ReadAllText(path, new UTF8Encoding(false)), problems, defaulted);
            }
            catch (Exception ex)
            {
                Note(problems, "설정을 읽지 못함: " + ex.Message);
                return new Settings();
            }
        }

        public void Save(string path) { Save(path, null); }

        /// <summary>root: 상태 폴더 — 그 아래에 재분석 지점이 끼어 있으면 쓰지 않는다(SafeFs).</summary>
        public void Save(string path, string root)
        {
            SafeFs.WriteAtomic(path, Serialize(), root);
        }
    }
}
