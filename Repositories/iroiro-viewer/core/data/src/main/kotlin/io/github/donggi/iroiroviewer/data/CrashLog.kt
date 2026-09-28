package io.github.donggi.iroiroviewer.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 크래시 기록기(14단계. 2단계가 '릴리스 마감 때 넣는다' 로 미뤄 둔 것).
 *
 * ## 무엇을 남기는가
 *
 * 앱이 잡지 못한 예외로 죽을 때 **앱 전용 저장소**(`filesDir/crash/`)에 글 파일 하나. 최근 [KEEP] 건만 두고, 한 건은
 * [CrashReport.MAX_BYTES] 를 넘지 않는다. 담는 것은 판 번호·API 수준·시각·스레드 이름·스택 트레이스뿐이고, **절대경로는
 * 지우고 적는다**([CrashReport.redact]) — 예외 메시지에는 사용자가 연 파일의 경로가 그대로 들어 있다(7z 의
 * `PasswordRequiredException`, `FileNotFoundException` 이 그 모양이다).
 *
 * ## 왜 화면에 내용을 보이지 않는가
 *
 * 예외 메시지를 화면에 보내지 않는다는 규칙(CLAUDE.md '코드')이 여기에도 그대로 걸린다. 설정 화면은 **건수와 마지막
 * 시각만** 보이고, 내용은 사용자가 '공유' 를 눌렀을 때만 사본으로 앱 밖에 나간다([exportTo]). 원본이 있는 `filesDir`
 * 은 `FileProvider` 가 노출하지 않는 곳이다(`iro_file_paths.xml` 은 저장 볼륨과 캐시의 `share/` 만 연다) — 그래서 사본을
 * 캐시의 공유 폴더에 만든다.
 *
 * ## 왜 `logcat` 이 아닌가
 *
 * 릴리스에서 `Iro.d` 는 호출 지점째 사라지고, 사용자 기기의 logcat 은 우리가 읽을 수 없다. 사용자가 '앱이 꺼졌다' 고
 * 말했을 때 손에 쥘 것이 이것 하나다.
 *
 * **이 클래스의 어느 함수도 던지지 않는다.** 기록기가 던지면 죽어 가는 프로세스에서 원래 예외 대신 기록기의 예외가
 * 보고되고, 설정 화면에서는 기록기 때문에 화면이 죽는다.
 */
class CrashLog(private val dirProvider: () -> File, private val clock: () -> Long = System::currentTimeMillis) {

    constructor(dir: File, clock: () -> Long = System::currentTimeMillis) : this({ dir }, clock)

    /** 기록 하나의 겉모습 — 화면이 보이는 것은 이것뿐이다. */
    data class Summary(val count: Int, val lastAt: Long?) {
        companion object {
            val EMPTY = Summary(0, null)
        }
    }

    private val lock = Any()

