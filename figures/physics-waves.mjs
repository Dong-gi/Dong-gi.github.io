/**
 * 8장 진동과 파동, 9장 유체의 그림.
 *
 * physics.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를 돌려주고
 * name 이 파일 이름(/figures/physics/<name>.svg)이 된다.
 * 이름은 wave- / fluid- 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 라벨은 유니코드 그리스 문자와 `v~0` 꼴의 아래첨자 표기로 적는다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);

const TAU = Math.PI * 2;
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구 — 용수철, 벽, 양쪽 화살표처럼 여러 그림에서 되풀이되는 것들
 * ------------------------------------------------------------------ */

/** 지그재그 용수철. 화소 좌표. */
function spring(x1, x2, y, n = 9, amp = 11) {
    const seg = (x2 - x1) / (n + 0.5);
    let d = `M${r2(x1)} ${r2(y)}`;
    for (let i = 0; i < n; i += 1) {
        d += ` L${r2(x1 + seg * (i + 0.25))} ${r2(y - amp)} L${r2(x1 + seg * (i + 0.75))} ${r2(y + amp)}`;
    }
    d += ` L${r2(x2)} ${r2(y)}`;
    return `<path d="${d}" fill="none" stroke="var(--ink2)" stroke-width="1.6" stroke-linejoin="round"/>`;
}

/** 벽·바닥 같은 고정면. 빗금으로 표시한다. dir 은 빗금이 뻗는 쪽. */
function hatch(x1, y1, x2, y2, dir = 1, gap = 10, len = 8) {
    const dx = x2 - x1, dy = y2 - y1;
    const L = Math.hypot(dx, dy);
    const ux = dx / L, uy = dy / L;
    const nx = -uy * dir, ny = ux * dir;
    const out = [`<path class="ax" stroke-width="2" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`];
    for (let s = 0; s <= L; s += gap) {
        const bx = x1 + ux * s, by = y1 + uy * s;
        out.push(`<path class="gr" d="M${r2(bx)} ${r2(by)} L${r2(bx + nx * len - ux * len)} ${r2(by + ny * len - uy * len)}"/>`);
    }
    return out.join('');
}

/** 양쪽 화살표. 가운데에서 양 끝으로 두 번 그린다. */
function dbl(x1, y1, x2, y2, o = {}) {
    const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
    return px(mx, my, x1, y1, o) + px(mx, my, x2, y2, o);
}

