/**
 * 4장(운동학)과 5장(뉴턴 역학)에 들어가는 그림.
 *
 * physics.mjs 와 같은 형식이고, 이름은 kin- / newton- 으로 시작한다.
 * 이름이 겹치면 build.mjs 가 오류를 내므로 접두어로 장을 구분한다.
 *
 * 라벨에는 수식을 쓸 수 없다(<img> 라 MathJax 가 닿지 않는다).
 * 아래첨자는 lib.mjs 의 `v~0` 표기를 쓰고, 나머지는 유니코드로 적는다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);

/** 직각 표시. dx, dy 는 ±1 로 꺾이는 방향(화소 좌표). */
const rightAngle = (x, y, dx, dy, s = 11) =>
    `<path class="gr" fill="none" d="M${x + dx * s} ${y} L${x + dx * s} ${y + dy * s} L${x} ${y + dy * s}"/>`;

/** 화소 좌표 점. */
const pdot = (x, y, cls = 'f1', r = 4) => `<circle class="${cls}" cx="${x}" cy="${y}" r="${r}"/>`;

/* ------------------------------------------------------------------ *
 * kin-1. 이동거리와 변위
 * ------------------------------------------------------------------ */
add((() => {
    const W = 560, H = 258;
    const X = v => 52 + v * 42;
    const yb = 172;
    const b = [];
    b.push(`<path class="ax" marker-end="url(#ark)" d="M${X(-0.4)} ${yb} H${X(10.6)}"/>`);
    for (let i = 0; i <= 10; i += 1) {
        b.push(`<path class="ax" stroke-width="1" d="M${X(i)} ${yb} V${yb - 7}"/>`);
        if (i % 2 === 0) b.push(txt(X(i), yb + 20, String(i), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(X(10.7), yb + 5, 'x (m)', { cls: 'ink2', size: 'sm' }));
    b.push(px(X(2), yb - 32, X(8), yb - 32, { cls: 's1', marker: 'ar1' }));
    b.push(txt((X(2) + X(8)) / 2, yb - 40, '① 앞으로 6 m', { anchor: 'middle', cls: 'ink' }));
    b.push(px(X(8), yb - 72, X(5), yb - 72, { cls: 's2', marker: 'ar2' }));
    b.push(txt((X(8) + X(5)) / 2, yb - 80, '② 뒤로 3 m', { anchor: 'middle', cls: 'ink' }));
    b.push(pdot(X(2), yb, 'f3', 4.5));
    b.push(pdot(X(5), yb, 'f3', 4.5));
    b.push(txt(X(2), yb + 42, '출발', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(X(5), yb + 42, '도착', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(26, 30, '이동거리 = 6 m + 3 m = 9 m', { cls: 'ink bold' }));
    b.push(txt(26, 48, '실제로 지나온 길의 길이', { cls: 'ink2', size: 'sm' }));
    b.push(txt(310, 30, '변위 = 5 m − 2 m = +3 m', { cls: 'ink bold' }));
    b.push(txt(310, 48, '끝 위치 − 처음 위치', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, H - 12, '되돌아오면 이동거리는 늘지만 변위는 줄어든다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'kin-position-displacement',
        title: '이동거리와 변위의 차이',
        desc: '2 m 에서 8 m 까지 갔다가 5 m 로 되돌아온 운동. 지나온 길이의 합인 이동거리는 9 m 지만 '
            + '처음과 끝만 보는 변위는 +3 m 다.',
        svg: svg({ width: W, height: H, title: '이동거리와 변위', desc: '수직선 위에서 갔다가 되돌아온 운동', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-2. 평균속도는 두 점을 이은 직선의 기울기
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const f = t => t * t;
    const g = frame({ xRange: [0, 5.4], yRange: [0, 28], box: { x: 58, y: 34, w: 300, h: 210 } });
    const P = [1, 1], Q = [4, 16];
    const body = [
        g.axes({ xLabel: 't (s)', yLabel: 'x (m)', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 5, 10, 15, 20, 25] }),
        g.curve(f, { from: 0, to: 5.2, cls: 's1' }),
        g.line([P, Q], { cls: 's2' }),
        g.guide(P, [Q[0], P[1]]),
        g.guide([Q[0], P[1]], Q),
        g.dot(P, { cls: 'f2' }),
        g.dot(Q, { cls: 'f2' }),
        g.label([2.5, 1], 'Δt = 3.0 s', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([4, 8], 'Δx = 15 m', { dx: 8, cls: 'ink2', size: 'sm' }),
        g.label(P, 'P', { dx: -16, dy: 5, cls: 'ink' }),
        g.label(Q, 'Q', { dx: -14, dy: -8, cls: 'ink' }),
        txt(390, 70, 'P 와 Q 를 이은 직선의', { cls: 'ink' }),
        txt(390, 90, '기울기가 평균속도다', { cls: 'ink' }),
        txt(390, 126, 'Δx / Δt = 15 m ÷ 3.0 s', { cls: 'ink2', size: 'sm' }),
        txt(390, 148, '= 5.0 m/s', { cls: 'ink bold' }),
        txt(390, 184, '곡선이 휘어 있으므로', { cls: 'ink2', size: 'sm' }),
        txt(390, 202, '구간을 바꾸면 값도 바뀐다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '평균속도는 구간 전체를 하나의 값으로 뭉갠 것이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-average-velocity-slope',
        title: '평균속도는 x-t 그래프에서 두 점을 이은 직선의 기울기',
        desc: 'x-t 그래프 위의 두 점 P 와 Q 를 직선으로 이으면 그 기울기가 평균속도다. '
            + '가로 변화 델타 t 에 대한 세로 변화 델타 x 의 비다.',
        svg: svg({ width: W, height: H, title: '평균속도와 그래프의 기울기', desc: 'x-t 곡선 위 두 점을 이은 직선', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-3. 할선이 접선으로 — 순간속도
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const f = t => t * t;
    const g = frame({ xRange: [0, 5.4], yRange: [0, 28], box: { x: 58, y: 34, w: 300, h: 210 } });
    const P = [1, 1];
    const seg = (t2, cls) => {
        const s = (f(t2) - 1) / (t2 - 1);
        return g.line([[0.6, 1 - 0.4 * s], [Math.min(t2 + 0.3, 5.2), f(t2) + 0.3 * s]], { cls, dash: '5 4' });
    };
    const body = [
        g.axes({ xLabel: 't (s)', yLabel: 'x (m)', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 5, 10, 15, 20, 25] }),
        g.curve(f, { from: 0, to: 5.2, cls: 's1' }),
        seg(4, 's3'), seg(2.5, 's3'), seg(1.6, 's3'),
        g.dot([4, 16], { cls: 'f3', r: 3 }),
        g.dot([2.5, 6.25], { cls: 'f3', r: 3 }),
        g.dot([1.6, 2.56], { cls: 'f3', r: 3 }),
        g.line([[0.2, 1 - 1.6], [3.2, 1 + 4.4]], { cls: 's2' }),
        g.dot(P, { cls: 'f2' }),
        g.label(P, 'P', { dx: -16, dy: 6, cls: 'ink' }),
        g.label([4, 16], 'Δt = 3.0', { dx: 6, dy: -4, cls: 'ink2', size: 'sm' }),
        g.label([2.5, 6.25], '1.5', { dx: 7, dy: 2, cls: 'ink2', size: 'sm' }),
        g.label([1.6, 2.56], '0.6', { dx: 7, dy: 4, cls: 'ink2', size: 'sm' }),
        g.label([3.2, 5.4], '접선', { dx: 4, dy: 12, cls: 'ink' }),
        txt(390, 66, '두 번째 점을 P 에 가까이', { cls: 'ink' }),
        txt(390, 86, '가져가면 직선이 한 자리에', { cls: 'ink' }),
        txt(390, 106, '멈춘다. 그것이 접선이다.', { cls: 'ink' }),
        txt(390, 142, 'P 에서의 순간속도', { cls: 'ink2', size: 'sm' }),
        txt(390, 162, '= 접선의 기울기 = 2.0 m/s', { cls: 'ink bold' }),
        txt(390, 196, '이 기울기를 dx/dt 로 적는다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, 'Δt 를 줄여도 기울기가 0 이나 무한대로 가지 않고 한 값에 정착한다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-secant-to-tangent',
        title: '할선이 접선에 다가가는 모습',
        desc: '고정한 점 P 와 다른 한 점을 잇는 직선의 기울기는 두 점이 가까워질수록 한 값에 다가간다. '
            + '그 값이 P 에서의 순간속도이고, 그림에서는 접선의 기울기로 나타난다.',
        svg: svg({ width: W, height: H, title: '할선에서 접선으로', desc: '점 간격을 줄이면 할선이 접선이 된다', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-4. v-t 그래프의 넓이가 변위 — 등가속도 공식의 유도
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const v0 = 4, a = 2, T = 5;
    const g = frame({ xRange: [0, 6], yRange: [0, 16], box: { x: 58, y: 34, w: 290, h: 210 } });
    const poly = (pts, fillVar, op) =>
        `<path d="M${pts.map(([x, y]) => `${g.X(x)} ${g.Y(y)}`).join(' L')} Z" fill="${fillVar}" fill-opacity="${op}" stroke="none"/>`;
    const body = [
        poly([[0, 0], [T, 0], [T, v0], [0, v0]], 'var(--s1)', 0.16),
        poly([[0, v0], [T, v0], [T, v0 + a * T]], 'var(--s2)', 0.22),
        g.axes({ xLabel: 't (s)', yLabel: 'v (m/s)', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 4, 8, 12] }),
        g.line([[0, v0], [T, v0 + a * T]], { cls: 's3' }),
        g.guide([0, v0], [T, v0]),
        g.guide([T, 0], [T, v0 + a * T]),
        g.dot([0, v0], { cls: 'f3' }),
        g.dot([T, v0 + a * T], { cls: 'f3' }),
        g.label([0, v0], 'v~0', { dx: 8, dy: -8, cls: 'ink' }),
        g.label([T, v0 + a * T], 'v', { dx: 6, dy: 0, cls: 'ink' }),
        g.label([2.4, 1.7], '직사각형  v~0 t', { anchor: 'middle', cls: 'ink' }),
        g.label([3.5, 8.4], '삼각형  ½ (v − v~0) t', { anchor: 'middle', cls: 'ink' }),
        txt(378, 62, 'v-t 그래프 아래 넓이가', { cls: 'ink' }),
        txt(378, 82, '그 시간 동안의 변위다', { cls: 'ink' }),
        txt(378, 118, 'Δx = v~0 t + ½ (v − v~0) t', { cls: 'ink2', size: 'sm' }),
        txt(378, 140, 'v − v~0 = a t 이므로', { cls: 'ink2', size: 'sm' }),
        txt(378, 164, 'Δx = v~0 t + ½ a t²', { cls: 'ink bold' }),
        txt(378, 198, '사다리꼴로 한 번에 보면', { cls: 'ink2', size: 'sm' }),
        txt(378, 216, 'Δx = ½ (v~0 + v) t', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '가속도가 일정하면 v-t 그래프가 직선이라 넓이를 도형으로 잴 수 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-vt-area',
        title: 'v-t 그래프의 넓이로 등가속도 공식을 얻는다',
        desc: '가속도가 일정하면 v-t 그래프는 직선이고 그 아래 넓이가 변위다. '
            + '넓이를 직사각형과 삼각형으로 나누면 변위가 v0 t 더하기 2분의 1 a t 제곱이 된다.',
        svg: svg({ width: W, height: H, title: 'v-t 그래프의 넓이', desc: '사다리꼴을 직사각형과 삼각형으로 나눈 그림', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-5. 직각삼각형의 변의 비 — 삼각비의 도입
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 290;
    const g = frame({ xRange: [-0.3, 5.6], yRange: [-0.4, 3.6], box: { x: 40, y: 34, w: 300, h: 205 } });
    const O = [0, 0], B = [4, 0], C = [4, 3], b2 = [2, 0], c2 = [2, 1.5];
    const body = [
        g.line([O, b2, c2, O], { cls: 's3' }),
        g.line([O, B, C, O], { cls: 's1' }),
        rightAngle(g.X(4), g.Y(0), -1, -1),
        rightAngle(g.X(2), g.Y(0), -1, -1, 8),
        arc(g.X(0), g.Y(0), 40, 0, 36.87, 'θ'),
        g.label([2, 0], '이웃한 변 4', { dy: 20, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([4, 1.5], '마주보는 변 3', { dx: 8, cls: 'ink2', size: 'sm' }),
        g.label([1.9, 1.6], '빗변 5', { dx: -6, dy: -6, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([2, 0.75], '작은 삼각형 2 : 1.5 : 2.5', { dx: 9, dy: 4, cls: 'ink2', size: 'sm' }),
        txt(378, 56, 'sin θ = 마주보는 변 ÷ 빗변', { cls: 'ink' }),
        txt(392, 76, '= 3 ÷ 5 = 0.60', { cls: 'ink2', size: 'sm' }),
        txt(378, 106, 'cos θ = 이웃한 변 ÷ 빗변', { cls: 'ink' }),
        txt(392, 126, '= 4 ÷ 5 = 0.80', { cls: 'ink2', size: 'sm' }),
        txt(378, 156, 'tan θ = 마주보는 변 ÷ 이웃한 변', { cls: 'ink' }),
        txt(392, 176, '= 3 ÷ 4 = 0.75', { cls: 'ink2', size: 'sm' }),
        txt(378, 212, '삼각형을 절반으로 줄여도', { cls: 'ink2', size: 'sm' }),
        txt(378, 230, '1.5 ÷ 2.5 = 0.60 으로 같다', { cls: 'ink bold' }),
        txt(W - 14, H - 12, '각이 같으면 삼각형의 크기와 상관없이 변의 비가 정해진다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-trig-ratio',
        title: '직각삼각형에서 변의 비가 각으로 정해진다',
        desc: '한 각이 세타인 직각삼각형은 크기를 바꿔도 변끼리의 비가 같다. '
            + '그 비에 이름을 붙인 것이 사인, 코사인, 탄젠트다.',
        svg: svg({ width: W, height: H, title: '삼각비의 뜻', desc: '닮은 직각삼각형 두 개와 변의 비', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-6. 벡터의 덧셈 — 화살표 잇기
 * ------------------------------------------------------------------ */
add((() => {
    const W = 570, H = 310;
    const g = frame({ xRange: [-0.4, 5.4], yRange: [-0.4, 4.2], box: { x: 50, y: 34, w: 300, h: 220 } });
    const A = [3, 1], B = [1.5, 2.5], S = [4.5, 3.5];
    const body = [
        g.axes({ xLabel: 'x', yLabel: 'y', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 1, 2, 3, 4] }),
        g.vector([0, 0], A, { cls: 's1', marker: 'ar1' }),
        g.vector(A, S, { cls: 's2', marker: 'ar2' }),
        g.vector([0, 0], B, { cls: 's2', marker: 'ar2', width: 1.6 }),
        g.vector(B, S, { cls: 's1', marker: 'ar1', width: 1.6 }),
        g.vector([0, 0], S, { cls: 's3', marker: 'ar3', width: 3 }),
        g.label([1.6, 0.45], 'A', { cls: 'ink' }),
        g.label([3.8, 2.4], 'B', { dx: 6, cls: 'ink' }),
        g.label([0.6, 1.5], 'B', { dx: -14, cls: 'ink2', size: 'sm' }),
        g.label([2.9, 3.2], 'A', { dx: -4, dy: -6, cls: 'ink2', size: 'sm' }),
        g.label(S, 'A + B', { dx: 6, dy: -6, cls: 'ink' }),
        txt(378, 66, 'A 의 머리에 B 의 꼬리를', { cls: 'ink' }),
        txt(378, 86, '붙이면 처음 꼬리에서', { cls: 'ink' }),
        txt(378, 106, '마지막 머리까지가 합이다', { cls: 'ink' }),
        txt(378, 146, '순서를 바꿔 B 다음에 A 를', { cls: 'ink2', size: 'sm' }),
        txt(378, 164, '이어도 같은 자리에 닿는다', { cls: 'ink2', size: 'sm' }),
        txt(378, 200, '성분으로는 그냥 더한다', { cls: 'ink2', size: 'sm' }),
        txt(378, 222, '(3 + 1.5, 1 + 2.5)', { cls: 'ink bold' }),
        txt(378, 242, '= (4.5, 3.5)', { cls: 'ink bold' }),
        txt(W - 14, H - 12, '벡터의 합은 자리를 옮겨 이어 붙인 화살표다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-vector-add',
        title: '벡터를 더하는 법',
        desc: '두 화살표를 머리와 꼬리로 이어 붙이면 처음 꼬리에서 마지막 머리까지 그은 화살표가 합이다. '
            + '순서를 바꿔도 같은 점에 닿으므로 평행사변형이 만들어진다.',
        svg: svg({ width: W, height: H, title: '벡터의 덧셈', desc: '화살표를 이어 붙이는 방법과 평행사변형', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-7. 벡터의 성분 분해
 * ------------------------------------------------------------------ */
add((() => {
    const W = 570, H = 300;
    const g = frame({ xRange: [-0.4, 5.2], yRange: [-0.4, 3.8], box: { x: 52, y: 34, w: 290, h: 210 } });
    const A = [4, 3];
    const body = [
        g.axes({ xLabel: 'x', yLabel: 'y', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 1, 2, 3] }),
        g.guide([A[0], 0], A),
        g.guide([0, A[1]], A),
        g.vector([0, 0], [A[0], 0], { cls: 's2', marker: 'ar2', width: 2 }),
        g.vector([0, 0], [0, A[1]], { cls: 's3', marker: 'ar3', width: 2 }),
        g.vector([0, 0], A, { cls: 's1', marker: 'ar1', width: 3 }),
        g.dot(A, { cls: 'f1' }),
        rightAngle(g.X(4), g.Y(0), -1, -1, 9),
        arc(g.X(0), g.Y(0), 46, 0, 36.87, 'θ'),
        g.label([2, 0], 'A~x', { dy: -9, anchor: 'middle', cls: 'ink' }),
        g.label([0, 1.5], 'A~y', { dx: 9, dy: 4, cls: 'ink' }),
        g.label([2.1, 1.7], 'A', { dx: -6, dy: -8, anchor: 'end', cls: 'ink' }),
        txt(368, 62, '크기와 방향으로 적힌 벡터를', { cls: 'ink' }),
        txt(368, 82, '가로·세로 두 수로 바꾼다', { cls: 'ink' }),
        txt(368, 118, 'A~x = A cos θ = 5 × 0.80 = 4.0', { cls: 'ink2', size: 'sm' }),
        txt(368, 138, 'A~y = A sin θ = 5 × 0.60 = 3.0', { cls: 'ink2', size: 'sm' }),
        txt(368, 174, '되돌아갈 때는 피타고라스', { cls: 'ink2', size: 'sm' }),
        txt(368, 194, 'A = √(A~x² + A~y²) = 5.0', { cls: 'ink bold' }),
        txt(368, 216, 'tan θ = A~y / A~x = 0.75', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '성분으로 바꾸면 축마다 따로 계산할 수 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-vector-components',
        title: '벡터를 가로와 세로 성분으로 나누기',
        desc: '크기 A 이고 x축과 각 세타를 이루는 벡터는 가로 성분 A 코사인 세타와 세로 성분 A 사인 세타로 나뉜다. '
            + '거꾸로 두 성분에서 피타고라스 정리로 크기를, 탄젠트로 방향을 되찾는다.',
        svg: svg({ width: W, height: H, title: '벡터의 성분 분해', desc: '직각삼각형으로 본 벡터의 두 성분', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-8. 떨어뜨린 공과 수평으로 던진 공
 * ------------------------------------------------------------------ */
add((() => {
    const W = 590, H = 320;
    const g = frame({ xRange: [-1, 13], yRange: [-1.5, 21], box: { x: 58, y: 34, w: 420, h: 225 } });
    const ts = [0, 0.4, 0.8, 1.2, 1.6, 2.0];
    const yOf = t => 20 - 4.905 * t * t;
    const xOf = t => 1 + 5 * t;
    const b = [
        g.axes({ xLabel: 'x (m)', yLabel: 'y (m)', xTicks: [0, 5, 10], yTicks: [0, 5, 10, 15, 20], grid: false }),
        g.curve(x => yOf((x - 1) / 5), { from: 1, to: 11, cls: 's2', dash: '5 4' }),
        g.line([[1, 20], [1, 0.38]], { cls: 's1', dash: '5 4' }),
    ];
    for (const t of ts) {
        b.push(g.guide([1, yOf(t)], [xOf(t), yOf(t)]));
        b.push(g.dot([1, yOf(t)], { cls: 'f1', r: 5 }));
        b.push(g.dot([xOf(t), yOf(t)], { cls: 'f2', r: 5 }));
    }
    b.push(g.label([6.4, 20], '두 공을 0.4 s 간격으로 찍은 위치', { dy: -12, anchor: 'middle', cls: 'ink' }));
    b.push(g.label([12.6, 13.5], '점선은 같은 시각', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g.label([12.6, 13.5], '= 같은 높이', { anchor: 'end', dy: 16, cls: 'ink2', size: 'sm' }));
    b.push(legend(178, 178, [{ slot: 1, name: '가만히 놓은 공' }, { slot: 2, name: '수평으로 던진 공' }]));
    b.push(txt(W - 14, H - 12, '수평 속도는 떨어지는 속도에 관여하지 않는다 — 두 공은 동시에 닿는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'kin-projectile-independence',
        title: '떨어뜨린 공과 수평으로 던진 공은 동시에 닿는다',
        desc: '같은 높이에서 하나는 놓고 하나는 수평으로 던져 같은 시간 간격으로 위치를 찍으면 '
            + '두 공의 높이가 언제나 같다. 수직 운동과 수평 운동이 서로 간섭하지 않기 때문이다.',
        svg: svg({ width: W, height: H, title: '수직 운동과 수평 운동의 독립', desc: '0.4초 간격으로 찍은 두 공의 위치', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-9. 포물체의 궤적과 속도 성분
 * ------------------------------------------------------------------ */
add((() => {
    const W = 610, H = 330;
    const G = 9.81, v0 = 20, th = 40 * Math.PI / 180;
    const vx = v0 * Math.cos(th), vy0 = v0 * Math.sin(th);
    const R = (v0 * v0 * Math.sin(2 * th)) / G;
    const Hm = (vy0 * vy0) / (2 * G);
    const y = x => x * Math.tan(th) - (G * x * x) / (2 * v0 * v0 * Math.cos(th) ** 2);
    const g = frame({ xRange: [-2, R + 8], yRange: [-2.4, 15], box: { x: 56, y: 30, w: 430, h: 235 } });
    const K = 0.5;                                   // m/s 를 그림의 m 로 바꾸는 배율
    const at = x => {
        const t = x / vx;
        return [x, y(x), vx, vy0 - G * t];
    };
    const arrowSet = (x, showComp) => {
        const [px0, py0, ux, uy] = at(x);
        const out = [g.vector([px0, py0], [px0 + ux * K, py0 + uy * K], { cls: 's1', marker: 'ar1', width: 2.6 })];
        if (showComp) {
            out.push(g.vector([px0, py0], [px0 + ux * K, py0], { cls: 's2', marker: 'ar2', width: 1.6 }));
            out.push(g.vector([px0, py0], [px0, py0 + uy * K], { cls: 's3', marker: 'ar3', width: 1.6 }));
        }
        return out.join('');
    };
    const body = [
        g.axes({ xLabel: 'x (m)', yLabel: 'y (m)', xTicks: [0, 10, 20, 30, 40], yTicks: [0, 5, 10], grid: false }),
        g.curve(y, { from: 0, to: R, cls: 's1', dash: '6 4' }),
        arrowSet(0.2, true),
        arrowSet(R / 2, false),
        arrowSet(R * 0.82, true),
        g.dot([R / 2, Hm], { cls: 'f1' }),
        g.dot([R, 0], { cls: 'f1' }),
        g.guide([R / 2, 0], [R / 2, Hm]),
        g.label([R / 2, Hm / 2], 'H', { dx: 6, cls: 'ink' }),
        g.label([R / 2, Hm], '정점: v~y = 0, v~x 는 그대로', { dx: 8, dy: -12, cls: 'ink2', size: 'sm' }),
        g.label([2, 3.4], 'v~0', { dx: 4, cls: 'ink' }),
        arc(g.X(0), g.Y(0), 34, 0, 40, 'θ'),
        legend(W - 168, 44, [{ slot: 1, name: '속도 v' }, { slot: 2, name: '수평 성분 v~x' }, { slot: 3, name: '수직 성분 v~y' }]),
        txt(W - 14, H - 30, 'v~0 = 20 m/s, θ = 40° 이면 H = 8.4 m, R = 40 m (공기저항 없음)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '수평 성분은 길이가 변하지 않고 수직 성분만 줄었다가 방향을 바꾼다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'kin-projectile-trajectory',
        title: '포물체의 궤적과 속도의 두 성분',
        desc: '발사각 40도로 던진 물체의 궤적. 속도의 수평 성분은 끝까지 같고 수직 성분만 중력 때문에 줄어든다. '
            + '정점에서는 수직 성분이 0 이 되지만 수평 성분은 남아 있어 속도가 0 이 아니다.',
        svg: svg({ width: W, height: H, title: '포물체의 궤적', desc: '세 지점에서 속도를 성분으로 나눠 그린 포물선', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * kin-10. 강 건너기 — 속도의 덧셈
 * ------------------------------------------------------------------ */
add((() => {
    const W = 610, H = 340;
    const S = 20;                                   // 1 m/s = 20 px
    const yTop = 96, yBot = 250;
    const b = [];
    const panel = (x0, title, note) => {
        b.push(`<rect x="${x0}" y="${yTop}" width="270" height="${yBot - yTop}" rx="4" fill="var(--s1)" fill-opacity="0.07" stroke="none"/>`);
        b.push(`<path class="ax" d="M${x0} ${yTop} h270"/>`);
        b.push(`<path class="ax" d="M${x0} ${yBot} h270"/>`);
        for (let i = 0; i < 3; i += 1) {
            b.push(px(x0 + 24 + i * 90, yTop + 22, x0 + 64 + i * 90, yTop + 22, { cls: 's2', marker: 'ar2', width: 1.4 }));
        }
        b.push(txt(x0 + 135, yTop + 42, '강물이 흐르는 방향', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(x0, 34, title, { cls: 'ink bold' }));
        b.push(txt(x0, 56, note, { cls: 'ink2', size: 'sm' }));
        b.push(txt(x0 + 135, yBot + 20, '출발 기슭', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    };
    // (가) 뱃머리를 강과 수직으로
    panel(20, '(가) 뱃머리를 강과 수직으로', '건너기는 빠르지만 하류로 밀린다');
    const s1 = [60, yBot];
    b.push(px(s1[0], s1[1], s1[0], s1[1] - 4 * S, { cls: 's1', marker: 'ar1' }));
    b.push(txt(s1[0] - 6, s1[1] - 2 * S, '배 4.0', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(s1[0], s1[1] - 4 * S, s1[0] + 3 * S, s1[1] - 4 * S, { cls: 's2', marker: 'ar2' }));
    b.push(txt(s1[0] + 1.5 * S, s1[1] - 4 * S - 8, '강물 3.0', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(px(s1[0], s1[1], s1[0] + 3 * S, s1[1] - 4 * S, { cls: 's3', marker: 'ar3', width: 3 }));
    b.push(txt(s1[0] + 3 * S + 6, s1[1] - 2.2 * S, '땅에 대해 5.0', { cls: 'ink', size: 'sm' }));
    b.push(`<path class="gr" stroke-dasharray="5 4" d="M${s1[0] + 3 * S} ${s1[1] - 4 * S} L${s1[0] + (yBot - yTop) * 0.75} ${yTop}"/>`);
    b.push(pdot(s1[0] + (yBot - yTop) * 0.75, yTop, 'f3', 5));
    b.push(txt(s1[0] + (yBot - yTop) * 0.75 + 6, yTop - 8, '여기 닿는다', { cls: 'ink2', size: 'sm' }));
    // (나) 맞은편에 닿으려면
    panel(320, '(나) 맞은편에 닿으려면', '상류로 틀어 강물을 상쇄한다');
    const s2 = [430, yBot];
    b.push(px(s2[0], s2[1], s2[0] - 3 * S, s2[1] - 2.646 * S, { cls: 's1', marker: 'ar1' }));
    b.push(txt(s2[0] - 34, s2[1] - 14, '배 4.0', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(s2[0] - 3 * S, s2[1] - 2.646 * S, s2[0], s2[1] - 2.646 * S, { cls: 's2', marker: 'ar2' }));
    b.push(txt(s2[0] - 1.5 * S, s2[1] - 2.646 * S - 8, '강물 3.0', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(px(s2[0], s2[1], s2[0], s2[1] - 2.646 * S, { cls: 's3', marker: 'ar3', width: 3 }));
    b.push(txt(s2[0] + 8, s2[1] - 1.4 * S, '땅에 대해 2.65', { cls: 'ink', size: 'sm' }));
    b.push(`<path class="gr" stroke-dasharray="5 4" d="M${s2[0]} ${s2[1] - 2.646 * S} V${yTop}"/>`);
    b.push(pdot(s2[0], yTop, 'f3', 5));
    b.push(txt(s2[0] + 6, yTop - 8, '바로 맞은편', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, H - 12, '배가 물에 대해 내는 속도 + 물이 땅에 대해 흐르는 속도 = 배가 땅에 대해 가는 속도', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'kin-relative-boat',
        title: '강을 건너는 배의 속도 더하기',
        desc: '뱃머리를 강과 수직으로 두면 강물에 밀려 하류에 닿고, 상류로 틀면 강물이 상쇄되어 맞은편에 닿는다. '
            + '두 경우 모두 배가 물에 대해 내는 속도와 물이 땅에 대해 흐르는 속도를 벡터로 더한 결과다.',
        svg: svg({ width: W, height: H, title: '강 건너기와 상대속도', desc: '두 가지 뱃머리 방향에 대한 속도 삼각형', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-1. 알짜힘
 * ------------------------------------------------------------------ */
add((() => {
    const W = 590, H = 300;
    const b = [];
    const box = (cx, cy) => `<rect x="${cx - 26}" y="${cy - 20}" width="52" height="40" rx="4" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`;
    // (가) 한 직선 위
    b.push(txt(24, 34, '(가) 같은 직선 위의 두 힘', { cls: 'ink bold' }));
    const c1 = [150, 100];
    b.push(box(...c1));
    b.push(px(c1[0] + 26, c1[1], c1[0] + 106, c1[1], { cls: 's1', marker: 'ar1' }));
    b.push(txt(c1[0] + 112, c1[1] + 5, '8.0 N', { cls: 'ink', size: 'sm' }));
    b.push(px(c1[0] - 26, c1[1], c1[0] - 56, c1[1], { cls: 's2', marker: 'ar2' }));
    b.push(txt(c1[0] - 62, c1[1] + 5, '3.0 N', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(c1[0], c1[1] + 52, '방향이 반대면 빼면 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(px(c1[0] - 26, c1[1] + 86, c1[0] + 24, c1[1] + 86, { cls: 's3', marker: 'ar3', width: 3 }));
    b.push(txt(c1[0] + 30, c1[1] + 91, '알짜힘 5.0 N', { cls: 'ink bold' }));
    // (나) 수직인 두 힘
    b.push(txt(320, 34, '(나) 서로 수직인 두 힘', { cls: 'ink bold' }));
    const c2 = [400, 200];
    b.push(box(...c2));
    b.push(px(c2[0] + 26, c2[1], c2[0] + 86, c2[1], { cls: 's1', marker: 'ar1' }));
    b.push(txt(c2[0] + 92, c2[1] + 5, '3.0 N', { cls: 'ink', size: 'sm' }));
    b.push(px(c2[0], c2[1] - 20, c2[0], c2[1] - 100, { cls: 's2', marker: 'ar2' }));
    b.push(txt(c2[0] + 6, c2[1] - 106, '4.0 N', { cls: 'ink', size: 'sm' }));
    b.push(px(c2[0], c2[1], c2[0] + 60, c2[1] - 80, { cls: 's3', marker: 'ar3', width: 3 }));
    b.push(txt(c2[0] + 66, c2[1] - 78, '알짜힘 5.0 N', { cls: 'ink bold' }));
    b.push(txt(320, 250, '√(3.0² + 4.0²) = 5.0,  방향은 tan θ = 4.0/3.0', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, H - 12, '힘은 벡터다. 크기만 더하지 말고 방향까지 함께 더한다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'newton-net-force',
        title: '여러 힘을 하나로 합친 알짜힘',
        desc: '같은 직선 위의 두 힘은 방향을 부호로 보고 빼면 되지만, 방향이 다르면 벡터로 더해야 한다. '
            + '수직인 3 N 과 4 N 의 알짜힘은 7 N 이 아니라 5 N 이다.',
        svg: svg({ width: W, height: H, title: '알짜힘 구하기', desc: '같은 직선 위의 두 힘과 수직인 두 힘', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-2. 가속도는 힘에 비례하고 질량에 반비례한다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 650, H = 270;
    const g1 = frame({ xRange: [0, 9], yRange: [0, 4.6], box: { x: 54, y: 44, w: 210, h: 161 } });
    const g2 = frame({ xRange: [0, 6.4], yRange: [0, 13], box: { x: 372, y: 44, w: 210, h: 161 } });
    const body = [
        txt(104, 26, '질량을 2.0 kg 으로 고정', { cls: 'ink' }),
        g1.axes({ xLabel: 'F (N)', yLabel: 'a (m/s²)', xTicks: [0, 2, 4, 6, 8], yTicks: [0, 2, 4] }),
        g1.curve(F => F / 2, { cls: 's1' }),
        g1.dot([8, 4], { cls: 'f1' }),
        g1.label([4.6, 2.3], '기울기 = 1/m', { dx: 6, dy: -6, cls: 'ink2', size: 'sm' }),
        txt(160, 232, '힘을 두 배로 하면 가속도도 두 배', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(422, 26, '힘을 12 N 으로 고정', { cls: 'ink' }),
        g2.axes({ xLabel: 'm (kg)', yLabel: 'a (m/s²)', xTicks: [0, 2, 4, 6], yTicks: [0, 4, 8, 12] }),
        g2.curve(m => 12 / m, { from: 1, to: 6.2, cls: 's2' }),
        g2.dot([1, 12], { cls: 'f2' }),
        g2.dot([6, 2], { cls: 'f2' }),
        g2.label([3, 4], 'a = F / m', { dx: 10, dy: -6, cls: 'ink2', size: 'sm' }),
        txt(478, 232, '질량을 두 배로 하면 가속도는 절반', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 10, '두 관계를 한 줄로 묶은 것이 F = ma 다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'newton-second-law-graphs',
        title: '가속도는 힘에 비례하고 질량에 반비례한다',
        desc: '질량을 고정하고 힘을 바꾸면 가속도는 힘에 정비례해 원점을 지나는 직선이 되고, '
            + '힘을 고정하고 질량을 바꾸면 가속도는 질량에 반비례하는 곡선이 된다.',
        svg: svg({ width: W, height: H, title: 'F = ma 의 두 얼굴', desc: 'a-F 직선과 a-m 반비례 곡선', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-3. 엘리베이터 안의 저울
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 300;
    const b = [];
    const car = (cx, cy, nLen, cap1, cap2, aDir) => {
        b.push(`<rect x="${cx - 62}" y="${cy - 122}" width="124" height="142" rx="5" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
        // 사람
        b.push(`<circle cx="${cx - 2}" cy="${cy - 78}" r="8" fill="none" stroke="var(--ink2)" stroke-width="1.5"/>`);
        b.push(`<path class="ax" stroke-width="1.5" fill="none" d="M${cx - 2} ${cy - 70} V${cy - 40} M${cx - 2} ${cy - 62} L${cx - 14} ${cy - 50} M${cx - 2} ${cy - 62} L${cx + 10} ${cy - 50} M${cx - 2} ${cy - 40} L${cx - 12} ${cy - 22} M${cx - 2} ${cy - 40} L${cx + 8} ${cy - 22}"/>`);
        b.push(`<rect x="${cx - 30}" y="${cy - 22}" width="56" height="12" rx="2" fill="none" stroke="var(--ink2)" stroke-width="1.4"/>`);
        // 무게는 언제나 같은 길이
        b.push(px(cx + 24, cy - 60, cx + 24, cy - 22, { cls: 's1', marker: 'ar1', width: 2 }));
        b.push(txt(cx + 30, cy - 40, 'mg', { cls: 'ink', size: 'sm' }));
        if (nLen > 0) {
            b.push(px(cx - 34, cy - 22, cx - 34, cy - 22 - nLen, { cls: 's2', marker: 'ar2', width: 2 }));
            b.push(txt(cx - 40, cy - 26 - nLen, 'N', { anchor: 'end', cls: 'ink', size: 'sm' }));
        } else {
            b.push(txt(cx - 34, cy - 34, 'N = 0', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        if (aDir) {
            b.push(px(cx + 76, cy - 76 + (aDir > 0 ? 30 : 0), cx + 76, cy - 76 + (aDir > 0 ? -30 : 60), { cls: 's3', marker: 'ar3', width: 2 }));
            b.push(txt(cx + 82, cy - 74, 'a', { cls: 'ink', size: 'sm' }));
        }
        b.push(txt(cx, cy + 46, cap1, { anchor: 'middle', cls: 'ink' }));
        b.push(txt(cx, cy + 66, cap2, { anchor: 'middle', cls: 'ink bold' }));
    };
    car(110, 170, 38, '정지 또는 등속', '저울 589 N');
    car(310, 170, 46, '위로 2.0 m/s² 가속', '저울 709 N', +1);
    car(510, 170, 0, '줄이 끊어져 자유낙하', '저울 0 N', -1);
    b.push(txt(24, 30, '질량 60 kg 인 사람. 저울이 읽는 값은 수직항력 N 이지 무게 mg 가 아니다.', { cls: 'ink' }));
    b.push(txt(W - 14, H - 12, '무게는 세 경우 모두 같다. 달라지는 것은 바닥이 떠받치는 힘이다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'newton-elevator-scale',
        title: '엘리베이터 안 저울이 읽는 값',
        desc: '엘리베이터가 정지했을 때, 위로 가속할 때, 자유낙하할 때 저울이 읽는 값. '
            + '무게는 변하지 않고 바닥이 떠받치는 수직항력만 달라진다. 자유낙하에서는 수직항력이 0 이 된다.',
        svg: svg({ width: W, height: H, title: '엘리베이터와 겉보기 무게', desc: '세 가지 가속 상태에서의 자유물체도', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-4. 애트우드 기계
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 320;
    const b = [];
    const cx = 200, cy = 70, r = 40;
    b.push(`<path class="ax" stroke-width="2" fill="none" d="M${cx - 70} ${cy - r - 24} h140"/>`);
    b.push(`<path class="ax" d="M${cx} ${cy - r - 24} V${cy - r}"/>`);
    b.push(`<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--ink2)" stroke-width="1.8"/>`);
    b.push(pdot(cx, cy, 'ink2', 3));
    b.push(`<path class="ax" stroke-width="2" fill="none" d="M${cx - r} ${cy} V196 M${cx + r} ${cy} V152"/>`);
    const block = (x, y, w, h, label) => {
        b.push(`<rect x="${x - w / 2}" y="${y}" width="${w}" height="${h}" rx="4" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
        b.push(txt(x, y + h / 2 + 5, label, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    };
    block(cx - r, 196, 54, 42, '3.0 kg');
    block(cx + r, 152, 54, 42, '5.0 kg');
    // 장력은 줄을 따라 위로, 무게는 아래로
    b.push(px(cx - r, 196, cx - r, 146, { cls: 's2', marker: 'ar2', width: 2.4 }));
    b.push(txt(cx - r - 8, 166, 'T', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(cx + r, 152, cx + r, 102, { cls: 's2', marker: 'ar2', width: 2.4 }));
    b.push(txt(cx + r + 8, 122, 'T', { cls: 'ink', size: 'sm' }));
    b.push(px(cx - r, 238, cx - r, 272, { cls: 's1', marker: 'ar1', width: 2.4 }));
    b.push(txt(cx - r - 8, 268, 'm~1 g', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(cx + r, 194, cx + r, 246, { cls: 's1', marker: 'ar1', width: 2.4 }));
    b.push(txt(cx + r + 8, 240, 'm~2 g', { cls: 'ink', size: 'sm' }));
    // 가속도
    b.push(px(cx - r - 74, 224, cx - r - 74, 188, { cls: 's3', marker: 'ar3', width: 2 }));
    b.push(txt(cx - r - 80, 208, 'a', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(cx + r + 74, 172, cx + r + 74, 208, { cls: 's3', marker: 'ar3', width: 2 }));
    b.push(txt(cx + r + 80, 194, 'a', { cls: 'ink', size: 'sm' }));
    b.push(txt(352, 70, '줄이 늘어나지 않으므로', { cls: 'ink' }));
    b.push(txt(352, 90, '두 물체의 가속도 크기가 같다', { cls: 'ink' }));
    b.push(txt(352, 126, '도르래가 가볍고 마찰이 없으면', { cls: 'ink2', size: 'sm' }));
    b.push(txt(352, 144, '줄 양쪽의 T 도 같다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(352, 180, '가벼운 쪽: T − m~1 g = m~1 a', { cls: 'ink bold' }));
    b.push(txt(352, 202, '무거운 쪽: m~2 g − T = m~2 a', { cls: 'ink bold' }));
    b.push(txt(W - 14, H - 12, '두 물체를 따로 그려야 T 와 a 를 함께 구할 수 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'newton-atwood',
        title: '도르래에 걸린 두 물체(애트우드 기계)',
        desc: '가벼운 도르래에 줄로 이은 두 물체. 줄이 늘어나지 않아 가속도의 크기가 같고, '
            + '도르래가 가볍고 마찰이 없어 줄 양쪽의 장력이 같다. 물체마다 따로 운동방정식을 세운다.',
        svg: svg({ width: W, height: H, title: '애트우드 기계', desc: '도르래 양쪽 물체에 걸린 장력과 무게', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-5. 미는 힘과 마찰력의 관계
 * ------------------------------------------------------------------ */
add((() => {
    const W = 610, H = 300;
    const g = frame({ xRange: [0, 80], yRange: [0, 62], box: { x: 60, y: 34, w: 300, h: 215 } });
    const body = [
        g.axes({ xLabel: '미는 힘 F (N)', yLabel: '마찰력 f (N)', xTicks: [0, 20, 40, 60, 80], yTicks: [0, 20, 40, 60] }),
        g.line([[0, 0], [50, 50]], { cls: 's1' }),
        g.line([[50, 40], [78, 40]], { cls: 's2' }),
        g.guide([50, 0], [50, 50]),
        g.guide([0, 50], [50, 50]),
        g.guide([0, 40], [50, 40]),
        `<path class="cv s2" stroke-dasharray="4 4" d="M${g.X(50)} ${g.Y(50)} L${g.X(50)} ${g.Y(40)}"/>`,
        g.dot([50, 50], { cls: 'f1' }),
        g.dot([50, 40], { cls: 'f2' }),
        g.label([50, 50], '최대 정지마찰 μ~s N', { dx: 6, dy: -8, cls: 'ink' }),
        g.label([64, 40], 'μ~k N', { dy: -10, anchor: 'middle', cls: 'ink' }),
        g.label([22, 30], 'f = F', { dx: -10, dy: 0, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([24, 6], '아직 정지', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([64, 6], '미끄러지는 중', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(388, 62, '움직이기 전에는 마찰력이', { cls: 'ink' }),
        txt(388, 82, '미는 만큼만 커진다', { cls: 'ink' }),
        txt(388, 118, '정지마찰은 f ≤ μ~s N 이라는', { cls: 'ink2', size: 'sm' }),
        txt(388, 136, '부등식이지 등식이 아니다', { cls: 'ink2', size: 'sm' }),
        txt(388, 172, '한 번 미끄러지면 마찰이', { cls: 'ink' }),
        txt(388, 192, '조금 줄고 거의 일정해진다', { cls: 'ink' }),
        txt(388, 226, 'N = 100 N, μ~s = 0.50, μ~k = 0.40 인 예', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '무거운 짐이 처음 밀 때 가장 힘든 이유가 이 꺾인 점이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'newton-friction-graph',
        title: '미는 힘에 따라 마찰력이 어떻게 변하는가',
        desc: '미는 힘이 작을 때 정지마찰은 미는 힘과 같은 크기로 따라 커진다. '
            + '최대 정지마찰을 넘는 순간 물체가 미끄러지기 시작하고, 마찰력은 조금 작은 값으로 떨어져 거의 일정해진다.',
        svg: svg({ width: W, height: H, title: '정지마찰과 운동마찰', desc: '미는 힘 대 마찰력 그래프', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-6. 경사면 위 물체의 자유물체도
 * ------------------------------------------------------------------ */
add((() => {
    const W = 590, H = 350;
    const deg = 30, rad = deg * Math.PI / 180;
    const A = [60, 280], B = [380, 280];
    const C = [380, 280 - 320 * Math.tan(rad)];
    const t = 0.56;                                   // 경사면 위 물체의 위치(0~1)
    const P = [A[0] + (C[0] - A[0]) * t, A[1] + (C[1] - A[1]) * t];
    const ux = Math.cos(rad), uy = -Math.sin(rad);    // 경사 위쪽 단위벡터
    const nx = -Math.sin(rad), ny = -Math.cos(rad);   // 경사면 바깥쪽 법선
    const bw = 48, bh = 28;
    const cxp = P[0] + nx * (bh / 2), cyp = P[1] + ny * (bh / 2);
    const L = 96;
    const b = [];
    b.push(`<path class="ax" fill="none" d="M${A[0]} ${A[1]} L${B[0]} ${B[1]} L${C[0]} ${C[1]} Z"/>`);
    b.push(arc(A[0], A[1], 62, 0, deg, `θ = ${deg}°`));
    b.push(`<g transform="translate(${P[0]} ${P[1]}) rotate(${-deg})">`
        + `<rect x="${-bw / 2}" y="${-bh}" width="${bw}" height="${bh}" rx="3" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`
        + `<text class="sm ink2" x="0" y="${-bh / 2 + 4}" text-anchor="middle">m</text></g>`);
    // 무게(연직 아래)
    b.push(px(cxp, cyp, cxp, cyp + L, { cls: 's1', marker: 'ar1' }));
    b.push(txt(cxp + 6, cyp + L + 2, 'mg', { cls: 'ink' }));
    // 수직항력
    b.push(px(cxp, cyp, cxp + nx * L, cyp + ny * L, { cls: 's2', marker: 'ar2' }));
    b.push(txt(cxp + nx * L - 6, cyp + ny * L - 6, 'N', { anchor: 'end', cls: 'ink' }));
    // 마찰(경사 위쪽)
    b.push(px(cxp, cyp, cxp + ux * 74, cyp + uy * 74, { cls: 's3', marker: 'ar3' }));
    b.push(txt(cxp + ux * 74 + 6, cyp + uy * 74 - 4, 'f', { cls: 'ink' }));
    // 무게의 성분 분해(점선)
    const dsx = -ux, dsy = -uy;                        // 경사 아래쪽
    b.push(px(cxp, cyp, cxp + dsx * L * Math.sin(rad), cyp + dsy * L * Math.sin(rad),
        { cls: 's1', marker: 'ar1', width: 1.5, dash: '5 4' }));
    b.push(txt(cxp + dsx * L * Math.sin(rad) - 6, cyp + dsy * L * Math.sin(rad) + 16, 'mg sin θ',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(px(cxp, cyp, cxp - nx * L * Math.cos(rad), cyp - ny * L * Math.cos(rad),
        { cls: 's1', marker: 'ar1', width: 1.5, dash: '5 4' }));
    b.push(txt(cxp - nx * L * Math.cos(rad) + 8, cyp - ny * L * Math.cos(rad) + 4, 'mg cos θ',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(24, 32, '축을 경사면에 맞춰 돌린다. 그러면 가속도가 한 축에만 생긴다.', { cls: 'ink' }));
    b.push(txt(414, 84, '경사면에 수직:', { cls: 'ink' }));
    b.push(txt(414, 104, 'N = mg cos θ', { cls: 'ink bold' }));
    b.push(txt(414, 140, '경사면 방향:', { cls: 'ink' }));
    b.push(txt(414, 160, 'mg sin θ − f = m a', { cls: 'ink bold' }));
    b.push(txt(414, 196, '무게를 두 조각으로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(414, 214, '나누는 것이 첫 단계다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, H - 12, '경사각 θ 는 무게와 수직항력 사이 각으로 다시 나타난다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'newton-incline-fbd',
        title: '경사면 위 물체에 작용하는 힘',
        desc: '경사각 30도인 면 위 물체에 걸린 무게, 수직항력, 마찰력. 무게를 경사면 방향 성분 mg 사인 세타와 '
            + '경사면에 수직인 성분 mg 코사인 세타로 나누면 축마다 식을 하나씩 세울 수 있다.',
        svg: svg({ width: W, height: H, title: '경사면의 자유물체도', desc: '무게를 경사면 좌표로 분해한 그림', body: b.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * newton-7. 등속원운동에서 속도 변화는 중심을 향한다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 610, H = 330;
    const cx = 190, cy = 180, r = 100, Lv = 66;
    const pt = a => [cx + r * Math.cos(a * Math.PI / 180), cy - r * Math.sin(a * Math.PI / 180)];
    const tv = a => [-Math.sin(a * Math.PI / 180), -Math.cos(a * Math.PI / 180)];
    const P1 = pt(60), P2 = pt(120), d1 = tv(60), d2 = tv(120);
    const b = [];
    b.push(`<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--grid)" stroke-width="1.5" stroke-dasharray="6 5"/>`);
    b.push(pdot(cx, cy, 'ink2', 3.5));
    b.push(txt(cx + 8, cy + 16, 'O', { cls: 'ink2', size: 'sm' }));
    b.push(`<path class="gr" d="M${cx} ${cy} L${P1[0]} ${P1[1]} M${cx} ${cy} L${P2[0]} ${P2[1]}"/>`);
    b.push(txt((cx + P1[0]) / 2 + 8, (cy + P1[1]) / 2, 'r', { cls: 'ink2', size: 'sm' }));
    b.push(pdot(P1[0], P1[1], 'f1', 4.5));
    b.push(pdot(P2[0], P2[1], 'f1', 4.5));
    b.push(px(P1[0], P1[1], P1[0] + d1[0] * Lv, P1[1] + d1[1] * Lv, { cls: 's1', marker: 'ar1' }));
    b.push(txt(P1[0] + d1[0] * Lv - 4, P1[1] + d1[1] * Lv - 8, '처음 v', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(P2[0], P2[1], P2[0] + d2[0] * Lv, P2[1] + d2[1] * Lv, { cls: 's1', marker: 'ar1' }));
    b.push(txt(P2[0] + d2[0] * Lv + 2, P2[1] + d2[1] * Lv + 16, '나중 v', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(P1[0] + 10, P1[1] - 10, '처음 위치', { cls: 'ink2', size: 'sm' }));
    b.push(txt(P2[0] - 10, P2[1] - 12, '나중 위치', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(px(cx - 34, cy + r + 30, cx + 34, cy + r + 30, { cls: 'ink2', marker: 'ark', width: 1.5 }));
    b.push(txt(cx, cy + r + 22, '이 방향으로 돈다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 속도 삼각형
    const Q = [430, 110];
    b.push(txt(360, 40, '두 속도를 한 점에 모아 그리면', { cls: 'ink' }));
    b.push(px(Q[0], Q[1], Q[0] + d2[0] * Lv, Q[1] + d2[1] * Lv, { cls: 's1', marker: 'ar1' }));
    b.push(txt(Q[0] + d2[0] * Lv - 6, Q[1] + d2[1] * Lv + 4, '처음 v', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(Q[0], Q[1], Q[0] + d1[0] * Lv, Q[1] + d1[1] * Lv, { cls: 's1', marker: 'ar1' }));
    b.push(txt(Q[0] + d1[0] * Lv - 4, Q[1] + d1[1] * Lv - 8, '나중 v', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(px(Q[0] + d2[0] * Lv, Q[1] + d2[1] * Lv, Q[0] + d1[0] * Lv, Q[1] + d1[1] * Lv, { cls: 's2', marker: 'ar2', width: 3 }));
    b.push(txt(Q[0] + (d1[0] + d2[0]) * Lv / 2 - 8, Q[1] + (d1[1] + d2[1]) * Lv / 2 + 6, 'Δv', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(360, 240, 'Δv 는 두 위치의 한가운데에서', { cls: 'ink2', size: 'sm' }));
    b.push(txt(360, 258, '원의 중심을 가리킨다', { cls: 'ink bold' }));
    b.push(txt(360, 286, '가속도 = Δv / Δt 도 중심 방향', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, H - 10, '속력이 일정해도 방향이 변하면 가속도가 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'newton-circular-delta-v',
        title: '등속원운동에서 속도의 변화는 중심을 향한다',
        desc: '원 위 두 지점의 속도는 크기가 같고 방향만 다르다. 두 화살표를 한 점에 모아 빼면 '
            + '그 차이인 델타 v 가 원의 중심 쪽을 가리킨다. 그래서 가속도의 방향도 중심 쪽이다.',
        svg: svg({ width: W, height: H, title: '구심가속도의 방향', desc: '원 위 두 속도 벡터와 그 차이', body: b.join('') }),
    };
})());

export default figures;
