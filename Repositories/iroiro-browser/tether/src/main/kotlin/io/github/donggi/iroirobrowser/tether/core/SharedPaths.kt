package io.github.donggi.iroirobrowser.tether.core

/**
 * 파일 서버의 경로 규약과 이름 도우미.
 *
 * API 가 받는 `path` 는 공유 폴더를 맨 위로 하는 **상대 경로**이고 구분자는 `/` 다. 빈 글이 맨 위다. [SharedTree] 는 성분마다 자식만 찾아
 * 내려가므로 트리 밖으로 나갈 길이 구조로 없지만, 여기서도 막는다 — 이 검사가 없으면 나중에 구현을 파일 경로 기반으로 바꿀 때 조용히 약해진다.
 */
object SharedPaths {

    /** 경로 성분의 최대 개수 — 끝없이 깊은 순회로 IPC 를 쏟아붓지 못하게. */
    const val MAX_SEGMENTS = 32

    /**
     * 상대 경로를 성분으로 쪼갠다. 거절: 절대 경로(`/` 로 시작), `.`·`..` 성분, 빈 성분, 역슬래시, NUL, 성분 수 초과. 빈 경로는 빈 목록(맨 위).
     * 공백은 정상적인 파일 이름 글자라 막지 않는다. NUL 은 네이티브 층에서 글을 자르는 데 쓰일 수 있어 막는다.
     *
     * @return 성분 목록. 경로가 틀렸으면 null
     */
    fun split(path: String): List<String>? {
        if (path.isEmpty()) return emptyList()
        if (path.startsWith("/")) return null
        if (path.contains('\\')) return null
        if (path.contains('\u0000')) return null
        val segments = path.split('/')
        if (segments.size > MAX_SEGMENTS) return null
        for (segment in segments) {
            if (segment.isEmpty()) return null
            if (segment == "." || segment == "..") return null
        }
        return segments
    }

    /**
     * 공유 폴더 맨 위의 **숨은 휴지통** 이름([SharedOps.trash] — 관리 기능의 '지우기' 는 여기로 옮기기다). 목록·나무·zip 에서 감추고, 보통의
     * API(목록·받기·올리기·옮기기 …)는 이 이름으로 시작하는 경로를 없는 것으로 본다([isHidden]) — 휴지통은 휴지통 API 로만 닿는다.
     */
    const val TRASH_NAME = ".web-trash"

    /** 보통의 API 가 닿지 않는 경로인가 — 맨 위 성분이 휴지통 이름([isTrashName]). */
    fun isHidden(segments: List<String>): Boolean = segments.firstOrNull()?.let { isTrashName(it) } == true

    /**
     * [TRASH_NAME] 과 같은 자리를 가리키는 이름인가 — 대소문자를 가리지 않고(SD 카드의 FAT·exFAT, 기기 저장소의 대소문자 무시), **끝의 점과
     * 공백도 떼고** 본다: 리눅스의 vfat·exfat 는 이름 끝의 점을 떼고 찾거나 만들고(윈도도 끝의 공백까지), 그래서 `.web-trash.` 로 만들거나 올린
     * 것이 휴지통 자리에 앉았다(이 PC 의 시험 폴더에서 그랬다 — 2026-11-21 검토에서 고침).
     */
    fun isTrashName(name: String): Boolean = trashKey(name).equals(TRASH_NAME, ignoreCase = true)

    /**
     * 휴지통 이름과 견줄 꼴 — Unicode Default_Ignorable 전부([isIgnorableCodePoint])와 끝의 점·공백을 뗀다(11-27 점검·재검토): 그런 글자를 끼운
     * `.web-trash` 는 제공자에 따라(정규화·무시되는 글자를 버리는 파일 시스템) 휴지통 자리에 앉거나 화면에서 휴지통과 구별되지 않는다. 새 이름에서
     * **빼는** 글자([cleanName])보다 넓다 — 일반 이름의 소프트 하이픈·태그 글자·이체 선택자는 그대로 두되(사용자 결정 11-21) 휴지통 판정만 넓게.
     */
    fun trashKey(name: String): String = removeCodePoints(name, ::isIgnorableCodePoint).trimEnd('.', ' ')

    /** 이름 하나의 UTF-8 바이트 상한 — 리눅스 파일 시스템의 255. */
    const val MAX_NAME_BYTES = 255

