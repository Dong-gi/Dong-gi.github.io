package io.github.donggi.iroiroviewer.format.archive

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer

/**
 * tar 머리를 **하나씩** 읽는다.
 *
 * ## commons-compress 의 `TarArchiveInputStream` 을 쓰지 않는 이유
 *
 * 1. **메타 머리를 끝까지 메모리에 모은다.** GNU 긴 이름은 머리에 적힌 크기만큼 `ByteArrayOutputStream` 에 쌓고,
 *    PAX 값도 적힌 길이만큼 배열을 잡는다. 크기는 공격자가 적는 값이다 — 1 GiB 라고 적은 긴 이름 하나에 앱이 죽는다.
 *    여기서는 [TarLimits] 로 묶는다.
 * 2. **이름의 원본 바이트를 주지 않는다.** 이름을 기기 기본 인코딩으로 푼 문자열만 준다. 한국어 윈도우 도구가
 *    CP949 로 적은 이름을 가려낼 길이 없다. 여기서는 바이트를 넘겨 ZIP 과 같은 판정([EntryNameDecoder])을 탄다.
 * 3. **링크의 자료 크기를 믿는다.** POSIX ustar 는 링크·장치·폴더에는 자료 레코드가 없다고 적는데, 크기 칸을
 *    채워 두는 도구가 있다. 그 크기만큼 건너뛰면 다음 머리를 놓친다.
 *
 * 머리 하나는 512바이트라 읽는 쪽은 짧다. 대신 판단이 전부 여기 있다. 자리 세기·건너뛰기·상한은 cpio·ar 와 함께 쓰는
 * [EntryScannerBase] 에 있다.
 *
 * ## 쓰는 법
 *
 * [next] 로 머리를 얻고, 필요하면 [data] 로 자료를 읽는다. 다 읽지 않아도 된다 — 다음 [next] 가 남은 자료와
 * 채움을 건너뛴다. 그 건너뛰기는 [ScanSource] 에 따라 공짜(자리 옮기기)이거나 풀어서 버리기다.
 *
 * @param scanCap tar 스트림에서 소비할 수 있는 최대 바이트(머리·자료·채움 전부). 압축한 tar 에서 한 항목에 닿거나
 *   목록을 만드는 비용의 상한이다.
 * @param onDrain 건너뛰느라 **읽어서 버린** 바이트. 순차 추출이 예산에 올린다(solid 7z 와 같은 규칙).
 */
