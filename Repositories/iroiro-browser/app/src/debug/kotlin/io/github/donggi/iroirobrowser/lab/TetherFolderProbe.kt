package io.github.donggi.iroirobrowser.lab

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.donggi.iroirobrowser.tether.SharedFolder
import io.github.donggi.iroirobrowser.tether.TetherActivity
import io.github.donggi.iroirobrowser.tether.TetherSettings
import io.github.donggi.iroirobrowser.tether.core.SharedOps
import io.github.donggi.iroirobrowser.tether.core.SharedPaths
import io.github.donggi.iroirobrowser.tether.core.Thumbnails
import kotlin.concurrent.thread

/**
 * **디버그 빌드에만 있다.** 테더 파일 서버의 SAF 쪽(앱의 `SharedFolder`)을 에뮬레이터에서 직접 돌려 본다 — 파일 서버는 손님 거르기 때문에
 * 에뮬레이터 안에서도 PC 에서도 닿지 않으므로(그룹에 들 다른 기기가 없다) 서버 대신 그 아래의 폴더 구현과 관리 기능([SharedOps])을 부른다.
 * `:tether` 프로세스에서 돈다(매니페스트). 결과는 logcat 의 `WebProbe` 에 — 공유 폴더 안의 이름은 이 탐침이 만든 것만 적는다.
 *
 * extras: `do`=`screen` 이면 테더 화면을 연다(내보내지 않은 화면을 adb 로 열려고). 없으면 탐침: 맨 위의 그림·동영상·소리마다 섬네일을 지어
 * 크기·형식을 적고, 맨 위에 `web-probe-…` 폴더를 만들어 파일 쓰기·이름 바꾸기·복사·옮기기·휴지통·되살리기·비우기를 차례로 하고 지운다.
 */
class TetherFolderProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra("do") == "screen") {
            startActivity(Intent(this, TetherActivity::class.java))
        } else {
            val context = applicationContext
            thread(name = "web-probe") { runCatching { probe(context) }.onFailure { Log.e(TAG, "탐침 실패", it) } }
        }
        finish()
    }

    private fun probe(context: Context) {
        val settings = TetherSettings(context)
        val folder = SharedFolder(context, settings)
        Log.i(TAG, "manageAllowed=${settings.manageAllowed} available=${folder.isAvailable()}")
        if (!folder.isAvailable()) return
        val storage = folder.storage()
        Log.i(TAG, "storage free=${storage?.free} total=${storage?.total}")
        val root = folder.find(emptyList())!!
        val children = folder.list(root)
        Log.i(TAG, "root children=${children.size}")
        for (entry in children.filter { !it.isDirectory }) {
            val mime = SharedPaths.guessMimeType(entry.name)
            if (!mime.startsWith("image/") && !mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            val started = System.nanoTime()
            val bytes = folder.thumbnail(entry, Thumbnails.SMALL)
            val ms = (System.nanoTime() - started) / 1_000_000
            Log.i(TAG, "thumb kind=${mime.substringBefore('/')} ext=${entry.name.substringAfterLast('.')} size=${entry.size} -> ${bytes?.size} ${bytes?.let { Thumbnails.sniff(it) }} ${ms}ms")
        }

        val ops = SharedOps(folder)
        val dir = "web-probe-${System.currentTimeMillis()}"
        val made = ops.makeDirectory(emptyList(), dir)
        Log.i(TAG, "mkdir ${made.name}")
        val file = folder.createFile(made, "a.txt")
        folder.openWrite(file)!!.use { it.write("hello".toByteArray()) }
        val renamed = ops.rename(listOf(dir, "a.txt"), "b.txt")
        Log.i(TAG, "rename -> ${renamed.name}")
        val sub = ops.makeDirectory(listOf(dir), "sub")
        Log.i(TAG, "copy ${outcome(ops.transfer(listOf(dir), listOf("b.txt"), listOf(dir, "sub"), copy = true, conflict = SharedOps.Conflict.ASK))}")
        Log.i(TAG, "copy-same ${outcome(ops.transfer(listOf(dir), listOf("b.txt"), listOf(dir), copy = true, conflict = SharedOps.Conflict.RENAME))}")
        Log.i(TAG, "move-rename ${outcome(ops.transfer(listOf(dir), listOf("b.txt"), listOf(dir, "sub"), copy = false, conflict = SharedOps.Conflict.RENAME))}")
        try {
            ops.transfer(listOf(dir), listOf("sub"), listOf(dir, "sub"), copy = false, conflict = SharedOps.Conflict.ASK)
            Log.e(TAG, "self-move NOT rejected")
        } catch (e: Exception) {
            Log.i(TAG, "self-move rejected: ${e.message}")
        }
        Log.i(TAG, "sub now ${folder.list(sub).map { it.name }.sorted()} dir now ${folder.list(made).map { it.name }.sorted()}")
        Log.i(TAG, "trash ${outcome(ops.trash(listOf(dir), listOf("sub")))}")
        val bin = folder.find(listOf(SharedPaths.TRASH_NAME))
        Log.i(TAG, "bin has nomedia=${bin?.let { b -> folder.list(b).any { it.name == SharedOps.NO_MEDIA && !it.isDirectory } }}")
        Log.i(TAG, "maintain(30) purged=${ops.maintainTrash(30).purged}")
        val items = ops.trashList().filter { it.from == dir }
        Log.i(TAG, "trash list ${items.map { "${it.name} from=${it.from} dir=${it.isDirectory}" }}")
        Log.i(TAG, "restore ${outcome(ops.restore(items.map { it.id }))}")
        Log.i(TAG, "dir after restore ${folder.list(ops.directory(listOf(dir))).map { it.name }.sorted()}")
        Log.i(TAG, "trash dir ${outcome(ops.trash(emptyList(), listOf(dir)))}")
        val left = ops.trashList().filter { it.name == dir }
        Log.i(TAG, "purge ${ops.purge(left.map { it.id })} root has probe=${folder.list(root).any { it.name == dir }}")
        // 탐침이 휴지통을 처음 만들었으면(다른 것이 없으면) 휴지통 폴더까지 걷는다 — 비우기는 휴지통 폴더와 .nomedia 를 남기므로 폴더째.
        if (ops.trashList().isEmpty()) {
            ops.purge(null)
            Log.i(TAG, "after purge-all bin=${folder.find(listOf(SharedPaths.TRASH_NAME))?.let { b -> folder.list(b).map { it.name } }}")
            folder.find(listOf(SharedPaths.TRASH_NAME))?.let { folder.delete(it) }
        }
        Log.i(TAG, "done")
    }

    private fun outcome(o: SharedOps.Outcome) = "done=${o.done} skipped=${o.skipped} failed=${o.failed}"

    private companion object {
        const val TAG = "WebProbe"
    }
}
