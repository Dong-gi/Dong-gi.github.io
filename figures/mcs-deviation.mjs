/**
 * mcs 22장(평균에서 벗어나기) · 23장(무작위 행보) · 24장(점화식)의 그림.
 *
 * 이름은 모두 `mcs-d-` 로 시작한다(담당 G 에게 배정된 접두어. d 는 deviation).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `T~n` 표기를, 나머지는 유니코드(≤ ≥ ≠ · × ⋯ √ Σ Θ σ μ β 등)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 중심 그림은 <b>한계가 분포를 얼마나 느슨하게 감싸는가</b>다.
 * 마르코프·체비쇼프·체르노프 세 상한과 참값을 같은 로그 축에 겹쳐 놓아야
 * 22장의 이야기가 한 장으로 보인다. 그래서 이항 꼬리를 정확히 계산하는
 * 헬퍼(logFact/binTailGe)를 두고 세 그림이 함께 쓴다.
 */
import { svg, frame, txt, esc } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
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
 * (figures/mcs-prob.mjs 의 arw 를 본떴다.)
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG }[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.4, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="${col}"/>`;

const ring = (x, y, r, col = C2, sw = 2, dash) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="none" stroke="${col}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;

/** 색을 직접 넣는 텍스트. txt() 는 CSS 클래스만 받으므로 계열색 글자에 이것을 쓴다. */
function ctxt(x, y, s, col, { anchor = 'start', size = 'sm', bold = false } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' class="sm"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(s)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 19, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 35, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 막대그래프. vals 는 0~1 로 규격화한 높이. */
function bars(x0, yBase, bw, gap, vals, labels, hMax, { col = C1, hl = [], hlCol = C2 } = {}) {
    const g = [];
    vals.forEach((v, i) => {
        const x = x0 + i * (bw + gap);
        const h = v * hMax;
        const c = hl.includes(i) ? hlCol : col;
        g.push(box(x, yBase - h, bw, h, { fill: c, op: 0.8, stroke: c, sw: 0.7, rx: 1 }));
        if (labels && labels[i] !== undefined && labels[i] !== '') {
            g.push(txt(x + bw / 2, yBase + 14, String(labels[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    g.push(ln([[x0 - 6, yBase], [x0 + vals.length * (bw + gap) + 2, yBase]], { stroke: CK, sw: 1.2 }));
    return g.join('');
}

/* ------------------------------------------------------------------ *
 * 이항 꼬리를 정확히 재는 도구. n = 1000 까지 로그 팩토리얼로 계산한다.
 * 스털링 근사를 쓰지 않는 이유는 그림에서 12자리 아래의 확률까지
 * 그려야 하고 그 영역에서 상대 오차가 눈에 보이기 때문이다.
 * ------------------------------------------------------------------ */

const LF = [0];
for (let i = 1; i <= 4000; i += 1) LF[i] = LF[i - 1] + Math.log(i);
const logC = (n, k) => LF[n] - LF[k] - LF[n - k];

/** Pr[Bin(n,p) ≥ k0]. k0 은 실수여도 된다(위로 올림). */
function binTailGe(n, k0, p) {
    const lo = Math.max(0, Math.ceil(k0 - 1e-9));
    let s = 0;
    for (let k = lo; k <= n; k += 1) s += Math.exp(logC(n, k) + k * Math.log(p) + (n - k) * Math.log(1 - p));
    return s;
}

const binPmf = (n, k, p) => Math.exp(logC(n, k) + k * Math.log(p) + (n - k) * Math.log(1 - p));

/** 체르노프 지수 β(c) = c ln c − c + 1. */
const beta = c => c * Math.log(c) - c + 1;

/** 감소하는 f 에 대해 f(x) = target 인 x 를 이분법으로 찾는다(그래프 자르기용). */
function crossing(f, from, to, target) {
    if (f(from) <= target) return from;
    if (f(to) >= target) return to;
    let lo = from;
    let hi = to;
    for (let i = 0; i < 60; i += 1) {
        const m = (lo + hi) / 2;
        if (f(m) > target) lo = m; else hi = m;
    }
    return hi;
}

const fmt = (v, d = 3) => Number.parseFloat(v.toPrecision(d)).toString();

/** 1.23e-9 를 1.2 × 10⁻⁹ 로. 유니코드 위첨자만 쓴다. */
const SUP = { '-': '⁻', 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹' };
function sci(v, d = 2) {
    if (v === 0) return '0';
    const e = Math.floor(Math.log10(Math.abs(v)));
    const m = v / 10 ** e;
    const sup = String(e).split('').map(ch => SUP[ch]).join('');
    return `${Number.parseFloat(m.toFixed(d - 1))} × 10${sup}`;
}

/* ================================================================== *
 * 22장 — 평균에서 벗어나기
 * ================================================================== */

/* 세 한계와 참값을 같은 로그 축에 — 이 블록의 중심 그림 */
add((() => {
    const W = 680;
    const H = 360;
    const g = [];
    const n = 1000;
    const p = 0.5;
    const mu = n * p;
    const vr = n * p * (1 - p);

    const L10 = v => Math.log10(Math.max(v, 1e-300));
    const fMarkov = x => L10(mu / x);
    const fCheb = x => L10(Math.min(1, vr / (x - mu) ** 2));
    const fChern = x => L10(Math.exp(-beta(x / mu) * mu));
    const fReal = x => L10(binTailGe(n, x, p));

    g.push(txt(20, 24, '공정한 동전 1000번의 앞면 수 T — 세 상한과 참값을 겹쳐 놓았다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 42, '세로축은 확률의 상용로그다. 한 칸 내려가면 확률이 10분의 1이 된다', { cls: 'ink2', size: 'sm' }));

    const f = frame({ xRange: [505, 720], yRange: [-14, 0], box: { x: 64, y: 82, w: 380, h: 210 } });
    g.push(f.axes({
        xLabel: 'x', yLabel: 'log₁₀ Pr[T ≥ x]',
        xTicks: [520, 560, 600, 640, 680, 720], yTicks: [0, -2, -4, -6, -8, -10, -12, -14],
    }));

    const draw = (fn, cls, dash) => {
        const to = crossing(fn, 505, 720, -13.9);
        g.push(f.curve(fn, { from: 505, to, cls, dash, steps: 160 }));
    };
    draw(fMarkov, 's3', '6 4');
    draw(fCheb, 's3');
    draw(fChern, 's2');
    draw(fReal, 's1');

    g.push(f.label([700, fMarkov(700)], '마르코프', { dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(f.label([700, fCheb(700)], '체비쇼프', { dy: 16, anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(f.label([640, fChern(640)], '체르노프', { dx: 8, dy: 4, cls: 'ink', size: 'sm' }));
    g.push(f.label([575, fReal(575)], '참값', { dx: -6, dy: 14, anchor: 'end', cls: 'ink bold', size: 'sm' }));

    g.push(f.guide([600, -14], [600, 0]));
    g.push(f.dot([600, fReal(600)], { cls: 'f1', r: 3.5 }));
    g.push(f.dot([600, fChern(600)], { cls: 'f2', r: 3.5 }));
    g.push(f.dot([600, fCheb(600)], { cls: 'f3', r: 3.5 }));

    const x0 = 470;
    g.push(txt(x0, 78, 'x = 600 에서 (평균의 1.2배)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 98, `마르코프  ${fmt(mu / 600, 3)}`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 116, `체비쇼프  ${fmt(vr / 100 ** 2, 3)}`, { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(x0, 134, `체르노프  ${sci(Math.exp(-beta(1.2) * mu))}`, C2, { bold: true }));
    g.push(ctxt(x0, 152, `참값      ${sci(binTailGe(n, 600, p))}`, C1, { bold: true }));
    g.push(txt(x0, 178, '한 단계 내려갈수록', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 196, '네 자리씩 좁혀진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 224, '더 아는 것이 있어야', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 242, '더 좁게 잡는다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 262, '평균만 → 마르코프', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 278, '분산까지 → 체비쇼프', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 294, '독립인 합 → 체르노프', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 314], [W - 20, 314]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 334, '마르코프와 체비쇼프는 다항식으로 떨어지고 체르노프는 지수로 떨어진다. 그래서 로그 축에서 앞의 둘은 거의 눕고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 352, '체르노프만 참값과 나란히 내려간다. 그래도 참값과 체르노프 사이에도 여전히 몇 자리의 여유가 남는다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-bounds-envelope',
        svg: svg({
            width: W, height: H,
            title: '마르코프 · 체비쇼프 · 체르노프 한계와 참값',
            desc: '공정한 동전 1000번의 앞면 수에 대해 세 상한과 정확한 꼬리확률을 상용로그 축에 겹쳐 그린 그림',
            body: g.join(''),
        }),
    };
})());

/* 표준편차가 재는 것 — 체비쇼프가 보장하는 것과 실제 */
add((() => {
    const W = 660;
    const H = 348;
    const g = [];
    const n = 100;
    const p = 0.5;
    const mu = 50;
    const sd = 5;

    g.push(txt(20, 24, '공정한 동전 100번의 앞면 수 — 평균 50, 표준편차 5', { cls: 'ink bold', size: 'sm' }));

    const lo = 30;
    const hi = 70;
    const vals = [];
    const labels = [];
    for (let k = lo; k <= hi; k += 1) {
        vals.push(binPmf(n, k, p) / binPmf(n, 50, p));
        labels.push(k % 5 === 0 ? String(k) : '');
    }
    const bw = 12;
    const gap = 2;
    const x0 = 46;
    const yBase = 210;
    const hl = [];
    for (let k = lo; k <= hi; k += 1) if (Math.abs(k - mu) >= 2 * sd) hl.push(k - lo);
    g.push(bars(x0, yBase, bw, gap, vals, labels, 130, { col: C1, hl, hlCol: C2 }));

    const xAt = k => x0 + (k - lo) * (bw + gap) + bw / 2;
    g.push(ln([[xAt(mu), yBase + 4], [xAt(mu), 66]], { stroke: CK, sw: 1.2, dash: '4 3' }));
    g.push(txt(xAt(mu), 60, '평균 μ', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    for (const c of [1, 2, 3]) {
        const y = 76 + c * 18;
        g.push(ln([[xAt(mu - c * sd), y], [xAt(mu + c * sd), y]], { stroke: c === 2 ? C2 : CK, sw: c === 2 ? 2 : 1.2 }));
        g.push(ln([[xAt(mu - c * sd), y - 4], [xAt(mu - c * sd), y + 4]], { stroke: c === 2 ? C2 : CK, sw: 1.2 }));
        g.push(ln([[xAt(mu + c * sd), y - 4], [xAt(mu + c * sd), y + 4]], { stroke: c === 2 ? C2 : CK, sw: 1.2 }));
        g.push(txt(xAt(mu + c * sd) + 8, y + 4, `${c}σ`, { cls: c === 2 ? 'ink bold' : 'ink2', size: 'sm' }));
    }

    const real = c => 2 * binTailGe(n, mu + c * sd, p);
    g.push(ln([[20, 240], [W - 20, 240]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 260, '체비쇼프는 Pr[|R − μ| ≥ cσ] ≤ 1/c² 만 말한다. 실제 꼬리는 그보다 훨씬 얇다.', { cls: 'ink bold', size: 'sm' }));
    const cols = [[24, 'c'], [90, '체비쇼프 1/c²'], [230, '실제 확률'], [350, '몇 배 느슨한가']];
    for (const [x, s] of cols) g.push(txt(x, 282, s, { cls: 'ink2', size: 'sm' }));
    let row = 300;
    for (const c of [1, 2, 3]) {
        g.push(txt(24, row, String(c), { cls: 'ink', size: 'sm' }));
        g.push(txt(90, row, fmt(1 / c ** 2, 3), { cls: 'ink', size: 'sm' }));
        g.push(txt(230, row, fmt(real(c), 3), { cls: 'ink', size: 'sm' }));
        g.push(ctxt(350, row, `약 ${Math.round(1 / c ** 2 / real(c))}배`, C2, { bold: true }));
        row += 16;
    }
    g.push(txt(470, 300, 'c = 1 에서는 아무것도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(470, 316, '보장하지 못한다(1/1² = 1)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(470, 332, '그래도 어떤 분포에나 통한다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-d-sigma-width',
        svg: svg({
            width: W, height: H,
            title: '표준편차와 체비쇼프 한계의 느슨함',
            desc: '동전 100번의 앞면 수 분포에 1σ 2σ 3σ 구간을 표시하고 체비쇼프 한계와 실제 꼬리확률을 견주는 그림',
            body: g.join(''),
        }),
    };
})());

/* 같은 평균, 전혀 다른 분산 — 두 도박 */
add((() => {
    const W = 660;
    const H = 300;
    const g = [];
    g.push(txt(20, 24, '기댓값이 같은 두 도박 — 평균은 둘 다 1달러다', { cls: 'ink bold', size: 'sm' }));

    /* 게임 가: +2 (2/3), −1 (1/3).  게임 나: +1002 (2/3), −2001 (1/3). */
    const axis = (y, xmin, xmax, ticks, label) => {
        const bx = 120;
        const bw = 460;
        const X = v => bx + ((v - xmin) / (xmax - xmin)) * bw;
        const out = [ln([[bx, y], [bx + bw, y]], { stroke: CK, sw: 1.2 })];
        for (const t of ticks) {
            out.push(ln([[X(t), y - 4], [X(t), y + 4]], { stroke: CK, sw: 1 }));
            out.push(txt(X(t), y + 19, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        out.push(txt(20, y + 5, label, { cls: 'ink bold', size: 'sm' }));
        return { body: out.join(''), X };
    };

    const a = axis(96, -2400, 1200, [-2000, -1000, 0, 1000], '게임 가');
    g.push(a.body);
    g.push(pdot(a.X(2), 96, C1, 5));
    g.push(pdot(a.X(-1), 96, C1, 5));
    g.push(ring(a.X(1), 96, 6, C2, 2));
    g.push(txt(a.X(1) + 12, 80, '평균 1', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(a.X(1) - 16, 76, '두 결과 +2 와 −1 이 이 점에 붙어 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const b = axis(186, -2400, 1200, [-2000, -1000, 0, 1000], '게임 나');
    g.push(b.body);
    g.push(pdot(b.X(1002), 186, C1, 5));
    g.push(pdot(b.X(-2001), 186, C1, 5));
    g.push(ring(b.X(1), 186, 6, C2, 2));
    g.push(txt(b.X(1002), 166, '+1002 (확률 2/3)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(b.X(-2001), 166, '−2001 (확률 1/3)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(b.X(1) - 12, 166, '평균 1', { anchor: 'end', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 232], [W - 20, 232]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 252, '분산 Var 은 게임 가에서 2, 게임 나에서 2004002 이다. 표준편차로 바꾸면 √2 ≈ 1.41 달러와 약 1416 달러다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 272, '기댓값은 두 게임을 구별하지 못한다. 표준편차는 구별한다 — 그것이 이 장에서 분산을 도입하는 이유다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 292, '열 판을 하면 둘 다 평균 10달러를 딴다. 그런데 게임 나는 2만 달러를 잃는 일도 일어난다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-variance-two-games',
        svg: svg({
            width: W, height: H,
            title: '기댓값이 같고 분산이 다른 두 도박',
            desc: '같은 수직선 위에 두 도박의 결과를 놓아 기댓값은 같지만 흩어짐이 백만 배 다른 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 표본 크기 — 체비쇼프가 요구하는 n */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '표본 n 명을 뽑아 지지율을 추정한다 — 오차 0.04 를 벗어날 확률의 상한', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 42, '가로축은 표본 크기 n 이다', { cls: 'ink2', size: 'sm' }));

    const bound = nn => Math.min(0.5, 1 / (4 * nn * 0.04 ** 2));
    const f = frame({ xRange: [0, 8000], yRange: [0, 0.5], box: { x: 70, y: 74, w: 320, h: 176 } });
    g.push(f.axes({
        xLabel: 'n', yLabel: '상한',
        xTicks: [0, 2000, 3125, 5000, 8000], yTicks: [0, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5],
    }));
    g.push(f.curve(bound, { from: 320, to: 8000, cls: 's1', steps: 200 }));
    g.push(f.line([[0, 0.05], [8000, 0.05]], { cls: 's2', dash: '5 4' }));
    g.push(f.guide([3125, 0], [3125, 0.05]));
    g.push(f.dot([3125, 0.05], { cls: 'f2', r: 4.5 }));
    g.push(f.label([3125, 0.05], 'n = 3125 에서 1/20', { dx: 10, dy: -8, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([8000, 0.05], '0.05', { dx: -4, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }));

    const x0 = 424;
    g.push(txt(x0, 74, '상한을 세우는 순서', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 94, '① Var[S~n] = npq ≤ n/4', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 112, '② Var[S~n/n] ≤ 1/4n', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 130, '③ 체비쇼프를 쓰면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 12, 148, '1/(4n · 0.04²) = 156.25/n', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 176, '이 값이 1/20 이하가 되려면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 12, 194, 'n ≥ 3125', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 220, '유권자가 만 명이든 10억 명이든', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 236, '같은 수다 — 모집단 크기가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 252, '식에 들어오지 않는다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 272], [W - 20, 272]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 292, '곡선이 1/n 로 떨어진다. 오차를 절반으로 줄이려면 x² 이 4분의 1이 되므로 표본은 네 배가 필요하다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 312, '체르노프 한계를 쓰면 같은 신뢰도에 필요한 n 이 대략 4분의 1로 줄어든다 — 이항분포임을 더 쓰기 때문이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-sample-size',
        svg: svg({
            width: W, height: H,
            title: '표본 크기와 신뢰도',
            desc: '체비쇼프 한계로 얻은 오차 초과 확률의 상한이 표본 크기에 따라 1/n 로 떨어지고 3125 에서 1/20 이 되는 그림',
            body: g.join(''),
        }),
    };
})());

/* 체르노프 지수 β(c) */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '체르노프 한계의 지수 β(c) = c ln c − c + 1', { cls: 'ink bold', size: 'sm' }));

    const f = frame({ xRange: [1, 3], yRange: [0, 1.4], box: { x: 66, y: 58, w: 276, h: 196 } });
    g.push(f.axes({
        xLabel: 'c', yLabel: 'β(c)',
        xTicks: [1, 1.5, 2, 2.5, 3], yTicks: [0, 0.2, 0.4, 0.6, 0.8, 1, 1.2, 1.4],
    }));
    g.push(f.curve(beta, { from: 1, to: 3, cls: 's1', steps: 200 }));
    for (const c of [1.2, 1.3, 2]) {
        g.push(f.dot([c, beta(c)], { cls: 'f2', r: 4 }));
    }
    g.push(f.guide([1.25, 0.04], [1.4, 0.86]));
    g.push(f.label([1.42, 1.14], 'β(1.2) = 0.0188', { cls: 'ink', size: 'sm' }));
    g.push(f.label([1.42, 0.98], 'β(1.3) = 0.0411', { cls: 'ink', size: 'sm' }));
    g.push(f.label([1.42, 0.82], '거의 0 이지만 0 은 아니다', { cls: 'ink2', size: 'sm' }));
    g.push(f.label([2, beta(2)], 'β(2) = 0.386', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));

    const x0 = 380;
    g.push(txt(x0, 74, 'Pr[T ≥ c·Ex[T]] ≤ e^(−β(c)·Ex[T])', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 98, 'β 는 지수 자리에 있고 Ex[T] 가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 114, '곱해진다. 그래서 β 가 조금만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 130, '커져도 한계는 크게 무너진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 156, '동전 1000번, Ex[T] = 500', { cls: 'ink bold', size: 'sm' }));
    g.push(ctxt(x0, 176, `c = 1.2 → ${sci(Math.exp(-beta(1.2) * 500))}`, C2, { bold: true }));
    g.push(ctxt(x0, 194, `c = 1.3 → ${sci(Math.exp(-beta(1.3) * 500))}`, C2, { bold: true }));
    g.push(txt(x0, 218, 'β 가 2배가 되었을 뿐인데', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 234, '확률은 다섯 자리가 줄었다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 256, 'β(1) = 0 — c = 1 에서는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 272, '아무것도 말하지 못한다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 288], [W - 20, 288]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 308, 'β 는 c = 1 에서 0 이고 그 뒤로 자란다. 평균을 조금 넘는 것은 흔하고 크게 넘는 것은 지수적으로 드물다는 뜻이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-chernoff-beta',
        svg: svg({
            width: W, height: H,
            title: '체르노프 지수 베타 함수',
            desc: 'β(c) = c ln c − c + 1 곡선과 c = 1.2, 1.3, 2 에서의 값 그리고 그것이 만드는 확률 상한',
            body: g.join(''),
        }),
    };
})());

/* 무작위 부하 분산 — 서버를 몇 대 두어야 하나 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '게시글 24000개를 서버 m 대에 무작위로 뿌린다 — 어느 서버든 넘칠 확률의 상한', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 42, '막대가 길수록 안전하다. 세로축은 상한이 1 보다 몇 자리 아래인지를 센 것이다', { cls: 'ink2', size: 'sm' }));

    const bnd = m => m * Math.exp(-beta(m / 10) * (6000 / m));
    const digits = m => -Math.log10(bnd(m));
    const f = frame({ xRange: [10.4, 16.6], yRange: [0, 26], box: { x: 74, y: 78, w: 276, h: 178 } });
    g.push(f.axes({
        xLabel: 'm', yLabel: '자릿수',
        xTicks: [11, 12, 13, 14, 15, 16], yTicks: [0, 5, 10, 15, 20, 25],
    }));
    for (let m = 11; m <= 16; m += 1) {
        g.push(f.line([[m, 0], [m, digits(m)]], { cls: 's1' }));
        g.push(f.dot([m, digits(m)], { cls: 'f2', r: 4 }));
    }
    g.push(f.label([11, digits(11)], '0.78', { dx: 0, dy: -10, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([12, digits(12)], '0.001', { dx: 0, dy: -10, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([13.1, digits(13)], '8 × 10⁻⁸', { dx: 0, dy: -10, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([16, digits(16)], '10⁻²⁴ 아래', { dx: -4, dy: -10, anchor: 'end', cls: 'ink2', size: 'sm' }));

    const x0 = 380;
    g.push(txt(x0, 76, '왜 10대로는 안 되는가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 96, '일이 24000 × 1/4 = 6000초다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 112, '10대면 한 대에 정확히 600초 —', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 128, '용량과 같으므로 절반쯤 넘친다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 154, 'm 대일 때 한 대의 평균은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 10, 172, '6000/m 초이고 c = m/10 이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 196, '합집합 한계로 m 배 하면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 10, 214, '어느 한 대라도 넘칠 확률', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 240, '서버 한 대를 더 놓는 것이', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 256, '확률을 세 자리씩 깎는다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 274], [W - 20, 274]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 294, '11대는 곧 넘치고, 12대는 며칠에 한 번 넘치고, 13대는 수백 년에 한 번 넘친다. 여유 30%가 이만큼을 산다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 314, '작업의 길이를 하나도 모른 채 무작위로 뿌리기만 했는데도 이런 보장이 나온다는 것이 체르노프 한계의 쓸모다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-load-balance',
        svg: svg({
            width: W, height: H,
            title: '무작위 부하 분산에 필요한 서버 수',
            desc: '서버 수에 따라 어느 서버든 과부하가 될 확률의 체르노프 상한이 지수적으로 줄어드는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 23장 — 무작위 행보
 * ================================================================== */

/* 도박꾼의 자본이 걷는 길 */
add((() => {
    const W = 660;
    const H = 300;
    const g = [];
    g.push(txt(20, 24, '자본을 시간에 대해 그리면 0 과 T 사이를 걷는 행보가 된다', { cls: 'ink bold', size: 'sm' }));

    const T = 14;
    const n = 8;
    /* 공정한 판을 손으로 골라 파산으로 끝나는 한 경로를 만들었다. */
    const moves = [1, -1, -1, 1, 1, 1, -1, -1, 1, -1, -1, -1, 1, 1, -1, -1, -1, 1, -1, -1, 1, -1, -1, -1, 1, -1, -1, -1];
    const pts = [[0, n]];
    let cur = n;
    for (let i = 0; i < moves.length; i += 1) {
        cur += moves[i];
        pts.push([i + 1, cur]);
        if (cur === 0 || cur === T) break;
    }

    const f = frame({ xRange: [0, 30], yRange: [0, T + 1], box: { x: 66, y: 56, w: 400, h: 176 } });
    g.push(f.axes({ xLabel: '판 수', yLabel: '자본', xTicks: [0, 5, 10, 15, 20, 25, 30], yTicks: [0, 4, 8, 14] }));
    g.push(f.line([[0, T], [30, T]], { cls: 's2', dash: '5 4' }));
    g.push(f.label([30, T], '목표 T', { dx: -4, dy: -8, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([12, 0], '파산 0', { dx: 0, dy: -8, cls: 'ink bold', size: 'sm' }));
    g.push(f.line(pts, { cls: 's1' }));
    g.push(f.dot([0, n], { cls: 'f1', r: 4.5 }));
    g.push(f.label([0, n], '초기 자본 n', { dx: 6, dy: -8, cls: 'ink', size: 'sm' }));
    const last = pts[pts.length - 1];
    g.push(f.dot(last, { cls: 'f2', r: 5 }));
    g.push(f.label(last, last[1] === 0 ? '파산했다' : '목표 도달', { dx: 4, dy: -10, cls: 'ink bold', size: 'sm' }));

    const x0 = 490;
    g.push(txt(x0, 74, '한 판마다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 94, '확률 p 로 +1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 110, '확률 q = 1 − p 로 −1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 136, '두 경계 가운데', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 152, '어느 쪽에 먼저', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 168, '닿는지가 문제다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 196, '목표 이익은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 212, 'm = T − n', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 250], [W - 20, 250]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 270, '한 걸음이 ±1 이므로 자본은 정수 위를 걷는다. 8장의 상태 기계로 보면 상태가 자본이고 0 과 T 가 종료 상태다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 290, '무작위 행보의 물음은 늘 둘이다 — 어느 쪽 경계에 닿는가, 그리고 닿기까지 몇 걸음인가.', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-d-gambler-walk',
        svg: svg({
            width: W, height: H,
            title: '도박꾼의 자본이 걷는 무작위 행보',
            desc: '자본을 시간에 대해 그려 0 과 목표 T 사이를 오르내리다 한쪽 경계에 닿는 한 가지 경로를 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 공정한 게임 — 초기 자본에 따른 승률 n/T */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '공정한 게임(p = 1/2)에서 목표가 T = 600 일 때 초기 자본에 따른 승률', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 42, '가로축은 초기 자본 n 이다', { cls: 'ink2', size: 'sm' }));

    const f = frame({ xRange: [0, 600], yRange: [0, 1], box: { x: 70, y: 74, w: 296, h: 176 } });
    g.push(f.axes({
        xLabel: 'n', yLabel: 'Pr[목표 도달]',
        xTicks: [0, 100, 300, 500, 600], yTicks: [0, 0.25, 0.5, 0.75, 1],
    }));
    g.push(f.curve(x => x / 600, { from: 0, to: 600, cls: 's1' }));
    for (const nn of [100, 300, 500]) {
        g.push(f.dot([nn, nn / 600], { cls: 'f2', r: 4 }));
        g.push(f.guide([nn, 0], [nn, nn / 600]));
    }
    g.push(f.label([100, 100 / 600], '1/6', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }));
    g.push(f.label([300, 0.5], '1/2', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }));
    g.push(f.label([500, 500 / 600], '5/6', { dx: -6, dy: -8, anchor: 'end', cls: 'ink bold', size: 'sm' }));

    const x0 = 414;
    g.push(txt(x0, 76, '두 사람의 게임으로 보기', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 96, '갑이 500, 을이 100 을 갖고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 112, '한 사람이 빌 때까지 한다면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 128, '갑이 이길 확률이 5/6 이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 154, '그런데 이겨서 얻는 것은 100,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 170, '져서 잃는 것은 500 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 190, '(5/6)·100 − (1/6)·500 = 0', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 214, '공정한 판을 아무리 이어도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 230, '평균 이익은 0 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 254, '자본이 크면 자주 이기지만', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 270, '질 때 크게 진다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 286], [W - 20, 286]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 306, '직선이라는 것이 요점이다. 승률은 초기 자본에 정확히 비례하고, 목표까지의 거리에 반비례한다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-ruin-fair',
        svg: svg({
            width: W, height: H,
            title: '공정한 게임에서 자본에 따른 파산 확률',
            desc: '목표가 600 일 때 초기 자본 n 에 대한 승률이 정확히 n/600 인 직선임을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 편향된 게임 — 목표 이익에 따라 지수적으로 무너지는 승률 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '한 판의 승률이 18/38 인 게임 — 목표 이익 m 에 따른 승률의 상한 (1/r)^m', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 42, 'r = q/p = 20/18 = 10/9 이므로 상한은 (9/10)^m 이다. 초기 자본이 얼마든 이 값을 넘지 못한다', { cls: 'ink2', size: 'sm' }));

    const f = frame({ xRange: [0, 220], yRange: [-10, 0], box: { x: 70, y: 84, w: 296, h: 172 } });
    g.push(f.axes({
        xLabel: 'm', yLabel: 'log₁₀ 상한',
        xTicks: [0, 50, 100, 150, 200], yTicks: [0, -2, -4, -6, -8, -10],
    }));
    g.push(f.curve(m => m * Math.log10(0.9), { from: 0, to: 220, cls: 's1' }));
    for (const m of [100, 200]) {
        g.push(f.dot([m, m * Math.log10(0.9)], { cls: 'f2', r: 4 }));
        g.push(f.guide([m, -10], [m, m * Math.log10(0.9)]));
    }
    g.push(f.label([100, 100 * Math.log10(0.9)], '1/37648', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([200, 200 * Math.log10(0.9)], '1/14억', { dx: -6, dy: -8, anchor: 'end', cls: 'ink bold', size: 'sm' }));

    const x0 = 414;
    g.push(txt(x0, 82, '공정한 게임과 견주기', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 102, '자본 500, 목표 600 이면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 120, '공정한 게임 → 5/6', { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(x0, 138, '이 게임 → 1/37648 미만', C2, { bold: true }));
    g.push(txt(x0, 164, '자본을 50억으로 늘려도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 180, '같은 상한이 유효하다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 206, '상한이 초기 자본에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 222, '들어 있지 않기 때문이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 248, '목표를 두 배로 하면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 264, '승률은 제곱이 된다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 282], [W - 20, 282]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 302, '로그 축에서 직선이라는 것은 승률이 목표 이익에 대해 지수적으로 떨어진다는 뜻이다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 322, '한 판의 불리함은 2.6%p 뿐인데 100달러를 따려는 계획은 3만 번에 한 번도 성공하지 못한다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-ruin-biased',
        svg: svg({
            width: W, height: H,
            title: '편향된 게임에서 목표 이익에 따른 승률 상한',
            desc: '한 판의 승률이 18/38 일 때 목표 이익 m 에 대한 승률 상한이 (9/10)의 m 제곱으로 지수적으로 줄어드는 그림',
            body: g.join(''),
        }),
    };
})());

/* 표류가 진동을 압도한다 */
add((() => {
    const W = 660;
    const H = 310;
    const g = [];
    g.push(txt(20, 24, '왜 큰 자본으로도 이기지 못하는가 — 표류와 진동', { cls: 'ink bold', size: 'sm' }));

    const f = frame({ xRange: [0, 100], yRange: [-60, 40], box: { x: 66, y: 56, w: 380, h: 180 } });
    g.push(f.axes({ xLabel: '판 수 k', yLabel: '자본의 변화', xTicks: [0, 25, 50, 75, 100], yTicks: [-60, -40, -20, 0, 20, 40] }));

    /* 표류: 판마다 평균 (2p−1) 달러. p = 18/38 이면 −1/19 달러. 눈에 보이게 기울기를 −0.5 로 둔다. */
    const drift = k => -0.5 * k;
    g.push(f.curve(drift, { from: 0, to: 100, cls: 's2', dash: '6 4' }));
    g.push(f.label([72, drift(72)], '평균은 이 직선을 따라 내려간다', { dx: -8, dy: 18, anchor: 'end', cls: 'ink2', size: 'sm' }));

    /* 진동을 얹은 경로. 재현 가능한 의사난수(선형 합동)로 만든다. */
    let s = 2024;
    const rnd = () => { s = (s * 1103515245 + 12345) & 0x7fffffff; return s / 0x7fffffff; };
    const path = [[0, 0]];
    let v = 0;
    /* 한 걸음의 평균이 −0.5 가 되도록 잡아 위의 표류 직선과 정확히 맞춘다. */
    for (let k = 1; k <= 100; k += 1) {
        v += (rnd() < 0.4 ? 1 : -1) * 2.5;
        path.push([k, v]);
    }
    g.push(f.line(path, { cls: 's1' }));

    g.push(f.line([[0, 20], [100, 20]], { cls: 's3', dash: '4 3' }));
    g.push(f.label([4, 20], '여기 닿으면 이긴다 (목표 이익 m)', { dy: -8, cls: 'ink bold', size: 'sm' }));

    let best = 0;
    let bestK = 0;
    for (const [k, y] of path) if (y > best) { best = y; bestK = k; }
    g.push(f.dot([bestK, best], { cls: 'f1', r: 4.5 }));
    g.push(f.label([bestK, best], '가장 운 좋았던 순간 — 목표에 닿지 못했다', { dx: 14, dy: 6, cls: 'ink', size: 'sm' }));

    g.push(ln([[20, 254], [W - 20, 254]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 274, '진동의 크기는 판 수의 제곱근으로 자라고(표준편차가 √(kpq) 다) 표류는 판 수에 비례해 자란다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 294, '오래 하면 표류가 반드시 이긴다. 그래서 초반에 운이 오지 않으면 필요한 진동이 갈수록 커져 사실상 불가능해진다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-drift-swing',
        svg: svg({
            width: W, height: H,
            title: '표류와 진동',
            desc: '편향된 무작위 행보에서 평균이 직선으로 내려가는 표류가 제곱근으로 자라는 진동을 압도하는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 파스칼의 칩 가치 */
add((() => {
    const W = 680;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '파스칼의 발상 — 칩의 가치를 r = q/p 의 거듭제곱으로 매기면 어떤 p 에서도 판이 공정해진다', { cls: 'ink bold', size: 'sm' }));

    const bw = 96;
    const ch = 26;
    const baseY = 232;
    const drawStack = (x, labels, title, col) => {
        const out = [txt(x + bw / 2, 62, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' })];
        labels.forEach((lb, i) => {
            const y = baseY - (i + 1) * ch;
            out.push(box(x, y, bw, ch - 4, { fill: col, op: 0.14, stroke: col, sw: 1.2, rx: 4 }));
            out.push(txt(x + bw / 2, y + ch - 12, lb, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
        out.push(ln([[x - 8, baseY], [x + bw + 8, baseY]], { stroke: CK, sw: 1.4 }));
        return out.join('');
    };

    g.push(drawStack(56, ['r¹', 'r²', '⋮', 'rⁿ'], '갑의 칩 n 개', C1));
    g.push(drawStack(300, ['rⁿ⁺ᵐ', '⋮', 'rⁿ⁺²', 'rⁿ⁺¹'], '을의 칩 m 개', C2));
    g.push(txt(56, 250, '아래가 바닥', { cls: 'ink2', size: 'sm' }));
    g.push(txt(300, 250, '아래가 바닥', { cls: 'ink2', size: 'sm' }));

    g.push(arw(292, 126, 160, 126, { cls: 's1', width: 2 }));
    g.push(txt(226, 118, '확률 p', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(160, 164, 292, 164, { cls: 's2', width: 2 }));
    g.push(txt(226, 182, '확률 q', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const x0 = 420;
    g.push(box(x0 - 12, 66, 250, 96, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(x0, 88, '한 판의 가치 기댓값', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 110, 'rⁿ⁺¹·p − rⁿ·q', { cls: 'ink', size: 'sm' }));
    g.push(txt(x0, 130, '= rⁿ(q/p)·p − rⁿ·q', { cls: 'ink', size: 'sm' }));
    g.push(txt(x0, 150, '= 0', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(x0, 182, '확률 p 로 이기면 을의 맨 위', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 198, '칩을 가져오고, 확률 q 로 지면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 214, '자기 맨 위 칩을 내준다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 234, '어느 쪽이든 가치의 줄', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 250, 'r, r², ⋯, rⁿ⁺ᵐ 은 그대로다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 264], [W - 20, 264]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 284, '모든 판의 가치 기댓값이 0 이므로 게임이 끝났을 때 갑이 가진 가치의 기댓값도 0 이다. 이 한 줄이 승률을 준다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 304, 'r = 1 (공정한 게임)이면 칩의 가치가 모두 1 이 되어 같은 식이 n/T 를 준다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-pascal-chips',
        svg: svg({
            width: W, height: H,
            title: '파스칼의 칩 가치 배정',
            desc: '두 사람의 칩을 아래에서 위로 r 의 거듭제곱으로 값을 매기면 각 판의 가치 기댓값이 0 이 되는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 그래프 위의 무작위 행보와 정상분포 */
add((() => {
    const W = 680;
    const H = 340;
    const g = [];
    g.push(txt(20, 24, '방향 그래프 위의 무작위 행보 — 각 간선을 1/(나가는 차수) 확률로 따른다', { cls: 'ink bold', size: 'sm' }));

    const V = {
        a: [110, 110], b: [250, 78], c: [250, 170], d: [386, 122],
    };
    const nameOf = { a: 'a', b: 'b', c: 'c', d: 'd' };
    const edges = [
        ['a', 'b', '1/2'], ['a', 'c', '1/2'],
        ['b', 'd', '1'],
        ['c', 'd', '1'],
    ];
    for (const [u, v, lb] of edges) {
        const [x1, y1] = V[u];
        const [x2, y2] = V[v];
        const dx = x2 - x1;
        const dy = y2 - y1;
        const len = Math.hypot(dx, dy);
        const rr = 19;
        g.push(arw(x1 + (dx / len) * rr, y1 + (dy / len) * rr, x2 - (dx / len) * rr, y2 - (dy / len) * rr, { cls: 'ark', width: 1.6 }));
        g.push(txt((x1 + x2) / 2 + (dy / len) * 12, (y1 + y2) / 2 - (dx / len) * 12 + 4, lb, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    /* d → a 는 위 목록에 있으므로 곡선 대신 바깥으로 돌아가는 선을 하나 더 그린다. */
    g.push(ln([[386, 143], [386, 216], [110, 216], [110, 131]], { stroke: CK, sw: 1.6 }));
    g.push(arw(110, 200, 110, 133, { cls: 'ark', width: 1.6 }));
    g.push(txt(248, 232, 'd 에서 a 로 되돌아온다 (확률 1)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    for (const k of Object.keys(V)) {
        const [x, y] = V[k];
        g.push(`<circle cx="${x}" cy="${y}" r="19" fill="var(--s1)" fill-opacity="0.14" stroke="var(--s1)" stroke-width="1.5"/>`);
        g.push(txt(x, y + 5, nameOf[k], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }

    const x0 = 452;
    g.push(txt(x0, 74, '정상분포의 정의', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 94, 'Pr[x 에 있다] =', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 12, 110, 'Σ Pr[y 에 있다]/outdeg(y)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 12, 126, '(y → x 인 간선마다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 152, '이 그래프의 방정식', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 170, 'a = d', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 186, 'b = a/2,  c = a/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 202, 'd = b + c', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 218, 'a + b + c + d = 1', { cls: 'ink2', size: 'sm' }));
    g.push(box(x0 - 10, 232, 216, 44, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(x0, 252, '풀면 (1/3, 1/6, 1/6, 1/3)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 270, 'a 와 d 가 두 배 중요하다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 292], [W - 20, 292]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 312, '진입 차수만 세면 d 가 2 로 가장 크지만, 무작위 행보는 a 와 d 를 같게 본다 — a 를 지나야 b, c 에 갈 수 있기 때문이다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 332, '마지막 줄(합이 1)이 없으면 모두 0 도 답이 되므로 반드시 함께 세운다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-walk-stationary',
        svg: svg({
            width: W, height: H,
            title: '그래프 위의 무작위 행보와 정상분포',
            desc: '네 정점 방향 그래프에 간선 확률을 매기고 정상분포 방정식을 세워 답이 3분의 1과 6분의 1이 되는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 정상분포가 말을 듣지 않는 두 경우 */
add((() => {
    const W = 680;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '정상분포가 있어도 도달하지 못하거나, 정상분포가 무한히 많은 경우', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(24, 44, 300, 200, '왕복 그래프', '정상분포는 (1/2, 1/2) 하나뿐이다'));
    const nd = (x, y, s, col = C1) => `<circle cx="${x}" cy="${y}" r="18" fill="${col}" fill-opacity="0.14" stroke="${col}" stroke-width="1.5"/>`
        + txt(x, y + 5, s, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    g.push(nd(110, 130, 'x'));
    g.push(nd(240, 130, 'y'));
    g.push(arw(128, 122, 222, 122, { cls: 'ark', width: 1.6 }));
    g.push(arw(222, 140, 128, 140, { cls: 'ark', width: 1.6 }));
    g.push(txt(175, 112, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(175, 160, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(44, 190, 'x 에서 출발하면 확률이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(44, 208, '(1,0), (0,1), (1,0), ⋯ 로 튄다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(44, 228, '정상분포에 결코 가까워지지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(352, 44, 304, 200, '싱크가 둘인 그래프', '정상분포가 셀 수 없이 많다'));
    g.push(nd(400, 106, 'a', C3));
    g.push(nd(504, 106, 'b', C1));
    g.push(nd(504, 178, 'c', C1));
    g.push(arw(416, 112, 488, 106, { cls: 'ark', width: 1.6 }));
    g.push(arw(414, 120, 490, 172, { cls: 'ark', width: 1.6 }));
    g.push(txt(452, 96, '1/2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 160, '1/2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(`<path d="M520 92 A16 16 0 1 1 520 120" fill="none" stroke="${CK}" stroke-width="1.6" marker-end="url(#ark)"/>`);
    g.push(`<path d="M520 164 A16 16 0 1 1 520 192" fill="none" stroke="${CK}" stroke-width="1.6" marker-end="url(#ark)"/>`);
    g.push(txt(548, 106, '1 (제자리)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(548, 178, '1 (제자리)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(372, 214, '어떤 t 에 대해서도 (0, t, 1−t) 가 정상분포다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(372, 232, 'a 에는 아무도 돌아오지 않으므로 0 이 강제된다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 258], [W - 20, 258]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 278, '그래서 페이지랭크는 슈퍼 정점을 더해 그래프를 강하게 연결된 것으로 만든다. 강한 연결이면 정상분포가 유일하고', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 298, '어디서 출발해도 충분히 오래 걸으면 그 분포에 가까워진다. 위 두 그래프에는 그 성질이 없다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-stationary-odd',
        svg: svg({
            width: W, height: H,
            title: '정상분포로 수렴하지 않거나 정상분포가 여럿인 그래프',
            desc: '두 정점 왕복 그래프와 싱크가 둘인 그래프를 나란히 놓아 정상분포의 유일성과 수렴이 깨지는 경우를 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 24장 — 점화식
 * ================================================================== */

/* 병합정렬의 재귀 트리 */
add((() => {
    const W = 680;
    const H = 350;
    const g = [];
    g.push(txt(20, 24, '병합정렬의 재귀 트리 — 층마다 하는 일이 거의 같고 층 수가 log n 이다', { cls: 'ink bold', size: 'sm' }));

    const cx = 300;
    const top = 62;
    const rowH = 46;
    const levels = 4;
    const totalW = 460;
    for (let L = 0; L < levels; L += 1) {
        const cnt = 2 ** L;
        const bw = totalW / cnt - 6;
        const y = top + L * rowH;
        for (let i = 0; i < cnt; i += 1) {
            const x = cx - totalW / 2 + i * (totalW / cnt) + 3;
            g.push(box(x, y, bw, 22, { fill: C1, op: 0.16, stroke: C1, sw: 1.1, rx: 3 }));
            if (cnt <= 4) g.push(txt(x + bw / 2, y + 16, L === 0 ? 'n' : `n/${cnt}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        if (L > 0) {
            for (let i = 0; i < cnt; i += 1) {
                const xc = cx - totalW / 2 + i * (totalW / cnt) + (totalW / cnt) / 2;
                const px = cx - totalW / 2 + Math.floor(i / 2) * (totalW / (cnt / 2)) + (totalW / (cnt / 2)) / 2;
                g.push(ln([[px, y - rowH + 22], [xc, y]], { stroke: CG, sw: 1 }));
            }
        }
        const lab = L === levels - 1 ? '⋮  (아래로 계속)' : (L === 0 ? '1개 × n 크기' : `${cnt}개 × n/${cnt} 크기`);
        g.push(txt(cx + totalW / 2 + 16, y + 16, lab, { cls: 'ink2', size: 'sm' }));
        g.push(txt(cx - totalW / 2 - 16, y + 16, `층 ${L}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }

    g.push(box(cx - totalW / 2 - 76, top + levels * rowH + 6, totalW + 160, 46, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(cx, top + levels * rowH + 26, '층마다 병합에 드는 비교가 최대 n − (그 층의 조각 수) 이므로 층마다 n 보다 조금 작다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx, top + levels * rowH + 44, '층이 log₂ n 개이므로 전체는 n log₂ n 규모다 — 정확히는 n log₂ n − n + 1', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(ln([[20, 300], [W - 20, 300]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 320, '재귀 트리는 점화식을 눈으로 푸는 방법이다. 각 층의 일을 더하고 층 수를 곱하면 답의 규모가 나온다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 338, '아래로 갈수록 조각이 작아지고 개수가 늘어나 곱이 유지되는 것이 이 점화식의 성격이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-recursion-tree-merge',
        svg: svg({
            width: W, height: H,
            title: '병합정렬의 재귀 트리',
            desc: '층마다 조각의 개수가 두 배가 되고 크기가 절반이 되어 층마다의 일이 n 으로 유지되는 것을 보이는 재귀 트리',
            body: g.join(''),
        }),
    };
})());

/* 마스터 정리의 세 경우 */
add((() => {
    const W = 680;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, 'T(n) = a·T(n/b) + g(n) — 어느 층이 전체를 지배하는가로 세 경우가 갈린다', { cls: 'ink bold', size: 'sm' }));

    const drawCase = (px, title, sub, widths, dom, note) => {
        const out = [panel(px, 46, 208, 208, title, sub)];
        const base = px + 104;
        widths.forEach((w, L) => {
            const y = 92 + L * 26;
            const ww = Math.max(6, w * 150);
            const isDom = dom === L || (dom === 'all');
            out.push(box(base - ww / 2, y, ww, 18, {
                fill: isDom ? C2 : C1, op: isDom ? 0.55 : 0.2,
                stroke: isDom ? C2 : C1, sw: 1,
            }));
        });
        out.push(txt(base, 226, note, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        return out.join('');
    };

    g.push(drawCase(24, '1. 잎이 지배', 'g(n) 이 작다', [0.3, 0.42, 0.6, 0.84, 1], 4, 'Θ(n^(log~b a))'));
    g.push(drawCase(240, '2. 모든 층이 같다', 'g(n) = Θ(n^(log~b a))', [1, 1, 1, 1, 1], 'all', 'Θ(n^(log~b a) · log n)'));
    g.push(drawCase(456, '3. 뿌리가 지배', 'g(n) 이 크다', [1, 0.7, 0.48, 0.32, 0.2], 0, 'Θ(g(n))'));

    g.push(ln([[20, 264], [W - 20, 264]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 284, '막대의 길이가 그 층에서 하는 일의 양이다. 등비수열이므로 첫 항이 크면 첫 항이, 마지막 항이 크면 마지막 항이,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 302, '모두 같으면 층 수만큼이 답이 된다. 이것이 마스터 정리 세 경우의 전부다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 322, '잎의 개수는 a^(log~b n) = n^(log~b a) 이므로 2번 경우의 기준선이 그 값이 된다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-master-cases',
        svg: svg({
            width: W, height: H,
            title: '마스터 정리의 세 경우',
            desc: '재귀 트리에서 층마다의 일을 막대로 그려 잎이 지배하는 경우와 모든 층이 같은 경우와 뿌리가 지배하는 경우를 나란히 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 특성근이 해의 모양을 정한다 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '피보나치 점화식의 특성근 두 개가 만드는 두 항', { cls: 'ink bold', size: 'sm' }));

    const phi = (1 + Math.sqrt(5)) / 2;
    const psi = (1 - Math.sqrt(5)) / 2;
    const f = frame({ xRange: [0, 10], yRange: [-3, 4], box: { x: 70, y: 58, w: 300, h: 196 } });
    g.push(f.axes({
        xLabel: 'n', yLabel: 'log₁₀ |항|',
        xTicks: [0, 2, 4, 6, 8, 10], yTicks: [-3, -2, -1, 0, 1, 2, 3, 4],
    }));
    g.push(f.curve(x => Math.log10(phi ** x / Math.sqrt(5)), { from: 0, to: 10, cls: 's1' }));
    g.push(f.curve(x => Math.log10(Math.abs(psi) ** x / Math.sqrt(5)), { from: 0, to: 10, cls: 's2', dash: '5 4' }));
    g.push(f.label([9.4, Math.log10(phi ** 9.4 / Math.sqrt(5))], 'φⁿ/√5  (φ = 1.618)', { dx: -6, dy: -8, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([8.2, Math.log10(Math.abs(psi) ** 8.2 / Math.sqrt(5))], '(0.618)ⁿ/√5 — 금방 사라진다', { dx: -8, dy: -12, anchor: 'end', cls: 'ink2', size: 'sm' }));

    const x0 = 396;
    g.push(txt(x0, 74, '특성방정식', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 94, 'f(n) = xⁿ 을 넣으면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 10, 112, 'x² = x + 1', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 132, '뿌리는 (1 ± √5)/2 다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 158, '큰 뿌리가 1 보다 크므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 174, '그 항이 지수적으로 자라고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 190, '작은 뿌리는 절댓값이 1 미만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 206, '이라 금방 사라진다', { cls: 'ink2', size: 'sm' }));
    g.push(box(x0 - 10, 220, 246, 46, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(x0, 240, 'f(n) = φⁿ⁺¹/√5 + o(1)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, 258, '무리수가 정수를 만든다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 282], [W - 20, 282]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 302, '세로축이 로그이므로 직선의 기울기가 곧 밑의 로그다. 두 직선의 기울기가 반대 부호인 것이 두 항의 운명을 갈랐다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 322, '선형 점화식의 해가 지수적인 까닭이 여기 있다 — 특성근의 거듭제곱을 더한 것이 답이다.', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-d-char-roots',
        svg: svg({
            width: W, height: H,
            title: '특성근이 만드는 두 항',
            desc: '피보나치 점화식의 두 특성근이 만드는 항의 크기를 로그 축에 그려 큰 뿌리만 남는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 하노이와 병합정렬 — 비슷한 점화식, 전혀 다른 해 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '비슷하게 생긴 두 점화식의 해 — 세로축은 상용로그다', { cls: 'ink bold', size: 'sm' }));

    const f = frame({ xRange: [1, 64], yRange: [0, 20], box: { x: 70, y: 58, w: 300, h: 196 } });
    g.push(f.axes({
        xLabel: 'n', yLabel: 'log₁₀ (걸음 수)',
        xTicks: [1, 16, 32, 48, 64], yTicks: [0, 5, 10, 15, 20],
    }));
    g.push(f.curve(x => Math.log10(2 ** x - 1), { from: 1, to: 64, cls: 's2' }));
    g.push(f.curve(x => Math.log10(Math.max(1, x * Math.log2(x) - x + 1)), { from: 2, to: 64, cls: 's1' }));
    g.push(f.curve(x => Math.log10(2 * 2 ** x - x - 2), { from: 1, to: 64, cls: 's2', dash: '5 4' }));

    g.push(f.label([52, Math.log10(2 ** 52)], '하노이 T~n = 2T~{n−1} + 1', { dx: -6, dy: 22, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([62, Math.log10(62 * Math.log2(62))], '병합정렬 T~n = 2T~{n/2} + n − 1', { dx: -6, dy: -8, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.dot([64, Math.log10(2 ** 64 - 1)], { cls: 'f2', r: 4 }));
    g.push(f.dot([64, Math.log10(64 * 6 - 64 + 1)], { cls: 'f1', r: 4 }));

    const x0 = 396;
    g.push(txt(x0, 74, 'n = 64 에서', { cls: 'ink bold', size: 'sm' }));
    g.push(ctxt(x0, 96, `하노이  ${sci(2 ** 64 - 1)} 걸음`, C2, { bold: true }));
    g.push(ctxt(x0, 114, '병합정렬  321 번 비교', C1, { bold: true }));
    g.push(txt(x0, 142, '점화식의 차이는 두 곳이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 162, '부분문제의 크기', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0 + 10, 178, 'n − 1 (거의 그대로)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 10, 194, 'n/2 (절반)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, 216, '한 번의 추가 작업', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0 + 10, 232, '1 (아주 적다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 10, 248, 'n − 1 (많다)', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 274], [W - 20, 274]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 294, '병합정렬이 추가 작업은 훨씬 많은데도 압도적으로 빠르다. 부분문제를 얼마나 줄이는지가 전부를 정한다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 314, '점선은 원판 옮기기에 크기만큼 시간이 드는 하노이 변형(2·2ⁿ − n − 2)이다. 추가 작업을 1 에서 n 으로 늘려도 두 배뿐이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-hanoi-vs-merge',
        svg: svg({
            width: W, height: H,
            title: '하노이 탑과 병합정렬의 해 견주기',
            desc: '2의 n 제곱과 n log n 을 상용로그 축에 그려 부분문제 크기의 차이가 해의 규모를 완전히 갈라 놓는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 점화식의 감각 — 크기와 개수의 격자 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '점화식의 감각 — 부분문제를 어떻게 줄이는가가 해의 규모를 정한다', { cls: 'ink bold', size: 'sm' }));

    const cw = 232;
    const chh = 82;
    const gx = 168;
    const gy = 74;
    const cells = [
        ['n − 1 로 줄인다 (가산적)', '개수 2', '2ⁿ 규모', '하노이 탑, 피보나치'],
        ['n − 1 로 줄인다 (가산적)', '개수 3', '3ⁿ 규모', '뿌리가 2에서 3으로'],
        ['n/2 로 줄인다 (배수적)', '개수 2', 'n log n 규모', '병합정렬'],
        ['n/2 로 줄인다 (배수적)', '개수 3', 'n^(log₂3) 규모', '카라추바 곱셈'],
    ];
    for (let i = 0; i < 4; i += 1) {
        const col = i % 2;
        const row = Math.floor(i / 2);
        const x = gx + col * (cw + 12);
        const y = gy + row * (chh + 14);
        const strong = i === 0 || i === 2;
        g.push(box(x, y, cw, chh, { fill: strong ? C1 : C3, op: 0.1, stroke: strong ? C1 : C3, sw: 1.3, rx: 5 }));
        g.push(txt(x + 12, y + 22, cells[i][1], { cls: 'ink2', size: 'sm' }));
        g.push(txt(x + 12, y + 46, cells[i][2], { cls: 'ink bold' }));
        g.push(txt(x + 12, y + 66, cells[i][3], { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(158, gy + 42, '가산적으로', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(158, gy + 60, '줄인다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(158, gy + chh + 14 + 42, '배수적으로', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(158, gy + chh + 14 + 60, '줄인다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(gx + cw / 2, gy - 10, '부분문제 2개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(gx + cw + 12 + cw / 2, gy - 10, '부분문제 3개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 258], [W - 20, 258]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 278, '위 줄은 지수, 아래 줄은 다항식이다. 가로로 옮기면(부분문제를 하나 더 만들면) 지수의 밑이나 지수 자리가 바뀐다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 296, '반면 한 번의 추가 작업을 1 에서 n 으로 늘리는 것은 상수배만 바꾼다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 314, '그러므로 알고리즘을 빠르게 하려면 층마다의 일을 줄이기보다 부분문제를 작게 만들어야 한다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-d-subproblem-grid',
        svg: svg({
            width: W, height: H,
            title: '부분문제의 크기와 개수가 정하는 해의 규모',
            desc: '부분문제를 가산적으로 줄이는지 배수적으로 줄이는지와 부분문제 개수로 나눈 네 칸에 해의 규모를 적은 격자',
            body: g.join(''),
        }),
    };
})());

export default figures;
