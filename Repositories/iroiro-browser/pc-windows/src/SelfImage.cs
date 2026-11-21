// '시작 시 실행' 의 사본 — 실행 중인 exe 를 **경로로** 복사하지 않는다.
//
// 사용자 폴더(내려받기)의 exe 는 같은 사용자의 프로그램이 이름을 바꾸고 그 자리에 다른 exe 를 둘 수 있다(돌고 있는 exe 도 이름은
// 바뀐다). 경로로 복사하면 그 다른 exe 가 Program Files 로 가서 로그온마다 관리자로 돈다. 그래서 시작할 때 **지금 메모리에 올라
// 돌고 있는 그 파일**을 지우기·쓰기를 막은 채(공유 모드 Read 만) 열어 쥐고, 복사는 그 핸들에서 한다:
//   1. GetMappedFileName 으로 이 프로세스 이미지 파일의 지금 이름(NT 경로 — 이름이 바뀌었으면 바뀐 이름)을 얻어 연다.
//   2. 연 핸들의 실제 경로가 그 뒤에 다시 얻은 이미지 이름과 같은지 본다 — 핸들은 이름 바꾸기를 막으므로 같으면 같은 파일이다.
// 복사본은 관리자 전용 폴더에 임시 이름으로 쓰고 SHA-256 을 쥔 바이트와 견준 뒤 제 이름으로 옮긴다(옮긴 뒤 한 번 더).
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace WebTether
{
    public sealed class SelfImage : IDisposable
    {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        static extern IntPtr GetModuleHandleW(string name);

        [DllImport("kernel32.dll")]
        static extern IntPtr GetCurrentProcess();

        [DllImport("psapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetMappedFileNameW(IntPtr process, IntPtr address, StringBuilder buf, uint size);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr sa, uint disposition, uint flags, IntPtr template);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetFinalPathNameByHandleW(SafeFileHandle h, StringBuilder buf, uint size, uint flags);

        const uint GENERIC_READ = 0x80000000, FILE_SHARE_READ = 0x1, OPEN_EXISTING = 3;
        const uint VOLUME_NAME_DOS = 0x0, VOLUME_NAME_NT = 0x2;

        /// <summary>쥔 exe — 처음부터 읽으려면 Position 을 0 으로(설정 저장·뜰 때의 사본 견주기·바꾸기가 일꾼 스레드에서 쓴다 — SystemOps 의 copyLock 안에서만).</summary>
        public FileStream Stream { get; private set; }
        /// <summary>연 파일의 실제 경로(DOS 꼴).</summary>
        public string Path { get; private set; }

        static string MappedName()
        {
            var sb = new StringBuilder(32768);
            uint n = GetMappedFileNameW(GetCurrentProcess(), GetModuleHandleW(null), sb, (uint)sb.Capacity);
            if (n == 0) throw new IOException("이 exe 의 이미지 이름을 얻지 못했습니다(" + Marshal.GetLastWin32Error() + ")");
            return sb.ToString(0, (int)n);
        }

        static string FinalPath(SafeFileHandle h, uint flags)
        {
            var sb = new StringBuilder(32768);
            uint n = GetFinalPathNameByHandleW(h, sb, (uint)sb.Capacity, flags);
            if (n == 0 || n >= sb.Capacity) throw new IOException("연 exe 의 실제 경로를 얻지 못했습니다(" + Marshal.GetLastWin32Error() + ")");
            return sb.ToString();
        }

        /// <summary>지금 돌고 있는 이 exe 를 쥔다. 실패하면 null 과 까닭.</summary>
        public static SelfImage Open(out string error)
        {
            error = null;
            try
            {
                var before = MappedName(); // \Device\HarddiskVolumeN\…
                var h = CreateFileW(@"\\?\GLOBALROOT" + before, GENERIC_READ, FILE_SHARE_READ, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
                if (h.IsInvalid)
                {
                    error = "이 exe 를 열지 못했습니다(" + Marshal.GetLastWin32Error() + ")";
                    h.Dispose();
                    return null;
                }
                var opened = FinalPath(h, VOLUME_NAME_NT);
                var after = MappedName();
                if (!string.Equals(opened, after, StringComparison.OrdinalIgnoreCase))
                {
                    error = "연 파일이 돌고 있는 exe 와 다릅니다(" + opened + " ≠ " + after + ")";
                    h.Dispose();
                    return null;
                }
                var dos = Binaries.StripPrefix(FinalPath(h, VOLUME_NAME_DOS));
                return new SelfImage { Stream = new FileStream(h, FileAccess.Read), Path = dos };
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return null;
            }
        }

        public void Dispose()
        {
            if (Stream != null) { Stream.Dispose(); Stream = null; }
        }
    }

    public static class Installer
    {
        /// <summary>
        /// held 의 바이트를 target 으로. 폴더는 policy 의 전용 폴더여야 한다 — 주인이 정책의 SID 면 권한을 고쳐 쓰고, 아니거나 링크면
        /// 옆으로 비키고 새로 만든다(SecureDir.Prepare). 임시 이름에 쓰고 SHA-256 을 견준 뒤 옮기고, 옮긴 뒤 한 번 더 견준다.
        /// 이미 같은 바이트면 쓰지 않는다. 실패하면 false 와 까닭.
        /// </summary>
        public static bool CopyVerified(Stream held, string target, DirPolicy policy, List<string> notes, out string error)
        {
            error = null;
            target = System.IO.Path.GetFullPath(target);
            var dir = System.IO.Path.GetDirectoryName(target);
            using (var hd = SecureDir.Prepare(dir, policy, true, notes, out error))
            {
                if (hd == null) return false;
                string want;
                held.Position = 0;
                want = Binaries.Sha256(held);
                try
                {
                    SecureDir.CheckNoReparse(dir, target);
                    if (File.Exists(target) && HashOf(target) == want) return true;
                    var tmp = System.IO.Path.Combine(dir, System.IO.Path.GetFileName(target) + ".new-" + Guid.NewGuid().ToString("N"));
                    try
                    {
                        using (var fs = new FileStream(tmp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                        {
                            held.Position = 0;
                            held.CopyTo(fs);
                            fs.Flush(true);
                        }
                        if (HashOf(tmp) != want) { error = "복사본의 SHA-256 이 쥔 exe 와 다릅니다"; return false; }
                        // File.Replace 는 옛 파일의 권한을 남긴다 — 지우고 옮겨 폴더에서 물려받은 권한(전용)으로 둔다.
                        if (File.Exists(target)) File.Delete(target);
                        File.Move(tmp, target);
                    }
                    finally
                    {
                        try { if (File.Exists(tmp)) File.Delete(tmp); } catch (IOException) { }
                    }
                    if (HashOf(target) != want) { error = "옮긴 사본의 SHA-256 이 쥔 exe 와 다릅니다"; return false; }
                    return true;
                }
                catch (Exception ex)
                {
                    error = ex.Message;
                    return false;
                }
            }
        }

        static string HashOf(string path)
        {
            using (var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read)) return Binaries.Sha256(fs);
        }
    }
}
