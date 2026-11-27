// 서비스 설치·고치기·제거 — 관리자 판(--install·--uninstall, 트레이가 runas 로 띄운다)만 부른다. 시스템을 바꾸는 일은 모두
// 인터페이스(IServiceManager·IRunKey·ToolRunner) 뒤에 있다 — 시험은 가짜로(실제 서비스·Run 키·예약 작업을 건드리지 않는다).
//
// 차례(Install): 돌고 있으면 멈춤(서비스가 끊고 걷는다) → 쥔 이 exe 의 바이트를 Program Files\WebTether 로(해시 대조 — SelfImage·
// Installer.CopyVerified) → 옛 '시작 시 실행' 예약 작업이 우리 것이면 지우고 launch.cmd 를 걷는다 → 서비스 만들기·고치기(따옴표로 싼 경로,
// LocalSystem, 지연 자동 시작, 실패하면 다시 시작, DACL) → 트레이 자동 시작(HKLM Run — 보통 권한 프로그램이 명령을 바꾸지 못한다) → 시작.
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text;
using System.Xml;

namespace WebTether
{
    public enum ServiceState { Missing, Stopped, Running, Pending, Unknown }

    public sealed class ServiceSpec
    {
        public string Name, DisplayName, Description, ImagePath, Sddl;
    }

    public interface IServiceManager
    {
        ServiceState Query(string name);
        /// <summary>멈추고 멈출 때까지 기다린다(시한). 없으면 참.</summary>
        bool Stop(string name, int timeoutMs, out string error);
        /// <summary>없으면 만들고 있으면 설정을 바꾼다 — 경로·계정·시작 방식·설명·지연 시작·실패 동작·DACL 까지.</summary>
        bool CreateOrUpdate(ServiceSpec spec, out string error);
        bool Start(string name, out string error);
        bool Delete(string name, out string error);
    }

    public interface IRunKey
    {
        string Get(string name);
        bool Set(string name, string value, out string error);
        bool Delete(string name, out string error);
    }

    public static class ServicePlan
    {
        public const string Name = WebTetherService.Name;
        public const string DisplayName = "Web 테더 도우미";
        public const string Description = "폰의 Wi-Fi Direct 핫스팟으로 이 PC 의 트래픽을 넘깁니다(tun2proxy).";
        public const string RunValueName = "WebTether";
        /// <summary>
        /// 서비스 DACL — SYSTEM·Administrators 모두, 대화형·인증된 사용자는 상태·설정 보기만(시작 RP·멈춤 WP·설정 DC·사용자 정의 제어 CR 없음).
        /// </summary>
        public const string Sddl = "D:(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;SY)(A;;CCDCLCSWRPWPDTLOCRSDRCWDWO;;;BA)(A;;CCLCSWLORC;;;IU)(A;;CCLCSWLORC;;;AU)";

        /// <summary>서비스의 실행 경로 — 따옴표로 싼다(따옴표 없는 경로 취약점). 경로에 따옴표·%·제어 글자가 있거나 온전하지 않으면 던진다.</summary>
        public static string ImagePath(string exe)
        {
            CheckPath(exe);
            return "\"" + exe + "\" --service";
        }

        /// <summary>트레이 자동 시작 값 — 따옴표로 싼 exe 하나(인자 없음).</summary>
        public static string RunValue(string exe)
        {
            CheckPath(exe);
            return "\"" + exe + "\"";
        }

