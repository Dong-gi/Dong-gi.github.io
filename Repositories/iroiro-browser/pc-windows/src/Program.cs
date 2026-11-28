// 들어가는 곳. 실제 판은 갈래가 넷이다 — 인자 없음: 트레이(보통 권한, 세션마다 하나 — 서비스에 파이프로 묻는다. 설정 창도 트레이의 것이고
// 저장은 서비스에 제안을 보내 콘솔 사용자의 확인을 받는다), --service: SCM 이 띄우는 서비스(LocalSystem — 관리자 권한이 드는 일을 모두
// 맡는다), --install·--uninstall: 트레이가 runas 로 띄우는 관리자 판(띄우기 전에 CLR 이 읽는 환경 변수를 지운다 — ClrEnv).
//
// 시험 판(TEST_BUILD — 매니페스트 asInvoker)은 늘 dry-run 이고 서비스 없이 같은 프로세스의 Controller 를 본다: tun2proxy 를 띄우지 않고,
// 경로·Wi-Fi 를 바꾸지 않고 기록만 한다. 상태 폴더는 --home <폴더>(없으면 %LOCALAPPDATA%\WebTether-test) — 이 사용자 전용 권한.
// 실제 판의 상태 폴더는 %ProgramData%\WebTether(관리자 전용 — SecureFs, 서비스가 쓴다).
using System;
using System.Collections.Generic;
using System.IO;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

// 이 어셈블리의 P/Invoke 는 System32 에서만 찾는다 — 이 exe 는 관리자로 돌고, 내려받기에서 띄우면 그 폴더(같은 사용자가 쓸 수 있다)가
// 'exe 의 폴더' 다. 이름만 적은 DllImport 가 그 폴더의 같은 이름 DLL 을 먼저 올리지 않게.
[assembly: DefaultDllImportSearchPaths(DllImportSearchPath.System32)]

namespace WebTether
{
    /// <summary>
    /// DLL 찾기를 System32 로 좁힌다 — Main 의 첫머리에서. SetDefaultDllDirectories 는 이 프로세스의 그 뒤 LoadLibrary(이름만 —
    /// 프레임워크의 P/Invoke·지연 로드 포함)가 exe 의 폴더·현재 폴더·PATH 를 보지 않게 한다. 온전한 경로로 올리는 것(CLR 의 구성 요소)은
    /// 그대로다. 그 전에 올라온 것(정적 가져오기 — mscoree.dll 등)은 막지 못한다(알고 둔 것).
    /// </summary>
    public static class DllSafety
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool SetDefaultDllDirectories(uint flags);
        const uint LOAD_LIBRARY_SEARCH_SYSTEM32 = 0x800;

