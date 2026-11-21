// 상태·로그·정보 창. 글은 짧게(할 일·경고만).
using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether
{
    public static class AppInfo
    {
        public const string Name = "Web 테더";
        public const string Version = "1.0.0";
    }

    public static class Browser
    {
        /// <summary>
        /// github.com 의 https 주소만 연다. 도우미는 관리자 권한이라 그대로 열면 브라우저도 관리자로 뜬다 —
        /// explorer.exe 에 넘겨 이미 떠 있는 (보통 권한의) 셸이 기본 브라우저로 열게 한다.
        /// </summary>
        public static void Open(string url, Log log)
        {
            if (!SafeText.IsGithubUrl(url)) { if (log != null) log.Write("열기", "github.com 의 https 주소가 아니라 열지 않음"); return; }
            try
            {
                var explorer = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "explorer.exe");
                using (System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(explorer, CommandLine.Quote(url)) { UseShellExecute = false })) { }
            }
            catch (Exception ex)
            {
                if (log != null) log.Write("열기", "브라우저를 열지 못함: " + ex.Message);
            }
        }
    }

    public static class Ui
    {
        public static Font Font = new Font("Malgun Gothic", 9f);
        public static Font Bold = new Font("Malgun Gothic", 9f, FontStyle.Bold);
        public static Font Mono = new Font("Consolas", 9f);

        public static void Prepare(Form f, string title)
        {
            f.Text = title;
            f.Font = Font;
            f.AutoScaleMode = AutoScaleMode.Dpi;
            f.StartPosition = FormStartPosition.CenterScreen;
            f.ShowIcon = true;
            f.Icon = Icons.For(TetherState.Connected, 32);
            f.MinimizeBox = false;
            f.MaximizeBox = false;
            f.Padding = new Padding(12);
        }

        public static Label Text(string s)
        {
            return new Label { Text = s, AutoSize = true, Margin = new Padding(3, 5, 3, 5) };
        }

        public static Button Button(string s, EventHandler click)
        {
            var b = new Button { Text = s, AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Padding = new Padding(8, 2, 8, 2), Margin = new Padding(4) };
            if (click != null) b.Click += click;
            return b;
        }

        public static FlowLayoutPanel ButtonRow(params Control[] buttons)
        {
            var p = new FlowLayoutPanel { FlowDirection = FlowDirection.RightToLeft, Dock = DockStyle.Fill, AutoSize = true, WrapContents = false, Margin = new Padding(0, 8, 0, 0) };
            foreach (var b in buttons) p.Controls.Add(b);
            return p;
        }

        public static string Resource(string name)
        {
            using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
            {
                if (s == null) return "(없음)";
                using (var r = new StreamReader(s, Encoding.UTF8)) return r.ReadToEnd();
            }
        }
    }

    public sealed class StatusForm : Form
    {
        readonly Controller ctl;
        readonly Label vState = new Label(), vHotspot = new Label(), vProxy = new Label(), vChild = new Label(), vUpdate = new Label();
        readonly PictureBox icon = new PictureBox();
        readonly Button bToggle;
        readonly Timer timer = new Timer { Interval = 1000 };
        ProbeResult lastProbe;
        DateTime lastProbeAt = DateTime.MinValue;
        bool probing;
        /// <summary>시험 — 프록시 확인을 하지 않는다.</summary>
        public bool ProbeEnabled = true;

        public StatusForm(Controller ctl)
        {
            this.ctl = ctl;
            Ui.Prepare(this, AppInfo.Name + " — 상태");
            FormBorderStyle = FormBorderStyle.FixedDialog;
            AutoSize = true;
            AutoSizeMode = AutoSizeMode.GrowAndShrink;

            var grid = new TableLayoutPanel { ColumnCount = 2, AutoSize = true, Dock = DockStyle.Fill };
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 330));
            Action<string, Label> row = (name, v) =>
            {
                v.AutoSize = true;
                v.MaximumSize = new Size(330, 0);
                v.Margin = new Padding(3, 5, 3, 5);
                var l = Ui.Text(name);
                l.ForeColor = SystemColors.GrayText;
                grid.Controls.Add(l);
                grid.Controls.Add(v);
            };
            vState.Font = Ui.Bold;
            row("상태", vState);
            row("핫스팟", vHotspot);
            row("폰 프록시", vProxy);
            row("tun2proxy", vChild);
            row("업데이트", vUpdate);

            icon.Size = new Size(32, 32);
            icon.Margin = new Padding(0, 4, 12, 0);
            // 글과 같은 조건(Controller.Toggle) — 연결 중에도 끊고, 앞 연결을 걷는 동안('정리 중')은 누를 수 없다(트레이 메뉴와 같다).
            bToggle = Ui.Button("연결", (s, e) => DoToggle(ctl));
            var bClose = Ui.Button("닫기", (s, e) => Hide());

            var outer = new TableLayoutPanel { ColumnCount = 2, AutoSize = true, Dock = DockStyle.Fill };
            outer.Controls.Add(icon, 0, 0);
            outer.Controls.Add(grid, 1, 0);
            outer.Controls.Add(Ui.ButtonRow(bClose, bToggle), 0, 1);
            outer.SetColumnSpan(outer.GetControlFromPosition(0, 1), 2);
            Controls.Add(outer);
            CancelButton = bClose;

            timer.Tick += (s, e) => RefreshView();
            VisibleChanged += (s, e) => { if (Visible) { lastProbeAt = DateTime.MinValue; RefreshView(); } timer.Enabled = Visible; };
            FormClosing += (s, e) => { if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };
            RefreshView();
        }

        public void RefreshView()
        {
            var s = ctl.Settings;
            var st = ctl.State;
            icon.Image = Icons.Draw(st, 32);
            vState.Text = StatusText.Title(st) + (string.IsNullOrEmpty(ctl.Detail) ? "" : " — " + ctl.Detail);
            vState.ForeColor = Icons.ColorOf(st);

            var hs = ctl.HotspotFinder();
            vHotspot.Text = hs == null ? "연결 안 됨 — 폰의 DIRECT-UT-… 에 붙으십시오" : "연결됨 · " + hs.AdapterName + " (" + hs.Address + ")";

            var url = CommandLine.ProxyUrl(s);
            if (lastProbe == null) vProxy.Text = (probing ? "확인 중 · " : "확인 안 함 · ") + url;
            else vProxy.Text = (lastProbe.Ok ? "응답함 · " : "응답 없음(" + lastProbe.Detail + ") · ") + url;
            if (ProbeEnabled && Visible && !probing && (DateTime.Now - lastProbeAt).TotalSeconds >= 10)
            {
                probing = true;
                lastProbeAt = DateTime.Now;
                Task.Run(() => ctl.Prober(s.ProxyHost, s.ProxyPort, s.Kind, 3000)).ContinueWith(t =>
                {
                    probing = false;
                    lastProbe = t.Exception == null ? t.Result : new ProbeResult { Detail = "오류" };
                    if (IsHandleCreated && !IsDisposed) BeginInvoke(new Action(RefreshView));
                });
            }

            if (ctl.DryRun) vChild.Text = ctl.ChildRunning ? "시험 — 띄운 척(명령줄만 기록)" : "시험 — 띄우지 않음";
            else vChild.Text = ctl.ChildRunning ? "실행 중 (PID " + ctl.ChildPid + ")" : "꺼짐";

            vUpdate.Text = UpdateLine(ctl, s);
            vUpdate.ForeColor = ctl.Updates.Advisories.Count > 0 ? Color.Firebrick : SystemColors.ControlText;

            var a = ctl.Toggle;
            bToggle.Text = StatusText.ToggleLabel(a);
            bToggle.Enabled = a != ToggleAction.Cleaning;
        }

        /// <summary>단추·트레이 메뉴 — 누른 때의 상태로 다시 정한다(보인 글이 낡았어도 '정리 중' 이면 아무것도 하지 않는다).</summary>
        public static void DoToggle(Controller ctl)
        {
            switch (ctl.Toggle)
            {
                case ToggleAction.Disconnect: ctl.Disconnect(); break;
                case ToggleAction.Connect: ctl.Connect(); break;
            }
        }

        public static string UpdateLine(Controller ctl, Settings s)
        {
            var u = ctl.Updates;
            var head = "tun2proxy " + Embedded.Tun2proxyVersion;
            if (!s.UpdateCheck) return head + " · 확인 꺼짐";
            if (u.LastCheck == null) return head + " · 아직 확인 안 함(연결된 동안 확인)";
            var when = u.LastCheck.Value.ToString("yyyy-MM-dd");
            if (u.Advisories.Count > 0) return head + " · 보안 권고 " + u.Advisories.Count + "건 — 트레이 메뉴 맨 위";
            var nv = ctl.NewVersion;
            if (nv != null) return head + " · 새 판 " + nv + " (" + when + " 확인)";
            if (!u.LastOk) return head + " · 확인 실패(" + when + ")";
            return head + " · 최신 (" + when + " 확인)";
        }
    }

    public sealed class LogForm : Form
    {
        readonly Log log;
        readonly TextBox box = new TextBox();
        readonly StringBuilder pending = new StringBuilder();
        readonly Timer flush = new Timer { Interval = 300 };

        public LogForm(Log log, string filePath)
        {
            this.log = log;
            Ui.Prepare(this, AppInfo.Name + " — 로그");
            MaximizeBox = true;
            MinimizeBox = true;
            ClientSize = new Size(820, 460);
            box.Multiline = true;
            box.ReadOnly = true;
            box.ScrollBars = ScrollBars.Both;
            box.WordWrap = false;
            box.Font = Ui.Mono;
            box.Dock = DockStyle.Fill;
            box.BackColor = SystemColors.Window;

            var path = Ui.Text("파일: " + filePath);
            path.ForeColor = SystemColors.GrayText;
            path.AutoEllipsis = true;
            var bCopy = Ui.Button("복사", (s, e) => { if (box.TextLength > 0) Clipboard.SetText(box.Text); });
            var bClose = Ui.Button("닫기", (s, e) => Hide());
            CancelButton = bClose;

            var layout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 3 };
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.Controls.Add(box);
            layout.Controls.Add(path);
            layout.Controls.Add(Ui.ButtonRow(bClose, bCopy));
            Controls.Add(layout);

            box.Text = string.Join("\r\n", log.Snapshot()) + "\r\n";
            log.Line += OnLine;
            flush.Tick += (s, e) => Flush();
            flush.Start();
            FormClosing += (s, e) => { if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };
            Disposed += (s, e) => { log.Line -= OnLine; flush.Dispose(); };
            Shown += (s, e) => ScrollToEnd();
        }

        void OnLine(string line)
        {
            lock (pending) pending.Append(line).Append("\r\n");
        }

        void Flush()
        {
            string add;
            lock (pending)
            {
                if (pending.Length == 0) return;
                add = pending.ToString();
                pending.Clear();
            }
            if (box.TextLength > 2000000) box.Text = string.Join("\r\n", log.Snapshot()) + "\r\n";
            else box.AppendText(add);
            ScrollToEnd();
        }

        void ScrollToEnd()
        {
            box.SelectionStart = box.TextLength;
            box.ScrollToCaret();
        }
    }

    public sealed class AboutForm : Form
    {
        public AboutForm(bool dryRun)
        {
            Ui.Prepare(this, AppInfo.Name + " — 정보");
            FormBorderStyle = FormBorderStyle.Sizable;
            ClientSize = new Size(640, 480);

            var head = new Label
            {
                AutoSize = true,
                MaximumSize = new Size(600, 0),
                Text = AppInfo.Name + " " + AppInfo.Version + (dryRun ? " (시험 판 — tun2proxy 를 띄우지 않음)" : "") + "\r\n" +
                       "폰 Web 앱의 테더링에 이 PC 의 모든 트래픽을 보냅니다.\r\n\r\n" +
                       "tun2proxy " + Embedded.Tun2proxyVersion + " (MIT)  SHA-256 " + Embedded.Tun2proxySha256 + "\r\n" +
                       "wintun.dll (WireGuard LLC)  SHA-256 " + Embedded.WintunSha256,
            };
            var box = new TextBox
            {
                Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill, Font = Ui.Mono,
                BackColor = SystemColors.Window,
                Text = "=== tun2proxy — MIT License ===\r\n" + Crlf(Ui.Resource("WebTether.licenses.tun2proxy.txt")) +
                       "\r\n\r\n=== Wintun — Prebuilt Binaries License ===\r\n" + Crlf(Ui.Resource("WebTether.licenses.wintun.txt")),
            };
            var bClose = Ui.Button("닫기", (s, e) => Hide());
            CancelButton = bClose;
            var layout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 3 };
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.Controls.Add(head);
            layout.Controls.Add(box);
            layout.Controls.Add(Ui.ButtonRow(bClose));
            Controls.Add(layout);
            ActiveControl = bClose;
            Shown += (s, e) => box.Select(0, 0);
            FormClosing += (s, e) => { if (e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); } };
        }

        static string Crlf(string s) { return s.Replace("\r\n", "\n").Replace("\n", "\r\n"); }
    }
}
