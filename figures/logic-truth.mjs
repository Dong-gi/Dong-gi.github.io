/**
 * 논리학 6장(진리표와 진리함수적 의미론)의 좌표계·구조 그림.
 *
 * 이름은 모두 `log-d-` 로 시작한다(6장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-d-*.d2` 에 있고, 여기에는
 * 위치를 손으로 잡아야 하는 것(함수 그림, 값이 올라가는 나무, 지수 곡선,
 * 배당 공간의 칸)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊨ 로 직접 적고, 아래첨자는 lib 의 `v~0`
 * 표기를 쓴다. 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다.
 */
import { svg, frame, txt, esc } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구
 * ------------------------------------------------------------------ */

/** lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가 SVG 안에 없어 선이 사라진다. */
function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.7, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { stroke = CG, sw = 1.4, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="none" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 색을 직접 넣는 글자. lib 의 txt 는 클래스만 받는다. */
function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/**
 * 진리값 한 칸. T 는 파랑 계열, F 는 주황 계열로 옅게 칠하고 글자를 함께 적는다.
 * 색만으로 구분하지 않으므로 흑백으로 봐도 읽힌다.
 */
function vcell(x, y, w, h, v, { sw = 1.2 } = {}) {
    const on = v === 'T';
    return box(x, y, w, h, { fill: on ? C1 : C2, op: 0.16, stroke: on ? C1 : C2, sw, rx: 2 })
        + txt(x + w / 2, y + h / 2 + 4, v, { anchor: 'middle', cls: 'ink', size: 'sm' });
}

/** 좌표계 위 곡선. lib 의 curve 는 클래스만 받는다. */
function fcurve(f, fn, { from, to, steps = 200, stroke = C1, sw = 2.4, dash } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const xv = from + ((to - from) * i) / steps;
        pts.push([f.X(xv), f.Y(fn(xv))]);
    }
    return ln(pts, { stroke, sw, dash });
}

