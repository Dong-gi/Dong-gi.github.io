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
                var xml = SystemOps.TaskXml(@"C:\Program Files\WebTether\WebTether.exe", "S-1-5-21-1-2-3-1001");
                var got = StartupCopy.TaskCommands("앞 글\r\n" + xml + "\r\n뒤 글");
                A.True(got != null && got.Count == 1, "하나");
                A.Eq(@"C:\Program Files\WebTether\WebTether.exe", got[0], "명령");
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
                    answer = Tuple.Create(0, SystemOps.TaskXml("\"" + target.ToUpperInvariant() + "\"", Sid()));
                    A.Eq(CopyStatus.Same, ops.CheckStartupCopy().Status, "따옴표·대문자로 적힌 작업");

                    var asInstalled = CopyOps(self, self.Path, () => { throw new Exception("묻지 않아야 함"); }, calls);
                    A.Eq(CopyStatus.RunningInstalled, asInstalled.CheckStartupCopy().Status, "지금 도는 것이 사본");
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
                    var asInstalled = CopyOps(self, self.Path, () => TaskPointingTo(self.Path), calls);
                    A.True(!asInstalled.RefreshStartupCopy(out err), "지금 도는 것이 사본이면 하지 않음");
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
                    TestMain.Say("      작업 WebTether 있음 · 명령 " + cmds.Count + "개 · 설치 사본을 가리킴 " + (cmds.Count == 1 && StartupCopy.SamePath(cmds[0], SystemOps.InstalledExe)));
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
