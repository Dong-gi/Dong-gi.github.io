/**
 * 선형대수 9장(내적공간과 직교성) · 10장(특이값 분해와 응용)의 그림.
 *
 * 이름은 모두 `la-in-` 으로 시작한다(담당 C 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~1` 표기를, 나머지는 유니코드(− × · ‖ ⟨ ⟩ ∫ Σ σ θ ⊥ √ ≈ ²)로 적는다.
 * 전치는 유니코드 위첨자 T 가 글꼴에 따라 빠지므로 `A^T` 로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 두 장의 주제는 &lsquo;수직으로 내린다&rsquo; 하나다. 정사영 · 그람-슈미트 · 최소제곱 ·
 * 특이값 분해가 모두 같은 그림의 변주이고, 그 사실을 보이는 것이 이 파일의 목적이다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/** 음수 부호를 하이픈이 아니라 진짜 빼기 기호로 적는다. */
const nm = v => String(v).replace(/-/g, '−');

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
function rightAngle(v, d1, d2, s = 10, col = CK) {
    const u = a => { const L = Math.hypot(a[0], a[1]) || 1; return [(a[0] / L) * s, (a[1] / L) * s]; };
    const p = u(d1); const q = u(d2);
    return ln([[v[0] + p[0], v[1] + p[1]], [v[0] + p[0] + q[0], v[1] + p[1] + q[1]], [v[0] + q[0], v[1] + q[1]]],
        { stroke: col, sw: 1.2 });
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

/** 가로세로 배율을 같게 맞춘 frame. 배율이 다르면 직각이 직각으로 보이지 않는다. */
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

/** 중심 c, 반축 벡터 a·b 인 타원(원 포함)의 데이터 좌표 점열. */
function ellipsePts(c, a, b, steps = 144) {
    const out = [];
    for (let i = 0; i <= steps; i += 1) {
        const t = (2 * Math.PI * i) / steps;
        out.push([c[0] + Math.cos(t) * a[0] + Math.sin(t) * b[0],
            c[1] + Math.cos(t) * a[1] + Math.sin(t) * b[1]]);
    }
    return out;
}

/** 타원 위의 각 구간 [t1, t2] 만 잘라낸 점열. 회전을 눈으로 좇게 하는 표식으로 쓴다. */
function arcPts(c, a, b, t1, t2, steps = 48) {
    const out = [];
    for (let i = 0; i <= steps; i += 1) {
        const t = t1 + ((t2 - t1) * i) / steps;
        out.push([c[0] + Math.cos(t) * a[0] + Math.sin(t) * b[0],
            c[1] + Math.cos(t) * a[1] + Math.sin(t) * b[1]]);
    }
    return out;
}

/* ------------------------------------------------------------------ *
 * 3차원 스케치용 등각 투영
 * ------------------------------------------------------------------ */

const EX = [-29, 16.5];
const EY = [32, 14.5];
const EZ = [0, -32];

const iso = (ox, oy, s = 1) => ([x, y, z]) => [
    ox + s * (x * EX[0] + y * EY[0] + z * EZ[0]),
    oy + s * (x * EX[1] + y * EY[1] + z * EZ[1]),
];

/* ------------------------------------------------------------------ *
 * 수치 계산 — 그림에 적는 값은 전부 여기서 실제로 계산한다
 * ------------------------------------------------------------------ */

/** 2×2 대칭행렬 [[a,b],[b,d]] 의 고유값과 정규직교 고유벡터. 큰 것부터. */
function eig2(a, b, d) {
    const tr = a + d;
    const disc = Math.sqrt((a - d) * (a - d) + 4 * b * b);
    const l1 = (tr + disc) / 2;
    const l2 = (tr - disc) / 2;
    let v1 = Math.abs(b) > 1e-12 ? [l1 - d, b] : (a >= d ? [1, 0] : [0, 1]);
    const n1 = Math.hypot(v1[0], v1[1]);
    v1 = [v1[0] / n1, v1[1] / n1];
    const v2 = [-v1[1], v1[0]];
    return { l1, l2, v1, v2 };
}

/**
 * 단면 자코비(one-sided Jacobi) 특이값 분해. 작은 행렬용이라 속도는 문제되지 않고,
 * 회전만으로 열을 직교화하므로 수치적으로 안정적이다.
 * 반환: { U(m×n), s(길이 n), V(n×n) }, 특이값 내림차순.
 */
function svdJacobi(Ain, sweeps = 60) {
    const m = Ain.length;
    const n = Ain[0].length;
    const U = Ain.map(r => r.slice());
    const V = Array.from({ length: n }, (_, i) => Array.from({ length: n }, (_, j) => (i === j ? 1 : 0)));
    for (let sw = 0; sw < sweeps; sw += 1) {
        let off = 0;
        for (let p = 0; p < n - 1; p += 1) {
            for (let q = p + 1; q < n; q += 1) {
                let al = 0; let be = 0; let ga = 0;
                for (let i = 0; i < m; i += 1) {
                    al += U[i][p] * U[i][p];
                    be += U[i][q] * U[i][q];
                    ga += U[i][p] * U[i][q];
                }
                off += ga * ga;
                if (Math.abs(ga) < 1e-15) continue;
                const zeta = (be - al) / (2 * ga);
                const t = (zeta >= 0 ? 1 : -1) / (Math.abs(zeta) + Math.sqrt(1 + zeta * zeta));
                const c = 1 / Math.sqrt(1 + t * t);
                const s = c * t;
                for (let i = 0; i < m; i += 1) {
                    const x = U[i][p]; const y = U[i][q];
                    U[i][p] = c * x - s * y;
                    U[i][q] = s * x + c * y;
                }
                for (let i = 0; i < n; i += 1) {
                    const x = V[i][p]; const y = V[i][q];
                    V[i][p] = c * x - s * y;
                    V[i][q] = s * x + c * y;
                }
            }
        }
        if (off < 1e-26) break;
    }
    const s = [];
    for (let j = 0; j < n; j += 1) {
        let t = 0;
        for (let i = 0; i < m; i += 1) t += U[i][j] * U[i][j];
        s.push(Math.sqrt(t));
    }
    const ord = s.map((_, i) => i).sort((i, j) => s[j] - s[i]);
    const Us = Array.from({ length: m }, () => new Array(n).fill(0));
    const Vs = Array.from({ length: n }, () => new Array(n).fill(0));
    const ss = [];
    ord.forEach((src, dst) => {
        ss.push(s[src]);
        for (let i = 0; i < m; i += 1) Us[i][dst] = s[src] > 1e-12 ? U[i][src] / s[src] : 0;
        for (let i = 0; i < n; i += 1) Vs[i][dst] = V[i][src];
    });
    return { U: Us, s: ss, V: Vs };
}

/** 특이값 큰 것부터 k 개만 써서 되살린 행렬. */
function rankK({ U, s, V }, k, m, n) {
    const out = Array.from({ length: m }, () => new Array(n).fill(0));
    for (let t = 0; t < k; t += 1) {
        for (let i = 0; i < m; i += 1) {
            for (let j = 0; j < n; j += 1) out[i][j] += s[t] * U[i][t] * V[j][t];
        }
    }
    return out;
}


/* ================================================================== *
 * 9장 — 내적공간과 직교성
 * ================================================================== */

/* ---- 9-1. 함수 공간의 내적 ---- */
add((() => {
    const W = 790; const H = 352;
    const g = [];
    g.push(txt(W / 2, 26, '내적은 화살표만의 것이 아니다 — 함수끼리도 내적을 잰다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = (i, yR) => {
        const px = 16 + i * 254; const py = 44; const pw = 242; const ph = 240;
        const f = frame({ xRange: [-0.06, 1.1], yRange: yR, box: { x: px + 42, y: py + 56, w: 164, h: 128 } });
        return { px, py, pw, ph, f };
    };
    {
        const yR = [-0.6, 1.12];
        const { px, py, pw, ph, f } = mk(0, yR);
        g.push(panel(px, py, pw, ph, '내적 = 곱해서 넓이를 잰다', 'f(x) = x,  g(x) = 1 − x'));
        g.push(axes(f, { xRange: [-0.06, 1.1], yRange: yR, xTicks: [0.5, 1], yTicks: [0.5, 1] }));
        const prod = [];
        for (let i = 0; i <= 40; i += 1) { const x = i / 40; prod.push([x, x * (1 - x)]); }
        g.push(dpoly(f, [[0, 0], ...prod, [1, 0]], { fill: C3, op: 0.32, stroke: C3, sw: 1.8 }));
        g.push(dln(f, [[0, 0], [1, 1]], { stroke: C1, sw: 2.2 }));
        g.push(dln(f, [[0, 1], [1, 0]], { stroke: C2, sw: 2.2 }));
        g.push(txt(f.X(0.87), f.Y(0.92), 'f', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.87), f.Y(0.2) - 4, 'g', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.5), f.Y(0.32), 'f g', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 38, '색칠한 넓이가 ⟨f, g⟩ 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '⟨f, g⟩ = ∫ x(1 − x) dx = 1/6', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const yR = [-0.78, 1.12];
        const { px, py, pw, ph, f } = mk(1, yR);
        g.push(panel(px, py, pw, ph, '직교 — 넓이가 서로 지워진다', 'f(x) = 1,  h(x) = x − ½'));
        g.push(axes(f, { xRange: [-0.06, 1.1], yRange: yR, xTicks: [0.5, 1], yTicks: [0.5, 1] }));
        g.push(dpoly(f, [[0, 0], [0, -0.5], [0.5, 0]], { fill: C2, op: 0.34, stroke: C2, sw: 1.4 }));
        g.push(dpoly(f, [[0.5, 0], [1, 0.5], [1, 0]], { fill: C3, op: 0.34, stroke: C3, sw: 1.4 }));
        g.push(dln(f, [[0, 1], [1, 1]], { stroke: C1, sw: 2.2 }));
        g.push(dln(f, [[0, -0.5], [1, 0.5]], { stroke: CK, sw: 2 }));
        g.push(txt(f.X(0.86), f.Y(1) - 7, 'f = 1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.92), f.Y(0.28), 'h', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.24), f.Y(-0.64), '넓이 − 1/8', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(0.72), f.Y(0.72), '넓이 + 1/8', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 38, '위와 아래 넓이가 같아 합이 0 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '⟨f, h⟩ = 0 — 두 함수는 직교한다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const yR = [-0.06, 0.34];
        const { px, py, pw, ph, f } = mk(2, yR);
        g.push(panel(px, py, pw, ph, '합이 적분이 된 것뿐이다', '유클리드 내적의 Σ 가 여기서는 ∫'));
        g.push(axes(f, { xRange: [-0.06, 1.1], yRange: yR, xTicks: [0.5, 1], yTicks: [0.1, 0.2, 0.3] }));
        for (let i = 0; i < 8; i += 1) {
            const x0 = i / 8; const x1 = (i + 1) / 8; const xm = (x0 + x1) / 2;
            const hgt = xm * (1 - xm);
            g.push(dpoly(f, [[x0, 0], [x0, hgt], [x1, hgt], [x1, 0]], { fill: C1, op: 0.24, stroke: C1, sw: 1 }));
        }
        const prod = [];
        for (let i = 0; i <= 60; i += 1) { const x = i / 60; prod.push([x, x * (1 - x)]); }
        g.push(dln(f, prod, { stroke: C3, sw: 2.2 }));
        g.push(txt(px + pw / 2, py + ph - 38, 'Σ f(x~i) g(x~i) Δx  →  ∫ f g dx', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '칸을 잘게 쪼갠 끝이 적분이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '벡터의 내적이 성분끼리 곱해 더하는 일이었다면, 함수의 내적은 같은 x 자리의 값끼리 곱해 적분하는 일이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '자리가 유한개에서 연속으로 늘어난 것뿐이고, 직교 · 노름 · 각의 뜻은 그대로 따라온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-function-dot',
        svg: svg({
            width: W, height: H,
            title: '함수 공간의 내적',
            desc: '두 함수의 곱이 만드는 넓이가 내적이고, 위아래 넓이가 지워지면 두 함수는 직교한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-2. 정규직교기저에서는 좌표가 내적이다 ---- */
add((() => {
    const W = 748; const H = 344;
    const xR = [-1.7, 3.5]; const yR = [-0.9, 3.6];
    const g = [];
    g.push(txt(W / 2, 26, '기저가 직교하면 좌표를 내적 한 번으로 읽는다', { anchor: 'middle', cls: 'ink bold' }));
    const q1 = [0.8, 0.6]; const q2 = [-0.6, 0.8]; const u = [2, 3];
    {
        const px = 20; const py = 44; const pw = 348; const ph = 254;
        const { f } = sq(xR, yR, px + 76, py + 50, 38);
        g.push(panel(px, py, pw, ph, '정규직교기저 { q~1, q~2 }', '축이 서로 수직이고 길이가 1 이다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[-0.4 * q1[0], -0.4 * q1[1]], [3.9 * q1[0], 3.9 * q1[1]]], { stroke: CK, sw: 1, dash: '5 4' }));
        g.push(dln(f, [[0.7 * q2[0], 0.7 * q2[1]], [1.9 * q2[0], 1.9 * q2[1]]], { stroke: CK, sw: 1, dash: '5 4' }));
        const p1 = [3.4 * q1[0], 3.4 * q1[1]];
        const p2 = [1.2 * q2[0], 1.2 * q2[1]];
        g.push(dln(f, [u, p1], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(dln(f, [u, p2], { stroke: CK, sw: 1.1, dash: '4 3' }));
        g.push(varw(f, [0, 0], p1, { cls: 's3', width: 3 }));
        g.push(varw(f, [0, 0], p2, { cls: 's3', width: 3 }));
        g.push(varw(f, [0, 0], q1, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], q2, { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], u, { cls: 'ark', width: 2.4, marker: 'ark' }));
        g.push(rightAngle(S(f, p1), [S(f, u)[0] - S(f, p1)[0], S(f, u)[1] - S(f, p1)[1]], [-q1[0], q1[1]], 10));
        g.push(pdot(f.X(p1[0]), f.Y(p1[1]), C3, 4));
        g.push(pdot(f.X(p2[0]), f.Y(p2[1]), C3, 4));
        g.push(txt(f.X(q1[0]) + 6, f.Y(q1[1]) + 17, 'q~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(q2[0]) - 6, f.Y(q2[1]) + 14, 'q~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(u[0]) + 8, f.Y(u[1]) - 4, 'u = (2, 3)', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(p1[0]) + 10, f.Y(p1[1]) + 6, '⟨u, q~1⟩ = 3.4', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(p2[0]) - 6, f.Y(p2[1]) - 10, '⟨u, q~2⟩ = 1.2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, 'u = 3.4 q~1 + 1.2 q~2 — 두 좌표를 따로따로 구했다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 382; const py = 44; const pw = 348; const ph = 254;
        const { f } = sq(xR, yR, px + 76, py + 50, 38);
        const b1 = [1, 0]; const b2 = [1, 1];
        g.push(panel(px, py, pw, ph, '기울어진 기저 { b~1, b~2 }', '수선을 내려도 좌표가 나오지 않는다'));
        g.push(axes(f, { xRange: xR, yRange: yR }));
        g.push(dln(f, [[-1.5, 0], [3.4, 0]], { stroke: CK, sw: 1, dash: '5 4' }));
        g.push(dln(f, [[-0.8, -0.8], [3.4, 3.4]], { stroke: CK, sw: 1, dash: '5 4' }));
        g.push(dln(f, [u, [-1, 0]], { stroke: C2, sw: 1.2, dash: '4 3' }));
        g.push(dln(f, [u, [3, 3]], { stroke: C2, sw: 1.2, dash: '4 3' }));
        g.push(varw(f, [0, 0], [-1, 0], { cls: 's3', width: 3 }));
        g.push(varw(f, [0, 0], [3, 3], { cls: 's3', width: 3 }));
        g.push(varw(f, [0, 0], b1, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], b2, { cls: 's2', width: 2.6 }));
        g.push(varw(f, [0, 0], u, { cls: 'ark', width: 2.4, marker: 'ark' }));
        g.push(pdot(f.X(2), f.Y(0), CK, 3.4));
        g.push(dln(f, [u, [2, 0]], { stroke: CK, sw: 1, dash: '2 3' }));
        g.push(txt(f.X(1) + 4, f.Y(0) + 18, 'b~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1) - 6, f.Y(1) - 6, 'b~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(u[0]) - 8, f.Y(u[1]) - 6, 'u = (2, 3)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(-1) - 6, f.Y(0) - 8, '−1 b~1', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(3) + 6, f.Y(3) + 16, '3 b~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2) + 6, f.Y(0) + 18, '수선의 발은 2', { cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 18, '좌표는 (−1, 3) 인데 수선은 2 를 준다. 연립방정식을 풀어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '직교기저의 값어치가 이것이다. 축끼리 서로 간섭하지 않으므로 한 축의 좌표를 구할 때 다른 축을 볼 필요가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-ortho-coord',
        svg: svg({
            width: W, height: H,
            title: '정규직교기저에서 좌표는 내적으로 구해진다',
            desc: '직교하는 축에서는 수선의 발이 곧 좌표이지만 기울어진 축에서는 그렇지 않다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-3. 부분공간으로의 정사영과 직교여공간 ---- */
add((() => {
    const W = 760; const H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '부분공간으로 내리는 정사영 — 남는 조각은 그 공간과 직교한다', { anchor: 'middle', cls: 'ink bold' }));
    const P0 = [1.8, -0.5, 0];
    const UU = [1.8, -0.5, 2.2];
    const plane = T => poly([T([-1.7, -1.7, 0]), T([2.1, -1.7, 0]), T([2.1, 2.1, 0]), T([-1.7, 2.1, 0])],
        { fill: C1, op: 0.14, stroke: C1, sw: 1.4 });
    {
        const px = 20; const py = 44; const pw = 348; const ph = 256;
        const T = iso(px + 178, py + 140, 0.95);
        const O = T([0, 0, 0]);
        g.push(panel(px, py, pw, ph, 'u 를 부분공간 W 로 내린다'));
        g.push(plane(T));
        g.push(ln([T([0, 0, -1.0]), T([0, 0, 2.4])], { stroke: C2, sw: 1.5, dash: '6 4' }));
        g.push(arw(...O, ...T([1.2, 0, 0]), { cls: 's1', width: 2 }));
        g.push(arw(...O, ...T([0, 1.2, 0]), { cls: 's1', width: 2 }));
        g.push(ln([T(P0), T(UU)], { stroke: C2, sw: 3 }));
        g.push(arw(...O, ...T(UU), { cls: 'ark', width: 2.4, marker: 'ark' }));
        g.push(arw(...O, ...T(P0), { cls: 's3', width: 3 }));
        g.push(rightAngle(T(P0), [0, -1], [O[0] - T(P0)[0], O[1] - T(P0)[1]], 11));
        g.push(pdot(...T(P0), C3, 4.4));
        g.push(txt(T(UU)[0] - 8, T(UU)[1] - 4, 'u', { anchor: 'end', cls: 'ink bold' }));
        g.push(txt(T([1.25, 0, 0])[0] + 4, T([1.25, 0, 0])[1] + 15, 'w~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([0, 1.25, 0])[0] + 4, T([0, 1.25, 0])[1] + 15, 'w~2', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([-1.7, 2.1, 0])[0] - 4, T([-1.7, 2.1, 0])[1] - 8, 'W', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([0, 0, 2.4])[0] + 8, T([0, 0, 2.4])[1] + 4, 'W⊥', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T(P0)[0] - 4, T(P0)[1] + 18, 'proj~W u', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([1.8, -0.5, 1.2])[0] - 8, T([1.8, -0.5, 1.2])[1], 'u − proj~W u', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '두 조각의 합이 u 이고, 둘은 서로 직교한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 392; const py = 44; const pw = 348; const ph = 256;
        const T = iso(px + 178, py + 140, 0.95);
        g.push(panel(px, py, pw, ph, '그 발이 W 안에서 u 에 가장 가깝다'));
        g.push(plane(T));
        [[0.4, 1.5, 0], [-1.2, 0.6, 0], [1.2, 1.6, 0]].forEach(q => {
            g.push(ln([T(q), T(UU)], { stroke: CK, sw: 1.1, dash: '3 3' }));
            g.push(ln([T(q), T(P0)], { stroke: CK, sw: 1, dash: '2 3' }));
            g.push(pdot(...T(q), CK, 3));
        });
        g.push(ln([T(P0), T(UU)], { stroke: C2, sw: 3 }));
        g.push(pdot(...T(UU), 'var(--ink)', 4.4));
        g.push(pdot(...T(P0), C3, 5));
        g.push(rightAngle(T(P0), [0, -1], [T([0.4, 1.5, 0])[0] - T(P0)[0], T([0.4, 1.5, 0])[1] - T(P0)[1]], 11));
        g.push(txt(T(UU)[0] - 8, T(UU)[1] - 4, 'u', { anchor: 'end', cls: 'ink bold' }));
        g.push(txt(T(P0)[0] - 4, T(P0)[1] + 18, '수선의 발 p', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([1.2, 1.6, 0])[0] + 6, T([1.2, 1.6, 0])[1] + 14, 'q', { cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 34, '‖u − q‖² = ‖u − p‖² + ‖p − q‖²', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '오른쪽 항이 0 이 될 때, 곧 q = p 일 때가 가장 가깝다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '오른쪽 등식은 직각삼각형의 피타고라스 정리일 뿐이다. 이 한 줄이 뒤의 최소제곱과 특이값 분해를 모두 떠받친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-proj-subspace',
        svg: svg({
            width: W, height: H,
            title: '부분공간으로의 정사영과 최단거리',
            desc: '벡터를 부분공간으로 내리면 나머지 조각이 그 공간과 직교하고, 그 발이 부분공간 안에서 가장 가까운 점이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-4. 그람-슈미트 과정 ---- */
add((() => {
    const W = 782; const H = 336;
    const xR = [-1.7, 4.0]; const yR = [-0.8, 3.8];
    const u1 = [3, 1]; const u2 = [1, 3];
    const t = (u1[0] * u2[0] + u1[1] * u2[1]) / (u1[0] * u1[0] + u1[1] * u1[1]);
    const pr = [r2(t * u1[0]), r2(t * u1[1])];
    const v2 = [r2(u2[0] - pr[0]), r2(u2[1] - pr[1])];
    const n1 = Math.hypot(u1[0], u1[1]);
    const n2 = Math.hypot(v2[0], v2[1]);
    const q1 = [r2(u1[0] / n1), r2(u1[1] / n1)];
    const q2 = [r2(v2[0] / n2), r2(v2[1] / n2)];
    const g = [];
    g.push(txt(W / 2, 26, '그람-슈미트 — 기울어진 기저를 직교기저로 펴는 세 걸음', { anchor: 'middle', cls: 'ink bold' }));
    const mk = i => {
        const px = 14 + i * 254; const py = 44; const pw = 248; const ph = 252;
        const { f } = sq(xR, yR, px + 24, py + 50, 36);
        return { px, py, pw, ph, f };
    };
    {
        const { px, py, pw, ph, f } = mk(0);
        g.push(panel(px, py, pw, ph, '1. 주어진 기저', '수직이 아니다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 3], yTicks: [1, 3] }));
        g.push(varw(f, [0, 0], u1, { cls: 's1', width: 2.6 }));
        g.push(varw(f, [0, 0], u2, { cls: 's2', width: 2.6 }));
        g.push(angleArc(S(f, [0, 0]), S(f, u1), S(f, u2), 40, 'θ ≈ 53°'));
        g.push(txt(f.X(3) + 8, f.Y(1) + 4, 'u~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1) - 8, f.Y(3) - 6, 'u~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'v~1 = u~1 으로 시작한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(1);
        g.push(panel(px, py, pw, ph, '2. 겹치는 성분을 뺀다', 'v~2 = u~2 − proj u~2'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 3], yTicks: [1, 3] }));
        g.push(dln(f, [pr, u2], { stroke: C2, sw: 1.6, dash: '5 4' }));
        g.push(varw(f, [0, 0], u1, { cls: 's1', width: 2.4 }));
        g.push(varw(f, [0, 0], u2, { cls: 'ark', width: 1.8, marker: 'ark', dash: '4 3' }));
        g.push(varw(f, [0, 0], pr, { cls: 's3', width: 3 }));
        g.push(varw(f, [0, 0], v2, { cls: 's2', width: 2.8 }));
        g.push(rightAngle(S(f, [0, 0]), [u1[0], -u1[1]], [v2[0], -v2[1]], 12));
        g.push(txt(f.X(3) + 8, f.Y(1) + 4, 'v~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(1) + 6, f.Y(3) - 6, 'u~2', { cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(pr[0]) - 2, f.Y(pr[1]) + 18, 'proj', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(v2[0]) - 8, f.Y(v2[1]) - 6, 'v~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'v~2 = (−0.8, 2.4) 이고 v~1 과 직교한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const { px, py, pw, ph, f } = mk(2);
        g.push(panel(px, py, pw, ph, '3. 길이를 1 로 맞춘다', 'q~i = v~i / ‖v~i‖'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 3], yTicks: [1, 3] }));
        g.push(dln(f, ellipsePts([0, 0], [1, 0], [0, 1]), { stroke: CK, sw: 1, dash: '4 3' }));
        g.push(dln(f, [[0, 0], u1], { stroke: CK, sw: 1, dash: '3 3' }));
        g.push(dln(f, [[0, 0], v2], { stroke: CK, sw: 1, dash: '3 3' }));
        g.push(varw(f, [0, 0], q1, { cls: 's1', width: 3 }));
        g.push(varw(f, [0, 0], q2, { cls: 's2', width: 3 }));
        g.push(rightAngle(S(f, [0, 0]), [q1[0], -q1[1]], [q2[0], -q2[1]], 12));
        g.push(txt(f.X(q1[0]) + 10, f.Y(q1[1]) - 6, 'q~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(q2[0]) - 8, f.Y(q2[1]) - 6, 'q~2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(2.4), f.Y(2.6), '점선 원의 반지름이 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'span{q~1, q~2} = span{u~1, u~2}', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '펴는 동안 생성하는 공간은 한 번도 바뀌지 않는다. 앞에서부터 k 개가 만드는 공간이 늘 같기 때문이고, 그것이 뒤에 나올 QR 분해의 R 을 상삼각으로 만든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-gram-schmidt',
        svg: svg({
            width: W, height: H,
            title: '그람-슈미트 과정의 세 걸음',
            desc: '앞 벡터 방향의 성분을 빼서 직교하게 만들고 마지막에 길이를 1로 맞춘다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-5. QR 분해의 모양 ---- */
add((() => {
    const W = 764; const H = 306;
    const g = [];
    const U = 26;
    g.push(txt(W / 2, 26, 'QR 분해 — 직교하는 부분과 상삼각 부분으로 나눈다', { anchor: 'middle', cls: 'ink bold' }));
    const y0 = 76;
    const drawGrid = (x, y, cols, rows, opt) => box(x, y, cols * U, rows * U, opt);
    g.push(drawGrid(46, y0, 3, 5, { fill: C1, op: 0.16, stroke: C1, sw: 1.6 }));
    g.push(txt(46 + 1.5 * U, y0 - 10, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(46 + 1.5 * U, y0 + 5 * U + 18, 'm × n', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let c = 1; c < 3; c += 1) g.push(ln([[46 + c * U, y0], [46 + c * U, y0 + 5 * U]], { stroke: C1, sw: 0.9 }));
    g.push(txt(150, y0 + 2.5 * U + 6, '=', { anchor: 'middle', cls: 'ink bold' }));
    g.push(drawGrid(174, y0, 3, 5, { fill: C3, op: 0.18, stroke: C3, sw: 1.6 }));
    g.push(txt(174 + 1.5 * U, y0 - 10, 'Q', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(174 + 1.5 * U, y0 + 5 * U + 18, 'm × n', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let c = 1; c < 3; c += 1) g.push(ln([[174 + c * U, y0], [174 + c * U, y0 + 5 * U]], { stroke: C3, sw: 0.9 }));
    g.push(txt(278, y0 + 2.5 * U + 6, '×', { anchor: 'middle', cls: 'ink bold' }));
    const rx = 302; const ry = y0 + U;
    for (let r = 0; r < 3; r += 1) {
        for (let c = 0; c < 3; c += 1) {
            const on = c >= r;
            g.push(box(rx + c * U, ry + r * U, U, U, { fill: on ? C2 : 'none', op: on ? 0.28 : 0, stroke: 'var(--grid)', sw: 0.8, rx: 1 }));
            g.push(txt(rx + c * U + U / 2, ry + r * U + U / 2 + 4, on ? '∗' : '0', { anchor: 'middle', cls: on ? 'ink bold' : 'ink2', size: 'sm' }));
        }
    }
    g.push(drawGrid(rx, ry, 3, 3, { stroke: C2, sw: 1.6 }));
    g.push(txt(rx + 1.5 * U, y0 - 10, 'R', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(rx + 1.5 * U, ry + 3 * U + 18, 'n × n 상삼각', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const tx = 424;
    g.push(txt(tx, y0 + 4, 'Q 의 열 q~1 … q~n 은 A 의 열에', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, y0 + 22, '그람-슈미트를 돌린 정규직교 벡터다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, y0 + 44, 'Q^T Q = I~n  (열끼리 내적하면 단위행렬)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx, y0 + 74, 'R 의 성분은 그냥 내적이다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, y0 + 94, 'r~{ij} = ⟨a~j, q~i⟩  이고  R = Q^T A', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx, y0 + 124, '왜 아래쪽이 0 인가 —', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, y0 + 144, 'a~j 는 q~1 … q~j 만으로 만들어지므로', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, y0 + 164, 'i > j 이면 ⟨a~j, q~i⟩ = 0 이다.', { cls: 'ink', size: 'sm' }));
    g.push(ln([[tx - 12, y0 - 6], [tx - 12, y0 + 176]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, H - 30, 'LU 분해가 소거 과정을 저장한 것이었다면, QR 분해는 직교화 과정을 저장한 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '열이 일차독립이면 언제나 가능하고, R 의 대각성분은 그람-슈미트가 만든 벡터의 길이라 모두 0 이 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-qr-shape',
        svg: svg({
            width: W, height: H,
            title: 'QR 분해에서 각 조각의 모양',
            desc: 'Q 의 열은 정규직교이고 R 은 상삼각이며 그 성분은 원래 열과 정규직교 벡터의 내적이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-6. 네 부분공간의 직교 관계 ---- */
add((() => {
    const W = 780; const H = 366;
    const g = [];
    g.push(txt(W / 2, 26, '행렬 하나가 만드는 네 부분공간 — 양쪽 모두 직각으로 갈린다', { anchor: 'middle', cls: 'ink bold' }));
    const drawSide = (px, title, sub, names, dims, cross) => {
        const py = 46; const pw = 286; const ph = 250;
        const cx = px + pw / 2; const cy = py + 130;
        const out = [panel(px, py, pw, ph, title, sub)];
        out.push(ln([[cx - 92, cy], [cx + 92, cy]], { stroke: C1, sw: 3 }));
        out.push(ln([[cx, cy - 56], [cx, cy + 56]], { stroke: C2, sw: 3 }));
        out.push(rightAngle([cx, cy], [1, 0], [0, -1], 13));
        out.push(pdot(cx, cy, 'var(--ink)', 4.5));
        out.push(txt(cx + 90, cy - 22, names[0], { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        out.push(txt(cx + 90, cy + 20, dims[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        out.push(txt(cx, cy - 66, names[1], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(cx, cy + 76, dims[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(cx - 8, cy + 18, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        out.push(txt(cx, py + ph - 16, cross, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return { html: out.join(''), cx, cy, px, pw, py, ph };
    };
    const L = drawSide(18, '정의역 쪽 — R^n', 'x 가 사는 곳',
        ['행공간 Row(A)', '영공간 N(A)'], ['차원 r', '차원 n − r'],
        'Ax = 0 은 모든 행과의 내적이 0 이라는 뜻이다');
    const R = drawSide(476, '공역 쪽 — R^m', 'Ax 와 b 가 사는 곳',
        ['열공간 Col(A)', '왼쪽 영공간'], ['차원 r', '차원 m − r'],
        '왼쪽 영공간은 A 의 전치의 영공간이다');
    g.push(L.html); g.push(R.html);
    g.push(arw(L.px + L.pw + 8, L.cy - 30, R.px - 8, R.cy - 30, { cls: 's1', width: 2.4 }));
    g.push(txt((L.px + L.pw + R.px) / 2, L.cy - 40, 'A — 일대일로 옮긴다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(L.px + L.pw + 8, L.cy + 46, R.cx - 6, R.cy + 4, { cls: 's2', width: 2, dash: '5 4' }));
    g.push(txt((L.px + L.pw + R.px) / 2 - 10, L.cy + 74, 'A — 전부 0 으로 보낸다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 30, '왼쪽 두 공간의 차원을 더하면 n 이다. 이것이 계수-퇴화차수 정리를 직교로 다시 읽은 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '최소제곱에서 쓰는 것은 오른쪽 그림이다. 잔차가 열공간과 직교한다는 말은 잔차가 왼쪽 영공간에 있다는 말과 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-four-subspaces',
        svg: svg({
            width: W, height: H,
            title: '행렬이 만드는 네 부분공간의 직교 관계',
            desc: '행공간과 영공간이 직교하고 열공간과 왼쪽 영공간이 직교하며 A 는 행공간을 열공간으로 일대일로 옮긴다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-7. 최소제곱 — 이 장의 중심 그림 ---- */
add((() => {
    const W = 796; const H = 366;
    const g = [];
    g.push(txt(W / 2, 26, '해가 없는 Ax = b 에서 무엇을 구하는가 — b 를 열공간에 수직으로 내린다', { anchor: 'middle', cls: 'ink bold' }));
    {
        const px = 18; const py = 44; const pw = 424; const ph = 288;
        const T = iso(px + 206, py + 152, 1);
        const O = T([0, 0, 0]);
        const p = [2.0, -0.6, 0];
        const b = [2.0, -0.6, 2.2];
        g.push(panel(px, py, pw, ph, '열공간은 평면이고 b 는 그 위에 없다'));
        g.push(poly([T([-1.8, -1.8, 0]), T([2.2, -1.8, 0]), T([2.2, 2.2, 0]), T([-1.8, 2.2, 0])],
            { fill: C1, op: 0.14, stroke: C1, sw: 1.4 }));
        [[0.9, 1.7, 0], [-1.4, 0.6, 0], [1.6, 1.6, 0]].forEach(q => {
            g.push(ln([T(q), T(b)], { stroke: CK, sw: 1, dash: '3 3' }));
            g.push(pdot(...T(q), CK, 2.8));
        });
        g.push(arw(...O, ...T([1.1, 0, 0]), { cls: 's1', width: 2.1 }));
        g.push(arw(...O, ...T([0, 1.1, 0]), { cls: 's1', width: 2.1 }));
        g.push(ln([T(p), T(b)], { stroke: C2, sw: 3 }));
        g.push(arw(...O, ...T(b), { cls: 'ark', width: 2.6, marker: 'ark' }));
        g.push(arw(...O, ...T(p), { cls: 's3', width: 3 }));
        g.push(rightAngle(T(p), [0, -1], [O[0] - T(p)[0], O[1] - T(p)[1]], 12));
        g.push(pdot(...T(b), 'var(--ink)', 4.6));
        g.push(pdot(...T(p), C3, 5));
        g.push(txt(T(b)[0] - 8, T(b)[1] - 4, 'b', { anchor: 'end', cls: 'ink bold' }));
        g.push(txt(T([1.15, 0, 0])[0] + 4, T([1.15, 0, 0])[1] + 16, 'a~1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([0, 1.15, 0])[0] + 4, T([0, 1.15, 0])[1] + 16, 'a~2', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([-1.8, 2.2, 0])[0] - 4, T([-1.8, 2.2, 0])[1] - 8, '열공간 Col(A)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T([2.0, -0.6, 1.15])[0] - 8, T([2.0, -0.6, 1.15])[1], '잔차 r = b − Ax', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(T(p)[0], T(p)[1] + 26, 'Ax (사영)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 34, 'Ax 가 갈 수 있는 곳은 색칠한 평면 전부다. b 는 그 위에 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '점선으로 이은 다른 점들은 모두 b 에서 더 멀다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 456; const py = 44; const pw = 322; const ph = 288;
        g.push(panel(px, py, pw, ph, '정규방정식은 여기서 나온다'));
        const steps = [
            ['잔차 r 이 열공간과 직교한다', false],
            ['A 의 열 하나하나와 내적이 0 이다', false],
            ['A^T r = 0', true],
            ['A^T (b − Ax) = 0', true],
            ['A^T A x = A^T b', true],
        ];
        steps.forEach(([s, strong], i) => {
            const by = py + 48 + i * 36;
            g.push(box(px + 18, by, pw - 36, 28, { fill: strong ? C3 : C1, op: strong ? 0.16 : 0.1, stroke: strong ? C3 : C1, sw: 1.2, rx: 5 }));
            g.push(txt(px + pw / 2, by + 19, s, { anchor: 'middle', cls: strong ? 'ink bold' : 'ink', size: 'sm' }));
            if (i < steps.length - 1) g.push(arw(px + pw / 2, by + 28, px + pw / 2, by + 34, { cls: 'ark', width: 1.5 }));
        });
        g.push(txt(px + pw / 2, py + ph - 52, '마지막 줄이 정규방정식이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 30, 'A 의 열이 일차독립이면 A^T A 가 가역이라', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 12, '최소제곱해가 하나로 정해진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 12, '최소제곱은 새로운 아이디어가 아니다. 5장에서 점을 직선에 내리던 일을 벡터를 부분공간에 내리는 일로 한 칸 올린 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-leastsq',
        svg: svg({
            width: W, height: H,
            title: '최소제곱해는 b 를 열공간에 정사영한 것이다',
            desc: '잔차가 열공간과 직교한다는 조건에서 정규방정식이 곧바로 나온다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-8. 직선 맞추기 ---- */
add((() => {
    const W = 784; const H = 368;
    const data = [[1, 2], [2, 3], [3, 5], [4, 6]];
    const a0 = 0.5; const b0 = 1.4;
    const g = [];
    g.push(txt(W / 2, 26, '직선 맞추기 — 세로 거리의 제곱 합을 가장 작게 하는 직선', { anchor: 'middle', cls: 'ink bold' }));
    const xR = [-0.2, 5.0]; const yR = [-0.4, 7.4];
    const mkf = (px, py) => frame({ xRange: xR, yRange: yR, box: { x: px + 54, y: py + 54, w: 240, h: 148 } });
    {
        const px = 18; const py = 44; const pw = 372; const ph = 272;
        const f = mkf(px, py);
        g.push(panel(px, py, pw, ph, '맞춘 직선과 잔차', 'y = 0.5 + 1.4 x'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [2, 4, 6], xLabel: 'x', yLabel: 'y' }));
        g.push(dln(f, [[0, a0], [4.8, a0 + b0 * 4.8]], { stroke: C1, sw: 2.4 }));
        data.forEach(([x, y]) => {
            const yh = a0 + b0 * x;
            g.push(dln(f, [[x, yh], [x, y]], { stroke: C2, sw: 2.6 }));
            g.push(pdot(f.X(x), f.Y(y), 'var(--ink)', 4.4));
            g.push(pdot(f.X(x), f.Y(yh), C1, 3));
            g.push(txt(f.X(x) + 8, f.Y((y + yh) / 2) + 4, nm(r2(y - yh)), { cls: 'ink bold', size: 'sm' }));
        });
        g.push(txt(f.X(1.5), f.Y(7.0), 'y = 0.5 + 1.4x', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 46, '잔차의 합 0.1 − 0.3 + 0.3 − 0.1 = 0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 26, 'x 와의 내적도 0.1 − 0.6 + 0.9 − 0.4 = 0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 8, '이 두 줄이 정규방정식 두 줄이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 402; const py = 44; const pw = 372; const ph = 272;
        const f = mkf(px, py);
        const P = [2, 5.6];
        const yh = a0 + b0 * P[0];
        const tt = (P[1] - yh) / (1 + b0 * b0);
        const fx = P[0] + b0 * tt;
        const fy = a0 + b0 * fx;
        g.push(panel(px, py, pw, ph, '무엇의 거리를 줄이는가', '세로 거리이지 수직 거리가 아니다'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4], yTicks: [2, 4, 6], xLabel: 'x', yLabel: 'y' }));
        g.push(dln(f, [[0, a0], [4.8, a0 + b0 * 4.8]], { stroke: C1, sw: 2.4 }));
        data.forEach(([x, y]) => g.push(pdot(f.X(x), f.Y(y), CK, 3)));
        g.push(dln(f, [[P[0], yh], P], { stroke: C2, sw: 3 }));
        g.push(dln(f, [P, [fx, fy]], { stroke: CK, sw: 1.8, dash: '4 3' }));
        g.push(rightAngle(S(f, [fx, fy]), [S(f, P)[0] - f.X(fx), S(f, P)[1] - f.Y(fy)], [-1, -b0], 8));
        g.push(pdot(f.X(P[0]), f.Y(P[1]), 'var(--ink)', 5));
        g.push(pdot(f.X(P[0]), f.Y(yh), C2, 3.4));
        g.push(pdot(f.X(fx), f.Y(fy), CK, 3.4));
        g.push(txt(f.X(P[0]) - 8, f.Y(P[1]) - 4, '점 P', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(P[0]) - 8, f.Y(4.5), '세로 거리', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(3.25), f.Y(5.55), '수직 거리', { cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 46, '최소제곱은 굵은 선(세로)의 제곱 합을 줄인다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 26, 'y 를 예측하는 것이 목적이라 y 방향 오차만 센다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 8, '점선(수직 거리)을 줄이는 것은 10장의 주성분분석이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '첫 줄은 잔차가 1 로만 이루어진 열과 직교한다는 뜻이고, 둘째 줄은 잔차가 x 열과 직교한다는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '점이 네 개이므로 직선 맞추기는 4차원 공간에서 평면으로 내리는 정사영이었다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-regression',
        svg: svg({
            width: W, height: H,
            title: '최소제곱으로 직선 맞추기',
            desc: '세로 거리의 제곱 합을 최소로 하는 직선이고 잔차는 상수열과 x열 모두와 직교한다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10장 — 특이값 분해와 응용
 * ================================================================== */

/* 이 장이 계속 쓰는 예제 행렬. A = [[3,0],[4,5]] */
const SIG1 = Math.sqrt(45);
const SIG2 = Math.sqrt(5);
const V1 = [1 / Math.SQRT2, 1 / Math.SQRT2];
const V2 = [-1 / Math.SQRT2, 1 / Math.SQRT2];
const AV1 = [3 / Math.SQRT2, 9 / Math.SQRT2];   // = σ₁u₁
const AV2 = [-3 / Math.SQRT2, 1 / Math.SQRT2];  // = σ₂u₂

/* ---- 10-1. 단위원이 타원으로 간다 ---- */
add((() => {
    const W = 800; const H = 352;
    const g = [];
    g.push(txt(W / 2, 26, '행렬이 하는 일 — 단위원을 타원으로 보낸다. 회전 · 늘이기 · 회전 셋으로 갈린다', { anchor: 'middle', cls: 'ink bold' }));
    const small = [-1.55, 1.55];
    const big = [-8.2, 8.2];
    const drawPanel = (i, R, title, sub, aVec, bVec, marks) => {
        const px = 12 + i * 200; const py = 44; const pw = 176; const ph = 244;
        const { f } = sq(R, R, px + 10, py + 52, 156 / (R[1] - R[0]));
        const out = [panel(px, py, pw, ph, title, sub)];
        out.push(axes(f, { xRange: R, yRange: R, xTicks: R[1] > 4 ? [5] : [1], yTicks: R[1] > 4 ? [5] : [1] }));
        out.push(dpoly(f, ellipsePts([0, 0], aVec, bVec), { fill: C1, op: 0.12, stroke: C1, sw: 2 }));
        out.push(dln(f, arcPts([0, 0], aVec, bVec, 0, Math.PI / 2), { stroke: C3, sw: 3.4 }));
        out.push(varw(f, [0, 0], aVec, { cls: 's1', width: 2.6 }));
        out.push(varw(f, [0, 0], bVec, { cls: 's2', width: 2.6 }));
        marks.forEach(([p, s, o = {}]) => out.push(txt(f.X(p[0]) + (o.dx ?? 0), f.Y(p[1]) + (o.dy ?? 0), s,
            { anchor: o.anchor ?? 'start', cls: 'ink bold', size: 'sm' })));
        return { html: out.join(''), px, py, pw, ph };
    };
    const p0 = drawPanel(0, small, '1. 단위원', '눈금 1', V1, V2,
        [[V1, 'v~1', { dx: 6, dy: -6 }], [V2, 'v~2', { dx: -6, dy: -6, anchor: 'end' }]]);
    const p1 = drawPanel(1, small, '2. V^T 로 돌린다', '눈금 1', [1, 0], [0, 1],
        [[[1, 0], 'e~1', { dx: 4, dy: -10 }], [[0, 1], 'e~2', { dx: 8, dy: -4 }]]);
    const p2 = drawPanel(2, big, '3. Σ 로 늘인다', '눈금 5', [SIG1, 0], [0, SIG2],
        [[[2.6, -3.9], 'σ~1 ≈ 6.71', { anchor: 'middle' }], [[0.5, 3.6], 'σ~2 ≈ 2.24', {}]]);
    const p3 = drawPanel(3, big, '4. U 로 돌린다', '눈금 5', AV1, AV2,
        [[AV1, 'σ~1 u~1', { dx: -6, dy: -8, anchor: 'end' }], [AV2, 'σ~2 u~2', { dx: -2, dy: 20, anchor: 'end' }]]);
    [p0, p1, p2, p3].forEach(p => g.push(p.html));
    [['V^T', p0], ['Σ', p1], ['U', p2]].forEach(([lab, p]) => {
        g.push(arw(p.px + p.pw + 4, p.py + 150, p.px + p.pw + 20, p.py + 150, { cls: 'ark', width: 1.8 }));
        g.push(txt(p.px + p.pw + 12, p.py + 138, lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 48, '초록 굵은 호는 같은 조각이 어디로 갔는지 좇으라고 칠한 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 30, 'V^T 와 U 는 직교행렬이라 길이도 각도 바꾸지 않는다. 크기를 바꾸는 것은 가운데 Σ 하나뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '앞의 두 칸과 뒤의 두 칸은 눈금이 다르다. 반지름 1 이던 원이 긴 쪽으로 σ~1 ≈ 6.71 배까지 늘어난 것이 세 번째 칸이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-svd-ellipse',
        svg: svg({
            width: W, height: H,
            title: '특이값 분해가 단위원에 하는 일',
            desc: 'V의 전치로 돌리고 시그마로 축을 늘이고 U로 다시 돌리면 단위원이 타원이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-2. U, Σ, V^T 의 모양 ---- */
add((() => {
    const W = 776; const H = 344;
    const g = [];
    const U = 17;
    g.push(txt(W / 2, 26, 'A = U Σ V^T — 세 조각의 크기', { anchor: 'middle', cls: 'ink bold' }));
    /** 한 줄짜리 블록 행. 이름은 위에, 크기는 아래에 한 높이로 맞춰 적는다. */
    const blockRow = (px, py, pw, blocks) => {
        const out = [];
        const bandTop = py + 62; const band = 5 * U;
        const total = blocks.reduce((a, b) => a + b.c * U, 0) + (blocks.length - 1) * 17;
        let x = px + (pw - total) / 2;
        blocks.forEach((bl, i) => {
            const bw = bl.c * U; const bh = bl.r * U;
            const yy = bandTop + (band - bh) / 2;
            if (bl.kind === 'sig') {
                out.push(box(x, yy, bw, bh, { stroke: C2, sw: 1.6 }));
                for (let d = 0; d < Math.min(bl.c, bl.r); d += 1) {
                    out.push(box(x + d * U, yy + d * U, U, U, { fill: C2, op: 0.45, stroke: C2, sw: 0.8, rx: 1 }));
                }
                if (bl.r > bl.c) out.push(txt(x + bw / 2, yy + bh - 8, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            } else {
                const col = bl.kind === 'a' ? C1 : C3;
                out.push(box(x, yy, bw, bh, { fill: col, op: 0.18, stroke: col, sw: 1.6 }));
            }
            out.push(txt(x + bw / 2, bandTop - 8, bl.n, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            out.push(txt(x + bw / 2, bandTop + band + 20, bl.d, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            x += bw;
            if (i < blocks.length - 1) {
                out.push(txt(x + 8.5, bandTop + band / 2 + 5, bl.op, { anchor: 'middle', cls: 'ink bold' }));
                x += 17;
            }
        });
        return out.join('');
    };
    g.push(panel(14, 44, 402, 200, '완전형', 'U 와 V 가 정사각 직교행렬이다'));
    g.push(blockRow(14, 44, 402, [
        { n: 'A', c: 3, r: 5, kind: 'a', d: 'm × n', op: '=' },
        { n: 'U', c: 5, r: 5, kind: 'o', d: 'm × m', op: '×' },
        { n: 'Σ', c: 3, r: 5, kind: 'sig', d: 'm × n', op: '×' },
        { n: 'V^T', c: 3, r: 3, kind: 'o', d: 'n × n' },
    ]));
    g.push(panel(430, 44, 332, 200, '축소형', '0 만 있는 부분을 잘라낸 것 (r = 계수)'));
    g.push(blockRow(430, 44, 332, [
        { n: 'A', c: 3, r: 5, kind: 'a', d: 'm × n', op: '=' },
        { n: 'U~r', c: 2, r: 5, kind: 'o', d: 'm × r', op: '×' },
        { n: 'Σ~r', c: 2, r: 2, kind: 'sig', d: 'r × r', op: '×' },
        { n: 'V~r^T', c: 3, r: 2, kind: 'o', d: 'r × n' },
    ]));
    g.push(txt(W / 2, 268, 'Σ 는 대각선에만 값이 있고 그 값이 σ~1 ≥ σ~2 ≥ … ≥ 0 이다. 0 이 아닌 σ 의 개수가 계수 r 이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 288, '완전형은 이론을 적기 좋고 축소형은 계산과 저장에 쓴다. 두 형태가 주는 A 는 똑같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 310, '고유값 분해와 달리 정사각행렬이 아니어도 되고, 어떤 행렬에도 존재하며, σ 는 언제나 실수이고 0 이상이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 330, '대각화가 되지 않는 행렬도 특이값 분해는 된다. 그 점이 SVD 를 실무의 기본 도구로 만들었다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-svd-shape',
        svg: svg({
            width: W, height: H,
            title: '특이값 분해에서 각 조각의 크기',
            desc: '완전형과 축소형에서 U 시그마 V의 전치가 각각 어떤 크기인지 보인다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-3. 계수 1 조각으로 쪼개기 ---- */
add((() => {
    const W = 782; const H = 306;
    const g = [];
    const A = [[3, 0], [4, 5]];
    const A1 = [[1.5, 1.5], [4.5, 4.5]];
    const A2 = [[1.5, -1.5], [-0.5, 0.5]];
    g.push(txt(W / 2, 26, 'A = σ~1 u~1 v~1^T + σ~2 u~2 v~2^T — 계수 1 짜리 조각의 합', { anchor: 'middle', cls: 'ink bold' }));
    const CW = 58; const CH = 46;
    const grid = (x, y, M, title, sub, col) => {
        const out = [];
        const mx = Math.max(...M.flat().map(Math.abs));
        M.forEach((row, r) => row.forEach((v, c) => {
            out.push(box(x + c * CW, y + r * CH, CW, CH, { fill: col, op: 0.55 * (Math.abs(v) / mx), stroke: 'var(--grid)', sw: 1, rx: 2 }));
            out.push(txt(x + c * CW + CW / 2, y + r * CH + CH / 2 + 5, nm(v), { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }));
        out.push(box(x, y, 2 * CW, 2 * CH, { stroke: col, sw: 1.8, rx: 3 }));
        out.push(txt(x + CW, y - 10, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + CW, y + 2 * CH + 20, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    const y0 = 84;
    g.push(grid(64, y0, A, 'A', '원래 행렬', C1));
    g.push(txt(214, y0 + CH + 6, '=', { anchor: 'middle', cls: 'ink bold' }));
    g.push(grid(248, y0, A1, '첫째 조각', 'σ~1 = 6.71 — 크다', C3));
    g.push(txt(398, y0 + CH + 6, '+', { anchor: 'middle', cls: 'ink bold' }));
    g.push(grid(432, y0, A2, '둘째 조각', 'σ~2 = 2.24 — 작다', C2));
    const tx = 574;
    g.push(ln([[tx - 16, 64], [tx - 16, 244]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(tx, 78, '두 조각 모두 계수가 1 이다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, 96, '(모든 행이 서로 비례한다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx, 126, '첫 조각만 남기면', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, 144, '오차가 정확히 둘째 조각이고', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, 162, '그 크기가 σ~2 = 2.24 다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx, 192, '‖A‖~F² = 3² + 4² + 5²', { cls: 'ink', size: 'sm' }));
    g.push(txt(tx, 210, '     = 50 = σ~1² + σ~2²', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx, 236, '성분의 제곱 합이 σ 의 제곱 합이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 30, '큰 σ 에 딸린 조각이 행렬의 모양을 대부분 결정한다. 작은 조각을 버리는 것이 낮은 계수 근사다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '칸의 색이 진할수록 절댓값이 큰 성분이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-svd-rank1',
        svg: svg({
            width: W, height: H,
            title: '특이값 분해를 계수 1 조각의 합으로 읽기',
            desc: '2×2 행렬이 두 개의 계수 1 행렬로 갈리고 그 크기를 특이값이 정한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-4. 낮은 계수 근사와 압축 ---- */
add((() => {
    const N = 12;
    const M = [];
    for (let i = 0; i < N; i += 1) {
        const row = [];
        for (let j = 0; j < N; j += 1) {
            const a = 0.85 * Math.exp(-(((i - 3) ** 2) + ((j - 3.5) ** 2)) / 9);
            const b = 0.6 * Math.exp(-(((i - 8.5) ** 2) + ((j - 8) ** 2)) / 6);
            const c = 0.2 * Math.sin(i * 0.9) * Math.cos(j * 1.25);
            // 아래 항은 분리되지 않아 특이값에 꼬리를 남긴다.
            const d = 0.35 * Math.exp(-Math.hypot(i - 7, j - 2) / 2.4);
            row.push(0.16 + a + b + c + d);
        }
        M.push(row);
    }
    const dec = svdJacobi(M);
    const ks = [1, 2, 4];
    const approx = ks.map(k => rankK(dec, k, N, N));
    const lo = Math.min(...M.flat());
    const hi = Math.max(...M.flat());
    const W = 716; const H = 374;
    const g = [];
    g.push(txt(W / 2, 26, '큰 특이값 몇 개만 남겨도 그림이 살아난다', { anchor: 'middle', cls: 'ink bold' }));
    const CS = 11;
    const drawImg = (x, y, G, title, sub) => {
        const out = [];
        for (let i = 0; i < N; i += 1) {
            for (let j = 0; j < N; j += 1) {
                const v = Math.min(1, Math.max(0, (G[i][j] - lo) / (hi - lo)));
                out.push(`<rect x="${x + j * CS}" y="${y + i * CS}" width="${CS}" height="${CS}" fill="${C1}" fill-opacity="${r2(0.06 + 0.9 * v)}"/>`);
            }
        }
        out.push(box(x, y, N * CS, N * CS, { stroke: CK, sw: 1.2, rx: 2 }));
        out.push(txt(x + (N * CS) / 2, y - 9, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + (N * CS) / 2, y + N * CS + 17, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    const gy = 66;
    const total = dec.s.reduce((a, v) => a + v * v, 0);
    const cum = k => dec.s.slice(0, k).reduce((a, v) => a + v * v, 0) / total;
    g.push(drawImg(38, gy, M, '원본 (계수 12)', '값 144 개'));
    ks.forEach((k, i) => {
        g.push(drawImg(200 + i * 162, gy, approx[i], `계수 ${k} 근사`, `값 ${k * (N + N + 1)} 개 · 에너지 ${(cum(k) * 100).toFixed(2)}%`));
    });
    const bx = 70; const by = 322; const bw = 570; const bh = 58;
    const smax = dec.s[0];
    g.push(txt(bx - 14, 242, '특이값 크기 (내림차순)', { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[bx - 6, by], [bx + bw, by]], { stroke: CK, sw: 1.2 }));
    dec.s.forEach((v, i) => {
        const w = bw / N - 12;
        const x = bx + (i * bw) / N;
        const h = (v / smax) * bh;
        g.push(box(x, by - h, w, h, { fill: i < 4 ? C3 : CK, op: i < 4 ? 0.6 : 0.35, stroke: 'none', sw: 0, rx: 1 }));
        if (i < 5) g.push(txt(x + w / 2, by - h - 5, v.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x + w / 2, by + 14, String(i + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(bx + bw + 6, by + 14, '번호', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '특이값이 빠르게 작아지는 행렬일수록 적은 조각으로 잘 근사된다. 근사값이 0 아래로 내려가면 0 으로 잘라 그렸다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-svd-approx',
        svg: svg({
            width: W, height: H,
            title: '낮은 계수 근사와 압축',
            desc: '12×12 짜리 그림을 계수 1, 2, 4 로 근사하면 큰 특이값 몇 개만으로도 형태가 살아난다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-5. 조건수 ---- */
add((() => {
    const W = 752; const H = 344;
    const g = [];
    g.push(txt(W / 2, 26, '조건수 — 타원이 납작할수록 답이 흔들린다', { anchor: 'middle', cls: 'ink bold' }));
    const R = [-3.6, 3.6];
    const mk = (px, title, sub, s1, s2) => {
        const py = 44; const pw = 356; const ph = 250;
        const { f } = sq(R, R, px + 88, py + 50, 176 / (R[1] - R[0]));
        const d1 = [1 / Math.SQRT2, 1 / Math.SQRT2];
        const d2 = [-1 / Math.SQRT2, 1 / Math.SQRT2];
        const a = [s1 * d1[0], s1 * d1[1]];
        const b = [s2 * d2[0], s2 * d2[1]];
        const out = [panel(px, py, pw, ph, title, sub)];
        out.push(axes(f, { xRange: R, yRange: R, xTicks: [1, 3], yTicks: [1, 3] }));
        out.push(dln(f, ellipsePts([0, 0], [1, 0], [0, 1]), { stroke: CK, sw: 1.2, dash: '4 3' }));
        out.push(dpoly(f, ellipsePts([0, 0], a, b), { fill: C1, op: 0.16, stroke: C1, sw: 2.2 }));
        out.push(varw(f, [0, 0], a, { cls: 's1', width: 2.4 }));
        out.push(varw(f, [0, 0], b, { cls: 's2', width: 2.4 }));
        out.push(txt(f.X(a[0]) + 8, f.Y(a[1]) - 4, `σ~1 = ${s1}`, { cls: 'ink bold', size: 'sm' }));
        out.push(txt(f.X(-2.0), f.Y(1.9), `σ~2 = ${s2}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(f.X(1.1), f.Y(-1.35), '단위원', { cls: 'ink2', size: 'sm' }));
        return { out, px, py, pw, ph };
    };
    {
        const { out, px, py, pw, ph } = mk(18, '조건수 3 — 건강하다', '타원이 둥글다', 3, 1);
        g.push(out.join(''));
        g.push(txt(px + pw / 2, py + ph - 34, 'b 의 상대오차 1% 는 해의 상대오차 3% 안쪽이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'κ = σ~1 / σ~2 = 3', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    {
        const { out, px, py, pw, ph } = mk(388, '조건수 30 — 위험하다', '타원이 납작하다', 3, 0.1);
        g.push(out.join(''));
        g.push(txt(px + pw / 2, py + ph - 34, '같은 1% 가 30% 까지 부풀 수 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, 'κ = σ~1 / σ~2 = 30', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '얇은 방향으로는 x 를 크게 움직여도 Ax 가 조금밖에 안 움직인다. 거꾸로 읽으면 b 의 작은 오차가 x 의 큰 오차가 된다는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '조건수는 그 최악의 증폭률이고, σ~2 = 0 이면 무한대가 된다. 그것이 특이행렬이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-condition',
        svg: svg({
            width: W, height: H,
            title: '조건수와 타원의 납작한 정도',
            desc: '가장 큰 특이값과 가장 작은 특이값의 비가 클수록 해가 입력 오차에 민감하다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-6. 주성분분석 ---- */
add((() => {
    const W = 768; const H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '주성분 — 데이터가 가장 많이 퍼진 방향을 축으로 삼는다', { anchor: 'middle', cls: 'ink bold' }));
    /** 2차원 점 무리의 평균과 주축을 실제로 계산한다. */
    const pca = pts => {
        const n = pts.length;
        const mx = pts.reduce((a, p) => a + p[0], 0) / n;
        const my = pts.reduce((a, p) => a + p[1], 0) / n;
        let sxx = 0; let sxy = 0; let syy = 0;
        pts.forEach(([x, y]) => {
            sxx += (x - mx) ** 2; sxy += (x - mx) * (y - my); syy += (y - my) ** 2;
        });
        return { mx, my, e: eig2(sxx, sxy, syy), n };
    };
    const R = [-3.4, 3.4];
    {
        const px = 18; const py = 44; const pw = 356; const ph = 254;
        const { f } = sq(R, R, px + 88, py + 48, 160 / (R[1] - R[0]));
        const pts = [[1, 2], [2, 1], [-1, -2], [-2, -1]];
        const { mx, my, e, n } = pca(pts);
        const sd1 = Math.sqrt(e.l1 / (n - 1));
        const sd2 = Math.sqrt(e.l2 / (n - 1));
        g.push(panel(px, py, pw, ph, '손으로 따라갈 수 있는 네 점', '평균은 원점이다'));
        g.push(axes(f, { xRange: R, yRange: R, xTicks: [1, 3], yTicks: [1, 3] }));
        g.push(dln(f, [[-3.2 * e.v1[0], -3.2 * e.v1[1]], [3.2 * e.v1[0], 3.2 * e.v1[1]]], { stroke: C1, sw: 1, dash: '5 4' }));
        pts.forEach(p => g.push(pdot(f.X(p[0]), f.Y(p[1]), 'var(--ink)', 4.6)));
        g.push(varw(f, [mx, my], [mx + sd1 * e.v1[0], my + sd1 * e.v1[1]], { cls: 's1', width: 3 }));
        g.push(varw(f, [mx, my], [mx + sd2 * e.v2[0], my + sd2 * e.v2[1]], { cls: 's2', width: 3 }));
        g.push(txt(f.X(sd1 * e.v1[0]) + 10, f.Y(sd1 * e.v1[1]) - 6, 'PC1', { cls: 'ink bold', size: 'sm' }));
        g.push(txt(f.X(sd2 * e.v2[0]) - 8, f.Y(sd2 * e.v2[1]) - 6, 'PC2', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 34, `PC1 방향 분산 ${(e.l1 / (n - 1)).toFixed(1)}, PC2 방향 분산 ${(e.l2 / (n - 1)).toFixed(2)}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, `첫 축이 전체 퍼짐의 ${((e.l1 / (e.l1 + e.l2)) * 100).toFixed(0)}% 를 설명한다`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    {
        const px = 394; const py = 44; const pw = 356; const ph = 254;
        const { f } = sq(R, R, px + 88, py + 48, 160 / (R[1] - R[0]));
        // 결정적 유사난수. 같은 그림이 늘 나와야 한다.
        let seed = 20260813;
        const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
        const gauss = () => {
            const a = Math.max(rnd(), 1e-9); const b = rnd();
            return Math.sqrt(-2 * Math.log(a)) * Math.cos(2 * Math.PI * b);
        };
        const pts = [];
        for (let i = 0; i < 46; i += 1) {
            const s = gauss() * 1.4; const t = gauss() * 0.4;
            pts.push([(s - t) / Math.SQRT2, (s + t) / Math.SQRT2]);
        }
        const { mx, my, e, n } = pca(pts);
        const sd1 = Math.sqrt(e.l1 / (n - 1));
        const sd2 = Math.sqrt(e.l2 / (n - 1));
        g.push(panel(px, py, pw, ph, '점이 많아져도 하는 일은 같다', '수직으로 내려 한 축에 담는다'));
        g.push(axes(f, { xRange: R, yRange: R, xTicks: [1, 3], yTicks: [1, 3] }));
        g.push(dln(f, [[mx - 3.2 * e.v1[0], my - 3.2 * e.v1[1]], [mx + 3.2 * e.v1[0], my + 3.2 * e.v1[1]]],
            { stroke: C1, sw: 1.6, dash: '6 4' }));
        pts.forEach(p => {
            const t = (p[0] - mx) * e.v1[0] + (p[1] - my) * e.v1[1];
            const q = [mx + t * e.v1[0], my + t * e.v1[1]];
            g.push(dln(f, [p, q], { stroke: CK, sw: 0.9, dash: '2 3' }));
            g.push(pdot(f.X(q[0]), f.Y(q[1]), C1, 2.4));
            g.push(pdot(f.X(p[0]), f.Y(p[1]), 'var(--ink)', 3.4));
        });
        g.push(varw(f, [mx, my], [mx + 1.9 * sd1 * e.v1[0], my + 1.9 * sd1 * e.v1[1]], { cls: 's1', width: 3 }));
        g.push(varw(f, [mx, my], [mx + 2.4 * sd2 * e.v2[0], my + 2.4 * sd2 * e.v2[1]], { cls: 's2', width: 3 }));
        g.push(txt(px + pw / 2, py + ph - 34, `PC1 이 설명하는 몫 ${((e.l1 / (e.l1 + e.l2)) * 100).toFixed(0)}%`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px + pw / 2, py + ph - 16, '점선이 잃어버리는 정보다. 그 합을 가장 작게 하는 축이 PC1 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, H - 30, '회귀는 세로 거리를, 주성분은 수직 거리를 줄인다. 같은 산점도를 놓고도 답이 다른 이유가 여기에 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '오른쪽 점 무리는 설명을 위해 만들어 낸 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'la-in-pca',
        svg: svg({
            width: W, height: H,
            title: '주성분분석이 찾는 축',
            desc: '중심을 옮긴 데이터가 가장 많이 퍼진 방향이 첫 주성분이고 그 축으로 수직으로 내리면 정보 손실이 가장 적다',
            body: g.join(''),
        }),
    };
})());

export default figures;
