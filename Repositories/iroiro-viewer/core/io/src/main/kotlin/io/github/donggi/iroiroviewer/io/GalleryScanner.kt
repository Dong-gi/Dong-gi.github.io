package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.donggi.iroiroviewer.model.DayBucket
import io.github.donggi.iroiroviewer.model.FileEntry
import io.github.donggi.iroiroviewer.model.FileKind
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.ZoneId

/**
 * 갤러리가 볼 곳을 훑는다.
 *
 * ## 왜 MediaStore 를 쓰지 않는가
 *
 * 안드로이드에는 이미 사진 색인이 있고 갤러리 앱들이 그것을 읽는다. 그런데 그 색인은
 * **미디어 스캐너가 본 것만** 담는다. 다른 앱이 우회 경로로 쓴 파일, 방금 `adb push`
 * 한 파일, 스캐너가 훑기 전의 파일은 없다. 우리는 모든 파일 접근 권한으로 파일시스템을
 * 직접 보는 앱이고, "폴더에 있는데 갤러리에는 없다" 가 이 앱에서 가장 나쁜 답이다.
 *
 * 그리고 MediaStore 를 읽으려면 `READ_MEDIA_IMAGES` 를 또 선언해야 한다 — 권한을 하나
 * 더 받아 이미 할 수 있는 일을 하는 셈이다.
 *
 * ## 규칙
 *
 * - **`.nomedia` 가 있는 폴더는 통째로 건너뛴다.** 그것이 안드로이드에서 '이 폴더는
 *   갤러리에 넣지 마라' 를 뜻하는 유일한 약속이고, 우리 휴지통도 그 표시를 단다.
 * - 숨김 폴더(`.` 로 시작)는 들어가지 않는다.
 * - 새것이 위로. 사진 목록의 관행이고, 대개 찾는 것은 방금 찍은 것이다.
 */
object GalleryScanner {

    /**
     * 기본으로 보는 폴더. 볼륨마다 이 이름의 폴더가 있으면 훑는다.
     *
     * 볼륨 전체를 훑지 않는 것은 속도 때문만이 아니다 — 앱 데이터 폴더 안의 캐시
     * 이미지 수천 장이 갤러리에 섞이면 그 목록은 쓸모가 없어진다.
     */
    val DEFAULT_FOLDERS = listOf("DCIM", "Pictures", "Download")

    /** 재귀 상한. DCIM 아래가 이보다 깊은 경우는 없고, 깊으면 링크 고리를 의심한다. */
    private const val MAX_DEPTH = 8

    /** 이만큼마다 진행을 알린다. */
    private const val PROGRESS_EVERY = 200

    sealed interface Scan {
        data class Scanning(val found: Int) : Scan

        /**
         * @param sections 날짜 구간. [images] 의 자리를 번호로 가리킨다 — 항목을 날짜별
         *   목록으로 다시 담지 않는 이유는 [DayBucket.Section] 의 주석에 있다.
         */
        data class Ready(
            val images: List<FileEntry>,
            val sections: List<DayBucket.Section>,
            val roots: List<String>,
            val scanMillis: Long,
        ) : Scan
    }

    /** 이 볼륨들에서 실제로 있는 기본 폴더. */
    fun defaultRoots(volumes: List<VolumeRegistry.Volume>): List<File> =
        volumes.flatMap { v -> DEFAULT_FOLDERS.map { File(v.path, it) } }.filter { it.isDirectory }

    fun scan(roots: List<File>): Flow<Scan> = flow {
        emit(Scan.Scanning(0))
        val started = System.nanoTime()
        val found = ArrayList<FileEntry>(512)
        val visited = HashSet<Long>()
        for (root in roots) {
            walk(root, visited, 0) { entry ->
                found += entry
                if (found.size % PROGRESS_EVERY == 0) emit(Scan.Scanning(found.size))
            }
        }
        found.sortByDescending { it.lastModified }
        // **날짜 묶기를 여기서 한다.** 이 flow 는 `IroDispatchers.io` 위에서 돌고,
        // 화면은 주 스레드다 — 1만 장의 시각을 주 스레드에서 날짜로 바꾸면 격자가
        // 그려지기 전에 한 번 멎는다. 구간은 훑기의 결과이지 그리기의 일이 아니다.
        //
        // 시간대는 **기기 설정**을 쓴다. 사용자가 사진을 찍은 날은 그 사람의 시계로
        // 센 날이다(시험이 이 둘을 갈라 확인한다).
        val sections = DayBucket.sectionsOf(found.map { it.lastModified }, ZoneId.systemDefault())
        emit(
            Scan.Ready(
                images = found,
                sections = sections,
                roots = roots.map { it.absolutePath },
                scanMillis = (System.nanoTime() - started) / 1_000_000,
            )
        )
    }.flowOn(IroDispatchers.io)

    private suspend fun walk(
        dir: File,
        visited: MutableSet<Long>,
        depth: Int,
        onImage: suspend (FileEntry) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > MAX_DEPTH) return
        if (TrashStore.isTrashPath(dir.absolutePath)) return
        // 이 한 줄이 '숨긴 사진첩' 을 존중한다. 다른 갤러리 앱과 같은 약속이다.
        if (File(dir, NOMEDIA).exists()) return

        val children = dir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            val st = try {
                Os.lstat(child.absolutePath)
            } catch (e: ErrnoException) {
                continue
            }
            // 링크는 따라가지 않는다. 고리를 만들 수 있고 볼륨 밖으로 나간다.
            if (OsConstants.S_ISLNK(st.st_mode)) continue
            val name = child.name
            if (name.startsWith('.')) continue

            if (OsConstants.S_ISDIR(st.st_mode)) {
                // 하드링크로 만든 디렉터리 고리를 막는다(복사 엔진과 같은 방식).
                val key = st.st_dev * 1_000_003L + st.st_ino
                if (!visited.add(key)) continue
                walk(child, visited, depth + 1, onImage)
                continue
            }

            if (MimeResolver.kindOf(name, isDirectory = false) != FileKind.IMAGE) continue
            onImage(
                FileEntry(
                    name = name,
                    path = child.absolutePath,
                    isDirectory = false,
                    size = st.st_size,
                    lastModified = st.st_mtime * 1000L,
                    isHidden = false,
                    isSymlink = false,
                    kind = FileKind.IMAGE,
                )
            )
        }
    }

    private const val NOMEDIA = ".nomedia"
}
