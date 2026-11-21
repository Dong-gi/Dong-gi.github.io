// 의존 업데이트 알림 — 알리기만 한다. 새 판을 받거나 바꿔 끼우지 않는다: exe 안의 tun2proxy·wintun 은
// 빌드 때 고정한 SHA-256 으로만 돌고(Binaries), 새 판은 사람이 build.py --update 로 확인하고 다시 짓는다.
// 묻는 곳 — GitHub API 의 tun2proxy 최신 릴리스와 보안 권고(GHSA 전역 + 저장소의 것). wintun 은 tun2proxy 의
// zip 을 따라가므로 따로 보지 않는다. RustSec 은 GHSA 가 들여오므로 따로 묻지 않는다.
// 남의 서버가 준 글이다 — 크기·시간 상한, 최소한만 읽고, 화면에 넣기 전에 제어·방향 제어 문자를 거르고 자른다.
using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;

namespace WebTether
{
    public sealed class Advisory
    {
        public string Id = "", Severity = "", Summary = "", Url = "", Range = "";
    }

    public sealed class UpdateInfo
    {
        public string Tag = "", Version = "", Url = "", Published = "", AssetDigest = "";
    }

    /// <summary>판 견주기 — semver 꼴(앞의 v 는 뗀다). 숫자 셋(모자라면 0), '-' 뒤는 시험판.</summary>
    public static class Versions
    {
        public sealed class V
        {
            public int[] Nums = new int[3];
            public string[] Pre = new string[0];
        }

        public static V Parse(string s)
        {
            if (s == null) return null;
            s = s.Trim();
            if (s.StartsWith("v") || s.StartsWith("V")) s = s.Substring(1);
            int plus = s.IndexOf('+');
            if (plus >= 0) s = s.Substring(0, plus);
            string pre = null;
            int dash = s.IndexOf('-');
            if (dash >= 0) { pre = s.Substring(dash + 1); s = s.Substring(0, dash); }
            var parts = s.Split('.');
            if (parts.Length < 1 || parts.Length > 3) return null;
            var v = new V();
            for (int i = 0; i < parts.Length; i++)
            {
                int n;
                if (parts[i].Length == 0 || parts[i].Length > 9 || !int.TryParse(parts[i], NumberStyles.None, CultureInfo.InvariantCulture, out n)) return null;
                v.Nums[i] = n;
            }
            if (pre != null)
            {
                if (pre.Length == 0) return null;
                v.Pre = pre.Split('.');
            }
            return v;
        }

        public static int Compare(V a, V b)
        {
            for (int i = 0; i < 3; i++) if (a.Nums[i] != b.Nums[i]) return a.Nums[i].CompareTo(b.Nums[i]);
            if (a.Pre.Length == 0 && b.Pre.Length == 0) return 0;
            if (a.Pre.Length == 0) return 1;   // 정식 > 시험판
            if (b.Pre.Length == 0) return -1;
            for (int i = 0; i < Math.Min(a.Pre.Length, b.Pre.Length); i++)
            {
                int x, y;
                bool nx = int.TryParse(a.Pre[i], NumberStyles.None, CultureInfo.InvariantCulture, out x);
                bool ny = int.TryParse(b.Pre[i], NumberStyles.None, CultureInfo.InvariantCulture, out y);
                int c = nx && ny ? x.CompareTo(y) : (nx ? -1 : (ny ? 1 : string.CompareOrdinal(a.Pre[i], b.Pre[i])));
                if (c != 0) return Math.Sign(c);
            }
            return a.Pre.Length.CompareTo(b.Pre.Length);
        }

        /// <summary>a 가 b 보다 새로운가. 읽지 못하면 false.</summary>
        public static bool Newer(string a, string b)
        {
            var x = Parse(a); var y = Parse(b);
            return x != null && y != null && Compare(x, y) > 0;
        }

