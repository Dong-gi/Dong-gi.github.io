/**
 * mcs 13장(통신망) · 14장(단순 그래프) · 15장(평면 그래프)의 그림.
 *
 * 이름은 모두 `mcs-g-` 로 시작한다(담당 D 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `in~0`, `K~{3,3}` 표기를 쓰고, 나머지는 유니코드
 * (≤ ≥ ≠ × · → ← ⌈⌉ ⌊⌋ √ ² ³ ⁿ χ ∈ ∪ ∩ ⊆ ✓)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 그림은 세 종류다. 13장은 스위치와 배선을 그린 망 그림,
 * 14장은 점과 선분(방향 없는 간선) 그림, 15장은 평면에 그렸을 때 생기는
 * 면을 보이는 그림이다. 세 장 모두 ‘무엇이 하한을 주는가’를 그림에서 읽게 했다.
 */
import { svg, txt, esc } from './lib.mjs';

const figures = [];
const add = (name, body) => figures.push({ name, svg: body });
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구 — lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * ------------------------------------------------------------------ */

const MK = { [C1]: 'ar1', [C2]: 'ar2', [C3]: 'ar3', [CK]: 'ark', [CI]: 'ark', [CG]: 'ark' };

function arw(x1, y1, x2, y2, { col = CK, sw = 1.8, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" marker-end="url(#${MK[col] ?? 'ark'})"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { col = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}"`
        + ` stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 색을 직접 지정하는 글자. txt() 는 CSS 클래스만 받아 계열색을 쓸 수 없다. */
function ctxt(x, y, str, col, { anchor = 'start', size = 'sm', bold = false } = {}) {
    const cls = [size === 'sm' ? 'sm' : null, bold ? 'bold' : null].filter(Boolean).join(' ');
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"${cls ? ` class="${cls}"` : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 19, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 35, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 표. hlCol 에 든 열은 배경을 옅게 칠하고, hlCell 은 칸 하나를 칠한다. */
function table(x, y, cols, rows, { cw = 80, rh = 26, hlCol = [], hlColor = C3, hlCell = [], cws = null } = {}) {
    const widths = cws ?? cols.map(() => cw);
    const xs = [];
    let acc = x;
    for (const w of widths) { xs.push(acc); acc += w; }
    const W = acc - x;
    const g = [];
    for (const c of hlCol) g.push(box(xs[c], y, widths[c], rh * (rows.length + 1), { fill: hlColor, op: 0.15, stroke: 'none', rx: 2 }));
    for (const [r, c, col] of hlCell) g.push(box(xs[c], y + rh * (r + 1), widths[c], rh, { fill: col, op: 0.25, stroke: col, sw: 1.6, rx: 2 }));
    cols.forEach((c, i) => g.push(txt(xs[i] + widths[i] / 2, y + 18, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    g.push(ln([[x, y + rh], [x + W, y + rh]], { col: CK, sw: 1.3 }));
    rows.forEach((row, r) => {
        row.forEach((v, i) => g.push(txt(xs[i] + widths[i] / 2, y + rh * (r + 2) - 8, String(v), { anchor: 'middle', cls: 'ink', size: 'sm' })));
        if (r < rows.length - 1) g.push(ln([[x, y + rh * (r + 2)], [x + W, y + rh * (r + 2)]], { col: CG, sw: 0.8 }));
    });
    g.push(box(x, y, W, rh * (rows.length + 1), { stroke: CK, sw: 1.2, rx: 3 }));
    return g.join('');
}

/* ------------------------------------------------------------------ *
 * 점과 선 소도구. 방향 그래프가 아니므로 기본이 화살촉 없는 선분이다.
 * ------------------------------------------------------------------ */

/** 원 정점. fill 을 주면 옅게 채운다. */
function nd(p, label, { col = CI, fill = null, r = 15, sw = 1.6, lc = null, dy = 4 } = {}) {
    return `<circle cx="${r2(p[0])}" cy="${r2(p[1])}" r="${r}" fill="${fill ?? 'none'}"`
        + `${fill ? ' fill-opacity="0.22"' : ''} stroke="${col}" stroke-width="${sw}"/>`
        + (label === '' ? '' : ctxt(p[0], p[1] + dy, label, lc ?? CI, { anchor: 'middle' }));
}

/** 사각 단말. */
function sq(p, label, { col = CI, w = 30, h = 21, fill = null, lc = null } = {}) {
    return box(p[0] - w / 2, p[1] - h / 2, w, h, { stroke: col, sw: 1.5, rx: 3, fill: fill ?? 'none', op: fill ? 0.22 : 1 })
        + ctxt(p[0], p[1] + 4, label, lc ?? CI, { anchor: 'middle' });
}

/** 정점 중심 p 와 q 를 잇는 방향 없는 간선. 두 원 반지름만큼 잘라낸다. */
function eg(p, q, { col = CK, sw = 1.6, r = 15, rq = null, dash, bend = 0 } = {}) {
    const rb = rq ?? r;
    const dx = q[0] - p[0], dy = q[1] - p[1];
    const L = Math.hypot(dx, dy) || 1;
    const nx = -dy / L, ny = dx / L;
    const cx = (p[0] + q[0]) / 2 + nx * bend * 2;
    const cy = (p[1] + q[1]) / 2 + ny * bend * 2;
    const trim = (a, b, rr) => {
        const ux = b[0] - a[0], uy = b[1] - a[1];
        const m = Math.hypot(ux, uy) || 1;
        return [a[0] + (ux / m) * rr, a[1] + (uy / m) * rr];
    };
    const s = trim(p, [cx, cy], r);
    const e = trim(q, [cx, cy], rb);
    if (!bend) return ln([s, e], { col, sw, dash });
    return `<path d="M${r2(s[0])} ${r2(s[1])} Q${r2(cx)} ${r2(cy)} ${r2(e[0])} ${r2(e[1])}" fill="none" stroke="${col}"`
        + ` stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 방향 있는 간선(12장 표기를 잇는 그림에서만 쓴다). */
function dg(p, q, { col = CK, sw = 1.6, r = 15, rq = null, dash, bend = 0 } = {}) {
    const rb = rq ?? r;
    const dx = q[0] - p[0], dy = q[1] - p[1];
    const L = Math.hypot(dx, dy) || 1;
    const nx = -dy / L, ny = dx / L;
    const cx = (p[0] + q[0]) / 2 + nx * bend * 2;
    const cy = (p[1] + q[1]) / 2 + ny * bend * 2;
    const trim = (a, b, rr) => {
        const ux = b[0] - a[0], uy = b[1] - a[1];
        const m = Math.hypot(ux, uy) || 1;
        return [a[0] + (ux / m) * rr, a[1] + (uy / m) * rr];
    };
    const s = trim(p, [cx, cy], r);
    const e = trim(q, [cx, cy], rb);
    if (!bend) return arw(s[0], s[1], e[0], e[1], { col, sw, dash });
    return `<path d="M${r2(s[0])} ${r2(s[1])} Q${r2(cx)} ${r2(cy)} ${r2(e[0])} ${r2(e[1])}" fill="none" stroke="${col}"`
        + ` stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''} marker-end="url(#${MK[col] ?? 'ark'})"/>`;
}

/** 자기 고리(방향 그래프 비교 그림에서만). */
function selfLoop(p, { col = CK, sw = 1.6, r = 15 } = {}) {
    const [x, y] = p;
    const a = [x - r * 0.55, y - r * 0.84];
    const b = [x + r * 0.55, y - r * 0.84];
    return `<path d="M${r2(a[0])} ${r2(a[1])} C${r2(x - r * 1.9)} ${r2(y - r * 3.0)} ${r2(x + r * 1.9)} ${r2(y - r * 3.0)} ${r2(b[0])} ${r2(b[1])}"`
        + ` fill="none" stroke="${col}" stroke-width="${sw}" marker-end="url(#${MK[col] ?? 'ark'})"/>`;
}

/**
 * 열마다 스위치가 같은 수만큼 있는 망(나비망·베네시망)을 그린다.
 * xors[c] 는 열 c 에서 c+1 로 갈 때 짝이 되는 행 번호의 XOR 값이다.
 * bold 는 [열, 행] 목록으로, 이어진 구간을 굵게 덧그린다.
 */
function columnNet({ x0, dx, y0, dy, cols, rows, xors, r = 9, bold = [], boldCol = C1, sw = 1.1 }) {
    const P = (c, k) => [x0 + c * dx, y0 + k * dy];
    const g = [];
    for (let c = 0; c < cols - 1; c += 1) {
        const x = xors[c];
        for (let k = 0; k < rows; k += 1) {
            g.push(eg(P(c, k), P(c + 1, k), { col: CG, sw, r }));
            g.push(eg(P(c, k), P(c + 1, k ^ x), { col: CG, sw, r }));
        }
    }
    for (let i = 0; i + 1 < bold.length; i += 1) {
        g.push(eg(P(...bold[i]), P(...bold[i + 1]), { col: boldCol, sw: 3, r }));
    }
    const boldSet = new Set(bold.map(b => `${b[0]},${b[1]}`));
    for (let c = 0; c < cols; c += 1) {
        for (let k = 0; k < rows; k += 1) {
            const on = boldSet.has(`${c},${k}`);
            g.push(nd(P(c, k), '', { col: on ? boldCol : CK, fill: on ? boldCol : null, r, sw: on ? 2.2 : 1.4 }));
        }
    }
    return { body: g.join(''), P };
}

/* ================================================================== *
 * 13장 — 통신망
 * ================================================================== */

/* 1. 완전 이진 트리망 — 라우팅이 유일하다 */
add('mcs-g-tree-net', (() => {
    const W = 720, H = 340;
    const g = [];
    g.push(txt(W / 2, 24, '완전 이진 트리망 — 입력 4개와 출력 4개. 동그라미가 스위치, 네모가 단말이다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 15;
    const lx = [150, 290, 450, 590];
    const leaf = lx.map(x => [x, 196]);
    const mid = [[(lx[0] + lx[1]) / 2, 138], [(lx[2] + lx[3]) / 2, 138]];
    const root = [(mid[0][0] + mid[1][0]) / 2, 80];
    const inT = lx.map(x => [x - 32, 262]);
    const outT = lx.map(x => [x + 32, 262]);

    const path = [inT[1], leaf[1], mid[0], root, mid[1], leaf[3], outT[3]];
    for (let i = 0; i + 1 < path.length; i += 1) {
        g.push(eg(path[i], path[i + 1], { col: C1, sw: 3.4, r: i === 0 ? 13 : R, rq: i === path.length - 2 ? 13 : R }));
    }
    for (let i = 0; i < 4; i += 1) {
        g.push(eg(leaf[i], mid[i < 2 ? 0 : 1], { col: CK, sw: 1.5, r: R }));
        g.push(eg(inT[i], leaf[i], { col: CK, sw: 1.5, r: 13, rq: R }));
        g.push(eg(outT[i], leaf[i], { col: CK, sw: 1.5, r: 13, rq: R }));
    }
    g.push(eg(mid[0], root, { col: CK, sw: 1.5, r: R }));
    g.push(eg(mid[1], root, { col: CK, sw: 1.5, r: R }));
    g.push(nd(root, '뿌리', { col: C2, fill: C2, r: R + 4 }));
    mid.forEach(p => g.push(nd(p, '', { col: CK, r: R })));
    leaf.forEach(p => g.push(nd(p, '', { col: CK, r: R })));
    for (let i = 0; i < 4; i += 1) {
        g.push(sq(inT[i], `in~${i}`, { col: i === 1 ? C1 : CK, fill: i === 1 ? C1 : null, w: 30, h: 21 }));
        g.push(sq(outT[i], `out~${i}`, { col: i === 3 ? C1 : CK, fill: i === 3 ? C1 : null, w: 34, h: 21 }));
    }
    g.push(ctxt(30, 300, '굵은 길이 입력 1 에서 출력 3 으로 가는 유일한 경로다. 간선 6개를 지나므로 길이가 6 이고, 이 망에서 가장 먼 짝이므로 지름도 6 이다.', C1));
    g.push(txt(30, 322, '나무에는 두 정점을 잇는 경로가 하나뿐이라 라우팅을 고를 여지가 없다. 그래서 지연을 줄일 방법도 없다',
        { cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '완전 이진 트리망과 그 안의 유일한 경로',
        desc: '입력 4개 출력 4개인 완전 이진 트리망에서 입력 1 에서 출력 3 으로 가는 길이 6 인 경로를 굵게 표시한 그림',
        body: g.join(''),
    });
})());

/* 2. 같은 망, 두 라우팅 문제 — 혼잡도가 갈린다 */
add('mcs-g-tree-congestion', (() => {
    const W = 720, H = 340;
    const g = [];
    g.push(txt(W / 2, 24, '같은 망인데 라우팅 문제에 따라 혼잡도가 1 과 4 로 갈린다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 12;

    const draw = (ox, title, sub, perm, col) => {
        const out = [];
        out.push(panel(ox, 40, 330, 250, title, sub));
        const lx = [0, 1, 2, 3].map(i => ox + 48 + i * 78);
        const leaf = lx.map(x => [x, 192]);
        const mid = [[(lx[0] + lx[1]) / 2, 142], [(lx[2] + lx[3]) / 2, 142]];
        const root = [(mid[0][0] + mid[1][0]) / 2, 92];
        const inT = lx.map(x => [x - 20, 252]);
        const outT = lx.map(x => [x + 20, 252]);
        // 배선
        for (let i = 0; i < 4; i += 1) {
            out.push(eg(leaf[i], mid[i < 2 ? 0 : 1], { col: CG, sw: 1.3, r: R }));
            out.push(eg(inT[i], leaf[i], { col: CG, sw: 1.3, r: 11, rq: R }));
            out.push(eg(outT[i], leaf[i], { col: CG, sw: 1.3, r: 11, rq: R }));
        }
        out.push(eg(mid[0], root, { col: CG, sw: 1.3, r: R }));
        out.push(eg(mid[1], root, { col: CG, sw: 1.3, r: R }));
        // 경로. 입력 i 와 출력 i 는 같은 잎 스위치에 붙어 있으므로 i = π(i) 면 잎에서 되돌아간다.
        const load = new Map();
        const bump = p => load.set(`${p[0]},${p[1]}`, (load.get(`${p[0]},${p[1]}`) ?? 0) + 1);
        for (let i = 0; i < 4; i += 1) {
            const j = perm[i];
            const up = [inT[i], leaf[i]];
            if (i !== j) {
                if ((i < 2) === (j < 2)) up.push(mid[i < 2 ? 0 : 1]);
                else up.push(mid[i < 2 ? 0 : 1], root, mid[j < 2 ? 0 : 1]);
                up.push(leaf[j]);
            }
            up.push(outT[j]);
            for (let k = 0; k + 1 < up.length; k += 1) {
                out.push(eg(up[k], up[k + 1], { col, sw: 2.4, r: k === 0 ? 11 : R, rq: k === up.length - 2 ? 11 : R, bend: (i - 1.5) * 2 }));
            }
            up.slice(1, -1).forEach(bump);
        }
        for (const p of [root, ...mid, ...leaf]) {
            const c = load.get(`${p[0]},${p[1]}`) ?? 0;
            out.push(nd(p, String(c), { col: c >= 4 ? C2 : CK, fill: c >= 4 ? C2 : null, r: R, lc: c >= 4 ? C2 : CK }));
        }
        for (let i = 0; i < 4; i += 1) {
            out.push(sq(inT[i], String(i), { col: CK, w: 22, h: 19 }));
            out.push(sq(outT[i], String(i), { col: CK, w: 22, h: 19 }));
        }
        out.push(ctxt(ox + 165, 282, '동그라미 안의 수가 그 스위치를 지나는 경로의 수다', CK, { anchor: 'middle' }));
        return out.join('');
    };

    g.push(draw(20, '항등 순열 — 입력 i 는 출력 i 로', 'π(i) = i', [0, 1, 2, 3], C3));
    g.push(draw(370, '뒤집기 순열 — 입력 i 는 출력 3−i 로', 'π(i) = 3 − i', [3, 2, 1, 0], C2));
    g.push(txt(W / 2, 312, '오른쪽에서는 네 경로가 모두 뿌리를 지난다. 어떻게 라우팅해도 그럴 수밖에 없으므로 이 망의 혼잡도는 N 이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 330, '망의 혼잡도는 ‘가장 나쁜 순열에 대해 가장 좋은 라우팅을 골랐을 때’의 값이다 — 최대와 최소를 겹쳐 쓴 정의다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '트리망에서 두 라우팅 문제의 혼잡도',
        desc: '항등 순열은 혼잡도 1 로 풀리지만 뒤집기 순열은 네 경로가 모두 뿌리를 지나 혼잡도가 4 가 되는 것을 보이는 그림',
        body: g.join(''),
    });
})());

/* 3. 이분폭이 혼잡도의 하한을 준다 */
add('mcs-g-bisection', (() => {
    const W = 720, H = 350;
    const g = [];
    g.push(txt(W / 2, 24, '이분폭 — 반으로 자르려면 몇 개의 선을 끊어야 하는가',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 13;
    g.push(panel(20, 40, 400, 250, '완전 이진 트리망: 이분폭이 1 이다', '선 하나만 끊으면 입력 절반 · 출력 절반이 떨어져 나간다'));
    const lx = [100, 160, 260, 320];
    const leaf = lx.map(x => [x, 226]);
    const mid = [[130, 168], [290, 168]];
    const root = [200, 112];
    for (let i = 0; i < 4; i += 1) g.push(eg(leaf[i], mid[i < 2 ? 0 : 1], { col: CK, sw: 1.5, r: R }));
    g.push(eg(mid[0], root, { col: C2, sw: 3.4, r: R }));
    g.push(eg(mid[1], root, { col: CK, sw: 1.5, r: R }));
    g.push(box(80, 150, 100, 98, { stroke: C1, sw: 1.6, dash: '6 4', rx: 10 }));
    g.push(nd(root, '', { col: CK, r: R }));
    mid.forEach(p => g.push(nd(p, '', { col: CK, r: R })));
    leaf.forEach((p, i) => g.push(nd(p, String(i), { col: i < 2 ? C1 : CK, fill: i < 2 ? C1 : null, r: R })));
    g.push(ctxt(216, 110, '상자를 넘는 선은 하나뿐', C2));
    g.push(ctxt(216, 127, '— 이것을 끊으면 갈린다', C2));
    g.push(ctxt(130, 270, '상자 안: 입력 2 · 출력 2', C1, { anchor: 'middle' }));
    g.push(ctxt(300, 270, '밖도: 입력 2 · 출력 2', CK, { anchor: 'middle' }));

    g.push(panel(440, 40, 260, 250, '왜 하한이 되는가', '비둘기집 원리 한 줄'));
    const lines = [
        '상자 안의 입력을 모두 상자 밖 출력으로',
        '보내는 순열을 고른다. 그러면 경로',
        'N/2 개가 반드시 절단면을 넘어야 한다.',
        '',
        '넘을 수 있는 선이 w 개뿐이므로',
        '어떤 선 하나에는 경로가',
        '적어도 (N/2) / w 개 얹힌다.',
        '',
        '트리는 w = 1 이므로 N/2 이상이고,',
        '실제 간선 혼잡도가 정확히 N/2 다.',
    ];
    lines.forEach((t, i) => g.push(ctxt(456, 92 + i * 18, t, i === 9 ? C2 : CK)));
    g.push(txt(W / 2, 316, '이분폭이 작다는 것은 ‘허리가 얇다’는 뜻이다. 허리가 얇으면 아무리 영리하게 라우팅해도 그곳이 막힌다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 336, '거꾸로 혼잡도 1 을 얻으려면 이분폭이 N/2 이상이어야 하고, 그만큼의 배선을 실제로 깔아야 한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '이분폭과 혼잡도의 하한',
        desc: '완전 이진 트리망의 이분폭이 2 임을 절단선으로 보이고, 비둘기집 원리로 혼잡도 하한을 얻는 논증을 적은 그림',
        body: g.join(''),
    });
})());

/* 4. 2차원 배열망 — 혼잡도 2 */
add('mcs-g-array-net', (() => {
    const W = 755, H = 360;
    const g = [];
    g.push(txt(W / 2, 24, '2차원 배열망 — 오른쪽으로 가다 아래로 꺾는다. 그래서 한 스위치에 경로가 둘까지만 모인다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 15;
    const xs = [230, 330, 430, 530];
    const ys = [80, 140, 200, 260];
    const P = (i, j) => [xs[j], ys[i]];
    const perm = [2, 0, 3, 1];
    const cols = [C1, C2, C3, CI];
    // 배선
    for (let i = 0; i < 4; i += 1) {
        for (let j = 0; j < 4; j += 1) {
            if (j < 3) g.push(eg(P(i, j), P(i, j + 1), { col: CG, sw: 1.4, r: R }));
            if (i < 3) g.push(eg(P(i, j), P(i + 1, j), { col: CG, sw: 1.4, r: R }));
        }
    }
    // 경로
    for (let i = 0; i < 4; i += 1) {
        const j = perm[i];
        const pts = [[140, ys[i]]];
        for (let k = 0; k <= j; k += 1) pts.push(P(i, k));
        for (let k = i + 1; k < 4; k += 1) pts.push(P(k, j));
        pts.push([xs[j], 320]);
        for (let k = 0; k + 1 < pts.length; k += 1) {
            g.push(eg(pts[k], pts[k + 1], { col: cols[i], sw: 2.6, r: k === 0 ? 16 : R, rq: k === pts.length - 2 ? 12 : R }));
        }
    }
    for (let i = 0; i < 4; i += 1) {
        for (let j = 0; j < 4; j += 1) g.push(nd(P(i, j), '', { col: CK, r: R }));
    }
    for (let i = 0; i < 4; i += 1) {
        g.push(sq([140, ys[i]], `in~${i}`, { col: cols[i], w: 34, h: 22, lc: cols[i] }));
        g.push(sq([xs[i], 320], `out~${i}`, { col: cols[perm.indexOf(i)], w: 38, h: 22, lc: cols[perm.indexOf(i)] }));
    }
    g.push(ctxt(600, 96, '행 i 를 따라', CK));
    g.push(ctxt(600, 114, '열 π(i) 까지 가고', CK));
    g.push(ctxt(600, 132, '거기서 아래로 내려간다', CK));
    g.push(ctxt(600, 164, '스위치 (i, j) 를 지날 수 있는', CI, { bold: true }));
    g.push(ctxt(600, 182, '경로는 둘뿐이다 —', CI, { bold: true }));
    g.push(ctxt(600, 200, '입력 i 에서 온 것과', C1));
    g.push(ctxt(600, 218, '출력 j 로 가는 것.', C2));
    g.push(ctxt(600, 244, '그래서 혼잡도가 2 다.', CI, { bold: true }));
    g.push(txt(W / 2, 344, '값을 치른 곳은 스위치 수다. 배열은 스위치를 N² 개 쓴다 — N 이 1000 이면 백만 개다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '2차원 배열망과 그 안의 라우팅',
        desc: '4×4 배열망에서 네 경로가 각각 행을 따라가다 열에서 내려가는 모습과, 한 스위치에 경로가 둘까지만 모이는 이유를 적은 그림',
        body: g.join(''),
    });
})());

/* 5. 나비망 F3 */
add('mcs-g-butterfly', (() => {
    const W = 720, H = 380;
    const g = [];
    g.push(txt(W / 2, 24, '나비망 F~3 — 열 네 개, 각 열에 스위치 8개. 열을 하나 지날 때마다 목적지의 한 비트가 정해진다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const net = columnNet({
        x0: 210, dx: 130, y0: 66, dy: 36, cols: 4, rows: 8,
        xors: [4, 2, 1], r: 10, bold: [[0, 0], [1, 4], [2, 4], [3, 5]], boldCol: C1,
    });
    g.push(net.body);
    for (let k = 0; k < 8; k += 1) {
        g.push(ctxt(184, 70 + k * 36, String(k), k === 0 || k === 5 ? C1 : CK, { anchor: 'end', bold: k === 0 || k === 5 }));
    }
    const heads = ['0열', '1열', '2열', '3열'];
    const notes = ['4 를 XOR', '2 를 XOR', '1 을 XOR'];
    heads.forEach((h, c) => g.push(ctxt(210 + c * 130, 50, h, CI, { anchor: 'middle', bold: true })));
    notes.forEach((t, c) => g.push(ctxt(275 + c * 130, 366, t, C3, { anchor: 'middle' })));
    g.push(ctxt(30, 80, '행 번호', CK));
    g.push(ctxt(30, 120, '굵은 길: 0 → 5', C1, { bold: true }));
    g.push(ctxt(30, 140, '5 는 이진법으로 101 이다.', CK));
    g.push(ctxt(30, 158, '0열에서 4 의 자리를 1 로', CK));
    g.push(ctxt(30, 176, '(0 → 4), 1열에서 2 의', CK));
    g.push(ctxt(30, 194, '자리를 0 으로 (그대로),', CK));
    g.push(ctxt(30, 212, '2열에서 1 의 자리를 1 로.', CK));
    g.push(ctxt(30, 240, '고를 여지가 없으므로', CI, { bold: true }));
    g.push(ctxt(30, 258, '여기서도 경로가 유일하다.', CI, { bold: true }));
    g.push(ctxt(30, 286, 'c 열의 스위치 하나에는', CK));
    g.push(ctxt(30, 304, '입력 2 의 c 제곱 개가 닿고', CK));
    g.push(ctxt(30, 322, '출력 2 의 (n−c) 제곱 개에', CK));
    g.push(ctxt(30, 340, '닿는다. 그 최솟값이 혼잡도다', CK));
    return svg({
        width: W, height: H,
        title: '나비망 F3 과 그 안의 유일한 경로',
        desc: '열 4개 각 8행인 나비망에서 0행 입력에서 5행 출력으로 가는 경로가 목적지의 비트를 차례로 맞추며 유일하게 정해지는 것을 보인 그림',
        body: g.join(''),
    });
})());

/* 6. 베네시망 B3 */
add('mcs-g-benes', (() => {
    const W = 720, H = 410;
    const g = [];
    g.push(txt(W / 2, 22, '베네시망 B~3 — 나비망 둘을 등 맞대어 붙인 것. 열이 여섯이고 가운데에 B~2 두 개가 들어 있다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const x0 = 150, dx = 80, y0 = 66, dy = 34;
    g.push(box(x0 + dx - 24, y0 - 18, dx * 3 + 48, dy * 3 + 36, { stroke: C3, sw: 1.4, dash: '6 4', rx: 6 }));
    g.push(box(x0 + dx - 24, y0 + dy * 4 - 18, dx * 3 + 48, dy * 3 + 36, { stroke: C3, sw: 1.4, dash: '6 4', rx: 6 }));
    const net = columnNet({
        x0, dx, y0, dy, cols: 6, rows: 8,
        xors: [4, 2, 1, 2, 4], r: 9, bold: [], boldCol: C1, sw: 1.1,
    });
    g.push(net.body);
    for (let k = 0; k < 8; k += 1) {
        g.push(ctxt(x0 - 24, y0 + k * dy + 4, String(k), CK, { anchor: 'end' }));
    }
    g.push(ctxt(x0 + dx * 2.5, 42, '위쪽 부분망 B~2', C3, { anchor: 'middle', bold: true }));
    g.push(ctxt(x0 + dx * 2.5, y0 + dy * 7 + 34, '아래쪽 부분망 B~2', C3, { anchor: 'middle', bold: true }));
    g.push(ctxt(x0, y0 + dy * 7 + 34, '새 입력 열', C1, { anchor: 'middle', bold: true }));
    g.push(ctxt(x0 + dx * 5, y0 + dy * 7 + 34, '새 출력 열', C1, { anchor: 'middle', bold: true }));
    g.push(ctxt(30, 352, '첫 열에서 위·아래 어느 부분망으로 보낼지 고를 수 있고, 그 선택 하나만 잘하면 나머지는 귀납법이 해 준다.', CI, { bold: true }));
    g.push(ctxt(30, 372, '행 k 와 행 k+4 의 두 입력은 첫 열 다음에 같은 스위치로 갈 수 있으므로 서로 다른 부분망으로 보내야 한다.', CK));
    g.push(ctxt(30, 390, '출력 쪽도 같다 — 행 k 와 k+4 로 나갈 두 패킷은 서로 다른 부분망에서 와야 한다.', CK));
    return svg({
        width: W, height: H,
        title: '베네시망 B3 의 구조',
        desc: '열 6개 각 8행인 베네시망에서 가운데 두 개의 B2 부분망을 점선으로 표시하고, 새 입력 열과 새 출력 열이 선택의 자유를 주는 것을 적은 그림',
        body: g.join(''),
    });
})());

/* 7. 제약 그래프와 2색 칠하기 */
add('mcs-g-benes-constraint', (() => {
    const W = 720, H = 356;
    const g = [];
    g.push(txt(W / 2, 22, '제약 그래프 — 같은 부분망에 넣을 수 없는 짝을 선으로 잇는다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 17;
    const cx = 250, cy = 172, rad = 96;
    const pos = k => {
        const a = (-90 + k * 45) * Math.PI / 180;
        return [cx + rad * Math.cos(a), cy + rad * Math.sin(a)];
    };
    const E1 = [[0, 4], [1, 5], [2, 6], [3, 7]];
    const E2 = [[0, 1], [2, 6], [5, 7], [3, 4]];
    const up = new Set([0, 3, 5, 2]);
    for (const [u, v] of E1) g.push(eg(pos(u), pos(v), { col: CK, sw: 1.7, r: R, dash: '6 4', bend: 8 }));
    for (const [u, v] of E2) g.push(eg(pos(u), pos(v), { col: CI, sw: 1.7, r: R, bend: -8 }));
    for (let k = 0; k < 8; k += 1) {
        const c = up.has(k) ? C1 : C2;
        g.push(nd(pos(k), String(k), { col: c, fill: c, r: R, lc: CI }));
        const p = pos(k);
        const a = Math.atan2(p[1] - cy, p[0] - cx);
        g.push(ctxt(p[0] + 27 * Math.cos(a), p[1] + 27 * Math.sin(a) + 4, up.has(k) ? '위' : '아래', c,
            { anchor: p[0] < cx - 4 ? 'end' : (p[0] > cx + 4 ? 'start' : 'middle') }));
    }
    g.push(ctxt(24, 78, '점선: 입력 쪽 제약', CK));
    g.push(ctxt(24, 96, '실선: 출력 쪽 제약', CI));
    g.push(ctxt(24, 122, '2 와 6 은 두 이유로', C3));
    g.push(ctxt(24, 140, '갈라져야 한다 — 겹친 선', C3));

    g.push(panel(430, 50, 270, 216, '왜 반드시 2색으로 칠할 수 있는가', ''));
    const t = [
        '점마다 점선이 한 개, 실선이 한 개다.',
        '그러니 어느 점에서 출발해 점선·실선을',
        '번갈아 따라가면 갈림길이 없다.',
        '',
        '따라가다 제자리로 돌아오면 그 고리는',
        '점선과 실선이 번갈아 나오므로',
        '반드시 짝수 길이다. 짝수 고리는',
        '한 칸씩 번갈아 칠할 수 있다.',
        '',
        '겹친 선은 두 점을 왕복할 뿐이라',
        '길이 2 인 짝수 고리다.',
    ];
    t.forEach((s, i) => g.push(ctxt(446, 90 + i * 16, s, i === 6 || i === 7 ? CI : CK, { bold: i === 6 || i === 7 })));
    g.push(txt(W / 2, 314, '위쪽 부분망으로 보낼 패킷과 아래쪽으로 보낼 패킷을 두 색으로 나눈 것이다. 색이 정해지면 첫 열과 마지막 열의 배선이 결정된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 334, '남은 것은 절반 크기 문제 둘이고, 그것은 귀납 가정이 해결해 준다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '베네시망 라우팅의 제약 그래프와 2색 칠하기',
        desc: '패킷 8개를 점으로 두고 입력 제약을 점선 출력 제약을 실선으로 그린 뒤, 각 점에 선이 두 종류 하나씩이라 2색으로 칠할 수 있음을 설명한 그림',
        body: g.join(''),
    });
})());

/* ================================================================== *
 * 14장 — 단순 그래프
 * ================================================================== */

/* 1. 방향을 지우면 무엇이 달라지는가 */
add('mcs-g-undirected', (() => {
    const W = 720, H = 394;
    const g = [];
    g.push(txt(W / 2, 24, '방향을 지우면 무엇이 달라지는가 — 12장의 방향 그래프와 이 장의 단순 그래프',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 16;
    const shape = ox => ({ a: [ox + 70, 118], b: [ox + 180, 118], c: [ox + 180, 208], d: [ox + 70, 208] });

    g.push(panel(20, 40, 330, 274, '12장: 방향 그래프', '화살표가 있고 자기 고리도 된다'));
    const L = shape(20);
    g.push(dg(L.a, L.b, { col: CK, r: R, bend: 9 }));
    g.push(dg(L.b, L.a, { col: CK, r: R, bend: 9 }));
    g.push(dg(L.b, L.c, { col: CK, r: R }));
    g.push(dg(L.c, L.d, { col: CK, r: R }));
    g.push(dg(L.d, L.b, { col: CK, r: R }));
    g.push(selfLoop(L.a, { col: C2, r: R }));
    for (const [k, p] of Object.entries(L)) g.push(nd(p, k, { col: CI, r: R }));
    g.push(table(70, 230, ['정점', 'indeg', 'outdeg'], [['a', '2', '2'], ['b', '2', '2']], { cw: 62, rh: 22 }));

    g.push(panel(370, 40, 330, 274, '14장: 단순 그래프', '방향이 없고 자기 고리와 겹친 간선이 없다'));
    const Rt = shape(370);
    g.push(eg(Rt.a, Rt.b, { col: C1, sw: 2.4, r: R }));
    g.push(eg(Rt.b, Rt.c, { col: CK, r: R }));
    g.push(eg(Rt.c, Rt.d, { col: CK, r: R }));
    g.push(eg(Rt.d, Rt.b, { col: CK, r: R }));
    for (const [k, p] of Object.entries(Rt)) g.push(nd(p, k, { col: CI, r: R }));
    g.push(table(420, 230, ['정점', 'deg'], [['a', '1'], ['b', '3']], { cw: 62, rh: 22 }));
    g.push(ctxt(556, 248, '자기 고리가 없어졌고', C2));
    g.push(ctxt(556, 268, '두 방향 간선 한 쌍이', C1));
    g.push(ctxt(556, 288, '간선 하나가 되었다', C1));

    g.push(txt(W / 2, 340, '차수가 하나로 합쳐지고, 간선은 두 원소 집합 {u, v} 로 적을 수 있다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 360, '순환의 정의도 바뀐다 — a b a 는 같은 간선을 왕복한 것이라 순환으로 세지 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 380, '그래서 단순 그래프의 순환은 길이 3 이상이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '방향 그래프와 단순 그래프의 차이',
        desc: '같은 그림에서 화살표와 자기 고리를 지우면 진입차수와 진출차수가 차수 하나로 합쳐지고 간선 수가 줄어드는 것을 보이는 그림',
        body: g.join(''),
    });
})());

/* 2. 이름이 붙은 그래프들 */
add('mcs-g-common-graphs', (() => {
    const W = 720, H = 420;
    const g = [];
    g.push(txt(W / 2, 24, '이름이 붙은 그래프들 — 이 뒤로 계속 예제로 쓰인다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 11;
    const circle = (cx, cy, rad, k, start = -90) => {
        const out = [];
        for (let i = 0; i < k; i += 1) {
            const a = (start + i * 360 / k) * Math.PI / 180;
            out.push([cx + rad * Math.cos(a), cy + rad * Math.sin(a)]);
        }
        return out;
    };

    // 윗줄: K5, C5, 경로 P5
    g.push(panel(20, 40, 212, 158, '완전 그래프 K~5', '모든 짝이 인접. 간선 10개'));
    const k5 = circle(126, 130, 46, 5);
    for (let i = 0; i < 5; i += 1) for (let j = i + 1; j < 5; j += 1) g.push(eg(k5[i], k5[j], { col: CK, sw: 1.3, r: R }));
    k5.forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: R })));
    g.push(ctxt(126, 190, '차수는 모두 4', CK, { anchor: 'middle' }));

    g.push(panel(254, 40, 212, 158, '순환 그래프 C~5', '고리 하나. 간선 5개'));
    const c5 = circle(360, 130, 46, 5);
    for (let i = 0; i < 5; i += 1) g.push(eg(c5[i], c5[(i + 1) % 5], { col: CK, sw: 1.6, r: R }));
    c5.forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: R })));
    g.push(ctxt(360, 190, '차수는 모두 2', CK, { anchor: 'middle' }));

    g.push(panel(488, 40, 212, 158, '경로 그래프 P~5', '한 줄. 간선 4개'));
    const p5 = [0, 1, 2, 3, 4].map(i => [520 + i * 37, 126]);
    for (let i = 0; i < 4; i += 1) g.push(eg(p5[i], p5[i + 1], { col: CK, sw: 1.6, r: R }));
    p5.forEach(p => g.push(nd(p, '', { col: C3, fill: C3, r: R })));
    g.push(ctxt(594, 156, '양 끝만 차수 1', CK, { anchor: 'middle' }));
    g.push(ctxt(594, 190, '원서는 L~5 로 적는다', CK, { anchor: 'middle' }));

    // 아랫줄: K33, 초입방체 Q3
    g.push(panel(20, 210, 210, 178, '완전 이분 K~{3,3}', '왼쪽 3 · 오른쪽 3'));
    const kl = [0, 1, 2].map(i => [70, 290 + i * 34]);
    const kr = [0, 1, 2].map(i => [180, 290 + i * 34]);
    for (const a of kl) for (const b of kr) g.push(eg(a, b, { col: CK, sw: 1.2, r: R }));
    kl.forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: R })));
    kr.forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: R })));

    g.push(panel(242, 210, 458, 178, '초입방체 Q~3', ''));
    const cube = {
        '000': [300, 362], '100': [376, 362], '010': [300, 286], '110': [376, 286],
        '001': [334, 338], '101': [410, 338], '011': [334, 262], '111': [410, 262],
    };
    const bits = Object.keys(cube);
    for (const u of bits) {
        for (const v of bits) {
            if (u < v) {
                let d = 0;
                for (let i = 0; i < 3; i += 1) if (u[i] !== v[i]) d += 1;
                if (d === 1) g.push(eg(cube[u], cube[v], { col: CK, sw: 1.3, r: R }));
            }
        }
    }
    for (const u of bits) g.push(nd(cube[u], '', { col: C3, fill: C3, r: R }));
    for (const u of bits) {
        const p = cube[u];
        const right = p[0] >= 340;
        g.push(ctxt(p[0] + (right ? 15 : -15), p[1] + 4, u, CK, { anchor: right ? 'start' : 'end' }));
    }
    g.push(ctxt(478, 258, '꼭짓점은 비트 3개', CI, { bold: true }));
    g.push(ctxt(478, 280, '비트 하나만 다르면 인접', CI, { bold: true }));
    g.push(ctxt(478, 308, '정점 2 의 n 제곱 개', CK));
    g.push(ctxt(478, 330, '차수는 모두 n', CK));
    g.push(ctxt(478, 352, '간선 n · 2 의 (n−1) 제곱 개', CK));
    g.push(ctxt(478, 374, '지름도 n — 비트를 하나씩 고친다', CK));
    g.push(txt(W / 2, 408, '완전 그래프와 간선이 없는 빈 그래프가 양 극단이고, 순환·경로·초입방체는 그 사이에서 규칙적인 것들이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '이름이 붙은 그래프들',
        desc: '완전 그래프 K5, 순환 C5, 경로 P5, 완전 이분 K33, 초입방체 Q3, 빈 그래프를 나란히 그린 그림',
        body: g.join(''),
    });
})());

/* 3. 동형사상 */
add('mcs-g-isomorphism', (() => {
    const W = 720, H = 350;
    const g = [];
    g.push(txt(W / 2, 24, '동형사상 — 그림이 달라도 같은 그래프인가',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 16;
    g.push(panel(20, 40, 200, 190, '그래프 G', '정점 4개 · 간선 5개'));
    const G = { a: [70, 110], b: [170, 110], c: [170, 200], d: [70, 200] };
    for (const [u, v] of [['a', 'b'], ['b', 'c'], ['c', 'd'], ['d', 'a'], ['a', 'c']]) {
        g.push(eg(G[u], G[v], { col: CK, sw: 1.6, r: R }));
    }
    for (const [k, p] of Object.entries(G)) g.push(nd(p, k, { col: C1, fill: C1, r: R }));

    g.push(panel(254, 40, 200, 190, '그래프 H', 'G 를 90° 돌린 것'));
    const Hh = { 1: [304, 110], 2: [404, 110], 3: [404, 200], 4: [304, 200] };
    for (const [u, v] of [['1', '2'], ['2', '3'], ['3', '4'], ['4', '1'], ['2', '4']]) {
        g.push(eg(Hh[u], Hh[v], { col: CK, sw: 1.6, r: R }));
    }
    for (const [k, p] of Object.entries(Hh)) g.push(nd(p, k, { col: C2, fill: C2, r: R }));

    g.push(panel(488, 40, 212, 190, '대응 f 와 차수', ''));
    g.push(table(510, 68, ['G', 'deg', 'H', 'deg'], [
        ['a', '3', '2', '3'], ['b', '2', '3', '2'], ['c', '3', '4', '3'], ['d', '2', '1', '2'],
    ], { cw: 42, rh: 24, hlCol: [1, 3] }));
    g.push(ctxt(594, 208, '차수가 맞고 간선 다섯 쌍도', CK, { anchor: 'middle' }));
    g.push(ctxt(594, 224, '모두 대응한다', CK, { anchor: 'middle' }));

    g.push(panel(20, 246, 680, 84, '동형이 아님을 보이려면 보존되는 성질 하나를 찾으면 된다', ''));
    const st = [[120, 300], [80, 278], [80, 322], [160, 278], [160, 322]];
    g.push(eg(st[0], st[1], { col: C3, sw: 1.5, r: 10 }));
    g.push(eg(st[0], st[2], { col: C3, sw: 1.5, r: 10 }));
    g.push(eg(st[0], st[3], { col: C3, sw: 1.5, r: 10 }));
    g.push(eg(st[0], st[4], { col: C3, sw: 1.5, r: 10 }));
    st.forEach((p, i) => g.push(nd(p, '', { col: i === 0 ? C3 : CK, fill: i === 0 ? C3 : null, r: 10 })));
    g.push(ctxt(196, 304, '차수 목록 (4,1,1,1,1)', C3));
    const pa = [0, 1, 2, 3, 4].map(i => [400 + i * 34, 300]);
    for (let i = 0; i < 4; i += 1) g.push(eg(pa[i], pa[i + 1], { col: C1, sw: 1.5, r: 10 }));
    pa.forEach(p => g.push(nd(p, '', { col: C1, r: 10 })));
    g.push(ctxt(560, 304, '차수 목록 (2,2,2,1,1)', C1));
    g.push(ctxt(360, 304, '≠', CI, { anchor: 'middle', bold: true }));
    return svg({
        width: W, height: H,
        title: '동형사상과 동형 불변량',
        desc: '같은 그래프를 다르게 그린 두 그림과 그 사이의 대응, 그리고 차수 목록이 달라 동형이 될 수 없는 두 그래프를 보인 그림',
        body: g.join(''),
    });
})());

/* 4. 이분 그래프와 매칭, 그리고 병목 */
add('mcs-g-matching', (() => {
    const W = 720, H = 360;
    const g = [];
    g.push(txt(W / 2, 24, '매칭 — 왼쪽을 모두 덮을 수 있는가',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 16;
    g.push(panel(20, 40, 400, 268, '매칭이 있는 경우', '지원자마다 자기가 할 수 있는 일자리 하나씩'));
    const ap = ['a', 'b', 'c', 'd'].map((k, i) => [110, 110 + i * 50]);
    const jb = [1, 2, 3, 4, 5].map((k, i) => [320, 92 + i * 44]);
    const edges = [[0, 0], [0, 1], [1, 1], [1, 2], [2, 2], [2, 3], [3, 3], [3, 4]];
    const match = new Set(['0-0', '1-1', '2-2', '3-3']);
    for (const [i, j] of edges) {
        const on = match.has(`${i}-${j}`);
        g.push(eg(ap[i], jb[j], { col: on ? C3 : CG, sw: on ? 3.4 : 1.5, r: R }));
    }
    ['a', 'b', 'c', 'd'].forEach((k, i) => g.push(nd(ap[i], k, { col: C1, fill: C1, r: R })));
    [1, 2, 3, 4, 5].forEach((k, i) => g.push(nd(jb[i], String(k), { col: C2, fill: C2, r: R })));
    g.push(ctxt(60, 88, '지원자', C1, { bold: true }));
    g.push(ctxt(360, 76, '일자리', C2, { bold: true }));
    g.push(ctxt(40, 296, '굵은 간선 넷이 매칭이다. 지원자마다 차수가 2 이상이고 일자리마다 차수가 2 이하다', C3));

    g.push(panel(440, 40, 260, 268, '병목이 있는 경우', '매칭이 있을 수 없다'));
    const ap2 = ['p', 'q', 'r'].map((k, i) => [510, 118 + i * 46]);
    const jb2 = [1, 2].map((k, i) => [640, 141 + i * 46]);
    for (const a of ap2) for (const b of jb2) g.push(eg(a, b, { col: C2, sw: 1.5, r: R }));
    ['p', 'q', 'r'].forEach((k, i) => g.push(nd(ap2[i], k, { col: C2, fill: C2, r: R })));
    [1, 2].forEach((k, i) => g.push(nd(jb2[i], String(k), { col: CK, r: R })));
    g.push(box(486, 96, 50, 138, { stroke: C2, sw: 1.6, dash: '6 4', rx: 8 }));
    g.push(ctxt(570, 254, 'S = {p, q, r}', C2, { anchor: 'middle', bold: true }));
    g.push(ctxt(570, 272, 'N(S) = {1, 2}', C2, { anchor: 'middle', bold: true }));
    g.push(ctxt(570, 292, '|S| = 3 > 2 = |N(S)|', CI, { anchor: 'middle', bold: true }));

    g.push(txt(W / 2, 332, '병목이 하나라도 있으면 매칭이 없다는 것은 쉽다. 홀의 정리는 그 역 — 병목이 하나도 없으면 매칭이 반드시 있다 — 을 말한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 352, '부분집합이 지수 개나 되는데도 조건이 충분하다는 것이 이 정리의 힘이고, 증명은 강한 귀납법이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '이분 그래프의 매칭과 병목',
        desc: '지원자 4명과 일자리 5개 사이의 매칭을 굵은 간선으로 보이고, 세 지원자가 일 둘만 할 수 있는 병목의 예를 함께 그린 그림',
        body: g.join(''),
    });
})());

/* 5. 색칠과 채색수 */
add('mcs-g-coloring', (() => {
    const W = 720, H = 364;
    const g = [];
    g.push(txt(W / 2, 24, '색칠 — 인접한 두 정점은 다른 색이어야 한다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 26;
    g.push(panel(20, 40, 380, 262, '시험 시간표: 채색수가 3 이다', '간선은 함께 듣는 학생이 있다는 뜻'));
    const V = {
        '이산수학': [210, 178], '확률': [110, 118], '물리': [310, 118],
        '알고리즘': [110, 245], '기하': [310, 245],
    };
    const E = [['확률', '물리'], ['확률', '이산수학'], ['물리', '이산수학'],
        ['알고리즘', '기하'], ['알고리즘', '이산수학'], ['기하', '이산수학']];
    for (const [u, v] of E) g.push(eg(V[u], V[v], { col: CK, sw: 1.6, r: R }));
    const col = { '이산수학': C1, '확률': C2, '물리': C3, '알고리즘': C2, '기하': C3 };
    for (const [k, p] of Object.entries(V)) {
        g.push(nd(p, '', { col: col[k], fill: col[k], r: R }));
        g.push(ctxt(p[0], p[1] + 4, k, CI, { anchor: 'middle' }));
    }
    g.push(ctxt(36, 290, '삼각형이 둘 있어 2색으로는 안 되고, 3색으로 실제로 칠했다', C1));

    g.push(panel(420, 40, 280, 262, '별 그래프: 최대 차수 6, 채색수 2', '차수 한계는 아주 느슨할 수 있다'));
    const cx = 560, cy = 182;
    const leaves = [];
    for (let i = 0; i < 6; i += 1) {
        const a = (i * 60 - 90) * Math.PI / 180;
        leaves.push([cx + 78 * Math.cos(a), cy + 78 * Math.sin(a)]);
    }
    leaves.forEach(p => g.push(eg([cx, cy], p, { col: CK, sw: 1.5, r: 15 })));
    g.push(nd([cx, cy], '', { col: C1, fill: C1, r: 15 }));
    leaves.forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: 15 })));
    g.push(ctxt(436, 290, '차수 한계 정리는 7색을 보장하지만 2색으로 끝난다', C2));

    g.push(txt(W / 2, 328, '최대 차수가 Δ 인 그래프는 언제나 Δ+1 색으로 칠할 수 있다. 이 상한이 딱 맞는 것은 완전 그래프이고, 별 그래프에서는 아주 헐렁하다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 348, '최소 색 수를 실제로 구하는 일은 어렵다 — 3색으로 칠할 수 있는지 판정하는 것만으로도 SAT 만큼 어렵다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '그래프 색칠과 채색수',
        desc: '삼각형 둘이 있는 시험 시간표 그래프를 3색으로 칠한 것과, 최대 차수 6 이지만 2색으로 칠해지는 별 그래프를 함께 보인 그림',
        body: g.join(''),
    });
})());

/* 6. 홀수 순환과 2색 칠하기 */
add('mcs-g-odd-cycle', (() => {
    const W = 720, H = 350;
    const g = [];
    g.push(txt(W / 2, 24, '2색으로 칠할 수 있는 것과 홀수 순환이 없는 것은 같은 말이다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 14;
    const ring = (cx, cy, rad, k) => {
        const out = [];
        for (let i = 0; i < k; i += 1) {
            const a = (-90 + i * 360 / k) * Math.PI / 180;
            out.push([cx + rad * Math.cos(a), cy + rad * Math.sin(a)]);
        }
        return out;
    };

    g.push(panel(20, 40, 200, 200, '짝수 순환 C~4', '번갈아 칠하면 맞는다'));
    const c4 = ring(120, 145, 52, 4);
    for (let i = 0; i < 4; i += 1) g.push(eg(c4[i], c4[(i + 1) % 4], { col: CK, sw: 1.6, r: R }));
    c4.forEach((p, i) => g.push(nd(p, '', { col: i % 2 ? C2 : C1, fill: i % 2 ? C2 : C1, r: R })));
    g.push(ctxt(120, 222, '어긋나는 간선이 없다', CK, { anchor: 'middle' }));

    g.push(panel(240, 40, 200, 200, '홀수 순환 C~5', '한 바퀴 돌면 어긋난다'));
    const c5 = ring(340, 145, 52, 5);
    for (let i = 0; i < 5; i += 1) {
        const bad = i === 4;
        g.push(eg(c5[i], c5[(i + 1) % 5], { col: bad ? C2 : CK, sw: bad ? 3.2 : 1.6, r: R }));
    }
    c5.forEach((p, i) => g.push(nd(p, '', { col: i === 4 ? C1 : (i % 2 ? C2 : C1), fill: i === 4 ? C1 : (i % 2 ? C2 : C1), r: R })));
    g.push(ctxt(340, 222, '굵은 간선의 양 끝이 같은 색', C2, { anchor: 'middle' }));

    g.push(panel(460, 40, 240, 200, '거꾸로 가는 논증', '거리의 짝홀로 칠해 본다'));
    const r0 = [500, 100];
    const l1 = [[560, 100], [560, 150]];
    const l2 = [[640, 100], [640, 150]];
    g.push(eg(r0, l1[0], { col: CK, sw: 1.5, r: 13 }));
    g.push(eg(r0, l1[1], { col: CK, sw: 1.5, r: 13 }));
    g.push(eg(l1[0], l2[0], { col: CK, sw: 1.5, r: 13 }));
    g.push(eg(l1[1], l2[1], { col: CK, sw: 1.5, r: 13 }));
    g.push(eg(l2[0], l2[1], { col: C2, sw: 3.2, r: 13 }));
    g.push(nd(r0, 'r', { col: C1, fill: C1, r: 13 }));
    l1.forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: 13 })));
    l2.forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: 13 })));
    g.push(ctxt(478, 186, '같은 색 두 정점 사이에 간선이', C2));
    g.push(ctxt(478, 204, '있으면 그 간선과 두 보행이', C2));
    g.push(ctxt(478, 222, '홀수 길이 닫힌 보행을 만든다', C2));

    g.push(txt(W / 2, 274, '셋이 서로 같은 말이다 — 홀수 길이 순환이 있다 · 2색으로 칠할 수 없다 · 홀수 길이 닫힌 보행이 있다.',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 294, '오른쪽 그림이 ‘2색이 안 되면 홀수 닫힌 보행이 있다’를 보이는 자리이고, 거기서 순환까지 가는 데는 정렬성 원리를 쓴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 320, '이 정리 덕분에 13장 베네시망에서 급히 만들어 쓴 2색 칠하기 보조정리가 일반적인 사실의 특별한 경우가 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '홀수 순환과 2색 칠하기의 동등성',
        desc: '짝수 순환은 번갈아 칠해지고 홀수 순환은 어긋나며, 거리의 짝홀로 칠했을 때 같은 색 사이의 간선이 홀수 닫힌 보행을 만드는 것을 보인 그림',
        body: g.join(''),
    });
})());

