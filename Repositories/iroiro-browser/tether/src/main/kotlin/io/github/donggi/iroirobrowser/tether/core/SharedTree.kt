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
 * 그 검사가 느슨해지는 순간 조용히 뚫린다(usb-tether 의 SharedFolder 가 남긴 경고).
 *
 * ## 올리기는 임시 이름으로 받고 다 받은 뒤에 바꾼다
 *
 * 예전 계약은 '올릴 자리를 만들 때 같은 이름의 파일을 지운다' 였다 — 그래서 덮어쓰기 올리기가 중간에 끊기면 새 파일은 지워지고 **원래 파일까지
 * 사라졌다**. 이제 [createUploadTemp] 가 숨은 임시 파일을 만들고(원래 파일은 건드리지 않는다), 본문을 다 받은 뒤에야
 * [commitUpload] 가 원래 것을 지우고 임시를 그 이름으로 바꾼다. 끊기면 임시만 지운다.
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
     * 다 받은 임시 파일 [temp] 를 [path] 의 이름으로 둔다 — 같은 이름의 파일이 있으면 그것을 지운 **뒤에** 이름을 바꾼다. 둔 항목(제공자가 이름을
     * 다듬을 수 있어 실제 이름은 다를 수 있다), 못 했으면 null. 원래 파일을 지우기 전에 실패하면(그 이름이 그새 폴더가 됨·지우지 못함) 임시를
     * 지우고, 원래 것을 지운 뒤 이름 바꾸기가 실패하면 **임시를 남긴다**(받은 내용이 거기에만 있다).
     */
    fun commitUpload(temp: SharedEntry, path: List<String>): SharedEntry?

    /** 지운다(끊긴 올리기의 임시를 남기지 않으려고). */
    fun delete(file: SharedEntry)
}
