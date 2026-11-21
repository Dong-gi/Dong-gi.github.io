// 업데이트 알림 시험 — 실제 망 없이, GitHub API 의 답 꼴을 옮긴 조각으로.
// 릴리스 조각은 2026-11-21 의 /releases/latest(v0.8.7)에서 쓰는 칸만 남기고 줄였다. 권고 조각은 GHSA 의 꼴로 지은 것이다
// (tun2proxy 에는 아직 권고가 없다 — 실제 답은 []).
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;

namespace WebTether.Tests
{
    static class UpdateTests
    {
        public const string ReleaseJson = @"{
  ""url"": ""https://api.github.com/repos/tun2proxy/tun2proxy/releases/261000000"",
  ""html_url"": ""https://github.com/tun2proxy/tun2proxy/releases/tag/v0.9.0"",
  ""tag_name"": ""v0.9.0"",
  ""name"": ""v0.9.0"",
  ""draft"": false,
  ""prerelease"": false,
  ""published_at"": ""2026-12-01T11:48:42Z"",
  ""assets"": [
    { ""name"": ""tun2proxy-aarch64-pc-windows-msvc.zip"", ""digest"": ""sha256:d9ab46718c2450eac64802f28cc0e17c111f2f473647835ee3b3bbd7295500b7"",
      ""browser_download_url"": ""https://github.com/tun2proxy/tun2proxy/releases/download/v0.9.0/tun2proxy-aarch64-pc-windows-msvc.zip"" },
    { ""name"": ""tun2proxy-x86_64-pc-windows-msvc.zip"", ""digest"": ""sha256:801396FF46776AAC4D9A6660A2972DBEB70B68C90A95A38289126C756298F863"", ""size"": 4938491,
      ""browser_download_url"": ""https://github.com/tun2proxy/tun2proxy/releases/download/v0.9.0/tun2proxy-x86_64-pc-windows-msvc.zip"" }
  ],
  ""body"": ""## tun2proxy 0.9.0\r\n\r\n### Highlights\r\n- ...""
}";

        public const string AdvisoriesJson = @"[
  { ""ghsa_id"": ""GHSA-aaaa-bbbb-cccc"", ""severity"": ""high"", ""summary"": ""UDP gateway \u202Edesrever\u202C session\nconfusion"",
    ""html_url"": ""https://github.com/advisories/GHSA-aaaa-bbbb-cccc"", ""withdrawn_at"": null,
    ""vulnerabilities"": [ { ""package"": { ""ecosystem"": ""rust"", ""name"": ""tun2proxy"" }, ""vulnerable_version_range"": "">= 0.8.0, < 0.8.9"", ""first_patched_version"": ""0.8.9"" } ] },
  { ""ghsa_id"": ""GHSA-dddd-eeee-ffff"", ""severity"": ""low"", ""summary"": ""old bug"", ""html_url"": ""https://github.com/advisories/GHSA-dddd-eeee-ffff"",
    ""vulnerabilities"": [ { ""package"": { ""name"": ""tun2proxy"" }, ""vulnerable_version_range"": ""< 0.7.0"" } ] },
  { ""ghsa_id"": ""GHSA-gggg-hhhh-iiii"", ""severity"": ""critical"", ""summary"": ""withdrawn"", ""html_url"": ""https://github.com/advisories/GHSA-gggg-hhhh-iiii"",
    ""withdrawn_at"": ""2026-01-01T00:00:00Z"",
    ""vulnerabilities"": [ { ""package"": { ""name"": ""tun2proxy"" }, ""vulnerable_version_range"": ""<= 1.0.0"" } ] },
  { ""ghsa_id"": ""GHSA-jjjj-kkkk-llll"", ""severity"": ""high"", ""summary"": ""other crate"", ""html_url"": ""https://github.com/advisories/GHSA-jjjj-kkkk-llll"",
    ""vulnerabilities"": [ { ""package"": { ""name"": ""hyper"" }, ""vulnerable_version_range"": ""< 9.0.0"" } ] },
  { ""ghsa_id"": ""GHSA-mmmm-nnnn-oooo"", ""severity"": ""moderate"", ""summary"": ""odd range"", ""html_url"": ""javascript:alert(1)"",
    ""vulnerabilities"": [ { ""package"": { ""name"": ""tun2proxy"" }, ""vulnerable_version_range"": ""^0.8"" } ] }
]";

