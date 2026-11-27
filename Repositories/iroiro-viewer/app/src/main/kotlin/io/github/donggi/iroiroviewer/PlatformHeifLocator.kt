package io.github.donggi.iroiroviewer

import android.media.MediaMetadataRetriever
import io.github.donggi.iroiroviewer.model.ExifContainerGuard
import java.io.FileDescriptor

/**
 * HEIF·AVIF 의 Exif 덩이 자리를 **ExifInterface 와 같은 키로** 얻는다 — 플랫폼 추출기(`MediaMetadataRetriever`)의 키 33·34
 * (`METADATA_KEY_EXIF_OFFSET`·`EXIF_LENGTH`), 그리고 ExifInterface 1.4.2 의 `getHeifAttributes` 가 Exif 앞에 넣어 두는 폭·높이·회전 — 그림이
 * 있으면(키 26) 29·30·31, 없고 영상이 있으면(키 17) 18·19·24(바이트코드). 판정과 블록 읽기는 검사기(`core:model` 의
 * `ExifContainerGuard.openForExif`)가 하고, 이것은 자리만 준다.
 *
 * **검사기가 연 그 서술자**로 묻는다 — 경로를 다시 열면 그 사이 이름이 바뀌어(rename) 추출기와 검사기가 다른 파일을 볼 수 있다. 서술자의 위치는
 * 추출기가 옮길 수 있으나 검사기는 위치를 지정해 읽으므로 상관없다.
 *
 * 순수 JVM 모듈은 추출기를 부를 수 없고 `core:io`·`core:ui` 는 서로를 볼 수 없어(의존 표), 두 쪽의 호출부가 함께 쓰도록 `app` 이 꽂는다.
 * 추출기를 열지 못하면(던지면) null — 그 파일의 Exif 는 읽지 않는다. 취소는 없다(추출기 한 번은 짧다).
 */
object PlatformHeifLocator : ExifContainerGuard.HeifLocator {

    override fun locate(fd: FileDescriptor): ExifContainerGuard.HeifBoxes? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(fd)
            fun key(k: Int): String? = retriever.extractMetadata(k)
            val (w, h, r) = when {
                key(MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE) == "yes" -> Triple(
                    MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH,
                    MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT,
                    MediaMetadataRetriever.METADATA_KEY_IMAGE_ROTATION,
                )
                key(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" -> Triple(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
                )
                else -> Triple(null, null, null)
            }
            ExifContainerGuard.HeifBoxes(
                exifOffset = key(MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET),
                exifLength = key(MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH),
                width = w?.let(::key),
                height = h?.let(::key),
                rotation = r?.let(::key),
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
