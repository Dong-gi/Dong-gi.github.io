/**
 * 통계학 16장(모형 선택과 과적합)의 그림. 이름은 전부 `sta-sel-` 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(<img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 유니코드(β₁ …)로 적고, 라벨에 물결표 `~` 를 쓰지 않는다(lib 의 esc() 가
 * 아래첨자로 바꾼다). 따옴표는 ‘ ’, HTML 엔티티는 쓰지 않는다.
 *
 * 난수는 `sta-sel-poly` 의 점 열 개에만 쓰고, 씨앗을 고정한 선형합동생성기로 만든다.
 * 훈련·시험 오차 곡선은 난수 없이 고정 설계의 기댓값 식으로 계산한다:
 *   훈련 = (B + σ²(n − p)) / n,  시험 = (B + σ²(n + p)) / n
 * 여기서 B 는 참 평균 벡터를 다항식 공간에 정사영하고 남은 제곱 길이, p 는 계수 개수다.
 * 다항식은 단항식 대신 점 위에서 직교하는 다항식(세 항 점화식)으로 적합해 수치 오차를 피한다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');

/* ---------------- 공통 소도구 ---------------- */

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3 } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

const pdot = (x, y, col = 'var(--s1)', r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

function panel(x, y, w, h, title) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' });
}

function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg, xLabel, yLabel, grid = true }) {
    const out = [];
    if (grid) {
        for (const t of xTicks) out.push(`<path class="gr" d="M${fr.X(t)} ${fr.Y(y0)} V${fr.Y(y1)}"/>`);
        for (const t of yTicks) out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
    }
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`);
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`);
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 16, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (xLabel) out.push(txt(fr.X(x1), fr.Y(y0) + 34, xLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) + 8, fr.Y(y1) - 4, yLabel, { cls: 'ink2', size: 'sm' }));
    return out.join('');
}

/** 씨앗을 고정한 선형합동생성기와 박스-뮬러 정규난수. */
function rng(seed) {
    let s = seed >>> 0;
    const u = () => {
        s = (Math.imul(1664525, s) + 1013904223) >>> 0;
        return (s + 0.5) / 4294967296;
    };
    const z = () => Math.sqrt(-2 * Math.log(u())) * Math.cos(2 * Math.PI * u());
    return { u, z };
}

/**
 * 점 xs 위에서 서로 직교하는 다항식 q₀, q₁, … (최고차 계수 1) 의 점화 계수를 구한다.
 * q_{j+1}(t) = (t − a_j) q_j(t) − b_j q_{j−1}(t). 가로값은 [−1, 1] 로 옮겨 쓴다.
 */
function orthoPoly(xs, maxDeg, toT) {
    const ts = xs.map(toT);
    let prev = ts.map(() => 0), cur = ts.map(() => 1);
    const a = [], b = [0], nrm = [];
    for (let j = 0; j <= maxDeg; j += 1) {
        nrm[j] = cur.reduce((s, v) => s + v * v, 0);
        a[j] = cur.reduce((s, v, i) => s + ts[i] * v * v, 0) / nrm[j];
        if (j > 0) b[j] = nrm[j] / nrm[j - 1];
        const next = cur.map((v, i) => (ts[i] - a[j]) * v - b[j] * prev[i]);
        prev = cur; cur = next;
    }
    const evalAll = (x, deg) => {
        const t = toT(x);
        const q = [1];
        if (deg >= 1) q.push(t - a[0]);
        for (let j = 1; j < deg; j += 1) q.push((t - a[j]) * q[j] - b[j] * q[j - 1]);
        return q;
    };
    /** 차수 deg 까지의 최소제곱 계수(직교 기저에서는 내적 ÷ 제곱 길이). */
    const fit = (ys, deg) => {
        const c = [];
        for (let j = 0; j <= deg; j += 1) {
            c.push(xs.reduce((s, x, i) => s + ys[i] * evalAll(x, deg)[j], 0) / nrm[j]);
        }
        return x => evalAll(x, deg).reduce((s, q, j) => s + c[j] * q, 0);
    };
    return { nrm, evalAll, fit };
}

const truth = x => Math.sin(2 * Math.PI * x);

