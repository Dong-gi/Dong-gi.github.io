/**
 * 통계학 7·8·9장(주요 이산분포 / 주요 연속분포 / 표본분포와 극한정리)의 그림.
 *
 * 이름은 전부 `st-d-` 로 시작한다(담당 E 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~0` 표기를, 나머지는 유니코드(√, π, σ, μ, λ, α, β, χ, ≈, ≤)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰면 안 된다.
 * 분포 기호는 ASCII 물결 대신 유니코드 닮음표 ∼ 를 쓴다. HTML 엔티티도 쓸 수 없고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 난수를 쓰는 그림(중심극한정리 모의실험, 큰 수의 법칙)은 씨앗을 고정한
 * 선형합동생성기를 쓴다. 빌드할 때마다 그림이 달라지면 안 되기 때문이다.
 * 정확한 분포를 계산할 수 있는 곳(이산 모집단의 표본평균)은 합성곱으로 정확히 구한다.
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

/** 양쪽 화살표. 길이·폭을 재는 표시에 쓴다. */
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
function underArea(g, f, from, to, col, op, base = 0, steps = 80) {
    const pts = [[from, base]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([x, f(x)]);
    }
    pts.push([to, base]);
    return poly(g, pts, col, op);
}

/**
 * 가로축만 그린다. 밀도·질량 그림에서는 세로축이 뜻이 없고, 값 범위 안에
 * 0 이 들어 있으면 lib 의 axes() 가 그림 한가운데에 세로축을 그어 버린다.
 */
