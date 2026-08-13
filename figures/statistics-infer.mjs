/**
 * 통계학 10·11·12장(추정 / 가설검정 / 회귀와 상관)의 그림.
 *
 * 이름은 전부 `st-i-` 로 시작한다(담당 F 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~0` 표기를, 나머지는 유니코드(√ π σ μ θ α β χ ρ ∑ ≈ ≤ ≥ − ×)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰면 안 되고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 * 표본평균은 결합 매크론을 붙인 x̄ 로 적는다(8·9장 그림과 같은 표기).
 *
 * 난수를 쓰는 그림(신뢰구간 100개, 잔차 진단, 다중검정)은 씨앗을 고정한
 * 선형합동생성기를 쓴다. 빌드할 때마다 그림이 달라지면 안 되기 때문이다.
 * 그림 안에 적는 개수·비율·회귀계수는 모두 그 자리에서 계산해 넣는다.
 * 손으로 적어 두면 씨앗을 바꿀 때 그림과 숫자가 어긋난다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const PI = Math.PI;

/* ------------------------------------------------------------------ *
 * 공통 소도구 — 화소 좌표 도형
 * ------------------------------------------------------------------ */

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어
 * 선이 사라지고 화살촉만 남는다. 색을 직접 넣는 화살표를 따로 둔다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 2, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 양쪽 화살표. 폭이나 거리를 재는 표시에 쓴다. */
function arw2(x1, y1, x2, y2, { cls = 'ark', width = 1.6 } = {}) {
    const col = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' }[cls] ?? 'var(--ink2)';
    const mk = cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark';
    return `<path fill="none" stroke="${col}" stroke-width="${width}" marker-start="url(#${mk})" marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 채운 점. */
const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 사이트 다크 모드 배경이 #121212 다. 흰 속을 채워야 할 때 쓴다. */
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

/** 빈 점 — 셈에서 빠진 것을 나타낼 때 쓴다. */
const odot = (x, y, col = 'var(--s2)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="var(--bgfix)" stroke="${col}" stroke-width="1.6"/>`;

/** 패널 테두리 + 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 19, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 35, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 데이터 좌표 다각형 채우기. */
const poly = (g, pts, col, op) =>
    `<path d="M${pts.map(([x, y]) => `${g.X(x)} ${g.Y(y)}`).join(' L')} Z" fill="${col}" fill-opacity="${op}" stroke="none"/>`;

/** 데이터 좌표로 준 곡선 아래 영역을 채운다. */
function underArea(g, f, from, to, col, op, base = 0, steps = 90) {
    const pts = [[from, base]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([x, f(x)]);
    }
    pts.push([to, base]);
    return poly(g, pts, col, op);
}

/** 데이터 좌표 직사각형. */
const drect = (g, x1, y1, x2, y2, o) =>
    box(g.X(x1), g.Y(y2), g.X(x2) - g.X(x1), g.Y(y1) - g.Y(y2), o);

/**
 * 가로축만 그린다. 밀도 그림에서는 세로축이 뜻이 없고, 값 범위 안에 0 이
 * 들어 있으면 lib 의 axes() 가 그림 한가운데에 세로축을 그어 버린다.
 */
function xaxis(fr, [x0, x1], ticks = [], { dy = 15, fmt = neg, y0 = 0 } = {}) {
    const y = fr.Y(y0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { stroke: 'var(--ink2)', sw: 1 }));
        out.push(txt(fr.X(t), y + dy, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 눈금 글자에서 ASCII 하이픈을 유니코드 음수 기호로 바꾼다. 본문 표기와 맞추기 위한 것. */
const neg = v => String(v).replace('-', '\u2212');

/** 세로축을 데이터 0 이 아니라 왼쪽 가장자리에 세우는 축. */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 16, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return out.join('');
}

/** 이산 확률 막대. pts 는 [값, 확률] 배열. */
function bars(fr, pts, { col = 'var(--s1)', op = 0.3, step = 1, wfrac = 0.68, sw = 1, y0 = 0 } = {}) {
    const halfW = Math.max(0.8, ((fr.X(step) - fr.X(0)) * wfrac) / 2);
    return pts.map(([x, p]) => box(fr.X(x) - halfW, fr.Y(p), halfW * 2, fr.Y(y0) - fr.Y(p), {
        fill: col, op, stroke: col, sw, rx: 1.2,
    })).join('');
}

/**
 * 회색(ink2) 곡선. frame().curve 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * s1/s2/s3 클래스만 있어 'ark' 를 주면 선이 통째로 사라진다.
 */
function curveInk(fr, f, { from, to, dash, sw = 1.8, steps = 150 }) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    return ln(pts, { stroke: 'var(--ink2)', sw, dash });
}

/** 씨앗을 고정한 난수. 빌드 결과가 매번 같아야 한다. */
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}

/** 표준정규 난수 하나(박스-뮐러). */
function gauss(rand) {
    const u = Math.max(rand(), 1e-9), v = rand();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * PI * v);
}

/* ------------------------------------------------------------------ *
 * 공통 소도구 — 확률 계산
 * ------------------------------------------------------------------ */

const normPdf = (x, mu = 0, sd = 1) => Math.exp(-((x - mu) ** 2) / (2 * sd * sd)) / (sd * Math.sqrt(2 * PI));

