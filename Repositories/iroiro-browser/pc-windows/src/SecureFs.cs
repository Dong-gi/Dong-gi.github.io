// 관리자가 쓰고 읽는 자리 — %ProgramData%\WebTether(상태)와 Program Files\WebTether(시작 시 실행의 사본).
//
// 도우미는 관리자로 돈다. 그 상태를 사용자 폴더(%APPDATA%·%LOCALAPPDATA%)에 두면 같은 사용자 권한의 프로그램이
// ① 접합점·심볼릭 링크로 관리자의 지우기·쓰기를 다른 곳으로 돌리고 ② 설정·상태 파일을 고쳐 관리자 동작(프록시 주소,
// 어떤 경로를 넣고 지울지)을 정할 수 있다. 그래서 상태는 관리자만 쓰는 폴더에 둔다:
//   - 만들 때 상속을 끊고 주인과 모두 권한을 정책의 SID(관리자: Administrators·SYSTEM)에만 준다 — 사용자는 읽기도 없다
//     (로그는 앱의 '로그' 창이 관리자로 읽어 보인다).
//   - 이미 있으면 링크를 따라가지 않는 핸들로 열어 재분석 지점이 아닌지, 주인이 정책의 SID 인지, 정책 밖의 누구도 쓰기·지우기·
//     권한 바꾸기를 못 하는지 본다. ProgramData 는 사용자도 하위 폴더를 만들 수 있다 — 누가 먼저 만들어 둔 것이면 이름을
//     옆으로 바꿔(링크면 링크 자체가 옮겨진다 — 대상은 건드리지 않는다) 비키고 새로 만든다.
//   - 쥔 핸들은 FILE_SHARE_DELETE 없이 연다 — 가진 동안 그 폴더의 이름을 바꾸거나 지울 수 없다.
//   - 그 아래에 쓰기 전에 루트부터 그 파일까지 재분석 지점이 끼어 있지 않은지 본다(관리자 전용이라 끼울 수 없지만 한 겹 더).
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace WebTether
{
    /// <summary>누가 주인일 수 있고 누가 쓸 수 있는가.</summary>
    public sealed class DirPolicy
    {
        public static readonly SecurityIdentifier Admins = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
        public static readonly SecurityIdentifier SystemSid = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);

        public string Name;
        /// <summary>새로 만들 때의 주인.</summary>
        public SecurityIdentifier Owner;
        /// <summary>주인이어도 되고 모두 권한을 받는 SID — 그 밖의 누구에게도 권한을 주지 않는다.</summary>
        public SecurityIdentifier[] Trusted;

        /// <summary>실제 판 — Administrators 가 주인, Administrators·SYSTEM 만.</summary>
        public static DirPolicy AdminOnly()
        {
            return new DirPolicy { Name = "관리자 전용", Owner = Admins, Trusted = new[] { Admins, SystemSid } };
        }

        /// <summary>시험 판·시험 — 관리자 아닌 권한으로 만들 수 있는 꼴(이 사용자가 주인, 이 사용자·Administrators·SYSTEM 만).</summary>
        public static DirPolicy CurrentUser()
        {
            using (var id = WindowsIdentity.GetCurrent())
                return new DirPolicy { Name = "이 사용자 전용", Owner = id.User, Trusted = new[] { id.User, Admins, SystemSid } };
        }

        public bool IsTrusted(SecurityIdentifier sid)
        {
            if (sid == null) return false;
            foreach (var t in Trusted) if (t.Equals(sid)) return true;
            return false;
        }

        public DirectorySecurity LockedSecurity()
        {
            var sec = new DirectorySecurity();
            sec.SetOwner(Owner);
            sec.SetAccessRuleProtection(true, false);
            const InheritanceFlags inh = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
            foreach (var t in Trusted)
                sec.AddAccessRule(new FileSystemAccessRule(t, FileSystemRights.FullControl, inh, PropagationFlags.None, AccessControlType.Allow));
            return sec;
        }
    }

    public static class SecureDir
    {
        [StructLayout(LayoutKind.Sequential)]
        struct SECURITY_ATTRIBUTES
        {
            public int nLength;
            public IntPtr lpSecurityDescriptor;
            public int bInheritHandle;
        }

        // FILETIME 은 DWORD 둘이라 4 바이트 정렬이다 — long 으로 적으면 앞에 틈이 생겨 뒤 칸(링크 수 등)이 어긋난다(시험에서 잡았다).
        [StructLayout(LayoutKind.Sequential)]
        struct BY_HANDLE_FILE_INFORMATION
        {
            public uint FileAttributes;
            public uint CreationLow, CreationHigh, AccessLow, AccessHigh, WriteLow, WriteHigh;
            public uint VolumeSerialNumber, FileSizeHigh, FileSizeLow, NumberOfLinks, FileIndexHigh, FileIndexLow;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool CreateDirectoryW(string path, ref SECURITY_ATTRIBUTES sa);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr sa, uint disposition, uint flags, IntPtr template);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool GetFileInformationByHandle(SafeFileHandle h, out BY_HANDLE_FILE_INFORMATION info);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern bool MoveFileExW(string from, string to, uint flags);

        [DllImport("advapi32.dll", SetLastError = true)]
        static extern uint GetSecurityInfo(SafeFileHandle h, int objectType, uint info, IntPtr owner, IntPtr group, IntPtr dacl, IntPtr sacl, out IntPtr sd);

        [DllImport("advapi32.dll")]
        static extern uint GetSecurityDescriptorLength(IntPtr sd);

        [DllImport("kernel32.dll")]
        static extern IntPtr LocalFree(IntPtr p);

        // FILE_LIST_DIRECTORY 를 함께 청한다 — 속성·권한만 청한 핸들은 공유 모드 검사에 끼지 않아 이름 바꾸기를 막지 못했다(시험).
        const uint FILE_LIST_DIRECTORY = 0x1, FILE_READ_ATTRIBUTES = 0x80, READ_CONTROL = 0x20000;
        const uint SHARE_READ = 0x1, SHARE_WRITE = 0x2;
        const uint OPEN_EXISTING = 3;
        const uint FILE_FLAG_BACKUP_SEMANTICS = 0x02000000, FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
        const uint ATTR_DIRECTORY = 0x10, ATTR_REPARSE = 0x400;
        const int SE_FILE_OBJECT = 1;
        const uint OWNER_SECURITY_INFORMATION = 0x1, DACL_SECURITY_INFORMATION = 0x4;
        const int ERROR_ALREADY_EXISTS = 183;

        /// <summary>정책 밖의 SID 가 가지면 안 되는 권한(쓰기·덧붙이기·하위 만들기·지우기·권한·주인 바꾸기, 일반 권한 포함).</summary>
        const int WriteLike = 0x2 | 0x4 | 0x10 | 0x40 | 0x100 | 0x10000 | 0x40000 | 0x80000 | 0x10000000 | 0x40000000;

        /// <summary>쥔 폴더 — 가진 동안 이름을 바꾸거나 지울 수 없다.</summary>
        public sealed class Held : IDisposable
        {
            public readonly string Path;
            SafeFileHandle handle;
            internal Held(string path, SafeFileHandle h) { Path = path; handle = h; }
            public void Dispose()
            {
                if (handle != null) { handle.Dispose(); handle = null; }
            }
        }

        /// <summary>
        /// 정책의 전용 폴더를 마련하고 쥔다. 실패하면 null 과 까닭.
        /// 이미 있는데 정책에 맞지 않으면(주인·권한·재분석 지점) **제자리에서 고치거나 안을 지우지 않는다** — 폴더째 `이름.untrusted-<때>` 로 옆으로
        /// 비키고 새로 만든다(비킨 것은 지우지 않는다 — 치우기는 사용자 몫). 11-27 재검토: 권한만 고치고 안의 것을 경로로 확인한 뒤 경로로 지우면
        /// 그 틈에 접합점을 끼워 관리자가 폴더 밖을 지우게 할 수 있었다. 비키지 못하면(그 안의 파일이 열려 있음·부모에 이름 바꾸기 권한 없음) 실패.
        /// </summary>
        public static Held Prepare(string path, DirPolicy policy, List<string> notes, out string error)
        {
            error = null;
            path = System.IO.Path.GetFullPath(path).TrimEnd('\\');
            for (int attempt = 0; attempt < 2; attempt++)
            {
                string why;
                if (!TryCreate(path, policy, out why))
                {
                    if (why != null) { error = "폴더를 만들지 못했습니다(" + why + "): " + path; return null; }
                }
                SafeFileHandle h;
                try { h = OpenNoFollow(path, FILE_LIST_DIRECTORY | READ_CONTROL | FILE_READ_ATTRIBUTES, SHARE_READ | SHARE_WRITE, true); }
                catch (IOException ex) { error = ex.Message; return null; }
                bool reparse, ownerOk;
                var bad = Check(h, policy, out reparse, out ownerOk);
                if (bad == null) return new Held(path, h);
                h.Dispose();
                if (attempt == 1) { error = bad + ": " + path; return null; }
                var aside = path + ".untrusted-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N").Substring(0, 6);
                // MoveFileEx 는 마지막 이름이 링크면 링크 자체를 옮긴다 — 대상은 건드리지 않는다.
                if (!MoveFileExW(path, aside, 0))
                {
                    error = bad + " — 옆으로 비키지도 못했습니다(" + Marshal.GetLastWin32Error() + "). 그 폴더를 지운 뒤 다시 실행하십시오: " + path;
                    return null;
                }
                if (notes != null) notes.Add(bad + " — 쓰지 않고 옆으로 옮겼습니다: " + aside);
            }
            error = "폴더를 마련하지 못했습니다: " + path;
            return null;
        }

        /// <summary>정책의 보안 설명자로 새로 만든다. 이미 있으면 false 와 why=null, 다른 실패면 false 와 까닭.</summary>
        static bool TryCreate(string path, DirPolicy policy, out string why)
        {
            why = null;
            var sd = policy.LockedSecurity().GetSecurityDescriptorBinaryForm();
            var pin = GCHandle.Alloc(sd, GCHandleType.Pinned);
            try
            {
                var sa = new SECURITY_ATTRIBUTES { nLength = Marshal.SizeOf(typeof(SECURITY_ATTRIBUTES)), lpSecurityDescriptor = pin.AddrOfPinnedObject() };
                if (CreateDirectoryW(path, ref sa)) return true;
                int e = Marshal.GetLastWin32Error();
                if (e != ERROR_ALREADY_EXISTS) why = new Win32Exception(e).Message + " " + e;
                return false;
            }
            finally { pin.Free(); }
        }

        static SafeFileHandle OpenNoFollow(string path, uint access, uint share, bool directory)
        {
            var h = CreateFileW(path, access, share, IntPtr.Zero, OPEN_EXISTING,
                                FILE_FLAG_OPEN_REPARSE_POINT | (directory ? FILE_FLAG_BACKUP_SEMANTICS : 0), IntPtr.Zero);
            if (h.IsInvalid)
            {
                int e = Marshal.GetLastWin32Error();
                h.Dispose();
                throw new IOException("열지 못했습니다(" + e + "): " + path);
            }
            return h;
        }

        /// <summary>문제가 없으면 null, 있으면 까닭.</summary>
        static string Check(SafeFileHandle h, DirPolicy policy, out bool reparse, out bool ownerOk)
        {
            reparse = false;
            ownerOk = false;
            BY_HANDLE_FILE_INFORMATION info;
            if (!GetFileInformationByHandle(h, out info)) return "폴더 정보를 읽지 못했습니다";
            if ((info.FileAttributes & ATTR_REPARSE) != 0) { reparse = true; return "재분석 지점(접합점·심볼릭 링크)입니다"; }
            if ((info.FileAttributes & ATTR_DIRECTORY) == 0) return "폴더가 아닙니다";
            var sd = ReadSecurity(h);
            if (sd == null) return "권한을 읽지 못했습니다";
            return CheckDescriptor(sd, policy, out ownerOk);
        }

        static RawSecurityDescriptor ReadSecurity(SafeFileHandle h)
        {
            IntPtr p;
            if (GetSecurityInfo(h, SE_FILE_OBJECT, OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
                                IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, out p) != 0) return null;
            try
            {
                var bytes = new byte[GetSecurityDescriptorLength(p)];
                Marshal.Copy(p, bytes, 0, bytes.Length);
                return new RawSecurityDescriptor(bytes, 0);
            }
            finally { LocalFree(p); }
        }

        /// <summary>주인이 정책의 SID 이고, 정책 밖의 누구에게도 쓰기 같은 권한을 허락하지 않는가(상속만 하는 줄도 본다).</summary>
        public static string CheckDescriptor(RawSecurityDescriptor sd, DirPolicy policy, out bool ownerOk)
        {
            ownerOk = policy.IsTrusted(sd.Owner);
            if (!ownerOk) return "주인이 " + policy.Name + " 이 아닙니다(" + NameOf(sd.Owner) + ")";
            if (sd.DiscretionaryAcl == null) return "권한 목록이 없습니다(누구나 쓸 수 있음)";
            foreach (GenericAce ace in sd.DiscretionaryAcl)
            {
                var known = ace as KnownAce;
                if (known == null) return "읽지 못하는 권한 줄이 있습니다";
                if (ace.AceType == AceType.AccessDenied || ace.AceType == AceType.AccessDeniedObject ||
                    ace.AceType == AceType.AccessDeniedCallback || ace.AceType == AceType.AccessDeniedCallbackObject) continue;
                if (policy.IsTrusted(known.SecurityIdentifier)) continue;
                if ((known.AccessMask & WriteLike) != 0) return NameOf(known.SecurityIdentifier) + " 이(가) 쓸 수 있습니다";
            }
            return null;
        }

        static string NameOf(SecurityIdentifier sid)
        {
            if (sid == null) return "없음";
            try { return sid.Translate(typeof(NTAccount)).Value; }
            catch (Exception) { return sid.Value; }
        }

        /// <summary>그 폴더의 지금 보안 설명자(시험·확인용 — 링크를 따라가지 않는다).</summary>
        public static RawSecurityDescriptor ReadDescriptor(string dir)
        {
            using (var h = OpenNoFollow(dir, READ_CONTROL | FILE_READ_ATTRIBUTES, SHARE_READ | SHARE_WRITE, true))
                return ReadSecurity(h);
        }

        /// <summary>
        /// root 부터 path 까지(있는 것만) 재분석 지점이 없는지 본다. 있으면 IOException. root 가 null 이면 path 와 그 폴더만.
        /// </summary>
        public static void CheckNoReparse(string root, string path)
        {
            path = System.IO.Path.GetFullPath(path);
            var chain = new List<string>();
            if (root == null)
            {
                chain.Add(System.IO.Path.GetDirectoryName(path));
                chain.Add(path);
            }
            else
            {
                root = System.IO.Path.GetFullPath(root).TrimEnd('\\');
                if (!string.Equals(path, root, StringComparison.OrdinalIgnoreCase) &&
                    !path.StartsWith(root + "\\", StringComparison.OrdinalIgnoreCase))
                    throw new IOException("상태 폴더 밖입니다: " + path);
                chain.Add(root);
                var cur = root;
                foreach (var part in path.Substring(root.Length).Split(new[] { '\\' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    cur = System.IO.Path.Combine(cur, part);
                    chain.Add(cur);
                }
            }
            foreach (var p in chain)
            {
                FileAttributes a;
                try { a = File.GetAttributes(p); } // 링크를 따라가지 않는다 — 링크 자신의 속성
                catch (FileNotFoundException) { return; }
                catch (DirectoryNotFoundException) { return; }
                if ((a & FileAttributes.ReparsePoint) != 0) throw new IOException("재분석 지점이라 쓰지 않습니다: " + p);
            }
        }

        /// <summary>폴더를 지운다 — 접합점·심볼릭 링크는 링크만 지우고 따라 들어가지 않는다.</summary>
        public static void DeleteTree(string dir)
        {
            var a = File.GetAttributes(dir);
            if ((a & FileAttributes.ReparsePoint) != 0)
            {
                if ((a & FileAttributes.Directory) != 0) Directory.Delete(dir, false); else File.Delete(dir);
                return;
            }
            foreach (var e in Directory.GetFileSystemEntries(dir))
            {
                var ea = File.GetAttributes(e);
                if ((ea & FileAttributes.Directory) != 0) DeleteTree(e);
                else
                {
                    if ((ea & FileAttributes.ReadOnly) != 0) File.SetAttributes(e, ea & ~FileAttributes.ReadOnly);
                    File.Delete(e);
                }
            }
            Directory.Delete(dir, false);
        }
    }

    /// <summary>상태 파일 쓰기·지우기 — 쓰기 전에 재분석 지점을 보고, 임시 파일에 다 쓴 뒤 바꿔 끼운다.</summary>
    public static class SafeFs
    {
        public static void WriteAtomic(string path, string text, string root)
        {
            var dir = Path.GetDirectoryName(path);
            SecureDir.CheckNoReparse(root, dir);
            Directory.CreateDirectory(dir);
            var tmp = path + ".tmp";
            SecureDir.CheckNoReparse(root, path);
            SecureDir.CheckNoReparse(root, tmp);
            if (File.Exists(tmp)) File.Delete(tmp);
            using (var fs = new FileStream(tmp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                var b = new UTF8Encoding(false).GetBytes(text);
                fs.Write(b, 0, b.Length);
                fs.Flush(true);
            }
            if (File.Exists(path)) File.Replace(tmp, path, null);
            else File.Move(tmp, path);
        }

        public static void Delete(string path, string root)
        {
            SecureDir.CheckNoReparse(root, path);
            if (File.Exists(path)) File.Delete(path);
        }
    }
}
