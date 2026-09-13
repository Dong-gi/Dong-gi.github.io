package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.media.MediaScannerConnection

/**
 * 미디어 색인(MediaStore)을 우리가 바꾼 것에 맞춘다.
 *
 * 이것을 안 하면 사진을 지운 뒤에도 갤러리에 유령 항목이 남고, 새로 복사한 음악이
 * 음악 앱에 안 보인다. 사용자에게는 "이 파일 관리자는 뭔가 이상하다" 로 보인다.
 *
 * **반드시 배치로 부른다.** `scanFile` 은 바인더 호출이라 파일마다 부르면 1만 개
 * 작업에서 호출이 1만 번이 되고 그것만으로 작업보다 오래 걸린다. 목록 API 는
 * 한 번의 연결로 전부 훑는다.
 */
class MediaIndex(private val context: Context) {

    /**
     * 바뀐 경로들을 색인에 알린다. 지워진 경로도 그대로 넘긴다 — 스캐너가 파일이
     * 없는 것을 보고 항목을 지운다.
     *
     * 한 번에 너무 많이 넘기면 바인더 트랜잭션이 커지므로 잘라서 보낸다.
     */
    fun scanAll(paths: List<String>) {
        if (paths.isEmpty()) return
        paths.distinct().chunked(CHUNK).forEach { chunk ->
            runCatching {
                MediaScannerConnection.scanFile(context, chunk.toTypedArray(), null, null)
            }
        }
    }

    private companion object {
        /** 한 번에 넘기는 경로 수. 바인더 트랜잭션 상한(1MB)에 여유를 둔 값이다. */
        const val CHUNK = 500
    }
}
