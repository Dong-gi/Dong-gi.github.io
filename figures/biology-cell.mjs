/**
 * 생물학 3장(생명의 특징과 연구 방법) ~ 7장(광합성)의 그림.
 *
 * chemistry-basic.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를
 * 돌려주고 name 이 파일 이름(/figures/biology/<name>.svg)이 된다.
 * 이 블록의 그림 이름은 모두 bio-cell- 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 화학식은 유니코드 아래첨자(H₂O)와 위첨자(Na⁺)로 적고, 그리스 문자도 직접 쓴다.
 * lib 의 esc 가 물결표를 tspan 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const SUP = {
    '-': '⁻', '+': '⁺', '=': '⁼', '(': '⁽', ')': '⁾', '.': '·',
    0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹',
};
const sup = s => String(s).split('').map(c => SUP[c] ?? c).join('');
const pow10 = e => `10${sup(e)}`;

/* ------------------------------------------------------------------ *
 * 공통 소도구 (화소 좌표계)
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rot = 0, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}${rot ? ` transform="rotate(${r2(rot)} ${r2(cx)} ${r2(cy)})"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.14, stroke = 'none', sw = 1, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * lib 의 px() 는 색을 CSS 클래스 이름으로 넘기는데, 이 문서의 SVG 에는 ar1/ark 같은
 * 클래스가 정의되어 있지 않아 선이 보이지 않고 화살촉만 남는다. 색을 직접 넣는다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 2, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
        ar1: 'var(--s1)', ar2: 'var(--s2)', ar3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : cls);
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 패널 테두리 + 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 라벨을 붙인 원. */
function blob(cx, cy, r, label, { cls = 's1', op = 0.2, size = 'sm', dy = 4 } = {}) {
    return circ(cx, cy, r, { fill: `var(--${cls})`, op, stroke: `var(--${cls})`, sw: 1.4 })
        + (label ? txt(cx, cy + dy, label, { anchor: 'middle', cls: 'ink', size }) : '');
}

/** 원 위의 점 좌표. 각은 도(degree), 수학 관례(반시계). */
const onCircle = (cx, cy, r, deg) => [cx + r * Math.cos((deg * Math.PI) / 180), cy - r * Math.sin((deg * Math.PI) / 180)];

/** 원호 경로. */
function arcPath(cx, cy, r, a1, a2, { stroke = 'var(--ink2)', sw = 2, marker, dash } = {}) {
    const p = a => onCircle(cx, cy, r, a).map(r2).join(' ');
    // 반시계로 갈 때 sweep-flag 는 0 이다.
    const sweep = a2 < a1 ? 1 : 0;
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    return `<path d="M${p(a1)} A${r} ${r} 0 ${large} ${sweep} ${p(a2)}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}${marker ? ` marker-end="url(#${marker})"` : ''}/>`;
}

/** 각도에 따라 바깥쪽으로 라벨을 붙인다. */
function radialLabel(cx, cy, r, deg, label, { size = 'sm', cls = 'ink' } = {}) {
    const [x, y] = onCircle(cx, cy, r, deg);
    const c = Math.cos((deg * Math.PI) / 180);
    const anchor = c > 0.35 ? 'start' : c < -0.35 ? 'end' : 'middle';
    const dy = Math.sin((deg * Math.PI) / 180) > 0.7 ? -4 : Math.sin((deg * Math.PI) / 180) < -0.7 ? 14 : 4;
    return txt(x, y + dy, label, { anchor, cls, size });
}

/* ================================================================== *
 * 3장 — 생명의 특징과 연구 방법
 * ================================================================== */

