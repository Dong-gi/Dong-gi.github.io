/**
 * 잠재결과 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽
 *   - 열린 화살표는 실선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 표 모양 그림(관측된 칸과 물음표 칸)은 인과 그래프가 아니지만, 칸과 글자를
 * 같은 규약으로 그리려고 graph-lib 의 panel·tag·caseTitle 을 그대로 쓴다.
 */
import { svg } from './lib.mjs';
import { node, edge, tag, caseTitle, panel } from './causal-inference-graph-lib.mjs';

/* 본문의 가상 표와 같은 숫자다. 화분 넷, 처치했을 때와 하지 않았을 때의 줄기 길이. */
const POTS = [
    { name: '가', treated: true, y1: '9', y0: '6' },
    { name: '나', treated: true, y1: '8', y0: '6' },
    { name: '다', treated: false, y1: '5', y0: '4' },
    { name: '라', treated: false, y1: '6', y0: '4' },
];

/* ── 1. 한 대상에 값이 둘 ───────────────────────────────────── */

const RP = 46, RA = 60, RB = 72;

const twoValues = node(100, 125, '한 화분', { rx: RP })
    + node(420, 62, '처치했을 때', { role: 'y', rx: RA })
    + node(420, 188, '처치하지 않았을 때', { role: 'y', rx: RB })
    + edge(100, 125, 420, 62, { from: RP, to: RA, tone: 's1' })
    + edge(100, 125, 420, 188, { from: RP, to: RB, tone: 's1' })
    + tag(258, 76, '처치를 한다면')
    + tag(258, 187, '하지 않는다면')
    + caseTitle(310, 232, '두 값이 다 정해져 있고 실제로 보이는 것은 한쪽뿐이다');

/* ── 2. 두 칸 가운데 한 칸은 언제나 물음표 ───────────────────── */

const COL = [85, 200, 355, 500];
const ROW = [100, 145, 190, 235];

const fillTable = panel(290, 30, 300, 228)
    + caseTitle(COL[0], 52, '화분')
    + caseTitle(COL[1], 52, '실제 처치')
    + caseTitle(COL[2], 52, '처치했을 때')
    + caseTitle(COL[3], 52, '하지 않았을 때')
    + POTS.map((p, i) => {
        const y = ROW[i];
        const seen = p.treated ? p.y1 : p.y0;
        const seenCol = p.treated ? COL[2] : COL[3];
        const gapCol = p.treated ? COL[3] : COL[2];
        return tag(COL[0], y, p.name)
            + tag(COL[1], y, p.treated ? '받았다' : '받지 않았다')
            + caseTitle(seenCol, y, seen)
            + panel(gapCol - 42, y - 19, 84, 28)
            + tag(gapCol, y, '?');
    }).join('')
    + caseTitle(310, 282, '어느 화분이든 두 칸 가운데 한 칸은 채워지지 않는다');

/* ── 3. 평균처치효과와 처치집단 평균처치효과 ─────────────────── */

const UNITS_T = [105, 185, 265];
const UNITS_C = [375, 455, 535];

const ateAtt = panel(40, 66, 545, 142)
    + panel(52, 78, 262, 118)
    + caseTitle(312, 56, '평균처치효과는 이 여섯 모두의 평균')
    + caseTitle(183, 98, '처치를 받은 대상들')
    + UNITS_T.map((x, i) => node(x, 140, ['가', '나', '다'][i], { role: 'x', rx: 22 })
        + tag(x, 182, '처치')).join('')
    + UNITS_C.map((x, i) => node(x, 140, ['라', '마', '바'][i], { rx: 22 })
        + tag(x, 182, '대조')).join('')
    + edge(183, 220, 183, 196, { from: 0, to: 0 })
    + caseTitle(183, 234, '처치집단 평균처치효과는 이 셋의 평균');

/* ── 4. 같은 평균 뒤에 서로 다른 대상 ────────────────────────── */