        /// <summary>
        /// GHSA 의 vulnerable_version_range('&lt; 0.8.9', '&gt;= 0.8.0, &lt; 0.8.9', '= 0.8.7', '0.8.7') 에 드는가.
        /// 읽지 못하는 표기면 null — 부르는 쪽이 '확인 필요' 로 본다.
        /// </summary>
        public static bool? InRange(string version, string range)
        {
            var v = Parse(version);
            if (v == null || string.IsNullOrEmpty(range)) return null;
            foreach (var raw in range.Split(','))
            {
                var c = raw.Trim();
                if (c.Length == 0) return null;
                string op = "=";
                foreach (var o in new[] { "<=", ">=", "<", ">", "=" })
                    if (c.StartsWith(o)) { op = o; c = c.Substring(o.Length).Trim(); break; }
                var w = Parse(c);
                if (w == null) return null;
                int cmp = Compare(v, w);
                bool ok;
                switch (op)
                {
                    case "<": ok = cmp < 0; break;
                    case "<=": ok = cmp <= 0; break;
                    case ">": ok = cmp > 0; break;
                    case ">=": ok = cmp >= 0; break;
                    default: ok = cmp == 0; break;
                }
                if (!ok) return false;
            }
            return true;
        }
    }

    public static class SafeText
    {
        static bool Bidi(char c)
        {
            return c == '‎' || c == '‏' || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩') ||
                   c == '؜' || c == '﻿' || c == '​';
        }

        /// <summary>제어·방향 제어 문자를 빼고 빈칸을 하나로, max 글자로 자른다.</summary>
        public static string Clean(string s, int max)
        {
            if (string.IsNullOrEmpty(s)) return "";
            var sb = new StringBuilder();
            bool space = false;
            int limit = Math.Min(s.Length, max * 8); // 긴 글을 끝까지 훑지 않는다
            for (int i = 0; i < limit && sb.Length <= max; i++)
            {
                var c = s[i];
                if (Bidi(c)) continue;
                if (char.IsControl(c) || char.IsWhiteSpace(c)) { space = true; continue; }
                if (space && sb.Length > 0) sb.Append(' ');
                space = false;
                sb.Append(c);
            }
            var r = sb.ToString();
            if (r.Length <= max && limit == s.Length) return r;
            int cut = Math.Min(r.Length, max - 1);
            if (cut > 0 && char.IsHighSurrogate(r[cut - 1])) cut--; // 짝을 가르지 않는다
            return r.Substring(0, cut) + "…";
        }

        /// <summary>브라우저로 열 주소 — github.com 의 https 만.</summary>
        public static bool IsGithubUrl(string u)
        {
            Uri uri;
            return Uri.TryCreate(u, UriKind.Absolute, out uri) && uri.Scheme == Uri.UriSchemeHttps &&
                   (uri.Host == "github.com") && uri.IsDefaultPort && u.Length <= 300 && u.IndexOfAny(new[] { '"', ' ', '\'', '<', '>' }) < 0;
        }
    }

    public static class UpdateParse
    {
        public const int MaxJsonChars = 2 * 1024 * 1024;

        static object Deserialize(string json)
        {
            if (json == null || json.Length > MaxJsonChars) throw new InvalidDataException("JSON 이 너무 큽니다");
            var js = new JavaScriptSerializer { MaxJsonLength = MaxJsonChars, RecursionLimit = 24 };
            return js.DeserializeObject(json);
        }

        static string Str(IDictionary<string, object> d, string k)
        {
            object v;
            return d != null && d.TryGetValue(k, out v) && v is string ? (string)v : null;
        }

        static IDictionary<string, object> Obj(IDictionary<string, object> d, string k)
        {
            object v;
            return d != null && d.TryGetValue(k, out v) ? v as IDictionary<string, object> : null;
        }

        static IEnumerable Arr(IDictionary<string, object> d, string k)
        {
            object v;
            return d != null && d.TryGetValue(k, out v) ? v as object[] : null;
        }

