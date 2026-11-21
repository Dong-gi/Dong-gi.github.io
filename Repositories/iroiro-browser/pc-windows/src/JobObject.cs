// 작업 개체(Job Object) — 도우미가 죽으면 tun2proxy 도 함께 끝나게.
// JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE: 작업 개체의 마지막 핸들이 닫히면 그 안의 프로세스를 모두 끝낸다. 핸들은 도우미만 쥔다
// (상속되지 않게 만든다) — 도우미가 어떻게 끝나든(강제 종료·충돌) 커널이 핸들을 닫고 tun2proxy 가 끝난다. 그 뒤 남은 경로는
// 다음 실행이 상태 파일로 걷는다(Controller.StartupCheck).
// 한계: Process.Start 는 멈춘 채 띄우지 못해 띄운 직후에 넣는다 — 그 사이(수 ms)에 tun2proxy 가 띄운 자식은 작업 밖에 남을 수
// 있다(--setup 의 route·netsh 는 TUN 을 세운 뒤라 그보다 늦다고 보았다 — 재지 않았다).
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace WebTether
{
    public sealed class JobObject : IDisposable
    {
        [StructLayout(LayoutKind.Sequential)]
        struct JOBOBJECT_BASIC_LIMIT_INFORMATION
        {
            public long PerProcessUserTimeLimit;
            public long PerJobUserTimeLimit;
            public uint LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass;
            public uint SchedulingClass;
        }

        [StructLayout(LayoutKind.Sequential)]
        struct IO_COUNTERS
        {
            public ulong ReadOperationCount, WriteOperationCount, OtherOperationCount, ReadTransferCount, WriteTransferCount, OtherTransferCount;
        }

        [StructLayout(LayoutKind.Sequential)]
        struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern IntPtr CreateJobObjectW(IntPtr sa, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool SetInformationJobObject(IntPtr job, int infoClass, ref JOBOBJECT_EXTENDED_LIMIT_INFORMATION info, uint length);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool IsProcessInJob(IntPtr process, IntPtr job, out bool result);

        [DllImport("kernel32.dll", SetLastError = true)]
        static extern bool CloseHandle(IntPtr h);

        const int JobObjectExtendedLimitInformation = 9;
        const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

        IntPtr handle;

        JobObject(IntPtr h) { handle = h; }

        /// <summary>마지막 핸들이 닫히면 안의 프로세스를 모두 끝내는 이름 없는 작업 개체. 실패하면 null 과 까닭.</summary>
        public static JobObject CreateKillOnClose(out string error)
        {
            error = null;
            var h = CreateJobObjectW(IntPtr.Zero, null); // 보안 특성 없음 — 핸들이 상속되지 않는다
            if (h == IntPtr.Zero) { error = "CreateJobObject 실패(" + Marshal.GetLastWin32Error() + ")"; return null; }
            var info = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if (!SetInformationJobObject(h, JobObjectExtendedLimitInformation, ref info, (uint)Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION))))
            {
                error = "SetInformationJobObject 실패(" + Marshal.GetLastWin32Error() + ")";
                CloseHandle(h);
                return null;
            }
            return new JobObject(h);
        }

        public bool Assign(Process p, out string error)
        {
            error = null;
            if (handle == IntPtr.Zero) { error = "닫힌 작업 개체"; return false; }
            try
            {
                if (AssignProcessToJobObject(handle, p.Handle)) return true;
                error = "AssignProcessToJobObject 실패(" + Marshal.GetLastWin32Error() + ")";
            }
            catch (InvalidOperationException ex) { error = ex.Message; }
            catch (Win32Exception ex) { error = ex.Message; }
            return false;
        }

        /// <summary>p 가 이 작업 개체 안에 있는가(시험).</summary>
        public bool Contains(Process p)
        {
            bool r;
            return handle != IntPtr.Zero && IsProcessInJob(p.Handle, handle, out r) && r;
        }

        /// <summary>핸들을 닫는다 — 안에 남은 프로세스가 모두 끝난다.</summary>
        public void Dispose()
        {
            var h = handle;
            handle = IntPtr.Zero;
            if (h != IntPtr.Zero) CloseHandle(h);
        }
    }
}
