package io.github.donggi.iroiroviewer.editor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 디버그 시험대 — **Files 가 할 일을 흉내 낸다**: 앱 전용 외부 폴더(`/sdcard/Android/data/<앱>/files/docs/` — adb 로 민다)의 문서를 고르면
 * 그 사본을 content URI 로 [EditorActivity] 에 넘기고(`startActivityForResult`), 돌려받은 것을 원래 파일 자리에 임시 파일 → 이름 바꾸기로 쓴다.
 *
 * 누르면 편집, 길게 누르면 보기. 형식은 확장자로 정하고, 그 형식의 엔진이 이 빌드에 없으면 시험 엔진(probe)으로 연다.
 */
class DevActivity : Activity() {

    private lateinit var list: ListView
    private var files: List<File> = emptyList()

    /** 열어 둔 문서(돌려받으면 여기에 쓴다). */
    private var target: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = savedInstanceState?.getString(KEY_TARGET)?.let(::File)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(this).apply {
            text = "adb push <파일> ${docsDir().absolutePath}/ — 누르면 편집, 길게 누르면 보기"
            setPadding(32, 32, 32, 16)
        })
        list = ListView(this)
        column.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(column)
        column.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
        list.setOnItemClickListener { _, _, position, _ -> open(files[position], EditorContract.MODE_EDIT) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            open(files[position], EditorContract.MODE_VIEW)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        files = docsDir().listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files.map { "${it.name}  (${it.length()} B)" })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        target?.let { outState.putString(KEY_TARGET, it.path) }
    }

    private fun docsDir(): File = File(getExternalFilesDir(null), "docs").apply { mkdirs() }

    private fun open(file: File, mode: String) {
        val byExt = Kind.entries.firstOrNull { it.ext == file.extension.lowercase() && it != Kind.PROBE }
        val kind = byExt?.takeIf { Engine.forKind(it).installed(assets) } ?: Kind.PROBE
        // Files 의 FileProvider 대신 이 앱의 OutputProvider 로 — 사본을 그 자리에 둔다.
        val copy = File(File(OutputProvider.rootOf(this), "dev-" + UUID.randomUUID()), file.name)
        try {
            copy.parentFile!!.mkdirs()
            file.copyTo(copy, overwrite = true)
        } catch (e: IOException) {
            Toast.makeText(this, "사본을 만들지 못했다", Toast.LENGTH_LONG).show()
            return
        }
        target = file
        val intent = Intent(this, EditorActivity::class.java)
            .setAction(EditorContract.ACTION_OPEN)
            .setData(OutputProvider.uriFor(this, copy))
            .putExtra(EditorContract.EXTRA_KIND, kind.id)
            .putExtra(EditorContract.EXTRA_MODE, mode)
            .putExtra(EditorContract.EXTRA_NAME, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST)
    }

    @Deprecated("플랫폼 Activity 의 결과 받기")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST) return
        val original = target ?: return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            Toast.makeText(this, "고친 것 없음", Toast.LENGTH_SHORT).show()
            return
        }
        val kind = Kind.of(data.getStringExtra(EditorContract.EXTRA_KIND))
        // 형식이 바뀌었으면(예: HWP 를 HWPX 로) 그 확장자의 새 파일로 — Files 도 그렇게 한다(2단계에서 정한다).
        val dest = if (kind != null && kind != Kind.PROBE && kind.ext != original.extension.lowercase()) {
            File(original.parentFile, original.nameWithoutExtension + "." + kind.ext)
        } else {
            original
        }
        try {
            val tmp = File(dest.parentFile, ".${dest.name}.part")
            contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { out -> input.copyTo(out); out.fd.sync() } }
            if (!tmp.renameTo(dest)) throw IOException("이름을 바꾸지 못했다")
            Toast.makeText(this, "저장: ${dest.name} ${dest.length()} B (${kind?.id})", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "쓰지 못했다: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REQUEST = 1
        const val KEY_TARGET = "target"
    }
}