        /// <summary>/releases/latest 의 답. assetName 의 digest(sha256:…)를 함께.</summary>
        public static UpdateInfo Release(string json, string assetName)
        {
            var d = Deserialize(json) as IDictionary<string, object>;
            if (d == null) throw new InvalidDataException("릴리스가 객체가 아닙니다");
            var tag = SafeText.Clean(Str(d, "tag_name"), 40);
            var v = Versions.Parse(tag);
            if (v == null) throw new InvalidDataException("판 이름을 읽지 못함");
            var info = new UpdateInfo
            {
                Tag = tag,
                Version = tag.TrimStart('v', 'V'),
                Url = Str(d, "html_url") ?? "",
                Published = SafeText.Clean(Str(d, "published_at"), 30),
            };
            if (!SafeText.IsGithubUrl(info.Url)) info.Url = "";
            var assets = Arr(d, "assets");
            if (assets != null)
                foreach (var o in assets)
                {
                    var a = o as IDictionary<string, object>;
                    if (Str(a, "name") != assetName) continue;
                    var dg = Str(a, "digest") ?? "";
                    if (dg.StartsWith("sha256:") && dg.Length == 71) info.AssetDigest = dg.Substring(7).ToLowerInvariant();
                }
            return info;
        }

        /// <summary>GHSA 목록(전역 /advisories 나 저장소의 security-advisories) 가운데 package 의 version 에 걸리는 것.</summary>
        public static List<Advisory> Advisories(string json, string package, string version)
        {
            var list = new List<Advisory>();
            var arr = Deserialize(json) as object[];
            if (arr == null) throw new InvalidDataException("권고 목록이 배열이 아닙니다");
            foreach (var o in arr)
            {
                var a = o as IDictionary<string, object>;
                if (a == null) continue;
                if (Str(a, "withdrawn_at") != null) continue;
                var vulns = Arr(a, "vulnerabilities");
                if (vulns == null) continue;
                foreach (var vo in vulns)
                {
                    var vd = vo as IDictionary<string, object>;
                    var pkg = Obj(vd, "package");
                    if (!string.Equals(Str(pkg, "name"), package, StringComparison.OrdinalIgnoreCase)) continue;
                    var range = Str(vd, "vulnerable_version_range") ?? "";
                    var hit = Versions.InRange(version, range);
                    if (hit == false) continue;
                    var url = Str(a, "html_url") ?? "";
                    list.Add(new Advisory
                    {
                        Id = SafeText.Clean(Str(a, "ghsa_id"), 40),
                        Severity = SafeText.Clean(Str(a, "severity"), 12),
                        Summary = SafeText.Clean(Str(a, "summary"), 120) + (hit == null ? " (범위를 읽지 못함 — 확인 필요)" : ""),
                        Url = SafeText.IsGithubUrl(url) ? url : "",
                        Range = SafeText.Clean(range, 60),
                    });
                    break;
                }
            }
            return list;
        }
    }

    public enum UpdateInterval { Daily, Weekly, Monthly }

    /// <summary>마지막 확인과 그 결과, 건너뛴 판, 이미 알린 것. 설정 폴더의 update 파일(key=value).</summary>
    public sealed class UpdateState
    {
        public DateTime? LastCheck;
        public bool LastOk;
        public string LastError = "";
        public string Latest = "", LatestUrl = "", LatestPublished = "", LatestDigest = "";
        public List<Advisory> Advisories = new List<Advisory>();
        public List<string> Skipped = new List<string>();
        public string NotifiedVersion = "";
        public string NotifiedAdvisories = "";

        public static TimeSpan Span(UpdateInterval i)
        {
            return i == UpdateInterval.Daily ? TimeSpan.FromDays(1) : (i == UpdateInterval.Monthly ? TimeSpan.FromDays(30) : TimeSpan.FromDays(7));
        }

        /// <summary>확인할 때인가. 시계가 거꾸로 갔으면(마지막이 하루 넘게 미래) 지금 확인한다.</summary>
        public bool Due(DateTime now, UpdateInterval i)
        {
            if (LastCheck == null) return true;
            var last = LastCheck.Value;
            if (last > now.AddDays(1)) return true;
            return now - last >= Span(i);
        }

        /// <summary>알릴 새 판 — 지금 판보다 새롭고, 사용자가 건너뛰지 않았고, 빌드가 막은 판이 아닌 것. 없으면 null.</summary>
        public string NewVersion(string ours, IEnumerable<string> buildSkipped)
        {
            if (string.IsNullOrEmpty(Latest) || !Versions.Newer(Latest, ours)) return null;
            if (Skipped.Contains(Latest)) return null;
            foreach (var s in buildSkipped) if (s == Latest) return null;
            return Latest;
        }

        static string Esc(string s) { return (s ?? "").Replace("\t", " ").Replace("\r", " ").Replace("\n", " "); }

