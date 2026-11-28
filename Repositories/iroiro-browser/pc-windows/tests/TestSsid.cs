// 시험 — 2026-11-21 사용자 결정 넷: '끊기' 는 메모리에만, Wi-Fi 이름(SSID) 접두어로만 저절로 연결, 작업 등록의 시간 초과를 실제
// 상태로 맞추기, 막는 호출(1초 점검의 살피기·설정 저장)을 일꾼 스레드로. 실제 망·작업 스케줄러는 바꾸지 않는다(SSID 는 읽기만 한 번).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace WebTether.Tests
{
    static partial class ControllerTests
    {
        const string Mine = "DIRECT-UT-시험폰";
        const string Foreign = "DIRECT-xy-Android_1234";

        static HotspotInfo Hs(string ssid)
        {
            return new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = ssid, SsidError = ssid == null ? "Wi-Fi 어댑터가 아님" : null };
        }

        /// <summary>예전 판이 남기던 끊어 둔 표시의 자리 — 이제는 쓰지도 읽지도 않는다.</summary>
        static string OffMark(Rig r) { return Path.Combine(r.Paths.Root, "user-disconnected"); }

        static void Tick(Rig r) { Run(r.C.TickAsync()); }

        /// <summary>WinForms 문맥이 깔린 이 스레드에서 비동기 저장을 기다린다 — 이어지는 일이 이 스레드로 오므로 메시지를 돌린다.</summary>
        static T Pump<T>(Task<T> t)
        {
            var sw = Stopwatch.StartNew();
            while (!t.IsCompleted)
            {
                Application.DoEvents();
                Thread.Sleep(10);
                if (sw.ElapsedMilliseconds > 20000) throw new Exception("시간 초과");
            }
            return t.Result;
        }

        /// <summary>설정 저장이 문이 열릴 때까지 막히고, 부른 스레드를 적는다(저장은 Controller 에).</summary>
        sealed class SlowHost : ISettingsHost
        {
            readonly Controller ctl;
            readonly ManualResetEventSlim gate;
            public volatile int Thread;
            public SlowHost(Controller ctl, ManualResetEventSlim gate) { this.ctl = ctl; this.gate = gate; }
            public Settings Settings { get { return ctl.Settings; } }
            public void SaveSettings(Settings s)
            {
                Thread = System.Threading.Thread.CurrentThread.ManagedThreadId;
                gate.Wait(15000);
                ctl.SaveSettings(s);
            }
            public void ApplySettings(Settings s) { ctl.ApplySettings(s); }
            public bool ChildRunning { get { return ctl.ChildRunning; } }
            public string SavingText { get { return "저장 중…"; } }
            public string LoadNotice { get { return null; } }
        }

        /// <summary>시험이 끝내기 전까지 살아 있는 자식 — Ctrl+C 는 Kill 이 올 때까지(또는 시한까지) 기다린다(경로 고리가 끊는 중의 Ctrl+C 를 앞당기는가).</summary>
        sealed class SlowChild : IChild
        {
            readonly ManualResetEventSlim dead = new ManualResetEventSlim(false);
            public int Kills;
            public int Pid { get { return 910007; } }
            public bool HasExited { get { return dead.IsSet; } }
            public DateTime StartedAt { get; set; }
            public event Action<int> Exited;
            public bool StopGracefully(int timeoutMs, out string detail) { detail = null; return dead.Wait(timeoutMs); }
            public void Kill()
            {
                Interlocked.Increment(ref Kills);
                if (dead.IsSet) return;
                dead.Set();
                var h = Exited;
                if (h != null) h(-1);
            }
            public void Dispose() { }
        }

        /// <summary>자식의 출력을 시험이 흘려 넣는다(tun2proxy 의 줄처럼). slow 면 SlowChild, 아니면 DryRunChild.</summary>
        sealed class ChattyFactory : IChildFactory
        {
            public readonly bool Slow;
            public Action<string, string> Output;
            public IChild Last;
            public int Starts;
            public readonly List<string> CommandLines = new List<string>();
            public ChattyFactory(bool slow) { Slow = slow; }
            public IChild Start(HeldBinaries bin, List<string> args, Action<string, string> output, out string error)
            {
                error = null;
                Starts++;
                CommandLines.Add(CommandLine.Join(args));
                Output = output;
                Last = Slow ? (IChild)new SlowChild { StartedAt = DateTime.Now } : new DryRunChild();
                return Last;
            }
            public void Say(string line) { Output("tun2proxy", line); }
        }

        /// <summary>tun2proxy 0.8.7 의 실제 줄 꼴(11-28 사용자 기기 로그 — env_logger 머리 + 'Beginning {session_info}').</summary>
        const string LoopLine = "[2026-11-28T03:15:16Z INFO  tun2proxy] Beginning #33 TCP 10.0.0.33:52298 -> 192.168.49.1:1080";

        static void AddSsid(Action<string, Action> T)
        {
            T("경로 고리(11-28): tun2proxy 의 'Beginning' 줄에서 프록시(폰) 주소로 가는 세션만 — TCP·UDP·어느 포트든, 그 밖은 아님", () =>
            {
                const string P = "192.168.49.1";
                A.Eq("TCP 192.168.49.1:1080", LoopGuard.Match(LoopLine, P), "0.8.7 의 실제 줄");
                A.Eq("TCP 192.168.49.1:1080", LoopGuard.Match("Beginning #1 TCP 10.0.0.33:1 -> 192.168.49.1:1080", P), "머리 없이");
                A.Eq("UDP 192.168.49.1:40123", LoopGuard.Match("[x INFO  tun2proxy] Beginning #7 UDP 10.0.0.33:50000 -> 192.168.49.1:40123", P), "UDP 묶음의 포트");
                A.Eq("TCP 192.168.49.1:8282", LoopGuard.Match("[x INFO  tun2proxy] Beginning #8 TCP 10.0.0.33:50001 -> 192.168.49.1:8282", P), "다른 포트(시스템 프록시)");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Beginning #9 UDP 10.0.0.33:50002 -> 1.0.0.1:53", P), "DNS(바뀐 목적지)");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Beginning #9 TCP 10.0.0.33:50002 -> 192.168.49.10:1080", P), "다른 주소");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Ending #33 TCP 10.0.0.33:52298 -> 192.168.49.1:1080 with \"timed out\"", P), "끝 줄은 아님");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Beginning #x TCP 10.0.0.33:1 -> 192.168.49.1:1080", P), "번호가 아님");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Beginning #1 ICMP 10.0.0.33:1 -> 192.168.49.1:1080", P), "모르는 프로토콜");
                A.Eq((string)null, LoopGuard.Match("[x INFO  tun2proxy] Beginning #1 TCP 10.0.0.33:1 => 192.168.49.1:1080", P), "화살표가 다름");
                A.Eq((string)null, LoopGuard.Match(LoopLine, "phone.example"), "프록시 주소가 IP 가 아니면 보지 않음");
                A.Eq("TCP [fe80::1]:1080", LoopGuard.Match("Beginning #2 TCP [fe80::2]:5 -> [fe80::1]:1080", "fe80::1"), "IPv6");
                A.Eq((string)null, LoopGuard.Match(null, P), "빈 줄");
                A.Eq("info", CommandLine.EffectiveVerbosity("off"), "off → info");
                A.Eq("info", CommandLine.EffectiveVerbosity("error"), "error → info");
                A.Eq("info", CommandLine.EffectiveVerbosity("warn"), "warn → info");
                A.Eq("info", CommandLine.EffectiveVerbosity("info"), "info");
                A.Eq("debug", CommandLine.EffectiveVerbosity("debug"), "debug 그대로");
                A.Eq("trace", CommandLine.EffectiveVerbosity("trace"), "trace 그대로");
                A.True(CommandLine.Join(CommandLine.Tun2proxyArgs(new Settings { Verbosity = "warn" })).Contains("--verbosity info"), "명령줄이 info 로");
                A.Eq("Wi-Fi 연결이 끊기는 중", WlanEvents.GoingDown(0x08, 20), "acm disconnecting");
                A.Eq("Wi-Fi 연결이 끊김", WlanEvents.GoingDown(0x08, 21), "acm disconnected");
                A.Eq("Wi-Fi 어댑터가 사라짐", WlanEvents.GoingDown(0x08, 14), "acm interface removal");
                A.Eq("Wi-Fi 연결을 푸는 중", WlanEvents.GoingDown(0x10, 9), "msm disassociating");
                A.Eq("Wi-Fi 연결이 끊김", WlanEvents.GoingDown(0x10, 10), "msm disconnected");
                A.Eq("Wi-Fi 어댑터가 사라짐", WlanEvents.GoingDown(0x10, 13), "msm adapter removal");
                A.Eq((string)null, WlanEvents.GoingDown(0x08, 10), "acm connection complete 는 아님");
                A.Eq((string)null, WlanEvents.GoingDown(0x10, 4), "msm connected 는 아님");
                A.Eq((string)null, WlanEvents.GoingDown(0x20, 20), "다른 출처");
            });

            T("경로 고리(11-28): 프록시 주소로 가는 세션이 보이면 곧바로 강제로 끝내고 띄운 뒤의 실패로(횟수를 씀 — 상한에서 멎음), 로그는 자식마다 한 줄, 끊는 중이면 Ctrl+C 를 앞당김", () =>
            {
                var f = new ChattyFactory(false);
                var r = Make("loop", new Settings { ReconnectInterval = 3, ReconnectMax = 1, Verbosity = "warn" }, null, f);
                Run(r.C.ConnectAsync());
                A.Eq(1, f.Starts, "띄움");
                A.True(f.CommandLines[0].Contains("--verbosity info"), "info 로 띄움: " + f.CommandLines[0]);
                var first = (DryRunChild)f.Last;
                for (int i = 0; i < 50; i++) f.Say(LoopLine.Replace("#33", "#" + (33 + i)));
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 3000, "대기");
                A.True(first.HasExited, "강제로 끝냄");
                A.True(r.C.Detail.Contains(Controller.RouteLoopWhy) && r.C.Detail.Contains("1/1"), "띄운 뒤의 실패로 횟수를 씀: " + r.C.Detail);
                Func<int> lines = () => { lock (r.Log) return r.Log.Count(l => l.Contains("경로 고리를 막으려고 tun2proxy 를 멈췄습니다")); };
                A.Eq(1, lines(), "자식마다 한 줄");
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("경로 고리 — 곧바로 끝냅니다")), "Ctrl+C 를 기다리지 않음");
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => f.Starts == 2, 5000, "다시 띄움");
                f.Say(LoopLine);
                A.WaitUntil(() => r.C.State == TetherState.Error, 3000, "상한에서 멎음 — " + r.C.Detail);
                A.Eq(2, lines(), "새 자식에서 한 줄 더");
                lock (r.Notices) A.Eq(1, r.Notices.Count(n => n.StartsWith("연결이 끊겼습니다")), "알림은 한 번");
                // 끊는 중(핫스팟 사라짐 — Ctrl+C 를 기다림)에 고리가 보이면 기다리지 않고 끝낸다
                var g = new ChattyFactory(true);
                var s = Make("loop-stopping", null, null, g);
                Run(s.C.ConnectAsync());
                var slow = (SlowChild)g.Last;
                s.C.NetworkQuietSeconds = 0;
                s.Hotspot = null;
                var sw = Stopwatch.StartNew();
                s.C.NetworkChanged(); // 연결 중의 알림 — 곧바로 핫스팟을 보고 Ctrl+C
                A.WaitUntil(() => { lock (s.Log) return s.Log.Any(l => l.Contains("Ctrl+C 를 보냅니다")); }, 3000, "Ctrl+C");
                g.Say(LoopLine);
                A.WaitUntil(() => slow.HasExited, 3000, "고리가 보이자 곧바로 끝냄");
                A.True(sw.ElapsedMilliseconds < Controller.StopGraceMs, "Ctrl+C 의 시한(" + Controller.StopGraceMs + " ms)을 기다리지 않음: " + sw.ElapsedMilliseconds);
                A.WaitUntil(() => s.C.State == TetherState.Waiting, 5000, "상태는 핫스팟 사라짐이 정함 — " + s.C.Detail);
                A.True(s.C.Detail.Contains("핫스팟"), s.C.Detail);
            });

            T("앞서 내리기(11-28): 연결 중의 주소 바뀜·Wi-Fi 끊김 알림이면 Tick 없이 곧바로 핫스팟을 보고 사라졌으면 내린다 — 핫스팟이 그대로면(TUN 자신의 알림) 그대로, 다른 어댑터의 Wi-Fi 알림은 보지 않음", () =>
            {
                var f = new ChattyFactory(false);
                var r = Make("link", null, null, f);
                r.C.NetworkQuietSeconds = 0;
                Run(r.C.ConnectAsync());
                r.C.NetworkChanged(); // TUN 이 생긴 알림 — 핫스팟은 그대로
                Thread.Sleep(300);
                A.True(r.C.ChildRunning && r.C.State == TetherState.Connecting, "그대로");
                r.Hotspot = null;
                r.C.NetworkChanged();
                A.WaitUntil(() => !r.C.ChildRunning, 3000, "Tick 없이 곧바로 내림");
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 3000, "대기 — " + r.C.Detail);
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("알림(주소 바뀜)으로 곧바로 확인함")), "적음");

                // Wi-Fi 끊김 알림 — 알림 때는 핫스팟이 아직 보이고, 잠시 뒤 사라진다(빠른 점검 창이 잡는다)
                var w = new ChattyFactory(false);
                var x = Make("link-wlan", null, null, w);
                var mine = Guid.NewGuid();
                x.Hotspot = Hs(Mine);
                x.Hotspot.AdapterId = mine.ToString("B");
                x.C.FastCheckMs = 50;
                x.C.FastWindowMs = 3000;
                Run(x.C.ConnectAsync());
                x.C.WlanGoingDown(Guid.NewGuid(), "Wi-Fi 연결이 끊기는 중");
                x.Hotspot = null;
                Thread.Sleep(500);
                A.True(x.C.ChildRunning, "다른 어댑터의 알림은 보지 않음");
                x.Hotspot = Hs(Mine);
                x.Hotspot.AdapterId = mine.ToString("B");
                x.C.WlanGoingDown(mine, "Wi-Fi 연결이 끊기는 중");
                Thread.Sleep(300);
                A.True(x.C.ChildRunning, "알림 때는 아직 핫스팟이 보임 — 그대로");
                x.Hotspot = null; // 주소가 빠짐
                A.WaitUntil(() => !x.C.ChildRunning, 2000, "빠른 점검 창 안에서 내림(Tick 없이)");
                lock (x.Log) A.True(x.Log.Any(l => l.Contains("알림(Wi-Fi 연결이 끊기는 중)으로 곧바로 확인함")), "적음");
            });


            T("저절로 다시 연결(9차): 꺼짐·오류 동안 접두어에 맞는 Wi-Fi 가 나타나거나 망이 바뀌면 다시 시작 — 처음 본 것은 바탕만, 접두어 밖은 아님", () =>
            {
                var r = Make("rearm", null);
                r.C.RearmPollSeconds = 0;
                r.Others.Add(4242); // 다른 tun2proxy — 다시 하지 않는 오류로 멈춘다
                Run(r.C.AutoConnectAsync());
                A.Eq(TetherState.Error, r.C.State, "오류로 멈춤");
                r.Others.Clear();
                Tick(r);
                A.True(r.C.State == TetherState.Error && r.F.CommandLines.Count == 0, "처음 본 핫스팟은 바탕만(이미 있던 것으로 다시 시작하지 않음)");
                r.Hotspot = null;
                Tick(r);
                r.Hotspot = Hs(Foreign);
                Tick(r);
                A.True(r.C.State == TetherState.Error && r.F.CommandLines.Count == 0, "접두어 밖 Wi-Fi 는 저절로 붙지 않음");
                r.Hotspot = Hs(Mine);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "나타나면 다시 시작해 띄움");
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("저절로 연결을 다시 시작합니다") && l.Contains("나타남")), "적음");
                // 망 바뀜(10차) — 되풀이하지 않는 오류로 멈췄으면 망 바뀜만으로는 다시 시작하지 않는다
                var r2 = Make("rearm-net", null);
                r2.C.RearmPollSeconds = 3600;
                r2.C.NetworkQuietSeconds = 0;
                r2.Others.Add(4242);
                Run(r2.C.AutoConnectAsync());
                r2.Others.Clear();
                Tick(r2);
                r2.C.NetworkChanged();
                Tick(r2);
                A.True(r2.C.State == TetherState.Error && r2.F.CommandLines.Count == 0, "다른 tun2proxy 로 멈췄으면 망 바뀜만으로는 다시 시작하지 않음");
                // 띄우기 전의 실패(폰 프록시 없음)로 멈췄으면 — 멈춘 직후의 알림은 세지 않고, 그 뒤의 알림이면 곧바로 보고 다시 시작
                var r3 = Make("rearm-net2", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r3.C.RearmPollSeconds = 3600;
                r3.C.StartupWaitSeconds = 0;
                r3.C.NetworkQuietSeconds = 3600;
                r3.ProbeOk = false;
                Run(r3.C.AutoConnectAsync());
                A.Eq(TetherState.Waiting, r3.C.State, "한 번은 대기");
                r3.C.NetworkChanged(); // 시도 중의 알림 — 세지 않음
                Thread.Sleep(3100);
                Tick(r3);
                A.WaitUntil(() => r3.C.State == TetherState.Error, 5000, "오류");
                r3.ProbeOk = true;
                Tick(r3);
                A.Eq(0, r3.F.CommandLines.Count, "시도 중에 온 알림은 남지 않음");
                r3.C.NetworkChanged();
                Tick(r3);
                A.Eq(0, r3.F.CommandLines.Count, "멈춘 직후의 알림은 세지 않음");
                r3.C.NetworkQuietSeconds = 0;
                r3.C.NetworkChanged();
                Tick(r3);
                A.Eq(1, r3.F.CommandLines.Count, "띄우기 전의 실패로 멈췄으면 망 바뀜 알림으로 곧바로 다시 시작");
                // 시작할 때 남은 깃발을 지운다 — 센 알림 뒤에 로그온으로 시작해 다시 멈췄으면 그 알림으로 또 시작하지 않는다
                var r4 = Make("rearm-net3", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r4.C.RearmPollSeconds = 3600;
                r4.C.StartupWaitSeconds = 0;
                r4.C.NetworkQuietSeconds = 0;
                r4.C.RearmBaseGapSeconds = 0; // 예산의 간격이 가리지 않게 — 깃발 지우기만 본다
                r4.ProbeOk = false;
                Run(r4.C.AutoConnectAsync());
                Thread.Sleep(3100);
                Tick(r4);
                A.WaitUntil(() => r4.C.State == TetherState.Error, 5000, "오류");
                Tick(r4);
                r4.C.NetworkChanged();
                Run(r4.C.RearmAsync("콘솔 로그온(세션 1)", true));
                A.Eq(TetherState.Waiting, r4.C.State, "로그온으로 시작 — 대기");
                Thread.Sleep(3100);
                Tick(r4);
                A.WaitUntil(() => r4.C.State == TetherState.Error, 5000, "다시 오류");
                Tick(r4);
                A.Eq(TetherState.Error, r4.C.State, "시작 전에 센 망 바뀜으로 또 시작하지 않음 — " + r4.C.Detail);
            });

            T("저절로 다시 연결(10차): 뜨자마자 죽는 tun2proxy 가 시도마다 망 바뀜을 일으켜도 다시 시작하지 않는다 — Wi-Fi 가 사라졌다 나타나면 다시, '연결이 끊겼습니다' 는 한 번", () =>
            {
                var r = Make("rearm-crash", new Settings { ReconnectInterval = 3, ReconnectMax = 1 });
                r.C.RearmPollSeconds = 0;
                r.C.NetworkQuietSeconds = 0;
                Run(r.C.AutoConnectAsync());
                A.Eq(1, r.F.CommandLines.Count, "띄움");
                r.C.NetworkChanged(); // TUN 이 생김
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "대기 1/1");
                r.C.NetworkChanged(); // TUN 이 사라짐
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 2, 5000, "다시 띄움");
                r.C.NetworkChanged();
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "다시 연결을 멈춤");
                for (int i = 0; i < 10; i++)
                {
                    r.C.NetworkChanged();
                    Tick(r);
                }
                A.True(r.C.State == TetherState.Error && r.F.CommandLines.Count == 2, "망 바뀜만으로는 다시 시작하지 않음 — 띄운 수 " + r.F.CommandLines.Count);
                r.Hotspot = null;
                Tick(r);
                r.Hotspot = Hs(Mine);
                Tick(r);
                A.Eq(3, r.F.CommandLines.Count, "Wi-Fi 가 사라졌다 나타나면 다시 시작");
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "대기");
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 4, 5000, "다시 띄움");
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Error, 5000, "다시 연결을 또 멈춤");
                lock (r.Notices)
                {
                    var all = string.Join("|", r.Notices.ToArray());
                    A.Eq(1, r.Notices.Count(n => n.StartsWith("연결이 끊겼습니다")), "'연결이 끊겼습니다' 는 한 번: " + all);
                    A.Eq(1, r.Notices.Count(n => n.StartsWith("다시 연결을 멈췄습니다")), "'다시 연결을 멈췄습니다' 는 한 번: " + all);
                }
                Run(r.C.DisconnectAsync());
            });

            T("저절로 다시 연결(10차): 예산 — 한 시간에 세 번, 잇달아 하면 간격을 두 배로, 미룬 것은 예산이 열리면 잇고, 로그온·'연결' 은 예산을 새로", () =>
            {
                int listed = 0;
                var r = Make("rearm-budget", null);
                r.C.RearmPollSeconds = 0;
                r.C.RearmBaseGapSeconds = 0;
                r.C.Tun2proxyLister = () => { Interlocked.Increment(ref listed); return new List<int> { 4242 }; }; // 시도마다 한 번 — 다른 tun2proxy 로 곧바로 멈춘다
                Action cycle = () => { r.Hotspot = null; Tick(r); r.Hotspot = Hs(Mine); Tick(r); };
                Run(r.C.AutoConnectAsync());
                A.True(r.C.State == TetherState.Error && listed == 1, "오류로 멈춤");
                Tick(r);
                for (int i = 0; i < 8; i++) cycle();
                A.Eq(1 + 3, listed, "한 시간에 세 번까지");
                lock (r.Log) A.Eq(1, r.Log.Count(l => l.Contains("저절로 다시 시작을 미룹니다") && l.Contains("한 시간에 3번")), "미룸은 한 줄");
                Run(r.C.RearmAsync("콘솔 로그온(세션 1)", true));
                A.Eq(5, listed, "로그온은 예산을 새로");

                int gl = 0;
                var g = Make("rearm-gap", null);
                g.C.RearmPollSeconds = 0;
                g.C.RearmBaseGapSeconds = 1;
                g.C.RearmPerHour = 100;
                g.C.Tun2proxyLister = () => { Interlocked.Increment(ref gl); return new List<int> { 4242 }; };
                Action gcycle = () => { g.Hotspot = null; Tick(g); g.Hotspot = Hs(Mine); Tick(g); };
                Run(g.C.AutoConnectAsync());
                Tick(g);
                gcycle();
                A.Eq(2, gl, "첫 번");
                gcycle();
                A.Eq(2, gl, "간격(1초) 안이면 미룸");
                Thread.Sleep(1100);
                Tick(g);
                A.Eq(3, gl, "Wi-Fi 가 그대로 보이면 간격이 지난 뒤 미룬 것을 잇는다");
                gcycle();
                Thread.Sleep(1100);
                Tick(g);
                A.Eq(3, gl, "잇달아 하면 간격이 두 배(2초)");
                Thread.Sleep(1000);
                Tick(g);
                A.Eq(4, gl, "두 배 간격이 지나면 잇는다");
                gcycle();
                A.Eq(4, gl, "간격(4초) 안");
                Run(g.C.ConnectAsync());
                A.Eq(5, gl, "사용자의 '연결'");
                gcycle();
                A.Eq(6, gl, "'연결' 은 예산을 새로");
            });

            T("저절로 다시 연결(9차): 사용자가 끊었으면 망이 바뀌어도·로그온해도 다시 시작하지 않고, 다음 '연결' 에서 풀린다 · '다시 연결' 을 끄면 하지 않는다 · 같은 까닭은 한 줄", () =>
            {
                var r = Make("rearm-user", null);
                r.C.RearmPollSeconds = 0;
                Run(r.C.ConnectAsync());
                A.Eq(1, r.F.CommandLines.Count, "연결");
                Run(r.C.DisconnectAsync());
                A.True(r.C.State == TetherState.Off && r.C.UserStopped, "사용자가 끊음");
                r.C.NetworkChanged();
                Tick(r);
                r.Hotspot = null;
                Tick(r);
                r.Hotspot = Hs(Mine);
                Tick(r);
                Run(r.C.RearmAsync("콘솔 로그온(세션 1)"));
                A.True(r.C.State == TetherState.Off && r.F.CommandLines.Count == 1, "끊은 동안은 다시 시작하지 않음");
                Run(r.C.ConnectAsync());
                A.True(!r.C.UserStopped && r.F.CommandLines.Count == 2, "'연결' 이 풀고 잇는다");
                // '다시 연결' 끔
                var off = Make("rearm-off", new Settings { Reconnect = false });
                off.Others.Add(4242);
                Run(off.C.AutoConnectAsync());
                off.Others.Clear();
                Run(off.C.RearmAsync("x"));
                A.True(off.C.State == TetherState.Error && off.F.CommandLines.Count == 0, "'다시 연결' 을 끄면 하지 않음");
                // 같은 까닭은 한 줄(되풀이가 로그를 채우지 않게)
                var lg = Make("rearm-log", null);
                lg.C.RearmPerHour = 100; // 예산(10차)은 따로 본다 — 여기는 줄 묶기만
                lg.C.RearmBaseGapSeconds = 0;
                lg.Others.Add(4242);
                Run(lg.C.AutoConnectAsync());
                for (int i = 0; i < 3; i++) Run(lg.C.RearmAsync("망이 바뀜"));
                Func<int> lines = () => { lock (lg.Log) return lg.Log.Count(l => l.Contains("저절로 연결을 다시 시작합니다 — 망이 바뀜")); };
                A.Eq(1, lines(), "같은 까닭은 한 줄");
                Run(lg.C.RearmAsync("콘솔 로그온(세션 1)"));
                lock (lg.Log) A.True(lg.Log.Any(l => l.Contains("다시 시작합니다 — 콘솔 로그온")), "다른 까닭은 적음");
                lg.C.RearmLogQuietMinutes = 0;
                Run(lg.C.RearmAsync("망이 바뀜"));
                A.Eq(2, lines(), "조용한 동안이 지나면 다시 적음");
            });

            T("SSID: 저절로 연결은 이름이 접두어로 시작할 때만 — 남의 DIRECT 망이면 띄우지 않고 묻지도 않으며 까닭을 보인다", () =>
            {
                var r = Make("ssid-auto", new Settings { Reconnect = false });
                r.Hotspot = Hs(Foreign);
                int asked = 0;
                r.C.AskConnect = (h, p) => { asked++; return true; };
                r.C.StartupWaitSeconds = 0;
                Run(r.C.AutoConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                A.Eq(0, asked, "저절로 하는 시도는 묻지 않음");
                A.Eq(TetherState.Error, r.C.State, "오류('다시 연결' 꺼짐)");
                A.True(r.C.Detail.Contains("DIRECT-UT- 로 시작하지 않음") && r.C.Detail.Contains(Foreign), r.C.Detail);
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("[Wi-Fi]") && l.Contains(Foreign)), "로그에 SSID");
            });

            T("SSID: 이름을 모르면(유선·Wi-Fi API 실패) 저절로 연결하지 않고 까닭을 보이며, 시작 직후의 기다림은 횟수를 쓰지 않는다", () =>
            {
                var r = Make("ssid-unknown", new Settings { ReconnectInterval = 3 });
                r.Hotspot = Hs(null);
                r.C.StartupWaitSeconds = 30;
                r.C.StartupRetrySeconds = 1;
                Run(r.C.AutoConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                A.Eq(TetherState.Waiting, r.C.State, "기다림");
                A.True(r.C.Detail.Contains("Wi-Fi 이름을 알 수 없음(Wi-Fi 어댑터가 아님)") && r.C.Detail.Contains("기다리는 중"), r.C.Detail);
                A.Eq(0, r.C.Retries, "횟수를 쓰지 않음");
                Thread.Sleep(1100);
                Tick(r);
                A.Eq(0, r.F.CommandLines.Count, "Tick 도 띄우지 않음");
                r.Hotspot = Hs(Mine);
                Thread.Sleep(1100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "이름이 맞게 되면 연결");
                Run(r.C.DisconnectAsync());
            });

            T("SSID: 기다리는 동안 남의 망이면 횟수를 쓰지 않고 까닭을 고쳐 보이다 우리 망이 되면 연결한다", () =>
            {
                var r = Make("ssid-wait", new Settings { ReconnectInterval = 3, ReconnectMax = 5 });
                r.C.StartupWaitSeconds = 0;
                r.Hotspot = null;
                Run(r.C.AutoConnectAsync());
                A.Eq(TetherState.Waiting, r.C.State, "대기");
                A.Eq(1, r.C.Retries, "핫스팟 없음 — 1/5");
                r.Hotspot = Hs(Foreign);
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(0, r.F.CommandLines.Count, "남의 망 — 띄우지 않음");
                A.Eq(1, r.C.Retries, "횟수를 쓰지 않음");
                A.True(r.C.Detail.Contains(Foreign) && r.C.Detail.EndsWith("기다리는 중"), "까닭을 고쳐 보임: " + r.C.Detail);
                r.Hotspot = Hs(Mine);
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "우리 망 — 연결");
                Run(r.C.DisconnectAsync());
            });

            T("SSID: 손으로 누른 '연결' 은 남의 망이면 한 번 묻는다 — '아니요' 면 꺼짐, '예' 면 연결하고 그 이름은 이 실행 동안 다시 연결도 한다", () =>
            {
                var r = Make("ssid-ask", new Settings { ReconnectInterval = 3 });
                r.Hotspot = Hs(Foreign);
                var asked = new List<string>();
                bool answer = false;
                r.C.AskConnect = (h, p) => { lock (asked) asked.Add(h.Ssid + "|" + p); return answer; };
                Run(r.C.ConnectAsync());
                A.Eq(1, asked.Count, "한 번 물음");
                A.Eq(Foreign + "|DIRECT-UT-", asked[0], "이름과 접두어를 넘김");
                A.Eq(0, r.F.CommandLines.Count, "'아니요' — 띄우지 않음");
                A.Eq(TetherState.Off, r.C.State, "꺼짐 — " + r.C.Detail);
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(0, r.F.CommandLines.Count, "다시 하지 않음");

                answer = true;
                Run(r.C.ConnectAsync());
                A.Eq(2, asked.Count, "다시 누르면 다시 물음");
                A.Eq(1, r.F.CommandLines.Count, "'예' — 연결");
                r.F.Last.SimulateExit(1);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "자식이 끝남 → 대기");
                Thread.Sleep(3100);
                Tick(r);
                A.WaitUntil(() => r.F.CommandLines.Count == 2, 5000, "허락한 이름이면 다시 연결도 저절로");
                A.Eq(2, asked.Count, "다시 연결은 묻지 않음");
                Run(r.C.DisconnectAsync());

                var r2 = Again(r, new Settings { Reconnect = false });
                r2.Hotspot = Hs(Foreign);
                r2.C.StartupWaitSeconds = 0;
                Run(r2.C.AutoConnectAsync());
                A.Eq(0, r2.F.CommandLines.Count, "다음 실행은 허락을 잊는다(메모리에만)");
            });

            T("SSID: 이름을 모르는 Wi-Fi 도 손으로 누르면 묻는다 — 묻는 창이 없으면(null) 연결하지 않는다", () =>
            {
                var r = Make("ssid-ask-unknown", null);
                r.Hotspot = Hs(null);
                Run(r.C.ConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "묻는 창이 없으면 연결하지 않음");
                A.Eq(TetherState.Off, r.C.State, "꺼짐");
                HotspotInfo seen = null;
                r.C.AskConnect = (h, p) => { seen = h; return true; };
                Run(r.C.ConnectAsync());
                A.True(seen != null && seen.Ssid == null && seen.SsidError == "Wi-Fi 어댑터가 아님", "까닭을 넘김");
                A.Eq(1, r.F.CommandLines.Count, "'예' — 연결");
                Run(r.C.DisconnectAsync());
            });

            T("SSID: 묻는 사이 사용자가 '끊기' 를 누르면 '예' 라고 해도 띄우지 않는다", () =>
            {
                var r = Make("ssid-ask-cancel", null);
                r.Hotspot = Hs(Foreign);
                r.C.AskConnect = (h, p) => { var d = r.C.DisconnectAsync(); return true; }; // 묻는 창이 떠 있는 동안 트레이의 '끊기'
                Run(r.C.ConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "띄우지 않음");
                A.Eq(TetherState.Off, r.C.State, "꺼짐");
            });

            T("SSID(검토): 묻는 창이 떠 있는 사이 Wi-Fi 가 다른 망으로 옮겨 가면 '예' 여도 띄우지 않는다 — 접두어에 맞는 망으로 옮겨 갔으면 잇는다", () =>
            {
                var r = Make("ssid-ask-moved", null);
                r.Hotspot = Hs(Foreign);
                r.C.AskConnect = (h, p) => { r.Hotspot = Hs("DIRECT-zz-Printer"); return true; };
                Run(r.C.ConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "다른 남의 망 — 띄우지 않음");
                A.Eq(TetherState.Off, r.C.State, "꺼짐 — " + r.C.Detail);
                A.True(r.C.Detail.Contains("바뀜"), r.C.Detail);

                var r2 = Make("ssid-ask-moved-unknown", null);
                r2.Hotspot = Hs(null);
                r2.C.AskConnect = (h, p) => { r2.Hotspot = new HotspotInfo { AdapterName = "Wi-Fi 2", Address = "192.168.49.61", IfIndex = 13, SsidError = "까닭" }; return true; };
                Run(r2.C.ConnectAsync());
                A.Eq(0, r2.F.CommandLines.Count, "이름 모름 — 다른 어댑터면 띄우지 않음");

                var r3 = Make("ssid-ask-moved-mine", null);
                r3.Hotspot = Hs(Foreign);
                r3.C.AskConnect = (h, p) => { r3.Hotspot = Hs(Mine); return true; };
                Run(r3.C.ConnectAsync());
                A.Eq(1, r3.F.CommandLines.Count, "우리 망으로 옮겨 갔으면 연결");
                Run(r3.C.DisconnectAsync());
            });

            T("SSID(검토): 연결을 정하는 자리는 Wi-Fi 이름의 기억을 버리고 새로 본다 — 기억한 옛 이름(DIRECT-UT-)으로 남의 망에 연결하지 않는다", () =>
            {
                // 기억을 흉내 낸다: 버리기 전에는 옛 이름을, 버린 뒤에는 지금 이름을 준다(WifiName 의 10초 기억과 같은 꼴).
                var r = Make("ssid-stale-cache", new Settings { Reconnect = false });
                string remembered = Mine;
                string actual = Foreign;
                int forgets = 0;
                r.C.HotspotFinder = p => Hs(remembered ?? actual);
                r.C.ForgetWifiNames = () => { forgets++; remembered = null; };
                r.C.StartupWaitSeconds = 0;
                Run(r.C.AutoConnectAsync());
                A.True(forgets >= 1, "버림");
                A.Eq(0, r.F.CommandLines.Count, "남의 망 — 띄우지 않음");
                A.True(r.C.Detail.Contains(Foreign), r.C.Detail);

                var r2 = Make("ssid-stale-cache-manual", null);
                remembered = Mine;
                int asked = 0;
                r2.C.HotspotFinder = p => Hs(remembered ?? actual);
                r2.C.ForgetWifiNames = () => { remembered = null; };
                r2.C.AskConnect = (h, p) => { asked++; return false; };
                Run(r2.C.ConnectAsync());
                A.Eq(1, asked, "손으로 누른 것도 지금 이름으로 묻는다");
                A.Eq(0, r2.F.CommandLines.Count, "띄우지 않음");
            });

            T("SSID: 연결된 동안 Wi-Fi 이름이 바뀌면(남의 망으로 옮겨 감) 끊는다 — 이름을 잠깐 모르게 된 것은 그대로 둔다", () =>
            {
                var r = Make("ssid-change", new Settings { ReconnectInterval = 3 });
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                var first = r.F.Last;
                r.Hotspot = Hs(null);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "이름을 모르게 된 것은 그대로");
                A.True(!first.HasExited, "끊지 않음");
                r.Hotspot = Hs(Foreign);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "다른 망 — 끊고 대기");
                A.True(first.HasExited, "앞 자식을 끝냄");
                lock (r.Notices) A.True(r.Notices.Any(n => n.Contains("다른 망") && n.Contains(Foreign)), "알림: " + string.Join("|", r.Notices.ToArray()));
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "남의 망으로는 다시 연결하지 않음");
                Run(r.C.DisconnectAsync());
            });

            T("SSID(11-21 고침 2): 저절로 하는 시도가 도는 중에 누른 '연결'(끊기 → 연결)은 묻히지 않는다 — 그 시도가 남의 망을 만나면 묻고, tun2proxy 는 하나", () =>
            {
                foreach (var answer in new[] { true, false })
                {
                    var r = Make("ssid-upgrade-" + answer, new Settings { Reconnect = false });
                    r.Hotspot = Hs(Foreign);
                    var started = new ManualResetEventSlim(false);
                    var gate = new ManualResetEventSlim(false);
                    r.C.HotspotFinder = p => { started.Set(); gate.Wait(10000); return r.Hotspot; };
                    int asked = 0;
                    r.C.AskConnect = (h, p) => { Interlocked.Increment(ref asked); return answer; };
                    var auto = r.C.AutoConnectAsync();
                    A.True(started.Wait(3000), "자동 시도가 핫스팟을 살피는 중");
                    StatusForm.DoToggle(r.C);          // '끊기' — 시도는 아직 돈다
                    A.Eq(TetherState.Off, r.C.State, "꺼짐");
                    A.Eq(ToggleAction.Connect, r.C.Toggle, "단추는 '연결'");
                    StatusForm.DoToggle(r.C);          // '연결' — 예전에는 도는 시도에 묻혔다
                    Run(r.C.ConnectAsync());           // 한 번 더 눌러도 새 시도를 띄우지 않는다
                    gate.Set();
                    Run(auto);
                    A.Eq(1, asked, "한 번 물음(" + answer + ")");
                    A.Eq(answer ? 1 : 0, r.F.CommandLines.Count, "띄운 수(" + answer + ")");
                    if (answer)
                    {
                        A.Eq(TetherState.Connecting, r.C.State, "연결 중");
                        Run(r.C.DisconnectAsync());
                    }
                    else A.Eq(TetherState.Off, r.C.State, "'아니요' — 꺼짐: " + r.C.Detail);
                }

                // 누른 뒤 다시 '끊기' 를 누르면 그 뜻은 사라진다 — 묻지도 띄우지도 않는다.
                var r2 = Make("ssid-upgrade-cancel", new Settings { Reconnect = false });
                r2.Hotspot = Hs(Foreign);
                var s2 = new ManualResetEventSlim(false);
                var g2 = new ManualResetEventSlim(false);
                r2.C.HotspotFinder = p => { s2.Set(); g2.Wait(10000); return r2.Hotspot; };
                int asked2 = 0;
                r2.C.AskConnect = (h, p) => { asked2++; return true; };
                var auto2 = r2.C.AutoConnectAsync();
                A.True(s2.Wait(3000), "살피는 중");
                Run(r2.C.DisconnectAsync());
                Run(r2.C.ConnectAsync());
                Run(r2.C.DisconnectAsync());
                g2.Set();
                Run(auto2);
                A.Eq(0, asked2, "묻지 않음");
                A.Eq(0, r2.F.CommandLines.Count, "띄우지 않음");
                A.Eq(TetherState.Off, r2.C.State, "꺼짐");

                // 그 뜻은 그 시도까지만 — 다음 저절로 하는 시도는 다시 묻지 않는다.
                var r3 = Make("ssid-upgrade-once", new Settings { ReconnectInterval = 3 });
                r3.Hotspot = null;
                var s3 = new ManualResetEventSlim(false);
                var g3 = new ManualResetEventSlim(false);
                r3.C.HotspotFinder = p => { s3.Set(); g3.Wait(10000); return r3.Hotspot; };
                int asked3 = 0;
                r3.C.AskConnect = (h, p) => { asked3++; return true; };
                r3.C.StartupWaitSeconds = 0;
                var auto3 = r3.C.AutoConnectAsync();
                A.True(s3.Wait(3000), "살피는 중");
                Run(r3.C.DisconnectAsync());
                Run(r3.C.ConnectAsync());
                g3.Set();
                Run(auto3);
                A.Eq(TetherState.Waiting, r3.C.State, "핫스팟 없음 — 대기");
                r3.Hotspot = Hs(Foreign);
                Thread.Sleep(3100);
                Tick(r3);
                A.Eq(0, asked3, "다음 시도(저절로)는 묻지 않음");
                A.Eq(0, r3.F.CommandLines.Count, "띄우지 않음");
                Run(r3.C.DisconnectAsync());

                // 위는 1초 점검이 남의 망을 보고 시도를 띄우지 않아 새는지를 보지 못한다(검토 r5) — 1초 점검은 우리 망으로 보고 시도를
                // 띄우되 그 시도(Attempt 의 새로 보기)가 남의 망을 만나게 한다. 뜻이 새면 여기서 묻는다.
                var r4 = Make("ssid-upgrade-noleak", new Settings { ReconnectInterval = 3 });
                var s4 = new ManualResetEventSlim(false);
                var g4 = new ManualResetEventSlim(false);
                int fromAttempt = 0, gated = 1;
                r4.C.ForgetWifiNames = () => Interlocked.Exchange(ref fromAttempt, 1); // Attempt 만 새로 보기 전에 부른다
                r4.C.HotspotFinder = p =>
                {
                    bool attempt = Interlocked.Exchange(ref fromAttempt, 0) == 1;
                    if (Volatile.Read(ref gated) == 1) { s4.Set(); g4.Wait(10000); return null; }
                    return attempt ? Hs(Foreign) : Hs(Mine);
                };
                int asked4 = 0;
                r4.C.AskConnect = (h, p) => { Interlocked.Increment(ref asked4); return true; };
                r4.C.StartupWaitSeconds = 0;
                var auto4 = r4.C.AutoConnectAsync();
                A.True(s4.Wait(3000), "살피는 중");
                Run(r4.C.DisconnectAsync());
                Run(r4.C.ConnectAsync());          // 손 시도로 올림 — 이 시도는 핫스팟 없음으로 끝난다(묻는 자리에 닿지 않는다)
                Volatile.Write(ref gated, 0);
                g4.Set();
                Run(auto4);
                A.Eq(TetherState.Waiting, r4.C.State, "핫스팟 없음 — 대기: " + r4.C.Detail);
                Thread.Sleep(3100);
                Tick(r4);                          // 1초 점검은 우리 망 → 저절로 하는 시도 → 그 안에서 남의 망
                A.Eq(0, asked4, "다음 저절로 하는 시도는 묻지 않음(뜻이 새지 않음)");
                A.Eq(0, r4.F.CommandLines.Count, "띄우지 않음");
                A.True(r4.C.Detail.Contains("DIRECT-UT-") && r4.C.Detail.Contains(Foreign), "거절의 까닭: " + r4.C.Detail);
                Run(r4.C.DisconnectAsync());
            });

            T("SSID(11-21 고침 1): 묻는 창이 떠 있는 동안 누른 '연결' 은 그 창을 다시 앞으로 부른다(새 시도·새 창 없음)", () =>
            {
                var r = Make("ssid-reshow", null);
                r.Hotspot = Hs(Foreign);
                int reshow = 0, asked = 0;
                r.C.ReshowAsk = () => reshow++;
                string during = null;
                r.C.AskConnect = (h, p) =>
                {
                    asked++;
                    during = r.C.Detail;
                    var d = r.C.DisconnectAsync();  // 묻는 사이 '끊기' …
                    StatusForm.DoToggle(r.C);       // … 그리고 '연결'
                    StatusForm.DoToggle(r.C);       // 거듭 눌러도 새 시도·새 창은 없다
                    var c = r.C.ConnectAsync();
                    return true;
                };
                Run(r.C.ConnectAsync());
                A.Eq(1, asked, "한 번만 물음");
                A.True(during != null && during.Contains("묻는 중"), "묻는 동안의 상태: " + during);
                A.True(reshow >= 1, "다시 앞으로(" + reshow + ")");
                A.Eq(1, r.F.CommandLines.Count, "'예' — 하나만 띄움");
                Run(r.C.DisconnectAsync());

                var r2 = Make("ssid-reshow-none", null);
                int reshow2 = 0;
                r2.C.ReshowAsk = () => reshow2++;
                Run(r2.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r2);
                A.Eq(TetherState.Connected, r2.C.State, "연결됨");
                Run(r2.C.ConnectAsync());
                A.Eq(0, reshow2, "묻는 창이 없으면 부르지 않음");
                Run(r2.C.DisconnectAsync());
            });

            T("SSID(11-21 고침 3): 이름을 모른 채 허락해 연결한 망이 남의 이름으로 확인되면 끊고 까닭을 남긴다 — 모르는 동안은 그대로, 우리 이름이면 기억한다", () =>
            {
                var r = Make("ssid-unknown-later", new Settings { ReconnectInterval = 3 });
                r.Hotspot = Hs(null);
                r.C.AskConnect = (h, p) => true;
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨(이름 모름)");
                var first = r.F.Last;
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "여전히 모르면 그대로");
                A.True(!first.HasExited, "끊지 않음");
                r.Hotspot = Hs(Foreign);
                Tick(r);
                A.WaitUntil(() => r.C.State == TetherState.Waiting, 5000, "남의 이름 — 끊고 대기");
                A.True(first.HasExited, "자식을 끝냄");
                A.True(r.C.Detail.Contains("이름을 모른 채") && r.C.Detail.Contains(Foreign), "까닭: " + r.C.Detail);
                lock (r.Notices) A.True(r.Notices.Any(n => n.Contains("이름을 모른 채") && n.Contains(Foreign) && n.Contains("DIRECT-UT-")), "풍선: " + string.Join("|", r.Notices.ToArray()));
                Thread.Sleep(3100);
                Tick(r);
                A.Eq(1, r.F.CommandLines.Count, "남의 망으로는 다시 연결하지 않음");
                Run(r.C.DisconnectAsync());

                var r2 = Make("ssid-unknown-later-mine", new Settings { ReconnectInterval = 3 });
                r2.Hotspot = Hs(null);
                r2.C.AskConnect = (h, p) => true;
                Run(r2.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r2);
                A.Eq(TetherState.Connected, r2.C.State, "연결됨(이름 모름)");
                r2.Hotspot = Hs(Mine);
                Tick(r2);
                A.Eq(TetherState.Connected, r2.C.State, "우리 이름 — 그대로");
                lock (r2.Log) A.True(r2.Log.Any(l => l.Contains("이름을 확인함") && l.Contains(Mine)), "기억함");
                r2.Hotspot = Hs(Foreign);
                Tick(r2);
                A.WaitUntil(() => r2.C.State == TetherState.Waiting, 5000, "그 뒤 바뀌면 끊음");
                A.True(r2.C.Detail.Contains("다른 망"), r2.C.Detail);
                Run(r2.C.DisconnectAsync());

                // 이 실행에서 '예' 라고 한 남의 이름이면 끊지 않는다.
                var r3 = Make("ssid-unknown-later-approved", new Settings { ReconnectInterval = 3 });
                r3.Hotspot = Hs(Foreign);
                r3.C.AskConnect = (h, p) => true;
                Run(r3.C.ConnectAsync());
                A.Eq(1, r3.F.CommandLines.Count, "허락한 남의 이름으로 연결");
                Run(r3.C.DisconnectAsync());
                r3.Hotspot = Hs(null);
                Run(r3.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r3);
                A.Eq(TetherState.Connected, r3.C.State, "연결됨(이름 모름)");
                r3.Hotspot = Hs(Foreign);
                Tick(r3);
                A.Eq(TetherState.Connected, r3.C.State, "허락한 이름 — 그대로");
                Run(r3.C.DisconnectAsync());
            });

            T("SSID: 접두어는 설정으로 바꾼다(대소문자를 가린다)", () =>
            {
                var r = Make("ssid-prefix", new Settings { SsidPrefix = "MYPHONE-", Reconnect = false });
                r.C.StartupWaitSeconds = 0;
                r.Hotspot = Hs(Mine);
                Run(r.C.AutoConnectAsync());
                A.Eq(0, r.F.CommandLines.Count, "DIRECT-UT- 는 이제 접두어가 아님");
                A.True(r.C.Detail.Contains("MYPHONE-"), r.C.Detail);
                var r2 = Make("ssid-prefix2", new Settings { SsidPrefix = "MYPHONE-", Reconnect = false });
                r2.C.StartupWaitSeconds = 0;
                r2.Hotspot = Hs("myphone-1");
                Run(r2.C.AutoConnectAsync());
                A.Eq(0, r2.F.CommandLines.Count, "대소문자가 다르면 아님");
                r2.Hotspot = Hs("MYPHONE-1");
                Run(r2.C.ConnectAsync());
                A.Eq(1, r2.F.CommandLines.Count, "맞으면 연결");
                Run(r2.C.DisconnectAsync());
            });
        }

        static void AddWorkers(Action<string, Action> T)
        {
            T("점검: 1초 점검의 살피기(핫스팟·SSID)는 일꾼 스레드에서 — 부른 쪽이 막히지 않고, 도는 동안의 Tick 은 겹치지 않는다", () =>
            {
                var r = Make("tick-worker", null);
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                var gate = new ManualResetEventSlim(false);
                int calls = 0, tid = 0;
                r.C.HotspotFinder = p => { Interlocked.Increment(ref calls); tid = Thread.CurrentThread.ManagedThreadId; gate.Wait(10000); return r.Hotspot; };
                var sw = Stopwatch.StartNew();
                r.C.Tick();
                A.True(sw.ElapsedMilliseconds < 1000, "곧바로 돌아옴(" + sw.ElapsedMilliseconds + " ms)");
                A.WaitUntil(() => calls == 1, 3000, "살피기 시작");
                A.True(tid != Thread.CurrentThread.ManagedThreadId, "부른 쪽과 다른 스레드");
                for (int i = 0; i < 5; i++) r.C.Tick();
                Thread.Sleep(300);
                A.Eq(1, calls, "도는 동안의 Tick 은 건너뜀(겹치지 않음)");
                gate.Set();
                A.WaitUntil(() => { r.C.Tick(); return calls >= 2; }, 5000, "끝나면 다음 Tick 이 다시 살핀다");
                A.Eq(TetherState.Connected, r.C.State, "그대로 연결됨");
                Run(r.C.DisconnectAsync());
            });

            T("점검: TUN 어댑터 살피기도 일꾼 스레드에서", () =>
            {
                var r = Make("tick-worker-tun", null);
                int tid = 0;
                r.C.TunPresent = n => { tid = Thread.CurrentThread.ManagedThreadId; return true; };
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                A.True(tid != 0 && tid != Thread.CurrentThread.ManagedThreadId, "다른 스레드");
                Run(r.C.DisconnectAsync());
            });

            T("점검: 살피는 사이 사용자가 끊으면 늦게 온 결과('핫스팟 없음')를 버린다 — 알리지도 상태를 덮지도 않는다", () =>
            {
                var r = Make("tick-stale", new Settings { ReconnectInterval = 3 });
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                var started = new ManualResetEventSlim(false);
                var gate = new ManualResetEventSlim(false);
                r.C.HotspotFinder = p => { started.Set(); gate.Wait(10000); return null; };
                var t = r.C.TickAsync();
                A.True(started.Wait(3000), "살피기 시작");
                Run(r.C.DisconnectAsync());
                A.Eq(TetherState.Off, r.C.State, "꺼짐");
                gate.Set();
                Run(t);
                Thread.Sleep(300);
                A.Eq(TetherState.Off, r.C.State, "꺼진 채");
                A.Eq("", r.C.Detail, "까닭을 덮지 않음");
                lock (r.Notices) A.True(!r.Notices.Any(n => n.Contains("핫스팟")), "알리지 않음: " + string.Join("|", r.Notices.ToArray()));
            });

            T("점검: 살피기가 던지면 그 Tick 은 아무것도 하지 않는다(끊지 않음)", () =>
            {
                var r = Make("tick-throw", null);
                Run(r.C.ConnectAsync());
                Thread.Sleep(2100);
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "연결됨");
                r.C.HotspotFinder = p => { throw new InvalidOperationException("시험의 실패"); };
                Tick(r);
                A.Eq(TetherState.Connected, r.C.State, "그대로");
                A.True(!r.F.Last.HasExited, "끊지 않음");
                lock (r.Log) A.True(r.Log.Any(l => l.Contains("살피지 못함") && l.Contains("시험의 실패")), "기록");
                Run(r.C.DisconnectAsync());
            });

            T("점검: 연결 시도의 다른 tun2proxy·핫스팟 살피기도 일꾼 스레드에서", () =>
            {
                var r = Make("attempt-worker", null);
                int me = Thread.CurrentThread.ManagedThreadId, lt = 0, ht = 0;
                r.C.Tun2proxyLister = () => { lt = Thread.CurrentThread.ManagedThreadId; return new List<int>(); };
                r.C.HotspotFinder = p => { ht = Thread.CurrentThread.ManagedThreadId; return r.Hotspot; };
                Run(r.C.ConnectAsync());
                A.True(lt != 0 && lt != me, "tun2proxy 목록 — 다른 스레드");
                A.True(ht != 0 && ht != me, "핫스팟 — 다른 스레드");
                A.Eq(1, r.F.CommandLines.Count, "연결");
                Run(r.C.DisconnectAsync());
            });

            T("상태 창: 핫스팟·SSID 살피기는 일꾼 스레드에서 — 이름을 보이고, 창이 닫힌 뒤 돌아온 결과는 버린다", () =>
            {
                var r = Make("status-hotspot", null);
                int me = Thread.CurrentThread.ManagedThreadId, tid = 0;
                var gate = new ManualResetEventSlim(true);
                r.C.HotspotFinder = p => { tid = Thread.CurrentThread.ManagedThreadId; gate.Wait(10000); return r.Hotspot; };
                r.C.Prober = (h, p, k, t) => new ProbeResult { Ok = true, Detail = "ok" };
                var before = SynchronizationContext.Current;
                try
                {
                    var f = new StatusForm(r.C);
                    f.StartPosition = FormStartPosition.Manual;
                    f.Location = new System.Drawing.Point(-20000, -20000);
                    f.ShowInTaskbar = false;
                    f.Opacity = 0;
                    f.Show();
                    var line = AllControls(f).OfType<Label>().First(x => x.Text == "확인 중" || x.Text.StartsWith("연결됨"));
                    var sw = Stopwatch.StartNew();
                    while (!line.Text.Contains(Mine) && sw.ElapsedMilliseconds < 5000) { Application.DoEvents(); Thread.Sleep(20); }
                    A.True(line.Text.Contains(Mine), "SSID 를 보임: " + line.Text);
                    A.True(tid != 0 && tid != me, "다른 스레드");
                    A.Eq("연결됨 · Wi-Fi (192.168.49.60) · 'DIRECT-xy-Android_1234' — 저절로 연결하지 않음",
                         StatusText.HotspotLine(Hs(Foreign), "DIRECT-UT-"), "남의 망의 줄");
                    gate.Reset();
                    f.ShowHotspot(null);
                    tid = 0;
                    Thread.Sleep(2100);
                    f.RefreshView();           // 2초가 지나 다시 살핀다 — 문에서 막힌다
                    A.WaitUntil(() => tid != 0, 3000, "다시 살피기");
                    f.Hide();
                    f.Dispose();               // 그 사이 창이 없어진다
                    gate.Set();
                    Thread.Sleep(300);
                    Application.DoEvents();    // 예외 없이 버린다
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("설정 창: 저장은 일꾼 스레드에서 — 그동안 '저장 중' 이고 단추·칸이 눌리지 않으며 닫히지 않는다, 끝나면 설정을 건다", () =>
            {
                var r = Make("save-worker", null);
                var gate = new ManualResetEventSlim(false);
                var ops = new SlowHost(r.C, gate);
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new SettingsForm(ops))
                    {
                        f.LoadFrom(new Settings { ReconnectMax = 7, SsidPrefix = "MYPHONE-" });
                        var sw = Stopwatch.StartNew();
                        var t = f.SaveAsync();
                        A.True(sw.ElapsedMilliseconds < 1000, "곧바로 돌아옴(" + sw.ElapsedMilliseconds + " ms)");
                        A.True(!t.IsCompleted && f.Saving, "저장 중");
                        var save = AllControls(f).OfType<Button>().First(b => b.Text == "저장");
                        A.True(!save.Enabled, "저장 단추를 막음");
                        A.True(!AllControls(f).OfType<TextBox>().First().Enabled, "칸을 막음");
                        A.True(AllControls(f).OfType<Label>().Any(l => l.Text == "저장 중…"), "'저장 중…'");
                        A.True(!f.SaveAsync().Result, "저장 중에 또 누르면 아무것도 하지 않음");
                        A.WaitUntil(() => ops.Thread != 0, 3000, "작업 시작");
                        A.True(ops.Thread != Thread.CurrentThread.ManagedThreadId, "일꾼 스레드에서");
                        A.Eq(Settings.DefaultReconnectMax, r.C.Settings.ReconnectMax, "끝나기 전에는 걸지 않음");
                        gate.Set();
                        A.True(Pump(t), "저장됨");
                        A.Eq(7, r.C.Settings.ReconnectMax, "설정을 걸었다");
                        A.Eq("MYPHONE-", r.C.Settings.SsidPrefix, "접두어");
                        A.True(save.Enabled, "다시 눌림");
                        A.True(File.ReadAllText(r.Paths.SettingsFile).Contains("ssid_prefix=MYPHONE-\n"), "파일에 씀");
                        A.True(AllControls(f).OfType<Label>().Any(l => l.Text == "저장했습니다."), "결과를 보임");
                    }
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });

            T("설정 창: 저장하는 사이 창이 없어져도 저장한 설정은 건다(파일과 어긋나지 않게)", () =>
            {
                var r = Make("save-disposed", null);
                var gate = new ManualResetEventSlim(false);
                var ops = new SlowHost(r.C, gate);
                var before = SynchronizationContext.Current;
                try
                {
                    var f = new SettingsForm(ops);
                    f.LoadFrom(new Settings { ReconnectMax = 9 });
                    var t = f.SaveAsync();
                    A.WaitUntil(() => ops.Thread != 0, 3000, "작업 시작");
                    f.Dispose();
                    gate.Set();
                    A.True(Pump(t), "저장됨");
                    A.Eq(9, r.C.Settings.ReconnectMax, "걸었다");
                }
                finally { SynchronizationContext.SetSynchronizationContext(before); }
            });
        }
    }

    /// <summary>초점을 빼앗지 않고 화면 밖에 투명하게 뜨는 보통 창(묻는 창의 z 차례 시험).</summary>
    sealed class QuietForm : Form
    {
        public QuietForm()
        {
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.Manual;
            Location = new System.Drawing.Point(-20000, -20000);
            Size = new System.Drawing.Size(120, 80);
            Opacity = 0;
        }
        protected override bool ShowWithoutActivation { get { return true; } }
    }

    /// <summary>시험만 쓰는 창 다루기 — 활성화하지 않는다.</summary>
    static class Win
    {
        delegate bool EnumProc(IntPtr hwnd, IntPtr lParam);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern IntPtr GetWindow(IntPtr hWnd, uint cmd);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern bool EnumThreadWindows(uint threadId, EnumProc fn, IntPtr lParam);
        [System.Runtime.InteropServices.DllImport("kernel32.dll")]
        static extern uint GetCurrentThreadId();
        [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
        static extern int GetClassNameW(IntPtr hWnd, StringBuilder name, int max);
        [System.Runtime.InteropServices.DllImport("user32.dll")]
        static extern bool PostMessageW(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
        const uint SWP_NOSIZE = 0x1, SWP_NOMOVE = 0x2, SWP_NOZORDER = 0x4, SWP_NOACTIVATE = 0x10;
        const uint GW_HWNDNEXT = 2;

        public static void MoveAway(IntPtr h) { SetWindowPos(h, IntPtr.Zero, -20000, -20000, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE); }

        /// <summary>보통 창을 (보통 창들 가운데) 맨 위로 — 활성화하지 않는다.</summary>
        public static void RaiseNoActivate(IntPtr h) { SetWindowPos(h, IntPtr.Zero, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE); }

        /// <summary>z 차례에서 a 가 b 보다 위인가(a 에서 아래로 내려가며 b 를 만나는가).</summary>
        public static bool IsAbove(IntPtr a, IntPtr b)
        {
            for (var h = GetWindow(a, GW_HWNDNEXT); h != IntPtr.Zero; h = GetWindow(h, GW_HWNDNEXT))
                if (h == b) return true;
            return false;
        }

        /// <summary>이 스레드의 대화 상자 모두에 '아니요'(시험이 멈추지 않게 하는 마지막 길).</summary>
        public static void AnswerAllDialogs()
        {
            EnumThreadWindows(GetCurrentThreadId(), (h, l) =>
            {
                var cls = new StringBuilder(64);
                GetClassNameW(h, cls, cls.Capacity);
                if (cls.ToString() == "#32770")
                {
                    PostMessageW(h, 0x111, new IntPtr(7), IntPtr.Zero); // 아니요
                    PostMessageW(h, 0x111, new IntPtr(2), IntPtr.Zero); // 취소(확인 하나뿐인 창)
                }
                return true;
            }, IntPtr.Zero);
        }
    }

    // ------------------------------------------------------------------ SSID 읽기·설정
    static class SsidTests
    {
        static byte[] Attrs(int state, int len, byte[] ssid)
        {
            var b = new byte[604];
            BitConverter.GetBytes(state).CopyTo(b, 0);
            BitConverter.GetBytes((uint)len).CopyTo(b, WifiSsid.SsidLengthOffset);
            if (ssid != null) Array.Copy(ssid, 0, b, WifiSsid.SsidBytesOffset, Math.Min(32, ssid.Length));
            // 프로필 이름 칸(UTF-16)에 다른 이름 — SSID 와 섞어 읽지 않는가
            Encoding.Unicode.GetBytes("profile-name").CopyTo(b, 8);
            return b;
        }

        public static void Add(Action<string, Action> T)
        {
            T("SSID: WLAN_CONNECTION_ATTRIBUTES 에서 이름 읽기(합성 바이트)", () =>
            {
                string err;
                var name = Encoding.UTF8.GetBytes("DIRECT-UT-폰1");
                A.Eq("DIRECT-UT-폰1", WifiSsid.Parse(Attrs(1, name.Length, name), 604, out err), "연결됨");
                A.Eq((string)null, err, "까닭 없음");
                A.Eq((string)null, WifiSsid.Parse(Attrs(0, name.Length, name), 604, out err), "연결 안 됨");
                A.True(err.Contains("연결되어 있지 않음"), err);
                A.Eq((string)null, WifiSsid.Parse(Attrs(1, 33, name), 604, out err), "길이 33");
                A.Eq((string)null, WifiSsid.Parse(Attrs(1, 0, name), 604, out err), "빈 이름");
                A.Eq((string)null, WifiSsid.Parse(Attrs(1, name.Length, name), 100, out err), "짧은 답");
                A.Eq((string)null, WifiSsid.Parse(null, 0, out err), "없음");
                var bad = Encoding.ASCII.GetBytes("DIRECT-UT-x").Concat(new byte[] { 0xFF, 0x41 }).ToArray();
                var got = WifiSsid.Parse(Attrs(1, bad.Length, bad), 604, out err);
                A.True(got.StartsWith("DIRECT-UT-x") && got.IndexOf((char)0xFFFD) > 0, "틀린 UTF-8 도 접두어는 견줄 수 있다");
                var full = Encoding.ASCII.GetBytes(new string('a', 32));
                A.Eq(new string('a', 32), WifiSsid.Parse(Attrs(1, 32, full), 604, out err), "32바이트");
            });

            T("SSID: 접두어 견주기와 화면 글 — 대소문자를 가리고, 남이 정한 이름의 제어·방향 제어 문자는 뺀다", () =>
            {
                var h = new HotspotInfo { Ssid = "DIRECT-UT-abc" };
                A.True(h.SsidStartsWith("DIRECT-UT-"), "맞음");
                A.True(!h.SsidStartsWith("direct-ut-"), "대소문자");
                A.True(!h.SsidStartsWith(""), "빈 접두어는 아무것에도 맞지 않음");
                A.True(!new HotspotInfo().SsidStartsWith("DIRECT-UT-"), "이름 모름");
                var evil = "DIRECT-xx" + (char)0x202E + "lmth" + (char)7;
                var shown = StatusText.Ssid(evil);
                A.True(shown.IndexOf((char)0x202E) < 0 && shown.IndexOf((char)7) < 0, "뺀다: " + shown);
                var why = StatusText.Refusal(new HotspotInfo { Ssid = evil }, "DIRECT-UT-", null);
                A.True(why.IndexOf((char)0x202E) < 0, "까닭에도");
                A.Eq((string)null, StatusText.Refusal(new HotspotInfo { Ssid = "DIRECT-xx" }, "DIRECT-UT-", "DIRECT-xx"), "허락한 이름");
                A.True(StatusText.Refusal(new HotspotInfo { Ssid = "DIRECT-xx" }, "DIRECT-UT-", "DIRECT-yy") != null, "다른 허락");
                A.Eq("연결 안 됨 — 폰의 DIRECT-UT-… 에 붙으십시오", StatusText.HotspotLine(null, "DIRECT-UT-"), "없음의 줄");
                A.True(StatusText.HotspotLine(new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.2", SsidError = "WLAN 서비스가 꺼져 있음" }, "DIRECT-UT-")
                    .EndsWith("이름 모름(WLAN 서비스가 꺼져 있음) — 저절로 연결하지 않음"), "모름의 줄");
            });

            T("SSID: 이름을 얻는 차례 — Wi-Fi API, 위치 권한으로 거절됐을 때만 네트워크 목록 이름, 그 밖의 실패는 이름 모름", () =>
            {
                string err, src;
                int nlmCalls = 0;
                Func<string> nlm = () => { nlmCalls++; return "DIRECT-UT-폰"; };
                A.Eq("DIRECT-UT-a", WifiName.Resolve("DIRECT-UT-a", null, false, nlm, out err, out src), "Wi-Fi API");
                A.Eq(WifiName.FromWlan, src, "어디서");
                A.Eq(0, nlmCalls, "NLM 을 묻지 않음");
                A.Eq("DIRECT-UT-폰", WifiName.Resolve(null, "Wi-Fi 이름을 읽을 권한이 없음(위치 꺼짐)", true, nlm, out err, out src), "거절 → NLM");
                A.Eq(WifiName.FromNlm, src, "어디서 — NLM");
                A.Eq((string)null, WifiName.Resolve(null, "Wi-Fi 어댑터가 아님", false, nlm, out err, out src), "유선 — 물러서지 않음");
                A.Eq("Wi-Fi 어댑터가 아님", err, "까닭");
                A.Eq(1, nlmCalls, "거절일 때만 NLM");
                A.Eq((string)null, WifiName.Resolve(null, "거절", true, () => null, out err, out src), "NLM 에도 없음");
                A.True(err.Contains("네트워크 목록에 이름 없음"), err);
                A.Eq((string)null, WifiName.Resolve(null, "거절", true, () => { throw new InvalidOperationException("WMI 꺼짐"); }, out err, out src), "NLM 이 던짐");
                A.True(err.Contains("WMI 꺼짐"), err);
                A.True(StatusText.HotspotLine(new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.2", Ssid = "DIRECT-UT-a", SsidSource = WifiName.FromNlm }, "DIRECT-UT-")
                    .EndsWith("'DIRECT-UT-a'(네트워크 목록 이름)"), "상태 창에 어디서 얻었는지");
            });

            T("SSID(검토): 이름 읽기가 거절(5)되면 위치와 상관없는 interface_state 로 연결된 Wi-Fi 어댑터일 때만 '거절' — 유선·없는 어댑터는 네트워크 목록으로 물러서지 않는다", () =>
            {
                string err;
                A.True(WifiSsid.DeniedOnConnectedWlan(0, 1, out err), "연결된 Wi-Fi — 거절(물러선다)");
                A.True(err.Contains("위치"), err);
                A.True(!WifiSsid.DeniedOnConnectedWlan(1168, -1, out err), "Wi-Fi 어댑터 아님(1168)");
                A.Eq("Wi-Fi 어댑터가 아님", err, "까닭");
                A.True(!WifiSsid.DeniedOnConnectedWlan(87, -1, out err), "87");
                A.True(!WifiSsid.DeniedOnConnectedWlan(0, 4, out err), "끊긴 Wi-Fi");
                A.True(err.Contains("연결되어 있지 않음"), err);
                A.True(!WifiSsid.DeniedOnConnectedWlan(0, -1, out err), "상태 못 읽음");
                A.True(!WifiSsid.DeniedOnConnectedWlan(1062, -1, out err), "서비스 꺼짐");
                // 이 PC(24H2·위치 꺼짐)에서는 current_connection 이 없는 GUID·유선에도 5 를 준다 — 읽기만.
                bool denied;
                A.Eq((string)null, WifiSsid.Query("{00000000-0000-0000-0000-000000000000}", out err, out denied), "없는 어댑터");
                A.True(!denied, "없는 어댑터는 '거절' 이 아니다(네트워크 목록으로 물러서지 않음): " + err);
                foreach (var ni in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.NetworkInterfaceType == System.Net.NetworkInformation.NetworkInterfaceType.Wireless80211) continue;
                    Guid g;
                    if (!Guid.TryParse(ni.Id, out g)) continue;
                    WifiSsid.Query(ni.Id, out err, out denied);
                    A.True(!denied, "Wi-Fi 가 아닌 어댑터(" + ni.NetworkInterfaceType + ")는 '거절' 이 아니다: " + err);
                }
            });

            T("SSID(검토): 네트워크 목록 질의에 시한이 있다 — WMI 가 멈춰도 1초 점검·연결 시도가 영영 서지 않게", () =>
            {
                using (var s = WifiName.NlmSearcher(12))
                {
                    A.True(s.Options.Timeout <= TimeSpan.FromSeconds(10), "열거 시한 " + s.Options.Timeout);
                    A.True(s.Scope.Options.Timeout <= TimeSpan.FromSeconds(10), "연결 시한 " + s.Scope.Options.Timeout);
                }
                string err, src;
                WifiName.Get("{00000000-0000-0000-0000-000000000000}", -1, out err, out src);
                A.True(WifiName.CachedCount >= 1, "기억함");
                WifiName.Forget();
                A.Eq(0, WifiName.CachedCount, "Forget 은 모두 버린다");
            });

            T("묻는 창(11-21 고침 1): MB_TOPMOST 로 늘 위 무리에 뜬다 — 뒤에 맨 위로 올린 보통 창보다 위, 다시 앞으로 부르기(짧게 띄우고 닫는다)", () =>
            {
                A.True((TopAsk.Flags & 0x40000) != 0, "MB_TOPMOST");
                A.True((TopAsk.Flags & 0x10000) != 0, "MB_SETFOREGROUND");
                A.True((TopAsk.Flags & 0x100) != 0 && (TopAsk.Flags & 0x4) != 0, "예/아니요, 기본 '아니요'");
                A.True(!TopAsk.IsOpen && !TopAsk.BringToFront(false), "떠 있지 않으면 부를 것이 없다");
                bool found = false, topmost = false, above = false, aboveAgain = false, refront = false, open = false;
                int ticks = 0;
                var before = SynchronizationContext.Current;
                var other = new QuietForm();
                try
                {
                    other.Show(); // 보통 창 — 초점을 빼앗지 않고 화면 밖에 투명하게
                    using (var timer = new System.Windows.Forms.Timer { Interval = 20 })
                    {
                        timer.Tick += (s, e) =>
                        {
                            ticks++;
                            var h = TopAsk.Find();
                            if (h == IntPtr.Zero)
                            {
                                if (ticks > 250) { timer.Stop(); Win.AnswerAllDialogs(); } // 못 찾으면 그래도 닫는다(시험이 멈추지 않게)
                                return;
                            }
                            timer.Stop();
                            found = true;
                            Win.MoveAway(h);                 // 사용자 화면에서 곧바로 비킨다
                            open = TopAsk.IsOpen;
                            topmost = TopAsk.IsTopmost(h);
                            Win.RaiseNoActivate(other.Handle); // 다른 보통 창이 맨 위로 올라와도
                            above = Win.IsAbove(h, other.Handle);
                            refront = TopAsk.BringToFront(false);
                            Win.RaiseNoActivate(other.Handle);
                            aboveAgain = Win.IsAbove(h, other.Handle);
                            TopAsk.AnswerForTest(h, false);
                        };
                        timer.Start();
                        bool yes = TopAsk.Ask("시험 — 곧 저절로 닫힙니다", false);
                        A.True(!yes, "'아니요' 로 닫힘");
                    }
                }
                finally
                {
                    other.Dispose();
                    SynchronizationContext.SetSynchronizationContext(before);
                }
                A.True(found, "창을 찾음(" + ticks + " 번째)");
                A.True(open, "떠 있는 동안 IsOpen");
                A.True(topmost, "WS_EX_TOPMOST");
                A.True(above, "맨 위로 올린 보통 창보다 위");
                A.True(refront && aboveAgain, "다시 앞으로 부름");
                A.True(!TopAsk.IsOpen && TopAsk.Find() == IntPtr.Zero && !TopAsk.BringToFront(false), "닫힌 뒤에는 없음");
            });

            T("SSID: 설정의 접두어 — 기본 DIRECT-UT-, 쓰고 읽기, 비움·앞뒤 빈칸·제어 글자·32바이트 넘음은 거절", () =>
            {
                var s = new Settings();
                A.Eq("DIRECT-UT-", s.SsidPrefix, "기본값");
                A.True(s.Serialize().Contains("\nssid_prefix=DIRECT-UT-\n"), "씀");
                var probs = new List<string>();
                A.Eq("MYPHONE-", Settings.Parse("ssid_prefix=MYPHONE-\n", probs).SsidPrefix, "ASCII");
                A.Eq(0, probs.Count, "문제 없음");
                A.Eq("DIRECT-UT-", Settings.Parse("ssid_prefix=내폰-\n", probs).SsidPrefix, "ASCII 밖은 버림(검토 11-27)");
                A.True(probs.Any(p => p.Contains("ssid_prefix")), "버린 것을 적음");
                probs.Clear();
                A.Eq("DIRECT-UT-", Settings.Parse("proxy_port=1080\n", probs).SsidPrefix, "옛 설정 파일 — 기본값");
                foreach (var bad in new[] { "", " DIRECT", "DIRECT ", "a" + (char)9 + "b", new string('가', 11), "DIRECT" + (char)0xD800,
                                            "DIRECT-" + (char)0x0423, "DIRECT" + (char)0xFF0D, new string('a', 33), "가나" })
                {
                    A.True(!Settings.IsSsidPrefix(bad), "거절 [" + bad + "]");
                    A.True(new Settings { SsidPrefix = bad }.Validate().Any(e => e.Key == "ssid_prefix"), "검사 [" + bad + "]");
                }
                A.True(Settings.IsSsidPrefix(new string('a', 32)), "32바이트");
                A.True(Settings.IsSsidPrefix("DIRECT-ab ~!"), "기호·빈칸도 ASCII 면");
                probs.Clear();
                A.Eq("DIRECT-UT-", Settings.Parse("ssid_prefix=\n", probs).SsidPrefix, "빈 값은 버림");
                A.True(probs.Any(p => p.Contains("ssid_prefix")), "적음");
            });

            T("SSID: 이 PC 에서 읽기만 — FindHotspot 이 던지지 않고, 핫스팟이 있으면 이름이나 까닭이 있다(이름은 적지 않는다)", () =>
            {
                var hs = NetState.FindHotspot(Settings.DefaultSsidPrefix);
                if (hs == null) { TestMain.Say("      (이 PC 에 192.168.49.x 어댑터 없음)"); return; }
                A.True(hs.Ssid != null || !string.IsNullOrEmpty(hs.SsidError), "이름이나 까닭");
                TestMain.Say("      핫스팟 어댑터 있음 · 이름 얻음 " + (hs.Ssid != null) + " · 어디서 " + hs.SsidSource + " · 접두어 맞음 " + hs.SsidStartsWith(Settings.DefaultSsidPrefix) +
                             (hs.Ssid == null ? " · 까닭 " + hs.SsidError : ""));
                var sw = Stopwatch.StartNew();
                NetState.FindHotspot(Settings.DefaultSsidPrefix);
                A.True(sw.ElapsedMilliseconds < 50, "10초 기억 — 두 번째는 Wi-Fi API·WMI 를 다시 부르지 않음(" + sw.ElapsedMilliseconds + " ms)");
                string err;
                A.Eq((string)null, WifiSsid.Query("{00000000-0000-0000-0000-000000000000}", out err), "없는 어댑터");
                A.True(!string.IsNullOrEmpty(err), "까닭: " + err);
                A.Eq((string)null, WifiSsid.Query("not-a-guid", out err), "GUID 아님");
            });
        }
    }

}