    /**
     * 새로 짓는 이름(폴더 만들기·이름 바꾸기·되살리기)으로 받는가 — 비지 않고, 공백만이 아니고, `.`·`..` 이 아니고, `/`·역슬래시·NUL·제어 문자
     * (U+0000–001F·U+007F)가 없고, UTF-8 로 [MAX_NAME_BYTES] 바이트 안. 맨 위의 [TRASH_NAME] 은 부르는 쪽이 따로 막는다(자리에 달렸다).
     * 손님이 보낸 새 이름은 먼저 [cleanName] 으로 다듬은 뒤에 본다(제어 글자는 거절하지 않고 뺀다 — 사용자 결정 11-21).
     */
    fun isValidName(name: String): Boolean {
        if (name.isBlank() || name == "." || name == "..") return false
        if (name.any { it == '/' || it == '\\' || it.code < 0x20 || it.code == 0x7F }) return false
        return name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES
    }

    /**
     * 새 이름에서 빼는 글자인가 — 보이지 않거나 글의 차례를 뒤집어 이름을 속이는 것들(사용자 결정 11-21 — 거절하지 않고 뺀다):
     * - C0·DEL·C1 제어 글자: U+0000–001F · U+007F · U+0080–009F
     * - 방향 제어: U+061C(ALM) · U+200E·200F(LRM·RLM) · U+202A–202E(LRE·RLE·PDF·LRO·RLO) · U+2066–2069(LRI·RLI·FSI·PDI) — `사진\u202Egpj.exe`
     *   가 `사진exe.jpg` 로 보이는 꼴
     * - 폭 없는 글자: U+200B–200D(ZWSP·ZWNJ·ZWJ) · U+2060–2064(WORD JOINER·보이지 않는 수학 연산자) · U+FEFF(BOM) · U+180E(몽골 모음 구분)
     * - 줄·문단 나눔: U+2028·2029(글 안에서 줄을 바꾼다)
     *
     * 소프트 하이픈(U+00AD)·태그 글자(U+E0000–E007F — 국기 이모지)·이체 선택자(U+FE00–FE0F — 이모지 꼴)·그 밖의 무시되는 글자는 **빼지 않는다**(일반
     * 이름에 그대로 — 휴지통 판정만 [isIgnorableCodePoint] 로 넓게 본다). 화면(`fileman.js` 의 `STRIP_RE`)이 같은 글자를 미리 빼 보인다 — 앱의
     * 시험(FileManTest)이 둘을 모든 코드 포인트에서 견준다.
     */
    fun isStrippedCodePoint(cp: Int): Boolean = when (cp) {
        in 0x0000..0x001F, in 0x007F..0x009F -> true
        0x061C, 0x200E, 0x200F, in 0x202A..0x202E, in 0x2066..0x2069 -> true
        in 0x200B..0x200D, in 0x2060..0x2064, 0xFEFF, 0x180E -> true
        0x2028, 0x2029 -> true
        else -> false
    }

    /**
     * 휴지통 판정([trashKey])에서 떼는 글자 — [isStrippedCodePoint] 에 Unicode Default_Ignorable_Code_Point 전부를 더한 것: U+00AD · U+034F ·
     * U+061C · U+115F·1160 · U+17B4·17B5 · U+180B–180F · U+200B–200F · U+202A–202E · U+2060–206F · U+3164 · U+FE00–FE0F · U+FEFF · U+FFA0 ·
     * U+FFF0–FFF8 · U+1BCA0–1BCA3 · U+1D173–1D17A · U+E0000–E0FFF(보충 평면은 코드 포인트로). 화면의 `IGNORABLE_RE` 와 같다(FileManTest).
     */
    fun isIgnorableCodePoint(cp: Int): Boolean = isStrippedCodePoint(cp) || when (cp) {
        0x00AD, 0x034F, 0x061C, 0x115F, 0x1160, 0x17B4, 0x17B5, in 0x180B..0x180F -> true
        in 0x200B..0x200F, in 0x202A..0x202E, in 0x2060..0x206F -> true
        0x3164, in 0xFE00..0xFE0F, 0xFEFF, 0xFFA0, in 0xFFF0..0xFFF8 -> true
        in 0x1BCA0..0x1BCA3, in 0x1D173..0x1D17A, in 0xE0000..0xE0FFF -> true
        else -> false
    }

