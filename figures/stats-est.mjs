/**
 * 통계학 7장(추정의 한계와 큰 표본)의 그림. 이름은 전부 `sta-est-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다).
 * - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 그리스 문자와 모자는 유니코드(θ̂ p̂ λ̂).
 * - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 * - lib 의 px() 에 cls 'ark' 를 주면 선이 사라진다. 화살표는 아래 arw() 로 색을 직접 준다.
 * - 난수를 쓰지 않는다. 분산·밀도·포함률은 모두 정확한 식(푸아송 적률, 감마밀도, 이항합)으로 계산한다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');

/* ---------------- 공통 소도구 ---------------- */

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
const pdot = (x, y, col = 'var(--s1)', r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;
function arw(x1, y1, x2, y2, { col = 'var(--ink2)', mk = 'ark', width = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg, xLabel, yLabel, yAt }) {
    const ya = yAt ?? y0;
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(ya)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) {
        out.push(ln([[fr.X(t), fr.Y(ya)], [fr.X(t), fr.Y(ya) + 4]], { sw: 1 }));
        out.push(txt(fr.X(t), fr.Y(ya) + 17, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (xLabel) out.push(txt(fr.X(x1) + 14, fr.Y(ya) + 4, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) + 6, fr.Y(y1) - 6, yLabel, { cls: 'ink2', size: 'sm' }));
    return out.join('');
}
function curveCol(fr, f, from, to, { col = 'var(--s1)', sw = 2.2, dash, steps = 240, yMin = -Infinity, yMax = Infinity } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        const y = f(x);
        if (y >= yMin && y <= yMax) pts.push([fr.X(x), fr.Y(y)]);
    }
    return ln(pts, { stroke: col, sw, dash });
}
const comb = (n, k) => { let r = 1; for (let i = 1; i <= k; i += 1) r = (r * (n - k + i)) / i; return r; };
const phi = z => Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
const lnFact = k => { let s = 0; for (let i = 2; i <= k; i += 1) s += Math.log(i); return s; };

/* ---------------- 1. 곡률과 정보 ---------------- */
{
    const ph = 0.3;
    const rel = n => p => n * (ph * Math.log(p / ph) + (1 - ph) * Math.log((1 - p) / (1 - ph)));
    const W = 720; const H = 380;
    const yr = [-4, 0.4];
    const fr = frame({ xRange: [0, 0.7], yRange: yr, box: { x: 80, y: 45, w: 540, h: 270 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 0.7, y0: yr[0], y1: yr[1], xTicks: [0.1, 0.2, 0.3, 0.4, 0.5, 0.6], yTicks: [-4, -3, -2, -1, 0], xLabel: 'p', yLabel: 'ℓ(p) − ℓ(p̂)' }));
    b.push(ln([[fr.X(0), fr.Y(0)], [fr.X(0.7), fr.Y(0)]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(curveCol(fr, rel(10), 0.01, 0.69, { col: 'var(--s1)', yMin: yr[0] }));
    b.push(curveCol(fr, rel(40), 0.01, 0.69, { col: 'var(--s2)', yMin: yr[0] }));
    const J = n => n / (ph * (1 - ph));
    b.push(txt(fr.X(0.55), fr.Y(-1.2), `n = 10: 곡률 ${J(10).toFixed(1)}`, { cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(0.38), fr.Y(-3.5), `n = 40: 곡률 ${J(40).toFixed(1)}`, { cls: 'ink2', size: 'sm' }));
    b.push(legend(110, 360, [{ slot: 1, name: '시행 10번 중 성공 3번' }]));
    b.push(legend(330, 360, [{ slot: 2, name: '시행 40번 중 성공 12번' }]));
    b.push(txt(fr.X(0.3), fr.Y(0) - 10, 'p̂ = 0.3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-est-curvature',
        svg: svg({ width: W, height: H, title: '꼭대기의 곡률이 정보다', desc: '같은 p̂ 에서 자료가 네 배면 로그가능도의 꼭대기가 네 배 뾰족하고, 그만큼 p 를 좁게 짚는다', body: b.join('') }),
    });
}

/* ---------------- 2. 크라메르-라오 하한과 세 추정량 (푸아송 λ = 2) ---------------- */
{
    const lam = 2;
    const vXbar = n => lam / n;
    const vS2 = n => lam / n + 2 * lam ** 2 / (n - 1);
    const vAvg = n => lam / n + lam ** 2 / (2 * (n - 1));
    const W = 720; const H = 400;
    const fr = frame({ xRange: [0, 42], yRange: [0, 20], box: { x: 80, y: 45, w: 540, h: 280 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 42, y0: 0, y1: 20, xTicks: [2, 10, 20, 30, 40], yTicks: [2, 5, 10, 15, 20], xLabel: 'n', yLabel: 'n × 분산' }));
    const series = (v, col) => { const pts = []; for (let n = 2; n <= 40; n += 1) pts.push([fr.X(n), fr.Y(n * v(n))]); return ln(pts, { stroke: col, sw: 2.2 }) + pts.map(([x, y]) => pdot(x, y, col, 2.4)).join(''); };
    b.push(ln([[fr.X(0), fr.Y(lam)], [fr.X(42), fr.Y(lam)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '6 4' }));
    b.push(series(vS2, 'var(--s2)'));
    b.push(series(vAvg, 'var(--s3)'));
    b.push(series(vXbar, 'var(--s1)'));
    b.push(txt(fr.X(41), fr.Y(lam) + 18, '하한 n × 1/(nI(λ)) = λ = 2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(41), fr.Y(40 * vS2(40)) - 10, `표본분산 S²: 2λ² 만큼 위로 (→ ${lam + 2 * lam ** 2})`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(41), fr.Y(40 * vAvg(40)) - 10, `둘의 평균 (x̄ + S²)/2 (→ ${lam + lam ** 2 / 2})`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(6), fr.Y(lam) - 10, '표본평균 x̄: 하한 위에 붙어 있다', { cls: 'ink bold', size: 'sm' }));
    b.push(legend(110, 370, [{ slot: 1, name: '표본평균' }]));
    b.push(legend(240, 370, [{ slot: 3, name: '둘의 평균' }]));
    b.push(legend(370, 370, [{ slot: 2, name: '표본분산' }]));
    add({
        name: 'sta-est-crbound',
        svg: svg({ width: W, height: H, title: '푸아송 λ 의 세 불편추정량과 크라메르-라오 하한', desc: '세 추정량 모두 불편이지만 하한에 붙는 것은 표본평균뿐이고 나머지 둘은 표본을 늘려도 하한의 몇 배로 남는다. λ = 2', body: b.join('') }),
    });
}

/* ---------------- 3. 지수분포 MLE 의 정확한 분포가 정규로 ---------------- */
{
    // λ̂ = n/G, G ~ 감마(모양 n, 비율 1)·(1/λ). λ = 1 로 두고 Z = (λ̂ − 1)√n.
    const dens = n => z => {
        const y = 1 + z / Math.sqrt(n);
        if (y <= 0) return 0;
        const g = n / y;
        const lf = (n - 1) * Math.log(g) - g - lnFact(n - 1);
        return Math.exp(lf) * (n / (y * y)) / Math.sqrt(n);
    };
    const W = 780; const H = 330;
    const b = [];
    [5, 20, 100].forEach((n, k) => {
        const px0 = 10 + k * 257;
        b.push(panel(px0, 10, 245, 300, `n = ${n}`, null));
        const fr = frame({ xRange: [-3, 4], yRange: [0, 0.55], box: { x: px0 + 20, y: 50, w: 200, h: 200 } });
        b.push(axesEdge(fr, { x0: -3, x1: 4, y0: 0, y1: 0.55, xTicks: [-2, 0, 2, 4] }));
        b.push(curveCol(fr, phi, -3, 4, { col: 'var(--ink2)', sw: 1.6, dash: '5 4' }));
        b.push(curveCol(fr, dens(n), -3, 4, { col: 'var(--s1)', steps: 400 }));
        // 꼬리 넓이: Z > 1.96 와 Z < -1.96 를 수치적분
        const f = dens(n); let lo = 0; let hi = 0; const h = 0.001;
        for (let z = -40; z < -1.96; z += h) lo += f(z + h / 2) * h;
        for (let z = 1.96; z < 60; z += h) hi += f(z + h / 2) * h;
        b.push(txt(px0 + 122, 282, `Z 가 −1.96 아래 ${(lo * 100).toFixed(1)}%`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(px0 + 122, 298, `Z 가 1.96 위 ${(hi * 100).toFixed(1)}%`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    b.push(legend(250, 323, [{ slot: 1, name: 'Z = (λ̂ − λ)√n / λ 의 정확한 밀도' }]));
    b.push(txt(560, 320, '점선: 표준정규', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-est-mle-normal',
        svg: svg({ width: W, height: H + 10, title: '지수분포 발생률 MLE 의 정확한 분포', desc: '표준화한 MLE 의 정확한 분포가 n = 5 에서는 오른쪽으로 치우쳤다가 n 이 커지며 표준정규에 붙는다', body: b.join('') }),
    });
}

/* ---------------- 4. 델타 방법: 접선이 흩어짐을 늘이고 줄인다 ---------------- */
{
    const g = t => Math.log(t);
    const W = 720; const H = 420;
    const fr = frame({ xRange: [0, 4], yRange: [-1.4, 1.6], box: { x: 90, y: 40, w: 540, h: 320 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 4, y0: -1.4, y1: 1.6, yAt: -1.4, xTicks: [0.5, 1, 1.5, 2, 2.5, 3, 3.5], yTicks: [-1, -0.5, 0, 0.5, 1, 1.5], xLabel: 'θ', yLabel: 'g(θ) = ln θ' }));
    b.push(curveCol(fr, g, 0.25, 4, { col: 'var(--s1)', yMin: -1.4 }));
    const sd = 0.15;
    [[0.6, 'var(--s2)', '기울기 1/0.6 ≈ 1.67: 흩어짐이 늘어난다'], [2.5, 'var(--s3)', '기울기 1/2.5 = 0.4: 흩어짐이 준다']].forEach(([t0, col, lab], k) => {
        const s = 1 / t0;
        const a = t0 - sd; const c = t0 + sd;
        const ya = g(t0) - s * sd; const yc = g(t0) + s * sd;
        // 접선
        b.push(curveCol(fr, t => g(t0) + s * (t - t0), Math.max(0.05, t0 - 0.45), t0 + 0.45, { col, sw: 1.6, dash: '6 4', yMin: -1.4 }));
        // 가로축 위 띠
        b.push(box(fr.X(a), fr.Y(-1.4) - 8, fr.X(c) - fr.X(a), 8, { fill: col, op: 0.6, stroke: col, sw: 1, rx: 1 }));
        // 세로축 옆 띠
        b.push(box(fr.X(0), fr.Y(yc), 8, fr.Y(ya) - fr.Y(yc), { fill: col, op: 0.6, stroke: col, sw: 1, rx: 1 }));
        // 투영선
        for (const [tx, ty] of [[a, ya], [c, yc]]) {
            b.push(ln([[fr.X(tx), fr.Y(-1.4)], [fr.X(tx), fr.Y(ty)], [fr.X(0), fr.Y(ty)]], { stroke: col, sw: 1, dash: '2 3' }));
        }
        b.push(pdot(fr.X(t0), fr.Y(g(t0)), col, 4));
        b.push(txt(fr.X(t0) + 12, fr.Y(g(t0)) + (k === 0 ? 34 : -14), lab, { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(fr.X(2), fr.Y(-1.4) + 36, '가로축의 띠: θ̂ 의 흩어짐(두 자리 모두 같은 폭 ±0.15)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(0) + 14, fr.Y(1.45), '세로축의 띠: g(θ̂) 의 흩어짐', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-est-delta',
        svg: svg({ width: W, height: H, title: '델타 방법', desc: '좁은 범위에서 g 를 접선으로 바꾸면 g(θ̂) 의 흩어짐은 θ̂ 의 흩어짐에 접선 기울기를 곱한 것이다', body: b.join('') }),
    });
}

/* ---------------- 5. 왈드 구간과 윌슨 구간의 정확 포함률 (n = 30) ---------------- */
{
    const n = 30; const z = 1.959964;
    const wald = x => { const p = x / n; const h = z * Math.sqrt(p * (1 - p) / n); return [p - h, p + h]; };
    const wilson = x => {
        const p = x / n; const den = 1 + z * z / n;
        const c = (p + z * z / (2 * n)) / den;
        const h = (z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))) / den;
        return [c - h, c + h];
    };
    const W1 = Array.from({ length: n + 1 }, (_, x) => wald(x));
    const S1 = Array.from({ length: n + 1 }, (_, x) => wilson(x));
    const cov = (ints, p) => {
        let s = 0;
        for (let x = 0; x <= n; x += 1) if (ints[x][0] <= p && p <= ints[x][1]) s += comb(n, x) * p ** x * (1 - p) ** (n - x);
        return s;
    };
    const W = 740; const H = 400;
    const fr = frame({ xRange: [0, 1], yRange: [0, 1], box: { x: 80, y: 45, w: 560, h: 280 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 1, y0: 0, y1: 1, xTicks: [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1], yTicks: [0.2, 0.4, 0.6, 0.8, 0.95], xLabel: 'p', yLabel: '정확 포함률' }));
    b.push(ln([[fr.X(0), fr.Y(0.95)], [fr.X(1), fr.Y(0.95)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
    const trace = (ints, col) => {
        const pts = [];
        for (let i = 2; i <= 998; i += 1) { const p = i / 1000; const c = cov(ints, p); pts.push([fr.X(p), fr.Y(c)]); }
        return ln(pts, { stroke: col, sw: 1.3 });
    };
    b.push(trace(W1, 'var(--s2)'));
    b.push(trace(S1, 'var(--s1)'));
    const c05 = cov(W1, 0.05); const s05 = cov(S1, 0.05);
    b.push(arw(fr.X(0.16), fr.Y(0.5), fr.X(0.055), fr.Y(c05) + 4, { col: 'var(--s2)', mk: 'ar2' }));
    b.push(txt(fr.X(0.165), fr.Y(0.5) + 4, `p = 0.05 에서 왈드 ${(c05 * 100).toFixed(1)}%, 윌슨 ${(s05 * 100).toFixed(1)}%`, { cls: 'ink2', size: 'sm' }));
    b.push(legend(120, 365, [{ slot: 2, name: '왈드 구간 p̂ ± 1.96 √(p̂(1 − p̂)/n)' }]));
    b.push(legend(390, 365, [{ slot: 1, name: '윌슨 구간 (8장)' }]));
    b.push(txt(530, 363, '점선: 명목 95%', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-est-wald-coverage',
        svg: svg({ width: W, height: H, title: 'n = 30 에서 비율 구간의 정확 포함률', desc: '왈드 구간은 p 가 0 이나 1 에 가까울수록 포함률이 명목 95% 아래로 크게 떨어지고, 윌슨 구간은 대체로 95% 근처에 머문다. 이항확률의 합으로 계산', body: b.join('') }),
    });
}

export default figures;