        public static void Add(Action<string, Action> T)
        {
            T("업데이트: 판 견주기", () =>
            {
                A.True(Versions.Newer("0.8.10", "0.8.7"), "0.8.10 > 0.8.7(문자열이 아니라 수로)");
                A.True(Versions.Newer("v0.9.0", "0.8.7"), "앞의 v");
                A.True(!Versions.Newer("0.9.0-rc.1", "0.9.0"), "시험판 < 정식");
                A.True(Versions.Newer("0.9.0-rc.1", "0.8.7"), "0.9.0-rc.1 > 0.8.7");
                A.True(Versions.Newer("0.9.0-rc.10", "0.9.0-rc.2"), "rc.10 > rc.2");
                A.True(Versions.Newer("0.9.0-rc.1", "0.9.0-beta.5"), "rc > beta(글자 차례)");
                A.True(!Versions.Newer("0.8.7", "0.8.7"), "같음");
                A.True(!Versions.Newer("0.8", "0.8.0"), "모자란 자리는 0");
                A.True(Versions.Parse("0.8.x") == null && Versions.Parse("") == null && Versions.Parse("1.2.3.4") == null && Versions.Parse("0.9.0-") == null, "읽지 못함");
                A.True(!Versions.Newer("garbage", "0.8.7"), "읽지 못하면 새 판 아님");
            });

            T("업데이트: 권고의 범위 표기", () =>
            {
                A.Eq((bool?)true, Versions.InRange("0.8.7", "< 0.8.9"), "<");
                A.Eq((bool?)true, Versions.InRange("0.8.7", ">= 0.8.0, < 0.8.9"), "구간");
                A.Eq((bool?)false, Versions.InRange("0.8.9", ">= 0.8.0, < 0.8.9"), "끝은 빠짐");
                A.Eq((bool?)true, Versions.InRange("0.8.7", "= 0.8.7"), "=");
                A.Eq((bool?)true, Versions.InRange("0.8.7", "0.8.7"), "맨 판");
                A.Eq((bool?)true, Versions.InRange("0.8.7", "<= 0.8.7"), "<=");
                A.Eq((bool?)false, Versions.InRange("0.8.7", "> 0.8.7"), ">");
                A.Eq((bool?)true, Versions.InRange("0.9.0-rc.1", "< 0.9.0"), "시험판은 정식보다 앞");
                A.Eq((bool?)null, Versions.InRange("0.8.7", "^0.8"), "모르는 표기");
                A.Eq((bool?)null, Versions.InRange("0.8.7", ""), "빈 범위");
                A.Eq((bool?)null, Versions.InRange("0.8.7", ">= 0.8.0,"), "빈 조각");
            });

            T("업데이트: 남의 글 거르기", () =>
            {
                A.Eq("a b c", SafeText.Clean("a\r\n\tb\u0000 c", 50), "제어 문자와 빈칸");
                A.Eq("evil.exe", SafeText.Clean("\u202Eevil\u2066.exe\u200F\uFEFF", 50), "방향 제어 문자");
                A.Eq("abcd…", SafeText.Clean("abcdefghij", 5), "자르기");
                A.Eq("abcde", SafeText.Clean("abcde", 5), "딱 맞으면 그대로");
                var emoji = string.Concat(Enumerable.Repeat("😀", 10));
                var cut = SafeText.Clean(emoji, 6);
                A.True(cut.Length <= 6 && !char.IsHighSurrogate(cut[cut.Length - 2]), "서로게이트를 반으로 자르지 않음: " + cut.Length);
                A.True(SafeText.IsGithubUrl("https://github.com/tun2proxy/tun2proxy/releases/tag/v0.9.0"), "릴리스 쪽");
                foreach (var bad in new[] { "http://github.com/x", "https://github.com.evil.test/x", "https://evil.test/github.com", "javascript:alert(1)",
                                            "https://github.com:8443/x", "https://github.com/x\" & calc", "file:///c:/windows", "" })
                    A.True(!SafeText.IsGithubUrl(bad), "거절 " + bad);
            });

            T("업데이트: 릴리스 답 읽기(digest 는 소문자로)", () =>
            {
                var r = UpdateParse.Release(ReleaseJson, UpdateFetch.Asset);
                A.Eq("0.9.0", r.Version, "판");
                A.Eq("https://github.com/tun2proxy/tun2proxy/releases/tag/v0.9.0", r.Url, "쪽");
                A.Eq("801396ff46776aac4d9a6660a2972dbeb70b68c90a95a38289126c756298f863", r.AssetDigest, "x64 의 digest");
                A.Throws<InvalidDataException>(() => UpdateParse.Release("[]", UpdateFetch.Asset), "배열");
                A.Throws<InvalidDataException>(() => UpdateParse.Release("{\"tag_name\":\"latest\"}", UpdateFetch.Asset), "판 아님");
                var big = "{\"tag_name\":\"v1.0.0\",\"body\":\"" + new string('x', UpdateParse.MaxJsonChars) + "\"}";
                A.Throws<InvalidDataException>(() => UpdateParse.Release(big, UpdateFetch.Asset), "너무 큼");
                var noUrl = UpdateParse.Release("{\"tag_name\":\"v1.0.0\",\"html_url\":\"https://evil.test/\"}", UpdateFetch.Asset);
                A.Eq("", noUrl.Url, "github.com 밖의 주소는 버림");
            });

            T("업데이트: 권고 가운데 이 판에 걸리는 것만", () =>
            {
                var list = UpdateParse.Advisories(AdvisoriesJson, "tun2proxy", "0.8.7");
                A.Eq("GHSA-aaaa-bbbb-cccc,GHSA-mmmm-nnnn-oooo", string.Join(",", list.Select(a => a.Id).ToArray()), "걸린 것(철회·다른 꾸러미·범위 밖 제외)");
                A.Eq("UDP gateway desrever session confusion", list[0].Summary, "요약 거름");
                A.True(list[1].Summary.Contains("확인 필요"), "모르는 범위는 확인 필요로");
                A.Eq("", list[1].Url, "javascript: 주소는 버림");
                A.Eq(0, UpdateParse.Advisories(AdvisoriesJson, "tun2proxy", "0.8.9").Count(a => a.Id == "GHSA-aaaa-bbbb-cccc"), "고친 판에는 걸리지 않음");
                A.Eq(0, UpdateParse.Advisories("[]", "tun2proxy", "0.8.7").Count, "실제 답(없음)");
            });

            T("업데이트: 간격과 건너뛴 판, 상태 파일", () =>
            {
                var st = new UpdateState();
                var now = new DateTime(2026, 11, 21, 12, 0, 0);
                A.True(st.Due(now, UpdateInterval.Weekly), "처음");
                st.LastCheck = now.AddDays(-6);
                A.True(!st.Due(now, UpdateInterval.Weekly) && st.Due(now, UpdateInterval.Daily), "6일 전 — 주 아님, 날 맞음");
                st.LastCheck = now.AddDays(-29);
                A.True(!st.Due(now, UpdateInterval.Monthly), "29일");
                st.LastCheck = now.AddDays(5);
                A.True(st.Due(now, UpdateInterval.Monthly), "시계가 거꾸로 가면 지금");
                st.Latest = "0.9.0";
                A.Eq("0.9.0", st.NewVersion("0.8.7", new string[0]), "새 판");
                A.Eq((string)null, st.NewVersion("0.8.7", new[] { "0.9.0" }), "빌드가 막은 판");
                st.Skipped.Add("0.9.0");
                A.Eq((string)null, st.NewVersion("0.8.7", new string[0]), "사용자가 건너뜀");
                st.Advisories.Add(new Advisory { Id = "GHSA-x", Severity = "high", Url = "https://github.com/advisories/GHSA-x", Range = "< 1", Summary = "a\tb" });
                st.LastCheck = now;
                var r = UpdateState.Parse(st.Serialize() + "latest_url=https://evil.test/\nskipped=bad,0.9.1\n");
                A.Eq(1, r.Advisories.Count, "권고");
                A.Eq("a b", r.Advisories[0].Summary, "탭은 칸 구분에만");
                A.True(r.Skipped.Contains("0.9.0") && r.Skipped.Contains("0.9.1") && !r.Skipped.Contains("bad"), "건너뛴 판");
                A.Eq("", r.LatestUrl, "낯선 주소 버림");
                A.Eq(now, r.LastCheck.Value, "시각");
            });

            T("업데이트: 연결된 동안 한 번 묻고 알린다(권고는 경고, 새 판은 알림), 다시는 묻지 않는다", () =>
            {
                var home = TestMain.Scratch("ctl-update");
                var paths = AppPaths.Under(home);
                var log = new Log(paths.LogFile);
                var lines = new List<string>();
                log.Line += l => { lock (lines) lines.Add(l); };
                var c = new Controller(paths, log, new DryRunOps(null), new DryRunChildFactory(), null, true, false, new Settings { UpdateEvery = UpdateInterval.Daily });
                var asked = new List<string>();
                c.UpdateFetcher = (url, deadline) =>
                {
                    lock (asked) asked.Add(url);
                    if (url.EndsWith("/releases/latest")) return ReleaseJson;
                    if (url.Contains("advisories")) return url.Contains("/repos/") ? "[]" : AdvisoriesJson;
                    throw new WebExceptionLike("unexpected " + url);
                };
                c.HotspotFinder = () => new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12 };
                c.Prober = (h, p, k, t) => new ProbeResult { Ok = true };
                c.Tun2proxyLister = () => new List<int>();
                c.RouteReader = () => new List<RouteRow>();
                var notices = new List<string>();
                var urls = new List<string>();
                c.Notice += (t, x, w) => { lock (notices) { notices.Add((w ? "!" : "i") + t); urls.Add(c.NoticeUrl); } };
                c.ConnectAsync().Wait(10000);
                Thread.Sleep(2100);
                c.Tick();
                A.Eq(TetherState.Connected, c.State, "연결됨");
                c.Tick(); // 이 Tick 에서 묻는다
                A.WaitUntil(() => { lock (notices) return notices.Count >= 2; }, 5000, "알림 둘");
                A.True(notices[0].StartsWith("!보안 권고"), notices[0]);
                A.Eq("https://github.com/advisories/GHSA-aaaa-bbbb-cccc", urls[0], "권고 쪽");
                A.True(notices[1].StartsWith("i새 판 tun2proxy 0.9.0"), notices[1]);
                A.Eq(3, asked.Count, "릴리스 하나와 권고 둘");
                A.True(File.Exists(paths.UpdateFile), "상태를 적음");
                c.Tick();
                Thread.Sleep(200);
                A.Eq(3, asked.Count, "간격 안에는 다시 묻지 않음");
                A.Eq("0.9.0", c.NewVersion, "메뉴에 보일 새 판");
                c.SkipVersion("0.9.0");
                A.Eq((string)null, c.NewVersion, "건너뛰면 사라짐");
                A.Eq("0.9.0", UpdateState.Load(paths.UpdateFile).Skipped.Single(), "건너뛴 판을 적음");
                c.DisconnectAsync().Wait(10000);
            });

