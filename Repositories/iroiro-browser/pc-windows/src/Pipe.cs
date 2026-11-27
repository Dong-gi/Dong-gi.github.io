// 서비스(LocalSystem) ↔ 트레이(보통 권한)의 이름 있는 파이프.
//
// 명령은 고정된 작은 집합(PipeCommands)뿐이다 — 연결·끊기·보기·로그 읽기·설정 읽기·설정 제안. 제안은 곧바로 적용되지 않는다 — 서비스가
// 설치본의 트레이에서 온 것만 받아 검사기로 거르고 콘솔 사용자에게 확인 창으로 묻는다(SYSTEM 이 띄우는 tun2proxy 의 명령줄이 되므로 —
// ServiceCore.Propose). 같은 사용자의 보통 권한 프로그램이 이 파이프로 할 수 있는 일은 트레이로 할 수 있는 일과 같다.
//
// 파이프 ACL: SYSTEM·Administrators 모두, INTERACTIVE 읽기·쓰기, NETWORK 거부, 원격 손님 거절. 서버는 손님마다 세션(지금 콘솔 세션)·
// 토큰(대화형·중간 무결성 이상)을 본다(PipeClientCheck). 트레이는 서버가 세션 0 이고 **그 PID 가 SCM 이 아는 서비스의 PID** 인지 본다
// (PipeClient.ServerVerdict) — 세션 0 만으로는 모자란다: 보통 권한 사용자도 S4U 예약 작업 등으로 세션 0 에 프로세스를 띄울 수 있어(검토 11-27),
// 지연 자동 시작의 서비스보다 먼저 이름을 잡은 가짜 서버를 믿게 된다. 서비스는 이름을 잡지 못하면 5초마다 다시 해 본다(ServiceCore).
//
// 메시지: 4 바이트(LE) 길이 + UTF-8 'key=value' 줄들(PipeMessage). 손으로 읽는 엄격한 꼴 — 모르는 꼴·겹친 키·상한 넘음은 오류.
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.IO;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using System.Threading;
using Microsoft.Win32.SafeHandles;

namespace WebTether
{
    public static class PipeNames
    {
        /// <summary>실제 판 — 고정 GUID(이름만으로 남과 겹치지 않게).</summary>
        public const string Service = "WebTether-7d1c2f0e-5b8a-4c39-9e61-3a4f0b2d8c17";
    }

    /// <summary>'key=value' 줄 꼴의 메시지 — 짓기·읽기(순수, 시험한다).</summary>
    public static class PipeMessage
    {
        public const int MaxBytes = 64 * 1024;
        public const int MaxKeys = 64;
        public const int MaxValueChars = 16 * 1024;

        public static bool IsKey(string k)
        {
            if (string.IsNullOrEmpty(k) || k.Length > 32) return false;
            foreach (var c in k) if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return false;
            return true;
        }

        /// <summary>값의 역슬래시·줄바꿈·CR 을 \\·\n·\r 로 — 줄 하나에 담는다.</summary>
        static string Escape(string v)
        {
            var sb = new StringBuilder(v.Length);
            foreach (var c in v)
            {
                if (c == '\\') sb.Append("\\\\");
                else if (c == '\n') sb.Append("\\n");
                else if (c == '\r') sb.Append("\\r");
                else sb.Append(c);
            }
            return sb.ToString();
        }

        static string Unescape(string v)
        {
            var sb = new StringBuilder(v.Length);
            for (int i = 0; i < v.Length; i++)
            {
                char c = v[i];
                if (c != '\\') { sb.Append(c); continue; }
                if (++i >= v.Length) throw new FormatException("끝의 역슬래시");
                char n = v[i];
                if (n == '\\') sb.Append('\\');
                else if (n == 'n') sb.Append('\n');
                else if (n == 'r') sb.Append('\r');
                else throw new FormatException("모르는 이스케이프");
            }
            return sb.ToString();
        }

