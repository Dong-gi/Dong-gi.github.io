package io.github.donggi.iroiroviewer.io

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 파일 작업이 실패한 까닭의 문장(`FileOpText`)과 휴지통의 짜임.
 */
class FileOpTextTest {

    /**
     * '이미 있다' 는 **제 문장**이다 — 오래 '쓸 수 없는 이름' 으로 말해져, 사용자는 글자가 틀린 줄 알고 처음부터 다시 쳤다.
     * 권한·공간 같은 다른 까닭과도 갈린다(예전의 새 폴더는 못 만들면 언제나 '권한이 없다' 였다).
     */
    @Test
    fun `이미 있음은 제 문장이다`() {
        val exists = FileOpText.reasonRes(FileOpEngine.Reason.EXISTS)
        for (other in FileOpEngine.Reason.entries - FileOpEngine.Reason.EXISTS) {
            assertNotEquals(FileOpText.reasonRes(other), exists, "$other 와 같은 문장")
        }
        assertEquals(R.string.io_error_exists, exists)
    }

    /** 휴지통의 실제 파일은 볼륨 루트의 숨은 폴더 아래 uuid 이름이다 — 썸네일이 그 자리를 읽는다. 목록이 그 폴더를 감춘다. */
    @Test
    fun `휴지통의 실제 파일 자리`() {
        val f = TrashStore.storedFileOf("/storage/emulated/0", "1f2e3d")
        assertEquals(File(File("/storage/emulated/0", TrashStore.DIR_NAME), "1f2e3d"), f)
        assertEquals(true, TrashStore.isTrashPath(f.path.replace(File.separatorChar, '/')))
    }
}
