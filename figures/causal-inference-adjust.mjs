/**
 * 조정 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽. 예외를 두지 않는다
 *   - 통제한 변수는 상자, 관측되지 않은 변수는 점선 원
 *   - 열린 경로는 실선, 막힌 경로는 회색 점선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 이 장은 계산이 주력이라 그래프보다 절차와 수치를 그리는 그림이 많다. 그래도
 * 인과 그래프가 나오는 자리에서는 공통 라이브러리의 node·edge 를 그대로 쓴다.
 *
 * 색의 뜻을 이 장 안에서 고정한다.
 *   s1 — 재려는 인과 경로, 그리고 대조군 쪽 자료
 *   s2 — 처치군 쪽 자료
 *   s3 — 조정으로 막지 못한 경로
 *   회색 점선 — 조정으로 막힌 경로, 그리고 자료가 없는 구간
 */
import { svg, frame, txt } from './lib.mjs';
import { node, edge, tag, caseTitle, panel, key } from './causal-inference-graph-lib.mjs';

/* 작은 글씨 도우미. 그림마다 같은 크기를 쓰려고 묶어 둔다. */
const mid = (x, y, s) => txt(x, y, s, { anchor: 'middle' });
const midSm = (x, y, s) => txt(x, y, s, { anchor: 'middle', cls: 'ink2', size: 'sm' });

/* ── 1. 통제와 조정은 다른 단계다 ───────────────────────────── */

const controlAdjust = panel(15, 45, 320, 195)
    + caseTitle(175, 33, '통제 — 어느 변수를 넣는가')
    + node(175, 100, '습관', { boxed: true })
    + node(90, 195, '처치', { role: 'x' })
    + node(260, 195, '결과', { role: 'y' })
    + edge(175, 100, 90, 195, { blocked: true })
    + edge(175, 100, 260, 195, { blocked: true })
    + edge(90, 195, 260, 195, { tone: 's1' })
    + tag(175, 232, '이 결정은 그래프가 정한다')

    + panel(365, 45, 320, 195)
    + caseTitle(525, 33, '조정 — 그 변수로 어떻게 계산하는가')
    + panel(385, 57, 130, 36) + midSm(450, 79, '층 ㄱ 안에서 견준다')
    + panel(385, 102, 130, 36) + midSm(450, 124, '층 ㄴ 안에서 견준다')
    + panel(385, 147, 130, 36) + midSm(450, 169, '층 ㄷ 안에서 견준다')
    + edge(520, 75, 580, 112, { from: 0, to: 0 })
    + edge(520, 120, 580, 120, { from: 0, to: 0 })
    + edge(520, 165, 580, 128, { from: 0, to: 0 })
    + panel(585, 98, 85, 45) + mid(627, 126, '합친다')
    + tag(525, 232, '이 계산은 자료 위에서 한다')

    + caseTitle(350, 262, '변수를 고르는 일과 계산하는 일은 다른 단계다');

/* ── 2. 층화 — 값이 같은 것끼리 묶는다 ──────────────────────── */

const stratum = (x, name) => panel(x, 125, 180, 95)
    + caseTitle(x + 90, 150, name)
    + panel(x + 15, 162, 70, 45) + mid(x + 50, 190, '처치')
    + panel(x + 95, 162, 70, 45) + mid(x + 130, 190, '대조');

const strata = caseTitle(330, 30, '자료를 조정 변수의 값으로 나눈다')
    + panel(255, 45, 150, 38) + mid(330, 69, '표본 전체')
    + edge(330, 83, 130, 122, { from: 0, to: 0 })
    + edge(330, 83, 330, 122, { from: 0, to: 0 })
    + edge(330, 83, 530, 122, { from: 0, to: 0 })
    + stratum(40, '층 ㄱ')
    + stratum(240, '층 ㄴ')
    + stratum(440, '층 ㄷ')
    + caseTitle(330, 252, '층 안에서는 조정 변수의 값이 같다')
    + tag(330, 278, '그 안에서만 처치와 대조를 견준다');