        /// <summary>걸었으면 참(실패는 던지지 않는다 — 시험이 본다).</summary>
        public static bool Harden()
        {
            try { return SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32); }
            catch (Exception) { return false; }
        }
    }

    public enum InstanceStatus { First, Another, Squatted }

    /// <summary>
    /// 하나만 실행 — 트레이(Local\WebTether-tray)와 시험 판(Local\…-test). 정책의 SID 로 보안 설명자를 짓고, 이미 있으면 주인을 본다:
    /// 정책의 SID 가 아니면 다른 프로그램이 먼저 잡은 것이다 — 알리고 뜬다(11-27 까지의 Global\ 표지와 잠금 파일은 서비스로 옮기며 걷었다).
    /// 보이기 이벤트도 같다(남이 만든 것이면 쓰지 않는다).
    /// </summary>
    public sealed class InstanceLock : IDisposable
    {
        public InstanceStatus Status;
        public string Detail = "";
        public Mutex Mutex;
        /// <summary>둘째가 첫째의 상태 창을 부르는 이벤트 — 믿지 못하면 null.</summary>
        public EventWaitHandle Show;

        public static InstanceLock Acquire(string mutexName, string eventName, DirPolicy policy)
        {
            var r = new InstanceLock();
            bool created;
            try
            {
                var ms = new MutexSecurity();
                ms.SetOwner(policy.Owner);
                foreach (var t in policy.Trusted) ms.AddAccessRule(new MutexAccessRule(t, MutexRights.FullControl, AccessControlType.Allow));
                r.Mutex = new Mutex(true, mutexName, out created, ms);
                if (created) r.Status = InstanceStatus.First;
                else
                {
                    string owner;
                    if (TrustedOwner(r.Mutex.GetAccessControl().GetOwner(typeof(SecurityIdentifier)) as SecurityIdentifier, policy, out owner))
                        r.Status = InstanceStatus.Another;
                    else
                    {
                        r.Status = InstanceStatus.Squatted;
                        r.Detail = mutexName + " 의 주인이 " + owner;
                        r.Mutex.Dispose();
                        r.Mutex = null;
                    }
                }
            }
            catch (UnauthorizedAccessException)
            {
                r.Status = InstanceStatus.Squatted;
                r.Detail = mutexName + " 을 열 수 없습니다(권한)";
                r.Mutex = null;
            }
            catch (WaitHandleCannotBeOpenedException)
            {
                // 같은 이름의 다른 종류 객체(이벤트·세마포어 …) — 보통 권한의 프로그램이 먼저 만들 수 있다. 던지게 두면 도우미가 뜨지 못했다.
                r.Status = InstanceStatus.Squatted;
                r.Detail = mutexName + " 이 다른 종류의 객체입니다";
                r.Mutex = null;
            }

            try
            {
                var es = new EventWaitHandleSecurity();
                es.SetOwner(policy.Owner);
                foreach (var t in policy.Trusted) es.AddAccessRule(new EventWaitHandleAccessRule(t, EventWaitHandleRights.FullControl, AccessControlType.Allow));
                bool evCreated;
                r.Show = new EventWaitHandle(false, EventResetMode.AutoReset, eventName, out evCreated, es);
                string owner;
                if (!evCreated && !TrustedOwner(r.Show.GetAccessControl().GetOwner(typeof(SecurityIdentifier)) as SecurityIdentifier, policy, out owner))
                {
                    r.Show.Dispose();
                    r.Show = null;
                    r.Detail += (r.Detail.Length > 0 ? " · " : "") + eventName + " 의 주인이 " + owner;
                }
            }
            catch (UnauthorizedAccessException)
            {
                r.Show = null;
                r.Detail += (r.Detail.Length > 0 ? " · " : "") + eventName + " 을 열 수 없습니다(권한)";
            }
            catch (WaitHandleCannotBeOpenedException)
            {
                r.Show = null;
                r.Detail += (r.Detail.Length > 0 ? " · " : "") + eventName + " 이 다른 종류의 객체입니다";
            }
            return r;
        }

        static bool TrustedOwner(SecurityIdentifier sid, DirPolicy policy, out string name)
        {
            name = sid == null ? "없음" : sid.Value;
            if (sid != null) { try { name = sid.Translate(typeof(NTAccount)).Value; } catch (Exception) { } }
            return policy.IsTrusted(sid);
        }

        public void Dispose()
        {
            if (Show != null) { Show.Dispose(); Show = null; }
            if (Mutex != null) { Mutex.Dispose(); Mutex = null; }
        }
    }

    public static class Program
    {
#if !TEST_BUILD
        /// <summary>설치를 마친 뒤 이 트레이를 끝내고 설치본의 트레이를 띄운다(뮤텍스를 놓은 뒤에).</summary>
        static string relaunch;
#endif

        [STAThread]
        public static int Main(string[] args)
        {
            DllSafety.Harden();
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
#if TEST_BUILD
            return TestMain(args);
#else
            var mode = args.Length > 0 ? args[0] : "";
            switch (mode)
            {
                case "--service": return RunService();
                case "--install": return RunInstall();
                case "--uninstall": return RunUninstall();
                case "": return RunTray();
            }
            return 2;
#endif
        }

#if TEST_BUILD
        /// <summary>시험 판 — 같은 프로세스의 Controller(dry-run)를 트레이가 본다. 서비스·설치는 없다.</summary>
        static int TestMain(string[] args)
        {
            string home = null;
            int exitAfter = 0;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--home" && i + 1 < args.Length) home = args[++i];
                else if (args[i] == "--exit-after" && i + 1 < args.Length) exitAfter = int.Parse(args[++i]);
            }
            var policy = DirPolicy.CurrentUser();
            if (home == null) home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "WebTether-test");
            SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
            using (var inst = InstanceLock.Acquire(@"Local\WebTether-instance-test", @"Local\WebTether-show-test", policy))
            {
                if (inst.Status == InstanceStatus.Another)
                {
                    if (inst.Show != null) inst.Show.Set();
                    return 0;
                }
                var paths = AppPaths.Under(home);
                var notes = new List<string>();
                string err;
                using (var rootHold = SecureDir.Prepare(paths.Root, policy, notes, out err))
                {
                    if (rootHold == null)
                    {
                        MessageBox.Show("상태 폴더를 쓸 수 없어 시작하지 않습니다.\r\n" + err, AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
                        return 1;
                    }
                    ConsoleCtrl.IgnoreForThisProcess();
                    var log = new Log(paths.LogFile, Log.MaxFileBytes, paths.Root);
                    log.Write("시작", AppInfo.Name + " " + AppInfo.Version + " · 시험 판(dry-run) · 상태 " + paths.Root);
                    var problems = new List<string>();
                    var defaulted = new List<string>();
                    var settings = Settings.Load(paths.SettingsFile, problems, defaulted);
                    foreach (var p in problems) log.Write("설정", p);
                    var ctl = new Controller(paths, log, new DryRunOps(m => log.Write("시험", m)), new DryRunChildFactory(), BinarySet.Embedded(), true, false, settings);
                    ctl.LoadNotice = Settings.DefaultedNotice(string.Join(",", defaulted.ToArray()));
                    ctl.AskConnect = (hs, prefix) => TopAsk.Ask(StatusText.AskConnectText(hs, prefix));
                    ctl.ReshowAsk = () => TopAsk.BringToFront();
                    ctl.StartupCheck();
                    SettingsForm sf = null;
                    var opt = new TrayOptions
                    {
                        LogPath = paths.LogFile,
                        OpenSettings = () =>
                        {
                            if (sf == null || sf.IsDisposed) sf = new SettingsForm(ctl);
                            if (!sf.Visible) sf.Show();
                            sf.Activate();
                        },
                    };
                    var app = new TrayApp(ctl, ctl.Watcher, log, opt, inst.Show);
                    ctl.AutoConnect();
                    if (exitAfter > 0) app.ExitAfter(exitAfter);
                    Application.Run(app);
                }
            }
            return 0;
        }
