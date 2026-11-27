// 트레이가 서비스를 보는 길(ServiceClient)과 업데이트 알림(UpdateWatcher — 실제 판에서는 트레이가, 시험 판에서는 Controller 가 쓴다).
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Threading;
using System.Threading.Tasks;

namespace WebTether
{
    /// <summary>
    /// tun2proxy 업데이트·보안 권고를 묻고 알린다(알리기만). 남의 서버의 글을 읽으므로 SYSTEM 서비스에서는 돌리지 않는다 — 실제 판은
    /// 트레이(보통 권한)가 자기 폴더(%LOCALAPPDATA%\WebTether)의 상태 파일로 돌린다.
    /// </summary>
    public sealed class UpdateWatcher
    {
        readonly string file, root;
        readonly Log log;
        bool running;
        public UpdateState Updates { get; private set; }
        /// <summary>(주소, 전체 마감 UTC) → 글(막는 호출). 시험이 바꾼다.</summary>
        public Func<string, DateTime, string> Fetcher = UpdateFetch.Get;
        public string[] BuildSkippedVersions = Embedded.SkippedVersions.Length == 0 ? new string[0] : Embedded.SkippedVersions.Split(',');
        /// <summary>(제목, 글, 경고인가, 누르면 열 주소).</summary>
        public Action<string, string, bool, string> Tell;
        public event Action Changed;

        public UpdateWatcher(string file, string root, Log log)
        {
            this.file = file;
            this.root = root;
            this.log = log;
            Updates = UpdateState.Load(file);
        }

        public bool Running { get { return running; } }
        public string NewVersion { get { return Updates.NewVersion(Embedded.Tun2proxyVersion, BuildSkippedVersions); } }

        public bool Due(DateTime now, Settings s)
        {
            return s != null && s.UpdateCheck && !running && Updates.Due(now, s.UpdateEvery);
        }

        void Raise()
        {
            var h = Changed;
            if (h != null) h();
        }

        void Say(string title, string text, bool warn, string url)
        {
            var t = Tell;
            if (t != null) t(title, text, warn, url);
        }

        public async Task CheckAsync()
        {
            if (running) return;
            running = true;
            try
            {
                var st = Updates;
                var fetch = Fetcher;
                bool ok = await Task.Run(() => UpdateFetch.Check(st, Embedded.Tun2proxyVersion, fetch));
                st.Save(file, root);
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
                    Say("보안 권고 — tun2proxy " + Embedded.Tun2proxyVersion,
                        a.Id + (a.Severity.Length > 0 ? " (" + a.Severity + ")" : "") + " " + a.Summary +
                        (st.Advisories.Count > 1 ? " 외 " + (st.Advisories.Count - 1) + "건" : ""), true, a.Url.Length > 0 ? a.Url : null);
                }
                else if (st.Advisories.Count == 0) st.NotifiedAdvisories = "";
                var nv = NewVersion;
                if (nv != null && nv != st.NotifiedVersion)
                {
                    st.NotifiedVersion = nv;
                    Say("새 판 tun2proxy " + nv, "눌러 변경 내역을 봅니다. 바꾸려면 build.py --update 로 다시 짓습니다.", false,
                        st.LatestUrl.Length > 0 ? st.LatestUrl : null);
                }
                st.Save(file, root);
                Raise();
            }
            finally
            {
                running = false;
            }
        }

