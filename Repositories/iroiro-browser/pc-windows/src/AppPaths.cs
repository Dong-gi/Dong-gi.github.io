// 파일 자리 — 모두 %ProgramData%\WebTether 하나(관리자 전용 — SecureFs). 설정·업데이트·연결 상태·끊어 둔 표시·로그·실행 파일.
// 예전 판은 %APPDATA%\WebTether(설정)·%LOCALAPPDATA%\WebTether(로그·실행 파일·상태)에 썼다 — 사용자가 쓸 수 있는 자리라 옮겼다.
// 옛 자리의 파일은 읽지도 지우지도 않는다(사용자 결정 — 가져오기를 걷었다). 걷은 사용량 통계가 남긴 usage 파일도 그대로 둔다.
using System;
using System.IO;

namespace WebTether
{
    public sealed class AppPaths
    {
        /// <summary>모든 상태의 뿌리 — 쓰기 전에 그 아래에 재분석 지점이 없는지 이것부터 본다.</summary>
        public string Root;

        public string SettingsFile { get { return Path.Combine(Root, "settings"); } }
        public string UpdateFile { get { return Path.Combine(Root, "update"); } }
        public string StateFile { get { return Path.Combine(Root, "connection-state"); } }
        /// <summary>사용자가 '끊기' 를 눌렀다는 표시 — 있으면 다음 실행 때 저절로 연결하지 않는다. '연결' 이 지운다.</summary>
        public string UserOffFile { get { return Path.Combine(Root, "user-disconnected"); } }
        /// <summary>하나만 실행의 둘째 겹 — 도는 동안 공유 없이 쥔다(관리자 전용 폴더라 보통 권한의 프로그램이 먼저 잡을 수 없다).</summary>
        public string InstanceFile { get { return Path.Combine(Root, "instance.lock"); } }
        public string LogDir { get { return Path.Combine(Root, "logs"); } }
        public string LogFile { get { return Path.Combine(LogDir, "webtether.log"); } }
        public string BinRoot { get { return Path.Combine(Root, "bin"); } }

        /// <summary>실제 판 — CommonApplicationData(환경 변수가 아니라 셸의 알려진 폴더 — 사용자가 바꾸지 못한다).</summary>
        public static AppPaths Default()
        {
            return new AppPaths { Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "WebTether") };
        }

        /// <summary>시험 판·시험 — 한 폴더 아래로.</summary>
        public static AppPaths Under(string home)
        {
            return new AppPaths { Root = home };
        }
    }
}