        public string Serialize()
        {
            var sb = new StringBuilder();
            Action<string, string> w = (k, v) => sb.Append(k).Append('=').Append(Esc(v)).Append('\n');
            w("last_check", LastCheck.HasValue ? LastCheck.Value.ToUniversalTime().ToString("o", CultureInfo.InvariantCulture) : "");
            w("last_ok", LastOk ? "1" : "0");
            w("last_error", LastError);
            w("latest", Latest);
            w("latest_url", LatestUrl);
            w("latest_published", LatestPublished);
            w("latest_digest", LatestDigest);
            // advisory 줄만 칸을 탭으로 가른다 — 칸마다 탭·줄바꿈을 먼저 지운다.
            foreach (var a in Advisories)
                sb.Append("advisory=").Append(string.Join("\t", new[] { Esc(a.Id), Esc(a.Severity), Esc(a.Url), Esc(a.Range), Esc(a.Summary) })).Append('\n');
            w("skipped", string.Join(",", Skipped.ToArray()));
            w("notified_version", NotifiedVersion);
            w("notified_advisories", NotifiedAdvisories);
            return sb.ToString();
        }

        public static UpdateState Parse(string text)
        {
            var u = new UpdateState();
            if (text == null) return u;
            foreach (var raw in text.Split('\n'))
            {
                var line = raw.TrimEnd('\r');
                int eq = line.IndexOf('=');
                if (eq <= 0) continue;
                string k = line.Substring(0, eq), v = line.Substring(eq + 1);
                switch (k)
                {
                    case "last_check":
                        DateTime t;
                        if (DateTime.TryParse(v, CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out t)) u.LastCheck = t.ToLocalTime();
                        break;
                    case "last_ok": u.LastOk = v == "1"; break;
                    case "last_error": u.LastError = SafeText.Clean(v, 200); break;
                    case "latest": if (Versions.Parse(v) != null) u.Latest = v; break;
                    case "latest_url": if (SafeText.IsGithubUrl(v)) u.LatestUrl = v; break;
                    case "latest_published": u.LatestPublished = SafeText.Clean(v, 30); break;
                    case "latest_digest": u.LatestDigest = SafeText.Clean(v, 64); break;
                    case "advisory":
                        var p = v.Split('\t');
                        if (p.Length == 5)
                            u.Advisories.Add(new Advisory
                            {
                                Id = SafeText.Clean(p[0], 40), Severity = SafeText.Clean(p[1], 12),
                                Url = SafeText.IsGithubUrl(p[2]) ? p[2] : "", Range = SafeText.Clean(p[3], 60), Summary = SafeText.Clean(p[4], 160)
                            });
                        break;
                    case "skipped":
                        foreach (var s in v.Split(','))
                            if (Versions.Parse(s) != null && !u.Skipped.Contains(s)) u.Skipped.Add(s);
                        break;
                    case "notified_version": u.NotifiedVersion = v; break;
                    case "notified_advisories": u.NotifiedAdvisories = v; break;
                }
            }
            return u;
        }

        public static UpdateState Load(string path)
        {
            try { return File.Exists(path) ? Parse(File.ReadAllText(path, Encoding.UTF8)) : new UpdateState(); }
            catch (IOException) { return new UpdateState(); }
            catch (UnauthorizedAccessException) { return new UpdateState(); }
        }

        public void Save(string path, string root)
        {
            try
            {
                SafeFs.WriteAtomic(path, Serialize(), root);
            }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }

        public string AdvisoryKey()
        {
            var ids = new List<string>();
            foreach (var a in Advisories) ids.Add(a.Id);
            ids.Sort(StringComparer.Ordinal);
            return string.Join(",", ids.ToArray());
        }
    }

    /// <summary>망에 묻는 쪽. 막는 호출 — 일꾼 스레드에서.</summary>
    public static class UpdateFetch
    {
        public const string Repo = "tun2proxy/tun2proxy";
        public const string Package = "tun2proxy";
        public const string Asset = "tun2proxy-x86_64-pc-windows-msvc.zip";
        public const int MaxBytes = 1024 * 1024;
        /// <summary>확인 한 번(요청 셋)의 **전체** 상한. 읽기 한 번마다의 시한이면 한 바이트씩 흘리는 서버가 끝없이 붙잡는다.</summary>
        public const int TotalTimeoutMs = 15000;

