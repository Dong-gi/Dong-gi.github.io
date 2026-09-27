package io.github.donggi.iroiroviewer.format.opc

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveReader
import io.github.donggi.iroiroviewer.format.archive.ZipArchiveReader
import io.github.donggi.iroiroviewer.format.html.FlowPackage
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * 관계 하나 — `_rels/` 아래 `.rels` 파일의 `<Relationship>`.
 *
 * @param target [external] 이 거짓이면 **푼 부분 이름**(앞 `/` 없음), 참이면 적힌 문자열 그대로
 *   (바깥 URL). 바깥 것은 **절대 `src`·`href` 가 되지 않는다** — 변환기는 세고 버린다.
 */
data class OpcRelationship(
    val id: String,
    val type: String,
    val target: String,
    val external: Boolean,
) {
    /** 유형의 마지막 조각(`image`·`hyperlink`). 과도기·엄격 두 벌을 한 이름으로 본다. */
    val typeName: String get() = type.substringAfterLast('/')
}

/**
 * OPC 패키지 하나(docx·xlsx·pptx 의 겉). ECMA-376 Part 2.
 *
 * ## 무엇을 들고 있는가
 *
 * ZIP 리더와 이름표(부분 이름 → 항목), `[Content_Types].xml`, 그리고 읽은 관계의 캐시다.
 * **본문은 들고 있지 않는다** — 변환기가 필요한 부분을 그때그때 읽는다(`EpubBook` 과 같은 판단).
 *
 * ## 스레드
 *
 * **스레드 안전하지 않다.** ZIP 리더의 예산(`EntryBudget`)이 그렇다. 여러 스레드에서 쓰는
 * `OpcFlowDocument` 가 자기 잠금으로 모든 접근을 한 줄로 세운다.
 *
 * ## 같은 이름
 *
 * ZIP 명세는 같은 이름을 금지하지 않는다(`ArchiveEntry` 의 주석). 대소문자만 다른 이름도
 * OPC 에서는 같은 부분이다. **먼저 나온 것을 쓰고 경고를 남긴다.**
 */