            T("업데이트: 실패는 조용히(로그만), 다음 간격에 다시", () =>
            {
                var home = TestMain.Scratch("ctl-update-fail");
                var paths = AppPaths.Under(home);
                var log = new Log(paths.LogFile);
                var c = new Controller(paths, log, new DryRunOps(null), new DryRunChildFactory(), null, true, false, new Settings());
                c.UpdateFetcher = (url, deadline) => { throw new System.Net.WebException("이름을 찾지 못함"); };
                var notices = 0;
                c.Notice += (t, x, w) => Interlocked.Increment(ref notices);
                c.CheckUpdatesAsync().Wait(10000);
                A.Eq(0, notices, "알리지 않음");
                A.True(!c.Updates.LastOk && c.Updates.LastError.Contains("WebException"), c.Updates.LastError);
                A.True(!c.Updates.Due(DateTime.Now, UpdateInterval.Weekly), "다음 간격까지 기다림");
                A.True(File.ReadAllText(paths.LogFile).Contains("확인하지 못함"), "로그");
            });
        }

        public static void AddTimeouts(Action<string, Action> T)
        {
            T("업데이트: 전체 시한 — 한 바이트씩 흘리는 서버도 마감에 끊는다(읽기마다의 시한이 아니다)", () =>
            {
                // 루프백 — 머리를 보내고 0.3초마다 한 바이트. 읽기 한 번마다 시한을 걸면 끝나지 않는다.
                var l = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
                l.Start();
                int port = ((System.Net.IPEndPoint)l.LocalEndpoint).Port;
                var stop = new ManualResetEventSlim(false);
                var server = System.Threading.Tasks.Task.Run(() =>
                {
                    try
                    {
                        using (var c = l.AcceptTcpClient())
                        {
                            var s = c.GetStream();
                            var buf = new byte[4096];
                            s.ReadTimeout = 3000;
                            try { s.Read(buf, 0, buf.Length); } catch (IOException) { }
                            var head = System.Text.Encoding.ASCII.GetBytes("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n");
                            s.Write(head, 0, head.Length);
                            for (int i = 0; i < 100 && !stop.Wait(300); i++) s.WriteByte((byte)'x');
                        }
                    }
                    catch (Exception) { }
                });
                try
                {
                    var sw = System.Diagnostics.Stopwatch.StartNew();
                    Exception got = null;
                    var call = System.Threading.Tasks.Task.Run(() =>
                    {
                        try { UpdateFetch.Get("http://127.0.0.1:" + port + "/", DateTime.UtcNow.AddMilliseconds(2000)); }
                        catch (Exception ex) { got = ex; }
                    });
                    A.True(call.Wait(8000), "8초 안에 끝남(" + sw.ElapsedMilliseconds + " ms)");
                    A.True(got is TimeoutException, "TimeoutException: " + (got == null ? "없음" : got.GetType().Name + " " + got.Message));
                    A.True(sw.ElapsedMilliseconds < 4000, "마감 근처에서 끊음: " + sw.ElapsedMilliseconds + " ms");
                }
                finally
                {
                    stop.Set();
                    l.Stop();
                    server.Wait(3000);
                }
            });

            T("업데이트: 요청 셋을 합쳐 전체 상한 — 넘으면 남은 요청을 하지 않고 실패", () =>
            {
                var st = new UpdateState();
                int calls = 0;
                bool ok = UpdateFetch.Check(st, "0.8.7", (url, deadline) =>
                {
                    Interlocked.Increment(ref calls);
                    Thread.Sleep(800);
                    return url.EndsWith("/releases/latest") ? ReleaseJson : "[]";
                }, 1500);
                A.True(!ok, "실패");
                A.True(st.LastError.Contains("시간"), st.LastError);
                A.Eq(2, calls, "둘째 뒤에 멈춤(셋째는 하지 않음)");
                var st2 = new UpdateState();
                A.True(UpdateFetch.Check(st2, "0.8.7", (url, deadline) => url.EndsWith("/releases/latest") ? ReleaseJson : "[]", 1500), "빠르면 통과");
                A.Eq(UpdateFetch.TotalTimeoutMs, 15000, "기본 전체 상한 15초");
            });
        }

        sealed class WebExceptionLike : Exception { public WebExceptionLike(string m) : base(m) { } }
    }
}
