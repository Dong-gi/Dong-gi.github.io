package io.github.donggi.iroiroviewer.io

import android.os.FileObserver
import android.system.ErrnoException
import android.system.Os
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 보고 있는 폴더가 바뀌었는가를 아는 두 길 — **통지**([DirectoryWatcher])와 **대조**([DirStamp]).
 *
 * ## 왜 둘인가
 *
 * `FileObserver`(inotify)는 커널이 그 마운트를 지나는 조작을 볼 때만 알린다. 안드로이드 11 부터
 * `/storage/emulated/0` 와 SD 는 FUSE 위에 있고, 다른 앱이 FUSE 를 지나지 않는 길(MediaProvider 가
 * 아래 파일시스템에 직접 쓰는 것, FUSE 패스스루로 도는 쓰기)로 바꾼 것은 **통지가 오지 않을 수 있다.**
 * 그리고 화면이 멈춘 동안에는 일부러 듣지 않는다. 그래서 화면으로 돌아올 때 폴더의 수정 시각을
 * 읽은 때의 값과 한 번 견준다 — 이것은 싸다(stat 한 번).
 *
 * **무엇을 쟀고 무엇을 가정했는가.** 둘 다 기기에서 재지 않았다(이 단계는 에뮬레이터 검증을 뒤로
 * 미뤘다). 가정은 셋이다 — ① 같은 앱이 FUSE 로 한 조작은 inotify 에 잡힌다, ② 폴더의 수정 시각은
 * **바로 아래 항목이 생기거나 지워지거나 이름이 바뀔 때** 바뀐다(POSIX 의 뜻 그대로) — 그래서
 * 이미 있는 파일의 **내용만** 바뀐 것은 대조로 잡지 못한다, ③ FAT(SD)의 폴더 수정 시각은 2초 단위라
 * 읽은 직후 2초 안의 변화는 대조가 놓칠 수 있다.
 */
object DirectoryWatcher {

    /**
     * 폴더 하나를 듣는다. 모을 때(collect)만 듣고, 모으기가 끝나면 곧바로 그친다.
     *
     * **`FileObserver` 를 흐름이 쥔다.** 공식 문서가 "가비지 수집되면 알림이 멎는다" 고 적는다 —
     * `awaitClose` 까지 람다가 참조를 들고 있어야 그 사이에 사라지지 않는다.
     *
     * 알림은 `FileObserver` 의 전용 스레드에서 온다. 막지 않는 `trySend` 로 넘기고, 몰려 오는 것은
     * 하나로 접는다(무엇이 바뀌었는지는 쓰지 않는다 — 다시 읽을지만 정한다).
     *
     * **걸고 푸는 것은 디스크 디스패처에서 한다.** `startWatching` 은 `inotify_add_watch` 라 FUSE 위에서는 경로를
     * 찾는 왕복이 끼고, 부르는 쪽은 주 스레드(`viewModelScope`)다.
     */
    fun changes(path: String): Flow<Unit> = callbackFlow {
        val observer = object : FileObserver(File(path), WatchRules.MASK) {
            override fun onEvent(event: Int, name: String?) {
                if (WatchRules.isRelevant(event, name)) trySend(Unit)
            }
        }
        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.buffer(Channel.CONFLATED).flowOn(IroDispatchers.io)
}

/** 어떤 알림이 목록을 다시 읽을 까닭인가. 순수 함수라 JVM 시험이 지킨다. */
object WatchRules {

    /**
     * 듣는 것. **`MODIFY` 는 뺀다** — 쓰는 동안 조각마다 오므로 1 GB 를 받는 동안 수만 번이 된다.
     * 쓰기가 끝난 것은 `CLOSE_WRITE` 가 한 번 알린다. `ATTRIB` 는 `touch` 처럼 시각만 바꾼 것이다.
     * `DELETE_SELF`·`MOVE_SELF` 는 **보고 있는 폴더 자신**이 사라진 것이라, 다시 읽어야
     * '사라졌습니다' 가 뜬다.
     */
    const val MASK: Int = FileObserver.CREATE or FileObserver.DELETE or
        FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
        FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or
        FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

    /**
     * @param event `onEvent` 가 준 값. **`IN_ISDIR`(0x40000000) 같은 깃발이 섞여 온다** —
     *   `ALL_EVENTS` 로 먼저 걸러야 한다. 거르지 않고 `==` 로 견주면 폴더에 대한 알림이 전부 빠진다.
     * @param name 폴더 안의 이름. 폴더 자신에 대한 알림이면 null.
     */
    fun isRelevant(event: Int, name: String?): Boolean {
        val e = event and FileObserver.ALL_EVENTS
        if ((e and MASK) == 0) return false
        if (name == null) return (e and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF)) != 0
        // **우리 임시 파일은 듣지 않는다.** 복사·풀기·회전이 `.iroiro-XXXXXXXX.part` 를 만들고
        // 쓰고 지우는데, 그때마다 다시 읽으면 2 GB 복사 동안 목록이 쉬지 않고 흔들린다. 제자리에
        // 놓이는 순간(`rename`)의 `MOVED_TO` 는 진짜 이름으로 오므로 그것 하나로 충분하다.
        if (name.startsWith(AtomicFileWriter.PART_PREFIX) && name.endsWith(AtomicFileWriter.PART_SUFFIX)) return false
        // 휴지통 폴더는 목록에 나오지 않는다(`DirectoryLister`). 그 폴더가 생긴 것으로 다시 읽을 까닭이 없다.
        if (name == TrashStore.DIR_NAME) return false
        return true
    }
}

