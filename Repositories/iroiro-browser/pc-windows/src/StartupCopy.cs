// '시작 시 실행' 사본이 지금 판과 같은가.
//
// 시작 시 실행을 켜면 그때 돌던 exe 를 Program Files\WebTether 로 한 번 복사하고 작업 스케줄러가 로그온 때 그 사본을 돌린다.
// 사용자가 내려받기 폴더의 exe 를 새 판으로 바꿔도 사본은 옛 판이라 재부팅 뒤 옛 판이 떴다(2026-11-21 사용자 기기). 그래서 도우미가 뜰 때
// 작업이 정말 우리 사본을 돌리는지(schtasks /Query /XML — 읽기만) 보고, 지금 도는 exe 가 그 사본이 아니며 내용(SHA-256)이 다르면 한 번
// 묻는다. 바꾸기는 '시작 시 실행' 을 켤 때와 같은 길(쥔 핸들의 바이트 → 임시 이름 → 해시 대조 → 옮김 → 다시 대조, 설치 폴더 ACL)이다.
// 작업이 옛 꼴(사본 exe 를 곧바로 띄운다 — 환경 변수를 거르지 않는다, SystemOps.StartAction)이거나 시작 스크립트가 다르면 사본이 같아도
// 묻고, '예' 면 작업을 다시 등록한다(schtasks /Create /F — 같은 이름을 갈아 끼운다).
//
// 옛 사본이 돌고 있으면 바꾸지 못한다(돌고 있는 exe 는 지울 수 없다). 보통은 하나만 실행 표지 때문에 새 판이 '이미 떠 있다' 로 끝나므로
// 그 길(Program.Main)에서 '옛 판을 끝낸 뒤 다시 실행하라' 를 알린다.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Xml;

namespace WebTether
{
    public enum CopyStatus
    {
        /// <summary>견주지 못했다(작업 물음이 시간 초과·XML 을 못 읽음·사본을 못 읽음 …) — 묻지 않는다.</summary>
        Unknown,
        /// <summary>작업이 없다 — 시작 시 실행이 꺼져 있다.</summary>
        Off,
        /// <summary>작업이 우리 사본이 아닌 것을 돌린다 — 손대지 않는다.</summary>
        OtherTarget,
        /// <summary>지금 도는 것이 그 사본이다(로그온 때 뜬 것).</summary>
        RunningInstalled,
        Same,
        /// <summary>다르다(사본 파일이 없는 것도).</summary>
        Stale,
    }

    public sealed class CopyCheck
    {
        public CopyStatus Status;
        /// <summary>작업은 사본을 가리키는데 그 파일이 없다(Stale 의 한 꼴).</summary>
        public bool Missing;
        /// <summary>사본이 다른 프로세스로 돌고 있다(로그온 때 뜬 옛 판) — 바꾸지 못한다.</summary>
        public bool InstalledRunning;
        /// <summary>작업이 옛 꼴이다 — 사본 exe 를 곧바로 띄운다(환경 변수를 거르지 않는다 — SystemOps.StartAction). 다시 등록해야 한다.</summary>
        public bool OldTask;
        /// <summary>작업은 새 꼴인데 설치 폴더의 시작 스크립트가 없거나 다르다.</summary>
        public bool LauncherStale;
        /// <summary>로그에 적을 까닭.</summary>
        public string Detail = "";

        public override string ToString()
        {
            return Status + (Missing ? " · 사본 없음" : "") + (InstalledRunning ? " · 사본이 돌고 있음" : "") +
                   (OldTask ? " · 옛 꼴 작업" : "") + (LauncherStale ? " · 시작 스크립트 다름" : "") + (Detail.Length > 0 ? " — " + Detail : "");
        }
    }

    /// <summary>사본 견주기·바꾸기 — 둘 다 막는 일이라 일꾼 스레드에서만 부른다.</summary>
    public interface IStartupCopy
    {
        CopyCheck CheckStartupCopy();
        /// <summary>사본을 지금 판으로, 작업이 옛 꼴이면 다시 등록. 실패하면 false 와 까닭.</summary>
        bool RefreshStartupCopy(out string error);
    }

    public enum CopyPrompt { None, Ask, OldRunning }