        static void CheckPath(string p)
        {
            if (string.IsNullOrEmpty(p) || !Path.IsPathRooted(p) || p.StartsWith(@"\\", StringComparison.Ordinal) ||
                p.IndexOf('"') >= 0 || p.IndexOf('%') >= 0 || !p.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                throw new ArgumentException("서비스 경로로 쓸 수 없습니다: " + p);
            foreach (var c in p) if (char.IsControl(c)) throw new ArgumentException("경로에 제어 글자가 있습니다");
        }

        public static ServiceSpec Spec(string exe)
        {
            return new ServiceSpec { Name = Name, DisplayName = DisplayName, Description = Description, ImagePath = ImagePath(exe), Sddl = Sddl };
        }

        /// <summary>설치본 자리 — Program Files\WebTether\WebTether.exe(관리자만 쓴다).</summary>
        public static string InstalledExe
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "WebTether", "WebTether.exe"); }
        }
    }

    /// <summary>
    /// 옛 '시작 시 실행' 예약 작업(이 판 전 — 로그온 때 관리자로 exe 를 곧바로, 또는 conhost → launch.cmd 로 띄우던 것). 서비스로 옮기며 우리 것이면
    /// 지운다(남의 작업이면 그대로 둔다).
    /// </summary>
    public static class LegacyTask
    {
        public const string TaskName = "WebTether";

        public sealed class Exec
        {
            public string Command, Arguments, WorkingDirectory;
        }

        /// <summary>작업 XML(schtasks /Query /XML 의 출력)의 Exec 들. 읽지 못하면 null. DTD 는 받지 않는다.</summary>
        public static List<Exec> Execs(string output)
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
                var r = new List<Exec>();
                foreach (XmlNode n in doc.SelectNodes("//*[local-name()='Actions']/*[local-name()='Exec']"))
                {
                    var e = new Exec();
                    foreach (XmlNode k in n.ChildNodes)
                    {
                        if (k.NodeType != XmlNodeType.Element) continue;
                        if (k.LocalName == "Command") e.Command = k.InnerText;
                        else if (k.LocalName == "Arguments") e.Arguments = k.InnerText;
                        else if (k.LocalName == "WorkingDirectory") e.WorkingDirectory = k.InnerText;
                    }
                    r.Add(e);
                }
                return r;
            }
            catch (XmlException) { return null; }
            catch (System.Xml.XPath.XPathException) { return null; }
        }

        static bool SamePath(string a, string b)
        {
            if (string.IsNullOrWhiteSpace(a) || string.IsNullOrWhiteSpace(b)) return false;
            a = a.Trim();
            if (a.Length >= 2 && a[0] == '"' && a[a.Length - 1] == '"') a = a.Substring(1, a.Length - 2);
            if (a.IndexOf('"') >= 0 || !Path.IsPathRooted(a)) return false;
            try { return string.Equals(Path.GetFullPath(a).TrimEnd('\\'), Path.GetFullPath(b).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase); }
            catch (Exception) { return false; }
        }

        /// <summary>
        /// 우리 것인가 — Exec 하나가 설치본 exe 를 인자 없이(11-21~) 또는 System32 의 conhost 로 설치 폴더의 .\launch.cmd 를(11-27) 돌린다.
        /// 그 밖은 남의 것.
        /// </summary>
        public static bool IsOurs(List<Exec> execs, string installedExe)
        {
            if (execs == null || execs.Count != 1) return false;
            var e = execs[0];
            if (SamePath(e.Command, installedExe) && string.IsNullOrWhiteSpace(e.Arguments)) return true;
            var sys = Environment.GetFolderPath(Environment.SpecialFolder.System);
            return SamePath(e.Command, Path.Combine(sys, "conhost.exe")) && (e.Arguments ?? "").Trim().EndsWith(" /c .\\launch.cmd", StringComparison.Ordinal) &&
                   SamePath(e.WorkingDirectory, Path.GetDirectoryName(installedExe));
        }
    }

    /// <summary>설치·제거의 차례(시험은 가짜 IServiceManager·IRunKey·ToolRunner 와 스크래치 경로로).</summary>
    public sealed class ServiceInstaller
    {
        readonly IServiceManager scm;
        readonly IRunKey run;
        readonly ToolRunner tool;
        readonly Action<string> note;
        /// <summary>설치본 자리(시험은 스크래치).</summary>
        public string Target = ServicePlan.InstalledExe;
        public DirPolicy Policy = DirPolicy.AdminOnly();
        public int StopTimeoutMs = 30000;

        public ServiceInstaller(IServiceManager scm, IRunKey run, ToolRunner tool, Action<string> note)
        {
            this.scm = scm;
            this.run = run;
            this.tool = tool;
            this.note = note;
        }

        void Note(string s) { if (note != null) note(s); }

        bool Fail(string step, string detail, out string error)
        {
            error = step + (string.IsNullOrEmpty(detail) ? "" : ": " + detail);
            Note("실패 — " + error);
            return false;
        }

        /// <summary>held: 쥔 이 exe(SelfImage.Stream) — 경로로 복사하지 않는다.</summary>
        public bool Install(Stream held, out string error)
        {
            error = null;
            var state = scm.Query(ServicePlan.Name);
            if (state == ServiceState.Running || state == ServiceState.Pending)
            {
                string e;
                if (!scm.Stop(ServicePlan.Name, StopTimeoutMs, out e)) return Fail("돌고 있는 서비스를 멈추지 못했습니다", e, out error);
                Note("돌던 서비스를 멈췄습니다(끊고 걷음)");
            }
            var notes = new List<string>();
            string cerr;
            if (!Installer.CopyVerified(held, Target, Policy, notes, out cerr))
            {
                foreach (var n in notes) Note(n);
                return Fail("설치본을 만들지 못했습니다(" + Target + ")", cerr, out error);
            }
            foreach (var n in notes) Note(n);
            RemoveLegacy();
            string se;
            if (!scm.CreateOrUpdate(ServicePlan.Spec(Target), out se)) return Fail("서비스를 만들지 못했습니다", se, out error);
            string re;
            if (!run.Set(ServicePlan.RunValueName, ServicePlan.RunValue(Target), out re)) Note("트레이 자동 시작을 쓰지 못했습니다: " + re);
            string st;
            if (!scm.Start(ServicePlan.Name, out st)) return Fail("서비스를 시작하지 못했습니다", st, out error);
            Note("설치했습니다 — 서비스 " + ServicePlan.Name + " → " + Target);
            return true;
        }

        /// <summary>옛 '시작 시 실행' 예약 작업이 우리 것이면 지우고 설치 폴더의 launch.cmd·launch-env.txt 를 걷는다(실패는 적기만).</summary>
        void RemoveLegacy()
        {
            string output;
            int code;
            try { code = tool("schtasks.exe", "/Query /TN " + LegacyTask.TaskName + " /XML", 10000, out output); }
            catch (Exception ex) { Note("옛 예약 작업을 묻지 못함: " + ex.Message); return; }
            if (code == 0)
            {
                var execs = LegacyTask.Execs(output);
                if (LegacyTask.IsOurs(execs, Target))
                {
                    string o;
                    int d = tool("schtasks.exe", "/Delete /TN " + LegacyTask.TaskName + " /F", 20000, out o);
                    Note(d == 0 ? "옛 '시작 시 실행' 예약 작업을 지웠습니다" : "옛 예약 작업을 지우지 못했습니다(코드 " + d + ") — 작업 스케줄러의 " + LegacyTask.TaskName + " 를 지우십시오");
                }
                else Note("예약 작업 " + LegacyTask.TaskName + " 가 우리 것이 아니라 그대로 둡니다");
            }
            var dir = Path.GetDirectoryName(Target);
            foreach (var name in new[] { "launch.cmd", "launch-env.txt" })
            {
                var f = Path.Combine(dir, name);
                try
                {
                    SecureDir.CheckNoReparse(Path.GetDirectoryName(dir), f);
                    if (File.Exists(f)) { File.Delete(f); Note("걷음: " + f); }
                }
                catch (Exception ex) { Note(name + " 을 걷지 못함: " + ex.Message); }
            }
        }

        /// <summary>
        /// 제거 — 멈춤(끊고 걷음) → 서비스 지우기 → Run 값 → 설치본(지금 도는 exe 면 다음 재부팅 때) → removeData 면 상태 폴더도.
        /// </summary>
        public bool Uninstall(bool removeData, string dataRoot, out string error)
        {
            error = null;
            string e;
            if (scm.Query(ServicePlan.Name) != ServiceState.Missing)
            {
                if (!scm.Stop(ServicePlan.Name, StopTimeoutMs, out e)) return Fail("서비스를 멈추지 못했습니다", e, out error);
                if (!scm.Delete(ServicePlan.Name, out e)) return Fail("서비스를 지우지 못했습니다", e, out error);
                Note("서비스를 지웠습니다");
            }
            if (run.Get(ServicePlan.RunValueName) != null && !run.Delete(ServicePlan.RunValueName, out e)) Note("트레이 자동 시작을 지우지 못했습니다: " + e);
            var dir = Path.GetDirectoryName(Target);
            try
            {
                SecureDir.CheckNoReparse(Path.GetDirectoryName(dir), Target);
                if (File.Exists(Target))
                {
                    try { File.Delete(Target); }
                    catch (Exception)
                    {
                        // 지금 도는 exe(설치본에서 띄운 제거기)는 지울 수 없다 — 다음 재부팅 때.
                        if (!DelayDelete(Target)) throw;
                        Note("설치본은 다음 재부팅 때 지웁니다: " + Target);
                    }
                }
                if (Directory.Exists(dir) && Directory.GetFileSystemEntries(dir).Length == 0) Directory.Delete(dir);
                else if (Directory.Exists(dir)) DelayDelete(dir);
            }
            catch (Exception ex) { Note("설치 폴더를 지우지 못함: " + ex.Message); }
            if (removeData && dataRoot != null && Directory.Exists(dataRoot))
            {
                try
                {
                    // 주인·권한이 정책에 맞을 때만 지운다 — 남이 미리 만들었거나 권한을 넓혀 둔 폴더(안에 접합점을 심었을 수 있다)는 옆으로 비키기만.
                    string aside;
                    var why = SecureDir.DeleteIfTrusted(dataRoot, Policy, out aside);
                    if (why != null) Note("상태 폴더를 지우지 못함: " + why);
                    else if (aside != null) Note("상태 폴더의 주인·권한이 예상과 달라 지우지 않고 옮겼습니다: " + aside);
                    else Note("상태 폴더를 지웠습니다: " + dataRoot);
                }
                catch (Exception ex) { Note("상태 폴더를 지우지 못함: " + ex.Message); }
            }
            return true;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool MoveFileExW(string from, string to, uint flags);

        /// <summary>시험이 바꾼다 — 다음 재부팅 때 지우기(MOVEFILE_DELAY_UNTIL_REBOOT).</summary>
        public Func<string, bool> DelayDelete = p => MoveFileExW(p, null, 0x4);
    }

    /// <summary>실제 SCM — advapi32 를 바로 부른다(sc.exe 의 인자 가르기를 거치지 않는다).</summary>
    public sealed class ScmManager : IServiceManager
    {
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr OpenSCManagerW(string machine, string db, uint access);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr OpenServiceW(IntPtr scm, string name, uint access);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateServiceW(IntPtr scm, string name, string display, uint access, uint type, uint start, uint error,
                                            string path, string group, IntPtr tag, string deps, string account, string password);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool ChangeServiceConfigW(IntPtr svc, uint type, uint start, uint error, string path, string group, IntPtr tag,
                                                string deps, string account, string password, string display);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool ChangeServiceConfig2W(IntPtr svc, uint level, IntPtr info);
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool SetServiceObjectSecurity(IntPtr svc, uint info, byte[] sd);
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool CloseServiceHandle(IntPtr h);
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool QueryServiceStatus(IntPtr svc, out SERVICE_STATUS st);
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool ControlService(IntPtr svc, uint control, out SERVICE_STATUS st);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool StartServiceW(IntPtr svc, uint argc, IntPtr argv);
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool DeleteService(IntPtr svc);

        [StructLayout(LayoutKind.Sequential)]
        struct SERVICE_STATUS { public uint Type, State, Accepted, Exit, SvcExit, Check, Wait; }
        [StructLayout(LayoutKind.Sequential)]
        struct SERVICE_STATUS_PROCESS { public uint Type, State, Accepted, Exit, SvcExit, Check, Wait, ProcessId, Flags; }
        [DllImport("advapi32.dll", SetLastError = true)]
        static extern bool QueryServiceStatusEx(IntPtr svc, int level, out SERVICE_STATUS_PROCESS buf, int size, out int needed);

        const uint SC_MANAGER_ALL = 0xF003F, SERVICE_ALL = 0xF01FF, SERVICE_WIN32_OWN = 0x10, SERVICE_AUTO_START = 0x2, ERROR_NORMAL = 0x1;
        const uint SERVICE_NO_CHANGE = 0xFFFFFFFF, SERVICE_STOPPED = 1, SERVICE_RUNNING = 4, CONTROL_STOP = 1;
        const uint CONFIG_DESCRIPTION = 1, CONFIG_FAILURE_ACTIONS = 2, CONFIG_DELAYED_AUTO_START = 3;
        const uint DACL_SECURITY_INFORMATION = 4;
        const int ERROR_SERVICE_DOES_NOT_EXIST = 1060, ERROR_SERVICE_NOT_ACTIVE = 1062, ERROR_SERVICE_ALREADY_RUNNING = 1056;

        static string Err() { return new Win32Exception(Marshal.GetLastWin32Error()).Message + " (" + Marshal.GetLastWin32Error() + ")"; }

        IntPtr Open(string name, out IntPtr scm)
        {
            scm = OpenSCManagerW(null, null, SC_MANAGER_ALL);
            if (scm == IntPtr.Zero) return IntPtr.Zero;
            return OpenServiceW(scm, name, SERVICE_ALL);
        }

        /// <summary>읽기만 — 보통 권한의 트레이도 부른다(SC_MANAGER_CONNECT·SERVICE_QUERY_STATUS).</summary>
        public static ServiceState QueryReadOnly(string name)
        {
            IntPtr scm = OpenSCManagerW(null, null, 0x1);
            if (scm == IntPtr.Zero) return ServiceState.Unknown;
            IntPtr svc = OpenServiceW(scm, name, 0x4);
            try
            {
                if (svc == IntPtr.Zero) return Marshal.GetLastWin32Error() == ERROR_SERVICE_DOES_NOT_EXIST ? ServiceState.Missing : ServiceState.Unknown;
                SERVICE_STATUS st;
                if (!QueryServiceStatus(svc, out st)) return ServiceState.Unknown;
                return st.State == SERVICE_STOPPED ? ServiceState.Stopped : st.State == SERVICE_RUNNING ? ServiceState.Running : ServiceState.Pending;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); CloseServiceHandle(scm); }
        }

        /// <summary>
        /// 돌고 있는 서비스의 PID — 읽기만(SC_MANAGER_CONNECT·SERVICE_QUERY_STATUS — 서비스 DACL 이 대화형·인증된 사용자에게 준다).
        /// 트레이가 파이프 서버가 정말 그 서비스인지 견준다. 돌고 있지 않거나 모르면 0.
        /// </summary>
        public static int ProcessIdReadOnly(string name)
        {
            IntPtr scm = OpenSCManagerW(null, null, 0x1);
            if (scm == IntPtr.Zero) return 0;
            IntPtr svc = OpenServiceW(scm, name, 0x4);
            try
            {
                if (svc == IntPtr.Zero) return 0;
                SERVICE_STATUS_PROCESS st;
                int needed;
                if (!QueryServiceStatusEx(svc, 0, out st, Marshal.SizeOf(typeof(SERVICE_STATUS_PROCESS)), out needed)) return 0;
                return st.State == SERVICE_RUNNING ? (int)st.ProcessId : 0;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); CloseServiceHandle(scm); }
        }

        public ServiceState Query(string name)
        {
            IntPtr scm, svc = Open(name, out scm);
            try
            {
                if (svc == IntPtr.Zero) return Marshal.GetLastWin32Error() == ERROR_SERVICE_DOES_NOT_EXIST ? ServiceState.Missing : ServiceState.Unknown;
                SERVICE_STATUS st;
                if (!QueryServiceStatus(svc, out st)) return ServiceState.Unknown;
                return st.State == SERVICE_STOPPED ? ServiceState.Stopped : st.State == SERVICE_RUNNING ? ServiceState.Running : ServiceState.Pending;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); if (scm != IntPtr.Zero) CloseServiceHandle(scm); }
        }

        public bool Stop(string name, int timeoutMs, out string error)
        {
            error = null;
            IntPtr scm, svc = Open(name, out scm);
            try
            {
                if (svc == IntPtr.Zero)
                {
                    if (Marshal.GetLastWin32Error() == ERROR_SERVICE_DOES_NOT_EXIST) return true;
                    error = Err();
                    return false;
                }
                SERVICE_STATUS st;
                if (!ControlService(svc, CONTROL_STOP, out st) && Marshal.GetLastWin32Error() != ERROR_SERVICE_NOT_ACTIVE) { error = Err(); return false; }
                var until = DateTime.UtcNow.AddMilliseconds(timeoutMs);
                while (DateTime.UtcNow < until)
                {
                    if (QueryServiceStatus(svc, out st) && st.State == SERVICE_STOPPED) return true;
                    System.Threading.Thread.Sleep(250);
                }
                error = "시한 안에 멈추지 않았습니다";
                return false;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); if (scm != IntPtr.Zero) CloseServiceHandle(scm); }
        }

        public bool CreateOrUpdate(ServiceSpec spec, out string error)
        {
            error = null;
            IntPtr scm = OpenSCManagerW(null, null, SC_MANAGER_ALL);
            if (scm == IntPtr.Zero) { error = Err(); return false; }
            IntPtr svc = IntPtr.Zero;
            try
            {
                svc = OpenServiceW(scm, spec.Name, SERVICE_ALL);
                if (svc == IntPtr.Zero)
                {
                    // 계정 null = LocalSystem.
                    svc = CreateServiceW(scm, spec.Name, spec.DisplayName, SERVICE_ALL, SERVICE_WIN32_OWN, SERVICE_AUTO_START, ERROR_NORMAL,
                                         spec.ImagePath, null, IntPtr.Zero, null, null, null);
                    if (svc == IntPtr.Zero) { error = "만들기: " + Err(); return false; }
                }
                else if (!ChangeServiceConfigW(svc, SERVICE_WIN32_OWN, SERVICE_AUTO_START, ERROR_NORMAL, spec.ImagePath, null, IntPtr.Zero,
                                               null, "LocalSystem", null, spec.DisplayName))
                { error = "고치기: " + Err(); return false; }
                if (!Config2Description(svc, spec.Description)) { error = "설명: " + Err(); return false; }
                if (!Config2Delayed(svc)) { error = "지연 시작: " + Err(); return false; }
                if (!Config2Failure(svc)) { error = "실패 동작: " + Err(); return false; }
                var sd = new System.Security.AccessControl.RawSecurityDescriptor(spec.Sddl);
                var bytes = new byte[sd.BinaryLength];
                sd.GetBinaryForm(bytes, 0);
                if (!SetServiceObjectSecurity(svc, DACL_SECURITY_INFORMATION, bytes)) { error = "DACL: " + Err(); return false; }
                return true;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); CloseServiceHandle(scm); }
        }

        static bool Config2Description(IntPtr svc, string text)
        {
            var p = Marshal.StringToHGlobalUni(text);
            var box = Marshal.AllocHGlobal(IntPtr.Size);
            try { Marshal.WriteIntPtr(box, p); return ChangeServiceConfig2W(svc, CONFIG_DESCRIPTION, box); }
            finally { Marshal.FreeHGlobal(box); Marshal.FreeHGlobal(p); }
        }

        static bool Config2Delayed(IntPtr svc)
        {
            var box = Marshal.AllocHGlobal(4);
            try { Marshal.WriteInt32(box, 1); return ChangeServiceConfig2W(svc, CONFIG_DELAYED_AUTO_START, box); }
            finally { Marshal.FreeHGlobal(box); }
        }

        /// <summary>실패하면 5초·30초·60초 뒤 다시 시작, 하루마다 셈을 처음으로.</summary>
        static bool Config2Failure(IntPtr svc)
        {
            // SERVICE_FAILURE_ACTIONSW: DWORD reset; LPWSTR reboot; LPWSTR command; DWORD cActions; SC_ACTION* actions
            int[] delays = { 5000, 30000, 60000 };
            var actions = Marshal.AllocHGlobal(8 * delays.Length);
            var fa = Marshal.AllocHGlobal(IntPtr.Size * 5);
            try
            {
                for (int i = 0; i < delays.Length; i++)
                {
                    Marshal.WriteInt32(actions, i * 8, 1); // SC_ACTION_RESTART
                    Marshal.WriteInt32(actions, i * 8 + 4, delays[i]);
                }
                int off = 0;
                Marshal.WriteInt32(fa, off, 86400); off += IntPtr.Size;
                Marshal.WriteIntPtr(fa, off, IntPtr.Zero); off += IntPtr.Size;
                Marshal.WriteIntPtr(fa, off, IntPtr.Zero); off += IntPtr.Size;
                Marshal.WriteInt32(fa, off, delays.Length); off += IntPtr.Size;
                Marshal.WriteIntPtr(fa, off, actions);
                return ChangeServiceConfig2W(svc, CONFIG_FAILURE_ACTIONS, fa);
            }
            finally { Marshal.FreeHGlobal(fa); Marshal.FreeHGlobal(actions); }
        }

        public bool Start(string name, out string error)
        {
            error = null;
            IntPtr scm, svc = Open(name, out scm);
            try
            {
                if (svc == IntPtr.Zero) { error = Err(); return false; }
                if (!StartServiceW(svc, 0, IntPtr.Zero) && Marshal.GetLastWin32Error() != ERROR_SERVICE_ALREADY_RUNNING) { error = Err(); return false; }
                return true;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); if (scm != IntPtr.Zero) CloseServiceHandle(scm); }
        }

        public bool Delete(string name, out string error)
        {
            error = null;
            IntPtr scm, svc = Open(name, out scm);
            try
            {
                if (svc == IntPtr.Zero) { if (Marshal.GetLastWin32Error() == ERROR_SERVICE_DOES_NOT_EXIST) return true; error = Err(); return false; }
                if (!DeleteService(svc)) { error = Err(); return false; }
                return true;
            }
            finally { if (svc != IntPtr.Zero) CloseServiceHandle(svc); if (scm != IntPtr.Zero) CloseServiceHandle(scm); }
        }
    }

    /// <summary>HKLM\Software\Microsoft\Windows\CurrentVersion\Run(64비트 보기) — 관리자만 쓴다.</summary>
    public sealed class HklmRunKey : IRunKey
    {
        const string Path = @"Software\Microsoft\Windows\CurrentVersion\Run";

        static Microsoft.Win32.RegistryKey Base()
        {
            return Microsoft.Win32.RegistryKey.OpenBaseKey(Microsoft.Win32.RegistryHive.LocalMachine, Microsoft.Win32.RegistryView.Registry64);
        }

        public string Get(string name)
        {
            using (var b = Base())
            using (var k = b.OpenSubKey(Path, false)) return k == null ? null : k.GetValue(name) as string;
        }

        public bool Set(string name, string value, out string error)
        {
            error = null;
            try
            {
                using (var b = Base())
                using (var k = b.OpenSubKey(Path, true)) k.SetValue(name, value, Microsoft.Win32.RegistryValueKind.String);
                return true;
            }
            catch (Exception ex) { error = ex.Message; return false; }
        }

        public bool Delete(string name, out string error)
        {
            error = null;
            try
            {
                using (var b = Base())
                using (var k = b.OpenSubKey(Path, true)) k.DeleteValue(name, false);
                return true;
            }
            catch (Exception ex) { error = ex.Message; return false; }
        }
    }

    /// <summary>
    /// CLR 이 관리 코드보다 먼저 읽는 환경 변수(프로파일러·설정·AppDomainManager·호환성 층). 트레이가 관리자 판을 runas 로 띄우기 직전에 제
    /// 프로세스에서 지운다 — 관리자 판이 트레이의 환경 블록을 물려받는다면(확인하지 못했다 — 그래서 Elevation.Precheck 도 둔다. 그쪽은 좁은 목록)(같은 사용자의 프로그램이 HKCU\Environment 에 둔 COR_PROFILER 따위가
    /// 관리자 .NET 프로세스에 DLL 을 올리지 않게 — T1574.012). 트레이 자신은 이미 떠 있어 해가 없다. HKCU\Software\Microsoft\.NETFramework 의
    /// 설정 값은 막지 못한다(남는 위험).
    /// </summary>
    public static class ClrEnv
    {
        /// <summary>
        /// 지우는 이름(Scrub) — 넓게: .NET Framework 4.8 에 닿는 것(FrameworkPrefixes)에 .NET(Core)의 CORECLR_·DOTNET_ 까지. 트레이 자신의 환경에서
        /// 지울 뿐이라 넓어도 해가 없다(DOTNET_CLI_TELEMETRY_OPTOUT 같은 것은 관리자 판에 쓸모가 없다).
        /// </summary>
        public static readonly string[] Prefixes = { "COR_", "COMPlus_", "CORECLR_", "DOTNET_", "APPDOMAIN_MANAGER_", "__COMPAT_LAYER" };
        /// <summary>
        /// 올리기를 멈추는 이름(Elevation.Precheck) — 좁게: .NET Framework 4.8 의 CLR·셔림이 실제로 읽는 것만(COR_ 프로파일러, COMPlus_ 설정,
        /// AppDomainManager, 호환성 층). DOTNET_·CORECLR_ 는 Framework 에 뜻이 없다(검토 11-27 — 그런 변수를 둔 개발자 PC 에서 설치가 막혔다).
        /// </summary>
        public static readonly string[] FrameworkPrefixes = { "COR_", "COMPlus_", "APPDOMAIN_MANAGER_", "__COMPAT_LAYER" };

        public static bool IsRisky(string name) { return Starts(name, Prefixes); }

        public static bool IsRiskyForFramework(string name) { return Starts(name, FrameworkPrefixes); }

        static bool Starts(string name, string[] prefixes)
        {
            if (string.IsNullOrEmpty(name)) return false;
            foreach (var p in prefixes) if (name.StartsWith(p, StringComparison.OrdinalIgnoreCase)) return true;
            return false;
        }

        /// <summary>이 프로세스의 환경에서 지운다 — 지운 이름들.</summary>
        public static List<string> Scrub()
        {
            var gone = new List<string>();
            foreach (System.Collections.DictionaryEntry e in Environment.GetEnvironmentVariables())
            {
                var k = e.Key as string;
                if (!IsRisky(k)) continue;
                Environment.SetEnvironmentVariable(k, null);
                gone.Add(k);
            }
            return gone;
        }
    }

    /// <summary>관리자 판을 따로 띄운다(UAC). 트레이(보통 권한)가 부른다 — 끝날 때까지 기다려 종료 코드를 돌려준다(-1 = 띄우지 못함·거절).</summary>
    public static class Elevation
    {
        /// <summary>실제로 띄우는 곳(시험이 가짜로 바꾼다 — 실제 runas 는 시험하지 않는다).</summary>
        public static Func<string, string, int> Launch = RunAs;
        /// <summary>HKCU 아래 키의 값 이름들(읽기만 — 없으면 null). 시험이 가짜로 바꾼다(실제 HKCU 에 쓰지 않는다).</summary>
        public static Func<string, string[]> UserValueNames = ReadUserValueNames;
        /// <summary>HKCU 아래 키의 하위 키 이름들(읽기만 — 없으면 null). 시험이 가짜로 바꾼다.</summary>
        public static Func<string, string[]> UserSubKeyNames = ReadUserSubKeyNames;
        public const string EnvironmentKey = "Environment", VolatileKey = "Volatile Environment", NetFrameworkKey = @"Software\Microsoft\.NETFramework";
        /// <summary>마지막으로 올리기를 멈춘 까닭(Run 이 -2 를 돌려줄 때).</summary>
        public static string LastBlocked;

        /// <summary>
        /// 올리기 전에 — 관리자 판이 사용자의 환경을 물려받는지는 확인하지 못했다(UAC 가 사용자 프로필로 환경을 새로 지을 수도 있다). 그래서
        /// 그 바탕에 .NET Framework 에 닿는 것이 있으면 올리지 않는다 — HKCU\Environment 와 HKCU\Volatile Environment(와 그 세션 번호 하위 키 —
        /// 로그온 때 환경 블록에 들어간다)의 COR_·COMPlus_·APPDOMAIN_MANAGER_·__COMPAT_LAYER 변수(ClrEnv.FrameworkPrefixes), 그리고
        /// HKCU\Software\Microsoft\.NETFramework 의 값(CLR 이 같은 이름으로 읽는 설정 — 이름을 가리지 않고 하나라도 있으면: 보통 비어 있다).
        /// 읽기만 한다. 없으면 null, 있으면 짧은 까닭. **완전하지 않다** — 이 검사와 UAC 창 사이에 같은 사용자의 프로그램이 값을 넣을 수 있다(경합).
        /// </summary>
        public static string Precheck()
        {
            var found = new List<string>();
            var env = UserValueNames(EnvironmentKey);
            if (env != null) foreach (var n in env) if (ClrEnv.IsRiskyForFramework(n)) found.Add(n);
            var vol = UserValueNames(VolatileKey);
            if (vol != null) foreach (var n in vol) if (ClrEnv.IsRiskyForFramework(n)) found.Add(n);
            var sessions = UserSubKeyNames(VolatileKey);
            if (sessions != null)
                foreach (var sub in sessions)
                {
                    var names = UserValueNames(VolatileKey + @"\" + sub);
                    if (names != null) foreach (var n in names) if (ClrEnv.IsRiskyForFramework(n)) found.Add(n);
                }
            var nf = UserValueNames(NetFrameworkKey);
            if (nf != null) foreach (var n in nf) found.Add(".NETFramework" + @"\" + (n.Length == 0 ? "(기본값)" : n));
            if (found.Count == 0) return null;
            var shown = found.Count > 3 ? string.Join(", ", found.GetRange(0, 3).ToArray()) + " 외 " + (found.Count - 3) : string.Join(", ", found.ToArray());
            return "관리자 권한을 쓰지 않았습니다 — 사용자 설정에 .NET 을 바꾸는 값이 있습니다: " + SafeText.Clean(shown, 200);
        }

        static string[] ReadUserValueNames(string key)
        {
            using (var k = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(key, false))
                return k == null ? null : k.GetValueNames();
        }

        static string[] ReadUserSubKeyNames(string key)
        {
            using (var k = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(key, false))
                return k == null ? null : k.GetSubKeyNames();
        }

        public static int Run(string exe, string args)
        {
            string why;
            try { why = Precheck(); }
            catch (Exception ex) { why = "사용자 설정을 확인하지 못해 관리자 권한을 쓰지 않았습니다: " + ex.Message; }
            if (why != null) { LastBlocked = why; return -2; }
            ClrEnv.Scrub();
            return Launch(exe, args);
        }

        static int RunAs(string exe, string args)
        {
            try
            {
                var psi = new System.Diagnostics.ProcessStartInfo(exe, args) { UseShellExecute = true, Verb = "runas" };
                using (var p = System.Diagnostics.Process.Start(psi))
                {
                    if (p == null) return -1;
                    p.WaitForExit();
                    return p.ExitCode;
                }
            }
            catch (Win32Exception) { return -1; } // UAC 거절(1223) 등
        }
    }
}
