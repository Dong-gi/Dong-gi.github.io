// 시스템을 바꾸는 일은 모두 여기로만 간다 — 시험 판·--dry-run 은 DryRunOps 가 기록만 한다.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Security;
using System.Security.Principal;
using System.Text;

namespace WebTether
{
    public interface ISystemOps
    {
        bool WlanConnect(string profile, out string output);
        bool Route(RouteAction action, out string output);
        bool EnableStartup(string exePath, out string error);
        bool DisableStartup(out string error);
        bool StartupEnabled();
    }

    public sealed class DryRunOps : ISystemOps, IStartupCopy
    {
        readonly Action<string> log;
        public readonly List<string> Calls = new List<string>();
        public bool StartupState;
        /// <summary>시험이 정한다 — null 이면 작업이 있으면 같음, 없으면 꺼짐.</summary>
        public CopyCheck CopyState;
        /// <summary>시험이 정한다 — 사본 바꾸기의 실패 글(null 이면 성공).</summary>
        public string CopyError;
        /// <summary>견주기·바꾸기를 부른 스레드(시험).</summary>
        public volatile int CopyThread;

        public DryRunOps(Action<string> log) { this.log = log; }

        void Note(string s)
        {
            lock (Calls) Calls.Add(s);
            if (log != null) log("[dry-run] " + s);
        }

        public bool WlanConnect(string profile, out string output)
        {
            output = "";
            Note("netsh " + CommandLine.NetshWlanConnect(profile));
            return true;
        }

        public bool Route(RouteAction action, out string output)
        {
            output = "";
            Note(action.ToString());
            return true;
        }

        public bool EnableStartup(string exePath, out string error)
        {
            error = null;
            Note("schtasks /Create /TN " + SystemOps.TaskName + " (" + exePath + ")");
            StartupState = true;
            return true;
        }

        public bool DisableStartup(out string error)
        {
            error = null;
            Note("schtasks /Delete /TN " + SystemOps.TaskName);
            StartupState = false;
            return true;
        }

        public bool StartupEnabled() { return StartupState; }

        public CopyCheck CheckStartupCopy()
        {
            CopyThread = System.Threading.Thread.CurrentThread.ManagedThreadId;
            var c = CopyState;
            return c ?? new CopyCheck { Status = StartupState ? CopyStatus.Same : CopyStatus.Off };
        }

        public bool RefreshStartupCopy(out string error)
        {
            CopyThread = System.Threading.Thread.CurrentThread.ManagedThreadId;
            Note("사본 바꾸기 " + SystemOps.InstalledExe);
            error = CopyError;
            if (error != null) return false;
            CopyState = new CopyCheck { Status = CopyStatus.Same };
            return true;
        }
    }

    /// <summary>System32 의 도구를 돌린다 — 끝 코드(시간 초과면 -1)와 출력.</summary>
    public delegate int ToolRunner(string exe, string args, int timeoutMs, out string output);

    public sealed class SystemOps : ISystemOps, IStartupCopy
    {
        public const string TaskName = "WebTether";
        public const int TimedOut = -1;
        static string Sys32 { get { return Environment.GetFolderPath(Environment.SpecialFolder.System); } }

        readonly SelfImage self;
        readonly Action<string> note;
        /// <summary>시험이 바꾼다 — schtasks 를 실제로 돌리지 않고.</summary>
        public ToolRunner RunTool = Run;
        /// <summary>schtasks 가 시간 초과로 끊긴 뒤 작업이 있는지 묻기 전에 쉬는 시간 — 끊긴 요청을 작업 스케줄러가 마저 끝낼 틈.</summary>
        public int SettleDelayMs = 1000;
        /// <summary>사본의 자리 — 시험은 스크래치로 바꾼다(실제 판은 InstalledExe).</summary>
        public string Target = InstalledExe;
        /// <summary>설치 폴더의 정책 — 시험은 '이 사용자 전용'.</summary>
        public DirPolicy Policy = DirPolicy.AdminOnly();
        /// <summary>사본이 다른 프로세스로 돌고 있는가 — 시험이 바꾼다.</summary>
        public Func<string, bool> IsRunning = StartupCopy.IsRunningElsewhere;
        /// <summary>쥔 exe(self.Stream 의 자리)와 사본 쓰기를 하나씩 — 뜰 때의 바꾸기와 설정 저장이 겹칠 수 있다.</summary>
        readonly object copyLock = new object();

