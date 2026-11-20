/**
 * 통계학 18장(시계열 기초) 그림. 이름은 전부 `sta-ts-` 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `X~t`·`r~k` 표기, 나머지는 유니코드(φ ε σ √ ± ≈ −)로 적는다.
 * 라벨에 물결표를 그냥 쓰면 아래첨자로 먹히고, 따옴표는 ‘ ’ 만 쓴다.
 * lib 의 px() 에 cls:'ark' 를 주면 선이 사라지므로 화살표는 아래 arw() 를 쓴다.
 *
 * 난수 계열은 씨앗을 고정한 선형합동생성기로 만든다. 그림 안의 숫자
 * (자기상관, 상관계수, 띠 밖 막대 수, t 값)는 전부 그 자리에서 계산해 넣는다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const PI = Math.PI;

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' };

function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.6, dash } = {}) {
    const mk = { s1: 'ar1', s2: 'ar2', s3: 'ar3' }[cls] ?? 'ark';
    return `<path fill="none" stroke="${COL[cls] ?? 'var(--ink2)'}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = 'var(--s1)', r = 3) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

const neg = v => String(v).replace('-', '−');

function xaxis(fr, [x0, x1], ticks = [], { fmt = neg, y0 = 0, arrow = true } = {}) {
    const y = fr.Y(y0);
    const out = [arrow
        ? `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`
        : `<path class="ax" d="M${fr.X(x0)} ${y} H${fr.X(x1)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 4]], { sw: 1 }));
        out.push(txt(fr.X(t), y + 16, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

function yaxis(fr, [y0, y1], x0, ticks = [], { fmt = neg } = {}) {
    const x = fr.X(x0);
    const out = [`<path class="ax" d="M${x} ${fr.Y(y0)} V${fr.Y(y1)}"/>`];
    for (const t of ticks) {
        out.push(ln([[x - 4, fr.Y(t)], [x, fr.Y(t)]], { sw: 1 }));
        out.push(txt(x - 7, fr.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 화소 좌표 꺾은선을 데이터 계열에서 만든다. */
const path = (fr, xs, ys, o) => ln(xs.map((x, i) => [fr.X(x), fr.Y(ys[i])]), o);

/** 씨앗을 고정한 난수. */
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}
function gauss(rand) {
    const u = Math.max(rand(), 1e-9), v = rand();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * PI * v);
}
const gaussSeq = (seed, n) => { const r = rng(seed); return Array.from({ length: n }, () => gauss(r)); };

const mean = a => a.reduce((s, v) => s + v, 0) / a.length;

/** 표본 자기상관 r_k (분모 n 의 자기공분산). */
function acf(x, K) {
    const n = x.length, m = mean(x);
    const c0 = x.reduce((s, v) => s + (v - m) ** 2, 0) / n;
    const out = [];
    for (let k = 1; k <= K; k += 1) {
        let c = 0;
        for (let t = 0; t + k < n; t += 1) c += (x[t] - m) * (x[t + k] - m);
        out.push(c / n / c0);
    }
    return out;
}

function ar1(eps, phi, x0 = 0) {
    const out = [];
    let prev = x0;
    for (const e of eps) { prev = phi * prev + e; out.push(prev); }
    return out;
}

/* ================================================================== *
 * 1. 분해 — 원계열, 추세, 계절, 나머지
 * ================================================================== */
