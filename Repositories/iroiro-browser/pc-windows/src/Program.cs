// 들어가는 곳. 하나만 뜬다(이름 있는 뮤텍스) — 둘째는 첫째의 상태 창을 앞으로 부르고 끝난다.
//
// 시험 판(TEST_BUILD — 매니페스트 asInvoker)은 늘 dry-run 이다: tun2proxy 를 띄우지 않고, 경로·Wi-Fi·작업 스케줄러를
// 바꾸지 않고 기록만 한다. 상태 폴더는 --home <폴더>(없으면 %LOCALAPPDATA%\WebTether-test) — 이 사용자 전용 권한으로 만든다.
// 실제 판은 %ProgramData%\WebTether(관리자 전용 — SecureFs).
// 뜨면 늘 저절로 연결한다 — 사용자의 '끊기' 는 그 실행 동안만 듣는다(Controller.AutoConnectAsync). Wi-Fi 이름이 접두어로 시작할 때만.
using System;
using System.Collections.Generic;
using System.IO;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Threading;
using System.Windows.Forms;

namespace WebTether
{
    public enum InstanceStatus { First, Another, Squatted }

    /// <summary>
    /// 하나만 실행 — 실제 판은 Global\ 이름에 관리자 전용 보안 설명자로 만든다. 이미 있으면 주인을 본다: 정책의 SID 가 아니면
    /// 보통 권한의 프로그램이 먼저 잡은 것이다(그대로면 도우미가 '이미 떠 있다' 며 뜨지 못한다) — 알리고 뜨되, 하나만 실행은 상태 폴더의
    /// 잠금 파일(InstanceFile)이 지킨다.
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

    /// <summary>
    /// 하나만 실행의 둘째 겹 — 상태 폴더 안의 잠금 파일을 공유 없이 쥔다. 실제 판의 상태 폴더는 관리자 전용이라 보통 권한의 프로그램이
    /// 먼저 잡을 수 없다. Global\ 표지를 남이 먼저 잡아(Squatted) 하나만 실행을 믿지 못할 때, 이것이 없으면 둘째 도우미도 떠서
    /// 둘이 함께 저절로 연결하고(시작 직후의 기다림이 같은 때 끝나면 둘 다 '다른 tun2proxy 없음' 을 보고 띄운다) 같은 상태 파일·경로를 다뤘다.
    /// </summary>
    public static class InstanceFile
    {
        /// <summary>쥐면 그 스트림, 다른 도우미가 쥐고 있으면 null 과 busy, 그 밖의 실패면 null 과 까닭(그때는 막지 않는다).</summary>
        public static FileStream TryHold(AppPaths paths, out bool busy, out string error)
        {
            busy = false;
            error = null;
            try
            {
                SecureDir.CheckNoReparse(paths.Root, paths.InstanceFile);
                return new FileStream(paths.InstanceFile, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            }
            catch (IOException ex)
            {
                int code = ex.HResult & 0xFFFF;
                busy = code == 32 || code == 33; // ERROR_SHARING_VIOLATION · ERROR_LOCK_VIOLATION
                error = ex.Message;
                return null;
            }
            catch (UnauthorizedAccessException ex)
            {
                error = ex.Message;
                return null;
            }
        }
    }

    public static class Program
    {
        [STAThread]
        public static int Main(string[] args)
        {
            bool dryRun = false;
            string home = null;
            int exitAfter = 0;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--dry-run") dryRun = true;
#if TEST_BUILD
                else if (args[i] == "--home" && i + 1 < args.Length) home = args[++i];
                else if (args[i] == "--exit-after" && i + 1 < args.Length) exitAfter = int.Parse(args[++i]);
#endif
            }
#if TEST_BUILD
            dryRun = true;
            var policy = DirPolicy.CurrentUser();
            const string mutexName = @"Local\WebTether-instance-test", eventName = @"Local\WebTether-show-test";
            if (home == null) home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "WebTether-test");
#else
            var policy = DirPolicy.AdminOnly();
            const string mutexName = @"Global\WebTether-instance", eventName = @"Global\WebTether-show";
#endif
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            // Controller 가 자식의 끝남을 UI 스레드로 넘기려고 지금의 문맥을 쥔다 — 첫 창보다 먼저 만든다.
            SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());

