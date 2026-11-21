// 트레이 상주 — 아이콘·메뉴·풍선 알림, 창들을 하나씩만.
using System;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether
{
    public sealed class TrayApp : ApplicationContext
    {
        readonly Controller ctl;
        readonly Log log;
        readonly AppPaths paths;
        readonly ISystemOps ops;
        readonly NotifyIcon tray = new NotifyIcon();
        readonly ToolStripMenuItem miToggle;
        readonly ToolStripMenuItem miAdvisory, miNewVersion, miSkip;
        readonly ToolStripSeparator sepUpdate;
        string balloonUrl;
        readonly System.Windows.Forms.Timer tick = new System.Windows.Forms.Timer { Interval = 1000 };
        readonly Control invoker = new Control();
        StatusForm status;
        SettingsForm settings;
        LogForm logForm;
        AboutForm about;
        TetherState shownState = (TetherState)(-1);
        string shownTip;
        bool exiting;
        /// <summary>'시작 시 실행' 사본이 옛 판인지 뜰 때 한 번 본다(StartupCopy).</summary>
        readonly StartupCopyFlow copyFlow;

        public TrayApp(Controller ctl, Log log, AppPaths paths, ISystemOps ops, EventWaitHandle showSignal)
        {
            this.ctl = ctl;
            this.log = log;
            this.paths = paths;
            this.ops = ops;
            invoker.CreateControl();

            var menu = new ContextMenuStrip { Font = Ui.Font };
            miAdvisory = new ToolStripMenuItem("", null, (s, e) => OpenAdvisory()) { Font = Ui.Bold, ForeColor = Color.Firebrick, Visible = false };
            miNewVersion = new ToolStripMenuItem("", null, (s, e) => Browser.Open(ctl.Updates.LatestUrl, log)) { Visible = false };
            miSkip = new ToolStripMenuItem("이 판은 알리지 않기", null, (s, e) => { var v = ctl.NewVersion; if (v != null) ctl.SkipVersion(v); }) { Visible = false };
            sepUpdate = new ToolStripSeparator { Visible = false };
            menu.Items.Add(miAdvisory);
            menu.Items.Add(miNewVersion);
            menu.Items.Add(miSkip);
            menu.Items.Add(sepUpdate);
            miToggle = new ToolStripMenuItem("연결", null, (s, e) => Toggle());
            miToggle.Font = Ui.Bold;
            menu.Items.Add(miToggle);
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("상태", null, (s, e) => ShowStatus());
            menu.Items.Add("설정", null, (s, e) => ShowForm(ref settings, () => new SettingsForm(ctl, ops, Application.ExecutablePath)));
            menu.Items.Add("로그", null, (s, e) => ShowForm(ref logForm, () => new LogForm(log, paths.LogFile)));
            menu.Items.Add("정보", null, (s, e) => ShowForm(ref about, () => new AboutForm(ctl.DryRun)));
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("끝내기", null, (s, e) => Exit());
            menu.Opening += (s, e) => UpdateToggle(); // 연 순간의 상태로(걷기가 끝난 것을 놓쳤어도)
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += (s, e) => ShowStatus();
            tray.MouseClick += (s, e) => { if (e.Button == MouseButtons.Left) ShowStatus(); };

            ctl.Changed += UpdateTray;
            ctl.AskConnect = AskConnect;
            ctl.ReshowAsk = () => TopAsk.BringToFront();
            ctl.Notice += (title, text, warn) =>
            {
                balloonUrl = ctl.NoticeUrl;
                tray.ShowBalloonTip(warn ? 10000 : 5000, title, text, warn ? ToolTipIcon.Warning : ToolTipIcon.Info);
            };
            tray.BalloonTipClicked += (s, e) => { if (balloonUrl != null) Browser.Open(balloonUrl, log); };
            tray.BalloonTipClosed += (s, e) => balloonUrl = null;
            tick.Tick += (s, e) => ctl.Tick();
            tick.Start();
            UpdateTray();
            tray.Visible = true;

            if (showSignal != null)
            {
                var t = new Thread(() =>
                {
                    while (true)
                    {
                        showSignal.WaitOne();
                        if (exiting) return;
                        try { invoker.BeginInvoke(new Action(ShowStatus)); } catch (InvalidOperationException) { return; }
                    }
                }) { IsBackground = true, Name = "show-signal" };
                t.Start();
            }

            var copy = ops as IStartupCopy;
            if (copy != null)
            {
                copyFlow = new StartupCopyFlow(copy, m => log.Write("시작 시 실행", m))
                {
                    // 묻는 사이 '끝내기' 를 눌렀으면 '예' 여도 바꾸지 않는다(끝내기가 기다리지 않은 복사가 임시 파일을 남기지 않게).
                    Ask = text => TopAsk.Ask(text, StartupCopy.Title, true) && !exiting,
                    Tell = text => TopAsk.Tell(text, StartupCopy.Title, true),
                    Notify = (text, warn) =>
                    {
                        balloonUrl = null;
                        tray.ShowBalloonTip(warn ? 10000 : 5000, StartupCopy.Title, text, warn ? ToolTipIcon.Warning : ToolTipIcon.Info);
                    },
                    Busy = () => TopAsk.IsOpen,
                    Stopping = () => exiting,
                };
                // 메시지 고리가 돈 뒤에(트레이 아이콘이 선 뒤) — 견주기·바꾸기는 일꾼 스레드, 묻기만 이 스레드.
                invoker.BeginInvoke(new Action(() =>
                {
                    if (exiting) return;
                    copyFlow.RunAsync().ContinueWith(x => log.Write("시작 시 실행", "사본 견주기 오류: " + x.Exception.GetBaseException().Message),
                                                     TaskContinuationOptions.OnlyOnFaulted);
                }));
            }
        }

        /// <summary>
        /// 손으로 누른 '연결' 이 접두어 밖(또는 이름을 모르는) Wi-Fi 를 만났을 때 — 사용자의 뜻이니 한 번 묻는다. 기본 단추는 '아니요'.
        /// '예' 면 그 이름은 이 실행 동안 다시 연결도 저절로 한다(Controller.approvedSsid). 창은 맨 앞에(TopAsk).
        /// </summary>
        static bool AskConnect(HotspotInfo hs, string prefix)
        {
            var text = hs.Ssid != null
                ? "Wi-Fi " + StatusText.Ssid(hs.Ssid) + " 에 연결할까요?" + Environment.NewLine +
                  "이름이 " + prefix + " 로 시작하지 않습니다 — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다."
                : "이름을 확인하지 못한 Wi-Fi(" + hs.AdapterName + ") 에 연결할까요?" + Environment.NewLine +
                  "까닭: " + hs.SsidError + " — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다.";
            return TopAsk.Ask(text);
        }

        void OpenAdvisory()
        {
            var a = ctl.Updates.Advisories;
            Browser.Open(a.Count > 0 && a[0].Url.Length > 0 ? a[0].Url : "https://github.com/tun2proxy/tun2proxy/security/advisories", log);
        }

        void Toggle()
        {
            StatusForm.DoToggle(ctl);
        }

        void UpdateToggle()
        {
            var a = ctl.Toggle;
            miToggle.Text = StatusText.ToggleLabel(a);
            miToggle.Enabled = a != ToggleAction.Cleaning;
        }

        void UpdateTray()
        {
            var st = ctl.State;
            if (st != shownState)
            {
                tray.Icon = Icons.For(st, SystemInformation.SmallIconSize.Width);
                shownState = st;
            }
            var tip = StatusText.Tooltip(st, ctl.Detail);
            if (tip != shownTip) { tray.Text = tip; shownTip = tip; }
            var up = ctl.Updates;
            miAdvisory.Visible = up.Advisories.Count > 0;
            miAdvisory.Text = "⚠ 보안 권고 " + up.Advisories.Count + "건 — tun2proxy " + Embedded.Tun2proxyVersion + " · 자세히";
            var nv = ctl.NewVersion;
            miNewVersion.Visible = nv != null;
            miNewVersion.Text = "새 판 " + nv + " — 변경 내역 보기";
            miSkip.Visible = nv != null;
            sepUpdate.Visible = miAdvisory.Visible || miNewVersion.Visible;
            UpdateToggle();
            if (status != null && status.Visible) status.RefreshView();
        }

        public void ShowStatus()
        {
            ShowForm(ref status, () => new StatusForm(ctl));
        }

        static void ShowForm<T>(ref T form, Func<T> make) where T : Form
        {
            if (form == null || form.IsDisposed) form = make();
            if (!form.Visible) form.Show();
            if (form.WindowState == FormWindowState.Minimized) form.WindowState = FormWindowState.Normal;
            form.Activate();
            form.BringToFront();
        }

        public void ExitAfter(int seconds)
        {
            var t = new System.Windows.Forms.Timer { Interval = seconds * 1000 };
            t.Tick += (s, e) => { t.Stop(); Exit(); };
            t.Start();
        }

        async void Exit()
        {
            if (exiting) return;
            exiting = true;
            tick.Stop();
            tray.Text = "Web 테더 — 끝내는 중";
            // 설정 저장(파일·작업 스케줄러)이 도는 중이면 끝나기를 기다린다 — 쓰다 말고 끝나지 않게.
            if (settings != null)
            {
                try { await settings.PendingSave; } catch (Exception ex) { log.Write("끝", "설정 저장을 기다리지 못함: " + ex.Message); }
            }
            // 사본을 바꾸는 중이면 끝나기를 기다린다 — Program Files 에 임시 파일을 남기지 않게.
            if (copyFlow != null)
            {
                try { await copyFlow.Pending; } catch (Exception ex) { log.Write("끝", "사본 바꾸기를 기다리지 못함: " + ex.Message); }
            }
            await ctl.ShutdownAsync();
            log.Write("끝", "도우미를 끝냅니다");
            tray.Visible = false;
            tray.Dispose();
            ExitThread();
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing) { tray.Visible = false; tray.Dispose(); tick.Dispose(); }
            base.Dispose(disposing);
        }
    }

    /// <summary>
    /// '연결할까요?' 창 — 다른 창 뒤에 숨지 않게 MB_TOPMOST(늘 위 무리 — 전면 권한이 없어도 보통 창들 위에 그려진다)와 MB_SETFOREGROUND
    /// (앞 창이 되어 본다 — 시스템이 허락하지 않으면 초점은 그대로이고 작업 표시줄 단추가 깜박인다). 주인 창이 없어 작업 표시줄에 단추가 있다.
    /// WinForms 의 MessageBox 는 MB_TOPMOST 를 받지 않아(MessageBoxOptions 밖이면 던진다) user32 를 바로 부른다. UI 스레드에서만 부른다.
    /// 한계: 다른 '늘 위' 창(작업 관리자 등)과는 그 무리 안의 차례를 따르고, 전체 화면 독점 게임·보안 데스크톱(UAC) 위에는 뜨지 않는다.
    /// </summary>
    public static class TopAsk
    {
        public const string Title = AppInfo.Name + " — Wi-Fi 확인";
        const uint MB_YESNO = 0x4, MB_ICONWARNING = 0x30, MB_DEFBUTTON2 = 0x100, MB_SETFOREGROUND = 0x10000, MB_TOPMOST = 0x40000;
        public const uint Flags = MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2 | MB_SETFOREGROUND | MB_TOPMOST;
        const int IDYES = 6, IDNO = 7;
        const int GWL_EXSTYLE = -20, WS_EX_TOPMOST = 0x8;
        const uint SWP_NOSIZE = 0x1, SWP_NOMOVE = 0x2, SWP_NOACTIVATE = 0x10, SWP_SHOWWINDOW = 0x40;
        static readonly IntPtr HWND_TOPMOST = new IntPtr(-1);

        delegate bool EnumProc(IntPtr hwnd, IntPtr lParam);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);
        [DllImport("user32.dll")]
        static extern bool EnumThreadWindows(uint threadId, EnumProc fn, IntPtr lParam);
        [DllImport("kernel32.dll")]
        static extern uint GetCurrentThreadId();
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        static extern int GetClassNameW(IntPtr hWnd, StringBuilder name, int max);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        static extern int GetWindowTextW(IntPtr hWnd, StringBuilder text, int max);
        [DllImport("user32.dll")]
        static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll")]
        static extern int GetWindowLongW(IntPtr hWnd, int index);
        [DllImport("user32.dll")]
        static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
        [DllImport("user32.dll")]
        static extern bool SetForegroundWindow(IntPtr hWnd);

        /// <summary>알리기만 하는 창 — 확인 단추 하나, 같은 '맨 앞'.</summary>
        public const uint TellFlags = MB_ICONINFORMATION | MB_SETFOREGROUND | MB_TOPMOST;
        const uint MB_ICONINFORMATION = 0x40;
        const int IDOK = 1;

        /// <summary>떠 있는 창의 스레드(없으면 0).</summary>
        static uint thread;
        /// <summary>떠 있는(마지막으로 띄운) 창의 제목 — Find 가 찾는다.</summary>
        static string shownTitle = Title;

        public static bool IsOpen { get { return thread != 0; } }

        /// <summary>묻는다 — '예' 면 참. 기본 단추는 '아니요'.</summary>
        public static bool Ask(string text)
        {
            return Ask(text, Title, true);
        }

        /// <summary>foreground: 앞 창이 되어 보는가(시험은 사용자의 초점을 빼앗지 않게 끈다 — 맨 앞에 그리는 것은 같다).</summary>
        public static bool Ask(string text, bool foreground)
        {
            return Ask(text, Title, foreground);
        }

        /// <summary>제목을 골라 묻는다('시작 시 실행' 사본 물음 — StartupCopy.Title). 다른 창이 떠 있으면 묻지 않고 거짓.</summary>
        public static bool Ask(string text, string title, bool foreground)
        {
            return Show(text, title, foreground ? Flags : Flags & ~MB_SETFOREGROUND) == IDYES;
        }

        /// <summary>알리기만 한다(확인 단추 하나). 다른 창이 떠 있으면 띄우지 않는다.</summary>
        public static void Tell(string text, string title, bool foreground)
        {
            Show(text, title, foreground ? TellFlags : TellFlags & ~MB_SETFOREGROUND);
        }

        static int Show(string text, string title, uint flags)
        {
            if (thread != 0) return 0; // 겹쳐 묻지 않는다(시도는 하나씩이라 오지 않는다)
            thread = GetCurrentThreadId();
            shownTitle = title;
            try { return MessageBoxW(IntPtr.Zero, text, title, flags); }
            finally { thread = 0; }
        }

        /// <summary>떠 있는 묻는 창(없으면 Zero) — 그 스레드의 보이는 대화 상자(#32770) 가운데 제목이 띄운 제목인 것.</summary>
        public static IntPtr Find()
        {
            var t = thread;
            if (t == 0) return IntPtr.Zero;
            var want = shownTitle;
            var found = IntPtr.Zero;
            EnumThreadWindows(t, (h, l) =>
            {
                if (!IsWindowVisible(h)) return true;
                var cls = new StringBuilder(64);
                GetClassNameW(h, cls, cls.Capacity);
                if (cls.ToString() != "#32770") return true;
                var title = new StringBuilder(256);
                GetWindowTextW(h, title, title.Capacity);
                if (title.ToString() != want) return true;
                found = h;
                return false;
            }, IntPtr.Zero);
            return found;
        }

        /// <summary>떠 있으면 다시 맨 앞으로(늘 위 무리의 맨 위)·앞 창이 되어 본다. 없으면 거짓.</summary>
        public static bool BringToFront()
        {
            return BringToFront(true);
        }

        public static bool BringToFront(bool foreground)
        {
            var h = Find();
            if (h == IntPtr.Zero) return false;
            SetWindowPos(h, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW | (foreground ? 0 : SWP_NOACTIVATE));
            if (foreground) SetForegroundWindow(h); // 누른 직후라 대개 허락된다 — 아니면 작업 표시줄 단추가 깜박인다
            return true;
        }

        public static bool IsTopmost(IntPtr h)
        {
            return (GetWindowLongW(h, GWL_EXSTYLE) & WS_EX_TOPMOST) != 0;
        }

#if TEST_BUILD
        const uint WM_COMMAND = 0x111;
        [DllImport("user32.dll")]
        static extern bool PostMessageW(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

        /// <summary>시험 판만 — 떠 있는 창에 단추를 누른 것처럼 답한다.</summary>
        public static void AnswerForTest(IntPtr h, bool yes)
        {
            PostMessageW(h, WM_COMMAND, new IntPtr(yes ? IDYES : IDNO), IntPtr.Zero);
        }

        /// <summary>시험 판만 — 알리는 창의 '확인'(그 단추의 번호가 무엇이든 — Esc 와 같은 IDCANCEL 도 함께).</summary>
        public static void CloseForTest(IntPtr h)
        {
            PostMessageW(h, WM_COMMAND, new IntPtr(IDOK), IntPtr.Zero);
            PostMessageW(h, WM_COMMAND, new IntPtr(2), IntPtr.Zero);
        }
#endif
    }
}
