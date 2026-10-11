package io.github.donggi.iroiroviewer.io.clip

import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * PCM WAV 의 한 구간 — **표본 경계**에서 자른다(소리의 가장 작은 단위라 고른 시각과 거의 같다).
 *
 * 머리는 새로 쓴다: `RIFF` · `fmt `(원본 그대로) · `LIST`(원본에 있으면 — 제목·작성자 같은 INFO) · `data`(자른 길이). 그 밖의 청크는
 * 옮기지 않는다 — `cue `·`smpl` 은 원본의 자리를 가리켜 자른 파일에서는 틀린 값이 된다.
 *
 * **다루는 것은 PCM(정수·실수, WAVE_FORMAT_EXTENSIBLE 포함)뿐이다.** ADPCM 같은 압축 WAV 는 블록 안의 표본 수가 따로 있어
 * 이 셈이 틀린다 — [CutPlan.Unsupported]. 4 GiB 를 넘는 RF64 도 아직 다루지 않는다.
 */
internal object WavCut {

    /** 옮길 `LIST` 청크의 상한. 그보다 크면 옮기지 않는다(그림을 품은 이상한 파일). */
    private const val MAX_LIST_BYTES = 1L * 1024 * 1024

    fun plan(raf: RandomAccessFile, startUs: Long, endUs: Long, sourceExt: String): CutPlan {
        val r = WindowReader(raf)
        if (r.length < 12 || r.ascii(0, 4) != "RIFF" || r.ascii(8, 4) != "WAVE") return CutPlan.Unsupported
        var fmt: ByteArray? = null
        var list: ByteArray? = null
        var dataStart = -1L
        var dataSize = 0L
        var pos = 12L
        var chunks = 0
        while (pos + 8 <= r.length && chunks < MAX_CHUNKS) {
            chunks++
            val id = r.ascii(pos, 4)
            val head = ByteArray(8)
            r.read(pos, head)
            val size = head.u32le(4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    if (size < 16 || size > 1024 || body + size > r.length) return CutPlan.Unsupported
                    // 홀수 크기면 채움 바이트 하나를 함께 든다 — 뒤 청크가 짝수 자리에서 시작해야 한다(RIFF).
                    fmt = ByteArray((8 + size + (size and 1)).toInt()).also { r.read(pos, it, 0, (8 + size).toInt()) }
                }
                "LIST" -> if (size <= MAX_LIST_BYTES && body + size <= r.length) {
                    list = ByteArray((8 + size + (size and 1)).toInt()).also { r.read(pos, it, 0, (8 + size).toInt()) }
                }
                "data" -> {
                    dataStart = body
                    // 흘려 쓴 파일(크기 0·FFFFFFFF)이나 잘린 파일은 적힌 크기를 믿지 않는다 — 파일 끝까지다. 다만 크기 0 뒤에 곧바로
                    // 다른 청크의 머리가 오면 **정말 빈** 데이터다 — 그 청크(LIST 등)를 소리로 자르면 잡음이 된다.
                    dataSize = when {
                        size == 0xFFFFFFFFL || body + size > r.length -> r.length - body
                        size == 0L -> if (looksLikeChunk(r, body)) 0 else r.length - body
                        else -> size
                    }
                }
            }
            if (dataStart >= 0 && fmt != null && id == "data") {
                // 데이터 뒤의 LIST(일부 편집기가 끝에 둔다)도 한 번 본다.
                val after = dataStart + dataSize + (dataSize and 1)
                if (list == null && after + 8 <= r.length && r.ascii(after, 4) == "LIST") {
                    val h = ByteArray(8).also { r.read(after, it) }
                    val s = h.u32le(4)
                    if (s <= MAX_LIST_BYTES && after + 8 + s <= r.length) {
                        list = ByteArray((8 + s + (s and 1)).toInt()).also { r.read(after, it, 0, (8 + s).toInt()) }
                    }
                }
                break
            }
            pos = body + size + (size and 1)
        }
        val f = fmt ?: return CutPlan.Unsupported
        if (dataStart < 0) return CutPlan.Unsupported

        val tag = f.u16le(8)
        val channels = f.u16le(10)
        val rate = f.u32le(12)
        val blockAlign = f.u16le(20)
        val pcm = when (tag) {
            FORMAT_PCM, FORMAT_FLOAT -> true
            // WAVE_FORMAT_EXTENSIBLE — 하위 형식 GUID 의 앞 두 바이트가 PCM(1)·실수(3)여야 한다.
            FORMAT_EXTENSIBLE -> f.size >= 8 + 26 && f.u16le(8 + 24).let { it == FORMAT_PCM || it == FORMAT_FLOAT }
            else -> false
        }
        if (!pcm || channels <= 0 || rate <= 0 || blockAlign <= 0) return CutPlan.Unsupported

        val frames = dataSize / blockAlign
        val startFrame = (startUs.coerceAtLeast(0) * rate / 1_000_000L).coerceAtMost(frames)
        val endFrame = ((endUs * rate + 999_999L) / 1_000_000L).coerceAtMost(frames)
        if (endFrame <= startFrame) return CutPlan.Empty
        val length = (endFrame - startFrame) * blockAlign
        val total = 4L + f.size + (list?.size ?: 0) + 8 + length + (length and 1)
        if (total > 0xFFFFFFFFL) return CutPlan.Unsupported

        val header = ByteArray(12 + f.size + (list?.size ?: 0) + 8)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        header.putU32le(4, total)
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
        f.copyInto(header, 12)
        var at = 12 + f.size
        list?.let {
            it.copyInto(header, at)
            at += it.size
        }
        "data".toByteArray(Charsets.US_ASCII).copyInto(header, at)
        header.putU32le(at + 4, length)

        val ext = if (sourceExt == "wav" || sourceExt == "wave") sourceExt else "wav"
        return CutPlan.Ok(
            object : AudioCut {
                override val startUs = startFrame * 1_000_000L / rate
                override val endUs = endFrame * 1_000_000L / rate
                override val outputBytes = header.size + length + (length and 1)
                override val extension = ext

                override suspend fun writeTo(out: OutputStream, onBytes: (Long) -> Unit) {
                    out.write(header)
                    onBytes(header.size.toLong())
                    copyRange(raf, dataStart + startFrame * blockAlign, length, out, ByteArray(COPY_BUFFER), onBytes)
                    if (length and 1L != 0L) {
                        out.write(0)
                        onBytes(1)
                    }
                }
            }
        )
    }

    /** [pos] 에 RIFF 청크 머리가 있어 보이는가 — 글자 넷(인쇄 가능한 ASCII)과 파일 안에 드는 크기. */
    private fun looksLikeChunk(r: ByteSource, pos: Long): Boolean {
        if (pos + 8 > r.length) return false
        for (i in 0 until 4) if (r.u8(pos + i) !in 0x20..0x7E) return false
        val head = ByteArray(8)
        if (r.read(pos, head) != 8) return false
        return pos + 8 + head.u32le(4) <= r.length
    }

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE
    private const val MAX_CHUNKS = 4096
    private const val COPY_BUFFER = 64 * 1024
}
