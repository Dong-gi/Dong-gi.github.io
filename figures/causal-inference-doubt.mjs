/**
 * 가정을 의심하는 법 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽
 *   - 통제한 변수는 상자, 관측되지 않은 변수는 점선 원
 *   - 열린 경로는 실선, 막힌 경로는 회색 점선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 이 장은 그래프 그림과 칸 그림이 섞여 있다. 어느 쪽이든 마디·화살표·칸·딱지는
 * graph-lib 의 함수로 그려서 앞 장들과 규약이 갈리지 않게 한다.
 *
 * 색의 뜻을 이 장 전체에서 고정한다.
 *   s1 — 재려는 인과 경로, 처치를 받은 쪽, 그리고 잰 것을 가리키는 점
 *   s2 — 대조하는 쪽, 그리고 결론이 뒤집히는 경계
 *   s3 — 가짜로 놓아 본 자리
 * 색만으로 구분하지 않도록 모든 칸과 선에 이름을 함께 적는다(규격 §4).
 */
import { svg, px, txt } from './lib.mjs';
import { node, edge, tag, caseTitle, panel } from './causal-inference-graph-lib.mjs';

const round = (v) => Number.parseFloat(v.toFixed(2));

/** 세로 점선. 시간선 위에서 어느 시점을 가리킬 때 쓴다. */
const vline = (x, y1, y2, tone) =>
    `<path d="M${x} ${y1} L${x} ${y2}" fill="none" stroke="var(--${tone})"`
    + ` stroke-width="1.6" stroke-dasharray="5 4"/>`;

