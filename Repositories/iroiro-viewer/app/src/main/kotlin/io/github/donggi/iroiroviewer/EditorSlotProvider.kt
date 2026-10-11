package io.github.donggi.iroiroviewer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.donggi.iroiroviewer.io.EditorApp
import java.io.File
import java.io.FileNotFoundException

/**
 * 편집기의 **저장 자리**([EditorSlots]). 내보내지 않는다(`exported=false`) — **이 앱(같은 UID)만** 닿는다: 편집기 화면은 따로
 * 된 프로세스(`:editor`)에서 돌고 이 제공자는 주 프로세스에서 돈다. 두 가지만 받는다:
 *
 * - [openFile] — 쓰기로만 연다(늘 처음부터 — 자르고 쓴다). 자리는 앱 캐시다. 읽기는 주지 않는다. 세션이 없으면 받은 것을 버리는 자리를 준다 —
 *   이어 오는 반영이 '세션 없음' 으로 답해 편집기가 맞는 문장을 보이게(예전에는 여기서 던져 까닭 없는 '저장하지 못했습니다' 만 남았다).
 * - [call] `commit`(인자는 자리의 표, 추가값은 형식·이름) — 다 쓴 것을 원본에 반영한다(파일 작업 큐 — 줄 서서 기다리는 것은 잠깐, 돌기 시작한
 *   것은 끝까지 기다려 답한다 — [EditorSlots.commit]).
 *
 * 둘 다 부른 쪽이 이 앱인지 본다(시스템도 막지만 겹쳐 둔다). 질의·넣기·지우기는 없다.
 */
class EditorSlotProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val token = EditorSlots.tokenOf(uri) ?: throw FileNotFoundException()
        requireWriter()
        if (!mode.contains('w')) throw SecurityException("쓰기로만 연다")
        // 세션이 없다(닫혔다·기록을 잃었다) — 받은 것을 버리는 자리(/dev/null)를 준다. 편집기는 이어서 반영을 부르고, 반영이 '세션 없음' 으로
        // 답한다(편집기가 '내보내기로 남기세요' 를 말한다). 디스크에는 아무것도 쓰이지 않는다.
        val file = EditorSlots.incoming(ctx, token)
            ?: return ParcelFileDescriptor.open(File(DISCARD), ParcelFileDescriptor.MODE_WRITE_ONLY)
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        )
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != EditorApp.METHOD_COMMIT) return null
        val ctx = context ?: return null
        val token = arg?.takeIf { EditorSlots.tokenOf(EditorSlots.uriOf(ctx, it)) == it } ?: throw SecurityException("자리가 아니다")
        requireWriter()
        return EditorSlots.commit(ctx, token, extras?.getString(EditorApp.KEY_KIND), extras?.getString(EditorApp.KEY_NAME))
    }

    /** 부른 쪽이 이 앱인가 — 아니면 던진다(편집기 프로세스든 주 프로세스든 같은 UID 다). */
    private fun requireWriter() {
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("이 앱만 쓴다")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        /** 세션이 없는 자리에 쓰인 것을 버리는 곳. */
        const val DISCARD = "/dev/null"
    }
}
