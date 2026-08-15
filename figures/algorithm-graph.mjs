/**
 * 알고리즘 7장(정렬과 선택) · 8장(그래프 — 표현과 탐색) · 9장(최단경로와 최소신장트리)의 그림.
 *
 * 이름은 모두 `alg-g-` 로 시작한다(담당 B 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `d~0` 표기를, 나머지는 유니코드(≤ ≥ ∞ → ⌊⌋ ² ⁿ ₂ ×)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 파일이 있는 이유는 두 가지다.
 *   정렬은 ‘한 단계씩 어떻게 변하는가’ 를 정지 화면으로 늘어놓지 않으면 읽히지 않는다.
 *   그래프 탐색은 ‘프런티어에 무엇이 담겨 있는가’ 를 보여 주지 않으면
 *   DFS 와 BFS 가 같은 코드에 자료구조만 다르다는 사실이 드러나지 않는다.
 */
import { svg, txt } from './lib.mjs';

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
 * 화소 좌표 소도구 — algorithm-basic.mjs 의 헬퍼를 같은 규약으로 둔다.
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
function cells(x, y, w, h, items, { hl = {}, idx = null, idxTop = false, sw = 1.3, small = false } = {}) {
    const g = [];
    items.forEach((v, i) => {
        const cx = x + i * w;
        const col = hl[i];
        g.push(box(cx, y, w, h, { fill: col ?? 'none', op: col ? 0.2 : 1, stroke: col ?? CK, sw: col ? 1.8 : sw, rx: 2 }));
        if (v !== null && v !== undefined && v !== '') {
            g.push(txt(cx + w / 2, y + h / 2 + 5, String(v), { anchor: 'middle', cls: 'ink', size: small || w < 32 ? 'sm' : undefined }));
        }
        if (idx) {
            const ty = idxTop ? y - 6 : y + h + 14;
            g.push(txt(cx + w / 2, ty, String(idx[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    return g.join('');
}

/** 칸을 가리키는 작은 삼각형과 이름표. up 이면 칸 위에서 아래를 가리킨다. */
function mark(x, y, label, { col = C1, up = true } = {}) {
    const d = up
        ? `M${r2(x - 5)} ${r2(y - 10)} L${r2(x + 5)} ${r2(y - 10)} L${r2(x)} ${r2(y - 1)} Z`
        : `M${r2(x - 5)} ${r2(y + 10)} L${r2(x + 5)} ${r2(y + 10)} L${r2(x)} ${r2(y + 1)} Z`;
    return `<path d="${d}" fill="${col}"/>`
        + (label ? txt(x, up ? y - 15 : y + 24, label, { anchor: 'middle', cls: 'ink', size: 'sm' }) : '');
}

/** 그래프 노드 한 개. 원과 가운데 글자. */
function gnode(x, y, label, { r = 15, col = null, dim = false, dash } = {}) {
    const stroke = col ?? (dim ? CG : CK);
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col ?? 'none'}" fill-opacity="${col ? 0.18 : 0}" stroke="${stroke}" stroke-width="${col ? 2 : 1.4}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`
        + txt(x, y + 5, label, { anchor: 'middle', cls: dim ? 'ink2' : 'ink', size: r < 14 ? 'sm' : undefined });
}

/** 두 노드의 중심을 잇되 원 반지름만큼 잘라 그린다. dir 이면 화살촉을 붙인다. */
function gedge(p1, p2, { r1 = 15, rr = 15, stroke = CK, sw = 1.4, dash, dir = false, mk = 'ark' } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    const a = [p1[0] + ux * r1, p1[1] + uy * r1];
    const b = [p2[0] - ux * (rr + (dir ? 4 : 0)), p2[1] - uy * (rr + (dir ? 4 : 0))];
    if (!dir) return ln([a, b], { stroke, sw, dash });
    return `<path d="M${r2(a[0])} ${r2(a[1])} L${r2(b[0])} ${r2(b[1])}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''} marker-end="url(#${mk})"/>`;
}

/** 간선 가중치 이름표. 선 가운데에서 수직으로 off 만큼 밀어 놓는다. */
function wlabel(p1, p2, v, { off = 11, cls = 'ink2' } = {}) {
    const mx = (p1[0] + p2[0]) / 2, my = (p1[1] + p2[1]) / 2;
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    return txt(mx - (dy / L) * off, my + (dx / L) * off + 4, String(v), { anchor: 'middle', cls: `${cls} bold`, size: 'sm' });
}

/**
 * 그래프 하나를 통째로 그린다.
 * nodes: { 이름: [x, y, 라벨, 옵션] }, edges: [[a, b, 옵션], ...]
 */
function graph(nodes, edges, { r = 15, dir = false } = {}) {
    const g = [];
    for (const [a, b, o] of edges) {
        const A = nodes[a], B = nodes[b];
        if (!A || !B) continue;
        const oo = o || {};
        g.push(gedge([A[0], A[1]], [B[0], B[1]], {
            r1: (A[3] && A[3].r) || r, rr: (B[3] && B[3].r) || r,
            stroke: oo.stroke || CK, sw: oo.sw || 1.4, dash: oo.dash,
            dir: oo.dir ?? dir, mk: oo.mk || (oo.stroke === C2 ? 'ar2' : oo.stroke === C1 ? 'ar1' : oo.stroke === C3 ? 'ar3' : 'ark'),
        }));
        if (oo.w !== undefined) g.push(wlabel([A[0], A[1]], [B[0], B[1]], oo.w, { off: oo.woff ?? 11, cls: oo.wcls || 'ink2' }));
    }
    for (const k of Object.keys(nodes)) {
        const [x, y, label, o] = nodes[k];
        g.push(gnode(x, y, label, { r, ...(o || {}) }));
    }
    return g.join('');
}

/** 정사각 격자. shade(i, j) 가 색을 돌려주면 그 칸을 칠한다. */
function grid(x, y, cw, cols, rows, shade) {
    const g = [];
    for (let j = 0; j < rows; j += 1) {
        for (let i = 0; i < cols; i += 1) {
            const col = shade(i, j);
            g.push(box(x + i * cw, y + j * cw, cw, cw, { fill: col ?? 'none', op: col ? 0.3 : 1, stroke: CG, sw: 0.7, rx: 1 }));
        }
    }
    return g.join('');
}

/* ================================================================== *
 * 7장 — 정렬과 선택
 * ================================================================== */

/* ---- 7-1. 안정성과 제자리 ---- */
add((() => {
    const W = 790, H = 348;
    const g = [];
    g.push(txt(W / 2, 24, '정렬을 고를 때 보는 두 가지 — 같은 값의 앞뒤가 남는가, 남의 자리를 빌리는가', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 안정성
    g.push(panel(14, 42, 452, 262, '안정성 — 점수가 같은 두 사람의 순서', '입력에서 박이 이보다 앞에 있었다'));
    const recs = [['3', '박'], ['1', '김'], ['3', '이'], ['2', '최']];
    const rows = [
        { y: 88, lab: '입력', order: [0, 1, 2, 3], hl: { 0: C2, 2: C2 } },
        { y: 158, lab: '안정 정렬', order: [1, 3, 0, 2], hl: { 2: C2, 3: C2 } },
        { y: 228, lab: '불안정 정렬', order: [1, 3, 2, 0], hl: { 2: C2, 3: C2 } },
    ];
    for (const r of rows) {
        g.push(txt(112, r.y + 26, r.lab, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        r.order.forEach((src, i) => {
            const cx = 124 + i * 76;
            const col = r.hl[i];
            g.push(box(cx, r.y, 66, 44, { fill: col ?? 'none', op: col ? 0.2 : 1, stroke: col ?? CK, sw: col ? 1.8 : 1.3, rx: 4 }));
            g.push(txt(cx + 33, r.y + 24, recs[src][0], { anchor: 'middle', cls: 'ink' }));
            g.push(txt(cx + 33, r.y + 38, recs[src][1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
    }
    g.push(txt(28, 292, '가운데 줄은 박 · 이 순서가 그대로고 아래 줄은 뒤바뀌었다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽: 제자리 여부
    g.push(panel(478, 42, 298, 262, '제자리 여부 — 배열 밖에 얼마나 더 쓰는가', '원소가 n 개일 때 필요한 추가 칸'));
    g.push(txt(494, 96, '제자리(in-place) — 삽입 · 힙 · 퀵', { cls: 'ink', size: 'sm' }));
    g.push(cells(496, 106, 30, 26, [5, 2, 8, 1, 9, 3, 7, 4], { small: true }));
    g.push(box(742, 106, 24, 26, { stroke: C1, sw: 1.8, rx: 2 }));
    g.push(txt(754, 148, '상수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 196, '아닌 것 — 병합 정렬', { cls: 'ink', size: 'sm' }));
    g.push(cells(496, 206, 30, 26, [5, 2, 8, 1, 9, 3, 7, 4], { small: true }));
    g.push(cells(496, 240, 30, 26, ['', '', '', '', '', '', '', ''], { hl: { 0: C2, 1: C2, 2: C2, 3: C2, 4: C2, 5: C2, 6: C2, 7: C2 } }));
    g.push(txt(748, 260, 'n 칸', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 288, '메모리가 빠듯하면 이 한 줄이 결정적이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, H - 24, '안정성이 필요한 까닭: 이름으로 한 번 정렬한 뒤 점수로 안정 정렬하면 점수가 같은 사람은 이름순이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '불안정 정렬로는 이 두 단계 요령을 쓸 수 없다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-sort-stable',
        svg: svg({
            width: W, height: H,
            title: '안정성과 제자리 여부',
            desc: '안정 정렬은 키가 같은 원소의 입력 순서를 지키고, 제자리 정렬은 상수 칸만 더 쓴다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-2. O(n²) 셋을 한 그림에 ---- */
add((() => {
    const W = 792, H = 396;
    const g = [];
    g.push(txt(W / 2, 24, '같은 입력 5 2 4 1 3 을 세 방법이 다르게 줄여 간다', { anchor: 'middle', cls: 'ink bold' }));
    const G = C3, O = C2;
    const specs = [
        {
            t: '버블 정렬', s: '붙어 있는 두 개만 견준다',
            rows: [
                ['시작', [5, 2, 4, 1, 3], {}],
                ['1회', [2, 4, 1, 3, 5], { 4: G }],
                ['2회', [2, 1, 3, 4, 5], { 3: G, 4: G }],
                ['3회', [1, 2, 3, 4, 5], { 2: G, 3: G, 4: G }],
            ],
            note: ['큰 값이 한 칸씩 밀려 뒤에', '쌓인다. 뒤쪽부터 정해진다'],
        },
        {
            t: '선택 정렬', s: '남은 것 중 최솟값을 찾는다',
            rows: [
                ['시작', [5, 2, 4, 1, 3], {}],
                ['1회', [1, 2, 4, 5, 3], { 0: G }],
                ['2회', [1, 2, 4, 5, 3], { 0: G, 1: G }],
                ['3회', [1, 2, 3, 5, 4], { 0: G, 1: G, 2: G }],
                ['4회', [1, 2, 3, 4, 5], { 0: G, 1: G, 2: G, 3: G }],
            ],
            note: ['찾는 일은 많지만 교환은', '많아야 n − 1 번뿐이다'],
        },
        {
            t: '삽입 정렬', s: '앞쪽 정렬된 곳에 끼운다',
            rows: [
                ['시작', [5, 2, 4, 1, 3], { 0: G }],
                ['1회', [2, 5, 4, 1, 3], { 0: G, 1: G }],
                ['2회', [2, 4, 5, 1, 3], { 0: G, 1: G, 2: G }],
                ['3회', [1, 2, 4, 5, 3], { 0: G, 1: G, 2: G, 3: G }],
                ['4회', [1, 2, 3, 4, 5], { 0: G, 1: G, 2: G, 3: G, 4: G }],
            ],
            note: ['이미 정렬돼 있으면 끼울 곳을', '바로 찾아 O(n) 으로 끝난다'],
        },
    ];
    specs.forEach((sp, k) => {
        const px0 = 14 + k * 256, py0 = 40, pw = 248, ph = 306;
        g.push(panel(px0, py0, pw, ph, sp.t, sp.s));
        sp.rows.forEach((r, i) => {
            const ry = py0 + 54 + i * 38;
            g.push(txt(px0 + 44, ry + 19, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
            g.push(cells(px0 + 52, ry, 30, 26, r[1], { hl: r[2], small: true }));
        });
        g.push(txt(px0 + pw / 2, py0 + 268, sp.note[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px0 + pw / 2, py0 + 286, sp.note[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(20, H - 26, '초록으로 칠한 칸이 ‘이제 확정된 자리’ 다. 세 방법의 차이는 그 초록이 어느 쪽에서 자라는가에 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '견주는 횟수는 셋 다 n(n−1)/2 에 가까워 모두 O(n²) 이지만 교환 횟수와 최선의 경우는 크게 다르다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-quadratic-three',
        svg: svg({
            width: W, height: H,
            title: '버블 · 선택 · 삽입 정렬을 한 단계씩',
            desc: '같은 입력에 대해 세 정렬이 확정된 구간을 서로 다른 방향으로 늘려 간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-3. 병합 정렬 — 쪼개고 합치는 층 ---- */
add((() => {
    const W = 772, H = 438;
    const g = [];
    g.push(txt(W / 2, 24, '쪼개는 데는 일이 들지 않는다. 값은 전부 합치는 층에서 움직인다', { anchor: 'middle', cls: 'ink bold' }));
    const CW = 30, CX = 372;
    const drawRow = (y, groups, { hl = null, lab = '', note = '' } = {}) => {
        const total = groups.reduce((a, b) => a + b.length, 0) * CW + (groups.length - 1) * 12;
        let x = CX - total / 2;
        const out = [];
        for (const grp of groups) {
            out.push(cells(x, y, CW, 24, grp, { hl: hl ? Object.fromEntries(grp.map((_, i) => [i, hl])) : {}, small: true }));
            x += grp.length * CW + 12;
        }
        if (lab) out.push(txt(CX - total / 2 - 14, y + 17, lab, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (note) out.push(txt(CX + total / 2 + 16, y + 17, note, { cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(drawRow(56, [[5, 2, 8, 1, 9, 3, 7, 4]], { lab: '크기 8' }));
    g.push(drawRow(104, [[5, 2, 8, 1], [9, 3, 7, 4]], { lab: '크기 4' }));
    g.push(drawRow(152, [[5, 2], [8, 1], [9, 3], [7, 4]], { lab: '크기 2' }));
    g.push(drawRow(200, [[5], [2], [8], [1], [9], [3], [7], [4]], { lab: '크기 1', note: '하나짜리는 이미 정렬돼 있다' }));
    g.push(drawRow(256, [[2, 5], [1, 8], [3, 9], [4, 7]], { hl: C3, lab: '합치기 1', note: '이 층의 비용 8' }));
    g.push(drawRow(304, [[1, 2, 5, 8], [3, 4, 7, 9]], { hl: C3, lab: '합치기 2', note: '이 층의 비용 8' }));
    g.push(drawRow(352, [[1, 2, 3, 4, 5, 7, 8, 9]], { hl: C3, lab: '합치기 3', note: '이 층의 비용 8' }));
    g.push(arw(40, 52, 40, 210, { cls: 'ark', width: 1.6 }));
    g.push(txt(52, 130, '쪼갠다', { cls: 'ink2', size: 'sm' }));
    g.push(arw(40, 372, 40, 250, { cls: 's3', width: 1.6 }));
    g.push(txt(52, 310, '합친다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '합치는 층이 log₂ 8 = 3 개이고 층마다 값 8 개가 한 번씩 옮겨 가므로 전체가 8 × 3 = 24 번이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '일반적으로 층은 log₂ n 개, 층마다 n 이므로 n log n 이다. 어느 층도 특별히 무겁지 않은 것이 요점이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-merge-tree',
        svg: svg({
            width: W, height: H,
            title: '병합 정렬의 층',
            desc: '하나짜리까지 쪼갠 뒤 두 개씩 합쳐 올라간다. 층마다 비용이 n 이고 층이 log n 개다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-4. 두 정렬된 줄을 합치기 ---- */
add((() => {
    const W = 768, H = 412;
    const g = [];
    g.push(txt(W / 2, 24, '두 줄의 맨 앞만 견준다 — 그래서 합치기가 n 번으로 끝난다', { anchor: 'middle', cls: 'ink bold' }));
    const A = [1, 2, 5, 8], B = [3, 4, 7, 9];
    const frames = [
        { lab: '옮긴 것 0개', i: 0, j: 0, out: [], note: '1 < 3 → 왼쪽에서 1 을 꺼낸다' },
        { lab: '옮긴 것 3개', i: 2, j: 1, out: [1, 2, 3], note: '4 < 5 → 오른쪽에서 4 를 꺼낸다' },
        { lab: '옮긴 것 6개', i: 3, j: 3, out: [1, 2, 3, 4, 5, 7], note: '8 < 9 → 왼쪽에서 8 을 꺼낸다' },
    ];
    frames.forEach((f, k) => {
        const y = 60 + k * 98;
        g.push(txt(20, y + 30, f.lab, { cls: 'ink2', size: 'sm' }));
        const hlA = f.i < 4 ? { [f.i]: C2 } : {};
        const hlB = f.j < 4 ? { [f.j]: C2 } : {};
        const dimA = Object.fromEntries(A.map((_, i) => [i, i < f.i ? CG : undefined]).filter(e => e[1]));
        const dimB = Object.fromEntries(B.map((_, i) => [i, i < f.j ? CG : undefined]).filter(e => e[1]));
        g.push(cells(104, y, 30, 26, A, { hl: { ...dimA, ...hlA }, small: true }));
        g.push(cells(104, y + 42, 30, 26, B, { hl: { ...dimB, ...hlB }, small: true }));
        g.push(txt(98, y + 18, '왼쪽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(98, y + 60, '오른쪽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        if (f.i < 4) g.push(mark(104 + f.i * 30 + 15, y - 2, '', { col: C2 }));
        if (f.j < 4) g.push(mark(104 + f.j * 30 + 15, y + 42 + 26, '', { col: C2, up: false }));
        const outItems = [...f.out, ...Array(8 - f.out.length).fill('')];
        g.push(cells(300, y + 21, 30, 26, outItems, { hl: Object.fromEntries(f.out.map((_, i) => [i, C3])), small: true }));
        g.push(txt(300, y + 12, '결과', { cls: 'ink2', size: 'sm' }));
        g.push(txt(552, y + 40, f.note, { cls: 'ink', size: 'sm' }));
    });
    g.push(txt(20, H - 44, '주황 삼각형이 각 줄에서 아직 안 꺼낸 첫 칸이다. 작은 쪽을 꺼내고 그 줄의 삼각형만 한 칸 나아간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '값 하나를 옮길 때마다 견주기가 한 번이므로 합치기 전체가 두 줄의 길이 합, 곧 O(n) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '결과를 따로 담아야 하므로 병합 정렬은 제자리가 아니다. 그 대가로 안정성을 얻는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-merge-two',
        svg: svg({
            width: W, height: H,
            title: '정렬된 두 줄 합치기',
            desc: '두 줄의 맨 앞을 견주어 작은 쪽을 결과로 옮기고 그 줄의 손가락만 한 칸 나아간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-5. 힙 정렬 ---- */
add((() => {
    const W = 782, H = 376;
    const g = [];
    g.push(txt(W / 2, 24, '뿌리를 맨 뒤와 바꾸고 힙을 하나 줄인다 — 정렬된 구간이 뒤에서 자란다', { anchor: 'middle', cls: 'ink bold' }));
    const N = {
        a: [170, 84, '16'], b: [110, 148, '4'], c: [230, 148, '9'],
        d: [78, 212, '2'], e: [142, 212, '1'], f: [198, 212, '3'],
    };
    g.push(graph(N, [['a', 'b'], ['a', 'c'], ['b', 'd'], ['b', 'e'], ['c', 'f']]));
    g.push(gnode(170, 84, '16', { col: C2 }));
    g.push(gnode(198, 212, '3', { col: C1 }));
    g.push(curvePath('M156 70 C 96 34, 268 250, 210 226', { stroke: C2, sw: 1.5, dash: '5 4', marker: 'ar2' }));
    g.push(txt(20, 262, '6장에서 만든 최대 힙 [16 4 9 2 1 3]', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 280, '뿌리 16 과 맨 뒤 3 을 바꾸면 16 은 제자리가 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 298, '남은 다섯 개에서 3 을 내리면 다시 힙이다', { cls: 'ink2', size: 'sm' }));

    const rows = [
        ['힙 완성', [16, 4, 9, 2, 1, 3], 6],
        ['1회', [9, 4, 3, 2, 1, 16], 5],
        ['2회', [4, 2, 3, 1, 9, 16], 4],
        ['3회', [3, 2, 1, 4, 9, 16], 3],
        ['4회', [2, 1, 3, 4, 9, 16], 2],
        ['5회', [1, 2, 3, 4, 9, 16], 1],
    ];
    rows.forEach((r, k) => {
        const y = 66 + k * 40;
        g.push(txt(444, y + 18, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        const hl = {};
        for (let i = r[2]; i < 6; i += 1) hl[i] = C3;
        g.push(cells(456, y, 34, 26, r[1], { hl, small: true }));
    });
    g.push(txt(676, 58, '정렬 완료 구간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '힙 만들기 O(n) 에 꺼내기 n 번을 더한다. 꺼낼 때마다 내리기가 O(log n) 이므로 전체 O(n log n) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '배열 한 개 안에서 앞은 힙, 뒤는 정렬 완료로 쓰므로 추가 칸이 상수다 — 제자리 정렬이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-heapsort-steps',
        svg: svg({
            width: W, height: H,
            title: '힙 정렬을 한 단계씩',
            desc: '뿌리와 맨 뒤를 바꾸고 힙 크기를 하나 줄이며 내리기를 되풀이한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-6. 퀵 정렬의 분할 ---- */
add((() => {
    const W = 792, H = 404;
    const g = [];
    g.push(txt(W / 2, 24, '한 번 훑으면서 피벗보다 작은 것을 왼쪽으로 몰아 놓는다', { anchor: 'middle', cls: 'ink bold' }));
    const frames = [
        { lab: '시작', arr: [3, 7, 8, 5, 2, 1, 9, 4], b: 0, s: 0, note: '피벗은 맨 뒤 4. 경계는 0 에서 출발' },
        { lab: '자리 = 4', arr: [3, 2, 8, 5, 7, 1, 9, 4], b: 2, s: 4, note: '2 < 4 → 경계 자리와 교환하고 경계를 한 칸' },
        { lab: '자리 = 5', arr: [3, 2, 1, 5, 7, 8, 9, 4], b: 3, s: 5, note: '1 < 4 → 또 교환. 7 8 9 는 건드리지 않는다' },
        { lab: '마지막', arr: [3, 2, 1, 4, 7, 8, 9, 5], b: 3, s: -1, note: '피벗을 경계 자리로 옮긴다 — 피벗이 제자리다' },
    ];
    frames.forEach((f, k) => {
        const y = 76 + k * 76;
        g.push(txt(150, y + 18, f.lab, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        const hl = {};
        if (k < 3) hl[7] = C3;
        if (k === 3) hl[f.b] = C3;
        for (let i = 0; i < f.b; i += 1) hl[i] = C1;
        if (k === 3) for (let i = 0; i < 3; i += 1) hl[i] = C1;
        g.push(cells(162, y, 34, 28, f.arr, { hl, small: true }));
        if (k < 3) {
            g.push(mark(162 + f.b * 34 + 17, y + 28, '경계', { col: C1, up: false }));
            g.push(mark(162 + f.s * 34 + 17, y, '자리', { col: C2 }));
        } else {
            g.push(mark(162 + f.b * 34 + 17, y + 28, '피벗', { col: C3, up: false }));
        }
        g.push(txt(452, y + 18, f.note, { cls: 'ink', size: 'sm' }));
    });
    g.push(ln([[162, 372], [264, 372]], { stroke: C1, sw: 2 }));
    g.push(txt(213, 388, '전부 4 보다 작다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[298, 372], [434, 372]], { stroke: CK, sw: 2 }));
    g.push(txt(366, 388, '전부 4 이상이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(452, 372, '이제 왼쪽 3개와 오른쪽 4개를 따로 정렬하면 끝이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(452, 390, '피벗은 다시 건드리지 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-quick-partition',
        svg: svg({
            width: W, height: H,
            title: '퀵 정렬의 분할',
            desc: '왼쪽에서 오른쪽으로 한 번 훑으며 피벗보다 작은 값을 경계 안쪽으로 옮긴다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-7. 결정 트리와 하한 ---- */
add((() => {
    const W = 792, H = 442;
    const g = [];
    g.push(txt(W / 2, 24, '비교 정렬은 예 · 아니오 질문의 나무다 — 잎이 n! 개면 나무가 그만큼 깊어야 한다', { anchor: 'middle', cls: 'ink bold' }));
    const q = (x, y, s) => box(x - 42, y - 16, 84, 32, { stroke: C1, sw: 1.5, rx: 16 }) + txt(x, y + 5, s, { anchor: 'middle', cls: 'ink', size: 'sm' });
    const leaf = (x, y, s) => box(x - 30, y - 15, 60, 30, { fill: C3, op: 0.18, stroke: C3, sw: 1.5, rx: 4 }) + txt(x, y + 5, s, { anchor: 'middle', cls: 'ink', size: 'sm' });
    const eg = (p1, p2, lab, side) => ln([p1, p2], { stroke: CK, sw: 1.2 })
        + txt((p1[0] + p2[0]) / 2 + (side === 'l' ? -14 : 14), (p1[1] + p2[1]) / 2 + 2, lab, { anchor: side === 'l' ? 'end' : 'start', cls: 'ink2', size: 'sm' });
    const R = [396, 66], L1 = [206, 148], L2 = [586, 148];
    const A1 = [116, 230], A2 = [296, 230], A3 = [496, 230], A4 = [676, 230];
    const B1 = [236, 312], B2 = [356, 312], B3 = [436, 312], B4 = [556, 312];
    g.push(eg([R[0] - 20, R[1] + 16], [L1[0] + 20, L1[1] - 16], '예', 'l'));
    g.push(eg([R[0] + 20, R[1] + 16], [L2[0] - 20, L2[1] - 16], '아니오', 'r'));
    g.push(eg([L1[0] - 20, L1[1] + 16], [A1[0] + 14, A1[1] - 15], '예', 'l'));
    g.push(eg([L1[0] + 20, L1[1] + 16], [A2[0] - 20, A2[1] - 16], '아니오', 'r'));
    g.push(eg([L2[0] - 20, L2[1] + 16], [A3[0] + 20, A3[1] - 16], '예', 'l'));
    g.push(eg([L2[0] + 20, L2[1] + 16], [A4[0] - 14, A4[1] - 15], '아니오', 'r'));
    g.push(eg([A2[0] - 16, A2[1] + 16], [B1[0] + 10, B1[1] - 15], '예', 'l'));
    g.push(eg([A2[0] + 16, A2[1] + 16], [B2[0] - 10, B2[1] - 15], '아니오', 'r'));
    g.push(eg([A3[0] - 16, A3[1] + 16], [B3[0] + 10, B3[1] - 15], '예', 'l'));
    g.push(eg([A3[0] + 16, A3[1] + 16], [B4[0] - 10, B4[1] - 15], '아니오', 'r'));
    g.push(q(R[0], R[1], 'a < b ?'));
    g.push(q(L1[0], L1[1], 'b < c ?'));
    g.push(q(L2[0], L2[1], 'b < c ?'));
    g.push(q(A2[0], A2[1], 'a < c ?'));
    g.push(q(A3[0], A3[1], 'a < c ?'));
    g.push(leaf(A1[0], A1[1], 'a b c'));
    g.push(leaf(A4[0], A4[1], 'c b a'));
    g.push(leaf(B1[0], B1[1], 'a c b'));
    g.push(leaf(B2[0], B2[1], 'c a b'));
    g.push(leaf(B3[0], B3[1], 'b a c'));
    g.push(leaf(B4[0], B4[1], 'b c a'));
    g.push(txt(56, 230, '잎 6개', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(56, 248, '= 3!', { cls: 'ink2', size: 'sm' }));
    g.push(txt(772, 312, '높이 3', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(772, 330, '견주기 최대 3번', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 62, '원소 세 개의 순서는 3! = 6 가지다. 어떤 비교 정렬도 이 여섯 가지를 모두 구분해야 하므로 잎이 6 개 이상이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 42, '높이 h 인 이진트리의 잎은 많아야 2^h 개이므로 2^h ≥ n! 이고, 양변에 로그를 취하면 h ≥ log₂(n!) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 22, 'n! 의 뒤쪽 절반만 남겨도 n! ≥ (n/2)^(n/2) 이므로 h ≥ (n/2)·log₂(n/2), 곧 h = Ω(n log n) 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 2, '최악의 입력은 가장 깊은 잎으로 가는 길이다. 그러니 어떤 비교 정렬도 그 깊이만큼은 견주어야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-decision-tree',
        svg: svg({
            width: W, height: H,
            title: '비교 정렬의 결정 트리',
            desc: '원소 세 개를 정렬하는 모든 비교의 갈림길을 나무로 그리면 잎이 3! 개이고 높이가 3 이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-8. 계수 정렬과 기수 정렬 ---- */
add((() => {
    const W = 800, H = 384;
    const g = [];
    g.push(txt(W / 2, 24, '견주지 않고 세거나 나눠 담는다 — 그래서 n log n 벽 아래로 내려간다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 386, 268, '계수 정렬 — 값을 첨자로 쓴다', '값의 범위가 0 부터 5 까지인 것을 안다'));
    g.push(txt(28, 96, '입력', { cls: 'ink2', size: 'sm' }));
    g.push(cells(74, 80, 30, 26, [2, 5, 3, 0, 2, 3, 0, 3], { small: true }));
    g.push(txt(28, 152, '개수', { cls: 'ink2', size: 'sm' }));
    g.push(cells(74, 136, 40, 26, [2, 0, 2, 3, 0, 1], { hl: { 0: C2, 2: C2, 3: C2, 5: C2 }, idx: [0, 1, 2, 3, 4, 5], small: true }));
    g.push(txt(324, 152, '값', { cls: 'ink2', size: 'sm' }));
    g.push(txt(324, 174, '첨자', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 226, '결과', { cls: 'ink2', size: 'sm' }));
    g.push(cells(74, 210, 30, 26, [0, 0, 2, 2, 3, 3, 3, 5], { hl: { 0: C3, 1: C3, 2: C3, 3: C3, 4: C3, 5: C3, 6: C3, 7: C3 }, small: true }));
    g.push(txt(28, 264, '개수를 앞에서부터 누적하면 각 값이 결과의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 282, '몇 번째 칸부터 들어갈지가 정해진다. O(n + k)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 300, '값의 범위 k 가 크면 개수 배열이 그만큼 커진다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(412, 42, 374, 268, '기수 정렬 — 낮은 자리부터 안정 정렬', '자릿수 3 이면 세 번 훑는다'));
    const rr = [
        ['입력', [329, 457, 657, 839, 436, 720, 355], null],
        ['1의 자리', [720, 355, 436, 457, 657, 329, 839], 2],
        ['10의 자리', [720, 329, 436, 839, 355, 457, 657], 1],
        ['100의 자리', [329, 355, 436, 457, 657, 720, 839], 0],
    ];
    rr.forEach((r, k) => {
        const y = 82 + k * 52;
        g.push(txt(494, y + 17, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        r[1].forEach((v, i) => {
            const cx = 502 + i * 40;
            const done = k === 3;
            g.push(box(cx, y, 36, 26, { fill: done ? C3 : 'none', op: done ? 0.18 : 1, stroke: done ? C3 : CK, sw: done ? 1.6 : 1.2, rx: 2 }));
            g.push(txt(cx + 18, y + 18, String(v), { anchor: 'middle', cls: 'ink', size: 'sm' }));
            if (r[2] !== null) g.push(txt(cx + 18, y + 40, String(v).charAt(r[2]), { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
        });
    });
    g.push(txt(426, 302, '아래 작은 숫자가 그 단계에서 본 자릿수다. 오름차순으로 놓여 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '두 방법 모두 값을 견주지 않고 값 자체를 자리로 쓴다. 그래서 비교 정렬의 하한이 적용되지 않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '대가는 조건이다. 키가 정수여야 하고 범위나 자릿수를 미리 알아야 하며 대개 추가 배열이 필요하다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-counting-radix',
        svg: svg({
            width: W, height: H,
            title: '계수 정렬과 기수 정렬',
            desc: '계수 정렬은 값을 첨자로 써서 개수를 세고, 기수 정렬은 낮은 자리부터 안정 정렬을 되풀이한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-9. 선택 문제 — 한쪽만 파고든다 ---- */
add((() => {
    const W = 790, H = 438;
    const g = [];
    g.push(txt(W / 2, 24, '정렬은 양쪽을 다 파지만 선택은 한쪽만 판다 — 그 차이가 n log n 과 n 이다', { anchor: 'middle', cls: 'ink bold' }));
    const arr = [3, 2, 1, 4, 7, 8, 9, 5, 6, 11, 10, 12];
    g.push(txt(20, 50, '분할이 끝나면 피벗의 자리가 정해진다. 그 자리가 찾는 k 보다 크면 오른쪽은 볼 필요가 없다', { cls: 'ink2', size: 'sm' }));
    const hl = { 3: C3 };
    for (let i = 0; i < 3; i += 1) hl[i] = C1;
    for (let i = 4; i < 12; i += 1) hl[i] = CG;
    g.push(cells(178, 68, 34, 28, arr, { hl, idx: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], small: true }));
    g.push(txt(172, 86, '값', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(172, 110, '몇 번째', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 86, '피벗 4 는 4번째로 작다', { cls: 'ink', size: 'sm' }));
    g.push(txt(228, 134, 'k = 3 이면 여기만', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(450, 134, '통째로 버린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(14, 152, 380, 210, '평균 — 반씩 줄어든다', '되풀이할 때마다 볼 것이 절반이 된다'));
    const bars = [[12, 'n'], [6, 'n/2'], [3, 'n/4'], [1.5, 'n/8']];
    bars.forEach((b, k) => {
        const y = 204 + k * 32;
        g.push(box(120, y, b[0] * 18, 20, { fill: C1, op: 0.28, stroke: C1, sw: 1.3, rx: 2 }));
        g.push(txt(112, y + 15, b[1], { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(28, 350, 'n + n/2 + n/4 + … < 2n → O(n)', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(406, 152, 380, 210, '최악 — 하나씩만 줄어든다', '피벗이 매번 가장 작거나 가장 큰 값일 때'));
    [[12, 'n'], [11, 'n−1'], [10, 'n−2'], [9, 'n−3']].forEach((b, k) => {
        const y = 204 + k * 32;
        g.push(box(512, y, b[0] * 18, 20, { fill: C2, op: 0.28, stroke: C2, sw: 1.3, rx: 2 }));
        g.push(txt(504, y + 15, b[1], { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(420, 350, 'n + (n−1) + … ≈ n²/2 → O(n²)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, H - 44, '퀵 정렬이라면 양쪽을 다 정렬해야 하므로 층마다 n 이 들고 층이 log n 개다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '피벗을 무작위로 고르면 평균이 O(n) 이고 최악은 여전히 O(n²) 이다. 확률이 아주 작을 뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 4, '최악까지 O(n) 으로 묶으려면 피벗을 중앙값의 중앙값으로 고른다. 상수가 커서 실무에서는 드물다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-quickselect',
        svg: svg({
            width: W, height: H,
            title: '선택 문제와 quickselect',
            desc: '분할 뒤 한쪽만 파고들면 볼 양이 등비수열로 줄어 합이 2n 을 넘지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8장 — 그래프: 표현과 탐색
 * ================================================================== */

/* ---- 8-1. 그래프의 말 ---- */
add((() => {
    const W = 792, H = 348;
    const g = [];
    g.push(txt(W / 2, 24, '이 그림 하나에 이 장의 용어가 거의 다 들어 있다', { anchor: 'middle', cls: 'ink bold' }));
    const N = {
        v1: [96, 132, 'v1'], v2: [204, 88, 'v2'], v3: [204, 200, 'v3'],
        v4: [316, 112, 'v4'], v5: [316, 232, 'v5'],
        v6: [420, 120, 'v6'], v7: [420, 216, 'v7'],
    };
    const cyc = { stroke: C2, sw: 2.4 };
    g.push(graph(N, [
        ['v1', 'v2', cyc], ['v2', 'v3', cyc], ['v1', 'v3', cyc],
        ['v2', 'v4'], ['v3', 'v5'], ['v6', 'v7'],
    ], { r: 17 }));
    g.push(box(60, 62, 300, 208, { stroke: CG, sw: 1, dash: '6 5', rx: 10 }));
    g.push(box(384, 84, 74, 170, { stroke: CG, sw: 1, dash: '6 5', rx: 10 }));
    g.push(txt(210, 288, '연결 요소 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(421, 272, '연결 요소 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(150, 60, '주황 삼각형이 순환이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const lines = [
        ['정점 (vertex)', '동그라미 7개. 집합 V'],
        ['간선 (edge)', '선 6개. 집합 E. 이 그래프는 G = (V, E)'],
        ['차수 (degree)', 'v2 는 3, v4 와 v5 는 1, 붙은 간선 수'],
        ['경로 (path)', 'v1 → v2 → v4. 같은 정점을 두 번 안 지나면 단순 경로'],
        ['순환 (cycle)', 'v1 → v2 → v3 → v1. 출발점으로 돌아온다'],
        ['연결 요소', '서로 오갈 수 있는 덩어리. 여기서는 2개'],
    ];
    lines.forEach((l, i) => {
        const y = 74 + i * 38;
        g.push(txt(482, y, l[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(482, y + 17, l[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(20, H - 24, '간선 수는 정점 수와 무관하게 정해진다. 정점이 n 개면 간선은 0 개부터 n(n−1)/2 개까지 가능하다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '연결 요소가 2개라는 것은 v4 에서 v6 으로 가는 길이 아예 없다는 뜻이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-graph-terms',
        svg: svg({
            width: W, height: H,
            title: '그래프의 기본 용어',
            desc: '정점 간선 차수 경로 순환 연결 요소를 한 그림에 표시했다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-2. 그래프의 종류 ---- */
add((() => {
    const W = 792, H = 404;
    const g = [];
    g.push(txt(W / 2, 24, '간선에 무엇을 얹느냐로 갈린다 — 방향, 가중치, 그리고 순환의 유무', { anchor: 'middle', cls: 'ink bold' }));
    const PW = 252, PH = 164;
    const spot = (px0, py0) => ({
        p: [px0 + 62, py0 + 74], q: [px0 + 126, py0 + 52],
        r: [px0 + 190, py0 + 74], s: [px0 + 126, py0 + 126],
    });
    const mk = (px0, py0, title, sub, edges, opt) => {
        const S = spot(px0, py0);
        const N = { p: [...S.p, 'a'], q: [...S.q, 'b'], r: [...S.r, 'c'], s: [...S.s, 'd'] };
        return panel(px0, py0, PW, PH, title, sub) + graph(N, edges, { r: 14, ...(opt || {}) });
    };
    const E4 = [['p', 'q'], ['q', 'r'], ['p', 's'], ['s', 'r']];
    g.push(mk(14, 40, '무방향 그래프', '간선에 방향이 없다', E4));
    g.push(mk(270, 40, '방향 그래프', 'a 의 진출차수 2, c 의 진입차수 2', E4, { dir: true }));
    g.push(mk(526, 40, '가중 그래프', '간선마다 값이 붙는다', [
        ['p', 'q', { w: 4 }], ['q', 'r', { w: 7 }], ['p', 's', { w: 2 }], ['s', 'r', { w: 5 }]]));
    g.push(mk(14, 216, 'DAG', '방향이 있고 순환이 없다', [
        ['p', 'q', { dir: true }], ['q', 'r', { dir: true }], ['p', 's', { dir: true }], ['s', 'r', { dir: true }]]));
    g.push(mk(270, 216, '트리', '연결 · 순환 없음 · 간선 n−1 개', [['p', 'q'], ['q', 'r'], ['q', 's']]));
    g.push(mk(526, 216, '완전 그래프', '가능한 간선을 다 그은 것. 6개', [
        ['p', 'q'], ['q', 'r'], ['p', 's'], ['s', 'r'], ['p', 'r'], ['q', 's']]));
    g.push(txt(20, H - 24, '트리는 그래프의 특별한 경우다. 정점이 n 개인 무방향 그래프가 연결되어 있고 간선이 n−1 개면 반드시 트리다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '정점 n 개의 간선 수는 많아야 n(n−1)/2 개다. 그 수에 가까우면 밀집, 훨씬 적으면 희소 그래프라 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-graph-kinds',
        svg: svg({
            width: W, height: H,
            title: '그래프의 종류',
            desc: '무방향 방향 가중 DAG 트리 완전 그래프를 같은 네 정점 위에 그렸다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-3. 인접 행렬과 인접 리스트 ---- */
add((() => {
    const W = 800, H = 386;
    const g = [];
    g.push(txt(W / 2, 24, '같은 그래프를 두 가지로 적는다 — 무엇이 싸지고 무엇이 비싸지는가', { anchor: 'middle', cls: 'ink bold' }));
    const N = { a: [86, 96, '1'], b: [180, 60, '2'], c: [180, 152, '3'], d: [274, 106, '4'], e: [274, 210, '5'] };
    g.push(graph(N, [['a', 'b'], ['a', 'c'], ['b', 'd'], ['c', 'd'], ['d', 'e']], { r: 16 }));
    g.push(txt(30, 60, '그래프', { cls: 'ink bold', size: 'sm' }));

    // 인접 행렬
    const M = [[0, 1, 1, 0, 0], [1, 0, 0, 1, 0], [1, 0, 0, 1, 0], [0, 1, 1, 0, 1], [0, 0, 0, 1, 0]];
    g.push(txt(348, 60, '인접 행렬', { cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < 5; i += 1) {
        g.push(txt(384 + i * 30, 84, String(i + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(360, 110 + i * 28, String(i + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(cells(369, 92 + i * 28, 30, 28, M[i], {
            hl: Object.fromEntries(M[i].map((v, j) => [j, v ? C1 : undefined]).filter(e => e[1])), small: true,
        }));
    }
    g.push(txt(348, 258, '칸 5 × 5 = 25 개. 간선이 5 개뿐인데도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(348, 276, '25 칸을 늘 들고 있어야 한다', { cls: 'ink2', size: 'sm' }));

    // 인접 리스트
    g.push(txt(556, 60, '인접 리스트', { cls: 'ink bold', size: 'sm' }));
    const L = [[2, 3], [1, 4], [1, 4], [2, 3, 5], [4]];
    L.forEach((row, i) => {
        const y = 92 + i * 28;
        g.push(box(556, y, 28, 24, { stroke: CK, sw: 1.2, rx: 2 }));
        g.push(txt(570, y + 17, String(i + 1), { anchor: 'middle', cls: 'ink', size: 'sm' }));
        row.forEach((v, j) => {
            const x = 600 + j * 44;
            g.push(arw(x - 16, y + 12, x - 2, y + 12, { cls: 'ark', width: 1.2 }));
            g.push(box(x, y, 28, 24, { fill: C3, op: 0.18, stroke: C3, sw: 1.4, rx: 2 }));
            g.push(txt(x + 14, y + 17, String(v), { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
    });
    g.push(txt(556, 258, '칸이 정점 5 개 + 간선 방향 10 개 = 15 개.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 276, '간선이 늘어야 칸도 는다', { cls: 'ink2', size: 'sm' }));

    const rows = [
        ['하는 일', '인접 행렬', '인접 리스트'],
        ['공간', 'V² 칸 (고정)', 'V + 2E 칸'],
        ['1과 4가 이어졌나', 'O(1) — 칸 하나를 본다', 'O(차수) — 목록을 훑는다'],
        ['4의 이웃을 전부', 'O(V) — 한 줄을 다 본다', 'O(차수) — 목록 길이만큼'],
    ];
    rows.forEach((r, i) => {
        const y = 306 + i * 18;
        const bold = i === 0;
        g.push(txt(24, y, r[0], { cls: bold ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(200, y, r[1], { cls: bold ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(450, y, r[2], { cls: bold ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(24, H - 6, '희소 그래프(E 가 V 에 가까움)면 인접 리스트, 밀집 그래프거나 간선 확인이 잦으면 인접 행렬이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-graph-repr',
        svg: svg({
            width: W, height: H,
            title: '인접 행렬과 인접 리스트',
            desc: '같은 그래프를 행렬과 목록으로 적고 공간과 두 가지 질의 비용을 견준다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-4. 스택과 큐의 차이가 곧 DFS 와 BFS 의 차이 ---- */
add((() => {
    const W = 802, H = 418;
    const g = [];
    g.push(txt(W / 2, 24, '코드는 한 글자도 다르지 않다 — 프런티어가 스택이냐 큐냐만 다르다', { anchor: 'middle', cls: 'ink bold' }));
    const CX = 158, CY = 182, R = 84;
    const P = {
        A: [CX, CY - R], B: [CX + 76, CY - 44], D: [CX + 76, CY + 44],
        F: [CX, CY + R], E: [CX - 76, CY + 44], C: [CX - 76, CY - 44],
    };
    const N = Object.fromEntries(Object.entries(P).map(([k, v]) => [k, [v[0], v[1], k]]));
    g.push(graph(N, [['A', 'B'], ['B', 'D'], ['D', 'F'], ['F', 'E'], ['E', 'C'], ['C', 'A']], { r: 17 }));
    const ord = { A: [1, 1], B: [6, 2], C: [2, 3], D: [5, 4], E: [3, 5], F: [4, 6] };
    for (const k of Object.keys(P)) {
        const [x, y] = P[k];
        const ux = (x - CX) / R, uy = (y - CY) / R;
        const lx = x + ux * 34, ly = y + uy * 30;
        g.push(txt(lx, ly - 3, String(ord[k][0]), { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(lx, ly + 12, String(ord[k][1]), { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    }
    g.push(txt(30, 62, '깊이 우선 방문 순서', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(30, 80, '너비 우선 방문 순서', { cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(24, 330, '여섯 정점이 고리로 이어져 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 348, '깊이 우선은 한쪽으로 끝까지 돌고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 366, '너비 우선은 양쪽으로 동시에 퍼진다', { cls: 'ink2', size: 'sm' }));

    const dfs = [['—', ['A']], ['A', ['B', 'C']], ['C', ['B', 'E']], ['E', ['B', 'F']], ['F', ['B', 'D']], ['D', ['B']], ['B', []]];
    const bfs = [['—', ['A']], ['A', ['B', 'C']], ['B', ['C', 'D']], ['C', ['D', 'E']], ['D', ['E', 'F']], ['E', ['F']], ['F', []]];
    const col = (x0, title, sub, data, isStack) => {
        const out = [txt(x0 + 108, 58, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }),
            txt(x0 + 108, 74, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' })];
        out.push(txt(x0 + 34, 94, '꺼낸 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(x0 + 132, 94, '남아 있는 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        data.forEach((d, k) => {
            const y = 104 + k * 32;
            g.push('');
            out.push(box(x0 + 16, y, 34, 26, { fill: d[0] === '—' ? 'none' : (isStack ? C2 : C1), op: d[0] === '—' ? 1 : 0.2, stroke: d[0] === '—' ? CG : (isStack ? C2 : C1), sw: 1.4, rx: 3 }));
            out.push(txt(x0 + 33, y + 18, d[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(box(x0 + 76, y, 116, 26, { stroke: CG, sw: 1, rx: 3 }));
            d[1].forEach((v, i) => {
                out.push(box(x0 + 82 + i * 32, y + 3, 28, 20, { fill: C3, op: 0.18, stroke: C3, sw: 1.3, rx: 2 }));
                out.push(txt(x0 + 96 + i * 32, y + 18, v, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            });
        });
        return out.join('');
    };
    g.push(col(320, '스택 — 깊이 우선', '나중에 넣은 것을 먼저 꺼낸다', dfs, true));
    g.push(col(560, '큐 — 너비 우선', '먼저 넣은 것을 먼저 꺼낸다', bfs, false));
    g.push(arw(462, 336, 486, 336, { cls: 's2', width: 1.4 }));
    g.push(arw(486, 350, 462, 350, { cls: 's2', width: 1.4 }));
    g.push(txt(428, 372, '오른쪽 끝에서만 넣고 뺀다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(660, 342, 640, 342, { cls: 's1', width: 1.4 }));
    g.push(arw(700, 342, 724, 342, { cls: 's1', width: 1.4 }));
    g.push(txt(668, 372, '왼쪽에서 빼고 오른쪽에 넣는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '꺼낸 것 칸을 위에서 아래로 읽으면 그것이 방문 순서다. 스택은 A C E F D B, 큐는 A B C D E F 다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '두 알고리즘의 차이는 여기서 끝난다. 4장의 스택과 큐가 그대로 두 탐색이 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-stack-vs-queue',
        svg: svg({
            width: W, height: H,
            title: '스택과 큐가 곧 DFS 와 BFS 다',
            desc: '같은 그래프에서 프런티어를 스택으로 쓰면 깊이 우선, 큐로 쓰면 너비 우선이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-5. 탐색 트리와 간선 분류 ---- */
add((() => {
    const W = 792, H = 366;
    const g = [];
    g.push(txt(W / 2, 24, '탐색이 지나간 간선이 트리를 이루고 나머지는 셋 중 하나로 분류된다', { anchor: 'middle', cls: 'ink bold' }));
    const N = { u: [104, 106, 'u'], v: [242, 106, 'v'], w: [392, 106, 'w'], x: [104, 236, 'x'], y: [242, 236, 'y'], z: [392, 236, 'z'] };
    const T = { stroke: CI, sw: 2.4, dir: true, mk: 'ark' };
    g.push(graph(N, [
        ['u', 'v', T], ['v', 'y', T], ['y', 'x', T], ['w', 'z', T],
        ['x', 'v', { stroke: C2, sw: 2, dir: true, mk: 'ar2' }],
        ['u', 'x', { stroke: C1, sw: 2, dir: true, mk: 'ar1' }],
        ['w', 'y', { stroke: C3, sw: 2, dir: true, mk: 'ar3' }],
    ], { r: 18 }));
    g.push(curvePath('M410 227 C 476 200, 476 142, 410 115', { stroke: C2, sw: 2, marker: 'ar2' }));
    const times = { u: '1 / 8', v: '2 / 7', y: '3 / 6', x: '4 / 5', w: '9 / 12', z: '10 / 11' };
    for (const k of Object.keys(times)) {
        const [x, y] = N[k];
        g.push(txt(x, y + 36, times[k], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(104, 62, '발견 시각 / 종료 시각', { cls: 'ink2', size: 'sm' }));
    const legend = [
        ['트리 간선', CI, '탐색이 실제로 지나간 간선. 이것만 모으면 탐색 트리다'],
        ['뒤 간선 (back)', C2, '조상으로 되돌아간다. 이것이 있으면 순환이 있다'],
        ['앞 간선 (forward)', C1, '이미 끝난 자손으로 건너뛴다'],
        ['교차 간선 (cross)', C3, '조상도 자손도 아닌 곳으로 간다'],
    ];
    legend.forEach((l, i) => {
        const y = 76 + i * 40;
        g.push(ln([[478, y - 4], [512, y - 4]], { stroke: l[1], sw: 2.6 }));
        g.push(txt(520, y, l[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(478, y + 17, l[2], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(20, H - 42, '분류 기준은 간선을 따라갈 때 상대 정점의 상태다. 처음 보면 트리, 아직 안 끝났으면 뒤, 이미 끝났으면 앞이나 교차다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '무방향 그래프에는 트리 간선과 뒤 간선만 생긴다. 앞 간선과 교차 간선은 방향이 있어야 나온다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '뒤 간선이 하나라도 있으면 순환이 있고, 하나도 없으면 없다 — 순환 탐지가 이 한 줄이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-edge-classify',
        svg: svg({
            width: W, height: H,
            title: '깊이 우선 탐색의 간선 분류',
            desc: '트리 간선 · 뒤 간선 · 앞 간선 · 교차 간선을 발견 시각과 종료 시각으로 구분한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-6. 이분 그래프 판정 ---- */
add((() => {
    const W = 782, H = 336;
    const g = [];
    g.push(txt(W / 2, 24, '너비 우선으로 층을 매기고 층의 홀짝으로 칠한다 — 같은 색끼리 이어지면 실패다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 374, 244, '짝수 길이 순환 — 이분 그래프다', '층 0 · 2 는 파랑, 층 1 · 3 은 주황. 성공'));
    const CX = 200, CY = 182, R = 66;
    const P = { a: [CX, CY - R], b: [CX + 57, CY - 33], c: [CX + 57, CY + 33], d: [CX, CY + R], e: [CX - 57, CY + 33], f: [CX - 57, CY - 33] };
    const colr = { a: C1, b: C2, c: C1, d: C2, e: C1, f: C2 };
    const lvl = { a: '0', b: '1', c: '2', d: '3', e: '2', f: '1' };
    const N1 = Object.fromEntries(Object.entries(P).map(([k, v]) => [k, [v[0], v[1], k, { col: colr[k] }]]));
    g.push(graph(N1, [['a', 'b'], ['b', 'c'], ['c', 'd'], ['d', 'e'], ['e', 'f'], ['f', 'a']], { r: 16 }));
    for (const k of Object.keys(P)) {
        const [x, y] = P[k];
        const right = x >= CX;
        g.push(txt(x + (right ? 36 : -36), y + 4, `층 ${lvl[k]}`, { anchor: right ? 'start' : 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(30, 274, '모든 간선이 파랑과 주황을 잇는다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(404, 42, 364, 244, '홀수 길이 순환 — 이분 그래프가 아니다', '같은 층 두 정점이 이어져 있다'));
    const Q = { p: [586, 108], q: [522, 216], r: [650, 216] };
    const N2 = { p: [...Q.p, 'p', { col: C1 }], q: [...Q.q, 'q', { col: C2 }], r: [...Q.r, 'r', { col: C2 }] };
    g.push(graph(N2, [['p', 'q'], ['p', 'r'], ['q', 'r', { stroke: C2, sw: 2.6 }]], { r: 16 }));
    g.push(txt(586, 92, '층 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(496, 222, '층 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(678, 222, '층 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(586, 252, '굵은 간선이 같은 층끼리 잇는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(420, 274, 'q 와 r 을 다른 색으로 칠할 방법이 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '판정은 너비 우선 탐색 한 번이면 끝난다. 간선을 볼 때마다 두 끝의 층이 홀짝이 다른지 확인하면 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '‘이분 그래프다’ 와 ‘홀수 길이 순환이 없다’ 는 정확히 같은 말이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-bipartite',
        svg: svg({
            width: W, height: H,
            title: '이분 그래프 판정',
            desc: '너비 우선 탐색의 층을 홀짝으로 칠하고 같은 색을 잇는 간선이 있는지 본다',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-7. 위상 정렬 두 방법 ---- */
add((() => {
    const W = 800, H = 426;
    const g = [];
    g.push(txt(W / 2, 24, '선후관계를 간선으로 적고 순서를 뽑는다 — 방법은 둘이고 답은 여럿이다', { anchor: 'middle', cls: 'ink bold' }));
    const NP = { a: [70, 122, 'a'], b: [70, 216, 'b'], c: [186, 122, 'c'], d: [186, 216, 'd'], e: [300, 168, 'e'] };
    const EE = [['a', 'c'], ['b', 'c'], ['b', 'd'], ['c', 'd'], ['c', 'e'], ['d', 'e']];
    g.push(panel(14, 42, 366, 350, '방법 1 — 진입차수가 0 인 것부터 꺼낸다', '노드 위 작은 숫자가 진입차수다'));
    g.push(graph(NP, EE.map(e => [e[0], e[1], { dir: true }]), { r: 17 }));
    const deg0 = { a: 0, b: 0, c: 2, d: 2, e: 2 };
    for (const k of Object.keys(NP)) g.push(txt(NP[k][0], NP[k][1] - 26, String(deg0[k]), { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    const steps = [
        ['시작', 'a b', '—'],
        ['a 꺼냄', 'b', 'c 가 1 로'],
        ['b 꺼냄', 'c', 'c 가 0, d 가 1 로'],
        ['c 꺼냄', 'd', 'd 가 0, e 가 1 로'],
        ['d 꺼냄', 'e', 'e 가 0 으로'],
        ['e 꺼냄', '(빔)', '끝'],
    ];
    g.push(txt(30, 262, '단계', { cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(118, 262, '큐', { cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(196, 262, '줄어든 진입차수', { cls: 'ink2 bold', size: 'sm' }));
    steps.forEach((s, i) => {
        const y = 282 + i * 18;
        g.push(txt(30, y, s[0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(118, y, s[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(196, y, s[2], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(30, 384, '결과: a b c d e', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(396, 42, 390, 350, '방법 2 — 깊이 우선의 종료 순서를 뒤집는다', '노드 위 작은 숫자가 종료 순서다'));
    const NQ = { a: [452, 122, 'a'], b: [452, 216, 'b'], c: [568, 122, 'c'], d: [568, 216, 'd'], e: [682, 168, 'e'] };
    g.push(graph(NQ, EE.map(e => [e[0], e[1], { dir: true }]), { r: 17 }));
    const fin = { a: 4, b: 5, c: 3, d: 2, e: 1 };
    for (const k of Object.keys(NQ)) g.push(txt(NQ[k][0], NQ[k][1] - 26, String(fin[k]), { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(412, 262, '끝난 차례로 적으면', { cls: 'ink2', size: 'sm' }));
    g.push(cells(412, 274, 44, 26, ['e', 'd', 'c', 'a', 'b'], { hl: { 0: CG, 1: CG, 2: CG, 3: CG, 4: CG }, small: true }));
    g.push(txt(412, 328, '뒤집으면', { cls: 'ink2', size: 'sm' }));
    g.push(cells(412, 340, 44, 26, ['b', 'a', 'c', 'd', 'e'], { hl: { 0: C3, 1: C3, 2: C3, 3: C3, 4: C3 }, small: true }));
    g.push(txt(412, 384, '결과: b a c d e — 앞의 답과 다르지만 둘 다 옳다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, H - 6, '순환이 있으면 방법 1 은 큐가 먼저 비고 방법 2 는 뒤 간선을 만난다. 그래서 순환 탐지가 덤으로 따라온다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-toposort',
        svg: svg({
            width: W, height: H,
            title: '위상 정렬 두 가지 방법',
            desc: '진입차수가 0 인 정점을 꺼내는 방법과 깊이 우선 종료 순서를 뒤집는 방법',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-8. 양방향 탐색 ---- */
add((() => {
    const W = 772, H = 356;
    const g = [];
    g.push(txt(W / 2, 24, '반지름 d 짜리 공 하나보다 반지름 d/2 짜리 공 두 개가 훨씬 작다', { anchor: 'middle', cls: 'ink bold' }));
    const circ = (cx, cy, r, col) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${col}" fill-opacity="0.16" stroke="${col}" stroke-width="1.6" stroke-dasharray="5 4"/>`;
    g.push(panel(14, 42, 366, 232, '한쪽에서만 넓히기', '거리 d 까지 전부 훑는다'));
    g.push(circ(168, 178, 70, C1));
    g.push(gnode(168, 178, 'S', { r: 15, col: C1 }));
    g.push(gnode(238, 178, 'T', { r: 15, col: C2 }));
    g.push(ln([[168, 178], [238, 178]], { stroke: CK, sw: 1.2, dash: '4 4' }));
    g.push(txt(194, 158, '반지름 d', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 266, '훑는 정점 수는 대략 b 의 d 제곱', { cls: 'ink2', size: 'sm' }));

    g.push(panel(396, 42, 366, 232, '양쪽에서 동시에 넓히기', '가운데서 만나면 멈춘다'));
    g.push(circ(538, 178, 42, C1));
    g.push(circ(622, 178, 42, C2));
    g.push(gnode(538, 178, 'S', { r: 15, col: C1 }));
    g.push(gnode(622, 178, 'T', { r: 15, col: C2 }));
    g.push(pdot(580, 178, CI, 4));
    g.push(txt(580, 118, '여기서 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[580, 126], [580, 168]], { stroke: CK, sw: 1, dash: '3 3' }));
    g.push(txt(412, 266, '훑는 정점 수는 대략 b 의 d/2 제곱의 두 배', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, H - 44, '갈래가 b = 10 이고 거리가 d = 6 이면 한쪽에서만 넓힐 때 10 의 6 제곱, 곧 100만 개다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '양쪽에서 넓히면 10 의 3 제곱을 두 번, 곧 2000 개다. 500 배 차이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '쓸 수 있는 조건이 있다. 도착점을 알아야 하고 간선을 거꾸로도 따라갈 수 있어야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-bidirectional',
        svg: svg({
            width: W, height: H,
            title: '양방향 탐색',
            desc: '한쪽에서 거리 d 까지 넓히는 대신 양쪽에서 d/2 까지 넓혀 가운데서 만난다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9장 — 최단경로와 최소신장트리
 * ================================================================== */

/* ---- 9-1. ‘가장 짧은’ 의 두 가지 뜻 ---- */
add((() => {
    const W = 764, H = 332;
    const g = [];
    g.push(txt(W / 2, 24, '간선 수로 재느냐 가중치 합으로 재느냐에 따라 답이 달라진다', { anchor: 'middle', cls: 'ink bold' }));
    const N = { S: [110, 118, 'S'], A: [268, 78, 'A'], B: [426, 78, 'B'], T: [584, 118, 'T'] };
    g.push(graph(N, [
        ['S', 'A', { w: 2, stroke: C2, sw: 2.6, dir: true, mk: 'ar2' }],
        ['A', 'B', { w: 3, stroke: C2, sw: 2.6, dir: true, mk: 'ar2' }],
        ['B', 'T', { w: 2, stroke: C2, sw: 2.6, dir: true, mk: 'ar2' }],
    ], { r: 19 }));
    g.push(curvePath('M124 134 C 240 226, 460 226, 570 134', { stroke: C1, sw: 2.6, marker: 'ar1' }));
    g.push(txt(347, 218, '10', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(ln([[30, 240], [58, 240]], { stroke: C1, sw: 2.8 }));
    g.push(txt(66, 244, '간선 수로 가장 짧은 길 — S 에서 T 로 바로. 간선 1개, 가중치 10', { cls: 'ink', size: 'sm' }));
    g.push(ln([[30, 266], [58, 266]], { stroke: C2, sw: 2.8 }));
    g.push(txt(66, 270, '가중치 합으로 가장 짧은 길 — S → A → B → T. 간선 3개, 가중치 2+3+2 = 7', { cls: 'ink', size: 'sm' }));
    g.push(txt(20, H - 44, '8장의 너비 우선 탐색은 앞의 답을 준다. 간선 하나를 한 걸음으로 세기 때문이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '이 장은 뒤의 답을 구한다. 걸음마다 값이 다르면 걸음 수를 세는 것이 뜻을 잃는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '가중치가 전부 같으면 두 답이 일치한다 — 너비 우선 탐색은 이 장의 특별한 경우다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-shortest-two-meanings',
        svg: svg({ width: W, height: H, title: '‘가장 짧은 길’ 의 두 가지 뜻',
            desc: '간선 수로 재면 직행이 짧고 가중치 합으로 재면 세 번 갈아타는 쪽이 짧다', body: g.join('') }),
    };
})());

/* ---- 9-2. 완화 ---- */
add((() => {
    const W = 764, H = 314;
    const g = [];
    g.push(txt(W / 2, 24, '이 장의 모든 알고리즘이 이 연산 하나를 되풀이한다', { anchor: 'middle', cls: 'ink bold' }));
    const draw = (px0, title, sub, du, dv, w, after, okCol) => {
        const out = [panel(px0, 42, 366, 200, title, sub)];
        const u = [px0 + 96, 158], v = [px0 + 262, 158];
        out.push(gedge(u, v, { r1: 22, rr: 22, stroke: okCol, sw: 2.4, dir: true, mk: okCol === C3 ? 'ar3' : 'ark' }));
        out.push(txt((u[0] + v[0]) / 2, 146, String(w), { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
        out.push(gnode(u[0], u[1], 'u', { r: 22 }));
        out.push(gnode(v[0], v[1], 'v', { r: 22, col: after !== null ? okCol : null }));
        out.push(txt(u[0], 122, `지금까지의 최단 ${du}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(v[0], 122, `지금까지의 최단 ${dv}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(px0 + 183, 204, `${du} + ${w} = ${du + w}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(px0 + 183, 224, after !== null ? `${du + w} < ${dv} 이므로 ${dv} 를 ${after} 로 고친다`
            : `${du + w} 는 ${dv} 보다 작지 않다. 그대로 둔다`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(draw(14, '완화가 일어나는 경우', 'u 를 거쳐 가면 더 짧다', 3, 9, 4, 7, C3));
    g.push(draw(396, '완화가 일어나지 않는 경우', 'u 를 거쳐 가면 더 멀다', 3, 5, 4, null, CK));
    g.push(txt(20, H - 50, '완화(relaxation) 란 ‘u 를 거쳐 가는 길이 지금 아는 길보다 짧으면 갈아탄다’ 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 32, '갈아탈 때 v 의 직전 정점을 u 로 함께 적어 두면, 끝난 뒤 거꾸로 따라가 경로를 복원할 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 14, '알고리즘마다 다른 것은 완화를 어떤 순서로 몇 번 하느냐뿐이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-relax',
        svg: svg({ width: W, height: H, title: '완화 — 더 짧은 길을 찾으면 갈아탄다',
            desc: 'u 까지의 거리에 간선 가중치를 더한 값이 v 의 현재 거리보다 작으면 갈아탄다', body: g.join('') }),
    };
})());

/* ---- 9-3. 다익스트라를 한 단계씩 ---- */
add((() => {
    const W = 800, H = 508;
    const g = [];
    g.push(txt(W / 2, 24, '가장 가까운 것부터 확정하고 그 이웃을 완화한다 — 여덟 단계를 표로', { anchor: 'middle', cls: 'ink bold' }));
    const N = { S: [78, 120, 'S'], A: [230, 72, 'A'], C: [230, 172, 'C'], B: [382, 72, 'B'], D: [382, 172, 'D'], E: [534, 120, 'E'] };
    g.push(graph(N, [
        ['S', 'A', { w: 4, dir: true }], ['S', 'C', { w: 2, dir: true }],
        ['C', 'A', { w: 1, dir: true, woff: 13 }], ['A', 'B', { w: 5, dir: true }],
        ['C', 'D', { w: 8, dir: true }], ['B', 'D', { w: 2, dir: true, woff: 13 }],
        ['B', 'E', { w: 6, dir: true }], ['D', 'E', { w: 3, dir: true }],
    ], { r: 19 }));
    g.push(txt(598, 86, '출발점은 S 다. 화살표 방향으로만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(598, 104, '갈 수 있고 숫자가 그 간선의 값이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(598, 130, '최종 답은 아래 표의 맨 마지막 줄', { cls: 'ink2', size: 'sm' }));
    g.push(txt(598, 148, '0 3 2 8 10 13 이다', { cls: 'ink2', size: 'sm' }));

    const cols = ['S', 'A', 'C', 'B', 'D', 'E'];
    const rows = [
        ['— (초기)', ['0', '∞', '∞', '∞', '∞', '∞'], [1, 2, 3, 4, 5], -1, '(S,0)'],
        ['S 확정 (0)', ['0', '4', '2', '∞', '∞', '∞'], [1, 2], 0, '(C,2) (A,4)'],
        ['C 확정 (2)', ['0', '3', '2', '∞', '10', '∞'], [1, 4], 2, '(A,3) (A,4) (D,10)'],
        ['A 확정 (3)', ['0', '3', '2', '8', '10', '∞'], [3], 1, '(A,4) (B,8) (D,10)'],
        ['A 는 낡은 항목', ['0', '3', '2', '8', '10', '∞'], [], -1, '(B,8) (D,10)'],
        ['B 확정 (8)', ['0', '3', '2', '8', '10', '14'], [5], 3, '(D,10) (E,14)'],
        ['D 확정 (10)', ['0', '3', '2', '8', '10', '13'], [5], 4, '(E,13) (E,14)'],
        ['E 확정 (13)', ['0', '3', '2', '8', '10', '13'], [], 5, '(E,14) — 건너뛴다'],
    ];
    const X0 = 152, CWd = 46;
    g.push(txt(146, 232, '단계', { anchor: 'end', cls: 'ink2 bold', size: 'sm' }));
    cols.forEach((c, i) => g.push(txt(X0 + i * CWd + CWd / 2, 232, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    g.push(txt(X0 + 6 * CWd + 14, 232, '우선순위 큐에 남은 것', { cls: 'ink2 bold', size: 'sm' }));
    rows.forEach((r, k) => {
        const y = 240 + k * 26;
        g.push(txt(146, y + 18, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        const hl = {};
        for (const i of r[2]) hl[i] = C2;
        if (r[3] >= 0) hl[r[3]] = C3;
        g.push(cells(X0, y, CWd, 24, r[1], { hl, small: true }));
        g.push(txt(X0 + 6 * CWd + 14, y + 17, r[4], { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[X0 - 96, 236], [X0 + 6 * CWd + 200, 236]], { stroke: CG, sw: 1 }));
    g.push(txt(20, H - 44, '초록 칸이 그 단계에서 확정된 정점, 주황 칸이 완화로 값이 줄어든 정점이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, 'A 가 4 로 확정될 뻔했다가 C 를 거치는 3 으로 줄어든 것을 보라. 확정 전에는 값이 얼마든 줄 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '큐에 (A,4) 가 남아 있지만 A 는 이미 3 으로 확정되었으므로 꺼내는 즉시 건너뛴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-dijkstra-steps',
        svg: svg({ width: W, height: H, title: '다익스트라 알고리즘의 완화 과정',
            desc: '우선순위 큐에서 가장 가까운 정점을 꺼내 확정하고 그 이웃을 완화하는 과정을 여덟 단계로 적었다', body: g.join('') }),
    };
})());

/* ---- 9-4. 음수 가중치에서 깨지는 이유 ---- */
add((() => {
    const W = 786, H = 344;
    const g = [];
    g.push(txt(W / 2, 24, '음수 간선 하나면 ‘가장 가까운 것은 이미 끝났다’ 는 전제가 무너진다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 374, 232, '음수 간선 — 답이 틀린다', 'S 에서 A 까지의 진짜 최단은 1 인데 2 라고 답한다'));
    const N1 = { S: [82, 156, 'S'], A: [216, 114, 'A'], B: [216, 198, 'B'] };
    g.push(graph(N1, [
        ['S', 'A', { w: 2, dir: true }], ['S', 'B', { w: 3, dir: true }],
        ['B', 'A', { w: -2, dir: true, stroke: C2, sw: 2.4, mk: 'ar2', woff: 13 }],
    ], { r: 19 }));
    g.push(txt(256, 100, 'A 를 2 로 확정한 뒤에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(256, 118, 'B 를 꺼내면 3 + (−2) = 1.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(256, 136, '이미 확정한 A 를 고칠 수', { cls: 'ink2', size: 'sm' }));
    g.push(txt(256, 154, '없으므로 답이 틀린 채 끝난다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 242, '거리가 가장 작은 것을 꺼냈다는 사실이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 260, '‘더 짧은 길이 없다’ 를 뜻하지 않게 된다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(404, 42, 368, 232, '음수 순환 — 답 자체가 없다', '고리를 돌 때마다 값이 줄어든다'));
    g.push(gnode(510, 146, 'P', { r: 19 }));
    g.push(gnode(650, 146, 'Q', { r: 19 }));
    g.push(curvePath('M523 133 C 558 100, 602 100, 637 133', { stroke: CK, sw: 1.8, marker: 'ark' }));
    g.push(curvePath('M637 159 C 602 192, 558 192, 523 159', { stroke: C2, sw: 2.4, marker: 'ar2' }));
    g.push(txt(580, 102, '1', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(580, 202, '−3', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(588, 242, '한 바퀴 돌면 −2, 두 바퀴면 −4', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(588, 260, '최솟값이 없으니 최단경로가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 44, '음수 간선이 있으면 다익스트라를 쓸 수 없다. 벨만-포드로 바꾸어야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '음수 순환이 있으면 어떤 알고리즘도 답을 낼 수 없다. 할 수 있는 일은 그것이 있다고 알려 주는 것뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '모든 가중치에 같은 수를 더해 음수를 없애는 요령은 통하지 않는다 — 간선 수가 다른 경로끼리 값이 뒤바뀐다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-dijkstra-negative',
        svg: svg({ width: W, height: H, title: '음수 가중치에서 다익스트라가 깨지는 이유',
            desc: '음수 간선이 있으면 확정한 값이 나중에 줄어들 수 있고 음수 순환이 있으면 최단경로가 없다', body: g.join('') }),
    };
})());

/* ---- 9-5. 벨만-포드의 라운드 ---- */
add((() => {
    const W = 792, H = 390;
    const g = [];
    g.push(txt(W / 2, 24, '간선을 훑는 순서가 나쁘면 한 라운드에 정점 하나씩만 정해진다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 374, 306, '왜 V−1 라운드가 필요한가', '간선을 B→C, A→B, S→A 순서로 훑는다고 하자'));
    const N = { S: [60, 122, 'S'], A: [150, 122, 'A'], B: [240, 122, 'B'], C: [330, 122, 'C'] };
    g.push(graph(N, [['S', 'A', { w: 3, dir: true }], ['A', 'B', { w: 2, dir: true }], ['B', 'C', { w: -1, dir: true }]], { r: 18 }));
    const rr = [
        ['0 (초기)', ['0', '∞', '∞', '∞'], []],
        ['1 라운드', ['0', '3', '∞', '∞'], [1]],
        ['2 라운드', ['0', '3', '5', '∞'], [2]],
        ['3 라운드', ['0', '3', '5', '4'], [3]],
        ['4 라운드', ['0', '3', '5', '4'], []],
    ];
    ['S', 'A', 'B', 'C'].forEach((c, i) => g.push(txt(168 + i * 44 + 22, 188, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    rr.forEach((r, k) => {
        const y = 194 + k * 26;
        g.push(txt(162, y + 16, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(cells(168, y, 44, 22, r[1], { hl: Object.fromEntries(r[2].map(i => [i, C2])), small: true }));
    });
    g.push(txt(28, 340, '4 라운드에 변화가 없다 — 여기서 멈춰도 된다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(404, 42, 374, 306, '음수 순환 탐지', 'C→A 간선 −5 를 더하면 고리의 합이 −4 다'));
    const N2 = { S: [450, 122, 'S'], A: [540, 122, 'A'], B: [630, 122, 'B'], C: [720, 122, 'C'] };
    g.push(graph(N2, [['S', 'A', { w: 3, dir: true }], ['A', 'B', { w: 2, dir: true }], ['B', 'C', { w: -1, dir: true }]], { r: 18 }));
    g.push(curvePath('M706 138 C 660 196, 590 196, 552 140', { stroke: C2, sw: 2.4, marker: 'ar2' }));
    g.push(txt(630, 190, '−5', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(420, 226, 'A → B → C → A 를 한 바퀴 돌면 2 + (−1) + (−5) = −4 다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(420, 246, '정점이 4 개이므로 3 라운드면 끝나야 하는데,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(420, 266, '4 라운드에서도 값이 또 줄어든다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(420, 292, 'V 번째 라운드에 갱신이 일어나면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(420, 312, '음수 순환이 있다는 뜻이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(420, 336, '탐지가 알고리즘 안에 들어 있는 셈이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '최단경로는 간선을 많아야 V−1 개 지난다. 라운드마다 적어도 정점 하나가 확정되므로 V−1 라운드면 충분하다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-bellman-rounds',
        svg: svg({ width: W, height: H, title: '벨만-포드의 라운드와 음수 순환 탐지',
            desc: '모든 간선을 V-1 번 훑고 V 번째에 갱신이 일어나면 음수 순환이 있다', body: g.join('') }),
    };
})());

/* ---- 9-6. A* 가 훑는 영역 ---- */
add((() => {
    const COLS = 26, ROWS = 14, CW = 14;
    const wall = (x, y) => x === 13 && y <= 9;
    const SX = 3, SY = 7, TX = 22, TY = 7;
    const idx = (x, y) => y * COLS + x;
    const dist = new Array(COLS * ROWS).fill(Infinity);
    dist[idx(SX, SY)] = 0;
    let q = [[SX, SY]];
    while (q.length) {
        const nq = [];
        for (const [x, y] of q) {
            for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
                const nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || wall(nx, ny)) continue;
                if (dist[idx(nx, ny)] > dist[idx(x, y)] + 1) {
                    dist[idx(nx, ny)] = dist[idx(x, y)] + 1;
                    nq.push([nx, ny]);
                }
            }
        }
        q = nq;
    }
    const D = dist[idx(TX, TY)];
    const h = (x, y) => Math.abs(TX - x) + Math.abs(TY - y);
    let cntD = 0, cntA = 0;
    for (let y = 0; y < ROWS; y += 1) for (let x = 0; x < COLS; x += 1) {
        if (wall(x, y)) continue;
        if (dist[idx(x, y)] <= D) cntD += 1;
        if (dist[idx(x, y)] + h(x, y) <= D) cntA += 1;
    }
    const W = 792, H = 388;
    const g = [];
    g.push(txt(W / 2, 24, '둘 다 같은 최단경로를 찾는다. 다른 것은 그러기 위해 들여다본 칸 수다', { anchor: 'middle', cls: 'ink bold' }));
    const draw = (px0, title, sub, pred) => {
        const out = [panel(px0, 42, 380, 262, title, sub)];
        out.push(grid(px0 + 8, 96, CW, COLS, ROWS, (x, y) => {
            if (wall(x, y)) return CI;
            return pred(x, y) ? C1 : null;
        }));
        for (let y = 0; y < ROWS; y += 1) for (let x = 0; x < COLS; x += 1) {
            if (wall(x, y)) out.push(box(px0 + 8 + x * CW, 96 + y * CW, CW, CW, { fill: CI, op: 0.7, stroke: CK, sw: 0.6, rx: 1 }));
        }
        out.push(box(px0 + 8 + SX * CW, 96 + SY * CW, CW, CW, { fill: C3, op: 0.95, stroke: C3, sw: 1, rx: 1 }));
        out.push(box(px0 + 8 + TX * CW, 96 + TY * CW, CW, CW, { fill: C2, op: 0.95, stroke: C2, sw: 1, rx: 1 }));
        out.push(txt(px0 + 8 + SX * CW + 7, 96 + SY * CW + 11, 'S', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        out.push(txt(px0 + 8 + TX * CW + 7, 96 + TY * CW + 11, 'T', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        return out.join('');
    };
    g.push(draw(14, '다익스트라 — 사방으로 고르게', `S 에서 ${D} 이하인 칸을 전부 본다`, (x, y) => dist[idx(x, y)] <= D));
    g.push(draw(398, 'A* — 목적지 쪽으로 기울여', '거리에 남은 거리의 추정을 더해 본다', (x, y) => dist[idx(x, y)] + h(x, y) <= D));
    g.push(txt(30, 322, `훑은 칸 ${cntD} 개`, { cls: 'ink bold', size: 'sm' }));
    g.push(txt(414, 322, `훑은 칸 ${cntA} 개 — 절반 아래로 줄었다`, { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, H - 42, `가운데 세로줄이 벽이다. 그 때문에 실제 최단 거리가 ${D} 이고 직선 거리 ${Math.abs(TX - SX)} 보다 멀다.`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, 'A* 는 남은 거리를 벽을 무시한 격자 거리로 어림잡는다. 실제보다 크게 잡지 않으므로 답이 틀리지 않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '어림값을 0 으로 두면 다익스트라가 되고, 크게 잡을수록 좁게 훑지만 실제보다 크면 답이 틀릴 수 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-astar',
        svg: svg({ width: W, height: H, title: 'A* 가 훑는 영역',
            desc: '다익스트라는 출발점을 중심으로 고르게 퍼지고 A* 는 목적지 쪽으로 기울어진 좁은 영역만 본다', body: g.join('') }),
    };
})());

/* ---- 9-7. 자르기 성질 ---- */
add((() => {
    const W = 792, H = 378;
    const g = [];
    g.push(txt(W / 2, 24, '어떻게 잘라도 그 자름을 건너는 가장 싼 간선은 최소신장트리에 들어 있다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 374, 272, '자르기 성질', '점선이 자름이다. 두 무리로 아무렇게나 나눈다'));
    const N = { a: [76, 108, 'a'], b: [76, 180, 'b'], c: [76, 252, 'c'], d: [316, 108, 'd'], e: [316, 180, 'e'], f: [316, 252, 'f'] };
    g.push(graph(N, [
        ['a', 'b'], ['b', 'c'], ['d', 'e'], ['e', 'f'],
        ['b', 'e', { w: 4, stroke: C3, sw: 2.8, woff: 13 }],
        ['a', 'd', { w: 9, stroke: CK, sw: 1.4, woff: 13 }],
        ['c', 'f', { w: 7, stroke: CK, sw: 1.4, woff: 13 }],
    ], { r: 17 }));
    g.push(ln([[248, 80], [236, 128], [260, 176], [234, 228], [248, 276]], { stroke: C2, sw: 2, dash: '7 5' }));
    g.push(txt(28, 292, '자름을 건너는 간선은 4 · 7 · 9 셋이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 308, '그중 가장 싼 4 는 반드시 답에 들어간다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(404, 42, 374, 272, '왜 그런가 — 바꿔치기', '4 가 없는 최소신장트리를 가정하고 모순을 본다'));
    g.push(box(440, 96, 130, 140, { stroke: CG, sw: 1, dash: '5 4', rx: 40 }));
    g.push(box(614, 96, 130, 140, { stroke: CG, sw: 1, dash: '5 4', rx: 40 }));
    const N2 = { p: [470, 130, ''], q: [470, 202, ''], r: [714, 130, ''], t: [714, 202, ''] };
    g.push(graph(N2, [['p', 'q', { dash: '4 3' }], ['r', 't', { dash: '4 3' }]], { r: 12 }));
    g.push(gedge([470, 202], [714, 202], { r1: 12, rr: 12, stroke: C3, sw: 2.8 }));
    g.push(txt(592, 224, '4  (넣고 싶은 간선)', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(gedge([470, 130], [714, 130], { r1: 12, rr: 12, stroke: C2, sw: 2.4 }));
    g.push(txt(592, 118, '7  (지금 트리에 있는 간선)', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(418, 264, '4 를 넣으면 고리가 생기고, 그 고리는 자름을 한 번 더 건넌다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(418, 284, '건너는 그 간선 7 을 빼면 다시 트리인데', { cls: 'ink2', size: 'sm' }));
    g.push(txt(418, 304, '무게는 3 만큼 줄었다. 최소였다는 가정에 모순이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 42, '크루스칼과 프림은 이 성질을 서로 다르게 쓴다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '크루스칼은 가장 싼 간선을 집어 들고 그것이 건너는 자름을 나중에 찾고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '프림은 자름을 먼저 정해 놓고(지금까지 자란 덩어리와 나머지) 그것을 건너는 가장 싼 간선을 고른다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-cut-property',
        svg: svg({ width: W, height: H, title: '자르기 성질',
            desc: '자름을 건너는 최소 가중치 간선은 어떤 최소신장트리에도 들어 있다는 것을 바꿔치기로 보인다', body: g.join('') }),
    };
})());

/* ---- 9-8. union-find ---- */
add((() => {
    const W = 792, H = 368;
    const g = [];
    g.push(txt(W / 2, 24, '두 요령이 있어야 거의 상수 시간이 된다 — 낮은 쪽을 붙이고, 지나간 길을 접는다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(panel(14, 42, 374, 260, '랭크로 합치기', '낮은 트리를 높은 트리 밑에 붙인다'));
    const tA = { r1: [90, 118, 'a'], c1: [58, 190, 'b'], c2: [122, 190, 'c'], gc: [122, 254, 'd'] };
    g.push(graph(tA, [['r1', 'c1', { dir: true }], ['r1', 'c2', { dir: true }], ['c2', 'gc', { dir: true }]], { r: 15 }));
    g.push(txt(90, 96, '높이 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const tB = { r2: [268, 118, 'e'], c3: [268, 190, 'f'] };
    g.push(graph(tB, [['r2', 'c3', { dir: true }]], { r: 15 }));
    g.push(txt(268, 96, '높이 1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(250, 118, 110, 118, { cls: 's3', width: 2.2 }));
    g.push(txt(180, 148, 'e 를 a 밑으로 붙인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(28, 280, '거꾸로 붙이면 높이가 3 이 된다. 낮은 쪽을 붙이면 2 그대로다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(404, 42, 374, 260, '경로 압축', 'find 를 부른 김에 지나간 것을 뿌리에 직접 붙인다'));
    const bef = { R: [470, 122, 'r'], x: [470, 168, 'x'], y: [470, 212, 'y'], z: [470, 256, 'z'] };
    g.push(graph(bef, [['x', 'R', { dir: true }], ['y', 'x', { dir: true }], ['z', 'y', { dir: true }]], { r: 14 }));
    g.push(txt(470, 100, '전', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    const aft = { R2: [672, 122, 'r'], x2: [612, 212, 'x'], y2: [672, 212, 'y'], z2: [732, 212, 'z'] };
    g.push(graph(aft, [['x2', 'R2', { dir: true }], ['y2', 'R2', { dir: true }], ['z2', 'R2', { dir: true }]], { r: 14 }));
    g.push(txt(672, 100, '후', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(arw(524, 190, 562, 190, { cls: 's3', width: 1.8 }));
    g.push(txt(543, 178, 'find(z)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(418, 280, '다음부터 x · y · z 의 find 가 한 걸음이면 끝난다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 42, '둘 중 하나만 쓰면 한 연산이 O(log n) 이고, 둘을 같이 쓰면 n 번 연산의 평균이 사실상 상수다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 24, '정확히는 역 애커만 함수라는 아주 천천히 자라는 함수인데, 현실의 어떤 n 에서도 5 를 넘지 않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 6, '경로 압축을 쓰면 트리의 실제 높이가 랭크보다 낮아진다. 그래서 랭크는 높이가 아니라 상한으로만 쓴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-unionfind',
        svg: svg({ width: W, height: H, title: 'union-find 의 두 요령',
            desc: '랭크가 낮은 트리를 높은 트리 밑에 붙이고 find 를 부른 김에 경로를 뿌리에 직접 붙인다', body: g.join('') }),
    };
})());

/* ---- 9-9. 크루스칼과 프림 ---- */
add((() => {
    const W = 800, H = 404;
    const g = [];
    g.push(txt(W / 2, 24, '고르는 순서가 다르고 결과는 같다 — 둘 다 자르기 성질을 쓰기 때문이다', { anchor: 'middle', cls: 'ink bold' }));
    const EDGES = [
        ['A', 'B', 1], ['B', 'C', 7], ['A', 'D', 4], ['B', 'E', 2],
        ['C', 'F', 8], ['D', 'E', 3], ['E', 'F', 5], ['A', 'E', 6], ['B', 'D', 9],
    ];
    const MST = new Set(['A-B', 'B-E', 'D-E', 'E-F', 'B-C']);
    const key = (a, b) => `${a}-${b}`;
    const mk = (px0, title, sub, order) => {
        const P = {
            A: [px0 + 70, 106], B: [px0 + 190, 106], C: [px0 + 310, 106],
            D: [px0 + 70, 226], E: [px0 + 190, 226], F: [px0 + 310, 226],
        };
        const N = Object.fromEntries(Object.entries(P).map(([k, v]) => [k, [v[0], v[1], k]]));
        const es = EDGES.map(([a, b, w]) => {
            const inMst = MST.has(key(a, b));
            const diag = key(a, b) === 'A-E' || key(a, b) === 'B-D';
            return [a, b, { w, stroke: inMst ? C3 : C2, sw: inMst ? 3 : 1.3, dash: inMst ? undefined : '5 4', woff: diag ? 24 : 12 }];
        });
        const out = [panel(px0, 42, 380, 268, title, sub), graph(N, es, { r: 18 })];
        for (const [a, b] of Object.entries(order)) {
            const [n1, n2] = a.split('-');
            const p1 = P[n1], p2 = P[n2];
            const mx = (p1[0] + p2[0]) / 2, my = (p1[1] + p2[1]) / 2;
            const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
            const L = Math.hypot(dx, dy) || 1;
            out.push(txt(mx + (dy / L) * 13, my - (dx / L) * 13 + 4, b, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        return out.join('');
    };
    g.push(mk(14, '크루스칼 — 싼 간선부터', '가중치 순서로 보되 고리를 만들면 버린다',
        { 'A-B': '1째', 'B-E': '2째', 'D-E': '3째', 'E-F': '4째', 'B-C': '5째' }));
    g.push(mk(406, '프림 — C 에서 시작해 키운다', '자란 덩어리에 붙은 간선 중 가장 싼 것',
        { 'B-C': '1째', 'A-B': '2째', 'B-E': '3째', 'D-E': '4째', 'E-F': '5째' }));
    g.push(txt(30, 288, '버린 간선: 4 (고리) · 6 (고리) · 8 · 9', { cls: 'ink2', size: 'sm' }));
    g.push(txt(422, 288, '덩어리 밖으로 나가는 간선만 후보가 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 62, '초록 굵은 선이 최소신장트리다. 간선 5 개 = 정점 6 개 − 1, 무게 합은 1+2+3+5+7 = 18 로 양쪽이 같다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 44, '크루스칼은 간선을 정렬해 O(E log E), 두 정점이 같은 덩어리인지 묻는 데 union-find 를 쓴다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 26, '프림은 덩어리에 붙은 간선을 우선순위 큐에 담아 O(E log V) 다. 다익스트라와 거의 같은 코드다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, H - 8, '가중치가 모두 다르면 최소신장트리는 하나뿐이다. 같은 값이 있으면 여럿일 수 있고 두 방법이 다른 답을 낼 수 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-g-kruskal-prim',
        svg: svg({ width: W, height: H, title: '크루스칼과 프림',
            desc: '같은 그래프에서 간선을 고르는 순서가 다르지만 최소신장트리는 같다', body: g.join('') }),
    };
})());

export default figures;

