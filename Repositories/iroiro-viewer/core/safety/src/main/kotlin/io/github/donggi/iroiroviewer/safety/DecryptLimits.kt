package io.github.donggi.iroiroviewer.safety

/**
 * 암호를 풀어 낸 **평문 한 벌을 RAM 에 들 때**의 상한. 포맷과 상관없는 하나의 예산이다.
 *
 * 평문은 저장소에 쓰지 않는다(CLAUDE.md '암호가 걸린 파일') — 그래서 문서 크기만큼 메모리를 쓴다.
 * 지금 쓰는 것은 암호 PDF(API 34 이하, memfd 로 넘긴다 — `PdfLimits.MAX_DECRYPTED_BYTES`) 하나다.
 */
object DecryptLimits {

    /**
     * 256 MiB. 이 앱의 쪽 비트맵 예산(수십 MB)보다 한 자리 크지만, 실물 암호 문서(명세서·논문·보고서)는
     * 대개 수십 MB 안쪽이라 걸리는 일이 드물다. 넘으면 '너무 커서 열 수 없습니다' 로 끝낸다 —
     * 기기가 메모리를 다 내주다 죽는 것보다 낫다.
     */
    const val MAX_BYTES = 256L * 1024 * 1024
}
