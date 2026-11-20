/**
 * 통계학 14장(일반화선형모형) 그림. 이름은 전부 `sta-glm-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다):
 * - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `p~i` 표기, 나머지는 유니코드.
 * - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 * - px() 에 cls 'ark'·'ar1' 을 주면 선이 사라진다 — 이 모듈은 색을 직접 넣는 arw() 를 쓴다.
 * - 색만으로 구분하지 않고 이름을 함께 적는다. 그림 안에 한자를 쓰지 않는다.
 * - 그림 안의 숫자(적합 계수, 로그가능도, 율, 분산 비)는 이 자리에서 계산한다. 난수는 쓰지 않는다.
 *
 * 본문 예제와 같은 자료를 쓴다.
 * - 열두 명 자료(x = 1…12): `직선이 무너지는 자리`·`추정과 이탈도` 의 예제
 * - 두 공장의 사고 수와 근무 시간: `푸아송 회귀` 의 예제
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');
const f2 = v => neg(v.toFixed(2));
const f3 = v => neg(v.toFixed(3));

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
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg, grid = true }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 17, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) {
        if (grid) out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
        out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

const expit = e => 1 / (1 + Math.exp(-e));

/** 설명변수 하나의 로지스틱 회귀를 뉴턴법으로 푼다(본문 `추정과 이탈도` 와 같은 절차). */
function logitFit(xs, ys) {
    let b0 = 0, b1 = 0;
    for (let it = 0; it < 60; it += 1) {
        const p = xs.map(x => expit(b0 + b1 * x));
        const w = p.map(v => v * (1 - v));
        const g0 = ys.reduce((s, y, i) => s + y - p[i], 0);
        const g1 = ys.reduce((s, y, i) => s + (y - p[i]) * xs[i], 0);
        const I00 = w.reduce((s, v) => s + v, 0);
        const I01 = w.reduce((s, v, i) => s + v * xs[i], 0);
        const I11 = w.reduce((s, v, i) => s + v * xs[i] * xs[i], 0);
        const det = I00 * I11 - I01 * I01;
        b0 += (I11 * g0 - I01 * g1) / det;
        b1 += (-I01 * g0 + I00 * g1) / det;
    }
    const ll = ys.reduce((s, y, i) => {
        const p = expit(b0 + b1 * xs[i]);
        return s + y * Math.log(p) + (1 - y) * Math.log(1 - p);
    }, 0);
    return { b0, b1, ll };
}

const XS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
const YS = [0, 0, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1];