        public static byte[] Encode(IDictionary<string, string> m)
        {
            if (m.Count > MaxKeys) throw new ArgumentException("키가 너무 많습니다");
            var sb = new StringBuilder();
            foreach (var kv in m)
            {
                if (!IsKey(kv.Key)) throw new ArgumentException("틀린 키: " + kv.Key);
                var v = kv.Value ?? "";
                if (v.Length > MaxValueChars) v = v.Substring(0, MaxValueChars);
                sb.Append(kv.Key).Append('=').Append(Escape(v)).Append('\n');
            }
            var body = new UTF8Encoding(false).GetBytes(sb.ToString());
            if (body.Length > MaxBytes) throw new ArgumentException("메시지가 너무 깁니다");
            var all = new byte[4 + body.Length];
            BitConverter.GetBytes(body.Length).CopyTo(all, 0);
            Buffer.BlockCopy(body, 0, all, 4, body.Length);
            return all;
        }

        /// <summary>몸(길이 머리 뒤)을 읽는다. 틀린 꼴이면 FormatException.</summary>
        public static Dictionary<string, string> Decode(byte[] body)
        {
            if (body == null || body.Length > MaxBytes) throw new FormatException("길이");
            string text;
            try { text = new UTF8Encoding(false, true).GetString(body); }
            catch (ArgumentException) { throw new FormatException("UTF-8 이 아님"); }
            var m = new Dictionary<string, string>(StringComparer.Ordinal);
            if (text.Length == 0) return m;
            if (text[text.Length - 1] != '\n') throw new FormatException("줄 끝이 없음");
            foreach (var line in text.Substring(0, text.Length - 1).Split('\n'))
            {
                int eq = line.IndexOf('=');
                if (eq <= 0) throw new FormatException("'=' 없음");
                var k = line.Substring(0, eq);
                if (!IsKey(k)) throw new FormatException("틀린 키");
                if (m.ContainsKey(k)) throw new FormatException("겹친 키");
                if (m.Count >= MaxKeys) throw new FormatException("키가 너무 많음");
                var v = Unescape(line.Substring(eq + 1));
                if (v.Length > MaxValueChars) throw new FormatException("값이 너무 김");
                m[k] = v;
            }
            return m;
        }

        /// <summary>길이 머리 + 몸을 읽는다(끝까지 못 읽으면 IOException, 상한 넘음은 FormatException).</summary>
        public static Dictionary<string, string> Read(Stream s)
        {
            var head = ReadExactly(s, 4);
            int len = BitConverter.ToInt32(head, 0);
            if (len < 0 || len > MaxBytes) throw new FormatException("길이 " + len);
            return Decode(ReadExactly(s, len));
        }

        public static void Write(Stream s, IDictionary<string, string> m)
        {
            var b = Encode(m);
            s.Write(b, 0, b.Length);
            s.Flush();
        }

        static byte[] ReadExactly(Stream s, int n)
        {
            var b = new byte[n];
            int got = 0, k;
            while (got < n && (k = s.Read(b, got, n - got)) > 0) got += k;
            if (got != n) throw new IOException("메시지가 잘렸습니다");
            return b;
        }
    }

    /// <summary>명령표 — 이 밖은 없다. 인자를 받는 것은 log 의 after 하나(정수).</summary>
    public static class PipeCommands
    {
        public const string Hello = "hello", Status = "status", Connect = "connect", Disconnect = "disconnect", Log = "log";
        /// <summary>설정 읽기(set_* 로 지금 설정 전체)와 설정 바꾸기 제안(set_* 로 새 설정 전체 — 서비스가 콘솔 사용자에게 확인 창으로 묻는다).</summary>
        public const string Settings = "settings", Propose = "propose";
        public const string SettingPrefix = "set_";
        public const int ProtocolVersion = 1;
        static readonly string[] All = { Hello, Status, Connect, Disconnect, Log, Settings, Propose };

