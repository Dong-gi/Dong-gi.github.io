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
        public bool AutoStart = false;
        public bool UpdateCheck = true;
        public UpdateInterval UpdateEvery = UpdateInterval.Weekly;

        public static readonly string[] IntervalNames = { "daily", "weekly", "monthly" };

        /// <summary>걷은 기능의 키(데이터 한도 알림 — 2026-11-21) — 옛 설정 파일에 남아 있어도 문제로 적지 않고 버린다.</summary>
        public static readonly string[] RemovedKeys = { "limit_on", "limit_day_gb", "limit_month_gb" };

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

        /// <summary>SSID 앞머리 — 비지 않고, 앞뒤 빈칸·제어 글자 없이, UTF-8 로 32바이트(SSID 의 상한)까지.</summary>
        public static bool IsSsidPrefix(string s)
        {
            if (string.IsNullOrEmpty(s) || s.Trim().Length != s.Length) return false;
            foreach (var c in s) if (char.IsControl(c)) return false;
            try { return new UTF8Encoding(false, true).GetByteCount(s) <= 32; }
            catch (ArgumentException) { return false; } // 짝 없는 서로게이트
        }

        /// <summary>문제가 있으면 (칸 이름, 까닭) 목록을 돌려준다. 비면 통과.</summary>
        public List<KeyValuePair<string, string>> Validate()
        {
            var e = new List<KeyValuePair<string, string>>();
            Action<string, string> add = (k, m) => e.Add(new KeyValuePair<string, string>(k, m));
            if (!IsHost(ProxyHost)) add("proxy_host", "프록시 주소가 올바르지 않습니다.");
            if (ProxyPort < 1 || ProxyPort > 65535) add("proxy_port", "포트는 1–65535 입니다.");
            if (!IsIPv4(DnsAddr)) add("dns_addr", "DNS 서버는 IPv4 주소로 적습니다.");
            if (Array.IndexOf(DnsModes, DnsMode) < 0) add("dns_mode", "DNS 방식을 고르십시오.");
            if (!TunPattern.IsMatch(TunName ?? "")) add("tun_name", "TUN 이름은 영문·숫자·_- 32자까지입니다.");
            foreach (var b in SplitBypass(Bypass))
                if (!IsCidr(b)) { add("bypass", "우회 목록의 '" + b + "' 가 IPv4 나 IPv4/길이가 아닙니다."); break; }
            if (Array.IndexOf(Verbosities, Verbosity) < 0) add("verbosity", "로그 수준을 고르십시오.");
            if (ReconnectInterval < 3 || ReconnectInterval > 600) add("reconnect_interval", "다시 연결 간격은 3–600초입니다.");
            if (ReconnectMax < 1 || ReconnectMax > 1000) add("reconnect_max", "다시 연결 횟수는 1–1000 입니다.");
            if (!IsWifiProfile(WifiProfile)) add("wifi_profile", "Wi-Fi 프로필 이름에 따옴표·제어 글자를 쓸 수 없습니다(64자까지).");
            if (!IsSsidPrefix(SsidPrefix)) add("ssid_prefix", "Wi-Fi 이름 앞머리는 비울 수 없고 32바이트까지입니다(앞뒤 빈칸·제어 글자 없이).");
            return e;
        }

        // ---- 파일 ----

        static string B(bool v) { return v ? "1" : "0"; }

        public string Serialize()
        {
            var sb = new StringBuilder();
            sb.Append("# WebTether settings — key=value, one per line\n");
            Action<string, string> w = (k, v) =>
            {
                if (v.IndexOf('\n') >= 0 || v.IndexOf('\r') >= 0) throw new InvalidOperationException("newline in " + k);
                sb.Append(k).Append('=').Append(v).Append('\n');
            };
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
            w("autostart", B(AutoStart));
            w("update_check", B(UpdateCheck));
            w("update_interval", IntervalNames[(int)UpdateEvery]);
            return sb.ToString();
        }

        /// <summary>읽지 못한 줄은 기본값으로 두고 problems 에 적는다.</summary>
        public static Settings Parse(string text, List<string> problems)
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
                if (Array.IndexOf(RemovedKeys, k) >= 0) continue;
                var t = s.Clone();
                if (!t.Apply(k, v)) { Note(problems, "값을 버림: " + k); continue; }
                var errs = t.Validate();
                bool bad = false;
                foreach (var er in errs) if (er.Key == k) { bad = true; break; }
                if (bad) { Note(problems, "값을 버림: " + k); continue; }
                s = t;
            }
            return s;
        }

        static void Note(List<string> p, string m) { if (p != null) p.Add(m); }
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
                case "bypass": Bypass = v.Trim(); return true;
                case "verbosity": Verbosity = v.Trim(); return true;
                case "reconnect": if (!TryBool(v, out b)) return false; Reconnect = b; return true;
                case "reconnect_interval": if (!TryInt(v, out i)) return false; ReconnectInterval = i; return true;
                case "reconnect_max": if (!TryInt(v, out i)) return false; ReconnectMax = i; return true;
                case "wifi_profile": WifiProfile = v; return true;
                case "ssid_prefix": SsidPrefix = v; return true;
                case "autostart": if (!TryBool(v, out b)) return false; AutoStart = b; return true;
                case "update_check": if (!TryBool(v, out b)) return false; UpdateCheck = b; return true;
                case "update_interval":
                    i = Array.IndexOf(IntervalNames, v.Trim());
                    if (i < 0) return false;
                    UpdateEvery = (UpdateInterval)i;
                    return true;
                default: return false;
            }
        }

        public static Settings Load(string path, List<string> problems)
        {
            if (!File.Exists(path)) return new Settings();
            try
            {
                var fi = new FileInfo(path);
                if (fi.Length > 64 * 1024) { Note(problems, "설정 파일이 너무 큽니다 — 기본값을 씁니다."); return new Settings(); }
                return Parse(File.ReadAllText(path, new UTF8Encoding(false)), problems);
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