class OpcPackage private constructor(
    private val reader: ArchiveReader,
    private val budget: EntryBudget,
    private val limits: ParseLimits,
    /** 닫을 때 0 으로 덮을 바이트 — 암호를 풀어 낸 평문 패키지. [open] 의 주석. */
    private val wipeOnClose: ByteArray?,
) : FlowPackage {

    /** 찾기 열쇠(`OpcNames.key`) → 항목. */
    private val byKey = HashMap<String, ArchiveEntry>()

    /** 찾기 열쇠 → 바깥에 내보내는 이름(대소문자 그대로). */
    private val names = HashMap<String, String>()

    private val defaults = HashMap<String, String>()
    private val overrides = HashMap<String, String>()
    private val relsCache = HashMap<String, List<OpcRelationship>>()

    /** [relsCache] 에 든 관계의 수. 관계 파일 하나는 [MAX_RELATIONSHIPS] 로 묶이지만 파일 수는 아니다. */
    private var cachedRelationships = 0
    private val warningList = ArrayList<ParseWarning>()

    /** 여는 동안 나온 경고. */
    override val warnings: List<ParseWarning> get() = warningList.toList()

    /** 부분 이름 전부(앞 `/` 없음, 퍼센트 푼 모양). */
    val partNames: Collection<String> get() = names.values

    init {
        try {
            for (e in reader.entries) {
                if (e.isDirectory) continue
                // 암호 걸린 ZIP 항목은 OOXML 이 쓰지 않는다(암호는 MS-OFFCRYPTO 로 건다).
                // 읽을 수 없는 항목은 없는 것으로 친다 — 필요한 부분이면 변환기가 '없다' 로 만난다.
                if (!e.isReadable) continue
                val key = OpcNames.key(e.name)
                if (key in byKey) {
                    warningList.add(ParseWarning(WARN_DUPLICATE, OpcNames.normalize(e.name)))
                    continue
                }
                byKey[key] = e
                names[key] = OpcNames.normalize(e.name)
            }
            readContentTypes()
        } catch (t: Throwable) {
            reader.close()
            wipeOnClose?.fill(0)
            throw t
        }
    }

    /** 이 부분이 있는가(대소문자 무관). */
    override fun has(name: String): Boolean = OpcNames.key(name) in byKey

    /** 바깥에 내보내는 이름으로 고친다(대소문자를 실제 항목에 맞춘다). 없으면 null. */
    override fun canonical(name: String): String? = names[OpcNames.key(name)]

    /**
     * 부분의 콘텐츠 형식. `Override` 가 이기고, 없으면 확장자의 `Default`.
     * `[Content_Types].xml` 이 없는 패키지도 오피스가 읽으므로 없으면 null 로 끝낸다.
     */
    override fun contentType(name: String): String? {
        val key = OpcNames.key(name)
        overrides[key]?.let { return it }
        val ext = key.substringAfterLast('/').substringAfterLast('.', "")
        return defaults[ext]
    }

    /**
     * 부분 하나를 읽는 스트림. 없으면 null. **부르는 쪽이 닫는다.** 엔트리별 상한(크기·압축비)이
     * 걸린 스트림이다.
     */
    fun open(name: String): InputStream? {
        val entry = byKey[OpcNames.key(name)] ?: return null
        // 부분 하나를 읽는 것이 '한 번의 작업' 이다. 총량을 되돌리지 않으면 세션 동안 그림을
        // 넘겨 보는 것만으로 1 GiB 에 걸린다(`EntryBudget` 의 주석).
        budget.resetOutput()
        return reader.open(entry)
    }

    /**
     * 부분 하나를 통째로. [max] 를 넘으면 null — 그림 하나가 수백 MB 면 정상이 아니다.
     * 선언된 크기를 믿지 않고 **실제로 읽은 바이트**를 센다.
     */
    override fun readBytes(name: String, max: Long): ByteArray? {
        val stream = open(name) ?: return null
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** XML 부분 하나에 물린 안전한 파서. 없으면 null. 부르는 쪽이 스트림을 닫아야 하므로 둘을 함께 준다. */
    fun parser(name: String): Pair<XmlPullParser, InputStream>? {
        val stream = open(name) ?: return null
        return try {
            SafeXml.newParser(stream, null, limits) to stream
        } catch (t: Throwable) {
            stream.close()
            throw t
        }
    }

    /**
     * 부분의 관계들. [source] 가 null 이면 패키지 관계(`_rels/.rels`).
     *
     * 관계 파일이 없거나 깨졌으면 빈 목록이다 — 관계 하나가 깨졌다고 문서를 못 여는 것은 아니다.
     * 다만 취소와 상한은 위로 던진다.
     */
    fun relationships(source: String?): List<OpcRelationship> {
        val relsName = relsNameOf(source)
        val key = OpcNames.key(relsName)
        relsCache[key]?.let { return it }
        val list = try {
            readRels(relsName, source)
        } catch (e: ParseLimitExceededException) {
            throw e
        } catch (e: java.io.InterruptedIOException) {
            throw e
        } catch (t: Exception) {
            warningList.add(ParseWarning(WARN_BROKEN_RELS, OpcNames.normalize(relsName)))
            emptyList()
        }
        // 관계 파일이 수천 개인 패키지는 정상이 아니지만 열 수는 있어야 한다. 쌓인 양이 넘치면 통째로
        // 비운다 — 다시 읽는 값은 들지만 기억이 끝없이 불지는 않는다(12단계 검토가 잡았다).
        if (cachedRelationships + list.size > MAX_CACHED_RELATIONSHIPS) {
            relsCache.clear()
            cachedRelationships = 0
        }
        relsCache[key] = list
        cachedRelationships += list.size
        return list
    }

    /** id 로 관계 하나. */
    fun relationship(source: String?, id: String): OpcRelationship? =
        relationships(source).firstOrNull { it.id == id }

    /** 유형 이름(`OpcRelationship.typeName`)으로 관계들. */
    fun relationshipsOfType(source: String?, typeName: String): List<OpcRelationship> =
        relationships(source).filter { it.typeName == typeName && !it.external }

    /**
     * 본문 부분(`officeDocument` 관계의 대상). 없으면 null.
     *
     * **패키지 관계를 따른다.** `word/document.xml` 이라는 이름은 관례일 뿐이고 명세가 정한 것은
     * 관계다 — 다른 이름을 쓰는 도구가 있다.
     */
    val mainDocument: String? by lazy {
        relationshipsOfType(null, "officeDocument").firstOrNull { has(it.target) }?.let { canonical(it.target) }
    }

    /** 매크로가 들어 있는가(`vbaProject.bin` 또는 매크로 콘텐츠 형식). **돌리지는 않는다.** */
    override val hasMacros: Boolean by lazy {
        names.values.any { it.substringAfterLast('/').equals("vbaProject.bin", ignoreCase = true) } ||
            (mainDocument?.let { contentType(it) }?.contains("macroEnabled", ignoreCase = true) == true)
    }

    override fun close() {
        reader.close()
        wipeOnClose?.fill(0)
    }

    // ---- 안쪽 -------------------------------------------------------------------

    private fun relsNameOf(source: String?): String {
        if (source == null) return "_rels/.rels"
        val n = OpcNames.normalize(source)
        val dir = OpcNames.dirOf(n)
        return dir + "_rels/" + n.substringAfterLast('/') + ".rels"
    }

    private fun readRels(relsName: String, source: String?): List<OpcRelationship> {
        val (p, stream) = parser(relsName) ?: return emptyList()
        stream.use {
            val out = ArrayList<OpcRelationship>()
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val type = p.getAttributeValue(null, "Type")
                    val target = p.getAttributeValue(null, "Target")
                    val external = p.getAttributeValue(null, "TargetMode").equals("External", ignoreCase = true)
                    if (id != null && type != null && target != null) {
                        if (external) {
                            out.add(OpcRelationship(id, type, target, true))
                        } else {
                            // 뿌리를 벗어나는 대상은 버린다(`OpcNames.resolve`).
                            OpcNames.resolve(source, target)?.let { out.add(OpcRelationship(id, type, it, false)) }
                        }
                    }
                    if (out.size > MAX_RELATIONSHIPS) break
                }
                event = p.nextGuarded(limits)
            }
            return out
        }
    }

    private fun readContentTypes() {
        val (p, stream) = parser("[Content_Types].xml") ?: run {
            warningList.add(ParseWarning(WARN_NO_CONTENT_TYPES, ""))
            return
        }
        stream.use {
            var event = p.eventType
            var count = 0
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "Default" -> {
                            val ext = p.getAttributeValue(null, "Extension")?.lowercase()
                            val type = p.getAttributeValue(null, "ContentType")
                            if (ext != null && type != null) defaults.putIfAbsent(ext, type)
                        }
                        "Override" -> {
                            val part = p.getAttributeValue(null, "PartName")
                            val type = p.getAttributeValue(null, "ContentType")
                            if (part != null && type != null) overrides.putIfAbsent(OpcNames.key(part), type)
                        }
                    }
                    if (++count > MAX_RELATIONSHIPS) return
                }
                event = p.nextGuarded(limits)
            }
        }
    }

    companion object {
        /** 같은 이름(대소문자 무관)의 부분이 둘 이상이다. detail 은 부분 이름. 화면이 문장을 고르도록 `format:api` 의 코드를 쓴다. */
        const val WARN_DUPLICATE = io.github.donggi.iroiroviewer.format.FlowWarnings.DUPLICATE_PART

        /** `[Content_Types].xml` 이 없다. 읽는 데는 지장이 없을 수 있다. */
        const val WARN_NO_CONTENT_TYPES = io.github.donggi.iroiroviewer.format.FlowWarnings.NO_CONTENT_TYPES

        /** 관계 파일 하나를 읽지 못했다. detail 은 그 파일 이름. */
        const val WARN_BROKEN_RELS = io.github.donggi.iroiroviewer.format.FlowWarnings.BROKEN_RELATIONSHIPS

        /** 관계 파일 하나·콘텐츠 형식 표의 항목 상한. 수만 개면 정상이 아니다. */
        private const val MAX_RELATIONSHIPS = 50_000

        /** 기억해 두는 관계의 총수. 큰 발표 자료도 슬라이드마다 수십 개라 수천 개면 넉넉하다. */
        private const val MAX_CACHED_RELATIONSHIPS = 200_000

        /**
         * 패키지를 연다. 실패하면(ZIP 이 아니다·상한) 던진다 — 여는이가 `toOpenFailure` 로 옮긴다.
         * 만든 리더는 이 객체가 가진다. 생성이 실패하면 스스로 닫는다.
         *
         * @param wipeOnClose 닫을 때(또는 여는 데 실패했을 때) 0 으로 덮을 바이트. **암호를 풀어 낸
         *   평문 패키지**를 넘긴다 — 평문은 메모리에만 두고(CLAUDE.md '암호가 걸린 파일'), 다 쓴 뒤에는
         *   GC 를 기다리지 않고 지운다. 암호를 `CharArray` 로 받아 지우는 것과 같은 판단이다.
         */
        fun open(
            source: DocumentSource,
            limits: ParseLimits = ParseLimits.DEFAULT,
            budget: EntryBudget = EntryBudget(limits),
            wipeOnClose: ByteArray? = null,
        ): OpcPackage {
            val reader = try {
                ZipArchiveReader(source, budget)
            } catch (t: Throwable) {
                wipeOnClose?.fill(0)
                throw zipFailure(t)
            }
            return OpcPackage(reader, budget, limits, wipeOnClose)
        }

        /** ZIP 의 겉(중앙 디렉터리)이 깨졌을 때의 [OpenFailure] 문장. 우리가 쓴 고정 문장이다. */
        internal const val CORRUPT_ZIP = "ZIP 구조가 깨졌다"

        /**
         * ZIP 리더가 여는 데 실패한 까닭을 옮긴다. 중앙 디렉터리를 못 읽는 것은 **깨진 파일**이다 —
         * commons-compress 는 그것을 `ZipException`(끝 레코드가 없다)이나 맨 `IOException`('중앙 디렉터리가
         * 비었다')으로 말하는데, 그대로 두면 `toOpenFailure` 가 '입출력이 실패했다' 로 옮겨 사용자가 저장소
         * 고장을 의심한다(Tika 의 잘린 docx 표본이 잡았다). 상한·취소·권한·사라진 파일은 그 뜻 그대로 둔다.
         */
        private fun zipFailure(t: Throwable): Throwable = when (t) {
            is ParseLimitExceededException, is java.io.InterruptedIOException,
            is java.nio.channels.ClosedByInterruptException, is java.io.FileNotFoundException,
            is java.nio.file.FileSystemException, is CorruptFormatException,
            -> t
            is java.io.IOException -> CorruptFormatException(CORRUPT_ZIP)
            else -> t
        }
    }
}
