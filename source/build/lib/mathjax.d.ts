/**
 * `@mathjax/src` 는 타입 선언을 함께 주지 않는다(4.1.3 기준). 여기서 최소한만 적어
 * `tsc --noEmit` 이 조용하게 한다.
 *
 * 반환값을 `any` 로 두는 것은 의도한 것이다. 우리가 쓰는 `MathJax.startup.*` 은 문서화된
 * 표면이 아니라 내부 구조이고(`lib/math.ts` 의 주석 참고), 그 모양을 여기 옮겨 적으면
 * 상류가 바뀔 때 **거짓말하는 타입**이 된다. 그 위험을 안고 있는 자리는 `math.ts` 한
 * 곳뿐이고, 거기에는 구조가 어긋나면 즉시 던지는 검사가 들어 있다.
 */
declare module '@mathjax/src/components/mjs/node-main/node-main.mjs' {
    export function init(config: Record<string, unknown>): Promise<any>;
}