/* ── 3. 표준화 — 층의 몫으로 가중해 더한다 ─────────────────── */

const row = (y, name) => panel(40, y - 24, 140, 44) + mid(110, y + 4, '층 ' + name + ' 의 차이')
    + midSm(200, y + 4, '곱하기')
    + panel(230, y - 24, 140, 44) + mid(300, y + 4, '층 ' + name + ' 의 몫');

const standardize = caseTitle(330, 30, '층마다 잰 차이를 층의 몫으로 가중해 더한다')
    + row(75, 'ㄱ') + midSm(205, 108, '더하기')
    + row(135, 'ㄴ') + midSm(205, 168, '더하기')
    + row(195, 'ㄷ')
    + edge(378, 135, 460, 135, { from: 0, to: 0 })
    + panel(470, 105, 160, 60) + mid(550, 140, '조정한 효과')
    + caseTitle(330, 255, '층별 차이를 그냥 평균하면 안 된다')
    + tag(330, 281, '층마다 사람 수가 다르기 때문이다');

/* ── 4. 가중치를 어디서 세는가 ──────────────────────────────── */

const weightBox = (x0, title, w1, w2, total, note) => panel(x0, 50, 320, 205)
    + caseTitle(x0 + 160, 38, title)
    + txt(x0 + 35, 90, '층 ㄱ') + txt(x0 + 120, 90, '차이 20') + txt(x0 + 225, 90, '몫 ' + w1)
    + txt(x0 + 35, 128, '층 ㄴ') + txt(x0 + 120, 128, '차이 10') + txt(x0 + 225, 128, '몫 ' + w2)
    + panel(x0 + 30, 145, 260, 1)
    + mid(x0 + 160, 185, '합치면 ' + total)
    + tag(x0 + 160, 225, note);

const weights = weightBox(15, '전체에서 센 몫', '0.6', '0.4', '16', '이것이 평균처치효과다')
    + weightBox(365, '처치받은 사람들에서 센 몫', '0.9', '0.1', '19', '이것이 처치집단 평균처치효과다')
    + caseTitle(350, 285, '층별 차이는 그대로인데 값이 다르다');

/* ── 5. 층이 잘게 쪼개진다 ──────────────────────────────────── */

const cell = (x, y, s) => panel(x, y, 62, 28) + midSm(x + 31, y + 19, s);

const sparse = panel(15, 55, 320, 205)
    + caseTitle(175, 42, '조정 변수 하나 — 층 둘')
    + midSm(175, 85, '층마다 처치와 대조의 인원')
    + txt(55, 120, '층 ㄱ') + cell(120, 102, '켬 180') + cell(205, 102, '끔 60')
    + txt(55, 160, '층 ㄴ') + cell(120, 142, '켬 20') + cell(205, 142, '끔 140')
    + tag(175, 215, '두 층 모두 양쪽이 다 있다')

    + panel(365, 55, 320, 205)
    + caseTitle(525, 42, '조정 변수 셋 — 층 여덟')
    + midSm(525, 85, '여덟 층에 남은 대조군 인원')
    + cell(385, 100, '41명') + cell(457, 100, '18명') + cell(529, 100, '27명') + cell(601, 100, '9명')
    + cell(385, 140, '12명') + cell(457, 140, '0명') + cell(529, 140, '6명') + cell(601, 140, '0명')
    + tag(525, 215, '0 인 칸에서는 견줄 상대가 없다')

    + caseTitle(350, 285, '층을 잘게 나눌수록 빈 칸이 생긴다');

/* ── 6. 회귀는 층 사이를 잇는 모양을 가정한다 ──────────────── */

const f6 = frame({ xRange: [0, 6], yRange: [0, 70], box: { x: 85, y: 60, w: 470, h: 165 } });
const pts6 = [[1, 10], [2, 14], [3, 20], [4, 34], [5, 62]];