        /// <summary>요청이 받을 만한가 — 받으면 null, 아니면 까닭. 제안의 값 자체는 서비스가 Settings 검사기로 다시 본다.</summary>
        public static string Validate(IDictionary<string, string> req)
        {
            string v, cmd;
            if (!req.TryGetValue("v", out v) || v != ProtocolVersion.ToString(CultureInfo.InvariantCulture)) return "판이 다릅니다";
            if (!req.TryGetValue("cmd", out cmd) || Array.IndexOf(All, cmd) < 0) return "모르는 명령";
            foreach (var k in req.Keys)
            {
                if (k == "v" || k == "cmd") continue;
                if (cmd == Log && k == "after")
                {
                    long n;
                    if (!long.TryParse(req[k], NumberStyles.None, CultureInfo.InvariantCulture, out n)) return "after 가 수가 아님";
                    continue;
                }
                if (cmd == Propose && k.StartsWith(SettingPrefix, StringComparison.Ordinal) &&
                    Array.IndexOf(WebTether.Settings.Keys, k.Substring(SettingPrefix.Length)) >= 0) continue;
                return "받지 않는 인자: " + k;
            }
            return null;
        }

        public static Dictionary<string, string> Request(string cmd)
        {
            return new Dictionary<string, string> { { "v", ProtocolVersion.ToString(CultureInfo.InvariantCulture) }, { "cmd", cmd } };
        }
    }

    /// <summary>파이프 손님의 신원(서버가 본 것).</summary>
    public sealed class PipeClientInfo
    {
        public int Pid;
        public int SessionId = -1;
        public string Sid = "";
        public bool Interactive, Network, SystemOrAdmin;
        /// <summary>무결성 수준의 RID(낮음 0x1000·중간 0x2000·높음 0x3000·시스템 0x4000). 모르면 0.</summary>
        public int Integrity;
        public string Image = "";

        public override string ToString()
        {
            return "pid " + Pid + " · 세션 " + SessionId + " · " + Sid + " · 무결성 0x" + Integrity.ToString("X", CultureInfo.InvariantCulture) +
                   (Interactive ? " · 대화형" : "") + (Network ? " · 망" : "") + (SystemOrAdmin ? " · 관리자" : "");
        }
    }

    public static class PipeClientCheck
    {
        public const int MediumIntegrity = 0x2000;

        /// <summary>받으면 null, 아니면 까닭(순수 — 시험한다). consoleSession: 지금 콘솔 세션(없으면 -1).</summary>
        public static string Verdict(PipeClientInfo c, int consoleSession)
        {
            if (c == null) return "손님을 모릅니다";
            if (c.Network) return "망 로그온의 손님";
            if (c.Sid == "S-1-5-18") return null; // SYSTEM(설치기·서비스 자신)
            if (c.Integrity < MediumIntegrity) return "무결성이 낮은 손님";
            if (consoleSession < 0 || c.SessionId != consoleSession) return "콘솔 세션이 아닌 손님(세션 " + c.SessionId + ")";
            if (!c.Interactive) return "대화형 로그온이 아닌 손님";
            return null;
        }
    }

    /// <summary>파이프의 보안 설명자 — SYSTEM·Administrators 모두, INTERACTIVE 읽기·쓰기, NETWORK 거부.</summary>
    public static class PipeAcl
    {
        public static readonly SecurityIdentifier Interactive = new SecurityIdentifier(WellKnownSidType.InteractiveSid, null);
        public static readonly SecurityIdentifier NetworkSid = new SecurityIdentifier(WellKnownSidType.NetworkSid, null);

