/**
 * 통계학 3장(자료를 들여다본다)의 그림. 이름은 전부 `sta-eda-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다)
 *   SVG 는 <img> 로 실리므로 라벨에 수식을 쓸 수 없다. 첨자는 lib 의 `x~0` 표기,
 *   나머지는 유니코드(θ μ σ ≈ ≤ − × ·)로 적는다. lib 의 esc() 가 물결표를 아래첨자로
 *   바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다. HTML 엔티티도 쓸 수 없다.
 *   lib 의 px() 에 cls 'ark'·'ar1' 을 주면 선이 사라지므로 화살표는 아래 arw() 로 긋는다.
 *   그림 안에 한자를 쓰지 않는다.
 *
 * 자료
 *   통학 시간 30개와 휴대전화 사용 시간 11개는 본문 예제와 같은 수다(손으로 만든 가상 자료).
 *   경험분포함수 그림만 난수를 쓰며 씨앗을 고정한 선형합동생성기로 뽑는다.
 *   Q-Q 그림과 로그 그림은 난수 대신 분포의 분위수를 그대로 찍는다(잡음 없는 모양).
 *   그림 안의 숫자(최대 차이, 평균, 다섯 수)는 모두 그 자리에서 계산한다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');
const fmt = (v, d = 1) => neg(Number(v.toFixed(d)));

/* ---------------- 공통 소도구 ---------------- */

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
const pdot = (x, y, col = 'var(--s1)', r = 3.5, op = 1) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}" fill-opacity="${op}"/>`;
const odot = (x, y, col = 'var(--s2)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="none" stroke="${col}" stroke-width="1.8"/>`;
function arw(x1, y1, x2, y2, { col = 'var(--ink2)', mk = 'ark', width = 1.6 } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function arw2(x1, y1, x2, y2, { col = 'var(--ink2)', mk = 'ark', width = 1.4 } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" marker-start="url(#${mk})" marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}
/** 왼쪽 가장자리에 세로축을 세우는 축. */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fx = neg, fy = neg, xLabel, yLabel }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) {
        out.push(ln([[fr.X(t), fr.Y(y0)], [fr.X(t), fr.Y(y0) + 4]], { sw: 1 }));
        out.push(txt(fr.X(t), fr.Y(y0) + 16, fx(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
        out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fy(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) out.push(txt(fr.X(x1), fr.Y(y0) + 32, xLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) - 4, fr.Y(y1) - 14, yLabel, { anchor: 'start', cls: 'ink2', size: 'sm' }));
    return out.join('');
}
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}
/** 표준정규 누적분포(Abramowitz-Stegun 7.1.26, 오차 1.5e-7). */
function normCdf(z) {
    const s = z < 0 ? -1 : 1;
    const x = Math.abs(z) / Math.SQRT2;
    const t = 1 / (1 + 0.3275911 * x);
    const y = 1 - t * Math.exp(-x * x) * (0.254829592
        + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
    return 0.5 * (1 + s * y);
}
/** 단조 증가 함수 F 의 역함수를 이분법으로. */
function invert(F, p, lo = -50, hi = 50) {
    for (let i = 0; i < 100; i += 1) {
        const m = (lo + hi) / 2;
        if (F(m) < p) lo = m; else hi = m;
    }
    return (lo + hi) / 2;
}
const qnorm = p => invert(normCdf, p, -10, 10);
const median = a => { const s = [...a].sort((x, y) => x - y); const n = s.length; return n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2; };
/** 이 문서의 사분위수 규칙: n 이 홀수면 중앙값을 양쪽에서 뺀 뒤 각 절반의 중앙값. */
function quartiles(a) {
    const s = [...a].sort((x, y) => x - y); const n = s.length;
    const lo = s.slice(0, Math.floor(n / 2)); const hi = s.slice(Math.ceil(n / 2));
    return [median(lo), median(s), median(hi)];
}
const mean = a => a.reduce((u, v) => u + v, 0) / a.length;

/* 본문 자료 */
const COMMUTE = [5, 7, 8, 9, 10, 10, 11, 12, 12, 13, 14, 17, 19, 21, 22, 23, 23, 24, 25, 25, 26, 26, 27, 28, 29, 30, 31, 33, 36, 44];
const PHONE = [2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 30];

/* ------------------------------------------------------------------ *
 * sta-eda-binwidth — 같은 30개를 계급 폭 셋으로
 * ------------------------------------------------------------------ */
{
    const widths = [2, 5, 10];
    const subs = ['좁다: 봉우리가 여럿, 대부분 잡음', '봉우리 둘이 보인다', '넓다: 봉우리 하나로 뭉개진다'];
    const PW = 340, PH = 290, GAP = 16, W = 3 * PW + 2 * GAP + 20, H = PH + 20;
    let body = '';
    widths.forEach((w, k) => {
        const x0 = 10 + k * (PW + GAP), y0 = 10;
        const counts = [];
        for (let e = 0; e < 50; e += w) counts.push([e, COMMUTE.filter(v => v >= e && v < e + w).length]);
        const ymax = Math.max(...counts.map(c => c[1]));
        const top = ymax <= 4 ? 4 : ymax <= 8 ? 8 : 14;
        const fr = frame({ xRange: [0, 50], yRange: [0, top], box: { x: x0 + 42, y: y0 + 62, w: PW - 70, h: PH - 140 } });
        body += panel(x0, y0, PW, PH, `계급 폭 ${w} min`, subs[k]);
        for (const [e, c] of counts) {
            if (!c) continue;
            body += box(fr.X(e), fr.Y(c), fr.X(e + w) - fr.X(e), fr.Y(0) - fr.Y(c), { fill: 'var(--s1)', op: 0.35, stroke: 'var(--s1)', sw: 1, rx: 0 });
        }
        const yt = top === 4 ? [0, 2, 4] : top === 8 ? [0, 4, 8] : [0, 5, 10];
        body += axesEdge(fr, { x0: 0, x1: 50, y0: 0, y1: top, xTicks: [0, 10, 20, 30, 40, 50], yTicks: yt.slice(1), xLabel: '통학 시간(min)', yLabel: '학생 수' });
        // 관측값 자리(러그)
        const seen = {};
        for (const v of COMMUTE) {
            seen[v] = (seen[v] ?? 0) + 1;
            body += ln([[fr.X(v), fr.Y(0) + 40 + (seen[v] - 1) * 6], [fr.X(v), fr.Y(0) + 45 + (seen[v] - 1) * 6]], { stroke: 'var(--ink)', sw: 1.4, cap: 'butt' });
        }
        body += txt(x0 + PW / 2, y0 + PH - 10, '아래 눈금: 30개 관측값의 자리(같은 값은 겹쳐 쌓음)', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    });
    add({ name: 'sta-eda-binwidth', svg: svg({ width: W, height: H, title: '같은 자료 30개, 계급 폭 셋', desc: '통학 시간 30개를 계급 폭 2, 5, 10 분으로 그린 히스토그램. 폭 5 에서 봉우리 둘이 보이고 폭 10 에서는 하나로 뭉개진다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-eda-ecdf — 경험분포함수와 참 누적분포함수
 * ------------------------------------------------------------------ */
{
    const rand = rng(20261120);
    const draw = n => Array.from({ length: n }, () => -5 * Math.log(1 - rand()));
    const samples = [draw(10), draw(100)];
    const F = t => 1 - Math.exp(-t / 5);
    const PW = 380, PH = 300, GAP = 16, W = 2 * PW + GAP + 20, H = PH + 20;
    let body = '';
    samples.forEach((s, k) => {
        const x0 = 10 + k * (PW + GAP), y0 = 10;
        const xs = [...s].sort((a, b) => a - b); const n = xs.length;
        // 가장 큰 차이(계단의 양쪽 끝에서 잰다)
        let D = 0, Dx = 0, Dy1 = 0, Dy2 = 0;
        xs.forEach((v, i) => {
            const a = Math.abs((i + 1) / n - F(v)), b = Math.abs(i / n - F(v));
            if (a > D) { D = a; Dx = v; Dy1 = (i + 1) / n; Dy2 = F(v); }
            if (b > D) { D = b; Dx = v; Dy1 = i / n; Dy2 = F(v); }
        });
        body += panel(x0, y0, PW, PH, `n = ${n}`, `가장 큰 세로 차이 ${D.toFixed(2)}`);
        const fr = frame({ xRange: [0, 20], yRange: [0, 1], box: { x: x0 + 46, y: y0 + 60, w: PW - 80, h: PH - 110 } });
        body += axesEdge(fr, { x0: 0, x1: 20, y0: 0, y1: 1, xTicks: [0, 5, 10, 15, 20], yTicks: [0.25, 0.5, 0.75, 1], fy: v => String(v), xLabel: '대기 시간(min)' });
        body += fr.curve(F, { from: 0, to: 20, cls: 's2', dash: '6 4' });
        // 계단
        let prev = 0, px0 = 0;
        const segs = [];
        xs.forEach((v, i) => {
            segs.push(ln([[fr.X(Math.min(px0, 20)), fr.Y(prev)], [fr.X(Math.min(v, 20)), fr.Y(prev)]], { stroke: 'var(--s1)', sw: 2 }));
            if (v <= 20) {
                segs.push(ln([[fr.X(v), fr.Y(prev)], [fr.X(v), fr.Y((i + 1) / n)]], { stroke: 'var(--s1)', sw: 1, dash: '2 2' }));
                if (n <= 10) segs.push(pdot(fr.X(v), fr.Y((i + 1) / n), 'var(--s1)', 3));
            }
            prev = (i + 1) / n; px0 = v;
        });
        segs.push(ln([[fr.X(Math.min(px0, 20)), fr.Y(1)], [fr.X(20), fr.Y(1)]], { stroke: 'var(--s1)', sw: 2 }));
        body += segs.join('');
        if (Dx <= 20) body += arw2(fr.X(Dx) + 7, fr.Y(Dy1), fr.X(Dx) + 7, fr.Y(Dy2), { width: 1.2 });
        body += legend(x0 + PW - 170, y0 + PH - 90, [{ slot: 1, name: '경험분포함수 F̂~n' }, { slot: 2, name: '참 누적분포함수 F(점선)' }]);
        if (k === 0) body += txt(fr.X(11), fr.Y(0.6), '관측값마다 1/10 씩 오른다', { cls: 'ink2', size: 'sm' });
    });
    add({ name: 'sta-eda-ecdf', svg: svg({ width: W, height: H, title: '경험분포함수는 계단으로 F 를 근사한다', desc: '평균 대기 시간 5 분인 지수분포에서 뽑은 10개와 100개의 경험분포함수. 관측값마다 1/n 씩 오르는 계단이며 n 이 크면 참 누적분포함수에 붙는다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-eda-breakdown — 값 하나를 끌어가면
 * ------------------------------------------------------------------ */
{
    const rest = [4, 5, 5, 6, 6, 7, 7, 8];
    const est = x => {
        const a = [...rest, x].sort((u, v) => u - v);
        return { m: mean(a), md: median(a), tr: mean(a.slice(1, -1)) };
    };
    const W = 720, H = 380;
    const fr = frame({ xRange: [9, 100], yRange: [0, 18], box: { x: 70, y: 50, w: 560, h: 260 } });
    let body = axesEdge(fr, { x0: 9, x1: 100, y0: 0, y1: 18, xTicks: [9, 20, 40, 60, 80, 100], yTicks: [3, 6, 9, 12, 15, 18], xLabel: '끌어간 한 값 x', yLabel: '요약값' });
    body += fr.curve(x => est(x).m, { from: 9, to: 100, cls: 's2' });
    body += fr.curve(x => est(x).tr, { from: 9, to: 100, cls: 's3', dash: '7 4' });
    body += fr.curve(x => est(x).md, { from: 9, to: 100, cls: 's1' });
    body += txt(fr.X(100) - 4, fr.Y(est(100).m) - 8, `평균 ${fmt(est(100).m, 1)}`, { anchor: 'end', cls: 'ink' });
    body += txt(fr.X(100) - 4, fr.Y(est(100).tr) - 22, `양끝 하나씩 자른 절사평균 ${fmt(est(100).tr, 2)}`, { anchor: 'end', cls: 'ink' });
    body += txt(fr.X(100) - 4, fr.Y(est(100).md) + 18, `중앙값 ${fmt(est(100).md, 1)}`, { anchor: 'end', cls: 'ink' });
    body += txt(W / 2, 26, '자료 4, 5, 5, 6, 6, 7, 7, 8 에 값 하나(x)를 더해 9개. x 를 9 에서 100 으로 끌어간다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += legend(90, 70, [{ slot: 2, name: '평균' }, { slot: 3, name: '절사평균(점선)' }, { slot: 1, name: '중앙값' }]);
    add({ name: 'sta-eda-breakdown', svg: svg({ width: W, height: H, title: '값 하나를 끌어가면 평균만 따라간다', desc: '아홉 개 중 값 하나를 크게 끌어가면 평균은 직선으로 따라 오르고 중앙값과 절사평균은 그대로 있다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-eda-boxplot — 상자그림과 같은 자료의 히스토그램
 * ------------------------------------------------------------------ */
{
    const [q1, md, q3] = quartiles(PHONE);
    const iqr = q3 - q1, lf = q1 - 1.5 * iqr, uf = q3 + 1.5 * iqr;
    const inside = PHONE.filter(v => v >= lf && v <= uf);
    const wl = Math.min(...inside), wu = Math.max(...inside);
    const outs = PHONE.filter(v => v < lf || v > uf);
    const W = 760, H = 470;
    const fr = frame({ xRange: [-5, 32], yRange: [0, 5], box: { x: 50, y: 270, w: 660, h: 130 } });
    let body = '';
    // 상자그림(위)
    const yb = 150, hb = 44;
    body += box(fr.X(q1), yb - hb / 2, fr.X(q3) - fr.X(q1), hb, { fill: 'var(--s1)', op: 0.18, stroke: 'var(--s1)', sw: 1.8, rx: 2 });
    body += ln([[fr.X(md), yb - hb / 2], [fr.X(md), yb + hb / 2]], { stroke: 'var(--s1)', sw: 3, cap: 'butt' });
    body += ln([[fr.X(wl), yb], [fr.X(q1), yb]], { stroke: 'var(--s1)', sw: 1.6 });
    body += ln([[fr.X(q3), yb], [fr.X(wu), yb]], { stroke: 'var(--s1)', sw: 1.6 });
    body += ln([[fr.X(wl), yb - 12], [fr.X(wl), yb + 12]], { stroke: 'var(--s1)', sw: 1.6 });
    body += ln([[fr.X(wu), yb - 12], [fr.X(wu), yb + 12]], { stroke: 'var(--s1)', sw: 1.6 });
    for (const o of outs) body += odot(fr.X(o), yb, 'var(--s2)', 5);
    for (const f of [lf, uf]) body += ln([[fr.X(f), 70], [fr.X(f), yb + 40]], { stroke: 'var(--s2)', sw: 1.3, dash: '5 4' });
    // 라벨: 위쪽 줄(울타리), 아래쪽 줄(다섯 수)
    body += txt(fr.X(lf), 62, `아래 울타리 Q~1 − 1.5·IQR = ${neg(lf)}`, { anchor: 'start', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(uf), 62, `위 울타리 Q~3 + 1.5·IQR = ${uf}`, { anchor: 'start', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(o0()), yb - 14, `울타리 밖 ${outs.join(', ')}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    function o0() { return outs[0]; }
    const lab = [[wl, `수염 끝 ${wl}`, 'end', -8, 4], [q1, `Q~1 = ${q1}`, 'middle', 0, 42], [md, `중앙값 ${md}`, 'middle', 0, 60], [q3, `Q~3 = ${q3}`, 'middle', 0, 42], [wu, `수염 끝 ${wu}`, 'start', 8, 4]];
    lab.forEach(([v, s, a, dx, dy]) => { body += txt(fr.X(v) + dx, yb + dy, s, { anchor: a, cls: 'ink', size: 'sm' }); });
    body += arw2(fr.X(q1), yb - hb / 2 - 10, fr.X(q3), yb - hb / 2 - 10, { width: 1.1 });
    body += txt((fr.X(q1) + fr.X(q3)) / 2, yb - hb / 2 - 15, `IQR = ${iqr}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(20, 26, '휴대전화 사용 시간(h) 11명 — 위: 상자그림, 아래: 같은 자료의 히스토그램(계급 폭 2 h)', { cls: 'ink', size: 'sm' });
    // 히스토그램(아래)
    for (let e = 0; e < 32; e += 2) {
        const c = PHONE.filter(v => v >= e && v < e + 2).length;
        if (c) body += box(fr.X(e), fr.Y(c), fr.X(e + 2) - fr.X(e), fr.Y(0) - fr.Y(c), { fill: 'var(--s1)', op: 0.35, stroke: 'var(--s1)', sw: 1, rx: 0 });
    }
    body += axesEdge(fr, { x0: -5, x1: 32, y0: 0, y1: 5, xTicks: [0, 5, 10, 15, 20, 25, 30], yTicks: [2, 4], xLabel: '사용 시간(h)', yLabel: '사람 수' });
    for (const f of [lf, uf]) body += ln([[fr.X(f), fr.Y(5)], [fr.X(f), fr.Y(0)]], { stroke: 'var(--s2)', sw: 1.1, dash: '5 4' });
    add({ name: 'sta-eda-boxplot', svg: svg({ width: W, height: H, title: '상자그림의 다섯 수와 울타리', desc: '상자는 Q1 에서 Q3 까지, 가운데 굵은 선이 중앙값, 수염은 울타리 안 가장 먼 관측값까지 가고 울타리 밖 값 30 은 따로 찍힌다. 아래 히스토그램은 같은 자료다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-eda-log — 로그를 씌우면 '배' 가 '간격' 이 된다
 * ------------------------------------------------------------------ */
{
    const n = 15, sig = 0.5;
    const qs = Array.from({ length: n }, (_, i) => qnorm((i + 0.5) / n));
    const A = qs.map(z => 20 * Math.exp(sig * z));
    const B = qs.map(z => 60 * Math.exp(sig * z));
    const PW = 450, PH = 300, GAP = 16, W = 2 * PW + GAP + 20, H = PH + 20;
    let body = '';
    const strip = (fr, vals, y, col, name) => {
        let s = vals.map(v => pdot(fr.X(v), y, col, 4.2, 0.75)).join('');
        s += txt(fr.X(fr.xr[0]) + 2, y - 14, name, { cls: 'ink', size: 'sm' });
        return s;
    };
    // 원래 척도
    {
        const x0 = 10, y0 = 10;
        body += panel(x0, y0, PW, PH, '원래 척도', 'B 가 A 의 세 배 — 간격도 퍼짐도 B 쪽이 크다');
        const fr = frame({ xRange: [0, 160], yRange: [0, 1], box: { x: x0 + 30, y: y0 + 60, w: PW - 70, h: PH - 120 } });
        fr.xr = [0, 160];
        body += strip(fr, A, fr.Y(0.72), 'var(--s1)', `A 집단(중앙값 ${fmt(median(A), 0)})`);
        body += strip(fr, B, fr.Y(0.3), 'var(--s2)', `B 집단(중앙값 ${fmt(median(B), 0)})`);
        body += axesEdge(fr, { x0: 0, x1: 160, y0: 0, y1: 0, xTicks: [0, 40, 80, 120, 160], xLabel: '값(원래 단위)' }).replace(/<path class="ax"[^>]*V[^>]*\/>/, '');
        body += txt(x0 + 20, y0 + PH - 14, `범위: A ${fmt(Math.min(...A), 0)}–${fmt(Math.max(...A), 0)}, B ${fmt(Math.min(...B), 0)}–${fmt(Math.max(...B), 0)}`, { cls: 'ink2', size: 'sm' });
    }
    // 로그 척도
    {
        const x0 = 10 + PW + GAP, y0 = 10;
        body += panel(x0, y0, PW, PH, '자연로그 척도', '두 집단의 퍼짐이 같고 간격이 ln 3 하나로 요약된다');
        const fr = frame({ xRange: [2, 5.5], yRange: [0, 1], box: { x: x0 + 30, y: y0 + 60, w: PW - 70, h: PH - 120 } });
        fr.xr = [2, 5.5];
        const la = A.map(Math.log), lb = B.map(Math.log);
        body += strip(fr, la, fr.Y(0.72), 'var(--s1)', 'ln A');
        body += strip(fr, lb, fr.Y(0.3), 'var(--s2)', 'ln B');
        const ma = mean(la), mb = mean(lb);
        body += ln([[fr.X(ma), fr.Y(0.72) + 8], [fr.X(ma), fr.Y(0.3) - 8]], { sw: 1, dash: '3 3' });
        body += ln([[fr.X(mb), fr.Y(0.72) + 8], [fr.X(mb), fr.Y(0.3) - 8]], { sw: 1, dash: '3 3' });
        body += arw2(fr.X(ma), fr.Y(0.51), fr.X(mb), fr.Y(0.51), { width: 1.2 });
        body += txt((fr.X(ma) + fr.X(mb)) / 2, fr.Y(0.51) - 6, `간격 ${(mb - ma).toFixed(2)} = ln 3`, { anchor: 'middle', cls: 'ink', size: 'sm' });
        body += axesEdge(fr, { x0: 2, x1: 5.5, y0: 0, y1: 0, xTicks: [2, 3, 4, 5], xLabel: 'ln(값)' }).replace(/<path class="ax"[^>]*V[^>]*\/>/, '');
        body += txt(x0 + 20, y0 + PH - 14, `ln 평균을 되돌리면 기하평균: A ${fmt(Math.exp(ma), 1)}, B ${fmt(Math.exp(mb), 1)}`, { cls: 'ink2', size: 'sm' });
    }
    add({ name: 'sta-eda-log', svg: svg({ width: W, height: H, title: '로그를 씌우면 배 차이가 간격 차이가 된다', desc: '오른쪽으로 치우친 두 집단(B 가 A 의 세 배)을 원래 척도와 자연로그 척도로 찍었다. 로그 척도에서 두 집단은 대칭이고 퍼짐이 같으며 간격 ln 3 만큼 떨어져 있다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-eda-qq-four — Q-Q 그림 네 칸
 * ------------------------------------------------------------------ */
{
    const n = 40;
    const ps = Array.from({ length: n }, (_, i) => (i + 0.5) / n);
    const zq = ps.map(qnorm);
    const mixCdf = x => 0.5 * normCdf(x + 2) + 0.5 * normCdf(x - 2);
    const cases = [
        { title: '정상', sub: '정규분포 — 점이 직선 위에 선다', q: p => qnorm(p) },
        { title: '두꺼운 꼬리', sub: 't 분포(자유도 2) — 양끝이 직선 밖으로', q: p => (2 * p - 1) / Math.sqrt(2 * p * (1 - p)) },
        { title: '오른쪽 치우침', sub: '지수분포 — 아래로 볼록하게 휜다', q: p => -Math.log(1 - p) },
        { title: '봉우리 둘', sub: '정규 둘을 반씩 섞음 — 가운데가 가파르다', q: p => invert(mixCdf, p, -10, 10) },
    ];
    const PW = 360, PH = 290, GAP = 14, W = 2 * PW + GAP + 20, H = 2 * PH + GAP + 20;
    let body = '';
    cases.forEach((c, k) => {
        const x0 = 10 + (k % 2) * (PW + GAP), y0 = 10 + Math.floor(k / 2) * (PH + GAP);
        const yq = ps.map(c.q);
        // 기준선: 제1·제3 사분위수 두 점을 잇는다
        const q1t = qnorm(0.25), q3t = qnorm(0.75), q1s = c.q(0.25), q3s = c.q(0.75);
        const sl = (q3s - q1s) / (q3t - q1t), ic = q1s - sl * q1t;
        const ymin = Math.min(...yq, ic + sl * -2.4), ymax = Math.max(...yq, ic + sl * 2.4);
        const pad = (ymax - ymin) * 0.06;
        body += panel(x0, y0, PW, PH, c.title, c.sub);
        const fr = frame({ xRange: [-2.5, 2.5], yRange: [ymin - pad, ymax + pad], box: { x: x0 + 40, y: y0 + 56, w: PW - 66, h: PH - 102 } });
        body += box(fr.X(-2.5), fr.Y(ymax + pad), fr.X(2.5) - fr.X(-2.5), fr.Y(ymin - pad) - fr.Y(ymax + pad), { stroke: 'var(--grid)', sw: 1, rx: 0 });
        body += ln([[fr.X(-2.4), fr.Y(ic + sl * -2.4)], [fr.X(2.4), fr.Y(ic + sl * 2.4)]], { stroke: 'var(--s2)', sw: 1.4, dash: '6 4' });
        zq.forEach((z, i) => { body += pdot(fr.X(z), fr.Y(yq[i]), 'var(--s1)', 3, 0.85); });
        for (const t of [-2, -1, 0, 1, 2]) body += txt(fr.X(t), fr.Y(ymin - pad) + 15, neg(t), { anchor: 'middle', cls: 'ink2', size: 'sm' });
        body += txt(x0 + PW / 2, y0 + PH - 12, '가로: 표준정규 분위수, 세로: 자료의 분위수', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    });
    add({ name: 'sta-eda-qq-four', svg: svg({ width: W, height: H, title: 'Q-Q 그림 네 모양', desc: '정상 칸은 점이 직선 위에 있다. 두꺼운 꼬리 칸은 왼쪽 끝이 아래로, 오른쪽 끝이 위로 벗어난다. 오른쪽 치우침 칸은 아래로 볼록하게 휜다. 봉우리 둘 칸은 가운데가 가파르고 양끝이 눕는다. 점선은 사분위수 두 점을 이은 기준선이다', body }) });
}

export default figures;
