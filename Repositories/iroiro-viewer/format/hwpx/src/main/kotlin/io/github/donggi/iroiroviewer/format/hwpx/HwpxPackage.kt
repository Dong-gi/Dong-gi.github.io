package io.github.donggi.iroiroviewer.format.hwpx

import io.github.donggi.iroiroviewer.format.CorruptFormatException
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ZipArchiveReader
import io.github.donggi.iroiroviewer.format.html.FlowPackage
import io.github.donggi.iroiroviewer.format.html.HancomChars
import io.github.donggi.iroiroviewer.safety.DecryptLimits
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimitExceededException
import io.github.donggi.iroiroviewer.safety.ParseLimits
import io.github.donggi.iroiroviewer.safety.SafeXml
import io.github.donggi.iroiroviewer.safety.nextGuarded
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException

/**
 * `content.hpf` 매니페스트의 항목 하나(`opf:item`).
 *
 * @param name 패키지 안의 이름(푼 모양). 패키지에 없으면(바깥을 가리키거나 빠졌다) null.
 * @param embedded `isEmbeded="1"`(한컴의 철자다). 한글은 그림을 이것 없이 두면 그리지 않는다(조사 노트).
 */
internal class HpfItem(val id: String, val href: String, val name: String?, val mediaType: String?, val embedded: Boolean)

/** 암호를 넣어 본 결과. */
internal sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data object WrongPassword : UnlockResult

    /** 풀 줄 모르는 방식. [reason] 은 우리가 쓴 짧은 말(진단용). */
    data class Unsupported(val reason: String) : UnlockResult
}

/**
 * HWPX 패키지 하나 — ZIP 과 OWPML 의 매니페스트(KS X 6101).
 *
 * ## 여는 차례
 *
 * 1. `mimetype` — `application/hwp+zip` 이 아니면서 **분명히 다른 포맷**(EPUB·ODF·OOXML)을 말하면 깨진 것으로 본다.
 *    없거나 모르는 값이면 넘어간다(조사 노트: 이 항목이 없어도 한글은 연다).
 * 2. `META-INF/container.xml` 의 `rootfile`(`application/hwpml-package+xml`) → 보통 `Contents/content.hpf`.
 * 3. `META-INF/manifest.xml` — 평문 문서는 빈 요소다. 암호 문서는 항목마다 `encryption-data` 가 있다.
 * 4. [loadContents] — `content.hpf` 의 `opf:manifest`(id → href·media-type)와 `opf:spine`(구역의 차례).
 *    암호 문서는 [unlock] 한 뒤에 부른다(한컴은 `content.hpf` 를 잠그지 않지만, 잠갔다면 암호가 먼저다).
 *
 * `content.hpf` 가 없거나 깨졌으면 `Contents/section0.xml`·`section1.xml`… 을 **번호 차례로** 찾고
 * [FlowWarnings.NO_CONTENT_TYPES] 를 남긴다. `META-INF/manifest.xml` 이 없는 것은 문제가 아니다 — hwpxlib 의
 * `no_manifest.hwpx`(O14)가 그렇고, 평문 문서에서는 어차피 비어 있다.
 *
 * ## 이름
 *
 * [FlowPackage] 의 한 모양(앞 `/` 없음, 퍼센트 푼 것). 찾을 때 대소문자를 가리지 않는다. 같은 이름(대소문자
 * 무관)의 항목이 둘이면 먼저 나온 것을 쓰고 [FlowWarnings.DUPLICATE_PART] 를 남긴다.
 *
 * ## 스레드
 *
 * 스레드 안전하지 않다. `FlowDocumentBase` 가 모든 접근을 한 잠금으로 세운다.
 */