const HIDE = [
    [100, '가', '개인 효과 4'],
    [245, '나', '개인 효과 2'],
    [390, '다', '개인 효과 0'],
    [535, '라', '개인 효과 -2'],
];

const averageHides = caseTitle(320, 40, '같은 평균 뒤에 서로 다른 대상이 있다')
    + HIDE.map(([x, name, eff]) => node(x, 95, name, { rx: 34 }) + tag(x, 150, eff)).join('')
    + panel(60, 175, 520, 48)
    + caseTitle(320, 205, '평균은 1인데 하나는 오히려 나빠졌다');

/* ── 5. 완전한 표와 손에 쥔 표 ───────────────────────────────── */

const LCOL = [55, 140, 232];
const RCOL = [405, 490, 582];
const MROW = [115, 148, 181, 214];

const missingData = caseTitle(155, 40, '있다고 치는 표')
    + panel(30, 52, 250, 195)
    + caseTitle(LCOL[0], 80, '화분')
    + tag(LCOL[1], 80, '처치했을 때')
    + tag(LCOL[2], 80, '안 했을 때')
    + POTS.map((p, i) => tag(LCOL[0], MROW[i], p.name)
        + caseTitle(LCOL[1], MROW[i], p.y1) + caseTitle(LCOL[2], MROW[i], p.y0)).join('')
    + edge(292, 140, 372, 140, { from: 0, to: 0 })
    + tag(332, 122, '한 칸이 지워진다')
    + caseTitle(505, 40, '실제로 손에 쥔 표')
    + panel(380, 52, 250, 195)
    + caseTitle(RCOL[0], 80, '화분')
    + tag(RCOL[1], 80, '처치했을 때')
    + tag(RCOL[2], 80, '안 했을 때')
    + POTS.map((p, i) => tag(RCOL[0], MROW[i], p.name) + (p.treated
        ? caseTitle(RCOL[1], MROW[i], p.y1) + tag(RCOL[2], MROW[i], '?')
        : tag(RCOL[1], MROW[i], '?') + caseTitle(RCOL[2], MROW[i], p.y0))).join('')
    + caseTitle(330, 267, '인과추론은 지워진 칸을 두고 하는 일이다');

/* ── 6. 처치 이름 하나에 실제가 여럿 ─────────────────────────── */

const RN = 62, RM = 64, RY = 40;

const consistencyMany = node(100, 135, '처치 이름 하나', { role: 'x', rx: RN })
    + node(360, 60, '실제로 한 것 가', { rx: RM })
    + node(360, 135, '실제로 한 것 나', { rx: RM })
    + node(360, 210, '실제로 한 것 다', { rx: RM })
    + node(565, 135, '결과', { role: 'y', rx: RY })
    + edge(100, 135, 360, 60, { from: RN, to: RM, tone: 's1' })
    + edge(100, 135, 360, 135, { from: RN, to: RM, tone: 's1' })
    + edge(100, 135, 360, 210, { from: RN, to: RM, tone: 's1' })
    + edge(360, 60, 565, 135, { from: RM, to: RY })
    + edge(360, 135, 565, 135, { from: RM, to: RY })
    + edge(360, 210, 565, 135, { from: RM, to: RY })
    + caseTitle(330, 262, '이름이 같아도 실제가 여럿이면 잠재결과가 하나로 정해지지 않는다');

/* ── 7. 간섭 ─────────────────────────────────────────────────── */

const RI = 52;

const interference = node(105, 70, '갑의 처치', { role: 'x', rx: RI })
    + node(105, 180, '을의 처치', { role: 'x', rx: RI })
    + node(470, 70, '갑의 결과', { role: 'y', rx: RI })
    + node(470, 180, '을의 결과', { role: 'y', rx: RI })
    + edge(105, 70, 470, 70, { from: RI, to: RI, tone: 's1' })
    + edge(105, 180, 470, 180, { from: RI, to: RI, tone: 's1' })
    + edge(105, 70, 470, 180, { from: RI, to: RI, tone: 's2' })
    + tag(300, 106, '남의 처치가 넘어온다')
    + caseTitle(320, 228, '대각선 화살표가 있으면 자기 처치만으로 잠재결과를 적을 수 없다');