/** 좁은 칸에서도 겹치지 않는 축. */
function axes2(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel, xFmt = String, yFmt = String } = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = f.Y(y0);
    const ay = f.X(x0);
    const g = [arw(ay, ax, f.X(x1) + 14, ax, { width: 1.2 }), arw(ay, ax, ay, f.Y(y1) - 14, { width: 1.2 })];
    for (const t of xTicks) {
        g.push(ln([[f.X(t), ax], [f.X(t), ax + 4]], { sw: 1 }));
        g.push(txt(f.X(t), ax + 17, xFmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        g.push(ln([[ay - 4, f.Y(t)], [ay, f.Y(t)]], { sw: 1 }));
        g.push(txt(ay - 8, f.Y(t) + 4, yFmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 18, ax + 5, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay + 2, f.Y(y1) - 20, yLabel, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/* ================================================================== *
 * 6-1. 배당은 함수다 — 진리표의 한 줄이 그 함수 하나
 * ================================================================== */
add((() => {
    const W = 780, H = 376;
    const g = [];
    g.push(txt(W / 2, 26, '배당은 문장문자마다 T 나 F 를 하나씩 골라 주는 함수다', { anchor: 'middle', cls: 'ink bold' }));

    /* 왼쪽 — 함수 그림 */
    g.push(panel(14, 44, 350, 300, '함수로 본 배당 v', '정의역은 문장문자, 공역은 { T, F }'));
    g.push(ell(104, 212, 54, 78));
    g.push(txt(104, 124, '문장문자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const letters = [['P', 172], ['Q', 212], ['R', 252]];
    for (const [nm, y] of letters) {
        g.push(`<circle cx="104" cy="${y}" r="4" fill="${C1}"/>`);
        g.push(txt(88, y + 5, nm, { anchor: 'end', cls: 'ink' }));
    }
    g.push(ell(288, 212, 44, 64));
    g.push(txt(288, 136, '{ T, F }', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(`<circle cx="288" cy="190" r="4" fill="${CK}"/>`);
    g.push(txt(302, 195, 'T', { cls: 'ink bold' }));
    g.push(`<circle cx="288" cy="240" r="4" fill="${CK}"/>`);
    g.push(txt(302, 245, 'F', { cls: 'ink bold' }));
    g.push(arw(160, 172, 280, 188, { col: C1 }));
    g.push(arw(160, 212, 280, 237, { col: C2 }));
    g.push(arw(160, 252, 280, 195, { col: C1 }));
    g.push(txt(189, 314, 'v(P) = T,  v(Q) = F,  v(R) = T', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(189, 332, '문자마다 화살표가 정확히 하나씩 나간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 오른쪽 — 진리표 여덟 줄 */
    g.push(panel(380, 44, 386, 300, '진리표의 한 줄 = 배당 하나', '문장문자가 셋이면 배당은 모두 여덟 개'));
    const tx0 = 468, ty0 = 96, cw = 46, rh = 25;
    g.push(txt(tx0 - 12, ty0 + 12, '이름', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    ['P', 'Q', 'R'].forEach((nm, i) => g.push(txt(tx0 + i * cw + cw / 2, ty0 + 12, nm, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    const rows = [];
    for (let i = 0; i < 8; i += 1) {
        rows.push([(i & 4) ? 'F' : 'T', (i & 2) ? 'F' : 'T', (i & 1) ? 'F' : 'T']);
    }
    rows.forEach((row, i) => {
        const y = ty0 + 20 + i * rh;
        g.push(txt(tx0 - 12, y + rh / 2 + 4, `v~${i}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        row.forEach((v, j) => g.push(vcell(tx0 + j * cw + 3, y + 2, cw - 6, rh - 4, v, { sw: i === 2 ? 2.2 : 1 })));
    });
    const hy = ty0 + 20 + 2 * rh;
    g.push(box(tx0 - 2, hy, 3 * cw + 4, rh, { stroke: CI, sw: 1.8, rx: 3 }));
    g.push(txt(tx0 + 3 * cw + 12, hy + rh / 2 + 4, '이 줄이 왼쪽 그림이다', { cls: 'ink', size: 'sm' }));
    g.push(txt(573, 330, '여덟 줄은 서로 다른 여덟 개의 함수다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 364, '문장문자가 n 개면 배당은 2ⁿ 개. 진리표를 다 채운다는 것은 그 2ⁿ 개 함수를 빠짐없이 훑는다는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'log-d-valuation-as-function',
        svg: svg({
            width: W, height: H,
            title: '진리값 배당은 문장문자에서 진리값으로 가는 함수다',
            desc: '왼쪽은 문장문자 P Q R 에서 T 와 F 로 화살표가 하나씩 나가는 함수 그림, 오른쪽은 그 함수 여덟 개를 줄로 늘어놓은 진리표',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6-2. 값은 잎에서 뿌리로 올라간다 — 재귀적 계산
 * ================================================================== */
add((() => {
    const W = 780, H = 452;
    const g = [];
    g.push(txt(W / 2, 26, '값은 잎에서 뿌리로 올라간다 — 한 단계가 진리함수 한 번', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 48, 'φ = ¬(P ∧ Q) → (R ∨ Q) — 배당 v 는 P = T, Q = F, R = T', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const N = {
        root: [390, 100, '→', 'T'],
        neg: [225, 176, '¬', 'T'],
        or: [555, 176, '∨', 'T'],
        and: [225, 256, '∧', 'F'],
        p: [150, 336, 'P', 'T'],
        q1: [300, 336, 'Q', 'F'],
        r: [480, 336, 'R', 'T'],
        q2: [630, 336, 'Q', 'F'],
    };
    const E = [['root', 'neg'], ['root', 'or'], ['neg', 'and'], ['and', 'p'], ['and', 'q1'], ['or', 'r'], ['or', 'q2']];
    for (const [a, b] of E) {
        const [x1, y1] = N[a], [x2, y2] = N[b];
        const dx = x2 - x1, dy = y2 - y1;
        const L = Math.hypot(dx, dy);
        g.push(ln([[x1 + (dx / L) * 21, y1 + (dy / L) * 21], [x2 - (dx / L) * 21, y2 - (dy / L) * 21]], { stroke: CG, sw: 1.6 }));
    }
    for (const k of Object.keys(N)) {
        const [x, y, sym, v] = N[k];
        const leaf = 'pq1rq2'.includes(k);
        g.push(`<circle cx="${x}" cy="${y}" r="21" fill="none" stroke="${CK}" stroke-width="1.5"/>`);
        g.push(txt(x, y + 6, sym, { anchor: 'middle', cls: 'ink bold' }));
        const bx = leaf ? x - 13 : x + 27;
        const by = leaf ? y + 30 : y - 13;
        g.push(vcell(bx, by, 26, 24, v, { sw: 1.6 }));
    }
    g.push(arw(300, 78, 356, 92, { col: CK, width: 1.4 }));
    g.push(txt(296, 74, '뿌리의 값이 문장 전체의 값이다', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(456, 112, 'v ⊨ φ', { cls: 'ink bold' }));

    g.push(box(60, 396, 660, 30, { stroke: CG, sw: 1, rx: 5 }));
    const steps = ['1) T ∧ F 는 F', '2) ¬F 는 T', '3) T ∨ F 는 T', '4) T → T 는 T'];
    steps.forEach((s, i) => g.push(txt(96 + i * 165, 416, s, { cls: 'ink', size: 'sm' })));
    g.push(txt(W / 2, 442, '괄호 구조가 계산 순서를 정한다 — 5장의 재귀적 문법 규칙 하나마다 값 계산 규칙 하나가 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'log-d-value-climb',
        svg: svg({
            width: W, height: H,
            title: '논리식의 값은 구조를 따라 재귀적으로 정해진다',
            desc: '문장문자에 배당된 값이 나무의 잎에 놓이고, 연결자마다 진리함수를 한 번씩 적용하며 뿌리까지 값이 올라간다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6-3. 줄 수는 문자 하나에 두 배 — 지수 폭발
 * ================================================================== */
add((() => {
    const W = 790, H = 404;
    const g = [];
    g.push(txt(W / 2, 26, '문장문자를 하나 더할 때마다 진리표가 통째로 두 벌이 된다', { anchor: 'middle', cls: 'ink bold' }));

    /* 왼쪽 — 2ⁿ 곡선 */
    g.push(panel(14, 44, 440, 312, '줄 수 2ⁿ', '가로는 문장문자 개수, 세로는 진리표의 줄 수'));
    const f = frame({ xRange: [0, 20], yRange: [0, 1100000], box: { x: 92, y: 96, w: 330, h: 212 } });
    g.push(axes2(f, {
        xRange: [0, 20], yRange: [0, 1100000],
        xTicks: [5, 10, 15, 20], yTicks: [250000, 500000, 750000, 1000000],
        xLabel: 'n', yLabel: '줄 수',
        yFmt: t => `${t / 10000}만`,
    }));
    g.push(fcurve(f, v => 2 ** v, { from: 0, to: 20 }));
    g.push(`<circle cx="${f.X(20)}" cy="${f.Y(1048576)}" r="4.5" fill="${C1}"/>`);
    g.push(txt(f.X(19.6), f.Y(980000), 'n = 20 이면 1048576 줄', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(`<circle cx="${f.X(13)}" cy="${f.Y(8192)}" r="4.5" fill="${C2}"/>`);
    g.push(ln([[f.X(13), f.Y(8192)], [f.X(11.4), f.Y(230000)]], { stroke: C2, sw: 1.2, dash: '4 3' }));
    g.push(txt(f.X(11.2), f.Y(240000), 'n = 13 은 축에 붙어 보이지만', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(11.2), f.Y(180000), '이미 8192 줄이다', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(234, 348, '곡선이 꺾이는 자리는 없다. 축에 붙어 보이는 구간도 두 배씩 늘고 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 오른쪽 — 두 배로 불어나는 까닭 */
    g.push(panel(470, 44, 306, 312, '왜 두 배인가', '문자 R 을 하나 더해 본다'));
    const small = [['T', 'T'], ['T', 'F'], ['F', 'T'], ['F', 'F']];
    g.push(txt(524, 100, 'P  Q', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    small.forEach((row, i) => {
        row.forEach((v, j) => g.push(vcell(500 + j * 26, 110 + i * 22, 24, 20, v)));
    });
    g.push(txt(524, 216, '4 줄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(566, 158, 600, 158, { col: CK, width: 1.6 }));
    g.push(txt(583, 148, 'R 추가', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(658, 100, 'P  Q  R', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 8; i += 1) {
        const base = small[i % 4];
        const rv = i < 4 ? 'T' : 'F';
        const y = 110 + i * 22 + (i < 4 ? 0 : 8);
        [...base, rv].forEach((v, j) => g.push(vcell(622 + j * 26, y, 24, 20, v)));
    }
    g.push(txt(736, 154, '위 벌', { cls: 'ink2', size: 'sm' }));
    g.push(txt(736, 246, '아래 벌', { cls: 'ink2', size: 'sm' }));
    g.push(txt(623, 322, '왼쪽 표를 그대로 두 번 베끼고 R 열만 T 와 F 로 채웠다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(623, 340, '그래서 줄 수가 정확히 두 배 — 이것이 2ⁿ 의 정체다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 386, '1 초에 100 만 줄을 채워도 n = 40 이면 열흘이 넘고 n = 50 이면 한 사람의 일생으로 모자란다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'log-d-row-explosion',
        svg: svg({
            width: W, height: H,
            title: '진리표의 줄 수는 문장문자 개수에 대해 지수적으로 늘어난다',
            desc: '왼쪽은 2의 n 제곱 곡선, 오른쪽은 문자를 하나 더하면 기존 표가 두 번 복사되는 까닭을 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6-4. 배당 공간을 나누는 법 — 항진·모순·우연, 그리고 귀결
 * ================================================================== */
add((() => {
    const W = 790, H = 470;
    const g = [];
    g.push(txt(W / 2, 26, '의미론의 개념은 전부 ‘어느 배당에서 참인가’ 하나로 정해진다', { anchor: 'middle', cls: 'ink bold' }));

    const strip = (x, y, pattern, { cw = 38, ch = 26 } = {}) => pattern
        .map((v, i) => vcell(x + i * cw, y, cw - 4, ch, v)).join('');

    /* 왼쪽 — 세 갈래 */
    g.push(panel(14, 44, 380, 244, '한 논리식이 배당 여덟 개에서 갖는 값'));
    const kinds = [
        ['항진명제 — 참인 칸이 전부다', ['T', 'T', 'T', 'T', 'T', 'T', 'T', 'T']],
        ['모순 — 참인 칸이 하나도 없다', ['F', 'F', 'F', 'F', 'F', 'F', 'F', 'F']],
        ['우연 — 참인 칸도 거짓인 칸도 있다', ['T', 'F', 'F', 'T', 'T', 'F', 'T', 'F']],
    ];
    kinds.forEach(([label, pat], i) => {
        const y = 84 + i * 66;
        g.push(txt(34, y, label, { cls: 'ink', size: 'sm' }));
        g.push(strip(34, y + 10, pat));
    });
    g.push(txt(204, 274, '만족가능하다 = 참인 칸이 하나라도 있다 (위 셋 중 첫째와 셋째)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 오른쪽 — 귀결은 포함이다 */
    g.push(panel(410, 44, 366, 244, '귀결 Γ ⊨ φ 는 칸의 포함 관계다', '위 줄은 Γ 가 모두 참인 칸, 아래 줄은 φ 가 참인 칸'));
    g.push(txt(428, 124, 'Γ', { anchor: 'middle', cls: 'ink bold' }));
    g.push(strip(446, 106, ['F', 'T', 'F', 'F', 'T', 'F', 'F', 'F'], { cw: 40 }));
    g.push(txt(428, 184, 'φ', { anchor: 'middle', cls: 'ink bold' }));
    g.push(strip(446, 166, ['F', 'T', 'F', 'T', 'T', 'F', 'T', 'F'], { cw: 40 }));
    for (const i of [1, 4]) {
        g.push(arw(446 + i * 40 + 18, 136, 446 + i * 40 + 18, 162, { col: CK, width: 1.3, dash: '3 3' }));
    }
    g.push(txt(593, 222, 'Γ 쪽에 칠해진 칸은 φ 쪽에도 칠해져 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(593, 242, '전제가 참인 곳마다 결론도 참 — 그것이 귀결이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(593, 262, '칠하지 않은 칸에서는 무슨 일이 일어나도 좋다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 아래 — 한 칸만 어긋나도 귀결이 아니다 */
    g.push(panel(14, 300, 762, 150, '반례 배당 — 한 칸만 어긋나면 끝난다'));
    g.push(txt(26, 367, 'Γ 가 모두 참인 칸', { cls: 'ink', size: 'sm' }));
    g.push(strip(170, 350, ['F', 'T', 'F', 'F', 'T', 'F', 'T', 'F']));
    g.push(txt(26, 413, 'φ 가 참인 칸', { cls: 'ink', size: 'sm' }));
    g.push(strip(170, 396, ['F', 'T', 'F', 'T', 'T', 'F', 'F', 'F']));
    g.push(box(168 + 6 * 38, 346, 38, 80, { stroke: C2, sw: 2.2, rx: 4 }));
    g.push(txt(187 + 6 * 38, 340, '반례', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(492, 360, '일곱째 칸에서 전제는 전부 참인데 결론이 거짓이다', { cls: 'ink', size: 'sm' }));
    g.push(txt(492, 384, '이 칸 하나로 귀결이 아니라는 것이 확정된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 408, '반대로 귀결임을 보이려면 여덟 칸을 다 봐야 한다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 464, '반례는 하나만 보이면 끝나고 없음은 전부 훑어야 한다 — 이 비대칭이 7장의 증명 체계를 부르는 자리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'log-d-semantic-zoo',
        svg: svg({
            width: W, height: H,
            title: '항진명제 · 모순 · 우연과 의미론적 귀결을 배당의 칸으로 본 그림',
            desc: '배당 여덟 개를 칸으로 늘어놓고 어느 칸에서 참인지로 항진명제와 모순과 우연을 가르고, 귀결을 칸의 포함 관계로 보인다',
            body: g.join(''),
        }),
    };
})());

export default figures;
