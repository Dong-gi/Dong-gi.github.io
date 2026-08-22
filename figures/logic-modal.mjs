/**
 * 논리학 18장(양상논리)의 그림.
 *
 * 이름은 모두 `log-p-` 로 시작한다(18장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-p-*.d2` 에 있고, 여기에는
 * 점과 화살표의 자리를 손으로 잡아야 하는 것 셋만 둔다 — 모형 하나에서
 * □ 와 ◇ 를 실제로 계산해 보이는 그림, 프레임 성질 셋의 모양, 그리고
 * S5 프레임이 덩어리로 갈라진다는 그림이다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊨ □ ◇ ∈ ∉ ⟨ ⟩ ℳ 로 직접 적는다.
 * 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다. lib 의 esc 가 `~` 를
 * 아래첨자로 먹으므로 세계 이름은 `w~1` 처럼 적고 그 밖에서는 물결표를 쓰지 않는다.
 */
import { svg, esc } from './lib.mjs';

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
 * 소도구. lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가 SVG 안에
 * 없어 선이 사라진다. 그래서 색을 직접 넣는 것들을 따로 둔다.
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.7, dash, back } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"`
        + `${back ? ` marker-start="url(#${marker})"` : ''}${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ` d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 색을 직접 넣는 글자. lib 의 txt 는 클래스만 받는다. */