internal class HwpxPackage private constructor(
    private val reader: ZipArchiveReader,
    private val budget: EntryBudget,
    private val limits: ParseLimits,
) : FlowPackage {

    private val byKey = HashMap<String, ArchiveEntry>()
    private val names = HashMap<String, String>()
    private val warningList = ArrayList<ParseWarning>()

    /** 루트 파일(`content.hpf`)의 이름. */
    private var rootFile: String = DEFAULT_ROOT

    private var odf: OdfManifest = OdfManifest.EMPTY
    private var decryptor: HwpxDecryptor? = null

    private val items = LinkedHashMap<String, HpfItem>()
    private val mediaTypes = HashMap<String, String>()

    /** 구역 부분들, 스파인 차례. */
    var sections: List<String> = emptyList()
        private set

    /** 머리(`header.xml`). 없으면 null. */
    var header: String? = null
        private set

    /** `settings.xml`. */
    var settings: String? = null
        private set

    /** `Preview/PrvText.txt`. 암호 문서는 이것도 잠겨 있다(O16). */
    var preview: String? = null
        private set

    /** `content.hpf` 의 `opf:title`. */
    var title: String = ""
        private set

    override val warnings: List<ParseWarning> get() = warningList.toList()

    override var hasMacros: Boolean = false
        private set

    /** 암호가 걸린 항목이 있다(ZIP 에 실제로 있는 것만 센다). */
    val encrypted: Boolean get() = odf.entries.keys.any { it in byKey }

    /** 암호 매니페스트에 우리가 풀 줄 모르는 방식이 있다. */
    val encryptionUnsupported: String? get() = odf.unsupported?.takeIf { encrypted || odf.entries.isEmpty() }

    init {
        try {
            for (e in reader.entries) {
                if (e.isDirectory || !e.isReadable) continue
                val key = HwpxNames.key(e.name)
                if (key in byKey) {
                    warningList.add(ParseWarning(FlowWarnings.DUPLICATE_PART, HwpxNames.normalize(e.name)))
                    continue
                }
                byKey[key] = e
                names[key] = HwpxNames.normalize(e.name)
            }
            checkMimetype()
            readContainer()
            readOdfManifest()
            hasMacros = names.values.any { it.startsWith("Scripts/", ignoreCase = true) }
        } catch (t: Throwable) {
            reader.close()
            throw t
        }
    }

    override fun has(name: String): Boolean = HwpxNames.key(name) in byKey

    override fun canonical(name: String): String? = names[HwpxNames.key(name)]

    /** 매니페스트가 말하는 MIME. 없으면 null(확장자로 짐작한다). */
    override fun contentType(name: String): String? = mediaTypes[HwpxNames.key(name)]

    /** id 로 매니페스트 항목(그림의 `binaryItemIDRef` 가 가리킨다). */
    fun item(id: String): HpfItem? = items[id]

    /**
     * 항목이 **적어 둔** 크기(평문). 잠긴 항목은 암호 매니페스트의 `size`, 아니면 ZIP 의 것. 모르면 -1.
     * 공격자가 적는 값이라 읽기의 상한으로 쓰지 않는다 — 어차피 못 내줄 것을 미리 거르는 데만 쓴다.
     */
    fun declaredSize(name: String): Long {
        val key = HwpxNames.key(name)
        val entry = byKey[key] ?: return -1
        odf.entries[key]?.let { return it.declaredSize }
        return entry.declaredSize
    }

    /**
     * **XML 부분** 하나를 읽는 스트림. 없으면 null. **부르는 쪽이 닫는다.** 잠긴 항목은 풀어서 주고, 닫을 때
     * 평문을 0 으로 덮는다.
     *
     * 잠긴 항목은 평문을 통째로 메모리에 푼다. 그래서 **XML 파서의 입력 상한(`maxXmlBytes`)을 한 바이트 넘는 데까지만**
     * 푼다 — 파서는 그 바이트에서 평문 항목과 똑같이 멈추고(앞부분은 그려진다), 그 뒤를 푸는 것은 기억만 쓴다.
     */
    fun open(name: String, checkCancel: () -> Unit = {}): InputStream? {
        val key = HwpxNames.key(name)
        val entry = byKey[key] ?: return null
        budget.resetOutput()
        val enc = odf.entries[key] ?: return reader.open(entry)
        val plain = decryptEntry(entry, enc, minOf(plainCap(), limits.maxXmlBytes + 1), checkCancel)
        return WipingInputStream(plain)
    }

    /**
     * 항목을 통째로. [max] 를 넘으면 null(평문 항목의 `readAll` 과 같다). 잠긴 항목도 **[max] 를 한 바이트 넘는 데까지만**
     * 푼다 — 다 푼 뒤에 크기를 보면 32 MB 짜리 그림 하나를 청하는 데 평문 상한(수백 MB)을 쓴다.
     */
    override fun readBytes(name: String, max: Long): ByteArray? {
        val key = HwpxNames.key(name)
        val entry = byKey[key] ?: return null
        budget.resetOutput()
        val enc = odf.entries[key]
        if (enc != null) {
            val cap = minOf(max, plainCap())
            // 그리기 쪽(WebView 의 스레드)에서 온다. 취소는 스레드 인터럽트로만 온다.
            val plain = decryptEntry(entry, enc, cap + 1, ::checkInterrupted)
            if (plain.size > cap) {
                plain.fill(0)
                if (cap < max) throw ParseLimitExceededException("maxSingleOutput", "암호를 푼 평문이 ${cap}바이트를 넘는다")
                return null
            }
            return plain
        }
        return readAll(reader.open(entry), max)
    }

    /** XML 부분 하나에 물린 안전한 파서. 부르는 쪽이 스트림을 닫아야 하므로 둘을 함께 준다. */
    fun parser(name: String, checkCancel: () -> Unit = {}): Pair<XmlPullParser, InputStream>? {
        val stream = open(name, checkCancel) ?: return null
        return try {
            SafeXml.newParser(stream, null, limits) to stream
        } catch (t: Throwable) {
            stream.close()
            throw t
        }
    }

    /**
     * 암호를 넣어 본다. 맞으면 이 뒤의 읽기가 잠긴 항목을 풀어서 준다.
     *
     * **암호문이 가장 작은 잠긴 항목 하나**의 앞부분을 풀어 검증값까지 확인한다([HwpxDecryptor.verify]) — 틀린 열쇠는
     * 거의 언제나 deflate 의 첫 블록에서 드러나지만, 검증값이 있으면 그것으로 확정한다. 그 항목이 깨졌을 뿐이라면
     * '틀렸다' 로 나간다(가를 방법이 없다).
     *
     * 암호문이 한 블록(16 바이트)도 안 되는 항목은 고르지 않는다 — 빈 평문(`size="0"`)은 **어떤 열쇠로도 풀리므로**
     * 그것으로 확인하면 틀린 암호가 통과한다. 검증값이 있는 항목을 먼저 고른다.
     */
    fun unlock(password: CharArray, checkCancel: () -> Unit): UnlockResult {
        odf.unsupported?.let { return UnlockResult.Unsupported(it) }
        val present = odf.entries.filterKeys { it in byKey }
        if (present.isEmpty()) return UnlockResult.Unlocked
        decryptor?.close()
        val d = HwpxDecryptor.create(password, present.values.mapTo(HashSet()) { it.startKeyAlgorithm })
        decryptor = d
        val candidates = present.entries.filter { byKey[it.key]!!.compressedSize >= CIPHER_BLOCK }
        if (candidates.isEmpty()) return UnlockResult.Unlocked
        val (key, probe) = candidates.minWithOrNull(
            compareBy<Map.Entry<String, EncryptedEntry>> { it.value.checksumAlgorithm == null }
                .thenBy { byKey[it.key]!!.compressedSize },
        )!!
        // 확인은 평문의 앞부분만 푼다 — 암호문도 앞부분만 읽는다(AES-CBC 는 앞의 블록만으로 앞을 푼다).
        val cipher = readPrefix(reader.open(byKey[key]!!), VERIFY_CIPHER_BYTES)
        val ok = d.verify(probe, cipher, checkCancel)
        if (!ok) {
            d.close()
            decryptor = null
            return UnlockResult.WrongPassword
        }
        return UnlockResult.Unlocked
    }

    /**
     * `content.hpf` 를 읽는다. 없거나 깨졌으면 이름으로 짐작하고 경고를 남긴다. 취소·상한은 위로 던진다.
     */
    fun loadContents(checkCancel: () -> Unit = {}) {
        val ok = try {
            readRootFile(checkCancel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            warningList.add(ParseWarning(FlowWarnings.NO_CONTENT_TYPES, ""))
            items.clear()
            mediaTypes.clear()
            title = ""
        }
        if (sections.isEmpty()) sections = sectionsByName(names.values)
        if (header == null) header = canonical(DEFAULT_HEADER)
        if (settings == null) settings = canonical(DEFAULT_SETTINGS)
        preview = canonical(DEFAULT_PREVIEW)
        if (!hasMacros) hasMacros = items.values.any { it.mediaType?.contains("javascript", ignoreCase = true) == true }
    }

    override fun close() {
        try {
            reader.close()
        } finally {
            decryptor?.close()
            decryptor = null
        }
    }

    // ---- 안쪽 -------------------------------------------------------------------

    /** 평문 한 벌의 상한 — 엔트리 하나의 해제 상한과 RAM 에 드는 평문의 상한 가운데 작은 것. */
    private fun plainCap(): Long = minOf(limits.maxSingleOutput, DecryptLimits.MAX_BYTES)

    /**
     * 잠긴 항목의 평문을 **앞의 [cap] 바이트까지만** 푼다(더 길면 거기서 자른다 — 넘었는지는 부르는 쪽이 길이로 본다).
     *
     * 암호문도 그만큼만 읽는다 — 평문 [cap] 바이트를 deflate 한 것은 [cap] 을 조금만 넘는다(저장 블록의 머리가
     * 64 KiB 마다 5 바이트). 그보다 긴 암호문의 뒤쪽은 [cap] 을 넘는 평문이라 읽을 까닭이 없다. AES-CBC 는 앞의
     * 블록만으로 앞의 평문을 푼다.
     */
    private fun decryptEntry(entry: ArchiveEntry, enc: EncryptedEntry, cap: Long, checkCancel: () -> Unit): ByteArray {
        val d = decryptor ?: throw CorruptFormatException("잠긴 항목이다")
        val cipherCap = minOf(cap + (cap shr 6) + CIPHER_SLACK, plainCap())
        val cipher = readPrefix(reader.open(entry), cipherCap)
        return d.decrypt(enc, cipher, cap, truncate = true, checkCancel = checkCancel)
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("취소")
    }

    private fun checkMimetype() {
        val entry = byKey[HwpxNames.key(MIMETYPE)] ?: return
        val bytes = readAll(reader.open(entry), HwpxLimits.MAX_MIMETYPE_BYTES) ?: return
        val value = String(bytes, Charsets.UTF_8).trim().lowercase()
        if (value == HWPX_MIME) return
        // 다른 포맷이라고 분명히 말하는 것만 거절한다. 모르는 값(`.owpml` 의 것일 수도 있다)은 넘어간다.
        if (FOREIGN_MIMES.any { value.startsWith(it) }) throw CorruptFormatException("HWPX 가 아니다")
    }

    private fun readContainer() {
        val (p, stream) = parser(CONTAINER) ?: return
        var chosen: String? = null
        var fallback: String? = null
        try {
            stream.use {
                if (!HwpxXml.toRoot(p, limits)) return
                var count = 0
                walkElements(p) { name ->
                    if (name == "rootfile") {
                        val path = HwpxXml.attr(p, "full-path")
                        val type = HwpxXml.attr(p, "media-type")
                        if (path != null) {
                            if (chosen == null && type.equals(HWPML_PACKAGE, ignoreCase = true)) chosen = path
                            if (fallback == null && path.endsWith(".hpf", ignoreCase = true)) fallback = path
                        }
                    }
                    ++count < HwpxLimits.MAX_MANIFEST_ITEMS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            throw e
        } catch (e: Exception) {
            // 컨테이너가 깨져도 관례의 자리(`Contents/content.hpf`)를 찾아본다.
        }
        (chosen ?: fallback)?.let { rootFile = HwpxNames.normalize(it) }
    }

    private fun readOdfManifest() {
        if (!has(ODF_MANIFEST)) return
        odf = try {
            val (p, stream) = parser(ODF_MANIFEST) ?: return
            stream.use { OdfManifestReader.parse(p, limits) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: ParseLimitExceededException) {
            throw e
        } catch (e: Exception) {
            // 평문 문서의 매니페스트는 비어 있다. 깨졌으면 잠긴 항목이 있는지 알 수 없다 — 말은 해 둔다.
            warningList.add(ParseWarning(FlowWarnings.AUX_FAILED, ODF_MANIFEST))
            OdfManifest.EMPTY
        }
    }

    /** `content.hpf` 를 읽는다. 없으면 거짓. */
    private fun readRootFile(checkCancel: () -> Unit): Boolean {
        val root = canonical(rootFile) ?: canonical(DEFAULT_ROOT) ?: return false
        val (p, stream) = parser(root, checkCancel) ?: return false
        val base = HwpxNames.dirOf(root)
        val spine = ArrayList<String>()
        stream.use {
            if (!HwpxXml.toRoot(p, limits)) return false
            var count = 0
            walkElements(p) { name ->
                when (name) {
                    "title" -> if (title.isEmpty()) {
                        title = displayTitle(HwpxXml.collectText(p, limits, HwpxLimits.MAX_TITLE_CHARS))
                    }
                    "item" -> addItem(p, base)
                    "itemref" -> HwpxXml.attr(p, "idref")?.let { spine.add(it) }
                }
                if (++count and 255 == 0) checkCancel()
                count < HwpxLimits.MAX_MANIFEST_ITEMS
            }
        }
        val fromSpine = ArrayList<String>()
        val seen = HashSet<String>()
        for (id in spine) {
            val it = items[id] ?: continue
            val n = it.name ?: continue
            if (isSection(it.id, n) && seen.add(HwpxNames.key(n))) fromSpine.add(n)
            if (fromSpine.size >= HwpxLimits.MAX_SECTIONS) break
        }
        sections = fromSpine.ifEmpty {
            // 스파인이 비었거나 구역을 적지 않았다. 매니페스트의 구역 항목을 번호 차례로.
            sectionsByName(items.values.filter { it.name != null && isSection(it.id, it.name) }.mapNotNull { it.name })
        }
        header = items["header"]?.name ?: items.values.firstOrNull { it.name?.endsWith("header.xml", true) == true }?.name
        settings = items["settings"]?.name
        return true
    }

    private fun addItem(p: XmlPullParser, base: String) {
        val id = HwpxXml.attr(p, "id") ?: return
        val href = HwpxXml.attr(p, "href") ?: return
        if (id in items) return
        // 한컴은 `content.hpf` 가 `Contents/` 에 있어도 **뿌리 기준**으로 적는다(`Contents/header.xml`).
        // OPF 의 관례(루트 파일 기준)도 받는다 — 먼저 뿌리 기준으로 찾고, 없으면 루트 파일의 폴더 기준으로.
        val rootRelative = HwpxNames.resolve("", href)
        val name = rootRelative?.let { canonical(it) } ?: HwpxNames.resolve(base, href)?.let { canonical(it) }
        val type = HwpxXml.attr(p, "media-type")?.trim()?.takeIf { it.isNotEmpty() }
        val embedded = HwpxXml.attr(p, "isEmbeded")?.trim()?.let { it == "1" || it.equals("true", true) } ?: true
        items[id] = HpfItem(id, href, name, type, embedded)
        if (name != null && type != null) mediaTypes.putIfAbsent(HwpxNames.key(name), type)
    }

    /**
     * 뿌리 아래 모든 요소를 문서 차례로 [onElement] 에 준다. [onElement] 는 요소를 **소비하지 않아도** 된다
     * (글자를 모으면 끝 태그에 서서 돌아오고, 그러지 않으면 시작 태그에 선 채로 돌아온다 — 둘 다 이어 간다).
     * 거짓을 돌려주면 멈춘다.
     */
    private inline fun walkElements(p: XmlPullParser, onElement: (String) -> Boolean) {
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if (!onElement(p.name)) return
            }
            ev = p.nextGuarded(limits)
        }
    }

    private fun readAll(stream: InputStream, max: Long): ByteArray? {
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

    /** 앞의 [max] 바이트(항목이 더 짧으면 전부). */
    private fun readPrefix(stream: InputStream, max: Long): ByteArray {
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (total < max) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
                if (n < 0) break
                total += n
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    companion object {
        const val HWPX_MIME = "application/hwp+zip"

        /** AES 블록. 이보다 짧은 암호문은 평문이 없다. */
        private const val CIPHER_BLOCK = 16L

        /** 평문 상한에서 암호문 상한을 잡을 때 더하는 여유(deflate 의 블록 머리·AES 채움). */
        private const val CIPHER_SLACK = 64L * 1024

        /** 암호를 확인할 때 읽는 암호문의 앞부분. 확인이 푸는 평문(64 KiB)을 넉넉히 덮는다. */
        private const val VERIFY_CIPHER_BYTES = 256L * 1024
        private const val HWPML_PACKAGE = "application/hwpml-package+xml"
        private const val MIMETYPE = "mimetype"
        private const val CONTAINER = "META-INF/container.xml"
        private const val ODF_MANIFEST = "META-INF/manifest.xml"
        private const val DEFAULT_ROOT = "Contents/content.hpf"
        private const val DEFAULT_HEADER = "Contents/header.xml"
        private const val DEFAULT_SETTINGS = "settings.xml"
        private const val DEFAULT_PREVIEW = "Preview/PrvText.txt"
        private val SPACES = Regex("\\s+")
        private val SECTION_NAME = Regex("(?i)(^|/)section(\\d+)\\.xml$")

        /** 분명히 다른 포맷이라고 말하는 `mimetype`. */
        private val FOREIGN_MIMES = listOf(
            "application/epub", "application/vnd.oasis.opendocument", "application/vnd.openxmlformats",
            "application/vnd.ms-", "application/zip",
        )

        /**
         * `opf:title` 을 화면의 제목으로 쓸 수 있는 모양으로. 쓸 수 없으면 빈 문자열 — 화면이 파일 이름으로 채운다.
         *
         * 한글의 '문서 정보' 제목은 서식 파일에서 물려받은 찌꺼기가 흔하다. 실물 열 가운데 여섯이 제목을 적었는데 K26 은
         * `3`, S08 은 `1111` 이고, O16 은 `U+F53A` + `글 97 안내문`(한컴 글꼴의 사설 영역 글자 — 기기에는 그 글꼴이 없어
         * 두부로 보인다)이다. **글자(letter)가 하나도 없는 것과 사설 영역 글자가 든 것은 버린다** — 파일 이름보다 나을 수
         * 없다. 그 밖의 찌꺼기(`통계청` 같은 기관 이름)는 가려낼 수 없어 그대로 둔다.
         */
        fun displayTitle(raw: String): String {
            val t = raw.trim().replace(SPACES, " ")
            var letters = false
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                if (HancomChars.isPrivateUse(cp)) return ""
                if (Character.isLetter(cp)) letters = true
                i += Character.charCount(cp)
            }
            return if (letters) t else ""
        }

        private fun isSection(id: String, name: String): Boolean =
            SECTION_NAME.containsMatchIn(name) || id.startsWith("section", ignoreCase = true) && name.endsWith(".xml", true)

        /** 이름이 `sectionN.xml` 인 것들을 번호 차례로. */
        fun sectionsByName(all: Collection<String>): List<String> =
            all.mapNotNull { n ->
                val m = SECTION_NAME.find(n) ?: return@mapNotNull null
                if (!n.startsWith("Contents/", ignoreCase = true) && '/' in n) return@mapNotNull null
                val num = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                num to n
            }.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }.distinct().take(HwpxLimits.MAX_SECTIONS)

        /**
         * 패키지를 연다. ZIP 이 아니거나 분명히 다른 포맷이면 던진다. 만든 리더는 이 객체가 가지고, 생성이
         * 실패하면 스스로 닫는다.
         */
        fun open(source: DocumentSource, limits: ParseLimits = ParseLimits.DEFAULT): HwpxPackage {
            val budget = EntryBudget(limits)
            val reader = try {
                ZipArchiveReader(source, budget, FormatId.HWPX)
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: ParseLimitExceededException) {
                throw e
            } catch (e: CorruptFormatException) {
                throw e
            } catch (e: java.io.IOException) {
                // 앞부분(`PK`)은 이미 읽었다 — 여기서 ZIP 이 열리지 않는 것은 거의 언제나 잘리거나 깨진 파일이다.
                // 공용 `toOpenFailure` 에 맡기면 '입출력이 실패했다' 가 되어 사용자가 저장소 고장을 의심한다
                // (PDF 의 실패 매핑과 같은 판단). 진짜 입출력 실패(떼어 낸 SD)도 이리로 오는 것이 대가다.
                throw CorruptFormatException("ZIP 구조가 깨졌다")
            }
            return HwpxPackage(reader, budget, limits)
        }
    }
}

/** 닫을 때 버퍼를 0 으로 덮는 스트림. 암호를 푼 평문을 파서에 물릴 때 쓴다. */
internal class WipingInputStream(private val buffer: ByteArray) : ByteArrayInputStream(buffer) {
    override fun close() {
        buffer.fill(0)
        super.close()
    }
}
