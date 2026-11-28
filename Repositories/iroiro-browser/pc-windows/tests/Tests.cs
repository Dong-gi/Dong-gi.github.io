// 시험 — NuGet 없이 콘솔 exe 하나. 바깥 망·경로·Wi-Fi·작업 스케줄러를 바꾸지 않는다(모두 루프백·스크래치·가짜).
//   WebTether-tests.exe [--scratch <폴더>] [--only <이름 일부>] [--shots <폴더>] [--with-dialogs(화면에 메시지 상자를 띄우는 시험도)]
// 결과는 콘솔과 <스크래치>\results.txt 에. Ctrl+C 시험이 이 프로세스의 콘솔을 떼므로 출력은 파일로 돌려 받는 편이 낫다.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether.Tests
{
    public static class TestMain
    {
        static string scratch;
        static string outDir;
        static StreamWriter results;
        static int passed, failed;
        internal static bool hardened;

        [STAThread]
        public static int Main(string[] args)
        {
            // 도우미와 같게 DLL 찾기를 좁힌 채 모든 시험을 돈다(창 그리기·WMI·HTTP 가 그대로 되는지).
            hardened = DllSafety.Harden();
            if (args.Length == 3 && args[0] == "--job-parent")
            {
                // 작업 개체 시험의 '도우미' 대역 — ChildProcess.Start 로 ctrlc-child 를 띄우고 번호를 알린 뒤 기다린다.
                // 시험이 이 프로세스(제가 띄운 시험 자식)를 강제로 끝내고, 손자가 함께 끝나는지 본다.
                ConsoleCtrl.IgnoreForThisProcess();
                string err;
                var c = ChildProcess.Start(args[1], new List<string> { "wait", args[2] }, Path.GetDirectoryName(args[2]), null, out err);
                if (c == null) { Console.WriteLine("error " + err); return 2; }
                Console.WriteLine("child " + c.Pid + " job " + c.InKillOnCloseJob);
                Console.Out.Flush();
                Thread.Sleep(60000);
                return 0;
            }
            scratch = Path.Combine(Path.GetTempPath(), "webtether-tests");
            string only = null, shots = null;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--scratch" && i + 1 < args.Length) scratch = args[++i];
                else if (args[i] == "--only" && i + 1 < args.Length) only = args[++i];
                else if (args[i] == "--shots" && i + 1 < args.Length) shots = args[++i];
            }
            outDir = Path.GetDirectoryName(Application.ExecutablePath);
            Directory.CreateDirectory(scratch);
            results = new StreamWriter(Path.Combine(scratch, "results.txt"), false, new UTF8Encoding(false)) { AutoFlush = true };
            ConsoleCtrl.IgnoreForThisProcess();

            if (args.Contains("--live-update"))
            {
                // 실제 GitHub API 에 한 번(작은 답 셋) — 도우미가 쓰는 .NET 의 길(TLS·크기 상한·JSON) 확인.
                var st = new UpdateState();
                var sw0 = Stopwatch.StartNew();
                bool ok = UpdateFetch.Check(st, Embedded.Tun2proxyVersion, UpdateFetch.Get);
                Say("live: " + sw0.ElapsedMilliseconds + " ms");
                Say("live: ok=" + ok + " latest=" + st.Latest + " url=" + st.LatestUrl + " digest=" + st.LatestDigest +
                    " advisories=" + st.Advisories.Count + " error=" + st.LastError);
                return ok ? 0 : 1;
            }
            if (shots != null)
            {
                Shots.Render(shots, scratch);
                Say("그림을 그렸습니다: " + shots);
                return 0;
            }

            var tests = new List<KeyValuePair<string, Action>>();
            // 화면에 보이는 창(메시지 상자 — 묻는 창의 z 차례 시험)은 --with-dialogs 일 때만(사용자 화면에 창을 띄우지 않는다 — 11-27).
            bool withDialogs = args.Contains("--with-dialogs");
            Action<string, Action> T = (n, a) =>
            {
                if (!withDialogs && n.StartsWith("묻는 창")) { Say("SKIP  " + n + "  (--with-dialogs 일 때만 — 화면에 창을 띄운다)"); return; }
                tests.Add(new KeyValuePair<string, Action>(n, a));
            };
            SettingsTests.Add(T);
            CommandLineTests.Add(T);
            BinariesTests.Add(T);
            ChildTests.Add(T);
            ProbeTests.Add(T);
            RouteTests.Add(T);
            MiscTests.Add(T);
            ControllerTests.Add(T);
            SsidTests.Add(T);
            ServiceTests.Add(T);
            UpdateTests.Add(T);
            UpdateTests.AddTimeouts(T);
            SecurityTests.Add(T);

            foreach (var t in tests)
            {
                if (only != null && t.Key.IndexOf(only, StringComparison.OrdinalIgnoreCase) < 0) continue;
                var sw = Stopwatch.StartNew();
                try
                {
                    t.Value();
                    passed++;
                    Say("PASS  " + t.Key + "  (" + sw.ElapsedMilliseconds + " ms)");
                }
                catch (Exception ex)
                {
                    failed++;
                    Say("FAIL  " + t.Key + "\n      " + ex.GetType().Name + ": " + ex.Message + "\n" + ex.StackTrace);
                }
            }
            Say(string.Format("\n통과 {0} · 실패 {1}", passed, failed));
            return failed == 0 ? 0 : 1;
        }

        public static void Say(string s)
        {
            try { Console.WriteLine(s); } catch (IOException) { }
            results.WriteLine(s);
        }

        public static string Scratch(string name)
        {
            var d = Path.Combine(scratch, name);
            if (Directory.Exists(d)) SecureDir.DeleteTree(d); // 접합점은 따라 들어가지 않는다
            Directory.CreateDirectory(d);
            return d;
        }

        public static string OutDir { get { return outDir; } }
        public static string ScratchRoot { get { return scratch; } }
    }

    static class A
    {
        public static void True(bool c, string what) { if (!c) throw new Exception("기대와 다름: " + what); }
        public static void Eq<T>(T want, T got, string what)
        {
            if (!EqualityComparer<T>.Default.Equals(want, got))
                throw new Exception(what + " — 기대 <" + want + "> 실제 <" + got + ">");
        }
        public static void Throws<E>(Action a, string what) where E : Exception
        {
            try { a(); } catch (E) { return; }
            throw new Exception("던지지 않음: " + what);
        }
        public static void WaitUntil(Func<bool> cond, int ms, string what)
        {
            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < ms) { if (cond()) return; Thread.Sleep(20); }
            if (!cond()) throw new Exception("시간 안에 되지 않음: " + what);
        }
    }

    // ------------------------------------------------------------------ 설정
    static class SettingsTests
    {
        public static void Add(Action<string, Action> T)
        {
            T("설정: 기본값은 예전 windows-wifi.bat 의 값", () =>
            {
                var s = new Settings();
                A.Eq("192.168.49.1", s.ProxyHost, "주소");
                A.Eq(1080, s.ProxyPort, "포트");
                A.Eq(ProxyKind.Socks5, s.Kind, "종류");
                A.Eq("1.0.0.1", s.DnsAddr, "DNS");
                A.Eq("USBTether", s.TunName, "TUN");
                A.True(s.Setup, "--setup 켬");
                A.True(s.Reconnect && s.WifiProfile == "", "켬·끔 기본값");
                A.Eq(0, s.Validate().Count, "기본값은 검사를 지난다");
            });

            T("설정: 쓰고 읽으면 같다(한글·빈칸 든 Wi-Fi 이름)", () =>
            {
                var s = new Settings { ProxyHost = "192.168.49.7", ProxyPort = 8282, Kind = ProxyKind.Http, DnsMode = "over-tcp",
                    Bypass = "10.0.0.0/8, 1.2.3.4", WifiProfile = "DIRECT-UT-내 폰 & 집",
                    ReconnectInterval = 30, ReconnectMax = 7, Setup = false, Verbosity = "debug" };
                var dir = TestMain.Scratch("settings");
                var path = Path.Combine(dir, "settings");
                s.Save(path);
                var probs = new List<string>();
                var r = Settings.Load(path, probs);
                A.Eq(0, probs.Count, "문제 없음");
                A.Eq(s.Serialize(), r.Serialize(), "같은 글");
                A.Eq("DIRECT-UT-내 폰 & 집", r.WifiProfile, "Wi-Fi 이름");
                s.Save(path); // 덮어쓰기(File.Replace)
                A.True(!File.Exists(path + ".tmp"), "임시 파일이 남지 않음");
            });

            T("설정: 읽을 때 잘못된 값은 그 키만 버린다", () =>
            {
                var probs = new List<string>();
                var r = Settings.Parse("proxy_port=99999\nproxy_host=evil host\ntun_name=a b\nunknown=1\nsetup=yes\n" +
                                       "dns_addr=1.1.1.1\r\nverbosity=loud\nwifi_profile=a\"b\nlimit_day_gb=-1\nno equals sign\nbypass=1.2.3.0/33", probs);
                A.Eq(1080, r.ProxyPort, "포트 기본값");
                A.Eq("192.168.49.1", r.ProxyHost, "주소 기본값");
                A.Eq("USBTether", r.TunName, "TUN 기본값");
                A.True(r.Setup, "setup 기본값");
                A.Eq("1.1.1.1", r.DnsAddr, "CR 이 붙은 줄도 읽는다");
                A.Eq("info", r.Verbosity, "로그 수준 기본값");
                A.Eq("", r.WifiProfile, "따옴표 든 프로필 버림");
                A.True(!probs.Any(x => x.Contains("limit")), "걷은 한도 키는 문제로 적지 않음: " + string.Join("|", probs.ToArray()));
                A.Eq("", r.Bypass, "잘못된 CIDR 버림");
                A.True(probs.Count >= 9, "버린 것을 적는다(" + probs.Count + ")");
            });

            T("설정: 걷은 데이터 한도 키(limit_*)는 읽어도 문제로 적지 않고, 다음 저장에서 사라진다", () =>
            {
                var probs = new List<string>();
                var r = Settings.Parse("limit_on=1\nlimit_day_gb=1.5\nlimit_month_gb=abc\nproxy_port=8282\n", probs);
                A.Eq(0, probs.Count, "문제 없음: " + string.Join("|", probs.ToArray()));
                A.Eq(8282, r.ProxyPort, "다른 키는 읽는다");
                A.True(r.Serialize().IndexOf("limit", StringComparison.Ordinal) < 0, "쓰지 않음");
            });

            T("설정: 주소·포트·이름 검사", () =>
            {
                foreach (var ok in new[] { "192.168.49.1", "1.0.0.1", "phone", "a-b.example.com", "0.0.0.0" })
                    A.True(Settings.IsHost(ok), "받음 " + ok);
                foreach (var bad in new[] { "", "192.168.49.256", "192.168.049.1", "1.2.3", "a..b", "-a.com", "a b", "a\"b", "a;b", "a&b", "[::1]", "1.2.3.4:80", "가.com" })
                    A.True(!Settings.IsHost(bad), "거절 " + bad);
                foreach (var ok in new[] { "10.0.0.0/8", "1.2.3.4", "0.0.0.0/0", "1.2.3.4/32" })
                    A.True(Settings.IsCidr(ok), "CIDR " + ok);
                foreach (var bad in new[] { "10.0.0.0/33", "10.0.0.0/", "/8", "10.0.0.0/-1", "1.2.3.4/8x", "fe80::/10" })
                    A.True(!Settings.IsCidr(bad), "CIDR 거절 " + bad);
                var s = new Settings { ProxyPort = 0, TunName = "Tun Name", ReconnectInterval = 2, ReconnectMax = 0, WifiProfile = " lead", DnsMode = "x" };
                var keys = s.Validate().Select(e => e.Key).ToList();
                foreach (var k in new[] { "proxy_port", "tun_name", "reconnect_interval", "reconnect_max", "wifi_profile", "dns_mode" })
                    A.True(keys.Contains(k), "걸림 " + k);
                A.True(Settings.IsWifiProfile("내 핫스팟"), "한글 프로필");
                A.True(!Settings.IsWifiProfile("a\nb"), "줄바꿈 프로필");
                A.True(!Settings.IsWifiProfile(new string('x', 65)), "65자");
            });

            T("설정: 줄바꿈이 든 값은 쓰지 않는다", () =>
            {
                var s = new Settings { Bypass = "1.2.3.4\nautostart=1" };
                A.True(s.Validate().Any(e => e.Key == "bypass"), "검사에서 걸림");
                A.Throws<InvalidOperationException>(() => s.Serialize(), "Serialize 가 던짐");
            });
        }
    }

    // ------------------------------------------------------------------ 명령줄
    static class CommandLineTests
    {
        [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        static extern IntPtr CommandLineToArgvW(string cmd, out int argc);
        [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr p);

        static string[] Split(string cmd)
        {
            int n;
            var p = CommandLineToArgvW(cmd, out n);
            var r = new string[n];
            for (int i = 0; i < n; i++) r[i] = Marshal.PtrToStringUni(Marshal.ReadIntPtr(p, i * IntPtr.Size));
            LocalFree(p);
            return r;
        }

        static readonly string[] Tricky =
        {
            "", "plain", "a b", "a\tb", "a\"b", "\"", "\"\"", "a\\", "a\\\\", "a\\\"b", "a\\\\\"b", "x y\\", "x y\\\\",
            "&|<>^%PATH%!", "한글 이름", "trailing space ", "\\\\server\\share\\", "c:\\path with space\\", "--proxy=socks5://a b"
        };

        public static void Add(Action<string, Action> T)
        {
            T("명령줄: 싼 인자를 CommandLineToArgvW 가 그대로 돌려준다", () =>
            {
                var cmd = "prog.exe " + CommandLine.Join(Tricky);
                var got = Split(cmd);
                A.Eq(Tricky.Length + 1, got.Length, "인자 수");
                for (int i = 0; i < Tricky.Length; i++) A.Eq(Tricky[i], got[i + 1], "인자 " + i);
            });

            T("명령줄: 실제 자식 프로세스도 같은 인자를 받는다", () =>
            {
                var exe = Path.Combine(TestMain.OutDir, "ctrlc-child.exe");
                var args = new List<string> { "args" };
                args.AddRange(Tricky.Where(a => a.Length > 0 && a.IndexOf('\t') < 0));
                var psi = new ProcessStartInfo(exe, CommandLine.Join(args)) { UseShellExecute = false, RedirectStandardOutput = true, CreateNoWindow = true, StandardOutputEncoding = Encoding.UTF8 };
                using (var p = Process.Start(psi))
                {
                    var lines = new List<string>();
                    string l;
                    while ((l = p.StandardOutput.ReadLine()) != null) lines.Add(l);
                    p.WaitForExit();
                    A.Eq(args.Count - 1, lines.Count, "줄 수");
                    for (int i = 1; i < args.Count; i++)
                    {
                        // 콘솔 출력 부호(기본 OEM)가 한글을 깨뜨릴 수 있어 ASCII 인자만 견준다.
                        if (args[i].Any(c => c > 127)) continue;
                        A.Eq("[" + args[i] + "]", lines[i - 1], "인자 " + i);
                    }
                }
            });

            T("명령줄: 기본 설정의 tun2proxy 인자 = 예전 배치 파일과 같은 뜻", () =>
            {
                var a = CommandLine.Tun2proxyArgs(new Settings());
                A.Eq("--proxy socks5://192.168.49.1:1080 --tun USBTether --dns direct --dns-addr 1.0.0.1 --setup --verbosity info",
                     string.Join(" ", a.ToArray()), "인자");
            });

            T("명령줄: http·우회 목록·--setup 끔", () =>
            {
                var s = new Settings { Kind = ProxyKind.Http, ProxyPort = 8282, Bypass = "10.0.0.0/8, 1.2.3.4", Setup = false, DnsMode = "over-tcp" };
                var a = string.Join(" ", CommandLine.Tun2proxyArgs(s).ToArray());
                A.Eq("--proxy http://192.168.49.1:8282 --tun USBTether --dns over-tcp --dns-addr 1.0.0.1 --bypass 10.0.0.0/8 --bypass 1.2.3.4 --verbosity info", a, "인자");
            });

            T("명령줄: netsh 의 Wi-Fi 연결 — 이름에 따옴표는 받지 않는다", () =>
            {
                A.Eq("wlan connect name=\"DIRECT-UT-내 폰 & 집\"", CommandLine.NetshWlanConnect("DIRECT-UT-내 폰 & 집"), "netsh 인자");
                A.Throws<ArgumentException>(() => CommandLine.NetshWlanConnect("a\" & calc"), "따옴표");
                A.Throws<ArgumentException>(() => CommandLine.NetshWlanConnect(""), "빈 이름");
            });
        }
    }

    // ------------------------------------------------------------------ 내장 실행 파일
    static class BinariesTests
    {
        static BinarySet Synthetic(Func<string, byte[]> bytesFor)
        {
            var exe = Encoding.ASCII.GetBytes("MZ fake exe");
            var dll = Encoding.ASCII.GetBytes("fake dll");
            var set = new BinarySet
            {
                BundleId = "synthetic0000000",
                Items = new[]
                {
                    new BinaryItem { FileName = "tun2proxy-bin.exe", Sha256 = Binaries.Sha256(exe) },
                    new BinaryItem { FileName = "wintun.dll", Sha256 = Binaries.Sha256(dll) },
                },
            };
            set.Source = it => bytesFor != null ? bytesFor(it.FileName) : (it.FileName.EndsWith(".exe") ? exe : dll);
            return set;
        }

        internal static void Junction(string link, string target)
        {
            var cmd = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "cmd.exe");
            var psi = new ProcessStartInfo(cmd, "/c mklink /J \"" + link + "\" \"" + target + "\"")
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
            using (var p = Process.Start(psi))
            {
                p.StandardOutput.ReadToEnd();
                p.WaitForExit(10000);
                A.Eq(0, p.ExitCode, "mklink /J");
            }
        }

        public static void Add(Action<string, Action> T)
        {
            T("내장: 처음 풀고, 쥔 동안 쓰기·지우기·폴더 이름 바꾸기가 막힌다", () =>
            {
                var root = TestMain.Scratch("bin1");
                string err;
                using (var h = Binaries.Ensure(Synthetic(null), root, false, null, out err))
                {
                    A.True(h != null, "확인됨: " + err);
                    var exe = Path.Combine(root, "synthetic0000000", "tun2proxy-bin.exe");
                    A.Eq(exe, h.ExePath, "exe 경로");
                    A.Throws<IOException>(() => File.WriteAllText(exe, "evil"), "쓰기");
                    A.Throws<IOException>(() => File.Delete(exe), "지우기");
                    A.Throws<IOException>(() => Directory.Move(Path.Combine(root, "synthetic0000000"), Path.Combine(root, "moved")), "폴더 이름 바꾸기");
                    A.Throws<IOException>(() => Directory.Move(root, root + "-moved"), "위 폴더 이름 바꾸기");
                }
            });

            T("내장: 바뀐 파일은 다시 풀어 되돌린다", () =>
            {
                var root = TestMain.Scratch("bin2");
                string err;
                Binaries.Ensure(Synthetic(null), root, false, null, out err).Dispose();
                var exe = Path.Combine(root, "synthetic0000000", "tun2proxy-bin.exe");
                File.WriteAllText(exe, "swapped by malware");
                var logs = new List<string>();
                using (var h = Binaries.Ensure(Synthetic(null), root, false, logs.Add, out err))
                {
                    A.True(h != null, "다시 풀어 확인됨: " + err);
                    A.True(logs.Any(l => l.Contains("SHA-256 이 다릅니다")), "바뀐 것을 알아챔");
                }
                A.Eq("MZ fake exe", File.ReadAllText(exe), "원래 내용");
            });

            T("내장: 폴더에 끼워 넣은 DLL 은 지우고 다시 푼다", () =>
            {
                var root = TestMain.Scratch("bin3");
                string err;
                Binaries.Ensure(Synthetic(null), root, false, null, out err).Dispose();
                var dir = Path.Combine(root, "synthetic0000000");
                File.WriteAllText(Path.Combine(dir, "version.dll"), "planted");
                File.WriteAllText(Path.Combine(dir, "route.exe"), "planted");
                var logs = new List<string>();
                using (var h = Binaries.Ensure(Synthetic(null), root, false, logs.Add, out err))
                {
                    A.True(h != null, "확인됨: " + err);
                    A.True(logs.Any(l => l.Contains("낯선 파일")), "낯선 파일을 알아챔");
                    A.Eq(2, Directory.GetFileSystemEntries(dir).Length, "두 파일만");
                }
            });

            T("내장: 내장본의 해시가 다르면 풀지도 띄우지도 않는다", () =>
            {
                var root = TestMain.Scratch("bin4");
                string err;
                var h = Binaries.Ensure(Synthetic(n => Encoding.ASCII.GetBytes("corrupted")), root, false, null, out err);
                A.True(h == null, "거절");
                A.True(err.Contains("내장본 해시"), "까닭: " + err);
                A.True(!Directory.Exists(Path.Combine(root, "synthetic0000000")), "풀지 않음");
            });

            T("내장: 실제 tun2proxy 0.8.7 — 풀고 쥔 채 --version 이 돈다", () =>
            {
                var root = TestMain.Scratch("bin-real");
                string err;
                var set = BinarySet.Embedded();
                using (var h = Binaries.Ensure(set, root, false, null, out err))
                {
                    A.True(h != null, "확인됨: " + err);
                    A.Eq(Embedded.BundleId, Path.GetFileName(h.Dir), "폴더 이름 = 묶음 id");
                    var psi = new ProcessStartInfo(h.ExePath, "--version") { UseShellExecute = false, RedirectStandardOutput = true, CreateNoWindow = true };
                    using (var p = Process.Start(psi))
                    {
                        var o = p.StandardOutput.ReadToEnd();
                        p.WaitForExit(10000);
                        A.True(o.Contains("0.8.7"), "판: " + o.Trim());
                    }
                    var t = TestMain.OutDir;
                }
            });

            T("내장: 확인한 뒤 위 폴더의 접합점을 바꿔도 띄울 경로는 확인한 파일이다", () =>
            {
                // 관리자 도우미가 쓰는 bin 폴더는 사용자 폴더 아래다 — 사용자(보통 권한)의 프로그램이 그것을 접합점으로 두고
                // 확인이 끝난 뒤 대상을 바꾸면, 글로 된 경로가 끼워 넣은 exe 를 가리킨다.
                // .NET Framework 의 재귀 지우기는 접합점에서 DeleteVolumeMountPoint 를 불러 실패한다 — 접합점은 따로 걷는다.
                var stale = Path.Combine(TestMain.ScratchRoot, "bin-junction", "bin");
                if (Directory.Exists(stale)) Directory.Delete(stale);
                var root = TestMain.Scratch("bin-junction");
                var realA = Path.Combine(root, "A");
                var realB = Path.Combine(root, "B");
                Directory.CreateDirectory(realA);
                Directory.CreateDirectory(Path.Combine(realB, "synthetic0000000"));
                File.WriteAllText(Path.Combine(realB, "synthetic0000000", "tun2proxy-bin.exe"), "MZ planted");
                File.WriteAllText(Path.Combine(realB, "synthetic0000000", "wintun.dll"), "planted dll");
                var link = Path.Combine(root, "bin");
                Junction(link, realA);
                string err;
                try
                {
                    using (var h = Binaries.Ensure(Synthetic(null), link, false, null, out err))
                    {
                        A.True(h != null, "확인됨: " + err);
                        Directory.Delete(link);          // 접합점만 지운다
                        Junction(link, realB);
                        A.Eq("MZ fake exe", File.ReadAllText(h.ExePath), "띄울 exe 는 확인한 그것: " + h.ExePath);
                        A.True(h.ExePath.StartsWith(realA, StringComparison.OrdinalIgnoreCase), "실제 경로: " + h.ExePath);
                    }
                }
                finally { if (Directory.Exists(link)) Directory.Delete(link); }
            });

            T("내장: 사용자 소유 폴더는 '잠김' 이 아니다", () =>
            {
                var d = TestMain.Scratch("lock");
                string why;
                A.True(!Binaries.IsLockedDown(d, out why), "잠기지 않음");
                A.True(why.Contains("주인"), "까닭: " + why);
            });
        }
    }

    // ------------------------------------------------------------------ 자식·Ctrl+C
    static class ChildTests
    {
        public static void Add(Action<string, Action> T)
        {
            T("자식: Ctrl+C 를 받아 정리하고 끝난다", () =>
            {
                var d = TestMain.Scratch("ctrlc");
                var marker = Path.Combine(d, "marker.txt");
                var lines = new List<string>();
                string err;
                var c = ChildProcess.Start(Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), new List<string> { "wait", marker }, d,
                    (src, l) => { lock (lines) lines.Add(l); }, out err);
                A.True(c != null, "띄움: " + err);
                int exitCode = -99;
                c.Exited += code => exitCode = code;
                A.WaitUntil(() => { lock (lines) return lines.Contains("ready"); }, 5000, "ready");
                A.True(c.InKillOnCloseJob, "작업 개체(KILL_ON_JOB_CLOSE) 안에 들었다");
                string detail;
                bool clean = c.StopGracefully(5000, out detail);
                A.True(clean, "5초 안에 끝남: " + detail);
                A.Eq("clean 0", File.ReadAllText(marker), "표지(CTRL_C_EVENT=0)");
                A.WaitUntil(() => exitCode == 0, 3000, "끝 코드 0");
                lock (lines) A.True(lines.Contains("cleaned up"), "출력 끝까지 받음");
                c.Dispose();
                A.True(c.HasExited, "닫은 뒤에도 묻기는 던지지 않는다");
            });

            T("자식: 도우미가 죽으면 작업 개체가 tun2proxy 도 끝낸다(다음 실행이 '다른 tun2proxy' 로 막히지 않게)", () =>
            {
                var d = TestMain.Scratch("job");
                var marker = Path.Combine(d, "marker.txt");
                var me = Application.ExecutablePath;
                var psi = new ProcessStartInfo(me, CommandLine.Join(new[] { "--job-parent", Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), marker }))
                { UseShellExecute = false, RedirectStandardOutput = true, CreateNoWindow = true };
                using (var parent = Process.Start(psi))
                {
                    Process grand = null;
                    try
                    {
                        var line = parent.StandardOutput.ReadLine() ?? "";
                        var parts = line.Split(' ');
                        A.True(parts.Length == 4 && parts[0] == "child", "손자 번호: " + line);
                        A.Eq("True", parts[3], "손자가 작업 개체에 들었다");
                        grand = Process.GetProcessById(int.Parse(parts[1])); // 핸들을 쥐어 번호가 다시 쓰이지 않게
                        A.True(!grand.HasExited, "손자가 떠 있다");
                        parent.Kill();                      // 도우미가 죽는다(제가 띄운 시험 자식만)
                        A.True(parent.WaitForExit(5000), "대역이 끝남");
                        A.True(grand.WaitForExit(5000), "손자(tun2proxy 대역)도 끝남");
                        A.True(!File.Exists(marker), "Ctrl+C 정리가 아니라 작업 개체가 끝냈다");
                    }
                    finally
                    {
                        if (!parent.HasExited) parent.Kill();
                        if (grand != null) { if (!grand.HasExited) grand.Kill(); grand.Dispose(); }
                    }
                }
            });

            T("자식: Ctrl+C 를 듣지 않으면 강제로 끝낸다", () =>
            {
                var d = TestMain.Scratch("ctrlc2");
                var lines = new List<string>();
                string err;
                var c = ChildProcess.Start(Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), new List<string> { "ignore" }, d,
                    (src, l) => { lock (lines) lines.Add(l); }, out err);
                A.True(c != null, "띄움: " + err);
                A.WaitUntil(() => { lock (lines) return lines.Contains("ready"); }, 5000, "ready");
                string detail;
                var sw = Stopwatch.StartNew();
                bool clean = c.StopGracefully(1500, out detail);
                A.True(!clean, "제때 끝나지 않음");
                A.True(sw.ElapsedMilliseconds >= 1400, "기다림");
                lock (lines) A.True(lines.Any(l => l.StartsWith("ctrl event 0")), "Ctrl+C 는 받았다");
                c.Kill();
                A.True(c.HasExited, "강제 종료됨");
                c.Dispose();
            });

            T("자식: 띄우자마자 끝나 알림이 구독보다 먼저 와도 늦게 구독한 쪽이 받는다(컨트롤러가 '연결 중' 에 멈추지 않게)", () =>
            {
                var d = TestMain.Scratch("early-exit");
                string err;
                var c = ChildProcess.Start(Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), new List<string> { "args", "x" }, d, null, out err);
                A.True(c != null, "띄움: " + err);
                A.WaitUntil(() => c.ExitDelivered, 5000, "끝났다는 알림을 냈다(구독자 없이)");
                int got = -99, calls = 0;
                c.Exited += code => { got = code; calls++; };
                A.Eq(1, calls, "늦게 구독해도 한 번 받는다");
                A.Eq(0, got, "끝 코드");
                c.Dispose();
            });

            T("자식: 이 프로세스는 Ctrl+C 를 보낸 뒤에도 살아 있다", () =>
            {
                // 위 둘이 이 프로세스도 그 콘솔에 붙였다 뗐다 — 여기까지 왔으면 함께 받지 않았다.
                A.True(true, "");
            });
        }
    }

    // ------------------------------------------------------------------ 가짜 SOCKS5·HTTP
    static class ProbeTests
    {
        static int Serve(byte[] reply, bool closeImmediately, List<byte[]> received)
        {
            var l = new TcpListener(IPAddress.Loopback, 0);
            l.Start();
            int port = ((IPEndPoint)l.LocalEndpoint).Port;
            Task.Run(() =>
            {
                using (var c = l.AcceptTcpClient())
                {
                    var s = c.GetStream();
                    s.ReadTimeout = 3000;
                    if (!closeImmediately)
                    {
                        var buf = new byte[256];
                        int n = 0;
                        try { n = s.Read(buf, 0, buf.Length); } catch (IOException) { }
                        lock (received) received.Add(buf.Take(n).ToArray());
                        if (reply != null) s.Write(reply, 0, reply.Length);
                        Thread.Sleep(100);
                    }
                }
                l.Stop();
            });
            return port;
        }

        public static void Add(Action<string, Action> T)
        {
            T("확인: SOCKS5 05 00 이면 응답함, 인사는 05 01 00", () =>
            {
                var got = new List<byte[]>();
                var port = Serve(new byte[] { 5, 0 }, false, got);
                var r = NetState.Probe("127.0.0.1", port, ProxyKind.Socks5, 2000);
                A.True(r.Ok, "Ok: " + r.Detail);
                A.WaitUntil(() => { lock (got) return got.Count == 1; }, 2000, "받음");
                A.Eq("05-01-00", BitConverter.ToString(got[0]), "인사");
            });
            T("확인: SOCKS5 05 FF·끊김·닫힌 포트는 응답 없음", () =>
            {
                var r = NetState.Probe("127.0.0.1", Serve(new byte[] { 5, 0xFF }, false, new List<byte[]>()), ProxyKind.Socks5, 2000);
                A.True(!r.Ok && r.Detail.Contains("05 ff"), "05 FF: " + r.Detail);
                r = NetState.Probe("127.0.0.1", Serve(null, true, new List<byte[]>()), ProxyKind.Socks5, 2000);
                A.True(!r.Ok, "바로 끊김: " + r.Detail);
                var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); int dead = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop();
                // 윈도는 루프백의 닫힌 포트에도 SYN 을 몇 번 다시 보내 2초쯤 걸린다 — 거부나 시간 초과 둘 다 '응답 없음'.
                r = NetState.Probe("127.0.0.1", dead, ProxyKind.Socks5, 5000);
                A.True(!r.Ok && (r.Detail == "연결 거부" || r.Detail == "연결 시간 초과"), "닫힌 포트: " + r.Detail);
                TestMain.Say("      닫힌 포트: " + r.Detail);
            });
            T("확인: HTTP 프록시는 HTTP/1.x 답이면 응답함", () =>
            {
                var got = new List<byte[]>();
                var r = NetState.Probe("127.0.0.1", Serve(Encoding.ASCII.GetBytes("HTTP/1.1 403 Forbidden\r\n\r\n"), false, got), ProxyKind.Http, 2000);
                A.True(r.Ok, "Ok: " + r.Detail);
                A.True(Encoding.ASCII.GetString(got[0]).StartsWith("CONNECT 127.0.0.1:9 HTTP/1.1"), "CONNECT 줄");
                r = NetState.Probe("127.0.0.1", Serve(new byte[] { 5, 0 }, false, new List<byte[]>()), ProxyKind.Http, 2000);
                A.True(!r.Ok, "HTTP 가 아닌 답: " + r.Detail);
            });
            T("확인: 핫스팟 손님 주소 판정", () =>
            {
                A.True(NetState.IsHotspotGuest(IPAddress.Parse("192.168.49.60")), ".60");
                A.True(!NetState.IsHotspotGuest(IPAddress.Parse("192.168.49.1")), ".1 은 폰");
                A.True(!NetState.IsHotspotGuest(IPAddress.Parse("192.168.49.255")), ".255");
                A.True(!NetState.IsHotspotGuest(IPAddress.Parse("192.168.48.60")), "다른 대역");
            });
        }
    }

    // ------------------------------------------------------------------ 경로
    static class RouteTests
    {
        static RouteRow R(string dest, string mask, string gw, uint ifx, uint metric)
        {
            return new RouteRow { Dest = RouteRow.Parse(dest), Mask = RouteRow.Parse(mask), NextHop = RouteRow.Parse(gw), IfIndex = ifx, Metric = metric };
        }

        static ConnectionState State(params string[] bypass)
        {
            var st = new ConnectionState { TunName = "USBTether" };
            st.DefaultsBefore.Add(R("0.0.0.0", "0.0.0.0", "192.168.49.1", 12, 35));
            st.Bypass.AddRange(bypass);
            return st;
        }

        public static void Add(Action<string, Action> T)
        {
            var live = new HashSet<uint> { 1, 12, 50 };
            T("경로: TUN 의 기본 경로만 지운다(같은 10.0.0.1 이라도 다른 어댑터면 그대로)", () =>
            {
                var table = new List<RouteRow>
                {
                    R("0.0.0.0", "0.0.0.0", "192.168.49.1", 12, 35),
                    R("0.0.0.0", "0.0.0.0", "10.0.0.1", 50, 6),
                    R("0.0.0.0", "0.0.0.0", "10.0.0.1", 7, 25), // 집 공유기가 10.0.0.1
                };
                var plan = Routes.CleanupPlan(table, null, 50, live, false, null);
                A.Eq(1, plan.Count, "하나");
                // 'if 50' 이 없으면 route.exe 가 어댑터 7 의 10.0.0.1 기본 경로까지 지운다.
                A.Eq("delete 0.0.0.0 mask 0.0.0.0 10.0.0.1 if 50", string.Join(" ", plan[0].RouteArgs().ToArray()), "인자");
                A.Eq(0, Routes.CleanupPlan(table, null, -1, live, false, null).Count, "TUN 이 없으면 지울 것 없음");
            });
            T("경로: tun2proxy 가 떠 있으면 아무것도 건드리지 않는다", () =>
            {
                var table = new List<RouteRow> { R("0.0.0.0", "0.0.0.0", "10.0.0.1", 50, 6) };
                var notes = new List<string>();
                A.Eq(0, Routes.CleanupPlan(table, State(), 50, live, true, notes).Count, "없음");
                A.True(notes.Count == 1, "까닭을 적음");
            });
            T("경로: 우리가 넘긴 우회 경로만 지운다", () =>
            {
                var table = new List<RouteRow>
                {
                    R("0.0.0.0", "0.0.0.0", "192.168.49.1", 12, 35),
                    R("1.2.3.0", "255.255.255.0", "192.168.49.1", 12, 1),
                    R("1.2.3.0", "255.255.255.0", "172.16.0.1", 9, 1),   // 다른 게이트웨이 — 사용자의 것
                    R("5.6.7.8", "255.255.255.255", "192.168.49.1", 12, 1),
                };
                var plan = Routes.CleanupPlan(table, State("1.2.3.9/24", "5.6.7.8"), 50, live, false, null);
                var lines = plan.Select(p => string.Join(" ", p.RouteArgs().ToArray())).ToList();
                A.Eq(2, lines.Count, "둘: " + string.Join(" | ", lines.ToArray()));
                A.True(lines.Contains("delete 1.2.3.0 mask 255.255.255.0 192.168.49.1 if 12"), "1.2.3.0/24");
                A.True(lines.Contains("delete 5.6.7.8 mask 255.255.255.255 192.168.49.1 if 12"), "5.6.7.8");
            });
            T("경로: 기본 경로가 다 사라졌으면 연결 전의 것을 metric 200 으로 되살린다", () =>
            {
                var table = new List<RouteRow> { R("192.168.49.0", "255.255.255.0", "0.0.0.0", 12, 35) };
                var plan = Routes.CleanupPlan(table, State(), -1, live, false, null);
                A.Eq(1, plan.Count, "하나");
                A.Eq("add 0.0.0.0 mask 0.0.0.0 192.168.49.1 metric 200 if 12", string.Join(" ", plan[0].RouteArgs().ToArray()), "인자");
                var notes = new List<string>();
                A.Eq(0, Routes.CleanupPlan(table, State(), -1, new HashSet<uint> { 1 }, false, notes).Count, "어댑터가 없으면 하지 않음");
                A.True(notes.Count == 1, "까닭");
                table.Add(R("0.0.0.0", "0.0.0.0", "192.168.49.1", 12, 200));
                A.Eq(0, Routes.CleanupPlan(table, State(), -1, live, false, null).Count, "이미 있으면 하지 않음");
            });
            T("경로: 되살릴 때 어댑터 번호와 GUID 를 함께 견준다 — 그 번호가 다른 어댑터에 돌아갔으면 넣지 않는다, 옛 상태 파일(GUID 없음)은 번호만", () =>
            {
                const string g = "{11111111-2222-3333-4444-555555555555}", other = "{99999999-2222-3333-4444-555555555555}";
                var table = new List<RouteRow> { R("192.168.49.0", "255.255.255.0", "0.0.0.0", 12, 35) };
                var st = State();
                st.DefaultsBefore[0].AdapterId = g;
                A.Eq(1, Routes.CleanupPlan(table, st, -1, live, new Dictionary<uint, string> { { 12, g.ToUpperInvariant() } }, false, null).Count, "같은 어댑터");
                var notes = new List<string>();
                A.Eq(0, Routes.CleanupPlan(table, st, -1, live, new Dictionary<uint, string> { { 12, other } }, false, notes).Count, "번호는 같고 다른 어댑터");
                A.True(notes.Any(n => n.Contains("다른 어댑터")), "까닭: " + string.Join("|", notes.ToArray()));
                A.Eq(0, Routes.CleanupPlan(table, st, -1, live, new Dictionary<uint, string>(), false, null).Count, "GUID 를 모르면 넣지 않음");
                A.Eq(1, Routes.CleanupPlan(table, State(), -1, live, new Dictionary<uint, string> { { 12, other } }, false, null).Count, "옛 상태 파일은 번호만");
                var back = ConnectionState.Parse(st.Serialize());
                A.Eq(g, back.DefaultsBefore[0].AdapterId, "GUID 를 쓰고 읽음");
                A.Eq(0, ConnectionState.Parse("default=192.168.49.1 12 35 {nope}\n").DefaultsBefore.Count, "틀린 넷째 칸은 버림");
                A.Eq(0, ConnectionState.Parse("default=192.168.49.1 12 35 " + g + " x\n").DefaultsBefore.Count, "다섯 칸은 버림");
                A.Eq(1, ConnectionState.Parse("default=192.168.49.1 12 35\n").DefaultsBefore.Count, "옛 꼴(세 칸)");
            });
            T("경로: 상태 파일 쓰고 읽기, 망가진 줄은 버림", () =>
            {
                var st = State("1.2.3.0/24");
                var r = ConnectionState.Parse(st.Serialize() + "default=evil\nbypass=1.2.3.4/99\ngarbage\n");
                A.Eq(st.Serialize(), r.Serialize(), "같음");
            });
            T("경로: 이 PC 의 경로 표를 읽는다(읽기만)", () =>
            {
                var t = Routes.ReadTable();
                A.True(t.Count > 0, "줄이 있다");
                A.True(t.Any(r => r.IsDefault), "기본 경로가 있다");
                var tun = NetState.IfIndexOf("USBTether");
                var outside = Routes.DefaultsOutside(t, tun);
                A.True(outside.All(r => tun < 0 || r.IfIndex != (uint)tun), "TUN 밖만");
                TestMain.Say("      경로 " + t.Count + "줄, TUN 밖 기본 경로: " + string.Join(", ", outside.Select(r => r.ToString()).ToArray()));
            });
        }
    }

    // ------------------------------------------------------------------ 그 밖
    static class MiscTests
    {
        public static void Add(Action<string, Action> T)
        {
            T("툴팁: 63자를 넘지 않는다", () =>
            {
                A.Eq("Web 테더 — 연결됨", StatusText.Tooltip(TetherState.Connected, "무시됨"), "연결됨");
                var t = StatusText.Tooltip(TetherState.Error, new string('가', 100));
                A.True(t.Length <= 63, "길이 " + t.Length);
                foreach (TetherState s in Enum.GetValues(typeof(TetherState)))
                    A.True(StatusText.Title(s).Length > 0, "제목 " + s);
            });
            T("로그: 1 MB(여기서는 2 KB)를 넘으면 .1 로 돌린다, 색 부호를 걷는다", () =>
            {
                var d = TestMain.Scratch("log");
                var f = Path.Combine(d, "x.log");
                var log = new Log(f, 2048);
                for (int i = 0; i < 100; i++) log.Write("t", "줄 " + i + " \x1b[32mINFO\x1b[0m\tend");
                A.True(File.Exists(f + ".1"), ".1 이 있다");
                A.True(new FileInfo(f).Length <= 2048, "크기 상한");
                A.True(!File.ReadAllText(f).Contains("\x1b"), "색 부호 없음");
                A.Eq(100, log.Snapshot().Length, "고리 버퍼");
                for (int i = 0; i < Log.Capacity + 10; i++) log.Write("t", "x");
                A.Eq(Log.Capacity, log.Snapshot().Length, "고리 상한");
            });
            T("DLL 찾기: 시험 exe 도 도우미처럼 SetDefaultDllDirectories(System32) 를 건 채 돈다 — 이 어셈블리의 P/Invoke 는 System32 만", () =>
            {
                A.True(TestMain.hardened, "SetDefaultDllDirectories 성공");
                var attr = (System.Runtime.InteropServices.DefaultDllImportSearchPathsAttribute)Attribute.GetCustomAttribute(
                    typeof(SystemOps).Assembly, typeof(System.Runtime.InteropServices.DefaultDllImportSearchPathsAttribute));
                A.True(attr != null && attr.Paths == System.Runtime.InteropServices.DllImportSearchPath.System32, "어셈블리 특성");
            });
            T("핫스팟: 192.168.49.x 어댑터가 둘 이상이면 192.168.49.1 로 가는 어댑터가 이름을 본 어댑터여야 한다(GetBestInterface)", () =>
            {
                A.Eq((string)null, NetState.RouteCheck(12, () => 12), "같음");
                A.True(NetState.RouteCheck(12, () => 7) != null, "다른 어댑터");
                A.True(NetState.RouteCheck(12, () => -1) != null, "모름");
                A.True(NetState.RouteCheck(-1, () => 5) != null, "이름을 본 어댑터의 번호를 모름");
                A.True(NetState.RouteCheck(12, () => { throw new InvalidOperationException(); }) != null, "던짐");
                var hs = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = "DIRECT-UT-x", RouteMismatch = "다름" };
                A.Eq("다름", StatusText.Refusal(hs, "DIRECT-UT-", null), "접두어가 맞아도 거절");
                A.Eq("다름", StatusText.Refusal(hs, "DIRECT-UT-", "DIRECT-UT-x"), "허락한 이름이어도 거절");
                A.True(StatusText.HotspotLine(hs, "DIRECT-UT-").Contains("연결하지 않음"), "상태 창");
                int best = NetState.BestInterfaceToPhone();
                TestMain.Say("      이 PC 에서 192.168.49.1 로 가는 어댑터 번호(읽기만): " + best);
            });
            T("아이콘: 상태마다 그린다", () =>
            {
                foreach (TetherState s in Enum.GetValues(typeof(TetherState)))
                    using (var b = Icons.Draw(s, 16)) A.True(b.Width == 16, "16px " + s);
                A.True(Icons.For(TetherState.Error, 16) != null, "Icon");
            });
        }
    }

    // ------------------------------------------------------------------ 상태 기계(dry-run)
    static partial class ControllerTests
    {
        sealed class Rig
        {
            public Controller C;
            public DryRunOps Ops;
            public DryRunChildFactory F;
            public AppPaths Paths;
            public HotspotInfo Hotspot = Hs(Mine);
            public bool ProbeOk = true;
            public bool TunUp = true;
            public List<RouteRow> Table = new List<RouteRow>();
            public List<int> Others = new List<int>();
            public List<string> Notices = new List<string>();
            public List<string> Log = new List<string>();
        }

        static Rig Make(string name, Settings s)
        {
            return Make(name, s, null, null);
        }

        static Rig Make(string name, Settings s, ISystemOps ops, IChildFactory factory)
        {
            return MakeAt(AppPaths.Under(TestMain.Scratch("ctl-" + name)), s, ops, factory);
        }

        /// <summary>같은 상태 폴더로 도우미를 다시 띄운 꼴(다음 실행).</summary>
        static Rig Again(Rig old, Settings s)
        {
            return MakeAt(old.Paths, s, null, null);
        }

        static Rig MakeAt(AppPaths paths, Settings s, ISystemOps ops, IChildFactory factory)
        {
            var r = new Rig();
            r.Paths = paths;
            var log = new Log(r.Paths.LogFile, Log.MaxFileBytes, r.Paths.Root);
            log.Line += l => { lock (r.Log) r.Log.Add(l); };
            r.Ops = new DryRunOps(null);
            r.F = new DryRunChildFactory();
            var exe = Encoding.ASCII.GetBytes("MZ x");
            var set = new BinarySet
            {
                BundleId = "ctltest000000000",
                Items = new[] { new BinaryItem { FileName = "tun2proxy-bin.exe", Sha256 = Binaries.Sha256(exe) } },
                Source = it => exe,
            };
            r.C = new Controller(r.Paths, log, ops ?? r.Ops, factory ?? r.F, set, true, false, s ?? new Settings());
            r.C.HotspotFinder = p => r.Hotspot;
            r.C.Prober = (h, p, k, t) => new ProbeResult { Ok = r.ProbeOk, Detail = r.ProbeOk ? "ok" : "연결 거부" };
            r.C.Tun2proxyLister = () => new List<int>(r.Others);
            r.C.RouteReader = () => new List<RouteRow>(r.Table);
            r.C.TunPresent = n => r.TunUp;
            r.C.WifiWaitSeconds = 1;
            r.C.Notice += (t, x, w) => { lock (r.Notices) r.Notices.Add(t + ": " + x); };
            return r;
        }

        static void Run(Task t) { if (!t.Wait(20000)) throw new Exception("시간 초과"); }

        /// <summary>Ctrl+C 도 강제 종료도 듣지 않는 자식(시험이 Dead 를 세울 때까지 살아 있다).</summary>
        sealed class StubbornChild : IChild
        {
            public volatile bool Dead;
            public int Pid { get { return 910003; } }
            public bool HasExited { get { return Dead; } }
            public DateTime StartedAt { get; set; }
            public event Action<int> Exited { add { } remove { } }
            public bool StopGracefully(int timeoutMs, out string detail) { detail = null; return false; }
            public void Kill() { }
            public void Dispose() { }
        }

        sealed class StubbornFactory : IChildFactory
        {
            public StubbornChild Last;
            public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
            {
                error = null;
                Last = new StubbornChild { StartedAt = DateTime.UtcNow };
                return Last;
            }
        }

        /// <summary>Ctrl+C 에서 문이 열릴 때까지 막히는 자식 — 끊는 도중을 만든다.</summary>
        sealed class GatedChild : IChild
        {
            public readonly ManualResetEventSlim Gate = new ManualResetEventSlim(false);
            public int StopCalls;
            volatile bool exited;
            public int Pid { get { return 910001; } }
            public bool HasExited { get { return exited; } }
            public DateTime StartedAt { get; set; }
            public event Action<int> Exited { add { } remove { } }
            public bool StopGracefully(int timeoutMs, out string detail)
            {
                detail = null;
                Interlocked.Increment(ref StopCalls);
                Gate.Wait(10000);
                exited = true;
                return true;
            }
            public void Kill() { exited = true; }
            public void Dispose() { }
        }

        /// <summary>끝났는데(HasExited) 끝남 알림은 시험이 부를 때 낸다 — 알림이 UI 스레드에 닿기 전의 틈을 만든다.</summary>
        sealed class LateExitChild : IChild
        {
            public volatile bool Dead;
            public int Pid { get { return 910002; } }
            public bool HasExited { get { return Dead; } }
            public DateTime StartedAt { get; set; }
            public event Action<int> Exited;
            public bool StopGracefully(int timeoutMs, out string detail) { detail = null; Dead = true; return true; }
            public void Kill() { Dead = true; }
            public void Dispose() { }
            public void RaiseExited(int code) { var h = Exited; if (h != null) h(code); }
        }

        sealed class LateExitFactory : IChildFactory
        {
            public LateExitChild Last;
            public int Starts;
            public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
            {
                error = null;
                Interlocked.Increment(ref Starts);
                Last = new LateExitChild { StartedAt = DateTime.Now };
                return Last;
            }
        }

        sealed class GatedFactory : IChildFactory
        {
            public GatedChild Last;
            public int Starts;
            public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
            {
                error = null;
                Starts++;
                Last = new GatedChild { StartedAt = DateTime.Now };
                return Last;
            }
        }

        /// <summary>route 를 부르면 문이 열릴 때까지 막히고, 부른 스레드를 적는다.</summary>
        sealed class GateOps : ISystemOps
        {
            readonly ManualResetEventSlim gate;
            public int Entered;
            public int Thread;
            public GateOps(ManualResetEventSlim gate) { this.gate = gate; }
            public bool WlanConnect(string profile, out string output) { output = ""; return true; }
            public bool Route(RouteAction action, out string output)
            {
                Thread = System.Threading.Thread.CurrentThread.ManagedThreadId;
                Interlocked.Increment(ref Entered);
                gate.Wait(15000);
                output = "";
                return true;
            }
        }

        static IEnumerable<Control> AllControls(Control c)
        {
            foreach (Control x in c.Controls)
            {
                yield return x;
                foreach (var y in AllControls(x)) yield return y;
            }
        }

        public static void Add(Action<string, Action> T)
        {
            T("상태: 연결 → 연결됨 → 끊기(명령줄은 기록만)", () =>
            {
                var r = Make("basic", null);
                Run(r.C.ConnectAsync());
                A.Eq(TetherState.Connecting, r.C.State, "연결 중");
                A.Eq(1, r.F.CommandLines.Count, "한 번 띄움");
                A.True(r.F.CommandLines[0].EndsWith("--proxy socks5://192.168.49.1:1080 --tun USBTether --dns direct --dns-addr 1.0.0.1 --setup --verbosity info"), r.F.CommandLines[0]);
                A.True(File.Exists(r.Paths.StateFile), "상태 파일");
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                Run(r.C.DisconnectAsync());
                A.Eq(TetherState.Off, r.C.State, "꺼짐");
                A.True(!File.Exists(r.Paths.StateFile), "상태 파일을 지움");
                A.True(r.F.Last.HasExited, "자식 끝남");
            });

            T("상태: 핫스팟이 사라지면 끊고 다시 연결을 기다린다, 돌아오면 다시 띄운다", () =>
            {
                var r = Make("lost", new Settings { ReconnectInterval = 3 });
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                var first = r.F.Last;
                r.Hotspot = null;
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "대기");
                A.True(first.HasExited, "앞 자식을 끝냄");
                A.True(r.Notices.Any(n => n.Contains("핫스팟")), "알림");
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(TetherState.Waiting, r.C.State, "핫스팟이 없으면 계속 대기");
                r.Hotspot = Hs(Mine);
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 2, 5000, "다시 띄움");
                Run(r.C.DisconnectAsync());
            });

            T("상태: 자식이 저절로 끝나면 알리고 다시 연결", () =>
            {
                var r = Make("exit", new Settings { ReconnectInterval = 3 });
                Run(r.C.ConnectAsync());
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "대기");
                A.True(r.Notices.Any(n => n.Contains("코드 1")), "알림: " + string.Join("|", r.Notices.ToArray()));
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 2, 5000, "다시 띄움");
                Run(r.C.DisconnectAsync());
            });

            T("상태: 다시 연결 횟수 상한에 닿으면 멈추고 알린다", () =>
            {
                var r = Make("max", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r.ProbeOk = false;
                Run(r.C.ConnectAsync());
                A.Eq(TetherState.Waiting, r.C.State, "한 번은 대기");
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "오류");
                A.True(r.Notices.Any(n => n.StartsWith("다시 연결을 멈췄습니다")), "알림");
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
            });

            T("상태: 다른 tun2proxy 가 떠 있으면 띄우지 않는다(다시 하지도 않음)", () =>
            {
                var r = Make("other", null);
                r.Others.Add(4242);
                Run(r.C.ConnectAsync());
                A.Eq(TetherState.Error, r.C.State, "오류");
                A.True(r.C.Detail.Contains("다른 tun2proxy") && r.C.Detail.Contains("PID 4242") && r.C.Detail.Contains("이전 판 도우미"), r.C.Detail);
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("PID 4242")), "로그에도 까닭");
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
            });

            T("상태: 핫스팟이 없고 Wi-Fi 프로필이 있으면 netsh 로 붙어 본다(기록만)", () =>
            {
                var r = Make("wifi", new Settings { WifiProfile = "DIRECT-UT-내 폰", Reconnect = false });
                r.Hotspot = null;
                Run(r.C.ConnectAsync());
                A.True(r.Ops.Calls.Any(c => c == "netsh wlan connect name=\"DIRECT-UT-내 폰\""), string.Join("|", r.Ops.Calls.ToArray()));
                A.Eq(TetherState.Error, r.C.State, "그래도 없으면 오류");
            });

            T("상태: 설정이 틀리면 띄우지 않는다", () =>
            {
                var r = Make("invalid", new Settings { TunName = "bad name" });
                Run(r.C.ConnectAsync());
                A.Eq(TetherState.Error, r.C.State, "오류");
                A.True(r.C.Detail.Contains("TUN"), r.C.Detail);
            });

            T("상태: 연결하는 도중(폰 프록시 확인) 끊으면 tun2proxy 를 띄우지 않는다", () =>
            {
                var r = Make("cancel", null);
                var gate = new ManualResetEventSlim(false);
                r.C.Prober = (h, p, k, t) => { gate.Wait(10000); return new ProbeResult { Ok = true, Detail = "ok" }; };
                var task = r.C.ConnectAsync();
                A.WaitUntil(() => r.C.Detail == "폰 프록시 확인", 5000, "확인 중");
                Run(r.C.DisconnectAsync());
                A.Eq(TetherState.Off, r.C.State, "곧바로 꺼짐");
                gate.Set();
                Run(task);
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음: " + string.Join("|", r.F.CommandLines.ToArray()));
                A.Eq(TetherState.Off, r.C.State, "꺼진 채");
                A.True(!r.C.ChildRunning, "자식 없음");
            });

            T("상태: 상태 창의 '끊기' 는 연결 중에도 끊는다", () =>
            {
                var r = Make("cancel-ui", null);
                var gate = new ManualResetEventSlim(false);
                r.C.Prober = (h, p, k, t) => { gate.Wait(10000); return new ProbeResult { Ok = true, Detail = "ok" }; };
                var task = r.C.ConnectAsync();
                A.WaitUntil(() => r.C.Detail == "폰 프록시 확인", 5000, "확인 중");
                // 컨트롤을 만들면 이 스레드에 WinForms 문맥이 깔린다 — 뒤 시험의 await 가 펌프 없는 이 스레드로 돌아와 멈추지 않게 되돌린다.
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new StatusForm(r.C) { ProbeEnabled = false })
                    {
                        f.RefreshView();
                        var b = AllControls(f).OfType<Button>().First(x => x.Text == "끊기");
                        typeof(Button).GetMethod("OnClick", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic)
                            .Invoke(b, new object[] { EventArgs.Empty });
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
                A.WaitUntil(() => r.C.State == TetherState.Off, 5000, "꺼짐");
                A.True(!File.Exists(OffMark(r)), "단추로 끊어도 파일에 남기지 않는다(메모리에만)");
                gate.Set();
                Run(task);
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
            });

            T("상태: '연결됨' 직후 죽기를 되풀이해도 다시 연결 횟수 상한에서 멈춘다", () =>
            {
                var r = Make("crashloop", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨 1");
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "대기 1/1");
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 2, 5000, "다시 띄움");
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨 2");
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State != TetherState.Connected, 5000, "끝남");
                Thread.Sleep(200);
                A.Eq(TetherState.Error, r.C.State, "상한(1)에서 멈춤 — " + r.C.Detail);
                A.True(r.Notices.Any(n => n.StartsWith("다시 연결을 멈췄습니다")), "알림");
            });

            T("상태: 끊은 뒤에도 tun2proxy 가 남아 걷지 못했으면 상태 파일을 남긴다", () =>
            {
                var r = Make("lingering", null);
                Run(r.C.ConnectAsync());
                A.True(File.Exists(r.Paths.StateFile), "상태 파일");
                r.Others.Add(4242); // 강제 종료가 늦은 자식이나 다른 tun2proxy
                Run(r.C.DisconnectAsync());
                A.True(File.Exists(r.Paths.StateFile), "걷지 않았으니 남김");
                r.Others.Clear();
                Run(r.C.StartupCheck());
                A.True(!File.Exists(r.Paths.StateFile), "다음에 걷고 지움");
            });

            T("상태: 시작할 때 지난번 흔적 — tun2proxy 가 없으면 걷고 상태 파일을 지운다", () =>
            {
                var r = Make("startup", null);
                var st = new ConnectionState { TunName = "USBTether" };
                st.DefaultsBefore.Add(new RouteRow { NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                st.Save(r.Paths.StateFile);
                Run(r.C.StartupCheck());
                A.True(!File.Exists(r.Paths.StateFile), "지움");
                // 가짜 표가 비어 있다 — 기본 경로가 없으니 되살리기를 '기록' 해야 한다. 어댑터 12 가 이 PC 에 없으면 되살리지 않고 까닭을 적는다.
                A.True(r.Ops.Calls.Any(c => c.Contains("add 0.0.0.0")) || r.Log.Any(l => l.Contains("어댑터")), "되살리기나 그 까닭");
                st.Save(r.Paths.StateFile);
                r.Others.Add(1);
                Run(r.C.StartupCheck());
                A.True(File.Exists(r.Paths.StateFile), "tun2proxy 가 떠 있으면 지우지 않음");
            });

            T("정리: 걷기를 막는 tun2proxy 는 이름이 아니라 우리가 띄운 것(그 핸들)으로 — 이름만 같은 다른 것은 TUN 이 있을 때만 막는다", () =>
            {
                var r = Make("cleanup-own", null);
                var st = new ConnectionState { TunName = "USBTether" };
                st.DefaultsBefore.Add(new RouteRow { NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                st.Save(r.Paths.StateFile);
                r.Others.Add(4242);
                r.TunUp = false;
                Run(r.C.StartupCheck());
                A.True(!File.Exists(r.Paths.StateFile), "이름만 tun2proxy 이고 TUN 이 없으면 걷는다");
                A.True(r.Log.Any(l => l.Contains("이름이 tun2proxy 인 다른 프로세스")), "적음");
                st.Save(r.Paths.StateFile);
                r.TunUp = true;
                Run(r.C.StartupCheck());
                A.True(File.Exists(r.Paths.StateFile), "TUN 이 있으면(배치 파일의 진짜 tun2proxy) 건드리지 않는다");

                // 끝내기가 듣지 않은 우리 자식 — 이름 목록이 비어도 걷지 않는다.
                var f = new StubbornFactory();
                var r2 = Make("cleanup-own-alive", null, null, f);
                Run(r2.C.ConnectAsync());
                A.True(File.Exists(r2.Paths.StateFile), "상태 파일");
                r2.TunUp = false;
                Run(r2.C.DisconnectAsync());
                A.True(File.Exists(r2.Paths.StateFile), "자식이 살아 있으면 걷지 않고 남김");
                A.True(r2.Log.Any(l => l.Contains("띄운 tun2proxy 가 아직 끝나지 않았습니다")), "까닭");
                f.Last.Dead = true;
                Run(r2.C.StartupCheck());
                A.True(!File.Exists(r2.Paths.StateFile), "끝난 뒤에는 걷는다");
            });

            T("상태: TUN 어댑터가 끝내 나타나지 않으면 시간 제한에서 끄고 걷고 오류", () =>
            {
                var r = Make("tun-timeout", new Settings { ReconnectInterval = 3 });
                r.TunUp = false;
                r.C.ConnectTimeoutSeconds = 3;
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connecting, r.C.State, "아직 연결 중");
                Thread.Sleep(1100);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "오류 — " + r.C.State + " " + r.C.Detail);
                A.True(r.C.Detail.Contains("TUN") && r.C.Detail.Contains("3초"), r.C.Detail);
                A.True(r.F.Last.HasExited, "tun2proxy 를 끝냄");
                A.WaitUntil(() => !r.C.CleanupPending, 5000, "걷기 끝");
                A.True(!File.Exists(r.Paths.StateFile), "걷고 상태 파일을 지움");
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "다시 하지 않음");
            });

            T("상태: 핫스팟이 사라져 끊는 사이 '끊기' 를 눌러도 한 번만 끊고 '꺼짐' 으로 남는다", () =>
            {
                var gated = new GatedFactory();
                var r = Make("double-stop", new Settings { ReconnectInterval = 3 }, null, gated);
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                r.Hotspot = null;
                Tick(r);                                   // 핫스팟 사라짐 → 끊기 시작(Ctrl+C 에서 막힘)
                A.WaitUntil(() => gated.Last.StopCalls == 1, 3000, "끊는 중");
                var d = r.C.DisconnectAsync();                // 사용자가 '끊기'
                Thread.Sleep(300);
                gated.Last.Gate.Set();
                Run(d);
                Thread.Sleep(300);
                A.Eq(1, gated.Last.StopCalls, "Ctrl+C 는 한 번");
                A.Eq(TetherState.Off, r.C.State, "꺼짐 — " + r.C.Detail);
                lock (r.Log) A.True(!r.Log.Any(l => l.Contains("[상태]") && l.Contains("폰 핫스팟이 사라졌습니다")),
                                    "사용자가 끊은 뒤 '오류·대기' 로 덮지 않음: " + string.Join(" | ", r.Log.Where(l => l.Contains("[상태]")).ToArray()));
                A.Eq(1, r.Log.Count(l => l.Contains("핫스팟이 사라짐 — Ctrl+C")) + r.Log.Count(l => l.Contains("사용자가 끊음 — Ctrl+C")), "끊기 기록 하나");
                r.Hotspot = Hs(Mine);
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, gated.Starts, "다시 띄우지 않음");
            });

            T("상태: 경로 정리는 일꾼 스레드에서 — 도는 동안 부른 쪽이 막히지 않고, 끝나기 전에는 tun2proxy 를 띄우지 않는다", () =>
            {
                var gate = new ManualResetEventSlim(false);
                var ops = new GateOps(gate);
                var r = Make("cleanup-async", new Settings { TunName = "WTTestTun" }, ops, null);
                var st = new ConnectionState { TunName = "WTTestTun" };
                st.DefaultsBefore.Add(new RouteRow { NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                st.Bypass.Add("5.6.7.8");
                st.Save(r.Paths.StateFile);
                r.Table.Add(new RouteRow { Dest = RouteRow.Parse("5.6.7.8"), Mask = RouteRow.Parse("255.255.255.255"), NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 1 });
                r.Table.Add(new RouteRow { Dest = 0, Mask = 0, NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                var sw = Stopwatch.StartNew();
                var t = r.C.StartupCheck();
                A.True(sw.ElapsedMilliseconds < 1000, "곧바로 돌아옴(" + sw.ElapsedMilliseconds + " ms)");
                A.WaitUntil(() => ops.Entered > 0, 3000, "route 를 부름");
                A.True(ops.Thread != Thread.CurrentThread.ManagedThreadId, "부른 쪽과 다른 스레드");
                var c = r.C.ConnectAsync();
                Thread.Sleep(500);
                A.Eq(0, r.F.CommandLines.Count, "걷는 동안 띄우지 않음");
                Tick(r);
                A.Eq(0, r.F.CommandLines.Count, "Tick 도 기다림");
                gate.Set();
                Run(t);
                Run(c);
                A.Eq(1, r.F.CommandLines.Count, "걷기가 끝난 뒤 띄움");
                Run(r.C.DisconnectAsync());
            });

            T("상태: 연결 전부터 있던 같은 우회 경로는 걷을 때 지우지 않는다", () =>
            {
                var r = Make("kept-bypass", new Settings { TunName = "WTTestTun", Bypass = "1.2.3.0/24, 5.6.7.8" });
                Func<string, string, RouteRow> row = (dest, mask) => new RouteRow { Dest = RouteRow.Parse(dest), Mask = RouteRow.Parse(mask), NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 1 };
                r.Table.Add(new RouteRow { Dest = 0, Mask = 0, NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                r.Table.Add(row("1.2.3.0", "255.255.255.0")); // 사용자가 미리 둔 것
                Run(r.C.ConnectAsync());
                var st = ConnectionState.Load(r.Paths.StateFile);
                A.Eq("1.2.3.0/24", string.Join(",", st.KeptBypass.ToArray()), "이미 있던 것을 적음");
                r.Table.Add(row("5.6.7.8", "255.255.255.255"));  // tun2proxy 가 넣은 것
                Run(r.C.DisconnectAsync());
                var routes = r.Ops.Calls.Where(x => x.StartsWith("route ")).ToList();
                A.True(routes.Any(x => x.Contains("delete 5.6.7.8")), "넣은 것은 지움: " + string.Join(" | ", routes.ToArray()));
                A.True(!routes.Any(x => x.Contains("delete 1.2.3.0")), "미리 있던 것은 그대로: " + string.Join(" | ", routes.ToArray()));
                A.True(r.Log.Any(l => l.Contains("연결 전부터 있던")), "까닭을 적음");
            });

            T("상태: 상태 폴더의 bin 이 접합점이면 풀지 않고 오류", () =>
            {
                var r = Make("bin-junction", null);
                var elsewhere = TestMain.Scratch("bin-junction-target");
                BinariesTests.Junction(r.Paths.BinRoot, elsewhere);
                try
                {
                    Run(r.C.ConnectAsync());
                    A.Eq(TetherState.Error, r.C.State, "오류");
                    A.True(r.C.Detail.Contains("재분석"), r.C.Detail);
                    A.Eq(0, Directory.GetFileSystemEntries(elsewhere).Length, "대상 폴더에 아무것도 쓰지 않음");
                    A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                }
                finally { Directory.Delete(r.Paths.BinRoot); }
            });

            AddAutoConnect(T);
            AddSettling(T);
            AddSsid(T);
            AddWorkers(T);
        }

        static void AddAutoConnect(Action<string, Action> T)
        {
            T("자동 연결: 끊어 둔 표시가 없으면 실행할 때 연결한다 — '끝내기' 는 끊음이 아니다", () =>
            {
                var r = Make("auto", null);
                Run(r.C.AutoConnectAsync());
                A.Eq(1, r.F.CommandLines.Count, "띄움");
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                Run(r.C.ShutdownAsync());
                A.True(!File.Exists(OffMark(r)), "끝내기는 표시를 남기지 않음");
                var r2 = Again(r, null);
                Run(r2.C.AutoConnectAsync());
                A.Eq(1, r2.F.CommandLines.Count, "다음 실행도 연결");
                Run(r2.C.ShutdownAsync());
            });

            T("자동 연결: 사용자의 '끊기' 는 그 실행 동안만 — Tick 도 다시 연결하지 않고, '연결' 로 다시 붙고, 다음 실행은 저절로 연결한다", () =>
            {
                var r = Make("auto-off", new Settings { ReconnectInterval = 3 });
                Run(r.C.AutoConnectAsync());
                A.Eq(1, r.F.CommandLines.Count, "띄움");
                Run(r.C.DisconnectAsync());
                A.Eq(TetherState.Off, r.C.State, "꺼짐");
                A.True(!File.Exists(OffMark(r)), "표시 파일을 쓰지 않음");
                A.Eq(0, Directory.GetFiles(r.Paths.Root).Count(x => Path.GetFileName(x).IndexOf("disconnect", StringComparison.OrdinalIgnoreCase) >= 0), "끊음을 적은 파일 없음");
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "그 실행 동안은 Tick 도 다시 연결하지 않음");
                A.Eq(TetherState.Off, r.C.State, "꺼진 채");
                Run(r.C.ConnectAsync());
                A.Eq(2, r.F.CommandLines.Count, "'연결' 로 다시 붙음");
                Run(r.C.DisconnectAsync());

                var r2 = Again(r, null);
                Run(r2.C.AutoConnectAsync());
                A.Eq(1, r2.F.CommandLines.Count, "다시 실행하면 저절로 연결");
                Run(r2.C.ShutdownAsync());
            });

            T("자동 연결: 예전 판이 남긴 user-disconnected 는 읽지도 지우지도 않는다 — 있어도 저절로 연결한다", () =>
            {
                var r = Make("auto-legacy-mark", null);
                var mark = OffMark(r);
                const string body = "disconnected-by-user 2026-11-21 10:00:00\n";
                File.WriteAllText(mark, body);
                var when = new DateTime(2026, 11, 21, 10, 0, 0);
                File.SetLastWriteTime(mark, when);
                using (new FileStream(mark, FileMode.Open, FileAccess.Read, FileShare.None)) // 열려고 들면 공유 위반 — 그래도 연결해야 한다
                {
                    Run(r.C.AutoConnectAsync());
                    A.Eq(1, r.F.CommandLines.Count, "표시가 있어도 연결");
                    Run(r.C.DisconnectAsync());
                    Run(r.C.ConnectAsync());
                    Run(r.C.ShutdownAsync());
                }
                lock (r.Log) A.True(!r.Log.Any(l => l.Contains("끊어 둠") || l.Contains("user-disconnected")), "표시를 다루지 않음: " + string.Join(" | ", r.Log.ToArray()));
                A.True(File.Exists(mark), "지우지 않음");
                A.Eq(body, File.ReadAllText(mark), "내용 그대로");
                A.Eq(when, File.GetLastWriteTime(mark), "쓰지 않음");
            });

            T("자동 연결: 핫스팟 사라짐·다른 tun2proxy·다시 연결 포기는 끊음이 아니다(표시 없음)", () =>
            {
                var r = Make("auto-notoff", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                Run(r.C.AutoConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                r.Hotspot = null;
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "핫스팟 사라짐 → 대기");
                A.True(!File.Exists(OffMark(r)), "핫스팟 사라짐");
                Run(r.C.ShutdownAsync());

                var r2 = Again(r, new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r2.Others.Add(4242);
                Run(r2.C.AutoConnectAsync());
                A.Eq(TetherState.Error, r2.C.State, "다른 tun2proxy — 오류");
                A.Eq(0, r2.F.CommandLines.Count, "띄우지 않음");
                A.True(!File.Exists(OffMark(r)), "다른 tun2proxy");

                var r3 = Again(r, new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r3.C.StartupWaitSeconds = 0; // 너그러운 기다림 없이 — 늘 쓰는 규칙
                r3.ProbeOk = false;
                Run(r3.C.AutoConnectAsync());
                A.Eq(TetherState.Waiting, r3.C.State, "1/1 대기");
                Thread.Sleep(3100);
                Tick(r3);
                A.WaitUntil(() => r3.C.State == TetherState.Error, 5000, "포기");
                A.True(!File.Exists(OffMark(r)), "다시 연결 포기");
            });

            T("자동 연결: 시작 직후 핫스팟·폰 프록시가 없으면 횟수를 쓰지 않고 기다리다('다시 연결' 이 꺼져 있어도) 생기면 연결", () =>
            {
                var r = Make("auto-wait", new Settings { Reconnect = false, ReconnectInterval = 3 });
                r.C.StartupWaitSeconds = 30;
                r.C.StartupRetrySeconds = 1;
                r.Hotspot = null;
                Run(r.C.AutoConnectAsync());
                A.Eq(TetherState.Waiting, r.C.State, "대기 — " + r.C.Detail);
                A.True(r.C.Detail.Contains("기다리는 중"), r.C.Detail);
                A.Eq(0, r.C.Retries, "횟수를 쓰지 않음");
                A.Eq(ToggleAction.Disconnect, r.C.Toggle, "기다리는 동안 끊을 수 있다");
                Thread.Sleep(1100);
                Tick(r);
                A.Eq(TetherState.Waiting, r.C.State, "아직 없으면 계속");
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                r.Hotspot = Hs(Mine);
                r.ProbeOk = false;
                Thread.Sleep(1100);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Waiting && r.C.Detail.Contains("폰 프록시"), 5000, "폰 프록시도 기다림 — " + r.C.Detail);
                A.Eq(0, r.C.Retries, "횟수를 쓰지 않음(프록시)");
                r.ProbeOk = true;
                Thread.Sleep(1100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 1, 5000, "연결");
                // 띄운 뒤의 실패는 늘 쓰는 규칙 — '다시 연결' 이 꺼져 있으니 오류로 멈춘다(시작 직후 죽기를 끝없이 되풀이하지 않는다).
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "오류 — " + r.C.State + " " + r.C.Detail);
                lock (r.Notices) A.True(!r.Notices.Any(n => n.StartsWith("저절로")), "띄운 뒤의 실패는 '저절로 연결하지 못했습니다' 가 아니다: " + string.Join("|", r.Notices.ToArray()));
                Thread.Sleep(1100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "다시 띄우지 않음");
            });

            T("자동 연결: 기다림의 상한이 지나면 늘 쓰는 규칙 — '다시 연결' 이 꺼져 있으면 오류와 알림, 켜져 있으면 횟수를 센다", () =>
            {
                var r = Make("auto-cap", new Settings { Reconnect = false, ReconnectInterval = 3 });
                r.C.StartupWaitSeconds = 2;
                r.C.StartupRetrySeconds = 1;
                r.Hotspot = null;
                Run(r.C.AutoConnectAsync());
                A.Eq(TetherState.Waiting, r.C.State, "대기");
                Thread.Sleep(1100);
                Tick(r);
                A.Eq(TetherState.Waiting, r.C.State, "아직 기다림");
                Thread.Sleep(1400);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "상한 뒤 오류 — " + r.C.State + " " + r.C.Detail);
                A.True(r.Notices.Any(n => n.StartsWith("저절로 연결하지 못했습니다")), "알림: " + string.Join("|", r.Notices.ToArray()));
                A.True(!File.Exists(OffMark(r)), "표시 없음");

                var r2 = Make("auto-cap2", new Settings { Reconnect = true, ReconnectInterval = 3, ReconnectMax = 20 });
                r2.C.StartupWaitSeconds = 1;
                r2.C.StartupRetrySeconds = 1;
                r2.Hotspot = null;
                Run(r2.C.AutoConnectAsync());
                A.Eq(0, r2.C.Retries, "기다리는 동안 0");
                Thread.Sleep(1100);
                Tick(r2);
                A.WaitUntil(() => r2.C.Retries == 1, 5000, "상한 뒤 횟수 1 — " + r2.C.Detail);
                A.True(r2.C.Detail.EndsWith("1/20"), r2.C.Detail);
                A.Eq(TetherState.Waiting, r2.C.State, "계속 기다림(핫스팟이 없으면 횟수 없이)");
                Run(r2.C.DisconnectAsync());
            });
        }

        static void AddSettling(Action<string, Action> T)
        {
            T("상태: 끊은 뒤 경로를 걷는 동안 단추는 '정리 중'(누를 수 없음) — 그 사이 연결해도 걷기가 끝난 뒤에 띄운다", () =>
            {
                var gate = new ManualResetEventSlim(false);
                var ops = new GateOps(gate);
                var r = Make("settle", new Settings { TunName = "WTTestTun", Bypass = "5.6.7.8" }, ops, null);
                r.Table.Add(new RouteRow { Dest = 0, Mask = 0, NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                A.Eq(ToggleAction.Disconnect, r.C.Toggle, "연결됨 — 끊기");
                r.Table.Add(new RouteRow { Dest = RouteRow.Parse("5.6.7.8"), Mask = RouteRow.Parse("255.255.255.255"), NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 1 });
                var d = r.C.DisconnectAsync();
                A.WaitUntil(() => ops.Entered > 0, 5000, "걷는 중(route 에서 막힘)");
                A.True(!r.C.ChildRunning, "자식은 끝남");
                A.Eq(ToggleAction.Cleaning, r.C.Toggle, "걷는 동안 '정리 중' — 상태 " + r.C.State);
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new StatusForm(r.C) { ProbeEnabled = false })
                    {
                        f.RefreshView();
                        var b = AllControls(f).OfType<Button>().FirstOrDefault(x => x.Text == "정리 중");
                        A.True(b != null, "단추 글 '정리 중': " + string.Join(",", AllControls(f).OfType<Button>().Select(x => x.Text).ToArray()));
                        A.True(!b.Enabled, "누를 수 없음");
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
                StatusForm.DoToggle(r.C); // 낡은 글을 보고 눌러도 아무것도 하지 않는다
                Thread.Sleep(300);
                A.Eq(1, r.F.CommandLines.Count, "'정리 중' 에 누른 것은 무시");
                var c = r.C.ConnectAsync(); // 다른 길로 연결해도
                Thread.Sleep(500);
                A.Eq(1, r.F.CommandLines.Count, "걷는 동안 띄우지 않음");
                gate.Set();
                Run(d);
                Run(c);
                A.Eq(2, r.F.CommandLines.Count, "걷기가 끝난 뒤 띄움");
                A.True(r.C.ChildRunning, "새 자식");
                A.Eq(TetherState.Connecting, r.C.State, "끊기의 마무리가 새 시도를 '꺼짐' 으로 덮지 않음 — " + r.C.Detail);
                Run(r.C.DisconnectAsync());
                A.WaitUntil(() => r.C.Toggle == ToggleAction.Connect, 5000, "끝나면 연결");
            });

            T("상태: 자식이 끝났는데 그 알림이 아직이면 단추는 '연결' 이 아니고, 연결해도 그 자식을 걷기 전에 띄우지 않는다", () =>
            {
                var late = new LateExitFactory();
                var r = Make("late-exit", new Settings { ReconnectInterval = 3 }, null, late);
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                late.Last.Dead = true;                         // tun2proxy 가 끝났다 — 알림은 아직 UI 스레드에 닿지 않았다
                A.True(r.C.Toggle != ToggleAction.Connect, "알림 전에는 '연결' 이 아니다 — " + r.C.Toggle);
                Run(r.C.ConnectAsync());                       // 다른 길로 눌러도
                A.Eq(1, late.Starts, "그 자식을 걷기 전에 새 tun2proxy 를 띄우지 않음");
                late.Last.RaiseExited(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "알림 뒤 다시 연결 대기 — " + r.C.State + " " + r.C.Detail);
                A.WaitUntil(() => !r.C.CleanupPending, 5000, "걷기 끝");
                Run(r.C.DisconnectAsync());
                A.Eq(1, late.Starts, "끝까지 하나");
            });

            T("설정 창: 설정 파일을 쓰지 못하면 '저장하지 못했습니다' 이고 Controller 의 설정은 옛 값", () =>
            {
                var r = Make("settings-rollback", null);
                var elsewhere = TestMain.Scratch("settings-rollback-target");
                BinariesTests.Junction(r.Paths.SettingsFile, elsewhere); // 설정 파일 자리에 접합점 — SafeFs 가 쓰기를 거절한다
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new SettingsForm(r.C))
                    {
                        f.LoadFrom(new Settings { ReconnectMax = 9 });
                        A.True(!Pump(f.SaveAsync()), "저장 실패");
                        A.Eq(new Settings().ReconnectMax, r.C.Settings.ReconnectMax, "설정은 옛 값");
                    }
                }
                finally
                {
                    SynchronizationContext.SetSynchronizationContext(before);
                    Directory.Delete(r.Paths.SettingsFile);
                }
                A.Eq(0, Directory.GetFileSystemEntries(elsewhere).Length, "접합점 너머에 쓰지 않음");
            });

            T("상태: 시작할 때의 걷기 동안도 '정리 중', 끝나면 Changed 로 알려 단추가 '연결' 로 돌아온다", () =>
            {
                var gate = new ManualResetEventSlim(false);
                var ops = new GateOps(gate);
                var r = Make("settle-startup", new Settings { TunName = "WTTestTun" }, ops, null);
                var st = new ConnectionState { TunName = "WTTestTun" };
                st.DefaultsBefore.Add(new RouteRow { NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                st.Bypass.Add("5.6.7.8");
                st.Save(r.Paths.StateFile);
                r.Table.Add(new RouteRow { Dest = RouteRow.Parse("5.6.7.8"), Mask = RouteRow.Parse("255.255.255.255"), NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 1 });
                r.Table.Add(new RouteRow { Dest = 0, Mask = 0, NextHop = RouteRow.Parse("192.168.49.1"), IfIndex = 12, Metric = 35 });
                var seen = new List<ToggleAction>();
                r.C.Changed += () => { lock (seen) seen.Add(r.C.Toggle); };
                var t = r.C.StartupCheck();
                A.WaitUntil(() => ops.Entered > 0, 3000, "route 를 부름");
                A.Eq(ToggleAction.Cleaning, r.C.Toggle, "시작 걷기 동안 '정리 중'");
                gate.Set();
                Run(t);
                A.WaitUntil(() => { lock (seen) return seen.Contains(ToggleAction.Connect); }, 5000, "걷기가 끝나면 알림");
                A.Eq(ToggleAction.Connect, r.C.Toggle, "연결");
            });
        }
    }

    // ------------------------------------------------------------------ 화면 그림
    static class Shots
    {
        public static void Render(string dir, string scratch)
        {
            Directory.CreateDirectory(dir);
            var paths = AppPaths.Under(Path.Combine(scratch, "shots-home"));
            var log = new Log(paths.LogFile);
            for (int i = 0; i < 30; i++) log.Write(i % 3 == 0 ? "tun2proxy" : "상태", "보기 줄 " + i + " — [INFO tun2proxy] Beginning #" + i + " TCP 10.0.0.33:5" + i + " -> example.test:443");
            var ops = new DryRunOps(null);
            var s = new Settings();
            var ctl = new Controller(paths, log, ops, new DryRunChildFactory(), null, true, false, s);
            Func<HotspotInfo, StatusForm> status = hs => { var f = new StatusForm(ctl) { ProbeEnabled = false }; f.ShowHotspot(hs); return f; };
            var mine = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = "DIRECT-UT-시험폰" };

            ctl.ForceStateForTest(TetherState.Connected, "");
            Save(status(mine), dir, "status-connected.png");
            ctl.ForceStateForTest(TetherState.Waiting, "폰 프록시가 응답하지 않습니다(연결 거부) — 3/20");
            Save(status(mine), dir, "status-waiting.png");
            ctl.ForceStateForTest(TetherState.Waiting, "Wi-Fi 이름이 DIRECT-UT- 로 시작하지 않음('DIRECT-xy-Android_1234') — 기다리는 중");
            Save(status(new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.77", IfIndex = 12, Ssid = "DIRECT-xy-Android_1234" }), dir, "status-foreign-ssid.png");
            ctl.ForceStateForTest(TetherState.Error, "다른 tun2proxy 가 실행 중입니다. 먼저 끄십시오.");
            Save(status(null), dir, "status-error.png");

            var sf = new SettingsForm(ctl);
            Save(sf, dir, "settings.png");
            var sf2 = new SettingsForm(ctl);
            Save(sf2, dir, "settings-error.png", () =>
            {
                sf2.LoadFrom(new Settings { Kind = ProxyKind.Http, ProxyPort = 8282, ProxyHost = "192.168.49.256" });
                var unused = sf2.SaveAsync(); // 검사에서 걸려 곧바로 끝난다
            });
            var sf3 = new SettingsForm(ctl);
            Save(sf3, dir, "settings-http-hint.png", () => sf3.LoadFrom(new Settings { Kind = ProxyKind.Http, ProxyPort = 8282 }));
            Save(new LogForm(log, paths.LogFile), dir, "log.png");
            Save(new AboutForm(true), dir, "about.png");

            using (var strip = new Bitmap(5 * 40, 40))
            using (var g = Graphics.FromImage(strip))
            {
                g.Clear(Color.White);
                int x = 4;
                foreach (TetherState st in Enum.GetValues(typeof(TetherState)))
                {
                    using (var b16 = Icons.Draw(st, 16)) g.DrawImage(b16, x, 2);
                    using (var b32 = Icons.Draw(st, 32)) g.DrawImage(b32, x, 4 + 16);
                    x += 40;
                }
                strip.Save(Path.Combine(dir, "tray-icons.png"), ImageFormat.Png);
            }
            using (var dark = new Bitmap(5 * 24, 24))
            using (var g = Graphics.FromImage(dark))
            {
                g.Clear(Color.FromArgb(32, 32, 32));
                int x = 4;
                foreach (TetherState st in Enum.GetValues(typeof(TetherState)))
                {
                    using (var b16 = Icons.Draw(st, 16)) g.DrawImage(b16, x, 4);
                    x += 24;
                }
                dark.Save(Path.Combine(dir, "tray-icons-dark.png"), ImageFormat.Png);
            }
            TestMain.Say("툴팁: " + string.Join(" | ", Enum.GetValues(typeof(TetherState)).Cast<TetherState>().Select(st => StatusText.Tooltip(st, "폰 프록시가 응답하지 않습니다(연결 거부) — 3/20")).ToArray()));
        }

        static void Save(Form f, string dir, string name, Action afterShow = null)
        {
            // 보이지 않은 창은 자식 컨트롤을 그리지 않는다 — 화면 밖에 투명하게 잠깐 띄우고 곧바로 닫는다.
            f.StartPosition = FormStartPosition.Manual;
            f.Location = new Point(-20000, -20000);
            f.ShowInTaskbar = false;
            f.Opacity = 0;
            f.Show();
            Application.DoEvents();
            if (afterShow != null) afterShow();
            f.PerformLayout();
            Application.DoEvents();
            using (var bmp = new Bitmap(f.Width, f.Height))
            {
                f.DrawToBitmap(bmp, new Rectangle(0, 0, f.Width, f.Height));
                bmp.Save(Path.Combine(dir, name), ImageFormat.Png);
            }
            TestMain.Say(name + " " + f.Width + "x" + f.Height);
            f.Hide();
            f.Dispose();
        }
    }
}
