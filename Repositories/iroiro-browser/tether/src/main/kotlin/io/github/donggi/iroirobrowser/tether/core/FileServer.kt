package io.github.donggi.iroirobrowser.tether.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wi-Fi Direct 의 손님이 브라우저로 폰의 폴더 하나([SharedTree])를 다루는 작은 HTTP 서버 — 시놀로지·QNAP 의 File Station 꼴의 화면
 * (앱의 `assets/tether/fileman.*`)과 그 API. [BASE_PORT](8080)부터 [PORT_ATTEMPTS] 개(8080…8089)를 차례로 해 보아 비어 있는 첫 포트에
 * 묶는다 — 모두 차 있을 때만 [StartError.PORT_TAKEN]. 실제 포트([actualPort])를 화면이 보이고, `Host` 검사는 포트를 가리지 않는다(아래 2).
 * **옮기는 것은 사용자 결정이다**([HttpProxyServer] 의 머리말). 위험은 남는다 — 이 폰의 다른 앱이 8080 을 먼저 쥐면 손님이 익숙한
 * 주소(…:8080)로 그 앱의 가짜 화면을 열고 올린 파일을 그 앱에 넘길 수 있다. 사용자가 감수했고, 화면의 '공유 폴더' 설명이 위 상태 줄의 실제
 * 포트를 쓰라고 적는다.
 *
 * ## 누가 닿는가
 *
 * 1. [PeerGate] 를 프록시와 **똑같이** 쓴다 — 핫스팟이 꺼져 있으면, 폰이 붙은 보통 Wi-Fi 에서는, 그리고 **폰 안의 다른 앱은** 닿지 못한다.
 *    마지막이 중요하다: 이 서버는 SAF 로 받은 폴더 권한을 HTTP 로 바꿔 주므로, 폰 안에 인증 없이 열려 있으면 다른 앱이 저장소 제한을 비켜 가는
 *    통로가 된다.
 * 2. **`Host` 머리가 [allowedHost](`192.168.49.1`, 포트는 있어도 없어도)가 아니면 421 로 거절한다.** 손님이 192.168.49.1 에 프록시
 *    없이 바로 닿는 구성(tun2proxy 의 `--setup`)에서는 손님 PC 의 브라우저가 연 **남의 웹 페이지**가
 *    DNS 재바인딩으로 이 서버를 같은 출처처럼 불러 공유 폴더를 읽고 쓸 수 있다고 보았다(추론 — 브라우저가 사설망 요청을 막으면 막힌다).
 *    재바인딩한 요청에는 공격자의 이름이 `Host` 로 실린다. 머리가 없는 요청(HTTP/1.0)도 거절한다 — 브라우저는 늘 싣는다.
 * 3. **바꾸는 요청은 PUT 과 `Content-Type: application/json` 몸의 POST 뿐이다** — 다른 사이트의 쪽이 사전 확인(OPTIONS) 없이 보낼 수 있는 꼴
 *    (폼 전송의 GET·POST, `text/plain` 의 POST)로는 아무것도 바뀌지 않는다. 이 서버는 OPTIONS 에 404 로 답하므로 다른 출처의 PUT·JSON POST
 *    는 사전 확인에서 막힌다. 덧붙여 바꾸는 요청에 `Origin` 이 실려 오면 이 서버의 출처여야 하고(`Sec-Fetch-Site` 도 같은 출처나 none) 아니면
 *    403. GET 은 아무것도 바꾸지 않는다.
 * 4. **관리 기능**(폴더 만들기·이름 바꾸기·옮기기·복사·지우기·휴지통·폴더째 올리기)은 폰의 테더 화면의 '관리 허용'(기본 끔)이 켜져 있을 때만 —
 *    **요청마다** [manageAllowed] 를 본다(끄면 다음 요청부터 403 과 까닭). 파일 올리기(같은 이름이면 덮어쓰기)는 예전부터의 기능이라 스위치와
 *    상관없다 — 다만 **덮어쓰기는 요청이 `overwrite=1` 로 밝혀야 하고**(없으면 같은 이름에 409 — 화면이 묻고 나서 싣는다, 사용자 결정) **덮인
 *    원래 파일은 휴지통으로 간다**(스위치가 꺼져 있어도 — 되살리기는 관리 허용이 있어야 한다). 원래 파일이 0바이트면 휴지통에 넣지 않고 바꾼다.
 *
 * 그 안에서는 **인증이 없다** — Wi-Fi Direct 의 WPA2 패스프레이즈가 유일한 통제선이라는 뜻이고 프록시와 같은 전제다(사용자 결정 — 그룹에
 * 붙인 기기만 닿는다). 그룹에 기기를 붙이는 순간 그 기기는 폴더를 읽고 **덮어쓸** 수 있다(관리 허용이면 지우기·옮기기까지).
 *
 * ## 답의 머리
 *
 * 모든 답에 `X-Content-Type-Options: nosniff`·`Referrer-Policy: no-referrer`·`Cross-Origin-Resource-Policy: same-origin`·`Connection: close`.
 * 화면(HTML·JS·CSS)은 **이 서버의 것만** 받는 CSP([UI_CSP] — 인라인 스크립트·스타일도 없다)와 `X-Frame-Options: DENY`. 그 밖(JSON·파일·
 * 섬네일·zip)은 [API_CSP](`sandbox` — 어쩌다 문서로 열려도 스크립트가 돌지 않는다). 파일 받기는 늘 `Content-Disposition: attachment` 다
 * — 브라우저로 여는 것은 PDF 하나(`inline=1`)이고, 화면의 미리 보기는 그림·영상·소리를 `<img>`·`<video>`·`<audio>` 로(그 요소는 attachment
 * 를 보지 않는다), 글을 앞부분만 받아 글로만 그린다. HTML·SVG 를 이 출처의 문서로 여는 길이 없다.
 *
 * ## API
 *
 * | 요청 | 하는 일 |
 * |---|---|
 * | `GET /` · `/fileman.js` · `/fileman.css` | 화면(앱의 자산 — [ui]) |
 * | `GET /api/info` | 폴더 이름·관리 허용·저장 공간 |
 * | `GET /api/list?path=…[&dirs=1]` | 폴더 목록(JSON — 휴지통은 감춘다, [MAX_LIST_ENTRIES] 에서 자른다) |
 * | `GET /api/file?path=…[&inline=1]` | 받기. `Range` 를 받는다 |
 * | `GET /api/thumb?path=…&size=…` | 섬네일([SharedTree.thumbnail] — 캐시 [ThumbnailCache], 동시에 짓기 [THUMB_CONCURRENCY]) |
 * | `PUT /api/file?path=…[&overwrite=1][&parents=1]` | 올리기 — 같은 이름(파일)은 `overwrite=1` 일 때만 다 받은 뒤 원래 것을 휴지통으로 옮기고 둔다(0바이트면 휴지통 없이 — [placeOverwriting]). `parents=1` 은 관리 |
 * | `POST /api/zip` → `GET /api/zip?id=…` | 여러 개·폴더를 zip 으로 흘려 받기([serveZipPlan]) |
 * | `PUT /api/folder?path=…` | 폴더 만들기(관리) |
 * | `POST /api/rename`·`/api/move`·`/api/copy`·`/api/delete` | 이름 바꾸기·옮기기·복사·휴지통으로(관리) |
 * | `GET /api/trash` · `POST /api/trash/restore`·`/api/trash/purge` | 휴지통 보기(보관 기간 `retentionDays` 와 함께)·되살리기·비우기(관리). 기간이 지난 것은 서버가 저절로 비운다([onStarted]) |
 *
 * 연결 하나에 요청 하나(`Connection: close`) — keep-alive 의 본문 경계를 틀릴 일이 없다. `HEAD`·`OPTIONS` 는 404.
 *
 * 상한: 동시 연결 [MAX_SESSIONS](브라우저는 호스트 하나에 6개 남짓만 연다), 연결마다 스레드 하나([ThreadBudget]) — zip·섬네일·복사도 그
 * 연결의 스레드에서 돈다(스레드를 더 띄우지 않는다). 연결 밖의 스레드는 휴지통을 손보는 것 하나뿐이다(하루 한 번 꼴 — [onStarted]).
 */
