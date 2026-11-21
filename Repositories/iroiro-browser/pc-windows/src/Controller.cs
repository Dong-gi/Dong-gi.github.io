// 연결의 상태 기계 — 화면과 따로. UI 스레드에서 만들고 부른다(Tick 은 1초마다).
// 경로 정리(route.exe)·실행 파일 풀기·Ctrl+C 기다리기처럼 막는 일은 일꾼 스레드에서 한다 — 화면이 멈추지 않게.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net.NetworkInformation;
using System.Threading;
using System.Threading.Tasks;

namespace WebTether
{
    public enum TetherState { Off, Connecting, Connected, Waiting, Error }

    /// <summary>'연결/끊기' 단추(트레이·상태 창)가 지금 할 일. Cleaning 은 앞 연결을 걷는 중이라 누를 수 없다.</summary>
    public enum ToggleAction { Connect, Disconnect, Cleaning }

    /// <summary>화면이 보이는 글과 아이콘을 정하는 순수 함수(시험한다).</summary>
    public static class StatusText
    {
        public static string ToggleLabel(ToggleAction a)
        {
            switch (a)
            {
                case ToggleAction.Disconnect: return "끊기";
                case ToggleAction.Cleaning: return "정리 중";
                default: return "연결";
            }
        }

        public static string Title(TetherState s)
        {
            switch (s)
            {
                case TetherState.Connecting: return "연결 중";
                case TetherState.Connected: return "연결됨";
                case TetherState.Waiting: return "다시 연결 대기";
                case TetherState.Error: return "오류";
                default: return "꺼짐";
            }
        }

        /// <summary>트레이 툴팁 — .NET Framework 는 63자를 넘으면 던진다.</summary>
        public static string Tooltip(TetherState s, string detail)
        {
            var t = "Web 테더 — " + Title(s);
            // 꺼짐의 글은 '직접 끊음'(끊어 두어 저절로 연결하지 않았다) 하나뿐이다.
            if (!string.IsNullOrEmpty(detail) && s != TetherState.Connected) t += " · " + detail;
            return t.Length > 63 ? t.Substring(0, 62) + "…" : t;
        }
    }

