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

        /// <summary>'시작 시 실행' 을 바꾸면 문이 열릴 때까지 막히고, 부른 스레드를 적는다.</summary>
        sealed class SlowStartupOps : ISystemOps
        {
            readonly ManualResetEventSlim gate;
            public volatile int Thread;
            public bool State;
            public SlowStartupOps(ManualResetEventSlim gate) { this.gate = gate; }
            public bool WlanConnect(string profile, out string output) { output = ""; return true; }
            public bool Route(RouteAction action, out string output) { output = ""; return true; }
            public bool EnableStartup(string exePath, out string error)
            {
                Thread = System.Threading.Thread.CurrentThread.ManagedThreadId;
                gate.Wait(15000);
                error = null;
                State = true;
                return true;
            }
            public bool DisableStartup(out string error)
            {
                Thread = System.Threading.Thread.CurrentThread.ManagedThreadId;
                gate.Wait(15000);
                error = null;
                State = false;
                return true;
            }
            public bool StartupEnabled() { return State; }
        }

        static void AddSsid(Action<string, Action> T)
        {
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
                var ops = new SlowStartupOps(gate);
                var before = SynchronizationContext.Current;
                try
                {
                    using (var f = new SettingsForm(r.C, ops, "x.exe"))
                    {
                        f.LoadFrom(new Settings { AutoStart = true, ReconnectMax = 7, SsidPrefix = "MYPHONE-" });
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
                        A.True(r.C.Settings.AutoStart && ops.State, "시작 시 실행");
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
                var ops = new SlowStartupOps(gate);
                var before = SynchronizationContext.Current;
                try
                {
                    var f = new SettingsForm(r.C, ops, "x.exe");
                    f.LoadFrom(new Settings { AutoStart = true, ReconnectMax = 9 });
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

            T("SSID: 설정의 접두어 — 기본 DIRECT-UT-, 쓰고 읽기, 비움·앞뒤 빈칸·제어 글자·32바이트 넘음은 거절", () =>
            {
                var s = new Settings();
                A.Eq("DIRECT-UT-", s.SsidPrefix, "기본값");
                A.True(s.Serialize().Contains("\nssid_prefix=DIRECT-UT-\n"), "씀");
                var probs = new List<string>();
                A.Eq("내폰-", Settings.Parse("ssid_prefix=내폰-\n", probs).SsidPrefix, "한글");
                A.Eq(0, probs.Count, "문제 없음");
                A.Eq("DIRECT-UT-", Settings.Parse("proxy_port=1080\n", probs).SsidPrefix, "옛 설정 파일 — 기본값");
                foreach (var bad in new[] { "", " DIRECT", "DIRECT ", "a" + (char)9 + "b", new string('가', 11), "DIRECT" + (char)0xD800 })
                {
                    A.True(!Settings.IsSsidPrefix(bad), "거절 [" + bad + "]");
                    A.True(new Settings { SsidPrefix = bad }.Validate().Any(e => e.Key == "ssid_prefix"), "검사 [" + bad + "]");
                }
                A.True(Settings.IsSsidPrefix(new string('a', 32)), "32바이트");
                A.True(Settings.IsSsidPrefix("가나다라마바사아자차" + "ab"), "한글 10자 + 2 = 32바이트");
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

    // ------------------------------------------------------------------ 작업 스케줄러(가짜 schtasks)
    static class StartupTaskTests
    {
        sealed class FakeSchtasks
        {
            public readonly List<string> Calls = new List<string>();
            public int CreateCode, DeleteCode, QueryCode;
            public int Run(string exe, string args, int timeoutMs, out string output)
            {
                lock (Calls) Calls.Add(exe + " " + args);
                output = "";
                if (args.StartsWith("/Create")) return CreateCode;
                if (args.StartsWith("/Delete")) return DeleteCode;
                if (args.StartsWith("/Query")) return QueryCode;
                return 99;
            }
        }

        static SystemOps Ops(FakeSchtasks f, List<string> notes)
        {
            return new SystemOps(null, n => { lock (notes) notes.Add(n); }) { RunTool = f.Run, SettleDelayMs = 0 };
        }

        const int Late = SystemOps.TimedOut;

        public static void Add(Action<string, Action> T)
        {
            T("시작 시 실행: 등록이 시간 초과면 한 번 물어 — 있으면 켜짐, 없으면 꺼짐, 물음도 답이 없으면 실패와 안내", () =>
            {
                var notes = new List<string>();
                string err;
                var f = new FakeSchtasks { CreateCode = Late, QueryCode = 0 };
                A.True(Ops(f, notes).CreateTask(@"C:\nowhere\task.xml", out err), "있으면 켜짐");
                A.Eq("schtasks.exe /Create /TN WebTether /XML C:\\nowhere\\task.xml /F|schtasks.exe /Query /TN WebTether", string.Join("|", f.Calls.ToArray()), "물은 것");
                A.True(notes.Any(n => n.Contains("시간 안에 끝나지 않음") && n.Contains("켜짐")), "기록: " + string.Join("|", notes.ToArray()));

                f = new FakeSchtasks { CreateCode = Late, QueryCode = 1 };
                A.True(!Ops(f, notes).CreateTask("x.xml", out err), "없으면 꺼짐");
                A.True(err.Contains("작업이 없습니다"), err);

                f = new FakeSchtasks { CreateCode = Late, QueryCode = Late };
                A.True(!Ops(f, notes).CreateTask("x.xml", out err), "물음도 시간 초과");
                A.True(err.Contains("확인하지 못했습니다") && err.Contains("작업 스케줄러"), err);

                f = new FakeSchtasks { CreateCode = 1, QueryCode = 0 };
                A.True(!Ops(f, notes).CreateTask("x.xml", out err), "시간 초과가 아닌 실패는 실패");
                A.Eq(1, f.Calls.Count, "묻지 않음");
                A.True(err.Contains("코드 1"), err);

                f = new FakeSchtasks { CreateCode = 0 };
                A.True(Ops(f, notes).CreateTask("x.xml", out err), "성공");
                A.Eq(1, f.Calls.Count, "묻지 않음");
            });

            T("시작 시 실행: 지우기가 시간 초과·실패면 한 번 물어 — 없으면 꺼짐, 남았으면 켜짐(실패), 모르면 실패", () =>
            {
                var notes = new List<string>();
                string err;
                var f = new FakeSchtasks { DeleteCode = Late, QueryCode = 1 };
                A.True(Ops(f, notes).DeleteTask(out err), "없으면 꺼짐");
                A.Eq("schtasks.exe /Delete /TN WebTether /F|schtasks.exe /Query /TN WebTether", string.Join("|", f.Calls.ToArray()), "물은 것");

                f = new FakeSchtasks { DeleteCode = Late, QueryCode = 0 };
                A.True(!Ops(f, notes).DeleteTask(out err), "남았으면 실패(켜짐 그대로)");
                A.True(err.Contains("남아 있습니다"), err);

                f = new FakeSchtasks { DeleteCode = Late, QueryCode = Late };
                A.True(!Ops(f, notes).DeleteTask(out err), "모르면 실패 — 꺼진 것으로 치지 않는다");
                A.True(err.Contains("확인하지 못했습니다"), err);

                f = new FakeSchtasks { DeleteCode = 1, QueryCode = 1 };
                A.True(Ops(f, notes).DeleteTask(out err), "이미 없으면 꺼짐(예전과 같다)");

                var o = Ops(new FakeSchtasks { QueryCode = 0 }, notes);
                A.Eq(true, o.TaskExists(), "있음");
                A.Eq(false, Ops(new FakeSchtasks { QueryCode = 1 }, notes).TaskExists(), "없음");
                A.Eq((bool?)null, Ops(new FakeSchtasks { QueryCode = Late }, notes).TaskExists(), "모름");
                A.True(!Ops(new FakeSchtasks { QueryCode = Late }, notes).StartupEnabled(), "모르면 켜짐이 아님");
            });
        }
    }
}
