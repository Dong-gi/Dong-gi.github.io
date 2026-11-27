// Windows 서비스(LocalSystem) — 관리자 권한이 드는 일(tun2proxy·경로 걷기·상태 폴더)을 맡는다. 트레이는 보통 권한으로 파이프로 묻는다.
//
// LocalSystem 으로 뜨면 SCM 이 주는 환경은 시스템 환경(HKLM)과 서비스 키의 것뿐이다 — 로그온 사용자의 HKCU\Environment·
// HKCU\Software\Microsoft\.NETFramework 는 닿지 않는다(예전 '시작 시 실행' 의 예약 작업은 사용자의 환경으로 관리자 프로세스를 띄웠다).
//
// 부팅 때 자동으로 뜨고 저절로 연결한다(사용자 결정 11-27 — 로그온 전에도, 로그오프 뒤에도). 콘솔에 로그온한 사용자가 없으면 물을 사람이
// 없으므로 이름이 접두어 밖인 Wi-Fi 에는 연결하지 않는다(저절로 하는 시도는 원래 묻지 않는다 — 사용자가 로그온해 트레이의 '연결' 을 누르면
// 그때 서비스가 WTSSendMessage 로 묻는다).
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.ServiceProcess;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether
{
    /// <summary>콘솔 세션과 그 사용자(시험이 바꾼다).</summary>
    public interface ISessionInfo
    {
        /// <summary>지금 콘솔 세션에 로그온한 사용자가 있으면 그 세션 번호, 없으면 -1.</summary>
        int ConsoleUserSession();
    }

    /// <summary>사용자에게 묻기 — (세션, 제목, 글, 시한 초) → 예/아니요/모름(null). 시험은 가짜로(창을 띄우지 않는다).</summary>
    public interface IUserPrompt
    {
        bool? Ask(int session, string title, string text, int timeoutSeconds);
    }

    public sealed class WtsSessions : ISessionInfo
    {
        [DllImport("kernel32.dll")] static extern uint WTSGetActiveConsoleSessionId();
        [DllImport("wtsapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool WTSQuerySessionInformationW(IntPtr server, uint session, int cls, out IntPtr buf, out int bytes);
        [DllImport("wtsapi32.dll")] static extern void WTSFreeMemory(IntPtr p);
        const int WTSUserName = 5;

        public int ConsoleUserSession()
        {
            uint s = WTSGetActiveConsoleSessionId();
            if (s == 0xFFFFFFFF) return -1;
            IntPtr buf;
            int bytes;
            if (!WTSQuerySessionInformationW(IntPtr.Zero, s, WTSUserName, out buf, out bytes)) return -1;
            try { return string.IsNullOrEmpty(Marshal.PtrToStringUni(buf)) ? -1 : (int)s; }
            finally { WTSFreeMemory(buf); }
        }
    }

    /// <summary>
    /// 서비스가 콘솔 세션의 사용자에게 직접 묻는다(WTSSendMessage — 세션의 시스템 쪽이 띄우는 창이라 그 세션의 보통 권한 프로그램이
    /// 입력을 넣지 못한다고 본다(UIPI) — 기기에서 확인할 것). 기본 단추는 '아니요', 시한이 지나면 '아니요'.
    /// </summary>
    public sealed class WtsPrompt : IUserPrompt
    {
        [DllImport("wtsapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool WTSSendMessageW(IntPtr server, uint session, string title, int titleBytes, string message, int messageBytes,
                                           uint style, int timeout, out int response, bool wait);
        const uint MB_YESNO = 0x4, MB_ICONWARNING = 0x30, MB_DEFBUTTON2 = 0x100, MB_TOPMOST = 0x40000, MB_SETFOREGROUND = 0x10000;
        const int IDYES = 6;

        public bool? Ask(int session, string title, string text, int timeoutSeconds)
        {
            int resp;
            if (!WTSSendMessageW(IntPtr.Zero, (uint)session, title, title.Length * 2, text, text.Length * 2,
                                 MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2 | MB_TOPMOST | MB_SETFOREGROUND, timeoutSeconds, out resp, true))
                return null;
            return resp == IDYES;
        }
    }

    /// <summary>
    /// 서비스의 몸통 — ServiceBase 와 떼어 시험한다(--service-console·시험은 보통 권한으로 dry-run). Controller 를 자기 UI 스레드(창 없이 도는
    /// 메시지 고리 — Controller 가 SynchronizationContext 로 일을 넘긴다)에서 돌리고, 파이프로 트레이의 명령을 받는다.
    /// </summary>
    public sealed class ServiceCore
    {
        readonly AppPaths paths;
        readonly DirPolicy policy;
        readonly bool dryRun;
        readonly string pipeName;
        readonly IUserPrompt prompt;
        readonly ISessionInfo sessions;
        Thread uiThread;
        SynchronizationContext ui;
        ApplicationContext appCtx;
        SecureDir.Held rootHold;
        PipeServer pipe;
        readonly object gate = new object();
        Dictionary<string, string> snapshot;
        long noticeSeq;
        string noticeTitle = "", noticeText = "", noticeUrl = "";
        bool noticeWarn, asking;
        // 묻는 창은 한 번에 하나(Wi-Fi 물음과 설정 확인이 함께) — askGate 아래에서 askBusy 를 본다.
        readonly object askGate = new object();
        bool askBusy;
        // 제안 번호 — 서비스가 뜰 때마다 무작위로 시작한다(검토 11-27: 다시 뜬 서비스의 번호가 트레이가 기다리는 옛 번호와 겹치지 않게).
        long proposalId = RandomProposalBase();
        string proposalResult = "";
        DateTime proposalQuietUntil = DateTime.MinValue;
        int proposalRejects; // 잇달아 거절·시간 초과된 수(적용되거나 ProposalRejectWindowMs 가 지나면 0)
        DateTime proposalLastReject = DateTime.MinValue;
        readonly List<KeyValuePair<long, string>> proposalRecent = new List<KeyValuePair<long, string>>();
        System.Threading.Timer pipeRetry;
        int pipeRetryFails;
        // 시작할 때 파일 읽기가 기본값으로 돌린 키(쉼표로) — 설정 창이 한 줄로 알린다. 제안이 적용되면 지운다.
        string settingsDefaulted = "";
        readonly List<string> logLines = new List<string>();
        long logFirst; // logLines[0] 의 번호
        public const int LogKeep = 3000;

        public Log Log { get; private set; }
        public AppPaths Paths { get { return paths; } }
        public Controller Controller { get; private set; }
        public PipeServer Pipe { get { return pipe; } }
        /// <summary>Controller 를 만든 뒤 부른다(시험이 가짜를 끼운다 — UI 스레드에서).</summary>
        public Action<Controller> Configure;
        /// <summary>시험 판: 파이프에 자기 SID 를 넣는다(보통 권한으로 인스턴스를 더 만들 수 있게).</summary>
        public bool PipeForCurrentUser;
        /// <summary>파이프 손님 확인의 콘솔 세션(시험이 바꾼다 — null 이면 WTS).</summary>
        public Func<int> ConsoleSession;
        /// <summary>파이프 손님의 신원 읽기(시험이 바꾼다 — null 이면 실제 토큰).</summary>
        public Func<Microsoft.Win32.SafeHandles.SafePipeHandle, PipeClientInfo> Identify;
        public int AskTimeoutSeconds = 60;
        /// <summary>설정 확인을 거절(또는 시간 초과)한 뒤 새 제안을 받지 않는 동안(피로 공격 — 시험이 줄인다).</summary>
        public int ProposalQuietMs = 90 * 1000;
        /// <summary>거절이 되풀이되면(둘째부터) 쉬는 동안 — 검토 11-27.</summary>
        public int ProposalQuietLongMs = 300 * 1000;
        /// <summary>어떤 결과든 끝난 뒤 쉬는 동안 — 사용자가 저장한 직후 남의 제안이 끼어들지 않게(검토 11-27).</summary>
        public int ProposalAfterMs = 10 * 1000;
        /// <summary>앞의 거절에서 이만큼 지나면 되풀이 셈을 0 으로(검토 11-27 — 하루에 두 번 거절했다고 늘 5분 쉬지 않게).</summary>
        public int ProposalRejectWindowMs = 10 * 60 * 1000;
        /// <summary>제안을 받는 프로그램 — 설치본의 트레이(이미지 경로가 같을 때만). 시험이 바꾼다.</summary>
        public string ProposerImage = ServicePlan.InstalledExe;
        /// <summary>파이프 이름을 잡지 못했으면 다시 해 보는 간격(시험이 줄인다).</summary>
        public int PipeRetryMs = 5000;
        /// <summary>서비스 exe 의 SHA-256(트레이가 판이 같은지 본다). 실제 판은 Start 가 쥔 이 exe 로 센다.</summary>
        public string ExeHash = "";

        public ServiceCore(AppPaths paths, DirPolicy policy, bool dryRun, string pipeName, IUserPrompt prompt, ISessionInfo sessions)
        {
            this.paths = paths;
            this.policy = policy;
            this.dryRun = dryRun;
            this.pipeName = pipeName;
            this.prompt = prompt;
            this.sessions = sessions;
        }

        /// <summary>상태 폴더·로그·Controller·파이프. 실패하면 false 와 까닭(연결 일은 파이프가 없어도 한다 — 파이프 실패는 로그만).</summary>
        public bool Start(out string error)
        {
            error = null;
            var notes = new List<string>();
            rootHold = SecureDir.Prepare(paths.Root, policy, notes, out error);
            if (rootHold == null) return false;
            Log = new Log(paths.LogFile, Log.MaxFileBytes, paths.Root);
            Log.Line += KeepLine;
            Log.Write("서비스", AppInfo.Name + " " + AppInfo.Version + " · tun2proxy " + Embedded.Tun2proxyVersion + (dryRun ? " · dry-run" : "") +
                              " · 상태 " + paths.Root + " (" + policy.Name + ")");
            foreach (var n in notes) Log.Write("상태 폴더", n);
            WarnClrVariables();
            if (!dryRun)
            {
                string serr;
                using (var selfImg = SelfImage.Open(out serr))
                    if (selfImg != null) { selfImg.Stream.Position = 0; ExeHash = Binaries.Sha256(selfImg.Stream); }
            }
            var ready = new ManualResetEventSlim(false);
            Exception failed = null;
            uiThread = new Thread(() =>
            {
                try
                {
                    SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
                    ui = SynchronizationContext.Current;
                    ConsoleCtrl.IgnoreForThisProcess();
                    var problems = new List<string>();
                    var defaulted = new List<string>();
                    var settings = Settings.Load(paths.SettingsFile, problems, defaulted);
                    foreach (var p in problems) Log.Write("설정", p);
                    lock (gate) settingsDefaulted = string.Join(",", defaulted.ToArray());
                    IChildFactory factory = dryRun ? (IChildFactory)new DryRunChildFactory() : new RealChildFactory();
                    ISystemOps ops = dryRun ? (ISystemOps)new DryRunOps(m => Log.Write("시험", m)) : new SystemOps(m => Log.Write("시스템", m));
                    var ctl = new Controller(paths, Log, ops, factory, BinarySet.Embedded(), dryRun, !dryRun, settings);
                    ctl.UpdatesEnabled = false;
                    ctl.AskConnect = AskUser;
                    ctl.Changed += Snapshot;
                    ctl.Notice += (t, x, w) =>
                    {
                        lock (gate)
                        {
                            noticeSeq++;
                            noticeTitle = t;
                            noticeText = x;
                            noticeWarn = w;
                            noticeUrl = ctl.NoticeUrl ?? "";
                        }
                        Snapshot();
                    };
                    Controller = ctl;
                    if (Configure != null) Configure(ctl);
                    Snapshot();
                    var timer = new System.Windows.Forms.Timer { Interval = 1000 };
                    timer.Tick += (s, e) => { ctl.Tick(); Snapshot(); };
                    timer.Start();
                    ctl.StartupCheck();
                    ctl.AutoConnect(); // 부팅 때부터 — 앞의 걷기가 끝난 뒤에 띄운다
                    appCtx = new ApplicationContext();
                    ready.Set();
                    Application.Run(appCtx);
                    timer.Dispose();
                }
                catch (Exception ex)
                {
                    failed = ex;
                    Log.Write("오류", ex.ToString());
                    ready.Set();
                }
            }) { IsBackground = true, Name = "service-ui" };
            uiThread.SetApartmentState(ApartmentState.STA);
            uiThread.Start();
            ready.Wait();
            if (failed != null) { error = failed.Message; return false; }

            var me = PipeForCurrentUser ? WindowsIdentity.GetCurrent().User : null;
            pipe = new PipeServer(pipeName, PipeAcl.Build(me), Handle, m => Log.Write("파이프", m));
            if (ConsoleSession != null) pipe.ConsoleSession = ConsoleSession;
            if (Identify != null) pipe.Identify = Identify;
            if (!pipe.Start()) Log.Write("파이프", pipe.StartError + " — 트레이와 이야기하지 못합니다(연결 일은 그대로 합니다). " +
                                         (PipeRetryMs / 1000.0).ToString("0.#", CultureInfo.InvariantCulture) + "초마다 다시 해 봅니다");
            // 이름을 남이 먼저 잡았거나(선점) 고리가 끝났으면 다시 잡는다 — 트레이는 서비스의 PID 로 서버를 가리므로 그동안 남의 서버를 믿지 않는다.
            pipeRetry = new System.Threading.Timer(_ => RetryPipe(), null, PipeRetryMs, PipeRetryMs);
            return true;
        }

        void RetryPipe()
        {
            var p = pipe;
            if (p == null || p.Listening) return;
            if (p.Start())
            {
                Log.Write("파이프", "파이프를 다시 열었습니다");
                pipeRetryFails = 0;
            }
            else if (++pipeRetryFails % 12 == 0) Log.Write("파이프", "아직 열지 못함 — " + p.StartError);
        }

        /// <summary>시스템 환경에 CLR 이 먼저 읽는 변수가 있으면 적는다(관리자가 둔 것이라 막지는 않는다 — 알린다).</summary>
        void WarnClrVariables()
        {
            foreach (System.Collections.DictionaryEntry e in Environment.GetEnvironmentVariables())
            {
                var k = (string)e.Key;
                if (k.StartsWith("COR_", StringComparison.OrdinalIgnoreCase) || k.StartsWith("COMPlus_", StringComparison.OrdinalIgnoreCase))
                    Log.Write("서비스", "시스템 환경에 CLR 변수가 있습니다: " + k);
            }
        }

        void KeepLine(string line)
        {
            lock (gate)
            {
                logLines.Add(line);
                if (logLines.Count > LogKeep) logLines.RemoveRange(0, logLines.Count - LogKeep);
                logTotal++;
                logFirst = logTotal - logLines.Count;
            }
        }
        long logTotal;

        /// <summary>UI 스레드 — Controller 의 지금 모습을 파이프가 읽을 꼴로.</summary>
        void Snapshot()
        {
            var c = Controller;
            if (c == null) return;
            var st = new ServiceStatus
            {
                State = c.State, Detail = c.Detail, Toggle = c.Toggle, ChildRunning = c.ChildRunning, ChildPid = c.ChildPid, DryRun = c.DryRun,
                Version = AppInfo.Version + "+" + Embedded.BundleId, Settings = c.Settings,
            };
            lock (gate)
            {
                st.NoticeSeq = noticeSeq;
                st.NoticeTitle = noticeTitle;
                st.NoticeText = noticeText;
                st.NoticeWarn = noticeWarn;
                st.NoticeUrl = noticeUrl;
                st.Asking = asking;
                st.ProposalId = proposalId;
                st.ProposalResult = proposalResult;
                st.ProposalRecent = new List<KeyValuePair<long, string>>(proposalRecent);
                snapshot = st.ToMap();
            }
        }

        /// <summary>파이프 스레드 — 명령은 UI 스레드로 넘기고 곧바로 답한다(상태는 미리 지어 둔 것).</summary>
        Dictionary<string, string> Handle(PipeClientInfo who, Dictionary<string, string> req)
        {
            var cmd = req["cmd"];
            var ok = new Dictionary<string, string> { { "ok", "1" } };
            switch (cmd)
            {
                case PipeCommands.Hello:
                    ok["version"] = AppInfo.Version + "+" + Embedded.BundleId;
                    ok["protocol"] = PipeCommands.ProtocolVersion.ToString(CultureInfo.InvariantCulture);
                    ok["exe"] = ExeHash;
                    return ok;
                case PipeCommands.Status:
                    lock (gate) return snapshot != null ? new Dictionary<string, string>(snapshot) : ok;
                case PipeCommands.Connect:
                    Log.Write("파이프", "연결 — " + who.Sid + " 세션 " + who.SessionId);
                    Post(() => Controller.Connect());
                    return ok;
                case PipeCommands.Disconnect:
                    Log.Write("파이프", "끊기 — " + who.Sid + " 세션 " + who.SessionId);
                    Post(() => Controller.Disconnect());
                    return ok;
                case PipeCommands.Settings:
                    {
                        var c = Controller;
                        if (c == null) return new Dictionary<string, string> { { "ok", "0" }, { "error", "아직 준비되지 않았습니다" } };
                        foreach (var kv in c.Settings.Pairs()) ok[PipeCommands.SettingPrefix + kv.Key] = kv.Value;
                        lock (gate) if (settingsDefaulted.Length > 0) ok["defaulted"] = settingsDefaulted;
                        return ok;
                    }
                case PipeCommands.Propose:
                    return Propose(who, req);
                case PipeCommands.Log:
                    long after = long.Parse(req.ContainsKey("after") ? req["after"] : "0", CultureInfo.InvariantCulture);
                    return LogReply(after);
            }
            return new Dictionary<string, string> { { "ok", "0" }, { "error", "모르는 명령" } };
        }

        Dictionary<string, string> LogReply(long after)
        {
            var sb = new StringBuilder();
            long next;
            lock (gate)
            {
                long i = Math.Max(after, logFirst);
                for (; i < logTotal; i++)
                {
                    var l = logLines[(int)(i - logFirst)];
                    if (sb.Length + l.Length + 1 > 48 * 1024) break;
                    if (sb.Length > 0) sb.Append('\n');
                    sb.Append(l);
                }
                next = i;
            }
            return new Dictionary<string, string> { { "ok", "1" }, { "lines", sb.ToString() }, { "next", next.ToString(CultureInfo.InvariantCulture) } };
        }

        /// <summary>
        /// 트레이의 설정 제안(사용자 결정 11-27 — 관리자 .NET 프로세스를 두지 않으려고). 검사기로 먼저 거르고(틀리면 묻지도 않는다), 콘솔 세션의
        /// 사용자에게 바뀌는 칸마다 '이름: 옛값 → 새값' 을 보이며 묻고, '예' 면 **확인 창의 글을 지은 그 객체**를 저장하고 건다. 묻는 창은 한 번에
        /// 하나, 끝나면 쉰다(FinishProposal — 어떤 결과든 ProposalAfterMs, 거절·시간 초과면 ProposalQuietMs, 10분 안에 되풀이되면 ProposalQuietLongMs).
        /// 보낸 프로그램은 설치본과 **같은 파일**이어야 한다(FileIdentity). 곧바로 답하고(묻는 동안 asking) 결과는 상태의 proposal_* 로 알린다.
        /// </summary>
        Dictionary<string, string> Propose(PipeClientInfo who, Dictionary<string, string> req)
        {
            // 설치본의 트레이만 — 같은 사용자의 다른 프로그램이 보낸 제안은 묻지도 않는다(트레이에 주입하면 비켜 가므로 완전하지 않다).
            if (!FileIdentity.Same(who.Image, ProposerImage))
            {
                Log.Write("설정", "제안 거절 — 설치본의 트레이가 아님: " + SafeText.Clean(who.Image, 300) + " (PID " + who.Pid + ")");
                return ProposalReply(ProposalResults.Program, null);
            }
            string why;
            var s = Settings.FromProposal(req, PipeCommands.SettingPrefix, out why);
            if (s == null)
            {
                Log.Write("설정", "제안 거절(값이 틀림): " + why);
                return ProposalReply(ProposalResults.Invalid, null);
            }
            var c = Controller;
            if (c == null) return ProposalReply(ProposalResults.Busy, null);
            string reject;
            var text = SettingsConfirm.Text(c.Settings, s, who.Image, who.Pid, out reject);
            if (reject != null)
            {
                Log.Write("설정", "제안 거절: " + reject);
                return ProposalReply(ProposalResults.Invalid, null);
            }
            if (text == null) return ProposalReply(ProposalResults.Unchanged, null);
            int ses = sessions.ConsoleUserSession();
            if (ses < 0 || ses != who.SessionId)
            {
                Log.Write("설정", "제안 거절 — 손님의 세션(" + who.SessionId + ")이 콘솔 세션(" + ses + ")이 아님");
                return ProposalReply(ProposalResults.Session, null);
            }
            long id;
            lock (askGate)
            {
                if (askBusy) return ProposalReply(ProposalResults.Busy, null);
                if (DateTime.UtcNow < proposalQuietUntil) return ProposalReply(ProposalResults.Later, null);
                askBusy = true;
                lock (gate)
                {
                    id = ++proposalId;
                    proposalResult = ProposalResults.Asking;
                    asking = true;
                }
            }
            Snapshot();
            Log.Write("설정", "제안 " + id + " — 세션 " + ses + " 에 묻습니다");
            System.Threading.Tasks.Task.Run(() =>
            {
                bool? answer;
                try { answer = prompt.Ask(ses, SettingsConfirm.Title, text, AskTimeoutSeconds); }
                catch (Exception) { answer = null; }
                if (answer == true)
                {
                    Post(() =>
                    {
                        string result = ProposalResults.Applied;
                        try
                        {
                            s.Save(paths.SettingsFile, paths.Root);
                            Controller.ApplySettings(s); // 확인 창의 글을 지은 바로 그 객체
                            lock (gate) settingsDefaulted = "";
                            Log.Write("설정", "제안 " + id + " 적용");
                        }
                        catch (Exception ex)
                        {
                            result = ProposalResults.Failed;
                            Log.Write("설정", "제안 " + id + " 을 저장하지 못함: " + ex.Message);
                        }
                        FinishProposal(id, result);
                    });
                }
                else
                {
                    Log.Write("설정", "제안 " + id + (answer == null ? " — 묻는 창을 띄우지 못함" : " — 거절 또는 시간 초과"));
                    FinishProposal(id, answer == null ? ProposalResults.Failed : ProposalResults.Rejected);
                }
            });
            return ProposalReply(ProposalResults.Asking, id);
        }

        static long RandomProposalBase()
        {
            var b = new byte[8];
            using (var rng = new System.Security.Cryptography.RNGCryptoServiceProvider()) rng.GetBytes(b);
            // 1 … 2^48 — 늘어나도 넘치지 않고 글로 15자 안팎(proposal_recent 의 400자 안에 여덟이 든다).
            return (long)(BitConverter.ToUInt64(b, 0) & 0xFFFFFFFFFFFFUL) + 1;
        }

        /// <summary>
        /// 끝 — 어떤 결과든 ProposalAfterMs 쉰다(사용자가 저장한 직후 남의 제안이 끼어들지 않게). 거절·시간 초과면 더 쉬고(ProposalQuietMs),
        /// 앞의 거절에서 ProposalRejectWindowMs 안에 되풀이되면 더 길게(ProposalQuietLongMs). 적용되거나 그 창이 지나면 되풀이 수를 0 으로.
        /// 최근 결과 여덟을 상태에 남긴다(트레이가 제 결과를 놓치지 않게).
        /// </summary>
        void FinishProposal(long id, string result)
        {
            lock (askGate)
            {
                askBusy = false;
                int quiet = ProposalAfterMs;
                if (result == ProposalResults.Rejected)
                {
                    var now = DateTime.UtcNow;
                    if ((now - proposalLastReject).TotalMilliseconds > ProposalRejectWindowMs) proposalRejects = 0;
                    proposalLastReject = now;
                    proposalRejects++;
                    quiet = Math.Max(quiet, proposalRejects >= 2 ? ProposalQuietLongMs : ProposalQuietMs);
                }
                else if (result == ProposalResults.Applied) proposalRejects = 0;
                var until = DateTime.UtcNow.AddMilliseconds(quiet);
                if (until > proposalQuietUntil) proposalQuietUntil = until;
                lock (gate)
                {
                    if (proposalId == id) proposalResult = result;
                    proposalRecent.Add(new KeyValuePair<long, string>(id, result));
                    if (proposalRecent.Count > 8) proposalRecent.RemoveAt(0);
                    asking = false;
                }
            }
            Post(Snapshot);
        }

        static Dictionary<string, string> ProposalReply(string result, long? id)
        {
            var r = new Dictionary<string, string> { { "ok", result == ProposalResults.Asking || result == ProposalResults.Unchanged ? "1" : "0" }, { "result", result } };
            if (id.HasValue) r["id"] = id.Value.ToString(CultureInfo.InvariantCulture);
            if (r["ok"] == "0") r["error"] = ProposalResults.Text(result);
            return r;
        }

        void Post(Action a)
        {
            var u = ui;
            if (u != null) u.Post(_ => { try { a(); } catch (Exception ex) { Log.Write("오류", ex.ToString()); } }, null);
        }

        /// <summary>
        /// 접두어 밖(또는 이름 모름) Wi-Fi 에 손으로 누른 '연결' — 콘솔의 사용자에게 서비스가 직접 묻는다. 물을 사람이 없으면(로그온 전·로그오프 뒤)
        /// 연결하지 않는다. 묻는 동안 메시지를 돌린다(모달처럼 — 1초 점검·파이프의 명령이 막히지 않게).
        /// </summary>
        bool AskUser(HotspotInfo hs, string prefix)
        {
            int ses = sessions.ConsoleUserSession();
            if (ses < 0)
            {
                Log.Write("Wi-Fi", "물을 사람이 없어(콘솔에 로그온한 사용자가 없음) 연결하지 않습니다");
                return false;
            }
            var text = StatusText.AskConnectText(hs, prefix);
            lock (askGate)
            {
                if (askBusy)
                {
                    Log.Write("Wi-Fi", "다른 물음(설정 확인)이 떠 있어 연결하지 않습니다");
                    return false;
                }
                askBusy = true;
            }
            lock (gate) asking = true;
            Snapshot();
            try
            {
                var t = Task.Run(() => prompt.Ask(ses, AppInfo.Name, text, AskTimeoutSeconds));
                while (!t.IsCompleted)
                {
                    Application.DoEvents();
                    t.Wait(30);
                }
                if (t.IsFaulted) return false;
                if (t.Result == null) Log.Write("Wi-Fi", "묻는 창을 띄우지 못해 연결하지 않습니다");
                return t.Result == true;
            }
            finally
            {
                lock (gate) asking = false;
                lock (askGate) askBusy = false;
            }
        }

        /// <summary>끝내기 — 끊고 걷기를 기다린다(시한 안에서), 파이프·UI 스레드를 내린다.</summary>
        public void Stop(int timeoutMs)
        {
            if (pipeRetry != null) { pipeRetry.Dispose(); pipeRetry = null; }
            if (pipe != null) { pipe.Dispose(); pipe = null; }
            var u = ui;
            var c = Controller;
            if (u != null && c != null)
            {
                var done = new ManualResetEventSlim(false);
                u.Post(async _ =>
                {
                    try { await c.ShutdownAsync(); }
                    catch (Exception ex) { Log.Write("오류", ex.ToString()); }
                    finally { done.Set(); }
                }, null);
                if (!done.Wait(timeoutMs)) Log.Write("서비스", "끊기·걷기가 시한 안에 끝나지 않았습니다");
                u.Post(_ => { if (appCtx != null) appCtx.ExitThread(); }, null);
            }
            if (uiThread != null) uiThread.Join(3000);
            if (Log != null) Log.Write("서비스", "멈춤");
            if (rootHold != null) { rootHold.Dispose(); rootHold = null; }
        }
    }

    /// <summary>설정 제안의 결과(파이프의 result·상태의 proposal_result).</summary>
    public static class ProposalResults
    {
        public const string Asking = "asking", Applied = "applied", Rejected = "rejected", Busy = "busy", Later = "later",
                            Invalid = "invalid", Session = "session", Unchanged = "unchanged", Failed = "failed", Program = "program";
        public static readonly string[] All = { Asking, Applied, Rejected, Busy, Later, Invalid, Session, Unchanged, Failed, Program };

        /// <summary>트레이에 보일 짧은 글.</summary>
        public static string Text(string r)
        {
            switch (r)
            {
                case Applied: return "적용했습니다.";
                case Rejected: return "확인 창에서 거절했습니다(또는 시간이 지났습니다).";
                case Busy: return "다른 확인 창이 떠 있습니다.";
                case Later: return "잠시 뒤 다시 하십시오.";
                case Invalid: return "설정 값이 틀렸습니다.";
                case Session: return "이 화면에서는 확인할 수 없습니다.";
                case Unchanged: return "바뀐 것이 없습니다.";
                case Program: return "설치된 트레이에서만 바꿀 수 있습니다.";
                case Asking: return "확인 창을 기다리는 중…";
                default: return "적용하지 못했습니다.";
            }
        }
    }

    /// <summary>
    /// 설정 확인 창의 글(검토 11-27 — 보이는 것 = 적용하는 것). 바뀌는 칸마다 '이름: 옛값 → 새값'. **자르지 않는다** — 긴 줄은 WrapChars 에서
    /// 줄을 바꿔 다 보이고, 값의 길이는 Settings.MaxValueLength(우회 목록 1000·나머지 200)로 묶는다. 글 값은 ‘ ’ 로 감싸고(값 안의 → ⟨ ⟩ ‘ ’ 는
    /// CheckValue 가 막는다 — 값이 줄의 꼴을 흉내 내지 못하게), 보이지 않는 글자·U+0020 밖의 빈칸·겹친 빈칸·앞뒤 빈칸은 ⟨U+XXXX⟩ 로 드러낸다.
    /// 거절할 것(값이 다른데 보이는 글이 같음·값이 상한을 넘음·전체가 MaxTextChars 를 넘음)은 reject 로. 바뀐 것이 없으면 null.
    /// 요청한 프로그램의 경로·PID 를 머리에 적는다. 순수 — 시험한다.
    /// </summary>
    public static class SettingsConfirm
    {
        public const int WrapChars = 60, MaxTextChars = 3000;
        public const string Title = AppInfo.Name + " — 설정 확인";

        public static string Show(string key, string v)
        {
            if (key == "setup" || key == "reconnect" || key == "update_check") return v == "1" ? "켬" : "끔";
            if (key == "update_interval") return v == "daily" ? "매일" : v == "monthly" ? "매월" : "매주";
            if (string.IsNullOrEmpty(v)) return "(없음)";
            var sb = new StringBuilder("‘");
            for (int i = 0; i < v.Length; i++)
            {
                var c = v[i];
                var cat = char.GetUnicodeCategory(c);
                bool odd = cat == UnicodeCategory.Control || cat == UnicodeCategory.Format || cat == UnicodeCategory.LineSeparator ||
                           cat == UnicodeCategory.ParagraphSeparator ||
                           (cat == UnicodeCategory.SpaceSeparator && c != ' ') ||
                           (c == ' ' && (i == 0 || i == v.Length - 1 || v[i - 1] == ' ' || (i + 1 < v.Length && v[i + 1] == ' ')));
                if (odd) sb.Append("⟨U+").Append(((int)c).ToString("X4", CultureInfo.InvariantCulture)).Append('⟩');
                else sb.Append(c);
            }
            return sb.Append('’').ToString();
        }

        /// <summary>긴 글을 width 마다 줄을 바꾼다(가능하면 쉼표·빈칸 뒤에서). 잇는 줄은 들여 쓴다.</summary>
        public static string Wrap(string s, int width)
        {
            if (s.Length <= width) return s;
            var sb = new StringBuilder();
            int pos = 0;
            while (s.Length - pos > width)
            {
                int cut = pos + width;
                for (int j = cut; j > pos + width / 2; j--)
                    if (s[j - 1] == ',' || s[j - 1] == ' ') { cut = j; break; }
                if (char.IsHighSurrogate(s[cut - 1])) cut--;
                sb.Append(s, pos, cut - pos).Append("\r\n    ");
                pos = cut;
            }
            sb.Append(s, pos, s.Length - pos);
            return sb.ToString();
        }

        public static string Text(Settings old, Settings now, string requester, int pid, out string reject)
        {
            reject = null;
            var a = old.Pairs();
            var b = now.Pairs();
            var lines = new List<string>();
            for (int i = 0; i < b.Count; i++)
            {
                if (a[i].Value == b[i].Value) continue;
                string ov = Show(a[i].Key, a[i].Value), nv = Show(b[i].Key, b[i].Value);
                if (ov == nv) { reject = "값이 다른데 보이는 글이 같음: " + b[i].Key; return null; }
                if (b[i].Value.Length > Settings.MaxValueLength(b[i].Key)) { reject = "너무 긴 값: " + b[i].Key; return null; }
                lines.Add(Wrap(Settings.Label(b[i].Key) + ": " + ov + " → " + nv, WrapChars));
            }
            if (lines.Count == 0) return null;
            var who = string.IsNullOrEmpty(requester) ? "알 수 없음" : Show("", requester);
            var sb = new StringBuilder("PC 도우미 설정을 바꾸라는 요청입니다. 직접 바꾸지 않았으면 '아니요'.\r\n");
            sb.Append(Wrap("요청: " + who + " (PID " + pid.ToString(CultureInfo.InvariantCulture) + ")", WrapChars)).Append("\r\n\r\n");
            foreach (var l in lines) sb.Append(l).Append("\r\n");
            sb.Append("\r\n적용할까요?");
            if (sb.Length > MaxTextChars) { reject = "한 번에 바꾸는 글이 너무 깁니다(" + sb.Length + "자)"; return null; }
            return sb.ToString();
        }
    }

    /// <summary>SCM 이 부르는 껍데기 — 몸통은 ServiceCore.</summary>
    public sealed class WebTetherService : ServiceBase
    {
        public const string Name = "WebTether";
        readonly ServiceCore core;

        public WebTetherService(ServiceCore core)
        {
            this.core = core;
            ServiceName = Name;
            CanStop = true;
            CanShutdown = true;
            CanPauseAndContinue = false;
            CanHandleSessionChangeEvent = true;
            AutoLog = true;
        }

        protected override void OnStart(string[] args)
        {
            string err;
            if (!core.Start(out err))
            {
                ExitCode = 1064; // ERROR_EXCEPTION_IN_SERVICE
                throw new InvalidOperationException("시작하지 못했습니다: " + err);
            }
        }

        protected override void OnStop()
        {
            RequestAdditionalTime(20000);
            core.Stop(15000);
        }

        protected override void OnShutdown()
        {
            core.Stop(10000);
        }

        protected override void OnSessionChange(SessionChangeDescription d)
        {
            if (core.Log != null) core.Log.Write("세션", d.Reason + " · 세션 " + d.SessionId);
        }

        // OnCustomCommand 는 덮지 않는다 — 사용자 정의 제어는 아무것도 하지 않는다(서비스 DACL 도 일반 사용자에게 주지 않는다).
    }
}