/* ------------------------------------------------------------------ *
 * 1. 같은 점 열 개에 1·3·9차 다항식
 * ------------------------------------------------------------------ */
{
    const n = 10, sigma = 0.3;
    const R = rng(20261120);
    const xs = Array.from({ length: n }, (_, i) => (i + 0.5) / n);
    const ys = xs.map(x => truth(x) + sigma * R.z());
    const op = orthoPoly(xs, n - 1, x => 2 * x - 1);
    const W = 820, H = 340;
    const b = [];
    const degs = [1, 3, 9];
    degs.forEach((d, k) => {
        const ox = 8 + k * 272;
        const f = op.fit(ys, d);
        const sse = xs.reduce((s, x, i) => s + (ys[i] - f(x)) ** 2, 0);
        b.push(panel(ox, 8, 262, 322, `${d}차 다항식 (계수 ${d + 1}개)`));
        const fr = frame({ xRange: [0, 1], yRange: [-2, 2], box: { x: ox + 40, y: 48, w: 200, h: 220 } });
        const id = `clip${d}`;
        b.push(`<clipPath id="${id}"><rect x="${fr.X(0)}" y="${fr.Y(2)}" width="${fr.X(1) - fr.X(0)}" height="${fr.Y(-2) - fr.Y(2)}"/></clipPath>`);
        b.push(axesEdge(fr, { x0: 0, x1: 1, y0: -2, y1: 2, xTicks: [0, 0.5, 1], yTicks: [-2, -1, 0, 1, 2] }));
        const tp = [], fp = [];
        for (let i = 0; i <= 300; i += 1) {
            const x = i / 300;
            tp.push([fr.X(x), fr.Y(truth(x))]);
            fp.push([fr.X(x), fr.Y(Math.max(-9, Math.min(9, f(x))))]);
        }
        b.push(ln(tp, { stroke: 'var(--ink2)', sw: 1.4, dash: '5 4' }));
        b.push(`<g clip-path="url(#${id})">${ln(fp, { stroke: 'var(--s2)', sw: 2.4 })}</g>`);
        xs.forEach((x, i) => b.push(pdot(fr.X(x), fr.Y(ys[i]), 'var(--s1)', 4)));
        b.push(txt(ox + 131, 298, `훈련 잔차제곱합 ${sse < 1e-9 ? '0' : sse.toFixed(2)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        b.push(txt(ox + 131, 316, d === 1 ? '너무 단순: 굽이를 못 따라간다' : d === 3 ? '굽이를 따라가고 사이도 차분하다' : '점을 다 지나고 사이에서 요동친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(W - 12, H - 2, '점선: 참 평균 곡선(사인 곡선)  점: 관측 10개  굵은 선: 적합한 다항식', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-sel-poly',
        svg: svg({
            width: W, height: H + 14, title: '같은 점 열 개에 1·3·9차 다항식',
            desc: '9차 다항식은 점 열 개를 모두 지나 훈련 잔차가 0 이지만 점 사이에서 크게 요동친다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 2. 복잡도에 따른 훈련 오차와 시험 오차(고정 설계의 기댓값)
 * ------------------------------------------------------------------ */
{
    const n = 20, s2 = 0.09, maxDeg = 14;
    const xs = Array.from({ length: n }, (_, i) => (i + 0.5) / n);
    const mu = xs.map(truth);
    const op = orthoPoly(xs, maxDeg, x => 2 * x - 1);
    const tot = mu.reduce((s, v) => s + v * v, 0);
    let explained = 0;
    const rows = [];
    for (let d = 0; d <= maxDeg; d += 1) {
        const proj = xs.reduce((s, x, i) => s + mu[i] * op.evalAll(x, maxDeg)[d], 0);
        explained += (proj * proj) / op.nrm[d];
        const B = Math.max(0, tot - explained);
        const p = d + 1;
        rows.push({ d, train: (B + s2 * (n - p)) / n, test: (B + s2 * (n + p)) / n });
    }
    const best = rows.reduce((m, r) => (r.test < m.test ? r : m));
    const W = 660, H = 430;
    const fr = frame({ xRange: [0, 14], yRange: [0, 0.6], box: { x: 64, y: 40, w: 450, h: 290 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 14, y0: 0, y1: 0.6, xTicks: [0, 2, 4, 6, 8, 10, 12, 14], yTicks: [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6], xLabel: '다항식 차수 (복잡도)', yLabel: '평균제곱 오차' }));
    b.push(ln([[fr.X(0), fr.Y(s2)], [fr.X(14), fr.Y(s2)]], { stroke: 'var(--ink2)', sw: 1, dash: '4 3' }));
    b.push(txt(fr.X(14) + 6, fr.Y(s2) + 4, '줄일 수 없는 오차 σ²', { cls: 'ink2', size: 'sm' }));
    const clipY = v => Math.min(v, 0.6);
    b.push(ln(rows.map(r => [fr.X(r.d), fr.Y(clipY(r.train))]), { stroke: 'var(--s1)', sw: 2.4 }));
    b.push(ln(rows.map(r => [fr.X(r.d), fr.Y(clipY(r.test))]), { stroke: 'var(--s2)', sw: 2.4, dash: '8 4' }));
    rows.forEach(r => {
        if (r.train <= 0.6) b.push(pdot(fr.X(r.d), fr.Y(r.train), 'var(--s1)', 3));
        if (r.test <= 0.6) b.push(pdot(fr.X(r.d), fr.Y(r.test), 'var(--s2)', 3));
    });
    const last = rows[rows.length - 1];
    b.push(txt(fr.X(14) + 6, fr.Y(last.train) + 4, '훈련 오차', { cls: 'ink', size: 'sm' }));
    b.push(txt(fr.X(14) + 6, fr.Y(last.test) + 4, '시험 오차', { cls: 'ink', size: 'sm' }));
    b.push(`<circle cx="${fr.X(best.d)}" cy="${fr.Y(best.test)}" r="7" fill="none" stroke="var(--s2)" stroke-width="1.6"/>`);
    b.push(txt(fr.X(best.d + 0.6), fr.Y(best.test + 0.08), `가장 낮은 시험 오차: ${best.d}차 (${best.test.toFixed(3)})`, { cls: 'ink', size: 'sm' }));
    b.push(ln([[fr.X(best.d + 0.15), fr.Y(best.test + 0.015)], [fr.X(best.d + 0.55), fr.Y(best.test + 0.07)]], { stroke: 'var(--ink2)', sw: 0.8 }));
    b.push(txt(64, 400, `관측 ${n}개, 참 평균은 사인 곡선, 오차 분산 σ² = ${s2}. 같은 x 에서 새로 잰 y 로 시험한 기댓값`, { cls: 'ink2', size: 'sm' }));
    b.push(txt(64, 418, '훈련 오차는 차수가 오를수록 줄기만 하고 시험 오차는 U 자를 그린다', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-sel-traintest',
        svg: svg({
            width: W + 70, height: H, title: '복잡도에 따른 훈련 오차와 시험 오차',
            desc: '복잡도가 오를수록 훈련 오차는 줄기만 하고 시험 오차는 줄다가 다시 오르는 U 자를 그린다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 3. 릿지와 라쏘의 계수 경로(직교 표준화 설계)
 * ------------------------------------------------------------------ */
{
    const ols = [3, -2, 1.2, 0.5];
    const names = ['β₁', 'β₂', 'β₃', 'β₄'];
    const sty = [
        { stroke: 'var(--s1)', dash: null },
        { stroke: 'var(--s2)', dash: '8 4' },
        { stroke: 'var(--s3)', dash: '3 3' },
        { stroke: 'var(--ink2)', dash: '10 3 2 3' },
    ];
    const ridge = (b, l) => b / (1 + l);
    const lasso = (b, l) => Math.sign(b) * Math.max(0, Math.abs(b) - l / 2);
    const W = 800, H = 400;
    const b = [];
    [['릿지: 모두 함께 줄고 0 에 닿지 않는다', ridge], ['라쏘: 하나씩 정확히 0 이 된다', lasso]].forEach(([title, f], k) => {
        const ox = 8 + k * 396;
        b.push(panel(ox, 8, 386, 384, title));
        const fr = frame({ xRange: [0, 7], yRange: [-2.6, 4.0], box: { x: ox + 48, y: 50, w: 270, h: 270 } });
        b.push(axesEdge(fr, { x0: 0, x1: 7, y0: -2.6, y1: 4.0, xTicks: [0, 1, 2, 3, 4, 5, 6, 7], yTicks: [-2, -1, 0, 1, 2, 3], xLabel: '벌점 λ', yLabel: '계수' }));
        b.push(ln([[fr.X(0), fr.Y(0)], [fr.X(7), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1 }));
        ols.forEach((v, j) => {
            const pts = [];
            for (let i = 0; i <= 200; i += 1) {
                const l = (7 * i) / 200;
                pts.push([fr.X(l), fr.Y(f(v, l))]);
            }
            b.push(ln(pts, { stroke: sty[j].stroke, sw: 2.2, dash: sty[j].dash }));
            b.push(txt(fr.X(0) + 6, fr.Y(v) + (v < 0 ? 16 : -6), `${names[j]} = ${neg(v)}`, { cls: 'ink', size: 'sm' }));
            if (f === lasso) {
                const z = 2 * Math.abs(v);
                b.push(`<circle cx="${fr.X(z)}" cy="${fr.Y(0)}" r="4" fill="none" stroke="${sty[j].stroke}" stroke-width="1.8"/>`);
                b.push(txt(fr.X(z), fr.Y(0) + (j % 2 ? -10 : 18), `${names[j]}: λ=${r2(z)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
        });
        b.push(txt(ox + 193, 384, f === ridge ? '계수 = 최소제곱 계수 ÷ (1 + λ)' : '계수 = 최소제곱 계수를 λ/2 만큼 0 쪽으로 깎은 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    add({
        name: 'sta-sel-path',
        svg: svg({
            width: W, height: H, title: '릿지와 라쏘의 계수 경로',
            desc: '벌점 λ 를 키우면 릿지 계수는 모두 같은 비율로 줄고, 라쏘 계수는 작은 것부터 하나씩 정확히 0 이 된다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 4. 잡음 변수 m 개 가운데 가장 작은 p값의 분포
 * ------------------------------------------------------------------ */
{
    const W = 660, H = 400;
    const fr = frame({ xRange: [0, 1], yRange: [0, 20], box: { x: 64, y: 40, w: 440, h: 290 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 1, y0: 0, y1: 20, xTicks: [0, 0.05, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [0, 5, 10, 15, 20], xLabel: '고른 변수의 p값', yLabel: '밀도' }));
    const ms = [
        { m: 1, stroke: 'var(--s1)', dash: null },
        { m: 5, stroke: 'var(--s3)', dash: '3 3' },
        { m: 20, stroke: 'var(--s2)', dash: '8 4' },
    ];
    const lines = [];
    for (const { m, stroke, dash } of ms) {
        const dens = u => m * (1 - u) ** (m - 1);
        const pts = [];
        for (let i = 0; i <= 300; i += 1) {
            const u = i / 300;
            pts.push([fr.X(u), fr.Y(Math.min(20, dens(u)))]);
        }
        b.push(ln(pts, { stroke, sw: 2.4, dash }));
        const P = 1 - 0.95 ** m;
        lines.push(`m = ${m}: 0.05 아래일 확률 ${P.toFixed(3)}`);
        const lx = m === 1 ? 0.75 : m === 5 ? 0.22 : 0.07;
        b.push(txt(fr.X(lx) + 6, fr.Y(Math.min(20, dens(lx))) - 8, `잡음 변수 ${m}개`, { cls: 'ink', size: 'sm' }));
    }
    b.push(ln([[fr.X(0.05), fr.Y(0)], [fr.X(0.05), fr.Y(20)]], { stroke: 'var(--ink2)', sw: 1, dash: '4 3' }));
    lines.forEach((s, i) => b.push(txt(fr.X(0.45), fr.Y(17) + i * 18, s, { cls: 'ink2', size: 'sm' })));
    b.push(txt(64, 400, '모든 설명변수가 결과와 무관하고 서로 독립인 검정일 때, m 개 가운데 p값이 가장 작은 것을 골랐다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(64, 418, '밀도 = m(1 − u)^(m−1). 하나만 보면(m = 1) 평평하지만 고르면 0 쪽으로 몰린다', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-sel-selbias',
        svg: svg({
            width: W, height: H + 28, title: '고른 변수의 p값 분포',
            desc: '잡음뿐인 변수 가운데 p값이 가장 작은 것을 고르면 그 p값의 분포가 0 쪽으로 쏠린다. 고른 개수 m 이 클수록 심하다',
            body: b.join(''),
        }),
    });
}

export default figures;