/**
 * 몰려 오는 사건을 한 번으로 묶는 규칙.
 *
 * 흔한 '디바운스'(마지막 사건 뒤 조용하면 내보냄)만 쓰면 **사건이 끊이지 않는 동안 영영 내보내지
 * 않는다** — 다른 앱이 사진 1,000장을 한 장씩 옮겨 넣는 동안 목록이 한 번도 바뀌지 않는다. 그래서
 * 첫 사건에서 [maxWaitMs] 가 지나면 조용하지 않아도 내보낸다.
 */
object SettleRule {

    /** 지금 묶음을 내보낼 때. 마지막 사건 뒤 [quietMs], 그리고 첫 사건 뒤 [maxWaitMs] 가운데 이른 쪽이다. */
    fun deadline(burstStart: Long, lastEvent: Long, quietMs: Long, maxWaitMs: Long): Long =
        minOf(lastEvent + quietMs, burstStart + maxWaitMs)
}

/**
 * [SettleRule] 로 묶어 내보낸다. 위쪽이 끝나면 남은 묶음을 내보내고 끝난다.
 *
 * @param now 밀리초 시계. 시험은 가상 시계를 넘긴다(`TestCoroutineScheduler.currentTime`) —
 *   `withTimeoutOrNull` 이 같은 가상 시간 위에서 돌기 때문에 둘이 어긋나지 않는다.
 */
fun Flow<Unit>.settle(
    quietMs: Long,
    maxWaitMs: Long,
    now: () -> Long = { System.nanoTime() / 1_000_000 },
): Flow<Unit> = channelFlow {
    val events = this@settle.buffer(Channel.CONFLATED).produceIn(this)
    while (true) {
        val first = events.receiveCatching()
        if (first.isClosed) {
            first.exceptionOrNull()?.let { throw it }
            return@channelFlow
        }
        val start = now()
        var last = start
        var closed = false
        while (true) {
            val wait = SettleRule.deadline(start, last, quietMs, maxWaitMs) - now()
            if (wait <= 0) break
            val next = withTimeoutOrNull(wait) { events.receiveCatching() } ?: break
            if (next.isClosed) {
                next.exceptionOrNull()?.let { throw it }
                closed = true
                break
            }
            last = now()
        }
        send(Unit)
        if (closed) return@channelFlow
    }
}

/**
 * 폴더의 수정 시각. **나노초까지** 든다.
 *
 * `StructStat.st_mtime` 은 초 단위라, 읽은 뒤 같은 1초 안에 생긴 파일은 값이 같아 놓친다.
 * `st_mtim`(API 27)은 나노초를 준다. FAT 처럼 시각이 거친 파일시스템에서는 그만큼 거칠다(위 가정 ③).
 */
data class DirStamp(val seconds: Long, val nanos: Long) {
    companion object {
        /** 없거나 읽을 수 없으면 null. */
        fun of(path: String): DirStamp? = try {
            val st = Os.stat(path)
            DirStamp(st.st_mtim.tv_sec, st.st_mtim.tv_nsec)
        } catch (e: ErrnoException) {
            null
        }
    }
}

/**
 * 마지막으로 읽기를 끝낸 폴더와, 그 읽기를 **시작하기 직전**의 수정 시각. 폴더가 없었으면(또는 stat 이 실패했으면)
 * [stamp] 가 null 이다.
 */
data class LastListed(val path: String, val stamp: DirStamp?)

/** 화면으로 돌아왔을 때 다시 읽을 것인가. */
object ForegroundRelist {

    /**
     * 끝난 나열 하나를 '돌아왔을 때 견줄 값' 으로 옮긴다. 아직 읽는 중이면 null(견줄 것이 없다).
     *
     * **실패한 읽기도 시각을 남긴다.** 막힌 폴더(`Android/data`)는 stat 은 되고 나열만 막힌다 — 실패의 시각을
     * 버리고 null 로 적으면 지금 값(null 이 아니다)과 늘 달라, 바뀐 것이 없는데도 화면으로 돌아올 때마다 다시 읽는다.
     */
    fun lastListedOf(path: String, listing: DirectoryLister.Listing): LastListed? = when (listing) {
        is DirectoryLister.Listing.Scanning -> null
        is DirectoryLister.Listing.Ready -> LastListed(path, listing.stamp)
        is DirectoryLister.Listing.Failed -> LastListed(path, listing.stamp)
    }

    /**
     * 읽은 적이 없으면(또는 다른 폴더를 읽었으면) 읽지 않는다 — 그 폴더의 읽기는 이미 돌고 있다.
     * 값이 다르면 읽는다. **없던 폴더가 생긴 것·있던 폴더가 사라진 것**(null 과 값)도 다른 것이다.
     *
     * @param inFlight 지금 보이는 목록이 이 폴더를 **아직 읽는 중**인가. 첫 화면에서 전에 봤던 폴더로 다시 들어오면
     *   [last] 는 그 폴더의 옛 읽기인데, 새 읽기가 이미 돌고 있다 — 거기서 또 다시 읽으면 도는 읽기를 끊고 처음부터 한다.
     */
    fun shouldRelist(last: LastListed?, currentPath: String?, now: DirStamp?, inFlight: Boolean = false): Boolean {
        if (inFlight) return false
        if (last == null || currentPath == null || last.path != currentPath) return false
        return last.stamp != now
    }
}