        public static PipeSecurity Build(SecurityIdentifier extraUser)
        {
            var ps = new PipeSecurity();
            ps.SetAccessRuleProtection(true, false);
            ps.AddAccessRule(new PipeAccessRule(NetworkSid, PipeAccessRights.FullControl, AccessControlType.Deny));
            ps.AddAccessRule(new PipeAccessRule(DirPolicy.SystemSid, PipeAccessRights.FullControl, AccessControlType.Allow));
            ps.AddAccessRule(new PipeAccessRule(DirPolicy.Admins, PipeAccessRights.FullControl, AccessControlType.Allow));
            ps.AddAccessRule(new PipeAccessRule(Interactive, PipeAccessRights.ReadWrite, AccessControlType.Allow));
            // 시험 판(보통 권한의 '서비스')은 자기 SID 도 넣는다 — 만든 이가 인스턴스를 더 만들 수 있게.
            if (extraUser != null) ps.AddAccessRule(new PipeAccessRule(extraUser, PipeAccessRights.FullControl, AccessControlType.Allow));
            return ps;
        }
    }

    static class PipeNative
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern SafePipeHandle CreateNamedPipeW(string name, uint openMode, uint pipeMode, uint maxInstances, uint outBuf, uint inBuf,
                                                              uint timeout, ref SECURITY_ATTRIBUTES sa);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool ConnectNamedPipe(SafePipeHandle h, IntPtr overlapped);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetNamedPipeClientProcessId(SafePipeHandle h, out uint pid);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetNamedPipeClientSessionId(SafePipeHandle h, out uint session);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetNamedPipeServerProcessId(SafePipeHandle h, out uint pid);
        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetNamedPipeServerSessionId(SafePipeHandle h, out uint session);
        [DllImport("advapi32.dll", SetLastError = true)]
        public static extern bool ImpersonateNamedPipeClient(SafePipeHandle h);
        [DllImport("advapi32.dll", SetLastError = true)]
        public static extern bool RevertToSelf();
        [DllImport("kernel32.dll")]
        public static extern IntPtr GetCurrentThread();
        [DllImport("advapi32.dll", SetLastError = true)]
        public static extern bool OpenThreadToken(IntPtr thread, uint access, bool openAsSelf, out IntPtr token);
        [DllImport("advapi32.dll", SetLastError = true)]
        public static extern bool GetTokenInformation(IntPtr token, int cls, IntPtr info, int len, out int retLen);
        [DllImport("advapi32.dll")]
        public static extern IntPtr GetSidSubAuthority(IntPtr sid, uint index);
        [DllImport("advapi32.dll")]
        public static extern IntPtr GetSidSubAuthorityCount(IntPtr sid);
        [DllImport("kernel32.dll")]
        public static extern bool CloseHandle(IntPtr h);
        [DllImport("kernel32.dll")]
        public static extern uint WTSGetActiveConsoleSessionId();
        [DllImport("kernel32.dll")]
        public static extern bool ProcessIdToSessionId(uint pid, out uint session);

        [StructLayout(LayoutKind.Sequential)]
        public struct SECURITY_ATTRIBUTES
        {
            public int nLength;
            public IntPtr lpSecurityDescriptor;
            public int bInheritHandle;
        }

