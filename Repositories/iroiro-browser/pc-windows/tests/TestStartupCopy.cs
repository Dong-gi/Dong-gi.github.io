// 시험 — '시작 시 실행' 사본이 옛 판일 때(2026-11-21 사용자 기기: 내려받기의 exe 를 바꿔도 로그온 때 옛 사본이 떴다).
// 작업 스케줄러·Program Files 는 건드리지 않는다 — 사본 자리는 스크래치, schtasks 는 가짜(읽기 시험 하나만 실제 /Query).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Principal;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether.Tests
{
    static partial class ControllerTests
    {
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern bool EnumWindows(EnumWindowsProc f, IntPtr l);
        delegate bool EnumWindowsProc(IntPtr h, IntPtr l);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern bool IsWindowVisible(IntPtr h);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);

        /// <summary>그 프로세스와 자손(WMI 로 찾는다)의 보이는 맨 위 창 — 'pid:이름'.</summary>
        static List<string> VisibleWindowsOfTree(int root)
        {
            var tree = new HashSet<int> { root };
            var names = new Dictionary<int, string>();
            using (var s = new System.Management.ManagementObjectSearcher("SELECT ProcessId, ParentProcessId, Name FROM Win32_Process"))
            {
                var rows = new List<Tuple<int, int, string>>();
                foreach (System.Management.ManagementObject o in s.Get())
                    using (o) rows.Add(Tuple.Create(Convert.ToInt32(o["ProcessId"]), Convert.ToInt32(o["ParentProcessId"]), (string)o["Name"]));
                bool grew = true;
                while (grew)
                {
                    grew = false;
                    foreach (var r in rows)
                        if (tree.Contains(r.Item2) && tree.Add(r.Item1)) grew = true;
                }
                foreach (var r in rows) if (tree.Contains(r.Item1)) names[r.Item1] = r.Item3;
            }
            var found = new List<string>();
            EnumWindows((h, l) =>
            {
                uint pid;
                GetWindowThreadProcessId(h, out pid);
                if (IsWindowVisible(h) && tree.Contains((int)pid)) found.Add(pid + ":" + (names.ContainsKey((int)pid) ? names[(int)pid] : "?"));
                return true;
            }, IntPtr.Zero);
            TestMain.Say("      프로세스 " + string.Join(",", names.Values.ToArray()) + " · 보이는 창 " + found.Count);
            return found;
        }

        /// <summary>
        /// dir 에 시작 스크립트와 가짜 exe(ctrlc-child.exe 를 WebTether.exe 이름으로 — 환경을 env-dump.txt 에 적는 콘솔 탐침)를 두고, env 를 이
        /// 프로세스에 잠깐 넣어 작업처럼(ShellExecute — 콘솔·표준 핸들 없이) conhost --headless 로 돌린다. exe 가 적은 줄들, 띄우지 않았으면 null.
        /// </summary>
        static string[] RunLauncher(string dir, byte[] script, Dictionary<string, string> env, bool checkWindows)
        {
            var exe = Path.Combine(dir, "WebTether.exe");
            if (!File.Exists(exe)) File.Copy(Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), exe);
            var dump = Path.Combine(dir, "env-dump.txt");
            if (File.Exists(dump)) File.Delete(dump);
            File.WriteAllBytes(Path.Combine(dir, SystemOps.LauncherName), script);
            var act = SystemOps.StartAction(exe);
            var before = new Dictionary<string, string>();
            foreach (var kv in env) { before[kv.Key] = Environment.GetEnvironmentVariable(kv.Key); Environment.SetEnvironmentVariable(kv.Key, kv.Value); }
            Process p;
            try { p = Process.Start(new ProcessStartInfo(act.Command, act.Arguments) { UseShellExecute = true, WorkingDirectory = act.WorkingDirectory }); }
            finally { foreach (var kv in before) Environment.SetEnvironmentVariable(kv.Key, kv.Value); }
            using (p)
            {
                var until = DateTime.UtcNow.AddSeconds(20);
                while (!File.Exists(dump) && !p.HasExited && DateTime.UtcNow < until) Thread.Sleep(50);
                if (checkWindows && File.Exists(dump))
                {
                    var visible = VisibleWindowsOfTree(p.Id);
                    A.Eq(0, visible.Count, "보이는 창 없음: " + string.Join(",", visible.ToArray()));
                }
                A.True(p.WaitForExit(20000), "끝남");
            }
            return File.Exists(dump) ? File.ReadAllLines(dump) : null;
        }

        static void NoneLeft(string[] lines)
        {
            foreach (var l in lines)
            {
                var name = l.Split('=')[0];
                foreach (var pre in SystemOps.ClearedPrefixes)
                    A.True(!name.StartsWith(pre, StringComparison.OrdinalIgnoreCase), "남은 변수: " + name);
                A.True(!name.Equals("__COMPAT_LAYER", StringComparison.OrdinalIgnoreCase), "남은 __COMPAT_LAYER");
            }
        }

        static string Sid()
        {
            using (var id = WindowsIdentity.GetCurrent()) return id.User.Value;
        }

        /// <summary>사본 자리를 스크래치로, schtasks 는 가짜로 — /Query /XML 은 query 가 답한다(코드, 출력), 다른 것은 적고 99.</summary>
        static SystemOps CopyOps(SelfImage self, string target, Func<Tuple<int, string>> query, List<string> calls)
        {
            return new SystemOps(self, null)
            {
                Target = target,
                Policy = DirPolicy.CurrentUser(),
                IsRunning = p => false,
                SettleDelayMs = 0,
                RunTool = (string exe, string args, int timeoutMs, out string output) =>
                {
                    lock (calls) calls.Add(exe + " " + args);
                    output = "";
                    if (args != "/Query /TN WebTether /XML") return 99;
                    var q = query();
                    output = q.Item2;
                    return q.Item1;
                },
            };
        }

        static Tuple<int, string> TaskPointingTo(string exe)
        {
            return Tuple.Create(0, "\r\n" + SystemOps.TaskXml(exe, Sid()) + "\r\n");
        }

        /// <summary>11de3c30~이 판 전의 작업 XML — 사본 exe 를 곧바로(인자 없이) 띄운다.</summary>
        static string OldTaskXml(string exe)
        {
            return "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\r\n<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\r\n" +
                   "  <Principals><Principal id=\"Author\"><LogonType>InteractiveToken</LogonType><RunLevel>HighestAvailable</RunLevel></Principal></Principals>\r\n" +
                   "  <Actions Context=\"Author\"><Exec><Command>" + System.Security.SecurityElement.Escape(exe) + "</Command></Exec></Actions>\r\n</Task>\r\n";
        }

        static Tuple<int, string> OldTaskPointingTo(string exe)
        {
            return Tuple.Create(0, "\r\n" + OldTaskXml(exe) + "\r\n");
        }

        static void PutLauncher(string target)
        {
            File.WriteAllBytes(Path.Combine(Path.GetDirectoryName(target), SystemOps.LauncherName), SystemOps.LauncherBytes(Path.GetFileName(target)));
        }

        /// <summary>Pump 의 Task 판 — 던진 것은 다시 던진다.</summary>
        static void PumpT(Task t)
        {
            Pump(t.ContinueWith(x =>
            {
                if (x.IsFaulted) throw x.Exception.GetBaseException();
                return 0;
            }));
        }

        internal static void AddStartupCopy(Action<string, Action> T)
        {
            T("사본(11-21): 작업 XML 에서 Exec 명령 읽기 — 이름공간·UTF-16 선언·앞뒤 글, 둘이면 둘, 깨진 것·DTD 는 읽지 않음", () =>
            {
                var xml = OldTaskXml(@"C:\Program Files\WebTether\WebTether.exe");
                var got = StartupCopy.TaskCommands("앞 글\r\n" + xml + "\r\n뒤 글");
                A.True(got != null && got.Count == 1, "하나");
                A.Eq(@"C:\Program Files\WebTether\WebTether.exe", got[0], "명령");
                var fresh = StartupCopy.TaskExecs(SystemOps.TaskXml(@"C:\Program Files\WebTether\WebTether.exe", "S-1-5-21-1-2-3-1001"));
                var want = SystemOps.StartAction(@"C:\Program Files\WebTether\WebTether.exe");
                A.True(fresh != null && fresh.Count == 1, "새 꼴 하나");
                A.Eq(want.Command, fresh[0].Command, "새 꼴 — 명령");
                A.Eq(want.Arguments, fresh[0].Arguments, "새 꼴 — 인자");
                A.Eq(want.WorkingDirectory, fresh[0].WorkingDirectory, "새 꼴 — 작업 폴더");
                A.Eq((string)null, StartupCopy.TaskExecs(xml)[0].Arguments, "옛 꼴은 인자 없음");
                var two = xml.Replace("</Exec>", "</Exec><Exec><Command>C:\\x.exe</Command></Exec>");
                A.Eq(2, StartupCopy.TaskCommands(two).Count, "둘");
                A.Eq(0, StartupCopy.TaskCommands(xml.Replace("<Exec><Command>" + @"C:\Program Files\WebTether\WebTether.exe" + "</Command></Exec>", "<ComHandler/>")).Count, "Exec 없음");
                A.Eq((List<string>)null, StartupCopy.TaskCommands(""), "빈 출력");
                A.Eq((List<string>)null, StartupCopy.TaskCommands("오류: 지정된 파일을 찾을 수 없습니다."), "XML 아님");
                A.Eq((List<string>)null, StartupCopy.TaskCommands(xml.Substring(0, xml.Length / 2)), "잘림");
                A.Eq((List<string>)null, StartupCopy.TaskCommands("<?xml version=\"1.0\"?><!DOCTYPE Task [<!ENTITY e \"C:\\evil.exe\">]><Task><Actions><Exec><Command>&e;</Command></Exec></Actions></Task>"), "DTD");
                A.Eq((List<string>)null, StartupCopy.TaskCommands("<?xml version=\"1.0\"?><Other><Task/></Other>"), "뿌리가 Task 가 아님");
            });

            T("사본: 경로 견주기 — 따옴표·대소문자·환경 변수는 같게, 상대·UNC·따옴표가 섞인 것은 아님", () =>
            {
                var p = @"C:\Program Files\WebTether\WebTether.exe";
                A.True(StartupCopy.SamePath(p, p), "같음");
                A.True(StartupCopy.SamePath("\"" + p + "\"", p), "따옴표");
                A.True(StartupCopy.SamePath(@"c:\program files\webtether\WEBTETHER.EXE", p), "대소문자");
                A.True(StartupCopy.SamePath(@"%windir%\system32\defrag.exe", Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "defrag.exe")), "환경 변수");
                A.True(!StartupCopy.SamePath(@"C:\Program Files\WebTether\WebTether.exe.bak", p), "다른 이름");
                A.True(!StartupCopy.SamePath(@"WebTether.exe", p), "상대");
                A.True(!StartupCopy.SamePath(@"\\server\share\WebTether.exe", @"\\server\share\WebTether.exe"), "UNC");
                A.True(!StartupCopy.SamePath("\"" + p + "\" --x", p), "인자가 붙은 꼴");
                A.True(!StartupCopy.SamePath("", p) && !StartupCopy.SamePath(p, null), "빈 것");
                A.True(!StartupCopy.SamePath("C:\\a\0b", p), "틀린 글자");
            });

            T("사본: 묻기 판단 — 다를 때만 묻고, 사본이 돌고 있으면 묻지 않고 알린다", () =>
            {
                A.Eq(CopyPrompt.Ask, StartupCopy.Decide(new CopyCheck { Status = CopyStatus.Stale }), "다름");
                A.Eq(CopyPrompt.Ask, StartupCopy.Decide(new CopyCheck { Status = CopyStatus.Stale, Missing = true }), "사본 없음");
                A.Eq(CopyPrompt.OldRunning, StartupCopy.Decide(new CopyCheck { Status = CopyStatus.Stale, InstalledRunning = true }), "옛 판이 돎");
                foreach (CopyStatus s in Enum.GetValues(typeof(CopyStatus)))
                    if (s != CopyStatus.Stale)
                        A.Eq(CopyPrompt.None, StartupCopy.Decide(new CopyCheck { Status = s, InstalledRunning = true }), s.ToString());
                A.Eq(CopyPrompt.None, StartupCopy.Decide(null), "없음");
            });

            T("사본: 견주기 — 작업 없음·시간 초과·남의 대상·XML 못 읽음·사본 없음·같음·다름·사본이 돎·지금 도는 것이 사본, schtasks 는 /Query 만", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("copy-check"), "WebTether");
                var target = Path.Combine(dir, "WebTether.exe");
                var calls = new List<string>();
                string err;
                using (var self = SelfImage.Open(out err))
                {
                    A.True(self != null, err);
                    Tuple<int, string> answer = Tuple.Create(1, "오류: 없음");
                    var ops = CopyOps(self, target, () => answer, calls);
                    A.Eq(CopyStatus.Off, ops.CheckStartupCopy().Status, "작업 없음");
                    answer = Tuple.Create(SystemOps.TimedOut, "시간 초과");
                    A.Eq(CopyStatus.Unknown, ops.CheckStartupCopy().Status, "시간 초과 — 모름");
                    answer = Tuple.Create(0, "깨진 <Task");
                    A.Eq(CopyStatus.Unknown, ops.CheckStartupCopy().Status, "XML 못 읽음");
                    answer = TaskPointingTo(@"C:\Elsewhere\WebTether.exe");
                    A.Eq(CopyStatus.OtherTarget, ops.CheckStartupCopy().Status, "남의 대상");
                    answer = TaskPointingTo(target);
                    var c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Stale && c.Missing && !c.InstalledRunning, "사본 없음: " + c);
                    Directory.CreateDirectory(dir);
                    File.WriteAllText(target, "옛 판");
                    c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Stale && !c.Missing, "다름: " + c);
                    ops.IsRunning = p => StartupCopy.SamePath(p, target);
                    A.True(ops.CheckStartupCopy().InstalledRunning, "사본이 돎");
                    ops.IsRunning = p => false;
                    File.Copy(self.Path, target, true);
                    A.Eq(CopyStatus.Same, ops.CheckStartupCopy().Status, "같음");
                    c = ops.CheckStartupCopy();
                    A.True(!c.OldTask && c.LauncherStale, "새 꼴인데 시작 스크립트 없음: " + c);
                    A.Eq(CopyPrompt.Ask, StartupCopy.Decide(c), "다시 등록을 묻는다");
                    PutLauncher(target);
                    c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Same && !c.OldTask && !c.LauncherStale, "새 꼴·스크립트 같음: " + c);
                    A.Eq(CopyPrompt.None, StartupCopy.Decide(c), "묻지 않음");
                    File.AppendAllText(Path.Combine(dir, SystemOps.LauncherName), "calc.exe\r\n");
                    A.True(ops.CheckStartupCopy().LauncherStale, "고친 스크립트는 다름");
                    PutLauncher(target);
                    answer = OldTaskPointingTo("\"" + target.ToUpperInvariant() + "\"");
                    c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Same && c.OldTask, "따옴표·대문자로 적힌 옛 꼴 작업: " + c);
                    A.Eq(CopyPrompt.Ask, StartupCopy.Decide(c), "옛 꼴이면 사본이 같아도 묻는다");
                    A.Eq(StartupCopy.AskTaskText, StartupCopy.AskTextFor(c), "등록만");
                    A.Eq(StartupCopy.FormNoteTask, StartupCopy.FormNoteFor(c), "설정 창의 줄");
                    answer = Tuple.Create(0, OldTaskXml(target).Replace("</Command>", "</Command><Arguments>--x</Arguments>"));
                    A.Eq(CopyStatus.OtherTarget, ops.CheckStartupCopy().Status, "인자가 붙은 옛 꼴은 남의 것");
                    answer = Tuple.Create(0, SystemOps.TaskXml(target, Sid()).Replace("/c .\\launch.cmd", "/c .\\other.cmd"));
                    A.Eq(CopyStatus.OtherTarget, ops.CheckStartupCopy().Status, "인자가 다른 새 꼴은 남의 것");
                    var sys = Environment.GetFolderPath(Environment.SpecialFolder.System);
                    answer = Tuple.Create(0, SystemOps.TaskXml(target, Sid()).Replace(sys + "\\conhost.exe</Command>", "%windir%\\system32\\conhost.exe</Command>"));
                    A.Eq(CopyStatus.OtherTarget, ops.CheckStartupCopy().Status, "환경 변수로 적은 conhost 는 우리 것이 아님");
                    answer = OldTaskPointingTo(target);
                    File.WriteAllText(target, "옛 판");
                    c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Stale && c.OldTask, "옛 판·옛 꼴: " + c);
                    A.Eq(StartupCopy.AskBothText, StartupCopy.AskTextFor(c), "사본과 등록");
                    A.Eq(StartupCopy.FormNote, StartupCopy.FormNoteFor(c), "설정 창 — 사본");
                    A.True(StartupCopy.DoneText(c, true, null).Contains("사본과 등록"), StartupCopy.DoneText(c, true, null));

                    var asInstalled = CopyOps(self, self.Path, () => OldTaskPointingTo(self.Path), calls);
                    c = asInstalled.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.RunningInstalled && c.OldTask, "지금 도는 것이 사본이어도 옛 꼴이면: " + c);
                    A.Eq(CopyPrompt.Ask, StartupCopy.Decide(c), "다시 등록을 묻는다");
                    var asInstalledOff = CopyOps(self, self.Path, () => Tuple.Create(1, "없음"), calls);
                    A.Eq(CopyStatus.Off, asInstalledOff.CheckStartupCopy().Status, "지금 도는 것이 사본 — 작업 없음");
                    A.Eq(CopyStatus.Unknown, CopyOps(null, target, () => answer, calls).CheckStartupCopy().Status, "쥐지 못했으면 모름");
                }
                A.True(calls.All(x => x == "schtasks.exe /Query /TN WebTether /XML"), "읽기만: " + string.Join("|", calls.Distinct().ToArray()));
            });

            T("사본: 바꾸기 — 쥔 바이트로, 해시가 같고 전용 폴더·임시 파일 없음, 작업은 건드리지 않는다(schtasks 를 부르지 않음), 사본이 돌면 하지 않는다", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("copy-refresh"), "WebTether");
                var target = Path.Combine(dir, "WebTether.exe");
                Directory.CreateDirectory(dir);
                File.WriteAllText(target, "옛 판");
                var calls = new List<string>();
                var notes = new List<string>();
                string err;
                using (var self = SelfImage.Open(out err))
                {
                    A.True(self != null, err);
                    self.Stream.Position = 0;
                    var want = Binaries.Sha256(self.Stream);
                    var ops = CopyOps(self, target, () => TaskPointingTo(target), calls);
                    PutLauncher(target);
                    ops.IsRunning = p => true;
                    A.True(!ops.RefreshStartupCopy(out err), "사본이 돌면 하지 않음");
                    A.True(err.Contains("옛 판이 아직 떠 있습니다"), err);
                    A.Eq("옛 판", File.ReadAllText(target), "그대로");
                    ops.IsRunning = p => false;
                    A.True(ops.RefreshStartupCopy(out err), "바꿈: " + err);
                    A.Eq(want, Binaries.Sha256(File.ReadAllBytes(target)), "같은 바이트");
                    bool ok;
                    A.Eq((string)null, SecureDir.CheckDescriptor(SecureDir.ReadDescriptor(dir), DirPolicy.CurrentUser(), out ok), "전용 폴더");
                    A.Eq(0, Directory.GetFiles(dir, "*.new-*").Length, "임시 파일 없음");
                    A.Eq(CopyStatus.Same, ops.CheckStartupCopy().Status, "이제 같음");
                    A.True(!calls.Any(x => x.Contains("/Create") || x.Contains("/Delete")), "작업은 그대로: " + string.Join("|", calls.ToArray()));
                    // 지금 도는 것이 사본이면 복사하지 않는다(작업이 없으면 아무것도 하지 않는다 — 시험 exe 의 폴더에 쓰지 않는다).
                    var asInstalled = CopyOps(self, self.Path, () => Tuple.Create(1, "없음"), calls);
                    A.True(asInstalled.RefreshStartupCopy(out err), "지금 도는 것이 사본 — 할 일 없음: " + err);
                    A.True(!File.Exists(Path.Combine(TestMain.OutDir, SystemOps.LauncherName)), "시험 exe 의 폴더에 쓰지 않음");
                }
            });

            T("사본: 사본이 실제로 돌고 있으면(시험 exe 를 사본 자리에서 띄움) 알아보고, 알아보지 못해도 바꾸지 못하며 망가뜨리지 않는다", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("copy-running"), "WebTether");
                Directory.CreateDirectory(dir);
                var target = Path.Combine(dir, "WebTether.exe");
                File.Copy(Path.Combine(TestMain.OutDir, "ctrlc-child.exe"), target);
                var before = Binaries.Sha256(File.ReadAllBytes(target));
                var psi = new ProcessStartInfo(target, "wait") { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true };
                using (var p = Process.Start(psi))
                {
                    try
                    {
                        p.StandardOutput.ReadLine(); // ready
                        A.True(StartupCopy.IsRunningElsewhere(target), "돌고 있음을 알아봄");
                        A.True(!StartupCopy.IsRunningElsewhere(Path.Combine(dir, "Other.exe")), "다른 경로는 아님");
                        var calls = new List<string>();
                        string err;
                        using (var self = SelfImage.Open(out err))
                        {
                            A.True(self != null, err);
                            var ops = CopyOps(self, target, () => TaskPointingTo(target), calls);
                            ops.IsRunning = StartupCopy.IsRunningElsewhere;
                            var c = ops.CheckStartupCopy();
                            A.True(c.Status == CopyStatus.Stale && c.InstalledRunning, "견주기: " + c);
                            A.Eq(CopyPrompt.OldRunning, StartupCopy.Decide(c), "묻지 않고 알림");
                            // 첫 확인을 비켜 가도(돌기 시작한 직후 등) 돌고 있는 exe 는 지워지지 않는다 — 실패하고 까닭을 옛 판으로 적는다.
                            int n = 0;
                            ops.IsRunning = x => n++ > 0 && StartupCopy.IsRunningElsewhere(x);
                            A.True(!ops.RefreshStartupCopy(out err), "바꾸지 못함");
                            A.True(err.Contains("옛 판이 아직 떠 있습니다"), err);
                        }
                        A.Eq(before, Binaries.Sha256(File.ReadAllBytes(target)), "돌고 있는 사본은 그대로");
                        A.Eq(0, Directory.GetFiles(dir, "*.new-*").Length, "임시 파일 없음");
                    }
                    finally
                    {
                        try { p.Kill(); } catch (InvalidOperationException) { } catch (System.ComponentModel.Win32Exception) { }
                        p.WaitForExit(5000);
                    }
                }
            });

            T("사본: 뜰 때의 흐름 — 견주기는 일꾼 스레드, 다르면 한 번 묻고 '아니요' 면 이 실행 동안 다시 묻지 않음", () =>
            {
                var ops = new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale } };
                var before = SynchronizationContext.Current;
                try
                {
                    SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
                    int me = Thread.CurrentThread.ManagedThreadId, askThread = 0, asks = 0;
                    var told = new List<string>();
                    var flow = new StartupCopyFlow(ops, null)
                    {
                        Ask = t => { asks++; askThread = Thread.CurrentThread.ManagedThreadId; return false; },
                        Tell = t => told.Add(t),
                        Notify = (t, w) => told.Add(t),
                    };
                    PumpT(flow.RunAsync());
                    A.True(ops.CopyThread != 0 && ops.CopyThread != me, "견주기는 일꾼 스레드");
                    A.Eq(1, asks, "물음");
                    A.Eq(me, askThread, "묻기는 UI 스레드");
                    A.True(flow.Declined, "'아니요'");
                    A.True(!ops.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꾸지 않음");
                    A.Eq(0, told.Count, "알림 없음");
                    PumpT(flow.RunAsync());
                    A.Eq(1, asks, "이 실행 동안 다시 묻지 않음");
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("사본: 뜰 때의 흐름 — '예' 면 일꾼 스레드에서 바꾸고 알림, 실패하면 경고, 같거나 꺼져 있으면 묻지 않음, 옛 판이 돌면 묻지 않고 알림, 다른 묻는 창이 닫힐 때까지 기다림", () =>
            {
                var before = SynchronizationContext.Current;
                try
                {
                    SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
                    int me = Thread.CurrentThread.ManagedThreadId;
                    Func<DryRunOps, List<string>, StartupCopyFlow> make = (o, said) => new StartupCopyFlow(o, null)
                    {
                        Ask = t => { said.Add("ask"); return true; },
                        Tell = t => said.Add("tell " + t),
                        Notify = (t, w) => said.Add((w ? "warn " : "info ") + t),
                    };

                    var ops = new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale } };
                    var said1 = new List<string>();
                    var f1 = make(ops, said1);
                    PumpT(f1.RunAsync());
                    A.True(ops.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꿈");
                    A.True(ops.CopyThread != me, "바꾸기는 일꾼 스레드");
                    A.Eq("ask|info 시작 시 실행 사본을 이 판으로 바꿨습니다.", string.Join("|", said1.ToArray()), "알림");
                    A.True(f1.Pending.IsCompleted, "끝남");

                    var bad = new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale }, CopyError = "디스크 가득" };
                    var said2 = new List<string>();
                    PumpT(make(bad, said2).RunAsync());
                    A.True(said2.Last().StartsWith("warn ") && said2.Last().Contains("디스크 가득"), "실패는 경고: " + said2.Last());

                    foreach (var st in new[] { CopyStatus.Same, CopyStatus.Off, CopyStatus.OtherTarget, CopyStatus.Unknown, CopyStatus.RunningInstalled })
                    {
                        var o = new DryRunOps(null) { CopyState = new CopyCheck { Status = st } };
                        var s = new List<string>();
                        PumpT(make(o, s).RunAsync());
                        A.Eq(0, s.Count, "묻지 않음 — " + st);
                    }

                    var old = new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale, InstalledRunning = true } };
                    var said3 = new List<string>();
                    PumpT(make(old, said3).RunAsync());
                    A.Eq("tell " + StartupCopy.OldRunningText, string.Join("|", said3.ToArray()), "옛 판 — 알리기만");
                    A.True(!old.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꾸지 않음");

                    bool busy = true;
                    var said4 = new List<string>();
                    var f4 = make(new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale } }, said4);
                    f4.Busy = () => busy;
                    f4.BusyPollMs = 20;
                    var t4 = f4.RunAsync();
                    var sw = Stopwatch.StartNew();
                    while (sw.ElapsedMilliseconds < 300) { Application.DoEvents(); Thread.Sleep(10); }
                    A.Eq(0, said4.Count, "다른 묻는 창이 떠 있는 동안 묻지 않음");
                    busy = false;
                    PumpT(t4);
                    A.Eq("ask", said4[0], "닫힌 뒤에 물음");
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("사본(r6 검토): 뜰 때의 흐름 — 견주는 사이·다른 창을 기다리는 사이 '끝내기' 를 눌렀으면 묻지도 알리지도 않고 끝난다", () =>
            {
                var before = SynchronizationContext.Current;
                try
                {
                    SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
                    foreach (var running in new[] { false, true })
                    {
                        var said = new List<string>();
                        var o = new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale, InstalledRunning = running } };
                        var f = new StartupCopyFlow(o, null)
                        {
                            Ask = t => { said.Add("ask"); return true; },
                            Tell = t => said.Add("tell"),
                            Notify = (t, w) => said.Add("notify"),
                            Stopping = () => true,
                        };
                        PumpT(f.RunAsync());
                        A.Eq(0, said.Count, "견주는 사이 끝내기 — 창 없음(" + (running ? "옛 판" : "다름") + "): " + string.Join("|", said.ToArray()));
                        A.True(!o.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꾸지 않음");
                    }

                    bool stopping = false;
                    var said2 = new List<string>();
                    var f2 = new StartupCopyFlow(new DryRunOps(null) { CopyState = new CopyCheck { Status = CopyStatus.Stale } }, null)
                    {
                        Ask = t => { said2.Add("ask"); return true; },
                        Tell = t => said2.Add("tell"),
                        Busy = () => true, // 다른 묻는 창이 닫히지 않은 채 끝낸다
                        BusyPollMs = 20,
                        Stopping = () => stopping,
                    };
                    var t2 = f2.RunAsync();
                    var sw = Stopwatch.StartNew();
                    while (sw.ElapsedMilliseconds < 200) { Application.DoEvents(); Thread.Sleep(10); }
                    A.True(!t2.IsCompleted, "기다리는 중");
                    stopping = true;
                    PumpT(t2);
                    A.Eq(0, said2.Count, "기다리는 사이 끝내기 — 창 없음");
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("사본: 설정 창 — 다르면 '시작 시 실행' 아래 한 줄, 칸을 끄면 숨김, 켠 채 저장하면 일꾼 스레드에서 바꾸고 알림(작업은 다시 만들지 않음)", () =>
            {
                var r = Make("copy-form", new Settings { AutoStart = true });
                r.Ops.StartupState = true;
                r.Ops.CopyState = new CopyCheck { Status = CopyStatus.Stale };
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new SettingsForm(r.C, r.Ops, "x.exe"))
                    {
                        int me = Thread.CurrentThread.ManagedThreadId;
                        A.Eq((string)null, f.CopyNoteText, "견주기 전에는 없음");
                        PumpT(f.RefreshCopyNoteAsync());
                        A.Eq(StartupCopy.FormNote, f.CopyNoteText, "한 줄");
                        A.True(r.Ops.CopyThread != me, "견주기는 일꾼 스레드");
                        var auto = AllControls(f).OfType<CheckBox>().First(c => c.Text == "Windows 시작 시 실행");
                        auto.Checked = false;
                        A.Eq((string)null, f.CopyNoteText, "칸을 끄면 숨김");
                        auto.Checked = true;
                        A.Eq(StartupCopy.FormNote, f.CopyNoteText, "다시 켜면 보임");
                        r.Ops.CopyThread = 0;
                        A.True(Pump(f.SaveAsync()), "저장");
                        A.True(r.Ops.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꿈: " + string.Join("|", r.Ops.Calls.ToArray()));
                        A.True(r.Ops.CopyThread != 0 && r.Ops.CopyThread != me, "바꾸기는 일꾼 스레드");
                        A.True(!r.Ops.Calls.Any(c => c.StartsWith("schtasks")), "작업은 다시 만들지 않음");
                        A.True(AllControls(f).OfType<Label>().Any(l => l.Text == "저장했습니다. 시작 시 실행 사본도 이 판으로 바꿨습니다."), "알림");
                        A.WaitUntil(() => { Application.DoEvents(); return f.CopyNoteText == null; }, 3000, "다시 견주어 줄을 걷음");
                        int n = r.Ops.Calls.Count;
                        A.True(Pump(f.SaveAsync()), "같으면");
                        A.Eq(n, r.Ops.Calls.Count, "바꾸지 않음");
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("사본: 설정 창 — 옛 판이 돌면 다른 줄이고 저장은 되되 경고, 바꾸기 실패도 경고, 칸을 끄고 저장하면 바꾸지 않음", () =>
            {
                var r = Make("copy-form-old", new Settings { AutoStart = true });
                r.Ops.StartupState = true;
                r.Ops.CopyState = new CopyCheck { Status = CopyStatus.Stale, InstalledRunning = true };
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new SettingsForm(r.C, r.Ops, "x.exe"))
                    {
                        PumpT(f.RefreshCopyNoteAsync());
                        A.Eq(StartupCopy.FormNoteRunning, f.CopyNoteText, "옛 판이 돎");
                        f.LoadFrom(new Settings { AutoStart = true, ReconnectMax = 5 });
                        A.True(Pump(f.SaveAsync()), "설정은 저장");
                        A.Eq(5, r.C.Settings.ReconnectMax, "걸림");
                        A.True(!r.Ops.Calls.Any(c => c.StartsWith("사본 바꾸기")), "바꾸지 않음");
                        A.True(AllControls(f).OfType<Label>().Any(l => l.Text.Contains("사본은 바꾸지 못했습니다") && l.Text.Contains("옛 판")), "경고");

                        r.Ops.CopyState = new CopyCheck { Status = CopyStatus.Stale };
                        r.Ops.CopyError = "디스크 가득";
                        A.True(Pump(f.SaveAsync()), "설정은 저장");
                        A.True(AllControls(f).OfType<Label>().Any(l => l.Text.Contains("사본은 바꾸지 못했습니다: 디스크 가득")), "실패 경고");

                        r.Ops.CopyError = null;
                        int n = r.Ops.Calls.Count(c => c.StartsWith("사본 바꾸기"));
                        f.LoadFrom(new Settings { AutoStart = false });
                        A.True(Pump(f.SaveAsync()), "끄기");
                        A.Eq(n, r.Ops.Calls.Count(c => c.StartsWith("사본 바꾸기")), "끄면 바꾸지 않음");
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("묻는 창: 제목을 골라 묻기·알리기 — 늘 위, Find 가 그 제목으로 찾고 닫히면 없다(짧게 띄우고 닫는다)", () =>
            {
                var before = SynchronizationContext.Current;
                try
                {
                    foreach (var tell in new[] { false, true })
                    {
                        bool found = false, topmost = false;
                        int ticks = 0;
                        using (var timer = new System.Windows.Forms.Timer { Interval = 20 })
                        {
                            timer.Tick += (s, e) =>
                            {
                                ticks++;
                                var h = TopAsk.Find();
                                if (h == IntPtr.Zero)
                                {
                                    // 못 찾으면 닫힐 때까지 거듭 닫는다(한 번만 보내고 멈추면 놓친 창이 화면에 남고 시험이 멈춘다 — r6 검토).
                                    if (ticks > 250) Win.AnswerAllDialogs();
                                    return;
                                }
                                timer.Stop();
                                found = true;
                                Win.MoveAway(h);
                                topmost = TopAsk.IsTopmost(h);
                                if (tell) TopAsk.CloseForTest(h); else TopAsk.AnswerForTest(h, true);
                            };
                            timer.Start();
                            if (tell) TopAsk.Tell("시험 — 곧 저절로 닫힙니다", StartupCopy.Title, false);
                            else A.True(TopAsk.Ask("시험 — 곧 저절로 닫힙니다", StartupCopy.Title, false), "'예'");
                        }
                        TestMain.Say("      " + (tell ? "알림" : "물음") + " 창 — 찾음 " + found + " · " + ticks + " 번째");
                        A.True(found && topmost, (tell ? "알림" : "물음") + " 창을 찾음·늘 위(" + ticks + ")");
                        A.True(!TopAsk.IsOpen && TopAsk.Find() == IntPtr.Zero, "닫힌 뒤에는 없음");
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("시작 시 실행(T1574.012): 작업은 .NET exe 를 곧바로 띄우지 않는다 — conhost --headless · cmd /d /e:on /v:off · .\\launch.cmd, '%' 없음", () =>
            {
                var sys = Environment.GetFolderPath(Environment.SpecialFolder.System);
                var a = SystemOps.StartAction(@"C:\Program Files\WebTether\WebTether.exe");
                A.Eq(Path.Combine(sys, "conhost.exe"), a.Command, "네이티브 시스템 실행 파일");
                A.True(a.Arguments.StartsWith("--headless "), "창 없이: " + a.Arguments);
                A.True(a.Arguments.Contains(" /d ") && a.Arguments.Contains(" /e:on ") && a.Arguments.Contains(" /v:off ") && a.Arguments.EndsWith(" /c .\\launch.cmd"), "AutoRun 끔·확장 켬·지연 확장 끔: " + a.Arguments);
                A.Eq(@"C:\Program Files\WebTether", a.WorkingDirectory, "작업 폴더");
                foreach (var f in new[] { a.Command, a.Arguments, a.WorkingDirectory })
                    A.True(f.IndexOf('%') < 0, "% 없음: " + f);
                A.True(a.Arguments.IndexOf("WebTether.exe", StringComparison.OrdinalIgnoreCase) < 0, "exe 를 곧바로 띄우지 않음");
                foreach (var bad in new[] { @"C:\Pro%gram%\WebTether.exe", "C:\\a\"b\\WebTether.exe", @"\\server\share\WebTether.exe", @"C:\x\Web Tether.exe", @"C:\x\a&b.exe" })
                {
                    bool threw = false;
                    try { SystemOps.StartAction(bad); } catch (ArgumentException) { threw = true; }
                    A.True(threw, "거절: " + bad);
                }
                var x = SystemOps.TaskXml(@"C:\Program Files\A&B (c)\WebTether.exe", "S-1-5-21-1");
                A.True(x.Contains("<WorkingDirectory>C:\\Program Files\\A&amp;B (c)</WorkingDirectory>"), "작업 폴더 이스케이프");
                var script = SystemOps.LauncherScript("WebTether.exe");
                A.True(script.Replace("%%v", "").IndexOf('%') < 0, "스크립트에 % 확장 없음(for 변수만)");
                A.True(script.IndexOf('!') < 0, "! 없음");
                foreach (var pre in SystemOps.ClearedPrefixes) A.True(script.Contains("set " + pre + ">"), "비우는 앞머리 " + pre);
                A.True(script.Contains("set \"__COMPAT_LAYER=\""), "__COMPAT_LAYER");
                A.True(!script.Contains("('"), "for /f ('명령') 을 쓰지 않음(%ComSpec% 의 cmd 를 AutoRun 과 함께 띄운다)");
                A.True(script.TrimEnd().EndsWith("\".\\WebTether.exe\""), "끝에 같은 폴더의 exe");
                foreach (var ch in SystemOps.LauncherBytes("WebTether.exe")) A.True(ch < 128, "ASCII");
                A.True(script.Contains("\r\n") && script.Replace("\r\n", "").IndexOf('\n') < 0, "CRLF");
            });

            T("시작 스크립트: conhost --headless 로 실제로 돌려 COR_·COMPlus_·CORECLR_·DOTNET_·APPDOMAIN_MANAGER_ 를 비우고 같은 폴더의 exe 를 띄운다, 보이는 창 없음", () =>
            {
                var dir = TestMain.Scratch("launcher-run");
                // 이름·값은 모두 뜻이 없는 것만 — 비우기가 깨져 그대로 닿아도 CLR 이 다르게 돌지 않게(11-27: COMPLUS_Version=v9 를 넣었더니 비우기를
                // 되돌린 시험에서 .NET 설치를 묻는 창이 사용자 화면에 떴다). 런타임·판을 고르는 변수, 화면에 창을 띄울 수 있는 변수는 쓰지 않는다.
                var r = RunLauncher(dir, SystemOps.LauncherBytes("WebTether.exe"), new Dictionary<string, string>
                {
                    { "COR_WEBTETHER_TEST", "0" }, { "COMPlus_WebTetherTestKnob", "1" }, { "complus_webtether_lowercase", "1" }, { "COMPlus_x&y", "1" },
                    { "CORECLR_WEBTETHER_TEST", "0" }, { "DOTNET_WEBTETHER_TEST", "0" }, { "APPDOMAIN_MANAGER_WEBTETHER_TEST", "0" },
                    { "WEBTETHER_TEST_KEEP", "keep" },
                }, true);
                A.True(r != null, "exe 가 떴다");
                NoneLeft(r);
                A.True(r.Contains("WEBTETHER_TEST_KEEP=keep"), "다른 변수는 그대로");
                A.True(!File.Exists(Path.Combine(dir, SystemOps.LauncherScratch)), "임시 파일을 지움");
            });

            T("시작 스크립트: 아주 긴 값(8,200·9,000자) 바로 뒤의 변수도 비운다 — for /f 가 한 줄을 8,191자 남짓까지만 읽는다(11-27 재검토가 재현)", () =>
            {
                foreach (var n in new[] { 8200, 9000, 20000 })
                {
                    var dir = TestMain.Scratch("launcher-long-" + n);
                    var r = RunLauncher(dir, SystemOps.LauncherBytes("WebTether.exe"), new Dictionary<string, string>
                    {
                        { "COR_WEBTETHER_A", new string('x', n) }, { "COR_WEBTETHER_B", "1" },
                        { "COMPlus_WebTetherA", new string('y', n) }, { "COMPlus_WebTetherB", "1" },
                    }, false);
                    A.True(r != null, "exe 가 떴다(" + n + ")");
                    NoneLeft(r);
                }
            });

            T("시작 스크립트: 비우고도 남은 것이 있으면 exe 를 띄우지 않는다(비우기를 건너뛴 판으로 검사만)", () =>
            {
                var dir = TestMain.Scratch("launcher-left");
                var script = SystemOps.LauncherScript("WebTether.exe", 0);
                foreach (var pre in new[] { "COR_WEBTETHER_TEST", "COMPlus_WebTetherTestKnob", "CORECLR_WEBTETHER_TEST", "DOTNET_WEBTETHER_TEST", "APPDOMAIN_MANAGER_WEBTETHER_TEST" })
                {
                    var r = RunLauncher(dir, System.Text.Encoding.ASCII.GetBytes(script), new Dictionary<string, string> { { pre, "0" } }, false);
                    A.True(r == null, "남았으면 띄우지 않음: " + pre);
                }
                A.True(RunLauncher(dir, System.Text.Encoding.ASCII.GetBytes(script), new Dictionary<string, string> { { "WEBTETHER_TEST_KEEP", "keep" } }, false) != null,
                       "남은 것이 없으면 띄움");
            });

            T("시작 시 실행: 옛 꼴 작업은 '예' 면 다시 등록한다 — 시작 스크립트를 쓰고 새 꼴 XML 로 /Create /F, 남의 작업·작업 없음이면 등록하지 않음", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("copy-reregister"), "WebTether");
                var target = Path.Combine(dir, "WebTether.exe");
                var calls = new List<string>();
                string registered = OldTaskXml(target), sentXml = null;
                string err;
                using (var self = SelfImage.Open(out err))
                {
                    A.True(self != null, err);
                    var ops = new SystemOps(self, null)
                    {
                        Target = target, Policy = DirPolicy.CurrentUser(), IsRunning = p => false, SettleDelayMs = 0,
                        RunTool = (string exe, string args, int timeoutMs, out string output) =>
                        {
                            lock (calls) calls.Add(exe + " " + args);
                            output = "";
                            if (args == "/Query /TN WebTether /XML")
                            {
                                if (registered == null) return 1;
                                output = registered;
                                return 0;
                            }
                            if (args.StartsWith("/Create /TN WebTether /XML "))
                            {
                                var path = args.Substring("/Create /TN WebTether /XML ".Length).Replace(" /F", "").Trim('"');
                                A.Eq(dir, Path.GetDirectoryName(path), "XML 은 설치 폴더에");
                                using (var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read))
                                using (var sr = new StreamReader(fs, System.Text.Encoding.Unicode)) sentXml = sr.ReadToEnd();
                                registered = sentXml;
                                return 0;
                            }
                            return 99;
                        },
                    };
                    var c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Stale && c.Missing && c.OldTask, "옛 꼴·사본 없음: " + c);
                    A.True(ops.RefreshStartupCopy(out err), "바꿈: " + err);
                    A.True(sentXml != null, "다시 등록함");
                    A.Eq(StartupCopy.TaskForm.Current, StartupCopy.FormOf(StartupCopy.TaskExecs(sentXml), target), "새 꼴");
                    A.True(File.Exists(Path.Combine(dir, SystemOps.LauncherName)), "시작 스크립트");
                    A.Eq(0, Directory.GetFiles(dir, "task-*.xml").Length, "XML 은 지움");
                    c = ops.CheckStartupCopy();
                    A.True(c.Status == CopyStatus.Same && !c.OldTask && !c.LauncherStale, "이제 같음: " + c);
                    int creates = calls.Count(x => x.Contains("/Create"));
                    A.True(ops.RefreshStartupCopy(out err), "할 일 없음: " + err);
                    A.Eq(creates, calls.Count(x => x.Contains("/Create")), "새 꼴이면 다시 등록하지 않음");
                    registered = OldTaskXml(@"C:\Elsewhere\WebTether.exe");
                    A.True(ops.RefreshStartupCopy(out err), "남의 작업: " + err);
                    A.Eq(creates, calls.Count(x => x.Contains("/Create")), "남의 작업은 그대로");
                    registered = null;
                    A.True(ops.RefreshStartupCopy(out err), "작업 없음: " + err);
                    A.Eq(creates, calls.Count(x => x.Contains("/Create")), "작업이 없으면 등록하지 않음");
                    A.True(!calls.Any(x => x.Contains("/Delete")), "지우지 않음");
                }
            });

            T("핫스팟: 이름을 본 어댑터와 192.168.49.1 로 가는 어댑터가 다르면 손으로 누른 '연결' 도 묻지 않고 띄우지 않는다", () =>
            {
                var r = Make("route-mismatch", null);
                r.Hotspot = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = "남의망", RouteMismatch = "192.168.49.x 어댑터가 여럿이고 길이 다름" };
                int asked = 0;
                r.C.AskConnect = (h, p) => { asked++; return true; };
                Run(r.C.ConnectAsync());
                A.Eq(0, asked, "묻지 않음");
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                A.True(r.C.Detail.Contains("길이 다름"), r.C.Detail);
                r.Hotspot = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = Mine, RouteMismatch = "192.168.49.x 어댑터가 여럿이고 길이 다름" };
                Run(r.C.ConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "이름이 맞아도 띄우지 않음");
            });

            T("사본: 이 PC 의 작업을 읽기만(schtasks /Query /XML) — 명령을 읽어 낸다(경로는 적지 않는다)", () =>
            {
                var ops = new SystemOps(null, null);
                string output;
                int code = ops.RunTool("schtasks.exe", "/Query /TN WebTether /XML", 10000, out output);
                if (code != 0) { TestMain.Say("      (작업 WebTether 없음 — 코드 " + code + ")"); }
                else
                {
                    var cmds = StartupCopy.TaskCommands(output);
                    A.True(cmds != null && cmds.Count >= 1, "명령을 읽음");
                    TestMain.Say("      작업 WebTether 있음 · 명령 " + cmds.Count + "개 · 설치 사본을 가리킴 " + (cmds.Count == 1 && StartupCopy.SamePath(cmds[0], SystemOps.InstalledExe)) +
                                 " · 꼴 " + StartupCopy.FormOf(StartupCopy.TaskExecs(output), SystemOps.InstalledExe));
                }
                code = ops.RunTool("schtasks.exe", @"/Query /TN \Microsoft\Windows\Defrag\ScheduledDefrag /XML", 10000, out output);
                if (code == 0)
                {
                    var cmds = StartupCopy.TaskCommands(output);
                    A.True(cmds != null && cmds.Count == 1, "시스템 작업의 명령");
                    A.True(StartupCopy.SamePath(cmds[0], Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "defrag.exe")), "환경 변수로 적힌 명령: " + cmds[0]);
                }
            });
        }
    }
}