add((() => {
    const W = 760, H = 610;
    const n = 56;
    const week = [12, 8, 6, 4, -2, -30, 2]; // 합 0
    const e = gaussSeq(18001, n).map(v => 6 * v);
    const t = Array.from({ length: n }, (_, i) => i + 1);
    const x = t.map((tt, i) => 200 + 1.2 * tt + week[i % 7] + e[i]);
    // 7항 중심 이동평균
    const trend = t.map((_, i) => (i >= 3 && i <= n - 4) ? mean(x.slice(i - 3, i + 4)) : null);
    // 계절: (원계열 − 추세)를 요일별로 평균하고 합이 0 이 되게 맞춘다
    const sums = Array(7).fill(0), cnts = Array(7).fill(0);
    trend.forEach((tr, i) => { if (tr !== null) { sums[i % 7] += x[i] - tr; cnts[i % 7] += 1; } });
    const raw = sums.map((s, j) => s / cnts[j]);
    const adj = mean(raw);
    const seas = raw.map(v => v - adj);
    const S = t.map((_, i) => seas[i % 7]);
    const rem = t.map((_, i) => (trend[i] === null ? null : x[i] - trend[i] - S[i]));

    const g = [txt(W / 2, 26, '한 계열을 추세 · 계절 · 나머지 세 줄로 쪼갠다', { anchor: 'middle', cls: 'ink bold' })];
    const rows = [
        { name: '원계열', ys: x, rng: [150, 290], ticks: [180, 240], cls: 'var(--ink2)' },
        { name: '추세', sub: '7항 이동평균', ys: trend, rng: [200, 270], ticks: [210, 250], cls: 'var(--s1)' },
        { name: '계절', sub: '요일별 평균', ys: S, rng: [-35, 20], ticks: [-30, 0], cls: 'var(--s2)' },
        { name: '나머지', ys: rem, rng: [-20, 20], ticks: [-15, 0, 15], cls: 'var(--s3)' },
    ];
    const top = 48, rowH = 112, gap = 18;
    rows.forEach((r, k) => {
        const y = top + k * (rowH + gap);
        const fr = frame({ xRange: [0, 57], yRange: r.rng, box: { x: 130, y, w: 590, h: rowH } });
        g.push(box(130, y, 590, rowH, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
        g.push(txt(92, y + rowH / 2, r.name, { anchor: 'end', cls: 'ink bold' }));
        if (r.sub) g.push(txt(92, y + rowH / 2 + 17, r.sub, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (r.rng[0] < 0) g.push(ln([[fr.X(0), fr.Y(0)], [fr.X(57), fr.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
        // null 은 끊어 그린다
        let seg = [];
        const flush = () => { if (seg.length > 1) g.push(ln(seg, { stroke: r.cls, sw: 1.8 })); seg = []; };
        r.ys.forEach((v, i) => { if (v === null) flush(); else seg.push([fr.X(t[i]), fr.Y(v)]); });
        flush();
        for (const tk of r.ticks) g.push(txt(126, fr.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (k === 0) {
            // 토요일(주의 여섯째 날) 골짜기를 짚는다
            const i = 5 + 7 * 3;
            g.push(txt(fr.X(t[i]) + 8, fr.Y(x[i]) + 14, '주마다 되풀이되는 골짜기', { cls: 'ink2', size: 'sm' }));
        }
    });
    const yb = top + 4 * (rowH + gap) - gap;
    const fb = frame({ xRange: [0, 57], yRange: [0, 1], box: { x: 130, y: yb, w: 590, h: 1 } });
    g.push(xaxis(fb, [0, 57], [1, 7, 14, 21, 28, 35, 42, 49, 56], { arrow: false }));
    g.push(txt(720, yb + 32, '시점 t (하루 간격)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '원계열 = 추세 + 계절 + 나머지. 이동평균은 양 끝 세 시점씩을 계산하지 못한다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'sta-ts-decompose',
        svg: svg({
            width: W, height: H,
            title: '시계열의 덧셈 분해 — 추세, 계절, 나머지',
            desc: '56일 동안 매일 센 값. 7항 이동평균이 추세를, 요일별 평균이 계절 성분을 잡고, 남은 것이 나머지다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 2. 상관도 셋 — 백색잡음, AR(1), 계절
 * ================================================================== */
add((() => {
    const W = 760, H = 560;
    const n = 100, K = 21;
    const e1 = gaussSeq(5150, n);
    const e2 = gaussSeq(7272, n);
    const e3 = gaussSeq(9393, n);
    const wn = e1;
    const ar = ar1(e2, 0.7);
    const pat = [3, 2, 1, 0, -1, -4, -1];
    const se = e3.map((v, i) => pat[i % 7] + 0.9 * v);
    const band = 2 / Math.sqrt(n);
    const series = [
        { name: '백색잡음', x: wn },
        { name: 'AR(1), φ = 0.7', x: ar },
        { name: '계절 계열, 주기 7', x: se },
    ];
    const g = [txt(W / 2, 26, '세 계열의 상관도 — 점선 띠 ±2/√n 밖으로 나가는 막대를 본다', { anchor: 'middle', cls: 'ink bold' })];
    const top = 46, rowH = 140, gap = 22;
    series.forEach((s, k) => {
        const r = acf(s.x, K);
        const out = r.filter(v => Math.abs(v) > band).length;
        const y = top + k * (rowH + gap);
        const fr = frame({ xRange: [0, K + 1], yRange: [-1, 1], box: { x: 120, y, w: 560, h: rowH } });
        g.push(box(120, y, 560, rowH, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
        g.push(txt(80, y + rowH / 2 - 4, s.name.split(',')[0], { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        if (s.name.includes(',')) g.push(txt(80, y + rowH / 2 + 12, s.name.split(',')[1].trim(), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(80, y + rowH / 2 + 30, `띠 밖 ${out}/${K}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(ln([[fr.X(0), fr.Y(0)], [fr.X(K + 1), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1 }));
        for (const b of [band, -band]) g.push(ln([[fr.X(0), fr.Y(b)], [fr.X(K + 1), fr.Y(b)]], { stroke: 'var(--s2)', sw: 1.2, dash: '5 4' }));
        r.forEach((v, i) => {
            const kk = i + 1;
            const col = Math.abs(v) > band ? 'var(--s2)' : 'var(--s1)';
            g.push(ln([[fr.X(kk), fr.Y(0)], [fr.X(kk), fr.Y(v)]], { stroke: col, sw: 4 }));
        });
        for (const tk of [-1, -0.5, 0.5, 1]) g.push(txt(116, fr.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (k === 2) {
            for (const kk of [1, 7, 14, 21]) g.push(txt(fr.X(kk), y + rowH + 15, `${kk}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    g.push(txt(680, top + 3 * (rowH + gap) - gap + 32, '시차 k', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 14, `막대는 표본 자기상관 r~k, 점선은 ±2/√${n} = ±${band.toFixed(2)}. 각 계열 n = ${n}`, { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'sta-ts-acf',
        svg: svg({
            width: W, height: H,
            title: '백색잡음, AR(1), 계절 계열의 상관도',
            desc: '시차 1부터 21까지의 표본 자기상관 막대와 백색잡음 띠. 계열마다 띠 밖으로 나간 막대 수를 적었다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 3. 확률보행과 그 차분 (정상성 절)
 * ================================================================== */
add((() => {
    const W = 760, H = 420;
    const n = 150;
    const e = gaussSeq(31337, n);
    const rw = [];
    e.reduce((s, v) => { const z = s + v; rw.push(z); return z; }, 0);
    const d = rw.map((v, i) => (i === 0 ? v : v - rw[i - 1]));
    const t = Array.from({ length: n }, (_, i) => i + 1);
    const lo = Math.floor(Math.min(...rw) / 5) * 5, hi = Math.ceil(Math.max(...rw) / 5) * 5;
    const g = [txt(W / 2, 26, '확률보행은 정상이 아니지만 한 번 차분하면 백색잡음이 된다', { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [0, n + 2], yRange: [lo, hi], box: { x: 150, y: 50, w: 580, h: 170 } });
    g.push(box(150, 50, 580, 170, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
    g.push(txt(104, 125, '확률보행', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(104, 142, 'X~t = X~{t−1} + ε~t', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(ln([[A.X(0), A.Y(0)], [A.X(n + 2), A.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(path(A, t, rw, { stroke: 'var(--s1)', sw: 1.6 }));
    for (const tk of [lo, 0, hi]) g.push(txt(146 - 4, A.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const B = frame({ xRange: [0, n + 2], yRange: [-3.5, 3.5], box: { x: 150, y: 246, w: 580, h: 110 } });
    g.push(box(150, 246, 580, 110, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
    g.push(txt(104, 296, '차분', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(104, 313, 'X~t − X~{t−1}', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(ln([[B.X(0), B.Y(0)], [B.X(n + 2), B.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(path(B, t, d, { stroke: 'var(--s2)', sw: 1.3 }));
    for (const tk of [-3, 0, 3]) g.push(txt(146 - 4, B.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const fb = frame({ xRange: [0, n + 2], yRange: [0, 1], box: { x: 150, y: 356, w: 580, h: 1 } });
    g.push(xaxis(fb, [0, n + 2], [1, 50, 100, 150], { arrow: false }));
    g.push(txt(730, 388, '시점 t', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const r1rw = acf(rw, 1)[0], r1d = acf(d, 1)[0];
    g.push(txt(W / 2, H - 10, `위 계열 r~1 = ${r1rw.toFixed(2)}, 아래 계열 r~1 = ${r1d.toFixed(2)}. 위는 오래 한쪽에 머물고 아래는 0 을 중심으로 고르게 흔들린다`,
        { anchor: 'middle', cls: 'ink', size: 'sm' }));
    return {
        name: 'sta-ts-diff',
        svg: svg({
            width: W, height: H,
            title: '확률보행과 그 일차 차분',
            desc: '같은 백색잡음을 누적한 확률보행과, 그것을 한 번 차분해 되찾은 백색잡음',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4. AR(1) 두 경로 — φ = 0.2 와 φ = 0.9
 * ================================================================== */
add((() => {
    const W = 760, H = 420;
    const n = 150;
    const e = gaussSeq(2024, n);
    const t = Array.from({ length: n }, (_, i) => i + 1);
    const a = ar1(e, 0.2), b = ar1(e, 0.9);
    const g = [txt(W / 2, 26, '같은 백색잡음으로 만든 두 AR(1) — φ 가 클수록 천천히, 크게 출렁인다', { anchor: 'middle', cls: 'ink bold' })];
    const rows = [{ phi: 0.2, x: a, y: 50, col: 'var(--s1)' }, { phi: 0.9, x: b, y: 210, col: 'var(--s2)' }];
    for (const r of rows) {
        const fr = frame({ xRange: [0, n + 2], yRange: [-6, 6], box: { x: 150, y: r.y, w: 580, h: 140 } });
        g.push(box(150, r.y, 580, 140, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
        g.push(txt(112, r.y + 62, `φ = ${r.phi}`, { anchor: 'end', cls: 'ink bold' }));
        const r1 = acf(r.x, 1)[0];
        const sd = Math.sqrt(r.x.reduce((s, v) => s + (v - mean(r.x)) ** 2, 0) / n);
        g.push(txt(112, r.y + 80, `r~1 = ${r1.toFixed(2)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(112, r.y + 96, `표준편차 ${sd.toFixed(2)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(112, r.y + 112, `이론값 ${(1 / Math.sqrt(1 - r.phi ** 2)).toFixed(2)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(ln([[fr.X(0), fr.Y(0)], [fr.X(n + 2), fr.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
        g.push(path(fr, t, r.x, { stroke: r.col, sw: 1.4 }));
        for (const tk of [-5, 0, 5]) g.push(txt(146 - 4, fr.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    const fb = frame({ xRange: [0, n + 2], yRange: [0, 1], box: { x: 150, y: 350, w: 580, h: 1 } });
    g.push(xaxis(fb, [0, n + 2], [1, 50, 100, 150], { arrow: false }));
    g.push(txt(730, 382, '시점 t', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 8, '백색잡음의 표준편차 σ~ε = 1. 이론 표준편차는 1/√(1 − φ²)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    return {
        name: 'sta-ts-ar1',
        svg: svg({
            width: W, height: H,
            title: 'AR(1) 과정의 두 경로',
            desc: '같은 잡음 계열에 φ = 0.2 와 φ = 0.9 를 적용한 경로. 각 경로의 r1 과 표준편차를 계산해 적었다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5. 독립인 두 확률보행이 높은 상관을 보인다
 * ================================================================== */
add((() => {
    const W = 760, H = 380;
    const n = 100;
    const ex = gaussSeq(4242, n), ey = gaussSeq(8686, n);
    const cum = e => { const o = []; e.reduce((s, v) => { const z = s + v; o.push(z); return z; }, 0); return o; };
    const x = cum(ex), y = cum(ey);
    const t = Array.from({ length: n }, (_, i) => i + 1);
    const mx = mean(x), my = mean(y);
    let sxx = 0, sxy = 0, syy = 0;
    for (let i = 0; i < n; i += 1) { sxx += (x[i] - mx) ** 2; sxy += (x[i] - mx) * (y[i] - my); syy += (y[i] - my) ** 2; }
    const b1 = sxy / sxx, b0 = my - b1 * mx, r = sxy / Math.sqrt(sxx * syy);
    const s2 = (syy - b1 * sxy) / (n - 2), tval = b1 / Math.sqrt(s2 / sxx);
    const lo = Math.floor(Math.min(...x, ...y) / 5) * 5, hi = Math.ceil(Math.max(...x, ...y) / 5) * 5;
    const g = [txt(W / 2, 26, '서로 아무 관계 없이 만든 두 확률보행이 뚜렷한 상관을 보인다', { anchor: 'middle', cls: 'ink bold' })];
    g.push(panel(20, 46, 400, 270, '시간에 따른 두 계열'));
    const A = frame({ xRange: [0, n + 1], yRange: [lo, hi], box: { x: 64, y: 82, w: 330, h: 200 } });
    g.push(ln([[A.X(0), A.Y(0)], [A.X(n + 1), A.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(path(A, t, x, { stroke: 'var(--s1)', sw: 1.6 }));
    g.push(path(A, t, y, { stroke: 'var(--s2)', sw: 1.6 }));
    g.push(xaxis(frame({ xRange: [0, n + 1], yRange: [0, 1], box: { x: 64, y: 282, w: 330, h: 1 } }), [0, n + 1], [1, 50, 100], { arrow: false }));
    for (const tk of [lo, 0, hi]) g.push(txt(58, A.Y(tk) + 4, neg(tk), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(box(74, 252, 16, 3, { fill: 'var(--s1)', stroke: 'none', rx: 1.5 }));
    g.push(txt(96, 258, '계열 x', { cls: 'ink bold', size: 'sm' }));
    g.push(box(74, 270, 16, 3, { fill: 'var(--s2)', stroke: 'none', rx: 1.5 }));
    g.push(txt(96, 276, '계열 y', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(440, 46, 300, 270, 'y 를 x 에 회귀'));
    const xl = Math.floor(Math.min(...x) / 5) * 5, xh = Math.ceil(Math.max(...x) / 5) * 5;
    const yl = Math.floor(Math.min(...y) / 5) * 5, yh = Math.ceil(Math.max(...y) / 5) * 5;
    const B = frame({ xRange: [xl, xh], yRange: [yl, yh], box: { x: 486, y: 82, w: 230, h: 200 } });
    for (let i = 0; i < n; i += 1) g.push(pdot(B.X(x[i]), B.Y(y[i]), 'var(--s3)', 2.4));
    g.push(ln([[B.X(xl), B.Y(b0 + b1 * xl)], [B.X(xh), B.Y(b0 + b1 * xh)]], { stroke: 'var(--ink)', sw: 1.6 }));
    g.push(box(486, 82, 230, 200, { stroke: 'var(--grid)', sw: 1, rx: 2 }));
    g.push(txt(601, 300, `x (${xl} 부터 ${xh})`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 342, `r = ${r.toFixed(2)},  기울기의 t = ${tval.toFixed(1)}  (n = ${n}). 독립인 백색잡음에서 같은 계산을 하면 t 가 대개 ±2 안에 든다`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 364, '계열마다 오래 한쪽으로 흘러가므로 두 흐름이 우연히 겹치는 구간이 길게 생긴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-ts-randomwalk',
        svg: svg({
            width: W, height: H,
            title: '허위 회귀 — 독립인 두 확률보행의 상관',
            desc: '서로 다른 씨앗으로 만든 두 확률보행과 그 산점도. 상관계수와 회귀 기울기의 t 값을 계산해 적었다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6. 유효 표본 크기 비율 (1 − φ)/(1 + φ)
 * ================================================================== */
add((() => {
    const W = 720, H = 380;
    const f = p => (1 - p) / (1 + p);
    const g = [txt(W / 2, 26, 'AR(1) 자료의 평균에서 유효 표본 크기는 n 의 (1 − φ)/(1 + φ) 배다', { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [-0.5, 1], yRange: [0, 3], box: { x: 90, y: 56, w: 560, h: 230 } });
    for (const y of [0.5, 1.5, 2, 2.5]) g.push(ln([[A.X(-0.5), A.Y(y)], [A.X(1), A.Y(y)]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(ln([[A.X(-0.5), A.Y(1)], [A.X(1), A.Y(1)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '6 4' }));
    g.push(txt(A.X(1) - 2, A.Y(1) - 6, '독립(φ = 0)이면 1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(A.curve(f, { from: -0.5, to: 0.98, cls: 's1', steps: 300 }));
    g.push(xaxis(A, [-0.5, 1], [-0.5, -0.25, 0, 0.25, 0.5, 0.75, 1]));
    g.push(yaxis(A, [0, 3], -0.5, [0, 0.5, 1, 1.5, 2, 2.5, 3]));
    g.push(txt(A.X(1) + 14, A.Y(0) + 4, 'φ', { cls: 'ink2' }));
    g.push(txt(A.X(-0.5) - 8, A.Y(3) - 8, '유효 n / n', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    for (const p of [-0.33, 0.5, 0.9]) {
        g.push(pdot(A.X(p), A.Y(f(p)), 'var(--s2)', 4));
    }
    g.push(txt(A.X(-0.33) + 8, A.Y(f(-0.33)) - 6, `φ = −0.33 → ${f(-0.33).toFixed(2)}`, { cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(0.5) + 8, A.Y(f(0.5)) - 8, `φ = 0.5 → ${f(0.5).toFixed(2)}`, { cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(0.9) - 6, A.Y(f(0.9)) - 22, `φ = 0.9 → ${f(0.9).toFixed(3)}`, { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(arw(A.X(0.9) - 6, A.Y(f(0.9)) - 18, A.X(0.9) - 2, A.Y(f(0.9)) - 6, { cls: 's2', width: 1.2 }));
    g.push(ln([[46, 322], [W - 46, 322]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 346, `φ = 0.9 이면 관측 1,000 개가 독립 관측 ${(1000 * f(0.9)).toFixed(0)} 개만큼의 정보밖에 주지 않는다`, { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 366, 'φ 가 음수이면 이웃이 서로 반대로 흔들려 평균이 오히려 더 안정된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-ts-neff',
        svg: svg({
            width: W, height: H,
            title: 'AR(1) 계수에 따른 유효 표본 크기 비율',
            desc: '가로축 φ, 세로축 유효 표본 크기를 n 으로 나눈 값. 큰 표본 근사 (1 − φ)/(1 + φ) 의 곡선이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 7. AR(1) 예측구간이 넓어지다 멈춘다
 * ================================================================== */
add((() => {
    const W = 760, H = 400;
    const mu = 50, phi = 0.7, se = 4, hist = 30, Hh = 15;
    // 마지막 관측이 60 근처가 되는 씨앗을 차례로 찾는다(결정적)
    let path0 = null;
    for (let seed = 1; seed < 5000 && !path0; seed += 1) {
        const e = gaussSeq(seed, hist);
        const p = [];
        let prev = mu;
        for (const v of e) { prev = mu + phi * (prev - mu) + se * v; p.push(prev); }
        if (Math.abs(p[hist - 1] - 60) < 0.6 && Math.abs(p[hist - 2] - 58) < 3) path0 = p;
    }
    path0[hist - 1] = 60;
    const fc = [], lo = [], hi = [];
    for (let h = 1; h <= Hh; h += 1) {
        const m = mu + phi ** h * (60 - mu);
        const sd = se * Math.sqrt((1 - phi ** (2 * h)) / (1 - phi * phi));
        fc.push(m); lo.push(m - 1.96 * sd); hi.push(m + 1.96 * sd);
    }
    const sdX = se / Math.sqrt(1 - phi * phi);
    const g = [txt(W / 2, 26, 'AR(1) 예측구간은 걸음마다 넓어지다가 정상분포의 폭에서 멈춘다', { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [0, hist + Hh + 1], yRange: [30, 75], box: { x: 80, y: 52, w: 600, h: 250 } });
    // 정상 분포 띠
    g.push(box(A.X(hist), A.Y(mu + 1.96 * sdX), A.X(hist + Hh) - A.X(hist), A.Y(mu - 1.96 * sdX) - A.Y(mu + 1.96 * sdX),
        { fill: 'var(--grid)', op: 0.35, stroke: 'none', rx: 0 }));
    const band = [];
    for (let h = 0; h <= Hh; h += 1) band.push([A.X(hist + h), A.Y(h === 0 ? 60 : hi[h - 1])]);
    for (let h = Hh; h >= 0; h -= 1) band.push([A.X(hist + h), A.Y(h === 0 ? 60 : lo[h - 1])]);
    g.push(`<path d="M${band.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s2)" fill-opacity="0.2" stroke="none"/>`);
    g.push(ln([[A.X(0), A.Y(mu)], [A.X(hist + Hh + 1), A.Y(mu)]], { stroke: 'var(--ink2)', sw: 1, dash: '6 4' }));
    g.push(txt(A.X(1), A.Y(mu) + 16, '평균 μ = 50', { cls: 'ink2', size: 'sm' }));
    g.push(path(A, Array.from({ length: hist }, (_, i) => i + 1), path0, { stroke: 'var(--s1)', sw: 1.8 }));
    g.push(path(A, Array.from({ length: Hh + 1 }, (_, i) => hist + i), [60, ...fc], { stroke: 'var(--s2)', sw: 2, dash: '5 3' }));
    g.push(pdot(A.X(hist), A.Y(60), 'var(--s1)', 4));
    g.push(txt(A.X(hist) - 6, A.Y(60) - 10, '마지막 관측 60', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(hist + 9), A.Y(57), '95% 예측구간', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(hist + Hh), A.Y(mu - 1.96 * sdX) + 16, `정상분포 폭 50 ± ${(1.96 * sdX).toFixed(1)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(xaxis(A, [0, hist + Hh + 1], [1, 10, 20, 30, 35, 40, 45], { y0: 30 }));
    g.push(yaxis(A, [30, 75], 0, [30, 40, 50, 60, 70]));
    g.push(txt(A.X(hist + Hh + 1) + 4, A.Y(30) + 30, '시점 t', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 352, `한 걸음 앞 ±${(1.96 * se).toFixed(1)},  두 걸음 ±${(1.96 * se * Math.sqrt(1 + phi * phi)).toFixed(1)},  열다섯 걸음 ±${(hi[Hh - 1] - fc[Hh - 1]).toFixed(1)}  (μ = 50, φ = 0.7, σ~ε = 4)`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 374, '멀리 내다볼수록 예측값은 평균으로 돌아가고, 마지막 관측이 주는 정보는 φ 의 거듭제곱으로 사라진다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-ts-fan',
        svg: svg({
            width: W, height: H,
            title: 'AR(1) 예측과 95% 예측구간',
            desc: '관측 30개 뒤 15걸음 앞까지의 예측값과 예측구간. 회색 띠는 정상분포의 95% 폭이다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
