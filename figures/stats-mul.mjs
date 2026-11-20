/**
 * 통계학 10장(많이 검정할 때) 그림. 이름은 전부 `sta-mul-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다)
 *   - 라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 나머지는 유니코드.
 *   - lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 *   - px() 에 cls 'ark'·'ar1' 을 넘기면 선이 사라진다. 화살표는 아래 arw() 를 쓴다.
 *   - 그림 안에 한자를 쓰지 않는다.
 * 승자의 저주·유형 S·M 은 절단정규 식으로 정확히 계산한다. 깔때기 그림만 난수를 쓰며
 * 씨앗을 고정한 선형합동생성기를 쓴다. 그림 안의 개수·평균은 그 자리에서 계산한다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const f2 = v => v.toFixed(2);
const f3 = v => v.toFixed(3);

/* ---------- 분포 소도구 ---------- */
const erf = x => {
    const s = Math.sign(x); const a = Math.abs(x);
    const t = 1 / (1 + 0.5 * a);
    const y = 1 - t * Math.exp(-a * a - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
        + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
        + t * (-0.82215223 + t * 0.17087277)))))))));
    return s * y;
};
const Phi = z => 0.5 * (1 + erf(z / Math.SQRT2));
const phi = z => Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
const Z = 1.959964;

/* ---------- 화소 소도구 ---------- */
function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.8, both = false, head = true } = {}) {
    const col = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' }[cls];
    const mk = { s1: 'ar1', s2: 'ar2', s3: 'ar3', ark: 'ark' }[cls];
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round"${both ? ` marker-start="url(#${mk})"` : ''}${head ? ` marker-end="url(#${mk})"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}
function rect(x, y, w, h, { fill = 'none', op = 1, stroke = 'none', sw = 1 } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}
function ln(pts, { stroke = 'var(--ink2)', sw = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')}"/>`;
}

/* ================================================================== *
 * sta-mul-holm-bh — 정렬한 p값과 세 문턱
 * ================================================================== */
{
    const P = [0.001, 0.004, 0.006, 0.012, 0.016, 0.029, 0.041, 0.21, 0.47, 0.78];
    const m = P.length; const a = 0.05;
    const bonf = P.filter(p => p <= a / m).length;
    let holm = 0; for (let i = 0; i < m; i += 1) { if (P[i] <= a / (m - i)) holm += 1; else break; }
    let bh = 0; P.forEach((p, i) => { if (p <= ((i + 1) * a) / m) bh = i + 1; });
    const W = 740; const H = 430;
    const yMax = 0.06;
    const fr = frame({ xRange: [0, 10.8], yRange: [0, yMax], box: { x: 80, y: 60, w: 540, h: 300 } });
    const body = [];
    body.push(txt(W / 2, 26, `같은 p값 열 개 — 본페로니 ${bonf}개, 홀름 ${holm}개, BH ${bh}개를 기각한다`, { anchor: 'middle', cls: 'ink bold' }));
    body.push(fr.axes({ xLabel: '순위 k', yLabel: 'p값', xTicks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], yTicks: [0.01, 0.02, 0.03, 0.04, 0.05] }));
    // 본페로니: 수평
    body.push(fr.line([[0.5, a / m], [10.5, a / m]], { cls: 's1' }));
    // 홀름: 계단
    const hp = []; for (let k = 1; k <= m; k += 1) { const t = a / (m - k + 1); hp.push([k - 0.5, t], [k + 0.5, t]); }
    body.push(fr.line(hp, { cls: 's3', dash: '6 3' }));
    // BH: 기울기
    body.push(fr.line([[0, 0], [10.5, (10.5 * a) / m]], { cls: 's2' }));
    P.forEach((p, i) => {
        const k = i + 1;
        if (p <= yMax) {
            body.push(`<circle cx="${fr.X(k)}" cy="${fr.Y(p)}" r="4.5" fill="${k <= bh ? 'var(--ink)' : 'none'}" stroke="var(--ink)" stroke-width="1.5"/>`);
        } else {
            body.push(arw(fr.X(k), fr.Y(yMax) + 18, fr.X(k), fr.Y(yMax) + 2, { cls: 'ark', width: 1.4 }));
            body.push(txt(fr.X(k), fr.Y(yMax) + 30, String(p), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    body.push(fr.label([10.5, a / m], '본페로니 α/m', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }));
    body.push(fr.label([10.5, a], '홀름 α/(m − k + 1)', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }));
    body.push(fr.label([10.5, (10.5 * a) / m], 'BH kα/m', { dx: 6, dy: -6, cls: 'ink', size: 'sm' }));
    body.push(legend(90, 390, [{ slot: 1, name: '본페로니 (수평선)' }, { slot: 3, name: '홀름 (계단, 점선)' }, { slot: 2, name: 'BH (기울어진 선)' }]));
    body.push(txt(330, 392, '채운 점 = BH 가 기각하는 것, 빈 점 = 기각하지 않는 것', { cls: 'ink2', size: 'sm' }));
    body.push(txt(330, 410, 'm = 10, α = q = 0.05. 0.06 을 넘는 셋은 위 화살표로 표시', { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-mul-holm-bh', svg: svg({ width: W, height: H, title: '본페로니·홀름·BH 의 문턱', body: body.join('') }) });
}

/* ================================================================== *
 * sta-mul-winner — 유의한 것만 모은 추정값의 평균
 * 추정값 ~ N(D, 1) (SE 단위), 오른쪽으로 유의(추정값 ≥ 1.96)한 것만 남긴다.
 * ================================================================== */
{
    const panel = (ox, D, tag) => {
        const a = Z - D;
        const pw = 1 - Phi(a);
        const cm = D + phi(a) / (1 - Phi(a));
        const fr = frame({ xRange: [-3, 6.2], yRange: [0, 0.46], box: { x: ox, y: 80, w: 285, h: 220 } });
        const out = [];
        out.push(txt(ox + 142, 60, `${tag} 참 효과 = ${D} SE (검정력 ${f2(pw)})`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        // 유의한 영역 칠하기
        const pts = []; for (let i = 0; i <= 60; i += 1) { const x = Z + ((6.2 - Z) * i) / 60; pts.push([fr.X(x), fr.Y(phi(x - D))]); }
        out.push(`<path d="M${fr.X(Z)} ${fr.Y(0)} L${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} L${fr.X(6.2)} ${fr.Y(0)} Z" fill="var(--s2)" fill-opacity="0.35"/>`);
        out.push(fr.axes({ xLabel: '추정값', xTicks: [-2, 0, 2, 4, 6], yTicks: [], grid: false }));
        out.push(fr.curve(x => phi(x - D), { cls: 's1' }));
        out.push(ln([[fr.X(Z), fr.Y(0)], [fr.X(Z), fr.Y(0.44)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
        out.push(txt(fr.X(Z) + 4, fr.Y(0.44) + 10, '유의 문턱 1.96', { cls: 'ink2', size: 'sm' }));
        // 참값과 조건부 평균
        out.push(ln([[fr.X(D), fr.Y(0)], [fr.X(D), fr.Y(0.41)]], { stroke: 'var(--s1)', sw: 2 }));
        out.push(ln([[fr.X(cm), fr.Y(0)], [fr.X(cm), fr.Y(0.33)]], { stroke: 'var(--s2)', sw: 2.4 }));
        out.push(arw(fr.X(D), fr.Y(0.30), fr.X(cm), fr.Y(0.30), { cls: 'ark', width: 1.4 }));
        out.push(txt(ox, 340, `참값 ${f2(D)}  →  유의한 것만의 평균 ${f2(cm)}`, { cls: 'ink', size: 'sm' }));
        out.push(txt(ox, 358, `부풀림 배율 ${f2(cm / D)} 배`, { cls: 'ink bold', size: 'sm' }));
        return out.join('');
    };
    const W = 760; const H = 400;
    const body = [];
    body.push(txt(W / 2, 26, '문턱을 넘은 것만 모으면 그 평균은 참값보다 크다 — 검정력이 낮을수록 더', { anchor: 'middle', cls: 'ink bold' }));
    body.push(panel(50, 1, '[가]'));
    body.push(panel(410, 2.8, '[나]')); // 오른쪽 패널의 축 이름 '추정값' 이 화폭 밖으로 잘리지 않게
    body.push(legend(50, 382, [{ slot: 1, name: '추정값의 분포와 참값(세로선)' }, { slot: 2, name: '유의한 부분과 그 평균(세로선)' }]));
    add({ name: 'sta-mul-winner', svg: svg({ width: W, height: H, title: '승자의 저주', body: body.join('') }) });
}

/* ================================================================== *
 * sta-mul-type-sm — 검정력에 따른 유형 S·M 오류 (양쪽 검정)
 * ================================================================== */
{
    const sm = D => {
        const up = 1 - Phi(Z - D); const lo = Phi(-Z - D); const pw = up + lo;
        const e = D * up + phi(Z - D) - D * lo + phi(-Z - D);
        return { pw, S: lo / pw, M: e / pw / D };
    };
    const Ds = []; for (let D = 0.05; D <= 4.0001; D += 0.01) Ds.push(D);
    const rows = Ds.map(D => ({ D, ...sm(D) }));
    const W = 760; const H = 380;
    const body = [];
    body.push(txt(W / 2, 26, '검정력이 낮을 때 유의한 추정값은 부호가 틀리거나 크게 부풀려진다', { anchor: 'middle', cls: 'ink bold' }));
    // S 패널
    const fa = frame({ xRange: [0.05, 1], yRange: [0, 0.5], box: { x: 60, y: 70, w: 280, h: 230 } });
    body.push(txt(200, 56, '[가] 유형 S — 부호가 뒤집힐 확률', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    body.push(fa.axes({ xLabel: '검정력', xTicks: [0.2, 0.5, 0.8, 1], yTicks: [0.1, 0.2, 0.3, 0.4, 0.5] }));
    body.push(fa.line(rows.filter(r => r.pw <= 1).map(r => [r.pw, r.S]), { cls: 's1' }));
    const at = pw => rows.reduce((b, r) => (Math.abs(r.pw - pw) < Math.abs(b.pw - pw) ? r : b));
    const r10 = at(0.06); const r20 = at(0.2); const r80 = at(0.8);
    body.push(fa.dot([r10.pw, r10.S]));
    body.push(fa.label([r10.pw, r10.S], `검정력 ${f2(r10.pw)}: ${f2(r10.S)}`, { dx: 8, dy: 4, cls: 'ink', size: 'sm' }));
    body.push(fa.dot([r20.pw, r20.S]));
    body.push(fa.label([r20.pw, r20.S], `${f2(r20.pw)}: ${f3(r20.S)}`, { dx: 6, dy: -8, cls: 'ink', size: 'sm' }));
    // M 패널
    const fb = frame({ xRange: [0.05, 1], yRange: [1, 10], box: { x: 440, y: 70, w: 260, h: 230 } }); // 폭 260: 축 이름 '검정력' 이 화폭 안에 들게
    body.push(txt(570, 56, '[나] 유형 M — 부풀림 배율', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    body.push(fb.axes({ xLabel: '검정력', xTicks: [0.2, 0.5, 0.8, 1], yTicks: [2, 4, 6, 8, 10] }));
    body.push(fb.line(rows.filter(r => r.M <= 10).map(r => [r.pw, r.M]), { cls: 's2' }));
    body.push(fb.dot([r20.pw, r20.M], { cls: 'f2' }));
    body.push(fb.label([r20.pw, r20.M], `${f2(r20.pw)}: ${f2(r20.M)} 배`, { dx: 8, dy: -6, cls: 'ink', size: 'sm' }));
    body.push(fb.dot([r80.pw, r80.M], { cls: 'f2' }));
    body.push(fb.label([r80.pw, r80.M], `${f2(r80.pw)}: ${f2(r80.M)} 배`, { dx: -6, dy: -12, anchor: 'middle', cls: 'ink', size: 'sm' }));
    body.push(txt(60, 345, '양쪽 검정, 유의수준 0.05. 추정값이 정규(참 효과, SE²) 를 따를 때 |추정값| ≥ 1.96 SE 인 것만 남긴 정확한 값.', { cls: 'ink2', size: 'sm' }));
    body.push(txt(60, 363, '가로축 왼쪽 끝(검정력 0.05)은 참 효과가 0 에 가까운 자리다.', { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-mul-type-sm', svg: svg({ width: W, height: H, title: '유형 S 와 유형 M 오류', body: body.join('') }) });
}

/* ================================================================== *
 * sta-mul-forest — 연구 다섯과 고정효과 결합
 * ================================================================== */
{
    const th = [0.30, 0.10, 0.45, 0.20, 0.25]; const se = [0.15, 0.10, 0.20, 0.08, 0.12];
    const w = se.map(s => 1 / s ** 2); const Wt = w.reduce((s, v) => s + v, 0);
    const fe = w.reduce((s, v, i) => s + v * th[i], 0) / Wt; const sfe = 1 / Math.sqrt(Wt);
    const W = 760; const H = 360;
    const fr = frame({ xRange: [-0.2, 0.9], yRange: [0, 7], box: { x: 150, y: 50, w: 400, h: 260 } });
    const body = [];
    body.push(txt(W / 2, 26, '결합 구간(마름모)은 다섯 연구의 어느 구간보다 좁다', { anchor: 'middle', cls: 'ink bold' }));
    body.push(ln([[fr.X(0), fr.Y(0)], [fr.X(0), fr.Y(6.8)]], { stroke: 'var(--ink2)', sw: 1.2 }));
    body.push(ln([[fr.X(fe), fr.Y(0)], [fr.X(fe), fr.Y(6.8)]], { stroke: 'var(--s2)', sw: 1.2, dash: '4 3' }));
    body.push(txt(560, 44, '추정값 [95% 구간]  가중치', { cls: 'ink2', size: 'sm' }));
    th.forEach((t, i) => {
        const y = fr.Y(6 - i); const lo = t - Z * se[i]; const hi = t + Z * se[i];
        body.push(ln([[fr.X(lo), y], [fr.X(hi), y]], { stroke: 'var(--s1)', sw: 2 }));
        const sz = 4 + 10 * Math.sqrt(w[i] / Wt);
        body.push(rect(fr.X(t) - sz / 2, y - sz / 2, sz, sz, { fill: 'var(--s1)' }));
        body.push(txt(140, y + 4, `연구 ${'가나다라마'[i]}`, { anchor: 'end', cls: 'ink', size: 'sm' }));
        body.push(txt(560, y + 4, `${f2(t)} [${f2(lo)}, ${f2(hi)}]  ${(100 * w[i] / Wt).toFixed(0)}%`, { cls: 'ink', size: 'sm' }));
    });
    const y = fr.Y(0.6); const lo = fe - Z * sfe; const hi = fe + Z * sfe;
    body.push(`<path d="M${fr.X(lo)} ${y} L${fr.X(fe)} ${y - 8} L${fr.X(hi)} ${y} L${fr.X(fe)} ${y + 8} Z" fill="var(--s2)"/>`);
    body.push(txt(140, y + 4, '고정효과 결합', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    body.push(txt(560, y + 4, `${f2(fe)} [${f2(lo)}, ${f2(hi)}]`, { cls: 'ink bold', size: 'sm' }));
    for (const t of [-0.2, 0, 0.2, 0.4, 0.6, 0.8]) body.push(txt(fr.X(t), fr.Y(0) + 16, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    body.push(ln([[fr.X(-0.2), fr.Y(0)], [fr.X(0.9), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1.2 }));
    body.push(txt(150, 345, '네모 크기는 가중치(1/SE²)에 비례한다. 효과는 표준화 평균차 같은, 연구끼리 견줄 수 있는 척도다.', { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-mul-forest', svg: svg({ width: W, height: H, title: '숲 그림', body: body.join('') }) });
}

/* ================================================================== *
 * sta-mul-funnel — 대칭 깔때기와 한쪽 아래가 빈 깔때기
 * ================================================================== */
{
    let seed = 20261120;
    const rnd = () => { seed = (1103515245 * seed + 12345) % 2147483648; return seed / 2147483648; };
    const gauss = () => { const u = Math.max(rnd(), 1e-12); const v = rnd(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); };
    const truth = 0.2;
    const studies = [];
    for (let i = 0; i < 70; i += 1) { const s = 0.04 + 0.32 * rnd() ** 1.3; studies.push({ s, t: truth + s * gauss() }); }
    // 유의(양쪽 5%)하지 않은 연구는 SE 가 0.1 을 넘으면 발표되지 않는다고 놓는다
    const pub = studies.filter(o => Math.abs(o.t / o.s) >= Z || o.s <= 0.1);
    const fe = arr => { const w = arr.map(o => 1 / o.s ** 2); return arr.reduce((a, o, i) => a + w[i] * o.t, 0) / w.reduce((a, v) => a + v, 0); };
    const mean = arr => arr.reduce((a, o) => a + o.t, 0) / arr.length;
    const panel = (ox, arr, title) => {
        const fr = frame({ xRange: [-0.6, 1.0], yRange: [-0.4, 0], box: { x: ox, y: 70, w: 290, h: 240 } });
        const out = [];
        out.push(txt(ox + 145, 56, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        // 깔때기 선: truth ± 1.96 SE
        out.push(fr.line([[truth - Z * 0.36, -0.36], [truth, 0], [truth + Z * 0.36, -0.36]], { cls: 's3', dash: '5 3' }));
        out.push(ln([[fr.X(truth), fr.Y(-0.4)], [fr.X(truth), fr.Y(0)]], { stroke: 'var(--s3)', sw: 1.2 }));
        out.push(ln([[fr.X(0), fr.Y(-0.4)], [fr.X(0), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1 }));
        for (const o of arr) out.push(`<circle cx="${fr.X(o.t)}" cy="${fr.Y(-o.s)}" r="3.4" fill="var(--s1)" fill-opacity="0.8"/>`);
        out.push(ln([[fr.X(-0.6), fr.Y(-0.4)], [fr.X(1.0), fr.Y(-0.4)]], { stroke: 'var(--ink2)', sw: 1.2 }));
        out.push(ln([[fr.X(-0.6), fr.Y(-0.4)], [fr.X(-0.6), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1.2 }));
        for (const t of [-0.4, 0, 0.4, 0.8]) out.push(txt(fr.X(t), fr.Y(-0.4) + 16, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        for (const s of [0, 0.1, 0.2, 0.3]) out.push(txt(fr.X(-0.6) - 6, fr.Y(-s) + 4, String(s), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        out.push(txt(fr.X(-0.6) - 6, fr.Y(0) - 12, 'SE', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        out.push(txt(ox, 345, `연구 ${arr.length}개, 단순 평균 ${f2(mean(arr))}, 고정효과 ${f2(fe(arr))}`, { cls: 'ink', size: 'sm' }));
        return out.join('');
    };
    const W = 760; const H = 400;
    const body = [];
    body.push(txt(W / 2, 26, '작고 유의하지 않은 연구가 빠지면 아래쪽 한 귀퉁이가 빈다', { anchor: 'middle', cls: 'ink bold' }));
    body.push(panel(60, studies, '[가] 모두 발표된 경우'));
    body.push(panel(430, pub, '[나] 작은 연구는 유의할 때만 발표된 경우'));
    body.push(txt(60, 370, `가로축은 효과 추정값, 세로축은 SE(위가 0). 위로 갈수록 큰(정밀한) 연구다.`, { cls: 'ink2', size: 'sm' }));
    body.push(txt(60, 388, `초록 세로선은 참값 ${truth}, 점선은 참값 ± 1.96 SE.`, { cls: 'ink2', size: 'sm' }));
    add({ name: 'sta-mul-funnel', svg: svg({ width: W, height: H, title: '깔때기 그림', body: body.join('') }) });
}

export default figures;