        static int RemainingMs(DateTime deadlineUtc)
        {
            var ms = (deadlineUtc - DateTime.UtcNow).TotalMilliseconds;
            return ms <= 0 ? 0 : (int)Math.Min(ms, int.MaxValue);
        }

        static TimeoutException Late() { return new TimeoutException("업데이트 확인이 시간 상한을 넘었습니다"); }

        /// <summary>deadlineUtc 까지 — 넘으면 요청을 끊고(Abort) TimeoutException.</summary>
        public static string Get(string url, DateTime deadlineUtc)
        {
            int remaining = RemainingMs(deadlineUtc);
            if (remaining == 0) throw Late();
            // TLS 1.2 이상만(.NET 4.8 의 Tls13 = 12288).
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | (SecurityProtocolType)12288;
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = "GET";
            req.UserAgent = "WebTether-update-check";
            req.Accept = "application/vnd.github+json";
            req.Timeout = remaining;
            req.ReadWriteTimeout = remaining;
            req.AllowAutoRedirect = false;
            req.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
            req.KeepAlive = false;
            using (new System.Threading.Timer(_ => { try { req.Abort(); } catch (Exception) { } }, null, remaining, System.Threading.Timeout.Infinite))
            {
                try
                {
                    using (var resp = (HttpWebResponse)req.GetResponse())
                    using (var s = resp.GetResponseStream())
                    using (var ms = new MemoryStream())
                    {
                        var buf = new byte[16384];
                        int n;
                        while ((n = s.Read(buf, 0, buf.Length)) > 0)
                        {
                            ms.Write(buf, 0, n);
                            if (ms.Length > MaxBytes) throw new InvalidDataException("답이 너무 큽니다");
                            if (RemainingMs(deadlineUtc) == 0) throw Late();
                        }
                        return Encoding.UTF8.GetString(ms.ToArray());
                    }
                }
                catch (WebException)
                {
                    if (RemainingMs(deadlineUtc) == 0) throw Late();
                    throw;
                }
                catch (IOException)
                {
                    if (RemainingMs(deadlineUtc) == 0) throw Late();
                    throw;
                }
            }
        }

        public static bool Check(UpdateState state, string ourVersion, Func<string, DateTime, string> get)
        {
            return Check(state, ourVersion, get, TotalTimeoutMs);
        }

        /// <summary>한 번 확인 — 결과를 state 에 적는다. 실패하면 false 와 까닭(state.LastError). 요청 셋을 합쳐 totalMs 안에.</summary>
        public static bool Check(UpdateState state, string ourVersion, Func<string, DateTime, string> get, int totalMs)
        {
            state.LastCheck = DateTime.Now;
            var deadline = DateTime.UtcNow.AddMilliseconds(totalMs);
            Func<string, string> fetch = url =>
            {
                if (RemainingMs(deadline) == 0) throw Late();
                var r = get(url, deadline);
                if (RemainingMs(deadline) == 0) throw Late();
                return r;
            };
            try
            {
                var rel = UpdateParse.Release(fetch("https://api.github.com/repos/" + Repo + "/releases/latest"), Asset);
                var adv = new List<Advisory>();
                var seen = new HashSet<string>();
                foreach (var url in new[]
                {
                    "https://api.github.com/advisories?ecosystem=rust&affects=" + Package + "&per_page=100",
                    "https://api.github.com/repos/" + Repo + "/security-advisories?state=published&per_page=100",
                })
                {
                    foreach (var a in UpdateParse.Advisories(fetch(url), Package, ourVersion))
                        if (seen.Add(a.Id)) adv.Add(a);
                }
                state.Latest = rel.Version;
                state.LatestUrl = rel.Url;
                state.LatestPublished = rel.Published;
                state.LatestDigest = rel.AssetDigest;
                state.Advisories = adv;
                state.LastOk = true;
                state.LastError = "";
                return true;
            }
            catch (Exception ex)
            {
                state.LastOk = false;
                state.LastError = SafeText.Clean(ex.GetType().Name + ": " + ex.Message, 200);
                return false;
            }
        }
    }
}
