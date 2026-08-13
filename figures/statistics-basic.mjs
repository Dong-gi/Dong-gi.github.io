/**
 * 통계학 3~6장(확률·확률변수·적률·결합분포)의 그림.
 *
 * 이름은 전부 `st-b-` 로 시작한다(담당 D 에 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `x~0` 표기를, 나머지는 유니코드(√ π σ μ ρ ∫ Σ ≈ ≤ ≥)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에서 `~` 를 범위 표기로 쓰면 안 되고,
 * 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 난수를 쓰는 그림은 씨앗을 고정한 선형합동생성기를 쓴다.
 * 빌드할 때마다 그림이 달라지면 안 되기 때문이다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const PI = Math.PI;

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

/**
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어
 * 선이 사라지고 화살촉만 남는다. 색을 직접 넣는 화살표를 따로 둔다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 2, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 양쪽 화살표. 길이나 범위를 재는 표시에 쓴다. */
function arw2(x1, y1, x2, y2, { cls = 'ark', width = 1.6 } = {}) {
    const col = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)' }[cls] ?? 'var(--ink2)';
    const mk = cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark';
    return `<path fill="none" stroke="${col}" stroke-width="${width}" marker-start="url(#${mk})" marker-end="url(#${mk})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 채운 점. */
const pdot = (x, y, col = 'var(--s1)', r = 4) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 빈 점 — 그 자리에 값이 없다는 뜻. 안쪽을 배경색으로 채워야 밑줄이 비친다. */
const odot = (x, y, col = 'var(--s2)', r = 4.5) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="var(--bgfix)" stroke="${col}" stroke-width="2"/>`;

/** odot 이 쓰는 배경색. 사이트 다크 모드 배경이 #121212 다. */
const BG = '<style>svg{--bgfix:#ffffff}@media (prefers-color-scheme:dark){svg{--bgfix:#121212}}</style>';

/** 패널 테두리 + 제목. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 21, title, { anchor: 'middle', cls: 'ink bold' }) : '')
        + (sub ? txt(x + w / 2, y + 38, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 데이터 좌표 다각형 채우기. */
const poly = (g, pts, col, op) =>
    `<path d="M${pts.map(([x, y]) => `${g.X(x)} ${g.Y(y)}`).join(' L')} Z" fill="${col}" fill-opacity="${op}" stroke="none"/>`;

/** 데이터 좌표로 준 곡선 아래 영역을 채운다. */
function underArea(g, f, from, to, col, op, base = 0, steps = 80) {
    const pts = [[from, base]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([x, f(x)]);
    }
    pts.push([to, base]);
    return poly(g, pts, col, op);
}

/** 데이터 좌표 직사각형. y1 < y2 를 가정한다. */
const drect = (g, x1, y1, x2, y2, o) =>
    box(g.X(x1), g.Y(y2), g.X(x2) - g.X(x1), g.Y(y1) - g.Y(y2), o);

/** 씨앗을 고정한 난수. 빌드 결과가 매번 같아야 한다. */
function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}

/** 표준정규 난수 두 개(박스-뮐러). */
function gauss(rand) {
    const u = Math.max(rand(), 1e-9), v = rand();
    const R = Math.sqrt(-2 * Math.log(u));
    return [R * Math.cos(2 * PI * v), R * Math.sin(2 * PI * v)];
}

/**
 * 회색(ink2) 곡선. frame().curve 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * s1/s2/s3 클래스만 있어 'ark' 를 주면 선이 통째로 사라진다.
 */
function curveInk(fr, f, { from, to, dash, sw = 1.8, steps = 140 }) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    return ln(pts, { stroke: 'var(--ink2)', sw, dash });
}

/** 세로축을 데이터 0 이 아니라 왼쪽 가장자리에 세우는 축. 값이 0 부터인 막대그림에 쓴다. */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [] }) {
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${fr.X(x1) + 10}"/>`,
        `<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${fr.Y(y1) - 10}"/>`];
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 16, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return out.join('');
}

/** 표준정규 밀도. */
const phi = z => Math.exp(-(z * z) / 2) / Math.sqrt(2 * PI);

/* ================================================================== *
 * 3장 — 확률을 다시 세운다
 * ================================================================== */