/* 7. 오일러 회로 */
add('mcs-g-euler-tour', (() => {
    const W = 720, H = 320;
    const g = [];
    g.push(txt(W / 2, 24, '오일러 회로 — 모든 간선을 정확히 한 번씩 지나 제자리로 돌아오기',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 16;
    g.push(panel(20, 40, 330, 220, '모든 차수가 짝수: 회로가 있다', '나비 모양. 간선 6개'));
    const A = { a: [70, 100], b: [70, 200], c: [180, 150], d: [290, 100], e: [290, 200] };
    const Ae = [['a', 'b'], ['b', 'c'], ['c', 'a'], ['c', 'd'], ['d', 'e'], ['e', 'c']];
    Ae.forEach(([u, v], i) => g.push(eg(A[u], A[v], { col: C1, sw: 2.2, r: R })));
    const order = { a: '2', b: '2', c: '4', d: '2', e: '2' };
    for (const [k, p] of Object.entries(A)) {
        g.push(nd(p, k, { col: CI, r: R }));
        g.push(ctxt(p[0], p[1] - 24, `deg ${order[k]}`, CK, { anchor: 'middle' }));
    }
    g.push(ctxt(36, 240, '회로 하나: a b c d e c a — 간선 여섯 개를 한 번씩 쓴다', C1));

    g.push(panel(370, 40, 330, 220, '홀수 차수 정점이 둘: 회로는 없다', '열린 보행만 있다. 간선 5개'));
    const B = { a: [430, 100], b: [560, 100], c: [560, 200], d: [430, 200] };
    for (const [u, v] of [['a', 'b'], ['b', 'c'], ['c', 'd'], ['d', 'a'], ['a', 'c']]) {
        g.push(eg(B[u], B[v], { col: C2, sw: 2.2, r: R }));
    }
    const dg2 = { a: '3', b: '2', c: '3', d: '2' };
    for (const [k, p] of Object.entries(B)) {
        g.push(nd(p, k, { col: k === 'a' || k === 'c' ? C2 : CI, fill: k === 'a' || k === 'c' ? C2 : null, r: R }));
        g.push(ctxt(p[0] + (k === 'b' || k === 'c' ? 24 : -24), p[1] + 4, `deg ${dg2[k]}`, CK,
            { anchor: k === 'b' || k === 'c' ? 'start' : 'end' }));
    }
    g.push(ctxt(386, 240, '열린 보행: a b c d a c — 시작과 끝이 홀수 차수 정점이다', C2));

    g.push(txt(W / 2, 286, '이유는 한 줄이다 — 회로가 정점을 들어왔다 나갈 때마다 간선 두 개를 쓰므로 모든 차수가 짝수여야 한다.',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 306, '거꾸로 짝수이면 회로가 있다는 것은 닫힌 보행을 찾아 끼워 넣기를 되풀이해 증명한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '오일러 회로가 있는 그래프와 없는 그래프',
        desc: '모든 차수가 짝수인 나비 모양 그래프에는 오일러 회로가 있고, 홀수 차수 정점이 둘인 그래프에는 열린 오일러 보행만 있다는 것을 보인 그림',
        body: g.join(''),
    });
})());

/* 8. 숲과 나무, 그리고 신장나무 */
add('mcs-g-tree-facts', (() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '숲과 나무 — 순환이 없는 그래프, 그중 연결된 것',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 12;
    g.push(panel(20, 40, 220, 230, '나무: 정점 9 · 간선 8', '잎(차수 1)이 다섯 개'));
    const T = {
        a: [130, 82], b: [80, 128], c: [180, 128], d: [50, 176], e: [110, 176],
        f: [155, 176], g: [210, 176], h: [90, 226], i: [130, 226],
    };
    const Te = [['a', 'b'], ['a', 'c'], ['b', 'd'], ['b', 'e'], ['c', 'f'], ['c', 'g'], ['e', 'h'], ['e', 'i']];
    for (const [u, v] of Te) g.push(eg(T[u], T[v], { col: CK, sw: 1.5, r: R }));
    const leafSet = new Set(['d', 'f', 'g', 'h', 'i']);
    for (const [k, p] of Object.entries(T)) {
        g.push(nd(p, '', { col: leafSet.has(k) ? C3 : C1, fill: leafSet.has(k) ? C3 : C1, r: R }));
    }
    g.push(ctxt(36, 254, '초록이 잎이다. 정점 수 = 간선 수 + 1', C3));

    g.push(panel(254, 40, 200, 230, '숲: 나무 두 그루', '연결 요소가 2개'));
    const F1 = [[300, 100], [340, 140], [300, 180], [360, 200]];
    const F2 = [[400, 110], [400, 170], [430, 215]];
    for (const [i, j] of [[0, 1], [1, 2], [1, 3]]) g.push(eg(F1[i], F1[j], { col: CK, sw: 1.5, r: R }));
    for (const [i, j] of [[0, 1], [1, 2]]) g.push(eg(F2[i], F2[j], { col: CK, sw: 1.5, r: R }));
    [...F1].forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: R })));
    [...F2].forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: R })));
    g.push(ctxt(270, 254, '정점 7 · 간선 5 · 요소 2', CK));

    g.push(panel(468, 40, 232, 230, '신장나무', '연결 그래프 안에 늘 있다'));
    const S = [[520, 90], [640, 90], [520, 170], [640, 170], [580, 240]];
    const all = [[0, 1], [0, 2], [1, 3], [2, 3], [2, 4], [3, 4], [0, 3]];
    const tree = new Set(['0-1', '0-2', '2-3', '2-4']);
    for (const [i, j] of all) {
        const on = tree.has(`${i}-${j}`);
        g.push(eg(S[i], S[j], { col: on ? C3 : CG, sw: on ? 3.2 : 1.4, r: R }));
    }
    S.forEach(p => g.push(nd(p, '', { col: CI, r: R })));
    g.push(ctxt(484, 254, '굵은 간선 넷이 정점 다섯을 모두 잇는다', C3));

    g.push(txt(W / 2, 296, '나무의 특징들은 서로 같은 말이다 — 경로가 유일하다 · 간선을 지우면 끊긴다 · 간선을 더하면 순환이 생긴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 316, '그리고 정점 수가 간선 수보다 정확히 하나 많다. 이 마지막 것이 계산에 가장 자주 쓰인다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '나무와 숲, 그리고 신장나무',
        desc: '잎이 다섯인 나무, 나무 두 그루로 된 숲, 연결 그래프 안에서 굵게 표시한 신장나무를 나란히 보인 그림',
        body: g.join(''),
    });
})());