    public interface IChildFactory
    {
        IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error);
    }

    public sealed class RealChildFactory : IChildFactory
    {
        public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
        {
            return ChildProcess.Start(bin.ExePath, args, Environment.GetFolderPath(Environment.SpecialFolder.System), output, out error);
        }
    }

    public sealed class DryRunChildFactory : IChildFactory
    {
        public readonly List<string> CommandLines = new List<string>();
        public DryRunChild Last;
        public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
        {
            error = null;
            var line = (bin != null ? CommandLine.Quote(bin.ExePath) : "tun2proxy-bin.exe") + " " + CommandLine.Join(args);
            CommandLines.Add(line);
            if (output != null) output("dry-run", "띄우지 않음: " + line);
            Last = new DryRunChild();
            return Last;
        }
    }

    public sealed class Controller
    {
        public const int StopGraceMs = 5000;

        readonly AppPaths paths;
        readonly Log log;
        readonly ISystemOps ops;
        readonly IChildFactory children;
        readonly BinarySet binaries;
        readonly bool dryRun;
        readonly bool harden;
        readonly SynchronizationContext ui;

        public Settings Settings { get; private set; }
        public TetherState State { get; private set; }
        public string Detail { get; private set; }
        public bool DryRun { get { return dryRun; } }

        public event Action Changed;
        /// <summary>(제목, 글, 경고인가). 누르면 열 주소가 있으면 NoticeUrl 에 둔다(없으면 null).</summary>
        public event Action<string, string, bool> Notice;
        public string NoticeUrl { get; private set; }

        public UpdateState Updates { get; private set; }
        bool updateRunning;
        /// <summary>시험이 바꾼다 — (주소, 전체 마감 UTC)를 받아 글을 돌려준다(막는 호출).</summary>
        public Func<string, DateTime, string> UpdateFetcher = UpdateFetch.Get;
        public string[] BuildSkippedVersions = Embedded.SkippedVersions.Length == 0 ? new string[0] : Embedded.SkippedVersions.Split(',');

        IChild child;
        HeldBinaries held;
        bool userWantsOn;
        bool busy;
        bool stopping;
        Task stopTask;
        IChild stopTarget;
        /// <summary>도는 중이거나 마지막으로 돈 경로 정리 — 끝나기 전에는 새 연결을 띄우지 않는다.</summary>
        Task pendingCleanup = Task.FromResult(0);
        /// <summary>끊고 걷은 뒤의 마무리(상태 정하기)까지 도는 일의 수 — 그동안 단추는 '정리 중'.</summary>
        int settling;
        /// <summary>
        /// 저절로 연결한 시도의 너그러운 기다림이 끝나는 때(아니면 MinValue). 그때까지는 핫스팟·폰 프록시가 없어도 횟수를 쓰지 않고
        /// '다시 연결' 설정과 상관없이 다시 본다 — 로그온 직후에는 Wi-Fi 가 늦게 붙는다. tun2proxy 를 띄우면 끝난다.
        /// </summary>
        DateTime startupUntil = DateTime.MinValue;
        /// <summary>저절로 연결할 때 너그럽게 기다리는 시간(초)과 그동안 다시 보는 간격(초, '다시 연결 간격' 이 더 짧으면 그것).</summary>
        public int StartupWaitSeconds = 180;
        public int StartupRetrySeconds = 5;
        int retries;
        DateTime nextRetry;
        DateTime connectedAt;
        /// <summary>시험이 바꾼다 — 핫스팟 찾기.</summary>
        public Func<HotspotInfo> HotspotFinder = NetState.FindHotspot;
        public Func<string, int, ProxyKind, int, ProbeResult> Prober = NetState.Probe;
        public Func<List<int>> Tun2proxyLister = NetState.Tun2proxyPids;
        /// <summary>TUN 어댑터가 보이는가. dry-run 이면 늘 참(띄우지 않으니 어댑터도 없다).</summary>
        public Func<string, bool> TunPresent = name => NetState.FindAdapter(name) != null;
        public Func<List<RouteRow>> RouteReader = Routes.ReadTable;
        public int WifiWaitSeconds = 15;
        /// <summary>tun2proxy 를 띄운 뒤 TUN 어댑터를 기다리는 시간 — 넘으면 끄고 걷고 오류.</summary>
        public int ConnectTimeoutSeconds = 30;

        public Controller(AppPaths paths, Log log, ISystemOps ops, IChildFactory children, BinarySet binaries,
                          bool dryRun, bool harden, Settings settings)
        {
            this.paths = paths;
            this.log = log;
            this.ops = ops;
            this.children = children;
            this.binaries = binaries;
            this.dryRun = dryRun;
            this.harden = harden;
            if (dryRun) TunPresent = name => true;
            ui = SynchronizationContext.Current ?? new SynchronizationContext();
            Settings = settings;
            Updates = UpdateState.Load(paths.UpdateFile);
            State = TetherState.Off;
            Detail = "";
        }

        public bool ChildRunning { get { return child != null && !child.HasExited; } }
        public int ChildPid { get { return ChildRunning ? child.Pid : 0; } }
        public int Retries { get { return retries; } }

        /// <summary>
        /// 단추가 할 일. 자식이 돌거나 시도·대기 중이면 끊기. 자식은 끝났는데 걷기(경로 정리)나 그 뒤 마무리가 아직이면 '정리 중' —
        /// 그 사이 '연결' 로 보이면 걷기와 새 연결이 겹치고 끊은 쪽의 마무리가 새 시도를 덮을 수 있었다. 그 밖은 연결.
        /// 자식이 끝났는데 그 알림(OnChildExited)을 아직 받지 못했으면(child 가 남았다) 끊기 — '연결' 로 보이면 새 시도가 그 자식의
        /// 걷기보다 먼저 tun2proxy 를 띄울 수 있었다. 끊기는 그 자식을 걷는 길로 간다.
        /// </summary>
        public ToggleAction Toggle
        {
            get
            {
                if (child != null || State == TetherState.Waiting || State == TetherState.Connecting) return ToggleAction.Disconnect;
                if (settling > 0 || stopping || !pendingCleanup.IsCompleted) return ToggleAction.Cleaning;
                return ToggleAction.Connect;
            }
        }

        /// <summary>사용자가 끊어 둔 표시가 있는가 — 있으면 실행할 때 저절로 연결하지 않는다.</summary>
        public bool UserDisconnected { get { return File.Exists(paths.UserOffFile); } }

        void Set(TetherState s, string detail)
        {
            State = s;
            Detail = detail ?? "";
            log.Write("상태", StatusText.Title(s) + (string.IsNullOrEmpty(detail) ? "" : " — " + detail));
            RaiseChanged();
        }

        void RaiseChanged()
        {
            var h = Changed;
            if (h != null) h();
        }

        /// <summary>끊어 둔 표시를 남기거나 지운다. 쓰지 못하면 기록만 — 연결·끊기 자체는 그대로 한다.</summary>
        void MarkUserOff(bool off)
        {
            try
            {
                if (off) SafeFs.WriteAtomic(paths.UserOffFile, "disconnected-by-user " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "\n", paths.Root);
                else SafeFs.Delete(paths.UserOffFile, paths.Root);
            }
            catch (Exception ex)
            {
                log.Write("끊어 둠", (off ? "표시를 남기지 못함: " : "표시를 지우지 못함: ") + ex.Message);
            }
        }

        void Tell(string title, string text, bool warn)
        {
            Tell(title, text, warn, null);
        }

        void Tell(string title, string text, bool warn, string url)
        {
            NoticeUrl = url;
            var h = Notice;
            if (h != null) h(title, text, warn);
            NoticeUrl = null;
        }

#if TEST_BUILD
        /// <summary>시험 판만 — 화면 그림을 그릴 때 상태를 정한다.</summary>
        public void ForceStateForTest(TetherState s, string detail) { Set(s, detail); }
#endif

        public void ApplySettings(Settings s)
        {
            Settings = s;
            RaiseChanged();
        }

        /// <summary>설정을 상태 폴더에 쓴다(재분석 지점이 끼어 있으면 던진다).</summary>
        public void SaveSettings(Settings s)
        {
            s.Save(paths.SettingsFile, paths.Root);
        }

        // ---- 연결 ----

        public async void Connect()
        {
            await ConnectAsync();
        }

        /// <summary>
        /// 사용자의 '연결'. 끊어 둔 표시를 지운다(다음 실행부터 다시 저절로 연결한다).
        /// 기다릴 수 있는 꼴(시험). 시도가 이미 도는 중이면 그 시도가 이어 간다(그사이 끊기를 눌렀어도).
        /// </summary>
        public Task ConnectAsync()
        {
            MarkUserOff(false);
            startupUntil = DateTime.MinValue;
            // 끝났지만 알림을 아직 받지 않은 자식도 — 그 걷기(OnChildExited)가 먼저다. 그 뒤는 다시 연결 규칙이 잇는다.
            if (child != null) return Task.FromResult(0);
            userWantsOn = true;
            if (busy) return Task.FromResult(0);
            retries = 0;
            return Attempt();
        }

        public async void AutoConnect()
        {
            await AutoConnectAsync();
        }

        /// <summary>
        /// 실행할 때 — 사용자가 끊어 둔 표시가 있으면 꺼진 채로 둔다. 없으면 연결한다: 시작 직후 StartupWaitSeconds 동안은 핫스팟·폰
        /// 프록시가 없어도 횟수를 쓰지 않고 기다린다(StartupRetrySeconds 마다). 다른 tun2proxy·틀린 설정은 지금처럼 오류로 멈춘다.
        /// </summary>
        public Task AutoConnectAsync()
        {
            if (UserDisconnected)
            {
                log.Write("시작", "지난번에 사용자가 끊어 두어 저절로 연결하지 않습니다");
                Set(TetherState.Off, "직접 끊음");
                return Task.FromResult(0);
            }
            if (ChildRunning || busy || userWantsOn) return Task.FromResult(0);
            log.Write("시작", "저절로 연결합니다");
            userWantsOn = true;
            retries = 0;
            startupUntil = DateTime.Now.AddSeconds(StartupWaitSeconds);
            return Attempt();
        }

        bool InStartupWait(DateTime now) { return startupUntil != DateTime.MinValue && now < startupUntil; }

        /// <summary>
        /// 시도 도중(기다리는 사이) 사용자가 끊었거나 끝내는 중인가 — 그러면 더 나아가지 않는다.
        /// 끊기는 자식이 없으면 곧바로 '꺼짐' 으로 두므로, 여기서 멈추지 않으면 그 뒤에 tun2proxy 를 띄웠다.
        /// </summary>
        bool Cancelled(HeldBinaries hb)
        {
            if (userWantsOn) return false;
            if (hb != null) hb.Dispose();
            log.Write("시작", "끊어 달라고 해서 연결을 그만둡니다");
            return true;
        }

        async Task Attempt()
        {
            if (busy) return;
            busy = true;
            try
            {
                // 앞 연결을 끝내는 중이거나 그 경로 정리가 아직 돌면 끝나기를 기다린다 — 걷는 사이 새 tun2proxy 가 경로를 넣으면 섞인다.
                // 끝내기(Ctrl+C·강제 종료)가 끝나야 그 걷기가 pendingCleanup 에 들어가므로 끝내기부터 기다린다.
                if (stopTask != null || !pendingCleanup.IsCompleted)
                {
                    Set(TetherState.Connecting, "앞 연결의 경로 정리를 기다림");
                    while (stopTask != null) await stopTask;
                    await pendingCleanup;
                    if (Cancelled(null)) return;
                }
                var s = Settings;
                Set(TetherState.Connecting, "확인 중");
                var errs = s.Validate();
                if (errs.Count > 0) { Fail("설정을 확인하십시오 — " + errs[0].Value, false); return; }

                if (Tun2proxyLister().Count > 0)
                {
                    Fail("다른 tun2proxy 가 실행 중입니다. 먼저 끄십시오.", false);
                    return;
                }

                var hs = HotspotFinder();
                if (hs == null && s.WifiProfile.Length > 0)
                {
                    Set(TetherState.Connecting, "Wi-Fi 연결 중");
                    string outp = null;
                    var ok = await Task.Run(() => ops.WlanConnect(s.WifiProfile, out outp));
                    if (Cancelled(null)) return;
                    if (!ok) log.Write("Wi-Fi", "netsh 실패: " + outp);
                    for (int i = 0; i < WifiWaitSeconds && hs == null; i++)
                    {
                        await Task.Delay(1000);
                        if (Cancelled(null)) return;
                        hs = HotspotFinder();
                    }
                }
                if (hs == null) { Fail("폰 핫스팟에 연결되어 있지 않습니다", true, true); return; }

                Set(TetherState.Connecting, "폰 프록시 확인");
                var probe = await Task.Run(() => Prober(s.ProxyHost, s.ProxyPort, s.Kind, 3000));
                if (Cancelled(null)) return;
                if (!probe.Ok) { Fail("폰 프록시가 응답하지 않습니다(" + probe.Detail + ")", true, true); return; }

                string err = null;
                HeldBinaries hb = null;
                if (!dryRun || binaries != null)
                {
                    hb = await Task.Run(() =>
                    {
                        try { SecureDir.CheckNoReparse(paths.Root, paths.BinRoot); }
                        catch (IOException ex) { err = ex.Message; return null; }
                        return Binaries.Ensure(binaries, paths.BinRoot, harden, m => log.Write("확인", m), out err);
                    });
                    if (Cancelled(hb)) return;
                    if (hb == null) { Fail(err, false); return; }
                }

                // 걷을 수 있게 지금의 기본 경로와, 이미 있던 우회 경로(사용자의 것 — 지우지 않는다)를 적어 둔다.
                var st = new ConnectionState { TunName = s.TunName };
                st.Bypass = Settings.SplitBypass(s.Bypass);
                try
                {
                    var table = RouteReader();
                    st.DefaultsBefore = Routes.DefaultsOutside(table, NetState.IfIndexOf(s.TunName));
                    st.KeptBypass = Routes.ExistingBypass(table, st.DefaultsBefore, st.Bypass);
                }
                catch (Exception ex) { log.Write("경로", "경로 표를 읽지 못함: " + ex.Message); }
                try { st.Save(paths.StateFile, paths.Root); }
                catch (Exception ex) { log.Write("상태", "상태 파일을 쓰지 못함: " + ex.Message); }

                var args = CommandLine.Tun2proxyArgs(s);
                log.Write("시작", "tun2proxy " + CommandLine.Join(args));
                var c = children.Start(hb, args, (src, line) => log.Write(src, line), out err);
                if (c == null)
                {
                    if (hb != null) hb.Dispose();
                    Fail("tun2proxy 를 띄우지 못했습니다: " + err, false);
                    return;
                }
                held = hb;
                child = c;
                startupUntil = DateTime.MinValue; // 띄운 뒤의 실패(자식이 끝남 등)는 늘 쓰는 규칙으로 — 시작 직후에 죽기를 되풀이해도 상한이 있게
                var mine = c;
                c.Exited += code => ui.Post(_ => OnChildExited(mine, code), null);
                Set(TetherState.Connecting, "tun2proxy 시작됨");
            }
            catch (Exception ex)
            {
                Fail("뜻밖의 오류: " + ex.Message, false);
            }
            finally
            {
                busy = false;
            }
        }

        void Fail(string why, bool retryable)
        {
            Fail(why, retryable, false);
        }

        /// <summary>beforeStart: tun2proxy 를 띄우기 전의 실패(핫스팟·폰 프록시 없음) — 저절로 연결한 직후의 너그러운 기다림이 받는다.</summary>
        void Fail(string why, bool retryable, bool beforeStart)
        {
            var now = DateTime.Now;
            if (retryable && beforeStart && userWantsOn && InStartupWait(now))
            {
                nextRetry = now.AddSeconds(Math.Min(StartupRetrySeconds, Settings.ReconnectInterval));
                Set(TetherState.Waiting, why + " — 기다리는 중");
                return;
            }
            bool wasStartup = startupUntil != DateTime.MinValue;
            startupUntil = DateTime.MinValue;
            if (retryable && wasStartup && userWantsOn && !Settings.Reconnect)
                Tell("저절로 연결하지 못했습니다", why, true);
            if (retryable && userWantsOn && Settings.Reconnect && retries < Settings.ReconnectMax)
            {
                retries++;
                nextRetry = DateTime.Now.AddSeconds(Settings.ReconnectInterval);
                Set(TetherState.Waiting, why + " — " + retries + "/" + Settings.ReconnectMax);
                return;
            }
            if (retryable && userWantsOn && Settings.Reconnect)
                Tell("다시 연결을 멈췄습니다", why, true);
            userWantsOn = false;
            Set(TetherState.Error, why);
        }

        void OnChildExited(IChild c, int code)
        {
            if (c != child || stopping) return;
            log.Write("tun2proxy", "끝남(코드 " + code + ")");
            child = null;
            CleanupAfterChild(c);
            // 횟수는 1분 넘게 붙어 있었을 때만 처음부터(Tick 과 같은 규칙) — 뜨자마자 '연결됨' 이 된 뒤 몇 초 만에 죽기를
            // 되풀이하면 횟수가 늘 0 으로 돌아가 상한 없이 다시 띄웠다.
            bool longConnected = State == TetherState.Connected && (DateTime.Now - connectedAt).TotalSeconds > 60;
            Tell("연결이 끊겼습니다", "tun2proxy 가 끝났습니다(코드 " + code + ").", true);
            if (longConnected) retries = 0;
            Fail("tun2proxy 가 끝났습니다(코드 " + code + ")", true);
        }

        // ---- 끊기 ----

        public async void Disconnect()
        {
            await DisconnectAsync();
        }

        /// <summary>
        /// 사용자의 '끊기'(트레이·상태 창) — 그 사실을 상태 폴더에 남겨 다음 실행 때 저절로 연결하지 않는다. 오류·핫스팟 사라짐·
        /// 다시 연결 포기·끝내기·로그오프·도우미가 죽음은 끊음이 아니다(이 길을 지나지 않는다).
        /// </summary>
        public async Task DisconnectAsync()
        {
            MarkUserOff(true);
            userWantsOn = false;
            retries = 0;
            startupUntil = DateTime.MinValue;
            settling++;
            try
            {
                if (child != null) await StopChildAsync("사용자가 끊음");
                else if (stopTask != null) await stopTask;
            }
            finally { settling--; }
            if (busy && userWantsOn) return; // 끊는 사이 새로 '연결' 했다 — 그 시도가 상태를 정한다(덮지 않는다)
            Set(TetherState.Off, "");
        }

        /// <summary>
        /// 자식을 끝내고 걷는다. 이미 같은 자식을 끝내는 중이면 그 일을 돌려준다 — 핫스팟이 사라져 끊는 사이 사용자가 '끊기' 를
        /// 누르면 Ctrl+C 를 두 번 보내고 걷기도 두 번 돌았다.
        /// </summary>
        Task StopChildAsync(string why)
        {
            var c = child;
            if (c == null) return stopTask ?? Task.FromResult(0);
            if (stopTask != null && stopTarget == c) return stopTask;
            stopTarget = c;
            var t = StopChildCore(c, why);
            if (!t.IsCompleted) stopTask = t;
            return t;
        }

        async Task StopChildCore(IChild c, string why)
        {
            stopping = true;
            try
            {
                Set(State, "끊는 중");
                log.Write("끊기", why + " — Ctrl+C 를 보냅니다");
                string detail = null;
                bool clean = await Task.Run(() => c.StopGracefully(StopGraceMs, out detail));
                if (!string.IsNullOrEmpty(detail)) log.Write("끊기", detail);
                if (!clean)
                {
                    log.Write("끊기", (StopGraceMs / 1000) + "초 안에 끝나지 않아 강제로 끝냅니다");
                    await Task.Run(() => c.Kill());
                }
                if (child == c) child = null;
                await CleanupAfterChild(c);
            }
            finally
            {
                stopping = false;
                stopTask = null;
                stopTarget = null;
            }
        }

        Task CleanupAfterChild(IChild c)
        {
            if (held != null) { held.Dispose(); held = null; }
            if (c != null) c.Dispose();
            return StartCleanup(false);
        }

        /// <summary>
        /// 남은 경로를 걷는다 — 일꾼 스레드에서(route.exe 를 부른다). 앞의 걷기가 돌면 그 뒤에. 끝나기 전에는 Tick 이 쉬고
        /// 새 시도가 기다린다.
        /// </summary>
        Task StartCleanup(bool startup)
        {
            var fallbackTun = Settings.TunName;
            var prev = pendingCleanup;
            var t = prev.ContinueWith(_ => CleanupCore(fallbackTun, startup), CancellationToken.None,
                                      TaskContinuationOptions.None, TaskScheduler.Default);
            pendingCleanup = t;
            // 끝나면 화면에 알린다 — 단추가 '정리 중' 에서 '연결' 로 돌아오게(트레이 메뉴는 Changed 때만 다시 그린다).
            t.ContinueWith(_ => ui.Post(__ => RaiseChanged(), null), CancellationToken.None,
                           TaskContinuationOptions.None, TaskScheduler.Default);
            return t;
        }

        void CleanupCore(string fallbackTun, bool startup)
        {
            try
            {
                var st = ConnectionState.Load(paths.StateFile);
                if (startup && st != null) log.Write("정리", "지난번 연결의 흔적을 확인합니다");
                var notes = new List<string>();
                var tunName = st != null && st.TunName.Length > 0 ? st.TunName : fallbackTun;
                var live = new HashSet<uint>();
                foreach (var ni in SafeInterfaces())
                {
                    try { live.Add((uint)ni.GetIPProperties().GetIPv4Properties().Index); } catch (Exception) { }
                }
                bool other = Tun2proxyLister().Count > 0;
                var plan = Routes.CleanupPlan(RouteReader(), st, NetState.IfIndexOf(tunName), live, other, notes);
                foreach (var n in notes) log.Write("정리", n);
                foreach (var a in plan)
                {
                    string outp;
                    bool ok = ops.Route(a, out outp);
                    log.Write("정리", (ok ? "" : "실패: ") + a + (string.IsNullOrEmpty(outp) ? "" : " → " + outp));
                }
                // 걷지 않았으면(tun2proxy 가 아직 떠 있다 — 강제 종료가 늦었거나 다른 것) 적어 둔 것을 남겨 다음에 걷는다.
                if (st != null && !other) SafeFs.Delete(paths.StateFile, paths.Root);
            }
            catch (Exception ex)
            {
                log.Write("정리", "경로를 정리하지 못함: " + ex.Message);
            }
        }

        static NetworkInterface[] SafeInterfaces()
        {
            try { return NetworkInterface.GetAllNetworkInterfaces(); }
            catch (NetworkInformationException) { return new NetworkInterface[0]; }
        }

        /// <summary>
        /// 시작할 때 — 지난번 상태 파일이 남았으면(도우미가 연결 중에 죽었다 — tun2proxy 는 작업 개체와 함께 끝났다) 걷는다.
        /// 일꾼 스레드에서 돌고, 끝나기 전에는 연결하지 않는다.
        /// </summary>
        public Task StartupCheck()
        {
            return StartCleanup(true);
        }

        /// <summary>걷기가 끝났는가(시험·화면).</summary>
        public bool CleanupPending { get { return !pendingCleanup.IsCompleted; } }

        // ---- 1초마다 ----

        public void Tick()
        {
            var now = DateTime.Now;
            var s = Settings;
            if (stopping || busy || settling > 0 || !pendingCleanup.IsCompleted) return;

            if (ChildRunning)
            {
                if (State == TetherState.Connecting)
                {
                    var age = (now - child.StartedAt).TotalSeconds;
                    if (age >= 2 && TunPresent(s.TunName))
                    {
                        connectedAt = now;
                        Set(TetherState.Connected, "");
                    }
                    else if (age >= ConnectTimeoutSeconds) ConnectTimedOut(s.TunName, ConnectTimeoutSeconds);
                }
                else if (State == TetherState.Connected)
                {
                    if ((now - connectedAt).TotalSeconds > 60) retries = 0;
                    if (HotspotFinder() == null) HotspotLost();
                    else if (s.UpdateCheck && !updateRunning && Updates.Due(now, s.UpdateEvery)) CheckUpdates();
                }
                return;
            }

            if (State == TetherState.Waiting && userWantsOn && now >= nextRetry)
            {
                // 저절로 연결한 직후의 기다림이 끝났으면 한 번 더 해 본다 — 그 실패부터 늘 쓰는 규칙(횟수·'다시 연결' 설정)을 탄다.
                bool startupOver = startupUntil != DateTime.MinValue && !InStartupWait(now);
                if (HotspotFinder() != null || s.WifiProfile.Length > 0 || startupOver)
                {
                    var t = Attempt();
                }
                else
                {
                    // 핫스팟이 돌아오기를 기다린다 — 횟수를 쓰지 않고 다시 미룬다(시작 직후에는 더 자주 본다).
                    nextRetry = now.AddSeconds(InStartupWait(now) ? Math.Min(StartupRetrySeconds, s.ReconnectInterval) : s.ReconnectInterval);
                }
            }
        }

        /// <summary>tun2proxy 는 떴는데 TUN 어댑터가 끝내 나타나지 않는다 — 끄고 걷고 오류(다시 하지 않는다).</summary>
        async void ConnectTimedOut(string tun, int seconds)
        {
            var why = "TUN 어댑터(" + tun + ")가 " + seconds + "초 안에 나타나지 않았습니다";
            log.Write("시작", why + " — 끕니다");
            settling++;
            try { await StopChildAsync("TUN 어댑터가 나타나지 않음"); }
            finally { settling--; }
            if (!userWantsOn || busy) return; // 그사이 사용자가 끊었거나 새 시도가 돈다 — 덮지 않는다
            Fail(why, false);
        }

        // ---- 업데이트 알림(알리기만) ----

        /// <summary>새 판이 있으면 그 판, 없으면 null.</summary>
        public string NewVersion { get { return Updates.NewVersion(Embedded.Tun2proxyVersion, BuildSkippedVersions); } }

        public async void CheckUpdates()
        {
            await CheckUpdatesAsync();
        }

        public async Task CheckUpdatesAsync()
        {
            if (updateRunning) return;
            updateRunning = true;
            try
            {
                var st = Updates;
                var fetch = UpdateFetcher;
                bool ok = await Task.Run(() => UpdateFetch.Check(st, Embedded.Tun2proxyVersion, fetch));
                st.Save(paths.UpdateFile, paths.Root);
                if (!ok)
                {
                    log.Write("업데이트", "확인하지 못함(다음 간격에 다시): " + st.LastError);
                    return;
                }
                log.Write("업데이트", "tun2proxy 최신 " + st.Latest + " · 이 판 " + Embedded.Tun2proxyVersion + " · 보안 권고 " + st.Advisories.Count + "건");
                var key = st.AdvisoryKey();
                if (st.Advisories.Count > 0 && key != st.NotifiedAdvisories)
                {
                    st.NotifiedAdvisories = key;
                    var a = st.Advisories[0];
                    Tell("보안 권고 — tun2proxy " + Embedded.Tun2proxyVersion,
                         a.Id + (a.Severity.Length > 0 ? " (" + a.Severity + ")" : "") + " " + a.Summary +
                         (st.Advisories.Count > 1 ? " 외 " + (st.Advisories.Count - 1) + "건" : ""), true, a.Url.Length > 0 ? a.Url : null);
                }
                else if (st.Advisories.Count == 0) st.NotifiedAdvisories = "";
                var nv = NewVersion;
                if (nv != null && nv != st.NotifiedVersion)
                {
                    st.NotifiedVersion = nv;
                    Tell("새 판 tun2proxy " + nv, "눌러 변경 내역을 봅니다. 바꾸려면 build.py --update 로 다시 짓습니다.", false,
                         st.LatestUrl.Length > 0 ? st.LatestUrl : null);
                }
                st.Save(paths.UpdateFile, paths.Root);
                var h = Changed;
                if (h != null) h();
            }
            finally
            {
                updateRunning = false;
            }
        }

        public void SkipVersion(string v)
        {
            if (Versions.Parse(v) == null || Updates.Skipped.Contains(v)) return;
            Updates.Skipped.Add(v);
            Updates.Save(paths.UpdateFile, paths.Root);
            log.Write("업데이트", v + " 는 알리지 않습니다");
            var h = Changed;
            if (h != null) h();
        }

        async void HotspotLost()
        {
            Tell("연결이 끊겼습니다", "폰 핫스팟이 사라졌습니다.", true);
            settling++;
            try { await StopChildAsync("핫스팟이 사라짐"); }
            finally { settling--; }
            // 그사이 사용자가 끊었다 — '꺼짐' 그대로(오류·대기로 덮지 않는다). 새 시도가 돌면 그것이 상태를 정한다.
            if (!userWantsOn || busy) return;
            retries = 0;
            Fail("폰 핫스팟이 사라졌습니다", true);
        }

        /// <summary>
        /// 끝내기 — 자식을 끝내고 걷기를 기다린다. 사용자가 끊은 것으로 치지 않는다(표시를 남기지 않는다) —
        /// 다음에 켜면 다시 저절로 연결한다.
        /// </summary>
        public async Task ShutdownAsync()
        {
            userWantsOn = false;
            startupUntil = DateTime.MinValue;
            if (child != null || stopTask != null) await StopChildAsync("끝내기");
            await pendingCleanup;
        }
    }
}