        public const uint PIPE_ACCESS_DUPLEX = 0x3, FILE_FLAG_FIRST_PIPE_INSTANCE = 0x00080000;
        public const uint PIPE_TYPE_BYTE = 0x0, PIPE_READMODE_BYTE = 0x0, PIPE_WAIT = 0x0, PIPE_REJECT_REMOTE_CLIENTS = 0x8;
        public const uint TOKEN_QUERY = 0x8;
        public const int TokenIntegrityLevel = 25;
        public const int ERROR_PIPE_CONNECTED = 535;
    }

    /// <summary>
    /// 듣는 쪽 — 한 번에 하나씩 받아 짧게 답한다(손님마다 5초 시한). 처음 인스턴스는 FIRST_PIPE_INSTANCE 로 만들어 남이 먼저 잡은
    /// 이름이면 실패한다(선점). 손님을 받으면 곧바로 다음 인스턴스를 만들어 이름을 놓지 않는다.
    /// </summary>
    public sealed class PipeServer : IDisposable
    {
        readonly string name;
        readonly PipeSecurity security;
        readonly Func<PipeClientInfo, Dictionary<string, string>, Dictionary<string, string>> handle;
        readonly Action<string> log;
        Thread thread;
        volatile bool stopping;
        SafePipeHandle listening;
        readonly object gate = new object();
        public int ClientTimeoutMs = 5000;
        /// <summary>손님의 신원을 읽는다(시험이 바꾼다).</summary>
        public Func<SafePipeHandle, PipeClientInfo> Identify = IdentifyClient;
        /// <summary>지금 콘솔 세션(시험이 바꾼다).</summary>
        public Func<int> ConsoleSession = () => { uint s = PipeNative.WTSGetActiveConsoleSessionId(); return s == 0xFFFFFFFF ? -1 : (int)s; };
        /// <summary>시작 실패의 까닭(선점 등) — 없으면 null.</summary>
        public string StartError { get; private set; }

        public PipeServer(string name, PipeSecurity security, Func<PipeClientInfo, Dictionary<string, string>, Dictionary<string, string>> handle,
                          Action<string> log)
        {
            this.name = name;
            this.security = security;
            this.handle = handle;
            this.log = log;
        }

        SafePipeHandle Create(bool first)
        {
            var sd = security.GetSecurityDescriptorBinaryForm();
            var pin = GCHandle.Alloc(sd, GCHandleType.Pinned);
            try
            {
                var sa = new PipeNative.SECURITY_ATTRIBUTES { nLength = Marshal.SizeOf(typeof(PipeNative.SECURITY_ATTRIBUTES)), lpSecurityDescriptor = pin.AddrOfPinnedObject() };
                var h = PipeNative.CreateNamedPipeW(@"\\.\pipe\" + name,
                    PipeNative.PIPE_ACCESS_DUPLEX | (first ? PipeNative.FILE_FLAG_FIRST_PIPE_INSTANCE : 0),
                    PipeNative.PIPE_TYPE_BYTE | PipeNative.PIPE_READMODE_BYTE | PipeNative.PIPE_WAIT | PipeNative.PIPE_REJECT_REMOTE_CLIENTS,
                    16, 65536, 65536, 0, ref sa);
                if (h.IsInvalid)
                {
                    int e = Marshal.GetLastWin32Error();
                    h.Dispose();
                    throw new Win32Exception(e);
                }
                return h;
            }
            finally { pin.Free(); }
        }

        /// <summary>듣기 시작. 이름을 남이 먼저 잡았으면 false(StartError).</summary>
        public bool Start()
        {
            if (stopping) return false;
            if (Listening) return true;
            SafePipeHandle h;
            try { h = Create(true); }
            catch (Win32Exception ex)
            {
                StartError = "파이프를 만들지 못했습니다(" + ex.NativeErrorCode + " — 남이 먼저 잡았을 수 있습니다): " + ex.Message;
                return false;
            }
            lock (gate) listening = h;
            StartError = null;
            thread = new Thread(Loop) { IsBackground = true, Name = "pipe-server" };
            thread.Start();
            return true;
        }

        /// <summary>듣고 있는가 — 시작하지 못했거나 다음 인스턴스를 만들지 못해 고리가 끝났으면 거짓(서비스가 다시 Start 를 부른다).</summary>
        public bool Listening { get { var t = thread; return t != null && t.IsAlive; } }

        void Loop()
        {
            while (!stopping)
            {
                SafePipeHandle h;
                lock (gate) h = listening;
                if (h == null) return;
                bool ok = PipeNative.ConnectNamedPipe(h, IntPtr.Zero) || Marshal.GetLastWin32Error() == PipeNative.ERROR_PIPE_CONNECTED;
                if (stopping) return;
                // 다음 인스턴스를 먼저 만든다 — 지금 것을 닫기 전에 이름을 이어 쥔다.
                SafePipeHandle next = null;
                try { next = Create(false); }
                catch (Win32Exception ex) { if (log != null) log("다음 파이프 인스턴스를 만들지 못함: " + ex.Message); }
                lock (gate) listening = next;
                if (ok) Serve(h);
                else h.Dispose();
                if (next == null) return;
            }
        }

        void Serve(SafePipeHandle h)
        {
            using (var stream = new NamedPipeServerStream(PipeDirection.InOut, false, true, h))
            using (var timer = new Timer(_ => { try { stream.Dispose(); } catch (Exception) { } }, null, ClientTimeoutMs, Timeout.Infinite))
            {
                try
                {
                    var who = Identify(h);
                    var why = PipeClientCheck.Verdict(who, ConsoleSession());
                    var req = PipeMessage.Read(stream);
                    Dictionary<string, string> reply;
                    if (why != null)
                    {
                        if (log != null) log("손님 거절 — " + why + " (" + who + ")");
                        reply = new Dictionary<string, string> { { "ok", "0" }, { "error", "거절: " + why } };
                    }
                    else
                    {
                        var bad = PipeCommands.Validate(req);
                        reply = bad != null ? new Dictionary<string, string> { { "ok", "0" }, { "error", bad } } : handle(who, req);
                    }
                    PipeMessage.Write(stream, reply);
                }
                catch (Exception ex)
                {
                    if (!stopping && log != null) log("파이프 손님 처리 실패: " + ex.GetType().Name);
                }
            }
        }

        /// <summary>손님의 PID·세션·토큰(가장해서 읽고 곧바로 되돌린다 — 가장한 채 아무것도 열지 않는다).</summary>
        public static PipeClientInfo IdentifyClient(SafePipeHandle h)
        {
            var c = new PipeClientInfo();
            uint pid, ses;
            if (PipeNative.GetNamedPipeClientProcessId(h, out pid)) c.Pid = (int)pid;
            if (PipeNative.GetNamedPipeClientSessionId(h, out ses)) c.SessionId = (int)ses;
            if (!PipeNative.ImpersonateNamedPipeClient(h)) return c;
            IntPtr tok = IntPtr.Zero;
            try
            {
                if (!PipeNative.OpenThreadToken(PipeNative.GetCurrentThread(), PipeNative.TOKEN_QUERY, true, out tok)) return c;
                using (var id = new WindowsIdentity(tok))
                {
                    c.Sid = id.User != null ? id.User.Value : "";
                    foreach (var g in id.Groups)
                    {
                        if (g.Value == "S-1-5-4") c.Interactive = true;
                        if (g.Value == "S-1-5-2") c.Network = true;
                        if (g.Value == "S-1-5-32-544") c.SystemOrAdmin = true;
                    }
                    if (c.Sid == "S-1-5-18") c.SystemOrAdmin = true;
                }
                c.Integrity = IntegrityOf(tok);
            }
            finally
            {
                PipeNative.RevertToSelf();
                if (tok != IntPtr.Zero) PipeNative.CloseHandle(tok);
            }
            try { c.Image = StartupImage(c.Pid); } catch (Exception) { }
            return c;
        }

        static string StartupImage(int pid) { return ProcessImage.Path(pid) ?? ""; }

        static int IntegrityOf(IntPtr tok)
        {
            int len;
            PipeNative.GetTokenInformation(tok, PipeNative.TokenIntegrityLevel, IntPtr.Zero, 0, out len);
            if (len <= 0) return 0;
            var buf = Marshal.AllocHGlobal(len);
            try
            {
                if (!PipeNative.GetTokenInformation(tok, PipeNative.TokenIntegrityLevel, buf, len, out len)) return 0;
                var sid = Marshal.ReadIntPtr(buf); // TOKEN_MANDATORY_LABEL.Label.Sid
                int count = Marshal.ReadByte(PipeNative.GetSidSubAuthorityCount(sid));
                return count > 0 ? Marshal.ReadInt32(PipeNative.GetSidSubAuthority(sid, (uint)(count - 1))) : 0;
            }
            finally { Marshal.FreeHGlobal(buf); }
        }

        public void Dispose()
        {
            stopping = true;
            SafePipeHandle h;
            lock (gate) { h = listening; listening = null; }
            // 막혀 있는 ConnectNamedPipe 를 풀려고 한 번 붙었다 끊는다 — 듣고 있을 때만(이름을 남이 쥐었으면 그 서버에 붙지 않는다).
            if (h != null && Listening)
            {
                try
                {
                    using (var c = new NamedPipeClientStream(".", name, PipeDirection.InOut, PipeOptions.None, TokenImpersonationLevel.Identification))
                        c.Connect(200);
                }
                catch (Exception) { }
            }
            if (h != null) h.Dispose();
            if (thread != null) thread.Join(2000);
        }
    }

    /// <summary>프로세스의 이미지 경로(읽기만).</summary>
    public static class ProcessImage
    {
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool QueryFullProcessImageNameW(IntPtr h, uint flags, StringBuilder buf, ref uint size);
        [DllImport("kernel32.dll")]
        static extern bool CloseHandle(IntPtr h);

        public static string Path(int pid)
        {
            var h = OpenProcess(0x1000, false, pid);
            if (h == IntPtr.Zero) return null;
            try
            {
                var sb = new StringBuilder(32768);
                uint n = (uint)sb.Capacity;
                return QueryFullProcessImageNameW(h, 0, sb, ref n) ? sb.ToString(0, (int)n) : null;
            }
            finally { CloseHandle(h); }
        }
    }

    /// <summary>
    /// 두 경로가 같은 파일인가 — 글이 아니라 **두 파일을 열어** 볼륨 일련번호와 파일 ID(GetFileInformationByHandle)로 견준다(검토 11-27 — 8.3
    /// 이름·접합점·대소문자·`\\?\` 꼴에 휘둘리지 않게). 공유 모드는 넉넉히(읽기·쓰기·지우기 — 실행 중인 이미지도 열린다), 링크는 따라간다,
    /// 속성 읽기만 청한다. 열지 못하면 false. 파일 ID 는 64비트 꼴이다(ReFS 의 128비트 ID 는 보지 않는다 — 설치 자리는 NTFS 의 Program Files).
    /// </summary>
    public static class FileIdentity
    {
        [StructLayout(LayoutKind.Sequential)]
        struct ByHandleInfo
        {
            public uint Attributes, CreatedLow, CreatedHigh, AccessedLow, AccessedHigh, WrittenLow, WrittenHigh;
            public uint VolumeSerial, SizeHigh, SizeLow, Links, IndexHigh, IndexLow;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr security, uint disposition, uint flags, IntPtr template);
        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool GetFileInformationByHandle(SafeFileHandle h, out ByHandleInfo info);

        const uint FileReadAttributes = 0x80, ShareAll = 7, OpenExisting = 3, BackupSemantics = 0x02000000;

        public static bool TryGet(string path, out uint volume, out ulong index)
        {
            volume = 0;
            index = 0;
            if (string.IsNullOrEmpty(path)) return false;
            using (var h = CreateFileW(path, FileReadAttributes, ShareAll, IntPtr.Zero, OpenExisting, BackupSemantics, IntPtr.Zero))
            {
                if (h.IsInvalid) return false;
                ByHandleInfo info;
                if (!GetFileInformationByHandle(h, out info)) return false;
                volume = info.VolumeSerial;
                index = ((ulong)info.IndexHigh << 32) | info.IndexLow;
                return true;
            }
        }

        public static bool Same(string a, string b)
        {
            uint va, vb;
            ulong ia, ib;
            try { return TryGet(a, out va, out ia) && TryGet(b, out vb, out ib) && va == vb && ia == ib; }
            catch (Exception) { return false; }
        }
    }

    /// <summary>
    /// 묻는 쪽 — 한 번 붙어 요청 하나·답 하나. 서버가 믿을 만한지 먼저 본다(TrustServer — 실제 판은 세션 0 이고 PID 가 SCM 이 아는
    /// 서비스 WebTether 의 PID. 이미지 경로는 보지 않는다 — PID 대조가 그보다 강하다).
    /// 신원 확인 수준(Identification)으로 붙는다 — 서버가 토큰을 읽을 수 있되 가장해 무엇을 열지는 못한다.
    /// </summary>
    public sealed class PipeClient
    {
        readonly string name;
        public int ConnectTimeoutMs = 2000;
        /// <summary>(서버 세션, 서버 PID) → 믿으면 null, 아니면 까닭. 시험 판은 같은 세션을 받는다.</summary>
        public Func<int, int, string> TrustServer = DefaultTrust;

        public PipeClient(string name) { this.name = name; }

        /// <summary>서비스의 PID(SCM — 보통 사용자도 조회 권한이 있다). 0 이면 돌고 있지 않거나 모름. 시험이 바꾼다.</summary>
        public static Func<int> ServicePid = () => ScmManager.ProcessIdReadOnly(ServicePlan.Name);

        public static string DefaultTrust(int session, int pid)
        {
            if (session != 0) return ServerVerdict(session, pid, 0);
            return ServerVerdict(session, pid, ServicePid()); // 붙은 뒤에 묻는다 — 그 사이 서비스가 다시 떴으면 다음 물음에서 맞는다
        }

        /// <summary>서버가 서비스인가 — 세션 0 이고 PID 가 SCM 의 서비스 PID 와 같을 때만. 아니면 까닭(짧게 — 트레이의 상태 글).</summary>
        public static string ServerVerdict(int session, int pid, int servicePid)
        {
            if (session != 0) return "서비스가 아닌 프로그램이 통로를 쥐었습니다(세션 " + session + ")";
            if (servicePid <= 0) return "서비스가 시작·멈추는 중이거나 돌고 있지 않습니다";
            if (pid != servicePid) return "서비스가 아닌 프로그램이 통로를 쥐었습니다(PID " + pid + ")";
            return null;
        }

        /// <summary>요청 하나. 실패하면 null 과 까닭.</summary>
        public Dictionary<string, string> Send(Dictionary<string, string> req, out string error)
        {
            error = null;
            try
            {
                using (var c = new NamedPipeClientStream(".", name, PipeDirection.InOut, PipeOptions.None, TokenImpersonationLevel.Identification))
                {
                    c.Connect(ConnectTimeoutMs);
                    uint ses, pid;
                    var h = c.SafePipeHandle;
                    if (!PipeNative.GetNamedPipeServerSessionId(h, out ses) || !PipeNative.GetNamedPipeServerProcessId(h, out pid))
                    {
                        error = "서버를 확인하지 못했습니다";
                        return null;
                    }
                    var why = TrustServer((int)ses, (int)pid);
                    if (why != null) { error = why; return null; }
                    // 파이프는 읽기 시한을 받지 않는다 — 5초가 지나면 닫아 막힌 읽기를 푼다.
                    using (new Timer(_ => { try { c.Dispose(); } catch (Exception) { } }, null, 5000, Timeout.Infinite))
                    {
                        PipeMessage.Write(c, req);
                        return PipeMessage.Read(c);
                    }
                }
            }
            catch (TimeoutException) { error = "서비스가 답하지 않습니다"; }
            catch (IOException ex) { error = "서비스와 이야기하지 못했습니다: " + ex.Message; }
            catch (FormatException ex) { error = "서비스의 답이 틀렸습니다: " + ex.Message; }
            catch (UnauthorizedAccessException) { error = "파이프에 닿을 권한이 없습니다"; }
            catch (ObjectDisposedException) { error = "서비스가 시간 안에 답하지 않았습니다"; }
            catch (InvalidOperationException ex) { error = ex.Message; }
            return null;
        }
    }
}