/** 테두리만 있는 상자(물체). */
function boxr(x, y, w, h, label, cls = 'ink2') {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="3" fill="none" stroke="var(--${cls})" stroke-width="1.6"/>`
        + (label ? txt(x + w / 2, y + h / 2 + 4, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 물(유체) 채우기. */
function fill(d, color = 's1', op = 0.14) {
    return `<path d="${d}" fill="var(--${color})" fill-opacity="${op}" stroke="none"/>`;
}

/* ================================================================== *
 * 8장 — 진동
 * ================================================================== */

/* 1. 용수철의 복원력 — 변위에 비례하고 방향이 반대 */
add((() => {
    const W = 580, H = 340;
    const wallX = 62, nat = 190, bw = 50, bh = 34, zero = wallX + nat + bw / 2;
    const rows = [
        { y: 92, dx: 0, tag: 'x = 0', note: '힘 없음 — 평형점' },
        { y: 186, dx: 62, tag: 'x = +A', note: '힘은 왼쪽, 크기 kA' },
        { y: 278, dx: -31, tag: 'x = −A/2', note: '힘은 오른쪽, 크기 kA/2' },
    ];
    const g = [txt(16, 26, '용수철이 물체를 되돌리는 힘', { cls: 'ink bold' })];
    g.push(`<path class="gr" stroke-dasharray="5 4" d="M${zero} 46 V${H - 24}"/>`);
    g.push(txt(zero, 42, '평형점', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const r of rows) {
        const left = wallX + nat + r.dx;
        g.push(hatch(wallX, r.y - 30, wallX, r.y + 26, -1, 9, 7));
        g.push(spring(wallX, left, r.y));
        g.push(boxr(left, r.y - bh / 2, bw, bh, 'm'));
        const cx = left + bw / 2;
        if (r.dx !== 0) {
            const len = Math.abs(r.dx) * 1.15;
            g.push(px(cx, r.y - 30, cx - Math.sign(r.dx) * len, r.y - 30, { cls: 's2', marker: 'ar2' }));
            g.push(txt(cx - Math.sign(r.dx) * len, r.y - 38, 'F', { anchor: 'middle', cls: 'ink' }));
            g.push(dbl(zero, r.y + 30, cx, r.y + 30, { cls: 's1', marker: 'ar1', width: 1.4 }));
        }
        g.push(txt(W - 14, r.y - 4, r.tag, { anchor: 'end', cls: 'ink' }));
        g.push(txt(W - 14, r.y + 14, r.note, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(16, H - 10, '변위가 반이면 힘도 반. 부호가 −인 것은 늘 평형점 쪽을 향한다는 뜻이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'wave-spring-restoring',
        title: '용수철의 복원력',
        desc: '평형점에서 밀거나 당긴 거리에 비례하는 힘이 언제나 평형점 쪽으로 작용한다. '
            + '변위가 절반이면 힘도 절반이고, 평형점에서는 힘이 0이다.',
        svg: svg({ width: W, height: H, title: '용수철의 복원력', desc: '변위에 비례하고 방향이 반대인 힘', body: g.join('') }),
    };
})());

/* 2. 원 위의 점에서 사인 곡선이 나온다 */
add((() => {
    const W = 640, H = 330;
    const cx = 150, cy = 162, R = 74;
    const g = frame({ xRange: [0, TAU + 0.35], yRange: [-1.35, 1.35], box: { x: 300, y: 62, w: 280, h: 200 } });
    const th = 55 * Math.PI / 180;
    const P = [cx + R * Math.cos(th), cy - R * Math.sin(th)];
    const marks = [
        [Math.PI / 2, 'π/2'], [Math.PI, 'π'], [3 * Math.PI / 2, '3π/2'], [TAU, '2π'],
    ];
    const deg = [[Math.PI / 2, '90°'], [Math.PI, '180°'], [3 * Math.PI / 2, '270°'], [TAU, '360°']];
    const b = [
        txt(16, 26, '각이 커지면 원 위의 점은 오르내린다', { cls: 'ink bold' }),
        `<circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="var(--grid)" stroke-width="1.5"/>`,
        `<path class="ax" d="M${cx - R - 22} ${cy} H${cx + R + 22}"/>`,
        `<path class="ax" d="M${cx} ${cy + R + 22} V${cy - R - 22}"/>`,
        px(cx, cy, P[0], P[1], { cls: 's1', marker: 'ar1', width: 2 }),
        `<path class="gr" stroke-dasharray="4 3" d="M${r2(P[0])} ${r2(P[1])} L${cx} ${r2(P[1])}"/>`,
        `<path class="gr" stroke-dasharray="4 3" d="M${r2(P[0])} ${r2(P[1])} L${r2(P[0])} ${cy}"/>`,
        px(cx, cy, cx, P[1], { cls: 's2', marker: 'ar2', width: 2.5 }),
        txt(cx - 8, (cy + P[1]) / 2 + 4, '높이', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        arc(cx, cy, 30, 0, 55, 'θ'),
        `<circle class="f1" cx="${r2(P[0])}" cy="${r2(P[1])}" r="4"/>`,
        txt(cx + R + 6, cy + 18, '반지름 1', { cls: 'ink2', size: 'sm' }),
        `<path class="gr" stroke-dasharray="4 3" d="M${r2(P[0])} ${r2(P[1])} L${g.X(th)} ${g.Y(Math.sin(th))}"/>`,
        g.axes({ xLabel: 'θ (rad)', yLabel: 'sin θ', xTicks: [], yTicks: [-1, 0, 1] }),
        g.curve(Math.sin, { from: 0, to: TAU, cls: 's1' }),
        g.dot([th, Math.sin(th)], { cls: 'f1' }),
    ];
    for (const [t, s] of marks) b.push(g.guide([t, -1], [t, 1]));
    for (const [t, s] of marks) b.push(txt(g.X(t), g.Y(0) + 16, s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const [t, s] of deg) b.push(txt(g.X(t), g.Y(0) + 32, s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 26, '한 바퀴 = 2π rad = 360°', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, 'cos θ 는 같은 점의 가로 좌표이고, 그래프는 이것을 왼쪽으로 π/2 옮긴 모양이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'wave-sine-from-circle',
        title: '원 위의 점의 높이가 사인 곡선을 그린다',
        desc: '반지름 1인 원 위를 도는 점의 높이가 sin θ 다. 각이 계속 커지면 높이가 1과 −1 사이를 '
            + '오르내리므로 그래프가 물결 모양이 된다. 한 바퀴는 2π 라디안, 즉 360도다.',
        svg: svg({ width: W, height: H, title: '원과 사인 곡선', desc: '단위원 위 점의 높이를 각에 대해 펼친 그래프', body: b.join('') }),
    };
})());

/* 3. 등속 원운동의 사영이 단진동이다 */
add((() => {
    const W = 600, H = 330;
    const cx = 180, cy = 175, R = 110;
    const th = 42 * Math.PI / 180;
    const P = [cx + R * Math.cos(th), cy - R * Math.sin(th)];
    const Q = [P[0], cy];
    const aLen = 78;
    const b = [
        txt(16, 26, '원운동을 옆에서 보면 단진동이다', { cls: 'ink bold' }),
        `<circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="var(--grid)" stroke-width="1.5" stroke-dasharray="5 4"/>`,
        `<path class="ax" stroke-width="2.5" d="M${cx - R} ${cy} H${cx + R}"/>`,
        txt(cx - R - 6, cy + 4, '−A', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(cx + R + 6, cy + 4, '+A', { cls: 'ink2', size: 'sm' }),
        px(cx, cy, P[0], P[1], { cls: 'gr', marker: 'ark', width: 1.4 }),
        arc(cx, cy, 40, 0, 42, 'ωt'),
        `<path class="gr" stroke-dasharray="4 3" d="M${r2(P[0])} ${r2(P[1])} L${r2(Q[0])} ${r2(Q[1])}"/>`,
        `<circle class="f1" cx="${r2(P[0])}" cy="${r2(P[1])}" r="6"/>`,
        txt(P[0] + 10, P[1] - 8, 'P', { cls: 'ink' }),
        `<circle class="f2" cx="${r2(Q[0])}" cy="${r2(Q[1])}" r="6"/>`,
        txt(Q[0] + 8, Q[1] + 20, 'Q (그림자)', { cls: 'ink', size: 'sm' }),
        // 구심가속도와 그 가로 성분
        px(P[0], P[1], P[0] - aLen * Math.cos(th), P[1] + aLen * Math.sin(th), { cls: 's3', marker: 'ar3', width: 2.2 }),
        txt(P[0] + 12, P[1] + 22, '가속도 ω²A', { cls: 'ink2', size: 'sm' }),
        px(Q[0], Q[1], Q[0] - aLen * Math.cos(th), Q[1], { cls: 's3', marker: 'ar3', width: 2.2 }),
        txt(Q[0] - aLen * Math.cos(th) - 4, Q[1] + 24, '가로 성분', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(cx, cy + R + 26, cx + 40, cy + R + 26, { cls: 's1', marker: 'ar1', width: 1.6 }),
        txt(cx + 48, cy + R + 30, '일정한 각속도 ω', { cls: 'ink2', size: 'sm' }),
    ];
    const lines = [
        'P 는 반지름 A 인 원을 일정한 빠르기로 돈다.',
        'Q 는 P 의 그림자. 지름 위를 왕복한다.',
        '',
        'P 의 가속도는 늘 중심을 향하고 크기는 ω²A.',
        'Q 의 가속도는 그 가로 성분이므로',
        '    a = −ω²x   (변위에 비례, 방향은 반대)',
        '',
        '힘으로 쓰면 F = ma = −mω²x.',
        '용수철의 F = −kx 와 견주면 k = mω²,',
        '따라서 ω = √(k/m).',
    ];
    lines.forEach((s, i) => b.push(txt(340, 70 + i * 21, s, { cls: i === 5 || i === 9 ? 'ink' : 'ink2', size: i === 5 || i === 9 ? undefined : 'sm' })));
    return {
        name: 'wave-shm-projection',
        title: '등속 원운동의 사영이 단진동',
        desc: '반지름 A 인 원 위를 일정한 각속도로 도는 점의 그림자는 지름 위를 왕복한다. '
            + '원운동의 가속도는 중심을 향하고 크기가 ω²A 이므로, 그림자의 가속도는 그 가로 성분인 '
            + '−ω²x 가 된다. 변위에 비례하고 방향이 반대인 가속도, 즉 단진동이다.',
        svg: svg({ width: W, height: H, title: '원운동의 사영', desc: '원 위 점의 그림자가 단진동을 한다', body: b.join('') }),
    };
})());

/* 4. 단진동의 x-t, v-t, a-t */
add((() => {
    const W = 580, H = 450;
    const panel = (top, yLabel, f, cls, note) => {
        const g = frame({ xRange: [0, 2], yRange: [-1.35, 1.35], box: { x: 66, y: top, w: 370, h: 92 } });
        return g.axes({ xLabel: 't/T', yLabel, xTicks: [0, 0.5, 1, 1.5, 2], yTicks: [-1, 0, 1] })
            + g.curve(f, { cls })
            + txt(W - 12, top + 8, note, { anchor: 'end', cls: 'ink2', size: 'sm' });
    };
    const b = [
        txt(16, 24, '한 주기 동안 x, v, a 가 어떻게 어긋나 있는가', { cls: 'ink bold' }),
        panel(66, 'x / A', t => Math.cos(TAU * t), 's1', 'x = A cos ωt'),
        panel(198, 'v / Aω', t => -Math.sin(TAU * t), 's2', 'x 가 0 일 때 최대'),
        panel(330, 'a / Aω²', t => -Math.cos(TAU * t), 's3', 'x 와 늘 반대 부호'),
        txt(16, H - 10, '가장 빠른 순간은 평형점을 지날 때이고, 가장 세게 되돌려지는 순간은 끝점이다',
            { cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-shm-xva',
        title: '단진동의 변위·속도·가속도',
        desc: '변위가 최대인 순간에 속도는 0이고 가속도는 크기가 최대이면서 반대 방향이다. '
            + '속도는 물체가 평형점을 지날 때 가장 크다. 세 그래프는 같은 모양이 사분의 일 주기씩 어긋난 것이다.',
        svg: svg({ width: W, height: H, title: '단진동의 세 그래프', desc: 'x, v, a 가 각각 4분의 1 주기씩 어긋난다', body: b.join('') }),
    };
})());

/* 5. 단진자 */
add((() => {
    const W = 640, H = 340;
    const O = [190, 42], L = 205, deg = 26, rad = deg * Math.PI / 180;
    const B = [O[0] + L * Math.sin(rad), O[1] + L * Math.cos(rad)];
    const low = [O[0], O[1] + L];
    const F = 92;
    const b = [
        txt(16, 24, '진자를 되돌리는 것은 무게의 접선 성분이다', { cls: 'ink bold' }),
        hatch(O[0] - 60, O[1] - 8, O[0] + 60, O[1] - 8, 1, 10, 8),
        `<path class="gr" stroke-dasharray="5 4" d="M${O[0]} ${O[1]} V${O[1] + L + 30}"/>`,
        `<path class="gr" stroke-dasharray="4 4" d="M${r2(low[0] - L * Math.sin(rad))} ${r2(O[1] + L * Math.cos(rad))} A${L} ${L} 0 0 1 ${r2(B[0])} ${r2(B[1])}"/>`,
        `<path class="ax" d="M${O[0]} ${O[1]} L${r2(B[0])} ${r2(B[1])}"/>`,
        arc(O[0], O[1], 62, 270, 270 + deg, 'θ'),
        `<circle class="f1" cx="${r2(B[0])}" cy="${r2(B[1])}" r="14"/>`,
        txt(B[0] + 22, B[1] + 4, 'm', { cls: 'ink' }),
        // 무게와 성분
        px(B[0], B[1], B[0], B[1] + F, { cls: 's1', marker: 'ar1' }),
        txt(B[0] + 6, B[1] + F + 14, 'mg', { cls: 'ink' }),
        px(B[0], B[1], B[0] - F * Math.sin(rad) * Math.cos(rad), B[1] + F * Math.sin(rad) * Math.sin(rad),
            { cls: 's2', marker: 'ar2', width: 2 }),
        txt(B[0] - F * Math.sin(rad) * Math.cos(rad) - 8, B[1] + F * Math.sin(rad) * Math.sin(rad) + 16,
            'mg sin θ', { anchor: 'end', cls: 'ink' }),
        px(B[0], B[1], B[0] + F * Math.cos(rad) * Math.sin(rad), B[1] + F * Math.cos(rad) * Math.cos(rad),
            { cls: 's3', marker: 'ar3', width: 2 }),
        txt(B[0] + F * Math.cos(rad) * Math.sin(rad) + 8, B[1] + F * Math.cos(rad) * Math.cos(rad) + 4,
            'mg cos θ', { cls: 'ink2', size: 'sm' }),
        txt(low[0] + 4, low[1] + 46, '평형 위치', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        legend(16, 60, [{ slot: 1, name: '무게 mg' }, { slot: 2, name: '되돌리는 성분' }, { slot: 3, name: '실이 버티는 성분' }]),
    ];
    const lines = [
        '호를 따라 잰 변위는 s = Lθ',
        '되돌리는 힘 = −mg sin θ',
        '',
        '작은 각에서 sin θ ≈ θ 이므로',
        '    힘 ≈ −(mg/L) s',
        '',
        '용수철의 k 자리에 mg/L 가 들어간다.',
        'ω = √(g/L),  T = 2π√(L/g)',
        '질량 m 이 없어진다.',
    ];
    lines.forEach((s, i) => b.push(txt(392, 148 + i * 20, s, { cls: i === 4 || i === 8 ? 'ink' : 'ink2', size: i === 4 || i === 8 ? undefined : 'sm' })));
    return {
        name: 'wave-pendulum',
        title: '단진자의 복원력',
        desc: '무게를 실 방향과 호의 접선 방향으로 나누면, 되돌리는 것은 접선 성분 mg sin θ 다. '
            + '작은 각에서 sin θ 를 θ 로 놓으면 힘이 호를 따라 잰 변위에 비례하게 되어 단진동이 된다. '
            + '이때 주기는 질량과 무관하고 실의 길이로만 정해진다.',
        svg: svg({ width: W, height: H, title: '단진자', desc: '무게를 접선 성분과 실 방향 성분으로 나눈 그림', body: b.join('') }),
    };
})());

/* 6. 진동의 에너지 — 포물선 그릇 */
add((() => {
    const W = 560, H = 320;
    const g = frame({ xRange: [-1.35, 1.35], yRange: [0, 1.32], box: { x: 70, y: 40, w: 330, h: 220 } });
    const xs = 0.55;
    const b = [
        txt(16, 24, '위치에너지 그릇 안에서 오가는 에너지', { cls: 'ink bold' }),
        g.axes({ xLabel: 'x / A', yLabel: 'E', xTicks: [-1, 0, 1], yTicks: [] }),
        g.curve(x => x * x, { from: -1.14, to: 1.14, cls: 's1' }),
        g.line([[-1.32, 1], [1.32, 1]], { cls: 's2', dash: '6 4' }),
        g.label([1.32, 1], '총 에너지 E = ½kA²', { dx: -4, dy: -10, anchor: 'end', cls: 'ink' }),
        g.label([-1.2, 0.72], 'U = ½kx²', { cls: 'ink' }),
        g.guide([xs, 0], [xs, 1]),
        px(g.X(xs), g.Y(xs * xs), g.X(xs), g.Y(1), { cls: 's3', marker: 'ar3', width: 2 }),
        px(g.X(xs), g.Y(1), g.X(xs), g.Y(xs * xs), { cls: 's3', marker: 'ar3', width: 2 }),
        g.label([xs, (1 + xs * xs) / 2], 'K', { dx: 8, cls: 'ink' }),
        g.label([xs, xs * xs / 2], 'U', { dx: 8, cls: 'ink' }),
        g.dot([1, 1], { cls: 'f2' }),
        g.dot([-1, 1], { cls: 'f2' }),
        g.label([1, 1], '반환점', { dx: 6, dy: 16, cls: 'ink2', size: 'sm' }),
        g.label([-1, 1], '반환점', { dx: -6, dy: 16, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([0, 0], 'K 가 최대', { dx: 0, dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W - 14, 100, '어디서나 K + U = E', { anchor: 'end', cls: 'ink' }),
        txt(W - 14, 124, 'x = ±A 에서 K = 0 (멈춘다)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 14, 146, 'x = 0 에서 U = 0 (가장 빠르다)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 14, 168, 'U 가 x² 이므로 변위가 반이면', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 14, 186, 'U 는 4분의 1 밖에 안 된다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(16, H - 10, '가로선(총 에너지)과 포물선(위치에너지) 사이의 간격이 운동에너지다', { cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-shm-energy',
        title: '단진동의 에너지 배분',
        desc: '위치에너지는 변위의 제곱에 비례하는 포물선이고 총 에너지는 수평선이다. '
            + '둘 사이의 간격이 운동에너지이며, 포물선과 수평선이 만나는 곳이 물체가 잠시 멈추는 반환점이다.',
        svg: svg({ width: W, height: H, title: '진동의 에너지', desc: '포물선 U 와 수평선 E, 그 사이가 K', body: b.join('') }),
    };
})());

/* 7. 감쇠와 공명 */
add((() => {
    const W = 620, H = 320;
    const gL = frame({ xRange: [0, 5], yRange: [-1.15, 1.15], box: { x: 54, y: 76, w: 224, h: 170 } });
    const env = t => Math.exp(-0.42 * t);
    const gR = frame({ xRange: [0, 2.2], yRange: [0, 7.2], box: { x: 378, y: 76, w: 200, h: 170 } });
    const amp = (r, d) => 1 / Math.sqrt((1 - r * r) ** 2 + (d * r) ** 2);
    const b = [
        txt(16, 24, '실제 진동: 저절로 줄어들고, 박자를 맞추면 커진다', { cls: 'ink bold' }),
        txt(78, 52, '가만히 두면 — 감쇠', { cls: 'ink' }),
        gL.axes({ xLabel: 't/T', yLabel: 'x', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [] }),
        gL.curve(t => env(t) * Math.cos(TAU * t), { cls: 's1', steps: 260 }),
        gL.curve(env, { cls: 's2', dash: '5 4', steps: 60 }),
        gL.curve(t => -env(t), { cls: 's2', dash: '5 4', steps: 60 }),
        gL.label([2.5, 0.66], '진폭의 자취', { cls: 'ink2', size: 'sm' }),
        txt(54, H - 30, '주기는 거의 그대로인데', { cls: 'ink2', size: 'sm' }),
        txt(54, H - 12, '진폭만 준다', { cls: 'ink2', size: 'sm' }),
        txt(404, 52, '주기적으로 밀면 — 공명', { cls: 'ink' }),
        gR.axes({ xLabel: '', yLabel: '진폭', xTicks: [0, 1, 2], yTicks: [] }),
        gR.curve(r => Math.min(amp(r, 0.15), 7), { from: 0.02, to: 2.2, cls: 's1', steps: 300 }),
        gR.curve(r => Math.min(amp(r, 0.5), 7), { from: 0.02, to: 2.2, cls: 's3', steps: 200 }),
        gR.guide([1, 0], [1, 7]),
        legend(452, 108, [{ slot: 1, name: '감쇠 약함' }, { slot: 3, name: '감쇠 강함' }]),
        txt(378, H - 30, '가로축은 미는 진동수 ÷ 고유진동수', { cls: 'ink2', size: 'sm' }),
        txt(378, H - 12, '박자가 맞을 때 가장 크게 흔들린다', { cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-damped-resonance',
        title: '감쇠진동과 공명 곡선',
        desc: '왼쪽: 마찰이 있으면 진폭이 주기마다 같은 비율로 줄어들지만 주기는 거의 변하지 않는다. '
            + '오른쪽: 주기적인 힘으로 밀 때 진폭은 미는 진동수가 그 계의 고유진동수와 같을 때 가장 커진다. '
            + '감쇠가 약할수록 봉우리가 높고 좁다.',
        svg: svg({ width: W, height: H, title: '감쇠와 공명', desc: '줄어드는 진동과 공명 봉우리', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 8장 — 파동
 * ================================================================== */

/* 8. 횡파와 종파 */
add((() => {
    const W = 600, H = 330;
    const g = frame({ xRange: [0, 4], yRange: [-1.6, 1.6], box: { x: 70, y: 44, w: 420, h: 90 } });
    const b = [
        txt(16, 24, '매질이 흔들리는 방향에 따라 두 가지', { cls: 'ink bold' }),
        g.curve(x => Math.sin(TAU * x), { cls: 's1', steps: 200 }),
        g.dot([1.25, 1], { cls: 'f2', r: 5 }),
        dbl(g.X(1.25), g.Y(1) - 24, g.X(1.25), g.Y(1) + 30, { cls: 's2', marker: 'ar2', width: 1.8 }),
        txt(g.X(1.25) + 12, g.Y(1) + 30, '매질은 위아래로만', { cls: 'ink2', size: 'sm' }),
        px(g.X(3.1), g.Y(-1.35), g.X(3.9), g.Y(-1.35), { cls: 's3', marker: 'ar3', width: 2 }),
        txt(g.X(3.5), g.Y(-1.35) + 20, '파가 가는 쪽', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(70, 40, '횡파 — 줄을 흔들 때', { cls: 'ink' }),
    ];
    // 종파: 세로 막대의 간격이 촘촘해졌다 성겨졌다 한다
    const y0 = 210, y1 = 268, x0 = 70, x1 = 490, N = 58;
    b.push(txt(70, 190, '종파 — 소리처럼 앞뒤로 흔들 때', { cls: 'ink' }));
    for (let i = 0; i < N; i += 1) {
        const u = i / (N - 1);
        const x = x0 + (x1 - x0) * u + 13 * Math.sin(TAU * 2 * u);
        b.push(`<path class="gr" stroke-width="1.4" d="M${r2(x)} ${y0} V${y1}"/>`);
    }
    const dense = x0 + (x1 - x0) * 0.25, sparse = x0 + (x1 - x0) * 0.5;
    b.push(txt(dense, y0 - 8, '밀한 곳', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(sparse, y0 - 8, '성긴 곳', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(dbl(dense - 26, y1 + 16, dense + 26, y1 + 16, { cls: 's2', marker: 'ar2', width: 1.8 }));
    b.push(txt(dense, y1 + 34, '매질은 앞뒤로만', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(px(x0 + (x1 - x0) * 0.82, y1 + 22, x0 + (x1 - x0) * 0.97, y1 + 22, { cls: 's3', marker: 'ar3', width: 2 }));
    b.push(txt(x0 + (x1 - x0) * 0.9, y1 + 40, '파가 가는 쪽', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 8, '어느 쪽이든 매질은 제자리를 지킨다. 옮겨 가는 것은 모양과 에너지다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'wave-transverse-longitudinal',
        title: '횡파와 종파',
        desc: '횡파는 매질이 파의 진행 방향과 수직으로 흔들리고, 종파는 나란한 방향으로 흔들린다. '
            + '종파에서는 매질이 촘촘해진 곳과 성겨진 곳이 번갈아 나타난다. 두 경우 모두 매질 자체는 '
            + '제자리에서 진동할 뿐 함께 이동하지 않는다.',
        svg: svg({ width: W, height: H, title: '횡파와 종파', desc: '흔들리는 방향이 수직인 파와 나란한 파', body: b.join('') }),
    };
})());

/* 9. 파장과 주기 — 가로축이 다른 두 그래프 */
add((() => {
    const W = 620, H = 300;
    const mk = (bx, xLabel, tickLab, spanLab) => {
        const g = frame({ xRange: [0, 2.35], yRange: [-1.5, 1.5], box: { x: bx, y: 76, w: 220, h: 150 } });
        return [
            g.axes({ xLabel, yLabel: 'y', xTicks: [], yTicks: [] }),
            g.curve(x => Math.sin(TAU * x), { cls: 's1', steps: 200 }),
            dbl(g.X(0.25), g.Y(1.28), g.X(1.25), g.Y(1.28), { cls: 's2', marker: 'ar2', width: 1.8 }),
            txt((g.X(0.25) + g.X(1.25)) / 2, g.Y(1.28) - 8, spanLab, { anchor: 'middle', cls: 'ink' }),
            g.guide([0.25, 0], [0.25, 1.3]),
            g.guide([1.25, 0], [1.25, 1.3]),
            px(g.X(1.75), g.Y(0), g.X(1.75), g.Y(-1), { cls: 's3', marker: 'ar3', width: 1.8 }),
            txt(g.X(1.75) + 8, g.Y(-0.6), 'A (진폭)', { cls: 'ink' }),
            txt(bx, 50, tickLab, { cls: 'ink' }),
        ].join('');
    };
    const b = [
        txt(16, 24, '파장과 주기는 서로 다른 축에서 읽는다', { cls: 'ink bold' }),
        mk(60, 'x (m)', '어느 한 순간의 사진', 'λ (파장)'),
        mk(350, 't (s)', '한 점의 시간 기록', 'T (주기)'),
        txt(16, H - 28, '왼쪽에서 재는 것은 마루와 마루 사이의 거리, 오른쪽에서 재는 것은 한 점이 한 번', { cls: 'ink2', size: 'sm' }),
        txt(16, H - 10, '왕복하는 데 걸리는 시간이다.  한 주기 동안 파는 한 파장을 간다: v = λ / T = f λ', { cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-anatomy',
        title: '파장과 주기, 진폭',
        desc: '왼쪽은 어느 한 순간에 찍은 사진으로 가로축이 위치이며 마루 사이의 거리가 파장이다. '
            + '오른쪽은 한 점만 계속 지켜본 기록으로 가로축이 시간이며 한 번 왕복에 걸리는 시간이 주기다. '
            + '두 그래프는 모양이 같아 보이지만 가로축의 뜻이 다르다.',
        svg: svg({ width: W, height: H, title: '파장과 주기', desc: '위치축 그래프와 시간축 그래프의 비교', body: b.join('') }),
    };
})());

/* 10. 중첩 — 보강, 상쇄, 맥놀이 */
add((() => {
    const W = 620, H = 420;
    const panel = (top, title, parts, note, steps = 200) => {
        const g = frame({ xRange: [0, 6], yRange: [-2.3, 2.3], box: { x: 60, y: top, w: 390, h: 86 } });
        return [
            txt(16, top - 10, title, { cls: 'ink' }),
            g.axes({ xLabel: '', yLabel: '', xTicks: [], yTicks: [] }),
            ...parts.map(([f, cls, dash]) => g.curve(f, { cls, dash, steps: dash ? steps : steps * 2 })),
            txt(W - 12, top + 48, note, { anchor: 'end', cls: 'ink2', size: 'sm' }),
        ].join('');
    };
    const s = (k, p) => x => Math.sin(TAU * k * x + p);
    const sum = (f, g) => x => f(x) + g(x);
    const beatEnv = x => 2 * Math.cos(Math.PI * 0.5 * x);
    const b = [
        txt(16, 22, '두 파가 만나면 변위가 그냥 더해진다', { cls: 'ink bold' }),
        legend(470, 22, [{ slot: 1, name: '파 1' }, { slot: 3, name: '파 2' }, { slot: 2, name: '합' }]),
        panel(72, '나란할 때 — 보강', [
            [s(1, 0), 's1', '4 3'], [s(1, 0), 's3', '4 3'], [sum(s(1, 0), s(1, 0)), 's2'],
        ], '진폭이 2배'),
        panel(196, '반 파장 어긋날 때 — 상쇄', [
            [s(1, 0), 's1', '4 3'], [s(1, Math.PI), 's3', '4 3'], [sum(s(1, 0), s(1, Math.PI)), 's2'],
        ], '합이 0'),
        panel(320, '진동수가 조금 다를 때 — 맥놀이', [
            [beatEnv, 's1', '5 4'], [x => -beatEnv(x), 's1', '5 4'], [sum(s(2.5, 0), s(3, 0)), 's2'],
        ], '커졌다 작아졌다', 300),
        txt(16, H - 8, '맥놀이가 한 번 크게 울렸다 잦아드는 횟수가 초당 |f₁ − f₂| 번이다', { cls: 'ink2', size: 'sm' }),
        txt(W - 12, H - 8, '맥놀이 그림의 점선은 진폭의 자취', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-superposition',
        title: '중첩 — 보강, 상쇄, 맥놀이',
        desc: '같은 자리에 있는 두 파의 변위는 단순히 더해진다. 마루끼리 겹치면 진폭이 두 배가 되고, '
            + '마루와 골이 겹치면 서로 지운다. 진동수가 조금 다른 두 파를 겹치면 합의 진폭이 '
            + '느리게 커졌다 작아졌다 하는 맥놀이가 생긴다.',
        svg: svg({ width: W, height: H, title: '파의 중첩', desc: '보강, 상쇄, 맥놀이 세 경우', body: b.join('') }),
    };
})());

/* 11. 양 끝이 고정된 줄의 정상파 */
add((() => {
    const W = 580, H = 340;
    const x0 = 80, x1 = 420, A = 34;
    const rows = [
        { y: 80, n: 1, lab: 'n = 1 (기본진동)', info: 'λ = 2L,  f~1 = v / 2L' },
        { y: 180, n: 2, lab: 'n = 2 (2배음)', info: 'λ = L,  f = 2f~1' },
        { y: 280, n: 3, lab: 'n = 3 (3배음)', info: 'λ = 2L/3,  f = 3f~1' },
    ];
    const b = [txt(16, 24, '양 끝이 묶인 줄에서는 정해진 모양만 살아남는다', { cls: 'ink bold' })];
    for (const r of rows) {
        const pts = [];
        const N = 60 * r.n;
        for (let i = 0; i <= N; i += 1) {
            const u = i / N;
            pts.push(`${r2(x0 + (x1 - x0) * u)} ${r2(r.y - A * Math.sin(Math.PI * r.n * u))}`);
        }
        const pts2 = pts.map(p => {
            const [px1, py] = p.split(' ').map(Number);
            return `${px1} ${r2(2 * r.y - py)}`;
        });
        b.push(`<path class="cv s1" d="M${pts.join(' L')}"/>`);
        b.push(`<path class="cv s1" stroke-dasharray="5 4" d="M${pts2.join(' L')}"/>`);
        b.push(`<path class="gr" d="M${x0} ${r.y} H${x1}"/>`);
        b.push(hatch(x0, r.y - 42, x0, r.y + 42, -1, 11, 7));
        b.push(hatch(x1, r.y - 42, x1, r.y + 42, 1, 11, 7));
        for (let k = 0; k <= r.n; k += 1) {
            b.push(`<circle class="f2" cx="${r2(x0 + (x1 - x0) * k / r.n)}" cy="${r.y}" r="4"/>`);
        }
        for (let k = 0; k < r.n; k += 1) {
            b.push(txt(x0 + (x1 - x0) * (k + 0.5) / r.n, r.y - A - 8, '배', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        if (r.n === 1) b.push(txt(x0 + 8, r.y + 22, '마디', { cls: 'ink2', size: 'sm' }));
        b.push(txt(W - 14, r.y - 8, r.lab, { anchor: 'end', cls: 'ink' }));
        b.push(txt(W - 14, r.y + 12, r.info, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(16, H - 10, '마디는 늘 정지해 있고 배는 가장 크게 흔들린다. 줄 길이 L 에 반파장이 정수 개 들어가야 한다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'wave-standing-modes',
        title: '양 끝이 고정된 줄의 정상파',
        desc: '양 끝이 반드시 마디여야 하므로 줄 길이에 반파장이 정수 개 들어가는 파만 남는다. '
            + '기본진동의 파장은 줄 길이의 두 배이고, 그 위의 배음은 기본진동수의 정수배가 된다.',
        svg: svg({ width: W, height: H, title: '줄의 정상파', desc: 'n = 1, 2, 3 모드와 마디·배', body: b.join('') }),
    };
})());

/* 12. 관 안의 정상파 */
add((() => {
    const W = 600, H = 340;
    const A = 17, len = 200;
    const pipe = (px0, py, closedLeft, shape) => {
        const out = [];
        const h = 46;
        out.push(`<rect x="${px0}" y="${py - h / 2}" width="${len}" height="${h}" rx="2" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
        if (closedLeft) out.push(hatch(px0, py - h / 2, px0, py + h / 2, 1, 9, 8));
        const pts = [], pts2 = [];
        for (let i = 0; i <= 90; i += 1) {
            const u = i / 90;
            const y = py - A * shape(u);
            pts.push(`${r2(px0 + len * u)} ${r2(y)}`);
            pts2.push(`${r2(px0 + len * u)} ${r2(2 * py - y)}`);
        }
        out.push(`<path class="cv s1" d="M${pts.join(' L')}"/>`);
        out.push(`<path class="cv s1" stroke-dasharray="5 4" d="M${pts2.join(' L')}"/>`);
        return out.join('');
    };
    const b = [
        txt(16, 24, '관 안의 공기도 정해진 모양으로만 흔들린다', { cls: 'ink bold' }),
        txt(40, 58, '양쪽 열린 관 — 두 끝이 모두 배', { cls: 'ink' }),
        pipe(40, 100, false, u => Math.cos(Math.PI * u)),
        txt(140, 142, 'n = 1,  f~1 = v / 2L', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        pipe(40, 196, false, u => Math.cos(TAU * u)),
        txt(140, 238, 'n = 2,  f = 2f~1', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(140, 268, '모든 정수배가 다 나온다', { anchor: 'middle', cls: 'ink' }),
        txt(340, 58, '한쪽 막힌 관 — 막힌 끝은 마디', { cls: 'ink' }),
        pipe(345, 100, true, u => Math.sin(Math.PI * u / 2)),
        txt(445, 142, 'n = 1,  f~1 = v / 4L', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        pipe(345, 196, true, u => Math.sin(3 * Math.PI * u / 2)),
        txt(445, 238, 'n = 3,  f = 3f~1  (2배음은 없다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(445, 268, '홀수배만 나온다', { anchor: 'middle', cls: 'ink' }),
        txt(16, H - 10, '곡선은 공기가 앞뒤로 얼마나 크게 움직이는지를 나타낸다. 막힌 끝에서는 움직일 수 없어 0 이다',
            { cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'wave-pipe-modes',
        title: '관 안의 정상파',
        desc: '열린 끝에서는 공기가 자유롭게 움직일 수 있어 배가 되고, 막힌 끝에서는 움직일 수 없어 마디가 된다. '
            + '그래서 양쪽이 열린 관은 기본진동수의 모든 정수배를 내지만 한쪽이 막힌 관은 홀수배만 낸다.',
        svg: svg({ width: W, height: H, title: '관의 정상파', desc: '열린 관과 막힌 관의 진동 모양', body: b.join('') }),
    };
})());

/* 13. 도플러 효과 */
add((() => {
    const W = 620, H = 380;
    const cy = 196, xs = 330, v = 55, vs = 32;
    const b = [txt(16, 24, '파원이 움직이면 앞쪽 파면이 촘촘해진다', { cls: 'ink bold' })];
    b.push(`<path class="gr" stroke-dasharray="5 4" d="M40 ${cy} H580"/>`);
    for (let k = 3; k >= 1; k -= 1) {
        const c = xs - vs * k, r = v * k;
        b.push(`<circle cx="${r2(c)}" cy="${cy}" r="${r2(r)}" fill="none" stroke="var(--s1)" stroke-width="1.5" stroke-opacity="0.85"/>`);
        b.push(`<circle class="f1" cx="${r2(c)}" cy="${cy}" r="2.5"/>`);
    }
    b.push(`<circle class="f2" cx="${xs}" cy="${cy}" r="7"/>`);
    b.push(px(xs, cy, xs + 52, cy, { cls: 's2', marker: 'ar2', width: 2.4 }));
    b.push(txt(xs + 10, cy - 18, '파원  v~s', { cls: 'ink' }));
    // 앞쪽·뒤쪽 파장 표시
    const front = [xs + 30, xs + 60, xs + 90];
    b.push(dbl(front[0], cy + 26, front[1], cy + 26, { cls: 's3', marker: 'ar3', width: 1.6 }));
    b.push(txt(front[2] + 24, cy + 30, '짧아진 파장', { cls: 'ink', anchor: 'start' }));
    const back = [xs - 110, xs - 220];
    b.push(dbl(back[0], cy + 26, back[1], cy + 26, { cls: 's3', marker: 'ar3', width: 1.6 }));
    b.push(txt((back[0] + back[1]) / 2, cy + 46, '길어진 파장', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(552, cy - 14, '앞쪽 관측자', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(552, cy + 4, '높은 소리', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(46, cy - 14, '뒤쪽 관측자', { cls: 'ink2', size: 'sm' }));
    b.push(txt(46, cy + 4, '낮은 소리', { cls: 'ink2', size: 'sm' }));
    b.push(`<circle class="f3" cx="566" cy="${cy}" r="5"/>`);
    b.push(`<circle class="f3" cx="40" cy="${cy}" r="5"/>`);
    b.push(txt(16, H - 28, '점은 그 파면이 만들어질 때의 파원 위치다. 파면 자체는 매질을 기준으로 사방으로 같은 속력으로 퍼진다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, '파원이 뒤따라오므로 앞쪽에서는 간격이 좁아지고 뒤쪽에서는 넓어진다. 속력 v 는 그대로다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'wave-doppler',
        title: '움직이는 파원의 파면',
        desc: '파면은 만들어진 자리를 중심으로 매질 속에서 같은 속력으로 퍼진다. 파원이 움직이면 '
            + '다음 파면이 앞쪽으로 조금 옮겨진 자리에서 만들어지므로 앞쪽 간격이 좁아지고 뒤쪽 간격이 넓어진다. '
            + '앞쪽 관측자는 파장이 짧은, 즉 진동수가 높은 소리를 듣는다.',
        svg: svg({ width: W, height: H, title: '도플러 효과', desc: '움직이는 파원이 만드는 촘촘한 앞쪽 파면', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 9장 — 유체
 * ================================================================== */

/* 14. 압력 = 힘 / 넓이 */
add((() => {
    const W = 580, H = 290;
    const gy = 224;
    const b = [
        txt(16, 24, '같은 힘이라도 닿는 넓이가 다르면 결과가 다르다', { cls: 'ink bold' }),
        hatch(30, gy, 270, gy, 1, 12, 9),
        hatch(320, gy, 560, gy, 1, 12, 9),
    ];
    // 왼쪽: 넓은 밑면
    b.push(`<rect x="90" y="${gy - 54}" width="120" height="54" rx="3" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
    b.push(px(150, 74, 150, gy - 62, { cls: 's1', marker: 'ar1', width: 3 }));
    b.push(txt(158, 100, 'F', { cls: 'ink' }));
    b.push(txt(150, 56, '같은 힘', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(dbl(90, gy + 22, 210, gy + 22, { cls: 's2', marker: 'ar2', width: 1.6 }));
    b.push(txt(150, gy + 42, '넓은 면적 A', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(150, gy + 60, '압력이 작다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 오른쪽: 뾰족한 밑면
    b.push(`<path d="M410 ${gy - 54} L470 ${gy - 54} L444 ${gy - 8} L436 ${gy - 8} Z" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
    b.push(`<path d="M436 ${gy - 8} L444 ${gy - 8} L440 ${gy + 8} Z" fill="var(--s2)" fill-opacity="0.7" stroke="var(--s2)" stroke-width="1.2"/>`);
    b.push(px(440, 74, 440, gy - 62, { cls: 's1', marker: 'ar1', width: 3 }));
    b.push(txt(448, 100, 'F', { cls: 'ink' }));
    b.push(txt(440, 56, '같은 힘', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(dbl(430, gy + 22, 450, gy + 22, { cls: 's2', marker: 'ar2', width: 1.6 }));
    b.push(txt(440, gy + 42, '좁은 면적 A', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(440, gy + 60, '압력이 크다 — 파고든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(295, 140, 'P = F / A', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(295, 162, '단위 Pa', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(295, 178, '= N/m²', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-pressure-area',
        title: '압력은 힘을 넓이로 나눈 값',
        desc: '같은 크기의 힘이라도 닿는 넓이가 좁으면 압력이 커진다. 그래서 뾰족한 것은 잘 파고들고 '
            + '넓은 판은 그렇지 않다. 압력의 단위 파스칼은 제곱미터당 뉴턴이다.',
        svg: svg({ width: W, height: H, title: '압력의 정의', desc: '같은 힘을 넓은 면과 좁은 면에 걸었을 때', body: b.join('') }),
    };
})());

/* 15. 깊이에 따른 압력, 그리고 그릇 모양은 상관없다 */
add((() => {
    const W = 620, H = 350;
    const b = [txt(16, 24, '압력을 정하는 것은 깊이뿐이다', { cls: 'ink bold' })];
    // 왼쪽: 수조와 깊이별 화살표
    const tx = 46, ty = 74, tw = 210, th = 190;
    b.push(fill(`M${tx} ${ty} H${tx + tw} V${ty + th} H${tx} Z`));
    b.push(`<path class="ax" d="M${tx} ${ty} V${ty + th} H${tx + tw} V${ty}"/>`);
    b.push(`<path class="s1" fill="none" stroke-width="2" d="M${tx} ${ty} H${tx + tw}"/>`);
    b.push(txt(tx + tw + 6, ty + 4, '수면', { cls: 'ink2', size: 'sm' }));
    const depths = [0.3, 0.6, 0.9];
    depths.forEach((d, i) => {
        const y = ty + th * d;
        const L = 20 + 44 * d;
        b.push(`<path class="gr" stroke-dasharray="3 3" d="M${tx + 16} ${y} H${tx + tw}"/>`);
        b.push(px(tx + tw - 6 - L, y, tx + tw - 4, y, { cls: 's2', marker: 'ar2', width: 2 }));
        b.push(txt(tx + 62, y - 6, `h~${i + 1}`, { cls: 'ink2', size: 'sm' }));
    });
    b.push(`<rect x="${tx + 16}" y="${ty}" width="34" height="${th * 0.9}" fill="var(--s3)" fill-opacity="0.25" stroke="var(--s3)" stroke-width="1.2" stroke-dasharray="4 3"/>`);
    b.push(txt(tx + 33, ty + th + 20, '이 기둥의', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(tx + 33, ty + th + 36, '무게가 누른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(tx + 96, ty + th + 30, 'P = P~0 + ρgh', { cls: 'ink' }));
    b.push(txt(tx + 96, ty + th + 50, '깊을수록 벽을 세게 민다', { cls: 'ink2', size: 'sm' }));
    // 오른쪽: 모양이 다른 세 그릇, 같은 수면 높이
    const bx = 330, by = 96, bh = 140, base = by + bh;
    const shapes = [
        `M${bx} ${by} V${base} H${bx + 46} V${by} Z`,
        `M${bx + 70} ${by} L${bx + 146} ${by} L${bx + 124} ${base} L${bx + 92} ${base} Z`,
        `M${bx + 182} ${by} L${bx + 208} ${by} L${bx + 226} ${base} L${bx + 164} ${base} Z`,
    ];
    for (const s of shapes) {
        b.push(fill(s));
        b.push(`<path class="ax" d="${s}" fill="none"/>`);
    }
    b.push(`<path class="gr" stroke-dasharray="4 3" d="M${bx - 16} ${by} H${bx + 236}"/>`);
    b.push(txt(bx - 20, by + 4, '같은 높이', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(hatch(bx - 10, base, bx + 240, base, -1, 12, 8));
    for (const x of [bx + 23, bx + 108, bx + 195]) {
        b.push(px(x, base - 34, x, base - 8, { cls: 's2', marker: 'ar2', width: 2 }));
    }
    b.push(txt(bx + 110, base + 42, '바닥 압력이 셋 다 같다', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(bx + 110, base + 62, '담긴 물의 양과는 무관하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, '깊이가 두 배면 압력의 증가분도 두 배다. 그릇이 넓든 좁든 달라지지 않는다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-depth-pressure',
        title: '깊이에 비례하는 압력',
        desc: '어떤 깊이에서의 압력은 그 위에 얹힌 유체 기둥의 무게를 넓이로 나눈 값이라 깊이에 비례한다. '
            + '그릇의 모양이나 담긴 물의 총량은 압력에 영향을 주지 않는다. 수면에서 같은 깊이면 압력도 같다.',
        svg: svg({ width: W, height: H, title: '깊이와 압력', desc: '깊을수록 큰 압력, 그릇 모양과는 무관', body: b.join('') }),
    };
})());

/* 16. 유압 장치 */
add((() => {
    const W = 580, H = 350;
    const b = [txt(16, 24, '작은 힘으로 큰 힘을 얻는 대신 많이 눌러야 한다', { cls: 'ink bold' })];
    const yTop = 168, yBot = 288;
    const lx = 90, lw = 44, rx = 330, rw = 130;
    const body = `M${lx} ${yTop} V${yBot} H${rx + rw} V${yTop - 44} H${rx} V${yBot - 18} H${lx + lw} V${yTop} Z`;
    b.push(fill(body));
    b.push(`<path class="ax" d="${body}" fill="none"/>`);
    // 작은 피스톤
    b.push(`<rect x="${lx}" y="${yTop - 14}" width="${lw}" height="14" fill="var(--ink2)" fill-opacity="0.5" stroke="var(--ink2)" stroke-width="1.4"/>`);
    b.push(px(lx + lw / 2, yTop - 96, lx + lw / 2, yTop - 22, { cls: 's1', marker: 'ar1', width: 3 }));
    b.push(txt(lx + lw / 2 + 8, yTop - 60, 'F~1 (작다)', { cls: 'ink' }));
    b.push(dbl(lx - 12, yTop, lx - 12, yTop - 78, { cls: 's3', marker: 'ar3', width: 1.6 }));
    b.push(txt(lx - 18, yTop - 40, 'd~1 (많이)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(lx + lw / 2, yBot + 24, '넓이 A~1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 큰 피스톤
    b.push(`<rect x="${rx}" y="${yTop - 58}" width="${rw}" height="14" fill="var(--ink2)" fill-opacity="0.5" stroke="var(--ink2)" stroke-width="1.4"/>`);
    b.push(px(rx + rw / 2, yTop - 66, rx + rw / 2, yTop - 132, { cls: 's2', marker: 'ar2', width: 4 }));
    b.push(txt(rx + rw / 2 + 10, yTop - 110, 'F~2 (크다)', { cls: 'ink' }));
    b.push(dbl(rx + rw + 14, yTop - 58, rx + rw + 14, yTop - 74, { cls: 's3', marker: 'ar3', width: 1.6 }));
    b.push(txt(rx + rw + 22, yTop - 62, 'd~2 (조금)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(rx + rw / 2, yBot + 24, '넓이 A~2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(232, 132, '갇힌 유체는 압력을 그대로 전한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, 186, 'F~1 / A~1 = F~2 / A~2', { anchor: 'end', cls: 'ink' }));
    b.push(txt(W - 14, 210, 'A~1 d~1 = A~2 d~2', { anchor: 'end', cls: 'ink' }));
    b.push(txt(W - 14, 232, '두 식을 곱하면', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 14, 252, 'F~1 d~1 = F~2 d~2', { anchor: 'end', cls: 'ink' }));
    b.push(txt(16, H - 10, '힘은 넓이 비만큼 커지지만 움직이는 거리는 그만큼 줄어, 한 일은 양쪽이 같다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-hydraulic-press',
        title: '유압 장치',
        desc: '갇힌 유체는 한쪽에 가한 압력을 모든 곳에 그대로 전한다. 넓은 피스톤에서는 같은 압력이 '
            + '넓은 넓이에 걸리므로 힘이 커진다. 그러나 유체의 부피가 보존되므로 넓은 쪽은 조금밖에 '
            + '움직이지 못하고, 양쪽이 한 일은 같다.',
        svg: svg({ width: W, height: H, title: '유압 장치', desc: '작은 피스톤과 큰 피스톤, 힘과 거리의 맞바꿈', body: b.join('') }),
    };
})());

/* 17. 부력 */
add((() => {
    const W = 620, H = 350;
    const b = [txt(16, 24, '부력은 위아래 압력의 차이다', { cls: 'ink bold' })];
    // 왼쪽: 잠긴 상자
    const wx = 40, wy = 64, ww = 250, wh = 194;
    b.push(fill(`M${wx} ${wy} H${wx + ww} V${wy + wh} H${wx} Z`));
    b.push(`<path class="s1" fill="none" stroke-width="2" d="M${wx} ${wy} H${wx + ww}"/>`);
    b.push(`<path class="ax" d="M${wx} ${wy} V${wy + wh} H${wx + ww} V${wy}"/>`);
    const bx = wx + 78, by = wy + 58, bw = 92, bh = 66;
    b.push(`<rect x="${bx}" y="${by}" width="${bw}" height="${bh}" rx="3" fill="none" stroke="var(--ink)" stroke-width="1.8"/>`);
    for (const f of [0.3, 0.7]) {
        b.push(px(bx + bw * f, by - 34, bx + bw * f, by - 6, { cls: 's2', marker: 'ar2', width: 2 }));
        b.push(px(bx + bw * f, by + bh + 58, bx + bw * f, by + bh + 6, { cls: 's3', marker: 'ar3', width: 2 }));
    }
    b.push(txt(bx + bw + 8, by - 22, '위에서 누르는 힘 (작다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(bx + bw + 8, by + bh + 40, '아래에서 미는 힘 (크다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(bx + bw / 2, by + bh / 2 + 5, 'V', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(wx, wy + wh + 26, '차이가 곧 부력이다', { cls: 'ink' }));
    b.push(txt(wx, wy + wh + 48, 'F~B = ρ~유체 g V~{잠긴부피}', { cls: 'ink' }));
    // 오른쪽: 떠 있는 물체
    const ox = 340, oy = 128, ow = 240, oh = 130;
    b.push(fill(`M${ox} ${oy} H${ox + ow} V${oy + oh} H${ox} Z`));
    b.push(`<path class="s1" fill="none" stroke-width="2" d="M${ox} ${oy} H${ox + ow}"/>`);
    b.push(`<path class="ax" d="M${ox} ${oy} V${oy + oh} H${ox + ow} V${oy}"/>`);
    const fx = ox + 66, fw = 108, ftop = oy - 34, fh = 96, sub = fh - 34;
    b.push(`<rect x="${fx}" y="${oy}" width="${fw}" height="${sub}" fill="var(--s2)" fill-opacity="0.28" stroke="none"/>`);
    b.push(`<rect x="${fx}" y="${ftop}" width="${fw}" height="${fh}" rx="3" fill="none" stroke="var(--ink)" stroke-width="1.8"/>`);
    b.push(`<path class="gr" stroke-dasharray="4 3" d="M${fx} ${oy} H${fx + fw}"/>`);
    b.push(dbl(fx - 16, oy, fx - 16, oy + sub, { cls: 's2', marker: 'ar2', width: 1.6 }));
    b.push(txt(fx - 22, oy + sub / 2 + 4, '잠긴 부피', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(px(fx + fw / 2 + 22, oy + sub, fx + fw / 2 + 22, ftop - 26, { cls: 's3', marker: 'ar3', width: 2.4 }));
    b.push(txt(fx + fw / 2 + 30, ftop - 16, 'F~B', { cls: 'ink' }));
    b.push(px(fx + fw / 2 - 26, ftop + 18, fx + fw / 2 - 26, oy + sub + 26, { cls: 's1', marker: 'ar1', width: 2.4 }));
    b.push(txt(fx + fw / 2 - 34, oy + sub + 22, 'mg', { anchor: 'end', cls: 'ink' }));
    b.push(txt(ox, oy + oh + 26, '떠 있으면 F~B = mg 이므로', { cls: 'ink' }));
    b.push(txt(ox, oy + oh + 48, 'V~{잠긴} / V~{전체} = ρ~물체 / ρ~유체', { cls: 'ink' }));
    b.push(txt(16, H - 8, '부력에 들어가는 것은 유체의 밀도와 잠긴 부피뿐이다. 물체가 무엇으로 되어 있는지는 들어가지 않는다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-buoyancy',
        title: '부력과 떠 있는 물체',
        desc: '잠긴 물체의 아랫면은 윗면보다 깊어서 더 큰 압력을 받는다. 그 차이가 위로 미는 힘, 곧 부력이며 '
            + '크기는 밀려난 유체의 무게와 같다. 떠 있는 물체는 부력과 무게가 같아지는 만큼만 잠기므로 '
            + '잠긴 부피의 비율이 두 밀도의 비가 된다.',
        svg: svg({ width: W, height: H, title: '부력', desc: '압력 차로 생기는 부력과 떠 있는 물체의 잠긴 부피', body: b.join('') }),
    };
})());

/* 18. 연속방정식 */
add((() => {
    const W = 580, H = 280;
    const cy = 150, x0 = 60, x1 = 520;
    const R = x => {
        const u = (x - x0) / (x1 - x0);
        if (u < 0.36) return 56;
        if (u > 0.62) return 24;
        return 56 - 32 * (1 - Math.cos(Math.PI * (u - 0.36) / 0.26)) / 2;
    };
    const top = [], bot = [];
    for (let i = 0; i <= 120; i += 1) {
        const x = x0 + (x1 - x0) * i / 120;
        top.push(`${r2(x)} ${r2(cy - R(x))}`);
        bot.push(`${r2(x)} ${r2(cy + R(x))}`);
    }
    const b = [
        txt(16, 24, '좁아지면 빨라진다 — 같은 시간에 같은 부피가 지나가야 하므로', { cls: 'ink bold' }),
        fill(`M${top.join(' L')} L${bot.slice().reverse().join(' L')} Z`),
        `<path class="ax" d="M${top.join(' L')}"/>`,
        `<path class="ax" d="M${bot.join(' L')}"/>`,
    ];
    for (const [x, len] of [[110, 26], [160, 26], [430, 62], [340, 62]]) {
        for (const dy of [-22, 0, 22]) {
            if (Math.abs(dy) > R(x) - 12) continue;
            b.push(px(x, cy + dy, x + len, cy + dy, { cls: 's2', marker: 'ar2', width: 2 }));
        }
    }
    b.push(dbl(88, cy - 56, 88, cy + 56, { cls: 's1', marker: 'ar1', width: 1.6 }));
    b.push(txt(80, cy + 4, 'A~1', { anchor: 'end', cls: 'ink' }));
    b.push(dbl(494, cy - 24, 494, cy + 24, { cls: 's1', marker: 'ar1', width: 1.6 }));
    b.push(txt(502, cy + 4, 'A~2', { cls: 'ink' }));
    b.push(txt(140, cy - 76, '느리다  v~1', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(410, cy - 52, '빠르다  v~2', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(W / 2, H - 34, 'A~1 v~1 = A~2 v~2 = Q  (부피유량, m³/s)', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, H - 12, '단면적이 4분의 1 이 되면 속력은 4배가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-continuity',
        title: '연속방정식',
        desc: '관의 어느 단면에서든 같은 시간에 같은 부피가 지나가야 하므로, 단면적과 속력의 곱이 일정하다. '
            + '관이 좁아지면 그만큼 흐름이 빨라진다.',
        svg: svg({ width: W, height: H, title: '연속방정식', desc: '좁아지는 관에서 빨라지는 흐름', body: b.join('') }),
    };
})());

/* 19. 베르누이 — 벤투리 관 */
add((() => {
    const W = 620, H = 360;
    const cy = 244, x0 = 50, x1 = 570;
    const R = x => {
        const u = (x - x0) / (x1 - x0);
        const wide = 34, narrow = 14;
        if (u < 0.3) return wide;
        if (u > 0.7) return wide;
        if (u < 0.42) return wide - (wide - narrow) * (1 - Math.cos(Math.PI * (u - 0.3) / 0.12)) / 2;
        if (u > 0.58) return narrow + (wide - narrow) * (1 - Math.cos(Math.PI * (u - 0.58) / 0.12)) / 2;
        return narrow;
    };
    const top = [], bot = [];
    for (let i = 0; i <= 160; i += 1) {
        const x = x0 + (x1 - x0) * i / 160;
        top.push(`${r2(x)} ${r2(cy - R(x))}`);
        bot.push(`${r2(x)} ${r2(cy + R(x))}`);
    }
    const b = [
        txt(16, 24, '빠른 곳은 압력이 낮다', { cls: 'ink bold' }),
        fill(`M${top.join(' L')} L${bot.slice().reverse().join(' L')} Z`),
    ];
    // 압력계 세 개 — 관 위쪽 같은 높이에서 시작해 물기둥 높이를 견줄 수 있게 한다
    const tubeBase = cy - 40, tubeH = 130;
    const tubes = [[150, 86], [310, 40], [470, 82]];
    for (const [x, h] of tubes) {
        b.push(`<path class="gr" d="M${x - 8} ${tubeBase} V${cy - R(x)}"/><path class="gr" d="M${x + 8} ${tubeBase} V${cy - R(x)}"/>`);
        b.push(`<rect x="${x - 9}" y="${tubeBase - tubeH}" width="18" height="${tubeH}" fill="none" stroke="var(--ink2)" stroke-width="1.3"/>`);
        b.push(`<rect x="${x - 8}" y="${tubeBase - h}" width="16" height="${h}" fill="var(--s1)" fill-opacity="0.35" stroke="none"/>`);
        b.push(`<path class="s1" fill="none" stroke-width="1.6" d="M${x - 8} ${tubeBase - h} H${x + 8}"/>`);
    }
    b.push(`<path class="ax" d="M${top.join(' L')}"/>`);
    b.push(`<path class="ax" d="M${bot.join(' L')}"/>`);
    for (const [x, len] of [[110, 22], [292, 54], [470, 22]]) {
        b.push(px(x, cy, x + len, cy, { cls: 's2', marker: 'ar2', width: 2 }));
    }
    b.push(txt(150, cy + 54, '넓다 · 느리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(310, cy + 54, '좁다 · 빠르다', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(470, cy + 54, '넓다 · 느리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(150, tubeBase - tubeH - 8, '높다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(310, tubeBase - tubeH - 8, '낮다', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(470, tubeBase - tubeH - 8, '높다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 46, '물기둥의 높이가', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 62, '그 자리의 압력이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 32, 'P + ½ρv² + ρgy 가 유선을 따라 일정', { cls: 'ink bold' }));
    b.push(txt(16, H - 12, '속력이 커진 만큼 압력이 내려간다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'fluid-bernoulli',
        title: '벤투리 관 — 빠른 곳의 압력이 낮다',
        desc: '관이 좁아진 곳에서는 흐름이 빨라지고, 그만큼 압력이 낮아진다. 위로 세운 관 속 물기둥의 '
            + '높이가 그 자리의 압력을 보여 준다. 압력과 운동에너지 밀도, 위치에너지 밀도의 합이 일정하다는 '
            + '것이 베르누이 식이다.',
        svg: svg({ width: W, height: H, title: '벤투리 관', desc: '좁은 곳에서 낮아지는 압력', body: b.join('') }),
    };
})());

export default figures;
