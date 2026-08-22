/**
 * 논리학 8장(증명을 찾는 전략)의 그림.
 *
 * 이름은 모두 `log-f-` 로 시작한다(8장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-f-*.d2` 에 있고, 여기에는
 * 자리를 손으로 잡아야 하는 것(피치 스타일 도출 용지, 부증명 상자의 범위,
 * 경우 나누기의 모양)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ 로 직접 적는다. 라벨에 물결표를 쓰면
 * lib 의 esc 가 아래첨자로 내려 버리므로 범위는 짧은 붙임표(–)로 적는다.
 */
import { svg, txt, esc } from './lib.mjs';

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
 * 화소 좌표 소도구 — lib 의 px()/txt() 는 색을 클래스로만 받아
 * 이 파일이 쓰는 강조색을 낼 수 없다. 색을 직접 넣는 판을 따로 둔다.
 * ------------------------------------------------------------------ */

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

function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub, { stroke = CG } = {}) {
    return box(x, y, w, h, { stroke, sw: 1.2, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 21, title, { anchor: 'middle', bold: true, size: 'sm' }) : '')
        + (sub ? ctxt(x + w / 2, y + 38, sub, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/**
 * 피치 스타일 도출 용지 한 줄.
 * depth 는 부증명의 깊이. 깊이마다 세로 막대가 하나씩 왼쪽에 선다.
 */
function fitchRow(x, y, w, rh, no, formula, reason, {
    depth = 0, tint, bar = C1, note, noteCol = CK, strike = false, bold = false,
} = {}) {
    const g = [];
    const indent = 16;
    if (tint) g.push(box(x, y, w, rh, { fill: tint, op: 0.14, stroke: 'none', sw: 0, rx: 2 }));
    if (no) g.push(ctxt(x + 20, y + rh / 2 + 4, String(no), { anchor: 'end', col: CK, size: 'sm' }));
    for (let d = 0; d < depth; d += 1) {
        g.push(ln([[x + 30 + d * indent, y], [x + 30 + d * indent, y + rh]], { stroke: bar, sw: 1.6 }));
    }
    const fx = x + 38 + depth * indent;
    g.push(ctxt(fx, y + rh / 2 + 4, formula, { col: CI, bold }));
    if (strike) {
        g.push(ln([[fx - 3, y + rh / 2], [fx + 8 * formula.length + 6, y + rh / 2]], { stroke: C2, sw: 1.4 }));
    }
    if (reason) g.push(ctxt(x + w - 10, y + rh / 2 + 4, reason, { anchor: 'end', col: CK, size: 'sm' }));
    if (note) g.push(ctxt(x + w + 12, y + rh / 2 + 4, note, { col: noteCol, size: 'sm' }));
    return g.join('');
}

/** 부증명 상자가 닫히는 자리를 알리는 짧은 가로 막대. */
function closeBar(x, y, depth, { bar = C1 } = {}) {
    const px0 = x + 30 + (depth - 1) * 16;
    return ln([[px0, y], [px0 + 12, y]], { stroke: bar, sw: 1.6 });
}

/* ================================================================== *
 * 8-1. 가운데서 만난다 — 두 방향이 같은 식에서 부딪친다
 * ================================================================== */
add((() => {
    const W = 800, H = 468;
    const g = [];
    g.push(ctxt(W / 2, 26, '거꾸로 간 목표와 앞으로 민 전제가 같은 식에서 만난다', { anchor: 'middle', bold: true }));
    g.push(ctxt(W / 2, 46, '전제 P → (Q → R) 에서 (P ∧ Q) → R 을 얻는 문제', { anchor: 'middle', col: CK, size: 'sm' }));

    /* 왼쪽 — 목표에서 거꾸로 */
    g.push(panel(14, 60, 372, 246, '거꾸로 — 목표를 쪼갠다', '주연결자를 보고 마지막 수를 정한다', { stroke: C1 }));
    const L = [
        ['목표  (P ∧ Q) → R', '주연결자가 → 이다'],
        ['→I 를 마지막 수로 쓴다', 'P ∧ Q 를 가정한다'],
        ['새 목표  R', 'R 은 원자다 — 더 쪼갤 수 없다'],
    ];
    L.forEach(([a, b], i) => {
        const y = 108 + i * 62;
        g.push(box(34, y, 332, 46, { stroke: i === 2 ? C1 : CG, sw: i === 2 ? 2 : 1.2, rx: 4 }));
        g.push(ctxt(48, y + 20, a, { col: CI, bold: i === 2 }));
        g.push(ctxt(48, y + 37, b, { col: CK, size: 'sm' }));
        if (i < 2) g.push(arw(200, y + 46, 200, y + 60, { col: C1, marker: 'ar1', width: 1.6 }));
    });
    g.push(ctxt(200, 322, '거꾸로 가기는 여기서 멈춘다', { anchor: 'middle', col: C1, size: 'sm' }));

    /* 오른쪽 — 전제에서 앞으로 */
    g.push(panel(400, 60, 386, 246, '앞으로 — 손에 든 것을 푼다', '가정도 손에 든 것에 들어간다', { stroke: C2 }));
    const R = [
        ['P → (Q → R) 과 P ∧ Q', '전제 하나, 가정 하나'],
        ['∧E 두 번 → P 와 Q', '언제나 안전한 수'],
        ['→E → Q → R,  다시 →E → R', '전건을 손에 넣었으니 뗄 수 있다'],
    ];
    R.forEach(([a, b], i) => {
        const y = 108 + i * 62;
        g.push(box(420, y, 348, 46, { stroke: i === 2 ? C2 : CG, sw: i === 2 ? 2 : 1.2, rx: 4 }));
        g.push(ctxt(434, y + 20, a, { col: CI, bold: i === 2 }));
        g.push(ctxt(434, y + 37, b, { col: CK, size: 'sm' }));
        if (i < 2) g.push(arw(594, y + 46, 594, y + 60, { col: C2, marker: 'ar2', width: 1.6 }));
    });
    g.push(ctxt(594, 322, '앞으로 밀기도 여기서 멈춘다', { anchor: 'middle', col: C2, size: 'sm' }));

    /* 만나는 자리 */
    g.push(box(258, 348, 284, 52, { fill: C3, op: 0.14, stroke: C3, sw: 2, rx: 6 }));
    g.push(ctxt(400, 372, '두 쪽이 모두 R 을 가리킨다', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(400, 391, '증명이 닫히는 자리다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(arw(200, 330, 300, 350, { col: C1, marker: 'ar1', width: 1.8 }));
    g.push(arw(594, 330, 500, 350, { col: C2, marker: 'ar2', width: 1.8 }));

    g.push(ctxt(W / 2, 428, '어느 한쪽만으로는 닿지 않는다 — 목표를 쪼개야 P ∧ Q 가 손에 들어오고,', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(W / 2, 446, '전제를 풀어야 그 P ∧ Q 가 쓸모를 얻는다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-f-meet-middle',
        svg: svg({
            width: W, height: H,
            title: '목표에서 거꾸로 간 길과 전제에서 앞으로 민 길이 같은 식에서 만난다',
            desc: '왼쪽은 목표 (P ∧ Q) → R 을 →I 로 쪼개 새 목표 R 에 이르는 흐름, 오른쪽은 전제와 가정을 ∧E 와 →E 로 풀어 R 에 이르는 흐름, 아래에서 두 흐름이 R 에서 만난다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8-2. 범위 — 닫힌 상자 안의 줄은 다시 부를 수 없다
 * ================================================================== */
add((() => {
    const W = 800, H = 452;
    const g = [];
    g.push(ctxt(W / 2, 26, '지금 줄에서 부를 수 있는 줄과 부를 수 없는 줄', { anchor: 'middle', bold: true }));
    g.push(ctxt(W / 2, 46, '세로 막대는 부증명 하나를 뜻한다. 막대가 끊긴 자리에서 그 상자는 닫힌다', { anchor: 'middle', col: CK, size: 'sm' }));

    const x0 = 22, w = 470, rh = 30;
    let y = 70;
    const rows = [
        [1, 'P → Q', '전제', 0, true],
        [2, 'P', '가정', 1, false],
        [3, 'Q', '1, 2 →E', 1, false],
        [4, 'P ∧ Q', '2, 3 ∧I', 1, false],
        [5, 'P → (P ∧ Q)', '2–4 →I', 0, true],
        [6, '¬Q', '가정', 1, null],
        [7, 'Q', '?', 1, null],
    ];
    g.push(box(x0, y - 4, w, rows.length * rh + 12, { stroke: CG, sw: 1.2, rx: 6 }));
    rows.forEach(([no, f, r, d, live], i) => {
        const yy = y + i * rh;
        const closed = live === false;
        g.push(fitchRow(x0, yy, w, rh, no, f, r, {
            depth: d,
            bar: i >= 5 ? C3 : C1,
            tint: closed ? C2 : (live === true ? C3 : undefined),
        }));
    });
    g.push(closeBar(x0, y + 5 * rh, 1, { bar: C1 }));
    g.push(ln([[x0 + 8, y + 7 * rh - 2], [x0 + w - 8, y + 7 * rh - 2]], { stroke: CG, sw: 1 }));

    /* 오른쪽 설명 */
    const bx = 512;
    g.push(box(bx, 66, 268, 84, { fill: C3, op: 0.12, stroke: C3, sw: 1.6, rx: 6 }));
    g.push(ctxt(bx + 14, 90, '7번 줄에서 부를 수 있는 줄', { col: CI, bold: true, size: 'sm' }));
    g.push(ctxt(bx + 14, 112, '1번 — 상자 밖에 있다', { col: CK, size: 'sm' }));
    g.push(ctxt(bx + 14, 130, '5번 — 상자가 닫힌 뒤 밖에 적혔다', { col: CK, size: 'sm' }));

    g.push(box(bx, 166, 268, 102, { fill: C2, op: 0.12, stroke: C2, sw: 1.6, rx: 6 }));
    g.push(ctxt(bx + 14, 190, '부를 수 없는 줄', { col: CI, bold: true, size: 'sm' }));
    g.push(ctxt(bx + 14, 212, '2 · 3 · 4번 — 5번에서 상자가 닫혔다', { col: CK, size: 'sm' }));
    g.push(ctxt(bx + 14, 230, '그 줄들은 가정 P 아래에서만 참이다', { col: CK, size: 'sm' }));
    g.push(ctxt(bx + 14, 248, '지금은 P 를 가정하고 있지 않다', { col: CK, size: 'sm' }));
    g.push(arw(500, 190, bx - 6, 190, { col: C2, marker: 'ar2', width: 1.5 }));
    g.push(arw(500, 100, bx - 6, 100, { col: C3, marker: 'ar3', width: 1.5 }));

    /* 아래 요지 */
    g.push(box(22, 300, 758, 62, { stroke: CG, sw: 1.2, rx: 6 }));
    g.push(ctxt(40, 324, '규칙 하나 — 어떤 줄을 부르려면 그 줄을 감싼 상자가 모두 아직 열려 있어야 한다.', { col: CI, size: 'sm' }));
    g.push(ctxt(40, 344, '상자가 하나라도 닫혔으면 그 줄은 없는 것과 같다. 남은 것은 상자 전체를 요약한 5번 줄뿐이다.', { col: CK, size: 'sm' }));

    g.push(ctxt(W / 2, 392, '5번 줄 하나가 상자 안의 세 줄을 대신한다 — 부증명이 남기는 것은 그 안의 줄들이 아니라 조건문 한 줄이다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(W / 2, 420, '증명을 다 쓴 뒤 이 검사를 한 번 돌리는 것만으로 흔한 오류의 절반이 걸러진다', { anchor: 'middle', col: CI, size: 'sm' }));

    return {
        name: 'log-f-scope-box',
        svg: svg({
            width: W, height: H,
            title: '부증명 상자가 닫히면 그 안의 줄은 다시 부를 수 없다',
            desc: '피치 스타일 도출 일곱 줄에서 2번부터 4번까지가 닫힌 상자 안에 있어 7번 줄에서 부를 수 없고, 상자 밖의 1번과 상자를 요약한 5번만 부를 수 있음을 색으로 갈라 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8-3. 경우 나누기의 모양과 값
 * ================================================================== */
add((() => {
    const W = 800, H = 470;
    const g = [];
    g.push(ctxt(W / 2, 26, '∨E 는 목표를 두 번 증명하게 만든다', { anchor: 'middle', bold: true }));
    g.push(ctxt(W / 2, 46, '두 부증명의 마지막 줄이 같아야 하고, 그 줄만 상자 밖으로 나온다', { anchor: 'middle', col: CK, size: 'sm' }));

    const x0 = 22, w = 452, rh = 30;
    const y = 70;
    const rows = [
        [1, 'P ∨ Q', '전제', 0, null],
        [2, 'P → R', '전제', 0, null],
        [3, 'Q → R', '전제', 0, null],
        [4, 'P', '가정', 1, 'a'],
        [5, 'R', '2, 4 →E', 1, 'a'],
        [6, 'Q', '가정', 1, 'b'],
        [7, 'R', '3, 6 →E', 1, 'b'],
        [8, 'R', '1, 4–5, 6–7 ∨E', 0, 'out'],
    ];
    g.push(box(x0, y - 4, w, rows.length * rh + 12, { stroke: CG, sw: 1.2, rx: 6 }));
    rows.forEach(([no, f, r, d, tag], i) => {
        const yy = y + i * rh;
        g.push(fitchRow(x0, yy, w, rh, no, f, r, {
            depth: d,
            bar: tag === 'b' ? C2 : C1,
            tint: tag === 'out' ? C3 : undefined,
            bold: tag === 'out',
        }));
    });
    g.push(closeBar(x0, y + 5 * rh, 1, { bar: C1 }));
    g.push(closeBar(x0, y + 7 * rh, 1, { bar: C2 }));

    /* 갈라졌다 모이는 모양 */
    const gx = 512;
    g.push(ctxt(gx + 130, 92, '모양', { anchor: 'middle', col: CI, bold: true, size: 'sm' }));
    g.push(box(gx + 78, 104, 104, 32, { stroke: CI, sw: 1.6, rx: 4 }));
    g.push(ctxt(gx + 130, 125, 'P ∨ Q', { anchor: 'middle', col: CI, bold: true }));
    g.push(box(gx + 8, 178, 104, 50, { stroke: C1, sw: 1.6, rx: 4 }));
    g.push(ctxt(gx + 60, 198, 'P 를 가정', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(gx + 60, 217, 'R 까지 간다', { anchor: 'middle', col: C1, size: 'sm' }));
    g.push(box(gx + 148, 178, 104, 50, { stroke: C2, sw: 1.6, rx: 4 }));
    g.push(ctxt(gx + 200, 198, 'Q 를 가정', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(gx + 200, 217, 'R 까지 간다', { anchor: 'middle', col: C2, size: 'sm' }));
    g.push(arw(gx + 112, 136, gx + 62, 174, { col: C1, marker: 'ar1', width: 1.6 }));
    g.push(arw(gx + 148, 136, gx + 198, 174, { col: C2, marker: 'ar2', width: 1.6 }));
    g.push(box(gx + 78, 268, 104, 32, { fill: C3, op: 0.16, stroke: C3, sw: 1.8, rx: 4 }));
    g.push(ctxt(gx + 130, 289, 'R', { anchor: 'middle', col: CI, bold: true }));
    g.push(arw(gx + 62, 230, gx + 112, 264, { col: C1, marker: 'ar1', width: 1.6 }));
    g.push(arw(gx + 198, 230, gx + 148, 264, { col: C2, marker: 'ar2', width: 1.6 }));
    g.push(ctxt(gx + 130, 322, '두 갈래가 같은 곳에 닿아야만', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(gx + 130, 340, 'R 이 상자 밖으로 나온다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* 값과 비용 */
    g.push(box(22, 366, 452, 84, { stroke: CG, sw: 1.2, rx: 6 }));
    g.push(ctxt(40, 390, '값 — ∨ 를 쓸 수 있게 해 주는 규칙은 이것뿐이다', { col: CI, size: 'sm' }));
    g.push(ctxt(40, 410, '비용 — 목표가 반으로 쪼개지지 않는다. 같은 목표를 두 번 증명한다', { col: C2, size: 'sm' }));
    g.push(ctxt(40, 430, '그래서 전제에 ∨ 가 있을 때만 꺼낸다. 목표가 ∨ 인 것은 이유가 못 된다', { col: CK, size: 'sm' }));

    return {
        name: 'log-f-or-elim-shape',
        svg: svg({
            width: W, height: H,
            title: '경우 나누기의 모양 — 두 부증명이 같은 결론에 닿아야 그 결론이 밖으로 나온다',
            desc: '왼쪽은 P ∨ Q 와 두 조건문에서 R 을 얻는 여덟 줄짜리 도출, 오른쪽은 같은 것을 갈라졌다 모이는 상자 그림으로 다시 그린 것',
            body: g.join(''),
        }),
    };
})());

export default figures;
