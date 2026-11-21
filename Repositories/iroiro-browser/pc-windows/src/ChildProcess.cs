// tun2proxy 자식 프로세스 — 띄우기(작업 개체에 넣는다 — 도우미가 죽으면 함께 끝난다), 출력 받기, Ctrl+C 로 끝내기, 늦으면 강제로.
// tun2proxy 0.8.7 은 ctrlc2 로 Ctrl+C·Ctrl+Break 를 듣고, 들으면 --setup 이 바꾼 경로를 스스로 걷는다
// (src/bin/main.rs → general_api.rs 의 tproxy_remove). 강제로 끝내면 그 걷기가 돌지 않는다.
using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

namespace WebTether
{
    public static class ConsoleCtrl
    {
        delegate bool HandlerRoutine(uint type);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool AttachConsole(uint pid);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool FreeConsole();
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool SetConsoleCtrlHandler(IntPtr handler, bool add);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool SetConsoleCtrlHandler(HandlerRoutine handler, bool add);
        [DllImport("kernel32.dll", SetLastError = true)] static extern bool GenerateConsoleCtrlEvent(uint ev, uint group);

        const uint CTRL_C_EVENT = 0;
        const uint CTRL_BREAK_EVENT = 1;
        static readonly object Gate = new object();
        static HandlerRoutine swallow;

        /// <summary>
        /// 이 프로세스는 Ctrl+C 를 무시한다(SetConsoleCtrlHandler(NULL, TRUE)) — 자식의 콘솔에 잠깐 붙어 보낼 때 함께 받아
        /// 끝나지 않게. 삼키는 처리기만 두었더니 이 프로세스가 STATUS_CONTROL_C_EXIT 로 끝났다(시험). 그런데 이 깃발은
        /// **자식에게 물려 간다** — 켠 채 띄운 자식은 Ctrl+C 를 받지 못했다(시험). 그래서 자식을 띄우는 동안만 끈다
        /// (WithCtrlCInheritable — 보내기와 같은 자물쇠 안이라 그사이 받을 Ctrl+C 가 없다).
        /// </summary>
        public static void IgnoreForThisProcess()
        {
            swallow = t => t == CTRL_C_EVENT || t == CTRL_BREAK_EVENT;
            SetConsoleCtrlHandler(swallow, true);
            SetConsoleCtrlHandler(IntPtr.Zero, true);
        }

        public static T WithCtrlCInheritable<T>(Func<T> start)
        {
            lock (Gate)
            {
                SetConsoleCtrlHandler(IntPtr.Zero, false);
                try { return start(); }
                finally { SetConsoleCtrlHandler(IntPtr.Zero, true); }
            }
        }

        /// <summary>
        /// 자식의 (보이지 않는) 콘솔에 잠깐 붙어 그 콘솔의 모든 프로세스에 Ctrl+C 를 보낸다.
        /// 이 프로세스에 콘솔이 있으면 떼어 낸다(GUI 도우미에는 없다).
        /// </summary>
        public static bool SendCtrlC(int pid, out string error)
        {
            error = null;
            lock (Gate)
            {
                FreeConsole();
                if (!AttachConsole((uint)pid))
                {
                    error = "AttachConsole 실패(" + Marshal.GetLastWin32Error() + ")";
                    return false;
                }
                bool ok = GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
                if (!ok) error = "GenerateConsoleCtrlEvent 실패(" + Marshal.GetLastWin32Error() + ")";
                FreeConsole();
                return ok;
            }
        }
    }

    public interface IChild : IDisposable
    {
        int Pid { get; }
        bool HasExited { get; }
        DateTime StartedAt { get; }
        /// <summary>Ctrl+C 를 보내고 timeoutMs 안에 끝나면 true. 막는 호출 — UI 스레드에서 부르지 않는다.</summary>
        bool StopGracefully(int timeoutMs, out string detail);
        void Kill();
        event Action<int> Exited;
    }

    public sealed class ChildProcess : IChild
    {
        readonly Process p;
        readonly Action<string, string> output;
        JobObject job;
        int pid;
        public DateTime StartedAt { get; private set; }
        int exitRaised;
        readonly object exitGate = new object();
        Action<int> exitHandlers;
        bool exitDone;
        int exitCode = -1;

        /// <summary>
        /// 끝났다는 알림. 이미 끝난 뒤에 구독하면 그 자리에서 한 번 부른다 — 띄우자마자 끝난 자식(인자 오류·wintun 실패)은
        /// Process.Exited 가 Start 가 돌아오기 전·구독하기 전에 올 수 있다. 그때 알림을 잃으면 컨트롤러가 '연결 중' 에 영영 멈췄다.
        /// </summary>
        public event Action<int> Exited
        {
            add
            {
                bool fire;
                int code;
                lock (exitGate)
                {
                    exitHandlers += value;
                    fire = exitDone;
                    code = exitCode;
                }
                if (fire && value != null) value(code);
            }
            remove { lock (exitGate) exitHandlers -= value; }
        }

        /// <summary>끝났다는 알림을 이미 냈는가(시험).</summary>
        internal bool ExitDelivered { get { lock (exitGate) return exitDone; } }

        ChildProcess(Process p, Action<string, string> output)
        {
            this.p = p;
            this.output = output;
        }

        public int Pid { get { return pid; } }
        public bool HasExited { get { try { return p.HasExited; } catch (InvalidOperationException) { return true; } } }
        /// <summary>도우미가 죽으면 함께 끝나는 작업 개체에 들었는가(시험).</summary>
        public bool InKillOnCloseJob { get { var j = job; try { return j != null && j.Contains(p); } catch (InvalidOperationException) { return false; } } }