/* ---- 3-1. 사건은 집합이고 연산도 집합 연산이다 ---- */
add((() => {
    const W = 740, H = 300;
    const cell = (bx, title, paint) => {
        const x = bx, y = 66, w = 158, h = 112;
        const out = [box(x, y, w, h, { stroke: 'var(--ink2)', sw: 1.2, rx: 4 })];
        out.push(txt(x + 7, y + 15, 'S', { cls: 'ink2', size: 'sm' }));
        out.push(...paint(x, y, w, h));
        out.push(circ(x + 58, y + 60, 37, { stroke: 'var(--s1)', sw: 1.6 }));
        out.push(circ(x + 100, y + 60, 37, { stroke: 'var(--s2)', sw: 1.6 }));
        out.push(txt(x + 32, y + 64, 'A', { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(x + 126, y + 64, 'B', { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(x + w / 2, y - 12, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        return out.join('');
    };
    const clipA = (id, x, y) => `<clipPath id="${id}"><circle cx="${x + 58}" cy="${y + 60}" r="37"/></clipPath>`;
    const g = [
        txt(370, 32, '사건은 표본공간 S 의 부분집합이다. 말과 집합 연산이 하나씩 짝을 이룬다', { anchor: 'middle', cls: 'ink' }),
        cell(28, '둘 중 적어도 하나   A ∪ B', (x, y) => [
            circ(x + 58, y + 60, 37, { fill: 'var(--s1)', op: 0.25, stroke: 'none' }),
            circ(x + 100, y + 60, 37, { fill: 'var(--s1)', op: 0.25, stroke: 'none' }),
        ]),
        cell(210, '둘 다   A ∩ B', (x, y) => [
            clipA('cA1', x, y),
            `<g clip-path="url(#cA1)">${circ(x + 100, y + 60, 37, { fill: 'var(--s2)', op: 0.5, stroke: 'none' })}</g>`,
        ]),
        cell(392, '일어나지 않는다   Aᶜ', (x, y, w, h) => [
            `<mask id="mA"><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="white"/><circle cx="${x + 58}" cy="${y + 60}" r="37" fill="black"/></mask>`,
            `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="var(--s3)" fill-opacity="0.3" mask="url(#mA)"/>`,
        ]),
        cell(554, 'A 이지만 B 는 아니다', (x, y) => [
            `<mask id="mAB"><circle cx="${x + 58}" cy="${y + 60}" r="37" fill="white"/><circle cx="${x + 100}" cy="${y + 60}" r="37" fill="black"/></mask>`,
            `<circle cx="${x + 58}" cy="${y + 60}" r="37" fill="var(--s1)" fill-opacity="0.35" mask="url(#mAB)"/>`,
        ]),
        ln([[28, 212], [712, 212]], { stroke: 'var(--grid)', sw: 1 }),
        txt(370, 240, '겹치는 부분이 비면(A ∩ B = ∅) 두 사건은 배반이다. 배반은 집합의 관계이고 독립은 확률의 관계다', { anchor: 'middle', cls: 'ink' }),
        txt(370, 266, '공리가 그냥 더해도 된다고 보증하는 것은 왼쪽 그림이 아니라 배반인 경우뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'st-b-event-algebra',
        svg: svg({
            width: W, height: H,
            title: '사건 연산은 집합 연산이다',
            desc: '합집합 교집합 여집합 차집합을 벤 다이어그램 네 칸으로 보인 것. 겹침이 없으면 배반이다',
            body: BG + g,
        }),
    };
})());

/* ---- 3-2. 공리 세 줄에서 나오는 성질들 ---- */
add((() => {
    const W = 740, H = 320;
    const g = [];
    g.push(txt(370, 28, '공리는 셋뿐이다. 나머지는 전부 여기서 끌어낸 것이다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽 — 단조성
    g.push(panel(40, 48, 210, 190, '포함하면 크다'));
    g.push(box(64, 92, 162, 118, { stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    g.push(circ(145, 152, 52, { fill: 'var(--s1)', op: 0.18, stroke: 'var(--s1)', sw: 1.5 }));
    g.push(circ(132, 158, 27, { fill: 'var(--s2)', op: 0.4, stroke: 'var(--s2)', sw: 1.5 }));
    g.push(txt(132, 162, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(186, 116, 'B', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(145, 228, 'A ⊂ B ⟹ P(A) ≤ P(B)', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 가운데 — 여사건
    g.push(panel(266, 48, 210, 190, '전체에서 뺀다'));
    g.push(box(290, 92, 162, 118, { stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    g.push(`<mask id="mC"><rect x="290" y="92" width="162" height="118" fill="white"/><circle cx="352" cy="151" r="40" fill="black"/></mask>`);
    g.push(`<rect x="290" y="92" width="162" height="118" fill="var(--s3)" fill-opacity="0.3" mask="url(#mC)"/>`);
    g.push(circ(352, 151, 40, { fill: 'var(--s1)', op: 0.28, stroke: 'var(--s1)', sw: 1.5 }));
    g.push(txt(352, 156, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(424, 110, 'Aᶜ', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(371, 228, 'P(Aᶜ) = 1 − P(A)', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 오른쪽 — 덧셈정리
    g.push(panel(492, 48, 210, 190, '겹친 만큼 뺀다'));
    g.push(box(516, 92, 162, 118, { stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    g.push(circ(576, 151, 42, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)', sw: 1.5 }));
    g.push(circ(622, 151, 42, { fill: 'var(--s2)', op: 0.25, stroke: 'var(--s2)', sw: 1.5 }));
    g.push(`<clipPath id="cX"><circle cx="576" cy="151" r="42"/></clipPath>`);
    g.push(`<g clip-path="url(#cX)"><circle cx="622" cy="151" r="42" fill="var(--s2)" fill-opacity="0.45"/></g>`);
    g.push(txt(550, 156, 'A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(648, 156, 'B', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(597, 228, 'P(A∪B) = P(A)+P(B)−P(A∩B)', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(ln([[40, 254], [700, 254]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 278, '증명의 요령은 하나다. 겹치지 않게 조각으로 쪼갠 뒤 셋째 공리를 쓴다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 302, '예컨대 B 를 A 와 (B − A) 로 쪼개면 P(B) = P(A) + P(B−A) ≥ P(A) 가 곧바로 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-axiom-derive',
        svg: svg({
            width: W, height: H,
            title: '콜모고로프 공리에서 유도되는 세 가지 성질',
            desc: '단조성 여사건 덧셈정리를 벤 다이어그램으로 보인 것. 셋 다 배반인 조각으로 쪼갠 뒤 셋째 공리를 쓴 결과다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-3. 기하학적 확률 — 약속 문제 ---- */
add((() => {
    const W = 700, H = 350;
    const fr = frame({ xRange: [0, 60], yRange: [0, 60], box: { x: 92, y: 50, w: 232, h: 232 } });
    const g = [];
    // |x - y| <= 15 인 띠
    g.push(poly(fr, [[0, 0], [0, 15], [45, 60], [60, 60], [60, 45], [15, 0]], 'var(--s1)', 0.28));
    g.push(box(fr.X(0), fr.Y(60), fr.X(60) - fr.X(0), fr.Y(0) - fr.Y(60), { stroke: 'var(--ink2)', sw: 1.4, rx: 0 }));
    g.push(fr.line([[0, 15], [45, 60]], { cls: 's2' }));
    g.push(fr.line([[15, 0], [60, 45]], { cls: 's2' }));
    for (const t of [0, 15, 30, 45, 60]) {
        g.push(txt(fr.X(t), fr.Y(0) + 18, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(fr.X(0) - 8, fr.Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(fr.X(30), fr.Y(0) + 38, '먼저 정한 도착 시각 x (분)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(74, 40, 'y (분)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(30), fr.Y(30) + 5, '만난다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(fr.X(13), fr.Y(48) + 4, '못 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(47), fr.Y(12) + 4, '못 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(366, 62, '두 사람이 한 시간 안에 아무 때나 와서', { cls: 'ink' }));
    g.push(txt(366, 84, '15분만 기다린다. 결과가 무한히 많으니', { cls: 'ink' }));
    g.push(txt(366, 106, '세어서 나눌 수 없다. 넓이의 비로 정한다.', { cls: 'ink' }));
    g.push(txt(366, 144, '전체 넓이 = 60 × 60 = 3600', { cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 166, '못 만나는 두 삼각형', { cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 186, '= 2 × (45 × 45 ÷ 2) = 2025', { cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 208, '만나는 넓이 = 3600 − 2025 = 1575', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[366, 222], [664, 222]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(366, 248, 'P(만난다) = 1575 / 3600 = 0.4375', { cls: 'ink bold' }));
    g.push(txt(366, 280, '넓이 비가 확률이 되려면 ‘한 점을 고르는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 300, '방식이 균등하다’는 약속이 먼저 있어야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(366, 320, '그 약속을 바꾸면 답도 바뀐다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-geometric-meet',
        svg: svg({
            width: W, height: H,
            title: '기하학적 확률 — 넓이의 비로 확률을 정한다',
            desc: '두 도착 시각을 좌표로 삼으면 만나는 경우가 정사각형 안의 띠가 된다. 그 넓이 비가 확률이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-4. 조건부확률 — 표본공간을 줄이는 일 ---- */
add((() => {
    const W = 720, H = 330;
    const g = [];
    const grid = (ox, oy, keep) => {
        const s = 26;
        const out = [];
        for (let i = 1; i <= 6; i += 1) {
            for (let j = 1; j <= 6; j += 1) {
                const x = ox + (i - 1) * s, y = oy + (j - 1) * s;
                const inB = i + j >= 9;
                const inA = i === j;
                if (keep && !inB) continue;
                const fill = inA ? 'var(--s2)' : inB ? 'var(--s1)' : 'none';
                const op = inA ? 0.55 : inB ? 0.18 : 0;
                out.push(box(x, y, s, s, { fill, op, stroke: 'var(--grid)', sw: 1, rx: 0 }));
                if (inA) out.push(box(x, y, s, s, { stroke: 'var(--s2)', sw: 1.8, rx: 0 }));
            }
        }
        return out.join('');
    };
    g.push(txt(360, 28, '조건부확률은 새 규칙이 아니라 분모를 바꾸는 일이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(148, 58, '아무것도 모를 때 — 칸 36개', { anchor: 'middle', cls: 'ink' }));
    g.push(grid(70, 76, false));
    g.push(box(70, 76, 156, 156, { stroke: 'var(--ink2)', sw: 1.4, rx: 0 }));
    g.push(txt(148, 254, 'P(두 눈이 같다) = 6/36 ≈ 0.167', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(arw(240, 154, 306, 154, { cls: 'ark', width: 1.8 }));
    g.push(txt(273, 140, '‘합이 9 이상’을', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(273, 176, '알게 되면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(420, 58, '알고 난 뒤 — 칸 10개', { anchor: 'middle', cls: 'ink' }));
    g.push(grid(342, 76, true));
    g.push(txt(420, 254, 'P(두 눈이 같다 | 합 ≥ 9) = 2/10 = 0.2', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(524, 104, '테두리 친 칸이 ‘두 눈이 같다’', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 124, '연한 칸이 ‘합이 9 이상’', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 152, '조건을 알면 6개 중 4개가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 172, '사라지고 (5,5) 와 (6,6) 만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 192, '남는다. 분모는 36 에서 10 으로,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(524, 212, '분자는 6 에서 2 로 줄었다.', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[70, 274], [660, 274]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 300, 'P(A|B) = P(A∩B) / P(B) 의 분자 분모를 36으로 곱하면 그대로 칸 수의 비가 된다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 322, '분모가 S 에서 B 로 줄었을 뿐, 확률의 공리는 줄어든 세계 안에서도 그대로 성립한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-cond-grid',
        svg: svg({
            width: W, height: H,
            title: '조건부확률은 표본공간을 줄이는 일이다',
            desc: '주사위 두 개의 36칸 격자에서 합이 9 이상인 10칸만 남기면 분모가 바뀐다. 그것이 조건부확률이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-5. 나무 그림 — 전확률과 베이즈 ---- */
add((() => {
    const W = 760, H = 340;
    const g = [];
    const rootX = 92, rootY = 168;
    const midX = 300, leafX = 540;
    const midY = [104, 236];
    const leafY = [[68, 140], [200, 272]];
    const pB = [0.30, 0.70];
    const pAgB = [0.80, 0.15];
    g.push(txt(380, 26, '가지를 따라 곱하고, 끝을 모아 더한다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(pdot(rootX, rootY, 'var(--ink2)', 4));
    g.push(txt(rootX, rootY + 24, '시작', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const names = ['원인 B~1 (기계 1)', '원인 B~2 (기계 2)'];
    let joint = [];
    for (let i = 0; i < 2; i += 1) {
        g.push(ln([[rootX + 8, rootY], [midX - 12, midY[i]]], { stroke: 'var(--ink2)', sw: 1.4 }));
        g.push(txt((rootX + midX) / 2 - 6, (rootY + midY[i]) / 2 - 8, `P = ${pB[i].toFixed(2)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(pdot(midX, midY[i], i === 0 ? 'var(--s1)' : 'var(--s3)', 4));
        g.push(txt(midX - 12, midY[i] + (i === 0 ? -14 : 22), names[i], { anchor: 'middle', cls: 'ink' }));
        for (let j = 0; j < 2; j += 1) {
            const p = j === 0 ? pAgB[i] : 1 - pAgB[i];
            g.push(ln([[midX + 8, midY[i]], [leafX - 12, leafY[i][j]]], { stroke: j === 0 ? 'var(--s2)' : 'var(--grid)', sw: j === 0 ? 1.8 : 1.2 }));
            g.push(txt((midX + leafX) / 2, (midY[i] + leafY[i][j]) / 2 - 7, `P = ${p.toFixed(2)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            const v = pB[i] * p;
            g.push(pdot(leafX, leafY[i][j], j === 0 ? 'var(--s2)' : 'var(--grid)', 4));
            g.push(txt(leafX + 12, leafY[i][j] + 4, `${j === 0 ? '불량' : '정상'}   ${pB[i].toFixed(2)} × ${p.toFixed(2)} = ${v.toFixed(3)}`, { cls: j === 0 ? 'ink bold' : 'ink2', size: j === 0 ? undefined : 'sm' }));
            if (j === 0) joint.push(v);
        }
    }
    const tot = joint[0] + joint[1];
    g.push(ln([[92, 296], [700, 296]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(92, 318, `전확률: P(불량) = ${joint[0].toFixed(3)} + ${joint[1].toFixed(3)} = ${tot.toFixed(3)}`, { cls: 'ink' }));
    g.push(txt(430, 318, `베이즈: P(B~1 | 불량) = ${joint[0].toFixed(3)} / ${tot.toFixed(3)} ≈ ${(joint[0] / tot).toFixed(3)}`, { cls: 'ink' }));
    return {
        name: 'st-b-bayes-tree',
        svg: svg({
            width: W, height: H,
            title: '나무 그림으로 본 전확률 정리와 베이즈 정리',
            desc: '두 원인에서 갈라진 가지의 확률을 곱해 끝값을 얻고 그것을 더하면 전확률, 그중 하나의 몫이 베이즈의 답이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-6. 쌍마다 독립인데 셋이 독립은 아니다 ---- */
add((() => {
    const W = 720, H = 330;
    const g = [];
    // 표본공간: 두 동전. 결과 4개. A=첫째 앞, B=둘째 앞, C=둘이 같다
    const s = 74, ox = 76, oy = 84;
    const outcomes = [
        { i: 0, j: 0, name: '앞 앞', A: 1, B: 1, C: 1 },
        { i: 1, j: 0, name: '앞 뒤', A: 1, B: 0, C: 0 },
        { i: 0, j: 1, name: '뒤 앞', A: 0, B: 1, C: 0 },
        { i: 1, j: 1, name: '뒤 뒤', A: 0, B: 0, C: 1 },
    ];
    g.push(txt(360, 28, '동전 두 개. A = 첫째가 앞, B = 둘째가 앞, C = 둘의 면이 같다', { anchor: 'middle', cls: 'ink bold' }));
    for (const o of outcomes) {
        const x = ox + o.i * s, y = oy + o.j * s;
        const inAll = o.A && o.B && o.C;
        g.push(box(x, y, s, s, { fill: inAll ? 'var(--s2)' : 'var(--s1)', op: inAll ? 0.5 : 0.12, stroke: 'var(--ink2)', sw: 1.2, rx: 0 }));
        g.push(txt(x + s / 2, y + 28, o.name, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(x + s / 2, y + 48, `확률 1/4`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const tag = [o.A ? 'A' : '', o.B ? 'B' : '', o.C ? 'C' : ''].filter(Boolean).join(' ');
        g.push(txt(x + s / 2, y + 66, tag || '없음', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(150, 250, '세 사건 모두 확률 1/2 이다', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    const rows = [
        ['P(A∩B) = 1/4', 'P(A)P(B) = 1/4', '같다'],
        ['P(A∩C) = 1/4', 'P(A)P(C) = 1/4', '같다'],
        ['P(B∩C) = 1/4', 'P(B)P(C) = 1/4', '같다'],
        ['P(A∩B∩C) = 1/4', 'P(A)P(B)P(C) = 1/8', '다르다'],
    ];
    rows.forEach((r, k) => {
        const y = 96 + k * 34;
        const last = k === 3;
        g.push(box(258, y - 20, 400, 28, { fill: last ? 'var(--s2)' : 'none', op: last ? 0.14 : 0, stroke: 'var(--grid)', sw: 1, rx: 4 }));
        g.push(txt(268, y, r[0], { cls: last ? 'ink bold' : 'ink' }));
        g.push(txt(430, y, r[1], { cls: last ? 'ink bold' : 'ink' }));
        g.push(txt(600, y, r[2], { cls: last ? 'ink bold' : 'ink2', size: last ? undefined : 'sm' }));
    });
    g.push(ln([[76, 262], [660, 262]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 286, '셋을 짝지어 보면 전부 독립인데 셋을 한꺼번에 보면 독립이 아니다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(360, 310, 'A 와 B 를 알면 C 는 완전히 정해진다. 그래서 세 사건의 독립은 짝만으로 정의할 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-three-independence',
        svg: svg({
            width: W, height: H,
            title: '쌍마다 독립이지만 셋이 독립은 아닌 예',
            desc: '동전 두 개에서 만든 세 사건은 짝마다 곱셈이 성립하지만 셋의 교집합에서는 성립하지 않는다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-7. 심슨의 역설 ---- */
add((() => {
    const W = 740, H = 340;
    const g = [];
    // 직접 만든 작은 표. 두 병원, 두 환자군.
    const data = {
        가: { 경증: [90, 100], 중증: [30, 100] },   // [성공, 시도]
        나: { 경증: [19, 20], 중증: [72, 180] },
    };
    const rate = (a) => a[0] / a[1];
    const bar = (x, y, w, v, cls, l1, l2) => {
        const h = 118;
        return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 2 })
            + box(x, y + h - h * v, w, h * v, { fill: `var(--${cls})`, op: 0.45, stroke: `var(--${cls})`, sw: 1.2, rx: 2 })
            + txt(x + w / 2, y + h - h * v - 8, `${(v * 100).toFixed(0)}%`, { anchor: 'middle', cls: 'ink bold' })
            + txt(x + w / 2, y + h + 18, l1, { anchor: 'middle', cls: 'ink2', size: 'sm' })
            + txt(x + w / 2, y + h + 34, l2, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    };
    g.push(txt(370, 28, '두 병원의 치료 성공률 — 직접 만든 표다', { anchor: 'middle', cls: 'ink bold' }));
    const groups = [
        { x: 54, title: '경증 환자', a: data.가.경증, b: data.나.경증 },
        { x: 250, title: '중증 환자', a: data.가.중증, b: data.나.중증 },
        { x: 496, title: '합쳐 보면', a: [120, 200], b: [91, 200] },
    ];
    for (const gr of groups) {
        g.push(txt(gr.x + 76, 62, gr.title, { anchor: 'middle', cls: 'ink bold' }));
        g.push(bar(gr.x + 6, 80, 54, rate(gr.a), 's1', '병원 가', `${gr.a[0]}/${gr.a[1]}`));
        g.push(bar(gr.x + 92, 80, 54, rate(gr.b), 's2', '병원 나', `${gr.b[0]}/${gr.b[1]}`));
    }
    g.push(ln([[452, 56], [452, 252]], { stroke: 'var(--grid)', sw: 1, dash: '5 4' }));
    g.push(txt(216, 256, '두 군 모두 병원 나가 앞선다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(572, 256, '그런데 뒤집힌다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[54, 274], [686, 274]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 298, '병원 가는 경증 환자를, 병원 나는 중증 환자를 훨씬 많이 받았다. 환자 구성이 다른 것이 원인이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 322, '조건부확률을 조건 없이 합치면 이런 뒤집힘이 일어난다. 합친 수만 보고 비교하면 안 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-simpson',
        svg: svg({
            width: W, height: H,
            title: '심슨의 역설 — 나눠 보면 이기는데 합치면 진다',
            desc: '두 환자군 모두에서 앞서던 쪽이 합친 수치에서는 뒤진다. 두 병원의 환자 구성이 다르기 때문이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 3-8. 도박사의 오류 ---- */
add((() => {
    const W = 740, H = 372;
    const N = 400;
    const rand = rng(20260813);
    const ratio = [], diff = [];
    let heads = 0;
    for (let i = 1; i <= N; i += 1) {
        if (rand() < 0.5) heads += 1;
        ratio.push([i, heads / i]);
        diff.push([i, heads - (i - heads)]);
    }
    const L = frame({ xRange: [0, N], yRange: [0.2, 0.8], box: { x: 74, y: 76, w: 250, h: 160 } });
    const R = frame({ xRange: [0, N], yRange: [-34, 34], box: { x: 434, y: 76, w: 236, h: 160 } });
    const g = [];
    g.push(txt(199, 52, '앞면의 비율', { anchor: 'middle', cls: 'ink bold' }));
    g.push(L.axes({ xTicks: [0, 200, 400], yTicks: [0.2, 0.5, 0.8], grid: false }));
    g.push(ln([[L.X(0), L.Y(0.5)], [L.X(N), L.Y(0.5)]], { stroke: 'var(--ink2)', sw: 1.2, dash: '6 4' }));
    g.push(L.line(ratio, { cls: 's1' }));
    g.push(txt(199, 296, '0.5 로 다가간다', { anchor: 'middle', cls: 'ink' }));

    g.push(txt(552, 52, '앞면 수 − 뒷면 수', { anchor: 'middle', cls: 'ink bold' }));
    g.push(R.axes({ xTicks: [0, 200, 400], yTicks: [-20, 0, 20], grid: false }));
    g.push(R.line(diff, { cls: 's2' }));
    g.push(txt(552, 296, '0 으로 돌아가지 않는다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(199, 272, '던진 횟수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(552, 272, '던진 횟수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(342, 156, 414, 156, { cls: 'ark', width: 1.6 }));
    g.push(txt(378, 142, '같은 자료', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[74, 314], [670, 314]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 338, '‘앞면이 계속 나왔으니 이제 뒷면 차례’ 라는 말이 틀린 이유가 오른쪽 그림이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 362, '비율이 0.5 로 가는 것은 차이가 메워져서가 아니라 나누는 수가 커져서다. 동전은 과거를 기억하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-gambler',
        svg: svg({
            width: W, height: H,
            title: '도박사의 오류 — 비율은 모여도 차이는 커진다',
            desc: '같은 동전 던지기 자료에서 앞면 비율은 0.5 로 수렴하지만 앞면과 뒷면의 개수 차이는 0 으로 돌아가지 않는다',
            body: BG + g.join(''),
        }),
    };
})());


/* ================================================================== *
 * 4장 — 확률변수와 분포
 * ================================================================== */

/* ---- 4-1. 확률변수는 함수다 ---- */
add((() => {
    const W = 700, H = 300;
    const g = [];
    const items = [
        { name: '앞 앞', v: 2 }, { name: '앞 뒤', v: 1 },
        { name: '뒤 앞', v: 1 }, { name: '뒤 뒤', v: 0 },
    ];
    g.push(txt(350, 30, '확률변수 X 는 표본공간의 결과 하나하나에 실수 하나를 붙이는 함수다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(`<ellipse cx="130" cy="150" rx="82" ry="94" fill="none" stroke="var(--ink2)" stroke-width="1.4"/>`);
    g.push(txt(130, 44, '표본공간 S', { anchor: 'middle', cls: 'ink' }));
    const fr = frame({ xRange: [-0.4, 2.4], yRange: [0, 1], box: { x: 420, y: 110, w: 200, h: 1 } });
    items.forEach((it, i) => {
        const y = 92 + i * 38;
        g.push(pdot(96, y, 'var(--s1)', 4));
        g.push(txt(110, y + 5, it.name, { cls: 'ink' }));
        g.push(arw(206, y, fr.X(it.v) - 8, 170, { cls: 's2', width: 1.3 }));
    });
    g.push(ln([[fr.X(-0.4), 172], [fr.X(2.4), 172]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const v of [0, 1, 2]) {
        g.push(ln([[fr.X(v), 166], [fr.X(v), 178]], { stroke: 'var(--ink2)', sw: 1.4 }));
        g.push(txt(fr.X(v), 196, String(v), { anchor: 'middle', cls: 'ink bold' }));
    }
    g.push(txt(520, 78, '실수 — X 가 가지는 값', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(520, 100, '(앞면이 나온 횟수)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(1), 220, '결과 두 개가 같은 값으로 간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(fr.X(1), 240, 'P(X = 1) = 1/4 + 1/4 = 1/2', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[48, 258], [652, 258]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 282, '‘X = 1’ 은 수가 아니라 사건이다. 그 값으로 가는 결과들을 모은 부분집합을 가리킨다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-rv-map',
        svg: svg({
            width: W, height: H,
            title: '확률변수는 표본공간에서 실수로 가는 함수다',
            desc: '동전 두 개의 결과 네 개가 앞면 횟수 0 1 2 로 옮겨진다. 값 하나에 결과 여럿이 모이면 확률이 더해진다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-2. 확률질량함수 ---- */
add((() => {
    const W = 700, H = 356;
    const fr = frame({ xRange: [1, 13], yRange: [0, 0.2], box: { x: 76, y: 66, w: 470, h: 170 } });
    const g = [fr.axes({ xTicks: [2, 4, 6, 8, 10, 12], yTicks: [0.05, 0.1, 0.15], grid: false })];
    for (let k = 2; k <= 12; k += 1) {
        const p = (6 - Math.abs(k - 7)) / 36;
        g.push(drect(fr, k - 0.38, 0, k + 0.38, p, { fill: 'var(--s1)', op: 0.45, stroke: 'var(--s1)', sw: 1.2, rx: 1 }));
        g.push(txt(fr.X(k), fr.Y(p) - 7, `${6 - Math.abs(k - 7)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(fr.X(7), 44, '주사위 두 개의 합 X — 확률질량함수 p(x)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(fr.X(7), fr.Y(0) + 38, 'x (두 눈의 합)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(72, 52, 'p(x)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 96, '막대 위의 수는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 116, '그 합을 만드는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 136, '칸의 개수다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 166, '모두 더하면 36,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 186, '확률의 합은 1 이다.', { cls: 'ink bold' }));
    g.push(ln([[76, 292], [648, 292]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 316, '이산확률변수에서는 막대의 높이가 그대로 확률이다. p(x) ≥ 0 이고 높이를 다 더하면 1 이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 340, '값 사이의 자리(예컨대 4.5)에는 막대가 없다. 그 값은 나오지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-pmf-sum2',
        svg: svg({
            width: W, height: H,
            title: '확률질량함수 — 주사위 두 개의 합',
            desc: '합 2 부터 12 까지의 확률을 막대로 그린 것. 7 에서 가장 높고 높이의 합은 1 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-3. 밀도는 확률이 아니다 (이 문서의 핵심 그림) ---- */
add((() => {
    const W = 780, H = 400;
    const g = [];
    g.push(txt(390, 30, '밀도는 확률이 아니다 — 확률은 넓이다', { anchor: 'middle', cls: 'ink bold' }));

    // 패널 1: 밀도가 1 보다 클 수 있다
    const A = frame({ xRange: [-0.1, 0.75], yRange: [0, 3.0], box: { x: 62, y: 88, w: 176, h: 150 } });
    g.push(txt(150, 62, '높이가 1 을 넘어도 된다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(drect(A, 0, 0, 0.4, 2.5, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1.8, rx: 0 }));
    g.push(A.axes({ xTicks: [0, 0.4], yTicks: [1, 2, 3], grid: false }));
    g.push(A.label([0.2, 2.5], 'f(x) = 2.5', { dx: 0, dy: -12, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(A.label([0.2, 1.25], '넓이 1', { dx: 0, dy: 0, anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(150, 272, '0.4 × 2.5 = 1 이므로 넓이는 1 이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(150, 290, '높이 2.5 는 확률일 수 없다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 패널 2: 좁은 구간의 확률 ≈ f(x)Δx
    const f = x => Math.exp(-((x - 3) ** 2) / 2) / Math.sqrt(2 * PI);
    const B = frame({ xRange: [0.2, 5.8], yRange: [0, 0.46], box: { x: 296, y: 88, w: 176, h: 150 } });
    g.push(txt(384, 62, '좁은 띠의 넓이가 확률이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(underArea(B, f, 0.2, 5.8, 'var(--s1)', 0.12));
    g.push(drect(B, 3.6, 0, 4.0, f(3.8), { fill: 'var(--s2)', op: 0.55, stroke: 'var(--s2)', sw: 1.2, rx: 0 }));
    g.push(B.axes({ xTicks: [1, 3, 5], yTicks: [], grid: false }));
    g.push(B.curve(f, { from: 0.25, to: 5.75, cls: 's2' }));
    g.push(arw(B.X(4.0) + 46, B.Y(0.30), B.X(3.9) + 6, B.Y(f(3.8)) - 4, { cls: 'ark', width: 1.3 }));
    g.push(txt(B.X(4.0) + 50, B.Y(0.30) + 4, '넓이', { cls: 'ink', size: 'sm' }));
    g.push(txt(384, 272, 'P(x ≤ X ≤ x+Δx) ≈ f(x) Δx', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(384, 290, '밀도에 너비를 곱해야 확률이 된다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 패널 3: 단위를 바꾸면 높이가 바뀐다
    const C = frame({ xRange: [-0.15, 1.15], yRange: [0, 3.0], box: { x: 540, y: 88, w: 176, h: 150 } });
    g.push(txt(628, 62, '자를 바꾸면 높이가 바뀐다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(drect(C, 0.1, 0, 0.6, 2.0, { fill: 'var(--s1)', op: 0.3, stroke: 'var(--s1)', sw: 1.8, rx: 0 }));
    g.push(C.axes({ xTicks: [0, 0.5, 1], yTicks: [1, 2, 3], grid: false }));
    g.push(C.label([0.35, 2.0], 'f(x) = 2.0  (단위 1/m)', { dx: 0, dy: -12, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(C.label([0.35, 1.0], '넓이 1', { dx: 0, dy: 0, anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(628, 272, '같은 분포를 cm 로 재면 높이는 0.02 다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(628, 290, '그래도 넓이는 여전히 1 이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[62, 308], [716, 308]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(390, 330, '세 칸이 같은 말을 한다. 밀도의 높이 하나만으로는 아무 확률도 읽을 수 없다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(390, 354, '읽을 수 있는 것은 구간을 정한 뒤의 넓이뿐이다. 그래서 P(X = c) = 0 이 되어도 모순이 없다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(390, 380, '밀도가 두 배 높다는 말은 ‘그 근처가 두 배 잘 나온다’는 뜻이지 ‘확률이 두 배’라는 뜻이 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-density-not-prob',
        svg: svg({
            width: W, height: H,
            title: '밀도는 확률이 아니다 — 넓이가 확률이다',
            desc: '밀도의 높이는 1 을 넘을 수 있고 단위를 바꾸면 값이 달라진다. 변하지 않는 것은 구간 아래의 넓이뿐이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-4. 누적분포함수 — 이산과 연속 ---- */
add((() => {
    const W = 740, H = 402;
    const g = [];
    const pm = [1 / 8, 3 / 8, 3 / 8, 1 / 8];
    g.push(txt(370, 28, '누적분포함수 F(x) = P(X ≤ x) 는 두 경우를 하나로 다룬다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽 위 — pmf
    const A = frame({ xRange: [-0.6, 3.6], yRange: [0, 0.45], box: { x: 78, y: 76, w: 246, h: 92 } });
    g.push(txt(201, 62, '이산 — 확률질량함수', { anchor: 'middle', cls: 'ink' }));
    pm.forEach((p, k) => g.push(drect(A, k - 0.22, 0, k + 0.22, p, { fill: 'var(--s1)', op: 0.45, stroke: 'var(--s1)', sw: 1.2, rx: 1 })));
    g.push(axesEdge(A, { x0: -0.6, x1: 3.6, y0: 0, y1: 0.45, xTicks: [0, 1, 2, 3] }));

    // 왼쪽 아래 — 계단 cdf
    const B = frame({ xRange: [-0.6, 3.6], yRange: [0, 1.12], box: { x: 78, y: 214, w: 246, h: 100 } });
    g.push(txt(201, 200, '계단 모양으로 뛴다', { anchor: 'middle', cls: 'ink' }));
    let cum = 0;
    const steps = [];
    pm.forEach((p, k) => { steps.push([k, cum, cum + p]); cum += p; });
    g.push(ln([[B.X(-0.6), B.Y(0)], [B.X(0), B.Y(0)]], { stroke: 'var(--s2)', sw: 2 }));
    steps.forEach(([k, lo, hi], i) => {
        const xEnd = i === 3 ? 3.6 : k + 1;
        g.push(ln([[B.X(k), B.Y(hi)], [B.X(xEnd), B.Y(hi)]], { stroke: 'var(--s2)', sw: 2 }));
        g.push(ln([[B.X(k), B.Y(lo)], [B.X(k), B.Y(hi)]], { stroke: 'var(--s2)', sw: 1, dash: '3 3' }));
        g.push(pdot(B.X(k), B.Y(hi), 'var(--s2)', 3.6));
        g.push(odot(B.X(k), B.Y(lo), 'var(--s2)', 3.6));
    });
    g.push(axesEdge(B, { x0: -0.6, x1: 3.6, y0: 0, y1: 1.12, xTicks: [0, 1, 2, 3], yTicks: [0.5, 1] }));
    g.push(txt(201, 356, '뛴 높이가 그 값의 확률이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 위 — pdf
    const fpdf = x => (x <= 0 || x >= 3) ? 0 : (x < 1.5 ? (2 / 3) * (x / 1.5) : (2 / 3) * ((3 - x) / 1.5));
    const C = frame({ xRange: [-0.4, 3.6], yRange: [0, 0.8], box: { x: 430, y: 76, w: 250, h: 92 } });
    g.push(txt(555, 62, '연속 — 확률밀도함수', { anchor: 'middle', cls: 'ink' }));
    g.push(underArea(C, fpdf, 0, 1.2, 'var(--s1)', 0.35));
    g.push(underArea(C, fpdf, 1.2, 3, 'var(--s1)', 0.12));
    g.push(C.axes({ xTicks: [0, 1, 2, 3], yTicks: [], grid: false }));
    g.push(C.line([[-0.4, 0], [0, 0], [1.5, 2 / 3], [3, 0], [3.6, 0]], { cls: 's2' }));
    g.push(C.label([0.72, 0.12], '넓이', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    // 오른쪽 아래 — 매끈한 cdf
    const F = x => {
        if (x <= 0) return 0;
        if (x >= 3) return 1;
        if (x < 1.5) return (2 / 9) * x * x;
        return 1 - (2 / 9) * (3 - x) * (3 - x);
    };
    const D = frame({ xRange: [-0.4, 3.6], yRange: [0, 1.12], box: { x: 430, y: 214, w: 250, h: 100 } });
    g.push(txt(555, 200, '끊김 없이 올라간다', { anchor: 'middle', cls: 'ink' }));
    g.push(D.axes({ xTicks: [0, 1, 2, 3], yTicks: [0.5, 1], grid: false }));
    g.push(D.curve(F, { from: -0.4, to: 3.6, cls: 's2' }));
    g.push(D.guide([1.2, 0], [1.2, F(1.2)]));
    g.push(D.guide([-0.4, F(1.2)], [1.2, F(1.2)]));
    g.push(D.label([1.2, F(1.2)], 'F(1.2) = 왼쪽 넓이', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }));
    g.push(txt(555, 356, '기울기가 밀도다. F′(x) = f(x)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[78, 372], [680, 372]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 396, 'F 는 어떤 확률변수에서든 정의된다. 왼쪽에서 0, 오른쪽에서 1, 그 사이에서 절대 내려가지 않는다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-cdf-pair',
        svg: svg({
            width: W, height: H,
            title: '누적분포함수 — 이산은 계단, 연속은 매끈한 곡선',
            desc: '이산에서는 F 가 확률만큼 뛰고 연속에서는 왼쪽 넓이가 그대로 F 가 된다. 기울기가 밀도다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-5. 분위수와 중앙값 ---- */
add((() => {
    const W = 700, H = 330;
    const F = x => 1 - Math.exp(-x / 2);
    const Q = p => -2 * Math.log(1 - p);
    const fr = frame({ xRange: [0, 8], yRange: [0, 1.1], box: { x: 76, y: 62, w: 330, h: 200 } });
    const g = [fr.axes({ xTicks: [0, 2, 4, 6, 8], yTicks: [0.25, 0.5, 0.75, 1], grid: false })];
    g.push(fr.curve(F, { from: 0, to: 8, cls: 's2' }));
    const marks = [[0.25, 's1', '제1사분위수'], [0.5, 's3', '중앙값'], [0.75, 's1', '제3사분위수']];
    for (const [p, cls, name] of marks) {
        const q = Q(p);
        g.push(ln([[fr.X(0), fr.Y(p)], [fr.X(q), fr.Y(p)]], { stroke: `var(--${cls})`, sw: 1.3, dash: '5 3' }));
        g.push(ln([[fr.X(q), fr.Y(p)], [fr.X(q), fr.Y(0)]], { stroke: `var(--${cls})`, sw: 1.3, dash: '5 3' }));
        g.push(pdot(fr.X(q), fr.Y(p), `var(--${cls})`, 4));
    }
    g.push(txt(fr.X(4), 44, '누적분포함수 F(x) 를 세로로 자르면 분위수가 나온다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(fr.X(8) + 16, fr.Y(0) + 4, 'x', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 84, '높이를 정하고 옆으로 그은 뒤', { cls: 'ink' }));
    g.push(txt(430, 106, '곡선과 만나는 자리를 읽는다.', { cls: 'ink' }));
    g.push(txt(430, 142, 'p 분위수 Q(p) 는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 162, 'F(x) = p 를 만족하는 x 다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 188, '0.25 분위수 ≈ 0.58', { cls: 'ink' }));
    g.push(txt(430, 210, '중앙값(0.5 분위수) ≈ 1.39', { cls: 'ink bold' }));
    g.push(txt(430, 232, '0.75 분위수 ≈ 2.77', { cls: 'ink' }));
    g.push(txt(430, 256, '사분위범위 = 2.77 − 0.58 ≈ 2.19', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[76, 282], [660, 282]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 306, '이 분포는 오른쪽으로 길게 끌린다. 그래서 중앙값(1.39)이 평균(2)보다 왼쪽에 있다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-quantile',
        svg: svg({
            width: W, height: H,
            title: '분위수는 누적분포함수를 거꾸로 읽은 값이다',
            desc: '세로축에서 0.25 0.5 0.75 를 잡고 곡선과 만나는 x 를 읽으면 사분위수가 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-6. 변환 — 밀도가 눌리고 늘어난다 ---- */
add((() => {
    const W = 740, H = 376;
    const g = [];
    g.push(txt(370, 28, 'Y = X² — 균등하게 놓인 구간이 고르지 않게 옮겨진다', { anchor: 'middle', cls: 'ink bold' }));
    const A = frame({ xRange: [0, 2.2], yRange: [0, 1.0], box: { x: 70, y: 74, w: 200, h: 118 } });
    g.push(txt(170, 60, 'X 는 0 과 2 사이 균등', { anchor: 'middle', cls: 'ink' }));
    g.push(drect(A, 0, 0, 2, 0.5, { fill: 'var(--s1)', op: 0.25, stroke: 'var(--s1)', sw: 1.6, rx: 0 }));
    for (let k = 1; k < 4; k += 1) g.push(ln([[A.X(k * 0.5), A.Y(0)], [A.X(k * 0.5), A.Y(0.5)]], { stroke: 'var(--s1)', sw: 1 }));
    g.push(A.axes({ xTicks: [0, 1, 2], yTicks: [0.5], grid: false }));
    g.push(A.label([1, 0.5], 'f~X(x) = 0.5', { dx: 0, dy: -10, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(170, 230, '너비가 같은 네 칸', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const fY = y => (y <= 0 || y > 4) ? 0 : 1 / (4 * Math.sqrt(y));
    const B = frame({ xRange: [0, 4.4], yRange: [0, 1.15], box: { x: 420, y: 74, w: 240, h: 118 } });
    g.push(txt(540, 60, 'Y = X² 는 0 과 4 사이', { anchor: 'middle', cls: 'ink' }));
    g.push(underArea(B, fY, 0.0625, 4, 'var(--s2)', 0.25));
    for (let k = 1; k < 4; k += 1) g.push(ln([[B.X((k * 0.5) ** 2), B.Y(0)], [B.X((k * 0.5) ** 2), B.Y(fY((k * 0.5) ** 2))]], { stroke: 'var(--s2)', sw: 1 }));
    g.push(B.axes({ xTicks: [0, 1, 2, 3, 4], yTicks: [0.5], grid: false }));
    g.push(B.curve(fY, { from: 0.0625, to: 4, cls: 's2' }));
    g.push(B.label([0.06, 1.0], '0 에 가까워지면 한없이 높아진다', { dx: 10, dy: -6, cls: 'ink2', size: 'sm' }));
    g.push(txt(540, 230, '칸의 너비가 0.25 : 0.75 : 1.25 : 1.75 로 벌어진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    for (const x0 of [0.5, 1.0, 1.5, 2.0]) {
        g.push(arw(A.X(x0), 252, B.X(x0 * x0), 252, { cls: 'ark', width: 1, dash: '4 3' }));
    }
    g.push(txt(345, 270, '제곱', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[70, 290], [670, 290]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 316, '같은 확률이 더 넓은 자리에 퍼지면 밀도는 낮아진다. 그 배율이 |dx/dy| 다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 342, 'x = √y 이므로 dx/dy = 1/(2√y) 이고, f~Y(y) = 0.5 × 1/(2√y) = 1/(4√y) 가 된다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 368, '넓이는 어느 쪽에서 재도 1 이다. 옮겨진 것은 확률이 아니라 그 확률이 놓인 자리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-transform-y2',
        svg: svg({
            width: W, height: H,
            title: '변환하면 밀도가 눌리고 늘어난다',
            desc: '균등분포를 제곱하면 같은 확률이 더 넓은 구간으로 퍼져 밀도가 낮아진다. 그 배율이 도함수의 절댓값이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 4-7. 역변환 표본추출 ---- */
add((() => {
    const W = 720, H = 372;
    const F = x => 1 - Math.exp(-x / 1.5);
    const Q = p => -1.5 * Math.log(1 - p);
    const fr = frame({ xRange: [0, 6], yRange: [0, 1.08], box: { x: 116, y: 62, w: 282, h: 190 } });
    const g = [fr.axes({ xTicks: [0, 2, 4, 6], yTicks: [0.5, 1], grid: false })];
    g.push(fr.curve(F, { from: 0, to: 6, cls: 's2' }));
    const us = [];
    for (let i = 1; i <= 12; i += 1) us.push(i / 13);
    for (const u of us) {
        const q = Q(u);
        g.push(ln([[fr.X(0), fr.Y(u)], [fr.X(q), fr.Y(u)]], { stroke: 'var(--grid)', sw: 0.9 }));
        g.push(ln([[fr.X(q), fr.Y(u)], [fr.X(q), fr.Y(0)]], { stroke: 'var(--grid)', sw: 0.9 }));
        g.push(pdot(fr.X(0) - 26, fr.Y(u), 'var(--s1)', 3));
        g.push(pdot(fr.X(q), fr.Y(0) + 10, 'var(--s3)', 3.4));
    }
    g.push(txt(256, 44, '세로축에 고르게 놓은 점을 곡선을 통해 가로축으로 내린다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(52, 156, '고르게', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(52, 174, '놓는다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(fr.X(3), fr.Y(0) + 36, '왼쪽에 몰리고 오른쪽에서 성기다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(424, 88, '난수 U 는 0 과 1 사이에서', { cls: 'ink' }));
    g.push(txt(424, 108, '고르게 뽑는다.', { cls: 'ink' }));
    g.push(txt(424, 140, 'X = F⁻¹(U) 로 옮기면', { cls: 'ink bold' }));
    g.push(txt(424, 160, 'X 의 누적분포함수가', { cls: 'ink bold' }));
    g.push(txt(424, 180, '정확히 F 가 된다.', { cls: 'ink bold' }));
    g.push(txt(424, 212, 'F 가 가파른 곳에서 점이 촘촘해지고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(424, 232, '완만한 곳에서 성기게 놓인다.', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[60, 306], [668, 306]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 332, '증명은 한 줄이다. P(F⁻¹(U) ≤ x) = P(U ≤ F(x)) = F(x)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(360, 358, '균등난수 하나만 있으면 어떤 분포든 만들어 낼 수 있다는 뜻이다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-inverse-sampling',
        svg: svg({
            width: W, height: H,
            title: '역변환 표본추출 — 균등난수를 원하는 분포로 바꾼다',
            desc: '세로축에 고르게 놓인 점을 누적분포함수를 통해 가로축으로 내리면 원하는 분포의 표본이 된다',
            body: BG + g.join(''),
        }),
    };
})());


/* ================================================================== *
 * 5장 — 기댓값, 분산, 적률
 * ================================================================== */

/* ---- 5-1. 기댓값은 무게중심이다 ---- */
add((() => {
    const W = 700, H = 348;
    const vals = [[1, 0.1], [2, 0.15], [3, 0.2], [4, 0.35], [5, 0.2]];
    const mu = vals.reduce((a, [x, p]) => a + x * p, 0);
    const fr = frame({ xRange: [0.3, 5.7], yRange: [0, 0.42], box: { x: 92, y: 76, w: 400, h: 150 } });
    const g = [axesEdge(fr, { x0: 0.3, x1: 5.7, y0: 0, y1: 0.42, xTicks: [1, 2, 3, 4, 5], yTicks: [0.1, 0.2, 0.3] })];
    for (const [x, p] of vals) {
        g.push(drect(fr, x - 0.24, 0, x + 0.24, p, { fill: 'var(--s1)', op: 0.4, stroke: 'var(--s1)', sw: 1.2, rx: 1 }));
        g.push(txt(fr.X(x), fr.Y(p) - 7, p.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    // 받침점
    const bx = fr.X(mu), by = fr.Y(0);
    g.push(ln([[fr.X(0.3), by + 6], [fr.X(5.7), by + 6]], { stroke: 'var(--ink2)', sw: 2.4 }));
    g.push(`<path d="M${bx} ${by + 8} L${bx - 13} ${by + 30} L${bx + 13} ${by + 30} Z" fill="var(--s2)" fill-opacity="0.75"/>`);
    g.push(txt(bx, by + 46, `받침점 μ = ${mu.toFixed(2)}`, { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(fr.X(3), 52, '기댓값은 확률을 무게로 본 막대의 균형점이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(520, 96, 'E(X) = Σ x p(x)', { cls: 'ink bold' }));
    g.push(txt(520, 122, '= 1(0.1) + 2(0.15)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 142, '  + 3(0.2) + 4(0.35)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 162, '  + 5(0.2) = 3.4', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 192, '값이 나올 자리는 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[92, 290], [652, 290]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 314, '받침점을 다른 곳에 두면 막대가 기운다. 그 기울어진 정도의 합이 0 이 되는 곳이 기댓값이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 338, '즉 Σ (x − μ) p(x) = 0 이다. 평균에서의 편차는 확률로 가중하면 서로 지워진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-balance',
        svg: svg({
            width: W, height: H,
            title: '기댓값은 확률분포의 무게중심이다',
            desc: '막대의 높이를 무게로 보면 균형이 잡히는 자리가 기댓값이다. 편차에 확률을 곱해 더하면 0 이 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-2. E[g(X)] — 값만 옮기고 확률은 따라간다 ---- */
add((() => {
    const W = 720, H = 366;
    const vals = [[-2, 0.2], [-1, 0.2], [0, 0.2], [1, 0.2], [2, 0.2]];
    const g = [];
    g.push(txt(360, 28, 'g(X) 의 기댓값을 구하려고 g(X) 의 분포를 새로 만들 필요는 없다', { anchor: 'middle', cls: 'ink bold' }));
    const A = frame({ xRange: [-2.8, 2.8], yRange: [0, 0.3], box: { x: 66, y: 78, w: 236, h: 110 } });
    g.push(txt(184, 64, 'X 의 분포', { anchor: 'middle', cls: 'ink' }));
    for (const [x, p] of vals) g.push(drect(A, x - 0.2, 0, x + 0.2, p, { fill: 'var(--s1)', op: 0.4, stroke: 'var(--s1)', sw: 1.2, rx: 1 }));
    g.push(axesEdge(A, { x0: -2.8, x1: 2.8, y0: 0, y1: 0.3, xTicks: [-2, -1, 0, 1, 2] }));
    g.push(txt(184, 226, '다섯 값이 모두 확률 0.2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const B = frame({ xRange: [-0.8, 4.8], yRange: [0, 0.6], box: { x: 420, y: 78, w: 236, h: 110 } });
    g.push(txt(538, 64, 'Y = X² 의 분포', { anchor: 'middle', cls: 'ink' }));
    const yvals = [[0, 0.2], [1, 0.4], [4, 0.4]];
    for (const [y, p] of yvals) g.push(drect(B, y - 0.2, 0, y + 0.2, p, { fill: 'var(--s2)', op: 0.4, stroke: 'var(--s2)', sw: 1.2, rx: 1 }));
    g.push(axesEdge(B, { x0: -0.8, x1: 4.8, y0: 0, y1: 0.6, xTicks: [0, 1, 2, 3, 4] }));
    g.push(txt(538, 226, '−1 과 1 이 같은 자리로 겹친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const [x] of vals) g.push(arw(A.X(x), 248, B.X(x * x), 248, { cls: 'ark', width: 0.9, dash: '3 3' }));
    g.push(txt(360, 274, '두 길이 같은 답을 준다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[66, 292], [656, 292]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 316, '새 분포로: 0(0.2) + 1(0.4) + 4(0.4) = 2.0', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 338, '옛 분포로: (−2)²(0.2) + (−1)²(0.2) + 0²(0.2) + 1²(0.2) + 2²(0.2) = 2.0', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 360, '뒤쪽이 무의식적 통계학자의 법칙이다. g 를 값에만 씌우고 확률은 그대로 쓴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-lotus',
        svg: svg({
            width: W, height: H,
            title: 'E[g(X)] 는 X 의 분포로 바로 계산한다',
            desc: 'g 를 씌우면 값이 옮겨 가고 겹칠 수 있지만 확률은 따라간다. 그래서 원래 분포로 곧장 더해도 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-3. 옌센 — E[g(X)] 와 g(E[X]) 는 다르다 ---- */
add((() => {
    const W = 700, H = 354;
    const gf = x => 0.36 * x * x;
    const x1 = 1, x2 = 5, mu = 3;
    const fr = frame({ xRange: [0, 6.2], yRange: [0, 10.5], box: { x: 84, y: 62, w: 330, h: 200 } });
    const g = [axesEdge(fr, { x0: 0, x1: 6.2, y0: 0, y1: 10.5, xTicks: [1, 3, 5], yTicks: [2, 5, 9] })];
    g.push(fr.curve(gf, { from: 0, to: 5.35, cls: 's2' }));
    g.push(fr.line([[x1, gf(x1)], [x2, gf(x2)]], { cls: 's1', dash: '6 4' }));
    const chordMid = (gf(x1) + gf(x2)) / 2;
    g.push(fr.dot([x1, gf(x1)], { cls: 'f2' }));
    g.push(fr.dot([x2, gf(x2)], { cls: 'f2' }));
    g.push(fr.dot([mu, chordMid], { cls: 'f1', r: 4.5 }));
    g.push(fr.dot([mu, gf(mu)], { cls: 'f3', r: 4.5 }));
    g.push(fr.guide([mu, 0], [mu, chordMid]));
    g.push(fr.guide([0, chordMid], [mu, chordMid]));
    g.push(fr.guide([0, gf(mu)], [mu, gf(mu)]));
    g.push(fr.label([mu, chordMid], 'E[g(X)] = 4.68', { dx: -12, dy: -8, anchor: 'end', cls: 'ink bold' }));
    g.push(fr.label([mu, gf(mu)], 'g(E[X]) = 3.24', { dx: 12, dy: 20, cls: 'ink bold' }));
    g.push(fr.label([5.5, 3.2], 'g(x) = 0.36x²', { dx: 0, dy: 0, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(249, 44, '아래로 볼록한 g 에서는 E[g(X)] 가 g(E[X]) 보다 크다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(440, 92, 'X 는 1 과 5 를', { cls: 'ink' }));
    g.push(txt(440, 112, '각각 확률 1/2 로 갖는다.', { cls: 'ink' }));
    g.push(txt(440, 142, 'E[X] = 3', { cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 162, 'g(3) = 3.24', { cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 182, '(0.36 + 9.0)/2 = 4.68', { cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 212, '차이 1.44 는 곡선과', { cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 232, '현 사이의 간격이다.', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[84, 294], [656, 294]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 320, '평균을 먼저 내고 함수를 씌우는 것과 함수를 씌우고 평균을 내는 것은 다르다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(350, 344, '직선(g(x) = ax + b)일 때만 두 값이 같다. 그것이 기댓값의 선형성이 말하는 전부다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-jensen',
        svg: svg({
            width: W, height: H,
            title: '평균을 먼저 낼 것인가 함수를 먼저 씌울 것인가',
            desc: '아래로 볼록한 함수에서는 함수를 씌운 뒤의 평균이 평균을 씌운 값보다 크다. 직선일 때만 같다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-4. 같은 평균, 다른 분산 ---- */
add((() => {
    const W = 700, H = 356;
    const fr = frame({ xRange: [-6.4, 6.4], yRange: [0, 0.46], box: { x: 78, y: 66, w: 420, h: 180 } });
    const g = [fr.axes({ xTicks: [-6, -3, 0, 3, 6], yTicks: [], grid: false })];
    const sds = [[1, 's1'], [2, 's2'], [3, 's3']];
    for (const [sd, cls] of sds) {
        g.push(fr.curve(x => phi(x / sd) / sd, { from: -6.3, to: 6.3, cls }));
    }
    g.push(fr.label([0, phi(0)], 'σ = 1', { dx: 8, dy: -6, cls: 'ink' }));
    g.push(fr.label([1.6, phi(1.6 / 2) / 2], 'σ = 2', { dx: 10, dy: -4, cls: 'ink' }));
    g.push(fr.label([4.2, phi(4.2 / 3) / 3], 'σ = 3', { dx: 12, dy: -6, cls: 'ink' }));
    g.push(txt(288, 46, '평균이 같아도 흩어진 정도는 다르다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[fr.X(0), fr.Y(0)], [fr.X(0), fr.Y(0.44)]], { stroke: 'var(--ink2)', sw: 1, dash: '5 4' }));
    g.push(txt(fr.X(0), fr.Y(0) + 34, '세 분포 모두 μ = 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 100, '분산 V(X) = E[(X−μ)²]', { cls: 'ink bold' }));
    g.push(txt(520, 126, '= E[X²] − μ²', { cls: 'ink bold' }));
    g.push(txt(520, 156, 'σ = 1 → V = 1', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 176, 'σ = 2 → V = 4', { cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 196, 'σ = 3 → V = 9', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[78, 296], [656, 296]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(350, 320, '분산은 제곱이라 단위도 제곱이 된다. 키가 cm 라면 분산의 단위는 cm² 다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 344, '그래서 원래 단위로 되돌린 표준편차 σ 를 함께 쓴다. 그림에서 폭으로 보이는 것이 σ 다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-spread',
        svg: svg({
            width: W, height: H,
            title: '평균이 같고 분산이 다른 세 분포',
            desc: '표준편차가 커질수록 곡선이 낮고 넓어진다. 넓이는 셋 다 1 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-5. 체비쇼프 부등식 — 옳지만 느슨하다 ---- */
add((() => {
    const W = 760, H = 360;
    const g = [];
    g.push(txt(380, 28, '체비쇼프는 어떤 분포에서도 참이다. 그 대가로 대개 크게 헐겁다', { anchor: 'middle', cls: 'ink bold' }));
    const fr = frame({ xRange: [-4.2, 4.2], yRange: [0, 0.46], box: { x: 66, y: 74, w: 300, h: 150 } });
    g.push(underArea(fr, phi, -4.2, -2, 'var(--s2)', 0.5));
    g.push(underArea(fr, phi, 2, 4.2, 'var(--s2)', 0.5));
    g.push(fr.axes({ xTicks: [-2, 0, 2], yTicks: [], grid: false }));
    g.push(fr.curve(phi, { from: -4.15, to: 4.15, cls: 's1' }));
    g.push(fr.label([-3.1, 0.06], '꼬리', { anchor: 'middle', dy: -6, cls: 'ink', size: 'sm' }));
    g.push(fr.label([3.1, 0.06], '꼬리', { anchor: 'middle', dy: -6, cls: 'ink', size: 'sm' }));
    g.push(txt(216, 258, '평균에서 2σ 보다 먼 곳(양쪽 꼬리)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const rows = [
        ['k', '체비쇼프 상한 1/k²', '정규분포', '균등분포'],
        ['2', '0.250', '0.046', '0'],
        ['3', '0.111', '0.0027', '0'],
        ['4', '0.0625', '0.00006', '0'],
    ];
    rows.forEach((r, i) => {
        const y = 96 + i * 32;
        const head = i === 0;
        g.push(box(392, y - 20, 320, 26, { fill: head ? 'var(--s1)' : 'none', op: head ? 0.14 : 0, stroke: 'var(--grid)', sw: 1, rx: 3 }));
        g.push(txt(404, y, r[0], { cls: head ? 'ink bold' : 'ink' }));
        g.push(txt(432, y, r[1], { cls: head ? 'ink bold' : 'ink', size: head ? 'sm' : undefined }));
        g.push(txt(566, y, r[2], { cls: head ? 'ink bold' : 'ink', size: head ? 'sm' : undefined }));
        g.push(txt(654, y, r[3], { cls: head ? 'ink bold' : 'ink2', size: 'sm' }));
    });
    g.push(txt(552, 240, '상한이 실제보다 다섯 배에서 천 배까지 크다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[66, 278], [712, 278]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(380, 302, 'P(|X − μ| ≥ kσ) ≤ 1/k² — 분포의 모양을 하나도 가정하지 않고 얻은 결과다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 326, '모양을 알면 훨씬 좋은 답을 낼 수 있다. 모양을 모를 때에도 쓸 수 있다는 것이 이 부등식의 값어치다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(380, 350, 'k ≤ 1 이면 상한이 1 이상이라 아무 말도 하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-chebyshev',
        svg: svg({
            width: W, height: H,
            title: '체비쇼프 부등식의 상한과 실제 꼬리 확률',
            desc: '어떤 분포에서도 성립하는 대신 정규분포나 균등분포의 실제 꼬리보다 훨씬 큰 값을 준다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-6. 왜도와 첨도 ---- */
add((() => {
    const W = 740, H = 348;
    const g = [];
    g.push(txt(370, 28, '3차 적률은 치우침을, 4차 적률은 꼬리의 두께를 잰다', { anchor: 'middle', cls: 'ink bold' }));
    const A = frame({ xRange: [-3.4, 3.4], yRange: [0, 0.62], box: { x: 60, y: 78, w: 290, h: 140 } });
    const skewed = (x, a) => 2 * phi(x) * (1 / (1 + Math.exp(-a * x)));
    g.push(A.axes({ xTicks: [-2, 0, 2], yTicks: [], grid: false }));
    g.push(A.curve(x => skewed(x, -3), { from: -3.35, to: 3.35, cls: 's1' }));
    g.push(A.label([-3.3, 0.55], '점선은 대칭인 정규', { dx: 0, dy: 0, cls: 'ink2', size: 'sm' }));
    g.push(curveInk(A, phi, { from: -3.35, to: 3.35, dash: '5 4', sw: 1.6 }));
    g.push(A.curve(x => skewed(x, 3), { from: -3.35, to: 3.35, cls: 's2' }));
    g.push(txt(205, 62, '왜도 — 어느 쪽으로 끌리는가', { anchor: 'middle', cls: 'ink' }));
    g.push(A.label([-2.35, 0.20], '왼쪽으로 끌림', { dx: 0, dy: -6, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(A.label([2.35, 0.20], '오른쪽으로 끌림', { dx: 0, dy: -6, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(205, 252, '왜도 음수 / 0 / 양수 — 긴 꼬리가 있는 쪽 부호다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const B = frame({ xRange: [-4.2, 4.2], yRange: [0, 0.62], box: { x: 420, y: 78, w: 270, h: 140 } });
    const lap = x => Math.exp(-Math.abs(x) * Math.SQRT2) * Math.SQRT2 / 2;
    g.push(B.axes({ xTicks: [-3, 0, 3], yTicks: [], grid: false }));
    g.push(B.curve(lap, { from: -4.15, to: 4.15, cls: 's2' }));
    g.push(B.curve(phi, { from: -4.15, to: 4.15, cls: 's1' }));
    g.push(txt(555, 62, '첨도 — 꼬리가 얼마나 두꺼운가', { anchor: 'middle', cls: 'ink' }));
    g.push(B.label([0.2, 0.55], '뾰족하고 꼬리가 두껍다', { dx: 6, dy: 0, cls: 'ink', size: 'sm' }));
    g.push(B.label([-3.5, 0.05], '정규', { dx: 0, dy: -10, anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(555, 252, '분산이 같아도 극단값이 나올 확률은 다르다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[60, 276], [690, 276]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 302, '왜도 = E[(X−μ)³]/σ³, 첨도 = E[(X−μ)⁴]/σ⁴. 둘 다 표준화해서 단위를 없앤 값이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 328, '평균과 분산이 같아도 모양이 다를 수 있다. 3차 4차 적률은 그 차이를 수로 붙잡는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-skew-kurt',
        svg: svg({
            width: W, height: H,
            title: '왜도와 첨도 — 3차 4차 적률이 잡아내는 모양',
            desc: '왜도는 치우친 방향을, 첨도는 꼬리의 두께와 봉우리의 뾰족함을 나타낸다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 5-7. 적률생성함수의 기울기가 평균이다 ---- */
add((() => {
    const W = 720, H = 340;
    const M = t => 0.5 * (Math.exp(t) + Math.exp(3 * t));
    const fr = frame({ xRange: [-0.65, 0.65], yRange: [0, 3.2], box: { x: 86, y: 66, w: 300, h: 180 } });
    const g = [fr.axes({ xTicks: [-0.5, 0, 0.5], yTicks: [1, 2, 3], grid: false })];
    g.push(fr.curve(M, { from: -0.62, to: 0.51, cls: 's2' }));
    g.push(fr.line([[-0.45, 1 - 2 * 0.45], [0.6, 1 + 2 * 0.6]], { cls: 's1', dash: '6 4' }));
    g.push(fr.dot([0, 1], { cls: 'f1', r: 4.5 }));
    g.push(fr.label([0, 1], 'M(0) = 1', { dx: -12, dy: 30, anchor: 'end', cls: 'ink' }));
    g.push(fr.label([-0.62, 2.95], '점선 = t = 0 의 접선', { dx: 0, dy: 0, cls: 'ink', size: 'sm' }));
    g.push(fr.label([-0.62, 2.6], '기울기 = μ = 2', { dx: 0, dy: 0, cls: 'ink bold' }));
    g.push(fr.label([0.5, M(0.5)], 'M(t)', { dx: -10, dy: 6, anchor: 'end', cls: 'ink' }));
    g.push(txt(86, 44, 'X 는 1 과 3 을 각각 확률 1/2 로 갖는다', { cls: 'ink bold' }));
    g.push(txt(412, 88, 'M(t) = E[e^tX]', { cls: 'ink bold' }));
    g.push(txt(412, 110, '     = (e^t + e^3t)/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 142, 'M′(t) = (e^t + 3e^3t)/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 162, 'M′(0) = 2 = E[X]', { cls: 'ink bold' }));
    g.push(txt(412, 192, 'M″(t) = (e^t + 9e^3t)/2', { cls: 'ink2', size: 'sm' }));
    g.push(txt(412, 212, 'M″(0) = 5 = E[X²]', { cls: 'ink bold' }));
    g.push(txt(412, 240, 'V(X) = 5 − 2² = 1', { cls: 'ink' }));
    g.push(ln([[86, 268], [670, 268]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 292, 't = 0 에서 몇 번 미분하느냐가 몇 차 적률을 뽑느냐를 정한다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(360, 316, 'M(t) = 1 + μt + E[X²]t²/2! + ⋯ 이므로 t 의 거듭제곱 앞에 적률이 하나씩 얹혀 있다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 338, '분포 전체를 함수 하나로 압축한 것이라 7·8·9장에서 계속 쓰인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-mgf-slope',
        svg: svg({
            width: W, height: H,
            title: '적률생성함수를 t = 0 에서 미분하면 적률이 나온다',
            desc: 'M(t) 곡선의 원점에서의 기울기가 평균이고 두 번 미분한 값이 2차 적률이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6장 — 결합분포와 독립
 * ================================================================== */

/* ---- 6-1. 결합 확률표와 주변분포 ---- */
add((() => {
    const W = 720, H = 372;
    const J = [
        [0.10, 0.05, 0.05],
        [0.08, 0.20, 0.12],
        [0.02, 0.15, 0.23],
    ];
    const xs = ['x = 0', 'x = 1', 'x = 2'];
    const ys = ['y = 0', 'y = 1', 'y = 2'];
    const g = [];
    const ox = 130, oy = 96, cw = 92, ch = 46;
    g.push(txt(360, 28, '두 변수를 함께 보면 표 하나가 된다. 가장자리 합이 주변분포다', { anchor: 'middle', cls: 'ink bold' }));
    for (let i = 0; i < 3; i += 1) g.push(txt(ox + i * cw + cw / 2, oy - 12, xs[i], { anchor: 'middle', cls: 'ink bold' }));
    for (let j = 0; j < 3; j += 1) g.push(txt(ox - 12, oy + j * ch + 28, ys[j], { anchor: 'end', cls: 'ink bold' }));
    let tot = 0;
    for (let j = 0; j < 3; j += 1) {
        for (let i = 0; i < 3; i += 1) {
            const v = J[j][i]; tot += v;
            g.push(box(ox + i * cw, oy + j * ch, cw, ch, { fill: 'var(--s1)', op: 0.08 + v * 1.6, stroke: 'var(--grid)', sw: 1, rx: 0 }));
            g.push(txt(ox + i * cw + cw / 2, oy + j * ch + 29, v.toFixed(2), { anchor: 'middle', cls: 'ink' }));
        }
    }
    g.push(box(ox, oy, cw * 3, ch * 3, { stroke: 'var(--ink2)', sw: 1.5, rx: 0 }));
    // 주변합
    for (let i = 0; i < 3; i += 1) {
        const cs = J[0][i] + J[1][i] + J[2][i];
        g.push(box(ox + i * cw, oy + ch * 3 + 8, cw, 34, { fill: 'var(--s2)', op: 0.18, stroke: 'var(--s2)', sw: 1.2, rx: 3 }));
        g.push(txt(ox + i * cw + cw / 2, oy + ch * 3 + 30, cs.toFixed(2), { anchor: 'middle', cls: 'ink bold' }));
    }
    for (let j = 0; j < 3; j += 1) {
        const rs = J[j].reduce((a, b) => a + b, 0);
        g.push(box(ox + cw * 3 + 8, oy + j * ch + 6, 66, 34, { fill: 'var(--s3)', op: 0.2, stroke: 'var(--s3)', sw: 1.2, rx: 3 }));
        g.push(txt(ox + cw * 3 + 41, oy + j * ch + 28, rs.toFixed(2), { anchor: 'middle', cls: 'ink bold' }));
    }
    g.push(txt(ox + cw * 1.5, oy + ch * 3 + 64, 'X 의 주변분포 g(x) — 세로로 더한 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + cw * 3 + 41, oy - 12, 'Y 의 주변', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(ox + cw * 3 + 41, oy + ch * 3 + 30, tot.toFixed(2), { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(ox + cw * 3 + 41, oy + ch * 3 + 64, '전체 합', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[62, 316], [660, 316]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 342, '주변분포는 관심 없는 변수를 더해서 지운 것이다. 표의 가장자리에 적어서 붙은 이름이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 366, '주변분포만 보아서는 두 변수의 관계를 알 수 없다. 그 정보는 표 안쪽에 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-joint-table',
        svg: svg({
            width: W, height: H,
            title: '결합확률표와 주변분포',
            desc: '표 안의 아홉 칸이 결합확률이고 행과 열의 합이 각 변수의 주변분포다. 전체 합은 1 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-2. 이중적분 — 안쪽부터 차례로 ---- */
add((() => {
    const W = 760, H = 360;
    const g = [];
    g.push(txt(380, 28, '이중적분은 두 번의 보통 적분이다. 안쪽부터 차례로 한다', { anchor: 'middle', cls: 'ink bold' }));
    // 비스듬히 본 좌표 (오블리크 투영)
    const P = (ox, oy) => (x, y, z) => [ox + x * 52 + y * 26, oy - z * 40 - y * 20];
    const surf = (x, y) => 0.35 + 0.55 * x * (2.1 - x) / 1.1 + 0.22 * y;

    // 왼쪽 — 한 y 에서 자른 단면
    const p1 = P(96, 226);
    const base1 = [[0, 0], [2, 0], [2, 2], [0, 2]].map(([x, y]) => p1(x, y, 0));
    g.push(`<path d="M${base1.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--grid)" fill-opacity="0.35" stroke="var(--ink2)" stroke-width="1.2"/>`);
    const y0 = 1.1;
    const slice = [];
    slice.push(p1(0, y0, 0));
    for (let i = 0; i <= 20; i += 1) { const x = (2 * i) / 20; slice.push(p1(x, y0, surf(x, y0))); }
    slice.push(p1(2, y0, 0));
    g.push(`<path d="M${slice.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s1)" fill-opacity="0.4" stroke="var(--s1)" stroke-width="1.6"/>`);
    g.push(txt(150, 62, '안쪽 적분 — y 를 고정한다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(150, 264, 'y 를 상수로 두고 x 로만 적분한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(150, 284, '결과는 이 단면의 넓이 A(y) 다', { anchor: 'middle', cls: 'ink' }));

    // 오른쪽 — 여러 단면 쌓기
    const p2 = P(456, 226);
    const base2 = [[0, 0], [2, 0], [2, 2], [0, 2]].map(([x, y]) => p2(x, y, 0));
    g.push(`<path d="M${base2.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--grid)" fill-opacity="0.35" stroke="var(--ink2)" stroke-width="1.2"/>`);
    for (let k = 6; k >= 0; k -= 1) {
        const yy = (2 * k) / 6;
        const sl = [p2(0, yy, 0)];
        for (let i = 0; i <= 16; i += 1) { const x = (2 * i) / 16; sl.push(p2(x, yy, surf(x, yy))); }
        sl.push(p2(2, yy, 0));
        g.push(`<path d="M${sl.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s2)" fill-opacity="0.2" stroke="var(--s2)" stroke-width="1"/>`);
    }
    g.push(txt(520, 62, '바깥 적분 — 단면을 y 방향으로 쌓는다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(520, 264, 'A(y) 를 y 로 적분한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(520, 284, '결과는 곡면 아래의 부피 = 확률이다', { anchor: 'middle', cls: 'ink' }));

    g.push(arw(288, 180, 372, 180, { cls: 'ark', width: 1.6 }));
    g.push(txt(330, 166, '모으면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[80, 304], [690, 304]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(380, 328, '∫∫ f(x,y) dx dy 에서 안쪽 dx 를 먼저 계산한다. 그동안 y 는 그냥 상수다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 352, '새 기술은 없다. 미적분에서 배운 적분을 두 번 하되 한 번은 다른 문자를 상수로 두는 것뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-double-int',
        svg: svg({
            width: W, height: H,
            title: '이중적분은 안쪽부터 차례로 하는 두 번의 적분이다',
            desc: '한 y 를 고정해 자른 단면의 넓이를 먼저 구하고 그 넓이를 y 방향으로 쌓으면 곡면 아래 부피가 된다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-3. 적분 순서 바꾸기 ---- */
add((() => {
    const W = 720, H = 320;
    const g = [];
    g.push(txt(360, 28, '직사각형 영역에서는 어느 쪽을 먼저 적분해도 답이 같다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = (ox, vertical) => {
        const fr = frame({ xRange: [-0.3, 2.6], yRange: [-0.3, 2.1], box: { x: ox, y: 76, w: 210, h: 150 } });
        const out = [drect(fr, 0, 0, 2, 1.5, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--s1)', sw: 1.6, rx: 0 })];
        if (vertical) {
            for (let k = 0; k < 8; k += 1) {
                const x = (2 * k) / 8;
                out.push(drect(fr, x, 0, x + 2 / 8, 1.5, { fill: 'var(--s2)', op: 0.14, stroke: 'var(--s2)', sw: 0.9, rx: 0 }));
            }
            out.push(arw(fr.X(0.75), fr.Y(0.1), fr.X(0.75), fr.Y(1.4), { cls: 's2', width: 1.6 }));
        } else {
            for (let k = 0; k < 6; k += 1) {
                const y = (1.5 * k) / 6;
                out.push(drect(fr, 0, y, 2, y + 1.5 / 6, { fill: 'var(--s3)', op: 0.16, stroke: 'var(--s3)', sw: 0.9, rx: 0 }));
            }
            out.push(arw(fr.X(0.1), fr.Y(0.75), fr.X(1.9), fr.Y(0.75), { cls: 's3', width: 1.6 }));
        }
        out.push(fr.axes({ xTicks: [0, 1, 2], yTicks: [1], grid: false }));
        out.push(fr.label([2.05, 0], 'x', { dx: 18, dy: 16, cls: 'ink2', size: 'sm' }));
        out.push(fr.label([0, 1.55], 'y', { dx: -16, dy: 0, cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(txt(170, 62, '세로로 잘라 y 부터', { anchor: 'middle', cls: 'ink' }));
    g.push(mk(66, true));
    g.push(txt(170, 254, '먼저 y 로 적분한 뒤 x 로 적분', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(520, 62, '가로로 잘라 x 부터', { anchor: 'middle', cls: 'ink' }));
    g.push(mk(416, false));
    g.push(txt(520, 254, '먼저 x 로 적분한 뒤 y 로 적분', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(345, 150, '같다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[66, 274], [656, 274]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 298, '영역이 직사각형이고 적분 범위가 상수이면 순서를 자유롭게 바꿔도 된다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 318, '영역이 비스듬하면 안쪽 적분의 범위에 바깥 변수가 들어가고, 그때는 순서를 바꿀 때 범위를 다시 읽어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-int-order',
        svg: svg({
            width: W, height: H,
            title: '직사각형 영역에서는 적분 순서를 바꿔도 된다',
            desc: '세로 띠로 잘라 세로 방향부터 적분하든 가로 띠로 잘라 가로 방향부터 적분하든 같은 값이 나온다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-4. 조건부분포는 단면을 다시 키운 것이다 ---- */
add((() => {
    const W = 740, H = 360;
    const g = [];
    const f = (x, y) => x + y;   // 단위정사각형에서 적분이 1 이 되는 결합밀도
    g.push(txt(370, 28, '조건부분포는 자른 단면의 넓이를 1 로 다시 맞춘 것이다', { anchor: 'middle', cls: 'ink bold' }));
    // 왼쪽: 결합밀도 히트맵 + 자르는 선
    const A = frame({ xRange: [0, 1], yRange: [0, 1], box: { x: 76, y: 76, w: 180, h: 150 } });
    const N = 12;
    for (let i = 0; i < N; i += 1) {
        for (let j = 0; j < N; j += 1) {
            const x = (i + 0.5) / N, y = (j + 0.5) / N;
            g.push(drect(A, i / N, j / N, (i + 1) / N, (j + 1) / N, { fill: 'var(--s1)', op: 0.05 + 0.24 * f(x, y), stroke: 'none', sw: 0, rx: 0 }));
        }
    }
    g.push(box(A.X(0), A.Y(1), A.X(1) - A.X(0), A.Y(0) - A.Y(1), { stroke: 'var(--ink2)', sw: 1.3, rx: 0 }));
    g.push(ln([[A.X(0.7), A.Y(0)], [A.X(0.7), A.Y(1)]], { stroke: 'var(--s2)', sw: 2.2 }));
    g.push(txt(166, 62, '결합밀도 f(x, y)', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(A.X(0.7) - 8, A.Y(0.06), 'x = 0.7 에서 자른다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(166, 246, '색이 짙을수록 밀도가 높다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 가운데: 자른 단면 (넓이가 1 이 아니다)
    const B = frame({ xRange: [0, 1], yRange: [0, 2.0], box: { x: 320, y: 76, w: 150, h: 150 } });
    const cut = y => f(0.7, y);
    g.push(underArea(B, cut, 0, 1, 'var(--s2)', 0.35));
    g.push(B.axes({ xTicks: [0, 1], yTicks: [1, 2], grid: false }));
    g.push(B.curve(cut, { from: 0, to: 1, cls: 's2' }));
    g.push(txt(395, 62, '자른 단면', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(395, 258, '넓이 = g(0.7) = 1.2', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(395, 278, '1 이 아니라 확률분포가 아니다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 정규화한 조건부밀도
    const C = frame({ xRange: [0, 1], yRange: [0, 2.0], box: { x: 552, y: 76, w: 150, h: 150 } });
    const gx = 0.7 + 0.5;   // ∫(0.7+y) dy = 0.7 + 0.5 = 1.2
    const cond = y => cut(y) / gx;
    g.push(underArea(C, cond, 0, 1, 'var(--s3)', 0.35));
    g.push(C.axes({ xTicks: [0, 1], yTicks: [1, 2], grid: false }));
    g.push(C.curve(cond, { from: 0, to: 1, cls: 's3' }));
    g.push(txt(627, 62, '조건부밀도 f(y | x = 0.7)', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(627, 258, '넓이 = 1', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(627, 278, '이제 어엿한 확률분포다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(478, 150, 542, 150, { cls: 'ark', width: 1.5 }));
    g.push(txt(510, 136, '÷ g(0.7)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[76, 302], [700, 302]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 326, 'f(y | x) = f(x, y) / g(x) — 3장의 P(A|B) = P(A∩B)/P(B) 와 같은 모양이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 350, '분모가 하는 일도 같다. 줄어든 세계의 확률을 다시 1 로 맞춘다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-cond-slice',
        svg: svg({
            width: W, height: H,
            title: '조건부밀도는 결합밀도의 단면을 정규화한 것이다',
            desc: '한 x 값에서 자른 단면은 넓이가 1 이 아니므로 주변밀도로 나누어 넓이를 1 로 맞춘다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-5. 공분산의 부호 ---- */
add((() => {
    const W = 740, H = 330;
    const g = [];
    g.push(txt(370, 28, '공분산은 두 편차의 곱을 평균한 값이다. 어느 사분면에 점이 많은가를 잰다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = (ox, rho, title) => {
        const rand = rng(4242 + Math.round(rho * 100) + 7);
        const fr = frame({ xRange: [-3.2, 3.2], yRange: [-3.2, 3.2], box: { x: ox, y: 74, w: 176, h: 152 } });
        const out = [];
        out.push(drect(fr, 0, 0, 3.2, 3.2, { fill: 'var(--s1)', op: 0.09, stroke: 'none', rx: 0 }));
        out.push(drect(fr, -3.2, -3.2, 0, 0, { fill: 'var(--s1)', op: 0.09, stroke: 'none', rx: 0 }));
        out.push(drect(fr, -3.2, 0, 0, 3.2, { fill: 'var(--s2)', op: 0.09, stroke: 'none', rx: 0 }));
        out.push(drect(fr, 0, -3.2, 3.2, 0, { fill: 'var(--s2)', op: 0.09, stroke: 'none', rx: 0 }));
        for (let i = 0; i < 46; i += 1) {
            const [a, b] = gauss(rand);
            const x = a * 1.15;
            const y = (rho * a + Math.sqrt(1 - rho * rho) * b) * 1.15;
            if (Math.abs(x) > 3 || Math.abs(y) > 3) continue;
            out.push(pdot(fr.X(x), fr.Y(y), 'var(--s1)', 2.6));
        }
        out.push(fr.axes({ xTicks: [], yTicks: [], grid: false }));
        out.push(txt(ox + 88, 60, title, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(fr.X(2.2), fr.Y(2.6) + 4, '+', { anchor: 'middle', cls: 'ink2' }));
        out.push(txt(fr.X(-2.2), fr.Y(-2.6) + 4, '+', { anchor: 'middle', cls: 'ink2' }));
        out.push(txt(fr.X(-2.2), fr.Y(2.6) + 4, '−', { anchor: 'middle', cls: 'ink2' }));
        out.push(txt(fr.X(2.2), fr.Y(-2.6) + 4, '−', { anchor: 'middle', cls: 'ink2' }));
        return out.join('');
    };
    g.push(mk(52, 0.85, '함께 커진다 — Cov > 0'));
    g.push(mk(282, -0.85, '반대로 간다 — Cov < 0'));
    g.push(mk(512, 0.0, '치우침이 없다 — Cov ≈ 0'));
    g.push(txt(140, 246, '+ 칸에 점이 몰린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 246, '− 칸에 점이 몰린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 246, '네 칸이 서로 지운다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[52, 268], [688, 268]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 292, 'Cov(X,Y) = E[(X − μ~X)(Y − μ~Y)] — 두 편차의 부호가 같으면 곱이 양수다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(370, 316, '가운데 두 선은 각 변수의 평균이다. 사분면의 이름은 그 선을 기준으로 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-cov-quadrant',
        svg: svg({
            width: W, height: H,
            title: '공분산의 부호는 점이 어느 사분면에 몰리는가로 정해진다',
            desc: '두 편차의 곱이 양수인 칸에 점이 몰리면 공분산이 양수이고 음수인 칸에 몰리면 음수다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-6. 상관은 직선 관계만 잡는다 (핵심 반례) ---- */
add((() => {
    const W = 760, H = 350;
    const g = [];
    g.push(txt(380, 28, '상관계수 ρ 가 0 이어도 관계가 없다는 뜻은 아니다', { anchor: 'middle', cls: 'ink bold' }));
    const corr = pts => {
        const n = pts.length;
        const mx = pts.reduce((a, p) => a + p[0], 0) / n;
        const my = pts.reduce((a, p) => a + p[1], 0) / n;
        let sxy = 0, sxx = 0, syy = 0;
        for (const [x, y] of pts) { sxy += (x - mx) * (y - my); sxx += (x - mx) ** 2; syy += (y - my) ** 2; }
        return sxy / Math.sqrt(sxx * syy);
    };
    const mk = (ox, pts, title, note) => {
        const fr = frame({ xRange: [-3.4, 3.4], yRange: [-2.6, 3.4], box: { x: ox, y: 76, w: 186, h: 150 } });
        const out = [fr.axes({ xTicks: [], yTicks: [], grid: false })];
        for (const [x, y] of pts) out.push(pdot(fr.X(x), fr.Y(y), 'var(--s1)', 2.8));
        out.push(txt(ox + 93, 62, title, { anchor: 'middle', cls: 'ink bold' }));
        const cv = corr(pts);
        out.push(txt(ox + 93, 248, `ρ = ${Math.abs(cv) < 0.005 ? '0.00' : cv.toFixed(2)}`, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(ox + 93, 270, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    const rand = rng(20260601);
    const lin = [], par = [], cir = [];
    for (let i = 0; i < 44; i += 1) {
        const [a, b] = gauss(rand);
        const x = a * 1.2;
        lin.push([x, 0.9 * x + 0.42 * b]);
    }
    for (let i = 0; i < 44; i += 1) {
        const x = -2.6 + (5.2 * i) / 43;
        par.push([x, 0.55 * x * x - 1.6 + (rand() - 0.5) * 0.35]);
    }
    for (let i = 0; i < 44; i += 1) {
        const t = (2 * PI * i) / 44;
        cir.push([2.2 * Math.cos(t), 2.2 * Math.sin(t) + 0.4]);
    }
    g.push(mk(48, lin, '직선 관계', '상관이 잡아내는 것'));
    g.push(mk(286, par, '뚜렷한 곡선 관계', '그런데 ρ 는 0 이다'));
    g.push(mk(524, cir, '완전한 원', '역시 ρ 는 0 이다'));
    g.push(ln([[48, 290], [712, 290]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(380, 314, '가운데와 오른쪽에서 X 를 알면 Y 에 대해 많은 것을 알 수 있다. 그런데도 ρ 는 0 이다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 338, 'ρ = 0 은 ‘직선으로 요약되는 부분이 없다’는 뜻일 뿐, 독립이라는 뜻이 아니다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-rho-zero',
        svg: svg({
            width: W, height: H,
            title: '상관계수는 직선 관계만 잡아낸다',
            desc: '포물선 위의 점들과 원 위의 점들은 관계가 뚜렷한데도 상관계수가 0 이다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-7. 독립이면 분산이 더해진다 ---- */
add((() => {
    const W = 720, H = 330;
    const g = [];
    g.push(txt(360, 28, '독립인 둘을 더하면 분산이 더해진다. 표준편차는 √2 배가 될 뿐이다', { anchor: 'middle', cls: 'ink bold' }));
    const A = frame({ xRange: [-6.5, 6.5], yRange: [0, 0.45], box: { x: 62, y: 80, w: 250, h: 150 } });
    g.push(A.axes({ xTicks: [-4, 0, 4], yTicks: [], grid: false }));
    g.push(A.curve(x => phi(x), { from: -6.4, to: 6.4, cls: 's1' }));
    g.push(txt(187, 64, 'X 와 Y — 각각 σ = 1', { anchor: 'middle', cls: 'ink' }));
    g.push(A.label([1.0, phi(1.0)], 'σ = 1', { dx: 12, dy: -4, cls: 'ink', size: 'sm' }));

    const B = frame({ xRange: [-6.5, 6.5], yRange: [0, 0.45], box: { x: 400, y: 80, w: 250, h: 150 } });
    g.push(B.axes({ xTicks: [-4, 0, 4], yTicks: [], grid: false }));
    g.push(curveInk(B, phi, { from: -6.4, to: 6.4, dash: '5 4', sw: 1.6 }));
    g.push(B.curve(x => phi(x / Math.SQRT2) / Math.SQRT2, { from: -6.4, to: 6.4, cls: 's2' }));
    g.push(txt(525, 64, 'X + Y — σ = √2 ≈ 1.41', { anchor: 'middle', cls: 'ink' }));
    g.push(B.label([2.2, phi(2.2 / Math.SQRT2) / Math.SQRT2], 'σ = √2', { dx: 10, dy: -4, cls: 'ink', size: 'sm' }));
    g.push(B.label([-3.6, 0.05], '점선은 원래 폭', { dx: 0, dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(330, 152, 384, 152, { cls: 'ark', width: 1.6 }));
    g.push(txt(357, 138, '더한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[62, 252], [658, 252]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(360, 276, 'V(X + Y) = V(X) + V(Y) + 2 Cov(X, Y) — 독립이면 마지막 항이 사라진다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(360, 300, '1 + 1 = 2 이므로 σ = √2 다. 두 배가 아니다. 이것이 평균을 여러 번 재면 정확해지는 이유의 씨앗이다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 324, 'V(X − Y) 도 같은 값이다. 빼도 흔들림은 커진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'st-b-var-add',
        svg: svg({
            width: W, height: H,
            title: '독립인 확률변수의 합에서는 분산이 더해진다',
            desc: '표준편차 1 인 둘을 더하면 분산이 2 가 되고 표준편차는 루트 2 가 된다. 두 배가 아니다',
            body: BG + g.join(''),
        }),
    };
})());

/* ---- 6-8. 이변량정규분포 ---- */
add((() => {
    const W = 740, H = 320;
    const g = [];
    g.push(txt(370, 28, '이변량정규분포 — 등고선이 타원이고 ρ 가 기울기를 정한다', { anchor: 'middle', cls: 'ink bold' }));
    const mk = (ox, rho, title) => {
        const fr = frame({ xRange: [-3.2, 3.2], yRange: [-3.2, 3.2], box: { x: ox, y: 74, w: 168, h: 152 } });
        const cx = (fr.X(-3.2) + fr.X(3.2)) / 2, cy = (fr.Y(-3.2) + fr.Y(3.2)) / 2;
        const sx = (fr.X(3.2) - fr.X(-3.2)) / 6.4, sy = (fr.Y(-3.2) - fr.Y(3.2)) / 6.4;
        const out = [fr.axes({ xTicks: [], yTicks: [], grid: false })];
        for (const c of [3.0, 2.0, 1.0]) {
            const a = Math.sqrt(c * (1 + rho)), b = Math.sqrt(c * (1 - rho));
            out.push(`<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(a * sx)}" ry="${r2(b * sy)}" transform="rotate(-45 ${r2(cx)} ${r2(cy)})" fill="var(--s1)" fill-opacity="0.13" stroke="var(--s1)" stroke-width="1.3"/>`);
        }
        out.push(pdot(cx, cy, 'var(--s2)', 3));
        out.push(txt(ox + 84, 60, title, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };
    g.push(mk(66, 0, 'ρ = 0 — 원'));
    g.push(mk(286, 0.8, 'ρ = 0.8 — 오른쪽 위로'));
    g.push(mk(506, -0.8, 'ρ = −0.8 — 왼쪽 위로'));
    g.push(txt(150, 246, '한 변수를 알아도', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(150, 264, '다른 쪽 예측이 나아지지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 246, 'x 를 알면 y 의 범위가', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(370, 264, '크게 좁아진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(590, 246, '방향만 반대일 뿐', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(590, 264, '좁아지는 정도는 같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[66, 284], [674, 284]], { stroke: 'var(--grid)', sw: 1 }));
    g.push(txt(370, 308, '이 분포에서만은 ρ = 0 이 곧 독립이다. 일반적으로는 성립하지 않는 특별한 성질이다', { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'st-b-binormal',
        svg: svg({
            width: W, height: H,
            title: '이변량정규분포의 등고선',
            desc: '상관이 0 이면 등고선이 원이고 상관이 커지면 대각선 방향으로 길쭉한 타원이 된다',
            body: BG + g.join(''),
        }),
    };
})());

export default figures;
