/**
 * 논리학 12장(동일성과 수 세기)의 그림.
 *
 * 이름은 모두 `log-j-` 로 시작한다(12장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-j-*.d2` 에 있고, 여기에는
 * 위치를 손으로 잡아야 하는 것(정의역의 점과 대각선, 두 모형 나란히 놓기,
 * 한정사가 같은 점을 두 번 집는 그림)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ≠ ∈ ⊆ 로 직접 적고, 모형은 ℳ,
 * 정의역은 D 로 적는다. 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다.
 * lib 의 esc 가 `~` 를 아래첨자로 먹으므로 라벨에 물결표를 쓰지 않는다.
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
 * 화소 좌표 소도구 — lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가
 * SVG 안에 없어 선이 사라진다. 그래서 색을 직접 넣는 것들을 따로 둔다.
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.7, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function carw(x1, y1, x2, y2, bow, { col = CK, marker = 'ark', width = 1.7 } = {}) {
    const mx = (x1 + x2) / 2;
    const my = (y1 + y2) / 2;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const len = Math.hypot(dx, dy) || 1;
    const cx = mx - (dy / len) * bow;
    const cy = my + (dx / len) * bow;
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})" d="M${r2(x1)} ${r2(y1)} Q${r2(cx)} ${r2(cy)} ${r2(x2)} ${r2(y2)}"/>`;
}

/** 제자리로 돌아오는 화살표(자기 자신과의 관계). */
function loop(cx, cy, r, { col = CK, marker = 'ark', width = 1.7 } = {}) {
    const a = `${r2(cx - r * 0.6)} ${r2(cy - r * 0.75)}`;
    const b = `${r2(cx + r * 0.62)} ${r2(cy - r * 0.72)}`;
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})" d="M${a} C${r2(cx - r * 2)} ${r2(cy - r * 3.1)} ${r2(cx + r * 2)} ${r2(cy - r * 3.1)} ${b}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { stroke = CG, sw = 1.4, fill = 'none', op = 1, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
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

/** 정의역의 점 하나. 이름을 점 아래에 적는다. */
function pt(x, y, name, { col = CI, r = 6, below = 20 } = {}) {
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`
        + (name ? txt(x, y + below, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '');
}

/* ================================================================== *
 * 12-1. 해석표에서 = 칸만 잠겨 있다
 * ================================================================== */
add((() => {
    const W = 800, H = 400;
    const g = [];
    g.push(txt(W / 2, 26, '해석 I 는 기호마다 D 안에서 무엇을 고를지 정한다 — 그런데 = 칸은 고를 수 없다', { anchor: 'middle', cls: 'ink bold' }));

    /* ---- 왼쪽: 해석표 ---- */
    g.push(panel(14, 46, 388, 338, '해석표', '위 세 줄은 모형이 채우고 맨 아랫줄은 못 채운다'));

    const rows = [
        ['a', '원소 하나를 받는다', 'I(a) = 1', false],
        ['F  (1항)', 'D 의 부분집합을 받는다', 'I(F) = { 1, 2 }', false],
        ['G  (2항)', 'D × D 의 부분집합을 받는다', 'I(G) = { (1,3), (3,3) }', false],
        ['=  (2항)', '고를 수 없다 — 모형이 무엇이든', '{ (1,1), (2,2), (3,3) }', true],
    ];
    g.push(ctxt(40, 104, '기호', { col: CK, size: 'sm' }));
    g.push(ctxt(126, 104, '해석 I 가 무엇을 주는가', { col: CK, size: 'sm' }));
    g.push(ln([[30, 114], [388, 114]], { stroke: CG, sw: 1 }));

    rows.forEach(([sym, kind, val, locked], i) => {
        const y = 148 + i * 54;
        if (locked) g.push(box(30, y - 26, 356, 48, { fill: C2, op: 0.14, stroke: C2, sw: 1.4, rx: 4 }));
        g.push(ctxt(40, y, sym, { col: locked ? C2 : CI, bold: true, size: 'sm' }));
        g.push(ctxt(126, y - 8, kind, { col: locked ? C2 : CK, size: 'sm', bold: locked }));
        g.push(ctxt(126, y + 11, val, { col: locked ? C2 : CI, size: 'sm' }));
    });

    /* ---- 오른쪽: 대각선 그림 ---- */
    g.push(panel(418, 46, 368, 338, 'I(=) 가 강제되는 모양', 'D = { 1, 2, 3 } 위의 화살표를 다 그려 보면'));
    const cx = [498, 588, 678];
    const cy = 196;
    cx.forEach((x, i) => g.push(pt(x, cy, String(i + 1))));
    cx.forEach(x => g.push(loop(x, cy - 4, 13, { col: C2, marker: 'ar2', width: 2 })));
    // 없는 화살표는 점선으로 — 그려 놓고 없다고 말한다
    g.push(ln([[506, cy + 30], [580, cy + 30]], { stroke: CG, sw: 1.4, dash: '5 4' }));
    g.push(ln([[596, cy + 30], [670, cy + 30]], { stroke: CG, sw: 1.4, dash: '5 4' }));
    g.push(ctxt(588, cy + 60, '서로 다른 두 점을 잇는 화살표는', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(588, cy + 77, '어느 모형에도 하나도 없다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(588, cy - 62, '점마다 제자리로 오는 화살표 하나씩', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(588, cy - 45, '— 이것을 대각선이라 한다', { anchor: 'middle', col: C2, size: 'sm' }));
    g.push(ln([[432, 316], [772, 316]], { stroke: CG, sw: 1 }));
    g.push(ctxt(588, 340, 'F 와 G 는 갈아끼워도 되지만 = 는 갈아끼울 수 없다.', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(588, 358, '그것이 4장의 낱말로 논리 상항이라는 뜻이다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-j-fixed-interpretation',
        svg: svg({
            width: W, height: H,
            title: '해석표에서 등호 칸만 잠겨 있다',
            desc: '왼쪽 표의 아래 한 줄은 모형이 채우지 못하고, 오른쪽 그림은 그때 강제되는 대각선을 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 12-2. 한정사 둘이 같은 점을 두 번 집을 수 있다
 * ================================================================== */
add((() => {
    const W = 800, H = 360;
    const g = [];
    g.push(txt(W / 2, 26, '왜 ∃x ∃y (Fx ∧ Fy) 가 ‘F 인 것이 둘 있다’ 를 말하지 못하는가', { anchor: 'middle', cls: 'ink bold' }));

    /* ---- 왼쪽: 점 하나뿐인 모형 ---- */
    g.push(panel(14, 46, 380, 232, '점이 하나뿐인 모형', 'D = { 1 },  I(F) = { 1 }'));
    g.push(box(96, 118, 216, 116, { stroke: CG, sw: 1.4, rx: 10, dash: '6 4' }));
    g.push(ctxt(302, 224, 'D', { anchor: 'end', col: CK, size: 'sm' }));
    g.push(ell(204, 168, 62, 34, { stroke: C1, sw: 1.6, fill: C1, op: 0.14 }));
    g.push(ctxt(204, 130, 'I(F)', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    g.push(pt(204, 168, '1'));
    g.push(arw(116, 110, 190, 156, { col: C2, marker: 'ar2', width: 1.8 }));
    g.push(arw(296, 110, 220, 156, { col: C3, marker: 'ar3', width: 1.8 }));
    g.push(ctxt(112, 102, 'x 가 집는 것', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(300, 102, 'y 가 집는 것', { anchor: 'middle', col: C3, size: 'sm', bold: true }));

    /* ---- 오른쪽: 조항이 하는 일 ---- */
    g.push(panel(406, 46, 380, 232, '10장의 조항이 무엇을 요구하는가', '갈아 끼우는 원소 둘이 달라야 한다는 말이 없다'));
    const lines = [
        ['∃x 조항 — 어떤 d ∈ D 가 있어', CK],
        ['∃y 조항 — 어떤 e ∈ D 가 있어', CK],
        ['d = 1, e = 1 로 잡아도 된다', C2],
        ['그러면 F1 ∧ F1 이고 참이다', C2],
    ];
    lines.forEach(([s, col], i) => g.push(ctxt(426, 116 + i * 26, s, { col, size: 'sm', bold: col === C2 })));
    g.push(box(420, 210, 352, 48, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(ctxt(596, 231, 'F 인 것이 하나뿐인 모형에서 참이 되었다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(596, 248, '그러니 이 식은 ‘둘’ 을 말하지 않는다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* ---- 아래: 고친 식 ---- */
    g.push(box(14, 294, 772, 52, { stroke: C3, sw: 1.5, rx: 6 }));
    g.push(ctxt(400, 316, '∃x ∃y (Fx ∧ Fy ∧ x ≠ y) — 마지막 덩어리 하나가 d = e 를 막는다', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(400, 336, '한정사를 몇 개 더 붙여도 이 일은 못 한다. 두 자리가 다르다는 말은 = 만 할 수 있다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-j-same-point-twice',
        svg: svg({
            width: W, height: H,
            title: '한정사 둘이 같은 점을 두 번 집는다',
            desc: '점이 하나뿐인 모형에서 두 존재 한정사가 같은 원소를 골라 식이 참이 된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 12-3. 꽉 찬 모형 둘 — 동일성 없이는 갈라지지 않는다
 * ================================================================== */
add((() => {
    const W = 800, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '술어를 모두 꽉 채운 두 모형 — 크기만 다르다', { anchor: 'middle', cls: 'ink bold' }));

    function model(px0, title, sub, pts) {
        const out = [panel(px0, 46, 372, 214, title, sub)];
        out.push(box(px0 + 40, 110, 292, 118, { stroke: CG, sw: 1.4, rx: 10, dash: '6 4' }));
        out.push(ctxt(px0 + 322, 218, 'D', { anchor: 'end', col: CK, size: 'sm' }));
        out.push(ctxt(px0 + 186, 128, '모든 술어의 해석을 꽉 채웠다', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
        if (pts.length === 2) {
            const [x1, x2] = [px0 + pts[0][0] + 13, px0 + pts[1][0] - 13];
            out.push(`<path fill="none" stroke="${C1}" stroke-width="1.7" marker-start="url(#ar1)" marker-end="url(#ar1)" d="M${r2(x1)} 182 L${r2(x2)} 182"/>`);
        }
        pts.forEach(([x, name]) => {
            out.push(loop(px0 + x, 178, 13, { col: C1, marker: 'ar1', width: 1.7 }));
            out.push(pt(px0 + x, 182, name, { below: 24 }));
        });
        return out.join('');
    }

    g.push(model(14, '작은 모형', 'D = { 1 },  모든 상수는 1 을 가리킨다', [[186, '1']]));
    g.push(model(414, '큰 모형', 'D = { 1, 2 },  모든 상수는 1 을 가리킨다', [[126, '1'], [246, '2']]));

    g.push(box(14, 274, 772, 42, { stroke: CG, sw: 1.2, rx: 6 }));
    g.push(ctxt(400, 300, '= 가 없는 언어에서는 원자식이 둘 다 전부 참이므로, 모든 문장의 값이 두 모형에서 똑같다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    g.push(box(14, 324, 772, 42, { stroke: C2, sw: 1.5, rx: 6 }));
    g.push(ctxt(400, 350, '∃x ∃y  x ≠ y  —  이 한 문장이 오른쪽에서 참, 왼쪽에서 거짓이 되어 둘을 가른다', { anchor: 'middle', col: C2, bold: true }));

    return {
        name: 'log-j-two-full-models',
        svg: svg({
            width: W, height: H,
            title: '꽉 찬 모형 둘을 가르는 문장',
            desc: '크기 1 과 크기 2 의 꽉 찬 모형은 동일성 없는 문장으로 구별되지 않고 x 와 y 가 다르다는 문장 하나로 구별된다',
            body: g.join(''),
        }),
    };
})());

export default figures;
