/**
 * 일본어 그림 모듈이 함께 쓰는 폰트 스택.
 *
 * 왜 lib.mjs 의 FONT 를 쓰지 않는가 — 그것은 `'Segoe UI', 'Noto Sans KR', …` 이고
 * 가나를 한국어 폰트로 그리게 된다. 가나의 획 모양 자체가 4장의 소재이므로
 * 일본어용으로 설계된 가나를 먼저 고르게 한다.
 *
 * 왜 서브셋 웹폰트(`Noto Sans JP Subset`)를 스택 맨 앞에 두지 않는가 —
 * 이 SVG 는 `<img>` 로 실린다. 그러면 본문의 CSS 가 닿지 않으므로
 * source/default.css 의 @font-face 도 닿지 않는다. SVG 안에서 웹폰트를 쓰려면
 * SVG 파일마다 폰트를 data URI 로 박아야 하고, 가나만 넣어도 파일당 20KB 가
 * 붙는다. 그래서 그림 안에서는 시스템 폰트에 맡긴다.
 *
 * 그것이 괜찮은 이유는 원장 §5.1 에 있다 — 그림 안에는 한자를 넣지 않고,
 * 가나에는 지역별 자형 치환이 없다. 폰트가 무엇으로 걸리든 나오는 글자는
 * 옳은 가나다. 서체가 달라질 뿐이다. 한자는 그 보장이 없어서 금지한다.
 *
 * 이 파일은 그림을 내보내지 않는다. figure.ts 가 `japanese-*.mjs` 의 default 를
 * 모두 읽으므로 빈 배열을 둔다.
 */

/** 그림 안에서 쓰는 일본어 폰트 스택. 끝에 사이트 기본 스택을 잇는다. */
export const JP_FONT = "'Noto Sans CJK JP', 'Noto Sans JP', 'Yu Gothic', "
    + "'Hiragino Sans', Meiryo, 'MS Gothic', 'Segoe UI', 'Noto Sans KR', "
    + 'system-ui, sans-serif';

/**
 * svg() 는 뿌리 <svg> 에 lib.mjs 의 FONT 를 박는다. 그것을 덮으려면 본문을
 * 폰트가 걸린 <g> 로 감싼다. 그림 하나가 통째로 일본어일 때 쓴다.
 */
export const jpGroup = (body) => `<g font-family="${JP_FONT}">${body}</g>`;

export default [];