        /// <summary>self: 시작할 때 쥔 이 exe(SelfImage.Open) — '시작 시 실행' 의 사본은 그 핸들에서만 만든다. null 이면 켜지 못한다.</summary>
        public SystemOps(SelfImage self, Action<string> note)
        {
            this.self = self;
            this.note = note;
        }

        static int Run(string exe, string args, int timeoutMs, out string output)
        {
            var psi = new ProcessStartInfo(Path.Combine(Sys32, exe), args)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                WorkingDirectory = Sys32,
            };
            using (var p = Process.Start(psi))
            {
                var so = p.StandardOutput.ReadToEndAsync();
                var se = p.StandardError.ReadToEndAsync();
                if (!p.WaitForExit(timeoutMs))
                {
                    try { p.Kill(); } catch (InvalidOperationException) { } catch (System.ComponentModel.Win32Exception) { }
                    output = "시간 초과";
                    return TimedOut;
                }
                output = (so.Result + se.Result).Trim();
                return p.ExitCode;
            }
        }

        public bool WlanConnect(string profile, out string output)
        {
            return Run("netsh.exe", CommandLine.NetshWlanConnect(profile), 15000, out output) == 0;
        }

        public bool Route(RouteAction action, out string output)
        {
            return Run("route.exe", CommandLine.Join(action.RouteArgs()), 10000, out output) == 0;
        }

