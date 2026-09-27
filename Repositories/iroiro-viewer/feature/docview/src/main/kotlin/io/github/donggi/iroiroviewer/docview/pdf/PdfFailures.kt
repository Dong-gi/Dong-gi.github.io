package io.github.donggi.iroiroviewer.docview.pdf

import io.github.donggi.iroiroviewer.format.OpenFailure
import io.github.donggi.iroiroviewer.format.toOpenFailure
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * pdfium 이 던진 것을 **공용 매핑에 넘기기 전에 우리가 먼저 가린다.**
 *
 * ## 왜 공용 매핑으로는 안 되는가
 *
 * 레거시 `PdfRenderer` 가 실패를 알리는 방법은 둘뿐이고, `toOpenFailure()` 는 그 둘을
 * **정확히 뒤바뀐 뜻으로** 옮긴다.
 *
 * * 암호가 걸린 PDF → 생성자에서 `SecurityException`.
 *   `toOpenFailure()` 는 그것을 `NoPermission("읽을 권한이 없다")` 로 만든다 —
 *   화면에는 '읽을 권한이 없습니다' 가 뜨고 **사용자는 권한 설정을 뒤진다.**
 * * 깨진 PDF·PDF 아닌 파일·0바이트 → **전부** `IOException: file not in PDF format or
 *   corrupted`. `toOpenFailure()` 는 `Io("입출력이 실패했다")` 로 만드는데, 그러면
 *   디스크가 고장 난 것처럼 읽힌다. 파일이 있고 읽을 수 있다는 것은 우리가 먼저
 *   확인했으므로 이 시점의 `IOException` 은 **내용** 쪽이다.
 *
 * `SecurityException` 이 '암호가 필요하다' 인지 '모르는 보안 처리기다' 인지는 이 예외만으로
 * 갈리지 않는다(pdfium 은 둘 다 같은 예외다). 여기서는 흔한 쪽인 **암호**로 옮기고,
 * 가르는 일은 `PdfOpener` 가 `/Encrypt` 를 직접 읽어서 한다.
 *
 * ## 메시지 문자열로 갈라 보지 않는다
 *
 * 예외 메시지는 플랫폼 판마다 바뀌는 값이고, 그것을 분기의 근거로 삼는 순간 그 문자열이
 * 화면 쪽으로 새는 길이 열린다. **종류만 본다.**
 */
internal object PdfFailures {

    fun of(t: Throwable): OpenFailure {
        // 경계 catch 의 첫 줄. 취소는 실패가 아니다.
        if (t is CancellationException) throw t
        return when (t) {
            // 암호. 화면이 암호를 묻는다(공개 명세라 우리가 풀 줄 안다).
            is SecurityException -> OpenFailure.PasswordRequired("암호가 필요한 문서")
            // 우리가 이미 막았어야 하는 자리(seekable 하지 않은 원본 등). 남아 있으면 우리 결함이다.
            is IllegalArgumentException -> OpenFailure.Unsupported("이 앱이 열 수 없는 PDF")
            is IOException -> OpenFailure.Corrupt("PDF 구조를 읽지 못했다")
            // 상한·메모리는 공용 매핑이 이미 옳게 옮긴다.
            else -> t.toOpenFailure()
        }
    }
}
