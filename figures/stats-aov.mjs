/**
 * 통계학 12장(분산분석)의 그림. 이름은 전부 `sta-aov-` 로 시작한다.
 *
 * 자료는 5장이 정한 세 공유 자료(완전확률화 3×4, 확률화블록 3처리×4블록, 2×2 칸당 3)와
 * 12장 `가정과 그 대안` 의 공분산분석 예제 자료다. 본문과 같은 수를 여기서 다시 적고,
 * 제곱합·F·p값·투키 문턱은 모두 이 파일이 계산한다(손으로 적지 않는다).
 *
 * 라벨에는 수식을 쓸 수 없다. 그리스 문자는 유니코드, 아래첨자는 `x~0` 표기,
 * 그래서 라벨에 물결표를 그냥 쓰지 않는다. 따옴표는 ‘ ’. 그림 안에 한자를 쓰지 않는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const fmt = (v, d = 2) => v.toFixed(d);
const sum = a => a.reduce((s, v) => s + v, 0);
const mean = a => sum(a) / a.length;

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function rect(x, y, w, h, { fill = 'none', op = 1, stroke = 'none', sw = 1 } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}
/** 색을 직접 넣는 화살표(lib 의 px 에 ar1/ark 를 넘기면 선이 사라진다). */
function arw(x1, y1, x2, y2, { col = 's1', width = 2 } = {}) {
    const stroke = col === 'ink2' ? 'var(--ink2)' : `var(--${col})`;
    const mk = col === 'ink2' ? 'ark' : `ar${col.slice(1)}`;
    return `<path fill="none" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/* ---------------- 수치 도구 ---------------- */
function lgamma(z) {
    const g = 7;
    const c = [0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
        1.5056327351493116e-7];
    if (z < 0.5) return Math.log(Math.PI / Math.sin(Math.PI * z)) - lgamma(1 - z);
    z -= 1;
    let x = c[0];
    for (let i = 1; i < g + 2; i += 1) x += c[i] / (z + i);
    const t = z + g + 0.5;
    return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
}
const fDens = (x, d1, d2) => (x <= 0 ? 0 : Math.exp(
    lgamma((d1 + d2) / 2) - lgamma(d1 / 2) - lgamma(d2 / 2)
    + (d1 / 2) * Math.log(d1 / d2) + (d1 / 2 - 1) * Math.log(x)
    - ((d1 + d2) / 2) * Math.log(1 + (d1 * x) / d2)));
/** 위쪽 꼬리 넓이 — 심프슨 적분(0 부터 x 까지)의 보수. */
function fSf(x, d1, d2) {
    const n = 4000, h = x / n;
    let s = 0;
    for (let i = 0; i <= n; i += 1) {
        const v = fDens(i === 0 ? 1e-9 : i * h, d1, d2);
        s += v * (i === 0 || i === n ? 1 : i % 2 ? 4 : 2);
    }
    return 1 - (s * h) / 3;
}
function fCrit(alpha, d1, d2) {
    let lo = 0.01, hi = 100;
    for (let i = 0; i < 60; i += 1) { const m = (lo + hi) / 2; if (fSf(m, d1, d2) > alpha) lo = m; else hi = m; }
    return (lo + hi) / 2;
}
function erf(x) {
    // Abramowitz-Stegun 7.1.26 보다 정밀한 급수/연분수 대신, 충분히 촘촘한 근사
    const s = Math.sign(x); x = Math.abs(x);
    const t = 1 / (1 + 0.5 * x);
    const y = 1 - t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
        + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
        + t * (-0.82215223 + t * 0.17087277)))))))));
    return s * y;
}
const Phi = z => 0.5 * (1 + erf(z / Math.SQRT2));
const phi = z => Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
/** 스튜던트화 범위의 누적분포 P(Q ≤ q; k 개, 자유도 df) — 이중 심프슨 적분. */
function ptukey(q, k, df) {
    const pr = w => {
        const n = 160, lo = -8, h = 16 / n;
        let s = 0;
        for (let i = 0; i <= n; i += 1) {
            const z = lo + i * h;
            s += phi(z) * (Phi(z + w) - Phi(z)) ** (k - 1) * (i === 0 || i === n ? 1 : i % 2 ? 4 : 2);
        }
        return (k * s * h) / 3;
    };
    const n = 300, lo = 1e-6, hi = 4, h = (hi - lo) / n;
    let s = 0;
    for (let i = 0; i <= n; i += 1) {
        const x = lo + i * h;
        const lf = Math.log(2) + (df / 2) * Math.log(df / 2) - lgamma(df / 2) + (df - 1) * Math.log(x) - (df * x * x) / 2;
        s += Math.exp(lf) * pr(q * x) * (i === 0 || i === n ? 1 : i % 2 ? 4 : 2);
    }
    return (s * h) / 3;
}
function qtukey(alpha, k, df) {
    let lo = 1, hi = 10;
    for (let i = 0; i < 40; i += 1) { const m = (lo + hi) / 2; if (1 - ptukey(m, k, df) > alpha) lo = m; else hi = m; }
    return (lo + hi) / 2;
}

/* ---------------- 5장 공유 자료 ---------------- */
const CRD = { A: [8, 10, 9, 13], B: [12, 14, 11, 15], C: [15, 17, 14, 18] };
const RCBD = { A: [14, 16, 20, 22], B: [14, 20, 23, 23], C: [17, 21, 23, 27] };
const FAC = { '25,1': [9, 10, 11], '35,1': [12, 14, 16], '25,2': [11, 12, 13], '35,2': [20, 22, 24] };

const crdAll = Object.values(CRD).flat();
const crdG = mean(crdAll);
const crdM = Object.fromEntries(Object.entries(CRD).map(([k, v]) => [k, mean(v)]));
const crdSSB = sum(Object.entries(CRD).map(([k, v]) => v.length * (crdM[k] - crdG) ** 2));
const crdSSW = sum(Object.entries(CRD).flatMap(([k, v]) => v.map(x => (x - crdM[k]) ** 2)));
const crdA = 3, crdN = crdAll.length;
const crdMSW = crdSSW / (crdN - crdA);
const crdF = (crdSSB / (crdA - 1)) / crdMSW;

/* ------------------------------------------------------------------ *
 * 1. 한 점의 편차가 두 조각으로
 * ------------------------------------------------------------------ */
{
    const W = 720, H = 360;
    const F = frame({ xRange: [0, 3.6], yRange: [6, 20], box: { x: 70, y: 50, w: 520, h: 250 } });
    let body = F.axes({ xLabel: '', yLabel: '건조 질량 (g)', xTicks: [], yTicks: [8, 10, 12, 14, 16, 18], grid: true });
    const pos = { A: 0.6, B: 1.6, C: 2.6 };
    const names = { A: '대조', B: '비료 가', C: '비료 나' };
    body += F.line([[0.2, crdG], [3.2, crdG]], { cls: 's3', dash: '6 4' });
    body += F.label([3.2, crdG], `전체 평균 ${fmt(crdG, 0)}`, { dx: 6, dy: 4, cls: 'ink', size: 'sm' });
    for (const k of Object.keys(CRD)) {
        const x0 = pos[k];
        body += F.line([[x0 - 0.3, crdM[k]], [x0 + 0.3, crdM[k]]], { cls: 's1' });
        CRD[k].forEach((v, i) => { body += F.dot([x0 - 0.18 + i * 0.12, v], { cls: 'f1', r: 4 }); });
        body += F.label([x0, 6], `${names[k]} (평균 ${fmt(crdM[k], 0)})`, { dy: 18, anchor: 'middle', cls: 'ink2', size: 'sm' });
    }
    // 강조할 점: 비료 나의 18
    const xC = pos.C - 0.18 + 3 * 0.12, yC = 18;
    body += F.dot([xC, yC], { cls: 'f2', r: 6 });
    const xa = xC + 0.18;
    body += arw(F.X(xa), F.Y(crdG), F.X(xa), F.Y(crdM.C) + 3, { col: 's3' });
    body += arw(F.X(xa), F.Y(crdM.C), F.X(xa), F.Y(yC) + 3, { col: 's2' });
    body += F.label([xa, (crdG + crdM.C) / 2], `집단 간 몫 ${fmt(crdM.C - crdG, 0)}`, { dx: 8, dy: 4, cls: 'ink', size: 'sm' });
    body += F.label([xa, (crdM.C + yC) / 2], `집단 안 몫 ${fmt(yC - crdM.C, 0)}`, { dx: 8, dy: 4, cls: 'ink', size: 'sm' });
    body += txt(W / 2, 24, `점 하나의 편차 ${fmt(yC - crdG, 0)} = 집단 평균까지 ${fmt(crdM.C - crdG, 0)} + 그 안에서 ${fmt(yC - crdM.C, 0)}`, { anchor: 'middle', cls: 'ink bold' });
    body += legend(90, 350, [{ slot: 1, name: '집단 평균 (실선)' }]);
    body += legend(260, 350, [{ slot: 3, name: '전체 평균 (점선)' }]);
    add({
        name: 'sta-aov-split',
        svg: svg({ width: W, height: H, title: '편차를 둘로 쪼갠다', desc: '한 관측값의 전체 평균으로부터의 편차가 집단 간 몫과 집단 안 몫으로 나뉜다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 2. F 분포의 오른쪽 꼬리
 * ------------------------------------------------------------------ */
{
    const d1 = crdA - 1, d2 = crdN - crdA;
    const c05 = fCrit(0.05, d1, d2);
    const p = fSf(crdF, d1, d2);
    const W = 720, H = 330;
    const F = frame({ xRange: [0, 12], yRange: [0, 0.3], box: { x: 70, y: 60, w: 560, h: 210 } });
    let body = F.axes({ xLabel: 'F', yLabel: '밀도', xTicks: [2, 4, 6, 8, 10, 12], yTicks: [0.1, 0.2, 0.3] });
    const pts = [];
    for (let x = c05; x <= 12; x += 0.05) pts.push(`${F.X(x)} ${F.Y(fDens(x, d1, d2))}`);
    body += `<path d="M${F.X(c05)} ${F.Y(0)} L${pts.join(' L')} L${F.X(12)} ${F.Y(0)} Z" fill="var(--s2)" fill-opacity="0.25" stroke="none"/>`;
    const top = fDens(1e-9, d1, d2);
    const xStart = Math.log(top / 0.3) * 2 / d1 > 0 ? (() => { let x = 0; while (fDens(x + 1e-9, d1, d2) > 0.3) x += 0.005; return x; })() : 0;
    body += F.curve(x => fDens(x, d1, d2), { cls: 's1', from: xStart, to: 12, steps: 300 });
    body += F.label([xStart, 0.3], `세로축은 0.3 에서 잘랐다(0 에서 밀도 ${fmt(top, 1)})`, { dx: 10, dy: 4, cls: 'ink2', size: 'sm' });
    body += F.guide([c05, 0], [c05, 0.16]);
    body += F.label([c05, 0.16], `5% 문턱 ${fmt(c05)}`, { dy: -6, anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += F.line([[crdF, 0], [crdF, 0.1]], { cls: 's2' });
    body += F.label([crdF, 0.1], `관측 F = ${fmt(crdF)}`, { dy: -22, anchor: 'middle', cls: 'ink', size: 'sm' });
    body += F.label([crdF, 0.1], `p값 = ${fmt(p, 4)}`, { dy: -6, anchor: 'middle', cls: 'ink', size: 'sm' });
    body += txt(W / 2, 28, `F(${d1}, ${d2}) 분포 — 칠한 꼬리의 넓이가 0.05`, { anchor: 'middle', cls: 'ink bold' });
    add({
        name: 'sta-aov-ftail',
        svg: svg({ width: W, height: H, title: 'F 분포의 꼬리', desc: '집단 간 평균제곱이 집단 안 평균제곱의 몇 배여야 드문지를 F 분포의 오른쪽 꼬리가 정한다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 3. 투키 동시 구간
 * ------------------------------------------------------------------ */
{
    const q = qtukey(0.05, crdA, crdN - crdA);
    const hw = q * Math.sqrt(crdMSW / 4);
    const pairs = [['B', 'A', '비료 가 − 대조'], ['C', 'B', '비료 나 − 비료 가'], ['C', 'A', '비료 나 − 대조']];
    const W = 720, H = 300;
    const F = frame({ xRange: [-4, 12], yRange: [0, 4], box: { x: 170, y: 50, w: 480, h: 190 } });
    let body = F.axes({ xLabel: '평균 차 (g)', yLabel: '', xTicks: [-2, 2, 4, 6, 8, 10], yTicks: [], grid: true });
    pairs.forEach(([a, b, nm], i) => {
        const d = crdM[a] - crdM[b];
        const y = 3 - i;
        const hit = d - hw > 0;
        body += F.line([[d - hw, y], [d + hw, y]], { cls: hit ? 's2' : 's1' });
        body += F.dot([d, y], { cls: hit ? 'f2' : 'f1', r: 5 });
        body += txt(160, F.Y(y) + 4, nm, { anchor: 'end', cls: 'ink', size: 'sm' });
        body += F.label([d + hw, y], `(${fmt(d - hw)}, ${fmt(d + hw)})`, { dx: 8, dy: 4, cls: 'ink2', size: 'sm' });
    });
    body += F.label([0, 0], '0', { dy: 15, anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 24, `투키 95% 동시 구간 — 반폭 ${fmt(q, 3)} × √(MSW/4) = ${fmt(hw)}`, { anchor: 'middle', cls: 'ink bold' });
    body += legend(180, 288, [{ slot: 1, name: '0 을 품는 짝' }]);
    body += legend(360, 288, [{ slot: 2, name: '0 을 품지 않는 짝' }]);
    add({
        name: 'sta-aov-tukey',
        svg: svg({ width: W, height: H, title: '투키 동시 구간', desc: '세 짝의 평균 차에 대한 동시 구간 가운데 비료 나와 대조의 짝만 0 을 품지 않는다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 4. 블록 없이 · 블록 넣고 — 제곱합 막대
 * ------------------------------------------------------------------ */
{
    const all = Object.values(RCBD).flat();
    const G = mean(all);
    const tm = Object.values(RCBD).map(mean);
    const bm = [0, 1, 2, 3].map(j => mean(Object.values(RCBD).map(v => v[j])));
    const SST = sum(all.map(x => (x - G) ** 2));
    const SStr = 4 * sum(tm.map(m => (m - G) ** 2));
    const SSbl = 3 * sum(bm.map(m => (m - G) ** 2));
    const SSE = SST - SStr - SSbl;
    const F1 = (SStr / 2) / ((SST - SStr) / 9);
    const F2 = (SStr / 2) / (SSE / 6);
    const p1 = fSf(F1, 2, 9), p2 = fSf(F2, 2, 6);
    const W = 720, H = 300;
    const x0 = 150, scale = 480 / SST;
    let body = txt(W / 2, 26, `같은 자료, 총제곱합 ${fmt(SST, 0)} 을 어떻게 나누나`, { anchor: 'middle', cls: 'ink bold' });
    const row = (y, parts, label, note) => {
        let x = x0, s = txt(x0 - 10, y + 22, label, { anchor: 'end', cls: 'ink bold' });
        for (const [v, col, nm] of parts) {
            const w = v * scale;
            s += rect(x, y, w, 34, { fill: `var(--${col})`, op: 0.55, stroke: 'var(--ink2)' });
            const inside = w > 70;
            s += txt(inside ? x + w / 2 : x + w + 4, inside ? y + 22 : y - 6, `${nm} ${fmt(v, 0)}`, { anchor: inside ? 'middle' : 'start', cls: 'ink', size: 'sm' });
            x += w;
        }
        s += txt(x0, y + 54, note, { cls: 'ink2', size: 'sm' });
        return s;
    };
    body += row(60, [[SStr, 's1', '처리'], [SST - SStr, 's3', '집단 안']], '블록 없이',
        `잔차 평균제곱 ${fmt((SST - SStr) / 9)} (자유도 9) → F = ${fmt(F1)}, p값 ${fmt(p1, 3)}`);
    body += row(170, [[SStr, 's1', '처리'], [SSbl, 's2', '블록'], [SSE, 's3', '잔차']], '블록 넣고',
        `잔차 평균제곱 ${fmt(SSE / 6)} (자유도 6) → F = ${fmt(F2)}, p값 ${fmt(p2, 3)}`);
    add({
        name: 'sta-aov-block',
        svg: svg({ width: W, height: H, title: '블록 제곱합이 잔차에서 빠진다', desc: '블록을 넣으면 집단 안 제곱합의 대부분이 블록 몫으로 옮겨 가 잔차가 작아지고 처리 F 가 커진다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 5. 2×2 칸 평균과 분산분석표의 상호작용 행
 * ------------------------------------------------------------------ */
{
    const cm = Object.fromEntries(Object.entries(FAC).map(([k, v]) => [k, mean(v)]));
    const all = Object.values(FAC).flat();
    const effA = (cm['35,1'] + cm['35,2'] - cm['25,1'] - cm['25,2']) / 2;
    const effB = (cm['25,2'] + cm['35,2'] - cm['25,1'] - cm['35,1']) / 2;
    const effAB = ((cm['35,2'] - cm['25,2']) - (cm['35,1'] - cm['25,1'])) / 2;
    const n = all.length;
    const ss = { A: n * effA ** 2 / 4, B: n * effB ** 2 / 4, AB: n * effAB ** 2 / 4 };
    const SSW = sum(Object.entries(FAC).flatMap(([k, v]) => v.map(x => (x - cm[k]) ** 2)));
    const MSW = SSW / (n - 4);
    const W = 760, H = 360;
    const F = frame({ xRange: [0, 1], yRange: [6, 26], box: { x: 70, y: 60, w: 300, h: 230 } });
    let body = F.axes({ xLabel: '', yLabel: '높이 (mm)', xTicks: [], yTicks: [10, 15, 20, 25] });
    const xs = { 25: 0.2, 35: 0.8 };
    for (const [yeast, cls, dash] of [['1', 's1', null], ['2', 's2', '7 4']]) {
        const pts = [25, 35].map(t => [xs[t], cm[`${t},${yeast}`]]);
        body += F.line(pts, { cls, dash });
        for (const t of [25, 35]) FAC[`${t},${yeast}`].forEach((v, i) => {
            body += F.dot([xs[t] + (i - 1) * 0.03 + (yeast === '2' ? 0.012 : -0.012), v], { cls: cls === 's1' ? 'f1' : 'f2', r: 3 });
        });
        body += F.label(pts[1], `효모 ${yeast} g`, { dx: 34, dy: 4, cls: 'ink', size: 'sm' });
    }
    body += F.label([0.2, 6], '25 °C', { dy: 16, anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += F.label([0.8, 6], '35 °C', { dy: 16, anchor: 'middle', cls: 'ink2', size: 'sm' });
    // 분산분석표
    const tx = 470, ty = 90;
    body += txt(tx, ty, '요인', { cls: 'ink bold', size: 'sm' });
    body += txt(tx + 110, ty, '제곱합', { anchor: 'end', cls: 'ink bold', size: 'sm' });
    body += txt(tx + 160, ty, '자유도', { anchor: 'end', cls: 'ink bold', size: 'sm' });
    body += txt(tx + 230, ty, 'F', { anchor: 'end', cls: 'ink bold', size: 'sm' });
    const rows = [['온도', ss.A, 1], ['효모', ss.B, 1], ['온도 × 효모', ss.AB, 1], ['칸 안(잔차)', SSW, n - 4]];
    rows.forEach(([nm, v, df], i) => {
        const y = ty + 28 * (i + 1);
        const hot = nm === '온도 × 효모';
        if (hot) body += rect(tx - 8, y - 17, 250, 24, { fill: 'var(--s2)', op: 0.18, stroke: 'var(--s2)' });
        body += txt(tx, y, nm, { cls: 'ink', size: 'sm' });
        body += txt(tx + 110, y, fmt(v, 0), { anchor: 'end', cls: 'ink', size: 'sm' });
        body += txt(tx + 160, y, String(df), { anchor: 'end', cls: 'ink', size: 'sm' });
        body += txt(tx + 230, y, i < 3 ? fmt(v / MSW, 1) : '', { anchor: 'end', cls: 'ink', size: 'sm' });
    });
    body += txt(tx, ty + 170, `25 °C → 35 °C 의 차: 효모 1 g 에서 ${fmt(cm['35,1'] - cm['25,1'], 0)}, 2 g 에서 ${fmt(cm['35,2'] - cm['25,2'], 0)}`, { cls: 'ink2', size: 'sm' });
    body += txt(tx, ty + 190, `두 차의 차의 절반 ${fmt(effAB, 0)} 이 상호작용 효과`, { cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 28, '선이 벌어진 만큼이 분산분석표의 상호작용 행이다', { anchor: 'middle', cls: 'ink bold' });
    add({
        name: 'sta-aov-interact',
        svg: svg({ width: W, height: H, title: '2×2 칸 평균과 상호작용', desc: '두 선이 평행하지 않고 벌어진 정도가 분산분석표의 온도×효모 행 제곱합으로 나온다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 6. 공분산분석 — 평행선 둘 사이의 세로 간격
 * ------------------------------------------------------------------ */
{
    const T = { x: [32, 37, 42, 47, 52], y: [46, 51, 52, 59, 62] };
    const C = { x: [30, 35, 40, 45, 50], y: [43, 44, 50, 56, 57] };
    const sxx = g => sum(g.x.map(v => (v - mean(g.x)) ** 2));
    const sxy = g => sum(g.x.map((v, i) => (v - mean(g.x)) * (g.y[i] - mean(g.y))));
    const b = (sxy(T) + sxy(C)) / (sxx(T) + sxx(C));
    const adj = (mean(T.y) - mean(C.y)) - b * (mean(T.x) - mean(C.x));
    const W = 720, H = 360;
    const F = frame({ xRange: [26, 56], yRange: [38, 66], box: { x: 70, y: 60, w: 540, h: 240 } });
    let body = F.axes({ xLabel: '기저값', yLabel: '사후값', xTicks: [30, 35, 40, 45, 50, 55], yTicks: [40, 45, 50, 55, 60, 65] });
    const lineOf = g => x => mean(g.y) + b * (x - mean(g.x));
    body += F.curve(lineOf(T), { cls: 's2', from: 28, to: 54 });
    body += F.curve(lineOf(C), { cls: 's1', from: 28, to: 54, dash: '7 4' });
    T.x.forEach((x, i) => { body += F.dot([x, T.y[i]], { cls: 'f2', r: 4.5 }); });
    C.x.forEach((x, i) => { body += F.dot([x, C.y[i]], { cls: 'f1', r: 4.5 }); });
    const xm = 41;
    body += arw(F.X(xm), F.Y(lineOf(C)(xm)), F.X(xm), F.Y(lineOf(T)(xm)) + 4, { col: 'ink2', width: 1.8 });
    body += F.guide([xm, (lineOf(C)(xm) + lineOf(T)(xm)) / 2], [35, 60]);
    body += F.label([35, 60], `조정된 처리 효과 ${fmt(adj, 1)}`, { dx: -4, dy: -4, anchor: 'end', cls: 'ink', size: 'sm' });
    body += F.label([54, lineOf(T)(54)], '처리', { dx: 6, dy: 4, cls: 'ink', size: 'sm' });
    body += F.label([54, lineOf(C)(54)], '대조', { dx: 6, dy: 4, cls: 'ink', size: 'sm' });
    body += txt(W / 2, 26, `공통 기울기 ${fmt(b, 1)} 의 평행선 둘 — 날 평균 차 ${fmt(mean(T.y) - mean(C.y), 0)} 에서 기저 차 몫을 뺀다`, { anchor: 'middle', cls: 'ink bold' });
    body += legend(90, 345, [{ slot: 2, name: '처리 (실선)' }]);
    body += legend(240, 345, [{ slot: 1, name: '대조 (점선)' }]);
    add({
        name: 'sta-aov-ancova',
        svg: svg({ width: W, height: H, title: '공분산분석의 평행선', desc: '기저값이 같은 자리에서 두 평행선의 세로 간격이 조정된 처리 효과다', body }),
    });
}

export default figures;
