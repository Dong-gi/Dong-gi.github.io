// 시스템을 바꾸는 일(Wi-Fi 연결·경로)은 여기로만 간다 — 시험 판·--dry-run 은 DryRunOps 가 기록만 한다.
// '시작 시 실행'(예약 작업·Program Files 사본·launch.cmd·옛 사본 흐름)은 걷었다 — 서비스(Service.cs·ServiceInstall.cs)가 대신한다.
// 걷은 코드는 git 이력에 있다(d95c1fcb).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;

namespace WebTether
{
    public interface ISystemOps
    {
        bool WlanConnect(string profile, out string output);
        bool Route(RouteAction action, out string output);
    }

    public sealed class DryRunOps : ISystemOps
    {
        readonly Action<string> log;
        public readonly List<string> Calls = new List<string>();

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
    }

    /// <summary>System32 의 도구를 돌린다 — 끝 코드(시간 초과면 -1)와 출력.</summary>
    public delegate int ToolRunner(string exe, string args, int timeoutMs, out string output);

    public sealed class SystemOps : ISystemOps
    {
        public const int TimedOut = -1;
        static string Sys32 { get { return Environment.GetFolderPath(Environment.SpecialFolder.System); } }

        readonly Action<string> note;

        public SystemOps(Action<string> note)
        {
            this.note = note;
        }

        /// <summary>System32 의 그 도구를 셸 없이 — 현재 폴더는 System32.</summary>
        public static int Run(string exe, string args, int timeoutMs, out string output)
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
            bool ok = Run("netsh.exe", CommandLine.NetshWlanConnect(profile), 15000, out output) == 0;
            if (!ok && note != null) note("netsh wlan connect 실패 — 서비스(SYSTEM)는 '모든 사용자' Wi-Fi 프로필만 쓸 수 있습니다: " + output);
            return ok;
        }

        public bool Route(RouteAction action, out string output)
        {
            return Run("route.exe", CommandLine.Join(action.RouteArgs()), 10000, out output) == 0;
        }
    }
}