/* ================================================================== *
 * 15장 — 평면 그래프
 * ================================================================== */

/* 1. K5 와 K33 — 그리고 간선 하나를 빼면 그려진다 */
add('mcs-g-planar-k5-k33', (() => {
    const W = 720, H = 400;
    const g = [];
    g.push(txt(W / 2, 24, '개집 세 채와 집 세 채, 그리고 문어 다섯 마리 — 간선 하나만 빼면 그려진다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 13;

    g.push(panel(20, 40, 330, 166, '완전 이분 K~{3,3}', '어떻게 그려도 선이 엇갈린다'));
    const t3 = [0, 1, 2].map(i => [110 + i * 70, 108]);
    const b3 = [0, 1, 2].map(i => [110 + i * 70, 178]);
    for (const u of t3) for (const v of b3) g.push(eg(u, v, { col: CK, sw: 1.2, r: R }));
    t3.forEach((p, i) => g.push(nd(p, ['1', '2', '3'][i], { col: C1, fill: C1, r: R })));
    b3.forEach((p, i) => g.push(nd(p, ['a', 'b', 'c'][i], { col: C2, fill: C2, r: R })));

    g.push(panel(370, 40, 330, 166, '완전 그래프 K~5', '엇갈림이 다섯 군데 남는다'));
    const k5 = [];
    for (let i = 0; i < 5; i += 1) {
        const a = (-90 + i * 72) * Math.PI / 180;
        k5.push([535 + 52 * Math.cos(a), 142 + 52 * Math.sin(a)]);
    }
    for (let i = 0; i < 5; i += 1) for (let j = i + 1; j < 5; j += 1) g.push(eg(k5[i], k5[j], { col: CK, sw: 1.2, r: R }));
    k5.forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: R })));

    g.push(panel(20, 216, 330, 166, 'K~{3,3} 에서 3–c 를 뺀 것', ''));
    const KB = { '3': [85, 300], '1': [158, 300], 'a': [230, 256], 'c': [230, 300], 'b': [230, 344], '2': [300, 300] };
    for (const [u, v] of [['1', 'a'], ['1', 'b'], ['1', 'c'], ['2', 'a'], ['2', 'b'], ['2', 'c'], ['3', 'a'], ['3', 'b']]) {
        g.push(eg(KB[u], KB[v], { col: CK, sw: 1.5, r: R }));
    }
    for (const [k, p] of Object.entries(KB)) {
        const left = k === '1' || k === '2' || k === '3';
        g.push(nd(p, k, { col: left ? C1 : C2, fill: left ? C1 : C2, r: R }));
    }
    g.push(ctxt(30, 368, '3–c 만 없다. 직선만으로 엇갈림 없이 그려진다', C3));

    g.push(panel(370, 216, 330, 166, 'K~5 에서 4–5 를 뺀 것', '하나는 삼각형 안, 하나는 밖에 둔다'));
    const T = [[520, 266], [463, 356], [577, 356]];
    const inn = [520, 326];
    const outn = [650, 306];
    for (let i = 0; i < 3; i += 1) g.push(eg(T[i], T[(i + 1) % 3], { col: CK, sw: 1.5, r: R }));
    for (const p of T) g.push(eg(inn, p, { col: C3, sw: 1.6, r: R }));
    g.push(eg(outn, T[0], { col: C2, sw: 1.6, r: R }));
    g.push(eg(outn, T[2], { col: C2, sw: 1.6, r: R }));
    g.push(`<path d="M${r2(outn[0])} ${r2(outn[1] + R)} C${r2(outn[0] + 22)} ${r2(outn[1] + 80)} ${r2(T[1][0] + 67)} ${r2(T[1][1] + 36)} ${r2(T[1][0] - 8)} ${r2(T[1][1] + 10)}" fill="none" stroke="var(--s2)" stroke-width="1.6"/>`);
    T.forEach((p, i) => g.push(nd(p, ['1', '2', '3'][i], { col: CI, r: R })));
    g.push(nd(inn, '4', { col: C3, fill: C3, r: R }));
    g.push(nd(outn, '5', { col: C2, fill: C2, r: R }));

    g.push(txt(W / 2, 396, '위의 둘은 평면에 그릴 수 없다. 이 장에서 오일러 공식으로 그것을 증명한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: 'K5 와 K33 은 평면이 아니지만 간선 하나를 빼면 평면이다',
        desc: '엇갈림이 남는 K33 과 K5 의 그림, 그리고 각각에서 간선 하나를 뺀 뒤 엇갈림 없이 그린 그림',
        body: g.join(''),
    });
})());