/* 3-1. 크기의 사다리 */
add((() => {
    const W = 680, H = 310;
    const x0 = 95, x1 = 605, yb = 186;
    const X = e => x0 + ((e + 10) / 10) * (x1 - x0);
    const g = [];
    g.push(txt(20, 30, '생물학이 다루는 크기 — 눈금 한 칸이 10배다', { cls: 'ink bold' }));
    g.push(line([[x0 - 16, yb], [x1 + 18, yb]], { stroke: 'var(--ink2)', sw: 1.5 }));
    const ticks = [
        [-10, '0.1 nm'], [-9, '1 nm'], [-8, '10 nm'], [-7, '100 nm'], [-6, '1 μm'],
        [-5, '10 μm'], [-4, '100 μm'], [-3, '1 mm'], [-2, '1 cm'], [-1, '10 cm'], [0, '1 m'],
    ];
    for (const [e, name] of ticks) {
        g.push(line([[X(e), yb], [X(e), yb + 8]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(e), yb + 24, name, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const items = [
        [-10, '원자 하나 0.1 nm', 1],
        [-8.7, 'DNA 이중나선 굵기 2 nm', 0],
        [-7.4, '리보솜 25 nm', 1],
        [-6.3, '세균 0.5 μm', 0],
        [-4.9, '사람 세포 20 μm', 1],
        [-3.7, '사람 난자 0.2 mm', 0],
        [-0.77, '사람 키 1.7 m', 1],
    ];
    for (const [e, name, lv] of items) {
        const yy = lv === 1 ? 82 : 118;
        g.push(line([[X(e), yy + 8], [X(e), yb - 5]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(circ(X(e), yb - 4, 3.4, { fill: 'var(--s1)', stroke: 'none', sw: 0 }));
        g.push(txt(X(e), yy, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    // 접두어 안내
    g.push(txt(20, 252, '1 mm = 10⁻³ m', { cls: 'ink2', size: 'sm' }));
    g.push(txt(150, 252, '1 μm = 10⁻⁶ m = 0.001 mm', { cls: 'ink2', size: 'sm' }));
    g.push(txt(380, 252, '1 nm = 10⁻⁹ m = 0.001 μm', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 274, '세포는 μm 자리, 세포 안의 분자는 nm 자리다. 두 자리 사이가 1000배 벌어져 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 16, 30, '왼쪽 끝과 오른쪽 끝은 100억 배 차이', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-size-ladder',
        title: '생물학이 다루는 크기의 사다리',
        desc: '원자 하나의 0.1 nm 에서 사람 키 1.7 m 까지를 한 칸이 10배인 자 위에 늘어놓았다. '
            + '세포는 마이크로미터 자리, 세포 안의 분자는 나노미터 자리에 모여 있고 두 자리 사이가 1000배다.',
        svg: svg({ width: W, height: H, title: '크기의 사다리', desc: '원자에서 사람까지의 크기를 10배 눈금 위에 늘어놓은 그림', body: g.join('') }),
    };
})());

/* 3-2. 배율과 해상도는 다르다 */
add((() => {
    const W = 660, H = 330;
    const g = [];
    g.push(txt(20, 30, '배율과 해상도는 다른 것이다', { cls: 'ink bold' }));
    g.push(txt(20, 50, '두 점이 얼마나 가까운지는 그대로인데, 크게만 키우면 흐린 덩어리가 커질 뿐이다', { cls: 'ink2', size: 'sm' }));

    // 실제 물체
    g.push(panel(30, 70, 170, 210, '실제 물체', { sub: '두 점의 거리 0.05 μm' }));
    g.push(blob(95, 190, 9, '', { cls: 's1', op: 0.5 }));
    g.push(blob(125, 190, 9, '', { cls: 's1', op: 0.5 }));
    g.push(line([[95, 218], [125, 218]], { stroke: 'var(--ink2)', sw: 1 }));
    g.push(txt(110, 236, '0.05 μm', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(206, 140, 244, 140, { cls: 'ark', marker: 'ark', width: 2 }));
    g.push(txt(225, 128, '광학', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(206, 236, 244, 236, { cls: 'ark', marker: 'ark', width: 2 }));
    g.push(txt(225, 224, '전자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 광학현미경 결과
    g.push(panel(250, 70, 190, 100, '광학현미경으로 본 상', { sub: '한계 0.2 μm' }));
    g.push(ell(345, 135, 34, 17, { fill: 'var(--s2)', op: 0.28, stroke: 'var(--s2)', sw: 1.2, dash: '4 3' }));
    g.push(txt(345, 158, '하나로 뭉쳐 보인다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 전자현미경 결과
    g.push(panel(250, 182, 190, 100, '전자현미경으로 본 상', { sub: '한계 0.1 nm' }));
    g.push(blob(325, 240, 13, '', { cls: 's3', op: 0.45 }));
    g.push(blob(367, 240, 13, '', { cls: 's3', op: 0.45 }));
    g.push(txt(346, 272, '둘로 갈라져 보인다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 설명
    g.push(txt(462, 100, '배율 (magnification)', { cls: 'ink bold' }));
    g.push(txt(462, 120, '몇 배로 키워 보이느냐.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 138, '렌즈를 더 끼우면 얼마든지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 156, '올릴 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 194, '해상도 (resolution)', { cls: 'ink bold' }));
    g.push(txt(462, 214, '얼마나 가까운 두 점까지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 232, '둘로 구별하느냐.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 250, '조명의 파장이 정한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 268, '올리려면 파장을 줄여야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 16, H - 14, '해상도를 넘겨 키운 배율을 빈 배율(empty magnification)이라 한다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-resolution',
        title: '배율과 해상도',
        desc: '0.05 μm 떨어진 두 점은 광학현미경에서 하나로 뭉쳐 보이고 전자현미경에서 둘로 갈라져 보인다. '
            + '배율은 얼마나 키워 보이느냐이고 해상도는 얼마나 가까운 두 점까지 구별하느냐다. 배율을 올려도 해상도는 오르지 않는다.',
        svg: svg({ width: W, height: H, title: '배율과 해상도', desc: '같은 두 점을 광학현미경과 전자현미경으로 본 결과 비교', body: g.join('') }),
    };
})());

/* 3-3. 관찰 수단별 분해능 범위 */
add((() => {
    const W = 680, H = 320;
    const x0 = 120, x1 = 620, yb = 250;
    const X = e => x0 + ((e + 10) / 7) * (x1 - x0);   // 10⁻¹⁰ m 에서 10⁻³ m
    const g = [];
    g.push(txt(20, 30, '무엇으로 보면 무엇이 보이는가', { cls: 'ink bold' }));
    g.push(txt(20, 50, '막대는 그 수단으로 ‘둘로 구별할 수 있는’ 범위다. 막대 왼쪽 끝이 그 수단의 분해능이다', { cls: 'ink2', size: 'sm' }));
    g.push(line([[x0 - 10, yb], [x1 + 14, yb]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (let e = -10; e <= -3; e += 1) {
        const nm = ['0.1 nm', '1 nm', '10 nm', '100 nm', '1 μm', '10 μm', '100 μm', '1 mm'][e + 10];
        g.push(line([[X(e), yb], [X(e), yb + 8]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(e), yb + 24, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const bars = [
        ['맨눈', -4, 's3'],
        ['광학현미경', Math.log10(2e-7), 's1'],
        ['초해상 형광', Math.log10(2e-8), 's2'],
        ['전자현미경', -10, 'ink2'],
    ];
    bars.forEach(([name, e, cls], i) => {
        const y = 92 + i * 34;
        const col = cls === 'ink2' ? 'var(--ink2)' : `var(--${cls})`;
        g.push(box(X(e), y - 9, X(-3) - X(e), 18, { fill: col, op: 0.22, stroke: col, sw: 1.2, rx: 4 }));
        g.push(txt(x0 - 16, y + 5, name, { anchor: 'end', cls: 'ink', size: 'sm' }));
        g.push(line([[X(e), y - 13], [X(e), yb - 4]], { stroke: col, sw: 1, dash: '3 3' }));
    });
    const marks = [[-10, '원자'], [Math.log10(2e-9), 'DNA 굵기'], [Math.log10(2.5e-8), '리보솜'], [-7, '바이러스'], [Math.log10(5e-7), '세균'], [Math.log10(2e-5), '사람 세포']];
    marks.forEach(([e, name], i) => {
        const yy = yb - 12 - (i % 2) * 20;
        g.push(circ(X(e), yb - 4, 3.2, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
        g.push(line([[X(e), yy + 4], [X(e), yb - 6]], { stroke: 'var(--grid)', sw: 1 }));
        g.push(txt(X(e), yy, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(W - 16, H - 14, '리보솜은 광학현미경 막대 바깥에 있다. 형광으로 ‘점’은 보여도 개수는 셀 수 없다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-microscope-range',
        title: '관찰 수단별 분해능',
        desc: '맨눈은 0.1 mm, 광학현미경은 0.2 μm, 초해상 형광은 20 nm, 전자현미경은 0.1 nm 아래까지 구별한다. '
            + '리보솜과 DNA 굵기는 광학현미경의 범위 밖이라서 전자현미경이 필요하다.',
        svg: svg({ width: W, height: H, title: '관찰 수단별 분해능', desc: '맨눈·광학·초해상·전자현미경이 구별할 수 있는 크기 범위', body: g.join('') }),
    };
})());

/* 3-4. 표면적 대 부피 비 */
add((() => {
    const W = 660, H = 336;
    const g = [];
    g.push(txt(20, 30, '세포가 무한히 커질 수 없는 이유', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 12], yRange: [0, 3.4], box: { x: 70, y: 60, w: 280, h: 190 } });
    g.push(f.axes({ xLabel: 'r (μm)', yLabel: 'S/V (1/μm)', xTicks: [0, 2, 4, 6, 8, 10, 12], yTicks: [0, 1, 2, 3] }));
    g.push(f.curve(r => 3 / r, { from: 0.9, to: 12, cls: 's1' }));
    g.push(f.dot([1, 3], { cls: 'f2' }));
    g.push(f.dot([5, 0.6], { cls: 'f2' }));
    g.push(f.label([1, 3], 'r = 1 μm 이면 3', { dx: 10, dy: -6, size: 'sm' }));
    g.push(f.label([5, 0.6], 'r = 5 μm 이면 0.6', { dx: 8, dy: -8, size: 'sm' }));
    g.push(txt(164, 118, 'S/V = 3/r', { cls: 'ink', size: 'sm' }));
    g.push(txt(164, 136, '— 반지름에 반비례한다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽: 부피는 세제곱, 표면은 제곱
    g.push(panel(380, 60, 260, 204, '반지름을 5배로 키우면'));
    g.push(circ(424, 196, 14, { fill: 'var(--s1)', op: 0.22, stroke: 'var(--s1)', sw: 1.4 }));
    g.push(circ(556, 186, 52, { fill: 'var(--s1)', op: 0.12, stroke: 'var(--s1)', sw: 1.4 }));
    g.push(txt(424, 226, 'r', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(556, 254, '5r', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(392, 106, '겉넓이는 25배', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 126, '부피는 125배', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 146, '→ 비는 1/5 로 준다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 300, '표면은 물질이 드나드는 문이고 부피는 그 물질을 쓰는 몸통이다. 커질수록 문이 모자란다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 320, '그래서 큰 세포는 납작해지거나(표면 확보), 길게 뻗고 능동 수송을 쓰거나, 큰 액포로 부피를 채운다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-surface-volume',
        title: '표면적 대 부피 비',
        desc: '구의 표면적 대 부피 비는 3/r 로 반지름에 반비례한다. 반지름을 5배로 키우면 겉넓이는 25배, '
            + '부피는 125배가 되어 비가 5분의 1로 줄어든다. 물질이 드나드는 문이 몸통에 비해 모자라진다.',
        svg: svg({ width: W, height: H, title: '표면적 대 부피 비', desc: 'S/V = 3/r 곡선과 크기를 키웠을 때의 비교', body: g.join('') }),
    };
})());

/* 3-5. 원핵세포와 진핵세포 */
add((() => {
    const W = 680, H = 340;
    const g = [];
    g.push(txt(20, 30, '원핵세포와 진핵세포 — 막으로 싼 방이 있는가', { cls: 'ink bold' }));

    // 원핵세포
    g.push(txt(30, 62, '원핵세포 (세균·고세균)', { cls: 'ink bold', size: 'sm' }));
    g.push(box(30, 120, 190, 96, { fill: 'var(--s1)', op: 0.08, stroke: 'var(--s1)', sw: 2, rx: 46 }));
    g.push(box(36, 126, 178, 84, { fill: 'none', stroke: 'var(--s1)', sw: 1, rx: 40, dash: '4 3' }));
    // 핵양체
    g.push(`<path d="M92 152 C110 138, 140 142, 152 158 C164 174, 140 186, 118 180 C98 175, 84 166, 92 152 Z" fill="var(--s2)" fill-opacity="0.2" stroke="var(--s2)" stroke-width="1.4"/>`);
    g.push(txt(122, 168, '핵양체', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    for (const [x, y] of [[58, 140], [70, 196], [186, 142], [176, 192], [58, 176], [196, 168]]) g.push(circ(x, y, 3.4, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    g.push(line([[220, 168], [244, 168], [252, 160], [244, 152], [252, 144]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(txt(30, 240, '· 크기 0.5-5 μm', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 258, '· 핵막이 없다. DNA 가 세포질에 그냥 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 276, '· 막으로 싼 소기관이 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 294, '· 리보솜은 있다 (작은 점)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 312, '· 대개 세포벽이 있고 편모를 갖기도 한다', { cls: 'ink2', size: 'sm' }));

    // 진핵세포
    g.push(txt(370, 62, '진핵세포 (동물·식물·균류·원생생물)', { cls: 'ink bold', size: 'sm' }));
    g.push(ell(480, 160, 108, 78, { fill: 'var(--s3)', op: 0.08, stroke: 'var(--s3)', sw: 2 }));
    g.push(circ(462, 148, 34, { fill: 'var(--s2)', op: 0.18, stroke: 'var(--s2)', sw: 1.6 }));
    g.push(circ(462, 148, 30, { fill: 'none', stroke: 'var(--s2)', sw: 1, dash: '3 3' }));
    g.push(circ(468, 142, 8, { fill: 'var(--s2)', op: 0.5, stroke: 'none', sw: 0 }));
    g.push(txt(462, 152, '핵', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(ell(548, 190, 22, 11, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)', sw: 1.3, rot: -20 }));
    g.push(ell(420, 208, 20, 10, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)', sw: 1.3, rot: 15 }));
    g.push(line([[500, 106], [530, 116], [498, 126], [532, 136]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const [x, y] of [[506, 108], [520, 120], [508, 128], [524, 136]]) g.push(circ(x, y, 2.6, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    g.push(txt(576, 104, '소포체', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(578, 108, 540, 120, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(676, 176, '미토콘드리아', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(602, 180, 570, 188, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(370, 240, '· 크기 10-100 μm (부피로는 1000배 이상)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 258, '· 핵막이 DNA 를 따로 싸고 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 276, '· 막으로 싼 소기관이 여럿 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 294, '· 리보솜이 더 크다 (80S)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 312, '· 세포벽은 식물·균류만 갖는다', { cls: 'ink2', size: 'sm' }));
    g.push(line([[340, 74], [340, 322]], { stroke: 'var(--grid)', sw: 1 }));
    return {
        name: 'bio-cell-prok-euk',
        title: '원핵세포와 진핵세포',
        desc: '원핵세포는 DNA 가 세포질에 그대로 놓여 있고 막으로 싼 소기관이 없다. 진핵세포는 핵막이 DNA 를 따로 싸고 '
            + '미토콘드리아·소포체 같은 막성 소기관을 갖는다. 갈리는 기준은 복잡함이 아니라 막으로 싼 방이 있는가다.',
        svg: svg({ width: W, height: H, title: '원핵세포와 진핵세포', desc: '두 세포 유형의 구조 비교', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 4장 — 생명의 화학
 * ================================================================== */

/* 4-1. 물의 극성과 수소 결합 */
add((() => {
    const W = 660, H = 320;
    const g = [];
    g.push(txt(20, 30, '물 분자는 한쪽으로 치우친 자석이다', { cls: 'ink bold' }));

    // 왼쪽: 물 분자 하나
    g.push(panel(30, 52, 250, 236, '분자 하나', { sub: '결합각 약 104.5°' }));
    const ox = 155, oy = 158;
    const h1 = [ox - 52, oy + 44], h2 = [ox + 52, oy + 44];
    g.push(line([[ox, oy], h1], { stroke: 'var(--ink2)', sw: 2.4 }));
    g.push(line([[ox, oy], h2], { stroke: 'var(--ink2)', sw: 2.4 }));
    g.push(blob(ox, oy, 26, 'O', { cls: 's1', op: 0.28, size: undefined, dy: 5 }));
    g.push(blob(h1[0], h1[1], 16, 'H', { cls: 's2', op: 0.28, dy: 5 }));
    g.push(blob(h2[0], h2[1], 16, 'H', { cls: 's2', op: 0.28, dy: 5 }));
    g.push(txt(ox, oy - 36, 'δ⁻ 전자가 몰린 쪽', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(h1[0] - 6, h1[1] + 34, 'δ⁺', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(h2[0] + 6, h2[1] + 34, 'δ⁺', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(155, 268, '산소가 전자를 더 세게 당긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 수소 결합 네트워크
    g.push(panel(300, 52, 330, 236, '분자 여럿', { sub: '점선이 수소 결합' }));
    const mol = (cx, cy, rot) => {
        const rad = (rot * Math.PI) / 180;
        const p = (dx, dy) => [cx + dx * Math.cos(rad) - dy * Math.sin(rad), cy + dx * Math.sin(rad) + dy * Math.cos(rad)];
        const a = p(-26, 22), b = p(26, 22);
        return line([[cx, cy], a], { stroke: 'var(--ink2)', sw: 1.8 })
            + line([[cx, cy], b], { stroke: 'var(--ink2)', sw: 1.8 })
            + blob(cx, cy, 14, 'O', { cls: 's1', op: 0.26 })
            + blob(a[0], a[1], 9, '', { cls: 's2', op: 0.26 })
            + blob(b[0], b[1], 9, '', { cls: 's2', op: 0.26 });
    };
    const centers = [[465, 130, 0], [378, 200, -35], [552, 200, 35], [465, 246, 180]];
    for (const [cx, cy, rot] of centers) g.push(mol(cx, cy, rot));
    g.push(line([[441, 150], [404, 182]], { stroke: 'var(--s3)', sw: 2, dash: '4 4' }));
    g.push(line([[489, 150], [526, 182]], { stroke: 'var(--s3)', sw: 2, dash: '4 4' }));
    g.push(line([[398, 218], [443, 240]], { stroke: 'var(--s3)', sw: 2, dash: '4 4' }));
    g.push(line([[532, 218], [487, 240]], { stroke: 'var(--s3)', sw: 2, dash: '4 4' }));
    g.push(txt(465, 100, '한 분자가 최대 4개까지 붙잡는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 308, '수소 결합 하나는 약 20 kJ/mol 로 공유 결합(약 400 kJ/mol)의 20분의 1이다. 약한 대신 수가 많고 끊임없이 다시 붙는다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-water-polarity',
        title: '물의 극성과 수소 결합',
        desc: '산소가 전자를 더 세게 당겨 산소 쪽이 음, 수소 쪽이 양으로 치우친다. 그 치우침 때문에 이웃 분자의 '
            + '수소와 산소가 서로 끌어당기는 수소 결합이 생기고, 한 분자가 최대 네 개까지 붙잡는다.',
        svg: svg({ width: W, height: H, title: '물의 극성과 수소 결합', desc: '물 분자의 부분 전하와 분자 사이 수소 결합', body: g.join('') }),
    };
})());

/* 4-2. 로그 눈금 도입 */
add((() => {
    const W = 680, H = 320;
    const x0 = 90, x1 = 620;
    const g = [];
    g.push(txt(20, 30, '십조 배 차이 나는 값을 한 줄에 적는 법', { cls: 'ink bold' }));

    // 위: 그냥 농도 자
    g.push(txt(20, 74, '농도를 그대로 (mol/L)', { cls: 'ink2', size: 'sm' }));
    const yA = 116;
    g.push(line([[x0, yA], [x1, yA]], { stroke: 'var(--ink2)', sw: 1.5 }));
    const vals = [1e-1, 1e-3, 1e-5, 1e-7, 1e-9, 1e-11, 1e-13];
    for (const v of vals) {
        const x = x0 + (v / 1e-1) * (x1 - x0);
        g.push(line([[x, yA - 7], [x, yA + 7]], { stroke: 'var(--s2)', sw: 1.6 }));
    }
    g.push(txt(x1, yA + 24, '10⁻¹', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 4, yA + 24, '나머지 여섯 눈금이 여기 전부 겹쳐 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, yA - 18, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 아래: 지수만 센 자
    g.push(txt(20, 190, '지수만 세면 (= 로그)', { cls: 'ink2', size: 'sm' }));
    const yB = 232;
    g.push(line([[x0, yB], [x1, yB]], { stroke: 'var(--ink2)', sw: 1.5 }));
    vals.forEach((v, i) => {
        const x = x0 + (i / (vals.length - 1)) * (x1 - x0);
        g.push(line([[x, yB - 7], [x, yB + 7]], { stroke: 'var(--s1)', sw: 1.6 }));
        g.push(txt(x, yB - 16, `10${sup(-1 - 2 * i)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(x, yB + 26, `${-1 - 2 * i}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x, yB + 46, `pH ${1 + 2 * i}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(20, yB + 26, 'log', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, yB + 46, '−log', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 16, H - 14, '눈금이 고르게 퍼진다. 한 칸이 10배이므로 ‘몇 배 차이인가’를 뺄셈으로 읽는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-log-scale',
        title: '로그 눈금이 필요한 이유',
        desc: '수소 이온 농도를 그대로 자 위에 찍으면 10⁻³ 아래가 전부 0 자리에 겹친다. 지수만 세면 눈금이 고르게 '
            + '퍼진다. 그 지수가 로그이고 부호를 뒤집은 것이 pH 다.',
        svg: svg({ width: W, height: H, title: '로그 눈금', desc: '농도를 그대로 찍은 자와 지수를 센 자의 비교', body: g.join('') }),
    };
})());

/* 4-3. pH 눈금과 생물학적 값들 */
add((() => {
    const W = 680, H = 300;
    const x0 = 70, x1 = 620, yb = 150;
    const X = p => x0 + (p / 14) * (x1 - x0);
    const g = [];
    g.push(txt(20, 30, 'pH 눈금 — 한 칸이 수소 이온 농도 10배', { cls: 'ink bold' }));
    g.push(box(x0, yb - 14, X(7) - x0, 28, { fill: 'var(--s2)', op: 0.14, stroke: 'none', sw: 0, rx: 0 }));
    g.push(box(X(7), yb - 14, x1 - X(7), 28, { fill: 'var(--s1)', op: 0.14, stroke: 'none', sw: 0, rx: 0 }));
    g.push(box(x0, yb - 14, x1 - x0, 28, { fill: 'none', stroke: 'var(--ink2)', sw: 1.4, rx: 0 }));
    for (let p = 0; p <= 14; p += 1) {
        g.push(line([[X(p), yb + 14], [X(p), yb + 20]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(p), yb + 36, `${p}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(X(3.5), yb + 5, '산성', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(X(10.5), yb + 5, '염기성', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 값이 7 근처에 몰려 있어 라벨을 고르게 편 뒤 꺾인 지시선으로 잇는다.
    const marks = [
        [1.8, '위액 1.8', 96], [4.8, '리소좀 안 4.8', 214], [6.8, '체온의 중성점 6.8', 336],
        [7.2, '세포질 7.2', 440], [7.4, '혈액 7.4', 526], [8.0, '엽록체 스트로마 8.0', 606],
    ];
    for (const [p, name, lx] of marks) {
        g.push(line([[lx, 84], [lx, 104], [X(p), 122], [X(p), yb - 15]], { stroke: 'var(--grid)', sw: 1 }));
        g.push(circ(X(p), yb - 14, 3.2, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
        g.push(txt(lx, 78, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(txt(20, 226, '혈액 7.4 와 리소좀 4.8 은 pH 로 2.6 차이지만 수소 이온 농도로는 약 400배 차이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 246, '사람 혈액이 7.35 아래로 내려가거나 7.45 위로 올라가면 살지 못한다. 붙잡아 두는 장치가 완충계다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 272, '중성의 정의는 pH 7 이 아니라 H⁺ 와 OH⁻ 의 농도가 같은 것이다. 체온에서 그 값은 약 6.8 이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-ph-scale',
        title: 'pH 눈금과 몸속의 값들',
        desc: '위액 1.8, 리소좀 4.8, 세포질 7.2, 혈액 7.4 를 pH 자 위에 늘어놓았다. 눈금 한 칸이 농도 10배이므로 '
            + 'pH 2.6 차이는 농도 400배 차이다. 중성점은 온도에 따라 움직이며 체온에서는 약 6.8 이다.',
        svg: svg({ width: W, height: H, title: 'pH 눈금', desc: '몸속 여러 곳의 pH 를 0에서 14 눈금 위에 표시', body: g.join('') }),
    };
})());

/* 4-4. 완충 용액의 효과 */
add((() => {
    const W = 700, H = 340;
    const g = [];
    g.push(txt(20, 30, '같은 양의 산을 넣었을 때 pH 가 얼마나 움직이는가', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 12], yRange: [1, 9], box: { x: 70, y: 66, w: 300, h: 190 } });
    g.push(f.axes({ yLabel: 'pH', xTicks: [0, 3, 6, 9, 12], yTicks: [1, 3, 5, 7, 9] }));
    g.push(txt(220, 294, '넣은 산 (mmol)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 물: 조금만 넣어도 곧바로 떨어진다
    g.push(f.curve(x => Math.min(7, 3 - Math.log10(Math.max(x, 1e-4))), { from: 0, to: 12, cls: 's2' }));
    // 완충액: 완만하다가 짝염기가 바닥나면서 무너진다
    const buf = x => (x <= 9.9
        ? 7.2 + Math.log10((10 - x) / (10 + x))
        : 3 - Math.log10(x - 9.9 + 0.0126));
    g.push(f.curve(buf, { from: 0, to: 12, cls: 's1' }));
    g.push(f.guide([0, 7.2], [9, 7.2]));
    g.push(f.label([4.5, 7.2], '완충 구간 — 기울기가 거의 없다', { dx: -84, dy: -10, size: 'sm', cls: 'ink' }));
    g.push(f.label([2.4, 2.4], '순수한 물', { dx: 0, dy: 0, size: 'sm', cls: 'ink' }));
    g.push(f.label([9.9, 4.9], '짝염기가 바닥나는 지점', { dx: -136, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(f.dot([9.9, 4.9], { cls: 'f1' }));
    g.push(legend(410, 84, [{ slot: 1, name: '완충액' }, { slot: 2, name: '순수한 물' }]));
    g.push(txt(410, 138, '완충의 원리', { cls: 'ink bold' }));
    g.push(txt(410, 160, '산을 넣으면 짝염기가 받아내고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 178, '염기를 넣으면 약산이 받아낸다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 202, '두 창고를 함께 두는 것이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 220, '완충 용액이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 244, '한쪽 창고가 비는 순간', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 262, '완충도 끝난다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 326, '완충액은 pH 를 ‘고정’하지 않는다. 움직이는 폭을 줄일 뿐이고, 쓸 수 있는 범위는 pKa 의 위아래 1 정도다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-buffer-curve',
        title: '완충 용액과 순수한 물의 차이',
        desc: '순수한 물은 산을 조금만 넣어도 pH 가 곧바로 떨어지지만, 약산과 짝염기를 함께 담은 완충액은 '
            + '한참 동안 거의 움직이지 않는다. 다만 한쪽 성분이 바닥나면 완충액도 똑같이 무너진다.',
        svg: svg({ width: W, height: H, title: '완충 용액의 효과', desc: '산을 넣을 때 물과 완충액의 pH 변화 비교', body: g.join('') }),
    };
})());

/* 4-5. 단백질의 네 수준 구조 */
add((() => {
    const W = 680, H = 300;
    const g = [];
    g.push(txt(20, 30, '단백질 구조의 네 수준 — 같은 사슬을 점점 크게 본 것이다', { cls: 'ink bold' }));
    const px0 = [24, 190, 356, 522];
    const titles = ['1차 — 서열', '2차 — 국소 모양', '3차 — 사슬 하나의 접힘', '4차 — 사슬 여럿의 조립'];
    const subs = ['아미노산의 차례', '나선과 병풍', '전체 3차원 모양', '완성된 복합체'];
    px0.forEach((x, i) => {
        g.push(panel(x, 54, 148, 196, titles[i], { sub: subs[i] }));
    });
    // 1차: 구슬 사슬
    for (let i = 0; i < 6; i += 1) {
        const cx = 44 + i * 20, cy = 150 + (i % 2) * 10;
        if (i) g.push(line([[cx - 20, 150 + ((i - 1) % 2) * 10], [cx, cy]], { stroke: 'var(--ink2)', sw: 1.6 }));
        g.push(circ(cx, cy, 8, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1.2 }));
    }
    g.push(txt(98, 200, '펩타이드 결합으로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(98, 218, '한 줄로 이어진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 2차: 나선 + 병풍
    let helix = 'M212 118';
    for (let i = 0; i < 5; i += 1) helix += ` q 12 -14 24 0 q -12 14 -24 0`;
    g.push(`<path d="M212 120 C224 104 244 104 256 120 C268 136 288 136 300 120" fill="none" stroke="var(--s2)" stroke-width="2.4"/>`);
    g.push(`<path d="M212 138 C224 122 244 122 256 138 C268 154 288 154 300 138" fill="none" stroke="var(--s2)" stroke-width="2.4"/>`);
    g.push(txt(264, 172, 'α-나선', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(212, 200, 300, 200, { cls: 's3', marker: 'ar3', width: 4 }));
    g.push(arw(300, 214, 212, 214, { cls: 's3', marker: 'ar3', width: 4 }));
    g.push(txt(264, 236, 'β-병풍', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 3차
    g.push(`<path d="M382 190 C368 150 410 118 438 138 C466 158 448 196 418 194 C398 192 396 168 414 164" fill="none" stroke="var(--s1)" stroke-width="2.6" stroke-linecap="round"/>`);
    g.push(circ(424, 162, 30, { fill: 'var(--s1)', op: 0.1, stroke: 'none', sw: 0 }));
    g.push(txt(430, 220, '소수성 부분이 안쪽으로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 238, '숨으면서 접힌다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 4차
    g.push(circ(576, 138, 24, { fill: 'var(--s1)', op: 0.26, stroke: 'var(--s1)', sw: 1.4 }));
    g.push(circ(618, 138, 24, { fill: 'var(--s2)', op: 0.26, stroke: 'var(--s2)', sw: 1.4 }));
    g.push(circ(576, 180, 24, { fill: 'var(--s2)', op: 0.26, stroke: 'var(--s2)', sw: 1.4 }));
    g.push(circ(618, 180, 24, { fill: 'var(--s1)', op: 0.26, stroke: 'var(--s1)', sw: 1.4 }));
    g.push(txt(596, 220, '헤모글로빈은 사슬 4개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 238, '모두 필요하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 282, '1차 구조만 정해지면 나머지가 대개 따라온다. 그래서 유전자가 아미노산 차례 하나만 지정해도 기능이 정해진다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-protein-levels',
        title: '단백질 구조의 네 수준',
        desc: '1차는 아미노산의 차례, 2차는 나선과 병풍 같은 국소 모양, 3차는 사슬 하나의 전체 접힘, '
            + '4차는 사슬 여럿의 조립이다. 네 가지는 다른 물질이 아니라 같은 사슬을 점점 크게 본 것이다.',
        svg: svg({ width: W, height: H, title: '단백질의 네 수준 구조', desc: '1차에서 4차까지의 단백질 구조', body: g.join('') }),
    };
})());

/* 4-6. 핵산의 구조 */
add((() => {
    const W = 680, H = 348;
    const g = [];
    g.push(txt(20, 30, 'DNA — 뉴클레오타이드 세 부품, 두 가닥, 정해진 짝', { cls: 'ink bold' }));

    // 왼쪽: 뉴클레오타이드 하나
    g.push(panel(24, 52, 210, 252, '뉴클레오타이드 하나'));
    g.push(circ(74, 130, 20, { fill: 'var(--s2)', op: 0.26, stroke: 'var(--s2)', sw: 1.4 }));
    g.push(txt(74, 135, '인산', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(poly([[104, 178], [144, 158], [176, 186], [156, 222], [112, 216]], { fill: 'var(--s3)', op: 0.24, stroke: 'var(--s3)', sw: 1.4 }));
    g.push(txt(140, 196, '5탄당', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(box(178, 118, 46, 34, { fill: 'var(--s1)', op: 0.24, stroke: 'var(--s1)', sw: 1.4, rx: 6 }));
    g.push(txt(201, 139, '염기', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(line([[88, 146], [112, 172]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(line([[152, 162], [180, 144]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(txt(130, 254, 'DNA 는 데옥시리보스 + A T G C', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(130, 272, 'RNA 는 리보스 + A U G C', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 두 가닥 사다리
    g.push(panel(252, 52, 404, 252, '두 가닥이 짝을 이룬다'));
    const yTop = 110, dy = 34;
    const pairs = [['A', 'T', 2], ['G', 'C', 3], ['T', 'A', 2], ['C', 'G', 3], ['A', 'T', 2]];
    const lx = 320, rx = 520;
    g.push(line([[lx, yTop - 18], [lx, yTop + dy * 4 + 18]], { stroke: 'var(--s1)', sw: 3 }));
    g.push(line([[rx, yTop - 18], [rx, yTop + dy * 4 + 18]], { stroke: 'var(--s1)', sw: 3 }));
    pairs.forEach(([a, b, n], i) => {
        const y = yTop + i * dy;
        g.push(box(lx + 6, y - 12, 44, 24, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.1, rx: 4 }));
        g.push(txt(lx + 28, y + 5, a, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(box(rx - 50, y - 12, 44, 24, { fill: 'var(--s3)', op: 0.22, stroke: 'var(--s3)', sw: 1.1, rx: 4 }));
        g.push(txt(rx - 28, y + 5, b, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        for (let k = 0; k < n; k += 1) {
            const yy = y - (n - 1) * 4 + k * 8;
            g.push(line([[lx + 54, yy], [rx - 54, yy]], { stroke: 'var(--ink2)', sw: 1.2, dash: '3 3' }));
        }
    });
    g.push(txt(lx, yTop - 20, "5'", { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(rx, yTop - 20, "3'", { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(lx, yTop + dy * 4 + 36, "3'", { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(rx, yTop + dy * 4 + 36, "5'", { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(646, 148, '두 가닥의', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 166, '방향이 반대다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 184, '(역평행)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 314, '가운데 점선이 수소 결합이다. A 와 T 사이에는 2개, G 와 C 사이에는 3개가 생긴다. 그래서 G-C 가 많을수록 잘 안 떨어진다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 336, 'A 는 T 하고만, G 는 C 하고만 짝을 짓는다. 한 가닥만 알면 나머지 한 가닥이 저절로 정해진다는 것이 복제의 원리다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-nucleic-acid',
        title: '핵산의 구조',
        desc: '뉴클레오타이드는 인산·5탄당·염기 세 부품으로 되어 있다. DNA 두 가닥은 방향이 반대이고 '
            + 'A 는 T 와 수소 결합 2개, G 는 C 와 3개로 짝을 짓는다. 한 가닥이 정해지면 나머지가 저절로 정해진다.',
        svg: svg({ width: W, height: H, title: '핵산의 구조', desc: '뉴클레오타이드의 세 부품과 두 가닥의 염기쌍', body: g.join('') }),
    };
})());

/* 4-7. 효소와 활성화 에너지 */
add((() => {
    const W = 660, H = 336;
    const g = [];
    g.push(txt(20, 30, '효소는 언덕을 낮춘다. 골짜기의 깊이는 건드리지 않는다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 80, y: 78, w: 380, h: 186 } });
    g.push(f.axes({ yLabel: '자유에너지', xTicks: [], yTicks: [] }));
    g.push(txt(270, 292, '반응이 진행하는 방향', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const hump = (h, x0) => x => 4 + h * Math.exp(-((x - x0) ** 2) / 1.4) - 2.2 / (1 + Math.exp(-(x - 5) * 1.6));
    g.push(f.curve(hump(5.2, 5), { from: 0.3, to: 9.7, cls: 's2' }));
    g.push(f.curve(hump(2.2, 5), { from: 0.3, to: 9.7, cls: 's1' }));
    g.push(f.guide([0.3, 4], [9.7, 4]));
    g.push(f.guide([0.3, 1.8], [9.7, 1.8]));
    g.push(f.vector([2.4, 4], [2.4, 8.4], { cls: 's2', marker: 'ar2', width: 1.8 }));
    g.push(f.vector([7.6, 4], [7.6, 5.6], { cls: 's1', marker: 'ar1', width: 1.8 }));
    g.push(f.label([2.4, 8.4], '효소 없을 때의 언덕', { dx: 6, dy: -6, size: 'sm' }));
    g.push(f.label([7.6, 5.8], '효소가 있을 때', { dx: -4, dy: -8, size: 'sm' }));
    g.push(f.label([0.4, 4], '반응물', { dx: 0, dy: -8, size: 'sm' }));
    g.push(f.label([9.7, 1.8], '생성물', { dx: -44, dy: 18, size: 'sm' }));
    g.push(f.vector([9.2, 4], [9.2, 1.8], { cls: 's3', marker: 'ar3', width: 1.8 }));
    g.push(f.label([9.2, 2.9], 'ΔG', { dx: -26, dy: 4, size: 'sm' }));
    g.push(txt(486, 90, '효소가 바꾸는 것', { cls: 'ink bold' }));
    g.push(txt(486, 112, '언덕의 높이 (반응 속도)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 150, '효소가 바꾸지 못하는 것', { cls: 'ink bold' }));
    g.push(txt(486, 172, '· 골짜기의 깊이 ΔG', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 190, '· 어느 쪽으로 갈지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 208, '· 평형에서의 비율', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 240, '정반응과 역반응을', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 258, '똑같이 빠르게 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 322, '‘효소가 없으면 그 반응은 일어나지 않는다’가 아니라 ‘일어나기는 하는데 쓸모없이 느리다’가 맞는 말이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-enzyme-energy',
        title: '효소와 활성화 에너지',
        desc: '효소는 반응물에서 생성물로 가는 길목의 언덕(활성화 에너지)을 낮춘다. 출발점과 도착점의 높이 차이 ΔG 는 '
            + '그대로이므로 반응의 방향과 평형은 바뀌지 않고 속도만 빨라진다.',
        svg: svg({ width: W, height: H, title: '효소와 활성화 에너지', desc: '효소가 있을 때와 없을 때의 반응 에너지 곡선', body: g.join('') }),
    };
})());

/* 4-8. 미카엘리스-멘텐 곡선 읽기 */
add((() => {
    const W = 690, H = 326;
    const Vm = 10, Km = 2;
    const g = [];
    g.push(txt(20, 30, '효소 반응 속도 곡선에서 두 값을 읽어낸다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 20], yRange: [0, 12], box: { x: 86, y: 84, w: 292, h: 170 } });
    g.push(f.axes({ xTicks: [0, 5, 10, 15, 20], yTicks: [0, 2, 4, 6, 8, 10, 12] }));
    g.push(txt(232, 292, '기질 농도 [S]', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(86, 74, '반응 속도 v', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.curve(x => (Vm * x) / (Km + x), { from: 0, to: 20, cls: 's1' }));
    g.push(f.line([[0, Vm], [20, Vm]], { cls: 's2', dash: '5 4' }));
    g.push(f.guide([0, Vm / 2], [Km, Vm / 2]));
    g.push(f.guide([Km, 0], [Km, Vm / 2]));
    g.push(f.dot([Km, Vm / 2], { cls: 'f2' }));
    g.push(f.label([20, Vm], 'V max', { dx: -46, dy: -8, size: 'sm', cls: 'ink' }));
    g.push(f.label([0, Vm / 2], 'V max / 2', { dx: -66, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(f.label([Km, 0], 'K M', { dx: -8, dy: 18, size: 'sm', cls: 'ink' }));
    g.push(f.curve(x => (Vm / Km) * x, { from: 0, to: 2.2, cls: 's3', dash: '4 3' }));
    g.push(f.label([12, 6.4], '아무리 기질을 넣어도', { dx: -6, dy: 0, size: 'sm', cls: 'ink' }));
    g.push(f.label([12, 5.2], '이 천장을 넘지 못한다', { dx: -6, dy: 0, size: 'sm', cls: 'ink' }));
    g.push(f.label([2.2, 11], '기울기 V max / K M', { dx: 6, dy: -4, size: 'sm', cls: 'ink2' }));
    g.push(txt(412, 96, '읽는 법', { cls: 'ink bold' }));
    g.push(txt(412, 120, 'V max: 곡선이 다가가는 천장.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 138, '효소를 두 배 넣으면 두 배가 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 164, 'K M: 속도가 천장의 절반이 되는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 182, '기질 농도. 효소량과 무관하다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 208, 'K M 이 작다 = 낮은 농도에서도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 226, '이미 빠르게 돈다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 252, '[S] 가 K M 보다 훨씬 작으면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 270, '속도가 농도에 거의 비례한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 312, 'K M 은 효소가 아니라 효소와 기질의 짝에 붙는 값이다. 같은 효소라도 기질이 다르면 K M 이 다르다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-mm-curve',
        title: '미카엘리스-멘텐 곡선 읽기',
        desc: '기질 농도를 올리면 반응 속도가 천장 V max 에 다가가며 포화한다. 속도가 천장의 절반이 되는 기질 농도가 K M 이다. '
            + '낮은 농도 구간에서는 속도가 농도에 거의 비례하고 높은 농도에서는 농도와 무관해진다.',
        svg: svg({ width: W, height: H, title: '미카엘리스-멘텐 곡선', desc: '기질 농도에 따른 반응 속도와 Km, Vmax 의 위치', body: g.join('') }),
    };
})());

/* 4-9. 두 가지 저해 */
add((() => {
    const W = 660, H = 326;
    const g = [];
    g.push(txt(20, 30, '경쟁적 저해와 비경쟁적 저해는 곡선 모양이 다르다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 20], yRange: [0, 12], box: { x: 74, y: 74, w: 330, h: 178 } });
    g.push(f.axes({ yLabel: 'v', xTicks: [0, 5, 10, 15, 20], yTicks: [0, 5, 10] }));
    g.push(txt(239, 290, '기질 농도 [S]', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.curve(x => (10 * x) / (2 + x), { from: 0, to: 20, cls: 's1' }));
    g.push(f.curve(x => (10 * x) / (6 + x), { from: 0, to: 20, cls: 's2' }));
    g.push(f.curve(x => (5 * x) / (2 + x), { from: 0, to: 20, cls: 's3' }));
    g.push(f.line([[0, 10], [20, 10]], { cls: 's1', dash: '4 4' }));
    g.push(f.line([[0, 5], [20, 5]], { cls: 's3', dash: '4 4' }));
    g.push(f.label([20, 9.1], '저해 없음', { dx: -66, dy: 0, size: 'sm' }));
    g.push(f.label([20, 7.2], '경쟁적', { dx: -46, dy: 0, size: 'sm' }));
    g.push(f.label([20, 4.1], '비경쟁적', { dx: -58, dy: 0, size: 'sm' }));
    g.push(txt(430, 84, '경쟁적 (competitive)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(430, 104, '저해제가 기질 자리에 앉는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 122, 'V max 는 그대로, K M 만 커진다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 140, '기질을 많이 넣으면 이길 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 176, '비경쟁적 (noncompetitive)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(430, 196, '저해제가 다른 자리에 앉아', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 214, '효소 모양을 바꾼다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 232, 'V max 가 내려가고 K M 은 그대로.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 250, '기질을 늘려도 이기지 못한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 314, '구별하는 물음은 하나다. 기질 농도를 아주 높이면 원래 속도를 되찾는가. 되찾으면 경쟁적, 못 되찾으면 비경쟁적이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-inhibition',
        title: '경쟁적 저해와 비경쟁적 저해',
        desc: '경쟁적 저해는 곡선이 오른쪽으로 늘어져 K M 이 커지지만 천장 V max 는 그대로다. 비경쟁적 저해는 '
            + '천장 자체가 내려간다. 기질을 아주 많이 넣었을 때 원래 속도를 되찾는지로 구별한다.',
        svg: svg({ width: W, height: H, title: '효소 저해의 두 유형', desc: '저해 없음, 경쟁적, 비경쟁적 세 곡선의 비교', body: g.join('') }),
    };
})());

/* 3-6. 대조군이 하는 일 */
add((() => {
    const W = 700, H = 320;
    const g = [];
    g.push(txt(20, 30, '대조군을 두지 않으면 효과를 부풀려 읽는다', { cls: 'ink bold' }));
    g.push(txt(20, 50, '같은 종자를 네 조건에 나누어 심고 발아율을 세었다', { cls: 'ink2', size: 'sm' }));
    const f = frame({ xRange: [0, 4], yRange: [0, 100], box: { x: 74, y: 86, w: 250, h: 160 } });
    g.push(f.axes({ yLabel: '발아율 (%)', xTicks: [], yTicks: [0, 20, 40, 60, 80, 100] }));
    const bars = [
        [0.5, 60, '①', 's3'], [1.5, 70, '②', 's2'], [2.5, 75, '③', 's1'], [3.5, 82, '④', 's3'],
    ];
    for (const [x, v, mark, cls] of bars) {
        const x1 = f.X(x - 0.3), x2 = f.X(x + 0.3);
        g.push(box(x1, f.Y(v), x2 - x1, f.Y(0) - f.Y(v), { fill: `var(--${cls})`, op: 0.28, stroke: `var(--${cls})`, sw: 1.4, rx: 3 }));
        g.push(txt((x1 + x2) / 2, f.Y(v) - 8, `${v}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt((x1 + x2) / 2, f.Y(0) + 20, mark, { anchor: 'middle', cls: 'ink' }));
    }
    g.push(txt(360, 88, '① 아무것도 넣지 않음', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 106, '② 용매만 넣음 — 음성 대조군', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 124, '③ 용매에 녹인 추출물 — 처리군', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 142, '④ 이미 아는 촉진제 — 양성 대조군', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 176, '③ 을 ① 과 견주면 +15 로 보인다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(360, 194, '그런데 ② 가 이미 +10 이다. 용매가 올린 몫이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 212, '추출물 자체의 몫은 ③ − ② = +5 뿐이다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(360, 240, '④ 가 오르지 않았다면 추출물을 논하기 전에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 258, '실험계 자체가 작동하는지를 의심해야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 296, '음성 대조군은 ‘조작 자체의 효과’를 걸러 내고, 양성 대조군은 ‘실험이 아예 안 돌아가는 경우’를 걸러 낸다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-controls',
        title: '음성 대조군과 양성 대조군',
        desc: '무처리 60%, 용매만 70%, 추출물 75%, 알려진 촉진제 82%. 처리군을 무처리와 견주면 효과가 15포인트로 '
            + '보이지만 용매만으로 이미 10포인트가 올랐으므로 추출물의 몫은 5포인트다. 양성 대조군은 실험계 자체의 작동을 확인한다.',
        svg: svg({ width: W, height: H, title: '대조군의 역할', desc: '네 조건의 발아율 막대와 각 대조군이 걸러 내는 것', body: g.join('') }),
    };
})());

/* 4-10. 포화 지방산과 불포화 지방산 */
add((() => {
    const W = 680, H = 320;
    const g = [];
    g.push(txt(20, 30, '탄소 수가 같아도 꺾이면 녹는점이 내려간다', { cls: 'ink bold' }));

    g.push(panel(24, 52, 300, 200, '포화 — 곧게 뻗는다', { sub: '이중 결합이 없다' }));
    for (let k = 0; k < 3; k += 1) {
        const x = 70 + k * 34;
        const pts = [];
        for (let i = 0; i < 9; i += 1) pts.push([x + (i % 2) * 10, 116 + i * 14]);
        g.push(line(pts, { stroke: 'var(--s1)', sw: 2.2 }));
        g.push(circ(x + 4, 106, 7, { fill: 'var(--s1)', op: 0.32, stroke: 'var(--s1)', sw: 1.2 }));
    }
    g.push(txt(190, 132, '나란히 붙는 면이 넓다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(190, 150, '→ 서로 당기는 힘의 합이 크다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(190, 168, '→ 떼어 놓기 어렵다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(190, 196, '녹는점이 높다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(190, 214, '상온에서 고체', { cls: 'ink2', size: 'sm' }));

    g.push(panel(348, 52, 306, 200, 'cis 불포화 — 꺾인다', { sub: '이중 결합 자리에서 약 30° 굽는다' }));
    for (let k = 0; k < 3; k += 1) {
        const x = 392 + k * 46;
        const pts = [];
        for (let i = 0; i < 5; i += 1) pts.push([x + (i % 2) * 10, 116 + i * 14]);
        for (let i = 0; i < 5; i += 1) pts.push([x + 12 + i * 8, 186 + (i % 2) * 10]);
        g.push(line(pts, { stroke: 'var(--s2)', sw: 2.2 }));
        g.push(circ(x + 4, 106, 7, { fill: 'var(--s2)', op: 0.32, stroke: 'var(--s2)', sw: 1.2 }));
        g.push(circ(x + 5, 180, 3.4, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
    }
    g.push(txt(560, 132, '꺾여서 밀착하지 못한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(560, 150, '→ 닿는 면이 좁다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(560, 168, '→ 쉽게 흐트러진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(560, 196, '녹는점이 낮다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(560, 214, '상온에서 액체', { cls: 'ink2', size: 'sm' }));

    g.push(txt(24, 278, '검은 점이 cis 이중 결합 자리다. 탄소 수는 양쪽 모두 18개로 같고 다른 것은 이중 결합 하나뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 300, '같은 원리가 세포막에도 적용된다. 추운 곳에 사는 생물은 막 지질의 불포화 비율을 올려 막이 굳는 것을 막는다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-fatty-acid',
        title: '포화 지방산과 불포화 지방산',
        desc: '포화 지방산은 곧게 뻗어 넓은 면으로 맞닿으므로 분자 사이 인력의 합이 커서 녹는점이 높다. '
            + 'cis 이중 결합은 사슬을 꺾어 밀착을 막으므로 녹는점이 낮다. 탄소 수가 같아도 결과가 크게 달라진다.',
        svg: svg({ width: W, height: H, title: '포화와 불포화 지방산', desc: '곧은 사슬과 꺾인 사슬의 쌓임 차이', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 5장 — 세포 구조와 막
 * ================================================================== */

/* 5-1. 세포골격 세 가지 */
add((() => {
    const W = 690, H = 330;
    const g = [];
    g.push(txt(20, 30, '세포골격 셋은 굵기도 하는 일도 다르다', { cls: 'ink bold' }));
    const cols = [24, 246, 468];
    const titles = ['미세소관', '액틴 필라멘트', '중간섬유'];
    const subs = ['지름 약 25 nm', '지름 약 7 nm', '지름 약 10 nm'];
    cols.forEach((x, i) => g.push(panel(x, 52, 200, 216, titles[i], { sub: subs[i] })));

    // 미세소관: 속 빈 관
    g.push(box(60, 96, 128, 40, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--s1)', sw: 1.6, rx: 8 }));
    for (let i = 0; i < 8; i += 1) {
        g.push(circ(72 + i * 16, 105, 6, { fill: 'var(--s1)', op: 0.42, stroke: 'var(--s1)', sw: 1 }));
        g.push(circ(72 + i * 16, 127, 6, { fill: 'var(--s2)', op: 0.42, stroke: 'var(--s2)', sw: 1 }));
    }
    g.push(txt(124, 154, '튜불린 두 종류가 관을 이룬다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 182, '· 양 끝의 성질이 다르다 (극성)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 200, '· 키네신·다이닌이 이 위를 걷는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 218, '· 소포 장거리 운반, 섬모와 편모', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 236, '· 분열 때 방추사가 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 254, '· 자랐다 무너졌다를 되풀이한다', { cls: 'ink2', size: 'sm' }));

    // 액틴: 두 가닥 꼬임
    const ax0 = 268, ax1 = 410;
    let p1 = `M${ax0} 116`, p2 = `M${ax0} 116`;
    for (let i = 0; i < 6; i += 1) {
        const xa = ax0 + (i * (ax1 - ax0)) / 6, xb = ax0 + ((i + 1) * (ax1 - ax0)) / 6;
        p1 += ` Q${(xa + xb) / 2} ${i % 2 ? 138 : 94} ${xb} 116`;
        p2 += ` Q${(xa + xb) / 2} ${i % 2 ? 94 : 138} ${xb} 116`;
    }
    g.push(`<path d="${p1}" fill="none" stroke="var(--s3)" stroke-width="3"/>`);
    g.push(`<path d="${p2}" fill="none" stroke="var(--s1)" stroke-width="3"/>`);
    g.push(txt(340, 160, '두 가닥이 서로 꼬여 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(262, 182, '· 극성이 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(262, 200, '· 미오신이 이 위를 걷는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(262, 218, '· 세포 표면 바로 아래에 그물을 이룬다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(262, 236, '· 세포가 기어가는 데 쓰인다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(262, 254, '· 분열 끝에 세포를 조르는 고리', { cls: 'ink2', size: 'sm' }));

    // 중간섬유: 밧줄
    for (let k = 0; k < 4; k += 1) {
        const y = 104 + k * 8;
        let d = `M492 ${y}`;
        for (let i = 0; i < 7; i += 1) d += ` q9 ${i % 2 ? 6 : -6} 18 0`;
        g.push(`<path d="${d}" fill="none" stroke="var(--ink2)" stroke-width="2"/>`);
    }
    g.push(txt(566, 160, '여러 가닥을 꼰 밧줄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(484, 182, '· 극성이 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(484, 200, '· 운동단백질이 붙지 않는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(484, 218, '· 잡아당기는 힘을 견딘다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(484, 236, '· 핵막 안쪽을 받친다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(484, 254, '· 셋 중 가장 안정하다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, 292, '굵기 순서는 액틴(7 nm) < 중간섬유(10 nm) < 미세소관(25 nm) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 312, '앞의 둘은 뉴클레오타이드를 쓰며 끊임없이 조립과 해체를 되풀이한다. 뼈대라기보다 늘 다시 지어지는 비계에 가깝다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-cytoskeleton',
        title: '세포골격 세 가지',
        desc: '미세소관은 지름 25 nm 의 속 빈 관으로 극성이 있고 운동단백질의 궤도가 된다. 액틴 필라멘트는 7 nm 로 '
            + '표면 아래 그물과 수축환을 만든다. 중간섬유는 10 nm 밧줄로 극성이 없고 잡아당기는 힘을 견딘다.',
        svg: svg({ width: W, height: H, title: '세포골격 세 가지', desc: '미세소관·액틴 필라멘트·중간섬유의 구조와 성질 비교', body: g.join('') }),
    };
})());

/* 5-2. 세포막의 유동 모자이크 구조 */
add((() => {
    const W = 720, H = 366;
    const g = [];
    g.push(txt(20, 30, '세포막 — 2차원 액체 위에 단백질이 떠 있다', { cls: 'ink bold' }));
    const yT = 160, yB = 240, mid = 200;
    const head = (x, y, up) => circ(x, y, 7, { fill: 'var(--s1)', op: 0.34, stroke: 'var(--s1)', sw: 1.1 })
        + line([[x - 3, y + (up ? 7 : -7)], [x - 4, y + (up ? 30 : -30)]], { stroke: 'var(--ink2)', sw: 1.3 })
        + line([[x + 3, y + (up ? 7 : -7)], [x + 5, y + (up ? 30 : -30)]], { stroke: 'var(--ink2)', sw: 1.3 });
    const heads = [];
    for (let x = 60; x <= 240; x += 20) heads.push(x);
    for (const x of [430, 450, 470, 552, 572, 592, 646, 666, 686]) heads.push(x);
    for (const x of heads) { g.push(head(x, yT, true)); g.push(head(x, yB, false)); }
    g.push(txt(20, 118, '세포 바깥', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 282, '세포 안쪽 (세포질)', { cls: 'ink2', size: 'sm' }));

    // 막관통 통로 단백질
    g.push(box(250, 142, 76, 116, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.6, rx: 14 }));
    g.push(txt(288, 204, '통로', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(288, 118, '막관통 단백질', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 당사슬이 붙은 단백질
    g.push(box(490, 142, 48, 116, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.6, rx: 12 }));
    for (const [dx, dy] of [[0, -16], [-13, -30], [13, -30]]) g.push(circ(514 + dx, 142 + dy, 5, { fill: 'var(--s3)', op: 0.5, stroke: 'var(--s3)', sw: 1 }));
    g.push(txt(514, 84, '당사슬 — 바깥쪽에만 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 콜레스테롤
    g.push(box(606, 164, 26, 30, { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)', sw: 1.3, rx: 4 }));
    g.push(box(606, 206, 26, 30, { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)', sw: 1.3, rx: 4 }));
    g.push(txt(704, 118, '콜레스테롤', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(660, 122, 626, 160, { cls: 'ark', marker: 'ark', width: 1.2 }));
    // 주변 단백질
    g.push(ell(172, 268, 30, 12, { fill: 'var(--s2)', op: 0.18, stroke: 'var(--s2)', sw: 1.4 }));
    g.push(txt(172, 300, '주변 단백질 — 막에 얹혀 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 두께
    g.push(line([[46, 152], [46, 248]], { stroke: 'var(--ink2)', sw: 1 }));
    g.push(line([[42, 152], [50, 152]], { stroke: 'var(--ink2)', sw: 1 }));
    g.push(line([[42, 248], [50, 248]], { stroke: 'var(--ink2)', sw: 1 }));
    g.push(txt(40, mid + 4, '약 4 nm', { anchor: 'end', cls: 'ink', size: 'sm' }));

    // 지질의 두 가지 움직임 (빈 자리에 그린다)
    g.push(arw(346, 160, 408, 160, { cls: 'ar1', marker: 'ar1', width: 1.8 }));
    g.push(txt(377, 146, '옆으로 미끄러짐 — 빠르다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(377, 172, 377, 228, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(txt(377, 258, '층을 넘기', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(377, 276, '— 저절로는 매우 느리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(20, 330, '두 층의 조성이 다르다는 것이 요점이다. 당사슬은 언제나 바깥쪽에만 있고, 층을 넘는 일이 거의 없으므로 그 비대칭이 유지된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 352, '‘유동’은 지질이 옆으로 잘 흐른다는 뜻이고, ‘모자이크’는 단백질이 그 안에 조각처럼 박혀 있다는 뜻이다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-membrane',
        title: '세포막의 유동 모자이크 구조',
        desc: '인지질 두 층이 꼬리를 맞대고 있고 그 사이에 막관통 단백질과 콜레스테롤이 박혀 있다. 두께는 약 4 nm 다. '
            + '지질은 같은 층 안에서는 빠르게 미끄러지지만 층을 넘는 일은 매우 드물어서 안팎의 조성 차이가 유지된다.',
        svg: svg({ width: W, height: H, title: '세포막의 구조', desc: '인지질 이중층과 막단백질, 당사슬, 콜레스테롤의 배치', body: g.join('') }),
    };
})());

/* 5-3. 수송 속도가 포화하는가 */
add((() => {
    const W = 690, H = 336;
    const g = [];
    g.push(txt(20, 30, '농도를 올려 보면 어떤 수송인지 갈린다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 20], yRange: [0, 12], box: { x: 76, y: 80, w: 296, h: 176 } });
    g.push(f.axes({ xLabel: '바깥 농도', yLabel: '들어오는 속도', xTicks: [0, 5, 10, 15, 20], yTicks: [0, 4, 8, 12] }));
    g.push(f.curve(x => 0.5 * x, { from: 0, to: 20, cls: 's2' }));
    g.push(f.curve(x => (10 * x) / (3 + x), { from: 0, to: 20, cls: 's1' }));
    g.push(f.line([[0, 10], [20, 10]], { cls: 's1', dash: '4 4' }));
    g.push(f.label([20, 10], '천장', { dx: -34, dy: -8, size: 'sm', cls: 'ink' }));
    g.push(f.label([15.5, 7.8], '단순 확산', { dx: 4, dy: -6, size: 'sm', cls: 'ink' }));
    g.push(f.label([13, 8.2], '수송체를 쓰는 수송', { dx: -128, dy: 20, size: 'sm', cls: 'ink' }));
    g.push(txt(410, 84, '직선이면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(410, 104, '결합할 자리가 없다는 뜻이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 122, '지질 이중층을 그냥 통과한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 140, '→ 단순 확산', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 174, '천장이 있으면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(410, 194, '결합할 자리의 수가 정해져 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 212, '즉 단백질이 관여한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(410, 230, '→ 촉진 확산이거나 능동 수송', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 304, '천장이 보인 뒤 둘을 더 가르려면 물음을 하나 더 던진다. 농도가 낮은 쪽으로만 가는가, 높은 쪽으로도 밀어 올리는가.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 326, '밀어 올린다면 에너지를 쓰고 있다는 뜻이므로 능동 수송이다. 이 곡선은 4장의 효소 포화 곡선과 모양이 같다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-transport-graph',
        title: '수송 속도의 포화 여부',
        desc: '바깥 농도를 올릴 때 속도가 끝없이 비례해 오르면 결합 자리가 없다는 뜻이고 단순 확산이다. '
            + '천장에 다가가며 포화하면 수가 정해진 결합 자리, 즉 수송 단백질이 관여한다는 뜻이다.',
        svg: svg({ width: W, height: H, title: '수송 속도와 농도', desc: '단순 확산의 직선과 수송체를 쓰는 수송의 포화 곡선', body: g.join('') }),
    };
})());

/* 5-4. 삼투 — 세 가지 용액에 넣은 두 세포 */
add((() => {
    const W = 700, H = 350;
    const g = [];
    g.push(txt(20, 30, '같은 용액이라도 세포벽이 있으면 결과가 다르다', { cls: 'ink bold' }));
    const cx = [140, 350, 560];
    const heads = ['저장액 (바깥이 묽다)', '등장액 (같다)', '고장액 (바깥이 진하다)'];
    heads.forEach((h, i) => g.push(txt(cx[i], 62, h, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    g.push(txt(20, 100, '동물세포', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 222, '식물세포', { cls: 'ink2', size: 'sm' }));
    g.push(line([[100, 178], [660, 178]], { stroke: 'var(--grid)', sw: 1 }));

    // 동물세포
    const animal = [[cx[0], 34, '부풀다 터진다', 's2'], [cx[1], 26, '그대로', 's1'], [cx[2], 18, '쭈그러든다', 's3']];
    for (const [x, r, label, cls] of animal) {
        g.push(circ(x, 122, r, { fill: `var(--${cls})`, op: 0.22, stroke: `var(--${cls})`, sw: 1.8 }));
        if (r === 34) for (let k = 0; k < 6; k += 1) g.push(line([[x + 34 * Math.cos(k), 122 + 34 * Math.sin(k)], [x + 46 * Math.cos(k), 122 + 46 * Math.sin(k)]], { stroke: 'var(--s2)', sw: 1.2 }));
        if (r === 18) g.push(circ(x, 122, 26, { fill: 'none', stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(txt(x, 168, label, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    // 식물세포
    const plant = [[cx[0], '팽팽해진다 (팽윤)', 1], [cx[1], '약간 늘어진다', 0.86], [cx[2], '원형질분리', 0.64]];
    for (const [x, label, s] of plant) {
        g.push(box(x - 44, 206, 88, 74, { fill: 'none', stroke: 'var(--ink2)', sw: 2.4, rx: 4 }));
        const w = 82 * s, h = 68 * s;
        g.push(box(x - w / 2, 243 - h / 2, w, h, { fill: 'var(--s3)', op: 0.2, stroke: 'var(--s3)', sw: 1.6, rx: 6 }));
        g.push(txt(x, 300, label, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(txt(140, 198, '세포벽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(146, 194, 158, 206, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(20, 328, '물은 용질이 적은 쪽에서 많은 쪽으로 간다. 동물세포에는 이 압력을 버틸 것이 없어 저장액에서 터지고, 식물세포는 세포벽이 받쳐 준다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-osmosis',
        title: '삼투 — 세 가지 용액 속의 동물세포와 식물세포',
        desc: '저장액에서 동물세포는 부풀어 터지지만 식물세포는 세포벽이 받쳐 팽팽해질 뿐이다. 고장액에서는 둘 다 물을 잃고, '
            + '식물세포에서는 세포막이 벽에서 떨어지는 원형질분리가 일어난다.',
        svg: svg({ width: W, height: H, title: '삼투와 세포의 부피', desc: '저장액·등장액·고장액에서 동물세포와 식물세포의 변화', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 6장 — 세포 호흡과 발효
 * ================================================================== */

/* 6-1. 해당과정의 ATP 수지 */
add((() => {
    const W = 690, H = 348;
    const g = [];
    g.push(txt(20, 30, '해당과정 — 먼저 2개를 쓰고 나중에 4개를 번다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 10], yRange: [-3, 3], box: { x: 84, y: 82, w: 300, h: 176 } });
    g.push(f.axes({ yLabel: '누적 ATP', xTicks: [0, 2, 4, 6, 8, 10], yTicks: [-3, -2, -1, 0, 1, 2, 3] }));
    g.push(txt(234, 296, '해당과정의 열 단계', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const steps = [[0, 0], [1, 0], [1, -1], [3, -1], [3, -2], [7, -2], [7, 0], [10, 0], [10, 2]];
    g.push(f.line(steps, { cls: 's1' }));
    g.push(f.dot([1, -1], { cls: 'f2' }));
    g.push(f.dot([3, -2], { cls: 'f2' }));
    g.push(f.dot([7, 0], { cls: 'f1' }));
    g.push(f.dot([10, 2], { cls: 'f1' }));
    g.push(f.label([1, -1], '헥소키네이스 −1', { dx: -18, dy: 18, size: 'sm', anchor: 'middle' }));
    g.push(f.label([3, -2], 'PFK−1 −1', { dx: 0, dy: 20, size: 'sm', anchor: 'middle' }));
    g.push(f.label([7, 0], '+2 (3탄당 2개 × 1)', { dx: -60, dy: -10, size: 'sm' }));
    g.push(f.label([10, 2], '+2', { dx: -20, dy: -10, size: 'sm' }));
    g.push(f.guide([0, 0], [10, 0]));
    g.push(txt(432, 84, '투자기 (1-5단계)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(432, 104, 'ATP 2개를 써서 포도당에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 122, '인산을 붙인다. 그러면 세포', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 140, '밖으로 새 나가지 못하고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 158, '쪼개지기 쉬운 모양이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 190, '회수기 (6-10단계)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(432, 210, '6탄당이 3탄당 2개로 갈라진', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 228, '뒤이므로 모든 단계를 두 번', { cls: 'ink2', size: 'sm' }));
    g.push(txt(432, 246, '센다. ATP 4개와 NADH 2개.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 322, '순수지 = −2 + 4 = +2 ATP. 여기에 NADH 2개가 따로 남는다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(20, 342, '가장 흔한 실수는 갈라진 뒤의 단계에 2를 곱하는 것을 잊는 것이다. 갈라지기 전 단계는 그대로 한 번씩 센다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-glycolysis-atp',
        title: '해당과정의 ATP 수지',
        desc: '투자기에서 ATP 2개를 쓰고 회수기에서 4개를 벌어 순수지는 +2 다. 회수기는 6탄당이 3탄당 두 개로 '
            + '갈라진 뒤이므로 각 단계를 두 번 세야 한다는 점이 계산의 핵심이다.',
        svg: svg({ width: W, height: H, title: '해당과정 ATP 누적', desc: '단계별 ATP 누적 그래프와 투자기·회수기의 구분', body: g.join('') }),
    };
})());

/* 6-2. 전자가 떨어지는 계단 */
add((() => {
    const W = 680, H = 330;
    const g = [];
    g.push(txt(20, 30, '전자는 계단을 굴러떨어지고, 세 칸에서 일이 뽑혀 나온다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 10], yRange: [-0.5, 1.0], box: { x: 92, y: 62, w: 380, h: 200 } });
    g.push(f.axes({ yLabel: '전자를 끌어당기는 세기 (V)', xTicks: [], yTicks: [-0.4, 0, 0.4, 0.8] }));
    const stops = [
        [0.6, -0.32, 'NADH'], [2.4, 0.03, '유비퀴논'], [4.8, 0.25, '사이토크롬 c'], [7.2, 0.82, 'O₂ → 물'],
    ];
    for (let i = 0; i < stops.length; i += 1) {
        const [x, y, name] = stops[i];
        g.push(f.line([[x - 0.55, y], [x + 0.55, y]], { cls: 's1' }));
        g.push(f.dot([x, y], { cls: 'f1' }));
        g.push(f.label([x, y], name, { dx: 0, dy: -12, size: 'sm', anchor: 'middle' }));
        if (i < stops.length - 1) {
            const [x2, y2] = stops[i + 1];
            g.push(f.vector([x + 0.55, y], [x2 - 0.55, y2], { cls: 's2', marker: 'ar2', width: 1.8 }));
        }
    }
    g.push(f.label([1.5, -0.44], '복합체 I', { dy: 0, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([3.6, -0.44], '복합체 III', { dy: 0, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([6.0, -0.44], '복합체 IV', { dy: 0, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.line([[8.6, -0.32], [8.6, 0.82]], { cls: 's3', dash: '4 3' }));
    g.push(f.label([8.6, 0.25], '전체 1.14 V', { dx: 8, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(txt(20, 288, '세로축이 클수록 전자를 세게 당긴다. 전자는 약하게 당기는 쪽(NADH)에서 세게 당기는 쪽(산소)으로 간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 310, '한 번에 떨어뜨리면 그 에너지가 전부 열이 된다. 세 칸으로 나누어 떨어뜨려야 각 칸에서 양성자를 퍼낼 수 있다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-redox-ladder',
        title: '전자전달계의 계단',
        desc: 'NADH 의 −0.32 V 에서 산소의 +0.82 V 까지, 전자는 모두 1.14 V 만큼의 계단을 굴러떨어진다. '
            + '중간에 유비퀴논과 사이토크롬 c 를 두어 세 칸으로 나누고, 각 칸에서 양성자를 퍼내는 일을 뽑아 쓴다.',
        svg: svg({ width: W, height: H, title: '전자전달계의 전위 계단', desc: 'NADH 에서 산소까지 전자가 내려가는 단계와 세 복합체', body: g.join('') }),
    };
})());

/* 6-3. 화학삼투 */
add((() => {
    const W = 720, H = 414;
    const g = [];
    g.push(txt(20, 30, '화학삼투 — 전자의 에너지를 일단 ‘양성자 댐’에 저장한다', { cls: 'ink bold' }));
    const yT = 200, yB = 250;
    g.push(txt(20, 62, '막간 공간 — 양성자가 쌓인다 (산성 쪽)', { cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < 16; i += 1) g.push(txt(56 + i * 42, 88, 'H⁺', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(box(36, yT, 664, yB - yT, { fill: 'var(--s1)', op: 0.1, stroke: 'var(--s1)', sw: 1.4, rx: 3 }));

    const comp = (x, w, label) => box(x, yT - 16, w, (yB - yT) + 32, { fill: 'var(--s2)', op: 0.2, stroke: 'var(--s2)', sw: 1.5, rx: 8 })
        + txt(x + w / 2, yT + 30, label, { anchor: 'middle', cls: 'ink' });
    g.push(comp(60, 76, 'I'));
    g.push(comp(176, 62, 'II'));
    g.push(comp(300, 76, 'III'));
    g.push(comp(434, 76, 'IV'));
    // ATP 합성효소
    g.push(box(578, yT - 16, 52, (yB - yT) + 32, { fill: 'var(--s3)', op: 0.2, stroke: 'var(--s3)', sw: 1.5, rx: 8 }));
    g.push(line([[604, 266], [604, 280]], { stroke: 'var(--s3)', sw: 4 }));
    g.push(circ(604, 308, 30, { fill: 'var(--s3)', op: 0.18, stroke: 'var(--s3)', sw: 1.6 }));
    g.push(txt(604, 304, 'ATP', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(604, 320, '합성효소', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 양성자 펌프
    for (const [x, label] of [[98, '4 H⁺'], [338, '4 H⁺'], [472, '2 H⁺']]) {
        g.push(arw(x, 274, x, 106, { cls: 'ar2', marker: 'ar2', width: 2 }));
        g.push(txt(x + 10, 148, label, { cls: 'ink', size: 'sm' }));
    }
    g.push(arw(604, 106, 604, 190, { cls: 'ar3', marker: 'ar3', width: 2.4 }));
    g.push(txt(614, 148, '되돌아 내려온다', { cls: 'ink', size: 'sm' }));

    // 전자의 길 (점선)
    g.push(circ(268, 226, 10, { fill: 'var(--ink2)', op: 0.14, stroke: 'var(--ink2)', sw: 1 }));
    g.push(txt(268, 230, 'Q', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(circ(406, 186, 10, { fill: 'var(--ink2)', op: 0.14, stroke: 'var(--ink2)', sw: 1 }));
    g.push(txt(406, 190, 'c', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(140, 226, 254, 226, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(arw(238, 244, 262, 234, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(arw(282, 226, 296, 226, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(arw(380, 194, 394, 188, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(arw(418, 188, 432, 194, { cls: 'ark', marker: 'ark', width: 1.4, dash: '4 3' }));
    g.push(txt(196, 214, '전자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(38, 302, 'NADH → NAD⁺', { cls: 'ink2', size: 'sm' }));
    g.push(txt(160, 302, '석신산 → 푸마르산', { cls: 'ink2', size: 'sm' }));
    g.push(txt(400, 302, '½O₂ + 2H⁺ → H₂O', { cls: 'ink2', size: 'sm' }));
    for (const x of [80, 200, 320, 440]) g.push(txt(x, 348, 'H⁺', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(604, 352, 'ADP + Pi → ATP', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(20, 378, '기질 — 양성자가 적다 (염기성 쪽)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 402, '복합체 II 는 양성자를 퍼내지 않는다. 그래서 FADH₂ 로 들어온 전자는 ATP 를 덜 만든다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-etc-chemiosmosis',
        title: '전자전달계와 화학삼투',
        desc: '복합체 I, III, IV 가 전자의 에너지를 써서 양성자를 막간 공간으로 퍼내고, 쌓인 양성자가 ATP 합성효소를 '
            + '통해 되돌아오면서 ATP 를 만든다. 복합체 II 는 퍼내지 않으므로 FADH₂ 의 몫이 작다.',
        svg: svg({ width: W, height: H, title: '화학삼투', desc: '미토콘드리아 내막의 복합체 배치와 양성자 흐름', body: g.join('') }),
    };
})());

/* 6-4. 포도당 하나의 ATP 수지 */
add((() => {
    const W = 690, H = 356;
    const g = [];
    g.push(txt(20, 30, '포도당 하나에서 ATP 몇 개가 나오는가 — 셔틀에 따라 갈린다', { cls: 'ink bold' }));
    const f = frame({ xRange: [0, 2], yRange: [0, 36], box: { x: 84, y: 80, w: 250, h: 180 } });
    g.push(f.axes({ yLabel: 'ATP', xTicks: [], yTicks: [0, 10, 20, 30] }));
    const stacks = [
        [0.5, [[4, 's3', '기질 수준 인산화'], [25, 's1', '미토콘드리아 NADH 10개'], [3, 's2', 'FADH₂ 2개']], '말산-아스파르트산 셔틀', '32'],
        [1.5, [[4, 's3', ''], [20, 's1', 'NADH 8개'], [6, 's2', 'FADH₂ 4개']], '글리세롤-3-인산 셔틀', '30'],
    ];
    for (const [x, parts, name, total] of stacks) {
        let base = 0;
        const x1 = f.X(x - 0.3), x2 = f.X(x + 0.3);
        for (const [v, cls] of parts) {
            g.push(box(x1, f.Y(base + v), x2 - x1, f.Y(base) - f.Y(base + v), { fill: `var(--${cls})`, op: 0.3, stroke: `var(--${cls})`, sw: 1.3, rx: 2 }));
            g.push(txt((x1 + x2) / 2, (f.Y(base) + f.Y(base + v)) / 2 + 4, `${v}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            base += v;
        }
        g.push(txt((x1 + x2) / 2, f.Y(base) - 10, total, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt((x1 + x2) / 2, f.Y(0) + 20, name.split(' ')[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt((x1 + x2) / 2, f.Y(0) + 36, '셔틀', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(legend(376, 84, [{ slot: 3, name: '기질 수준 인산화 (4개)' }, { slot: 1, name: '미토콘드리아 NADH × 2.5' }, { slot: 2, name: 'FADH₂ × 1.5' }]));
    g.push(txt(376, 160, '두 막대의 차이는 딱 하나다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(376, 178, '세포질에서 만든 NADH 2개를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(376, 196, '미토콘드리아 안으로 어떻게', { cls: 'ink2', size: 'sm' }));
    g.push(txt(376, 214, '넘기느냐. NADH 로 넘기면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(376, 232, '2.5씩, FADH₂ 로 넘기면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(376, 250, '1.5씩 받는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 324, '이 값들은 정수가 아니고 조건에 따라 변한다. ‘포도당 하나당 약 30-32개’라고 적는 것이 정직하다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 346, '옛 교재의 36-38개는 NADH 당 3, FADH₂ 당 2 로 어림하던 시절의 값이다. 사실이 바뀐 것이 아니라 가정이 바뀌었다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-atp-budget',
        title: '포도당 하나의 ATP 수지',
        desc: '기질 수준 인산화 4개에 NADH 와 FADH₂ 의 몫을 더한다. 세포질 NADH 를 어느 셔틀로 넘기느냐에 따라 '
            + '합계가 32개와 30개로 갈린다. 단일한 정답은 없고 조건에 따라 변하는 값이다.',
        svg: svg({ width: W, height: H, title: 'ATP 수지', desc: '두 셔틀에 따른 포도당 1분자당 ATP 수량 비교', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 7장 — 광합성
 * ================================================================== */

/* 7-1. 엽록체의 구조 */
add((() => {
    const W = 690, H = 380;
    const g = [];
    g.push(txt(20, 30, '엽록체 — 막이 세 겹이고 방이 두 개다', { cls: 'ink bold' }));
    g.push(ell(250, 176, 210, 108, { fill: 'var(--s3)', op: 0.07, stroke: 'var(--s3)', sw: 2.2 }));
    g.push(ell(250, 176, 196, 96, { fill: 'none', stroke: 'var(--s3)', sw: 1.6 }));
    // 그라나 세 무더기
    for (const gx of [150, 250, 350]) {
        for (let k = 0; k < 5; k += 1) {
            g.push(ell(gx, 148 + k * 15, 34, 6, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1.2 }));
        }
    }
    g.push(line([[184, 158], [216, 200]], { stroke: 'var(--s1)', sw: 2.4 }));
    g.push(line([[284, 200], [316, 158]], { stroke: 'var(--s1)', sw: 2.4 }));
    g.push(txt(478, 92, '외막', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(482, 96, 424, 132, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(478, 118, '내막', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(482, 122, 414, 148, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(430, 316, '틸라코이드 — 이 막에 명반응 장치가 박혀 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(342, 308, 296, 224, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(66, 256, '스트로마 — 여기서 캘빈 회로가 돈다', { cls: 'ink2', size: 'sm' }));
    g.push(arw(150, 246, 190, 224, { cls: 'ark', marker: 'ark', width: 1.2 }));
    g.push(txt(72, 106, '그라나 (틸라코이드 무더기)', { cls: 'ink2', size: 'sm' }));
    g.push(arw(120, 112, 142, 140, { cls: 'ark', marker: 'ark', width: 1.2 }));

    g.push(panel(492, 132, 182, 128, '두 방'));
    g.push(txt(504, 174, '루멘 — 틸라코이드 안쪽', { cls: 'ink2', size: 'sm' }));
    g.push(txt(504, 192, '양성자가 쌓이는 곳', { cls: 'ink2', size: 'sm' }));
    g.push(txt(504, 220, '스트로마 — 틸라코이드 바깥', { cls: 'ink2', size: 'sm' }));
    g.push(txt(504, 238, 'ATP 와 NADPH 가 나오는 곳', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 344, '미토콘드리아와 견주면 이렇다. 미토콘드리아는 막간 공간에 양성자를 쌓고, 엽록체는 틸라코이드 루멘에 쌓는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 366, '둘 다 ‘막을 사이에 둔 양성자 차이로 ATP 를 만든다’는 같은 장치를 쓴다. 방의 이름만 다르다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-chloroplast',
        title: '엽록체의 구조',
        desc: '외막과 내막이 엽록체를 싸고, 그 안에 틸라코이드라는 세 번째 막이 납작한 주머니로 쌓여 그라나를 이룬다. '
            + '명반응은 틸라코이드 막에서, 캘빈 회로는 그 바깥의 스트로마에서 일어난다.',
        svg: svg({ width: W, height: H, title: '엽록체의 구조', desc: '외막·내막·틸라코이드와 스트로마·루멘의 배치', body: g.join('') }),
    };
})());

/* 7-2. 흡수 스펙트럼과 작용 스펙트럼 */
add((() => {
    const W = 690, H = 336;
    const g = [];
    g.push(txt(20, 30, '무엇을 흡수하는가와 무엇이 실제로 광합성을 돌리는가', { cls: 'ink bold' }));
    const f = frame({ xRange: [400, 700], yRange: [0, 110], box: { x: 78, y: 80, w: 400, h: 176 } });
    g.push(f.axes({ xLabel: '파장 (nm)', yLabel: '상대값 (%)', xTicks: [400, 450, 500, 550, 600, 650, 700], yTicks: [0, 50, 100] }));
    const G = (x, c, w, h) => h * Math.exp(-((x - c) ** 2) / (2 * w * w));
    const chlA = x => Math.min(105, G(x, 432, 20, 100) + G(x, 662, 15, 84) + 4);
    const chlB = x => Math.min(105, G(x, 455, 18, 66) + G(x, 642, 13, 52) + 3);
    const act = x => Math.min(105, G(x, 438, 30, 88) + G(x, 660, 20, 92) + G(x, 500, 40, 26) + 10);
    g.push(f.curve(act, { from: 400, to: 700, cls: 's3' }));
    g.push(f.curve(chlA, { from: 400, to: 700, cls: 's1' }));
    g.push(f.curve(chlB, { from: 400, to: 700, cls: 's2', dash: '5 4' }));
    g.push(legend(500, 84, [{ slot: 1, name: '엽록소 a 흡수' }, { slot: 2, name: '엽록소 b 흡수' }, { slot: 3, name: '작용 스펙트럼' }]));
    g.push(f.label([560, 62], '초록은 거의 흡수하지 않는다', { dx: 0, dy: 0, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(txt(500, 150, '두 곡선이 대체로 겹치는 것이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(500, 168, '엽록소가 주 색소라는 증거다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(500, 196, '500 nm 근처에서 작용 곡선만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(500, 214, '솟아 있다. 엽록소가 아닌 다른', { cls: 'ink2', size: 'sm' }));
    g.push(txt(500, 232, '색소가 빛을 넘겨주고 있다는 뜻.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 302, '흡수 스펙트럼은 ‘색소가 어느 파장을 삼키는가’이고, 작용 스펙트럼은 ‘어느 파장을 주었을 때 광합성이 잘 되는가’다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 324, '식물이 초록으로 보이는 이유는 초록을 반사하기 때문이고, 그것은 곧 초록을 가장 적게 쓰고 있다는 뜻이기도 하다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-absorption-spectrum',
        title: '흡수 스펙트럼과 작용 스펙트럼',
        desc: '엽록소 a 와 b 는 청색과 적색에서 흡수가 크고 초록은 거의 흡수하지 않는다. 작용 스펙트럼이 대체로 이를 '
            + '따라가지만 500 nm 근처에서 더 높은데, 그 차이가 보조 색소의 기여를 알려 준다.',
        svg: svg({ width: W, height: H, title: '흡수와 작용 스펙트럼', desc: '파장에 따른 엽록소 흡수와 광합성 효율', body: g.join('') }),
    };
})());

/* 7-3. Z-도식 */
add((() => {
    const W = 710, H = 366;
    const g = [];
    g.push(txt(20, 30, 'Z-도식 — 광자 두 번으로 전자를 물에서 NADP⁺ 까지 끌어올린다', { cls: 'ink bold' }));
    g.push(txt(20, 50, '세로축은 전자를 끌어당기는 세기(V)다. 위로 갈수록 약하게 당긴다 = 전자가 에너지를 더 많이 갖는다', { cls: 'ink2', size: 'sm' }));
    const f = frame({ xRange: [0, 10], yRange: [1.0, -1.6], box: { x: 92, y: 76, w: 396, h: 208 } });
    g.push(f.axes({ xTicks: [], yTicks: [1.0, 0.5, 0, -0.5, -1.0, -1.5] }));
    const pt = [
        [0.7, 0.82, '물'], [2.0, 0.9, 'P680'], [2.0, -0.8, 'P680*'],
        [3.3, 0.0, 'PQ'], [4.3, 0.2, 'b6f'], [5.3, 0.37, 'PC'],
        [6.4, 0.45, 'P700'], [6.4, -1.3, 'P700*'], [7.6, -0.9, 'Fd'], [8.9, -0.32, 'NADP⁺'],
    ];
    for (const [x, y, name] of pt) {
        g.push(f.line([[x - 0.34, y], [x + 0.34, y]], { cls: 's1' }));
        g.push(f.dot([x, y], { cls: 'f1' }));
    }
    const arrows = [[0, 1], [2, 3], [3, 4], [4, 5], [5, 6], [7, 8], [8, 9]];
    for (const [a, b] of arrows) g.push(f.vector([pt[a][0] + 0.34, pt[a][1]], [pt[b][0] - 0.34, pt[b][1]], { cls: 'ax', marker: 'ark', width: 1.5 }));
    g.push(f.vector([2.0, 0.9], [2.0, -0.8], { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(f.vector([6.4, 0.45], [6.4, -1.3], { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(f.label([2.0, 0.05], '광자 흡수', { dx: -60, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(f.label([6.4, -0.45], '광자 흡수', { dx: -60, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(f.label([0.7, 0.82], '물', { dx: -8, dy: 18, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([2.0, 0.9], 'P680 (광계 II)', { dx: 4, dy: 18, size: 'sm', cls: 'ink' }));
    g.push(f.label([2.0, -0.8], 'P680*', { dx: -4, dy: -10, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([3.3, 0.0], 'PQ', { dx: 0, dy: -10, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([4.3, 0.2], 'b6f', { dx: 0, dy: 18, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([5.3, 0.37], 'PC', { dx: 0, dy: 18, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([6.4, 0.45], 'P700 (광계 I)', { dx: 4, dy: 18, size: 'sm', cls: 'ink' }));
    g.push(f.label([6.4, -1.3], 'P700*', { dx: -4, dy: -10, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([7.6, -0.9], 'Fd', { dx: 0, dy: -10, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([8.9, -0.32], 'NADP⁺ → NADPH', { dx: -104, dy: -10, size: 'sm', cls: 'ink' }));
    g.push(txt(528, 106, '읽는 법', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(528, 126, '주황 화살표는 빛이 준 힘이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 144, '위로 올라간다 = 에너지를 얻는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 168, '회색 화살표는 저절로 내려가는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 186, '구간이다. 이때 양성자를 루멘에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 204, '퍼내는 일이 뽑힌다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 228, '광계가 둘인 이유가 여기 보인다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 246, '+0.82 에서 −0.32 까지 한 번에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(528, 264, '올리기에는 광자 하나가 모자란다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 330, '전체 모양이 알파벳 Z 를 옆으로 눕힌 것과 닮아 Z-도식이라 부른다. 올라갔다 내려오고, 다시 올라갔다 내려온다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 352, '물에서 뽑은 전자가 두 번의 광자 흡수로 NADP⁺ 까지 올라가는 것이 비순환적 흐름이고, 이때 산소가 나온다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-z-scheme',
        title: 'Z-도식',
        desc: '물에서 뽑은 전자가 광계 II 에서 광자를 받아 솟구쳤다가 내려오고, 광계 I 에서 다시 광자를 받아 솟구쳐 '
            + 'NADP⁺ 를 환원한다. 두 번 올리는 이유는 광자 하나로 +0.82 V 에서 −0.32 V 까지 올릴 수 없기 때문이다.',
        svg: svg({ width: W, height: H, title: 'Z-도식', desc: '광계 II 와 광계 I 을 거치는 전자의 전위 변화', body: g.join('') }),
    };
})());

/* 7-4. C3 와 C4 의 교차점 */
add((() => {
    const W = 690, H = 344;
    const g = [];
    g.push(txt(20, 30, '온도가 오르면 유리한 쪽이 뒤바뀐다', { cls: 'ink bold' }));
    const f = frame({ xRange: [10, 45], yRange: [0, 45], box: { x: 84, y: 82, w: 296, h: 178 } });
    g.push(f.axes({ yLabel: '순 광합성 속도 (상대값)', xTicks: [10, 20, 30, 40], yTicks: [0, 10, 20, 30, 40] }));
    g.push(txt(232, 302, '잎 온도 (°C)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const c3 = t => 26 * Math.exp(-((t - 23) ** 2) / 200);
    const c4 = t => 38 * Math.exp(-((t - 34) ** 2) / 220);
    g.push(f.curve(c3, { from: 10, to: 45, cls: 's1' }));
    g.push(f.curve(c4, { from: 10, to: 45, cls: 's2' }));
    g.push(f.dot([24.6, c3(24.6)], { cls: 'f3', r: 4.5 }));
    g.push(f.guide([24.6, 0], [24.6, c3(24.6)]));
    g.push(f.label([24.6, 26], '약 25 °C 에서 뒤집힌다', { dx: -58, dy: -16, size: 'sm', anchor: 'middle', cls: 'ink' }));
    g.push(f.label([17, c3(17)], 'C3', { dx: -26, dy: 0, size: 'sm', cls: 'ink' }));
    g.push(f.label([40, c4(40)], 'C4', { dx: 8, dy: 4, size: 'sm', cls: 'ink' }));
    g.push(txt(414, 84, 'C3 가 온도에 약한 이유', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(414, 104, '온도가 오르면 두 가지가 겹친다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 122, '① 물에 녹은 CO₂ 가 O₂ 보다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 140, '   빠르게 줄어든다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 158, '② 루비스코가 CO₂ 를 가려내는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 176, '   능력 자체가 떨어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 200, '→ 광호흡이 늘어 순 고정이 준다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 228, 'C4 는 CO₂ 를 미리 농축해서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(414, 246, '이 손실을 거의 없앤다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 322, 'C4 는 CO₂ 하나당 ATP 를 2개 더 쓴다. 서늘한 곳에서는 그 추가 비용이 손해이므로 C3 가 앞선다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 342, '어느 쪽이 ‘더 나은 광합성’이냐는 물음은 성립하지 않는다. 답은 온도와 물 사정에 따라 갈린다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-cell-c3-c4-temp',
        title: 'C3 와 C4 의 온도 교차점',
        desc: '서늘한 온도에서는 추가 비용이 없는 C3 가 앞서지만, 온도가 오르면 광호흡 손실이 커져 25 °C 부근에서 '
            + '순서가 뒤바뀐다. C4 는 CO₂ 를 농축해 손실을 없애는 대신 CO₂ 하나당 ATP 2개를 더 쓴다.',
        svg: svg({ width: W, height: H, title: 'C3 와 C4 의 온도 반응', desc: '잎 온도에 따른 두 광합성 유형의 순 광합성 속도', body: g.join('') }),
    };
})());

export default figures;
