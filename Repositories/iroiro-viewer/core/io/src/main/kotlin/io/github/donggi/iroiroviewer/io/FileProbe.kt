package io.github.donggi.iroiroviewer.io

import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 파일이 **실제로 열리는가** — 뷰어가 '깨졌다·다루지 않는다' 와 '읽을 수 없다' 를 가르는 탐침.
 *
 * 둘을 가르는 것이 '다른 앱으로 열기' 의 규칙이다(깨진 파일에는 단추를 달고, 없거나 열리지 않는 파일에는 달지 않는다 —
 * 받는 앱도 같은 벽에 부딪힌다). 그런데 안드로이드는 권한 거부도 `FileNotFoundException`(EACCES)으로 알려서, 여는
 * 도중의 예외만 보면 열리지 않는 파일이 '깨진 파일' 로 떨어지고 단추가 선다.
 *
 * **`canRead()` 로 묻지 않는다.** 묻는 답과 여는 답이 FUSE 위에서 같다는 것을 확인한 적이 없다. 그래서 연다.
 *
 * 처음에는 문서·압축·만화·이미지 뷰어가 이 몇 줄을 모듈마다 한 벌씩 들었다(feature 끼리 참조할 수 없어서다). 판단이 여럿이면
 * 한쪽만 고쳐지는 날이 오므로 여기 하나로 둔다 — 모듈 쪽의 함수는 이것을 부르는 한 줄이고, 시험이 여는 일을 바꿔 끼우는
 * 자리(`open`)만 남았다(JVM 에서는 있는데 열리지 않는 파일을 만들 수 없다).
 *
 * 디스크 입출력이다. 부르는 쪽이 입출력 디스패처에서 부른다.
 */
object FileProbe {

    /** 여는 일의 기본값 — 읽으려고 열었다가 곧바로 닫는다. 모듈 쪽 함수가 시험을 위해 여는 일을 받을 때 이것을 기본값으로 둔다. */
    val openAndClose: (File) -> Unit = { FileInputStream(it).close() }

    /** 열어 보고, 못 열면 그 예외를 준다(열리면 null). 폴더인지는 보지 않는다 — 부르는 쪽이 따로 본다. */
    fun openError(file: File, open: (File) -> Unit = openAndClose): Throwable? = try {
        open(file)
        null
    } catch (e: IOException) {
        e
    } catch (e: SecurityException) {
        e
    }

    /** 보통 파일이고 실제로 열리는가. */
    fun opens(file: File, open: (File) -> Unit = openAndClose): Boolean = file.isFile && openError(file, open) == null
}
