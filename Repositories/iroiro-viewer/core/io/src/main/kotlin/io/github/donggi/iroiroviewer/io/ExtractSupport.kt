package io.github.donggi.iroiroviewer.io

/**
 * 아카이브 풀기를 파일 작업 큐에 잇는 **이음매**.
 *
 * ## 왜 인터페이스 한 겹이 필요한가
 *
 * 풀기는 사용자 저장소에 쓰는 작업이라 복사·이동과 **같은 큐**를 지나야 한다. 그러지
 * 않으면 같은 폴더를 두 작업이 동시에 만지고, 포그라운드 서비스·진행률·취소·결과
 * 보고를 전부 새로 만들게 된다. 그런데 큐는 [FileOpManager](`core:io`)에 있고,
 * 아카이브를 읽는 코드는 `format:archive` 에 있다. 의존 표는 `core:io` 가 `format:*` 을
 * 보는 것을 허용하지 않는다 — 허용하면 `core → format` 이 되어 계층이 뒤집힌다.
 *
 * 그래서 `core:io` 에 **포맷을 전혀 모르는 함수 하나짜리 인터페이스**를 두고,
 * `feature:archive` 가 구현을 만들고, `app` 이 시작할 때 꽂는다. 이 프로젝트가
 * 인터페이스를 늘리는 것을 꺼리는 것은 사실이지만(휴지통이 `core:io` 와 `core:data` 를
 * 가르지 않은 이유), 여기는 **정합성이 아니라 계층 방향**의 문제라 사정이 다르다.
 * 조립을 `app` 이 한다는 것은 모듈 지도가 이미 적어 둔 규칙이다.
 */
object ExtractSupport {

    /**
     * 실제로 푸는 것. `feature:archive` 가 구현한다.
     *
     * **`app` 이 꽂지 않으면 풀기 요청은 실패로 끝난다.** 조용히 아무 일도 안 하는 것보다
     * 실패가 낫다 — 사용자는 '풀었다' 고 생각하고 원본을 지울 수 있다.
     */
    fun interface Runner {
        suspend fun run(
            request: FileOpManager.Request.Extract,
            onProgress: (FileOpEngine.Progress) -> Unit,
        ): Result
    }

    data class Result(val outcome: FileOpEngine.Outcome, val report: ExtractReport)

    @Volatile
    var runner: Runner? = null
}

/**
 * 풀기가 **거부한 것**들의 집계.
 *
 * 거부를 조용히 삼키면 사용자는 아카이브 안의 파일 수와 푼 파일 수가 다른 것을 보고도
 * 이유를 알 수 없다. 그래서 종류별로 세고 표본 이름을 몇 개 싣는다.
 *
 * 표본 이름은 **화면이 자기 칸에** 그린다 — 우리 문장 안에 끼워 넣으면 아카이브가 적은
 * 문자열이 우리 문구인 척하게 된다.
 */
data class ExtractReport(
    /** 실제로 푼 곳. 사용자가 '폴더 열기' 를 누를 목적지다. */
    val destDir: String,
    /** 풀 수 없는 이름(경로 탈출·제어문자 등). */
    val refusedUnsafe: Int = 0,
    /** 심볼릭 링크·하드링크·정션. 내용이 파일이 아니라 경로 문자열이다. */
    val refusedLink: Int = 0,
    /** 암호가 걸린 항목. 이 뷰어는 복호화하지 않는다. */
    val refusedEncrypted: Int = 0,
    /** 목적지에 같은 이름의 **파일**이 있어 폴더를 만들지 못한 자리. */
    val blockedDirs: Int = 0,
    /** 아카이브 자신을 덮어쓸 뻔한 항목. */
    val refusedSelfOverwrite: Int = 0,
    /** 거부한 항목의 이름 표본. 최대 [MAX_SAMPLES] 개. */
    val samples: List<String> = emptyList(),
) {
    val refusedTotal: Int
        get() = refusedUnsafe + refusedLink + refusedEncrypted + blockedDirs + refusedSelfOverwrite

    companion object {
        const val MAX_SAMPLES = 5
    }
}
