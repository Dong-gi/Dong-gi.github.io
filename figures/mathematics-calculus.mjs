/**
 * 기초수학 8장(방정식과 부등식) · 9장(극한과 연속) · 10장(미분)의 그림.
 *
 * 이름은 전부 `math-cal-` 로 시작한다(담당 C 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 그래서 첨자는 lib 의 `a~1` 표기를, 나머지는 유니코드(√, π, θ, ε, δ, ½, ′, ″)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰면 안 되고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 */
import { svg, frame, txt, arc, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

/** 음수 부호를 하이픈이 아니라 진짜 빼기 기호로 적는다. */
const nm = v => String(v).replace('-', '−');

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어
 * 선이 사라지고 화살촉만 남는다. 색을 직접 넣는 화살표를 따로 둔다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 다각형(채우기용). */
function poly(pts, { fill = 'var(--s1)', op = 0.18, stroke = 'none', sw = 1.4 } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 채운 점. */
const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 빈 점 — 그 자리에 값이 없다는 뜻. 안쪽을 배경색으로 채워야 선이 비치지 않는다. */
const odot = (x, y, col = 'var(--s2)', r = 4.5) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="var(--bgfix)" stroke="${col}" stroke-width="2"/>`;

/** odot 이 쓰는 배경색. 사이트 다크 모드 배경이 #121212 다. */
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

/** 각도호 경로. 수학 관례(반시계, y 는 화면 아래가 양수라 부호를 뒤집는다). */
function arcPath(cx, cy, r, a1, a2) {
    const rad = a => (a * Math.PI) / 180;
    const p = a => `${r2(cx + r * Math.cos(rad(a)))} ${r2(cy - r * Math.sin(rad(a)))}`;
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    return `M${p(a1)} A${r} ${r} 0 ${large} 0 ${p(a2)}`;
}

/** 부채꼴 채우기. */
function wedge(cx, cy, r, a1, a2, { fill = 'var(--s3)', op = 0.2 } = {}) {
    const rad = a => (a * Math.PI) / 180;
    const p1 = [cx + r * Math.cos(rad(a1)), cy - r * Math.sin(rad(a1))];
    return `<path d="M${r2(cx)} ${r2(cy)} L${r2(p1[0])} ${r2(p1[1])} ${arcPath(cx, cy, r, a1, a2).slice(arcPath(cx, cy, r, a1, a2).indexOf('A'))} Z" fill="${fill}" fill-opacity="${op}" stroke="none"/>`;
}

/** 패널 테두리 + 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y - 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y - 4, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 직각 표시. */
const rightAngle = (x, y, dx, dy, s = 10) =>
    `<path fill="none" stroke="var(--ink2)" stroke-width="1.2" d="M${x + dx * s} ${y} L${x + dx * s} ${y + dy * s} L${x} ${y + dy * s}"/>`;

/**
 * 상자 안에 축을 긋는다. 축이 지나는 데이터 좌표를 골라 줄 수 있어야
 * 0 이 범위 밖인 그래프(예: y 가 1.9 부터 시작하는 그림)도 그릴 수 있다.
 */
function axes(f, {
    xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel,
    axisY = 0, axisX = 0, tickFmt = nm, yTickFmt,
} = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = f.Y(axisY);
    const ay = f.X(axisX);
    const g = [];
    g.push(arw(f.X(x0), ax, f.X(x1) + 12, ax, { cls: 'ark', width: 1.3 }));
    g.push(arw(ay, f.Y(y0), ay, f.Y(y1) - 12, { cls: 'ark', width: 1.3 }));
    for (const t of xTicks) {
        if (t === axisX) continue;
        g.push(ln([[f.X(t), ax - 3.5], [f.X(t), ax + 3.5]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(f.X(t), ax + 17, tickFmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        if (t === axisY) continue;
        g.push(ln([[ay - 3.5, f.Y(t)], [ay + 3.5, f.Y(t)]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(ay - 8, f.Y(t) + 4, (yTickFmt ?? tickFmt)(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 16, ax + 5, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay + 8, f.Y(y1) - 12, yLabel, { cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 오른쪽 설명 블록. 줄 간격을 한 곳에서 관리한다. */
function lines(x, y, rows, { gap = 22, size = 'sm', cls = 'ink2' } = {}) {
    return rows.map((r, i) => {
        if (r === '') return '';
        const s = typeof r === 'string' ? { t: r } : r;
        return txt(x, y + i * gap, s.t, { cls: s.cls ?? cls, size: s.size ?? size });
    }).join('');
}

/* ================================================================== *
 * 8장 — 방정식과 부등식
 * ================================================================== */

/* ---- 8-1. 판별식 세 경우 ---- */
add((() => {
    const W = 700, H = 305;
    const g = [BG];
    const specs = [
        { x: 40, title: 'D > 0', f: v => v * v - 4, eq: 'y = x² − 4', cap: '서로 다른 두 실근', roots: [-2, 2], from: -3, to: 3 },
        { x: 265, title: 'D = 0', f: v => v * v, eq: 'y = x²', cap: '중근 (실근 하나)', roots: [0], from: -2.65, to: 2.65 },
        { x: 490, title: 'D < 0', f: v => v * v + 2, eq: 'y = x² + 2', cap: '실근 없음 (켤레 허근)', roots: [], from: -2.24, to: 2.24 },
    ];
    for (const s of specs) {
        const f = frame({ xRange: [-3, 3], yRange: [-5, 7], box: { x: s.x, y: 66, w: 170, h: 176 } });
        g.push(panel(s.x, 66, 170, 176, s.title));
        g.push(axes(f, { xRange: [-3, 3], yRange: [-5, 7], xTicks: [], yTicks: [] }));
        g.push(f.curve(s.f, { from: s.from, to: s.to, cls: 's1' }));
        for (const r of s.roots) g.push(pdot(f.X(r), f.Y(0), 'var(--s2)', 4.5));
        g.push(txt(s.x + 85, 264, s.eq, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(s.x + 85, 286, s.cap, { anchor: 'middle', cls: 'ink' }));
    }
    return {
        name: 'math-cal-discriminant',
        svg: svg({
            width: W, height: H,
            title: '판별식의 부호와 포물선이 x축과 만나는 방식',
            desc: 'D > 0 이면 두 점에서 만나고, D = 0 이면 닿기만 하며, D < 0 이면 만나지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-2. 완전제곱 만들기 ---- */
add((() => {
    const W = 660, H = 300;
    const g = [BG];
    const X0 = 60, Y0 = 66, S = 110, T = 60;
    // 왼쪽: x² + 6x 는 정사각형이 되다 만 모양
    g.push(box(X0, Y0, S, S, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--s1)' }));
    g.push(txt(X0 + S / 2, Y0 + S / 2 + 5, 'x²', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(X0 + S, Y0, T, S, { fill: 'var(--s3)', op: 0.18, stroke: 'var(--s3)' }));
    g.push(txt(X0 + S + T / 2, Y0 + S / 2 + 5, '3x', { anchor: 'middle', cls: 'ink' }));
    g.push(box(X0, Y0 + S, S, T, { fill: 'var(--s3)', op: 0.18, stroke: 'var(--s3)' }));
    g.push(txt(X0 + S / 2, Y0 + S + T / 2 + 5, '3x', { anchor: 'middle', cls: 'ink' }));
    g.push(box(X0 + S, Y0 + S, T, T, { stroke: 'var(--s2)', dash: '5 4' }));
    g.push(txt(X0 + S + T / 2, Y0 + S + T / 2 + 5, '9', { anchor: 'middle', cls: 'ink2' }));
    g.push(txt(X0 - 8, Y0 + S / 2 + 4, 'x', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(X0 - 8, Y0 + S + T / 2 + 4, '3', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(X0 + S / 2, Y0 - 10, 'x', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X0 + S + T / 2, Y0 - 10, '3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X0 + 85, 262, 'x² + 6x', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(X0 + 85, 282, '모자란 칸이 9 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 화살표
    g.push(arw(255, 150, 315, 150, { cls: 'ark', width: 2 }));
    g.push(txt(285, 138, '9 를 더한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 오른쪽: 완성된 정사각형
    const X1 = 345;
    g.push(box(X1, Y0, S + T, S + T, { fill: 'var(--s1)', op: 0.12, stroke: 'var(--s1)' }));
    g.push(box(X1 + S, Y0 + S, T, T, { fill: 'var(--s2)', op: 0.2, stroke: 'var(--s2)', dash: '5 4' }));
    g.push(txt(X1 + S / 2 + 8, Y0 + 70, '(x + 3)²', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(X1 + S + T / 2, Y0 + S + T / 2 + 5, '9', { anchor: 'middle', cls: 'ink2' }));
    g.push(txt(X1 + S + T + 10, Y0 + (S + T) / 2 + 4, 'x + 3', { cls: 'ink2', size: 'sm' }));
    g.push(txt(X1 + (S + T) / 2, Y0 - 10, 'x + 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X1 + 85, 262, '(x + 3)² − 9', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(X1 + 85, 282, '더한 9 를 다시 뺀다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-completing-square',
        svg: svg({
            width: W, height: H,
            title: '완전제곱꼴로 고치는 일을 넓이로 본 그림',
            desc: 'x 짜리 정사각형과 3x 짜리 직사각형 두 개는 9 만큼 모자란 정사각형이다. 9 를 더하고 다시 빼면 (x+3)² − 9 가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-3. 이차부등식을 그래프로 읽기 ---- */
add((() => {
    const W = 700, H = 330;
    const g = [BG];
    const f = frame({ xRange: [-4.5, 5.5], yRange: [-8.5, 9], box: { x: 70, y: 30, w: 400, h: 255 } });
    const fn = v => v * v - v - 6;
    // 두 근 사이에서 곡선과 x축이 감싸는 영역
    const pts = [];
    for (let i = 0; i <= 60; i += 1) {
        const xv = -2 + (5 * i) / 60;
        pts.push([f.X(xv), f.Y(fn(xv))]);
    }
    pts.push([f.X(3), f.Y(0)], [f.X(-2), f.Y(0)]);
    g.push(poly(pts, { fill: 'var(--s2)', op: 0.16 }));
    g.push(axes(f, {
        xRange: [-4.5, 5.5], yRange: [-8.5, 9],
        xTicks: [-4, -2, 3, 5], yTicks: [-8, 4, 8], xLabel: 'x', yLabel: 'y',
    }));
    g.push(f.curve(fn, { from: -3.41, to: 4.41, cls: 's1' }));
    g.push(pdot(f.X(-2), f.Y(0), 'var(--s2)', 5));
    g.push(pdot(f.X(3), f.Y(0), 'var(--s2)', 5));
    g.push(txt(f.X(1), f.Y(-4), '아래쪽 (y < 0)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(lines(505, 66, [
        { t: 'y = x² − x − 6', cls: 'ink', size: 'md' },
        { t: '  = (x + 2)(x − 3)', cls: 'ink' },
        '',
        { t: '그래프가 x축 아래', cls: 'ink' },
        '→ x² − x − 6 < 0',
        '→ 해는 −2 < x < 3',
        '',
        { t: '그래프가 x축 위', cls: 'ink' },
        '→ x² − x − 6 > 0',
        '→ 해는 x < −2 또는 x > 3',
    ], { gap: 24 }));
    return {
        name: 'math-cal-quadratic-inequality',
        svg: svg({
            width: W, height: H,
            title: '이차부등식의 해는 포물선이 x축 위에 있는지 아래에 있는지로 읽는다',
            desc: '두 근 사이에서는 값이 음수이고 바깥에서는 양수다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-4. 부호표 ---- */
add((() => {
    const W = 700, H = 322;
    const g = [BG];
    const L = 130, R = 615;
    const cx = [220, 360, 500];
    const mids = [(L + cx[0]) / 2, (cx[0] + cx[1]) / 2, (cx[1] + cx[2]) / 2, (cx[2] + R) / 2];
    g.push(txt(W / 2, 30, '(x + 2)(x − 1) / (x − 3) ≥ 0 의 부호표', { anchor: 'middle', cls: 'ink bold' }));
    // 위쪽 수직선
    g.push(ln([[L, 72], [R, 72]], { stroke: 'var(--ink2)', sw: 1.4 }));
    const marks = ['−2', '1', '3'];
    cx.forEach((x, i) => {
        g.push(ln([[x, 66], [x, 78]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(txt(x, 58, marks[i], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(ln([[x, 80], [x, 290]], { stroke: 'var(--grid)', sw: 1, dash: '4 4' }));
    });
    g.push(txt(120, 76, 'x', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const rows = [
        { y: 110, name: 'x + 2', sg: ['−', '+', '+', '+'] },
        { y: 148, name: 'x − 1', sg: ['−', '−', '+', '+'] },
        { y: 186, name: 'x − 3', sg: ['−', '−', '−', '+'] },
        { y: 232, name: '전체', sg: ['−', '+', '−', '+'], bold: true },
    ];
    for (const r of rows) {
        g.push(txt(120, r.y + 5, r.name, { anchor: 'end', cls: r.bold ? 'ink bold' : 'ink2', size: 'sm' }));
        r.sg.forEach((s, i) => {
            g.push(txt(mids[i], r.y + 6, s, {
                anchor: 'middle',
                cls: r.bold ? (s === '+' ? 'f3 bold' : 'f2 bold') : 'ink2',
            }));
        });
    }
    g.push(ln([[110, 208], [R + 10, 208]], { stroke: 'var(--grid)', sw: 1 }));
    // 해 구간
    g.push(txt(120, 279, '해', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(ln([[L, 275], [R, 275]], { stroke: 'var(--grid)', sw: 1.2 }));
    g.push(ln([[cx[0], 275], [cx[1], 275]], { stroke: 'var(--s3)', sw: 4.5 }));
    g.push(ln([[cx[2], 275], [R, 275]], { stroke: 'var(--s3)', sw: 4.5 }));
    g.push(pdot(cx[0], 275, 'var(--s3)', 5));
    g.push(pdot(cx[1], 275, 'var(--s3)', 5));
    g.push(odot(cx[2], 275, 'var(--s2)', 5));
    g.push(txt(cx[2] + 12, 258, 'x = 3 은 분모가 0 이라 제외', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 312, '해 : −2 ≤ x ≤ 1 또는 x > 3', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-cal-sign-chart',
        svg: svg({
            width: W, height: H,
            title: '인수마다 부호를 적어 전체 부호를 얻는 부호표',
            desc: '각 인수가 0 이 되는 점으로 수직선을 나누고 구간마다 부호를 곱한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-5. 산술-기하 평균 부등식의 반원 그림 ---- */
add((() => {
    const W = 660, H = 335;
    const g = [BG];
    const O = [330, 250], R = 200, HX = 230;
    const PY = 250 - Math.sqrt(100 * 300);
    g.push(`<path d="${arcPath(O[0], O[1], R, 0, 180)}" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
    g.push(ln([[130, 250], [530, 250]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(ln([[HX, 250], [HX, PY]], { stroke: 'var(--s2)', sw: 2.5 }));
    g.push(ln([[O[0], O[1]], [HX, PY]], { stroke: 'var(--s1)', sw: 2.5, dash: '6 4' }));
    g.push(rightAngle(HX, 250 - 12, 1, 1, 12));
    g.push(pdot(HX, PY, 'var(--s2)', 4.5));
    g.push(pdot(130, 250, 'var(--ink2)', 3.5));
    g.push(pdot(530, 250, 'var(--ink2)', 3.5));
    g.push(pdot(HX, 250, 'var(--ink2)', 3.5));
    g.push(pdot(O[0], O[1], 'var(--ink2)', 3.5));
    g.push(txt(124, 268, 'A', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 268, 'B', { cls: 'ink2', size: 'sm' }));
    g.push(txt(HX, 288, 'H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(O[0], 288, 'O', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(HX - 8, PY - 8, 'P', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(180, 270, 'a', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 270, 'b', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(HX - 10, 170, '√(ab)', { anchor: 'end', cls: 'f2 bold' }));
    g.push(txt(300, 128, '반지름 = (a + b)/2', { cls: 'f1' }));
    g.push(txt(W / 2, 36, '지름이 a + b 인 반원', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 310, '빗변(반지름)은 직각변(높이)보다 짧을 수 없다  →  (a + b)/2 ≥ √(ab)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(W / 2, 328, '두 값이 같아지는 것은 H 가 중심 O 에 올 때, 곧 a = b 인 경우뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-am-gm',
        svg: svg({
            width: W, height: H,
            title: '산술평균과 기하평균을 반원 위에서 견준다',
            desc: '지름을 a 와 b 로 나눈 점에서 세운 수선의 길이가 √(ab) 이고 반지름이 (a+b)/2 다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-6. 연립방정식의 해는 교점 ---- */
add((() => {
    const W = 700, H = 330;
    const g = [BG];
    const f = frame({ xRange: [-6.5, 6.5], yRange: [-6.5, 6.5], box: { x: 60, y: 30, w: 270, h: 270 } });
    g.push(axes(f, {
        xRange: [-6.5, 6.5], yRange: [-6.5, 6.5],
        xTicks: [-2, 2], yTicks: [-2, 2], xLabel: 'x', yLabel: 'y',
    }));
    const cp = [];
    for (let i = 0; i <= 120; i += 1) {
        const a = (2 * Math.PI * i) / 120;
        cp.push([f.X(5 * Math.cos(a)), f.Y(5 * Math.sin(a))]);
    }
    g.push(ln(cp, { stroke: 'var(--s1)', sw: 2 }));
    g.push(f.line([[0.8, 6.2], [6.2, 0.8]], { cls: 's2' }));
    g.push(pdot(f.X(3), f.Y(4), 'var(--s3)', 5.5));
    g.push(pdot(f.X(4), f.Y(3), 'var(--s3)', 5.5));
    g.push(txt(f.X(3) - 14, f.Y(4) + 16, '(3, 4)', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(-1.6), f.Y(-3.6), '반지름 5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(4) + 10, f.Y(3) + 14, '(4, 3)', { cls: 'ink', size: 'sm' }));
    g.push(lines(390, 62, [
        { t: '연립방정식', cls: 'ink bold', size: 'md' },
        '',
        { t: 'x² + y² = 25   (원)', cls: 'ink' },
        { t: 'x + y = 7   (직선)', cls: 'ink' },
        '',
        '두 식을 동시에 만족하는 (x, y)',
        '= 두 그래프가 만나는 점',
        '',
        { t: '해는 (3, 4) 와 (4, 3)', cls: 'ink' },
        '',
        '직선이 원을 스치면 해가 하나,',
        '비껴가면 실수해가 없다.',
    ], { gap: 22 }));
    return {
        name: 'math-cal-system-graph',
        svg: svg({
            width: W, height: H,
            title: '연립방정식의 해는 두 그래프의 교점이다',
            desc: '원과 직선이 만나는 두 점의 좌표가 연립방정식의 두 해다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-7. 고차방정식의 근과 x절편 ---- */
add((() => {
    const W = 700, H = 320;
    const g = [BG];
    const f = frame({ xRange: [-3.7, 3.3], yRange: [-15, 22], box: { x: 70, y: 30, w: 400, h: 255 } });
    const fn = v => v * v * v - 7 * v + 6;
    g.push(axes(f, {
        xRange: [-3.7, 3.3], yRange: [-15, 22], xTicks: [-3, -2, -1, 1, 2, 3],
        yTicks: [-10, 10, 20], xLabel: 'x', yLabel: 'y',
    }));
    g.push(f.curve(fn, { from: -3.52, to: 3.25, cls: 's1' }));
    for (const r of [-3, 1, 2]) g.push(pdot(f.X(r), f.Y(0), 'var(--s2)', 5.5));
    g.push(lines(505, 70, [
        { t: 'y = x³ − 7x + 6', cls: 'ink', size: 'md' },
        { t: '  = (x + 3)(x − 1)(x − 2)', cls: 'ink' },
        '',
        'x절편 세 개가 그대로',
        '방정식의 근 세 개다.',
        '',
        '인수 하나만 찾아내면',
        '나머지는 이차식이라',
        '근의 공식으로 끝난다.',
    ], { gap: 24 }));
    return {
        name: 'math-cal-cubic-roots',
        svg: svg({
            width: W, height: H,
            title: '삼차방정식의 근은 그래프의 x절편이다',
            desc: 'y = x³ − 7x + 6 은 x = −3, 1, 2 에서 x축을 지나고 그 세 값이 방정식의 근이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-8. 절댓값 두 개의 합 ---- */
add((() => {
    const W = 700, H = 320;
    const g = [BG];
    const f = frame({ xRange: [-5.2, 4.6], yRange: [-1.2, 9.5], box: { x: 70, y: 30, w: 400, h: 255 } });
    g.push(axes(f, {
        xRange: [-5.2, 4.6], yRange: [-1.2, 9.5], xTicks: [-4, -2, 2, 4],
        yTicks: [5, 9], xLabel: 'x', yLabel: 'y',
    }));
    g.push(f.line([[-5, 9], [-2, 3], [1, 3], [4, 9]], { cls: 's1' }));
    g.push(f.line([[-5.2, 5], [4.6, 5]], { cls: 's3', dash: '6 4' }));
    g.push(pdot(f.X(-3), f.Y(5), 'var(--s2)', 5));
    g.push(pdot(f.X(2), f.Y(5), 'var(--s2)', 5));
    g.push(ln([[f.X(-3), f.Y(0)], [f.X(2), f.Y(0)]], { stroke: 'var(--s2)', sw: 5 }));
    g.push(ln([[f.X(-3), f.Y(5)], [f.X(-3), f.Y(0)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(ln([[f.X(2), f.Y(5)], [f.X(2), f.Y(0)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(txt(f.X(-0.5), f.Y(0) + 30, '해 : −3 ≤ x ≤ 2', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(f.X(-0.5), f.Y(3) - 10, '가운데는 3 으로 일정', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(lines(505, 66, [
        { t: 'y = |x − 1| + |x + 2|', cls: 'ink', size: 'md' },
        '',
        '수직선 위의 점 x 에서',
        '1 까지의 거리와',
        '−2 까지의 거리를 더한 값.',
        '',
        '두 점 사이에 있으면 합이',
        '언제나 3 (두 점의 간격).',
        '',
        { t: 'y ≤ 5 인 구간이 부등식의 해', cls: 'ink' },
    ], { gap: 24 }));
    return {
        name: 'math-cal-abs-sum',
        svg: svg({
            width: W, height: H,
            title: '절댓값 두 개의 합은 거리의 합이다',
            desc: '|x−1| + |x+2| 는 두 점 사이에서 3 으로 일정하고 바깥에서 기울기 ±2 로 늘어난다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9장 — 극한과 연속
 * ================================================================== */

/* ---- 9-1. 수열의 극한 — 수렴과 진동 ---- */
add((() => {
    const W = 700, H = 318;
    const g = [BG];
    const xR = [0, 13], yR = [-1.35, 1.35];
    const mk = (x0, title) => frame({ xRange: xR, yRange: yR, box: { x: x0, y: 64, w: 252, h: 176 } });

    const fa = mk(62);
    g.push(panel(62, 64, 252, 176, '수렴한다'));
    g.push(box(fa.X(0), fa.Y(0.25), fa.X(13) - fa.X(0), fa.Y(-0.25) - fa.Y(0.25),
        { fill: 'var(--s3)', op: 0.18, stroke: 'none', rx: 0 }));
    g.push(axes(fa, { xRange: xR, yRange: yR, xTicks: [4, 8, 12], yTicks: [-1, 1], xLabel: 'n', yLabel: 'a~n' }));
    for (let k = 1; k <= 12; k += 1) g.push(pdot(fa.X(k), fa.Y(1 / k), 'var(--s1)', 3.6));
    g.push(ln([[fa.X(4.5), fa.Y(-1.3)], [fa.X(4.5), fa.Y(1.3)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
    g.push(txt(fa.X(12.7), fa.Y(0.78), 'a~n = 1/n', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(fa.X(4.8), fa.Y(-0.62), '이 뒤로는 전부 띠 안', { cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(0.3), fa.Y(-0.95), '띠의 폭이 허용 오차', { cls: 'f3', size: 'sm' }));

    const fb = mk(388);
    g.push(panel(388, 64, 252, 176, '진동한다'));
    g.push(axes(fb, { xRange: xR, yRange: yR, xTicks: [4, 8, 12], yTicks: [-1, 1], xLabel: 'n', yLabel: 'b~n' }));
    for (let k = 1; k <= 12; k += 1) g.push(pdot(fb.X(k), fb.Y(k % 2 === 0 ? 1 : -1), 'var(--s2)', 3.6));
    g.push(txt(fb.X(12.7), fb.Y(0.55), 'b~n = (−1) 의 n 제곱', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(fb.X(0.3), fb.Y(0.12), '어느 값에도 가까워지지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(188, 268, '어떤 폭의 띠를 그려도', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(188, 288, '유한 개만 밖에 남는다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(514, 268, '폭 1 짜리 띠를 그리면', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(514, 288, '언제나 무한히 많이 밖에 남는다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 310, '수렴한다는 말의 뜻은 ‘어떤 오차 폭을 잡아도 결국 그 안에 들어와 머문다’ 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-sequence-limit',
        svg: svg({
            width: W, height: H,
            title: '수렴하는 수열과 진동하는 수열',
            desc: '1/n 은 어떤 폭의 띠를 잡아도 어느 항부터 그 안에 머물지만, 1 과 −1 을 오가는 수열은 그렇지 않다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-2. 극한은 그 점의 함숫값과 무관하다 ---- */
add((() => {
    const W = 700, H = 316;
    const g = [BG];
    const xR = [0, 4.4], yR = [0, 6.8];
    const specs = [
        { x: 42, title: '(1) 값이 없다', cap: ['x = 2 에서', '정의되지 않는다'] },
        { x: 262, title: '(2) 값이 따로 있다', cap: ['f(2) = 1 이라고', '따로 정해 두었다'] },
        { x: 482, title: '(3) 값이 극한과 같다', cap: ['f(2) = 4 다.', '이때만 연속이라 부른다'] },
    ];
    specs.forEach((s, i) => {
        const f = frame({ xRange: xR, yRange: yR, box: { x: s.x, y: 64, w: 176, h: 158 } });
        g.push(panel(s.x, 64, 176, 158, s.title));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [2], yTicks: [4], xLabel: 'x', yLabel: 'y' }));
        g.push(f.guide([2, 0], [2, 4]));
        g.push(f.guide([0, 4], [2, 4]));
        g.push(f.line([[0, 2], [1.97, 3.97]], { cls: 's1' }));
        g.push(f.line([[2.03, 4.03], [4.3, 6.3]], { cls: 's1' }));
        if (i === 2) {
            g.push(pdot(f.X(2), f.Y(4), 'var(--s1)', 4.5));
        } else {
            g.push(odot(f.X(2), f.Y(4), 'var(--s1)', 4.5));
            if (i === 1) g.push(pdot(f.X(2), f.Y(1), 'var(--s2)', 4.5));
        }
        g.push(txt(s.x + 88, 250, s.cap[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(s.x + 88, 268, s.cap[1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(W / 2, 300, '셋 다 x → 2 일 때 함숫값은 4 에 가까워진다. 극한은 x = 2 에 무슨 일이 있든 상관하지 않는다', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-cal-limit-hole',
        svg: svg({
            width: W, height: H,
            title: '극한은 그 점에서의 함숫값과 무관하다',
            desc: '같은 곡선이 x = 2 에서 값이 없거나, 다른 값을 갖거나, 극한과 같은 값을 갖는 세 경우',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-3. 엡실론-델타 ---- */
add((() => {
    const W = 700, H = 344;
    const g = [BG];
    const xR = [0, 3.4], yR = [0, 4.2];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 76, y: 40, w: 330, h: 232 } });
    const fn = v => 0.6 * v * v;
    const a = 2, L = 2.4, EP = 0.62, DL = 0.24;
    g.push(box(f.X(0), f.Y(L + EP), f.X(3.4) - f.X(0), f.Y(L - EP) - f.Y(L + EP),
        { fill: 'var(--s3)', op: 0.2, stroke: 'none', rx: 0 }));
    g.push(box(f.X(a - DL), f.Y(4.2), f.X(a + DL) - f.X(a - DL), f.Y(0) - f.Y(4.2),
        { fill: 'var(--s2)', op: 0.16, stroke: 'none', rx: 0 }));
    g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(fn, { from: 0, to: 2.62, cls: 's1' }));
    g.push(f.guide([a, 0], [a, L]));
    g.push(f.guide([0, L], [a, L]));
    g.push(pdot(f.X(a), f.Y(L), 'var(--s1)', 4.5));
    g.push(txt(f.X(a), f.Y(0) + 18, 'a', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(f.X(0) - 8, f.Y(L) + 4, 'L', { anchor: 'end', cls: 'ink' }));
    g.push(txt(f.X(0) - 8, f.Y(L + EP) + 4, 'L + ε', { anchor: 'end', cls: 'f3', size: 'sm' }));
    g.push(txt(f.X(0) - 8, f.Y(L - EP) + 4, 'L − ε', { anchor: 'end', cls: 'f3', size: 'sm' }));
    g.push(txt(f.X(a - DL) - 10, f.Y(0) + 34, 'a − δ', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    g.push(txt(f.X(a + DL) + 12, f.Y(0) + 34, 'a + δ', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    g.push(lines(432, 78, [
        { t: '① 상대가 오차 폭 ε 를 고른다', cls: 'ink' },
        { t: '② 나는 그에 맞는 폭 δ 를 내놓는다', cls: 'ink' },
        { t: '③ 폭 δ 안의 x 는 값이 반드시', cls: 'ink' },
        { t: '   폭 ε 안에 들어간다', cls: 'ink' },
        '',
        'ε 를 아무리 좁게 잡아도 언제나',
        'δ 를 찾아낼 수 있다면, 그때',
        '극한이 L 이라고 한다.',
    ], { gap: 24 }));
    g.push(txt(W / 2, 330, '가로 폭을 충분히 좁히면 세로 폭 안에 가둘 수 있다 — ‘한없이 가까워진다’ 를 부등식으로 옮긴 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-epsilon-delta',
        svg: svg({
            width: W, height: H,
            title: '엡실론과 델타로 극한을 적는 방식',
            desc: '세로 방향 허용 오차 ε 를 먼저 정하면 그에 맞는 가로 방향 폭 δ 를 찾을 수 있다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-4. 샌드위치 정리 ---- */
add((() => {
    const W = 700, H = 300;
    const g = [BG];
    const xR = [-1.05, 1.05], yR = [-1.1, 1.1];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 76, y: 40, w: 300, h: 232 } });
    g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [-1, 1], yTicks: [-1, 1], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(v => v * v, { from: -1.02, to: 1.02, cls: 's1' }));
    g.push(f.curve(v => -v * v, { from: -1.02, to: 1.02, cls: 's3' }));
    g.push(f.curve(v => (v === 0 ? 0 : v * v * Math.sin(1 / v)), { from: -1.02, to: 1.02, cls: 's2', steps: 1100 }));
    g.push(pdot(f.X(0), f.Y(0), 'var(--ink2)', 4));
    g.push(legend(404, 76, [
        { slot: 1, name: 'y = x²  (위에서 죈다)' },
        { slot: 2, name: 'y = x² sin(1/x)  (가운데)' },
        { slot: 3, name: 'y = −x²  (아래에서 죈다)' },
    ]));
    g.push(lines(404, 152, [
        { t: '가운데 함수는 원점 근처에서', cls: 'ink' },
        { t: '무한히 많이 흔들린다.', cls: 'ink' },
        '',
        { t: '그래도 값은 언제나', cls: 'ink' },
        { t: '−x² 이상 x² 이하다.', cls: 'ink' },
        '',
        { t: '위와 아래가 모두 0 으로 모이면', cls: 'ink' },
        { t: '가운데도 0 으로 갈 수밖에 없다.', cls: 'ink' },
    ], { gap: 20 }));
    return {
        name: 'math-cal-squeeze',
        svg: svg({
            width: W, height: H,
            title: '샌드위치 정리 — 위아래에서 죄어 값을 정한다',
            desc: 'x² sin(1/x) 는 −x² 과 x² 사이에 갇혀 있고 그 둘이 0 으로 모이므로 자신도 0 으로 간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-5. 부채꼴 넓이 비교로 sin x / x ---- */
add((() => {
    const W = 700, H = 372;
    const g = [BG];
    const TH = 42, rad = (TH * Math.PI) / 180, R = 128;
    const specs = [
        { x: 26, fill: 'var(--s1)', kind: 'tri', title: '삼각형 OAT', area: '넓이 = ½ · 1 · sin x' },
        { x: 250, fill: 'var(--s3)', kind: 'sec', title: '부채꼴 OAT', area: '넓이 = ½ · 1² · x' },
        { x: 474, fill: 'var(--s2)', kind: 'big', title: '삼각형 OBT', area: '넓이 = ½ · 1 · tan x' },
    ];
    for (const s of specs) {
        const O = [s.x + 38, 216];
        const T = [O[0] + R, O[1]];
        const A = [O[0] + R * Math.cos(rad), O[1] - R * Math.sin(rad)];
        const B = [O[0] + R, O[1] - R * Math.tan(rad)];
        g.push(panel(s.x, 56, 200, 180, s.title));
        if (s.kind === 'tri') g.push(poly([O, T, A], { fill: s.fill, op: 0.28 }));
        if (s.kind === 'sec') g.push(wedge(O[0], O[1], R, 0, TH, { fill: s.fill, op: 0.3 }));
        if (s.kind === 'big') g.push(poly([O, T, B], { fill: s.fill, op: 0.26 }));
        g.push(`<path d="${arcPath(O[0], O[1], R, 0, 90)}" fill="none" stroke="var(--grid)" stroke-width="1.3"/>`);
        g.push(ln([O, [O[0] + R + 18, O[1]]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(ln([O, [O[0], O[1] - R - 18]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(ln([O, B], { stroke: 'var(--ink2)', sw: 1.4, dash: s.kind === 'big' ? undefined : '5 4' }));
        g.push(ln([T, B], { stroke: s.kind === 'big' ? 'var(--s2)' : 'var(--grid)', sw: s.kind === 'big' ? 2.2 : 1.2 }));
        if (s.kind === 'tri') g.push(ln([A, T], { stroke: 'var(--s1)', sw: 2 }));
        g.push(pdot(O[0], O[1], 'var(--ink2)', 3.2));
        g.push(pdot(T[0], T[1], 'var(--ink2)', 3.2));
        g.push(pdot(A[0], A[1], 'var(--ink2)', 3.6));
        g.push(pdot(B[0], B[1], 'var(--ink2)', 3.2));
        g.push(txt(O[0] - 12, O[1] + 16, 'O', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(T[0] + 2, T[1] + 18, 'T', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(A[0] - 12, A[1] - 4, 'A', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(B[0] + 8, B[1] + 4, 'B', { cls: 'ink2', size: 'sm' }));
        g.push(arc(O[0], O[1], 40, 0, TH, 'x', { cls: 'gr' }));
        g.push(txt(s.x + 100, 254, s.area, { anchor: 'middle', cls: 'ink' }));
    }
    g.push(txt(226, 150, '⊂', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(450, 150, '⊂', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 288, '0 < x < π/2 에서 세 영역은 차례로 포함되므로      ½ sin x  <  ½ x  <  ½ tan x', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 314, '양변을 ½ sin x 로 나누면   1  <  x / sin x  <  1 / cos x', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 338, 'x → 0 이면 오른쪽 끝이 1 로 가고, 가운데도 1 로 갈 수밖에 없다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W / 2, 360, '반지름이 1 이고 각을 라디안으로 재기 때문에 부채꼴 넓이가 ½ x 로 단순해진다. 도수로 쟀다면 이 결과는 나오지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-squeeze-sin',
        svg: svg({
            width: W, height: H,
            title: '부채꼴 넓이를 견주어 sin x 와 x 의 비를 구한다',
            desc: '반지름 1 인 원에서 삼각형 OAT, 부채꼴 OAT, 삼각형 OBT 의 넓이가 차례로 커진다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-6. 불연속의 유형 ---- */
add((() => {
    const W = 700, H = 316;
    const g = [BG];
    const specs = [
        { x: 42, title: '뛴다', cap: ['좌극한 −1, 우극한 1.', '극한 자체가 없다'] },
        { x: 262, title: '치솟는다', cap: ['한쪽은 +∞, 다른 쪽은 −∞.', '극한이 유한하지 않다'] },
        { x: 482, title: '흔들린다', cap: ['0 에 다가갈수록 진동이 빨라져', '어느 값에도 정착하지 않는다'] },
    ];
    // (1) 도약
    {
        const s = specs[0];
        const xR = [-2, 2], yR = [-2.2, 2.2];
        const f = frame({ xRange: xR, yRange: yR, box: { x: s.x, y: 64, w: 176, h: 158 } });
        g.push(panel(s.x, 64, 176, 158, s.title));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
        g.push(txt(f.X(-1.85), f.Y(-1) + 18, '값 −1', { cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(1.85), f.Y(1) - 10, '값 1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(f.line([[-1.9, -1], [0.67, -1]], { cls: 's1' }));
        g.push(f.line([[0.73, 1], [1.9, 1]], { cls: 's1' }));
        g.push(odot(f.X(0.7), f.Y(-1), 'var(--s1)', 4.2));
        g.push(pdot(f.X(0.7), f.Y(1), 'var(--s1)', 4.2));
        g.push(ln([[f.X(0.7), f.Y(-1)], [f.X(0.7), f.Y(1)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    }
    // (2) 무한
    {
        const s = specs[1];
        const xR = [-2, 2], yR = [-6.5, 6.5];
        const f = frame({ xRange: xR, yRange: yR, box: { x: s.x, y: 64, w: 176, h: 158 } });
        g.push(panel(s.x, 64, 176, 158, s.title));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
        g.push(f.curve(v => 1 / v, { from: -2, to: -0.155, cls: 's1' }));
        g.push(f.curve(v => 1 / v, { from: 0.155, to: 2, cls: 's1' }));
        g.push(ln([[f.X(0), f.Y(-6.5)], [f.X(0), f.Y(6.5)]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
    }
    // (3) 진동
    {
        const s = specs[2];
        const xR = [0, 0.62], yR = [-1.35, 1.35];
        const f = frame({ xRange: xR, yRange: yR, box: { x: s.x, y: 64, w: 176, h: 158 } });
        g.push(panel(s.x, 64, 176, 158, s.title));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [-1, 1], xLabel: 'x' }));
        g.push(f.curve(v => Math.sin(1 / Math.max(v, 1e-6)), { from: 0.006, to: 0.62, cls: 's1', steps: 1300 }));
    }
    specs.forEach(s => {
        g.push(txt(s.x + 88, 250, s.cap[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(s.x + 88, 268, s.cap[1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(W / 2, 300, '앞의 ‘구멍’ 까지 넣으면 불연속은 네 가지다. 메울 수 있는 것은 구멍뿐이다', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-cal-discontinuity',
        svg: svg({
            width: W, height: H,
            title: '불연속의 세 가지 모습',
            desc: '값이 뛰는 경우, 무한대로 치솟는 경우, 무한히 흔들리는 경우',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-7. 중간값 정리 ---- */
add((() => {
    const W = 700, H = 322;
    const g = [BG];
    const xR = [-0.3, 4.4], yR = [-2, 4.6];
    const fn = v => 0.9 * v - 1 + 1.5 * Math.sin(2 * v);
    const fa = frame({ xRange: xR, yRange: yR, box: { x: 62, y: 62, w: 256, h: 186 } });
    g.push(panel(62, 62, 256, 186, '연속이면 반드시 지난다'));
    g.push(axes(fa, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(ln([[fa.X(-0.3), fa.Y(1)], [fa.X(4.4), fa.Y(1)]], { stroke: 'var(--s3)', sw: 1.8, dash: '6 4' }));
    g.push(fa.curve(fn, { from: 0, to: 4, cls: 's1' }));
    g.push(pdot(fa.X(0), fa.Y(fn(0)), 'var(--ink2)', 4));
    g.push(pdot(fa.X(4), fa.Y(fn(4)), 'var(--ink2)', 4));
    for (const c of [0.605, 1.253, 2.933]) g.push(pdot(fa.X(c), fa.Y(1), 'var(--s2)', 4.6));
    g.push(txt(fa.X(0) + 10, fa.Y(fn(0)) + 5, 'f(a)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(4) - 2, fa.Y(fn(4)) - 10, 'f(b)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(4.3), fa.Y(1) - 9, 'v', { anchor: 'end', cls: 'f3' }));
    g.push(txt(fa.X(1.9), fa.Y(-1.75), 'f(c) = v 인 c 가 세 개', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    const xR2 = [-0.3, 4.4], yR2 = [-2, 4.6];
    const fb = frame({ xRange: xR2, yRange: yR2, box: { x: 388, y: 62, w: 256, h: 186 } });
    g.push(panel(388, 62, 256, 186, '끊기면 건너뛴다'));
    g.push(axes(fb, { xRange: xR2, yRange: yR2, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(ln([[fb.X(-0.3), fb.Y(1)], [fb.X(4.4), fb.Y(1)]], { stroke: 'var(--s3)', sw: 1.8, dash: '6 4' }));
    g.push(fb.line([[0, -1], [1.97, -0.2]], { cls: 's1' }));
    g.push(fb.line([[2.03, 2.6], [4, 3.6]], { cls: 's1' }));
    g.push(odot(fb.X(2), fb.Y(-0.2), 'var(--s1)', 4.2));
    g.push(pdot(fb.X(2), fb.Y(2.6), 'var(--s1)', 4.2));
    g.push(txt(fb.X(4.3), fb.Y(1) - 9, 'v', { anchor: 'end', cls: 'f3' }));
    g.push(txt(fb.X(0.12), fb.Y(3.4), '값 v 를 갖는 점이 없다', { cls: 'ink', size: 'sm' }));

    g.push(txt(W / 2, 278, '연속함수가 두 값을 가지면 그 사이의 모든 값도 반드시 갖는다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 302, '정리는 c 가 있다고만 말할 뿐 어디인지는 말하지 않는다. 개수도 하나라고 말하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-ivt',
        svg: svg({
            width: W, height: H,
            title: '중간값 정리와 그 반례',
            desc: '연속이면 f(a) 와 f(b) 사이의 값 v 를 반드시 어딘가에서 갖지만, 끊긴 함수는 v 를 건너뛸 수 있다',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-8. 최대·최소 정리 ---- */
add((() => {
    const W = 700, H = 322;
    const g = [BG];
    const xR = [-0.4, 4.2], yR = [-0.2, 4.4];
    const fn = v => 2 + 1.4 * Math.sin(1.6 * v);
    const fa = frame({ xRange: xR, yRange: yR, box: { x: 62, y: 62, w: 256, h: 186 } });
    g.push(panel(62, 62, 256, 186, '닫힌 구간에서 연속'));
    g.push(axes(fa, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(fa.curve(fn, { from: 0, to: 3.7, cls: 's1' }));
    const cMax = Math.PI / 2 / 1.6, cMin = 3 * Math.PI / 2 / 1.6;
    g.push(ln([[fa.X(-0.4), fa.Y(3.4)], [fa.X(4.2), fa.Y(3.4)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(ln([[fa.X(-0.4), fa.Y(0.6)], [fa.X(4.2), fa.Y(0.6)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(pdot(fa.X(cMax), fa.Y(3.4), 'var(--s2)', 5));
    g.push(pdot(fa.X(cMin), fa.Y(0.6), 'var(--s3)', 5));
    g.push(pdot(fa.X(0), fa.Y(fn(0)), 'var(--ink2)', 3.6));
    g.push(pdot(fa.X(3.7), fa.Y(fn(3.7)), 'var(--ink2)', 3.6));
    g.push(txt(fa.X(0.12), fa.Y(3.4) - 9, '최댓값 M', { cls: 'f2', size: 'sm' }));
    g.push(txt(fa.X(0.12), fa.Y(0.6) - 9, '최솟값 m', { cls: 'f3', size: 'sm' }));
    g.push(fa.guide([0, 0], [0, fn(0)]));
    g.push(fa.guide([3.7, 0], [3.7, fn(3.7)]));
    g.push(txt(fa.X(0), fa.Y(0) + 17, 'a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(3.7), fa.Y(0) + 17, 'b', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const xR2 = [-0.35, 2.6], yR2 = [-0.2, 2.9];
    const fb = frame({ xRange: xR2, yRange: yR2, box: { x: 388, y: 62, w: 256, h: 186 } });
    g.push(panel(388, 62, 256, 186, '한 점에서 끊긴 경우'));
    g.push(axes(fb, { xRange: xR2, yRange: yR2, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(fb.line([[0, 0], [1.97, 1.97]], { cls: 's1' }));
    g.push(odot(fb.X(2), fb.Y(2), 'var(--s1)', 4.5));
    g.push(pdot(fb.X(2), fb.Y(0.35), 'var(--s1)', 4.5));
    g.push(ln([[fb.X(-0.35), fb.Y(2)], [fb.X(2.6), fb.Y(2)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(txt(fb.X(0.1), fb.Y(2.62), '2 에 얼마든지 가깝지만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(0.1), fb.Y(2.28), '2 가 되지는 않는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(0.95), fb.Y(0.3), '최댓값이 없다', { anchor: 'middle', cls: 'ink' }));

    g.push(txt(W / 2, 278, '닫힌 구간에서 연속인 함수는 그 구간 안에서 최댓값과 최솟값을 실제로 갖는다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 302, '‘닫힌’ 과 ‘연속’ 중 하나만 빠져도 오른쪽처럼 최댓값이 사라진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-evt',
        svg: svg({
            width: W, height: H,
            title: '최대·최소 정리와 그 반례',
            desc: '닫힌 구간에서 연속이면 최댓값과 최솟값이 존재하지만, 끊긴 함수는 상한에 닿지 못할 수 있다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10장 — 미분
 * ================================================================== */

/* ---- 10-1. 할선에서 접선으로 ---- */
add((() => {
    const W = 700, H = 336;
    const g = [BG];
    const xR = [-0.35, 3.8], yR = [-0.5, 4.6];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 70, y: 44, w: 380, h: 246 } });
    const fn = v => 0.4 * v * v;
    const a = 1;
    g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x', yLabel: 'y' }));
    g.push(poly([[f.X(a), f.Y(fn(a))], [f.X(3), f.Y(fn(a))], [f.X(3), f.Y(fn(3))]], { fill: 'var(--s1)', op: 0.12 }));
    g.push(ln([[f.X(a), f.Y(fn(a))], [f.X(3), f.Y(fn(a))]], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
    g.push(ln([[f.X(3), f.Y(fn(a))], [f.X(3), f.Y(fn(3))]], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
    g.push(f.curve(fn, { from: -0.2, to: 3.4, cls: 's1' }));
    g.push(f.line([[0.45, 0.4 + 1.6 * (0.45 - 1)], [3.5, 0.4 + 1.6 * (3.5 - 1)]], { cls: 's3' }));
    g.push(f.line([[0.42, 0.4 + 1.2 * (0.42 - 1)], [2.7, 0.4 + 1.2 * (2.7 - 1)]], { cls: 's3', dash: '6 4' }));
    g.push(f.line([[0.1, 0.4 + 0.8 * (0.1 - 1)], [2.9, 0.4 + 0.8 * (2.9 - 1)]], { cls: 's2' }));
    g.push(pdot(f.X(a), f.Y(fn(a)), 'var(--s2)', 5));
    g.push(pdot(f.X(3), f.Y(fn(3)), 'var(--s1)', 5));
    g.push(pdot(f.X(2), f.Y(fn(2)), 'var(--s1)', 4.2));
    g.push(txt(f.X(a) - 12, f.Y(fn(a)) - 9, 'P (a, f(a))', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(3) - 8, f.Y(fn(3)) - 12, 'Q (a+h, f(a+h))', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(2) - 9, f.Y(fn(2)) - 8, 'Q′', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(2), f.Y(fn(a)) - 9, 'Δx = h', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(f.X(3) + 8, f.Y(2), 'Δy', { cls: 'ink' }));
    g.push(txt(f.X(2.3), f.Y(1.02), '접선', { cls: 'f2 bold', size: 'sm' }));
    g.push(txt(f.X(3.05), f.Y(4.35), '할선', { anchor: 'end', cls: 'f3 bold', size: 'sm' }));
    g.push(lines(474, 72, [
        { t: '할선 PQ 의 기울기', cls: 'ink bold' },
        { t: '= Δy / Δx', cls: 'ink' },
        { t: '= (f(a+h) − f(a)) / h', cls: 'ink' },
        '',
        'h 를 줄이면 Q 가 P 로 다가가고',
        '할선은 접선으로 다가간다.',
        '',
        { t: '그 극한값이 미분계수 f′(a) 이고', cls: 'ink' },
        { t: '접선의 기울기다.', cls: 'ink' },
    ], { gap: 24 }));
    g.push(txt(W / 2, 322, '미분은 이 그림 한 장이다 — 두 점을 잇는 기울기에서 한 점에서의 기울기로 넘어가는 일', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-tangent',
        svg: svg({
            width: W, height: H,
            title: '할선의 기울기가 접선의 기울기로 다가간다',
            desc: 'P 와 Q 를 잇는 할선의 기울기는 Δy 를 Δx 로 나눈 값이고, h 를 0 으로 줄이면 접선의 기울기가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-2. 미분가능하지 않은 자리 ---- */
add((() => {
    const W = 700, H = 316;
    const g = [BG];
    const P = [42, 262, 482];
    {
        const xR = [-2, 2], yR = [-0.6, 2.2];
        const f = frame({ xRange: xR, yRange: yR, box: { x: P[0], y: 64, w: 176, h: 158 } });
        g.push(panel(P[0], 64, 176, 158, '뾰족점'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
        g.push(f.line([[-1.9, 1.9], [0, 0]], { cls: 's1' }));
        g.push(f.line([[0, 0], [1.9, 1.9]], { cls: 's1' }));
        g.push(pdot(f.X(0), f.Y(0), 'var(--s2)', 4.5));
        g.push(txt(f.X(-1.8), f.Y(1.05), '기울기 −1', { cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(1.8), f.Y(1.05), '기울기 +1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    {
        const xR = [-1.6, 1.6], yR = [-1.35, 1.35];
        const f = frame({ xRange: xR, yRange: yR, box: { x: P[1], y: 64, w: 176, h: 158 } });
        g.push(panel(P[1], 64, 176, 158, '접선이 수직'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
        g.push(f.curve(v => Math.cbrt(v), { from: -1.5, to: 1.5, cls: 's1', steps: 300 }));
        g.push(ln([[f.X(0), f.Y(-1.2)], [f.X(0), f.Y(1.2)]], { stroke: 'var(--s2)', sw: 1.6, dash: '5 4' }));
        g.push(pdot(f.X(0), f.Y(0), 'var(--s2)', 4.5));
        g.push(txt(f.X(0.12), f.Y(-0.85), '기울기가 무한대', { cls: 'ink2', size: 'sm' }));
    }
    {
        const xR = [-2, 2], yR = [-1.6, 1.9];
        const f = frame({ xRange: xR, yRange: yR, box: { x: P[2], y: 64, w: 176, h: 158 } });
        g.push(panel(P[2], 64, 176, 158, '끊긴 자리'));
        g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
        g.push(f.line([[-1.9, -0.9], [0.57, -0.9]], { cls: 's1' }));
        g.push(f.line([[0.63, 1.3], [1.9, 1.3]], { cls: 's1' }));
        g.push(odot(f.X(0.6), f.Y(-0.9), 'var(--s1)', 4.2));
        g.push(pdot(f.X(0.6), f.Y(1.3), 'var(--s1)', 4.2));
        g.push(txt(f.X(-1.85), f.Y(0.35), '값이 뛴다', { cls: 'ink2', size: 'sm' }));
    }
    const caps = [
        ['좌우 기울기가 다르다.', 'y = |x| 의 원점'],
        ['기울기가 정해지지 않는다.', '세제곱근 함수의 원점'],
        ['연속이 아니면', '미분도 될 수 없다'],
    ];
    P.forEach((x, i) => {
        g.push(txt(x + 88, 250, caps[i][0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(x + 88, 268, caps[i][1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(W / 2, 300, '미분가능하면 연속이지만, 연속이라고 미분가능한 것은 아니다 — 왼쪽 둘이 그 반례다', { anchor: 'middle', cls: 'ink bold' }));
    return {
        name: 'math-cal-nondiff',
        svg: svg({
            width: W, height: H,
            title: '미분할 수 없는 세 가지 자리',
            desc: '좌우 기울기가 다른 뾰족점, 접선이 수직인 점, 값이 끊긴 점',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-3. 도함수의 부호와 원함수의 증감 ---- */
add((() => {
    const W = 700, H = 412;
    const g = [BG];
    const xR = [-2.4, 2.4];
    const ft = frame({ xRange: xR, yRange: [-3.4, 3.4], box: { x: 92, y: 44, w: 330, h: 140 } });
    const fb = frame({ xRange: xR, yRange: [-4.5, 10], box: { x: 92, y: 232, w: 330, h: 140 } });
    for (const c of [-1, 1]) g.push(ln([[ft.X(c), 40], [ft.X(c), 376]], { stroke: 'var(--s2)', sw: 1.2, dash: '5 4' }));
    g.push(panel(92, 44, 330, 140, 'f(x) = x³ − 3x'));
    g.push(axes(ft, { xRange: xR, yRange: [-3.4, 3.4], xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(ft.curve(v => v ** 3 - 3 * v, { from: -2.05, to: 2.05, cls: 's1' }));
    g.push(pdot(ft.X(-1), ft.Y(2), 'var(--s2)', 4.5));
    g.push(pdot(ft.X(1), ft.Y(-2), 'var(--s2)', 4.5));
    g.push(txt(ft.X(-1) - 8, ft.Y(2) - 8, '극대', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(ft.X(1) + 8, ft.Y(-2) + 16, '극소', { cls: 'ink', size: 'sm' }));
    g.push(txt(ft.X(-1.75), ft.Y(-2.5), '오른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(ft.X(0), ft.Y(2.6), '내린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(ft.X(2.3), ft.Y(2.9), '오른다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(panel(92, 232, 330, 140, 'f′(x) = 3x² − 3'));
    g.push(axes(fb, { xRange: xR, yRange: [-4.5, 10], xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(fb.curve(v => 3 * v * v - 3, { from: -2.05, to: 2.05, cls: 's3' }));
    g.push(pdot(fb.X(-1), fb.Y(0), 'var(--s2)', 4.5));
    g.push(pdot(fb.X(1), fb.Y(0), 'var(--s2)', 4.5));
    g.push(txt(fb.X(-1.8), fb.Y(5), '양수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(0), fb.Y(-2.6), '음수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(1.8), fb.Y(5), '양수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(lines(456, 74, [
        { t: '두 그래프를 세로로 붙여 읽는다.', cls: 'ink' },
        '',
        { t: 'f′ > 0 인 구간에서 f 는 오른다', cls: 'ink' },
        { t: 'f′ < 0 인 구간에서 f 는 내린다', cls: 'ink' },
        { t: 'f′ = 0 은 방향이 바뀌는 후보', cls: 'ink' },
        '',
        '아래 그래프가 x축을 지나는 자리가',
        '위 그래프의 봉우리와 골짜기다.',
        '',
        '부호가 바뀌지 않고 스치기만 하면',
        '극값이 아니다. y = x³ 의 원점이',
        '그런 경우다.',
    ], { gap: 24 }));
    g.push(txt(W / 2, 398, '도함수의 그래프만 있으면 원래 함수의 개형을 그릴 수 있다. 이 장의 후반부가 그 작업이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-derivative-graph',
        svg: svg({
            width: W, height: H,
            title: '도함수의 부호가 원함수의 증감을 정한다',
            desc: 'f′ 이 0 이 되는 x = ±1 에서 f 의 증가와 감소가 뒤바뀐다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-4. 평균값 정리와 롤의 정리 ---- */
add((() => {
    const W = 700, H = 330;
    const g = [BG];
    const xR = [-0.3, 4.7], yR = [-0.6, 7.2];
    const fa = frame({ xRange: xR, yRange: yR, box: { x: 56, y: 62, w: 270, h: 190 } });
    g.push(panel(56, 62, 270, 190, '평균값 정리'));
    g.push(axes(fa, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(fa.curve(v => 3 * Math.sqrt(v), { from: 0.05, to: 4.3, cls: 's1' }));
    g.push(fa.line([[0.2, 1.342], [4, 6]], { cls: 's3' }));
    const c = 1.4964, fc = 3 * Math.sqrt(c), sl = (6 - 1.342) / 3.8;
    g.push(fa.line([[0.35, fc + sl * (0.35 - c)], [3.0, fc + sl * (3.0 - c)]], { cls: 's2' }));
    g.push(fa.guide([c, 0], [c, fc]));
    g.push(pdot(fa.X(0.2), fa.Y(1.342), 'var(--ink2)', 4));
    g.push(pdot(fa.X(4), fa.Y(6), 'var(--ink2)', 4));
    g.push(pdot(fa.X(c), fa.Y(fc), 'var(--s2)', 5));
    g.push(txt(fa.X(0.2) - 6, fa.Y(0) + 17, 'a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(4) + 4, fa.Y(6) + 16, 'b', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fa.X(c), fa.Y(0) + 17, 'c', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    g.push(txt(fa.X(2.7), fa.Y(6.5), '할선', { anchor: 'middle', cls: 'f3', size: 'sm' }));
    g.push(txt(fa.X(3.1), fa.Y(3.5), '평행한 접선', { cls: 'f2', size: 'sm' }));

    const fb = frame({ xRange: xR, yRange: yR, box: { x: 390, y: 62, w: 270, h: 190 } });
    g.push(panel(390, 62, 270, 190, '롤의 정리'));
    g.push(axes(fb, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x' }));
    g.push(fb.curve(v => 1 + 3 * Math.sin((Math.PI * v) / 4), { from: 0, to: 4, cls: 's1' }));
    g.push(fb.line([[0, 1], [4, 1]], { cls: 's3' }));
    g.push(fb.line([[1, 4], [3, 4]], { cls: 's2' }));
    g.push(pdot(fb.X(0), fb.Y(1), 'var(--ink2)', 4));
    g.push(pdot(fb.X(4), fb.Y(1), 'var(--ink2)', 4));
    g.push(pdot(fb.X(2), fb.Y(4), 'var(--s2)', 5));
    g.push(fb.guide([2, 0], [2, 4]));
    g.push(txt(fb.X(0) - 6, fb.Y(0) + 17, 'a', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(4) + 6, fb.Y(0) + 17, 'b', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fb.X(2), fb.Y(0) + 17, 'c', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    g.push(txt(fb.X(2), fb.Y(4.7), '수평 접선', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    g.push(txt(fb.X(0.35), fb.Y(1.45), 'f(a) = f(b)', { cls: 'f3', size: 'sm' }));

    g.push(txt(W / 2, 288, '구간 전체의 평균 기울기와 똑같은 순간 기울기를 갖는 점이 안쪽에 반드시 있다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 312, '롤의 정리는 그 할선이 수평인 특별한 경우다. 평균값 정리의 증명이 여기서 출발한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-mvt',
        svg: svg({
            width: W, height: H,
            title: '평균값 정리와 롤의 정리',
            desc: '두 끝점을 잇는 할선과 기울기가 같은 접선을 갖는 점 c 가 구간 안에 존재한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-5. 볼록성과 변곡점 ---- */
add((() => {
    const W = 700, H = 332;
    const g = [BG];
    const xR = [-2.5, 2.5], yR = [-3.6, 3.6];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 66, y: 44, w: 350, h: 236 } });
    const fn = v => v ** 3 - 3 * v;
    g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(fn, { from: -2.05, to: 2.05, cls: 's1' }));
    const tan = (x0, from, to, cls) => {
        const y0 = fn(x0), s = 3 * x0 * x0 - 3;
        return f.line([[from, y0 + s * (from - x0)], [to, y0 + s * (to - x0)]], { cls, dash: '6 4' });
    };
    g.push(tan(-1.4, -2.05, -0.75, 's2'));
    g.push(tan(1.4, 0.75, 2.05, 's3'));
    g.push(pdot(f.X(-1.4), f.Y(fn(-1.4)), 'var(--s2)', 4.2));
    g.push(pdot(f.X(1.4), f.Y(fn(1.4)), 'var(--s3)', 4.2));
    g.push(pdot(f.X(0), f.Y(0), 'var(--ink)', 5));
    g.push(txt(f.X(0.14), f.Y(0.4), '변곡점', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(-2.4), f.Y(3.2), '위로 볼록', { cls: 'f2 bold', size: 'sm' }));
    g.push(txt(f.X(2.4), f.Y(-3.2), '아래로 볼록', { anchor: 'end', cls: 'f3 bold', size: 'sm' }));
    g.push(lines(440, 72, [
        { t: 'f″ < 0 — 위로 볼록', cls: 'f2 bold' },
        '접선이 곡선보다 위에 있다.',
        '기울기가 점점 작아진다.',
        '',
        { t: 'f″ > 0 — 아래로 볼록', cls: 'f3 bold' },
        '접선이 곡선보다 아래에 있다.',
        '기울기가 점점 커진다.',
        '',
        { t: '변곡점', cls: 'ink bold' },
        '볼록의 방향이 바뀌는 점. f″ = 0 은',
        '후보일 뿐이고 부호가 실제로',
        '바뀌어야 변곡점이다.',
    ], { gap: 20 }));
    g.push(txt(W / 2, 316, '증감이 f′ 의 부호에서 나오듯, 굽는 방향은 f″ 의 부호에서 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-concavity',
        svg: svg({
            width: W, height: H,
            title: '볼록의 방향과 변곡점',
            desc: '위로 볼록한 곳에서는 접선이 곡선 위에, 아래로 볼록한 곳에서는 접선이 곡선 아래에 있다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-6. 부호표에서 개형으로 ---- */
add((() => {
    const W = 700, H = 366;
    const g = [BG];
    const L = 96, R = 344;
    const cx = [L + 42, L + 106, L + 170];
    const mids = [(L + cx[0]) / 2, (cx[0] + cx[1]) / 2, (cx[1] + cx[2]) / 2, (cx[2] + R) / 2];
    g.push(txt(220, 40, 'y = x³ − 3x² + 2 의 부호표', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[L, 76], [R, 76]], { stroke: 'var(--ink2)', sw: 1.4 }));
    ['0', '1', '2'].forEach((m, i) => {
        g.push(ln([[cx[i], 70], [cx[i], 82]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(txt(cx[i], 64, m, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(ln([[cx[i], 84], [cx[i], 262]], { stroke: 'var(--grid)', sw: 1, dash: '4 4' }));
    });
    g.push(txt(L - 14, 80, 'x', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const rows = [
        { y: 112, name: 'f′', sg: ['+', '−', '−', '+'] },
        { y: 148, name: 'f″', sg: ['−', '−', '+', '+'] },
    ];
    for (const r of rows) {
        g.push(txt(L - 14, r.y + 5, r.name, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        r.sg.forEach((s, i) => g.push(txt(mids[i], r.y + 5, s, {
            anchor: 'middle', cls: s === '+' ? 'f3 bold' : 'f2 bold',
        })));
    }
    g.push(txt(L - 14, 196, '개형', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    [['증가', '위로 볼록'], ['감소', '위로 볼록'], ['감소', '아래로 볼록'], ['증가', '아래로 볼록']]
        .forEach((t, i) => {
            g.push(txt(mids[i], 190, t[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
            g.push(txt(mids[i], 208, t[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
    g.push(txt(L - 14, 246, '점', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx[0], 246, '극대', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx[1], 246, '변곡', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx[2], 246, '극소', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(220, 292, '표의 네 칸이 곡선의 네 토막이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(220, 316, '증감이 개형의 뼈대를 잡고', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(220, 334, '볼록이 굽는 방향을 채운다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const xR = [-1.3, 3.4], yR = [-3.2, 4.6];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 412, y: 56, w: 250, h: 240 } });
    g.push(panel(412, 56, 250, 240, '그렇게 그려진 그래프'));
    g.push(axes(f, { xRange: xR, yRange: yR, xTicks: [1, 2, 3], yTicks: [2, 4], xLabel: 'x', yLabel: 'y' }));
    g.push(f.curve(v => v ** 3 - 3 * v * v + 2, { from: -0.92, to: 3.16, cls: 's1' }));
    g.push(pdot(f.X(0), f.Y(2), 'var(--s2)', 4.8));
    g.push(pdot(f.X(2), f.Y(-2), 'var(--s2)', 4.8));
    g.push(pdot(f.X(1), f.Y(0), 'var(--ink)', 4.8));
    g.push(txt(f.X(0) + 10, f.Y(2) - 9, '극대', { cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(2) + 8, f.Y(-2) + 16, '극소', { cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(1) + 8, f.Y(0.25), '변곡', { cls: 'ink', size: 'sm' }));
    g.push(txt(W / 2, 356, '그래프를 그리는 일은 결국 f′ 과 f″ 의 부호를 구간마다 적어 보는 일이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-curve-sketch',
        svg: svg({
            width: W, height: H,
            title: '부호표에서 그래프 개형으로',
            desc: 'f′ 과 f″ 의 부호를 구간마다 적으면 증감과 볼록이 정해지고 그것이 곧 그래프의 개형이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-7. 최적화 ---- */
add((() => {
    const W = 700, H = 336;
    const g = [BG];
    g.push(panel(40, 62, 290, 200, '문제 상황'));
    g.push(ln([[62, 96], [308, 96]], { stroke: 'var(--ink2)', sw: 5, cap: 'butt' }));
    g.push(txt(185, 88, '벽 — 이쪽에는 울타리가 필요 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(box(110, 99, 160, 113, { fill: 'var(--s1)', op: 0.12, stroke: 'none', rx: 0 }));
    g.push(ln([[110, 99], [110, 212]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(ln([[270, 99], [270, 212]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(ln([[110, 212], [270, 212]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(txt(102, 160, 'x', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(278, 160, 'x', { cls: 'ink bold' }));
    g.push(txt(190, 232, 'y', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(185, 286, '울타리 길이는 2x + y = 60 m', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(185, 310, '넓이 S = xy = x(60 − 2x)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(185, 330, '변수를 하나로 줄이는 것이 첫 단계다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const xR = [-2, 34], yR = [-40, 560];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 408, y: 62, w: 244, h: 200 } });
    g.push(panel(408, 62, 244, 200, '넓이 S 를 x 의 함수로'));
    g.push(axes(f, {
        xRange: xR, yRange: yR, xTicks: [15, 30], yTicks: [450], xLabel: 'x (m)',
    }));
    g.push(txt(f.X(0) + 8, f.Y(540), 'S (m²)', { cls: 'ink2', size: 'sm' }));
    g.push(f.curve(v => v * (60 - 2 * v), { from: 0, to: 30, cls: 's1' }));
    g.push(f.guide([15, 0], [15, 450]));
    g.push(f.guide([0, 450], [15, 450]));
    g.push(pdot(f.X(15), f.Y(450), 'var(--s2)', 5.5));
    g.push(txt(f.X(15) + 10, f.Y(500), '최대 450 m²', { cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(6.5), f.Y(120), 'S′ > 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(23.5), f.Y(120), 'S′ < 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(530, 292, '정의역은 0 < x < 30 이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(530, 316, '양 끝에서 넓이가 0 이므로 최대는 안쪽에 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-optimization',
        svg: svg({
            width: W, height: H,
            title: '최적화 문제 — 조건식으로 변수를 하나로 줄인다',
            desc: '울타리 길이가 정해져 있으면 넓이는 x 하나의 함수가 되고, 그 함수의 최대를 도함수로 찾는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-8. 로피탈 정리 ---- */
add((() => {
    const W = 700, H = 330;
    const g = [BG];
    const fnf = v => v * v - 1;
    const fng = v => Math.log(v);
    const xR = [0, 2.4], yR = [-1.7, 3.4];
    const fa = frame({ xRange: xR, yRange: yR, box: { x: 56, y: 62, w: 260, h: 190 } });
    g.push(panel(56, 62, 260, 190, '멀리서 보면'));
    g.push(axes(fa, { xRange: xR, yRange: yR, xTicks: [1, 2], yTicks: [], xLabel: 'x' }));
    g.push(fa.curve(fnf, { from: 0.15, to: 2.05, cls: 's1' }));
    g.push(fa.curve(fng, { from: 0.19, to: 2.35, cls: 's2' }));
    g.push(pdot(fa.X(1), fa.Y(0), 'var(--ink)', 5));
    g.push(txt(fa.X(2.05), fa.Y(3.2), 'f(x) = x² − 1', { anchor: 'end', cls: 'f1', size: 'sm' }));
    g.push(txt(fa.X(2.35), fa.Y(1.25), 'g(x) = ln x', { anchor: 'end', cls: 'f2', size: 'sm' }));
    g.push(txt(fa.X(1.1), fa.Y(-1.2), '둘 다 x = 1 에서 0', { cls: 'ink', size: 'sm' }));

    const xR2 = [0.88, 1.16], yR2 = [-0.28, 0.36];
    const fb = frame({ xRange: xR2, yRange: yR2, box: { x: 390, y: 62, w: 260, h: 190 } });
    g.push(panel(390, 62, 260, 190, '가까이서 보면'));
    g.push(axes(fb, { xRange: xR2, yRange: yR2, xTicks: [], yTicks: [], xLabel: 'x', axisX: 1 }));
    g.push(fb.curve(fnf, { from: 0.88, to: 1.16, cls: 's1' }));
    g.push(fb.curve(fng, { from: 0.88, to: 1.16, cls: 's2' }));
    g.push(pdot(fb.X(1), fb.Y(0), 'var(--ink)', 5));
    g.push(ln([[fb.X(1.1), fb.Y(0)], [fb.X(1.1), fb.Y(fnf(1.1))]], { stroke: 'var(--s1)', sw: 3.4 }));
    g.push(ln([[fb.X(1.12), fb.Y(0)], [fb.X(1.12), fb.Y(fng(1.12))]], { stroke: 'var(--s2)', sw: 3.4 }));
    g.push(txt(fb.X(1.02), fb.Y(0.3), '두 곡선이 거의 직선이다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(fb.X(1.155), fb.Y(-0.24), '같은 자리에서 잰 높이의 비 ≈ 2', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(fb.X(1) + 5, fb.Y(0) + 16, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 288, '0/0 꼴은 두 함수가 0 에 다가가는 빠르기의 비다. 그 빠르기가 곧 도함수다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 312, '여기서는 f′(1) / g′(1) = 2 / 1 = 2 이므로 x → 1 일 때 f(x) / g(x) 는 2 로 간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'math-cal-lhopital',
        svg: svg({
            width: W, height: H,
            title: '로피탈 정리를 그림으로 — 0 으로 가는 빠르기의 비',
            desc: '같은 점에서 0 이 되는 두 함수는 가까이서 보면 직선이고, 값의 비가 기울기의 비로 간다',
            body: g.join(''),
        }),
    };
})());

export default figures;
