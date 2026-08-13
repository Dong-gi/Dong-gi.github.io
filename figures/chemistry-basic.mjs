/**
 * 일반화학 3장(물질과 측정) ~ 8장(화학량론)의 그림.
 *
 * physics.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를 돌려주고
 * name 이 파일 이름(/figures/chemistry/<name>.svg)이 된다. 이름은 chem- 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 화학식은 유니코드 아래첨자(H₂O)와 위첨자(Na⁺)로 적는다. lib 의 esc 가 `v~0` 을
 * tspan 아래첨자로 바꾸므로, 라벨에 물결표를 쓸 때는 그 규칙을 따른다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

const SUP = {
    '-': '⁻', '+': '⁺', '=': '⁼', '(': '⁽', ')': '⁾', '.': '·',
    0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹',
};
/** '10⁻¹²' 처럼 쓰기 위한 위첨자 변환. */
const sup = s => String(s).split('').map(c => SUP[c] ?? c).join('');
const pow10 = e => `10${sup(e)}`;

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 타원(회전 가능). */
function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rot = 0, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}${rot ? ` transform="rotate(${r2(rot)} ${r2(cx)} ${r2(cy)})"` : ''}/>`;
}

/** 화소 좌표 꺾은선. */
function line(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 다각형(채움). */
function poly(pts, { fill = 'var(--s1)', op = 0.14, stroke = 'none', sw = 1, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 원자 하나. 원 + 가운데 기호. */
function atom(cx, cy, r, label, { fill = 'var(--s1)', op = 0.2, stroke = 'var(--s1)', sw = 1.5, size } = {}) {
    return circ(cx, cy, r, { fill, op, stroke, sw })
        + txt(cx, cy + (size === 'sm' ? 4 : 5), label, { anchor: 'middle', cls: 'ink', size });
}

/** 두 점을 잇는 결합선. n=1,2,3 이면 단일·이중·삼중. 원자 반지름만큼 양끝을 비운다. */
function bond(p1, p2, n = 1, { r1 = 0, r2r = 0, stroke = 'var(--ink2)', sw = 1.8, gap = 4 } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    const a = [p1[0] + ux * r1, p1[1] + uy * r1];
    const b = [p2[0] - ux * r2r, p2[1] - uy * r2r];
    const nx = -uy, ny = ux;
    const out = [];
    const offs = n === 1 ? [0] : n === 2 ? [-gap / 2, gap / 2] : [-gap, 0, gap];
    for (const o of offs) {
        out.push(line([[a[0] + nx * o, a[1] + ny * o], [b[0] + nx * o, b[1] + ny * o]], { stroke, sw, cap: 'butt' }));
    }
    return out.join('');
}

/** 비공유 전자쌍 두 점. 원자 중심에서 각 ang(도) 방향, 거리 r 자리에 찍는다. */
function lonePair(cx, cy, r, ang, { fill = 'var(--ink)', dot = 2.4, spread = 6 } = {}) {
    const a = (ang * Math.PI) / 180;
    const px0 = cx + r * Math.cos(a), py0 = cy - r * Math.sin(a);
    const nx = -Math.sin(a), ny = -Math.cos(a);
    return circ(px0 + nx * spread, py0 + ny * spread, dot, { fill, stroke: 'none', sw: 0 })
        + circ(px0 - nx * spread, py0 - ny * spread, dot, { fill, stroke: 'none', sw: 0 });
}

/** 한 오비탈 로브(물방울 모양). 원점에서 각 ang 방향으로 길이 L, 최대 폭 w. */
function lobe(cx, cy, L, w, ang, { fill = 'var(--s1)', op = 0.35, stroke = 'var(--s1)', sw = 1.2 } = {}) {
    const d = `M0 0 C ${L * 0.22} ${-w} ${L * 0.86} ${-w * 0.9} ${L} 0 C ${L * 0.86} ${w * 0.9} ${L * 0.22} ${w} 0 0 Z`;
    return `<path transform="translate(${r2(cx)} ${r2(cy)}) rotate(${r2(-ang)})" d="${d}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 작은 좌표축 십자(오비탈 그림용). */
