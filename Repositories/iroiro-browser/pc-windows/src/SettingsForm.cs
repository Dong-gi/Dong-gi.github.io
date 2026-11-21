// 설정 창 — 칸마다 '기본값'. 저장할 때 모두 검사하고 첫 문제의 칸으로 간다.
// 저장의 막는 일(설정 파일 쓰기, '시작 시 실행' 의 복사·해시·schtasks)은 일꾼 스레드에서 — 그동안 창은 '저장 중' 으로 눌리지 않는다.
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
        readonly Controller ctl;
        readonly ISystemOps ops;
        readonly string exePath;
        readonly Dictionary<string, Control> fields = new Dictionary<string, Control>();
        readonly TableLayoutPanel grid = new TableLayoutPanel();
        readonly Label message = new Label();
        /// <summary>'시작 시 실행' 아래 한 줄 — 사본이 옛 판이면(StartupCopy).</summary>
        readonly Label copyNote = new Label();
        CopyCheck lastCopy;
        int copyGen;

        TextBox tHost, tDns, tTun, tBypass, tWifi, tSsid;
        NumericUpDown nPort, nInterval, nMax;
        ComboBox cKind, cDnsMode, cVerb;
        CheckBox kSetup, kReconnect, kAuto, kUpdate;
        ComboBox cUpdate;
        static readonly string[] IntervalLabels = { "매일", "매주", "매월" };
        Button bSave, bCancel;
        bool saving;
        Task pending = Task.FromResult(0);

        /// <summary>도는 저장(없으면 끝난 일) — 끝내기가 기다린다(설정 파일·작업 스케줄러를 쓰다 말고 끝나지 않게).</summary>
        public Task PendingSave { get { return pending; } }
        public bool Saving { get { return saving; } }

        public SettingsForm(Controller ctl, ISystemOps ops, string exePath)
        {
            this.ctl = ctl;
            this.ops = ops;
            this.exePath = exePath;
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

            Section("다시 연결");
            tSsid = AddText("ssid_prefix", "자동 연결 Wi-Fi", () => tSsid.Text = Settings.DefaultSsidPrefix);
            kReconnect = AddCheck("reconnect", "끊기면 다시 연결", () => kReconnect.Checked = true);
            nInterval = AddNumber("reconnect_interval", "간격(초)", 3, 600, 0, () => nInterval.Value = Settings.DefaultReconnectInterval);
            nMax = AddNumber("reconnect_max", "횟수 상한", 1, 1000, 0, () => nMax.Value = Settings.DefaultReconnectMax);
            tWifi = AddText("wifi_profile", "Wi-Fi 프로필", () => tWifi.Text = "");

            Section("그 밖");
            // 칸과 그 아래 한 줄을 한 칸(가운데 열)에 — 표에 줄을 따로 두면 숨겼다 보일 때 칸 배치가 어긋났다.
            kAuto = new CheckBox { Text = "Windows 시작 시 실행", AutoSize = true, Margin = Padding.Empty };
            copyNote.AutoSize = true;
            copyNote.MaximumSize = new Size(244, 0);
            copyNote.ForeColor = Color.FromArgb(0xB2, 0x6A, 0x00);
            copyNote.Margin = new Padding(0, 2, 0, 0);
            copyNote.Visible = false;
            var autoBox = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink };
            autoBox.Controls.Add(kAuto);
            autoBox.Controls.Add(copyNote);
            Row("autostart", "", autoBox, () => kAuto.Checked = false);
            kAuto.CheckedChanged += (s, e) => ShowCopyNote();
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
            tip.SetToolTip(tBypass, "터널 밖으로 보낼 IPv4·CIDR. 쉼표로 가릅니다. 예: 10.0.0.0/8");
            tip.SetToolTip(tWifi, "핫스팟이 없을 때 netsh wlan connect 로 붙을 프로필 이름. 비우면 하지 않습니다.");
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
                var unused = RefreshCopyNoteAsync();
            };
            LoadFrom(ctl.Settings);
        }

        /// <summary>사본을 견주어(일꾼 스레드) 한 줄을 고친다. 늦게 온 옛 결과·창이 없어진 뒤의 결과는 버린다.</summary>
        public async Task RefreshCopyNoteAsync()
        {
            var copy = ops as IStartupCopy;
            if (copy == null) return;
            int gen = ++copyGen;
            CopyCheck c;
            try { c = await Task.Run(() => copy.CheckStartupCopy()); }
            catch (Exception) { return; }
            if (IsDisposed || gen != copyGen) return;
            lastCopy = c;
            ShowCopyNote();
        }

        /// <summary>시험이 본다 — 보이는 한 줄(없으면 null).</summary>
        public string CopyNoteText { get { return copyNoteShown ? copyNote.Text : null; } }
        bool copyNoteShown; // Control.Visible 은 창이 숨어 있으면 늘 거짓이다

        void ShowCopyNote()
        {
            var what = StartupCopy.Decide(lastCopy);
            bool show = kAuto.Checked && what != CopyPrompt.None;
            copyNoteShown = show;
            copyNote.Text = what == CopyPrompt.OldRunning ? StartupCopy.FormNoteRunning : StartupCopy.FormNote;
            copyNote.Visible = show;
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
            kAuto.Checked = s.AutoStart;
            kUpdate.Checked = s.UpdateCheck;
            cUpdate.SelectedIndex = (int)s.UpdateEvery;
            message.Text = "";
            Hint();
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
                AutoStart = kAuto.Checked,
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
            else if (message.ForeColor != Color.Firebrick) message.Text = "";
        }

        sealed class SaveOutcome
        {
            public bool Saved;
            public string Message;
            /// <summary>'시작 시 실행' 칸을 옛 값으로 되돌린다.</summary>
            public bool RevertAuto;
            /// <summary>옛 판이던 사본을 이 판으로 바꿨다.</summary>
            public bool CopyRefreshed;
            /// <summary>사본이 옛 판인데 바꾸지 못한 까닭(설정은 저장됐다).</summary>
            public string CopyWarning;
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
            if (r.RevertAuto) kAuto.Checked = old.AutoStart;
            if (r.Saved)
            {
                message.ForeColor = SystemColors.ControlText;
                message.Text = ctl.ChildRunning ? "저장했습니다. 다음 연결부터 적용됩니다." : "저장했습니다.";
                if (r.CopyRefreshed) message.Text += " 시작 시 실행 사본도 이 판으로 바꿨습니다.";
                if (r.CopyWarning != null)
                {
                    message.ForeColor = Color.FromArgb(0xB2, 0x6A, 0x00);
                    message.Text += "\r\n사본은 바꾸지 못했습니다: " + r.CopyWarning;
                }
                var unused = RefreshCopyNoteAsync();
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
                message.Text = "저장 중…";
            }
            UseWaitCursor = on;
        }

        /// <summary>일꾼 스레드 — 화면을 만지지 않는다. 던지지 않는다(무엇이든 실패로 돌려준다).</summary>
        SaveOutcome SaveWork(Settings s, Settings old)
        {
            bool startupChanged = s.AutoStart != old.AutoStart;
            try
            {
                if (startupChanged)
                {
                    string err;
                    bool ok = s.AutoStart ? ops.EnableStartup(exePath, out err) : ops.DisableStartup(out err);
                    if (!ok) return new SaveOutcome { Message = "시작 시 실행을 바꾸지 못했습니다: " + err, RevertAuto = true };
                }
            }
            catch (Exception ex)
            {
                return new SaveOutcome { Message = "시작 시 실행을 바꾸지 못했습니다: " + ex.Message, RevertAuto = true };
            }
            try
            {
                ctl.SaveSettings(s);
                var ok = new SaveOutcome { Saved = true };
                // 켜 둔 채 저장하면 사본이 옛 판인지 보고 바꾼다(그 칸을 껐다 켤 필요 없이). 막 켠 것은 EnableStartup 이 이미 복사했다.
                if (s.AutoStart && !startupChanged) RefreshCopyIfStale(ok);
                return ok;
            }
            catch (Exception ex)
            {
                var r = new SaveOutcome { Message = "저장하지 못했습니다: " + ex.Message };
                if (startupChanged)
                {
                    // 작업은 바꿨는데 설정은 옛 값 — 그대로 두면 칸은 꺼짐인데 로그온마다 뜨거나(그 반대) 한다. 작업을 되돌린다.
                    r.RevertAuto = true;
                    string back;
                    bool undone;
                    try { undone = old.AutoStart ? ops.EnableStartup(exePath, out back) : ops.DisableStartup(out back); }
                    catch (Exception ex2) { undone = false; back = ex2.Message; }
                    if (!undone) r.Message += "\r\n시작 시 실행도 되돌리지 못했습니다(" + back + ") — 작업 스케줄러의 " + SystemOps.TaskName + " 를 확인하십시오.";
                }
                return r;
            }
        }

        /// <summary>일꾼 스레드 — 사본이 옛 판이면 바꾼다. 던지지 않는다(설정은 이미 저장됐다 — 까닭만 돌려준다).</summary>
        void RefreshCopyIfStale(SaveOutcome r)
        {
            var copy = ops as IStartupCopy;
            if (copy == null) return;
            try
            {
                var what = StartupCopy.Decide(copy.CheckStartupCopy());
                if (what == CopyPrompt.None) return;
                if (what == CopyPrompt.OldRunning) { r.CopyWarning = StartupCopy.OldRunningShort; return; }
                string err;
                if (copy.RefreshStartupCopy(out err)) r.CopyRefreshed = true;
                else r.CopyWarning = err;
            }
            catch (Exception ex) { r.CopyWarning = ex.Message; }
        }
    }
}
