/**
 * 통계학 13장(범주형 자료) 그림. 이름은 전부 `sta-cat-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다):
 * - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `p~2` 표기, 나머지는 유니코드.
 * - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 * - px() 에 cls 'ark'·'ar1' 을 주면 선이 사라진다 — 이 모듈은 색을 직접 넣는 arw() 를 쓴다.
 * - 색만으로 구분하지 않고 이름을 함께 적는다. 그림 안에 한자를 쓰지 않는다.
 * - 그림 안의 숫자(구간 끝점, 확률, 오즈비)는 모두 이 자리에서 계산한다. 난수는 쓰지 않는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');
const f3 = v => neg(v.toFixed(3));
const f2 = v => neg(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' };
const MK = { s1: 'ar1', s2: 'ar2', s3: 'ar3', ark: 'ark' };

function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.8, both = false } = {}) {
    const mk = MK[cls];
    return `<path fill="none" stroke="${COL[cls]}" stroke-width="${width}" stroke-linecap="round"${both ? ` marker-start="url(#${mk})"` : ''} marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = 'var(--s1)', r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 가로축만. */
function xaxis(fr, x0, x1, ticks, { fmt = neg, y0 = 0 } = {}) {
    const y = fr.Y(y0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { sw: 1 }));
        out.push(txt(fr.X(t), y + 18, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 세로축을 왼쪽 가장자리에. */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 17, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) {
        out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
        out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/* 이항·초기하 계산 */
const comb = (n, k) => {
    if (k < 0 || k > n) return 0;
    let r = 1;
    for (let i = 1; i <= k; i += 1) r = (r * (n - k + i)) / i;
    return r;
};
const binomCdf = (k, n, p) => {
    let s = 0;
    for (let i = 0; i <= k; i += 1) s += comb(n, i) * p ** i * (1 - p) ** (n - i);
    return s;
};
function bisect(f, lo, hi) {
    for (let i = 0; i < 200; i += 1) {
        const m = (lo + hi) / 2;
        if (f(lo) * f(m) <= 0) hi = m; else lo = m;
    }
    return (lo + hi) / 2;
}

/* ------------------------------------------------------------------ *
 * sta-cat-intervals — 3/20 에 세 구간
 * ------------------------------------------------------------------ */
{
    const x = 3, n = 20, z = 1.959964, ph = x / n;
    const se = Math.sqrt((ph * (1 - ph)) / n);
    const wald = [ph - z * se, ph + z * se];
    const c = (x + (z * z) / 2) / (n + z * z);
    const h = (z * Math.sqrt(n * ph * (1 - ph) + (z * z) / 4)) / (n + z * z);
    const wil = [c - h, c + h];
    const cp = [
        bisect(p => 1 - binomCdf(x - 1, n, p) - 0.025, 1e-9, ph),
        bisect(p => binomCdf(x, n, p) - 0.025, ph, 1 - 1e-9),
    ];
    const W = 800, H = 300;
    const fr = frame({ xRange: [-0.08, 0.45], yRange: [0, 4], box: { x: 40, y: 50, w: 540, h: 190 } });
    const rows = [
        { name: '왈드', iv: wald, cls: 's2', y: 3.1 },
        { name: '윌슨', iv: wil, cls: 's1', y: 2.0, center: c },
        { name: '클로퍼-피어슨', iv: cp, cls: 's3', y: 0.9 },
    ];
    let body = '';
    // 0 아래 칠하기
    body += box(fr.X(-0.08), fr.Y(4), fr.X(0) - fr.X(-0.08), fr.Y(0) - fr.Y(4), { fill: 'var(--ink2)', op: 0.12, stroke: 'none', rx: 0 });
    body += txt(fr.X(-0.04), fr.Y(3.75), '비율이', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(-0.04), fr.Y(3.75) + 14, '될 수 없는 곳', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += xaxis(fr, -0.08, 0.45, [0, 0.1, 0.2, 0.3, 0.4]);
    body += ln([[fr.X(0), fr.Y(0)], [fr.X(0), fr.Y(4)]], { sw: 1.4, dash: '5 3' });
    body += ln([[fr.X(ph), fr.Y(0)], [fr.X(ph), fr.Y(3.7)]], { sw: 1, dash: '2 3' });
    body += txt(fr.X(ph) + 4, fr.Y(3.7) + 4, 'p̂ = 0.15', { cls: 'ink2', size: 'sm' });
    for (const r of rows) {
        const y = fr.Y(r.y);
        body += ln([[fr.X(r.iv[0]), y], [fr.X(r.iv[1]), y]], { stroke: COL[r.cls], sw: 4 });
        body += ln([[fr.X(r.iv[0]), y - 8], [fr.X(r.iv[0]), y + 8]], { stroke: COL[r.cls], sw: 2 });
        body += ln([[fr.X(r.iv[1]), y - 8], [fr.X(r.iv[1]), y + 8]], { stroke: COL[r.cls], sw: 2 });
        body += pdot(fr.X(ph), y, 'var(--ink)', 4);
        if (r.center) body += `<circle cx="${fr.X(r.center)}" cy="${y}" r="5" fill="none" stroke="var(--ink)" stroke-width="1.5"/>`;
        body += txt(fr.X(r.iv[1]) + 10, y + 4, `${r.name}  (${f3(r.iv[0])}, ${f3(r.iv[1])})`, { cls: 'ink' });
    }
    body += pdot(fr.X(0.3), H - 16, 'var(--ink)', 4);
    body += txt(fr.X(0.3) + 8, H - 12, '= p̂', { cls: 'ink2', size: 'sm' });
    body += `<circle cx="${r2(fr.X(0.36))}" cy="${H - 16}" r="5" fill="none" stroke="var(--ink)" stroke-width="1.5"/>`;
    body += txt(fr.X(0.36) + 9, H - 12, '= 윌슨 구간의 가운데', { cls: 'ink2', size: 'sm' });
    body += txt(fr.X(0.45) + 16, fr.Y(0) + 4, '모비율 p', { cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-cat-intervals',
        svg: svg({ width: W, height: H, title: '20번 중 3번 성공한 자료에 세 가지 95% 구간', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-cat-measures — 같은 오즈비, 기저 위험에 따른 위험비
 * ------------------------------------------------------------------ */
{
    const W = 720, H = 340;
    const fr = frame({ xRange: [0, 1], yRange: [0, 4.6], box: { x: 70, y: 40, w: 520, h: 260 } });
    const rr = (or, p2) => or / (1 - p2 + p2 * or);
    let body = axesEdge(fr, { x0: 0, x1: 1, y0: 0, y1: 4.6, xTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [1, 2, 3, 4] });
    body += txt(fr.X(1) + 16, fr.Y(0) + 4, '기저 위험 p~2', { cls: 'ink2', size: 'sm' });
    body += txt(fr.X(0) - 6, fr.Y(4.6) - 16, '위험비', { anchor: 'end', cls: 'ink2', size: 'sm' });
    for (const [or, cls] of [[4, 's2'], [2, 's1']]) {
        body += ln([[fr.X(0), fr.Y(or)], [fr.X(1), fr.Y(or)]], { stroke: COL[cls], sw: 1.2, dash: '5 4' });
        body += fr.curve(p => rr(or, p), { from: 0, to: 1, cls });
        body += txt(fr.X(1) + 8, fr.Y(or) + 4, `오즈비 ${or} (점선)`, { cls: 'ink', size: 'sm' });
        const lx = cls === 's2' ? 0.2 : 0.6;
        body += txt(fr.X(lx) + (cls === 's2' ? 8 : 0), fr.Y(rr(or, lx)) + (cls === 's2' ? -10 : 22), `오즈비 ${or} 의 위험비 곡선`, { cls: 'ink', size: 'sm' });
    }
    // 대표 점
    for (const [or, p2, cls, dy] of [[2, 0.05, 's1', 18], [2, 0.4, 's1', 18], [4, 0.05, 's2', -10], [4, 0.4, 's2', -10]]) {
        const v = rr(or, p2);
        body += pdot(fr.X(p2), fr.Y(v), COL[cls], 4.5);
        body += txt(fr.X(p2) + 7, fr.Y(v) + dy, `${f2(v)}`, { cls: 'ink', size: 'sm' });
    }
    body += txt(fr.X(0.62), fr.Y(3.3), '드문 결과(p~2 가 0 에 가까움): 위험비 ≈ 오즈비', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(0.62), fr.Y(3.3) + 16, '흔한 결과일수록 위험비가 1 쪽으로 내려온다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-cat-measures',
        svg: svg({ width: W, height: H, title: '오즈비를 고정하고 기저 위험을 바꿀 때의 위험비', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-cat-logor — 오즈비 구간: 로그 축과 원래 축
 * ------------------------------------------------------------------ */
{
    const a = 30, b = 170, c = 10, d = 190;
    const or = (a * d) / (b * c);
    const se = Math.sqrt(1 / a + 1 / b + 1 / c + 1 / d);
    const lo = Math.log(or) - 1.96 * se, hi = Math.log(or) + 1.96 * se;
    const W = 760, H = 250;
    let body = '';
    // 왼쪽 패널: 로그 축
    body += panel(10, 10, 360, 230, '로그 축: ln(오즈비)', '구간이 점추정값을 가운데 두고 대칭');
    const g1 = frame({ xRange: [0, 2.4], yRange: [0, 1], box: { x: 40, y: 70, w: 290, h: 120 } });
    body += xaxis(g1, 0, 2.4, [0, 0.5, 1, 1.5, 2]);
    const y1 = g1.Y(0.55);
    body += ln([[g1.X(lo), y1], [g1.X(hi), y1]], { stroke: COL.s1, sw: 4 });
    body += pdot(g1.X(Math.log(or)), y1, 'var(--ink)', 5);
    body += txt(g1.X(Math.log(or)), y1 - 14, f2(Math.log(or)), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g1.X(lo), y1 + 22, f2(lo), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g1.X(hi), y1 + 22, f2(hi), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g1.X((lo + Math.log(or)) / 2), y1 + 40, `−${f2(1.96 * se)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(g1.X((hi + Math.log(or)) / 2), y1 + 40, `+${f2(1.96 * se)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    // 오른쪽 패널: 원래 축
    body += panel(390, 10, 360, 230, '원래 축: 오즈비', '양 끝을 지수로 되돌리면 오른쪽이 길다');
    const g2 = frame({ xRange: [0, 8], yRange: [0, 1], box: { x: 420, y: 70, w: 290, h: 120 } });
    body += xaxis(g2, 0, 8, [0, 1, 2, 3, 4, 5, 6, 7]);
    body += ln([[g2.X(1), g2.Y(0)], [g2.X(1), g2.Y(1)]], { sw: 1, dash: '4 3' });
    body += txt(g2.X(1) + 4, g2.Y(1) + 4, '1 = 연관 없음', { cls: 'ink2', size: 'sm' });
    const y2 = g2.Y(0.55);
    body += ln([[g2.X(Math.exp(lo)), y2], [g2.X(Math.exp(hi)), y2]], { stroke: COL.s1, sw: 4 });
    body += pdot(g2.X(or), y2, 'var(--ink)', 5);
    body += txt(g2.X(or), y2 - 14, f2(or), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g2.X(Math.exp(lo)), y2 + 22, f2(Math.exp(lo)), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g2.X(Math.exp(hi)), y2 + 22, f2(Math.exp(hi)), { anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(g2.X((Math.exp(lo) + or) / 2), y2 + 40, `−${f2(or - Math.exp(lo))}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(g2.X((Math.exp(hi) + or) / 2), y2 + 40, `+${f2(Math.exp(hi) - or)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-cat-logor',
        svg: svg({ width: W, height: H, title: '오즈비 95% 구간을 두 축에서', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-cat-fisher — 여백 고정 표 전부와 그 확률
 * ------------------------------------------------------------------ */
{
    const n1 = 8, n2 = 7, m = 9, N = 15, obs = 7;
    const tot = comb(N, m);
    const pts = [];
    for (let a = Math.max(0, m - n2); a <= Math.min(n1, m); a += 1) pts.push([a, (comb(n1, a) * comb(n2, m - a)) / tot]);
    const pObs = pts.find(p => p[0] === obs)[1];
    const inP = p => p[1] <= pObs * (1 + 1e-9);
    const pv = pts.filter(inP).reduce((s, p) => s + p[1], 0);
    const W = 740, H = 380;
    const fr = frame({ xRange: [1.3, 8.7], yRange: [0, 0.45], box: { x: 70, y: 50, w: 560, h: 220 } });
    let body = axesEdge(fr, { x0: 1.3, x1: 8.7, y0: 0, y1: 0.45, yTicks: [0.1, 0.2, 0.3, 0.4] });
    body += txt(fr.X(1.3) - 6, fr.Y(0.45) - 16, '확률', { anchor: 'end', cls: 'ink2', size: 'sm' });
    const hw = (fr.X(1) - fr.X(0)) * 0.34;
    for (const [a, p] of pts) {
        const col = inP([a, p]) ? COL.s2 : COL.s1;
        body += box(fr.X(a) - hw, fr.Y(p), 2 * hw, fr.Y(0) - fr.Y(p), { fill: col, op: inP([a, p]) ? 0.75 : 0.3, stroke: col, sw: 1.2, rx: 1.5 });
        body += txt(fr.X(a), fr.Y(p) - 6, p.toFixed(4), { anchor: 'middle', cls: 'ink', size: 'sm' });
        // 표의 네 칸: 새 처리(개선, 미개선) / 기존 처리(개선, 미개선)
        body += txt(fr.X(a), fr.Y(0) + 18, `a = ${a}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
        body += txt(fr.X(a), fr.Y(0) + 34, `${a}  ${n1 - a}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        body += txt(fr.X(a), fr.Y(0) + 48, `${m - a}  ${n2 - (m - a)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    }
    body += txt(fr.X(obs), fr.Y(pObs) - 24, '관측된 표', { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    body += txt(fr.X(1.3) - 6, fr.Y(0) + 34, '새 처리', { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(1.3) - 6, fr.Y(0) + 48, '기존 처리', { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += legend(fr.X(8.7) - 150, 62, [{ slot: 2, name: '관측보다 드문 표(p값에 넣음)' }, { slot: 1, name: '관측보다 흔한 표' }]);
    body += txt(W / 2, H - 14, `여백 고정: 행 합 8·7, 열 합 9·6.  양쪽 p값 = 진한 막대의 합 = ${pv.toFixed(4)}`, { anchor: 'middle', cls: 'ink' });
    add({
        name: 'sta-cat-fisher',
        svg: svg({ width: W, height: H, title: '여백을 고정한 2×2 표 일곱 개와 각 확률', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-cat-strata — 층마다 오즈비 2, 합치면 다르다
 * ------------------------------------------------------------------ */
{
    // [처리 회복, 처리 미회복, 대조 회복, 대조 미회복]
    const S = [
        { name: '경증 층', t: [64, 16], c: [20, 10] },
        { name: '중증 층', t: [10, 20], c: [18, 72] },
    ];
    const pool = { name: '두 층을 합친 표', t: [74, 36], c: [38, 82] };
    const ORof = s => (s.t[0] * s.c[1]) / (s.t[1] * s.c[0]);
    const W = 780, H = 330;
    let body = '';
    [...S, pool].forEach((s, i) => {
        const x0 = 10 + i * 258;
        body += panel(x0, 10, 248, 310, s.name, `오즈비 ${ORof(s).toFixed(2)}`);
        const fr = frame({ xRange: [0, 2], yRange: [0, 1], box: { x: x0 + 40, y: 70, w: 190, h: 170 } });
        body += axesEdge(fr, { x0: 0, x1: 2, y0: 0, y1: 1, yTicks: [0.25, 0.5, 0.75, 1], fmtY: v => v.toFixed(2) });
        [['처리', s.t, 's1', 0.55], ['대조', s.c, 's2', 1.45]].forEach(([lab, cnt, cls, xc]) => {
            const n = cnt[0] + cnt[1], p = cnt[0] / n;
            body += box(fr.X(xc - 0.3), fr.Y(p), fr.X(0.6) - fr.X(0), fr.Y(0) - fr.Y(p), { fill: COL[cls], op: 0.55, stroke: COL[cls], sw: 1.2, rx: 1.5 });
            body += txt(fr.X(xc), fr.Y(p) - 6, `${cnt[0]}/${n}`, { anchor: 'middle', cls: 'ink', size: 'sm' });
            body += txt(fr.X(xc), fr.Y(0) + 17, lab, { anchor: 'middle', cls: 'ink', size: 'sm' });
        });
        const nt = s.t[0] + s.t[1], nc = s.c[0] + s.c[1];
        body += txt(x0 + 124, 290, `처리받은 사람 ${nt}/${nt + nc}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        body += txt(x0 + 124, 306, '막대 높이 = 회복 비율', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    });
    add({
        name: 'sta-cat-strata',
        svg: svg({ width: W, height: H, title: '두 층의 오즈비는 모두 2인데 합친 표의 오즈비는 4.4', body }),
    });
}

export default figures;
