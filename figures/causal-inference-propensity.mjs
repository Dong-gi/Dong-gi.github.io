/**
 * 성향점수 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽
 *   - 통제한 변수는 상자, 관측되지 않은 변수는 점선 원
 *   - 열린 경로는 실선, 막힌 경로는 회색 점선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 이 장은 인과 그래프보다 분포·표 모양 그림이 많다. 그래도 칸과 글자는 다른 장과
 * 같은 규약으로 그리려고 graph-lib 의 panel·tag·caseTitle·node·edge 를 쓴다.
 *
 * 색의 뜻을 그림 전체에서 고정한다.
 *   s1 — 처치를 받은 쪽, 그리고 재려는 인과 경로
 *   s2 — 처치를 받지 않은 쪽, 그리고 조정하기 전의 상태
 *   s3 — 막지 못해 열려 있는 경로
 * 색만으로 구분하지 않도록 모든 칸과 막대에 이름을 함께 적는다(규격 §4).
 */
import { svg } from './lib.mjs';
import { node, edge, tag, caseTitle, panel } from './causal-inference-graph-lib.mjs';

const round = (v) => Number.parseFloat(v.toFixed(2));

/** 층 하나를 나타내는 칸. kind 로 그 층에 누가 있는지 나눈다. */
const slot = (x, y, w, h, kind) => {
    const fill = kind === 'both' ? 'var(--s1)' : kind === 'one' ? 'var(--s2)' : 'none';
    return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="2" fill="${fill}"`
        + `${kind === 'none' ? '' : ' fill-opacity="0.45"'} stroke="var(--ink2)" stroke-width="1"/>`;
};

/** 도수 막대. 높이가 0 이면 그리지 않는다. */
const bar = (x, base, w, h, tone) => (h <= 0 ? ''
    : `<rect x="${round(x)}" y="${round(base - h)}" width="${w}" height="${round(h)}" rx="1"`
      + ` fill="var(--${tone})" fill-opacity="0.5" stroke="var(--${tone})" stroke-width="1.2"/>`);

/** 색 견본. 범례에 이름과 함께 쓴다. */
const chip = (x, y, tone) =>
    `<rect x="${x}" y="${y}" width="15" height="11" rx="2" fill="var(--${tone})"`
    + ` fill-opacity="0.5" stroke="var(--${tone})" stroke-width="1.2"/>`;

const rule = (x1, y1, x2, y2) =>
    `<path d="M${x1} ${y1} L${x2} ${y2}" stroke="var(--ink2)" stroke-width="1.2" fill="none"/>`;

const dotm = (x, y, tone, r = 7) =>
    `<circle cx="${x}" cy="${y}" r="${r}" fill="var(--${tone})" fill-opacity="0.5"`
    + ` stroke="var(--${tone})" stroke-width="1.4"/>`;

/* ── 1. 조정 변수를 늘리면 층이 쪼개진다 ────────────────────────── */

const KINDS = [
    'both', 'one', 'both', 'none', 'one', 'one', 'both', 'none',
    'one', 'none', 'one', 'both', 'none', 'one', 'one', 'none',
    'both', 'one', 'none', 'one', 'none', 'one', 'both', 'none',
    'one', 'none', 'one', 'one', 'none', 'one', 'none', 'one',
];

const strataSplit = caseTitle(350, 32, '조정할 변수를 늘릴 때 한 층에 남는 사람')
    + tag(24, 90, '변수 하나 — 층 둘', { anchor: 'start' })
    + panel(190, 62, 235, 44) + caseTitle(307, 90, '처치 30 · 대조 30')
    + panel(435, 62, 235, 44) + caseTitle(552, 90, '처치 30 · 대조 30')
    + tag(24, 160, '변수 둘 — 층 넷', { anchor: 'start' })
    + [0, 1, 2, 3].map((i) => panel(190 + i * 122, 132, 112, 44)
        + caseTitle(246 + i * 122, 160, '처치 15 · 대조 15')).join('')
    + tag(24, 234, '변수 다섯 — 층 서른둘', { anchor: 'start' })
    + KINDS.map((k, i) => slot(190 + i * 15, 208, 11, 44, k)).join('')
    + slot(190, 268, 14, 14, 'both') + tag(212, 280, '양쪽이 다 있다', { anchor: 'start' })
    + slot(330, 268, 14, 14, 'one') + tag(352, 280, '한쪽만 있다', { anchor: 'start' })
    + slot(455, 268, 14, 14, 'none') + tag(477, 280, '비어 있다', { anchor: 'start' })
    + caseTitle(350, 312, '층을 잘게 나눌수록 견줄 상대가 없는 층이 늘어난다');

/* ── 2. 조합 여럿이 점수 하나로 모인다 ─────────────────────────── */

const COMBO_Y = [110, 150, 190, 230, 270, 310];
const COMBO_TO = [0, 0, 1, 1, 2, 2];
const SCORE_Y = [114, 210, 306];
const SCORE_NAME = ['점수 0.8', '점수 0.5', '점수 0.2'];

const collapse = caseTitle(340, 30, '성질 조합이 여럿이어도 점수는 하나다')
    + caseTitle(150, 62, '잰 성질의 값 조합')
    + caseTitle(540, 62, '성향점수')
    + panel(30, 78, 240, 264)
    + ['조합 하나', '조합 둘', '조합 셋', '조합 넷', '조합 다섯', '조합 여섯']
        .map((s, i) => caseTitle(150, COMBO_Y[i] + 4, s)).join('')
    + SCORE_Y.map((y, i) => panel(450, y - 22, 180, 44) + caseTitle(540, y + 4, SCORE_NAME[i])).join('')
    + COMBO_Y.map((y, i) => edge(275, y, 445, SCORE_Y[COMBO_TO[i]], { from: 0, to: 0 })).join('')
    + caseTitle(340, 362, '나눌 층이 여섯에서 셋으로 준다');

/* ── 3. 점수로 묶으면 그 안에서 구성이 같아진다 ────────────────── */

const balanceWithin = caseTitle(170, 34, '전체를 한꺼번에 보면')
    + panel(25, 48, 290, 148)
    + tag(78, 88, '처치군') + caseTitle(215, 88, '습관 있음 45 · 없음 15')
    + tag(78, 128, '대조군') + caseTitle(215, 128, '습관 있음 15 · 없음 45')
    + caseTitle(170, 174, '구성이 다르다')
    + caseTitle(510, 34, '점수가 같은 사람끼리 묶고 보면')
    + panel(365, 48, 290, 148)
    + tag(418, 88, '처치군') + caseTitle(555, 88, '습관 있음 10 · 없음 10')
    + tag(418, 128, '대조군') + caseTitle(555, 128, '습관 있음 10 · 없음 10')
    + caseTitle(510, 174, '구성이 같다')
    + caseTitle(340, 224, '점수로 묶는 순간 잰 성질의 구성이 두 쪽에서 같아진다');

/* ── 4. 점수를 쓰는 방법 셋 ────────────────────────────────────── */

const threeUses = panel(20, 55, 200, 140)
    + caseTitle(120, 44, '층화')
    + panel(45, 70, 150, 32) + caseTitle(120, 90, '점수 낮은 구간')
    + panel(45, 108, 150, 32) + caseTitle(120, 128, '가운데 구간')
    + panel(45, 146, 150, 32) + caseTitle(120, 166, '점수 높은 구간')
    + tag(120, 212, '구간 안에서 견주고 합친다')

    + panel(250, 55, 200, 140)
    + caseTitle(350, 44, '매칭')
    + tag(272, 99, '처치') + tag(272, 159, '대조')
    + [[305, 308], [350, 347], [398, 404]].map(([a, b]) => rule(a, 95, b, 155)).join('')
    + [305, 350, 398].map((x) => dotm(x, 95, 's1')).join('')
    + [308, 347, 404].map((x) => dotm(x, 155, 's2')).join('')
    + tag(350, 212, '점수가 가까운 짝을 짓는다')

    + panel(480, 55, 200, 140)
    + caseTitle(580, 44, '역확률 가중')
    + dotm(525, 125, 's1', 9)
    + edge(545, 125, 585, 125, { from: 0, to: 0, tone: 's2' })
    + dotm(625, 125, 's1', 26)
    + tag(580, 212, '한 사람을 여럿처럼 센다')

    + caseTitle(350, 240, '셋이 같은 물음에 답하지 않는다');

/* ── 5. 가중치가 하는 일 ───────────────────────────────────────── */

const weighting = caseTitle(160, 34, '자료에 있는 사람 수')
    + panel(25, 48, 270, 148)
    + tag(85, 88, '점수 0.75') + caseTitle(215, 88, '처치 45 · 대조 15')
    + tag(85, 128, '점수 0.25') + caseTitle(215, 128, '처치 15 · 대조 45')
    + caseTitle(160, 174, '묶음마다 두 쪽 인원이 다르다')
    + edge(312, 122, 388, 122, { from: 0, to: 0, tone: 's2' })
    + tag(350, 104, '가중치를 곱한다')
    + caseTitle(540, 34, '가중치를 곱한 뒤')
    + panel(405, 48, 270, 148)
    + tag(465, 88, '점수 0.75') + caseTitle(595, 88, '처치 60 · 대조 60')
    + tag(465, 128, '점수 0.25') + caseTitle(595, 128, '처치 60 · 대조 60')
    + caseTitle(540, 174, '네 칸이 모두 같아졌다')
    + caseTitle(350, 224, '가중한 뒤에는 두 쪽이 각각 전체 집단을 한 벌씩 흉내 낸다')
    + caseTitle(350, 248, '그 둘을 그대로 견주면 된다');

/* ── 6. 겹침 — 이 장에서 가장 중요한 그림 ──────────────────────── */

const BASE = 268;
const A_T = [1, 3, 6, 10, 14, 16, 15, 12, 8, 4];
const A_C = [5, 10, 15, 16, 14, 11, 8, 5, 3, 1];
const B_T = [0, 0, 1, 2, 4, 9, 17, 22, 19, 11];
const B_C = [22, 19, 15, 9, 5, 2, 1, 0, 0, 0];

const hist = (x0, treated, control, scale) => treated.map((v, i) =>
    bar(x0 + i * 31 + 3, BASE, 11, v * scale, 's1')
    + bar(x0 + i * 31 + 17, BASE, 11, control[i] * scale, 's2')).join('');

const overlap = caseTitle(185, 34, '두 분포가 넓게 겹친다')
    + hist(32, A_T, A_C, 6)
    + rule(28, BASE, 345, BASE)
    + tag(34, 286, '0') + tag(340, 286, '1') + caseTitle(185, 304, '성향점수')

    + caseTitle(520, 34, '두 분포가 거의 겹치지 않는다')
    + hist(362, B_T, B_C, 4.4)
    + rule(358, BASE, 675, BASE)
    + tag(364, 286, '0') + tag(670, 286, '1') + caseTitle(520, 304, '성향점수')

    + chip(205, 318, 's1') + tag(226, 328, '처치를 받은 사람', { anchor: 'start' })
    + chip(375, 318, 's2') + tag(396, 328, '받지 않은 사람', { anchor: 'start' })
    + caseTitle(350, 356, '오른쪽에서는 양쪽 끝에 견줄 상대가 아예 없다');

/* ── 7. 잘라내면 답하는 대상이 바뀐다 ──────────────────────────── */

const trim = caseTitle(330, 34, '겹치는 구간만 남기면 무엇이 달라지는가')
    + panel(40, 58, 140, 46) + caseTitle(110, 86, '대조군만 있다')
    + panel(180, 58, 300, 46) + caseTitle(330, 86, '양쪽이 다 있다')
    + panel(480, 58, 140, 46) + caseTitle(550, 86, '처치군만 있다')
    + tag(110, 124, '견줄 상대가 없다')
    + tag(330, 124, '왼쪽이 점수가 낮고 오른쪽이 높다')
    + tag(550, 124, '견줄 상대가 없다')
    + edge(330, 140, 330, 178, { from: 0, to: 0, tone: 's2' })
    + tag(420, 164, '가운데만 남긴다', { anchor: 'start' })
    + panel(180, 184, 300, 46) + caseTitle(330, 212, '남긴 구간')
    + caseTitle(330, 254, '이 값은 남긴 구간에 있는 사람들에 대한 답이다')
    + caseTitle(330, 278, '자르기 전에 묻던 대상 전체에 대한 답이 아니다');

/* ── 8. 균형 확인 ──────────────────────────────────────────────── */

const BAL = [
    [84, '습관', 220, 20],
    [120, '나이대', 160, 30],
    [156, '가입 시기', 120, 96],
    [192, '기기', 62, 12],
    [228, '지난 수강', 92, 24],
];

const balanceCheck = caseTitle(330, 32, '묶은 뒤에 각 성질의 차이가 얼마나 줄었는가')
    + rule(200, 60, 200, 248)
    + tag(200, 52, '차이 0')
    + BAL.map(([y, name, before, after]) => tag(30, y + 4, name, { anchor: 'start' })
        + bar(200, y - 3, before, 9, 's2')
        + bar(200, y + 11, after, 9, 's1')).join('')
    + chip(200, 258, 's2') + tag(221, 268, '묶기 전', { anchor: 'start' })
    + chip(330, 258, 's1') + tag(351, 268, '묶은 뒤', { anchor: 'start' })
    + caseTitle(330, 290, '이 확인은 점수에 넣은 성질에 대해서만 할 수 있다');

/* ── 9. 성향점수와 무선배정 ────────────────────────────────────── */

const RM = 26;
const RU = 52;
const RZ = 39;

const notRandom = panel(15, 58, 330, 226)
    + caseTitle(180, 44, '성향점수로 묶었을 때')
    + node(85, 105, '잰 성질', { boxed: true, rx: RZ })
    + node(258, 105, '못 잰 성질', { unobserved: true, rx: RU })
    + node(90, 238, '처치', { role: 'x' })
    + node(270, 238, '결과', { role: 'y' })
    + edge(85, 105, 90, 238, { blocked: true, from: RZ, to: RM })
    + edge(85, 105, 270, 238, { blocked: true, from: RZ, to: RM })
    + edge(258, 105, 90, 238, { tone: 's3', from: RU, to: RM })
    + edge(258, 105, 270, 238, { tone: 's3', from: RU, to: RM })
    + edge(90, 238, 270, 238, { tone: 's1', from: RM, to: RM })
    + tag(180, 274, '막지 못한 뒷문이 남는다')

    + panel(355, 58, 330, 226)
    + caseTitle(520, 44, '무선배정을 했을 때')
    + node(425, 105, '동전', { rx: 28 })
    + node(600, 105, '못 잰 성질', { unobserved: true, rx: RU })
    + node(430, 238, '처치', { role: 'x' })
    + node(610, 238, '결과', { role: 'y' })
    + edge(425, 105, 430, 238, { tone: 's1', from: 28, to: RM })
    + edge(600, 105, 610, 238, { from: RU, to: RM })
    + edge(430, 238, 610, 238, { tone: 's1', from: RM, to: RM })
    + tag(520, 274, '처치로 들어오는 화살표가 동전뿐이다')

    + caseTitle(350, 312, '점수로 묶는 일은 잰 성질에만 닿는다');

export default [
    {
        name: 'ci-e-strata-split',
        svg: svg({
            width: 700, height: 330,
            title: '조정 변수의 개수에 따라 층이 쪼개지는 모양',
            desc: '위에서 아래로 내려갈수록 칸이 잘게 나뉜다. 맨 아래 줄은 서른두 칸인데 그중 여섯만 양쪽이 다 있고 열다섯은 한쪽만, 열하나는 비어 있다',
            body: strataSplit,
        }),
    },
    {
        name: 'ci-e-score-collapse',
        svg: svg({
            width: 680, height: 372,
            title: '성질 값의 조합 여섯이 성향점수 셋으로 모이는 모양',
            desc: '왼쪽 칸에 조합 여섯이 세로로 놓여 있고 각 조합에서 오른쪽 점수 세 칸 가운데 하나로 화살표가 간다. 두 조합씩 같은 점수로 모인다',
            body: collapse,
        }),
    },
    {
        name: 'ci-e-balance-within',
        svg: svg({
            width: 680, height: 245,
            title: '전체에서 본 두 집단의 성질 구성과 점수가 같은 묶음 안에서 본 구성',
            desc: '왼쪽 칸에서는 처치군과 대조군의 습관 구성이 서로 뒤집혀 있고, 오른쪽 칸에서는 두 줄의 숫자가 같다',
            body: balanceWithin,
        }),
    },
    {
        name: 'ci-e-three-uses',
        svg: svg({
            width: 700, height: 255,
            title: '성향점수를 쓰는 세 가지 방법',
            desc: '왼쪽 칸은 구간 셋으로 나뉘어 있고, 가운데 칸은 위아래 점 셋씩이 선으로 이어져 있으며, 오른쪽 칸은 작은 원에서 큰 원으로 화살표가 간다',
            body: threeUses,
        }),
    },
    {
        name: 'ci-e-weighting',
        svg: svg({
            width: 700, height: 265,
            title: '가중치를 곱하기 전과 후의 네 칸 인원',
            desc: '왼쪽 표에서는 묶음마다 처치와 대조의 인원이 45 대 15 와 15 대 45 로 어긋나 있고, 오른쪽 표에서는 네 칸이 모두 60 이다',
            body: weighting,
        }),
    },
    {
        name: 'ci-e-overlap',
        svg: svg({
            width: 700, height: 370,
            title: '처치를 받은 쪽과 받지 않은 쪽의 성향점수 분포를 겹쳐 그린 두 경우',
            desc: '왼쪽에서는 두 무리의 막대가 점수 전 구간에 걸쳐 나란히 서 있고, 오른쪽에서는 낮은 점수 쪽에 처치 막대가 거의 없고 높은 점수 쪽에 대조 막대가 거의 없다',
            body: overlap,
        }),
    },
    {
        name: 'ci-e-trim',
        svg: svg({
            width: 660, height: 300,
            title: '겹치지 않는 양 끝을 잘라내고 가운데 구간만 남기는 그림',
            desc: '위쪽 띠가 세 칸으로 나뉘어 있고 양 끝 칸에는 한쪽만 있다고 적혀 있다. 아래로 내려온 화살표 끝에는 가운데 칸만 남아 있다',
            body: trim,
        }),
    },
    {
        name: 'ci-e-balance-check',
        svg: svg({
            width: 660, height: 300,
            title: '성질 다섯에 대해 묶기 전과 묶은 뒤 두 집단의 차이를 나란히 그린 막대',
            desc: '성질마다 위아래 막대 두 개가 있고 대부분은 아래 막대가 훨씬 짧은데 가입 시기만 두 막대의 길이가 비슷하다',
            body: balanceCheck,
        }),
    },
    {
        name: 'ci-e-not-random',
        svg: svg({
            width: 700, height: 330,
            title: '성향점수로 묶은 그래프와 무선배정을 한 그래프',
            desc: '왼쪽은 상자를 친 잰 성질에서 나가는 화살표가 점선이지만 점선 원으로 그린 못 잰 성질에서 처치와 결과로 가는 화살표가 실선이다. 오른쪽은 처치로 들어오는 화살표가 동전에서 오는 것뿐이다',
            body: notRandom,
        }),
    },
];