    /**
     * 기록 하나를 쓴다. 임시 파일에 쓰고 이름을 바꾼다 — 죽어 가는 도중에 끊겨도 반쯤 쓴 기록이 '기록' 으로 세어지지
     * 않는다. 스레드 둘이 동시에 죽을 수 있어 잠근다.
     */
    fun record(meta: CrashReport.Meta, thread: Thread, error: Throwable) {
        try {
            synchronized(lock) {
                val dir = dirProvider()
                if (!dir.isDirectory && !dir.mkdirs()) return
                val at = clock()
                val bytes = CrashReport.encode(CrashReport.compose(meta, at, thread.name, error))
                val name = nameFor(dir, at)
                val tmp = File(dir, "$name.tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!tmp.renameTo(File(dir, name))) tmp.delete()
                prune(dir)
            }
        } catch (_: Throwable) {
            // 기록을 남기지 못해도 원래 예외의 보고(이전 처리기)는 이어져야 한다. 여기서 할 수 있는 일이 없다.
        }
    }

    fun summary(): Summary = try {
        val files = reports()
        Summary(files.size, files.firstOrNull()?.let { timeOf(it.name) })
    } catch (_: Throwable) {
        Summary.EMPTY
    }

    /**
     * 기록 전부를 **새것부터** 한 파일로 이어 [target] 에 쓴다. 기록이 없으면 null.
     *
     * 파일 하나로 잇는 까닭 — 공유는 `ACTION_SEND` 한 건이고(받는 앱 대부분이 첨부 하나를 가장 잘 받는다), 기록 다섯이
     * 따로 가면 받는 쪽에서 차례가 흐트러진다. 원본을 옮기지 않고 **복사**한다: 공유가 끝나도 기록은 사용자가 지울 때까지
     * 남는다.
     *
     * **잠근다** — '공유' 를 두 번 빨리 누르면 두 쓰기가 같은 임시 파일을 동시에 열어(하나가 자르고 하나가 쓰는 중) 섞인
     * 사본이 나간다(14단계 검토).
     */
    fun exportTo(target: File): File? = try {
        synchronized(lock) {
            val files = reports()
            if (files.isEmpty()) {
                null
            } else {
                target.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
                val tmp = File(target.path + ".tmp")
                FileOutputStream(tmp).use { out ->
                    files.forEachIndexed { i, f ->
                        out.write(CrashReport.encode(CrashReport.separator(i + 1, files.size)))
                        f.inputStream().use { it.copyTo(out) }
                        out.write('\n'.code)
                    }
                }
                if (target.exists()) target.delete()
                if (tmp.renameTo(target)) target else null.also { tmp.delete() }
            }
        }
    } catch (_: Throwable) {
        null
    }

    /** 기록을 전부 지운다. 남은 것이 없으면 true. */
    fun clear(): Boolean = try {
        synchronized(lock) {
            val dir = dirProvider()
            dir.listFiles()?.filter { it.name.startsWith(PREFIX) }?.forEach { it.delete() }
            reports().isEmpty()
        }
    } catch (_: Throwable) {
        false
    }

    /** 새것부터. 이름에 시각을 13자리로 채워 적으므로 이름 차례가 곧 시간 차례다. */
    internal fun reports(): List<File> {
        val dir = dirProvider()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
            .sortedByDescending { it.name }
    }

    private fun prune(dir: File) {
        val all = dir.listFiles() ?: return
        // 기록이 쓰다 끊긴 임시 파일(앞선 죽음이 남긴 것)도 함께 걷는다.
        all.filter { it.name.startsWith(PREFIX) && it.name.endsWith(".tmp") }.forEach { it.delete() }
        all.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    /** 같은 밀리초에 둘이 죽어도 덮어쓰지 않는다 — 뒤에 번호를 붙인다. */
    private fun nameFor(dir: File, at: Long): String {
        val stamp = at.coerceAtLeast(0).toString().padStart(13, '0')
        var n = 0
        while (true) {
            val name = "$PREFIX$stamp-$n$SUFFIX"
            if (!File(dir, name).exists()) return name
            n++
        }
    }

    private fun timeOf(name: String): Long? =
        name.removePrefix(PREFIX).substringBefore('-').toLongOrNull()

    companion object {
        /** 남기는 건수. 다섯이면 '같은 자리에서 되풀이해 죽는다' 가 보이고 저장소에는 흔적 정도만 남는다. */
        const val KEEP = 5
        private const val PREFIX = "crash-"
        private const val SUFFIX = ".txt"

        /** `filesDir` 아래의 기록 폴더. **`FileProvider` 가 노출하지 않는 곳**이어야 한다(클래스 주석). */
        fun dirOf(context: Context): File = File(context.filesDir, "crash")

        fun of(context: Context): CrashLog {
            val app = context.applicationContext ?: context
            return CrashLog({ dirOf(app) })
        }

        /**
         * 처리기를 건다. **앞의 처리기를 반드시 잇는다** — 안드로이드의 기본 처리기가 '앱이 멈췄습니다' 와 프로세스
         * 정리를 한다. 우리 것이 그 줄을 끊으면 앱이 죽지 않고 얼어붙은 채로 남는다.
         *
         * `Application.attachBaseContext` 에서 부른다 — `onCreate` 보다 앞이라 콘텐츠 제공자(androidx.startup 등)의
         * 초기화에서 나는 죽음도 잡힌다. 그 시점에 쓸 수 있는 것은 넘겨받은 [base] 뿐이다. 두 번 불려도 한 겹만 건다.
         */
        fun install(base: Context, versionName: String, versionCode: Long) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            val meta = CrashReport.Meta(versionName, versionCode, Build.VERSION.SDK_INT)
            // 폴더는 죽는 순간에 정한다. 여기서 `filesDir` 을 부르면 시작할 때마다 디스크를 한 번 더 만진다.
            val log = CrashLog({ dirOf(base) })
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(log, meta, current))
        }
    }
}