        /// <summary>
        /// 셸 없이 띄운다. 창 없음(CREATE_NO_WINDOW — 자식은 보이지 않는 제 콘솔을 얻는다), 출력은 파이프로.
        /// workDir 는 System32 — tun2proxy 는 시작할 때 현재 폴더와 그 위에서 .env 를 읽는다(dotenvy).
        /// </summary>
        public static ChildProcess Start(string exe, IList<string> args, string workDir, Action<string, string> output, out string error)
        {
            error = null;
            var psi = new ProcessStartInfo(exe, CommandLine.Join(args))
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                RedirectStandardInput = false,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8,
                WorkingDirectory = workDir,
            };
            // 사용자가 둔 RUST_LOG 가 --verbosity 를 덮지 않게, 색 부호는 끄게.
            var drop = new List<string>();
            foreach (DictionaryEntry e in psi.EnvironmentVariables)
            {
                var k = (string)e.Key;
                if (k.StartsWith("RUST_", StringComparison.OrdinalIgnoreCase)) drop.Add(k);
            }
            foreach (var k in drop) psi.EnvironmentVariables.Remove(k);
            psi.EnvironmentVariables["RUST_LOG_STYLE"] = "never";
            psi.EnvironmentVariables["NO_COLOR"] = "1";

            var p = new Process { StartInfo = psi, EnableRaisingEvents = true };
            var child = new ChildProcess(p, output);
            p.OutputDataReceived += (s, e) => { if (e.Data != null && output != null) output("tun2proxy", e.Data); };
            p.ErrorDataReceived += (s, e) => { if (e.Data != null && output != null) output("tun2proxy", e.Data); };
            p.Exited += (s, e) => child.RaiseExited();
            string jobError;
            var job = JobObject.CreateKillOnClose(out jobError);
            try
            {
                if (!ConsoleCtrl.WithCtrlCInheritable(() => p.Start()))
                {
                    error = "프로세스를 띄우지 못했습니다";
                    if (job != null) job.Dispose();
                    p.Dispose();
                    return null;
                }
            }
            catch (Exception ex)
            {
                error = ex.Message;
                if (job != null) job.Dispose();
                p.Dispose();
                return null;
            }
            child.pid = p.Id;
            // 띄운 곧바로 작업 개체에 — 도우미가 죽으면(핸들이 닫히면) 함께 끝난다. 넣지 못해도 띄운 채 두고 알린다.
            if (job != null && job.Assign(p, out jobError)) child.job = job;
            else
            {
                if (job != null) job.Dispose();
                if (output != null) output("작업 개체", "넣지 못함 — 도우미가 죽으면 tun2proxy 가 남을 수 있습니다: " + jobError);
            }
            child.StartedAt = DateTime.Now;
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();
            return child;
        }

        void RaiseExited()
        {
            if (Interlocked.Exchange(ref exitRaised, 1) != 0) return;
            int code = -1;
            try
            {
                p.WaitForExit(); // 출력 끝까지 받는다
                code = p.ExitCode;
            }
            catch (InvalidOperationException) { }
            catch (System.ComponentModel.Win32Exception) { }
            Action<int> h;
            lock (exitGate)
            {
                exitDone = true;
                exitCode = code;
                h = exitHandlers;
            }
            if (h != null) h(code);
        }

        /// <summary>
        /// 다 쓴 뒤 — Process 핸들을 닫고, 작업 개체를 닫는다(아직 남은 것이 있으면 그때 끝난다). 자식이 끝났거나 끝낸 뒤에 부른다.
        /// </summary>
        public void Dispose()
        {
            var j = Interlocked.Exchange(ref job, null);
            if (j != null) j.Dispose();
            try { p.Dispose(); } catch (InvalidOperationException) { }
        }

        public bool StopGracefully(int timeoutMs, out string detail)
        {
            detail = null;
            if (HasExited) return true;
            string err;
            if (!ConsoleCtrl.SendCtrlC(pid, out err)) detail = err;
            bool done = p.WaitForExit(timeoutMs);
            if (done) p.WaitForExit();
            return done;
        }

        public void Kill()
        {
            try { if (!p.HasExited) p.Kill(); } catch (InvalidOperationException) { } catch (System.ComponentModel.Win32Exception) { }
            try { p.WaitForExit(3000); } catch (InvalidOperationException) { }
        }
    }

    /// <summary>띄우지 않는 자식 — 명령줄만 기록한다(--dry-run·시험 판).</summary>
    public sealed class DryRunChild : IChild
    {
        static int nextPid = 900000;
        volatile bool exited;
        public int Pid { get; private set; }
        public bool HasExited { get { return exited; } }
        public DateTime StartedAt { get; private set; }
        public event Action<int> Exited;

        public DryRunChild()
        {
            Pid = Interlocked.Increment(ref nextPid);
            StartedAt = DateTime.Now;
        }

        public bool StopGracefully(int timeoutMs, out string detail)
        {
            detail = "dry-run";
            Finish(0);
            return true;
        }

        public void Kill() { Finish(-1); }

        public void Dispose() { }

        /// <summary>시험 — 자식이 저절로 끝난 것처럼.</summary>
        public void SimulateExit(int code) { Finish(code); }

        void Finish(int code)
        {
            if (exited) return;
            exited = true;
            var h = Exited;
            if (h != null) h(code);
        }
    }
}