            using (var inst = InstanceLock.Acquire(mutexName, eventName, policy))
            {
                if (inst.Status == InstanceStatus.Another)
                {
#if !TEST_BUILD
                    // 떠 있는 것이 로그온 때 뜬 옛 사본이고 이 exe 와 다르면 — 그대로 끝나면 사용자는 옛 판이 왜 남는지 모른다.
                    if (TellIfOldCopyRunning()) return 0;
#endif
                    if (inst.Show != null) inst.Show.Set();
                    return 0;
                }
#if !TEST_BUILD
                if (!dryRun && !Binaries.IsElevated())
                {
                    MessageBox.Show("관리자 권한으로 실행해야 합니다.", AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return 1;
                }
#endif
                var paths = home != null ? AppPaths.Under(home) : AppPaths.Default();
                var notes = new List<string>();
                string err;
                var rootHold = SecureDir.Prepare(paths.Root, policy, false, notes, out err);
                if (rootHold == null)
                {
                    MessageBox.Show("상태 폴더를 쓸 수 없어 시작하지 않습니다.\r\n" + err, AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return 1;
                }
                // 둘째 겹 — 상태 폴더의 잠금 파일. 표지를 남이 먼저 잡았어도 도우미가 둘 뜨지 않는다.
                bool busy;
                string singleErr;
                var single = InstanceFile.TryHold(paths, out busy, out singleErr);
                if (busy)
                {
                    rootHold.Dispose();
#if !TEST_BUILD
                    if (TellIfOldCopyRunning()) return 0;
#endif
                    MessageBox.Show("도우미가 이미 실행 중입니다 — 알림 영역의 아이콘을 쓰십시오.", AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Information);
                    return 0;
                }
                if (inst.Status == InstanceStatus.Squatted)
                    MessageBox.Show("다른 프로그램이 이 도우미의 실행 표지를 먼저 만들었습니다(" + inst.Detail + ").\r\n" +
                                    "상태 폴더의 잠금으로 하나만 실행을 지키며 계속합니다. 낯선 프로그램이 돌고 있지 않은지 확인하십시오.",
                                    AppInfo.Name, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                SelfImage self = null;
                try
                {
                    ConsoleCtrl.IgnoreForThisProcess();
                    var log = new Log(paths.LogFile, Log.MaxFileBytes, paths.Root);
                    log.Write("시작", AppInfo.Name + " " + AppInfo.Version + " · tun2proxy " + Embedded.Tun2proxyVersion + (dryRun ? " · dry-run" : "") +
                              " · 상태 " + paths.Root + " (" + policy.Name + ")");
                    foreach (var n in notes) log.Write("상태 폴더", n);
                    if (inst.Status == InstanceStatus.Squatted || inst.Detail.Length > 0) log.Write("실행 표지", "믿지 못함 — " + inst.Detail);
                    if (single == null) log.Write("실행 표지", "상태 폴더의 잠금 파일을 쥐지 못함(막지 않고 계속): " + singleErr);
#if !TEST_BUILD
                    string selfErr;
                    self = SelfImage.Open(out selfErr);
                    if (self == null) log.Write("시작", "이 exe 를 쥐지 못함 — '시작 시 실행' 을 켤 수 없습니다: " + selfErr);
#endif
                    var problems = new List<string>();
                    var settings = Settings.Load(paths.SettingsFile, problems);
                    foreach (var p in problems) log.Write("설정", p);

                    ISystemOps ops = dryRun ? (ISystemOps)new DryRunOps(m => log.Write("시험", m)) : new SystemOps(self, m => log.Write("시작 시 실행", m));
                    IChildFactory factory = dryRun ? (IChildFactory)new DryRunChildFactory() : new RealChildFactory();
                    var ctl = new Controller(paths, log, ops, factory, BinarySet.Embedded(), dryRun, !dryRun, settings);
                    ctl.StartupCheck(); // 일꾼 스레드에서 — 끝나기 전에는 연결하지 않는다
                    try
                    {
                        var app = new TrayApp(ctl, log, paths, ops, inst.Show);
                        ctl.AutoConnect(); // 앞의 걷기가 끝난 뒤에 띄운다
                        if (exitAfter > 0) app.ExitAfter(exitAfter); // 시험 판만 — 트레이 아이콘을 오래 두지 않는다
                        Application.Run(app);
                    }
                    catch (Exception ex)
                    {
                        log.Write("오류", ex.ToString());
                        throw;
                    }
                }
                finally
                {
                    if (self != null) self.Dispose();
                    if (single != null) single.Dispose();
                    rootHold.Dispose();
                }
            }
            return 0;
        }

        /// <summary>
        /// 이미 떠 있는 도우미 때문에 끝나는 둘째 — '시작 시 실행' 사본이 다른 프로세스로 돌고 있고 이 exe 와 내용이 다르면(로그온 때 뜬 옛 판)
        /// 맨 앞 창으로 '옛 판을 끝낸 뒤 다시 실행' 을 알리고 참. 견주기(schtasks 물음·해시)는 일꾼 스레드에서, 20초까지만 기다린다.
        /// </summary>
        static bool TellIfOldCopyRunning()
        {
            string err;
            using (var self = SelfImage.Open(out err))
            {
                if (self == null) return false;
                var ops = new SystemOps(self, null);
                var check = System.Threading.Tasks.Task.Run(() => ops.CheckStartupCopy());
                if (!check.Wait(20000)) return false;
                if (StartupCopy.Decide(check.Result) != CopyPrompt.OldRunning) return false;
            }
            TopAsk.Tell(StartupCopy.OldRunningText, StartupCopy.Title, true);
            return true;
        }
    }
}
