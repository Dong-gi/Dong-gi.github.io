/**
 * 기초수학 11장(적분)과 12장(확률)의 그림.
 *
 * 이름은 전부 `math-int-` 로 시작한다(담당 D 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 그래서 첨자는 lib 의 `x~0` 표기를, 나머지는 유니코드(√, π, σ, μ, ∫, Σ, Δ, ≈)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰면 안 되고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 난수를 쓰는 그림(큰 수의 법칙)은 씨앗을 고정한 선형합동생성기를 쓴다.
 * 빌드할 때마다 그림이 달라지면 안 되기 때문이다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const PI = Math.PI;

/* ------------------------------------------------------------------ *
 * 공통 소도구
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

/** 양쪽 화살표. 길이를 재는 표시에 쓴다. */
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
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 타원. 회전체의 단면을 비스듬히 본 모습에 쓴다. */
function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 채운 점. */
const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 빈 점 — 그 자리에 값이 없다는 뜻. 안쪽을 배경색으로 채워야 밑줄이 비친다. */
const odot = (x, y, col = 'var(--s2)', r = 4.5) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="var(--bgfix)" stroke="${col}" stroke-width="2"/>`;

/** odot 이 쓰는 배경색. 사이트 다크 모드 배경이 #121212 다. */
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

/** 패널 테두리 + 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 데이터 좌표 다각형 채우기. */
const poly = (g, pts, col, op) =>
    `<path d="M${pts.map(([x, y]) => `${g.X(x)} ${g.Y(y)}`).join(' L')} Z" fill="${col}" fill-opacity="${op}" stroke="none"/>`;

/** 데이터 좌표로 준 곡선 아래 영역을 채운다. */
function underArea(g, f, from, to, col, op, base = 0, steps = 60) {
    const pts = [[from, base]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([x, f(x)]);
    }
    pts.push([to, base]);
    return poly(g, pts, col, op);
}

/** 데이터 좌표 직사각형. y1 < y2 를 가정한다. */
const drect = (g, x1, y1, x2, y2, o) =>
    box(g.X(x1), g.Y(y2), g.X(x2) - g.X(x1), g.Y(y1) - g.Y(y2), o);

/** 씨앗을 고정한 난수. 빌드 결과가 매번 같아야 한다. */
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}

/* ================================================================== *
 * 11장 — 적분
 * ================================================================== */

/* ---- 11-1. 구분구적법 — 칸을 잘게 나눈다 ---- */
add((() => {
    const W = 700, H = 350;
    const f = x => x * x;
    const g = [];
    const mk = (bx, nrect) => {
        const fr = frame({ xRange: [0, 1.15], yRange: [0, 1.18], box: { x: bx, y: 78, w: 232, h: 190 } });
        const out = [];
        let sum = 0;
        for (let i = 1; i <= nrect; i += 1) {
            const xl = (i - 1) / nrect, xr = i / nrect, ht = f(xr);
            sum += ht / nrect;
            out.push(drect(fr, xl, 0, xr, ht, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--s1)', sw: 0.9, rx: 0 }));
        }
        out.push(fr.axes({ xLabel: 'x', xTicks: [0, 0.5, 1], yTicks: [0.5, 1], grid: false }));
        out.push(fr.curve(f, { from: 0, to: 1.08, cls: 's2' }));
        out.push(fr.label([1.08, f(1.08)], 'y = x²', { dx: -4, dy: -10, anchor: 'end', cls: 'ink' }));
        return { body: out.join(''), sum };
    };
    const A = mk(64, 4);
    const B = mk(396, 12);
    const body = [
        txt(180, 46, '칸 4개', { anchor: 'middle', cls: 'ink bold' }),
        txt(512, 46, '칸 12개', { anchor: 'middle', cls: 'ink bold' }),
        A.body, B.body,
        txt(180, 306, `직사각형 넓이의 합 = ${A.sum.toFixed(4)}`, { anchor: 'middle', cls: 'ink' }),
        txt(512, 306, `직사각형 넓이의 합 = ${B.sum.toFixed(4)}`, { anchor: 'middle', cls: 'ink' }),
        txt(350, 334, '칸을 잘게 나눌수록 합은 1/3 = 0.3333… 으로 내려간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-exhaustion',
        svg: svg({
            width: W, height: H,
            title: '구분구적법 — 곡선 아래를 직사각형으로 덮는다',
            desc: '포물선 아래 넓이를 직사각형 4개와 12개로 근사한 그림. 칸 수를 늘리면 합이 1/3 로 다가간다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-2. 표본점을 어디서 잡을 것인가 ---- */
add((() => {
    const W = 760, H = 300;
    const f = x => 0.25 * x * x + 0.4;
    const a = 0, b = 2.4, nrect = 6, dx = (b - a) / nrect;
    const mk = (bx, pick) => {
        const fr = frame({ xRange: [-0.1, 2.8], yRange: [0, 2.1], box: { x: bx, y: 64, w: 190, h: 165 } });
        const out = [];
        let sum = 0;
        for (let i = 0; i < nrect; i += 1) {
            const xl = a + i * dx, xr = xl + dx;
            const xs = pick === 'L' ? xl : pick === 'R' ? xr : (xl + xr) / 2;
            const ht = f(xs);
            sum += ht * dx;
            out.push(drect(fr, xl, 0, xr, ht, { fill: 'var(--s1)', op: 0.15, stroke: 'var(--s1)', sw: 0.9, rx: 0 }));
            out.push(pdot(fr.X(xs), fr.Y(ht), 'var(--s2)', 2.6));
        }
        out.push(fr.axes({ xTicks: [0, 1, 2], yTicks: [1, 2], grid: false }));
        out.push(fr.curve(f, { from: -0.05, to: 2.7, cls: 's2' }));
        return { body: out.join(''), sum };
    };
    const P = [
        { x: 55, pick: 'L', name: '왼쪽 끝점', note: '실제보다 작다' },
        { x: 285, pick: 'M', name: '중점', note: '가장 가깝다' },
        { x: 515, pick: 'R', name: '오른쪽 끝점', note: '실제보다 크다' },
    ];
    const exact = (0.25 / 3) * b ** 3 + 0.4 * b;
    const body = P.map(p => {
        const m = mk(p.x, p.pick);
        return txt(p.x + 95, 44, p.name, { anchor: 'middle', cls: 'ink bold' })
            + m.body
            + txt(p.x + 95, 262, `합 = ${m.sum.toFixed(3)}  (${p.note})`, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    }).join('')
        + txt(380, 288, `참값은 ${exact.toFixed(3)}. 칸을 잘게 나누면 세 값이 모두 이 하나로 모인다 — 그 값을 정적분이라 부른다`, { anchor: 'middle', cls: 'ink', size: 'sm' });
    return {
        name: 'math-int-riemann-sample',
        svg: svg({
            width: W, height: H,
            title: '리만 합에서 표본점을 고르는 세 가지 방법',
            desc: '같은 6칸이라도 왼쪽 끝점은 작게, 오른쪽 끝점은 크게, 중점은 그 사이 값을 준다. 칸을 잘게 나누면 셋이 같은 값으로 모인다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-3. 부호 있는 넓이 ---- */
add((() => {
    const W = 700, H = 300;
    const f = x => 1.2 * Math.sin((PI * x) / 1.5);
    const fr = frame({ xRange: [-0.25, 3.35], yRange: [-1.7, 1.7], box: { x: 62, y: 40, w: 320, h: 215 } });
    const body = [
        underArea(fr, f, 0, 1.5, 'var(--s1)', 0.2),
        underArea(fr, f, 1.5, 3, 'var(--s2)', 0.24),
        fr.axes({ xLabel: 'x', xTicks: [0, 1, 2, 3], yTicks: [-1, 1], grid: false }),
        fr.curve(f, { from: -0.2, to: 3.3, cls: 's3' }),
        fr.label([0.75, 0.42], '+', { anchor: 'middle', cls: 'ink bold' }),
        fr.label([2.25, -0.5], '−', { anchor: 'middle', cls: 'ink bold' }),
        fr.label([0.75, 0.72], '위쪽 넓이', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        fr.label([2.25, -0.85], '아래쪽 넓이', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(418, 74, '정적분은 넓이를 부호까지', { cls: 'ink' }),
        txt(418, 98, '세어 더한다. 위는 +, 아래는 −.', { cls: 'ink' }),
        txt(418, 136, '이 그림에서는 두 넓이가 같으므로', { cls: 'ink2', size: 'sm' }),
        txt(418, 156, '0 부터 3 까지의 정적분은 0 이다.', { cls: 'ink2', size: 'sm' }),
        txt(418, 194, '‘실제로 칠해진 넓이’를 원한다면', { cls: 'ink2', size: 'sm' }),
        txt(418, 214, '|f(x)| 를 적분해야 한다.', { cls: 'ink2', size: 'sm' }),
        txt(418, 234, '둘은 다른 값이다.', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-signed-area',
        svg: svg({
            width: W, height: H,
            title: '정적분은 부호 있는 넓이다',
            desc: 'x축 위쪽 넓이는 양수, 아래쪽 넓이는 음수로 센다. 두 넓이가 같으면 정적분은 0 이 된다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-4. 넓이 함수 A(x) ---- */
add((() => {
    const W = 660, H = 450;
    const k = 1.3;
    const f = x => 1.2 + 0.9 * Math.sin(k * x);
    const A = x => 1.2 * x + (0.9 / k) * (1 - Math.cos(k * x));
    const x0 = 2.6;
    const xMax = (PI / 2) / k;          // f 가 최대인 곳
    const xMin = ((3 * PI) / 2) / k;    // f 가 최소인 곳
    const top = frame({ xRange: [-0.15, 4.4], yRange: [0, 2.5], box: { x: 74, y: 56, w: 470, h: 135 } });
    const bot = frame({ xRange: [-0.15, 4.4], yRange: [0, 5.9], box: { x: 74, y: 262, w: 470, h: 135 } });
    const body = [
        txt(74, 38, 'f(x) — 곡선의 높이', { cls: 'ink bold' }),
        underArea(top, f, 0, x0, 'var(--s1)', 0.18),
        top.axes({ xTicks: [0, 1, 2, 3, 4], yTicks: [1, 2], grid: false }),
        top.curve(f, { from: -0.1, to: 4.3, cls: 's2' }),
        top.guide([x0, 0], [x0, f(x0)]),
        top.label([1.25, 0.5], '이만큼의 넓이가', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(556, 100, 'x 를 오른쪽으로', { cls: 'ink2', size: 'sm' }),
        txt(556, 118, '밀면서 넓이를', { cls: 'ink2', size: 'sm' }),
        txt(556, 136, '계속 재 나간다', { cls: 'ink2', size: 'sm' }),

        pdot(top.X(xMax), top.Y(f(xMax)), 'var(--s3)', 4),
        pdot(top.X(xMin), top.Y(f(xMin)), 'var(--s3)', 4),
        txt(top.X(xMax), top.Y(f(xMax)) - 10, '①', { anchor: 'middle', cls: 'ink bold' }),
        txt(top.X(xMin) - 14, top.Y(f(xMin)) - 10, '②', { anchor: 'end', cls: 'ink bold' }),

        txt(74, 236, 'A(x) — 0 부터 x 까지 쌓인 넓이', { cls: 'ink bold' }),
        bot.axes({ xLabel: 'x', xTicks: [0, 1, 2, 3, 4], yTicks: [2, 4], grid: false }),
        bot.curve(A, { from: 0, to: 4.3, cls: 's1' }),
        bot.guide([x0, 0], [x0, A(x0)]),
        bot.guide([-0.1, A(x0)], [x0, A(x0)]),
        pdot(bot.X(x0), bot.Y(A(x0)), 'var(--s1)', 4.5),
        bot.label([x0, A(x0)], 'A(2.6)', { dx: -8, dy: -10, anchor: 'end', cls: 'ink' }),
        // ① 과 ② 자리에서 A 의 기울기를 짧은 접선으로 보인다
        ln([[bot.X(xMax - 0.5), bot.Y(A(xMax) - 0.5 * f(xMax))], [bot.X(xMax + 0.5), bot.Y(A(xMax) + 0.5 * f(xMax))]], { stroke: 'var(--s3)', sw: 2.4 }),
        ln([[bot.X(xMin - 0.5), bot.Y(A(xMin) - 0.5 * f(xMin))], [bot.X(xMin + 0.5), bot.Y(A(xMin) + 0.5 * f(xMin))]], { stroke: 'var(--s3)', sw: 2.4 }),
        pdot(bot.X(xMax), bot.Y(A(xMax)), 'var(--s3)', 4),
        pdot(bot.X(xMin), bot.Y(A(xMin)), 'var(--s3)', 4),
        txt(bot.X(xMax) - 6, bot.Y(A(xMax)) + 20, '①', { anchor: 'end', cls: 'ink bold' }),
        txt(bot.X(xMin), bot.Y(A(xMin)) + 24, '②', { anchor: 'middle', cls: 'ink bold' }),

        txt(340, 428, '① f 가 가장 큰 자리에서 A 가 가장 가파르고, ② f 가 가장 작은 자리에서 A 가 가장 평평하다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-area-function',
        svg: svg({
            width: W, height: H,
            title: '넓이 함수와 원래 함수의 관계',
            desc: '위는 f(x), 아래는 0 부터 x 까지의 넓이 A(x). f 가 클수록 A 가 가파르게 오르고 f 가 작을수록 A 가 평평하다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-5. 미적분학의 기본정리 — 조각 하나를 늘려 본다 ---- */
add((() => {
    const W = 720, H = 400;
    const f = x => 0.9 + 0.55 * x - 0.08 * x * x;
    const a = 0.5, x0 = 2.3, h = 0.55;
    const fr = frame({ xRange: [0, 4.3], yRange: [0, 2.5], box: { x: 62, y: 62, w: 340, h: 210 } });
    const zx = 470, zy = 78, zw = 96, zh = 140;   // 확대한 조각
    const slope = (f(x0 + h) - f(x0)) / h;
    const tilt = 18;                               // 확대 그림에서 윗변의 기울기(화소)
    const body = [
        underArea(fr, f, a, x0, 'var(--s1)', 0.16),
        underArea(fr, f, x0, x0 + h, 'var(--s2)', 0.35),
        fr.axes({ xLabel: 'x', xTicks: [], yTicks: [], grid: false }),
        fr.curve(f, { from: 0, to: 4.2, cls: 's3' }),
        fr.guide([a, 0], [a, f(a)]),
        fr.guide([x0, 0], [x0, f(x0)]),
        fr.guide([x0 + h, 0], [x0 + h, f(x0 + h)]),
        txt(fr.X(a), fr.Y(0) + 18, 'a', { anchor: 'middle', cls: 'ink' }),
        txt(fr.X(x0), fr.Y(0) + 18, 'x', { anchor: 'middle', cls: 'ink' }),
        txt(fr.X(x0 + h) + 4, fr.Y(0) + 18, 'x + h', { anchor: 'middle', cls: 'ink' }),
        fr.label([(a + x0) / 2, 0.5], 'A(x)', { anchor: 'middle', cls: 'ink bold' }),
        fr.label([(a + x0) / 2, 0.24], '이미 쌓인 넓이', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        arw(fr.X(x0 + h / 2), fr.Y(f(x0)) - 46, fr.X(x0 + h / 2), fr.Y(f(x0)) - 8, { cls: 's2', width: 1.6 }),
        txt(fr.X(x0 + h / 2), fr.Y(f(x0)) - 54, '늘어난 조각', { anchor: 'middle', cls: 'ink', size: 'sm' }),

        // 확대한 조각
        `<path d="M${zx} ${zy + zh} L${zx} ${zy + tilt} L${zx + zw} ${zy} L${zx + zw} ${zy + zh} Z" fill="var(--s2)" fill-opacity="0.35" stroke="var(--s2)" stroke-width="1.6"/>`,
        ln([[zx, zy + tilt], [zx + zw, zy + tilt]], { stroke: 'var(--ink2)', sw: 1, dash: '4 3' }),
        arw2(zx, zy + zh + 16, zx + zw, zy + zh + 16),
        txt(zx + zw / 2, zy + zh + 34, 'h', { anchor: 'middle', cls: 'ink' }),
        arw2(zx - 14, zy + tilt, zx - 14, zy + zh),
        txt(zx - 20, zy + zh / 2, 'f(x)', { anchor: 'end', cls: 'ink' }),
        txt(zx + zw / 2, zy - 12, '확대하면 거의 직사각형', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

        txt(430, 268, 'A(x + h) − A(x)  ≈  f(x) · h', { cls: 'ink bold' }),
        txt(430, 294, '늘어난 넓이 ≈ 높이 × 너비', { cls: 'ink2', size: 'sm' }),
        ln([[430, 312], [692, 312]], { stroke: 'var(--grid)', sw: 1 }),
        txt(430, 336, '양변을 h 로 나누고 h → 0 으로 보내면', { cls: 'ink2', size: 'sm' }),
        txt(430, 368, 'A′(x) = f(x)', { cls: 'ink bold' }),
        txt(62, 336, '높이가 곧 넓이의 순간 증가율이다.', { cls: 'ink', size: 'sm' }),
        txt(62, 358, '넓이를 재는 일과 기울기를 재는 일이', { cls: 'ink', size: 'sm' }),
        txt(62, 380, '서로 역이라는 사실이 여기서 나온다.', { cls: 'ink', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-ftc-strip',
        svg: svg({
            width: W, height: H,
            title: '미적분학의 기본정리 — 넓이를 h 만큼 늘려 보면',
            desc: 'x 에서 x+h 까지 늘어난 넓이는 높이 f(x), 너비 h 인 직사각형에 가깝다. h 를 0 으로 보내면 넓이 함수의 도함수가 f(x) 가 된다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-6. 두 곡선 사이의 넓이 ---- */
add((() => {
    const W = 660, H = 300;
    const fr = frame({ xRange: [-0.12, 1.32], yRange: [-0.12, 1.28], box: { x: 66, y: 44, w: 300, h: 200 } });
    const up = x => x, low = x => x * x;
    const xs = 0.5;
    const band = [];
    for (let i = 0; i <= 60; i += 1) band.push([i / 60, up(i / 60)]);
    for (let i = 60; i >= 0; i -= 1) band.push([i / 60, low(i / 60)]);
    const body = [
        poly(fr, band, 'var(--s3)', 0.22),
        fr.axes({ xLabel: 'x', xTicks: [0, 0.5, 1], yTicks: [0.5, 1], grid: false }),
        fr.curve(up, { from: -0.08, to: 1.25, cls: 's2' }),
        fr.curve(low, { from: -0.08, to: 1.13, cls: 's1' }),
        fr.label([0.36, up(0.36)], 'y = x', { dx: -6, dy: -8, anchor: 'end', cls: 'ink' }),
        fr.label([0.88, low(0.88)], 'y = x²', { dx: 10, dy: 22, cls: 'ink' }),
        arw2(fr.X(xs), fr.Y(low(xs)), fr.X(xs), fr.Y(up(xs)), { cls: 's3', width: 1.8 }),
        fr.label([xs, (up(xs) + low(xs)) / 2], '위 − 아래', { dx: 8, dy: 4, cls: 'ink', size: 'sm' }),
        fr.dot([1, 1], { cls: 'f3', r: 4 }),
        fr.dot([0, 0], { cls: 'f3', r: 4 }),
        txt(404, 78, '두 곡선이 만나는 x 를 먼저 구한다.', { cls: 'ink', size: 'sm' }),
        txt(404, 100, 'x = x² 에서 x = 0 과 x = 1.', { cls: 'ink', size: 'sm' }),
        txt(404, 136, '그 사이에서는 언제나 x ≥ x² 이므로', { cls: 'ink', size: 'sm' }),
        txt(404, 158, '위에 있는 것은 y = x 쪽이다.', { cls: 'ink', size: 'sm' }),
        txt(404, 196, '넓이 = ∫ (위 − 아래) dx', { cls: 'ink bold' }),
        txt(404, 222, '= 1/2 − 1/3 = 1/6', { cls: 'ink bold' }),
        txt(404, 252, '위아래가 뒤바뀌는 구간이 있으면', { cls: 'ink2', size: 'sm' }),
        txt(404, 272, '거기서 끊어 따로 적분해야 한다.', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-between-curves',
        svg: svg({
            width: W, height: H,
            title: '두 곡선 사이의 넓이',
            desc: '위에 있는 함수에서 아래에 있는 함수를 뺀 것을 적분하면 두 곡선 사이의 넓이가 된다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-7. 회전체의 부피 — 원판으로 썰기 ---- */
add((() => {
    const W = 680, H = 330;
    const f = x => 0.45 + 0.26 * x;
    const fr = frame({ xRange: [0, 4.2], yRange: [-1.7, 1.7], box: { x: 60, y: 52, w: 320, h: 200 } });
    const a = 0.5, b = 3.6, xs = 2.2;
    const band = [];
    for (let i = 0; i <= 40; i += 1) { const x = a + ((b - a) * i) / 40; band.push([x, f(x)]); }
    for (let i = 40; i >= 0; i -= 1) { const x = a + ((b - a) * i) / 40; band.push([x, -f(x)]); }
    const ryPix = fr.Y(0) - fr.Y(f(xs));
    const body = [
        poly(fr, band, 'var(--s1)', 0.14),
        fr.axes({ xLabel: 'x', xTicks: [], yTicks: [], grid: false }),
        fr.curve(f, { from: a, to: b, cls: 's1' }),
        fr.curve(x => -f(x), { from: a, to: b, cls: 's1', dash: '5 4' }),
        ell(fr.X(b), fr.Y(0), 11, ryPix + 8, { stroke: 'var(--s1)', sw: 1.2, dash: '4 3' }),
        drect(fr, xs - 0.09, -f(xs), xs + 0.09, f(xs), { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)', sw: 1.2, rx: 0 }),
        ell(fr.X(xs + 0.09), fr.Y(0), 9, ryPix, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)', sw: 1.4 }),
        txt(fr.X(a), fr.Y(0) + 18, 'a', { anchor: 'middle', cls: 'ink' }),
        txt(fr.X(b), fr.Y(0) + 18, 'b', { anchor: 'middle', cls: 'ink' }),
        txt(fr.X(xs) - 15, fr.Y(0) + 18, 'x', { anchor: 'middle', cls: 'ink' }),
        fr.label([1.1, f(1.1)], 'y = f(x)', { dx: -6, dy: -10, anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(fr.X(xs) + 4, 44, '이 자리를 얇게 썬 원판', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

        circ(520, 132, 62, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.6 }),
        arw(520, 132, 520 + 44, 132 - 44, { cls: 's2', width: 1.6 }),
        txt(592, 76, '반지름 f(x)', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        pdot(520, 132, 'var(--s2)', 3),
        txt(520, 226, '단면 넓이 = π f(x)²', { anchor: 'middle', cls: 'ink' }),
        txt(520, 250, '두께 dx 를 곱해 a 에서 b 까지 더한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(520, 288, 'V = ∫ π f(x)² dx', { anchor: 'middle', cls: 'ink bold' }),
        txt(340, 316, '부피 구하기는 ‘단면 넓이를 구한 뒤 두께 방향으로 적분하는 일’로 늘 같은 꼴이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-disk',
        svg: svg({
            width: W, height: H,
            title: '회전체의 부피 — 얇은 원판으로 썰어 더한다',
            desc: 'x축을 축으로 회전시킨 입체를 x 자리에서 얇게 썰면 반지름 f(x) 인 원판이 된다. 그 넓이 πf(x)² 를 적분하면 부피가 나온다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-8. 곡선의 길이 ---- */
add((() => {
    const W = 680, H = 320;
    const f = x => 0.25 * x * x + 0.3;
    const fr = frame({ xRange: [-0.15, 3.3], yRange: [0, 3.1], box: { x: 62, y: 50, w: 290, h: 205 } });
    const nseg = 4, a = 0, b = 3;
    const pts = [];
    for (let i = 0; i <= nseg; i += 1) { const x = a + ((b - a) * i) / nseg; pts.push([x, f(x)]); }
    const i0 = 2;
    const p1 = pts[i0], p2 = pts[i0 + 1];
    const body = [
        fr.axes({ xLabel: 'x', xTicks: [0, 1, 2, 3], yTicks: [1, 2, 3], grid: false }),
        fr.curve(f, { from: -0.1, to: 3.2, cls: 's1' }),
        fr.line(pts, { cls: 's2' }),
        ...pts.map(p => fr.dot(p, { cls: 'f2', r: 3.5 })),
        fr.label([0.35, f(0.35)], '곡선', { dx: -6, dy: -8, anchor: 'end', cls: 'ink', size: 'sm' }),
        fr.label([2.1, 1.0], '꺾은선으로 근사', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        drect(fr, p1[0], p1[1], p2[0], p2[1], { stroke: 'var(--s3)', sw: 1.2, dash: '4 3', rx: 0 }),

        txt(520, 44, '한 조각을 확대하면', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        `<path d="M420 190 L620 190 L620 78 Z" fill="var(--s3)" fill-opacity="0.16" stroke="none"/>`,
        ln([[420, 190], [620, 190]], { stroke: 'var(--ink2)', sw: 1.6 }),
        ln([[620, 190], [620, 78]], { stroke: 'var(--ink2)', sw: 1.6 }),
        ln([[420, 190], [620, 78]], { stroke: 'var(--s2)', sw: 2.4 }),
        `<path class="gr" fill="none" d="M608 190 L608 178 L620 178"/>`,
        txt(520, 210, 'Δx', { anchor: 'middle', cls: 'ink' }),
        txt(636, 138, 'Δy', { cls: 'ink' }),
        txt(500, 118, '√(Δx² + Δy²)', { anchor: 'middle', cls: 'ink' }),
        txt(420, 244, '= √(1 + (Δy/Δx)²) · Δx', { cls: 'ink', size: 'sm' }),
        txt(420, 268, 'Δx → 0 이면 Δy/Δx → f′(x) 이므로', { cls: 'ink2', size: 'sm' }),
        txt(420, 296, 'L = ∫ √(1 + f′(x)²) dx', { cls: 'ink bold' }),
        txt(62, 296, '조각을 잘게 나눌수록 꺾은선의', { cls: 'ink2', size: 'sm' }),
        txt(62, 314, '길이가 곡선의 길이로 다가간다.', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-arclength',
        svg: svg({
            width: W, height: H,
            title: '곡선의 길이 — 꺾은선으로 근사한다',
            desc: '곡선을 짧은 선분으로 잇고 피타고라스 정리로 각 선분의 길이를 잰 뒤 잘게 나누는 극한을 취한다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-9. 이상적분 — 꼬리의 넓이가 유한할 수도 있다 ---- */
add((() => {
    const W = 700, H = 340;
    const mk = (bx, f, lab) => {
        const fr = frame({ xRange: [0.55, 6.6], yRange: [0, 1.3], box: { x: bx, y: 66, w: 250, h: 180 } });
        return underArea(fr, f, 1, 6.4, 'var(--s1)', 0.2, 0, 90)
            + fr.axes({ xLabel: 'x', xTicks: [1, 2, 3, 4, 5, 6], yTicks: [0.5, 1], grid: false })
            + fr.curve(f, { from: 0.8, to: 6.5, cls: 's2' })
            + fr.guide([1, 0], [1, f(1)])
            + fr.label([3.2, 0.12], lab, { anchor: 'middle', cls: 'ink', size: 'sm' });
    };
    const body = [
        txt(183, 46, 'y = 1/x²', { anchor: 'middle', cls: 'ink bold' }),
        txt(515, 46, 'y = 1/x', { anchor: 'middle', cls: 'ink bold' }),
        mk(58, x => 1 / (x * x), '넓이를 1 부터 끝없이 더한다'),
        mk(390, x => 1 / x, '넓이를 1 부터 끝없이 더한다'),
        txt(183, 284, '1 − 1/t  →  1', { anchor: 'middle', cls: 'ink bold' }),
        txt(515, 284, 'ln t  →  ∞', { anchor: 'middle', cls: 'ink bold' }),
        txt(183, 306, '수렴한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(515, 306, '발산한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(350, 332, '두 그림은 눈으로는 구별되지 않는다. 어느 쪽인지는 계산해 봐야 안다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-improper',
        svg: svg({
            width: W, height: H,
            title: '끝없이 뻗은 영역의 넓이 — 수렴과 발산',
            desc: '1/x² 의 꼬리 넓이는 1 로 수렴하지만 1/x 의 꼬리 넓이는 무한대로 발산한다. 그림만으로는 구별할 수 없다',
            body: BG + body,
        }),
    };
})());

/* ---- 11-10. 수치적분 — 사다리꼴과 심프슨 ---- */
add((() => {
    const W = 700, H = 330;
    const f = x => 0.42 + 0.9 * Math.sin(0.62 * x);
    const a = 0, b = 4, nseg = 4, dx = (b - a) / nseg;
    const mk = (bx, kind) => {
        const fr = frame({ xRange: [-0.2, 4.5], yRange: [0, 1.6], box: { x: bx, y: 72, w: 250, h: 175 } });
        const out = [underArea(fr, f, a, b, 'var(--s1)', 0.1)];
        if (kind === 'trap') {
            for (let i = 0; i < nseg; i += 1) {
                const xl = a + i * dx, xr = xl + dx;
                out.push(poly(fr, [[xl, 0], [xl, f(xl)], [xr, f(xr)], [xr, 0]], 'var(--s2)', 0.2));
                out.push(fr.line([[xl, f(xl)], [xr, f(xr)]], { cls: 's2' }));
                out.push(fr.line([[xl, 0], [xl, f(xl)]], { cls: 's2' }));
            }
            out.push(fr.line([[b, 0], [b, f(b)]], { cls: 's2' }));
        } else {
            // 세 점을 지나는 포물선 두 개
            for (let s = 0; s < nseg / 2; s += 1) {
                const x0 = a + 2 * s * dx, x1 = x0 + dx, x2 = x0 + 2 * dx;
                const y0 = f(x0), y1 = f(x1), y2 = f(x2);
                const q = t => {
                    const l0 = ((t - x1) * (t - x2)) / ((x0 - x1) * (x0 - x2));
                    const l1 = ((t - x0) * (t - x2)) / ((x1 - x0) * (x1 - x2));
                    const l2 = ((t - x0) * (t - x1)) / ((x2 - x0) * (x2 - x1));
                    return y0 * l0 + y1 * l1 + y2 * l2;
                };
                out.push(underArea(fr, q, x0, x2, 'var(--s2)', 0.2, 0, 40));
                out.push(fr.curve(q, { from: x0, to: x2, cls: 's2' }));
                out.push(fr.line([[x0, 0], [x0, y0]], { cls: 's2' }));
            }
            out.push(fr.line([[b, 0], [b, f(b)]], { cls: 's2' }));
        }
        out.push(fr.axes({ xLabel: 'x', xTicks: [0, 1, 2, 3, 4], yTicks: [0.5, 1, 1.5], grid: false }));
        out.push(fr.curve(f, { from: -0.15, to: 4.4, cls: 's3' }));
        for (let i = 0; i <= nseg; i += 1) out.push(fr.dot([a + i * dx, f(a + i * dx)], { cls: 'f3', r: 3 }));
        return out.join('');
    };
    const body = [
        txt(183, 48, '사다리꼴 공식', { anchor: 'middle', cls: 'ink bold' }),
        txt(515, 48, '심프슨 공식', { anchor: 'middle', cls: 'ink bold' }),
        mk(58, 'trap'), mk(390, 'simp'),
        txt(183, 286, '점과 점을 직선으로 잇는다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(515, 286, '점 세 개를 포물선으로 잇는다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(350, 316, '같은 5개 점을 쓰는데도 포물선 쪽이 원래 곡선(가는 선)에 훨씬 가깝게 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'math-int-numerical',
        svg: svg({
            width: W, height: H,
            title: '수치적분 — 사다리꼴 공식과 심프슨 공식',
            desc: '같은 개수의 점을 쓸 때 직선으로 잇는 사다리꼴 공식보다 포물선으로 잇는 심프슨 공식이 곡선에 더 가깝다',
            body: BG + body,
        }),
    };
})());

/* ================================================================== *
 * 12장 — 확률
 * ================================================================== */

/* ---- 12-1. 곱의 법칙 — 수형도 ---- */
add((() => {
    const W = 620, H = 300;
    const tops = ['윗옷 ㄱ', '윗옷 ㄴ', '윗옷 ㄷ'];
    const bots = ['바지 1', '바지 2'];
    const g = [];
    const rx = 56, ry = 150;
    g.push(pdot(rx, ry, 'var(--ink2)', 5));
    g.push(txt(rx, ry + 24, '출발', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const l1y = [64, 150, 236];
    tops.forEach((t, i) => {
        g.push(arw(rx + 8, ry, 168, l1y[i], { cls: 's1', width: 1.6 }));
        g.push(txt(176, l1y[i] + 4, t, { cls: 'ink' }));
        const off = [-30, 30];
        bots.forEach((bl, j) => {
            const y2 = l1y[i] + off[j];
            g.push(arw(248, l1y[i], 316, y2, { cls: 's2', width: 1.4 }));
            g.push(txt(324, y2 + 4, bl, { cls: 'ink2', size: 'sm' }));
        });
    });
    g.push(ln([[404, 40], [404, 268]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(txt(430, 96, '첫 단계에서 3가지,', { cls: 'ink' }));
    g.push(txt(430, 120, '그 각각에 대해 2가지.', { cls: 'ink' }));
    g.push(txt(430, 156, '끝의 가지 수는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 184, '3 × 2 = 6', { cls: 'ink bold' }));
    g.push(txt(430, 214, '더하는 것이 아니라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 232, '곱하는 이유가 그림에 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(310, 292, '단계가 이어지면 곱하고, 서로 겹치지 않는 경우가 나란히 놓이면 더한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-count-tree',
        svg: svg({
            width: W, height: H,
            title: '곱의 법칙을 수형도로 본 것',
            desc: '첫 단계 3가지 각각에 대해 두 번째 단계가 2가지씩 붙어 끝의 가지가 6개가 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-2. 순열과 조합 ---- */
add((() => {
    const W = 660, H = 320;
    const rows = [
        { y: 82, perm: ['ㄱㄴ', 'ㄴㄱ'], comb: '{ㄱ, ㄴ}' },
        { y: 152, perm: ['ㄱㄷ', 'ㄷㄱ'], comb: '{ㄱ, ㄷ}' },
        { y: 222, perm: ['ㄴㄷ', 'ㄷㄴ'], comb: '{ㄴ, ㄷ}' },
    ];
    const g = [];
    g.push(txt(158, 48, '줄 세운다 — 6가지', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(520, 48, '순서를 지운다 — 3가지', { anchor: 'middle', cls: 'ink bold' }));
    for (const r of rows) {
        g.push(box(56, r.y - 22, 204, 44, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
        r.perm.forEach((p, i) => {
            const x = 74 + i * 96;
            g.push(box(x, r.y - 14, 78, 28, { fill: 'var(--s1)', op: 0.14, stroke: 'var(--s1)', sw: 1.2, rx: 5 }));
            g.push(txt(x + 39, r.y + 5, p, { anchor: 'middle', cls: 'ink' }));
        });
        g.push(arw(276, r.y, 428, r.y, { cls: 'ark', width: 1.6 }));
        g.push(box(440, r.y - 16, 160, 32, { fill: 'var(--s3)', op: 0.16, stroke: 'var(--s3)', sw: 1.3, rx: 5 }));
        g.push(txt(520, r.y + 5, r.comb, { anchor: 'middle', cls: 'ink' }));
    }
    g.push(txt(352, 142, '순서를', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(352, 162, '지운다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(330, 274, '한 조합마다 2! = 2 개의 순열이 붙는다. 그래서 조합의 수는 순열의 수를 2! 로 나눈 것이다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(330, 300, '6 ÷ 2 = 3', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-int-perm-comb',
        svg: svg({
            width: W, height: H,
            title: '순열과 조합의 차이',
            desc: '세 개에서 두 개를 뽑는 순열 6가지를 순서를 지워 묶으면 조합 3가지가 된다. 한 묶음의 크기가 2! 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-3. 파스칼 삼각형 ---- */
add((() => {
    const W = 660, H = 350;
    const rowsN = 6;
    const cx = 250, y0 = 62, dy = 46, dxc = 54;
    const C = [];
    for (let r = 0; r < rowsN; r += 1) {
        C[r] = [];
        for (let k = 0; k <= r; k += 1) C[r][k] = k === 0 || k === r ? 1 : C[r - 1][k - 1] + C[r - 1][k];
    }
    const px = (r, k) => cx + (k - r / 2) * dxc;
    const py = r => y0 + r * dy;
    const g = [];
    // 4 + 6 = 10 을 잇는 화살표
    g.push(arw(px(4, 1) + 10, py(4) + 12, px(5, 2) - 12, py(5) - 16, { cls: 's2', width: 1.5 }));
    g.push(arw(px(4, 2) - 10, py(4) + 12, px(5, 2) + 12, py(5) - 16, { cls: 's2', width: 1.5 }));
    for (let r = 0; r < rowsN; r += 1) {
        for (let k = 0; k <= r; k += 1) {
            const hot = (r === 4 && (k === 1 || k === 2)) || (r === 5 && k === 2);
            g.push(circ(px(r, k), py(r), 17, {
                fill: hot ? 'var(--s2)' : 'var(--s1)', op: hot ? 0.28 : 0.12,
                stroke: hot ? 'var(--s2)' : 'var(--grid)', sw: hot ? 1.6 : 1,
            }));
            g.push(txt(px(r, k), py(r) + 5, String(C[r][k]), { anchor: 'middle', cls: 'ink' }));
        }
        g.push(txt(498, py(r) + 5, `(a + b)${'⁰¹²³⁴⁵'[r]} 의 계수`, { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(330, 324, '각 수는 바로 위 두 수의 합이다.  4 + 6 = 10 이 그 예다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    return {
        name: 'math-int-pascal',
        svg: svg({
            width: W, height: H,
            title: '파스칼 삼각형과 이항계수',
            desc: '각 수는 바로 위 두 수의 합이며, n 번째 줄이 (a+b)의 n 제곱을 전개한 계수와 같다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-4. 표본공간은 집합이고 사건은 부분집합이다 ---- */
add((() => {
    const W = 660, H = 380;
    const cell = 36, x0 = 96, y0 = 92;
    const g = [];
    const inA = (i, j) => i + j + 2 === 8;     // 두 눈의 합이 8
    const inB = (i, j) => i === j;             // 두 눈이 같다
    for (let i = 0; i < 6; i += 1) {
        for (let j = 0; j < 6; j += 1) {
            const x = x0 + j * cell, y = y0 + i * cell;
            const a = inA(i, j), b = inB(i, j);
            const fill = a && b ? 'var(--s3)' : a ? 'var(--s1)' : b ? 'var(--s2)' : 'none';
            const op = a && b ? 0.5 : 0.24;
            g.push(box(x, y, cell, cell, { fill, op, stroke: 'var(--grid)', sw: 1, rx: 0 }));
        }
    }
    for (let k = 0; k < 6; k += 1) {
        g.push(txt(x0 + k * cell + cell / 2, y0 - 10, String(k + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 - 10, y0 + k * cell + cell / 2 + 4, String(k + 1), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 + 108, y0 - 32, '둘째 주사위', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(x0 - 24, y0 + 108, '첫째', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(x0 - 24, y0 + 126, '주사위', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(50, 42, '표본공간 S — 칸 36개', { cls: 'ink bold' }));

    const lx = 350;
    const item = (y, col, op, t1, t2) => box(lx, y - 11, 16, 16, { fill: col, op, stroke: col, sw: 1.2, rx: 3 })
        + txt(lx + 26, y, t1, { cls: 'ink' })
        + (t2 ? txt(lx + 26, y + 19, t2, { cls: 'ink2', size: 'sm' }) : '');
    g.push(item(104, 'var(--s1)', 0.24, 'A = 두 눈의 합이 8', '칸 5개'));
    g.push(item(158, 'var(--s2)', 0.24, 'B = 두 눈이 같다', '칸 6개'));
    g.push(item(212, 'var(--s3)', 0.5, 'A ∩ B = (4, 4)', '칸 1개'));
    g.push(ln([[lx, 244], [640, 244]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(lx, 270, '|A ∪ B| = 5 + 6 − 1 = 10', { cls: 'ink' }));
    g.push(txt(lx, 296, 'P(A ∪ B) = 10/36', { cls: 'ink bold' }));
    g.push(txt(lx, 322, '4장의 포함배제 원리를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(lx, 340, '36으로 나눈 것이 전부다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-sample-space',
        svg: svg({
            width: W, height: H,
            title: '주사위 두 개의 표본공간과 두 사건',
            desc: '36칸이 표본공간이고 사건은 그중 일부 칸을 모은 부분집합이다. 겹친 칸을 한 번만 세는 것이 포함배제 원리다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-5. 확률의 덧셈정리 ---- */
add((() => {
    const W = 580, H = 320;
    const g = [];
    g.push(box(80, 48, 400, 190, { stroke: 'var(--ink2)', sw: 1.4, rx: 6 }));
    g.push(txt(92, 68, 'S', { cls: 'ink bold' }));
    g.push(circ(240, 145, 76, { fill: 'var(--s1)', op: 0.22, stroke: 'var(--s1)', sw: 1.6 }));
    g.push(circ(336, 145, 76, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.6 }));
    g.push(txt(190, 98, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(388, 98, 'B', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(288, 150, 'A ∩ B', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(288, 216, 288, 176, { cls: 'ark', width: 1.4 }));
    g.push(txt(288, 232, '이 부분이 두 번 세어진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(290, 274, 'P(A ∪ B) = P(A) + P(B) − P(A ∩ B)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(290, 300, '4장 포함배제 원리를 그대로 확률로 옮긴 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-prob-venn',
        svg: svg({
            width: W, height: H,
            title: '확률의 덧셈정리',
            desc: '두 사건의 확률을 그냥 더하면 겹친 부분을 두 번 세게 되므로 한 번 빼 준다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-6. 조건부확률 — 세상이 B 로 줄어든다 ---- */
add((() => {
    const W = 680, H = 310;
    const g = [];
    // 왼쪽: 원래 표본공간
    g.push(txt(160, 48, '아무것도 모를 때', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(46, 66, 232, 150, { stroke: 'var(--ink2)', sw: 1.3, rx: 6 }));
    g.push(txt(56, 84, 'S', { cls: 'ink2', size: 'sm' }));
    g.push(circ(126, 145, 54, { fill: 'var(--s1)', op: 0.2, stroke: 'var(--s1)', sw: 1.5 }));
    g.push(circ(196, 145, 54, { fill: 'var(--s2)', op: 0.2, stroke: 'var(--s2)', sw: 1.5 }));
    g.push(txt(90, 106, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(232, 106, 'B', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(160, 236, 'P(A) = A 의 크기 ÷ S 의 크기', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(298, 145, 366, 145, { cls: 'ark', width: 1.8 }));
    g.push(txt(332, 122, 'B 가', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(332, 176, '일어났다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 줄어든 표본공간
    g.push(txt(524, 48, 'B 가 일어난 것을 안 뒤', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(400, 66, 248, 150, { stroke: 'var(--grid)', sw: 1, rx: 6, dash: '5 4' }));
    g.push(circ(524, 145, 62, { fill: 'var(--s2)', op: 0.2, stroke: 'var(--s2)', sw: 1.8 }));
    g.push(`<path d="M481 101 A62 62 0 0 0 481 189 A54 54 0 0 0 481 101 Z" fill="var(--s3)" fill-opacity="0.42" stroke="none"/>`);
    g.push(txt(590, 106, 'B', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(474, 150, 'A ∩ B', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(524, 236, '새 전체집합은 B 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(340, 278, 'P(A | B) = P(A ∩ B) ÷ P(B)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(340, 300, '분모가 S 에서 B 로 바뀐 것이 조건부확률의 전부다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-conditional',
        svg: svg({
            width: W, height: H,
            title: '조건부확률 — 전체집합이 B 로 줄어든다',
            desc: 'B 가 일어났음을 알면 가능한 결과가 B 안으로 제한되므로 확률의 분모가 S 에서 B 로 바뀐다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-7. 베이즈 정리 — 사람 수로 세어 본다 ---- */
add((() => {
    const W = 720, H = 350;
    const g = [];
    const bx = (x, y, w, h, t1, t2, col, op) => box(x, y, w, h, { fill: col ?? 'none', op: op ?? 0.18, stroke: col ?? 'var(--ink2)', sw: 1.4, rx: 5 })
        + txt(x + w / 2, y + (t2 ? 20 : h / 2 + 5), t1, { anchor: 'middle', cls: 'ink' })
        + (t2 ? txt(x + w / 2, y + 38, t2, { anchor: 'middle', cls: 'ink bold' }) : '');
    g.push(bx(28, 128, 132, 56, '검사받는 사람', '10000명'));
    g.push(bx(232, 46, 124, 56, '병이 있다', '100명', 'var(--s1)'));
    g.push(bx(232, 214, 124, 56, '병이 없다', '9900명', 'var(--s3)'));
    g.push(bx(452, 20, 124, 46, '양성  99명', null, 'var(--s2)', 0.3));
    g.push(bx(452, 78, 124, 46, '음성  1명', null));
    g.push(bx(452, 188, 124, 46, '양성  495명', null, 'var(--s2)', 0.3));
    g.push(bx(452, 246, 124, 46, '음성  9405명', null));
    g.push(arw(164, 150, 228, 82, { cls: 's1', width: 1.5 }));
    g.push(arw(164, 162, 228, 234, { cls: 's3', width: 1.5 }));
    g.push(arw(360, 66, 448, 46, { cls: 's2', width: 1.4 }));
    g.push(arw(360, 80, 448, 100, { cls: 'ark', width: 1.4 }));
    g.push(arw(360, 228, 448, 210, { cls: 's2', width: 1.4 }));
    g.push(arw(360, 242, 448, 266, { cls: 'ark', width: 1.4 }));
    g.push(txt(186, 88, '1%', { cls: 'ink2', size: 'sm' }));
    g.push(txt(186, 208, '99%', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 36, '99%', { cls: 'ink2', size: 'sm' }));
    g.push(txt(396, 104, '1%', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 198, '5%', { cls: 'ink2', size: 'sm' }));
    g.push(txt(396, 272, '95%', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[600, 30], [600, 240]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
    g.push(txt(612, 116, '양성은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(612, 136, '모두', { cls: 'ink2', size: 'sm' }));
    g.push(txt(612, 158, '594명', { cls: 'ink bold' }));
    g.push(txt(360, 320, '양성 594명 가운데 실제로 병이 있는 사람은 99명뿐이다.  99 ÷ 594 ≈ 0.17', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 342, '검사가 99% 맞는데도 양성의 대부분이 건강한 사람인 이유는 건강한 사람이 훨씬 많기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-bayes-tree',
        svg: svg({
            width: W, height: H,
            title: '베이즈 정리를 사람 수로 풀어 본 것',
            desc: '유병률 1퍼센트인 병을 민감도 99퍼센트 검사로 걸러도 양성 594명 중 실제 환자는 99명뿐이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-8. 이항분포 ---- */
add((() => {
    const W = 660, H = 340;
    const nTrial = 10, p = 0.3;
    const comb = (a, b) => { let r = 1; for (let i = 1; i <= b; i += 1) r = (r * (a - b + i)) / i; return Math.round(r); };
    const pr = k => comb(nTrial, k) * p ** k * (1 - p) ** (nTrial - k);
    const fr = frame({ xRange: [-0.8, 10.8], yRange: [0, 0.3], box: { x: 68, y: 56, w: 430, h: 200 } });
    const g = [fr.axes({ xLabel: 'k', yTicks: [0.1, 0.2, 0.3], xTicks: [], grid: false })];
    const halfW = (fr.X(1) - fr.X(0)) * 0.36;
    for (let k = 0; k <= nTrial; k += 1) {
        const v = pr(k);
        g.push(box(fr.X(k) - halfW, fr.Y(v), halfW * 2, fr.Y(0) - fr.Y(v), {
            fill: 'var(--s1)', op: 0.35, stroke: 'var(--s1)', sw: 1.2, rx: 2,
        }));
        g.push(txt(fr.X(k), fr.Y(0) + 17, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const mu = nTrial * p, sd = Math.sqrt(nTrial * p * (1 - p));
    g.push(ln([[fr.X(mu), fr.Y(0)], [fr.X(mu), fr.Y(0.285)]], { stroke: 'var(--s2)', sw: 1.8, dash: '6 4' }));
    g.push(txt(fr.X(mu) + 6, fr.Y(0.285) - 6, 'μ = np = 3', { cls: 'ink' }));
    g.push(arw2(fr.X(mu), fr.Y(0.062), fr.X(mu + sd), fr.Y(0.062), { cls: 's2', width: 1.4 }));
    g.push(txt(fr.X(mu + sd) + 8, fr.Y(0.062) + 4, 'σ ≈ 1.45', { cls: 'ink', size: 'sm' }));
    g.push(txt(524, 84, 'n = 10 번 던지고', { cls: 'ink' }));
    g.push(txt(524, 106, '한 번의 성공 확률이', { cls: 'ink' }));
    g.push(txt(524, 128, 'p = 0.3 일 때', { cls: 'ink' }));
    g.push(txt(524, 164, '성공 횟수 k 의 분포', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 200, 'μ = np', { cls: 'ink bold' }));
    g.push(txt(524, 224, 'σ² = np(1 − p)', { cls: 'ink bold' }));
    g.push(txt(330, 300, '막대의 높이를 모두 더하면 정확히 1 이다. 이것이 확률분포라는 말의 뜻이다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(330, 324, 'p 가 0.5 보다 작으면 왼쪽으로 치우치고, n 이 커지면 종 모양에 가까워진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-binomial',
        svg: svg({
            width: W, height: H,
            title: '이항분포 — 10번 시행에서 성공 횟수의 분포',
            desc: '성공 확률 0.3 으로 10번 시행할 때 성공 횟수별 확률을 막대로 그린 것. 평균은 3, 표준편차는 약 1.45 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-9. 밀도함수 — 한 점의 확률이 0 인 이유 ---- */
add((() => {
    const W = 700, H = 350;
    const f = x => Math.exp(-((x - 3) ** 2) / 2) / Math.sqrt(2 * PI);
    const mk = bx => frame({ xRange: [-0.2, 6.4], yRange: [0, 0.47], box: { x: bx, y: 74, w: 250, h: 165 } });
    const L = mk(58), R = mk(392);
    const x0 = 3.5;
    const g = [
        txt(183, 52, '구간의 확률 = 넓이', { anchor: 'middle', cls: 'ink bold' }),
        underArea(L, f, 2, 4, 'var(--s1)', 0.28),
        L.axes({ xLabel: 'x', xTicks: [0, 2, 4, 6], yTicks: [], grid: false }),
        L.curve(f, { from: -0.1, to: 6.3, cls: 's2' }),
        L.label([3, 0.12], 'P(2 ≤ X ≤ 4)', { anchor: 'middle', cls: 'ink', size: 'sm' }),

        txt(517, 52, '한 점으로 좁혀 가면', { anchor: 'middle', cls: 'ink bold' }),
        underArea(R, f, x0 - 0.5, x0 + 0.5, 'var(--s1)', 0.16),
        underArea(R, f, x0 - 0.2, x0 + 0.2, 'var(--s1)', 0.3),
        underArea(R, f, x0 - 0.05, x0 + 0.05, 'var(--s2)', 0.75),
        R.axes({ xLabel: 'x', xTicks: [0, 2, 4, 6], yTicks: [], grid: false }),
        R.curve(f, { from: -0.1, to: 6.3, cls: 's2' }),
        R.guide([x0, 0], [x0, 0.44]),
        txt(R.X(x0), 68, 'x~0', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        arw(R.X(x0) + 74, R.Y(0.20), R.X(x0) + 16, R.Y(0.20), { cls: 'ark', width: 1.4 }),
        txt(R.X(x0) + 78, R.Y(0.20) + 4, '너비 → 0', { cls: 'ink', size: 'sm' }),

        ln([[58, 272], [642, 272]], { stroke: 'var(--grid)', sw: 1 }),
        txt(350, 298, '너비가 0 이면 넓이도 0 이다. 그래서 P(X = x~0) = 0 이다', { anchor: 'middle', cls: 'ink bold' }),
        txt(350, 324, '그런데도 X 를 관측하면 반드시 어떤 값 하나가 나온다. 확률 0 은 ‘불가능’이 아니다', { anchor: 'middle', cls: 'ink' }),
    ].join('');
    return {
        name: 'math-int-pdf-point',
        svg: svg({
            width: W, height: H,
            title: '확률밀도함수에서 한 점의 확률이 0 인 까닭',
            desc: '구간의 확률은 곡선 아래 넓이이고 구간을 한 점으로 좁히면 넓이가 0 이 된다. 그래도 관측하면 값 하나는 반드시 나온다',
            body: BG + g,
        }),
    };
})());

/* ---- 12-10. 정규분포와 표준편차 ---- */
add((() => {
    const W = 700, H = 360;
    const f = z => Math.exp(-(z * z) / 2) / Math.sqrt(2 * PI);
    const fr = frame({ xRange: [-3.9, 3.9], yRange: [0, 0.46], box: { x: 70, y: 46, w: 560, h: 185 } });
    const g = [
        underArea(fr, f, -3, 3, 'var(--s1)', 0.1),
        underArea(fr, f, -2, 2, 'var(--s1)', 0.12),
        underArea(fr, f, -1, 1, 'var(--s1)', 0.22),
        fr.axes({ xTicks: [], yTicks: [], grid: false }),
        fr.curve(f, { from: -3.85, to: 3.85, cls: 's2' }),
    ];
    const names = ['μ−3σ', 'μ−2σ', 'μ−σ', 'μ', 'μ+σ', 'μ+2σ', 'μ+3σ'];
    for (let i = -3; i <= 3; i += 1) {
        g.push(ln([[fr.X(i), fr.Y(0)], [fr.X(i), fr.Y(0) + 6]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(fr.X(i), fr.Y(0) + 22, names[i + 3], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i !== 0) g.push(ln([[fr.X(i), fr.Y(0)], [fr.X(i), fr.Y(f(i))]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    }
    const bracket = (z, y, label) => {
        const xa = fr.X(-z), xb = fr.X(z);
        return ln([[xa, y - 6], [xa, y], [xb, y], [xb, y - 6]], { stroke: 'var(--s3)', sw: 1.4 })
            + txt((xa + xb) / 2, y + 17, label, { anchor: 'middle', cls: 'ink' });
    };
    g.push(bracket(1, 264, '약 68%'));
    g.push(bracket(2, 300, '약 95%'));
    g.push(bracket(3, 336, '약 99.7%'));
    g.push(txt(fr.X(0), fr.Y(0.42) - 6, '평균에서 가장 높다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(fr.X(-2.9), fr.Y(0.30), '좌우 대칭', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-normal-sigma',
        svg: svg({
            width: W, height: H,
            title: '정규분포에서 표준편차가 차지하는 범위',
            desc: '평균에서 표준편차 1배 안쪽에 약 68퍼센트, 2배 안쪽에 약 95퍼센트, 3배 안쪽에 약 99.7퍼센트가 들어간다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 12-11. 큰 수의 법칙 ---- */
add((() => {
    const W = 730, H = 320;
    const N = 300;
    const fr = frame({ xRange: [0, N], yRange: [0, 1], box: { x: 66, y: 46, w: 460, h: 200 } });
    const g = [fr.axes({ xLabel: '던진 횟수', xTicks: [0, 100, 200, 300], yTicks: [0, 0.5, 1], grid: false })];
    const seeds = [20260812, 777001, 31415926];
    const cls = ['s1', 's2', 's3'];
    seeds.forEach((sd, idx) => {
        const rand = rng(sd);
        const pts = [];
        let heads = 0;
        for (let i = 1; i <= N; i += 1) {
            if (rand() < 0.5) heads += 1;
            pts.push([i, heads / i]);
        }
        g.push(fr.line(pts, { cls: cls[idx] }));
    });
    g.push(ln([[fr.X(0), fr.Y(0.5)], [fr.X(N) + 6, fr.Y(0.5)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '6 4' }));
    g.push(txt(fr.X(N) + 12, fr.Y(0.5) + 4, '0.5', { cls: 'ink' }));
    g.push(txt(66, 30, '앞면이 나온 비율', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 96, '세 번 따로 해 본 것.', { cls: 'ink' }));
    g.push(txt(566, 122, '처음에는 크게 흔들리다가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 140, '횟수가 늘수록 0.5 근처로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 158, '모여든다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 286, '‘확률 0.5’ 가 뜻하는 것은 두 번에 한 번씩 나온다는 것이 아니라 이 그림의 수렴이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(365, 310, '어느 순간에도 정확히 0.5 가 되리라는 보장은 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-int-lln',
        svg: svg({
            width: W, height: H,
            title: '큰 수의 법칙 — 상대도수가 확률로 모여든다',
            desc: '동전 던지기를 세 번 따로 시뮬레이션한 것. 횟수가 늘수록 앞면의 비율이 0.5 근처로 좁혀진다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