    /** BMP 글자 하나가 [isStrippedCodePoint] 인가(서로게이트 반쪽은 아니다). */
    fun isStrippedChar(c: Char): Boolean = !c.isSurrogate() && isStrippedCodePoint(c.code)

    /** 손님이 보낸 새 이름에서 [isStrippedCodePoint] 글자를 뺀다. 빼고 나서 비거나 틀린 이름이면 부르는 쪽이 [isValidName] 으로 거절한다. */
    fun cleanName(name: String): String = removeCodePoints(name, ::isStrippedCodePoint)

    /** [drop] 이 참인 코드 포인트를 뺀다(보충 평면은 서로게이트 짝째 — 짝 없는 반쪽은 그 값으로 본다). 뺄 것이 없으면 [name] 그대로. */
    private fun removeCodePoints(name: String, drop: (Int) -> Boolean): String {
        var i = 0
        var out: StringBuilder? = null
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val n = Character.charCount(cp)
            if (drop(cp)) {
                if (out == null) out = StringBuilder(name.length).append(name, 0, i)
            } else {
                out?.append(name, i, i + n)
            }
            i += n
        }
        return out?.toString() ?: name
    }

    /**
     * [taken] 과 겹치지 않는 이름 — [name] 이 비어 있으면 그대로, 아니면 `이름 (1).확장자`, `이름 (2).확장자` …(폴더는 확장자를 가르지 않는다).
     * 견주기는 대소문자를 가리지 않는다(FAT·exFAT 의 SD 카드에서 `A.txt` 와 `a.txt` 는 같은 자리다).
     */
    fun freeName(name: String, isDirectory: Boolean, taken: Collection<String>): String {
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        if (name.lowercase() !in lower) return name
        val dot = if (isDirectory) -1 else name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (n in 1..9999) {
            val candidate = "$stem ($n)$ext"
            if (candidate.lowercase() !in lower) return candidate
        }
        return "$stem (${System.nanoTime()})$ext"
    }

    /** 올리기를 받는 동안의 임시 이름의 끝. */
    const val UPLOAD_TEMP_SUFFIX = ".part"

    /**
     * 올리기를 받는 동안의 임시 이름 — 숨은 파일(점으로 시작)이고 끝이 [UPLOAD_TEMP_SUFFIX] 다(`사진.jpg` → `.사진.jpg.part`). 다 받은 뒤에야
     * 원래 이름으로 바꾼다([SharedTree.commitUpload]) — 끊긴 올리기가 원래 파일을 건드리지 않게.
     */
    fun uploadTempName(name: String): String = ".$name$UPLOAD_TEMP_SUFFIX"

    /**
     * 확장자로 MIME 형식을 어림한다. 모르면 `application/octet-stream`(내려받기로 처리된다 — 파일을 옮기는 용도에는 그것이 맞다). 플랫폼의
     * `MimeTypeMap` 을 쓰지 않는다 — 모르는 확장자에 null 을 주는데, SAF 의 파일 만들기는 null 을 받지 않는다.
     */
    fun guessMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /**
     * RFC 3986 의 unreserved 밖의 모든 바이트를 퍼센트 부호로 바꾼다(`Content-Disposition` 의 `filename*` — RFC 6266. 한글 이름이 깨지지
     * 않으려면 이 꼴이어야 한다). `URLEncoder` 는 공백을 `+` 로 바꾸는 폼 부호라 여기에 틀리다. 16진은 손으로 짠다 — `String.format` 은 기본
     * 로케일을 탄다.
     */
    fun percentEncode(text: String): String {
        val builder = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 0x80 && (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9') || char in "-_.~") {
                builder.append(char)
            } else {
                builder.append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
        return builder.toString()
    }

    private const val HEX = "0123456789ABCDEF"

    /** 브라우저가 페이지 안에서 열 수 있는 흔한 형식만. */
    private val MIME_BY_EXTENSION = mapOf(
        "txt" to "text/plain", "md" to "text/plain", "log" to "text/plain",
        "csv" to "text/csv", "json" to "application/json", "xml" to "text/xml",
        "html" to "text/html", "htm" to "text/html", "css" to "text/css",
        "js" to "text/javascript",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "bmp" to "image/bmp",
        "svg" to "image/svg+xml", "heic" to "image/heic",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
        "flac" to "audio/flac", "wav" to "audio/wav",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "pdf" to "application/pdf", "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive",
    )
}
