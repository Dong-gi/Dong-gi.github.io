// 시험 — 서비스(LocalSystem)·파이프·설치기. 실제 서비스·Run 키·예약 작업·관리자 상승은 건드리지 않는다:
// 서비스의 몸통(ServiceCore)은 이 프로세스에서 보통 권한·dry-run 으로, 파이프는 시험마다 다른 이름으로, SCM·Run 키·schtasks 는 가짜로,
// 묻는 창(WTSSendMessage)도 가짜로(화면에 창을 띄우지 않는다). 실제 시스템에는 읽기만(서비스 상태·예약 작업 XML).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Linq;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using System.Threading;

namespace WebTether.Tests
{
    static class ServiceTests
    {
        static string MyImage()
        {
            using (var p = Process.GetCurrentProcess()) return p.MainModule.FileName;
        }

        static int MySession()
        {
            using (var p = Process.GetCurrentProcess()) return p.SessionId;
        }

        sealed class FakePrompt : IUserPrompt
        {
            public bool? Answer = true;
            public ManualResetEventSlim Gate;
            public readonly List<string> Asked = new List<string>();
            public bool? Ask(int session, string title, string text, int timeoutSeconds)
            {
                lock (Asked) Asked.Add(session + "|" + text);
                if (Gate != null) Gate.Wait(10000);
                return Answer;
            }
        }

        static Dictionary<string, string> ProposalFrom(Settings s)
        {
            var req = PipeCommands.Request(PipeCommands.Propose);
            foreach (var kv in s.Pairs()) req[PipeCommands.SettingPrefix + kv.Key] = kv.Value;
            return req;
        }

        sealed class FakeSessions : ISessionInfo
        {
            public int Session = -1;
            public int ConsoleUserSession() { return Session; }
        }

        sealed class Svc : IDisposable
        {
            public ServiceCore Core;
            public string Pipe;
            public FakePrompt Prompt = new FakePrompt();
            public FakeSessions Sessions = new FakeSessions();
            public HotspotInfo Hotspot = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.60", IfIndex = 12, Ssid = "DIRECT-UT-시험폰" };
            public int Console;
            public void Dispose() { Core.Stop(10000); }
        }

        /// <summary>이 프로세스에서 서비스의 몸통을 dry-run 으로 띄운다(파이프 이름은 시험마다 따로, 손님 확인의 콘솔 세션은 이 세션).</summary>
        static Svc Start(string name, string pipe = null, Action<ServiceCore> tweak = null)
        {
            var s = new Svc { Pipe = pipe ?? "WebTether-test-" + Guid.NewGuid().ToString("N"), Console = MySession() };
            var paths = AppPaths.Under(TestMain.Scratch("svc-" + name));
            s.Core = new ServiceCore(paths, DirPolicy.CurrentUser(), true, s.Pipe, s.Prompt, s.Sessions)
            {
                PipeForCurrentUser = true,
                ConsoleSession = () => s.Console,
                AskTimeoutSeconds = 5,
                ProposerImage = MyImage(),
                ProposalAfterMs = 0,
                Configure = c =>
                {
                    c.HotspotFinder = p => s.Hotspot;
                    c.Prober = (h, port, k, t) => new ProbeResult { Ok = true, Detail = "ok" };
                    c.Tun2proxyLister = () => new List<int>();
                    c.RouteReader = () => new List<RouteRow>();
                    c.ForgetWifiNames = () => { };
                },
            };
            if (tweak != null) tweak(s.Core);
            string err;
            A.True(s.Core.Start(out err), "서비스 몸통: " + err);
            return s;
        }

        static PipeClient Client(Svc s)
        {
            int me;
            using (var p = Process.GetCurrentProcess()) me = p.Id;
            return new PipeClient(s.Pipe) { TrustServer = (ses, pid) => pid == me ? null : "다른 서버(" + pid + ")" };
        }

        static Dictionary<string, string> Send(PipeClient c, Dictionary<string, string> req)
        {
            string err;
            var r = c.Send(req, out err);
            A.True(r != null, "답: " + err);
            return r;
        }

        static ServiceStatus Status(PipeClient c)
        {
            return ServiceStatus.FromMap(Send(c, PipeCommands.Request(PipeCommands.Status)));
        }

        static void WaitState(PipeClient c, TetherState want, int ms, string what)
        {
            var sw = Stopwatch.StartNew();
            ServiceStatus st = null;
            while (sw.ElapsedMilliseconds < ms)
            {
                st = Status(c);
                if (st.State == want) return;
                Thread.Sleep(100);
            }
            throw new Exception("기대와 다름: " + what + " — " + (st == null ? "?" : st.State + " " + st.Detail));
        }

