/**
 * 논리학 4장(논증의 형태를 드러내기)의 그림.
 *
 * 이름은 모두 `log-b-` 로 시작한다(4장 담당에게 배정된 접두어).
 * figure 요소가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드로 적는다: ¬ ∧ ∨ → ↔ ⊥ ∀ ∃.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 장의 주제는 ‘옮기기’다. 그래서 그림도 좌표 곡선이 아니라
 * 한국어 문장이 기호로 바뀌는 자리에서 무엇이 붙고 무엇이 떨어지는지를 보인다.
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
 * 소도구. lib 의 px()/txt() 는 색을 CSS 클래스로 넘기는데 그 클래스가
 * SVG 안에 없는 경우가 있어 선이 사라진다. 색을 직접 써 넣는다.
 * ------------------------------------------------------------------ */

/** 텍스트. fill 과 크기를 직접 준다. */
function t(x, y, s, { anchor = 'start', fill = CI, size = 13, weight } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${fill}" font-size="${size}"`
        + `${weight ? ` font-weight="${weight}"` : ''}>${esc(s)}</text>`;
}

/** 화살촉 있는 직선. */
function arw(x1, y1, x2, y2, { stroke = CK, width = 1.6, marker = 'ark', dash } = {}) {
    return `<path d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}" fill="none" stroke="${stroke}"`
        + ` stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화살촉 없는 꺾은선. */
function ln(pts, { stroke = CK, width = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${width}"`
        + ` stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, width = 1.3, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${width}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const circ = (x, y, r, { fill = 'none', stroke = CK, width = 1.6 } = {}) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="${fill}" stroke="${stroke}" stroke-width="${width}"/>`;

/* ------------------------------------------------------------------ *
 * 1. 한국어 문장을 조각내면 무엇이 상항이고 무엇이 원자인가
 * ------------------------------------------------------------------ */
add((() => {
    const W = 720;
    const H = 292;
    const g = [];

    // 조각: [글자, 논리 상항인가, 그 조각이 되는 기호]
    const parts = [
        ['비가 오', false, 'P'],
        ['지 않', true, '¬'],
        ['으면', true, '→'],
        ['길이 마르', false, 'Q'],
        ['거나', true, '∨'],
        ['바람이 분다', false, 'R'],
    ];
    const wOf = s => s.length * 15 + 22;
    const total = parts.reduce((a, p) => a + wOf(p[0]), 0) + (parts.length - 1) * 10;
    let x = (W - total) / 2;

    g.push(t(W / 2, 28, '한국어에서는 논리 상항이 따로 선 낱말이 아니라 어미로 붙어 있다',
        { anchor: 'middle', fill: CI, weight: 600 }));

    const centers = [];
    for (const [word, isConst, sym] of parts) {
        const w = wOf(word);
        const col = isConst ? C2 : C1;
        g.push(box(x, 48, w, 37, { stroke: col, width: isConst ? 2.2 : 1.4, fill: col, op: isConst ? 0.16 : 0.06 }));
        g.push(t(x + w / 2, 72, word, { anchor: 'middle', fill: CI }));
        g.push(t(x + w / 2, 102, isConst ? '논리 상항' : '원자가 될 조각',
            { anchor: 'middle', fill: col, size: 11 }));
        g.push(arw(x + w / 2, 110, x + w / 2, 136, { stroke: col, marker: isConst ? 'ar2' : 'ar1', width: 1.4 }));
        g.push(t(x + w / 2, 160, sym, { anchor: 'middle', fill: col, size: 21, weight: 600 }));
        centers.push(x + w / 2);
        x += w + 10;
    }

    g.push(ln([[40, 182], [W - 40, 182]], { stroke: CG, width: 1 }));
    g.push(t(W / 2, 204, '낱말 차례대로 읽으면 P ¬ → Q ∨ R 이다. 기호로 적으려면 차례를 다시 잡아야 한다',
        { anchor: 'middle', fill: CK, size: 12 }));
    g.push(t(W / 2, 240, '¬P → (Q ∨ R)', { anchor: 'middle', fill: CI, size: 23, weight: 600 }));
    g.push(t(W / 2, 268, '괄호는 원문에 없다. 옮기는 사람이 넣는 것이다',
        { anchor: 'middle', fill: CK, size: 12 }));

    return {
        name: 'log-b-constant-split',
        svg: svg({
            width: W, height: H,
            title: '문장을 조각내어 논리 상항과 원자로 가른다',
            desc: '‘비가 오지 않으면 길이 마르거나 바람이 분다’ 를 여섯 조각으로 잘라 셋은 기호로, 셋은 원자 글자로 바꾸는 그림',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 포함적 ‘또는’ 과 배타적 ‘또는’ 은 네 경우 중 한 칸에서만 갈린다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 700;
    const H = 366;
    const g = [];

    const hx = 20;
    const hw = 150;
    const cw = 250;
    const c1 = hx + hw;
    const c2 = c1 + cw;
    const hy = 54;
    const hh = 40;
    const rh = 100;
    const r1 = hy + hh;
    const r2y = r1 + rh;

    g.push(t(W / 2, 28, '‘커피 또는 차를 마셨다’ 를 참으로 받아들이는 칸',
        { anchor: 'middle', fill: CI, weight: 600 }));

    // 머리
    g.push(t(c1 + cw / 2, hy + 26, '차를 마셨다', { anchor: 'middle', fill: CK, size: 12 }));
    g.push(t(c2 + cw / 2, hy + 26, '차를 마시지 않았다', { anchor: 'middle', fill: CK, size: 12 }));
    g.push(t(hx + hw - 10, r1 + rh / 2 + 4, '커피를 마셨다', { anchor: 'end', fill: CK, size: 12 }));
    g.push(t(hx + hw - 10, r2y + rh / 2 + 4, '커피를 마시지 않았다', { anchor: 'end', fill: CK, size: 12 }));

    const cell = (cx, cy, lines, kind) => {
        const col = kind === 'split' ? C2 : kind === 'both' ? C3 : CK;
        const out = [box(cx + 6, cy + 6, cw - 12, rh - 12, {
            stroke: col, width: kind === 'split' ? 2.4 : 1.3,
            fill: col, op: kind === 'split' ? 0.16 : kind === 'both' ? 0.10 : 0.05,
        })];
        lines.forEach((s, i) => {
            out.push(t(cx + cw / 2, cy + 34 + i * 20, s, {
                anchor: 'middle', fill: i === 0 ? CI : col, size: i === 0 ? 13 : 12,
                weight: i === 0 ? 600 : undefined,
            }));
        });
        return out.join('');
    };

    g.push(cell(c1, r1, ['둘 다 마셨다', '포함적 읽기 — 참', '배타적 읽기 — 거짓'], 'split'));
    g.push(cell(c2, r1, ['커피만 마셨다', '두 읽기 모두 참'], 'both'));
    g.push(cell(c1, r2y, ['차만 마셨다', '두 읽기 모두 참'], 'both'));
    g.push(cell(c2, r2y, ['아무것도 안 마셨다', '두 읽기 모두 거짓'], 'none'));

    g.push(ln([[20, 312], [W - 20, 312]], { stroke: CG, width: 1 }));
    g.push(t(20, 334, '네 칸 중 갈리는 칸은 왼쪽 위 하나뿐이다. 그러니 판별 물음도 하나다 — 둘 다일 때도 참인가.',
        { fill: CI, size: 12 }));
    g.push(t(20, 354, '논리학은 ∨ 를 포함적 쪽으로 정한다. 배타로 읽어야 하면 (P ∨ Q) ∧ ¬(P ∧ Q) 로 조립해 적는다.',
        { fill: CK, size: 12 }));

    return {
        name: 'log-b-or-cases',
        svg: svg({
            width: W, height: H,
            title: '포함적 또는와 배타적 또는가 갈리는 칸은 하나뿐이다',
            desc: '커피와 차를 마셨는가로 만든 네 칸 격자. 둘 다 마신 칸에서만 두 읽기의 판정이 다르다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 괄호가 없으면 같은 글자열이 두 나무로 갈라진다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 720;
    const H = 352;
    const g = [];
    const R = 17;

    g.push(t(W / 2, 28, '괄호가 없는 P ∧ Q ∨ R 은 서로 다른 두 문장으로 갈라진다',
        { anchor: 'middle', fill: CI, weight: 600 }));

    /** 마디 하나. main 이면 주연결자 표시를 덧붙인다. */
    const node = (x, y, s, { main = false } = {}) => {
        const col = main ? C2 : C1;
        return circ(x, y, R, { fill: col, stroke: col, width: 0 })
            + t(x, y + 5, s, { anchor: 'middle', fill: '#ffffff', size: 15, weight: 600 })
            + (main ? circ(x, y, R + 5, { stroke: C2, width: 2 }) : '');
    };
    const leaf = (x, y, s) => circ(x, y, R, { fill: CG, stroke: CK, width: 1.2 })
        + t(x, y + 5, s, { anchor: 'middle', fill: CI, size: 14 });

    const tree = (ox, title, rootSym, leftIsLeaf, syms) => {
        const out = [];
        const rx = ox;
        const ry = 108;
        out.push(t(ox, 66, title, { anchor: 'middle', fill: CI, size: 14, weight: 600 }));
        const lx = ox - 74;
        const rxx = ox + 74;
        const cy = 182;
        out.push(ln([[rx, ry + R], [lx, cy - R]], { stroke: CK, width: 1.4 }));
        out.push(ln([[rx, ry + R], [rxx, cy - R]], { stroke: CK, width: 1.4 }));
        out.push(node(rx, ry, rootSym, { main: true }));
        out.push(t(rx + R + 12, ry - 8, '주연결자', { fill: C2, size: 11 }));
        // 아래 단
        const bx = leftIsLeaf ? rxx : lx;
        const gy = 256;
        out.push(ln([[bx, cy + R], [bx - 44, gy - R]], { stroke: CK, width: 1.4 }));
        out.push(ln([[bx, cy + R], [bx + 44, gy - R]], { stroke: CK, width: 1.4 }));
        out.push(leftIsLeaf ? leaf(lx, cy, syms[0]) : leaf(rxx, cy, syms[0]));
        out.push(node(bx, cy, syms[1]));
        out.push(leaf(bx - 44, gy, syms[2]));
        out.push(leaf(bx + 44, gy, syms[3]));
        return out.join('');
    };

    g.push(tree(180, '읽기 ①   P ∧ (Q ∨ R)', '∧', true, ['P', '∨', 'Q', 'R']));
    g.push(tree(540, '읽기 ②   (P ∧ Q) ∨ R', '∨', false, ['R', '∧', 'P', 'Q']));
    g.push(ln([[W / 2, 60], [W / 2, 290]], { stroke: CG, width: 1, dash: '5 4' }));

    g.push(ln([[20, 302], [W - 20, 302]], { stroke: CG, width: 1 }));
    g.push(t(20, 324, 'P 가 거짓, R 이 참인 상황에서 ① 은 거짓이고 ② 는 참이다. 답이 갈리니 다른 문장이다.',
        { fill: CI, size: 12 }));
    g.push(t(20, 344, '꼭대기에 선 연결자를 주연결자라 한다. ① 은 논리곱 문장, ② 는 논리합 문장이다.',
        { fill: CK, size: 12 }));

    return {
        name: 'log-b-two-readings',
        svg: svg({
            width: W, height: H,
            title: '괄호 없는 글자열은 두 나무로 갈라진다',
            desc: 'P ∧ Q ∨ R 이 ∧ 를 꼭대기로 하는 나무와 ∨ 를 꼭대기로 하는 나무로 갈라지고, 꼭대기 연결자가 주연결자라는 그림',
            body: g.join(''),
        }),
    };
})());

export default figures;
