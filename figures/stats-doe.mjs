/**
 * 통계학 5장(실험 설계)의 그림. 이름은 전부 `sta-doe-` 로 시작한다.
 *
 * 라벨에는 수식을 쓸 수 없다(<img> 로 실려 MathJax 가 닿지 않는다). 그리스 문자는
 * 유니코드, 아래첨자는 lib 의 `x~0` 표기. 그래서 라벨에 물결표를 그냥 쓰지 않는다.
 * 따옴표는 ‘ ’, HTML 엔티티 금지. 그림 안에 한자를 쓰지 않는다.
 *
 * 난수를 쓰지 않는다. 그림 안의 수(배정 개수·p값·분산비·표본 크기)는 모두
 * 이 파일이 그 자리에서 계산한다 — 손으로 적으면 본문 자료를 고칠 때 어긋난다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const fmt = (v, d = 2) => v.toFixed(d);

/** 색을 직접 넣는 꺾은선(lib 의 px 에 ar1/ark 클래스를 넘기면 선이 사라진다). */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function rect(x, y, w, h, { fill = 'none', op = 1, stroke = 'none', sw = 1 } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}
function circ(cx, cy, r, { fill = 'var(--s1)', stroke = 'none' } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="1.5"/>`;
}

/** 표준정규 누적분포(오차함수 근사, 정밀도 1e-7 정도). */
function Phi(z) {
    const t = 1 / (1 + 0.2316419 * Math.abs(z));
    const d = 0.3989422804014327 * Math.exp(-z * z / 2);
    const p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
    return z > 0 ? 1 - p : p;
}

/* ------------------------------------------------------------------ *
 * 1. 무작위화 분포 — 여섯 단위를 셋·셋으로 나누는 20가지 배정
 * ------------------------------------------------------------------ */
{
    const units = [8, 9, 11, 12, 14, 15];
    const treated = [11, 14, 15];
    const total = units.reduce((a, b) => a + b, 0);
    const diffOf = s => (2 * s - total) / 3;
    const obs = diffOf(treated.reduce((a, b) => a + b, 0));
    const diffs = [];
    for (let i = 0; i < 6; i += 1) for (let j = i + 1; j < 6; j += 1) for (let k = j + 1; k < 6; k += 1) {
        diffs.push(diffOf(units[i] + units[j] + units[k]));
    }
    const key = v => Math.round(v * 3); // 차이는 1/3 의 배수다
    const bins = new Map();
    for (const d of diffs) bins.set(key(d), (bins.get(key(d)) ?? 0) + 1);
    const extreme = diffs.filter(d => d >= obs - 1e-9).length;

    const W = 740, H = 330;
    const F = frame({ xRange: [-5, 5], yRange: [0, 4], box: { x: 60, y: 70, w: 600, h: 200 } });
    let body = F.axes({ xLabel: '평균 차', yLabel: '배정 수', xTicks: [-4, -3, -2, -1, 0, 1, 2, 3, 4], yTicks: [1, 2, 3] });
    // 관측값 이상 영역
    body += rect(F.X(obs - 0.2), F.Y(4), F.X(5) - F.X(obs - 0.2), F.Y(0) - F.Y(4), { fill: 'var(--s2)', op: 0.10 });
    for (const [k, c] of bins) {
        const x = k / 3;
        for (let h = 0; h < c; h += 1) {
            const hit = x >= obs - 1e-9;
            body += circ(F.X(x), F.Y(h + 0.5), 9, { fill: hit ? 'var(--s2)' : 'var(--s1)' });
        }
    }
    body += ln([[F.X(obs), F.Y(0)], [F.X(obs), F.Y(3.6)]], { stroke: 'var(--s2)', sw: 2, dash: '5 3' });
    body += txt(F.X(obs) - 6, F.Y(3.75), `실제 배정의 차 ${fmt(obs)}`, { anchor: 'end', cls: 'ink' });
    body += txt(F.X(3.2), F.Y(2.6), `이 이상인 배정 ${extreme}개`, { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += txt(F.X(3.2), F.Y(2.25), `한쪽 p값 = ${extreme}/${diffs.length} = ${fmt(extreme / diffs.length)}`, { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 30, `여섯 단위를 셋·셋으로 나누는 배정 ${diffs.length}가지 — 점 하나가 배정 하나`, { anchor: 'middle', cls: 'ink bold' });
    body += legend(70, 312, [{ slot: 1, name: '관측보다 작은 차를 주는 배정' }]);
    body += legend(330, 312, [{ slot: 2, name: '관측 이상의 차를 주는 배정' }]);
    add({
        name: 'sta-doe-randdist',
        svg: svg({ width: W, height: H, title: '무작위화 분포', desc: '여섯 단위의 스무 가지 배정에서 나오는 평균 차의 분포와 실제 배정의 차', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 2. 짝지음의 이득 — 차이의 분산이 1 − ρ 배
 * ------------------------------------------------------------------ */
{
    const W = 720, H = 340;
    const F = frame({ xRange: [0, 1], yRange: [0, 1.25], box: { x: 70, y: 60, w: 540, h: 220 } });
    let body = F.axes({ xLabel: 'ρ', yLabel: '분산의 비', xTicks: [0.2, 0.4, 0.6, 0.8, 1], yTicks: [0.25, 0.5, 0.75, 1] });
    body += F.line([[0, 1], [1, 1]], { cls: 's1', dash: '6 4' });
    body += F.label([1, 1], '독립 두 집단 = 1', { dx: -4, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' });
    body += F.curve(r => 1 - r, { cls: 's2', from: 0, to: 1 });
    body += F.label([0.08, 0.6], '짝지음 = 1 − ρ', { cls: 'ink', size: 'sm' });
    for (const r of [0.5, 0.7, 0.9]) {
        body += F.dot([r, 1 - r], { cls: 'f2', r: 4 });
        body += F.label([r, 1 - r], `ρ=${r} → ${fmt(1 - r, 1)}`, { dx: 8, dy: -8, cls: 'ink2', size: 'sm' });
    }
    body += txt(W / 2, 28, '같은 단위 수에서 짝지은 차이의 분산 ÷ 독립 두 집단 차이의 분산', { anchor: 'middle', cls: 'ink bold' });
    body += txt(W / 2, 318, 'ρ = 같은 블록(짝) 안 두 단위의 반응 상관 — 블록끼리 다를수록 커진다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({
        name: 'sta-doe-block',
        svg: svg({ width: W, height: H, title: '짝지음의 이득', desc: '블록 안 상관 ρ 가 클수록 짝지은 차이의 분산이 독립 두 집단의 1 − ρ 배로 줄어든다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 3. 2×2 평균 그림 네 칸
 * ------------------------------------------------------------------ */
{
    const W = 760, H = 560;
    const panels = [
        { name: '가. 효과 없음', lo: [12, 12], hi: [12, 12] },
        { name: '나. 주효과만', lo: [10, 14], hi: [13, 17] },
        { name: '다. 벌어짐', lo: [10, 14], hi: [12, 22] },
        { name: '라. 교차', lo: [10, 16], hi: [16, 10] },
    ];
    let body = txt(W / 2, 26, '칸 평균을 잇는 두 선 — 평행이면 상호작용 없음', { anchor: 'middle', cls: 'ink bold' });
    panels.forEach((p, i) => {
        const ox = 50 + (i % 2) * 360, oy = 60 + Math.floor(i / 2) * 250;
        const F = frame({ xRange: [0, 1], yRange: [6, 24], box: { x: ox + 30, y: oy + 30, w: 230, h: 160 } });
        body += txt(ox + 145, oy + 14, p.name, { anchor: 'middle', cls: 'ink bold' });
        body += F.axes({ xLabel: '', yLabel: '반응', xTicks: [], yTicks: [10, 15, 20] });
        body += F.label([0.15, 6], '요인 A 낮음', { dy: 16, anchor: 'middle', cls: 'ink2', size: 'sm' });
        body += F.label([0.85, 6], '요인 A 높음', { dy: 16, anchor: 'middle', cls: 'ink2', size: 'sm' });
        const lineA = [[0.15, p.lo[0]], [0.85, p.lo[1]]];
        const lineB = [[0.15, p.hi[0]], [0.85, p.hi[1]]];
        body += F.line(lineA, { cls: 's1' });
        body += F.line(lineB, { cls: 's2', dash: '7 4' });
        for (const q of [...lineA]) body += F.dot(q, { cls: 'f1' });
        for (const q of [...lineB]) body += F.dot(q, { cls: 'f2' });
        const same = p.lo[1] === p.hi[1];
        body += F.label([0.85, p.lo[1]], 'B 낮음', { dx: 8, dy: same ? 14 : (p.lo[1] > p.hi[1] ? -4 : 12), cls: 'ink2', size: 'sm' });
        body += F.label([0.85, p.hi[1]], 'B 높음', { dx: 8, dy: same ? -6 : (p.hi[1] >= p.lo[1] ? -4 : 12), cls: 'ink2', size: 'sm' });
    });
    body += legend(240, 548, [{ slot: 1, name: '요인 B 낮음 (실선)' }]);
    body += legend(420, 548, [{ slot: 2, name: '요인 B 높음 (점선)' }]);
    add({
        name: 'sta-doe-interaction',
        svg: svg({ width: W, height: H, title: '2×2 평균 그림 네 칸', desc: '효과 없음, 주효과만, 벌어짐, 교차 — 두 선이 평행하지 않으면 상호작용이 있다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 4. 기저값을 쓰는 세 분석의 분산
 * ------------------------------------------------------------------ */
{
    const W = 720, H = 350;
    const F = frame({ xRange: [0, 1], yRange: [0, 2], box: { x: 70, y: 60, w: 520, h: 220 } });
    let body = F.axes({ xLabel: 'ρ', yLabel: '분산(사후값만 = 1)', xTicks: [0.2, 0.4, 0.5, 0.6, 0.8, 1], yTicks: [0.5, 1, 1.5, 2] });
    body += F.curve(() => 1, { cls: 's1' });
    body += F.curve(r => 2 * (1 - r), { cls: 's2', dash: '7 4' });
    body += F.curve(r => 1 - r * r, { cls: 's3' });
    body += F.guide([0.5, 0], [0.5, 1]);
    body += F.label([1, 1], '사후값만 1', { dx: 6, dy: 4, cls: 'ink', size: 'sm' });
    body += F.label([0.04, 1.92], '변화량 2(1 − ρ)', { dx: 6, dy: 0, cls: 'ink', size: 'sm' });
    body += F.label([0.06, 0.8], '공분산 조정 1 − ρ²', { cls: 'ink', size: 'sm' });
    body += F.label([0.5, 1], 'ρ = 0.5 에서 변화량이 사후값만과 같아진다', { dx: 6, dy: -10, cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 28, '무선배정 실험에서 처리 효과 추정량의 분산 — 기저-사후 상관 ρ 에 따라', { anchor: 'middle', cls: 'ink bold' });
    body += legend(90, 322, [{ slot: 1, name: '사후값만' }]);
    body += legend(230, 322, [{ slot: 2, name: '변화량 (점선)' }]);
    body += legend(400, 322, [{ slot: 3, name: '공분산 조정' }]);
    add({
        name: 'sta-doe-baseline',
        svg: svg({ width: W, height: H, title: '기저값을 쓰는 세 분석', desc: '공분산 조정의 분산은 어느 ρ 에서나 가장 작고, 변화량은 ρ 가 0.5 를 넘어야 사후값만보다 낫다', body }),
    });
}

/* ------------------------------------------------------------------ *
 * 5. 검정력 곡선 — 독립 두 집단 vs 짝지음(ρ = 0.6)
 * ------------------------------------------------------------------ */
{
    const sigma = 10, delta = 5, rho = 0.6, z = 1.959963984540054;
    const power = se => 1 - Phi(z - delta / se) + Phi(-z - delta / se);
    const pInd = n => power(Math.sqrt(2 * sigma ** 2 / n));
    const pPair = n => power(Math.sqrt(2 * sigma ** 2 * (1 - rho) / n));
    const firstN = f => { let n = 2; while (f(n) < 0.8) n += 1; return n; };
    const nInd = firstN(pInd), nPair = firstN(pPair);
    const W = 720, H = 350;
    const F = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: 70, y: 60, w: 560, h: 220 } });
    let body = F.axes({ xLabel: 'n', yLabel: '검정력', xTicks: [20, 40, 60, 80, 100], yTicks: [0.2, 0.4, 0.6, 0.8, 1] });
    body += F.curve(pInd, { cls: 's1', from: 2, to: 100 });
    body += F.curve(pPair, { cls: 's2', from: 2, to: 100, dash: '7 4' });
    body += F.line([[0, 0.8], [100, 0.8]], { cls: 's3', dash: '3 3' });
    body += F.dot([nInd, pInd(nInd)], { cls: 'f1', r: 4 });
    body += F.dot([nPair, pPair(nPair)], { cls: 'f2', r: 4 });
    body += F.label([nInd, pInd(nInd)], `독립: 집단마다 ${nInd}`, { dx: 8, dy: 18, cls: 'ink', size: 'sm' });
    body += F.label([nPair, pPair(nPair)], `짝지음: ${nPair}쌍`, { dx: -8, dy: -10, anchor: 'end', cls: 'ink', size: 'sm' });
    body += F.label([100, 0.8], '목표 0.8', { dx: -2, dy: -6, anchor: 'end', cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 28, `σ = ${sigma}, 잡을 차이 δ = ${delta}, 양쪽 α = 0.05 (정규 근사)`, { anchor: 'middle', cls: 'ink bold' });
    body += txt(W / 2, 46, 'n = 독립이면 한 집단의 단위 수, 짝지음이면 쌍의 수', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    body += legend(120, 322, [{ slot: 1, name: '독립 두 집단' }]);
    body += legend(300, 322, [{ slot: 2, name: `짝지음 ρ = ${rho} (점선)` }]);
    add({
        name: 'sta-doe-power',
        svg: svg({ width: W, height: H, title: '설계별 검정력 곡선', desc: '같은 효과를 0.8 의 확률로 잡는 데 짝지음은 독립 두 집단보다 훨씬 적은 단위로 충분하다', body }),
    });
}

export default figures;
