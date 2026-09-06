/**
 * 일반화학 13장(산과 염기) ~ 17장(핵화학)의 그림.
 *
 * chemistry-basic.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를
 * 돌려주고 name 이 파일 이름(/figures/chemistry/<name>.svg)이 된다.
 * 앞 장의 그림과 이름이 겹치지 않게 chem-acid- / chem-echem- / chem-kinetics- /
 * chem-org- / chem-nuc- 접두어를 쓴다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 화학식은 유니코드 아래첨자(H₂O)와 위첨자(Na⁺)로 적고, 유니코드에 없는 아래첨자는
 * lib 의 esc 규칙 `K~a`, `t~{1/2}` 로 적는다.
 */
import { svg, frame, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const log10 = v => Math.log10(v);

/* ------------------------------------------------------------------ *
 * 공통 소도구 (chemistry-basic.mjs 와 같은 도구를 다시 정의한다.
 * 그쪽 모듈은 그림 배열만 내보내므로 가져다 쓸 수 없다.)
 * ------------------------------------------------------------------ */

const SUP = {
    '-': '⁻', '+': '⁺', '=': '⁼', '(': '⁽', ')': '⁾', '.': '·',
    0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹',
};
const sup = s => String(s).split('').map(c => SUP[c] ?? c).join('');
const pow10 = e => `10${sup(e)}`;

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.14, stroke = 'none', sw = 1 } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 원자/이온 하나. 원 + 가운데 기호. */
function atom(cx, cy, r, label, { fill = 'var(--s1)', op = 0.22, stroke = 'var(--s1)', sw = 1.4, size = 'sm' } = {}) {
    return circ(cx, cy, r, { fill, op, stroke, sw })
        + txt(cx, cy + (size === 'sm' ? 4 : 5), label, { anchor: 'middle', cls: 'ink', size });
}

/** 두 점을 잇는 결합선. n=1,2,3 이면 단일·이중·삼중. */
function bond(p1, p2, n = 1, { r1 = 0, r2r = 0, stroke = 'var(--ink2)', sw = 1.7, gap = 4 } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    const a = [p1[0] + ux * r1, p1[1] + uy * r1];
    const b = [p2[0] - ux * r2r, p2[1] - uy * r2r];
    const nx = -uy, ny = ux;
    const offs = n === 1 ? [0] : n === 2 ? [-gap / 2, gap / 2] : [-gap, 0, gap];
    return offs.map(o => line(
        [[a[0] + nx * o, a[1] + ny * o], [b[0] + nx * o, b[1] + ny * o]],
        { stroke, sw, cap: 'butt' },
    )).join('');
}

/** 패널 제목 + 테두리. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 비커 하나. 액체 높이 fill(화소). */
function beaker(x, y, w, h, fillH, { cls = 's1', op = 0.12 } = {}) {
    return box(x + 2, y + h - fillH, w - 4, fillH - 2, { fill: `var(--${cls})`, op, stroke: 'none', sw: 0, rx: 2 })
        + box(x, y, w, h, { stroke: 'var(--ink2)', sw: 1.8, rx: 4 });
}

/* ================================================================== *
 * 13장 — 산과 염기
 * ================================================================== */

