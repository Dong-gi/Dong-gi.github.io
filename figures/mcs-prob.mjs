/**
 * mcs 19장(사건과 확률 공간) · 20장(조건부확률) · 21장(확률변수)의 그림.
 *
 * 이름은 모두 `mcs-r-` 로 시작한다(담당 F 에게 배정된 접두어. r 은 random).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `R~1` 표기를, 나머지는 유니코드(≤ ≥ ≠ ∩ ∪ ∈ ⊆ ∅ ⅓ · × ¬ 등)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 중심 그림은 <b>확률의 나무 그림</b>이다. 네 단계 방법이 19장에서
 * 표준 도구가 되고 20·21장에서 계속 쓰이므로, 나무를 그리는 헬퍼를 하나 만들어
 * 세 장이 같은 모양의 나무를 쓰게 했다.
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
 * 화소 좌표 소도구 — lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG }[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.4, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="${col}"/>`;

const ring = (x, y, r, col = C2, sw = 2, dash) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="none" stroke="${col}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 19, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 35, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/* ------------------------------------------------------------------ *
 * 확률 나무
 *
 * spec 은 { edge?, children?: [...] } 의 중첩이다. children 이 없으면 잎.
 * 잎은 위에서 아래로 균등 간격으로 놓고, 내부 노드는 자식들의 가운데에 둔다.
 * mark: true 인 간선·잎은 강조색으로 그린다(사건에 속하는 결과).
 * ------------------------------------------------------------------ */

function layoutTree(node, depth, st) {
    node.depth = depth;
    node.x = st.x0 + depth * st.colW;
    if (!node.children || node.children.length === 0) {
        node.y = st.y0 + st.leaves.length * st.rowH;
        node.leafIndex = st.leaves.length;
        st.leaves.push(node);
    } else {
        for (const c of node.children) layoutTree(c, depth + 1, st);
        const ys = node.children.map(c => c.y);
        node.y = (Math.min(...ys) + Math.max(...ys)) / 2;
    }
}

/**
 * 나무를 그리고 { body, leaves } 를 돌려준다. leaves 로 오른쪽 열을 붙인다.
 * edgeAbove: 간선 라벨을 선 위에 둘지(false 면 선 아래).
 */
