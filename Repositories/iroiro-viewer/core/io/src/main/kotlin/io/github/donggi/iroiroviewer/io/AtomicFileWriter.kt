package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * 파일 하나를 **원자적으로** 놓는다. 원본을 파괴할 수 있는 모든 경로가 여기를 지난다.
 *
 * ## 왜 한 곳에 모으는가
 *
 * 이 앱에서 사용자의 파일이 사라질 수 있는 자리는 열이다(CLAUDE.md '안전') — EXIF 저장, 덮어쓰기 복사,
 * EXDEV 이동, 휴지통 복원 충돌, **아카이브 풀기**, 곁에 새 파일을 만드는 셋(ZIP 만들기·
 * 그림 잘라 저장(그리기·메모 포함)·구간 저장 — 이름을 끝에 맡는다, `NewFiles`), 글 편집의 저장
 * (`TextSaver`), 편집기가 돌려준 문서의 저장(`EditorSaver`). 휴지통에 사본 넣기와 새 글 파일·새 문서도
 * 같은 길을 지난다. 각자 "임시본 → fsync →
 * rename" 을 구현하면 여러 번 중 한 번은 어딘가를 빠뜨린다. 실제로 4단계 검토에서 나온
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

    /**
     * 읽고 쓸 수 있는 임시본을 연다 — **되감아 고쳐 쓰는 소비자**를 위한 모양이다. 플랫폼의 `MediaMuxer` 가 그렇다(MP4 는 끝에서
     * 머리의 크기를 고치고, 경로를 받는 생성자도 파일을 읽기·쓰기로 연다). 나머지 약속은 [begin] 과 같다 — 같은 폴더, 같은 임시
     * 이름 규약([sweepPartials] 가 걷는다), [RandomPending.commit] 이나 [RandomPending.abort] 중 하나를 반드시 부른다.
     */
    fun beginRandomAccess(target: File): RandomPending {
        val parent = target.parentFile ?: throw ErrnoException("parent", OsConstants.ENOENT)
        val tmp = File(parent, "$PART_PREFIX${UUID.randomUUID().toString().take(8)}$PART_SUFFIX")
        return RandomPending(tmp, RandomAccessFile(tmp, "rw"))
    }

    /**
     * 다 쓴 임시본을 **두 걸음으로** 놓는 모양 — 내용을 디스크에 내리고([sync], 큰 파일이면 몇 초), 그다음 이름을 붙인다
     * ([moveInto], 순간). 새 파일을 끝에 맡는 쓰기(`NewFiles.place`)가 쓴다: 두 걸음 사이에서 이름을 맡아야, 맡은 이름이 0바이트로
     * 목록에 떠 있는 시간이 내리는 시간만큼 길어지지 않는다(누른 사람이 깨진 파일을 연다).
     */
    interface Staged {
        /** 내용을 디스크에 내리고 닫는다. 한 번만 부른다. */
        fun sync()

        /** 내린 임시본을 [into] 로 원자적으로 옮긴다. 같은 폴더여야 한다. [sync] 뒤에 부른다. */
        fun moveInto(into: File)

        /** 포기한다. 임시 파일을 반드시 지운다. */
        fun abort()
    }

    /** [beginRandomAccess] 로 연 임시본. 대상 이름은 [commit] 이 받는다(새 파일은 끝에 이름을 맡는다 — `NewFiles`). */
    class RandomPending internal constructor(
        private val tmp: File,
        val file: RandomAccessFile,
    ) : Staged {
        /** 내용을 디스크에 내리고 [into] 로 원자적으로 옮긴다. 같은 폴더여야 한다. */
        fun commit(into: File) {
            sync()
            moveInto(into)
        }

        override fun sync() {
            file.use { it.fd.sync() }
        }

        override fun moveInto(into: File) {
            Os.rename(tmp.absolutePath, into.absolutePath)
            into.parentFile?.let { syncDirectory(it) }
        }

        /** 포기한다. 임시 파일을 반드시 지운다. */
        override fun abort() {
            runCatching { file.close() }
            tmp.delete()
        }
    }

    /** 쓰는 중인 파일 하나. [commit] 하기 전까지 대상은 손대지 않은 그대로다. */
    class Pending internal constructor(
        private val target: File,
        private val tmp: File,
        val stream: FileOutputStream,
        private val lastModified: Long,
    ) : Staged {
        /**
         * 임시본의 자리. 쓴 뒤에 **파일을 다시 열어 고치는** 소비자(잘라 저장이 EXIF 날짜를 옮긴다 — `ExifInterface` 는 경로를
         * 받는다)가 쓴다. 이름은 [commit] 전까지 우리 규약의 임시 이름이다.
         */
        internal val tempFile: File get() = tmp

        /**
         * 내용을 디스크에 내리고 원자적으로 제자리에 놓는다.
         *
         * @param into 놓을 자리. 기본은 [begin] 에 준 대상이다. **다 쓴 뒤에야 이름이 정해지는 쓰기**(ZIP 만들기 —
         *   쓰는 동안 그 이름이 생겼을 수 있어 끝에 맡는다)가 다른 자리를 준다. 같은 폴더여야 한다 — `rename(2)` 는
         *   폴더를 넘지 못한다.
         */
        fun commit(into: File = target) {
            sync()
            moveInto(into)
        }

        override fun sync() {
            stream.use { it.fd.sync() }
        }

        override fun moveInto(into: File) {
            if (lastModified > 0) runCatching { tmp.setLastModified(lastModified) }
            Os.rename(tmp.absolutePath, into.absolutePath)
            into.parentFile?.let { syncDirectory(it) }
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
        override fun abort() {
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