function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${size === 'lg' ? ' font-size="16"' : ''}`
        + `${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 20, title, { anchor: 'middle', col: CI, bold: true, size: 'sm' }) : '')
        + (sub ? ctxt(x + w / 2, y + 37, sub, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/** 세계 하나. 원과 이름, 그리고 그 세계에서 참인 원자 목록. */
function world(cx, cy, name, atoms, { r = 26, col = C1, fill = C1 } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r}" fill="${fill}" fill-opacity="0.12" stroke="${col}" stroke-width="1.8"/>`
        + ctxt(cx, cy + 5, name, { anchor: 'middle', col, bold: true })
        + (atoms ? ctxt(cx, cy + r + 17, atoms, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/** 원 두 개를 잇는 접근관계 화살표. 원의 테두리에서 시작해 테두리에서 끝난다. */
function edge(a, b, { r = 26, gap = 5, col = CK, marker = 'ark', width = 1.7, dash, back } = {}) {
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1;
    const ux = dx / len;
    const uy = dy / len;
    return arw(a[0] + ux * (r + gap), a[1] + uy * (r + gap), b[0] - ux * (r + gap + 3), b[1] - uy * (r + gap + 3),
        { col, marker, width, dash, back });
}

/** 자기 자신으로 가는 화살표. 원 위쪽에 고리를 그린다. */
function loop(cx, cy, { r = 26, col = CK, marker = 'ark', width = 1.7, up = 56 } = {}) {
    const y0 = cy - r + 2;
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})" `
        + `d="M${r2(cx - 11)} ${r2(y0)} C${r2(cx - 0.8 * up)} ${r2(cy - r - up)}, ${r2(cx + 0.8 * up)} ${r2(cy - r - up)}, ${r2(cx + 11)} ${r2(y0)}"/>`;
}

/* ================================================================== *
 * 18-1. 모형 하나에서 □ 와 ◇ 를 실제로 계산한다
 * ================================================================== */
add((() => {
    const W = 900, H = 508;
    const g = [];
    g.push(ctxt(W / 2, 26, '□ 의 값은 이 세계가 아니라 이 세계에서 보이는 세계들이 정한다', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 왼쪽: 그래프 ---- */
    g.push(panel(20, 44, 430, 316, 'ℳ = ⟨W, R, V⟩'));
    g.push(ctxt(235, 82, 'R = { ⟨w~1,w~2⟩, ⟨w~1,w~3⟩, ⟨w~2,w~4⟩, ⟨w~3,w~2⟩ }', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(235, 99, 'V(P) = {w~2, w~3} · V(Q) = {w~3, w~4} — 원 아래는 참인 원자', { anchor: 'middle', col: CK, size: 'sm' }));

    const w1 = [100, 224], w2 = [232, 152], w3 = [232, 296], w4 = [364, 224];
    g.push(edge(w1, w2));
    g.push(edge(w1, w3));
    g.push(edge(w2, w4));
    g.push(edge(w3, w2));
    g.push(world(w1[0], w1[1], 'w~1', '(없음)'));
    g.push(world(w2[0], w2[1], 'w~2', 'P'));
    g.push(world(w3[0], w3[1], 'w~3', 'P, Q'));
    g.push(world(w4[0], w4[1], 'w~4', 'Q', { col: C2, fill: C2 }));
    g.push(ctxt(364, 182, '막다른 세계', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(364, 288, '나가는 화살표가 없다', { anchor: 'middle', col: C2, size: 'sm' }));

    /* ---- 오른쪽: w~1 에서의 계산 ---- */
    g.push(panel(466, 44, 414, 316, 'w~1 에서 하나씩 따져 본다', 'w~1 이 보는 것은 w~2 와 w~3 뿐이다'));

    const rows = [
        ['P', '거짓', 'w~1 ∉ V(P)', false],
        ['◇P', '참', 'w~2 에서 P 가 참이다', true],
        ['□P', '참', 'w~2 도 w~3 도 P 다', true],
        ['□Q', '거짓', 'w~2 에서 Q 가 거짓이다', false],
        ['□□P', '거짓', 'w~2 에서 □P 가 거짓', false],
    ];
    rows.forEach((row, i) => {
        const y = 124 + i * 40;
        const col = row[3] ? C3 : C2;
        g.push(box(482, y - 22, 382, 34, { fill: col, op: 0.1, stroke: col, sw: 1.2, rx: 4 }));
        g.push(ctxt(498, y, row[0], { col: CI, bold: true }));
        g.push(ctxt(566, y, row[1], { col, bold: true, size: 'sm' }));
        g.push(ctxt(612, y, row[2], { col: CK, size: 'sm' }));
    });
    g.push(ctxt(482, 322, 'w~2 가 보는 것은 w~4 뿐이고 거기서 P 가 거짓이다', { col: CK, size: 'sm' }));
    g.push(ctxt(482, 344, '셋째 줄과 다섯째 줄 — 이 모형에서 4 가 깨진다', { col: CI, size: 'sm', bold: true }));

    /* ---- 아래 메모 ---- */
    g.push(ln([[20, 380], [880, 380]], { stroke: CG, sw: 1 }));
    g.push(ctxt(24, 404, '조항 하나만 새것이다. ¬ ∧ ∨ → ↔ 는 그 세계 안에서 6장 그대로 계산되고, □ 와 ◇ 만 화살표를 따라 다른 세계로 나간다.', { col: CI, size: 'sm' }));
    g.push(ctxt(24, 424, '□φ 는 보이는 모든 세계에서 φ 가 참일 때, ◇φ 는 보이는 세계 중 하나에서 φ 가 참일 때 참이다.', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(24, 452, 'w~4 를 보라. 나가는 화살표가 하나도 없으므로 ‘보이는 모든 세계에서 참’ 이 공허하게 성립한다. w~4 에서는 □⊥ 조차 참이고', { col: C2, size: 'sm' }));
    g.push(ctxt(24, 472, '◇(P ∨ ¬P) 조차 거짓이다. 6장의 공허하게 참이 여기서 되돌아온다 — 그리고 w~1 에서도 w~4 에서도 □P → P 가 무너진다.', { col: C2, size: 'sm' }));
    g.push(ctxt(24, 498, '무너지는 이유는 하나다. 이 프레임에는 자기 자신으로 가는 화살표가 없다.', { col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-p-frame-eval',
        svg: svg({
            width: W, height: H,
            title: '크립케 모형 하나에서 상자와 마름모의 값을 실제로 계산한다',
            desc: '네 세계와 네 화살표로 된 모형에서 P, ◇P, □P, □Q, □□P 의 값을 첫 세계에서 하나씩 따진다. 막다른 세계에서는 □ 가 공허하게 참이 된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 18-2. 프레임 성질 셋의 모양
 * ================================================================== */
add((() => {
    const W = 900, H = 424;
    const g = [];
    g.push(ctxt(W / 2, 26, '점선 화살표를 보라 — 그 화살표가 반드시 있어야 한다는 요구가 곧 그 성질이다', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 반사적 ---- */
    g.push(panel(20, 44, 276, 270, '반사적 (reflexive)', '모든 w 에 대해 wRw'));
    g.push(loop(158, 214, { col: C2, marker: 'ar2', width: 2 }));
    g.push(world(158, 214, 'w', ''));
    g.push(ctxt(158, 128, '자기 자신이 보인다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(158, 296, 'T   □φ → φ', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 추이적 ---- */
    g.push(panel(312, 44, 276, 270, '추이적 (transitive)', 'wRu 이고 uRt 이면 wRt'));
    const tw = [372, 148], tu = [450, 244], tv = [528, 148];
    g.push(edge(tw, tu, { r: 22 }));
    g.push(edge(tu, tv, { r: 22 }));
    g.push(edge(tw, tv, { r: 22, col: C2, marker: 'ar2', width: 2, dash: '6 4' }));
    g.push(world(tw[0], tw[1], 'w', '', { r: 22 }));
    g.push(world(tu[0], tu[1], 'u', '', { r: 22 }));
    g.push(world(tv[0], tv[1], 't', '', { r: 22 }));
    g.push(ctxt(450, 118, '두 걸음이 한 걸음으로', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(450, 296, '4   □φ → □□φ', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 유클리드적 ---- */
    g.push(panel(604, 44, 276, 270, '유클리드적 (euclidean)', 'wRu 이고 wRt 이면 uRt'));
    const ew = [742, 130], eu = [676, 228], ev = [812, 228];
    g.push(edge(ew, eu, { r: 22 }));
    g.push(edge(ew, ev, { r: 22 }));
    g.push(edge(eu, ev, { r: 22, col: C2, marker: 'ar2', width: 2, dash: '6 4' }));
    g.push(world(ew[0], ew[1], 'w', '', { r: 22 }));
    g.push(world(eu[0], eu[1], 'u', '', { r: 22 }));
    g.push(world(ev[0], ev[1], 't', '', { r: 22 }));
    g.push(ctxt(742, 270, '보이는 둘끼리도 서로 보인다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(742, 296, '5   ◇φ → □◇φ', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 아래 메모 ---- */
    g.push(ln([[20, 336], [880, 336]], { stroke: CG, sw: 1 }));
    g.push(ctxt(24, 360, '유클리드적은 w · u · t 를 어떻게 고르든 성립해야 하므로 u 와 t 를 맞바꾼 것도 요구된다 — 점선은 사실 양쪽 방향이다.', { col: CI, size: 'sm' }));
    g.push(ctxt(24, 382, '그래서 반사적이면서 유클리드적이면 대칭적이고 추이적이게 된다. 그 셋을 다 갖춘 관계가 동치관계이고, 그것이 S5 다.', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(24, 408, '세 그림 어디에도 논리식이 없다는 것을 눈여겨보라. 이것은 관계 R 에 대한 조건이지 대상언어의 문장이 아니다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-p-three-properties',
        svg: svg({
            width: W, height: H,
            title: '반사적 추이적 유클리드적 — 세 프레임 성질의 모양',
            desc: '세 칸에 각각 자기 고리, 두 걸음을 잇는 지름길, 한 점에서 보이는 두 세계를 잇는 화살표를 그리고 대응하는 공리 T 4 5 를 아래에 적었다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 18-3. S5 프레임은 덩어리로 갈라진다
 * ================================================================== */
add((() => {
    const W = 900, H = 468;
    const g = [];
    g.push(ctxt(W / 2, 26, 'S5 에서는 어느 세계에서 보아도 보이는 것이 같다 — 그래서 양상이 쌓이지 않는다', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 왼쪽: 덩어리 ---- */
    g.push(panel(20, 44, 486, 316, 'R 이 동치관계이면 W 가 덩어리로 갈라진다', '덩어리 안에서는 모든 짝이 서로 보이고 덩어리 사이에는 화살표가 없다'));

    // 덩어리 하나
    g.push(box(48, 118, 236, 190, { fill: C1, op: 0.07, stroke: C1, sw: 1.4, rx: 14, dash: '6 4' }));
    const a1 = [106, 252], a2 = [226, 252], a3 = [166, 180];
    [[a1, a2], [a1, a3], [a2, a3]].forEach(([p, q]) => g.push(edge(p, q, { r: 19, gap: 3, width: 1.3, back: true })));
    [a1, a2, a3].forEach(p => g.push(loop(p[0], p[1], { r: 19, width: 1.3, up: 30 })));
    g.push(world(a1[0], a1[1], 'w', '', { r: 19 }));
    g.push(world(a2[0], a2[1], 'u', '', { r: 19 }));
    g.push(world(a3[0], a3[1], 't', '', { r: 19 }));
    g.push(ctxt(166, 332, '덩어리 하나 — 셋이 서로 통한다', { anchor: 'middle', col: C1, size: 'sm', bold: true }));

    // 다른 덩어리
    g.push(box(316, 118, 168, 190, { fill: C3, op: 0.09, stroke: C3, sw: 1.4, rx: 14, dash: '6 4' }));
    const b1 = [360, 230], b2 = [440, 230];
    g.push(edge(b1, b2, { r: 19, gap: 3, width: 1.3, back: true }));
    [b1, b2].forEach(p => g.push(loop(p[0], p[1], { r: 19, width: 1.3, up: 30 })));
    g.push(world(b1[0], b1[1], 'x', '', { r: 19, col: C3, fill: C3 }));
    g.push(world(b2[0], b2[1], 'y', '', { r: 19, col: C3, fill: C3 }));
    g.push(ctxt(398, 332, '다른 덩어리 — 저기가 안 보인다', { anchor: 'middle', col: C3, size: 'sm', bold: true }));

    /* ---- 오른쪽: 양상 붕괴 ---- */
    g.push(panel(522, 44, 358, 316, '그 결과 — 양상이 쌓여도 늘어나지 않는다', '앞에 무엇이 붙든 맨 뒤의 것 하나만 남는다'));

    ['□□φ  ↔  □φ', '◇□φ  ↔  □φ', '□◇φ  ↔  ◇φ', '◇◇φ  ↔  ◇φ'].forEach((e, i) => {
        const y = 128 + i * 38;
        g.push(box(538, y - 21, 326, 30, { fill: C1, op: 0.09, stroke: C1, sw: 1.1, rx: 4 }));
        g.push(ctxt(562, y, e, { col: CI, bold: true }));
    });
    g.push(ctxt(538, 306, 'S5 에서 서로 다른 양상은 셋뿐이다 — 없음 · □ · ◇', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(538, 328, 'S4 에서는 열넷이라고 알려져 있다', { col: CK, size: 'sm' }));

    /* ---- 아래 메모 ---- */
    g.push(ln([[20, 384], [880, 384]], { stroke: CG, sw: 1 }));
    g.push(ctxt(24, 408, '이유는 왼쪽 그림에 있다. □φ 를 판정하려고 덩어리 안의 어느 세계로 옮겨 가도 그 세계가 보는 것은 같은 덩어리 전체다.', { col: CI, size: 'sm' }));
    g.push(ctxt(24, 430, '그러니 한 번 물은 것을 다시 물어도 답이 같다. 겹쳐 붙인 □ 와 ◇ 가 하나로 줄어드는 것이 그 뜻이다.', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(24, 456, 'S5 가 편해 보이지만 편한 것이 늘 옳은 것은 아니다. 인식 읽기에서 이 붕괴는 ‘모르면 모른다는 것을 안다’ 는 강한 주장이 된다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-p-s5-cluster',
        svg: svg({
            width: W, height: H,
            title: 'S5 프레임은 서로 통하는 덩어리로 갈라지고 양상이 붕괴한다',
            desc: '왼쪽은 동치관계가 세계를 덩어리로 나누는 모습이고 오른쪽은 그 결과로 겹친 양상이 하나로 줄어드는 네 등가식이다',
            body: g.join(''),
        }),
    };
})());

export default figures;