/* 2. 면과 이산면 */
add('mcs-g-faces', (() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '면 — 곡선이 평면을 나누어 만드는 조각. 그 경계를 닫힌 보행으로 적은 것이 이산면이다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 15;
    const a = [200, 80], b = [110, 250], c = [290, 250], d = [200, 186];
    for (const [u, v] of [[a, b], [b, c], [c, a], [a, d], [b, d], [c, d]]) {
        g.push(eg(u, v, { col: CK, sw: 1.7, r: R }));
    }
    g.push(ctxt(166, 170, 'I', C1, { bold: true, anchor: 'middle' }));
    g.push(ctxt(200, 232, 'II', C1, { bold: true, anchor: 'middle' }));
    g.push(ctxt(234, 170, 'III', C1, { bold: true, anchor: 'middle' }));
    g.push(ctxt(60, 106, 'IV — 바깥면', C2, { bold: true }));
    g.push(nd(a, 'a', { col: CI, r: R }));
    g.push(nd(b, 'b', { col: CI, r: R }));
    g.push(nd(c, 'c', { col: CI, r: R }));
    g.push(nd(d, 'd', { col: C3, fill: C3, r: R }));

    g.push(panel(360, 46, 340, 226, '이산면 네 개', '각 면의 경계를 정점 수열로 적었다'));
    const rows = [['I', 'a b d a', '3'], ['II', 'b c d b', '3'], ['III', 'a d c a', '3'], ['IV', 'a b c a', '3']];
    g.push(table(400, 82, ['면', '경계 닫힌 보행', '길이'], rows, { cws: [46, 148, 52], rh: 26 }));
    g.push(ctxt(400, 236, 'v − e + f = 4 − 6 + 4 = 2', CI, { bold: true }));
    g.push(ctxt(400, 258, '간선마다 경계에 정확히 두 번 나온다', C2));

    g.push(txt(W / 2, 300, '바깥면도 다른 면과 다를 것이 없다. 구 위에 그린 뒤 어느 면을 뚫어 펼치는지에 따라 어느 면이든 바깥이 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 320, '그래서 평면 배치에는 바깥면이라는 것이 아예 없다. 이산면들의 모음만 있다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '평면 그림의 면과 이산면',
        desc: '정점 4개 간선 6개인 평면 그림의 네 면에 번호를 붙이고 각 면의 경계 닫힌 보행을 표로 적은 그림',
        body: g.join(''),
    });
})());

