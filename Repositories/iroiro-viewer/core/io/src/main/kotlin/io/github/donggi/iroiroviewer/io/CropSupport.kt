package io.github.donggi.iroiroviewer.io

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * 그림 잘라 저장을 파일 작업 큐에 잇는 **이음매**. [ExtractSupport] 와 같은 모양이다. 그리기·메모도 이것을
 * 넓혀 쓴다 — 그림 전체를 뜨고 그 위에 [Overlay] 를 그려 같은 쓰는 쪽([ImageSaver])으로 보낸다.
 *
 * ## 왜 둘로 나누는가 — 뜨는 일과 쓰는 일
 *
 * 잘라 저장은 사용자 저장소에 새 파일을 쓰는 일이라 복사·이동과 **같은 큐**를 지난다(같은 폴더를 두 작업이 동시에 만지지 않게,
 * 진행·취소·포그라운드 서비스·결과 보고를 그대로 얻게). 그런데 **화면이 본 그대로** 뜨는 일은 `core:ui` 가 안다 — EXIF 방향을
 * 디코더가 적용했는지(`ImageIo.probe`·`RegionOrientation`), 화면 방향의 사각형을 저장 방향으로 옮기는 셈(`RegionMath`). 의존 표는
 * `core:io` 가 `core:ui` 를 보는 것을 허락하지 않는다. 그래서 뜨는 일([Renderer])은 `feature:image` 가 하고 `app` 이 꽂으며,
 * 쓰는 일(형식 고르기·임시본·이름 맡기·미디어 색인)은 여기 [ImageSaver] 가 한다 — 사용자 파일을 쓰는 코드는 이 모듈에만 둔다.
 */
object CropSupport {

    /**
     * 화면이 고른 자리. 좌표는 **화면 방향**(EXIF 를 적용해 보인 모양)의 원본 화소이고, 오른·아래는 포함하지 않는다.
     *
     * @param expectedWidth·expectedHeight 고를 때 본 원본의 화면 방향 치수. 큐가 돌 때 파일이 그사이 바뀌었으면(회전 저장·덮어쓰기)
     *   자리가 어긋나므로 [Rendered.Changed] 로 거절한다.
     * @param maxPixels 결과 한 장의 화소 상한. 넘으면 줄여 뜨고 [Rendered.Ok.downscaled] 를 켠다 — 말없이 줄이지 않는다.
     * @param overlay 뜬 자리 위에 그릴 것(그리기·메모). null 이면 잘라 저장 그대로다.
     */
    data class Ask(
        val path: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val expectedWidth: Int,
        val expectedHeight: Int,
        val maxPixels: Long,
        val overlay: Overlay? = null,
    )

    /**
     * 뜬 그림 위에 **뜬 해상도로** 그릴 것 — 그리기·메모의 획과 글 메모. `core:io` 는 내용을 모른다: 화면이 만든 것을
     * 요청에 실어 [Renderer] 에 그대로 넘기고, 그리는 법은 그것을 만든 쪽(`feature:image`)이 안다. 화면이 그리는 코드와 같은 코드가
     * 배율만 바꿔 그려야 화면에서 본 자리·굵기·글자 크기 그대로 남는다.
     *
     * 구현은 불변이어야 한다 — 큐에 줄 선 동안 화면이 다음 그림을 그려도 이 요청의 것은 그대로다. `toString` 에 메모의 글을 싣지
     * 않는다(요청이 로그에 찍힐 수 있다).
     */
    fun interface Overlay {
        /** [canvas] 는 뜬 그림 위다 — 원점이 그림의 왼쪽 위, 그림은 [width]×[height] 화소(보이는 방향). */
        fun draw(canvas: Canvas, width: Int, height: Int)
    }

    sealed interface Rendered {
        /**
         * 뜬 그림. [bitmap] 은 받는 쪽이 다 쓰고 `recycle` 한다(아직 아무도 그리지 않은 비트맵이다). [sourceMime] 은 원본의 형식이고
         * 결과 형식을 고르는 데 쓴다.
         */
        class Ok(val bitmap: Bitmap, val sourceMime: String?, val downscaled: Boolean) : Rendered

        /** 고른 뒤에 파일이 바뀌었다. */
        data object Changed : Rendered

        /** 이 그림은 잘라 저장하지 않는다(움직이는 그림, 디코더가 없는 형식). */
        data object Unsupported : Rendered

        /** 열지 못했다(없어졌다·읽을 수 없다·깨졌다). */
        data object Unreadable : Rendered
    }

    /** 실제로 뜨는 것. `feature:image` 가 구현한다. [Ask.overlay] 가 있으면 뜬 그림 위에 그것을 그려서 준다. */
    fun interface Renderer {
        suspend fun render(ask: Ask): Rendered
    }

    /**
     * **`app` 이 꽂지 않으면 잘라 저장 요청은 실패로 끝난다**([ExtractSupport.runner] 와 같은 판단) — 조용히 성공을 말하면 사용자는
     * 잘라 낸 그림이 있는 줄 안다.
     */
    @Volatile
    var renderer: Renderer? = null
}