/* 13-1. 왜 로그를 쓰는가 — 10의 거듭제곱과 로그 값 */
add((() => {
    const W = 700, H = 430;
    const x0 = 96, x1 = 640;
    const g = [];

    // 위쪽: 있는 그대로의 농도 축
    const yA = 122;
    const XA = v => x0 + (v / 0.1) * (x1 - x0);
    g.push(txt(24, 40, '농도를 있는 그대로 자 위에 찍으면', { cls: 'ink bold' }));
    g.push(line([[x0, yA], [x1 + 12, yA]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (const v of [0, 0.02, 0.04, 0.06, 0.08, 0.1]) {
        g.push(line([[XA(v), yA], [XA(v), yA + 8]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(XA(v), yA + 24, v.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x1 + 20, yA + 5, 'mol/L', { cls: 'ink2', size: 'sm' }));
    for (let e = 1; e <= 6; e += 1) {
        g.push(circ(XA(10 ** -e), yA, 4, { fill: 'var(--s1)', stroke: 'none', sw: 0 }));
    }
    g.push(line([[XA(0.1), yA - 6], [XA(0.1), yA - 26]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(txt(XA(0.1), yA - 32, `${pow10(-1)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(line([[XA(0.01), yA - 6], [XA(0.01), yA - 26]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(txt(XA(0.01), yA - 32, `${pow10(-2)}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(px(300, 72, 130, yA - 12, { cls: 's2', marker: 'ar2', width: 1.8 }));
    g.push(txt(306, 68, `${pow10(-3)} 부터 아래는 전부 0 자리에 겹쳐 버린다`, { cls: 'ink2', size: 'sm' }));

    // 아래쪽: 지수(로그) 축
    const yB = 300;
    const XB = e => x0 + ((-e - 1) / 13) * (x1 - x0);
    g.push(txt(24, 208, '지수만 세어 늘어놓으면 고르게 퍼진다 — 이것이 로그다', { cls: 'ink bold' }));
    g.push(line([[x0 - 12, yB], [x1 + 12, yB]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (let e = -1; e >= -14; e -= 1) {
        const x = XB(e);
        g.push(line([[x, yB - 6], [x, yB + 6]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(circ(x, yB, 4, { fill: 'var(--s1)', stroke: 'none', sw: 0 }));
        if (e % 2 === -1 || e === -14) {
            g.push(txt(x, yB - 16, pow10(e), { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        g.push(txt(x, yB + 26, String(e), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x, yB + 48, String(-e), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 - 22, yB + 26, '로그', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 - 22, yB + 48, 'pH', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(x0 - 22, yB - 16, '농도', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(box(96, 356, 540, 56, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
    g.push(txt(366, 378, `${pow10(-3)} 은 10을 세 번 나눈 값이다. 그래서 로그는 −3 이고, 부호를 뒤집은 3 이 pH 다`,
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(366, 398, '농도가 10배 묽어질 때마다 pH 는 정확히 1 씩 커진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-log-scale',
        title: '로그를 쓰는 이유와 pH 의 정의',
        desc: '수소 이온 농도는 10⁻¹ 에서 10⁻¹⁴ mol/L 까지 걸쳐 있다. 있는 그대로 자 위에 찍으면 10⁻³ 아래가 모두 '
            + '0 자리에 겹쳐 구별되지 않는다. 10을 몇 번 곱했는지(=지수)만 세면 값이 고르게 퍼지고, 그 지수가 로그다. '
            + '지수의 부호를 뒤집은 것이 pH 이며, 농도가 10배 묽어질 때마다 pH 는 1 씩 커진다.',
        svg: svg({ width: W, height: H, title: '로그 눈금과 pH', desc: '농도 축과 로그 축을 위아래로 견준 그림', body: g.join('') }),
    };
})());

/* 13-2. pH 척도 */
add((() => {
    const W = 720, H = 330;
    const x0 = 118, x1 = 636, yb = 190, hb = 44;
    const X = p => x0 + (p / 14) * (x1 - x0);
    const grad = '<defs><linearGradient id="phg" x1="0" y1="0" x2="1" y2="0">'
        + '<stop offset="0" stop-color="#d0342c"/><stop offset="0.22" stop-color="#e88b2a"/>'
        + '<stop offset="0.42" stop-color="#d8c93a"/><stop offset="0.5" stop-color="#4faa62"/>'
        + '<stop offset="0.62" stop-color="#3aa8a0"/><stop offset="0.82" stop-color="#2a78d6"/>'
        + '<stop offset="1" stop-color="#5b3fb5"/></linearGradient></defs>';
    const g = [grad];
    g.push(`<rect x="${x0}" y="${yb}" width="${x1 - x0}" height="${hb}" rx="4" fill="url(#phg)" fill-opacity="0.6"/>`);
    g.push(box(x0, yb, x1 - x0, hb, { stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    for (let p = 0; p <= 14; p += 1) {
        g.push(line([[X(p), yb + hb], [X(p), yb + hb + 7]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(p), yb + hb + 22, String(p), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 - 22, yb + hb + 22, 'pH', { anchor: 'end', cls: 'ink bold' }));
    for (let p = 0; p <= 14; p += 2) {
        g.push(txt(X(p), yb + hb + 46, pow10(-p), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 - 22, yb + hb + 46, '[H₃O⁺] (mol/L)', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const items = [
        [1.5, '위액', 0], [2.4, '레몬즙', 1], [5.0, '커피', 0],
        [7.0, '순수한 물', 1], [8.1, '바닷물', 0], [11.5, '암모니아수', 1], [12.5, '표백제', 0],
    ];
    for (const [p, name, lv] of items) {
        const yy = 80 + lv * 30;
        g.push(line([[X(p), yb - 4], [X(p), yy + 6]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(circ(X(p), yb - 4, 3.4, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
        g.push(txt(X(p), yy, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    g.push(line([[X(7), yb - 30], [X(7), yb + hb + 58]], { stroke: 'var(--ink)', sw: 1.4, dash: '6 4' }));
    g.push(txt(X(7) - 8, yb + 26, '중성', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(X(3.2), yb + 26, '산성 ←', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(X(11), yb + 26, '→ 염기성', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(24, 40, 'pH 1 차이는 농도 10배 차이다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 12, '위액(pH 1.5)과 순수한 물(pH 7.0)의 H₃O⁺ 농도 차이는 약 30만 배다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-ph-scale',
        title: 'pH 척도와 일상 물질의 대략적인 위치',
        desc: 'pH 는 0에서 14 사이에 몰려 있지만 눈금 한 칸이 농도 10배에 해당한다. 위액과 순수한 물은 pH 로 '
            + '5.5 차이인데 수소 이온 농도로는 30만 배 차이다. 표시한 물질의 pH 는 시료마다 달라지는 대략값이다.',
        svg: svg({ width: W, height: H, title: 'pH 척도', desc: 'pH 0 부터 14 까지의 축 위에 일상 물질을 배치한 그림', body: g.join('') }),
    };
})());

/* 13-3. 강산과 약산의 이온화 비교 */
add((() => {
    const W = 680, H = 394;
    const g = [];
    const bx = [50, 380], by = 84, bw = 250, bh = 210;
    const pair = (x, y, neg = 'A⁻') => atom(x, y, 13, 'H⁺', { fill: 'var(--s2)', stroke: 'var(--s2)' })
        + atom(x + 34, y, 15, neg, { fill: 'var(--s1)', stroke: 'var(--s1)' });
    const whole = (x, y) => `<g>${circ(x + 17, y, 26, { fill: 'var(--s3)', op: 0.18, stroke: 'var(--s3)', sw: 1.4 })}`
        + `${txt(x + 17, y + 4, 'HA', { anchor: 'middle', cls: 'ink', size: 'sm' })}</g>`;

    // 강산
    g.push(beaker(bx[0], by, bw, bh, 170, { cls: 's2', op: 0.07 }));
    g.push(txt(bx[0] + bw / 2, by - 30, '강산 HCl', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(bx[0] + bw / 2, by - 12, '0.10 mol/L', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gridA = [[26, 164], [110, 166], [190, 162], [26, 214], [110, 216], [190, 212], [64, 264], [148, 262]];
    for (const [dx, dy] of gridA) g.push(pair(bx[0] + dx, dy, 'Cl⁻'));
    g.push(txt(bx[0] + bw / 2, by + bh + 28, '전부 갈라졌다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(bx[0] + bw / 2, by + bh + 48, '[H₃O⁺] = 0.10 mol/L → pH 1.00', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 약산
    g.push(beaker(bx[1], by, bw, bh, 170, { cls: 's3', op: 0.07 }));
    g.push(txt(bx[1] + bw / 2, by - 30, '약산 CH₃COOH', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(bx[1] + bw / 2, by - 12, '0.10 mol/L', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gridB = [[24, 162], [90, 166], [156, 160], [24, 214], [90, 218], [156, 212], [58, 264], [124, 260]];
    for (const [dx, dy] of gridB) g.push(whole(bx[1] + dx, dy));
    g.push(pair(bx[1] + 190, 262));
    g.push(txt(bx[1] + bw / 2, by + bh + 28, '거의 그대로 있고 아주 일부만 갈라진다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(bx[1] + bw / 2, by + bh + 48, '[H₃O⁺] ≈ 0.0013 mol/L → pH 2.87', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(24, 28, '같은 농도인데 pH 가 다른 이유', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '"강하다·약하다"는 진하기가 아니라 얼마나 갈라지느냐다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-strong-weak',
        title: '강산과 약산 — 같은 농도, 다른 pH',
        desc: '같은 0.10 mol/L 라도 강산은 거의 전부 이온으로 갈라지고 약산은 대부분 분자 그대로 남는다. '
            + '그래서 수소 이온 농도가 약 100배 차이 나고 pH 가 약 2 차이 난다. '
            + '강약은 용액이 진한지 묽은지가 아니라 얼마나 갈라지느냐를 말한다.',
        svg: svg({ width: W, height: H, title: '강산과 약산의 이온화', desc: '두 비커에 든 강산과 약산의 알갱이 그림', body: g.join('') }),
    };
})());

/* 13-4. 완충 용액이 작동하는 방식 */
add((() => {
    const W = 720, H = 430;
    const g = [];
    const cx = 360;
    g.push(box(cx - 110, 96, 220, 86, { fill: 'var(--s1)', op: 0.1, stroke: 'var(--s1)', sw: 1.6, rx: 8 }));
    g.push(txt(cx, 124, '완충 용액', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(cx, 146, 'HA 도 많이,  A⁻ 도 많이', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(cx, 168, '두 창고가 함께 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(px(220, 139, cx - 118, 139, { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(210, 132, '산을 넣으면', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(210, 152, 'H₃O⁺', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(px(500, 139, cx + 118, 139, { cls: 's3', marker: 'ar3', width: 2.2 }));
    g.push(txt(510, 132, '염기를 넣으면', { cls: 'ink bold' }));
    g.push(txt(510, 152, 'OH⁻', { cls: 'ink2', size: 'sm' }));

    g.push(box(40, 208, 300, 62, { stroke: 'var(--s2)', sw: 1.4, rx: 6 }));
    g.push(txt(190, 232, 'A⁻ + H₃O⁺ → HA + H₂O', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(190, 254, 'A⁻ 창고가 받아낸다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(box(380, 208, 300, 62, { stroke: 'var(--s3)', sw: 1.4, rx: 6 }));
    g.push(txt(530, 232, 'HA + OH⁻ → A⁻ + H₂O', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(530, 254, 'HA 창고가 받아낸다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 아래 미니 그래프
    const f = frame({ xRange: [0, 10], yRange: [0, 14], box: { x: 96, y: 296, w: 280, h: 92 } });
    g.push(f.axes({ xLabel: 'mL', yLabel: 'pH', xTicks: [0, 5, 10], yTicks: [0, 7, 14] }));
    g.push(f.line([[0, 4.74], [2, 4.6], [4, 4.42], [6, 4.2], [8, 3.9], [10, 3.5]], { cls: 's1' }));
    g.push(f.line([[0, 7], [0.3, 4], [1, 2.6], [4, 2.1], [10, 1.7]], { cls: 's2', dash: '6 4' }));
    g.push(txt(200, 296, '강산을 조금씩 넣었을 때', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(424, 316, '완충 용액 — 거의 평평하다', { cls: 'ink bold' }));
    g.push(txt(424, 338, '순수한 물(점선) — 곧바로 떨어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(424, 368, '완충 능력에는 한계가 있다. 한쪽 창고가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(424, 386, '바닥나면 그때부터 pH 가 무너진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 40, '완충 용액은 산을 받아낼 창고와 염기를 받아낼 창고를 함께 가진다', { cls: 'ink bold' }));
    return {
        name: 'chem-acid-buffer-action',
        title: '완충 용액이 pH 를 붙잡는 방식',
        desc: '완충 용액에는 약산 HA 와 그 짝염기 A⁻ 가 둘 다 많이 들어 있다. 산을 넣으면 A⁻ 가 받아 HA 가 되고, '
            + '염기를 넣으면 HA 가 받아 A⁻ 가 된다. 어느 쪽을 넣어도 자유로운 H₃O⁺ 가 거의 늘지 않으므로 pH 가 '
            + '거의 변하지 않는다. 다만 한쪽 창고가 바닥나면 완충 능력은 사라진다.',
        svg: svg({ width: W, height: H, title: '완충 용액의 작동', desc: '두 창고 도식과 pH 변화 비교 그래프', body: g.join('') }),
    };
})());

/* ---- 적정 곡선 세 종류 ---- */

function titrationBase(W, H, xMax, xTicks) {
    return frame({ xRange: [0, xMax], yRange: [0, 14], box: { x: 74, y: 54, w: W - 210, h: H - 132 } });
}

/* 13-5. 강산-강염기 적정 */
add((() => {
    const W = 640, H = 380;
    const f = titrationBase(W, H, 50, [0, 10, 20, 30, 40, 50]);
    const pH = V => {
        if (V < 25) return -log10((2.5 - 0.1 * V) / (25 + V));
        if (V > 25) return 14 + log10((0.1 * V - 2.5) / (25 + V));
        return 7;
    };
    const vs = [0, 2, 5, 10, 15, 20, 22, 23, 24, 24.5, 24.8, 24.9, 25, 25.1, 25.2, 25.5, 26, 27, 28, 30, 35, 40, 45, 50];
    const g = [
        f.axes({ xLabel: '넣은 NaOH (mL)', yLabel: 'pH', xTicks: [0, 10, 20, 30, 40, 50], yTicks: [0, 2, 4, 6, 8, 10, 12, 14] }),
        f.line(vs.map(v => [v, pH(v)]), { cls: 's1' }),
        f.guide([25, 0], [25, 14]),
        f.guide([0, 7], [50, 7]),
        f.dot([25, 7], { cls: 'f2', r: 5 }),
    ];
    g.push(txt(f.X(25) + 10, f.Y(7) - 10, '당량점  pH 7.00', { cls: 'ink bold' }));
    g.push(txt(f.X(25) + 10, f.Y(7) + 8, '넣은 NaOH 몰수 = 처음 HCl 몰수', { cls: 'ink2', size: 'sm' }));
    g.push(px(f.X(29), f.Y(4.5), f.X(25.6), f.Y(6), { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(txt(f.X(29) + 4, f.Y(4.5) + 4, '1 mL 사이에 pH 가 4 넘게 뛴다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(2), f.Y(1) - 12, '시작 pH 1.00', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '0.100 mol/L HCl 25.00 mL 를 0.100 mol/L NaOH 로 적정', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '당량점 앞뒤가 거의 수직이라, 한 방울 차이로 색이 변해도 오차가 작다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-titration-strong',
        title: '강산을 강염기로 적정한 곡선',
        desc: '강산-강염기 적정에서는 당량점의 pH 가 정확히 7 이다. 생성된 염(NaCl)이 물과 반응하지 않기 때문이다. '
            + '당량점 앞뒤 1 mL 사이에 pH 가 4 이상 뛰므로 지시약 한 방울의 오차가 거의 문제되지 않는다.',
        svg: svg({ width: W, height: H, title: '강산-강염기 적정 곡선', desc: '당량점 pH 7 에서 수직으로 뛰는 곡선', body: g.join('') }),
    };
})());

/* 13-6. 약산-강염기 적정 */
add((() => {
    const W = 640, H = 380;
    const f = titrationBase(W, H, 50, [0, 10, 20, 30, 40, 50]);
    const pKa = 4.74;
    const pts = [[0, 2.87]];
    for (const v of [0.5, 1, 2, 4, 6, 8, 10, 12.5, 15, 18, 20, 22, 23, 24, 24.5, 24.75]) {
        pts.push([v, pKa + log10(v / (25 - v))]);
    }
    pts.push([25, 8.72]);
    for (const v of [25.25, 25.5, 26, 27, 28, 30, 35, 40, 45, 50]) {
        pts.push([v, 14 + log10((0.1 * v - 2.5) / (25 + v))]);
    }
    const g = [
        f.axes({ xLabel: '넣은 NaOH (mL)', yLabel: 'pH', xTicks: [0, 10, 20, 30, 40, 50], yTicks: [0, 2, 4, 6, 8, 10, 12, 14] }),
        poly([[f.X(2), f.Y(3.2)], [f.X(23), f.Y(3.2)], [f.X(23), f.Y(6.6)], [f.X(2), f.Y(6.6)]],
            { fill: 'var(--s3)', op: 0.12 }),
        f.line(pts, { cls: 's1' }),
        f.guide([25, 0], [25, 14]),
        f.guide([12.5, 0], [12.5, pKa]),
        f.guide([0, pKa], [12.5, pKa]),
        f.dot([12.5, pKa], { cls: 'f3', r: 5 }),
        f.dot([25, 8.72], { cls: 'f2', r: 5 }),
    ];
    g.push(txt(f.X(12.5) + 8, f.Y(pKa) + 22, '반당량점', { cls: 'ink bold' }));
    g.push(txt(f.X(12.5) + 8, f.Y(pKa) + 40, 'pH = pK~a = 4.74', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(25) + 10, f.Y(8.72) - 8, '당량점  pH 8.72', { cls: 'ink bold' }));
    g.push(txt(f.X(25) + 10, f.Y(8.72) + 10, '7 보다 크다 — 남은 A⁻ 가 염기다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(12.5), f.Y(6.9), '완충 구간 — 기울기가 완만하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0.6), f.Y(2.87) - 12, '시작 pH 2.87', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '0.100 mol/L CH₃COOH 25.00 mL 를 0.100 mol/L NaOH 로 적정', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, 'K~a = 1.8 × 10⁻⁵ 로 계산한 곡선. 반당량점 pH 를 읽으면 pK~a 를 바로 얻는다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-titration-weak',
        title: '약산을 강염기로 적정한 곡선',
        desc: '약산 적정 곡선은 강산과 세 곳이 다르다. 시작 pH 가 더 높고, 가운데에 기울기가 완만한 완충 구간이 있으며, '
            + '당량점 pH 가 7 보다 크다. 넣은 염기가 처음 산의 절반인 반당량점에서는 pH 가 정확히 pKa 와 같아지므로, '
            + '이 곡선의 중간 지점을 읽는 것만으로 Ka 를 구할 수 있다.',
        svg: svg({ width: W, height: H, title: '약산-강염기 적정 곡선', desc: '완충 구간과 반당량점이 표시된 곡선', body: g.join('') }),
    };
})());

/* 13-7. 이양성자산 적정 */
add((() => {
    const W = 640, H = 380;
    const f = titrationBase(W, H, 75, [0, 25, 50, 75]);
    const pts = [[0, 2.0]];
    for (const v of [1, 2, 5, 8, 12.5, 17, 20, 22, 24]) pts.push([v, 3 + log10(v / (25 - v))]);
    pts.push([25, 5.0]);
    for (const v of [26, 28, 32, 37.5, 43, 46, 48, 49]) pts.push([v, 7 + log10((v - 25) / (50 - v))]);
    pts.push([50, 9.76]);
    for (const v of [51, 52, 55, 60, 65, 70, 75]) pts.push([v, 14 + log10((0.1 * v - 5.0) / (25 + v))]);
    const g = [
        f.axes({ xLabel: '넣은 NaOH (mL)', yLabel: 'pH', xTicks: [0, 25, 50, 75], yTicks: [0, 2, 4, 6, 8, 10, 12, 14] }),
        f.line(pts, { cls: 's1' }),
        f.guide([25, 0], [25, 14]),
        f.guide([50, 0], [50, 14]),
        f.dot([12.5, 3], { cls: 'f3', r: 4.5 }),
        f.dot([37.5, 7], { cls: 'f3', r: 4.5 }),
        f.dot([25, 5], { cls: 'f2', r: 5 }),
        f.dot([50, 9.76], { cls: 'f2', r: 5 }),
    ];
    g.push(txt(f.X(12.5), f.Y(3) - 12, 'pH = pK~{a1} = 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(37.5), f.Y(7) - 12, 'pH = pK~{a2} = 7', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(25) + 6, f.Y(5) + 4, '1단계 당량점', { cls: 'ink bold' }));
    g.push(txt(f.X(50) + 6, f.Y(9.76) + 4, '2단계 당량점', { cls: 'ink bold' }));
    g.push(txt(f.X(2), f.Y(11.5), 'H₂A → HA⁻', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(27), f.Y(1.4), 'HA⁻ → A²⁻', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '양성자를 둘 내놓는 산은 계단이 둘 생긴다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '두 당량점의 부피가 정확히 2배 관계인 것이 다양성자산의 표시다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-titration-diprotic',
        title: '이양성자산의 적정 곡선',
        desc: '양성자를 둘 내놓는 산을 적정하면 계단이 둘 생긴다. 두 번째 당량점의 부피는 첫 번째의 정확히 두 배다. '
            + '두 계단의 중간(반당량점)에서 pH 가 각각 pKa1, pKa2 와 같아진다. '
            + '그림은 pKa1 = 3, pKa2 = 7 인 가상의 산으로 그린 것이다.',
        svg: svg({ width: W, height: H, title: '이양성자산 적정 곡선', desc: '두 개의 계단이 나타나는 적정 곡선', body: g.join('') }),
    };
})());

/* 13-8. 지시약의 변색 범위 */
add((() => {
    const W = 700, H = 340;
    const x0 = 150, x1 = 650, yb = 250;
    const X = p => x0 + (p / 14) * (x1 - x0);
    const g = [];
    g.push(line([[x0, yb], [x1, yb]], { stroke: 'var(--ink2)', sw: 1.5 }));
    for (let p = 0; p <= 14; p += 1) {
        g.push(line([[X(p), yb], [X(p), yb + 7]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(p), yb + 22, String(p), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(X(7), yb + 44, 'pH', { anchor: 'middle', cls: 'ink bold' }));
    const inds = [
        [3.1, 4.4, '메틸오렌지', '빨강 → 노랑', 's2', 76],
        [6.0, 7.6, '브로모티몰블루', '노랑 → 파랑', 's3', 130],
        [8.3, 10.0, '페놀프탈레인', '무색 → 붉은색', 's1', 184],
    ];
    for (const [a, b, name, chg, cls, y] of inds) {
        g.push(box(X(a), y - 15, X(b) - X(a), 30, { fill: `var(--${cls})`, op: 0.3, stroke: `var(--${cls})`, sw: 1.5, rx: 4 }));
        g.push(txt(x0 - 12, y - 2, name, { anchor: 'end', cls: 'ink bold' }));
        g.push(txt(x0 - 12, y + 16, chg, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt((X(a) + X(b)) / 2, y + 5, `${a} – ${b}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(line([[X(a), y + 16], [X(a), yb - 4]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(line([[X(b), y + 16], [X(b), yb - 4]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    }
    for (const [p, name, ly] of [[7.0, '강산-강염기 당량점 7.0', 40], [8.7, '약산-강염기 당량점 8.7', 58]]) {
        g.push(line([[X(p), ly + 4], [X(p), yb]], { stroke: 'var(--ink)', sw: 1.3, dash: '6 4' }));
        g.push(txt(X(p) + 8, ly, name, { cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(20, 28, '지시약은 자기 변색 범위 안에서만 색이 변한다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '고르는 기준: 변색 범위가 그 적정의 당량점을 품어야 한다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-acid-indicators',
        title: '대표 지시약의 변색 범위와 당량점',
        desc: '지시약은 저마다 정해진 pH 구간에서만 색이 변한다. 적정에 쓸 지시약은 그 적정의 당량점 pH 를 '
            + '자기 변색 범위 안에 품는 것으로 고른다. 강산-강염기(당량점 7)에는 브로모티몰블루가, '
            + '약산-강염기(당량점 8 에서 9)에는 페놀프탈레인이 맞는다. 표시한 범위는 흔히 인용되는 대략값이다.',
        svg: svg({ width: W, height: H, title: '지시약 변색 범위', desc: 'pH 축 위에 세 지시약의 변색 구간을 그린 막대', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 14장 — 전기화학
 * ================================================================== */

/* 14-1. 갈바니 전지의 구조와 염다리 */
add((() => {
    const W = 740, H = 510;
    const g = [];
    const bL = { x: 90, y: 200, w: 190, h: 180 };
    const bR = { x: 450, y: 200, w: 190, h: 180 };
    g.push(beaker(bL.x, bL.y, bL.w, bL.h, 142, { cls: 's1', op: 0.1 }));
    g.push(beaker(bR.x, bR.y, bR.w, bR.h, 142, { cls: 's2', op: 0.1 }));

    // 전극
    g.push(box(130, 150, 18, 200, { fill: 'var(--ink2)', op: 0.35, stroke: 'var(--ink2)', sw: 1.4, rx: 2 }));
    g.push(box(582, 150, 18, 200, { fill: 'var(--s2)', op: 0.4, stroke: 'var(--s2)', sw: 1.4, rx: 2 }));

    // 도선과 전압계
    g.push(line([[139, 150], [139, 100], [347, 100]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
    g.push(line([[383, 100], [591, 100], [591, 150]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
    g.push(circ(365, 100, 18, { fill: 'none', stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(txt(365, 105, 'V', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(365, 70, '1.10 V', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(210, 100, 300, 100, { cls: 's1', marker: 'ar1', width: 2.4 }));
    g.push(txt(255, 90, 'e⁻ 가 흐른다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(px(520, 100, 440, 100, { cls: 's1', marker: 'ar1', width: 2.4 }));

    // 염다리
    g.push(line([[240, 300], [240, 174], [490, 174], [490, 300]],
        { stroke: 'var(--s3)', sw: 15, cap: 'butt' }));
    g.push(line([[240, 300], [240, 174], [490, 174], [490, 300]],
        { stroke: 'var(--ink2)', sw: 1.2, cap: 'butt' }));
    g.push(txt(365, 166, '염다리 (KNO₃ 용액)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(330, 196, 262, 196, { cls: 's2', marker: 'ar2', width: 1.8 }));
    g.push(txt(338, 200, 'NO₃⁻', { cls: 'ink2', size: 'sm' }));
    g.push(px(400, 196, 468, 196, { cls: 's2', marker: 'ar2', width: 1.8 }));
    g.push(txt(392, 200, 'K⁺', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    // 라벨
    g.push(txt(139, 138, '아연 Zn', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(591, 138, '구리 Cu', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(96, 396, '산화전극 (−)', { cls: 'ink bold' }));
    g.push(txt(96, 416, 'Zn → Zn²⁺ + 2e⁻', { cls: 'ink' }));
    g.push(txt(96, 436, '전극이 녹아 가늘어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(634, 396, '환원전극 (+)', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(634, 416, 'Cu²⁺ + 2e⁻ → Cu', { anchor: 'end', cls: 'ink' }));
    g.push(txt(634, 436, '전극에 구리가 쌓인다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(185, 330, 'Zn²⁺ 용액', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(545, 330, 'Cu²⁺ 용액', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 34, '갈바니 전지 — 전자가 도선으로 돌아가게 만든 장치', { cls: 'ink bold' }));
    g.push(txt(365, 476, '염다리가 없으면 왼쪽에 양전하가, 오른쪽에 음전하가 쌓여 몇 초 만에 전류가 멈춘다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(365, 496, '염다리 속 이온이 반대로 옮겨가 그 전하를 중화해 주기 때문에 전류가 계속 흐른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-echem-galvanic-cell',
        title: '갈바니 전지의 구조',
        desc: '아연이 산화되며 내놓은 전자가 도선을 지나 구리 쪽으로 흐른다. 산화가 일어나는 전극이 산화전극이고 '
            + '갈바니 전지에서는 음극이다. 염다리는 두 용액을 이어 전하가 한쪽에 쌓이는 것을 막는다. '
            + '염다리를 빼면 몇 초 만에 전류가 끊긴다.',
        svg: svg({ width: W, height: H, title: '갈바니 전지', desc: '아연-구리 전지의 전극·염다리·전자 흐름', body: g.join('') }),
    };
})());

/* 14-2. 표준 수소 전극 */
add((() => {
    const W = 660, H = 400;
    const g = [];
    g.push(beaker(120, 140, 210, 200, 162, { cls: 's1', op: 0.1 }));
    // 유리관
    g.push(box(190, 58, 46, 226, { stroke: 'var(--ink2)', sw: 1.6, rx: 4 }));
    g.push(px(213, 26, 213, 54, { cls: 's3', marker: 'ar3', width: 2.2 }));
    g.push(txt(228, 34, 'H₂ 기체 100 kPa', { cls: 'ink', size: 'sm' }));
    // 백금 전극
    g.push(box(204, 116, 18, 150, { fill: 'var(--ink2)', op: 0.45, stroke: 'var(--ink2)', sw: 1.3, rx: 2 }));
    g.push(line([[213, 116], [213, 96], [372, 96]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
    g.push(txt(380, 100, '측정하려는 반쪽 전지로', { cls: 'ink2', size: 'sm' }));
    // 거품
    for (const [x, y, r] of [[198, 248, 4], [228, 230, 5], [202, 206, 3.5], [230, 188, 4.5], [196, 170, 4]]) {
        g.push(circ(x, y, r, { fill: 'none', stroke: 'var(--s3)', sw: 1.4 }));
    }
    g.push(txt(155, 300, 'H⁺ 1 mol/L', { anchor: 'middle', cls: 'ink' }));
    g.push(line([[224, 200], [372, 200]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
    g.push(txt(380, 196, '백금 전극', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(380, 214, '반응하지 않고 전자만 주고받는다', { cls: 'ink2', size: 'sm' }));
    g.push(box(388, 250, 250, 62, { stroke: 'var(--s2)', sw: 1.6, rx: 6 }));
    g.push(txt(513, 276, 'E° ≡ 0 V', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(513, 296, '재어서 얻은 값이 아니라 약속한 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 30, '표준 수소 전극', { cls: 'ink bold' }));
    g.push(txt(20, H - 30, '2H⁺(1 mol/L) + 2e⁻ ⇌ H₂(100 kPa) 의 전위를 0 으로 약속한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '전극 하나의 절대 전위는 잴 수 없다. 잴 수 있는 것은 언제나 두 전극의 차이뿐이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-echem-she',
        title: '표준 수소 전극',
        desc: '전극 하나의 전위는 절대값으로 잴 수 없고 두 전극의 차이만 잴 수 있다. 그래서 수소 전극 하나를 골라 '
            + '0 V 로 약속하고, 나머지 전위는 모두 이 기준과 견준 상대값으로 적는다. 백금은 반응에 참여하지 않고 '
            + '전자를 주고받는 자리만 내준다.',
        svg: svg({ width: W, height: H, title: '표준 수소 전극', desc: '수소 기체를 흘리는 백금 전극과 1 mol/L 산 용액', body: g.join('') }),
    };
})());

/* 14-3. 표준 환원전위 사다리 */
add((() => {
    const W = 660, H = 520;
    const yTop = 70, yBot = 452;
    const E0 = -3.2, E1 = 3.1;
    const Y = e => yBot - ((e - E0) / (E1 - E0)) * (yBot - yTop);
    const xa = 150, xb = 470;
    const g = [];
    g.push(line([[xa - 24, yBot + 10], [xa - 24, yTop - 14]], { stroke: 'var(--ink2)', sw: 1.4 }));
    g.push(line([[xa - 30, Y(0)], [xb + 66, Y(0)]], { stroke: 'var(--ink)', sw: 1.4 }));
    const rows = [
        [2.87, 'F₂ + 2e⁻ → 2F⁻'],
        [1.36, 'Cl₂ + 2e⁻ → 2Cl⁻'],
        [0.80, 'Ag⁺ + e⁻ → Ag'],
        [0.34, 'Cu²⁺ + 2e⁻ → Cu'],
        [0.00, '2H⁺ + 2e⁻ → H₂   (기준)'],
        [-0.44, 'Fe²⁺ + 2e⁻ → Fe'],
        [-0.76, 'Zn²⁺ + 2e⁻ → Zn'],
        [-1.66, 'Al³⁺ + 3e⁻ → Al'],
        [-2.71, 'Na⁺ + e⁻ → Na'],
        [-3.04, 'Li⁺ + e⁻ → Li'],
    ];
    for (const [e, name] of rows) {
        const y = Y(e);
        g.push(line([[xa - 30, y], [xa - 18, y]], { stroke: 'var(--ink2)', sw: 1.2 }));
        g.push(line([[xa, y], [xb, y]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        g.push(txt(xa, y + 5, name, { cls: 'ink', size: 'sm' }));
        g.push(txt(xb + 60, y + 5, `${e >= 0 ? '+' : ''}${e.toFixed(2)}`, { anchor: 'end', cls: 'ink', size: 'sm' }));
    }
    g.push(txt(xb + 60, yTop - 20, 'E° (V)', { anchor: 'end', cls: 'ink bold' }));
    // 자발 방향 표시
    g.push(px(96, Y(-0.76), 96, Y(0.34), { cls: 's3', marker: 'ar3', width: 2.4 }));
    g.push(txt(88, Y(-0.2), '전자는 아래에서', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(88, Y(-0.42), '위로 흐른다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(112, yTop - 14, '환원되기 쉽다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(112, yBot + 28, '산화되기 쉽다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 28, '표준 환원전위 — 큰 쪽이 전자를 빼앗는다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 30, 'Zn 과 Cu 를 짝지으면 0.34 − (−0.76) = 1.10 V', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 10, '수소보다 아래에 있는 금속은 묽은 산에 녹는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-echem-potential-ladder',
        title: '표준 환원전위 사다리',
        desc: '표준 환원전위가 큰 반쪽반응일수록 전자를 잘 빼앗아 환원되기 쉽다. 두 반쪽 전지를 이으면 위쪽이 환원, '
            + '아래쪽이 산화되고 두 값의 차이가 전지 전압이다. 수소보다 아래에 있는 금속이 묽은 산에 녹는 것도 같은 이유다. '
            + '값은 25 °C 표준 상태에서 흔히 인용되는 표의 값이다.',
        svg: svg({ width: W, height: H, title: '표준 환원전위', desc: 'F₂ 부터 Li 까지의 표준 환원전위를 세로 축에 늘어놓은 그림', body: g.join('') }),
    };
})());

/* 14-4. 전기분해 셀 */
add((() => {
    const W = 700, H = 420;
    const g = [];
    g.push(beaker(140, 140, 420, 220, 180, { cls: 's1', op: 0.1 }));
    // 전원
    g.push(box(310, 40, 80, 44, { stroke: 'var(--ink2)', sw: 1.6, rx: 4 }));
    g.push(txt(350, 68, '전원', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(300, 78, '−', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(400, 78, '+', { anchor: 'middle', cls: 'ink bold' }));
    g.push(line([[318, 84], [318, 124], [230, 124], [230, 170]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
    g.push(line([[382, 84], [382, 124], [470, 124], [470, 170]], { stroke: 'var(--ink2)', sw: 2, cap: 'butt' }));
    g.push(px(296, 124, 250, 124, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(txt(273, 114, 'e⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(20, 54, '전원이 전자를 한쪽 전극으로 밀어 넣는다', { cls: 'ink2', size: 'sm' }));

    // 전극
    g.push(box(222, 170, 16, 160, { fill: 'var(--ink2)', op: 0.4, stroke: 'var(--ink2)', sw: 1.3, rx: 2 }));
    g.push(box(462, 170, 16, 160, { fill: 'var(--ink2)', op: 0.4, stroke: 'var(--ink2)', sw: 1.3, rx: 2 }));
    // 시험관과 기체
    const tube = (x, gasH, label, sub) => box(x - 30, 150, 60, 120, { stroke: 'var(--ink2)', sw: 1.4, rx: 4 })
        + box(x - 28, 152, 56, gasH, { fill: 'var(--s3)', op: 0.25, stroke: 'none', sw: 0, rx: 3 })
        + txt(x, 152 + gasH / 2 + 4, label, { anchor: 'middle', cls: 'ink bold' })
        + txt(x, 288, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    g.push(tube(150, 96, 'H₂', '부피 2몫'));
    g.push(tube(550, 48, 'O₂', '부피 1몫'));
    for (const [x, y] of [[214, 300], [206, 268], [216, 240], [204, 214], [214, 190]]) {
        g.push(circ(x, y, 4, { fill: 'none', stroke: 'var(--s3)', sw: 1.3 }));
    }
    for (const [x, y] of [[486, 296], [494, 264], [484, 230], [492, 200]]) {
        g.push(circ(x, y, 3.6, { fill: 'none', stroke: 'var(--s2)', sw: 1.3 }));
    }
    g.push(txt(230, 348, '음극 — 환원이 일어난다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(230, 376, '2H₂O + 2e⁻ → H₂ + 2OH⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(470, 348, '양극 — 산화가 일어난다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(470, 376, '2H₂O → O₂ + 4H⁺ + 4e⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(20, 32, '전기분해 — 전기를 넣어 저절로 일어나지 않는 반응을 억지로 시킨다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '수소와 산소의 부피비가 2 대 1 인 것은 반응식의 계수 그대로다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-echem-electrolysis',
        title: '물의 전기분해',
        desc: '갈바니 전지는 저절로 일어나는 반응에서 전기를 꺼내고, 전기분해는 반대로 전기를 넣어 일어나지 않을 '
            + '반응을 강제한다. 환원이 일어나는 전극이 음극, 산화가 일어나는 전극이 양극이다. '
            + '모이는 수소와 산소의 부피비 2 대 1 은 반응식의 계수비와 같다.',
        svg: svg({ width: W, height: H, title: '전기분해 셀', desc: '전원에 이어진 두 전극과 모이는 기체', body: g.join('') }),
    };
})());

/* 14-5. 부식과 희생 양극 */
add((() => {
    const W = 720, H = 400;
    const g = [];
    // 왼쪽: 그냥 철
    g.push(panel(20, 50, 330, 320, '맨 철', { sub: '물방울 하나가 작은 전지를 만든다' }));
    g.push(box(46, 250, 278, 44, { fill: 'var(--ink2)', op: 0.28, stroke: 'var(--ink2)', sw: 1.4, rx: 2 }));
    g.push(txt(185, 278, '철', { anchor: 'middle', cls: 'ink bold' }));
    g.push(`<path d="M96 250 Q 96 176 185 176 Q 274 176 274 250 Z" fill="var(--s1)" fill-opacity="0.16" stroke="var(--s1)" stroke-width="1.4"/>`);
    g.push(txt(185, 166, '물방울', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(circ(185, 250, 5, { fill: 'var(--s2)', stroke: 'none', sw: 0 }));
    g.push(txt(185, 216, 'Fe → Fe²⁺ + 2e⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(185, 234, '가운데는 산화전극', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(px(175, 262, 108, 262, { cls: 's1', marker: 'ar1', width: 1.8 }));
    g.push(px(195, 262, 262, 262, { cls: 's1', marker: 'ar1', width: 1.8 }));
    g.push(txt(185, 306, 'e⁻ 는 금속 속을 지나 가장자리로 간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(185, 326, '가장자리: O₂ + 2H₂O + 4e⁻ → 4OH⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(185, 346, 'Fe²⁺ 와 OH⁻ 가 만나 녹이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 아연 도금
    g.push(panel(370, 50, 330, 320, '아연을 입힌 철', { sub: '흠집이 나도 철이 아니라 아연이 녹는다' }));
    g.push(box(396, 250, 278, 44, { fill: 'var(--ink2)', op: 0.28, stroke: 'var(--ink2)', sw: 1.4, rx: 2 }));
    g.push(txt(535, 278, '철', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(396, 236, 120, 14, { fill: 'var(--s3)', op: 0.4, stroke: 'var(--s3)', sw: 1.2, rx: 2 }));
    g.push(box(556, 236, 118, 14, { fill: 'var(--s3)', op: 0.4, stroke: 'var(--s3)', sw: 1.2, rx: 2 }));
    g.push(txt(456, 228, '아연 도금', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 228, '흠집', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(`<path d="M486 236 Q 486 182 560 182 Q 634 182 634 236 Z" fill="var(--s1)" fill-opacity="0.16" stroke="var(--s1)" stroke-width="1.4"/>`);
    g.push(txt(560, 172, '물방울', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(516, 208, 'Zn → Zn²⁺ + 2e⁻', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(px(500, 262, 570, 262, { cls: 's3', marker: 'ar3', width: 1.8 }));
    g.push(txt(535, 306, 'E°(Zn²⁺/Zn) = −0.76 V  <  E°(Fe²⁺/Fe) = −0.44 V', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(535, 326, '더 작은 쪽이 산화전극이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(535, 346, '아연이 대신 녹는 동안 철은 멀쩡하다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 32, '부식은 철 위에서 저절로 만들어지는 전지다', { cls: 'ink bold' }));
    return {
        name: 'chem-echem-corrosion',
        title: '철의 부식과 아연 도금',
        desc: '물방울 하나가 철 위에 작은 갈바니 전지를 만든다. 방울 가운데의 철이 산화되고, 산소가 닿는 가장자리에서 '
            + '환원이 일어난다. 아연을 입히면 아연의 표준 환원전위가 철보다 작으므로 아연이 먼저 산화된다. '
            + '흠집이 나서 철이 드러나도 철은 보호된다. 반대로 구리를 입히면 철이 더 빨리 부식된다.',
        svg: svg({ width: W, height: H, title: '부식과 희생 양극', desc: '맨 철과 아연 도금 철의 부식 비교', body: g.join('') }),
    };
})());

/* 14-6. 네른스트 식 — E 와 log Q */
add((() => {
    const W = 720, H = 340;
    const f = frame({ xRange: [-6, 6], yRange: [0.9, 1.32], box: { x: 74, y: 72, w: 380, h: 200 } });
    const E = q => 1.10 - (0.0592 / 2) * q;
    const g = [
        f.axes({ xLabel: 'log Q', yLabel: 'E (V)', xTicks: [-6, -4, -2, 0, 2, 4, 6], yTicks: [0.9, 1.0, 1.1, 1.2, 1.3] }),
        f.curve(E, { cls: 's1' }),
        f.guide([0, 0.9], [0, 1.10]),
        f.guide([-6, 1.10], [0, 1.10]),
        f.dot([0, 1.10], { cls: 'f2', r: 5 }),
    ];
    g.push(txt(f.X(0) + 10, f.Y(1.10) - 10, 'E° = 1.10 V', { cls: 'ink bold', size: 'sm' }));
    const cx = 488;
    g.push(txt(cx, 84, 'E = E° − (0.0592 / n) log Q', { cls: 'ink bold' }));
    g.push(txt(cx, 104, '(25 °C, 이 그림은 n = 2)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 138, 'Q 가 10배 커질 때마다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 156, '전압은 0.0296 V 씩 내려간다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 188, 'Q < 1 (생성물이 적다)  →  E > E°', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 206, 'Q > 1 (생성물이 많다)  →  E < E°', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 240, 'Q = K 가 되면 E = 0', { cls: 'ink bold' }));
    g.push(txt(cx, 260, '전지가 다 소모된 상태다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '네른스트 식은 로그 Q 에 대한 일차함수다', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '기울기가 −(0.0592 / n) 이므로, 전자를 많이 주고받는 반응일수록 농도 변화에 덜 민감하다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-echem-nernst-log',
        title: '네른스트 식 — 전압은 log Q 의 일차함수',
        desc: '네른스트 식은 전지 전압을 반응지수 Q 의 로그에 대한 일차함수로 준다. Q 가 10배 커질 때마다 전압이 '
            + '일정한 양(0.0592/n 볼트)만큼 떨어진다. 반응이 진행되면 Q 가 커지고 전압이 줄다가, Q 가 평형상수 K 와 '
            + '같아지는 순간 전압이 0 이 된다. 그때가 전지가 다 소모된 상태다.',
        svg: svg({ width: W, height: H, title: '네른스트 식의 직선', desc: 'E 대 log Q 그래프', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 15장 — 반응 속도론
 * ================================================================== */

/* 15-1. 농도-시간 곡선과 두 가지 속도 */
add((() => {
    const W = 660, H = 380;
    const A = t => Math.exp(-0.35 * t);
    const f = frame({ xRange: [0, 10], yRange: [0, 1.1], box: { x: 76, y: 78, w: 380, h: 232 } });
    const g = [
        f.axes({ xLabel: '시간 (s)', yLabel: '농도 (mol/L)', xTicks: [0, 2, 4, 6, 8, 10], yTicks: [0, 0.25, 0.5, 0.75, 1] }),
        f.curve(A, { cls: 's1' }),
        f.curve(t => 1 - A(t), { cls: 's3' }),
    ];
    g.push(f.line([[0, 1], [4, A(4)]], { cls: 's2', dash: '6 4' }));
    g.push(f.dot([0, 1], { cls: 'f2', r: 4 }));
    g.push(f.dot([4, A(4)], { cls: 'f2', r: 4 }));
    const m = -0.35 * A(4);
    g.push(f.line([[2.2, A(4) + m * (2.2 - 4)], [7, A(4) + m * (7 - 4)]], { cls: 's2' }));
    g.push(txt(f.X(6.6), f.Y(0.17), '반응물 A', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(6.6), f.Y(0.97), '생성물 B', { cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([2.0, 0.6233], [1.5, 0.90]));
    g.push(txt(f.X(0.9), f.Y(1.04), '두 점을 잇는 직선(점선)의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0.9), f.Y(0.96), '기울기 = 평균 속도', { cls: 'ink2', size: 'sm' }));
    g.push(f.guide([5.0, 0.16], [5.0, 0.36]));
    g.push(txt(f.X(4.3), f.Y(0.44), '접선의 기울기 = 그 순간의 속도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 92, '속도는 시간에 따라 달라진다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(478, 120, '반응물이 줄면 부딪힐', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 138, '기회가 줄기 때문이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 156, '곡선이 점점 완만해진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 190, '"이 반응의 속도"라고 하려면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 208, '언제의 속도인지 밝혀야 한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(478, 244, '속도식을 세울 때는', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(478, 262, 't = 0 의 접선 기울기', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(478, 280, '(초기 속도)를 쓴다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 32, '반응 속도는 곡선의 기울기다', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, 'A 가 줄어드는 만큼 B 가 늘어난다. 계수가 1대1 이면 두 곡선은 위아래로 뒤집힌 모양이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-conc-time',
        title: '농도-시간 곡선에서 읽는 두 가지 속도',
        desc: '반응물의 농도는 시간이 갈수록 줄지만 줄어드는 빠르기 자체가 계속 달라진다. 두 점을 잇는 직선의 '
            + '기울기가 그 구간의 평균 속도이고, 한 점에서 그은 접선의 기울기가 그 순간의 속도다. '
            + '속도식을 세울 때는 처음 순간의 기울기, 즉 초기 속도를 쓴다.',
        svg: svg({ width: W, height: H, title: '농도-시간 곡선', desc: '반응물과 생성물의 농도 곡선, 할선과 접선', body: g.join('') }),
    };
})());

/* 15-2. 차수 판정 — 어느 축에서 직선이 되는가 */
add((() => {
    const W = 780, H = 380;
    const ts = [0, 2, 4, 6, 8, 10];
    const P = t => 100 * Math.exp(-0.25 * t);
    const g = [];
    const pw = 180, py = 96, ph = 186;
    const xs = [70, 320, 570];

    const p1 = frame({ xRange: [0, 10], yRange: [0, 108], box: { x: xs[0], y: py, w: pw, h: ph } });
    g.push(p1.axes({ xLabel: 't', yLabel: '', xTicks: [0, 5, 10], yTicks: [0, 50, 100] }));
    g.push(p1.line([[0, 100], [10, P(10)]], { cls: 's2', dash: '5 4' }));
    g.push(p1.curve(P, { cls: 's1' }));
    for (const t of ts) g.push(p1.dot([t, P(t)], { cls: 'f1', r: 3.5 }));

    const p2 = frame({ xRange: [0, 10], yRange: [1.8, 5.0], box: { x: xs[1], y: py, w: pw, h: ph } });
    g.push(p2.axes({ xLabel: 't', yLabel: '', xTicks: [0, 5, 10], yTicks: [2, 3, 4, 5] }));
    g.push(p2.curve(t => Math.log(P(t)), { cls: 's1' }));
    for (const t of ts) g.push(p2.dot([t, Math.log(P(t))], { cls: 'f1', r: 3.5 }));

    const p3 = frame({ xRange: [0, 10], yRange: [0, 0.135], box: { x: xs[2], y: py, w: pw, h: ph } });
    g.push(p3.axes({ xLabel: 't', yLabel: '', xTicks: [0, 5, 10], yTicks: [0, 0.05, 0.1] }));
    g.push(p3.line([[0, 0.01], [10, 1 / P(10)]], { cls: 's2', dash: '5 4' }));
    g.push(p3.curve(t => 1 / P(t), { cls: 's1' }));
    for (const t of ts) g.push(p3.dot([t, 1 / P(t)], { cls: 'f1', r: 3.5 }));

    const heads = [
        [0, '세로축 = p', '0차라면 직선', '아래로 휘었다 → 0차 아니다'],
        [1, '세로축 = ln p', '1차라면 직선', '똑바른 직선 → 1차다'],
        [2, '세로축 = 1/p', '2차라면 직선', '위로 휘었다 → 2차 아니다'],
    ];
    for (const [i, a, b, c] of heads) {
        const cx = xs[i] + pw / 2;
        g.push(txt(cx, 62, a, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(cx, 80, b, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(cx, py + ph + 46, c, { anchor: 'middle', cls: i === 1 ? 'ink bold' : 'ink2', size: 'sm' }));
    }
    g.push(box(xs[1] - 26, py - 54, pw + 52, ph + 116, { stroke: 'var(--s3)', sw: 2, rx: 8, dash: '6 4' }));
    g.push(txt(20, 30, '같은 자료를 세 가지 세로축에 그려 본다. 직선이 되는 축이 차수를 알려 준다', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '기체 반응이라 농도 대신 부분압력 p (kPa)를 썼다. 가로축 t 는 분(min)이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 14, H - 12, '주황 점선은 눈으로 직선과 견주기 위한 보조선이다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-linearize',
        title: '차수는 직선이 되는 축을 찾아 판정한다',
        desc: '같은 자료를 p, ln p, 1/p 세 가지 세로축에 각각 그린다. 0차라면 첫째가, 1차라면 둘째가, '
            + '2차라면 셋째가 직선이 된다. 이 자료에서는 가운데 ln p 만 직선이므로 1차 반응이다. '
            + '미적분 없이 차수를 정하는 실용적인 방법이다.',
        svg: svg({ width: W, height: H, title: '적분 속도식의 선형화', desc: '같은 자료를 세 축에 그린 세 패널', body: g.join('') }),
    };
})());

/* 15-3. 반감기 — 1차와 2차의 차이 */
add((() => {
    const W = 720, H = 360;
    const g = [];
    const mk = (x, fA, halves, title, sub, cls) => {
        const fr = frame({ xRange: [0, 16], yRange: [0, 1.08], box: { x, y: 88, w: 250, h: 180 } });
        const out = [
            fr.axes({ xLabel: '시간', yLabel: '[A]', xTicks: [0, 4, 8, 12, 16], yTicks: [0, 0.25, 0.5, 0.75, 1] }),
            fr.curve(fA, { cls }),
        ];
        let prev = 0;
        for (let i = 0; i < halves.length; i += 1) {
            const t = halves[i];
            const v = 1 / 2 ** (i + 1);
            out.push(fr.guide([t, 0], [t, v]));
            out.push(fr.guide([0, v], [t, v]));
            out.push(fr.dot([t, v], { cls: 'f2', r: 4 }));
            out.push(px(fr.X(prev), fr.Y(v) - 9, fr.X(t), fr.Y(v) - 9, { cls: 's2', marker: 'ar2', width: 1.4 }));
            out.push(txt((fr.X(prev) + fr.X(t)) / 2, fr.Y(v) - 14, String(t - prev), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            prev = t;
        }
        out.push(txt(x + 125, 62, title, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(x + 125, 310, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mk(70, t => 2 ** (-t / 4), [4, 8, 12], '1차 반응', '4, 4, 4 — 반감기가 늘 같다', 's1'));
    g.push(mk(410, t => 1 / (1 + t / 4), [4, 12], '2차 반응', '4, 8, … — 반감기가 자꾸 길어진다', 's3'));
    g.push(txt(20, 30, '반감기가 일정한가를 보면 1차인지 알 수 있다', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '1차에서는 반감기가 처음 농도와 무관하다. 반감기를 n 번 지나면 남는 양은 2ⁿ 분의 1 이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-half-life',
        title: '1차 반응과 2차 반응의 반감기',
        desc: '반감기는 반응물이 절반으로 줄어드는 데 걸리는 시간이다. 1차 반응에서는 이 시간이 처음 농도와 상관없이 '
            + '늘 같아서, 반감기를 n 번 지나면 남는 양이 2의 n 제곱분의 1 이 된다. 2차 반응에서는 농도가 줄수록 '
            + '반감기가 길어진다. 반감기가 일정한지 보는 것만으로도 1차 여부를 가릴 수 있다.',
        svg: svg({ width: W, height: H, title: '반감기 비교', desc: '1차와 2차 반응의 반감기 간격 비교', body: g.join('') }),
    };
})());

/* 15-4. 반응 좌표와 활성화 에너지 */
add((() => {
    const W = 680, H = 400;
    const dH = -30, peak = 62;
    const S = x => { const t = Math.min(1, Math.max(0, (x - 2.5) / 5)); return t * t * (3 - 2 * t); };
    const E = x => dH * S(x) + peak * Math.exp(-(((x - 5) / 1.7) ** 2));
    const f = frame({ xRange: [0, 10], yRange: [-45, 62], box: { x: 96, y: 76, w: 420, h: 222 } });
    const g = [
        f.axes({ xLabel: '반응 좌표', yLabel: '에너지', xTicks: [], yTicks: [] }),
        f.curve(E, { cls: 's1' }),
        f.guide([0, 0], [10, 0]),
        f.guide([0, dH], [10, dH]),
        f.guide([5, dH], [5, E(5)]),
    ];
    g.push(px(f.X(2.2), f.Y(0), f.X(2.2), f.Y(E(5)), { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(f.X(2.05), f.Y(30), 'E~a (정반응)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(px(f.X(7.9), f.Y(dH), f.X(7.9), f.Y(E(5)), { cls: 's3', marker: 'ar3', width: 2 }));
    g.push(txt(f.X(8.05), f.Y(18), 'E~a (역반응)', { cls: 'ink bold', size: 'sm' }));
    g.push(px(f.X(9.5), f.Y(0), f.X(9.5), f.Y(dH), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(f.X(9.4), f.Y(-17), 'ΔH', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.dot([5, E(5)], { cls: 'f2', r: 5 }));
    g.push(txt(f.X(5), f.Y(E(5)) - 12, '전이 상태 (활성화 착물)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(0.2), f.Y(0) + 18, '반응물', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(9.9), f.Y(dH) + 18, '생성물', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 32, '반응은 언덕을 넘어야 일어난다', { cls: 'ink bold' }));
    g.push(txt(20, H - 50, '언덕의 높이가 활성화 에너지 E~a 다. 넘을 만큼의 에너지를 가진 충돌만 반응이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 30, 'E~a 는 속도를 정하고, 시작과 끝의 높이 차이 ΔH 는 열출입을 정한다. 둘은 별개의 양이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '여기서는 생성물이 더 낮으므로 발열 반응이고, 역반응의 언덕이 정반응보다 높다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-activation',
        title: '반응 좌표 그림과 활성화 에너지',
        desc: '반응물이 생성물로 가려면 먼저 에너지 언덕을 넘어야 한다. 언덕 높이가 활성화 에너지이고 꼭대기가 '
            + '전이 상태다. 활성화 에너지는 반응이 얼마나 빠른지를, 시작과 끝의 높이 차이 ΔH 는 열이 나가는지 '
            + '들어오는지를 정한다. 두 양은 서로 무관하며, 발열 반응이라고 반드시 빠른 것은 아니다.',
        svg: svg({ width: W, height: H, title: '활성화 에너지', desc: '반응 좌표에 따른 에너지 곡선과 Ea, ΔH', body: g.join('') }),
    };
})());

/* 15-5. 촉매의 효과 */
add((() => {
    const W = 680, H = 380;
    const dH = -30;
    const S = x => { const t = Math.min(1, Math.max(0, (x - 2.5) / 5)); return t * t * (3 - 2 * t); };
    const E1 = x => dH * S(x) + 62 * Math.exp(-(((x - 5) / 1.7) ** 2));
    const E2 = x => dH * S(x) + 28 * Math.exp(-(((x - 3.8) / 0.9) ** 2)) + 24 * Math.exp(-(((x - 6.2) / 0.9) ** 2));
    const f = frame({ xRange: [0, 10], yRange: [-45, 62], box: { x: 96, y: 76, w: 420, h: 210 } });
    const g = [
        f.axes({ xLabel: '반응 좌표', yLabel: '에너지', xTicks: [], yTicks: [] }),
        f.curve(E1, { cls: 's1' }),
        f.curve(E2, { cls: 's2', dash: '7 4' }),
        f.guide([0, 0], [10, 0]),
        f.guide([0, dH], [10, dH]),
    ];
    g.push(txt(f.X(5), f.Y(E1(5)) - 10, '촉매 없이 — 언덕이 높다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(4.0), f.Y(-42), '촉매를 넣으면 — 낮은 언덕 둘로 갈라진다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(0.2), f.Y(0) + 18, '반응물', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(9.9), f.Y(dH) + 18, '생성물', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(px(f.X(9.5), f.Y(0), f.X(9.5), f.Y(dH), { cls: 's3', marker: 'ar3', width: 2 }));
    g.push(txt(f.X(9.4), f.Y(-17), 'ΔH 는 그대로', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 32, '촉매는 길을 바꾼다. 출발점과 도착점은 건드리지 않는다', { cls: 'ink bold' }));
    g.push(txt(20, H - 50, '반응물과 생성물의 높이가 그대로이므로 ΔH 도, 평형상수 K 도 변하지 않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 30, '촉매는 정반응과 역반응의 속도를 같은 배로 키운다. 평형에 빨리 갈 뿐 더 멀리 가지 않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '가운데 골짜기는 중간체다. 촉매 반응이 대개 여러 단계로 나뉘는 이유다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-catalyst',
        title: '촉매가 하는 일과 하지 않는 일',
        desc: '촉매는 활성화 에너지가 낮은 다른 경로를 열어 준다. 그러나 반응물과 생성물의 에너지는 건드리지 않으므로 '
            + 'ΔH 도 평형상수도 그대로다. 정반응과 역반응의 속도를 똑같은 배로 키우기 때문에 평형에 빨리 도달할 뿐 '
            + '수득률이 늘지는 않는다.',
        svg: svg({ width: W, height: H, title: '촉매의 효과', desc: '촉매 있는 경로와 없는 경로의 에너지 곡선 비교', body: g.join('') }),
    };
})());

/* 15-6. 충돌 이론 — 방향이 맞아야 한다 */
add((() => {
    const W = 700, H = 360;
    const g = [];
    const mol = (x, y, l1, l2) => atom(x, y, 17, l1, { fill: 'var(--s1)', stroke: 'var(--s1)' })
        + bond([x, y], [x + 44, y], 1, { r1: 17, r2r: 17 })
        + atom(x + 44, y, 17, l2, { fill: 'var(--s3)', stroke: 'var(--s3)' });

    g.push(panel(20, 56, 660, 122, '방향이 맞은 충돌 — 반응이 된다'));
    g.push(mol(110, 132, 'A', 'B'));
    g.push(atom(250, 132, 17, 'C', { fill: 'var(--s2)', stroke: 'var(--s2)' }));
    g.push(px(230, 132, 186, 132, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(300, 137, '→', { anchor: 'middle', cls: 'ink bold' }));
    g.push(atom(350, 132, 17, 'A', { fill: 'var(--s1)', stroke: 'var(--s1)' }));
    g.push(px(374, 132, 412, 132, { cls: 's1', marker: 'ar1', width: 1.8 }));
    g.push(atom(470, 132, 17, 'B', { fill: 'var(--s3)', stroke: 'var(--s3)' }));
    g.push(bond([470, 132], [514, 132], 1, { r1: 17, r2r: 17 }));
    g.push(atom(514, 132, 17, 'C', { fill: 'var(--s2)', stroke: 'var(--s2)' }));
    g.push(txt(560, 137, 'A + B–C', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 196, 'C 가 B 쪽으로 다가온다 — 끊을 결합과 만들 결합이 한 줄에 놓인다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(20, 214, 660, 106, '방향이 어긋난 충돌 — 그냥 튕긴다'));
    g.push(mol(150, 284, 'A', 'B'));
    g.push(atom(84, 250, 17, 'C', { fill: 'var(--s2)', stroke: 'var(--s2)' }));
    g.push(px(98, 262, 126, 272, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(px(140, 266, 108, 236, { cls: 's2', marker: 'ar2', width: 2, dash: '5 3' }));
    g.push(txt(268, 289, '→', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(300, 289, 'A–B + C   (그대로)', { cls: 'ink' }));
    g.push(txt(300, 310, 'C 가 A 쪽에서 부딪히면 B–C 결합을 만들 자리가 없다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, 32, '부딪히기만 해서는 반응이 되지 않는다', { cls: 'ink bold' }));
    g.push(txt(20, H - 10, '반응이 되려면 두 조건이 함께 맞아야 한다. 활성화 에너지를 넘을 만큼의 에너지, 그리고 알맞은 방향',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-collision',
        title: '충돌 이론 — 에너지와 방향',
        desc: '분자가 부딪힌다고 모두 반응이 되지는 않는다. 활성화 에너지를 넘을 만큼 세게 부딪혀야 하고, 끊을 결합과 '
            + '만들 결합이 한 줄에 놓이도록 방향까지 맞아야 한다. 방향이 맞을 확률이 낮은 반응일수록 실제 속도가 '
            + '충돌 횟수에서 기대한 것보다 훨씬 느리다.',
        svg: svg({ width: W, height: H, title: '충돌의 방향', desc: '방향이 맞은 충돌과 어긋난 충돌의 비교', body: g.join('') }),
    };
})());

/* 15-7. 온도와 에너지 분포 */
add((() => {
    const W = 680, H = 360;
    const dist = (E, kT) => Math.sqrt(E) * Math.exp(-E / kT);
    const norm = kT => { let m = 0; for (let e = 0.01; e < 16; e += 0.01) m = Math.max(m, dist(e, kT)); return m; };
    const n1 = norm(1.0), n2 = norm(1.7);
    const f1 = e => dist(e, 1.0) / n1;
    const f2 = e => dist(e, 1.7) / n2;
    const Ea = 6;
    const f = frame({ xRange: [0, 14], yRange: [0, 1.15], box: { x: 76, y: 78, w: 390, h: 214 } });
    const g = [f.axes({ xLabel: '분자 하나의 에너지', yLabel: '분자 수', xTicks: [], yTicks: [] })];
    const fill = (fn, cls) => {
        const pts = [[f.X(Ea), f.Y(0)]];
        for (let e = Ea; e <= 14; e += 0.2) pts.push([f.X(e), f.Y(fn(e))]);
        pts.push([f.X(14), f.Y(0)]);
        return poly(pts, { fill: `var(--${cls})`, op: 0.32 });
    };
    g.push(fill(f2, 's2'));
    g.push(fill(f1, 's1'));
    g.push(f.curve(f1, { cls: 's1', from: 0.01 }));
    g.push(f.curve(f2, { cls: 's2', from: 0.01 }));
    g.push(f.line([[Ea, 0], [Ea, 1.04]], { cls: 's3', dash: '6 4' }));
    g.push(txt(f.X(Ea), f.Y(1.09), 'E~a', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.guide([1.2, 0.77], [7.0, 0.96]));
    g.push(txt(f.X(7.15), f.Y(0.97), '낮은 온도 T₁', { cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([2.6, 0.64], [7.0, 0.82]));
    g.push(txt(f.X(7.15), f.Y(0.83), '높은 온도 T₂', { cls: 'ink bold', size: 'sm' }));
    g.push(px(f.X(11.4), f.Y(0.42), f.X(8.4), f.Y(0.1), { cls: 's2', marker: 'ar2', width: 1.6 }));
    g.push(txt(f.X(9.4), f.Y(0.50), '넘을 수 있는 분자 (색칠한 부분)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '온도를 올리면 곡선이 오른쪽으로 퍼진다', { cls: 'ink bold' }));
    g.push(txt(20, H - 50, '분자들의 에너지는 제각각이다. 그중 E~a 를 넘는 것만 반응할 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 30, '온도가 조금만 올라도 꼬리 쪽 넓이는 크게 늘어난다. 속도가 급격히 빨라지는 이유다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 10, '곡선 아래 전체 넓이(분자의 총수)는 두 온도에서 같다는 점을 눈여겨볼 것', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-boltzmann',
        title: '온도가 오르면 왜 반응이 빨라지는가',
        desc: '같은 온도에서도 분자들의 에너지는 제각각이고, 활성화 에너지를 넘는 것은 꼬리 쪽의 일부뿐이다. '
            + '온도를 올리면 분포가 오른쪽으로 퍼지면서 그 꼬리의 넓이가 크게 늘어난다. 평균 에너지는 조금 올랐는데 '
            + '반응 속도는 몇 배로 뛰는 것이 이 때문이다.',
        svg: svg({ width: W, height: H, title: '에너지 분포와 활성화 에너지', desc: '두 온도의 에너지 분포 곡선과 Ea 를 넘는 넓이', body: g.join('') }),
    };
})());

/* 15-8. 아레니우스 그림 */
add((() => {
    const W = 700, H = 360;
    const lnk = x => 23.03 - 6.014 * x;       // x 는 1/T 를 10⁻³ K⁻¹ 단위로 적은 값
    const f = frame({ xRange: [2.4, 3.5], yRange: [0, 10], box: { x: 88, y: 66, w: 350, h: 224 } });
    const g = [
        f.axes({ xLabel: '1/T  (10⁻³ K⁻¹)', yLabel: 'ln k', xTicks: [2.4, 2.7, 3.0, 3.3], yTicks: [0, 2, 4, 6, 8, 10] }),
        f.curve(lnk, { cls: 's1' }),
        f.guide([2.5, 0], [2.5, lnk(2.5)]),
        f.guide([3.33, 0], [3.33, lnk(3.33)]),
        f.dot([2.5, lnk(2.5)], { cls: 'f2', r: 4.5 }),
        f.dot([3.33, lnk(3.33)], { cls: 'f2', r: 4.5 }),
    ];
    g.push(txt(f.X(2.44), f.Y(9.3), 'T = 400 K', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(3.33), f.Y(1.2), 'T = 300 K', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 96, 'ln k = ln A − (E~a / R)(1/T)', { cls: 'ink bold' }));
    g.push(txt(462, 126, '가로축을 1/T 로 잡으면 직선이 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 156, '기울기 = −E~a / R', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(462, 174, '세로축 절편 = ln A', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(462, 204, '온도 두 개에서 k 를 재면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 222, '기울기가 나오고, 거기서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 240, 'E~a 를 구할 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, 270, '오른쪽으로 갈수록 저온이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '아레니우스 식도 로그를 취하면 직선이 된다', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '이 그림은 E~a = 50 kJ/mol 로 그린 것이다. 기울기가 가파를수록 온도에 민감한 반응이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-kinetics-arrhenius',
        title: '아레니우스 그림 — ln k 와 1/T',
        desc: '속도상수는 온도에 지수적으로 의존하지만, 양변에 로그를 취하고 가로축을 1/T 로 잡으면 직선이 된다. '
            + '기울기가 −Ea/R 이므로 서로 다른 두 온도에서 k 를 재는 것만으로 활성화 에너지를 구할 수 있다. '
            + '기울기가 가파른 반응일수록 온도를 조금만 바꿔도 속도가 크게 달라진다.',
        svg: svg({ width: W, height: H, title: '아레니우스 그림', desc: 'ln k 대 1/T 직선과 기울기', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 16장 — 유기화학 입문
 * ================================================================== */

/** 라벨 하나(원자 기호). */
const lbl = (x, y, t, { cls = 'ink', size } = {}) => txt(x, y + 5, t, { anchor: 'middle', cls, size });
/** 라벨 사이 결합선. 양끝을 pad 만큼 비운다. */
function bnd(x1, y1, x2, y2, n = 1, pad = 13) {
    return bond([x1, y1], [x2, y2], n, { r1: pad, r2r: pad, sw: 1.6, gap: 5 });
}
/** 앞으로 튀어나온 결합(쐐기). */
function wedge(x1, y1, x2, y2, w = 7) {
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L, nx = -uy, ny = ux;
    const a = [x1 + ux * 13, y1 + uy * 13], b = [x2 - ux * 13, y2 - uy * 13];
    return poly([a, [b[0] + nx * w, b[1] + ny * w], [b[0] - nx * w, b[1] - ny * w]],
        { fill: 'var(--ink)', op: 0.85 });
}
/** 뒤로 들어간 결합(빗금). */
function hashb(x1, y1, x2, y2, k = 5) {
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L, nx = -uy, ny = ux;
    const out = [];
    for (let i = 1; i <= k; i += 1) {
        const t = 13 + ((L - 26) * i) / (k + 1);
        const w = 1.6 + (i / k) * 5.4;
        out.push(line([[x1 + ux * t + nx * w, y1 + uy * t + ny * w], [x1 + ux * t - nx * w, y1 + uy * t - ny * w]],
            { stroke: 'var(--ink)', sw: 1.6, cap: 'butt' }));
    }
    return out.join('');
}

/* 16-1. 사면체 탄소 */
add((() => {
    const W = 680, H = 340;
    const g = [];
    const cx = 190, cy = 180;
    g.push(panel(20, 56, 330, 256, '메테인 CH₄ 를 종이 위에 그리는 법'));
    g.push(bnd(cx, cy, cx - 62, cy - 40));
    g.push(bnd(cx, cy, cx + 62, cy - 40));
    g.push(wedge(cx, cy, cx - 38, cy + 66));
    g.push(hashb(cx, cy, cx + 38, cy + 66));
    g.push(lbl(cx, cy, 'C', { cls: 'ink bold' }));
    for (const [x, y] of [[cx - 62, cy - 40], [cx + 62, cy - 40], [cx - 38, cy + 66], [cx + 38, cy + 66]]) {
        g.push(lbl(x, y, 'H'));
    }
    g.push(txt(cx - 74, cy + 92, '굵은 쐐기 = 앞으로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + 78, cy + 92, '빗금 = 뒤로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, 116, '결합각 109.5°', { anchor: 'middle', cls: 'ink bold' }));

    // 오른쪽: 사면체
    const tx = 515, ty = 186;
    // 정사면체 꼭짓점 (1,1,1) (1,-1,-1) (-1,1,-1) (-1,-1,1) 을 비스듬히 투영한다.
    const S3 = 54;
    const proj = ([a, b, cc]) => [tx + S3 * (a + 0.35 * b), ty + S3 * (-cc + 0.28 * b)];
    const v = [[1, 1, 1], [1, -1, -1], [-1, 1, -1], [-1, -1, 1]].map(proj);
    g.push(panel(370, 56, 290, 256, '실제 모양은 정사면체다'));
    g.push(poly([v[0], v[1], v[3]], { fill: 'var(--s1)', op: 0.1 }));
    for (let i = 0; i < 4; i += 1) {
        for (let j = i + 1; j < 4; j += 1) g.push(line([v[i], v[j]], { stroke: 'var(--grid)', sw: 1.2 }));
    }
    for (const q of v) g.push(line([[tx, ty], q], { stroke: 'var(--ink2)', sw: 1.4, dash: '5 3' }));
    for (const q of v) {
        g.push(circ(q[0], q[1], 13, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)', sw: 1.3 }));
        g.push(lbl(q[0], q[1], 'H', { size: 'sm' }));
    }
    g.push(circ(tx, ty, 16, { fill: 'var(--s2)', op: 0.35, stroke: 'var(--s2)', sw: 1.5 }));
    g.push(lbl(tx, ty, 'C', { cls: 'ink bold' }));
    g.push(txt(515, 296, '네 방향이 서로 가장 멀어지는 배치', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '단일결합만 가진 탄소는 평면이 아니라 사면체다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '평면에 그린 그림에 속으면 안 된다. 이 입체성이 이성질체와 반응성을 좌우한다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-org-tetrahedral-carbon',
        title: '사면체 탄소와 쐐기·빗금 표기',
        desc: '단일결합 네 개를 가진 탄소는 정사면체의 꼭짓점 방향으로 결합을 뻗고 결합각은 약 109.5도다. '
            + '종이에 그릴 때는 두 결합을 평면에 두고, 앞으로 나온 결합은 굵은 쐐기로, 뒤로 들어간 결합은 빗금으로 '
            + '적는다. 이 입체 구조를 놓치면 거울상 이성질체를 이해할 수 없다.',
        svg: svg({ width: W, height: H, title: '사면체 탄소', desc: '메테인의 쐐기·빗금 표기와 정사면체 모형', body: g.join('') }),
    };
})());

/* 16-2. 탄화수소 네 갈래 */
add((() => {
    const W = 760, H = 320;
    const g = [];
    const px0 = [20, 205, 390, 575];
    const pwv = 170;
    const heads = [
        ['알케인', 'CₙH₂ₙ₊₂', '단일결합 · sp³ · 109.5°'],
        ['알켄', 'CₙH₂ₙ', '이중결합 · sp² · 120°'],
        ['알카인', 'CₙH₂ₙ₋₂', '삼중결합 · sp · 180°'],
        ['방향족', '벤젠 C₆H₆', 'π 전자가 고리 전체에 퍼진다'],
    ];
    for (let i = 0; i < 4; i += 1) {
        g.push(panel(px0[i], 56, pwv, 210, heads[i][0], { sub: heads[i][1] }));
        g.push(txt(px0[i] + pwv / 2, 246, heads[i][2], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    // 에테인
    let c = px0[0] + pwv / 2;
    g.push(bnd(c - 30, 150, c + 30, 150, 1));
    g.push(lbl(c - 30, 150, 'C', { cls: 'ink bold' })); g.push(lbl(c + 30, 150, 'C', { cls: 'ink bold' }));
    for (const [bx0, x, y] of [[c - 30, c - 30, 108], [c - 30, c - 70, 174], [c - 30, c - 44, 192],
        [c + 30, c + 30, 108], [c + 30, c + 70, 174], [c + 30, c + 44, 192]]) {
        g.push(bnd(bx0, 150, x, y, 1, 12));
        g.push(lbl(x, y, 'H', { size: 'sm' }));
    }
    g.push(txt(c, 214, '에테인 C₂H₆', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 에텐
    c = px0[1] + pwv / 2;
    g.push(bnd(c - 30, 150, c + 30, 150, 2));
    g.push(lbl(c - 30, 150, 'C', { cls: 'ink bold' })); g.push(lbl(c + 30, 150, 'C', { cls: 'ink bold' }));
    for (const [x, y] of [[c - 66, 118], [c - 66, 182], [c + 66, 118], [c + 66, 182]]) {
        g.push(bnd(x < c ? c - 30 : c + 30, 150, x, y, 1, 12));
        g.push(lbl(x, y, 'H', { size: 'sm' }));
    }
    g.push(txt(c, 214, '에텐 C₂H₄ (평면)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 에타인
    c = px0[2] + pwv / 2;
    g.push(bnd(c - 24, 150, c + 24, 150, 3));
    g.push(lbl(c - 24, 150, 'C', { cls: 'ink bold' })); g.push(lbl(c + 24, 150, 'C', { cls: 'ink bold' }));
    g.push(bnd(c - 24, 150, c - 66, 150, 1, 12)); g.push(lbl(c - 66, 150, 'H', { size: 'sm' }));
    g.push(bnd(c + 24, 150, c + 66, 150, 1, 12)); g.push(lbl(c + 66, 150, 'H', { size: 'sm' }));
    g.push(txt(c, 214, '에타인 C₂H₂ (직선)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    // 벤젠
    c = px0[3] + pwv / 2;
    const R = 44, cyb = 152;
    const hex = [];
    for (let k = 0; k < 6; k += 1) {
        const a = (Math.PI / 180) * (90 + k * 60);
        hex.push([c + R * Math.cos(a), cyb - R * Math.sin(a)]);
    }
    for (let k = 0; k < 6; k += 1) g.push(line([hex[k], hex[(k + 1) % 6]], { stroke: 'var(--ink2)', sw: 1.8 }));
    g.push(circ(c, cyb, R * 0.58, { stroke: 'var(--s1)', sw: 2 }));
    g.push(txt(c, 214, '벤젠 — 결합 길이가 모두 같다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(20, 32, '탄소 골격이 무엇으로 이어졌는가로 네 갈래가 갈린다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '결합이 여러 겹일수록 두 탄소가 가까워지고 그 축을 돌 수 없게 된다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-org-hydrocarbons',
        title: '탄화수소의 네 갈래',
        desc: '탄소끼리 단일결합만으로 이어지면 알케인, 이중결합이 하나 있으면 알켄, 삼중결합이면 알카인이다. '
            + '벤젠은 이중결합과 단일결합이 번갈아 있는 것처럼 그리지만 실제로는 여섯 결합의 길이가 모두 같고 '
            + 'π 전자가 고리 전체에 퍼져 있어 따로 방향족으로 분류한다.',
        svg: svg({ width: W, height: H, title: '탄화수소의 분류', desc: '에테인·에텐·에타인·벤젠의 구조', body: g.join('') }),
    };
})());

/* 16-3. 작용기 표 */
add((() => {
    const W = 760, H = 508;
    const g = [];
    const cols = [20, 268, 516], rows = [56, 202, 348];
    const pwv = 228, phv = 130;
    const cell = (i, j) => [cols[i] + pwv / 2, rows[j] + 84];

    const put = (i, j, name, ex, draw) => {
        g.push(panel(cols[i], rows[j], pwv, phv, name));
        g.push(txt(cols[i] + pwv / 2, rows[j] + phv - 8, ex, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const [x, y] = cell(i, j);
        g.push(draw(x, y));
    };
    const dblO = (x, y) => bnd(x, y, x, y - 34, 2) + lbl(x, y - 34, 'O');

    put(0, 0, '알코올  R–OH', '에탄올', (x, y) => bnd(x - 38, y, x, y) + bnd(x, y, x + 38, y)
        + lbl(x - 38, y, 'R') + lbl(x, y, 'O') + lbl(x + 38, y, 'H'));
    put(1, 0, '에터  R–O–R′', '다이에틸에터', (x, y) => bnd(x - 42, y, x, y) + bnd(x, y, x + 42, y)
        + lbl(x - 42, y, 'R') + lbl(x, y, 'O') + lbl(x + 42, y, 'R′'));
    put(2, 0, '할로젠화알킬  R–X', '클로로메테인', (x, y) => bnd(x - 24, y, x + 24, y)
        + lbl(x - 24, y, 'R') + lbl(x + 24, y, 'X') + txt(x, y - 32, 'X = F, Cl, Br, I', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    put(0, 1, '알데하이드  R–CHO', '에탄알', (x, y) => bnd(x - 40, y, x, y) + bnd(x, y, x + 40, y) + dblO(x, y)
        + lbl(x - 40, y, 'R') + lbl(x, y, 'C') + lbl(x + 40, y, 'H'));
    put(1, 1, '케톤  R–CO–R′', '프로판온', (x, y) => bnd(x - 42, y, x, y) + bnd(x, y, x + 42, y) + dblO(x, y)
        + lbl(x - 42, y, 'R') + lbl(x, y, 'C') + lbl(x + 42, y, 'R′'));
    put(2, 1, '아민  R–NH₂', '메틸아민', (x, y) => bnd(x - 40, y, x, y) + bnd(x, y, x + 40, y) + bnd(x, y, x, y - 32)
        + lbl(x - 40, y, 'R') + lbl(x, y, 'N') + lbl(x + 40, y, 'H') + lbl(x, y - 32, 'H'));
    put(0, 2, '카복실산  R–COOH', '에탄산(아세트산)', (x, y) => bnd(x - 64, y, x - 24, y) + bnd(x - 24, y, x + 20, y)
        + bnd(x + 20, y, x + 60, y) + dblO(x - 24, y)
        + lbl(x - 64, y, 'R') + lbl(x - 24, y, 'C') + lbl(x + 20, y, 'O') + lbl(x + 60, y, 'H'));
    put(1, 2, '에스터  R–COO–R′', '에틸에탄오에이트', (x, y) => bnd(x - 66, y, x - 26, y) + bnd(x - 26, y, x + 18, y)
        + bnd(x + 18, y, x + 62, y) + dblO(x - 26, y)
        + lbl(x - 66, y, 'R') + lbl(x - 26, y, 'C') + lbl(x + 18, y, 'O') + lbl(x + 62, y, 'R′'));
    put(2, 2, '아마이드  R–CONH₂', '펩타이드 결합의 골격', (x, y) => bnd(x - 64, y, x - 24, y) + bnd(x - 24, y, x + 20, y)
        + bnd(x + 20, y, x + 60, y) + bnd(x + 20, y, x + 20, y - 32) + dblO(x - 24, y)
        + lbl(x - 64, y, 'R') + lbl(x - 24, y, 'C') + lbl(x + 20, y, 'N') + lbl(x + 60, y, 'H') + lbl(x + 20, y - 32, 'H'));

    g.push(txt(20, 32, '작용기 — 분자의 성질을 정하는 부분. R 은 나머지 탄소 골격이다', { cls: 'ink bold' }));
    g.push(txt(W - 14, H - 10, '탄소 골격이 아무리 길어져도 작용기가 같으면 성질과 반응이 닮는다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-org-functional-groups',
        title: '주요 작용기의 구조',
        desc: '유기 화합물의 성질은 탄소 골격보다 거기 붙은 작용기가 정한다. 산소 하나가 어디에 어떻게 붙었느냐로 '
            + '알코올·에터·알데하이드·케톤·카복실산·에스터가 갈린다. R 은 나머지 탄소 골격을 뭉뚱그린 기호이고, '
            + '골격이 길어져도 작용기가 같으면 반응은 닮는다.',
        svg: svg({ width: W, height: H, title: '작용기 표', desc: '아홉 가지 작용기의 구조식', body: g.join('') }),
    };
})());

/* 16-4. 거울상 이성질체 */
add((() => {
    const W = 800, H = 380;
    const g = [];
    const draw = (cx, cy, flip) => {
        const s = flip ? -1 : 1;
        const out = [
            bnd(cx, cy, cx, cy - 58),
            bnd(cx, cy, cx - 56 * s, cy + 24),
            wedge(cx, cy, cx + 40 * s, cy + 54),
            hashb(cx, cy, cx - 10 * s, cy + 62),
            lbl(cx, cy, 'C', { cls: 'ink bold' }),
            lbl(cx, cy - 58, 'COOH'),
            lbl(cx - 56 * s, cy + 24, 'CH₃'),
            lbl(cx + 40 * s, cy + 54, 'OH'),
            lbl(cx - 10 * s, cy + 62, 'H'),
        ];
        return out.join('');
    };
    g.push(draw(190, 170, false));
    g.push(draw(510, 170, true));
    g.push(line([[350, 66], [350, 300]], { stroke: 'var(--s3)', sw: 2, dash: '8 5' }));
    g.push(txt(350, 58, '거울', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(190, 300, '한쪽', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(510, 300, '거울에 비친 쪽', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(20, 32, '탄소 하나에 서로 다른 네 개가 붙으면 거울상 짝이 생긴다', { cls: 'ink bold' }));
    g.push(txt(20, H - 52, '두 분자는 결합 순서가 완전히 같은데도 아무리 돌리고 뒤집어도 포갤 수 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 32, '왼손과 오른손이 그런 관계다. 이런 탄소를 카이랄 중심이라 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 12, '보통의 성질은 똑같지만, 생체 분자와 만나면 한쪽만 맞는다. 약에서 결정적인 차이가 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-org-chirality',
        title: '거울상 이성질체',
        desc: '탄소 하나에 서로 다른 네 개의 원자단이 붙으면 그 분자와 거울에 비친 상은 아무리 돌려도 포갤 수 없다. '
            + '왼손과 오른손의 관계와 같다. 끓는점이나 밀도 같은 보통의 성질은 서로 같지만, 생체 분자와 결합할 때는 '
            + '한쪽만 맞는다. 같은 약이라도 두 거울상의 작용이 완전히 다를 수 있는 이유다.',
        svg: svg({ width: W, height: H, title: '거울상 이성질체', desc: '거울면을 사이에 둔 두 카이랄 탄소', body: g.join('') }),
    };
})());

/* 16-5. 시스-트랜스 이성질체 */
add((() => {
    const W = 700, H = 350;
    const g = [];
    const alkene = (cx, cy, same) => {
        const out = [bnd(cx - 28, cy, cx + 28, cy, 2)];
        out.push(lbl(cx - 28, cy, 'C', { cls: 'ink bold' }));
        out.push(lbl(cx + 28, cy, 'C', { cls: 'ink bold' }));
        const L = [[cx - 70, cy - 40, 'CH₃'], [cx - 70, cy + 40, 'H']];
        const Rt = same ? [[cx + 70, cy - 40, 'CH₃'], [cx + 70, cy + 40, 'H']]
            : [[cx + 70, cy - 40, 'H'], [cx + 70, cy + 40, 'CH₃']];
        for (const [x, y, t] of [...L, ...Rt]) {
            out.push(bnd(x < cx ? cx - 28 : cx + 28, cy, x, y, 1, 15));
            out.push(lbl(x, y, t, { size: 'sm' }));
        }
        return out.join('');
    };
    g.push(panel(20, 56, 320, 200, '시스 (cis)', { sub: '같은 것이 같은 쪽에' }));
    g.push(alkene(180, 168, true));
    g.push(panel(360, 56, 320, 200, '트랜스 (trans)', { sub: '같은 것이 반대쪽에' }));
    g.push(alkene(520, 168, false));
    g.push(txt(20, 32, '이중결합은 축을 돌 수 없다. 그래서 두 가지가 따로 존재한다', { cls: 'ink bold' }));
    g.push(txt(20, 292, '단일결합이라면 축을 자유롭게 돌 수 있어 둘이 같은 물질이 된다. 이중결합은 π 결합이 회전을 막는다.',
        { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 312, '두 분자는 끓는점도 쌍극자 모멘트도 다르다. 서로 다른 물질이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 332, '고리 화합물에서도 고리가 회전을 막으므로 같은 종류의 이성질체가 생긴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-org-cis-trans',
        title: '시스-트랜스 이성질체',
        desc: '이중결합의 π 결합은 두 탄소가 축을 도는 것을 막는다. 그래서 치환기가 같은 쪽에 있는 것과 반대쪽에 '
            + '있는 것이 서로 다른 물질로 따로 존재한다. 단일결합이었다면 돌려서 겹칠 수 있으므로 같은 물질이다. '
            + '두 이성질체는 끓는점도 쌍극자 모멘트도 다르다.',
        svg: svg({ width: W, height: H, title: '시스-트랜스 이성질체', desc: '2-뷰텐의 시스형과 트랜스형', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 17장 — 핵화학 개요
 * ================================================================== */

/* 17-1. 붕괴 곡선과 반감기 */
add((() => {
    const W = 700, H = 380;
    const f = frame({ xRange: [0, 5.2], yRange: [0, 1.08], box: { x: 84, y: 76, w: 390, h: 226 } });
    const g = [
        f.axes({ xLabel: '지난 반감기 횟수', yLabel: '남은 비율', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 0.25, 0.5, 0.75, 1] }),
        f.curve(t => 2 ** -t, { cls: 's1' }),
    ];
    const frac = ['1', '1/2', '1/4', '1/8', '1/16', '1/32'];
    for (let k = 0; k <= 5; k += 1) {
        const v = 2 ** -k;
        g.push(f.guide([k, 0], [k, v]));
        g.push(f.guide([0, v], [k, v]));
        g.push(f.dot([k, v], { cls: 'f2', r: 4.5 }));
        if (k <= 4) g.push(txt(f.X(k) + 8, f.Y(v) - 8, frac[k], { cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(490, 94, '반감기 한 번마다 절반', { cls: 'ink bold' }));
    g.push(txt(490, 120, 'n 번 지나면 남는 양은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 138, '처음의 2ⁿ 분의 1 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 164, '3번 → 1/8 = 12.5 %', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 182, '10번 → 약 0.1 %', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 208, '반감기 횟수가 정수가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 226, '아니면 로그가 필요하다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(490, 254, 'N = N₀ e^(−λt)', { cls: 'ink bold' }));
    g.push(txt(490, 274, 't~{1/2} = ln2 / λ', { cls: 'ink bold' }));
    g.push(txt(20, 32, '방사성 붕괴는 반감기를 셀 줄 알면 대부분 풀린다', { cls: 'ink bold' }));
    g.push(txt(20, H - 32, '붕괴 확률은 핵이 얼마나 오래되었는지와 무관하다. 그래서 남은 비율만 시간의 함수가 된다.',
        { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 12, '온도·압력·화학 결합 상태를 바꿔도 반감기는 바뀌지 않는다. 화학 반응과 결정적으로 다른 점이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-nuc-decay-curve',
        title: '붕괴 곡선과 반감기',
        desc: '방사성 핵은 반감기가 한 번 지날 때마다 정확히 절반으로 준다. 반감기를 n 번 지나면 남는 양은 처음의 '
            + '2의 n 제곱분의 1 이므로, 횟수가 정수일 때는 로그 없이도 답이 나온다. 온도나 화학 결합 상태를 바꿔도 '
            + '반감기는 변하지 않는다.',
        svg: svg({ width: W, height: H, title: '붕괴 곡선', desc: '반감기 횟수에 따른 남은 비율', body: g.join('') }),
    };
})());

/* 17-2. 안정선과 붕괴 방식 */
add((() => {
    const W = 660, H = 440;
    const Nc = Z => Z + 0.0065 * Z * Z;
    const f = frame({ xRange: [0, 100], yRange: [0, 155], box: { x: 78, y: 78, w: 400, h: 282 } });
    const g = [f.axes({ xLabel: '양성자 수 Z', yLabel: '중성자 수 N', xTicks: [0, 20, 40, 60, 80, 100], yTicks: [0, 50, 100, 150] })];
    // 안정 띠
    const up = [], dn = [];
    for (let z = 0; z <= 92; z += 2) {
        const w = 1.4 + 0.045 * z;
        up.push([f.X(z), f.Y(Nc(z) + w)]);
        dn.push([f.X(z), f.Y(Nc(z) - w)]);
    }
    g.push(poly([...up, ...dn.reverse()], { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1 }));
    g.push(f.line([[0, 0], [100, 100]], { cls: 's3', dash: '6 4' }));
    g.push(txt(f.X(76), f.Y(58), 'N = Z 선', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(70), f.Y(Nc(70)) - 14, '안정한 핵이 모인 띠', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    // 붕괴 화살표
    const arrow = (z, n, dz, dn2, cls, mk) => px(f.X(z), f.Y(n), f.X(z + dz), f.Y(n + dn2), { cls, marker: mk, width: 2.2 });
    g.push(f.dot([30, 62], { cls: 'f2', r: 4 }));
    g.push(arrow(30, 62, 7, -7, 's2', 'ar2'));
    g.push(txt(f.X(28), f.Y(72), '중성자가 많다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(28), f.Y(63), 'β⁻ 붕괴', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.dot([46, 38], { cls: 'f2', r: 4 }));
    g.push(arrow(46, 38, -7, 7, 's2', 'ar2'));
    g.push(txt(f.X(49), f.Y(40), '양성자가 많다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(49), f.Y(31), 'β⁺ 붕괴 · 전자 포획', { cls: 'ink bold', size: 'sm' }));
    g.push(f.dot([90, Nc(90)], { cls: 'f2', r: 4 }));
    g.push(arrow(90, Nc(90), -9, -9, 's3', 'ar3'));
    g.push(txt(f.X(88), f.Y(Nc(90) + 12), '너무 무겁다 → α 붕괴', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([83, 0], [83, 140]));
    g.push(txt(f.X(97), f.Y(14), 'Z = 83 오른쪽에는', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(97), f.Y(5), '안정한 핵이 하나도 없다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '핵이 안정하려면 중성자와 양성자의 비가 맞아야 한다', { cls: 'ink bold' }));
    g.push(txt(20, H - 32, '가벼운 핵은 N ≒ Z 일 때 안정하지만, 무거워질수록 양성자끼리 밀어내는 힘이 커져',
        { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 12, '더 많은 중성자가 필요해진다. 띠에서 벗어난 핵은 벗어난 방향에 맞는 붕괴로 띠 쪽으로 돌아온다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-nuc-stability-band',
        title: '안정선과 붕괴 방식',
        desc: '가로축에 양성자 수, 세로축에 중성자 수를 놓으면 안정한 핵들이 좁은 띠를 이룬다. 가벼운 핵은 두 수가 '
            + '비슷할 때 안정하지만 무거워질수록 중성자가 더 필요해진다. 띠 위쪽(중성자 과잉)은 β⁻ 붕괴로, '
            + '아래쪽(양성자 과잉)은 β⁺ 붕괴나 전자 포획으로, 너무 무거운 핵은 α 붕괴로 띠를 향해 움직인다.',
        svg: svg({ width: W, height: H, title: '안정선', desc: 'N-Z 평면의 안정 띠와 붕괴 화살표', body: g.join('') }),
    };
})());

/* 17-3. 핵자당 결합에너지 곡선 */
add((() => {
    const W = 700, H = 380;
    const data = [
        [2, 1.112], [3, 2.573], [4, 7.074], [6, 5.332], [7, 5.606], [9, 6.463],
        [12, 7.680], [14, 7.476], [16, 7.976], [20, 8.032], [24, 8.261], [28, 8.448],
        [32, 8.493], [40, 8.551], [56, 8.790], [62, 8.795], [84, 8.717], [107, 8.554],
        [120, 8.504], [140, 8.376], [160, 8.183], [184, 8.025], [208, 7.867],
        [235, 7.591], [238, 7.570],
    ];
    const f = frame({ xRange: [0, 250], yRange: [0, 9.6], box: { x: 84, y: 74, w: 390, h: 226 } });
    const g = [
        f.axes({ xLabel: '질량수 A', yLabel: 'MeV', xTicks: [0, 50, 100, 150, 200, 250], yTicks: [0, 2, 4, 6, 8] }),
        f.line(data, { cls: 's1' }),
    ];
    for (const [a, b] of data) g.push(f.dot([a, b], { cls: 'f1', r: 2.6 }));
    for (const [a, b, name, dx, dy] of [[56, 8.790, '⁵⁶Fe', 0, -12], [238, 7.570, '²³⁸U', -14, 18]]) {
        g.push(txt(f.X(a) + dx, f.Y(b) + dy, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(f.guide([4, 7.074], [34, 5.4]));
    g.push(txt(f.X(36), f.Y(5.2), '⁴He', { cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([2, 1.112], [26, 2.2]));
    g.push(txt(f.X(28), f.Y(2.0), '²H — 가장 헐겁다', { cls: 'ink2', size: 'sm' }));
    g.push(px(f.X(30), f.Y(3.9), f.X(52), f.Y(7.6), { cls: 's3', marker: 'ar3', width: 2.2 }));
    g.push(txt(f.X(30), f.Y(3.4), '핵융합', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(px(f.X(226), f.Y(6.0), f.X(126), f.Y(7.4), { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(f.X(232), f.Y(5.2), '핵분열', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([56, 0], [56, 8.79]));
    const cx2 = 516;
    g.push(txt(cx2, 96, '세로축은 핵자 하나당 결합에너지다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 114, '클수록 단단히 묶여 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 148, '봉우리는 질량수 56 부터 62', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx2, 166, '(철·니켈) 근처, 약 8.8 MeV', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 200, '봉우리 왼쪽에서는 합쳐야,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 218, '오른쪽에서는 쪼개야', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 236, '에너지가 나온다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx2, 270, '철보다 무거운 원소는 별의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx2, 288, '평범한 연소로 만들어지지 않는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 32, '핵자 하나당 결합에너지 — 어느 쪽으로 가야 에너지가 나오는가', { cls: 'ink bold' }));
    g.push(txt(20, H - 12, '양쪽 끝에서 가운데로 가는 변화만 에너지를 내놓는다. 그 가운데가 철이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-nuc-binding-energy',
        title: '핵자당 결합에너지 곡선',
        desc: '핵자 하나당 결합에너지는 질량수 56에서 62 근처(철과 니켈)에서 가장 크고, 그보다 가볍거나 무거우면 '
            + '작아진다. 가벼운 핵을 합치거나(핵융합) 무거운 핵을 쪼개면(핵분열) 봉우리 쪽으로 올라가면서 '
            + '그 차이만큼 에너지가 나온다. 철은 어느 쪽으로도 갈 수 없는 종착점이다.',
        svg: svg({ width: W, height: H, title: '핵결합에너지 곡선', desc: '질량수에 따른 핵자당 결합에너지', body: g.join('') }),
    };
})());

/* 17-4. 핵분열과 핵융합 */
add((() => {
    const W = 820, H = 400;
    const g = [];
    const nucleus = (x, y, r, t, cls) => circ(x, y, r, { fill: `var(--${cls})`, op: 0.28, stroke: `var(--${cls})`, sw: 1.5 })
        + txt(x, y + 5, t, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    const neutron = (x, y) => circ(x, y, 6, { fill: 'var(--ink2)', op: 0.7, stroke: 'none', sw: 0 });

    g.push(panel(20, 56, 780, 168, '핵분열 — 무거운 핵이 쪼개진다'));
    g.push(neutron(70, 150));
    g.push(px(80, 150, 118, 150, { cls: 'ax', marker: 'ark', width: 1.8 }));
    g.push(nucleus(160, 150, 32, '²³⁵U', 's1'));
    g.push(txt(212, 155, '→', { anchor: 'middle', cls: 'ink bold' }));
    g.push(nucleus(276, 118, 24, '¹⁴¹Ba', 's3'));
    g.push(nucleus(276, 186, 22, '⁹²Kr', 's3'));
    for (const [x, y] of [[344, 116], [344, 152], [344, 188]]) g.push(neutron(x, y));
    g.push(txt(370, 155, '중성자 3개', { cls: 'ink2', size: 'sm' }));
    g.push(px(444, 152, 480, 152, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt(462, 138, '연쇄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let k = 0; k < 3; k += 1) {
        const y = 112 + k * 40;
        g.push(nucleus(516, y, 15, 'U', 's1'));
        g.push(px(534, y, 560, y, { cls: 'ax', marker: 'ark', width: 1.4 }));
        for (let j = 0; j < 3; j += 1) g.push(neutron(578 + j * 16, y));
    }
    g.push(txt(644, 118, '중성자 하나가 셋을 만들면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(644, 136, '반응이 스스로 이어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(644, 166, '이 배수를 1 로 맞춰 붙든', { cls: 'ink2', size: 'sm' }));
    g.push(txt(644, 184, '것이 원자로다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(20, 240, 780, 138, '핵융합 — 가벼운 핵이 합쳐진다'));
    g.push(nucleus(120, 312, 26, '²H', 's1'));
    g.push(txt(172, 317, '+', { anchor: 'middle', cls: 'ink bold' }));
    g.push(nucleus(224, 312, 26, '³H', 's1'));
    g.push(txt(280, 317, '→', { anchor: 'middle', cls: 'ink bold' }));
    g.push(nucleus(340, 312, 28, '⁴He', 's3'));
    g.push(txt(388, 317, '+', { anchor: 'middle', cls: 'ink bold' }));
    g.push(neutron(420, 312));
    g.push(txt(438, 317, '중성자', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 288, '두 핵이 서로 밀어내는 전기력을 이기고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 306, '닿을 만큼 가까워져야 하므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 324, '아주 높은 온도가 필요하다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 348, '별의 중심에서 일어나는 일이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 32, '두 길 모두 결합에너지 봉우리 쪽으로 가는 변화다', { cls: 'ink bold' }));
    return {
        name: 'chem-nuc-fission-fusion',
        title: '핵분열과 핵융합',
        desc: '핵분열은 중성자를 맞은 무거운 핵이 두 조각으로 쪼개지며 중성자를 여럿 내놓는 반응이다. '
            + '그 중성자가 다시 다른 핵을 때려 연쇄 반응이 된다. 핵융합은 가벼운 핵 둘이 합쳐지는 반응인데, '
            + '양전하끼리 밀어내는 힘을 이겨야 해서 아주 높은 온도가 필요하다. 두 길 모두 핵자당 결합에너지가 '
            + '큰 쪽으로 가는 변화이고, 그 차이가 에너지로 나온다.',
        svg: svg({ width: W, height: H, title: '핵분열과 핵융합', desc: '연쇄 반응 도식과 중수소-삼중수소 융합', body: g.join('') }),
    };
})());

export default figures;