        internal static void Add(Action<string, Action> T)
        {
            T("파이프 글: 짓고 읽으면 같다(줄바꿈·역슬래시·한글), 틀린 꼴은 읽지 않는다", () =>
            {
                var m = new Dictionary<string, string> { { "v", "1" }, { "cmd", "status" }, { "detail", "줄1\n줄2\\끝\r" } };
                var b = PipeMessage.Encode(m);
                var back = PipeMessage.Read(new MemoryStream(b));
                A.Eq("줄1\n줄2\\끝\r", back["detail"], "값");
                A.Eq(3, back.Count, "셋");
                Func<string, bool> bad = text =>
                {
                    try { PipeMessage.Decode(Encoding.UTF8.GetBytes(text)); return false; }
                    catch (FormatException) { return true; }
                };
                A.True(bad("a=1\na=2\n"), "겹친 키");
                A.True(bad("A=1\n"), "대문자 키");
                A.True(bad("a=1"), "줄 끝 없음");
                A.True(bad("a\n"), "= 없음");
                A.True(bad("a=\\x\n"), "모르는 이스케이프");
                A.True(bad("a=\\\n"), "끝의 역슬래시");
                var many = new StringBuilder();
                for (int i = 0; i < PipeMessage.MaxKeys + 1; i++) many.Append("k" + i + "=1\n");
                A.True(bad(many.ToString()), "키가 너무 많음");
                bool threw = false;
                try { PipeMessage.Decode(new byte[] { 0x61, 0x3d, 0xff, 0x0a }); } catch (FormatException) { threw = true; }
                A.True(threw, "UTF-8 아님");
                threw = false;
                try { PipeMessage.Read(new MemoryStream(BitConverter.GetBytes(PipeMessage.MaxBytes + 1))); } catch (FormatException) { threw = true; }
                A.True(threw, "길이 상한");
                threw = false;
                try { PipeMessage.Read(new MemoryStream(new byte[] { 10, 0, 0, 0, 0x61 })); } catch (IOException) { threw = true; }
                A.True(threw, "잘림");
            });

            T("파이프 명령: 고정된 일곱뿐 — 설정은 propose(set_ 아는 키만)로만, 그 밖의 인자는 log 의 after(수) 하나", () =>
            {
                A.True(PipeCommands.Validate(PipeCommands.Request("reload")) != null, "걷은 reload");
                var prop = ProposalFrom(new Settings());
                A.Eq((string)null, PipeCommands.Validate(prop), "제안");
                prop["set_evil"] = "1";
                A.True(PipeCommands.Validate(prop) != null, "모르는 set_ 키");
                var st = PipeCommands.Request("status");
                st["set_proxy_host"] = "x";
                A.True(PipeCommands.Validate(st) != null, "propose 밖의 set_");
                A.Eq((string)null, PipeCommands.Validate(PipeCommands.Request("status")), "status");
                A.Eq((string)null, PipeCommands.Validate(PipeCommands.Request("connect")), "connect");
                var withArg = PipeCommands.Request("connect");
                withArg["proxy_host"] = "evil.example";
                A.True(PipeCommands.Validate(withArg) != null, "connect 에 인자");
                foreach (var cmd in new[] { "set", "save", "route", "run", "exec", "install", "reload" })
                    A.True(PipeCommands.Validate(PipeCommands.Request(cmd)) != null, "없는 명령: " + cmd);
                var v2 = PipeCommands.Request("status");
                v2["v"] = "2";
                A.True(PipeCommands.Validate(v2) != null, "다른 판");
                var log = PipeCommands.Request("log");
                log["after"] = "12";
                A.Eq((string)null, PipeCommands.Validate(log), "log after");
                log["after"] = "-1";
                A.True(PipeCommands.Validate(log) != null, "음수");
                log["after"] = "x";
                A.True(PipeCommands.Validate(log) != null, "수가 아님");
            });

            T("파이프 손님 확인: 콘솔 세션의 대화형 사용자(중간 무결성 이상)만 — 망·원격 세션·낮은 무결성·비대화형은 거절, SYSTEM 은 받음", () =>
            {
                Func<PipeClientInfo> ok = () => new PipeClientInfo { SessionId = 1, Sid = "S-1-5-21-1-2-3-1001", Interactive = true, Integrity = 0x2000 };
                A.Eq((string)null, PipeClientCheck.Verdict(ok(), 1), "콘솔의 사용자");
                var c = ok(); c.Network = true;
                A.True(PipeClientCheck.Verdict(c, 1) != null, "망 로그온");
                c = ok(); c.SessionId = 2;
                A.True(PipeClientCheck.Verdict(c, 1) != null, "다른 세션(원격 데스크톱)");
                A.True(PipeClientCheck.Verdict(ok(), -1) != null, "콘솔에 사용자 없음");
                c = ok(); c.Integrity = 0x1000;
                A.True(PipeClientCheck.Verdict(c, 1) != null, "낮은 무결성");
                c = ok(); c.Interactive = false;
                A.True(PipeClientCheck.Verdict(c, 1) != null, "비대화형");
                A.Eq((string)null, PipeClientCheck.Verdict(new PipeClientInfo { Sid = "S-1-5-18", SessionId = 0 }, 1), "SYSTEM");
                A.True(PipeClientCheck.Verdict(null, 1) != null, "모름");
            });

            T("파이프 ACL: NETWORK 거부, SYSTEM·Administrators 모두, INTERACTIVE 읽기·쓰기 — 그 밖(Everyone·AppContainer)은 없음", () =>
            {
                var ps = PipeAcl.Build(null);
                var rules = ps.GetAccessRules(true, false, typeof(SecurityIdentifier)).Cast<PipeAccessRule>().ToList();
                Func<string, AccessControlType, PipeAccessRule> find = (sid, t) => rules.FirstOrDefault(r => ((SecurityIdentifier)r.IdentityReference).Value == sid && r.AccessControlType == t);
                A.True(find("S-1-5-2", AccessControlType.Deny) != null, "NETWORK 거부");
                A.True(find("S-1-5-18", AccessControlType.Allow) != null, "SYSTEM");
                A.True(find("S-1-5-32-544", AccessControlType.Allow) != null, "Administrators");
                var iu = find("S-1-5-4", AccessControlType.Allow);
                A.True(iu != null && (iu.PipeAccessRights & PipeAccessRights.ChangePermissions) == 0 && (iu.PipeAccessRights & PipeAccessRights.TakeOwnership) == 0, "INTERACTIVE 는 읽기·쓰기만");
                A.Eq(4, rules.Count, "넷뿐: " + ps.GetSecurityDescriptorSddlForm(AccessControlSections.Access));
                A.True(ps.AreAccessRulesProtected, "상속을 받지 않음");
            });

            T("서비스 몸통(dry-run): 파이프로 hello·status·connect·disconnect·log — 연결과 끊기가 상태에 실리고 log 는 이어 읽는다", () =>
            {
                using (var s = Start("roundtrip"))
                {
                    var c = Client(s);
                    var h = Send(c, PipeCommands.Request(PipeCommands.Hello));
                    A.Eq("1", h["protocol"], "판");
                    WaitState(c, TetherState.Connected, 8000, "부팅 때 저절로 연결(접두어에 맞는 Wi-Fi)");
                    Send(c, PipeCommands.Request(PipeCommands.Disconnect));
                    WaitState(c, TetherState.Off, 8000, "끊김");
                    Send(c, PipeCommands.Request(PipeCommands.Connect));
                    WaitState(c, TetherState.Connected, 8000, "다시 연결");
                    var lr = PipeCommands.Request(PipeCommands.Log);
                    lr["after"] = "0";
                    var r = Send(c, lr);
                    A.True(r["lines"].Contains("[서비스]"), "로그 줄");
                    long next = long.Parse(r["next"]);
                    lr["after"] = next.ToString();
                    A.Eq("", Send(c, lr)["lines"], "이어 읽으면 새 줄만(없음)");
                    var st = Status(c);
                    A.True(st.DryRun && st.Settings.SsidPrefix == "DIRECT-UT-", "설정 몇 칸: " + st.Settings.SsidPrefix);
                }
            });

            T("서비스 몸통: 콘솔 세션이 아닌 손님은 거절(원격 데스크톱 꼴), 같은 이름의 파이프를 남이 먼저 쥐면 서비스는 듣지 못하고 연결 일은 한다", () =>
            {
                using (var s = Start("reject"))
                {
                    s.Console = MySession() + 1000; // 손님(이 프로세스)이 콘솔 세션이 아닌 것으로
                    string err;
                    var r = Client(s).Send(PipeCommands.Request(PipeCommands.Status), out err);
                    A.True(r != null && r.ContainsKey("error") && r["error"].Contains("거절"), "거절: " + (r == null ? err : string.Join(",", r.Values.ToArray())));
                    // 같은 이름으로 두 번째 서버(선점 흉내) — FIRST_PIPE_INSTANCE 라 실패한다.
                    var second = new PipeServer(s.Pipe, PipeAcl.Build(WindowsIdentity.GetCurrent().User), (w, q) => q, null);
                    A.True(!second.Start(), "남이 쥔 이름");
                    A.True(second.StartError != null && second.StartError.Contains("먼저"), second.StartError);
                }
            });

            T("서비스 몸통: 접두어 밖 Wi-Fi 에 '연결' — 콘솔에 사용자가 없으면 묻지 않고 연결하지 않는다, 있으면 그 세션에 묻고 '예' 면 연결", () =>
            {
                using (var s = Start("ask"))
                {
                    s.Hotspot = new HotspotInfo { AdapterName = "Wi-Fi", Address = "192.168.49.61", IfIndex = 12, Ssid = "DIRECT-xy-남의망" };
                    var c = Client(s);
                    Thread.Sleep(1500);
                    Send(c, PipeCommands.Request(PipeCommands.Disconnect));
                    WaitState(c, TetherState.Off, 8000, "꺼짐");
                    s.Sessions.Session = -1; // 로그온 전·로그오프 뒤
                    Send(c, PipeCommands.Request(PipeCommands.Connect));
                    Thread.Sleep(2500);
                    A.Eq(0, s.Prompt.Asked.Count, "물을 사람이 없으면 묻지 않음");
                    A.True(Status(c).State != TetherState.Connected, "연결하지 않음");
                    A.True(s.Core.Log.Snapshot().Any(l => l.Contains("물을 사람이 없어")), "까닭을 적음");
                    s.Sessions.Session = 3;
                    s.Prompt.Answer = false;
                    Send(c, PipeCommands.Request(PipeCommands.Connect));
                    A.WaitUntil(() => s.Prompt.Asked.Count == 1, 5000, "물음");
                    A.True(s.Prompt.Asked[0].StartsWith("3|") && s.Prompt.Asked[0].Contains("DIRECT-xy-남의망"), "그 세션에 이름을 보이며: " + s.Prompt.Asked[0]);
                    Thread.Sleep(1000);
                    A.True(Status(c).State != TetherState.Connected, "'아니요' 면 연결하지 않음");
                    s.Prompt.Answer = true;
                    Send(c, PipeCommands.Request(PipeCommands.Connect));
                    WaitState(c, TetherState.Connected, 8000, "'예' 면 연결");
                }
            });

            T("서비스 상태 글: 짓고 읽으면 같고, 읽을 때 범위를 넘는 수·틀린 주소·GitHub 밖 주소·방향 제어 문자를 거른다", () =>
            {
                var st = new ServiceStatus { State = TetherState.Waiting, Detail = "기다림", Toggle = ToggleAction.Disconnect, ChildRunning = true, ChildPid = 42, NoticeSeq = 3, NoticeTitle = "알림", NoticeUrl = "https://github.com/x" };
                var back = ServiceStatus.FromMap(st.ToMap());
                A.True(back.State == TetherState.Waiting && back.Detail == "기다림" && back.Toggle == ToggleAction.Disconnect && back.ChildRunning && back.ChildPid == 42, "같음");
                A.Eq("https://github.com/x", back.NoticeUrl, "GitHub 주소");
                var m = st.ToMap();
                m["state"] = "99";
                m["proxy_host"] = "bad host!";
                m["notice_url"] = "https://evil.example/";
                m["detail"] = "a\u202Eb";
                var odd = ServiceStatus.FromMap(m);
                A.Eq(TetherState.Off, odd.State, "범위 밖 → 꺼짐");
                A.Eq(Settings.DefaultHost, odd.Settings.ProxyHost, "틀린 주소는 기본값");
                A.Eq("", odd.NoticeUrl, "GitHub 밖은 버림");
                A.True(odd.Detail.IndexOf('\u202E') < 0, "방향 제어 문자 거름");
            });

            T("트레이(ServiceClient): 서비스에 닿지 못하면 '꺼짐'·까닭·'연결' 단추, 알림 번호가 오르면 풍선 하나(처음 받은 번호는 지난 것)", () =>
            {
                var client = new ServiceClient(new PipeClient("WebTether-none-" + Guid.NewGuid().ToString("N")) { ConnectTimeoutMs = 100 }, new Log(null));
                int changed = 0;
                var notices = new List<string>();
                client.Changed += () => changed++;
                client.Notice += (t, x, w) => notices.Add(t);
                client.Apply(null, "서비스가 답하지 않습니다");
                A.True(client.State == TetherState.Off && client.Detail.Contains("서비스에 닿지 못함") && client.Toggle == ToggleAction.Connect, client.Detail);
                A.Eq(1, changed, "알림");
                client.Apply(new ServiceStatus { State = TetherState.Connected, NoticeSeq = 5, NoticeTitle = "지난 것" }, null);
                A.Eq(0, notices.Count, "처음 받은 알림 번호는 보이지 않음");
                client.Apply(new ServiceStatus { State = TetherState.Connected, NoticeSeq = 6, NoticeTitle = "연결이 끊겼습니다" }, null);
                A.Eq(1, notices.Count, "새 알림");
                A.Eq(TetherState.Connected, client.State, "상태");
            });

            T("서비스 계획: 경로는 따옴표로 싸고 --service, 따옴표·%·제어 글자·UNC·exe 아님은 거절, 서비스 DACL 은 사용자에게 시작·멈춤·설정·사용자 정의 제어를 주지 않는다", () =>
            {
                A.Eq("\"C:\\Program Files\\WebTether\\WebTether.exe\" --service", ServicePlan.ImagePath(@"C:\Program Files\WebTether\WebTether.exe"), "따옴표");
                A.Eq("\"C:\\Program Files\\WebTether\\WebTether.exe\"", ServicePlan.RunValue(@"C:\Program Files\WebTether\WebTether.exe"), "Run 값");
                foreach (var bad in new[] { "C:\\a\"b\\W.exe", @"C:\%windir%\W.exe", @"\\srv\share\W.exe", @"relative\W.exe", @"C:\x\W.bat", "C:\\x\\W\u0001.exe" })
                {
                    bool threw = false;
                    try { ServicePlan.ImagePath(bad); } catch (ArgumentException) { threw = true; }
                    A.True(threw, "거절: " + bad);
                }
                var sd = new RawSecurityDescriptor(ServicePlan.Sddl);
                const int START = 0x10, STOP = 0x20, CHANGE_CONFIG = 0x2, USER_CONTROL = 0x100, PAUSE = 0x40, WRITE_DAC = 0x40000, WRITE_OWNER = 0x80000, DELETE = 0x10000;
                foreach (CommonAce ace in sd.DiscretionaryAcl)
                {
                    var sid = ace.SecurityIdentifier.Value;
                    if (sid == "S-1-5-18" || sid == "S-1-5-32-544") continue;
                    A.True(sid == "S-1-5-4" || sid == "S-1-5-11", "그 밖의 SID 없음: " + sid);
                    A.Eq(0, ace.AccessMask & (START | STOP | CHANGE_CONFIG | USER_CONTROL | PAUSE | WRITE_DAC | WRITE_OWNER | DELETE), "사용자 권한: " + sid);
                }
                A.True(ServicePlan.InstalledExe.StartsWith(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles)), "Program Files 아래");
            });

