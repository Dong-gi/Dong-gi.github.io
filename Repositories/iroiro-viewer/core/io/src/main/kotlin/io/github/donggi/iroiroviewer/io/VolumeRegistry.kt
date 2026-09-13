package io.github.donggi.iroiroviewer.io

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.getSystemService
import java.io.File

/**
 * 이 기기에 붙어 있는 저장 볼륨.
 *
 * 내부 저장소와 SD 카드는 **다른 파일시스템**이다. 그래서 둘 사이의 이동은 `rename`
 * 으로 끝나지 않고(EXDEV) 복사+삭제로 내려가야 한다(4단계). 볼륨을 구분해 들고 있는
 * 것이 그 분기의 출발점이다.
 *
 * 볼륨은 꽂았다 뺐다 한다. 화면이 보일 때마다 다시 읽는 것이 맞다 — 캐시해 두면
 * SD 를 뺀 뒤에도 목록에 남아 '있는데 안 열리는' 항목이 된다.
 */
object VolumeRegistry {

    data class Volume(
        /** 안정적인 식별자. 휴지통 표가 이 값으로 볼륨을 가린다. */
        val id: String,
        val label: String,
        val path: String,
        val isPrimary: Boolean,
        val isRemovable: Boolean,
        val totalBytes: Long,
        val freeBytes: Long,
    ) {
        val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
        val usedFraction: Float
            get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    fun volumes(context: Context): List<Volume> {
        val sm = context.getSystemService<StorageManager>() ?: return listOfNotNull(primaryFallback(context))
        return sm.storageVolumes.mapNotNull { v -> toVolume(context, v) }
    }

    private fun toVolume(context: Context, v: StorageVolume): Volume? {
        val dir = v.directory ?: return null // 마운트되지 않은 볼륨은 경로가 없다
        val path = dir.absolutePath
        val (total, free) = spaceOf(path)
        return Volume(
            id = v.uuid ?: if (v.isPrimary) "primary" else path,
            // **기본 볼륨의 이름은 우리가 붙인다.** 안드로이드가 주는 설명은
            // `Internal shared storage` 라, 한국어 화면에서 그 한 줄만 영어로 길게
            // 남는다(사용자가 지적했다). 다른 볼륨은 그대로 둔다 — SD 카드의 이름은
            // 기기가 아는 것이고 우리가 더 잘 지을 수 없다.
            label = if (v.isPrimary) {
                context.getString(R.string.io_volume_internal)
            } else {
                v.getDescription(context) ?: dir.name
            },
            path = path,
            isPrimary = v.isPrimary,
            isRemovable = v.isRemovable,
            totalBytes = total,
            freeBytes = free,
        )
    }

    /** [StorageManager] 를 못 얻는 일은 없어야 하지만, 그때도 내부 저장소는 보여야 한다. */
    private fun primaryFallback(context: Context): Volume? {
        val dir = Environment.getExternalStorageDirectory() ?: return null
        val (total, free) = spaceOf(dir.absolutePath)
        val label = context.getString(R.string.io_volume_internal)
        return Volume("primary", label, dir.absolutePath, true, false, total, free)
    }

    /**
     * `File.getTotalSpace` 대신 [StatFs] 를 쓰는 것은 블록 수를 Long 으로 주어
     * 2TB 를 넘는 볼륨에서도 넘치지 않기 때문이다.
     */
    private fun spaceOf(path: String): Pair<Long, Long> = try {
        val fs = StatFs(path)
        fs.blockCountLong * fs.blockSizeLong to fs.availableBlocksLong * fs.blockSizeLong
    } catch (e: IllegalArgumentException) {
        0L to 0L
    }

    /** 어떤 경로가 어느 볼륨에 속하는가. 가장 긴 접두어가 이긴다. */
    fun volumeOf(volumes: List<Volume>, path: String): Volume? =
        volumes.filter { path == it.path || path.startsWith(it.path + File.separator) }
            .maxByOrNull { it.path.length }
}
