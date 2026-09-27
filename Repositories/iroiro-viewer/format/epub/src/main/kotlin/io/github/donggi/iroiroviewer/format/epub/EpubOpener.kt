package io.github.donggi.iroiroviewer.format.epub

import io.github.donggi.iroiroviewer.format.DocumentOpener
import io.github.donggi.iroiroviewer.format.DocumentSource
import io.github.donggi.iroiroviewer.format.FormatId
import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.OpenOutcome
import io.github.donggi.iroiroviewer.format.OpenedDocument
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.ProgressSink
import io.github.donggi.iroiroviewer.format.archive.ArchiveEntry
import io.github.donggi.iroiroviewer.format.archive.ArchiveReader
import io.github.donggi.iroiroviewer.format.archive.ZipArchiveReader
import io.github.donggi.iroiroviewer.format.toOpenFailure
import io.github.donggi.iroiroviewer.safety.EntryBudget
import io.github.donggi.iroiroviewer.safety.ParseLimits
import java.util.concurrent.CancellationException
import java.util.zip.ZipException

/**
 * EPUB 을 연다.
 *
 * ## 순서가 곧 방어다
 *
 * 1. ZIP 으로 연다 — 엔트리 수·압축비·경로 탈출은 8단계의 리더가 이미 막는다.
 * 2. ZIP 자체에 암호가 걸린 엔트리가 하나라도 있으면 **거기서 끝낸다.** 복호화하지 않는다.
 * 3. `META-INF/encryption.xml` 이 있으면 **무엇이 어떤 방식으로 걸렸는지** 본다
 *    ([EpubEncryption]). 본문이 잠겼거나 모르면 끝내고, 글꼴 난독화면 풀 준비를 하고 계속한다
 *    ([FontObfuscation] — 방식이 공개돼 있다). 풀 수 없는 방식으로 걸린 글꼴은 알리고 계속한다.
 * 4. `META-INF/container.xml` → OPF → 매니페스트·차례.
 * 5. 차례가 비면 실패다. 읽을 것이 없는 책은 연 것이 아니다.
 *
 * ## `mimetype` 엔트리를 근거로 삼지 않는다
 *
 * 명세는 `mimetype` 이 **압축되지 않은 채 첫 엔트리**여야 한다고 적지만, 실물에는 그
 * 규칙을 어긴 파일이 흔하다(만드는 도구가 그냥 압축해 넣는다). 그것으로 거절하면
 * 멀쩡히 읽히는 책을 '깨졌다' 고 말하게 된다 — 8단계가 ZIP 엔트리 수 상계로 겪은
 * 형태다. **읽어서 다르면 경고만 남기고 계속한다.**
 */
