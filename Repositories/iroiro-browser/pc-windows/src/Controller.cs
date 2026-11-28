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
            if (!string.IsNullOrEmpty(detail) && s != TetherState.Connected) t += " · " + detail;
            return t.Length > 63 ? t.Substring(0, 62) + "…" : t;
        }

        /// <summary>화면·로그에 보일 SSID — 남이 정한 이름이라 제어·방향 제어 문자를 빼고 자른다.</summary>
        public static string Ssid(string ssid)
        {
            return "'" + SafeText.Clean(ssid, 40) + "'";
        }

        /// <summary>
        /// 저절로 연결해도 되는 핫스팟인가 — 되면 null, 아니면 상태에 보일 까닭. 이름이 접두어로 시작하거나 이 실행에서 사용자가
        /// 연결하겠다고 한 이름(approved)이어야 한다. 이름을 모르면(유선·권한·API 실패) 연결하지 않는다.
        /// </summary>
        public static string Refusal(HotspotInfo hs, string prefix, string approved)
        {
            if (hs == null) return "폰 핫스팟에 연결되어 있지 않습니다";
            if (hs.RouteMismatch != null) return hs.RouteMismatch;
            if (hs.Ssid == null) return "Wi-Fi 이름을 알 수 없음(" + (hs.SsidError ?? "까닭 모름") + ")";
            if (hs.SsidStartsWith(prefix) || (approved != null && hs.Ssid == approved)) return null;
            return "Wi-Fi 이름이 " + prefix + " 로 시작하지 않음(" + Ssid(hs.Ssid) + ")";
        }

        /// <summary>접두어 밖(또는 이름 모름) Wi-Fi 에 손으로 누른 '연결' 의 물음 글.</summary>
        public static string AskConnectText(HotspotInfo hs, string prefix)
        {
            return hs.Ssid != null
                ? "Wi-Fi " + Ssid(hs.Ssid) + " 에 연결할까요?" + Environment.NewLine +
                  "이름이 " + prefix + " 로 시작하지 않습니다 — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다."
                : "이름을 확인하지 못한 Wi-Fi(" + hs.AdapterName + ") 에 연결할까요?" + Environment.NewLine +
                  "까닭: " + hs.SsidError + " — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다.";
        }

        /// <summary>상태 창의 '핫스팟' 줄.</summary>
        public static string HotspotLine(HotspotInfo hs, string prefix)
        {
            if (hs == null) return "연결 안 됨 — 폰의 " + prefix + "… 에 붙으십시오";
            var head = "연결됨 · " + hs.AdapterName + " (" + hs.Address + ")";
            if (hs.RouteMismatch != null) return head + " · " + hs.RouteMismatch + " — 연결하지 않음";
            if (hs.Ssid == null) return head + " · 이름 모름(" + (hs.SsidError ?? "까닭 모름") + ") — 저절로 연결하지 않음";
            var name = Ssid(hs.Ssid) + (hs.SsidSource == WifiName.FromNlm ? "(" + WifiName.FromNlm + ")" : "");
            if (!hs.SsidStartsWith(prefix)) return head + " · " + name + " — 저절로 연결하지 않음";
            return head + " · " + name;
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

    /// <summary>
    /// 트레이·상태 창이 보는 것 — 같은 프로세스의 Controller(시험 판) 또는 서비스에 파이프로 묻는 ServiceClient(실제 판).
    /// </summary>
    public interface ITetherView
    {
        TetherState State { get; }
        string Detail { get; }
        ToggleAction Toggle { get; }
        bool ChildRunning { get; }
        int ChildPid { get; }
        bool DryRun { get; }
        /// <summary>알고 있는 설정(서비스의 것이면 상태에 실려 온 몇 칸만 — 비밀은 없다).</summary>
        Settings Settings { get; }
        event Action Changed;
        /// <summary>(제목, 글, 경고인가). 누르면 열 주소가 있으면 NoticeUrl.</summary>
        event Action<string, string, bool> Notice;
        string NoticeUrl { get; }
        void Connect();
        void Disconnect();
        void Tick();
        Task ShutdownAsync();
    }

    /// <summary>설정 창이 쓰는 것 — 같은 프로세스의 Controller(시험 판) 또는 서비스에 제안하는 ServiceSettingsHost(실제 판).</summary>
    public interface ISettingsHost
    {
        Settings Settings { get; }
        void SaveSettings(Settings s);
        void ApplySettings(Settings s);
        bool ChildRunning { get; }
        /// <summary>저장하는 동안 설정 창에 보일 글.</summary>
        string SavingText { get; }
        /// <summary>설정 파일을 읽을 때 기본값으로 돌린 키가 있으면 설정 창이 여는 때 보일 한 줄(없으면 null).</summary>
        string LoadNotice { get; }
    }

    public sealed class Controller : ITetherView, ISettingsHost
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

        readonly UpdateWatcher upd;
        public UpdateState Updates { get { return upd.Updates; } }
        public UpdateWatcher Watcher { get { return upd; } }
        /// <summary>시험이 바꾼다 — (주소, 전체 마감 UTC)를 받아 글을 돌려준다(막는 호출).</summary>
        public Func<string, DateTime, string> UpdateFetcher { get { return upd.Fetcher; } set { upd.Fetcher = value; } }
        public string[] BuildSkippedVersions { get { return upd.BuildSkippedVersions; } set { upd.BuildSkippedVersions = value; } }
        /// <summary>
        /// 연결된 동안 업데이트를 물을까 — 서비스(LocalSystem)는 끈다: 남의 서버(GitHub)의 글을 SYSTEM 프로세스에서 읽지 않는다.
        /// 실제 판의 트레이가 따로 묻는다(UpdateWatcher).
        /// </summary>
        public bool UpdatesEnabled = true;

        IChild child;
        HeldBinaries held;
        bool userWantsOn;
        /// <summary>
        /// 사용자가 '끊기' 를 눌렀다 — 다음 '연결' 이나 서비스 재시작까지 저절로 다시 연결하지 않는다(RearmAsync 도). 오류·다시 연결 포기는
        /// 끊음이 아니다(9차 판정 11-28).
        /// </summary>
        bool userStopped;
        /// <summary>망 바뀜 알림이 왔다 — 다음 Tick 이 핫스팟을 곧바로 본다.</summary>
        bool networkChanged;
        DateTime nextRearmProbe = DateTime.MinValue;
        /// <summary>꺼짐·오류 동안 마지막으로 본 핫스팟이 접두어에 맞았는가(null = 아직 안 봄 — 첫 관찰은 바탕만 정한다).</summary>
        bool? rearmSawMatch;
        string rearmLogWhy;
        DateTime rearmLogAt = DateTime.MinValue;
        /// <summary>꺼짐·오류 동안 핫스팟을 다시 보는 간격(초) — 망 바뀜 알림을 놓쳤을 때의 물러서기. 시험이 줄인다.</summary>
        public int RearmPollSeconds = 30;
        /// <summary>같은 까닭의 '다시 시작' 줄을 이 동안 한 번만 적는다(분).</summary>
        public int RearmLogQuietMinutes = 10;
        /// <summary>
        /// 망 바뀜만으로 다시 시작해도 되는가 — 마지막으로 멈춘 까닭이 tun2proxy 를 띄우기 전의 되풀이할 수 있는 실패(핫스팟·폰 프록시
        /// 없음)였을 때만(10차 판정 11-28). 띄운 뒤의 실패(뜨자마자 죽음 — 시도마다 TUN 이 생기고 사라져 망 바뀜 알림이 스스로 온다)나
        /// 되풀이하지 않는 오류(TUN 이 나타나지 않음·다른 tun2proxy·설정)로 멈췄으면 접두어 Wi-Fi 가 사라졌다 나타날 때와 콘솔 로그온 때만.
        /// </summary>
        bool rearmOnNetwork = true;
        /// <summary>꺼짐·오류로 들어선 때 — 그 직후 NetworkQuietSeconds 안의 망 바뀜 알림은 우리가 걷은 TUN 의 것으로 보고 세지 않는다.</summary>
        DateTime stoppedAt = DateTime.MinValue;
        public int NetworkQuietSeconds = 10;
        /// <summary>다시 시작의 예산 — 한 시간에 RearmPerHour 번, 잇달아 할수록 간격을 RearmBaseGapSeconds 에서 두 배씩(한 시간까지).
        /// 1분 넘게 붙어 있었거나 사용자의 '연결'·콘솔 로그온이면 새로.</summary>
        public int RearmPerHour = 3;
        public int RearmBaseGapSeconds = 60;
        readonly List<DateTime> rearmTimes = new List<DateTime>();
        int rearmStreak;
        DateTime nextRearmAllowed = DateTime.MinValue;
        /// <summary>예산이 막아 미룬 다시 시작의 까닭 — 그 Wi-Fi 가 그대로 보이면 예산이 열린 뒤의 점검이 잇는다.</summary>
        string rearmDeferred;
        string deferLogWhy;
        DateTime deferLogAt = DateTime.MinValue;
        /// <summary>'연결이 끊겼습니다'·'다시 연결을 멈췄습니다' 를 이미 띄웠다 — 1분 넘게 붙어 있거나 사용자가 '연결' 할 때까지 되풀이하지 않는다.</summary>
        bool dropNoticeShown;
        bool stopNoticeShown;
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
        /// <summary>시험이 바꾼다 — 핫스팟과 그 SSID 찾기(SSID 접두어를 받는다). 막는 호출이라 일꾼 스레드에서만 부른다.</summary>
        public Func<string, HotspotInfo> HotspotFinder = NetState.FindHotspot;
        /// <summary>
        /// Wi-Fi 이름의 10초 기억을 버린다(시험이 바꾼다). 연결을 정하는 자리(Attempt)는 핫스팟을 살피기 전에 늘 부른다 — 기억한 옛 이름으로
        /// 남의 망에 연결하지 않게. 1초 점검·상태 창은 기억을 쓴다(위치 표시 — WifiName).
        /// </summary>
        public Action ForgetWifiNames = WifiName.Forget;
        /// <summary>
        /// 손으로 누른 '연결' 이 접두어 밖(또는 이름을 모르는) Wi-Fi 를 만났을 때 묻는다 — (핫스팟, 접두어) → 연결할까. UI 스레드에서 부른다.
        /// null 이면 묻지 않고 연결하지 않는다.
        /// </summary>
        public Func<HotspotInfo, string, bool> AskConnect;
        /// <summary>묻는 창이 떠 있는 동안 '연결' 을 다시 누르면 부른다 — 그 창을 다시 맨 앞으로(UI 스레드). null 이면 아무것도 하지 않는다.</summary>
        public Action ReshowAsk;
        /// <summary>AskConnect 가 떠 있는 동안 참.</summary>
        bool asking;
        /// <summary>
        /// 시도가 도는 동안(busy) 사용자가 누른 '연결' — 새 시도를 띄우지 않고(tun2proxy 를 둘 세우지 않게) 도는 시도를 손 시도로 올린다:
        /// 그 시도가 Wi-Fi 이름을 접두어 밖(또는 모름)으로 보면 거절하지 않고 묻는다. 시도가 끝나면 지운다(검토 11-21 — 전에는 묻혔다).
        /// </summary>
        bool manualRequested;
        /// <summary>이 실행에서 사용자가 물음에 '예' 라고 한 접두어 밖의 SSID — 그 이름이면 다시 연결도 저절로 한다(메모리에만).</summary>
        string approvedSsid;
        /// <summary>지금 연결이 붙은 SSID(모르면 null) — 연결된 동안 이름이 바뀌면 핫스팟이 사라진 것으로 친다.</summary>
        string connectedSsid;
        /// <summary>연결한 핫스팟 어댑터의 GUID(NetworkInterface.Id) — Wi-Fi 끊김 알림이 그 어댑터의 것인지 본다(모르면 빈 글).</summary>
        string linkAdapterId = "";
        /// <summary>링크 알림(주소 변경·Wi-Fi 끊김)으로 곧바로 핫스팟을 보는 중인가 — 그동안 온 알림은 한 번 더 보게만 한다.</summary>
        bool linkChecking;
        bool linkAgain;
        string linkWhy;
        DateTime fastUntil = DateTime.MinValue;
        /// <summary>Wi-Fi 끊김 알림 뒤 이 동안(밀리초) FastCheckMs 마다 핫스팟을 다시 본다 — 알림이 주소가 빠지기 전에 온다. 시험이 줄인다.</summary>
        public int FastWindowMs = 3000;
        public int FastCheckMs = 100;
        /// <summary>1초 점검의 살피기가 일꾼 스레드에서 도는 중 — 겹치지 않게 그동안의 Tick 은 건너뛴다.</summary>
        bool tickRunning;
        /// <summary>상태가 바뀔 때마다 오른다 — 늦게 돌아온 살피기 결과가 그사이 바뀐 상태를 덮지 않게.</summary>
        int version;
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
            upd = new UpdateWatcher(paths.UpdateFile, paths.Root, log);
            upd.Tell = (title, text, warn, url) => Tell(title, text, warn, url);
            upd.Changed += RaiseChanged;
            State = TetherState.Off;
            Detail = "";
        }

        public bool ChildRunning { get { return child != null && !child.HasExited; } }
        public string SavingText { get { return "저장 중…"; } }
        public string LoadNotice { get; set; }
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

        void Set(TetherState s, string detail)
        {
            version++;
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
        /// 사용자의 '연결'. Wi-Fi 이름이 접두어 밖이거나 모르면 AskConnect 로 한 번 묻는다(사용자의 뜻이므로).
        /// 기다릴 수 있는 꼴(시험). 시도가 이미 도는 중이면 그 시도가 이어 간다(그사이 끊기를 눌렀어도) — 손 시도로 올려(manualRequested)
        /// 이름이 접두어 밖이면 묻게 한다. 묻는 창이 떠 있으면 그 창을 다시 앞으로(ReshowAsk).
        /// </summary>
        public Task ConnectAsync()
        {
            startupUntil = DateTime.MinValue;
            userStopped = false;
            RenewRearmBudget();
            dropNoticeShown = false;
            stopNoticeShown = false;
            // 끝났지만 알림을 아직 받지 않은 자식도 — 그 걷기(OnChildExited)가 먼저다. 그 뒤는 다시 연결 규칙이 잇는다.
            if (child != null) return Task.FromResult(0);
            userWantsOn = true;
            if (busy)
            {
                retries = 0;
                if (asking)
                {
                    log.Write("Wi-Fi", "묻는 창이 떠 있습니다 — 다시 앞으로");
                    var reshow = ReshowAsk;
                    if (reshow != null) reshow();
                }
                else if (!manualRequested)
                {
                    manualRequested = true;
                    log.Write("시작", "도는 시도를 사용자의 '연결' 로 잇습니다");
                }
                return Task.FromResult(0);
            }
            retries = 0;
            return Attempt(true);
        }

        public async void AutoConnect()
        {
            await AutoConnectAsync();
        }

        /// <summary>
        /// 실행할 때 — 늘 저절로 연결한다(사용자의 '끊기' 는 메모리에만 남아 그 실행 동안만 듣는다 — 사용자 결정 11-21). 시작 직후
        /// StartupWaitSeconds 동안은 핫스팟·폰 프록시가 없어도(이름이 접두어 밖인 Wi-Fi 도) 횟수를 쓰지 않고 기다린다(StartupRetrySeconds 마다).
        /// 다른 tun2proxy·틀린 설정은 지금처럼 오류로 멈춘다.
        /// </summary>
        public Task AutoConnectAsync()
        {
            if (ChildRunning || busy || userWantsOn) return Task.FromResult(0);
            log.Write("시작", "저절로 연결합니다");
            return AutoConnectCore();
        }

        Task AutoConnectCore()
        {
            userWantsOn = true;
            rearmSawMatch = null;
            networkChanged = false;
            retries = 0;
            startupUntil = DateTime.Now.AddSeconds(StartupWaitSeconds);
            return Attempt(false);
        }

        bool InStartupWait(DateTime now) { return startupUntil != DateTime.MinValue && now < startupUntil; }

        /// <summary>
        /// 저절로 연결을 다시 시작한다(9차 판정 11-28) — 부팅 때 한 번만 시작하면 폰의 테더링을 나중에 켜거나 로그온이 늦을 때 '다시 연결을
        /// 멈췄습니다' 로 끝나 사용자가 '연결' 을 눌러야 했다. 사용자가 끊지 않았고(userStopped), 연결되지 않았고 하는 시도도 없고, '다시 연결' 이
        /// 켜져 있을 때만. 시작은 서비스가 뜰 때와 같다(너그러운 기다림부터). 같은 까닭의 줄은 RearmLogQuietMinutes 동안 한 번만 적는다.
        /// 부르는 곳: 망이 바뀌어 접두어에 맞는 Wi-Fi 가 보임·꺼짐 동안 그 Wi-Fi 가 나타남(Tick), 콘솔 로그온(서비스의 세션 변경 — renew).
        /// 예산(RearmPerHour·RearmBaseGapSeconds — 10차 판정 11-28)이 막으면 미루고(rearmDeferred) 같은 까닭이면 한 줄만 적는다.
        /// renew: 예산을 새로 하고 시작한다(콘솔 로그온).
        /// </summary>
        public Task RearmAsync(string why, bool renew = false)
        {
            if (renew) RenewRearmBudget();
            if (userStopped || userWantsOn || busy || child != null || stopTask != null || settling > 0 || !Settings.Reconnect)
                return Task.FromResult(0);
            var now = DateTime.Now;
            var refusal = RearmRefusal(now);
            if (refusal != null)
            {
                rearmDeferred = why;
                if (why != deferLogWhy || (now - deferLogAt).TotalMinutes >= RearmLogQuietMinutes)
                {
                    log.Write("시작", "저절로 다시 시작을 미룹니다 — " + why + " (" + refusal + ")");
                    deferLogWhy = why;
                    deferLogAt = now;
                }
                return Task.FromResult(0);
            }
            rearmDeferred = null;
            SpendRearm(now);
            if (why != rearmLogWhy || (now - rearmLogAt).TotalMinutes >= RearmLogQuietMinutes)
            {
                log.Write("시작", "저절로 연결을 다시 시작합니다 — " + why);
                rearmLogWhy = why;
                rearmLogAt = now;
            }
            retries = 0;
            return AutoConnectCore();
        }

        /// <summary>예산이 막으면 그 까닭, 아니면 null. 시계가 거꾸로 갔으면 거꾸로 간 기록은 버린다.</summary>
        string RearmRefusal(DateTime now)
        {
            rearmTimes.RemoveAll(t => t > now || (now - t).TotalHours >= 1);
            if (nextRearmAllowed > now.AddHours(1)) nextRearmAllowed = now;
            if (rearmTimes.Count >= RearmPerHour) return "한 시간에 " + RearmPerHour + "번을 다 썼습니다";
            if (now < nextRearmAllowed)
                return "다음은 " + ((int)Math.Ceiling((nextRearmAllowed - now).TotalSeconds)).ToString(System.Globalization.CultureInfo.InvariantCulture) + "초 뒤";
            return null;
        }

        void SpendRearm(DateTime now)
        {
            rearmTimes.Add(now);
            rearmStreak++;
            double gap = RearmBaseGapSeconds * Math.Pow(2, Math.Min(rearmStreak - 1, 16));
            nextRearmAllowed = now.AddSeconds(Math.Min(gap, 3600));
        }

        void RenewRearmBudget()
        {
            rearmTimes.Clear();
            rearmStreak = 0;
            nextRearmAllowed = DateTime.MinValue;
            rearmDeferred = null;
        }

        /// <summary>
        /// 망이 바뀌었다(서비스의 NetworkChange 알림 — UI 스레드에서) — 다음 Tick 이 핫스팟을 곧바로 본다. 꺼짐·오류로 들어선 뒤
        /// NetworkQuietSeconds 가 지나 온 알림만 센다 — 시도 중·연결 중의 알림과 멈춘 직후의 알림은 우리가 만들고 걷은 TUN 의 것이다(10차 판정).
        /// </summary>
        public void NetworkChanged()
        {
            // 연결 중(자식이 있음)의 알림은 다시 시작 깃발에 쓰지 않고 곧바로 핫스팟을 보는 데만 쓴다(10차와 따로 — 내리기용).
            if (child != null) { CheckLinkSoon("주소 바뀜", false); return; }
            if (userWantsOn || busy || child != null || stopTask != null || settling > 0) return;
            if (State != TetherState.Off && State != TetherState.Error) return;
            var since = (DateTime.Now - stoppedAt).TotalSeconds;
            if (since >= 0 && since < NetworkQuietSeconds) return;
            networkChanged = true;
        }

        /// <summary>사용자가 끊어 저절로 다시 연결하지 않는 중인가(시험·상태).</summary>
        public bool UserStopped { get { return userStopped; } }

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

        /// <summary>
        /// 한 번의 연결 시도. manual: 사용자가 누른 '연결' — Wi-Fi 이름이 접두어 밖이면 묻는다. 저절로 하는 시도(시작·다시 연결)는
        /// 묻지 않고 기다린다. 막는 살피기(다른 tun2proxy·핫스팟·SSID·폰 프록시·풀기)는 모두 일꾼 스레드에서.
        /// </summary>
        async Task Attempt(bool manual)
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

                var lister = Tun2proxyLister;
                var ownNow = child;
                int minePid = ownNow != null ? ownNow.Pid : 0;
                var others = await Task.Run(() => { var r = new List<int>(); foreach (var pid in lister()) if (pid != minePid) r.Add(pid); return r; });
                if (Cancelled(null)) return;
                if (others.Count > 0)
                {
                    // 이전 판 도우미가 떠 있으면 그 tun2proxy(-bin) 가 여기에 걸린다(11-28 — 사용자 기기에서 그랬다). 까닭과 할 일을 로그·트레이에.
                    Fail("다른 tun2proxy 가 실행 중입니다(PID " + string.Join(", ", others.ConvertAll(p => p.ToString(System.Globalization.CultureInfo.InvariantCulture)).ToArray()) +
                         ") — 이전 판 도우미라면 그 알림 영역 아이콘의 ‘끝내기’ 로 끝낸 뒤 다시 연결하십시오.", false);
                    return;
                }

                var prefix = s.SsidPrefix;
                var finder = HotspotFinder;
                var forget = ForgetWifiNames;
                Func<HotspotInfo> fresh = () => { if (forget != null) forget(); return finder(prefix); };
                var hs = await Task.Run(fresh);
                if (Cancelled(null)) return;
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
                        hs = await Task.Run(fresh);
                        if (Cancelled(null)) return;
                    }
                }
                if (hs == null) { Fail("폰 핫스팟에 연결되어 있지 않습니다", true, true); return; }
                log.Write("Wi-Fi", hs.AdapterName + " · " + (hs.Ssid != null ? StatusText.Ssid(hs.Ssid) + (hs.SsidSource != null ? " (" + hs.SsidSource + ")" : "") : "이름 모름 — " + hs.SsidError));

                // 안드로이드의 Wi-Fi Direct 그룹 오너는 모두 192.168.49.1 — 주소·프록시 응답만 보면 저장된 프로필로 남의 DIRECT 망에
                // 붙었을 때도 PC 의 트래픽 전체를 그쪽으로 보낸다. 이름이 접두어로 시작할 때만 저절로, 손으로 누른 것은 묻고.
                // 이름을 검사한 어댑터와 192.168.49.1 로 가는 어댑터가 다르면 묻지도 않는다 — '예' 가 검사하지 않은 망을 허락하게 된다.
                if (hs.RouteMismatch != null) { manualRequested = false; Fail(hs.RouteMismatch, true, true); return; }
                var refusal = StatusText.Refusal(hs, prefix, approvedSsid);
                if (refusal != null)
                {
                    // 저절로 하는 시도가 도는 사이 사용자가 '연결' 을 눌렀다 — 그 뜻을 묻는 데까지 잇는다(새 시도를 띄우지 않았다).
                    if (!manual && manualRequested)
                    {
                        manual = true;
                        log.Write("Wi-Fi", refusal + " — 사용자가 '연결' 을 눌러 묻습니다");
                    }
                    manualRequested = false;
                    if (!manual) { Fail(refusal, true, true); return; }
                    var ask = AskConnect;
                    Set(TetherState.Connecting, "묻는 중 — Wi-Fi 이름");
                    bool yes = false;
                    asking = true;
                    try { yes = ask != null && ask(hs, prefix); }
                    finally { asking = false; }
                    log.Write("Wi-Fi", refusal + " — 물어서 " + (yes ? "연결합니다" : "연결하지 않습니다"));
                    if (Cancelled(null)) return;
                    if (!yes)
                    {
                        userWantsOn = false;
                        Set(TetherState.Off, "연결하지 않음 — Wi-Fi 이름");
                        return;
                    }
                    if (hs.Ssid != null) approvedSsid = hs.Ssid;
                    // 묻는 창은 오래 떠 있을 수 있다(주인 창이 없어 다른 창 뒤에도) — 그사이 Wi-Fi 가 다른 망으로 옮겨 갔으면 허락은 그 망의
                    // 것이 아니다. 다시 보고 허락한 이름(또는 접두어에 맞는 망)일 때만, 이름을 모르던 것은 같은 어댑터·주소일 때만 잇는다(검토 11-21).
                    var asked = hs;
                    hs = await Task.Run(fresh);
                    if (Cancelled(null)) return;
                    bool same = hs != null && (StatusText.Refusal(hs, prefix, approvedSsid) == null ||
                                               (asked.Ssid == null && hs.Ssid == null && hs.AdapterName == asked.AdapterName && hs.Address == asked.Address));
                    if (!same)
                    {
                        log.Write("Wi-Fi", "묻는 사이 Wi-Fi 가 바뀌어 연결하지 않습니다");
                        userWantsOn = false;
                        Set(TetherState.Off, "연결하지 않음 — 묻는 사이 Wi-Fi 가 바뀜");
                        return;
                    }
                }

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
                    // 번호와 함께 어댑터 GUID 를 적는다 — 되살릴 때 그 번호가 다른 어댑터에 돌아갔으면 넣지 않는다.
                    var ids = LiveAdapterIds();
                    foreach (var d in st.DefaultsBefore)
                    {
                        string id;
                        if (ids.TryGetValue(d.IfIndex, out id)) d.AdapterId = id;
                    }
                    st.KeptBypass = Routes.ExistingBypass(table, st.DefaultsBefore, st.Bypass);
                }
                catch (Exception ex) { log.Write("경로", "경로 표를 읽지 못함: " + ex.Message); }
                try { st.Save(paths.StateFile, paths.Root); }
                catch (Exception ex) { log.Write("상태", "상태 파일을 쓰지 못함: " + ex.Message); }

                var args = CommandLine.Tun2proxyArgs(s);
                log.Write("시작", "tun2proxy " + CommandLine.Join(args));
                // 경로 고리(LinkWatch.cs 머리말) — 프록시(폰) 주소로 가는 세션이 TUN 에 들어오면 곧바로 끝낸다. 출력은 일꾼 스레드에서 온다.
                var proxyHost = s.ProxyHost;
                IChild launched = null;
                int loopSeen = 0;
                Action<string, string> onOutput = (src, line) =>
                {
                    log.Write(src, line);
                    var dst = LoopGuard.Match(line, proxyHost);
                    if (dst != null && Interlocked.Exchange(ref loopSeen, 1) == 0)
                        ui.Post(_ => RouteLoop(launched, dst), null);
                };
                var c = children.Start(hb, args, onOutput, out err);
                launched = c;
                if (c == null)
                {
                    if (hb != null) hb.Dispose();
                    Fail("tun2proxy 를 띄우지 못했습니다: " + err, false);
                    return;
                }
                held = hb;
                child = c;
                connectedSsid = hs.Ssid;
                linkAdapterId = hs.AdapterId ?? "";
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
                manualRequested = false;
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
            {
                if (!stopNoticeShown) Tell("다시 연결을 멈췄습니다", why, true);
                else log.Write("시작", "되풀이라 알리지 않음 — 다시 연결을 멈췄습니다");
                stopNoticeShown = true;
            }
            userWantsOn = false;
            rearmOnNetwork = retryable && beforeStart;
            stoppedAt = DateTime.Now;
            Set(TetherState.Error, why);
        }

        /// <summary>'연결이 끊겼습니다' — 다시 시작 고리(뜨자마자 죽기)에서 되풀이하지 않는다(10차 판정).</summary>
        void TellDropped(string text)
        {
            if (dropNoticeShown) { log.Write("시작", "되풀이라 알리지 않음 — 연결이 끊겼습니다: " + text); return; }
            dropNoticeShown = true;
            Tell("연결이 끊겼습니다", text, true);
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
            TellDropped("tun2proxy 가 끝났습니다(코드 " + code + ").");
            if (longConnected) retries = 0;
            Fail("tun2proxy 가 끝났습니다(코드 " + code + ")", true);
        }

        // ---- 끊기 ----

        public async void Disconnect()
        {
            await DisconnectAsync();
        }

        /// <summary>
        /// 사용자의 '끊기'(트레이·상태 창) — 이 실행 동안은 다시 연결하지 않는다(userWantsOn). 파일에 남기지 않는다 — 도우미를 다시
        /// 실행하면 저절로 연결한다(사용자 결정 11-21). 예전 판이 남긴 user-disconnected 파일은 읽지도 지우지도 않는다.
        /// </summary>
        public async Task DisconnectAsync()
        {
            userWantsOn = false;
            userStopped = true;
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
        Task StopChildAsync(string why, bool hard = false)
        {
            var c = child;
            if (c == null) return stopTask ?? Task.FromResult(0);
            if (stopTask != null && stopTarget == c) return stopTask;
            stopTarget = c;
            var t = StopChildCore(c, why, hard);
            if (!t.IsCompleted) stopTask = t;
            return t;
        }

        async Task StopChildCore(IChild c, string why, bool hard)
        {
            stopping = true;
            try
            {
                Set(State, "끊는 중");
                string detail = null;
                bool clean;
                if (hard)
                {
                    // Ctrl+C 를 기다리는 동안에도 고리가 불어난다 — 곧바로 끝낸다. tun2proxy 의 걷기는 돌지 않고 우리 정리(Routes)가 맡는다.
                    log.Write("끊기", why + " — 곧바로 끝냅니다");
                    clean = true;
                    await Task.Run(() => c.Kill());
                }
                else
                {
                    log.Write("끊기", why + " — Ctrl+C 를 보냅니다");
                    clean = await Task.Run(() => c.StopGracefully(StopGraceMs, out detail));
                }
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
            // 우리가 띄운 것인지는 이름이 아니라 그 핸들로 본다 — 끝내기가 아직 안 끝났으면(살아 있다) 걷지 않는다.
            bool ownAlive = false;
            int ownPid = 0;
            if (c != null)
            {
                ownPid = c.Pid;
                try { ownAlive = !c.HasExited; } catch (Exception) { ownAlive = true; }
                c.Dispose();
            }
            return StartCleanup(false, ownAlive, ownPid);
        }

        /// <summary>
        /// 남은 경로를 걷는다 — 일꾼 스레드에서(route.exe 를 부른다). 앞의 걷기가 돌면 그 뒤에. 끝나기 전에는 Tick 이 쉬고
        /// 새 시도가 기다린다.
        /// </summary>
        Task StartCleanup(bool startup, bool ownAlive = false, int ownPid = 0)
        {
            var fallbackTun = Settings.TunName;
            var prev = pendingCleanup;
            var t = prev.ContinueWith(_ => CleanupCore(fallbackTun, startup, ownAlive, ownPid), CancellationToken.None,
                                      TaskContinuationOptions.None, TaskScheduler.Default);
            pendingCleanup = t;
            // 끝나면 화면에 알린다 — 단추가 '정리 중' 에서 '연결' 로 돌아오게(트레이 메뉴는 Changed 때만 다시 그린다).
            t.ContinueWith(_ => ui.Post(__ => RaiseChanged(), null), CancellationToken.None,
                           TaskContinuationOptions.None, TaskScheduler.Default);
            return t;
        }

        /// <summary>
        /// 이름이 tun2proxy 로 시작하는 프로세스 가운데 우리가 띄운 것(그 PID)을 뺀 것 — 이름은 아무 프로그램이나 쓸 수 있으므로 '다른
        /// tun2proxy' 의 어림일 뿐이다(배치 파일로 띄운 것과 TUN 이 부딪치지 않게).
        /// </summary>
        List<int> OtherTun2proxies(int ownPid)
        {
            var r = new List<int>();
            foreach (var pid in Tun2proxyLister()) if (pid != ownPid) r.Add(pid);
            return r;
        }

        void CleanupCore(string fallbackTun, bool startup, bool ownAlive, int ownPid)
        {
            try
            {
                var st = ConnectionState.Load(paths.StateFile);
                if (startup && st != null) log.Write("정리", "지난번 연결의 흔적을 확인합니다");
                var notes = new List<string>();
                var tunName = st != null && st.TunName.Length > 0 ? st.TunName : fallbackTun;
                var ids = LiveAdapterIds();
                var live = new HashSet<uint>(ids.Keys);
                // 우리가 띄운 것이 살아 있으면(끝내기가 늦다) 걷지 않는다. 이름만 같은 다른 프로세스는 TUN 어댑터가 떠 있을 때만 막는다 —
                // 진짜 tun2proxy(배치 파일)는 TUN 을 세우고, 이름만 빌린 프로그램이 걷기를 영영 막지 못하게(관리자 일을 보통 권한이 막는다).
                var others = OtherTun2proxies(ownPid);
                bool tunUp = others.Count > 0 && TunPresent(tunName);
                bool other = ownAlive || tunUp;
                if (!other && others.Count > 0)
                    log.Write("정리", "이름이 tun2proxy 인 다른 프로세스가 있지만 TUN 어댑터(" + tunName + ")가 없어 걷습니다");
                if (ownAlive) notes.Add("띄운 tun2proxy 가 아직 끝나지 않았습니다");
                var plan = Routes.CleanupPlan(RouteReader(), st, NetState.IfIndexOf(tunName), live, ids, other, notes);
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

        /// <summary>지금 있는 어댑터의 IPv4 번호 → GUID(NetworkInterface.Id). 시험이 바꾼다.</summary>
        public Func<Dictionary<uint, string>> LiveAdapterIds = ReadAdapterIds;

        static Dictionary<uint, string> ReadAdapterIds()
        {
            var r = new Dictionary<uint, string>();
            foreach (var ni in SafeInterfaces())
            {
                try { r[(uint)ni.GetIPProperties().GetIPv4Properties().Index] = ni.Id; } catch (Exception) { }
            }
            return r;
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
            var t = TickAsync();
        }

        sealed class Probed<T>
        {
            public bool Ok;
            public T Value;
        }

        /// <summary>
        /// 막는 살피기(핫스팟·SSID 는 어댑터·Wi-Fi API, TUN 은 어댑터 목록)를 일꾼 스레드에서 — 결과만 이 스레드(UI)로 돌아온다.
        /// 그동안 tickRunning 이라 다음 Tick 이 겹치지 않는다. 던지면 기록하고 Ok=false(그 Tick 은 아무것도 하지 않는다 —
        /// 살피기의 실패를 '핫스팟 없음' 으로 읽어 끊지 않게).
        /// </summary>
        async Task<Probed<T>> Probe<T>(Func<T> f)
        {
            tickRunning = true;
            try
            {
                var v = await Task.Run(f);
                return new Probed<T> { Ok = true, Value = v };
            }
            catch (Exception ex)
            {
                log.Write("점검", "살피지 못함: " + ex.Message);
                return new Probed<T>();
            }
            finally { tickRunning = false; }
        }

        /// <summary>살피는 사이 상태·자식이 바뀌었거나 끊는·걷는·시도하는 중이면 그 결과는 낡았다 — 버리고 다음 Tick 이 다시 본다.</summary>
        bool Stale(int v, IChild c)
        {
            return version != v || child != c || stopping || busy || settling > 0 || !pendingCleanup.IsCompleted;
        }

        /// <summary>
        /// 연결된 동안 — 핫스팟이 사라졌거나 Wi-Fi 이름이 연결할 때와 달라졌으면 그 까닭(모르게 된 것은 그대로 둔다).
        /// 이름을 모르는 채 허락해 붙은 망(connectedSsid 없음)은 이름이 알려지면 다시 본다 — 접두어 밖이고 허락한 이름도 아니면 끊고,
        /// 맞으면 그 이름을 기억해 그 뒤 바뀌는 것을 잡는다. 여전히 모르는 동안은 그대로 둔다(검토 11-21).
        /// </summary>
        string LostWhy(HotspotInfo hs, string prefix)
        {
            if (hs == null) return "폰 핫스팟이 사라졌습니다";
            if (hs.RouteMismatch != null) return hs.RouteMismatch;
            if (hs.Ssid == null) return null;
            if (connectedSsid == null)
            {
                if (StatusText.Refusal(hs, prefix, approvedSsid) != null)
                    return "이름을 모른 채 연결한 Wi-Fi 가 " + StatusText.Ssid(hs.Ssid) + " 로 확인됨 — " + prefix + " 로 시작하지 않음";
                connectedSsid = hs.Ssid;
                log.Write("Wi-Fi", "연결한 Wi-Fi 의 이름을 확인함 — " + StatusText.Ssid(hs.Ssid));
                return null;
            }
            if (hs.Ssid != connectedSsid)
                return "Wi-Fi 가 다른 망으로 바뀌었습니다(" + StatusText.Ssid(hs.Ssid) + ")";
            return null;
        }

        /// <summary>
        /// 1초마다(UI 스레드). 막는 살피기는 Probe 로 일꾼 스레드에서 하고, 앞 살피기가 아직 돌면 건너뛴다. 돌아온 결과는 그사이 상태가
        /// 바뀌었으면 버린다. 기다릴 수 있는 꼴(시험 — 그 Tick 이 띄운 시도까지 기다린다).
        /// </summary>
        public async Task TickAsync()
        {
            if (tickRunning) return;
            var now = DateTime.Now;
            var s = Settings;
            if (stopping || busy || settling > 0 || !pendingCleanup.IsCompleted) return;
            int v = version;
            var c = child;

            if (ChildRunning)
            {
                if (State == TetherState.Connecting)
                {
                    var age = (now - child.StartedAt).TotalSeconds;
                    if (age < 2) return;
                    var tun = s.TunName;
                    var present = TunPresent;
                    var up = await Probe(() => present(tun));
                    if (!up.Ok || Stale(v, c)) return;
                    if (up.Value)
                    {
                        connectedAt = now;
                        Set(TetherState.Connected, "");
                    }
                    else if (age >= ConnectTimeoutSeconds) ConnectTimedOut(tun, ConnectTimeoutSeconds);
                }
                else if (State == TetherState.Connected)
                {
                    if ((now - connectedAt).TotalSeconds > 60)
                    {
                        retries = 0;
                        RenewRearmBudget();
                        dropNoticeShown = false;
                        stopNoticeShown = false;
                    }
                    var prefix = s.SsidPrefix;
                    var finder = HotspotFinder;
                    var hs = await Probe(() => finder(prefix));
                    if (!hs.Ok || Stale(v, c)) return;
                    var lost = LostWhy(hs.Value, prefix);
                    if (lost != null) HotspotLost(lost);
                    else if (UpdatesEnabled && upd.Due(now, s)) CheckUpdates();
                }
                return;
            }

            if (State == TetherState.Waiting && userWantsOn && now >= nextRetry)
            {
                // 저절로 연결한 직후의 기다림이 끝났으면 한 번 더 해 본다 — 그 실패부터 늘 쓰는 규칙(횟수·'다시 연결' 설정)을 탄다.
                bool startupOver = startupUntil != DateTime.MinValue && !InStartupWait(now);
                var prefix = s.SsidPrefix;
                var finder = HotspotFinder;
                var found = await Probe(() => finder(prefix));
                if (!found.Ok || Stale(v, c) || !userWantsOn) return;
                var hs = found.Value;
                var refusal = StatusText.Refusal(hs, prefix, approvedSsid);
                if (refusal == null || (hs == null && s.WifiProfile.Length > 0) || startupOver)
                {
                    await Attempt(false);
                }
                else
                {
                    // 핫스팟이 돌아오기를(또는 접두어에 맞는 Wi-Fi 를) 기다린다 — 횟수를 쓰지 않고 다시 미룬다(시작 직후에는 더 자주 본다).
                    nextRetry = now.AddSeconds(InStartupWait(now) ? Math.Min(StartupRetrySeconds, s.ReconnectInterval) : s.ReconnectInterval);
                    if (hs != null)
                    {
                        var text = refusal + " — 기다리는 중";
                        if (Detail != text) Set(TetherState.Waiting, text);
                    }
                }
                return;
            }

            // 꺼짐·오류 동안(사용자가 끊은 것이 아니면) — 망이 바뀌었거나 RearmPollSeconds 가 지나면 핫스팟을 보고, 접두어에 맞는 Wi-Fi 가
            // 보이면(망 바뀜 — rearmOnNetwork 일 때만) 또는 새로 나타나면(처음 본 것은 바탕만) 저절로 연결을 다시 시작한다. 접두어 밖 Wi-Fi 는
            // 저절로 붙지 않는다(지금처럼 — 손으로 '연결' 해야 묻는다).
            if (!userWantsOn && !userStopped && (State == TetherState.Off || State == TetherState.Error) && s.Reconnect)
            {
                bool netChanged = networkChanged;
                if (!netChanged && now < nextRearmProbe) return;
                networkChanged = false;
                nextRearmProbe = now.AddSeconds(RearmPollSeconds);
                var prefix = s.SsidPrefix;
                var finder = HotspotFinder;
                var found = await Probe(() => finder(prefix));
                if (!found.Ok || Stale(v, c) || userWantsOn || userStopped) return;
                bool match = found.Value != null && StatusText.Refusal(found.Value, prefix, null) == null;
                bool? before = rearmSawMatch;
                rearmSawMatch = match;
                if (!match) { rearmDeferred = null; return; }
                // 망 바뀜만으로는 띄우기 전의 실패로 멈췄을 때만(rearmOnNetwork — 10차 판정). 나타남은 멈춘 까닭과 상관없다. 예산이 미룬 것은
                // 그 Wi-Fi 가 내내 보이는 동안 예산이 열리면 잇는다.
                string why = null;
                if (before == false) why = "Wi-Fi 이름이 " + prefix + " 로 시작하는 망이 나타남";
                else if (netChanged && rearmOnNetwork) why = "망이 바뀌었고 Wi-Fi 이름이 " + prefix + " 로 시작함";
                else if (rearmDeferred != null) why = rearmDeferred;
                if (why != null) await RearmAsync(why);
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

        // ---- 업데이트 알림(알리기만) — UpdateWatcher 에 맡긴다 ----

        /// <summary>새 판이 있으면 그 판, 없으면 null.</summary>
        public string NewVersion { get { return upd.NewVersion; } }

        public async void CheckUpdates()
        {
            await CheckUpdatesAsync();
        }

        public Task CheckUpdatesAsync() { return upd.CheckAsync(); }

        public void SkipVersion(string v) { upd.Skip(v); }

        /// <summary>
        /// 경로 고리 — tun2proxy 가 프록시(폰) 주소로 가는 세션을 TUN 에서 받았다(LoopGuard). 폰으로 가는 길이 TUN 으로 바뀌었다는 뜻이라 곧바로
        /// 강제로 끝낸다. 이미 끊는 중(핫스팟 사라짐 등)이면 Ctrl+C 를 기다리지 않고 끝내기만 하고 상태는 그 끊기가 정한다.
        /// 분류는 '띄운 뒤의 실패'(Fail retryable, beforeStart 아님, 횟수를 0 으로 돌리지 않는다) — 핫스팟이 그대로인데 길만 틀린 고장이면
        /// 핫스팟 사라짐(횟수를 0 으로)처럼 다루면 끝없이 띄우고 끊는다. Wi-Fi 가 정말 사라졌으면 대기가 횟수를 쓰지 않고 핫스팟을 기다린다.
        /// </summary>
        async void RouteLoop(IChild c, string dst)
        {
            if (c == null) c = child;
            if (c == null || c != child) return;
            log.Write("경로", "경로 고리를 막으려고 tun2proxy 를 멈췄습니다 — " + dst + " 가 TUN 으로 들어옴(폰으로 가는 길이 TUN 으로 바뀜)");
            if (stopTask != null && stopTarget == c)
            {
                await Task.Run(() => { try { c.Kill(); } catch (Exception) { } });
                return;
            }
            if (stopping) return;
            TellDropped("폰으로 가는 길이 끊겨(경로 고리) tun2proxy 를 멈췄습니다.");
            settling++;
            try { await StopChildAsync("경로 고리", true); }
            finally { settling--; }
            if (!userWantsOn || busy) return;
            Fail(RouteLoopWhy, true);
        }

        public const string RouteLoopWhy = "폰으로 가는 길이 TUN 으로 바뀌었습니다(Wi-Fi 가 끊겼을 수 있습니다)";

        /// <summary>
        /// Wi-Fi 끊김 알림(Native Wifi — 서비스가 UI 스레드로 넘긴다). 연결 중이고 그 알림이 연결한 핫스팟 어댑터의 것이면(모르면 어느 것이든)
        /// 곧바로 핫스팟을 보고, 그 뒤 FastWindowMs 동안 FastCheckMs 마다 다시 본다 — 알림은 주소가 빠지기 전에 온다.
        /// </summary>
        public void WlanGoingDown(Guid adapter, string what)
        {
            if (child == null || stopping) return;
            Guid mine;
            if (linkAdapterId.Length > 0 && Guid.TryParse(linkAdapterId, out mine) && mine != adapter) return;
            CheckLinkSoon(what, true);
        }

        /// <summary>링크 알림으로 곧바로 핫스팟을 본다(1초 Tick 보다 빨리). 사라졌으면 HotspotLost — TUN 자신의 알림이면 핫스팟이 그대로라 아무것도 않는다.</summary>
        void CheckLinkSoon(string why, bool fast)
        {
            if (fast) fastUntil = DateTime.Now.AddMilliseconds(FastWindowMs);
            linkWhy = why;
            if (linkChecking) { linkAgain = true; return; }
            LinkCheckLoop();
        }

        async void LinkCheckLoop()
        {
            linkChecking = true;
            try
            {
                while (true)
                {
                    linkAgain = false;
                    var c = child;
                    if (c == null || stopping || busy || settling > 0) return;
                    if (State != TetherState.Connected && State != TetherState.Connecting) return;
                    var prefix = Settings.SsidPrefix;
                    var finder = HotspotFinder;
                    HotspotInfo hs;
                    try { hs = await Task.Run(() => finder(prefix)); }
                    catch (Exception ex) { log.Write("점검", "살피지 못함: " + ex.Message); return; }
                    if (c != child || stopping || busy || settling > 0) return;
                    var lost = LostWhy(hs, prefix);
                    if (lost != null)
                    {
                        log.Write("Wi-Fi", "알림(" + linkWhy + ")으로 곧바로 확인함 — " + lost);
                        HotspotLost(lost);
                        return;
                    }
                    if (linkAgain) continue;
                    if (DateTime.Now >= fastUntil) return;
                    await Task.Delay(FastCheckMs);
                }
            }
            finally { linkChecking = false; }
        }

        async void HotspotLost(string why)
        {
            if (child == null || stopping) return; // 다른 길(Tick·링크 알림·경로 고리)이 이미 끊는 중 — 상태는 그쪽이 정한다
            TellDropped(why + ".");
            settling++;
            try { await StopChildAsync(why == "폰 핫스팟이 사라졌습니다" ? "핫스팟이 사라짐" : why); }
            finally { settling--; }
            // 그사이 사용자가 끊었다 — '꺼짐' 그대로(오류·대기로 덮지 않는다). 새 시도가 돌면 그것이 상태를 정한다.
            if (!userWantsOn || busy) return;
            retries = 0;
            Fail(why, true);
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
