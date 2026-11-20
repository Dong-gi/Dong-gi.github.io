/**
 * 통계학 17장(결측 자료)의 그림. 이름은 전부 `sta-mis-` 로 시작한다.
 *
 * 함정(statistics-infer.mjs 머리 주석과 같다):
 *   라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 그리스 문자는 유니코드.
 *   그래서 라벨에 물결표를 그냥 쓰면 아래첨자로 먹힌다 — 범위는 ‘에서’ 나 ‘–’ 로 적는다.
 *   lib 의 px() 에 cls 'ark' 를 주면 선이 사라지므로 색을 직접 넣는 선을 쓴다.
 *   그림 안에 한자를 쓰지 않는다.
 *
 * 그림 속 수치(평균·상관계수·문턱)는 모두 이 파일이 그 자리에서 계산한다.
 * 난수는 평균 대체 그림의 산점도에만 쓰고, 씨앗을 고정한 선형합동생성기로 뽑는다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const fx = (v, d = 2) => v.toFixed(d).replace('-', '−');
const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink: 'var(--ink)', ink2: 'var(--ink2)' };
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}
function crv(fr, f, from, to, { col = 's1', dash, sw = 2.2, steps = 200 } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    return ln(pts, { stroke: COL[col], sw, dash });
}
function area(fr, f, from, to, col, op = 0.25, steps = 160) {
    const pts = [[fr.X(from), fr.Y(0)]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    pts.push([fr.X(to), fr.Y(0)]);
    return `<path d="M${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="${COL[col]}" fill-opacity="${op}" stroke="none"/>`;
}
function xaxis(fr, x0, x1, ticks, { y0 = 0, fmt = v => fx(v, 0) } = {}) {
    const y = fr.Y(y0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { sw: 1 }));
        out.push(txt(fr.X(t), y + 18, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}
function yaxis(fr, x0, y0, y1, ticks, { fmt = String } = {}) {
    const x = fr.X(x0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${x} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[x - 5, fr.Y(t)], [x, fr.Y(t)]], { sw: 1 }));
        out.push(txt(x - 8, fr.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}
function lcg(seed) {
    let s = seed >>> 0;
    return () => {
        s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
        return (s + 0.5) / 4294967296;
    };
}
const corr = (xs, ys) => {
    const n = xs.length;
    const mx = xs.reduce((a, b) => a + b, 0) / n, my = ys.reduce((a, b) => a + b, 0) / n;
    let sxy = 0, sxx = 0, syy = 0;
    for (let i = 0; i < n; i += 1) {
        sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) ** 2; syy += (ys[i] - my) ** 2;
    }
    return sxy / Math.sqrt(sxx * syy);
};

/* ------------------------------------------------------------------ *
 * sta-mis-indist — 관측된 부분이 같은데 빠진 부분이 다른 네 세계
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860, H = 280;
    const worlds = [
        { name: '세계 A', miss1: 24, note: '응답이 합격과 무관' },
        { name: '세계 B', miss1: 9, note: '합격한 사람이 더 잘 응답' },
        { name: '하한 세계', miss1: 0, note: '빠진 40명이 모두 불합격' },
        { name: '상한 세계', miss1: 40, note: '빠진 40명이 모두 합격' },
    ];
    const x0 = 120, unit = 4.4, bh = 34, top = 50, gap = 22;
    const out = [BG];
    out.push(txt(x0, 28, '진한 칸: 관측된 응답 60명(네 줄 모두 같다) · 연한 점선 칸: 빠진 40명(세계마다 다르다)', { cls: 'ink2', size: 'sm' }));
    worlds.forEach((w, i) => {
        const y = top + i * (bh + gap);
        const seg = [
            { n: 36, lab: '합격 36', col: 's1', op: 0.75, dash: undefined },
            { n: 24, lab: '불합격 24', col: 's2', op: 0.75, dash: undefined },
            { n: w.miss1, lab: `합격 ${w.miss1}`, col: 's1', op: 0.15, dash: '4 3' },
            { n: 40 - w.miss1, lab: `불합격 ${40 - w.miss1}`, col: 's2', op: 0.15, dash: '4 3' },
        ];
        let x = x0;
        out.push(txt(x0 - 12, y + bh / 2 + 5, w.name, { anchor: 'end', cls: 'ink bold' }));
        for (const s of seg) {
            if (s.n === 0) continue;
            const ww = s.n * unit;
            out.push(box(x, y, ww, bh, { fill: COL[s.col], op: s.op, stroke: COL[s.col], sw: 1.4, rx: 2, dash: s.dash }));
            if (ww > 50) out.push(txt(x + ww / 2, y + bh / 2 + 4, s.lab, { anchor: 'middle', cls: s.op > 0.5 ? 'ink bold' : 'ink', size: 'sm' }));
            else if (ww > 14) out.push(txt(x + ww / 2, y + bh / 2 + 4, String(s.n), { anchor: 'middle', cls: 'ink', size: 'sm' }));
            x += ww;
        }
        const p1 = (36 + w.miss1) / 100;
        out.push(txt(x0 + 100 * unit + 14, y + 15, `합격률 ${fx(p1)}`, { cls: 'ink bold', size: 'sm' }));
        out.push(txt(x0 + 100 * unit + 14, y + 31, w.note, { cls: 'ink2', size: 'sm' }));
    });
    return {
        name: 'sta-mis-indist',
        svg: svg({ width: W, height: H, title: '관측된 자료가 같은 네 세계', desc: '응답한 60명의 자료는 네 세계에서 같지만 전체 합격률은 0.36 에서 0.76 사이 어디든 될 수 있다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-mis-cc — 큰 값이 잘 빠지면 남은 자료가 왼쪽으로 밀린다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900, H = 340;
    const mu = 50, sd = 10;
    const phi = y => Math.exp(-((y - mu) ** 2) / (2 * sd * sd)) / (Math.sqrt(2 * Math.PI) * sd);
    const resp = y => 1 / (1 + Math.exp((y - 55) / 5));
    // 응답률과 남은 자료의 평균 — 수치적분
    let a = 0, b = 0;
    for (let i = 0; i <= 4000; i += 1) {
        const y = (100 * i) / 4000;
        a += phi(y) * resp(y) * 0.025; b += y * phi(y) * resp(y) * 0.025;
    }
    const rate = a, mObs = b / a;
    const out = [];
    // 왼쪽: 응답 확률
    out.push(panel(15, 15, 330, 305, '응답 확률', '값이 클수록 잘 빠진다'));
    const f1 = frame({ xRange: [10, 90], yRange: [0, 1], box: { x: 60, y: 75, w: 250, h: 190 } });
    out.push(crv(f1, resp, 10, 90, { col: 's2', sw: 2.6 }));
    out.push(xaxis(f1, 10, 90, [10, 30, 50, 70, 90]));
    out.push(yaxis(f1, 10, 0, 1, [0, 0.5, 1]));
    out.push(txt(f1.X(90) + 4, f1.Y(0) + 32, 'y (점수)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    out.push(txt(f1.X(57), f1.Y(0.55), 'P(R = 1 | y)', { cls: 'ink', size: 'sm' }));
    // 오른쪽: 분포
    out.push(panel(360, 15, 525, 305, '전체 분포와 남은 자료', `응답률 ${fx(rate)}`));
    const f2 = frame({ xRange: [10, 90], yRange: [0, 0.045], box: { x: 400, y: 75, w: 440, h: 190 } });
    out.push(area(f2, y => phi(y) * resp(y), 10, 90, 's1', 0.3));
    out.push(crv(f2, phi, 10, 90, { col: 's1', dash: '7 5', sw: 2 }));
    out.push(crv(f2, y => phi(y) * resp(y), 10, 90, { col: 's1', sw: 2.4 }));
    out.push(xaxis(f2, 10, 90, [10, 30, 50, 70, 90]));
    out.push(ln([[f2.X(mu), f2.Y(0)], [f2.X(mu), f2.Y(0.043)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '5 4' }));
    out.push(ln([[f2.X(mObs), f2.Y(0)], [f2.X(mObs), f2.Y(0.036)]], { stroke: 'var(--ink)', sw: 1.6 }));
    out.push(txt(f2.X(mu) + 6, f2.Y(0.043) + 4, `전체 평균 ${fx(mu, 1)}`, { cls: 'ink2', size: 'sm' }));
    out.push(txt(f2.X(mObs) - 6, f2.Y(0.036) - 4, `남은 자료 평균 ${fx(mObs, 1)}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    out.push(txt(f2.X(70), f2.Y(0.03), '점선: 모든 사람', { cls: 'ink2', size: 'sm' }));
    out.push(txt(f2.X(70), f2.Y(0.026), '칠한 부분: 응답한 사람', { cls: 'ink2', size: 'sm' }));
    out.push(txt(f2.X(90) + 4, f2.Y(0) + 32, 'y (점수)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-mis-cc',
        svg: svg({ width: W, height: H, title: '큰 값이 잘 빠질 때의 완전 사례', desc: '값이 클수록 응답하지 않으면 남은 자료의 분포가 왼쪽으로 밀리고 평균이 내려간다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-mis-meanimp — 평균 대체는 한 줄로 서서 상관을 깎고, 회귀 대체는 선 위에 서서 부풀린다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900, H = 380;
    const rand = lcg(17017);
    const nrm = () => Math.sqrt(-2 * Math.log(rand())) * Math.cos(2 * Math.PI * rand());
    const n = 40, rho = 0.7;
    const X = [], Y = [];
    for (let i = 0; i < n; i += 1) {
        const z1 = nrm(), z2 = nrm();
        X.push(50 + 10 * z1);
        Y.push(50 + 10 * (rho * z1 + Math.sqrt(1 - rho * rho) * z2));
    }
    // 완전무작위로 12개를 지운다(난수 순위가 작은 12개).
    const u = X.map(() => rand());
    const order = u.map((v, i) => [v, i]).sort((p, q) => p[0] - q[0]).map(p => p[1]);
    const miss = new Set(order.slice(0, 12));
    const obsIdx = [...Array(n).keys()].filter(i => !miss.has(i));
    const yObsMean = obsIdx.reduce((s, i) => s + Y[i], 0) / obsIdx.length;
    const mxo = obsIdx.reduce((s, i) => s + X[i], 0) / obsIdx.length;
    let sxy = 0, sxx = 0;
    for (const i of obsIdx) { sxy += (X[i] - mxo) * (Y[i] - yObsMean); sxx += (X[i] - mxo) ** 2; }
    const b1 = sxy / sxx, b0 = yObsMean - b1 * mxo;
    const Ymean = Y.map((y, i) => (miss.has(i) ? yObsMean : y));
    const Yreg = Y.map((y, i) => (miss.has(i) ? b0 + b1 * X[i] : y));
    const rFull = corr(X, Y), rMean = corr(X, Ymean), rReg = corr(X, Yreg);
    const rObs = corr(obsIdx.map(i => X[i]), obsIdx.map(i => Y[i]));
    const out = [BG];
    const draw = (px0, title, Yc, rC) => {
        out.push(panel(px0, 15, 430, 335, title, `채운 뒤 r = ${fx(rC)}`));
        const fr = frame({ xRange: [20, 80], yRange: [20, 80], box: { x: px0 + 50, y: 65, w: 340, h: 230 } });
        out.push(xaxis(fr, 20, 80, [20, 40, 60, 80], { y0: 20 }));
        out.push(yaxis(fr, 20, 20, 80, [20, 40, 60, 80]));
        out.push(txt(fr.X(80) + 14, fr.Y(20) + 4, 'x', { cls: 'ink2' }));
        out.push(txt(fr.X(20) - 8, fr.Y(80) - 14, 'y', { anchor: 'end', cls: 'ink2' }));
        for (const i of obsIdx) out.push(`<circle cx="${fr.X(X[i])}" cy="${fr.Y(Y[i])}" r="3.6" fill="var(--s1)" fill-opacity="0.8"/>`);
        for (const i of miss) out.push(`<circle cx="${fr.X(X[i])}" cy="${fr.Y(Yc[i])}" r="4.2" fill="var(--bgfix)" stroke="var(--s2)" stroke-width="2"/>`);
        out.push(txt(px0 + 215, 330, '채운 점: 관측된 28개 · 빈 점: 메운 12개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return fr;
    };
    const fA = draw(15, '평균 대체', Ymean, rMean);
    out.push(ln([[fA.X(22), fA.Y(yObsMean)], [fA.X(78), fA.Y(yObsMean)]], { stroke: COL.s2, sw: 1.2, dash: '4 3' }));
    const fB = draw(455, '회귀 대체', Yreg, rReg);
    out.push(ln([[fB.X(22), fB.Y(b0 + b1 * 22)], [fB.X(78), fB.Y(b0 + b1 * 78)]], { stroke: COL.s2, sw: 1.2, dash: '4 3' }));
    out.push(txt(W / 2, H - 8, `지우기 전 40개의 r = ${fx(rFull)}, 관측된 28개만의 r = ${fx(rObs)} — 지운 12개는 완전무작위로 골랐다`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-mis-meanimp',
        svg: svg({ width: W, height: H, title: '평균 대체와 회귀 대체', desc: '평균 대체한 점은 가로 한 줄에 서서 상관을 깎고, 회귀 대체한 점은 회귀선 위에 서서 상관을 부풀린다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-mis-delta — 델타를 키우면 추정값과 구간이 움직이고 결론이 바뀌는 문턱이 있다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 760, H = 350;
    const est = 4, se = 1.5, frac = 0.3, z = 1.96;
    const e = d => est + frac * d;
    const tip = -(est - z * se) / frac;
    const fr = frame({ xRange: [-8, 2], yRange: [-3, 9], box: { x: 70, y: 42, w: 500, h: 250 } });
    const out = [];
    for (const t of [-2, 0, 2, 4, 6, 8]) out.push(ln([[fr.X(-8), fr.Y(t)], [fr.X(2), fr.Y(t)]], { stroke: t === 0 ? 'var(--ink2)' : 'var(--grid)', sw: t === 0 ? 1.4 : 1 }));
    const up = [], dn = [];
    for (let i = 0; i <= 100; i += 1) {
        const d = -8 + (10 * i) / 100;
        up.push([fr.X(d), fr.Y(e(d) + z * se)]);
        dn.push([fr.X(d), fr.Y(e(d) - z * se)]);
    }
    out.push(`<path d="M${up.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} L${dn.reverse().map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s1)" fill-opacity="0.16" stroke="none"/>`);
    out.push(crv(fr, e, -8, 2, { col: 's1', sw: 2.6 }));
    out.push(ln([[fr.X(tip), fr.Y(-3)], [fr.X(tip), fr.Y(9)]], { stroke: COL.s2, sw: 2, dash: '6 4' }));
    out.push(ln([[fr.X(0), fr.Y(-3)], [fr.X(0), fr.Y(9)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '2 4' }));
    out.push(xaxis(fr, -8, 2, [-8, -6, -4, -2, 0, 2], { y0: -3 }));
    out.push(yaxis(fr, -8, -3, 9, [-2, 0, 2, 4, 6, 8], { fmt: v => fx(v, 0) }));
    out.push(txt(fr.X(2) + 14, fr.Y(-3) + 4, 'δ', { cls: 'ink2' }));
    out.push(txt(12, fr.Y(9) - 14, '처리 효과 추정값', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(tip) - 6, fr.Y(8.3), `문턱 δ = ${fx(tip)}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    out.push(txt(fr.X(0) + 6, fr.Y(8.3), 'δ = 0: MAR 분석', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(-7.8), fr.Y(-1.2), '이 쪽: 95% 구간이 0 을 품는다', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(-1.8), fr.Y(-1.2), '이 쪽: 구간이 0 위', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    out.push(txt(600, 70, '진한 선: 추정값', { cls: 'ink2', size: 'sm' }));
    out.push(txt(600, 90, '연한 띠: 95% 신뢰구간', { cls: 'ink2', size: 'sm' }));
    out.push(txt(600, 120, '처리군의 30% 가 빠졌고', { cls: 'ink2', size: 'sm' }));
    out.push(txt(600, 138, '메운 값에만 δ 를 더한다', { cls: 'ink2', size: 'sm' }));
    out.push(txt(600, 156, `추정값 = ${fx(est, 1)} + 0.3 δ`, { cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-mis-delta',
        svg: svg({ width: W, height: H, title: '델타 조정 민감도 분석', desc: 'δ 를 음수 쪽으로 키우면 처리 효과의 추정값과 구간이 내려가고 문턱을 넘으면 구간이 0 을 품는다', body: out.join('') }),
    };
})());

export default figures;