class FileServer(
    env: TetherEnvironment,
    private val tree: SharedTree,
    /** 화면 자산의 바이트 — 이름은 [UI_FILES] 의 것만 묻는다. 읽지 못하면 null(500). */
    private val ui: (String) -> ByteArray?,
    basePort: Int = BASE_PORT,
    attempts: Int = PORT_ATTEMPTS,
    /** 받는 `Host` — 이 이름에 `:포트` 가 붙거나 붙지 않은 것만(포트는 가리지 않는다 — 옮겨 선 포트로 와도 받는다). `Origin` 도 이 이름이어야 한다. */
    private val allowedHost: String = PeerFilter.GROUP_OWNER,
    /** 관리 허용 — **요청마다** 묻는다(폰의 스위치). */
    private val manageAllowed: () -> Boolean = { false },
    /** 섬네일 캐시. 없으면 늘 새로 짓는다. */
    private val thumbnails: ThumbnailCache? = null,
    /** 복사·zip 의 항목 수 상한([SharedOps.MAX_TREE_ENTRIES]) — 시험이 작게 준다. */
    maxTreeEntries: Int = SharedOps.MAX_TREE_ENTRIES,
    /** 휴지통 보관 기간(일, 0 은 비우지 않음) — 손볼 때마다 읽는다(폰의 설정 — [sweepTrash]). */
    private val trashRetentionDays: () -> Int = { SharedOps.DEFAULT_TRASH_DAYS },
    /** 휴지통을 손보는 때 — 서버가 선 뒤 첫 번([TRASH_SWEEP_FIRST_MS])과 그 뒤의 간격([TRASH_SWEEP_EVERY_MS]). 시험이 짧게 준다. */
    private val trashSweepFirstMs: Long = TRASH_SWEEP_FIRST_MS,
    private val trashSweepEveryMs: Long = TRASH_SWEEP_EVERY_MS,
    /** 휴지통 기록의 '지운 때' 를 짓고 견주는 벽시계(epoch ms — [TetherEnvironment.nowMs] 는 단조 시계라 날짜가 아니다). 시험이 돌린다. */
    wallClockMs: () -> Long = { System.currentTimeMillis() },
) : TcpServer(env, "file-server", basePort, attempts, maxSessions = MAX_SESSIONS, threadsPerSession = 1) {

    private val ops = SharedOps(tree, nowMs = wallClockMs, maxTreeEntries = maxTreeEntries)

    /** 휴지통을 손보는 스레드 하나(연결이 아니라 스레드 예산 밖 — 하루 한 번 꼴로 잠깐). [onStarted] 에 서고 [onStopped] 에 내린다. */
    private val sweepLock = Any()
    private var sweeper: ScheduledExecutorService? = null
    private var sweeperClosed = false

    /** 관리 동작은 하나씩 — 두 손님이 같은 것을 함께 옮기거나 지우는 틈을 두지 않는다. */
    private val mutation = ReentrantLock()

    /** 섬네일을 동시에 짓는 수 — 동영상의 장면 풀기는 무겁다. */
    private val thumbGate = Semaphore(THUMB_CONCURRENCY)

    /** zip 받기의 표 — 짧게 살고 한 번 쓴다([serveZipPlan]). */
    private val zipTickets = ConcurrentHashMap<String, ZipTicket>()
    private val activeZips = AtomicInteger()
    private val random = SecureRandom()

    private class ZipTicket(val items: List<SharedOps.ZipItem>, val fileName: String, val createdMs: Long)

    override fun handle(client: Socket) {
        client.tcpNoDelay = true
        // 머리를 다 받기까지의 전체 마감(DeadlineInputStream 의 머리말).
        val deadline = DeadlineInputStream(client, client.getInputStream(), env.timings.fileHeaderMs, env.nowMs)
        // 머리를 바이트 단위로 읽으므로 버퍼로 감싸고, 올리기의 본문이 이미 버퍼에 들어와 있을 수 있어 **이 스트림을 그대로** 본문 읽기에 넘긴다.
        val input = BufferedInputStream(deadline, HEADER_BUFFER_BYTES)
        val output = client.getOutputStream()
        try {
            val request = readRequest(input) ?: return
            // 머리를 다 받았다 — 본문(올리기)은 큰 파일이 오래 걸리므로 전체 마감 대신 읽기 하나의 시한만 둔다.
            deadline.finish()
            client.soTimeout = env.timings.fileBodyReadMs
            if (!isAllowedHost(request.header("host"), allowedHost)) {
                env.log.w(TAG, "Host 머리가 그룹 오너 주소가 아니라 거절했다")
                sendText(output, 421, WRONG_HOST_HELP)
                return
            }
            if (request.method != "GET" && !isSameOrigin(request)) {
                env.log.w(TAG, "다른 출처의 바꾸는 요청을 거절했다")
                sendJson(output, 403, Json.error("다른 사이트의 쪽이 보낸 요청은 받지 않습니다"))
                return
            }
            route(request, input, output)
        } catch (_: MalformedRequest) {
            env.log.w(TAG, "틀린 요청을 거절했다")
            sendStatus(output, 400)
        }
    }

    // ── 휴지통 손보기(보관 기간·.nomedia) ─────────────────────────────────────────────────────────────────────

    /**
     * 서버가 서면 휴지통을 손보는 차례를 건다 — [trashSweepFirstMs] 뒤에 한 번(테더링을 켤 때마다 — 이 서버는 테더링과 함께 서고 내린다), 그 뒤
     * [trashSweepEveryMs](하루)마다. 휴지통 API 를 부를 때 하지 않는 것은 휴지통을 열지 않으면 영영 비우지 않기 때문이고(보관 기간의 뜻은
     * 공간을 돌려받는 것), 더 자주 하지 않는 것은 손볼 때마다 휴지통의 목록과 지난 칸의 기록을 SAF 로 읽기 때문이다. 설정을 바꾸면 앱이
     * [requestTrashSweep] 으로 곧바로 한 번 더 부른다.
     */
    override fun onStarted() {
        synchronized(sweepLock) {
            if (sweeperClosed || sweeper != null) return
            val s = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "file-server-trash").apply { isDaemon = true } }
            s.scheduleWithFixedDelay({ sweepTrash() }, trashSweepFirstMs, trashSweepEveryMs, TimeUnit.MILLISECONDS)
            sweeper = s
        }
    }

    override fun onStopped() {
        synchronized(sweepLock) {
            sweeperClosed = true
            sweeper?.shutdownNow()
            sweeper = null
        }
    }

    /** 휴지통을 곧 한 번 손본다(설정을 바꿨을 때 — 손보는 스레드에서). 서지 않았거나 멈춘 서버면 아무것도 하지 않는다. */
    fun requestTrashSweep() {
        synchronized(sweepLock) {
            try {
                sweeper?.execute { sweepTrash() }
            } catch (_: RejectedExecutionException) {
                // 멈추는 중.
            }
        }
    }

    /**
     * 휴지통을 손본다 — [SharedOps.maintainTrash](`.nomedia` 더하기·보관 기간이 지난 칸 지우기). 관리 동작과 같은 차례를 쥔다(되살리기와 엇갈리지
     * 않게 — 차례가 오지 않으면 이번은 건너뛴다). **관리 허용과 상관없다**(보관 기간은 폰의 설정이다). 실패는 기록만 하고 던지지 않는다 —
     * 예약된 일이 던지면 다음 차례가 사라진다. 지운 칸 수(손보지 못했으면 -1).
     */
    internal fun sweepTrash(): Int {
        try {
            if (!tree.isAvailable()) return -1
            if (!mutation.tryLock(MUTATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
                env.log.w(TAG, "휴지통 손보기 — 다른 관리 작업이 길어 이번은 건너뛴다")
                return -1
            }
            try {
                val result = ops.maintainTrash(trashRetentionDays())
                if (result.purged > 0 || result.failed > 0) {
                    env.log.i(TAG, "휴지통 보관 기간이 지난 것을 비웠다: ${result.purged}개" + if (result.failed > 0) ", 못 지운 것 ${result.failed}개" else "")
                }
                return result.purged
            } finally {
                mutation.unlock()
            }
        } catch (t: Throwable) {
            env.log.w(TAG, "휴지통 손보기 실패: ${t.javaClass.simpleName}")
            return -1
        }
    }

    // ── 요청 읽기 ─────────────────────────────────────────────────────────────────────────────────────────

    /** 읽어 낸 요청 하나. 이 서버는 연결마다 하나만 처리하고 닫는다. */
    private class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
    ) {
        /** `?` 앞. */
        val path: String get() = target.substringBefore('?')

        /** 질의의 `path` 를 퍼센트 부호를 풀어 돌려준다. 없으면 빈 글(맨 위). */
        val requestedPath: String get() = query("path") ?: ""

        fun header(name: String): String? = headers[name.lowercase()]

        fun query(name: String): String? {
            val query = target.substringAfter('?', "")
            if (query.isEmpty()) return null
            for (pair in query.split('&')) {
                val key = pair.substringBefore('=')
                if (key != name) continue
                val raw = pair.substringAfter('=', "")
                return try {
                    // UTF-8 퍼센트 풀기(한글 이름).
                    URLDecoder.decode(raw, Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }

        fun flag(name: String): Boolean = query(name) == "1"
    }

    /** 요청 문법 오류 — 400. */
    private class MalformedRequest : Exception()

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 3) throw MalformedRequest()

        val headers = HashMap<String, String>()
        var lines = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            // 줄을 센다 — 담은 머리 수(이름이 겹치면 덮는다)로 세면 `:` 없는 줄이나 같은 이름이 끝없이 이어져도 걸리지 않는다.
            if (++lines > MAX_HEADER_COUNT) throw MalformedRequest()
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            // Host·Origin·Content-Type·Content-Length 가 둘이면 어느 것을 볼지 갈린다 — 거절한다(HttpProxyServer 의 스머글링 막기와 같은 판단).
            if (name in SINGLE_HEADERS && headers.containsKey(name)) throw MalformedRequest()
            headers[name] = line.substring(separator + 1).trim()
        }
        return Request(parts[0].uppercase(), parts[1], headers)
    }

    /** CRLF 로 끝나는 한 줄. 홀로 선 CR 은 거절한다(HttpProxyServer.readHeaderLine 과 같은 판단 — 머리 주입). */
    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            if (builder.length >= MAX_HEADER_LINE_BYTES) throw MalformedRequest()
            val b = input.read()
            if (b == -1) return if (builder.isEmpty()) null else builder.toString()
            when (b) {
                '\r'.code -> {
                    val next = input.read()
                    if (next == '\n'.code || next == -1) return builder.toString()
                    throw MalformedRequest()
                }
                '\n'.code -> return builder.toString()
                else -> builder.append(b.toChar())
            }
        }
    }

    /**
     * 바꾸는 요청(GET 이 아닌 것)의 출처 — `Origin` 이 있으면 `http://<allowedHost>[:포트]` 여야 하고, `Sec-Fetch-Site` 가 있으면 `same-origin`
     * 이나 `none` 이어야 한다. 둘 다 없으면(curl 등 — 브라우저는 바꾸는 요청에 Origin 을 싣는다) 받는다. 사전 확인을 부르는 꼴만 받는 것(머리말 3)
     * 위에 한 겹 더.
     */
    private fun isSameOrigin(request: Request): Boolean {
        request.header("origin")?.let { origin ->
            if (!origin.startsWith("http://") || !isAllowedHost(origin.removePrefix("http://"), allowedHost)) return false
        }
        request.header("sec-fetch-site")?.let { site ->
            if (site != "same-origin" && site != "none") return false
        }
        return true
    }

    // ── 길 고르기 ─────────────────────────────────────────────────────────────────────────────────────────

    private fun route(request: Request, input: InputStream, output: OutputStream) {
        val m = request.method
        val p = request.path
        try {
            when {
                m == "GET" && p in UI_FILES -> serveUi(output, UI_FILES.getValue(p))
                m == "GET" && p == "/api/info" -> serveInfo(output)
                m == "GET" && p == PATH_LIST -> serveList(request, output)
                m == "GET" && p == PATH_FILE -> serveDownload(request, output)
                m == "GET" && p == "/api/thumb" -> serveThumb(request, output)
                m == "PUT" && p == PATH_FILE -> serveUpload(request, input, output)
                m == "POST" && p == PATH_ZIP -> serveZipPlan(request, input, output)
                m == "GET" && p == PATH_ZIP -> serveZip(request, output)
                m == "PUT" && p == "/api/folder" -> managed(output) { serveMakeFolder(request, output) }
                m == "POST" && p == "/api/rename" -> managedJson(request, input, output) { serveRename(it, output) }
                m == "POST" && p == "/api/move" -> managedJson(request, input, output) { serveTransfer(it, copy = false, output) }
                m == "POST" && p == "/api/copy" -> managedJson(request, input, output) { serveTransfer(it, copy = true, output) }
                m == "POST" && p == "/api/delete" -> managedJson(request, input, output) { serveTrash(it, output) }
                m == "GET" && p == "/api/trash" -> managed(output) { serveTrashList(output) }
                m == "POST" && p == "/api/trash/restore" -> managedJson(request, input, output) { serveRestore(it, output) }
                m == "POST" && p == "/api/trash/purge" -> managedJson(request, input, output) { servePurge(it, output) }
                // HEAD 는 받지 않는다 — 화면이 쓰지 않고, 받으려면 본문 없이 머리만 보내는 갈래가 받기 전체에 퍼진다. OPTIONS 도(머리말 3).
                else -> sendStatus(output, 404)
            }
        } catch (e: OpFailure) {
            sendJson(output, e.status, Json.error(e.message ?: "실패했습니다"))
        } catch (e: SharedOps.ConflictFailure) {
            sendJson(output, 409, Json.obj("error" to "같은 이름이 이미 있습니다", "conflicts" to e.names))
        }
    }

    /**
     * 관리 기능의 문 — 스위치를 **이 요청에서** 보고(꺼져 있으면 403 과 까닭), 공유 폴더가 있는지 보고, 관리 동작을 하나씩만 돌린다(30초 안에
     * 차례가 오지 않으면 503).
     */
    private fun managed(output: OutputStream, body: () -> Unit) {
        if (!manageAllowed()) {
            sendJson(output, 403, Json.obj("error" to MANAGE_OFF, "manageOff" to true))
            return
        }
        if (!tree.isAvailable()) {
            sendJson(output, 503, Json.folderMissing())
            return
        }
        if (!mutation.tryLock(MUTATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
            sendJson(output, 503, Json.error("다른 관리 작업이 오래 걸립니다 — 잠시 뒤에 다시 하세요"))
            return
        }
        try {
            body()
        } catch (e: OpFailure) {
            throw e
        } catch (e: SharedOps.ConflictFailure) {
            throw e
        } catch (e: MalformedRequest) {
            throw e
        } catch (e: Exception) {
            env.log.w(TAG, "관리 동작 실패: ${e.javaClass.simpleName}")
            throw OpFailure(500, "폰의 저장소가 거절했습니다")
        } finally {
            mutation.unlock()
        }
    }

    /**
     * JSON 몸을 받는 관리 기능 — 스위치를 먼저 보고(꺼져 있으면 몸을 읽지 않고 403), 몸을 **잠금 밖에서** 다 읽은 뒤에 [managed] 로 들어간다.
     * 몸 읽기는 손님의 망 속도에 묶여 있다(읽기 하나에 [TetherTimings.fileBodyReadMs] — 512 KiB 를 한 바이트씩 흘리면 몇 달) — 잠금 안에서
     * 읽으면 손님 하나가 몸을 흘리는 동안 다른 모든 관리 동작이 503 이고 폴더째 올리기가 멈춘다(2026-11-21 검토에서 고침).
     */
    private fun managedJson(request: Request, input: InputStream, output: OutputStream, action: (Map<String, Any?>) -> Unit) {
        if (!manageAllowed()) {
            sendJson(output, 403, Json.obj("error" to MANAGE_OFF, "manageOff" to true))
            return
        }
        val body = readJson(request, input)
        managed(output) { action(body) }
    }

    /**
     * 관리 기능의 JSON 몸 — `Content-Type: application/json`(매개변수는 받는다)이 아니면 415(다른 사이트의 폼·`text/plain` POST 가 여기 닿지
     * 않게), `Transfer-Encoding` 은 501, 길이가 없으면 411, [MAX_JSON_BYTES] 를 넘으면 413, 객체가 아니면 400.
     */
    @Suppress("UNCHECKED_CAST")
    private fun readJson(request: Request, input: InputStream): Map<String, Any?> {
        val type = request.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
        if (type != "application/json") throw OpFailure(415, "JSON 몸(Content-Type: application/json)만 받습니다")
        if (request.header("transfer-encoding") != null) throw OpFailure(501, "chunked 는 받지 않습니다")
        val length = request.header("content-length")?.toLongOrNull() ?: throw OpFailure(411, "Content-Length 가 필요합니다")
        if (length < 0) throw OpFailure(411, "Content-Length 가 틀렸습니다")
        if (length > MAX_JSON_BYTES) throw OpFailure(413, "요청이 너무 큽니다")
        val bytes = SharedOps.readUpTo(input, length.toInt())
        if (bytes.size.toLong() != length) throw OpFailure(400, "본문 길이가 선언과 다릅니다")
        val parsed = try {
            JsonReader.parse(String(bytes, Charsets.UTF_8))
        } catch (e: JsonReader.Malformed) {
            throw OpFailure(400, "JSON 을 읽을 수 없습니다")
        }
        return parsed as? Map<String, Any?> ?: throw OpFailure(400, "JSON 객체가 아닙니다")
    }

    private fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: throw OpFailure(400, "‘$key’ 가 없습니다")

    private fun Map<String, Any?>.texts(key: String): List<String> {
        val list = this[key] as? List<*> ?: throw OpFailure(400, "‘$key’ 가 없습니다")
        return list.map { it as? String ?: throw OpFailure(400, "‘$key’ 에 글이 아닌 것이 있습니다") }
    }

    /** 보통의 API 가 받는 경로 — 틀리면 400, 휴지통이면 없는 것(404). */
    private fun publicPath(path: String): List<String> {
        val segments = SharedPaths.split(path) ?: throw OpFailure(400, "경로가 틀렸습니다")
        if (SharedPaths.isHidden(segments)) throw OpFailure(404, "찾을 수 없습니다")
        return segments
    }

    // ── 화면·정보·목록 ─────────────────────────────────────────────────────────────────────────────────────

    private fun serveUi(output: OutputStream, file: Pair<String, String>) {
        val body = try {
            ui(file.first)
        } catch (e: Exception) {
            null
        }
        if (body == null) {
            env.log.e(TAG, "화면 파일을 읽지 못했다")
            sendStatus(output, 500)
            return
        }
        sendHeaders(
            output, 200,
            listOf(
                "Content-Type" to file.second,
                "Content-Length" to body.size.toString(),
                // 화면은 앱의 판과 함께 바뀐다 — 캐시하지 않는다.
                "Cache-Control" to "no-store",
                "X-Frame-Options" to "DENY",
            ),
            csp = UI_CSP,
        )
        output.write(body)
        output.flush()
    }

    private fun serveInfo(output: OutputStream) {
        val available = tree.isAvailable()
        val storage = if (available) runCatching { tree.storage() }.getOrNull() else null
        sendJson(
            output, 200,
            Json.obj(
                "available" to available,
                "root" to (if (available) tree.rootName() ?: "" else ""),
                "manage" to manageAllowed(),
                "free" to storage?.free,
                "total" to storage?.total,
                "limits" to mapOf("batch" to SharedOps.MAX_BATCH, "zipEntries" to SharedOps.MAX_TREE_ENTRIES, "zips" to MAX_CONCURRENT_ZIPS),
            ),
        )
    }

    private fun serveList(request: Request, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendJson(output, 503, Json.folderMissing())
            return
        }
        val path = request.requestedPath
        val segments = SharedPaths.split(path)?.takeUnless { SharedPaths.isHidden(it) }
        val target = segments?.let { tree.find(it) }
        if (segments == null || target == null || !target.isDirectory) {
            sendJson(output, 404, Json.error("폴더를 찾을 수 없습니다"))
            return
        }
        val children = try {
            ops.visibleChildren(target, atRoot = segments.isEmpty())
        } catch (e: Exception) {
            env.log.w(TAG, "목록을 읽지 못했다: ${e.javaClass.simpleName}")
            sendJson(output, 500, Json.error("목록을 읽을 수 없습니다"))
            return
        }
        val shown = if (request.flag("dirs")) children.filter { it.isDirectory } else children
        val truncated = shown.size > MAX_LIST_ENTRIES
        sendJson(output, 200, Json.listing(path, tree.rootName(), if (truncated) shown.take(MAX_LIST_ENTRIES) else shown, truncated))
    }

    // ── 받기·섬네일 ────────────────────────────────────────────────────────────────────────────────────────

    /** 파일을 보낸다. `Range: bytes=start-end` 를 받는다 — 동영상 찾기와 끊긴 받기 잇기가 여기에 달렸다. */
    private fun serveDownload(request: Request, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendStatus(output, 503)
            return
        }
        val target = SharedPaths.split(request.requestedPath)?.takeUnless { SharedPaths.isHidden(it) }?.let { tree.find(it) }
        if (target == null || target.isDirectory) {
            sendStatus(output, 404)
            return
        }
        val length = target.size
        val name = target.name.ifEmpty { "download" }
        val mime = SharedPaths.guessMimeType(name)
        // 브라우저로 여는 것은 PDF 하나 — 나머지는 늘 받기다(HTML·SVG 를 이 출처의 문서로 열지 않는다, 머리말 '답의 머리').
        val inline = request.flag("inline") && mime == "application/pdf"

        val rangeHeader = request.header("range")
        val range = ByteRange.parse(rangeHeader, length)
        if (range == null && rangeHeader != null) {
            sendHeaders(output, 416, listOf("Content-Range" to "bytes */$length", "Content-Length" to "0"))
            output.flush()
            return
        }

        val stream = tree.openRead(target)
        if (stream == null) {
            sendStatus(output, 500)
            return
        }
        stream.use { source ->
            val effective = range ?: ByteRange(0, if (length > 0) length - 1 else 0)
            val count = if (length == 0L) 0L else effective.end - effective.start + 1

            val headers = mutableListOf(
                "Content-Type" to mime,
                "Content-Length" to count.toString(),
                "Accept-Ranges" to "bytes",
                // filename* 는 RFC 6266 — 한글 이름이 깨지지 않으려면 이 꼴이어야 한다.
                "Content-Disposition" to "${if (inline) "inline" else "attachment"}; filename*=UTF-8''${SharedPaths.percentEncode(name)}",
                "Cache-Control" to "no-store",
            )
            // PDF 를 브라우저로 열 때는 sandbox 를 걸지 않는다 — 크로미움의 PDF 보기가 sandbox 문서에서 막힌다. PDF 안의 스크립트는 브라우저의
            // 보기가 이 출처 밖에서 다룬다.
            val csp = if (inline) PDF_CSP else API_CSP
            if (range != null) {
                headers += "Content-Range" to "bytes ${range.start}-${range.end}/$length"
                sendHeaders(output, 206, headers, csp)
            } else {
                sendHeaders(output, 200, headers, csp)
            }

            if (effective.start > 0) skipFully(source, effective.start)
            copy(source, output, count) { env.onBytesOut(it) }
            output.flush()
        }
    }

    /** 섬네일 — 캐시에 있으면 그것, 없으면 짓는다(동시에 [THUMB_CONCURRENCY] 개). 없거나 모르는 형식이면 404(화면은 아이콘을 그린다). */
    private fun serveThumb(request: Request, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendStatus(output, 503)
            return
        }
        val target = SharedPaths.split(request.requestedPath)?.takeUnless { SharedPaths.isHidden(it) }?.let { tree.find(it) }
        if (target == null || target.isDirectory) {
            sendStatus(output, 404)
            return
        }
        val size = Thumbnails.clampSize(request.query("size")?.toIntOrNull())
        val key = thumbnails?.key(target, size)
        var bytes = key?.let { thumbnails?.get(it) }
        if (bytes == null) {
            if (!thumbGate.tryAcquire(THUMB_WAIT_SECONDS, TimeUnit.SECONDS)) {
                sendStatus(output, 503)
                return
            }
            bytes = try {
                tree.thumbnail(target, size)
            } catch (e: Exception) {
                env.log.v(TAG, "섬네일을 짓지 못했다: ${e.javaClass.simpleName}")
                null
            } finally {
                thumbGate.release()
            }
            if (bytes != null && bytes.size <= Thumbnails.MAX_BYTES && Thumbnails.sniff(bytes) != null && key != null) thumbnails?.put(key, bytes)
        }
        val mime = bytes?.let { Thumbnails.sniff(it) }
        if (bytes == null || mime == null || bytes.size > Thumbnails.MAX_BYTES) {
            sendStatus(output, 404)
            return
        }
        sendHeaders(
            output, 200,
            listOf(
                "Content-Type" to mime,
                "Content-Length" to bytes.size.toString(),
                // 화면은 주소에 바뀐 때를 싣는다(`&v=`) — 바뀌면 주소가 달라지므로 오래 둬도 된다.
                "Cache-Control" to "private, max-age=604800",
            ),
        )
        output.write(bytes)
        output.flush()
        env.onBytesOut(bytes.size.toLong())
    }

    // ── 올리기 ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * 올리기 — 본문 전체가 파일 내용이 된다. `Content-Length` 를 요구한다(chunked 는 받지 않는다 — 브라우저의 `XMLHttpRequest.send(File)` 은
     * 늘 길이를 붙이고, chunked 를 받으려면 풀개가 필요해 범위를 넘는다). `Transfer-Encoding` 이 있으면 `Content-Length` 와 함께든 아니든 501
     * 이다(길이부터 보면 TE 만 올 때 411 이 된다). 흘려서 옮긴다 — 본문을 메모리에 모으면 큰 파일에서 곧바로 OOM 이다.
     *
     * **임시 이름으로 받고 다 받은 뒤에 바꾼다**([SharedTree] 의 머리말). 끊기거나(손님이 끊음·읽기 시한) 길이가 선언과 다르거나
     * 쓰기를 열지 못하면 임시만 지운다 — 원래 파일은 그대로다.
     *
     * **덮어쓰기는 `overwrite=1` 일 때만이다**(사용자 결정 — 같은 이름이면 화면이 한 번 묻는다). 없거나 `0` 이면 같은 이름(대소문자 무시)이 있을
     * 때 받기 전에 409 `exists`, 받는 동안 누가 그 이름을 만들었으면 두기 직전에 다시 보고 받은 것을 버리고 409 — 묻지 않은 요청(옛 화면·curl·
     * 물은 뒤의 틈)이 조용히 덮지 않는다(두기 자체도 같은 이름을 지우지 않는다 — [SharedTree.commitUpload]). 다른 값은 400. **같은 이름이
     * 폴더면 `overwrite=1` 이어도 409**(`folder: true` — 한 폴더에 같은 이름의 파일과 폴더는 둘 수 없고, 파일로 폴더를 덮지 않는다). 덮을 때는
     * 원래 파일을 **휴지통으로 옮긴 뒤에** 둔다([placeOverwriting] — 옮기지 못하면 덮지 않는다). `parents=1` 은 없는 폴더를 만들며 올린다(폴더째
     * 올리기 — 관리 허용일 때만, 아니면 403).
     *
     * 파일 이름(과 `parents=1` 로 새로 만드는 폴더 성분)은 **보이지 않는 글자를 뺀 이름**으로 쓴다([SharedOps.newName] — 사용자 결정 11-21).
     * 답의 `name` 이 실제로 둔 이름이다.
     */
    private fun serveUpload(request: Request, input: InputStream, output: OutputStream) {
        if (!tree.isAvailable()) {
            sendJson(output, 503, Json.folderMissing())
            return
        }
        if (request.header("transfer-encoding") != null) {
            sendJson(output, 501, Json.error("chunked 올리기는 받지 않습니다 — Content-Length 로 보내세요"))
            return
        }
        val declared = request.header("content-length")?.toLongOrNull()
        if (declared == null || declared < 0) {
            sendJson(output, 411, Json.error("Content-Length 가 필요합니다"))
            return
        }

        val requested = SharedPaths.split(request.requestedPath)?.takeIf { it.isNotEmpty() && !SharedPaths.isHidden(it) }
        if (requested == null) {
            sendJson(output, 400, Json.error("올릴 경로가 틀렸습니다"))
            return
        }
        // 파일 이름은 새 이름이다 — 보이지 않는 글자를 빼고 쓴다(같은 이름 검사도 뺀 뒤의 이름으로). 폴더 성분은 이미 있는 폴더의 이름이라 그대로
        // (parents=1 이면 없는 성분만 다듬어 만든다 — SharedOps.makeDirectories).
        val name = try {
            ops.newName(requested.last(), atRoot = requested.size == 1)
        } catch (e: OpFailure) {
            sendJson(output, 400, Json.error(e.message ?: "올릴 이름이 틀렸습니다"))
            return
        }
        var segments = requested.dropLast(1) + name
        val overwrite = when (request.query("overwrite")) {
            "1" -> true
            null, "0" -> false
            else -> {
                sendJson(output, 400, Json.error("overwrite 는 0 이나 1 입니다"))
                return
            }
        }
        if (request.flag("parents")) {
            if (!manageAllowed()) {
                sendJson(output, 403, Json.obj("error" to MANAGE_OFF, "manageOff" to true))
                return
            }
            try {
                // 다른 관리 동작과 같은 차례 — 기다림에 끝이 있다([managed] 와 같은 시한. 끝없이 기다리면 연결이 쌓여 서버의 상한을 채운다).
                if (!mutation.tryLock(MUTATION_WAIT_SECONDS, TimeUnit.SECONDS)) throw OpFailure(503, "다른 관리 작업이 오래 걸립니다 — 잠시 뒤에 다시 하세요")
                try {
                    segments = ops.makeDirectories(segments.dropLast(1)).names + name
                } finally {
                    mutation.unlock()
                }
            } catch (e: OpFailure) {
                sendJson(output, e.status, Json.error(e.message ?: "폴더를 만들지 못했습니다"))
                return
            } catch (e: Exception) {
                env.log.w(TAG, "올릴 폴더를 만들지 못했다: ${e.javaClass.simpleName}")
                sendJson(output, 500, Json.error("올릴 폴더를 만들지 못했습니다"))
                return
            }
        }
        // 같은 이름 — 덮어쓰기로 밝히지 않았거나 그것이 폴더면(파일로 덮을 수 없다) 받기 전에 409.
        val before = try {
            sameName(segments)
        } catch (e: Exception) {
            env.log.w(TAG, "올릴 자리를 보지 못했다: ${e.javaClass.simpleName}")
            sendJson(output, 500, Json.error("올릴 자리를 보지 못했습니다"))
            return
        }
        before?.let { existing ->
            if (!overwrite || existing.isDirectory) {
                sendExists(output, existing)
                return
            }
        }
        val temp = try {
            tree.createUploadTemp(segments)
        } catch (e: Exception) {
            env.log.w(TAG, "올릴 자리를 만들지 못했다: ${e.javaClass.simpleName}")
            sendJson(output, 500, Json.error("올릴 자리를 만들지 못했습니다"))
            return
        }
        if (temp == null) {
            sendJson(output, 400, Json.error("올릴 경로가 틀렸습니다"))
            return
        }
        val sink = tree.openWrite(temp)
        if (sink == null) {
            // 빈 임시 파일을 남기지 않는다.
            deleteQuietly(temp)
            sendJson(output, 500, Json.error("파일을 열 수 없습니다"))
            return
        }

        val written = try {
            sink.use { copy(input, it, declared) { bytes -> env.onBytesIn(bytes) } }
        } catch (e: Exception) {
            env.log.w(TAG, "올리기 실패: ${e.javaClass.simpleName}")
            // 끊긴 것은 임시뿐이다 — 원래 파일은 그대로다. 손님이 다시 올리면 된다.
            deleteQuietly(temp)
            sendJson(output, 500, Json.error("올리기가 끊겼습니다 — 원래 파일은 그대로입니다"))
            return
        }
        if (written != declared) {
            deleteQuietly(temp)
            sendJson(output, 400, Json.error("본문 길이가 선언과 다릅니다 — 원래 파일은 그대로입니다"))
            return
        }
        if (overwrite) {
            placeOverwriting(temp, segments, written, output)
            return
        }
        // 받는 동안 다른 손님이 같은 이름을 만들었을 수 있다 — 두기 직전에 다시 보고 받은 것을 버린다(조용히 덮지 않게). 두기 자체도 같은 이름을
        // 지우지 않는다([SharedTree.commitUpload]) — 그 사이의 틈에 생긴 것도 덮지 않는다.
        val takenNow = try {
            sameName(segments)
        } catch (e: Exception) {
            deleteQuietly(temp)
            sendJson(output, 500, Json.error("올릴 자리를 보지 못했습니다"))
            return
        }
        if (takenNow != null) {
            deleteQuietly(temp)
            sendExists(output, takenNow)
            return
        }
        val placed = commit(temp, segments)
        if (placed == null) {
            val raced = runCatching { sameName(segments) }.getOrNull()
            if (raced != null) sendExists(output, raced) else sendJson(output, 500, Json.error("받은 파일을 제자리에 두지 못했습니다"))
            return
        }
        sendJson(output, 200, Json.uploaded(placed.name, written))
    }

    /**
     * 덮어쓰기(`overwrite=1`)의 두기 — 다 받은 임시 [temp] 를 두기 전에 **같은 이름의 원래 파일을 휴지통으로 옮긴다**(사용자 결정 11-21 — 관리
     * 허용과 상관없이, 지우기와 같은 칸·기록이라 되살릴 수 있다. 휴지통 보기·되살리기는 관리 허용이 필요하다). 휴지통으로 옮기지 못하면 **덮어쓰지
     * 않고** 받은 임시를 지운다(원래 파일은 그대로). 원래 것이 폴더면 409(파일로 폴더를 덮지 않는다). 휴지통과 엇갈리지 않게 관리 동작과 같은
     * 차례를 쥔다(30초 안에 오지 않으면 503 — 원래 파일은 그대로). 옮긴 뒤에 두지 못하면 원래 것을 휴지통에서 되살려 본다.
     *
     * **원래 파일이 0바이트면 휴지통에 넣지 않는다**(사용자 결정 11-21 — 덮어쓰기만, 관리의 지우기는 그대로 휴지통). 판정은 잠금 안에서 방금 읽은
     * 실제 항목으로([SharedOps.isEmptyFile] — 크기 0 과 열어 읽은 바이트 없음). 두기는 같은 이름을 지우지 않으므로 원래 것을 숨은 이름으로
     * 비켜 두고([SharedOps.setAside]) 둔 뒤에 지운다([SharedOps.discardAside] — 지우기 직전에 다시 보고, 그새 커졌으면 원래 이름으로 휴지통에 —
     * 답의 `trashed` 가 true). 두기가 실패하면 비켜 둔 것을 원래 이름으로 돌린다(그새 생긴 같은 이름이 있으면 `이름 (1)`). 비켜 두지 못하면 휴지통
     * 길로 간다. 남는 틈: 다시 본 뒤 지우기까지 사이에 다른 앱이 쓰는 것은 막지 못한다(지우기는 조건부가 아니다).
     */
    private fun placeOverwriting(temp: SharedEntry, segments: List<String>, written: Long, output: OutputStream) {
        if (!mutation.tryLock(MUTATION_WAIT_SECONDS, TimeUnit.SECONDS)) {
            deleteQuietly(temp)
            sendJson(output, 503, Json.error("다른 관리 작업이 오래 걸립니다 — 원래 파일은 그대로입니다. 잠시 뒤에 다시 하세요"))
            return
        }
        try {
            val existing = try {
                sameName(segments)
            } catch (e: Exception) {
                deleteQuietly(temp)
                sendJson(output, 500, Json.error("올릴 자리를 보지 못했습니다 — 원래 파일은 그대로입니다"))
                return
            }
            if (existing != null && existing.isDirectory) {
                deleteQuietly(temp)
                sendExists(output, existing)
                return
            }
            val parentSegments = segments.dropLast(1)
            // 0바이트 원래 파일은 휴지통에 넣지 않는다(사용자 결정 11-21) — 숨은 이름으로 비켜 두고 둔 뒤에 지운다(지우기 직전에 다시 보고 그새
            // 커졌으면 휴지통으로). 비켜 두지 못하면 휴지통 길로 — 원래 것은 그대로다.
            val aside = if (existing == null || !ops.isEmptyFile(existing)) null else try {
                ops.setAside(existing)
            } catch (e: Exception) {
                env.log.w(TAG, "빈 원래 파일을 비켜 두지 못했다 — 휴지통 길로: ${e.javaClass.simpleName}")
                null
            }
            val trashedId = if (existing == null || aside != null) null else try {
                ops.trashReplaced(parentSegments, existing)
            } catch (e: Exception) {
                env.log.w(TAG, "덮을 원래 파일을 휴지통으로 옮기지 못했다: ${e.javaClass.simpleName}")
                deleteQuietly(temp)
                // '그대로' 는 원래 것이 정말 제자리에 있을 때만 — 옮기기가 된 뒤에 실패를 알렸거나 확인하지 못한 꼴이 있다(SharedOps.trashOne).
                val where = when (stillInPlace(segments, existing)) {
                    true -> "원래 파일은 그대로입니다"
                    false -> "원래 파일이 제자리에 없습니다 — 휴지통을 확인하세요"
                    null -> "원래 파일이 그대로인지 확인하지 못했습니다 — 휴지통을 확인하세요"
                }
                sendJson(output, 500, Json.error("원래 파일을 휴지통으로 옮기지 못해 덮어쓰지 않았습니다 — $where"))
                return
            }
            val placed = commit(temp, segments)
            if (placed == null && aside != null) {
                // 비켜 둔 빈 원래 것을 제자리로(그새 같은 이름이 생겼으면 `이름 (1)` — 그때는 그 이름을 알린다).
                val putBack = ops.putBack(parentSegments, aside)
                val where = when (putBack) {
                    (parentSegments + aside.name).joinToString("/") -> "원래 파일은 그대로입니다"
                    null -> "원래 파일(빈 파일)을 제자리로 돌리지 못했습니다"
                    else -> "원래 파일은 ‘$putBack’ 로 되살렸습니다"
                }
                sendJson(output, 500, Json.error("받은 파일을 제자리에 두지 못했습니다 — $where"))
                return
            }
            if (placed == null) {
                // 원래 것은 휴지통에 있다 — 제자리로 되살려 본다(같은 이름이 그새 생겼으면 `이름 (1)` 로 — 그때는 그 이름을 알린다. '그대로' 라
                // 답하면 손님은 원래 것이 덮인 줄 안다 — 2026-11-21 3차 검토).
                val restoredTo = if (trashedId == null) null else runCatching { ops.restore(listOf(trashedId)).done.singleOrNull()?.second }.getOrNull()
                val where = when {
                    trashedId == null || restoredTo == segments.joinToString("/") -> "원래 파일은 그대로입니다"
                    restoredTo != null -> "원래 파일은 ‘$restoredTo’ 로 되살렸습니다"
                    else -> "원래 파일은 휴지통에 있습니다"
                }
                sendJson(output, 500, Json.error("받은 파일을 제자리에 두지 못했습니다 — $where"))
                return
            }
            val asideTrashedId = if (aside == null) null else try {
                ops.discardAside(parentSegments, aside)
            } catch (e: Exception) {
                // 새 것은 제자리에 있다 — 비켜 둔 것은 숨은 이름으로 남는다(내용을 잃지 않는 쪽). 기록만 한다.
                env.log.w(TAG, "비켜 둔 원래 파일을 치우지 못했다: ${e.javaClass.simpleName}")
                null
            }
            sendJson(output, 200, Json.uploaded(placed.name, written, trashed = (trashedId ?: asideTrashedId) != null))
        } finally {
            mutation.unlock()
        }
    }

    /** 다 받은 임시를 제자리에 둔다. 못 했으면 null — 어디서 실패했는지 모른다(임시가 받은 내용을 쥐고 있을 수 있어 여기서 지우지 않는다). */
    private fun commit(temp: SharedEntry, segments: List<String>): SharedEntry? = try {
        tree.commitUpload(temp, segments)
    } catch (e: Exception) {
        env.log.w(TAG, "올린 파일을 제자리에 두지 못했다: ${e.javaClass.simpleName}")
        null
    }

    /**
     * [segments] 의 부모 폴더에서 같은 이름(대소문자 무시 — 파일이든 폴더든, 이름 그대로인 것이 먼저)의 항목. 없거나 부모가 없으면 null(부모가
     * 없으면 올리기가 400 으로 답한다).
     */
    private fun sameName(segments: List<String>): SharedEntry? {
        val parent = tree.find(segments.dropLast(1))?.takeIf { it.isDirectory } ?: return null
        val children = tree.list(parent)
        val name = segments.last()
        return children.firstOrNull { it.name == name } ?: children.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /** [before] 가 아직 그 자리에 그대로인가(같은 항목·같은 크기의 파일). 읽지 못하면 null. */
    private fun stillInPlace(segments: List<String>, before: SharedEntry): Boolean? = try {
        val now = sameName(segments)
        now != null && !now.isDirectory && now.ref == before.ref && now.name == before.name && now.size == before.size
    } catch (_: Exception) {
        null
    }

    /** 같은 이름이 있어 두지 않았다 — 409 `exists`, 폴더면 `folder` 와 그 까닭(화면은 '덮어쓰기' 를 고를 수 없게 한다). */
    private fun sendExists(output: OutputStream, existing: SharedEntry) {
        val message = if (existing.isDirectory) "${SharedOps.FOLDER_SAME_NAME} — 파일로 덮어쓸 수 없습니다" else OVERWRITE_EXISTS
        sendJson(output, 409, Json.obj("error" to message, "exists" to true, "folder" to existing.isDirectory))
    }

    private fun deleteQuietly(target: SharedEntry) {
        try {
            tree.delete(target)
        } catch (_: Exception) {
        }
    }

    // ── zip ──────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * zip 받기의 첫 걸음 — `{"base": 폴더, "names": [이름…]}` 을 받아 담을 차례를 **미리 펴 보고**(항목 수·깊이 상한을 받기 전에 알린다 —
     * [SharedOps.zipPlan]) 한 번 쓰는 표를 준다. 화면은 그 표로 `GET /api/zip?id=…` 를 `<a download>` 로 연다 — 브라우저가 디스크로 바로
     * 받는다(XHR 로 받으면 통째로 메모리에 쌓인다). 표는 [ZIP_TICKET_MS] 동안만 살고, 한꺼번에 [MAX_ZIP_TICKETS] 개까지다. 고른 것이 많아도
     * 주소가 길어지지 않는다(머리 한 줄 [MAX_HEADER_LINE_BYTES]).
     */
    private fun serveZipPlan(request: Request, input: InputStream, output: OutputStream) {
        val body = readJson(request, input)
        if (!tree.isAvailable()) {
            sendJson(output, 503, Json.folderMissing())
            return
        }
        val baseSegments = publicPath(body["base"] as? String ?: "")
        val names = body.texts("names")
        if (activeZips.get() >= MAX_CONCURRENT_ZIPS) throw OpFailure(503, "다른 묶음 받기가 끝난 뒤에 다시 하세요(동시에 ${MAX_CONCURRENT_ZIPS}개까지)")
        // 기다리는 표의 상한을 **펴 보기 전에** 본다 — 펴 보기는 폴더마다 SAF 질의라, 뒤에서 보면 표가 다 찬 동안 몰아 보낸 요청마다 1만 항목을
        // 펴 보고 버린다. 펴는 사이에 다른 요청이 표를 채웠을 수 있어 넣기 전에 한 번 더 본다.
        val now = env.nowMs()
        zipTickets.entries.removeIf { now - it.value.createdMs > ZIP_TICKET_MS }
        if (zipTickets.size >= MAX_ZIP_TICKETS) throw OpFailure(503, ZIP_TICKETS_FULL)
        val items = ops.zipPlan(baseSegments, names)
        if (zipTickets.size >= MAX_ZIP_TICKETS) throw OpFailure(503, ZIP_TICKETS_FULL)
        val fileName = when {
            names.size == 1 -> "${names[0]}.zip"
            baseSegments.isNotEmpty() -> "${baseSegments.last()}.zip"
            else -> "${tree.rootName()?.takeIf { it.isNotEmpty() } ?: "공유 폴더"}.zip"
        }
        val id = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        zipTickets[id] = ZipTicket(items, fileName, now)
        sendJson(
            output, 200,
            Json.obj("id" to id, "name" to fileName, "count" to items.count { !it.entry.isDirectory }, "bytes" to items.sumOf { if (it.entry.isDirectory) 0L else it.entry.size }),
        )
    }

    /**
     * zip 을 흘려 보낸다 — 파일마다 읽어 곧바로 쓴다(메모리에 쌓지 않는다). 길이를 미리 모르므로 `Content-Length` 없이 보내고 연결을 닫아 끝을
     * 알린다. 이미 눌린 형식(그림·영상·소리·압축)은 압축하지 않고(폰의 CPU), 나머지는 가볍게(수준 1). 중간에 읽지 못하면 연결을 끊는다 —
     * 브라우저는 받기 실패로 보인다(반쯤 된 zip 을 '완료' 로 보이지 않게). 동시에 [MAX_CONCURRENT_ZIPS] 개. 이 연결의 스레드에서 돈다.
     */
    private fun serveZip(request: Request, output: OutputStream) {
        val ticket = request.query("id")?.let { zipTickets.remove(it) }
        if (ticket == null || env.nowMs() - ticket.createdMs > ZIP_TICKET_MS) {
            sendText(output, 404, "이 묶음 받기는 끝났거나 오래되었습니다 — 화면에서 다시 고르세요.")
            return
        }
        if (activeZips.incrementAndGet() > MAX_CONCURRENT_ZIPS) {
            activeZips.decrementAndGet()
            sendText(output, 503, "다른 묶음 받기가 끝난 뒤에 다시 하세요.")
            return
        }
        try {
            sendHeaders(
                output, 200,
                listOf(
                    "Content-Type" to "application/zip",
                    "Content-Disposition" to "attachment; filename*=UTF-8''${SharedPaths.percentEncode(ticket.fileName)}",
                    "Cache-Control" to "no-store",
                ),
            )
            val counting = object : FilterOutputStream(output) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    env.onBytesOut(len.toLong())
                }
            }
            val zip = ZipOutputStream(BufferedOutputStream(counting, TRANSFER_BUFFER_BYTES))
            val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
            for (item in ticket.items) {
                val entry = ZipEntry(item.path)
                if (item.entry.modified > 0) entry.time = item.entry.modified
                if (item.entry.isDirectory) {
                    zip.putNextEntry(entry)
                    zip.closeEntry()
                    continue
                }
                zip.setLevel(if (isPacked(item.entry.name)) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED)
                val source = tree.openRead(item.entry) ?: throw java.io.IOException("읽지 못했다")
                source.use {
                    zip.putNextEntry(entry)
                    while (true) {
                        val n = it.read(buffer)
                        if (n == -1) break
                        zip.write(buffer, 0, n)
                    }
                    zip.closeEntry()
                }
                // 파일마다 내보낸다 — 다음 파일을 읽는 동안 받는 쪽이 멈춰 보이지 않게(작은 파일이 많으면 버퍼가 채워질 때까지 기다리게 된다).
                zip.flush()
            }
            zip.finish()
            zip.flush()
        } finally {
            activeZips.decrementAndGet()
        }
    }

    private fun isPacked(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in PACKED_EXTENSIONS

    // ── 관리 ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun serveMakeFolder(request: Request, output: OutputStream) {
        val segments = publicPath(request.requestedPath)
        if (segments.isEmpty()) throw OpFailure(400, "만들 폴더의 이름이 없습니다")
        val made = ops.makeDirectory(segments.dropLast(1), segments.last())
        sendJson(output, 200, Json.obj("ok" to true, "name" to made.name))
    }

    private fun serveRename(body: Map<String, Any?>, output: OutputStream) {
        val renamed = ops.rename(publicPath(body.text("path")), body.text("name"))
        sendJson(output, 200, Json.obj("ok" to true, "name" to renamed.name))
    }

    private fun serveTransfer(body: Map<String, Any?>, copy: Boolean, output: OutputStream) {
        val conflict = when (body["conflict"] as? String ?: "ask") {
            "ask" -> SharedOps.Conflict.ASK
            "skip" -> SharedOps.Conflict.SKIP
            "rename" -> SharedOps.Conflict.RENAME
            else -> throw OpFailure(400, "conflict 는 ask·skip·rename 가운데 하나입니다")
        }
        val outcome = ops.transfer(publicPath(body.text("base")), body.texts("names"), publicPath(body.text("to")), copy, conflict)
        sendJson(output, 200, outcomeJson(outcome))
    }

    private fun serveTrash(body: Map<String, Any?>, output: OutputStream) {
        sendJson(output, 200, outcomeJson(ops.trash(publicPath(body.text("base")), body.texts("names"))))
    }

    private fun serveTrashList(output: OutputStream) {
        val items = ops.trashList().map {
            mapOf("id" to it.id, "name" to it.name, "from" to it.from, "deleted" to it.deleted, "dir" to it.isDirectory, "size" to it.size)
        }
        // 보관 기간을 함께 — 화면이 '보관 기간 N일 — 지난 것은 저절로 비워집니다' 를 보인다(0 은 비우지 않음).
        sendJson(output, 200, Json.obj("items" to items, "retentionDays" to trashRetentionDays().coerceAtLeast(0)))
    }

    private fun serveRestore(body: Map<String, Any?>, output: OutputStream) {
        sendJson(output, 200, outcomeJson(ops.restore(body.texts("ids"))))
    }

    private fun servePurge(body: Map<String, Any?>, output: OutputStream) {
        val count = if (body["all"] == true) ops.purge(null) else ops.purge(body.texts("ids"))
        sendJson(output, 200, Json.obj("ok" to true, "count" to count))
    }

    private fun outcomeJson(outcome: SharedOps.Outcome): String = Json.obj(
        "ok" to outcome.failed.isEmpty(),
        "done" to outcome.done.map { mapOf("from" to it.first, "to" to it.second) },
        "skipped" to outcome.skipped,
        "failed" to outcome.failed.map { mapOf("name" to it.first, "error" to it.second) },
    )

    // ── 바이트 옮기기 ─────────────────────────────────────────────────────────────────────────────────────

    /** [count] 바이트를 옮기고 실제로 옮긴 양을 돌려준다. 원본이 먼저 끝나면 그만큼만 — 부르는 쪽이 선언과 견준다. */
    private fun copy(source: InputStream, sink: OutputStream, count: Long, onBytes: (Long) -> Unit): Long {
        val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
        var remaining = count
        while (remaining > 0) {
            val want = minOf(remaining, buffer.size.toLong()).toInt()
            val read = source.read(buffer, 0, want)
            if (read == -1) break
            sink.write(buffer, 0, read)
            onBytes(read.toLong())
            remaining -= read
        }
        sink.flush()
        return count - remaining
    }

    /** [count] 바이트를 버린다. `skip` 은 덜 건너뛸 수 있고 0 을 주는 구현도 있어 그때는 읽어서 버린다. */
    private fun skipFully(source: InputStream, count: Long) {
        var remaining = count
        val scratch = ByteArray(TRANSFER_BUFFER_BYTES)
        while (remaining > 0) {
            val skipped = source.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val want = minOf(remaining, scratch.size.toLong()).toInt()
            val read = source.read(scratch, 0, want)
            if (read == -1) return
            remaining -= read
        }
    }

    // ── 답 쓰기 ──────────────────────────────────────────────────────────────────────────────────────────

    /** 머리를 쓴다 — 모든 답에 [COMMON_HEADERS] 와 [csp](null 이면 싣지 않는다)를 붙인다(머리말 '답의 머리'). */
    private fun sendHeaders(output: OutputStream, code: Int, headers: List<Pair<String, String>>, csp: String? = API_CSP) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(code).append(' ').append(REASONS[code] ?: "Status").append("\r\n")
        for ((name, value) in headers) builder.append(name).append(": ").append(value).append("\r\n")
        for ((name, value) in COMMON_HEADERS) builder.append(name).append(": ").append(value).append("\r\n")
        if (csp != null) builder.append("Content-Security-Policy: ").append(csp).append("\r\n")
        // 연결마다 요청 하나 — keep-alive 를 하려면 모든 답의 본문 경계를 정확히 맞춰야 하고 얻는 것보다 틀릴 틈이 크다.
        builder.append("Connection: close\r\n")
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun sendStatus(output: OutputStream, code: Int) {
        try {
            sendHeaders(output, code, listOf("Content-Length" to "0"))
            output.flush()
        } catch (_: Exception) {
            // 이미 끊긴 연결.
        }
    }

    private fun sendText(output: OutputStream, code: Int, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        sendHeaders(
            output, code,
            listOf("Content-Type" to "text/plain; charset=utf-8", "Content-Length" to body.size.toString(), "Cache-Control" to "no-store"),
        )
        output.write(body)
        output.flush()
    }

    private fun sendJson(output: OutputStream, code: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        sendHeaders(
            output, code,
            listOf(
                "Content-Type" to "application/json; charset=utf-8",
                "Content-Length" to body.size.toString(),
                "Cache-Control" to "no-store",
            ),
        )
        output.write(body)
        output.flush()
    }

    companion object {
        private const val TAG = "FileServer"

        /** 기본 포트. 차 있으면 다음 포트로(클래스 머리말). */
        const val BASE_PORT = 8080

        /** 해 볼 포트 수 — [BASE_PORT] 부터 하나씩(8080…8089). */
        const val PORT_ATTEMPTS = 10

        /** 동시 연결 상한. 근거는 클래스 머리말. */
        const val MAX_SESSIONS = 32

        /** 목록 한 번에 담는 항목 상한 — 넘으면 자르고 `truncated`(화면이 알린다). 화면은 보이는 줄만 그린다. */
        const val MAX_LIST_ENTRIES = 20_000

        /** 동시에 흘리는 zip 수. 하나가 연결 하나(스레드 하나)를 끝날 때까지 쥔다. */
        const val MAX_CONCURRENT_ZIPS = 2

        /** 기다리는 zip 표의 수와 수명. */
        const val MAX_ZIP_TICKETS = 4
        const val ZIP_TICKET_MS = 60_000L

        /** 섬네일을 동시에 짓는 수와 차례를 기다리는 시간. */
        const val THUMB_CONCURRENCY = 2
        private const val THUMB_WAIT_SECONDS = 20L

        /** 관리 동작이 차례를 기다리는 시간. */
        private const val MUTATION_WAIT_SECONDS = 30L

        /** 관리 기능의 JSON 몸 상한 — 이름 [SharedOps.MAX_BATCH] 개가 넉넉히 든다. */
        const val MAX_JSON_BYTES = 512 * 1024

        private const val PATH_LIST = "/api/list"
        private const val PATH_FILE = "/api/file"
        private const val PATH_ZIP = "/api/zip"

        /** 화면 자산 — 주소 → (자산 이름, Content-Type). 이 밖의 이름은 묻지 않는다. */
        val UI_FILES: Map<String, Pair<String, String>> = mapOf(
            "/" to ("fileman.html" to "text/html; charset=utf-8"),
            "/fileman.js" to ("fileman.js" to "text/javascript; charset=utf-8"),
            "/fileman.css" to ("fileman.css" to "text/css; charset=utf-8"),
        )

        /**
         * 화면의 CSP — 스크립트·스타일·그림·미디어·요청 모두 **이 서버의 것만**, 인라인 스크립트·스타일 없음(화면은 HTML 안에 스크립트를 두지
         * 않는다), 다른 쪽이 틀로 싣지 못함, `<base>`·폼 보내기 없음.
         */
        const val UI_CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; media-src 'self'; connect-src 'self'; " +
            "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

        /** 화면이 아닌 답의 CSP — 어쩌다 문서로 열려도 스크립트가 돌지 않게(`sandbox`). */
        const val API_CSP = "default-src 'none'; sandbox; frame-ancestors 'none'"

        /** 브라우저로 여는 PDF — sandbox 없이(크로미움의 PDF 보기), 틀로 싣지는 못하게. */
        const val PDF_CSP = "frame-ancestors 'none'"

        /** 모든 답에. */
        private val COMMON_HEADERS = listOf(
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            "Cross-Origin-Resource-Policy" to "same-origin",
        )

        private val REASONS = mapOf(
            200 to "OK", 206 to "Partial Content", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict",
            411 to "Length Required", 413 to "Payload Too Large", 415 to "Unsupported Media Type", 416 to "Range Not Satisfiable",
            421 to "Misdirected Request", 500 to "Internal Server Error", 501 to "Not Implemented", 503 to "Service Unavailable",
        )

        /** 둘이면 거절하는 머리. */
        private val SINGLE_HEADERS = setOf("host", "origin", "content-type", "content-length", "transfer-encoding", "sec-fetch-site", "range")

        /** 이미 눌린 형식 — zip 에서 다시 누르지 않는다. */
        private val PACKED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "mp4", "m4v", "mov", "mkv", "webm", "3gp", "mp3", "m4a", "aac", "ogg",
            "opus", "flac", "zip", "7z", "rar", "gz", "xz", "bz2", "apk", "pdf",
        )

        private const val MANAGE_OFF = "관리 기능이 꺼져 있습니다 — 폰의 테더링 화면에서 ‘관리 허용’ 을 켜야 합니다"

        private const val ZIP_TICKETS_FULL = "묶음 받기를 기다리는 것이 많습니다 — 잠시 뒤에 다시 하세요"

        /** 서버가 선 뒤 휴지통을 처음 손보기까지 — 테더링을 켜는 동안(핫스팟·권한)과 겹치지 않게 잠깐 미룬다. */
        const val TRASH_SWEEP_FIRST_MS = 15_000L

        /** 그 뒤 휴지통을 손보는 간격 — 하루. */
        const val TRASH_SWEEP_EVERY_MS = 24L * 60 * 60 * 1000

        private const val OVERWRITE_EXISTS = "같은 이름이 이미 있습니다"

        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val HEADER_BUFFER_BYTES = MAX_HEADER_LINE_BYTES

        /** 머리 줄 수 상한(`:` 없는 줄·겹친 이름도 센다). */
        internal const val MAX_HEADER_COUNT = 64

        /** 옮기기 버퍼 — 프록시의 중계(8 KiB)보다 크게. 파일은 대개 큰 덩이다. */
        private const val TRANSFER_BUFFER_BYTES = 64 * 1024

        /** 421 의 본문 — 손님의 브라우저에 보인다. */
        internal const val WRONG_HOST_HELP =
            "이 파일 서버는 http://192.168.49.1 로 연 페이지의 요청만 받습니다(Host 머리가 다르면 거절합니다 — DNS 재바인딩 막기).\n" +
                "주소창에 http://192.168.49.1:8080 처럼 IP 주소로 여세요."

        /**
         * `Host` 가 [allowed] 이거나 `[allowed]:포트`(숫자 1~5자)인가. 대소문자·끝의 점·다른 이름은 받지 않는다 — 주소 리터럴만 받으므로 그럴
         * 일이 없다. 머리가 없으면(null) 거절.
         */
        fun isAllowedHost(host: String?, allowed: String): Boolean {
            val value = host?.trim() ?: return false
            if (value == allowed) return true
            if (!value.startsWith("$allowed:")) return false
            val port = value.substring(allowed.length + 1)
            return port.length in 1..5 && port.all { it in '0'..'9' }
        }
    }
}
