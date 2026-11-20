/**
 * 통계학 8장(검정의 이론) 그림. 이름은 전부 `sta-tst-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다)
 *   - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 나머지는 유니코드.
 *   - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 *   - px() 에 cls 'ark'·'ar1' 을 넘기면 선이 사라진다. 화살표는 아래 arw() 를 쓴다.
 *   - 그림 안에 한자를 쓰지 않는다.
 * 난수를 쓰지 않는다. 모든 곡선과 막대 높이는 정확한 식으로 계산한다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const f3 = v => v.toFixed(3);

/* ---------- 분포 소도구 ---------- */
const erf = x => {
    // Abramowitz-Stegun 7.1.26 보다 정확한 급수/연분수 대신, 충분히 정밀한 근사(오차 1e-7 수준)
    const s = Math.sign(x); const a = Math.abs(x);
    const t = 1 / (1 + 0.5 * a);
    const y = 1 - t * Math.exp(-a * a - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
        + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
        + t * (-0.82215223 + t * 0.17087277)))))))));
    return s * y;
};
const Phi = z => 0.5 * (1 + erf(z / Math.SQRT2));
const zUpper = p => { let lo = -10; let hi = 10; for (let i = 0; i < 100; i += 1) { const m = (lo + hi) / 2; if (1 - Phi(m) > p) lo = m; else hi = m; } return (lo + hi) / 2; };
const choose = (n, k) => { let c = 1; for (let i = 1; i <= k; i += 1) c = (c * (n - k + i)) / i; return c; };