/* 3. 다리와 동글 — 면 경계가 순환이 아닌 경우 */
add('mcs-g-bridge-dongle', (() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '면 경계가 늘 순환은 아니다 — 다리와 동글이 있으면 같은 간선을 두 번 지난다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 14;

    g.push(panel(20, 40, 330, 200, '다리(bridge)', '자른 간선 c–e 가 한 면의 경계에 두 번 나온다'));
    const A = { a: [90, 108], b: [180, 108], c: [180, 194], d: [90, 194], e: [250, 194], f: [312, 152], gg: [312, 226] };
    for (const [u, v] of [['a', 'b'], ['b', 'c'], ['c', 'd'], ['d', 'a'], ['e', 'f'], ['f', 'gg'], ['gg', 'e']]) {
        g.push(eg(A[u], A[v], { col: CK, sw: 1.6, r: R }));
    }
    g.push(eg(A.c, A.e, { col: C2, sw: 3.2, r: R }));
    for (const [k, p] of Object.entries(A)) g.push(nd(p, k === 'gg' ? 'g' : k, { col: CI, r: R }));
    g.push(ctxt(30, 258, '바깥면: a b c e f g e c d a — c–e 가 두 번 나온다', C2));

    g.push(panel(370, 40, 330, 200, '동글(dongle)', '다리로만 된 나무가 면 안에 달려 있다'));
    const B = { p: [440, 108], q: [600, 108], r: [600, 214], s: [440, 214], x: [540, 161], y: [492, 132], z: [492, 194] };
    for (const [u, v] of [['p', 'q'], ['q', 'r'], ['r', 's'], ['s', 'p']]) {
        g.push(eg(B[u], B[v], { col: CK, sw: 1.6, r: R }));
    }
    for (const [u, v] of [['r', 'x'], ['x', 'y'], ['x', 'z']]) {
        g.push(eg(B[u], B[v], { col: C2, sw: 3.2, r: R }));
    }
    for (const [k, p] of Object.entries(B)) g.push(nd(p, k, { col: CI, r: R }));
    g.push(ctxt(378, 258, '안쪽 면: p q r x y x z x r s p — 세 간선이 모두 두 번', C2));

    g.push(txt(W / 2, 286, '그래서 면의 경계를 순환이라 하지 못하고 닫힌 보행이라 해야 한다. 다리는 한 면의 경계에 두 번 나오는 간선이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 306, '다리가 아닌 간선은 서로 다른 두 면의 경계에 한 번씩 나온다. 어느 쪽이든 간선마다 두 번이 지켜진다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 326, '이 사실이 뒤에서 간선 수의 상한을 얻는 열쇠다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '다리와 동글이 있는 평면 그림',
        desc: '자른 간선이 한 면의 경계에 두 번 나오는 예와, 나무 모양 동글의 간선들이 모두 두 번 나오는 예를 보인 그림',
        body: g.join(''),
    });
})());

