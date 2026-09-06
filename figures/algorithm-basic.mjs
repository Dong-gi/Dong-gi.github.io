/**
 * 알고리즘 3장(재는 법) · 4장(배열과 리스트) · 5장(해시 테이블) · 6장(트리와 힙)의 그림.
 *
 * 이름은 모두 `alg-b-` 로 시작한다(담당 A 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `n~0` 표기를, 나머지는 유니코드(≤ ≥ → × ⌊⌋ ² ⁿ ₂)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 자료구조는 그림 없이 읽히지 않는다. 원문 1,117줄에 그림이 하나뿐이었고,
 * 그래서 AVL 회전은 코드만 남아 무엇이 어디로 가는지 알 수 없었다. 이 파일의 목적이
 * 그 빈자리를 메우는 것이다.
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
 * 화소 좌표 소도구
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

/**
 * 배열 칸 한 줄. items 의 null 은 빈 칸이다.
 * hl 에 든 인덱스는 강조색으로 칠하고, idx 를 주면 칸 아래에 번호를 적는다.
 */
function cells(x, y, w, h, items, { hl = {}, idx = null, idxTop = false, sw = 1.3 } = {}) {
    const g = [];
    items.forEach((v, i) => {
        const cx = x + i * w;
        const col = hl[i];
        g.push(box(cx, y, w, h, { fill: col ?? 'none', op: col ? 0.2 : 1, stroke: col ?? CK, sw: col ? 1.8 : sw, rx: 2 }));
        if (v !== null && v !== undefined && v !== '') {
            g.push(txt(cx + w / 2, y + h / 2 + 5, String(v), { anchor: 'middle', cls: 'ink', size: w < 34 ? 'sm' : undefined }));
        }
        if (idx) {
            const ty = idxTop ? y - 6 : y + h + 14;
            g.push(txt(cx + w / 2, ty, String(idx[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    return g.join('');
}

/** 트리 노드 한 개. 원과 가운데 글자. */
function tnode(x, y, label, { r = 16, col = null, dim = false, dash } = {}) {
    const stroke = col ?? (dim ? CG : CK);
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col ?? 'none'}" fill-opacity="${col ? 0.18 : 0}" stroke="${stroke}" stroke-width="${col ? 2 : 1.4}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`
        + txt(x, y + 5, label, { anchor: 'middle', cls: dim ? 'ink2' : 'ink', size: r < 15 ? 'sm' : undefined });
}

/** 두 노드의 중심을 잇되 원 반지름만큼 잘라 그린다. */
function tedge(p1, p2, { r1 = 16, r2r = 16, stroke = CK, sw = 1.4, dash } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    return ln([[p1[0] + ux * r1, p1[1] + uy * r1], [p2[0] - ux * r2r, p2[1] - uy * r2r]], { stroke, sw, dash });
}

/**
 * 트리 하나를 통째로 그린다.
 * nodes: { 이름: [x, y, 라벨, 옵션] }, edges: [[부모, 자식], ...]
 */
function tree(nodes, edges, { r = 16, stroke = CK } = {}) {
    const g = [];
    for (const [a, b, o] of edges) {
        const A = nodes[a], B = nodes[b];
        if (!A || !B) continue;
        g.push(tedge([A[0], A[1]], [B[0], B[1]], { r1: (A[3] && A[3].r) || r, r2r: (B[3] && B[3].r) || r, stroke: (o && o.stroke) || stroke, sw: (o && o.sw) || 1.4, dash: o && o.dash }));
    }
    for (const k of Object.keys(nodes)) {
        const [x, y, label, o] = nodes[k];
        g.push(tnode(x, y, label, { r, ...(o || {}) }));
    }
    return g.join('');
}

/** 삼각형(서브트리 뭉치). */
function subtri(cx, cy, w, h, label, { stroke = CK, fill = 'none' } = {}) {
    const p = `M${r2(cx)} ${r2(cy)} L${r2(cx - w / 2)} ${r2(cy + h)} L${r2(cx + w / 2)} ${r2(cy + h)} Z`;
    return `<path d="${p}" fill="${fill}" fill-opacity="${fill === 'none' ? 0 : 0.14}" stroke="${stroke}" stroke-width="1.3" stroke-linejoin="round"/>`
        + (label ? txt(cx, cy + h - 8, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 좌표계 위 곡선을 직접 색을 넣어 그린다. lib 의 curve 는 클래스만 받는다. */
function fcurve(f, fn, { from, to, steps = 160, stroke = C1, sw = 2.2, dash } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const xv = from + ((to - from) * i) / steps;
        pts.push([f.X(xv), f.Y(fn(xv))]);
    }
    return ln(pts, { stroke, sw, dash });
}

/** 작은 패널용 축. lib 의 axes 는 글자가 커서 좁은 칸에서 겹친다. */
function axes2(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel, fmt = String } = {}) {
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
        g.push(txt(ay - 8, f.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 16, ax + 5, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay - 4, f.Y(y1) - 18, yLabel, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return g.join('');
}

const lg2 = v => Math.log(v) / Math.LN2;

/* ================================================================== *
 * 3장 — 알고리즘을 어떻게 재는가
 * ================================================================== */

/* ---- 3-1. 증가율의 서열 ---- */
add((() => {
    const W = 760, H = 412;
    const xR = [0, 20], yR = [0, 100];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 66, y: 52, w: 560, h: 280 } });
    const g = [];
    g.push(txt(W / 2, 26, '입력이 커질 때 벌어지는 차이는 상수배가 아니라 모양의 차이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(axes2(f, { xRange: xR, yRange: yR, xTicks: [5, 10, 15, 20], yTicks: [25, 50, 75, 100], xLabel: '입력 크기 n', yLabel: '연산 횟수' }));

    // 각 곡선은 상자를 벗어나기 직전에서 끊는다.
    const series = [
        { fn: v => lg2(Math.max(v, 1)), to: 20, stroke: CK, dash: '6 4', name: 'log n', at: [20.4, 4.32], anchor: 'start' },
        { fn: v => v, to: 20, stroke: C3, name: 'n', at: [20.4, 20], anchor: 'start' },
        { fn: v => v * lg2(Math.max(v, 1.001)), to: 20, stroke: C1, name: 'n log n', at: [20.4, 86.4], anchor: 'start' },
        { fn: v => v * v, to: 10, stroke: C2, name: 'n²', at: [10.3, 97], anchor: 'start' },
        { fn: v => 2 ** v, to: 6.644, stroke: CI, name: '2ⁿ', at: [6.45, 97], anchor: 'end' },
    ];
    for (const s of series) {
        g.push(fcurve(f, s.fn, { from: 0.001, to: s.to, stroke: s.stroke, dash: s.dash, sw: 2.2 }));
    }
    for (const s of series) {
        g.push(txt(f.X(s.at[0]), f.Y(s.at[1]) + 4, s.name, { anchor: s.anchor, cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(70, H - 34, '2ⁿ 은 n = 7 을 넘기기 전에 화면 밖으로 나가고 n² 은 n = 10 에서 나간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(70, H - 14, 'log n 은 n 이 20 이 되도록 4 를 겨우 넘는다. 이 벌어짐이 n 이 백만이 되면 어떻게 되는지가 본문의 표다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-growth-curves',
        svg: svg({
            width: W, height: H,
            title: '흔히 만나는 증가율 다섯 가지',
            desc: 'log n, n, n log n, n 제곱, 2의 n 제곱을 같은 축에 그렸다. 뒤의 둘은 화면을 금방 벗어난다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-2. O, Ω, Θ 의 정의 그림 ---- */
add((() => {
    const W = 770, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '어떤 자리 n₀ 를 지나고 나서부터 눌린다 — 그 앞은 보지 않는다', { anchor: 'middle', cls: 'ink bold' }));
    const xR = [0, 10], yR = [0, 100];
    const spec = [
        {
            title: 'O(g) — 위로 눌린다',
            sub: 'n₀ 뒤에서 f ≤ b·g',
            bands: [{ fn: v => 9 * v, stroke: C2, name: 'b·g(n)' }],
            n0: 3.2,
        },
        {
            title: 'Ω(g) — 아래로 눌린다',
            sub: 'n₀ 뒤에서 f ≥ b·g',
            bands: [{ fn: v => 3.4 * v, stroke: C3, name: 'b·g(n)' }],
            n0: 3.2,
        },
        {
            title: 'Θ(g) — 양쪽으로 눌린다',
            sub: 'n₀ 뒤에서 b₁·g ≤ f ≤ b₂·g',
            bands: [{ fn: v => 9 * v, stroke: C2, name: 'b₂·g(n)' }, { fn: v => 3.4 * v, stroke: C3, name: 'b₁·g(n)' }],
            n0: 3.2,
        },
    ];
    const fEx = v => 5.5 * v + 18 * Math.sin(v * 1.1) + 12;
    spec.forEach((s, i) => {
        const px0 = 14 + i * 250, py0 = 40, pw = 236, ph = 244;
        g.push(panel(px0, py0, pw, ph, s.title, s.sub));
        const f = frame({ xRange: xR, yRange: yR, box: { x: px0 + 34, y: py0 + 56, w: 150, h: 150 } });
        g.push(axes2(f, { xRange: xR, yRange: yR, xTicks: [], yTicks: [], xLabel: 'n' }));
        // n₀ 오른쪽 영역을 옅게 칠해 ‘여기부터’ 를 표시한다.
        g.push(box(f.X(s.n0), f.Y(100), f.X(10) - f.X(s.n0), f.Y(0) - f.Y(100), { fill: CG, op: 0.35, stroke: 'none', rx: 0 }));
        for (const b of s.bands) g.push(fcurve(f, b.fn, { from: 0, to: 10, stroke: b.stroke, sw: 2 }));
        g.push(fcurve(f, fEx, { from: 0, to: 10, stroke: C1, sw: 2.4 }));
        g.push(ln([[f.X(s.n0), f.Y(0)], [f.X(s.n0), f.Y(100)]], { stroke: CK, sw: 1.2, dash: '4 4' }));
        g.push(txt(f.X(s.n0) - 6, f.Y(0) - 7, 'n~0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(f.X(10) + 5, f.Y(fEx(10)) - 2, 'f(n)', { anchor: 'start', cls: 'ink bold', size: 'sm' }));
        s.bands.forEach((b) => {
            const low = b.fn(10) < 50;
            g.push(txt(f.X(10) + 5, f.Y(b.fn(10)) + (low ? 15 : 4), b.name, { anchor: 'start', cls: 'ink2', size: 'sm' }));
        });
        g.push(txt(px0 + pw / 2, py0 + 228, i === 0 ? '회색 영역에서 위 선을 넘지 않는다' : i === 1 ? '회색 영역에서 아래 선 밑으로 안 간다' : '회색 영역에서 두 선 사이에 갇힌다',
            { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 10, '왼쪽 끝(작은 n)에서는 대소가 뒤집혀도 상관없다. 점근 표기가 말하는 것은 오직 회색 영역 안의 일이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-asymptotic-bounds',
        svg: svg({
            width: W, height: H,
            title: '빅오 · 오메가 · 세타의 뜻',
            desc: '어떤 상수 n0 를 지난 뒤부터 f 가 상수배한 g 에 위로 눌리거나 아래로 눌리거나 양쪽으로 갇힌다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-3. 최선 · 평균 · 최악 ---- */
add((() => {
    const W = 720, H = 366;
    const g = [];
    g.push(txt(W / 2, 26, '같은 크기의 입력이라도 비용이 다르다 — 그래서 셋을 따로 잰다', { anchor: 'middle', cls: 'ink bold' }));
    const N = 12;
    const xR = [0, N + 1], yR = [0, N + 1];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 70, y: 56, w: 470, h: 222 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [1, 4, 8, 12], yTicks: [1, 4, 8, 12],
        xLabel: '찾는 값이 놓인 자리', yLabel: '비교 횟수',
    }));
    const bw = (f.X(1) - f.X(0)) * 0.62;
    for (let i = 1; i <= N; i += 1) {
        const hgt = f.Y(0) - f.Y(i);
        const col = i === 1 ? C3 : i === N ? C2 : C1;
        g.push(box(f.X(i) - bw / 2, f.Y(i), bw, hgt, { fill: col, op: i === 1 || i === N ? 0.55 : 0.28, stroke: col, sw: 1 }));
    }
    const avg = (N + 1) / 2;
    g.push(ln([[f.X(0), f.Y(avg)], [f.X(N + 1), f.Y(avg)]], { stroke: CK, sw: 1.6, dash: '7 4' }));
    g.push(txt(f.X(N + 1) + 6, f.Y(avg) + 4, '평균 6.5', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(1), f.Y(1) - 10, '최선 1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(N) - 2, f.Y(N) - 10, '최악 12', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(70, H - 32, '앞에서부터 훑어 찾는 알고리즘. 값이 첫 자리에 있으면 1번, 마지막에 있으면 12번 비교한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(70, H - 12, '어느 자리든 같은 확률이라 보면 평균은 (n+1)/2 번이고, 이것도 n 에 비례하므로 최악과 평균이 같은 O(n) 이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-best-worst-average',
        svg: svg({
            width: W, height: H,
            title: '선형 탐색의 최선 · 평균 · 최악',
            desc: '찾는 값의 위치에 따라 비교 횟수가 1에서 n까지 달라지고 평균은 그 중간이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-4. 두 배 늘리기와 분할상환 ---- */
add((() => {
    const W = 760, H = 412;
    const g = [];
    g.push(txt(W / 2, 24, '가끔 크게 드는 비용을 전체로 나누면 한 번당 상수가 된다', { anchor: 'middle', cls: 'ink bold' }));
    const N = 17;
    // 위 패널 — 한 번의 넣기에 드는 비용
    const xR = [0, N + 1], yR = [0, 18];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 66, y: 60, w: 600, h: 130 } });
    g.push(axes2(f, { xRange: xR, yRange: yR, xTicks: [1, 4, 8, 16], yTicks: [1, 8, 16], xLabel: '몇 번째 넣기', yLabel: '그 한 번의 비용' }));
    const bw2 = (f.X(1) - f.X(0)) * 0.6;
    let sum = 0;
    const cum = [];
    for (let i = 1; i <= N; i += 1) {
        const isPow = i > 1 && (i - 1 & (i - 2)) === 0 && i - 1 >= 1;
        const cost = isPow ? 1 + (i - 1) : 1;
        sum += cost;
        cum.push([i, sum / i]);
        const col = isPow ? C2 : C1;
        g.push(box(f.X(i) - bw2 / 2, f.Y(cost), bw2, f.Y(0) - f.Y(cost), { fill: col, op: isPow ? 0.5 : 0.3, stroke: col, sw: 1 }));
    }
    g.push(txt(f.X(9), f.Y(9) - 8, '자리가 꽉 차 두 배로 옮기는 순간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 아래 패널 — 여기까지의 평균
    const yR2 = [0, 4];
    const f2 = frame({ xRange: xR, yRange: yR2, box: { x: 66, y: 246, w: 600, h: 96 } });
    g.push(axes2(f2, { xRange: xR, yRange: yR2, xTicks: [1, 4, 8, 16], yTicks: [1, 2, 3], xLabel: '몇 번째 넣기', yLabel: '여기까지의 평균 비용' }));
    g.push(ln(cum.map(([i, v]) => [f2.X(i), f2.Y(v)]), { stroke: C1, sw: 2.2 }));
    for (const [i, v] of cum) g.push(pdot(f2.X(i), f2.Y(v), C1, 2.6));
    g.push(ln([[f2.X(0), f2.Y(3)], [f2.X(N + 1), f2.Y(3)]], { stroke: CK, sw: 1.4, dash: '6 4' }));
    g.push(txt(f2.X(N + 1) + 8, f2.Y(3) + 4, '3', { cls: 'ink2', size: 'sm' }));
    g.push(txt(66, H - 32, '위: 대부분은 비용 1 이고, 자리가 꽉 차는 순간에만 이미 든 원소를 전부 옮기느라 크게 든다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(66, H - 12, '아래: 그 값비싼 순간이 점점 드물어지므로 누적 평균은 3 아래에서 멈춘다. 이것이 분할상환 O(1) 이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-amortized-doubling',
        svg: svg({
            width: W, height: H,
            title: '동적 배열 두 배 늘리기의 분할상환 비용',
            desc: '한 번의 넣기 비용은 들쭉날쭉하지만 누적 평균은 상수에 수렴한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-5. 재귀 트리 ---- */
add((() => {
    const W = 760, H = 380;
    const g = [];
    g.push(txt(W / 2, 24, '재귀식을 나무로 펼치면 층마다 드는 비용이 보인다', { anchor: 'middle', cls: 'ink bold' }));
    const cx = 300;
    const levels = [
        { y: 70, xs: [cx], label: 'n', total: 'n' },
        { y: 140, xs: [cx - 120, cx + 120], label: 'n/2', total: '2 × n/2 = n' },
        { y: 210, xs: [cx - 180, cx - 60, cx + 60, cx + 180], label: 'n/4', total: '4 × n/4 = n' },
        { y: 292, xs: [cx - 182, cx - 130, cx - 78, cx - 26, cx + 26, cx + 78, cx + 130, cx + 182], label: '1', total: 'n × 1 = n' },
    ];
    // 간선
    for (let L = 0; L < 3; L += 1) {
        const up = levels[L], dn = levels[L + 1];
        up.xs.forEach((ux, i) => {
            g.push(tedge([ux, up.y], [dn.xs[2 * i], dn.y], { r1: 17, r2r: L === 2 ? 13 : 17, stroke: CG, sw: 1.2 }));
            g.push(tedge([ux, up.y], [dn.xs[2 * i + 1], dn.y], { r1: 17, r2r: L === 2 ? 13 : 17, stroke: CG, sw: 1.2 }));
        });
    }
    g.push(ln([[cx - 205, 244], [cx + 205, 244]], { stroke: CG, sw: 1, dash: '3 4' }));
    g.push(txt(cx, 258, '⋮   중간 층이 이렇게 이어진다   ⋮', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    levels.forEach((L, li) => {
        L.xs.forEach(x => g.push(tnode(x, L.y, L.label, { r: li === 3 ? 13 : 17, col: li === 3 ? C3 : C1 })));
        g.push(txt(586, L.y + 5, L.total, { cls: 'ink', size: 'sm' }));
        g.push(txt(550, L.y + 5, li === 3 ? '잎 층' : `${li} 층`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[560, 56], [560, 306]], { stroke: CG, sw: 1 }));
    g.push(txt(586, 44, '그 층의 비용 합', { cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(500, 330, '층이 log₂ n + 1 개 × 층마다 n = n log₂ n', { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
    g.push(txt(66, H - 30, 'T(n) = 2T(n/2) + n 을 펼친 그림. 문제를 반으로 쪼개므로 층은 log₂ n 개 생기고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(66, H - 12, '층마다 합치는 비용은 언제나 n 이다. 두 값을 곱한 것이 전체 비용이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-recursion-tree',
        svg: svg({
            width: W, height: H,
            title: '재귀 트리로 재귀식 풀기',
            desc: '문제를 반으로 나누는 재귀식을 펼치면 층이 log n 개이고 층마다 비용이 n 이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-6. 마스터 정리의 세 경우 ---- */
add((() => {
    const W = 770, H = 350;
    const g = [];
    g.push(txt(W / 2, 24, '층별 비용이 늘어나는가 · 고른가 · 줄어드는가 — 세 경우는 이것뿐이다', { anchor: 'middle', cls: 'ink bold' }));
    const spec = [
        { title: '1. 잎이 무겁다', sub: '아래로 갈수록 커진다', prof: [0.30, 0.46, 0.68, 1.0], ans: 'T(n) = Θ(잎 층의 비용)', hint: '합치는 비용이 더 작다' },
        { title: '2. 고르다', sub: '층마다 비슷하다', prof: [0.86, 0.86, 0.86, 0.86], ans: 'T(n) = Θ(잎 비용 × log n)', hint: '두 비용이 같은 크기다' },
        { title: '3. 뿌리가 무겁다', sub: '위로 갈수록 커진다', prof: [1.0, 0.68, 0.46, 0.30], ans: 'T(n) = Θ(f(n))', hint: '합치는 비용이 더 크다' },
    ];
    spec.forEach((s, i) => {
        const px0 = 14 + i * 250, py0 = 40, pw = 236, ph = 252;
        g.push(panel(px0, py0, pw, ph, s.title, s.sub));
        const bx = px0 + 30, by = py0 + 56, bw = 176;
        s.prof.forEach((v, k) => {
            const hgt = 26;
            const wdt = bw * v;
            g.push(box(bx + (bw - wdt) / 2, by + k * 34, wdt, hgt, { fill: k === 0 ? C2 : k === 3 ? C3 : C1, op: 0.35, stroke: k === 0 ? C2 : k === 3 ? C3 : C1, sw: 1.2, rx: 3 }));
            g.push(txt(bx + bw + 8, by + k * 34 + 18, k === 3 ? '잎' : `${k} 층`, { cls: 'ink2', size: 'sm' }));
        });
        g.push(txt(px0 + pw / 2, py0 + 210, s.hint, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px0 + pw / 2, py0 + 232, s.ans, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(W / 2, H - 28, '막대의 가로 길이가 그 층 전체의 비용이다. 어느 쪽이 무거운지가 답을 정한다. 잎 층의 비용은 본문의 식으로 정확히 적는다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 10, '세 경우 사이에 틈이 있어서, 어느 쪽에도 해당하지 않는 재귀식에는 마스터 정리를 쓸 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-master-cases',
        svg: svg({
            width: W, height: H,
            title: '마스터 정리의 세 경우',
            desc: '층별 비용이 아래로 갈수록 커지는지 고른지 위가 큰지에 따라 답이 셋으로 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4장 — 배열, 연결 리스트, 스택, 큐
 * ================================================================== */

/* ---- 4-1. 연속된 메모리와 흩어진 메모리 ---- */
add((() => {
    const W = 770, H = 400;
    const g = [];
    g.push(txt(W / 2, 24, '같은 다섯 값을 담아도 메모리에 놓이는 모양이 다르다', { anchor: 'middle', cls: 'ink bold' }));

    // 위 — 배열
    g.push(txt(26, 56, '배열: 한 덩어리로 붙어 있다', { cls: 'ink bold', size: 'sm' }));
    const ax0 = 26, ay0 = 84, cw = 74, ch = 40;
    const vals = ['17', '4', '9', '23', '8'];
    g.push(cells(ax0, ay0, cw, ch, vals, { idx: ['1000', '1004', '1008', '1012', '1016'] }));
    vals.forEach((_, i) => g.push(txt(ax0 + i * cw + cw / 2, ay0 - 9, `[${i}]`, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    g.push(txt(ax0, ay0 + ch + 38, '칸마다 4바이트. 세 번째 칸의 주소는 1000 + 2 × 4 = 1008 로 계산된다 — 곱셈 한 번이면 끝이라 O(1) 이다', { cls: 'ink2', size: 'sm' }));

    // 아래 — 연결 리스트
    g.push(txt(26, 200, '연결 리스트: 아무 데나 흩어져 있고 화살표로 이어진다', { cls: 'ink bold', size: 'sm' }));
    const nodesL = [
        { x: 40, y: 222, v: '17', a: '2040' },
        { x: 320, y: 288, v: '4', a: '1112' },
        { x: 150, y: 288, v: '9', a: '3300' },
        { x: 450, y: 222, v: '23', a: '2044' },
        { x: 580, y: 288, v: '8', a: '1500' },
    ];
    const order = [0, 1, 2, 3, 4];
    const nw = 44, nh = 34;
    for (const nd of nodesL) {
        g.push(box(nd.x, nd.y, nw, nh, { stroke: CK, sw: 1.3 }));
        g.push(box(nd.x + nw, nd.y, 24, nh, { stroke: CK, sw: 1.3, fill: C1, op: 0.16 }));
        g.push(txt(nd.x + nw / 2, nd.y + 23, nd.v, { anchor: 'middle', cls: 'ink' }));
        g.push(txt(nd.x + nw / 2 + 10, nd.y - 7, `주소 ${nd.a}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (let i = 0; i < order.length - 1; i += 1) {
        const a = nodesL[order[i]], b = nodesL[order[i + 1]];
        const sx = a.x + nw + 12, sy = a.y + nh / 2;
        const ex = b.x - 4, ey = b.y + nh / 2;
        const mx = (sx + ex) / 2;
        g.push(curvePath(`M${r2(sx)} ${r2(sy)} C${r2(mx)} ${r2(sy + (ey > sy ? 34 : -34))} ${r2(mx)} ${r2(ey)} ${r2(ex)} ${r2(ey)}`,
            { stroke: C1, sw: 1.7, marker: 'ar1' }));
    }
    g.push(txt(nodesL[4].x + nw + 34, nodesL[4].y + 22, '끝', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, 364, '세 번째 값을 보려면 앞에서부터 화살표를 두 번 따라가야 한다 — 원소 수에 비례하므로 O(n) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, 384, '파란 칸이 다음 노드의 주소다. 값 하나마다 주소 한 개를 더 저장하므로 공간도 더 쓴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-memory-layout',
        svg: svg({
            width: W, height: H,
            title: '배열의 연속 메모리와 연결 리스트의 흩어진 메모리',
            desc: '배열은 주소 계산으로 곧바로 접근하고 연결 리스트는 화살표를 차례로 따라가야 한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-2. 캐시는 줄 단위로 퍼 온다 ---- */
add((() => {
    const W = 760, H = 346;
    const g = [];
    g.push(txt(W / 2, 24, '메모리는 한 칸씩이 아니라 한 줄씩 퍼 온다 — 그래서 붙어 있는 편이 빠르다', { anchor: 'middle', cls: 'ink bold' }));
    const cw = 42, ch = 34;
    // 배열
    g.push(txt(26, 58, '배열을 앞에서 뒤로 훑을 때', { cls: 'ink bold', size: 'sm' }));
    const y1 = 96;
    for (let i = 0; i < 16; i += 1) {
        const inFirst = i < 8;
        g.push(box(26 + i * cw, y1, cw, ch, { stroke: inFirst ? C1 : CK, sw: inFirst ? 1.6 : 1.2, fill: inFirst ? C1 : 'none', op: inFirst ? 0.2 : 1, rx: 2 }));
    }
    g.push(box(24, y1 - 4, 8 * cw + 4, ch + 8, { stroke: C1, sw: 1.6, dash: '5 4', rx: 4 }));
    g.push(txt(26 + 4 * cw, y1 - 14, '한 번 퍼 오면 여덟 칸이 함께 온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(26 + 12 * cw, y1 - 14, '다음 줄', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(26, y1 + ch + 26, '느린 메모리를 건드리는 것은 여덟 칸에 한 번뿐이다. 나머지 일곱 번은 이미 가까이 와 있는 값을 쓴다', { cls: 'ink2', size: 'sm' }));

    // 연결 리스트
    g.push(txt(26, 208, '연결 리스트를 앞에서 뒤로 훑을 때', { cls: 'ink bold', size: 'sm' }));
    const y2 = 226;
    for (let i = 0; i < 16; i += 1) {
        const used = i === 1 || i === 9 || i === 13;
        g.push(box(26 + i * cw, y2, cw, ch, { stroke: used ? C2 : CG, sw: used ? 1.6 : 1, fill: used ? C2 : 'none', op: used ? 0.28 : 1, rx: 2 }));
    }
    for (const s of [[0, 8], [8, 16]]) {
        g.push(box(24 + s[0] * cw, y2 - 4, 8 * cw + 4, ch + 8, { stroke: C2, sw: 1.4, dash: '5 4', rx: 4 }));
    }
    g.push(txt(26, y2 + ch + 26, '노드가 흩어져 있으면 한 줄을 퍼 와도 쓸 값이 하나뿐이다. 같은 개수를 훑어도 느린 메모리를 훨씬 자주 건드린다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 14, '복잡도는 둘 다 O(n) 이지만 실제 시간은 몇 배씩 차이 난다. 점근 표기가 감추는 상수가 여기에 들어 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-cache-locality',
        svg: svg({
            width: W, height: H,
            title: '캐시 지역성 — 붙어 있는 것이 빠른 이유',
            desc: '메모리는 줄 단위로 옮겨지므로 연속된 배열은 한 줄에 여러 값이 담기고 흩어진 노드는 그렇지 않다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-3. 가운데에 끼워 넣기 ---- */
add((() => {
    const W = 770, H = 424;
    const g = [];
    g.push(txt(W / 2, 24, '가운데에 하나 끼워 넣기 — 배열은 밀고, 리스트는 화살표만 고친다', { anchor: 'middle', cls: 'ink bold' }));
    const cw = 52, ch = 36;
    // 배열
    g.push(txt(26, 62, '배열', { cls: 'ink bold', size: 'sm' }));
    g.push(cells(26, 72, cw, ch, ['a', 'b', 'c', 'd', 'e', '']));
    g.push(txt(26 + 6 * cw + 16, 96, '처음', { cls: 'ink2', size: 'sm' }));
    for (let i = 2; i < 5; i += 1) {
        g.push(arw(26 + i * cw + cw / 2, 118, 26 + (i + 1) * cw + cw / 2 - 6, 136, { cls: 's2', width: 1.5 }));
    }
    g.push(cells(26, 142, cw, ch, ['a', 'b', '', 'c', 'd', 'e'], { hl: { 2: C2 } }));
    g.push(txt(26 + 6 * cw + 16, 166, '뒤 셋을 한 칸씩 민 뒤', { cls: 'ink2', size: 'sm' }));
    g.push(cells(26, 200, cw, ch, ['a', 'b', 'x', 'c', 'd', 'e'], { hl: { 2: C3 } }));
    g.push(txt(26 + 6 * cw + 16, 224, '넣는다 — 최악 O(n)', { cls: 'ink2', size: 'sm' }));

    // 연결 리스트
    const ly = 268, nw = 40, nh = 34;
    g.push(txt(26, ly - 16, '연결 리스트', { cls: 'ink bold', size: 'sm' }));
    const xs = [26, 146, 266, 386, 506];
    ['a', 'b', 'c', 'd', 'e'].forEach((v, i) => {
        g.push(box(xs[i], ly, nw, nh, { stroke: CK }));
        g.push(box(xs[i] + nw, ly, 22, nh, { stroke: CK, fill: C1, op: 0.16 }));
        g.push(txt(xs[i] + nw / 2, ly + 23, v, { anchor: 'middle', cls: 'ink' }));
    });
    for (let i = 0; i < 4; i += 1) {
        if (i === 1) continue;
        g.push(arw(xs[i] + nw + 11, ly + nh / 2, xs[i + 1] - 4, ly + nh / 2, { cls: 's1', width: 1.6 }));
    }
    // 새 노드
    const bx = 176, by = ly + 56;
    g.push(box(bx, by, nw, nh, { stroke: C3, sw: 1.8, fill: C3, op: 0.18 }));
    g.push(box(bx + nw, by, 22, nh, { stroke: C3, sw: 1.8, fill: C3, op: 0.18 }));
    g.push(txt(bx + nw / 2, by + 23, 'x', { anchor: 'middle', cls: 'ink' }));
    g.push(curvePath(`M${146 + nw + 11} ${ly + nh / 2} C${190} ${ly + 20} ${bx - 20} ${by} ${bx - 4} ${by + nh / 2}`, { stroke: C3, sw: 1.8, marker: 'ar3' }));
    g.push(curvePath(`M${bx + nw + 11} ${by + nh / 2} C${300} ${by} ${250} ${ly + 20} ${266 - 4} ${ly + nh / 2}`, { stroke: C3, sw: 1.8, marker: 'ar3' }));
    g.push(txt(26, H - 32, '앞뒤 화살표 두 개만 고친다. 나머지 노드는 자리를 옮기지 않으므로 O(1) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 12, '단, 끼워 넣을 자리를 미리 알고 있어야 한다. 그 자리를 찾아가는 데는 여전히 O(n) 이 든다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-insert-middle',
        svg: svg({
            width: W, height: H,
            title: '배열과 연결 리스트에 가운데 삽입하기',
            desc: '배열은 뒤 원소를 모두 밀어야 하고 연결 리스트는 화살표 두 개만 바꾼다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-4. 연결 리스트 세 종류 ---- */
add((() => {
    const W = 760, H = 366;
    const g = [];
    g.push(txt(W / 2, 24, '화살표를 몇 개 두느냐로 할 수 있는 일이 달라진다', { anchor: 'middle', cls: 'ink bold' }));
    const nw = 44, nh = 34, gap = 122;
    const drawRow = (y, kind) => {
        const xs = [80, 80 + gap, 80 + 2 * gap, 80 + 3 * gap];
        const out = [];
        xs.forEach((x, i) => {
            out.push(box(x, y, nw, nh, { stroke: CK }));
            out.push(txt(x + nw / 2, y + 22, ['p', 'q', 'r', 's'][i], { anchor: 'middle', cls: 'ink' }));
            if (kind !== 'double') out.push(box(x + nw, y, 20, nh, { stroke: CK, fill: C1, op: 0.16 }));
            else {
                out.push(box(x + nw, y, 20, nh, { stroke: CK, fill: C1, op: 0.16 }));
                out.push(box(x - 20, y, 20, nh, { stroke: CK, fill: C2, op: 0.16 }));
            }
        });
        for (let i = 0; i < 3; i += 1) {
            out.push(arw(xs[i] + nw + 10, y + 11, xs[i + 1] - (kind === 'double' ? 24 : 4), y + 11, { cls: 's1', width: 1.6 }));
            if (kind === 'double') out.push(arw(xs[i + 1] - 10, y + 26, xs[i] + nw + 24, y + 26, { cls: 's2', width: 1.6 }));
        }
        if (kind === 'circle') {
            out.push(curvePath(`M${xs[3] + nw + 10} ${y + nh / 2} C${xs[3] + 110} ${y - 34} ${xs[0] - 100} ${y - 34} ${xs[0] - 6} ${y + nh / 2}`,
                { stroke: C3, sw: 1.8, marker: 'ar3' }));
        } else {
            out.push(txt(xs[3] + nw + 30, y + 22, '끝', { cls: 'ink2', size: 'sm' }));
        }
        return out.join('');
    };
    g.push(txt(26, 62, '단일 연결 — 앞으로만 간다', { cls: 'ink bold', size: 'sm' }));
    g.push(drawRow(74, 'single'));
    g.push(txt(26, 142, '이중 연결 — 뒤로도 간다. 그래서 노드 하나만 알아도 그 자리에서 지울 수 있다', { cls: 'ink bold', size: 'sm' }));
    g.push(drawRow(154, 'double'));
    g.push(txt(26, 238, '원형 — 끝이 처음을 가리킨다. 시작과 끝의 구분이 사라진다', { cls: 'ink bold', size: 'sm' }));
    g.push(drawRow(284, 'circle'));
    g.push(txt(26, H - 32, '단일 연결에서 노드 하나를 지우려면 그 앞 노드를 알아야 한다. 뒤로 갈 수 없으니 처음부터 다시 훑는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 12, '이중 연결은 주소 하나를 더 저장하는 값을 치르고 그 문제를 없앤다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-list-kinds',
        svg: svg({
            width: W, height: H,
            title: '단일 · 이중 · 원형 연결 리스트',
            desc: '노드가 다음만 가리키는지 이전도 가리키는지 끝이 처음으로 돌아오는지의 차이',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-5. 스택과 큐 ---- */
add((() => {
    const W = 760, H = 346;
    const g = [];
    g.push(txt(W / 2, 24, '넣는 곳과 빼는 곳이 같은가 다른가 — 차이는 이것뿐이다', { anchor: 'middle', cls: 'ink bold' }));
    // 스택
    g.push(panel(20, 44, 350, 258, '스택 — 같은 쪽에서 넣고 뺀다', '나중에 넣은 것이 먼저 나온다'));
    const sx = 130, sw2 = 96, shh = 34;
    ['A', 'B', 'C'].forEach((v, i) => {
        const y = 222 - i * shh;
        g.push(box(sx, y, sw2, shh, { stroke: CK, fill: C1, op: 0.16 }));
        g.push(txt(sx + sw2 / 2, y + 23, v, { anchor: 'middle', cls: 'ink' }));
    });
    g.push(ln([[sx - 2, 120], [sx - 2, 256], [sx + sw2 + 2, 256], [sx + sw2 + 2, 120]], { stroke: CK, sw: 2 }));
    g.push(arw(sx + sw2 + 46, 112, sx + sw2 - 20, 140, { cls: 's3', width: 1.8 }));
    g.push(txt(sx + sw2 + 50, 108, '넣기', { cls: 'ink bold', size: 'sm' }));
    g.push(arw(sx + 20, 140, sx - 46, 112, { cls: 's2', width: 1.8 }));
    g.push(txt(sx - 50, 108, '빼기', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(195, 288, 'C 가 가장 마지막에 들어왔고 가장 먼저 나간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 큐
    g.push(panel(390, 44, 350, 258, '큐 — 한쪽으로 넣고 반대쪽으로 뺀다', '먼저 넣은 것이 먼저 나온다'));
    const qy = 150, qw = 62, qh = 40, qx = 430;
    ['A', 'B', 'C'].forEach((v, i) => {
        g.push(box(qx + i * qw, qy, qw, qh, { stroke: CK, fill: C1, op: 0.16 }));
        g.push(txt(qx + i * qw + qw / 2, qy + 26, v, { anchor: 'middle', cls: 'ink' }));
    });
    g.push(ln([[qx - 2, qy - 4], [qx + 3 * qw + 42, qy - 4]], { stroke: CK, sw: 2 }));
    g.push(ln([[qx - 2, qy + qh + 4], [qx + 3 * qw + 42, qy + qh + 4]], { stroke: CK, sw: 2 }));
    g.push(arw(qx + 3 * qw + 66, qy + qh / 2, qx + 3 * qw + 12, qy + qh / 2, { cls: 's3', width: 1.8 }));
    g.push(txt(qx + 3 * qw + 70, qy + qh / 2 - 14, '넣기', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(qx - 12, qy + qh / 2, qx - 46, qy + qh / 2, { cls: 's2', width: 1.8 }));
    g.push(txt(qx - 30, qy + qh / 2 - 14, '빼기', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(565, 288, 'A 가 가장 먼저 들어왔고 가장 먼저 나간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 12, '둘 다 넣기와 빼기가 O(1) 이다. 자료를 어디에 두느냐가 아니라 어느 쪽 끝만 쓰기로 약속했느냐가 이 둘을 만든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-stack-queue',
        svg: svg({
            width: W, height: H,
            title: '스택과 큐',
            desc: '스택은 같은 쪽에서 넣고 빼고 큐는 한쪽으로 넣어 반대쪽으로 뺀다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-6. 원형 버퍼 ---- */
add((() => {
    const W = 760, H = 400;
    const g = [];
    g.push(txt(W / 2, 24, '배열의 끝에 닿으면 처음으로 돌아간다 — 나머지 연산 하나로 끝난다', { anchor: 'middle', cls: 'ink bold' }));
    // 왼쪽 — 고리 그림
    const cx = 156, cy = 176, R = 90;
    const M = 8;
    const filled = [5, 6, 7, 0, 1];
    for (let i = 0; i < M; i += 1) {
        const a = (-90 + i * 45) * Math.PI / 180;
        const x = cx + R * Math.cos(a), y = cy + R * Math.sin(a);
        const on = filled.includes(i);
        g.push(`<circle cx="${r2(x)}" cy="${r2(y)}" r="18" fill="${on ? C1 : 'none'}" fill-opacity="${on ? 0.2 : 0}" stroke="${on ? C1 : CK}" stroke-width="${on ? 1.8 : 1.2}"/>`);
        g.push(txt(x, y + 5, String(i), { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    const at = (i, d) => {
        const a = (-90 + i * 45) * Math.PI / 180;
        return [cx + (R + d) * Math.cos(a), cy + (R + d) * Math.sin(a)];
    };
    const hd = at(5, 44), hd2 = at(5, 22), tl = at(2, 44), tl2 = at(2, 22);
    g.push(arw(hd[0], hd[1], hd2[0], hd2[1], { cls: 's2', width: 1.8 }));
    g.push(txt(hd[0] - 6, hd[1] + 4, '앞', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(arw(tl[0], tl[1], tl2[0], tl2[1], { cls: 's3', width: 1.8 }));
    g.push(txt(tl[0] + 6, tl[1] + 4, '뒤', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx, cy - 6, '칸 8개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, cy + 14, '든 것 5개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 같은 것을 배열로
    const bx = 372, by = 190, cw = 44, chh = 40;
    g.push(txt(bx, by - 60, '같은 상태를 배열로 펴 보면', { cls: 'ink bold', size: 'sm' }));
    g.push(cells(bx, by, cw, chh, ['w', 'x', '', '', '', 's', 't', 'u'], { idx: [0, 1, 2, 3, 4, 5, 6, 7], hl: { 0: C1, 1: C1, 5: C1, 6: C1, 7: C1 } }));
    g.push(arw(bx + 5 * cw + cw / 2, by - 28, bx + 5 * cw + cw / 2, by - 6, { cls: 's2', width: 1.6 }));
    g.push(txt(bx + 5 * cw + cw / 2, by - 34, '앞 = 5', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(bx + 2 * cw + cw / 2, by - 28, bx + 2 * cw + cw / 2, by - 6, { cls: 's3', width: 1.6 }));
    g.push(txt(bx + 2 * cw + cw / 2, by - 34, '뒤 = 2', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx, by + chh + 44, '든 값이 두 토막으로 보이지만 고리 위에서는 이어져 있다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(26, H - 52, '넣기: 뒤 자리에 쓰고 뒤 ← (뒤 + 1) mod 8.   빼기: 앞 자리를 읽고 앞 ← (앞 + 1) mod 8.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 32, '두 연산 모두 O(1) 이고 원소를 옮기는 일이 없다. 큐를 배열 하나로 만들 때 쓰는 표준 수법이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 12, '가득 찬 상태와 텅 빈 상태가 둘 다 ‘앞 = 뒤’ 로 보이므로, 개수를 따로 세거나 한 칸을 비워 둔다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-ring-buffer',
        svg: svg({
            width: W, height: H,
            title: '원형 버퍼',
            desc: '배열의 끝에서 처음으로 돌아가는 두 개의 표시로 큐를 만든다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5장 — 해시 테이블
 * ================================================================== */

/* ---- 5-1. 키에서 자리를 계산한다 ---- */
add((() => {
    const W = 760, H = 340;
    const g = [];
    g.push(txt(W / 2, 24, '찾아다니는 대신 자리를 계산한다 — 그 계산기가 해시 함수다', { anchor: 'middle', cls: 'ink bold' }));
    const keys = [['‘사과’', 3], ['‘포도’', 0], ['‘감귤’', 5], ['‘배’', 3]];
    const kx = 40, ky = 76, kh = 46;
    keys.forEach((k, i) => {
        g.push(box(kx, ky + i * kh, 90, 32, { stroke: CK, rx: 4 }));
        g.push(txt(kx + 45, ky + i * kh + 21, k[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(kx + 45, ky - 14, '키', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    // 해시 함수 상자
    const hx = 200, hy = 90, hw = 120, hh = 152;
    g.push(box(hx, hy, hw, hh, { stroke: C1, sw: 1.8, fill: C1, op: 0.1, rx: 6 }));
    g.push(txt(hx + hw / 2, hy + 66, 'h(키)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(hx + hw / 2, hy + 88, '해시 함수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 버킷 배열
    const bx = 470, by = 66, bw = 130, bh = 34;
    for (let i = 0; i < 6; i += 1) {
        const hit = [3, 0, 5].includes(i);
        g.push(box(bx, by + i * bh, bw, bh, { stroke: hit ? C1 : CK, sw: hit ? 1.7 : 1.2, fill: hit ? C1 : 'none', op: hit ? 0.16 : 1, rx: 2 }));
        g.push(txt(bx - 10, by + i * bh + 22, String(i), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(bx + bw / 2, by - 14, '버킷 배열 (칸 6개)', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    keys.forEach((k, i) => {
        const y1 = ky + i * kh + 16;
        g.push(arw(kx + 94, y1, hx - 4, hy + 20 + i * 38, { cls: 'ark', width: 1.4 }));
        const y2 = by + k[1] * bh + bh / 2;
        g.push(curvePath(`M${hx + hw + 4} ${hy + 20 + i * 38} C${hx + hw + 70} ${hy + 20 + i * 38} ${bx - 70} ${y2} ${bx - 26} ${y2}`,
            { stroke: k[0] === '‘배’' ? C2 : C1, sw: 1.6, marker: k[0] === '‘배’' ? 'ar2' : 'ar1' }));
    });
    g.push(txt(354, 292, '‘사과’ 와 ‘배’ 가 같은 3번 칸으로 갔다 — 충돌', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(26, H - 26, '키가 무엇이든 h 는 0 부터 5 사이의 수 하나를 내놓는다. 그 수가 곧 배열의 첨자이므로 넣기도 찾기도 한 번에 끝난다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 8, '대신 키의 종류는 칸 수보다 훨씬 많으므로 서로 다른 키가 같은 칸을 받는 일이 반드시 생긴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-hash-map',
        svg: svg({
            width: W, height: H,
            title: '해시 함수가 키를 배열 첨자로 바꾼다',
            desc: '키를 해시 함수에 넣으면 버킷 번호가 나오고 서로 다른 키가 같은 번호를 받으면 충돌이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-2. 체이닝과 개방 주소법 ---- */
add((() => {
    const W = 770, H = 400;
    const g = [];
    g.push(txt(W / 2, 24, '같은 칸을 두 키가 차지하려 할 때 — 밖에 매달거나, 옆칸으로 밀거나', { anchor: 'middle', cls: 'ink bold' }));
    // 왼쪽 체이닝
    g.push(panel(14, 42, 366, 300, '체이닝 — 칸마다 목록을 매단다', '충돌한 키는 그 목록에 이어 붙인다'));
    const cx0 = 60, cy0 = 92, cw = 40, chh = 28;
    const chains = [['E'], [], ['A', 'D', 'G'], ['B'], [], ['C', 'F']];
    chains.forEach((ch, i) => {
        const y = cy0 + i * 34;
        g.push(box(cx0, y, cw, chh, { stroke: CK, rx: 2 }));
        g.push(txt(cx0 - 10, y + 19, String(i), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        ch.forEach((v, k) => {
            const nx = cx0 + cw + 26 + k * 66;
            g.push(arw(nx - 22, y + chh / 2, nx - 4, y + chh / 2, { cls: 's1', width: 1.4 }));
            g.push(box(nx, y, 40, chh, { stroke: C1, sw: 1.5, fill: C1, op: 0.16, rx: 2 }));
            g.push(txt(nx + 20, y + 19, v, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
    });
    g.push(txt(196, 326, '목록이 길어지면 그만큼 훑어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 개방 주소법
    g.push(panel(392, 42, 366, 300, '개방 주소법 — 빈 칸을 찾아 옆으로 민다', '표 밖에 아무것도 두지 않는다'));
    const ox = 456, oy = 92, sh = 26;
    const slots = ['E', '', 'A', 'B', 'D', 'C', 'F', 'G'];
    const moved = { 4: C2, 6: C2, 7: C2 };
    slots.forEach((v, i) => {
        const y = oy + i * sh;
        g.push(box(ox, y, 60, sh - 4, { stroke: moved[i] ?? CK, sw: moved[i] ? 1.7 : 1.2, fill: moved[i] ?? (v ? C1 : 'none'), op: moved[i] ? 0.22 : (v ? 0.14 : 1), rx: 2 }));
        g.push(txt(ox - 10, y + 16, String(i), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (v) g.push(txt(ox + 30, y + 16, v, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(curvePath(`M${ox + 66} ${oy + 2 * sh + 11} C${ox + 106} ${oy + 2 * sh + 11} ${ox + 106} ${oy + 4 * sh + 11} ${ox + 66} ${oy + 4 * sh + 11}`,
        { stroke: C2, sw: 1.7, marker: 'ar2' }));
    g.push(txt(ox + 116, oy + 2 * sh + 16, 'D 도 2번을 원했지만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + 116, oy + 3 * sh + 16, '차 있어서 3, 4 를 보다가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + 116, oy + 4 * sh + 16, '4번에 앉았다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(574, 326, '찬 칸이 뭉치면 탐사가 길어진다 — 군집화', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 28, '체이닝은 적재율이 1 을 넘어도 동작하고 삭제가 간단하다. 개방 주소법은 포인터가 없어 공간이 알뜰하고 캐시에 친하다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 8, '대신 개방 주소법은 적재율이 1 에 가까워지면 급격히 느려지고, 삭제할 때 빈 칸을 그냥 만들면 안 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-collision-handling',
        svg: svg({
            width: W, height: H,
            title: '충돌 처리 — 체이닝과 개방 주소법',
            desc: '체이닝은 칸마다 목록을 매달고 개방 주소법은 비어 있는 다른 칸을 찾아 넣는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-3. 생일 문제 ---- */
add((() => {
    const W = 740, H = 380;
    const g = [];
    g.push(txt(W / 2, 24, '칸이 많아도 충돌은 생각보다 훨씬 빨리 일어난다', { anchor: 'middle', cls: 'ink bold' }));
    const xR = [0, 80], yR = [0, 1];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 74, y: 56, w: 540, h: 246 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [10, 23, 40, 57, 70], yTicks: [0.25, 0.5, 0.75, 1],
        xLabel: '넣은 키의 개수 k', yLabel: '충돌 확률',
    }));
    const pcol = k => {
        let q = 1;
        for (let i = 0; i < k; i += 1) q *= (365 - i) / 365;
        return 1 - q;
    };
    const pts = [];
    for (let k = 0; k <= 80; k += 1) pts.push([f.X(k), f.Y(pcol(k))]);
    g.push(ln(pts, { stroke: C1, sw: 2.4 }));
    for (const k of [23, 57]) {
        const p = pcol(k);
        g.push(ln([[f.X(k), f.Y(0)], [f.X(k), f.Y(p)]], { stroke: CK, sw: 1.1, dash: '4 4' }));
        g.push(ln([[f.X(0), f.Y(p)], [f.X(k), f.Y(p)]], { stroke: CK, sw: 1.1, dash: '4 4' }));
        g.push(pdot(f.X(k), f.Y(p), C2, 5));
    }
    g.push(txt(f.X(23) + 8, f.Y(pcol(23)) - 10, 'k = 23 에서 벌써 0.507', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(57) + 8, f.Y(pcol(57)) - 10, 'k = 57 에서 0.99', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(74, H - 52, '칸이 365개인 표에 키를 하나씩 넣는다. 23개만 넣어도 두 키가 같은 칸에 들어갈 확률이 절반을 넘는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(74, H - 32, '365 의 절반인 182개쯤 넣어야 충돌이 시작될 것 같지만 실제로는 그 8분의 1에서 이미 반반이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(74, H - 12, '비교 대상이 키 하나가 아니라 키 쌍이기 때문이다. 23명이면 쌍이 253개다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-birthday',
        svg: svg({
            width: W, height: H,
            title: '생일 문제 — 충돌은 생각보다 일찍 온다',
            desc: '칸이 365개일 때 키 23개만 넣어도 충돌 확률이 절반을 넘는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-4. 적재율과 성능 ---- */
add((() => {
    const W = 730, H = 384;
    const g = [];
    g.push(txt(W / 2, 24, '표가 얼마나 찼는가 하나가 성능을 정한다', { anchor: 'middle', cls: 'ink bold' }));
    const xR = [0, 1], yR = [0, 12];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 78, y: 56, w: 520, h: 232 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [0.25, 0.5, 0.75, 0.9], yTicks: [2, 4, 6, 8, 10, 12],
        xLabel: '적재율 α', yLabel: '평균 탐사 횟수',
    }));
    g.push(fcurve(f, a => 1 + a, { from: 0, to: 1, stroke: C3, sw: 2.4 }));
    g.push(fcurve(f, a => Math.min(1 / (1 - a), 12), { from: 0, to: 0.92, stroke: C2, sw: 2.4 }));
    g.push(txt(f.X(0.72), f.Y(1.72) + 20, '체이닝 1 + α', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(0.62), f.Y(2.63) - 12, '개방 주소법 1 / (1 − α)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    for (const a of [0.5, 0.75, 0.9]) {
        g.push(pdot(f.X(a), f.Y(1 / (1 - a)), C2, 4.5));
        g.push(txt(f.X(a) - 8, f.Y(1 / (1 - a)) + 4, String(Math.round(1 / (1 - a) * 10) / 10), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(ln([[f.X(0.75), f.Y(0)], [f.X(0.75), f.Y(12)]], { stroke: CK, sw: 1.2, dash: '5 4' }));
    g.push(txt(f.X(0.75) + 6, f.Y(11.4), '흔히 쓰는 문턱 0.75', { cls: 'ink2', size: 'sm' }));
    g.push(txt(78, H - 52, '적재율 α 는 든 원소 수를 칸 수로 나눈 값이다. 두 곡선은 찾는 키가 없을 때의 평균 탐사 횟수 추정이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(78, H - 32, '체이닝은 α 가 1 을 넘어도 완만히 나빠지지만 개방 주소법은 1 근처에서 수직으로 치솟는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(78, H - 12, 'α 가 문턱을 넘으면 표를 두 배로 키우고 전부 다시 넣는다. 그래야 α 가 상수로 유지되고 평균 O(1) 이 지켜진다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-load-factor',
        svg: svg({
            width: W, height: H,
            title: '적재율에 따른 평균 탐사 횟수',
            desc: '체이닝은 1 더하기 알파로 완만하고 개방 주소법은 알파가 1 에 가까워지면 치솟는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-5. 일관 해싱의 고리 ---- */
add((() => {
    const W = 760, H = 390;
    const g = [];
    g.push(txt(W / 2, 24, '서버 수가 바뀌어도 옮기는 키를 최소로 — 나머지 연산 대신 고리를 쓴다', { anchor: 'middle', cls: 'ink bold' }));
    const draw = (ox, oy, R, servers, keys, title, sub, extra) => {
        const out = [];
        out.push(`<circle cx="${r2(ox)}" cy="${r2(oy)}" r="${r2(R)}" fill="none" stroke="${CG}" stroke-width="2"/>`);
        out.push(txt(ox, oy - 6, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(ox, oy + 14, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        for (const s of servers) {
            const a = (s.deg - 90) * Math.PI / 180;
            const x = ox + R * Math.cos(a), y = oy + R * Math.sin(a);
            out.push(box(x - 20, y - 13, 40, 26, { stroke: s.col, sw: 1.8, fill: s.col, op: 0.2, rx: 4 }));
            out.push(txt(x, y + 5, s.name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        for (const k of keys) {
            const a = (k.deg - 90) * Math.PI / 180;
            const x = ox + (R + 30) * Math.cos(a), y = oy + (R + 30) * Math.sin(a);
            out.push(pdot(x, y, k.col ?? CK, 4));
            const lx = ox + (R + 46) * Math.cos(a), ly = oy + (R + 46) * Math.sin(a);
            out.push(txt(lx, ly + 4, k.name, { anchor: Math.cos(a) < -0.2 ? 'end' : Math.cos(a) > 0.2 ? 'start' : 'middle', cls: 'ink2', size: 'sm' }));
        }
        return out.join('') + (extra || '');
    };
    const S = [{ name: 'S1', deg: 20, col: C1 }, { name: 'S2', deg: 150, col: C1 }, { name: 'S3', deg: 265, col: C1 }];
    const K = [{ name: 'a', deg: 60 }, { name: 'b', deg: 118 }, { name: 'c', deg: 232 }, { name: 'd', deg: 318 }];
    g.push(draw(180, 200, 86, S, K, '서버 3대', '키는 시계 방향으로'));
    g.push(txt(180, 316, '다음에 만나는 서버가 맡는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const S2 = [...S, { name: 'S4', deg: 90, col: C3 }];
    const K2 = [{ name: 'a', deg: 60, col: C3 }, { name: 'b', deg: 118 }, { name: 'c', deg: 232 }, { name: 'd', deg: 318 }];
    g.push(draw(560, 200, 86, S2, K2, 'S4 를 끼워 넣으면', '옮기는 키는 a 하나뿐'));
    g.push(txt(560, 316, 'b, c, d 는 그대로 남는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(320, 200, 400, 200, { cls: 's3', width: 2 }));
    g.push(txt(360, 186, '서버 추가', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(26, H - 46, '키와 서버를 같은 해시 값의 고리 위에 얹고, 키는 시계 방향으로 처음 만나는 서버가 맡는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 26, '서버 번호 = 해시 mod 서버수 로 정하면 서버 수가 바뀔 때 거의 모든 키가 자리를 옮겨야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 6, '고리를 쓰면 새로 들어온 서버 앞 구간의 키만 옮긴다. 평균 1/n 만 움직인다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-consistent-hash',
        svg: svg({
            width: W, height: H,
            title: '일관 해싱의 고리',
            desc: '서버와 키를 같은 고리에 얹으면 서버가 늘어도 옮기는 키가 한 구간뿐이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-6. 삭제와 tombstone ---- */
add((() => {
    const W = 780, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '개방 주소법에서 그냥 지우면 뒤에 있는 것을 못 찾는다', { anchor: 'middle', cls: 'ink bold' }));
    const cw = 62, ch = 40, x0 = 168;
    const rows = [
        { y: 76, label: '처음', items: ['', 'A', 'B', 'C', '', '', ''], hl: {}, note: 'A · B · C 가 모두 1번 칸을 원했다. B 는 2번, C 는 3번으로 밀렸다' },
        { y: 156, label: 'B 를 빈 칸으로 지우면', items: ['', 'A', '', 'C', '', '', ''], hl: { 2: C2 }, note: 'C 를 찾을 때 1 → 2 에서 빈 칸을 만나 ‘없다’ 고 답해 버린다' },
        { y: 236, label: '표시만 남기면', items: ['', 'A', '×', 'C', '', '', ''], hl: { 2: C3 }, note: '× 는 ‘여기 있었지만 지웠다, 계속 가라’ 는 뜻이다. 탐사가 멈추지 않는다' },
    ];
    for (const r of rows) {
        g.push(txt(x0 - 12, r.y + 25, r.label, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(cells(x0, r.y, cw, ch, r.items, { hl: r.hl, idx: [0, 1, 2, 3, 4, 5, 6] }));
        g.push(txt(x0, r.y + ch + 32, r.note, { cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(26, H - 10, '이 표시를 묘비(tombstone)라 한다. 묘비가 쌓이면 표가 실제보다 꽉 찬 것처럼 느려지므로 가끔 표를 다시 짓는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-tombstone',
        svg: svg({
            width: W, height: H,
            title: '개방 주소법의 삭제와 묘비',
            desc: '삭제한 자리를 그냥 비우면 탐사가 거기서 멈춰 뒤의 키를 찾지 못한다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6장 — 트리와 힙
 * ================================================================== */

/** 6장 예제로 계속 쓰는 이진 탐색 트리의 좌표. (x, y) 는 화소. */
const DEMO_TREE = ox => ({
    n8: [ox + 240, 86, '8'], n3: [ox + 140, 146, '3'], n10: [ox + 340, 146, '10'],
    n1: [ox + 84, 206, '1'], n6: [ox + 196, 206, '6'], n14: [ox + 396, 206, '14'],
    n4: [ox + 160, 266, '4'], n7: [ox + 232, 266, '7'], n13: [ox + 356, 266, '13'],
});
const DEMO_EDGES = [['n8', 'n3'], ['n8', 'n10'], ['n3', 'n1'], ['n3', 'n6'],
    ['n10', 'n14'], ['n6', 'n4'], ['n6', 'n7'], ['n14', 'n13']];

/* ---- 6-1. 트리 용어 ---- */
add((() => {
    const W = 780, H = 356;
    const g = [];
    g.push(txt(W / 2, 24, '트리를 말하려면 이 여섯 낱말이 필요하다', { anchor: 'middle', cls: 'ink bold' }));
    const nodes = {
        a: [280, 82, 'A'], b: [190, 152, 'B'], c: [380, 152, 'C'],
        d: [130, 222, 'D'], e: [250, 222, 'E'], f: [380, 222, 'F'],
        g1: [210, 292, 'G'], h: [292, 292, 'H'],
    };
    const edges = [['a', 'b'], ['a', 'c'], ['b', 'd'], ['b', 'e'], ['c', 'f'], ['e', 'g1'], ['e', 'h']];
    for (let d = 0; d <= 3; d += 1) {
        const y = 82 + d * 70;
        g.push(ln([[74, y], [446, y]], { stroke: CG, sw: 1, dash: '3 5' }));
        g.push(txt(68, y + 5, `깊이 ${d}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(box(108, 130, 206, 184, { stroke: C1, sw: 1.5, dash: '6 4', rx: 10 }));
    g.push(tree(nodes, edges, { r: 18 }));
    g.push(txt(280, 58, '뿌리(root) — 부모가 없는 단 하나의 노드', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(600, 152, 404, 152, { cls: 's1', width: 1.6 }));
    g.push(txt(606, 156, 'C 의 차수(degree)는 1', { cls: 'ink2', size: 'sm' }));
    g.push(arw(600, 222, 404, 222, { cls: 's2', width: 1.6 }));
    g.push(txt(606, 226, '잎(leaf) — 자식이 없는 노드', { cls: 'ink2', size: 'sm' }));
    g.push(arw(600, 292, 316, 292, { cls: 's3', width: 1.6 }));
    g.push(txt(606, 296, '가장 깊은 잎까지가 높이 3', { cls: 'ink2', size: 'sm' }));
    g.push(txt(606, 96, 'B 아래를 따로 떼면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(606, 114, '그것도 트리다 — 부분트리', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 12, '노드 n 개짜리 트리의 간선은 언제나 n − 1 개다. 뿌리를 뺀 모든 노드가 부모와 이어지는 간선을 정확히 하나씩 갖기 때문이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-tree-terms',
        svg: svg({
            width: W, height: H,
            title: '트리의 용어',
            desc: '뿌리 잎 깊이 높이 차수 부분트리를 한 그림에 표시했다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-2. 순회 세 가지 ---- */
add((() => {
    const W = 780, H = 420;
    const g = [];
    g.push(txt(W / 2, 24, '언제 자기 자신을 처리하는가 — 그것만 다르다', { anchor: 'middle', cls: 'ink bold' }));
    const nodes = DEMO_TREE(50);
    g.push(tree(nodes, DEMO_EDGES, { r: 18 }));
    g.push(txt(544, 96, '왼쪽 부분트리 · 자기 · 오른쪽', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 114, '부분트리 셋을 어느 순서로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 132, '놓느냐가 순회다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 176, '중위 순회의 결과가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(544, 194, '오름차순으로 정렬되어 있다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(544, 212, '이 트리가 이진 탐색 트리라는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 230, '증거다', { cls: 'ink2', size: 'sm' }));
    const rows = [
        ['전위 (자기 → 왼쪽 → 오른쪽)', '8  3  1  6  4  7  10  14  13', C2],
        ['중위 (왼쪽 → 자기 → 오른쪽)', '1  3  4  6  7  8  10  13  14', C1],
        ['후위 (왼쪽 → 오른쪽 → 자기)', '1  4  7  6  3  13  14  10  8', C3],
        ['레벨 (위층부터 왼쪽에서 오른쪽)', '8  3  10  1  6  14  4  7  13', CK],
    ];
    rows.forEach((r, i) => {
        const y = 330 + i * 24;
        g.push(box(26, y - 13, 9, 9, { fill: r[2], op: 0.9, stroke: r[2], rx: 2 }));
        g.push(txt(46, y - 4, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(292, y - 4, r[1], { cls: 'ink', size: 'sm' }));
    });
    return {
        name: 'alg-b-traversals',
        svg: svg({
            width: W, height: H,
            title: '이진트리 순회 네 가지',
            desc: '전위 중위 후위 레벨 순회가 같은 트리를 다른 순서로 훑는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-3. 이진 탐색 트리의 모양이 성능을 정한다 ---- */
add((() => {
    const W = 770, H = 390;
    const g = [];
    g.push(txt(W / 2, 24, '같은 일곱 값이라도 넣은 순서에 따라 트리 모양이 딴판이 된다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 366, 300, '4, 2, 6, 1, 3, 5, 7 순으로 넣으면', '높이 2 — 균형'));
    const bal = {
        r: [196, 116, '4'], l1: [126, 180, '2'], r1: [266, 180, '6'],
        a: [90, 244, '1'], b: [162, 244, '3'], c: [230, 244, '5'], d: [302, 244, '7'],
    };
    g.push(tree(bal, [['r', 'l1'], ['r', 'r1'], ['l1', 'a'], ['l1', 'b'], ['r1', 'c'], ['r1', 'd']], { r: 17 }));
    g.push(tedge([196, 116], [266, 180], { r1: 17, r2r: 17, stroke: C2, sw: 3 }));
    g.push(tedge([266, 180], [302, 244], { r1: 17, r2r: 17, stroke: C2, sw: 3 }));
    g.push(txt(196, 296, '7 을 찾는 데 비교 3번', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(196, 320, '높이가 log₂ n 이라 O(log n)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(392, 42, 366, 300, '1, 2, 3, 4, 5, 6, 7 순으로 넣으면', '높이 6 — 한쪽으로 치우침'));
    const sk = {};
    const se = [];
    for (let i = 0; i < 7; i += 1) {
        sk['s' + i] = [452 + i * 34, 104 + i * 30, String(i + 1)];
        if (i > 0) se.push(['s' + (i - 1), 's' + i]);
    }
    g.push(tree(sk, se, { r: 15 }));
    for (const e of se) g.push(tedge([sk[e[0]][0], sk[e[0]][1]], [sk[e[1]][0], sk[e[1]][1]], { r1: 15, r2r: 15, stroke: C2, sw: 3 }));
    g.push(txt(574, 320, '7 을 찾는 데 비교 7번 — 사실상 연결 리스트다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(26, H - 26, '오른쪽 트리도 이진 탐색 트리의 규칙은 하나도 어기지 않았다. 규칙만으로는 모양을 보장하지 못한다는 것이 문제다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 8, '정렬된 자료를 순서대로 넣는 일은 실무에서 아주 흔하다. 그래서 스스로 균형을 잡는 트리가 필요해진다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-bst-shapes',
        svg: svg({
            width: W, height: H,
            title: '균형 잡힌 이진 탐색 트리와 치우친 트리',
            desc: '같은 값이라도 넣는 순서에 따라 높이가 log n 이 되기도 하고 n 이 되기도 한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-4. 한 번으로 끝나는 회전 (LL · RR) ---- */
add((() => {
    const W = 820, H = 476;
    const g = [];
    g.push(txt(W / 2, 24, 'x · y · z 가 한 방향으로 늘어섰으면 한 번 돌리면 된다', { anchor: 'middle', cls: 'ink bold' }));
    const T = (x, y, s) => subtri(x, y, 42, 34, s, { stroke: CG });
    const draw = side => {
        const out = [];
        for (const [p, q] of side.links) out.push(ln([p, q], { stroke: CG, sw: 1.2 }));
        out.push(side.tris.join(''));
        out.push(tree(side.nodes, side.edges, { r: 17 }));
        return out.join('');
    };
    // LL
    g.push(txt(26, 62, 'LL — 왼쪽으로 두 번 내려간 모양. 오른쪽으로 한 번 돌린다', { cls: 'ink bold', size: 'sm' }));
    g.push(draw({
        nodes: { z: [180, 96, 'z', { col: C2 }], y: [130, 154, 'y', { col: C2 }], x: [80, 212, 'x', { col: C2 }] },
        edges: [['z', 'y'], ['y', 'x']],
        tris: [T(186, 212, 'T3'), T(250, 154, 'T4'), T(40, 264, 'T1'), T(112, 264, 'T2')],
        links: [[[130, 154], [186, 212]], [[180, 96], [250, 154]], [[80, 212], [40, 264]], [[80, 212], [112, 264]]],
    }));
    g.push(draw({
        nodes: { y: [548, 106, 'y', { col: C3 }], x: [488, 170, 'x'], z: [608, 170, 'z'] },
        edges: [['y', 'x'], ['y', 'z']],
        tris: [T(456, 226, 'T1'), T(524, 226, 'T2'), T(576, 226, 'T3'), T(644, 226, 'T4')],
        links: [[[488, 170], [456, 226]], [[488, 170], [524, 226]], [[608, 170], [576, 226]], [[608, 170], [644, 226]]],
    }));
    g.push(arw(340, 170, 400, 170, { cls: 's3', width: 2.2 }));
    g.push(txt(370, 158, '회전', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(370, 200, 'y 가 올라가고', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 218, 'z 가 내려온다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 236, 'T3 만 자리를', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 254, '옮긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // RR
    g.push(txt(26, 322, 'RR — 오른쪽으로 두 번 내려간 모양. 왼쪽으로 한 번 돌린다. LL 의 거울상이다', { cls: 'ink bold', size: 'sm' }));
    g.push(tree({
        z: [110, 352, 'z', { col: C2 }], y: [160, 396, 'y', { col: C2 }], x: [210, 440, 'x', { col: C2 }],
    }, [['z', 'y'], ['y', 'x']], { r: 16 }));
    g.push(tree({
        y: [548, 366, 'y', { col: C3 }], z: [496, 424, 'z'], x: [600, 424, 'x'],
    }, [['y', 'z'], ['y', 'x']], { r: 16 }));
    g.push(arw(340, 400, 400, 400, { cls: 's3', width: 2.2 }));
    g.push(txt(370, 388, '회전', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(646, 396, '부분트리도 LL 과 같은 방식으로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 414, '좌우만 뒤집어 옮겨 간다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-rotate-single',
        svg: svg({
            width: W, height: H,
            title: 'AVL 트리의 단일 회전 LL 과 RR',
            desc: '치우친 세 노드가 한 방향이면 가운데 노드를 위로 올리는 한 번의 회전으로 균형이 맞는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-5. 두 번 돌려야 하는 회전 (LR) ---- */
add((() => {
    const W = 770, H = 356;
    const g = [];
    g.push(txt(W / 2, 24, 'x · y · z 가 꺾여 있으면 먼저 펴고 나서 돌린다 — 원문의 LR 예제', { anchor: 'middle', cls: 'ink bold' }));
    const panels = [
        {
            x: 14, title: '처음 — 9 가 불균형', sub: 'z = 9, y = 3, x = 5 로 꺾였다',
            nodes: { z: [110, 122, '9'], y: [62, 182, '3'], t: [180, 182, '11'], a: [30, 242, '1'], x: [100, 242, '5'], b: [136, 296, '7'] },
            edges: [['z', 'y'], ['z', 't'], ['y', 'a'], ['y', 'x'], ['x', 'b']],
            hl: ['z', 'y', 'x'],
        },
        {
            x: 264, title: '1단계 — 3 을 왼쪽으로 돌린다', sub: '9, 5, 3 이 한 방향이 되었다',
            nodes: { z: [360, 122, '9'], x: [312, 182, '5'], t: [430, 182, '11'], y: [280, 242, '3'], b: [350, 242, '7'], a: [248, 296, '1'] },
            edges: [['z', 'x'], ['z', 't'], ['x', 'y'], ['x', 'b'], ['y', 'a']],
            hl: ['z', 'x', 'y'],
        },
        {
            x: 514, title: '2단계 — 9 를 오른쪽으로 돌린다', sub: '높이 차가 모두 1 이하가 되었다',
            nodes: { x: [630, 130, '5'], y: [570, 196, '3'], z: [690, 196, '9'], a: [534, 262, '1'], b: [654, 262, '7'], t: [726, 262, '11'] },
            edges: [['x', 'y'], ['x', 'z'], ['y', 'a'], ['z', 'b'], ['z', 't']],
            hl: ['x'],
        },
    ];
    for (const p of panels) {
        g.push(panel(p.x, 42, 242, 274, p.title, p.sub));
        const nd = {};
        for (const k of Object.keys(p.nodes)) nd[k] = [...p.nodes[k], p.hl.includes(k) ? { col: C2 } : {}];
        g.push(tree(nd, p.edges, { r: 17 }));
    }
    g.push(txt(26, H - 26, '한 번만 돌리면 꺾인 모양이 반대쪽으로 옮겨 갈 뿐 균형이 잡히지 않는다. 그래서 아래쪽을 먼저 펴 준다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 8, 'RL 은 이것의 거울상이다. 오른쪽 자식을 먼저 오른쪽으로 돌려 편 다음 전체를 왼쪽으로 돌린다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-rotate-double',
        svg: svg({
            width: W, height: H,
            title: 'AVL 트리의 이중 회전 LR',
            desc: '꺾인 모양은 아래쪽을 먼저 돌려 한 방향으로 편 뒤 다시 돌려야 균형이 잡힌다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-6. 힙 — 트리와 배열은 같은 것이다 ---- */
add((() => {
    const W = 780, H = 452;
    const g = [];
    g.push(txt(W / 2, 24, '완전 이진트리는 배열 한 줄로 적을 수 있다 — 포인터가 필요 없다', { anchor: 'middle', cls: 'ink bold' }));
    const vals = [16, 14, 10, 8, 7, 9, 3, 2, 4, 1];
    const pos = [[250, 84], [160, 148], [340, 148], [110, 212], [212, 212], [300, 212], [392, 212], [84, 276], [140, 276], [196, 276]];
    const nodes = {};
    vals.forEach((v, i) => { nodes['k' + i] = [pos[i][0], pos[i][1], String(v)]; });
    const edges = [];
    for (let i = 1; i < vals.length; i += 1) edges.push(['k' + ((i - 1) >> 1), 'k' + i]);
    g.push(tree(nodes, edges, { r: 17 }));
    vals.forEach((v, i) => g.push(txt(pos[i][0] + 20, pos[i][1] - 14, String(i), { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    g.push(txt(250, 56, '노드 옆의 작은 수가 배열 첨자다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const bx = 96, by = 316, cw = 54, ch = 38;
    g.push(cells(bx, by, cw, ch, vals.map(String), { idx: vals.map((_, i) => i), hl: { 1: C2, 3: C1, 4: C1 } }));
    g.push(txt(bx - 12, by + 25, '배열', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(curvePath(`M${bx + 1.5 * cw} ${by - 4} C${bx + 1.5 * cw} ${by - 30} ${bx + 3.5 * cw} ${by - 30} ${bx + 3.5 * cw} ${by - 4}`, { stroke: C1, sw: 1.6, marker: 'ar1' }));
    g.push(curvePath(`M${bx + 1.5 * cw} ${by - 4} C${bx + 1.5 * cw} ${by - 44} ${bx + 4.5 * cw} ${by - 44} ${bx + 4.5 * cw} ${by - 4}`, { stroke: C1, sw: 1.6, marker: 'ar1' }));
    g.push(txt(96, 396, '1번 칸의 자식은 3번 칸과 4번 칸이다. 트리에서 14 의 자식이 8 과 7 인 것과 같다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(96, 416, 'i 의 자식 = 2i + 1 과 2i + 2,    i 의 부모 = (i − 1) ÷ 2 의 몫', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(96, 438, '이 세 줄의 산술만으로 트리를 오르내린다. 저장하는 것은 값뿐이고 주소는 하나도 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 116, '힙 성질: 부모 ≥ 두 자식', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(508, 138, '형제끼리는 아무 관계가 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 156, '그래서 힙은 정렬된 것이 아니고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 174, '가장 큰 값이 뿌리에 있다는 것만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 192, '보장된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 226, '빈틈없이 채운 완전 이진트리라서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(508, 244, '첨자 계산이 어긋나지 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-heap-array',
        svg: svg({
            width: W, height: H,
            title: '힙의 트리 표현과 배열 표현',
            desc: '완전 이진트리를 위층부터 왼쪽에서 오른쪽으로 배열에 적으면 첨자 계산으로 부모와 자식을 찾는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-7. 내려가기 ---- */
add((() => {
    const W = 770, H = 330;
    const g = [];
    g.push(txt(W / 2, 24, '규칙을 어긴 값은 더 큰 자식과 자리를 바꾸며 내려간다', { anchor: 'middle', cls: 'ink bold' }));
    const shape = ox => ({
        r: [ox + 120, 116, ''], l: [ox + 74, 180, ''], rr: [ox + 166, 180, ''],
        a: [ox + 44, 244, ''], b: [ox + 104, 244, ''], c: [ox + 138, 244, ''], d: [ox + 196, 244, ''],
    });
    const edges = [['r', 'l'], ['r', 'rr'], ['l', 'a'], ['l', 'b'], ['rr', 'c'], ['rr', 'd']];
    const steps = [
        { x: 14, title: '2 가 뿌리에 있다', sub: '두 자식보다 작아 규칙 위반', vals: { r: '2', l: '14', rr: '10', a: '8', b: '7', c: '9', d: '3' }, hl: ['r', 'l'] },
        { x: 264, title: '더 큰 자식 14 와 바꾼다', sub: '아직 8 보다 작다', vals: { r: '14', l: '2', rr: '10', a: '8', b: '7', c: '9', d: '3' }, hl: ['l', 'a'] },
        { x: 514, title: '다시 8 과 바꾸면 끝', sub: '모든 부모가 자식보다 크다', vals: { r: '14', l: '8', rr: '10', a: '2', b: '7', c: '9', d: '3' }, hl: [] },
    ];
    for (const s of steps) {
        g.push(panel(s.x, 42, 242, 240, s.title, s.sub));
        const base = shape(s.x);
        const nd = {};
        for (const k of Object.keys(base)) nd[k] = [base[k][0], base[k][1], s.vals[k], s.hl.includes(k) ? { col: C2 } : {}];
        g.push(tree(nd, edges, { r: 17 }));
    }
    g.push(txt(26, H - 26, '바꿀 때마다 한 층씩 내려가므로 많아야 트리 높이만큼, 곧 O(log n) 번이면 끝난다. 올리기는 이것의 반대다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 8, '넣기는 배열 맨 뒤에 붙이고 올리며, 꺼내기는 뿌리를 떼고 맨 뒤 값을 뿌리에 얹은 뒤 내린다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-heap-siftdown',
        svg: svg({
            width: W, height: H,
            title: '힙에서 값을 내리기',
            desc: '힙 성질을 어긴 값이 더 큰 자식과 자리를 바꾸며 한 층씩 내려간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-8. 힙 만들기가 O(n) 인 이유 ---- */
add((() => {
    const W = 780, H = 368;
    const g = [];
    g.push(txt(W / 2, 24, '아래층에 노드가 몰려 있고, 아래층일수록 적게 내려간다', { anchor: 'middle', cls: 'ink bold' }));
    const rows = [
        { y: 84, xs: [270], lv: '0 층', cnt: '1개', work: '최대 3칸', prod: '3' },
        { y: 148, xs: [190, 350], lv: '1 층', cnt: '2개', work: '최대 2칸', prod: '4' },
        { y: 212, xs: [140, 240, 300, 400], lv: '2 층', cnt: '4개', work: '최대 1칸', prod: '4' },
        { y: 276, xs: [110, 170, 210, 270, 310, 370, 410, 450], lv: '3 층 (잎)', cnt: '8개', work: '0칸', prod: '0' },
    ];
    for (let L = 0; L < 3; L += 1) {
        rows[L].xs.forEach((ux, i) => {
            g.push(tedge([ux, rows[L].y], [rows[L + 1].xs[2 * i], rows[L + 1].y], { r1: 15, r2r: L === 2 ? 11 : 15, stroke: CG, sw: 1.1 }));
            g.push(tedge([ux, rows[L].y], [rows[L + 1].xs[2 * i + 1], rows[L + 1].y], { r1: 15, r2r: L === 2 ? 11 : 15, stroke: CG, sw: 1.1 }));
        });
    }
    rows.forEach((r, i) => {
        r.xs.forEach(x => g.push(tnode(x, r.y, '', { r: i === 3 ? 11 : 15, col: i === 3 ? C3 : C1 })));
        g.push(txt(508, r.y + 5, r.lv, { cls: 'ink2', size: 'sm' }));
        g.push(txt(590, r.y + 5, r.cnt, { anchor: 'end', cls: 'ink', size: 'sm' }));
        g.push(txt(688, r.y + 5, r.work, { anchor: 'end', cls: 'ink', size: 'sm' }));
        g.push(txt(748, r.y + 5, r.prod, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(590, 58, '노드 수', { anchor: 'end', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(688, 58, '내려갈 칸', { anchor: 'end', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(748, 58, '곱', { anchor: 'end', cls: 'ink2 bold', size: 'sm' }));
    g.push(ln([[500, 66], [756, 66]], { stroke: CG, sw: 1 }));
    g.push(ln([[620, 296], [756, 296]], { stroke: CG, sw: 1 }));
    g.push(txt(688, 316, '합계', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(748, 316, '11', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(26, H - 26, '노드 15개짜리 힙을 아래층부터 훑으며 만들면 자리바꿈이 많아야 11번이다. 15 × log₂ 15 ≈ 59 가 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 8, '노드 절반이 잎이라 아예 일하지 않고 많이 내려가야 하는 노드는 위층에 몇 개뿐이다. 합은 언제나 2n 을 넘지 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-build-heap',
        svg: svg({
            width: W, height: H,
            title: '힙 만들기가 O(n) 인 이유',
            desc: '아래층일수록 노드가 많지만 내려갈 칸이 적어 층별 비용의 합이 노드 수에 비례한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-9. 트라이 ---- */
add((() => {
    const W = 760, H = 376;
    const g = [];
    g.push(txt(W / 2, 24, '문자열을 노드에 담지 않는다 — 뿌리에서 여기까지 온 길이 곧 문자열이다', { anchor: 'middle', cls: 'ink bold' }));
    const nodes = {
        root: [140, 82, '', { r: 12 }],
        c: [220, 146, 'c'], a: [220, 210, 'a'],
        r: [140, 274, 'r', { col: C3 }], t: [220, 274, 't', { col: C3 }], p: [300, 274, 'p', { col: C3 }],
        d: [420, 146, 'd'], o: [420, 210, 'o', { col: C3 }], gg: [420, 274, 'g', { col: C3 }],
    };
    const edges = [['root', 'c'], ['c', 'a'], ['a', 'r'], ['a', 't'], ['a', 'p'], ['root', 'd'], ['d', 'o'], ['o', 'gg']];
    g.push(tree(nodes, edges, { r: 17 }));
    g.push(txt(140, 62, '뿌리 (빈 문자열)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const [k, lab] of [['r', 'car'], ['t', 'cat'], ['p', 'cap'], ['gg', 'dog']]) {
        const nd = nodes[k];
        g.push(txt(nd[0], nd[1] + 36, lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(452, 214, 'do', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(520, 116, '초록 테두리는 ‘여기서 단어가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 134, '끝난다’ 는 표시다. do 처럼', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 152, '다른 단어의 앞부분인 단어가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 170, '있기 때문에 필요하다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 210, 'car · cat · cap 은 앞 두 글자를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 228, '공유하고 그만큼 공간을 아낀다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 44, '길이 m 인 문자열을 찾는 데 드는 시간은 m 번의 내려가기, 곧 O(m) 이다. 사전에 든 단어가 몇 개든 상관없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 24, '해시 테이블도 O(m) 이지만 트라이는 ‘ca 로 시작하는 단어를 전부’ 같은 접두어 질의를 그대로 처리한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(26, H - 4, '자동완성이 트라이를 쓰는 이유가 이것이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-b-trie',
        svg: svg({
            width: W, height: H,
            title: '트라이 — 문자열 사전',
            desc: '간선을 따라 글자를 하나씩 붙여 내려가고 뿌리에서 온 길이 곧 문자열이 된다',
            body: g.join(''),
        }),
    };
})());

export default figures;
