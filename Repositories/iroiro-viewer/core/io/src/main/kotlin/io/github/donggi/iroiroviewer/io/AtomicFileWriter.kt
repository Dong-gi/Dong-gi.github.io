package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 파일 하나를 **원자적으로** 놓는다. 원본을 파괴할 수 있는 모든 경로가 여기를 지난다.
 *
 * ## 왜 한 곳에 모으는가
 *
 * 이 앱에서 사용자의 파일이 사라질 수 있는 자리는 다섯이다 — EXIF 저장, 덮어쓰기 복사,
 * EXDEV 이동, 휴지통 복원 충돌, **아카이브 풀기**. 다섯이 각자 "임시본 → fsync →
 * rename" 을 구현하면 다섯 번 중 한 번은 어딘가를 빠뜨린다. 실제로 4단계 검토에서 나온
 * 치명 넷이 전부 되돌릴 수 없는 손실이었다.
 *
 * ## 다섯 걸음과 그 순서가 중요한 이유
 *
 * 1. **임시 이름으로 쓴다.** 대상을 먼저 지우지 않는다 — `rename(2)` 가 이미 원자적
 *    대체이고, 지우고 옮기는 사이는 **원본도 복사본도 없는 창**이다.
 * 2. **`fd.sync()`** — 내용이 디스크에 닿은 뒤에 이름을 붙인다.
 * 3. **수정시각을 되살린다**(rename 전에). 뒤에 하면 rename 이 만든 새 이름에 다시
 *    걸어야 하고, 그 사이에 프로세스가 죽으면 시각이 '지금' 으로 남는다.
 * 4. **`Os.rename`** — 원자적 대체.
 * 5. **부모 디렉터리 fsync** — 내용만 sync 하면 FAT(SD)처럼 저널이 없는 파일시스템에서
 *    '이름은 사라졌는데 새 이름은 안 붙은' 배열이 가능하다.
 *
 * ## 임시 이름이 대상 이름과 무관한 이유
 *
 * `.<대상이름>.part` 로 만들면 원본 이름이 242바이트를 넘는 순간 임시 이름이 상한을
 * 넘어 `ENAMETOOLONG` 으로 죽는다. 그래서 **고정 길이**다.
 */
object AtomicFileWriter {

    /** 우리가 만든 임시 파일임을 알아보는 표식. [sweepPartials] 가 이것으로 찾는다. */
    const val PART_PREFIX = ".iroiro-"
    const val PART_SUFFIX = ".part"

    /** 이보다 오래 손대지 않은 임시 파일만 걷는다. 도는 작업의 것을 지우지 않으려는 것이다. */
    const val PART_GRACE_MS = 60L * 60 * 1000

    /**
     * [target] 에 원자적으로 쓴다.
     *
     * @param lastModified epoch 밀리초. **0 이면 건드리지 않는다** — 모르는 시각을
     *   지어내면 날짜 정렬과 이어보기 키가 거짓이 된다.
     * @param body 임시 파일에 쓸 내용. 이 안에서 던지면 임시본은 정리된다.
     */
    suspend fun write(
        target: File,
        lastModified: Long,
        body: suspend (FileOutputStream) -> Unit,
    ) {
        val pending = begin(target, lastModified)
        try {
            body(pending.stream)
            pending.commit()
        } catch (t: Throwable) {
            withContext(NonCancellable) { pending.abort() }
            throw t
        }
    }

    /**
     * 쓰기를 시작한다. **스트림을 먼저 달라고 하는 소비자**를 위한 모양이다 —
     * 아카이브 리더는 우리에게 [java.io.OutputStream] 을 받아 거기에 밀어 넣으므로
     * [write] 처럼 본문을 람다로 감쌀 수 없다.
     *
     * 반드시 [Pending.commit] 이나 [Pending.abort] 중 하나를 부른다. 부르지 않으면
     * 임시 파일이 남는다(다만 [sweepPartials] 가 한 시간 뒤 걷는다).
     */
    fun begin(target: File, lastModified: Long): Pending {
        val parent = target.parentFile ?: throw ErrnoException("parent", OsConstants.ENOENT)
        val tmp = File(parent, "$PART_PREFIX${UUID.randomUUID().toString().take(8)}$PART_SUFFIX")
        return Pending(target, tmp, FileOutputStream(tmp), lastModified)
    }

    /** 쓰는 중인 파일 하나. [commit] 하기 전까지 대상은 손대지 않은 그대로다. */
    class Pending internal constructor(
        private val target: File,
        private val tmp: File,
        val stream: FileOutputStream,
        private val lastModified: Long,
    ) {
        /** 내용을 디스크에 내리고 원자적으로 제자리에 놓는다. */
        fun commit() {
            stream.use { it.fd.sync() }
            if (lastModified > 0) runCatching { tmp.setLastModified(lastModified) }
            Os.rename(tmp.absolutePath, target.absolutePath)
            target.parentFile?.let { syncDirectory(it) }
        }

        /**
         * 포기한다. **임시 파일을 반드시 지운다.**
         *
         * 예전에는 "대상이 없고 임시 파일이 온전하면 그것이 남은 유일한 사본일 수 있다"
         * 는 이유로 지우지 않고 두었다. 그 조건은 **두 호출자 모두에서 성립하지 않는다** —
         * 복사는 원본이 그대로 있고, 아카이브 풀기는 아카이브 안에 바이트가 그대로 있다.
         * 게다가 [commit] 의 `rename(2)` 은 원자적이라 '반쯤 이름이 바뀐' 상태가 없다.
         *
         * 실제로 그 조건 때문에 **압축폭탄을 막은 자리에 1 MB 짜리 숨은 파일이 남았다**
         * (실측: `.iroiro-26849784.part` 1,042,580바이트). 숨김 이름이라 목록에도 안 떠서
         * 사용자는 그것이 있는 줄도 모른다. [sweepPartials] 가 한 시간 뒤 걷지만,
         * 걷을 것을 일부러 만들어 둘 이유가 없다.
         */
        fun abort() {
            runCatching { stream.close() }
            tmp.delete()
        }
    }

    /** 디렉터리 항목을 디스크에 내린다. 실패해도 던지지 않는다 — 최선의 노력이다. */
    fun syncDirectory(dir: File) {
        runCatching {
            val fd = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(fd)
            } finally {
                Os.close(fd)
            }
        }
    }

    /**
     * 프로세스가 죽어 남은 임시 파일을 걷는다.
     *
     * 지금 돌고 있는 쓰기의 임시 파일을 지우면 안 되므로 **나이로 가른다.** 우리가 만든
     * 이름 꼴이면서 [PART_GRACE_MS] 넘게 손대지 않은 것만 지운다.
     */
    fun sweepPartials(dir: File) {
        val cutoff = System.currentTimeMillis() - PART_GRACE_MS
        runCatching {
            dir.listFiles { f ->
                f.isFile && f.name.startsWith(PART_PREFIX) && f.name.endsWith(PART_SUFFIX)
            }?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }
}
