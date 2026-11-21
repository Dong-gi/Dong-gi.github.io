// 자식 프로세스의 명령줄 짓기. 셸(cmd.exe)을 거치지 않으므로 %·&·^ 는 뜻이 없고,
// 남는 것은 CommandLineToArgvW(MSVC·Rust std 와 같은 규칙)가 인자를 어떻게 가르느냐뿐이다.
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace WebTether
{
    public static class CommandLine
    {
        /// <summary>인자 하나를 CommandLineToArgvW 가 그대로 돌려받는 꼴로 싼다.</summary>
        public static string Quote(string arg)
        {
            if (arg == null) arg = "";
            if (arg.Length > 0 && arg.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0) return arg;
            var sb = new StringBuilder();
            sb.Append('"');
            int i = 0;
            while (true)
            {
                int slashes = 0;
                while (i < arg.Length && arg[i] == '\\') { i++; slashes++; }
                if (i == arg.Length) { sb.Append('\\', slashes * 2); break; }
                if (arg[i] == '"') { sb.Append('\\', slashes * 2 + 1); sb.Append('"'); }
                else { sb.Append('\\', slashes); sb.Append(arg[i]); }
                i++;
            }
            sb.Append('"');
            return sb.ToString();
        }

        public static string Join(IEnumerable<string> args)
        {
            var sb = new StringBuilder();
            foreach (var a in args)
            {
                if (sb.Length > 0) sb.Append(' ');
                sb.Append(Quote(a));
            }
            return sb.ToString();
        }

        /// <summary>tun2proxy 0.8.7 의 인자. 값은 Settings.Validate 를 지난 것이어야 한다.</summary>
        public static List<string> Tun2proxyArgs(Settings s)
        {
            var a = new List<string>();
            a.Add("--proxy");
            a.Add(ProxyUrl(s));
            a.Add("--tun");
            a.Add(s.TunName);
            a.Add("--dns");
            a.Add(s.DnsMode);
            a.Add("--dns-addr");
            a.Add(s.DnsAddr);
            if (s.Setup) a.Add("--setup");
            foreach (var b in Settings.SplitBypass(s.Bypass))
            {
                a.Add("--bypass");
                a.Add(b);
            }
            a.Add("--verbosity");
            a.Add(s.Verbosity);
            return a;
        }

        public static string ProxyUrl(Settings s)
        {
            return (s.Kind == ProxyKind.Http ? "http" : "socks5") + "://" + s.ProxyHost + ":" +
                   s.ProxyPort.ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>netsh 는 제 나름으로 줄을 가른다 — name="…" 꼴을 그대로 쓴다(이름은 따옴표·제어 글자가 없음을 검사했다).</summary>
        public static string NetshWlanConnect(string profile)
        {
            if (!Settings.IsWifiProfile(profile) || profile.Length == 0)
                throw new System.ArgumentException("bad profile");
            return "wlan connect name=\"" + profile + "\"";
        }
    }
}
