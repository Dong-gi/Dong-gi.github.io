package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * 폴더 나무를 훑는다 — ZIP 만들기의 계획([ZipMaker.plan])과 폴더 정보([FolderStats])가 같은 것을 쓴다.
 *
 * ## 세 겹
 *
 * 복사의 훑기(`FileOpEngine.walk`)와 같은 세 겹이다 — **링크를 따라가지 않는다**(링크 너머는 고른 폴더 밖일 수 있다), **같은
 * (장치, inode) 를 두 번 들어가지 않는다**(디렉터리 하드링크·바인드 마운트의 고리), **깊이 [MAX_DEPTH] 까지**. 장치·소켓·파이프도
 * 담지 않는다. 읽는 것은 `lstat` 한 번과 폴더의 이름 목록뿐이다 — 파일을 열지 않는다.
 *
 * ## 본 자리를 무엇까지 적는가
 *
 * 폴더는 언제나 적는다. **파일은 링크 수(`st_nlink`)가 2 이상일 때만** 적는다 — 링크가 하나뿐인 파일은 나무 안의 이름이 하나라 두 번
 * 만날 길이 없다(고른 것들은 한 폴더 안의 서로 다른 이름이다). 폴더 정보가 내부 저장소 통째를 셀 때 파일 수십만 개의 자리를 들고 있지
 * 않으려는 것이다. 장치와 inode 는 섞지 않고 짝으로 적는다 — 섞은 값이 우연히 같으면 다른 파일을 '본 것' 으로 빠뜨린다.
 *
 * ## 순수하게 둔 까닭
 *
 * 파일시스템을 [Fs] 로 받는다. 링크·고리·깊이·못 읽는 폴더를 JVM 시험이 가짜 나무로 만들어 지킨다(`TreeWalkTest`) — 기기의 나무로는
 * 고리를 만들 수 없다(링크는 따라가지 않으니 고리가 되지 않고, 디렉터리 하드링크는 만들 수 없다).
 */
internal object TreeWalk {

    /** 깊이 상한. 정상 폴더가 이보다 깊은 일은 없다 — 여기 걸리면 고리이거나 일부러 만든 구조다. */
    const val MAX_DEPTH = 64

    enum class Type { DIRECTORY, FILE, LINK, OTHER }

    /** `lstat` 한 번의 값. [modifiedMillis] 는 초 단위 시각을 밀리초로 옮긴 것이다. */
    class Stat(
        val type: Type,
        val dev: Long,
        val ino: Long,
        val links: Long,
        val size: Long,
        val modifiedMillis: Long,
    )

    /** 훑기가 쓰는 파일시스템. 기기에서는 [PosixTree] 다. */
    interface Fs {
        /** 링크를 따라가지 않는 stat. 읽지 못하면 null. */
        fun lstat(path: String): Stat?

        /** 폴더의 이름 목록. 읽지 못하면 null. */
        fun list(path: String): Array<String>?
    }

    /** 담지 않고 건너뛴 까닭. */
    enum class Skip {
        /** 깊이 상한을 넘었다. */
        DEPTH,

        /** 링크·장치·소켓·파이프. */
        NOT_REGULAR,

        /** 이미 본 (장치, inode). */
        SEEN,

        /** 거르는 이름([walk] 의 `exclude` — 묶어 보내기의 숨김·MediaStore 임시 이름, 폴더 정보의 숨김). 폴더면 그 안도 보지 않는다. */
        EXCLUDED,
    }

    /** 훑으며 만나는 것. 작업 스레드에서 불린다. */
    interface Visitor {
        /**
         * 담을 항목 하나(폴더 또는 보통 파일). [relative] 는 고른 것의 이름부터 `/` 로 이은 이름이다. **거짓을 돌려주면 훑기를 멈춘다**
         * (ZIP 만들기의 항목 상한). 폴더면 이것이 참을 돌려준 뒤에 그 안으로 들어간다.
         */
        fun entry(path: String, relative: String, stat: Stat, depth: Int): Boolean

        /** 건너뛴 것. */
        fun skipped(why: Skip)

        /** 읽지 못한 것 — `lstat` 이 실패했거나 폴더의 목록을 읽지 못했다. */
        fun unreadable()
    }

    /**
     * [roots] 를 차례로 훑는다. 루트 각각의 이름이 [Visitor.entry] 의 상대 이름의 첫 조각이다.
     *
     * @param sorted 폴더 안을 이름 차례로 돈다(ZIP 은 같은 폴더를 두 번 묶어도 같은 차례가 나와야 한다). 세기만 하면 필요 없다.
     * @param exclude 거를 이름(마지막 조각). 참이면 `lstat` 도 하지 않고 [Skip.EXCLUDED] 로 건너뛴다 — 폴더면 그 안도 보지 않는다.
     * @param excludeRoots 루트에도 [exclude] 를 건다. 폴더 정보는 걸지 않는다(사용자가 고른 그 폴더를 센다).
     */
    suspend fun walk(
        roots: List<String>,
        fs: Fs,
        visitor: Visitor,
        sorted: Boolean = false,
        exclude: ((String) -> Boolean)? = null,
        excludeRoots: Boolean = false,
    ) {
        val seen = HashSet<Pair<Long, Long>>()
        var stopped = false

        suspend fun visit(path: String, relative: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (stopped) return
            if (exclude != null && (depth > 0 || excludeRoots) && exclude(nameOf(path))) {
                visitor.skipped(Skip.EXCLUDED)
                return
            }
            if (depth > MAX_DEPTH) {
                visitor.skipped(Skip.DEPTH)
                return
            }
            val st = fs.lstat(path)
            if (st == null) {
                visitor.unreadable()
                return
            }
            if (st.type != Type.DIRECTORY && st.type != Type.FILE) {
                visitor.skipped(Skip.NOT_REGULAR)
                return
            }
            val remember = st.type == Type.DIRECTORY || st.links > 1
            if (remember && !seen.add(st.dev to st.ino)) {
                visitor.skipped(Skip.SEEN)
                return
            }
            if (!visitor.entry(path, relative, st, depth)) {
                stopped = true
                return
            }
            if (st.type != Type.DIRECTORY) return
            val names = fs.list(path)
            if (names == null) {
                visitor.unreadable()
                return
            }
            if (sorted) names.sort()
            for (name in names) visit(join(path, name), "$relative/$name", depth + 1)
        }

        for (root in roots) visit(root, nameOf(root), 0)
    }

    /** 경로 잇기. 안드로이드의 구분자는 언제나 `/` 다(JVM 시험은 윈도에서도 돈다 — `File.separator` 를 쓰지 않는다). */
    internal fun join(parent: String, name: String): String =
        if (parent.endsWith('/')) parent + name else "$parent/$name"

    /** 경로의 마지막 조각. */
    internal fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')
}

/** 기기의 파일시스템 — `Os.lstat` 과 `File.list`. */
internal object PosixTree : TreeWalk.Fs {

    override fun lstat(path: String): TreeWalk.Stat? = try {
        val st = Os.lstat(path)
        val mode = st.st_mode
        val type = when {
            OsConstants.S_ISLNK(mode) -> TreeWalk.Type.LINK
            OsConstants.S_ISDIR(mode) -> TreeWalk.Type.DIRECTORY
            OsConstants.S_ISREG(mode) -> TreeWalk.Type.FILE
            else -> TreeWalk.Type.OTHER
        }
        TreeWalk.Stat(type, st.st_dev, st.st_ino, st.st_nlink, st.st_size, st.st_mtime * 1000L)
    } catch (e: ErrnoException) {
        null
    }

    override fun list(path: String): Array<String>? = File(path).list()
}
