package io.github.donggi.iroirobrowser.tether.core

import java.io.InputStream
import java.io.OutputStream

/**
 * 공유 폴더 안의 항목 하나. [ref] 는 구현이 정하는 열쇠(앱은 SAF 의 문서 ID)라 서버는 들여다보지 않는다.
 */
class SharedEntry(
    val ref: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    /** 마지막으로 바뀐 때(epoch ms). 모르면 0. */
    val modified: Long,
)

/**
 * 파일 서버가 내주는 폴더 하나 — 앱은 SAF 트리(사용자가 고른 폴더 하나, `DocumentsContract`)로, 시험은 임시 폴더로 짓는다.
 *
 * 경로는 [SharedPaths.split] 이 이미 거른 **성분 목록**으로만 받는다(빈 목록이 맨 위). 구현은 성분마다 **자식만** 찾아 내려가야 한다 — 그래야
 * 트리 밖으로 나갈 길이 구조로 없다. 성분을 이어 붙여 파일 경로를 만드는 구현(java.io.File 등)은 [SharedPaths.split] 의 검사에 기대게 되어,
 * 그 검사가 느슨해지는 순간 조용히 뚫린다.
 *
 * ## 올리기는 임시 이름으로 받고 다 받은 뒤에 바꾼다
 *
 * 올릴 자리를 만들 때 같은 이름의 파일을 지우면 덮어쓰기 올리기가 중간에 끊길 때 **원래 파일까지 사라진다**. 그래서 [createUploadTemp] 가
 * 숨은 임시 파일을 만들고(원래 파일은 건드리지 않는다), 본문을 다 받은 뒤에야 [commitUpload] 가 임시를 그 이름으로 바꾼다. 끊기면 임시만
 * 지운다. 덮어쓰기면 파일 서버가 그 앞에 원래 것을 **휴지통으로 옮긴다**([SharedOps.trashReplaced]) — [commitUpload] 는 같은 이름을 지우지
 * 않는다(지우면 휴지통을 비켜 간다). 원래 것이 0바이트면 휴지통 대신 숨은 이름으로 비켜 두고 둔 뒤에 지운다([SharedOps.setAside]).
 */
interface SharedTree {
    /** 지금 실제로 쓸 수 있는가 — 고르지 않았거나, 권한이 사라졌거나(사용자가 거둠·앱 다시 깔기·SD 카드 뺌) 폴더가 아니면 false. */
    fun isAvailable(): Boolean

    /** 사용자에게 보일 폴더 이름. 모르면 null. */
    fun rootName(): String?

    /** [path] 의 항목. 없으면 null. 빈 목록은 맨 위 폴더. */
    fun find(path: List<String>): SharedEntry?

    /** 폴더의 자식들. 읽지 못하면 던진다. */
    fun list(directory: SharedEntry): List<SharedEntry>

    /** 읽기 스트림. 열지 못하면 null. */
    fun openRead(file: SharedEntry): InputStream?

    /**
     * [path] 로 올릴 본문을 받을 임시 파일을 만든다 — [path] 의 부모 폴더 안에 숨은 이름([SharedPaths.uploadTempName])으로. **[path] 의 원래
     * 파일은 건드리지 않는다.** 부모가 없거나 [path] 의 이름이 폴더면 null(경로가 틀렸다). 만들지 못하면 던진다(쓸 수 없는 폴더·이름 바꾸기를
     * 못 하는 제공자 — [commitUpload] 가 바꾸지 못할 임시는 만들지 않는다).
     */
    fun createUploadTemp(path: List<String>): SharedEntry?

    /** 쓰기 스트림 — 반드시 잘라 쓴다(`"wt"` — `"w"` 만 주면 짧은 파일로 덮을 때 뒤의 바이트가 남는 구현이 있다). 열지 못하면 null. */
    fun openWrite(file: SharedEntry): OutputStream?

    /**
     * 다 받은 임시 파일 [temp] 를 [path] 의 이름으로 둔다(이름 바꾸기). **같은 이름(대소문자 무시 — 파일이든 폴더든)이 있으면 지우지 않고**
     * 임시를 지운 뒤 null — 덮어쓰기는 부르는 쪽이 원래 것을 휴지통으로 옮긴 뒤에 부른다. 둔 항목(제공자가 이름을 다듬거나 겹치지 않게 바꿀 수
     * 있어 실제 이름은 다를 수 있다), 못 했으면 null. 이름 바꾸기 자체가 실패하면 **임시를 남긴다**(어디까지 됐는지 모른다).
     */
    fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry?

    /**
     * 지운다 — 폴더면 안의 것까지(되살릴 수 없다). 끊긴 올리기의 임시를 남기지 않으려고, 그리고 휴지통을 비울 때 쓴다. 사용자가 고른 것을
     * '지우기' 는 이것이 아니라 휴지통으로 옮기기다([SharedOps.trash]). 지웠으면 true.
     */
    fun delete(entry: SharedEntry): Boolean

    // ── 관리 기능의 바탕(SharedOps 가 쓴다 — 이름 검사·겹침·휴지통은 그쪽이 한다) ─────────────────────────────────────────────

    /** [parent] 안에 폴더 [name] 을 만든다. 만든 항목(제공자가 이름을 다듬으면 실제 이름이 다를 수 있다). 못 하면 던진다. */
    fun createDirectory(parent: SharedEntry, name: String): SharedEntry

    /** [parent] 안에 빈 파일 [name] 을 만든다(휴지통의 기록). 못 하면 던진다. */
    fun createFile(parent: SharedEntry, name: String): SharedEntry

    /** 같은 폴더 안에서 이름만 바꾼다. 바뀐 항목(열쇠가 바뀔 수 있다 — SAF 의 문서 ID). 못 하면 던진다. */
    fun rename(entry: SharedEntry, newName: String): SharedEntry

    /**
     * [entry] 를 [fromParent] 에서 [toParent] 로 옮긴다(이름 그대로 — 겹치지 않는 것은 부르는 쪽이 맞춘다). 옮긴 항목. 제공자가 옮기기를 하지
     * 않으면 null — 부르는 쪽이 복사한 뒤 지운다. 하려다 실패하면 던진다.
     */
    fun move(entry: SharedEntry, fromParent: SharedEntry, toParent: SharedEntry): SharedEntry?

    /** [entry] 를(폴더면 통째로) [toParent] 로 복사한다. 제공자가 복사를 하지 않으면 null — 부르는 쪽이 한 항목씩 짓는다. 실패하면 던진다. */
    fun copy(entry: SharedEntry, toParent: SharedEntry): SharedEntry?

    /** 공유 폴더가 있는 저장소의 남은 공간·전체. 모르면 null. */
    fun storage(): Storage? = null

    /**
     * 섬네일 — 한 변이 대략 [sizePx] 인 그림의 바이트. 없거나 만들 수 없으면 null(화면은 아이콘을 그린다). 구현은 **남이 올린 그림을 이
     * 프로세스에서 풀지 않는 길**을 고른다(앱 — 제공자가 준 섬네일 바이트를 그대로, 동영상은 미디어 서버가 푼 장면) — 파일 서버는 받은
     * 바이트의 머리로 형식을 보고 모르는 것은 내보내지 않는다([Thumbnails.sniff]).
     */
    fun thumbnail(entry: SharedEntry, sizePx: Int): ByteArray? = null
}

/** 저장소의 공간(바이트). */
class Storage(val free: Long, val total: Long)
