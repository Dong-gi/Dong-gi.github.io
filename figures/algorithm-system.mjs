/**
 * 알고리즘 14장(확률적 자료구조와 공간 색인) · 15장(병행성과 동기화) ·
 * 16장(암호와 보안 프로토콜)의 그림.
 *
 * 이름은 모두 `alg-s-` 로 시작한다(담당 D 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `h~1` 표기를, 나머지는 유니코드(≤ ≥ → ⊕ ⌈⌉ ² ⁿ ₀ ₁ ₂ α ε δ)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 파일이 있는 이유.
 *   이 블록의 세 장은 ‘말로는 전달되지 않는 것’ 이 유난히 많다.
 *   블룸 필터는 비트 배열을 보지 않으면 거짓 양성이 왜 한쪽으로만 생기는지 알 수 없고(14장),
 *   경쟁 상태는 두 스레드의 단계가 엇갈리는 그림 없이는 ‘운이 나쁘면 틀린다’ 로만 남으며(15장),
 *   ECB 모드가 왜 안 되는지는 패턴이 남은 그림 한 장이 열 문단보다 낫다(16장).
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
 * 화소 좌표 소도구 — algorithm-basic.mjs · algorithm-design.mjs 와 같은 규약.
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
function tbox(x, y, w, h, lines, { stroke = CK, fill = 'none', op = 0.16, cls = 'ink', sw = 1.3, size = 'sm', rx = 4, dash } = {}) {
    const arr = Array.isArray(lines) ? lines : [lines];
    const lh = size === 'sm' ? 15 : 17;
    const y0 = y + h / 2 - ((arr.length - 1) * lh) / 2 + (size === 'sm' ? 4 : 5);
    return box(x, y, w, h, { stroke, fill, op: fill === 'none' ? 1 : op, sw, rx, dash })
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
    if (head) head.forEach((h, c) => g.push(txt(x + c * cw + cw / 2, y - 8, String(h), { anchor: 'middle', cls: 'ink2', size: 'sm' })));
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

/** 원 노드 한 개. */
function tnode(x, y, label, { r = 16, col = null, dim = false, dash, sub = null } = {}) {
    const stroke = col ?? (dim ? CG : CK);
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col ?? 'none'}" fill-opacity="${col ? 0.2 : 0}" stroke="${stroke}" stroke-width="${col ? 2 : 1.4}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`
        + txt(x, y + 4, label, { anchor: 'middle', cls: dim ? 'ink2' : 'ink', size: r < 16 ? 'sm' : undefined })
        + (sub ? txt(x, y + r + 14, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 두 노드의 중심을 잇되 원 반지름만큼 잘라 그린다. */
function tedge(p1, p2, { r1 = 16, r2r = 16, stroke = CK, sw = 1.4, dash, label = null, marker = null } = {}) {
    const dx = p2[0] - p1[0], dy = p2[1] - p1[1];
    const L = Math.hypot(dx, dy) || 1;
    const ux = dx / L, uy = dy / L;
    const a = [p1[0] + ux * r1, p1[1] + uy * r1];
    const b = [p2[0] - ux * r2r, p2[1] - uy * r2r];
    const seg = marker
        ? arw(a[0], a[1], b[0], b[1], { cls: stroke === C1 ? 's1' : stroke === C2 ? 's2' : stroke === C3 ? 's3' : 'ark', width: sw, dash })
        : ln([a, b], { stroke, sw, dash });
    return seg + (label ? txt((a[0] + b[0]) / 2 + (dx > 0 ? 7 : -7), (a[1] + b[1]) / 2 - 3, label, { anchor: dx > 0 ? 'start' : 'end', cls: 'ink2', size: 'sm' }) : '');
}

/** 좌표계 위 곡선을 직접 색을 넣어 그린다. lib 의 curve 는 클래스만 받는다. */
function fcurve(f, fn, { from, to, steps = 240, stroke = C1, sw = 2.2, dash } = {}) {
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

/** 재현 가능한 난수. 그림이 빌드마다 달라지면 안 된다(mulberry32). */
function rng(seed) {
    let a = seed >>> 0;
    return () => {
        a = (a + 0x6D2B79F5) >>> 0;
        let t = a;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

/**
 * 암호문 블록을 칠할 색. 밝은 배경과 어두운 배경 양쪽에서 읽히도록
 * 명도를 중간 대역에 가둔다(다크 모드에서도 그대로 쓴다).
 */
function noiseColor(rand) {
    const h = Math.floor(rand() * 360);
    const s = 12 + Math.floor(rand() * 26);
    const l = 34 + Math.floor(rand() * 34);
    return `hsl(${h},${s}%,${l}%)`;
}

/* ================================================================== *
 * 14장 — 확률적 자료구조와 공간 색인
 * ================================================================== */

/* ---- 14-1. 무엇을 포기하고 무엇을 얻는가 ---- */
add((() => {
    const W = 820, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '주소 1억 개를 담아 ‘이것을 본 적 있는가’ 에 답하려 한다 — 주소 하나가 평균 60바이트라 하자', { anchor: 'middle', cls: 'ink bold' }));

    const bx = 210, bw = 470;
    // 정확한 집합
    g.push(txt(bx - 12, 88, '해시 집합 (5장)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(box(bx, 70, bw, 26, { fill: C1, op: 0.3, stroke: C1, sw: 1.6, rx: 3 }));
    g.push(txt(bx + bw + 12, 88, '약 6 GB', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx, 118, '키를 통째로 저장한다. 틀리는 일이 없고, 원소 목록을 그대로 되돌려 받을 수 있다', { cls: 'ink2', size: 'sm' }));

    // 블룸 필터
    g.push(txt(bx - 12, 174, '블룸 필터', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(box(bx, 156, bw * 0.02, 26, { fill: C2, op: 0.55, stroke: C2, sw: 1.6, rx: 1 }));
    g.push(arw(bx + 24, 169, bx + 12, 169, { cls: 's2', width: 1.4 }));
    g.push(txt(bx + 30, 174, '약 120 MB — 같은 그림에서 이만큼밖에 안 된다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx, 204, '비트만 저장한다. 키는 어디에도 남지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[60, 232], [W - 60, 232]], { stroke: CG, sw: 1 }));

    const cols = [
        ['포기하는 것', ['없는 것을 있다고 할 확률 1%', '원소 목록을 되돌려 받을 수 없다', '한 번 넣은 것을 지울 수 없다'], C2],
        ['그대로 얻는 것', ['있는 것을 없다고 하는 일은 절대 없다', '넣기 · 찾기가 해시 k 번으로 끝난다', '원소 수가 늘어도 크기가 그대로다'], C3],
    ];
    cols.forEach((c, i) => {
        const x = 78 + i * 356;
        g.push(box(x, 248, 330, 74, { stroke: c[2], sw: 1.4, rx: 5 }));
        g.push(txt(x + 12, 266, c[0], { cls: 'ink bold', size: 'sm' }));
        c[1].forEach((s, j) => g.push(txt(x + 12, 285 + j * 15, '· ' + s, { cls: 'ink2', size: 'sm' })));
    });
    return {
        name: 'alg-s-tradeoff-space',
        svg: svg({
            width: W, height: H,
            title: '정확함을 조금 포기하고 공간을 크게 아낀다',
            desc: '같은 질문에 답하는 해시 집합과 블룸 필터의 공간 차이, 그리고 그 대가로 포기하는 것들',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-2. 블룸 필터의 비트 배열 ---- */
add((() => {
    const W = 800, H = 474;
    const g = [];
    g.push(txt(W / 2, 26, '비트 24칸, 해시 3개 — 넣기 두 번과 찾기 두 번', { anchor: 'middle', cls: 'ink bold' }));

    const M = 24, cw = 26, ch = 26, x0 = 106;
    const bits = new Array(M).fill(0);
    const rows = [
        { y: 66, kind: 'put', key: '사과', pos: [2, 9, 17], note: '2 · 9 · 17 번을 1 로 켠다' },
        { y: 162, kind: 'put', key: '바나나', pos: [5, 9, 21], note: '5 · 21 번을 켠다. 9번은 이미 1 이었으므로 그대로 둔다' },
        { y: 258, kind: 'get', key: '체리', pos: [3, 11, 17], note: '3번이 0 이다 → 넣은 적이 없는 것이 확실하다', ok: false },
        { y: 354, kind: 'get', key: '두리안', pos: [2, 5, 21], note: '셋 다 1 이다 → 있다고 답한다. 그런데 넣은 적이 없다 — 거짓 양성', ok: true },
    ];

    rows.forEach((rw) => {
        if (rw.kind === 'put') rw.pos.forEach(p => { bits[p] = 1; });
        const hl = {};
        rw.pos.forEach(p => { hl[p] = rw.kind === 'put' ? C1 : (rw.ok ? C2 : C3); });
        g.push(txt(x0 - 14, rw.y + 18, `${rw.kind === 'put' ? '넣기' : '찾기'}  ${rw.key}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(cells(x0, rw.y, cw, ch, bits.map(b => String(b)), { hl, small: true }));
        // 세 해시가 가리키는 자리 표시
        rw.pos.forEach((p, i) => {
            const px0 = x0 + p * cw + cw / 2;
            g.push(txt(px0, rw.y - 6, `h${['₁', '₂', '₃'][i]}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
        g.push(txt(x0, rw.y + 46, rw.note, { cls: rw.kind === 'get' ? 'ink bold' : 'ink2', size: 'sm' }));
    });

    g.push(ln([[60, 424], [W - 60, 424]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 444, '0 을 하나라도 만나면 없는 것이 확실하고, 모두 1 이면 있을 수도 있다. 이 비대칭이 자료구조의 전부다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 464, '지우려고 비트를 0 으로 되돌리면 그 비트를 함께 쓰던 ‘사과’ 까지 사라진다. 그래서 삭제가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-bloom-bits',
        svg: svg({
            width: W, height: H,
            title: '블룸 필터의 비트 배열',
            desc: '넣을 때는 세 자리를 켜고 찾을 때는 세 자리를 본다. 0 이 하나라도 있으면 없는 것이 확실하다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-3. 거짓 양성률과 최적 k ---- */
add((() => {
    const W = 812, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '해시를 몇 개 쓸 것인가 — 원소 하나당 비트 수 m/n 를 고정하고 k 를 바꿔 본다', { anchor: 'middle', cls: 'ink bold' }));

    const fpr = (k, b) => Math.pow(1 - Math.exp(-k / b), k);
    const lg10 = v => Math.log10(v);
    const f = frame({ xRange: [0, 20], yRange: [-4, 0], box: { x: 96, y: 56, w: 456, h: 218 } });
    g.push(axes2(f, {
        xRange: [0, 20], yRange: [-4, 0],
        xTicks: [1, 4, 8, 12, 16, 20], yTicks: [0, -1, -2, -3, -4],
        xLabel: '해시 개수 k', yLabel: '거짓 양성률',
        fmt: String, yFmt: v => ['100%', '10%', '1%', '0.1%', '0.01%'][-v],
    }));
    for (const t of [-1, -2, -3]) g.push(ln([[f.X(0), f.Y(t)], [f.X(20), f.Y(t)]], { stroke: CG, sw: 1, dash: '3 4' }));

    const series = [
        { b: 4, col: C2, name: 'm/n = 4' },
        { b: 8, col: C1, name: 'm/n = 8' },
        { b: 16, col: C3, name: 'm/n = 16' },
    ];
    series.forEach((s) => {
        const pts = [];
        for (let k = 0.6; k <= 20; k += 0.1) pts.push([f.X(k), f.Y(Math.max(-4, lg10(fpr(k, s.b))))]);
        g.push(ln(pts, { stroke: s.col, sw: 2.3 }));
        const kb = s.b * Math.LN2;
        g.push(pdot(f.X(kb), f.Y(lg10(fpr(kb, s.b))), s.col, 4.5));
    });
    g.push(txt(f.X(3.2) - 4, f.Y(lg10(fpr(2.77, 4))) - 12, 'm/n = 4', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(7.5), f.Y(lg10(fpr(5.55, 8))) + 22, 'm/n = 8', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(14.5), f.Y(lg10(fpr(11.1, 16))) + 22, 'm/n = 16', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const info = [
        ['m/n', '최적 k', '거짓 양성률'],
        ['4', '3', '약 14.7%'],
        ['8', '6', '약 2.2%'],
        ['16', '11', '약 0.05%'],
    ];
    const ix = 606;
    g.push(txt(ix, 74, '최적 k = (m/n) × ln 2', { cls: 'ink bold', size: 'sm' }));
    info.forEach((row, r) => {
        row.forEach((v, c) => {
            g.push(txt(ix + [4, 62, 148][c], 100 + r * 22, v, { cls: r === 0 ? 'ink bold' : 'ink2', size: 'sm', anchor: c === 0 ? 'start' : 'middle' }));
        });
    });
    g.push(ln([[ix - 4, 106], [ix + 196, 106]], { stroke: CG, sw: 1 }));

    g.push(txt(W / 2, H - 58, '점이 각 곡선의 바닥이다. k 가 너무 작으면 판별이 거칠고, 너무 크면 비트를 다 켜 버려 오히려 나빠진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, H - 36, '바닥에서는 비트 배열의 정확히 절반이 켜져 있고, 그때 거짓 양성률이 1/2 의 k 제곱이 된다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, H - 14, '원소 하나당 비트를 두 배로 늘리면 오류율은 제곱으로 줄어든다 — 값에 비해 얻는 것이 크다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-bloom-fpr',
        svg: svg({
            width: W, height: H,
            title: '블룸 필터의 거짓 양성률과 최적 해시 개수',
            desc: '원소당 비트 수를 고정했을 때 해시 개수 k 에 따른 거짓 양성률과 그 최솟값',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-4. 쿠쿠 해싱의 밀어내기 ---- */
add((() => {
    const W = 852, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '자리가 차 있으면 밀어낸다 — 뻐꾸기가 남의 알을 밀어내듯이', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 52, ch = 38, x0 = 62;
    const y1 = 104, y2 = 246;
    const cx = i => x0 + i * cw + cw / 2;
    g.push(txt(x0, y1 - 26, 'T₁ — 해시 h₁ 이 정하는 자리', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, y2 - 12, 'T₂ — 해시 h₂ 가 정하는 자리', { cls: 'ink bold', size: 'sm' }));
    g.push(cells(x0, y1, cw, ch, ['', '별', '', '새', '', '', '', ''], { hl: { 3: C1 }, idx: [0, 1, 2, 3, 4, 5, 6, 7] }));
    g.push(box(x0 + 6 * cw, y1, cw, ch, { stroke: C3, sw: 1.9, rx: 2, dash: '5 4' }));
    g.push(cells(x0, y2, cw, ch, ['', '달', '', '', '', '해', '', ''], { hl: { 5: C2 }, idx: [0, 1, 2, 3, 4, 5, 6, 7] }));

    // ① 나비가 T1[3] 으로 들어온다
    g.push(arw(cx(3), 66, cx(3), y1 - 6, { cls: 's1', width: 2 }));
    g.push(txt(cx(3), 58, '나비', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx(3) + 16, 58, '①', { cls: 'ink bold', size: 'sm' }));
    // ② ‘새’ 를 T2[5] 로
    g.push(curvePath(`M${cx(3) - 8} ${y1 + ch + 20} Q${cx(4) - 6} ${y2 - 46} ${cx(5) - 12} ${y2 - 6}`, { stroke: C2, sw: 2, marker: 'ar2' }));
    g.push(txt(cx(3) - 18, y2 - 54, '②', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    // ③ ‘해’ 를 T1[6] 으로 — 비어 있다
    g.push(curvePath(`M${cx(5) + 22} ${y2 - 2} Q${cx(6) + 44} ${y2 - 60} ${cx(6) + 6} ${y1 + ch + 8}`, { stroke: C3, sw: 2, marker: 'ar3' }));
    g.push(txt(cx(6) - 14, y2 - 52, '③', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const sx = 486;
    const steps = [
        ['① 나비를 넣는다. h₁(나비) = 3 인데', '그 자리에 ‘새’ 가 앉아 있다.', '나비를 앉히고 새를 일으킨다', C1, 92],
        ['② 새의 다른 자리는 h₂(새) = 5 다.', '거기에는 ‘해’ 가 있다.', '새를 앉히고 해를 일으킨다', C2, 186],
        ['③ 해의 다른 자리는 h₁(해) = 6 이고', '비어 있다. 여기서 멈춘다', '', C3, 280],
    ];
    steps.forEach((s) => {
        g.push(ln([[sx - 12, s[4] - 12], [sx - 12, s[4] + (s[2] ? 40 : 22)]], { stroke: s[3], sw: 2.4 }));
        g.push(txt(sx, s[4], s[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(sx, s[4] + 17, s[1], { cls: 'ink2', size: 'sm' }));
        if (s[2]) g.push(txt(sx, s[4] + 34, s[2], { cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[52, 330], [W - 52, 330]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 350, '찾기는 언제나 두 자리만 본다. T₁[h₁(x)] 와 T₂[h₂(x)] 뿐이므로 최악에도 상수 시간이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 370, '밀어내기가 고리를 돌아 끝나지 않을 수 있다. 정해진 횟수를 넘으면 표를 키우고 전부 다시 넣는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 388, '표의 절반 정도까지만 채우면 그 일이 드물다 — 남는 자리를 비워 두는 것이 쿠쿠 해싱이 치르는 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-cuckoo-kick',
        svg: svg({
            width: W, height: H,
            title: '쿠쿠 해싱의 밀어내기',
            desc: '키를 넣을 자리가 차 있으면 그 키를 다른 표의 제 자리로 밀어내고, 그것이 연쇄로 일어난다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-5. HyperLogLog — 선행 0 의 개수로 크기를 짐작한다 ---- */
add((() => {
    const W = 828, H = 380;
    const g = [];
    g.push(txt(W / 2, 26, '해시 값의 앞머리에 0 이 몇 개나 붙어 있는가', { anchor: 'middle', cls: 'ink bold' }));

    const samples = [
        ['가', '1011010…', 0],
        ['나', '0110001…', 1],
        ['다', '1101110…', 0],
        ['라', '0001011…', 3],
        ['마', '0100111…', 1],
        ['바', '1010100…', 0],
    ];
    const bw = 15, bx = 128, by = 62;
    samples.forEach((s, i) => {
        const y = by + i * 30;
        g.push(txt(bx - 14, y + 13, `h(${s[0]})`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        [...s[1]].forEach((ch, j) => {
            if (ch === '…') { g.push(txt(bx + j * bw + 7, y + 13, '…', { anchor: 'middle', cls: 'ink2', size: 'sm' })); return; }
            const lead = j < s[2];
            g.push(box(bx + j * bw, y, bw, 20, { fill: lead ? C2 : 'none', op: lead ? 0.3 : 1, stroke: lead ? C2 : CG, sw: lead ? 1.6 : 1, rx: 1 }));
            g.push(txt(bx + j * bw + bw / 2, y + 14, ch, { anchor: 'middle', cls: lead ? 'ink bold' : 'ink2', size: 'sm' }));
        });
        g.push(txt(bx + 8 * bw + 16, y + 13, `선행 0 이 ${s[2]}개`, { cls: s[2] === 3 ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(bx + 8 * bw + 16, by + 6 * 30 + 10, '여섯 개 중 최대는 3 → 크기를 2⁴ = 16 쯤으로 짐작한다', { cls: 'ink bold', size: 'sm' }));

    const tx = 452, ty = 62;
    g.push(box(tx, ty - 16, 328, 190, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(tx + 14, ty + 4, '왜 그렇게 짐작하는가', { cls: 'ink bold', size: 'sm' }));
    const rows = [
        ['선행 0 이 1개 이상일 확률', '1/2'],
        ['선행 0 이 2개 이상일 확률', '1/4'],
        ['선행 0 이 k개 이상일 확률', '1/2ᵏ'],
    ];
    rows.forEach((r, i) => {
        g.push(txt(tx + 20, ty + 30 + i * 20, r[0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(tx + 250, ty + 30 + i * 20, r[1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(ln([[tx + 14, ty + 100], [tx + 314, ty + 100]], { stroke: CG, sw: 1 }));
    g.push(txt(tx + 20, ty + 122, '선행 0 이 k개인 값을 보려면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 20, ty + 140, '대략 2ᵏ 개를 뽑아 봐야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 20, ty + 158, '거꾸로 읽으면 크기 추정이 된다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[52, 268], [W - 52, 268]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 290, '같은 원소를 몇 번 넣어도 해시 값이 같으므로 최대 선행 0 이 변하지 않는다 — 중복이 저절로 무시된다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 312, '이 추정 하나만으로는 못 쓴다. 운 나쁘게 앞머리가 긴 값 하나가 걸리면 추정이 통째로 두 배씩 뛴다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 332, '게다가 짐작할 수 있는 값이 1, 2, 4, 8, 16 … 뿐이라 그 사이를 표현하지 못한다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 356, '해결책은 하나뿐인 관측을 여럿으로 쪼개는 것이다 — 다음 그림', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-hll-zeros',
        svg: svg({
            width: W, height: H,
            title: '선행 0 의 개수로 집합 크기를 짐작한다',
            desc: '해시 값의 앞머리에 붙은 0 의 최대 개수가 k 이면 원소 수를 2 의 k+1 제곱쯤으로 짐작한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-6. HyperLogLog — 버킷과 조화평균 ---- */
add((() => {
    const W = 836, H = 424;
    const g = [];
    g.push(txt(W / 2, 26, '해시 하나를 둘로 쪼갠다 — 앞은 버킷 번호, 뒤는 선행 0 세는 자리', { anchor: 'middle', cls: 'ink bold' }));

    // 해시 값의 분할
    const hx = 150, hy = 56;
    g.push(box(hx, hy, 150, 30, { fill: C1, op: 0.25, stroke: C1, sw: 1.6, rx: 3 }));
    g.push(txt(hx + 75, hy + 20, '앞 14비트', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(box(hx + 150, hy, 380, 30, { fill: C2, op: 0.2, stroke: C2, sw: 1.6, rx: 3 }));
    g.push(txt(hx + 340, hy + 20, '나머지 50비트', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(hx - 12, hy + 20, '해시 64비트', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(hx + 75, hy + 48, '버킷 번호 (2¹⁴ = 16384 개)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(hx + 340, hy + 48, '여기서 선행 0 을 세어 그 버킷의 최댓값만 남긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 버킷들
    const cw = 46, ch = 36, bx = 150, by = 168;
    const vals = [3, 5, 2, 4, 3, 11, 2, 4, 3, 5, 4, 2];
    g.push(cells(bx, by, cw, ch, vals.map(String), { hl: { 5: C2 }, idx: vals.map((_, i) => i), small: true }));
    g.push(txt(bx - 12, by + 22, '버킷별 최대', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(arw(bx + 5 * cw + cw / 2, by - 24, bx + 5 * cw + cw / 2, by - 6, { cls: 's2', width: 1.6 }));
    g.push(txt(bx + 5 * cw + cw / 2, by - 32, '운 나쁘게 걸린 값 하나', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 두 평균 비교
    const arith = vals.reduce((a, b) => a + Math.pow(2, b), 0) / vals.length;
    const harm = vals.length / vals.reduce((a, b) => a + Math.pow(2, -b), 0);
    const py = 254;
    const items = [
        ['산술평균으로 묶으면', `버킷마다의 짐작 2ᵏ 을 더해 나눈다 → 약 ${Math.round(arith)}`, '큰 값 하나가 전체를 끌어올린다', C2],
        ['조화평균으로 묶으면', `역수를 더해 나눈 뒤 뒤집는다 → 약 ${Math.round(harm)}`, '큰 값의 역수는 작아서 영향이 작다', C3],
    ];
    items.forEach((it, i) => {
        const x = 74 + i * 356;
        g.push(box(x, py, 330, 84, { stroke: it[3], sw: 1.4, rx: 5 }));
        g.push(txt(x + 14, py + 22, it[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + 14, py + 44, it[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(x + 14, py + 64, it[2], { cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[52, 360], [W - 52, 360]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 382, '버킷이 많을수록 흔들림이 줄어든다. 표준 오차는 버킷 개수의 제곱근에 반비례해 1.04 ÷ √(버킷 수) 다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 404, '버킷 16384 개면 오차 약 0.81%. 버킷마다 6비트만 있으면 되므로 전부 합쳐 12 KB 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-hll-buckets',
        svg: svg({
            width: W, height: H,
            title: 'HyperLogLog 의 버킷과 조화평균',
            desc: '해시를 버킷 번호와 나머지로 쪼개 여러 번 관측하고, 큰 값 하나에 끌려가지 않도록 조화평균으로 묶는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-7. Count-Min Sketch ---- */
add((() => {
    const W = 848, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '빈도를 세는 표 — 행마다 다른 해시로 자리를 정하고, 읽을 때는 가장 작은 것을 고른다', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 46, chh = 34, gx = 120, gy = 76;
    const rows = [
        [3, 41, 8, 12, 5, 27, 9, 16, 4, 22],
        [17, 6, 30, 9, 44, 8, 13, 25, 7, 11],
        [8, 19, 5, 33, 10, 21, 40, 3, 14, 26],
        [24, 12, 15, 7, 18, 9, 6, 38, 20, 31],
    ];
    const hitApple = [3, 5, 0, 5];      // 사과가 가리키는 열 — 값 12, 2, 8, 9
    const hitPear = [1, 4, 6, 7];       // 배가 가리키는 열 — 값 41, 44, 40, 38
    const hl = {};
    hitApple.forEach((c, r) => { hl[`${r},${c}`] = C1; });
    hitPear.forEach((c, r) => { hl[`${r},${c}`] = C2; });
    g.push(grid(gx, gy, cw, chh, rows.map(r => r.map(String)), {
        hl, side: ['h₁', 'h₂', 'h₃', 'h₄'], head: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
    }));
    g.push(txt(gx - 10, gy - 30, '해시', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(gx + 5 * cw, gy - 30, '카운터 열 w 개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(gx - 56, gy + 2 * chh, '행 d 개', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const rx = gx + 10 * cw + 26;
    const info = [
        ['‘사과’ 를 읽는다', `${hitApple.map((c, r) => rows[r][c]).join(' · ')} 중 최소 → ${Math.min(...hitApple.map((c, r) => rows[r][c]))}`, '참값도 8 이었다', C1],
        ['‘배’ 를 읽는다', `${hitPear.map((c, r) => rows[r][c]).join(' · ')} 중 최소 → ${Math.min(...hitPear.map((c, r) => rows[r][c]))}`, '참값은 30. 남의 몫이 얹혔다', C2],
    ];
    info.forEach((it, i) => {
        const y = gy + i * 82;
        g.push(ln([[rx - 10, y - 6], [rx - 10, y + 54]], { stroke: it[3], sw: 2.4 }));
        g.push(txt(rx, y + 8, it[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(rx, y + 28, it[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(rx, y + 46, it[2], { cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[52, 254], [W - 52, 254]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 276, '칸 하나에는 그 칸으로 해시된 모든 원소의 몫이 함께 쌓인다. 그러니 어느 칸이든 참값보다 작을 수 없다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 298, '곧 오차가 한쪽으로만 생긴다 — 과대 추정은 있어도 과소 추정은 없다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 318, '가장 작은 칸을 고르는 것은 ‘남의 몫이 가장 적게 얹힌 증언’ 을 고르는 일이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(box(160, 336, 520, 50, { stroke: C3, sw: 1.4, rx: 5 }));
    g.push(txt(W / 2, 356, '열을 늘리면 얹히는 양이 줄고, 행을 늘리면 운 나쁜 경우가 줄어든다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 376, 'w = ⌈e/ε⌉ 열과 d = ⌈ln(1/δ)⌉ 행이면 오차가 εN 이하임을 확률 1−δ 로 보장한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-cms-grid',
        svg: svg({
            width: W, height: H,
            title: 'Count-Min Sketch 의 표와 최솟값 읽기',
            desc: '행마다 다른 해시로 카운터를 올리고 읽을 때 최솟값을 고른다. 오차는 과대 추정 쪽으로만 생긴다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-8. 공간 채움 곡선 — Z-곡선과 힐베르트 곡선 ---- */
add((() => {
    const W = 828, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '2차원을 1차원으로 줄 세우는 두 가지 방법 (8 × 8 격자)', { anchor: 'middle', cls: 'ink bold' }));

    // Z-곡선(모턴 순서): 짝수 비트가 x, 홀수 비트가 y
    const morton = d => {
        let x = 0, y = 0;
        for (let i = 0; i < 8; i += 1) {
            x |= ((d >> (2 * i)) & 1) << i;
            y |= ((d >> (2 * i + 1)) & 1) << i;
        }
        return [x, y];
    };
    // 힐베르트 곡선: 사분면마다 방향을 뒤집어 이웃을 유지한다
    const hilbert = (order, d) => {
        const nSide = 1 << order;
        let t = d, x = 0, y = 0;
        for (let s = 1; s < nSide; s *= 2) {
            const rx = 1 & (t >> 1);
            const ry = 1 & (t ^ rx);
            if (ry === 0) {
                if (rx === 1) { x = s - 1 - x; y = s - 1 - y; }
                const tmp = x; x = y; y = tmp;
            }
            x += s * rx;
            y += s * ry;
            t = Math.floor(t / 4);
        }
        return [x, y];
    };

    const cell = 30, N = 8;
    const panels = [
        { x: 74, name: 'Z-곡선 — 지오해시가 쓴다', fn: d => morton(d), col: C2 },
        { x: 468, name: '힐베르트 곡선 — 구글 S2 가 쓴다', fn: d => hilbert(3, d), col: C3 },
    ];
    panels.forEach((p) => {
        const gx = p.x, gy = 76;
        for (let i = 0; i <= N; i += 1) {
            g.push(ln([[gx, gy + i * cell], [gx + N * cell, gy + i * cell]], { stroke: CG, sw: 1 }));
            g.push(ln([[gx + i * cell, gy], [gx + i * cell, gy + N * cell]], { stroke: CG, sw: 1 }));
        }
        const pts = [];
        let longest = 0, longIdx = 0;
        for (let d = 0; d < N * N; d += 1) {
            const [cxi, cyi] = p.fn(d);
            pts.push([gx + cxi * cell + cell / 2, gy + cyi * cell + cell / 2]);
            if (d > 0) {
                const L = Math.hypot(pts[d][0] - pts[d - 1][0], pts[d][1] - pts[d - 1][1]);
                if (L > longest) { longest = L; longIdx = d; }
            }
        }
        g.push(ln(pts, { stroke: p.col, sw: 2 }));
        g.push(pdot(pts[0][0], pts[0][1], CI, 4));
        g.push(txt(gx, gy - 12, p.name, { cls: 'ink bold', size: 'sm' }));
        if (longest > cell * 1.6) {
            g.push(ln([pts[longIdx - 1], pts[longIdx]], { stroke: CI, sw: 3 }));
            g.push(txt(gx + N * cell, gy + N * cell + 22, `한 걸음에 ${Math.round(longest / cell)}칸을 건너뛴다`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        } else {
            g.push(txt(gx + N * cell, gy + N * cell + 22, '언제나 옆칸으로만 움직인다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        }
    });

    g.push(ln([[52, 350], [W - 52, 350]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 370, '두 곡선 모두 ‘번호가 가까우면 지도에서도 가깝다’ 를 대체로 지킨다. 그래서 1차원 색인으로 범위 질의를 할 수 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 390, '거꾸로는 아니다. 지도에서 붙어 있어도 번호가 멀 수 있다 — 굵은 선이 그런 자리다. 경계를 넘는 질의는 조각을 여럿 읽어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-space-curves',
        svg: svg({
            width: W, height: H,
            title: 'Z-곡선과 힐베르트 곡선',
            desc: '2차원 격자를 1차원 번호로 줄 세우는 두 곡선. 힐베르트 곡선은 언제나 옆칸으로만 움직인다',
            body: g.join(''),
        }),
    };
})());

/* ---- 14-9. 균등 격자와 쿼드트리 ---- */
add((() => {
    const W = 828, H = 404;
    const g = [];
    g.push(txt(W / 2, 26, '같은 점들을 두 가지로 나눈다 — 자료가 몰려 있을 때', { anchor: 'middle', cls: 'ink bold' }));

    const rand = rng(20260813);
    const gauss = (mx, my, sd) => {
        const u = Math.max(rand(), 1e-9), v = rand();
        const r = Math.sqrt(-2 * Math.log(u));
        return [mx + sd * r * Math.cos(2 * Math.PI * v), my + sd * r * Math.sin(2 * Math.PI * v)];
    };
    const pts = [];
    for (let i = 0; i < 34; i += 1) pts.push(gauss(0.23, 0.74, 0.055));
    for (let i = 0; i < 20; i += 1) pts.push(gauss(0.70, 0.30, 0.05));
    for (let i = 0; i < 6; i += 1) pts.push([rand(), rand()]);
    const inRange = p => p[0] > 0.01 && p[0] < 0.99 && p[1] > 0.01 && p[1] < 0.99;
    const P = pts.filter(inRange);

    const side = 256;
    const draw = (ox, oy) => ([px0, py0]) => [ox + px0 * side, oy + py0 * side];

    // 왼쪽: 균등 격자 8×8
    const lx = 74, ly = 76;
    const mapL = draw(lx, ly);
    const NL = 8;
    const cnt = new Array(NL * NL).fill(0);
    P.forEach((p) => {
        const c = Math.min(NL - 1, Math.floor(p[0] * NL));
        const r = Math.min(NL - 1, Math.floor(p[1] * NL));
        cnt[r * NL + c] += 1;
    });
    const maxC = Math.max(...cnt);
    for (let r = 0; r < NL; r += 1) {
        for (let c = 0; c < NL; c += 1) {
            const v = cnt[r * NL + c];
            g.push(box(lx + (c * side) / NL, ly + (r * side) / NL, side / NL, side / NL,
                { fill: v ? C2 : 'none', op: v ? 0.12 + 0.5 * (v / maxC) : 1, stroke: CG, sw: 1, rx: 0 }));
        }
    }
    P.forEach(p => g.push(pdot(...mapL(p), CI, 2.4)));
    const empty = cnt.filter(v => v === 0).length;
    g.push(txt(lx, ly - 12, '균등 격자 — 칸 64개', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(lx, ly + side + 22, `${empty}칸이 비어 있고, 가장 붐비는 칸에 점이 ${maxC}개 들어 있다`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(lx, ly + side + 40, '빈 칸도 자리를 차지하고, 붐비는 칸은 여전히 훑어야 한다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽: 쿼드트리 — 임계값을 넘는 칸만 쪼갠다
    const rxp = 468, ryp = 76;
    const mapR = draw(rxp, ryp);
    const leaves = [];
    const split = (x, y, w, h, inside, depth) => {
        if (inside.length <= 3 || depth >= 5) { leaves.push([x, y, w, h, inside.length]); return; }
        const hw = w / 2, hh = h / 2;
        for (const [qx, qy] of [[x, y], [x + hw, y], [x, y + hh], [x + hw, y + hh]]) {
            split(qx, qy, hw, hh, inside.filter(p => p[0] >= qx && p[0] < qx + hw && p[1] >= qy && p[1] < qy + hh), depth + 1);
        }
    };
    split(0, 0, 1, 1, P, 0);
    leaves.forEach((L) => {
        g.push(box(rxp + L[0] * side, ryp + L[1] * side, L[2] * side, L[3] * side,
            { fill: L[4] ? C3 : 'none', op: L[4] ? 0.28 : 1, stroke: CG, sw: 1, rx: 0 }));
    });
    P.forEach(p => g.push(pdot(...mapR(p), CI, 2.4)));
    g.push(txt(rxp, ryp - 12, '쿼드트리 — 칸 하나에 점 3개를 넘으면 넷으로 쪼갠다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rxp, ryp + side + 22, `칸이 ${leaves.length}개다. 빈 곳은 큰 칸 하나로 남고 붐비는 곳만 잘게 나뉜다`, { cls: 'ink2', size: 'sm' }));
    g.push(txt(rxp, ryp + side + 40, '어느 칸을 열어도 점이 세 개 이하라 훑는 값이 고르다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[52, 378], [W - 52, 378]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 398, '균등 격자는 자리 계산이 나눗셈 한 번으로 끝나고, 쿼드트리는 트리를 타고 내려가야 한다. 값을 치르고 고르게 만드는 것이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-quadtree',
        svg: svg({
            width: W, height: H,
            title: '균등 격자와 쿼드트리',
            desc: '같은 점 집합을 균등 격자와 쿼드트리로 나눈 결과. 쿼드트리는 붐비는 곳만 잘게 쪼갠다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 15장 — 병행성과 동기화
 * ================================================================== */

/* ---- 15-1. 경쟁 상태 — 두 스레드의 실행 순서가 엇갈린다 ---- */
add((() => {
    const W = 852, H = 448;
    const g = [];
    g.push(txt(W / 2, 24, '두 스레드가 같은 잔액에 1 을 더한다 — 한 줄짜리 문장이 세 단계로 쪼개진다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 46, '잔액 = 잔액 + 1     →     ① 읽는다   ② 1 을 더한다   ③ 쓴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const bw = 122, bh = 26, gap = 34;
    const scenes = [
        {
            x: 46, name: '운이 좋을 때 — 겹치지 않는다', col: C3,
            steps: [
                ['A', '① 읽는다 → 0', 0],
                ['A', '② 0 + 1 = 1', 0],
                ['A', '③ 쓴다 → 1', 1],
                ['B', '① 읽는다 → 1', 1],
                ['B', '② 1 + 1 = 2', 1],
                ['B', '③ 쓴다 → 2', 2],
            ],
            result: '잔액 = 2 — 맞다',
        },
        {
            x: 452, name: '운이 나쁠 때 — 사이를 파고든다', col: C2,
            steps: [
                ['A', '① 읽는다 → 0', 0],
                ['B', '① 읽는다 → 0', 0],
                ['A', '② 0 + 1 = 1', 0],
                ['B', '② 0 + 1 = 1', 0],
                ['A', '③ 쓴다 → 1', 1],
                ['B', '③ 쓴다 → 1', 1],
            ],
            result: '잔액 = 1 — 한 번이 사라졌다',
        },
    ];

    scenes.forEach((sc) => {
        const px0 = sc.x, py0 = 68;
        g.push(box(px0, py0, 354, 340, { stroke: sc.col, sw: 1.5, rx: 6 }));
        g.push(txt(px0 + 177, py0 + 22, sc.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px0 + 74, py0 + 46, '스레드 A', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px0 + 74 + bw + gap, py0 + 46, '스레드 B', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(px0 + 336, py0 + 46, '잔액', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        // 시간 축
        g.push(arw(px0 + 22, py0 + 56, px0 + 22, py0 + 306, { cls: 'ark', width: 1.2 }));
        g.push(txt(px0 + 16, py0 + 180, '시간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        sc.steps.forEach((st, i) => {
            const y = py0 + 62 + i * 40;
            const isA = st[0] === 'A';
            const x = px0 + 34 + (isA ? 0 : bw + gap);
            g.push(tbox(x, y, bw, bh, st[1], { stroke: isA ? C1 : C2, fill: isA ? C1 : C2, op: 0.16, sw: 1.4 }));
            g.push(txt(px0 + 336, y + 18, String(st[2]), { anchor: 'end', cls: 'ink', size: 'sm' }));
        });
        g.push(ln([[px0 + 20, py0 + 306], [px0 + 336, py0 + 306]], { stroke: CG, sw: 1 }));
        g.push(txt(px0 + 177, py0 + 328, sc.result, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });

    g.push(txt(W / 2, 428, '같은 프로그램이 같은 입력에서 어느 날은 맞고 어느 날은 틀린다. 이것이 경쟁 상태다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 446, '엇갈리는 순서가 수없이 많고 그중 아주 일부만 틀리기 때문에, 시험으로 잡히지 않고 부하가 걸린 새벽에 터진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-race-interleave',
        svg: svg({
            width: W, height: H,
            title: '경쟁 상태 — 두 스레드의 실행 순서가 엇갈린다',
            desc: '더하기 한 줄이 읽기 · 계산 · 쓰기 세 단계로 쪼개지고, 그 사이를 다른 스레드가 파고들면 결과가 사라진다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-2. 원자성과 가시성은 다른 문제다 ---- */
add((() => {
    const W = 840, H = 404;
    const g = [];
    g.push(txt(W / 2, 26, '고쳐야 할 것이 둘이다 — 나뉘지 않게 하는 것과 남에게 보이게 하는 것', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 가시성 (캐시)
    const lx = 46;
    g.push(box(lx, 52, 372, 296, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(lx + 186, 74, '가시성 — 쓴 값이 아직 남에게 안 보인다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(tbox(lx + 26, 92, 140, 44, ['핵 1 의 스레드 A', '멈춤 = 참 이라 썼다'], { stroke: C1, fill: C1, sw: 1.5 }));
    g.push(tbox(lx + 206, 92, 140, 44, ['핵 2 의 스레드 B', '멈춤 을 계속 읽는다'], { stroke: C2, fill: C2, sw: 1.5 }));
    g.push(tbox(lx + 26, 154, 140, 34, ['자기 캐시: 참'], { stroke: C1 }));
    g.push(tbox(lx + 206, 154, 140, 34, ['자기 캐시: 거짓'], { stroke: C2 }));
    g.push(arw(lx + 96, 136, lx + 96, 150, { cls: 's1', width: 1.4 }));
    g.push(arw(lx + 276, 154 + 34, lx + 276, 136, { cls: 's2', width: 1.4 }));
    g.push(tbox(lx + 96, 216, 180, 34, ['주 메모리: 거짓'], { stroke: CK, sw: 1.5 }));
    g.push(arw(lx + 96, 188, lx + 140, 214, { cls: 'ark', width: 1.4, dash: '4 3' }));
    g.push(txt(lx + 186, 268, '아직 내려쓰지 않았다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx + 186, 292, 'B 의 반복문은 영원히 끝나지 않는다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx + 186, 312, '읽기도 쓰기도 각각은 나뉘지 않았는데도 그렇다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx + 186, 336, '원자성만으로는 못 고친다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽: 재배치
    const rx = 448;
    g.push(box(rx, 52, 372, 296, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(rx + 186, 74, '재배치 — 적은 순서대로 실행되지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 30, 106, '내가 적은 순서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 212, 106, '실제로 실행될 수 있는 순서', { cls: 'ink2', size: 'sm' }));
    const codeL = ['자료 = 42', '준비됨 = 참'];
    const codeR = ['준비됨 = 참', '자료 = 42'];
    codeL.forEach((c, i) => g.push(tbox(rx + 30, 122 + i * 40, 150, 32, c, { stroke: C1, fill: C1, sw: 1.4 })));
    codeR.forEach((c, i) => g.push(tbox(rx + 212, 122 + i * 40, 150, 32, c, { stroke: C2, fill: C2, sw: 1.4 })));
    g.push(arw(rx + 186, 154, rx + 206, 154, { cls: 'ark', width: 1.4 }));
    g.push(txt(rx + 186, 226, '컴파일러와 CPU 는 ‘한 스레드에서 본 결과가 같으면’', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 186, 246, '순서를 마음대로 바꿀 수 있다. 두 문장 사이에 의존이 없기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 186, 274, '다른 스레드는 준비됨 이 참인데 자료 가 42 가 아닌', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 186, 294, '있을 수 없어 보이는 상태를 본다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 186, 322, '순서를 지키라고 못 박는 표시가 따로 필요하다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(W / 2, 376, '락을 걸면 셋이 한꺼번에 해결된다. 상호 배제뿐 아니라 ‘락을 풀 때 내려쓰고 걸 때 다시 읽는다’ 는 약속이 붙어 있기 때문이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 396, '락 없이 원자적 연산만 쓰겠다면 이 약속을 손으로 챙겨야 한다. 락-프리 코드가 어려운 이유가 여기에 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-atomic-visible',
        svg: svg({
            width: W, height: H,
            title: '원자성과 가시성은 다른 문제다',
            desc: '한 핵이 쓴 값이 다른 핵에 보이지 않는 일과, 적은 순서가 지켜지지 않는 일은 원자성으로 풀리지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-3. 임계 구역과 상호 배제 ---- */
add((() => {
    const W = 828, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '한 번에 하나만 들어가는 구간을 만든다', { anchor: 'middle', cls: 'ink bold' }));

    const tx = 128, tw = 560, t0 = 68;
    // 시간 축
    g.push(arw(tx, 224, tx + tw + 16, 224, { cls: 'ark', width: 1.3 }));
    g.push(txt(tx + tw + 22, 229, '시간', { cls: 'ink2', size: 'sm' }));

    const lanes = [
        { y: t0 + 20, name: '스레드 A', col: C1, segs: [[0.02, 0.16, '들어가기 전', 0], [0.16, 0.46, '임계 구역', 1], [0.46, 0.62, '나온 뒤', 0]] },
        { y: t0 + 96, name: '스레드 B', col: C2, segs: [[0.02, 0.16, '들어가기 전', 0], [0.16, 0.46, '기다린다 — 잠겨 있다', 2], [0.46, 0.78, '임계 구역', 1], [0.78, 0.94, '나온 뒤', 0]] },
    ];
    lanes.forEach((L) => {
        g.push(txt(tx - 14, L.y + 22, L.name, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        L.segs.forEach((s) => {
            const x = tx + s[0] * tw, w = (s[1] - s[0]) * tw;
            const col = s[3] === 1 ? L.col : s[3] === 2 ? CK : CG;
            g.push(box(x, L.y, w, 36, { fill: s[3] === 1 ? L.col : 'none', op: s[3] === 1 ? 0.25 : 1, stroke: col, sw: s[3] === 1 ? 1.8 : 1.2, rx: 3, dash: s[3] === 2 ? '5 4' : undefined }));
            g.push(txt(x + w / 2, L.y + 23, s[2], { anchor: 'middle', cls: s[3] === 1 ? 'ink bold' : 'ink2', size: 'sm' }));
        });
    });
    // 임계 구역 경계
    [0.16, 0.46, 0.78].forEach((f) => g.push(ln([[tx + f * tw, t0 + 8], [tx + f * tw, 218]], { stroke: CG, sw: 1, dash: '4 4' })));
    g.push(txt(tx + 0.16 * tw, t0 + 2, '잠근다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 0.46 * tw, t0 + 2, 'A 가 푼다 → B 가 들어간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 0.78 * tw, t0 + 2, 'B 가 푼다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(tx, 252, '두 임계 구역 상자가 시간 위에서 겹치지 않는다 — 이것이 상호 배제다', { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[52, 270], [W - 52, 270]], { stroke: CG, sw: 1 }));

    const reqs = [
        ['상호 배제', '한 번에 한 스레드만 안에 있다', C1],
        ['진행', '아무도 안에 없으면 들어가려는 스레드 중 하나는 반드시 들어간다', C2],
        ['유한 대기', '기다리는 스레드가 언젠가는 들어간다 — 새치기가 끝없이 이어지지 않는다', C3],
    ];
    reqs.forEach((r, i) => {
        const y = 318 + i * 26;
        g.push(box(tx - 100, y - 14, 14, 14, { fill: r[2], op: 0.55, stroke: r[2], sw: 1.2, rx: 2 }));
        g.push(txt(tx - 80, y - 2, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(tx + 6, y - 2, r[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(tx - 100, 294, '임계 구역을 지키는 방법이 갖춰야 할 것 셋', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-critical-section',
        svg: svg({
            width: W, height: H,
            title: '임계 구역과 상호 배제',
            desc: '두 스레드의 임계 구역이 시간 위에서 겹치지 않게 만드는 것이 상호 배제다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-4. 비교-교환(CAS) 고리 ---- */
add((() => {
    const W = 836, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, '락 없이 고치는 법 — 바꾸기 직전에 ‘아직 그대로인가’ 를 함께 묻는다', { anchor: 'middle', cls: 'ink bold' }));

    const steps = [
        ['① 읽는다', '옛값 = 잔액', C1],
        ['② 계산한다', '새값 = 옛값 + 1', C1],
        ['③ 비교하고 바꾼다', '잔액이 아직 옛값이면 새값을 쓴다', C2],
    ];
    const bx = 60;
    steps.forEach((s, i) => {
        const x = bx + i * 216;
        g.push(tbox(x, 62, 186, 54, [s[0], s[1]], { stroke: s[2], fill: s[2], sw: 1.5 }));
        if (i < 2) g.push(arw(x + 186, 89, x + 214, 89, { cls: 'ark', width: 1.5 }));
    });
    g.push(arw(bx + 2 * 216 + 186, 89, bx + 2 * 216 + 220, 89, { cls: 'ark', width: 1.5 }));
    g.push(tbox(bx + 2 * 216 + 222, 62, 108, 54, ['성공하면', '끝'], { stroke: C3, fill: C3, sw: 1.5 }));
    // 실패 고리
    g.push(curvePath(`M${bx + 2 * 216 + 93} 116 Q${bx + 216} 172 ${bx + 93} 120`, { stroke: C2, sw: 2, marker: 'ar2' }));
    g.push(txt(bx + 240, 168, '실패하면 처음부터 다시 — 그 사이 누가 값을 바꿨다는 뜻이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[52, 196], [W - 52, 196]], { stroke: CG, sw: 1 }));
    g.push(txt(60, 220, '③ 이 한 덩어리로 일어난다는 것이 열쇠다. 읽고 견주고 쓰는 일을 CPU 명령 하나가 나뉘지 않게 해 준다', { cls: 'ink bold', size: 'sm' }));

    const cmp = [
        ['락', ['못 들어가면 스레드를 재운다', '기다리는 동안 CPU 를 쓰지 않는다', '들고 있던 스레드가 멈추면 모두가 멈춘다'], C1],
        ['CAS 고리', ['못 바꾸면 곧바로 다시 시도한다', '기다리는 동안에도 CPU 를 쓴다', '누군가는 언제나 앞으로 나아간다'], C2],
    ];
    cmp.forEach((c, i) => {
        const x = 60 + i * 372;
        g.push(box(x, 244, 348, 96, { stroke: c[2], sw: 1.4, rx: 5 }));
        g.push(txt(x + 14, 266, c[0], { cls: 'ink bold', size: 'sm' }));
        c[1].forEach((t, j) => g.push(txt(x + 14, 288 + j * 18, '· ' + t, { cls: 'ink2', size: 'sm' })));
    });
    g.push(txt(W / 2, 364, '경쟁이 심하면 CAS 가 계속 실패해 오히려 느려진다. 짧고 드물게 부딪히는 자리에 쓴다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 386, '값이 A 에서 B 로 갔다가 A 로 돌아오면 CAS 는 변화를 못 알아챈다 — 이것을 ABA 문제라 하고, 번호를 함께 붙여 푼다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-cas-loop',
        svg: svg({
            width: W, height: H,
            title: '비교-교환(CAS) 고리',
            desc: '읽고 계산한 뒤 값이 아직 그대로일 때만 쓴다. 실패하면 처음부터 다시 한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-5. 생산자-소비자와 유계 버퍼 ---- */
add((() => {
    const W = 844, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '칸이 정해진 상자를 사이에 두고 — 세 가지를 한꺼번에 지켜야 한다', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 54, ch = 44, bx = 250, by = 92;
    g.push(cells(bx, by, cw, ch, ['자료', '자료', '자료', '', '', ''], { hl: { 0: C1, 1: C1, 2: C1 } }));
    g.push(txt(bx + 3 * cw, by - 14, '유계 버퍼 — 칸 6개', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 1.5 * cw, by + ch + 18, '찬 칸 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 4.5 * cw, by + ch + 18, '빈 칸 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(tbox(60, by, 150, 44, ['생산자'], { stroke: C1, fill: C1, sw: 1.5, size: undefined }));
    g.push(tbox(W - 210, by, 150, 44, ['소비자'], { stroke: C2, fill: C2, sw: 1.5, size: undefined }));
    g.push(arw(210, by + 22, bx - 8, by + 22, { cls: 's1', width: 1.8 }));
    g.push(arw(bx + 6 * cw + 8, by + 22, W - 212, by + 22, { cls: 's2', width: 1.8 }));

    const cols = [
        ['무엇이 잘못될 수 있는가', [
            '가득 찼는데 생산자가 또 넣는다 → 덮어쓴다',
            '비었는데 소비자가 꺼낸다 → 쓰레기를 읽는다',
            '둘이 동시에 같은 칸을 건드린다 → 하나가 사라진다',
        ], C2],
        ['무엇으로 막는가', [
            '세마포어 빈칸 — 처음 6. 넣기 전에 하나 얻는다',
            '세마포어 찬칸 — 처음 0. 꺼내기 전에 하나 얻는다',
            '뮤텍스 — 버퍼를 실제로 건드리는 짧은 구간만 감싼다',
        ], C3],
    ];
    cols.forEach((c, i) => {
        const x = 60 + i * 372;
        g.push(box(x, 190, 348, 106, { stroke: c[2], sw: 1.4, rx: 5 }));
        g.push(txt(x + 14, 212, c[0], { cls: 'ink bold', size: 'sm' }));
        c[1].forEach((t, j) => g.push(txt(x + 14, 236 + j * 20, '· ' + t, { cls: 'ink2', size: 'sm' })));
    });

    g.push(ln([[52, 314], [W - 52, 314]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 336, '세마포어는 개수를 센다. 값이 0 이면 얻으려는 스레드가 잠들고, 누가 반납하면 하나가 깨어난다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 358, '순서가 중요하다. 세마포어를 먼저 얻고 뮤텍스를 나중에 잠가야 한다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 378, '뮤텍스를 쥔 채로 세마포어를 기다리면, 깨워 줄 상대가 뮤텍스를 못 얻어 둘 다 영원히 멈춘다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-producer-consumer',
        svg: svg({
            width: W, height: H,
            title: '생산자-소비자와 유계 버퍼',
            desc: '가득 참 · 비어 있음 · 동시 접근 세 가지를 세마포어 둘과 뮤텍스 하나로 막는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-6. 교착 상태 — 네 조건과 기다림의 고리 ---- */
add((() => {
    const W = 844, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '넷이 모두 갖춰졌을 때만 교착이 생긴다 — 하나만 깨면 된다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 기다림의 고리
    const cxm = 210, cym = 190;
    const nodes = {
        t1: [cxm - 96, cym - 62, '스레드 A'],
        r1: [cxm + 96, cym - 62, '자원 1'],
        t2: [cxm + 96, cym + 62, '스레드 B'],
        r2: [cxm - 96, cym + 62, '자원 2'],
    };
    const R = 38;
    g.push(tedge([nodes.r1[0], nodes.r1[1]], [nodes.t1[0], nodes.t1[1]], { r1: R, r2r: R, stroke: C3, sw: 2, marker: true }));
    g.push(tedge([nodes.t1[0], nodes.t1[1]], [nodes.r2[0], nodes.r2[1]], { r1: R, r2r: R, stroke: C2, sw: 2, marker: true }));
    g.push(tedge([nodes.r2[0], nodes.r2[1]], [nodes.t2[0], nodes.t2[1]], { r1: R, r2r: R, stroke: C3, sw: 2, marker: true }));
    g.push(tedge([nodes.t2[0], nodes.t2[1]], [nodes.r1[0], nodes.r1[1]], { r1: R, r2r: R, stroke: C2, sw: 2, marker: true }));
    g.push(txt(cxm - 106, cym, '기다린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxm + 106, cym, '기다린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxm, cym - 74, '쥐고 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxm, cym + 78, '쥐고 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    Object.values(nodes).forEach((nd) => {
        const isT = nd[2].startsWith('스레드');
        g.push(tnode(nd[0], nd[1], nd[2], { r: R, col: isT ? C1 : null }));
    });
    g.push(txt(cxm, 62, '기다림이 고리를 이루면 아무도 못 나간다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cxm, 306, '자원 할당 그래프에 순환이 있는가 — 그것이 탐지의 기준이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxm, 326, '잠그는 순서를 하나로 정하면 고리가 생길 수 없다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽: 네 조건과 깨는 법
    const rx = 434;
    g.push(txt(rx, 62, '네 조건과 그것을 깨는 법', { cls: 'ink bold', size: 'sm' }));
    const four = [
        ['상호 배제', '자원을 한 번에 하나만 쓴다', '쓸 수 있으면 공유 자원으로 바꾼다'],
        ['점유하고 대기', '쥔 채로 다른 것을 기다린다', '필요한 것을 한꺼번에 얻거나 모두 놓는다'],
        ['빼앗지 못함', '남이 쥔 것을 뺏을 수 없다', '기다리다 지치면 스스로 놓고 물러난다'],
        ['순환 대기', '기다림이 고리를 이룬다', '자원에 번호를 매겨 오름차순으로만 잠근다'],
    ];
    four.forEach((f, i) => {
        const y = 82 + i * 56;
        g.push(box(rx, y, 366, 48, { stroke: i === 3 ? C2 : CG, sw: i === 3 ? 1.6 : 1, rx: 4 }));
        g.push(txt(rx + 12, y + 20, f[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(rx + 96, y + 20, f[1], { cls: 'ink2', size: 'sm' }));
        g.push(txt(rx + 12, y + 39, '깨는 법: ' + f[2], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(rx, 322, '실무에서 가장 많이 쓰는 것이 마지막 줄이다.', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[52, 344], [W - 52, 344]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 366, '교착은 아무도 나아가지 못하는 것이고, 기아는 남들만 나아가고 나만 못 나아가는 것이다. 둘은 다르다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 386, '교착은 멈춰 있으므로 보면 알 수 있지만, 기아는 시스템이 잘 도는 것처럼 보여 더 늦게 발견된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-deadlock-cycle',
        svg: svg({
            width: W, height: H,
            title: '교착 상태의 네 조건',
            desc: '기다림이 고리를 이루면 교착이다. 네 조건 중 하나만 깨면 교착이 생기지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-7. 우선순위 역전 ---- */
add((() => {
    const W = 836, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '급한 일이 안 급한 일에 밀린다', { anchor: 'middle', cls: 'ink bold' }));

    const tx = 132, tw = 600, top = 60;
    const lanes = [
        { name: '높음 H', col: C2, y: top + 16, segs: [[0.22, 0.72, '락이 풀리기를 기다리며 막혀 있다', 'block'], [0.72, 0.92, '실행', 'run']] },
        { name: '중간 M', col: C1, y: top + 72, segs: [[0.36, 0.72, '실행 — 락과 아무 상관 없다', 'run']] },
        { name: '낮음 L', col: C3, y: top + 128, segs: [[0.06, 0.36, '락을 잡고 실행', 'run'], [0.36, 0.72, '밀려나 멈춤', 'block'], [0.72, 0.80, '푼다', 'run']] },
    ];
    g.push(arw(tx, top + 186, tx + tw + 16, top + 186, { cls: 'ark', width: 1.3 }));
    g.push(txt(tx + tw + 22, top + 191, '시간', { cls: 'ink2', size: 'sm' }));
    lanes.forEach((L) => {
        g.push(txt(tx - 14, L.y + 24, L.name, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(ln([[tx, L.y + 38], [tx + tw, L.y + 38]], { stroke: CG, sw: 1 }));
        L.segs.forEach((s) => {
            const x = tx + s[0] * tw, w = (s[1] - s[0]) * tw;
            const run = s[3] === 'run';
            g.push(box(x, L.y, w, 36, {
                fill: run ? L.col : 'none', op: run ? 0.28 : 1,
                stroke: run ? L.col : CK, sw: run ? 1.8 : 1.2, rx: 3, dash: run ? undefined : '5 4',
            }));
            g.push(txt(x + w / 2, L.y + 23, s[2], { anchor: 'middle', cls: run ? 'ink bold' : 'ink2', size: 'sm' }));
        });
    });
    [0.22, 0.36, 0.72].forEach((f, i) => {
        g.push(ln([[tx + f * tw, top + 8], [tx + f * tw, top + 182]], { stroke: CG, sw: 1, dash: '4 4' }));
        g.push(txt(tx + f * tw + [-6, 6, 0][i], top + 2, ['H 가 락을 원한다', 'M 이 L 을 밀어낸다', 'L 이 락을 푼다'][i], { anchor: ['end', 'start', 'middle'][i], cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[52, 274], [W - 52, 274]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 296, 'H 가 기다리는 것은 L 이 쥔 락인데, 정작 H 를 붙잡아 두는 것은 락과 아무 상관 없는 M 이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 318, '우선순위가 뒤집혔다. L 이 밀려나 있는 동안 락이 풀리지 않고, 그래서 H 가 M 보다 늦게 끝난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 344, '해결책은 우선순위 상속이다 — 락을 쥔 L 에게 기다리는 H 의 우선순위를 잠시 빌려주면 M 이 끼어들지 못한다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 364, '1997년 화성 탐사선 패스파인더가 계속 재시동된 원인이 이것이었다고 알려져 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-priority-inversion',
        svg: svg({
            width: W, height: H,
            title: '우선순위 역전',
            desc: '락을 쥔 낮은 우선순위 스레드가 중간 우선순위 스레드에 밀려나면 높은 우선순위 스레드가 그만큼 기다린다',
            body: g.join(''),
        }),
    };
})());

/* ---- 15-8. TOCTOU — 검사와 사용 사이의 틈 ---- */
add((() => {
    const W = 836, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '검사한 순간과 쓰는 순간 사이에 세상이 바뀐다', { anchor: 'middle', cls: 'ink bold' }));

    const tx = 150, tw = 560, top = 58;
    g.push(arw(tx, top + 168, tx + tw + 16, top + 168, { cls: 'ark', width: 1.3 }));
    g.push(txt(tx + tw + 22, top + 173, '시간', { cls: 'ink2', size: 'sm' }));

    g.push(txt(tx - 14, top + 34, '프로그램', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(tbox(tx + 0.02 * tw, top + 12, 0.28 * tw, 42, ['① 이 이름의 파일이', '내 것인지 검사한다'], { stroke: C1, fill: C1, sw: 1.5 }));
    g.push(tbox(tx + 0.62 * tw, top + 12, 0.32 * tw, 42, ['③ 그 이름으로', '파일을 연다'], { stroke: C1, fill: C1, sw: 1.5 }));
    g.push(ln([[tx + 0.30 * tw, top + 33], [tx + 0.62 * tw, top + 33]], { stroke: CK, sw: 1.4, dash: '5 4' }));
    g.push(txt(tx + 0.46 * tw, top + 26, '틈', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(tx - 14, top + 122, '공격자', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(tbox(tx + 0.34 * tw, top + 100, 0.24 * tw, 42, ['② 그 이름을 시스템 파일로', '가는 링크로 바꾼다'], { stroke: C2, fill: C2, sw: 1.5 }));
    g.push(arw(tx + 0.46 * tw, top + 98, tx + 0.46 * tw, top + 60, { cls: 's2', width: 1.8 }));

    g.push(txt(tx, top + 196, '① 에서 검사한 대상과 ③ 에서 연 대상이 다르다. 검사는 그때의 진실이었을 뿐이다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[52, 278], [W - 52, 278]], { stroke: CG, sw: 1 }));
    const fix = [
        ['틈을 없앤다', '검사와 사용을 한 덩어리로 만든다 — 열고 나서 그 열린 것에 대해 검사한다'],
        ['이름 대신 손잡이', '이름은 언제든 다른 것을 가리킬 수 있다. 한 번 연 대상을 그대로 들고 쓴다'],
        ['같은 문제, 다른 얼굴', '잔액을 확인하고 인출하기, 자리가 있는지 보고 예약하기도 모두 이 모양이다'],
    ];
    fix.forEach((f, i) => {
        g.push(txt(60, 300 + i * 22, f[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(196, 300 + i * 22, f[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(60, 366, '스레드가 하나뿐이어도 생긴다. 파일 시스템과 운영체제라는 다른 참여자가 있기 때문이다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-toctou',
        svg: svg({
            width: W, height: H,
            title: 'TOCTOU — 검사와 사용 사이의 틈',
            desc: '검사한 뒤 실제로 쓰기까지의 틈에 대상이 바뀌면 검사 결과가 아무 의미가 없어진다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 16장 — 암호와 보안 프로토콜
 * ================================================================== */

/** 32비트 정수 흩뿌리기. 같은 입력이면 같은 색이 나와야 한다. */
function mixInt(v) {
    let h = v >>> 0;
    h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
    h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
    return (h ^ (h >>> 16)) >>> 0;
}

/* ---- 16-1. ECB 모드는 패턴을 남긴다 ---- */
add((() => {
    const W = 800, H = 424;
    const g = [];
    g.push(txt(W / 2, 26, '같은 그림을 같은 열쇠로 암호화한 결과 — 블록을 어떻게 이어 붙이느냐만 다르다', { anchor: 'middle', cls: 'ink bold' }));

    const N = 48, PX = 4, BLK = 4;          // 48×48 화소, 화소 4px, 블록은 4×4 화소
    const side = N * PX;                    // 192
    // 자물쇠 그림 — 고리와 몸통과 열쇠 구멍
    const fg = (x, y) => {
        const cx = x + 0.5, cy = y + 0.5;
        const dx = cx - 24, dy = cy - 21;
        if (dy <= 0 && Math.hypot(dx, dy) >= 7.5 && Math.hypot(dx, dy) <= 12) return true;
        if (cx >= 9 && cx < 39 && cy >= 21 && cy < 43) {
            const kx = cx - 24, ky = cy - 29;
            if (Math.hypot(kx, ky) < 3.6) return false;
            if (Math.abs(kx) < 1.8 && cy >= 29 && cy < 37) return false;
            return true;
        }
        return false;
    };
    const bit = [];
    for (let y = 0; y < N; y += 1) { bit.push([]); for (let x = 0; x < N; x += 1) bit[y].push(fg(x, y) ? 1 : 0); }

    const panels = [
        { x: 40, name: '원본 평문', sub: '48 × 48 화소', mode: 'plain' },
        { x: 304, name: 'ECB 로 암호화', sub: '블록마다 따로 암호화', mode: 'ecb' },
        { x: 568, name: 'CBC · CTR 로 암호화', sub: '앞 블록이나 카운터를 섞는다', mode: 'chain' },
    ];
    panels.forEach((p) => {
        const ox = p.x, oy = 96;
        g.push(txt(ox + side / 2, 62, p.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(ox + side / 2, 80, p.sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (p.mode === 'plain') {
            // 가로로 이어진 화소를 한 사각형으로 묶어 파일을 작게 유지한다
            for (let y = 0; y < N; y += 1) {
                let run = -1;
                for (let x = 0; x <= N; x += 1) {
                    const on = x < N && bit[y][x] === 1;
                    if (on && run < 0) run = x;
                    if (!on && run >= 0) {
                        g.push(`<rect x="${ox + run * PX}" y="${oy + y * PX}" width="${(x - run) * PX}" height="${PX}" fill="${CI}"/>`);
                        run = -1;
                    }
                }
            }
        } else {
            for (let by = 0; by < N / BLK; by += 1) {
                for (let bx = 0; bx < N / BLK; bx += 1) {
                    let key = 0;
                    for (let j = 0; j < BLK; j += 1) for (let i = 0; i < BLK; i += 1) key = (key << 1) | bit[by * BLK + j][bx * BLK + i];
                    // ECB 는 블록 내용만으로 색이 정해진다. 사슬 모드는 자리까지 섞는다.
                    const seed = p.mode === 'ecb' ? mixInt(key + 7) : mixInt(key * 31 + (by * 12 + bx) * 2654435761);
                    g.push(`<rect x="${ox + bx * BLK * PX}" y="${oy + by * BLK * PX}" width="${BLK * PX}" height="${BLK * PX}" fill="${noiseColor(rng(seed))}"/>`);
                }
            }
        }
        g.push(box(ox, oy, side, side, { stroke: CG, sw: 1, rx: 0 }));
    });
    g.push(txt(40 + side / 2, 306, '무엇을 그린 것인지 보인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(304 + side / 2, 306, '내용은 못 읽는데 모양이 그대로 남는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(568 + side / 2, 306, '아무것도 읽어낼 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[40, 330], [W - 40, 330]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 352, 'ECB 는 같은 평문 블록을 언제나 같은 암호문 블록으로 보낸다. 배경 블록끼리 같은 색, 몸통 블록끼리 같은 색이 되는 이유다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 374, '블록 하나하나는 제대로 암호화되었다. 새어 나가는 것은 블록 안의 내용이 아니라 ‘어느 블록과 어느 블록이 같은가’ 라는 사실이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 396, '그림이 아니어도 마찬가지다. 같은 값이 되풀이되는 자료라면 그 되풀이 구조가 그대로 드러난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 418, '오른쪽 두 모드는 앞 블록의 암호문이나 블록 번호를 섞어 넣어 같은 평문이 같은 암호문이 되지 않게 만든다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-ecb-pattern',
        svg: svg({
            width: W, height: H,
            title: 'ECB 모드는 패턴을 남긴다',
            desc: '같은 그림을 ECB 로 암호화하면 같은 평문 블록이 같은 암호문 블록이 되어 원래 모양이 그대로 드러난다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-2. 블록 암호 운용 모드 셋 ---- */
add((() => {
    const W = 852, H = 424;
    const g = [];
    g.push(txt(W / 2, 26, '블록을 이어 붙이는 세 가지 방법 — 암호 함수 자체는 똑같다', { anchor: 'middle', cls: 'ink bold' }));

    const xo = (x, y, r = 12) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="none" stroke="${CK}" stroke-width="1.4"/>`
        + txt(x, y + 5, '⊕', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    const eBox = (x, y) => tbox(x, y - 15, 62, 30, ['E · 키'], { stroke: C1, fill: C1, sw: 1.5 });

    const rows = [
        { y: 100, tag: 'ECB', note: ['블록마다 따로 암호화한다', '같은 평문 블록 → 같은 암호문 블록'] },
        { y: 212, tag: 'CBC', note: ['앞 블록의 암호문을 섞는다', '암호화는 차례대로, 복호화는 병렬로'] },
        { y: 324, tag: 'CTR', note: ['평문은 암호 함수를 통과하지 않는다', '암 · 복호화 모두 병렬로 된다'] },
    ];
    rows.forEach((rw) => {
        g.push(txt(40, rw.y + 5, rw.tag, { cls: 'ink bold' }));
        rw.note.forEach((t, i) => g.push(txt(668, rw.y - 6 + i * 18, t, { cls: i === 0 ? 'ink bold' : 'ink2', size: 'sm' })));
    });

    // ECB
    for (let j = 0; j < 2; j += 1) {
        const gx = 118 + j * 270;
        g.push(tbox(gx, rows[0].y - 15, 48, 30, [`P${j ? '₂' : '₁'}`], { stroke: CK }));
        g.push(arw(gx + 48, rows[0].y, gx + 70, rows[0].y, { cls: 'ark', width: 1.4 }));
        g.push(eBox(gx + 72, rows[0].y));
        g.push(arw(gx + 134, rows[0].y, gx + 156, rows[0].y, { cls: 'ark', width: 1.4 }));
        g.push(tbox(gx + 158, rows[0].y - 15, 48, 30, [`C${j ? '₂' : '₁'}`], { stroke: C2, fill: C2 }));
    }
    // CBC
    for (let j = 0; j < 2; j += 1) {
        const gx = 118 + j * 270;
        const y = rows[1].y;
        g.push(tbox(gx + 40, y - 62, 48, 26, [`P${j ? '₂' : '₁'}`], { stroke: CK }));
        g.push(arw(gx + 64, y - 36, gx + 64, y - 14, { cls: 'ark', width: 1.4 }));
        g.push(xo(gx + 64, y));
        if (j === 0) {
            g.push(tbox(gx - 46, y - 15, 48, 30, ['IV'], { stroke: C3, fill: C3 }));
            g.push(arw(gx + 2, y, gx + 50, y, { cls: 's3', width: 1.4 }));
        }
        g.push(arw(gx + 78, y, gx + 100, y, { cls: 'ark', width: 1.4 }));
        g.push(eBox(gx + 102, y));
        g.push(arw(gx + 164, y, gx + 186, y, { cls: 'ark', width: 1.4 }));
        g.push(tbox(gx + 188, y - 15, 48, 30, [`C${j ? '₂' : '₁'}`], { stroke: C2, fill: C2 }));
        if (j === 0) g.push(arw(gx + 236, y, gx + 320, y, { cls: 's2', width: 1.4 }));
    }
    // CTR
    for (let j = 0; j < 2; j += 1) {
        const gx = 118 + j * 270;
        const y = rows[2].y;
        g.push(tbox(gx - 40, y - 15, 66, 30, [`nonce·${j + 1}`], { stroke: C3, fill: C3 }));
        g.push(arw(gx + 26, y, gx + 40, y, { cls: 'ark', width: 1.4 }));
        g.push(eBox(gx + 42, y));
        g.push(arw(gx + 104, y, gx + 122, y, { cls: 'ark', width: 1.4 }));
        g.push(xo(gx + 136, y));
        g.push(tbox(gx + 112, y - 62, 48, 26, [`P${j ? '₂' : '₁'}`], { stroke: CK }));
        g.push(arw(gx + 136, y - 36, gx + 136, y - 14, { cls: 'ark', width: 1.4 }));
        g.push(arw(gx + 150, y, gx + 168, y, { cls: 'ark', width: 1.4 }));
        g.push(tbox(gx + 170, y - 15, 44, 30, [`C${j ? '₂' : '₁'}`], { stroke: C2, fill: C2 }));
    }

    g.push(ln([[40, 366], [W - 40, 366]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 388, 'IV 와 nonce 는 비밀이 아니다. 다만 같은 열쇠로 두 번 쓰면 안 된다 — CTR 에서 그러면 두 평문의 XOR 이 그대로 드러난다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 410, '세 모드 어느 것도 변조를 막지 못한다. 무결성은 뒤에 볼 MAC 이 따로 맡는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-block-modes',
        svg: svg({
            width: W, height: H,
            title: '블록 암호 운용 모드 — ECB · CBC · CTR',
            desc: '같은 암호 함수를 블록에 어떻게 이어 붙이는가에 따라 세 모드가 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-3. 모듈러 거듭제곱은 한쪽으로만 쉽다 ---- */
add((() => {
    const W = 828, H = 392;
    const g = [];
    g.push(txt(W / 2, 26, '3ˣ 을 17 로 나눈 나머지 — x 를 1 부터 16 까지 올려 본다', { anchor: 'middle', cls: 'ink bold' }));

    const pMod = 17, gBase = 3;
    const vals = [];
    let acc = 1;
    for (let x = 1; x <= 16; x += 1) { acc = (acc * gBase) % pMod; vals.push([x, acc]); }

    const f = frame({ xRange: [0, 17], yRange: [0, 17], box: { x: 84, y: 52, w: 360, h: 200 } });
    g.push(axes2(f, {
        xRange: [0, 17], yRange: [0, 17],
        xTicks: [1, 4, 8, 12, 16], yTicks: [1, 4, 8, 12, 16],
        xLabel: '지수 x', yLabel: '3ˣ mod 17',
    }));
    vals.forEach(v => g.push(pdot(f.X(v[0]), f.Y(v[1]), C1, 4.5)));
    g.push(pdot(f.X(6), f.Y(15), C2, 6));
    g.push(txt(f.X(6) - 10, f.Y(15) - 6, '3⁶ = 15', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(84, 296, '점들이 아무 규칙 없이 흩어진다. 1 부터 16 까지가 한 번씩 나오지만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(84, 314, '어느 지수가 어느 값을 내는지 짐작할 방법이 없다', { cls: 'ink2', size: 'sm' }));

    const bx = 502;
    const dirs = [
        ['앞으로 가는 길 — 쉽다', ['x 를 알 때 3ˣ mod 17 을 구한다', '10장의 빠른 거듭제곱으로', '곱셈 log x 번이면 끝난다', '지수가 2²⁰⁴⁸ 급이어도 수천 번이다'], C3],
        ['되돌아오는 길 — 어렵다', ['15 를 보고 x = 6 을 알아낸다', '이것을 이산로그 문제라 한다', '작은 수는 하나씩 해 보면 되지만', '수가 커지면 알려진 빠른 방법이 없다'], C2],
    ];
    dirs.forEach((d, i) => {
        const y = 58 + i * 122;
        g.push(box(bx, y, 288, 106, { stroke: d[2], sw: 1.5, rx: 5 }));
        g.push(txt(bx + 14, y + 22, d[0], { cls: 'ink bold', size: 'sm' }));
        d[1].forEach((t, j) => g.push(txt(bx + 14, y + 42 + j * 17, t, { cls: 'ink2', size: 'sm' })));
    });

    g.push(ln([[52, 328], [W - 52, 328]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 350, '한쪽으로만 쉬운 계산 — 이 비대칭 하나가 공개키 암호 전체를 떠받친다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 372, '어렵다는 것이 증명된 적은 없다. 빠른 방법이 아직 발견되지 않았을 뿐이라는 점을 잊지 말아야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-modexp-oneway',
        svg: svg({
            width: W, height: H,
            title: '모듈러 거듭제곱의 비대칭',
            desc: '지수에서 값을 구하는 것은 빠르지만 값에서 지수를 되찾는 이산로그 문제는 알려진 빠른 방법이 없다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-4. RSA 의 뼈대 ---- */
add((() => {
    const W = 844, H = 432;
    const g = [];
    g.push(txt(W / 2, 26, 'RSA — 아주 작은 수로 한 번 돌려 본다', { anchor: 'middle', cls: 'ink bold' }));

    const steps = [
        ['① 소수 두 개를 고른다', ['p = 5,  q = 11', '실제로는 각각 1000자리쯤 된다'], C1],
        ['② 곱과 φ 를 구한다', ['N = p·q = 55', 'φ(N) = (p−1)(q−1) = 40'], C1],
        ['③ 두 지수를 정한다', ['e = 3  (φ 와 서로소)', 'd = 27  (e·d ≡ 1 mod φ)'], C2],
        ['④ 공개하고 감춘다', ['공개키 (N, e) = (55, 3)', '비밀키 d = 27. p, q 는 지운다'], C3],
    ];
    steps.forEach((s, i) => {
        const x = 40 + i * 202;
        g.push(box(x, 52, 190, 92, { stroke: s[2], sw: 1.5, rx: 5 }));
        g.push(txt(x + 12, 74, s[0], { cls: 'ink bold', size: 'sm' }));
        s[1].forEach((t, j) => g.push(txt(x + 12, 98 + j * 20, t, { cls: 'ink2', size: 'sm' })));
        if (i < 3) g.push(arw(x + 190, 98, x + 200, 98, { cls: 'ark', width: 1.4 }));
    });

    const y0 = 176;
    g.push(tbox(72, y0, 130, 40, ['평문 M = 7'], { stroke: CK, sw: 1.5 }));
    g.push(arw(202, y0 + 20, 250, y0 + 20, { cls: 's1', width: 1.8 }));
    g.push(txt(226, y0 + 12, '공개키로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(tbox(252, y0, 178, 40, ['7³ mod 55 = 13'], { stroke: C1, fill: C1, sw: 1.5 }));
    g.push(arw(430, y0 + 20, 478, y0 + 20, { cls: 's2', width: 1.8 }));
    g.push(txt(454, y0 + 12, '비밀키로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(tbox(480, y0, 190, 40, ['13²⁷ mod 55 = 7'], { stroke: C2, fill: C2, sw: 1.5 }));
    g.push(arw(670, y0 + 20, 706, y0 + 20, { cls: 'ark', width: 1.8 }));
    g.push(tbox(708, y0, 96, 40, ['평문으로'], { stroke: C3, fill: C3, sw: 1.5 }));
    g.push(txt(72, y0 + 62, '누구나 할 수 있다 — 공개키만 있으면 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(480, y0 + 62, 'd 를 아는 사람만 할 수 있다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[40, 262], [W - 40, 262]], { stroke: CG, sw: 1 }));
    g.push(txt(40, 286, '왜 되돌아오는가', { cls: 'ink bold', size: 'sm' }));
    const why = [
        'e·d ≡ 1 (mod φ) 이므로 어떤 정수 k 에 대해 e·d = 1 + kφ 다.',
        '그러면 M 을 e·d 제곱한 것은 M 을 1 + kφ 제곱한 것이고, 그것은 M × (M 을 φ 제곱한 것)을 k 제곱한 것이다.',
        '오일러 정리가 M 을 φ 제곱하면 N 으로 나눈 나머지가 1 임을 보장하므로 남는 것은 M 뿐이다.',
        '페르마 소정리(소수 p 에 대해 M 을 p−1 제곱하면 나머지가 1)를 소수 아닌 N 으로 넓힌 것이 오일러 정리다.',
    ];
    why.forEach((t, i) => g.push(txt(40, 308 + i * 20, t, { cls: 'ink2', size: 'sm' })));

    g.push(txt(40, 396, '무엇에 기대고 있는가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(196, 396, 'N 을 알아도 p 와 q 를 되찾기 어렵다는 것. 그것을 알면 φ 가 나오고 φ 를 알면 d 가 나온다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 418, '실제 구현은 다르다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(196, 418, '여기 적은 그대로 쓰면 안전하지 않다. 평문에 무작위 채움을 섞는 규격을 반드시 함께 쓴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-rsa-flow',
        svg: svg({
            width: W, height: H,
            title: 'RSA 의 뼈대',
            desc: '소수 둘로 N 과 φ 를 만들고 e·d ≡ 1 (mod φ) 인 두 지수를 정하면 암호화와 복호화가 서로를 되돌린다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-5. 디피-헬만 — 색 섞기 ---- */
add((() => {
    const W = 836, H = 412;
    const g = [];
    g.push(txt(W / 2, 26, '섞기는 쉽고 되돌리기는 어렵다 — 색으로 본 디피-헬만', { anchor: 'middle', cls: 'ink bold' }));

    // 물감처럼 곱셈으로 섞는다. 곱셈은 순서를 바꿔도 결과가 같다.
    const P = [230, 210, 120], A = [220, 110, 90], B = [110, 150, 230];
    const mix = (c1, c2) => c1.map((v, i) => Math.round((v * c2[i]) / 255));
    const hex = c => '#' + c.map(v => v.toString(16).padStart(2, '0')).join('');
    const PA = mix(P, A), PB = mix(P, B), S1 = mix(PA, B), S2 = mix(PB, A);
    const swatch = (x, y, c, label, sub) => box(x, y, 52, 40, { fill: hex(c), op: 1, stroke: CG, sw: 1, rx: 4 })
        + txt(x + 26, y + 56, label, { anchor: 'middle', cls: 'ink bold', size: 'sm' })
        + (sub ? txt(x + 26, y + 72, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');

    g.push(swatch(392, 58, P, '공개색', '누구나 본다'));
    g.push(swatch(140, 152, A, '앨리스의 비밀색', ''));
    g.push(swatch(644, 152, B, '밥의 비밀색', ''));
    g.push(swatch(258, 152, PA, '공개색 + 앨리스', '주고받는다'));
    g.push(swatch(526, 152, PB, '공개색 + 밥', '주고받는다'));
    g.push(swatch(310, 268, S1, '받은 것 + 앨리스', ''));
    g.push(swatch(474, 268, S2, '받은 것 + 밥', ''));
    g.push(arw(392, 100, 300, 148, { cls: 'ark', width: 1.4 }));
    g.push(arw(444, 100, 546, 148, { cls: 'ark', width: 1.4 }));
    g.push(arw(192, 194, 276, 210, { cls: 'ark', width: 1.4 }));
    g.push(arw(670, 194, 582, 210, { cls: 'ark', width: 1.4 }));
    g.push(curvePath('M284 224 Q300 250 330 264', { stroke: C1, sw: 1.8, marker: 'ar1' }));
    g.push(curvePath('M552 224 Q536 250 506 264', { stroke: C2, sw: 1.8, marker: 'ar2' }));
    g.push(txt(418, 250, '섞는 순서가 달라도 같은 색에 이른다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(418, 292, '같다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(ln([[40, 344], [W - 40, 344]], { stroke: CG, sw: 1 }));
    g.push(txt(40, 366, '수로 바꾸면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(140, 366, '공개색은 (소수 p, 밑 g). 섞기는 gˣ mod p. 앨리스가 gᵃ 를, 밥이 gᵇ 를 보내고 둘 다 gᵃᵇ 를 얻는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 388, '엿듣는 자는', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(140, 388, 'g, gᵃ, gᵇ 를 모두 보지만 gᵃᵇ 를 만들 수 없다. a 나 b 를 알아내야 하는데 그것이 이산로그 문제다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 410, '막지 못하는 것', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(140, 410, '가운데서 양쪽과 따로 키를 맺는 중간자. 상대가 누구인지는 서명과 인증서가 따로 확인해야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-dh-mix',
        svg: svg({
            width: W, height: H,
            title: '디피-헬만 키 교환을 색 섞기로',
            desc: '공개색에 각자의 비밀색을 섞어 교환하면 양쪽이 같은 색에 도달하지만 엿듣는 사람은 만들 수 없다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-6. 암호화만으로는 변조를 못 막는다 ---- */
add((() => {
    const W = 844, H = 424;
    const g = [];
    g.push(txt(W / 2, 26, '읽지 못하게 하는 것과 고치지 못하게 하는 것은 다른 일이다', { anchor: 'middle', cls: 'ink bold' }));

    // 위: CTR 모드에서 비트를 뒤집으면 평문의 같은 비트가 뒤집힌다
    const y0 = 58;
    g.push(txt(40, y0 + 14, '① 암호문만 보낸다면 — CTR 모드에서 한 비트를 뒤집어 본다', { cls: 'ink bold', size: 'sm' }));
    const rowsA = [
        ['평문', '00110001  00110000  ⋯', '‘10000원’ 의 앞 두 글자 1 과 0', CK],
        ['키 스트림', '10100110  01011101  ⋯', '공격자는 이것을 모른다', CG],
        ['암호문', '10010111  01101101  ⋯', '이대로 보낸다', C1],
        ['공격자가 뒤집은 암호문', '10011111  01101101  ⋯', '다섯째 비트 하나만 바꿨다', C2],
        ['받는 쪽이 푼 평문', '00111001  00110000  ⋯', '1 이 9 가 되어 ‘90000원’ 이 되었다', C2],
    ];
    rowsA.forEach((r, i) => {
        const y = y0 + 36 + i * 26;
        g.push(txt(228, y, r[0], { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(box(238, y - 15, 214, 21, { fill: r[3] === CG ? 'none' : r[3], op: 0.2, stroke: r[3], sw: 1.2, rx: 2 }));
        g.push(txt(345, y, r[1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(466, y, r[2], { cls: i >= 3 ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(40, y0 + 190, '공격자는 내용을 읽지 못했는데도 내용을 바꿨다. 무엇으로 바뀌는지까지 조종할 수 있다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[40, 268], [W - 40, 268]], { stroke: CG, sw: 1 }));

    // 아래: 해시만 붙이면 왜 안 되는가
    const y1 = 290;
    g.push(txt(40, y1, '② 그러면 해시를 함께 보내면 되는가 — 안 된다', { cls: 'ink bold', size: 'sm' }));
    const three = [
        ['해시만 붙인다', ['메시지 + H(메시지)', '공격자가 메시지를 바꾸고', 'H 를 다시 계산하면 그만이다'], C2],
        ['키를 섞은 해시 — MAC', ['메시지 + HMAC(키, 메시지)', '키가 없으면 값을 다시 만들 수 없다', '받는 쪽도 같은 키가 있어야 확인한다'], C3],
        ['왜 H(키 ‖ 메시지) 가 아닌가', ['흔한 해시 함수는 중간 상태를 이어받아', '뒤에 덧붙인 메시지의 해시를 만들 수 있다', 'HMAC 은 두 번 해싱해 이 길을 막는다'], C1],
    ];
    three.forEach((t, i) => {
        const x = 40 + i * 268;
        g.push(box(x, y1 + 12, 254, 92, { stroke: t[2], sw: 1.4, rx: 5 }));
        g.push(txt(x + 12, y1 + 34, t[0], { cls: 'ink bold', size: 'sm' }));
        t[1].forEach((u, j) => g.push(txt(x + 12, y1 + 54 + j * 17, u, { cls: 'ink2', size: 'sm' })));
    });
    g.push(txt(W / 2, 414, '암호화는 기밀성만 준다. 무결성과 인증은 MAC 이 따로 준다 — 요즘 규격은 둘을 한 덩어리로 묶어 제공한다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-mac-need',
        svg: svg({
            width: W, height: H,
            title: '암호화만으로는 변조를 못 막는다',
            desc: '스트림 방식 암호문은 비트를 뒤집으면 평문도 뒤집힌다. 해시만 붙여서도 막을 수 없고 키를 섞은 MAC 이 필요하다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-7. 전자 서명과 인증서 사슬 ---- */
add((() => {
    const W = 844, H = 412;
    const g = [];
    g.push(txt(W / 2, 26, '서명은 거꾸로 쓴다 — 비밀키로 만들고 공개키로 확인한다', { anchor: 'middle', cls: 'ink bold' }));

    // 위: 서명 만들기와 확인하기
    const y0 = 52;
    g.push(tbox(40, y0, 120, 36, ['메시지'], { stroke: CK, sw: 1.4 }));
    g.push(arw(160, y0 + 18, 186, y0 + 18, { cls: 'ark', width: 1.4 }));
    g.push(tbox(188, y0, 108, 36, ['해시 H'], { stroke: C1, fill: C1, sw: 1.4 }));
    g.push(arw(296, y0 + 18, 322, y0 + 18, { cls: 'ark', width: 1.4 }));
    g.push(tbox(324, y0, 150, 36, ['보내는 이의 비밀키'], { stroke: C2, fill: C2, sw: 1.4 }));
    g.push(arw(474, y0 + 18, 500, y0 + 18, { cls: 'ark', width: 1.4 }));
    g.push(tbox(502, y0, 110, 36, ['서명'], { stroke: C3, fill: C3, sw: 1.4 }));
    g.push(txt(624, y0 + 22, '메시지와 함께 보낸다', { cls: 'ink2', size: 'sm' }));

    const y1 = 116;
    g.push(tbox(40, y1, 120, 36, ['받은 메시지'], { stroke: CK, sw: 1.4 }));
    g.push(arw(160, y1 + 18, 186, y1 + 18, { cls: 'ark', width: 1.4 }));
    g.push(tbox(188, y1, 108, 36, ['해시 H'], { stroke: C1, fill: C1, sw: 1.4 }));
    g.push(tbox(324, y1, 150, 36, ['보내는 이의 공개키'], { stroke: C2, fill: C2, sw: 1.4 }));
    g.push(arw(502, y1 + 18, 476, y1 + 18, { cls: 'ark', width: 1.4 }));
    g.push(tbox(502, y1, 110, 36, ['받은 서명'], { stroke: C3, fill: C3, sw: 1.4 }));
    g.push(arw(324, y1 + 18, 298, y1 + 18, { cls: 'ark', width: 1.4 }));
    g.push(txt(624, y1 + 22, '두 해시가 같으면 확인 끝', { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[242, y0 + 38], [242, y1 - 2]], { stroke: CK, sw: 1.4, dash: '4 3' }));
    g.push(txt(252, y0 + 58, '같은가?', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(40, y1 + 62, '메시지가 한 비트라도 바뀌면 해시가 달라지고, 서명은 비밀키 없이 다시 만들 수 없다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[40, 200], [W - 40, 200]], { stroke: CG, sw: 1 }));

    // 아래: 인증서 사슬
    const y2 = 232;
    g.push(txt(40, y2, '그런데 그 공개키가 정말 그 사람 것인가 — 인증서가 답하는 질문이다', { cls: 'ink bold', size: 'sm' }));
    const chain = [
        ['뿌리 인증기관', ['브라우저와 운영체제에', '미리 심어져 있다'], C3],
        ['중간 인증기관', ['뿌리의 비밀키로', '서명받은 인증서'], C1],
        ['서버 인증서', ['중간의 비밀키로', '서명받았고, 서버의', '공개키와 이름이 담겨 있다'], C2],
    ];
    chain.forEach((c, i) => {
        const x = 40 + i * 244;
        g.push(box(x, y2 + 16, 214, 84, { stroke: c[2], sw: 1.5, rx: 5 }));
        g.push(txt(x + 12, y2 + 38, c[0], { cls: 'ink bold', size: 'sm' }));
        c[1].forEach((t, j) => g.push(txt(x + 12, y2 + 58 + j * 17, t, { cls: 'ink2', size: 'sm' })));
        if (i < 2) g.push(arw(x + 214, y2 + 58, x + 240, y2 + 58, { cls: 'ark', width: 1.6 }));
    });
    g.push(txt(772, y2 + 52, '이 사슬을', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(772, y2 + 70, '거꾸로 올라가며', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(772, y2 + 88, '서명을 확인한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 366, '디피-헬만만으로는 가운데 낀 사람을 막을 수 없었다. 상대의 공개키가 진짜인지 확인해 주는 것이 이 사슬이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 388, '믿음의 출발점은 뿌리 인증기관이다. 그 하나가 뚫리면 그 아래 전부가 무너진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 408, '그래서 인증서에는 만료 시각이 있고, 잘못 발급된 것을 취소하는 별도의 절차가 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'alg-s-signature-chain',
        svg: svg({
            width: W, height: H,
            title: '전자 서명과 인증서 사슬',
            desc: '비밀키로 서명하고 공개키로 확인한다. 그 공개키가 진짜인지는 인증서 사슬이 보증한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-8. CSRF — 브라우저가 쿠키를 대신 붙여 준다 ---- */
add((() => {
    const W = 844, H = 424;
    const g = [];
    g.push(txt(W / 2, 26, '요청을 누가 보냈는지 서버가 구분하지 못한다', { anchor: 'middle', cls: 'ink bold' }));

    const lanes = ['이용자의 브라우저', '공격자 사이트 A', '은행 B'];
    const lx = [130, 400, 670];
    lanes.forEach((L, i) => {
        g.push(txt(lx[i], 58, L, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(ln([[lx[i], 68], [lx[i], 300]], { stroke: CG, sw: 1, dash: '4 4' }));
    });

    const msgs = [
        [0, 2, '① 로그인한다 — 서버가 쿠키를 심어 준다', 96, C1],
        [1, 0, '② 다른 일로 A 를 방문한다', 148, CK],
        [0, 2, '③ A 의 페이지 안에 숨어 있던 폼이 B 로 요청을 보낸다', 200, C2],
        [2, 0, '④ B 는 쿠키가 붙어 있으니 본인이라 여기고 처리한다', 252, C2],
    ];
    msgs.forEach((m) => {
        const from = lx[m[0]], to = lx[m[1]];
        g.push(arw(from, m[3], to, m[3], { cls: m[4] === C1 ? 's1' : m[4] === C2 ? 's2' : 'ark', width: 1.7 }));
        g.push(txt((from + to) / 2, m[3] - 8, m[2], { anchor: 'middle', cls: m[4] === C2 ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, 288, '이용자는 ③ 을 누른 적도 본 적도 없다. 브라우저가 B 로 가는 요청에 쿠키를 자동으로 붙였을 뿐이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[40, 314], [W - 40, 314]], { stroke: CG, sw: 1 }));
    g.push(txt(40, 336, '무엇이 잘못 설계되었나', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(220, 336, '쿠키는 ‘어느 서버로 가는가’ 만 보고 붙는다. ‘누가 이 요청을 일으켰는가’ 는 담기지 않는다', { cls: 'ink2', size: 'sm' }));
    const fixes = [
        ['SameSite 쿠키', '다른 사이트에서 시작된 요청에는 쿠키를 붙이지 않는다 — 가장 근본적인 수정'],
        ['요청 토큰', '서버가 준 값을 요청 본문에 함께 넣게 한다. 남의 사이트는 그 값을 읽을 수 없다'],
        ['출처 헤더 검사', 'Origin · Sec-Fetch-Site 로 요청이 어디서 시작됐는지 서버가 직접 본다'],
    ];
    fixes.forEach((f, i) => {
        g.push(txt(40, 364 + i * 20, '· ' + f[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(220, 364 + i * 20, f[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(40, 418, '남의 스크립트가 페이지 안에서 도는 것을 허용하면 이 방어는 전부 무력해진다. 그 스크립트는 토큰도 읽어 갈 수 있다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'alg-s-csrf-flow',
        svg: svg({
            width: W, height: H,
            title: 'CSRF — 브라우저가 쿠키를 대신 붙여 준다',
            desc: '이용자가 로그인해 둔 사이트로 남의 페이지가 요청을 보내면 쿠키가 자동으로 붙어 정상 요청처럼 처리된다',
            body: g.join(''),
        }),
    };
})());

export default figures;
