// 관리자 상태 폴더·시작 시 실행의 사본·하나만 실행 — 실제 ProgramData·Program Files·전역 이름에는 손대지 않는다.
// 스크래치 폴더를 뿌리로 주고 '이 사용자 전용' 정책(관리자 아닌 권한으로 만들 수 있는 꼴)으로 시험한다. 관리자 전용 정책은
// 거절하는 길(주인이 다름·만들 수 없음)만 여기서 보고, 실제로 만드는 길은 기기에서 확인한다.
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Threading;
using System.Windows.Forms;

namespace WebTether.Tests
{
    static class SecurityTests
    {
        static readonly SecurityIdentifier Everyone = new SecurityIdentifier(WellKnownSidType.WorldSid, null);

        static void GrantEveryone(string dir)
        {
            var sec = Directory.GetAccessControl(dir);
            sec.AddAccessRule(new FileSystemAccessRule(Everyone, FileSystemRights.Modify,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            Directory.SetAccessControl(dir, sec);
        }

        static string[] Aside(string path)
        {
            return Directory.GetFileSystemEntries(Path.GetDirectoryName(path), Path.GetFileName(path) + ".untrusted-*");
        }

        static string Clean(string dir, DirPolicy policy)
        {
            bool ok;
            return SecureDir.CheckDescriptor(SecureDir.ReadDescriptor(dir), policy, out ok);
        }

        public static void Add(Action<string, Action> T)
        {
            T("상태 폴더: 새로 만들면 상속을 끊고 정책의 SID 에게만(모든 사람·사용자 그룹에는 읽기도 없음), 쥔 동안 이름을 바꿀 수 없다", () =>
            {
                var root = Path.Combine(TestMain.Scratch("sec-new"), "WebTether");
                var policy = DirPolicy.CurrentUser();
                var notes = new List<string>();
                string err;
                using (var h = SecureDir.Prepare(root, policy, notes, out err))
                {
                    A.True(h != null, "마련함: " + err);
                    var sd = SecureDir.ReadDescriptor(root);
                    A.True((sd.ControlFlags & ControlFlags.DiscretionaryAclProtected) != 0, "상속을 끊음");
                    A.Eq(policy.Owner, sd.Owner, "주인");
                    foreach (GenericAce ace in sd.DiscretionaryAcl)
                        A.True(policy.IsTrusted(((KnownAce)ace).SecurityIdentifier), "정책 밖의 권한 줄: " + ((KnownAce)ace).SecurityIdentifier);
                    A.Throws<IOException>(() => Directory.Move(root, root + "-moved"), "쥔 동안 이름 바꾸기");
                    var logs = Path.Combine(root, "logs");
                    Directory.CreateDirectory(logs);
                    A.Eq((string)null, Clean(logs, policy), "하위도 물려받아 전용");
                }
                using (var h = SecureDir.Prepare(root, policy, notes, out err))
                    A.True(h != null && notes.Count == 0, "다시 마련하면 그대로 씀: " + err + string.Join("|", notes.ToArray()));
            });

            T("상태 폴더: 남이 쓸 수 있게 미리 만들어 둔 폴더는 쓰지 않고 옆으로 비킨 뒤 새로 만든다", () =>
            {
                var root = Path.Combine(TestMain.Scratch("sec-squat"), "WebTether");
                Directory.CreateDirectory(root);
                GrantEveryone(root);
                File.WriteAllText(Path.Combine(root, "settings"), "proxy_host=evil.example\n");
                var policy = DirPolicy.CurrentUser();
                A.True(Clean(root, policy) != null, "모든 사람이 쓸 수 있으면 거절: " + Clean(root, policy));
                var notes = new List<string>();
                string err;
                using (var h = SecureDir.Prepare(root, policy, notes, out err))
                {
                    A.True(h != null, "마련함: " + err);
                    A.True(!File.Exists(Path.Combine(root, "settings")), "심어 둔 설정을 쓰지 않음");
                    A.True(notes.Any(n => n.Contains("옆으로")), "알림: " + string.Join("|", notes.ToArray()));
                    var aside = Aside(root);
                    A.Eq(1, aside.Length, "비킨 폴더 하나");
                    A.True(File.Exists(Path.Combine(aside[0], "settings")), "비킨 것은 지우지 않음");
                    A.Eq((string)null, Clean(root, policy), "새 폴더는 전용");
                }
            });

            T("상태 폴더: 접합점이면 따라가지 않고 접합점만 비킨다(대상 폴더는 그대로)", () =>
            {
                var parent = TestMain.Scratch("sec-junction");
                var target = Path.Combine(parent, "target");
                Directory.CreateDirectory(target);
                File.WriteAllText(Path.Combine(target, "keep.txt"), "keep");
                var root = Path.Combine(parent, "WebTether");
                BinariesTests.Junction(root, target);
                var notes = new List<string>();
                string err;
                using (var h = SecureDir.Prepare(root, DirPolicy.CurrentUser(), notes, out err))
                {
                    A.True(h != null, "마련함: " + err);
                    A.True((File.GetAttributes(root) & FileAttributes.ReparsePoint) == 0, "새로 만든 진짜 폴더");
                    A.Eq(1, Directory.GetFileSystemEntries(target).Length, "대상은 그대로");
                    var aside = Aside(root);
                    A.True(aside.Length == 1 && (File.GetAttributes(aside[0]) & FileAttributes.ReparsePoint) != 0, "비킨 것은 접합점 자체");
                    A.True(notes.Any(n => n.Contains("재분석")), string.Join("|", notes.ToArray()));
                }
            });

            T("상태 폴더: 관리자 전용 정책 — 사용자 소유 폴더는 주인 때문에 거절, 관리자 아닌 권한으로는 만들지 못하고 오류로 멈춘다", () =>
            {
                var d = TestMain.Scratch("sec-admin");
                var why = Clean(d, DirPolicy.AdminOnly());
                A.True(why != null && why.Contains("주인"), "거절: " + why);
                if (!Binaries.IsElevated())
                {
                    var notes = new List<string>();
                    string err;
                    var h = SecureDir.Prepare(Path.Combine(d, "WebTether"), DirPolicy.AdminOnly(), notes, out err);
                    A.True(h == null && err != null, "만들지 못함");
                    TestMain.Say("      관리자 아닌 권한: " + err);
                }
            });

            T("상태 폴더: 그 아래의 접합점으로는 로그·상태 파일을 쓰지 않는다, 폴더 밖도 거절", () =>
            {
                var parent = TestMain.Scratch("sec-sub");
                var root = Path.Combine(parent, "WebTether");
                var elsewhere = Path.Combine(parent, "elsewhere");
                Directory.CreateDirectory(elsewhere);
                string err;
                using (var h = SecureDir.Prepare(root, DirPolicy.CurrentUser(), null, out err))
                {
                    A.True(h != null, err);
                    var paths = AppPaths.Under(root);
                    BinariesTests.Junction(paths.LogDir, elsewhere);
                    var log = new Log(paths.LogFile, Log.MaxFileBytes, paths.Root);
                    log.Write("t", "hello");
                    A.Eq(0, Directory.GetFileSystemEntries(elsewhere).Length, "접합점 너머에 쓰지 않음");
                    A.Eq(1, log.Snapshot().Length, "고리 버퍼에는 남음");
                    A.Throws<IOException>(() => SafeFs.WriteAtomic(Path.Combine(paths.LogDir, "x"), "x", paths.Root), "상태 파일도 거절");
                    A.Throws<IOException>(() => SafeFs.WriteAtomic(Path.Combine(parent, "outside"), "x", paths.Root), "상태 폴더 밖");
                    new Settings().Save(paths.SettingsFile, paths.Root);
                    A.True(File.Exists(paths.SettingsFile), "보통 쓰기는 된다");
                }
            });

            T("시작 시 실행: 돌고 있는 이 exe 를 쥐면 이름 바꾸기가 막히고, 쥔 바이트가 그 파일이다", () =>
            {
                var exe = Path.GetFullPath(Application.ExecutablePath);
                string err;
                using (var self = SelfImage.Open(out err))
                {
                    A.True(self != null, "쥠: " + err);
                    A.True(string.Equals(exe, self.Path, StringComparison.OrdinalIgnoreCase), self.Path);
                    self.Stream.Position = 0;
                    A.Eq(Binaries.Sha256(File.ReadAllBytes(exe)), Binaries.Sha256(self.Stream), "같은 바이트");
                    bool renamed = false;
                    try { File.Move(exe, exe + ".moved"); renamed = true; }
                    catch (IOException) { }
                    if (renamed) File.Move(exe + ".moved", exe);
                    A.True(!renamed, "쥔 동안 이름 바꾸기가 막힘");
                }
                // 놓으면 돌고 있는 exe 도 이름이 바뀐다 — 그래서 경로가 아니라 쥔 핸들에서 복사한다.
                File.Move(exe, exe + ".moved");
                File.Move(exe + ".moved", exe);
            });

            T("시작 시 실행: 쥔 핸들에서 전용 폴더로 복사하고 해시를 견준다 — 권한이 틀린 폴더는 고치거나 지우지 않고 통째로 비키고 새로 만든다, 비키지 못하면 쓰지 않는다, 접합점 폴더 너머에는 쓰지 않는다", () =>
            {
                var parent = TestMain.Scratch("install");
                var policy = DirPolicy.CurrentUser();
                string err;
                using (var self = SelfImage.Open(out err))
                {
                    A.True(self != null, err);
                    self.Stream.Position = 0;
                    var want = Binaries.Sha256(self.Stream);
                    var dir = Path.Combine(parent, "WebTether");
                    Directory.CreateDirectory(dir);
                    GrantEveryone(dir);  // 주인은 믿을 만한데 권한이 넓다(예전 판이 만든 Program Files\WebTether)
                    // 권한이 넓던 동안 남이 넣었을 것 — exe 옆 DLL(관리자로 도는 exe 가 먼저 찾는다)과 하위 폴더.
                    File.WriteAllText(Path.Combine(dir, "version.dll"), "planted");
                    Directory.CreateDirectory(Path.Combine(dir, "sub"));
                    File.WriteAllText(Path.Combine(dir, "sub", "x.txt"), "planted");
                    var target = Path.Combine(dir, "WebTether.exe");
                    var notes = new List<string>();
                    A.True(Installer.CopyVerified(self.Stream, target, policy, notes, out err), "복사: " + err);
                    A.True(notes.Any(n => n.Contains("옆으로 옮겼습니다")), "비킴: " + string.Join("|", notes.ToArray()));
                    A.Eq(1, Directory.GetFileSystemEntries(dir).Length, "새 폴더에는 exe 하나: " + string.Join(",", Directory.GetFileSystemEntries(dir)));
                    var moved = Aside(dir);
                    A.Eq(1, moved.Length, "비킨 폴더 하나");
                    A.True(File.Exists(Path.Combine(moved[0], "version.dll")) && File.Exists(Path.Combine(moved[0], "sub", "x.txt")), "비킨 것은 지우지 않는다");

                    // 비키지 못하면(안의 파일이 열려 있음 — 이름 바꾸기가 막힌다) 쓰지 않는다.
                    var dir3 = Path.Combine(parent, "WebTether3");
                    Directory.CreateDirectory(dir3);
                    GrantEveryone(dir3);
                    using (new FileStream(Path.Combine(dir3, "busy.dll"), FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        var n3 = new List<string>();
                        A.True(!Installer.CopyVerified(self.Stream, Path.Combine(dir3, "WebTether.exe"), policy, n3, out err), "비키지 못하면 실패");
                        A.True(err.Contains("비키지도 못했습니다"), err);
                        A.True(File.Exists(Path.Combine(dir3, "busy.dll")), "지우지 않음");
                        A.True(!File.Exists(Path.Combine(dir3, "WebTether.exe")), "쓰지 않음");
                    }
                    A.Eq((string)null, Clean(dir, policy), "전용이 됨");
                    A.Eq(want, Binaries.Sha256(File.ReadAllBytes(target)), "같은 바이트");
                    A.Eq(0, Directory.GetFiles(dir, "*.new-*").Length, "임시 파일이 남지 않음");
                    var t0 = File.GetLastWriteTimeUtc(target);
                    Thread.Sleep(50);
                    A.True(Installer.CopyVerified(self.Stream, target, policy, notes, out err), err);
                    A.Eq(t0, File.GetLastWriteTimeUtc(target), "같으면 다시 쓰지 않음");
                    File.WriteAllText(target, "old build");
                    A.True(Installer.CopyVerified(self.Stream, target, policy, notes, out err), err);
                    A.Eq(want, Binaries.Sha256(File.ReadAllBytes(target)), "다르면 바꿈");

                    var t2 = Path.Combine(parent, "target2");
                    Directory.CreateDirectory(t2);
                    var dir2 = Path.Combine(parent, "WebTether2");
                    BinariesTests.Junction(dir2, t2);
                    A.True(Installer.CopyVerified(self.Stream, Path.Combine(dir2, "WebTether.exe"), policy, notes, out err), err);
                    A.Eq(0, Directory.GetFileSystemEntries(t2).Length, "접합점 너머에 쓰지 않음");
                    A.True((File.GetAttributes(dir2) & FileAttributes.ReparsePoint) == 0, "새로 만든 진짜 폴더");
                }
            });

            T("시작 시 실행: 작업 XML 은 다 쓴 뒤 읽기만 하는 핸들로 쥔다 — schtasks 처럼(읽기, 공유 Read) 열 수 있고 남은 바꾸거나 지우지 못한다", () =>
            {
                // 2026-11-21 사용자 기기: 쓰기 권한을 쥔 채 schtasks 를 돌려 '다른 프로세스가 파일을 사용 중' 으로 등록이 실패했다.
                // schtasks 가 그렇게 여는 것은 스크래치에서 틀린 XML 로 한 번 확인했다(작업은 만들어지지 않는다) — 여기서는 그 열기를 흉내 낸다.
                var d = TestMain.Scratch("task-xml");
                var path = Path.Combine(d, "task.xml");
                var bytes = System.Text.Encoding.Unicode.GetBytes("<Task/>");
                using (var held = SystemOps.WriteThenHoldForReaders(path, bytes))
                {
                    byte[] seen;
                    using (var reader = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read)) // schtasks 의 열기
                    {
                        seen = new byte[reader.Length];
                        reader.Read(seen, 0, seen.Length);
                    }
                    A.Eq(Binaries.Sha256(bytes), Binaries.Sha256(seen), "쓴 그대로 읽힌다");
                    A.Throws<IOException>(() => new FileStream(path, FileMode.Open, FileAccess.Write, FileShare.ReadWrite).Dispose(), "남의 쓰기");
                    A.Throws<IOException>(() => File.Delete(path), "남의 지우기");
                    A.True(!held.CanWrite, "쥔 핸들은 읽기만");
                }
                A.Throws<IOException>(() => SystemOps.WriteThenHoldForReaders(path, bytes).Dispose(), "이미 있으면 새로 쓰지 않는다");
            });

            T("하나만 실행: 같은 이름의 다른 종류 객체를 먼저 만들어 두어도 던지지 않고 알아챈다", () =>
            {
                var id = Guid.NewGuid().ToString("N");
                string m = @"Local\WebTether-k-" + id, e = @"Local\WebTether-ks-" + id;
                using (var fakeMutex = new EventWaitHandle(false, EventResetMode.AutoReset, m))   // 뮤텍스 자리에 이벤트
                using (var fakeEvent = new Mutex(false, e))                                       // 이벤트 자리에 뮤텍스
                using (var r = InstanceLock.Acquire(m, e, DirPolicy.CurrentUser()))
                {
                    A.Eq(InstanceStatus.Squatted, r.Status, r.Detail);
                    A.True(r.Mutex == null && r.Show == null, "쓰지 않음");
                    A.True(r.Detail.Contains("다른 종류"), r.Detail);
                }
            });

            T("하나만 실행: 정책의 주인이 만든 표지는 '이미 떠 있음', 보통 권한의 프로그램이 먼저 잡은 것은 알아챈다", () =>
            {
                var id = Guid.NewGuid().ToString("N");
                string m = @"Local\WebTether-t-" + id, e = @"Local\WebTether-ts-" + id;
                var mine = DirPolicy.CurrentUser();
                using (var first = InstanceLock.Acquire(m, e, mine))
                {
                    A.Eq(InstanceStatus.First, first.Status, first.Detail);
                    A.True(first.Show != null, "보이기 이벤트");
                    var sec = first.Mutex.GetAccessControl();
                    A.Eq(mine.Owner, sec.GetOwner(typeof(SecurityIdentifier)), "주인");
                    foreach (MutexAccessRule rule in sec.GetAccessRules(true, true, typeof(SecurityIdentifier)))
                        A.True(mine.IsTrusted((SecurityIdentifier)rule.IdentityReference), "정책 밖 권한: " + rule.IdentityReference);
                    using (var second = InstanceLock.Acquire(m, e, mine))
                    {
                        A.Eq(InstanceStatus.Another, second.Status, second.Detail);
                        A.True(second.Show != null, "첫째를 부를 수 있음");
                    }
                }
                string m2 = @"Local\WebTether-q-" + id, e2 = @"Local\WebTether-qs-" + id;
                using (var squat = new Mutex(false, m2))          // 보통 권한(이 사용자가 주인)으로 먼저
                using (var squatEvent = new EventWaitHandle(false, EventResetMode.AutoReset, e2))
                using (var r = InstanceLock.Acquire(m2, e2, DirPolicy.AdminOnly()))
                {
                    A.Eq(InstanceStatus.Squatted, r.Status, r.Detail);
                    A.True(r.Detail.Contains("주인"), r.Detail);
                    A.True(r.Show == null, "남이 만든 보이기 이벤트는 쓰지 않음");
                }
            });

            T("하나만 실행: 표지를 믿지 못해도 상태 폴더의 잠금 파일로 둘째 도우미를 막는다(접합점이면 쓰지 않고 막지도 않는다)", () =>
            {
                var paths = AppPaths.Under(TestMain.Scratch("instance-file"));
                bool busy;
                string err;
                using (var first = InstanceFile.TryHold(paths, out busy, out err))
                {
                    A.True(first != null && !busy, "첫째는 쥔다: " + err);
                    var second = InstanceFile.TryHold(paths, out busy, out err);
                    A.True(second == null, "둘째는 쥐지 못함");
                    A.True(busy, "다른 도우미가 쥐고 있다고 알아챔: " + err);
                }
                using (var again = InstanceFile.TryHold(paths, out busy, out err))
                    A.True(again != null && !busy, "첫째가 놓으면 다시 쥔다: " + err);

                var elsewhere = TestMain.Scratch("instance-file-target");
                File.Delete(paths.InstanceFile);
                BinariesTests.Junction(paths.InstanceFile, elsewhere);
                try
                {
                    var j = InstanceFile.TryHold(paths, out busy, out err);
                    A.True(j == null && !busy, "접합점이면 쥐지 않고 '이미 실행 중' 으로도 보지 않음");
                    A.True(err != null && err.Contains("재분석"), err);
                }
                finally { Directory.Delete(paths.InstanceFile); }
            });
        }
    }
}