function drawTree(root, { x0, y0, colW, rowH, edgeDy = -5, leafDot = true }) {
    const st = { x0, y0, colW, rowH, leaves: [] };
    layoutTree(root, 0, st);
    const edges = [];
    const dots = [];
    const labels = [];
    const walk = node => {
        for (const c of node.children ?? []) {
            const col = c.mark ? C2 : CK;
            edges.push(ln([[node.x + 4, node.y], [c.x - 4, c.y]], { stroke: col, sw: c.mark ? 2 : 1.2 }));
            if (c.edge) {
                const mx = (node.x + c.x) / 2;
                const my = (node.y + c.y) / 2;
                labels.push(txt(mx, my + edgeDy, c.edge, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
            if (c.name) {
                labels.push(txt(c.x + (c.children ? 0 : 7), c.y + (c.children ? -8 : 4),
                    c.name, { anchor: c.children ? 'middle' : 'start', cls: 'ink', size: 'sm' }));
            }
            walk(c);
        }
        if (node.children) dots.push(pdot(node.x, node.y, CK, 3));
        else if (leafDot) dots.push(pdot(node.x, node.y, node.mark ? C2 : C1, 3.5));
    };
    walk(root);
    return { body: edges.join('') + labels.join('') + dots.join(''), leaves: st.leaves };
}

/** 잎 옆에 붙이는 값 열. */
function leafCol(leaves, x, values, { cls = 'ink', anchor = 'middle', size = 'sm', head, headY } = {}) {
    const g = leaves.map((lf, i) => {
        const v = values[i];
        return (v === null || v === undefined || v === '') ? ''
            : txt(x, lf.y + 4, String(v), { anchor, cls: lf.mark ? 'ink bold' : cls, size });
    });
    if (head) g.push(txt(x, headY, head, { anchor, cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 막대그래프(pmf·cdf 용). vals 는 0~1 로 규격화한 높이, labels 는 가로축 눈금. */
function bars(x0, yBase, bw, gap, vals, labels, hMax, { col = C1, hl = [], sw = 0 } = {}) {
    const g = [];
    vals.forEach((v, i) => {
        const x = x0 + i * (bw + gap);
        const h = v * hMax;
        const c = hl.includes(i) ? C2 : col;
        g.push(box(x, yBase - h, bw, h, { fill: c, op: 0.82, stroke: c, sw: sw || 0.8, rx: 1 }));
        if (labels) g.push(txt(x + bw / 2, yBase + 14, String(labels[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[x0 - 6, yBase], [x0 + vals.length * (bw + gap) + 2, yBase]], { stroke: CK, sw: 1.2 }));
    return g.join('');
}

const binom = (n, k) => {
    let r = 1;
    for (let i = 0; i < k; i += 1) r = (r * (n - i)) / (i + 1);
    return r;
};

/* ================================================================== *
 * 19장 — 사건과 확률 공간
 * ================================================================== */

/* 몬티 홀의 나무 그림. 이 블록 전체의 중심 그림. */
add((() => {
    const W = 700;
    const H = 434;
    const g = [];

    // 차가 A/B/C 뒤, 참가자가 A/B/C 선택, 사회자가 염소 문을 연다.
    const doors = ['A', 'B', 'C'];
    const root = {
        children: doors.map(car => ({
            edge: '1/3', name: car,
            children: doors.map(pick => {
                const opens = doors.filter(d => d !== car && d !== pick);
                return {
                    edge: '1/3', name: pick,
                    children: opens.map(o => ({
                        edge: opens.length === 2 ? '1/2' : '1',
                        name: o,
                        mark: car !== pick,     // 바꾸면 이기는 결과
                        out: `(${car},${pick},${o})`,
                        prob: opens.length === 2 ? '1/18' : '1/9',
                    })),
                };
            }),
        })),
    };

    const t = drawTree(root, { x0: 46, y0: 66, colW: 96, rowH: 26 });
    g.push(t.body);

    g.push(txt(46, 44, '차의 위치', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(142, 44, '참가자 선택', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(238, 44, '열린 문', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(leafCol(t.leaves, 380, t.leaves.map(l => l.out), { head: '결과', headY: 44 }));
    g.push(leafCol(t.leaves, 480, t.leaves.map(l => (l.mark ? '이긴다' : '')), { head: '바꾸면', headY: 44, cls: 'ink' }));
    g.push(leafCol(t.leaves, 560, t.leaves.map(l => l.prob), { head: '확률', headY: 44 }));

    g.push(txt(20, 22, '네 단계 방법 — 결과를 정하고, 나무를 그리고, 간선에 확률을 매기고, 사건의 확률을 더한다', { cls: 'ink bold', size: 'sm' }));

    // 오른쪽 요약
    g.push(panel(590, 64, 100, 122, '바꾸면 이긴다'));
    g.push(txt(640, 112, '6 개 결과', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 134, '각 1/9', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 166, '합 = 2/3', { anchor: 'middle', cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C2}"`));
    g.push(panel(590, 204, 100, 122, '안 바꿔야 이긴다'));
    g.push(txt(640, 252, '6 개 결과', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 274, '각 1/18', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 306, '합 = 1/3', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 376], [W - 20, 376]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 398, '잎 12 개 가운데 6 개가 강조되어 있지만 확률은 1/2 이 아니다 — 결과들의 확률이 서로 다르다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 420, '사회자에게 선택권이 있는 잎은 1/18, 없는 잎은 1/9 다. 이것이 이 문제의 모든 혼란이 시작되는 자리다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-monty-tree',
        svg: svg({
            width: W, height: H,
            title: '몬티 홀 문제의 나무 그림',
            desc: '차의 위치, 참가자 선택, 열린 문 세 층으로 된 나무와 결과 12개의 확률. 바꾸면 이기는 결과 여섯 개의 확률 합이 2/3 이다',
            body: g.join(''),
        }),
    };
})());

/* 이상한 주사위 — 추이적이지 않은 승률 */
add((() => {
    const W = 690;
    const H = 386;
    const g = [];
    g.push(txt(20, 24, '이상한 주사위 — 마주 보는 면에 같은 수가 적혀 있어 각 수가 나올 확률이 1/3 이다', { cls: 'ink bold', size: 'sm' }));

    const dice = [
        { n: 'A', v: [2, 6, 7], x: 62 },
        { n: 'B', v: [1, 5, 9], x: 192 },
        { n: 'C', v: [3, 4, 8], x: 322 },
    ];
    for (const d of dice) {
        g.push(box(d.x - 34, 46, 68, 68, { stroke: CK, sw: 1.4, rx: 8 }));
        g.push(txt(d.x, 68, d.n, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(d.x, 92, d.v.join('  '), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(d.x, 108, '각 1/3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    // A 대 B 의 아홉 칸
    const gx = 62;
    const gy = 156;
    const cw = 30;
    g.push(txt(gx - 14, gy - 14, 'A 대 B — 아홉 칸이 각각 1/9', { cls: 'ink2', size: 'sm' }));
    const A = [2, 6, 7];
    const B = [1, 5, 9];
    for (let i = 0; i < 3; i += 1) {
        g.push(txt(gx - 12, gy + 22 + i * cw, String(A[i]), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(gx + cw / 2 + i * cw, gy + 2, String(B[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        for (let j = 0; j < 3; j += 1) {
            const win = A[i] > B[j];
            g.push(box(gx + j * cw, gy + 8 + i * cw, cw, cw,
                { fill: win ? C2 : CG, op: win ? 0.24 : 0.18, stroke: win ? C2 : CG, sw: 1 }));
            g.push(txt(gx + j * cw + cw / 2, gy + 8 + i * cw + cw / 2 + 4, win ? 'A' : 'B',
                { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    }
    g.push(txt(gx - 14, gy + 122, 'A 가 이기는 칸이 다섯 → 5/9', { cls: 'ink bold', size: 'sm' }));

    // 순환 그림
    const cx = 336;
    const cy = 204;
    const R = 60;
    const pos = [[cx, cy - R], [cx + R * 0.87, cy + R * 0.5], [cx - R * 0.87, cy + R * 0.5]];
    const nm = ['A', 'B', 'C'];
    for (let i = 0; i < 3; i += 1) {
        const p1 = pos[i];
        const q = pos[(i + 1) % 3];
        const ux = q[0] - p1[0];
        const uy = q[1] - p1[1];
        const L = Math.hypot(ux, uy);
        g.push(arw(p1[0] + (ux / L) * 21, p1[1] + (uy / L) * 21, q[0] - (ux / L) * 23, q[1] - (uy / L) * 23, { cls: 's2', width: 2 }));
    }
    for (let i = 0; i < 3; i += 1) {
        g.push(pdot(pos[i][0], pos[i][1], CG, 17));
        g.push(txt(pos[i][0], pos[i][1] + 5, nm[i], { anchor: 'middle', cls: 'ink bold' }));
    }
    g.push(txt(cx, cy + 112, '한 번 굴리면 A 가 B 를, B 가 C 를, C 가 A 를 이긴다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx, cy + 132, '어느 것을 골라도 상대가 이길 주사위를 남겨 둘 수 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(panel(506, 156, 164, 110, '두 번 굴리면'));
    g.push(txt(588, 200, '뒤집힌다', { anchor: 'middle', cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C2}"`));
    g.push(txt(588, 226, 'B 가 A 를 이긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(588, 248, '42/81 대 37/81', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 348], [W - 20, 348]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 368, '‘더 자주 이긴다’ 는 관계는 추이적이지 않다 — 12장의 부분순서가 되지 못한다는 뜻이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-strange-dice',
        svg: svg({
            width: W, height: H,
            title: '이상한 주사위의 순환하는 승률',
            desc: '세 주사위의 눈, A 대 B 의 아홉 칸 표, 그리고 A 가 B 를, B 가 C 를, C 가 A 를 이기는 순환 그림',
            body: g.join(''),
        }),
    };
})());

/* 생일 원리 — 일치가 없을 확률 */
add((() => {
    const W = 620;
    const H = 320;
    const g = [];
    const d = 365;
    const exact = n => {
        let p = 1;
        for (let i = 0; i < n; i += 1) p *= 1 - i / d;
        return p;
    };
    const bound = n => Math.exp(-(n * (n - 1)) / (2 * d));

    const f = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: 60, y: 56, w: 380, h: 200 } });
    g.push(f.axes({
        xLabel: '사람 수 n', yLabel: '생일이 모두 다를 확률',
        xTicks: [0, 20, 27, 40, 60, 80, 100], yTicks: [0, 0.25, 0.368, 0.5, 0.75, 1],
    }));
    g.push(f.curve(exact, { from: 0, to: 100, cls: 's1' }));
    g.push(f.curve(bound, { from: 0, to: 100, cls: 's2', dash: '5 4' }));
    g.push(f.guide([27, 0], [27, 0.368]));
    g.push(f.guide([0, 0.368], [27, 0.368]));
    g.push(f.dot([27, exact(27)], { cls: 'f1', r: 4 }));
    g.push(f.label([27, exact(27)], 'n = 27 에서 1/e 근처', { dx: 10, dy: 4, cls: 'ink', size: 'sm' }));

    g.push(txt(20, 24, '생일 원리 — 날이 d 일이고 사람이 √(2d) 명이면 일치가 생길 확률이 약 1 − 1/e ≈ 0.632', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(460, 76, '실선: 정확한 값', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 96, '점선: 상한', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 114, 'exp(−n(n−1)/2d)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 142, '1 − x ≤ e^(−x) 를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 160, '항마다 쓴 결과다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 192, 'd = 365, √(2d) ≈ 27', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(460, 214, 'n = 23 에서 이미 1/2 아래', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, 236, 'n = 95 에서는 1/200000 미만', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 274], [W - 20, 274]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 296, '두 곡선이 거의 붙어 있으므로 상한만 계산해도 충분하다. n 이 √d 규모가 되는 순간 확률이 급히 무너진다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-birthday-curve',
        svg: svg({
            width: W, height: H,
            title: '생일이 모두 다를 확률과 그 상한',
            desc: 'd = 365 일 때 사람 수에 따라 생일이 모두 다를 확률과 지수 상한이 거의 겹치는 그림',
            body: g.join(''),
        }),
    };
})());

/* 집합론에서 온 확률 규칙 */
add((() => {
    const W = 660;
    const H = 340;
    const g = [];
    g.push(txt(20, 24, '유한집합의 크기에 대한 규칙이 확률로 그대로 옮겨 온다 — 크기를 재는 자가 |·| 에서 Pr 로 바뀔 뿐이다', { cls: 'ink bold', size: 'sm' }));

    // 두 사건의 벤 그림
    const cx = 150;
    const cy = 130;
    g.push(ring(cx - 26, cy, 54, C1, 1.8));
    g.push(ring(cx + 26, cy, 54, C2, 1.8));
    g.push(txt(cx - 62, cy - 44, 'A', { cls: 'ink bold' }));
    g.push(txt(cx + 58, cy - 44, 'B', { cls: 'ink bold' }));
    g.push(txt(cx, cy + 4, 'A ∩ B', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx - 52, cy + 4, 'A − B', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + 52, cy + 4, 'B − A', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(box(cx - 106, cy - 78, 212, 156, { stroke: CG, sw: 1, rx: 4 }));
    g.push(txt(cx - 100, cy - 62, 'S', { cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, cy + 104, '겹치는 부분을 두 번 세면 Pr[A ∪ B] 가 부풀려진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 규칙 목록
    const rules = [
        ['합의 규칙', '서로소이면 Pr[A ∪ B] = Pr[A] + Pr[B]'],
        ['여사건 규칙', 'Pr[¬A] = 1 − Pr[A]'],
        ['차의 규칙', 'Pr[B − A] = Pr[B] − Pr[A ∩ B]'],
        ['포함배제', 'Pr[A ∪ B] = Pr[A] + Pr[B] − Pr[A ∩ B]'],
        ['불의 부등식', 'Pr[A ∪ B] ≤ Pr[A] + Pr[B]'],
        ['단조성', 'A ⊆ B 이면 Pr[A] ≤ Pr[B]'],
    ];
    const bx = 306;
    let by = 62;
    for (const [k, v] of rules) {
        g.push(box(bx, by - 16, 336, 32, { stroke: CG, sw: 1, rx: 4 }));
        g.push(txt(bx + 10, by + 5, k, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(bx + 96, by + 5, v, { cls: 'ink2', size: 'sm' }));
        by += 38;
    }

    g.push(ln([[20, 268], [W - 20, 268]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 290, '여섯 규칙 모두 첫 줄의 합의 규칙 하나에서 나온다. 서로소인 조각으로 쪼개고 다시 더하는 것이 증명의 전부다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 312, '균등 확률공간에서는 Pr[E] = |E| / |S| 이므로 17장의 셈의 규칙이 그대로 확률 계산이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 330, '가산 가법성 덕분에 사건이 무한히 많아도 같은 규칙을 쓴다 — 합집합 한계가 그 예다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-set-rules',
        svg: svg({
            width: W, height: H,
            title: '집합론에서 온 확률 규칙 여섯 개',
            desc: '두 사건의 벤 그림과 합의 규칙에서 나오는 여섯 가지 확률 규칙 목록',
            body: g.join(''),
        }),
    };
})());

/* 무한 확률공간 — 먼저 앞면을 내는 사람이 이기는 게임 */
add((() => {
    const W = 640;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '두 사람이 번갈아 공정한 동전을 던져 먼저 앞면을 낸 사람이 이긴다 — 표본공간이 무한하다', { cls: 'ink bold', size: 'sm' }));

    // 사슬 모양 나무: 가로로 뒷면이 이어지고 앞면은 아래로 빠져 잎이 된다
    const x0 = 74;
    const y0 = 74;
    const dx = 104;
    const seq = ['1번', '2번', '1번', '2번', '1번'];
    for (let i = 0; i < 5; i += 1) {
        const x = x0 + i * dx;
        const lx = x + 30;
        const ly = y0 + 66;
        g.push(ln([[x + 3, y0 + 3], [lx - 2, ly - 4]], { stroke: i % 2 === 0 ? C2 : CK, sw: i % 2 === 0 ? 2 : 1.2 }));
        g.push(txt(x - 5, y0 + 38, 'H 1/2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(pdot(lx, ly, i % 2 === 0 ? C2 : C1, 4));
        g.push(txt(lx, ly + 20, `${seq[i]} 승`, { anchor: 'middle', cls: i % 2 === 0 ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(lx, ly + 38, `1/${2 ** (i + 1)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i < 4) {
            g.push(ln([[x + 5, y0], [x + dx - 5, y0]], { stroke: CK, sw: 1.2 }));
            g.push(txt(x + dx / 2, y0 - 8, 'T 1/2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        g.push(pdot(x, y0, CK, 3));
    }
    g.push(txt(x0 + 4 * dx + 22, y0 + 5, '⋯ 끝없이 이어진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 - 40, y0 + 5, '시작', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    // 합
    g.push(box(60, 210, 520, 66, { stroke: C2, sw: 1.5, rx: 5 }));
    g.push(txt(320, 236, 'Pr[1번 승] = 1/2 + 1/8 + 1/32 + ⋯ = (1/2) · 1/(1 − 1/4) = 2/3', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(320, 262, '2번은 1/3 이다. 두 값을 더하면 1 이므로 확률함수의 조건이 맞다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 292], [W - 20, 292]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 312, '표본공간은 {T^n H : n ∈ ℕ} 이고 Pr[T^n H] = 1/2^(n+1) 이다. ‘영원히 뒷면’ 은 잎이 아니므로 결과가 아니다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-infinite-tree',
        svg: svg({
            width: W, height: H,
            title: '무한 확률공간의 나무 그림',
            desc: '번갈아 동전을 던져 먼저 앞면을 낸 사람이 이기는 게임의 무한 나무와 등비급수로 얻은 확률 2/3',
            body: g.join(''),
        }),
    };
})());

/* 균등 확률공간과 셈의 규칙 */
add((() => {
    const W = 620;
    const H = 300;
    const g = [];
    g.push(txt(20, 24, '균등 확률공간에서는 확률을 재는 일이 개수를 세는 일이 된다', { cls: 'ink bold', size: 'sm' }));

    // 격자 위에 사건 영역
    const gx = 44;
    const gy = 52;
    const cw = 22;
    const cols = 12;
    const rows = 7;
    const inE = (i, j) => (i >= 2 && i <= 5 && j >= 1 && j <= 3) || (i >= 8 && i <= 10 && j >= 4 && j <= 5);
    let cnt = 0;
    for (let j = 0; j < rows; j += 1) {
        for (let i = 0; i < cols; i += 1) {
            const on = inE(i, j);
            if (on) cnt += 1;
            g.push(box(gx + i * cw, gy + j * cw, cw, cw,
                { fill: on ? C2 : CG, op: on ? 0.3 : 0.14, stroke: on ? C2 : CG, sw: on ? 1.2 : 0.8, rx: 1 }));
        }
    }
    g.push(txt(gx, gy - 8, `|S| = ${cols * rows} 개 결과가 모두 같은 확률 1/${cols * rows}`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(gx, gy + rows * cw + 20, `사건 E 에 든 결과 ${cnt} 개 → Pr[E] = ${cnt}/${cols * rows}`, { cls: 'ink bold', size: 'sm' }));

    const bx = 352;
    g.push(panel(bx, 52, 246, 190, '17장이 그대로 쓰인다'));
    const items = [
        'Pr[E] = |E| / |S|',
        '분모는 전체 결과의 개수',
        '분자는 사건에 든 결과의 개수',
        '',
        '풀하우스: |E| = 13·C(4,3)·12·C(4,2)',
        '전체: |S| = C(52,5)',
        'Pr ≈ 1/694',
    ];
    items.forEach((v, i) => {
        if (!v) return;
        g.push(txt(bx + 14, 98 + i * 21, v, { cls: i === 0 || i === 6 ? 'ink bold' : 'ink2', size: 'sm' }));
    });

    g.push(ln([[20, 246], [W - 20, 246]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 268, '주의 — 균등하지 않은 공간에서 개수만 세면 틀린다. 몬티 홀의 잎 12 개가 바로 그 경우다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 288, '먼저 ‘결과들이 같은 확률인가’ 를 확인하고, 그때만 개수 세기로 넘어간다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-uniform-count',
        svg: svg({
            width: W, height: H,
            title: '균등 확률공간에서 확률은 개수의 비다',
            desc: '격자로 나타낸 표본공간과 그 안의 사건, 그리고 Pr[E] = |E|/|S| 로 계산한 포커 손의 예',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 20장 — 조건부확률
 * ================================================================== */

/* 조건을 걸면 표본공간이 바뀐다 */
add((() => {
    const W = 660;
    const H = 350;
    const g = [];
    g.push(txt(20, 24, '조건을 건다는 것은 표본공간을 줄이고 남은 확률을 다시 1 로 맞추는 일이다', { cls: 'ink bold', size: 'sm' }));

    // 왼쪽: 전체 12 결과, 조건에 든 3 개만 강조
    const outs = [
        ['(A,A,B)', '1/18', true, true],
        ['(A,A,C)', '1/18', true, false],
        ['(A,B,C)', '1/9', false, false],
        ['(A,C,B)', '1/9', false, false],
        ['(B,A,C)', '1/9', false, false],
        ['(B,B,A)', '1/18', false, false],
        ['(B,B,C)', '1/18', false, false],
        ['(B,C,A)', '1/9', false, false],
        ['(C,A,B)', '1/9', true, true],
        ['(C,B,A)', '1/9', false, false],
        ['(C,C,A)', '1/18', false, false],
        ['(C,C,B)', '1/18', false, false],
    ];
    const x0 = 40;
    const y0 = 58;
    g.push(txt(x0, y0 - 12, '조건 전 — 결과 12 개', { cls: 'ink2', size: 'sm' }));
    outs.forEach(([o, p, inC], i) => {
        const y = y0 + i * 20;
        g.push(box(x0, y, 150, 18, { fill: inC ? C1 : CG, op: inC ? 0.22 : 0.1, stroke: inC ? C1 : CG, sw: 1, rx: 2 }));
        g.push(txt(x0 + 8, y + 13, o, { cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 + 142, y + 13, p, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(x0, y0 + 258, '강조한 셋이 조건 [A 를 골랐고 B 에 염소]', { cls: 'ink2', size: 'sm' }));

    g.push(arw(206, 150, 258, 150, { cls: 's1', width: 2 }));
    g.push(txt(232, 138, '조건', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 새 공간
    const x1 = 272;
    g.push(txt(x1, y0 - 12, '조건 후 — 결과 3 개, 확률을 Pr[Y] 로 나눈다', { cls: 'ink2', size: 'sm' }));
    const news = [['(A,A,B)', '1/18', '(1/18)/(2/9) = 1/4', false],
        ['(A,A,C)', '1/18', '(1/18)/(2/9) = 1/4', false],
        ['(C,A,B)', '1/9', '(1/9)/(2/9) = 1/2', true]];
    news.forEach(([o, p, q, win], i) => {
        const y = y0 + i * 26;
        g.push(box(x1, y, 300, 22, { fill: win ? C2 : C1, op: 0.2, stroke: win ? C2 : C1, sw: 1.2, rx: 2 }));
        g.push(txt(x1 + 8, y + 16, o, { cls: 'ink2', size: 'sm' }));
        g.push(txt(x1 + 76, y + 16, p, { cls: 'ink2', size: 'sm' }));
        g.push(txt(x1 + 120, y + 16, q, { cls: win ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(x1, y0 + 96, 'Pr[Y] = 1/18 + 1/18 + 1/9 = 2/9', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1, y0 + 118, '바꿔서 이기는 결과는 (C,A,B) 하나 → 1/2', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(x1, y0 + 134, 300, 118, '계산은 옳다. 조건이 틀렸다'));
    g.push(txt(x1 + 12, y0 + 176, '참가자가 아는 것은 ‘카롤이 B 를 열었다’', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1 + 12, y0 + 196, '인데, 위 조건은 (A,A,C) 까지 품는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1 + 12, y0 + 216, '— 카롤이 C 를 연 결과다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1 + 12, y0 + 240, '그 잎을 빼면 (1/9)/(1/6) = 2/3', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 326], [W - 20, 326]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 344, '조건부확률을 틀리는 흔한 방식은 계산이 아니라 조건을 잘못 고르는 것이다 — 아는 것을 다 넣어야 한다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-r-monty-condition',
        svg: svg({
            width: W, height: H,
            title: '조건을 걸면 표본공간이 줄어든다',
            desc: '몬티 홀의 결과 12개 가운데 조건에 맞는 셋만 남기고 확률을 다시 정규화하는 그림',
            body: g.join(''),
        }),
    };
})());

/* 나무 그림이 왜 옳은가 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '나무의 간선에 적은 수는 조건부확률이고, 곱하기는 곱셈 규칙이다', { cls: 'ink bold', size: 'sm' }));

    // 한 경로를 크게 그린다
    const pts = [[70, 96], [230, 76], [390, 112], [540, 92]];
    const eds = [
        ['Pr[E~1]', '뿌리에서 첫 갈림'],
        ['Pr[E~2 | E~1]', '첫 갈림을 지난 뒤'],
        ['Pr[E~3 | E~1 ∩ E~2]', '둘을 지난 뒤'],
    ];
    for (let i = 0; i < 3; i += 1) {
        g.push(ln([[pts[i][0] + 5, pts[i][1]], [pts[i + 1][0] - 5, pts[i + 1][1]]], { stroke: C2, sw: 2.2 }));
        const mx = (pts[i][0] + pts[i + 1][0]) / 2;
        const my = (pts[i][1] + pts[i + 1][1]) / 2;
        g.push(txt(mx, my - 12, eds[i][0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(mx, my + 22, eds[i][1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    // 갈림에서 갈라져 나가는 다른 가지들(짧은 그루터기)
    for (const [pp, dx, dy] of [[pts[0], 52, 34], [pts[1], 46, -36], [pts[2], 50, -32]]) {
        g.push(ln([[pp[0] + 5, pp[1]], [pp[0] + dx, pp[1] + dy]], { stroke: CG, sw: 1.2 }));
    }
    for (const p of pts) g.push(pdot(p[0], p[1], p === pts[3] ? C2 : CK, p === pts[3] ? 4.5 : 3.5));
    g.push(txt(70, 84, '뿌리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(552, 96, '잎 — 결과 하나', { cls: 'ink bold', size: 'sm' }));

    g.push(box(40, 168, 580, 60, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(330, 194, 'Pr[E~1 ∩ E~2 ∩ E~3] = Pr[E~1] · Pr[E~2 | E~1] · Pr[E~3 | E~1 ∩ E~2]', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(330, 216, '조건부확률의 정의를 세 번 쓴 것이 전부다 — 이것이 ‘경로의 수를 곱한다’ 의 정체다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 250], [W - 20, 250]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 272, '증명은 정의를 펼치면 분모와 분자가 차례로 지워지는 것을 보이면 끝난다. n 개 간선으로 가는 것은 귀납법이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 294, '그래서 간선 확률을 매길 때 물어야 할 것은 하나다 — ‘여기까지 왔다고 할 때’ 이 갈림이 얼마나 자주 일어나는가.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 314, '간선 확률의 합이 갈림마다 1 이 되는 것도 이 때문이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-tree-why',
        svg: svg({
            width: W, height: H,
            title: '나무 그림이 옳은 이유',
            desc: '뿌리에서 잎까지 가는 한 경로의 간선 확률이 각각 조건부확률이고, 그 곱이 결과의 확률이라는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 하키 3전 2선승 나무 */
add((() => {
    const W = 660;
    const H = 356;
    const g = [];
    g.push(txt(20, 24, '조건부확률의 네 단계 방법 — 두 사건에 든 결과를 각각 표시하고 확률의 비를 잡는다', { cls: 'ink bold', size: 'sm' }));

    const root = {
        children: [
            {
                edge: '1/2', name: 'W',
                children: [
                    { edge: '2/3', name: 'W', out: 'WW', prob: '1/3', inT: true, inF: true },
                    {
                        edge: '1/3', name: 'L',
                        children: [
                            { edge: '1/3', name: 'W', out: 'WLW', prob: '1/18', inT: true, inF: true },
                            { edge: '2/3', name: 'L', out: 'WLL', prob: '1/9', inT: false, inF: true },
                        ],
                    },
                ],
            },
            {
                edge: '1/2', name: 'L',
                children: [
                    {
                        edge: '1/3', name: 'W',
                        children: [
                            { edge: '2/3', name: 'W', out: 'LWW', prob: '1/9', inT: true, inF: false },
                            { edge: '1/3', name: 'L', out: 'LWL', prob: '1/18', inT: false, inF: false },
                        ],
                    },
                    { edge: '2/3', name: 'L', out: 'LL', prob: '1/3', inT: false, inF: false },
                ],
            },
        ],
    };
    // 사건 F(1차전 승) 에 든 잎을 강조
    const t = drawTree(root, { x0: 56, y0: 74, colW: 92, rowH: 34 });
    g.push(t.body);
    g.push(txt(56, 52, '1차전', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(148, 52, '2차전', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(240, 52, '3차전', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(leafCol(t.leaves, 380, t.leaves.map(l => l.out), { head: '결과', headY: 52 }));
    g.push(leafCol(t.leaves, 452, t.leaves.map(l => (l.inT ? '●' : '')), { head: 'T 우승', headY: 52 }));
    g.push(leafCol(t.leaves, 524, t.leaves.map(l => (l.inF ? '●' : '')), { head: 'F 1차전승', headY: 52 }));
    g.push(leafCol(t.leaves, 600, t.leaves.map(l => l.prob), { head: '확률', headY: 52 }));

    g.push(box(28, 282, 604, 62, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(330, 308, 'Pr[T | F] = Pr[T ∩ F] / Pr[F] = (1/3 + 1/18) / (1/3 + 1/18 + 1/9) = 7/9', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(330, 332, '분자는 두 열에 모두 ● 인 잎, 분모는 오른쪽 열에 ● 인 잎이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-hockey-tree',
        svg: svg({
            width: W, height: H,
            title: '3전 2선승 대회의 조건부확률',
            desc: '1차전을 이긴다는 조건에서 대회 우승 확률을 나무 그림의 두 열로 계산하는 그림',
            body: g.join(''),
        }),
    };
})());

/* 검사 결과의 자연 빈도 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '‘정확도 95%’ 인 검사에서 양성이 나와도 병일 확률은 낮다 — 건강한 사람이 압도적으로 많기 때문이다', { cls: 'ink bold', size: 'sm' }));

    // 10000 명 상자 분해
    const bx = 34;
    g.push(box(bx, 48, 150, 44, { stroke: CK, sw: 1.4, rx: 4 }));
    g.push(txt(bx + 75, 68, '10000 명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 75, 85, '유병률 1%', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(bx + 150, 70, bx + 196, 132, { cls: 's2', width: 1.6 }));
    g.push(arw(bx + 150, 70, bx + 196, 226, { cls: 's1', width: 1.6 }));

    g.push(box(bx + 200, 112, 132, 40, { fill: C2, op: 0.2, stroke: C2, sw: 1.3, rx: 4 }));
    g.push(txt(bx + 266, 130, '병이 있는 100 명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 266, 146, '민감도 90%', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(box(bx + 200, 206, 132, 40, { fill: C1, op: 0.18, stroke: C1, sw: 1.3, rx: 4 }));
    g.push(txt(bx + 266, 224, '건강한 9900 명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 266, 240, '위양성 5%', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(bx + 332, 132, bx + 386, 132, { cls: 's2', width: 1.6 }));
    g.push(arw(bx + 332, 226, bx + 386, 226, { cls: 's1', width: 1.6 }));

    g.push(box(bx + 390, 112, 176, 40, { stroke: C2, sw: 1.3, rx: 4 }));
    g.push(txt(bx + 478, 138, '진짜 양성 90 명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(box(bx + 390, 206, 176, 40, { stroke: C1, sw: 1.3, rx: 4 }));
    g.push(txt(bx + 478, 232, '헛 양성 495 명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 막대로 비율 보이기
    const rx = bx + 390;
    const ry = 166;
    const wTot = 176;
    const wTrue = (90 / 585) * wTot;
    g.push(box(rx, ry, wTrue, 20, { fill: C2, op: 0.8, stroke: C2, sw: 1, rx: 2 }));
    g.push(box(rx + wTrue, ry, wTot - wTrue, 20, { fill: C1, op: 0.55, stroke: C1, sw: 1, rx: 2 }));
    g.push(txt(rx + wTot / 2, ry + 36, '양성 585 명 가운데 90 명 — 약 15%', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 272], [W - 20, 272]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 294, '수식으로는 Pr[병 | 양성] = 0.009 / (0.009 + 0.0495) ≈ 0.154 다. 사람 수로 세어 보면 같은 값이 눈에 보인다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 316, '‘항상 음성’ 이라고만 답하는 검사도 정확도가 99% 다. 정확도라는 말이 얼마나 허술한지 알 수 있다.', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-r-test-frequencies',
        svg: svg({
            width: W, height: H,
            title: '검사의 양성 결과를 자연 빈도로 세어 보기',
            desc: '만 명을 병이 있는 100명과 건강한 9900명으로 나누어 양성자 585명 가운데 진짜 양성이 90명뿐임을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* 심슨의 역설 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '심슨의 역설 — 두 학과에서 모두 여성 합격률이 높은데 전체로는 남성이 높다', { cls: 'ink bold', size: 'sm' }));

    const groups = [
        { t: '전산 (CS)', m: [2, 5, 0.4], f: [50, 100, 0.5] },
        { t: '전기 (EE)', m: [70, 100, 0.7], f: [4, 5, 0.8] },
        { t: '전체', m: [72, 105, 72 / 105], f: [54, 105, 54 / 105] },
    ];
    const gx = 44;
    const gy = 64;
    const bh = 150;
    groups.forEach((grp, i) => {
        const x = gx + i * 196;
        g.push(txt(x + 74, gy - 8, grp.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(ln([[x, gy + bh], [x + 148, gy + bh]], { stroke: CK, sw: 1.2 }));
        // 남성 막대
        [['남', grp.m, C1, 20], ['여', grp.f, C2, 86]].forEach(([nm, v, col, dx]) => {
            const h = v[2] * bh;
            g.push(box(x + dx, gy + bh - h, 42, h, { fill: col, op: 0.75, stroke: col, sw: 1, rx: 2 }));
            g.push(txt(x + dx + 21, gy + bh - h - 6, `${Math.round(v[2] * 100)}%`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(txt(x + dx + 21, gy + bh + 15, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(txt(x + dx + 21, gy + bh + 31, `${v[0]}/${v[1]}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
        if (i < 2) g.push(txt(x + 74, gy + bh + 52, '여성이 높다', { anchor: 'middle', cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C2}"`));
        else g.push(txt(x + 74, gy + bh + 52, '남성이 높다', { anchor: 'middle', cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C1}"`));
    });

    g.push(ln([[20, 280], [W - 20, 280]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 300, '까닭은 지원 분포다. 여성은 합격률 낮은 CS 에 100 명, 높은 EE 에 5 명 몰렸고 남성은 반대다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 320, '조건부확률은 조건마다 다른 가중치로 평균된다. 가중치를 무시하고 비율만 더하면 반대 결론이 나온다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-simpson',
        svg: svg({
            width: W, height: H,
            title: '심슨의 역설의 수치',
            desc: '두 학과에서는 여성 합격률이 각각 더 높지만 합치면 남성 합격률이 높아지는 막대그래프',
            body: g.join(''),
        }),
    };
})());

/* 쌍마다 독립이어도 상호 독립이 아니다 */
add((() => {
    const W = 660;
    const H = 354;
    const g = [];
    g.push(txt(20, 24, '동전 셋을 던져 A~1 = ‘1·2 가 같다’, A~2 = ‘2·3 이 같다’, A~3 = ‘3·1 이 같다’ 로 둔다', { cls: 'ink bold', size: 'sm' }));

    const outs = ['HHH', 'HHT', 'HTH', 'HTT', 'THH', 'THT', 'TTH', 'TTT'];
    const a1 = o => o[0] === o[1];
    const a2 = o => o[1] === o[2];
    const a3 = o => o[2] === o[0];
    const gx = 40;
    const gy = 56;
    const cw = 62;
    const rh = 26;
    const heads = ['결과', '확률', 'A~1', 'A~2', 'A~3'];
    heads.forEach((h, i) => g.push(txt(gx + i * cw + cw / 2, gy - 6, h, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    outs.forEach((o, r) => {
        const y = gy + r * rh;
        const vals = [o, '1/8', a1(o) ? '✓' : '', a2(o) ? '✓' : '', a3(o) ? '✓' : ''];
        const all = a1(o) && a2(o) && a3(o);
        vals.forEach((v, c) => {
            g.push(box(gx + c * cw, y, cw, rh, {
                fill: all && c >= 2 ? C2 : 'none', op: 0.22, stroke: CG, sw: 1, rx: 1,
            }));
            g.push(txt(gx + c * cw + cw / 2, y + rh / 2 + 5, v, { anchor: 'middle', cls: all ? 'ink bold' : 'ink2', size: 'sm' }));
        });
    });
    g.push(txt(gx, gy + 8 * rh + 22, '세 사건이 동시에 일어나는 결과는 HHH 와 TTT 둘뿐이다', { cls: 'ink2', size: 'sm' }));

    const bx = 366;
    g.push(panel(bx, 52, 274, 96, '쌍마다는 독립이다', ''));
    g.push(txt(bx + 14, 90, 'Pr[A~1] = Pr[A~2] = Pr[A~3] = 1/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 112, 'Pr[A~1 ∩ A~2] = 1/4 = (1/2)(1/2)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 134, '다른 두 쌍도 마찬가지다', { cls: 'ink2', size: 'sm' }));

    g.push(box(bx, 164, 274, 100, { stroke: C2, sw: 1.6, rx: 6 }));
    g.push(txt(bx + 137, 186, '그러나 상호 독립은 아니다', { anchor: 'middle', cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C2}"`));
    g.push(txt(bx + 14, 210, 'Pr[A~1 ∩ A~2 ∩ A~3] = 2/8 = 1/4', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 232, '곱은 (1/2)³ = 1/8 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 254, '1/4 ≠ 1/8 이므로 조건이 깨진다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 296], [W - 20, 296]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 318, '까닭은 셋 가운데 둘이 정해지면 나머지가 결정된다는 것이다 — 두 쌍이 같으면 세 번째 쌍도 반드시 같다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 340, '상호 독립을 확인하려면 크기 2 이상인 모든 부분집합을 봐야 한다. 쌍만 보고 넘어가면 이 함정에 빠진다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-pairwise',
        svg: svg({
            width: W, height: H,
            title: '쌍마다 독립이지만 상호 독립이 아닌 세 사건',
            desc: '동전 세 개의 결과 여덟 개를 늘어놓고 세 사건이 쌍마다는 독립이나 셋을 함께 보면 독립이 아님을 확인하는 표',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 21장 — 확률변수
 * ================================================================== */

/* 확률변수는 표본공간을 나누는 함수다 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '확률변수는 결과마다 수를 하나 붙이는 함수다. 값이 같은 결과를 모으면 표본공간의 분할이 생긴다', { cls: 'ink bold', size: 'sm' }));

    const outs = ['HHH', 'HHT', 'HTH', 'HTT', 'THH', 'THT', 'TTH', 'TTT'];
    const C = o => [...o].filter(c => c === 'H').length;
    const M = o => (o === 'HHH' || o === 'TTT' ? 1 : 0);

    // C 로 나눈 분할
    const gy = 64;
    g.push(txt(36, gy - 8, 'C = 앞면의 개수 — 블록 네 개', { cls: 'ink2', size: 'sm' }));
    const groupsC = [0, 1, 2, 3].map(v => outs.filter(o => C(o) === v));
    let cx = 36;
    groupsC.forEach((grp, v) => {
        const w = 24 + grp.length * 52;
        g.push(box(cx, gy, w, 44, { stroke: C1, sw: 1.4, rx: 5 }));
        grp.forEach((o, i) => g.push(txt(cx + 12 + i * 52 + 22, gy + 28, o, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
        g.push(txt(cx + w / 2, gy + 60, `C = ${v}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx + w / 2, gy + 78, `Pr = ${grp.length}/8`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        cx += w + 10;
    });

    // M 으로 나눈 분할
    const my = 194;
    g.push(txt(36, my - 8, 'M = 세 동전이 모두 같으면 1, 아니면 0 — 지시확률변수', { cls: 'ink2', size: 'sm' }));
    const groupsM = [1, 0].map(v => outs.filter(o => M(o) === v));
    cx = 36;
    groupsM.forEach((grp, k) => {
        const w = 24 + grp.length * 52;
        g.push(box(cx, my, w, 44, { fill: k === 0 ? C2 : 'none', op: 0.18, stroke: C2, sw: 1.4, rx: 5 }));
        grp.forEach((o, i) => g.push(txt(cx + 12 + i * 52 + 22, my + 28, o, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
        g.push(txt(cx + w / 2, my + 60, `M = ${1 - k}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx + w / 2, my + 78, `Pr = ${grp.length}/8`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        cx += w + 10;
    });

    g.push(ln([[20, 288], [W - 20, 288]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 308, 'C 와 M 은 독립이 아니다 — Pr[C = 2 ∩ M = 1] = 0 인데 Pr[C = 2]·Pr[M = 1] = (3/8)(1/4) ≠ 0 이다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-r-randvar-partition',
        svg: svg({
            width: W, height: H,
            title: '확률변수가 만드는 표본공간의 분할',
            desc: '동전 세 개의 결과 여덟 개를 앞면 개수로, 그리고 모두 같은지로 나눈 두 분할',
            body: g.join(''),
        }),
    };
})());

/* 두 주사위 합의 pmf 와 cdf */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '같은 확률변수를 두 방식으로 그린다 — 값마다의 확률(pmf)과 그 이하일 확률(cdf)', { cls: 'ink bold', size: 'sm' }));

    const pmf = [];
    for (let s = 2; s <= 12; s += 1) pmf.push((6 - Math.abs(7 - s)) / 36);
    const labels = [];
    for (let s = 2; s <= 12; s += 1) labels.push(s);

    // pmf
    g.push(txt(38, 56, 'pmf — PDF~T(x) = Pr[T = x]', { cls: 'ink2', size: 'sm' }));
    g.push(bars(44, 200, 20, 6, pmf.map(v => v / (6 / 36)), labels, 116, { hl: [5] }));
    g.push(txt(30, 200 - 116, '6/36', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 200 - 58, '3/36', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 204, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(44 + 5 * 26 + 10, 200 - 124, '합 7 이 가장 흔하다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(44, 232, '막대 넓이의 합이 1 이다', { cls: 'ink2', size: 'sm' }));

    // cdf
    const cdf = [];
    let acc = 0;
    for (const p of pmf) { acc += p; cdf.push(acc); }
    g.push(txt(372, 56, 'cdf — CDF~T(x) = Pr[T ≤ x]', { cls: 'ink2', size: 'sm' }));
    g.push(bars(378, 200, 20, 6, cdf, labels, 116, { col: C3 }));
    g.push(txt(366, 200 - 116, '1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 200 - 58, '1/2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 204, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(378, 232, '왼쪽 막대들을 차례로 쌓은 것이다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 258], [W - 20, 258]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 280, '두 그림에는 표본공간이 없다. 그래서 서로 다른 실험의 확률변수가 같은 pmf 를 가질 수 있다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 302, '분포에 이름이 붙는 것이 그 때문이다 — 베르누이, 균등, 이항, 기하.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-dice-pmf-cdf',
        svg: svg({
            width: W, height: H,
            title: '두 주사위 합의 pmf 와 cdf',
            desc: '주사위 두 개의 합의 확률질량함수와 누적분포함수를 나란히 그린 막대그래프',
            body: g.join(''),
        }),
    };
})());

/* 이항분포와 꼬리 */
add((() => {
    const W = 672;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '이항분포 — 독립인 동전 n 번 가운데 앞면이 k 번일 확률. 봉우리에서 멀어지면 급히 작아진다', { cls: 'ink bold', size: 'sm' }));

    const n = 20;
    const mk = (p) => {
        const out = [];
        for (let k = 0; k <= n; k += 1) out.push(binom(n, k) * p ** k * (1 - p) ** (n - k));
        return out;
    };
    const f1 = mk(0.5);
    const f2 = mk(0.75);
    const hMax = 116;
    const scale = 0.2;

    g.push(txt(38, 54, 'p = 1/2 — 봉우리는 k = 10', { cls: 'ink2', size: 'sm' }));
    g.push(bars(42, 194, 11, 3, f1.map(v => v / scale), null, hMax, { hl: [0, 1, 2, 3, 4, 5, 16, 17, 18, 19, 20] }));
    for (const k of [0, 5, 10, 15, 20]) g.push(txt(42 + k * 14 + 5.5, 210, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(360, 54, 'p = 3/4 — 봉우리는 k = 15', { cls: 'ink2', size: 'sm' }));
    g.push(bars(364, 194, 11, 3, f2.map(v => v / scale), null, hMax, { col: C3, hl: [] }));
    for (const k of [0, 5, 10, 15, 20]) g.push(txt(364 + k * 14 + 5.5, 210, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(42, 240, '강조한 부분이 꼬리(tail)다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(42, 260, '100 번 던져 앞면이 25 번 이하일 확률은 300 만 분의 1 미만이다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 276], [W - 20, 276]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 296, '컴퓨터과학의 확률 분석은 대개 이 꼬리를 위에서 잡는 일이다 — 나쁜 일이 일어날 확률을 재는 것이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 314, '봉우리의 높이는 스털링 근사로 1/√(2πnpq) 정도임을 보일 수 있다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-binomial-tails',
        svg: svg({
            width: W, height: H,
            title: '이항분포의 봉우리와 꼬리',
            desc: 'n = 20 에서 p = 1/2 와 p = 3/4 인 이항분포의 확률질량함수와 양쪽 꼬리',
            body: g.join(''),
        }),
    };
})());

/* 숫자 게임 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '반정수 x 를 고르고, 엿본 수 T 가 x 보다 크면 ‘이것이 큰 쪽’, 작으면 ‘다른 쪽이 큰 쪽’ 이라 답한다', { cls: 'ink bold', size: 'sm' }));

    const root = {
        children: [
            {
                edge: 'L/n', name: '너무 작다',
                children: [
                    { edge: '1/2', peek: 'T = H', out: '이긴다', mark: true, prob: 'L/2n' },
                    { edge: '1/2', peek: 'T = L', out: '진다', prob: 'L/2n' },
                ],
            },
            {
                edge: '(H−L)/n', name: '두 수 사이',
                children: [
                    { edge: '1/2', peek: 'T = H', out: '이긴다', mark: true, prob: '(H−L)/2n' },
                    { edge: '1/2', peek: 'T = L', out: '이긴다', mark: true, prob: '(H−L)/2n' },
                ],
            },
            {
                edge: '(n−H)/n', name: '너무 크다',
                children: [
                    { edge: '1/2', peek: 'T = H', out: '진다', prob: '(n−H)/2n' },
                    { edge: '1/2', peek: 'T = L', out: '이긴다', mark: true, prob: '(n−H)/2n' },
                ],
            },
        ],
    };
    const t = drawTree(root, { x0: 58, y0: 88, colW: 168, rowH: 30 });
    g.push(t.body);
    g.push(txt(58, 60, 'x 를 고른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(226, 60, 'x 는 어디에 떨어졌나', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(leafCol(t.leaves, 430, t.leaves.map(l => l.peek), { head: '엿본 수', headY: 60 }));
    g.push(leafCol(t.leaves, 508, t.leaves.map(l => l.out), { head: '결과', headY: 60 }));
    g.push(leafCol(t.leaves, 596, t.leaves.map(l => l.prob), { head: '확률', headY: 60 }));

    g.push(box(30, 256, 600, 58, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(txt(330, 280, 'Pr[이긴다] = 1/2 + (H − L)/2n ≥ 1/2 + 1/2n', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(330, 302, 'x 가 두 수 사이에 떨어지면 반드시 이기고, 아니면 반반이다 — 손해가 없는 도박이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-numbers-tree',
        svg: svg({
            width: W, height: H,
            title: '숫자 게임의 나무 그림',
            desc: '반정수 x 를 무작위로 골라 엿본 수와 견주는 전략이 언제나 1/2 보다 큰 확률로 이기는 것을 보이는 나무',
            body: g.join(''),
        }),
    };
})());

/* 지시확률변수의 합 — 모자 되돌려주기 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '어려운 확률변수를 지시확률변수의 합으로 쪼갠다 — 선형성은 독립을 요구하지 않는다', { cls: 'ink bold', size: 'sm' }));

    // 왼쪽: 다섯 사람과 모자
    const n = 5;
    const perm = [3, 2, 5, 1, 4]; // 사람 i 가 받은 모자
    const px0 = 50;
    const py0 = 66;
    for (let i = 0; i < n; i += 1) {
        const y = py0 + i * 34;
        const own = perm[i] === i + 1;
        g.push(box(px0, y, 46, 26, { stroke: CK, sw: 1.2, rx: 3 }));
        g.push(txt(px0 + 23, y + 18, `${i + 1}번`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(arw(px0 + 50, y + 13, px0 + 96, y + 13, { cls: own ? 's2' : 'ark', width: own ? 2 : 1.3 }));
        g.push(box(px0 + 100, y, 52, 26, { fill: own ? C2 : 'none', op: 0.2, stroke: own ? C2 : CK, sw: own ? 1.6 : 1.2, rx: 3 }));
        g.push(txt(px0 + 126, y + 18, `모자 ${perm[i]}`, { anchor: 'middle', cls: own ? 'ink bold' : 'ink2', size: 'sm' }));
        g.push(txt(px0 + 164, y + 18, own ? `G~${i + 1} = 1` : `G~${i + 1} = 0`, { cls: own ? 'ink bold' : 'ink2', size: 'sm' }));
    }
    g.push(txt(px0, py0 + n * 34 + 16, 'G = G~1 + G~2 + ⋯ + G~n — 이 경우 G = 1', { cls: 'ink2', size: 'sm' }));

    const bx = 330;
    g.push(panel(bx, 60, 300, 92, 'G~i 들은 독립이 아니다', ''));
    g.push(txt(bx + 14, 100, 'n−1 명이 제 모자를 받으면 마지막 사람도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 120, '반드시 제 모자를 받는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 142, '그래도 선형성은 그대로 쓸 수 있다', { cls: 'ink bold', size: 'sm' }));

    g.push(box(bx, 166, 300, 116, { stroke: C2, sw: 1.6, rx: 6 }));
    g.push(txt(bx + 150, 192, 'Ex[G~i] = Pr[G~i = 1] = 1/n', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 150, 220, 'Ex[G] = n · (1/n) = 1', { anchor: 'middle', cls: 'bold' }).replace('class="ink bold"', `class="bold" fill="${C2}"`));
    g.push(txt(bx + 150, 248, '사람이 몇 명이든 평균 한 사람이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 150, 268, '제 모자를 돌려받는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 298], [W - 20, 298]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 316, 'G 의 분포는 계산하지 않았다. 알아낸 것은 각 지시변수의 확률 하나뿐이고 그것으로 충분했다.', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'mcs-r-linearity-hats',
        svg: svg({
            width: W, height: H,
            title: '모자 되돌려주기와 기댓값의 선형성',
            desc: '다섯 사람이 모자를 무작위로 받는 그림과, 지시확률변수의 합으로 기댓값이 1 임을 얻는 계산',
            body: g.join(''),
        }),
    };
})());

/* 뒤집힘의 개수 */
add((() => {
    const W = 660;
    const H = 330;
    const g = [];
    g.push(txt(20, 24, '뒤집힘(inversion) — 앞자리가 뒷자리보다 큰 쌍. 정렬 알고리즘이 옮겨야 하는 횟수와 직결된다', { cls: 'ink bold', size: 'sm' }));

    const a = [3, 1, 5, 2, 4];
    const cw = 44;
    const gx = 60;
    const gy = 62;
    a.forEach((v, i) => {
        g.push(box(gx + i * cw, gy, cw, 32, { stroke: CK, sw: 1.3, rx: 3 }));
        g.push(txt(gx + i * cw + cw / 2, gy + 22, String(v), { anchor: 'middle', cls: 'ink' }));
        g.push(txt(gx + i * cw + cw / 2, gy - 8, `${i + 1}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    // 뒤집힌 쌍을 호로 잇는다
    const pairs = [];
    for (let i = 0; i < a.length; i += 1) for (let j = i + 1; j < a.length; j += 1) if (a[i] > a[j]) pairs.push([i, j]);
    pairs.forEach(([i, j], k) => {
        const x1 = gx + i * cw + cw / 2;
        const x2 = gx + j * cw + cw / 2;
        const h = 22 + k * 15;
        g.push(`<path d="M${r2(x1)} ${gy + 32} C ${r2(x1)} ${r2(gy + 32 + h)}, ${r2(x2)} ${r2(gy + 32 + h)}, ${r2(x2)} ${gy + 32}" fill="none" stroke="${C2}" stroke-width="1.5"/>`);
    });
    g.push(txt(gx, gy + 140, `뒤집힘이 ${pairs.length} 개 — (3,1) (3,2) (5,2) (5,4)`, { cls: 'ink bold', size: 'sm' }));

    const bx = 344;
    g.push(panel(bx, 52, 292, 128, '쌍마다 지시변수를 붙인다', ''));
    g.push(txt(bx + 14, 92, 'X~{ij} = 1 ⟺ i 자리가 j 자리보다 크다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 114, '두 값 가운데 어느 쪽이 앞에 오는지는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 134, '반반이므로 Pr[X~{ij} = 1] = 1/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 14, 162, '쌍은 C(n,2) 개다', { cls: 'ink bold', size: 'sm' }));

    g.push(box(bx, 196, 292, 88, { stroke: C2, sw: 1.6, rx: 6 }));
    g.push(txt(bx + 146, 224, 'Ex[뒤집힘] = C(n,2)/2 = n(n−1)/4', { anchor: 'middle', cls: 'bold' }).replace('class="ink bold"', `class="bold" fill="${C2}"`));
    g.push(txt(bx + 146, 252, 'n = 5 이면 5 개. 예의 4 개는 그 근처다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 146, 272, '지시변수들은 독립이 아니다 — 상관없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 300], [W - 20, 300]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 320, '이 값이 삽입 정렬의 평균 교환 횟수다. 분포를 구하려면 훨씬 어렵지만 평균은 두 줄로 나온다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-inversions',
        svg: svg({
            width: W, height: H,
            title: '뒤집힘의 개수의 기댓값',
            desc: '순열 3 1 5 2 4 의 뒤집힌 쌍을 호로 표시한 그림과 쌍마다 지시변수를 붙여 기댓값을 얻는 계산',
            body: g.join(''),
        }),
    };
})());

/* 쿠폰 수집가 */
add((() => {
    const W = 660;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '쿠폰 수집가 — n 종류를 모두 모으기까지 몇 번 사야 하는가. 구간으로 잘라 각각의 평균을 더한다', { cls: 'ink bold', size: 'sm' }));

    const segs = [
        { k: 0, items: ['파랑'], p: 'n/n' },
        { k: 1, items: ['초록'], p: 'n/(n−1)' },
        { k: 2, items: ['초록', '빨강'], p: 'n/(n−2)' },
        { k: 3, items: ['파랑', '주황'], p: 'n/(n−3)' },
        { k: 4, items: ['파랑', '주황', '회색'], p: 'n/(n−4)' },
    ];
    let x = 44;
    const y = 66;
    segs.forEach((s, i) => {
        const w = s.items.length * 56 + 12;
        g.push(box(x, y, w, 40, { fill: i % 2 ? C1 : C3, op: 0.16, stroke: i % 2 ? C1 : C3, sw: 1.3, rx: 4 }));
        s.items.forEach((it, j) => g.push(txt(x + 6 + j * 56 + 28, y + 25, it, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
        g.push(txt(x + w / 2, y - 8, `X~${s.k}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + w / 2, y + 58, `평균 ${s.p}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        x += w + 8;
    });
    g.push(txt(44, y + 92, '구간은 새로운 종류를 얻는 순간 끝난다. k 종류를 가졌을 때 새 것이 나올 확률은 (n−k)/n 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(44, y + 112, '그래서 평균 시간이 n/(n−k) 다 — 평균 고장 시간 규칙이다.', { cls: 'ink2', size: 'sm' }));

    g.push(box(44, 200, 574, 62, { stroke: C2, sw: 1.5, rx: 5 }));
    g.push(txt(331, 226, 'Ex[T] = n/n + n/(n−1) + ⋯ + n/2 + n/1 = n·H~n ≈ n ln n', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(331, 248, '주사위 여섯 눈을 모두 보려면 평균 6·H~6 ≈ 14.7 번, 생일 365 가지는 약 2365 명', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 278], [W - 20, 278]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 300, '16장의 조화수가 여기서 다시 나온다. 마지막 한 종류를 기다리는 데 평균 n 번이 드는 것이 합의 대부분이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-coupon-segments',
        svg: svg({
            width: W, height: H,
            title: '쿠폰 수집가 문제를 구간으로 자르기',
            desc: '수집 과정을 새 종류가 나올 때마다 잘라 각 구간의 평균 길이를 더해 n 곱하기 조화수를 얻는 그림',
            body: g.join(''),
        }),
    };
})());

/* 배팅 두 배 — 무한 선형성이 깨지는 곳 */
add((() => {
    const W = 640;
    const H = 320;
    const g = [];
    g.push(txt(20, 24, '판돈을 두 배로 올리는 전략 — 각 판의 기댓값은 0 인데 전체 기댓값은 10 이다', { cls: 'ink bold', size: 'sm' }));

    const f = frame({ xRange: [0.5, 8.5], yRange: [0, 16], box: { x: 64, y: 62, w: 300, h: 160 } });
    g.push(f.axes({ xLabel: 'n 번째 판', yLabel: '', xTicks: [1, 2, 3, 4, 5, 6, 7, 8], yTicks: [0, 5, 10, 15] }));
    // Ex[|B_n|] = 10 인 수평선
    g.push(f.line([[0.5, 10], [8.5, 10]], { cls: 's2' }));
    for (let k = 1; k <= 8; k += 1) g.push(f.dot([k, 10], { cls: 'f2', r: 4 }));
    g.push(f.label([4.6, 10], 'Ex[|B~n|] = 10 — 판마다 같다', { dy: -12, cls: 'ink bold', size: 'sm' }));
    g.push(f.line([[0.5, 0], [8.5, 0]], { cls: 's1', dash: '5 4' }));
    g.push(f.label([6.4, 0], 'Ex[B~n] = 0 — 판마다 공정하다', { dy: -10, anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(392, 74, '판돈 10·2^(n−1) 달러를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 94, '확률 2^(−(n−1)) 로 건다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 122, '곱하면 언제나 10 이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(392, 150, 'Σ Ex[|B~n|] = 10 + 10 + ⋯', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 170, '이 발산한다', { cls: 'sm bold' }).replace('class="ink sm bold"', `class="sm bold" fill="${C2}"`));
    g.push(txt(392, 198, '무한 선형성의 조건이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 216, '깨진 것이다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 248], [W - 20, 248]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 268, '선형성을 무한히 많은 항에 쓰려면 Σ Ex[|R~i|] 가 수렴해야 한다. 여기서는 그렇지 않으므로 두 값이 다르다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 290, '현실에서 이 전략이 안 되는 까닭도 같다 — 무한한 자금이 있어야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 310, '자금이 k 판 분량뿐이면 합이 유한해지고 기댓값은 다시 0(공정한 판) 또는 음수가 된다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-r-bet-doubling',
        svg: svg({
            width: W, height: H,
            title: '배팅 두 배 전략과 무한 선형성',
            desc: '판마다 절댓값 기댓값이 5 로 일정해 급수가 발산하므로 무한 개 항에 선형성을 쓸 수 없음을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

export default figures;
