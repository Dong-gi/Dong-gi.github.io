/**
 * 알고리즘 10장(분할정복) · 11장(탐욕 알고리즘) · 12장(동적 계획법) ·
 * 13장(계산 복잡도 — P와 NP)의 그림.
 *
 * 이름은 모두 `alg-d-` 로 시작한다(담당 C 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `d~0` 표기를, 나머지는 유니코드(≤ ≥ ∞ → ⌊⌋ ² ⁿ ₂ × ∨ ¬)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 파일이 있는 이유.
 *   설계 기법은 ‘결과’ 가 아니라 ‘고르는 순간’ 을 보여야 한다.
 *   탐욕이 왜 틀리는지는 반례 그림 한 장이 열 문단보다 낫고(11장 거스름돈),
 *   동적 계획법은 재귀 트리가 표로 접히는 장면을 보지 않으면 끝내 외우게 된다(12장).
 *   환원은 화살표 방향이 헷갈리는 곳이라 방향 자체가 그림의 주제다(13장).
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구 — algorithm-basic.mjs · algorithm-graph.mjs 와 같은 규약.
 * ------------------------------------------------------------------ */

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어
 * 선이 사라지고 화살촉만 남는다. 색을 직접 넣는 화살표를 따로 둔다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI }[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 곡선 경로(베지어). 화살촉을 붙일 수 있다. */
function curvePath(d, { stroke = CK, sw = 1.5, dash, marker } = {}) {
    return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}${marker ? ` marker-end="url(#${marker})"` : ''}/>`;
}

/** 두 점을 위로 볼록한 호로 잇고 끝에 화살촉을 단다. */
function arcTo(x1, y1, x2, y2, lift, { stroke = CK, sw = 1.5, marker = 'ark', dash } = {}) {
    const mx = (x1 + x2) / 2;
    const my = Math.min(y1, y2) - lift;
    return curvePath(`M${r2(x1)} ${r2(y1)} Q${r2(mx)} ${r2(my)} ${r2(x2)} ${r2(y2)}`, { stroke, sw, marker, dash });
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 글상자 하나. 가운데 정렬한 여러 줄을 담는다. */
function tbox(x, y, w, h, lines, { stroke = CK, fill = 'none', op = 0.16, cls = 'ink', sw = 1.3, size = 'sm', rx = 4 } = {}) {
    const arr = Array.isArray(lines) ? lines : [lines];
    const lh = size === 'sm' ? 15 : 17;
    const y0 = y + h / 2 - ((arr.length - 1) * lh) / 2 + (size === 'sm' ? 4 : 5);
    return box(x, y, w, h, { stroke, fill, op: fill === 'none' ? 1 : op, sw, rx })
        + arr.map((s, i) => txt(x + w / 2, y0 + i * lh, s, { anchor: 'middle', cls, size })).join('');
}

/**
 * 배열 칸 한 줄. items 의 null 은 빈 칸이다.
 * hl 에 든 인덱스는 강조색으로 칠하고, idx 를 주면 칸 아래(또는 위)에 번호를 적는다.
 */
function cells(x, y, w, h, items, { hl = {}, idx = null, idxTop = false, dim = {}, sw = 1.3, small = false } = {}) {
    const g = [];
    items.forEach((v, i) => {
        const cx = x + i * w;
        const col = hl[i];
        const faded = dim[i];
        g.push(box(cx, y, w, h, {
            fill: col ?? 'none', op: col ? 0.22 : 1,
            stroke: col ?? (faded ? CG : CK), sw: col ? 1.9 : sw, rx: 2,
        }));
        if (v !== null && v !== undefined && v !== '') {
            g.push(txt(cx + w / 2, y + h / 2 + 5, String(v), {
                anchor: 'middle', cls: faded ? 'ink2' : 'ink', size: small || w < 32 ? 'sm' : undefined,
            }));
        }
        if (idx) {
            const ty = idxTop ? y - 6 : y + h + 14;
            g.push(txt(cx + w / 2, ty, String(idx[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    return g.join('');
}

/**
 * 표 격자. rows 는 문자열(또는 null)의 2차원 배열이다.
 * hl 은 '행,열' → 색. head 는 열 머리, side 는 행 머리.
 */
function grid(x, y, cw, ch, rows, { hl = {}, head = null, side = null, headLabel = null, sideLabel = null } = {}) {
    const g = [];
    if (head) {
        head.forEach((h, c) => g.push(txt(x + c * cw + cw / 2, y - 8, String(h), { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    }
    if (headLabel) g.push(txt(x - 10, y - 8, headLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    rows.forEach((row, r) => {
        if (side) g.push(txt(x - 10, y + r * ch + ch / 2 + 4, String(side[r]), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        row.forEach((v, c) => {
            const col = hl[`${r},${c}`];
            g.push(box(x + c * cw, y + r * ch, cw, ch, {
                fill: col ?? 'none', op: col ? 0.22 : 1, stroke: col ?? CG, sw: col ? 1.9 : 1, rx: 2,
            }));
            if (v !== null && v !== undefined && v !== '') {
                g.push(txt(x + c * cw + cw / 2, y + r * ch + ch / 2 + 5, String(v), { anchor: 'middle', cls: 'ink', size: cw < 34 ? 'sm' : undefined }));
            }
        });
    });
    if (sideLabel) g.push(txt(x - 10, y + rows.length * ch + 16, sideLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 트리 노드 한 개. 원과 가운데 글자. */
function tnode(x, y, label, { r = 16, col = null, dim = false, dash, sub = null } = {}) {
    const stroke = col ?? (dim ? CG : CK);
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col ?? 'none'}" fill-opacity="${col ? 0.2 : 0}" stroke="${stroke}" stroke-width="${col ? 2 : 1.4}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`
        + txt(x, y + 4, label, { anchor: 'middle', cls: dim ? 'ink2' : 'ink', size: r < 16 ? 'sm' : undefined })
        + (sub ? txt(x, y + r + 14, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 두 노드의 중심을 잇되 원 반지름만큼 잘라 그린다. */
function tedge(p1, p2, { r1 = 16, r2r = 16, stroke = CK, sw = 1.4, dash, label = null, lcls = 'ink2' } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    const a = [p1[0] + ux * r1, p1[1] + uy * r1];
    const b = [p2[0] - ux * r2r, p2[1] - uy * r2r];
    return ln([a, b], { stroke, sw, dash })
        + (label ? txt((a[0] + b[0]) / 2 + (dx > 0 ? 7 : -7), (a[1] + b[1]) / 2 - 2, label, { anchor: dx > 0 ? 'start' : 'end', cls: lcls, size: 'sm' }) : '');
}

/** 좌표계 위 곡선을 직접 색을 넣어 그린다. lib 의 curve 는 클래스만 받는다. */
function fcurve(f, fn, { from, to, steps = 200, stroke = C1, sw = 2.2, dash } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const xv = from + ((to - from) * i) / steps;
        pts.push([f.X(xv), f.Y(fn(xv))]);
    }
    return ln(pts, { stroke, sw, dash });
}

/** 작은 패널용 축. lib 의 axes 는 글자가 커서 좁은 칸에서 겹친다. */
function axes2(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel, fmt = String, yFmt = null } = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = f.Y(y0);
    const ay = f.X(x0);
    const g = [arw(ay, ax, f.X(x1) + 12, ax, { cls: 'ark', width: 1.2 }),
        arw(ay, ax, ay, f.Y(y1) - 12, { cls: 'ark', width: 1.2 })];
    for (const t of xTicks) {
        g.push(ln([[f.X(t), ax], [f.X(t), ax + 4]], { sw: 1 }));
        g.push(txt(f.X(t), ax + 17, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        g.push(ln([[ay - 4, f.Y(t)], [ay, f.Y(t)]], { sw: 1 }));
        g.push(txt(ay - 8, f.Y(t) + 4, (yFmt ?? fmt)(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 16, ax + 5, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay - 4, f.Y(y1) - 18, yLabel, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return g.join('');
}

const lg2 = v => Math.log(v) / Math.LN2;

/* ================================================================== *
 * 10장 — 분할정복
 * ================================================================== */

/* ---- 10-1. 세 단계와 재귀식의 대응 ---- */
add((() => {
    const W = 800, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '나누고 · 풀고 · 합친다 — 세 단계가 그대로 재귀식의 세 자리가 된다', { anchor: 'middle', cls: 'ink bold' }));

    const cx = 300;
    g.push(tbox(cx - 90, 46, 180, 34, '크기 n 인 문제', { stroke: CI, sw: 1.6 }));

    const kids = [130, 300, 470];
    for (const k of kids) {
        g.push(arw(cx, 80, k, 116, { cls: 'ark', width: 1.4 }));
        g.push(tbox(k - 62, 118, 124, 32, '크기 n/b', { stroke: C1, fill: C1 }));
        g.push(arw(k, 150, k, 186, { cls: 'ark', width: 1.4, dash: '4 3' }));
        g.push(tbox(k - 62, 188, 124, 30, '부분해', { stroke: CG }));
        g.push(arw(k, 218, cx, 252, { cls: 'ark', width: 1.4 }));
    }
    g.push(txt(600, 100, '1. 나눈다 — a 개로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 172, '2. 정복한다 — 각각을 재귀로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 246, '3. 합친다 — f(n) 만큼 일한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 130, '(그림에서는 a = 3)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 202, '(크기 1 이 되면 바로 답한다)', { cls: 'ink2', size: 'sm' }));
    g.push(tbox(cx - 100, 254, 200, 34, '전체의 답', { stroke: C2, fill: C2, sw: 1.6 }));

    g.push(ln([[40, 306], [W - 40, 306]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 332, 'T(n) = a · T(n/b) + f(n)', { anchor: 'middle', cls: 'ink bold' }));
    const legendItems = [
        ['a', '몇 조각으로 나누는가', C1],
        ['b', '조각 하나가 얼마나 작아지는가', C1],
        ['f(n)', '나누고 합치는 데 드는 일', C2],
    ];
    legendItems.forEach((it, i) => {
        const x = 74 + i * 240;
        g.push(box(x, 350, 11, 11, { fill: it[2], op: 0.55, stroke: it[2], sw: 1.2, rx: 2 }));
        g.push(txt(x + 18, 360, `${it[0]} — ${it[1]}`, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 8, '재귀식을 세우는 일은 코드를 읽어 이 세 자리를 채우는 일이다. 나머지는 마스터 정리가 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-divide-to-recurrence',
        svg: svg({
            width: W, height: H,
            title: '분할정복의 세 단계와 재귀식',
            desc: '문제를 a 개로 나누고 각각을 재귀로 풀고 f(n) 만큼 들여 합친다. 그 셋이 재귀식의 a, b, f(n) 이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-2. 이진 탐색 — 후보가 반씩 준다 ---- */
add((() => {
    const W = 812, H = 348;
    const g = [];
    const a = [2, 5, 8, 12, 16, 23, 38, 45, 56, 61, 72, 73, 80, 88, 91, 99];
    g.push(txt(W / 2, 26, '이진 탐색 — 한 번 견줄 때마다 남은 후보가 반이 된다 (72 를 찾는다)', { anchor: 'middle', cls: 'ink bold' }));

    const x0 = 48, cw = 34, chh = 30;
    const steps = [
        { lo: 0, hi: 15, mid: 7, note: '72 > 45 · 오른쪽만' },
        { lo: 8, hi: 15, mid: 11, note: '72 < 73 · 왼쪽만' },
        { lo: 8, hi: 10, mid: 9, note: '72 > 61 · 오른쪽만' },
        { lo: 10, hi: 10, mid: 10, note: '찾았다 · 첨자 10' },
    ];
    steps.forEach((s, i) => {
        const y = 62 + i * 62;
        const hl = {}, dim = {};
        a.forEach((_, j) => { if (j < s.lo || j > s.hi) dim[j] = true; });
        hl[s.mid] = i === 3 ? C3 : C2;
        g.push(txt(38, y + 20, `${i + 1}회`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(cells(x0, y, cw, chh, a, { hl, dim, idx: i === 3 ? a.map((_, j) => j) : null, small: true }));
        g.push(txt(x0 + s.mid * cw + cw / 2, y - 6, '가운데', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 + 16 * cw + 12, y + 20, s.note, { cls: i === 3 ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(x0 + 16 * cw + 12, y + 36, `남은 후보 ${s.hi - s.lo + 1}개`, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 30, '16 → 8 → 4 → 2 → 1. 반씩 줄이므로 견주는 횟수는 log₂ 16 = 4 번을 넘지 않는다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '합치는 일이 없고 한쪽만 재귀하므로 재귀식은 T(n) = T(n/2) + 1 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-binary-search',
        svg: svg({
            width: W, height: H,
            title: '이진 탐색의 네 단계',
            desc: '정렬된 16개에서 가운데와 견주어 절반을 버리기를 되풀이하면 네 번 만에 답에 닿는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-3. 거듭제곱의 빠른 계산 ---- */
add((() => {
    const W = 780, H = 344;
    const g = [];
    g.push(txt(W / 2, 26, 'x¹³ 을 구하는 데 곱셈 다섯 번이면 된다', { anchor: 'middle', cls: 'ink bold' }));

    // 이진 표기
    g.push(txt(64, 68, '13 을 이진법으로 적으면', { cls: 'ink2', size: 'sm' }));
    const bx = 250, bw = 46;
    const bits = ['1', '1', '0', '1'];
    const place = ['8', '4', '2', '1'];
    bits.forEach((b, i) => {
        const on = b === '1';
        g.push(box(bx + i * bw, 52, bw - 6, 30, { fill: on ? C2 : 'none', op: on ? 0.22 : 1, stroke: on ? C2 : CG, sw: on ? 1.8 : 1.2, rx: 3 }));
        g.push(txt(bx + i * bw + (bw - 6) / 2, 72, b, { anchor: 'middle', cls: 'ink' }));
        g.push(txt(bx + i * bw + (bw - 6) / 2, 98, place[i], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(bx + 4 * bw + 12, 72, '= 8 + 4 + 1', { cls: 'ink', size: 'sm' }));
    g.push(txt(bx + 4 * bw + 12, 96, '켜진 자리만 곱한다', { cls: 'ink2', size: 'sm' }));

    // 제곱 사슬
    const cy = 168, sx = 96, gap = 148;
    const chain = [['x', 'x¹'], ['x²', 'x²'], ['x⁴', 'x⁴'], ['x⁸', 'x⁸']];
    chain.forEach((c, i) => {
        const x = sx + i * gap;
        const used = i !== 1; // x¹, x⁴, x⁸ 을 쓴다
        g.push(tbox(x - 40, cy - 20, 80, 40, c[0], { stroke: used ? C2 : CG, fill: used ? C2 : 'none', sw: used ? 1.8 : 1.2, size: undefined }));
        if (i < 3) g.push(arw(x + 40, cy, x + gap - 42, cy, { cls: 'ark', width: 1.6 }));
        if (i < 3) g.push(txt(x + gap / 2, cy - 12, '제곱한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });

    // 곱하기
    const my = 254;
    [0, 2, 3].forEach((i) => {
        const x = sx + i * gap;
        g.push(arw(x, cy + 22, x, my - 14, { cls: 's2', width: 1.6 }));
    });
    g.push(tbox(W / 2 - 200, my - 14, 400, 42, ['x⁸ × x⁴ × x¹ = x¹³', '제곱 3번 + 곱셈 2번'], { stroke: C3, fill: C3, sw: 1.6 }));
    g.push(txt(W / 2, H - 34, '모두 5번. 곧이곧대로 열두 번 곱하는 것과 견주면 자릿수가 늘수록 차이가 벌어진다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 14, '지수를 반으로 줄이는 재귀이므로 T(n) = T(n/2) + 1, 곧 Θ(log n) 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-power-fast',
        svg: svg({
            width: W, height: H,
            title: '거듭제곱의 빠른 계산',
            desc: '지수를 이진법으로 적고 제곱을 되풀이해 만든 값 중 켜진 자리만 곱한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-4. 카라추바 곱셈 ---- */
add((() => {
    const W = 812, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '곱셈 네 번을 세 번으로 — 카라추바가 바꾼 것은 a 하나뿐이다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(txt(W / 2, 56, '1234 = 12·100 + 34,   5678 = 56·100 + 78     (a=12, b=34, c=56, d=78)', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    const pw = 372, ph = 250, py = 76;
    // 왼쪽 — 곧이곧대로
    g.push(panel(22, py, pw, ph, '곧이곧대로 펼치면', '곱셈 4번'));
    const lx = 22 + pw / 2;
    ['(a·100 + b)(c·100 + d)',
        '= ac·10000 + ad·100 + bc·100 + bd'].forEach((s, i) => {
        g.push(txt(lx, py + 66 + i * 20, s, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    [['ac', C1], ['ad', C2], ['bc', C2], ['bd', C1]].forEach((p, i) => {
        const x = 56 + i * 84;
        g.push(tbox(x, py + 118, 68, 34, p[0], { stroke: p[1], fill: p[1], sw: 1.7, size: undefined }));
    });
    g.push(txt(lx, py + 178, 'T(n) = 4 T(n/2) + Θ(n)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(lx, py + 200, '잎 비용 n^log₂4 = n² 이 이긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx, py + 224, 'Θ(n²) — 초등학교 곱셈과 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 카라추바
    g.push(panel(W - 22 - pw, py, pw, ph, '가운데 둘을 하나로 묶으면', '곱셈 3번'));
    const rx = W - 22 - pw / 2;
    ['가운데 계수 ad + bc 는 따로 구하지 않아도 된다',
        '(a+b)(c+d) − ac − bd = ad + bc'].forEach((s, i) => {
        g.push(txt(rx, py + 66 + i * 20, s, { anchor: 'middle', cls: i === 1 ? 'ink' : 'ink2', size: 'sm' }));
    });
    [['ac', C1], ['bd', C1], ['(a+b)(c+d)', C3]].forEach((p, i) => {
        const wBox = i === 2 ? 128 : 68;
        const x = W - 22 - pw + 30 + (i === 0 ? 0 : i === 1 ? 84 : 168);
        g.push(tbox(x, py + 118, wBox, 34, p[0], { stroke: p[1], fill: p[1], sw: 1.7, size: i === 2 ? 'sm' : undefined }));
    });
    g.push(txt(rx, py + 178, 'T(n) = 3 T(n/2) + Θ(n)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx, py + 200, '잎 비용 n^log₂3 ≈ n^1.585', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx, py + 224, 'Θ(n^1.585) — 덧셈이 몇 번 늘어난 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 42, '두 재귀식의 차이는 a 가 4 냐 3 이냐 하나뿐이고, 그 하나가 지수를 2 에서 1.585 로 내린다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 22, 'n = 10000 자리에서 n² 은 10⁸, n^1.585 는 10⁶ 남짓이다. 덧셈 몇 번을 더 하고 곱셈 하나를 던 결과다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-karatsuba',
        svg: svg({
            width: W, height: H,
            title: '카라추바 곱셈이 곱셈 하나를 더는 방법',
            desc: '네 곱 중 가운데 두 개의 합을 한 번의 곱으로 얻어 재귀식의 a 를 4 에서 3 으로 낮춘다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-5. 지수 눈금 ---- */
add((() => {
    const W = 780, H = 268;
    const g = [];
    g.push(txt(W / 2, 26, '분할정복이 옮겨 놓은 것은 계수가 아니라 지수다', { anchor: 'middle', cls: 'ink bold' }));

    const x0 = 80, x1 = W - 60, ay = 150;
    const X = v => x0 + ((v - 1) / 2) * (x1 - x0);
    g.push(arw(x0 - 20, ay, x1 + 20, ay, { cls: 'ark', width: 1.5 }));
    for (let t = 1; t <= 3; t += 0.5) {
        g.push(ln([[X(t), ay], [X(t), ay + 6]], { sw: 1.2 }));
        g.push(txt(X(t), ay + 22, t.toFixed(1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x1 + 26, ay + 5, 'n 의 지수', { cls: 'ink2', size: 'sm' }));

    const marks = [
        { v: 1.585, up: true, col: C2, name: '카라추바 곱셈', sub: 'log₂3' },
        { v: 2, up: false, col: CK, name: '곧이곧대로 곱셈', sub: 'n²' },
        { v: 2.373, up: true, col: C3, name: '이론상 가장 좋은 행렬 곱', sub: '2.37 언저리 · 계속 갱신된다' },
        { v: 2.807, up: false, col: C2, name: '스트라센 행렬 곱', sub: 'log₂7' },
        { v: 3, up: true, col: CK, name: '곧이곧대로 행렬 곱', sub: 'n³' },
    ];
    marks.forEach((m) => {
        const y2 = m.up ? ay - 40 : ay + 46;
        g.push(pdot(X(m.v), ay, m.col, 5));
        g.push(ln([[X(m.v), ay], [X(m.v), y2]], { stroke: m.col, sw: 1.4, dash: '3 3' }));
        const anchor = m.v > 2.6 ? 'end' : m.v < 1.8 ? 'start' : 'middle';
        const dx = anchor === 'end' ? 8 : anchor === 'start' ? -8 : 0;
        g.push(txt(X(m.v) + dx, y2 + (m.up ? -4 : 14), m.name, { anchor, cls: 'ink bold', size: 'sm' }));
        g.push(txt(X(m.v) + dx, y2 + (m.up ? -20 : 30), m.sub, { anchor, cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 30, '지수 0.2 의 차이가 별것 아니게 보이지만 n = 10⁶ 에서는 열여섯 배가 넘는 차이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '오른쪽 끝에 가까운 두 방법은 상수가 워낙 커서 실제로 쓰이는 크기가 서로 다르다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-exponent-line',
        svg: svg({
            width: W, height: H,
            title: '곱셈 알고리즘의 지수 눈금',
            desc: '카라추바는 2 를 1.585 로, 스트라센은 3 을 2.807 로 내렸다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-6. 최근접 점 쌍 ---- */
add((() => {
    const W = 812, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '최근접 점 쌍 — 가운데 띠만 다시 보면 되고, 띠 안에서도 여섯 점만 보면 된다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 전체 그림
    const px0 = 22, py0 = 48, pw = 452, ph = 288;
    g.push(panel(px0, py0, pw, ph, '나누기 — 세로선으로 반씩', 'δ = min(왼쪽 최소, 오른쪽 최소)'));
    const bx = px0 + 30, by = py0 + 70, bw = pw - 60, bh = ph - 112;
    g.push(box(bx, by, bw, bh, { stroke: CG, sw: 1 }));
    const mid = bx + bw / 2;
    const delta = 46;
    g.push(box(mid - delta, by, delta * 2, bh, { fill: C3, op: 0.12, stroke: C3, sw: 1.2, dash: '5 4' }));
    g.push(ln([[mid, by - 8], [mid, by + bh + 8]], { stroke: CI, sw: 1.6 }));
    g.push(txt(mid, by - 14, '가운데 선', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const left = [[42, 30], [86, 96], [30, 142], [118, 56], [70, 168], [140, 128]];
    const right = [[262, 40], [318, 104], [382, 48], [296, 162], [356, 146], [236, 128]];
    left.forEach(p => g.push(pdot(bx + p[0], by + p[1], C1, 4.5)));
    right.forEach(p => g.push(pdot(bx + p[0], by + p[1], C1, 4.5)));
    // 각 반쪽의 가장 가까운 쌍
    g.push(ln([[bx + 86, by + 96], [bx + 140, by + 128]], { stroke: C1, sw: 2 }));
    g.push(txt(bx + 118, by + 108, 'δ~L', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[bx + 318, by + 104], [bx + 356, by + 146]], { stroke: C1, sw: 2 }));
    g.push(txt(bx + 344, by + 124, 'δ~R', { cls: 'ink2', size: 'sm' }));
    // 띠 안의 후보 쌍
    g.push(pdot(mid - 24, by + 72, C2, 5));
    g.push(pdot(mid + 20, by + 94, C2, 5));
    g.push(ln([[mid - 24, by + 72], [mid + 20, by + 94]], { stroke: C2, sw: 2.2 }));
    g.push(txt(mid + 26, by + 60, '띠를 가로지르는 쌍', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(mid - delta - 4, by + bh + 18, '폭 2δ', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[mid - delta, by + bh + 6], [mid + delta, by + bh + 6]], { stroke: C3, sw: 1.4 }));
    g.push(txt(px0 + pw / 2, py0 + ph - 12, '띠 밖의 쌍은 이미 δ 보다 멀다 — 다시 볼 필요가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 띠 안의 상자
    const qx = 496, qy = py0, qw = W - 22 - 496, qh = ph;
    g.push(panel(qx, qy, qw, qh, '띠 안에서 몇 개를 보나', 'y 순으로 정렬해 두면'));
    const gx = qx + 76, gy = qy + 66, cw = 60, chh = 60;
    for (let r = 0; r < 2; r += 1) {
        for (let c = 0; c < 2; c += 1) {
            g.push(box(gx + c * cw, gy + r * chh, cw, chh, { stroke: CG, sw: 1 }));
        }
    }
    g.push(box(gx, gy, cw * 2, chh * 2, { stroke: C3, sw: 1.8, dash: '5 4' }));
    g.push(ln([[gx + cw, gy - 10], [gx + cw, gy + chh * 2 + 10]], { stroke: CI, sw: 1.4 }));
    [[26, 22], [92, 40], [30, 88], [96, 104]].forEach(p => g.push(pdot(gx + p[0], gy + p[1], C1, 4.5)));
    g.push(pdot(gx + cw, gy + chh * 2, C2, 5.5));
    g.push(txt(gx + cw + 12, gy + chh * 2 + 16, '지금 보는 점', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(gx - 8, gy + chh, '2δ', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(gx + cw, gy - 18, '가로 2δ', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + qw / 2, qy + qh - 62, '한 칸(δ/2 × δ/2)에는 점이 둘 이상 들어갈 수 없다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + qw / 2, qy + qh - 42, '들어가면 그 둘의 거리가 δ 보다 작아 모순이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + qw / 2, qy + qh - 16, '그래서 뒤로 여섯 점만 보면 충분하다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(W / 2, H - 14, 'T(n) = 2T(n/2) + Θ(n) — 마스터 정리 둘째 경우이므로 Θ(n log n) 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-closest-pair',
        svg: svg({
            width: W, height: H,
            title: '최근접 점 쌍의 가운데 띠',
            desc: '가운데 선에서 폭 2δ 안의 점만 다시 보고, 그 안에서도 y 순으로 여섯 점만 견주면 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-7. 분할정복이 손해인 세 자리 ---- */
add((() => {
    const W = 820, H = 344;
    const g = [];
    g.push(txt(W / 2, 26, '분할정복이 손해인 세 자리', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 254, ph = 254, py = 44;
    // 1) 작은 입력
    g.push(panel(18, py, pw, ph, '1. 입력이 작을 때', '재귀 자체의 비용이 이긴다'));
    const f = frame({ xRange: [0, 60], yRange: [0, 460], box: { x: 60, y: py + 62, w: 176, h: 140 } });
    g.push(axes2(f, { xRange: [0, 60], yRange: [0, 460], xTicks: [], yTicks: [], xLabel: 'n', yLabel: '비용' }));
    g.push(fcurve(f, v => 0.12 * v * v, { from: 0, to: 60, stroke: C3, sw: 2.2 }));
    g.push(fcurve(f, v => 0.5 * v * lg2(Math.max(v, 2)) + 2 * v, { from: 0, to: 60, stroke: C1, sw: 2.2 }));
    g.push(txt(f.X(56), f.Y(0.12 * 56 * 56) - 8, 'n²', { cls: 'ink bold', size: 'sm', anchor: 'end' }));
    g.push(txt(f.X(1), f.Y(438), 'n log n', { anchor: 'start', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(1), f.Y(390), '+ 재귀 비용', { anchor: 'start', cls: 'ink2', size: 'sm' }));
    const cross = 41;
    g.push(ln([[f.X(cross), f.Y(0)], [f.X(cross), f.Y(460)]], { stroke: CK, sw: 1.2, dash: '4 4' }));
    g.push(txt(f.X(cross) - 4, py + 74, '교차점', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(18 + pw / 2, py + 228, '작은 구간에서는 단순한 쪽으로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(18 + pw / 2, py + 244, '갈아탄다 — 이것이 바닥 문턱이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 2) 부분문제가 겹칠 때
    const qx = 18 + pw + 26;
    g.push(panel(qx, py, pw, ph, '2. 부분문제가 겹칠 때', '같은 것을 몇 번이나 다시 푼다'));
    const nodes = {
        r: [qx + 127, py + 74, 'F(5)'],
        a: [qx + 78, py + 122, 'F(4)'],
        b: [qx + 176, py + 122, 'F(3)'],
        c: [qx + 50, py + 170, 'F(3)'],
        d: [qx + 106, py + 170, 'F(2)'],
        e: [qx + 148, py + 170, 'F(2)'],
        h: [qx + 204, py + 170, 'F(1)'],
    };
    [['r', 'a'], ['r', 'b'], ['a', 'c'], ['a', 'd'], ['b', 'e'], ['b', 'h']].forEach(([u, v]) => {
        g.push(tedge([nodes[u][0], nodes[u][1]], [nodes[v][0], nodes[v][1]], { r1: 17, r2r: 17 }));
    });
    for (const k of Object.keys(nodes)) {
        const [x, y, lb] = nodes[k];
        const dup = lb === 'F(3)' || lb === 'F(2)';
        g.push(tnode(x, y, lb, { r: 17, col: dup ? C2 : null }));
    }
    g.push(txt(qx + pw / 2, py + 210, '주황색은 이미 푼 것을 또 푸는 자리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + pw / 2, py + 232, '나눈 조각이 서로 겹치면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + pw / 2, py + 248, '분할정복이 아니라 12장의 DP 다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 3) 합치는 값이 비쌀 때
    const rx = qx + pw + 26;
    g.push(panel(rx, py, pw, ph, '3. 합치는 값이 비쌀 때', 'f(n) 이 잎 비용을 눌러 버린다'));
    const barX = rx + 34, barY = py + 64;
    const levels = [{ w: 180, t: '뿌리 — f(n) = n²' }, { w: 92, t: '다음 층 합 — n²/2' }, { w: 46, t: 'n²/4' }, { w: 24, t: 'n²/8' }];
    levels.forEach((L, i) => {
        g.push(box(barX, barY + i * 34, L.w, 22, { fill: C2, op: 0.3, stroke: C2, sw: 1.3, rx: 2 }));
        g.push(txt(barX + L.w + 8, barY + i * 34 + 16, L.t, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(rx + pw / 2, py + 208, '맨 위 한 번이 전체를 정한다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + pw / 2, py + 228, '나누는 수고를 해도 답은 Θ(n²) 그대로다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + pw / 2, py + 248, '(마스터 정리 셋째 경우)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 12, '나눌 수 있다고 나누는 것이 이득은 아니다. 세 자리에서 각각 다른 답이 필요하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-divide-loses',
        svg: svg({
            width: W, height: H,
            title: '분할정복이 손해인 세 경우',
            desc: '입력이 작을 때, 부분문제가 겹칠 때, 합치는 값이 비쌀 때는 나누는 것이 이득이 아니다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 11장 — 탐욕 알고리즘
 * ================================================================== */

/* ---- 11-1. 거스름돈 — 탐욕의 반례 (12장으로 이어지는 실) ---- */
add((() => {
    const W = 812, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '동전이 1 · 4 · 6 원뿐인 나라에서 8 원을 거슬러 준다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 372, ph = 216, py = 48;
    const scale = 264 / 8; // 1원당 화소

    const draw = (x0, title, sub, parts, col) => {
        const out = [panel(x0, py, pw, ph, title, sub)];
        const bx = x0 + (pw - 264) / 2;
        let acc = 0;
        parts.forEach((v) => {
            out.push(box(bx + acc * scale, py + 62, v * scale, 40, { fill: col, op: 0.26, stroke: col, sw: 1.7, rx: 3 }));
            out.push(txt(bx + (acc + v / 2) * scale, py + 87, `${v}`, { anchor: 'middle', cls: 'ink bold' }));
            acc += v;
        });
        for (let t = 0; t <= 8; t += 1) {
            out.push(ln([[bx + t * scale, py + 102], [bx + t * scale, py + 108]], { sw: 1 }));
            if (t % 2 === 0) out.push(txt(bx + t * scale, py + 122, `${t}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        out.push(txt(x0 + pw / 2, py + 152, `동전 ${parts.length}개`, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };

    g.push(draw(22, '탐욕 — 매번 가장 큰 동전', '6 을 집고 나면 남은 2 를 1 로만 채운다', [6, 1, 1], C2));
    g.push(txt(22 + pw / 2, py + 180, '6 → 남은 2 → 1 → 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(22 + pw / 2, py + 200, '한 번의 좋은 선택이 뒤를 망쳤다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(draw(W - 22 - pw, '최적 — 6 을 아예 쓰지 않는다', '큰 동전을 포기하면 딱 맞는다', [4, 4], C3));
    g.push(txt(W - 22 - pw / 2, py + 180, '4 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 22 - pw / 2, py + 200, '지금 손해를 보는 선택이 전체로는 이긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(22 + pw + 10, py + ph / 2, W - 22 - pw - 10, py + ph / 2, { cls: 'ark', width: 1.6 }));

    g.push(ln([[40, 288], [W - 40, 288]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 312, '탐욕은 되돌아보지 않는다. 그래서 ‘지금 가장 좋은 것’ 이 뒤에 무엇을 막는지 알 수 없다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 336, '12장에서 같은 문제를 동적 계획법으로 다시 풀어 2 라는 답을 얻는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-coin-greedy-fail',
        svg: svg({
            width: W, height: H,
            title: '거스름돈에서 탐욕이 지는 예',
            desc: '동전이 1, 4, 6 일 때 8 원을 탐욕으로 거슬러 주면 세 개가 필요하지만 4 원 두 개면 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-2. 활동 선택 ---- */
add((() => {
    const W = 828, H = 478;
    const g = [];
    g.push(txt(W / 2, 26, '겹치지 않게 가장 많이 고르려면 — 끝나는 시각이 이른 것부터', { anchor: 'middle', cls: 'ink bold' }));

    const X = t => 110 + t * 32;
    const acts = [
        ['A', 1, 4], ['B', 3, 5], ['C', 0, 6], ['D', 5, 7],
        ['E', 3, 9], ['F', 6, 10], ['G', 8, 11], ['H', 12, 16],
    ];
    // 끝나는 시각 순으로 훑으며 겹치지 않으면 고른다.
    let last = 0;
    const picked = new Set();
    for (const [nm, s, e] of acts) { if (s >= last) { picked.add(nm); last = e; } }

    // 눈금
    const topY = 62, rowH = 27;
    for (let t = 0; t <= 16; t += 2) {
        g.push(ln([[X(t), topY - 6], [X(t), topY + acts.length * rowH + 4]], { stroke: CG, sw: 1 }));
        g.push(txt(X(t), topY - 12, `${t}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(X(16) + 26, topY - 12, '시각', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    acts.forEach(([nm, s, e], i) => {
        const y = topY + i * rowH;
        const on = picked.has(nm);
        g.push(txt(94, y + 17, nm, { anchor: 'end', cls: on ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(box(X(s), y + 4, X(e) - X(s), 19, {
            fill: on ? C3 : CG, op: on ? 0.34 : 0.5, stroke: on ? C3 : CK, sw: on ? 1.9 : 1, rx: 3,
        }));
        g.push(txt((X(s) + X(e)) / 2, y + 18, `${s} – ${e}`, { anchor: 'middle', cls: on ? 'ink' : 'ink2', size: 'sm' }));
        if (on) g.push(txt(X(16) + 20, y + 18, '고른다', { anchor: 'start', cls: 'ink bold', size: 'sm' }));
        else g.push(txt(X(16) + 20, y + 18, '겹친다', { anchor: 'start', cls: 'ink2', size: 'sm' }));
    });
    const by = topY + acts.length * rowH + 10;
    g.push(txt(110, by + 14, '끝나는 시각 순으로 늘어놓았다. 앞서 고른 것이 끝난 뒤에 시작하면 고르고, 아니면 버린다 — 네 개.', { cls: 'ink2', size: 'sm' }));

    // 반례: 짧은 것부터 고르면
    const cy = by + 40;
    g.push(ln([[40, cy - 6], [W - 40, cy - 6]], { stroke: CG, sw: 1 }));
    g.push(txt(110, cy + 18, '‘가장 짧은 것부터’ 는 왜 안 되는가', { cls: 'ink bold', size: 'sm' }));
    const bad = [['P', 0, 9, false], ['Q', 8, 11, true], ['R', 10, 16, false]];
    bad.forEach(([nm, s, e, on], i) => {
        const y = cy + 28 + i * 24;
        g.push(txt(94, y + 15, nm, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(box(X(s), y + 3, X(e) - X(s), 17, {
            fill: on ? C2 : CG, op: on ? 0.3 : 0.45, stroke: on ? C2 : CK, sw: on ? 1.8 : 1, rx: 3,
        }));
        g.push(txt((X(s) + X(e)) / 2, y + 16, `${s} – ${e}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(X(16) + 20, y + 16, on ? '가장 짧다 · 고른다' : '이제 못 고른다', { cls: on ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(110, cy + 116, '가장 짧은 Q 하나를 고르면 P 와 R 을 둘 다 잃는다. 하나 대 둘이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(110, cy + 134, '‘무엇이 좋아 보이는가’ 를 잘못 고르면 탐욕은 곧바로 무너진다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-d-activity-select',
        svg: svg({
            width: W, height: H,
            title: '활동 선택 문제',
            desc: '끝나는 시각이 이른 것부터 고르면 최대 개수가 나오지만 짧은 것부터 고르면 손해를 본다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-3. 바꿔치기 논법 ---- */
add((() => {
    const W = 800, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '탐욕이 옳음을 보이는 표준 수법 — 최적해를 탐욕해 쪽으로 한 칸씩 민다', { anchor: 'middle', cls: 'ink bold' }));

    const bw = 96, bh = 40, gap = 14, x0 = 150;
    const rows = [
        { y: 68, name: '탐욕해 G', items: ['g~1', 'g~2', 'g~3', 'g~4'], col: C3, diff: 2, note: '탐욕이 고른 것' },
        { y: 146, name: '어떤 최적해 O', items: ['g~1', 'g~2', 'o~3', 'o~4'], col: C1, diff: 2, note: '여기서 처음 갈린다' },
        { y: 246, name: 'O 를 고친 것', items: ['g~1', 'g~2', 'g~3', 'o~4'], col: C2, diff: 2, note: '갈리는 자리가 한 칸 뒤로' },
    ];
    rows.forEach((r) => {
        g.push(txt(x0 - 16, r.y + 25, r.name, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        r.items.forEach((s, i) => {
            const same = i < r.diff;
            const hit = i === r.diff;
            g.push(tbox(x0 + i * (bw + gap), r.y, bw, bh, s, {
                stroke: hit ? r.col : same ? CG : CK,
                fill: hit ? r.col : 'none', sw: hit ? 2 : 1.2, size: undefined,
            }));
        });
        g.push(txt(x0 + 4 * (bw + gap) + 8, r.y + 25, r.note, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(x0 + 2 * (bw + gap) + bw / 2, 60, '↓ 처음 갈리는 자리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(x0 + 2 * (bw + gap) + bw / 2, 190, x0 + 2 * (bw + gap) + bw / 2, 240, { cls: 's2', width: 2 }));
    g.push(txt(x0 + 2 * (bw + gap) + bw / 2 + 12, 218, 'o~3 를 g~3 로 바꿔치기', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[40, 302], [W - 40, 302]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 322, '바꾼 것도 여전히 답이고 값이 나빠지지 않는다. 그러면 이 되풀이 끝에 O 가 G 가 된다 — G 도 최적이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 340, '보여야 할 것은 딱 하나, ‘바꿔도 나빠지지 않는다’ 이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-d-exchange-argument',
        svg: svg({
            width: W, height: H,
            title: '바꿔치기 논법',
            desc: '최적해에서 탐욕과 처음 갈리는 선택을 탐욕의 선택으로 바꿔도 나빠지지 않음을 보인다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-4. 배낭 — 쪼갤 수 있을 때와 없을 때 ---- */
add((() => {
    const W = 812, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '쪼갤 수 있으면 탐욕이 맞고, 쪼갤 수 없으면 틀린다 (배낭 50 kg)', { anchor: 'middle', cls: 'ink bold' }));

    const items = [['A', 10, 60], ['B', 20, 100], ['C', 30, 120]];
    // 왼쪽 — 가치 밀도
    g.push(panel(22, 46, 250, 176, '가치 밀도로 줄을 세운다', '가치 ÷ 무게'));
    items.forEach((it, i) => {
        const y = 96 + i * 40;
        const d = it[2] / it[1];
        g.push(txt(52, y + 16, `${it[0]}  ${it[1]}kg · ${it[2]}원`, { cls: 'ink2', size: 'sm' }));
        g.push(box(174, y + 4, d * 12, 16, { fill: C1, op: 0.3, stroke: C1, sw: 1.3, rx: 2 }));
        g.push(txt(174 + d * 12 + 6, y + 17, `${d}`, { cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(147, 210, 'A > B > C 순서다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 쪼갤 수 있을 때
    const sc = 9; // 1kg 당 화소
    g.push(panel(288, 46, W - 22 - 288, 176, '쪼갤 수 있으면 (분할 가능 배낭)', '밀도 높은 것부터 붓는다'));
    const kx = 330, ky = 110;
    g.push(box(kx, ky, 50 * sc, 44, { stroke: CI, sw: 1.6, rx: 4 }));
    const fill = [['A 10kg', 10, C1, '60'], ['B 20kg', 20, C1, '100'], ['C 의 2/3', 20, C2, '80']];
    let acc = 0;
    fill.forEach((p) => {
        g.push(box(kx + acc * sc, ky, p[1] * sc, 44, { fill: p[2], op: 0.28, stroke: p[2], sw: 1.5, rx: 3 }));
        g.push(txt(kx + (acc + p[1] / 2) * sc, ky + 20, p[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(kx + (acc + p[1] / 2) * sc, ky + 36, `${p[3]}원`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        acc += p[1];
    });
    g.push(txt(kx + 25 * sc, ky + 76, '60 + 100 + 80 = 240 원 — 이것이 최적이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(kx + 25 * sc, ky + 96, '마지막 물건을 잘라 배낭을 남김없이 채웠다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 아래 — 0-1
    g.push(ln([[40, 242], [W - 40, 242]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 266, '쪼갤 수 없으면 (0-1 배낭) — 같은 순서로 담아 본다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const sc2 = 6.6;
    const two = [
        { x: 56, title: '탐욕 · 밀도 순', parts: [['A', 10, C2, 60], ['B', 20, C2, 100], ['빈자리 20kg', 20, null, 0]], sum: '160 원' },
        { x: 426, title: '최적', parts: [['B', 20, C3, 100], ['C', 30, C3, 120]], sum: '220 원' },
    ];
    two.forEach((t) => {
        g.push(txt(t.x + 25 * sc2, 296, t.title, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(box(t.x, 306, 50 * sc2, 34, { stroke: CI, sw: 1.4, rx: 4 }));
        let a2 = 0;
        t.parts.forEach((p) => {
            if (p[2]) {
                g.push(box(t.x + a2 * sc2, 306, p[1] * sc2, 34, { fill: p[2], op: 0.28, stroke: p[2], sw: 1.5, rx: 3 }));
                g.push(txt(t.x + (a2 + p[1] / 2) * sc2, 328, `${p[0]} · ${p[3]}원`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            } else {
                g.push(txt(t.x + (a2 + p[1] / 2) * sc2, 328, p[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
            a2 += p[1];
        });
        g.push(txt(t.x + 25 * sc2, 362, t.sum, { anchor: 'middle', cls: 'ink bold' }));
    });
    g.push(txt(W / 2, 390, 'C 를 통째로 넣으려면 A 를 포기해야 하는데, 탐욕은 이미 A 를 넣은 뒤라 되돌릴 수 없다 — 12장의 자리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-knapsack-fractional',
        svg: svg({
            width: W, height: H,
            title: '분할 가능 배낭과 0-1 배낭',
            desc: '가치 밀도가 높은 것부터 담는 탐욕은 쪼갤 수 있을 때만 최적이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-5. 허프만 — 합치는 순서 ---- */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '허프만 — 매번 가장 가벼운 둘을 합친다 (빈도 a45 b13 c12 d16 e9 f5)', { anchor: 'middle', cls: 'ink bold' }));

    const steps = [
        { pool: ['f 5', 'e 9', 'c 12', 'b 13', 'd 16', 'a 45'], pick: [0, 1], made: '14' },
        { pool: ['c 12', 'b 13', '14', 'd 16', 'a 45'], pick: [0, 1], made: '25' },
        { pool: ['14', 'd 16', '25', 'a 45'], pick: [0, 1], made: '30' },
        { pool: ['25', '30', 'a 45'], pick: [0, 1], made: '55' },
        { pool: ['a 45', '55'], pick: [0, 1], made: '100' },
    ];
    const bx = 112, bw = 56, bh = 30, step = bw + 6;
    const ax = bx + 6 * step + 8;   // 줄이 가장 길 때(6개)의 오른쪽 끝
    g.push(txt(bx, 50, '가벼운 것부터 늘어놓은 줄 (최소 힙)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(ax + 62, 50, '합친 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    steps.forEach((s, i) => {
        const y = 62 + i * 52;
        g.push(txt(102, y + 20, `${i + 1}단계`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        s.pool.forEach((v, j) => {
            const on = s.pick.includes(j);
            g.push(tbox(bx + j * step, y, bw, bh, v, { stroke: on ? C2 : CG, fill: on ? C2 : 'none', sw: on ? 1.9 : 1.1 }));
        });
        g.push(arw(ax, y + 15, ax + 30, y + 15, { cls: 's2', width: 1.5 }));
        g.push(tbox(ax + 36, y, bw, bh, s.made, { stroke: C3, fill: C3, sw: 1.7 }));
        g.push(txt(ax + 36 + bw + 10, y + 20, i === 4 ? '뿌리 — 끝' : '다시 줄로', { cls: i === 4 ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(118, 330, '‘가장 가벼운 둘’ 을 꺼내는 일이 되풀이되므로 6장의 최소 힙을 쓴다. 원소가 n 개면 합치기가 n−1 번, 전체 O(n log n) 이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-huffman-build',
        svg: svg({
            width: W, height: H,
            title: '허프만 부호의 합치는 순서',
            desc: '가장 가벼운 두 개를 꺼내 합치고 다시 넣기를 되풀이하면 하나가 남는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-6. 허프만 트리와 부호 ---- */
add((() => {
    const W = 812, H = 404;
    const g = [];
    g.push(txt(W / 2, 26, '자주 나오는 글자가 뿌리에 가깝다 — 그것이 부호를 짧게 만든다', { anchor: 'middle', cls: 'ink bold' }));

    const N = {
        root: [286, 68, '100'],
        a: [176, 132, 'a 45'],
        n55: [396, 132, '55'],
        n25: [326, 200, '25'],
        n30: [466, 200, '30'],
        c: [286, 268, 'c 12'],
        b: [360, 268, 'b 13'],
        n14: [430, 268, '14'],
        d: [508, 268, 'd 16'],
        f: [396, 334, 'f 5'],
        e: [464, 334, 'e 9'],
    };
    const E = [
        ['root', 'a', '0'], ['root', 'n55', '1'],
        ['n55', 'n25', '0'], ['n55', 'n30', '1'],
        ['n25', 'c', '0'], ['n25', 'b', '1'],
        ['n30', 'n14', '0'], ['n30', 'd', '1'],
        ['n14', 'f', '0'], ['n14', 'e', '1'],
    ];
    E.forEach(([u, v, lb]) => {
        g.push(tedge([N[u][0], N[u][1]], [N[v][0], N[v][1]], { r1: 21, r2r: 21, label: lb, lcls: 'ink2' }));
    });
    for (const k of Object.keys(N)) {
        const leaf = ['a', 'b', 'c', 'd', 'e', 'f'].includes(k);
        g.push(tnode(N[k][0], N[k][1], N[k][2], { r: 21, col: leaf ? C1 : null }));
    }
    g.push(txt(176, 178, '가장 자주 나오는 a 는', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(176, 194, '한 걸음 — 부호가 1비트', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const tx = 592;
    g.push(txt(tx, 66, '글자   빈도   부호   비트', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[tx, 74], [tx + 190, 74]], { stroke: CG, sw: 1 }));
    const table = [['a', 45, '0', 1], ['b', 13, '101', 3], ['c', 12, '100', 3], ['d', 16, '111', 3], ['e', 9, '1101', 4], ['f', 5, '1100', 4]];
    table.forEach((r, i) => {
        const y = 96 + i * 24;
        g.push(txt(tx + 6, y, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(tx + 58, y, `${r[1]}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(tx + 76, y, r[2], { cls: 'ink', size: 'sm' }));
        g.push(txt(tx + 178, y, `${r[1] * r[3]}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[tx, 250], [tx + 190, 250]], { stroke: CG, sw: 1 }));
    g.push(txt(tx + 6, 270, '허프만', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx + 178, 270, '224 비트', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx + 6, 292, '길이 3 고정 부호', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 178, 292, '300 비트', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 6, 320, '25% 를 줄였다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(W / 2, H - 32, '어떤 부호도 다른 부호의 앞부분이 아니다(글자가 모두 잎이므로). 그래서 띄어쓰기 없이 이어 붙여도 되읽을 수 있다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '왼쪽 0, 오른쪽 1 로 읽으면 뿌리에서 잎까지의 길이가 곧 부호의 비트 수다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-huffman-tree',
        svg: svg({
            width: W, height: H,
            title: '허프만 트리와 부호표',
            desc: '빈도가 높은 글자가 얕은 곳에 놓여 짧은 부호를 받는다. 어떤 부호도 다른 부호의 접두가 아니다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-7. 탐색과 활용 ---- */
add((() => {
    const W = 800, H = 380;
    const g = [];
    g.push(txt(W / 2, 26, '늘 가장 좋아 보이는 것만 당기면 ‘좋아 보이는’ 이 틀렸을 때 영영 모른다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 368, ph = 250, py = 46;
    const panels = [
        {
            x: 22, title: 'ε = 0 — 순수 탐욕', sub: '100번 당긴 뒤',
            rows: [['1번', 0.3, 0.50, 97], ['2번', 0.7, 0.00, 2], ['3번', 0.5, 0.00, 1]],
            note: ['처음 두 번 운 좋게 딴 1번에 갇혔다.', '참값이 가장 높은 2번은 두 번 당기고 말았다.'],
            col: C2,
        },
        {
            x: W - 22 - pw, title: 'ε = 0.1 — 열 번에 한 번은 아무거나', sub: '100번 당긴 뒤',
            rows: [['1번', 0.3, 0.33, 12], ['2번', 0.7, 0.69, 76], ['3번', 0.5, 0.50, 12]],
            note: ['가끔 딴 데를 찔러 본 덕에 표본이 참값에 붙었다.', '그 뒤로는 2번을 주로 당긴다.'],
            col: C3,
        },
    ];
    panels.forEach((p) => {
        g.push(panel(p.x, py, pw, ph, p.title, p.sub));
        const ax = p.x + 74, aw = 200;
        g.push(txt(ax - 8, py + 58, '표본 평균', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(ax + aw + 30, py + 58, '당긴 횟수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        p.rows.forEach((r, i) => {
            const y = py + 72 + i * 44;
            g.push(txt(ax - 8, y + 18, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
            g.push(box(ax, y + 4, aw, 20, { stroke: CG, sw: 1, rx: 2 }));
            g.push(box(ax, y + 4, aw * r[2], 20, { fill: p.col, op: 0.3, stroke: p.col, sw: 1.4, rx: 2 }));
            g.push(ln([[ax + aw * r[1], y], [ax + aw * r[1], y + 28]], { stroke: CI, sw: 1.8 }));
            g.push(txt(ax + aw * r[1], y + 40, `참 ${r[1]}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(txt(ax + aw + 30, y + 18, `${r[3]}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        });
        p.note.forEach((s, i) => g.push(txt(p.x + pw / 2, py + ph - 34 + i * 18, s, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    });
    g.push(txt(W / 2, H - 42, '굵은 세로선이 그 레버의 참 기댓값이고 색칠한 막대가 지금까지의 표본 평균이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 22, '탐색은 당장은 손해다. 그 손해를 얼마나 감수할지 정하는 값이 ε 이고, 대개 시간이 갈수록 줄인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-explore-exploit',
        svg: svg({
            width: W, height: H,
            title: '탐색과 활용의 맞바꿈',
            desc: '순수 탐욕은 이른 우연에 갇히고 가끔 임의로 찔러 보는 쪽은 참값을 찾아낸다',
            body: g.join(''),
        }),
    };
})());

/* ---- 11-8. 비서 문제 ---- */
add((() => {
    const W = 780, H = 358;
    const g = [];
    g.push(txt(W / 2, 26, '얼마를 그냥 흘려보내야 하는가 — 후보 100명일 때', { anchor: 'middle', cls: 'ink bold' }));

    const nS = 100;
    const Psec = (r) => {
        if (r <= 1) return 1 / nS;
        let s = 0;
        for (let k = r; k <= nS - 1; k += 1) s += 1 / k;
        return (r / nS) * s;
    };
    const f = frame({ xRange: [0, 1], yRange: [0, 0.45], box: { x: 92, y: 58, w: 520, h: 214 } });
    g.push(axes2(f, {
        xRange: [0, 1], yRange: [0, 0.45],
        xTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [0.1, 0.2, 0.3, 0.4],
        xLabel: '흘려보내는 비율 r/n', yLabel: '최고를 뽑을 확률',
        fmt: v => (Math.round(v * 100) / 100).toString(),
    }));
    const pts = [];
    for (let r = 2; r <= 99; r += 1) pts.push([f.X(r / nS), f.Y(Psec(r))]);
    g.push(ln(pts, { stroke: C1, sw: 2.4 }));

    const invE = 1 / Math.E;
    let best = 2;
    for (let r = 2; r <= 99; r += 1) if (Psec(r) > Psec(best)) best = r;
    g.push(ln([[f.X(invE), f.Y(0)], [f.X(invE), f.Y(0.42)]], { stroke: C2, sw: 1.5, dash: '5 4' }));
    g.push(ln([[f.X(0), f.Y(invE)], [f.X(1), f.Y(invE)]], { stroke: C2, sw: 1.2, dash: '5 4' }));
    g.push(pdot(f.X(best / nS), f.Y(Psec(best)), C2, 5));
    g.push(txt(f.X(invE) + 10, f.Y(0.43), '1/e ≈ 0.368', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(1) + 6, f.Y(invE) + 4, '≈ 0.368', { anchor: 'start', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(best / nS) + 12, f.Y(Psec(best)) - 10, `봉우리 r = ${best}`, { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[f.X(0), f.Y(0.01)], [f.X(1), f.Y(0.01)]], { stroke: CK, sw: 1.2, dash: '3 3' }));
    g.push(txt(f.X(0.78), f.Y(0.01) - 9, '아무나 한 명 찍으면 1/100', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(92, H - 54, '왼쪽 끝: 너무 일찍 멈추면 기준이 없어 아무나 뽑게 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(92, H - 34, '오른쪽 끝: 너무 오래 보면 최고가 이미 지나가 버려 아무도 못 뽑는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(92, H - 12, '봉우리가 1/e 자리에 있고 그때의 성공 확률도 1/e 다. 사람 수가 늘어도 이 값은 변하지 않는다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-d-secretary',
        svg: svg({
            width: W, height: H,
            title: '비서 문제의 성공 확률',
            desc: '앞의 r 명을 흘려보내고 그보다 나은 첫 사람을 뽑을 때 성공 확률은 r/n 이 1/e 근처에서 가장 크다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 12장 — 동적 계획법
 * ================================================================== */

const FIBCOL = { 4: C3, 3: C2, 2: C1 };

/* ---- 12-1. 피보나치 재귀 트리 — 같은 것을 몇 번이나 ---- */
add((() => {
    const W = 852, H = 412;
    const g = [];
    g.push(txt(W / 2, 26, 'F(6) 을 재귀로 구하면 25번을 부른다 — 그런데 서로 다른 것은 일곱 개뿐이다', { anchor: 'middle', cls: 'ink bold' }));

    // 잎에 차례로 자리를 주고 부모는 자식의 가운데에 둔다.
    const nodes = [];
    const edges = [];
    let leaf = 0;
    const build = (k, depth) => {
        const id = nodes.length;
        nodes.push({ k, depth, x: 0 });
        if (k <= 1) { nodes[id].x = leaf; leaf += 1; return id; }
        const a = build(k - 1, depth + 1);
        const b = build(k - 2, depth + 1);
        edges.push([id, a], [id, b]);
        nodes[id].x = (nodes[a].x + nodes[b].x) / 2;
        return id;
    };
    build(6, 0);
    const gapX = 62, x0 = 56, y0 = 66, dy = 56;
    const PX = nd => x0 + nd.x * gapX;
    const PY = nd => y0 + nd.depth * dy;
    edges.forEach(([u, v]) => {
        g.push(tedge([PX(nodes[u]), PY(nodes[u])], [PX(nodes[v]), PY(nodes[v])], { r1: 16, r2r: 16, sw: 1.2 }));
    });
    nodes.forEach((nd) => {
        g.push(tnode(PX(nd), PY(nd), `F${nd.k}`, { r: 16, col: FIBCOL[nd.k] ?? null }));
    });

    const counts = [[6, 1], [5, 1], [4, 2], [3, 3], [2, 5], [1, 8], [0, 5]];
    g.push(txt(W / 2, H - 52, counts.map(c => `F${c[0]} ${c[1]}번`).join('   ·   ') + '   =   모두 25번', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 30, '색이 같은 원은 똑같은 계산이다. 아래로 갈수록 같은 것이 늘어나고, n 이 커지면 호출 수가 F(n) 만큼, 곧 지수로 늘어난다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 10, '이것이 ‘겹치는 부분문제(overlapping subproblems)’ 다. 답을 한 번만 구해 적어 두면 25번이 7번이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-fib-tree',
        svg: svg({
            width: W, height: H,
            title: '피보나치 재귀 트리의 겹치는 부분문제',
            desc: 'F(6) 의 재귀 호출 25번 가운데 서로 다른 부분문제는 F(0) 부터 F(6) 까지 일곱 개뿐이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-2. 트리가 그래프로, 그래프가 표로 접힌다 ---- */
add((() => {
    const W = 852, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '재귀 트리 → 겹친 것을 하나로 → 한 줄짜리 표', { anchor: 'middle', cls: 'ink bold' }));

    const py = 48, ph = 216;
    // 1) 트리
    g.push(panel(20, py, 258, ph, '재귀 트리', '같은 계산이 되풀이된다'));
    const TN = {
        a: [149, py + 66, 'F5'], b: [104, py + 112, 'F4'], c: [196, py + 112, 'F3'],
        d: [76, py + 158, 'F3'], e: [132, py + 158, 'F2'], f: [172, py + 158, 'F2'], h: [222, py + 158, 'F1'],
    };
    [['a', 'b'], ['a', 'c'], ['b', 'd'], ['b', 'e'], ['c', 'f'], ['c', 'h']].forEach(([u, v]) => {
        g.push(tedge([TN[u][0], TN[u][1]], [TN[v][0], TN[v][1]], { r1: 15, r2r: 15, sw: 1.2 }));
    });
    for (const k of Object.keys(TN)) {
        const lb = TN[k][2];
        g.push(tnode(TN[k][0], TN[k][1], lb, { r: 15, col: lb === 'F3' ? C2 : lb === 'F2' ? C1 : null }));
    }
    g.push(txt(149, py + 196, '가지가 두 배씩 벌어진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(286, py + ph / 2, 316, py + ph / 2, { cls: 'ark', width: 1.8 }));
    g.push(txt(301, py + ph / 2 - 12, '합친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 2) DAG
    g.push(panel(324, py, 258, ph, '겹친 것을 하나로 = DAG', '노드 7개 · 간선 10개'));
    const dx0 = 348, dgap = 35, dy2 = py + 148;
    for (let k = 0; k <= 6; k += 1) {
        g.push(tnode(dx0 + k * dgap, dy2, `${k}`, { r: 14, col: k === 3 ? C2 : k === 2 ? C1 : null }));
    }
    for (let k = 2; k <= 6; k += 1) {
        g.push(arcTo(dx0 + k * dgap - 8, dy2 - 14, dx0 + (k - 1) * dgap + 6, dy2 - 14, 16, { stroke: CK, sw: 1.2 }));
        g.push(arcTo(dx0 + k * dgap - 6, dy2 - 12, dx0 + (k - 2) * dgap + 8, dy2 - 12, 40, { stroke: CG, sw: 1.2 }));
    }
    g.push(txt(453, py + 62, '화살표는 ‘이것이 필요하다’ 를 가리킨다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(453, dy2 + 34, '순환이 없다 — 8장의 DAG 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(453, dy2 + 54, '위상 순서대로 채우면 된다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(arw(590, py + ph / 2, 620, py + ph / 2, { cls: 'ark', width: 1.8 }));
    g.push(txt(605, py + ph / 2 - 12, '펼친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 3) 표
    g.push(panel(628, py, W - 20 - 628, ph, '표 하나', '왼쪽에서 오른쪽으로'));
    const vals = [0, 1, 1, 2, 3, 5, 8];
    g.push(cells(652, py + 122, 24, 34, vals, { idx: [0, 1, 2, 3, 4, 5, 6], hl: { 2: C1, 3: C2 }, small: true }));
    g.push(txt(652 + 7 * 12, py + 100, 'F[k] = F[k−1] + F[k−2]', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(652 + 7 * 12, py + 186, '계산 7번 · 공간 7칸', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(W / 2, H - 42, '트리에서 표로 접히는 조건은 두 가지다. 부분문제가 겹칠 것, 그리고 큰 답이 작은 답들로만 정해질 것.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 20, '접고 나면 계산 횟수는 ‘서로 다른 부분문제의 수 × 한 칸을 채우는 값’ 이 된다 — 25번이 7번이 되었다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-fib-fold',
        svg: svg({
            width: W, height: H,
            title: '재귀 트리가 표로 접히는 과정',
            desc: '겹치는 부분문제를 하나로 합치면 DAG 가 되고 그 위상 순서대로 펼치면 표가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-3. 하향식과 상향식 ---- */
add((() => {
    const W = 812, H = 366;
    const g = [];
    g.push(txt(W / 2, 26, '같은 표를 채우는 두 방향', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 380, ph = 254, py = 46;
    // 하향식
    g.push(panel(20, py, pw, ph, '하향식 — 메모이제이션', '재귀 그대로 두고 답을 적어 둔다'));
    const MN = {
        a: [200, py + 56, 'F6', 0], b: [140, py + 96, 'F5', 0], c: [278, py + 96, 'F4', 1],
        d: [96, py + 136, 'F4', 0], e: [216, py + 136, 'F3', 1],
        f: [70, py + 176, 'F3', 0], h: [148, py + 176, 'F2', 1],
        i: [52, py + 212, 'F2', 0], j: [110, py + 212, 'F1', 0],
    };
    [['a', 'b'], ['a', 'c'], ['b', 'd'], ['b', 'e'], ['d', 'f'], ['d', 'h'], ['f', 'i'], ['f', 'j']].forEach(([u, v]) => {
        g.push(tedge([MN[u][0], MN[u][1]], [MN[v][0], MN[v][1]], { r1: 15, r2r: 15, sw: 1.2, dash: MN[v][3] ? '4 3' : undefined }));
    });
    for (const k of Object.keys(MN)) {
        const hit = MN[k][3] === 1;
        g.push(tnode(MN[k][0], MN[k][1], MN[k][2], { r: 15, col: hit ? C3 : null, dash: hit ? '4 3' : undefined }));
    }
    g.push(txt(308, py + 146, '점선 원은 표에서', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(308, py + 162, '꺼내 쓴 것 — 더 안 판다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20 + pw / 2, py + ph - 12, '필요한 칸만 채운다. 재귀 깊이만큼 스택을 쓴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 상향식
    const qx = W - 20 - pw;
    g.push(panel(qx, py, pw, ph, '상향식 — 표 채우기', '작은 것부터 순서대로'));
    const cx0 = qx + 44, cwid = 42, cy0 = py + 96;
    const vals = [0, 1, 1, 2, 3, 5, 8];
    g.push(cells(cx0, cy0, cwid, 40, vals, { idx: [0, 1, 2, 3, 4, 5, 6], hl: { 6: C2 } }));
    for (let k = 0; k < 7; k += 1) {
        g.push(txt(cx0 + k * cwid + cwid / 2, cy0 - 10, `${k + 1}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(cx0 - 8, cy0 - 10, '채우는 차례', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arcTo(cx0 + 5.5 * cwid, cy0 + 42, cx0 + 6.5 * cwid - 3, cy0 + 42, -22, { stroke: C2, sw: 1.6, marker: 'ar2' }));
    g.push(arcTo(cx0 + 4.5 * cwid, cy0 + 42, cx0 + 6.5 * cwid - 13, cy0 + 42, -44, { stroke: C2, sw: 1.6, marker: 'ar2' }));
    g.push(txt(qx + pw / 2, cy0 + 122, '앞 두 칸만 보면 되므로 두 변수로도 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + pw / 2, py + ph - 12, '표를 전부 채운다. 재귀가 없어 스택을 안 쓴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 32, '둘의 답은 같다. 부분문제 중 실제로 쓰이는 것이 적으면 하향식이, 어차피 다 쓰면 상향식이 유리하다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '상향식은 채우는 순서를 사람이 정해야 한다 — 그 순서가 곧 부분문제 DAG 의 위상 순서다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-memo-vs-table',
        svg: svg({
            width: W, height: H,
            title: '메모이제이션과 상향식 표 채우기',
            desc: '하향식은 필요한 칸만 재귀로 채우고 상향식은 작은 것부터 표를 전부 채운다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-4. 거스름돈 DP 표가 채워지는 순서 (11장 반례의 해결) ---- */
add((() => {
    const W = 828, H = 470;
    const g = [];
    g.push(txt(W / 2, 26, '11장에서 탐욕이 진 문제 — 표를 왼쪽부터 채우면 답이 나온다 (동전 1 · 4 · 6)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 50, 'dp[m] = m 원을 만드는 데 드는 가장 적은 동전 수 = min( dp[m−1], dp[m−4], dp[m−6] ) + 1', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    const full = [0, 1, 2, 3, 1, 2, 1, 2, 2];
    const idx = [0, 1, 2, 3, 4, 5, 6, 7, 8];
    const x0 = 168, cw = 58, ch = 34;
    const stills = [
        { y: 76, upto: 3, left: '3 원까지', right: '1 원밖에 없다' },
        { y: 134, upto: 6, left: '6 원까지', right: '4 와 6 이 들어온다' },
        { y: 278, upto: 8, left: '8 원', right: '완성' },
    ];
    stills.forEach((s) => {
        const row = full.map((v, i) => (i <= s.upto ? v : ''));
        const hl = {};
        if (s.upto === 8) { hl[8] = C3; hl[7] = C2; hl[4] = C2; hl[2] = C2; }
        if (s.upto === 6) { hl[4] = C1; hl[6] = C1; }
        g.push(cells(x0, s.y, cw, ch, row, { hl, idx: s.upto === 8 ? idx : null }));
        g.push(txt(x0 - 12, s.y + 22, s.left, { anchor: 'end', cls: s.upto === 8 ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(x0 + 9 * cw + 12, s.y + 22, s.right, { cls: 'ink2', size: 'sm' }));
    });

    // 마지막 칸을 정하는 세 후보
    const CX = k => x0 + (k + 0.5) * cw;
    g.push(txt(W / 2, 196, '8 원 칸은 ‘마지막에 놓은 동전’ 세 가지를 견주어 정한다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    [[7, 24, '1 원', CK], [4, 42, '4 원', C2], [2, 60, '6 원', CK]].forEach(([k, lift, lb, col]) => {
        g.push(arcTo(CX(k), 278, CX(8) - 2, 278, lift, { stroke: col, sw: 1.6, marker: col === C2 ? 'ar2' : 'ark' }));
        g.push(txt((CX(k) + CX(8)) / 2, 278 - lift - 6, lb, { anchor: 'middle', cls: col === C2 ? 'ink bold' : 'ink2', size: 'sm' }));
    });

    const ey = 360;
    [
        ['마지막이 1 원이면', 'dp[7] + 1 = 2 + 1 = 3', false],
        ['마지막이 4 원이면', 'dp[4] + 1 = 1 + 1 = 2', true],
        ['마지막이 6 원이면', 'dp[2] + 1 = 2 + 1 = 3', false],
    ].forEach((r, i) => {
        const y = ey + i * 22;
        g.push(txt(228, y, r[0], { anchor: 'end', cls: r[2] ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(244, y, r[1], { cls: r[2] ? 'ink bold' : 'ink2', size: 'sm' }));
        if (r[2]) g.push(txt(452, y, '← 가장 작다', { cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 34, '탐욕은 6 원을 먼저 집어 세 개였다. 표는 세 가지를 모두 견주므로 6 원을 안 쓰는 길을 놓치지 않는다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '칸이 금액 수만큼, 칸마다 동전 종류만큼 견주므로 O(금액 × 동전 종류) 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-coin-dp-fill',
        svg: svg({
            width: W, height: H,
            title: '거스름돈 표가 채워지는 순서',
            desc: '0 원부터 차례로 채우고 마지막 동전이 무엇이었는지 세 가지를 견주어 가장 작은 것을 고른다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-5. 0-1 배낭 표 ---- */
add((() => {
    const W = 828, H = 444;
    const g = [];
    g.push(txt(W / 2, 26, '0-1 배낭 — 물건을 하나씩 늘려 가며 줄을 채운다 (용량 5)', { anchor: 'middle', cls: 'ink bold' }));

    const rows = [
        [0, 0, 0, 0, 0, 0],
        [0, 0, 3, 3, 3, 3],
        [0, 0, 3, 4, 4, 7],
        [0, 0, 3, 4, 5, 7],
        [0, 0, 3, 4, 5, 7],
    ];
    const side = ['없음', '1까지', '2까지', '3까지', '4까지'];
    const head = [0, 1, 2, 3, 4, 5];
    const gx = 150, gy = 104, cw = 54, ch = 34;
    const hl = { '2,5': C3, '1,5': CK, '1,2': C2 };
    g.push(txt(gx + 3 * cw, 74, '배낭에 남은 용량 w', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(grid(gx, gy, cw, ch, rows, { hl, head, side, headLabel: '물건' }));

    // 세 칸에 이름표를 달고 설명은 표 아래에 둔다(표 안에 글을 넣으면 숫자와 겹친다).
    const CX = c => gx + (c + 0.5) * cw;
    const CY = r => gy + (r + 0.5) * ch;
    const tag = (r, c, s, col) => txt(gx + c * cw + 7, gy + r * ch + 13, s, { cls: 'ink2', size: 'sm' })
        + (col ? '' : '');
    g.push(tag(1, 5, '가'));
    g.push(tag(1, 2, '나'));
    g.push(tag(2, 5, '답'));
    g.push(arw(CX(5), CY(1) + 13, CX(5), CY(2) - 13, { cls: 'ark', width: 1.5 }));

    // 오른쪽 물건표
    const tx = 530;
    g.push(txt(tx, 74, '물건    무게   가치', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[tx, 82], [tx + 200, 82]], { stroke: CG, sw: 1 }));
    [['1번', 2, 3, true], ['2번', 3, 4, true], ['3번', 4, 5, false], ['4번', 5, 6, false]].forEach((r, i) => {
        const y = 104 + i * 23;
        g.push(txt(tx + 6, y, r[0], { cls: r[3] ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(tx + 96, y, `${r[1]} kg`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(tx + 168, y, `${r[2]} 원`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[tx, 204], [tx + 200, 204]], { stroke: CG, sw: 1 }));
    g.push(txt(tx + 6, 226, '답 dp[4][5] = 7', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(tx + 6, 248, '되짚으면 1번 + 2번', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 6, 268, '무게 2 + 3 = 5', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 6, 286, '11장의 탐욕은 160 원이었다', { cls: 'ink2', size: 'sm' }));

    const ey = 316;
    g.push(txt(gx - 34, ey, '2번 물건(무게 3, 가치 4)을 쓸 수 있게 되었을 때 용량 5 칸을 정하는 법', { cls: 'ink bold', size: 'sm' }));
    [
        ['가', '안 넣는다 → 위 칸 그대로 dp[1][5] = 3'],
        ['나', '넣는다 → 무게 3 을 빼고 dp[1][2] + 4 = 3 + 4 = 7'],
        ['답', '큰 쪽인 7 을 적는다'],
    ].forEach((r, i) => {
        const y = ey + 22 + i * 20;
        g.push(txt(gx - 20, y, r[0], { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(gx - 8, y, r[1], { cls: i === 2 ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 32, '칸이 n × W 개이고 칸마다 상수 일이므로 O(nW) 다. 다만 W 는 입력에 적힌 수이지 입력의 길이가 아니다 —', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '자릿수로 재면 지수다. 이런 것을 유사 다항 시간이라 하고 13장에서 다시 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-knapsack-table',
        svg: svg({
            width: W, height: H,
            title: '0-1 배낭의 표',
            desc: '물건을 하나씩 늘려 가며 용량별 최대 가치를 채우고 넣을지 말지 두 가지를 견준다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-6. 최장 공통 부분수열 표 ---- */
add((() => {
    const W = 812, H = 412;
    const g = [];
    g.push(txt(W / 2, 26, '두 문자열을 견주는 표 — 글자가 같으면 대각선, 다르면 위와 왼쪽 중 큰 쪽', { anchor: 'middle', cls: 'ink bold' }));

    const rows = [
        [0, 0, 0, 0, 0],
        [0, 0, 1, 1, 1],
        [0, 1, 1, 1, 1],
        [0, 1, 1, 2, 2],
        [0, 1, 2, 2, 2],
        [0, 1, 2, 2, 3],
    ];
    const side = ['·', 'A', 'G', 'C', 'A', 'T'];
    const head = ['·', 'G', 'A', 'C', 'T'];
    const gx = 150, gy = 96, cw = 52, ch = 36;
    const path = [[5, 4], [4, 3], [3, 3], [2, 2], [1, 2], [0, 1]];
    const hl = {};
    for (const [r, c] of path) hl[`${r},${c}`] = r === 5 && c === 4 ? C3 : C2;
    g.push(txt(gx - 30, 66, '세로 X = A G C A T   /   가로 Y = G A C T', { cls: 'ink2', size: 'sm' }));
    g.push(grid(gx, gy, cw, ch, rows, { hl, head, side }));
    const CX = c => gx + (c + 0.5) * cw;
    const CY = r => gy + (r + 0.5) * ch;
    // 화살표를 칸 가운데에서 그리면 숫자를 덮는다. 양 끝을 안으로 당겨 칸 사이만 잇는다.
    for (let i = 0; i < path.length - 1; i += 1) {
        const [r, c] = path[i];
        const [rn, cn] = path[i + 1];
        const dx = CX(cn) - CX(c), dy = CY(rn) - CY(r);
        const L = Math.hypot(dx, dy) || 1;
        const pad = 17;
        g.push(arw(CX(c) + (dx / L) * pad, CY(r) + (dy / L) * pad,
            CX(cn) - (dx / L) * pad, CY(rn) - (dy / L) * pad, { cls: 's2', width: 1.6 }));
    }
    const by2 = gy + 6 * ch;
    g.push(txt(gx - 30, by2 + 26, '오른쪽 아래 칸(3)에서 거꾸로 따라간다. 대각선으로 움직인 자리의 글자를 모으면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(gx - 30, by2 + 46, '가장 긴 공통 부분수열 A C T 가 나온다', { cls: 'ink bold', size: 'sm' }));

    // 규칙 세 가지
    const rx = 566, ry = 96;
    g.push(txt(rx, ry - 8, '한 칸을 채우는 규칙', { cls: 'ink bold', size: 'sm' }));
    const mini = (x, y, lb, diag) => {
        const out = [];
        for (let r = 0; r < 2; r += 1) for (let c = 0; c < 2; c += 1) {
            out.push(box(x + c * 26, y + r * 24, 26, 24, { stroke: CG, sw: 1, rx: 2 }));
        }
        out.push(box(x + 26, y + 24, 26, 24, { stroke: C2, sw: 1.8, rx: 2, fill: C2, op: 0.2 }));
        if (diag === 'd') out.push(arw(x + 13, y + 12, x + 33, y + 32, { cls: 's2', width: 1.5 }));
        if (diag === 'u') out.push(arw(x + 39, y + 12, x + 39, y + 30, { cls: 's2', width: 1.5 }));
        if (diag === 'l') out.push(arw(x + 13, y + 36, x + 31, y + 36, { cls: 's2', width: 1.5 }));
        out.push(txt(x + 62, y + 30, lb, { cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(mini(rx, ry + 4, '글자가 같다 → 대각선 + 1', 'd'));
    g.push(mini(rx, ry + 62, '다르다 → 위 칸을 그대로', 'u'));
    g.push(mini(rx, ry + 120, '다르다 → 왼쪽 칸을 그대로', 'l'));

    g.push(txt(W / 2, H - 34, '칸이 (길이+1) × (길이+1) 개이고 칸마다 상수 일이므로 O(mn) 이다. 두 줄만 들고 있으면 길이는 O(min(m,n)) 공간으로도 나온다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '다만 부분수열 자체를 되짚으려면 표 전체가 있어야 한다 — 공간을 줄이면 잃는 것이 이것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-lcs-table',
        svg: svg({
            width: W, height: H,
            title: '최장 공통 부분수열의 표와 되짚기',
            desc: '글자가 같으면 대각선에서 하나 늘리고 다르면 위와 왼쪽 중 큰 값을 가져온다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-7. 편집 거리 — 세 이웃이 세 연산이다 ---- */
add((() => {
    const W = 800, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '편집 거리 — 왼쪽 · 위 · 대각선 세 이웃이 그대로 삽입 · 삭제 · 교체다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 세 이웃
    g.push(panel(20, 46, 380, 246, '한 칸을 정하는 세 후보', 'd[i][j] 를 채운다'));
    const bx = 128, by = 116, cw = 84, ch = 60;
    const label = [['d[i−1][j−1]', 0, 0], ['d[i−1][j]', 1, 0], ['d[i][j−1]', 0, 1], ['d[i][j]', 1, 1]];
    label.forEach(([lb, c, r]) => {
        const on = lb === 'd[i][j]';
        g.push(box(bx + c * cw, by + r * ch, cw, ch, { stroke: on ? C2 : CG, sw: on ? 2 : 1, rx: 3, fill: on ? C2 : 'none', op: 0.18 }));
        g.push(txt(bx + c * cw + cw / 2, by + r * ch + ch / 2 + 4, lb, { anchor: 'middle', cls: on ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(arw(bx + cw - 16, by + ch - 12, bx + cw + 22, by + ch + 22, { cls: 's2', width: 1.7 }));
    g.push(arw(bx + cw + cw / 2, by + ch - 14, bx + cw + cw / 2, by + ch + 12, { cls: 's2', width: 1.7 }));
    g.push(arw(bx + cw - 16, by + ch + ch / 2, bx + cw + 12, by + ch + ch / 2, { cls: 's2', width: 1.7 }));
    g.push(txt(38, 250, '대각선 — 두 글자가 같으면 +0, 다르면 교체 +1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(38, 268, '위 — 윗글자를 삭제 +1        왼쪽 — 글자를 삽입 +1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(38, 286, '셋 중 가장 작은 값을 적는다', { cls: 'ink bold', size: 'sm' }));

    // 오른쪽: kitten → sitting
    g.push(panel(418, 46, W - 20 - 418, 246, 'kitten 을 sitting 으로', '세 번이면 된다'));
    const sx = 452, sy = 116, gw = 36;
    const A = ['k', 'i', 't', 't', 'e', 'n', ''];
    const B = ['s', 'i', 't', 't', 'i', 'n', 'g'];
    const opn = ['교체', '', '', '', '교체', '', '삽입'];
    A.forEach((c, i) => {
        const on = opn[i] !== '';
        g.push(box(sx + i * gw, sy, gw - 4, 30, { stroke: on ? C2 : CG, sw: on ? 1.8 : 1, rx: 3 }));
        g.push(txt(sx + i * gw + (gw - 4) / 2, sy + 21, c === '' ? '·' : c, { anchor: 'middle', cls: c === '' ? 'ink2' : 'ink', size: 'sm' }));
        g.push(box(sx + i * gw, sy + 62, gw - 4, 30, { stroke: on ? C2 : CG, sw: on ? 1.8 : 1, rx: 3 }));
        g.push(txt(sx + i * gw + (gw - 4) / 2, sy + 83, B[i], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        if (on) {
            g.push(arw(sx + i * gw + (gw - 4) / 2, sy + 32, sx + i * gw + (gw - 4) / 2, sy + 58, { cls: 's2', width: 1.5 }));
            g.push(txt(sx + i * gw + (gw - 4) / 2, sy + 112, opn[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(txt(sx + 3.5 * gw, sy + 142, '편집 거리 = 3', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(sx + 3.5 * gw, sy + 166, '표의 오른쪽 아래 칸이 이 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 32, '맞춤법 검사, 유전자 서열 비교, 파일 차이 보기(diff)가 모두 이 표다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '연산의 값을 바꾸면(예컨대 삽입만 비싸게) 표 채우는 규칙만 고치면 된다 — 이것이 DP 의 유연함이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-edit-ops',
        svg: svg({
            width: W, height: H,
            title: '편집 거리의 세 연산',
            desc: '대각선 이웃은 교체, 위 이웃은 삭제, 왼쪽 이웃은 삽입에 해당한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 12-8. 공간 줄이기 ---- */
add((() => {
    const W = 812, H = 340;
    const g = [];
    g.push(txt(W / 2, 26, '직전 줄만 보면 되는 표는 줄 두 개로, 때로는 한 개로 줄인다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 250, ph = 226, py = 46;
    const drawRows = (x, y, rowsN, colsN, { cwid = 30, chh = 24, live = [], arrowBack = false } = {}) => {
        const out = [];
        for (let r = 0; r < rowsN; r += 1) {
            for (let c = 0; c < colsN; c += 1) {
                const on = live.includes(r);
                out.push(box(x + c * cwid, y + r * chh, cwid, chh, {
                    stroke: on ? C1 : CG, sw: on ? 1.6 : 1, rx: 2, fill: on ? C1 : 'none', op: 0.16,
                }));
            }
        }
        if (arrowBack) {
            out.push(arw(x + colsN * cwid - 4, y + chh + 34, x + 6, y + chh + 34, { cls: 's2', width: 1.7 }));
        }
        return out.join('');
    };

    g.push(panel(20, py, pw, ph, '표 전부', 'n × W 칸'));
    g.push(drawRows(66, py + 62, 5, 6, { live: [0, 1, 2, 3, 4] }));
    g.push(txt(20 + pw / 2, py + 200, '되짚기까지 하려면 이것이 필요하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(20 + pw + 21, py, pw, ph, '줄 두 개', '직전 줄과 지금 줄'));
    g.push(drawRows(66 + pw + 21, py + 86, 2, 6, { live: [0, 1] }));
    g.push(txt(66 + pw + 21 + 90, py + 76, '직전 줄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(66 + pw + 21 + 90, py + 152, '지금 줄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20 + pw + 21 + pw / 2, py + 186, '한 줄을 다 채우면 자리를 맞바꾼다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20 + pw + 21 + pw / 2, py + 206, '공간이 O(W) 로 준다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(panel(W - 20 - pw, py, pw, ph, '줄 하나', '뒤에서부터 채우면'));
    g.push(drawRows(W - 20 - pw + 46, py + 96, 1, 6, { live: [0], arrowBack: true }));
    g.push(txt(W - 20 - pw + 46 + 90, py + 154, '오른쪽에서 왼쪽으로', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W - 20 - pw / 2 - 20, py + 186, '앞에서부터 채우면 같은 물건을', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W - 20 - pw / 2 - 20, py + 206, '두 번 넣게 된다 — 흔한 버그다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 32, '공간을 줄이면 잃는 것이 있다. 표를 지우고 나면 ‘무엇을 골랐는가’ 를 되짚을 수 없다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '값만 필요하면 줄이고, 답 자체가 필요하면 표를 남기거나 되짚기용 표시를 따로 저장한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-dp-space',
        svg: svg({
            width: W, height: H,
            title: 'DP 표의 공간 줄이기',
            desc: '직전 줄만 참조하면 두 줄로, 방향을 뒤집으면 한 줄로 줄일 수 있다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 13장 — 계산 복잡도 (P와 NP)
 * ================================================================== */

/** 타원. 집합 그림에 쓴다. */
function ell(cx, cy, rx, ry, { stroke = CK, sw = 1.5, fill = 'none', op = 0.12, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${fill === 'none' ? 0 : op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/* ---- 13-1. 푸는 것과 확인하는 것 ---- */
add((() => {
    const W = 828, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '문제: {3, 34, 4, 12, 5, 2} 에서 몇 개를 골라 합을 9 로 만들 수 있는가', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 386, ph = 254, py = 48;
    // 왼쪽 — 푼다
    g.push(panel(20, py, pw, ph, '푼다 — 답을 찾아내야 한다', '고르는 방법이 2⁶ = 64 가지'));
    const set = [3, 34, 4, 12, 5, 2];
    g.push(cells(72, py + 52, 46, 32, set, {}));
    const lx = 213;
    g.push(txt(lx, py + 108, '넣는다 · 뺀다 를 여섯 번 정한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let d = 0; d < 3; d += 1) {
        const cnt = 2 ** d;
        const span = 230;
        for (let k = 0; k < cnt; k += 1) {
            const x = lx - span / 2 + (span / cnt) * (k + 0.5);
            const y = py + 128 + d * 26;
            g.push(pdot(x, y, d === 2 ? CG : CK, 3.5));
            if (d < 2) {
                const nsp = span / (cnt * 2);
                g.push(ln([[x, y], [x - nsp / 2, y + 26]], { stroke: CG, sw: 1 }));
                g.push(ln([[x, y], [x + nsp / 2, y + 26]], { stroke: CG, sw: 1 }));
            }
        }
    }
    g.push(txt(lx, py + 206, '…  가지가 두 배씩 벌어져 잎이 64 개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx, py + 232, '원소가 100 개면 2¹⁰⁰ 가지다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽 — 확인한다
    const qx = W - 20 - pw;
    g.push(panel(qx, py, pw, ph, '확인한다 — 답을 받아 검사만 한다', '더하기 한 번이면 끝'));
    g.push(txt(qx + pw / 2, py + 58, '누가 답이라며 이것을 내민다 (증명서)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(cells(qx + 146, py + 68, 46, 32, [4, 5], { hl: { 0: C3, 1: C3 } }));
    g.push(arw(qx + pw / 2, py + 104, qx + pw / 2, py + 128, { cls: 'ark', width: 1.6 }));
    g.push(tbox(qx + 92, py + 130, 200, 36, '4 + 5 = 9 인가', { stroke: C1, fill: C1, sw: 1.6, size: undefined }));
    g.push(arw(qx + pw / 2, py + 168, qx + pw / 2, py + 190, { cls: 'ark', width: 1.6 }));
    g.push(tbox(qx + 122, py + 192, 140, 34, '그렇다', { stroke: C3, fill: C3, sw: 1.7, size: undefined }));
    g.push(txt(qx + pw / 2, py + 244, '원소가 100 개여도 덧셈 100 번 안쪽이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[40, 322], [W - 40, 322]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 344, 'P 는 왼쪽이 다항 시간인 문제들, NP 는 오른쪽이 다항 시간인 문제들이다.', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 364, '‘답이 없다’ 를 확인하는 데는 내밀 증명서가 없다는 점을 눈여겨보라 — 그래서 NP 는 예 · 아니오가 대칭이 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-solve-verify',
        svg: svg({
            width: W, height: H,
            title: '푸는 것과 답을 보고 확인하는 것',
            desc: '부분집합 합 문제는 찾는 데 2의 n 제곱 가지를 훑어야 하지만 답을 받으면 덧셈 한 번으로 확인된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-2. 최적화 · 판정 · 탐색 ---- */
add((() => {
    const W = 800, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '왜 하필 예 · 아니오 문제만 다루는가 — 잃는 것이 없기 때문이다', { anchor: 'middle', cls: 'ink bold' }));

    const bw = 216, bh = 66, y = 58;
    const forms = [
        { x: 40, t: ['최적화 문제', '가장 큰 클리크의', '크기는 얼마인가'], col: C1 },
        { x: 292, t: ['판정 문제', '크기 k 인 클리크가', '있는가 (예 / 아니오)'], col: C2 },
        { x: 544, t: ['탐색 문제', '그 클리크를', '실제로 내놓아라'], col: C3 },
    ];
    forms.forEach((f) => { g.push(tbox(f.x, y, bw, bh, f.t, { stroke: f.col, fill: f.col, sw: 1.7 })); });
    g.push(arw(40 + bw + 8, y + bh / 2, 292 - 8, y + bh / 2, { cls: 'ark', width: 1.6 }));
    g.push(arw(292 + bw + 8, y + bh / 2, 544 - 8, y + bh / 2, { cls: 'ark', width: 1.6 }));

    const cols = [
        { x: 148, lines: ['판정 문제를 다항 번 부르면', '최적값이 나온다', '', 'k 를 1 부터 n 까지 이분 탐색하면', 'log₂ n 번이면 충분하다'], bold: 1 },
        { x: 400, lines: ['이론은 여기서만 한다', '', '답이 예 · 아니오 하나뿐이라', '기계와 문제를 깔끔하게', '견줄 수 있기 때문이다'], bold: 0 },
        { x: 652, lines: ['판정을 풀 수 있으면 정점을 하나씩', '지워 보며 답을 복원한다', '', '지워도 답이 그대로면', '그 정점은 필요 없었던 것이다'], bold: 1 },
    ];
    cols.forEach((c) => {
        c.lines.forEach((s, i) => {
            if (!s) return;
            g.push(txt(c.x, y + bh + 34 + i * 19, s, { anchor: 'middle', cls: i === c.bold ? 'ink bold' : 'ink2', size: 'sm' }));
        });
    });
    g.push(txt(W / 2, H - 34, '셋은 다항 시간 안에서 서로 오갈 수 있다. 그래서 ‘판정 문제가 어렵다’ 와 ‘최적화 문제가 어렵다’ 가 같은 말이 된다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '앞으로 나오는 SAT · 클리크 · 정점 덮개 · 해밀턴 경로 · TSP 는 모두 가운데 형태로 적은 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-decision-optimization',
        svg: svg({
            width: W, height: H,
            title: '최적화 · 판정 · 탐색 문제의 관계',
            desc: '판정 문제를 다항 번 부르면 최적값과 답 자체를 모두 얻을 수 있어 판정만 다루어도 잃는 것이 없다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-3. 두 세계 ---- */
add((() => {
    const W = 812, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '아무도 모르는 것은 왼쪽인가 오른쪽인가 하나다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46, ph = 296, pw = 386;
    g.push(panel(20, py, pw, ph, 'P ≠ NP 라면 (대부분이 그렇게 믿는다)', '풀기는 어려운데 확인은 쉬운 문제가 있다'));
    g.push(ell(160, 202, 106, 74, { stroke: C1, fill: C1, op: 0.1, sw: 1.8 }));
    g.push(ell(300, 202, 100, 82, { stroke: C2, dash: '6 5', sw: 1.8 }));
    g.push(ell(124, 208, 62, 44, { stroke: C3, fill: C3, op: 0.18, sw: 1.8 }));
    g.push(txt(124, 204, 'P', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(124, 226, '정렬 · 최단경로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(104, 148, 'NP', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(352, 152, 'NP-난해', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(228, 206, 'NP-완전', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(228, 302, 'SAT · 클리크 · TSP', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 302, '정지 문제', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20 + pw / 2, py + ph - 12, '겹치는 자리가 NP-완전 — NP 안에서 가장 어려운 것들', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const qx = W - 20 - pw;
    g.push(panel(qx, py, pw, ph, 'P = NP 라면', '확인이 쉬우면 푸는 것도 쉽다'));
    g.push(ell(qx + 142, 202, 104, 76, { stroke: C1, fill: C1, op: 0.12, sw: 1.8 }));
    g.push(ell(qx + 288, 202, 94, 82, { stroke: C2, dash: '6 5', sw: 1.8 }));
    g.push(txt(qx + 142, 196, 'P = NP', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(qx + 142, 218, '= NP-완전', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(qx + 310, 152, 'NP-난해', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(qx + 310, 302, '정지 문제는 그대로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + 142, 302, '세 겹이 하나로 무너진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(qx + pw / 2, py + ph - 12, '공개키 암호가 무너지고 최적화가 전부 쉬워진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 30, 'P ⊆ NP 는 쉽게 보인다. 답을 다항 시간에 찾을 수 있으면 증명서를 무시하고 그냥 풀어 확인하면 되기 때문이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 10, '반대쪽, 곧 NP ⊆ P 인지가 열려 있는 문제다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-classes',
        svg: svg({
            width: W, height: H,
            title: 'P · NP · NP-완전 · NP-난해의 관계',
            desc: 'NP 와 NP-난해가 겹치는 자리가 NP-완전이고 P = NP 라면 그 셋이 하나로 무너진다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-4. 환원의 방향 ---- */
add((() => {
    const W = 844, H = 388;
    const g = [];
    g.push(txt(W / 2, 26, '환원 A ≤ₚ B — 화살표는 A 에서 B 로 가는데 어려움은 거꾸로 전해진다', { anchor: 'middle', cls: 'ink bold' }));

    const y = 96, bh = 52;
    g.push(box(96, y - 30, 664, 122, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(txt(106, y - 12, 'A 를 푸는 기계 (통째로 다항 시간)', { cls: 'ink2', size: 'sm' }));

    g.push(tbox(20, y + 4, 100, bh, ['A 의 입력', 'x'], { stroke: C1, fill: C1, sw: 1.7 }));
    g.push(arw(124, y + 30, 162, y + 30, { cls: 'ark', width: 1.7 }));
    g.push(tbox(166, y + 4, 152, bh, ['변환 f', '다항 시간에 바꾼다'], { stroke: C2, fill: C2, sw: 1.7 }));
    g.push(arw(322, y + 30, 358, y + 30, { cls: 'ark', width: 1.7 }));
    g.push(tbox(362, y + 4, 116, bh, ['B 의 입력', 'f(x)'], { stroke: C1, fill: C1, sw: 1.7 }));
    g.push(arw(482, y + 30, 518, y + 30, { cls: 'ark', width: 1.7 }));
    g.push(tbox(522, y + 4, 152, bh, ['B 를 푸는 기계', '(있다고 치자)'], { stroke: C3, fill: C3, sw: 1.7 }));
    g.push(arw(678, y + 30, 714, y + 30, { cls: 'ark', width: 1.7 }));
    g.push(tbox(718, y + 4, 106, bh, ['예 / 아니오', '그대로 A 의 답'], { stroke: CI, sw: 1.5 }));
    g.push(txt(W / 2, y + 112, '변환이 반드시 지켜야 할 것:  x 가 예  ⟺  f(x) 가 예', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[40, 236], [W - 40, 236]], { stroke: CG, sw: 1 }));
    const ry = 260;
    g.push(txt(60, ry, '같은 그림을 읽는 두 가지 방법', { cls: 'ink bold', size: 'sm' }));
    g.push(tbox(60, ry + 12, 340, 46, ['B 가 쉬우면 A 도 쉽다', '(B 의 기계 앞에 변환기를 붙이면 된다)'], { stroke: C3, fill: C3, sw: 1.5 }));
    g.push(tbox(444, ry + 12, 340, 46, ['A 가 어려우면 B 도 어렵다', '(위 문장의 대우다)'], { stroke: C2, fill: C2, sw: 1.5 }));
    g.push(txt(W / 2, ry + 84, '새 문제 B 가 어렵다고 주장하려면 ‘이미 어렵다고 알려진 A’ 를 B 로 환원해야 한다.', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, ry + 106, '방향을 뒤집어 B 를 A 로 환원하면 아무것도 증명하지 못한다 — 가장 흔한 실수가 이것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-reduction-direction',
        svg: svg({
            width: W, height: H,
            title: '다항 시간 환원의 방향',
            desc: 'A 를 B 로 환원하면 B 의 해결기로 A 를 풀 수 있고 따라서 B 는 적어도 A 만큼 어렵다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-5. 3SAT 에서 클리크로 ---- */
add((() => {
    const W = 828, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '3SAT 을 클리크 문제로 바꾼다 — 절마다 세 점을 찍고 모순이 아닌 것끼리 잇는다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 52, '(x₁ ∨ ¬x₂ ∨ ¬x₃) ∧ (¬x₁ ∨ x₂ ∨ x₃) ∧ (x₁ ∨ x₂ ∨ x₃)', { anchor: 'middle', cls: 'ink bold' }));

    const cl = [
        { cx: 470, cy: 120, lits: ['x₁', '¬x₂', '¬x₃'], name: '절 1', up: true },
        { cx: 300, cy: 288, lits: ['¬x₁', 'x₂', 'x₃'], name: '절 2', up: false },
        { cx: 638, cy: 288, lits: ['x₁', 'x₂', 'x₃'], name: '절 3', up: false },
    ];
    const pos = {};
    cl.forEach((c, ci) => {
        c.lits.forEach((s, i) => {
            const x = c.cx + (i - 1) * 56;
            const y = c.cy + (i === 1 ? (c.up ? -36 : 36) : 0);
            pos[`${ci}-${i}`] = [x, y, s];
        });
        g.push(txt(c.cx, c.up ? c.cy + 52 : c.cy - 44, c.name, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    cl.forEach((c, ci) => {
        const p = [0, 1, 2].map(i => pos[`${ci}-${i}`]);
        g.push(ln([[p[0][0], p[0][1]], [p[1][0], p[1][1]], [p[2][0], p[2][1]], [p[0][0], p[0][1]]], { stroke: CG, sw: 1, dash: '3 3' }));
    });

    const clique = ['0-1', '1-2', '2-0'];
    for (let i = 0; i < clique.length; i += 1) {
        for (let j = i + 1; j < clique.length; j += 1) {
            const a = pos[clique[i]], b = pos[clique[j]];
            g.push(tedge([a[0], a[1]], [b[0], b[1]], { r1: 21, r2r: 21, stroke: C2, sw: 2.4 }));
        }
    }
    const na = pos['0-0'], nb = pos['1-0'];
    g.push(tedge([na[0], na[1]], [nb[0], nb[1]], { r1: 21, r2r: 21, stroke: CG, sw: 1.4, dash: '5 4' }));
    g.push(txt(56, 190, 'x₁ 과 ¬x₁ 은 모순이라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(56, 208, '잇지 않는다 (연한 점선)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(56, 238, '같은 절 안끼리도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(56, 256, '잇지 않는다', { cls: 'ink2', size: 'sm' }));

    Object.keys(pos).forEach((k) => {
        const [x, y, s] = pos[k];
        g.push(tnode(x, y, s, { r: 21, col: clique.includes(k) ? C2 : null }));
    });

    g.push(ln([[40, 342], [W - 40, 342]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 366, '절이 k 개일 때 크기 k 인 클리크가 있다 ⟺ 논리식을 참으로 만드는 값이 있다. 굵게 이어진 셋은 x₁ = 참, x₂ = 거짓, x₃ = 참 을 뜻한다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 386, '점을 찍고 선을 긋는 일이 논리식 길이에 비례하므로 변환 자체는 다항 시간이다 — 환원의 조건을 지켰다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-3sat-clique',
        svg: svg({
            width: W, height: H,
            title: '3SAT 에서 클리크로 가는 환원',
            desc: '절마다 세 점을 찍고 서로 다른 절이면서 모순이 아닌 리터럴끼리 이으면 절 수만 한 클리크가 곧 만족 배정이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-6. 클리크와 정점 덮개 ---- */
add((() => {
    const W = 812, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '클리크와 정점 덮개는 같은 문제를 뒤집어 본 것이다', { anchor: 'middle', cls: 'ink bold' }));

    const layout = ox => ({
        1: [ox + 56, 132], 2: [ox + 154, 132], 3: [ox + 56, 228], 4: [ox + 154, 228],
        5: [ox + 254, 132], 6: [ox + 254, 228],
    });
    const drawG = (ox, edges, mark, title, sub, col) => {
        const P = layout(ox);
        const out = [panel(ox - 22, 46, 346, 254, title, sub)];
        edges.forEach(([u, v]) => out.push(tedge(P[u], P[v], { r1: 19, r2r: 19, sw: 1.4 })));
        Object.keys(P).forEach((k) => {
            out.push(tnode(P[k][0], P[k][1], k, { r: 19, col: mark.includes(Number(k)) ? col : null }));
        });
        return out.join('');
    };
    const gEdges = [[5, 6], [5, 1], [5, 2], [5, 3], [5, 4], [6, 1], [6, 2], [6, 3], [6, 4]];
    const cEdges = [[1, 2], [1, 3], [1, 4], [2, 3], [2, 4], [3, 4]];
    g.push(drawG(44, gEdges, [5, 6], '그래프 G — 간선 9개', '정점 덮개 {5, 6} · 크기 2', C2));
    g.push(drawG(446, cEdges, [1, 2, 3, 4], '여집합 그래프 — 간선 6개', '클리크 {1,2,3,4} · 크기 4', C3));
    g.push(txt(195, 286, '색칠한 두 점이 모든 간선에 하나씩 닿는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(597, 286, '색칠한 네 점이 서로 전부 이어져 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, H - 32, 'G 에 크기 k 인 클리크가 있다  ⟺  여집합 그래프에 크기 n − k 인 정점 덮개가 있다.', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '변환이 간선을 뒤집는 일뿐이라 O(n²) 이다. 그래서 한쪽이 NP-완전이면 다른 쪽도 NP-완전이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-clique-vc',
        svg: svg({
            width: W, height: H,
            title: '클리크와 정점 덮개의 환원',
            desc: '그래프의 간선을 뒤집으면 크기 k 인 클리크가 크기 n 빼기 k 인 정점 덮개로 바뀐다',
            body: g.join(''),
        }),
    };
})());

/* ---- 13-7. 정점 덮개의 2-근사 ---- */
add((() => {
    const W = 828, H = 376;
    const g = [];
    g.push(txt(W / 2, 26, '풀 수 없으면 가까이 간다 — 정점 덮개를 최적의 두 배 안으로 맞추는 법', { anchor: 'middle', cls: 'ink bold' }));

    const P = {
        a: [86, 122], b: [186, 122], c: [286, 122], d: [386, 122],
        e: [286, 232], f: [186, 232], gg: [86, 232],
    };
    const edges = [['a', 'b'], ['b', 'c'], ['c', 'd'], ['d', 'e'], ['e', 'f'], ['f', 'gg'], ['b', 'f'], ['c', 'e']];
    const picked = [['a', 'b'], ['c', 'd'], ['e', 'f']];
    edges.forEach(([u, v]) => {
        const on = picked.some(p => (p[0] === u && p[1] === v) || (p[0] === v && p[1] === u));
        g.push(tedge(P[u], P[v], { r1: 19, r2r: 19, stroke: on ? C2 : CK, sw: on ? 3 : 1.3 }));
    });
    picked.forEach((p, i) => {
        const mx = (P[p[0]][0] + P[p[1]][0]) / 2;
        const my = (P[p[0]][1] + P[p[1]][1]) / 2;
        g.push(txt(mx, my - 10, `${i + 1}회`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    const inCover = ['a', 'b', 'c', 'd', 'e', 'f'];
    Object.keys(P).forEach((k) => {
        g.push(tnode(P[k][0], P[k][1], k === 'gg' ? 'g' : k, { r: 19, col: inCover.includes(k) ? C2 : null }));
    });
    g.push(txt(86, 288, '얻은 덮개 {a, b, c, d, e, f} — 6개', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(86, 308, '최적은 {b, c, e, f} — 4개', { cls: 'ink2', size: 'sm' }));
    g.push(txt(86, 328, '6 ≤ 2 × 4 를 지켰다', { cls: 'ink2', size: 'sm' }));

    const tx = 470;
    g.push(txt(tx, 96, '절차', { cls: 'ink bold', size: 'sm' }));
    ['아직 덮이지 않은 간선을 아무거나 하나 고른다', '그 간선의 양 끝을 둘 다 답에 넣는다', '그 두 점에 닿는 간선을 모두 지운다', '남은 간선이 없을 때까지 되풀이한다'].forEach((s, i) => {
        g.push(txt(tx, 120 + i * 20, `${i + 1}. ${s}`, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(tx, 226, '왜 두 배를 넘지 않는가', { cls: 'ink bold', size: 'sm' }));
    ['굵게 고른 간선들은 끝점을 하나도 공유하지 않는다.', '어떤 덮개든 그 간선 하나마다 적어도 한 점을 써야 하므로', '최적해의 크기는 고른 간선 수 이상이다.', '우리가 쓴 점은 정확히 그 두 배다.'].forEach((s, i) => {
        g.push(txt(tx, 250 + i * 19, s, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 8, '최적을 모르면서도 ‘최적의 두 배 안’ 이라고 말할 수 있다는 것이 근사 알고리즘의 요령이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-d-vc-approx',
        svg: svg({
            width: W, height: H,
            title: '정점 덮개의 2-근사 알고리즘',
            desc: '덮이지 않은 간선의 양 끝을 함께 넣기를 되풀이하면 최적의 두 배를 넘지 않는 덮개가 나온다',
            body: g.join(''),
        }),
    };
})());

export default figures;