/**
 * 잡지 못한 예외를 기록하고 **앞의 처리기에 넘긴다.** 앞의 것이 없으면(안드로이드에서는 없을 일이다) 스레드를 그냥
 * 끝내지 않고 프로세스를 내린다 — 반쯤 죽은 앱이 화면에 남는 것이 가장 나쁘다.
 */
internal class CrashHandler(
    private val log: CrashLog,
    private val meta: CrashReport.Meta,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val exitWithoutPrevious: () -> Unit = {
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(10)
    },
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            log.record(meta, t, e)
        } catch (_: Throwable) {
            // record 는 던지지 않지만, 여기는 마지막 방어선이라 한 번 더 막는다.
        }
        val p = previous
        if (p != null) p.uncaughtException(t, e) else exitWithoutPrevious()
    }
}

/**
 * 기록 한 건의 모양. **순수 함수만 둔다** — JVM 시험이 경로 지우기와 크기 상한을 그대로 본다.
 */
object CrashReport {

    /** 한 건의 상한(UTF-8 바이트). 깊은 재귀의 `StackOverflowError` 는 프레임이 천 개를 넘는다. */
    const val MAX_BYTES = 64 * 1024

    /** 지운 경로 자리에 남기는 표시. 사용자가 공유 전에 열어 봐도 무엇이 빠졌는지 알 수 있게 우리 말로 적는다. */
    const val REDACTED = "<경로>"

    /**
     * 잘라 낸 자리의 표시. 기록의 머리(`version:`…)와 같이 **개발자가 읽는 글**이라 화면 문구(`strings.xml`)가 아니다 —
     * 이 파일은 앱 화면에 한 번도 그려지지 않는다.
     */
    internal const val TRUNCATED = "\n\n... (truncated) ...\n\n"

    data class Meta(val versionName: String, val versionCode: Long, val apiLevel: Int)

    /**
     * 경로가 시작하는 자리 — 앞이 글자·숫자·점이 아닌 `/` 뒤에 이름 한 칸과 `/` 가 더 온다(`/storage/…`, `/data/…`,
     * `/sdcard/…`, `file:///…` 의 셋째 `/`). `image/jpeg`·`1/2` 는 앞이 글자·숫자라 걸리지 않는다.
     *
     * **어디서 끝나는지는 알 수 없다** — 파일 이름에는 공백도 괄호도 **따옴표도** 들어간다(`Don't Stop.mp3`,
     * `Guns N' Roses`). 그래서 줄 끝이나 `": "` 까지를 통째로 지운다(`FileNotFoundException` 은 `경로: open failed: …`
     * 모양이라 뒤의 까닭은 남는다). **따옴표에서 멈추지 않는다** — 멈추면 아포스트로피 뒤의 이름 조각이 기록에 남는다
     * (14단계 검토가 잡았다). 따옴표로 싼 경로는 [QUOTED] 가 먼저 맡는다. 덜 지우는 것보다 더 지우는 편이 낫다.
     */
    private val ABSOLUTE_PATH = Regex("""(?<![\w.])/[^\s/'"`:]+/[^\r\n]*?(?=: |[\r\n]|$)""")

    /**
     * 문서 제공자·파일 주소도 경로를 싣는다(`content://…/document/primary%3ADownload%2F…`). 주소째 지운다 — 끝을 정하는
     * 규칙은 경로와 같다(날것의 공백이 든 `file://` 주소가 실제로 오고, `Uri.encode` 는 `'` 를 바꾸지 않는다).
     */
    private val URI_WITH_PATH = Regex("""\b(?:content|file)://[^\r\n]*?(?=: |[\r\n]|$)""")