/* ------------------------------------------------------------------ *
 * sta-glm-lpm — 같은 0/1 자료에 직선과 로지스틱 곡선
 * ------------------------------------------------------------------ */
{
    const n = XS.length;
    const mx = XS.reduce((s, v) => s + v, 0) / n, my = YS.reduce((s, v) => s + v, 0) / n;
    const sxx = XS.reduce((s, v) => s + (v - mx) ** 2, 0);
    const sxy = XS.reduce((s, v, i) => s + (v - mx) * (YS[i] - my), 0);
    const c1 = sxy / sxx, c0 = my - c1 * mx;
    const { b0, b1 } = logitFit(XS, YS);
    const W = 740, H = 360;
    const fr = frame({ xRange: [0, 14], yRange: [-0.25, 1.3], box: { x: 70, y: 40, w: 560, h: 270 } });
    let body = '';
    // 확률이 될 수 없는 띠
    body += box(fr.X(0), fr.Y(0), fr.X(14) - fr.X(0), fr.Y(-0.25) - fr.Y(0), { fill: 'var(--ink2)', op: 0.12, stroke: 'none', rx: 0 });
    body += box(fr.X(0), fr.Y(1.3), fr.X(14) - fr.X(0), fr.Y(1) - fr.Y(1.3), { fill: 'var(--ink2)', op: 0.12, stroke: 'none', rx: 0 });
    body += axesEdge(fr, { x0: 0, x1: 14, y0: -0.25, y1: 1.3, xTicks: [0, 2, 4, 6, 8, 10, 12, 14], yTicks: [0, 0.25, 0.5, 0.75, 1], grid: true });
    body += txt(fr.X(14) + 14, fr.Y(-0.25) + 4, 'x', { cls: 'ink2', size: 'sm' });
    body += txt(fr.X(0) + 8, fr.Y(1.3) - 12, 'y 또는 예측 확률', { cls: 'ink2', size: 'sm' });
    body += txt(fr.X(7), fr.Y(-0.25) - 8, '확률이 될 수 없는 곳(0 아래)', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(fr.X(7), fr.Y(1.3) + 16, '확률이 될 수 없는 곳(1 위)', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += fr.curve(x => c0 + c1 * x, { from: 0, to: 14, cls: 's2' });
    body += fr.curve(x => expit(b0 + b1 * x), { from: 0, to: 14, cls: 's1' });
    XS.forEach((x, i) => { body += pdot(fr.X(x), fr.Y(YS[i]), 'var(--ink)', 4.5); });
    for (const x of [1, 12]) {
        const v = c0 + c1 * x;
        body += `<circle cx="${fr.X(x)}" cy="${fr.Y(v)}" r="6" fill="none" stroke="var(--s2)" stroke-width="2"/>`;
        body += txt(fr.X(x) + (x === 1 ? 10 : -10), fr.Y(v) + (x === 1 ? 18 : -12), `직선 x=${x}: ${f3(v)}`, { anchor: x === 1 ? 'start' : 'end', cls: 'ink', size: 'sm' });
    }
    body += legend(fr.X(14) - 170, fr.Y(0.62), [
        { slot: 2, name: `최소제곱 직선 ${f3(c0)} + ${f3(c1)}x` },
        { slot: 1, name: '로지스틱 곡선' },
    ]);
    body += txt(fr.X(14) - 170, fr.Y(0.62) + 36, '검은 점 = 관측 y(0 또는 1)', { cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-glm-lpm',
        svg: svg({ width: W, height: H, title: '0/1 결과 열두 개에 맞춘 직선과 로지스틱 곡선', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-glm-logit — 로그오즈 축의 직선, 확률 축의 S 곡선
 * ------------------------------------------------------------------ */
{
    const W = 780, H = 320;
    let body = '';
    body += panel(10, 10, 375, 300, '로그오즈 축: η = x', '세로축 η. 한 칸 갈 때마다 언제나 1 씩 오른다');
    body += panel(395, 10, 375, 300, '확률 축: η 를 확률 p 로 되돌린 것', '세로축 p. 같은 한 칸이 가운데서 크고 끝에서 작다');
    const g1 = frame({ xRange: [-4.5, 4.5], yRange: [-4.5, 4.5], box: { x: 60, y: 65, w: 290, h: 210 } });
    body += axesEdge(g1, { x0: -4.5, x1: 4.5, y0: -4.5, y1: 4.5, xTicks: [-4, -2, 0, 2, 4], yTicks: [-4, -2, 0, 2, 4] });
    body += g1.curve(x => x, { from: -4.5, to: 4.5, cls: 's1' });
    const g2 = frame({ xRange: [-4.5, 4.5], yRange: [0, 1], box: { x: 445, y: 65, w: 290, h: 210 } });
    body += axesEdge(g2, { x0: -4.5, x1: 4.5, y0: 0, y1: 1, xTicks: [-4, -2, 0, 2, 4], yTicks: [0.25, 0.5, 0.75, 1], fmtY: v => v.toFixed(2) });
    body += g2.curve(expit, { from: -4.5, to: 4.5, cls: 's1' });
    for (const [x0, cls] of [[0, 's2'], [3, 's3']]) {
        // 왼쪽: 한 칸
        body += ln([[g1.X(x0), g1.Y(x0)], [g1.X(x0 + 1), g1.Y(x0)], [g1.X(x0 + 1), g1.Y(x0 + 1)]], { stroke: COL[cls], sw: 2.5 });
        body += txt(g1.X(x0 + 1) + 6, g1.Y(x0 + 0.5) + 4, '+1', { cls: 'ink', size: 'sm' });
        // 오른쪽: 같은 한 칸의 확률 변화
        const p0 = expit(x0), p1 = expit(x0 + 1);
        body += ln([[g2.X(x0), g2.Y(p0)], [g2.X(x0 + 1), g2.Y(p0)], [g2.X(x0 + 1), g2.Y(p1)]], { stroke: COL[cls], sw: 2.5 });
        body += x0 === 3
            ? txt(g2.X(2.8), g2.Y(0.995), `+${f3(p1 - p0)}  (${f3(p0)} → ${f3(p1)})`, { cls: 'ink', size: 'sm', anchor: 'end' })
            : txt(g2.X(x0 + 1) + 6, g2.Y((p0 + p1) / 2) + 4, `+${f3(p1 - p0)}  (${f3(p0)} → ${f3(p1)})`, { cls: 'ink', size: 'sm' });
    }
    body += txt(g1.X(4.5) + 12, g1.Y(-4.5) + 4, 'x', { cls: 'ink2', size: 'sm' });
    body += txt(g2.X(4.5) + 12, g2.Y(0) + 4, 'x', { cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-glm-logit',
        svg: svg({ width: W, height: H, title: '같은 계수 1 이 로그오즈 축과 확률 축에서 움직이는 양', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-glm-deviance — 세 모형의 로그가능도 높이
 * ------------------------------------------------------------------ */
{
    const { ll } = logitFit(XS, YS);
    const n = YS.length, k = YS.reduce((s, v) => s + v, 0);
    const ll0 = k * Math.log(k / n) + (n - k) * Math.log(1 - k / n);
    const W = 700, H = 380;
    const fr = frame({ xRange: [0, 1], yRange: [-9.5, 0.8], box: { x: 90, y: 30, w: 520, h: 310 } });
    let body = axesEdge(fr, { x0: 0, x1: 1, y0: -9.5, y1: 0.8, yTicks: [-8, -6, -4, -2, 0], grid: false });
    body += txt(fr.X(0) - 6, fr.Y(0.8) - 16, '로그가능도', { anchor: 'end', cls: 'ink2', size: 'sm' });
    const levels = [
        [0, '포화 모형: 관측마다 모수 하나 — 자료를 그대로 되풀이', 's3'],
        [ll, '현재 모형: 절편 + x', 's1'],
        [ll0, '귀무 모형: 절편만', 's2'],
    ];
    for (const [v, lab, cls] of levels) {
        body += ln([[fr.X(0), fr.Y(v)], [fr.X(0.62), fr.Y(v)]], { stroke: COL[cls], sw: 3 });
        body += txt(fr.X(0.02), fr.Y(v) - 7, `${lab}  (${f2(v)})`, { cls: 'ink', size: 'sm' });
    }
    // 괄호 화살표
    const bx = [0.66, 0.78, 0.9];
    body += arw(fr.X(bx[0]), fr.Y(0), fr.X(bx[0]), fr.Y(ll), { cls: 's1', both: true });
    body += txt(fr.X(bx[0]) + 6, fr.Y(ll / 2) - 6, '잔차 이탈도', { cls: 'ink', size: 'sm' });
    body += txt(fr.X(bx[0]) + 6, fr.Y(ll / 2) + 10, `D = 2×${f2(-ll)} = ${f2(-2 * ll)}`, { cls: 'ink', size: 'sm' });
    body += arw(fr.X(bx[1]), fr.Y(ll), fr.X(bx[1]), fr.Y(ll0), { cls: 'ark', both: true });
    body += txt(fr.X(bx[1]) - 8, fr.Y((ll + ll0) / 2) - 4, `차이 2×${f2(ll - ll0)} = ${f2(2 * (ll - ll0))}`, { anchor: 'end', cls: 'ink bold', size: 'sm' });
    body += txt(fr.X(bx[1]) - 8, fr.Y((ll + ll0) / 2) + 12, '= 가능도비 통계량', { anchor: 'end', cls: 'ink', size: 'sm' });
    body += arw(fr.X(bx[2]), fr.Y(0), fr.X(bx[2]), fr.Y(ll0), { cls: 's2', both: true });
    body += txt(fr.X(bx[2]) + 8, fr.Y(ll0 / 2) - 6, '귀무 이탈도', { cls: 'ink', size: 'sm' });
    body += txt(fr.X(bx[2]) + 8, fr.Y(ll0 / 2) + 10, f2(-2 * ll0), { cls: 'ink', size: 'sm' });
    add({
        name: 'sta-glm-deviance',
        svg: svg({ width: W, height: H, title: '포화·현재·귀무 모형의 로그가능도와 이탈도', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-glm-offset — 개수와 율의 순위가 뒤집힌다
 * ------------------------------------------------------------------ */
{
    const F = [
        { name: '공장 A', y: 18, t: 60000, cls: 's1' },
        { name: '공장 B', y: 12, t: 20000, cls: 's2' },
    ];
    const W = 720, H = 320;
    let body = '';
    body += panel(10, 10, 345, 300, '사고 수', '노출을 무시하면 A 가 더 위험해 보인다');
    body += panel(365, 10, 345, 300, '1,000 h 당 사고율', '근무 시간으로 나누면 B 가 두 배');
    const g1 = frame({ xRange: [0, 2], yRange: [0, 22], box: { x: 60, y: 60, w: 260, h: 190 } });
    body += axesEdge(g1, { x0: 0, x1: 2, y0: 0, y1: 22, yTicks: [5, 10, 15, 20] });
    const g2 = frame({ xRange: [0, 2], yRange: [0, 0.75], box: { x: 415, y: 60, w: 260, h: 190 } });
    body += axesEdge(g2, { x0: 0, x1: 2, y0: 0, y1: 0.75, yTicks: [0.2, 0.4, 0.6], fmtY: v => v.toFixed(1) });
    F.forEach((f, i) => {
        const xc = 0.55 + i * 0.9;
        const rate = (f.y / f.t) * 1000;
        body += box(g1.X(xc - 0.3), g1.Y(f.y), g1.X(0.6) - g1.X(0), g1.Y(0) - g1.Y(f.y), { fill: COL[f.cls], op: 0.55, stroke: COL[f.cls], sw: 1.2, rx: 1.5 });
        body += txt(g1.X(xc), g1.Y(f.y) - 6, `${f.y}건`, { anchor: 'middle', cls: 'ink', size: 'sm' });
        body += txt(g1.X(xc), g1.Y(0) + 17, f.name, { anchor: 'middle', cls: 'ink', size: 'sm' });
        body += txt(g1.X(xc), g1.Y(0) + 33, `${f.t.toLocaleString('en-US')} h`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        body += box(g2.X(xc - 0.3), g2.Y(rate), g2.X(0.6) - g2.X(0), g2.Y(0) - g2.Y(rate), { fill: COL[f.cls], op: 0.55, stroke: COL[f.cls], sw: 1.2, rx: 1.5 });
        body += txt(g2.X(xc), g2.Y(rate) - 6, rate.toFixed(2), { anchor: 'middle', cls: 'ink', size: 'sm' });
        body += txt(g2.X(xc), g2.Y(0) + 17, f.name, { anchor: 'middle', cls: 'ink', size: 'sm' });
        body += txt(g2.X(xc), g2.Y(0) + 33, `${f.y} ÷ ${f.t / 1000}`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    });
    add({
        name: 'sta-glm-offset',
        svg: svg({ width: W, height: H, title: '근무 시간이 다른 두 공장의 사고 수와 사고율', body }),
    });
}

/* ------------------------------------------------------------------ *
 * sta-glm-overdisp — 같은 평균, 다른 퍼짐
 * ------------------------------------------------------------------ */
{
    const mu = 4, r = 2; // 음이항: 분산 = μ + μ²/r = 12
    const lg = v => { // ln Γ, 정수·반정수만 쓰지 않으므로 Lanczos
        const c = [76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5];
        let x = v, y = v, t = x + 5.5;
        t -= (x + 0.5) * Math.log(t);
        let s = 1.000000000190015;
        for (const ci of c) s += ci / ++y;
        return -t + Math.log((2.5066282746310005 * s) / x);
    };
    const pois = y => Math.exp(-mu + y * Math.log(mu) - lg(y + 1));
    const nb = y => Math.exp(lg(y + r) - lg(r) - lg(y + 1) + r * Math.log(r / (r + mu)) + y * Math.log(mu / (r + mu)));
    const ys = Array.from({ length: 17 }, (_, i) => i);
    const W = 760, H = 340;
    const fr = frame({ xRange: [-0.7, 16.7], yRange: [0, 0.22], box: { x: 70, y: 40, w: 600, h: 240 } });
    let body = axesEdge(fr, { x0: -0.7, x1: 16.7, y0: 0, y1: 0.22, xTicks: [0, 2, 4, 6, 8, 10, 12, 14, 16], yTicks: [0.05, 0.1, 0.15, 0.2] });
    body += txt(fr.X(16.7) + 14, fr.Y(0) + 4, '개수', { cls: 'ink2', size: 'sm' });
    body += txt(fr.X(-0.7) - 6, fr.Y(0.22) - 16, '확률', { anchor: 'end', cls: 'ink2', size: 'sm' });
    const hw = (fr.X(1) - fr.X(0)) * 0.2;
    let vP = 0, vN = 0;
    for (const y of ys) {
        const a = pois(y), b = nb(y);
        body += box(fr.X(y) - 2 * hw, fr.Y(a), 2 * hw, fr.Y(0) - fr.Y(a), { fill: COL.s1, op: 0.6, stroke: COL.s1, sw: 1, rx: 1 });
        body += box(fr.X(y), fr.Y(b), 2 * hw, fr.Y(0) - fr.Y(b), { fill: COL.s2, op: 0.6, stroke: COL.s2, sw: 1, rx: 1 });
    }
    for (let y = 0; y < 400; y += 1) { vP += (y - mu) ** 2 * pois(y); vN += (y - mu) ** 2 * nb(y); }
    body += legend(fr.X(8), fr.Y(0.2), [
        { slot: 1, name: `푸아송: 평균 ${mu}, 분산 ${vP.toFixed(1)}, 분산/평균 ${(vP / mu).toFixed(1)}` },
        { slot: 2, name: `더 퍼진 개수: 평균 ${mu}, 분산 ${vN.toFixed(1)}, 분산/평균 ${(vN / mu).toFixed(1)}` },
    ]);
    body += txt(fr.X(8), fr.Y(0.2) + 40, '더 퍼진 쪽은 0 과 큰 값이 모두 잦다', { cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-glm-overdisp',
        svg: svg({ width: W, height: H, title: '평균이 4 로 같은 두 개수 분포', body }),
    });
}

export default figures;
