/**
 * 일반물리학 문서에 들어가는 그림들.
 *
 * 각 항목은 { name, title, desc, svg } 를 돌려준다.
 * name 이 파일 이름이 되고, title/desc 는 접근성 텍스트로 SVG 안에 들어간다.
 * 문서 쪽 캡션은 pug 의 +w3img 두 번째 인자로 따로 적는다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);

/* ------------------------------------------------------------------ *
 * 1. 등가속도 운동의 세 그래프
 *    a 는 상수, v 는 1차, x 는 2차라는 관계를 한눈에 보이게 세로로 쌓는다.
 *    같은 시간축을 공유하지만 세로축의 단위가 다르므로 축을 하나로 겹치지 않고
 *    작은 그래프 셋으로 나눈다(이중 축은 쓰지 않는다).
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 450;
    const a = 2, v0 = 1, x0 = 0;          // SI, 보기 좋은 값
    const T = 5;
    const panel = (top, yRange, yLabel, f, cls, ticks, note) => {
        const g = frame({ xRange: [0, T], yRange, box: { x: 60, y: top, w: 400, h: 96 } });
        return g.axes({ xLabel: 't (s)', yLabel, xTicks: [0, 1, 2, 3, 4, 5], yTicks: ticks })
            + g.curve(f, { cls })
            + txt(492, top + 54, note, { cls: 'ink2', size: 'sm' });
    };
    const body = [
        panel(30, [0, 3], 'a (m/s²)', () => a, 's3', [0, 2], '일정'),
        panel(170, [0, 12], 'v (m/s)', t => v0 + a * t, 's2', [0, 5, 10], '기울기 = a'),
        panel(300, [0, 30], 'x (m)', t => x0 + v0 * t + 0.5 * a * t * t, 's1', [0, 10, 20], '기울기 = v'),
    ].join('');
    return {
        name: 'kinematics-atvx',
        title: '등가속도 운동의 a-t, v-t, x-t 그래프',
        desc: '가속도가 일정하면 속도는 시간에 비례해 늘고 위치는 시간의 제곱으로 늘어난다. '
            + 'v-t 그래프의 기울기가 가속도이고, x-t 그래프의 기울기가 속도다.',
        svg: svg({ width: W, height: H, title: '등가속도 운동의 세 그래프', desc: 'a는 일정, v는 직선, x는 포물선', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 벡터의 성분 분해
 * ------------------------------------------------------------------ */