const modelShape = caseTitle(330, 32, '층마다 잰 값과 직선이 지나가는 자리')
    + f6.axes({ xLabel: '조정 변수', yLabel: '결과의 평균', xTicks: [1, 2, 3, 4, 5], yTicks: [0, 20, 40, 60] })
    + f6.line([[1, 2], [5, 58]], { cls: 's2' })
    + pts6.map((p) => f6.dot(p, { cls: 'f1', r: 5 })).join('')
    + f6.label([1.15, 40], '직선을 가정하면 이렇게 지나간다', { cls: 'ink2', size: 'sm' })
    + f6.label([3.15, 12], '층마다 따로 잰 값', { cls: 'ink2', size: 'sm' })
    + caseTitle(330, 262, '두 자리가 어긋난 만큼이 처치 쪽으로 흘러든다');

/* ── 7. 겹치지 않는 자리를 모형이 채운다 ───────────────────── */

const f7 = frame({ xRange: [0, 10], yRange: [0, 60], box: { x: 85, y: 62, w: 470, h: 150 } });

const extrapolate = caseTitle(320, 32, '두 군이 조정 변수의 값에서 겹치지 않는 자료')
    + f7.axes({ xLabel: '조정 변수', yLabel: '결과의 평균', xTicks: [2, 4, 6, 8], yTicks: [0, 20, 40] })
    + f7.line([[4, 19], [10, 37]], { cls: 's1', dash: '6 5' })
    + f7.line([[1, 10], [4, 19]], { cls: 's1' })
    + f7.line([[0, 12], [6, 30]], { cls: 's2', dash: '6 5' })
    + f7.line([[6, 30], [9, 39]], { cls: 's2' })
    + [[1, 10], [2, 13], [3, 16], [4, 19]].map((p) => f7.dot(p, { cls: 'f1', r: 4.5 })).join('')
    + [[6, 30], [7, 33], [8, 36], [9, 39]].map((p) => f7.dot(p, { cls: 'f2', r: 4.5 })).join('')
    + f7.label([1.1, 27], '대조군이 있는 구간', { cls: 'ink2', size: 'sm' })
    + f7.label([6.2, 17], '처치군이 있는 구간', { cls: 'ink2', size: 'sm' })
    + caseTitle(320, 252, '점선 구간에는 잰 것이 없다')
    + tag(320, 276, '그 자리의 차이는 자료가 아니라 모형이 정한 것이다');

/* ── 8. 조정이 막는 뒷문과 막지 못하는 뒷문 ────────────────── */

const R5 = 46; /* '숨은 원인' 다섯 글자의 가로 반지름 */

const leftover = (x0, adjusted) => panel(x0, 55, 320, 240)
    + caseTitle(x0 + 160, 42, adjusted ? '습관으로 조정한 뒤' : '조정하기 전')
    + node(x0 + 160, 105, '습관', { boxed: adjusted })
    + node(x0 + 70, 190, '처치', { role: 'x' })
    + node(x0 + 250, 190, '결과', { role: 'y' })
    + node(x0 + 160, 265, '숨은 원인', { unobserved: true, rx: R5 })
    + edge(x0 + 160, 105, x0 + 70, 190, { blocked: adjusted, tone: 's3' })
    + edge(x0 + 160, 105, x0 + 250, 190, { blocked: adjusted, tone: 's3' })
    + edge(x0 + 160, 265, x0 + 70, 190, { tone: 's3', from: R5 })
    + edge(x0 + 160, 265, x0 + 250, 190, { tone: 's3', from: R5 })
    + edge(x0 + 70, 190, x0 + 250, 190, { tone: 's1' });

const residual = leftover(15, false) + leftover(365, true)
    + caseTitle(350, 318, '아래쪽 뒷문은 조정 전과 조정 후가 똑같다')
    + key(20, 348, [{ kind: 'boxed', name: '조정에 넣은 변수' }])
    + key(200, 348, [{ kind: 'blocked', name: '막힌 뒷문' }])
    + key(360, 348, [{ kind: 'unobserved', name: '재지 못한 변수' }])
    + key(545, 348, [{ kind: 'open', name: '남은 뒷문' }]);