        /// <summary>자동 시작용 사본 — Program Files 아래(관리자만 쓸 수 있다). 사용자 폴더의 exe 를 '가장 높은 권한' 으로 돌리면
        /// 다른 프로그램이 그 exe 를 바꿔 로그온 때 관리자 권한을 얻을 수 있다.</summary>
        public static string InstalledExe
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "WebTether", "WebTether.exe"); }
        }

        /// <summary>
        /// schtasks 에 넘길 XML 의 자리 — 설치 폴더(Program Files 아래, 관리자만 쓴다) 안. %TEMP% 는 사용자가 쓰는 곳이라
        /// 사용자의 프로그램이 그 폴더를 접합점으로 두고 쓴 뒤·읽기 전에 대상을 바꾸면(쥔 핸들은 옛 대상의 파일을 지킬 뿐이다)
        /// schtasks 가 남의 XML — 로그온 때 '가장 높은 권한' 으로 도는 아무 명령 — 을 등록한다.
        /// </summary>
        public static string TaskXmlPath(string installedExe)
        {
            return Path.Combine(Path.GetDirectoryName(installedExe), "task-" + Guid.NewGuid().ToString("N") + ".xml");
        }

        public static string TaskXml(string exePath, string userSid)
        {
            Func<string, string> x = SecurityElement.Escape;
            var sb = new StringBuilder();
            sb.Append("<?xml version=\"1.0\" encoding=\"UTF-16\"?>\r\n");
            sb.Append("<Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\r\n");
            sb.Append("  <RegistrationInfo><Description>Web tether helper</Description></RegistrationInfo>\r\n");
            sb.Append("  <Triggers><LogonTrigger><Enabled>true</Enabled><UserId>").Append(x(userSid)).Append("</UserId></LogonTrigger></Triggers>\r\n");
            sb.Append("  <Principals><Principal id=\"Author\"><UserId>").Append(x(userSid)).Append("</UserId>");
            sb.Append("<LogonType>InteractiveToken</LogonType><RunLevel>HighestAvailable</RunLevel></Principal></Principals>\r\n");
            sb.Append("  <Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>");
            sb.Append("<DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries><StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>");
            sb.Append("<ExecutionTimeLimit>PT0S</ExecutionTimeLimit><Enabled>true</Enabled></Settings>\r\n");
            sb.Append("  <Actions Context=\"Author\"><Exec><Command>").Append(x(exePath)).Append("</Command></Exec></Actions>\r\n");
            sb.Append("</Task>\r\n");
            return sb.ToString();
        }

        /// <summary>
        /// 남이 읽을 파일을 새로 쓰고, 다 쓴 뒤 **읽기만 하는 핸들**(공유 Read)로 다시 열어 쓴 바이트와 같은지 보고 그 핸들을 돌려준다.
        /// 쥔 동안 남은 쓰거나 지울 수 없고(공유에 Write·Delete 없음) 읽기만 할 수 있다.
        /// 쓰기 권한을 쥔 채 남에게 읽히면 안 된다 — schtasks 는 XML 을 공유 Read 로(남의 쓰기를 막으며) 여는데, 쓰기 권한이 있는
        /// 핸들이 열려 있으면 '다른 프로세스가 파일을 사용 중' 으로 실패했다(2026-11-21 사용자 기기 — '시작 시 실행' 을 켜지 못함).
        /// </summary>
        public static FileStream WriteThenHoldForReaders(string path, byte[] bytes)
        {
            using (var w = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                w.Write(bytes, 0, bytes.Length);
                w.Flush(true);
            }
            var r = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
            try
            {
                if (r.Length != bytes.Length) throw new IOException("다시 연 파일의 길이가 다릅니다: " + path);
                var back = new byte[bytes.Length];
                int got = 0, n;
                while (got < back.Length && (n = r.Read(back, got, back.Length - got)) > 0) got += n;
                for (int i = 0; i < bytes.Length; i++)
                    if (got != bytes.Length || back[i] != bytes[i]) throw new IOException("다시 연 파일의 내용이 다릅니다: " + path);
                r.Position = 0;
                return r;
            }
            catch
            {
                r.Dispose();
                throw;
            }
        }

        bool Failed(string step, string detail, out string error)
        {
            error = step + (string.IsNullOrEmpty(detail) ? "" : ": " + detail);
            if (note != null) note("실패 — " + error);
            return false;
        }

        public bool EnableStartup(string exePath, out string error)
        {
            error = null;
            string step = "준비";
            try
            {
                var target = Target;
                if (self == null || self.Stream == null)
                    return Failed("시작할 때 이 exe 를 쥐지 못해 복사하지 않습니다", "로그의 '이 exe 를 쥐지 못함' 을 보십시오", out error);
                // 경로(exePath)로 복사하지 않는다 — 쥔 핸들의 바이트를 관리자 전용 폴더에 쓰고 해시를 견준다. 이미 같으면 쓰지 않는다
                // (설치본에서 돌고 있으면 같은 바이트다).
                step = "설치본 복사(" + target + ")";
                string cerr;
                if (!CopySelf(target, out cerr)) return Failed("설치본을 만들지 못했습니다(" + target + ")", cerr, out error);
                string sid;
                using (var id = WindowsIdentity.GetCurrent()) sid = id.User.Value;
                var xmlPath = TaskXmlPath(target);
                step = "작업 XML 쓰기(" + xmlPath + ")";
                var pre = Encoding.Unicode.GetPreamble();
                var body = Encoding.Unicode.GetBytes(TaskXml(target, sid));
                var all = new byte[pre.Length + body.Length];
                Buffer.BlockCopy(pre, 0, all, 0, pre.Length);
                Buffer.BlockCopy(body, 0, all, pre.Length, body.Length);
                try
                {
                    // 다 쓴 뒤 읽기만 하는 핸들로 쥔다 — schtasks 가 읽는 동안 남이 바꾸거나 지우지 못하고, schtasks 는 열 수 있다.
                    using (WriteThenHoldForReaders(xmlPath, all))
                    {
                        step = "작업 스케줄러 등록(schtasks /Create)";
                        if (!CreateTask(xmlPath, out error)) return false;
                    }
                }
                finally
                {
                    try { File.Delete(xmlPath); } catch (IOException) { } catch (UnauthorizedAccessException) { }
                }
                if (note != null) note("켰습니다 — 작업 " + TaskName + " → " + target);
                return true;
            }
            catch (Exception ex)
            {
                return Failed(step + " 중 오류", ex.Message, out error);
            }
        }

        public bool DisableStartup(out string error)
        {
            error = null;
            if (!DeleteTask(out error)) return false;
            if (note != null) note("껐습니다 — 작업 " + TaskName);
            try
            {
                var target = Target;
                string me;
                using (var cur = Process.GetCurrentProcess()) me = cur.MainModule.FileName;
                if (File.Exists(target) && !string.Equals(me, target, StringComparison.OrdinalIgnoreCase))
                {
                    SecureDir.CheckNoReparse(Path.GetDirectoryName(Path.GetDirectoryName(target)), target);
                    File.Delete(target);
                    Directory.Delete(Path.GetDirectoryName(target));
                }
            }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
            return true;
        }

        public bool StartupEnabled()
        {
            return TaskExists() == true;
        }

        /// <summary>
        /// 작업이 있는가 — schtasks /Query(읽기만). 0 이면 있음, 다른 끝 코드면 없음(없는 이름은 1 — 관리자로 도는 도우미에게 다른 까닭의
        /// 실패는 드물다고 보았다), 시간 초과·띄우지 못함은 모름(null).
        /// </summary>
        public bool? TaskExists()
        {
            string output;
            int code;
            try { code = RunTool("schtasks.exe", "/Query /TN " + TaskName, 10000, out output); }
            catch (Exception) { return null; }
            if (code == 0) return true;
            if (code == TimedOut) return null;
            return false;
        }

        /// <summary>쥔 핸들의 바이트를 사본 자리로(Installer.CopyVerified — 같으면 쓰지 않는다). 사본이 돌고 있어 실패했으면 그렇게 적는다.</summary>
        bool CopySelf(string target, out string error)
        {
            var notes = new List<string>();
            bool ok;
            lock (copyLock) ok = Installer.CopyVerified(self.Stream, target, Policy, notes, out error);
            if (note != null) foreach (var n in notes) note(n);
            if (!ok)
            {
                bool running = false;
                try { running = IsRunning(target); } catch (Exception) { }
                if (running) error = StartupCopy.OldRunningShort + " (" + error + ")";
            }
            return ok;
        }

        static string HashOf(string path)
        {
            using (var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read)) return Binaries.Sha256(fs);
        }

        /// <summary>
        /// 작업이 우리 사본을 돌리는가(schtasks /Query /XML — 읽기만, Exec 명령 하나가 Target), 지금 도는 것이 사본이 아니고, 내용이 다른가.
        /// 다르면 사본이 다른 프로세스로 돌고 있는지도 본다. 던지지 않는다(모르면 Unknown).
        /// </summary>
        public CopyCheck CheckStartupCopy()
        {
            var c = new CopyCheck { Status = CopyStatus.Unknown };
            try
            {
                var target = Target;
                if (self == null || self.Stream == null) { c.Detail = "이 exe 를 쥐지 못함"; return c; }
                if (StartupCopy.SamePath(self.Path, target)) { c.Status = CopyStatus.RunningInstalled; return c; }
                string output;
                int code;
                try { code = RunTool("schtasks.exe", "/Query /TN " + TaskName + " /XML", 10000, out output); }
                catch (Exception ex) { c.Detail = "작업을 묻지 못함: " + ex.Message; return c; }
                if (code == TimedOut) { c.Detail = "작업 물음이 시간 안에 끝나지 않음"; return c; }
                if (code != 0) { c.Status = CopyStatus.Off; c.Detail = "작업 없음(코드 " + code + ")"; return c; }
                var cmds = StartupCopy.TaskCommands(output);
                if (cmds == null) { c.Detail = "작업 XML 을 읽지 못함"; return c; }
                if (cmds.Count != 1 || !StartupCopy.SamePath(cmds[0], target))
                {
                    c.Status = CopyStatus.OtherTarget;
                    c.Detail = "작업이 " + target + " 가 아닌 것을 돌림";
                    return c;
                }
                string want;
                lock (copyLock)
                {
                    self.Stream.Position = 0;
                    want = Binaries.Sha256(self.Stream);
                }
                if (!File.Exists(target)) { c.Status = CopyStatus.Stale; c.Missing = true; }
                else c.Status = HashOf(target) == want ? CopyStatus.Same : CopyStatus.Stale;
                if (c.Status == CopyStatus.Stale)
                {
                    try { c.InstalledRunning = IsRunning(target); }
                    catch (Exception ex) { c.Detail = "사본이 돌고 있는지 보지 못함: " + ex.Message; }
                }
                return c;
            }
            catch (Exception ex)
            {
                c.Status = CopyStatus.Unknown;
                c.Detail = ex.Message;
                return c;
            }
        }

        /// <summary>사본을 이 판으로 — '시작 시 실행' 을 켤 때와 같은 복사(CopySelf). 작업은 그대로(경로가 같다). 사본이 돌고 있으면 하지 않는다.</summary>
        public bool RefreshStartupCopy(out string error)
        {
            error = null;
            var target = Target;
            try
            {
                if (self == null || self.Stream == null)
                    return Failed("시작할 때 이 exe 를 쥐지 못해 복사하지 않습니다", "로그의 '이 exe 를 쥐지 못함' 을 보십시오", out error);
                if (StartupCopy.SamePath(self.Path, target)) return Failed("지금 도는 것이 사본입니다", null, out error);
                if (IsRunning(target)) return Failed(StartupCopy.OldRunningShort, null, out error);
                string cerr;
                if (!CopySelf(target, out cerr)) return Failed("사본을 바꾸지 못했습니다(" + target + ")", cerr, out error);
                if (note != null) note("사본을 이 판으로 바꿨습니다 — " + target + " (작업은 그대로)");
                return true;
            }
            catch (Exception ex)
            {
                return Failed("사본을 바꾸지 못했습니다(" + target + ")", ex.Message, out error);
            }
        }

        /// <summary>작업 등록(schtasks /Create /XML). 시간 초과면 한 번 물어 실제 상태로 맞춘다(Settle).</summary>
        public bool CreateTask(string xmlPath, out string error)
        {
            string output;
            int code = RunTool("schtasks.exe", "/Create /TN " + TaskName + " /XML " + CommandLine.Quote(xmlPath) + " /F", 20000, out output);
            return Settle("작업 스케줄러 등록(schtasks /Create)", code, output, true, out error);
        }

        /// <summary>작업 지우기(schtasks /Delete). 실패·시간 초과면 한 번 물어 실제 상태로 맞춘다 — 이미 없으면 꺼진 것이다.</summary>
        public bool DeleteTask(out string error)
        {
            string output;
            int code = RunTool("schtasks.exe", "/Delete /TN " + TaskName + " /F", 20000, out output);
            return Settle("작업 스케줄러에서 지우기(schtasks /Delete)", code, output, false, out error);
        }

        /// <summary>
        /// schtasks 가 끝나지 않았을 때(시간 초과 — 끊었지만 작업 스케줄러는 그 요청을 마쳤을 수 있다) 작업이 있는지 한 번 물어 바란
        /// 상태(want: 있음 = 켜짐)와 같으면 성공으로 친다 — 설정 칸과 작업이 어긋나지 않게. 지우기는 시간 초과가 아닌 실패도 묻는다(이미
        /// 없으면 된 것). 등록의 시간 초과가 아닌 실패는 묻지 않고 실패. 물음도 답하지 못하면 실패와 '작업 스케줄러를 확인하라'.
        /// </summary>
        bool Settle(string what, int code, string output, bool want, out string error)
        {
            error = null;
            if (code == 0) return true;
            bool late = code == TimedOut;
            if (!late && want) return Failed(what + " 실패(코드 " + code + ")", output, out error);
            if (late && SettleDelayMs > 0) System.Threading.Thread.Sleep(SettleDelayMs);
            var exists = TaskExists();
            var head = what + (late ? " 이 시간 안에 끝나지 않음" : " 실패(코드 " + code + ")");
            if (exists == want)
            {
                if (note != null) note(head + " — 확인하니 작업이 " + (want ? "있어 켜짐으로 둡니다" : "없어 꺼짐으로 둡니다"));
                return true;
            }
            if (exists == null)
                return Failed(head + " — 작업이 있는지도 확인하지 못했습니다. 작업 스케줄러의 " + TaskName + " 를 확인하십시오", output, out error);
            return Failed(head + " — 확인하니 작업이 " + (want ? "없습니다" : "남아 있습니다"), output, out error);
        }
    }
}