add((() => {
    const W = 500, H = 330;
    const g = frame({ xRange: [-0.4, 5], yRange: [-0.4, 3.6], box: { x: 92, y: 30, w: 330, h: 225 } });
    const A = [4, 3];
    const body = [
        g.axes({ xLabel: 'x', yLabel: 'y', xTicks: [0, 1, 2, 3, 4], yTicks: [0, 1, 2, 3] }),
        g.guide([A[0], 0], A),
        g.guide([0, A[1]], A),
        g.vector([0, 0], [A[0], 0], { cls: 's2', marker: 'ar2', width: 2 }),
        g.vector([0, 0], [0, A[1]], { cls: 's3', marker: 'ar3', width: 2 }),
        g.vector([0, 0], A, { cls: 's1', marker: 'ar1', width: 3 }),
        g.label([A[0] / 2, 0], 'A~x = A cos θ', { dy: 34, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([0, A[1] / 2], 'A~y = A sin θ', { dx: -12, dy: 4, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label(A, 'A', { dx: 8, dy: -6, cls: 'ink' }),
        g.dot(A, { cls: 'f1' }),
        arc(g.X(0), g.Y(0), 46, 0, Math.atan2(A[1], A[0]) * 180 / Math.PI, 'θ'),
        txt(W - 12, H - 12, 'A = √(A~x² + A~y²),   θ = arctan(A~y / A~x)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'vector-components',
        title: '벡터의 성분 분해',
        desc: '벡터 A를 x축과 y축 성분으로 나눈 그림. Aₓ는 A cos θ, A_y는 A sin θ이고 '
            + '두 성분으로부터 크기와 방향을 되찾을 수 있다.',
        svg: svg({ width: W, height: H, title: '벡터의 성분 분해', desc: 'A를 Aₓ와 A_y로 나눈 직각삼각형', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 포물체 궤적
 * ------------------------------------------------------------------ */
add((() => {
    const W = 560, H = 320;
    const g0 = 9.81, v0 = 20, th = 50 * Math.PI / 180;
    const R = (v0 * v0 * Math.sin(2 * th)) / g0;
    const Hm = (v0 * v0 * Math.sin(th) ** 2) / (2 * g0);
    const y = x => x * Math.tan(th) - (g0 * x * x) / (2 * v0 * v0 * Math.cos(th) ** 2);
    const g = frame({ xRange: [-2, R + 6], yRange: [-1.5, Hm + 4], box: { x: 55, y: 28, w: 450, h: 230 } });
    const apex = [R / 2, Hm];
    const body = [
        g.axes({ xLabel: 'x (m)', yLabel: 'y (m)', xTicks: [0, 10, 20, 30, 40], yTicks: [0, 5, 10] }),
        g.curve(y, { from: 0, to: R, cls: 's1' }),
        g.guide([apex[0], 0], apex),
        g.dot(apex, { cls: 'f1' }),
        g.dot([R, 0], { cls: 'f1' }),
        // 정점에서는 수직 속도만 0 이고 수평 속도는 그대로다 — 가장 흔한 오해라 명시한다.
        g.vector(apex, [apex[0] + 7, Hm], { cls: 's2', marker: 'ar2', width: 2 }),
        g.label([apex[0] + 7, Hm], 'v~x (일정)', { dx: 6, dy: -4, cls: 'ink2', size: 'sm' }),
        g.label(apex, 'v~y = 0', { dx: -6, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([apex[0], Hm / 2], 'H', { dx: 6, cls: 'ink' }),
        g.label([R, 0], 'R', { dx: 4, dy: 34, anchor: 'middle', cls: 'ink' }),
        g.guide([R, 0], [R, -1.2]),
        g.vector([0, 0], [4.5, 4.5 * Math.tan(th)], { cls: 's2', marker: 'ar2', width: 2.5 }),
        g.label([4.5, 4.5 * Math.tan(th)], 'v~0', { dx: 4, dy: -2, cls: 'ink' }),
        arc(g.X(0), g.Y(0), 34, 0, 50, 'θ'),
        txt(W - 12, H - 14, 'v~0 = 20 m/s, θ = 50°,   R = v~0² sin2θ / g', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'projectile-trajectory',
        title: '포물체 운동의 궤적',
        desc: '수평 방향은 등속, 수직 방향은 등가속도이므로 궤적이 포물선이 된다. '
            + '최고점에서 수직 속도만 0이 되고 수평 속도는 그대로 유지된다.',
        svg: svg({ width: W, height: H, title: '포물체 운동의 궤적', desc: '발사각 50도, 초속 20 m/s 인 포물선 궤적', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 사거리와 발사각 — 45°에서 최대이고 여각이 같은 거리를 준다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 480, H = 300;
    const g0 = 9.81, v0 = 20;
    const R = d => (v0 * v0 * Math.sin(2 * d * Math.PI / 180)) / g0;
    const g = frame({ xRange: [0, 90], yRange: [0, 46], box: { x: 55, y: 30, w: 380, h: 210 } });
    const body = [
        g.axes({ xLabel: 'θ (°)', yLabel: 'R (m)', xTicks: [0, 15, 30, 45, 60, 75, 90], yTicks: [0, 20, 40] }),
        g.curve(R, { cls: 's1' }),
        g.guide([45, 0], [45, R(45)]),
        g.dot([45, R(45)], { cls: 'f1' }),
        g.label([45, R(45)], '45° 최대', { dx: 6, dy: -8, cls: 'ink' }),
        g.guide([30, 0], [30, R(30)]),
        g.guide([60, 0], [60, R(60)]),
        g.guide([30, R(30)], [60, R(60)]),
        g.dot([30, R(30)], { cls: 'f2' }),
        g.dot([60, R(60)], { cls: 'f2' }),
        g.label([45, 8], '여각 30°와 60°는 같은 거리', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.guide([45, 10], [45, R(30) - 2]),
        txt(W - 12, H - 12, 'v~0 = 20 m/s (공기저항 없음, 같은 높이)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'projectile-range',
        title: '발사각에 따른 수평 도달거리',
        desc: '같은 높이로 돌아오는 경우 수평 도달거리는 45도에서 최대가 되고, '
            + '서로 여각인 두 각(예: 30도와 60도)은 같은 거리를 준다.',
        svg: svg({ width: W, height: H, title: '발사각에 따른 수평 도달거리', desc: '45도에서 최대, 여각끼리 같은 값', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 경사면 위 물체의 자유물체도
 *    좌표계가 없는 기하 그림이라 화소 좌표로 직접 그린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 520, H = 330;
    const deg = 28, rad = deg * Math.PI / 180;
    const A = [70, 268];                       // 경사면 왼쪽 아래(직각 꼭짓점)
    const B = [430, 268];                      // 오른쪽 아래
    const C = [430, 268 - 360 * Math.tan(rad)]; // 위 꼭짓점 — 오른쪽이 높은 경사
    const P = [250, 268 - 180 * Math.tan(rad)]; // 물체 위치(경사면 중앙)
    const bw = 46, bh = 28;
    // 경사면에 붙인 상자: 경사 방향으로 회전시킨다
    const box = `<g transform="translate(${P[0]} ${P[1]}) rotate(${-deg})">`
        + `<rect x="${-bw / 2}" y="${-bh}" width="${bw}" height="${bh}" rx="3" fill="none" stroke="var(--ink2)" stroke-width="1.5"/>`
        + `<text class="sm ink2" x="0" y="${-bh / 2 + 4}" text-anchor="middle">m</text></g>`;
    // 힘 벡터의 시작점은 상자 중심(경사면에서 살짝 띄운 곳)
    const cx = P[0] + (bh / 2) * Math.sin(rad);
    const cy = P[1] - (bh / 2) * Math.cos(rad);
    const Lm = 92;
    const nAng = rad;                                   // 수직항력: 경사면에 수직
    const body = [
        `<path class="ax" d="M${A[0]} ${A[1]} L${B[0]} ${B[1]} L${C[0]} ${C[1]} Z" fill="none"/>`,
        arc(B[0], B[1], 58, 180 - deg, 180, `θ = ${deg}°`),
        box,
        // 무게: 항상 연직 아래
        px(cx, cy, cx, cy + Lm, { cls: 's1', marker: 'ar1' }),
        txt(cx + 6, cy + Lm + 4, 'mg', { cls: 'ink' }),
        // 수직항력: 경사면 법선
        px(cx, cy, cx + Lm * Math.sin(nAng), cy - Lm * Math.cos(nAng), { cls: 's2', marker: 'ar2' }),
        txt(cx + Lm * Math.sin(nAng) + 6, cy - Lm * Math.cos(nAng), 'N', { cls: 'ink' }),
        // 마찰: 경사면을 따라 위쪽(미끄러짐을 막는 방향)
        px(cx, cy, cx + 78 * Math.cos(rad), cy - 78 * Math.sin(rad), { cls: 's3', marker: 'ar3' }),
        txt(cx + 78 * Math.cos(rad) + 6, cy - 78 * Math.sin(rad) - 4, 'f', { cls: 'ink' }),
        // 무게의 성분 분해(점선) — 경사면 문제의 핵심
        // 무게의 경사면 성분(점선). 경사 아래 방향으로 mg sin θ 만큼.
        px(cx, cy, cx - Lm * Math.sin(rad) * Math.cos(rad), cy + Lm * Math.sin(rad) * Math.sin(rad),
            { cls: 's1', marker: 'ar1', width: 1.6, dash: '4 3' }),
        txt(cx - Lm * Math.sin(rad) * Math.cos(rad) - 8, cy + Lm * Math.sin(rad) * Math.sin(rad) + 14,
            'mg sin θ', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(24, 40, '경사면 좌표로 나누면', { cls: 'ink', anchor: 'start' }),
        txt(24, 60, 'mg sin θ  (경사 아래 방향)', { cls: 'ink2', size: 'sm' }),
        txt(24, 78, 'mg cos θ  (경사면에 수직)', { cls: 'ink2', size: 'sm' }),
        txt(24, 100, 'N = mg cos θ,  f ≤ μ N', { cls: 'ink2', size: 'sm' }),
        legend(W - 150, 40, [{ slot: 1, name: '무게 mg' }, { slot: 2, name: '수직항력 N' }, { slot: 3, name: '마찰력 f' }]),
    ].join('');
    return {
        name: 'free-body-incline',
        title: '경사면 위 물체의 자유물체도',
        desc: '경사각 세타인 빗면 위 물체에 작용하는 세 힘. 무게는 연직 아래, 수직항력은 경사면에 수직, '
            + '마찰력은 경사면을 따라 작용한다. 무게를 경사면 좌표로 분해하면 mg sin θ와 mg cos θ가 된다.',
        svg: svg({ width: W, height: H, title: '경사면 위 물체의 자유물체도', desc: '무게·수직항력·마찰력 세 벡터', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 부피 단위의 크기 비교 — 1 m³ 안에 1 L 가 1000 개 들어간다
 *    "1 L 가 몇 m³ 인가"가 왜 헷갈리는지의 정체는 세제곱이다. 길이는 10배인데
 *    부피는 1000배라는 것을 그림으로 보이면 환산을 외울 필요가 없어진다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 560, H = 330;
    // 큰 정육면체(한 변 1 m)를 비스듬히 그리고, 그 안에 작은 칸(한 변 10 cm)을 표시한다.
    const ox = 70, oy = 250, S = 190, dx = 62, dy = -44;   // 밑면 원점, 한 변 길이, 안쪽 방향
    const P = (a, b, c) => [ox + a * S + b * dx, oy - c * S + b * dy];   // a:가로 b:깊이 c:높이 (0~1)
    const poly = (pts, fill, op) => `<path d="M${pts.map(p => p.join(' ')).join(' L')} Z" fill="${fill}" fill-opacity="${op}" stroke="var(--ink2)" stroke-width="1.2"/>`;
    const g = [];
    // 큰 상자 세 면
    g.push(poly([P(0,0,0), P(1,0,0), P(1,0,1), P(0,0,1)], 'var(--s1)', 0.06));       // 앞면
    g.push(poly([P(0,0,1), P(1,0,1), P(1,1,1), P(0,1,1)], 'var(--s1)', 0.12));       // 윗면
    g.push(poly([P(1,0,0), P(1,1,0), P(1,1,1), P(1,0,1)], 'var(--s1)', 0.03));       // 옆면
    // 앞면의 10x10 격자 — 한 칸이 한 변 10 cm
    for (let i = 1; i < 10; i += 1) {
        g.push(`<path class="gr" d="M${P(i/10,0,0).join(' ')} L${P(i/10,0,1).join(' ')}"/>`);
        g.push(`<path class="gr" d="M${P(0,0,i/10).join(' ')} L${P(1,0,i/10).join(' ')}"/>`);
    }
    // 한 칸만 색칠 = 1 L
    g.push(poly([P(0,0,0), P(0.1,0,0), P(0.1,0,0.1), P(0,0,0.1)], 'var(--s2)', 0.85));
    g.push(px(P(0.05,0,0.05)[0] - 60, P(0.05,0,0.05)[1] + 46, P(0.05,0,0.05)[0] - 6, P(0.05,0,0.05)[1] + 8,
        { cls: 's2', marker: 'ar2', width: 1.8 }));
    g.push(txt(P(0,0,0)[0] - 64, P(0,0,0)[1] + 62, '색칠한 칸 하나 = 1 L', { cls: 'ink', anchor: 'start' }));
    g.push(txt(P(0,0,0)[0] - 64, P(0,0,0)[1] + 80, '(한 변 10 cm 인 정육면체)', { cls: 'ink2', size: 'sm', anchor: 'start' }));
    // 치수선
    g.push(txt(...P(0.5,0,0), '', {}));
    g.push(txt(P(0.5,0,0)[0], P(0.5,0,0)[1] + 22, '1 m = 100 cm', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(txt(P(1,0,0.5)[0] + 14, P(1,0,0.5)[1], '1 m', { cls: 'ink2', size: 'sm' }));
    // 결론
    g.push(txt(W - 16, 40, '가로 10칸 × 세로 10칸 × 깊이 10칸', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 16, 62, '= 1000칸', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 16, 88, '1 m³ = 1000 L,   1 L = 0.001 m³', { anchor: 'end', cls: 'ink' }));
    g.push(txt(W - 16, H - 14, '길이는 10배지만 부피는 10³ = 1000배가 된다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'volume-litre-cubic-metre',
        title: '1 m³ 와 1 L 의 크기 비교',
        desc: '한 변 1 m 인 정육면체를 한 변 10 cm 인 칸으로 나누면 가로·세로·깊이로 각각 10칸씩, '
            + '모두 1000칸이 된다. 그 한 칸의 부피가 1 L 이므로 1 m³ 는 1000 L 이고 1 L 는 0.001 m³ 다.',
        svg: svg({ width: W, height: H, title: '1 m³ 안에 1 L 가 1000개', desc: '한 변 1 m 정육면체를 10 cm 칸으로 나눈 그림', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 유효숫자 — 자로 잰다는 것이 무엇인지
 * ------------------------------------------------------------------ */
add((() => {
    const W = 560, H = 250;
    const x0 = 60, x1 = 500, yr = 90;      // 자
    const g = [];
    // 자 눈금: 1 mm 간격
    g.push(`<path class="ax" d="M${x0} ${yr} H${x1}"/>`);
    for (let i = 0; i <= 44; i += 1) {
        const x = x0 + i * 10;
        const big = i % 10 === 0, mid = i % 5 === 0;
        g.push(`<path class="ax" stroke-width="1" d="M${x} ${yr} V${yr - (big ? 16 : mid ? 11 : 7)}"/>`);
        if (big) g.push(txt(x, yr + 16, String(i / 10), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x1 + 10, yr + 5, 'cm', { cls: 'ink2', size: 'sm' }));
    // 재는 물체: 2.36 cm 근처에서 끝난다
    const end = x0 + 23.6 * 10;
    g.push(`<rect x="${x0}" y="${yr + 30}" width="${end - x0}" height="26" rx="3" fill="var(--s1)" fill-opacity="0.18" stroke="var(--s1)" stroke-width="1.5"/>`);
    g.push(px(end, yr + 88, end, yr + 60, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(end, yr + 106, '여기서 끝난다', { anchor: 'middle', cls: 'ink' }));
    g.push(`<path class="gr" stroke-dasharray="3 3" d="M${end} ${yr} V${yr + 58}"/>`);
    // 설명
    g.push(txt(60, 34, '눈금은 1 mm 까지 있다 → 2.3 cm 까지는 확실하다', { cls: 'ink' }));
    g.push(txt(60, 54, '그 다음 자리는 눈대중이다 → 2.36 cm 의 6 은 어림값', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 16, H - 14, '확실한 자리 + 어림한 한 자리 = 유효숫자.  여기서는 3자리', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'significant-figures',
        title: '자로 길이를 잴 때의 유효숫자',
        desc: '눈금 간격이 1 mm 인 자로 물체를 재면 2.3 cm 까지는 눈금이 직접 알려주고 '
            + '그 아래 한 자리는 눈대중으로 읽는다. 확실한 자리에 어림한 한 자리를 더한 것이 유효숫자다.',
        svg: svg({ width: W, height: H, title: '자로 재기와 유효숫자', desc: '1 mm 눈금 자로 2.36 cm 를 읽는 그림', body: g.join('') }),
    };
})());

export default figures;