export default [
    {
        name: 'ci-a-control-adjust',
        svg: svg({
            width: 700, height: 270,
            title: '변수를 고르는 단계와 그 변수로 계산하는 단계',
            desc: '왼쪽은 습관에 상자를 친 그래프이고 뒷문 화살표 둘이 점선이다. 오른쪽은 층 셋을 나란히 놓고 화살표 셋이 합친다는 상자 하나로 모이는 그림이다',
            body: controlAdjust,
        }),
    },
    {
        name: 'ci-a-strata',
        svg: svg({
            width: 660, height: 300,
            title: '표본을 조정 변수의 값으로 나눈 모습',
            desc: '표본 전체라고 적힌 상자에서 화살표 셋이 내려가 층 셋으로 갈라지고 각 층 안에 처치와 대조라고 적힌 작은 상자가 둘씩 들어 있다',
            body: strata,
        }),
    },
    {
        name: 'ci-a-standardize',
        svg: svg({
            width: 660, height: 300,
            title: '층별 차이를 가중해 하나로 합치는 절차',
            desc: '세 줄이 있고 각 줄은 층의 차이와 층의 몫을 곱하기로 잇는다. 세 줄이 더하기로 이어지고 오른쪽 상자 하나로 화살표가 간다',
            body: standardize,
        }),
    },
    {
        name: 'ci-a-weights',
        svg: svg({
            width: 700, height: 300,
            title: '같은 층별 차이에 두 가지 가중치를 쓴 결과',
            desc: '왼쪽 칸과 오른쪽 칸에 층별 차이 20 과 10 이 똑같이 적혀 있고 몫만 다르다. 왼쪽은 0.6 과 0.4 로 합쳐 16, 오른쪽은 0.9 와 0.1 로 합쳐 19 가 된다',
            body: weights,
        }),
    },
    {
        name: 'ci-a-sparse',
        svg: svg({
            width: 700, height: 300,
            title: '조정 변수를 늘렸을 때 층마다 남는 인원',
            desc: '왼쪽 칸은 층 둘에 각각 처치와 대조 인원이 모두 적혀 있다. 오른쪽 칸은 층 여덟의 대조군 인원을 격자로 늘어놓았고 그중 두 칸이 0 명이다',
            body: sparse,
        }),
    },
    {
        name: 'ci-a-model-shape',
        svg: svg({
            width: 660, height: 290,
            title: '층마다 따로 잰 값과 직선 하나가 어긋나는 그림',
            desc: '가로축이 조정 변수, 세로축이 결과의 평균이다. 점 다섯이 오른쪽으로 갈수록 가파르게 올라가고 직선 하나가 그 점들을 가로질러 지나간다',
            body: modelShape,
        }),
    },
    {
        name: 'ci-a-extrapolate',
        svg: svg({
            width: 660, height: 290,
            title: '자료가 없는 구간까지 모형이 뻗어 나간 그림',
            desc: '대조군 점 넷은 가로축 왼쪽에만 있고 처치군 점 넷은 오른쪽에만 있다. 두 직선이 각각 자료가 없는 쪽으로 점선이 되어 뻗어 있다',
            body: extrapolate,
        }),
    },
    {
        name: 'ci-a-residual',
        svg: svg({
            width: 700, height: 370,
            title: '조정으로 막히는 뒷문과 조정과 상관없이 남는 뒷문',
            desc: '같은 그래프를 두 번 그렸다. 오른쪽에서는 습관에 상자가 쳐져 위쪽 화살표 둘이 점선이 되었지만, 점선 원으로 그린 숨은 원인에서 나가는 아래쪽 화살표 둘은 양쪽 그림에서 똑같이 실선이다',
            body: residual,
        }),
    },
];