/* 4. 평면 배치의 두 생성자 */
add('mcs-g-embed-constructors', (() => {
    const W = 720, H = 344;
    const g = [];
    g.push(txt(W / 2, 24, '평면 배치를 재귀적으로 만드는 두 규칙 — 이것으로 구조적 귀납법을 쓴다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 14;

    g.push(panel(20, 40, 330, 234, '면 쪼개기', '한 면 위의 두 정점을 이으면 면과 간선이 각각 하나 늘어난다'));
    const hex2 = [];
    const nm = ['a', 'w', 'x', 'b', 'y', 'z'];
    for (let i = 0; i < 6; i += 1) {
        const ang = (150 + i * 60) * Math.PI / 180;
        hex2.push([185 + 80 * Math.cos(ang), 160 + 58 * Math.sin(ang)]);
    }
    for (let i = 0; i < 6; i += 1) g.push(eg(hex2[i], hex2[(i + 1) % 6], { col: CK, sw: 1.6, r: R }));
    g.push(eg(hex2[0], hex2[3], { col: C2, sw: 3, r: R, dash: '7 4' }));
    hex2.forEach((p, i) => g.push(nd(p, nm[i], { col: i === 0 || i === 3 ? C2 : CI, fill: i === 0 || i === 3 ? C2 : null, r: R })));
    g.push(ctxt(30, 246, 'a w x b y z a 하나가 a w x b a 와 a b y z a 둘로 갈린다', C2));
    g.push(ctxt(30, 266, 'v 그대로, e 하나 늘고, f 하나 늚 → v − e + f 는 그대로', CI, { bold: true }));

    g.push(panel(370, 40, 330, 234, '다리 잇기', '떨어진 두 배치를 이으면 면이 하나 줄고 간선이 하나 늘어난다'));
    const L2 = [[432, 106], [502, 106], [467, 176]];
    const R2 = [[588, 124], [658, 124], [658, 194], [588, 194]];
    for (let i = 0; i < 3; i += 1) g.push(eg(L2[i], L2[(i + 1) % 3], { col: CK, sw: 1.6, r: R }));
    for (let i = 0; i < 4; i += 1) g.push(eg(R2[i], R2[(i + 1) % 4], { col: CK, sw: 1.6, r: R }));
    g.push(eg(L2[2], R2[3], { col: C1, sw: 3, r: R, dash: '7 4' }));
    L2.forEach(p => g.push(nd(p, '', { col: C3, fill: C3, r: R })));
    R2.forEach(p => g.push(nd(p, '', { col: C3, fill: C3, r: R })));
    g.push(ctxt(380, 246, '두 바깥면이 합쳐져 하나가 된다', C1));
    g.push(ctxt(380, 266, 'v 는 더해지고, e 는 더한 뒤 +1, f 는 더한 뒤 −1', CI, { bold: true }));

    g.push(txt(W / 2, 302, '기저는 정점 하나에 면 하나(길이 0 닫힌 보행)이고 1 − 0 + 1 = 2 다. 두 생성자가 v − e + f 를 바꾸지 않으므로',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 322, '오일러 공식이 구조적 귀납법 한 번으로 나온다. 다리 잇기에서는 2 + 2 − 2 = 2 라는 셈이 필요하다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '평면 배치의 두 생성자',
        desc: '면 쪼개기는 간선과 면을 각각 하나 늘리고 다리 잇기는 간선을 하나 늘리며 면을 하나 줄인다는 것을 보인 그림',
        body: g.join(''),
    });
})());

/* 5. 인접 정점 합치기 */
add('mcs-g-merge-vertices', (() => {
    const W = 720, H = 286;
    const g = [];
    g.push(txt(W / 2, 24, '인접한 두 정점 합치기 — 평면성이 유지된다. 5색 정리에서 이것을 쓴다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 15;
    g.push(panel(20, 40, 300, 196, '합치기 전', 'n~1 과 n~2 는 인접하다'));
    const P1 = { n1: [130, 122], n2: [220, 176], u: [76, 190], v: [200, 96], w: [276, 122] };
    for (const [x, y] of [['n1', 'n2'], ['n1', 'u'], ['n1', 'v'], ['n2', 'v'], ['n2', 'w']]) {
        const isCore = x === 'n1' && y === 'n2';
        g.push(eg(P1[x], P1[y], { col: isCore ? C2 : CK, sw: isCore ? 3 : 1.6, r: R }));
    }
    g.push(nd(P1.n1, 'n~1', { col: C2, fill: C2, r: R }));
    g.push(nd(P1.n2, 'n~2', { col: C2, fill: C2, r: R }));
    g.push(nd(P1.u, 'u', { col: CI, r: R }));
    g.push(nd(P1.v, 'v', { col: CI, r: R }));
    g.push(nd(P1.w, 'w', { col: CI, r: R }));

    g.push(arw(340, 140, 390, 140, { col: CK, sw: 2 }));

    g.push(panel(410, 40, 290, 196, '합친 뒤', 'm 은 두 정점의 이웃을 모두 갖는다'));
    const P2 = { m: [540, 140], u: [468, 196], v: [540, 96], w: [634, 166] };
    for (const [x, y] of [['m', 'u'], ['m', 'v'], ['m', 'w']]) g.push(eg(P2[x], P2[y], { col: CK, sw: 1.6, r: R }));
    g.push(nd(P2.m, 'm', { col: C1, fill: C1, r: R }));
    g.push(nd(P2.u, 'u', { col: CI, r: R }));
    g.push(nd(P2.v, 'v', { col: CI, r: R }));
    g.push(nd(P2.w, 'w', { col: CI, r: R }));
    g.push(ctxt(420, 224, 'v 로 가던 간선 두 개가 하나가 되었다', C1));

    g.push(txt(W / 2, 260, '두 정점이 인접하므로 그 간선을 따라 한쪽을 다른 쪽으로 끌어당기면 된다. 새로 엇갈리는 선은 생기지 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 280, '합친 뒤 정점이 하나 줄어들므로 정점 수에 대한 귀납법에서 귀납 가정을 쓸 수 있게 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '인접한 두 정점을 합치기',
        desc: '인접한 두 정점을 하나로 합치면 두 정점의 이웃을 모두 갖는 새 정점이 되고 평면성이 유지되는 것을 보인 그림',
        body: g.join(''),
    });
})());

/* 6. 정다면체 셋의 평면 배치 */
add('mcs-g-platonic', (() => {
    const W = 720, H = 356;
    const g = [];
    g.push(txt(W / 2, 24, '정다면체를 구에 투영하면 평면 배치가 된다 — 그래서 오일러 공식을 쓸 수 있다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const R = 11;
    const note = (cx, y, s2, c) => ctxt(cx, y, s2, c, { anchor: 'middle' });

    g.push(panel(20, 40, 190, 260, '정사면체 · v 4 e 6 f 4', ''));
    const t = [[115, 83], [32, 227], [198, 227]];
    const ti = [115, 179];
    for (let i = 0; i < 3; i += 1) g.push(eg(t[i], t[(i + 1) % 3], { col: CK, sw: 1.5, r: R }));
    for (const p of t) g.push(eg(ti, p, { col: CK, sw: 1.5, r: R }));
    t.forEach(p => g.push(nd(p, '', { col: C1, fill: C1, r: R })));
    g.push(nd(ti, '', { col: C1, fill: C1, r: R }));
    g.push(note(115, 252, '면마다 변 3개', CK));
    g.push(note(115, 270, '꼭짓점마다 면 3개', CK));
    g.push(ctxt(115, 290, 'n = 3, m = 3', CI, { anchor: 'middle', bold: true }));

    g.push(panel(222, 40, 230, 260, '정육면체 · v 8 e 12 f 6', ''));
    const co = [[272, 86], [402, 86], [402, 216], [272, 216]];
    const ci = [[306, 120], [368, 120], [368, 182], [306, 182]];
    for (let i = 0; i < 4; i += 1) {
        g.push(eg(co[i], co[(i + 1) % 4], { col: CK, sw: 1.5, r: R }));
        g.push(eg(ci[i], ci[(i + 1) % 4], { col: CK, sw: 1.5, r: R }));
        g.push(eg(co[i], ci[i], { col: CK, sw: 1.5, r: R }));
    }
    [...co, ...ci].forEach(p => g.push(nd(p, '', { col: C2, fill: C2, r: R })));
    g.push(note(337, 252, '면마다 변 4개', CK));
    g.push(note(337, 270, '꼭짓점마다 면 3개', CK));
    g.push(ctxt(337, 290, 'n = 4, m = 3', CI, { anchor: 'middle', bold: true }));

    g.push(panel(464, 40, 236, 260, '정팔면체 · v 6 e 12 f 8', ''));
    const cx = 582, cy = 179;
    const ot = [[cx, cy - 96], [cx - 83, cy + 48], [cx + 83, cy + 48]];
    const it = [[cx - 24, cy - 14], [cx, cy + 28], [cx + 24, cy - 14]];
    for (let i = 0; i < 3; i += 1) {
        g.push(eg(ot[i], ot[(i + 1) % 3], { col: CK, sw: 1.5, r: R }));
        g.push(eg(it[i], it[(i + 1) % 3], { col: CK, sw: 1.5, r: R }));
    }
    for (const [i, j] of [[0, 0], [0, 1], [1, 1], [1, 2], [2, 2], [2, 0]]) {
        g.push(eg(it[i], ot[j], { col: CK, sw: 1.5, r: R }));
    }
    [...ot, ...it].forEach(p => g.push(nd(p, '', { col: C3, fill: C3, r: R })));
    g.push(note(582, 252, '면마다 변 3개', CK));
    g.push(note(582, 270, '꼭짓점마다 면 4개', CK));
    g.push(ctxt(582, 290, 'n = 3, m = 4', CI, { anchor: 'middle', bold: true }));

    g.push(txt(W / 2, 326, '꼭짓점마다 면이 m 개 모이면 m·v = 2e 이고, 면마다 변이 n 개면 n·f = 2e 다. 둘 다 악수 보조정리와 같은 셈이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 346, '이 두 식을 오일러 공식에 넣으면 n 과 m 만 남는 식 하나가 나오고, 그 식이 답을 다섯 개로 묶는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '정사면체·정육면체·정팔면체의 평면 배치',
        desc: '세 정다면체를 평면에 엇갈림 없이 그리고 각각의 꼭짓점 수 간선 수 면 수와 n, m 값을 적은 그림',
        body: g.join(''),
    });
})());

/* 7. 왜 다섯 개뿐인가 — 남는 (n, m) 이 다섯 칸이다 */
add('mcs-g-platonic-cases', (() => {
    const W = 720, H = 336;
    const g = [];
    g.push(txt(W / 2, 24, '1/n + 1/m 이 1/2 보다 커야 한다 — 그런 (n, m) 은 다섯 칸뿐이다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const rows = [
        ['3', '2/3 ✓', '7/12 ✓', '8/15 ✓', '1/2 ✗'],
        ['4', '7/12 ✓', '1/2 ✗', '9/20 ✗', '5/12 ✗'],
        ['5', '8/15 ✓', '9/20 ✗', '2/5 ✗', '11/30 ✗'],
        ['6', '1/2 ✗', '5/12 ✗', '11/30 ✗', '1/3 ✗'],
    ];
    g.push(table(24, 54, ['면의 변 수 n', 'm = 3', 'm = 4', 'm = 5', 'm = 6'], rows, {
        cws: [118, 70, 70, 70, 70], rh: 30,
        hlCell: [[0, 1, C3], [0, 2, C3], [0, 3, C3], [1, 1, C3], [2, 1, C3]],
    }));
    g.push(ctxt(24, 226, 'n 은 면의 변 수, m 은 한 꼭짓점에 모이는 면 수. 둘 다 3 이상이다', CK));
    g.push(ctxt(24, 246, 'n 이나 m 이 6 이상이면 왼쪽이 1/3 + 1/6 = 1/2 이하가 되어 끝난다', CK));
    g.push(ctxt(24, 268, '초록 칸 다섯 개가 실제 정다면체 다섯 개다', C3, { bold: true }));

    g.push(panel(454, 46, 246, 208, '남은 다섯', ''));
    g.push(table(470, 84, ['n', 'm', 'v', 'e', 'f'], [
        ['3', '3', '4', '6', '4'], ['4', '3', '8', '12', '6'], ['3', '4', '6', '12', '8'],
        ['3', '5', '12', '30', '20'], ['5', '3', '20', '30', '12'],
    ], { cws: [42, 42, 42, 42, 42], rh: 24 }));
    g.push(ctxt(577, 238, '위에서부터 정사면체 · 정육면체 · 정팔면체', CK, { anchor: 'middle' }));

    g.push(txt(W / 2, 300, '1/m + 1/n = 1/e + 1/2 에서 e 가 양수이므로 왼쪽이 1/2 보다 커야 한다. 그 제약이 다섯 칸만 남긴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 322, '남은 다섯이 모두 실제로 존재한다는 것은 따로 확인해야 한다 — 부등식은 후보를 줄일 뿐 존재를 주지 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '정다면체가 다섯 개뿐인 이유',
        desc: 'n 과 m 을 3 부터 6 까지 넣은 표에서 1/n + 1/m 이 1/2 를 넘는 칸이 다섯 개뿐임을 보이고 그 다섯의 꼭짓점 간선 면 수를 적은 그림',
        body: g.join(''),
    });
})());

export default figures;