/** 표준정규 누적분포. 유리함수 근사(Abramowitz-Stegun 26.2.17), 오차 7.5e-8. */
function normCdf(z) {
    const s = z < 0 ? -1 : 1;
    const x = Math.abs(z) / Math.SQRT2;
    const t = 1 / (1 + 0.3275911 * x);
    const y = 1 - t * Math.exp(-x * x) * (0.254829592
        + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
    return 0.5 * (1 + s * y);
}

const LANCZOS = [
    676.5203681218851, -1259.1392167224028, 771.32342877765313,
    -176.61502916214059, 12.507343278686905, -0.13857109526572012,
    9.9843695780195716e-6, 1.5056327351493116e-7,
];

/** 로그 감마함수(Lanczos 근사). */
function lgamma(z) {
    if (z < 0.5) return Math.log(PI / Math.sin(PI * z)) - lgamma(1 - z);
    const w = z - 1;
    let x = 0.99999999999980993;
    for (let i = 0; i < 8; i += 1) x += LANCZOS[i] / (w + i + 1);
    const t = w + 7.5;
    return 0.5 * Math.log(2 * PI) + (w + 0.5) * Math.log(t) - t + Math.log(x);
}
const lfact = n => lgamma(n + 1);
const lchoose = (n, k) => lfact(n) - lfact(k) - lfact(n - k);

/** 최소제곱 적합. 점 배열 [[x,y]] 에서 절편·기울기와 제곱합을 돌려준다. */
function lsq(pts) {
    const n = pts.length;
    const xb = pts.reduce((a, p) => a + p[0], 0) / n;
    const yb = pts.reduce((a, p) => a + p[1], 0) / n;
    let sxx = 0, sxy = 0, syy = 0;
    for (const [x, y] of pts) {
        sxx += (x - xb) ** 2; sxy += (x - xb) * (y - yb); syy += (y - yb) ** 2;
    }
    const b1 = sxy / sxx;
    const b0 = yb - b1 * xb;
    const sse = syy - b1 * sxy;
    return {
        n, xb, yb, sxx, sxy, syy, b1, b0, sse, ssr: b1 * sxy,
        r: sxy / Math.sqrt(sxx * syy), f: x => b0 + b1 * x,
    };
}

/* ================================================================== *
 * 10장 — 추정
 * ================================================================== */

/* ---- 10-1. 불편성 — 분포의 중심이 참값에 맞는가 ---- */
add((() => {
    const W = 720, H = 322;
    const th = 10, sdA = 1.15, sdB = 0.95, muB = 11.4;
    const g = [txt(360, 26, '추정량은 표본마다 값이 달라진다. 불편성은 그 분포의 중심을 보는 성질이다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [6.4, 14.6], yRange: [0, 0.46], box: { x: 62, y: 62, w: 596, h: 170 } });
    g.push(underArea(A, x => normPdf(x, th, sdA), 6.4, 14.6, 'var(--s1)', 0.16));
    g.push(A.curve(x => normPdf(x, th, sdA), { from: 6.4, to: 14.6, cls: 's1', steps: 200 }));
    g.push(underArea(A, x => normPdf(x, muB, sdB), 6.4, 14.6, 'var(--s2)', 0.14));
    g.push(A.curve(x => normPdf(x, muB, sdB), { from: 6.4, to: 14.6, cls: 's2', steps: 200 }));
    g.push(xaxis(A, [6.4, 14.6], [8, 10, 12, 14]));

    // 참값과 두 분포의 중심
    g.push(ln([[A.X(th), A.Y(0)], [A.X(th), A.Y(0.44)]], { stroke: 'var(--ink)', sw: 1.8, dash: '6 4' }));
    g.push(txt(A.X(th), A.Y(0.44) - 8, '참값 θ', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ln([[A.X(muB), A.Y(0)], [A.X(muB), A.Y(0.40)]], { stroke: 'var(--s2)', sw: 1.4, dash: '4 3' }));
    g.push(arw2(A.X(th) + 2, A.Y(0.40), A.X(muB) - 2, A.Y(0.40), { cls: 's2' }));
    g.push(txt((A.X(th) + A.X(muB)) / 2, A.Y(0.40) - 9, '편향 = E(θ̂) − θ', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(A.X(8.1), A.Y(0.345), '불편추정량', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(8.1), A.Y(0.345) + 15, '중심이 참값에 맞는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(13.4), A.Y(0.24), '편향추정량', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(13.4), A.Y(0.24) + 15, '중심이 밀려 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 258], [674, 258]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 282, '불편성은 ‘한 번 뽑아 맞힌다’ 는 뜻이 아니다. 왼쪽 분포도 참값을 빗맞히는 일이 대부분이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 304, '되풀이해 뽑은 추정값들의 평균이 참값이 된다는 뜻이며, 표본 하나에 대한 약속은 아무것도 없다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-unbiased',
        svg: svg({
            width: W, height: H,
            title: '불편성 — 추정량의 표본분포의 중심이 참값에 맞는가',
            desc: '두 추정량의 표본분포를 겹쳐 그린 것. 하나는 중심이 참값에 맞고 다른 하나는 오른쪽으로 밀려 있다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-2. 효율성 — 둘 다 불편이면 덜 흔들리는 쪽 ---- */
add((() => {
    const W = 720, H = 330;
    const th = 50, sdM = 2, sdD = 2 * Math.sqrt(PI / 2), eps = 3; // 중앙값의 표준오차는 약 1.253배
    const inM = 2 * normCdf(eps / sdM) - 1;
    const inD = 2 * normCdf(eps / sdD) - 1;
    const g = [txt(360, 26, '둘 다 불편이면 남는 기준은 하나다. 어느 쪽이 덜 흔들리는가',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [41, 59], yRange: [0, 0.23], box: { x: 62, y: 60, w: 596, h: 176 } });
    g.push(drect(A, th - eps, 0, th + eps, 0.23, { fill: 'var(--grid)', op: 0.45, stroke: 'none' }));
    g.push(A.curve(x => normPdf(x, th, sdD), { from: 41, to: 59, cls: 's2', steps: 200 }));
    g.push(A.curve(x => normPdf(x, th, sdM), { from: 41, to: 59, cls: 's1', steps: 200 }));
    g.push(xaxis(A, [41, 59], [44, 47, 50, 53, 56]));
    g.push(ln([[A.X(th), A.Y(0)], [A.X(th), A.Y(0.215)]], { stroke: 'var(--ink)', sw: 1.5, dash: '6 4' }));
    g.push(txt(A.X(th), A.Y(0.215) - 7, '참값 μ = 50', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(48.6), A.Y(0.198), '표본평균', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(56.9), A.Y(0.118), '표본중앙값', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(A.X(55.6), A.Y(0.112), A.X(53.7), A.Y(0.066), { cls: 's2', width: 1.4 }));

    g.push(txt(A.X(41.3), A.Y(0.205), `회색 띠(±3) 안에 들 확률`, { cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(41.3), A.Y(0.205) + 17, `표본평균 ${(inM * 100).toFixed(0)}%,  중앙값 ${(inD * 100).toFixed(0)}%`,
        { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[46, 262], [674, 262]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 286, '정규모집단에서 표본중앙값의 분산은 표본평균의 약 1.57배다. 그만큼 효율이 낮다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 310, '같은 정밀도를 얻으려면 중앙값 쪽이 표본을 1.57배 더 뽑아야 한다는 뜻이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-efficient',
        svg: svg({
            width: W, height: H,
            title: '효율성 — 불편추정량 가운데 분산이 작은 쪽',
            desc: '같은 참값을 중심으로 하는 두 불편추정량의 표본분포. 좁은 쪽이 정해진 구간 안에 들 확률이 높다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-3. 일치성 — 표본을 늘리면 참값으로 몰린다 ---- */
add((() => {
    const W = 740, H = 342;
    const th = 50, sd0 = 8, eps = 2;
    const ns = [4, 16, 100];
    const g = [txt(370, 26, '일치성은 한 표본의 성질이 아니라 표본을 늘려 갈 때의 성질이다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [40, 60], yRange: [0, 0.56], box: { x: 66, y: 58, w: 520, h: 178 } });
    g.push(drect(A, th - eps, 0, th + eps, 0.56, { fill: 'var(--grid)', op: 0.45, stroke: 'none' }));
    const cls = ['s3', 's2', 's1'];
    ns.forEach((n, i) => {
        const sd = sd0 / Math.sqrt(n);
        g.push(A.curve(x => normPdf(x, th, sd), { from: 40, to: 60, cls: cls[i], steps: 220 }));
    });
    g.push(xaxis(A, [40, 60], [44, 46, 48, 50, 52, 54, 56]));
    g.push(ln([[A.X(th), A.Y(0)], [A.X(th), A.Y(0.54)]], { stroke: 'var(--ink)', sw: 1.4, dash: '6 4' }));
    g.push(txt(A.X(th) + 8, A.Y(0.54) + 2, '참값 θ', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(53.4), A.Y(0.30), '회색 띠의 폭은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(53.4), A.Y(0.30) + 16, 'ε = 2 로 고정', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 띠 안에 들 확률
    g.push(panel(600, 58, 118, 178, '띠 안 확률'));
    ns.forEach((n, i) => {
        const sd = sd0 / Math.sqrt(n);
        const p = 2 * normCdf(eps / sd) - 1;
        const y = 96 + i * 46;
        g.push(box(614, y, 90, 3, { fill: `var(--${cls[i]})`, stroke: 'none', rx: 1.5 }));
        g.push(txt(614, y + 22, `n = ${n}`, { cls: 'ink', size: 'sm' }));
        g.push(txt(704, y + 22, `${(p * 100).toFixed(0)}%`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    });

    g.push(ln([[46, 262], [694, 262]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 286, '띠를 아무리 좁게 잡아도 n 을 키우면 그 안에 들 확률이 1 로 간다 — 이것이 일치성이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 310, '편향이 있어도 n 이 커지며 0 으로 사라지면 일치추정량일 수 있다. 두 성질은 서로 다른 것을 본다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-consistency',
        svg: svg({
            width: W, height: H,
            title: '일치성 — 표본을 늘리면 추정량이 참값으로 몰린다',
            desc: '표본 크기 4, 16, 100 에서 추정량의 표본분포. 고정된 폭의 띠 안에 들 확률이 31%, 58%, 95% 로 커진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-4. 평균제곱오차 = 분산 + 편향² ---- */
add((() => {
    const W = 740, H = 366;
    const rand = rng(20250813);
    const cases = [
        { t: '편향 작고 분산 작다', s: '가장 좋다', bx: 0, by: 0, sd: 7 },
        { t: '편향 작고 분산 크다', s: '평균은 맞지만 한 번의 값은 못 믿는다', bx: 0, by: 0, sd: 19 },
        { t: '편향 크고 분산 작다', s: '늘 같은 쪽으로 빗맞힌다', bx: 22, by: -14, sd: 7 },
        { t: '편향 크고 분산 크다', s: '가장 나쁘다', bx: 22, by: -14, sd: 19 },
    ];
    const g = [txt(370, 26, '과녁의 중심이 참값, 점 하나가 표본 하나에서 나온 추정값이다',
        { anchor: 'middle', cls: 'ink bold' })];
    cases.forEach((c, i) => {
        const cx = 118 + i * 172, cy = 158;
        g.push(txt(cx, 62, c.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        for (const r of [46, 31, 16]) g.push(circ(cx, cy, r, { stroke: 'var(--grid)', sw: 1 }));
        g.push(circ(cx, cy, 3.2, { fill: 'var(--ink2)', stroke: 'none' }));
        // 편향 중심. 흩어짐의 평균을 0 으로 맞춰야 ‘분포의 중심’ 이 그림에서 눈에 보인다.
        const mx = cx + c.bx, my = cy + c.by;
        const off = [];
        for (let k = 0; k < 14; k += 1) off.push([gauss(rand), gauss(rand)]);
        const ax = off.reduce((a, p) => a + p[0], 0) / off.length;
        const ay = off.reduce((a, p) => a + p[1], 0) / off.length;
        for (const [ox, oy] of off) {
            g.push(pdot(mx + (ox - ax) * c.sd, my + (oy - ay) * c.sd, 'var(--s1)', 3.2));
        }
        if (c.bx !== 0) g.push(arw(cx, cy, mx, my, { cls: 's2', width: 1.6 }));
        g.push(txt(cx, 228, c.s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[46, 254], [694, 254]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 282, '평균제곱오차 MSE(θ̂) = 분산 + 편향²  — 두 가지 나쁨을 하나의 수로 합친 것이다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 308, '가운데 두 칸을 견주는 것이 요점이다. 편향이 있어도 분산이 훨씬 작으면 MSE 가 더 작을 수 있다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 332, '그래서 ‘불편이면 무조건 좋다’ 가 아니다. 무엇을 줄이려는지 먼저 정해야 한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-bias-variance',
        svg: svg({
            width: W, height: H,
            title: '편향과 분산 — 과녁 네 개',
            desc: '편향과 분산의 크기를 조합한 네 가지 과녁. 평균제곱오차는 분산과 편향의 제곱을 더한 값이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-5. 왜 n − 1 로 나누는가 ---- */
add((() => {
    const W = 740, H = 352;
    const data = [2, 4, 5, 9, 10];
    const mu = 5, xb = 6;
    const sMu = data.reduce((a, x) => a + (x - mu) ** 2, 0);   // 51
    const sXb = data.reduce((a, x) => a + (x - xb) ** 2, 0);   // 46
    const g = [txt(370, 26, '같은 자료를 참값 μ 기준으로 재는 것과 표본평균 x̄ 기준으로 재는 것',
        { anchor: 'middle', cls: 'ink bold' })];

    // 왼쪽 — 자료와 두 기준선
    g.push(panel(36, 46, 330, 196, '편차를 어디서 재는가'));
    const A = frame({ xRange: [0.5, 11.5], yRange: [0, 1], box: { x: 62, y: 96, w: 282, h: 96 } });
    g.push(xaxis(A, [0.5, 11.5], [2, 4, 6, 8, 10]));
    g.push(ln([[A.X(mu), A.Y(0) - 6], [A.X(mu), A.Y(0.92)]], { stroke: 'var(--s2)', sw: 1.6, dash: '5 4' }));
    g.push(ln([[A.X(xb), A.Y(0) - 6], [A.X(xb), A.Y(0.92)]], { stroke: 'var(--s1)', sw: 1.6 }));
    g.push(txt(A.X(mu) - 5, A.Y(0.92) - 4, 'μ = 5', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(xb) + 5, A.Y(0.92) - 4, 'x̄ = 6', { cls: 'ink bold', size: 'sm' }));
    data.forEach((x, i) => {
        const y = 0.14 + i * 0.155;
        g.push(ln([[A.X(x), A.Y(y)], [A.X(xb), A.Y(y)]], { stroke: 'var(--s1)', sw: 2.4, cap: 'butt' }));
        g.push(ln([[A.X(x), A.Y(y) - 5], [A.X(mu), A.Y(y) - 5]], { stroke: 'var(--s2)', sw: 1.2, dash: '3 3', cap: 'butt' }));
        g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 3.4));
    });
    g.push(txt(201, 226, `μ 기준 제곱합 ${sMu}  >  x̄ 기준 제곱합 ${sXb}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽 — 제곱합을 기준점의 함수로
    g.push(panel(382, 46, 322, 196, '기준점을 옮기며 제곱합을 본다'));
    const S = c => data.reduce((a, x) => a + (x - c) ** 2, 0);
    const B = frame({ xRange: [3, 9], yRange: [40, 90], box: { x: 428, y: 78, w: 246, h: 112 } });
    g.push(axesEdge(B, { x0: 3, x1: 9, y0: 40, y1: 90, xTicks: [4, 5, 6, 7, 8, 9], yTicks: [50, 70, 90] }));
    g.push(B.curve(S, { from: 3.3, to: 8.7, cls: 's1', steps: 160 }));
    g.push(pdot(B.X(xb), B.Y(sXb), 'var(--s1)', 4.5));
    g.push(pdot(B.X(mu), B.Y(sMu), 'var(--s2)', 4.5));
    g.push(txt(B.X(mu), B.Y(sMu) - 10, 'μ', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(xb), B.Y(sXb) - 10, 'x̄', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(7.4), B.Y(43), 'x̄ 에서 최소', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(543, 226, `차이 ${sMu} − ${sXb} = ${sMu - sXb} = n(x̄ − μ)²`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[46, 258], [694, 258]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 282, 'x̄ 는 자료 쪽으로 끌려온 기준점이므로 제곱합이 언제나 참값 기준보다 작다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 306, '평균적으로 정확히 n·V(x̄) = σ² 만큼 작다. 그래서 n 이 아니라 n − 1 로 나눠야 제자리로 온다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 330, '자유도를 하나 잃었다는 말과 같은 이야기다. 편차 다섯 개의 합은 반드시 0 이라 넷만 자유롭다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-n-minus-1',
        svg: svg({
            width: W, height: H,
            title: '표본분산이 n − 1 로 나누는 이유',
            desc: '표본평균 기준의 제곱합은 참값 기준의 제곱합보다 언제나 작고, 그 차이가 정확히 n 곱하기 표본평균의 분산이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-6. 가능도함수 ---- */
add((() => {
    const W = 740, H = 362;
    const n = 20, k = 14;
    const L = p => (p <= 0 || p >= 1 ? 0 : Math.exp(lchoose(n, k) + k * Math.log(p) + (n - k) * Math.log(1 - p)));
    const lnL = p => (p <= 0 || p >= 1 ? -99 : lchoose(n, k) + k * Math.log(p) + (n - k) * Math.log(1 - p));
    const ph = k / n;
    const g = [txt(370, 24, '씨앗 20개를 심어 14개가 싹텄다. 이 자료를 고정해 놓고 모수 p 를 바꿔 본다',
        { anchor: 'middle', cls: 'ink bold' })];
    // 자료 띠
    for (let i = 0; i < n; i += 1) {
        const x = 214 + i * 16;
        if (i < k) g.push(pdot(x, 48, 'var(--s1)', 5));
        else g.push(odot(x, 48, 'var(--s2)', 5));
    }
    g.push(txt(206, 52, '자료', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(538, 52, '싹텄다 14 / 안 텄다 6', { cls: 'ink2', size: 'sm' }));

    g.push(panel(36, 70, 330, 206, '가능도 L(p)', { sub: '이 자료가 나올 확률을 p 의 함수로 본다' }));
    const A = frame({ xRange: [0, 1], yRange: [0, 0.22], box: { x: 70, y: 118, w: 274, h: 100 } });
    g.push(axesEdge(A, { x0: 0, x1: 1, y0: 0, y1: 0.22, xTicks: [0, 0.25, 0.5, 0.7, 1], yTicks: [0.1, 0.2] }));
    g.push(underArea(A, L, 0, 1, 'var(--s1)', 0.14));
    g.push(A.curve(L, { from: 0.002, to: 0.998, cls: 's1', steps: 220 }));
    g.push(ln([[A.X(ph), A.Y(0)], [A.X(ph), A.Y(L(ph))]], { stroke: 'var(--ink)', sw: 1.4, dash: '5 4' }));
    g.push(pdot(A.X(ph), A.Y(L(ph)), 'var(--ink)', 4));
    g.push(txt(A.X(ph) + 8, A.Y(L(ph)) - 6, 'p̂ = 0.7', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(201, 264, '넓이가 1 이 아니다 — 확률분포가 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(382, 70, 322, 206, '로그가능도 ln L(p)', { sub: '곱이 합으로 바뀌고 최대점은 그대로다' }));
    const B = frame({ xRange: [0, 1], yRange: [-18, 0], box: { x: 428, y: 118, w: 248, h: 100 } });
    g.push(axesEdge(B, { x0: 0, x1: 1, y0: -18, y1: 0, xTicks: [0, 0.25, 0.5, 0.7, 1], yTicks: [-15, -10, -5] }));
    g.push(B.curve(lnL, { from: 0.145, to: 0.9913, cls: 's3', steps: 260 }));
    g.push(ln([[B.X(ph), B.Y(-18)], [B.X(ph), B.Y(lnL(ph))]], { stroke: 'var(--ink)', sw: 1.4, dash: '5 4' }));
    g.push(pdot(B.X(ph), B.Y(lnL(ph)), 'var(--ink)', 4));
    g.push(ln([[B.X(0.44), B.Y(lnL(ph))], [B.X(0.86), B.Y(lnL(ph))]], { stroke: 'var(--grid)', sw: 1.4 }));
    g.push(txt(B.X(0.42), B.Y(lnL(ph)) + 5, '접선의 기울기 0', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(543, 264, '미분해서 0 이 되는 곳을 찾으면 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 292], [694, 292]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 316, '가능도는 모수의 함수다. 가로축이 자료가 아니라 모수라는 점이 확률분포와 다르다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 340, '최대가능도추정은 ‘내가 본 자료를 가장 그럴듯하게 만드는 모수’ 를 고르는 일이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-likelihood',
        svg: svg({
            width: W, height: H,
            title: '가능도함수와 로그가능도',
            desc: '자료를 고정하고 모수를 바꿔 가며 그 자료가 나올 확률을 그린 곡선. 최대점이 최대가능도추정값이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-7. 신뢰구간 100개 — 흔들리는 것은 구간이다 ---- */
add((() => {
    const W = 740, H = 420;
    const mu = 100, sd = 10, n = 25, z = 1.96;
    const se = sd / Math.sqrt(n);
    const rand = rng(4242021);
    const REP = 100;
    const ivs = [];
    for (let i = 0; i < REP; i += 1) {
        let s = 0;
        for (let k = 0; k < n; k += 1) s += mu + sd * gauss(rand);
        const xb = s / n;
        ivs.push({ xb, lo: xb - z * se, hi: xb + z * se });
    }
    const miss = ivs.filter(v => v.lo > mu || v.hi < mu).length;
    const g = [txt(370, 24, '같은 모집단에서 크기 25 표본을 100번 뽑아 95% 신뢰구간을 100개 만들었다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [0, 101], yRange: [90, 110], box: { x: 62, y: 48, w: 606, h: 250 } });
    // 참값 가로선
    g.push(ln([[A.X(0), A.Y(mu)], [A.X(101) + 8, A.Y(mu)]], { stroke: 'var(--ink)', sw: 1.6 }));
    g.push(txt(A.X(101) + 12, A.Y(mu) + 4, 'μ', { cls: 'ink bold' }));
    for (const t of [92, 96, 104, 108]) {
        g.push(ln([[A.X(0), A.Y(t)], [A.X(101), A.Y(t)]], { stroke: 'var(--grid)', sw: 1, dash: '3 4' }));
        g.push(txt(A.X(0) - 8, A.Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    ivs.forEach((v, i) => {
        const bad = v.lo > mu || v.hi < mu;
        const col = bad ? 'var(--s2)' : 'var(--s1)';
        const x = A.X(i + 1);
        g.push(ln([[x, A.Y(v.lo)], [x, A.Y(v.hi)]], { stroke: col, sw: bad ? 2.6 : 1.6, cap: 'butt' }));
        g.push(pdot(x, A.Y(v.xb), col, bad ? 2.8 : 1.9));
        if (bad) g.push(txt(x, A.Y(v.hi) < A.Y(mu) ? A.Y(v.hi) - 7 : A.Y(v.lo) + 15, '✕',
            { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(370, 320, '세로 막대 하나가 구간 하나다. 가로선 μ 는 처음부터 끝까지 움직이지 않는다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 344, `참값을 놓친 구간(✕ 표시) ${miss}개 — 95% 이니 평균 5개이고 이 개수 자체도 우연히 달라진다`,
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[46, 360], [694, 360]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 384, '‘참값이 이 구간 안에 있을 확률 95%’ 는 틀린 읽기다. μ 는 상수여서 확률을 붙일 대상이 아니다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 406, '95% 는 구간 하나의 성질이 아니라 구간을 만드는 절차의 성질이다. 구간 하나는 품었거나 놓쳤을 뿐이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-ci-100',
        svg: svg({
            width: W, height: H,
            title: '신뢰구간 100개 — 흔들리는 것은 구간이고 참값은 고정되어 있다',
            desc: '같은 모집단에서 100번 표본을 뽑아 만든 95% 신뢰구간. 참값을 놓친 구간이 몇 개인지 세어 보는 그림',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 10-8. 구간의 폭을 정하는 두 가지 ---- */
add((() => {
    const W = 740, H = 348;
    const xb = 100, se = 2, sd = 10;
    const levels = [['80%', 1.282], ['90%', 1.645], ['95%', 1.960], ['99%', 2.576]];
    const g = [txt(370, 24, '구간이 넓어지는 두 가지 이유 — 더 확실해지려 할 때, 그리고 표본이 적을 때',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 44, 340, 222, '신뢰수준을 올리면', { sub: '같은 자료, 같은 x̄ = 100' }));
    const A = frame({ xRange: [92, 108], yRange: [0, 5], box: { x: 74, y: 90, w: 274, h: 122 } });
    g.push(ln([[A.X(xb), A.Y(0.2)], [A.X(xb), A.Y(4.8)]], { stroke: 'var(--ink)', sw: 1.4, dash: '5 4' }));
    levels.forEach(([nm, zz], i) => {
        const y = 4.2 - i * 1.05;
        g.push(ln([[A.X(xb - zz * se), A.Y(y)], [A.X(xb + zz * se), A.Y(y)]],
            { stroke: 'var(--s1)', sw: 3, cap: 'butt' }));
        for (const s of [-1, 1]) {
            g.push(ln([[A.X(xb + s * zz * se), A.Y(y - 0.22)], [A.X(xb + s * zz * se), A.Y(y + 0.22)]],
                { stroke: 'var(--s1)', sw: 2.4 }));
        }
        g.push(txt(A.X(92.4), A.Y(y) + 4, nm, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(A.X(107.6), A.Y(y) + 4, `±${r2(zz * se)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(xaxis(A, [92, 108], [95, 100, 105], { y0: 0.2 }));
    g.push(txt(206, 252, '100% 신뢰구간은 (−∞, ∞) 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(392, 44, 312, 222, '표본을 늘리면', { sub: '95% 구간의 폭, σ = 10' }));
    const B = frame({ xRange: [0, 430], yRange: [0, 13], box: { x: 442, y: 86, w: 232, h: 124 } });
    g.push(axesEdge(B, {
        x0: 0, x1: 430, y0: 0, y1: 13, xTicks: [25, 100, 200, 300, 400], yTicks: [4, 8, 12],
    }));
    g.push(B.curve(nn => 2 * 1.96 * sd / Math.sqrt(nn), { from: 12, to: 425, cls: 's2', steps: 220 }));
    for (const nn of [25, 100, 400]) {
        const w = 2 * 1.96 * sd / Math.sqrt(nn);
        g.push(pdot(B.X(nn), B.Y(w), 'var(--s2)', 4));
        g.push(txt(B.X(nn) + 9, B.Y(w) - 6, `${r2(w)}`, { cls: 'ink', size: 'sm' }));
    }
    g.push(txt(B.X(210), B.Y(10.4), '폭 ∝ 1/√n', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(548, 252, 'n 을 네 배로 해야 폭이 절반이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 284], [694, 284]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 308, '신뢰수준과 폭은 맞바꿈 관계다. 확실함을 사는 값은 넓은 구간, 곧 덜 쓸모 있는 답이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 332, '둘을 동시에 좋게 만드는 길은 하나뿐이다. 표본을 늘리는 것',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-ci-width',
        svg: svg({
            width: W, height: H,
            title: '신뢰구간의 폭을 정하는 것 — 신뢰수준과 표본 크기',
            desc: '신뢰수준을 올리면 구간이 넓어지고 표본을 늘리면 좁아진다. 폭은 표본 크기의 제곱근에 반비례한다',
            body: BG + g.join(''),
        }),
    };
})());


/* ================================================================== *
 * 11장 — 가설검정
 * ================================================================== */

/* ---- 11-1. 1종 오류와 2종 오류의 맞바꿈 ---- */
add((() => {
    const W = 740, H = 470;
    const m0 = 100, m1 = 105;
    const rows = [
        { se: 2, c: 104, t: 'n = 16 (표준오차 2), 기각역 x̄ ≥ 104' },
        { se: 2, c: 103, t: 'n = 16 (표준오차 2), 기각역 x̄ ≥ 103 — 기각역을 넓혔다' },
        { se: 1, c: 102, t: 'n = 64 (표준오차 1), 기각역 x̄ ≥ 102 — 표본을 네 배로 늘렸다' },
    ];
    const g = [txt(370, 24, '두 분포가 겹쳐 있다. 어디를 잘라도 두 오류 가운데 하나는 남는다',
        { anchor: 'middle', cls: 'ink bold' }),
    txt(370, 46, '왼쪽 봉우리는 귀무가설이 참일 때, 오른쪽 봉우리는 참값이 실제로 105 일 때의 x̄ 분포다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' })];
    rows.forEach((rw, i) => {
        const top = 66 + i * 122;
        const ymax = 1.02 / (rw.se * Math.sqrt(2 * PI));
        const A = frame({ xRange: [93, 112], yRange: [0, ymax], box: { x: 96, y: top + 20, w: 452, h: 74 } });
        g.push(txt(96, top + 14, rw.t, { cls: 'ink bold', size: 'sm' }));
        const f0 = x => normPdf(x, m0, rw.se);
        const f1 = x => normPdf(x, m1, rw.se);
        g.push(underArea(A, f0, rw.c, 112, 'var(--s1)', 0.55));
        g.push(underArea(A, f1, 93, rw.c, 'var(--s2)', 0.35));
        g.push(A.curve(f0, { from: 93, to: 112, cls: 's1', steps: 200 }));
        g.push(A.curve(f1, { from: 93, to: 112, cls: 's2', steps: 200 }));
        g.push(xaxis(A, [93, 112], [96, 100, 104, 108, 112]));
        g.push(ln([[A.X(rw.c), A.Y(0)], [A.X(rw.c), A.Y(ymax)]], { stroke: 'var(--ink)', sw: 1.6, dash: '5 4' }));
        const al = 1 - normCdf((rw.c - m0) / rw.se);
        const be = normCdf((rw.c - m1) / rw.se);
        g.push(txt(556, top + 42, `α = ${al.toFixed(3)}`, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(556, top + 60, `β = ${be.toFixed(3)}`, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(556, top + 78, `검정력 ${(1 - be).toFixed(3)}`, { cls: 'ink2', size: 'sm' }));
        g.push(box(548, top + 26, 128, 62, { stroke: 'var(--grid)', sw: 1, rx: 4 }));
        if (i === 0) {
            g.push(txt(A.X(105.6), A.Y(ymax * 0.72), 'β', { cls: 'ink bold' }));
            g.push(arw(A.X(105.4), A.Y(ymax * 0.72), A.X(103.4), A.Y(ymax * 0.22), { cls: 's2', width: 1.4 }));
            g.push(txt(A.X(107.4), A.Y(ymax * 0.34), 'α', { cls: 'ink bold' }));
            g.push(arw(A.X(107.2), A.Y(ymax * 0.32), A.X(105.0), A.Y(ymax * 0.06), { cls: 's1', width: 1.4 }));
        }
    });
    g.push(ln([[46, 434], [694, 434]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 456, '위 두 줄은 맞바꿈이다. α 를 3분의 1 로 줄이면 β 가 두 배가 된다. 셋째 줄만이 둘을 함께 줄인다',
        { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'st-i-two-errors',
        svg: svg({
            width: W, height: H,
            title: '1종 오류와 2종 오류의 맞바꿈',
            desc: '귀무가설과 대립가설의 두 분포를 겹쳐 놓고 기각역의 경계를 옮기면 두 오류 확률이 반대 방향으로 움직인다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-2. p 값의 정의 ---- */
add((() => {
    const W = 740, H = 344;
    const zo = 2.0;
    const one = 1 - normCdf(zo), two = 2 * one;
    const g = [txt(370, 24, 'p 값은 넓이다. 어느 넓이인지가 정의의 전부다', { anchor: 'middle', cls: 'ink bold' })];
    const mk = (bx, title, sub, both, val) => {
        const out = [panel(bx, 44, 330, 202, title, { sub })];
        const A = frame({ xRange: [-3.6, 3.6], yRange: [0, 0.44], box: { x: bx + 30, y: 90, w: 270, h: 112 } });
        out.push(underArea(A, x => normPdf(x), zo, 3.6, 'var(--s2)', 0.6));
        if (both) out.push(underArea(A, x => normPdf(x), -3.6, -zo, 'var(--s2)', 0.6));
        out.push(A.curve(x => normPdf(x), { from: -3.6, to: 3.6, cls: 's1', steps: 200 }));
        out.push(xaxis(A, [-3.6, 3.6], [-2, 0, 2]));
        out.push(ln([[A.X(zo), A.Y(0)], [A.X(zo), A.Y(0.30)]], { stroke: 'var(--ink)', sw: 1.5 }));
        out.push(txt(A.X(zo), A.Y(0.30) - 7, '관측값 2.0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        if (both) {
            out.push(ln([[A.X(-zo), A.Y(0)], [A.X(-zo), A.Y(0.22)]], { stroke: 'var(--ink)', sw: 1.5, dash: '4 3' }));
            out.push(txt(A.X(-zo), A.Y(0.22) - 7, '−2.0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        out.push(txt(bx + 165, 236, `p = ${val.toFixed(3)}`, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };
    g.push(mk(36, '한쪽 꼬리 검정', '대립가설이 ‘크다’ 쪽만 볼 때', false, one));
    g.push(mk(374, '양쪽 꼬리 검정', '대립가설이 ‘다르다’ 일 때', true, two));
    g.push(ln([[46, 262], [694, 262]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 284, 'p = 귀무가설이 참일 때 ‘관측된 것과 같거나 그보다 더 극단적인’ 자료가 나올 확률',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 306, '관측값 하나의 확률이 아니라 그 바깥 전체의 넓이다. 그래서 ‘같거나 더’ 라는 말이 정의에 들어간다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 328, '가설이 참일 확률도 아니다. 조건이 ‘귀무가설이 참일 때’ 이므로 방향이 반대다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-pvalue-def',
        svg: svg({
            width: W, height: H,
            title: 'p 값의 정의 — 관측값보다 극단적인 쪽의 넓이',
            desc: '한쪽 꼬리와 양쪽 꼬리 검정에서 p 값이 각각 어느 넓이를 뜻하는지 표준정규곡선 위에 칠해 보인 것',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-3. p < 0.05 인 결과 가운데 위양성은 몇인가 ---- */
add((() => {
    const W = 740, H = 402;
    const M = 1000, real = 100, alpha = 0.05, power = 0.8;
    const tp = Math.round(real * power);            // 80
    const fn = real - tp;                            // 20
    const fp = Math.round((M - real) * alpha);       // 45
    const tn = M - real - fp;                        // 855
    const sig = tp + fp;
    const fdr = fp / sig;
    const g = [txt(370, 24, '가설 1000개를 검정한다. 그중 100개만 실제로 효과가 있다고 하자',
        { anchor: 'middle', cls: 'ink bold' }),
    txt(370, 44, '유의수준 0.05, 검정력 0.8 — 점 하나가 가설 하나다', { anchor: 'middle', cls: 'ink2', size: 'sm' })];
    const cols = 50, sp = 12.4, spy = 11.4, x0 = 62, y0 = 66;
    for (let i = 0; i < M; i += 1) {
        const cx = x0 + (i % cols) * sp, cy = y0 + Math.floor(i / cols) * spy;
        if (i < tp) g.push(pdot(cx, cy, 'var(--s1)', 4.2));
        else if (i < tp + fn) g.push(odot(cx, cy, 'var(--s1)', 4));
        else if (i < tp + fn + fp) g.push(pdot(cx, cy, 'var(--s2)', 4.2));
        else g.push(pdot(cx, cy, 'var(--grid)', 3));
    }
    const item = (x, y, kind, label) => {
        const mark = kind === 'tp' ? pdot(x, y - 4, 'var(--s1)', 4.2)
            : kind === 'fn' ? odot(x, y - 4, 'var(--s1)', 4)
                : kind === 'fp' ? pdot(x, y - 4, 'var(--s2)', 4.2)
                    : pdot(x, y - 4, 'var(--grid)', 3);
        return mark + txt(x + 11, y, label, { cls: 'ink2', size: 'sm' });
    };
    g.push(item(112, 304, 'tp', `효과 있고 유의하다 ${tp}`));
    g.push(item(396, 304, 'fn', `효과 있는데 놓쳤다 ${fn}`));
    g.push(item(112, 326, 'fp', `효과 없는데 유의하다 ${fp}`));
    g.push(item(396, 326, 'tn', `효과 없고 유의하지 않다 ${tn}`));
    g.push(ln([[46, 344], [694, 344]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 366, `‘유의하다’ 고 보고되는 것은 ${tp} + ${fp} = ${sig}개이고, 그중 ${fp}개는 귀무가설이 참이다`,
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 390, `곧 ${(fdr * 100).toFixed(0)}% 가 위양성이다. 유의수준 5% 와는 전혀 다른 수이며 사전확률이 낮을수록 커진다`,
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-i-pvalue-base-rate',
        svg: svg({
            width: W, height: H,
            title: 'p 값과 ‘귀무가설이 참일 확률’ 은 다른 수다',
            desc: '가설 1000개를 검정할 때 유의하다고 보고되는 125개 가운데 45개는 귀무가설이 참인 위양성이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-4. 검정력 곡선 ---- */
add((() => {
    const W = 740, H = 356;
    const m0 = 100, sd = 8, zc = 1.645;
    const ns = [4, 16, 64];
    const cls = ['s3', 's2', 's1'];
    const pw = (mu, n) => normCdf((mu - m0) / (sd / Math.sqrt(n)) - zc);
    const g = [txt(370, 24, '참값이 귀무가설에서 멀어질수록 잡을 확률이 올라간다. 그 곡선이 검정력이다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [100, 110], yRange: [0, 1.06], box: { x: 78, y: 48, w: 470, h: 178 } });
    g.push(axesEdge(A, {
        x0: 100, x1: 110, y0: 0, y1: 1.06,
        xTicks: [100, 102, 104, 106, 108, 110], yTicks: [0.2, 0.4, 0.6, 0.8, 1],
        fmtY: v => v.toFixed(1),
    }));
    g.push(ln([[A.X(100), A.Y(0.8)], [A.X(110), A.Y(0.8)]], { stroke: 'var(--grid)', sw: 1.2, dash: '5 4' }));
    g.push(txt(A.X(110) + 8, A.Y(0.8) + 4, '0.8', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[A.X(100), A.Y(0.05)], [A.X(110), A.Y(0.05)]], { stroke: 'var(--grid)', sw: 1.2, dash: '5 4' }));
    g.push(txt(A.X(110) + 8, A.Y(0.05) + 4, 'α', { cls: 'ink2', size: 'sm' }));
    ns.forEach((n, i) => g.push(A.curve(mu => pw(mu, n), { from: 100, to: 110, cls: cls[i], steps: 200 })));
    g.push(legend(586, 76, [{ slot: 1, name: 'n = 64' }, { slot: 2, name: 'n = 16' }, { slot: 3, name: 'n = 4' }]));
    // 표본이 작으면 실제 차이가 있어도 못 잡는다
    const mk = 103;
    g.push(pdot(A.X(mk), A.Y(pw(mk, 16)), 'var(--s2)', 4.5));
    g.push(ln([[A.X(mk), A.Y(0)], [A.X(mk), A.Y(pw(mk, 16))]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(txt(586, 142, '참값이 103 이라면', { cls: 'ink', size: 'sm' }));
    g.push(txt(586, 160, `n = 16 의 검정력은 ${pw(mk, 16).toFixed(2)}`, { cls: 'ink bold', size: 'sm' }));
    g.push(txt(586, 178, `n = 64 라면 ${pw(mk, 64).toFixed(2)}`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(586, 200, '차이는 그대로인데', { cls: 'ink2', size: 'sm' }));
    g.push(txt(586, 216, '잡을 확률만 달라진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(313, 256, '가로축은 실제 모평균 μ (귀무가설은 μ = 100)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[46, 274], [694, 274]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 296, '검정력을 정하는 것은 셋이다 — 유의수준 α, 참 효과의 크기, 표본 크기 n',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 318, '왼쪽 끝에서 세 곡선이 모두 α 를 지난다. 참값이 귀무가설과 같으면 기각 확률이 곧 α 이기 때문이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 340, '검정력이 낮은 검정에서 ‘유의하지 않다’ 는 결과는 차이가 없다는 증거가 되지 못한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-power-curve',
        svg: svg({
            width: W, height: H,
            title: '검정력 곡선 — 표본 크기와 효과 크기가 정한다',
            desc: '표본 크기 4, 16, 64 에서 참 모평균에 따른 검정력. 같은 차이라도 표본이 작으면 잡을 확률이 낮다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-5. 유의하지 않다는 것이 차이가 없다는 뜻은 아니다 ---- */
add((() => {
    const W = 740, H = 334;
    const rows = [
        { est: 0.2, lo: -0.4, hi: 0.8, nm: '가', v: '유의하지 않다 — 그리고 좁다', c: 's1' },
        { est: 3.0, lo: -1.0, hi: 7.0, nm: '나', v: '유의하지 않다 — 그러나 넓다', c: 's2' },
        { est: 3.0, lo: 0.5, hi: 5.5, nm: '다', v: '유의하다', c: 's3' },
    ];
    const g = [txt(370, 24, '세 연구 모두 두 집단의 차이를 추정했다. 가와 나는 둘 다 유의하지 않다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [-2.2, 8.2], yRange: [0, 4], box: { x: 96, y: 56, w: 420, h: 152 } });
    g.push(drect(A, -1, 0.2, 1, 3.8, { fill: 'var(--grid)', op: 0.4, stroke: 'none' }));
    g.push(txt(A.X(0), A.Y(3.9) - 2, '실용적으로 무시할 수 있는 범위', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[A.X(0), A.Y(0.15)], [A.X(0), A.Y(3.72)]], { stroke: 'var(--ink)', sw: 1.6, dash: '5 4' }));
    rows.forEach((rw, i) => {
        const y = 3 - i;
        const col = `var(--${rw.c})`;
        g.push(ln([[A.X(rw.lo), A.Y(y)], [A.X(rw.hi), A.Y(y)]], { stroke: col, sw: 3.4, cap: 'butt' }));
        for (const v of [rw.lo, rw.hi]) {
            g.push(ln([[A.X(v), A.Y(y - 0.16)], [A.X(v), A.Y(y + 0.16)]], { stroke: col, sw: 2.6 }));
        }
        g.push(pdot(A.X(rw.est), A.Y(y), col, 5));
        g.push(txt(88, A.Y(y) + 5, `연구 ${rw.nm}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(534, A.Y(y) + 5, rw.v, { cls: 'ink', size: 'sm' }));
    });
    g.push(xaxis(A, [-2.2, 8.2], [-2, 0, 2, 4, 6, 8], { y0: 0.15 }));
    g.push(txt(A.X(3), A.Y(0.15) + 32, '가로축은 두 집단의 평균 차이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[46, 252], [694, 252]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 274, '연구 가는 ‘차이가 있어도 작다’ 고 말할 근거가 있다. 연구 나는 아무것도 말하지 못한다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 296, '나의 구간은 7 만큼의 큰 차이도 배제하지 않는다. 표본이 모자라 판단을 못 한 것이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 320, '그래서 검정 결과만 보고하면 안 된다. 추정값과 구간을 함께 보아야 둘을 가를 수 있다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-notsig',
        svg: svg({
            width: W, height: H,
            title: '유의하지 않은 두 가지 경우 — 좁은 구간과 넓은 구간',
            desc: '둘 다 0 을 품어 유의하지 않지만 좁은 구간은 차이가 작다는 정보이고 넓은 구간은 정보가 없다는 뜻이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-6. 신뢰구간과 검정의 대응 ---- */
add((() => {
    const W = 740, H = 356;
    const xb = 100, se = 2, z = 1.96;
    const lo = xb - z * se, hi = xb + z * se;
    const cands = [98, 103, 105];
    const g = [txt(370, 24, '같은 자료로 신뢰구간을 만드는 일과 여러 μ~0 을 검정하는 일은 같은 계산이다',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 44, 668, 116, '95% 신뢰구간과 세 가지 귀무가설'));
    const A = frame({ xRange: [94, 108], yRange: [0, 1], box: { x: 90, y: 92, w: 566, h: 40 } });
    g.push(ln([[A.X(lo), A.Y(0.55)], [A.X(hi), A.Y(0.55)]], { stroke: 'var(--s1)', sw: 4, cap: 'butt' }));
    for (const v of [lo, hi]) {
        g.push(ln([[A.X(v), A.Y(0.32)], [A.X(v), A.Y(0.78)]], { stroke: 'var(--s1)', sw: 2.6 }));
        g.push(txt(A.X(v), A.Y(0.78) - 8, v.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(pdot(A.X(xb), A.Y(0.55), 'var(--s1)', 5));
    g.push(txt(A.X(xb), A.Y(0.55) - 12, 'x̄ = 100', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    cands.forEach(mu => {
        const inside = mu > lo && mu < hi;
        const col = inside ? 'var(--s3)' : 'var(--s2)';
        g.push(ln([[A.X(mu), A.Y(0.05)], [A.X(mu), A.Y(0.34)]], { stroke: col, sw: 2 }));
        g.push(txt(A.X(mu), A.Y(0.05) + 16, `${mu}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(84, A.Y(0.55) + 5, '구간', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(panel(36, 172, 668, 130, '같은 세 가설을 검정통계량으로 본다', { sub: 'z = (x̄ − μ~0)/표준오차, 기각역은 |z| ≥ 1.96' }));
    const B = frame({ xRange: [-4, 4], yRange: [0, 0.44], box: { x: 130, y: 216, w: 480, h: 68 } });
    g.push(underArea(B, x => normPdf(x), 1.96, 4, 'var(--s2)', 0.5));
    g.push(underArea(B, x => normPdf(x), -4, -1.96, 'var(--s2)', 0.5));
    g.push(B.curve(x => normPdf(x), { from: -4, to: 4, cls: 's1', steps: 200 }));
    g.push(xaxis(B, [-4, 4], [-3, -1.96, 0, 1.96, 3]));
    cands.forEach(mu => {
        const zz = (xb - mu) / se;
        const col = Math.abs(zz) < 1.96 ? 'var(--s3)' : 'var(--s2)';
        g.push(pdot(B.X(zz), B.Y(0), col, 5));
        g.push(txt(B.X(zz), B.Y(0) - 10, `${mu}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(ln([[46, 314], [694, 314]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 336, '신뢰구간은 ‘유의수준 5% 로 기각되지 않는 μ~0 을 모두 모은 것’ 이다. 두 절차가 같은 것을 다르게 적었을 뿐이다',
        { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'st-i-ci-test',
        svg: svg({
            width: W, height: H,
            title: '신뢰구간과 양쪽 꼬리 검정의 대응',
            desc: '신뢰구간 안에 있는 귀무가설 값은 기각되지 않고 밖에 있는 값은 기각된다. 두 절차는 같은 계산이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-7. 다중검정 ---- */
add((() => {
    const W = 740, H = 352;
    const rand = rng(90210);
    const m = 20;
    const ps = [];
    for (let i = 0; i < m; i += 1) ps.push(rand());
    const hit = ps.filter(p => p < 0.05).length;
    const g = [txt(370, 24, '귀무가설이 모두 참인데도 여러 번 검정하면 하나쯤은 유의해진다',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 44, 340, 214, '검정 20번', { sub: '전부 귀무가설이 참인 상황에서 얻은 p 값' }));
    const A = frame({ xRange: [0.4, 20.6], yRange: [0, 1], box: { x: 78, y: 92, w: 274, h: 116 } });
    g.push(drect(A, 0.4, 0, 20.6, 0.05, { fill: 'var(--s2)', op: 0.28, stroke: 'none' }));
    g.push(axesEdge(A, { x0: 0.4, x1: 20.6, y0: 0, y1: 1, xTicks: [1, 5, 10, 15, 20], yTicks: [0.05, 0.5, 1] }));
    ps.forEach((p, i) => {
        const bad = p < 0.05;
        g.push(ln([[A.X(i + 1), A.Y(0)], [A.X(i + 1), A.Y(p)]],
            { stroke: bad ? 'var(--s2)' : 'var(--grid)', sw: 1.6, cap: 'butt' }));
        g.push(pdot(A.X(i + 1), A.Y(p), bad ? 'var(--s2)' : 'var(--s1)', bad ? 4.4 : 3));
    });
    g.push(txt(206, 246, `0.05 밑으로 내려간 것 ${hit}개`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(panel(392, 44, 312, 214, '적어도 하나가 유의할 확률', { sub: '독립인 검정 m 번, 1 − 0.95\u1d50' }));
    const B = frame({ xRange: [0, 31], yRange: [0, 1.05], box: { x: 442, y: 92, w: 232, h: 116 } });
    g.push(axesEdge(B, {
        x0: 0, x1: 31, y0: 0, y1: 1.05, xTicks: [1, 10, 20, 30], yTicks: [0.25, 0.5, 0.75, 1],
        fmtY: v => v.toFixed(2),
    }));
    g.push(B.curve(mm => 1 - 0.95 ** mm, { from: 0, to: 31, cls: 's2', steps: 200 }));
    for (const mm of [1, 10, 20]) {
        const v = 1 - 0.95 ** mm;
        g.push(pdot(B.X(mm), B.Y(v), 'var(--s2)', 4));
        g.push(txt(B.X(mm) + 8, B.Y(v) - 6, v.toFixed(2), { cls: 'ink', size: 'sm' }));
    }
    g.push(txt(548, 246, '20번이면 64% — 반보다 잦다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[46, 272], [694, 272]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 294, '검정을 여러 번 하면 유의수준 5% 는 검정 하나의 값일 뿐이고 전체로는 훨씬 헐렁해진다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 316, '본페로니 보정은 각 검정의 기준을 α/m 로 낮춘다. m = 20 이면 0.0025 다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 338, '자료를 본 뒤 가설을 고르면 셈에 들어가지 않은 검정이 숨는다. 그것이 가장 흔한 형태다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-multiple',
        svg: svg({
            width: W, height: H,
            title: '다중검정 — 여러 번 하면 우연히 유의해진다',
            desc: '귀무가설이 모두 참인 20번의 검정에서 나온 p 값과, 검정 횟수에 따라 적어도 하나가 유의할 확률',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 11-8. 적합도 검정과 독립성 검정 ---- */
add((() => {
    const W = 740, H = 392;
    const obs = [8, 9, 14, 7, 13, 9];
    const exp = obs.reduce((a, b) => a + b, 0) / obs.length;
    const chi1 = obs.reduce((a, o) => a + ((o - exp) ** 2) / exp, 0);
    const O = [[50, 50], [30, 70]];
    const rowS = O.map(r => r[0] + r[1]);
    const colS = [O[0][0] + O[1][0], O[0][1] + O[1][1]];
    const tot = rowS[0] + rowS[1];
    const E = O.map((r, i) => r.map((_, j) => (rowS[i] * colS[j]) / tot));
    let chi2 = 0;
    for (let i = 0; i < 2; i += 1) for (let j = 0; j < 2; j += 1) chi2 += ((O[i][j] - E[i][j]) ** 2) / E[i][j];
    const g = [txt(370, 24, '두 검정 모두 같은 식이다 — 관측도수와 기대도수의 차를 기대도수로 재서 더한다',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 46, 340, 254, '적합도 검정', { sub: '주사위 60번, 눈마다 기대 10회' }));
    const A = frame({ xRange: [0.4, 6.6], yRange: [0, 16], box: { x: 74, y: 96, w: 278, h: 128 } });
    g.push(axesEdge(A, { x0: 0.4, x1: 6.6, y0: 0, y1: 16, xTicks: [1, 2, 3, 4, 5, 6], yTicks: [5, 10, 15] }));
    g.push(bars(A, obs.map((o, i) => [i + 1, o]), { col: 'var(--s1)', op: 0.34, wfrac: 0.56 }));
    g.push(ln([[A.X(0.4), A.Y(exp)], [A.X(6.6), A.Y(exp)]], { stroke: 'var(--s2)', sw: 1.8, dash: '5 4' }));
    g.push(txt(A.X(6.6) + 6, A.Y(exp) - 8, '기대 10', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(206, 256, `χ² = ${chi1.toFixed(2)},  자유도 ${obs.length - 1}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(206, 280, '임곗값 11.07 보다 작다 — 기각하지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(392, 46, 312, 254, '독립성 검정', { sub: '두 집단 × 찬반, 위가 관측 아래가 기대' }));
    const cx0 = 470, cy0 = 106, cw = 74, ch = 40;
    const heads = ['찬성', '반대', '합'];
    heads.forEach((h, j) => g.push(txt(cx0 + j * cw + cw / 2, cy0 - 6, h, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    ['집단 A', '집단 B'].forEach((nm, i) => {
        g.push(txt(cx0 - 8, cy0 + i * ch + 26, nm, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        for (let j = 0; j < 2; j += 1) {
            g.push(box(cx0 + j * cw, cy0 + i * ch, cw, ch, { stroke: 'var(--grid)', sw: 1, rx: 2 }));
            g.push(txt(cx0 + j * cw + cw / 2, cy0 + i * ch + 17, String(O[i][j]),
                { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(txt(cx0 + j * cw + cw / 2, cy0 + i * ch + 33, `(${E[i][j]})`,
                { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        g.push(txt(cx0 + 2 * cw + cw / 2, cy0 + i * ch + 24, String(rowS[i]),
            { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    colS.forEach((s, j) => g.push(txt(cx0 + j * cw + cw / 2, cy0 + 2 * ch + 18, String(s),
        { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    g.push(txt(cx0 + 2 * cw + cw / 2, cy0 + 2 * ch + 18, String(tot), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(548, 222, '기대도수 = 행합 × 열합 / 총합', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(548, 256, `χ² = ${chi2.toFixed(2)},  자유도 (2−1)(2−1) = 1`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(548, 280, '임곗값 3.84 보다 크다 — 기각한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 316], [694, 316]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 338, '기대도수로 나누는 이유는 같은 크기의 차이라도 기대도수가 작을 때 더 놀랍기 때문이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 360, '오른쪽 꼬리만 본다. χ² 가 크다는 것은 어느 쪽으로든 어긋났다는 뜻이므로 방향이 하나뿐이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 382, '기대도수가 5 보다 작은 칸이 있으면 근사가 나빠진다. 칸을 합치거나 정확검정을 쓴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-gof-table',
        svg: svg({
            width: W, height: H,
            title: '적합도 검정과 독립성 검정',
            desc: '주사위 눈의 관측도수와 기대도수의 비교, 그리고 두 집단 찬반 분할표에서 기대도수를 만드는 방법',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 12장 — 회귀와 상관
 * ================================================================== */

/** 12장 전체가 쓰는 예제 자료. 조명 시간 x(h)와 한 주 성장량 y(mm). 만들어 낸 값이다. */
const D12 = [[1, 3], [2, 5], [3, 4], [4, 8], [5, 10]];
const F12 = lsq(D12);

/* ---- 12-1. 산점도, 회귀선, 잔차 ---- */
add((() => {
    const W = 740, H = 366;
    const g = [txt(370, 24, '직선을 고르는 기준은 ‘세로 거리의 제곱합을 가장 작게’ 다',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 44, 356, 228, '자료와 최소제곱 직선', { sub: '점선이 잔차 — 관측값에서 직선까지의 세로 거리' }));
    const A = frame({ xRange: [0.3, 5.7], yRange: [0, 11.5], box: { x: 84, y: 92, w: 282, h: 118 } });
    g.push(axesEdge(A, { x0: 0.3, x1: 5.7, y0: 0, y1: 11.5, xTicks: [1, 2, 3, 4, 5], yTicks: [2, 4, 6, 8, 10] }));
    g.push(A.line([[0.3, F12.f(0.3)], [5.7, F12.f(5.7)]], { cls: 's1' }));
    for (const [x, y] of D12) {
        g.push(ln([[A.X(x), A.Y(y)], [A.X(x), A.Y(F12.f(x))]], { stroke: 'var(--s2)', sw: 1.6, dash: '4 3' }));
        g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 4));
        g.push(pdot(A.X(x), A.Y(F12.f(x)), 'var(--s1)', 2.6));
        const e = y - F12.f(x);
        g.push(txt(A.X(x) + 7, A.Y((y + F12.f(x)) / 2) + 4, (e > 0 ? '+' : '') + neg(e.toFixed(1)),
            { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(214, 254, `ŷ = ${F12.b0.toFixed(1)} + ${F12.b1.toFixed(1)}x,  잔차제곱합 ${F12.sse.toFixed(1)}`,
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(panel(408, 44, 296, 228, '왜 세로 거리인가', { sub: 'x 로 y 를 맞히는 것이 목적이다' }));
    const B = frame({ xRange: [2, 7], yRange: [3, 9.6], box: { x: 452, y: 98, w: 208, h: 112 } });
    g.push(B.line([[2.1, 2.7], [6.9, 7.5]], { cls: 's1' }));
    const P = [3.4, 8.2];
    g.push(ln([[B.X(P[0]), B.Y(P[1])], [B.X(P[0]), B.Y(4.0)]], { stroke: 'var(--s2)', sw: 2.6 }));
    // 수직 거리 — 직선 y = x + 0.6 에 내린 발
    const t = (P[0] + P[1] - 0.6) / 2;
    g.push(ln([[B.X(P[0]), B.Y(P[1])], [B.X(t), B.Y(t + 0.6)]], { stroke: 'var(--ink2)', sw: 1.6, dash: '4 3' }));
    g.push(pdot(B.X(P[0]), B.Y(P[1]), 'var(--ink)', 4.5));
    g.push(txt(B.X(3.28), B.Y(6.5), '세로 거리', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(3.28), B.Y(6.5) + 16, '이것을 줄인다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(4.75), B.Y(8.9), '수직 거리', { cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(4.75), B.Y(8.9) + 16, '이것이 아니다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 254, 'x 는 정확히 안다고 보고 y 의 오차만 센다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 288], [694, 288]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 310, '잔차의 합은 언제나 0 이다. 그래서 잔차를 그냥 더해 좋은 직선을 고를 수는 없다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 332, '절댓값을 쓰는 방법도 있지만 제곱을 쓰면 미분이 되고 답이 식으로 나온다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 356, '대가는 제곱이 큰 잔차를 크게 벌준다는 것 — 이상점 하나에 끌려가기 쉽다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-scatter-residual',
        svg: svg({
            width: W, height: H,
            title: '산점도, 최소제곱 직선, 잔차',
            desc: '자료 다섯 점과 최소제곱 직선, 각 점의 세로 방향 잔차. 최소제곱이 줄이는 것은 수직 거리가 아니라 세로 거리다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-2. 제곱합은 기울기에 대한 이차함수다 ---- */
add((() => {
    const W = 740, H = 352;
    const S = b1 => F12.syy - 2 * b1 * F12.sxy + b1 * b1 * F12.sxx;
    const cands = [1.0, F12.b1, 2.4];
    const g = [txt(370, 24, '절편을 최적으로 맞춰 두면 남는 것은 기울기 하나뿐이고, 제곱합은 그 이차함수다',
        { anchor: 'middle', cls: 'ink bold' })];

    g.push(panel(36, 44, 330, 226, '제곱합 S(b~1)', { sub: '위로 열린 포물선 — 최소가 하나뿐이다' }));
    const A = frame({ xRange: [0.2, 3.2], yRange: [0, 26], box: { x: 78, y: 96, w: 274, h: 112 } });
    g.push(axesEdge(A, { x0: 0.2, x1: 3.2, y0: 0, y1: 26, xTicks: [0.5, 1, 1.5, 2, 2.5, 3], yTicks: [10, 20] }));
    g.push(A.curve(S, { from: 0.2, to: 3.2, cls: 's1', steps: 180 }));
    cands.forEach((b, i) => {
        const col = i === 1 ? 'var(--s2)' : 'var(--ink2)';
        g.push(pdot(A.X(b), A.Y(S(b)), col, 4.5));
        g.push(txt(A.X(b), A.Y(S(b)) - 10, S(b).toFixed(1), { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(A.X(F12.b1), A.Y(S(F12.b1)) + 22, `b~1 = ${F12.b1.toFixed(1)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(201, 254, '미분해서 0 인 곳이 유일한 최소다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(382, 44, 322, 226, '같은 세 기울기의 직선', { sub: '셋 다 (x̄, ȳ) 를 지난다' }));
    const B = frame({ xRange: [0.3, 5.7], yRange: [0, 12], box: { x: 424, y: 96, w: 250, h: 112 } });
    g.push(axesEdge(B, { x0: 0.3, x1: 5.7, y0: 0, y1: 12, xTicks: [1, 2, 3, 4, 5], yTicks: [4, 8, 12] }));
    cands.forEach((b, i) => {
        const f = x => F12.yb + b * (x - F12.xb);
        const cl = i === 1 ? 's2' : 's1';
        g.push(B.line([[0.4, f(0.4)], [5.6, f(5.6)]], { cls: cl, dash: i === 1 ? undefined : '5 4' }));
    });
    for (const [x, y] of D12) g.push(pdot(B.X(x), B.Y(y), 'var(--ink)', 4));
    g.push(pdot(B.X(F12.xb), B.Y(F12.yb), 'var(--s3)', 5));
    g.push(txt(B.X(0.55), B.Y(10.9), '(x̄, ȳ)', { cls: 'ink bold', size: 'sm' }));
    g.push(arw(B.X(1.25), B.Y(10.5), B.X(2.82), B.Y(6.45), { cls: 'ark', width: 1.3 }));
    g.push(txt(543, 254, '가운데 실선이 최소제곱 직선', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 288], [694, 288]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 310, '두 후보의 제곱합이 똑같이 10.0 인 것에 주목하라. 최소에서 같은 거리만큼 어긋났기 때문이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 334, '이차함수라는 것이 최소제곱의 값어치다. 최솟값이 하나뿐이고 미분 한 번으로 식이 나온다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-lsq-parabola',
        svg: svg({
            width: W, height: H,
            title: '최소제곱 — 제곱합이 기울기의 이차함수라 최소가 하나다',
            desc: '기울기를 바꾸며 잔차제곱합을 그린 포물선과, 세 후보 기울기에 해당하는 직선들',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-3. 잔차 그림으로 가정을 확인한다 ---- */
add((() => {
    const W = 740, H = 398;
    const rand = rng(778899);
    const N = 26;
    const kinds = [
        {
            t: '좋은 잔차', s: '패턴 없고 폭이 일정', ok: true,
            f: i => gauss(rand) * 1,
        },
        {
            t: '곡선이 보인다', s: '직선 모형이 틀렸다', ok: false,
            f: (i, u) => 3.4 * (1 - 4 * (u - 0.5) ** 2) - 1.5 + gauss(rand) * 0.30,
        },
        {
            t: '나팔이 벌어진다', s: '등분산 가정이 깨졌다', ok: false,
            f: (i, u) => gauss(rand) * (0.16 + 2.7 * u),
        },
        {
            t: '점 하나가 튄다', s: '이상점 또는 기록 오류', ok: false,
            f: (i, u) => (i === 19 ? 3.6 : gauss(rand) * 0.62),
        },
        {
            t: '차례에 따라 밀린다', s: '빠진 변수 또는 자기상관', ok: false,
            f: (i, u) => 2.4 * (u - 0.5) + gauss(rand) * 0.42,
        },
    ];
    const g = [txt(370, 24, '회귀의 가정은 식이 아니라 그림으로 확인한다. 가로축은 예측값, 세로축은 잔차다',
        { anchor: 'middle', cls: 'ink bold' })];
    kinds.forEach((kd, k) => {
        const row = k < 3 ? 0 : 1;
        const col = k < 3 ? k : k - 3;
        const bx = 30 + col * 228, by = 44 + row * 146;
        g.push(panel(bx, by, 214, 134, kd.t, { sub: kd.s }));
        const A = frame({ xRange: [0, 1], yRange: [-4, 4], box: { x: bx + 26, y: by + 44, w: 172, h: 80 } });
        g.push(ln([[A.X(0), A.Y(0)], [A.X(1), A.Y(0)]], { stroke: 'var(--ink2)', sw: 1.4 }));
        for (let i = 0; i < N; i += 1) {
            const u = (i + 0.5) / N;
            const e = Math.max(-3.8, Math.min(3.8, kd.f(i, u)));
            g.push(pdot(A.X(u), A.Y(e), kd.ok ? 'var(--s1)' : 'var(--s2)', 3.2));
        }
        g.push(txt(bx + 22, A.Y(0) + 4, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    // 오른쪽 아래 설명 칸
    g.push(panel(486, 190, 214, 134, '무엇을 하면 되나'));
    const tips = ['곡선 → x² 항 또는 log 변환', '나팔 → y 변환, 가중최소제곱',
        '이상점 → 자료를 되짚어 본다', '추세 → 빠진 변수를 찾는다'];
    tips.forEach((s, i) => g.push(txt(500, 234 + i * 22, s, { cls: 'ink2', size: 'sm' })));

    g.push(ln([[46, 338], [694, 338]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 360, '결정계수가 커도 잔차 그림이 나쁘면 그 모형은 믿을 수 없다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 384, '거꾸로 잔차 그림이 깨끗하면 R² 가 작아도 모형 자체는 옳다 — 흩어짐이 큰 것일 뿐이다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-i-residual-four',
        svg: svg({
            width: W, height: H,
            title: '잔차 그림 — 좋은 잔차와 나쁜 잔차 네 유형',
            desc: '패턴이 없는 좋은 잔차와, 곡선·나팔·이상점·추세라는 네 가지 나쁜 잔차 패턴',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-4. 분산 분해와 결정계수 ---- */
add((() => {
    const W = 740, H = 360;
    const g = [txt(370, 24, '한 점의 편차를 두 조각으로 쪼갠다. 직선이 설명한 몫과 남은 몫',
        { anchor: 'middle', cls: 'ink bold' })];
    g.push(panel(36, 44, 372, 236, '점 하나에서 본 분해', { sub: '(y − ȳ) = (ŷ − ȳ) + (y − ŷ)' }));
    const A = frame({ xRange: [0.3, 6.4], yRange: [0, 11.5], box: { x: 82, y: 92, w: 300, h: 122 } });
    g.push(axesEdge(A, { x0: 0.3, x1: 6.4, y0: 0, y1: 11.5, xTicks: [1, 2, 3, 4, 5], yTicks: [2, 4, 6, 8, 10] }));
    g.push(ln([[A.X(0.3), A.Y(F12.yb)], [A.X(6.4), A.Y(F12.yb)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '5 4' }));
    g.push(txt(A.X(0.45), A.Y(F12.yb) - 7, 'ȳ = 6', { cls: 'ink2', size: 'sm' }));
    g.push(A.line([[0.3, F12.f(0.3)], [6.0, F12.f(6.0)]], { cls: 's1' }));
    for (const [x, y] of D12) g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 3.6));
    const px5 = 5, py5 = 10, ph5 = F12.f(5);
    // 세 막대를 나란히
    const bar = (dx, y1, y2, col, lab) => [
        ln([[A.X(px5 + dx), A.Y(y1)], [A.X(px5 + dx), A.Y(y2)]], { stroke: col, sw: 3.4, cap: 'butt' }),
        txt(A.X(px5 + dx), A.Y(Math.max(y1, y2)) - 7, lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' }),
    ].join('');
    g.push(bar(0.42, F12.yb, py5, 'var(--ink2)', '4.0'));
    g.push(bar(0.82, F12.yb, ph5, 'var(--s1)', '3.4'));
    g.push(bar(1.22, ph5, py5, 'var(--s2)', '0.6'));
    g.push(pdot(A.X(px5), A.Y(py5), 'var(--ink)', 4.6));
    g.push(pdot(A.X(px5), A.Y(ph5), 'var(--s1)', 3.4));
    g.push(txt(214, 252, '막대는 왼쪽부터 회색 전체 · 파랑 설명된 · 주황 남은', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(214, 272, '전체 = 설명된 + 남은,  4.0 = 3.4 + 0.6', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(panel(424, 44, 280, 236, '제곱해 모두 더하면', { sub: '교차항이 0 이어서 그대로 쪼개진다' }));
    const tot = F12.syy, ssr = F12.ssr, sse = F12.sse;
    const bw = 200, bx = 462, by = 106;
    g.push(box(bx, by, bw, 30, { fill: 'var(--s1)', op: 0.35, stroke: 'var(--s1)', sw: 1 }));
    g.push(txt(bx + bw / 2, by + 20, `SST = ${tot.toFixed(1)}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const w1 = (bw * ssr) / tot;
    g.push(box(bx, by + 44, w1, 30, { fill: 'var(--s3)', op: 0.4, stroke: 'var(--s3)', sw: 1 }));
    g.push(box(bx + w1, by + 44, bw - w1, 30, { fill: 'var(--s2)', op: 0.4, stroke: 'var(--s2)', sw: 1 }));
    g.push(txt(bx + w1 / 2, by + 64, `SSR ${ssr.toFixed(1)}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + w1 + (bw - w1) / 2, by + 92, `SSE ${sse.toFixed(1)}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(564, by + 124, `R² = SSR/SST = ${(ssr / tot).toFixed(2)}`, { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(564, by + 148, '흩어짐의 85% 를 직선이 설명한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 294], [694, 294]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 316, 'R² 은 ‘y 의 흩어짐 가운데 x 로 설명되는 몫’ 이다. 0 과 1 사이이고 단위가 없다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 340, 'R² 이 크다는 것이 인과를 뜻하지도, 모형이 옳다는 것을 뜻하지도 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-r2-split',
        svg: svg({
            width: W, height: H,
            title: '분산 분해와 결정계수',
            desc: '한 점의 편차를 설명된 몫과 남은 몫으로 쪼개고, 제곱해 더하면 총제곱합이 두 조각으로 나뉜다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-5. 신뢰구간과 예측구간 ---- */
add((() => {
    const W = 740, H = 352;
    const s = Math.sqrt(F12.sse / (F12.n - 2));
    const tc = 3.182; // t(3) 의 양쪽 2.5% 점
    const half = (x, extra) => tc * s * Math.sqrt(extra + 1 / F12.n + ((x - F12.xb) ** 2) / F12.sxx);
    const g = [txt(370, 24, '두 가지 물음이 있다. 평균이 어디인가, 그리고 다음 하나가 어디에 떨어지는가',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [0.4, 5.6], yRange: [-4, 16], box: { x: 96, y: 48, w: 468, h: 186 } });
    // 예측띠
    const bandPts = [];
    for (let i = 0; i <= 60; i += 1) {
        const x = 0.5 + (5 - 0.5) * (i / 60);
        bandPts.push([x, F12.f(x) + half(x, 1)]);
    }
    for (let i = 60; i >= 0; i -= 1) {
        const x = 0.5 + (5 - 0.5) * (i / 60);
        bandPts.push([x, F12.f(x) - half(x, 1)]);
    }
    g.push(poly(A, bandPts, 'var(--s2)', 0.16));
    const ciPts = [];
    for (let i = 0; i <= 60; i += 1) {
        const x = 0.5 + (5 - 0.5) * (i / 60);
        ciPts.push([x, F12.f(x) + half(x, 0)]);
    }
    for (let i = 60; i >= 0; i -= 1) {
        const x = 0.5 + (5 - 0.5) * (i / 60);
        ciPts.push([x, F12.f(x) - half(x, 0)]);
    }
    g.push(poly(A, ciPts, 'var(--s1)', 0.26));
    g.push(axesEdge(A, { x0: 0.4, x1: 5.6, y0: -4, y1: 16, xTicks: [1, 2, 3, 4, 5], yTicks: [0, 4, 8, 12, 16] }));
    g.push(A.line([[0.5, F12.f(0.5)], [5, F12.f(5)]], { cls: 's1' }));
    for (const [x, y] of D12) g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 4));
    g.push(ln([[A.X(F12.xb), A.Y(-3.6)], [A.X(F12.xb), A.Y(15)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(txt(A.X(F12.xb), A.Y(15) + 12, 'x̄ — 두 띠가 가장 좁은 곳', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(legend(586, 74, [{ slot: 1, name: '평균의 신뢰구간' }, { slot: 2, name: '개별값의 예측구간' }]));
    g.push(txt(586, 122, 'x~0 = 4 에서', { cls: 'ink', size: 'sm' }));
    g.push(txt(586, 142, `ŷ = ${F12.f(4).toFixed(1)}`, { cls: 'ink bold', size: 'sm' }));
    g.push(txt(586, 164, `신뢰 ±${half(4, 0).toFixed(2)}`, { cls: 'ink', size: 'sm' }));
    g.push(txt(586, 184, `예측 ±${half(4, 1).toFixed(2)}`, { cls: 'ink', size: 'sm' }));
    g.push(txt(586, 210, '두 배 넘게 넓다', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[46, 266], [694, 266]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 288, '평균의 구간은 직선의 위치가 얼마나 불확실한가만 담는다. 표본을 늘리면 0 으로 줄어든다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 312, '예측구간은 여기에 개체 자체의 흔들림 σ² 를 더한다. 표본을 늘려도 그 몫은 남는다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 336, '두 띠 모두 x̄ 에서 좁고 멀어질수록 벌어진다. 자료 범위 밖의 예측을 조심해야 하는 이유다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-ci-pi',
        svg: svg({
            width: W, height: H,
            title: '평균의 신뢰구간과 개별값의 예측구간',
            desc: '회귀직선 주위의 두 띠. 좁은 띠는 평균 응답의 신뢰구간이고 넓은 띠는 개별 관측의 예측구간이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-6. 상관은 인과가 아니다 ---- */
add((() => {
    const W = 740, H = 388;
    const rand = rng(135791);
    const groups = [];
    for (let k = 0; k < 3; k += 1) {
        const c = 3 + 3 * k;
        const pts = [];
        for (let i = 0; i < 13; i += 1) pts.push([c + gauss(rand) * 0.8, c + gauss(rand) * 0.8]);
        // 층 안의 상관을 정확히 0 으로 맞춘다. 교란변수만이 관계를 만들었다는 그림이어야 한다.
        const fit = lsq(pts);
        groups.push(pts.map(([x, y]) => [x, y - fit.b1 * (x - fit.xb)]));
    }
    const all = groups.flat();
    const rAll = lsq(all).r;
    const rIn = groups.map(p => (Math.abs(lsq(p).r) < 0.005 ? 0 : lsq(p).r));
    const g = [txt(370, 24, '왼쪽만 보면 두 변수가 함께 커진다. 오른쪽은 같은 점을 숨은 변수로 나눈 것이다',
        { anchor: 'middle', cls: 'ink bold' })];
    const mk = (bx, title, sub, colored) => {
        const out = [panel(bx, 44, 274, 212, title, { sub })];
        const A = frame({ xRange: [0, 12], yRange: [0, 12], box: { x: bx + 40, y: 92, w: 208, h: 132 } });
        out.push(axesEdge(A, { x0: 0, x1: 12, y0: 0, y1: 12, xTicks: [3, 6, 9, 12], yTicks: [3, 6, 9, 12] }));
        groups.forEach((pts, k) => {
            const col = colored ? `var(--s${k + 1})` : 'var(--s1)';
            for (const [x, y] of pts) out.push(pdot(A.X(x), A.Y(y), col, 3.4));
            if (colored) {
                const f = lsq(pts);
                out.push(A.line([[f.xb - 2, f.f(f.xb - 2)], [f.xb + 2, f.f(f.xb + 2)]], { cls: `s${k + 1}` }));
            }
        });
        if (!colored) {
            const f = lsq(all);
            out.push(A.line([[0.5, f.f(0.5)], [11.5, f.f(11.5)]], { cls: 's2' }));
        }
        out.push(txt(bx + 137, 268, colored
            ? `층 안의 상관 ${rIn.map(v => v.toFixed(2)).join(', ')}`
            : `전체 상관계수 r = ${rAll.toFixed(2)}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(36, '숨은 변수를 모른 채 본 산점도', '점 13 × 3 = 39 개', false));
    g.push(mk(330, '숨은 변수 Z 로 층을 나눈 산점도', 'Z 의 세 값으로 갈랐다', true));

    // 인과 그림
    g.push(panel(626, 44, 78, 212, ''));
    g.push(txt(665, 66, '숨은 변수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(665, 92, 'Z', { anchor: 'middle', cls: 'ink bold' }));
    g.push(arw(658, 102, 645, 130, { cls: 's3', width: 1.6 }));
    g.push(arw(672, 102, 685, 130, { cls: 's3', width: 1.6 }));
    g.push(txt(641, 146, 'X', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(689, 146, 'Y', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[650, 141], [680, 141]], { stroke: 'var(--ink2)', sw: 1.4, dash: '4 3' }));
    g.push(txt(665, 176, '직접 연결이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(665, 192, '없어도 상관은', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(665, 208, '나타난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 288], [694, 288]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 310, `층 안에서는 상관이 사라졌다. 두 변수를 함께 움직인 것은 X 가 아니라 Z 였다`,
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 334, '상관이 인과가 아닌 세 가지 이유 — 방향이 반대일 수 있다, 교란변수가 있을 수 있다, 우연일 수 있다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 358, '인과를 말하려면 무작위 배정 실험을 하거나 교란변수를 찾아 통제해야 한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 380, '3장의 심슨의 역설이 바로 이 구조이며, 거기서는 층을 나눌 때 부호까지 뒤집혔다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-confounder',
        svg: svg({
            width: W, height: H,
            title: '상관은 인과가 아니다 — 교란변수',
            desc: '전체로 보면 강한 양의 상관이지만 숨은 변수로 층을 나누면 각 층 안에서 상관이 사라지는 자료',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-7. 이상점 하나가 직선을 끌고 간다 ---- */
add((() => {
    const W = 740, H = 336;
    const base = [[1, 1.2], [2, 2.4], [3, 2.8], [4, 4.3], [5, 5.1], [6, 5.8], [7, 7.4], [8, 7.9], [9, 9.2]];
    const out = [16, 3.5];
    const f0 = lsq(base);
    const f1 = lsq([...base, out]);
    const g = [txt(370, 24, '점 아홉 개가 만든 직선에 멀리 떨어진 점 하나를 더했다',
        { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [0, 17.5], yRange: [0, 11], box: { x: 92, y: 50, w: 456, h: 176 } });
    g.push(axesEdge(A, {
        x0: 0, x1: 17.5, y0: 0, y1: 11,
        xTicks: [2, 4, 6, 8, 10, 12, 14, 16], yTicks: [2, 4, 6, 8, 10],
    }));
    const xTop = (10.7 - f0.b0) / f0.b1;
    g.push(A.line([[0.3, f0.f(0.3)], [xTop, f0.f(xTop)]], { cls: 's1', dash: '6 4' }));
    g.push(A.line([[0.3, f1.f(0.3)], [17, f1.f(17)]], { cls: 's2' }));
    for (const [x, y] of base) g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 4));
    g.push(pdot(A.X(out[0]), A.Y(out[1]), 'var(--s2)', 6));
    g.push(circ(A.X(out[0]), A.Y(out[1]), 11, { stroke: 'var(--s2)', sw: 1.4, dash: '3 3' }));
    g.push(txt(A.X(out[0]) - 16, A.Y(out[1]) - 14, '더한 점 하나', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(legend(580, 82, [
        { slot: 1, name: `없을 때 기울기 ${f0.b1.toFixed(2)}` },
        { slot: 2, name: `있을 때 기울기 ${f1.b1.toFixed(2)}` },
    ]));
    g.push(txt(580, 136, `R² 은 ${(f0.ssr / f0.syy).toFixed(2)} 에서`, { cls: 'ink', size: 'sm' }));
    g.push(txt(580, 154, `${(f1.ssr / f1.syy).toFixed(2)} 로 떨어진다`, { cls: 'ink', size: 'sm' }));
    g.push(txt(580, 180, '점 하나가 결론을', { cls: 'ink2', size: 'sm' }));
    g.push(txt(580, 196, '뒤집을 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[46, 250], [694, 250]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 272, 'x 가 자료 뭉치에서 멀리 떨어진 점은 지렛대가 길어 직선을 크게 움직인다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 294, '제곱이 큰 잔차를 크게 벌주기 때문이며, 이것이 최소제곱의 대가다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 318, '그래서 계수를 읽기 전에 산점도와 잔차 그림을 먼저 본다. 지워도 되는 점인지는 자료를 되짚어 판단한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-outlier-leverage',
        svg: svg({
            width: W, height: H,
            title: '이상점 하나가 회귀직선을 끌고 간다',
            desc: '자료 뭉치에서 멀리 떨어진 점 하나를 더하면 기울기와 결정계수가 크게 달라진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-8. 상관계수와 회귀 — 두 회귀직선과 평균으로의 회귀 ---- */
add((() => {
    const W = 740, H = 400;
    const sx = Math.sqrt(F12.sxx / (F12.n - 1));
    const sy = Math.sqrt(F12.syy / (F12.n - 1));
    const bYX = F12.b1;                  // y 를 x 로 회귀한 기울기
    const bSD = sy / sx;                 // SD 선의 기울기. 두 회귀 기울기의 기하평균이다
    const bXY = F12.syy / F12.sxy;       // x 를 y 로 회귀한 직선을 이 평면에 그렸을 때의 기울기
    const bXYown = F12.sxy / F12.syy;    // 그 회귀 자신의 기울기(x 방향으로 잰 값)
    const g = [txt(370, 24, '같은 자료에 직선이 하나만 있는 것이 아니다. 무엇으로 무엇을 맞히려는지가 직선을 정한다',
        { anchor: 'middle', cls: 'ink bold' })];

    /* 왼쪽 — 원래 단위에서 본 세 직선 */
    g.push(panel(36, 44, 356, 262, '두 회귀직선과 SD 선', { sub: '셋 다 (x̄, ȳ) 를 지나고 기울기만 다르다' }));
    const A = frame({ xRange: [0.2, 5.9], yRange: [0, 12.6], box: { x: 88, y: 94, w: 276, h: 140 } });
    g.push(axesEdge(A, { x0: 0.2, x1: 5.9, y0: 0, y1: 12.6, xTicks: [1, 2, 3, 4, 5], yTicks: [3, 6, 9, 12] }));
    const seg = b => [[0.4, F12.yb + b * (0.4 - F12.xb)], [5.7, F12.yb + b * (5.7 - F12.xb)]];
    g.push(A.line(seg(bXY), { cls: 's2', dash: '6 4' }));
    g.push(A.line(seg(bSD), { cls: 's3', dash: '2 4' }));
    g.push(A.line(seg(bYX), { cls: 's1' }));
    for (const [x, y] of D12) g.push(pdot(A.X(x), A.Y(y), 'var(--ink)', 4));
    g.push(pdot(A.X(F12.xb), A.Y(F12.yb), 'var(--ink)', 5.5));
    g.push(txt(A.X(2.95) - 4, A.Y(6.6), '(x̄, ȳ)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(legend(238, 186, [
        { slot: 1, name: 'y 를 x 로 회귀' },
        { slot: 3, name: 'SD 선' },
        { slot: 2, name: 'x 를 y 로 회귀' },
    ]));
    g.push(txt(214, 272, `기울기는 ${bYX.toFixed(2)} · ${bSD.toFixed(2)} · ${bXY.toFixed(2)} 순으로 서 있다`,
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(214, 290, `x 를 y 로 잰 기울기 ${bXYown.toFixed(2)} 을 이 평면에 그리면 ${bXY.toFixed(2)} 이 된다`,
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 오른쪽 — 표준화한 축에서는 기울기가 곧 r 이다 */
    g.push(panel(408, 44, 296, 262, '표준화한 축에서 본 기울기', { sub: 'z 단위로 바꾸면 기울기가 곧 r 이다' }));
    const B = frame({ xRange: [-2.3, 2.6], yRange: [-2.3, 2.6], box: { x: 470, y: 96, w: 158, h: 158 } });
    g.push(ln([[B.X(-2.3), B.Y(0)], [B.X(2.6), B.Y(0)]], { stroke: 'var(--ink2)', sw: 1.4 }));
    g.push(ln([[B.X(0), B.Y(-2.3)], [B.X(0), B.Y(2.6)]], { stroke: 'var(--ink2)', sw: 1.4 }));
    // 음수 쪽 눈금은 축 위에 적는다. 아래에 적으면 왼쪽 아래로 내려가는 직선들과 겹친다.
    for (const t of [-2, -1, 1, 2]) {
        g.push(txt(B.X(t), B.Y(0) + (t < 0 ? -7 : 15), neg(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of [-2, -1, 1, 2]) g.push(txt(B.X(0) - 6, B.Y(t) + 4, neg(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const zseg = r => [[-2.2, -2.2 * r], [2.4, 2.4 * r]];
    g.push(ln([[B.X(-2.2), B.Y(-2.2)], [B.X(2.4), B.Y(2.4)]], { stroke: 'var(--ink2)', sw: 1.5, dash: '5 4' }));
    g.push(B.line(zseg(0.7), { cls: 's1' }));
    g.push(B.line(zseg(0.3), { cls: 's2', dash: '5 4' }));
    g.push(txt(632, B.Y(2.4) + 4, 'r = 1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(632, B.Y(1.68) + 4, 'r = 0.7', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(632, B.Y(0.72) + 4, 'r = 0.3', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[B.X(2), B.Y(0)], [B.X(2), B.Y(2)]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(pdot(B.X(2), B.Y(2), 'var(--ink2)', 3.4));
    g.push(pdot(B.X(2), B.Y(1.4), 'var(--s1)', 4.2));
    g.push(pdot(B.X(2), B.Y(0.6), 'var(--s2)', 3.4));
    g.push(arw(B.X(2.2), B.Y(1.95), B.X(2.2), B.Y(0.72), { cls: 's2', width: 1.8 }));
    g.push(txt(556, 276, 'z~x = 2 인 개체의 예측은 2 가 아니라 2r 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 294, 'r 이 작을수록 평균 쪽으로 더 눌린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 322], [694, 322]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 344, '표준화하면 기울기가 곧 r 이 된다. 그래서 회귀계수를 b~1 = r s~y / s~x 로 적을 수 있다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 368, `y 를 x 로 잰 기울기 ${bYX.toFixed(2)} 과 x 를 y 로 잰 기울기 ${bXYown.toFixed(2)} 의 곱이 `
        + `${(bYX * bXYown).toFixed(2)} = R² 이다`, { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 392, 'SD 선의 기울기는 두 회귀 기울기의 기하평균이다. 어느 쪽을 맞히든 회귀직선은 SD 선보다 완만하다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-i-two-lines',
        svg: svg({
            width: W, height: H,
            title: '상관계수와 회귀 — 두 회귀직선, SD 선, 평균으로의 회귀',
            desc: '같은 자료에 y 를 x 로 회귀한 직선과 x 를 y 로 회귀한 직선, 그리고 SD 선을 함께 그렸다. 표준화한 축에서는 회귀직선의 기울기가 상관계수와 같다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
