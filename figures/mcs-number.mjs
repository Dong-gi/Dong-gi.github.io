/**
 * mcs 11장(정수론) · 12장(방향 그래프와 부분순서)의 그림.
 *
 * 이름은 모두 `mcs-n-` 로 시작한다(담당 C 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `n~0`, `Z~{15}` 표기를 쓰고, 나머지는 유니코드
 * (≤ ≥ ≠ ∤ ∈ ∘ ⊆ ⌊⌋ φ ≡ · × → ← ↔ ² ³ ⁴ ✓)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 그림은 두 종류다. 11장은 ‘같은 값이 계속 유지된다’(불변량)와
 * ‘어느 것에 역원이 있는가’를 보이는 표 그림이고, 12장은 점과 화살표 그림이다.
 * 관계의 성질을 방향 그래프로 그린 mcs-n-relation-props 가 12장의 중심이다.
 */
import { svg, txt, esc } from './lib.mjs';

const figures = [];
const add = (name, body) => figures.push({ name, svg: body });
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구 — lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * ------------------------------------------------------------------ */

const MK = { [C1]: 'ar1', [C2]: 'ar2', [C3]: 'ar3', [CK]: 'ark', [CI]: 'ark', [CG]: 'ark' };

function arw(x1, y1, x2, y2, { col = CK, sw = 1.8, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" marker-end="url(#${MK[col] ?? 'ark'})"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { col = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}"`
        + ` stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 색을 직접 지정하는 글자. txt() 는 CSS 클래스만 받아 계열색을 쓸 수 없다. */
function ctxt(x, y, str, col, { anchor = 'start', size = 'sm', bold = false } = {}) {
    const cls = [size === 'sm' ? 'sm' : null, bold ? 'bold' : null].filter(Boolean).join(' ');
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"${cls ? ` class="${cls}"` : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 표. hlCol 에 든 열은 배경을 옅게 칠한다. */
function table(x, y, cols, rows, { cw = 80, rh = 26, hlCol = [], hlColor = C3, hlCell = [] } = {}) {
    const xs = cols.map((_, i) => x + i * cw);
    const W = cw * cols.length;
    const g = [];
    for (const c of hlCol) g.push(box(xs[c], y, cw, rh * (rows.length + 1), { fill: hlColor, op: 0.15, stroke: 'none', rx: 2 }));
    for (const [r, c, col] of hlCell) g.push(box(xs[c], y + rh * (r + 1), cw, rh, { fill: col, op: 0.25, stroke: col, sw: 1.6, rx: 2 }));
    cols.forEach((c, i) => g.push(txt(xs[i] + cw / 2, y + 18, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    g.push(ln([[x, y + rh], [x + W, y + rh]], { col: CK, sw: 1.3 }));
    rows.forEach((row, r) => {
        row.forEach((v, i) => g.push(txt(xs[i] + cw / 2, y + rh * (r + 2) - 8, String(v), { anchor: 'middle', cls: 'ink', size: 'sm' })));
        if (r < rows.length - 1) g.push(ln([[x, y + rh * (r + 2)], [x + W, y + rh * (r + 2)]], { col: CG, sw: 0.8 }));
    });
    g.push(box(x, y, W, rh * (rows.length + 1), { stroke: CK, sw: 1.2, rx: 3 }));
    return g.join('');
}

/** 칸 한 줄. hl[i] 에 색이 있으면 그 칸을 칠한다. */
function strip(x, y, w, h, items, { hl = {}, under = null, small = true } = {}) {
    const g = [];
    items.forEach((v, i) => {
        const cx = x + i * w;
        const col = hl[i];
        g.push(box(cx, y, w, h, { fill: col ?? 'none', op: col ? 0.2 : 1, stroke: col ?? CK, sw: col ? 1.8 : 1, rx: 2 }));
        g.push(txt(cx + w / 2, y + h / 2 + 5, String(v), { anchor: 'middle', cls: 'ink', size: small ? 'sm' : undefined }));
        if (under) {
            const u = under[i];
            g.push(ctxt(cx + w / 2, y + h + 16, String(u.t), u.c ?? CK, { anchor: 'middle' }));
        }
    });
    return g.join('');
}

/* ------------------------------------------------------------------ *
 * 점과 화살표 그래프 소도구
 * ------------------------------------------------------------------ */

const NR = 17;

function node(p, label, { col = CI, fill = 'none', r = NR, sw = 1.6 } = {}) {
    return `<circle cx="${r2(p[0])}" cy="${r2(p[1])}" r="${r}" fill="${fill === 'none' ? 'none' : fill}"`
        + `${fill === 'none' ? '' : ' fill-opacity="0.18"'} stroke="${col}" stroke-width="${sw}"/>`
        + ctxt(p[0], p[1] + 4, label, CI, { anchor: 'middle' });
}

/**
 * 정점 중심 p 에서 q 로 가는 간선. 원 반지름만큼 잘라내고, bend 를 주면 휜다.
 * 휜 간선의 화살촉 방향은 조절점에서 재야 어긋나지 않는다.
 */
function edge(p, q, { col = CK, sw = 1.7, bend = 0, r = NR, dash } = {}) {
    const dx = q[0] - p[0], dy = q[1] - p[1];
    const L = Math.hypot(dx, dy) || 1;
    const nx = -dy / L, ny = dx / L;
    const cx = (p[0] + q[0]) / 2 + nx * bend * 2;
    const cy = (p[1] + q[1]) / 2 + ny * bend * 2;
    const trim = (a, b) => {
        const ux = b[0] - a[0], uy = b[1] - a[1];
        const m = Math.hypot(ux, uy) || 1;
        return [a[0] + (ux / m) * r, a[1] + (uy / m) * r];
    };
    const s = trim(p, [cx, cy]);
    const e = trim(q, [cx, cy]);
    if (!bend) {
        return arw(s[0], s[1], e[0], e[1], { col, sw, dash });
    }
    return `<path d="M${r2(s[0])} ${r2(s[1])} Q${r2(cx)} ${r2(cy)} ${r2(e[0])} ${r2(e[1])}" fill="none" stroke="${col}"`
        + ` stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ` marker-end="url(#${MK[col] ?? 'ark'})"/>`;
}

/** 자기 고리. 정점 위쪽에 작은 원을 그린다. */
function loop(p, { col = CK, sw = 1.7, r = NR } = {}) {
    const [x, y] = p;
    const a = [x - r * 0.55, y - r * 0.84];
    const b = [x + r * 0.55, y - r * 0.84];
    return `<path d="M${r2(a[0])} ${r2(a[1])} C${r2(x - r * 1.9)} ${r2(y - r * 3.1)} ${r2(x + r * 1.9)} ${r2(y - r * 3.1)} ${r2(b[0])} ${r2(b[1])}"`
        + ` fill="none" stroke="${col}" stroke-width="${sw}" marker-end="url(#${MK[col] ?? 'ark'})"/>`;
}

/* ================================================================== *
 * 11장 — 정수론
 * ================================================================== */

/* 1. 나눗셈 정리 — 나머지가 늘 [0, d) 에 있다 */
add('mcs-n-division-theorem', (() => {
    const W = 720, H = 300;
    const x0 = -21, x1 = 28;
    const X = v => r2(55 + ((v - x0) / (x1 - x0)) * 620);
    const g = [];
    g.push(txt(W / 2, 26, '나눗셈 정리 — 어떤 정수 n 도 7 의 배수 하나와 0 이상 7 미만인 나머지로 딱 한 가지로 갈린다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    for (const [ly, n, q, r, note] of [[110, 25, 3, 4, 'n 이 양수'], [222, -11, -2, 3, 'n 이 음수']]) {
        g.push(ln([[X(x0), ly], [X(x1), ly]], { col: CK, sw: 1.4 }));
        for (let t = -21; t <= 28; t += 7) {
            g.push(ln([[X(t), ly - 7], [X(t), ly + 7]], { col: CK, sw: 1.2 }));
            g.push(txt(X(t), ly + 24, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        const base = q * 7;
        g.push(ln([[X(base), ly], [X(n), ly]], { col: C2, sw: 5 }));
        g.push(pdot(X(n), ly, C1, 5.5));
        g.push(ctxt(X(n) + 10, ly - 12, `n = ${n}`, C1, { anchor: 'start', bold: true }));
        g.push(ctxt((X(base) + X(n)) / 2, ly - 16, `r = ${r}`, C2, { anchor: 'middle', bold: true }));
        g.push(ctxt(X(base), ly + 42, `q · 7 = ${base}`, C2, { anchor: 'middle' }));
        g.push(ctxt(60, ly - 34, `${note}: ${n} = (${q}) · 7 + ${r}`, CI, { anchor: 'start', bold: true }));
    }
    g.push(txt(W / 2, 288, '몫 q 는 n 을 넘지 않는 가장 오른쪽 눈금의 번호이므로 ⌊n / 7⌋ 이고, 나머지 r 는 그 눈금에서 오른쪽으로 남은 거리다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '나눗셈 정리에서 몫과 나머지',
        desc: '수직선 위에 7 의 배수를 표시하고, 25 와 -11 에 대해 몫과 나머지를 읽어내는 그림',
        body: g.join(''),
    });
})());

/* 2. 유클리드 알고리즘의 불변량 */
add('mcs-n-euclid-trace', (() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '유클리드 알고리즘 — 오른쪽 열이 한 줄도 바뀌지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ctxt(50, 52, 'gcd(1147, 899) = 31', C1, { bold: true }));
    g.push(table(50, 62, ['x', 'y', 'rem(x, y)', 'gcd(x, y)'], [
        ['1147', '899', '248', '31'], ['899', '248', '155', '31'], ['248', '155', '93', '31'],
        ['155', '93', '62', '31'], ['93', '62', '31', '31'], ['62', '31', '0', '31'], ['31', '0', '—', '31'],
    ], { cw: 75, rh: 25, hlCol: [3] }));
    g.push(ctxt(420, 52, 'gcd(26, 21) = 1', C1, { bold: true }));
    g.push(table(420, 62, ['x', 'y', 'rem(x, y)', 'gcd(x, y)'], [
        ['26', '21', '5', '1'], ['21', '5', '1', '1'], ['5', '1', '0', '1'], ['1', '0', '—', '1'],
    ], { cw: 70, rh: 25, hlCol: [3] }));
    g.push(ctxt(420, 220, '두 걸음이면 x 가 절반 이하로 줄어든다.', CK));
    g.push(ctxt(420, 238, '그래서 걸음 수가 자릿수에 비례한다.', CK));
    g.push(txt(W / 2, 285, 'y 가 0 이 되는 순간 gcd(x, 0) = x 이므로 그때의 x 가 답이다. 표는 확인이지 증명이 아니다 —',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 305, '증명은 gcd(x, y) = gcd(y, rem(x, y)) 한 줄이 전이마다 성립함을 보이는 것이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '유클리드 알고리즘의 보존되는 불변량',
        desc: '두 예에 대해 상태 (x, y) 가 바뀌는 동안 gcd 열이 상수로 유지되는 것을 보이는 표',
        body: g.join(''),
    });
})());

/* 3. 물통에서 얻을 수 있는 양 = gcd 의 배수 */
add('mcs-n-jug-reachable', (() => {
    const W = 720, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '두 물통으로 얻을 수 있는 양은 정확히 두 용량의 최대공약수의 배수다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const cw = 23, x = 60;
    // 패널 A: 12 L, 18 L → gcd 6
    g.push(ctxt(x, 62, '통 12 L 와 18 L — gcd 는 6', CI, { bold: true }));
    const itemsA = [], hlA = {};
    for (let k = 0; k <= 18; k += 1) {
        itemsA.push(k);
        if (k % 6 === 0) hlA[k] = C3;
        if (k === 4) hlA[k] = C2;
    }
    g.push(strip(x, 74, cw, 30, itemsA, { hl: hlA }));
    g.push(ctxt(x + 19 * cw + 12, 94, '4 는 6 의 배수가 아니다 → 불가능', C2));

    // 패널 B: 21 L, 26 L → gcd 1
    g.push(ctxt(x, 168, '통 21 L 와 26 L — gcd 는 1', CI, { bold: true }));
    const itemsB = [], hlB = {};
    for (let k = 0; k <= 26; k += 1) {
        itemsB.push(k);
        hlB[k] = C3;
        if (k === 3) hlB[k] = C1;
    }
    g.push(strip(x, 180, cw, 30, itemsB, { hl: hlB }));
    g.push(ctxt(x, 230, '3 은 1 의 배수 → 가능. 사실 0 부터 26 까지 전부 만들 수 있다', C1));

    g.push(txt(W / 2, 272, '초록 칸이 만들 수 있는 양이다. 두 통을 다루는 어떤 순서도 물의 양을 두 용량의 정수 선형결합으로만 만들고,',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 290, '그 선형결합 전체가 바로 gcd 의 배수 전체다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '물통 문제에서 도달 가능한 양',
        desc: '용량 12 와 18 에서는 6 의 배수만, 21 과 26 에서는 모든 정수가 얻어진다는 것을 눈금으로 보인 그림',
        body: g.join(''),
    });
})());

/* 4. 합동류가 정수를 조각낸다 */
add('mcs-n-mod-classes', (() => {
    const W = 720, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '5 를 법으로 하는 합동 — 정수 전체가 다섯 조각으로 갈린다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const cols = [C1, C2, C3, CK, CI];
    for (let i = 0; i < 5; i += 1) {
        const y = 56 + i * 44;
        g.push(ctxt(48, y + 21, `나머지 ${i}`, cols[i], { anchor: 'end', bold: true }));
        const items = [];
        for (let j = -2; j <= 2; j += 1) items.push(i + 5 * j);
        const hl = {};
        for (let k = 0; k < 5; k += 1) hl[k] = cols[i];
        g.push(strip(58, y, 48, 30, items, { hl }));
        g.push(ctxt(316, y + 21, '…', CK));
    }
    // Z~5 의 바퀴
    const cx = 540, cy = 150, R = 74;
    const pos = k => [cx + R * Math.cos(((90 - 72 * k) * Math.PI) / 180), cy - R * Math.sin(((90 - 72 * k) * Math.PI) / 180)];
    for (let k = 0; k < 5; k += 1) {
        const a = pos(k), b = pos((k + 1) % 5);
        g.push(edge(a, b, { col: CK, bend: 9, r: 19 }));
    }
    for (let k = 0; k < 5; k += 1) g.push(node(pos(k), String(k), { col: cols[k], fill: cols[k], r: 19 }));
    g.push(ctxt(cx, cy + R + 52, 'Z~5 — 더하기 1 은 한 칸 돌기다', CI, { anchor: 'middle', bold: true }));
    g.push(txt(W / 2, 288, '왼쪽 한 줄이 하나의 합동류이고, 오른쪽은 그 다섯 줄에 이름을 붙여 만든 수 체계 Z~5 다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '5 를 법으로 하는 합동류와 Z 5',
        desc: '정수를 나머지에 따라 다섯 줄로 나눈 표와, 그 다섯 줄을 원소로 갖는 바퀴 모양 수 체계',
        body: g.join(''),
    });
})());

/* 5. 역원이 있는 것과 없는 것 */
add('mcs-n-inverse-table', (() => {
    const W = 720, H = 310;
    const g = [];
    g.push(txt(W / 2, 26, '곱셈 역원은 법과 서로소인 수에만 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const inv15 = { 1: 1, 2: 8, 4: 4, 7: 13, 8: 2, 11: 11, 13: 7, 14: 14 };
    const items15 = [], hl15 = {}, un15 = [];
    for (let k = 0; k <= 14; k += 1) {
        items15.push(k);
        if (inv15[k] !== undefined) { hl15[k] = C3; un15.push({ t: inv15[k], c: C3 }); }
        else { hl15[k] = k === 3 ? C2 : undefined; un15.push({ t: '없음', c: k === 3 ? C2 : CK }); }
    }
    g.push(ctxt(58, 58, 'n = 15 — 15 와 서로소인 8 개에만 역원이 있다. φ(15) = 8', CI, { bold: true }));
    g.push(strip(58, 68, 40, 30, items15, { hl: hl15, under: un15 }));
    g.push(ctxt(58, 142, '아래 줄이 그 수의 역원이다.', CK));

    const inv7 = { 1: 1, 2: 4, 3: 5, 4: 2, 5: 3, 6: 6 };
    const items7 = [], hl7 = {}, un7 = [];
    for (let k = 0; k <= 6; k += 1) {
        items7.push(k);
        if (inv7[k] !== undefined) { hl7[k] = C3; un7.push({ t: inv7[k], c: C3 }); }
        else un7.push({ t: '없음', c: CK });
    }
    g.push(ctxt(58, 186, 'n = 7 (소수) — 0 을 뺀 6 개 전부에 역원이 있다. φ(7) = 6', CI, { bold: true }));
    g.push(strip(58, 196, 40, 30, items7, { hl: hl7, under: un7 }));

    g.push(ctxt(390, 196, '법이 소수이면 0 만 빼고 다 가역이다.', CK));
    g.push(ctxt(390, 216, '그래서 유리수처럼 나눗셈이 자유롭다.', CK));
    g.push(ctxt(390, 240, '3 은 15 와 서로소가 아니라 역원이 없고,', C2));
    g.push(ctxt(390, 258, '그래서 3 · 10 = 3 · 5 (Z~{15}) 에서', C2));
    g.push(ctxt(390, 276, '3 을 지울 수 없다.', C2));
    g.push(txt(240, 300, '역원이 있는 것 · 소거할 수 있는 것 · 법과 서로소인 것이 정확히 같은 셋이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '법 15 와 법 7 에서의 곱셈 역원',
        desc: '0 부터 14 까지, 0 부터 6 까지 각 수에 역원이 있는지와 그 값을 나란히 보인 그림',
        body: g.join(''),
    });
})());

/* 6. 오일러 정리의 뼈대 — 곱하기 k 는 뒤섞기다 */
add('mcs-n-euler-shuffle', (() => {
    const W = 720, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '오일러 정리의 뼈대 — ‘곱하기 3’ 은 가역원들을 잃지도 겹치지도 않고 뒤섞는다',
        { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const left = [1, 3, 7, 9];
    const map = { 1: 3, 3: 9, 7: 1, 9: 7 };
    const ys = [80, 132, 184, 236];
    g.push(ctxt(180, 58, '10 과 서로소인 수 넷', CI, { anchor: 'middle', bold: true }));
    g.push(ctxt(430, 58, '각각에 3 을 곱한 것', CI, { anchor: 'middle', bold: true }));
    left.forEach((v, i) => {
        g.push(node([180, ys[i]], String(v), { col: C1, fill: C1, r: 19 }));
        g.push(node([430, ys[i]], String(v), { col: C3, fill: C3, r: 19 }));
    });
    left.forEach((v, i) => {
        const j = left.indexOf(map[v]);
        g.push(edge([180, ys[i]], [430, ys[j]], { col: C2, r: 20, sw: 1.6 }));
    });
    g.push(ctxt(560, 110, '넷이 넷으로,', CK));
    g.push(ctxt(560, 128, '한 칸에 하나씩 간다.', CK));
    g.push(ctxt(560, 156, '그래서 오른쪽 전체가', C3));
    g.push(ctxt(560, 174, '왼쪽 전체와 같은 집합이다.', C3));
    g.push(txt(W / 2, 278, '넷을 다 곱한 값을 P 라 하면, 화살표를 따라 곱해도 같은 P 가 나오는데 그 곱은 3⁴ · P 이기도 하다.',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 298, 'P 는 가역원이므로 지울 수 있고, 3⁴ = 1 (Z~{10}) 이 남는다. 여기서 4 는 φ(10) 이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '오일러 정리의 증명 뼈대',
        desc: '10 과 서로소인 네 수에 3 을 곱하면 같은 네 수의 순서만 바뀐다는 것을 화살표로 보인 그림',
        body: g.join(''),
    });
})());

/* ================================================================== *
 * 12장 — 방향 그래프와 부분순서
 * ================================================================== */

/** 12장에서 되풀이 쓰는 4정점 방향 그래프. 간선 여섯 개. */
const G4 = {
    pos: { a: [130, 80], b: [300, 80], c: [300, 215], d: [130, 215] },
    edges: [['a', 'b', 0], ['a', 'd', 0], ['b', 'd', 0], ['b', 'c', 26], ['c', 'b', 26], ['d', 'c', 0]],
};

function drawG4(dx, dy, s, { hl = [], hlCol = C2, r = NR, nodeCol = CI, baseCol = CG } = {}) {
    const P = k => [G4.pos[k][0] * s + dx, G4.pos[k][1] * s + dy];
    const key = (u, v) => `${u}${v}`;
    const g = [];
    for (const [u, v, bend] of G4.edges) {
        const on = hl.includes(key(u, v));
        g.push(edge(P(u), P(v), { col: on ? hlCol : baseCol, sw: on ? 3 : 1.6, bend: bend * s, r }));
    }
    for (const k of Object.keys(G4.pos)) g.push(node(P(k), k, { col: nodeCol, r }));
    return g.join('');
}

/* 7. 방향 그래프와 차수 */
add('mcs-n-digraph-degrees', (() => {
    const W = 720, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '방향 그래프 — 진입차수의 합과 진출차수의 합은 언제나 같다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(drawG4(-20, 30, 1, { nodeCol: C1, baseCol: CK }));
    g.push(ctxt(215, 285, '정점 4 개, 간선 6 개', CK, { anchor: 'middle' }));
    g.push(ctxt(300, 155, '두 정점 사이에', CK));
    g.push(ctxt(300, 173, '양방향 간선이 있어도 된다', CK));
    g.push(table(430, 60, ['정점', '진입차수', '진출차수'], [
        ['a', '0', '2'], ['b', '2', '2'], ['c', '2', '1'], ['d', '2', '1'], ['합', '6', '6'],
    ], { cw: 82, rh: 27, hlCol: [1, 2], hlColor: C3 }));
    g.push(ctxt(430, 250, '두 합이 같은 것은 둘 다 간선 수를 센 것이기 때문이다.', CK));
    g.push(ctxt(430, 268, '간선마다 머리가 하나, 꼬리가 하나다.', CK));
    return svg({
        width: W, height: H,
        title: '방향 그래프의 진입차수와 진출차수',
        desc: '정점 네 개와 간선 여섯 개인 방향 그래프와, 정점마다 진입차수와 진출차수를 적은 표',
        body: g.join(''),
    });
})());

/* 8. 보행 · 경로 · 순환 */
add('mcs-n-walk-path-cycle', (() => {
    const W = 720, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '같은 그래프에서 읽는 세 가지', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const panels = [
        [10, '경로 abd', '정점을 다시 밟지 않는다. 길이 2', ['ab', 'bd']],
        [246, '보행 abcbd', 'b 를 두 번 밟는다. 길이 4', ['ab', 'bc', 'cb', 'bd']],
        [482, '순환 bdcb', '시작과 끝만 같다. 길이 3', ['bd', 'dc', 'cb']],
    ];
    for (const [px, title, sub, hl] of panels) {
        g.push(panel(px, 44, 228, 232, title, sub));
        g.push(drawG4(px - 12, 66, 0.62, { hl, hlCol: C2, r: 13 }));
    }
    g.push(txt(W / 2, 293, '보행에서 되풀이되는 조각을 잘라내면 경로가 되므로, 가장 짧은 보행은 언제나 경로다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '보행과 경로와 순환',
        desc: '같은 방향 그래프에서 경로 abd, 보행 abcbd, 순환 bdcb 를 각각 굵게 표시한 세 그림',
        body: g.join(''),
    });
})());

/* 9. 인접행렬의 거듭제곱이 보행 수를 센다 */
add('mcs-n-adjacency-power', (() => {
    const W = 720, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '인접행렬을 제곱하면 길이 2 인 보행의 수가 나온다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const A = [[0, 1, 0, 1], [0, 0, 1, 1], [0, 1, 0, 0], [0, 0, 1, 0]];
    const A2 = [[0, 0, 2, 1], [0, 1, 1, 0], [0, 0, 1, 1], [0, 1, 0, 0]];
    const lab = ['a', 'b', 'c', 'd'];

    function matrix(x, y, M, { hl = null } = {}) {
        const cw = 34, rh = 28;
        const gg = [];
        lab.forEach((c, j) => gg.push(ctxt(x + 30 + j * cw + cw / 2, y + 14, c, C1, { anchor: 'middle', bold: true })));
        M.forEach((row, i) => {
            gg.push(ctxt(x + 24, y + 24 + (i + 1) * rh - 9, lab[i], C1, { anchor: 'end', bold: true }));
            row.forEach((v, j) => {
                const on = hl && hl[0] === i && hl[1] === j;
                if (on) gg.push(box(x + 30 + j * cw + 3, y + 24 + i * rh + 5, cw - 6, rh - 6, { fill: C2, op: 0.25, stroke: C2, sw: 1.6, rx: 3 }));
                gg.push(ctxt(x + 30 + j * cw + cw / 2, y + 24 + (i + 1) * rh - 9, String(v), on ? C2 : CI, { anchor: 'middle', bold: on }));
            });
        });
        gg.push(ln([[x + 26, y + 22], [x + 26, y + 24 + 4 * rh + 4]], { col: CK, sw: 1.4 }));
        gg.push(ln([[x + 34 + 4 * cw, y + 22], [x + 34 + 4 * cw, y + 24 + 4 * rh + 4]], { col: CK, sw: 1.4 }));
        return gg.join('');
    }
    g.push(ctxt(115, 58, '인접행렬 A', CI, { anchor: 'middle', bold: true }));
    g.push(matrix(40, 62, A));
    g.push(ctxt(345, 58, 'A 를 제곱한 것', CI, { anchor: 'middle', bold: true }));
    g.push(matrix(270, 62, A2, { hl: [0, 2] }));
    g.push(ctxt(232, 130, '×', CK, { anchor: 'middle', size: null }));

    g.push(panel(490, 62, 210, 150, 'a 에서 c 로 가는 길이 2 보행', '두 개다 — 그래서 칸이 2'));
    const P = { a: [530, 110], b: [660, 110], d: [530, 180], c: [660, 180] };
    g.push(edge(P.a, P.b, { col: C2, sw: 2.4, r: 15 }));
    g.push(edge(P.b, P.c, { col: C2, sw: 2.4, r: 15 }));
    g.push(edge(P.a, P.d, { col: C3, sw: 2.4, r: 15 }));
    g.push(edge(P.d, P.c, { col: C3, sw: 2.4, r: 15 }));
    for (const k of ['a', 'b', 'c', 'd']) g.push(node(P[k], k, { col: CI, r: 15 }));

    g.push(txt(W / 2, 248, '이유는 곱셈의 정의 그대로다. u 에서 v 로 가는 길이 2 보행은 가운데 정점 w 를 하나 고르는 일이고,',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 268, 'w 마다 (u 에서 w 로 가는 수) × (w 에서 v 로 가는 수) 를 더한 것이 곧 행렬 곱의 (u, v) 성분이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 296, '같은 논증을 되풀이하면 A 의 k 제곱이 길이 k 보행을 센다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '인접행렬의 제곱과 보행의 수',
        desc: '인접행렬과 그 제곱을 나란히 놓고, 값이 2 인 성분에 대응하는 두 보행을 오른쪽에 그린 그림',
        body: g.join(''),
    });
})());

/* 10. 사슬과 반사슬 */
add('mcs-n-chain-antichain', (() => {
    const W = 720, H = 402;
    const g = [];
    g.push(txt(W / 2, 26, '사슬과 반사슬 — 최소 병렬 시간은 가장 긴 사슬의 크기다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const P = {
        A: [160, 80], B: [300, 80],
        C: [160, 148], G: [340, 148],
        D: [100, 216], E: [220, 216],
        F: [160, 284],
        H: [250, 348],
    };
    const E = [['A', 'C'], ['B', 'C'], ['B', 'G'], ['C', 'D'], ['C', 'E'], ['D', 'F'], ['E', 'F'], ['G', 'H'], ['F', 'H']];
    const chain = ['AC', 'CD', 'DF', 'FH'];
    const bands = [[80, ['A', 'B']], [148, ['C', 'G']], [216, ['D', 'E']], [284, ['F']], [348, ['H']]];
    bands.forEach(([y], i) => {
        g.push(box(50, y - 26, 350, 52, { fill: CG, op: 0.35, stroke: 'none', rx: 8 }));
        g.push(ctxt(44, y + 5, `${i + 1}단계`, CK, { anchor: 'end' }));
    });
    for (const [u, v] of E) {
        const on = chain.includes(`${u}${v}`);
        g.push(edge(P[u], P[v], { col: on ? C2 : CK, sw: on ? 3.2 : 1.5, r: 16 }));
    }
    const anti = ['D', 'E', 'G'];
    for (const k of Object.keys(P)) {
        g.push(node(P[k], k, { col: anti.includes(k) ? C3 : CI, fill: anti.includes(k) ? C3 : 'none', r: 16 }));
    }
    g.push(ctxt(430, 80, '사슬 A → C → D → F → H', C2, { bold: true }));
    g.push(ctxt(430, 100, '다섯이 서로 견줄 수 있으므로 어떤 두 개도', CK));
    g.push(ctxt(430, 118, '같은 단계에 둘 수 없다. 그래서 5 단계보다', CK));
    g.push(ctxt(430, 136, '빨리 끝낼 수 없다.', CK));
    g.push(ctxt(430, 178, '반사슬 {D, E, G}', C3, { bold: true }));
    g.push(ctxt(430, 198, '셋 사이에는 어떤 방향으로도 길이 없다.', CK));
    g.push(ctxt(430, 216, '그래서 셋을 동시에 해도 된다.', CK));
    g.push(ctxt(430, 258, '깊이별 묶음 다섯 개가 각각 반사슬이고,', CK));
    g.push(ctxt(430, 276, '그 다섯으로 정점 여덟이 모두 덮인다.', CK));
    g.push(ctxt(430, 300, '딜워스: 사슬의 크기가 t 이하이면', C1));
    g.push(ctxt(430, 318, '반사슬의 크기가 8/t 이상이다.', C1));
    g.push(ctxt(430, 336, 't = 4 로 두면 2 이상이 된다.', C1));
    g.push(txt(225, 396, '띠 하나가 한 단계다. 굵은 길이 임계 경로이고 초록 정점이 최대 반사슬이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: 'DAG 의 사슬과 반사슬',
        desc: '여덟 작업의 DAG 를 깊이별 띠로 나누고, 크기 5 인 사슬과 크기 3 인 반사슬을 표시한 그림',
        body: g.join(''),
    });
})());

/* 11. 관계의 성질을 방향 그래프로 */
add('mcs-n-relation-props', (() => {
    const W = 720, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '관계의 성질은 방향 그래프의 모양이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const cells = [
        ['반사', '모든 정점에 자기 고리', { loops: ['x', 'y', 'z'], arcs: [['x', 'y', 0]] }],
        ['비반사', '자기 고리가 하나도 없다', { loops: [], arcs: [['x', 'y', 0], ['y', 'z', 0]] }],
        ['대칭', '한 방향이 있으면 반대도 있다', { loops: [], arcs: [['x', 'y', 15], ['y', 'x', 15], ['y', 'z', 15], ['z', 'y', 15]] }],
        ['비대칭', '한 방향만. 자기 고리도 없다', { loops: [], arcs: [['x', 'y', 0], ['y', 'z', 0], ['x', 'z', 0]] }],
        ['반대칭', '한 방향만. 다만 자기 고리는 허용', { loops: ['x', 'y', 'z'], arcs: [['x', 'y', 0], ['y', 'z', 0], ['x', 'z', 0]] }],
        ['추이', '두 걸음으로 가면 한 걸음으로도 간다', { loops: [], arcs: [['x', 'y', 0], ['y', 'z', 0], ['x', 'z', 0]] }],
    ];
    cells.forEach((cell, i) => {
        const cx = 15 + (i % 3) * 232;
        const cy = 42 + Math.floor(i / 3) * 172;
        const [title, sub, spec] = cell;
        g.push(panel(cx, cy, 224, 162, title, null));
        const pos = { x: [cx + 112, cy + 66], y: [cx + 62, cy + 126], z: [cx + 162, cy + 126] };
        const col = i === 5 ? C3 : C1;
        for (const [u, v, bend] of spec.arcs) {
            const dashed = i === 5 && u === 'x' && v === 'z';
            g.push(edge(pos[u], pos[v], { col: dashed ? C2 : col, bend, r: 15, sw: dashed ? 2.6 : 1.7, dash: dashed ? '5 4' : undefined }));
        }
        for (const u of spec.loops) g.push(loop(pos[u], { col: C3, r: 15 }));
        for (const k of ['x', 'y', 'z']) g.push(node(pos[k], k, { col: CI, r: 15 }));
        g.push(ctxt(cx + 112, cy + 154, sub, CK, { anchor: 'middle' }));
    });
    g.push(txt(W / 2, 392, '오른쪽 아래의 점선이 추이가 요구하는 간선이다. 강한 부분순서는 비반사이면서 추이인 것,'
        + ' 약한 부분순서는 반사이면서 반대칭이고 추이인 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '관계의 성질을 방향 그래프로 그린 것',
        desc: '반사 · 비반사 · 대칭 · 비대칭 · 반대칭 · 추이를 각각 정점 세 개짜리 방향 그래프로 보인 여섯 그림',
        body: g.join(''),
    });
})());

/* 12. 하세 다이어그램 */
add('mcs-n-hasse-divides', (() => {
    const W = 720, H = 380;
    const g = [];
    g.push(txt(W / 2, 26, '하세 다이어그램 — 되풀이되는 간선을 지우면 남는 것', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const base = { 1: [190, 320], 2: [120, 246], 3: [270, 246], 4: [90, 172], 6: [230, 172], 8: [70, 98], 12: [200, 98] };
    const cover = [[1, 2], [1, 3], [2, 4], [2, 6], [3, 6], [4, 8], [4, 12], [6, 12]];
    const derived = [[1, 4], [1, 6], [1, 8], [1, 12], [2, 8], [2, 12], [3, 12]];
    const keys = Object.keys(base);

    function draw(dx, { showDerived }) {
        const P = k => [base[k][0] + dx, base[k][1]];
        const gg = [];
        for (const [u, v] of cover) gg.push(edge(P(u), P(v), { col: showDerived ? CG : C1, sw: showDerived ? 1.4 : 2.2, r: 16 }));
        if (showDerived) {
            for (const [u, v] of derived) gg.push(edge(P(u), P(v), { col: C2, sw: 1.6, r: 16, dash: '5 4', bend: 14 }));
            for (const k of keys) gg.push(loop(P(k), { col: C3, r: 16 }));
        }
        for (const k of keys) gg.push(node(P(k), k, { col: CI, r: 16 }));
        return gg.join('');
    }
    g.push(panel(20, 44, 330, 306, '하세 다이어그램 — 간선 8 개', '덮음 간선만 그린다'));
    g.push(draw(10, { showDerived: false }));
    g.push(panel(370, 44, 330, 306, '약한 부분순서 전체 — 간선 22 개', '점선과 자기 고리는 되풀이다'));
    g.push(draw(360, { showDerived: true }));
    g.push(txt(W / 2, 372, '{1, 2, 3, 4, 6, 8, 12} 위의 나누어떨어짐 관계다. 아래에서 위로 읽는다 —'
        + ' 선이 있으면 아래가 위를 나눈다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '나누어떨어짐 관계의 하세 다이어그램',
        desc: '일곱 수 위의 나누어떨어짐 관계를 덮음 간선만으로 그린 것과, 자기 고리와 추이 간선까지 모두 그린 것의 대조',
        body: g.join(''),
    });
})());

/* 13. 동치관계와 분할 */
add('mcs-n-equiv-partition', (() => {
    const W = 720, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '동치관계의 화살표는 덩어리 안에서만 오간다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(panel(20, 44, 330, 210, '관계로 본 모습', '반사 · 대칭 · 추이를 모두 만족한다'));
    const L = { a: [95, 120], b: [175, 100], c: [135, 190], d: [250, 120], e: [310, 175], f: [250, 210] };
    const blocks = [['a', 'b', 'c'], ['d', 'e', 'f']];
    blocks.forEach((blk, bi) => {
        const col = bi === 0 ? C1 : C3;
        for (let i = 0; i < blk.length; i += 1) {
            for (let j = i + 1; j < blk.length; j += 1) {
                g.push(edge(L[blk[i]], L[blk[j]], { col, bend: 8, r: 15, sw: 1.5 }));
                g.push(edge(L[blk[j]], L[blk[i]], { col, bend: 8, r: 15, sw: 1.5 }));
            }
        }
        for (const k of blk) g.push(loop(L[k], { col, r: 15 }));
        for (const k of blk) g.push(node(L[k], k, { col: CI, r: 15 }));
    });
    g.push(panel(390, 44, 310, 210, '분할로 본 모습', '블록 두 개가 정점 전체를 덮는다'));
    g.push(box(415, 92, 250, 62, { fill: C1, op: 0.16, stroke: C1, sw: 1.8, rx: 10 }));
    g.push(ctxt(540, 128, '{a, b, c} — a 의 동치류', CI, { anchor: 'middle', bold: true }));
    g.push(box(415, 172, 250, 62, { fill: C3, op: 0.16, stroke: C3, sw: 1.8, rx: 10 }));
    g.push(ctxt(540, 208, '{d, e, f} — d 의 동치류', CI, { anchor: 'middle', bold: true }));
    g.push(txt(W / 2, 278, '왼쪽에서 오른쪽으로 가는 것은 동치류를 모으는 일이고, 오른쪽에서 왼쪽으로 가는 것은',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 296, '‘같은 블록에 있다’ 를 관계로 읽는 일이다. 두 방향이 서로 역이라 동치관계와 분할은 같은 것이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return svg({
        width: W, height: H,
        title: '동치관계와 분할',
        desc: '여섯 원소 위의 동치관계를 화살표로 그린 것과, 같은 것을 두 블록의 분할로 적은 것의 대조',
        body: g.join(''),
    });
})());

export default figures;