function xaxis(fr, [x0, x1], ticks = [], { dy = 15, fmt = v => String(v) } = {}) {
    const y = fr.Y(0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { stroke: 'var(--ink2)', sw: 1 }));
        out.push(txt(fr.X(t), y + dy, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/**
 * 이산 확률 막대. pts 는 [값, 확률] 배열, step 은 값 사이의 간격(데이터 단위).
 * 막대 폭은 그 간격의 wfrac 배다.
 */
function bars(fr, pts, { col = 'var(--s1)', op = 0.3, step = 1, wfrac = 0.68, sw = 1, y0 = 0 } = {}) {
    const halfW = Math.max(0.8, ((fr.X(step) - fr.X(0)) * wfrac) / 2);
    return pts.map(([x, p]) => box(fr.X(x) - halfW, fr.Y(p), halfW * 2, fr.Y(y0) - fr.Y(p), {
        fill: col, op, stroke: col, sw, rx: 1.2,
    })).join('');
}

/** 이산 확률을 가는 막대(성냥개비)로. 두 분포를 겹쳐 볼 때 쓴다. */
function sticks(fr, pts, { col = 'var(--s1)', sw = 2, y0 = 0 } = {}) {
    return pts.map(([x, p]) => ln([[fr.X(x), fr.Y(y0)], [fr.X(x), fr.Y(p)]], { stroke: col, sw, cap: 'butt' })).join('');
}

/** 씨앗을 고정한 난수. 빌드 결과가 매번 같아야 한다. */
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}

/* ------------------------------------------------------------------ *
 * 공통 소도구 — 확률 계산
 * ------------------------------------------------------------------ */

const LANCZOS = [
    676.5203681218851, -1259.1392167224028, 771.32342877765313,
    -176.61502916214059, 12.507343278686905, -0.13857109526572012,
    9.9843695780195716e-6, 1.5056327351493116e-7,
];

/** 로그 감마함수(Lanczos 근사). 큰 계승을 넘치지 않게 다루려고 쓴다. */
function lgamma(z) {
    if (z < 0.5) return Math.log(PI / Math.sin(PI * z)) - lgamma(1 - z);
    const w = z - 1;
    let x = 0.99999999999980993;
    for (let i = 0; i < 8; i += 1) x += LANCZOS[i] / (w + i + 1);
    const t = w + 7.5;
    return 0.5 * Math.log(2 * PI) + (w + 0.5) * Math.log(t) - t + Math.log(x);
}
const gammaFn = z => Math.exp(lgamma(z));
const lfact = n => lgamma(n + 1);
const lchoose = (n, k) => lfact(n) - lfact(k) - lfact(n - k);

const binomPmf = (k, n, p) => (k < 0 || k > n ? 0
    : Math.exp(lchoose(n, k) + k * Math.log(p) + (n - k) * Math.log(1 - p)));
const poisPmf = (k, lam) => Math.exp(-lam + k * Math.log(lam) - lfact(k));
const geomPmf = (k, p) => p * (1 - p) ** (k - 1);
const nbinPmf = (x, r, p) => (x < r ? 0 : Math.exp(lchoose(x - 1, r - 1)) * p ** r * (1 - p) ** (x - r));
function hyperPmf(x, N, K, n) {
    if (x < 0 || x > K || n - x < 0 || n - x > N - K) return 0;
    return Math.exp(lchoose(K, x) + lchoose(N - K, n - x) - lchoose(N, n));
}

const normPdf = (x, mu = 0, sd = 1) => Math.exp(-((x - mu) ** 2) / (2 * sd * sd)) / (sd * Math.sqrt(2 * PI));
const expPdf = (x, lam) => (x < 0 ? 0 : lam * Math.exp(-lam * x));
const gammaPdf = (x, a, th) => (x <= 0 ? 0
    : Math.exp((a - 1) * Math.log(x) - x / th - a * Math.log(th) - lgamma(a)));
const chisqPdf = (x, k) => gammaPdf(x, k / 2, 2);
const betaPdf = (x, a, b) => (x <= 0 || x >= 1 ? 0
    : Math.exp((a - 1) * Math.log(x) + (b - 1) * Math.log(1 - x) + lgamma(a + b) - lgamma(a) - lgamma(b)));
const tPdf = (t, k) => Math.exp(lgamma((k + 1) / 2) - lgamma(k / 2)) / Math.sqrt(k * PI)
    * (1 + (t * t) / k) ** (-(k + 1) / 2);
function fPdf(x, d1, d2) {
    if (x <= 0) return 0;
    const l = (d1 / 2) * Math.log(d1 / d2) + (d1 / 2 - 1) * Math.log(x)
        - ((d1 + d2) / 2) * Math.log(1 + (d1 * x) / d2)
        + lgamma((d1 + d2) / 2) - lgamma(d1 / 2) - lgamma(d2 / 2);
    return Math.exp(l);
}

/* ================================================================== *
 * 7장 — 주요 이산분포
 * ================================================================== */

/* ---- 7-1. 이산균등분포와 베르누이 시행 ---- */
add((() => {
    const W = 700, H = 320;
    const g = [panel(40, 40, 300, 232, '이산균등분포', { sub: '주사위 눈 — 값 6개가 모두 1/6' })];
    const A = frame({ xRange: [0.2, 6.8], yRange: [0, 0.26], box: { x: 74, y: 90, w: 236, h: 118 } });
    g.push(bars(A, [1, 2, 3, 4, 5, 6].map(k => [k, 1 / 6]), { col: 'var(--s1)', op: 0.32 }));
    g.push(xaxis(A, [0.2, 6.8], [1, 2, 3, 4, 5, 6]));
    g.push(ln([[A.X(0.2), A.Y(1 / 6)], [A.X(6.8), A.Y(1 / 6)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
    g.push(txt(A.X(6.8) + 6, A.Y(1 / 6) - 8, '1/6', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(190, 250, 'μ = 3.5,  σ² = 35/12 ≈ 2.92', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(360, 40, 300, 232, '베르누이 시행', { sub: '값 두 개 — 성공 p, 실패 1 − p' }));
    const B = frame({ xRange: [-0.55, 1.55], yRange: [0, 0.85], box: { x: 424, y: 90, w: 176, h: 118 } });
    g.push(bars(B, [[0, 0.7], [1, 0.3]], { col: 'var(--s3)', op: 0.34, wfrac: 0.42 }));
    g.push(xaxis(B, [-0.55, 1.55]));
    g.push(txt(B.X(0), B.Y(0) + 16, '0 (실패)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(1), B.Y(0) + 16, '1 (성공)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(0), B.Y(0.7) - 8, '0.7', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(B.X(1), B.Y(0.3) - 8, '0.3', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(510, 250, 'p = 0.3 일 때  μ = 0.3,  σ² = 0.21', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(350, 300, '두 분포 모두 값이 몇 개뿐이다. 뒤에 나오는 분포는 전부 이 둘을 되풀이해 만든다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-uniform-bernoulli',
        svg: svg({
            width: W, height: H,
            title: '이산균등분포와 베르누이 시행의 확률질량',
            desc: '왼쪽은 여섯 값이 모두 1/6 인 이산균등분포, 오른쪽은 실패 0.7 성공 0.3 인 베르누이 시행이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-2. 이항분포 — 모수가 바뀌면 모양이 어떻게 바뀌나 ---- */
add((() => {
    const W = 730, H = 414;
    const g = [];
    const mk = (bx, by, n, p, cls) => {
        const top = Math.max(...Array.from({ length: n + 1 }, (_, k) => binomPmf(k, n, p)));
        const fr = frame({
            xRange: [-0.6, n + 0.6], yRange: [0, top * 1.28],
            box: { x: bx, y: by, w: 268, h: 108 },
        });
        const stepTick = n <= 12 ? 2 : 5;
        const ticks = [];
        for (let k = 0; k <= n; k += stepTick) ticks.push(k);
        const out = [bars(fr, Array.from({ length: n + 1 }, (_, k) => [k, binomPmf(k, n, p)]),
            { col: `var(--${cls})`, op: 0.32, sw: 0.9 })];
        out.push(xaxis(fr, [-0.6, n + 0.6], ticks));
        const mu = n * p;
        out.push(ln([[fr.X(mu), fr.Y(0)], [fr.X(mu), fr.Y(top * 1.16)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
        out.push(txt(bx + 4, by - 8, `n = ${n},  p = ${p}`, { cls: 'ink bold' }));
        out.push(txt(bx + 264, by - 8, `μ = ${r2(mu)},  σ = ${r2(Math.sqrt(n * p * (1 - p)))}`,
            { anchor: 'end', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, 64, 10, 0.2, 's1'));
    g.push(mk(400, 64, 10, 0.5, 's1'));
    g.push(mk(56, 234, 10, 0.8, 's1'));
    g.push(mk(400, 234, 40, 0.2, 's3'));
    g.push(txt(365, 32, '이항분포 — 성공 횟수 k 의 확률', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 372, 'p 가 0.5 보다 작으면 오른쪽으로 꼬리가 길고, 0.5 면 좌우 대칭, 0.5 보다 크면 반대로 기운다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 398, 'p 를 그대로 두고 n 만 키우면(오른쪽 아래) 치우침이 옅어지고 종 모양에 가까워진다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-binom-shapes',
        svg: svg({
            width: W, height: H,
            title: '이항분포의 모수와 모양',
            desc: 'n 이 10 일 때 p 가 0.2, 0.5, 0.8 인 세 경우와 n 이 40 이고 p 가 0.2 인 경우의 확률질량을 비교한다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-3. 초기하분포와 이항 근사 ---- */
add((() => {
    const W = 730, H = 388;
    const n = 5, frac = 0.2;
    const g = [];
    const mk = (bx, N, title) => {
        const K = Math.round(N * frac);
        const hs = Array.from({ length: n + 1 }, (_, x) => [x, hyperPmf(x, N, K, n)]);
        const bs = Array.from({ length: n + 1 }, (_, x) => [x, binomPmf(x, n, frac)]);
        const top = Math.max(...hs.map(h => h[1]), ...bs.map(b => b[1]));
        const fr = frame({ xRange: [-0.6, n + 0.6], yRange: [0, top * 1.22], box: { x: bx, y: 96, w: 186, h: 140 } });
        const out = [
            bars(fr, hs, { col: 'var(--s1)', op: 0.34, wfrac: 0.62 }),
            sticks(fr, bs, { col: 'var(--s2)', sw: 3 }),
            xaxis(fr, [-0.6, n + 0.6], [0, 1, 2, 3, 4, 5]),
        ];
        const gap = Math.max(...hs.map((h, i) => Math.abs(h[1] - bs[i][1])));
        out.push(txt(bx + 93, 76, title, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 93, 274, `n/N = ${r2(n / N)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        out.push(txt(bx + 93, 292, `가장 큰 차이 ${gap.toFixed(3)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(58, 20, 'N = 20 (K = 4)'));
    g.push(mk(288, 60, 'N = 60 (K = 12)'));
    g.push(mk(518, 400, 'N = 400 (K = 80)'));
    g.push(txt(365, 34, '상자에 든 20% 가 불량일 때, 5개를 비복원으로 뽑아 나온 불량 개수', { anchor: 'middle', cls: 'ink bold' }));
    g.push(legend(268, 328, [{ slot: 1, name: '초기하분포 (비복원, 막대)' }, { slot: 2, name: '이항분포 (복원, 막대 위 선)' }]));
    g.push(txt(365, 374, '모집단이 커질수록 한 개 뽑은 것이 남은 비율을 거의 바꾸지 못한다. 그래서 두 분포가 겹친다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-hyper-vs-binom',
        svg: svg({
            width: W, height: H,
            title: '초기하분포가 이항분포로 다가가는 모습',
            desc: '불량 비율이 20퍼센트로 같은 모집단에서 5개를 비복원으로 뽑을 때, 모집단 크기가 20, 60, 400 으로 커지면 초기하분포가 이항분포에 가까워진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-4. 기하분포와 무기억성 ---- */
add((() => {
    const W = 730, H = 360;
    const p = 0.3, K = 12;
    const g = [panel(40, 44, 330, 250, '기하분포', { sub: 'p = 0.3 일 때 첫 성공이 나오는 시행 번호' })];
    const fr = frame({ xRange: [0.3, K + 0.7], yRange: [0, 0.345], box: { x: 76, y: 96, w: 268, h: 132 } });
    g.push(bars(fr, Array.from({ length: K }, (_, i) => [i + 1, geomPmf(i + 1, p)]), { col: 'var(--s1)', op: 0.34 }));
    g.push(xaxis(fr, [0.3, K + 0.7], [1, 2, 4, 6, 8, 10, 12]));
    g.push(txt(205, 268, '가장 흔한 값은 언제나 1 이다.  μ = 1/p ≈ 3.33', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(390, 44, 300, 250, '무기억성', { sub: '이미 5번 실패했다는 사실을 알아도' }));
    const B = frame({ xRange: [0.3, 8.7], yRange: [0, 0.345], box: { x: 424, y: 108, w: 236, h: 120 } });
    g.push(bars(B, Array.from({ length: 8 }, (_, i) => [i + 1, geomPmf(i + 1, p)]), { col: 'var(--s3)', op: 0.3 }));
    g.push(xaxis(B, [0.3, 8.7], [1, 2, 3, 4, 5, 6, 7, 8]));
    g.push(txt(540, 96, '남은 기다림의 분포는 처음과 똑같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(540, 268, 'P(X > 5 + k | X > 5) = P(X > k)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(365, 320, '‘다섯 번이나 안 나왔으니 이제 나올 때가 됐다’ 는 틀렸다. 동전은 앞의 결과를 기억하지 못한다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 344, '기하분포는 이 성질을 갖는 유일한 이산분포다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-geom-memoryless',
        svg: svg({
            width: W, height: H,
            title: '기하분포의 확률질량과 무기억성',
            desc: '첫 성공까지의 시행 횟수 분포는 1 에서 가장 크고 지수적으로 줄어들며, 이미 몇 번 실패했든 남은 기다림의 분포가 같다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-5. 음이항분포 — 기하분포의 확장 ---- */
add((() => {
    const W = 700, H = 342;
    const p = 0.4, K = 20;
    const fr = frame({ xRange: [0.2, K + 0.8], yRange: [0, 0.42], box: { x: 70, y: 62, w: 456, h: 170 } });
    const g = [fr.axes({ xTicks: [], yTicks: [0.1, 0.2, 0.3, 0.4], grid: false })];
    const setup = [{ r: 1, cls: 's1' }, { r: 3, cls: 's2' }, { r: 6, cls: 's3' }];
    for (const s of setup) {
        const pts = Array.from({ length: K }, (_, i) => [i + 1, nbinPmf(i + 1, s.r, p)]);
        g.push(fr.line(pts, { cls: s.cls }));
        for (const [x, y] of pts) if (y > 0.004) g.push(pdot(fr.X(x), fr.Y(y), `var(--${s.cls})`, 2.6));
    }
    for (let k = 2; k <= K; k += 2) g.push(txt(fr.X(k), fr.Y(0) + 16, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(K) + 44, fr.Y(0) + 4, 'x', { cls: 'ink2', size: 'sm' }));
    g.push(txt(70, 44, '확률', { cls: 'ink2', size: 'sm' }));
    g.push(legend(552, 92, [
        { slot: 1, name: 'r = 1 (기하분포)' },
        { slot: 2, name: 'r = 3' },
        { slot: 3, name: 'r = 6' },
    ]));
    g.push(txt(552, 168, 'p = 0.4', { cls: 'ink bold' }));
    g.push(txt(552, 190, 'μ = r/p', { cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 278, 'r 번째 성공이 x 번째 시행에서 나올 확률. r 이 커지면 봉우리가 오른쪽으로 밀리고 낮아진다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 306, 'r = 1 이 기하분포다. 음이항분포는 기하분포를 r 개 이어 붙인 것이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-negbin-shapes',
        svg: svg({
            width: W, height: H,
            title: '음이항분포 — r 번째 성공까지의 시행 횟수',
            desc: '성공 확률 0.4 에서 r 이 1, 3, 6 일 때의 확률질량. r 이 1 이면 기하분포와 같다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-6. 푸아송 분포 ---- */
add((() => {
    const W = 730, H = 358;
    const g = [];
    const mk = (bx, lam, kmax) => {
        const pts = Array.from({ length: kmax + 1 }, (_, k) => [k, poisPmf(k, lam)]);
        const top = Math.max(...pts.map(q => q[1]));
        const fr = frame({ xRange: [-0.6, kmax + 0.6], yRange: [0, top * 1.2], box: { x: bx, y: 84, w: 190, h: 148 } });
        const st = kmax <= 6 ? 1 : kmax <= 12 ? 2 : 4;
        const ticks = [];
        for (let k = 0; k <= kmax; k += st) ticks.push(k);
        const out = [bars(fr, pts, { col: 'var(--s1)', op: 0.33, sw: 0.9 })];
        out.push(xaxis(fr, [-0.6, kmax + 0.6], ticks));
        out.push(ln([[fr.X(lam), fr.Y(0)], [fr.X(lam), fr.Y(top * 1.1)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
        out.push(txt(bx + 95, 66, `λ = ${lam}`, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 95, 268, `μ = σ² = ${lam}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, 0.8, 6));
    g.push(mk(276, 3, 12));
    g.push(mk(496, 9, 22));
    g.push(txt(365, 34, '푸아송 분포 — 일정한 구간에서 사건이 일어난 횟수', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 308, 'λ 가 작으면 0 이 가장 흔하고 오른쪽으로 길게 늘어진다. λ 가 커지면 대칭에 가까워진다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 334, '점선은 평균 λ 다. 푸아송 분포는 평균과 분산이 같아서 모수 하나가 모양을 다 정한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-poisson-shapes',
        svg: svg({
            width: W, height: H,
            title: '푸아송 분포의 모수와 모양',
            desc: '평균 λ 가 0.8, 3, 9 일 때의 확률질량. λ 가 커질수록 봉우리가 오른쪽으로 가고 좌우 대칭에 가까워진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 7-7. 이항분포를 푸아송으로 근사한다 ---- */
add((() => {
    const W = 730, H = 372;
    const lam = 2;
    const g = [];
    const mk = (bx, n) => {
        const p = lam / n;
        const kmax = 8;
        const bs = Array.from({ length: kmax + 1 }, (_, k) => [k, binomPmf(k, n, p)]);
        const ps = Array.from({ length: kmax + 1 }, (_, k) => [k, poisPmf(k, lam)]);
        const top = Math.max(...bs.map(b => b[1]), ...ps.map(q => q[1]));
        const fr = frame({ xRange: [-0.6, kmax + 0.6], yRange: [0, top * 1.2], box: { x: bx, y: 96, w: 186, h: 132 } });
        const out = [
            bars(fr, bs, { col: 'var(--s1)', op: 0.32, wfrac: 0.6 }),
            sticks(fr, ps, { col: 'var(--s2)', sw: 3 }),
            xaxis(fr, [-0.6, kmax + 0.6], [0, 2, 4, 6, 8]),
        ];
        const gap = Math.max(...bs.map((b, i) => Math.abs(b[1] - ps[i][1])));
        out.push(txt(bx + 93, 78, `n = ${n},  p = ${p}`, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 93, 268, `가장 큰 차이 ${gap.toFixed(3)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(58, 8));
    g.push(mk(288, 40));
    g.push(mk(518, 400));
    g.push(txt(365, 34, 'np = 2 를 고정하고 n 을 키운다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 56, '시행 수가 늘고 한 번의 확률이 작아져도 평균은 그대로 2 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(legend(258, 304, [{ slot: 1, name: '이항분포 (막대)' }, { slot: 2, name: '푸아송 분포 λ = 2 (선)' }]));
    g.push(txt(365, 356, 'n 이 크고 p 가 작으면 이항분포는 λ = np 인 푸아송 분포와 사실상 같아진다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-d-binom-poisson',
        svg: svg({
            width: W, height: H,
            title: '이항분포의 푸아송 근사',
            desc: 'np 를 2 로 고정하고 n 을 8, 40, 400 으로 키우면 이항분포가 평균 2 인 푸아송 분포에 겹쳐진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8장 — 주요 연속분포
 * ================================================================== */

/* ---- 8-1. 연속균등분포 — 밀도와 누적분포 ---- */
add((() => {
    const W = 700, H = 330;
    const a = 2, b = 8, h = 1 / (b - a);
    const g = [panel(40, 42, 300, 214, '밀도함수 f(x)', { sub: '높이가 일정한 직사각형' })];
    const A = frame({ xRange: [0.4, 9.6], yRange: [0, 0.235], box: { x: 74, y: 92, w: 236, h: 116 } });
    g.push(poly(A, [[a, 0], [a, h], [b, h], [b, 0]], 'var(--s1)', 0.24));
    g.push(xaxis(A, [0.4, 9.6]));
    g.push(A.line([[0.4, 0], [a, 0]], { cls: 's2' }));
    g.push(A.line([[a, h], [b, h]], { cls: 's2' }));
    g.push(A.line([[b, 0], [9.5, 0]], { cls: 's2' }));
    g.push(ln([[A.X(a), A.Y(0)], [A.X(a), A.Y(h)]], { stroke: 'var(--s2)', sw: 1.4, dash: '4 3' }));
    g.push(ln([[A.X(b), A.Y(0)], [A.X(b), A.Y(h)]], { stroke: 'var(--s2)', sw: 1.4, dash: '4 3' }));
    g.push(txt(A.X(a), A.Y(0) + 16, 'a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(b), A.Y(0) + 16, 'b', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(a) - 6, A.Y(h) - 8, '1/(b − a)', { cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(5), A.Y(h / 2), '넓이 = 1', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(360, 42, 300, 214, '누적분포함수 F(x)', { sub: '0 에서 1 까지 곧게 올라간다' }));
    const B = frame({ xRange: [0.4, 9.6], yRange: [0, 1.16], box: { x: 400, y: 92, w: 232, h: 116 } });
    g.push(xaxis(B, [0.4, 9.6]));
    g.push(B.line([[0.4, 0], [a, 0], [b, 1], [9.5, 1]], { cls: 's3' }));
    g.push(ln([[B.X(0.4), B.Y(1)], [B.X(9.6), B.Y(1)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(txt(B.X(0.4) - 6, B.Y(1) + 4, '1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(a), B.Y(0) + 16, 'a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(b), B.Y(0) + 16, 'b', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(5) + 6, B.Y(0.42), '기울기 = 1/(b − a)', { cls: 'ink', size: 'sm' }));

    g.push(txt(350, 288, '어느 자리든 똑같이 그럴듯하다는 것을 식으로 적으면 밀도가 상수라는 말이 된다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 314, 'μ = (a + b)/2 로 한가운데,  σ² = (b − a)²/12 로 구간 길이의 제곱에 비례한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-uniform-cont',
        svg: svg({
            width: W, height: H,
            title: '연속균등분포의 밀도함수와 누적분포함수',
            desc: '구간 a 부터 b 까지 높이가 1/(b−a) 인 직사각형 밀도와, 그 넓이를 왼쪽부터 쌓아 만든 직선 모양의 누적분포함수',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-2. 지수분포와 무기억성 ---- */
add((() => {
    const W = 730, H = 360;
    const g = [panel(38, 42, 330, 232, '모수 λ 가 바꾸는 것', { sub: '평균 1/λ. λ 가 크면 빨리 죽는다' })];
    const A = frame({ xRange: [0, 6.2], yRange: [0, 1.62], box: { x: 74, y: 94, w: 272, h: 126 } });
    g.push(xaxis(A, [0, 6.2], [1, 2, 3, 4, 5]));
    const lams = [[1.5, 's2'], [1, 's1'], [0.5, 's3']];
    for (const [lam, cls] of lams) g.push(A.curve(x => expPdf(x, lam), { from: 0, to: 6.1, cls }));
    g.push(legend(238, 108, [
        { slot: 2, name: 'λ = 1.5  (평균 0.67)' },
        { slot: 1, name: 'λ = 1     (평균 1)' },
        { slot: 3, name: 'λ = 0.5  (평균 2)' },
    ]));
    g.push(txt(203, 258, '어느 곡선이든 가장 흔한 값은 0 근처다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(388, 42, 304, 232, '무기억성', { sub: '3시간을 버틴 뒤의 남은 수명' }));
    const B = frame({ xRange: [0, 6.2], yRange: [0, 1.15], box: { x: 424, y: 100, w: 244, h: 120 } });
    g.push(xaxis(B, [0, 6.2], [1, 2, 3, 4, 5]));
    g.push(B.curve(x => expPdf(x, 1), { from: 0, to: 6.1, cls: 's1' }));
    g.push(B.curve(x => expPdf(x - 3, 1), { from: 3, to: 6.1, cls: 's3', dash: '6 4' }));
    g.push(ln([[B.X(3), B.Y(0)], [B.X(3), B.Y(1.06)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
    g.push(txt(B.X(3) + 5, B.Y(1.08) + 2, '여기까지 버텼다', { cls: 'ink', size: 'sm' }));
    g.push(txt(B.X(3.4), B.Y(0.86), '점선 = 남은 수명의 분포', { cls: 'ink2', size: 'sm' }));
    g.push(txt(B.X(3.4), B.Y(0.72), '(원래 곡선을 옮긴 것과 같다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(540, 258, '앞으로 3시간을 더 버틸 확률도 처음과 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(365, 306, 'P(X > s + t | X > s) = P(X > t) — 이미 버틴 시간은 앞으로의 수명에 아무 정보도 주지 않는다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 332, '이 성질을 갖는 연속분포는 지수분포뿐이다. 그래서 마모가 있는 부품에는 맞지 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-exp-shapes',
        svg: svg({
            width: W, height: H,
            title: '지수분포의 모양과 무기억성',
            desc: '왼쪽은 λ 가 0.5, 1, 1.5 인 지수분포 밀도, 오른쪽은 3시간을 버틴 뒤의 남은 수명 분포가 처음 분포와 같다는 것을 보인다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-3. 푸아송 과정 — 개수는 푸아송, 간격은 지수, 합은 감마 ---- */
add((() => {
    const W = 740, H = 340;
    const t0 = 70, t1 = 660, base = 132;
    const T = 8;
    const at = v => t0 + ((t1 - t0) * v) / T;
    const evs = [0.7, 1.5, 2.2, 3.9, 4.5, 5.4, 7.2];
    const g = [];
    g.push(box(at(0), base - 58, at(3) - at(0), 58, { fill: 'var(--s1)', op: 0.12, stroke: 'none', rx: 0 }));
    g.push(ln([[t0 - 10, base], [t1 + 16, base]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(arw(t1, base, t1 + 22, base, { cls: 'ark', width: 1.6 }));
    g.push(txt(t1 + 28, base + 5, '시간', { cls: 'ink2', size: 'sm' }));
    for (const e of evs) {
        g.push(ln([[at(e), base - 16], [at(e), base + 16]], { stroke: 'var(--s2)', sw: 2.2 }));
        g.push(pdot(at(e), base - 16, 'var(--s2)', 3.4));
    }
    for (let k = 0; k <= T; k += 1) g.push(ln([[at(k), base], [at(k), base + 6]], { stroke: 'var(--ink2)', sw: 1 }));
    for (let k = 0; k <= T; k += 2) g.push(txt(at(k), base + 22, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[at(0), base - 58], [at(3), base - 58]], { stroke: 'var(--s1)', sw: 1.4, dash: '5 4' }));
    g.push(txt(at(1.5), base - 66, '구간 [0, 3] 안의 사건 수 = 3 → 푸아송 분포 (평균 3λ)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const bracket = (x1, x2, y, label, cls) => arw2(x1, y, x2, y, { cls, width: 1.4 })
        + (label ? txt((x1 + x2) / 2, y + 17, label, { anchor: 'middle', cls: 'ink', size: 'sm' }) : '');
    g.push(bracket(at(0), at(evs[0]), base + 46, '첫 사건까지', 's3'));
    g.push(bracket(at(evs[0]), at(evs[1]), base + 46, '다음까지', 's3'));
    g.push(bracket(at(evs[1]), at(evs[2]), base + 46, '그다음', 's3'));
    g.push(txt(at(4.6), base + 51, '간격 하나하나 → 지수분포 (평균 1/λ)', { cls: 'ink bold', size: 'sm' }));

    g.push(bracket(at(0), at(evs[2]), base + 94, '', 's2'));
    g.push(txt(52, base + 116, '세 번째 사건이 일어날 때까지의 시간 → 감마분포 (α = 3)', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[52, 272], [688, 272]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 296, '같은 하나의 그림을 세 가지로 읽은 것이다. 세는 것을 바꾸면 분포가 바뀐다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 322, '사건이 서로 독립이고 발생률 λ 가 일정하다는 가정이 셋 모두의 뿌리다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(52, 40, '푸아송 과정 — 시간 위에 흩어진 사건들', { cls: 'ink bold' }));
    return {
        name: 'st-d-poisson-process',
        svg: svg({
            width: W, height: H,
            title: '푸아송 과정에서 푸아송·지수·감마가 함께 나오는 자리',
            desc: '시간축 위의 사건들을 구간 안의 개수로 세면 푸아송, 사건 사이의 간격으로 보면 지수, 세 번째 사건까지의 시간으로 보면 감마분포가 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-4. 감마함수 ---- */
add((() => {
    const W = 740, H = 372;
    const g = [panel(38, 44, 372, 250, '감마함수 Γ(α)', { sub: '계승을 정수가 아닌 곳까지 늘린 함수' })];
    const A = frame({ xRange: [0, 4.35], yRange: [0, 7.4], box: { x: 78, y: 96, w: 310, h: 140 } });
    g.push(A.axes({ xTicks: [1, 2, 3, 4], yTicks: [2, 4, 6], grid: false }));
    g.push(A.curve(a => gammaFn(a), { from: 0.38, to: 4.12, cls: 's1', steps: 200 }));
    for (const [x, y] of [[1, 1], [2, 1], [3, 2], [4, 6]]) {
        g.push(ln([[A.X(x), A.Y(0)], [A.X(x), A.Y(y)]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(pdot(A.X(x), A.Y(y), 'var(--s2)', 4));
    }
    g.push(txt(A.X(4) - 8, A.Y(6) - 10, 'Γ(4) = 3! = 6', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(0.45), A.Y(5.2), '정수에서는 Γ(n) = (n − 1)!', { cls: 'ink', size: 'sm' }));
    g.push(pdot(A.X(0.5), A.Y(Math.sqrt(PI)), 'var(--s3)', 4.2));
    g.push(txt(A.X(0.62), A.Y(Math.sqrt(PI)) - 12, 'Γ(1/2) = √π ≈ 1.772', { cls: 'ink', size: 'sm' }));
    g.push(txt(224, 276, 'Γ(α + 1) = α Γ(α) — 부분적분 한 번으로 나오는 점화식', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(panel(430, 44, 268, 250, 'Γ(1/2) 가 √π 인 까닭', { sub: '고리로 잘라 더한다' }));
    const cx = 564, cy = 160;
    for (const r of [20, 36, 52, 68]) g.push(circ(cx, cy, r, { stroke: 'var(--grid)', sw: 1 }));
    g.push(`<path d="M${cx - 52} ${cy} A52 52 0 1 0 ${cx + 52} ${cy} A52 52 0 1 0 ${cx - 52} ${cy} M${cx - 36} ${cy} A36 36 0 1 1 ${cx + 36} ${cy} A36 36 0 1 1 ${cx - 36} ${cy} Z" fill="var(--s1)" fill-opacity="0.28" fill-rule="evenodd" stroke="none"/>`);
    g.push(arw2(cx, cy, cx + 44, cy, { cls: 's2', width: 1.3 }));
    g.push(txt(cx + 50, cy - 6, 'r', { cls: 'ink', size: 'sm' }));
    g.push(pdot(cx, cy, 'var(--ink2)', 2.6));
    g.push(txt(cx, cy + 96, '고리 넓이 = 2πr dr,  높이 = e^(−r²)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx, cy + 118, '더하면 ∫ 2πr e^(−r²) dr = π', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(370, 330, '평면 전체에서 e^(−x²−y²) 아래 부피가 π 이고, 그 부피는 ∫ e^(−x²) dx 의 제곱이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 356, '그래서 ∫ e^(−x²) dx = √π 이고, 치환하면 Γ(1/2) = √π 가 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-gamma-function',
        svg: svg({
            width: W, height: H,
            title: '감마함수와 Γ(1/2) 의 값',
            desc: '감마함수 곡선 위에서 정수 자리의 값이 계승과 같고, 평면을 고리로 잘라 더하면 가우스 적분의 값이 나온다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-5. 감마분포 — 모수 두 개가 하는 일 ---- */
add((() => {
    const W = 730, H = 356;
    const g = [panel(38, 44, 330, 224, '모양모수 α (θ = 1 고정)', { sub: 'α 가 커지면 봉우리가 생긴다' })];
    const A = frame({ xRange: [0, 9.6], yRange: [0, 1.05], box: { x: 74, y: 96, w: 272, h: 130 } });
    g.push(A.axes({ xTicks: [2, 4, 6, 8], yTicks: [0.5, 1], grid: false }));
    g.push(A.curve(x => Math.min(gammaPdf(x, 1, 1), 1.04), { from: 0.02, to: 9.5, cls: 's1' }));
    g.push(A.curve(x => gammaPdf(x, 2, 1), { from: 0.01, to: 9.5, cls: 's2' }));
    g.push(A.curve(x => gammaPdf(x, 5, 1), { from: 0.01, to: 9.5, cls: 's3' }));
    g.push(legend(232, 116, [
        { slot: 1, name: 'α = 1 (지수분포)' },
        { slot: 2, name: 'α = 2' },
        { slot: 3, name: 'α = 5' },
    ]));

    g.push(panel(388, 44, 304, 224, '척도모수 θ (α = 2 고정)', { sub: 'θ 는 가로축의 눈금만 늘린다' }));
    const B = frame({ xRange: [0, 12.4], yRange: [0, 0.78], box: { x: 424, y: 96, w: 246, h: 130 } });
    g.push(B.axes({ xTicks: [3, 6, 9, 12], yTicks: [0.25, 0.5, 0.75], grid: false }));
    g.push(B.curve(x => gammaPdf(x, 2, 0.6), { from: 0.01, to: 12.3, cls: 's1' }));
    g.push(B.curve(x => gammaPdf(x, 2, 1.2), { from: 0.01, to: 12.3, cls: 's2' }));
    g.push(B.curve(x => gammaPdf(x, 2, 2.4), { from: 0.01, to: 12.3, cls: 's3' }));
    g.push(legend(566, 116, [
        { slot: 1, name: 'θ = 0.6' },
        { slot: 2, name: 'θ = 1.2' },
        { slot: 3, name: 'θ = 2.4' },
    ]));

    g.push(txt(365, 300, 'α 는 ‘몇 번째 사건까지 기다리는가’ 이고, θ 는 ‘한 번 기다리는 데 걸리는 평균 시간’ 이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 326, 'μ = αθ,  σ² = αθ².  α = 1 이면 지수분포, α = k/2 이고 θ = 2 이면 카이제곱분포가 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-gamma-shapes',
        svg: svg({
            width: W, height: H,
            title: '감마분포에서 모양모수와 척도모수가 하는 일',
            desc: '모양모수 α 가 커지면 0 에서 내려가던 곡선에 봉우리가 생기고, 척도모수 θ 는 가로축을 늘려 분포를 옆으로 퍼뜨린다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-6. 베타분포 ---- */
add((() => {
    const W = 700, H = 340;
    const fr = frame({ xRange: [0, 1.02], yRange: [0, 3.15], box: { x: 74, y: 60, w: 400, h: 170 } });
    const g = [fr.axes({ xTicks: [0.25, 0.5, 0.75, 1], yTicks: [1, 2, 3], grid: false })];
    const cases = [
        { a: 2, b: 5, cls: 's2' },
        { a: 5, b: 2, cls: 's3' },
        { a: 0.6, b: 0.6, cls: 's1', dash: '6 4' },
    ];
    for (const c of cases) {
        g.push(fr.curve(x => Math.min(betaPdf(x, c.a, c.b), 3.1), { from: 0.006, to: 0.994, cls: c.cls, dash: c.dash, steps: 220 }));
    }
    g.push(ln([[fr.X(0), fr.Y(1)], [fr.X(1), fr.Y(1)]], { stroke: 'var(--s1)', sw: 2 }));
    g.push(legend(496, 88, [
        { slot: 1, name: 'α = 1, β = 1 (균등)' },
        { slot: 2, name: 'α = 2, β = 5' },
        { slot: 3, name: 'α = 5, β = 2' },
    ]));
    g.push(ln([[496, 142], [510, 142]], { stroke: 'var(--s1)', sw: 3, dash: '5 3' }));
    g.push(txt(516, 146, 'α = 0.6, β = 0.6', { cls: 'ink2', size: 'sm' }));
    g.push(txt(496, 182, 'μ = α/(α + β)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(496, 202, '값이 0 과 1 사이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(496, 220, '비율 자체를 다룬다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 284, '두 모수의 크기 비가 봉우리의 위치를, 합이 뾰족한 정도를 정한다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 310, '둘 다 1 보다 작으면 봉우리가 아니라 양 끝이 올라간 U 자가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-beta-shapes',
        svg: svg({
            width: W, height: H,
            title: '베타분포의 모수와 모양',
            desc: '0 과 1 사이에서만 값을 갖는 베타분포는 두 모수의 비로 봉우리 위치가, 합으로 뾰족한 정도가 정해진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-7. 정규분포 — 모수와 표준화 ---- */
add((() => {
    const W = 740, H = 366;
    const g = [panel(38, 44, 340, 240, '두 모수가 하는 일', { sub: 'μ 는 자리를, σ 는 폭을 정한다' })];
    const A = frame({ xRange: [-5, 11], yRange: [0, 0.46], box: { x: 74, y: 100, w: 282, h: 130 } });
    g.push(xaxis(A, [-5, 11], [-4, 0, 4, 8]));
    g.push(A.curve(x => normPdf(x, 0, 1), { from: -4.95, to: 10.9, cls: 's1' }));
    g.push(A.curve(x => normPdf(x, 3, 1), { from: -4.95, to: 10.9, cls: 's2' }));
    g.push(A.curve(x => normPdf(x, 3, 2.2), { from: -4.95, to: 10.9, cls: 's3' }));
    g.push(legend(244, 112, [
        { slot: 1, name: 'μ = 0, σ = 1' },
        { slot: 2, name: 'μ = 3, σ = 1' },
        { slot: 3, name: 'μ = 3, σ = 2.2' },
    ]));
    g.push(txt(208, 268, '어느 곡선이든 아래 넓이는 1 이다. 넓어지면 반드시 낮아진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(398, 44, 300, 240, '표준화', { sub: '눈금만 바꾸면 언제나 같은 곡선' }));
    const B = frame({ xRange: [40, 100], yRange: [0, 0.046], box: { x: 424, y: 104, w: 250, h: 58 } });
    g.push(underArea(B, x => normPdf(x, 70, 10), 85, 100, 'var(--s2)', 0.3));
    g.push(xaxis(B, [40, 100], [50, 70, 90]));
    g.push(B.curve(x => normPdf(x, 70, 10), { from: 40, to: 100, cls: 's1' }));
    g.push(txt(424, 96, 'X ∼ N(70, 10²)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(85) + 4, B.Y(0.012), 'x = 85', { cls: 'ink', size: 'sm' }));
    g.push(arw(548, 188, 548, 210, { cls: 'ark', width: 1.6 }));
    g.push(txt(556, 204, 'Z = (X − μ)/σ', { cls: 'ink', size: 'sm' }));
    const C = frame({ xRange: [-3, 3], yRange: [0, 0.46], box: { x: 424, y: 220, w: 250, h: 58 } });
    g.push(underArea(C, z => normPdf(z, 0, 1), 1.5, 3, 'var(--s2)', 0.3));
    g.push(xaxis(C, [-3, 3], [-2, 0, 2]));
    g.push(C.curve(z => normPdf(z, 0, 1), { from: -3, to: 3, cls: 's1' }));
    g.push(txt(424, 214, 'Z ∼ N(0, 1)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(C.X(1.5) + 4, C.Y(0.12), 'z = 1.5', { cls: 'ink', size: 'sm' }));

    g.push(txt(370, 316, '두 그림에서 색칠한 넓이는 정확히 같다. 그래서 표 하나로 모든 정규분포를 다룬다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 342, '변곡점은 언제나 μ ± σ 자리에 있다. 곡선을 보고 σ 를 읽어 낼 수 있다는 뜻이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-normal-shapes',
        svg: svg({
            width: W, height: H,
            title: '정규분포의 모수와 표준화',
            desc: '평균은 곡선의 자리를 표준편차는 폭을 정하고, 표준화하면 어떤 정규분포도 표준정규분포의 같은 넓이 문제로 바뀐다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-8. 카이제곱분포와 F 분포 ---- */
add((() => {
    const W = 740, H = 352;
    const g = [panel(38, 44, 340, 226, '카이제곱분포 χ²(k)', { sub: '표준정규를 k 개 제곱해 더한 것' })];
    const A = frame({ xRange: [0, 16.4], yRange: [0, 0.56], box: { x: 76, y: 96, w: 280, h: 124 } });
    g.push(A.axes({ xTicks: [4, 8, 12, 16], yTicks: [0.2, 0.4], grid: false }));
    g.push(A.curve(x => chisqPdf(x, 1), { from: 0.44, to: 16.3, cls: 's1', steps: 200 }));
    g.push(A.curve(x => chisqPdf(x, 2), { from: 0.01, to: 16.3, cls: 's2' }));
    g.push(A.curve(x => chisqPdf(x, 4), { from: 0.01, to: 16.3, cls: 's3' }));
    g.push(A.curve(x => chisqPdf(x, 8), { from: 0.01, to: 16.3, cls: 's1', dash: '6 4' }));
    g.push(legend(250, 112, [
        { slot: 1, name: 'k = 1' }, { slot: 2, name: 'k = 2' }, { slot: 3, name: 'k = 4' },
    ]));
    g.push(ln([[250, 164], [264, 164]], { stroke: 'var(--s1)', sw: 3, dash: '5 3' }));
    g.push(txt(270, 168, 'k = 8', { cls: 'ink2', size: 'sm' }));
    g.push(txt(208, 254, 'μ = k,  σ² = 2k', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(398, 44, 302, 226, 'F 분포 F(d~1, d~2)', { sub: '카이제곱 두 개를 자유도로 나눠 비를 낸 것' }));
    const B = frame({ xRange: [0, 4.1], yRange: [0, 1.05], box: { x: 434, y: 96, w: 246, h: 124 } });
    g.push(B.axes({ xTicks: [1, 2, 3, 4], yTicks: [0.5, 1], grid: false }));
    g.push(B.curve(x => Math.min(fPdf(x, 5, 5), 1.03), { from: 0.01, to: 4.05, cls: 's1', steps: 200 }));
    g.push(B.curve(x => fPdf(x, 10, 10), { from: 0.01, to: 4.05, cls: 's2', steps: 200 }));
    g.push(B.curve(x => fPdf(x, 20, 50), { from: 0.01, to: 4.05, cls: 's3', steps: 200 }));
    g.push(ln([[B.X(1), B.Y(0)], [B.X(1), B.Y(1.02)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 3' }));
    g.push(txt(B.X(1.35), B.Y(0.95), '1 근처가 중심', { cls: 'ink', size: 'sm' }));
    g.push(legend(578, 146, [
        { slot: 1, name: '(5, 5)' }, { slot: 2, name: '(10, 10)' }, { slot: 3, name: '(20, 50)' },
    ]));
    g.push(txt(549, 254, '두 분산이 같으면 비가 1 근처에 모인다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(370, 302, '둘 다 음수가 될 수 없다. 제곱합과 제곱합의 비이기 때문이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 328, '카이제곱은 자유도가 커지면 오른쪽으로 밀리며 대칭에 가까워진다. F 는 분산의 비를 다루는 자리에서 나온다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-chisq-f',
        svg: svg({
            width: W, height: H,
            title: '카이제곱분포와 F 분포의 모양',
            desc: '자유도에 따라 카이제곱분포는 오른쪽으로 밀리며 대칭에 가까워지고, F 분포는 1 근처를 중심으로 오른쪽 꼬리가 긴 모양이 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 8-9. t 분포와 표준정규 ---- */
add((() => {
    const W = 730, H = 352;
    const fr = frame({ xRange: [-4.4, 4.4], yRange: [0, 0.44], box: { x: 72, y: 60, w: 400, h: 170 } });
    const g = [fr.axes({ xTicks: [-4, -2, 0, 2, 4], yTicks: [0.1, 0.2, 0.3, 0.4], grid: false })];
    g.push(fr.curve(x => normPdf(x), { from: -4.35, to: 4.35, cls: 's1' }));
    g.push(fr.curve(x => tPdf(x, 10), { from: -4.35, to: 4.35, cls: 's3' }));
    g.push(fr.curve(x => tPdf(x, 3), { from: -4.35, to: 4.35, cls: 's2' }));
    g.push(fr.curve(x => tPdf(x, 1), { from: -4.35, to: 4.35, cls: 's2', dash: '6 4' }));
    g.push(legend(492, 84, [
        { slot: 1, name: '표준정규 N(0, 1)' },
        { slot: 3, name: 't 분포, 자유도 10' },
        { slot: 2, name: 't 분포, 자유도 3' },
    ]));
    g.push(ln([[492, 138], [506, 138]], { stroke: 'var(--s2)', sw: 3, dash: '5 3' }));
    g.push(txt(512, 142, 't 분포, 자유도 1', { cls: 'ink2', size: 'sm' }));

    const Z = frame({ xRange: [1.9, 4.2], yRange: [0, 0.075], box: { x: 512, y: 178, w: 180, h: 60 } });
    g.push(box(506, 168, 194, 80, { stroke: 'var(--grid)', sw: 1, rx: 5 }));
    g.push(Z.curve(x => normPdf(x), { from: 1.95, to: 4.15, cls: 's1' }));
    g.push(Z.curve(x => tPdf(x, 3), { from: 1.95, to: 4.15, cls: 's2' }));
    g.push(txt(599, 164, '오른쪽 꼬리를 확대한 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(365, 274, '가운데는 낮고 꼬리는 두껍다. 표준편차를 모르고 표본에서 추정해 쓴 대가다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 300, '자유도가 커지면 t 는 표준정규로 다가간다. 30 을 넘으면 눈으로는 구별하기 어렵다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 326, '자유도 1 인 t 분포는 평균조차 없다. 꼬리가 그만큼 무겁다는 뜻이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-t-vs-normal',
        svg: svg({
            width: W, height: H,
            title: 't 분포와 표준정규분포의 비교',
            desc: 't 분포는 표준정규보다 가운데가 낮고 꼬리가 두꺼우며, 자유도가 커질수록 표준정규에 가까워진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9장 — 표본분포와 극한정리
 * ================================================================== */

/** 표준정규 난수(박스-뮐러). 씨앗을 고정한 rng 를 받아 쓴다. */
function normalGen(rand) {
    let spare = null;
    return () => {
        if (spare !== null) { const v = spare; spare = null; return v; }
        let u = rand();
        const v = rand();
        if (u < 1e-12) u = 1e-12;
        const r = Math.sqrt(-2 * Math.log(u));
        spare = r * Math.sin(2 * PI * v);
        return r * Math.cos(2 * PI * v);
    };
}

/** 값 목록을 [lo, hi) 구간의 히스토그램(밀도)으로. [중심, 밀도] 배열을 준다. */
function hist(values, lo, hi, nbin) {
    const c = new Array(nbin).fill(0);
    for (const v of values) {
        const i = Math.floor(((v - lo) / (hi - lo)) * nbin);
        if (i >= 0 && i < nbin) c[i] += 1;
    }
    const w = (hi - lo) / nbin;
    return { pts: c.map((k, i) => [lo + (i + 0.5) * w, k / (values.length * w)]), w };
}

/** 커피 잔 수 모집단 — 9장에서 되풀이해 쓰는 치우친 이산 모집단. */
const POP_V = [0, 1, 2, 3];
const POP_P = [0.70, 0.15, 0.10, 0.05];
const POP_MU = POP_V.reduce((s, v, i) => s + v * POP_P[i], 0);
const POP_VAR = POP_V.reduce((s, v, i) => s + v * v * POP_P[i], 0) - POP_MU ** 2;

/** 크기 n 인 표본합의 정확한 확률분포를 합성곱으로 구한다. */
function sumDist(n) {
    let d = new Map([[0, 1]]);
    for (let i = 0; i < n; i += 1) {
        const nx = new Map();
        for (const [s, p] of d) {
            for (let j = 0; j < POP_V.length; j += 1) {
                const k = s + POP_V[j];
                nx.set(k, (nx.get(k) ?? 0) + p * POP_P[j]);
            }
        }
        d = nx;
    }
    return d;
}

/** 모집단에서 한 개 뽑기. */
function popDraw(rand) {
    const u = rand();
    let acc = 0;
    for (let i = 0; i < POP_V.length; i += 1) { acc += POP_P[i]; if (u < acc) return POP_V[i]; }
    return POP_V[POP_V.length - 1];
}

/* ---- 9-1. 표본분포가 만들어지는 과정 ---- */
add((() => {
    const W = 740, H = 386;
    const g = [];
    g.push(panel(36, 46, 208, 208, '① 모집단', { sub: '알고 싶지만 다 볼 수 없다' }));
    const A = frame({ xRange: [-0.6, 3.6], yRange: [0, 0.82], box: { x: 70, y: 98, w: 148, h: 104 } });
    g.push(bars(A, POP_V.map((v, i) => [v, POP_P[i]]), { col: 'var(--s1)', op: 0.32 }));
    g.push(xaxis(A, [-0.6, 3.6], POP_V));
    g.push(txt(140, 240, `μ = ${POP_MU},  σ = ${r2(Math.sqrt(POP_VAR))}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(266, 46, 200, 208, '② 표본을 뽑는다', { sub: '크기 5 짜리로 몇 번' }));
    const rand = rng(11235813);
    for (let s = 0; s < 4; s += 1) {
        const sample = Array.from({ length: 5 }, () => popDraw(rand));
        const mean = sample.reduce((a, b) => a + b, 0) / 5;
        const y = 106 + s * 34;
        g.push(txt(282, y, sample.join('  '), { cls: 'ink' }));
        g.push(arw(370, y - 5, 392, y - 5, { cls: 'ark', width: 1.4 }));
        g.push(txt(398, y, `x̄ = ${mean.toFixed(1)}`, { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(366, 244, '표본마다 x̄ 가 다르다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(panel(488, 46, 216, 208, '③ 표본평균의 분포', { sub: '이것이 표본분포다' }));
    const pts = [...sumDist(5).entries()].map(([s, p]) => [s / 5, p * 5]).filter(q => q[0] <= 2.4);
    const top = Math.max(...pts.map(q => q[1]));
    const C = frame({ xRange: [-0.12, 2.42], yRange: [0, top * 1.18], box: { x: 512, y: 98, w: 172, h: 104 } });
    g.push(bars(C, pts, { col: 'var(--s3)', op: 0.34, step: 0.2, wfrac: 0.8 }));
    g.push(xaxis(C, [-0.12, 2.42], [0, 1, 2]));
    g.push(ln([[C.X(POP_MU), C.Y(0)], [C.X(POP_MU), C.Y(top * 1.1)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
    g.push(txt(C.X(POP_MU) + 5, C.Y(top * 1.1) + 8, 'μ', { cls: 'ink', size: 'sm' }));
    g.push(txt(596, 240, '중심은 μ, 폭은 σ/√5', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(arw(248, 150, 262, 150, { cls: 'ark', width: 1.8 }));
    g.push(arw(470, 150, 484, 150, { cls: 'ark', width: 1.8 }));
    g.push(txt(370, 296, '통계량은 표본에 따라 달라지므로 그 자체가 확률변수다. 그 분포를 표본분포라 한다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 322, '③ 은 모집단의 분포가 아니다. ‘표본평균이라는 새 확률변수’ 의 분포다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 348, '모집단은 0 이 가장 흔한 치우친 모양인데 x̄ 의 분포는 벌써 가운데가 두툼해졌다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 374, '실제 조사에서 우리는 ② 의 표본 하나만 본다. ③ 은 계산으로만 아는 것이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-sampling-dist',
        svg: svg({
            width: W, height: H,
            title: '모집단에서 표본으로, 표본에서 표본분포로',
            desc: '치우친 모집단에서 크기 5 인 표본을 여러 번 뽑으면 표본평균이 표본마다 다르고, 그 값들이 이루는 분포가 표본분포다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-2. 표준오차는 n 의 제곱근으로 줄어든다 ---- */
add((() => {
    const W = 730, H = 346;
    const sd = 1;
    const fr = frame({ xRange: [-2.6, 2.6], yRange: [0, 3.5], box: { x: 70, y: 58, w: 384, h: 176 } });
    const g = [xaxis(fr, [-2.6, 2.6], [-2, -1, 0, 1, 2])];
    const ns = [[1, 's1', undefined], [4, 's2', undefined], [16, 's3', undefined], [64, 's1', '6 4']];
    for (const [nn, cls, dash] of ns) {
        g.push(fr.curve(x => normPdf(x, 0, sd / Math.sqrt(nn)), { from: -2.55, to: 2.55, cls, dash, steps: 260 }));
    }
    g.push(legend(474, 86, [
        { slot: 1, name: 'n = 1   (표준오차 1)' },
        { slot: 2, name: 'n = 4   (0.5)' },
        { slot: 3, name: 'n = 16 (0.25)' },
    ]));
    g.push(ln([[474, 140], [488, 140]], { stroke: 'var(--s1)', sw: 3, dash: '5 3' }));
    g.push(txt(494, 144, 'n = 64 (0.125)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 176, '가로축은 x̄ − μ', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 196, '네 곡선의 넓이는 모두 1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 216, '좁아지면 반드시 높아진다', { cls: 'ink2', size: 'sm' }));

    const S = frame({ xRange: [0, 68], yRange: [0, 1.1], box: { x: 542, y: 252, w: 150, h: 60 } });
    g.push(box(534, 242, 168, 82, { stroke: 'var(--grid)', sw: 1, rx: 5 }));
    g.push(S.curve(x => 1 / Math.sqrt(Math.max(x, 0.6)), { from: 1, to: 67, cls: 's2' }));
    g.push(S.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(618, 238, '표준오차 대 n', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(262, 276, '표본을 4배로 늘려야 폭이 절반이 된다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(262, 302, '정밀도가 n 이 아니라 √n 으로 좋아진다는 것이 조사 비용을 지배한다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(262, 328, '표준오차 σ/√n 은 표본평균의 표준편차다. 자료의 흩어짐 σ 와 혼동하면 안 된다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-se-shrink',
        svg: svg({
            width: W, height: H,
            title: '표본 크기와 표준오차',
            desc: '표본평균의 분포는 표본 크기가 커질수록 좁아지는데, 폭이 절반이 되려면 표본을 네 배로 늘려야 한다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-3. 큰 수의 법칙과 체비쇼프 울타리 ---- */
add((() => {
    const W = 730, H = 336;
    const N = 400, mu = POP_MU, sd = Math.sqrt(POP_VAR);
    const fr = frame({ xRange: [1, N], yRange: [0, 1.45], box: { x: 72, y: 54, w: 466, h: 196 } });
    const g = [fr.axes({ xLabel: 'n', xTicks: [100, 200, 300, 400], yTicks: [0.5, 1], grid: false })];
    const clamp = v => Math.min(Math.max(v, 0.015), 1.43);
    const band = [];
    for (let i = 1; i <= N; i += 1) band.push([i, clamp(mu + (2 * sd) / Math.sqrt(i))]);
    for (let i = N; i >= 1; i -= 1) band.push([i, clamp(mu - (2 * sd) / Math.sqrt(i))]);
    g.push(poly(fr, band, 'var(--s3)', 0.16));
    const seeds = [20260101, 555013, 31415926];
    const cls = ['s1', 's2', 's3'];
    seeds.forEach((sd0, idx) => {
        const rand = rng(sd0);
        const pts = [];
        let acc = 0;
        for (let i = 1; i <= N; i += 1) {
            acc += popDraw(rand);
            pts.push([i, acc / i]);
        }
        g.push(fr.line(pts, { cls: cls[idx] }));
    });
    g.push(ln([[fr.X(1), fr.Y(mu)], [fr.X(N) + 8, fr.Y(mu)]], { stroke: 'var(--ink2)', sw: 1.5, dash: '6 4' }));
    g.push(txt(fr.X(N) + 14, fr.Y(mu) - 8, 'μ', { cls: 'ink' }));
    g.push(txt(578, 86, '표본을 세 번 따로 뽑아', { cls: 'ink', size: 'sm' }));
    g.push(txt(578, 104, '본 것이다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(578, 136, '옅은 띠는 μ ± 2σ/√n.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(578, 154, '체비쇼프에 따르면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(578, 172, '이 밖으로 나가는 일은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(578, 190, '많아야 1/4 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(578, 220, '띠는 √n 으로 좁아진다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 286, '표본평균이 μ 로 모여든다는 것이 큰 수의 법칙이다. 어느 순간에 정확히 μ 가 된다는 뜻이 아니다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 312, '체비쇼프 부등식이 그 수렴 속도에 눈에 보이는 울타리를 쳐 준다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-lln',
        svg: svg({
            width: W, height: H,
            title: '큰 수의 법칙 — 표본평균이 모평균으로 모여든다',
            desc: '치우친 모집단에서 표본을 늘려 갈 때 표본평균의 자취 세 개가 모평균 주위로 좁혀지고, 체비쇼프 부등식이 그 폭에 울타리를 친다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-4. 중심극한정리 모의실험 — 이 문서의 분기점 ---- */
add((() => {
    const W = 740, H = 440;
    const mu = POP_MU, sd = Math.sqrt(POP_VAR);
    const g = [];
    const mk = (bx, by, n) => {
        const se = sd / Math.sqrt(n);
        const lo = mu - 3.4 * se, hi = mu + 3.4 * se;
        const pts = [...sumDist(n).entries()]
            .map(([s, p]) => [s / n, p * n])
            .filter(q => q[0] >= lo - 1 / n && q[0] <= hi + 1 / n);
        const top = Math.max(...pts.map(q => q[1]), normPdf(mu, mu, se));
        const fr = frame({ xRange: [lo, hi], yRange: [0, top * 1.16], box: { x: bx, y: by, w: 268, h: 112 } });
        const out = [bars(fr, pts, { col: 'var(--s1)', op: 0.3, step: 1 / n, wfrac: 0.88, sw: 0.7 })];
        out.push(xaxis(fr, [lo, hi], [mu], { fmt: () => '0.5' }));
        out.push(fr.curve(x => normPdf(x, mu, se), { from: lo, to: hi, cls: 's2', steps: 220 }));
        out.push(txt(bx + 2, by - 9, `n = ${n}`, { cls: 'ink bold' }));
        out.push(txt(bx + 266, by - 9, `표준오차 ${se.toFixed(3)}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, 88, 1));
    g.push(mk(400, 88, 2));
    g.push(mk(56, 250, 5));
    g.push(mk(400, 250, 30));
    g.push(txt(370, 34, '커피 잔 수 모집단(0잔 70%, 1잔 15%, 2잔 10%, 3잔 5%)에서 뽑은 표본평균의 분포',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 56, '막대는 정확히 계산한 확률, 곡선은 같은 평균과 표준오차를 갖는 정규분포',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 400, 'n = 1 은 모집단 그 자체라 한쪽으로 완전히 쏠려 있다. n 이 커지면 곡선과 막대가 겹쳐 간다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 426, '가로 눈금이 패널마다 다르다는 데 주의하라. 실제 폭은 √n 배로 좁아지고 있다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-clt-sim',
        svg: svg({
            width: W, height: H,
            title: '중심극한정리 — 치우친 모집단에서도 표본평균은 종 모양이 된다',
            desc: '한쪽으로 쏠린 이산 모집단에서 표본 크기를 1, 2, 5, 30 으로 늘리며 표본평균의 정확한 분포를 정규곡선과 겹쳐 보인 것',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-5. 모집단이 무엇이든 같은 일이 일어난다 ---- */
add((() => {
    const W = 740, H = 408;
    const REP = 16000, n = 30;
    const rand = rng(777123);
    const pops = [
        {
            name: '균등분포', sub: '0 과 1 사이 아무 값', mu: 0.5, sd: Math.sqrt(1 / 12), nbin: 26,
            draw: () => rand(),
            shape: fr => fr.line([[0, 0], [0, 1], [1, 1], [1, 0]], { cls: 's1' }),
            xr: [-0.15, 1.15], yr: [0, 1.35],
        },
        {
            name: '지수분포', sub: '한쪽으로 크게 쏠림', mu: 1, sd: 1, nbin: 26,
            draw: () => -Math.log(Math.max(rand(), 1e-12)),
            shape: fr => fr.curve(x => expPdf(x, 1), { from: 0, to: 4, cls: 's1' }),
            xr: [-0.3, 4], yr: [0, 1.15],
        },
        {
            name: '값이 둘뿐', sub: '0 아니면 1', mu: 0.5, sd: 0.5, nbin: 21,
            lo: 4.5 / 30, hi: 25.5 / 30,
            draw: () => (rand() < 0.5 ? 0 : 1),
            shape: fr => bars(fr, [[0, 0.5], [1, 0.5]], { col: 'var(--s1)', op: 0.32, wfrac: 0.3 }),
            xr: [-0.4, 1.4], yr: [0, 0.72],
        },
    ];
    const g = [];
    pops.forEach((pp, i) => {
        const bx = 44 + i * 232;
        g.push(panel(bx, 60, 200, 300, pp.name, { sub: pp.sub }));
        const T = frame({ xRange: pp.xr, yRange: pp.yr, box: { x: bx + 24, y: 104, w: 152, h: 58 } });
        g.push(xaxis(T, pp.xr));
        g.push(pp.shape(T));
        g.push(txt(bx + 100, 182, '모집단', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(arw(bx + 100, 190, bx + 100, 208, { cls: 'ark', width: 1.5 }));

        const means = [];
        for (let r = 0; r < REP; r += 1) {
            let s = 0;
            for (let k = 0; k < n; k += 1) s += pp.draw();
            means.push(s / n);
        }
        const se = pp.sd / Math.sqrt(n);
        const lo = pp.lo ?? pp.mu - 3.6 * se;
        const hi = pp.hi ?? pp.mu + 3.6 * se;
        const hh = hist(means, lo, hi, pp.nbin);
        const top = Math.max(...hh.pts.map(q => q[1]), normPdf(pp.mu, pp.mu, se)) * 1.16;
        const B = frame({ xRange: [lo, hi], yRange: [0, top], box: { x: bx + 22, y: 220, w: 156, h: 106 } });
        g.push(bars(B, hh.pts, { col: 'var(--s3)', op: 0.32, step: hh.w, wfrac: 0.94, sw: 0.5 }));
        g.push(xaxis(B, [lo, hi]));
        g.push(B.curve(x => normPdf(x, pp.mu, se), { from: lo, to: hi, cls: 's2', steps: 180 }));
        g.push(txt(bx + 100, 350, `표본평균 (n = ${n})`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(370, 36, '모집단이 무엇이든 크기 30 인 표본의 평균은 정규분포에 가깝다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 382, '막대는 16000번 되풀이한 모의실험, 곡선은 N(μ, σ²/30) 이다. 셋 다 잘 맞는다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 404, '쏠림이 심한 지수분포는 30 에서도 오른쪽이 아주 조금 길다. n 이 얼마나 필요한지는 모집단이 정한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-clt-populations',
        svg: svg({
            width: W, height: H,
            title: '서로 다른 세 모집단에서 확인하는 중심극한정리',
            desc: '균등분포, 지수분포, 값이 둘뿐인 분포에서 각각 크기 30 인 표본을 여러 번 뽑아 만든 표본평균의 분포가 모두 정규곡선에 겹친다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-6. 표본분산과 카이제곱분포 ---- */
add((() => {
    const W = 730, H = 344;
    const REP = 14000;
    const g = [];
    const mk = (bx, n) => {
        const rand = rng(4242 + n * 97);
        const nrm = normalGen(rand);
        const vals = [];
        for (let r = 0; r < REP; r += 1) {
            const xs = Array.from({ length: n }, nrm);
            const m = xs.reduce((a, b) => a + b, 0) / n;
            vals.push(xs.reduce((a, b) => a + (b - m) ** 2, 0));
        }
        const hi = n === 5 ? 18 : 36;
        const hh = hist(vals, 0, hi, 30);
        const top = Math.max(...hh.pts.map(q => q[1]), chisqPdf(Math.max(n - 3, 0.5), n - 1)) * 1.2;
        const fr = frame({ xRange: [0, hi], yRange: [0, top], box: { x: bx, y: 92, w: 268, h: 122 } });
        const out = [bars(fr, hh.pts, { col: 'var(--s1)', op: 0.3, step: hh.w, wfrac: 0.94, sw: 0.5 })];
        out.push(xaxis(fr, [0, hi], n === 5 ? [4, 8, 12, 16] : [8, 16, 24, 32]));
        out.push(fr.curve(x => chisqPdf(x, n - 1), { from: 0.05, to: hi, cls: 's2', steps: 220 }));
        out.push(txt(bx + 134, 74, `n = ${n} → 자유도 ${n - 1}`, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 134, 254, `막대의 평균 ${(vals.reduce((a, b) => a + b, 0) / REP).toFixed(2)}  (이론값 ${n - 1})`,
            { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, 5));
    g.push(mk(400, 15));
    g.push(txt(365, 36, '정규모집단에서 (n − 1)S²/σ² 를 여러 번 계산해 쌓은 것', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 54, '곡선은 자유도 n − 1 인 카이제곱분포', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 288, '자유도가 n 이 아니라 n − 1 이다. 편차 n 개의 합이 0 이라 하나는 나머지가 정해 버린다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 314, '분포가 오른쪽으로 길다는 것은 표본분산이 가끔 크게 튄다는 뜻이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-s2-chisq',
        svg: svg({
            width: W, height: H,
            title: '표본분산을 표준화하면 카이제곱분포가 된다',
            desc: '정규모집단에서 크기 5 와 15 인 표본의 편차제곱합을 모분산으로 나눈 값이 자유도 n−1 인 카이제곱분포를 따른다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-7. t 통계량이 태어나는 자리 ---- */
add((() => {
    const W = 730, H = 372;
    const REP = 20000, n = 5;
    const rand = rng(90210);
    const nrm = normalGen(rand);
    const zs = [], ts = [];
    for (let r = 0; r < REP; r += 1) {
        const xs = Array.from({ length: n }, nrm);
        const m = xs.reduce((a, b) => a + b, 0) / n;
        const s = Math.sqrt(xs.reduce((a, b) => a + (b - m) ** 2, 0) / (n - 1));
        zs.push(m * Math.sqrt(n));
        ts.push((m * Math.sqrt(n)) / s);
    }
    const g = [];
    const mk = (bx, vals, title, sub, withT) => {
        const hh = hist(vals, -5, 5, 40);
        const fr = frame({ xRange: [-5, 5], yRange: [0, 0.47], box: { x: bx, y: 88, w: 268, h: 118 } });
        const out = [bars(fr, hh.pts, { col: 'var(--s1)', op: 0.28, step: hh.w, wfrac: 0.94, sw: 0.4 })];
        out.push(xaxis(fr, [-5, 5], [-4, -2, 0, 2, 4]));
        out.push(fr.curve(x => normPdf(x), { from: -5, to: 5, cls: 's2', dash: withT ? '6 4' : undefined }));
        if (withT) out.push(fr.curve(x => tPdf(x, n - 1), { from: -5, to: 5, cls: 's3' }));
        out.push(txt(bx + 134, 70, title, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 134, 244, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const tail = vals.filter(v => Math.abs(v) > 2.776).length / REP;
        out.push(txt(bx + 134, 264, `2.776 보다 멀리 간 비율 ${(tail * 100).toFixed(1)}%`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, zs, 'σ 를 알고 쓸 때', '분모가 상수 σ/√n 이다', false));
    g.push(mk(400, ts, 'σ 대신 S 를 쓸 때', '분모도 표본마다 흔들린다', true));
    g.push(txt(365, 36, '크기 5 인 정규표본에서 두 통계량을 2만 번씩 계산했다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[56, 284], [674, 284]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(legend(290, 306, [{ slot: 2, name: '표준정규' }, { slot: 3, name: 't 분포, 자유도 4' }]));
    g.push(txt(365, 356, '분모가 흔들리면 꼬리가 두꺼워진다. 그래서 σ 를 모를 때는 t 분포를 써야 한다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-d-t-birth',
        svg: svg({
            width: W, height: H,
            title: '모표준편차를 표본에서 추정해 쓰면 t 분포가 나온다',
            desc: '분모에 참값 σ 를 쓴 통계량은 표준정규를 따르지만 표본표준편차 S 를 쓰면 꼬리가 두꺼워져 자유도 4 인 t 분포를 따른다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-8. 표본비율의 분포 ---- */
add((() => {
    const W = 730, H = 356;
    const p = 0.3;
    const g = [];
    const mk = (bx, n, xr, ticks) => {
        const ks = [];
        for (let k = 0; k <= n; k += 1) {
            const ph = k / n;
            if (ph >= xr[0] && ph <= xr[1]) ks.push([ph, binomPmf(k, n, p) * n]);
        }
        const se = Math.sqrt((p * (1 - p)) / n);
        const top = Math.max(...ks.map(q => q[1]), normPdf(p, p, se)) * 1.16;
        const fr = frame({ xRange: xr, yRange: [0, top], box: { x: bx, y: 92, w: 268, h: 118 } });
        const out = [bars(fr, ks, { col: 'var(--s1)', op: 0.3, step: 1 / n, wfrac: 0.9, sw: 0.6 })];
        out.push(xaxis(fr, xr, ticks));
        out.push(fr.curve(x => normPdf(x, p, se), { from: xr[0], to: xr[1], cls: 's2', steps: 220 }));
        out.push(txt(bx + 134, 70, `n = ${n}`, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(bx + 134, 250, `np = ${r2(n * p)},  n(1 − p) = ${r2(n * (1 - p))}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        out.push(txt(bx + 134, 270, `표준오차 ${se.toFixed(3)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(56, 10, [-0.05, 0.75], [0, 0.3, 0.6]));
    g.push(mk(400, 100, [0.15, 0.47], [0.2, 0.3, 0.4]));
    g.push(txt(365, 36, '참비율이 0.3 일 때 표본비율의 분포', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 54, '막대는 정확한 이항 확률, 곡선은 정규근사', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 306, 'n = 10 이면 막대가 성기고 곡선이 왼쪽 끝에서 음수 쪽까지 흘러 넘친다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 332, 'np 와 n(1 − p) 가 둘 다 10 을 넘으면 근사를 믿을 만하다는 기준이 여기서 나온다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-phat',
        svg: svg({
            width: W, height: H,
            title: '표본비율의 분포와 정규근사',
            desc: '참비율 0.3 에서 표본 크기 10 과 100 일 때 표본비율의 정확한 분포를 정규곡선과 비교한다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 9-9. 유한모집단 수정 ---- */
add((() => {
    const W = 700, H = 330;
    const fr = frame({ xRange: [0, 1.02], yRange: [0, 1.1], box: { x: 76, y: 58, w: 380, h: 176 } });
    const g = [fr.axes({ xLabel: 'n/N', xTicks: [0.2, 0.4, 0.6, 0.8, 1], yTicks: [0.5, 1], grid: false })];
    g.push(poly(fr, [[0, 0], [0.05, 0], [0.05, 1.1], [0, 1.1]], 'var(--s3)', 0.2));
    g.push(fr.curve(f => Math.sqrt(Math.max(1 - f, 0)), { from: 0, to: 1, cls: 's1', steps: 240 }));
    g.push(ln([[fr.X(0), fr.Y(1)], [fr.X(1.02), fr.Y(1)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    for (const f of [0.5, 1]) g.push(pdot(fr.X(f), fr.Y(Math.sqrt(1 - f)), 'var(--s2)', 4));
    g.push(txt(fr.X(0.12), fr.Y(0.44), 'n/N 이 0.05 이하이면 수정계수가', { cls: 'ink', size: 'sm' }));
    g.push(txt(fr.X(0.12), fr.Y(0.33), '0.975 보다 커서 무시해도 된다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(fr.X(0.12), fr.Y(0.22), '왼쪽 옅은 띠가 그 구간이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(0.5) + 8, fr.Y(Math.sqrt(0.5)) + 4, '절반을 조사하면 0.707 배', { cls: 'ink', size: 'sm' }));
    g.push(txt(fr.X(1) - 6, fr.Y(0) - 12, '전수조사면 0', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(76, 44, '표준오차에 곱해지는 수정계수 √((N − n)/(N − 1))', { cls: 'ink2', size: 'sm' }));

    g.push(box(486, 66, 186, 152, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
    g.push(txt(579, 90, '왜 줄어드는가', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(500, 118, '모집단을 다 조사하면', { cls: 'ink', size: 'sm' }));
    g.push(txt(500, 138, '표본평균이 곧 모평균이라', { cls: 'ink', size: 'sm' }));
    g.push(txt(500, 158, '흔들릴 여지가 없다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(500, 186, '비복원으로 뽑을 때만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(500, 204, '쓰는 보정이다.', { cls: 'ink2', size: 'sm' }));

    g.push(txt(350, 274, '큰 모집단에서는 표본이 몇 명인지가 중요하지 모집단이 몇 명인지는 거의 중요하지 않다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 302, '인구 5천만에서 1000명을 뽑든 인구 50만에서 1000명을 뽑든 정밀도는 사실상 같다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-d-fpc',
        svg: svg({
            width: W, height: H,
            title: '유한모집단 수정계수',
            desc: '표본 비율 n/N 이 커질수록 표준오차에 곱하는 수정계수가 작아지고, n/N 이 0.05 이하면 1 에 가까워 무시할 수 있다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
