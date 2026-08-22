/**
 * 선형대수 6장(일반 벡터공간) · 7장(선형변환) · 8장(고유값과 고유벡터)의 그림.
 *
 * 이름은 모두 `la-sp-` 로 시작한다(담당 B 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~1` 표기를, 나머지는 유니코드(− × · ‖ θ λ ₁₂₃)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 그림들은 하나의 질문에 답하려고 있다. 벡터공간·선형변환·고유값은
 * 계산 규칙이 아니라 공간에 일어나는 일이고, 그 일은 격자를 그려 보면 보인다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/** 음수 부호를 하이픈이 아니라 진짜 빼기 기호로 적는다. */
const nm = v => String(v).replace('-', '−');

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구
 * ------------------------------------------------------------------ */

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어
 * 선이 사라지고 화살촉만 남는다. 색을 직접 넣는 화살표를 따로 둔다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.9, dash } = {}) {
    const col = { s1: C1, s2: C2, s3: C3, ark: CK }[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.4, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = C1, op = 0.18, stroke = 'none', sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = CK, sw = 1.4, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 격자가 상자 밖으로 넘치는 것을 막는다. 변환한 격자는 반드시 넘친다. */
let uid = 0;
function clip(x, y, w, h, inner) {
    const id = `lasp${uid += 1}`;
    return `<clipPath id="${id}"><rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}"/></clipPath>`
        + `<g clip-path="url(#${id})">${inner}</g>`;
}

/** 꼭짓점 v 에서 방향 d1 과 d2 사이의 직각 표시. 화소 좌표. */
function rightAngle(v, d1, d2, s = 10) {
    const u = a => { const L = Math.hypot(a[0], a[1]) || 1; return [a[0] / L * s, a[1] / L * s]; };
    const p = u(d1); const q = u(d2);
    return ln([[v[0] + p[0], v[1] + p[1]], [v[0] + p[0] + q[0], v[1] + p[1] + q[1]], [v[0] + q[0], v[1] + q[1]]],
        { stroke: CK, sw: 1.2 });
}

/** 꼭짓점 v 에서 p1 방향과 p2 방향 사이의 각을 호로 그린다. 화소 좌표. */
function angleArc(v, p1, p2, r, label, { stroke = CK, sw = 1.3, gap = 14 } = {}) {
    const a1 = Math.atan2(p1[1] - v[1], p1[0] - v[0]);
    const a2 = Math.atan2(p2[1] - v[1], p2[0] - v[0]);
    let da = a2 - a1;
    while (da <= -Math.PI) da += 2 * Math.PI;
    while (da > Math.PI) da -= 2 * Math.PI;
    const sweep = da > 0 ? 1 : 0;
    const q1 = [v[0] + r * Math.cos(a1), v[1] + r * Math.sin(a1)];
    const q2 = [v[0] + r * Math.cos(a2), v[1] + r * Math.sin(a2)];
    const am = a1 + da / 2;
    const lp = [v[0] + (r + gap) * Math.cos(am), v[1] + (r + gap) * Math.sin(am) + 4];
    return `<path d="M${r2(q1[0])} ${r2(q1[1])} A${r2(r)} ${r2(r)} 0 0 ${sweep} ${r2(q2[0])} ${r2(q2[1])}" fill="none" stroke="${stroke}" stroke-width="${sw}"/>`
        + (label ? txt(lp[0], lp[1], label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/* ------------------------------------------------------------------ *
 * 데이터 좌표 소도구
 * ------------------------------------------------------------------ */

/** 가로세로 배율을 같게 맞춘 frame. 배율이 다르면 직각이 직각으로 보이지 않는다. */
function sq(xR, yR, x, y, s) {
    const w = (xR[1] - xR[0]) * s;
    const h = (yR[1] - yR[0]) * s;
    return { f: frame({ xRange: xR, yRange: yR, box: { x, y, w, h } }), w, h, x, y };
}

const varw = (f, p1, p2, o) => arw(f.X(p1[0]), f.Y(p1[1]), f.X(p2[0]), f.Y(p2[1]), o);
const dpoly = (f, pts, o) => poly(pts.map(p => [f.X(p[0]), f.Y(p[1])]), o);
const dln = (f, pts, o) => ln(pts.map(p => [f.X(p[0]), f.Y(p[1])]), o);
const ddot = (f, p, col, r) => pdot(f.X(p[0]), f.Y(p[1]), col, r);
const dtxt = (f, p, s, o = {}) => txt(f.X(p[0]) + (o.dx ?? 0), f.Y(p[1]) + (o.dy ?? 0), s, o);
const S = (f, p) => [f.X(p[0]), f.Y(p[1])];

/** 상자 안의 축. lib 의 axes 는 눈금 글자가 커서 작은 패널에서 겹친다. */
function axes(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel } = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = f.Y(0);
    const ay = f.X(0);
    const g = [arw(f.X(x0), ax, f.X(x1) + 9, ax, { cls: 'ark', width: 1.2 }),
        arw(ay, f.Y(y0), ay, f.Y(y1) - 9, { cls: 'ark', width: 1.2 })];
    for (const t of xTicks) {
        if (t === 0) continue;
        g.push(ln([[f.X(t), ax - 3], [f.X(t), ax + 3]], { sw: 1 }));
        g.push(txt(f.X(t), ax + 15, nm(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        if (t === 0) continue;
        g.push(ln([[ay - 3, f.Y(t)], [ay + 3, f.Y(t)]], { sw: 1 }));
        g.push(txt(ay - 7, f.Y(t) + 4, nm(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 13, ax + 4, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay + 8, f.Y(y1) + 2, yLabel, { cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 2×2 행렬을 벡터에 적용한다. M = [[a, b], [c, d]]. */
const mv = (M, p) => [M[0][0] * p[0] + M[0][1] * p[1], M[1][0] * p[0] + M[1][1] * p[1]];
const mm = (A, B) => [[A[0][0] * B[0][0] + A[0][1] * B[1][0], A[0][0] * B[0][1] + A[0][1] * B[1][1]],
    [A[1][0] * B[0][0] + A[1][1] * B[1][0], A[1][0] * B[0][1] + A[1][1] * B[1][1]]];
const rot = deg => {
    const t = (deg * Math.PI) / 180;
    return [[Math.cos(t), -Math.sin(t)], [Math.sin(t), Math.cos(t)]];
};

/**
 * 정수 격자를 M 으로 옮긴 격자. 선형변환이 공간에 하는 일을 보이는 핵심 도구다.
 * 넘치는 부분은 부르는 쪽에서 clip 으로 잘라야 한다.
 */
function lattice(f, M, { nx = 6, ny = 6, stroke = CG, sw = 1, shift = [0, 0], step = 1 } = {}) {
    const q = p => { const r = mv(M, p); return [r[0] + shift[0], r[1] + shift[1]]; };
    const g = [];
    for (let i = -nx; i <= nx; i += step) g.push(dln(f, [q([i, -ny]), q([i, ny])], { stroke, sw }));
    for (let j = -ny; j <= ny; j += step) g.push(dln(f, [q([-nx, j]), q([nx, j])], { stroke, sw }));
    return g.join('');
}

/**
 * M 으로 옮긴 단위정사각형과 두 기저벡터의 상.
 * 사영처럼 기저벡터가 0 으로 눌리는 경우가 있으므로 길이 0 이면 화살표 대신 점을 찍는다
 * (길이 0 인 path 는 화살촉만 엉뚱한 방향으로 남는다).
 */
function unitCell(f, M, { fill = C3, op = 0.24, e1 = 's1', e2 = 's2', width = 2.6 } = {}) {
    const o = [0, 0]; const a = mv(M, [1, 0]); const b = mv(M, [0, 1]);
    const c = [a[0] + b[0], a[1] + b[1]];
    const one = (p, cls) => (Math.hypot(p[0], p[1]) < 1e-9
        ? ddot(f, o, cls === 's1' ? C1 : C2, 5)
        : varw(f, o, p, { cls, width }));
    return dpoly(f, [o, a, c, b], { fill, op, stroke: fill, sw: 1.3 })
        + one(a, e1) + one(b, e2);
}

/* ------------------------------------------------------------------ *
 * 3차원 축측투영 — x 는 왼쪽 아래로, y 는 오른쪽으로, z 는 위로
 * ------------------------------------------------------------------ */

function iso(ox, oy, s) {
    return ([x, y, z]) => [ox + s * (y - 0.6 * x), oy + s * (0.42 * x - z)];
}
const l3 = (P, pts, o) => ln(pts.map(P), o);
const a3 = (P, p, q, o) => { const A = P(p); const B = P(q); return arw(A[0], A[1], B[0], B[1], o); };
const poly3 = (P, pts, o) => poly(pts.map(P), o);
const t3 = (P, p, s, o = {}) => { const A = P(p); return txt(A[0] + (o.dx ?? 0), A[1] + (o.dy ?? 0), s, o); };
const d3 = (P, p, col, r) => { const A = P(p); return pdot(A[0], A[1], col, r); };

/** 3차원 좌표축 세 개. 눈금은 없다 — 방향만 알면 되는 그림에 쓴다. */
function axes3(P, L = 2.6) {
    return a3(P, [0, 0, 0], [L, 0, 0], { cls: 'ark', width: 1.1 })
        + a3(P, [0, 0, 0], [0, L, 0], { cls: 'ark', width: 1.1 })
        + a3(P, [0, 0, 0], [0, 0, L], { cls: 'ark', width: 1.1 })
        + t3(P, [L, 0, 0], 'x', { dx: -12, dy: 12, cls: 'ink2', size: 'sm' })
        + t3(P, [0, L, 0], 'y', { dx: 5, dy: 12, cls: 'ink2', size: 'sm' })
        + t3(P, [0, 0, L], 'z', { dx: 5, dy: -2, cls: 'ink2', size: 'sm' });
}

/* ================================================================== *
 * 6장 — 일반 벡터공간
 * ================================================================== */

/* ---- 6-1. 부분공간인 것과 아닌 것 ---- */
add((() => {
    const W = 780; const H = 318;
    const g = [];
    g.push(txt(W / 2, 26, '부분공간은 ‘원점을 품고, 더해도 늘려도 밖으로 나가지 않는’ 조각이다',
        { anchor: 'middle', cls: 'ink bold' }));
    const mk = i => {
        const px = 12 + i * 256; const py = 44; const pw = 244; const ph = 262;
        const { f } = sq([-2.4, 2.4], [-2, 2], px + 26, py + 52, 40);
        return { px, py, pw, ph, f };
    };
    {   // 원점을 지나는 직선
        const { px, py, pw, ph, f } = mk(0);
        g.push(panel(px, py, pw, ph, '원점을 지나는 직선'));
        g.push(axes(f, { xRange: [-2.4, 2.4], yRange: [-2, 2] }));
        g.push(dln(f, [[-2.4, -1.2], [2.4, 1.2]], { stroke: C1, sw: 2.4 }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], [-1.4, -0.7], { cls: 's2', width: 2.4 }));
        g.push(ddot(f, [0.6, 0.3], C3, 5));
        g.push(dtxt(f, [2, 1], 'u', { dx: 4, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1.4, -0.7], 'v', { dx: -14, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0.6, 0.3], 'u + v', { dx: 8, dy: -9, cls: 'ink', size: 'sm' }));
        g.push(ddot(f, [0, 0], CK, 3.5));
        g.push(txt(px + pw / 2, py + ph - 32, '0 이 있고, 합도 스칼라배도', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '직선을 벗어나지 않는다 — 부분공간', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {   // 원점을 지나지 않는 직선
        const px = 12 + 1 * 256; const py = 44; const pw = 244; const ph = 262;
        const { f } = sq([-2.4, 2.4], [-1.6, 2.4], px + 26, py + 52, 40);
        g.push(panel(px, py, pw, ph, '원점을 지나지 않는 직선'));
        g.push(axes(f, { xRange: [-2.4, 2.4], yRange: [-1.6, 2.4] }));
        g.push(dln(f, [[-2.4, -0.2], [2.4, 2.2]], { stroke: C1, sw: 2.4 }));
        g.push(varw(f, [0, 0], [-1, 0.5], { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], [1, 1.5], { cls: 's2', width: 2.4 }));
        g.push(dln(f, [[0, 1], [0, 2]], { stroke: C3, sw: 1.2, dash: '4 3' }));
        g.push(ddot(f, [0, 2], C3, 5));
        g.push(dtxt(f, [-1, 0.5], 'u', { dx: -14, dy: -2, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1, 1.5], 'v', { dx: 6, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 2], 'u + v', { dx: -34, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(`<circle cx="${f.X(0)}" cy="${f.Y(0)}" r="4.5" fill="none" stroke="${CK}" stroke-width="1.4"/>`);
        g.push(dtxt(f, [0, 0], '0 은 직선 밖', { dx: 9, dy: 21, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 32, '합이 직선을 벗어난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '부분공간이 아니다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {   // 제1사분면
        const { px, py, pw, ph, f } = mk(2);
        g.push(panel(px, py, pw, ph, '제1사분면'));
        g.push(dpoly(f, [[0, 0], [2.4, 0], [2.4, 2], [0, 2]], { fill: C1, op: 0.12 }));
        g.push(axes(f, { xRange: [-2.4, 2.4], yRange: [-2, 2] }));
        g.push(varw(f, [0, 0], [1.5, 1], { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], [-1.5, -1], { cls: 's2', width: 2.4, dash: '5 4' }));
        g.push(dtxt(f, [1.5, 1], 'u', { dx: 5, dy: -4, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1.5, -1], '−u', { dx: -22, dy: 6, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 32, '더하기는 괜찮지만 −1 배가 밖으로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '부분공간이 아니다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    return {
        name: 'la-sp-subspace',
        svg: svg({
            width: W,
            height: H,
            title: '부분공간인 것과 아닌 것',
            desc: '원점을 지나는 직선은 부분공간이고, 원점을 비켜간 직선과 제1사분면은 부분공간이 아니다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-2. 생성(span) ---- */
add((() => {
    const W = 780; const H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '생성이란 ‘그 벡터들로 갈 수 있는 곳 전부’다', { anchor: 'middle', cls: 'ink bold' }));
    {   // span{v} = 직선
        const px = 12; const py = 44; const pw = 244; const ph = 280;
        const { f } = sq([-2.4, 2.4], [-2, 2], px + 26, py + 58, 40);
        g.push(panel(px, py, pw, ph, '벡터 하나', 'span{v} = 직선'));
        g.push(axes(f, { xRange: [-2.4, 2.4], yRange: [-2, 2] }));
        g.push(dln(f, [[-2.4, -1.2], [2.4, 1.2]], { stroke: C1, sw: 2.2, dash: '7 5' }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 2.6 }));
        [-1.6, 0.7, 1.6].forEach(t => g.push(ddot(f, [2 * t * 0.6, 1 * t * 0.6], CK, 3.4)));
        g.push(dtxt(f, [2, 1], 'v', { dx: 5, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1.9, -0.95], 't v', { dx: -4, dy: 16, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '한 방향뿐이라 직선이 최선이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {   // span{v,w} = 평면 전체
        const px = 268; const py = 44; const pw = 244; const ph = 280;
        const { f, w, h, x, y } = sq([-2.4, 2.4], [-2, 2], px + 26, py + 58, 40);
        g.push(panel(px, py, pw, ph, '방향이 다른 둘', 'span{v, w} = 평면 전체'));
        g.push(clip(x, y, w, h, lattice(f, [[2, -1], [1, 1]], { nx: 4, ny: 4, step: 0.5, stroke: CG, sw: 1 })));
        g.push(axes(f, { xRange: [-2.4, 2.4], yRange: [-2, 2] }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], [-1, 1], { cls: 's2', width: 2.6 }));
        g.push(dtxt(f, [2, 1], 'v', { dx: 5, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1, 1], 'w', { dx: -16, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '두 방향의 눈금이 평면을 덮는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {   // R^3 에서 평면
        const px = 524; const py = 44; const pw = 244; const ph = 280;
        g.push(panel(px, py, pw, ph, 'R³ 에서 둘', 'span{u, v} = 원점을 지나는 평면'));
        const P = iso(px + 130, py + 172, 30);
        const u = [2, 0, 1]; const v = [0, 2, 1];
        const cmb = (a, b) => [a * u[0] + b * v[0], a * u[1] + b * v[1], a * u[2] + b * v[2]];
        g.push(poly3(P, [cmb(-1.05, -1.05), cmb(1.05, -1.05), cmb(1.05, 1.05), cmb(-1.05, 1.05)],
            { fill: C3, op: 0.22, stroke: C3, sw: 1.4 }));
        g.push(axes3(P, 2.5));
        g.push(a3(P, [0, 0, 0], u, { cls: 's1', width: 2.5 }));
        g.push(a3(P, [0, 0, 0], v, { cls: 's2', width: 2.5 }));
        g.push(t3(P, u, 'u', { dx: -14, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(t3(P, v, 'v', { dx: 7, dy: -4, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '평면 전체가 부분공간이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-span',
        svg: svg({
            width: W,
            height: H,
            title: '생성(span)은 직선이거나 평면이거나 공간 전체다',
            desc: '벡터 하나의 생성은 직선, 방향이 다른 둘의 생성은 평면이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-3. 일차독립과 일차종속 ---- */
add((() => {
    const W = 760; const H = 332;
    const g = [];
    g.push(txt(W / 2, 26, '갈림길은 하나다 — 세 벡터가 한 평면 위에 놓이는가', { anchor: 'middle', cls: 'ink bold' }));
    const u = [2, 0, 1]; const v = [0, 2, 1];
    const cmb = (a, b) => [a * u[0] + b * v[0], a * u[1] + b * v[1], a * u[2] + b * v[2]];
    const plane = [cmb(-1.1, -1.1), cmb(1.25, -1.1), cmb(1.25, 1.25), cmb(-1.1, 1.25)];
    {
        const px = 12; const py = 44; const pw = 356; const ph = 274;
        g.push(panel(px, py, pw, ph, '일차종속', 'w = u + v — 새 방향이 없다'));
        const P = iso(px + 190, py + 166, 36);
        g.push(poly3(P, plane, { fill: C3, op: 0.2, stroke: C3, sw: 1.3 }));
        g.push(axes3(P, 2.6));
        g.push(a3(P, [0, 0, 0], u, { cls: 's1', width: 2.5 }));
        g.push(a3(P, [0, 0, 0], v, { cls: 's2', width: 2.5 }));
        g.push(a3(P, [0, 0, 0], cmb(1, 1), { cls: 's3', width: 3 }));
        g.push(l3(P, [u, cmb(1, 1)], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(l3(P, [v, cmb(1, 1)], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(t3(P, u, 'u', { dx: -14, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(t3(P, v, 'v', { dx: 7, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(t3(P, cmb(1, 1), 'w', { dx: 2, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'w 가 평면 안에 갇혀 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 392; const py = 44; const pw = 356; const ph = 274;
        g.push(panel(px, py, pw, ph, '일차독립', 'w 가 평면을 벗어난다'));
        const P = iso(px + 190, py + 166, 36);
        const w = [0.7, 0.7, 2.1];
        g.push(poly3(P, plane, { fill: C3, op: 0.14, stroke: C3, sw: 1.3, dash: '5 4' }));
        g.push(axes3(P, 2.6));
        g.push(l3(P, [[0.7, 0.7, 0.7], w], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(a3(P, [0, 0, 0], u, { cls: 's1', width: 2.5 }));
        g.push(a3(P, [0, 0, 0], v, { cls: 's2', width: 2.5 }));
        g.push(a3(P, [0, 0, 0], w, { cls: 's3', width: 3 }));
        g.push(t3(P, u, 'u', { dx: -14, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(t3(P, v, 'v', { dx: 7, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(t3(P, w, 'w', { dx: 8, dy: 4, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '셋이 공간 전체를 생성한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-independence',
        svg: svg({
            width: W,
            height: H,
            title: '일차독립과 일차종속',
            desc: '세 벡터가 한 평면 위에 놓이면 종속이고, 하나가 평면을 벗어나면 독립이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-4. 기저와 좌표 ---- */
add((() => {
    const W = 744; const H = 324;
    const g = [];
    g.push(txt(W / 2, 26, '같은 화살표라도 어떤 자를 대느냐에 따라 좌표가 달라진다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-1.4, 4.6]; const yR = [-1.6, 3.4];
    const mkf = px => sq(xR, yR, px + 60, 92, 40);
    {
        const px = 12; const py = 44; const pw = 356; const ph = 268;
        const { f, w, h, x, y } = mkf(px);
        g.push(panel(px, py, pw, ph, '표준기저 e₁, e₂', '[x] = (4, 2)'));
        g.push(clip(x, y, w, h, lattice(f, [[1, 0], [0, 1]], { nx: 6, ny: 5 })));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 3, 4], yTicks: [2, 3] }));
        g.push(dln(f, [[4, 0], [4, 2], [0, 2]], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(varw(f, [0, 0], [1, 0], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [0, 1], { cls: 's3', width: 2.6 }));
        g.push(varw(f, [0, 0], [4, 2], { cls: 's1', width: 3 }));
        g.push(dtxt(f, [4, 2], 'x', { dx: 6, dy: -6, cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [1, 0], 'e₁', { dx: -2, dy: 17, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 1], 'e₂', { dx: -22, dy: 4, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 8, '오른쪽으로 4칸, 위로 2칸', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 376; const py = 44; const pw = 356; const ph = 268;
        const { f, w, h, x, y } = mkf(px);
        g.push(panel(px, py, pw, ph, '기저 B = {b₁, b₂}', '[x]~B = (3, −1)'));
        g.push(clip(x, y, w, h, lattice(f, [[1, -1], [1, 1]], { nx: 5, ny: 5 })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[0, 0], [3, 3], [4, 2]], { stroke: C2, sw: 1.6, dash: '5 4' }));
        g.push(varw(f, [0, 0], [1, 1], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [-1, 1], { cls: 's3', width: 2.6 }));
        g.push(varw(f, [0, 0], [4, 2], { cls: 's1', width: 3 }));
        g.push(dtxt(f, [4, 2], 'x', { dx: 6, dy: -6, cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [1, 1], 'b₁', { dx: 4, dy: 14, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1, 1], 'b₂', { dx: -22, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1.6, 1.9], '3b₁', { dx: -6, dy: -6, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [3.5, 2.5], '−b₂', { dx: 2, dy: 0, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 8, 'b₁ 로 3칸, b₂ 로 −1칸', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-basis-coord',
        svg: svg({
            width: W,
            height: H,
            title: '기저를 바꾸면 좌표가 바뀐다',
            desc: '같은 벡터가 표준기저에서 (4, 2), 기울어진 기저에서 (3, −1) 이라는 좌표를 갖는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-5. 네 부분공간과 계수-퇴화차수 정리 ---- */
add((() => {
    const W = 760; const H = 342;
    const g = [];
    g.push(txt(W / 2, 26, '정의역은 ‘0 으로 눌리는 부분’과 ‘일대일로 살아남는 부분’으로 갈린다',
        { anchor: 'middle', cls: 'ink bold' }));
    const LC = [196, 190]; const RC = [566, 190]; const RX = 118; const RY = 120;
    // 왼쪽 타원 — 정의역
    g.push(ell(LC[0], LC[1], RX, RY, { fill: C1, op: 0.06, stroke: CK, sw: 1.4 }));
    g.push(ln([[LC[0] - 112, LC[1] + 24], [LC[0] + 112, LC[1] + 24]], { stroke: CK, sw: 1.2, dash: '5 4' }));
    g.push(txt(LC[0], LC[1] - 46, '행공간 row(A)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(LC[0], LC[1] - 28, '차원 r = rank(A)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(LC[0], LC[1] + 62, '영공간 null(A)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(LC[0], LC[1] + 80, '차원 n − r = nullity(A)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(LC[0], 68, 'Rⁿ  (정의역)', { anchor: 'middle', cls: 'ink bold' }));
    // 오른쪽 타원 — 공역
    g.push(ell(RC[0], RC[1], RX, RY, { fill: C2, op: 0.06, stroke: CK, sw: 1.4 }));
    g.push(ln([[RC[0] - 112, RC[1] + 24], [RC[0] + 112, RC[1] + 24]], { stroke: CK, sw: 1.2, dash: '5 4' }));
    g.push(txt(RC[0], RC[1] - 46, '열공간 col(A)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(RC[0], RC[1] - 28, '차원 r — 상(image) 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(pdot(RC[0], RC[1] + 60, C3, 5));
    g.push(txt(RC[0] + 12, RC[1] + 64, '0', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(RC[0], RC[1] + 88, '열공간 밖은 도달하지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(RC[0], 68, 'Rᵐ  (공역)', { anchor: 'middle', cls: 'ink bold' }));
    // 화살표
    g.push(arw(LC[0] + 96, LC[1] - 62, RC[0] - 96, RC[1] - 62, { cls: 's1', width: 2.4 }));
    g.push(txt((LC[0] + RC[0]) / 2, LC[1] - 70, '일대일로 대응', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(LC[0] + 92, LC[1] + 66, RC[0] - 18, RC[1] + 60, { cls: 's3', width: 2.4 }));
    g.push(txt((LC[0] + RC[0]) / 2 - 6, LC[1] + 46, '전부 0 으로 눌린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 16, 'n = rank(A) + nullity(A)', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'la-sp-four-spaces',
        svg: svg({
            width: W,
            height: H,
            title: '계수-퇴화차수 정리를 그림으로',
            desc: '정의역이 영공간과 행공간으로 갈리고, 행공간만 열공간에 일대일로 대응한다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 7장 — 선형변환
 * ================================================================== */

/* ---- 7-1. 선형변환이 격자에 하는 일 ---- */
add((() => {
    const W = 780; const H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '선형변환은 격자를 격자로 보낸다 — 원점은 제자리, 평행은 평행',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-3.4, 3.4]; const yR = [-2.8, 2.8];
    const mk = (i, title, sub) => {
        const px = 12 + i * 256; const py = 44; const pw = 244; const ph = 280;
        const s = sq(xR, yR, px + 22, py + 60, 29);
        g.push(panel(px, py, pw, ph, title, sub));
        return { px, py, pw, ph, ...s };
    };
    {
        const { px, pw, py, ph, f, w, h, x, y } = mk(0, '보내기 전', '정수 격자와 단위정사각형');
        g.push(clip(x, y, w, h, lattice(f, [[1, 0], [0, 1]], { nx: 5, ny: 4 })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(unitCell(f, [[1, 0], [0, 1]]));
        g.push(dtxt(f, [1, 0], 'e₁', { dx: 2, dy: 16, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 1], 'e₂', { dx: -22, dy: 2, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '한 칸이 넓이 1 인 정사각형', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const A = [[2, 1], [1, 1]];
        const { px, pw, py, ph, f, w, h, x, y } = mk(1, '선형변환을 먹인 뒤', 'e₁ → (2, 1),  e₂ → (1, 1)');
        g.push(clip(x, y, w, h, lattice(f, A, { nx: 6, ny: 6 })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, unitCell(f, A)));
        g.push(dtxt(f, [2, 1], 'T(e₁)', { dx: 4, dy: 14, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1, 1], 'T(e₂)', { dx: -42, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(ddot(f, [0, 0], CK, 3.5));
        g.push(txt(px + pw / 2, py + ph - 14, '기울었지만 여전히 격자다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const sh = [1.2, 0.9];
        const { px, pw, py, ph, f, w, h, x, y } = mk(2, '평행이동', '(x, y) → (x + 1.2, y + 0.9)');
        g.push(clip(x, y, w, h, lattice(f, [[1, 0], [0, 1]], { nx: 5, ny: 4, shift: sh })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, dpoly(f, [[sh[0], sh[1]], [sh[0] + 1, sh[1]], [sh[0] + 1, sh[1] + 1], [sh[0], sh[1] + 1]],
            { fill: C3, op: 0.24, stroke: C3, sw: 1.3 })));
        g.push(ddot(f, [0, 0], CK, 3.5));
        g.push(ddot(f, sh, C2, 5));
        g.push(arw(f.X(0), f.Y(0), f.X(sh[0]) - 4, f.Y(sh[1]) + 3, { cls: 's2', width: 2 }));
        g.push(dtxt(f, sh, 'T(0)', { dx: 6, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 0], '0', { dx: -13, dy: 15, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '원점이 움직였다 — 선형변환이 아니다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    return {
        name: 'la-sp-linear-grid',
        svg: svg({
            width: W,
            height: H,
            title: '선형변환이 격자에 하는 일',
            desc: '선형변환은 격자를 기울어진 격자로 보내고 원점을 고정한다. 평행이동은 원점을 옮기므로 선형변환이 아니다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-2. R² 의 기본 변환 여섯 ---- */
add((() => {
    const W = 790; const H = 552;
    const g = [];
    g.push(txt(W / 2, 26, 'R² 의 기본 변환 — 격자에 무슨 일이 일어나는지로 구분한다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-2.3, 2.3]; const yR = [-2.3, 2.3];
    const cell = (i, title, sub, M, extra) => {
        const px = 12 + (i % 3) * 256; const py = 44 + Math.floor(i / 3) * 246;
        const pw = 244; const ph = 238;
        const { f, w, h, x, y } = sq(xR, yR, px + 40, py + 56, 36);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(clip(x, y, w, h, lattice(f, M, { nx: 6, ny: 6 })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, unitCell(f, M)));
        if (extra) g.push(extra(f, x, y, w, h));
        return { px, py, pw, ph, f };
    };
    cell(0, '항등변환', '(x, y) → (x, y)', [[1, 0], [0, 1]]);
    cell(1, '회전', '(x, y) → 60° 돌린 자리', rot(60),
        (f) => angleArc(S(f, [0, 0]), S(f, [1.9, 0]), S(f, mv(rot(60), [1.9, 0])), 56, '60°'));
    cell(2, '반사', '(x, y) → (x, −y)', [[1, 0], [0, -1]], (f) => dln(f, [[-2.3, 0], [2.3, 0]], { stroke: CK, sw: 1.6, dash: '6 4' }));
    cell(3, '확대', '(x, y) → (1.5x, 1.5y)', [[1.5, 0], [0, 1.5]]);
    cell(4, '전단', '(x, y) → (x + y, y)', [[1, 1], [0, 1]]);
    {
        const { f } = cell(5, '사영', '(x, y) → (x, 0)', [[1, 0], [0, 0]]);
        g.push(dln(f, [[-2.3, 0], [2.3, 0]], { stroke: C3, sw: 3 }));
        g.push(dln(f, [[1.6, 1.6], [1.6, 0]], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(ddot(f, [1.6, 1.6], CK, 3.6));
        g.push(ddot(f, [1.6, 0], C3, 4.4));
        g.push(dtxt(f, [1.6, 1.6], '평면 전체가', { dx: -76, dy: -6, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [1.6, 0], '직선 하나로', { dx: -78, dy: 18, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [0, 0], 'e₂ 는 0 으로', { dx: -104, dy: -14, cls: 'ink', size: 'sm' }));
    }
    return {
        name: 'la-sp-basic-maps',
        svg: svg({
            width: W,
            height: H,
            title: 'R² 의 기본 선형변환',
            desc: '회전 반사 확대 전단 사영이 각각 격자에 하는 일. 사영만 격자를 직선으로 눌러 버린다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-3. 행렬의 열은 기저의 상이다 ---- */
add((() => {
    const W = 744; const H = 316;
    const g = [];
    g.push(txt(W / 2, 26, '기저를 어디로 보내는지만 알면 나머지는 따라온다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-0.9, 6.3]; const yR = [-0.9, 5.3];
    const mk = px => sq(xR, yR, px + 78, 100, 27);
    {
        const px = 12; const py = 44; const pw = 356; const ph = 258;
        const { f } = mk(px);
        g.push(panel(px, py, pw, ph, '보내기 전', 'x = 2e₁ + e₂'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2, 4] }));
        g.push(dln(f, [[0, 0], [2, 0], [2, 1]], { stroke: CK, sw: 1.2, dash: '4 3' }));
        g.push(varw(f, [0, 0], [1, 0], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [0, 1], { cls: 's3', width: 2.6 }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 3 }));
        g.push(dtxt(f, [1, 0], 'e₁', { dx: -2, dy: 17, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 1], 'e₂', { dx: -20, dy: 4, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [2, 1], 'x = (2, 1)', { dx: 7, dy: -5, cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '어떤 벡터든 e₁ 과 e₂ 의 일차결합이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 376; const py = 44; const pw = 356; const ph = 258;
        const { f } = mk(px);
        g.push(panel(px, py, pw, ph, '보낸 뒤', 'T(x) = 2T(e₁) + T(e₂)'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2, 4] }));
        g.push(dln(f, [[0, 0], [4, 2], [5, 4]], { stroke: CK, sw: 1.2, dash: '4 3' }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [1, 2], { cls: 's3', width: 2.6 }));
        g.push(varw(f, [0, 0], [5, 4], { cls: 's1', width: 3 }));
        g.push(dtxt(f, [2, 1], 'T(e₁) = (2, 1)', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1, 2], 'T(e₂) = (1, 2)', { dx: -6, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [5, 4], 'T(x) = (5, 4)', { dx: -18, dy: -10, cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '두 상의 일차결합, 계수는 그대로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-columns',
        svg: svg({
            width: W,
            height: H,
            title: '행렬의 열은 기저의 상이다',
            desc: 'x 를 기저의 일차결합으로 적으면 T(x) 는 같은 계수로 기저의 상을 일차결합한 것이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-4. 합성은 행렬 곱이다 ---- */
add((() => {
    const W = 780; const H = 306;
    const g = [];
    g.push(txt(W / 2, 24, '두 번 옮기기 = 행렬 두 개를 곱해 한 번에 옮기기',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-2.4, 2.4]; const yR = [-2.4, 2.4];
    const Sm = [[1, 1], [0, 1]];
    const Tm = rot(90);
    const cellAt = (px, title, sub, M) => {
        const py = 44; const pw = 224; const ph = 240;
        const { f, w, h, x, y } = sq(xR, yR, px + 35, py + 54, 32);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(clip(x, y, w, h, lattice(f, M, { nx: 6, ny: 6 })));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, unitCell(f, M)));
        return { f, py, ph };
    };
    cellAt(12, '처음', '단위정사각형', [[1, 0], [0, 1]]);
    cellAt(276, 'S 를 먹인 뒤', '전단 (x, y) → (x + y, y)', Sm);
    cellAt(540, '이어서 T', '90° 회전', mm(Tm, Sm));
    g.push(arw(244, 164, 270, 164, { cls: 's2', width: 2.2 }));
    g.push(txt(257, 150, 'S', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(508, 164, 534, 164, { cls: 's2', width: 2.2 }));
    g.push(txt(521, 150, 'T', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '가운데를 건너뛰고 한 번에 가는 행렬이 TS 다 — 오른쪽 것을 먼저 쓴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-sp-compose',
        svg: svg({
            width: W,
            height: H,
            title: '합성이 행렬 곱인 이유',
            desc: '전단을 하고 회전을 한 결과가 두 행렬의 곱 하나를 적용한 결과와 같다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-5. 핵과 상 ---- */
add((() => {
    const W = 760; const H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '핵은 0 으로 눌리는 방향, 상은 도달할 수 있는 곳',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-2.6, 2.6]; const yR = [-2.6, 2.6];
    {
        const px = 12; const py = 44; const pw = 356; const ph = 272;
        const { f } = sq(xR, yR, px + 74, py + 50, 40);
        g.push(panel(px, py, pw, ph, '정의역', 'T(x, y) = (x + y, x + y)'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[-2.4, 2.4], [2.4, -2.4]], { stroke: C3, sw: 3 }));
        g.push(dln(f, [[-0.4, 2.4], [2.4, -0.4]], { stroke: C1, sw: 1.8, dash: '6 4' }));
        [[2, 0], [1, 1], [0, 2]].forEach(p => g.push(ddot(f, p, C1, 4.6)));
        g.push(varw(f, [0, 0], [1.4, -1.4], { cls: 's3', width: 2.6 }));
        g.push(dtxt(f, [1.4, -1.4], '핵 : x + y = 0', { dx: -10, dy: 20, cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [0.4, 2.2], 'x + y = 2 인 점들', { dx: 4, dy: 0, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '점선 위의 점은 모두 같은 곳으로 간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 392; const py = 44; const pw = 356; const ph = 272;
        const { f } = sq(xR, yR, px + 74, py + 50, 40);
        g.push(panel(px, py, pw, ph, '공역', '상 = 직선 y = x'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[-2.4, -2.4], [2.4, 2.4]], { stroke: C1, sw: 3 }));
        g.push(ddot(f, [2, 2], C1, 5.6));
        g.push(ddot(f, [0, 0], C3, 5.6));
        g.push(dtxt(f, [2, 2], '(2, 2)', { dx: -8, dy: -12, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 0], '핵은 전부 여기로', { dx: 10, dy: 18, cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [-2.2, -1.5], '상 = 열공간', { dx: 0, dy: 0, cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '직선 밖의 점에는 도달하지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-kernel-image',
        svg: svg({
            width: W,
            height: H,
            title: '핵과 상',
            desc: '평면 전체가 직선 하나로 눌린다. 눌려서 0 이 되는 방향이 핵이고 도달하는 직선이 상이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-6. 기저를 바꾸면 같은 변환의 행렬이 바뀐다 ---- */
add((() => {
    const W = 780; const H = 358;
    const g = [];
    g.push(txt(W / 2, 26, '변환은 그대로다 — 바뀐 것은 그것을 적는 자(기저)뿐이다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-2.2, 4.2]; const yR = [-2.6, 3.8];
    {
        const px = 12; const py = 44; const pw = 366; const ph = 298;
        const { f } = sq(xR, yR, px + 74, py + 54, 34);
        g.push(panel(px, py, pw, ph, '표준기저로 적으면', '열이 (2, 1) 과 (1, 2) — 대각행렬이 아니다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dpoly(f, [[0, 0], [1, 0], [1, 1], [0, 1]], { fill: CK, op: 0.08, stroke: CK, sw: 1.2, dash: '5 4' }));
        g.push(dpoly(f, [[0, 0], [2, 1], [3, 3], [1, 2]], { fill: C3, op: 0.16, stroke: C3, sw: 1.4 }));
        g.push(varw(f, [0, 0], [1, 0], { cls: 'ark', width: 1.8 }));
        g.push(varw(f, [0, 0], [0, 1], { cls: 'ark', width: 1.8 }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 2.8 }));
        g.push(varw(f, [0, 0], [1, 2], { cls: 's2', width: 2.8 }));
        g.push(dtxt(f, [2, 1], 'A e₁', { dx: 6, dy: 12, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1, 2], 'A e₂', { dx: -34, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '두 기저벡터가 모두 방향까지 바뀐다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 402; const py = 44; const pw = 366; const ph = 298;
        const { f } = sq(xR, yR, px + 74, py + 54, 34);
        g.push(panel(px, py, pw, ph, '기저 {b₁, b₂} 로 적으면', '대각행렬 — b₁ 은 3배, b₂ 는 그대로'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dpoly(f, [[0, 0], [1, 1], [2, 0], [1, -1]], { fill: CK, op: 0.08, stroke: CK, sw: 1.2, dash: '5 4' }));
        g.push(dpoly(f, [[0, 0], [3, 3], [4, 2], [1, -1]], { fill: C3, op: 0.16, stroke: C3, sw: 1.4 }));
        g.push(varw(f, [0, 0], [1, 1], { cls: 'ark', width: 1.8 }));
        g.push(varw(f, [0, 0], [1, -1], { cls: 'ark', width: 1.8 }));
        g.push(varw(f, [0, 0], [3, 3], { cls: 's1', width: 2.8 }));
        g.push(varw(f, [0, 0], [1, -1], { cls: 's2', width: 2.8 }));
        g.push(dtxt(f, [1, 1], 'b₁', { dx: -20, dy: -2, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [3, 3], 'A b₁ = 3b₁', { dx: -14, dy: -10, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [1, -1], 'A b₂ = b₂', { dx: 8, dy: 6, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '두 방향 모두 방향이 그대로다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-basis-change-map',
        svg: svg({
            width: W,
            height: H,
            title: '같은 변환, 다른 기저, 다른 행렬',
            desc: '표준기저에서는 뒤섞인 행렬이 기울어진 기저에서는 대각행렬이 된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8장 — 고유값과 고유벡터
 * ================================================================== */

/* ---- 8-1. 방향이 그대로인 벡터 ---- */
add((() => {
    const W = 760; const H = 326;
    const g = [];
    g.push(txt(W / 2, 26, '거의 모든 방향은 틀어진다. 틀어지지 않는 방향이 고유벡터다',
        { anchor: 'middle', cls: 'ink bold' }));
    const A = [[2, 1], [1, 2]];
    const xR = [-1.5, 3.7]; const yR = [-1.9, 3.3];
    {
        const px = 12; const py = 44; const pw = 356; const ph = 268;
        const { f } = sq(xR, yR, px + 79, py + 50, 38);
        g.push(panel(px, py, pw, ph, '방향이 바뀌는 벡터', '화살표가 원래 방향에서 벗어난다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        [[1, 0], [0, 1]].forEach((p, i) => {
            const q = mv(A, p);
            g.push(dln(f, [[0, 0], [q[0] * 1.02, q[1] * 1.02]], { stroke: CG, sw: 1 }));
            g.push(varw(f, [0, 0], p, { cls: 'ark', width: 1.8 }));
            g.push(varw(f, [0, 0], q, { cls: i === 0 ? 's1' : 's2', width: 2.8 }));
            g.push(angleArc(S(f, [0, 0]), S(f, p), S(f, q), 52, ''));
        });
        g.push(dtxt(f, [1.55, 0.3], '틀어진 만큼', { dx: 0, dy: 0, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [1, 0], 'x', { dx: -2, dy: 17, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [2, 1], 'A x', { dx: 7, dy: 4, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0, 1], 'y', { dx: -18, dy: 4, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [1, 2], 'A y', { dx: 6, dy: -4, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, 'Ax 는 x 의 배수가 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 392; const py = 44; const pw = 356; const ph = 268;
        const { f } = sq(xR, yR, px + 79, py + 50, 38);
        g.push(panel(px, py, pw, ph, '방향이 그대로인 벡터', 'Ax 가 x 와 같은 직선 위에 있다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[-1.2, -1.2], [3.2, 3.2]], { stroke: CG, sw: 1.4, dash: '6 4' }));
        g.push(dln(f, [[-1.2, 1.2], [2.2, -2.2]], { stroke: CG, sw: 1.4, dash: '6 4' }));
        g.push(varw(f, [0, 0], [3, 3], { cls: 's1', width: 2.8 }));
        g.push(varw(f, [0, 0], [1, 1], { cls: 'ark', width: 2 }));
        g.push(varw(f, [0, 0], [1, -1], { cls: 's2', width: 2.8 }));
        g.push(dtxt(f, [1, 1], 'b₁', { dx: -22, dy: 0, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [3, 3], 'A b₁ = 3b₁', { dx: -20, dy: -10, cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [1, -1], 'A b₂ = b₂', { dx: 8, dy: 8, cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '길이만 λ 배로 바뀐다 (λ = 3 과 λ = 1)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-eigen-directions',
        svg: svg({
            width: W,
            height: H,
            title: '고유벡터는 방향을 유지한다',
            desc: '보통의 벡터는 변환 뒤 방향이 틀어지지만 고유벡터는 같은 직선 위에 머문다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-2. 특성방정식 ---- */
add((() => {
    const W = 770; const H = 330;
    const g = [];
    g.push(txt(W / 2, 26, 'det(A − λI) 가 0 이 되는 λ 에서만 공간이 눌린다',
        { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 12; const py = 44; const pw = 380; const ph = 272;
        const xR = [-0.6, 4.6]; const yR = [-1.5, 2.9];
        const { f } = sq(xR, yR, px + 66, py + 54, 48);
        g.push(panel(px, py, pw, ph, '특성다항식', 'p(λ) = λ² − 4λ + 3'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], xLabel: 'λ' }));
        g.push(f.curve(t => t * t - 4 * t + 3, { from: 0.13, to: 3.87, cls: 's1' }));
        g.push(ddot(f, [1, 0], C2, 5.2));
        g.push(ddot(f, [3, 0], C2, 5.2));
        g.push(dtxt(f, [1, 0], 'λ = 1', { dx: -4, dy: -12, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [3, 0], 'λ = 3', { dx: 4, dy: -12, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [0.2, 2.4], 'det(A − λI)', { dx: 4, dy: 0, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '근이 둘 — 고유값이 둘이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 402; const py = 44; const pw = 356; const ph = 272;
        const xR = [-1.4, 2.6]; const yR = [-1.6, 2.4];
        const { f } = sq(xR, yR, px + 78, py + 54, 50);
        g.push(panel(px, py, pw, ph, 'λ = 1 일 때 A − I 가 하는 일', '(x, y) → (x + y, x + y)'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dpoly(f, [[0, 0], [1, 0], [1, 1], [0, 1]], { fill: CK, op: 0.08, stroke: CK, sw: 1.2, dash: '5 4' }));
        g.push(dln(f, [[0, 0], [2, 2]], { stroke: C1, sw: 4 }));
        g.push(varw(f, [0, 0], [1, -1], { cls: 's2', width: 2.8 }));
        g.push(ddot(f, [0, 0], C3, 5));
        g.push(dtxt(f, [1.4, 1.4], '정사각형이 선분으로', { dx: -6, dy: -10, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [1, -1], '이 방향이 0 으로 → 고유벡터', { dx: -124, dy: 14, cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '넓이가 0 이니 det(A − I) = 0 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-char-det',
        svg: svg({
            width: W,
            height: H,
            title: '특성방정식이 고유값을 찾는 이유',
            desc: 'det(A − λI) 의 근에서 A − λI 가 공간을 찌부러뜨리고 그 눌리는 방향이 고유벡터가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-3. 두 가지 중복도 — 살아남는 방향의 개수 ---- */
add((() => {
    const W = 760; const H = 350;
    const g = [];
    g.push(txt(W / 2, 26, '고유값이 같아도 제자리 직선에 남는 방향의 개수는 다를 수 있다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-3.4, 3.4]; const yR = [-0.9, 3.5];
    const dirs = [0, 40, 90, 140];
    const unit = deg => [Math.cos((deg * Math.PI) / 180), Math.sin((deg * Math.PI) / 180)];

    const draw = (px, title, sub, M, foot, keep) => {
        const py = 44; const pw = 356; const ph = 272;
        const { f, w, h, x, y } = sq(xR, yR, px + 35, py + 58, 42);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        const inner = [];
        for (const deg of dirs) {
            const u = unit(deg);
            const far = [u[0] * 3.6, u[1] * 3.6];
            const near = [-u[0] * 0.9, -u[1] * 0.9];
            const on = keep == null || keep.includes(deg);
            inner.push(dln(f, [near, far], { stroke: on ? C1 : CG, sw: on ? 1.6 : 1.2, dash: '5 4' }));
        }
        for (const deg of dirs) {
            const u = unit(deg);
            const q = mv(M, u);
            const on = keep == null || keep.includes(deg);
            inner.push(varw(f, [0, 0], q, { cls: on ? 's1' : 's2', width: 2.6 }));
            inner.push(varw(f, [0, 0], u, { cls: 'ark', width: 1.6 }));
        }
        g.push(clip(x, y, w, h, inner.join('')));
        g.push(txt(px + pw / 2, py + ph - 14, foot, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return f;
    };

    {
        const f = draw(12, '고유공간이 평면 전체', 'B = 3I  :  (x, y) → (3x, 3y)', [[3, 0], [0, 3]],
            '네 방향 모두 점선 위에 남는다 — 기하적 중복도 2', null);
        g.push(dtxt(f, [2.12, 2.12], '3배만 커진다', { dx: 6, dy: -6, cls: 'ink bold', size: 'sm' }));
    }
    {
        const f = draw(392, '고유공간이 직선 하나', 'C  :  (x, y) → (3x + y, 3y)', [[3, 1], [0, 3]],
            '가로축 하나만 점선 위에 남는다 — 기하적 중복도 1', [0]);
        g.push(dtxt(f, [3.1, 0], '이 방향만 살아남는다', { dx: 0, dy: 24, anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(dtxt(f, [1, 3], '점선을 벗어났다', { dx: 10, dy: 2, cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '가는 검은 화살표가 넣은 방향, 굵은 화살표가 나온 방향이다. 두 행렬 모두 고유값은 λ = 3 하나뿐이고 대수적 중복도는 2다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-sp-multiplicity',
        svg: svg({
            width: W,
            height: H,
            title: '대수적 중복도와 기하적 중복도의 차이',
            desc: '고유값 3 이 두 번 겹치는 두 행렬. 3I 는 모든 방향이 고유방향이고 전단이 섞인 쪽은 가로축 하나만 남는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-4. AP = PD 를 열별로 읽는다 ---- */
add((() => {
    const W = 780; const H = 336;
    const g = [];
    g.push(txt(W / 2, 26, 'AP = PD 는 열마다 A p~j = λ~j p~j 라는 말이다',
        { anchor: 'middle', cls: 'ink bold' }));
    const A = [[4, -1], [2, 1]];
    const xR = [-0.8, 3.8]; const yR = [-0.8, 4.6];
    {
        const px = 12; const py = 44; const pw = 340; const ph = 272;
        const { f } = sq(xR, yR, px + 87, py + 48, 36);
        g.push(panel(px, py, pw, ph, 'P 의 두 열을 A 에 통과시킨다', 'A  :  (x, y) → (4x − y, 2x + y)'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [1, 2, 3, 4] }));
        g.push(dln(f, [[-0.7, -0.7], [3.7, 3.7]], { stroke: CG, sw: 1.4, dash: '6 4' }));
        g.push(dln(f, [[-0.35, -0.7], [2.25, 4.5]], { stroke: CG, sw: 1.4, dash: '6 4' }));
        const p1 = [1, 1]; const p2 = [1, 2];
        g.push(varw(f, [0, 0], mv(A, p1), { cls: 's1', width: 2.8 }));
        g.push(varw(f, [0, 0], mv(A, p2), { cls: 's2', width: 2.8 }));
        g.push(varw(f, [0, 0], p1, { cls: 'ark', width: 1.8 }));
        g.push(varw(f, [0, 0], p2, { cls: 'ark', width: 1.8 }));
        g.push(dtxt(f, p1, 'p~1', { dx: 13, dy: 11, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, mv(A, p1), 'A p~1 = 3 p~1', { dx: 2, dy: 20, cls: 'f1 bold', size: 'sm' }));
        g.push(dtxt(f, p2, 'p~2', { dx: -22, dy: 2, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, mv(A, p2), 'A p~2 = 2 p~2', { dx: 9, dy: 4, cls: 'f2 bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '두 화살표 모두 제 점선 위에 남고 길이만 λ 배가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 364; const py = 44; const pw = 404; const ph = 272;
        g.push(panel(px, py, pw, ph, '같은 사실을 열로 적으면', 'j 열끼리만 견주면 된다'));
        const cx = px + 92; const cw = 250; const chh = 46;
        const cell = (y, label, a, b) => {
            g.push(txt(cx - 14, y + chh / 2 + 5, label, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
            g.push(box(cx, y, cw, chh, { stroke: CG, sw: 1.3, rx: 4 }));
            g.push(ln([[cx + cw / 2, y], [cx + cw / 2, y + chh]], { stroke: CG, sw: 1.3 }));
            g.push(txt(cx + cw / 4, y + chh / 2 + 5, a, { anchor: 'middle', cls: 'f1 bold', size: 'sm' }));
            g.push(txt(cx + (3 * cw) / 4, y + chh / 2 + 5, b, { anchor: 'middle', cls: 'f2 bold', size: 'sm' }));
        };
        cell(py + 62, 'AP =', 'A p~1', 'A p~2');
        cell(py + 132, 'PD =', 'λ~1 p~1', 'λ~2 p~2');
        g.push(txt(px + pw / 2, py + 128, '위아래가 같아야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(ln([[cx + cw / 4, py + 196], [cx + cw / 4, py + 214]], { stroke: CK, sw: 1.4 }));
        g.push(ln([[cx + (3 * cw) / 4, py + 196], [cx + (3 * cw) / 4, py + 214]], { stroke: CK, sw: 1.4 }));
        g.push(txt(cx + cw / 4, py + 234, 'A p~1 = λ~1 p~1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx + (3 * cw) / 4, py + 234, 'A p~2 = λ~2 p~2', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '그래서 P 의 열은 고유벡터, D 의 대각성분은 그 고유값이어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-diagonalize-columns',
        svg: svg({
            width: W,
            height: H,
            title: 'AP = PD 를 열 단위로 읽는다',
            desc: 'AP 의 j 열은 A p_j 이고 PD 의 j 열은 lambda_j p_j 이므로 두 행렬이 같다는 것은 열마다 고유벡터 관계가 성립한다는 뜻이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-5. 대칭행렬의 고유방향은 직교한다 ---- */
add((() => {
    const W = 780; const H = 322;
    const g = [];
    g.push(txt(W / 2, 26, '단위원을 보내 보면 고유방향이 보인다 — 직교하는 것은 대칭행렬뿐이다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-3.4, 3.4]; const yR = [-3.4, 3.4];
    const circ = (f, M) => {
        const pts = [];
        for (let i = 0; i <= 96; i += 1) {
            const t = (i / 96) * 2 * Math.PI;
            pts.push(mv(M, [Math.cos(t), Math.sin(t)]));
        }
        return dln(f, pts, { stroke: C1, sw: 2.2 });
    };
    const mk = (i, title, sub) => {
        const px = 12 + i * 256; const py = 44; const pw = 244; const ph = 264;
        const { f } = sq(xR, yR, px + 27, py + 56, 28);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        return { px, py, pw, ph, f };
    };
    {
        const { f } = mk(0, '단위원', '길이 1 인 벡터 전부');
        g.push(circ(f, [[1, 0], [0, 1]]));
        g.push(varw(f, [0, 0], [0.707, 0.707], { cls: 's2', width: 2.4 }));
        g.push(varw(f, [0, 0], [0.707, -0.707], { cls: 's3', width: 2.4 }));
        g.push(rightAngle(S(f, [0, 0]), [1, -1], [1, 1], 11));
    }
    {
        const A = [[2, 1], [1, 2]];
        const { px, pw, py, ph, f } = mk(1, '대칭행렬의 상', 'A = 열 (2, 1) 과 (1, 2)');
        g.push(circ(f, A));
        g.push(varw(f, [0, 0], [2.12, 2.12], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [0.707, -0.707], { cls: 's3', width: 2.6 }));
        g.push(rightAngle(S(f, [0, 0]), [1, -1], [1, 1], 11));
        g.push(dtxt(f, [2.12, 2.12], '3배', { dx: -8, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [0.707, -0.707], '1배', { dx: 8, dy: 12, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '타원의 두 주축이 직교한다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const A = [[2, 1], [0, 1]];
        const { px, pw, py, ph, f } = mk(2, '대칭이 아닌 행렬의 상', 'A = 열 (2, 0) 과 (1, 1)');
        g.push(circ(f, A));
        g.push(varw(f, [0, 0], [2.4, 0], { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], [-1.7, 1.7], { cls: 's3', width: 2.6 }));
        g.push(angleArc(S(f, [0, 0]), S(f, [2.4, 0]), S(f, [-1.7, 1.7]), 40, '135°'));
        g.push(dtxt(f, [2.4, 0], 'λ = 2', { dx: -34, dy: 22, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, [-1.7, 1.7], 'λ = 1', { dx: -8, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '고유방향이 직교하지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    return {
        name: 'la-sp-symmetric-axes',
        svg: svg({
            width: W,
            height: H,
            title: '대칭행렬의 고유벡터는 직교한다',
            desc: '단위원이 타원으로 가는데, 대칭행렬일 때만 두 고유방향이 서로 수직이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-6. 복소 고유값 ---- */
add((() => {
    const W = 760; const H = 326;
    const g = [];
    g.push(txt(W / 2, 26, '회전은 어느 방향도 그대로 두지 않는다 — 그래서 고유값이 실수가 아니다',
        { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 12; const py = 44; const pw = 356; const ph = 268;
        const xR = [-2.2, 2.2]; const yR = [-2.2, 2.2];
        const { f } = sq(xR, yR, px + 82, py + 52, 43);
        g.push(panel(px, py, pw, ph, '90° 회전', '방향이 유지되는 벡터가 하나도 없다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        const R = rot(90);
        [[1.7, 0], [1.2, 1.2], [0, 1.7], [1.2, -1.2]].forEach((p, i) => {
            const q = mv(R, p);
            g.push(dln(f, [[0, 0], p], { stroke: CG, sw: 2 }));
            g.push(varw(f, [0, 0], q, { cls: i % 2 === 0 ? 's1' : 's2', width: 2.4 }));
        });
        g.push(dtxt(f, [1.75, 0], '보내기 전', { dx: -18, dy: 18, cls: 'ink2', size: 'sm' }));
        g.push(dtxt(f, [0, 1.75], '보낸 뒤', { dx: 8, dy: -4, cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '모두 90° 돌아간다 — 실수 λ 로는 적을 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 392; const py = 44; const pw = 356; const ph = 268;
        const xR = [-1.7, 1.7]; const yR = [-1.7, 1.7];
        const { f } = sq(xR, yR, px + 82, py + 52, 55);
        g.push(panel(px, py, pw, ph, '복소평면에서 보면', 'θ 만큼 회전한 곳에 켤레쌍이 있다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xLabel: '실수축' }));
        const pts = [];
        for (let i = 0; i <= 96; i += 1) {
            const t = (i / 96) * 2 * Math.PI;
            pts.push([Math.cos(t), Math.sin(t)]);
        }
        g.push(dln(f, pts, { stroke: CG, sw: 1.4 }));
        const th = (50 * Math.PI) / 180;
        const p1 = [Math.cos(th), Math.sin(th)];
        const p2 = [Math.cos(th), -Math.sin(th)];
        g.push(dln(f, [[0, 0], p1], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(ddot(f, p1, C1, 5.4));
        g.push(ddot(f, p2, C2, 5.4));
        g.push(dtxt(f, p1, 'cos θ + i sin θ', { dx: 8, dy: -6, cls: 'ink', size: 'sm' }));
        g.push(dtxt(f, p2, 'cos θ − i sin θ', { dx: 8, dy: 14, cls: 'ink', size: 'sm' }));
        g.push(angleArc(S(f, [0, 0]), S(f, [1, 0]), S(f, p1), 34, 'θ'));
        g.push(dtxt(f, [0, 1.5], '허수축', { dx: 8, dy: 2, cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '크기 1, 편각 θ — 회전각이 그대로 편각이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-complex-eigen',
        svg: svg({
            width: W,
            height: H,
            title: '회전변환의 고유값은 복소수다',
            desc: '회전은 실수 고유값을 갖지 않고, 복소평면 단위원 위의 켤레쌍을 고유값으로 갖는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-7. 켤레복소 쌍은 회전과 확대다 ---- */
add((() => {
    const W = 744; const H = 316;
    const g = [];
    g.push(txt(W / 2, 26, '고유값의 크기 r 이 커지는지 작아지는지를 정한다',
        { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-3.3, 3.3]; const yR = [-3.3, 3.3];
    const orbit = (f, r, steps, start) => {
        const M = rot(30).map(row => row.map(v => v * r));
        let p = start;
        const out = [];
        const pts = [p];
        for (let i = 0; i < steps; i += 1) { p = mv(M, p); pts.push(p); }
        out.push(dln(f, pts.slice(0, -1), { stroke: C1, sw: 1.8 }));
        out.push(varw(f, pts[steps - 1], pts[steps], { cls: 's1', width: 1.8 }));
        pts.slice(0, -1).forEach((q, i) => out.push(ddot(f, q, i === 0 ? C2 : C1, i === 0 ? 5 : 2.8)));
        return out.join('');
    };
    {
        const px = 12; const py = 44; const pw = 356; const ph = 258;
        const { f, w, h, x, y } = sq(xR, yR, px + 79, py + 52, 30);
        g.push(panel(px, py, pw, ph, 'r > 1 — 밖으로 감긴다', 'θ = 30°, r = 1.085'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, orbit(f, 1.085, 18, [0.6, 0])));
        g.push(dtxt(f, [0.55, 0], '시작', { dx: -4, dy: 19, anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '한 번 곱할 때마다 30° 돌고 1.085배', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 376; const py = 44; const pw = 356; const ph = 258;
        const { f, w, h, x, y } = sq(xR, yR, px + 79, py + 52, 30);
        g.push(panel(px, py, pw, ph, 'r < 1 — 안으로 감긴다', 'θ = 30°, r = 0.92'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(clip(x, y, w, h, orbit(f, 0.92, 20, [3, 0])));
        g.push(dtxt(f, [3, 0], '시작', { dx: -4, dy: 19, anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 14, '원점으로 빨려 들어간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return {
        name: 'la-sp-spiral',
        svg: svg({
            width: W,
            height: H,
            title: '켤레복소 고유값은 회전과 확대의 합성이다',
            desc: '고유값의 편각이 회전각을, 크기가 확대율을 정한다. 크기가 1 보다 크면 나선이 밖으로 감긴다',
            body: g.join(''),
        }),
    };
})());

export default figures;