/** 꺾은선. 점 배열을 그대로 잇는다. */
const poly = (pts, tone) =>
    `<path d="M${pts.map(([a, b]) => `${round(a)} ${round(b)}`).join(' L')}" fill="none"`
    + ` stroke="var(--${tone})" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;

/** 자유 곡선. 민감도 그림의 경계선처럼 좌표계가 없는 자리에 쓴다. */
const freeCurve = (d, tone) =>
    `<path d="${d}" fill="none" stroke="var(--${tone})" stroke-width="2.2" stroke-linecap="round"/>`;

const dotm = (x, y, tone, r = 5) =>
    `<circle cx="${x}" cy="${y}" r="${r}" fill="var(--${tone})" fill-opacity="0.6"`
    + ` stroke="var(--${tone})" stroke-width="1.4"/>`;

/* ── 1. 검사 가능성 세 등급 ────────────────────────────────────── */

const GRADES = [
    { x: 20, name: '자료로 검사되는 것', items: ['겹침', '균형', '관련성', '사전 추세', '배정 기록'] },
    { x: 252, name: '부분적으로만 검사되는 것', items: ['그래프가 함의하는', '조건부독립'] },
    { x: 484, name: '원리적으로 검사되지 않는 것', items: ['교환가능성', '배제 제약', '평행추세', '일관성', '충실성'] },
];

const testable = GRADES.map((g) =>
    panel(g.x, 60, 216, 205)
    + caseTitle(g.x + 108, 46, g.name)
    + g.items.map((it, i) => tag(g.x + 108, 100 + i * 32, it)).join('')).join('')
    + tag(360, 190, '어긋나면 떨어뜨린다')
    + tag(360, 220, '맞아도 고르지 못한다')
    + caseTitle(360, 296, '왼쪽 칸을 다 통과해도 오른쪽 칸은 그대로 남는다');

/* ── 2. 그래프가 함의하는 독립을 자료와 견준다 ─────────────────── */

const implied = caseTitle(200, 30, '가상의 그래프')
    + edge(200, 62, 100, 130, { from: 30, to: 22 })
    + edge(200, 62, 300, 130, { from: 30, to: 22 })
    + edge(100, 130, 300, 130, { from: 32, to: 32, tone: 's1' })
    + edge(200, 205, 300, 130, { from: 32, to: 21 })
    + node(200, 62, '구독자', { rx: 32 })
    + node(100, 130, '이미지', { role: 'x', rx: 32 })
    + node(300, 130, '조회수', { role: 'y', rx: 32 })
    + node(200, 205, '글 길이', { rx: 39 })
    + caseTitle(550, 30, '그래프가 함의하는 독립 주장')
    + panel(400, 45, 300, 84)
    + tag(550, 74, '글 길이와 이미지는 무관해야 한다')
    + caseTitle(550, 104, '자료에서 이어져 있었다 — 어긋난다')
    + panel(400, 145, 300, 84)
    + tag(550, 174, '글 길이와 구독자는 무관해야 한다')
    + caseTitle(550, 204, '자료에서도 무관했다 — 어긋나지 않는다')
    + caseTitle(360, 268, '어긋난 줄이 하나라도 있으면 그래프가 틀렸다. 다 맞아도 옳다는 뜻은 아니다');

/* ── 3. 같은 독립을 함의하는 그래프들 ──────────────────────────── */

const CELLS = [
    { x: 20, y: 46, ny: 100, nx: [80, 180, 280], name: '첫째 — 사슬' },
    { x: 360, y: 46, ny: 100, nx: [420, 520, 620], name: '둘째 — 방향이 반대인 사슬' },
    { x: 20, y: 194, ny: 248, nx: [80, 180, 280], name: '셋째 — 갈림길' },
    { x: 360, y: 194, ny: 248, nx: [420, 520, 620], name: '넷째 — 충돌부' },
];

const trio = (c, arrows, note) => {
    const [a, b, d] = c.nx;
    const map = { ab: [a, b], ba: [b, a], bc: [b, d], cb: [d, b] };
    return panel(c.x, c.y, 320, 118)
        + caseTitle(c.x + 160, c.y - 8, c.name)
        + arrows.map((k) => edge(map[k][0], c.ny, map[k][1], c.ny)).join('')
        + node(a, c.ny, '갑') + node(b, c.ny, '을') + node(d, c.ny, '병')
        + tag(c.x + 160, c.y + 106, note);
};

const equivalent = trio(CELLS[0], ['ab', 'bc'], '을을 통제하면 갑과 병이 독립')
    + trio(CELLS[1], ['cb', 'ba'], '을을 통제하면 갑과 병이 독립')
    + trio(CELLS[2], ['ba', 'bc'], '을을 통제하면 갑과 병이 독립')
    + trio(CELLS[3], ['ab', 'cb'], '통제하지 않으면 갑과 병이 독립')
    + caseTitle(350, 332, '앞의 셋은 같은 독립을 함의해 자료가 갈라 주지 못하고 넷째만 갈린다');

/* ── 4. 결론이 뒤집히는 경계 ───────────────────────────────────── */

const sensitivity = px(80, 250, 615, 250, { cls: 'ax', marker: 'ark', width: 1.5 })
    + px(80, 250, 80, 48, { cls: 'ax', marker: 'ark', width: 1.5 })
    + freeCurve('M140 80 C 230 190, 330 215, 560 226', 's2')
    + dotm(185, 175, 's1')
    + txt(196, 179, '잰 혼입', { size: 'sm', cls: 'ink2' })
    + txt(150, 228, '이만한 세기로는 결론이 그대로다', { size: 'sm', cls: 'ink2' })
    + txt(330, 110, '이만하면 결론이 뒤집힌다', { size: 'sm', cls: 'ink2' })
    + txt(88, 38, '숨은 혼입이 결과와 이어진 세기', { size: 'sm', cls: 'ink2' })
    + txt(348, 274, '숨은 혼입이 처치와 이어진 세기', { anchor: 'middle', size: 'sm', cls: 'ink2' })
    + txt(348, 302, '점은 잰 혼입 가운데 가장 센 것이 어디쯤인지 찍은 것이다', { anchor: 'middle', size: 'sm', cls: 'ink2' });

/* ── 5. 음성 대조 결과 ─────────────────────────────────────────── */

const ncOutcome = edge(245, 70, 110, 175, { from: 26, to: 22 })
    + edge(245, 70, 380, 175, { from: 26, to: 22 })
    + edge(245, 70, 540, 175, { from: 26, to: 34, bow: -38 })
    + edge(110, 175, 380, 175, { from: 32, to: 32, tone: 's1' })
    + node(245, 70, '열의', { unobserved: true, rx: 26 })
    + node(110, 175, '이미지', { role: 'x', rx: 32 })
    + node(380, 175, '조회수', { role: 'y', rx: 32 })
    + node(540, 175, '옛글 조회', { rx: 46 })
    + tag(245, 36, '재지 못한 혼입')
    + tag(110, 222, '처치')
    + tag(380, 222, '재려는 결과')
    + tag(540, 222, '음성 대조 결과')
    + caseTitle(320, 256, '이미지에서 옛글 조회로 가는 화살표는 없다')
    + caseTitle(320, 284, '조정한 뒤에도 이미지와 옛글 조회가 이어져 있으면 남은 혼입의 신호다');

/* ── 6. 음성 대조 원인 ─────────────────────────────────────────── */

const ncExposure = edge(280, 60, 110, 90, { from: 26, to: 43 })
    + edge(280, 60, 110, 195, { from: 26, to: 21 })
    + edge(280, 60, 470, 195, { from: 26, to: 22 })
    + edge(110, 195, 470, 195, { from: 32, to: 32, tone: 's1' })
    + node(280, 60, '열의', { unobserved: true, rx: 26 })
    + node(110, 90, '비공개 메모', { rx: 54 })
    + node(110, 195, '이미지', { role: 'x', rx: 32 })
    + node(470, 195, '조회수', { role: 'y', rx: 32 })
    + tag(280, 26, '재지 못한 혼입')
    + tag(110, 55, '음성 대조 원인')
    + tag(110, 248, '실제 처치')
    + tag(470, 248, '결과')
    + caseTitle(300, 284, '메모는 독자에게 보이지 않으므로 조회수로 가는 화살표가 없다');

/* ── 7. 위약 검정 — 가짜 처치 시점 ─────────────────────────────── */

const TX = [110, 175, 240, 305, 370, 435, 500, 565];
const SER_A = [200, 193, 186, 179, 172, 150, 136, 124];
const SER_B = [212, 205, 198, 191, 184, 177, 170, 163];

const placebo = px(90, 230, 640, 230, { cls: 'ax', marker: 'ark', width: 1.5 })
    + px(90, 230, 90, 50, { cls: 'ax', marker: 'ark', width: 1.5 })
    + vline(207, 56, 234, 's3')
    + vline(402, 56, 234, 'ink2')
    + poly(TX.map((x, i) => [x, SER_A[i]]), 's1')
    + poly(TX.map((x, i) => [x, SER_B[i]]), 's2')
    + TX.map((x, i) => dotm(x, SER_A[i], 's1', 3.5) + dotm(x, SER_B[i], 's2', 3.5)).join('')
    + TX.map((x, i) => txt(x, 248, `${i + 1}월`, { anchor: 'middle', size: 'sm', cls: 'ink2' })).join('')
    + txt(96, 42, '조회수', { size: 'sm', cls: 'ink2' })
    + txt(575, 128, '집단 가', { size: 'sm', cls: 'ink2' })
    + txt(575, 167, '집단 나', { size: 'sm', cls: 'ink2' })
    + txt(207, 46, '가짜 처치 시점', { anchor: 'middle', size: 'sm', cls: 'ink2' })
    + txt(402, 46, '실제 처치 시점', { anchor: 'middle', size: 'sm', cls: 'ink2' })
    + txt(207, 276, '여기서 재면 차이가 없어야 한다', { anchor: 'middle', size: 'sm', cls: 'ink2' })
    + txt(450, 276, '여기서 잰 것이 효과라고 말하는 값', { anchor: 'middle', size: 'sm', cls: 'ink2' });

/* ── 8. 분석을 고르는 갈림길 ───────────────────────────────────── */

const LEAF = [50, 120, 200, 270];
const LEAF_NAME = ['값 하나', '값 둘', '값 셋', '값 넷'];

const choices = edge(68, 160, 250, 95, { from: 41, to: 33 })
    + edge(68, 160, 250, 225, { from: 41, to: 33 })
    + edge(250, 95, 440, 50, { from: 44, to: 30 })
    + edge(250, 95, 440, 120, { from: 45, to: 31 })
    + edge(250, 225, 440, 200, { from: 45, to: 31 })
    + edge(250, 225, 440, 270, { from: 44, to: 30 })
    + LEAF.map((y) => edge(440, y, 600, y, { from: 39, to: 0 })).join('')
    + node(68, 160, '같은 자료', { rx: 46 })
    + node(250, 95, '그래프 가', { rx: 46 })
    + node(250, 225, '그래프 나', { rx: 46 })
    + LEAF.map((y, i) => node(440, y, `집합 ${'가나다라'[i]}`, { rx: 39 })).join('')
    + LEAF.map((y, i) => tag(630, y + 4, LEAF_NAME[i])).join('')
    + tag(632, 148, '이것만 보고되었다')
    + tag(230, 20, '결과를 보고 나서 고르면 어느 갈래든 고를 수 있다')
    + caseTitle(350, 312, '미리 정해 두면 갈래가 하나로 좁혀지고 고르지 않은 갈래도 함께 남는다');

export default [
    {
        name: 'ci-t-testable',
        svg: svg({
            width: 720, height: 318,
            title: '가정을 검사 가능성에 따라 세 칸에 나눈 것',
            desc: '왼쪽 칸에 겹침과 균형 같은 항목이, 오른쪽 칸에 교환가능성과 일관성 같은 항목이 놓여 있다',
            body: testable,
        }),
    },
    {
        name: 'ci-t-implied',
        svg: svg({
            width: 720, height: 300,
            title: '그래프에서 뽑은 독립 주장을 자료와 견주는 일',
            desc: '왼쪽에 마디 넷짜리 그래프가 있고 오른쪽에 그 그래프가 함의하는 독립 주장 둘과 자료의 판정이 있다',
            body: implied,
        }),
    },
    {
        name: 'ci-t-equivalent',
        svg: svg({
            width: 700, height: 344,
            title: '화살표가 다른데 같은 독립을 함의하는 그래프들',
            desc: '마디 셋짜리 그래프 넷을 나란히 놓았다. 앞의 셋은 같은 독립을, 넷째는 다른 독립을 함의한다',
            body: equivalent,
        }),
    },
    {
        name: 'ci-t-sensitivity',
        svg: svg({
            width: 660, height: 320,
            title: '숨은 혼입의 두 세기와 결론이 뒤집히는 경계',
            desc: '가로축은 처치와 이어진 세기, 세로축은 결과와 이어진 세기이고 두 영역을 가르는 곡선이 있다',
            body: sensitivity,
        }),
    },
    {
        name: 'ci-t-nc-outcome',
        svg: svg({
            width: 640, height: 300,
            title: '음성 대조 결과가 신호를 주는 이유',
            desc: '재지 못한 혼입이 두 결과 모두에 화살표를 보내고 처치는 한쪽에만 화살표를 보낸다',
            body: ncOutcome,
        }),
    },
    {
        name: 'ci-t-nc-exposure',
        svg: svg({
            width: 640, height: 310,
            title: '결과에 닿을 수 없는 처치를 대신 놓아 보는 일',
            desc: '재지 못한 혼입이 실제 처치와 음성 대조 원인 양쪽에 화살표를 보내는데 음성 대조 원인은 결과로 가지 않는다',
            body: ncExposure,
        }),
    },
    {
        name: 'ci-t-placebo',
        svg: svg({
            width: 680, height: 300,
            title: '가짜 처치 시점에서 같은 분석을 돌려 보는 일',
            desc: '두 집단의 시간선 위에 가짜 처치 시점과 실제 처치 시점이 세로 점선으로 표시되어 있다',
            body: placebo,
        }),
    },
    {
        name: 'ci-t-choices',
        svg: svg({
            width: 700, height: 320,
            title: '그래프와 조정 집합을 고르는 갈림이 만드는 값들',
            desc: '같은 자료에서 그래프 둘, 조정 집합 넷으로 갈라져 값이 넷 나오고 그중 하나만 보고된다',
            body: choices,
        }),
    },
];