/* ---------- 화소 소도구 ---------- */
function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.8, both = false, dash } = {}) {
    const col = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' }[cls];
    const mk = { s1: 'ar1', s2: 'ar2', s3: 'ar3', ark: 'ark' }[cls];
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round"${both ? ` marker-start="url(#${mk})"` : ''} marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function rect(x, y, w, h, { fill = 'none', op = 1, stroke = 'none', sw = 1, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function ln(pts, { stroke = 'var(--ink2)', sw = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')}"/>`;
}

/* ================================================================== *
 * sta-tst-np — 가능도비가 큰 자리부터 α 를 채운다
 * H0: X ~ Bin(8, 0.5), H1: X 가 0~8 에 고르게(각 1/9).
 * ================================================================== */
{
    const n = 8;
    const f0 = Array.from({ length: n + 1 }, (_, x) => choose(n, x) / 2 ** n);
    const f1 = Array.from({ length: n + 1 }, () => 1 / (n + 1));
    const ratio = f0.map((p, x) => f1[x] / p);
    // 비가 큰 순서로 채우되 α = 0.08 을 넘지 않게
    const alpha = 0.08;
    const order = [...ratio.keys()].sort((a, b) => ratio[b] - ratio[a]);
    const R = new Set(); let size = 0;
    for (const x of order) { if (size + f0[x] <= alpha + 1e-12) { R.add(x); size += f0[x]; } else break; }
    const power = [...R].reduce((s, x) => s + f1[x], 0);
    // 같은 크기 이하의 한쪽 꼬리(큰 쪽): {7,8}
    const tail = [7, 8];
    const tSize = tail.reduce((s, x) => s + f0[x], 0);
    const tPow = tail.reduce((s, x) => s + f1[x], 0);

    const W = 720; const H = 405;
    const fr = frame({ xRange: [-0.7, 8.7], yRange: [0, 0.3], box: { x: 70, y: 120, w: 610, h: 220 } });
    // 축만 그리는 틀: x 범위가 0 을 품지 않게 옮겨 세로축이 x = 0 칸을 가르지 않고 왼쪽 가장자리에 서게 한다
    const frAx = frame({ xRange: [0.3, 9.7], yRange: [0, 0.3], box: { x: 70, y: 120, w: 610, h: 220 } });
    const body = [];
    body.push(txt(W / 2, 26, 'H₀ 아래 확률은 α 를 쓰는 값, H₁ 아래 확률은 사는 검정력이다', { anchor: 'middle', cls: 'ink bold' }));
    body.push(frAx.axes({ xLabel: 'x', yLabel: '확률', xTicks: [], yTicks: [0.1, 0.2, 0.3] }));
    for (let x = 0; x <= n; x += 1) {
        const cx = fr.X(x);
        if (R.has(x)) body.push(rect(cx - 30, fr.Y(0.3), 60, fr.Y(0) - fr.Y(0.3), { fill: 'var(--s3)', op: 0.16 }));
        body.push(rect(cx - 22, fr.Y(f0[x]), 20, fr.Y(0) - fr.Y(f0[x]), { fill: 'var(--s1)', op: 0.85 }));
        body.push(rect(cx + 2, fr.Y(f1[x]), 20, fr.Y(0) - fr.Y(f1[x]), { fill: 'var(--s2)', op: 0.85 }));
        body.push(txt(cx, fr.Y(0) + 16, String(x), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        body.push(txt(cx, 88, ratio[x] >= 10 ? ratio[x].toFixed(1) : ratio[x].toFixed(2), { anchor: 'middle', cls: R.has(x) ? 'ink bold' : 'ink2', size: 'sm' }));
    }
    body.push(txt(20, 88, '비 f₁/f₀', { cls: 'ink2', size: 'sm' }));
    body.push(txt(20, 62, `기각역(초록 칸) = 비가 큰 칸부터 α ≤ ${alpha} 까지: {${[...R].sort((a, b) => a - b).join(', ')}}`, { cls: 'ink', size: 'sm' }));
    body.push(legend(70, 372, [{ slot: 1, name: 'H₀: Bin(8, 0.5)' }, { slot: 2, name: 'H₁: 0–8 에 고르게' }]));
    body.push(txt(300, 370, `이 기각역: 크기 ${f3(size)}, 검정력 ${f3(power)}`, { cls: 'ink', size: 'sm' }));
    body.push(txt(300, 388, `큰 쪽 꼬리 {7, 8}: 크기 ${f3(tSize)}, 검정력 ${f3(tPow)}`, { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-tst-np', svg: svg({ width: W, height: H, title: '네이만-피어슨: 가능도비가 큰 자리부터 채운 기각역', body: body.join('') }) });
}

/* ================================================================== *
 * sta-tst-ump — 한쪽 검정과 양쪽 검정의 검정력 곡선
 * ================================================================== */
{
    const za = zUpper(0.05); const za2 = zUpper(0.025);
    const one = d => 1 - Phi(za - d);
    const two = d => 1 - Phi(za2 - d) + Phi(-za2 - d);
    const W = 700; const H = 380;
    const fr = frame({ xRange: [-3, 4], yRange: [0, 1], box: { x: 70, y: 60, w: 560, h: 260 } });
    const body = [];
    body.push(txt(W / 2, 26, '오른쪽 한쪽 검정은 오른쪽에서 언제나 위, 왼쪽에서는 α 아래로 무너진다', { anchor: 'middle', cls: 'ink bold' }));
    // 세로축은 μ = μ₀ 자리(가운데)에 그대로 두되, 눈금 글자와 축 이름은 왼쪽 가장자리로 뺀다 —
    // 가운데 두면 0.05 글자를 두 곡선이 지나간다
    body.push(fr.axes({ xLabel: '(μ − μ₀)/SE', xTicks: [-3, -2, -1, 1, 2, 3, 4], yTicks: [] }));
    for (const t of [0.05, 0.5, 1]) {
        body.push(`<path class="gr" d="M${fr.X(-3)} ${fr.Y(t)} H${fr.X(4)}"/>`);
        body.push(txt(fr.X(-3) - 6, fr.Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    body.push(txt(fr.X(-3) - 6, fr.Y(1) - 14, '기각 확률', { anchor: 'start', cls: 'ink2', size: 'sm' }));
    body.push(txt(fr.X(0), fr.Y(0) + 15, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    body.push(fr.curve(two, { cls: 's1' }));
    body.push(fr.curve(one, { cls: 's2', dash: '7 4' }));
    body.push(fr.label([2, one(2)], `한쪽(오른쪽) ${f3(one(2))}`, { dx: -10, dy: -6, anchor: 'end', cls: 'ink', size: 'sm' }));
    body.push(fr.dot([2, one(2)], { cls: 'f2' }));
    body.push(fr.dot([2, two(2)], { cls: 'f1' }));
    body.push(fr.label([2, two(2)], `양쪽 ${f3(two(2))}`, { dx: 10, dy: 16, cls: 'ink', size: 'sm' }));
    body.push(fr.label([-2.9, two(-2.9)], '양쪽', { dx: 4, dy: -8, cls: 'ink2', size: 'sm' }));
    body.push(fr.label([-2.3, 0.85], '한쪽 검정은 왼쪽에서 거의 0 이다', { cls: 'ink2', size: 'sm' }));
    body.push(fr.label([-2.3, 0.85], `(μ − μ₀ = −2 SE 에서 ${one(-2).toFixed(4)})`, { dy: 16, cls: 'ink2', size: 'sm' }));
    body.push(legend(440, 300, [{ slot: 1, name: '양쪽 검정 (|z| ≥ 1.96)' }, { slot: 2, name: '오른쪽 한쪽 검정 (z ≥ 1.645, 점선)' }]));
    add({ name: 'sta-tst-ump', svg: svg({ width: W, height: H, title: '한쪽 검정과 양쪽 검정의 검정력 곡선', body: body.join('') }) });
}

/* ================================================================== *
 * sta-tst-three — 로그가능도 위 세 거리 (이항 n=40, x=8, p0=0.33)
 * ================================================================== */
{
    const n = 40; const x = 8; const ph = x / n; const p0 = 0.33;
    const ell = p => x * Math.log(p) + (n - x) * Math.log(1 - p);
    const rel = p => ell(p) - ell(ph);
    const slope = (x - n * p0) / (p0 * (1 - p0));
    const lr = -2 * rel(p0);
    const wald = n * (ph - p0) ** 2 / (ph * (1 - ph));
    const score = n * (ph - p0) ** 2 / (p0 * (1 - p0));
    const W = 760; const H = 440;
    const fr = frame({ xRange: [0.08, 0.42], yRange: [-4, 0.6], box: { x: 80, y: 70, w: 560, h: 300 } });
    const body = [];
    body.push(txt(W / 2, 26, '같은 곡선에서 세 가지를 잰다: 가로 거리, 기울기, 높이', { anchor: 'middle', cls: 'ink bold' }));
    body.push(fr.axes({ xLabel: 'p', yLabel: 'ℓ(p) − ℓ(p̂)', xTicks: [0.1, 0.2, 0.3, 0.4], yTicks: [-4, -3, -2, -1] }));
    body.push(fr.curve(rel, { from: 0.08, to: 0.42, cls: 's1' }));
    // 높이(가능도비)
    body.push(fr.guide([p0, 0], [p0, rel(p0)]));
    body.push(fr.guide([0.08, rel(p0)], [p0, rel(p0)]));
    body.push(arw(fr.X(p0) + 34, fr.Y(0), fr.X(p0) + 34, fr.Y(rel(p0)), { cls: 's2', both: true }));
    body.push(txt(fr.X(p0) + 42, fr.Y(rel(p0) / 2) - 4, '[높이] 가능도비', { cls: 'ink', size: 'sm' }));
    body.push(txt(fr.X(p0) + 42, fr.Y(rel(p0) / 2) + 12, `2 × 높이 = ${lr.toFixed(2)}`, { cls: 'ink2', size: 'sm' }));
    // 가로(왈드)
    const yH = 0.35;
    body.push(arw(fr.X(ph), fr.Y(yH), fr.X(p0), fr.Y(yH), { cls: 's3', both: true }));
    body.push(txt((fr.X(ph) + fr.X(p0)) / 2, fr.Y(yH) - 8, `[가로] 왈드: ${wald.toFixed(2)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(fr.guide([ph, yH], [ph, 0]));
    // 기울기(점수): p0 에서의 접선
    const tl = p => rel(p0) + slope * (p - p0);
    body.push(fr.curve(tl, { from: p0 - 0.045, to: p0 + 0.03, cls: 's2', dash: '5 3' }));
    body.push(fr.label([p0 + 0.03, tl(p0 + 0.03)], `[기울기] 점수: ${score.toFixed(2)}`, { dx: -8, dy: 18, anchor: 'end', cls: 'ink', size: 'sm' }));
    body.push(fr.dot([ph, 0], { cls: 'f1' }));
    body.push(fr.label([ph, 0], 'p̂ = 0.2 (꼭대기)', { dx: -8, dy: -10, anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(fr.dot([p0, rel(p0)], { cls: 'f2' }));
    body.push(fr.label([p0, rel(p0)], 'p₀ = 0.33', { dx: -10, dy: 20, anchor: 'end', cls: 'ink', size: 'sm' }));
    body.push(txt(80, 420, `세 통계량 모두 귀무가설 아래 근사적으로 χ²(1) 을 따른다(5% 점 3.84). 왈드만 기각한다.`, { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-tst-three', svg: svg({ width: W, height: H, title: '왈드·점수·가능도비 검정이 로그가능도 곡선에서 재는 것', body: body.join('') }) });
}

/* ================================================================== *
 * sta-tst-invert — 점수 검정을 뒤집으면 윌슨 구간 (n = 20)
 * ================================================================== */
{
    const n = 20; const z = zUpper(0.025);
    const wil = x => { const p = x / n; const d = 1 + z * z / n; const c = (p + z * z / (2 * n)) / d; const h = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d; return [Math.max(0, c - h), Math.min(1, c + h)]; };
    const W = 760; const H = 470;
    const fr0 = frame({ xRange: [-1.2, 20.6], yRange: [0, 1], box: { x: 70, y: 60, w: 600, h: 340 } });
    const fr = { ...fr0, X: v => fr0.X(v) };
    const body = [];
    body.push(txt(W / 2, 26, '칠한 칸은 기각, 비운 칸은 기각하지 않음 — 세로로 읽으면 구간, 가로로 읽으면 검정', { anchor: 'middle', cls: 'ink bold' }));
    const cw = (fr.X(1) - fr.X(0));
    for (let x = 0; x <= n; x += 1) {
        const [L, U] = wil(x);
        const x0 = fr.X(x) - cw / 2 + 1;
        body.push(rect(x0, fr.Y(1), cw - 2, fr.Y(U) - fr.Y(1), { fill: 'var(--s2)', op: 0.28 }));
        body.push(rect(x0, fr.Y(L), cw - 2, fr.Y(0) - fr.Y(L), { fill: 'var(--s2)', op: 0.28 }));
    }
    body.push(ln([[fr.X(-0.6), fr.Y(0)], [fr.X(20.6), fr.Y(0)]]));
    body.push(ln([[fr.X(-0.6), fr.Y(0)], [fr.X(-0.6), fr.Y(1)]]));
    for (const t of [0, 5, 10, 15, 20]) body.push(txt(fr.X(t), fr.Y(0) + 16, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of [0, 0.25, 0.5, 0.75, 1]) body.push(txt(fr.X(-0.6) - 6, fr.Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    body.push(txt(fr.X(20.6) + 6, fr.Y(0) + 4, 'x (20 번 중 성공)', { cls: 'ink2', size: 'sm' }));
    body.push(txt(fr.X(-0.6) - 6, fr.Y(1) - 10, 'p₀', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // x = 6 의 세로 띠
    const [L6, U6] = wil(6);
    body.push(rect(fr.X(6) - cw / 2, fr.Y(U6), cw, fr.Y(L6) - fr.Y(U6), { stroke: 'var(--s1)', sw: 2.5 }));
    body.push(txt(fr.X(6) + cw / 2 + 6, fr.Y(L6) + 4, `x = 6 의 구간 (${f3(L6)}, ${f3(U6)})`, { cls: 'ink bold', size: 'sm' }));
    // p0 = 0.5 의 가로 줄
    const acc = []; for (let x = 0; x <= n; x += 1) { const [L, U] = wil(x); if (L < 0.5 && 0.5 < U) acc.push(x); }
    body.push(ln([[fr.X(-0.5), fr.Y(0.5)], [fr.X(20.5), fr.Y(0.5)]], { stroke: 'var(--s3)', sw: 2, dash: '6 3' }));
    body.push(txt(fr.X(20.5) + 6, fr.Y(0.5) - 6, 'p₀ = 0.5 의', { cls: 'ink', size: 'sm' }));
    body.push(txt(fr.X(20.5) + 6, fr.Y(0.5) + 10, `채택역 x = ${acc[0]}–${acc[acc.length - 1]}`, { cls: 'ink', size: 'sm' }));
    body.push(txt(70, 445, '칸마다 점수 검정(분모 p₀)을 유의수준 5% 로 했다. 세로 띠의 끝이 윌슨 구간의 두 끝이다.', { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-tst-invert', svg: svg({ width: W, height: H, title: '검정을 뒤집어 만든 구간', body: body.join('') }) });
}

/* ================================================================== *
 * sta-tst-pdist — p값의 분포 (오른쪽 한쪽 z 검정)
 * ================================================================== */
{
    const theta = 2; const za = zUpper(0.05);
    const cdf = (u, th) => (u <= 0 ? 0 : u >= 1 ? 1 : Phi(th - zUpper(u)));
    const bins = 20; const w = 1 / bins;
    const panel = (ox, th, title, cls) => {
        const fr = frame({ xRange: [0, 1], yRange: [0, 13], box: { x: ox, y: 70, w: 290, h: 260 } });
        const out = [];
        out.push(txt(ox + 145, 56, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(fr.axes({ xLabel: 'p값', yLabel: '밀도', xTicks: [0.05, 0.5, 1], yTicks: [1, 5, 10], grid: true }));
        for (let i = 0; i < bins; i += 1) {
            const pr = cdf((i + 1) * w, th) - cdf(i * w, th);
            const dens = pr / w;
            const hgt = Math.min(dens, 13);
            out.push(rect(fr.X(i * w) + 1, fr.Y(hgt), fr.X(w) - fr.X(0) - 2, fr.Y(0) - fr.Y(hgt), { fill: `var(--${cls})`, op: 0.8 }));
            if (i === 0) out.push(hgt > 3
                ? txt(fr.X(w) + 6, fr.Y(hgt) + 14, `첫 칸 넓이 ${f3(pr)}`, { cls: 'ink', size: 'sm' })
                : txt(fr.X(0) + 2, fr.Y(hgt) - 8, `첫 칸 넓이 ${f3(pr)}`, { cls: 'ink', size: 'sm' }));
        }
        return out.join('');
    };
    const W = 760; const H = 400;
    const body = [];
    body.push(txt(W / 2, 26, '귀무가설 아래 p값은 고르게, 대립가설 아래 0 쪽으로 몰린다', { anchor: 'middle', cls: 'ink bold' }));
    body.push(panel(60, 0, '[가] 귀무가설이 참 (μ = μ₀)', 's1'));
    body.push(panel(430, theta, `[나] 대립가설이 참 ((μ − μ₀)/SE = ${theta})`, 's2'));
    body.push(txt(60, 375, `오른쪽 한쪽 z 검정. 막대는 폭 0.05 칸의 정확한 확률을 폭으로 나눈 값이다. [나] 의 첫 칸 넓이가 검정력 ${f3(1 - Phi(za - theta))} 이다.`, { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-tst-pdist', svg: svg({ width: W, height: H, title: 'p값의 분포', body: body.join('') }) });
}

/* ================================================================== *
 * sta-tst-tost — 90% 구간과 동등 범위, 네 경우
 * ================================================================== */
{
    const D = 2;
    const cases = [
        ['[가]', 0.6, 1.4, '동등 범위 안, 0 을 품지 않음', '동등하다 — 차이도 유의하지만 작다'],
        ['[나]', -0.7, 0.9, '동등 범위 안, 0 을 품음', '동등하다'],
        ['[다]', -1.6, 2.8, '0 을 품지만 범위를 넘음', '판정할 수 없다 — 자료가 모자란다'],
        ['[라]', 2.3, 3.6, '범위 밖, 0 을 품지 않음', '동등하지 않다 — 차이가 크다'],
    ];
    const W = 760; const H = 360;
    const fr = frame({ xRange: [-3, 4.2], yRange: [0, 5], box: { x: 60, y: 60, w: 420, h: 250 } });
    const body = [];
    body.push(txt(W / 2, 26, '90% 구간이 동등 범위 안에 통째로 들어가야 동등하다', { anchor: 'middle', cls: 'ink bold' }));
    body.push(rect(fr.X(-D), fr.Y(5), fr.X(D) - fr.X(-D), fr.Y(0) - fr.Y(5), { fill: 'var(--s3)', op: 0.14 }));
    body.push(ln([[fr.X(-D), fr.Y(5)], [fr.X(-D), fr.Y(0)]], { stroke: 'var(--s3)', sw: 1.5, dash: '5 3' }));
    body.push(ln([[fr.X(D), fr.Y(5)], [fr.X(D), fr.Y(0)]], { stroke: 'var(--s3)', sw: 1.5, dash: '5 3' }));
    body.push(ln([[fr.X(0), fr.Y(5)], [fr.X(0), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1.2 }));
    body.push(txt(fr.X(-D), fr.Y(0) + 18, '−Δ', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(txt(fr.X(D), fr.Y(0) + 18, 'Δ', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(txt(fr.X(0), fr.Y(0) + 18, '0', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(txt(fr.X(0), fr.Y(5) - 6, '동등 범위', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    cases.forEach(([nm, lo, hi, what, verdict], i) => {
        const y = fr.Y(4.2 - i);
        body.push(arw(fr.X(lo), y, fr.X(hi), y, { cls: 's1', width: 2.5 }).replace(/marker-end="[^"]*"/, ''));
        body.push(`<circle cx="${r2(fr.X((lo + hi) / 2))}" cy="${r2(y)}" r="4" class="f1"/>`);
        body.push(txt(fr.X(-3) - 8, y + 4, nm, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        body.push(txt(500, y - 4, what, { cls: 'ink2', size: 'sm' }));
        body.push(txt(500, y + 12, verdict, { cls: 'ink', size: 'sm' }));
    });
    body.push(txt(60, 345, '가로 막대는 차이의 90% 신뢰구간, 점은 추정값이다.', { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-tst-tost', svg: svg({ width: W, height: H, title: '동등성 검정의 네 경우', body: body.join('') }) });
}

export default figures;