function axesCross(cx, cy, r, { labels = ['x', 'z'], depth = false } = {}) {
    const out = [
        line([[cx - r, cy], [cx + r, cy]], { stroke: 'var(--grid)', sw: 1 }),
        line([[cx, cy - r], [cx, cy + r]], { stroke: 'var(--grid)', sw: 1 }),
        txt(cx + r + 4, cy + 4, labels[0], { cls: 'ink2', size: 'sm' }),
        txt(cx + 4, cy - r - 3, labels[1], { cls: 'ink2', size: 'sm' }),
    ];
    if (depth) {
        out.push(line([[cx - r * 0.62, cy + r * 0.62], [cx + r * 0.62, cy - r * 0.62]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        out.push(txt(cx + r * 0.62 + 4, cy - r * 0.62, labels[2] ?? 'y', { cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 패널 제목 + 테두리. 여러 그림을 한 장에 나열할 때 쓴다. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 곡선을 [0,1] 로 정규화하기 위한 최댓값. */
function peak(f, from, to, steps = 800) {
    let m = 0;
    for (let i = 0; i <= steps; i += 1) m = Math.max(m, f(from + ((to - from) * i) / steps));
    return m || 1;
}

/* ================================================================== *
 * 3장 — 물질과 측정
 * ================================================================== */

/* 3-1. 10의 거듭제곱 자 — 원자에서 지구까지 */
add((() => {
    const W = 660, H = 320;
    const x0 = 70, x1 = 600, yb = 240;
    const E0 = -12, E1 = 7;
    const X = e => x0 + ((e - E0) / (E1 - E0)) * (x1 - x0);
    const g = [line([[x0 - 10, yb], [x1 + 14, yb]], { stroke: 'var(--ink2)', sw: 1.5 })];
    for (let e = E0; e <= E1; e += 1) {
        const big = (e - E0) % 3 === 0;
        g.push(line([[X(e), yb], [X(e), yb + (big ? 11 : 6)]], { stroke: 'var(--ink2)', sw: 1 }));
        if (big) g.push(txt(X(e), yb + 28, `${pow10(e)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x1 + 20, yb + 5, 'm', { cls: 'ink2', size: 'sm' }));
    const items = [
        [-10, '원자 하나 (약 0.1 nm)', 0],
        [-9.5, '물 분자', 1],
        [-8.7, 'DNA 두 가닥 굵기 2 nm', 2],
        [-5, '사람 세포 10 μm', 0],
        [-4, '머리카락 굵기', 1],
        [0.23, '사람 키 1.7 m', 0],
        [6.8, '지구 반지름 6400 km', 1],
    ];
    for (const [e, name, lv] of items) {
        const yy = 66 + lv * 30;
        g.push(line([[X(e), yb - 4], [X(e), yy + 6]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(circ(X(e), yb - 4, 3.2, { fill: 'var(--s1)', stroke: 'none', sw: 0 }));
        g.push(txt(X(e), yy, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(txt(x0 - 10, 34, '눈금 한 칸이 10배다. 한 칸 옮길 때마다 0이 하나 붙는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 12, '화학이 다루는 크기(왼쪽 끝)와 우리가 재는 크기(오른쪽) 사이가 이만큼 벌어져 있다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-powers-of-ten',
        title: '10의 거듭제곱으로 본 크기의 사다리',
        desc: '원자 하나의 지름 10⁻¹⁰ m 에서 지구 반지름 10⁷ m 까지를 한 칸이 10배인 자 위에 늘어놓았다. '
            + '한 칸 옮길 때마다 값이 10배가 되므로, 0을 세는 대신 지수 하나를 고치면 된다.',
        svg: svg({ width: W, height: H, title: '10의 거듭제곱 자', desc: '원자에서 지구까지의 크기를 지수로 늘어놓은 그림', body: g.join('') }),
    };
})());

/* 3-2. 배수비례의 법칙 */
add((() => {
    const W = 620, H = 300;
    const S = 7.6;                        // 1 g 당 화소
    const xs = 150;
    const rowY = [86, 190];
    const g = [];
    const bar = (x, y, gm, cls, label) => box(x, y, gm * S, 40, { fill: `var(--${cls})`, op: 0.35, stroke: `var(--${cls})`, sw: 1.4 })
        + txt(x + (gm * S) / 2, y + 25, label, { anchor: 'middle', cls: 'ink', size: 'sm' });
    g.push(txt(20, rowY[0] - 16, '일산화탄소 CO', { cls: 'ink bold' }));
    g.push(bar(xs, rowY[0], 12, 's1', '탄소 12 g'));
    g.push(bar(xs + 12 * S, rowY[0], 16, 's2', '산소 16 g'));
    g.push(txt(20, rowY[1] - 16, '이산화탄소 CO₂', { cls: 'ink bold' }));
    g.push(bar(xs, rowY[1], 12, 's1', '탄소 12 g'));
    g.push(bar(xs + 12 * S, rowY[1], 32, 's2', '산소 32 g'));
    // 산소 부분만 견주는 보조선
    g.push(line([[xs + 12 * S, rowY[0] - 8], [xs + 12 * S, rowY[1] + 52]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(line([[xs + 28 * S, rowY[0] - 8], [xs + 28 * S, rowY[1] + 52]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(px(xs + 12 * S, 64, xs + 28 * S, 64, { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(px(xs + 28 * S, 64, xs + 12 * S, 64, { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(txt(xs + 20 * S, 56, '1몫', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(px(xs + 12 * S, 262, xs + 44 * S, 262, { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(px(xs + 44 * S, 262, xs + 12 * S, 262, { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(txt(xs + 28 * S, 254, '2몫', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 34, '같은 탄소 12 g 에 결합한 산소의 질량', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 12, '16 g : 32 g = 1 : 2 — 간단한 정수비가 나온다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-multiple-proportions',
        title: '배수비례의 법칙',
        desc: '같은 질량의 탄소 12 g 에 결합하는 산소가 일산화탄소에서는 16 g, 이산화탄소에서는 32 g 이다. '
            + '두 값의 비가 1 대 2 라는 간단한 정수비가 되는 것이 배수비례의 법칙이고, 물질이 낱개로 결합한다는 증거다.',
        svg: svg({ width: W, height: H, title: '배수비례의 법칙', desc: '탄소 12 g 에 붙는 산소가 16 g 과 32 g 으로 1대2', body: g.join('') }),
    };
})());

/* 3-3. 눈금을 읽는 법 — 유효숫자 */
add((() => {
    const W = 600, H = 340;
    const cx = 150, top = 40, bot = 300, wCyl = 96;
    const vTop = 30, vBot = 20;                 // 위쪽 눈금 30 mL, 아래 20 mL
    const Y = v => bot - ((v - vBot) / (vTop - vBot)) * (bot - top);
    const g = [];
    const read = 24.6;
    // 액체
    g.push(box(cx - wCyl / 2, Y(read), wCyl, bot - Y(read), { fill: 'var(--s1)', op: 0.18, stroke: 'none', sw: 0, rx: 0 }));
    // 관
    g.push(box(cx - wCyl / 2, top, wCyl, bot - top, { stroke: 'var(--ink2)', sw: 1.6, rx: 4 }));
    for (let v = vBot; v <= vTop; v += 1) {
        const big = v % 5 === 0;
        g.push(line([[cx - wCyl / 2, Y(v)], [cx - wCyl / 2 + (big ? 26 : 15), Y(v)]], { stroke: 'var(--ink2)', sw: 1 }));
        if (big) g.push(txt(cx - wCyl / 2 - 8, Y(v) + 4, String(v), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    // 메니스커스
    g.push(`<path d="M${r2(cx - wCyl / 2)} ${r2(Y(read) - 7)} Q ${r2(cx)} ${r2(Y(read) + 9)} ${r2(cx + wCyl / 2)} ${r2(Y(read) - 7)}" fill="none" stroke="var(--s1)" stroke-width="2"/>`);
    g.push(px(cx + wCyl / 2 + 100, Y(read), cx + wCyl / 2 + 8, Y(read), { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(cx + wCyl / 2 + 108, Y(read) + 5, '오목한 바닥을 읽는다', { cls: 'ink' }));
    g.push(txt(cx + wCyl / 2 + 108, Y(read) + 30, '눈금은 1 mL 간격 → 24 와 25 사이인 것은 확실', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + wCyl / 2 + 108, Y(read) + 50, '그 아래 한 자리는 눈대중 → 24.6 의 6', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + wCyl / 2 + 108, Y(read) + 78, '24.6 mL — 유효숫자 3자리', { cls: 'ink bold' }));
    g.push(txt(20, 24, '눈금실린더로 부피 재기', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 12, '확실한 자리 + 어림한 한 자리 = 유효숫자', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-measure-reading',
        title: '눈금실린더를 읽는 법과 유효숫자',
        desc: '1 mL 간격 눈금이 있는 실린더에서는 24 와 25 사이라는 것까지가 눈금이 알려주는 확실한 정보이고, '
            + '그 아래 한 자리는 눈대중으로 어림한다. 확실한 자리에 어림한 한 자리를 더한 것이 유효숫자다.',
        svg: svg({ width: W, height: H, title: '눈금 읽기와 유효숫자', desc: '눈금실린더에서 24.6 mL 를 읽는 그림', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 4장 — 원자의 구조
 * ================================================================== */

/* 4-1. 러더퍼드 산란 */
add((() => {
    const W = 640, H = 400;
    const fx = 400;
    const g = [];
    // 금박 원자 세 개
    const ys = [80, 175, 270];
    for (const y of ys) {
        g.push(circ(fx, y, 46, { fill: 'var(--s1)', op: 0.08, stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        g.push(circ(fx, y, 4.5, { fill: 'var(--s2)', stroke: 'none', sw: 0 }));
    }
    g.push(txt(fx, 336, '금 원자 (점선 = 전자가 퍼져 있는 범위, 가운데 점 = 핵)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 368, '거의 다 그대로 통과 → 원자 속은 대부분 비어 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 388, '아주 드물게 크게 되튄다 → 가운데에 작고 무겁고 양전하인 덩어리가 있다', { cls: 'ink2', size: 'sm' }));
    // 통과하는 알파 입자
    for (const y of [50, 128, 222, 300]) {
        g.push(px(70, y, 600, y, { cls: 's3', marker: 'ar3', width: 1.8 }));
    }
    // 살짝 휘는 것
    g.push(`<path d="M70 175 L${fx - 60} 175 Q ${fx} 172 600 138" fill="none" stroke="var(--s1)" stroke-width="2" marker-end="url(#ar1)"/>`);
    // 크게 되튀는 것
    g.push(`<path d="M70 80 L${fx - 14} 80 Q ${fx + 6} 78 ${fx - 120} 26" fill="none" stroke="var(--s2)" stroke-width="2.4" marker-end="url(#ar2)"/>`);
    g.push(txt(70, 26, '알파 입자를 쏜다', { cls: 'ink' }));
    g.push(txt(292, 30, '크게 되튄 것 (아주 드물다)', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-rutherford-scattering',
        title: '러더퍼드의 산란 실험',
        desc: '금박에 알파 입자를 쏘면 거의 전부가 그대로 지나간다. 원자 속이 대부분 비어 있다는 뜻이다. '
            + '그런데 아주 드물게 크게 튕겨 나오는 것이 있다. 작고 무겁고 양전하를 띤 핵이 가운데 있어야 설명된다.',
        svg: svg({ width: W, height: H, title: '러더퍼드 산란', desc: '알파 입자 대부분은 통과하고 드물게 크게 되튄다', body: g.join('') }),
    };
})());

/* 4-2. 동위원소와 평균 원자량 */
add((() => {
    const W = 580, H = 320;
    const g0 = frame({ xRange: [34.2, 37.8], yRange: [0, 100], box: { x: 70, y: 40, w: 420, h: 210 } });
    const bar = (x, h, cls, label, sub) => {
        const bw = 46;
        return box(g0.X(x) - bw / 2, g0.Y(h), bw, g0.Y(0) - g0.Y(h), { fill: `var(--${cls})`, op: 0.35, stroke: `var(--${cls})`, sw: 1.5, rx: 2 })
            + txt(g0.X(x), g0.Y(h) - 8, label, { anchor: 'middle', cls: 'ink bold' })
            + txt(g0.X(x), g0.Y(0) + 34, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    };
    const g = [
        g0.axes({ xLabel: '원자 질량 (Da)', yLabel: '존재비 (%)', xTicks: [35, 36, 37], yTicks: [0, 25, 50, 75, 100] }),
        bar(34.969, 75.76, 's1', '75.76%', '³⁵Cl  34.969 Da'),
        bar(36.966, 24.24, 's2', '24.24%', '³⁷Cl  36.966 Da'),
        line([[g0.X(35.45), g0.Y(0)], [g0.X(35.45), g0.Y(100) - 6]], { stroke: 'var(--s3)', sw: 2, dash: '6 4' }),
        txt(g0.X(35.45) + 6, g0.Y(100) + 6, '평균 35.45 Da', { cls: 'ink bold' }),
        txt(g0.X(35.45) + 6, g0.Y(100) + 24, '무거운 쪽이 적으므로 평균은 35 쪽으로 치우친다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '평균 = 0.7576 × 34.969 + 0.2424 × 36.966 = 35.45', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'chem-isotope-average',
        title: '염소의 동위원소와 평균 원자량',
        desc: '염소에는 질량 34.969 Da 인 것과 36.966 Da 인 것이 각각 75.76%, 24.24% 섞여 있다. '
            + '주기율표에 적힌 35.45 는 이 둘을 존재비로 가중평균한 값이고, 실제로 그런 질량을 가진 원자는 없다.',
        svg: svg({ width: W, height: H, title: '염소 동위원소의 존재비와 평균', desc: '35Cl 과 37Cl 의 막대와 가중평균 위치', body: g.join('') }),
    };
})());

/* 4-3. 수소의 선스펙트럼 */
add((() => {
    const W = 640, H = 320;
    const x0 = 70, x1 = 590, yTop = 74, hBand = 74;
    const L0 = 380, L1 = 720;
    const X = l => x0 + ((l - L0) / (L1 - L0)) * (x1 - x0);
    const grad = '<defs><linearGradient id="vis" x1="0" y1="0" x2="1" y2="0">'
        + '<stop offset="0" stop-color="#6a00b8"/><stop offset="0.16" stop-color="#2a2ad6"/>'
        + '<stop offset="0.34" stop-color="#00b7c2"/><stop offset="0.50" stop-color="#2fbf2f"/>'
        + '<stop offset="0.66" stop-color="#e8e800"/><stop offset="0.82" stop-color="#ff8c00"/>'
        + '<stop offset="1" stop-color="#d00000"/></linearGradient></defs>';
    const g = [grad];
    g.push(`<rect x="${x0}" y="${yTop}" width="${x1 - x0}" height="${hBand}" rx="3" fill="url(#vis)" fill-opacity="0.55"/>`);
    g.push(box(x0, yTop, x1 - x0, hBand, { stroke: 'var(--ink2)', sw: 1.2, rx: 3 }));
    const lines = [
        [656.3, '656 nm', '3 → 2', '빨강', 0],
        [486.1, '486 nm', '4 → 2', '청록', 0],
        [434.0, '434 nm', '5 → 2', '파랑', 0],
        [410.2, '410 nm', '6 → 2', '보라', 1],
    ];
    for (const [l, nm, tr, col, row] of lines) {
        const y1 = yTop + hBand + (row ? 60 : 18);
        g.push(line([[X(l), yTop], [X(l), yTop + hBand]], { stroke: 'var(--ink)', sw: 3, cap: 'butt' }));
        g.push(line([[X(l), yTop + hBand], [X(l), y1 - 12]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(txt(X(l), y1, nm, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(X(l), y1 + 17, tr, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(X(l), yTop - (row ? 26 : 10), col, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const l of [400, 450, 500, 550, 600, 650, 700]) {
        g.push(txt(X(l), yTop + hBand + 118, String(l), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(line([[x0, yTop + hBand + 100], [x1, yTop + hBand + 100]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(x1 + 12, yTop + hBand + 118, 'nm', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 30, '수소 기체에 전기를 흘리면 나오는 빛 — 띄엄띄엄한 네 줄뿐이다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '연속된 무지개가 아니라 정해진 자리에만 선이 선다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-hydrogen-spectrum',
        title: '수소 원자의 가시광선 방출 스펙트럼',
        desc: '수소가 내는 빛은 연속된 무지개가 아니라 656, 486, 434, 410 nm 네 자리에만 선으로 나타난다. '
            + '원자가 내놓을 수 있는 에너지가 띄엄띄엄하다는 직접적인 증거다.',
        svg: svg({ width: W, height: H, title: '수소의 선스펙트럼', desc: '가시광선 영역의 발머 계열 네 선', body: g.join('') }),
    };
})());

/* 4-4. 보어 모형의 에너지 준위 */
add((() => {
    const W = 720, H = 470;
    const xa = 210, xb = 460;
    const E = n => -13.606 / (n * n);
    const yTop = 70, yBot = 390;
    const Y = e => yBot - ((e - (-14)) / (0 - (-14))) * (yBot - yTop);
    const g = [];
    g.push(px(44, Y(-14), 44, Y(0) - 12, { cls: 's1', marker: 'ar1', width: 1.6 }));
    g.push(txt(26, Y(0) - 20, '에너지 (eV)', { cls: 'ink2', size: 'sm' }));
    for (let n = 1; n <= 6; n += 1) {
        const y = Y(E(n));
        g.push(line([[xa, y], [xb, y]], { stroke: 'var(--ink2)', sw: 1.6, cap: 'butt' }));
    }
    // n ≥ 3 은 서로 너무 가까워 라벨을 옆으로 빼고 안내선을 긋는다.
    for (const [n, ly] of [[6, 74], [5, 98], [4, 122], [3, 146]]) {
        g.push(line([[xb, Y(E(n))], [xb + 44, ly - 4]], { stroke: 'var(--grid)', sw: 1 }));
        g.push(txt(xb + 50, ly, `n = ${n}    ${E(n).toFixed(2)} eV`, { cls: 'ink', size: 'sm' }));
    }
    for (const n of [1, 2]) {
        g.push(txt(xa - 10, Y(E(n)) + 4, `n = ${n}    ${E(n).toFixed(2)} eV`, { anchor: 'end', cls: 'ink', size: 'sm' }));
    }
    g.push(line([[xa, Y(0)], [xb, Y(0)]], { stroke: 'var(--grid)', sw: 1.4, dash: '5 4', cap: 'butt' }));
    g.push(txt(xb + 50, 46, 'n = ∞ — 전자가 떨어져 나간 상태', { cls: 'ink2', size: 'sm' }));
    g.push(txt(xa - 10, Y(0) + 4, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 발머 전이 — n = 2 로 떨어지는 네 가지
    const trans = [[3, 258, '656 nm', 0], [4, 306, '486 nm', 1], [5, 354, '434 nm', 0], [6, 402, '410 nm', 1]];
    for (const [ni, x, wl, row] of trans) {
        g.push(px(x, Y(E(ni)), x, Y(E(2)), { cls: 's2', marker: 'ar2', width: 2 }));
        g.push(txt(x, Y(E(2)) + 20 + row * 17, wl, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(px(232, Y(E(2)), 232, Y(E(1)), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(240, (Y(E(2)) + Y(E(1))) / 2, '2 → 1 (자외선)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(330, Y(E(2)) + 62, 'n = 2 로 떨어지는 네 전이가 눈에 보이는 네 선이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 30, '수소 원자가 가질 수 있는 에너지는 이 값들뿐이다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 34, '위 칸에서 아래 칸으로 떨어질 때 그 차이만큼을 빛 알갱이 하나로 내놓는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 14, '칸 사이가 위로 갈수록 촘촘해진다는 점도 눈여겨볼 것', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-bohr-levels',
        title: '수소 원자의 에너지 준위와 전이',
        desc: '수소 원자의 에너지는 -13.6 eV 를 n 제곱으로 나눈 값들만 가질 수 있다. n=3,4,5,6 에서 n=2 로 '
            + '떨어질 때 나오는 빛이 눈에 보이는 네 선이고, n=2 에서 n=1 로 떨어지는 빛은 자외선이라 보이지 않는다.',
        svg: svg({ width: W, height: H, title: '수소의 에너지 준위', desc: 'n=1~6 준위와 발머 전이 화살표', body: g.join('') }),
    };
})());

/* 4-5. s 와 p 오비탈의 모양 */
add((() => {
    const W = 660, H = 440;
    const pw = 210, ph = 200;
    const cols = [15, 228, 441], rows = [20, 226];
    const grad = '<defs><radialGradient id="sfade"><stop offset="0" stop-color="var(--s1)" stop-opacity="0.75"/>'
        + '<stop offset="0.55" stop-color="var(--s1)" stop-opacity="0.30"/>'
        + '<stop offset="1" stop-color="var(--s1)" stop-opacity="0.02"/></radialGradient>'
        + '<radialGradient id="sring"><stop offset="0" stop-color="var(--s1)" stop-opacity="0.70"/>'
        + '<stop offset="0.32" stop-color="var(--s1)" stop-opacity="0.05"/>'
        + '<stop offset="0.46" stop-color="var(--s2)" stop-opacity="0.05"/>'
        + '<stop offset="0.70" stop-color="var(--s2)" stop-opacity="0.34"/>'
        + '<stop offset="1" stop-color="var(--s2)" stop-opacity="0.02"/></radialGradient></defs>';
    const g = [grad];
    const P = (i, j) => [cols[i], rows[j]];
    const mid = (i, j) => [cols[i] + pw / 2, rows[j] + ph / 2 + 14];

    // 1s
    let [x, y] = P(0, 0); let [mx, my] = mid(0, 0);
    g.push(panel(x, y, pw, ph, '1s', { sub: '공 모양. 방향이 없다' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'z'] }));
    g.push(`<circle cx="${mx}" cy="${my}" r="62" fill="url(#sfade)"/>`);
    g.push(circ(mx, my, 62, { stroke: 'var(--s1)', sw: 1, dash: '4 3' }));

    // 2s
    [x, y] = P(1, 0); [mx, my] = mid(1, 0);
    g.push(panel(x, y, pw, ph, '2s', { sub: '가운데를 자르면 마디가 하나' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'z'] }));
    g.push(`<circle cx="${mx}" cy="${my}" r="70" fill="url(#sring)"/>`);
    g.push(circ(mx, my, 70, { stroke: 'var(--s2)', sw: 1, dash: '4 3' }));
    g.push(circ(mx, my, 27, { stroke: 'var(--ink2)', sw: 1, dash: '3 3' }));
    g.push(txt(mx + 32, my - 30, '마디', { cls: 'ink2', size: 'sm' }));

    // 2p_z
    [x, y] = P(2, 0); [mx, my] = mid(2, 0);
    g.push(panel(x, y, pw, ph, '2p(z)', { sub: 'z 축을 따라 아령 모양' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'z'] }));
    g.push(lobe(mx, my, 66, 46, 90, { fill: 'var(--s1)', stroke: 'var(--s1)' }));
    g.push(lobe(mx, my, 66, 46, -90, { fill: 'var(--s2)', stroke: 'var(--s2)' }));
    g.push(txt(mx + 14, my - 46, '+', { cls: 'ink', size: 'sm' }));
    g.push(txt(mx + 14, my + 54, '−', { cls: 'ink', size: 'sm' }));

    // 2p_x
    [x, y] = P(0, 1); [mx, my] = mid(0, 1);
    g.push(panel(x, y, pw, ph, '2p(x)', { sub: '같은 모양을 x 축으로 돌린 것' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'z'] }));
    g.push(lobe(mx, my, 66, 46, 0, { fill: 'var(--s1)', stroke: 'var(--s1)' }));
    g.push(lobe(mx, my, 66, 46, 180, { fill: 'var(--s2)', stroke: 'var(--s2)' }));

    // 2p_y
    [x, y] = P(1, 1); [mx, my] = mid(1, 1);
    g.push(panel(x, y, pw, ph, '2p(y)', { sub: 'y 축은 화면 안쪽 방향' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'z', 'y'], depth: true }));
    g.push(lobe(mx, my, 62, 40, 45, { fill: 'var(--s1)', stroke: 'var(--s1)' }));
    g.push(lobe(mx, my, 62, 40, 225, { fill: 'var(--s2)', stroke: 'var(--s2)' }));

    // 3d_xy
    [x, y] = P(2, 1); [mx, my] = mid(2, 1);
    g.push(panel(x, y, pw, ph, '3d(xy)', { sub: 'd 는 잎이 넷 (5장에서 쓴다)' }));
    g.push(axesCross(mx, my, 74, { labels: ['x', 'y'] }));
    for (const [a, c] of [[45, 's1'], [135, 's2'], [225, 's1'], [315, 's2']]) {
        g.push(lobe(mx, my, 58, 34, a, { fill: `var(--${c})`, stroke: `var(--${c})` }));
    }
    g.push(txt(W - 14, H - 8, '색은 파동의 부호(위상)다. 진하기가 전자를 발견할 확률에 해당한다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-orbital-shapes',
        title: 's · p · d 오비탈의 모양',
        desc: 's 오비탈은 방향이 없는 공 모양이고 2s 에는 안쪽에 확률이 0인 마디가 하나 있다. '
            + 'p 오비탈은 아령 모양이며 x, y, z 세 방향으로 하나씩 있다. d 오비탈은 잎이 넷이다. '
            + '두 색은 파동의 부호(위상)를 나타내고, 결합을 다룰 때 이 부호가 중요해진다.',
        svg: svg({ width: W, height: H, title: 's, p, d 오비탈의 모양', desc: '1s, 2s, 2p 세 방향, 3d 하나를 나란히 그린 그림', body: g.join('') }),
    };
})());

/* 4-6. s 오비탈의 지름 확률 분포 */
add((() => {
    const W = 600, H = 330;
    const f1 = r => 4 * r * r * Math.exp(-2 * r);
    const f2 = r => (1 / 8) * r * r * (2 - r) ** 2 * Math.exp(-r);
    const f3 = r => (r * r / 2187) * (27 - 18 * r + 2 * r * r) ** 2 * Math.exp(-2 * r / 3);
    const n1 = peak(f1, 0, 22), n2 = peak(f2, 0, 22), n3 = peak(f3, 0, 22);
    const g0 = frame({ xRange: [0, 22], yRange: [0, 1.12], box: { x: 62, y: 46, w: 460, h: 196 } });
    const g = [
        g0.axes({ xLabel: '', yLabel: '확률', xTicks: [0, 5, 10, 15, 20], yTicks: [] }),
        g0.curve(r => f1(r) / n1, { cls: 's1' }),
        g0.curve(r => f2(r) / n2, { cls: 's2' }),
        g0.curve(r => f3(r) / n3, { cls: 's3' }),
        g0.label([1, f1(1) / n1], '1s', { dx: 6, dy: -8, cls: 'ink' }),
        g0.label([5.24, f2(5.24) / n2], '2s', { dx: 6, dy: -8, cls: 'ink' }),
        g0.label([13, f3(13) / n3], '3s', { dx: 6, dy: -8, cls: 'ink' }),
        `<path d="M${g0.X(2)} ${g0.Y(0) - 18} l-5 -9 h10 z" fill="var(--s2)"/>`,
        `<path d="M${g0.X(7.1)} ${g0.Y(0) - 3} l-5 -9 h10 z" fill="var(--s3)"/>`,
        `<path d="M${g0.X(1.9)} ${g0.Y(0) - 3} l-5 -9 h10 z" fill="var(--s3)"/>`,
        txt(300, 30, '핵에서 거리 r 인 껍질에서 전자를 발견할 확률', { anchor: 'middle', cls: 'ink bold' }),
        txt(300, H - 52, '▲ 표는 마디 — 확률이 정확히 0이 되는 자리 (2s 는 하나, 3s 는 둘)',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(300, H - 34, '가로축은 핵에서의 거리 (보어 반지름 단위). 곡선마다 최댓값을 1로 맞췄다',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, '2s·3s 는 바깥에 크게 퍼지면서도 안쪽에 작은 봉우리를 남긴다 — 이것이 침투다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'chem-orbital-radial',
        title: '1s · 2s · 3s 오비탈에서 전자가 있을 확률',
        desc: '핵에서 거리 r 인 자리에서 전자를 발견할 확률을 그린 것이다. n 이 커질수록 봉우리가 바깥으로 밀리지만, '
            + '2s 와 3s 는 안쪽에도 작은 봉우리를 남긴다. 이 안쪽 봉우리(침투) 때문에 같은 껍질에서도 s 가 p 보다 에너지가 낮다. '
            + '확률이 0이 되는 자리가 마디다.',
        svg: svg({ width: W, height: H, title: 's 오비탈의 지름 확률 분포', desc: '1s, 2s, 3s 의 확률 곡선과 마디', body: g.join('') }),
    };
})());

/* 4-7. 오비탈 에너지 순서와 쌓음 원리 */
add((() => {
    const W = 640, H = 450;
    const bw = 26, bh = 18, gap = 4;
    // [라벨, 상대 에너지(그리는 높이), 칸 수, x 위치]
    const set = [
        ['1s', 0.0, 1, 120, 1], ['2s', 1.5, 1, 120, 2], ['2p', 2.1, 3, 250, 3],
        ['3s', 3.3, 1, 120, 4], ['3p', 3.9, 3, 250, 5], ['4s', 4.7, 1, 120, 6],
        ['3d', 5.1, 5, 400, 7], ['4p', 5.7, 3, 250, 8], ['5s', 6.4, 1, 120, 9],
        ['4d', 6.9, 5, 400, 10], ['5p', 7.5, 3, 250, 11],
    ];
    const yBot = 356, yTop = 60;
    const Y = e => yBot - (e / 7.9) * (yBot - yTop);
    const g = [px(70, yBot + 6, 70, yTop - 14, { cls: 's1', marker: 'ar1', width: 1.6 })];
    g.push(txt(58, yTop - 20, '에너지', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    for (const [name, e, k, x, order] of set) {
        const y = Y(e);
        for (let i = 0; i < k; i += 1) {
            g.push(box(x + i * (bw + gap), y - bh / 2, bw, bh, { stroke: 'var(--ink2)', sw: 1.3, rx: 2 }));
        }
        g.push(txt(x - 10, y + 5, name, { anchor: 'end', cls: 'ink' }));
        g.push(txt(x + k * (bw + gap) + 8, y + 5, `${order}번째`, { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(320, 34, '다전자 원자의 오비탈 에너지', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(20, H - 52, '칸 하나에 전자 두 개까지 (스핀이 반대여야 한다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 32, '낮은 칸부터 채운다. 같은 값의 칸이 여럿이면 하나씩 먼저 흩어 넣는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '4s 가 3d 보다 낮다 — 그래서 칼륨·칼슘에서 3d 를 건너뛰고 4s 가 먼저 찬다', { cls: 'ink bold' }));
    return {
        name: 'chem-orbital-energy-order',
        title: '다전자 원자의 오비탈 에너지와 채우는 순서',
        desc: '전자가 둘 이상인 원자에서는 같은 껍질이라도 s 가 p 보다, p 가 d 보다 낮다. '
            + '그 결과 4s 가 3d 보다 낮아져 칼륨과 칼슘에서 4s 가 먼저 채워진다. '
            + '칸 하나에 전자 두 개까지 들어가고, 에너지가 같은 칸이 여럿이면 하나씩 흩어 넣은 뒤에 짝을 짓는다.',
        svg: svg({ width: W, height: H, title: '오비탈 에너지 순서', desc: '1s부터 5p까지의 에너지 순서와 채우는 차례', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 5장 — 주기율표와 주기적 성질
 * ================================================================== */

/* 5-1. 주기율표의 블록 구조 */
add((() => {
    const W = 700, H = 420;
    const cw = 30, ch = 26, ox = 90, oy = 78;
    const CX = c => ox + (c - 1) * cw;              // 1~18족
    const CY = p => oy + (p - 1) * ch;              // 1~7주기
    const g = [];
    const cell = (c, p, cls, label) => box(CX(c), CY(p), cw - 2, ch - 2,
        { fill: `var(--${cls})`, op: 0.28, stroke: `var(--${cls})`, sw: 1 })
        + (label ? txt(CX(c) + cw / 2 - 1, CY(p) + ch / 2 + 3, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
    // s 블록: 1·2족 (He 는 1s 이지만 자리는 18족)
    for (let p = 1; p <= 7; p += 1) {
        g.push(cell(1, p, 's1'));
        if (p >= 2) g.push(cell(2, p, 's1'));
    }
    // p 블록: 13~18족
    for (let p = 2; p <= 7; p += 1) for (let c = 13; c <= 18; c += 1) g.push(cell(c, p, 's2'));
    g.push(cell(18, 1, 's2'));
    // d 블록: 3~12족, 4~7주기
    for (let p = 4; p <= 7; p += 1) for (let c = 3; c <= 12; c += 1) g.push(cell(c, p, 's3'));
    // f 블록: 아래 따로
    const fy = CY(7) + ch + 22;
    for (let i = 0; i < 14; i += 1) {
        g.push(box(CX(4) + i * cw, fy, cw - 2, ch - 2, { fill: 'var(--ink2)', op: 0.2, stroke: 'var(--ink2)', sw: 1 }));
        g.push(box(CX(4) + i * cw, fy + ch, cw - 2, ch - 2, { fill: 'var(--ink2)', op: 0.2, stroke: 'var(--ink2)', sw: 1 }));
    }
    g.push(txt(CX(4) - 8, fy + ch - 6, 'f 블록', { anchor: 'end', cls: 'ink', size: 'sm' }));
    // 족·주기 번호
    for (let c = 1; c <= 18; c += 1) g.push(txt(CX(c) + cw / 2 - 1, oy - 8, String(c), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let p = 1; p <= 7; p += 1) g.push(txt(ox - 8, CY(p) + ch / 2 + 3, String(p), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(ox - 8, oy - 8, '족 →', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(24, CY(4) + 4, '주기', { cls: 'ink2', size: 'sm' }));
    // 블록 이름
    g.push(txt(CX(1) + cw, CY(1) - 26, 's 블록', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(CX(15) + cw, CY(1) - 26, 'p 블록', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(CX(7) + cw, CY(4) - 12, 'd 블록', { anchor: 'middle', cls: 'ink bold' }));
    // 대표 자리
    g.push(txt(CX(1) + cw / 2 - 1, CY(1) + ch / 2 + 3, 'H', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(CX(18) + cw / 2 - 1, CY(1) + ch / 2 + 3, 'He', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(CX(1) + cw / 2 - 1, CY(3) + ch / 2 + 3, 'Na', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(CX(17) + cw / 2 - 1, CY(3) + ch / 2 + 3, 'Cl', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 아래 설명
    g.push(txt(30, fy + 2 * ch + 26, '1족 — 알칼리 금속 (바깥 전자가 하나 남는다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, fy + 2 * ch + 46, '17족 — 할로젠 (전자 하나가 모자란다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, fy + 2 * ch + 66, '18족 — 비활성 기체 (가장 바깥 껍질이 꽉 찼다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 30, '주기율표의 모양은 오비탈이 채워지는 순서 그 자체다', { cls: 'ink bold' }));
    g.push(txt(W - 14, fy + 2 * ch + 46, '같은 족 = 가장 바깥 껍질의 전자 배치가 같다', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 14, fy + 2 * ch + 66, '→ 성질이 닮는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-periodic-blocks',
        title: '주기율표의 s · p · d · f 블록',
        desc: '주기율표에서 가로 줄(주기)은 전자 껍질 번호, 세로 줄(족)은 가장 바깥 껍질의 전자 배치에 해당한다. '
            + '1~2족은 s 오비탈, 13~18족은 p 오비탈, 가운데 열 칸은 d 오비탈이 채워지는 자리다. '
            + '표의 모양 자체가 오비탈을 채우는 순서를 그린 것이다.',
        svg: svg({ width: W, height: H, title: '주기율표의 블록 구조', desc: 's, p, d, f 블록으로 나눈 주기율표 도식', body: g.join('') }),
    };
})());

/* 5-2. 유효핵전하와 차폐 */
add((() => {
    const W = 700, H = 400;
    const cx = 180, cy = 216;
    const g = [];
    g.push(circ(cx, cy, 128, { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(circ(cx, cy, 62, { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(circ(cx, cy, 26, { fill: 'var(--s2)', op: 0.35, stroke: 'var(--s2)', sw: 1.6 }));
    g.push(txt(cx, cy + 5, '+11', { anchor: 'middle', cls: 'ink bold' }));
    // 안쪽 전자 10개
    for (let i = 0; i < 10; i += 1) {
        const a = (i / 10) * 2 * Math.PI + 0.25;
        g.push(circ(cx + 62 * Math.cos(a), cy + 62 * Math.sin(a), 6, { fill: 'var(--s1)', op: 0.8, stroke: 'none', sw: 0 }));
    }
    // 바깥 전자 1개
    g.push(circ(cx, cy - 128, 8, { fill: 'var(--s3)', op: 0.95, stroke: 'none', sw: 0 }));
    g.push(txt(cx + 14, cy - 132, '바깥 전자', { cls: 'ink', size: 'sm' }));
    g.push(px(cx - 14, cy - 118, cx - 14, cy - 34, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(cx - 22, cy - 78, '핵이 끌어당긴다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx - 22, cy - 60, '+11', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(px(cx + 40, cy - 48, cx + 12, cy - 112, { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(cx + 46, cy - 52, '안쪽 전자 10개가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + 46, cy - 34, '밀어낸다  −10', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 96, '남는 것: 대략 +1', { cls: 'ink bold' }));
    g.push(txt(390, 118, '이 값을 유효핵전하라 하고 Z(eff) 로 적는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 160, '같은 주기를 오른쪽으로 가면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 180, '양성자는 하나씩 늘어나는데', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 200, '새 전자는 같은 껍질에 들어가 잘 가려 주지 못한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 226, '→ Z(eff) 가 커지고 원자가 작아진다', { cls: 'ink bold' }));
    g.push(txt(390, 264, '같은 족을 아래로 내려가면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 284, '껍질이 하나 더 붙어 바깥 전자가 멀어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(390, 310, '→ 원자가 커진다', { cls: 'ink bold' }));
    g.push(txt(20, 30, '나트륨 원자 — 바깥 전자 하나가 실제로 느끼는 끌림', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 12, '가려 주는 정도는 껍질마다 달라서 실제 값은 정수가 아니다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-zeff-shielding',
        title: '차폐와 유효핵전하',
        desc: '나트륨의 바깥 전자는 핵의 +11 을 그대로 느끼지 않는다. 안쪽 전자 10개가 사이에서 밀어내기 때문에 '
            + '실제로 느끼는 끌림은 대략 +1 이다. 이 알짜 전하를 유효핵전하라 한다. '
            + '같은 주기 안에서 오른쪽으로 갈수록 유효핵전하가 커지고 원자는 작아진다.',
        svg: svg({ width: W, height: H, title: '차폐와 유효핵전하', desc: '나트륨 원자에서 안쪽 전자가 바깥 전자를 가리는 그림', body: g.join('') }),
    };
})());

/* 5-3. 원자 반지름의 주기적 경향 */
add((() => {
    const W = 640, H = 340;
    // 단일결합 공유결합 반지름 (pm). Ne, Ar 은 결합을 거의 만들지 않아 추정값이다.
    const data = [
        [3, 'Li', 128], [4, 'Be', 96], [5, 'B', 84], [6, 'C', 76], [7, 'N', 71], [8, 'O', 66], [9, 'F', 57],
        [11, 'Na', 166], [12, 'Mg', 141], [13, 'Al', 121], [14, 'Si', 111], [15, 'P', 107], [16, 'S', 105], [17, 'Cl', 102],
        [19, 'K', 203], [20, 'Ca', 176],
    ];
    const g0 = frame({ xRange: [2, 21], yRange: [0, 220], box: { x: 62, y: 50, w: 470, h: 210 } });
    const g = [g0.axes({ xLabel: '원자번호', yLabel: '반지름 (pm)', xTicks: [3, 5, 7, 9, 11, 13, 15, 17, 19], yTicks: [0, 50, 100, 150, 200] })];
    const seg = (from, to, cls) => g0.line(data.filter(d => d[0] >= from && d[0] <= to).map(d => [d[0], d[2]]), { cls });
    g.push(seg(3, 9, 's1'));
    g.push(seg(11, 17, 's2'));
    g.push(seg(19, 20, 's3'));
    for (const [z, sym, r] of data) {
        g.push(g0.dot([z, r], { cls: z <= 9 ? 'f1' : z <= 17 ? 'f2' : 'f3', r: 3.2 }));
        g.push(g0.label([z, r], sym, { dy: -10, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(300, 28, '단일결합 공유결합 반지름 — 톱니처럼 되풀이된다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W - 14, 206, '한 주기 안(Li→F, Na→Cl)에서는 작아진다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 226, '같은 족(Li→Na→K)에서는 커진다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 12, '값은 재는 방법과 정의에 따라 조금씩 다르다. 경향만 읽을 것', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-atomic-radius-trend',
        title: '원자 반지름의 주기적 경향',
        desc: '원자 반지름은 같은 주기 안에서 오른쪽으로 갈수록 작아지고, 같은 족에서 아래로 갈수록 커진다. '
            + '주기마다 같은 모양이 되풀이되는 톱니 무늬가 나타난다. 값은 단일결합 공유결합 반지름 기준이며 '
            + '정의에 따라 조금씩 달라진다.',
        svg: svg({ width: W, height: H, title: '원자 반지름의 주기성', desc: 'Li부터 Ca까지 원자 반지름 그래프', body: g.join('') }),
    };
})());

/* 5-4. 첫 번째 이온화 에너지 */
add((() => {
    const W = 660, H = 360;
    const data = [
        [1, 'H', 1312], [2, 'He', 2372], [3, 'Li', 520], [4, 'Be', 899], [5, 'B', 801], [6, 'C', 1086],
        [7, 'N', 1402], [8, 'O', 1314], [9, 'F', 1681], [10, 'Ne', 2081], [11, 'Na', 496], [12, 'Mg', 738],
        [13, 'Al', 578], [14, 'Si', 787], [15, 'P', 1012], [16, 'S', 1000], [17, 'Cl', 1251], [18, 'Ar', 1521],
        [19, 'K', 419], [20, 'Ca', 590],
    ];
    const g0 = frame({ xRange: [0, 21], yRange: [0, 2600], box: { x: 68, y: 54, w: 470, h: 220 } });
    const g = [g0.axes({ xLabel: '원자번호', yLabel: '이온화 에너지 (kJ/mol)', xTicks: [1, 5, 10, 15, 20], yTicks: [0, 500, 1000, 1500, 2000, 2500] })];
    g.push(g0.line(data.map(d => [d[0], d[2]]), { cls: 's1' }));
    for (const [z, sym, e] of data) {
        const mark = ['He', 'Ne', 'Ar', 'Li', 'Na', 'K'].includes(sym);
        g.push(g0.dot([z, e], { cls: mark ? 'f2' : 'f1', r: mark ? 4 : 2.8 }));
        if (mark) g.push(g0.label([z, e], sym, { dy: sym === 'He' || sym === 'Ne' || sym === 'Ar' ? -10 : 18, anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(g0.label([5, 801], 'B', { dy: 18, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(g0.label([4, 899], 'Be', { dy: -10, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(g0.label([7, 1402], 'N', { dy: -10, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(g0.label([8, 1314], 'O', { dy: 18, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(330, 28, '원자 하나에서 전자 하나를 떼어내는 데 드는 에너지', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W - 14, 76, '봉우리 = 비활성 기체 (떼어내기 가장 어렵다)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 96, '골 = 알칼리 금속 (가장 쉽다)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 130, '작은 예외 두 곳', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 14, 150, 'Be → B : p 가 s 보다 높다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 170, 'N → O : 짝지은 전자끼리 밀어낸다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 12, '한 주기 안에서는 오르고, 다음 주기가 시작될 때 뚝 떨어진다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-ionization-trend',
        title: '첫 번째 이온화 에너지의 주기성',
        desc: '수소부터 칼슘까지 첫 번째 이온화 에너지를 원자번호 순으로 늘어놓았다. 비활성 기체에서 봉우리가 되고 '
            + '알칼리 금속에서 골이 된다. 베릴륨에서 붕소로, 질소에서 산소로 갈 때 오히려 떨어지는 두 예외가 '
            + '오비탈 구조를 그대로 드러낸다.',
        svg: svg({ width: W, height: H, title: '첫 이온화 에너지의 주기성', desc: 'H부터 Ca까지 이온화 에너지 곡선과 두 예외', body: g.join('') }),
    };
})());

/* 5-5. 연속 이온화 에너지의 도약 */
add((() => {
    const W = 620, H = 340;
    const ie = [738, 1451, 7733, 10543, 13630, 18020];
    const g0 = frame({ xRange: [0.3, 6.7], yRange: [0, 20000], box: { x: 78, y: 50, w: 440, h: 216 } });
    const g = [g0.axes({ xLabel: '몇 번째 전자를 떼어내는가', yLabel: 'kJ/mol', xTicks: [1, 2, 3, 4, 5, 6], yTicks: [0, 5000, 10000, 15000, 20000] })];
    ie.forEach((v, i) => {
        const z = i + 1, bw = 40;
        g.push(box(g0.X(z) - bw / 2, g0.Y(v), bw, g0.Y(0) - g0.Y(v),
            { fill: `var(--${z <= 2 ? 's1' : 's2'})`, op: 0.35, stroke: `var(--${z <= 2 ? 's1' : 's2'})`, sw: 1.4, rx: 2 }));
        g.push(txt(g0.X(z), g0.Y(v) - 8, String(v), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(line([[g0.X(2.5), g0.Y(0)], [g0.X(2.5), g0.Y(19000)]], { stroke: 'var(--s3)', sw: 2, dash: '6 4' }));
    g.push(txt(g0.X(2.5) + 8, g0.Y(18000), '여기서 껍질이 바뀐다', { cls: 'ink bold' }));
    g.push(txt(g0.X(2.5) + 8, g0.Y(18000) + 18, '두 개까지는 바깥 껍질(3s)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(g0.X(2.5) + 8, g0.Y(18000) + 36, '세 번째부터는 안쪽 껍질(2p)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 30, '마그네슘에서 전자를 하나씩 떼어낼 때 드는 에너지', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 12, '2 → 3 에서 5배 넘게 뛴다 → 마그네슘의 원자가전자는 두 개다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-successive-ionization',
        title: '마그네슘의 연속 이온화 에너지',
        desc: '전자를 하나씩 떼어낼 때 드는 에너지는 조금씩 늘다가 세 번째에서 갑자기 다섯 배 넘게 뛴다. '
            + '두 개까지는 바깥 껍질에서 빠지지만 세 번째는 안쪽 껍질을 건드려야 하기 때문이다. '
            + '이 도약 자리가 그 원소의 원자가전자 수를 알려준다.',
        svg: svg({ width: W, height: H, title: '연속 이온화 에너지의 도약', desc: '마그네슘의 1~6차 이온화 에너지 막대', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 6장 — 화학 결합
 * ================================================================== */

/* 6-1. 두 원자 사이의 에너지 곡선 */
add((() => {
    const W = 640, H = 370;
    const D = 436, r0 = 74, a = 0.035;
    const V = r => D * ((1 - Math.exp(-a * (r - r0))) ** 2 - 1);
    const g0 = frame({ xRange: [45, 300], yRange: [-520, 360], box: { x: 78, y: 56, w: 430, h: 236 } });
    const g = [
        g0.axes({ xLabel: 'pm', yLabel: 'kJ/mol', xTicks: [50, 100, 150, 200, 250, 300], yTicks: [-500, -250, 0, 250] }),
        g0.curve(V, { from: 51, to: 300, cls: 's1' }),
        g0.guide([r0, -520], [r0, -60]),
        g0.guide([45, -D], [r0, -D]),
        g0.dot([r0, -D], { cls: 'f2', r: 4 }),
        g0.label([r0, -520], '74 pm = 결합 길이', { dx: 6, dy: -6, cls: 'ink', size: 'sm' }),
        px(g0.X(150), g0.Y(-D), g0.X(150), g0.Y(0), { cls: 's2', marker: 'ar2', width: 2 }),
        px(g0.X(150), g0.Y(0), g0.X(150), g0.Y(-D), { cls: 's2', marker: 'ar2', width: 2 }),
        txt(g0.X(158), g0.Y(-200), '436 kJ/mol = 결합 에너지', { cls: 'ink' }),
        txt(g0.X(158), g0.Y(-200) + 18, '이만큼을 주어야 떼어놓을 수 있다', { cls: 'ink2', size: 'sm' }),
        txt(g0.X(88), g0.Y(300), '너무 가까우면 핵끼리 밀어낸다', { cls: 'ink2', size: 'sm' }),
        txt(g0.X(230), g0.Y(60), '멀면 서로 남남 (에너지 0)', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(300, 28, '수소 원자 두 개를 가까이 가져갈 때의 에너지', { anchor: 'middle', cls: 'ink bold' }),
        txt(W - 14, H - 12, '에너지가 가장 낮은 자리에 자리 잡는 것 — 그것이 결합이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'chem-bond-energy-curve',
        title: '두 원자 사이 거리와 에너지',
        desc: '수소 원자 두 개를 가까이 가져가면 에너지가 낮아지다가 74 pm 에서 가장 낮아지고, 더 가까워지면 '
            + '핵끼리 밀어내 다시 치솟는다. 가장 낮은 자리의 거리가 결합 길이이고, 그 깊이가 결합 에너지다. '
            + '결합이란 에너지가 낮아지기 때문에 생기는 것이다.',
        svg: svg({ width: W, height: H, title: '결합 길이와 결합 에너지', desc: '수소 분자의 에너지 곡선', body: g.join('') }),
    };
})());

/* 6-2. 이온 결정의 격자 */
add((() => {
    const W = 620, H = 330;
    const ox = 120, oy = 70, s = 46;
    const g = [];
    for (let i = 0; i < 5; i += 1) {
        for (let j = 0; j < 5; j += 1) {
            const na = (i + j) % 2 === 0;
            const cx = ox + i * s, cy = oy + j * s;
            g.push(circ(cx, cy, na ? 12 : 20, {
                fill: na ? 'var(--s1)' : 'var(--s3)', op: 0.4, stroke: na ? 'var(--s1)' : 'var(--s3)', sw: 1.3,
            }));
            g.push(txt(cx, cy + 4, na ? 'Na⁺' : 'Cl⁻', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    }
    g.push(box(ox - s / 2, oy - s / 2, 2 * s, 2 * s, { stroke: 'var(--s2)', sw: 2, dash: '6 4', rx: 2 }));
    g.push(txt(ox + 1.7 * s, oy - s / 2 - 8, '되풀이되는 최소 단위', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 96, '분자가 따로 없다', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 14, 120, '"NaCl 분자 하나"라는 것은 없고', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 140, '이온이 1 : 1 로 끝없이 이어진 덩어리다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 174, 'NaCl 은 그 개수비를 적은 화학식이다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 214, '모든 이온이 반대 부호 이웃에 둘러싸여 있어', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 234, '떼어내기 어렵다 → 녹는점이 높고 단단하다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 30, '염화나트륨 결정의 단면', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '실제로는 이 무늬가 삼차원으로 이어져 각 이온이 이웃 6개에 둘러싸인다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-ionic-lattice',
        title: '염화나트륨 결정의 격자',
        desc: '이온 결합 물질에는 분자라는 단위가 없다. 양이온과 음이온이 번갈아 끝없이 이어진 하나의 덩어리이고, '
            + 'NaCl 이라는 화학식은 그 개수비를 적은 것이다. 모든 이온이 반대 부호 이웃에 둘러싸여 있어 '
            + '떼어내기 어렵고, 그래서 녹는점이 높다.',
        svg: svg({ width: W, height: H, title: '이온 결정의 격자', desc: 'Na+ 와 Cl- 가 번갈아 놓인 결정 단면', body: g.join('') }),
    };
})());

/* 6-3. 루이스 구조의 예 */
add((() => {
    const W = 670, H = 400;
    const g = [];
    const A = (x, y, s) => txt(x, y + 5, s, { anchor: 'middle', cls: 'ink bold' });
    const R = 15;   // 기호 주변으로 비워 둘 반지름
    const B = (p1, p2, n) => bond(p1, p2, n, { r1: R, r2r: R, sw: 1.7, gap: 5 });

    // 패널 1 — 물
    g.push(panel(12, 20, 206, 168, 'H₂O', { sub: '전자 8개, 결합 2 + 홀전자쌍 2' }));
    let c = [115, 118];
    g.push(B([c[0] - 52, c[1] + 26], c, 1), B(c, [c[0] + 52, c[1] + 26], 1));
    g.push(A(c[0], c[1], 'O'), A(c[0] - 58, c[1] + 26, 'H'), A(c[0] + 58, c[1] + 26, 'H'));
    g.push(lonePair(c[0], c[1], 20, 115), lonePair(c[0], c[1], 20, 65));

    // 패널 2 — 암모니아
    g.push(panel(230, 20, 206, 168, 'NH₃', { sub: '결합 3 + 홀전자쌍 1' }));
    c = [333, 122];
    g.push(B([c[0] - 52, c[1] + 22], c, 1), B(c, [c[0] + 52, c[1] + 22], 1), B(c, [c[0], c[1] + 48], 1));
    g.push(A(c[0], c[1], 'N'), A(c[0] - 58, c[1] + 22, 'H'), A(c[0] + 58, c[1] + 22, 'H'), A(c[0], c[1] + 54, 'H'));
    g.push(lonePair(c[0], c[1], 20, 90));

    // 패널 3 — 이산화탄소
    g.push(panel(448, 20, 210, 168, 'CO₂', { sub: '이중결합 두 개, 직선' }));
    c = [553, 112];
    g.push(B([c[0] - 56, c[1]], c, 2), B(c, [c[0] + 56, c[1]], 2));
    g.push(A(c[0], c[1], 'C'), A(c[0] - 62, c[1], 'O'), A(c[0] + 62, c[1], 'O'));
    g.push(lonePair(c[0] - 62, c[1], 18, 90), lonePair(c[0] - 62, c[1], 18, 270));
    g.push(lonePair(c[0] + 62, c[1], 18, 90), lonePair(c[0] + 62, c[1], 18, 270));

    // 패널 4 — 질소
    g.push(panel(12, 200, 206, 168, 'N₂', { sub: '삼중결합. 아주 튼튼하다' }));
    c = [115, 292];
    g.push(B([c[0] - 40, c[1]], [c[0] + 40, c[1]], 3));
    g.push(A(c[0] - 46, c[1], 'N'), A(c[0] + 46, c[1], 'N'));
    g.push(lonePair(c[0] - 46, c[1], 20, 180), lonePair(c[0] + 46, c[1], 20, 0));

    // 패널 5 — 오존의 공명
    g.push(panel(230, 200, 428, 168, 'O₃ — 공명', { sub: '어느 한쪽이 아니라 둘의 평균이 실제 모습이다' }));
    // 이중결합 쪽 끝 산소는 홀전자쌍 2개, 단일결합 쪽 끝 산소는 3개, 가운데는 1개.
    const ozone = (bx, leftDouble) => {
        const o = [bx, 288];
        const l = [bx - 56, 322], r = [bx + 56, 322];
        const pairs = (p, angs) => angs.map(a => lonePair(p[0], p[1], 18, a)).join('');
        return B(l, o, leftDouble ? 2 : 1) + B(o, r, leftDouble ? 1 : 2)
            + A(o[0], o[1], 'O') + A(l[0], l[1], 'O') + A(r[0], r[1], 'O')
            + lonePair(o[0], o[1], 19, 90)
            + pairs(l, leftDouble ? [175, 250] : [175, 250, 315])
            + pairs(r, leftDouble ? [5, 290, 225] : [5, 290]);
    };
    g.push(ozone(320, true));
    g.push(ozone(570, false));
    g.push(txt(445, 298, '↔', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(445, 330, '두 구조를', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(445, 346, '오가는 것이 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 8, '점 두 개 = 홀전자쌍(비공유 전자쌍), 선 하나 = 공유한 전자 두 개',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-lewis-examples',
        title: '루이스 구조 다섯 가지',
        desc: '선 하나는 공유한 전자 두 개, 점 두 개는 한 원자에만 속한 홀전자쌍이다. '
            + '물과 암모니아는 홀전자쌍을 가지고 있고, 이산화탄소는 이중결합 두 개, 질소는 삼중결합이다. '
            + '오존처럼 한 가지 구조로 다 적을 수 없는 경우에는 여러 구조를 함께 적고, 실제 모습은 그 평균이다.',
        svg: svg({ width: W, height: H, title: '루이스 구조의 예', desc: 'H2O, NH3, CO2, N2, O3 의 루이스 구조', body: g.join('') }),
    };
})());

/* 6-4. VSEPR — 전자쌍 수가 모양을 정한다 */
add((() => {
    const W = 690, H = 580;
    const pw = 218, ph = 178;
    const cols = [12, 236, 460], rows = [20, 210, 400];
    const g = [];
    /** 중심 원자에서 각 ang 방향으로 결합 하나. style: 'plain' | 'wedge' | 'dash' */
    const link = (mx, my, ang, len, style, label) => {
        const a = (ang * Math.PI) / 180;
        const ex = mx + len * Math.cos(a), ey = my - len * Math.sin(a);
        const nx = -Math.sin(a), ny = -Math.cos(a);
        let sBody = '';
        if (style === 'wedge') {
            sBody = poly([[mx + nx * 2, my + ny * 2], [ex + nx * 7, ey + ny * 7], [ex - nx * 7, ey - ny * 7], [mx - nx * 2, my - ny * 2]],
                { fill: 'var(--ink2)', op: 0.85 });
        } else if (style === 'dash') {
            const out = [];
            for (let i = 1; i <= 5; i += 1) {
                const t = i / 5.5, w = 2 + 5 * t;
                const px0 = mx + (ex - mx) * t, py0 = my + (ey - my) * t;
                out.push(line([[px0 + nx * w, py0 + ny * w], [px0 - nx * w, py0 - ny * w]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
            }
            sBody = out.join('');
        } else {
            sBody = line([[mx, my], [ex, ey]], { stroke: 'var(--ink2)', sw: 1.8 });
        }
        return sBody + circ(ex, ey, 12, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1.2 })
            + (label ? txt(ex, ey + 4, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
    };
    const lp = (mx, my, ang, len) => lobe(mx, my, len, 30, ang, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)', sw: 1 });
    const cell = (i, j, title, sub, draw, { core = true } = {}) => {
        const x = cols[i], y = rows[j];
        const mx = x + pw / 2, my = y + ph / 2 + 20;
        g.push(panel(x, y, pw, ph, title, { sub }));
        g.push(draw(mx, my));
        if (core) {
            g.push(circ(mx, my, 15, { fill: 'var(--ink2)', op: 0.25, stroke: 'var(--ink2)', sw: 1.4 }));
            g.push(txt(mx, my + 4, 'A', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
    };
    const L = 52;
    cell(0, 0, '전자쌍 2 — 직선형', '180°  예: CO₂',
        (mx, my) => link(mx, my, 0, L, 'plain') + link(mx, my, 180, L, 'plain'));
    cell(1, 0, '전자쌍 3 — 평면삼각형', '120°  예: BF₃',
        (mx, my) => link(mx, my, 90, L, 'plain') + link(mx, my, 210, L, 'plain') + link(mx, my, 330, L, 'plain'));
    cell(2, 0, '전자쌍 4 — 사면체', '109.5°  예: CH₄',
        (mx, my) => link(mx, my, 90, L, 'plain') + link(mx, my, 200, L, 'plain')
            + link(mx, my, 340, L, 'wedge') + link(mx, my, 260, L - 12, 'dash'));
    cell(0, 1, '전자쌍 5 — 삼각쌍뿔', '90° 와 120°  예: PCl₅',
        (mx, my) => link(mx, my, 90, L, 'plain') + link(mx, my, 270, L, 'plain')
            + link(mx, my, 180, L, 'plain') + link(mx, my, 20, L, 'wedge') + link(mx, my, 325, L - 6, 'dash'));
    cell(1, 1, '전자쌍 6 — 팔면체', '90°  예: SF₆',
        (mx, my) => link(mx, my, 90, L, 'plain') + link(mx, my, 270, L, 'plain')
            + link(mx, my, 0, L, 'plain') + link(mx, my, 180, L, 'plain')
            + link(mx, my, 315, L - 8, 'wedge') + link(mx, my, 135, L - 8, 'dash'));
    cell(2, 1, '읽는 법', '', () => '', { core: false });
    g.push(txt(cols[2] + 20, rows[1] + 66, '① 중심 원자 둘레의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cols[2] + 20, rows[1] + 84, '   전자쌍 수를 센다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cols[2] + 20, rows[1] + 106, '② 전자쌍끼리 가장 멀어지는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cols[2] + 20, rows[1] + 124, '   배치를 고른다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cols[2] + 20, rows[1] + 146, '③ 홀전자쌍 자리를 지우고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cols[2] + 20, rows[1] + 164, '   원자만 보고 이름을 붙인다', { cls: 'ink2', size: 'sm' }));
    const lpTag = (mx, my, dy) => txt(mx, my - dy, '주황색 = 홀전자쌍', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    cell(0, 2, '4쌍 중 1쌍이 홀전자쌍', '삼각뿔형 107°  예: NH₃',
        (mx, my) => link(mx, my, 200, L, 'plain') + link(mx, my, 340, L, 'wedge')
            + link(mx, my, 280, L - 10, 'dash') + lp(mx, my, 90, 44) + lpTag(mx, my, 62));
    cell(1, 2, '4쌍 중 2쌍이 홀전자쌍', '굽은형 104.5°  예: H₂O',
        (mx, my) => link(mx, my, 235, L, 'plain') + link(mx, my, 305, L, 'plain')
            + lp(mx, my, 55, 42) + lp(mx, my, 125, 42) + lpTag(mx, my, 62));
    cell(2, 2, '3쌍 중 1쌍이 홀전자쌍', '굽은형 약 119°  예: SO₂',
        (mx, my) => link(mx, my, 210, L, 'plain') + link(mx, my, 330, L, 'plain')
            + lp(mx, my, 90, 44) + lpTag(mx, my, 62));
    g.push(txt(20, H - 8, '홀전자쌍은 결합 전자쌍보다 넓게 퍼져 더 세게 밀어낸다 → 결합각이 조금씩 좁아진다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-vsepr-shapes',
        title: 'VSEPR — 전자쌍 수로 정해지는 분자 모양',
        desc: '중심 원자 둘레의 전자쌍은 서로 밀어내므로 가능한 한 멀리 떨어진 배치를 잡는다. '
            + '전자쌍이 2, 3, 4, 5, 6 개일 때 각각 직선·평면삼각·사면체·삼각쌍뿔·팔면체가 된다. '
            + '그중 일부가 홀전자쌍이면 그 자리는 비어 보이므로 삼각뿔형·굽은형 같은 이름이 붙고, '
            + '홀전자쌍이 더 세게 밀기 때문에 결합각이 조금 좁아진다.',
        svg: svg({ width: W, height: H, title: 'VSEPR 로 정해지는 분자 모양', desc: '전자쌍 2~6개와 홀전자쌍이 있는 경우의 모양', body: g.join('') }),
    };
})());

/* 6-5. 결합의 극성과 분자의 극성 */
add((() => {
    const W = 640, H = 320;
    const g = [];
    g.push(panel(12, 20, 300, 260, 'CO₂ — 무극성 분자', { sub: '극성 결합 두 개가 서로 지운다' }));
    let mx = 162, my = 160;
    g.push(line([[mx - 62, my], [mx + 62, my]], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(atom(mx, my, 20, 'C', { fill: 'var(--ink2)', op: 0.2, stroke: 'var(--ink2)' }));
    g.push(atom(mx - 76, my, 22, 'O', { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)' }));
    g.push(atom(mx + 76, my, 22, 'O', { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)' }));
    g.push(px(mx - 6, my - 40, mx - 62, my - 40, { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(px(mx + 6, my - 40, mx + 62, my - 40, { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(mx, my + 62, '합 = 0', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(mx, my + 84, '크기가 같고 방향이 반대다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(mx, my - 54, '결합 쌍극자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(328, 20, 300, 260, 'H₂O — 극성 분자', { sub: '굽어 있어 지워지지 않는다' }));
    mx = 478; my = 176;
    g.push(line([[mx, my], [mx - 58, my + 34]], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(line([[mx, my], [mx + 58, my + 34]], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(atom(mx, my, 22, 'O', { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)' }));
    g.push(atom(mx - 66, my + 38, 16, 'H', { fill: 'var(--ink2)', op: 0.2, stroke: 'var(--ink2)' }));
    g.push(atom(mx + 66, my + 38, 16, 'H', { fill: 'var(--ink2)', op: 0.2, stroke: 'var(--ink2)' }));
    g.push(px(mx - 48, my + 40, mx - 12, my + 18, { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(px(mx + 48, my + 40, mx + 12, my + 18, { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(px(mx, my + 6, mx, my - 52, { cls: 's2', marker: 'ar2', width: 3 }));
    g.push(txt(mx + 8, my - 44, '합이 남는다', { cls: 'ink bold' }));
    g.push(txt(mx, my + 88, '이 남은 화살표가 물의 모든 성질을 만든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '화살표는 전자가 끌려가는 쪽을 가리킨다. 결합이 극성이어도 모양에 따라 분자는 무극성일 수 있다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-dipole-water-co2',
        title: '결합의 극성과 분자의 극성은 다르다',
        desc: '이산화탄소와 물은 둘 다 산소가 전자를 끌어당기는 극성 결합을 가지고 있다. '
            + '그런데 이산화탄소는 직선이라 두 화살표가 정확히 지워져 무극성이 되고, '
            + '물은 굽어 있어 합이 남아 극성이 된다. 결합의 극성만으로는 분자의 극성을 알 수 없고 모양을 함께 봐야 한다.',
        svg: svg({ width: W, height: H, title: '분자의 극성', desc: 'CO2 는 지워지고 H2O 는 남는 쌍극자', body: g.join('') }),
    };
})());

/* 6-6. 혼성 오비탈 */
add((() => {
    const W = 660, H = 280;
    const g = [];
    const cells = [
        ['sp', [0, 180], '직선 180°   예: 아세틸렌의 C'],
        ['sp²', [90, 210, 330], '평면삼각 120°   예: 에틸렌의 C'],
        ['sp³', [90, 210, 330, 270], '사면체 109.5°   예: 메테인의 C'],
    ];
    cells.forEach(([name, angs, sub], i) => {
        const x = 12 + i * 214;
        g.push(panel(x, 20, 206, 220, name, { sub }));
        const mx = x + 103, my = 146;
        for (const a of angs) {
            g.push(lobe(mx, my, 62, 34, a, { fill: 'var(--s1)', op: 0.32, stroke: 'var(--s1)' }));
            g.push(lobe(mx, my, 20, 14, a + 180, { fill: 'var(--s2)', op: 0.25, stroke: 'var(--s2)' }));
        }
        g.push(circ(mx, my, 6, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    });
    g.push(txt(20, H - 10, 's 하나와 p 몇 개를 섞으면 같은 모양의 새 오비탈이 그 개수만큼 생긴다. 큰 잎이 결합에 쓰인다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-hybrid-orbitals',
        title: 'sp · sp² · sp³ 혼성 오비탈',
        desc: 's 오비탈 하나와 p 오비탈 몇 개를 섞으면 모양과 에너지가 똑같은 새 오비탈이 섞은 개수만큼 생긴다. '
            + 'sp 는 둘이 180도, sp² 는 셋이 120도, sp³ 는 넷이 109.5도로 벌어진다. '
            + 'VSEPR 이 예측한 모양과 정확히 같은 배치이고, 큰 잎이 결합에 쓰인다.',
        svg: svg({ width: W, height: H, title: '혼성 오비탈', desc: 'sp, sp2, sp3 혼성 오비탈의 배치', body: g.join('') }),
    };
})());

/* 6-7. 산소 분자의 분자 오비탈 */
add((() => {
    const W = 680, H = 490;
    const xa = 110, xm = 340, xb = 570;
    const lw = 44;
    const g = [];
    const lvl = (x, y, w, label, anchor = 'middle', dy = -10) => line([[x - w / 2, y], [x + w / 2, y]], { stroke: 'var(--ink2)', sw: 1.8, cap: 'butt' })
        + (label ? txt(anchor === 'end' ? x - w / 2 - 8 : x, y + (anchor === 'end' ? 4 : dy), label, { anchor, cls: 'ink', size: 'sm' }) : '');
    /** 칸 하나에 전자 화살표 k 개(1 또는 2). */
    const el = (x, y, k) => (k >= 1 ? txt(x - (k === 2 ? 7 : 0), y + 5, '↑', { anchor: 'middle', cls: 'ink' }) : '')
        + (k === 2 ? txt(x + 7, y + 5, '↓', { anchor: 'middle', cls: 'ink' }) : '');
    // 원자 오비탈
    const y2s = 372, y2p = 250;
    for (const x of [xa, xb]) {
        g.push(lvl(x, y2s, lw, '2s'));
        g.push(el(x, y2s, 2));
        for (let i = -1; i <= 1; i += 1) {
            g.push(lvl(x + i * 52, y2p, lw, i === 0 ? '2p' : ''));
        }
        g.push(el(x - 52, y2p, 2), el(x, y2p, 1), el(x + 52, y2p, 1));
    }
    g.push(txt(xa, 60, '산소 원자', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(xb, 60, '산소 원자', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(xm, 60, '산소 분자 O₂', { anchor: 'middle', cls: 'ink bold' }));
    // 분자 오비탈
    // O₂ 는 σ2p 가 π2p 보다 낮다(Z ≥ 8). 화면에서는 아래쪽이 낮은 에너지다.
    const mo = [
        ['σ*2p', 106, [0], 1],
        ['π*2p', 168, [-34, 34], 1],
        ['π2p', 216, [-34, 34], 2],
        ['σ2p', 272, [0], 2],
        ['σ*2s', 336, [0], 2],
        ['σ2s', 420, [0], 2],
    ];
    for (const [name, y, xs, fill] of mo) {
        for (const dx of xs) {
            g.push(lvl(xm + dx, y, lw, ''));
            g.push(el(xm + dx, y, name === 'π*2p' ? 1 : fill));
        }
        g.push(txt(xm - (xs.length > 1 ? 78 : 44), y + 4, name, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    // 연결선
    const link2 = (x1, y1, x2, y2) => line([[x1, y1], [x2, y2]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' });
    g.push(link2(xa + lw / 2, y2s, xm - lw / 2, 420), link2(xb - lw / 2, y2s, xm + lw / 2, 420));
    g.push(link2(xa + lw / 2, y2s, xm - lw / 2, 336), link2(xb - lw / 2, y2s, xm + lw / 2, 336));
    g.push(link2(xa + 52 + lw / 2, y2p, xm - lw / 2, 272), link2(xb - 52 - lw / 2, y2p, xm + lw / 2, 272));
    g.push(link2(xa + 52 + lw / 2, y2p, xm - 34 - lw / 2, 216), link2(xb - 52 - lw / 2, y2p, xm + 34 + lw / 2, 216));
    // 결론
    g.push(box(xm - 34 - lw / 2 - 8, 154, 2 * (34 + lw / 2) + 16, 28, { stroke: 'var(--s2)', sw: 1.6, dash: '5 4', rx: 5 }));
    g.push(txt(xm + 84, 146, '짝이 없는 전자 두 개', { cls: 'ink bold' }));
    g.push(txt(20, 30, '왜 산소는 자석에 끌리는가 — 루이스 구조로는 답할 수 없다', { cls: 'ink bold' }));
    g.push(txt(20, H - 32, '결합 차수 = (결합성 8 − 반결합성 4) ÷ 2 = 2  → 이중결합에 해당', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 12, '짝 없는 전자가 둘이라 산소는 자기장에 끌린다. 이것이 분자 오비탈 이론의 대표적인 성과다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-mo-diagram',
        title: '산소 분자의 분자 오비탈',
        desc: '두 산소 원자의 오비탈이 섞이면 에너지가 낮아진 결합성 오비탈과 높아진 반결합성 오비탈이 생긴다. '
            + '전자 12개를 낮은 것부터 채우면 반결합성 파이 오비탈 두 칸에 짝 없는 전자가 하나씩 남는다. '
            + '이것이 산소가 자석에 끌리는 이유이고, 루이스 구조로는 설명되지 않는 부분이다.',
        svg: svg({ width: W, height: H, title: '산소 분자의 분자 오비탈', desc: 'O2 의 MO 에너지 도표와 짝 없는 전자 두 개', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 7장 — 물질의 상태
 * ================================================================== */

/* 7-1. 수소화물의 끓는점 — 수소결합의 증거 */
add((() => {
    const W = 660, H = 380;
    const series = [
        ['14족 (CH₄ …)', 's1', [[2, -161.5, 'CH₄'], [3, -111.8, 'SiH₄'], [4, -88.5, 'GeH₄'], [5, -52, 'SnH₄']]],
        ['15족 (NH₃ …)', 's2', [[2, -33.3, 'NH₃'], [3, -87.7, 'PH₃'], [4, -62.5, 'AsH₃'], [5, -17, 'SbH₃']]],
        ['16족 (H₂O …)', 's3', [[2, 100, 'H₂O'], [3, -60.7, 'H₂S'], [4, -41.3, 'H₂Se'], [5, -2, 'H₂Te']]],
        ['17족 (HF …)', 'ink2', [[2, 19.5, 'HF'], [3, -85, 'HCl'], [4, -66.8, 'HBr'], [5, -35.4, 'HI']]],
    ];
    const g0 = frame({ xRange: [1.8, 5.4], yRange: [-180, 130], box: { x: 74, y: 62, w: 330, h: 236 } });
    const g = [g0.axes({ xLabel: '주기', yLabel: '끓는점 (°C)', xTicks: [2, 3, 4, 5], yTicks: [-150, -100, -50, 50, 100] })];
    for (const [, cls, pts] of series) {
        g.push(g0.line(pts.map(p => [p[0], p[1]]), { cls: cls === 'ink2' ? 's1' : cls, dash: cls === 'ink2' ? '6 4' : undefined }));
        for (const [x, y, name] of pts) {
            g.push(g0.dot([x, y], { cls: cls === 'ink2' ? 'f1' : `f${cls.slice(1)}`, r: 3.2 }));
            // 가운데 점까지 이름을 달면 겹친다. 양 끝만 적는다.
            if (x === 2) g.push(g0.label([x, y], name, { dx: -8, dy: 4, anchor: 'end', cls: 'ink2', size: 'sm' }));
            if (x === 5) g.push(g0.label([x, y], name, { dx: 8, dy: 4, cls: 'ink2', size: 'sm' }));
        }
    }
    g.push(legend(470, 92, [{ slot: 1, name: '14족 CH₄ SiH₄ GeH₄ SnH₄' }, { slot: 2, name: '15족 NH₃ PH₃ AsH₃ SbH₃' },
        { slot: 3, name: '16족 H₂O H₂S H₂Se H₂Te' }, { slot: 1, name: '17족 HF HCl HBr HI (점선)' }]));
    g.push(txt(470, 190, 'H₂O · HF · NH₃ 세 개만', { cls: 'ink bold' }));
    g.push(txt(470, 210, '줄의 첫머리에서 튀어 오른다', { cls: 'ink bold' }));
    g.push(txt(470, 232, '→ 수소결합 때문이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(470, 262, '물이 14족처럼 굴었다면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(470, 280, '끓는점이 −80 °C 쯤이어서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(470, 298, '지구에 바다가 없었을 것이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(300, 30, '수소화물의 끓는점 — 세 점만 줄에서 벗어난다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(20, H - 10, 'N·O·F 에 붙은 수소만 이웃 분자를 특별히 강하게 붙잡는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-hydride-boiling',
        title: '수소화물의 끓는점과 수소결합',
        desc: '같은 족의 수소화물은 무거워질수록 끓는점이 고르게 오른다. 그런데 물, 플루오린화 수소, 암모니아 '
            + '세 개만 줄의 첫머리에서 크게 튀어 오른다. 질소·산소·플루오린에 붙은 수소가 이웃 분자를 특별히 '
            + '강하게 붙잡기 때문이고, 이 힘이 수소결합이다. 물이 상온에서 액체인 이유가 여기 있다.',
        svg: svg({ width: W, height: H, title: '수소화물의 끓는점', desc: '14~17족 수소화물의 끓는점과 수소결합에 의한 예외', body: g.join('') }),
    };
})());

/* 7-2. 보일 법칙과 샤를 법칙 */
add((() => {
    const W = 700, H = 340;
    const g = [];
    // (가) 보일
    const gA = frame({ xRange: [0, 5.2], yRange: [0, 260], box: { x: 66, y: 82, w: 250, h: 190 } });
    g.push(gA.axes({ xLabel: 'V (L)', yLabel: 'P (kPa)', xTicks: [1, 2, 3, 4, 5], yTicks: [100, 200] }));
    g.push(gA.curve(v => 200 / v, { from: 0.85, to: 5.2, cls: 's1' }));
    g.push(gA.curve(v => 300 / v, { from: 1.25, to: 5.2, cls: 's2', dash: '6 4' }));
    g.push(gA.label([4.4, 200 / 4.4], '낮은 T', { dx: 4, dy: 14, cls: 'ink2', size: 'sm' }));
    g.push(gA.label([4.4, 300 / 4.4], '높은 T', { dx: 4, dy: -8, cls: 'ink2', size: 'sm' }));
    g.push(txt(190, 34, '(가) 온도·몰수 고정 — 보일', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(190, 320, 'P V = 일정 — 부피를 반으로 줄이면 압력이 두 배', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // (나) 샤를 — 가로축을 켈빈으로 잡으면 원점이 그대로 절대영도다
    const gB = frame({ xRange: [0, 420], yRange: [0, 5], box: { x: 420, y: 82, w: 240, h: 190 } });
    g.push(gB.axes({ xLabel: 'T (K)', yLabel: 'V (L)', xTicks: [0, 100, 200, 300, 400], yTicks: [1, 2, 3, 4] }));
    g.push(gB.line([[0, 0], [273, 3]], { cls: 's1', dash: '6 4' }));
    g.push(gB.line([[273, 3], [373, 4.1]], { cls: 's1' }));
    g.push(gB.dot([0, 0], { cls: 'f2', r: 4 }));
    g.push(gB.dot([273, 3], { cls: 'f1', r: 3.2 }));
    g.push(gB.label([273, 3], '0 °C', { dx: 4, dy: -8, cls: 'ink2', size: 'sm' }));
    g.push(gB.label([10, 0.6], '여기서 부피가 0', { cls: 'ink', size: 'sm' }));
    g.push(gB.label([10, 0.6], '0 K = −273.15 °C', { dy: 16, cls: 'ink2', size: 'sm' }));
    g.push(txt(540, 34, '(나) 압력·몰수 고정 — 샤를', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(540, 320, '실선이 실제로 잰 구간, 점선이 늘여 그은 부분', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-gas-laws',
        title: '보일 법칙과 샤를 법칙',
        desc: '온도를 고정하면 압력과 부피는 반비례해서 곡선이 쌍곡선이 된다. 압력을 고정하면 부피는 온도에 '
            + '대해 직선이 되는데, 그 직선을 왼쪽으로 늘여 그으면 어떤 기체로 재도 −273.15 °C 에서 부피가 0이 된다. '
            + '이 자리가 절대영도이고, 켈빈 눈금의 0이다.',
        svg: svg({ width: W, height: H, title: '보일 법칙과 샤를 법칙', desc: 'P-V 쌍곡선과 V-T 직선의 절대영도 외삽', body: g.join('') }),
    };
})());

/* 7-3. 맥스웰 속력 분포 */
add((() => {
    const W = 640, H = 340;
    const R = 8.314, M = 0.028;
    const f = (v, T) => 4 * Math.PI * (M / (2 * Math.PI * R * T)) ** 1.5 * v * v * Math.exp(-M * v * v / (2 * R * T));
    const S = 2.7e-3;
    const g0 = frame({ xRange: [0, 2400], yRange: [0, 1.05], box: { x: 62, y: 52, w: 450, h: 210 } });
    const g = [g0.axes({ xLabel: '속력 (m/s)', yLabel: '분자 수', xTicks: [0, 500, 1000, 1500, 2000], yTicks: [] })];
    for (const [T, cls] of [[200, 's1'], [300, 's2'], [1000, 's3']]) {
        g.push(g0.curve(v => f(v, T) / S, { from: 0, to: 2400, cls }));
        const vmp = Math.sqrt(2 * R * T / M);
        g.push(g0.label([vmp, f(vmp, T) / S], `${T} K`, { dx: 6, dy: -8, cls: 'ink', size: 'sm' }));
        g.push(g0.guide([vmp, 0], [vmp, f(vmp, T) / S]));
    }
    g.push(txt(20, 28, '질소 분자의 속력 분포 — 온도가 오르면 오른쪽으로 퍼진다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 32, '봉우리 자리 = 가장 흔한 속력. 오른쪽 꼬리는 길게 이어진다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 12, '세 곡선 아래 넓이는 모두 같다(분자 수가 같으므로)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-maxwell-distribution',
        title: '기체 분자의 속력 분포',
        desc: '같은 온도의 기체라도 분자마다 속력이 다르다. 온도가 오르면 분포 전체가 오른쪽으로 밀리면서 낮고 '
            + '넓게 퍼진다. 곡선 아래 넓이는 분자 수이므로 세 곡선 모두 같다. 오른쪽 꼬리가 길게 이어지는 것이 '
            + '반응 속도와 증발을 이해하는 열쇠다.',
        svg: svg({ width: W, height: H, title: '맥스웰 속력 분포', desc: '질소 분자의 200, 300, 1000 K 속력 분포', body: g.join('') }),
    };
})());

/* 7-4. 실제 기체의 압축인자 */
add((() => {
    const W = 620, H = 330;
    const g0 = frame({ xRange: [0, 60], yRange: [0.4, 1.7], box: { x: 72, y: 66, w: 420, h: 200 } });
    // 개략적인 모양만 보이는 곡선이다. 실제 값은 온도와 기체마다 다르다.
    const ch4 = p => 1 - 0.030 * p * Math.exp(-p / 16) + 0.0045 * p;
    const n2 = p => 1 - 0.013 * p * Math.exp(-p / 16) + 0.0065 * p;
    const h2 = p => 1 + 0.006 * p;
    const g = [
        g0.axes({ xLabel: 'MPa', yLabel: 'Z', xTicks: [0, 10, 20, 30, 40, 50, 60], yTicks: [0.5, 1.0, 1.5] }),
        line([[g0.X(0), g0.Y(1)], [g0.X(60), g0.Y(1)]], { stroke: 'var(--ink2)', sw: 1.6, dash: '7 5' }),
        g0.label([30, 1], '이상기체 (Z = 1)', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g0.curve(ch4, { cls: 's1' }),
        g0.curve(n2, { cls: 's2' }),
        g0.curve(h2, { cls: 's3' }),
        g0.label([50, ch4(50)], 'CH₄', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }),
        g0.label([52, n2(52)], 'N₂', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }),
        g0.label([56, h2(56)], 'H₂', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }),
        txt(300, 30, '실제 기체는 PV = nRT 에서 얼마나 벗어나는가', { anchor: 'middle', cls: 'ink bold' }),
        txt(300, 48, '가로축 압력 (MPa), 세로축 Z = PV / nRT. 300 K 부근의 개략도다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 32, 'Z 가 1보다 작다 : 분자끼리 서로 끌어당겨 부피가 예상보다 작다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 12, 'Z 가 1보다 크다 : 분자 자신의 부피를 무시할 수 없게 된다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'chem-real-gas-z',
        title: '실제 기체의 압축인자',
        desc: 'PV 를 nRT 로 나눈 값 Z 는 이상기체라면 언제나 1이다. 실제 기체는 압력이 낮을 때 분자 사이의 '
            + '끌어당김 때문에 Z 가 1보다 작아지고, 압력이 아주 높아지면 분자 자신의 부피 때문에 1보다 커진다. '
            + '수소처럼 끌어당김이 약한 기체는 처음부터 올라간다. 곡선은 경향을 보이는 개략도다.',
        svg: svg({ width: W, height: H, title: '실제 기체의 압축인자 Z', desc: 'CH4, N2, H2 의 Z-P 곡선 개략도', body: g.join('') }),
    };
})());

/* 7-5. 상평형 그림 */
add((() => {
    const W = 680, H = 360;
    const g = [];
    const drawPanel = (x0, title, sub, opts) => {
        const bx = x0 + 56, by = 76, bw = 240, bh = 200;
        const out = [panel(x0, 20, 322, 320, title, { sub })];
        out.push(px(bx, by + bh, bx + bw + 12, by + bh, { cls: 's1', marker: 'ar1', width: 1.4 }));
        out.push(px(bx, by + bh, bx, by - 12, { cls: 's1', marker: 'ar1', width: 1.4 }));
        out.push(txt(bx + bw + 16, by + bh + 4, 'T', { cls: 'ink2', size: 'sm' }));
        out.push(txt(bx - 6, by - 18, 'P', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        const [tpx, tpy] = opts.triple, [cpx, cpy] = opts.critical;
        // 승화 곡선, 융해 곡선, 기화 곡선
        out.push(`<path d="M${bx + 6} ${by + bh - 6} Q ${tpx - 26} ${tpy + 16} ${tpx} ${tpy}" fill="none" stroke="var(--s2)" stroke-width="2"/>`);
        out.push(line([[tpx, tpy], [tpx + opts.slope, by + 6]], { stroke: 'var(--s1)', sw: 2 }));
        out.push(`<path d="M${tpx} ${tpy} Q ${(tpx + cpx) / 2 + 10} ${(tpy + cpy) / 2 + 30} ${cpx} ${cpy}" fill="none" stroke="var(--s3)" stroke-width="2"/>`);
        out.push(circ(tpx, tpy, 4, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
        out.push(circ(cpx, cpy, 4, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
        out.push(txt(tpx - 8, tpy + 4, '삼중점', { anchor: 'end', cls: 'ink', size: 'sm' }));
        out.push(txt(cpx + 8, cpy + 4, '임계점', { cls: 'ink', size: 'sm' }));
        out.push(txt(opts.solid[0], opts.solid[1], '고체', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(opts.liquid[0], opts.liquid[1], '액체', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(opts.gas[0], opts.gas[1], '기체', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        // 100 kPa 선
        out.push(line([[bx, opts.p100], [bx + bw, opts.p100]], { stroke: 'var(--ink2)', sw: 1.2, dash: '5 4' }));
        out.push(txt(bx + bw, opts.p100 - 6, '100 kPa', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        for (const [tx, ty, label] of opts.notes) out.push(txt(tx, ty, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(drawPanel(12, '물', '융해 곡선이 왼쪽으로 기운다', {
        triple: [128, 236], critical: [268, 96], slope: -18, p100: 176,
        solid: [98, 140], liquid: [190, 140], gas: [230, 250],
        notes: [[186, 300, '100 kPa 선을 따라 오른쪽으로 가면'], [186, 318, '얼음 → 물 → 수증기']],
    }));
    g.push(drawPanel(346, '이산화탄소', '융해 곡선이 오른쪽으로 기운다', {
        triple: [470, 130], critical: [596, 100], slope: 16, p100: 200,
        solid: [430, 106], liquid: [540, 118], gas: [512, 250],
        notes: [[520, 300, '100 kPa 에서는 삼중점보다 낮아'], [520, 318, '고체가 곧장 기체가 된다 (승화)']],
    }));
    return {
        name: 'chem-phase-diagram',
        title: '물과 이산화탄소의 상평형 그림',
        desc: '온도와 압력을 두 축으로 삼고 어느 상태가 안정한지를 나눈 그림이다. 세 곡선이 만나는 삼중점에서는 '
            + '고체·액체·기체가 함께 있고, 임계점 너머에서는 액체와 기체의 구별이 사라진다. '
            + '물은 융해 곡선이 왼쪽으로 기울어 눌러 주면 얼음이 녹는 드문 물질이고, 이산화탄소는 100 kPa 에서 '
            + '삼중점 아래라 액체를 거치지 않고 곧장 승화한다. 축의 눈금은 실제 비율이 아니다.',
        svg: svg({ width: W, height: H, title: '상평형 그림', desc: '물과 이산화탄소의 상평형 그림 개략도', body: g.join('') }),
    };
})());

/* 7-6. 세 가지 입방 단위세포 */
add((() => {
    const W = 660, H = 300;
    const g = [];
    const cube = (ox, oy, S, dx, dy, extra) => {
        const P = (a, b, c) => [ox + a * S + b * dx, oy - c * S + b * dy];
        const out = [];
        const edge = (p, q) => line([P(...p), P(...q)], { stroke: 'var(--grid)', sw: 1.2 });
        const V = [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1], [0, 1, 0], [1, 1, 0], [1, 1, 1], [0, 1, 1]];
        for (const [i, j] of [[0, 1], [1, 2], [2, 3], [3, 0], [4, 5], [5, 6], [6, 7], [7, 4], [0, 4], [1, 5], [2, 6], [3, 7]]) {
            out.push(edge(V[i], V[j]));
        }
        for (const v of V) {
            const [x, y] = P(...v);
            out.push(circ(x, y, 11, { fill: 'var(--s1)', op: 0.55, stroke: 'var(--s1)', sw: 1.2 }));
        }
        out.push(extra(P));
        return out.join('');
    };
    const panels = [
        ['단순 입방', '꼭짓점만 — 세포당 1개', () => ''],
        ['체심 입방', '가운데 하나 더 — 세포당 2개', P => {
            const [x, y] = P(0.5, 0.5, 0.5);
            return circ(x, y, 12, { fill: 'var(--s2)', op: 0.85, stroke: 'var(--s2)', sw: 1.2 });
        }],
        ['면심 입방', '여섯 면 가운데 — 세포당 4개', P => [[0.5, 0.5, 0], [0.5, 0.5, 1], [0.5, 0, 0.5], [0.5, 1, 0.5], [0, 0.5, 0.5], [1, 0.5, 0.5]]
            .map(v => { const [x, y] = P(...v); return circ(x, y, 12, { fill: 'var(--s3)', op: 0.8, stroke: 'var(--s3)', sw: 1.2 }); }).join('')],
    ];
    panels.forEach(([name, sub, extra], i) => {
        const x = 12 + i * 214;
        g.push(panel(x, 20, 206, 232, name, { sub }));
        g.push(cube(x + 46, 216, 92, 40, -30, extra));
    });
    g.push(txt(20, H - 10, '꼭짓점의 구는 이웃한 세포 여덟 개가 나눠 쓰므로 한 세포 몫은 8분의 1이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-unit-cells',
        title: '세 가지 입방 단위세포',
        desc: '결정은 같은 배치가 삼차원으로 되풀이된 것이고, 그 최소 단위가 단위세포다. '
            + '꼭짓점의 구는 이웃 세포 여덟 개가 나눠 쓰므로 한 세포 몫은 8분의 1이고, 면 위의 구는 두 세포가 '
            + '나눠 쓰므로 절반이다. 그래서 세포당 입자 수가 단순 입방 1개, 체심 입방 2개, 면심 입방 4개가 된다.',
        svg: svg({ width: W, height: H, title: '입방 단위세포 세 가지', desc: '단순·체심·면심 입방 격자', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 8장 — 화학량론
 * ================================================================== */

/* 8-1. 반응식의 균형 맞추기 */
add((() => {
    const W = 680, H = 340;
    const g = [];
    const mol = {
        CH4: (x, y) => atom(x, y, 15, 'C', { fill: 'var(--ink2)', op: 0.25, stroke: 'var(--ink2)', size: 'sm' })
            + [[-20, -14], [20, -14], [-20, 14], [20, 14]].map(([a, b]) => atom(x + a, y + b, 8, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' })).join(''),
        O2: (x, y) => atom(x - 12, y, 12, '', { fill: 'var(--s3)', op: 0.6, stroke: 'var(--s3)' })
            + atom(x + 12, y, 12, '', { fill: 'var(--s3)', op: 0.6, stroke: 'var(--s3)' }),
        CO2: (x, y) => atom(x - 22, y, 12, '', { fill: 'var(--s3)', op: 0.6, stroke: 'var(--s3)' })
            + atom(x, y, 14, 'C', { fill: 'var(--ink2)', op: 0.25, stroke: 'var(--ink2)', size: 'sm' })
            + atom(x + 22, y, 12, '', { fill: 'var(--s3)', op: 0.6, stroke: 'var(--s3)' }),
        H2O: (x, y) => atom(x, y, 12, '', { fill: 'var(--s3)', op: 0.6, stroke: 'var(--s3)' })
            + atom(x - 16, y + 12, 8, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' })
            + atom(x + 16, y + 12, 8, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' }),
    };
    const row = (y, coefs, label) => {
        const out = [txt(20, y - 44, label, { cls: 'ink bold' })];
        out.push(mol.CH4(90, y));
        out.push(txt(130, y + 5, '+', { anchor: 'middle', cls: 'ink' }));
        for (let i = 0; i < coefs[0]; i += 1) out.push(mol.O2(176 + i * 56, y));
        const ax = 176 + coefs[0] * 56 + 6;
        out.push(txt(ax, y + 5, '→', { anchor: 'middle', cls: 'ink' }));
        out.push(mol.CO2(ax + 52, y));
        out.push(txt(ax + 100, y + 5, '+', { anchor: 'middle', cls: 'ink' }));
        for (let i = 0; i < coefs[1]; i += 1) out.push(mol.H2O(ax + 142 + i * 56, y));
        return out.join('');
    };
    g.push(row(96, [1, 1], '맞추기 전 — 개수가 안 맞는다'));
    g.push(txt(W - 14, 82, 'C 1 = 1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 100, 'H 4 ≠ 2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 118, 'O 2 ≠ 3', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(row(236, [2, 2], '맞춘 뒤 — 양쪽 원자 수가 같다'));
    g.push(txt(W - 14, 222, 'C 1 = 1', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 14, 240, 'H 4 = 4', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(W - 14, 258, 'O 4 = 4', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(20, 30, 'CH₄ + O₂ → CO₂ + H₂O 의 계수 맞추기', { cls: 'ink bold' }));
    g.push(txt(20, H - 30, '작은 공 = 수소, 큰 공 = 산소, 회색 = 탄소', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '계수만 고칠 수 있다. 아래첨자를 고치면 다른 물질이 되어 버린다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-balance-atom-count',
        title: '반응식의 계수를 맞춘다는 것',
        desc: '반응 전후로 원자는 사라지지도 새로 생기지도 않는다. 그래서 화살표 양쪽의 원자 개수가 같아야 하고, '
            + '그것을 맞추는 일이 계수 맞추기다. 고칠 수 있는 것은 앞에 붙이는 계수뿐이고 화학식 안의 아래첨자를 '
            + '고치면 아예 다른 물질이 되어 버린다.',
        svg: svg({ width: W, height: H, title: '반응식의 계수 맞추기', desc: '메테인 연소 반응식의 계수를 맞추기 전과 후', body: g.join('') }),
    };
})());

/* 8-2. 한계 반응물 */
add((() => {
    const W = 660, H = 340;
    const g = [];
    const N2 = (x, y) => atom(x - 11, y, 13, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' })
        + atom(x + 11, y, 13, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' });
    const H2 = (x, y) => atom(x - 7, y, 8, '', { fill: 'var(--s2)', op: 0.7, stroke: 'var(--s2)' })
        + atom(x + 7, y, 8, '', { fill: 'var(--s2)', op: 0.7, stroke: 'var(--s2)' });
    const NH3 = (x, y) => atom(x, y, 13, '', { fill: 'var(--s1)', op: 0.6, stroke: 'var(--s1)' })
        + [[-17, 10], [17, 10], [0, -17]].map(([a, b]) => atom(x + a, y + b, 7, '', { fill: 'var(--s2)', op: 0.7, stroke: 'var(--s2)' })).join('');
    g.push(txt(20, 30, 'N₂ + 3H₂ → 2NH₃  —  N₂ 3개와 H₂ 6개를 넣으면?', { cls: 'ink bold' }));
    g.push(txt(20, 76, '넣은 것', { cls: 'ink bold' }));
    for (let i = 0; i < 3; i += 1) g.push(N2(120 + i * 60, 82));
    g.push(txt(320, 87, 'N₂ 3개', { cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 6; i += 1) g.push(H2(120 + i * 34, 126));
    g.push(txt(320, 131, 'H₂ 6개', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 82, 'N₂ 로 따지면 NH₃ 6개까지 가능', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, 126, 'H₂ 로 따지면 NH₃ 4개까지 가능 ←  적은 쪽', { anchor: 'end', cls: 'ink bold' }));
    g.push(line([[20, 158], [W - 20, 158]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(20, 196, '실제로 일어난 것', { cls: 'ink bold' }));
    for (let i = 0; i < 4; i += 1) g.push(NH3(130 + i * 62, 210));
    g.push(txt(400, 215, 'NH₃ 4개가 생긴다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 272, '남은 것', { cls: 'ink bold' }));
    g.push(N2(130, 274));
    g.push(txt(180, 279, 'N₂ 1개는 짝을 못 찾고 남는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '먼저 바닥나는 쪽(여기서는 H₂)이 한계 반응물이고, 생성물의 양은 그쪽이 정한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-limiting-reagent',
        title: '한계 반응물이 생성물의 양을 정한다',
        desc: '질소 3개와 수소 6개를 넣어도 암모니아는 6개가 아니라 4개만 생긴다. 수소가 먼저 바닥나기 때문이다. '
            + '반응물마다 "이것만 다 쓰면 생성물이 몇 개인가"를 따로 계산해서 가장 작은 값을 고르면 되고, '
            + '그 값을 낸 반응물이 한계 반응물이다. 나머지는 남는다.',
        svg: svg({ width: W, height: H, title: '한계 반응물', desc: '암모니아 합성에서 수소가 먼저 바닥나는 그림', body: g.join('') }),
    };
})());

/* 8-3. 희석 */
add((() => {
    const W = 620, H = 300;
    const g = [];
    const beaker = (x, y, w, h, fill, dots, label, sub) => {
        const out = [box(x, y, w, h, { stroke: 'var(--ink2)', sw: 1.8, rx: 4 })];
        out.push(box(x + 2, y + h - fill, w - 4, fill - 2, { fill: 'var(--s1)', op: 0.16, stroke: 'none', sw: 0, rx: 2 }));
        for (const [a, b] of dots) out.push(circ(x + a, y + h - b, 4.5, { fill: 'var(--s2)', stroke: 'none', sw: 0 }));
        out.push(txt(x + w / 2, y + h + 24, label, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(x + w / 2, y + h + 42, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(beaker(60, 90, 90, 140, 46, [[22, 12], [46, 30], [68, 14], [30, 36], [56, 8], [70, 34]],
        '진한 용액 조금', '용질 6알, 부피 작다'));
    g.push(px(190, 180, 250, 180, { cls: 's3', marker: 'ar3', width: 2.4 }));
    g.push(txt(220, 164, '물을 붓는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(beaker(290, 90, 150, 140, 118, [[26, 26], [64, 90], [118, 40], [42, 70], [96, 16], [130, 100]],
        '묽은 용액', '용질은 그대로 6알, 부피만 커졌다'));
    g.push(txt(480, 116, '용질의 몰수는 변하지 않는다', { cls: 'ink bold' }));
    g.push(txt(480, 144, 'C₁V₁ = C₂V₂', { cls: 'ink bold' }));
    g.push(txt(480, 172, '양쪽 다 "용질의 몰수"를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(480, 190, '적은 것일 뿐이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(480, 220, '부피가 3배가 되면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(480, 238, '농도는 3분의 1이 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 34, '희석 — 물을 부어도 녹아 있는 알갱이 수는 그대로다', { cls: 'ink bold' }));
    return {
        name: 'chem-dilution',
        title: '희석에서 변하지 않는 것',
        desc: '용액에 물을 부으면 부피가 늘고 농도가 낮아지지만, 녹아 있는 용질의 몰수는 그대로다. '
            + '희석 공식 C₁V₁ = C₂V₂ 는 양변이 모두 "용질의 몰수"라는 같은 값을 다르게 적은 것이다. '
            + '부피가 세 배가 되면 농도는 삼분의 일이 된다.',
        svg: svg({ width: W, height: H, title: '희석', desc: '물을 부어도 용질 알갱이 수는 변하지 않는다', body: g.join('') }),
    };
})());

export default figures;