    /**
     * 따옴표로 싼 경로·주소(`'/storage/…/a.7z' is locked`, SQLite 의 `'/data/…/x.db'`). **여는 따옴표와 같은 따옴표가 그
     * 줄에서 마지막으로 나오는 자리까지** 지운다 — 이름 안의 아포스트로피(`'/…/Tom's.jpg'`)에서 끊기지 않는다. 닫는
     * 따옴표로 치는 것은 뒤에 글자·숫자가 붙지 않은 것뿐이다(`Tom's` 의 `'` 는 닫는 따옴표가 아니다). 따옴표 자체는
     * 남긴다(뒤의 까닭이 읽히게). 닫는 따옴표가 없으면 이 규칙은 비켜 서고 위의 두 규칙이 줄 끝까지 지운다.
     */
    private val QUOTED = Regex("""(['"`])(?:/[^\s/'"`:]+/|(?:content|file)://)[^\r\n]*(?=\1(?!\w))""")

    fun redact(text: String): String {
        val quoted = QUOTED.replace(text) { m -> m.groupValues[1] + REDACTED }
        return ABSOLUTE_PATH.replace(URI_WITH_PATH.replace(quoted, REDACTED), REDACTED)
    }

    /**
     * 기록 한 건. 머리(우리가 쓴 값)와 몸(스택 트레이스 — 지운 뒤)을 잇고 [MAX_BYTES] 로 자른다.
     *
     * 트레이스를 만드는 일 자체가 실패할 수 있다(메모리가 모자라 죽는 중이거나, 예외의 `toString` 이 다시 던진다). 그때는
     * 예외의 클래스 이름만이라도 남긴다.
     */
    fun compose(meta: Meta, at: Long, threadName: String, error: Throwable): String {
        val body = try {
            error.stackTraceToString()
        } catch (_: Throwable) {
            try {
                error.javaClass.name
            } catch (_: Throwable) {
                "?"
            }
        }
        val head = buildString {
            append("iroiro-viewer crash report\n")
            append("version: ").append(meta.versionName).append(" (").append(meta.versionCode).append(")\n")
            append("api: ").append(meta.apiLevel).append('\n')
            append("time: ").append(timeText(at)).append('\n')
            append("thread: ").append(redact(threadName)).append('\n')
            append('\n')
        }
        return limit(head + redact(body), MAX_BYTES)
    }

    /** 공유 사본에서 기록과 기록 사이에 넣는 줄. */
    fun separator(index: Int, total: Int): String = "===== $index / $total =====\n"

    fun encode(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    /**
     * UTF-8 로 [maxBytes] 를 넘지 않게 자른다. **앞과 뒤를 함께 남긴다** — 앞에는 죽은 자리가, 끝에는 원인
     * (`Caused by:` 의 마지막 — 가장 안쪽의 예외)이 있다. 앞만 남기면 긴 사슬에서 정작 원인이 잘린다.
     * 대리 쌍(이모지 같은 BMP 밖 글자)을 가르지 않는다.
     */
    fun limit(text: String, maxBytes: Int): String {
        if (utf8Length(text) <= maxBytes) return text
        val marker = TRUNCATED
        val room = maxBytes - utf8Length(marker)
        if (room <= 0) return utf8Prefix(text, maxBytes)
        val headBytes = room * 3 / 4
        val head = utf8Prefix(text, headBytes)
        val tail = utf8Suffix(text, room - utf8Length(head))
        return head + marker + tail
    }

    internal fun utf8Length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            n += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> {
                    i++
                    4
                }
                else -> 3
            }
            i++
        }
        return n
    }

    private fun utf8Prefix(s: String, maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val pair = Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])
            val w = when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                pair -> 4
                else -> 3
            }
            if (bytes + w > maxBytes) break
            bytes += w
            i += if (pair) 2 else 1
        }
        return s.substring(0, i)
    }

    private fun utf8Suffix(s: String, maxBytes: Int): String {
        var bytes = 0
        var i = s.length
        while (i > 0) {
            val c = s[i - 1]
            val pair = Character.isLowSurrogate(c) && i - 2 >= 0 && Character.isHighSurrogate(s[i - 2])
            val w = when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                pair -> 4
                else -> 3
            }
            if (bytes + w > maxBytes) break
            bytes += w
            i -= if (pair) 2 else 1
        }
        return s.substring(i)
    }

    /** 기기의 시간대로, 시간대 차이를 함께 적는다 — 받는 사람이 다른 시간대에 있어도 언제인지 틀리지 않게. */
    private fun timeText(at: Long): String = try {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
    } catch (_: Throwable) {
        at.toString()
    }
}