internal class TarScanner(
    src: ScanSource,
    scanCap: Long = Long.MAX_VALUE,
    onDrain: (Long) -> Unit = {},
) : EntryScannerBase(src, scanCap, onDrain, "tarHeader") {

    /** 하드링크의 크기 칸을 판단하려고 미리 읽은 머리 한 칸. */
    private var pending: ByteArray? = null

    /**
     * 다음 항목. 끝이면 null.
     *
     * ## 종류마다 자료가 있는가
     *
     * | 종류 | 자료 |
     * |---|---|
     * | `0`·NUL·`7`·모르는 글자 | 크기만큼. POSIX 는 모르는 종류를 **보통 파일로** 읽으라고 적는다 |
     * | `5`(폴더)·`2`(심볼릭 링크)·`3`·`4`·`6`(장치·FIFO) | **없다.** 크기 칸은 무시한다 |
     * | `1`(하드링크) | PAX 머리가 있으면 크기만큼(pax 는 하드링크에 본문을 허락한다), 없으면 아래 판단 |
     * | `D`(GNU 폴더 목록) | 크기만큼 있고, 항목은 폴더다 |
     * | `S`(옛 GNU 희소)·`M`(다음 볼륨에서 이어짐) | 크기만큼 있고, 풀지 않는다 |
     * | `L`·`K`·`x`·`X`·`g`·`V`·`N` | 메타 머리. 항목이 아니다 |
     *
     * **하드링크의 크기 칸** — 옛 도구는 크기를 채우고 자료는 싣지 않았고, POSIX 1988 은 그것을 무시하라고 했다.
     * pax 머리 없이 크기가 적힌 하드링크를 만나면 **다음 칸을 미리 읽어** 그것이 머리(검사합이 맞는다)거나 끝
     * 표시면 자료가 없는 것으로, 아니면 자료가 있는 것으로 본다. 하드링크의 자료는 어차피 풀지 않으므로(링크다)
     * 건너뛰기만 맞으면 된다.
     */
    override fun next(): ScannedEntry? {
        if (ended) return null
        skipRest()
        var longName: ByteArray? = null
        var pax = PaxHeaders.EMPTY
        var sawPax = false
        while (true) {
            val rec = readRecord() ?: return end()
            if (TarFormat.isZero(rec)) return end()
            if (!TarFormat.checksumOk(rec)) throw IOException("tar 머리의 검사합이 맞지 않는다")
            val type = TarFormat.type(rec)
            val size = TarFormat.size(rec)
            when (type) {
                'L' -> {
                    longName = readMeta(size, EntryNameLimits.MAX_LONG_NAME_BYTES).trimEndNul()
                    continue
                }
                'K' -> {
                    // 긴 링크 대상. 링크는 풀지 않으므로 대상은 쓰지 않지만 크기는 묶는다.
                    readMeta(size, EntryNameLimits.MAX_LONG_NAME_BYTES)
                    continue
                }
                'x', 'X' -> {
                    pax = pax.merge(PaxHeaders.parse(readMeta(size, TarLimits.MAX_PAX_BYTES)))
                    sawPax = true
                    continue
                }
                'g' -> {
                    // 전역 PAX 머리. `git archive` 가 커밋 번호를 여기 싣는다. 뒤 항목 전부에 걸리는 값인데
                    // 우리가 쓰는 열쇠(이름·크기·시각)를 전역으로 적는 도구는 없다시피 해 읽고 버린다.
                    readMeta(size, TarLimits.MAX_PAX_BYTES)
                    continue
                }
                'V', 'N' -> {
                    // 볼륨 이름·옛 GNU 긴 이름 목록. 항목이 아니다.
                    skipBytes(TarFormat.padded(size))
                    continue
                }
            }
            return entry(rec, type, size, longName, pax, sawPax)
        }
    }

    private fun entry(
        rec: ByteArray,
        type: Char,
        headerSize: Long,
        longName: ByteArray?,
        pax: PaxHeaders,
        sawPax: Boolean,
    ): ScannedEntry {
        val size = pax.size ?: headerSize
        // 이름의 차례: PAX `path` → GNU 긴 이름 → 머리의 이름(ustar 면 앞머리를 이은 것). PAX 경로는 UTF-8 이
        // 약속이다 — 그렇게 읽히지 않거나 `hdrcharset=BINARY` 면 바이트를 넘겨 판정에 맡긴다.
        val paxPath = pax.path
        val paxName = if (paxPath != null && !pax.binaryNames) strictUtf8(paxPath) else null
        val nameBytes = paxPath ?: longName ?: TarFormat.nameBytes(rec)
        // 이름은 목록이 끝까지 들고 있다 — 하나의 길이와 합을 함께 묶는다([EntryNameLimits.MAX_NAME_BYTES_TOTAL]).
        // PAX 경로는 머리 상한(1 MiB)까지 올 수 있어 여기서 긴 이름과 같은 64 KiB 로 다시 자른다.
        countName(nameBytes)
        val endsWithSlash = nameBytes.isNotEmpty() && nameBytes.last() == '/'.code.toByte()
        var kind: ScannedEntry.Kind
        var dataSize: Long
        when (type) {
            '5' -> {
                kind = ScannedEntry.Kind.DIRECTORY
                dataSize = 0
            }
            'D' -> {
                kind = ScannedEntry.Kind.DIRECTORY
                dataSize = size
            }
            '2', '3', '4', '6' -> {
                kind = if (type == '2') ScannedEntry.Kind.LINK else ScannedEntry.Kind.DEVICE
                dataSize = 0
            }
            '1' -> {
                kind = ScannedEntry.Kind.LINK
                dataSize = if (size == 0L || sawPax) size else hardlinkData(size)
            }
            'S', 'M' -> {
                if (type == 'S') skipGnuSparseExtensions(rec)
                kind = ScannedEntry.Kind.UNREADABLE
                dataSize = size
            }
            else -> {
                // '0'·NUL·'7' 과 모르는 글자. 옛 V7 tar 는 폴더를 '이름 끝의 /' 로만 표시했다.
                kind = if ((type == '0' || type.code == 0) && endsWithSlash) ScannedEntry.Kind.DIRECTORY else ScannedEntry.Kind.FILE
                dataSize = if (kind == ScannedEntry.Kind.DIRECTORY) 0 else size
            }
        }
        if (kind == ScannedEntry.Kind.FILE && pax.sparse) kind = ScannedEntry.Kind.UNREADABLE
        val mtime = pax.mtimeMillis ?: TarFormat.mtimeMillis(rec)
        beginData(dataSize, TarFormat.padded(dataSize) - dataSize)
        return ScannedEntry(
            nameBytes = nameBytes,
            presetName = paxName,
            presetLabel = PAX_LABEL,
            kind = kind,
            dataSize = dataSize,
            declaredSize = if (kind == ScannedEntry.Kind.FILE) dataSize else 0L,
            mtimeMillis = mtime,
            dataOffset = position,
        )
    }

    /** 위 표의 하드링크 판단. 미리 읽은 칸은 머리였으면 [pending] 에 두고, 자료였으면 자료의 첫 칸으로 센다. */
    private fun hardlinkData(size: Long): Long {
        val peek = readRecord() ?: return 0L
        if (TarFormat.isZero(peek) || TarFormat.checksumOk(peek)) {
            pending = peek
            position -= TarFormat.RECORD
            return 0L
        }
        // 자료였다. 첫 칸은 이미 지나갔으므로 나머지만 건너뛰게 둔다 — 자리는 [position] 이 이미 센다.
        skipBytes((TarFormat.padded(size) - TarFormat.RECORD).coerceAtLeast(0L))
        return 0L
    }

    /** 옛 GNU 희소 머리 뒤의 확장 머리들. 자료보다 먼저 놓인다 — 건너뛰지 않으면 자료를 머리로 읽는다. */
    private fun skipGnuSparseExtensions(header: ByteArray) {
        var more = header[TarFormat.GNU_SPARSE_EXTENDED].toInt() != 0
        while (more) {
            val ext = readRecord() ?: throw EOFException("tar 가 중간에 끝났다")
            more = ext[TarFormat.GNU_SPARSE_EXTENSION_EXTENDED].toInt() != 0
        }
    }

    /** 메타 머리의 자료와 그 채움. 적힌 크기가 [cap] 을 넘으면 읽기 전에 거절한다. */
    private fun readMeta(size: Long, cap: Int): ByteArray {
        val buf = readBytes(size, cap)
        skipBytes(TarFormat.padded(size) - size)
        return buf
    }

    /** 512바이트 한 칸. 칸 경계에서 깨끗하게 끝나면 null, 칸 한가운데에서 끝나면 잘린 파일이다. */
    private fun readRecord(): ByteArray? {
        pending?.let {
            pending = null
            position += TarFormat.RECORD
            return it
        }
        val rec = ByteArray(TarFormat.RECORD)
        val got = readUpTo(rec, 0, TarFormat.RECORD)
        if (got == 0) return null
        if (got < TarFormat.RECORD) throw EOFException("tar 가 중간에 끝났다")
        return rec
    }

    private fun ByteArray.trimEndNul(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1].toInt() == 0) end--
        return if (end == size) this else copyOf(end)
    }

    private fun strictUtf8(raw: ByteArray): String? = try {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }

    private companion object {
        const val PAX_LABEL = "UTF-8(PAX)"
    }
}
