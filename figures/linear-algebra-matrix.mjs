/**
 * 선형대수 3장(연립일차방정식과 행렬) · 4장(행렬식) · 5장(벡터와 유클리드 공간)의 그림.
 *
 * 이름은 모두 `la-mat-` 로 시작한다(담당 A 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~1` 표기를, 나머지는 유니코드(− × · ‖ θ ₁₂₃)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 문서의 원문에는 그림이 하나도 없었다. 선형대수의 계산은 전부 기하가 있고,
 * 그 기하를 보여 주는 것이 이 파일의 목적이다.
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

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 꼭짓점 v 에서 방향 d1 과 d2 사이의 직각 표시. 화소 좌표. */
function rightAngle(v, d1, d2, s = 10) {
    const u = a => { const L = Math.hypot(a[0], a[1]) || 1; return [a[0] / L * s, a[1] / L * s]; };
    const p = u(d1); const q = u(d2);
    return ln([[v[0] + p[0], v[1] + p[1]], [v[0] + p[0] + q[0], v[1] + p[1] + q[1]], [v[0] + q[0], v[1] + q[1]]],
        { stroke: CK, sw: 1.2 });
}

/** 꼭짓점 v 에서 p1 방향과 p2 방향 사이의 각을 호로 그린다. 화소 좌표. */
function angleArc(v, p1, p2, r, label, { stroke = CK, sw = 1.3, gap = 15 } = {}) {
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

/**
 * 가로세로 배율을 같게 맞춘 frame 을 만든다.
 * 배율이 다르면 직각이 직각으로 보이지 않고 평행사변형의 넓이 감각도 무너진다.
 */
function sq(xR, yR, x, y, s) {
    const w = (xR[1] - xR[0]) * s;
    const h = (yR[1] - yR[0]) * s;
    return { f: frame({ xRange: xR, yRange: yR, box: { x, y, w, h } }), w, h };
}

const varw = (f, p1, p2, o) => arw(f.X(p1[0]), f.Y(p1[1]), f.X(p2[0]), f.Y(p2[1]), o);
const dpoly = (f, pts, o) => poly(pts.map(p => [f.X(p[0]), f.Y(p[1])]), o);
const dln = (f, pts, o) => ln(pts.map(p => [f.X(p[0]), f.Y(p[1])]), o);
const S = (f, p) => [f.X(p[0]), f.Y(p[1])];

/** 상자 안의 축. lib 의 axes 는 눈금 글자가 커서 작은 패널에서 겹친다. */
function axes(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel } = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = f.Y(0);
    const ay = f.X(0);
    const g = [arw(f.X(x0), ax, f.X(x1) + 10, ax, { cls: 'ark', width: 1.2 }),
        arw(ay, f.Y(y0), ay, f.Y(y1) - 10, { cls: 'ark', width: 1.2 })];
    for (const t of xTicks) {
        if (t === 0) continue;
        g.push(ln([[f.X(t), ax - 3], [f.X(t), ax + 3]], { sw: 1 }));
        g.push(txt(f.X(t), ax + 16, nm(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        if (t === 0) continue;
        g.push(ln([[ay - 3, f.Y(t)], [ay + 3, f.Y(t)]], { sw: 1 }));
        g.push(txt(ay - 7, f.Y(t) + 4, nm(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 14, ax + 4, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay + 9, f.Y(y1) + 3, yLabel, { cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 두 점을 지나는 직선을 상자 안에서 잘라 그린다. ax + by = c 꼴로 준다. */
function lineIn(f, a, b, c, xR, yR, opt) {
    const pts = [];
    const push = (x, y) => {
        if (x >= xR[0] - 1e-9 && x <= xR[1] + 1e-9 && y >= yR[0] - 1e-9 && y <= yR[1] + 1e-9) pts.push([x, y]);
    };
    if (Math.abs(b) > 1e-9) { push(xR[0], (c - a * xR[0]) / b); push(xR[1], (c - a * xR[1]) / b); }
    if (Math.abs(a) > 1e-9) { push((c - b * yR[0]) / a, yR[0]); push((c - b * yR[1]) / a, yR[1]); }
    if (pts.length < 2) return '';
    return dln(f, [pts[0], pts[pts.length - 1]], opt);
}

/* ------------------------------------------------------------------ *
 * 3차원 스케치용 등각 투영
 * ------------------------------------------------------------------ */

const EX = [-29, 16.5];
const EY = [32, 14.5];
const EZ = [0, -32];

/** 원점을 (ox, oy) 에 두고 배율 s 로 3차원 점을 화면에 얹는다. */
const iso = (ox, oy, s = 1) => ([x, y, z]) => [
    ox + s * (x * EX[0] + y * EY[0] + z * EZ[0]),
    oy + s * (x * EX[1] + y * EY[1] + z * EZ[1]),
];

/** 세 벡터 a, b, c 의 계수 (i, j, k) 를 화면 좌표로 보내는 함수. */
const combo = (T, a, b, c) => ([i, j, k]) => T([
    i * a[0] + j * b[0] + k * c[0],
    i * a[1] + j * b[1] + k * c[1],
    i * a[2] + j * b[2] + k * c[2],
]);

const FACES = [
    [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]],
    [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]],
    [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]],
    [[0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]],
    [[0, 0, 0], [0, 1, 0], [0, 1, 1], [0, 0, 1]],
    [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]],
];
const EDGES = [
    [[0, 0, 0], [1, 0, 0]], [[0, 0, 0], [0, 1, 0]], [[0, 0, 0], [0, 0, 1]],
    [[1, 0, 0], [1, 1, 0]], [[1, 0, 0], [1, 0, 1]],
    [[0, 1, 0], [1, 1, 0]], [[0, 1, 0], [0, 1, 1]],
    [[0, 0, 1], [1, 0, 1]], [[0, 0, 1], [0, 1, 1]],
    [[1, 1, 0], [1, 1, 1]], [[1, 0, 1], [1, 1, 1]], [[0, 1, 1], [1, 1, 1]],
];

/** 세 벡터가 만드는 평행육면체. 면을 낮은 불투명도로 겹쳐 칠하면 앞뒤 정렬이 필요 없다. */
function ppiped(P, { fill = C1, op = 0.09, stroke = CK, sw = 1.3 } = {}) {
    return FACES.map(q => poly(q.map(P), { fill, op }))
        .concat(EDGES.map(e => ln(e.map(P), { stroke, sw })))
        .join('');
}

/* ================================================================== *
 * 3장 — 연립일차방정식과 행렬
 * ================================================================== */

/* ---- 3-1. 해가 없다 / 하나다 / 무수히 많다 ---- */
add((() => {
    const W = 770, H = 348;
    const xR = [-0.7, 4.2], yR = [-0.7, 4.2];
    const g = [];
    g.push(txt(W / 2, 26, '두 직선이 놓일 수 있는 방법은 세 가지뿐이다', { anchor: 'middle', cls: 'ink bold' }));
    const spec = [
        ['해가 하나', '한 점에서 만난다', [[1, 1, 3], [1, -1, 1]], ['x + y = 3', 'x − y = 1'], [2, 1]],
        ['해가 없다', '나란해서 만나지 않는다', [[1, 1, 3], [1, 1, 1]], ['x + y = 3', 'x + y = 1'], null],
        ['해가 무수히 많다', '두 식이 같은 직선이다', [[1, 1, 3], [2, 2, 6]], ['x + y = 3', '2x + 2y = 6'], null],
    ];
    spec.forEach(([title, sub, lines, texts, sol], i) => {
        const px = 18 + i * 250, py = 44, pw = 234, ph = 268;
        const { f } = sq(xR, yR, px + 42, py + 52, 30);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [1, 2, 3], xLabel: 'x', yLabel: 'y' }));
        g.push(lineIn(f, lines[0][0], lines[0][1], lines[0][2], xR, yR, { stroke: C1, sw: 2.4 }));
        g.push(lineIn(f, lines[1][0], lines[1][1], lines[1][2], xR, yR,
            { stroke: C2, sw: 2.4, dash: i === 2 ? '7 5' : undefined }));
        if (sol) {
            g.push(pdot(f.X(sol[0]), f.Y(sol[1]), C3, 5.5));
            g.push(txt(f.X(sol[0]) + 10, f.Y(sol[1]) - 9, '(2, 1)', { cls: 'ink bold', size: 'sm' }));
        }
        g.push(txt(px + pw / 2, py + 226, texts[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + 246, texts[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 12, '미지수가 둘인 일차방정식 하나는 직선 하나다. 연립방정식을 푼다는 것은 직선들의 공통점을 찾는 일이고, 그래서 답의 개수는 0 · 1 · 무한 셋뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-two-lines',
        svg: svg({
            width: W, height: H,
            title: '연립일차방정식의 해가 놓이는 세 경우',
            desc: '두 직선이 한 점에서 만나면 해가 하나, 나란하면 해가 없고, 겹치면 해가 무수히 많다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-2. 소거법이 직선을 옮긴다 ---- */
add((() => {
    const W = 770, H = 348;
    const xR = [-0.7, 4.2], yR = [-0.7, 4.2];
    const g = [];
    g.push(txt(W / 2, 26, '가우스 소거법은 교점을 그대로 둔 채 직선만 단순하게 바꾼다', { anchor: 'middle', cls: 'ink bold' }));
    const spec = [
        ['처음', '기울어진 두 직선', [[1, 1, 3], [1, -1, 1]], ['x + y = 3', 'x − y = 1']],
        ['R₂ ← R₁ − R₂', '한 직선이 수평이 되었다', [[1, 1, 3], [0, 1, 1]], ['x + y = 3', 'y = 1']],
        ['R₁ ← R₁ − R₂', '읽으면 그대로 답이다', [[1, 0, 2], [0, 1, 1]], ['x = 2', 'y = 1']],
    ];
    spec.forEach(([title, sub, lines, texts], i) => {
        const px = 18 + i * 250, py = 44, pw = 234, ph = 268;
        const { f } = sq(xR, yR, px + 42, py + 52, 30);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [1, 2, 3], xLabel: 'x', yLabel: 'y' }));
        g.push(lineIn(f, lines[0][0], lines[0][1], lines[0][2], xR, yR, { stroke: C1, sw: 2.4 }));
        g.push(lineIn(f, lines[1][0], lines[1][1], lines[1][2], xR, yR, { stroke: C2, sw: 2.4 }));
        g.push(pdot(f.X(2), f.Y(1), C3, 5.5));
        g.push(txt(f.X(2) + 10, f.Y(1) - 9, '(2, 1)', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + 226, texts[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + 246, texts[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i < 2) g.push(arw(px + pw + 3, py + 130, px + pw + 13, py + 130, { cls: 'ark', width: 1.6 }));
    });
    g.push(txt(W / 2, H - 12, '초록 점은 한 번도 움직이지 않았다. 행 연산이 해집합을 바꾸지 않는다는 말의 뜻이 이것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-gauss-lines',
        svg: svg({
            width: W, height: H,
            title: '가우스 소거법이 두 직선에 하는 일',
            desc: '행 연산을 거듭할수록 직선은 축과 나란해지고 교점은 그대로 남는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-3. 행사다리꼴과 기약 행사다리꼴 ---- */
add((() => {
    const W = 760, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '소거를 끝낸 행렬의 모양 — 계단이 내려가는 자리가 축이다', { anchor: 'middle', cls: 'ink bold' }));
    const REF = [
        ['1', '∗', '∗', '∗', '∗'],
        ['0', '0', '1', '∗', '∗'],
        ['0', '0', '0', '1', '∗'],
        ['0', '0', '0', '0', '0'],
    ];
    const RREF = [
        ['1', '∗', '0', '0', '∗'],
        ['0', '0', '1', '0', '∗'],
        ['0', '0', '0', '1', '∗'],
        ['0', '0', '0', '0', '0'],
    ];
    const piv = [[0, 0], [1, 2], [2, 3]];
    const draw = (px, title, sub, M, note) => {
        const py = 46, pw = 340, ph = 236;
        const cw = 50, ch = 34, gx = px + 45, gy = py + 54;
        const out = [panel(px, py, pw, ph, title, sub)];
        M.forEach((row, r) => row.forEach((v, c) => {
            const on = piv.some(p => p[0] === r && p[1] === c);
            out.push(box(gx + c * cw, gy + r * ch, cw, ch, { stroke: 'var(--grid)', sw: 1, rx: 2, fill: on ? C1 : 'none', op: on ? 0.25 : 0 }));
            out.push(txt(gx + c * cw + cw / 2, gy + r * ch + ch / 2 + 5, v, { anchor: 'middle', cls: on ? 'ink bold' : 'ink', size: 'sm' }));
        }));
        const stair = [[0, 1], [2, 1], [2, 2], [3, 2], [3, 3], [5, 3]]
            .map(([c, r]) => [gx + c * cw, gy + r * ch]);
        out.push(ln(stair, { stroke: C2, sw: 2.6 }));
        out.push(txt(px + pw / 2, py + ph - 18, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(draw(24, '행사다리꼴 (REF)', '축 아래가 모두 0', REF, '계단 아래는 전부 0. 축은 오른쪽으로 밀려 가며 내려간다'));
    g.push(draw(396, '기약 행사다리꼴 (RREF)', '축 위아래가 모두 0이고 축은 1', RREF, '축이 있는 열은 단위벡터가 된다. 여기까지 오면 해를 그냥 읽는다'));
    g.push(txt(W / 2, H - 12, '별표 자리에는 어떤 수가 와도 좋다. 축이 없는 열이 자유변수의 자리이고, 그 개수가 해의 자유도다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-echelon',
        svg: svg({
            width: W, height: H,
            title: '행사다리꼴과 기약 행사다리꼴의 모양',
            desc: '축의 위치가 계단을 이루고, 기약 행사다리꼴에서는 축이 있는 열이 단위벡터가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-4. 세 평면 ---- */
add((() => {
    const W = 770, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '미지수가 셋이면 방정식 하나가 평면 하나다', { anchor: 'middle', cls: 'ink bold' }));
    const para = (c, a, b) => [
        [c[0] - a[0] - b[0], c[1] - a[1] - b[1]],
        [c[0] + a[0] - b[0], c[1] + a[1] - b[1]],
        [c[0] + a[0] + b[0], c[1] + a[1] + b[1]],
        [c[0] - a[0] + b[0], c[1] - a[1] + b[1]],
    ];
    const plane = (c, a, b, col) => poly(para(c, a, b), { fill: col, op: 0.16, stroke: col, sw: 1.4 });
    const spec = [
        ['한 점에서 만난다', '해가 하나'],
        ['한 직선을 공유한다', '해가 무수히 많다'],
        ['공통점이 없다', '해가 없다'],
    ];
    spec.forEach(([title, sub], i) => {
        const px = 18 + i * 250, py = 44, pw = 234, ph = 234;
        const cx = px + pw / 2, cy = py + 132;
        g.push(panel(px, py, pw, ph, title, sub));
        if (i === 0) {
            g.push(plane([cx, cy], [52, 11], [-24, 21], C1));
            g.push(plane([cx, cy], [52, 11], [0, -46], C2));
            g.push(plane([cx, cy], [-24, 21], [0, -46], C3));
            g.push(pdot(cx, cy, 'var(--ink)', 5));
            g.push(txt(cx, cy + 90, '공통점 하나', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        } else if (i === 1) {
            g.push(plane([cx, cy], [52, 11], [0, -46], C1));
            g.push(plane([cx, cy], [30, 26], [0, -46], C2));
            g.push(plane([cx, cy], [-24, 21], [0, -46], C3));
            g.push(ln([[cx, cy - 62], [cx, cy + 62]], { stroke: 'var(--ink)', sw: 3 }));
            g.push(txt(cx, cy + 90, '공통 직선', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        } else {
            g.push(plane([cx, cy - 34], [52, 11], [-24, 21], C1));
            g.push(plane([cx, cy + 34], [52, 11], [-24, 21], C1));
            g.push(plane([cx, cy], [52, 11], [0, -50], C2));
            g.push(ln([[cx - 52, cy - 45], [cx + 52, cy - 23]], { stroke: C3, sw: 2.6 }));
            g.push(ln([[cx - 52, cy + 23], [cx + 52, cy + 45]], { stroke: C3, sw: 2.6 }));
            g.push(txt(cx, cy + 90, '두 교선이 나란하다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(txt(W / 2, H - 12, '평면이 셋이어도 답의 개수는 여전히 0 · 1 · 무한뿐이다. 가운데처럼 한 직선을 공유하면 그 직선 위의 점이 모두 해가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-three-planes',
        svg: svg({
            width: W, height: H,
            title: '세 평면이 놓이는 방법과 해의 개수',
            desc: '한 점에서 만나면 해가 하나, 한 직선을 공유하면 무수히 많고, 공통점이 없으면 해가 없다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-5. 행렬 곱은 변환의 합성이다 ---- */
add((() => {
    const W = 780, H = 322;
    const xR = [-0.5, 4.5], yR = [-0.5, 2.7];
    const g = [];
    g.push(txt(W / 2, 26, '행렬 곱이 그렇게 정의된 이유 — 두 변환을 이어 붙인 것이 곱이다', { anchor: 'middle', cls: 'ink bold' }));
    const stage = [
        ['시작', '단위 정사각형', [[0, 0], [1, 0], [1, 1], [0, 1]], [[1, 0], [0, 1]], ['e~1', 'e~2']],
        ['B 를 적용한 뒤', 'B 의 두 열이 상이 된다', [[0, 0], [1, 0], [2, 1], [1, 1]], [[1, 0], [1, 1]], ['Be~1', 'Be~2']],
        ['다시 A 를 적용하면', 'AB 의 두 열이 나온다', [[0, 0], [2, 0], [4, 1], [2, 1]], [[2, 0], [2, 1]], ['ABe~1', 'ABe~2']],
    ];
    stage.forEach(([title, sub, quad, vecs, names], i) => {
        const px = 24 + i * 254, py = 46, pw = 224, ph = 178;
        const { f } = sq(xR, yR, px + 18, py + 46, 37.6);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [1, 2] }));
        g.push(dpoly(f, quad, { fill: C3, op: 0.2 }));
        g.push(varw(f, [0, 0], vecs[0], { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], vecs[1], { cls: 's2', width: 2.4 }));
        g.push(txt(f.X(vecs[0][0]) + 7, f.Y(vecs[0][1]) - 8, names[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(vecs[1][0]) + 7, f.Y(vecs[1][1]) - 8, names[1], { cls: 'ink bold', size: 'sm' }));
        if (i < 2) {
            g.push(arw(px + pw + 4, py + 100, px + pw + 26, py + 100, { cls: 'ark', width: 1.7 }));
            g.push(txt(px + pw + 15, py + 90, i === 0 ? 'B' : 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(arw(140, 258, 640, 258, { cls: 'ark', width: 1.7 }));
    g.push(txt(W / 2, 250, 'AB 를 한 번 곱한 것과 결과가 같다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 286, 'AB 의 j 번째 열은 A 에 B 의 j 번째 열을 곱한 것이다. 곱의 정의는 이 그림을 좌표로 옮겨 적은 것뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 8, '그래서 곱은 오른쪽 것을 먼저 적용한다. AB 는 ‘B 를 하고 나서 A 를 한다’ 는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-product-compose',
        svg: svg({
            width: W, height: H,
            title: '행렬 곱은 두 변환의 합성이다',
            desc: '단위 정사각형에 B 를 적용하고 다시 A 를 적용한 결과가 AB 를 한 번 적용한 결과와 같다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-6. 교환법칙이 성립하지 않는다 ---- */
add((() => {
    const W = 706, H = 430;
    const xR = [-1.6, 1.6], yR = [-1.6, 1.6];
    const g = [];
    g.push(txt(W / 2, 26, '순서를 바꾸면 다른 변환이 된다', { anchor: 'middle', cls: 'ink bold' }));
    const L = [[0, 0], [1.2, 0], [1.2, 0.3], [0.35, 0.3], [0.35, 1.0], [0, 1.0]];
    const A = ([x, y]) => [-y, x];
    const B = ([x, y]) => [x, -y];
    const rows = [
        ['먼저 B, 다음 A  (AB)', [L, L.map(B), L.map(p => A(B(p)))], ['B — x축 반사', 'A — 90° 회전'], 'AB 는 직선 y = x 에 대한 반사'],
        ['먼저 A, 다음 B  (BA)', [L, L.map(A), L.map(p => B(A(p)))], ['A — 90° 회전', 'B — x축 반사'], 'BA 는 직선 y = −x 에 대한 반사'],
    ];
    rows.forEach(([rowTitle, shapes, arrows, note], r) => {
        const py = 54 + r * 166;
        g.push(txt(16, py + 10, rowTitle, { cls: 'ink bold', size: 'sm' }));
        shapes.forEach((shape, i) => {
            const px = 30 + i * 246, pw = 146, ph = 132;
            const { f } = sq(xR, yR, px + 14, py + 22, 36.9);
            g.push(panel(px, py + 18, pw, ph));
            g.push(axes(f, { xRange: xR, yRange: yR }));
            g.push(dpoly(f, shape, { fill: i === 2 ? C2 : C1, op: 0.3, stroke: i === 2 ? C2 : C1, sw: 1.6 }));
            if (i < 2) {
                g.push(arw(px + pw + 8, py + 84, px + pw + 88, py + 84, { cls: 'ark', width: 1.7 }));
                g.push(txt(px + pw + 48, py + 74, arrows[i], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
        });
        g.push(txt(595, py + 166, note, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 26, '같은 두 행렬을 곱했는데 결과가 다르다. 행렬 곱에는 교환법칙이 없다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, H - 8, '반면 결합법칙은 성립한다. (AB)C 도 A(BC) 도 ‘C, B, A 순으로 적용한다’ 는 같은 말이기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-noncommute',
        svg: svg({
            width: W, height: H,
            title: 'AB 와 BA 는 다르다',
            desc: '90도 회전과 x축 반사를 서로 다른 순서로 합성하면 서로 다른 반사가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-7. 역행렬은 되돌리기다 ---- */
add((() => {
    const W = 750, H = 300;
    const xR = [-0.4, 3.4], yR = [-0.4, 2.4];
    const g = [];
    g.push(txt(W / 2, 26, '역행렬은 A 가 한 일을 정확히 되돌리는 변환이다', { anchor: 'middle', cls: 'ink bold' }));
    const M = ([x, y]) => [2 * x + y, x + y];
    const cell = [[0, 0], [0.5, 0], [0.5, 0.5], [0, 0.5]];
    const grids = [];
    for (let i = 0; i < 2; i += 1) {
        for (let j = 0; j < 2; j += 1) grids.push(cell.map(([x, y]) => [x + i * 0.5, y + j * 0.5]));
    }
    const stage = [
        ['시작', '한 변이 1 인 정사각형', p => p, C1],
        ['A 를 적용', '기울었다 (넓이는 그대로)', M, C2],
        ['A⁻¹ 를 다시 적용', '처음 자리로 돌아왔다', p => p, C1],
    ];
    stage.forEach(([title, sub, T, col], i) => {
        const px = 22 + i * 244, pw = 222, ph = 190;
        const py = 46;
        const { f } = sq(xR, yR, px + 24, py + 48, 46);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [1, 2] }));
        grids.forEach(q => g.push(dpoly(f, q.map(T), { fill: col, op: 0.16, stroke: col, sw: 1.2 })));
        g.push(varw(f, [0, 0], T([1, 0]), { cls: 's1', width: 2.2 }));
        g.push(varw(f, [0, 0], T([0, 1]), { cls: 's2', width: 2.2 }));
        if (i < 2) {
            g.push(arw(px + pw + 3, py + 100, px + pw + 19, py + 100, { cls: 'ark', width: 1.7 }));
            g.push(txt(px + pw + 11, py + 90, i === 0 ? 'A' : 'A⁻¹', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(txt(W / 2, H - 12, '되돌릴 수 있으려면 A 가 서로 다른 점을 서로 다른 점으로 보내야 한다. 언제 그렇지 못한지는 4장에서 행렬식이 답한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-inverse-grid',
        svg: svg({
            width: W, height: H,
            title: '역행렬은 변환을 되돌린다',
            desc: '정사각형 격자에 A 를 적용해 기울인 뒤 역행렬을 적용하면 원래 격자로 돌아온다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4장 — 행렬식
 * ================================================================== */

/* ---- 4-1. 2×2 행렬식은 넓이다 ---- */
add((() => {
    const W = 790, H = 322;
    const xR = [-0.4, 4.4], yR = [-0.4, 3.4];
    const u = [3, 1], v = [1, 2];
    const g = [];
    g.push(txt(W / 2, 26, '2×2 행렬식은 두 열이 만드는 평행사변형의 넓이다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = (i) => {
        const px = 16 + i * 258, py = 44, pw = 242, ph = 250;
        const { f } = sq(xR, yR, px + 34, py + 40, 36);
        return { px, py, pw, ph, f };
    };
    {
        const { px, py, pw, ph, f } = mk(0);
        g.push(panel(px, py, pw, ph, '평행사변형의 넓이'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [1, 2, 3] }));
        g.push(dpoly(f, [[0, 0], u, [u[0] + v[0], u[1] + v[1]], v], { fill: C3, op: 0.22, stroke: C3, sw: 1.4 }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], v, { cls: 's2', width: 2.6 }));
        g.push(txt(f.X(3) + 7, f.Y(1) + 4, 'u = (3, 1)', { cls: 'ink', size: 'sm' }));
        g.push(txt(f.X(1) + 7, f.Y(2) - 7, 'v = (1, 2)', { cls: 'ink', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 48, '넓이 = 5', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 28, '3 · 2 − 1 · 1 = 5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(1);
        g.push(panel(px, py, pw, ph, '왜 ad − bc 인가'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [1, 2, 3] }));
        g.push(dpoly(f, [[0, 0], [4, 0], [4, 3], [0, 3]], { fill: CK, op: 0.05, stroke: CK, sw: 1.2, dash: '5 4' }));
        [[[0, 0], [3, 0], [3, 1]], [[1, 2], [1, 3], [4, 3]]].forEach(q =>
            g.push(dpoly(f, q, { fill: C1, op: 0.3, stroke: C1, sw: 1 })));
        [[[0, 0], [0, 2], [1, 2]], [[3, 1], [4, 1], [4, 3]]].forEach(q =>
            g.push(dpoly(f, q, { fill: C2, op: 0.3, stroke: C2, sw: 1 })));
        [[[3, 0], [4, 0], [4, 1], [3, 1]], [[0, 2], [1, 2], [1, 3], [0, 3]]].forEach(q =>
            g.push(dpoly(f, q, { fill: CK, op: 0.25, stroke: CK, sw: 1 })));
        g.push(dpoly(f, [[0, 0], u, [4, 3], v], { fill: C3, op: 0, stroke: C3, sw: 2.4 }));
        g.push(txt(f.X(3.5), f.Y(0.5) + 4, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(0.5), f.Y(2.5) + 4, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(2), f.Y(0.3) + 4, '1.5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(2.4), f.Y(2.75) + 4, '1.5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(0.3), f.Y(1.4) + 4, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(3.75), f.Y(1.7) + 4, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 62, '큰 직사각형 4 × 3 = 12', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 44, '바깥 조각 여섯의 넓이 합 = 7', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 24, '12 − 7 = 5', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(2);
        g.push(panel(px, py, pw, ph, '순서를 바꾸면 부호가 바뀐다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [1, 2, 3] }));
        g.push(dpoly(f, [[0, 0], u, [4, 3], v], { fill: C3, op: 0.18, stroke: C3, sw: 1.4 }));
        g.push(varw(f, [0, 0], v, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], u, { cls: 's2', width: 2.6 }));
        g.push(angleArc(S(f, [0, 0]), S(f, v), S(f, u), 46, '시계 방향'));
        g.push(txt(px + pw / 2, py + ph - 48, '넓이는 그대로 5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 28, '1 · 1 − 2 · 3 = −5', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '행렬식은 넓이에 방향을 붙인 값이다. 첫 벡터에서 둘째 벡터로 반시계로 돌면 양수, 시계로 돌면 음수다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-det-area',
        svg: svg({
            width: W, height: H,
            title: '2×2 행렬식과 평행사변형의 넓이',
            desc: 'ad − bc 가 평행사변형의 넓이이고 부호는 두 벡터의 회전 방향을 뜻한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-2. 행 연산이 행렬식에 하는 일 ---- */
add((() => {
    const W = 748, H = 348;
    const xR = [-0.5, 5.5], yR = [-0.5, 5.5];
    const u = [2, 1], v = [1, 2];
    const g = [];
    g.push(txt(W / 2, 26, '기본 행 연산 세 가지가 넓이에 하는 일', { anchor: 'middle', cls: 'ink bold' }));
    const mk = i => {
        const px = 16 + i * 246, py = 44, pw = 222, ph = 254;
        const { f } = sq(xR, yR, px + 32, py + 40, 25);
        return { px, py, pw, ph, f };
    };
    const base = f => dpoly(f, [[0, 0], u, [u[0] + v[0], u[1] + v[1]], v], { fill: CK, op: 0.1, stroke: CK, sw: 1.1, dash: '4 3' });
    {
        const { px, py, pw, ph, f } = mk(0);
        const v2 = [2, 4];
        g.push(panel(px, py, pw, ph, '한 행을 2배 한다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2, 4] }));
        g.push(base(f));
        g.push(dpoly(f, [[0, 0], u, [u[0] + v2[0], u[1] + v2[1]], v2], { fill: C3, op: 0.2, stroke: C3, sw: 1.5 }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], v2, { cls: 's2', width: 2.4 }));
        g.push(txt(px + pw / 2, py + ph - 44, '넓이도 2배', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 24, 'det 3 → 6', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(1);
        g.push(panel(px, py, pw, ph, '두 행을 바꾼다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2, 4] }));
        g.push(dpoly(f, [[0, 0], u, [u[0] + v[0], u[1] + v[1]], v], { fill: C3, op: 0.2, stroke: C3, sw: 1.5 }));
        g.push(varw(f, [0, 0], v, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], u, { cls: 's2', width: 2.4 }));
        g.push(angleArc(S(f, [0, 0]), S(f, v), S(f, u), 42, '방향이 뒤집힌다'));
        g.push(txt(px + pw / 2, py + ph - 44, '넓이는 그대로', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 24, 'det 3 → −3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(2);
        const v3 = [3, 3];
        g.push(panel(px, py, pw, ph, '한 행의 배수를 다른 행에 더한다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2, 4] }));
        g.push(base(f));
        g.push(dpoly(f, [[0, 0], u, [u[0] + v3[0], u[1] + v3[1]], v3], { fill: C3, op: 0.2, stroke: C3, sw: 1.5 }));
        g.push(dln(f, [[0.2, 2.1], [4.6, 4.3]], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], v3, { cls: 's2', width: 2.4 }));
        g.push(arw(f.X(1), f.Y(2), f.X(2.75), f.Y(2.87), { cls: 'ark', width: 1.4 }));
        g.push(txt(px + pw / 2, py + ph - 44, '밑변도 높이도 그대로', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 24, 'det 3 → 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '오른쪽이 소거법의 핵심이다. v 를 u 와 나란한 방향으로 밀어도 밑변과 높이가 변하지 않으므로 넓이가 보존된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '그래서 배수를 더하는 소거를 아무리 해도 행렬식은 바뀌지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-det-rowops',
        svg: svg({
            width: W, height: H,
            title: '기본 행 연산이 행렬식에 하는 일',
            desc: '배수는 행렬식을 그만큼 곱하고, 교환은 부호를 바꾸며, 다른 행의 배수를 더하는 것은 행렬식을 바꾸지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-3. 3×3 행렬식은 부피다 ---- */
add((() => {
    const W = 740, H = 332;
    const g = [];
    g.push(txt(W / 2, 26, '2×2 에서 넓이였던 것이 3×3 에서는 부피가 된다', { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 24, py = 44, pw = 330, ph = 240;
        g.push(panel(px, py, pw, ph, '단위 정육면체', '세 열이 e~1, e~2, e~3'));
        const T = iso(px + 168, py + 148, 1);
        const P = combo(T, [1, 0, 0], [0, 1, 0], [0, 0, 1]);
        g.push(ppiped(P, { fill: C1, op: 0.11 }));
        g.push(arw(...P([0, 0, 0]), ...P([1, 0, 0]), { cls: 's1', width: 2.2 }));
        g.push(arw(...P([0, 0, 0]), ...P([0, 1, 0]), { cls: 's2', width: 2.2 }));
        g.push(arw(...P([0, 0, 0]), ...P([0, 0, 1]), { cls: 's3', width: 2.2 }));
        g.push(txt(px + pw / 2, py + ph - 20, '부피 1 = det I', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const px = 386, py = 44, pw = 330, ph = 240;
        g.push(panel(px, py, pw, ph, '세 열이 만드는 상자', 'a = (2,0,0), b = (1,2,0), c = (1,1,2)'));
        const T = iso(px + 190, py + 118, 1);
        const P = combo(T, [2, 0, 0], [1, 2, 0], [1, 1, 2]);
        g.push(ppiped(P, { fill: C3, op: 0.1 }));
        g.push(arw(...P([0, 0, 0]), ...P([1, 0, 0]), { cls: 's1', width: 2.4 }));
        g.push(arw(...P([0, 0, 0]), ...P([0, 1, 0]), { cls: 's2', width: 2.4 }));
        g.push(arw(...P([0, 0, 0]), ...P([0, 0, 1]), { cls: 's3', width: 2.4 }));
        g.push(txt(...P([1.05, 0, 0]).map((v, k) => (k === 1 ? v + 16 : v - 6)), 'a', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(...P([0, 1.06, 0]).map((v, k) => (k === 1 ? v + 16 : v + 6)), 'b', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(...P([0, 0, 1.06]).map((v, k) => (k === 1 ? v - 6 : v + 8)), 'c', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 20, '부피 8 = |det A|', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '한 변이 2배인 상자의 부피가 8배인 것과 같은 이치다. 행렬식은 그 배율을 한 수로 적은 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '부호는 세 벡터가 오른손 좌표계를 이루면 양수, 뒤집혀 있으면 음수다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-det-volume',
        svg: svg({
            width: W, height: H,
            title: '3×3 행렬식은 평행육면체의 부피다',
            desc: '단위 정육면체의 부피 1이 세 열이 만드는 상자의 부피로 바뀌고 그 값이 행렬식의 절댓값이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-4. det = 0 은 납작해진다는 뜻이다 ---- */
add((() => {
    const W = 750, H = 344;
    const xR = [-0.4, 3.4], yR = [-0.4, 3.4];
    const u = [2, 1];
    const g = [];
    g.push(txt(W / 2, 26, '행렬식이 0 이 된다는 것은 평행사변형이 납작해진다는 것이다', { anchor: 'middle', cls: 'ink bold' }));
    const spec = [
        [[1, 2], 'det = 3', '평면을 제대로 덮는다'],
        [[0.6, 1.0], 'det = 1.4', '두 벡터가 가까워졌다'],
        [[1, 0.5], 'det = 0', '같은 직선 위에 놓였다'],
    ];
    spec.forEach(([v, big, small], i) => {
        const px = 20 + i * 240, pw = 222, py = 44, ph = 252;
        const { f } = sq(xR, yR, px + 34, py + 40, 40);
        g.push(panel(px, py, pw, ph));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [1, 2, 3] }));
        if (i === 2) g.push(dln(f, [[0, 0], [3.3, 1.65]], { stroke: CK, sw: 1.1, dash: '5 4' }));
        g.push(dpoly(f, [[0, 0], u, [u[0] + v[0], u[1] + v[1]], v], { fill: C3, op: 0.24, stroke: C3, sw: 1.5 }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], v, { cls: 's2', width: 2.6 }));
        g.push(txt(px + pw / 2, py + ph - 44, big, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 24, small, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 30, '오른쪽에서는 두 열이 한 직선 위에 있어 그 어떤 조합으로도 그 직선 밖의 점을 만들 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '평면이 직선으로 찌부러지면 되돌릴 수 없다. 행렬식이 0 인 것과 역행렬이 없는 것이 같은 말인 이유다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-det-zero',
        svg: svg({
            width: W, height: H,
            title: '행렬식이 0 이라는 것의 뜻',
            desc: '두 열이 같은 직선 위에 놓이면 평행사변형이 납작해지고 행렬식이 0 이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-5. 곱의 행렬식 ---- */
add((() => {
    const W = 780, H = 300;
    const xR = [-0.4, 4.6], yR = [-0.4, 2.6];
    const g = [];
    g.push(txt(W / 2, 26, '넓이 배율은 곱해진다', { anchor: 'middle', cls: 'ink bold' }));
    const stage = [
        ['시작', '넓이 1', [[0, 0], [1, 0], [1, 1], [0, 1]], 'B 를 곱하면'],
        ['B 를 적용', '넓이 2 = det B', [[0, 0], [1, 0], [2, 2], [1, 2]], 'A 를 곱하면'],
        ['다시 A 를 적용', '넓이 4 = det A · det B', [[0, 0], [2, 0], [4, 2], [2, 2]], null],
    ];
    stage.forEach(([title, sub, quad, arrow], i) => {
        const px = 24 + i * 254, py = 46, pw = 224, ph = 180;
        const { f } = sq(xR, yR, px + 22, py + 48, 38);
        g.push(panel(px, py, pw, ph, title, sub));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [1, 2] }));
        g.push(dpoly(f, quad, { fill: C3, op: 0.24, stroke: C3, sw: 1.6 }));
        if (arrow) {
            g.push(arw(px + pw + 4, py + 100, px + pw + 26, py + 100, { cls: 'ark', width: 1.7 }));
            g.push(txt(px + pw + 15, py + 88, i === 0 ? 'B' : 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(txt(W / 2, 254, 'det B = 2 로 두 배, det A = 2 로 다시 두 배 → 모두 네 배', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '이것이 det(AB) = det A · det B 의 전부다. 배율을 두 번 겪으면 배율이 곱해진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-det-product',
        svg: svg({
            width: W, height: H,
            title: '곱의 행렬식은 행렬식의 곱이다',
            desc: '넓이를 두 배로 만드는 변환을 두 번 겪으면 넓이가 네 배가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-6. 크라메르 공식은 넓이의 비다 ---- */
add((() => {
    const W = 700, H = 396;
    const xR = [-0.6, 7.4], yR = [-0.6, 6.4];
    const a1 = [2, 1], a2 = [1, 2], b = [5, 4];
    const g = [];
    g.push(txt(W / 2, 26, '크라메르 공식 — 미지수는 두 넓이의 비다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = i => {
        const px = 20 + i * 332, py = 44, pw = 310, ph = 294;
        const { f } = sq(xR, yR, px + 46, py + 46, 27);
        return { px, py, pw, ph, f };
    };
    {
        const { px, py, pw, ph, f } = mk(0);
        g.push(panel(px, py, pw, ph, '첫 열을 b 로 바꾼다', '넓이 6 ÷ 넓이 3 = 2'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4, 6], yTicks: [2, 4, 6] }));
        g.push(dpoly(f, [[0, 0], b, [b[0] + a2[0], b[1] + a2[1]], a2], { fill: C2, op: 0.18, stroke: C2, sw: 1.5 }));
        g.push(dpoly(f, [[0, 0], a1, [a1[0] + a2[0], a1[1] + a2[1]], a2], { fill: C1, op: 0.3, stroke: C1, sw: 1.6 }));
        g.push(varw(f, [0, 0], a1, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], a2, { cls: 's3', width: 2.4 }));
        g.push(varw(f, [0, 0], b, { cls: 's2', width: 2.4 }));
        g.push(txt(f.X(5) + 8, f.Y(4) - 4, 'b', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2) + 12, f.Y(1) + 6, 'a~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1) - 6, f.Y(2) - 6, 'a~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(3.4), f.Y(3.6), '넓이 6', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.3), f.Y(1.6), '3', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 22, 'x~1 = 6 ÷ 3 = 2', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(1);
        g.push(panel(px, py, pw, ph, '둘째 열을 b 로 바꾼다', '넓이 3 ÷ 넓이 3 = 1'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4, 6], yTicks: [2, 4, 6] }));
        g.push(dpoly(f, [[0, 0], a1, [a1[0] + b[0], a1[1] + b[1]], b], { fill: C2, op: 0.18, stroke: C2, sw: 1.5 }));
        g.push(dpoly(f, [[0, 0], a1, [a1[0] + a2[0], a1[1] + a2[1]], a2], { fill: C1, op: 0.3, stroke: C1, sw: 1.6 }));
        g.push(varw(f, [0, 0], a1, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], a2, { cls: 's3', width: 2.4 }));
        g.push(varw(f, [0, 0], b, { cls: 's2', width: 2.4 }));
        g.push(txt(f.X(5) + 8, f.Y(4) - 4, 'b', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(4.4), f.Y(2.6), '넓이 3', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.3), f.Y(1.6), '3', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 22, 'x~2 = 3 ÷ 3 = 1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, 'b = 2a~1 + 1·a~2 이므로 b 와 a~2 가 만드는 넓이는 2a~1 과 a~2 가 만드는 넓이와 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, 'a~2 방향으로 미는 것은 넓이를 바꾸지 않기 때문이다. 그 넓이는 원래 넓이의 x~1 배다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-cramer',
        svg: svg({
            width: W, height: H,
            title: '크라메르 공식의 넓이 그림',
            desc: '한 열을 상수 벡터로 바꾼 행렬의 행렬식은 원래 행렬식의 미지수 배가 된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5장 — 벡터와 유클리드 공간
 * ================================================================== */

/* ---- 5-1. 합, 차, 스칼라배 ---- */
add((() => {
    const W = 770, H = 306;
    const g = [];
    g.push(txt(W / 2, 26, '벡터의 세 가지 기본 동작', { anchor: 'middle', cls: 'ink bold' }));
    const u = [3, 1], v = [1, 2];
    {
        const px = 18, py = 44, pw = 234, ph = 214;
        const xR = [-0.6, 4.6], yR = [-0.6, 3.6];
        const { f } = sq(xR, yR, px + 28, py + 48, 33);
        g.push(panel(px, py, pw, ph, '합 — 이어 붙인다', 'u + v = (4, 3)'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(dpoly(f, [[0, 0], u, [4, 3], v], { fill: C3, op: 0.12, stroke: CK, sw: 1, dash: '4 3' }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.4 }));
        g.push(varw(f, u, [4, 3], { cls: 's2', width: 2.4 }));
        g.push(varw(f, [0, 0], [4, 3], { cls: 's3', width: 2.8 }));
        g.push(txt(f.X(1.6), f.Y(0.4), 'u', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(3.6), f.Y(1.9), 'v', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.7), f.Y(1.8), 'u + v', { cls: 'ink bold', size: 'sm' }));
    }
    {
        const px = 268, py = 44, pw = 234, ph = 214;
        const xR = [-0.6, 4.6], yR = [-1.6, 2.6];
        const { f } = sq(xR, yR, px + 28, py + 48, 33);
        g.push(panel(px, py, pw, ph, '차 — v 끝에서 u 끝으로', 'u − v = (2, −1)'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], v, { cls: 's2', width: 2.4 }));
        g.push(varw(f, v, u, { cls: 's3', width: 2.8 }));
        g.push(varw(f, [0, 0], [2, -1], { cls: 's3', width: 2, dash: '5 4' }));
        g.push(txt(f.X(2.2), f.Y(0.55), 'u', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.55), f.Y(1.6), 'v', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2.1), f.Y(1.7), 'u − v', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2.1), f.Y(-1.35), '원점으로 옮긴 같은 벡터', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 518, py = 44, pw = 234, ph = 214;
        const xR = [-2.8, 4.4], yR = [-1.6, 2.6];
        const { f } = sq(xR, yR, px + 26, py + 52, 24);
        g.push(panel(px, py, pw, ph, '스칼라배 — 길이만 바뀐다', '방향은 그대로거나 정반대'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(varw(f, [0, 0], [4, 2], { cls: 's2', width: 2.2 }));
        g.push(varw(f, [0, 0], [2, 1], { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], [1, 0.5], { cls: 's3', width: 2.4 }));
        g.push(varw(f, [0, 0], [-2, -1], { cls: 's2', width: 2.2, dash: '5 4' }));
        g.push(txt(f.X(4.1), f.Y(2) - 6, '2u', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2.1), f.Y(1) + 16, 'u', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.05), f.Y(0.5) + 20, '½u', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(-2.05), f.Y(-1) + 18, '−u', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '이 두 동작(합과 스칼라배)만으로 벡터로 할 수 있는 일이 정해진다. 6장에서 벡터공간을 정의할 때 공리로 삼는 것이 바로 이 둘이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-vec-add',
        svg: svg({
            width: W, height: H,
            title: '벡터의 합과 차와 스칼라배',
            desc: '합은 화살표를 이어 붙인 것, 차는 한 끝에서 다른 끝으로 가는 화살표, 스칼라배는 길이만 바꾼 것이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-2. 내적의 부호와 각 ---- */
add((() => {
    const W = 770, H = 316;
    const xR = [-2.4, 3.6], yR = [-0.9, 3.1];
    const g = [];
    g.push(txt(W / 2, 26, '내적의 부호가 곧 두 벡터가 이루는 각의 종류다', { anchor: 'middle', cls: 'ink bold' }));
    const spec = [
        [60, '예각 — 내적이 양수', 'u · v = 3.6 > 0'],
        [90, '직각 — 내적이 0', 'u · v = 0'],
        [120, '둔각 — 내적이 음수', 'u · v = −3.6 < 0'],
    ];
    spec.forEach(([deg, title, val], i) => {
        const px = 18 + i * 250, py = 44, pw = 234, ph = 216;
        const { f } = sq(xR, yR, px + 30, py + 46, 32);
        const rad = deg * Math.PI / 180;
        const v = [r2(2.4 * Math.cos(rad)), r2(2.4 * Math.sin(rad))];
        g.push(panel(px, py, pw, ph, title));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [] }));
        g.push(ln([[f.X(v[0]), f.Y(v[1])], [f.X(v[0]), f.Y(0) + 8]], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(ln([[f.X(0), f.Y(0) + 8], [f.X(v[0]), f.Y(0) + 8]], { stroke: C3, sw: 3.6 }));
        g.push(varw(f, [0, 0], [3, 0], { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], v, { cls: 's2', width: 2.6 }));
        g.push(angleArc(S(f, [0, 0]), S(f, [3, 0]), S(f, v), 34, `θ = ${deg}°`));
        g.push(txt(f.X(3) + 4, f.Y(0) + 17, 'u', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(v[0]) + (deg > 90 ? -8 : 8), f.Y(v[1]) - 6, 'v', { anchor: deg > 90 ? 'end' : 'start', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 34, val, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, i === 1 ? '그림자의 길이가 0 이다' : '굵은 선분이 v 의 그림자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 12, '내적은 ‘v 를 u 위에 내린 그림자의 길이’ 에 ‘u 의 길이’ 를 곱한 값이다. 그림자가 u 와 반대쪽을 향하면 음수가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-dot-cos',
        svg: svg({
            width: W, height: H,
            title: '내적의 부호와 두 벡터가 이루는 각',
            desc: '예각이면 내적이 양수, 직각이면 0, 둔각이면 음수다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-3. 정사영 ---- */
add((() => {
    const W = 730, H = 340;
    const xR = [-0.6, 4.8], yR = [-0.6, 3.8];
    const u = [1, 3], v = [4, 2];
    const t = (u[0] * v[0] + u[1] * v[1]) / (v[0] * v[0] + v[1] * v[1]);
    const p = [r2(t * v[0]), r2(t * v[1])];
    const g = [];
    g.push(txt(W / 2, 26, '정사영 — 그림자를 내린다', { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 22, py = 44, pw = 330, ph = 254;
        const { f } = sq(xR, yR, px + 46, py + 44, 34);
        g.push(panel(px, py, pw, ph, 'u 를 v 방향으로 내린 그림자'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(dln(f, [[0, 0], [4.7, 2.35]], { stroke: CK, sw: 1.1, dash: '5 4' }));
        g.push(dln(f, [p, u], { stroke: C3, sw: 2, dash: '5 4' }));
        g.push(varw(f, [0, 0], v, { cls: 's2', width: 2.4 }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], p, { cls: 's3', width: 3.2 }));
        g.push(rightAngle(S(f, p), [S(f, u)[0] - S(f, p)[0], S(f, u)[1] - S(f, p)[1]], [-v[0], v[1]], 11));
        g.push(txt(f.X(1) - 8, f.Y(3) - 6, 'u', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(4) + 8, f.Y(2) - 4, 'v', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(p[0]) + 4, f.Y(p[1]) + 20, '사영', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.7), f.Y(2.2), 'u − 사영', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 20, '남는 조각은 v 와 수직이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 378, py = 44, pw = 330, ph = 254;
        const { f } = sq(xR, yR, px + 46, py + 44, 34);
        g.push(panel(px, py, pw, ph, '수선의 발이 가장 가깝다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(dln(f, [[0, 0], [4.7, 2.35]], { stroke: CK, sw: 1.1, dash: '5 4' }));
        [0.8, 1.4, 2.8, 3.8].forEach(s => {
            const q = [r2(s * v[0] / 4), r2(s * v[1] / 4)];
            g.push(dln(f, [q, u], { stroke: CK, sw: 1, dash: '3 3' }));
            g.push(pdot(f.X(q[0]), f.Y(q[1]), CK, 2.6));
        });
        g.push(dln(f, [p, u], { stroke: C3, sw: 2.6 }));
        g.push(varw(f, [0, 0], u, { cls: 's1', width: 2.6 }));
        g.push(pdot(f.X(p[0]), f.Y(p[1]), C3, 5));
        g.push(rightAngle(S(f, p), [S(f, u)[0] - S(f, p)[0], S(f, u)[1] - S(f, p)[1]], [-v[0], v[1]], 11));
        g.push(txt(f.X(1) - 8, f.Y(3) - 6, 'u', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(p[0]) + 4, f.Y(p[1]) + 20, '수선의 발', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 20, '직선 위 다른 점까지의 거리는 모두 더 멀다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '오른쪽 그림이 9장의 최소제곱법으로 곧장 이어진다. 풀 수 없는 방정식에서 ‘가장 가까운 답’ 을 찾는 일이 결국 수직으로 내리는 일이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-projection',
        svg: svg({
            width: W, height: H,
            title: '정사영과 수선의 발',
            desc: 'u 를 v 방향으로 내린 사영과 남는 수직 성분, 그리고 수선의 발이 최단 거리를 준다는 것',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-4. 외적 ---- */
add((() => {
    const W = 740, H = 360;
    const g = [];
    g.push(txt(W / 2, 26, '외적 — 두 벡터가 만드는 평면에 수직인 벡터', { anchor: 'middle', cls: 'ink bold' }));
    const u = [2, 0, 0], v = [1, 2, 0];
    const draw = (px, title, sub, up) => {
        const py = 44, pw = 330, ph = 248;
        const T = iso(px + 158, py + (up ? 170 : 104), 0.82);
        const O = T([0, 0, 0]);
        const tip = T([0, 0, up ? 3.2 : -3.2]);
        const out = [panel(px, py, pw, ph, title, sub)];
        out.push(poly([O, T(u), T([u[0] + v[0], u[1] + v[1], 0]), T(v)], { fill: C3, op: 0.25, stroke: C3, sw: 1.5 }));
        out.push(arw(...O, ...T(u), { cls: 's1', width: 2.4 }));
        out.push(arw(...O, ...T(v), { cls: 's2', width: 2.4 }));
        out.push(arw(...O, ...tip, { cls: 'ark', width: 2.6, marker: 'ark' }));
        out.push(txt(T([1.15, 0, 0])[0], T([1.15, 0, 0])[1] + 16, 'u', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(T([0, 1.15, 0])[0] + 8, T([0, 1.15, 0])[1] + 16, 'v', { cls: 'ink bold', size: 'sm' }));
        out.push(txt(tip[0] + 10, tip[1] + 5, up ? 'u × v' : 'v × u', { cls: 'ink bold', size: 'sm' }));
        out.push(rightAngle(O, [T(u)[0] - O[0], T(u)[1] - O[1]], [0, up ? -1 : 1], 12));
        return out.join('');
    };
    g.push(draw(24, 'u × v', '오른손 네 손가락을 u 에서 v 로 감으면 엄지 방향', true));
    g.push(draw(386, 'v × u', '순서를 바꾸면 정반대 방향', false));
    g.push(txt(W / 2, H - 46, '두 경우 모두 크기는 같다. 색칠한 평행사변형의 넓이 4 가 그 크기다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 28, '‖u × v‖ = ‖u‖‖v‖ sin θ 이고, 내적의 cos 자리에 sin 이 들어간 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 10, '그래서 두 벡터가 나란하면 외적이 영벡터가 된다. 넓이가 0 이기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-cross',
        svg: svg({
            width: W, height: H,
            title: '외적의 방향과 크기',
            desc: '외적은 두 벡터가 만드는 평면에 수직이고 크기는 평행사변형의 넓이이며 순서를 바꾸면 방향이 뒤집힌다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-5. 스칼라 삼중곱과 부피 ---- */
add((() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '스칼라 삼중곱 — 밑넓이 곱하기 높이', { anchor: 'middle', cls: 'ink bold' }));
    const px = 40, py = 44, pw = 640, ph = 226;
    g.push(panel(px, py, pw, ph, 'u = (2,0,0), v = (1,2,0), w = (1,1,2)', '밑넓이 4, 높이 2, 부피 8'));
    const T = iso(px + 250, py + 168, 1);
    const P = combo(T, [2, 0, 0], [1, 2, 0], [1, 1, 2]);
    g.push(ppiped(P, { fill: C1, op: 0.08 }));
    g.push(poly([P([0, 0, 0]), P([1, 0, 0]), P([1, 1, 0]), P([0, 1, 0])], { fill: C3, op: 0.3, stroke: C3, sw: 1.6 }));
    g.push(arw(...P([0, 0, 0]), ...P([1, 0, 0]), { cls: 's1', width: 2.4 }));
    g.push(arw(...P([0, 0, 0]), ...P([0, 1, 0]), { cls: 's2', width: 2.4 }));
    g.push(arw(...P([0, 0, 0]), ...P([0, 0, 1]), { cls: 's3', width: 2.6 }));
    g.push(arw(...T([0, 0, 0]), ...T([0, 0, 3]), { cls: 'ark', width: 2, marker: 'ark', dash: '5 4' }));
    g.push(ln([T([1, 1, 2]), T([1, 1, 0])], { stroke: CK, sw: 1.3, dash: '4 3' }));
    g.push(txt(...T([1.1, 0, 0]).map((q, k) => (k === 1 ? q + 18 : q)), 'u', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(...T([0, 1.1, 0]).map((q, k) => (k === 1 ? q + 18 : q + 6)), 'v', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(...T([1.1, 1.1, 2]).map((q, k) => (k === 1 ? q - 8 : q + 8)), 'w', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(...T([0, 0, 3]).map((q, k) => (k === 1 ? q - 8 : q + 8)), 'u × v 방향', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px + 424, py + 74, '밑면의 넓이 = ‖u × v‖ = 4', { cls: 'ink', size: 'sm' }));
    g.push(txt(px + 424, py + 96, '높이 = w 의 u × v 방향 성분 = 2', { cls: 'ink', size: 'sm' }));
    g.push(txt(px + 424, py + 118, '부피 = (u × v) · w = 8', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(px + 424, py + 146, '이 값은 세 벡터를 행으로 쌓은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px + 424, py + 164, '3×3 행렬의 행렬식과 같다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 30, '내적이 ‘길이 × 그림자’ 였던 것과 같은 방식이다. u × v 와 w 를 내적하면 w 의 높이 성분만 남는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '삼중곱이 0 이면 세 벡터가 한 평면 위에 있다는 뜻이고, 그것이 4장의 det = 0 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-triple',
        svg: svg({
            width: W, height: H,
            title: '스칼라 삼중곱은 평행육면체의 부피다',
            desc: '밑면의 넓이는 외적의 크기이고 높이는 세 번째 벡터의 법선 방향 성분이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-6. 직선의 두 가지 표현 ---- */
add((() => {
    const W = 740, H = 346;
    const xR = [-1.6, 5.6], yR = [-1.2, 3.8];
    const P0 = [1, 1], d = [2, 1], nv = [1, -2];
    const g = [];
    g.push(txt(W / 2, 26, '직선을 벡터로 적는 두 가지 방법', { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 22, py = 44, pw = 330, ph = 258;
        const { f } = sq(xR, yR, px + 50, py + 48, 30);
        g.push(panel(px, py, pw, ph, '매개형 — 출발점 + t · 방향', 'x = P~0 + t d'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(dln(f, [[-1.4, -0.2], [5.4, 3.2]], { stroke: C1, sw: 2.4 }));
        [1, 2].forEach(t => {
            const q = [P0[0] + t * d[0], P0[1] + t * d[1]];
            g.push(pdot(f.X(q[0]), f.Y(q[1]), CK, 3.4));
            g.push(txt(f.X(q[0]) + 22, f.Y(q[1]) + 16, `t = ${t}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
        g.push(varw(f, P0, [P0[0] + d[0], P0[1] + d[1]], { cls: 's2', width: 2.6 }));
        g.push(pdot(f.X(P0[0]), f.Y(P0[1]), C3, 5));
        g.push(txt(f.X(1) - 8, f.Y(1) - 8, 'P~0 (t = 0)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1.55), f.Y(2.15), 'd = (2, 1)', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 20, 't 를 움직이면 직선 위의 점이 모두 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 378, py = 44, pw = 330, ph = 258;
        const { f } = sq(xR, yR, px + 50, py + 48, 30);
        g.push(panel(px, py, pw, ph, '법선형 — 법선과 수직인 점들', 'n · (x − P~0) = 0'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2, 4], yTicks: [2] }));
        g.push(dln(f, [[-1.4, -0.2], [5.4, 3.2]], { stroke: C1, sw: 2.4 }));
        g.push(varw(f, P0, [P0[0] + nv[0], P0[1] + nv[1]], { cls: 's2', width: 2.6 }));
        g.push(varw(f, P0, [3, 2], { cls: 's3', width: 2.4 }));
        g.push(pdot(f.X(P0[0]), f.Y(P0[1]), C3, 5));
        g.push(pdot(f.X(3), f.Y(2), C1, 4.5));
        g.push(rightAngle(S(f, P0), [nv[0], -nv[1]], [2, -1], 11));
        g.push(txt(f.X(1) - 8, f.Y(1) - 8, 'P~0', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2.2) + 6, f.Y(-1) + 4, 'n = (1, −2)', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(3) + 8, f.Y(2) - 6, 'x', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 20, 'x − 2y + 1 = 0 이라는 익숙한 식이 이것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '평면에서는 두 표현이 모두 직선을 준다. 공간으로 올라가면 매개형은 직선을, 법선형은 평면을 준다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-line',
        svg: svg({
            width: W, height: H,
            title: '직선의 매개형과 법선형',
            desc: '출발점과 방향벡터로 적는 방법과 법선벡터에 수직인 점들로 적는 방법',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-7. 평면과 점 사이의 거리 ---- */
add((() => {
    const W = 740, H = 352;
    const g = [];
    g.push(txt(W / 2, 26, '평면의 방정식과, 점에서 평면까지의 거리', { anchor: 'middle', cls: 'ink bold' }));
    const quad = (T) => [T([-2, -2, 0]), T([2.4, -2, 0]), T([2.4, 2, 0]), T([-2, 2, 0])];
    {
        const px = 22, py = 44, pw = 330, ph = 244;
        const T = iso(px + 190, py + 150, 0.92);
        const O = T([0, 0, 0]);
        const X = [1.8, -1.4, 0];
        g.push(panel(px, py, pw, ph, '평면 — 법선에 수직인 점 전부', 'n · (x − P~0) = 0'));
        g.push(poly(quad(T), { fill: C1, op: 0.16, stroke: C1, sw: 1.4 }));
        g.push(arw(...O, ...T([0, 0, 2.2]), { cls: 's2', width: 2.6 }));
        g.push(arw(...O, ...T(X), { cls: 's3', width: 2.4 }));
        g.push(pdot(...O, 'var(--ink)', 4.5));
        g.push(rightAngle(O, [0, -1], [T(X)[0] - O[0], T(X)[1] - O[1]], 12));
        g.push(txt(T([0, 0, 2.3])[0] + 8, T([0, 0, 2.3])[1], 'n', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(O[0] + 8, O[1] + 18, 'P~0', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T(X)[0] - 4, T(X)[1] + 20, 'x − P~0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '평면 안에서 어느 방향으로 가도 n 과 수직이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 378, py = 44, pw = 330, ph = 244;
        const T = iso(px + 190, py + 152, 0.92);
        const O = T([0, 0, 0]);
        const F = [2, -1.5, 0];
        const Pp = [2, -1.5, 2];
        g.push(panel(px, py, pw, ph, '거리 — 그 벡터를 법선에 사영한 길이', 'd = |n · (P − P~0)| ÷ ‖n‖'));
        g.push(poly(quad(T), { fill: C1, op: 0.16, stroke: C1, sw: 1.4 }));
        g.push(ln([T(F), T(Pp)], { stroke: C2, sw: 2.6 }));
        g.push(arw(...O, ...T([0, 0, 2.2]), { cls: 's3', width: 2.2 }));
        g.push(arw(...O, ...T(Pp), { cls: 's1', width: 2.4 }));
        g.push(pdot(...O, 'var(--ink)', 4.5));
        g.push(pdot(...T(Pp), C2, 5));
        g.push(pdot(...T(F), CK, 3.6));
        g.push(rightAngle(T(F), [0, -1], [O[0] - T(F)[0], O[1] - T(F)[1]], 11));
        g.push(txt(T(Pp)[0] - 8, T(Pp)[1] - 8, 'P', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([0, 0, 2.3])[0] + 8, T([0, 0, 2.3])[1], 'n', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(O[0] + 8, O[1] + 18, 'P~0', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T(F)[0] - 10, (T(F)[1] + T(Pp)[1]) / 2, '거리 d', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '법선 방향 성분만 남기면 그것이 거리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '거리를 재는 일이 결국 정사영이다. 평면에 나란한 성분은 아무리 길어도 거리에 보태지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '‖n‖ 으로 나누는 것은 법선을 길이 1 로 맞추기 위해서다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-mat-plane-dist',
        svg: svg({
            width: W, height: H,
            title: '평면의 방정식과 점에서 평면까지의 거리',
            desc: '평면은 법선에 수직인 점들의 모임이고 거리는 그 법선 방향으로의 정사영 길이다',
            body: g.join(''),
        }),
    };
})());

export default figures;
