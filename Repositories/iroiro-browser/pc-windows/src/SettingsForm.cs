// 설정 창 — 칸마다 '기본값'. 저장할 때 모두 검사하고 첫 문제의 칸으로 간다.
// 저장의 막는 일(시험 판은 설정 파일 쓰기, 실제 판은 서비스에 제안하고 확인 창의 답 기다리기)은 일꾼 스레드에서 — 그동안 창은 눌리지 않는다.
// 실제 판은 트레이가 연다(ServiceSettingsHost) — 적용은 서비스가 콘솔 사용자에게 확인 창으로 물은 뒤다.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether
{
    public sealed class SettingsForm : Form
    {
        readonly ISettingsHost ctl;
        readonly Dictionary<string, Control> fields = new Dictionary<string, Control>();
        readonly TableLayoutPanel grid = new TableLayoutPanel();
        readonly Label message = new Label();

        TextBox tHost, tDns, tTun, tBypass, tWifi, tSsid;
        NumericUpDown nPort, nInterval, nMax;
        ComboBox cKind, cDnsMode, cVerb;
        CheckBox kSetup, kReconnect, kUpdate;
        ComboBox cUpdate;
        static readonly string[] IntervalLabels = { "매일", "매주", "매월" };
        Button bSave, bCancel;
        bool saving;
        Task pending = Task.FromResult(0);

        /// <summary>도는 저장(없으면 끝난 일) — 끝내기가 기다린다(설정 파일을 쓰다 말고 끝나지 않게).</summary>
        public Task PendingSave { get { return pending; } }
        public bool Saving { get { return saving; } }

        public SettingsForm(ISettingsHost ctl)
        {
            this.ctl = ctl;
            Ui.Prepare(this, AppInfo.Name + " — 설정");
            FormBorderStyle = FormBorderStyle.FixedDialog;
            AutoSize = true;
            AutoSizeMode = AutoSizeMode.GrowAndShrink;

            grid.ColumnCount = 3;
            grid.AutoSize = true;
            grid.Dock = DockStyle.Fill;
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 250));
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));

            var d = new Settings();
            Section("폰 프록시");
            tHost = AddText("proxy_host", "주소", () => tHost.Text = Settings.DefaultHost);
            cKind = AddCombo("proxy_type", "종류", new[] { "socks5", "http" }, () => cKind.SelectedIndex = 0);
            nPort = AddNumber("proxy_port", "포트", 1, 65535, 0, () => nPort.Value = Settings.DefaultPortFor(KindOf()));
            cKind.SelectedIndexChanged += (s, e) =>
            {
                // 다른 종류의 기본 포트를 쓰고 있었으면 이 종류의 기본 포트로.
                var other = KindOf() == ProxyKind.Http ? Settings.DefaultSocksPort : Settings.DefaultHttpPort;
                if ((int)nPort.Value == other) nPort.Value = Settings.DefaultPortFor(KindOf());
                Hint();
            };

            Section("tun2proxy");
            tDns = AddText("dns_addr", "DNS 서버", () => tDns.Text = Settings.DefaultDnsAddr);
            cDnsMode = AddCombo("dns_mode", "DNS 방식", Settings.DnsModes, () => cDnsMode.SelectedItem = Settings.DefaultDnsMode);
            cDnsMode.SelectedIndexChanged += (s, e) => Hint();
            tTun = AddText("tun_name", "TUN 이름", () => tTun.Text = Settings.DefaultTunName);
            kSetup = AddCheck("setup", "경로·DNS 자동 설정(--setup)", () => kSetup.Checked = true);
            tBypass = AddText("bypass", "우회 목록", () => tBypass.Text = "");
            cVerb = AddCombo("verbosity", "로그 수준", Settings.Verbosities, () => cVerb.SelectedItem = Settings.DefaultVerbosity);
            cVerb.SelectedIndexChanged += (s, e) => Hint();

            Section("다시 연결");
            tSsid = AddText("ssid_prefix", "자동 연결 Wi-Fi", () => tSsid.Text = Settings.DefaultSsidPrefix);
            kReconnect = AddCheck("reconnect", "끊기면 다시 연결", () => kReconnect.Checked = true);
            nInterval = AddNumber("reconnect_interval", "간격(초)", 3, 600, 0, () => nInterval.Value = Settings.DefaultReconnectInterval);
            nMax = AddNumber("reconnect_max", "횟수 상한", 1, 1000, 0, () => nMax.Value = Settings.DefaultReconnectMax);
            tWifi = AddText("wifi_profile", "Wi-Fi 프로필", () => tWifi.Text = "");

            Section("그 밖");
            kUpdate = AddCheck("update_check", "tun2proxy 업데이트 확인(알리기만)", () => kUpdate.Checked = true);
            cUpdate = AddCombo("update_interval", "확인 간격", IntervalLabels, () => cUpdate.SelectedIndex = (int)UpdateInterval.Weekly);

            message.AutoSize = true;
            message.MaximumSize = new Size(520, 0);
            message.Margin = new Padding(3, 8, 3, 0);
            grid.Controls.Add(message);
            grid.SetColumnSpan(message, 3);

            bSave = Ui.Button("저장", async (s, e) =>
            {
                if (await SaveAsync() && !IsDisposed && !ctl.ChildRunning) Hide();
            });
            bCancel = Ui.Button("취소", (s, e) => { if (!saving) Hide(); });
            AcceptButton = bSave;
            CancelButton = bCancel;
            var buttons = Ui.ButtonRow(bCancel, bSave);
            grid.Controls.Add(buttons);
            grid.SetColumnSpan(buttons, 3);
            Controls.Add(grid);

            var tip = new ToolTip();
            tip.SetToolTip(cVerb, "tun2proxy 의 기록 수준. info 밑은 info 로 씁니다(경로 고리 감지).");
            tip.SetToolTip(tBypass, "터널 밖으로 보낼 IPv4·CIDR. 쉼표로 가릅니다. 예: 10.0.0.0/8");
            tip.SetToolTip(tWifi, "핫스팟이 없을 때 붙을 Wi-Fi 프로필. 로그온 전에도 붙으려면 '모든 사용자' 프로필이어야 합니다.");
            tip.SetToolTip(tSsid, "이 이름으로 시작하는 Wi-Fi 에서만 저절로 연결합니다. 다른 Wi-Fi 는 '연결' 을 누르면 묻습니다.");
            FormClosing += (s, e) =>
            {
                if (e.CloseReason != CloseReason.UserClosing) return;
                e.Cancel = true;
                if (!saving) Hide(); // 저장 중에는 닫지 않는다 — 끝나면 결과를 보인다
            };
            VisibleChanged += (s, e) =>
            {
                if (!Visible) return;
                LoadFrom(ctl.Settings);
            };
            LoadFrom(ctl.Settings);
        }

        ProxyKind KindOf() { return cKind.SelectedIndex == 1 ? ProxyKind.Http : ProxyKind.Socks5; }

        void Section(string title)
        {
            var l = Ui.Text(title);
            l.Font = Ui.Bold;
            l.Margin = new Padding(3, grid.Controls.Count == 0 ? 0 : 10, 3, 2);
            grid.Controls.Add(l);
            grid.SetColumnSpan(l, 3);
        }

        void Row(string key, string label, Control c, Action reset)
        {
            var l = Ui.Text(label);
            c.Margin = new Padding(3, 3, 3, 3);
            c.Dock = DockStyle.Fill;
            grid.Controls.Add(l);
            grid.Controls.Add(c);
            var b = new Button { Text = "기본값", AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, Margin = new Padding(3, 2, 3, 2) };
            b.Click += (s, e) => { reset(); message.Text = ""; };
            grid.Controls.Add(b);
            fields[key] = c;
        }

        TextBox AddText(string key, string label, Action reset)
        {
            var t = new TextBox();
            Row(key, label, t, reset);
            return t;
        }

        NumericUpDown AddNumber(string key, string label, int min, int max, int decimals, Action reset)
        {
            var n = new NumericUpDown { Minimum = min, Maximum = max, DecimalPlaces = decimals, Increment = decimals > 0 ? 0.5m : 1 };
            Row(key, label, n, reset);
            return n;
        }

        ComboBox AddCombo(string key, string label, string[] items, Action reset)
        {
            var c = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList };
            c.Items.AddRange(items);
            Row(key, label, c, reset);
            return c;
        }

        CheckBox AddCheck(string key, string label, Action reset)
        {
            var c = new CheckBox { Text = label, AutoSize = true };
            Row(key, "", c, reset);
            return c;
        }

        static decimal Clamp(NumericUpDown n, double v)
        {
            var d = (decimal)v;
            return d < n.Minimum ? n.Minimum : (d > n.Maximum ? n.Maximum : d);
        }

        public void LoadFrom(Settings s)
        {
            tHost.Text = s.ProxyHost;
            cKind.SelectedIndex = s.Kind == ProxyKind.Http ? 1 : 0;
            nPort.Value = Clamp(nPort, s.ProxyPort);
            tDns.Text = s.DnsAddr;
            cDnsMode.SelectedItem = s.DnsMode;
            tTun.Text = s.TunName;
            kSetup.Checked = s.Setup;
            tBypass.Text = s.Bypass;
            cVerb.SelectedItem = s.Verbosity;
            kReconnect.Checked = s.Reconnect;
            nInterval.Value = Clamp(nInterval, s.ReconnectInterval);
            nMax.Value = Clamp(nMax, s.ReconnectMax);
            tWifi.Text = s.WifiProfile;
            tSsid.Text = s.SsidPrefix;
            kUpdate.Checked = s.UpdateCheck;
            cUpdate.SelectedIndex = (int)s.UpdateEvery;
            message.Text = "";
            Hint();
            if ((message.Text.Length == 0 || message.Text == VerbosityNoteText) && !string.IsNullOrEmpty(ctl.LoadNotice))
            {
                message.ForeColor = Color.FromArgb(0xB2, 0x6A, 0x00);
                message.Text = ctl.LoadNotice;
            }
        }

        public Settings Collect()
        {
            return new Settings
            {
                ProxyHost = tHost.Text.Trim(),
                Kind = KindOf(),
                ProxyPort = (int)nPort.Value,
                DnsAddr = tDns.Text.Trim(),
                DnsMode = (string)cDnsMode.SelectedItem ?? "",
                TunName = tTun.Text.Trim(),
                Setup = kSetup.Checked,
                Bypass = tBypass.Text.Trim(),
                Verbosity = (string)cVerb.SelectedItem ?? "",
                Reconnect = kReconnect.Checked,
                ReconnectInterval = (int)nInterval.Value,
                ReconnectMax = (int)nMax.Value,
                WifiProfile = tWifi.Text,
                SsidPrefix = tSsid.Text,
                UpdateCheck = kUpdate.Checked,
                UpdateEvery = (UpdateInterval)Math.Max(0, cUpdate.SelectedIndex),
            };
        }

        void Hint()
        {
            if (KindOf() == ProxyKind.Http && (string)cDnsMode.SelectedItem == "direct")
            {
                message.ForeColor = Color.FromArgb(0xB2, 0x6A, 0x00);
                message.Text = "http 프록시는 UDP 를 넘기지 못합니다 — DNS 방식을 over-tcp 로 두십시오.";
            }
            else if (VerbosityNote((string)(cVerb == null ? null : cVerb.SelectedItem)) != null)
            {
                message.ForeColor = Color.FromArgb(0xB2, 0x6A, 0x00);
                message.Text = VerbosityNote((string)cVerb.SelectedItem);
            }
            else if (message.ForeColor != Color.Firebrick) message.Text = "";
        }

        /// <summary>
        /// 고른 로그 수준이 tun2proxy 에 그대로 가지 않으면(off·error·warn — 경로 고리 감지가 info 의 줄을 읽어 info 로 올린다) 그 한 줄.
        /// </summary>
        public static string VerbosityNote(string selected)
        {
            if (string.IsNullOrEmpty(selected) || CommandLine.EffectiveVerbosity(selected) == selected) return null;
            return VerbosityNoteText;
        }

        public const string VerbosityNoteText = "tun2proxy 기록은 경로 고리 감지 때문에 info 이상으로 남깁니다.";

        /// <summary>창 아래 알림 줄의 글(시험).</summary>
        public string MessageText { get { return message.Text; } }

        sealed class SaveOutcome
        {
            public bool Saved;
            public string Message;
        }

        /// <summary>
        /// 저장 — 성공하면 true(시험이 부른다). 검사는 이 자리에서, 쓰기는 일꾼 스레드에서. 그동안 칸·단추를 막고 '저장 중'.
        /// 돌아왔을 때 창이 없어졌어도 저장한 설정은 Controller 에 건다(파일과 메모리가 어긋나지 않게).
        /// </summary>
        public async Task<bool> SaveAsync()
        {
            if (saving) return false;
            var s = Collect();
            var errs = s.Validate();
            if (errs.Count > 0)
            {
                message.ForeColor = Color.Firebrick;
                message.Text = errs[0].Value;
                Control c;
                if (fields.TryGetValue(errs[0].Key, out c)) c.Focus();
                return false;
            }
            var old = ctl.Settings;
            saving = true;
            SetBusy(true);
            var work = Task.Run(() => SaveWork(s, old));
            pending = work;
            SaveOutcome r;
            try { r = await work; }
            finally { saving = false; }
            if (r.Saved) ctl.ApplySettings(s);
            if (IsDisposed) return r.Saved;
            SetBusy(false);
            if (r.Saved)
            {
                message.ForeColor = SystemColors.ControlText;
                message.Text = ctl.ChildRunning ? "저장했습니다. 다음 연결부터 적용됩니다." : "저장했습니다.";
            }
            else
            {
                message.ForeColor = Color.Firebrick;
                message.Text = r.Message;
            }
            return r.Saved;
        }

        void SetBusy(bool on)
        {
            foreach (Control c in grid.Controls) if (c != message) c.Enabled = !on;
            if (on)
            {
                message.ForeColor = SystemColors.ControlText;
                message.Text = ctl.SavingText;
            }
            UseWaitCursor = on;
        }

        /// <summary>일꾼 스레드 — 화면을 만지지 않는다. 던지지 않는다(무엇이든 실패로 돌려준다).</summary>
        SaveOutcome SaveWork(Settings s, Settings old)
        {
            try
            {
                ctl.SaveSettings(s);
                return new SaveOutcome { Saved = true };
            }
            catch (Exception ex)
            {
                return new SaveOutcome { Message = "저장하지 못했습니다: " + ex.Message };
            }
        }
    }
}
