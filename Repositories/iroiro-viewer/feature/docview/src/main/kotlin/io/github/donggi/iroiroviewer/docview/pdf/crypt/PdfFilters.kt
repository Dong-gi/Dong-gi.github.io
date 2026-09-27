package io.github.donggi.iroiroviewer.docview.pdf.crypt

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * 스트림 필터. **우리가 스스로 읽어야 하는 두 가지에만** 쓴다 — xref 스트림과 객체 스트림.
 * 그림·글꼴·내용 스트림은 풀지 않는다(복호화만 하고 압축은 그대로 pdfium 에 넘긴다).
 *
 * ## 출력 상한
 *
 * 1 KB 가 1 GB 로 부푸는 압축 폭탄이 PDF 에도 있다. 풀기 전에 선언을 믿지 않고
 * **실제로 나온 바이트**를 센다 — 이 저장소의 방어 규칙 그대로다.
 */
internal object PdfFilters {

    /** xref·객체 스트림 하나가 풀려 나올 수 있는 최대 크기. */
    const val MAX_DECODED = 64L * 1024 * 1024

    /**
     * `/Filter` 와 `/DecodeParms` 를 따라 푼다. 모르는 필터를 만나면 [PdfSyntaxException].
     *
     * xref·객체 스트림에 실제로 쓰이는 것은 `/FlateDecode`(+ PNG 예측자)뿐이라 그것만 안다.
     */
    fun decode(dict: PdfDict, data: ByteArray): ByteArray {
        val filters = listOf(dict["Filter"]).flatMap { f ->
            when (f) {
                is PdfName -> listOf(f.name)
                is PdfArray -> f.items.mapNotNull { (it as? PdfName)?.name }
                else -> emptyList()
            }
        }
        val parms = when (val p = dict["DecodeParms"]) {
            is PdfDict -> listOf(p)
            is PdfArray -> p.items.map { it as? PdfDict }
            else -> emptyList()
        }
        var out = data
        filters.forEachIndexed { i, name ->
            out = when (name) {
                "FlateDecode", "Fl" -> predict(inflate(out), parms.getOrNull(i))
                "Crypt" -> out // 암호 필터는 이미 우리가 풀었다
                else -> throw PdfSyntaxException("xref·객체 스트림에 쓰지 않는 필터 $name")
            }
        }
        return out
    }

    fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        try {
            inf.setInput(data)
            val out = ByteArrayOutputStream(minOf(data.size * 4, 1 shl 20))
            val buf = ByteArray(64 * 1024)
            while (!inf.finished()) {
                val n = try {
                    inf.inflate(buf)
                } catch (e: DataFormatException) {
                    // 끝이 잘린 zlib — 나온 데까지 쓴다(pdfium 도 그렇게 한다).
                    break
                }
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                }
                out.write(buf, 0, n)
                if (out.size() > MAX_DECODED) throw PdfSyntaxException("스트림이 너무 크게 풀린다")
            }
            return out.toByteArray()
        } finally {
            inf.end()
        }
    }

    /**
     * 예측자(명세 7.4.4.4). xref 스트림은 거의 언제나 PNG `Up`(12)을 쓴다.
     */
    fun predict(data: ByteArray, parms: PdfDict?): ByteArray {
        val predictor = parms?.int("Predictor") ?: 1
        if (predictor < 2) return data
        val colors = (parms?.int("Colors") ?: 1).coerceIn(1, 32)
        val bpc = (parms?.int("BitsPerComponent") ?: 8).coerceIn(1, 16)
        val columns = (parms?.int("Columns") ?: 1).coerceIn(1, 1 shl 20)
        val bpp = maxOf(1, (colors * bpc + 7) / 8)
        val rowLen = (colors * bpc * columns + 7) / 8
        if (predictor == 2) {
            // TIFF 예측자. 8비트만 다룬다(그 밖은 xref 에 나오지 않는다).
            if (bpc != 8) return data
            val out = data.copyOf()
            var row = 0
            while (row + rowLen <= out.size) {
                for (i in bpp until rowLen) {
                    out[row + i] = (out[row + i] + out[row + i - bpp]).toByte()
                }
                row += rowLen
            }
            return out
        }
        // PNG: 줄마다 앞에 필터 종류 1바이트가 붙는다.
        //
        // **줄 폭을 실제 데이터로 누른다.** `/Columns` 는 파일이 적는 값이라 몇 바이트짜리
        // 스트림에 64 MiB 폭을 적을 수 있다. 그 폭으로 줄 버퍼를 잡고 0 으로 채워 내보내면
        // 몇 바이트가 128 MiB 가 된다(적대적 검토가 잡았다). 데이터보다 넓은 줄은 어차피 모자라게
        // 끝나므로, 있는 바이트까지만 잡고 그만큼만 내보낸다. 결과 전체에도 상한을 건다.
        val width = minOf(rowLen, data.size)
        val out = ByteArrayOutputStream(data.size)
        var prev = ByteArray(width)
        var p = 0
        while (p < data.size) {
            val type = data[p].toInt() and 0xFF
            p++
            val cur = ByteArray(width)
            val n = minOf(width, data.size - p)
            System.arraycopy(data, p, cur, 0, n)
            p += n
            for (i in 0 until n) {
                val left = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val up = prev[i].toInt() and 0xFF
                val upLeft = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val x = cur[i].toInt() and 0xFF
                cur[i] = when (type) {
                    0 -> x
                    1 -> x + left
                    2 -> x + up
                    3 -> x + (left + up) / 2
                    4 -> x + paeth(left, up, upLeft)
                    else -> x
                }.toByte()
            }
            out.write(cur, 0, n)
            if (out.size() > MAX_DECODED) throw PdfSyntaxException("예측자를 푼 결과가 너무 크다")
            prev = cur
        }
        return out.toByteArray()
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }
}
