package io.github.donggi.iroiroviewer.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 파일 조작의 경계 조건.
 *
 * **JVM 테스트로 대신할 수 없다.** 여기서 확인하는 것은 우리 코드의 분기가 아니라
 * 파일시스템의 실제 행동이다 — 볼륨을 넘는 `rename` 이 정말 `EXDEV` 를 내는가,
 * `FileUtils.copy` 가 취소에 어떻게 반응하는가, 휴지통 왕복이 정합한가.
 * 이런 것은 기기(또는 에뮬레이터)에서만 답이 나온다.
 */
@RunWith(AndroidJUnit4::class)
class FileOpEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var engine: FileOpEngine
    private lateinit var work: File

    /**
     * 앱 전용 외부 디렉터리들. 첫째가 내부 저장소, 둘째가 있으면 SD 카드다.
     *
     * **볼륨 루트가 아니라 이곳을 쓰는 이유**는 권한이다. 여기는 어떤 권한도 없이 쓸 수
     * 있고, 그러면서도 서로 다른 파일시스템이라 EXDEV 가 그대로 성립한다. 시험하려는
     * 것은 '권한이 있는가' 가 아니라 '볼륨을 넘는 이동이 되는가' 다.
     */
    private val roots: List<File> by lazy {
        context.getExternalFilesDirs(null).filterNotNull()
    }

    private val primaryRoot: File get() = roots.first()

    /** 다른 파일시스템인 곳. 없으면 EXDEV 시험을 건너뛴다. */
    private val secondaryRoot: File? get() = roots.getOrNull(1)

    /**
     * 휴지통 시험용 가짜 볼륨. [TrashStore] 가 볼륨 목록을 인자로 받게 설계한 덕분에
     * 실제 볼륨 루트에 쓰지 않고도 같은 코드 경로를 검증할 수 있다.
     */
    private fun fakeVolumes(): List<VolumeRegistry.Volume> = roots.mapIndexed { i, dir ->
        VolumeRegistry.Volume(
            id = if (i == 0) "test-primary" else "test-secondary-$i",
            label = dir.absolutePath,
            path = dir.absolutePath,
            isPrimary = i == 0,
            isRemovable = i > 0,
            totalBytes = 0,
            freeBytes = 0,
        )
    }

    @Before
    fun setUp() {
        engine = FileOpEngine(MediaIndex(context))
        work = File(primaryRoot, "optest-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
        secondaryRoot?.let { File(it, work.name).deleteRecursively() }
        roots.forEach { File(it, TrashStore.DIR_NAME).deleteRecursively() }
    }

    private fun file(parent: File, name: String, size: Int): File =
        File(parent, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(size) { (it % 251).toByte() })
        }

    // ---- EXDEV: 볼륨을 넘는 이동 -----------------------------------------

    @Test
    fun 볼륨을_넘는_rename_은_EXDEV_로_실패한다() {
        val second = secondaryRoot
        assumeNotNull("SD 볼륨이 없으면 이 시험은 의미가 없다", second)
        val src = file(work, "a.txt", 10)
        val dst = File(second!!, "exdev-${System.nanoTime()}.txt")
        try {
            val errno = try {
                Os.rename(src.absolutePath, dst.absolutePath)
                null
            } catch (e: ErrnoException) {
                e.errno
            }
            // 이 전제가 깨지면(같은 볼륨으로 잡히면) 아래의 이동 시험이 EXDEV 경로를
            // 한 번도 타지 않으면서 '통과' 한다. 그래서 먼저 확인한다.
            assertEquals("볼륨 간 rename 은 EXDEV 여야 한다", OsConstants.EXDEV, errno)
        } finally {
            dst.delete()
        }
    }

    @Test
    fun 비어있지_않은_폴더를_다른_볼륨으로_옮긴다() = runBlocking {
        val second = secondaryRoot
        assumeNotNull("SD 볼륨이 없으면 이 시험은 의미가 없다", second)

        val tree = File(work, "tree").apply { mkdirs() }
        file(tree, "one.txt", 100)
        file(File(tree, "sub"), "two.txt", 200)
        file(File(tree, "sub/deeper"), "three.txt", 300)

        val destRoot = File(second!!, work.name).apply { mkdirs() }
        val outcome = engine.move(listOf(tree.absolutePath), destRoot.absolutePath, FileOpEngine.Conflict.SKIP) { }

        assertTrue("이동이 끝나야 한다: $outcome", outcome is FileOpEngine.Outcome.Done)
        assertEquals(0, (outcome as FileOpEngine.Outcome.Done).failed)
        assertFalse("원본이 남아 있으면 이동이 아니다", tree.exists())
        assertTrue(File(destRoot, "tree/one.txt").isFile)
        assertTrue(File(destRoot, "tree/sub/two.txt").isFile)
        assertTrue(File(destRoot, "tree/sub/deeper/three.txt").isFile)
        assertEquals(300, File(destRoot, "tree/sub/deeper/three.txt").length())
    }

    @Test
    fun 같은_볼륨_이동은_내용을_그대로_옮긴다() = runBlocking {
        val src = file(work, "same.bin", 4096)
        val dest = File(work, "dest").apply { mkdirs() }
        val before = src.readBytes()

        val outcome = engine.move(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.SKIP) { }

        assertTrue(outcome is FileOpEngine.Outcome.Done)
        assertFalse(src.exists())
        val moved = File(dest, "same.bin")
        assertTrue(moved.isFile)
        assertTrue("내용이 같아야 한다", before.contentEquals(moved.readBytes()))
    }

    // ---- 원자적 쓰기와 취소 ----------------------------------------------

    @Test
    fun 취소하면_반쪽_파일이_남지_않는다() = runBlocking {
        // 취소가 복사 도중에 걸리도록 충분히 크게 만든다.
        val src = file(work, "big.bin", 48 * 1024 * 1024)
        val dest = File(work, "dest").apply { mkdirs() }

        val job = launch {
            engine.copy(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.SKIP) { }
        }
        delay(120)
        job.cancel()
        // 취소가 실제로 전파될 시간을 준다.
        withTimeoutOrNull(5_000) { job.join() }

        val leftovers = dest.listFiles()?.filter { it.name.endsWith(".iroiro-part") }.orEmpty()
        assertTrue("임시 파일이 남았다: ${leftovers.map { it.name }}", leftovers.isEmpty())
        // 결과가 있다면 온전한 것이어야 한다. 반쪽 파일이 제자리에 놓이는 일은 없어야 한다.
        val copied = File(dest, "big.bin")
        if (copied.exists()) assertEquals(src.length(), copied.length())
    }

    @Test
    fun 덮어쓰기가_실패해도_원본은_남는다() = runBlocking {
        val src = file(work, "src.bin", 1024)
        val dest = File(work, "dest").apply { mkdirs() }
        val existing = file(dest, "src.bin", 64)
        val existingBytes = existing.readBytes()

        // 건너뛰기면 대상이 그대로 있어야 한다.
        val outcome = engine.copy(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.SKIP) { }
        assertEquals(1, (outcome as FileOpEngine.Outcome.Done).skipped)
        assertTrue(existingBytes.contentEquals(existing.readBytes()))

        // 덮어쓰기면 온전히 바뀌어야 한다(반쪽이 아니라).
        engine.copy(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.OVERWRITE) { }
        assertEquals(1024, existing.length())
        assertTrue(src.readBytes().contentEquals(File(dest, "src.bin").readBytes()))
    }

    @Test
    fun 둘_다_보관하면_번호가_붙는다() = runBlocking {
        val src = file(work, "photo.jpg", 128)
        val dest = File(work, "dest").apply { mkdirs() }
        file(dest, "photo.jpg", 1)

        engine.copy(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.KEEP_BOTH) { }

        assertTrue(File(dest, "photo (2).jpg").isFile)
        assertEquals("확장자가 살아 있어야 한다", 128, File(dest, "photo (2).jpg").length())
    }

    // ---- 안전장치 --------------------------------------------------------

    @Test
    fun 자기_안으로_옮기려_하면_거부한다() = runBlocking {
        val outer = File(work, "outer").apply { mkdirs() }
        val inner = File(outer, "inner").apply { mkdirs() }
        val outcome = engine.move(listOf(outer.absolutePath), inner.absolutePath, FileOpEngine.Conflict.SKIP) { }
        assertTrue(outcome is FileOpEngine.Outcome.Failed)
        assertEquals(
            FileOpEngine.Reason.TARGET_INSIDE_SOURCE,
            (outcome as FileOpEngine.Outcome.Failed).reason,
        )
    }

    @Test
    fun 심볼릭_링크는_따라가지_않는다() = runBlocking {
        val target = File(work, "target").apply { mkdirs() }
        file(target, "secret.txt", 10)
        val link = File(work, "link")
        val made = try {
            Os.symlink(target.absolutePath, link.absolutePath)
            true
        } catch (e: ErrnoException) {
            false
        }
        // FAT 볼륨이나 FUSE 설정에 따라 심링크를 못 만들 수 있다. 그때는 시험할 것이 없다.
        if (!made) return@runBlocking

        val dest = File(work, "dest").apply { mkdirs() }
        engine.copy(listOf(link.absolutePath), dest.absolutePath, FileOpEngine.Conflict.SKIP) { }
        assertFalse("링크를 따라가 내용을 복제했다", File(dest, "link/secret.txt").exists())
    }

    @Test
    fun 계획은_재귀로_파일_수와_바이트를_센다() = runTest {
        file(work, "a.bin", 100)
        file(File(work, "s1"), "b.bin", 200)
        file(File(work, "s1/s2"), "c.bin", 300)
        val plan = engine.plan(listOf(work.absolutePath))
        assertEquals(3, plan.files)
        assertEquals(600L, plan.bytes)
    }

    // ---- 휴지통 ----------------------------------------------------------

    @Test
    fun 휴지통_왕복이_원래_자리로_돌아온다() = runBlocking {
        val store = TrashStore(context, MediaIndex(context))
        val volumes = fakeVolumes()
        val victim = file(work, "지울것.txt", 321)
        val originalBytes = victim.readBytes()

        val moved = store.moveToTrash(listOf(victim.absolutePath), volumes)
        assertTrue("휴지통으로 보내야 한다: $moved", moved is TrashStore.Result.Done)
        assertFalse("원래 자리에 남아 있으면 삭제가 아니다", victim.exists())

        // 기록이 남아 있어야 한다. 이것이 정본이다.
        val entry = io.github.donggi.iroiroviewer.data.IroiroDatabase.get(context).trash()
            .olderThan(System.currentTimeMillis() + 1000)
            .firstOrNull { it.originalName == "지울것.txt" }
        assertNotNull("휴지통 기록이 없다", entry)

        val restored = store.restore(entry!!.uuid, volumes)
        assertTrue("되돌려야 한다: $restored", restored is TrashStore.Result.Done)
        assertTrue("원래 자리에 돌아와야 한다", victim.exists())
        assertTrue(originalBytes.contentEquals(victim.readBytes()))
    }

    @Test
    fun 휴지통_사이드카에는_경로를_적지_않는다() = runBlocking {
        val store = TrashStore(context, MediaIndex(context))
        val volumes = fakeVolumes()
        val victim = file(work, "민감한이름.txt", 10)
        store.moveToTrash(listOf(victim.absolutePath), volumes)

        val trashDir = File(primaryRoot, TrashStore.DIR_NAME)
        val sidecars = trashDir.listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()
        assertTrue("사이드카가 없다", sidecars.isNotEmpty())
        // 모든 파일 접근 권한을 가진 다른 앱이 이 폴더를 읽을 수 있다. 거기에
        // '무엇을 언제 지웠는가' 가 평문으로 쌓이면 안 된다.
        sidecars.forEach { s ->
            val text = s.readText()
            assertFalse("사이드카에 원래 경로가 있다: $text", text.contains(work.name))
            assertFalse("사이드카에 원래 이름이 있다: $text", text.contains("민감한이름"))
        }
    }

    @Test
    fun 이름_규칙이_잘못된_이름을_막는다() {
        assertTrue(PathRules.validate("정상 이름.txt") is PathRules.Verdict.Ok)
        assertTrue(PathRules.validate("a/b") is PathRules.Verdict.Rejected)
        assertTrue(PathRules.validate("") is PathRules.Verdict.Rejected)
        assertTrue(PathRules.validate("끝에점.") is PathRules.Verdict.Rejected)
        // 한글은 UTF-8 로 3바이트다. 글자 수로 재면 이 시험이 통과해 버린다.
        val long = "가".repeat(90) + ".txt"
        assertTrue(PathRules.validate(long) is PathRules.Verdict.Rejected)
        // 앞뒤 공백은 다듬어 준다. 이름 자체를 거부할 이유는 없다.
        assertEquals("a b", (PathRules.validate("  a b  ") as PathRules.Verdict.Ok).name)
    }

    @Test
    fun 이름을_자를_때_확장자를_살린다() {
        val long = "나".repeat(200) + ".jpg"
        val cut = PathRules.sanitize(long)
        assertTrue("확장자가 사라졌다: $cut", cut.endsWith(".jpg"))
        assertTrue("바이트 상한을 넘었다", cut.toByteArray().size <= PathRules.MAX_NAME_BYTES)
    }

    // ---- 3·4단계 적대적 검토에서 나온 회귀 ------------------------------

    /**
     * 상한에 닿은 이름에서 `nextAvailable` 이 **있는 이름을 돌려주면 안 된다.**
     *
     * 예전에는 `"$base ($n)$ext"` 를 만든 뒤 255바이트로 잘랐다. 원래 이름이 이미
     * 255바이트면 ` (2)` 가 통째로 잘려 후보가 원본과 같아지고, 9,999번 헛돈 끝에
     * 그 이름을 그대로 돌려줬다. 그것을 '둘 다 보관' 의 대상 이름으로 쓴 복사는
     * 남의 파일을 지우고 그 자리에 썼다.
     */
    @Test
    fun 상한에_닿은_이름에서도_없는_이름만_돌려준다() {
        // 정확히 255바이트: ASCII 251자 + ".jpg"
        val name = "a".repeat(251) + ".jpg"
        assertEquals(255, name.toByteArray().size)
        val existing = File(work, name)
        assertTrue("255바이트 이름을 만들 수 없다", existing.createNewFile())

        val next = PathRules.nextAvailable(work, name)
        assertFalse("이미 있는 이름을 돌려줬다", File(work, next).exists())
        assertTrue("상한을 넘었다: ${next.toByteArray().size}", next.toByteArray().size <= PathRules.MAX_NAME_BYTES)

        // 한글(3바이트) 쪽도 같다.
        val ko = "가".repeat(85)
        assertEquals(255, ko.toByteArray().size)
        assertTrue(File(work, ko).createNewFile())
        val nextKo = PathRules.nextAvailable(work, ko)
        assertFalse("이미 있는 이름을 돌려줬다(한글)", File(work, nextKo).exists())
    }

    /**
     * 파일을 덮어쓰라는 답이 **폴더를 지워라** 로 번역되면 안 된다.
     *
     * 예전에는 `target.delete()` 가 빈 디렉터리를 `rmdir` 로 지우고 그 자리에 파일을
     * 놓았다. 사용자의 폴더가 소리 없이 파일로 바뀐다.
     */
    @Test
    fun 종류가_다른_충돌은_덮어쓰지_않는다() = runTest {
        val src = file(work, "이름.dat", 64)
        val dest = File(work, "대상").apply { mkdirs() }
        val victim = File(dest, "이름.dat").apply { mkdirs() }
        File(victim, "안에있던것.txt").writeText("남아 있어야 한다")

        val outcome = engine.copy(
            listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.OVERWRITE
        ) { }

        assertTrue(outcome is FileOpEngine.Outcome.Done)
        assertEquals("실패로 세야 한다", 1, (outcome as FileOpEngine.Outcome.Done).failed)
        assertTrue("폴더가 사라졌다", victim.isDirectory)
        assertTrue("폴더 안의 파일이 사라졌다", File(victim, "안에있던것.txt").isFile)
    }

    /**
     * **한 항목의 실패가 배치를 끝내지 않는다.**
     *
     * 예전에는 `transfer` 의 예외를 소스 루프 **바깥**에서 받아, 3번째가 실패하면
     * 나머지를 아예 시도하지 않고 진행 수까지 버렸다.
     */
    @Test
    fun 한_항목이_실패해도_나머지를_계속_옮긴다() = runTest {
        val ok1 = file(work, "1.bin", 128)
        val broken = File(work, "2.bin")
        val ok2 = file(work, "3.bin", 128)
        val dest = File(work, "대상").apply { mkdirs() }

        // 읽을 수 없는 소스: 이름만 있고 실체가 없다.
        assertFalse(broken.exists())

        val outcome = engine.copy(
            listOf(ok1.absolutePath, broken.absolutePath, ok2.absolutePath),
            dest.absolutePath,
            FileOpEngine.Conflict.SKIP,
        ) { }

        assertTrue("배치가 통째로 끝났다: $outcome", outcome is FileOpEngine.Outcome.Done)
        val done = outcome as FileOpEngine.Outcome.Done
        assertEquals("둘은 옮겨져야 한다", 2, done.moved)
        assertEquals(1, done.failed)
        assertTrue(File(dest, "1.bin").isFile)
        assertTrue("실패 뒤의 항목이 처리되지 않았다", File(dest, "3.bin").isFile)
    }

    /**
     * 복사가 **수정시각을 보존한다.**
     *
     * 보존하지 않으면 SD↔내부 이동 한 번에 사진 수천 장이 전부 '지금' 이 되어 날짜
     * 정렬이 무너지고, 이어보기 키 `sha256(크기+이름+수정시각)` 도 함께 끊긴다.
     */
    @Test
    fun 복사가_수정시각을_보존한다() = runTest {
        val src = file(work, "오래된것.bin", 1024)
        val past = System.currentTimeMillis() - 400L * 24 * 3600 * 1000
        assertTrue("수정시각을 바꿀 수 없는 파일시스템이다", src.setLastModified(past))
        val dest = File(work, "대상").apply { mkdirs() }

        engine.copy(listOf(src.absolutePath), dest.absolutePath, FileOpEngine.Conflict.SKIP) { }

        val copied = File(dest, "오래된것.bin")
        assertTrue(copied.isFile)
        // 파일시스템 해상도(FAT 은 2초)를 감안해 여유를 둔다.
        assertTrue(
            "수정시각이 보존되지 않았다: ${copied.lastModified()} vs $past",
            Math.abs(copied.lastModified() - past) < 3000,
        )
    }

    /**
     * 정합성 검사가 **기록 없는 파일을 지우지 않는다.**
     *
     * 예전에는 사이드카의 시각을 못 읽으면 그 자리에서 영구 삭제했다. 그것은 '무엇인지
     * 모르겠다' 를 '지워도 된다' 로 읽는 것이었고, 휴지통으로 옮기는 중이던 파일이
     * 그 판정에 걸릴 수 있었다.
     */
    @Test
    fun 정합성_검사가_고아를_지우지_않고_되살린다() = runBlocking {
        val store = TrashStore(context, MediaIndex(context))
        val volumes = fakeVolumes()
        val trashDir = File(primaryRoot, TrashStore.DIR_NAME).apply { mkdirs() }

        // 기록도 사이드카도 없는 UUID 이름의 파일. 사람이 잃으면 안 되는 바이트다.
        val uuid = java.util.UUID.randomUUID().toString()
        val orphan = File(trashDir, uuid)
        orphan.writeBytes(ByteArray(777) { 7 })

        store.reconcileAndPurgeExpired(volumes, engine)

        assertTrue("고아를 지워 버렸다", orphan.isFile)
        assertEquals(777, orphan.length().toInt())
        val record = io.github.donggi.iroiroviewer.data.IroiroDatabase.get(context).trash().find(uuid)
        assertNotNull("목록에 띄우지 않으면 사용자는 이것이 있는지도 모른다", record)
        assertEquals(TrashStore.UNKNOWN_PARENT, record!!.originalParent)

        // 뒤처리
        io.github.donggi.iroiroviewer.data.IroiroDatabase.get(context).trash().delete(record)
        orphan.delete()
        Unit
    }

    /**
     * 볼륨이 빠진 항목은 **기록을 지우지 않는다.**
     *
     * 예전에는 볼륨을 못 찾아도 `dao.delete(record)` 가 그대로 돌아, 카드 안에는
     * uuid 덩어리가 남는데 목록에서만 사라졌다. 사용자 눈에는 지워진 것으로 보이고
     * 용량은 그대로다.
     */
    @Test
    fun 볼륨이_없으면_기록을_지우지_않는다() = runBlocking {
        val store = TrashStore(context, MediaIndex(context))
        val volumes = fakeVolumes()
        val victim = file(work, "카드에있던것.txt", 50)
        store.moveToTrash(listOf(victim.absolutePath), volumes)
        val dao = io.github.donggi.iroiroviewer.data.IroiroDatabase.get(context).trash()
        val entry = dao.olderThan(System.currentTimeMillis() + 1000)
            .first { it.originalName == "카드에있던것.txt" }

        // 볼륨 목록이 비었다 = 카드가 빠졌다.
        val result = store.purge(listOf(entry.uuid), emptyList(), engine)

        assertTrue(result is TrashStore.Result.Done)
        assertEquals("지우지도 않았는데 성공으로 셌다", 0, (result as TrashStore.Result.Done).done)
        assertNotNull("파일이 남아 있는데 기록만 지웠다", dao.find(entry.uuid))
        assertTrue("파일은 그대로여야 한다", File(File(primaryRoot, TrashStore.DIR_NAME), entry.uuid).exists())

        store.purge(listOf(entry.uuid), volumes, engine)
        Unit
    }

    // ---- 6단계: EXIF 회전 -------------------------------------------------

    /**
     * 방향 태그가 있는 JPEG 을 만든다. 픽셀 내용은 상관없고 EXIF 가 붙는 것이 요점이다.
     */
    private fun jpegWithOrientation(dir: File, name: String, orientation: Int): File {
        dir.mkdirs()
        val f = File(dir, name)
        val bmp = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        androidx.exifinterface.media.ExifInterface(f.absolutePath).apply {
            setAttribute(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                orientation.toString(),
            )
            saveAttributes()
        }
        return f
    }

    private fun orientationOf(f: File): Int =
        androidx.exifinterface.media.ExifInterface(f.absolutePath).getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED,
        )

    /**
     * 회전은 **태그만** 바꾼다. 픽셀을 다시 인코딩하면 화질이 깎이고 HEIC 가 JPEG 이 된다.
     */
    @Test
    fun 회전이_픽셀을_다시_인코딩하지_않는다() = runBlocking {
        val f = jpegWithOrientation(work, "세로사진.jpg", 1)
        val before = f.length()

        val outcome = engine.rotateExif(f.absolutePath, 90)

        assertTrue("회전이 실패했다: $outcome", outcome is FileOpEngine.Outcome.Done)
        assertEquals(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90, orientationOf(f))
        // EXIF 를 고치면 몇 바이트는 움직인다. 다시 인코딩했다면 그 정도로 끝나지 않는다.
        assertTrue("크기가 너무 많이 변했다: $before → ${f.length()}", Math.abs(f.length() - before) < 2048)
        Unit
    }

    /**
     * **수정시각을 보존한다.** 4단계가 치명으로 고친 자리다 — 날짜 정렬이 무너지고
     * 이어보기 키(`sha256(크기+이름+수정시각)`)도 함께 끊긴다.
     */
    @Test
    fun 회전이_수정시각을_보존한다() = runBlocking {
        val f = jpegWithOrientation(work, "오래된사진.jpg", 1)
        val past = System.currentTimeMillis() - 500L * 24 * 3600 * 1000
        assertTrue(f.setLastModified(past))

        engine.rotateExif(f.absolutePath, 180)

        assertTrue(
            "수정시각이 바뀌었다: ${f.lastModified()} vs $past",
            Math.abs(f.lastModified() - past) < 3000,
        )
        Unit
    }

    /**
     * **거울상을 뭉개지 않는다.** 정방향 넷만 다루고 나머지를 NORMAL 로 되돌리면,
     * 좌우가 뒤집힌 사진을 한 번 돌리는 순간 뒤집힘이 조용히 사라진다.
     */
    @Test
    fun 회전이_거울상을_잃지_않는다() = runBlocking {
        val flip = androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        val f = jpegWithOrientation(work, "거울상.jpg", flip)

        engine.rotateExif(f.absolutePath, 90)

        // FLIP_HORIZONTAL(0°, 거울) 을 90° 돌리면 TRANSPOSE(90°, 거울) 다.
        assertEquals(
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE,
            orientationOf(f),
        )
        // 네 번 돌리면 제자리.
        repeat(3) { runBlocking { engine.rotateExif(f.absolutePath, 90) } }
        assertEquals(flip, orientationOf(f))
        Unit
    }

    /**
     * 회전이 **임시 파일을 남기지 않는다.** 그리고 남아 있던 죽은 임시 파일을 걷는다 —
     * 회전은 어느 폴더에서나 일어나는데, 지금까지 임시 파일 청소는 복사·이동의 *대상*
     * 폴더에서만 돌았다.
     */
    @Test
    fun 회전이_임시파일을_남기지_않고_옛것도_걷는다() = runBlocking {
        val f = jpegWithOrientation(work, "사진.jpg", 1)
        // 죽은 작업이 남긴 것처럼 꾸민다(한 시간보다 오래됐어야 걷힌다).
        val stale = File(work, "${FileOpEngine.PART_PREFIX}deadbeef${FileOpEngine.PART_SUFFIX}")
        stale.writeBytes(ByteArray(1024))
        assertTrue(stale.setLastModified(System.currentTimeMillis() - 3 * 60 * 60 * 1000L))

        engine.rotateExif(f.absolutePath, 90)

        assertFalse("죽은 임시 파일이 남았다", stale.exists())
        val leftovers = work.listFiles { x -> x.name.startsWith(FileOpEngine.PART_PREFIX) }.orEmpty()
        assertEquals("임시 파일이 남았다: ${leftovers.map { it.name }}", 0, leftovers.size)
        Unit
    }

    /** 실패해도 **원본은 바이트 하나 안 바뀐다.** */
    @Test
    fun 회전이_실패해도_원본이_그대로다() = runBlocking {
        // EXIF 를 붙일 수 없는 파일. saveAttributes 가 던진다.
        val f = file(work, "사진아님.jpg", 300)
        val before = f.readBytes()
        val modified = f.lastModified()

        val outcome = engine.rotateExif(f.absolutePath, 90)

        assertTrue("실패로 끝나야 한다: $outcome", outcome is FileOpEngine.Outcome.Failed)
        assertTrue("원본이 바뀌었다", before.contentEquals(f.readBytes()))
        assertEquals(modified, f.lastModified())
        assertEquals(0, work.listFiles { x -> x.name.startsWith(FileOpEngine.PART_PREFIX) }.orEmpty().size)
        Unit
    }

    /** 0도 회전은 파일을 건드리지 않는다. */
    @Test
    fun 회전_0도는_파일을_열지도_않는다() = runBlocking {
        val f = jpegWithOrientation(work, "그대로.jpg", 1)
        val before = f.readBytes()
        val outcome = engine.rotateExif(f.absolutePath, 360)
        assertTrue(outcome is FileOpEngine.Outcome.Done)
        assertTrue(before.contentEquals(f.readBytes()))
        Unit
    }
}
