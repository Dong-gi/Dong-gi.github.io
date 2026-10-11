package io.github.donggi.iroirobrowser.setup

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import io.github.donggi.iroirobrowser.R
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 설정 파일 묶음([SetupBundle])을 zip 하나로 공용 '내려받기' 폴더에 쓴다 — 설정 › 프록시의 '서버 구성 파일 받기'(주 프로세스)와 테더 화면의
 * 'PC 연결 파일 받기'(`:tether` 프로세스)가 함께 쓴다. 그래서 브라우저의 것(앱의 설정·길·내려받기 목록 …)을 부르지 않는다 — 테더 프로세스에서는
 * 그것이 막혀 있다(`TetherIsolationTest` 가 이 패키지도 본다).
 *
 * `MediaStore.Downloads`(API 29+ — 저장소 권한이 필요 없다)에 감춘 채(IS_PENDING) 넣고, 다 쓴 뒤 드러낸다. 실패하면 감춘 항목을 지운다. 이름이
 * 겹치면 MediaStore 가 ` (1)` 을 붙이고, 토스트가 실제 이름을 적는다. 일은 따로 된 스레드에서 하고 결과는 주 스레드의 토스트 한 줄이다. 도는 동안
 * 다시 누르면 무시한다(같은 zip 이 둘 생기지 않게).
 */
object SetupFiles {

    private const val ZIP_MIME = "application/zip"
    private val busy = AtomicBoolean(false)

    fun save(context: Context, bundle: SetupBundle) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread({
            // 테더 프로세스에서 잡히지 않은 예외는 프로세스를 죽여 테더링(PC 의 인터넷)까지 끊는다 — 무엇이든 잡아 토스트로만 알린다.
            val (message, length) = try {
                app.getString(R.string.setup_files_saved, write(app, bundle)) to Toast.LENGTH_SHORT
            } catch (e: Throwable) {
                app.getString(R.string.setup_files_failed, (e.message ?: e.javaClass.simpleName).take(120)) to Toast.LENGTH_LONG
            } finally {
                busy.set(false)
            }
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, length).show() }
        }, "setup-zip").start()
    }

    /** 자산을 읽어 zip 을 짓고 '내려받기' 에 쓴다 — MediaStore 가 정한 최종 이름을 돌려준다. zip 은 다 지은 뒤에야 항목을 만든다. */
    private fun write(context: Context, bundle: SetupBundle): String {
        val zip = SetupZip.build(
            bundle.topFolder,
            bundle.files.map { path -> path to context.assets.open("${bundle.assetDir}/$path").use { it.readBytes() } },
        )
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, bundle.zipName)
            put(MediaStore.Downloads.MIME_TYPE, ZIP_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("cannot create a download entry")
        try {
            (resolver.openOutputStream(uri, "w") ?: throw IOException("cannot open the download entry")).use { it.write(zip) }
            val shown = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            if (resolver.update(uri, shown, null, null) == 0) throw IOException("cannot publish the download entry")
            return resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: bundle.zipName
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