class EpubOpener(
    private val limits: ParseLimits = ParseLimits.DEFAULT,
) : DocumentOpener {

    override val handles: Set<FormatId> = setOf(FormatId.EPUB)

    override suspend fun open(
        source: DocumentSource,
        progress: ProgressSink,
        onOpen: (OpenedDocument) -> Unit,
    ): OpenOutcome {
        var reader: ArchiveReader? = null
        var book: EpubBook? = null
        try {
            reader = ZipArchiveReader(source, EntryBudget(limits), FormatId.EPUB)
            val entries = reader.entries
            // **ZIP 암호는 '암호로는 열 수 없는 방식' 이 아니다.** 공개된 암호 방식인데 EPUB 에서
            // 아직 암호를 받지 않을 뿐이다. `Encrypted`('암호로는 열 수 없다')로 말하면 사용자가
            // 모르는 열쇠를 찾아 헤맨다(적대적 검토가 잡았다) — '다루지 않는다' 로 끝낸다.
            if (entries.any { it.isEncrypted }) {
                return OpenOutcome.Failed(OpenFailure.Unsupported("ZIP 암호가 걸린 EPUB"))
            }
            progress.report(1, 4)

            val warnings = ArrayList<ParseWarning>()
            val byPath = index(entries, warnings)

            // **`encryption.xml` 은 DRM 일 수도 글꼴 난독화일 수도 있다**(그 파일의 주석).
            // 본문이 걸렸거나 무엇이 걸렸는지 모르면 거기서 끝낸다. 열쇠는 OPF 를 읽은 뒤에 만든다
            // (고유 식별자가 거기 있다).
            val encryption = byPath[ENCRYPTION]
            var plan: EpubEncryption.Plan.Open? = null
            if (encryption != null) {
                val result = reader.open(encryption).use { EpubEncryption.parse(it, limits) }
                plan = when (val p = EpubEncryption.plan(result)) {
                    is EpubEncryption.Plan.Open -> p
                    EpubEncryption.Plan.Refuse ->
                        return OpenOutcome.Failed(OpenFailure.Encrypted("풀 수 없는 방식으로 잠긴 자원"))
                }
            }

            val mimetype = byPath["mimetype"]
            if (mimetype != null) {
                val declared = reader.open(mimetype).use { it.readBytes() }
                    .toString(Charsets.US_ASCII).trim()
                if (declared != EPUB_MIME) {
                    warnings.add(ParseWarning(WARN_MIMETYPE, "mimetype 이 $declared 다"))
                }
            } else {
                warnings.add(ParseWarning(WARN_MIMETYPE, "mimetype 항목이 없다"))
            }

            val container = byPath[CONTAINER]
                ?: return OpenOutcome.Failed(OpenFailure.Corrupt("container.xml 이 없다"))
            val opfPath = reader.open(container).use { EpubPackage.rootFile(it, limits) }
                ?: return OpenOutcome.Failed(OpenFailure.Corrupt("OPF 를 가리키지 않는다"))
            progress.report(2, 4)

            val opf = byPath[opfPath]
                ?: return OpenOutcome.Failed(OpenFailure.Corrupt("OPF 가 없다"))
            val pkg = reader.open(opf).use { EpubPackage.parse(it, opfPath, limits) }
            progress.report(3, 4)

            // 난독화된 글꼴마다 열쇠를 만든다. 만들 수 없으면(식별자가 없거나 UUID 가 아니다)
            // 그 글꼴은 풀지 못한 것으로 센다 — 본문은 멀쩡하므로 책은 연다.
            //
            // **글꼴이 아니면 연다고 하지 않는다.** 난독화 방식이 본문·그림에 걸려 있는데 열쇠를 못
            // 만들면, 앞 1024·1040 바이트가 뒤섞인 채로 화면에 간다 — 깨진 글자 위에 '글꼴을 못
            // 풀었다' 는 안내가 뜬다(검토가 잡았다). 그때는 잠긴 책으로 끝낸다.
            val masks = HashMap<String, FontObfuscation.Mask>()
            var lockedFonts = plan?.lockedFonts ?: 0
            for ((path, algorithm) in plan?.obfuscated.orEmpty()) {
                val mask = FontObfuscation.maskFor(algorithm, pkg.uniqueIdentifier, pkg.identifiers)
                when {
                    mask != null -> masks[path] = mask
                    EpubEncryption.isFont(path) -> lockedFonts++
                    else -> return OpenOutcome.Failed(OpenFailure.Encrypted("풀 수 없는 방식으로 잠긴 자원"))
                }
            }
            if (lockedFonts > 0) {
                warnings.add(ParseWarning(WARN_OBFUSCATED, "글꼴 ${lockedFonts}개를 풀지 못했다"))
            }

            // **문서를 만든 자리에서 건넨다.** 아래에서 실패로 끝내더라도 호출자의
            // `finally` 가 닫을 수 있다(`OpenedDocument` 의 주석).
            val made = EpubBook(reader, pkg, byPath, warnings, limits, masks)
            book = made
            onOpen(made)

            if (made.spine.isEmpty()) {
                made.close()
                return OpenOutcome.Failed(OpenFailure.Corrupt("읽을 차례가 없다"))
            }
            progress.report(4, 4)
            reader = null // 주인이 책으로 넘어갔다
            book = null
            return OpenOutcome.Success(made)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ZipException) {
            // **ZIP 이 깨졌으면 '입출력 실패' 가 아니라 '깨진 파일' 이다.** `ZipException` 은 `IOException` 을
            // 이어 공용 `toOpenFailure()` 가 `Io` 로 옮기는데, 그러면 덜 받은 책·ZIP 이 아닌 파일이
            // '입출력이 실패했습니다' 로 떠 **디스크가 고장 난 것처럼** 읽힌다(PDF·CFB 가 같은 이유로 따로
            // 가린다 — CLAUDE.md). 실세계 말뭉치를 반으로 자른 책이 전부 이 길로 갔다(검토가 잡았다).
            // 여기 오는 것은 중앙 디렉터리가 없거나 압축이 깨진 경우다 — 진짜 입출력 오류는 `ZipException` 이 아니다.
            return OpenOutcome.Failed(OpenFailure.Corrupt("ZIP 구조가 깨졌다"))
        } catch (t: Throwable) {
            return OpenOutcome.Failed(t.toOpenFailure())
        } finally {
            // 성공 경로에서는 위에서 null 로 바꿔 두었다. 실패·취소로 버려진 것만 닫는다.
            book?.close()
            reader?.close()
        }
    }

    /**
     * 이름 → 엔트리. **같은 이름이 둘이면 먼저 나온 것을 쓰고 그 사실을 적는다.**
     *
     * ZIP 명세가 같은 이름을 금지하지 않으므로 이것은 깨진 파일이 아니라 **있을 수 있는
     * 파일**이다. 조용히 고르면 사용자가 보는 그림이 왜 그것인지 아무도 모른다.
     */
    private fun index(
        entries: List<ArchiveEntry>,
        warnings: MutableList<ParseWarning>,
    ): Map<String, ArchiveEntry> {
        val out = LinkedHashMap<String, ArchiveEntry>(entries.size)
        var duplicates = 0
        for (entry in entries) {
            if (entry.isDirectory) continue
            // **엔트리 이름을 그대로 키로 쓰지 않는다.** `./OEBPS/a.xhtml` 과
            // `OEBPS/a.xhtml` 은 같은 것을 가리키는데 문자열로는 다르다.
            val key = EpubHref.resolve("", entry.name) ?: continue
            if (out.putIfAbsent(key, entry) != null) duplicates++
        }
        if (duplicates > 0) {
            warnings.add(ParseWarning(WARN_DUPLICATE, "같은 이름의 항목 ${duplicates}개"))
        }
        return out
    }

    companion object {
        const val WARN_MIMETYPE = "mimetype"
        const val WARN_DUPLICATE = "duplicate"
        /** 풀 수 없는 방식으로 걸린 글꼴이 있다. 그 글꼴은 기본 글꼴로 대신 보인다. */
        const val WARN_OBFUSCATED = "obfuscated"

        private const val EPUB_MIME = "application/epub+zip"
        private const val CONTAINER = "META-INF/container.xml"
        private const val ENCRYPTION = "META-INF/encryption.xml"
    }
}
