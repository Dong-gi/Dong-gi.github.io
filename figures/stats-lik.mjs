/**
 * 통계학 6장(가능도)의 그림. 이름은 전부 `sta-lik-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다).
 * - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 그리스 문자와 모자는 유니코드(θ̂ p̂ λ̂).
 * - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 * - lib 의 px() 에 cls 'ark' 를 주면 선이 사라진다. 화살표는 아래 arw() 로 색을 직접 준다.
 * - 그림 안 숫자(구간 끝, p값, 뉴턴 걸음)는 모두 이 파일에서 계산해 넣는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const f3 = v => v.toFixed(3);
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
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';
const odot = (x, y, col = 'var(--s2)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="var(--bgfix)" stroke="${col}" stroke-width="1.6"/>`;
function arw(x1, y1, x2, y2, { col = 'var(--ink2)', mk = 'ark', width = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}
/** 축: 가로는 y0 높이에, 세로는 x0 자리에. */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg, xLabel, yLabel }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) {
        out.push(ln([[fr.X(t), fr.Y(y0)], [fr.X(t), fr.Y(y0) + 4]], { sw: 1 }));
        out.push(txt(fr.X(t), fr.Y(y0) + 17, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (xLabel) out.push(txt(fr.X(x1) + 14, fr.Y(y0) + 4, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) + 6, fr.Y(y1) - 6, yLabel, { cls: 'ink2', size: 'sm' }));
    return out.join('');
}
function curveCol(fr, f, from, to, { col = 'var(--s1)', sw = 2.2, dash, steps = 240 } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    return ln(pts, { stroke: col, sw, dash });
}
function bisect(f, a, b) {
    let fa = f(a);
    for (let i = 0; i < 200; i += 1) {
        const m = (a + b) / 2;
        const fm = f(m);
        if ((fa > 0) === (fm > 0)) { a = m; fa = fm; } else b = m;
    }
    return (a + b) / 2;
}
const comb = (n, k) => { let r = 1; for (let i = 1; i <= k; i += 1) r = (r * (n - k + i)) / i; return r; };

/* ---------------- 1. 상대가능도: n=10 과 n=100 ---------------- */
{
    const relB = (n, x) => p => Math.exp(x * Math.log(p / (x / n)) + (n - x) * Math.log((1 - p) / (1 - x / n)));
    const R10 = relB(10, 3);
    const R100 = relB(100, 30);
    const cut = 1 / 8;
    const end = R => [bisect(p => R(p) - cut, 1e-6, 0.3), bisect(p => R(p) - cut, 0.3, 1 - 1e-6)];
    const i10 = end(R10);
    const i100 = end(R100);
    const W = 720; const H = 380;
    const fr = frame({ xRange: [0, 1], yRange: [0, 1.1], box: { x: 70, y: 40, w: 560, h: 270 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 1, y0: 0, y1: 1.1, xTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [0.5, 1], xLabel: 'p', yLabel: '상대가능도 L(p)/L(p̂)' }));
    b.push(ln([[fr.X(0), fr.Y(cut)], [fr.X(1), fr.Y(cut)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
    b.push(txt(fr.X(1) - 4, fr.Y(cut) - 6, '1/8', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(curveCol(fr, R10, 0.001, 0.999, { col: 'var(--s1)' }));
    b.push(curveCol(fr, R100, 0.05, 0.6, { col: 'var(--s2)' }));
    // 구간 막대: 축 아래 두 줄
    const yA = fr.Y(0) + 34; const yB = fr.Y(0) + 52;
    b.push(ln([[fr.X(i10[0]), yA], [fr.X(i10[1]), yA]], { stroke: 'var(--s1)', sw: 4 }));
    b.push(ln([[fr.X(i100[0]), yB], [fr.X(i100[1]), yB]], { stroke: 'var(--s2)', sw: 4 }));
    b.push(txt(fr.X(i10[1]) + 8, yA + 4, `n = 10: ${f3(i10[0])} – ${f3(i10[1])}`, { cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(i100[1]) + 8, yB + 4, `n = 100: ${f3(i100[0])} – ${f3(i100[1])}`, { cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(0) - 6, yA + 4, '1/8 구간', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(legend(470, 80, [{ slot: 1, name: '시행 10번 중 성공 3번' }, { slot: 2, name: '시행 100번 중 성공 30번' }]));
    add({
        name: 'sta-lik-relative',
        svg: svg({ width: W, height: H, title: '같은 비율 0.3 의 두 상대가능도', desc: '표본비율이 같아도 시행이 열 배면 상대가능도가 훨씬 좁다', body: b.join('') }),
    });
}

/* ---------------- 2. 균등분포의 가능도: 최댓값에서 끊긴다 ---------------- */
{
    const xs = [0.2, 0.4, 0.5, 0.6, 2.8];
    const mx = Math.max(...xs);
    const n = xs.length;
    const R = t => (t < mx ? 0 : (mx / t) ** n);
    const W = 720; const H = 360;
    const fr = frame({ xRange: [0, 7], yRange: [0, 1.15], box: { x: 70, y: 40, w: 570, h: 250 } });
    const b = [BG];
    b.push(axesEdge(fr, { x0: 0, x1: 7, y0: 0, y1: 1.15, xTicks: [0, 1, 2, 3, 4, 5, 6, 7], yTicks: [0.5, 1], xLabel: 'θ', yLabel: '상대가능도 L(θ)/L(θ̂)' }));
    b.push(ln([[fr.X(0), fr.Y(0)], [fr.X(mx), fr.Y(0)]], { stroke: 'var(--s1)', sw: 3 }));
    b.push(curveCol(fr, R, mx, 7, { col: 'var(--s1)' }));
    b.push(ln([[fr.X(mx), fr.Y(0)], [fr.X(mx), fr.Y(1)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    b.push(odot(fr.X(mx), fr.Y(0), 'var(--s1)'));
    b.push(pdot(fr.X(mx), fr.Y(1), 'var(--s1)', 4.5));
    for (const x of xs) b.push(`<path d="M${fr.X(x)} ${fr.Y(0) - 7} V${fr.Y(0) + 0}" stroke="var(--s2)" stroke-width="2.4"/>`);
    b.push(txt(fr.X(0.5), fr.Y(0) - 14, '관측값 다섯 개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(mx) + 10, fr.Y(1) + 4, `θ̂ = 최댓값 ${mx}`, { cls: 'ink bold' }));
    b.push(txt(fr.X(mx) + 10, fr.Y(1) + 22, '이 자리의 기울기는 0 이 아니다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(1.4), fr.Y(0.42), 'θ 가 2.8 보다 작으면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(1.4), fr.Y(0.42) + 16, '2.8 이 나올 수 없다 → 가능도 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(5.2), fr.Y(0.62), 'θ 가 커질수록 (2.8/θ)⁵ 로 줄어든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-lik-uniform',
        svg: svg({ width: W, height: H, title: '균등분포 U(0, θ) 의 가능도', desc: '가능도는 관측 최댓값에서 0 에서 꼭대기로 뛰어오르고 그 뒤로 줄기만 하므로 기울기가 0 인 자리가 없다', body: b.join('') }),
    });
}

/* ---------------- 3. 뉴턴법 두 걸음 (영이 빠진 푸아송) ---------------- */
{
    const n = 10; const s = 20;
    const l = L => s * Math.log(L) - n * L - n * Math.log(1 - Math.exp(-L));
    const d1 = L => s / L - n / (1 - Math.exp(-L));
    const d2 = L => { const e = Math.exp(-L); return -s / L ** 2 + n * e / (1 - e) ** 2; };
    const L0 = 2; const L1 = L0 - d1(L0) / d2(L0); const L2 = L1 - d1(L1) / d2(L1);
    const Lhat = bisect(d1, 0.5, 3);
    const quad = a => L => l(a) + d1(a) * (L - a) + 0.5 * d2(a) * (L - a) ** 2;
    const xr = [1, 2.4]; const yr = [-5.6, -4.2];
    const W = 780; const H = 430;
    const b = [];
    /** y 범위 안에 드는 부분만 잇는다. */
    const clipped = (fr, f, col, dash) => {
        const pts = [];
        for (let i = 0; i <= 240; i += 1) {
            const x = xr[0] + ((xr[1] - xr[0]) * i) / 240;
            const y = f(x);
            if (y >= yr[0] && y <= yr[1]) pts.push([fr.X(x), fr.Y(y)]);
        }
        return ln(pts, { stroke: col, sw: 2.2, dash });
    };
    const steps = [[L0, L1, '첫 걸음', 'λ~0 = 2 에서 포물선을 댄다'], [L1, L2, '둘째 걸음', `λ~1 = ${L1.toFixed(3)} 에서 다시 댄다`]];
    steps.forEach(([a, nx, title, sub], k) => {
        const px0 = 15 + k * 385;
        b.push(panel(px0, 10, 370, 370, title, sub));
        const fr = frame({ xRange: xr, yRange: yr, box: { x: px0 + 60, y: 70, w: 270, h: 250 } });
        b.push(axesEdge(fr, { x0: xr[0], x1: xr[1], y0: yr[0], y1: yr[1], xTicks: [1.2, 1.6, 2, 2.4], yTicks: [-5.5, -5, -4.5], xLabel: 'λ', yLabel: 'ℓ(λ)' }));
        const q = quad(a);
        b.push(clipped(fr, q, 'var(--s2)', '6 4'));
        b.push(clipped(fr, l, 'var(--s1)'));
        b.push(ln([[fr.X(nx), fr.Y(q(nx))], [fr.X(nx), fr.Y(yr[0])]], { stroke: 'var(--s2)', sw: 1, dash: '3 3' }));
        b.push(pdot(fr.X(a), fr.Y(l(a)), 'var(--ink2)', 4.5));
        b.push(pdot(fr.X(nx), fr.Y(q(nx)), 'var(--s2)', 4.5));
        b.push(txt(fr.X(nx) - 5, fr.Y(yr[0]) - 8, `${k === 0 ? 'λ~1' : 'λ~2'} = ${nx.toFixed(3)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        b.push(txt(fr.X(a) + 8, fr.Y(l(a)) + 4, k === 0 ? '출발점' : '', { cls: 'ink2', size: 'sm' }));
        b.push(txt(fr.X(nx) + 8, fr.Y(q(nx)) - 8, '포물선의 꼭짓점', { cls: 'ink2', size: 'sm' }));
        b.push(txt(px0 + 185, 366, `참 최댓값 λ̂ = ${Lhat.toFixed(4)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    b.push(legend(250, 405, [{ slot: 1, name: '로그가능도' }]));
    b.push(legend(400, 405, [{ slot: 2, name: '테일러 2차 근사(점선)' }]));
    add({
        name: 'sta-lik-newton',
        svg: svg({ width: W, height: H, title: '뉴턴-랩슨 두 걸음', desc: '지금 자리에서 로그가능도에 포물선을 대고 그 꼭짓점으로 옮겨 가기를 되풀이하면 두 걸음 만에 최댓값 가까이 간다', body: b.join('') }),
    });
}

/* ---------------- 4. 같은 3/12, 두 표본공간 ---------------- */
{
    const binom = Array.from({ length: 13 }, (_, x) => [x, comb(12, x) / 4096]);
    const nb = []; for (let N = 3; N <= 24; N += 1) nb.push([N, comb(N - 1, 2) * 0.5 ** N]);
    const pB = binom.filter(([x]) => x <= 3).reduce((a, [, p]) => a + p, 0);
    let pN = 0; for (let N = 12; N < 400; N += 1) pN += comb(N - 1, 2) * 0.5 ** N;
    const W = 780; const H = 380;
    const b = [];
    const draw = (px0, title, sub, xr, ticks, pts, isTail, obs, lab, tailLab) => {
        b.push(panel(px0, 10, 370, 360, title, sub));
        const fr = frame({ xRange: xr, yRange: [0, 0.25], box: { x: px0 + 50, y: 75, w: 290, h: 230 } });
        b.push(axesEdge(fr, { x0: xr[0], x1: xr[1], y0: 0, y1: 0.25, xTicks: ticks, yTicks: [0.1, 0.2], xLabel: lab }));
        const hw = ((fr.X(1) - fr.X(0)) * 0.7) / 2;
        for (const [x, p] of pts) {
            const t = isTail(x);
            b.push(box(fr.X(x) - hw, fr.Y(p), hw * 2, fr.Y(0) - fr.Y(p), { fill: t ? 'var(--s2)' : 'var(--s1)', op: t ? 0.75 : 0.25, stroke: t ? 'var(--s2)' : 'var(--s1)', sw: 1, rx: 1 }));
        }
        b.push(arw(fr.X(obs), fr.Y(0.235), fr.X(obs), fr.Y(0.205), { col: 'var(--ink2)' }));
        b.push(txt(fr.X(obs), fr.Y(0.235) - 5, '관측', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(px0 + 185, 352, tailLab, { anchor: 'middle', cls: 'ink bold' }));
    };
    draw(15, '시행 12번으로 정한 실험', '성공 수 X 가 확률변수 (이항)', [-0.5, 12.5], [0, 3, 6, 9, 12], binom, x => x <= 3, 3, 'x', `p값 = P(X ≤ 3) = ${pB.toFixed(4)}`);
    draw(400, '성공 3번까지 하기로 한 실험', '시행 수 N 이 확률변수 (음이항)', [2.5, 24.5], [3, 6, 9, 12, 15, 18, 21, 24], nb, x => x >= 12, 12, 'n', `p값 = P(N ≥ 12) = ${pN.toFixed(4)}`);
    add({
        name: 'sta-lik-stopping',
        svg: svg({ width: W, height: H, title: '같은 자료, 두 표본공간', desc: '시행 12번에 성공 3번이라는 같은 자료라도 실험 계획이 다르면 극단 쪽 꼬리가 달라 p값이 다르다. p = 0.5 일 때의 분포', body: b.join('') }),
    });
}

/* ---------------- 5. 재매개화: p 눈금과 로그오즈 눈금 ---------------- */
{
    const n = 10; const x = 2; const ph = x / n;
    const R = p => Math.exp(x * Math.log(p / ph) + (n - x) * Math.log((1 - p) / (1 - ph)));
    const lo = bisect(p => R(p) - 1 / 8, 1e-6, ph); const hi = bisect(p => R(p) - 1 / 8, ph, 1 - 1e-6);
    const logit = p => Math.log(p / (1 - p)); const expit = t => 1 / (1 + Math.exp(-t));
    const W = 780; const H = 380;
    const b = [];
    // 왼쪽: p
    b.push(panel(15, 10, 370, 360, 'p 눈금', `p̂ = ${ph}`));
    const f1 = frame({ xRange: [0, 0.8], yRange: [0, 1.1], box: { x: 65, y: 70, w: 280, h: 220 } });
    b.push(axesEdge(f1, { x0: 0, x1: 0.8, y0: 0, y1: 1.1, xTicks: [0, 0.2, 0.4, 0.6, 0.8], yTicks: [0.5, 1], xLabel: 'p' }));
    b.push(ln([[f1.X(0), f1.Y(1 / 8)], [f1.X(0.8), f1.Y(1 / 8)]], { sw: 1, dash: '5 4' }));
    b.push(curveCol(f1, R, 0.0005, 0.8, { col: 'var(--s1)' }));
    b.push(ln([[f1.X(lo), f1.Y(0) + 32], [f1.X(hi), f1.Y(0) + 32]], { stroke: 'var(--s2)', sw: 4 }));
    b.push(txt(200, 345, `1/8 구간 ${f3(lo)} – ${f3(hi)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(f1.X(0.8) - 2, f1.Y(1 / 8) - 6, '1/8', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 오른쪽: 로그오즈
    const tl = logit(lo); const th = logit(hi);
    b.push(panel(400, 10, 370, 360, '로그오즈 눈금 ln(p/(1−p))', `최댓값 자리 ln(0.25) = ${neg(logit(ph).toFixed(3))}`));
    const f2 = frame({ xRange: [-5, 1], yRange: [0, 1.1], box: { x: 450, y: 70, w: 250, h: 220 } }); // 축 이름 '로그오즈' 가 화폭 안에 들도록 폭을 줄였다
    b.push(axesEdge(f2, { x0: -5, x1: 1, y0: 0, y1: 1.1, xTicks: [-5, -4, -3, -2, -1, 0, 1], yTicks: [0.5, 1], xLabel: '로그오즈' }));
    b.push(ln([[f2.X(-5), f2.Y(1 / 8)], [f2.X(1), f2.Y(1 / 8)]], { sw: 1, dash: '5 4' }));
    b.push(curveCol(f2, t => R(expit(t)), -5, 1, { col: 'var(--s1)' }));
    b.push(ln([[f2.X(tl), f2.Y(0) + 32], [f2.X(th), f2.Y(0) + 32]], { stroke: 'var(--s2)', sw: 4 }));
    b.push(txt(585, 345, `1/8 구간 ${neg(tl.toFixed(3))} – ${neg(th.toFixed(3))}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(585, 362, '= p 눈금 칸의 구간 끝을 로그오즈로 바꾼 값', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-lik-reparam',
        svg: svg({ width: W, height: H, title: '같은 가능도를 두 눈금으로', desc: '눈금을 바꾸면 곡선의 모양은 바뀌지만 꼭대기의 높이와 1/8 구간의 끝은 서로 대응한다. 시행 10번 중 성공 2번', body: b.join('') }),
    });
}

export default figures;
