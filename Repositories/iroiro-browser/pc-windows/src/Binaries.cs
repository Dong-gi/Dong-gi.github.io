// exe 안에 든 tun2proxy-bin.exe·wintun.dll 을 %ProgramData%\WebTether\bin\<묶음 id>\(관리자 전용 — SecureFs)에 풀고,
// 띄울 때마다 SHA-256 을 확인한다. 확인한 핸들을 tun2proxy 가 끝날 때까지 쥔다 —
// FileShare.Read 만 허락하므로 그동안 파일을 바꾸거나 지우거나 그 폴더(와 위 폴더)의 이름을 바꿀 수 없다.
// 확인·띄우기는 폴더의 **실제 경로**로 한다 — 접합점은 핸들을 쥔 뒤에도 대상을 바꿀 수 있다(OpenDirectory. 예전에는 사용자
// 폴더 아래였다 — 지금은 관리자 전용이라 끼울 수 없지만 그대로 둔다). 다시 풀 때 지우기는 링크를 따라 들어가지 않는다.
// 관리자로 돌면 폴더의 주인을 Administrators 로 하고 다른 이의 쓰기를 막는다 — 그 폴더에 DLL·route.exe 를
// 끼워 넣으면 관리자 권한의 tun2proxy 가 그것을 먼저 읽는다(실행 파일의 폴더가 검색 차례의 앞이다).
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace WebTether
{
    public sealed class BinaryItem
    {
        public string FileName;
        public string ResourceName;
        public string Sha256;
    }

    public sealed class HeldBinaries : IDisposable
    {
        /// <summary>실제 자리(접합점·심볼릭 링크를 다 푼 경로) — 띄우는 경로도 이것이다.</summary>
        public string Dir;
        public string ExePath;
        readonly List<IDisposable> handles = new List<IDisposable>();

        internal void Add(IDisposable h) { handles.Add(h); }

        public void Dispose()
        {
            foreach (var s in handles) { try { s.Dispose(); } catch (IOException) { } }
            handles.Clear();
        }
    }

    public sealed class BinarySet
    {
        public string BundleId;
        public BinaryItem[] Items;
        /// <summary>자원 대신 바이트를 주는 곳(시험). null 이면 이 exe 의 자원.</summary>
        public Func<BinaryItem, byte[]> Source;

        public static BinarySet Embedded()
        {
            return new BinarySet
            {
                BundleId = WebTether.Embedded.BundleId,
                Items = new[]
                {
                    new BinaryItem { FileName = "tun2proxy-bin.exe", ResourceName = "WebTether.tun2proxy-bin.exe.gz", Sha256 = WebTether.Embedded.Tun2proxySha256 },
                    new BinaryItem { FileName = "wintun.dll", ResourceName = "WebTether.wintun.dll.gz", Sha256 = WebTether.Embedded.WintunSha256 },
                },
            };
        }

        public byte[] Read(BinaryItem item)
        {
            if (Source != null) return Source(item);
            using (var rs = Assembly.GetExecutingAssembly().GetManifestResourceStream(item.ResourceName))
            {
                if (rs == null) throw new InvalidDataException("자원이 없습니다: " + item.ResourceName);
                using (var gz = new GZipStream(rs, CompressionMode.Decompress))
                using (var ms = new MemoryStream())
                {
                    gz.CopyTo(ms);
                    return ms.ToArray();
                }
            }
        }
    }

    public static class Binaries
    {
        public static string Hex(byte[] b)
        {
            var c = new char[b.Length * 2];
            const string h = "0123456789abcdef";
            for (int i = 0; i < b.Length; i++) { c[2 * i] = h[b[i] >> 4]; c[2 * i + 1] = h[b[i] & 15]; }
            return new string(c);
        }

        public static string Sha256(byte[] data)
        {
            using (var sha = SHA256.Create()) return Hex(sha.ComputeHash(data));
        }

        public static string Sha256(Stream s)
        {
            using (var sha = SHA256.Create()) return Hex(sha.ComputeHash(s));
        }

        /// <summary>
        /// 풀고 확인한 뒤 핸들을 쥐어 돌려준다. 실패하면 null 과 까닭.
        /// harden: 관리자일 때 폴더의 주인·권한을 잠그고 그것을 확인한다.
        /// </summary>
        public static HeldBinaries Ensure(BinarySet set, string binRoot, bool harden, Action<string> log, out string error)
        {
            error = null;
            var dir = Path.Combine(binRoot, set.BundleId);
            for (int attempt = 0; attempt < 2; attempt++)
            {
                string why;
                if (attempt == 1 || !Directory.Exists(dir))
                {
                    if (!Extract(set, dir, harden, out why))
                    {
                        error = "실행 파일을 풀지 못했습니다: " + why;
                        return null;
                    }
                }
                var held = OpenVerified(set, dir, harden, out why);
                if (held != null) return held;
                if (log != null) log("실행 파일 확인 실패(" + why + ") — 다시 풉니다");
                error = why;
            }
            error = "실행 파일을 확인하지 못해 띄우지 않습니다: " + error;
            return null;
        }

        static bool Extract(BinarySet set, string dir, bool harden, out string why)
        {
            why = null;
            try
            {
                // 내용을 먼저 확인한다 — exe 의 자원이 깨졌으면 풀지 않는다.
                var data = new Dictionary<string, byte[]>();
                foreach (var it in set.Items)
                {
                    var bytes = set.Read(it);
                    var h = Sha256(bytes);
                    if (!string.Equals(h, it.Sha256, StringComparison.OrdinalIgnoreCase))
                    {
                        why = it.FileName + " 의 내장본 해시가 다릅니다";
                        return false;
                    }
                    data[it.FileName] = bytes;
                }
                // 링크를 따라 들어가지 않고 지운다(링크면 링크만).
                if (Directory.Exists(dir) || File.Exists(dir)) SecureDir.DeleteTree(dir);
                Directory.CreateDirectory(Path.GetDirectoryName(dir));
                if (harden) Directory.CreateDirectory(dir, LockedSecurity());
                else Directory.CreateDirectory(dir);
                foreach (var it in set.Items)
                {
                    var path = Path.Combine(dir, it.FileName);
                    using (var fs = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                    {
                        var b = data[it.FileName];
                        fs.Write(b, 0, b.Length);
                    }
                }
                return true;
            }
            catch (Exception ex)
            {
                why = ex.Message;
                return false;
            }
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr sa, uint disposition, uint flags, IntPtr template);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetFinalPathNameByHandleW(SafeFileHandle h, StringBuilder buf, uint size, uint flags);

        /// <summary>
        /// 폴더를 지우기·이름 바꾸기를 막은 채 열고(FILE_SHARE_DELETE 없음) 그 실제 경로를 돌려준다.
        /// 사용자 경로(%LOCALAPPDATA%\…\bin)는 사용자가 접합점으로 바꿀 수 있다 — 확인한 뒤 접합점의 대상을 바꾸면 같은 글의
        /// 경로가 다른 폴더의 exe 를 가리킨다(쥔 핸들은 옛 대상의 파일을 지킬 뿐이다). 실제 경로의 위 폴더들은 이 핸들이 있는 동안
        /// 이름을 바꿀 수 없고, 비지 않은 폴더는 접합점이 될 수 없으므로 실제 경로는 바뀌지 않는다.
        /// </summary>
        static SafeFileHandle OpenDirectory(string dir, out string real)
        {
            real = null;
            // FILE_LIST_DIRECTORY 를 함께 — 속성만 청한 핸들은 공유 모드 검사에 끼지 않아 그 폴더의 이름 바꾸기를 막지 못한다
            // (안에 쥔 파일 핸들이 대신 막고 있었다).
            const uint FILE_LIST_DIRECTORY = 0x1, FILE_READ_ATTRIBUTES = 0x80, SHARE_READ_WRITE = 0x1 | 0x2, OPEN_EXISTING = 3, FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
            var h = CreateFileW(dir, FILE_LIST_DIRECTORY | FILE_READ_ATTRIBUTES, SHARE_READ_WRITE, IntPtr.Zero, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, IntPtr.Zero);
            if (h.IsInvalid) throw new IOException("폴더를 열지 못했습니다(" + Marshal.GetLastWin32Error() + ")");
            var sb = new StringBuilder(1024);
            uint n = GetFinalPathNameByHandleW(h, sb, (uint)sb.Capacity, 0);
            if (n == 0 || n >= sb.Capacity) { h.Dispose(); throw new IOException("폴더의 실제 경로를 얻지 못했습니다"); }
            real = StripPrefix(sb.ToString());
            return h;
        }

        public static string StripPrefix(string p)
        {
            if (p.StartsWith(@"\\?\UNC\", StringComparison.Ordinal)) return @"\\" + p.Substring(8);
            if (p.StartsWith(@"\\?\", StringComparison.Ordinal)) return p.Substring(4);
            return p;
        }

        static HeldBinaries OpenVerified(BinarySet set, string dir, bool harden, out string why)
        {
            why = null;
            var held = new HeldBinaries { Dir = dir };
            try
            {
                string real;
                held.Add(OpenDirectory(dir, out real));
                dir = real;
                held.Dir = real;
                if (harden && !IsLockedDown(dir, out why)) { held.Dispose(); return null; }
                var expected = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var it in set.Items) expected.Add(it.FileName);
                foreach (var e in Directory.GetFileSystemEntries(dir))
                {
                    if (!expected.Contains(Path.GetFileName(e)))
                    {
                        why = "낯선 파일이 있습니다: " + Path.GetFileName(e);
                        held.Dispose();
                        return null;
                    }
                }
                foreach (var it in set.Items)
                {
                    var path = Path.Combine(dir, it.FileName);
                    // 쓰기·지우기·이름 바꾸기를 막는다(FileShare.Delete 없음).
                    var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
                    held.Add(fs);
                    var h = Sha256(fs);
                    if (!string.Equals(h, it.Sha256, StringComparison.OrdinalIgnoreCase))
                    {
                        why = it.FileName + " 의 SHA-256 이 다릅니다";
                        held.Dispose();
                        return null;
                    }
                    if (it.FileName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) held.ExePath = path;
                }
                // 핸들을 쥔 뒤에 한 번 더 — 그 사이 끼워 넣은 것이 있는지.
                if (Directory.GetFileSystemEntries(dir).Length != set.Items.Length)
                {
                    why = "확인하는 사이 폴더가 바뀌었습니다";
                    held.Dispose();
                    return null;
                }
                return held;
            }
            catch (Exception ex)
            {
                why = ex.Message;
                held.Dispose();
                return null;
            }
        }

        static readonly SecurityIdentifier Admins = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
        static readonly SecurityIdentifier System_ = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);

        static DirectorySecurity LockedSecurity()
        {
            var sec = new DirectorySecurity();
            sec.SetOwner(Admins);
            sec.SetAccessRuleProtection(true, false);
            const InheritanceFlags inh = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
            sec.AddAccessRule(new FileSystemAccessRule(Admins, FileSystemRights.FullControl, inh, PropagationFlags.None, AccessControlType.Allow));
            sec.AddAccessRule(new FileSystemAccessRule(System_, FileSystemRights.FullControl, inh, PropagationFlags.None, AccessControlType.Allow));
            var me = WindowsIdentity.GetCurrent().User;
            if (me != null)
                sec.AddAccessRule(new FileSystemAccessRule(me, FileSystemRights.ReadAndExecute, inh, PropagationFlags.None, AccessControlType.Allow));
            return sec;
        }

        const FileSystemRights WriteLike = FileSystemRights.WriteData | FileSystemRights.AppendData |
            FileSystemRights.WriteExtendedAttributes | FileSystemRights.WriteAttributes | FileSystemRights.Delete |
            FileSystemRights.DeleteSubdirectoriesAndFiles | FileSystemRights.ChangePermissions | FileSystemRights.TakeOwnership;

        /// <summary>주인이 Administrators 이고, SYSTEM·Administrators 밖의 누구도 쓰기·지우기·권한 바꾸기를 못 하는가.</summary>
        public static bool IsLockedDown(string dir, out string why)
        {
            why = null;
            var sec = Directory.GetAccessControl(dir, AccessControlSections.Owner | AccessControlSections.Access);
            var owner = sec.GetOwner(typeof(SecurityIdentifier)) as SecurityIdentifier;
            if (owner == null || !owner.Equals(Admins)) { why = "폴더의 주인이 Administrators 가 아닙니다"; return false; }
            foreach (FileSystemAccessRule r in sec.GetAccessRules(true, true, typeof(SecurityIdentifier)))
            {
                if (r.AccessControlType != AccessControlType.Allow) continue;
                var sid = r.IdentityReference as SecurityIdentifier;
                if (sid != null && (sid.Equals(Admins) || sid.Equals(System_))) continue;
                if ((r.FileSystemRights & WriteLike) != 0) { why = "다른 사용자가 폴더에 쓸 수 있습니다"; return false; }
            }
            return true;
        }

        public static bool IsElevated()
        {
            using (var id = WindowsIdentity.GetCurrent())
                return new WindowsPrincipal(id).IsInRole(WindowsBuiltInRole.Administrator);
        }
    }
}
