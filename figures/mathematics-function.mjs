/**
 * 기초수학 6장(수 체계)과 7장(함수)의 그림.
 *
 * 이름은 전부 `math-fn-` 으로 시작한다(담당 B 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 그래서 첨자는 lib 의 `a~1` 표기를, 나머지는 유니코드(√, π, θ, ℝ, ˣ)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰면 안 되고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 */
import { svg, frame, arc, txt } from './lib.mjs';

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

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 채운 점. */
const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 빈 점 — 그 자리에 수가 없다는 뜻. 안쪽을 배경색으로 채워야 밑줄이 비친다. */
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

/** 직각 표시. */
const rightAngle = (x, y, dx, dy, s = 10) =>
    `<path class="gr" fill="none" d="M${x + dx * s} ${y} L${x + dx * s} ${y + dy * s} L${x} ${y + dy * s}"/>`;

/* ================================================================== *
 * 6장 — 수 체계
 * ================================================================== */

/* ---- 6-1. 네 개의 수직선 ---- */
add((() => {
    const W = 660, H = 330;
    const X = v => r2(340 + v * 62);
    const g = [];
    const rows = [
        { y: 70, name: 'ℕ  자연수 — 세는 수. 띄엄띄엄 놓여 있다' },
        { y: 142, name: 'ℤ  정수 — 0 과 음수가 붙었다. 여전히 띄엄띄엄하다' },
        { y: 220, name: 'ℚ  유리수 — 촘촘하다. 그런데도 빈 곳이 남는다' },
        { y: 288, name: 'ℝ  실수 — 빈 곳 없이 이어진다' },
    ];
    for (const r of rows) {
        g.push(txt(58, r.y - 16, r.name, { cls: 'ink2', size: 'sm' }));
        g.push(ln([[X(-4.35), r.y], [X(4.35), r.y]], { stroke: 'var(--grid)', sw: 1.2 }));
    }
    for (let k = 1; k <= 4; k += 1) g.push(pdot(X(k), rows[0].y, 'var(--s1)', 4));
    for (let k = -4; k <= 4; k += 1) g.push(pdot(X(k), rows[1].y, 'var(--s1)', 4));
    const seen = new Set();
    for (let q = 1; q <= 6; q += 1) {
        for (let p = -4 * q; p <= 4 * q; p += 1) {
            const key = r2(p / q);
            if (seen.has(key)) continue;
            seen.add(key);
            g.push(pdot(X(p / q), rows[2].y, 'var(--s1)', 1.7));
        }
    }
    g.push(ln([[X(-4.3), rows[3].y], [X(4.3), rows[3].y]], { stroke: 'var(--s1)', sw: 5 }));
    for (let k = -4; k <= 4; k += 1) {
        g.push(ln([[X(k), rows[3].y + 6], [X(k), rows[3].y + 11]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(k), rows[3].y + 25, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const s2x = X(Math.SQRT2);
    g.push(ln([[s2x, rows[2].y - 26], [s2x, rows[2].y - 7]], { stroke: 'var(--s2)', sw: 1.4, dash: '4 3' }));
    g.push(odot(s2x, rows[2].y, 'var(--s2)', 5));
    g.push(txt(s2x + 8, rows[2].y - 28, '√2 자리에는 유리수가 없다', { cls: 'ink', size: 'sm' }));
    return {
        name: 'math-fn-number-line',
        svg: svg({
            width: W, height: H,
            title: '수 체계가 넓어지는 모습을 수직선으로 본 것',
            desc: '자연수는 띄엄띄엄, 정수는 양쪽으로, 유리수는 촘촘하지만 √2 자리가 비어 있고, 실수는 빈틈이 없다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-2. 유리수의 조밀성 ---- */
add((() => {
    const W = 640, H = 240;
    const X = v => r2(72 + v * 496);
    const yb = 180;
    const g = [];
    g.push(txt(66, 36, '두 유리수 사이의 중점도 유리수다. 그 중점과 a 사이에 또 중점이 있다.', { cls: 'ink' }));
    g.push(txt(66, 56, '이 과정은 끝나지 않는다 — 아무리 좁은 구간에도 유리수가 무한히 많다.', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[X(-0.02), yb], [X(1.03), yb]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (const t of [0, 1]) {
        g.push(ln([[X(t), yb], [X(t), yb + 7]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(txt(X(t), yb + 24, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const a = 0.18, b = 0.86;
    const m1 = (a + b) / 2, m2 = (a + m1) / 2, m3 = (a + m2) / 2;
    const steps = [[b, m1, 74], [m1, m2, 48], [m2, m3, 26]];
    for (const [right, mid, h] of steps) {
        const x1 = X(a), x2 = X(right);
        g.push(`<path d="M${x1} ${yb - 5} Q ${r2((x1 + x2) / 2)} ${yb - h} ${x2} ${yb - 5}" fill="none" stroke="var(--s2)" stroke-width="1.4" stroke-dasharray="4 3"/>`);
        g.push(pdot(X(mid), yb, 'var(--s2)', 4));
    }
    g.push(pdot(X(a), yb, 'var(--s1)', 5));
    g.push(pdot(X(b), yb, 'var(--s1)', 5));
    g.push(txt(X(a), yb + 24, 'a', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X(b), yb + 24, 'b', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X(m1), yb - 82, '(a+b)/2', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'math-fn-rational-dense',
        svg: svg({
            width: W, height: H,
            title: '유리수의 조밀성 — 두 유리수 사이에 늘 또 다른 유리수가 있다',
            desc: '구간을 절반씩 계속 자르는 그림. 중점을 취하는 일이 끝나지 않으므로 유리수는 조밀하다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-3. √2 를 수직선에 옮기기 ---- */
add((() => {
    const W = 580, H = 320;
    const U = 106, y0 = 258, x0 = 92;
    const X = v => r2(x0 + v * U);
    const g = [];
    g.push(txt(52, 38, '한 변이 1 인 정사각형의 대각선을 컴퍼스로 수직선에 옮긴 것.', { cls: 'ink' }));
    g.push(txt(52, 58, '자와 컴퍼스로 찍을 수 있는 점인데 유리수가 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[X(-0.3), y0], [X(3.4), y0]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (let k = 0; k <= 3; k += 1) {
        g.push(ln([[X(k), y0], [X(k), y0 + 7]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(txt(X(k), y0 + 24, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(box(X(0), y0 - U, U, U, { fill: 'var(--s1)', op: 0.1, stroke: 'var(--s1)', sw: 1.8, rx: 0 }));
    g.push(ln([[X(0), y0], [X(1), y0 - U]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(txt(X(0.5), y0 + 20, '1', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X(1) + 9, y0 - U / 2 + 4, '1', { cls: 'ink' }));
    g.push(txt(X(0.33), y0 - U * 0.55, '√2', { anchor: 'middle', cls: 'ink bold' }));
    g.push(rightAngle(X(1), y0, -1, -1, 12));
    const R = U * Math.SQRT2;
    g.push(`<path d="M${X(1)} ${y0 - U} A ${r2(R)} ${r2(R)} 0 0 1 ${r2(x0 + R)} ${y0}" fill="none" stroke="var(--s2)" stroke-width="1.4" stroke-dasharray="5 4"/>`);
    g.push(pdot(x0 + R, y0, 'var(--s2)', 5));
    g.push(ln([[x0 + R, y0 - 4], [x0 + R, y0 - 42]], { stroke: 'var(--s2)', sw: 1.2, dash: '4 3' }));
    g.push(txt(x0 + R + 10, y0 - 48, '√2 = 1.41421356…', { cls: 'ink bold' }));
    g.push(txt(x0 + R + 10, y0 - 30, '분수로는 결코 적을 수 없다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-sqrt2-diagonal',
        svg: svg({
            width: W, height: H,
            title: '한 변 1 인 정사각형의 대각선이 √2 이고 그 점이 수직선 위에 있다',
            desc: '피타고라스 정리로 대각선 길이가 √2 임을 얻고 컴퍼스로 그 길이를 수직선에 옮겨 √2 의 자리를 찍는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-4. 유리수의 구멍과 실수의 완비성 ---- */
add((() => {
    const W = 640, H = 280;
    const xa = 92, xb = 556;
    const X = v => r2(xa + ((v - 1.0) / 0.8) * (xb - xa));
    const s2x = X(Math.SQRT2);
    const g = [];
    const rows = [
        { y: 112, title: '유리수만 있는 세계', note: '가른 자리에 해당하는 수가 없다 — 구멍이 남는다', open: true },
        { y: 220, title: '실수', note: '어떻게 갈라도 경계에 반드시 수가 있다 — 완비성', open: false },
    ];
    for (const r of rows) {
        g.push(txt(xa - 12, r.y - 46, r.title, { cls: 'ink bold' }));
        g.push(txt(xa - 12, r.y - 28, r.note, { cls: 'ink2', size: 'sm' }));
        g.push(ln([[xa - 12, r.y], [s2x - 7, r.y]], { stroke: 'var(--s1)', sw: 5, cap: 'butt' }));
        g.push(ln([[s2x + 7, r.y], [xb + 12, r.y]], { stroke: 'var(--s2)', sw: 5, cap: 'butt' }));
        if (r.open) g.push(odot(s2x, r.y, 'var(--ink2)', 6));
        else g.push(pdot(s2x, r.y, 'var(--ink)', 5.5));
        g.push(txt(xa - 12, r.y + 24, '제곱하면 2 보다 작은 쪽', { cls: 'ink2', size: 'sm' }));
        g.push(txt(xb + 12, r.y + 24, '2 보다 큰 쪽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(ln([[s2x, rows[0].y + 12], [s2x, rows[1].y - 14]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(txt(s2x + 11, rows[1].y - 8, '√2', { cls: 'ink bold' }));
    return {
        name: 'math-fn-real-gap',
        svg: svg({
            width: W, height: H,
            title: '유리수를 두 무리로 가르면 경계에 수가 없을 수 있다',
            desc: '제곱이 2 보다 작은 유리수와 큰 유리수로 가르면 유리수 안에는 경계가 없다. 실수에서는 경계점 √2 가 존재한다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-5. 절댓값은 거리다 ---- */
add((() => {
    const W = 640, H = 280;
    const X = v => r2(322 + v * 56);
    const g = [];
    const yA = 116;
    g.push(txt(58, 40, '절댓값은 거리다 — |x − a| 는 수직선에서 x 와 a 사이의 거리', { cls: 'ink' }));
    g.push(ln([[X(-4.4), yA], [X(4.4), yA]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (let k = -4; k <= 4; k += 1) {
        g.push(ln([[X(k), yA], [X(k), yA + 6]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(k), yA + 22, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(pdot(X(-1), yA, 'var(--s1)', 5));
    g.push(pdot(X(3), yA, 'var(--s2)', 5));
    g.push(txt(X(-1), yA - 14, 'a = −1', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X(3), yA - 14, 'x = 3', { anchor: 'middle', cls: 'ink' }));
    g.push(ln([[X(-1), yA - 36], [X(3), yA - 36]], { stroke: 'var(--s3)', sw: 2 }));
    g.push(ln([[X(-1), yA - 42], [X(-1), yA - 30]], { stroke: 'var(--s3)', sw: 2 }));
    g.push(ln([[X(3), yA - 42], [X(3), yA - 30]], { stroke: 'var(--s3)', sw: 2 }));
    g.push(txt(X(1), yA - 46, '|3 − (−1)| = 4', { anchor: 'middle', cls: 'ink bold' }));
    const yB = 220;
    g.push(txt(58, 176, '그래서 |x − a| < r 은 ‘a 에서 r 보다 가까운 점’, 곧 하나의 구간이다', { cls: 'ink' }));
    g.push(ln([[X(-4.4), yB], [X(4.4), yB]], { stroke: 'var(--ink2)', sw: 1.5 }));
    g.push(ln([[X(-3), yB], [X(1), yB]], { stroke: 'var(--s1)', sw: 5, cap: 'butt' }));
    g.push(odot(X(-3), yB, 'var(--s1)', 5.5));
    g.push(odot(X(1), yB, 'var(--s1)', 5.5));
    g.push(pdot(X(-1), yB, 'var(--ink)', 4));
    g.push(txt(X(-1), yB + 22, 'a', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X(-3), yB + 22, 'a − r', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X(1), yB + 22, 'a + r', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(X(-1), yB - 20, X(-3) + 4, yB - 20, { cls: 's2', width: 1.6 }));
    g.push(arw(X(-1), yB - 20, X(1) - 4, yB - 20, { cls: 's2', width: 1.6 }));
    // 화살표는 가운데 a 에서 양쪽으로 하나씩이다. 라벨을 가운데 하나만 두면
    // 두 화살표가 길이 2r 짜리 하나로 읽히므로 반쪽마다 붙인다.
    g.push(txt(X(-2), yB - 28, 'r', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X(0), yB - 28, 'r', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-abs-distance',
        svg: svg({
            width: W, height: H,
            title: '절댓값을 거리로 읽으면 절댓값 부등식이 구간이 된다',
            desc: '위는 두 점 사이 거리로서의 절댓값, 아래는 a 를 중심으로 반지름 r 인 구간',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-6. 복소평면 ---- */
add((() => {
    const W = 520, H = 400;
    const f = frame({ xRange: [-1.6, 4.2], yRange: [-3.2, 3.2], box: { x: 60, y: 40, w: 400, h: 310 } });
    const g = [];
    g.push(f.axes({ xTicks: [-1, 1, 2, 3, 4], yTicks: [-3, -2, -1, 1, 2, 3], grid: true }));
    g.push(txt(f.X(4.2) + 8, f.Y(0) - 10, '실수부', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0) + 9, f.Y(3.2) - 6, '허수부', { cls: 'ink2', size: 'sm' }));
    g.push(f.guide([3, 0], [3, 2]));
    g.push(f.guide([0, 2], [3, 2]));
    g.push(f.vector([0, 0], [3, 2], { cls: 's1', marker: 'ar1' }));
    g.push(f.dot([3, 2], { cls: 'f1', r: 5 }));
    g.push(f.label([3, 2], 'z = 3 + 2i', { dx: 10, dy: -8, cls: 'ink bold' }));
    g.push(f.guide([3, 0], [3, -2]));
    g.push(f.vector([0, 0], [3, -2], { cls: 's2', marker: 'ar2' }));
    g.push(f.dot([3, -2], { cls: 'f2', r: 5 }));
    g.push(f.label([3, -2], '켤레 3 − 2i', { dx: 10, dy: 16, cls: 'ink bold' }));
    g.push(f.label([1.35, 1.4], '|z| = √13', { cls: 'ink', anchor: 'middle' }));
    g.push(arc(f.X(0), f.Y(0), 34, 0, 33.7, ''));
    g.push(txt(f.X(0) + 42, f.Y(0) - 9, 'θ', { cls: 'ink2', size: 'sm' }));
    g.push(txt(60, 378, '켤레는 실수축에 대한 거울상, |z| 는 원점에서 그 점까지의 거리다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-complex-plane',
        svg: svg({
            width: W, height: H,
            title: '복소평면 위의 z = 3 + 2i 와 그 켤레복소수',
            desc: '가로축이 실수부 세로축이 허수부다. 켤레복소수는 실수축에 대한 대칭점이고 절댓값은 원점에서의 거리다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-7. 극형식과 i 곱하기 ---- */
add((() => {
    const W = 660, H = 344;
    const g = [];
    const fa = frame({ xRange: [-1.5, 1.5], yRange: [-1.5, 1.5], box: { x: 64, y: 80, w: 212, h: 212 } });
    g.push(panel(28, 30, 288, 302, '극형식'));
    g.push(fa.axes({ xTicks: [], yTicks: [], grid: false }));
    const th = 50 * PI / 180, rr = 1.15;
    const zp = [rr * Math.cos(th), rr * Math.sin(th)];
    g.push(fa.vector([0, 0], zp, { cls: 's1', marker: 'ar1' }));
    g.push(fa.dot(zp, { cls: 'f1', r: 4.5 }));
    g.push(fa.guide(zp, [zp[0], 0]));
    g.push(fa.guide(zp, [0, zp[1]]));
    g.push(arc(fa.X(0), fa.Y(0), 40, 0, 50, 'θ'));
    g.push(fa.label(zp, 'z', { dx: 9, dy: -6, cls: 'ink bold' }));
    g.push(fa.label([zp[0] / 2 - 0.1, zp[1] / 2 + 0.18], 'r', { cls: 'ink', anchor: 'middle' }));
    g.push(fa.label([zp[0], 0], 'r cos θ', { dy: 20, cls: 'ink2', anchor: 'middle', size: 'sm' }));
    g.push(fa.label([0, zp[1]], 'r sin θ', { dx: -9, dy: 4, cls: 'ink2', anchor: 'end', size: 'sm' }));
    g.push(txt(172, 318, 'z = r(cos θ + i sin θ)', { anchor: 'middle', cls: 'ink' }));
    const fb = frame({ xRange: [-1.6, 1.6], yRange: [-1.6, 1.6], box: { x: 386, y: 80, w: 212, h: 212 } });
    g.push(panel(344, 30, 288, 302, 'i 를 곱한다'));
    g.push(fb.axes({ xTicks: [1], yTicks: [], grid: false }));
    const z1 = [1.2, 0.5], z2 = [-0.5, 1.2];
    g.push(fb.vector([0, 0], z1, { cls: 's1', marker: 'ar1' }));
    g.push(fb.vector([0, 0], z2, { cls: 's2', marker: 'ar2' }));
    g.push(fb.label(z1, 'z', { dx: 9, dy: 2, cls: 'ink bold' }));
    g.push(fb.label(z2, 'iz', { dx: -7, dy: -9, anchor: 'end', cls: 'ink bold' }));
    g.push(arc(fb.X(0), fb.Y(0), 62, 22.6, 112.6, '90°'));
    g.push(txt(488, 318, '길이는 그대로, 각만 90° 커진다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'math-fn-complex-polar',
        svg: svg({
            width: W, height: H,
            title: '복소수의 극형식과, i 를 곱하는 것이 90도 회전이라는 사실',
            desc: '왼쪽은 길이 r 과 각 θ 로 복소수를 적는 방법, 오른쪽은 i 를 곱하면 길이는 그대로이고 각만 90도 커진다는 것',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 7장 — 함수
 * ================================================================== */

/* ---- 7-1. 평행이동 ---- */
add((() => {
    const W = 620, H = 330;
    const f = frame({ xRange: [-3.2, 5.2], yRange: [-1.2, 7.6], box: { x: 60, y: 40, w: 500, h: 250 } });
    const g = [];
    g.push(f.axes({ xTicks: [-3, -2, -1, 1, 2, 3, 4, 5], yTicks: [2, 4], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(x => x * x, { from: -2.3, to: 2.3, cls: 'ax', dash: '6 4' }));
    g.push(f.curve(x => (x - 2) * (x - 2) + 1, { from: -0.1, to: 4.1, cls: 's1' }));
    g.push(f.dot([0, 0], { cls: 'f2', r: 4.5 }));
    g.push(f.dot([2, 1], { cls: 'f1', r: 4.5 }));
    g.push(f.vector([0.14, 0.07], [1.88, 0.94], { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(f.label([-3.1, 6.6], 'y = x²  원래 그래프', { cls: 'ink2', size: 'sm' }));
    g.push(f.label([4.0, 6.4], 'y = (x − 2)² + 1', { anchor: 'end', cls: 'ink bold' }));
    g.push(f.label([1.6, 0.32], '평행이동', { cls: 'ink', size: 'sm' }));
    g.push(txt(60, 316, 'x 자리에 x − 2 를 넣으면 오른쪽으로 2 간다. 괄호 안의 부호와 이동 방향이 반대인 것이 이 절의 함정이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-graph-shift',
        svg: svg({
            width: W, height: H,
            title: 'x 를 x − p 로 바꾸면 그래프가 오른쪽으로 p 만큼 간다',
            desc: 'y = x² 를 오른쪽 2 위로 1 옮기면 y = (x−2)² + 1 이 된다. 괄호 안의 부호와 이동 방향이 반대다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-2. 우함수와 기함수 ---- */
add((() => {
    const W = 660, H = 330;
    const g = [];
    const RY = [-4.6, 5.2];
    const fa = frame({ xRange: [-2.5, 2.5], yRange: RY, box: { x: 58, y: 92, w: 244, h: 196 } });
    g.push(panel(30, 32, 296, 282, '우함수 — y축 대칭', { sub: 'f(−x) = f(x)' }));
    g.push(fa.axes({ xTicks: [-1, 1], yTicks: [], grid: false }));
    g.push(fa.curve(x => x * x, { from: -1.95, to: 1.95, cls: 's1' }));
    g.push(fa.dot([-1.5, 2.25], { cls: 'f2', r: 4 }));
    g.push(fa.dot([1.5, 2.25], { cls: 'f2', r: 4 }));
    g.push(fa.guide([-1.5, 2.25], [1.5, 2.25]));
    g.push(fa.label([2.35, 4.2], 'y = x²', { anchor: 'end', cls: 'ink' }));
    g.push(fa.label([0, 2.25], '높이가 같다', { dy: -12, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const fb = frame({ xRange: [-2.5, 2.5], yRange: RY, box: { x: 382, y: 92, w: 244, h: 196 } });
    g.push(panel(354, 32, 296, 282, '기함수 — 원점 대칭', { sub: 'f(−x) = −f(x)' }));
    g.push(fb.axes({ xTicks: [-1, 1], yTicks: [], grid: false }));
    g.push(fb.curve(x => x * x * x, { from: -1.63, to: 1.63, cls: 's1' }));
    g.push(fb.dot([-1.4, -2.744], { cls: 'f2', r: 4 }));
    g.push(fb.dot([1.4, 2.744], { cls: 'f2', r: 4 }));
    g.push(fb.guide([-1.4, -2.744], [1.4, 2.744]));
    g.push(fb.label([-2.35, 4.2], '부호가 뒤집힌다', { cls: 'ink2', size: 'sm' }));
    g.push(fb.label([2.35, -4.2], 'y = x³', { anchor: 'end', cls: 'ink' }));
    return {
        name: 'math-fn-even-odd',
        svg: svg({
            width: W, height: H,
            title: '우함수는 y축에 대해, 기함수는 원점에 대해 대칭이다',
            desc: 'x 를 −x 로 바꿨을 때 값이 그대로면 우함수, 부호만 뒤집히면 기함수다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-3. 유리함수의 점근선 ---- */
add((() => {
    const W = 600, H = 380;
    const f = frame({ xRange: [-4, 6], yRange: [-6, 10], box: { x: 66, y: 40, w: 480, h: 296 } });
    const g = [];
    g.push(f.axes({ xTicks: [-4, -2, 2, 4, 6], yTicks: [-6, -4, -2, 4, 6, 8], xLabel: 'x', yLabel: 'y' }));
    const R = x => (2 * x + 1) / (x - 1);
    g.push(f.curve(R, { from: -4, to: 0.625, cls: 's1' }));
    g.push(f.curve(R, { from: 1.375, to: 6, cls: 's1' }));
    g.push(f.line([[1, -6], [1, 9.4]], { cls: 's2', dash: '6 4' }));
    g.push(f.line([[-4, 2], [6, 2]], { cls: 's2', dash: '6 4' }));
    g.push(f.label([1, 10], 'x = 1  수직점근선', { dx: 10, dy: 4, cls: 'ink bold' }));
    g.push(f.label([-3.9, 3.2], 'y = 2  수평점근선', { cls: 'ink bold' }));
    g.push(f.dot([-0.5, 0], { cls: 'f3', r: 4.5 }));
    g.push(f.guide([-0.5, -0.25], [-1.35, -1.55]));
    g.push(f.label([-1.7, -1.9], 'x절편 −1/2', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(f.label([5.9, 4.6], 'y = (2x+1)/(x−1)', { anchor: 'end', cls: 'ink' }));
    g.push(txt(66, 366, '분모가 0 이 되는 x = 1 에서 값이 폭발하고, x 가 커지면 2x/x = 2 에 가까워진다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-rational-asymptote',
        svg: svg({
            width: W, height: H,
            title: '유리함수의 수직점근선과 수평점근선',
            desc: '분모의 근에서 수직점근선이 서고 x 가 무한히 커질 때 다가가는 값이 수평점근선이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-4. 지수함수 세 가지 ---- */
add((() => {
    const W = 620, H = 340;
    const f = frame({ xRange: [-3, 3], yRange: [-0.6, 8.4], box: { x: 62, y: 40, w: 490, h: 258 } });
    const g = [];
    g.push(f.axes({ xTicks: [-3, -2, -1, 1, 2, 3], yTicks: [2, 4, 6, 8], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(x => Math.pow(3, x), { from: -3, to: 1.893, cls: 's3' }));
    g.push(f.curve(x => Math.pow(2, x), { from: -3, to: 3, cls: 's1' }));
    g.push(f.curve(x => Math.pow(0.5, x), { from: -3, to: 3, cls: 's2' }));
    g.push(f.dot([0, 1], { cls: 'f1', r: 4.5 }));
    g.push(f.label([0, 1], '(0, 1)', { dx: -9, dy: 17, anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(f.label([1.8, 8.25], 'y = 3ˣ', { anchor: 'end', cls: 'ink bold' }));
    g.push(f.label([3, 8.25], 'y = 2ˣ', { anchor: 'end', cls: 'ink bold' }));
    g.push(f.label([-3, 8], 'y = (1/2)ˣ', { dx: 6, dy: 7, cls: 'ink bold' }));
    g.push(txt(62, 322, '밑이 무엇이든 (0, 1) 을 지난다. 밑이 1 보다 크면 늘어나고 작으면 줄어들며, 값은 언제나 0 보다 크다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-exp-family',
        svg: svg({
            width: W, height: H,
            title: '밑에 따라 달라지는 지수함수의 모양',
            desc: '2의 x제곱, 3의 x제곱, 2분의 1의 x제곱 그래프. 모두 (0,1)을 지나고 값이 항상 양수다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-5. 지수함수와 로그함수는 y = x 대칭 ---- */
add((() => {
    const W = 430, H = 430;
    const f = frame({ xRange: [-3, 5], yRange: [-3, 5], box: { x: 56, y: 40, w: 340, h: 340 } });
    const g = [];
    g.push(f.axes({ xTicks: [-2, 2, 4], yTicks: [-2, 2, 4], xLabel: 'x', yLabel: 'y' }));
    g.push(f.line([[-2.8, -2.8], [4.8, 4.8]], { cls: 'ax', dash: '5 4' }));
    g.push(f.label([4.55, 4.55], 'y = x', { dx: -4, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(f.curve(x => Math.pow(2, x), { from: -3, to: 2.32, cls: 's1' }));
    g.push(f.curve(x => Math.log2(x), { from: 0.13, to: 5, cls: 's2' }));
    g.push(f.dot([2, 4], { cls: 'f1', r: 4.5 }));
    g.push(f.dot([4, 2], { cls: 'f2', r: 4.5 }));
    g.push(f.guide([2, 4], [4, 2]));
    g.push(f.label([2, 4], '(2, 4)', { dx: -9, dy: -8, anchor: 'end', cls: 'ink' }));
    g.push(f.label([4, 2], '(4, 2)', { dx: 9, dy: 4, cls: 'ink' }));
    g.push(f.label([1.2, 4.6], 'y = 2ˣ', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.label([4.7, 0.9], 'y = log₂x', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(56, 412, '좌표를 뒤집으면 다른 곡선 위의 점이 된다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-exp-log-mirror',
        svg: svg({
            width: W, height: H,
            title: '로그함수는 지수함수를 직선 y = x 에 대해 뒤집은 것이다',
            desc: '2의 x제곱 그래프와 밑이 2인 로그 그래프가 y = x 에 대해 대칭이고 점 (2,4)와 (4,2)가 서로 대응한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-6. 라디안 ---- */
add((() => {
    const W = 660, H = 330;
    const g = [];
    const R = 88, cx = 168, cy = 182;
    g.push(panel(28, 32, 288, 272, '1 라디안의 정의'));
    g.push(circ(cx, cy, R, { stroke: 'var(--grid)', sw: 1.4 }));
    const P = a => [cx + R * Math.cos(a), cy - R * Math.sin(a)];
    g.push(ln([[cx, cy], P(0)], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(ln([[cx, cy], P(1)], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(`<path d="M${r2(P(0)[0])} ${r2(P(0)[1])} A ${R} ${R} 0 0 0 ${r2(P(1)[0])} ${r2(P(1)[1])}" fill="none" stroke="var(--s2)" stroke-width="4"/>`);
    g.push(arc(cx, cy, 32, 0, 57.3, '1 rad'));
    g.push(txt(cx + R * 0.55, cy + 20, 'r', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(168, 80, '호의 길이 = r', { anchor: 'middle', cls: 'ink bold' }));
    g.push(arw(206, 86, P(0.55)[0] - 5, P(0.55)[1] - 5, { cls: 's2', width: 1.4 }));
    g.push(txt(172, 292, '반지름과 호의 길이가 같아지는 각 ≈ 57.3°', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const dx = 488, dy = 182, R2 = 84;
    g.push(panel(344, 32, 288, 272, '한 바퀴는 2π 라디안'));
    g.push(circ(dx, dy, R2, { stroke: 'var(--grid)', sw: 1.4 }));
    g.push(circ(dx, dy, R2 - 24, { stroke: 'var(--s2)', sw: 2, dash: '6 4' }));
    const marks = [
        { a: 0, s: '0° = 0', ax: 'start', ddx: 10, ddy: 5 },
        { a: PI / 2, s: '90° = π/2', ax: 'middle', ddx: 0, ddy: -12 },
        { a: PI, s: '180° = π', ax: 'end', ddx: -10, ddy: 5 },
        { a: 3 * PI / 2, s: '270° = 3π/2', ax: 'middle', ddx: 0, ddy: 22 },
    ];
    for (const m of marks) {
        const x = dx + R2 * Math.cos(m.a), y = dy - R2 * Math.sin(m.a);
        g.push(pdot(x, y, 'var(--s1)', 4));
        g.push(txt(x + m.ddx, y + m.ddy, m.s, { anchor: m.ax, cls: 'ink', size: 'sm' }));
    }
    g.push(txt(dx, dy + 5, '2π = 360°', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-fn-radian',
        svg: svg({
            width: W, height: H,
            title: '라디안은 호의 길이를 반지름으로 잰 각이다',
            desc: '왼쪽은 호의 길이가 반지름과 같아지는 각이 1 라디안이라는 정의, 오른쪽은 한 바퀴가 2π 라디안이라는 환산',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-7. 직각삼각형에서 단위원으로 ---- */
add((() => {
    const W = 680, H = 348;
    const g = [];
    g.push(panel(24, 30, 300, 286, '5장 — 직각삼각형의 비', { sub: '0° 와 90° 사이에서만 뜻이 있다' }));
    const A = [76, 256], B = [268, 256], C = [268, 116];
    g.push(`<path d="M${A[0]} ${A[1]} L${B[0]} ${B[1]} L${C[0]} ${C[1]} Z" fill="var(--s1)" fill-opacity="0.1" stroke="var(--s1)" stroke-width="2"/>`);
    g.push(rightAngle(B[0], B[1], -1, -1, 13));
    g.push(arc(A[0], A[1], 40, 0, 36, 'θ'));
    g.push(txt(172, 276, '밑변 a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(276, 190, '높이 b', { cls: 'ink2', size: 'sm' }));
    g.push(txt(152, 174, '빗변 c', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(174, 300, 'sin θ = b/c,  cos θ = a/c,  tan θ = b/a', { anchor: 'middle', cls: 'ink' }));
    g.push(panel(352, 30, 304, 286, '7장 — 단위원', { sub: '반지름 1 인 원. 어떤 각에서도 뜻이 있다' }));
    const ox = 516, oy = 190, R = 84;
    g.push(circ(ox, oy, R, { stroke: 'var(--grid)', sw: 1.4 }));
    g.push(ln([[ox - R - 18, oy], [ox + R + 18, oy]], { stroke: 'var(--ink2)', sw: 1.2 }));
    g.push(ln([[ox, oy + R + 12], [ox, oy - R - 18]], { stroke: 'var(--ink2)', sw: 1.2 }));
    const t = 132 * PI / 180;
    const Px = ox + R * Math.cos(t), Py = oy - R * Math.sin(t);
    g.push(ln([[ox, oy], [Px, Py]], { stroke: 'var(--s1)', sw: 2.4 }));
    g.push(ln([[Px, Py], [Px, oy]], { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }));
    g.push(ln([[Px, Py], [ox, Py]], { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }));
    g.push(ln([[ox, oy], [ox, Py]], { stroke: 'var(--s2)', sw: 3.4 }));
    g.push(ln([[ox, oy], [Px, oy]], { stroke: 'var(--s3)', sw: 3.4 }));
    g.push(pdot(Px, Py, 'var(--s1)', 5));
    g.push(arc(ox, oy, 30, 0, 132, ''));
    g.push(txt(ox - 15, oy - 36, 'θ', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(505, 94, 'P(cos θ, sin θ)', { anchor: 'end', cls: 'ink bold' }));
    g.push(arw(470, 100, Px + 2, Py - 8, { cls: 'ark', width: 1.3 }));
    g.push(txt(ox + 10, Py + 7, 'sin θ', { cls: 'ink2', size: 'sm' }));
    g.push(txt((ox + Px) / 2, oy + 20, 'cos θ < 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + R * 0.5, oy - 8, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(504, 302, '좌표를 그대로 읽으면 부호까지 함께 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-unit-circle',
        svg: svg({
            width: W, height: H,
            title: '직각삼각형의 삼각비를 단위원 위의 좌표로 넓힌다',
            desc: '왼쪽은 예각에서만 쓸 수 있는 직각삼각형의 비, 오른쪽은 어떤 각에서도 쓸 수 있는 단위원 위 점의 좌표',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-8. 유도공식 — 네 자리의 대칭 ---- */
add((() => {
    const W = 600, H = 404;
    const g = [];
    const ox = 300, oy = 190, R = 118;
    const th = 40;
    const P = a => [ox + R * Math.cos(a * PI / 180), oy - R * Math.sin(a * PI / 180)];
    const [px1, py1] = P(th);          // θ
    const [px2, py2] = P(180 - th);    // π − θ
    const [px3, py3] = P(180 + th);    // π + θ
    const [px4, py4] = P(-th);         // −θ

    g.push(circ(ox, oy, R, { stroke: 'var(--grid)', sw: 1.4 }));
    g.push(ln([[ox - R - 26, oy], [ox + R + 26, oy]], { stroke: 'var(--ink2)', sw: 1.2 }));
    g.push(ln([[ox, oy + R + 22], [ox, oy - R - 22]], { stroke: 'var(--ink2)', sw: 1.2 }));

    // 같은 높이(사인이 같다) · 같은 가로 위치(코사인이 같다)를 점선으로 잇는다.
    g.push(ln([[px2, py2], [px1, py1]], { stroke: 'var(--s3)', sw: 1.4, dash: '5 4' }));
    g.push(ln([[px1, py1], [px4, py4]], { stroke: 'var(--s3)', sw: 1.4, dash: '5 4' }));
    g.push(txt(ox, py1 - 15, '높이가 같다 — 사인이 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(px1 + 9, oy + 32, '가로가 같다 — 코사인이 같다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[ox, oy], [px3, py3]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(ln([[ox, oy], [px4, py4]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(ln([[ox, oy], [px1, py1]], { stroke: 'var(--s1)', sw: 2.4 }));
    g.push(ln([[ox, oy], [px2, py2]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(pdot(px1, py1, 'var(--s1)', 5));
    g.push(pdot(px2, py2, 'var(--s2)', 5));
    g.push(pdot(px3, py3, 'var(--ink2)', 4));
    g.push(pdot(px4, py4, 'var(--ink2)', 4));
    g.push(arc(ox, oy, 40, 0, th, 'θ'));

    const tag = (x, y, anchor, head, coord) => {
        g.push(txt(x, y, head, { anchor, cls: 'ink bold' }));
        g.push(txt(x, y + 18, coord, { anchor, cls: 'ink2', size: 'sm' }));
    };
    tag(px1 + 14, py1 - 12, 'start', 'θ', '(cos θ, sin θ)');
    tag(px2 - 14, py2 - 12, 'end', 'π − θ', '(−cos θ, sin θ)');
    tag(px3 - 14, py3 + 16, 'end', 'π + θ', '(−cos θ, −sin θ)');
    tag(px4 + 14, py4 + 16, 'start', '−θ', '(cos θ, −sin θ)');

    g.push(txt(28, 356, 'π − θ 는 θ 를 y축에 대해 뒤집은 자리다.', { cls: 'ink bold' }));
    g.push(txt(28, 378, '5장이 빌려 쓴 sin(180° − θ) = sin θ 와 cos(180° − θ) = −cos θ 가 이 그림 한 장이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-reduction',
        svg: svg({
            width: W, height: H,
            title: '유도공식은 단위원 위 네 점의 좌표를 견주는 것이다',
            desc: '각 θ, π−θ, π+θ, −θ 자리의 네 점은 좌표의 부호만 다르다. 그 부호를 읽은 것이 유도공식이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-9. 사인·코사인·탄젠트 그래프 ---- */
add((() => {
    const W = 700, H = 566;
    const g = [];
    const XR = [-PI - 0.35, 2 * PI + 0.55];
    const ticks = [
        { v: -PI, s: '−π' }, { v: -PI / 2, s: '−π/2' }, { v: PI / 2, s: 'π/2' },
        { v: PI, s: 'π' }, { v: 3 * PI / 2, s: '3π/2' }, { v: 2 * PI, s: '2π' },
    ];

    // 위 — sin 과 cos
    const f = frame({ xRange: XR, yRange: [-1.8, 1.9], box: { x: 70, y: 52, w: 578, h: 196 } });
    g.push(txt(70, 34, 'sin x 와 cos x — 주기 2π', { cls: 'ink bold' }));
    for (const t of ticks) g.push(`<path class="gr" d="M${f.X(t.v)} ${f.Y(-1.8)} V${f.Y(1.9)}"/>`);
    for (const yv of [-1, 1]) g.push(`<path class="gr" d="M${f.X(XR[0])} ${f.Y(yv)} H${f.X(XR[1])}"/>`);
    g.push(f.axes({ xTicks: [], yTicks: [], grid: false, xLabel: 'x' }));
    for (const t of ticks) g.push(txt(f.X(t.v), f.Y(0) + 17, t.s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0) - 8, f.Y(1) - 8, '1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0) - 8, f.Y(-1) + 15, '−1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(f.curve(Math.sin, { cls: 's1', steps: 280 }));
    g.push(f.curve(Math.cos, { cls: 's2', steps: 280 }));
    g.push(f.label([PI / 2, 1], 'sin x', { dx: 8, dy: -11, cls: 'ink bold' }));
    g.push(f.label([2 * PI, 1], 'cos x', { dx: 8, dy: -11, anchor: 'end', cls: 'ink bold' }));
    g.push(f.vector([PI / 2 - 0.06, 1.52], [0.08, 1.52], { cls: 's3', marker: 'ar3', width: 2 }));
    g.push(f.label([PI / 4, 1.52], 'π/2 만큼 왼쪽으로', { dy: -8, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(f.line([[0, -1.5], [2 * PI, -1.5]], { cls: 's3', dash: '5 4' }));
    g.push(f.label([PI, -1.5], '주기 2π', { dy: 17, anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 아래 — tan. x 범위를 위와 똑같이 잡아 주기를 눈으로 견줄 수 있게 한다.
    const h = frame({ xRange: XR, yRange: [-4.8, 4.8], box: { x: 70, y: 324, w: 578, h: 202 } });
    g.push(txt(70, 306, 'tan x — 주기 π. 같은 x 범위인데 되풀이가 두 배로 잦다', { cls: 'ink bold' }));
    for (const a of [-PI / 2, PI / 2, 3 * PI / 2]) {
        g.push(h.line([[a, -4.8], [a, 4.8]], { cls: 's2', dash: '5 4' }));
    }
    g.push(h.axes({ xTicks: [], yTicks: [-4, -2, 2, 4], grid: false, xLabel: 'x' }));
    for (const t of ticks) g.push(txt(h.X(t.v), h.Y(0) + 17, t.s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const eps = 0.207;
    const spans = [
        [XR[0], -PI / 2 - eps], [-PI / 2 + eps, PI / 2 - eps],
        [PI / 2 + eps, 3 * PI / 2 - eps], [3 * PI / 2 + eps, XR[1]],
    ];
    for (const [s, e] of spans) g.push(h.curve(Math.tan, { from: s, to: e, cls: 's1', steps: 170 }));
    g.push(h.label([PI / 2, 4.8], '점근선', { dx: 9, dy: 10, cls: 'ink bold', size: 'sm' }));
    g.push(h.dot([0, 0], { cls: 'f1', r: 4 }));
    g.push(h.line([[PI / 2, -4.1], [3 * PI / 2, -4.1]], { cls: 's3', dash: '5 4' }));
    g.push(h.label([PI, -4.1], '주기 π', { dy: 17, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(70, 556, 'x 는 라디안이다. cos x 가 0 이 되는 자리마다 tan x 가 끊긴다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-trig-graphs',
        svg: svg({
            width: W, height: H,
            title: '사인과 코사인은 2π 마다, 탄젠트는 π 마다 되풀이된다',
            desc: '같은 x 범위에 그린 사인·코사인 곡선과 탄젠트 곡선. 아래 그래프는 점근선으로 끊겨 있다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-10. 덧셈정리의 증명 그림 ---- */
add((() => {
    const W = 470, H = 430;
    const g = [];
    const ox = 206, oy = 176, R = 130;
    g.push(circ(ox, oy, R, { stroke: 'var(--grid)', sw: 1.4 }));
    g.push(ln([[ox - R - 20, oy], [ox + R + 22, oy]], { stroke: 'var(--ink2)', sw: 1.2 }));
    g.push(ln([[ox, oy + R + 16], [ox, oy - R - 16]], { stroke: 'var(--ink2)', sw: 1.2 }));
    const al = 74 * PI / 180, be = 20 * PI / 180;
    const Ax = ox + R * Math.cos(al), Ay = oy - R * Math.sin(al);
    const Bx = ox + R * Math.cos(be), By = oy - R * Math.sin(be);
    g.push(ln([[ox, oy], [Ax, Ay]], { stroke: 'var(--s1)', sw: 2.2 }));
    g.push(ln([[ox, oy], [Bx, By]], { stroke: 'var(--s2)', sw: 2.2 }));
    g.push(ln([[Ax, Ay], [Bx, By]], { stroke: 'var(--s3)', sw: 3.2 }));
    g.push(pdot(Ax, Ay, 'var(--s1)', 5));
    g.push(pdot(Bx, By, 'var(--s2)', 5));
    g.push(arc(ox, oy, 54, 0, 74, 'α'));
    g.push(arc(ox, oy, 92, 0, 20, 'β'));
    g.push(txt(Ax + 8, Ay - 10, 'A(cos α, sin α)', { cls: 'ink bold' }));
    g.push(txt(Bx + 10, By + 2, 'B(cos β, sin β)', { cls: 'ink bold' }));
    g.push(txt((Ax + Bx) / 2 - 4, (Ay + By) / 2 + 16, 'AB', { cls: 'ink' }));
    g.push(txt(ox - 12, oy + 20, 'O', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + R * 0.55, oy + 20, '반지름 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(26, 356, 'AB 의 길이를 두 가지 방법으로 재고 견준다.', { cls: 'ink bold' }));
    g.push(txt(26, 378, '① 코사인법칙(5장) — 끼인각이 α − β 다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, 398, '② 두 좌표 사이의 거리 공식', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, 418, '같은 길이를 두 방법으로 적으면 덧셈정리가 나온다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-fn-addition-proof',
        svg: svg({
            width: W, height: H,
            title: '덧셈정리는 단위원 위 두 점 사이의 거리를 두 가지로 계산해 얻는다',
            desc: '단위원 위의 두 점 A, B 를 잇는 선분의 길이를 코사인법칙과 거리 공식으로 각각 구해 견주는 그림',
            body: g.join(''),
        }),
    };
})());

export default figures;