#else
        /// <summary>SCM 이 띄운다 — LocalSystem, 세션 0.</summary>
        static int RunService()
        {
            var core = new ServiceCore(AppPaths.Default(), DirPolicy.AdminOnly(), false, PipeNames.Service, new WtsPrompt(), new WtsSessions());
            System.ServiceProcess.ServiceBase.Run(new WebTetherService(core));
            return 0;
        }

        static bool NeedAdmin()
        {
            if (Binaries.IsElevated()) return false;
            MessageBox.Show("관리자 권한으로 실행해야 합니다.", AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return true;
        }

        /// <summary>관리자 판 — 쥔 이 exe 로 설치·고치기. 트레이가 runas 로 띄우고 끝 코드를 본다(0 = 됨).</summary>
        static int RunInstall()
        {
            if (NeedAdmin()) return 1;
            var notes = new List<string>();
            string err;
            using (var self = SelfImage.Open(out err))
            {
                if (self == null)
                {
                    MessageBox.Show("이 exe 를 쥐지 못해 설치하지 않습니다: " + err, AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return 1;
                }
                var inst = new ServiceInstaller(new ScmManager(), new HklmRunKey(), SystemOps.Run, notes.Add);
                var exe = Application.ExecutablePath;
                inst.OtherRunning = () => OtherHelpers.Check(exe);
                if (!inst.Install(self.Stream, out err))
                {
                    MessageBox.Show("설치하지 못했습니다.\r\n" + err + "\r\n\r\n" + string.Join("\r\n", notes.ToArray()), AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return 1;
                }
            }
            return 0;
        }

        static int RunUninstall()
        {
            if (NeedAdmin()) return 1;
            var data = AppPaths.Default().Root;
            var removeData = MessageBox.Show("설정·로그(" + data + ")도 지울까요?", AppInfo.Name, MessageBoxButtons.YesNo, MessageBoxIcon.Question,
                                             MessageBoxDefaultButton.Button2) == DialogResult.Yes;
            var notes = new List<string>();
            string err;
            var inst = new ServiceInstaller(new ScmManager(), new HklmRunKey(), SystemOps.Run, notes.Add);
            if (!inst.Uninstall(removeData, data, out err))
            {
                MessageBox.Show("제거하지 못했습니다.\r\n" + err, AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
            MessageBox.Show("제거했습니다.\r\n" + string.Join("\r\n", notes.ToArray()), AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Information);
            return 0;
        }

        /// <summary>트레이 — 보통 권한. 세션마다 하나.</summary>
        static int RunTray()
        {
            SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
            var policy = DirPolicy.CurrentUser();
            using (var inst = InstanceLock.Acquire(@"Local\WebTether-tray", @"Local\WebTether-tray-show", policy))
            {
                if (inst.Status == InstanceStatus.Another)
                {
                    if (inst.Show != null) inst.Show.Set();
                    return 0;
                }
                var home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "WebTether");
                var notes = new List<string>();
                string err;
                var hold = SecureDir.Prepare(home, policy, notes, out err);
                var log = new Log(null); // 서비스의 로그를 받아 보인다(파일은 서비스가 쓴다)
                foreach (var n in notes) log.Write("트레이", n);
                if (hold == null) log.Write("트레이", "트레이 폴더를 쓰지 못함(업데이트 기억 없이): " + err);
                var upd = new UpdateWatcher(Path.Combine(home, "update"), hold != null ? home : null, log);
                var pipe = new PipeClient(PipeNames.Service);
                var client = new ServiceClient(pipe, log);
                // 설정 창 — 지금 설정은 서비스에서 읽고, 저장은 제안으로(서비스가 콘솔 사용자에게 확인 창으로 묻는다 — 사용자 결정 11-27).
                var settingsHost = new ServiceSettingsHost(new PipeClient(PipeNames.Service), () => client.ChildRunning);
                SettingsForm sf = null;
                var self = Application.ExecutablePath;
                TrayApp app = null;
                // 설치 전에 — 이전 판 도우미가 떠 있으면 설치하지 않는다(OtherHelpers, 11-28). -3 = 그래서 띄우지 않음.
                string otherText = null;
                Action install = () => Task.Run(() =>
                {
                    otherText = OtherHelpers.Check(self);
                    return otherText != null ? -3 : Elevation.Run(self, "--install");
                }).ContinueWith(t =>
                {
                    bool ok = !t.IsFaulted && t.Result == 0;
                    var msg = ok ? "서비스를 설치했습니다." : !t.IsFaulted && t.Result == -2 ? Elevation.LastBlocked :
                              !t.IsFaulted && t.Result == -3 ? otherText : "서비스를 설치하지 못했습니다.";
                    if (app != null) app.Balloon(AppInfo.Name, msg, !ok, null);
                    if (ok && !StartupPaths.Same(self, ServicePlan.InstalledExe))
                    {
                        relaunch = ServicePlan.InstalledExe; // 이 트레이를 끝내고 설치본의 트레이를 띄운다
                        Application.Exit();
                    }
                }, TaskScheduler.FromCurrentSynchronizationContext());
                Action uninstall = () =>
                {
                    if (!TopAsk.Ask("서비스를 제거할까요? 연결이 끊깁니다.", AppInfo.Name, true)) return;
                    Task.Run(() => Elevation.Run(self, "--uninstall")).ContinueWith(t =>
                    {
                        if (!t.IsFaulted && t.Result == 0) Application.Exit();
                        else if (!t.IsFaulted && t.Result == -2 && app != null) app.Balloon(AppInfo.Name, Elevation.LastBlocked, true, null);
                    }, TaskScheduler.FromCurrentSynchronizationContext());
                };
                var opt = new TrayOptions
                {
                    OpenSettings = () => Task.Run(() => settingsHost.Refresh()).ContinueWith(t =>
                    {
                        var why = t.IsFaulted ? "까닭 모름" : t.Result;
                        if (why != null)
                        {
                            if (app != null) app.Balloon(AppInfo.Name, "설정을 읽지 못했습니다: " + why, true, null);
                            return;
                        }
                        if (sf == null || sf.IsDisposed) sf = new SettingsForm(settingsHost);
                        if (sf.Visible) sf.LoadFrom(settingsHost.Settings);
                        else sf.Show();
                        sf.Activate();
                    }, TaskScheduler.FromCurrentSynchronizationContext()),
                    Install = install,
                    Uninstall = uninstall,
                    ChecksUpdates = true,
                    FetchServiceLog = client.FetchLog,
                    LogPath = "서비스가 씁니다 — " + AppPaths.Default().LogFile,
                };
                app = new TrayApp(client, upd, log, opt, inst.Show);
                // 서비스가 없거나 판이 다르면 한 번 묻는다(메시지 고리가 돈 뒤에).
                var startup = new System.Windows.Forms.Timer { Interval = 1500 };
                startup.Tick += (s, e) =>
                {
                    startup.Stop();
                    startup.Dispose();
                    Task.Run(() => ServiceCheck.Look(self)).ContinueWith(t =>
                    {
                        if (t.IsFaulted) return;
                        var ask = ServiceCheck.AskText(t.Result);
                        if (ask != null && t.Result.Others != null)
                        {
                            // 묻기 전에 — 이전 판 도우미가 떠 있으면 묻지 않고 알린다.
                            if (app != null) app.Balloon(AppInfo.Name, t.Result.Others, true, null);
                            return;
                        }
                        if (ask != null && TopAsk.Ask(ask, AppInfo.Name + " — 서비스", true)) install();
                    }, TaskScheduler.FromCurrentSynchronizationContext());
                };
                startup.Start();
                Application.Run(app);
                if (hold != null) hold.Dispose();
            }
            if (relaunch != null)
            {
                var why = TrayRelaunch.Run(relaunch, p => { using (System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(p) { UseShellExecute = false })) { } });
                if (why != null) MessageBox.Show(why, AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
            return 0;
        }
#endif
    }

    /// <summary>경로 견주기(대소문자·끝의 역슬래시 무시).</summary>
    public static class StartupPaths
    {
        public static bool Same(string a, string b)
        {
            try { return string.Equals(Path.GetFullPath(a).TrimEnd('\\'), Path.GetFullPath(b).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase); }
            catch (Exception) { return false; }
        }
    }

    /// <summary>설치 뒤 설치본의 트레이를 띄운다 — 못 띄우면 사용자가 볼 글(되면 null). 예전에는 실패를 삼켜 트레이가 조용히 사라졌다(11-28).</summary>
    public static class TrayRelaunch
    {
        public static string Run(string exe, Action<string> start)
        {
            try
            {
                start(exe);
                return null;
            }
            catch (Exception ex)
            {
                return "설치는 됐지만 트레이를 다시 띄우지 못했습니다: " + SafeText.Clean(ex.Message, 200) + "\r\n" + exe + " 를 실행하거나 다시 로그온하십시오.";
            }
        }
    }

    /// <summary>트레이가 뜰 때 — 서비스가 있는가, 이 exe 와 같은 판인가(읽기만 — SCM 상태·파이프 hello·이 exe 의 해시).</summary>
    public static class ServiceCheck
    {
        public sealed class Result
        {
            public ServiceState State;
            /// <summary>서비스가 알린 exe 해시(모르면 "").</summary>
            public string ServiceHash = "";
            public string MyHash = "";
            public bool RunningInstalled;
            /// <summary>이전 판 도우미 등 다른 WebTether.exe 가 떠 있으면 그 글(OtherHelpers).</summary>
            public string Others;
        }

        public static Result Look(string self)
        {
            var r = new Result { State = ScmManager.QueryReadOnly(ServicePlan.Name), RunningInstalled = StartupPaths.Same(self, ServicePlan.InstalledExe) };
            string err;
            using (var me = SelfImage.Open(out err))
                if (me != null) { me.Stream.Position = 0; r.MyHash = Binaries.Sha256(me.Stream); }
            try { r.Others = OtherHelpers.Check(self); } catch (Exception) { }
            if (r.State == ServiceState.Running)
            {
                var h = new PipeClient(PipeNames.Service).Send(PipeCommands.Request(PipeCommands.Hello), out err);
                string v;
                if (h != null && h.TryGetValue("exe", out v)) r.ServiceHash = SafeText.Clean(v, 80);
            }
            return r;
        }

        /// <summary>물을 글(묻지 않으면 null) — 순수, 시험한다.</summary>
        public static string AskText(Result r)
        {
            if (r == null) return null;
            if (r.State == ServiceState.Missing) return "서비스가 설치되어 있지 않습니다.\r\n설치할까요? (관리자 승인)";
            if (r.RunningInstalled) return null;
            if (r.State == ServiceState.Running && r.ServiceHash.Length > 0 && r.MyHash.Length > 0 && r.ServiceHash != r.MyHash)
                return "서비스가 이 판과 다릅니다.\r\n이 판으로 바꿀까요? (관리자 승인 — 잠깐 끊깁니다)";
            return null;
        }
    }
}
