/**
 * 통계학 4장(표본조사)의 그림. 이름은 전부 `sta-srv-` 로 시작한다.
 *
 * 함정(figures/statistics-infer.mjs 머리 주석과 같다)
 *   라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0`, 나머지는 유니코드.
 *   lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않는다.
 *   lib 의 px() 에 cls 'ark'·'ar1' 을 주지 않는다(선이 사라진다). 그림 안에 한자를 쓰지 않는다.
 *
 * 난수를 쓰지 않는다. 모든 곡선은 본문의 식으로 계산한다.
 *   층화: 표준오차 = √(층 안 분산 몫 × 전체 분산 / n), 본문 층화 예제의 수(전체 분산 366, n = 100)
 *   설계효과: 1 + (m − 1)ρ
 *   무응답 편향: (1 − r) × (응답자 평균 − 무응답자 평균)
 *   큰 비확률표본의 오차: |ρ| × √((1 − f)/f) × σ (Meng 2018), 비율이므로 σ = 0.5 로 잡는다
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
const pdot = (x, y, col = 'var(--s1)', r = 3.5, op = 1) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}" fill-opacity="${op}"/>`;
const odot = (x, y, col = 'var(--ink2)', r = 3) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="none" stroke="${col}" stroke-width="1.2"/>`;
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fx = neg, fy = neg, xLabel, yLabel, grid = true }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) {
        out.push(ln([[fr.X(t), fr.Y(y0)], [fr.X(t), fr.Y(y0) + 4]], { sw: 1 }));
        out.push(txt(fr.X(t), fr.Y(y0) + 16, fx(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        if (grid) out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
        out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fy(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) out.push(txt(fr.X(x1), fr.Y(y0) + 34, xLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) - 4, fr.Y(y1) - 14, yLabel, { anchor: 'start', cls: 'ink2', size: 'sm' }));
    return out.join('');
}
const normPdf = (x, mu, sd) => Math.exp(-((x - mu) ** 2) / (2 * sd * sd)) / (sd * Math.sqrt(2 * Math.PI));

/* ------------------------------------------------------------------ *
 * sta-srv-strata — 같은 n, 세 방식의 표본평균 분포
 * ------------------------------------------------------------------ */
{
    const total = 366, n = 100, mu = 62;
    const cases = [
        { share: 1, cls: 's2', name: '단순무작위' },
        { share: 0.7, cls: 's3', name: '층화 — 층 안 분산 70%' },
        { share: 150 / 366, cls: 's1', name: '층화 — 층 안 분산 41%(본문 예제)' },
    ].map(c => ({ ...c, se: Math.sqrt((c.share * total) / n) }));
    const W = 760, H = 380;
    const top = normPdf(mu, mu, cases[2].se) * 1.12;
    const fr = frame({ xRange: [55, 69], yRange: [0, top], box: { x: 50, y: 60, w: 650, h: 250 } });
    let body = axesEdge(fr, { x0: 55, x1: 69, y0: 0, y1: top, xTicks: [56, 58, 60, 62, 64, 66, 68], xLabel: '표본평균 ȳ' })
        .replace(/<path class="ax"[^>]*V[^>]*\/>/, '');
    for (const c of cases) body += fr.curve(x => normPdf(x, mu, c.se), { from: 55, to: 69, cls: c.cls, steps: 200 });
    body += ln([[fr.X(mu), fr.Y(0)], [fr.X(mu), fr.Y(top * 0.98)]], { sw: 1, dash: '3 3' });
    body += txt(fr.X(mu) - 6, fr.Y(top * 0.95), '모평균 62', { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += legend(430, 80, cases.map(c => ({ slot: { s1: 1, s2: 2, s3: 3 }[c.cls], name: `${c.name}: 표준오차 ${c.se.toFixed(2)}` })).reverse());
    body += txt(W / 2, 30, '두 층(비중 0.6·0.4, 층 평균 50·80)에서 n = 100. 세 곡선 모두 모평균 62 를 중심으로 하고 폭만 다르다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({ name: 'sta-srv-strata', svg: svg({ width: W, height: H, title: '층 안이 고를수록 층화 표본평균이 덜 흔들린다', desc: '같은 표본 크기 100 에서 단순무작위추출, 층 안 분산 몫이 70% 인 층화, 41% 인 층화의 표본평균 분포. 층 안이 고를수록 분포가 좁다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-srv-deff — 설계효과와 유효 표본 크기
 * ------------------------------------------------------------------ */
{
    const ms = [{ m: 5, cls: 's3', slot: 3 }, { m: 10, cls: 's1', slot: 1 }, { m: 25, cls: 's2', slot: 2 }];
    const PW = 450, PH = 330, GAP = 16, W = 2 * PW + GAP + 20, H = PH + 20;
    let body = '';
    {
        const x0 = 10, y0 = 10;
        body += panel(x0, y0, PW, PH, '설계효과 1 + (m − 1)ρ', '집락 크기 m 이 클수록 기울기가 가파르다');
        const fr = frame({ xRange: [0, 0.2], yRange: [0, 6], box: { x: x0 + 50, y: y0 + 60, w: PW - 90, h: PH - 120 } });
        body += axesEdge(fr, { x0: 0, x1: 0.2, y0: 0, y1: 6, xTicks: [0, 0.05, 0.1, 0.15, 0.2], yTicks: [1, 2, 3, 4, 5, 6], fx: String, xLabel: '집락 내 상관 ρ', yLabel: '설계효과' });
        for (const c of ms) {
            // 세로 범위(6)를 넘는 선은 6 에 닿는 자리에서 자른다
            const xEnd = Math.min(0.2, 5 / (c.m - 1));
            const yEnd = 1 + (c.m - 1) * xEnd;
            body += fr.line([[0, 1], [xEnd, yEnd]], { cls: c.cls });
            body += txt(fr.X(xEnd) - 4, fr.Y(yEnd) - 8, `m = ${c.m}`, { anchor: 'end', cls: 'ink', size: 'sm' });
        }
        body += pdot(fr.X(0.05), fr.Y(2.2), 'var(--s2)', 4);
        body += txt(fr.X(0.004), fr.Y(5.2), '채운 점: 본문 예제', { cls: 'ink2', size: 'sm' }) + txt(fr.X(0.004), fr.Y(5.2) + 16, 'm = 25, ρ = 0.05 → 2.2', { cls: 'ink2', size: 'sm' });
    }
    {
        const x0 = 10 + PW + GAP, y0 = 10;
        body += panel(x0, y0, PW, PH, '학생 1,000명을 집락으로 뽑으면', '유효 표본 크기 = 1000 ÷ 설계효과');
        const fr = frame({ xRange: [0, 0.2], yRange: [0, 1000], box: { x: x0 + 56, y: y0 + 60, w: PW - 96, h: PH - 120 } });
        body += axesEdge(fr, { x0: 0, x1: 0.2, y0: 0, y1: 1000, xTicks: [0, 0.05, 0.1, 0.15, 0.2], yTicks: [200, 400, 600, 800, 1000], fx: String, xLabel: '집락 내 상관 ρ', yLabel: '유효 표본 크기' });
        for (const c of ms) {
            body += fr.curve(r => 1000 / (1 + (c.m - 1) * r), { from: 0, to: 0.2, cls: c.cls });
            const v = 1000 / (1 + (c.m - 1) * 0.2);
            // 선이 오른쪽으로 내려가므로 라벨은 끝점 아래에 두어야 선과 겹치지 않는다
            body += txt(fr.X(0.2) - 2, fr.Y(v) + 16, `m = ${c.m}: ${Math.round(v)}`, { anchor: 'end', cls: 'ink', size: 'sm' });
        }
        body += pdot(fr.X(0.05), fr.Y(1000 / 2.2), 'var(--s2)', 4);
        body += txt(fr.X(0.05) + 8, fr.Y(1000 / 2.2) - 8, `≈ ${Math.round(1000 / 2.2)}`, { cls: 'ink2', size: 'sm' });
    }
    add({ name: 'sta-srv-deff', svg: svg({ width: W, height: H, title: '집락 내 상관이 커지면 유효 표본이 준다', desc: '설계효과는 집락 내 상관에 대해 직선으로 커지고 기울기는 집락 크기에서 1 을 뺀 값이다. 같은 1000명이라도 유효 표본 크기는 설계효과로 나눈 만큼 준다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-srv-systematic — 주기가 있는 목록에서 계통추출
 * ------------------------------------------------------------------ */
{
    const N = 60, P = 10;
    // 층마다 10세대, 끝 세대(1호·10호)가 넓은 아파트 동의 세대 면적(㎡ 는 쓰지 않고 m² 로)
    const val = i => { const ph = (i - 1) % P; return ph === 0 || ph === P - 1 ? 110 : 60 + 3 * Math.abs(ph - 4.5); };
    const pop = Array.from({ length: N }, (_, k) => val(k + 1));
    const mu = pop.reduce((a, b) => a + b, 0) / N;
    const rows = [
        { k: 10, s: 1, title: '간격 10, 시작 1호' },
        { k: 10, s: 5, title: '간격 10, 시작 5호' },
        { k: 7, s: 3, title: '간격 7, 시작 3호' },
    ];
    const RH = 150, W = 780, H = rows.length * RH + 40;
    let body = txt(20, 24, `아파트 한 동 60세대를 호수 순서로 적은 목록. 한 층에 10세대, 양끝 세대가 넓다(모평균 ${mu.toFixed(1)} m²)`, { cls: 'ink2', size: 'sm' });
    rows.forEach((r, j) => {
        const y0 = 36 + j * RH;
        const fr = frame({ xRange: [0.5, 60.5], yRange: [40, 120], box: { x: 60, y: y0 + 26, w: 680, h: RH - 66 } });
        const idx = []; for (let i = r.s; i <= N; i += r.k) idx.push(i);
        const sm = idx.reduce((a, i) => a + pop[i - 1], 0) / idx.length;
        body += txt(60, y0 + 16, `${r.title} → 뽑힌 ${idx.length}세대 평균 ${sm.toFixed(1)} m²`, { cls: 'ink bold', size: 'sm' });
        body += axesEdge(fr, { x0: 0.5, x1: 60.5, y0: 40, y1: 120, xTicks: j === rows.length - 1 ? [1, 10, 20, 30, 40, 50, 60] : [], yTicks: [60, 110], grid: false });
        for (let i = 1; i <= N; i += 1) body += odot(fr.X(i), fr.Y(pop[i - 1]));
        for (const i of idx) body += pdot(fr.X(i), fr.Y(pop[i - 1]), 'var(--s2)', 4.5);
        body += ln([[fr.X(0.5), fr.Y(mu)], [fr.X(60.5), fr.Y(mu)]], { sw: 1, dash: '4 3' });
    });
    body += txt(740, H - 6, '목록 순서(세대 번호)', { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += legend(560, 20, [{ slot: 2, name: '뽑힌 세대(채운 점)' }]);
    add({ name: 'sta-srv-systematic', svg: svg({ width: W, height: H, title: '주기가 있는 목록에서 계통추출', desc: '목록에 10세대 주기가 있을 때 간격 10 으로 뽑으면 한 위상만 뽑혀 평균이 크게 어긋나고, 간격 7 이면 여러 위상이 고루 뽑힌다. 점선은 모평균', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-srv-nonresponse — 무응답 편향 등고선
 * ------------------------------------------------------------------ */
{
    const W = 720, H = 420;
    const fr = frame({ xRange: [0.1, 1], yRange: [0, 30], box: { x: 70, y: 62, w: 560, h: 280 } });
    let body = axesEdge(fr, { x0: 0.1, x1: 1, y0: 0, y1: 30, xTicks: [0.1, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [5, 10, 15, 20, 25, 30], fx: v => `${Math.round(v * 100)}%`, xLabel: '응답률 r', yLabel: '응답자 − 무응답자 차이(%p)' });
    const levels = [{ b: 2, cls: 's3' }, { b: 5, cls: 's1' }, { b: 10, cls: 's2' }];
    for (const L of levels) {
        const to = Math.min(1 - L.b / 30, 0.995);
        body += fr.curve(r => L.b / (1 - r), { from: 0.1, to, cls: L.cls });
        const lx = 0.13;
        body += txt(fr.X(lx), fr.Y(L.b / (1 - lx)) - 14, `편향 ${L.b}%p`, { cls: 'ink', size: 'sm' });
    }
    const pts = [[0.4, 10, 'A'], [0.7, 10, 'B']];
    for (const [r, d, s] of pts) {
        body += pdot(fr.X(r), fr.Y(d), 'var(--ink)', 4);
        body += txt(fr.X(r) - 4, fr.Y(d) - 8, s, { anchor: 'end', cls: 'ink bold', size: 'sm' });
    }
    body += txt(70, H - 12, 'A: 응답률 40%, 차이 10%p → 편향 6%p.   B: 응답률 70%, 차이 10%p → 편향 3%p', { cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 20, '같은 선 위의 조합은 편향이 같다. 응답률을 올려도 차이가 커지면 같은 선 위에 머문다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({ name: 'sta-srv-nonresponse', svg: svg({ width: W, height: H, title: '무응답 편향의 등고선', desc: '무응답 편향은 무응답 비율과 응답자·무응답자 차이의 곱이다. 응답률이 70% 여도 차이가 17%p 면 편향 5%p 선 위에 있다', body }) });
}

/* ------------------------------------------------------------------ *
 * sta-srv-bigdata — 큰 비확률표본의 오차(Meng 2018 의 식)
 * ------------------------------------------------------------------ */
{
    const W = 760, H = 420;
    const L = f => Math.log10(f);
    const fr = frame({ xRange: [L(0.001), L(0.5)], yRange: [0, 6], box: { x: 70, y: 60, w: 600, h: 290 } });
    const err = (rho, f) => Math.abs(rho) * Math.sqrt((1 - f) / f) * 0.5 * 100;
    const ft = [0.001, 0.01, 0.1, 0.5];
    let body = axesEdge(fr, { x0: L(0.001), x1: L(0.5), y0: 0, y1: 6, xTicks: ft.map(L), yTicks: [1, 2, 3, 4, 5, 6], fx: v => `${+(10 ** v * 100).toPrecision(2)}%`, xLabel: '모집단 가운데 자료에 든 비율 f(로그 눈금)', yLabel: '비율 추정의 오차(%p)' });
    const rhos = [{ rho: 0.005, cls: 's1', slot: 1 }, { rho: 0.01, cls: 's3', slot: 3 }, { rho: 0.05, cls: 's2', slot: 2 }];
    for (const c of rhos) {
        // y ≤ 6 인 구간만
        let lo = 0.001; while (err(c.rho, lo) > 6) lo *= 1.01;
        body += fr.curve(u => err(c.rho, 10 ** u), { from: L(lo), to: L(0.5), cls: c.cls, steps: 200 });
        body += txt(fr.X(L(lo)) + 4, fr.Y(Math.min(6, err(c.rho, lo))) + 2, `ρ = ${c.rho}`, { cls: 'ink', size: 'sm' });
    }
    const srs = 0.5 / Math.sqrt(1000) * 100;
    body += ln([[fr.X(L(0.001)), fr.Y(srs)], [fr.X(L(0.5)), fr.Y(srs)]], { sw: 1.4, dash: '6 4' });
    body += txt(fr.X(L(0.001)) + 6, fr.Y(srs) + 16, `점선: 무작위 표본 1,000명의 표준오차 ${srs.toFixed(2)}%p`, { cls: 'ink2', size: 'sm' });
    const mx = 0.01, my = err(0.005, 0.01);
    body += pdot(fr.X(L(mx)), fr.Y(my), 'var(--ink)', 4);
    body += txt(fr.X(L(mx)) - 24, fr.Y(my) + 16, 'Meng(2018)의 2016 미국 대선 예', { anchor: 'end', cls: 'ink2', size: 'sm' }) + txt(fr.X(L(mx)) - 24, fr.Y(my) + 32, `f = 1%, ρ = −0.005 → ${my.toFixed(2)}%p`, { anchor: 'end', cls: 'ink2', size: 'sm' });
    body += txt(W / 2, 20, '오차 = |ρ| × √((1 − f)/f) × σ — 응답 여부와 답의 상관 ρ 가 0 이 아니면 f 가 커져야만 준다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    add({ name: 'sta-srv-bigdata', svg: svg({ width: W, height: H, title: '큰 비확률표본의 오차', desc: '자료에 드는 일이 답과 조금만 상관돼도 모집단의 1% 를 모은 자료의 오차가 무작위 표본 1000명의 표준오차보다 크다. 오차를 줄이는 것은 자료의 개수가 아니라 비율 f 와 상관 ρ 다', body }) });
}

export default figures;
