/**
 * 통계학 15장(비모수 방법과 재표집) 그림. 이름은 전부 `sta-np-` 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~0`·`R~{A}` 표기, 나머지는 유니코드(θ̂ ν π ≈ ≤ ≥ − ×)로 적는다.
 * 라벨에 물결표를 그냥 쓰면 아래첨자로 먹히고, 따옴표는 ‘ ’ 만 쓴다.
 * lib 의 px() 에 cls:'ark' 를 주면 선이 사라지므로 화살표는 아래 arw() 를 쓴다.
 *
 * 그림 속 숫자(순위합 분포, 효율, 재배열 개수, 부트스트랩 확률)는 전부
 * 이 파일이 그 자리에서 계산한다. 난수는 쓰지 않는다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const PI = Math.PI;

/* ------------------------------------------------------------------ *
 * 공통 소도구 — 화소 좌표 도형
 * ------------------------------------------------------------------ */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' };

function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.6, dash } = {}) {
    const mk = { s1: 'ar1', s2: 'ar2', s3: 'ar3' }[cls] ?? 'ark';
    return `<path fill="none" stroke="${COL[cls] ?? 'var(--ink2)'}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

const neg = v => String(v).replace('-', '−');

/** 가로축만. 밀도·막대 그림에서 세로축이 한가운데 서지 않게 한다. */
function xaxis(fr, [x0, x1], ticks = [], { fmt = neg, y0 = 0 } = {}) {
    const y = fr.Y(y0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { sw: 1 }));
        out.push(txt(fr.X(t), y + 18, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 왼쪽 가장자리에 세우는 세로축. */
function yaxis(fr, [y0, y1], x0, ticks = [], { fmt = neg } = {}) {
    const x = fr.X(x0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${x} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[x - 5, fr.Y(t)], [x, fr.Y(t)]], { sw: 1 }));
        out.push(txt(x - 8, fr.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 데이터 좌표 막대 하나. */
function bar(fr, x, h, halfW, { col = 'var(--s1)', op = 0.35 } = {}) {
    return box(fr.X(x) - halfW, fr.Y(h), halfW * 2, fr.Y(0) - fr.Y(h), { fill: col, op, stroke: col, sw: 1, rx: 1.5 });
}

/* ------------------------------------------------------------------ *
 * 계산 소도구
 * ------------------------------------------------------------------ */

/** 크기 k 인 부분집합 전부. */
function combos(arr, k) {
    const out = [];
    const rec = (start, cur) => {
        if (cur.length === k) { out.push(cur.slice()); return; }
        for (let i = start; i < arr.length; i += 1) { cur.push(arr[i]); rec(i + 1, cur); cur.pop(); }
    };
    rec(0, []);
    return out;
}

const LANCZOS = [
    676.5203681218851, -1259.1392167224028, 771.32342877765313,
    -176.61502916214059, 12.507343278686905, -0.13857109526572012,
    9.9843695780195716e-6, 1.5056327351493116e-7,
];
function lgamma(z) {
    if (z < 0.5) return Math.log(PI / Math.sin(PI * z)) - lgamma(1 - z);
    const w = z - 1;
    let x = 0.99999999999980993;
    for (let i = 0; i < 8; i += 1) x += LANCZOS[i] / (w + i + 1);
    const t = w + 7.5;
    return 0.5 * Math.log(2 * PI) + (w + 0.5) * Math.log(t) - t + Math.log(x);
}

/** 자유도 ν 인 t 분포 밀도. */
const tPdf = (x, v) => Math.exp(lgamma((v + 1) / 2) - lgamma(v / 2) - 0.5 * Math.log(v * PI)
    - ((v + 1) / 2) * Math.log(1 + (x * x) / v));

/** 심프슨 적분. */
function simpson(f, a, b, n = 4000) {
    const h = (b - a) / n;
    let s = f(a) + f(b);
    for (let i = 1; i < n; i += 1) s += (i % 2 ? 4 : 2) * f(a + i * h);
    return (s * h) / 3;
}

/**
 * 위치 이동 대립가설에서 t 검정에 대한 점근상대효율.
 * 윌콕슨: 12 σ² (∫f²)², 부호검정: 4 σ² f(0)². 둘 다 밀도 f 가 0 에 대칭일 때의 공식.
 */
function areT(v) {
    const varT = v / (v - 2);
    // 꼬리가 두꺼우므로 범위를 넓게 잡는다. ∫f² 는 꼬리 기여가 작아 ±400 이면 충분하다.
    const i2 = simpson(x => tPdf(x, v) ** 2, -400, 400, 80000);
    return { wil: 12 * varT * i2 * i2, sign: 4 * varT * tPdf(0, v) ** 2 };
}

/* ================================================================== *
 * 1. 값을 순위로 — 이상점 하나는 끝 순위 하나가 된다
 * ================================================================== */
add((() => {
    const W = 740, H = 330;
    const vals = [14, 17, 19, 21, 22, 25, 28, 96];
    const n = vals.length;
    const mean = vals.reduce((a, b) => a + b, 0) / n;
    const sorted = vals.slice().sort((a, b) => a - b);
    const med = (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    const g = [txt(W / 2, 26, '값을 순위로 바꾸면 이상점 하나는 끝 순위 하나가 될 뿐이다', { anchor: 'middle', cls: 'ink bold' })];

    // 위: 값의 축
    const A = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: 70, y: 50, w: 600, h: 60 } });
    g.push(txt(60, A.Y(0) + 4, '값', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(xaxis(A, [0, 100], [0, 20, 40, 60, 80, 100]));
    // 아래: 순위 축
    const B = frame({ xRange: [0.5, 8.5], yRange: [0, 1], box: { x: 70, y: 186, w: 600, h: 40 } });
    g.push(txt(60, B.Y(0) + 4, '순위', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(xaxis(B, [0.5, 8.5], [1, 2, 3, 4, 5, 6, 7, 8]));

    vals.forEach((v, i) => {
        const rank = sorted.indexOf(v) + 1;
        const col = v === 96 ? 'var(--s2)' : 'var(--s1)';
        g.push(ln([[A.X(v), A.Y(0) - 6], [B.X(rank), B.Y(0) - 6]], { stroke: 'var(--grid)', sw: 1.2 }));
        g.push(pdot(A.X(v), A.Y(0) - 6, col, 4.5));
        g.push(pdot(B.X(rank), B.Y(0) - 6, col, 4.5));
    });
    // 평균과 중앙값 표시(값 축)
    g.push(ln([[A.X(mean), A.Y(0) - 40], [A.X(mean), A.Y(0)]], { stroke: 'var(--s2)', sw: 1.4, dash: '4 3' }));
    g.push(txt(A.X(mean) + 4, A.Y(0) - 44, `평균 ${mean}`, { cls: 'ink', size: 'sm' }));
    g.push(ln([[A.X(med), A.Y(0) - 26], [A.X(med), A.Y(0)]], { stroke: 'var(--s1)', sw: 1.4, dash: '4 3' }));
    g.push(txt(A.X(med) - 4, A.Y(0) - 30, `중앙값 ${med}`, { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(96), A.Y(0) - 18, '96', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(8), B.Y(0) - 18, '8', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[46, 250], [W - 46, 250]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 274, `96 이 30 이었어도, 960 이었어도 순위는 8 이다. 값 축에서는 이 한 점 때문에 평균이 중앙값보다 ${r2(mean - med)} 위에 있다`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 296, '순위 축에서는 간격이 모두 1 이므로 어느 점도 남보다 더 큰 목소리를 내지 못한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-np-ranks',
        svg: svg({
            width: W, height: H,
            title: '값을 순위로 바꾸기 — 이상점의 영향이 끝 순위 하나로 줄어든다',
            desc: '여덟 값 가운데 96 하나가 멀리 떨어져 있다. 순위 축으로 옮기면 그 점은 그냥 8 위다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 2. 3명·3명 순위합의 정확한 귀무분포
 * ================================================================== */
add((() => {
    const W = 720, H = 370;
    const sums = combos([1, 2, 3, 4, 5, 6], 3).map(c => c.reduce((a, b) => a + b, 0));
    const total = sums.length; // 20
    const cnt = {};
    for (const s of sums) cnt[s] = (cnt[s] ?? 0) + 1;
    const obs = 14;
    const upper = sums.filter(s => s >= obs).length;
    const lower = sums.filter(s => s <= 21 - obs).length; // 대칭: E(R_A) = 10.5
    const g = [txt(W / 2, 26, '3명과 3명의 순위합 — 순위 배치 20가지가 모두 같은 확률이다', { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [5, 16], yRange: [0, 3.6], box: { x: 80, y: 58, w: 560, h: 200 } });
    for (let s = 6; s <= 15; s += 1) {
        const c = cnt[s] ?? 0;
        const tail = s >= obs;
        const mirror = s <= 21 - obs;
        const col = tail ? 'var(--s2)' : mirror ? 'var(--s2)' : 'var(--s1)';
        g.push(bar(A, s, c, 20, { col, op: tail ? 0.55 : mirror ? 0.2 : 0.3 }));
        g.push(txt(A.X(s), A.Y(c) - 6, `${c}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(xaxis(A, [5, 16], [6, 7, 8, 9, 10, 11, 12, 13, 14, 15]));
    g.push(yaxis(A, [0, 3.6], 5, [1, 2, 3]));
    g.push(txt(A.X(5) - 8, A.Y(3.6) - 4, '배치 수', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(16) + 14, A.Y(0) + 4, 'R~{A}', { cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(14.5), A.Y(2.6), '관측 R~{A} = 14', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(A.X(14.5), A.Y(2.45), A.X(14.1), A.Y(1.25), { cls: 's2' }));
    g.push(txt(A.X(6.5), A.Y(2.6), '반대쪽 꼬리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(A.X(6.5), A.Y(2.45), A.X(6.9), A.Y(1.25), { cls: 'ark', width: 1.2 }));

    g.push(ln([[46, 290], [W - 46, 290]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 314, `14 이상인 배치 ${upper}/${total} → 한쪽 p값 ${r2(upper / total)}, 양쪽 p값 ${r2((upper + lower) / total)}`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 336, `가장 극단인 배치(15)조차 양쪽 p값이 ${r2(2 / total)} 이다. 이 크기에서는 0.05 로 기각할 길이 없다`,
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-np-ranksum',
        svg: svg({
            width: W, height: H,
            title: '두 집단 3명씩의 순위합 정확 귀무분포',
            desc: '순위 1부터 6 가운데 셋을 A 집단에 주는 20가지 배치의 순위합 분포. 6부터 15까지 대칭이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 3. 점근상대효율 — t 분포의 꼬리 두께에 따라
 * ================================================================== */
add((() => {
    const W = 740, H = 400;
    const nus = [];
    for (let v = 3; v <= 30; v += 0.5) nus.push(v);
    const rows = nus.map(v => ({ v, ...areT(v) }));
    const wilN = 3 / PI, signN = 2 / PI;
    const at = v => rows.find(r => r.v === v);
    const g = [txt(W / 2, 26, '꼬리가 두꺼울수록 순위·부호 검정이 t 검정보다 표본을 덜 쓴다', { anchor: 'middle', cls: 'ink bold' })];
    const A = frame({ xRange: [2, 32], yRange: [0.5, 2.0], box: { x: 80, y: 58, w: 520, h: 240 } });
    for (const y of [0.75, 1.25, 1.5, 1.75]) g.push(ln([[A.X(2), A.Y(y)], [A.X(32), A.Y(y)]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(ln([[A.X(2), A.Y(1)], [A.X(32), A.Y(1)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '6 4' }));
    g.push(txt(A.X(32) - 2, A.Y(1) - 6, '1 = t 검정과 같은 효율', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(ln([[A.X(2), A.Y(wilN)], [A.X(32), A.Y(wilN)]], { stroke: 'var(--s1)', sw: 1, dash: '2 4' }));
    g.push(ln([[A.X(2), A.Y(signN)], [A.X(32), A.Y(signN)]], { stroke: 'var(--s2)', sw: 1, dash: '2 4' }));
    g.push(A.line(rows.map(r => [r.v, r.wil]), { cls: 's1' }));
    g.push(A.line(rows.map(r => [r.v, Math.min(r.sign, 2.0)]), { cls: 's2' }));
    g.push(xaxis(A, [2, 32], [3, 5, 10, 15, 20, 25, 30], { y0: 0.5 }));
    g.push(yaxis(A, [0.5, 2.0], 2, [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]));
    g.push(txt(A.X(32) + 14, A.Y(0.5) + 4, 'ν', { cls: 'ink2' }));
    g.push(txt(A.X(17), A.Y(0.5) + 36, '자료 분포 = 자유도 ν 인 t 분포 (ν 가 작을수록 꼬리가 두껍다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(2) - 8, A.Y(2.0) - 14, 'ARE', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 곡선 이름
    g.push(txt(A.X(3) + 8, A.Y(at(3).wil) + 4, `윌콕슨 대 t  (ν=3 에서 ${at(3).wil.toFixed(2)})`, { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[A.X(3) + 4, A.Y(at(3).sign)], [A.X(4.6) - 4, A.Y(at(3).sign)]], { stroke: 'var(--s2)', sw: 1 }));
    g.push(txt(A.X(4.6), A.Y(at(3).sign) + 4, `부호검정 대 t  (ν=3 에서 ${at(3).sign.toFixed(2)})`, { cls: 'ink bold', size: 'sm' }));
    // 오른쪽: 정규 극한
    g.push(panel(612, 58, 112, 240, '정규일 때'));
    g.push(txt(668, 104, '윌콕슨', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(668, 122, `3/π ≈ ${wilN.toFixed(3)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(box(632, 130, 72, 3, { fill: 'var(--s1)', stroke: 'none', rx: 1.5 }));
    g.push(txt(668, 166, '부호검정', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(668, 184, `2/π ≈ ${signN.toFixed(3)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(box(632, 192, 72, 3, { fill: 'var(--s2)', stroke: 'none', rx: 1.5 }));
    g.push(txt(668, 230, '점선이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(668, 246, 'ν → ∞ 의', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(668, 262, '극한값', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[46, 352], [W - 46, 352]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 376, `ARE 가 1.9 이면 같은 검정력에 t 검정이 1.9 배의 표본을 써야 한다. 윌콕슨은 ν 가 ${(() => { const r = rows.find(q => q.wil < 1); return r ? r.v : '?'; })()} 쯤에서 1 아래로 내려간다`,
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'sta-np-are',
        svg: svg({
            width: W, height: H,
            title: 't 분포 자료에서 윌콕슨·부호검정의 t 검정 대비 점근상대효율',
            desc: '가로축은 자료 분포의 자유도, 세로축은 점근상대효율. 공식 12σ²(∫f²)² 와 4σ²f(0)² 를 수치적분으로 계산했다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4. 순열검정 — 표지를 섞은 70가지 배열
 * ================================================================== */
add((() => {
    const W = 740, H = 394;
    const A0 = [23, 27, 30, 34], B0 = [18, 20, 24, 25];
    const pool = [...A0, ...B0];
    const tot = pool.reduce((a, b) => a + b, 0);
    const mean = a => a.reduce((s, v) => s + v, 0) / a.length;
    const obs = mean(A0) - mean(B0);
    const diffs = combos([0, 1, 2, 3, 4, 5, 6, 7], 4).map(c => {
        const sa = c.reduce((s, i) => s + pool[i], 0);
        return sa / 4 - (tot - sa) / 4;
    });
    const nAll = diffs.length;
    const eps = 1e-9;
    const up = diffs.filter(d => d >= obs - eps).length;
    const both = diffs.filter(d => Math.abs(d) >= obs - eps).length;
    const cnt = new Map();
    for (const d of diffs) cnt.set(d, (cnt.get(d) ?? 0) + 1);
    const g = [txt(W / 2, 26, '두 집단 표지를 섞은 배열 70가지와 관측된 평균 차', { anchor: 'middle', cls: 'ink bold' })];
    g.push(txt(W / 2, 48, `A 집단 ${A0.join(', ')}  /  B 집단 ${B0.join(', ')}  /  관측된 평균 차 ${obs}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const maxC = Math.max(...cnt.values());
    const A = frame({ xRange: [-8.5, 8.5], yRange: [0, maxC + 1], box: { x: 70, y: 76, w: 600, h: 190 } });
    for (const [d, c] of [...cnt.entries()].sort((a, b) => a[0] - b[0])) {
        const tail = d >= obs - eps;
        const mirror = d <= -obs + eps;
        g.push(bar(A, d, c, 6.5, { col: tail || mirror ? 'var(--s2)' : 'var(--s1)', op: tail ? 0.6 : mirror ? 0.25 : 0.3 }));
    }
    g.push(xaxis(A, [-8.5, 8.5], [-8, -6, -4, -2, 0, 2, 4, 6, 8]));
    g.push(yaxis(A, [0, maxC + 1], -8.5, [1, 2, 3, 4, 5]));
    g.push(txt(A.X(-8.5) - 8, A.Y(maxC + 1) - 4, '배열 수', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(8.5), A.Y(0) + 34, '가로축: A 평균 − B 평균', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(A.X(obs) - 30, A.Y(4.3), '관측값', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(A.X(obs) - 30, A.Y(4.1), A.X(obs) - 3, A.Y(1.4), { cls: 's2' }));

    g.push(ln([[46, 312], [W - 46, 312]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 336, `관측값 이상인 배열 ${up}/${nAll} → 한쪽 p값 ${(up / nAll).toFixed(3)},  절댓값이 그 이상인 배열 ${both}/${nAll} → 양쪽 p값 ${(both / nAll).toFixed(3)}`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 358, '귀무가설 아래 표지는 아무 뜻이 없으므로, 실제 배열은 이 70가지 가운데 아무것이나 될 수 있었다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-np-perm',
        svg: svg({
            width: W, height: H,
            title: '순열검정 — 표지를 섞은 모든 배열의 평균 차 분포',
            desc: '여덟 값을 넷·넷으로 나누는 70가지 배열마다 평균 차를 계산한 막대그림과 관측된 평균 차',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5. 최댓값의 부트스트랩이 실패한다
 * ================================================================== */
add((() => {
    const W = 760, H = 390;
    const n = 10;
    const sample = [0.07, 0.15, 0.29, 0.36, 0.48, 0.55, 0.63, 0.74, 0.82, 0.91];
    const mx = Math.max(...sample);
    const mass = sample.map((x, i) => [x - mx, ((i + 1) / n) ** n - (i / n) ** n]);
    const top = mass[n - 1][1];
    const g = [txt(W / 2, 26, '최댓값의 부트스트랩 분포는 관측 최댓값 한 점에 덩어리가 박힌다', { anchor: 'middle', cls: 'ink bold' })];

    // 왼쪽: 참 분포 θ̂ − θ (θ = 1, n = 10): 밀도 10(1+u)^9, u ∈ [−1, 0]
    g.push(panel(20, 50, 350, 270, '참 분포:  최댓값 − θ', { sub: 'U(0, θ) 에서 n = 10 (θ = 1)' }));
    const L = frame({ xRange: [-0.5, 0.05], yRange: [0, 11], box: { x: 64, y: 104, w: 270, h: 170 } });
    const pts = [[-0.5, 0]];
    for (let i = 0; i <= 120; i += 1) { const u = -0.5 + (0.5 * i) / 120; pts.push([u, n * (1 + u) ** (n - 1)]); }
    pts.push([0, 0]);
    g.push(`<path d="M${pts.map(([x, y]) => `${L.X(x)} ${L.Y(y)}`).join(' L')} Z" fill="var(--s1)" fill-opacity="0.18" stroke="none"/>`);
    g.push(L.line(pts.slice(1, -1), { cls: 's1' }));
    g.push(xaxis(L, [-0.5, 0.05], [-0.4, -0.2, 0]));
    g.push(yaxis(L, [0, 11], -0.5, [5, 10]));
    g.push(txt(L.X(-0.5) - 8, L.Y(11) - 4, '밀도', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(L.X(-0.27), L.Y(7.2), '0 에서 가장 높지만', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(L.X(-0.27), L.Y(7.2) + 16, '정확히 0 일 확률은 0', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 오른쪽: 부트스트랩 분포 θ̂* − θ̂ (확률 막대)
    g.push(panel(390, 50, 350, 270, '부트스트랩:  재표본 최댓값 − 0.91', { sub: '표본 하나(n = 10, 최댓값 0.91)에서 계산' }));
    const R = frame({ xRange: [-0.5, 0.05], yRange: [0, 0.75], box: { x: 434, y: 104, w: 270, h: 170 } });
    for (const [u, p] of mass) {
        if (u < -0.5) continue;
        g.push(bar(R, u, p, 5, { col: u === 0 ? 'var(--s2)' : 'var(--s1)', op: u === 0 ? 0.6 : 0.35 }));
    }
    g.push(xaxis(R, [-0.5, 0.05], [-0.4, -0.2, 0]));
    g.push(yaxis(R, [0, 0.75], -0.5, [0.25, 0.5]));
    g.push(txt(R.X(-0.5) - 8, R.Y(0.75) - 4, '확률', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(R.X(-0.06), R.Y(top) - 8, `${top.toFixed(3)}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(R.X(-0.25), R.Y(0.5), '오른쪽(양수)은', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(R.X(-0.25), R.Y(0.5) + 16, '한 번도 나오지 않는다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(ln([[46, 340], [W - 46, 340]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(W / 2, 364, `재표본이 관측 최댓값을 한 번이라도 뽑을 확률 1 − (1 − 1/n)ⁿ = ${top.toFixed(3)}. n 이 커져도 1 − 1/e ≈ ${(1 - Math.exp(-1)).toFixed(3)} 로 남는다`,
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'sta-np-boot-max',
        svg: svg({
            width: W, height: H,
            title: '최댓값의 부트스트랩 분포와 참 분포',
            desc: '균등분포 상한을 최댓값으로 추정할 때, 참 표본분포는 연속인데 부트스트랩 분포는 0 에 확률 0.65 의 덩어리를 갖는다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
