package io.github.donggi.iroiroviewer.io

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * 닫아도 아래 흐름은 닫지 않는 껍데기(비우기만 한다). ZIP 흐름(`ZipOutputStream`)을 닫아 압축기의 네이티브 메모리를 놓되, 그 아래의
 * 임시본은 큐가 fsync 하고 놓을 때까지 열어 둔다([AtomicFileWriter.Pending.commit] 이 그 흐름으로 내린다) — ZIP 만들기([ZipMaker])와 빈
 * 문서([NewDocuments])가 쓴다. 바이트 묶음 쓰기를 넘긴다 — `FilterOutputStream` 의 기본 구현은 한 바이트씩 쓴다(함정 표).
 */
internal class KeepOpenStream(out: OutputStream) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
    }

    override fun close() {
        flush()
    }
}