    public static class StartupCopy
    {
        public const string Title = AppInfo.Name + " — 시작 시 실행";
        public const string AskText = "'시작 시 실행' 사본이 이 판과 다릅니다.\r\n사본을 이 판으로 바꿀까요?";
        public const string OldRunningText = "시작 시 실행으로 뜬 옛 판이 아직 떠 있습니다 — 알림 영역 아이콘의 '끝내기' 로 끝낸 뒤 이 판을 다시 실행하세요.";
        public const string OldRunningShort = "시작 시 실행으로 뜬 옛 판이 아직 떠 있습니다 — 끝낸 뒤 다시 하세요";
        public const string FormNote = "사본이 옛 판입니다 — 저장하면 바꿉니다";
        public const string FormNoteRunning = "사본이 옛 판입니다 — 떠 있는 옛 판을 끝낸 뒤 저장하세요";

        public const string AskTaskText = "'시작 시 실행' 등록이 옛 꼴입니다(보안 고침).\r\n등록을 이 판으로 바꿀까요?";
        public const string AskBothText = "'시작 시 실행' 사본이 이 판과 다르고 등록도 옛 꼴입니다.\r\n사본과 등록을 이 판으로 바꿀까요?";
        public const string FormNoteTask = "등록이 옛 꼴입니다 — 저장하면 바꿉니다";

        /// <summary>작업을 다시 등록해야 하는가(옛 꼴·시작 스크립트 다름).</summary>
        public static bool NeedsTask(CopyCheck c)
        {
            return c != null && (c.OldTask || c.LauncherStale);
        }

        public static CopyPrompt Decide(CopyCheck c)
        {
            if (c == null) return CopyPrompt.None;
            if (c.Status == CopyStatus.Stale) return c.InstalledRunning ? CopyPrompt.OldRunning : CopyPrompt.Ask;
            // 사본은 같은데(또는 지금 도는 것이 사본인데) 작업이 옛 꼴 — 다시 등록만 한다(사본이 돌고 있어도 된다).
            if (NeedsTask(c) && (c.Status == CopyStatus.Same || c.Status == CopyStatus.RunningInstalled)) return CopyPrompt.Ask;
            return CopyPrompt.None;
        }

        public static string AskTextFor(CopyCheck c)
        {
            if (!NeedsTask(c)) return AskText;
            return c.Status == CopyStatus.Stale ? AskBothText : AskTaskText;
        }

        public static string FormNoteFor(CopyCheck c)
        {
            if (Decide(c) == CopyPrompt.OldRunning) return FormNoteRunning;
            return c != null && c.Status != CopyStatus.Stale && NeedsTask(c) ? FormNoteTask : FormNote;
        }

        /// <summary>바꾼 뒤의 풍선 글.</summary>
        public static string DoneText(CopyCheck c, bool ok, string err)
        {
            bool task = NeedsTask(c), copy = c == null || c.Status == CopyStatus.Stale;
            var what = task && copy ? "사본과 등록" : task ? "등록" : "사본";
            return ok ? "시작 시 실행 " + what + "을 이 판으로 바꿨습니다." : "시작 시 실행 " + what + "을 바꾸지 못했습니다: " + err;
        }

        /// <summary>작업 XML(schtasks /Query /XML 의 출력)의 Exec 명령들. 읽지 못하면 null. DTD 는 받지 않는다.</summary>
        public static List<string> TaskCommands(string output)
        {
            var execs = TaskExecs(output);
            if (execs == null) return null;
            var r = new List<string>();
            foreach (var e in execs) r.Add(e.Command);
            return r;
        }

        public enum TaskForm { Other, Old, Current }

        /// <summary>
        /// 작업이 우리 것인가 — Exec 하나가 지금 꼴(SystemOps.StartAction 과 글자까지 같다 — 명령은 환경 변수를 펼치지 않고 견준다)이면
        /// Current, 사본 exe 를 인자 없이 곧바로 돌리는 옛 꼴이면 Old, 그 밖은 Other(손대지 않는다).
        /// </summary>
        public static TaskForm FormOf(List<SystemOps.TaskAction> execs, string target)
        {
            if (execs == null || execs.Count != 1) return TaskForm.Other;
            var e = execs[0];
            SystemOps.TaskAction want;
            try { want = SystemOps.StartAction(target); }
            catch (ArgumentException) { want = null; }
            if (want != null && string.Equals((e.Command ?? "").Trim(), want.Command, StringComparison.OrdinalIgnoreCase) &&
                string.Equals((e.Arguments ?? "").Trim(), want.Arguments, StringComparison.Ordinal) &&
                string.Equals((e.WorkingDirectory ?? "").Trim().TrimEnd('\\'), want.WorkingDirectory.TrimEnd('\\'), StringComparison.OrdinalIgnoreCase))
                return TaskForm.Current;
            if (SamePath(e.Command, target) && string.IsNullOrWhiteSpace(e.Arguments)) return TaskForm.Old;
            return TaskForm.Other;
        }