            T("옛 예약 작업: 설치본을 인자 없이(11-21~) 또는 conhost 로 .\\launch.cmd(11-27) 돌리는 것만 우리 것 — 남의 것·둘·DTD 는 아님", () =>
            {
                var exe = @"C:\Program Files\WebTether\WebTether.exe";
                var sys = Environment.GetFolderPath(Environment.SpecialFolder.System);
                Func<string, string> xml = inner => "<?xml version=\"1.0\" encoding=\"UTF-16\"?><Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\"><Actions Context=\"Author\">" + inner + "</Actions></Task>";
                A.True(LegacyTask.IsOurs(LegacyTask.Execs(xml("<Exec><Command>\"" + exe + "\"</Command></Exec>")), exe), "옛 꼴");
                A.True(LegacyTask.IsOurs(LegacyTask.Execs(xml("<Exec><Command>" + sys + "\\conhost.exe</Command><Arguments>--headless " + sys + "\\cmd.exe /d /e:on /v:off /c .\\launch.cmd</Arguments><WorkingDirectory>C:\\Program Files\\WebTether</WorkingDirectory></Exec>")), exe), "launch.cmd 꼴");
                A.True(!LegacyTask.IsOurs(LegacyTask.Execs(xml("<Exec><Command>" + exe + "</Command><Arguments>--x</Arguments></Exec>")), exe), "인자가 붙음");
                A.True(!LegacyTask.IsOurs(LegacyTask.Execs(xml("<Exec><Command>C:\\Elsewhere\\WebTether.exe</Command></Exec>")), exe), "남의 대상");
                A.True(!LegacyTask.IsOurs(LegacyTask.Execs(xml("<Exec><Command>" + exe + "</Command></Exec><Exec><Command>C:\\x.exe</Command></Exec>")), exe), "둘");
                A.Eq((List<LegacyTask.Exec>)null, LegacyTask.Execs("<?xml version=\"1.0\"?><!DOCTYPE Task [<!ENTITY e \"x\">]><Task><Actions><Exec><Command>&e;</Command></Exec></Actions></Task>"), "DTD");
                A.Eq((List<LegacyTask.Exec>)null, LegacyTask.Execs(""), "빈 것");
            });

            T("설치기(가짜 SCM·Run 키·schtasks): 멈춤 → 해시를 견준 복사 → 옛 작업(우리 것만) 지우고 launch.cmd 걷기 → 서비스 만들기 → Run → 시작", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("install-svc"), "WebTether");
                Directory.CreateDirectory(dir);
                File.WriteAllText(Path.Combine(dir, "launch.cmd"), "@echo off");
                File.WriteAllText(Path.Combine(dir, "launch-env.txt"), "");
                var target = Path.Combine(dir, "WebTether.exe");
                var scm = new FakeScm { State = ServiceState.Running };
                var run = new FakeRun();
                var tool = new FakeTool { QueryOutput = "<Task><Actions><Exec><Command>" + target + "</Command></Exec></Actions></Task>" };
                var notes = new List<string>();
                var inst = new ServiceInstaller(scm, run, tool.Run, notes.Add) { Target = target, Policy = DirPolicy.CurrentUser(), StopTimeoutMs = 100 };
                string err;
                var bytes = Encoding.ASCII.GetBytes("MZ 가짜 exe");
                A.True(inst.Install(new MemoryStream(bytes), out err), "설치: " + err);
                A.Eq("Stop|CreateOrUpdate|Start", string.Join("|", scm.Calls.ToArray()), "SCM 차례");
                A.Eq(ServicePlan.ImagePath(target), scm.Spec.ImagePath, "따옴표 경로");
                A.Eq(ServicePlan.Sddl, scm.Spec.Sddl, "DACL");
                A.Eq(ServicePlan.RunValue(target), run.Values[ServicePlan.RunValueName], "Run 값");
                A.True(File.ReadAllBytes(target).SequenceEqual(bytes), "같은 바이트");
                A.True(tool.Calls.Any(c => c.Contains("/Delete /TN WebTether")), "옛 작업 지움: " + string.Join("|", tool.Calls.ToArray()));
                A.True(!File.Exists(Path.Combine(dir, "launch.cmd")) && !File.Exists(Path.Combine(dir, "launch-env.txt")), "launch.cmd 걷음");

