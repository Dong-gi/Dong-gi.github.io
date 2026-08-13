/**
 * 10장 열과 열역학, 11장 전기의 그림.
 *
 * physics.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를 돌려주고
 * name 이 파일 이름(/figures/physics/<name>.svg)이 된다.
 * 이름은 heat- / elec- 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 라벨은 유니코드 그리스 문자와 `v~0` 꼴의 아래첨자 표기로 적는다.
 */
import { svg, frame, arc, px, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

/** 화소 좌표 다각형(채움). CSS 클래스는 fill:none 을 강제하므로 속성으로 직접 준다. */
function poly(pts, { fill = 'var(--s1)', op = 0.14, stroke = 'none', sw = 1, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 꺾은선. */
function line(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 회로 배선. 소자가 놓인 자리는 끊어서 그린다(선이 소자를 관통하지 않게). */
const wire = pts => line(pts, { sw: 1.8, cap: 'butt' });

/** 저항 기호. 가로/세로 두 방향. 소자 길이는 양쪽 50, 두께 20 으로 고정한다. */
function resistor(cx, cy, label, { horizontal = true } = {}) {
    const [w, h] = horizontal ? [50, 20] : [20, 50];
    return box(cx - w / 2, cy - h / 2, w, h, { sw: 1.6 })
        + txt(horizontal ? cx : cx + 18, horizontal ? cy - 18 : cy + 4, label,
            { anchor: horizontal ? 'middle' : 'start', cls: 'ink', size: 'sm' });
}

/** 세로 배선에 놓는 전지 기호. 긴 판이 +, 짧고 두꺼운 판이 −. */
function battery(cx, cy) {
    return line([[cx - 17, cy - 7], [cx + 17, cy - 7]], { sw: 2, cap: 'butt' })
        + line([[cx - 9, cy + 7], [cx + 9, cy + 7]], { sw: 4.5, cap: 'butt' })
        + txt(cx + 24, cy - 4, '+', { cls: 'ink2', size: 'sm' })
        + txt(cx + 24, cy + 16, '−', { cls: 'ink2', size: 'sm' });
}

/** 여러 점전하가 만드는 전기장(화소 좌표에서의 방향만 쓴다). */
function fieldAt(charges, p) {
    let ex = 0, ey = 0;
    for (const c of charges) {
        const dx = p[0] - c.p[0], dy = p[1] - c.p[1];
        const r = Math.hypot(dx, dy);
        if (r < 1e-6) continue;
        ex += (c.q * dx) / (r * r * r);
        ey += (c.q * dy) / (r * r * r);
    }
    return [ex, ey];
}

/** 전기력선 한 가닥. 장의 방향을 따라 조금씩 나아가며 점을 모은다. */
function streamline(charges, start, { h = 3, steps = 420, bounds, stopR = 11 } = {}) {
    const pts = [start];
    let p = start;
    for (let i = 0; i < steps; i += 1) {
        const [ex, ey] = fieldAt(charges, p);
        const m = Math.hypot(ex, ey);
        if (!Number.isFinite(m) || m === 0) break;
        const q = [p[0] + (h * ex) / m, p[1] + (h * ey) / m];
        pts.push(q);
        p = q;
        if (bounds && (p[0] < bounds[0] || p[0] > bounds[2] || p[1] < bounds[1] || p[1] > bounds[3])) break;
        if (charges.some(c => Math.hypot(p[0] - c.p[0], p[1] - c.p[1]) < stopR)) break;
    }
    return pts;
}

/** 선 도중에 진행 방향 화살표를 하나 얹는다. */
function midArrow(pts, frac = 0.4, cls = 's1', marker = 'ar1') {
    const i = Math.max(1, Math.min(pts.length - 2, Math.floor(pts.length * frac)));
    const a = pts[i], b = pts[i + 1];
    const m = Math.hypot(b[0] - a[0], b[1] - a[1]) || 1;
    return px(a[0], a[1], a[0] + ((b[0] - a[0]) / m) * 9, a[1] + ((b[1] - a[1]) / m) * 9,
        { cls, marker, width: 1.8 });
}

/* ================================================================== *
 * 10장 — 열과 열역학
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 열평형 — 맞닿아 두면 온도가 하나로 모인다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 330;
    const g = frame({ xRange: [0, 10], yRange: [0, 100], box: { x: 360, y: 78, w: 200, h: 180 } });
    const Th = t => 50 + 30 * Math.exp(-t / 2.2);
    const Tc = t => 50 - 30 * Math.exp(-t / 2.2);
    const b = [
        txt(36, 40, '① 맞닿게 두면', { cls: 'ink bold' }),
        txt(340, 40, '② 시간에 따른 온도', { cls: 'ink bold' }),
        box(40, 118, 100, 80, { fill: 'var(--s2)', op: 0.18, stroke: 'var(--s2)' }),
        box(140, 118, 100, 80, { fill: 'var(--s1)', op: 0.18, stroke: 'var(--s1)' }),
        txt(90, 108, '뜨거운 쪽', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(190, 108, '찬 쪽', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(90, 164, '80 °C', { anchor: 'middle', cls: 'ink' }),
        txt(190, 164, '20 °C', { anchor: 'middle', cls: 'ink' }),
        px(112, 224, 168, 224, { cls: 's2', marker: 'ar2', width: 2.2 }),
        txt(140, 248, '열은 뜨거운 쪽에서 찬 쪽으로만', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(140, 268, '저절로 흐른다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(140, 294, '온도가 같아지면 멈춘다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.axes({ xLabel: 't', yLabel: 'T (°C)', xTicks: [0], yTicks: [0, 20, 50, 80] }),
        g.curve(Th, { cls: 's2' }),
        g.curve(Tc, { cls: 's1' }),
        g.guide([0, 50], [10, 50]),
        g.label([9.6, 50], '열평형', { dy: -9, anchor: 'end', cls: 'ink' }),
        g.label([9.6, 50], '같은 온도', { dy: 15, anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 12, H - 12, '열평형이면 온도가 같다 — 이것이 온도를 정의한다(제0법칙)',
            { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-thermal-equilibrium',
        title: '열평형',
        desc: '뜨거운 물체와 찬 물체를 맞대면 열이 뜨거운 쪽에서 찬 쪽으로 흐르고, '
            + '두 온도가 하나의 값으로 모인 뒤에는 더 변하지 않는다. 이 상태가 열평형이다.',
        svg: svg({ width: W, height: H, title: '열평형에 이르는 두 물체', desc: '온도가 하나로 모이는 과정', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 켈빈과 섭씨 — 눈금 간격은 같고 0으로 삼은 자리만 다르다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 320;
    const lo = -40, hi = 420;
    const X = v => r2(90 + ((v - lo) / (hi - lo)) * 500);
    const A = 150;
    const marks = [
        { K: '0', C: '−273.15', v: 0, name: '절대영도', up: true, col: 's2' },
        { K: '273.15', C: '0', v: 273.15, name: '물이 언다', up: false, col: 's1' },
        { K: '310', C: '약 37', v: 310, name: '사람 체온', up: true, col: 's3' },
        { K: '373.15', C: '100', v: 373.15, name: '물이 끓는다', up: false, col: 's1' },
    ];
    const minor = [];
    for (let v = 0; v <= 420; v += 20) minor.push(line([[X(v), A - 5], [X(v), A + 5]], { sw: 1 }));
    const b = [
        txt(36, 40, '같은 자를 두 곳에서 0으로 삼은 것뿐이다', { cls: 'ink bold' }),
        txt(36, 62, '눈금 한 칸의 크기가 같다. 그래서 온도차 ΔT 는 K 로 재나 °C 로 재나 같은 수다',
            { cls: 'ink2', size: 'sm' }),
        poly([[X(lo), A - 12], [X(0), A - 12], [X(0), A + 12], [X(lo), A + 12]], { fill: 'var(--ink2)', op: 0.12 }),
        line([[X(lo), A], [X(hi), A]], { sw: 1.8 }),
        ...minor,
        txt(78, A - 8, 'K', { anchor: 'end', cls: 'ink bold' }),
        txt(78, A + 24, '°C', { anchor: 'end', cls: 'ink bold' }),
        ...marks.flatMap(m => {
            const y = m.up ? A - 44 : A + 60;
            return [
                line([[X(m.v), m.up ? A - 16 : A + 30], [X(m.v), m.up ? A - 38 : A + 50]],
                    { stroke: 'var(--grid)', sw: 1, dash: '4 3' }),
                `<circle cx="${X(m.v)}" cy="${A}" r="4.5" fill="var(--${m.col})"/>`,
                txt(X(m.v), A - 8, m.K, { anchor: 'middle', cls: 'ink', size: 'sm' }),
                txt(X(m.v), A + 24, m.C, { anchor: 'middle', cls: 'ink', size: 'sm' }),
                txt(X(m.v), y, m.name, { anchor: 'middle', cls: 'ink' }),
            ];
        }),
        txt(X(-20) + 6, A + 60, '이보다 낮은 온도는 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(X(273.15), A + 96, X(373.15), A + 96, { cls: 's3', marker: 'ar3', width: 1.5 }),
        px(X(373.15), A + 96, X(273.15), A + 96, { cls: 's3', marker: 'ar3', width: 1.5 }),
        txt((X(273.15) + X(373.15)) / 2, A + 118, '100 K 차이 = 100 °C 차이', { anchor: 'middle', cls: 'ink' }),
        txt(W - 12, H - 14, 'T(K) = T(°C) + 273.15', { anchor: 'end', cls: 'ink bold' }),
    ].join('');
    return {
        name: 'heat-temperature-scales',
        title: '켈빈 눈금과 섭씨 눈금',
        desc: '켈빈과 섭씨는 눈금 한 칸의 크기가 같고 0으로 삼은 자리만 273.15 만큼 다르다. '
            + '그래서 온도차는 두 눈금에서 같은 수가 되고 온도 자체는 273.15 만큼 어긋난다. '
            + '0 K 보다 낮은 온도는 없다.',
        svg: svg({ width: W, height: H, title: '켈빈과 섭씨 눈금 비교', desc: '0 K 는 −273.15 °C', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 열팽창 — 구멍도 함께 커진다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const s = 1.22;
    const w0 = 160, h0 = 96;
    const c1 = [150, 158], c2 = [420, 158];
    const b = [
        txt(36, 40, '① 데우기 전', { cls: 'ink bold' }),
        txt(310, 40, '② 데운 뒤 (팽창을 과장해 그렸다)', { cls: 'ink bold' }),
        box(c1[0] - w0 / 2, c1[1] - h0 / 2, w0, h0, { fill: 'var(--s1)', op: 0.14, stroke: 'var(--s1)' }),
        circ(c1[0], c1[1], 26, { fill: 'var(--ink)', op: 0.06, stroke: 'var(--s1)' }),
        txt(c1[0], c1[1] + 5, '구멍', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        box(c2[0] - w0 / 2, c2[1] - h0 / 2, w0, h0, { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }),
        circ(c2[0], c2[1], 26, { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }),
        box(c2[0] - (w0 * s) / 2, c2[1] - (h0 * s) / 2, w0 * s, h0 * s, { fill: 'var(--s2)', op: 0.14, stroke: 'var(--s2)' }),
        circ(c2[0], c2[1], 26 * s, { fill: 'var(--ink)', op: 0.06, stroke: 'var(--s2)' }),
        px(c2[0] + 27, c2[1], c2[0] + 26 * s + 8, c2[1], { cls: 's2', marker: 'ar2', width: 1.6 }),
        px(c2[0] + w0 / 2 + 2, c2[1] - 40, c2[0] + (w0 * s) / 2 + 8, c2[1] - 40, { cls: 's2', marker: 'ar2', width: 1.6 }),
        txt(c2[0], c2[1] + (h0 * s) / 2 + 26, '구멍도 판과 같은 비율로 커진다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(c1[0], c1[1] + h0 / 2 + 26, '사진을 확대하듯 모두 같은 비율', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(36, H - 40, 'ΔL = α L ΔT   (길이),    ΔV ≈ 3α V ΔT   (부피)', { cls: 'ink bold' }),
        txt(36, H - 18, 'α 는 물질마다 정해진 값이다. 데우면 구멍이 좁아진다고 착각하기 쉽다',
            { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-thermal-expansion',
        title: '열팽창과 구멍',
        desc: '금속판을 데우면 판 전체가 사진을 확대하듯 같은 비율로 커진다. '
            + '그래서 판에 뚫린 구멍도 좁아지지 않고 같은 비율로 커진다.',
        svg: svg({ width: W, height: H, title: '열팽창 — 구멍도 함께 커진다', desc: '데우기 전과 후의 금속판', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 물 1 kg 의 가열 곡선 — 상변화 중에는 온도가 멈춘다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 340;
    const pts = [[0, -20], [42, 0], [376, 0], [794.6, 100], [3054.6, 100], [3094.8, 120]];
    const g = frame({ xRange: [0, 3300], yRange: [-45, 145], box: { x: 62, y: 36, w: 480, h: 230 } });
    const ticks = [1000, 2000, 3000];
    const b = [
        g.axes({ xLabel: 'Q (kJ)', yLabel: 'T (°C)', xTicks: [], yTicks: [-20, 0, 50, 100] }),
        ...ticks.map(v => g.guide([v, -45], [v, 145])),
        ...ticks.map(v => txt(g.X(v), g.Y(-45) + 16, String(v), { anchor: 'middle', cls: 'ink2', size: 'sm' })),
        g.line(pts, { cls: 's1' }),
        g.line([[42, 0], [376, 0]], { cls: 's2' }),
        g.line([[794.6, 100], [3054.6, 100]], { cls: 's2' }),
        g.dot([42, 0], { cls: 'f2', r: 3 }),
        g.dot([376, 0], { cls: 'f2', r: 3 }),
        g.dot([794.6, 100], { cls: 'f2', r: 3 }),
        g.dot([3054.6, 100], { cls: 'f2', r: 3 }),
        g.label([430, -16], '녹는 중 334 kJ', { cls: 'ink', size: 'sm' }),
        g.label([1900, 100], '끓는 중 2260 kJ', { dy: -12, anchor: 'middle', cls: 'ink', size: 'sm' }),
        g.label([130, -34], '얼음', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([560, 55], '물', { dx: -16, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([3120, 118], '수증기', { dx: 4, dy: 4, cls: 'ink2', size: 'sm' }),
        txt(300, 24, '물 1 kg 을 −20 °C 얼음에서 120 °C 수증기까지', { anchor: 'middle', cls: 'ink bold' }),
        txt(W - 12, H - 34, '평평한 곳에서는 열을 넣어도 온도가 오르지 않는다', { anchor: 'end', cls: 'ink' }),
        txt(W - 12, H - 14, '들어간 열의 4분의 3이 마지막 기화 구간에 쓰인다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-phase-curve',
        title: '물의 가열 곡선과 잠열',
        desc: '얼음을 계속 데우면 온도가 오르다가 0 °C 와 100 °C 에서 멈춘다. '
            + '그동안 들어간 열은 온도가 아니라 상태를 바꾸는 데 쓰이며 이것이 잠열이다. '
            + '기화에 드는 열이 융해에 드는 열의 약 7배다.',
        svg: svg({ width: W, height: H, title: '물 1 kg 의 가열 곡선', desc: '두 번의 평평한 구간이 잠열', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 기체 분자의 충돌이 만드는 압력
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 320;
    const bx = 50, by = 56, bw = 300, bh = 220;
    let seed = 20260806;
    const rnd = () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);
    const mol = [];
    for (let i = 0; i < 14; i += 1) {
        const x = bx + 20 + rnd() * (bw * 0.58);
        const y = by + 20 + rnd() * (bh - 40);
        const a = rnd() * Math.PI * 2;
        const L = 15 + rnd() * 11;
        mol.push(`<circle cx="${r2(x)}" cy="${r2(y)}" r="4" fill="var(--ink2)"/>`);
        mol.push(px(x, y, x + L * Math.cos(a), y + L * Math.sin(a), { cls: 's1', marker: 'ar1', width: 1.4 }));
    }
    const hy = by + 150;
    const b = [
        box(bx, by, bw, bh, { stroke: 'var(--ink2)', sw: 2 }),
        ...mol,
        px(bx + 200, hy - 44, bx + bw - 10, hy - 4, { cls: 's2', marker: 'ar2', width: 2.4 }),
        px(bx + bw - 10, hy + 4, bx + 200, hy + 44, { cls: 's2', marker: 'ar2', width: 2.4, dash: '5 4' }),
        `<circle cx="${bx + bw - 8}" cy="${hy}" r="5" fill="var(--s2)"/>`,
        txt(bx + bw - 12, hy - 54, '들어올 때 mv', { anchor: 'end', cls: 'ink', size: 'sm' }),
        txt(bx + bw - 12, hy + 66, '되튈 때 −mv', { anchor: 'end', cls: 'ink', size: 'sm' }),
        txt(bx + bw / 2, by - 14, '분자는 쉬지 않고 돌아다니며 벽에 부딪힌다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(378, 76, '충돌 한 번이 벽에 주는 것', { cls: 'ink bold' }),
        txt(378, 98, '운동량 변화 2mv → 아주 작은 충격량', { cls: 'ink2', size: 'sm' }),
        txt(378, 132, '1초에 부딪히는 횟수가 어마어마하게 많다', { cls: 'ink2', size: 'sm' }),
        txt(378, 154, '그 평균이 우리가 재는 압력 P 다', { cls: 'ink' }),
        txt(378, 192, '온도를 올리면', { cls: 'ink bold' }),
        txt(378, 214, '분자가 빨라진다 → 더 세게, 더 자주 부딪힌다', { cls: 'ink2', size: 'sm' }),
        txt(378, 236, '→ 같은 부피라면 압력이 올라간다', { cls: 'ink2', size: 'sm' }),
        txt(378, 274, '온도는 분자 하나의', { cls: 'ink' }),
        txt(378, 294, '평균 운동에너지를 재는 눈금이다', { cls: 'ink' }),
    ].join('');
    return {
        name: 'heat-molecular-pressure',
        title: '기체 분자의 충돌과 압력',
        desc: '기체 분자는 제멋대로 돌아다니다 벽에 부딪혀 되튄다. 충돌 한 번의 운동량 변화는 아주 작지만 '
            + '수가 압도적으로 많아 평균이 일정한 압력으로 나타난다. 온도가 높으면 분자가 빨라져 압력이 커진다.',
        svg: svg({ width: W, height: H, title: '분자 충돌이 만드는 압력', desc: '상자 안 분자들과 벽에서의 되튐', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. PV 선도의 넓이가 일이다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 330;
    const g = frame({ xRange: [0, 5], yRange: [0, 7.4], box: { x: 62, y: 36, w: 320, h: 240 } });
    const f = v => 6 / v;
    const shade = [[1, 0], ...Array.from({ length: 41 }, (_, i) => {
        const v = 1 + (3 * i) / 40; return [v, f(v)];
    }), [4, 0]].map(p => [g.X(p[0]), g.Y(p[1])]);
    const b = [
        poly(shade, { fill: 'var(--s1)', op: 0.18 }),
        g.axes({ xLabel: 'V', yLabel: 'P', xTicks: [0], yTicks: [0] }),
        g.curve(f, { from: 1, to: 4, cls: 's1' }),
        g.dot([1, 6], { cls: 'f1' }),
        g.dot([4, 1.5], { cls: 'f1' }),
        g.label([1, 6], '처음', { dx: 6, dy: -8, cls: 'ink', size: 'sm' }),
        g.label([4, 1.5], '나중', { dx: 8, dy: 4, cls: 'ink', size: 'sm' }),
        g.label([2.3, 1.3], '넓이 = 기체가 한 일 W', { anchor: 'middle', cls: 'ink' }),
        g.guide([1, 0], [1, 6]),
        g.guide([4, 0], [4, 1.5]),
        g.line([[1, 6], [4, 6], [4, 1.5]], { cls: 's2', dash: '6 4' }),
        g.label([2.5, 6], '다른 경로', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(410, 66, '왜 넓이가 일인가', { cls: 'ink bold' }),
        txt(410, 88, '부피가 ΔV 만큼 늘 때 기체가 한 일은 P ΔV', { cls: 'ink2', size: 'sm' }),
        txt(410, 108, '= 가로 ΔV, 세로 P 인 가는 띠의 넓이', { cls: 'ink2', size: 'sm' }),
        txt(410, 128, '띠를 전부 더하면 곡선 아래 넓이가 된다', { cls: 'ink2', size: 'sm' }),
        txt(410, 168, '부피가 늘면 W 는 양수', { cls: 'ink', size: 'sm' }),
        txt(410, 188, '부피가 줄면 W 는 음수', { cls: 'ink', size: 'sm' }),
        txt(410, 208, '부피가 그대로면 W = 0', { cls: 'ink', size: 'sm' }),
        txt(410, 246, '처음과 나중이 같아도', { cls: 'ink bold' }),
        txt(410, 266, '지나온 길이 다르면 넓이가 다르다', { cls: 'ink2', size: 'sm' }),
        txt(410, 286, '→ 일은 상태가 아니라 과정에 딸린 양', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-pv-work-area',
        title: 'PV 선도의 넓이가 일이다',
        desc: 'P-V 선도에서 곡선 아래 넓이가 기체가 한 일이다. 부피 변화를 가는 띠로 쪼개면 '
            + '띠 하나의 넓이가 P ΔV 이기 때문이다. 처음과 나중이 같아도 경로가 다르면 넓이가 달라진다.',
        svg: svg({ width: W, height: H, title: 'PV 선도와 일', desc: '곡선 아래 넓이가 일', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 네 가지 과정을 한 점에서 출발시켜 비교
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 350;
    const g = frame({ xRange: [0, 5], yRange: [0, 4.4], box: { x: 62, y: 36, w: 350, h: 230 } });
    const V0 = 2, P0 = 3;
    const iso = v => (P0 * V0) / v;
    const adi = v => (P0 * V0 ** (5 / 3)) / v ** (5 / 3);
    const b = [
        g.axes({ xLabel: 'V', yLabel: 'P', xTicks: [0], yTicks: [0] }),
        g.line([[V0, P0], [4.4, P0]], { cls: 's3' }),
        g.curve(iso, { from: V0, to: 4.4, cls: 's1' }),
        g.curve(adi, { from: V0, to: 4.4, cls: 's2' }),
        g.line([[V0, P0], [V0, 0.9]], { cls: 's1', dash: '6 4' }),
        g.dot([V0, P0], { cls: 'f1', r: 4.5 }),
        g.label([V0, P0], '같은 출발점', { dx: -8, dy: -10, anchor: 'end', cls: 'ink', size: 'sm' }),
        g.label([4.4, P0], '등압 (P 일정)', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }),
        g.label([4.4, iso(4.4)], '등온 (T 일정)', { dx: 6, dy: 4, cls: 'ink', size: 'sm' }),
        g.label([4.4, adi(4.4)], '단열 (Q = 0)', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }),
        g.label([V0, 0.9], '등적 (V 일정)', { dx: -8, dy: 4, anchor: 'end', cls: 'ink', size: 'sm' }),
        txt(W - 12, H - 58, '단열 곡선이 등온 곡선보다 가파르다', { anchor: 'end', cls: 'ink' }),
        txt(W - 12, H - 36, '단열팽창에서는 부피가 느는 동시에 온도까지 떨어지기 때문이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 12, H - 14, '같은 부피까지 팽창해도 곡선 아래 넓이가 다르다 = 한 일이 다르다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-pv-processes',
        title: '등압·등온·단열·등적 과정',
        desc: '같은 상태에서 출발해도 어떤 조건으로 변화시키느냐에 따라 P-V 선도의 경로가 달라진다. '
            + '단열 곡선은 등온 곡선보다 가파르고, 곡선 아래 넓이가 다르므로 한 일도 다르다.',
        svg: svg({ width: W, height: H, title: '네 가지 과정의 PV 경로', desc: '등압·등온·단열·등적 비교', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 카르노 순환
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 360;
    const gam = 5 / 3;
    const A = [1, 6], B = [2, 3], C = [6, (B[1] * B[0] ** gam) / 6 ** gam];
    const D = [3, (C[0] * C[1]) / 3];
    const g = frame({ xRange: [0, 7], yRange: [0, 7], box: { x: 62, y: 40, w: 300, h: 250 } });
    const isoT = k => v => k / v;
    const adiK = k => v => k / v ** gam;
    const kAB = A[0] * A[1], kCD = C[0] * C[1];
    const kBC = B[1] * B[0] ** gam, kDA = A[1] * A[0] ** gam;
    const seg = (f, v1, v2) => Array.from({ length: 31 }, (_, i) => {
        const v = v1 + ((v2 - v1) * i) / 30; return [g.X(v), g.Y(f(v))];
    });
    const loop = [...seg(isoT(kAB), A[0], B[0]), ...seg(adiK(kBC), B[0], C[0]),
        ...seg(isoT(kCD), C[0], D[0]), ...seg(adiK(kDA), D[0], A[0])];
    const b = [
        poly(loop, { fill: 'var(--s1)', op: 0.16 }),
        g.axes({ xLabel: 'V', yLabel: 'P', xTicks: [0], yTicks: [0] }),
        g.curve(isoT(kAB), { from: A[0], to: B[0], cls: 's2' }),
        g.curve(adiK(kBC), { from: B[0], to: C[0], cls: 's3' }),
        g.curve(isoT(kCD), { from: D[0], to: C[0], cls: 's1' }),
        g.curve(adiK(kDA), { from: A[0], to: D[0], cls: 's3' }),
        g.vector([1.4, kAB / 1.4], [1.55, kAB / 1.55], { cls: 's2', marker: 'ar2', width: 2 }),
        g.vector([3.4, adiK(kBC)(3.4)], [3.7, adiK(kBC)(3.7)], { cls: 's3', marker: 'ar3', width: 2 }),
        g.vector([4.6, kCD / 4.6], [4.3, kCD / 4.3], { cls: 's1', marker: 'ar1', width: 2 }),
        g.vector([1.9, adiK(kDA)(1.9)], [1.7, adiK(kDA)(1.7)], { cls: 's3', marker: 'ar3', width: 2 }),
        ...[[A, 'A', -6], [B, 'B', -8], [C, 'C', 16], [D, 'D', 16]].map(([p, s, dy]) =>
            g.dot(p, { cls: 'f1' }) + g.label(p, s, { dx: 8, dy, cls: 'ink bold' })),
        g.guide([3.5, 3.2], [2.7, 1.5]),
        g.label([3.6, 3.4], '고리 안 넓이', { cls: 'ink', size: 'sm' }),
        g.label([3.6, 3.4], '= 한 바퀴에 얻는 알짜 일', { dy: 16, cls: 'ink', size: 'sm' }),
        txt(392, 68, 'A → B  등온팽창', { cls: 'ink bold' }),
        txt(392, 88, '고온 T~H 에서 열 Q~H 를 받는다', { cls: 'ink2', size: 'sm' }),
        txt(392, 122, 'B → C  단열팽창', { cls: 'ink bold' }),
        txt(392, 142, '열 출입 없이 T~H 에서 T~C 로 식는다', { cls: 'ink2', size: 'sm' }),
        txt(392, 176, 'C → D  등온압축', { cls: 'ink bold' }),
        txt(392, 196, '저온 T~C 로 열 Q~C 를 버린다', { cls: 'ink2', size: 'sm' }),
        txt(392, 230, 'D → A  단열압축', { cls: 'ink bold' }),
        txt(392, 250, '열 출입 없이 다시 T~H 가 된다', { cls: 'ink2', size: 'sm' }),
        txt(392, 292, '열을 주고받는 곳이 등온 구간뿐이라', { cls: 'ink', size: 'sm' }),
        txt(392, 312, '온도차 때문에 생기는 낭비가 없다', { cls: 'ink', size: 'sm' }),
        txt(392, 336, '→ 두 온도 사이의 최대 효율', { cls: 'ink bold', size: 'sm' }),
    ].join('');
    return {
        name: 'heat-carnot-cycle',
        title: '카르노 순환',
        desc: '등온팽창, 단열팽창, 등온압축, 단열압축 네 과정으로 이루어진 순환이다. '
            + '열을 주고받는 구간이 등온뿐이라 온도차로 인한 낭비가 없고, 고리 안 넓이가 한 바퀴에 얻는 알짜 일이다.',
        svg: svg({ width: W, height: H, title: '카르노 순환의 PV 선도', desc: '등온 두 개와 단열 두 개로 된 고리', body: b }),
    };
})());

/* ================================================================== *
 * 11장 — 전기
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 9. 같은 부호는 밀고 다른 부호는 당긴다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 250;
    const y = 128;
    const chg = (x, s, col) => circ(x, y, 22, { fill: `var(--${col})`, op: 0.2, stroke: `var(--${col})` })
        + txt(x, y + 7, s, { anchor: 'middle', cls: 'ink bold' });
    const b = [
        chg(120, '+', 's2'), chg(250, '+', 's2'),
        px(98, y, 58, y, { cls: 's1', marker: 'ar1', width: 2.4 }),
        px(272, y, 312, y, { cls: 's1', marker: 'ar1', width: 2.4 }),
        txt(185, 60, '같은 부호', { anchor: 'middle', cls: 'ink bold' }),
        txt(185, 82, '민다 (척력)', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        chg(420, '+', 's2'), chg(560, '−', 's1'),
        px(444, y, 478, y, { cls: 's1', marker: 'ar1', width: 2.4 }),
        px(536, y, 502, y, { cls: 's1', marker: 'ar1', width: 2.4 }),
        txt(490, 60, '다른 부호', { anchor: 'middle', cls: 'ink bold' }),
        txt(490, 82, '당긴다 (인력)', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(185, 196, '두 힘은 크기가 같고 방향이 반대다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(490, 196, '전하가 작은 쪽이 받는 힘도 같은 크기다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(W / 2, 230, '5장의 작용·반작용이 전기력에도 그대로 적용된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-charge-force',
        title: '전하 사이의 밀고 당김',
        desc: '같은 부호의 전하끼리는 서로 밀고 다른 부호끼리는 서로 당긴다. '
            + '두 전하가 주고받는 힘은 전하량이 서로 달라도 크기가 같고 방향이 반대다.',
        svg: svg({ width: W, height: H, title: '전기력의 방향', desc: '척력과 인력', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 10. 거리에 따른 쿨롱 힘 — 거꾸로 제곱
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 340;
    const g = frame({ xRange: [0, 4.4], yRange: [0, 1.2], box: { x: 62, y: 36, w: 370, h: 210 } });
    const f = r => 1 / (r * r);
    const marks = [[1, 1, '기준'], [2, 0.25, '1/4'], [3, 1 / 9, '1/9']];
    const b = [
        g.axes({ xLabel: 'r (기준 거리의 몇 배)', yLabel: 'F (기준 힘의 몇 배)', xTicks: [0, 1, 2, 3, 4], yTicks: [0, 0.25, 0.5, 1] }),
        g.curve(f, { from: 0.92, to: 4.3, cls: 's1' }),
        ...marks.flatMap(([r, y, s]) => [
            g.guide([r, 0], [r, y]), g.guide([0, y], [r, y]), g.dot([r, y], { cls: 'f2' }),
            g.label([r, y], s, { dx: 8, dy: -6, cls: 'ink', size: 'sm' }),
        ]),
        txt(W - 12, H - 34, '거리를 2배로 하면 힘은 4분의 1', { anchor: 'end', cls: 'ink' }),
        txt(W - 12, H - 14, '3배면 9분의 1. 가까울수록 급격히 세진다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-coulomb-distance',
        title: '쿨롱 힘의 거리 의존',
        desc: '두 점전하 사이의 힘은 거리의 제곱에 반비례한다. 거리를 2배로 하면 힘은 4분의 1, '
            + '3배로 하면 9분의 1이 된다.',
        svg: svg({ width: W, height: H, title: '거리에 따른 쿨롱 힘', desc: '거꾸로 제곱 곡선', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 11. 점전하의 전기력선
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const spokes = (cx, cy, out, cls, marker) => Array.from({ length: 12 }, (_, i) => {
        const a = (i * Math.PI) / 6;
        const p1 = [cx + 25 * Math.cos(a), cy + 25 * Math.sin(a)];
        const p2 = [cx + 100 * Math.cos(a), cy + 100 * Math.sin(a)];
        return out ? px(p1[0], p1[1], p2[0], p2[1], { cls, marker, width: 1.6 })
            : px(p2[0], p2[1], p1[0], p1[1], { cls, marker, width: 1.6 });
    }).join('');
    const b = [
        spokes(160, 155, true, 's2', 'ar2'),
        circ(160, 155, 20, { fill: 'var(--s2)', op: 0.25, stroke: 'var(--s2)' }),
        txt(160, 161, '+', { anchor: 'middle', cls: 'ink bold' }),
        txt(160, 44, '양전하: 밖으로 나간다', { anchor: 'middle', cls: 'ink bold' }),
        spokes(440, 155, false, 's1', 'ar1'),
        circ(440, 155, 20, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)' }),
        txt(440, 161, '−', { anchor: 'middle', cls: 'ink bold' }),
        txt(440, 44, '음전하: 안으로 들어온다', { anchor: 'middle', cls: 'ink bold' }),
        txt(W / 2, H - 34, '전하에 가까울수록 선이 촘촘하다 = 전기장이 세다', { anchor: 'middle', cls: 'ink' }),
        txt(W / 2, H - 12, '선의 방향은 그 자리에 놓은 양전하가 밀리는 방향이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-field-lines-point',
        title: '점전하의 전기력선',
        desc: '양전하에서는 전기력선이 사방으로 뻗어 나가고 음전하에서는 사방에서 들어온다. '
            + '전하에 가까울수록 선이 촘촘해지고 그만큼 전기장이 세다.',
        svg: svg({ width: W, height: H, title: '점전하의 전기력선', desc: '양전하는 나가고 음전하는 들어온다', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 12. 쌍극자와 평행판 — 균일한 전기장이란
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 320;
    const q1 = { p: [110, 165], q: 1 }, q2 = { p: [250, 165], q: -1 };
    const bounds = [30, 50, 320, 290];
    const lines = [];
    for (let i = 0; i < 14; i += 1) {
        const a = (i * Math.PI * 2) / 14 + 0.08;
        const start = [q1.p[0] + 13 * Math.cos(a), q1.p[1] + 13 * Math.sin(a)];
        const pts = streamline([q1, q2], start, { h: 2.6, steps: 400, bounds });
        if (pts.length > 6) {
            lines.push(line(pts, { stroke: 'var(--s1)', sw: 1.5 }));
            lines.push(midArrow(pts, 0.45, 's1', 'ar1'));
        }
    }
    const plateX = [390, 600];
    const arrows = Array.from({ length: 8 }, (_, i) => {
        const x = plateX[0] + 14 + i * ((plateX[1] - plateX[0] - 28) / 7);
        return px(x, 108, x, 222, { cls: 's1', marker: 'ar1', width: 1.6 });
    }).join('');
    const b = [
        ...lines,
        circ(q1.p[0], q1.p[1], 15, { fill: 'var(--s2)', op: 0.25, stroke: 'var(--s2)' }),
        txt(q1.p[0], q1.p[1] + 5, '+', { anchor: 'middle', cls: 'ink bold' }),
        circ(q2.p[0], q2.p[1], 15, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)' }),
        txt(q2.p[0], q2.p[1] + 5, '−', { anchor: 'middle', cls: 'ink bold' }),
        txt(180, 38, '전기 쌍극자', { anchor: 'middle', cls: 'ink bold' }),
        txt(180, H - 12, '+ 에서 나와 − 로 들어간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        line([[plateX[0], 100], [plateX[1], 100]], { stroke: 'var(--s2)', sw: 4 }),
        line([[plateX[0], 230], [plateX[1], 230]], { stroke: 'var(--s1)', sw: 4 }),
        txt(plateX[0] - 8, 96, '+', { anchor: 'end', cls: 'ink bold' }),
        txt(plateX[0] - 8, 236, '−', { anchor: 'end', cls: 'ink bold' }),
        arrows,
        txt(495, 38, '평행판 사이', { anchor: 'middle', cls: 'ink bold' }),
        txt(495, 260, '선의 간격이 어디서나 같다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(495, 282, '= 세기와 방향이 같은 균일한 전기장', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(495, H - 12, '가장자리에서는 어긋난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-field-dipole-plates',
        title: '쌍극자와 평행판의 전기장',
        desc: '크기가 같고 부호가 반대인 두 전하가 만드는 전기력선은 양전하에서 나와 음전하로 들어간다. '
            + '넓은 평행판 사이에서는 전기력선의 간격이 일정해 세기와 방향이 같은 균일한 전기장이 된다.',
        svg: svg({ width: W, height: H, title: '쌍극자와 평행판', desc: '휘는 전기장과 균일한 전기장', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 13. 가우스 법칙 — 닫힌 면을 뚫고 나가는 선의 총수
 * ------------------------------------------------------------------ */
add((() => {
    const W = 660, H = 320;
    const aC = [110, 150];
    const aLines = Array.from({ length: 10 }, (_, i) => {
        const a = (i * Math.PI) / 5;
        return px(aC[0] + 12 * Math.cos(a), aC[1] + 12 * Math.sin(a),
            aC[0] + 92 * Math.cos(a), aC[1] + 92 * Math.sin(a), { cls: 's1', marker: 'ar1', width: 1.4 });
    }).join('');
    const bP = [280, 158], bM = [366, 158];
    const bArcs = [40, 82, 168].map(lift =>
        `<path d="M${bP[0] + 14} ${bP[1] - 6} Q${(bP[0] + bM[0]) / 2} ${bP[1] - lift} ${bM[0] - 14} ${bM[1] - 6}" fill="none" stroke="var(--s1)" stroke-width="1.5"/>`).join('');
    const cQ = [452, 150], cS = [578, 150], cR = 52;
    const cLines = [-0.28, -0.1, 0.1, 0.28].map(a =>
        px(cQ[0] + 14 * Math.cos(a), cQ[1] + 14 * Math.sin(a),
            cQ[0] + 180 * Math.cos(a), cQ[1] + 180 * Math.sin(a), { cls: 's1', marker: 'ar1', width: 1.4 })).join('');
    const b = [
        aLines,
        circ(aC[0], aC[1], 62, { stroke: 'var(--s3)', sw: 2, dash: '6 4' }),
        circ(aC[0], aC[1], 11, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)' }),
        txt(aC[0], aC[1] + 4, '+', { anchor: 'middle', cls: 'ink bold' }),
        txt(aC[0], 44, '(가) 안에 +q', { anchor: 'middle', cls: 'ink bold' }),
        txt(aC[0], 256, '나가는 선만 있다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(aC[0], 278, '총수는 +q 에 비례', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

        bArcs,
        `<ellipse cx="${(bP[0] + bM[0]) / 2}" cy="158" rx="82" ry="52" fill="none" stroke="var(--s3)" stroke-width="2" stroke-dasharray="6 4"/>`,
        circ(bP[0], bP[1], 11, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)' }),
        txt(bP[0], bP[1] + 4, '+', { anchor: 'middle', cls: 'ink bold' }),
        circ(bM[0], bM[1], 11, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)' }),
        txt(bM[0], bM[1] + 4, '−', { anchor: 'middle', cls: 'ink bold' }),
        txt((bP[0] + bM[0]) / 2, 44, '(나) 안에 +q 와 −q', { anchor: 'middle', cls: 'ink bold' }),
        txt((bP[0] + bM[0]) / 2, 256, '나간 선은 반드시 되돌아온다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt((bP[0] + bM[0]) / 2, 278, '알짜 전하 0 → 총수 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

        cLines,
        circ(cS[0], cS[1], cR, { stroke: 'var(--s3)', sw: 2, dash: '6 4' }),
        circ(cQ[0], cQ[1], 11, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)' }),
        txt(cQ[0], cQ[1] + 4, '+', { anchor: 'middle', cls: 'ink bold' }),
        txt(cS[0], 44, '(다) 전하가 밖에', { anchor: 'middle', cls: 'ink bold' }),
        txt(cS[0], 256, '들어온 선이 그대로 나간다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(cS[0], 278, '들어온 수 = 나간 수 → 합 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W / 2, H - 12, '점선이 닫힌 면(가우스 면)이다. 뚫고 나간 선의 총수는 오직 안에 든 알짜 전하로 정해진다',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-gauss-surfaces',
        title: '가우스 법칙을 그림으로',
        desc: '닫힌 면을 뚫고 나가는 전기력선의 총수는 그 안에 든 알짜 전하만으로 정해진다. '
            + '안에 양전하가 있으면 나가는 선이 남고, 알짜 전하가 0이면 나간 선이 반드시 되돌아오며, '
            + '전하가 면 밖에 있으면 들어온 선이 그대로 나가 합이 0이 된다.',
        svg: svg({ width: W, height: H, title: '가우스 면과 전기력선', desc: '세 가지 경우의 비교', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 14. 등전위면
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 340;
    const c = [155, 190];
    const rings = [34, 60, 86].map(r => circ(c[0], c[1], r, { stroke: 'var(--s3)', sw: 1.5, dash: '5 4' })).join('');
    const spokes = Array.from({ length: 8 }, (_, i) => {
        const a = (i * Math.PI) / 4;
        return px(c[0] + 16 * Math.cos(a), c[1] + 16 * Math.sin(a),
            c[0] + 104 * Math.cos(a), c[1] + 104 * Math.sin(a), { cls: 's1', marker: 'ar1', width: 1.5 });
    }).join('');
    const pl = [380, 590];
    const eq = [[150, 'V~1'], [182, 'V~2'], [214, 'V~3']].map(([y, s]) =>
        line([[pl[0], y], [pl[1], y]], { stroke: 'var(--s3)', sw: 1.5, dash: '5 4' })
        + txt(pl[1] + 6, y + 4, s, { cls: 'ink2', size: 'sm' })).join('');
    const fld = Array.from({ length: 7 }, (_, i) => {
        const x = pl[0] + 16 + i * ((pl[1] - pl[0] - 32) / 6);
        return px(x, 126, x, 238, { cls: 's1', marker: 'ar1', width: 1.5 });
    }).join('');
    const b = [
        txt(W / 2, 36, '점선(등전위면)과 실선(전기력선)은 어디서나 직각으로 만난다', { anchor: 'middle', cls: 'ink bold' }),
        txt(c[0], 70, '점전하 둘레', { anchor: 'middle', cls: 'ink bold' }),
        rings, spokes,
        circ(c[0], c[1], 14, { fill: 'var(--s2)', op: 0.3, stroke: 'var(--s2)' }),
        txt(c[0], c[1] + 5, '+', { anchor: 'middle', cls: 'ink bold' }),
        txt(c[0], 320, '등전위면은 동심원, 전기력선은 반지름 방향', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        line([[pl[0], 118], [pl[1], 118]], { stroke: 'var(--s2)', sw: 4 }),
        line([[pl[0], 246], [pl[1], 246]], { stroke: 'var(--s1)', sw: 4 }),
        eq, fld,
        txt((pl[0] + pl[1]) / 2, 70, '평행판 사이', { anchor: 'middle', cls: 'ink bold' }),
        txt((pl[0] + pl[1]) / 2, 282, '등전위면이 판과 나란하고 간격이 고르다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt((pl[0] + pl[1]) / 2, 320, '같은 거리마다 전위가 같은 폭으로 떨어진다 → V = E d', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-equipotential',
        title: '등전위면과 전기력선',
        desc: '전위가 같은 점을 이은 등전위면은 전기력선과 언제나 직각으로 만난다. '
            + '점전하 둘레에서는 동심원이 되고 평행판 사이에서는 판과 나란한 고른 간격의 평면이 된다.',
        svg: svg({ width: W, height: H, title: '등전위면', desc: '전기력선과 직각으로 만나는 면', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 15. 평행판 축전기
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 300;
    const x0 = 150, x1 = 400, yT = 100, yB = 210;
    const at = i => x0 + 14 + i * ((x1 - x0 - 28) / 7);
    const fld = Array.from({ length: 8 }, (_, i) => px(at(i), yT + 10, at(i), yB - 10, { cls: 's1', marker: 'ar1', width: 1.5 })).join('');
    const plus = Array.from({ length: 8 }, (_, i) => txt(at(i), yT - 8, '+', { anchor: 'middle', cls: 'ink2', size: 'sm' })).join('');
    const minus = Array.from({ length: 8 }, (_, i) => txt(at(i), yB + 18, '−', { anchor: 'middle', cls: 'ink2', size: 'sm' })).join('');
    const b = [
        line([[x0, yT], [x1, yT]], { stroke: 'var(--s2)', sw: 4 }),
        line([[x0, yB], [x1, yB]], { stroke: 'var(--s1)', sw: 4 }),
        plus, minus, fld,
        wire([[x0, yT], [70, yT], [70, 148]]),
        wire([[x0, yB], [70, yB], [70, 162]]),
        battery(70, 155),
        txt(40, 158, 'V', { anchor: 'end', cls: 'ink bold' }),
        px(x1 + 24, yT, x1 + 24, yB, { cls: 's3', marker: 'ar3', width: 1.5 }),
        px(x1 + 24, yB, x1 + 24, yT, { cls: 's3', marker: 'ar3', width: 1.5 }),
        txt(x1 + 32, (yT + yB) / 2 + 4, 'd', { cls: 'ink bold' }),
        txt((x0 + x1) / 2, yB + 46, '판 넓이 A, 간격 d', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(470, 96, 'Q = C V', { cls: 'ink bold' }),
        txt(470, 122, 'C = ε~0 A / d', { cls: 'ink bold' }),
        txt(470, 150, '넓이를 키우면 용량이 커진다', { cls: 'ink2', size: 'sm' }),
        txt(470, 170, '간격을 좁혀도 커진다', { cls: 'ink2', size: 'sm' }),
        txt(470, 200, '사이의 전기장은 균일하고', { cls: 'ink2', size: 'sm' }),
        txt(470, 222, 'E = V / d 다', { cls: 'ink bold' }),
        txt(W / 2, 44, '두 판에 크기가 같고 부호가 반대인 전하가 모인다', { anchor: 'middle', cls: 'ink' }),
        txt(W / 2, H - 12, '축전기가 저장하는 것은 알짜 전하가 아니라 갈라놓은 전하와 그 사이의 전기장이다',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-capacitor',
        title: '평행판 축전기',
        desc: '전지를 연결하면 두 판에 크기가 같고 부호가 반대인 전하가 모이고 사이에 균일한 전기장이 생긴다. '
            + '전기용량은 판 넓이에 비례하고 간격에 반비례한다.',
        svg: svg({ width: W, height: H, title: '평행판 축전기', desc: '전하가 갈라져 모이고 사이에 균일한 장이 생긴다', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 16. 저항의 직렬과 병렬
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 340;
    // 직렬 — 왼쪽 세로선에 전지, 윗변에 저항 둘
    const sL = 100, sR = 300, sT = 110, sB = 210;
    const ser = [
        wire([[sL, sT], [135, sT]]),
        wire([[185, sT], [225, sT]]),
        wire([[275, sT], [sR, sT]]),
        wire([[sR, sT], [sR, sB]]),
        wire([[sR, sB], [sL, sB]]),
        wire([[sL, sB], [sL, 167]]),
        wire([[sL, sT], [sL, 153]]),
        battery(sL, 160),
        resistor(160, sT, 'R~1'),
        resistor(250, sT, 'R~2'),
        txt(sL - 22, 164, 'V', { anchor: 'end', cls: 'ink bold' }),
        px(190, 132, 220, 132, { cls: 's1', marker: 'ar1', width: 1.8 }),
        txt(205, 152, 'I', { anchor: 'middle', cls: 'ink bold' }),
        txt(200, 62, '직렬', { anchor: 'middle', cls: 'ink bold' }),
        txt(200, 244, '전류가 같다. 전압이 나뉜다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(200, 268, 'V = V~1 + V~2', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(200, 292, 'R = R~1 + R~2', { anchor: 'middle', cls: 'ink bold' }),
        txt(200, 316, '합성 저항은 어느 쪽보다도 크다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    // 병렬 — 가로 두 레일 사이에 세로 저항 둘
    const pL = 400, pR = 600, pT = 110, pB = 210, r1x = 480, r2x = 550;
    const par = [
        wire([[pL, pT], [pR, pT]]),
        wire([[pL, pB], [pR, pB]]),
        wire([[pR, pT], [pR, pB]]),
        wire([[pL, pT], [pL, 153]]),
        wire([[pL, pB], [pL, 167]]),
        battery(pL, 160),
        wire([[r1x, pT], [r1x, 135]]),
        wire([[r1x, 185], [r1x, pB]]),
        wire([[r2x, pT], [r2x, 135]]),
        wire([[r2x, 185], [r2x, pB]]),
        resistor(r1x, 160, 'R~1', { horizontal: false }),
        resistor(r2x, 160, 'R~2', { horizontal: false }),
        txt(pL - 22, 164, 'V', { anchor: 'end', cls: 'ink bold' }),
        px(424, 94, 452, 94, { cls: 's1', marker: 'ar1', width: 1.8 }),
        txt(438, 86, 'I', { anchor: 'middle', cls: 'ink bold' }),
        px(r1x - 12, 128, r1x - 12, 152, { cls: 's2', marker: 'ar2', width: 1.6 }),
        px(r2x + 34, 128, r2x + 34, 152, { cls: 's2', marker: 'ar2', width: 1.6 }),
        txt(r1x - 18, 142, 'I~1', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(r2x + 40, 142, 'I~2', { cls: 'ink2', size: 'sm' }),
        txt(500, 62, '병렬', { anchor: 'middle', cls: 'ink bold' }),
        txt(500, 244, '전압이 같다. 전류가 나뉜다', { anchor: 'middle', cls: 'ink', size: 'sm' }),
        txt(500, 268, 'I = I~1 + I~2', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(500, 292, '1/R = 1/R~1 + 1/R~2', { anchor: 'middle', cls: 'ink bold' }),
        txt(500, 316, '합성 저항은 어느 쪽보다도 작다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    const b = [ser, par, txt(W / 2, 34, '길을 하나 더 내주면 전체 저항은 줄어든다 — 이것이 병렬의 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' })].join('');
    return {
        name: 'elec-series-parallel',
        title: '저항의 직렬 연결과 병렬 연결',
        desc: '직렬에서는 전류가 같고 전압이 나뉘어 합성 저항이 커진다. '
            + '병렬에서는 전압이 같고 전류가 나뉘어 합성 저항이 어느 쪽보다도 작아진다.',
        svg: svg({ width: W, height: H, title: '직렬과 병렬', desc: '전류와 전압이 나뉘는 방식의 차이', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 17. 키르히호프 두 법칙
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 340;
    const L = 80, M = 310, R = 540, T = 100, B = 260;
    const b = [
        wire([[L, T], [165, T]]),
        wire([[215, T], [405, T]]),
        wire([[455, T], [R, T]]),
        wire([[L, B], [R, B]]),
        wire([[L, T], [L, 173]]),
        wire([[L, 187], [L, B]]),
        wire([[R, T], [R, 173]]),
        wire([[R, 187], [R, B]]),
        wire([[M, T], [M, 155]]),
        wire([[M, 205], [M, B]]),
        battery(L, 180),
        battery(R, 180),
        resistor(190, T, 'R~1'),
        resistor(430, T, 'R~2'),
        resistor(M, 180, 'R~3', { horizontal: false }),
        txt(L - 26, 184, 'ε~1', { anchor: 'end', cls: 'ink bold' }),
        txt(R + 40, 184, 'ε~2', { cls: 'ink bold' }),
        `<circle cx="${M}" cy="${T}" r="4.5" fill="var(--ink)"/>`,
        `<circle cx="${M}" cy="${B}" r="4.5" fill="var(--ink)"/>`,
        px(236, T + 18, 274, T + 18, { cls: 's1', marker: 'ar1', width: 1.8 }),
        txt(255, T + 38, 'I~1', { anchor: 'middle', cls: 'ink bold' }),
        px(384, T + 18, 346, T + 18, { cls: 's2', marker: 'ar2', width: 1.8 }),
        txt(365, T + 38, 'I~2', { anchor: 'middle', cls: 'ink bold' }),
        px(M - 18, T + 30, M - 18, T + 66, { cls: 's3', marker: 'ar3', width: 1.8 }),
        txt(M - 24, T + 82, 'I~3', { anchor: 'end', cls: 'ink bold' }),
        arc(195, 190, 40, -70, 200, '고리 1'),
        arc(425, 190, 40, -20, 250, '고리 2'),
        txt(W / 2, 40, '접합점 법칙: 들어오는 전류의 합 = 나가는 전류의 합', { anchor: 'middle', cls: 'ink bold' }),
        txt(W / 2, 62, '위 접합점에서 I~1 + I~2 = I~3', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W / 2, 300, '고리 법칙: 한 고리를 한 바퀴 돌며 전위 변화를 더하면 0', { anchor: 'middle', cls: 'ink bold' }),
        txt(W / 2, 324, '올라간 만큼 내려와야 제자리다. 전하 보존과 에너지 보존을 회로에 옮긴 것이다',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-kirchhoff',
        title: '키르히호프 법칙',
        desc: '두 개의 고리가 있는 회로. 접합점에서는 들어오는 전류의 합과 나가는 전류의 합이 같고, '
            + '한 고리를 한 바퀴 돌며 전위 변화를 모두 더하면 0이 된다.',
        svg: svg({ width: W, height: H, title: '두 고리 회로와 키르히호프 법칙', desc: '접합점 법칙과 고리 법칙', body: b }),
    };
})());

/* ------------------------------------------------------------------ *
 * 18. RC 충전과 방전
 * ------------------------------------------------------------------ */
add((() => {
    const W = 660, H = 320;
    const g = frame({ xRange: [0, 5], yRange: [0, 1.15], box: { x: 68, y: 40, w: 320, h: 226 } });
    const b = [
        g.axes({ xLabel: 't / τ', yLabel: 'q / Q', xTicks: [0, 1, 2, 3, 4, 5], yTicks: [0, 0.37, 0.63, 1] }),
        g.curve(t => 1 - Math.exp(-t), { cls: 's1' }),
        g.curve(t => Math.exp(-t), { cls: 's2' }),
        g.guide([0, 0.632], [1, 0.632]),
        g.guide([1, 0], [1, 0.632]),
        g.dot([1, 0.632], { cls: 'f1' }),
        g.dot([1, 0.368], { cls: 'f2' }),
        g.dot([2, 0.135], { cls: 'f2' }),
        g.dot([3, 0.0498], { cls: 'f2' }),
        g.label([1.15, 0.632], '충전: τ 만에 63%', { dy: -6, cls: 'ink', size: 'sm' }),
        g.label([2.1, 0.135], '방전: τ 마다 0.37배', { dy: -8, cls: 'ink', size: 'sm' }),
        g.label([4.6, 0.98], '결코 1에 닿지 않는다', { dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(440, 68, 'τ = R C 를 시간상수라 한다', { cls: 'ink bold' }),
        txt(440, 94, '남은 몫이 τ 마다 같은 비율로 준다', { cls: 'ink2', size: 'sm' }),
        txt(440, 124, 'τ 뒤   0.37배', { cls: 'ink2', size: 'sm' }),
        txt(440, 144, '2τ 뒤  0.14배', { cls: 'ink2', size: 'sm' }),
        txt(440, 164, '3τ 뒤  0.05배', { cls: 'ink2', size: 'sm' }),
        txt(440, 184, '5τ 뒤  0.007배', { cls: 'ink2', size: 'sm' }),
        txt(440, 218, '반씩 줄어드는 것과 같은 종류의', { cls: 'ink', size: 'sm' }),
        txt(440, 238, '변화이고, 배율이 0.37일 뿐이다', { cls: 'ink', size: 'sm' }),
        txt(440, 272, '실무에서 5τ 면 끝났다고 본다', { cls: 'ink bold', size: 'sm' }),
    ].join('');
    return {
        name: 'elec-rc-curve',
        title: 'RC 회로의 충전과 방전',
        desc: '축전기의 전하는 시간상수 τ = RC 마다 남은 몫이 같은 비율로 줄어든다. '
            + '충전은 τ 만에 63%까지 차오르고 방전은 τ 마다 0.37배로 준다. 어느 쪽도 유한한 시간에 끝나지 않는다.',
        svg: svg({ width: W, height: H, title: 'RC 충전·방전 곡선', desc: '지수적으로 차오르고 줄어든다', body: b }),
    };
})());

export default figures;
