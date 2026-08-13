/**
 * 기초수학 3장(명제) ~ 5장(유클리드 기하학)의 그림.
 *
 * 이 블록의 그림 이름은 모두 math-log- 로 시작한다(담당 A 에게 배정된 접두어).
 * 각 항목은 { name, svg } 이고 name 이 파일 이름(/figures/mathematics/<name>.svg)이 된다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 기호는 유니코드로 직접 적는다. lib 의 esc 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 물결표를 쓰지 않고, 아래첨자도 유니코드(₁₂₃)로 적는다.
 */
import { svg, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const RAD = Math.PI / 180;

/* ------------------------------------------------------------------ *
 * 벡터 소도구 — 화소 좌표계다. y 는 아래로 증가한다.
 * ------------------------------------------------------------------ */
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
const addv = (a, b) => [a[0] + b[0], a[1] + b[1]];
const mul = (a, k) => [a[0] * k, a[1] * k];
const len = a => Math.hypot(a[0], a[1]);
const unit = (a) => { const L = Math.hypot(a[0], a[1]) || 1; return [a[0] / L, a[1] / L]; };
const mid = (a, b) => [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
/** 원 위의 점. 각은 도(degree), 수학 관례(반시계, 0° 가 오른쪽). */
const onC = (cx, cy, r, deg) => [cx + r * Math.cos(deg * RAD), cy - r * Math.sin(deg * RAD)];

/* ------------------------------------------------------------------ *
 * 그리기 소도구
 * ------------------------------------------------------------------ */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function pathd(d, { fill = 'none', op = 1, stroke = 'none', sw = 1.5, rule, dash } = {}) {
    return `<path d="${d}" fill="${fill}" fill-opacity="${op}"${rule ? ` fill-rule="${rule}"` : ''} stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.13, stroke = 'none', sw = 1.6, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 그 클래스가 SVG 안에 없어 선이 사라진다.
 * 색을 직접 넣는다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.9, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
        ar1: 'var(--s1)', ar2: 'var(--s2)', ar3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 점 하나와 그 이름. */
function dot(p, label, { dx = 8, dy = -8, anchor = 'start', cls = 'f1', r = 4, size = 'sm' } = {}) {
    return `<circle cx="${r2(p[0])}" cy="${r2(p[1])}" r="${r}" fill="var(--${cls === 'f2' ? 's2' : cls === 'f3' ? 's3' : cls === 'fk' ? 'ink2' : 's1'})"/>`
        + (label ? txt(p[0] + dx, p[1] + dy, label, { anchor, cls: 'ink', size }) : '');
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 21, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 38, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/**
 * 꼭짓점 v 에서 p1 방향과 p2 방향 사이의 각을 호로 표시한다.
 * 두 방향 중 사잇각이 작은 쪽(180° 미만)을 그린다.
 */
function angleMark(v, p1, p2, r, label, { stroke = 'var(--ink2)', sw = 1.4, lgap = 15, cls = 'ink2' } = {}) {
    const a1 = Math.atan2(p1[1] - v[1], p1[0] - v[0]);
    const a2 = Math.atan2(p2[1] - v[1], p2[0] - v[0]);
    let da = a2 - a1;
    while (da <= -Math.PI) da += 2 * Math.PI;
    while (da > Math.PI) da -= 2 * Math.PI;
    const sweep = da > 0 ? 1 : 0;
    const q1 = [v[0] + r * Math.cos(a1), v[1] + r * Math.sin(a1)];
    const q2 = [v[0] + r * Math.cos(a2), v[1] + r * Math.sin(a2)];
    const am = a1 + da / 2;
    const lp = [v[0] + (r + lgap) * Math.cos(am), v[1] + (r + lgap) * Math.sin(am) + 4];
    return `<path d="M${r2(q1[0])} ${r2(q1[1])} A${r2(r)} ${r2(r)} 0 0 ${sweep} ${r2(q2[0])} ${r2(q2[1])}" fill="none" stroke="${stroke}" stroke-width="${sw}"/>`
        + (label ? txt(lp[0], lp[1], label, { anchor: 'middle', cls, size: 'sm' }) : '');
}

/** 직각 표시. */
function rightAngle(v, p1, p2, s = 11) {
    const u1 = mul(unit(sub(p1, v)), s);
    const u2 = mul(unit(sub(p2, v)), s);
    return line([addv(v, u1), addv(addv(v, u1), u2), addv(v, u2)], { sw: 1.2 });
}

/** 같은 길이·같은 각 표시용 짧은 빗금. */
function tick(a, b, count = 1, { gap = 5 } = {}) {
    const m = mid(a, b);
    const u = unit(sub(b, a));
    const nrm = [-u[1], u[0]];
    const out = [];
    for (let i = 0; i < count; i += 1) {
        const c = addv(m, mul(u, (i - (count - 1) / 2) * gap));
        out.push(line([addv(c, mul(nrm, 5)), addv(c, mul(nrm, -5))], { sw: 1.3 }));
    }
    return out.join('');
}

const circPath = (cx, cy, r) => `M${r2(cx - r)} ${r2(cy)} a ${r} ${r} 0 1 0 ${2 * r} 0 a ${r} ${r} 0 1 0 ${-2 * r} 0 Z`;
const rectPath = (x, y, w, h) => `M${r2(x)} ${r2(y)} H${r2(x + w)} V${r2(y + h)} H${r2(x)} Z`;
/** 반지름이 같은 두 원이 겹친 렌즈꼴. */
function lensPath(x1, x2, cy, r) {
    const xm = (x1 + x2) / 2;
    const h = Math.sqrt(Math.max(r * r - ((x2 - x1) / 2) ** 2, 0));
    return `M${r2(xm)} ${r2(cy - h)} A${r} ${r} 0 0 1 ${r2(xm)} ${r2(cy + h)} A${r} ${r} 0 0 1 ${r2(xm)} ${r2(cy - h)} Z`;
}

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';

/* ================================================================== *
 * 3장 — 명제
 * ================================================================== */

add({
    name: 'math-log-truth-table',
    svg: (() => {
        const W = 760, H = 302;
        const cols = ['p', 'q', '¬p', 'p ∧ q', 'p ∨ q', 'p → q'];
        const rows = [
            [1, 1, 0, 1, 1, 1],
            [1, 0, 0, 0, 1, 0],
            [0, 1, 1, 0, 1, 1],
            [0, 0, 1, 0, 0, 1],
        ];
        const x0 = 44, cw = 112, y0 = 58, rh = 46;
        const g = [];
        g.push(txt(W / 2, 26, 'p 와 q 가 참이냐 거짓이냐로 나뉘는 네 가지 경우가 전부다', { anchor: 'middle', cls: 'ink bold' }));
        cols.forEach((c, i) => g.push(txt(x0 + cw * i + (cw - 6) / 2, y0 - 9, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
        rows.forEach((row, ri) => row.forEach((v, ci) => {
            const x = x0 + cw * ci, y = y0 + rh * ri;
            const on = v === 1;
            g.push(box(x, y, cw - 6, rh - 6, { fill: on ? C1 : 'var(--ink2)', op: on ? 0.22 : 0.07, stroke: 'var(--grid)', sw: 1, rx: 4 }));
            g.push(txt(x + (cw - 6) / 2, y + rh / 2 - 1, on ? '참' : '거짓', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }));
        const hx = x0 + cw * 5;
        g.push(box(hx - 4, y0 + rh * 2 - 4, cw + 2, rh * 2, { fill: 'none', stroke: C2, sw: 2, rx: 6, dash: '5 4' }));
        g.push(txt(W / 2, y0 + rh * 4 + 24, '표시한 두 줄은 p 가 거짓인 경우다. 조건문은 이때 무조건 참이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '기본 논리 연산의 진리표', desc: '부정, 논리곱, 논리합, 조건문의 진리표. 조건문은 p 가 거짓인 두 줄에서 참이다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-venn-logic',
    svg: (() => {
        const W = 770, H = 270;
        const pw = 230, ph = 162, py = 50;
        const g = [];
        g.push(txt(W / 2, 26, '드모르간 법칙 — 가운데와 오른쪽은 같은 영역이다', { anchor: 'middle', cls: 'ink bold' }));
        const draw = (px, title, mode, col) => {
            const bx = px + 12, by = py + 38, bw = pw - 24, bh = ph - 50;
            const cy = by + bh / 2;
            const a = px + 92, b = px + 138, r = 44;
            const out = [panel(px, py, pw, ph, title)];
            if (mode === 'and') out.push(pathd(lensPath(a, b, cy, r), { fill: col, op: 0.34 }));
            else out.push(pathd(`${rectPath(bx, by, bw, bh)} ${lensPath(a, b, cy, r)}`, { fill: col, op: 0.24, rule: 'evenodd' }));
            out.push(box(bx, by, bw, bh, { stroke: 'var(--grid)', sw: 1.2, rx: 3 }));
            out.push(circ(a, cy, r, { stroke: 'var(--ink2)', sw: 1.4 }));
            out.push(circ(b, cy, r, { stroke: 'var(--ink2)', sw: 1.4 }));
            out.push(txt(a - 24, cy - 16, 'p', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            out.push(txt(b + 24, cy - 16, 'q', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            return out.join('');
        };
        g.push(draw(18, 'p ∧ q', 'and', C1));
        g.push(draw(270, '¬(p ∧ q)', 'not', C2));
        g.push(draw(522, '¬p ∨ ¬q', 'not', C3));
        g.push(txt(W / 2, H - 12, '칠한 부분이 그 명제가 참이 되는 경우다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '드모르간 법칙의 영역 그림', desc: '논리곱의 부정과 부정의 논리합이 같은 영역임을 보인다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-sufficient-necessary',
    svg: (() => {
        const W = 730, H = 340;
        const g = [];
        g.push(txt(W / 2, 26, 'p ⟹ q 를 영역으로 보면', { anchor: 'middle', cls: 'ink bold' }));
        g.push(ell(228, 190, 152, 108, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
        g.push(ell(196, 190, 76, 58, { fill: C2, op: 0.16, stroke: C2, sw: 1.8 }));
        g.push(txt(196, 196, 'p', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(330, 122, 'q', { anchor: 'middle', cls: 'ink bold' }));
        g.push(dot([180, 218], '', { cls: 'f2' }));
        g.push(dot([300, 236], '', { cls: 'f1' }));
        g.push(txt(300, 256, 'q 지만 p 는 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(dot([120, 316], '', { cls: 'fk' }));
        g.push(txt(120, 334, 'q 밖 — p 도 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(arw(150, 306, 176, 226, { cls: 'ark', width: 1.2, dash: '4 3' }));
        const tx = 412;
        g.push(txt(tx, 106, 'p 안에 있으면 반드시 q 안에 있다', { cls: 'ink' }));
        g.push(txt(tx, 128, 'p 는 q 이기 위한 충분조건', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 172, 'q 안에 있다고 p 안에 있지는 않다', { cls: 'ink' }));
        g.push(txt(tx, 194, 'q 는 p 이기 위한 필요조건', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 238, 'q 밖으로 나가면 p 밖이다', { cls: 'ink' }));
        g.push(txt(tx, 260, '이것이 대우 ¬q ⟹ ¬p 다', { cls: 'ink2', size: 'sm' }));
        g.push(line([[tx - 12, 88], [tx - 12, 272]], { stroke: 'var(--grid)', sw: 1.4 }));
        return svg({ width: W, height: H, title: '충분조건과 필요조건', desc: 'p 의 영역이 q 의 영역 안에 들어 있는 그림으로 충분조건, 필요조건, 대우를 함께 읽는다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-quantifier',
    svg: (() => {
        const W = 720, H = 268;
        const g = [];
        const beads = (px, py, kinds) => kinds.map((k, i) => {
            const cx = px + 40 + (i % 4) * 62;
            const cy = py + 66 + Math.floor(i / 4) * 58;
            const col = k === 'b' ? C1 : C2;
            return circ(cx, cy, 20, { fill: col, op: 0.26, stroke: col, sw: 1.6 })
                + txt(cx, cy + 5, k === 'b' ? '파' : '주', { anchor: 'middle', cls: 'ink', size: 'sm' });
        }).join('');
        g.push(panel(18, 40, 336, 196, '∀x  (x 는 파랗다)', { sub: '모든 구슬이 파랗다' }));
        g.push(beads(18, 40, ['b', 'b', 'b', 'b', 'b', 'o', 'b', 'b']));
        g.push(arw(96, 230, 114, 190, { cls: 's2', width: 1.8 }));
        g.push(txt(120, 250, '반례 하나면 거짓', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(panel(366, 40, 336, 196, '∃x  (x 는 파랗다)', { sub: '파란 구슬이 하나라도 있다' }));
        g.push(beads(366, 40, ['o', 'o', 'o', 'o', 'o', 'b', 'o', 'o']));
        g.push(arw(444, 230, 462, 190, { cls: 's1', width: 1.8 }));
        g.push(txt(468, 250, '예 하나면 참', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        return svg({ width: W, height: H, title: '전칭명제와 존재명제', desc: '전칭명제는 반례 하나로 무너지고 존재명제는 예 하나로 세워진다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-domino',
    svg: (() => {
        const W = 720, H = 296;
        const g = [];
        const base = 236;
        g.push(txt(W / 2, 28, '수학적 귀납법은 도미노 두 조건이다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(line([[46, base], [560, base]], { stroke: 'var(--grid)', sw: 1.5 }));
        const domino = (x, label, col, angle) => {
            const body = box(x, base - 74, 15, 74, { fill: col, op: 0.24, stroke: col, sw: 1.6, rx: 2 });
            const wrapped = angle ? `<g transform="rotate(${angle} ${r2(x + 15)} ${r2(base)})">${body}</g>` : body;
            return wrapped + txt(x + 7, base + 18, label, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        };
        g.push(domino(88, '1', C2, 46));
        g.push(domino(142, '2', 'var(--ink2)', 0));
        g.push(domino(196, '3', 'var(--ink2)', 0));
        g.push(txt(258, base - 30, '···', { anchor: 'middle', cls: 'ink2' }));
        g.push(domino(310, 'k', C1, 0));
        g.push(domino(376, 'k+1', C1, 0));
        g.push(txt(444, base - 30, '···', { anchor: 'middle', cls: 'ink2' }));
        g.push(domino(492, 'n', 'var(--ink2)', 0));
        g.push(arw(48, 132, 82, 156, { cls: 's2', width: 2 }));
        g.push(txt(40, 122, '① 첫 번째를 민다', { cls: 'ink', size: 'sm' }));
        g.push(`<path d="M318 ${base - 88} Q351 ${base - 122} 384 ${base - 88}" fill="none" stroke="${C1}" stroke-width="1.8" marker-end="url(#ar1)"/>`);
        g.push(txt(351, base - 130, '② 넘어지면 다음도 넘어진다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(586, 150, '두 조건이 갖추어지면', { cls: 'ink', size: 'sm' }));
        g.push(txt(586, 172, '몇 번째든 언젠가', { cls: 'ink', size: 'sm' }));
        g.push(txt(586, 194, '넘어진다', { cls: 'ink bold', size: 'sm' }));
        g.push(line([[574, 128], [574, 208]], { stroke: 'var(--grid)', sw: 1.4 }));
        g.push(txt(W / 2, H - 12, '②만 있고 ①이 없으면 아무것도 넘어지지 않는다는 점이 중요하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '수학적 귀납법의 도미노 그림', desc: '출발점과 이어짐 두 조건이 모두 있어야 전체가 넘어진다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-induction-sum',
    svg: (() => {
        const W = 700, H = 300;
        const s = 32, x0 = 132, y0 = 66, n = 5;
        const g = [];
        g.push(txt(W / 2, 30, '1 + 2 + 3 + 4 + 5 을 두 벌 맞물리면', { anchor: 'middle', cls: 'ink bold' }));
        for (let i = 0; i < n; i += 1) {
            for (let j = 0; j <= i; j += 1) g.push(box(x0 + j * s, y0 + i * s, s, s, { fill: C1, op: 0.26, stroke: C1, sw: 1.2, rx: 2 }));
            for (let j = i + 1; j <= n; j += 1) g.push(box(x0 + j * s, y0 + i * s, s, s, { fill: C2, op: 0.22, stroke: C2, sw: 1.2, rx: 2 }));
        }
        g.push(box(x0, y0, s * (n + 1), s * n, { stroke: 'var(--ink)', sw: 2, rx: 3 }));
        g.push(txt(x0 + s * (n + 1) / 2, y0 - 12, 'n + 1 = 6 칸', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(line([[x0 - 14, y0], [x0 - 22, y0], [x0 - 22, y0 + s * n], [x0 - 14, y0 + s * n]], { sw: 1.3 }));
        g.push(txt(x0 - 30, y0 + s * n / 2 + 4, 'n = 5 줄', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        const tx = 372;
        g.push(txt(tx, 100, '파란 계단 = 1+2+3+4+5', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 126, '주황 계단 = 5+4+3+2+1', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 158, '둘을 더하면 직사각형', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 182, '5 × 6 = 30 칸', { cls: 'ink bold' }));
        g.push(txt(tx, 214, '따라서 한 벌은 30 ÷ 2 = 15', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 240, '일반형은 n(n+1) ÷ 2', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(W / 2, H - 14, '그림은 n = 5 만 보여 준다. 모든 n 에 대해 성립함을 보이려면 귀납법이 필요하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '1부터 n 까지의 합', desc: '같은 계단 두 벌을 맞물려 n(n+1) 칸의 직사각형을 만든다.', body: g.join('') });
    })(),
});

/* ================================================================== *
 * 4장 — 집합
 * ================================================================== */

add({
    name: 'math-log-venn-ops',
    svg: (() => {
        const W = 776, H = 252;
        const pw = 182, ph = 158, py = 46;
        const g = [];
        g.push(txt(W / 2, 26, '네 가지 집합 연산 — 칠한 부분이 결과다', { anchor: 'middle', cls: 'ink bold' }));
        const cell = (px, title, kind) => {
            const bx = px + 12, by = py + 32, bw = pw - 24, bh = ph - 46;
            const cy = by + bh / 2, a = px + 68, b = px + 112, r = 42;
            const out = [panel(px, py, pw, ph, title)];
            const rp = rectPath(bx, by, bw, bh);
            if (kind === 'union') out.push(pathd(`${circPath(a, cy, r)} ${circPath(b, cy, r)}`, { fill: C1, op: 0.28 }));
            if (kind === 'inter') out.push(pathd(lensPath(a, b, cy, r), { fill: C1, op: 0.34 }));
            if (kind === 'diff') out.push(pathd(`${circPath(a, cy, r)} ${lensPath(a, b, cy, r)}`, { fill: C1, op: 0.28, rule: 'evenodd' }));
            if (kind === 'comp') out.push(pathd(`${rp} ${circPath(a, cy, r)}`, { fill: C1, op: 0.22, rule: 'evenodd' }));
            out.push(box(bx, by, bw, bh, { stroke: 'var(--grid)', sw: 1.2, rx: 3 }));
            out.push(txt(bx + 5, by + 14, 'U', { cls: 'ink2', size: 'sm' }));
            out.push(circ(a, cy, r, { stroke: 'var(--ink2)', sw: 1.4 }));
            out.push(circ(b, cy, r, { stroke: 'var(--ink2)', sw: 1.4 }));
            out.push(txt(a - 22, cy - 16, 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            out.push(txt(b + 22, cy - 16, 'B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            return out.join('');
        };
        g.push(cell(14, 'A ∪ B  합집합', 'union'));
        g.push(cell(206, 'A ∩ B  교집합', 'inter'));
        g.push(cell(398, 'A − B  차집합', 'diff'));
        g.push(cell(590, 'Aᶜ  여집합', 'comp'));
        g.push(txt(W / 2, H - 10, '여집합은 전체집합 U 를 정해 두어야 뜻이 생긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '집합 연산 네 가지', desc: '합집합, 교집합, 차집합, 여집합을 벤 다이어그램으로 나란히 보인다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-venn-subset',
    svg: (() => {
        const W = 700, H = 262;
        const g = [];
        g.push(txt(W / 2, 26, '부분집합인가 아닌가는 원소 하나가 정한다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(panel(20, 42, 320, 196, 'A ⊆ B', { sub: 'A 의 원소가 모두 B 안에 있다' }));
        g.push(ell(186, 156, 116, 62, { fill: C1, op: 0.1, stroke: C1, sw: 1.7 }));
        g.push(ell(160, 156, 58, 38, { fill: C2, op: 0.16, stroke: C2, sw: 1.7 }));
        g.push(txt(160, 132, 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(272, 122, 'B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(dot([142, 164], '', { cls: 'f2', r: 3.5 }));
        g.push(dot([176, 160], '', { cls: 'f2', r: 3.5 }));
        g.push(dot([232, 174], '', { cls: 'f1', r: 3.5 }));
        g.push(panel(360, 42, 320, 196, 'A ⊄ B', { sub: 'B 밖으로 나간 원소가 하나 있다' }));
        g.push(ell(546, 156, 100, 60, { fill: C1, op: 0.1, stroke: C1, sw: 1.7 }));
        g.push(ell(462, 156, 62, 44, { fill: C2, op: 0.16, stroke: C2, sw: 1.7 }));
        g.push(txt(438, 122, 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(614, 120, 'B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(dot([492, 158], '', { cls: 'f2', r: 3.5 }));
        g.push(dot([424, 152], '', { cls: 'f2', r: 4.5 }));
        g.push(arw(404, 214, 420, 164, { cls: 's2', width: 1.6 }));
        g.push(txt(400, 230, '이 원소가 B 에 없다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        return svg({ width: W, height: H, title: '부분집합과 그렇지 않은 경우', desc: 'B 밖으로 벗어난 원소가 하나라도 있으면 부분집합이 아니다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-inclusion-exclusion',
    svg: (() => {
        const W = 700, H = 290;
        const g = [];
        g.push(txt(W / 2, 28, '겹친 부분을 두 번 세지 않으려면', { anchor: 'middle', cls: 'ink bold' }));
        const cy = 168, a = 200, b = 288, r = 84;
        g.push(box(72, 62, 344, 202, { stroke: 'var(--grid)', sw: 1.2, rx: 4 }));
        g.push(txt(80, 80, 'U', { cls: 'ink2', size: 'sm' }));
        g.push(pathd(circPath(a, cy, r), { fill: C1, op: 0.16 }));
        g.push(pathd(circPath(b, cy, r), { fill: C2, op: 0.16 }));
        g.push(circ(a, cy, r, { stroke: C1, sw: 1.7 }));
        g.push(circ(b, cy, r, { stroke: C2, sw: 1.7 }));
        g.push(txt(a - 62, cy - 62, 'A', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(b + 62, cy - 62, 'B', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(a - 40, cy + 6, '12', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt((a + b) / 2, cy + 6, '5', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(b + 40, cy + 6, '8', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(388, 248, '6', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(388, 232, '바깥', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const tx = 448;
        g.push(line([[tx - 14, 84], [tx - 14, 252]], { stroke: 'var(--grid)', sw: 1.4 }));
        g.push(txt(tx, 104, '|A| = 12 + 5 = 17', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 130, '|B| = 8 + 5 = 13', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 160, '그냥 더하면 17 + 13 = 30', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 184, '겹친 5 를 두 번 세었다', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 214, '|A ∪ B| = 30 − 5 = 25', { cls: 'ink bold' }));
        g.push(txt(tx, 240, '전체는 25 + 6 = 31', { cls: 'ink', size: 'sm' }));
        return svg({ width: W, height: H, title: '포함배제 원리', desc: '두 집합의 크기를 더하면 겹친 부분을 두 번 세므로 한 번 빼야 한다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-product-grid',
    svg: (() => {
        const W = 620, H = 300;
        const g = [];
        g.push(txt(W / 2, 28, 'A × B — 순서쌍을 격자의 자리로 본다', { anchor: 'middle', cls: 'ink bold' }));
        const xs = [180, 300, 420], ys = [200, 116];
        const A = ['1', '2', '3'], B = ['a', 'b'];
        g.push(line([[130, 236], [500, 236]], { stroke: 'var(--ink2)', sw: 1.4 }));
        g.push(line([[130, 236], [130, 76]], { stroke: 'var(--ink2)', sw: 1.4 }));
        g.push(txt(510, 240, 'A', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(126, 66, 'B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        xs.forEach((x, i) => {
            g.push(txt(x, 256, A[i], { anchor: 'middle', cls: 'ink', size: 'sm' }));
            g.push(line([[x, 236], [x, 100]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        });
        ys.forEach((y, j) => {
            g.push(txt(118, y + 4, B[j], { anchor: 'end', cls: 'ink', size: 'sm' }));
            g.push(line([[130, y], [470, y]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        });
        xs.forEach((x, i) => ys.forEach((y, j) => {
            g.push(dot([x, y], '', { cls: j === 0 ? 'f1' : 'f2', r: 5 }));
            g.push(txt(x + 10, y - 10, `(${A[i]}, ${B[j]})`, { cls: 'ink', size: 'sm' }));
        }));
        g.push(txt(W / 2, H - 16, '자리가 3 × 2 = 6 개다. 원소 개수의 곱이 곱집합의 크기가 되는 이유가 이 격자다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '곱집합을 격자로 보기', desc: 'A 의 원소를 가로, B 의 원소를 세로에 놓으면 순서쌍이 격자의 자리가 된다.', body: g.join('') });
    })(),
});

/** 두 집합 사이의 화살표 그림을 만든다. */
function arrowPanel(px, py, pw, ph, title, sub, L, R, edges, { lname = 'X', rname = 'Y', badge } = {}) {
    const out = [panel(px, py, pw, ph, title, { sub })];
    const lx = px + 62, rx = px + pw - 62;
    const top = py + 68;
    const gap = 34;
    const lp = L.map((_, i) => [lx, top + i * gap + (Math.max(L.length, R.length) - L.length) * gap / 2]);
    const rp = R.map((_, i) => [rx, top + i * gap + (Math.max(L.length, R.length) - R.length) * gap / 2]);
    const hL = (L.length - 1) * gap;
    const hR = (R.length - 1) * gap;
    out.push(ell(lx, top + hL / 2 + (Math.max(L.length, R.length) - L.length) * gap / 2, 32, hL / 2 + 28, { stroke: 'var(--ink2)', sw: 1.3 }));
    out.push(ell(rx, top + hR / 2 + (Math.max(L.length, R.length) - R.length) * gap / 2, 32, hR / 2 + 28, { stroke: 'var(--ink2)', sw: 1.3 }));
    out.push(txt(lx, py + ph - 12, lname, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    out.push(txt(rx, py + ph - 12, rname, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    for (const [i, j] of edges) out.push(arw(lp[i][0] + 12, lp[i][1], rp[j][0] - 14, rp[j][1], { cls: 's1', width: 1.6 }));
    L.forEach((nm, i) => { out.push(dot(lp[i], '', { cls: 'fk', r: 4 })); out.push(txt(lp[i][0] - 12, lp[i][1] + 4, nm, { anchor: 'end', cls: 'ink', size: 'sm' })); });
    R.forEach((nm, i) => { out.push(dot(rp[i], '', { cls: 'fk', r: 4 })); out.push(txt(rp[i][0] + 12, rp[i][1] + 4, nm, { cls: 'ink', size: 'sm' })); });
    if (badge) out.push(txt(px + pw / 2, py + ph - 30, badge, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return out.join('');
}

add({
    name: 'math-log-function-arrow',
    svg: (() => {
        const W = 776, H = 268;
        const g = [];
        g.push(txt(W / 2, 26, '함수는 ‘정의역의 원소마다 화살표가 정확히 하나’다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(arrowPanel(14, 42, 246, 208, '함수다', '둘이 같은 곳으로 가도 된다', ['x₁', 'x₂', 'x₃'], ['y₁', 'y₂', 'y₃'], [[0, 0], [1, 0], [2, 2]]));
        g.push(arrowPanel(266, 42, 246, 208, '함수가 아니다', 'x₃ 에서 나가는 화살표가 없다', ['x₁', 'x₂', 'x₃'], ['y₁', 'y₂', 'y₃'], [[0, 0], [1, 1]]));
        g.push(arrowPanel(518, 42, 246, 208, '함수가 아니다', 'x₂ 에서 화살표가 둘 나간다', ['x₁', 'x₂', 'x₃'], ['y₁', 'y₂', 'y₃'], [[0, 0], [1, 1], [1, 2], [2, 2]]));
        return svg({ width: W, height: H, title: '무엇이 함수인가', desc: '정의역의 원소마다 화살표가 하나씩 정확히 나가야 함수다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-function-types',
    svg: (() => {
        const W = 776, H = 268;
        const g = [];
        g.push(txt(W / 2, 26, '단사 · 전사 · 전단사 — 도착점 쪽을 보면 구별된다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(arrowPanel(14, 42, 246, 208, '일대일(단사)', '남는 도착점이 있어도 된다', ['x₁', 'x₂'], ['y₁', 'y₂', 'y₃'], [[0, 0], [1, 1]], { badge: '화살표가 겹치지 않는다' }));
        g.push(arrowPanel(266, 42, 246, 208, '위로(전사)', '겹쳐도 된다', ['x₁', 'x₂', 'x₃'], ['y₁', 'y₂'], [[0, 0], [1, 0], [2, 1]], { badge: '남는 도착점이 없다' }));
        g.push(arrowPanel(518, 42, 246, 208, '일대일대응(전단사)', '둘 다 만족', ['x₁', 'x₂', 'x₃'], ['y₁', 'y₂', 'y₃'], [[0, 0], [1, 1], [2, 2]], { badge: '역함수가 생기는 경우' }));
        return svg({ width: W, height: H, title: '함수의 세 가지 성질', desc: '단사는 화살표가 겹치지 않는 것, 전사는 남는 도착점이 없는 것, 전단사는 둘 다인 것이다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-composite-inverse',
    svg: (() => {
        const W = 760, H = 292;
        const g = [];
        g.push(txt(W / 2, 26, '합성은 이어 붙이는 것, 역함수는 되돌아오는 것', { anchor: 'middle', cls: 'ink bold' }));
        const oval = (cx, cy, nm, items) => {
            const out = [ell(cx, cy, 34, 62, { stroke: 'var(--ink2)', sw: 1.3 }), txt(cx, cy + 78, nm, { anchor: 'middle', cls: 'ink bold', size: 'sm' })];
            items.forEach((it, i) => {
                const p = [cx, cy - 26 + i * 26];
                out.push(dot(p, '', { cls: 'fk', r: 3.5 }));
                out.push(txt(cx + 8, p[1] + 4, it, { cls: 'ink', size: 'sm' }));
            });
            return out.join('');
        };
        g.push(panel(14, 44, 420, 232, '합성함수 g∘f'));
        g.push(oval(80, 150, 'X', ['a', 'b', 'c']));
        g.push(oval(224, 150, 'Y', ['p', 'q', 'r']));
        g.push(oval(368, 150, 'Z', ['1', '2', '3']));
        [[0, 0], [1, 2], [2, 1]].forEach(([i, j]) => g.push(arw(114, 124 + i * 26, 190, 124 + j * 26, { cls: 's1', width: 1.5 })));
        [[0, 1], [1, 0], [2, 2]].forEach(([i, j]) => g.push(arw(258, 124 + i * 26, 334, 124 + j * 26, { cls: 's2', width: 1.5 })));
        g.push(txt(152, 96, 'f', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(296, 96, 'g', { anchor: 'middle', cls: 'ink bold' }));
        g.push(`<path d="M80 236 Q224 272 368 236" fill="none" stroke="${C3}" stroke-width="2" stroke-dasharray="6 4" marker-end="url(#ar3)"/>`);
        g.push(txt(224, 268, 'g∘f — f 를 먼저, g 를 나중에', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(panel(446, 44, 300, 232, '역함수 f⁻¹'));
        g.push(oval(526, 150, 'X', ['a', 'b', 'c']));
        g.push(oval(668, 150, 'Y', ['p', 'q', 'r']));
        [[0, 0], [1, 1], [2, 2]].forEach(([i, j]) => g.push(arw(560, 118 + i * 26, 632, 118 + j * 26, { cls: 's1', width: 1.5 })));
        [[0, 0], [1, 1], [2, 2]].forEach(([i, j]) => g.push(arw(632, 130 + j * 26, 560, 130 + i * 26, { cls: 's2', width: 1.5, dash: '5 3' })));
        g.push(txt(597, 92, 'f', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(597, 258, 'f⁻¹', { anchor: 'middle', cls: 'ink bold' }));
        return svg({ width: W, height: H, title: '합성함수와 역함수', desc: '합성은 화살표를 이어 붙인 것이고 역함수는 화살표를 거꾸로 돌린 것이다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-diagonal',
    svg: (() => {
        const W = 700, H = 336;
        const rows = ['31415', '50000', '12345', '99999', '27182'];
        const cw = 40, x0 = 200, y0 = 78, rh = 34;
        const g = [];
        g.push(txt(W / 2, 28, '목록에 없는 수를 만드는 방법 — 대각선', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(W / 2, 50, '0 과 1 사이의 실수를 전부 줄 세웠다고 해 보자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        rows.forEach((row, i) => {
            g.push(txt(x0 - 12, y0 + i * rh + 6, `s${'₁₂₃₄₅'[i]} = 0.`, { anchor: 'end', cls: 'ink', size: 'sm' }));
            row.split('').forEach((d, j) => {
                const on = i === j;
                g.push(box(x0 + j * cw, y0 + i * rh - 18, cw - 4, rh - 6, { fill: on ? C2 : 'var(--ink2)', op: on ? 0.24 : 0.05, stroke: on ? C2 : 'var(--grid)', sw: on ? 1.8 : 1, rx: 3 }));
                g.push(txt(x0 + j * cw + (cw - 4) / 2, y0 + i * rh + 4, d, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            });
            g.push(txt(x0 + 5 * cw + 10, y0 + i * rh + 6, '···', { cls: 'ink2', size: 'sm' }));
        });
        const yn = y0 + rows.length * rh + 16;
        g.push(line([[x0 - 96, yn - 20], [x0 + 5 * cw + 30, yn - 20]], { stroke: 'var(--grid)', sw: 1.3 }));
        g.push(txt(x0 - 12, yn + 12, 'd = 0.', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        '41403'.split('').forEach((d, j) => {
            g.push(box(x0 + j * cw, yn - 12, cw - 4, rh - 6, { fill: C1, op: 0.24, stroke: C1, sw: 1.6, rx: 3 }));
            g.push(txt(x0 + j * cw + (cw - 4) / 2, yn + 10, d, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
        g.push(txt(x0 + 5 * cw + 10, yn + 12, '···', { cls: 'ink2', size: 'sm' }));
        g.push(txt(W / 2, H - 30, '대각선 자리의 숫자를 모두 바꾸어 만든 d 는 s₁ 과 첫째 자리가,', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(W / 2, H - 10, 's₂ 와 둘째 자리가 다르다. 그래서 목록의 어느 줄과도 같을 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '칸토어의 대각선 논법', desc: '목록의 대각선 숫자를 모두 바꾸면 목록의 어느 줄과도 다른 수가 만들어진다.', body: g.join('') });
    })(),
});

/* ================================================================== *
 * 5장 — 유클리드 기하학
 * ================================================================== */

add({
    name: 'math-log-postulate5',
    svg: (() => {
        const W = 780, H = 350;
        const g = [];
        const L1 = x => 130 + 0.10 * (x - 60);
        const L2 = x => 280 - 0.14 * (x - 60);
        const tx = 140;
        const p1 = [tx, L1(tx)], p2 = [tx, L2(tx)];
        const meet = [685, L1(685)];
        g.push(txt(W / 2, 28, '다섯 번째 공준 — 어느 쪽에서 만나는지까지 말한다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(line([[60, L1(60)], [700, L1(700)]], { stroke: C1, sw: 2 }));
        g.push(line([[60, L2(60)], [700, L2(700)]], { stroke: C1, sw: 2 }));
        g.push(line([[tx, 64], [tx, 330]], { stroke: C2, sw: 2 }));
        g.push(txt(tx - 10, 76, '가로지르는 직선', { anchor: 'end', cls: 'ink', size: 'sm' }));
        g.push(dot(p1, '', { cls: 'fk', r: 4 }));
        g.push(dot(p2, '', { cls: 'fk', r: 4 }));
        g.push(angleMark(p1, [tx, L1(tx) + 60], [tx + 70, L1(tx + 70)], 34, '84°'));
        g.push(angleMark(p2, [tx + 70, L2(tx + 70)], [tx, L2(tx) - 60], 34, '82°'));
        g.push(dot(meet, '', { cls: 'f2', r: 5 }));
        g.push(txt(meet[0] - 6, meet[1] - 16, '여기서 만난다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(300, 200, '두 내각의 합 84° + 82° = 166° < 180°', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(300, 222, '그러면 이 오른쪽에서 두 직선이 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(64, 300, '왼쪽 두 내각의 합은 194° 라', { cls: 'ink2', size: 'sm' }));
        g.push(txt(64, 320, '이쪽에서는 만나지 않는다', { cls: 'ink2', size: 'sm' }));
        g.push(txt(W / 2, H - 4, '내각의 합이 정확히 180° 일 때만 두 직선이 영영 만나지 않는다 — 그것이 평행이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '유클리드의 다섯 번째 공준', desc: '가로지르는 직선이 만드는 같은 쪽 두 내각의 합이 180° 보다 작으면 그쪽에서 만난다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-parallel-angles',
    svg: (() => {
        const W = 770, H = 306;
        const g = [];
        g.push(txt(W / 2, 26, '평행선이 만드는 각과, 그것으로 얻는 첫 정리', { anchor: 'middle', cls: 'ink bold' }));
        g.push(panel(14, 42, 356, 240, '동위각과 엇각은 같다'));
        const yA = 128, yB = 214;
        const T = t => [110 + 0.85 * t, 96 + t];
        g.push(line([[50, yA], [340, yA]], { stroke: C1, sw: 2 }));
        g.push(line([[50, yB], [340, yB]], { stroke: C1, sw: 2 }));
        g.push(line([T(-24), T(150)], { stroke: C2, sw: 2 }));
        const iA = [110 + 0.85 * (yA - 96), yA];
        const iB = [110 + 0.85 * (yB - 96), yB];
        g.push(dot(iA, '', { cls: 'fk', r: 3.5 }));
        g.push(dot(iB, '', { cls: 'fk', r: 3.5 }));
        g.push(angleMark(iA, [iA[0] + 60, yA], [iA[0] + 0.85 * 50, yA + 50], 26, 'α'));
        g.push(angleMark(iB, [iB[0] + 60, yB], [iB[0] + 0.85 * 50, yB + 50], 26, 'α'));
        g.push(angleMark(iB, [iB[0] - 0.85 * 50, yB - 50], [iB[0] - 60, yB], 26, 'α'));
        g.push(txt(338, yA - 12, '두 직선은 평행', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(196, 268, '같은 자리의 각(동위각)과 엇갈린 각(엇각)이 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(panel(390, 42, 366, 240, '삼각형 세 내각의 합은 180°'));
        const A = [520, 140], B = [430, 246], Cc = [656, 246];
        g.push(poly([A, B, Cc], { fill: C1, op: 0.1, stroke: C1, sw: 2 }));
        g.push(line([[424, 140], [666, 140]], { stroke: C2, sw: 1.8, dash: '6 4' }));
        g.push(txt(672, 126, 'A 를 지나는 평행선', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(dot(A, 'A', { dx: -4, dy: -12, anchor: 'middle', cls: 'fk' }));
        g.push(dot(B, 'B', { dx: -12, dy: 14, anchor: 'middle', cls: 'fk' }));
        g.push(dot(Cc, 'C', { dx: 12, dy: 14, anchor: 'middle', cls: 'fk' }));
        g.push(angleMark(B, Cc, A, 30, 'β'));
        g.push(angleMark(Cc, A, B, 30, 'γ'));
        g.push(angleMark(A, B, [440, 140], 26, 'β'));
        g.push(angleMark(A, [650, 140], Cc, 26, 'γ'));
        g.push(angleMark(A, Cc, B, 40, 'α'));
        g.push(txt(573, 272, 'A 에 모인 β + α + γ 가 한 직선을 이룬다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '평행선의 각과 삼각형 내각의 합', desc: '엇각이 같다는 사실에서 삼각형 세 내각의 합이 180° 라는 정리가 나온다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-similar',
    svg: (() => {
        const W = 740, H = 300;
        const g = [];
        g.push(txt(W / 2, 28, '닮음비가 1 : 2 이면 넓이비는 1 : 4', { anchor: 'middle', cls: 'ink bold' }));
        const a = [56, 240], b = [156, 240], c = [106, 154];
        g.push(poly([a, b, c], { fill: C1, op: 0.16, stroke: C1, sw: 2 }));
        g.push(angleMark(a, b, c, 20, ''));
        g.push(angleMark(b, c, a, 20, ''));
        g.push(txt(106, 262, '변의 길이 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(106, 208, '넓이 1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        const A = [240, 240], B = [440, 240], Cc = [340, 68];
        const mAB = mid(A, B), mBC = mid(B, Cc), mCA = mid(Cc, A);
        g.push(poly([A, B, Cc], { fill: C1, op: 0.16, stroke: C1, sw: 2 }));
        g.push(line([mAB, mBC], { stroke: C2, sw: 1.5 }));
        g.push(line([mBC, mCA], { stroke: C2, sw: 1.5 }));
        g.push(line([mCA, mAB], { stroke: C2, sw: 1.5 }));
        g.push(angleMark(A, B, Cc, 20, ''));
        g.push(angleMark(B, Cc, A, 20, ''));
        g.push(txt(340, 262, '변의 길이 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        [[290, 226], [390, 226], [340, 140], [340, 198]].forEach(p => g.push(txt(p[0], p[1], '1', { anchor: 'middle', cls: 'ink', size: 'sm' })));
        g.push(txt(340, 52, '넓이 4', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        const tx = 496;
        g.push(line([[tx - 14, 74], [tx - 14, 256]], { stroke: 'var(--grid)', sw: 1.4 }));
        g.push(txt(tx, 96, '두 각이 같으면 닮음이다', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 122, '(나머지 한 각은 저절로 같다)', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 156, '대응변의 비가 모두 같고', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 180, '그 비를 닮음비라 한다', { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx, 214, '닮음비 k → 넓이비 k²', { cls: 'ink bold' }));
        g.push(txt(tx, 240, '큰 삼각형이 작은 것 네 개로 나뉜다', { cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '닮음비와 넓이비', desc: '변을 두 배로 늘린 삼각형은 원래 삼각형 네 개로 정확히 나뉜다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-inscribed-angle',
    svg: (() => {
        const W = 786, H = 348;
        const g = [];
        g.push(txt(W / 2, 26, '원주각 = 중심각의 절반 — 세 경우로 나누어 증명한다', { anchor: 'middle', cls: 'ink bold' }));
        const one = (cx, cy, r, degP, degA, degB, title, note, lab) => {
            const P = onC(cx, cy, r, degP), A = onC(cx, cy, r, degA), B = onC(cx, cy, r, degB);
            const O = [cx, cy];
            const Q = onC(cx, cy, r, degP + 180);
            const out = [];
            out.push(txt(cx, cy - r - 42, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            out.push(circ(cx, cy, r, { stroke: 'var(--ink2)', sw: 1.5 }));
            out.push(line([P, A], { stroke: C1, sw: 1.8 }));
            out.push(line([P, B], { stroke: C1, sw: 1.8 }));
            out.push(line([O, A], { stroke: C2, sw: 1.8 }));
            out.push(line([O, B], { stroke: C2, sw: 1.8 }));
            out.push(line([P, Q], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
            out.push(angleMark(P, A, B, 24, 'θ'));
            out.push(angleMark(O, A, B, 22, '2θ'));
            out.push(dot(P, 'P', lab.P));
            out.push(dot(A, 'A', lab.A));
            out.push(dot(B, 'B', lab.B));
            out.push(dot(O, 'O', { ...lab.O, r: 3.5 }));
            out.push(txt(cx, cy + r + 44, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            return out.join('');
        };
        const up = { dx: 0, dy: -12, anchor: 'middle', cls: 'fk' };
        const lft = { dx: -11, dy: 6, anchor: 'end', cls: 'fk' };
        const rgt = { dx: 11, dy: 6, cls: 'fk' };
        g.push(one(140, 176, 84, 90, 200, 340, '① O 가 각 안에 있다', '이등변삼각형 둘로 쪼갠다',
            { P: up, A: lft, B: rgt, O: { dx: 9, dy: -6, cls: 'fk' } }));
        g.push(one(393, 176, 84, 90, 190, 270, '② O 가 변 위에 있다', '이등변삼각형 하나로 끝난다',
            { P: up, A: lft, B: { dx: 10, dy: 14, cls: 'fk' }, O: { dx: 10, dy: -6, cls: 'fk' } }));
        g.push(one(646, 176, 84, 150, 30, 350, '③ O 가 각 밖에 있다', '두 각의 차로 얻는다',
            { P: { dx: -10, dy: -6, anchor: 'end', cls: 'fk' }, A: { dx: 6, dy: -10, cls: 'fk' }, B: rgt, O: { dx: -2, dy: 20, anchor: 'middle', cls: 'fk' } }));
        g.push(txt(W / 2, H - 10, '세 경우 어디서나 ∠AOB = 2∠APB 다. P 를 원 위에서 옮겨도 원주각은 변하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '원주각 정리의 세 가지 경우', desc: '중심 O 가 원주각 안, 변 위, 밖에 있는 세 경우로 나누어 원주각이 중심각의 절반임을 본다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-circle-corollary',
    svg: (() => {
        const W = 786, H = 336;
        const g = [];
        g.push(txt(W / 2, 26, '원주각 정리에서 바로 나오는 세 가지', { anchor: 'middle', cls: 'ink bold' }));
        // ① 지름에 대한 원주각은 직각
        {
            const cx = 140, cy = 178, r = 82;
            const A = onC(cx, cy, r, 180), B = onC(cx, cy, r, 0), P = onC(cx, cy, r, 118);
            g.push(txt(cx, cy - r - 40, '① 지름에 대한 원주각', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(circ(cx, cy, r, { stroke: 'var(--ink2)', sw: 1.5 }));
            g.push(line([A, B], { stroke: C2, sw: 1.8 }));
            g.push(poly([A, B, P], { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
            g.push(rightAngle(P, A, B, 13));
            g.push(dot(A, 'A', { dx: -10, dy: 6, anchor: 'end', cls: 'fk' }));
            g.push(dot(B, 'B', { dx: 10, dy: 6, cls: 'fk' }));
            g.push(dot(P, 'P', { dx: 0, dy: -12, anchor: 'middle', cls: 'fk' }));
            g.push(dot([cx, cy], 'O', { dx: 6, dy: 16, cls: 'fk', r: 3.5 }));
            g.push(txt(cx, cy + r + 42, '중심각이 180° 이므로 항상 90°', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        // ② 내접사각형
        {
            const cx = 393, cy = 178, r = 82;
            const A = onC(cx, cy, r, 112), B = onC(cx, cy, r, 196), Cc = onC(cx, cy, r, 292), D = onC(cx, cy, r, 20);
            g.push(txt(cx, cy - r - 40, '② 원에 내접하는 사각형', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(circ(cx, cy, r, { stroke: 'var(--ink2)', sw: 1.5 }));
            g.push(poly([A, B, Cc, D], { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
            g.push(angleMark(A, B, D, 22, 'α'));
            g.push(angleMark(Cc, D, B, 22, ''));
            g.push(txt(Cc[0], Cc[1] - 26, '180° − α', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(dot(A, 'A', { dx: 0, dy: -12, anchor: 'middle', cls: 'fk' }));
            g.push(dot(B, 'B', { dx: -10, dy: 4, anchor: 'end', cls: 'fk' }));
            g.push(dot(Cc, 'C', { dx: -4, dy: 18, anchor: 'middle', cls: 'fk' }));
            g.push(dot(D, 'D', { dx: 10, dy: 2, cls: 'fk' }));
            g.push(txt(cx, cy + r + 42, '마주 보는 두 내각의 합이 180°', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        // ③ 접현각
        {
            const cx = 646, cy = 168, r = 78;
            const T = onC(cx, cy, r, 270), A = onC(cx, cy, r, 172), P = onC(cx, cy, r, 40);
            g.push(txt(cx, cy - r - 30, '③ 접선과 현이 이루는 각', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(circ(cx, cy, r, { stroke: 'var(--ink2)', sw: 1.5 }));
            g.push(line([[cx - 116, T[1]], [cx + 106, T[1]]], { stroke: C2, sw: 1.8 }));
            g.push(txt(cx - 120, T[1] + 4, 'Q', { anchor: 'end', cls: 'ink', size: 'sm' }));
            g.push(line([T, A], { stroke: C1, sw: 1.8 }));
            g.push(line([T, P], { stroke: C1, sw: 1.4 }));
            g.push(line([A, P], { stroke: C1, sw: 1.4 }));
            g.push(angleMark(T, [cx - 116, T[1]], A, 26, ''));
            g.push(angleMark(P, A, T, 24, ''));
            g.push(dot(T, 'T', { dx: 6, dy: 16, cls: 'fk' }));
            g.push(dot(A, 'A', { dx: -10, dy: 4, anchor: 'end', cls: 'fk' }));
            g.push(dot(P, 'P', { dx: 10, dy: -4, cls: 'fk' }));
            g.push(txt(cx, cy + r + 52, '접현각 ∠ATQ 와 원주각 ∠APT 가 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        g.push(txt(W / 2, H - 10, '셋 다 ‘같은 호를 바라보는 각’이라는 한 가지 사실의 다른 얼굴이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '원주각 정리의 따름정리', desc: '지름의 원주각, 내접사각형의 대각, 접현각이 모두 원주각 정리에서 나온다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-power-point',
    svg: (() => {
        const W = 766, H = 352;
        const g = [];
        g.push(txt(W / 2, 26, '방멱 — 점을 지나는 직선을 어떻게 긋든 두 길이의 곱이 같다', { anchor: 'middle', cls: 'ink bold' }));
        // 안쪽
        {
            const O = [172, 194], r = 95, P = [192, 214];
            const A = [261.97, 224.47], B = [77.03, 196.8];
            const Cc = [241.15, 128.9], D = [150.17, 286.4];
            g.push(txt(172, 62, '점이 원 안에 있을 때', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(circ(O[0], O[1], r, { stroke: 'var(--ink2)', sw: 1.5 }));
            g.push(poly([A, P, Cc], { fill: C1, op: 0.14 }));
            g.push(poly([D, P, B], { fill: C2, op: 0.14 }));
            g.push(line([A, B], { stroke: C1, sw: 1.7 }));
            g.push(line([Cc, D], { stroke: C1, sw: 1.7 }));
            g.push(line([A, Cc], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
            g.push(line([D, B], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
            g.push(dot(P, 'P', { dx: 8, dy: 16, cls: 'f2' }));
            g.push(dot(A, 'A', { dx: 8, dy: 4, cls: 'fk' }));
            g.push(dot(B, 'B', { dx: -8, dy: 4, anchor: 'end', cls: 'fk' }));
            g.push(dot(Cc, 'C', { dx: 8, dy: -6, cls: 'fk' }));
            g.push(dot(D, 'D', { dx: -8, dy: 12, anchor: 'end', cls: 'fk' }));
            g.push(dot(O, 'O', { dx: -14, dy: -6, anchor: 'end', cls: 'fk', r: 3.5 }));
            g.push(txt(172, 322, '△APC 와 △DPB 가 닮음이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(txt(172, 342, 'PA · PB = PC · PD', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        // 바깥
        {
            const O = [592, 190], r = 90, P = [392, 225];
            const Cc = [506.95, 219.4], D = [679.49, 211], T = [538.8, 117.4];
            g.push(txt(560, 62, '점이 원 밖에 있을 때', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(circ(O[0], O[1], r, { stroke: 'var(--ink2)', sw: 1.5 }));
            g.push(line([P, D], { stroke: C1, sw: 1.7 }));
            g.push(line([P, T], { stroke: C2, sw: 1.7 }));
            g.push(line([O, T], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
            g.push(rightAngle(T, O, P, 11));
            g.push(dot(P, 'P', { dx: -8, dy: 14, anchor: 'end', cls: 'f2' }));
            g.push(dot(Cc, 'C', { dx: -2, dy: 20, anchor: 'middle', cls: 'fk' }));
            g.push(dot(D, 'D', { dx: 9, dy: 4, cls: 'fk' }));
            g.push(dot(T, 'T', { dx: 0, dy: -12, anchor: 'middle', cls: 'fk' }));
            g.push(dot(O, 'O', { dx: 4, dy: -10, cls: 'fk', r: 3.5 }));
            g.push(txt(560, 322, '접선이면 두 교점이 T 하나로 겹친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(txt(560, 342, 'PC · PD = PT²', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        return svg({ width: W, height: H, title: '방멱정리', desc: '점을 지나는 직선을 어떻게 긋든 원과 만나는 두 점까지의 거리의 곱이 일정하다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-sin-def',
    svg: (() => {
        const W = 750, H = 320;
        const g = [];
        g.push(txt(W / 2, 26, '직각삼각형의 삼각비 — 각이 같으면 비도 같다', { anchor: 'middle', cls: 'ink bold' }));
        const A = [50, 258], Cb = [260, 258], Bb = [260, 132];
        const Cs = [155, 258], Bs = [155, 195];
        g.push(poly([A, Cb, Bb], { fill: C1, op: 0.1, stroke: C1, sw: 1.9 }));
        g.push(line([Cs, Bs], { stroke: C2, sw: 1.9 }));
        g.push(rightAngle(Cb, A, Bb, 12));
        g.push(rightAngle(Cs, A, Bs, 10));
        g.push(angleMark(A, Cb, Bb, 40, 'θ'));
        g.push(dot(A, '', { cls: 'fk', r: 3.5 }));
        g.push(txt(155, 300, '작은 삼각형과 큰 삼각형은 닮음', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(150, 174, '빗변', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(276, 198, '높이 (θ 의 대변)', { cls: 'ink', size: 'sm' }));
        g.push(txt(155, 276, '밑변 (θ 의 이웃변)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        const tx = 404;
        g.push(line([[tx - 16, 58], [tx - 16, 300]], { stroke: 'var(--grid)', sw: 1.4 }));
        g.push(txt(tx, 80, 'sin θ = 높이 ÷ 빗변', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(tx, 104, 'cos θ = 밑변 ÷ 빗변', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(tx, 128, 'tan θ = 높이 ÷ 밑변', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(tx, 152, '크기와 무관하게 θ 만으로 정해진다', { cls: 'ink2', size: 'sm' }));
        const a1 = [tx + 12, 268], c1 = [tx + 92, 268], b1 = [tx + 92, 188];
        g.push(poly([a1, c1, b1], { fill: C1, op: 0.12, stroke: C1, sw: 1.8 }));
        g.push(rightAngle(c1, a1, b1, 10));
        g.push(angleMark(a1, c1, b1, 26, '45°'));
        g.push(txt(tx + 52, 286, '1', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 100, 232, '1', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 38, 216, '√2', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        const a2 = [tx + 168, 268], c2 = [tx + 238, 268], b2 = [tx + 238, 147];
        g.push(poly([a2, c2, b2], { fill: C2, op: 0.12, stroke: C2, sw: 1.8 }));
        g.push(rightAngle(c2, a2, b2, 10));
        g.push(angleMark(a2, c2, b2, 28, '60°'));
        g.push(angleMark(b2, a2, c2, 24, '30°'));
        g.push(txt(tx + 203, 286, '1', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 246, 212, '√3', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 186, 200, '2', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 124, 306, '자주 쓰는 두 삼각형', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '직각삼각형의 삼각비', desc: '닮음 때문에 변의 비가 각만으로 정해진다. 45° 와 30°·60° 의 값은 두 삼각형에서 바로 읽는다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-law-sines',
    svg: (() => {
        const W = 760, H = 344;
        const g = [];
        const O = [292, 186], r = 110;
        const B = onC(O[0], O[1], r, 215);
        const Cc = onC(O[0], O[1], r, 335);
        const A = onC(O[0], O[1], r, 115);
        const A2 = onC(O[0], O[1], r, 35);
        g.push(txt(W / 2, 26, '사인법칙은 외접원의 지름에서 나온다', { anchor: 'middle', cls: 'ink bold' }));
        g.push(circ(O[0], O[1], r, { stroke: 'var(--ink2)', sw: 1.4 }));
        g.push(poly([A, B, Cc], { fill: C1, op: 0.1, stroke: C1, sw: 2 }));
        g.push(line([B, A2], { stroke: C2, sw: 1.9 }));
        g.push(line([A2, Cc], { stroke: C2, sw: 1.9 }));
        g.push(rightAngle(Cc, B, A2, 12));
        g.push(angleMark(A, B, Cc, 30, 'A'));
        g.push(angleMark(A2, Cc, B, 28, 'A'));
        g.push(dot(A, 'A', { dx: -6, dy: -12, anchor: 'middle', cls: 'fk' }));
        g.push(dot(B, 'B', { dx: -10, dy: 12, anchor: 'end', cls: 'fk' }));
        g.push(dot(Cc, 'C', { dx: 10, dy: 10, cls: 'fk' }));
        g.push(dot(A2, 'A′', { dx: 10, dy: -6, cls: 'fk' }));
        g.push(dot(O, 'O', { dx: 6, dy: -8, cls: 'fk', r: 3.5 }));
        g.push(txt(mid(B, Cc)[0], mid(B, Cc)[1] + 20, 'a', { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(mid(B, A2)[0] + 4, mid(B, A2)[1] + 22, '지름 2R', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const tx = 448;
        g.push(line([[tx - 16, 70], [tx - 16, 296]], { stroke: 'var(--grid)', sw: 1.4 }));
        g.push(txt(tx, 96, 'BA′ 를 지름으로 잡으면', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 120, '∠BCA′ 는 직각이다', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 152, '∠BA′C 와 ∠BAC 는', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 176, '같은 호 BC 를 보는 원주각이라 같다', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 210, '직각삼각형 BCA′ 에서', { cls: 'ink', size: 'sm' }));
        g.push(txt(tx, 234, 'sin A = a ÷ 2R', { cls: 'ink bold' }));
        g.push(txt(tx, 266, '곧 a ÷ sin A = 2R', { cls: 'ink bold' }));
        g.push(txt(tx, 290, 'B, C 에 대해서도 똑같다', { cls: 'ink2', size: 'sm' }));
        g.push(txt(W / 2, H - 8, '세 비가 모두 같은 값 2R 이 되는 것은 셋 다 같은 외접원에서 나오기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return svg({ width: W, height: H, title: '사인법칙의 증명 그림', desc: '외접원의 지름을 그으면 직각삼각형이 생기고 거기서 a = 2R sin A 가 나온다.', body: g.join('') });
    })(),
});

add({
    name: 'math-log-law-cosines',
    svg: (() => {
        const W = 764, H = 356;
        const g = [];
        g.push(txt(W / 2, 26, '코사인법칙 — 수선을 내리고 피타고라스를 두 번 쓴다', { anchor: 'middle', cls: 'ink bold' }));
        const draw = (ox, A, Cc, B, title, note, hOutside, bdx, hdx) => {
            const H0 = [B[0], A[1]];
            const out = [txt(ox + 190, 62, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' })];
            if (hOutside) out.push(line([Cc, H0], { stroke: 'var(--ink2)', sw: 1.3, dash: '5 4' }));
            out.push(poly([A, Cc, B], { fill: C1, op: 0.1, stroke: C1, sw: 2 }));
            out.push(line([B, H0], { stroke: C2, sw: 1.8 }));
            out.push(rightAngle(H0, B, A, 11));
            out.push(dot(A, 'A', { dx: -10, dy: 12, anchor: 'end', cls: 'fk' }));
            out.push(dot(Cc, 'C', { dx: hOutside ? -4 : 6, dy: hOutside ? 20 : 20, anchor: 'middle', cls: 'fk' }));
            out.push(dot(B, 'B', { dx: 6, dy: -12, cls: 'fk' }));
            out.push(dot(H0, 'H', { dx: 4, dy: 20, anchor: 'middle', cls: 'fk', r: 3.5 }));
            out.push(txt(mid(A, B)[0] - 12, mid(A, B)[1] - 8, 'c', { anchor: 'middle', cls: 'ink bold' }));
            out.push(txt(mid(Cc, B)[0] + 14, mid(Cc, B)[1], 'a', { cls: 'ink bold' }));
            out.push(txt(mid(A, Cc)[0] + bdx, A[1] + 22, 'b', { anchor: 'middle', cls: 'ink bold' }));
            out.push(txt(B[0] + hdx, mid(B, H0)[1], 'a sin C', { anchor: hdx < 0 ? 'end' : 'start', cls: 'ink2', size: 'sm' }));
            out.push(txt(ox + 190, 296, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            return out.join('');
        };
        g.push(draw(0, [60, 244], [250, 244], [170, 118], 'C 가 예각일 때', 'CH = a cos C 이고 AH = b − a cos C', false, -26, -9));
        g.push(draw(380, [440, 244], [610, 244], [680, 118], 'C 가 둔각일 때', 'H 가 C 바깥으로 나가지만 AH = b − a cos C 는 그대로다', true, -8, 10));
        g.push(txt(W / 2, H - 30, '두 경우 모두 직각삼각형 ABH 에 피타고라스를 쓰면', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(W / 2, H - 10, 'c² = (a sin C)² + (b − a cos C)² = a² + b² − 2ab cos C', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        return svg({ width: W, height: H, title: '코사인법칙의 증명 그림', desc: '한 꼭짓점에서 대변에 수선을 내리면 어느 경우에도 같은 식이 나온다.', body: g.join('') });
    })(),
});

export default figures;