        /// <summary>작업 XML 의 Exec 들(명령·인자·작업 폴더). 읽지 못하면 null. DTD 는 받지 않는다.</summary>
        public static List<SystemOps.TaskAction> TaskExecs(string output)
        {
            if (string.IsNullOrEmpty(output)) return null;
            int start = output.IndexOf("<?xml", StringComparison.Ordinal);
            if (start < 0) start = output.IndexOf("<Task", StringComparison.Ordinal);
            int end = output.LastIndexOf("</Task>", StringComparison.Ordinal);
            if (start < 0 || end < start) return null;
            var xml = output.Substring(start, end + "</Task>".Length - start);
            try
            {
                var doc = new XmlDocument { XmlResolver = null };
                var rs = new XmlReaderSettings { DtdProcessing = DtdProcessing.Prohibit, XmlResolver = null };
                using (var sr = new StringReader(xml))
                using (var xr = XmlReader.Create(sr, rs)) doc.Load(xr);
                if (doc.DocumentElement == null || doc.DocumentElement.LocalName != "Task") return null;
                var r = new List<SystemOps.TaskAction>();
                foreach (XmlNode n in doc.SelectNodes("//*[local-name()='Actions']/*[local-name()='Exec']"))
                {
                    Func<string, string> child = name =>
                    {
                        string v = null;
                        foreach (XmlNode k in n.ChildNodes)
                            if (k.NodeType == XmlNodeType.Element && k.LocalName == name) v = k.InnerText;
                        return v;
                    };
                    r.Add(new SystemOps.TaskAction { Command = child("Command") ?? "", Arguments = child("Arguments"), WorkingDirectory = child("WorkingDirectory") });
                }
                return r;
            }
            catch (XmlException) { return null; }
            catch (System.Xml.XPath.XPathException) { return null; }
        }

        /// <summary>작업의 명령(따옴표·환경 변수가 있을 수 있다)이 그 경로인가 — 대소문자를 가리지 않는다. 온전한 경로가 아니면 아니다.</summary>
        public static bool SamePath(string command, string path)
        {
            if (string.IsNullOrWhiteSpace(command) || string.IsNullOrWhiteSpace(path)) return false;
            var c = command.Trim();
            if (c.Length >= 2 && c[0] == '"' && c[c.Length - 1] == '"') c = c.Substring(1, c.Length - 2);
            c = Environment.ExpandEnvironmentVariables(c);
            if (c.IndexOf('"') >= 0 || !Path.IsPathRooted(c) || c.StartsWith(@"\\", StringComparison.Ordinal)) return false;
            try
            {
                return string.Equals(Path.GetFullPath(c).TrimEnd('\\'), Path.GetFullPath(path).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase);
            }
            catch (ArgumentException) { return false; }
            catch (NotSupportedException) { return false; }
            catch (PathTooLongException) { return false; }
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool QueryFullProcessImageNameW(IntPtr h, uint flags, StringBuilder buf, ref uint size);
        [DllImport("kernel32.dll")]
        static extern bool CloseHandle(IntPtr h);
        const uint PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

        /// <summary>그 프로세스의 이미지 경로(못 읽으면 null) — 읽기만 한다.</summary>
        public static string ImagePath(int pid)
        {
            var h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
            if (h == IntPtr.Zero) return null;
            try
            {
                var sb = new StringBuilder(32768);
                uint n = (uint)sb.Capacity;
                return QueryFullProcessImageNameW(h, 0, sb, ref n) ? sb.ToString(0, (int)n) : null;
            }
            finally { CloseHandle(h); }
        }

        /// <summary>이 프로세스 밖에서 그 exe 가 돌고 있는가 — 같은 이름의 프로세스만 이미지 경로를 견준다(읽기만).</summary>
        public static bool IsRunningElsewhere(string exe)
        {
            int me;
            using (var cur = Process.GetCurrentProcess()) me = cur.Id;
            var ps = Process.GetProcessesByName(Path.GetFileNameWithoutExtension(exe));
            try
            {
                foreach (var p in ps)
                {
                    if (p.Id == me) continue;
                    var img = ImagePath(p.Id);
                    if (img != null && SamePath(img, exe)) return true;
                }
                return false;
            }
            finally { foreach (var p in ps) p.Dispose(); }
        }
    }

