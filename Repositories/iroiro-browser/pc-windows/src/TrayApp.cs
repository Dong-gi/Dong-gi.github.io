// 트레이 상주 — 아이콘·메뉴·풍선 알림, 창들을 하나씩만.
using System;
using System.Drawing;
using System.Threading;
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
        }

        /// <summary>
        /// 손으로 누른 '연결' 이 접두어 밖(또는 이름을 모르는) Wi-Fi 를 만났을 때 — 사용자의 뜻이니 한 번 묻는다. 기본 단추는 '아니요'.
        /// '예' 면 그 이름은 이 실행 동안 다시 연결도 저절로 한다(Controller.approvedSsid).
        /// </summary>
        static bool AskConnect(HotspotInfo hs, string prefix)
        {
            var text = hs.Ssid != null
                ? "Wi-Fi " + StatusText.Ssid(hs.Ssid) + " 에 연결할까요?" + Environment.NewLine +
                  "이름이 " + prefix + " 로 시작하지 않습니다 — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다."
                : "이름을 확인하지 못한 Wi-Fi(" + hs.AdapterName + ") 에 연결할까요?" + Environment.NewLine +
                  "까닭: " + hs.SsidError + " — 이 PC 의 모든 트래픽이 그 망의 기기로 갑니다.";
            return MessageBox.Show(text, AppInfo.Name, MessageBoxButtons.YesNo, MessageBoxIcon.Warning, MessageBoxDefaultButton.Button2) == DialogResult.Yes;
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
}