/* ── 8. 관측된 차이를 두 덩어리로 가른다 ─────────────────────── */

const naiveGap = caseTitle(320, 34, '관측되는 차이를 두 덩어리로 가른다')
    + panel(70, 46, 500, 48)
    + caseTitle(320, 76, '처치군 평균 빼기 대조군 평균')
    + edge(240, 96, 175, 140, { from: 0, to: 0 })
    + edge(400, 96, 465, 140, { from: 0, to: 0 })
    + panel(50, 146, 240, 68)
    + caseTitle(170, 174, '처치집단 평균처치효과')
    + tag(170, 196, '재고 싶은 것')
    + tag(320, 182, '더하기')
    + panel(350, 146, 240, 68)
    + caseTitle(470, 174, '선택 편향')
    + tag(470, 196, '처치가 없었더라도 있던 차이');

export default [
    {
        name: 'ci-p-two-values',
        svg: svg({
            width: 620, height: 250,
            title: '한 화분에서 갈래가 둘로 나뉘고 각 갈래 끝에 값이 하나씩 있다',
            desc: '왼쪽 화분에서 두 화살표가 나와 처치했을 때와 처치하지 않았을 때로 갈라진다',
            body: twoValues,
        }),
    },
    {
        name: 'ci-p-fill-table',
        svg: svg({
            width: 620, height: 300,
            title: '화분 넷의 두 잠재결과 칸 가운데 한 칸씩이 비어 있는 표',
            desc: '실제 처치를 받은 화분은 처치했을 때 칸만, 받지 않은 화분은 하지 않았을 때 칸만 채워져 있다',
            body: fillTable,
        }),
    },
    {
        name: 'ci-p-ate-att',
        svg: svg({
            width: 640, height: 250,
            title: '여섯 대상 가운데 셋만 처치를 받았고 평균을 어느 범위에서 내는지가 다르다',
            desc: '바깥 칸은 여섯 모두를, 안쪽 칸은 처치를 받은 셋만 둘러싼다',
            body: ateAtt,
        }),
    },
    {
        name: 'ci-p-average-hides',
        svg: svg({
            width: 640, height: 250,
            title: '개인 효과가 넷 둘 영 마이너스둘인 네 대상의 평균이 1이 된다',
            desc: '네 대상의 개인 효과가 서로 다르고 그 가운데 하나는 음수다',
            body: averageHides,
        }),
    },
    {
        name: 'ci-p-missing-data',
        svg: svg({
            width: 660, height: 280,
            title: '두 칸이 다 찬 표에서 한 칸씩 지우면 실제 자료가 된다',
            desc: '왼쪽은 두 잠재결과가 모두 적힌 가상의 표, 오른쪽은 화분마다 한 칸이 물음표인 표',
            body: missingData,
        }),
    },
    {
        name: 'ci-p-consistency-many',
        svg: svg({
            width: 660, height: 275,
            title: '처치 이름 하나에서 서로 다른 시행 셋으로 갈라져 결과로 간다',
            desc: '왼쪽 처치 이름에서 세 갈래가 나와 각각 다른 시행으로 가고 셋 모두 오른쪽 결과로 이어진다',
            body: consistencyMany,
        }),
    },
    {
        name: 'ci-p-interference',
        svg: svg({
            width: 640, height: 250,
            title: '갑의 처치에서 나온 화살표가 을의 결과로도 간다',
            desc: '두 사람의 처치와 결과가 나란히 놓이고 갑의 처치에서 을의 결과로 대각선 화살표가 하나 더 있다',
            body: interference,
        }),
    },
    {
        name: 'ci-p-naive-gap',
        svg: svg({
            width: 640, height: 245,
            title: '관측된 두 평균의 차이가 두 덩어리의 합으로 갈라진다',
            desc: '위 칸에서 화살표 둘이 내려가 아래 두 칸으로 나뉜다',
            body: naiveGap,
        }),
    },
];