        public void Skip(string v)
        {
            if (Versions.Parse(v) == null || Updates.Skipped.Contains(v)) return;
            Updates.Skipped.Add(v);
            Updates.Save(file, root);
            log.Write("업데이트", v + " 는 알리지 않습니다");
            Raise();
        }
    }

    /// <summary>서비스가 돌려주는 상태 — 파이프의 'key=value' 와 오간다(순수, 시험한다).</summary>
    public sealed class ServiceStatus
    {
        public TetherState State = TetherState.Off;
        public string Detail = "";
        public ToggleAction Toggle = ToggleAction.Connect;
        public bool ChildRunning, DryRun;
        public int ChildPid;
        public string Version = "";
        /// <summary>트레이가 쓰는 설정 몇 칸(비밀 없음 — 폰 프록시 주소·접두어·업데이트).</summary>
        public Settings Settings = new Settings();
        public long NoticeSeq;
        public string NoticeTitle = "", NoticeText = "", NoticeUrl = "";
        public bool NoticeWarn;
        /// <summary>물을 사람이 없어 연결하지 않은 것 등 — 트레이가 '연결' 을 누르면 그때 묻는다.</summary>
        public bool Asking;
        /// <summary>마지막 설정 제안의 번호와 결과(ProposalResults 의 하나 — 묻는 동안은 asking).</summary>
        public long ProposalId;
        public string ProposalResult = "";
        /// <summary>최근 끝난 제안(번호 → 결과, 여덟까지) — 트레이가 제 결과를 놓치지 않게.</summary>
        public List<KeyValuePair<long, string>> ProposalRecent = new List<KeyValuePair<long, string>>();

        public Dictionary<string, string> ToMap()
        {
            var s = Settings;
            return new Dictionary<string, string>
            {
                { "ok", "1" }, { "state", ((int)State).ToString(CultureInfo.InvariantCulture) }, { "detail", Detail },
                { "toggle", ((int)Toggle).ToString(CultureInfo.InvariantCulture) }, { "child", ChildRunning ? "1" : "0" },
                { "pid", ChildPid.ToString(CultureInfo.InvariantCulture) }, { "dry", DryRun ? "1" : "0" }, { "version", Version },
                { "proxy_host", s.ProxyHost }, { "proxy_port", s.ProxyPort.ToString(CultureInfo.InvariantCulture) },
                { "proxy_kind", s.Kind == ProxyKind.Http ? "http" : "socks5" }, { "ssid_prefix", s.SsidPrefix },
                { "update_check", s.UpdateCheck ? "1" : "0" }, { "update_every", ((int)s.UpdateEvery).ToString(CultureInfo.InvariantCulture) },
                { "notice_seq", NoticeSeq.ToString(CultureInfo.InvariantCulture) }, { "notice_title", NoticeTitle }, { "notice_text", NoticeText },
                { "notice_url", NoticeUrl }, { "notice_warn", NoticeWarn ? "1" : "0" }, { "asking", Asking ? "1" : "0" },
                { "proposal_id", ProposalId.ToString(CultureInfo.InvariantCulture) }, { "proposal_result", ProposalResult },
                { "proposal_recent", RecentText() },
            };
        }

        string RecentText()
        {
            var parts = new List<string>();
            foreach (var kv in ProposalRecent) parts.Add(kv.Key.ToString(CultureInfo.InvariantCulture) + ":" + kv.Value);
            return string.Join(";", parts.ToArray());
        }

        static int Int(Dictionary<string, string> m, string k, int lo, int hi, int dflt)
        {
            string v;
            int n;
            if (!m.TryGetValue(k, out v) || !int.TryParse(v, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out n) || n < lo || n > hi) return dflt;
            return n;
        }

        static string Str(Dictionary<string, string> m, string k, int max)
        {
            string v;
            if (!m.TryGetValue(k, out v) || v == null) return "";
            v = SafeText.Clean(v, max);
            return v;
        }

        /// <summary>서비스의 답을 읽는다 — 값마다 범위를 보고, 글은 제어·방향 제어 문자를 걸러 자른다(SafeText).</summary>
        public static ServiceStatus FromMap(Dictionary<string, string> m)
        {
            var r = new ServiceStatus();
            r.State = (TetherState)Int(m, "state", 0, Enum.GetValues(typeof(TetherState)).Length - 1, 0);
            r.Detail = Str(m, "detail", 500);
            r.Toggle = (ToggleAction)Int(m, "toggle", 0, Enum.GetValues(typeof(ToggleAction)).Length - 1, 0);
            r.ChildRunning = Str(m, "child", 1) == "1";
            r.ChildPid = Int(m, "pid", 0, int.MaxValue, 0);
            r.DryRun = Str(m, "dry", 1) == "1";
            r.Version = Str(m, "version", 64);
            var s = new Settings();
            var host = Str(m, "proxy_host", 255);
            if (Settings.IsPhoneAddress(host)) s.ProxyHost = host;
            s.ProxyPort = Int(m, "proxy_port", 1, 65535, s.ProxyPort);
            s.Kind = Str(m, "proxy_kind", 10) == "http" ? ProxyKind.Http : ProxyKind.Socks5;
            var pre = Str(m, "ssid_prefix", 64);
            if (Settings.IsSsidPrefix(pre)) s.SsidPrefix = pre;
            s.UpdateCheck = Str(m, "update_check", 1) != "0";
            s.UpdateEvery = (UpdateInterval)Int(m, "update_every", 0, 2, (int)s.UpdateEvery);
            r.Settings = s;
            long seq;
            string sv;
            if (m.TryGetValue("notice_seq", out sv) && long.TryParse(sv, NumberStyles.None, CultureInfo.InvariantCulture, out seq)) r.NoticeSeq = seq;
            r.NoticeTitle = Str(m, "notice_title", 200);
            r.NoticeText = Str(m, "notice_text", 500);
            var url = Str(m, "notice_url", 500);
            r.NoticeUrl = SafeText.IsGithubUrl(url) ? url : "";
            r.NoticeWarn = Str(m, "notice_warn", 1) == "1";
            r.Asking = Str(m, "asking", 1) == "1";
            if (m.TryGetValue("proposal_id", out sv) && long.TryParse(sv, NumberStyles.None, CultureInfo.InvariantCulture, out seq)) r.ProposalId = seq;
            var pr = Str(m, "proposal_result", 16);
            r.ProposalResult = Array.IndexOf(ProposalResults.All, pr) >= 0 ? pr : "";
            string rec;
            if (m.TryGetValue("proposal_recent", out rec) && rec.Length <= 400)
                foreach (var part in rec.Split(';'))
                {
                    int colon = part.IndexOf(':');
                    long rid;
                    if (colon <= 0 || !long.TryParse(part.Substring(0, colon), NumberStyles.None, CultureInfo.InvariantCulture, out rid)) continue;
                    var res = part.Substring(colon + 1);
                    if (Array.IndexOf(ProposalResults.All, res) >= 0 && r.ProposalRecent.Count < 8) r.ProposalRecent.Add(new KeyValuePair<long, string>(rid, res));
                }
            return r;
        }
    }

    /// <summary>
    /// 실제 판의 트레이가 서비스를 보는 길 — 1초마다 '상태' 를 묻고(일꾼 스레드), 바뀌면 UI 스레드에서 Changed. 연결·끊기는 명령만 보낸다.
    /// 서비스가 없으면 상태는 '꺼짐' 이고 Detail 이 까닭('서비스가 없습니다' 등).
    /// </summary>
    public sealed class ServiceClient : ITetherView
    {
        readonly PipeClient pipe;
        readonly SynchronizationContext ui;
        readonly Log log;
        ServiceStatus last = new ServiceStatus();
        string lastError;
        bool polling;
        long seenNotice = -1;
        long logAfter;

        public ServiceClient(PipeClient pipe, Log log)
        {
            this.pipe = pipe;
            this.log = log;
            ui = SynchronizationContext.Current ?? new SynchronizationContext();
        }

        public TetherState State { get { return lastError != null ? TetherState.Off : last.State; } }
        public string Detail { get { return lastError ?? last.Detail; } }
        public ToggleAction Toggle { get { return lastError != null ? ToggleAction.Connect : last.Toggle; } }
        public bool ChildRunning { get { return lastError == null && last.ChildRunning; } }
        public int ChildPid { get { return lastError == null ? last.ChildPid : 0; } }
        public bool DryRun { get { return last.DryRun; } }
        public Settings Settings { get { return last.Settings; } }
        public string ServiceVersion { get { return last.Version; } }
        /// <summary>서비스와 이야기하지 못한 까닭(없으면 null).</summary>
        public string Unreachable { get { return lastError; } }
        public event Action Changed;
        public event Action<string, string, bool> Notice;
        public string NoticeUrl { get; private set; }

        public void Connect() { Command(PipeCommands.Connect); }
        public void Disconnect() { Command(PipeCommands.Disconnect); }

        void Command(string cmd)
        {
            Task.Run(() =>
            {
                string err;
                var r = pipe.Send(PipeCommands.Request(cmd), out err);
                if (r == null) log.Write("서비스", cmd + " 를 보내지 못함: " + err);
                else if (r.ContainsKey("error")) log.Write("서비스", cmd + " — " + SafeText.Clean(r["error"], 200));
            }).ContinueWith(_ => Poll());
        }

        public void Tick() { Poll(); }

        void Poll()
        {
            if (polling) return;
            polling = true;
            Task.Run(() =>
            {
                string err;
                var r = pipe.Send(PipeCommands.Request(PipeCommands.Status), out err);
                ServiceStatus st = null;
                if (r != null && r.ContainsKey("error")) err = SafeText.Clean(r["error"], 200);
                else if (r != null) st = ServiceStatus.FromMap(r);
                return Tuple.Create(st, err);
            }).ContinueWith(t =>
            {
                ui.Post(_ =>
                {
                    polling = false;
                    if (t.IsFaulted) return;
                    Apply(t.Result.Item1, t.Result.Item2);
                }, null);
            });
        }

        /// <summary>UI 스레드 — 답을 걸고 바뀌었으면 알린다(시험이 부른다).</summary>
        public void Apply(ServiceStatus st, string err)
        {
            bool changed;
            if (st == null)
            {
                var why = "서비스에 닿지 못함 — " + (err ?? "까닭 모름");
                changed = lastError != why;
                lastError = why;
            }
            else
            {
                changed = lastError != null || st.State != last.State || st.Detail != last.Detail || st.Toggle != last.Toggle ||
                          st.ChildRunning != last.ChildRunning;
                lastError = null;
                if (seenNotice >= 0 && st.NoticeSeq > seenNotice && st.NoticeTitle.Length > 0)
                {
                    NoticeUrl = st.NoticeUrl.Length > 0 ? st.NoticeUrl : null;
                    var h = Notice;
                    if (h != null) h(st.NoticeTitle, st.NoticeText, st.NoticeWarn);
                    NoticeUrl = null;
                }
                seenNotice = st.NoticeSeq;
                last = st;
            }
            if (changed)
            {
                var h = Changed;
                if (h != null) h();
            }
        }

        /// <summary>서비스 로그의 그 뒤 줄들(막는 호출 — 일꾼 스레드에서). 실패하면 null.</summary>
        public List<string> FetchLog()
        {
            string err;
            var req = PipeCommands.Request(PipeCommands.Log);
            req["after"] = logAfter.ToString(CultureInfo.InvariantCulture);
            var r = pipe.Send(req, out err);
            if (r == null || r.ContainsKey("error")) return null;
            var lines = new List<string>();
            string v;
            if (r.TryGetValue("lines", out v) && v.Length > 0)
                foreach (var l in v.Split('\n')) lines.Add(SafeText.Clean(l, 2000));
            long next;
            if (r.TryGetValue("next", out v) && long.TryParse(v, NumberStyles.None, CultureInfo.InvariantCulture, out next)) logAfter = next;
            return lines;
        }

        public Task ShutdownAsync() { return Task.FromResult(0); }
    }

    /// <summary>
    /// 트레이의 설정 창이 보는 곳(사용자 결정 11-27 — 관리자 판 설정 창을 걷었다). 지금 설정은 서비스에서 읽고(settings), 저장은 새 설정 전체를
    /// 제안한다(propose) — 서비스가 검사기로 거르고 콘솔 사용자에게 확인 창으로 물어 '예' 일 때만 건다. 결과는 상태의 proposal_* 로 기다린다.
    /// </summary>
    public sealed class ServiceSettingsHost : ISettingsHost
    {
        readonly PipeClient pipe;
        readonly Func<bool> childRunning;
        Settings current = new Settings();
        /// <summary>확인 창의 답을 기다리는 시한(서비스의 묻는 시한 60초 + 넉넉히) — 시험이 줄인다.</summary>
        public int WaitMs = 90 * 1000;
        public int PollMs = 500;

        public ServiceSettingsHost(PipeClient pipe, Func<bool> childRunning)
        {
            this.pipe = pipe;
            this.childRunning = childRunning;
        }

        public Settings Settings { get { return current; } }
        public bool ChildRunning { get { return childRunning != null && childRunning(); } }
        public string SavingText { get { return "확인 창을 기다리는 중…"; } }
        /// <summary>서비스가 시작할 때 설정 파일의 키를 기본값으로 돌렸으면 그 한 줄(Refresh 가 채운다).</summary>
        public string LoadNotice { get; private set; }

        /// <summary>막는 호출(일꾼 스레드) — 서비스의 지금 설정을 읽는다. 되면 null, 아니면 까닭.</summary>
        public string Refresh()
        {
            string err;
            var r = pipe.Send(PipeCommands.Request(PipeCommands.Settings), out err);
            if (r == null) return err;
            string e;
            if (r.TryGetValue("error", out e)) return SafeText.Clean(e, 200);
            var s = Settings.FromProposal(r, PipeCommands.SettingPrefix, out err);
            if (s == null) return "서비스의 설정을 읽지 못했습니다(" + err + ")";
            current = s;
            string d;
            LoadNotice = r.TryGetValue("defaulted", out d) ? Settings.DefaultedNotice(d) : null;
            return null;
        }

        /// <summary>일꾼 스레드 — 제안하고 확인 창의 답을 기다린다. 적용되지 않으면 짧은 까닭으로 던진다(설정 창이 '저장하지 못했습니다: …').</summary>
        public void SaveSettings(Settings s)
        {
            var req = PipeCommands.Request(PipeCommands.Propose);
            foreach (var kv in s.Pairs()) req[PipeCommands.SettingPrefix + kv.Key] = kv.Value;
            string err;
            var r = pipe.Send(req, out err);
            if (r == null) throw new IOException(err);
            string result, idText;
            r.TryGetValue("result", out result);
            if (result == ProposalResults.Unchanged) return;
            long id;
            if (result != ProposalResults.Asking || !r.TryGetValue("id", out idText) ||
                !long.TryParse(idText, NumberStyles.None, CultureInfo.InvariantCulture, out id))
                throw new InvalidOperationException(ProposalResults.Text(result));
            var until = DateTime.UtcNow.AddMilliseconds(WaitMs);
            while (DateTime.UtcNow < until)
            {
                Thread.Sleep(PollMs);
                var m = pipe.Send(PipeCommands.Request(PipeCommands.Status), out err);
                if (m == null || m.ContainsKey("error")) continue;
                var verdict = Judge(ServiceStatus.FromMap(m), id);
                if (verdict == null) continue;
                if (verdict.Length == 0) return;
                throw new InvalidOperationException(verdict);
            }
            throw new TimeoutException("확인 창의 답을 받지 못했습니다.");
        }

        public void ApplySettings(Settings s) { current = s; }

        /// <summary>
        /// 상태로 제 제안(id)의 끝을 가린다 — 아직이면 null, 적용이면 "", 아니면 까닭. 최근 결과 목록의 제 것을 먼저 보고, 제 결과를 모르는 채
        /// 더 큰 번호가 보이면 남의 제안이 들어온 것이라 끝낸다(남의 결과를 제 것으로 읽지 않게 — 검토 11-27). 순수 — 시험한다.
        /// </summary>
        public static string Judge(ServiceStatus st, long id)
        {
            string mine = null;
            foreach (var kv in st.ProposalRecent) if (kv.Key == id) mine = kv.Value;
            if (mine == null && st.ProposalId == id && st.ProposalResult != ProposalResults.Asking && st.ProposalResult.Length > 0) mine = st.ProposalResult;
            if (mine != null) return mine == ProposalResults.Applied ? "" : ProposalResults.Text(mine);
            // 번호는 서비스가 뜰 때마다 무작위로 시작한다 — 크기가 아니라 '다른가' 로 본다(다른 요청이 앞섰거나 서비스가 다시 떴다).
            if (st.ProposalId != id) return "다른 요청이 처리됐거나 서비스가 다시 시작됐습니다. 다시 저장하세요.";
            return null;
        }
    }
}