    /// <summary>
    /// 뜰 때 한 번 — 견주기(일꾼 스레드) → 다르면 묻기(UI 스레드, 맨 앞) → '예' 면 바꾸기(일꾼 스레드) → 결과 알림. '아니요' 면 이 실행
    /// 동안 다시 묻지 않는다(다음 실행에는 다시 묻는다). 사본이 돌고 있으면 묻지 않고 '옛 판을 끝낸 뒤 다시' 를 알린다.
    /// RunAsync 는 UI 스레드에서 부른다(이어지는 일이 그 스레드로 온다).
    /// </summary>
    public sealed class StartupCopyFlow
    {
        readonly IStartupCopy copy;
        readonly Action<string> log;
        bool started;
        Task work = Task.FromResult(0);

        /// <summary>묻기 — '예' 면 참(TopAsk).</summary>
        public Func<string, bool> Ask;
        /// <summary>알리기만(맨 앞 창).</summary>
        public Action<string> Tell;
        /// <summary>결과 알림(글, 경고인가) — 풍선.</summary>
        public Action<string, bool> Notify;
        /// <summary>다른 묻는 창이 떠 있는가 — 떠 있으면 닫힐 때까지 기다렸다 묻는다(겹쳐 묻지 않는다).</summary>
        public Func<bool> Busy = () => false;
        public int BusyPollMs = 500;
        /// <summary>끝내는 중인가 — 참이면 묻거나 알리지 않는다(견주기·기다리기는 일꾼 스레드·타이머라 그사이 '끝내기' 를 누를 수 있다).</summary>
        public Func<bool> Stopping = () => false;
        /// <summary>시험이 본다.</summary>
        public CopyCheck LastCheck;
        public bool Declined;

        public StartupCopyFlow(IStartupCopy copy, Action<string> log)
        {
            this.copy = copy;
            this.log = log;
        }

        /// <summary>바꾸는 중이면 그 일 — 끝내기가 기다린다(Program Files 에 임시 파일을 남기지 않게).</summary>
        public Task Pending { get { return work; } }

        void Log(string s) { if (log != null) log(s); }

        public async Task RunAsync()
        {
            if (started || copy == null) return;
            started = true;
            CopyCheck c;
            try { c = await Task.Run(() => copy.CheckStartupCopy()); }
            catch (Exception ex) { Log("견주지 못함: " + ex.Message); return; }
            LastCheck = c;
            Log("사본 견줌 — " + c);
            var what = StartupCopy.Decide(c);
            if (what == CopyPrompt.None) return;
            while (Busy() && !Stopping()) await Task.Delay(BusyPollMs);
            // 견주는 사이·다른 창을 기다리는 사이 '끝내기' 를 눌렀으면 끝나는 도우미가 창을 띄우지 않는다(r6 검토).
            if (Stopping()) { Log("끝내는 중이라 묻지 않습니다"); return; }
            if (what == CopyPrompt.OldRunning)
            {
                if (Tell != null) Tell(StartupCopy.OldRunningText);
                return;
            }
            if (Ask == null || !Ask(StartupCopy.AskTextFor(c)))
            {
                Declined = true;
                Log("사본을 바꾸지 않음('아니요') — 이 실행 동안 다시 묻지 않습니다");
                return;
            }
            string err = null;
            var t = Task.Run(() => copy.RefreshStartupCopy(out err));
            work = t;
            bool ok;
            try { ok = await t; }
            catch (Exception ex) { ok = false; err = ex.Message; }
            if (Notify != null) Notify(StartupCopy.DoneText(c, ok, err), !ok);
        }
    }
}
