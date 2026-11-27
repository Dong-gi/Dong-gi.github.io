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