                // 남의 작업이면 지우지 않는다. 만들기가 실패하면 시작하지 않는다.
                tool = new FakeTool { QueryOutput = "<Task><Actions><Exec><Command>C:\\Other\\x.exe</Command></Exec></Actions></Task>" };
                scm = new FakeScm { State = ServiceState.Missing, FailCreate = true };
                inst = new ServiceInstaller(scm, run, tool.Run, notes.Add) { Target = target, Policy = DirPolicy.CurrentUser() };
                A.True(!inst.Install(new MemoryStream(bytes), out err), "만들기 실패");
                A.True(!tool.Calls.Any(c => c.Contains("/Delete")), "남의 작업은 그대로");
                A.True(!scm.Calls.Contains("Start") && !scm.Calls.Contains("Stop"), "없던 서비스는 멈추지 않고, 실패하면 시작하지 않음: " + string.Join("|", scm.Calls.ToArray()));
            });

            T("제거기(가짜): 멈춤 → 지우기 → Run 값 → 설치본(지우지 못하면 다음 재부팅 때) → 묻고 고른 때만 상태 폴더", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("uninstall-svc"), "WebTether");
                Directory.CreateDirectory(dir);
                var target = Path.Combine(dir, "WebTether.exe");
                File.WriteAllText(target, "x");
                var data = Path.Combine(TestMain.Scratch("uninstall-data"), "WebTether");
                string perr;
                using (var held = SecureDir.Prepare(data, DirPolicy.CurrentUser(), null, out perr)) A.True(held != null, perr);
                File.WriteAllText(Path.Combine(data, "settings"), "a=1");
                var scm = new FakeScm { State = ServiceState.Running };
                var run = new FakeRun();
                run.Values[ServicePlan.RunValueName] = "\"x\"";
                var delayed = new List<string>();
                var inst = new ServiceInstaller(scm, run, new FakeTool().Run, null) { Target = target, Policy = DirPolicy.CurrentUser(), DelayDelete = p => { delayed.Add(p); return true; } };
                string err;
                using (new FileStream(target, FileMode.Open, FileAccess.Read, FileShare.Read)) // 도는 exe 흉내 — 지우기가 막힌다
                    A.True(inst.Uninstall(false, data, out err), "제거: " + err);
                A.Eq("Stop|Delete", string.Join("|", scm.Calls.ToArray()), "SCM");
                A.True(!run.Values.ContainsKey(ServicePlan.RunValueName), "Run 값 지움");
                A.True(delayed.Contains(target), "다음 재부팅 때");
                A.True(File.Exists(Path.Combine(data, "settings")), "묻지 않았으면 상태 폴더는 남김");
                inst.Uninstall(true, data, out err);
                A.True(!Directory.Exists(data), "고른 때만 지움");
            });

            T("트레이의 첫 물음: 서비스가 없으면 설치를, 판이 다르면 바꾸기를 — 설치본에서 돌거나 같으면 묻지 않음", () =>
            {
                A.True(ServiceCheck.AskText(new ServiceCheck.Result { State = ServiceState.Missing }).Contains("설치"), "없음");
                A.True(ServiceCheck.AskText(new ServiceCheck.Result { State = ServiceState.Running, ServiceHash = "a", MyHash = "b" }).Contains("이 판으로"), "다름");
                A.Eq((string)null, ServiceCheck.AskText(new ServiceCheck.Result { State = ServiceState.Running, ServiceHash = "a", MyHash = "a" }), "같음");
                A.Eq((string)null, ServiceCheck.AskText(new ServiceCheck.Result { State = ServiceState.Running, ServiceHash = "a", MyHash = "b", RunningInstalled = true }), "설치본");
                A.Eq((string)null, ServiceCheck.AskText(new ServiceCheck.Result { State = ServiceState.Unknown }), "모름");
            });

            T("이 PC 를 읽기만: 서비스 WebTether 의 상태(SCM 읽기 권한)와 예약 작업 WebTether 의 꼴", () =>
            {
                var st = ScmManager.QueryReadOnly(ServicePlan.Name);
                TestMain.Say("      서비스 " + ServicePlan.Name + ": " + st);
                string output;
                int code = SystemOps.Run("schtasks.exe", "/Query /TN " + LegacyTask.TaskName + " /XML", 10000, out output);
                if (code != 0) TestMain.Say("      예약 작업 없음(코드 " + code + ")");
                else
                {
                    var ex = LegacyTask.Execs(output);
                    A.True(ex != null, "읽음");
                    TestMain.Say("      예약 작업 있음 · 우리 것 " + LegacyTask.IsOurs(ex, ServicePlan.InstalledExe));
                }
            });

            T("트레이의 서버 확인: 세션 0 이고 PID 가 SCM 의 서비스 PID 와 같을 때만 — 세션 0 의 다른 프로세스(S4U 작업 등)·서비스가 안 돌 때는 믿지 않는다", () =>
            {
                A.Eq((string)null, PipeClient.ServerVerdict(0, 4242, 4242), "서비스");
                A.True(PipeClient.ServerVerdict(0, 4243, 4242) != null, "세션 0 의 다른 PID");
                A.True(PipeClient.ServerVerdict(0, 4242, 0) != null, "서비스가 돌고 있지 않음");
                A.True(PipeClient.ServerVerdict(1, 4242, 4242) != null, "세션 1");
                A.True(PipeClient.ServerVerdict(0, 9, 8).Contains("서비스가 아닌 프로그램"), "짧은 글");
                var keep = PipeClient.ServicePid;
                try
                {
                    PipeClient.ServicePid = () => 4242;
                    A.Eq((string)null, PipeClient.DefaultTrust(0, 4242), "기본 확인이 SCM 의 PID 를 본다");
                    A.True(PipeClient.DefaultTrust(0, 77) != null, "다른 PID");
                }
                finally { PipeClient.ServicePid = keep; }
            });

            T("서비스 몸통: 파이프 이름을 남이 먼저 잡았으면 다시 해 보고, 놓이면 연다", () =>
            {
                var name = "WebTether-test-" + Guid.NewGuid().ToString("N");
                var squat = new PipeServer(name, PipeAcl.Build(WindowsIdentity.GetCurrent().User),
                                           (w, q) => new Dictionary<string, string> { { "ok", "1" }, { "squat", "1" } }, null);
                A.True(squat.Start(), "선점");
                using (var s = Start("retry", name, c => c.PipeRetryMs = 300))
                {
                    A.True(!s.Core.Pipe.Listening, "듣지 못함");
                    var r = Send(Client(s), PipeCommands.Request(PipeCommands.Status));
                    A.True(r.ContainsKey("squat"), "그동안은 남의 서버");
                    squat.Dispose();
                    A.WaitUntil(() => s.Core.Pipe.Listening, 5000, "다시 열기");
                    r = Send(Client(s), PipeCommands.Request(PipeCommands.Status));
                    A.True(!r.ContainsKey("squat") && r.ContainsKey("state"), "이제 서비스");
                    A.True(s.Core.Log.Snapshot().Any(l => l.Contains("다시 열었습니다")), "적음");
                }
            });

            T("관리자 판 띄우기: runas 직전에 CLR 이 읽는 변수(COR_·COMPlus_·CORECLR_·DOTNET_·APPDOMAIN_MANAGER_·__COMPAT_LAYER — 대소문자 무시)를 지운다", () =>
            {
                foreach (var n in new[] { "COR_ENABLE_PROFILING", "cor_profiler", "COMPlus_X", "complus_x", "CORECLR_X", "DOTNET_X", "APPDOMAIN_MANAGER_ASM", "__COMPAT_LAYER", "__compat_layer" })
                    A.True(ClrEnv.IsRisky(n), "지움: " + n);
                foreach (var n in new[] { "PATH", "XCOR_X", "COMPLUSX", "DOTNETX", "" })
                    A.True(!ClrEnv.IsRisky(n), "둠: " + n);
                // 뜻 없는 이름만 넣는다(CLR·셔림이 읽는 이름을 쓰지 않는다 — 화면에 창이 뜰 수 있다).
                var mine = new[] { "COR_WTTEST_ZZ", "complus_WtTestZz", "CORECLR_WTTEST_ZZ", "DOTNET_WTTEST_ZZ", "APPDOMAIN_MANAGER_WTTEST_ZZ" };
                var keepLaunch = Elevation.Launch;
                var seen = new List<string>();
                try
                {
                    foreach (var n in mine) Environment.SetEnvironmentVariable(n, "1");
                    Environment.SetEnvironmentVariable("WTTEST_KEEP_ZZ", "1");
                    Elevation.Launch = (exe, args) =>
                    {
                        foreach (System.Collections.DictionaryEntry e in Environment.GetEnvironmentVariables()) seen.Add((string)e.Key);
                        return 0;
                    };
                    A.Eq(0, Elevation.Run("x.exe", "--install"), "가짜 runas");
                    foreach (var n in mine) A.True(!seen.Any(k => string.Equals(k, n, StringComparison.OrdinalIgnoreCase)), "자식 환경에 없음: " + n);
                    A.True(seen.Contains("WTTEST_KEEP_ZZ"), "다른 변수는 그대로");
                }
                finally
                {
                    Elevation.Launch = keepLaunch;
                    foreach (var n in mine) Environment.SetEnvironmentVariable(n, null);
                    Environment.SetEnvironmentVariable("WTTEST_KEEP_ZZ", null);
                }
            });

            T("설정 확인 창의 글: 자르지 않고 줄을 바꿔 다 보인다, 보이지 않는 글자·겹친 빈칸은 드러낸다, 값이 다른데 보이는 글이 같거나 너무 길면 거절, 요청한 프로그램", () =>
            {
                string rej;
                var a = new Settings();
                A.Eq((string)null, SettingsConfirm.Text(a, a.Clone(), "x.exe", 1, out rej), "같으면 묻지 않음");
                A.Eq((string)null, rej, "거절도 아님");
                var b = a.Clone();
                b.ReconnectMax = 77;
                b.Reconnect = false;
                b.Bypass = "10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 11.0.0.0/8 12.0.0.0/8";
                var t = SettingsConfirm.Text(a, b, "C:/Program Files/WebTether/WebTether.exe", 4321, out rej);
                A.True(rej == null && t != null, rej);
                A.True(t.Contains("다시 연결 횟수 상한: ‘20’ → ‘77’"), t);
                A.True(t.Contains("끊기면 다시 연결: 켬 → 끔"), t);
                A.True(t.Contains("(PID 4321)") && t.Contains("WebTether.exe"), "요청한 프로그램");
                var flat = t.Replace("" + (char)13 + (char)10 + "    ", "");
                A.True(flat.Contains("12.0.0.0/8"), "우회 목록의 꼬리까지 보인다: " + t);
                A.True(t.IndexOf('…') < 0, "자르지 않음");
                A.True(t.Split((char)10).All(l => l.Length <= SettingsConfirm.WrapChars + 6), "줄을 바꿈");
                // 옛 값(파일)의 겹친 빈칸·NBSP 는 드러낸다 — 새 값과 같아 보이지 않게
                var o = a.Clone();
                o.WifiProfile = "Home" + (char)0xA0 + "Wifi";
                var n = a.Clone();
                n.WifiProfile = "Home Wifi";
                t = SettingsConfirm.Text(o, n, "x.exe", 1, out rej);
                A.True(t != null && t.Contains("U+00A0"), "NBSP 를 드러냄: " + t);
                o.WifiProfile = "Home  Wifi";
                t = SettingsConfirm.Text(o, n, "x.exe", 1, out rej);
                A.True(t != null && t.Contains("U+0020"), "겹친 빈칸을 드러냄: " + t);
                A.Eq("‘a" + "⟨U+202E⟩" + "b’", SettingsConfirm.Show("wifi_profile", "a" + (char)0x202E + "b"), "방향 제어");
                A.Eq("(없음)", SettingsConfirm.Show("wifi_profile", ""), "빈 값은 감싸지 않음");
                // 우회 목록은 여러 /16 이 200자를 넘어도 다 보인다(검토 11-27 — 상한 1000)
                var many = a.Clone();
                many.Bypass = string.Join(", ", Enumerable.Range(0, 40).Select(i => "172." + (16 + i % 16) + "." + i + ".0/24").ToArray());
                A.True(many.Bypass.Length > 600, "길이 " + many.Bypass.Length);
                t = SettingsConfirm.Text(a, many, "x.exe", 1, out rej);
                A.True(t != null && rej == null, "긴 우회 목록: " + rej);
                A.True(t.Replace("" + (char)13 + (char)10 + "    ", "").Contains(many.Bypass), "다 보인다");
                // 상한을 넘는 값은 거절(자르지 않는다)
                var lng = a.Clone();
                lng.Bypass = string.Join(",", Enumerable.Range(0, 120).Select(i => "10.0.0." + i).ToArray());
                A.True(lng.Bypass.Length > 1000, "1000자 넘음");
                A.True(SettingsConfirm.Text(a, lng, "x.exe", 1, out rej) == null && rej != null, "긴 값 거절");
                var lp = a.Clone();
                lp.WifiProfile = new string('w', 201);
                A.True(SettingsConfirm.Text(a, lp, "x.exe", 1, out rej) == null && rej != null, "다른 칸은 200자");
            });

            T("설정 제안: 검사기에 걸리면(빠진 키·틀린 값·보이지 않는 글자·줄바꿈) 묻지도 않고 거절, 바뀐 것이 없으면 묻지 않음", () =>
            {
                using (var s = Start("propose-invalid"))
                {
                    s.Sessions.Session = MySession();
                    var c = Client(s);
                    Func<Action<Dictionary<string, string>>, Dictionary<string, string>> propose = tweak =>
                    {
                        var req = ProposalFrom(new Settings());
                        tweak(req);
                        string err;
                        var r = c.Send(req, out err);
                        A.True(r != null, err);
                        return r;
                    };
                    A.Eq(ProposalResults.Invalid, propose(q => q["set_proxy_port"] = "0")["result"], "포트 0");
                    A.Eq(ProposalResults.Invalid, propose(q => q.Remove("set_bypass"))["result"], "빠진 키");
                    A.Eq(ProposalResults.Invalid, propose(q => q["set_wifi_profile"] = "a" + (char)0x202E + "b")["result"], "방향 제어");
                    A.Eq(ProposalResults.Invalid, propose(q => q["set_wifi_profile"] = "a" + (char)10 + "b")["result"], "줄바꿈");
                    A.Eq(ProposalResults.Invalid, propose(q => q["set_bypass"] = new string('1', 600))["result"], "긴 값");
                    A.True(propose(q => q["set_evil"] = "1").ContainsKey("error"), "모르는 키는 파이프가 거절");
                    A.Eq(ProposalResults.Unchanged, propose(q => { })["result"], "바뀐 것 없음");
                    A.Eq(0, s.Prompt.Asked.Count, "묻지 않음");
                }
            });

            T("설정 제안: 콘솔 세션의 손님만, '예' 면 확인 창에 보인 그 값을 저장하고 건다 — 트레이의 설정 창 바탕(ServiceSettingsHost)으로", () =>
            {
                using (var s = Start("propose-apply"))
                {
                    var host = new ServiceSettingsHost(Client(s), () => false) { PollMs = 100, WaitMs = 15000 };
                    A.Eq((string)null, host.Refresh(), "지금 설정 읽기");
                    var want = host.Settings.Clone();
                    want.ReconnectMax = 77;
                    want.Bypass = "10.0.0.0/8,172.16.0.0/12";
                    s.Sessions.Session = MySession() + 7; // 손님의 세션이 콘솔이 아님
                    Exception ex = null;
                    try { host.SaveSettings(want); } catch (Exception e) { ex = e; }
                    A.True(ex != null && ex.Message.Contains("이 화면"), "세션이 다르면 거절: " + (ex == null ? "" : ex.Message));
                    s.Sessions.Session = -1;
                    ex = null;
                    try { host.SaveSettings(want); } catch (Exception e) { ex = e; }
                    A.True(ex != null, "콘솔에 사용자 없음");
                    A.Eq(0, s.Prompt.Asked.Count, "묻지 않음");
                    s.Sessions.Session = MySession();
                    s.Prompt.Answer = true;
                    host.SaveSettings(want);
                    A.Eq(1, s.Prompt.Asked.Count, "한 번 물음");
                    A.True(s.Prompt.Asked[0].StartsWith(MySession() + "|"), "그 세션에");
                    A.True(s.Prompt.Asked[0].Contains("다시 연결 횟수 상한: ‘20’ → ‘77’") && s.Prompt.Asked[0].Contains("‘10.0.0.0/8, 172.16.0.0/12’"), s.Prompt.Asked[0]);
                    A.WaitUntil(() => s.Core.Controller.Settings.ReconnectMax == 77, 3000, "걸림");
                    A.Eq("10.0.0.0/8, 172.16.0.0/12", s.Core.Controller.Settings.Bypass, "보인 값(저장 꼴) 그대로");
                    var onDisk = Settings.Load(s.Core.Paths.SettingsFile, null);
                    A.Eq(77, onDisk.ReconnectMax, "저장");
                    A.Eq((string)null, host.Refresh(), "다시 읽기");
                    A.Eq(77, host.Settings.ReconnectMax, "서비스의 지금 설정");
                }
            });

            T("설정 제안: 묻는 창은 한 번에 하나, 거절·시간 초과 뒤에는 쉬는 동안 새 제안을 받지 않는다", () =>
            {
                using (var s = Start("propose-busy", null, c => { c.ProposalQuietMs = 1500; c.ProposalQuietLongMs = 60000; }))
                {
                    s.Sessions.Session = MySession();
                    var c = Client(s);
                    var gate = new ManualResetEventSlim(false);
                    s.Prompt.Gate = gate;
                    s.Prompt.Answer = false;
                    var a = new Settings { ReconnectMax = 55 };
                    var r1 = Send(c, ProposalFrom(a));
                    A.Eq(ProposalResults.Asking, r1["result"], "첫 제안");
                    A.WaitUntil(() => s.Prompt.Asked.Count == 1, 3000, "물음");
                    var r2 = Send(c, ProposalFrom(new Settings { ReconnectMax = 66 }));
                    A.Eq(ProposalResults.Busy, r2["result"], "다른 물음이 떠 있음");
                    gate.Set();
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절");
                    var r3 = Send(c, ProposalFrom(new Settings { ReconnectMax = 66 }));
                    A.Eq(ProposalResults.Later, r3["result"], "쉬는 동안");
                    Thread.Sleep(1700);
                    s.Prompt.Answer = true;
                    var r4 = Send(c, ProposalFrom(new Settings { ReconnectMax = 66 }));
                    A.Eq(ProposalResults.Asking, r4["result"], "쉬고 나면 다시");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Applied, 3000, "적용");
                    A.Eq(2, s.Prompt.Asked.Count, "물음 둘뿐");
                }
            });

            T("설정 검사(검토 11-27): 프록시 주소는 폰의 192.168.49.x 만, 우회 목록은 /8 보다 넓거나 0. 으로 시작하면 거절, 옛 파일의 그런 값은 버리고 기본값", () =>
            {
                Func<Action<Settings>, bool> bad = f => { var s = new Settings(); f(s); return s.Validate().Count > 0; };
                A.True(!bad(s => s.ProxyHost = "192.168.49.7"), "폰");
                foreach (var h in new[] { "192.168.49.l", "192.168.49.1-phone.attacker.example", "phone.local", "192.168.49.0", "192.168.49.255", "192.168.50.1", "192.168.049.1" })
                    A.True(bad(s => s.ProxyHost = h), "거절: " + h);
                A.True(!bad(s => s.Bypass = "10.0.0.0/8, 172.16.0.0/12, 1.2.3.4"), "보통 목록");
                foreach (var w in new[] { "0.0.0.0/1", "128.0.0.0/1", "10.0.0.0/7", "0.1.2.3", "0.0.0.0/0" })
                    A.True(bad(s => s.Bypass = "10.0.0.0/8," + w), "넓음: " + w);
                var probs = new List<string>();
                var r = Settings.Parse("proxy_host=192.168.49.1-x.attacker.example" + (char)10 + "bypass=0.0.0.0/1" + (char)10 + "reconnect_max=9" + (char)10, probs);
                A.Eq(Settings.DefaultHost, r.ProxyHost, "옛 파일의 이름은 버리고 기본값");
                A.Eq("", r.Bypass, "옛 파일의 넓은 우회는 버림");
                A.Eq(9, r.ReconnectMax, "다른 키는 그대로");
                A.Eq(2, probs.Count, string.Join("|", probs.ToArray()));
            });

            T("설정 제안의 값(검토 11-27): U+0020 밖의 빈칸·겹친 빈칸·앞뒤 빈칸은 거절", () =>
            {
                Func<string, string> err = v =>
                {
                    var q = ProposalFrom(new Settings());
                    q["set_wifi_profile"] = v;
                    string e;
                    return Settings.FromProposal(q, PipeCommands.SettingPrefix, out e) == null ? e : null;
                };
                A.Eq((string)null, err("Home Wifi"), "보통");
                A.True(err("Home" + (char)0xA0 + "Wifi") != null, "NBSP");
                A.True(err("Home" + (char)0x3000 + "Wifi") != null, "전각 빈칸");
                A.True(err("Home  Wifi") != null, "겹친 빈칸");
                A.True(err(" Home") != null && err("Home ") != null, "앞뒤 빈칸");
            });

            T("값 규칙(검토 11-27): 파일 읽기와 제안이 같은 CheckValue — 걸린 키는 파일에서 기본값, 우회 목록은 틀리거나 넓은 칸만 뺀다, → ⟨ ⟩ ‘ ’·섞인 글자 거절", () =>
            {
                Func<string, string, string> err = (k, v) =>
                {
                    var q = ProposalFrom(new Settings());
                    q["set_" + k] = v;
                    string e;
                    return Settings.FromProposal(q, PipeCommands.SettingPrefix, out e) == null ? e : null;
                };
                Func<string, string, string> parsed = (k, v) =>
                {
                    var probs = new List<string>();
                    var s = Settings.Parse(k + "=" + v + (char)10, probs);
                    return s.Pairs().First(kv => kv.Key == k).Value + (probs.Count > 0 ? "|" + string.Join("|", probs.ToArray()) : "");
                };
                var bad = new[]
                {
                    "Home" + (char)0xA0 + "Wifi", "Home  Wifi", " Home", "Home" + (char)0x2192 + "x", "a" + (char)0x27E8 + "U+0041" + (char)0x27E9,
                    (char)0x2018 + "x" + (char)0x2019, "H" + (char)0x043E + "me", "Wi" + (char)0x03B1, (char)0xFF37 + "ifi", "Home" + (char)0x200B,
                };
                foreach (var v in bad)
                {
                    A.True(err("wifi_profile", v) != null, "제안 거절 [" + v + "]");
                    var p = parsed("wifi_profile", v);
                    A.True(p.StartsWith("|") && p.Contains("값을 버림: wifi_profile"), "파일에서도 버림 [" + v + "] → " + p);
                }
                foreach (var v in new[] { "집 Wi-Fi", "카페Wi-Fi", "Café 5G", "Дом Wi-Fi", "Ｗｉｆｉ" })
                {
                    A.Eq((string)null, err("wifi_profile", v), "받음 [" + v + "]");
                    A.Eq(v, parsed("wifi_profile", v), "파일도 받음 [" + v + "]");
                }
                A.True(err("ssid_prefix", "DIRECT-" + (char)0x0410) != null, "SSID 앞머리는 ASCII 만");
                // 우회 목록: 제안은 통째로 거절, 파일은 틀리거나 넓은 칸만 뺀다
                A.True(err("bypass", "10.1.0.0/16, 0.0.0.0/0") != null, "제안 — 넓은 칸");
                var pb = parsed("bypass", "10.1.0.0/16, 0.0.0.0/0 junk;172.16.0.0/12");
                A.True(pb.StartsWith("10.1.0.0/16, 172.16.0.0/12|") && pb.Contains("우회 목록에서 뺌") && pb.Contains("0.0.0.0/0") && pb.Contains("junk"), pb);
                A.Eq("10.1.0.0/16, 172.16.0.0/12", parsed("bypass", "10.1.0.0/16 172.16.0.0/12"), "좁은 것만이면 문제 없음");
                // 상한 — 우회 목록 1000, 나머지 200
                var wide = string.Join(", ", Enumerable.Range(0, 60).Select(i => "10." + i + ".0.0/16").ToArray());
                A.True(wide.Length > 600 && wide.Length <= 1000, "길이 " + wide.Length);
                A.Eq((string)null, err("bypass", wide), "여러 /16 은 받음");
                A.Eq(wide, parsed("bypass", wide), "파일도");
                var tooLong = string.Join(", ", Enumerable.Range(0, 90).Select(i => "10." + i + ".0.0/16").ToArray());
                A.True(err("bypass", tooLong) != null, "1000자 넘음");
                A.True(parsed("bypass", tooLong).StartsWith("|"), "파일에서도 버림");
                A.True(err("wifi_profile", new string('w', 201)) != null, "다른 칸 200자");
            });

            T("우회 목록의 저장 꼴(7차): 제안·확인 창·저장·다시 읽기가 같은 ', ' 꼴 — 1000자 가까운 목록이 다시 읽어도 그대로, 날 글이 짧아도 저장 꼴이 1000자를 넘으면 거절", () =>
            {
                Func<int, string, string> list = (n, sep) => string.Join(sep, Enumerable.Range(0, n).Select(i => "10.1." + (100 + i) + ".0/24").ToArray());
                // 빈칸·쉼표·쌍반점이 섞인 입력
                var mixed = string.Join("", Enumerable.Range(0, 66).Select(i => (i == 0 ? "" : i % 3 == 0 ? "," : i % 3 == 1 ? " " : ";") + "10.1." + (100 + i) + ".0/24").ToArray());
                var canon = list(66, ", ");
                A.True(canon.Length > 980 && canon.Length <= 1000, "길이 " + canon.Length);
                A.Eq(canon, Settings.NormalizeBypass(mixed), "정규화");
                var q = ProposalFrom(new Settings());
                q["set_bypass"] = mixed;
                string e;
                var s = Settings.FromProposal(q, PipeCommands.SettingPrefix, out e);
                A.True(s != null, "받음: " + e);
                A.Eq(canon, s.Bypass, "저장 꼴로");
                string rej;
                var t = SettingsConfirm.Text(new Settings(), s, "x.exe", 1, out rej);
                A.True(t != null && t.Replace("" + (char)13 + (char)10 + "    ", "").Contains("‘" + canon + "’"), "확인 창은 저장 꼴: " + rej);
                var probs = new List<string>();
                var defaulted = new List<string>();
                var back = Settings.Parse(s.Serialize(), probs, defaulted);
                A.Eq(canon, back.Bypass, "다시 읽어도 그대로");
                A.True(probs.Count == 0 && defaulted.Count == 0, "문제 없음: " + string.Join("|", probs.ToArray()));
                // 감사가 재현한 꼴 — 빈칸으로 이은 목록(날 글은 1000자 밑)도 ', ' 로 이으면 1000자를 넘는다 → 제안에서 거절
                var spaced = list(67, " ");
                A.True(spaced.Length < 1000 && Settings.NormalizeBypass(spaced).Length > 1000, "날 글 " + spaced.Length + " · 저장 꼴 " + Settings.NormalizeBypass(spaced).Length);
                q["set_bypass"] = spaced;
                A.True(Settings.FromProposal(q, PipeCommands.SettingPrefix, out e) == null, "저장 꼴이 1000자를 넘으면 거절");
                // 실제 서비스: 섞인 입력을 제안 → 저장 → 서비스를 다시 띄워 읽기
                string file;
                using (var sv = Start("bypass-canon"))
                {
                    sv.Sessions.Session = MySession();
                    var c = Client(sv);
                    q["set_bypass"] = mixed;
                    A.Eq(ProposalResults.Asking, Send(c, q)["result"], "물음");
                    A.WaitUntil(() => sv.Core.Controller.Settings.Bypass == canon, 3000, "걸림");
                    A.True(sv.Prompt.Asked[0].Replace("" + (char)13 + (char)10 + "    ", "").Contains("‘" + canon + "’"), "보인 것");
                    file = sv.Core.Paths.SettingsFile;
                }
                var again = Settings.Load(file, probs, defaulted);
                A.Eq(canon, again.Bypass, "파일을 다시 읽어도 그대로");
                A.True(probs.Count == 0 && defaulted.Count == 0, "버린 것 없음");
            });

            T("파일 읽기에서 기본값으로 돌린 키(7차): Parse 가 키를 모으고, 서비스가 settings 답에 싣고, 설정 창 바탕이 한 줄로", () =>
            {
                var probs = new List<string>();
                var d = new List<string>();
                Settings.Parse("ssid_prefix=내폰-" + (char)10 + "bypass=10.0.0.0/8 0.0.0.0/0" + (char)10 + "reconnect_max=9" + (char)10 + "evil=1" + (char)10 +
                               "wifi_profile=a" + (char)0x2192 + "b" + (char)10 + "wifi_profile=Home" + (char)10, probs, d);
                A.True(d.Contains("ssid_prefix") && d.Contains("bypass") && d.Count == 2, "버린 키·칸을 뺀 우회 목록만(뒤 줄이 바로 적은 키는 빼고): " + string.Join(",", d.ToArray()));
                A.Eq("설정 일부를 기본값으로 돌렸습니다: 자동 연결 Wi-Fi, 우회 목록", Settings.DefaultedNotice("ssid_prefix,bypass,evil"), "한 줄(아는 키만)");
                A.Eq((string)null, Settings.DefaultedNotice(""), "없으면 null");
                A.Eq((string)null, Settings.DefaultedNotice("evil"), "모르는 키만이면 null");
                using (var sv = Start("defaulted", null, c => File.WriteAllText(c.Paths.SettingsFile, "ssid_prefix=내폰-" + (char)10 + "reconnect_max=9" + (char)10, new System.Text.UTF8Encoding(false))))
                {
                    A.Eq(9, sv.Core.Controller.Settings.ReconnectMax, "다른 키는 읽음");
                    var host = new ServiceSettingsHost(Client(sv), () => false) { PollMs = 100, WaitMs = 15000 };
                    A.Eq((string)null, host.Refresh(), "읽기");
                    A.Eq("설정 일부를 기본값으로 돌렸습니다: 자동 연결 Wi-Fi", host.LoadNotice, "설정 창의 한 줄");
                    sv.Sessions.Session = MySession();
                    var want = host.Settings.Clone();
                    want.ReconnectMax = 10;
                    host.SaveSettings(want);
                    A.Eq((string)null, host.Refresh(), "다시 읽기");
                    A.Eq((string)null, host.LoadNotice, "제안이 적용되면 지운다");
                }
            });

            T("설치본 대조(검토 11-27): 경로 글이 아니라 열어서 볼륨 일련번호 + 파일 ID — 접합점·대소문자를 거쳐도 같고, 사본은 다르다", () =>
            {
                var me = MyImage();
                var dir = TestMain.Scratch("file-identity");
                var copy = Path.Combine(dir, "copy.exe");
                File.Copy(me, copy, true);
                var link = Path.Combine(dir, "j");
                BinariesTests.Junction(link, Path.GetDirectoryName(me));
                try
                {
                    var viaLink = Path.Combine(link, Path.GetFileName(me));
                    A.True(FileIdentity.Same(me, me), "같은 경로");
                    A.True(FileIdentity.Same(me.ToUpperInvariant(), me), "대소문자");
                    A.True(FileIdentity.Same(viaLink, me), "접합점을 거쳐도 같은 파일");
                    A.True(!FileIdentity.Same(copy, me), "사본은 다른 파일");
                    A.True(!FileIdentity.Same(Path.Combine(dir, "none.exe"), me) && !FileIdentity.Same(null, me) && !FileIdentity.Same("", me), "없으면 false");
                    using (var s = Start("propose-identity", null, c => c.ProposerImage = viaLink))
                    {
                        s.Sessions.Session = MySession();
                        A.Eq(ProposalResults.Asking, Send(Client(s), ProposalFrom(new Settings { ReconnectMax = 34 }))["result"], "접합점 경로로 적은 설치본도 받는다");
                    }
                    using (var s = Start("propose-copy", null, c => c.ProposerImage = copy))
                    {
                        s.Sessions.Session = MySession();
                        A.Eq(ProposalResults.Program, Send(Client(s), ProposalFrom(new Settings { ReconnectMax = 35 }))["result"], "사본이면 거절");
                    }
                }
                finally { Directory.Delete(link, false); }
            });

            T("제안 번호는 서비스가 뜰 때마다 무작위로 시작하고, 거절 되풀이 셈은 창(ProposalRejectWindowMs)이 지나면 0 으로", () =>
            {
                long b1, b2;
                using (var s = Start("propose-base1")) b1 = Status(Client(s)).ProposalId;
                using (var s = Start("propose-base2"))
                {
                    s.Sessions.Session = MySession();
                    var c = Client(s);
                    b2 = Status(c).ProposalId;
                    A.True(b1 > 0 && b2 > 0 && b1 != b2, "무작위 시작: " + b1 + " " + b2);
                    A.Eq(b2 + 1, long.Parse(Send(c, ProposalFrom(new Settings { ReconnectMax = 36 }))["id"]), "그 뒤로는 하나씩");
                }
                using (var s = Start("propose-window", null, c => { c.ProposalQuietMs = 300; c.ProposalQuietLongMs = 60000; c.ProposalRejectWindowMs = 1000; }))
                {
                    s.Sessions.Session = MySession();
                    s.Prompt.Answer = false;
                    var c = Client(s);
                    A.Eq(ProposalResults.Asking, Send(c, ProposalFrom(new Settings { ReconnectMax = 37 }))["result"], "1");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절 1");
                    Thread.Sleep(1300); // 창이 지남
                    A.Eq(ProposalResults.Asking, Send(c, ProposalFrom(new Settings { ReconnectMax = 38 }))["result"], "2");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절 2");
                    Thread.Sleep(450);
                    A.Eq(ProposalResults.Asking, Send(c, ProposalFrom(new Settings { ReconnectMax = 39 }))["result"], "창이 지난 뒤의 거절은 첫 거절로 — 짧게 쉼");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절 3");
                    Thread.Sleep(450);
                    A.Eq(ProposalResults.Later, Send(c, ProposalFrom(new Settings { ReconnectMax = 40 }))["result"], "창 안에서 되풀이되면 길게");
                }
            });

            T("설정 제안: 설치본의 트레이가 아닌 프로그램의 제안은 묻지 않고 거절, 확인 창에 요청한 프로그램의 경로·PID", () =>
            {
                using (var s = Start("propose-program", null, c => c.ProposerImage = Path.Combine(Path.GetTempPath(), "not-the-tray.exe")))
                {
                    s.Sessions.Session = MySession();
                    var r = Send(Client(s), ProposalFrom(new Settings { ReconnectMax = 33 }));
                    A.Eq(ProposalResults.Program, r["result"], "거절");
                    A.Eq(0, s.Prompt.Asked.Count, "묻지 않음");
                }
                using (var s = Start("propose-requester"))
                {
                    s.Sessions.Session = MySession();
                    var c = Client(s);
                    Send(c, ProposalFrom(new Settings { ReconnectMax = 33 }));
                    A.WaitUntil(() => s.Prompt.Asked.Count == 1, 3000, "물음");
                    int me;
                    using (var p = Process.GetCurrentProcess()) me = p.Id;
                    var flat = s.Prompt.Asked[0].Replace("" + (char)13 + (char)10 + "    ", "");
                    A.True(flat.Contains("‘" + MyImage() + "’ (PID " + me + ")"), s.Prompt.Asked[0]);
                }
            });

            T("설정 제안: 끝난 뒤에는 결과와 상관없이 잠깐 쉬고, 거절이 되풀이되면 더 길게, 최근 결과가 상태에 남고 트레이는 남의 제안을 알아챈다", () =>
            {
                using (var s = Start("propose-after", null, c => { c.ProposalAfterMs = 1500; c.ProposalQuietMs = 1500; c.ProposalQuietLongMs = 60000; }))
                {
                    s.Sessions.Session = MySession();
                    var c = Client(s);
                    var r = Send(c, ProposalFrom(new Settings { ReconnectMax = 41 }));
                    long id1 = long.Parse(r["id"]);
                    A.WaitUntil(() => Status(c).ProposalRecent.Any(kv => kv.Key == id1 && kv.Value == ProposalResults.Applied), 3000, "적용이 최근 결과에");
                    A.Eq(ProposalResults.Later, Send(c, ProposalFrom(new Settings { ReconnectMax = 42 }))["result"], "적용 직후에도 쉰다");
                    Thread.Sleep(1700);
                    s.Prompt.Answer = false;
                    r = Send(c, ProposalFrom(new Settings { ReconnectMax = 43 }));
                    A.Eq(ProposalResults.Asking, r["result"], "쉬고 나면");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절 1");
                    Thread.Sleep(1700);
                    r = Send(c, ProposalFrom(new Settings { ReconnectMax = 44 }));
                    A.Eq(ProposalResults.Asking, r["result"], "첫 거절 뒤의 쉼은 짧다");
                    A.WaitUntil(() => Status(c).ProposalResult == ProposalResults.Rejected, 3000, "거절 2");
                    Thread.Sleep(1700);
                    A.Eq(ProposalResults.Later, Send(c, ProposalFrom(new Settings { ReconnectMax = 45 }))["result"], "되풀이되면 길게");
                    var st = Status(c);
                    A.True(st.ProposalRecent.Count == 3 && st.ProposalRecent[0].Key == id1, "최근 결과 셋: " + st.ProposalRecent.Count);
                }
                // 트레이: 제 결과를 모르는 채 더 큰 번호가 보이면 기다림을 끝낸다
                var fake = new ServiceStatus { ProposalId = 9, ProposalResult = ProposalResults.Asking };
                var back = ServiceStatus.FromMap(fake.ToMap());
                A.Eq(9L, back.ProposalId, "번호");
                fake.ProposalRecent.Add(new KeyValuePair<long, string>(7, ProposalResults.Applied));
                back = ServiceStatus.FromMap(fake.ToMap());
                A.True(back.ProposalRecent.Count == 1 && back.ProposalRecent[0].Key == 7, "최근 결과 왕복");
                var m = fake.ToMap();
                m["proposal_recent"] = "7:applied;x:applied;8:evil;9:rejected";
                back = ServiceStatus.FromMap(m);
                A.Eq(2, back.ProposalRecent.Count, "틀린 것은 버림");
            });

            T("트레이의 설정 창 바탕: 제 제안의 결과를 기다리고(실제 파이프), 번호가 달라지면(남의 제안·서비스 다시 뜸) 기다림을 끝낸다", () =>
            {
                using (var s = Start("propose-race", null, c => c.ProposalAfterMs = 0))
                {
                    s.Sessions.Session = MySession();
                    var gate = new ManualResetEventSlim(false);
                    s.Prompt.Gate = gate;
                    var host = new ServiceSettingsHost(Client(s), () => false) { PollMs = 100, WaitMs = 15000 };
                    A.Eq((string)null, host.Refresh(), "읽기");
                    var want = host.Settings.Clone();
                    want.ReconnectMax = 61;
                    Exception ex = null;
                    var t = new Thread(() => { try { host.SaveSettings(want); } catch (Exception e) { ex = e; } });
                    t.Start();
                    A.WaitUntil(() => s.Prompt.Asked.Count == 1, 3000, "물음");
                    gate.Set();
                    t.Join(10000);
                    A.True(ex == null, "제 것은 적용: " + (ex == null ? "" : ex.Message));
                }
                var st = new ServiceStatus { ProposalId = 12, ProposalResult = ProposalResults.Asking };
                A.True(ServiceSettingsHost.Judge(st, 11).Contains("다시 저장"), "더 큰 번호");
                A.True(ServiceSettingsHost.Judge(new ServiceStatus { ProposalId = 5, ProposalResult = "" }, 11).Contains("다시 저장"), "더 작은 번호(서비스가 다시 뜸)");
                st.ProposalRecent.Add(new KeyValuePair<long, string>(11, ProposalResults.Rejected));
                A.True(ServiceSettingsHost.Judge(st, 11).Contains("거절"), "최근 목록의 제 결과가 먼저");
                A.Eq((string)null, ServiceSettingsHost.Judge(new ServiceStatus { ProposalId = 11, ProposalResult = ProposalResults.Asking }, 11), "아직");
                A.Eq("", ServiceSettingsHost.Judge(new ServiceStatus { ProposalId = 11, ProposalResult = ProposalResults.Applied }, 11), "적용");
            });

            T("관리자 판 띄우기 전: HKCU 의 Environment(CLR 변수)·.NETFramework(값 아무거나)를 읽기만 하고, 있으면 올리지 않고 이름을 알린다", () =>
            {
                var keepNames = Elevation.UserValueNames;
                var keepSubs = Elevation.UserSubKeyNames;
                var keepLaunch = Elevation.Launch;
                int launched = 0;
                try
                {
                    Elevation.Launch = (e, a) => { launched++; return 0; };
                    var reg = new Dictionary<string, string[]>();
                    Elevation.UserValueNames = k => { string[] v; return reg.TryGetValue(k, out v) ? v : null; };
                    var subs = new Dictionary<string, string[]>();
                    Elevation.UserSubKeyNames = k => { string[] v; return subs.TryGetValue(k, out v) ? v : null; };
                    A.Eq(0, Elevation.Run("x.exe", "--install"), "없으면 올린다");
                    // Framework 에 뜻 없는 이름은 막지 않는다(검토 11-27)
                    reg[Elevation.EnvironmentKey] = new[] { "DOTNET_CLI_TELEMETRY_OPTOUT", "CORECLR_X", "DOTNET_ROOT" };
                    A.Eq(0, Elevation.Run("x.exe", "--install"), "DOTNET_·CORECLR_ 는 막지 않음");
                    // Volatile Environment 와 그 세션 하위 키
                    reg[Elevation.EnvironmentKey] = new[] { "Path" };
                    reg[Elevation.VolatileKey] = new[] { "LOGONSERVER", "__compat_layer" };
                    A.Eq(-2, Elevation.Run("x.exe", "--install"), "Volatile Environment 의 값");
                    A.True(Elevation.LastBlocked.Contains("__compat_layer"), Elevation.LastBlocked);
                    reg[Elevation.VolatileKey] = new[] { "LOGONSERVER" };
                    subs[Elevation.VolatileKey] = new[] { "1" };
                    reg[Elevation.VolatileKey + @"\1"] = new[] { "CLIENTNAME", "COMPlus_WtTestZz" };
                    A.Eq(-2, Elevation.Run("x.exe", "--install"), "세션 하위 키의 값");
                    A.True(Elevation.LastBlocked.Contains("COMPlus_WtTestZz"), Elevation.LastBlocked);
                    reg.Remove(Elevation.VolatileKey + @"\1");
                    A.Eq(0, Elevation.Run("x.exe", "--install"), "하위 키에 없으면 올린다");
                    launched -= 2;
                    reg[Elevation.EnvironmentKey] = new[] { "Path", "TEMP", "cor_profiler" };
                    A.Eq(-2, Elevation.Run("x.exe", "--install"), "환경에 CLR 변수");
                    A.True(Elevation.LastBlocked.Contains("cor_profiler"), Elevation.LastBlocked);
                    reg[Elevation.EnvironmentKey] = new[] { "Path" };
                    reg[Elevation.NetFrameworkKey] = new[] { "OnlyUseLatestCLR" };
                    A.Eq(-2, Elevation.Run("x.exe", "--uninstall"), ".NETFramework 의 값");
                    A.True(Elevation.LastBlocked.Contains("OnlyUseLatestCLR"), Elevation.LastBlocked);
                    reg[Elevation.NetFrameworkKey] = new string[0];
                    A.Eq(0, Elevation.Run("x.exe", "--install"), "빈 키는 괜찮다");
                    A.Eq(2, launched, "올린 것은 두 번뿐");
                    Elevation.UserValueNames = k => { throw new UnauthorizedAccessException("막힘"); };
                    A.Eq(-2, Elevation.Run("x.exe", "--install"), "읽지 못하면 올리지 않음");
                }
                finally
                {
                    Elevation.UserValueNames = keepNames;
                    Elevation.UserSubKeyNames = keepSubs;
                    Elevation.Launch = keepLaunch;
                }
                TestMain.Say("      이 PC 의 HKCU(읽기만): " + (Elevation.Precheck() ?? "걸리는 값 없음"));
            });

            T("제거기: 상태 폴더는 주인·권한이 정책에 맞을 때만 지우고, 아니면 지우지 않고 옆으로 옮긴다", () =>
            {
                var dir = Path.Combine(TestMain.Scratch("uninstall-trust"), "Prog");
                Directory.CreateDirectory(dir);
                var target = Path.Combine(dir, "WebTether.exe");
                File.WriteAllText(target, "x");
                var data = Path.Combine(TestMain.Scratch("uninstall-trust-data"), "WebTether");
                Directory.CreateDirectory(data);
                SecurityTests.GrantEveryone(data);
                File.WriteAllText(Path.Combine(data, "settings"), "a=1");
                var notes = new List<string>();
                var inst = new ServiceInstaller(new FakeScm { State = ServiceState.Missing }, new FakeRun(), new FakeTool().Run, notes.Add)
                {
                    Target = target, Policy = DirPolicy.CurrentUser(), DelayDelete = p => true,
                };
                string err;
                A.True(inst.Uninstall(true, data, out err), err);
                A.True(!Directory.Exists(data), "제자리에는 없음");
                var aside = SecurityTests.Aside(data);
                A.Eq(1, aside.Length, "옮긴 폴더 하나");
                A.True(File.Exists(Path.Combine(aside[0], "settings")), "지우지 않음");
                A.True(notes.Any(n => n.Contains("옮겼습니다")), string.Join("|", notes.ToArray()));
            });
        }

        sealed class FakeScm : IServiceManager
        {
            public ServiceState State;
            public bool FailCreate;
            public ServiceSpec Spec;
            public readonly List<string> Calls = new List<string>();
            public ServiceState Query(string name) { return State; }
            public bool Stop(string name, int timeoutMs, out string error) { Calls.Add("Stop"); error = null; State = ServiceState.Stopped; return true; }
            public bool CreateOrUpdate(ServiceSpec spec, out string error)
            {
                Calls.Add("CreateOrUpdate");
                Spec = spec;
                error = FailCreate ? "거절" : null;
                return !FailCreate;
            }
            public bool Start(string name, out string error) { Calls.Add("Start"); error = null; State = ServiceState.Running; return true; }
            public bool Delete(string name, out string error) { Calls.Add("Delete"); error = null; State = ServiceState.Missing; return true; }
        }

        sealed class FakeRun : IRunKey
        {
            public readonly Dictionary<string, string> Values = new Dictionary<string, string>();
            public string Get(string name) { string v; return Values.TryGetValue(name, out v) ? v : null; }
            public bool Set(string name, string value, out string error) { Values[name] = value; error = null; return true; }
            public bool Delete(string name, out string error) { Values.Remove(name); error = null; return true; }
        }

        sealed class FakeTool
        {
            public string QueryOutput;
            public readonly List<string> Calls = new List<string>();
            public int Run(string exe, string args, int timeoutMs, out string output)
            {
                Calls.Add(exe + " " + args);
                output = "";
                if (args.StartsWith("/Query"))
                {
                    if (QueryOutput == null) return 1;
                    output = QueryOutput;
                    return 0;
                }
                return 0;
            }
        }
    }
}
