/**
 * 논리학 14장(완전성)의 그림.
 *
 * 이름은 모두 `log-l-` 로 시작한다(14장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-l-*.d2` 에 있고, 여기에는
 * 자리를 손으로 잡아야 하는 둘만 둔다 — 극대 일관 집합이 자라나는 계단과,
 * 진리 보조정리에서 두 사다리의 가로대가 짝을 이루는 그림.
 *
 * SVG 안에는 수식을 쓸 수 없다(<img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ∈ ∉ ⊆ ⟺ 로 직접 적는다.
 * lib 의 esc 가 `~` 를 아래첨자로 먹으므로 라벨에 물결표를 그대로 쓰지 않는다.
 * 큰따옴표와 HTML 엔티티도 쓸 수 없으므로 ‘ ’ 를 쓴다.
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

/* lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가 SVG 안에 없어 선이 사라진다.
 * 그래서 색을 직접 넣는 소도구를 따로 둔다. */
function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.7, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(x1, y1, x2, y2, { stroke = CK, sw = 1.4, dash } = {}) {
    return `<path d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${size === 'xs' ? ' font-size="10"' : ''}`
        + `${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/* ================================================================== *
 * 14-1. 린덴바움 — 일관적인 Γ 를 계단처럼 키운다
 * ================================================================== */
add((() => {
    const W = 900, H = 452;
    const b = [];

    b.push(ctxt(W / 2, 28, '일관적인 Γ 를 극대 일관 집합 Γ* 로 키운다 — 린덴바움 보조정리', { anchor: 'middle', bold: true }));
    b.push(ctxt(W / 2, 52, '5장이 준 것: 적형식 전체를 한 줄로 세울 수 있다.   φ~1 = P,   φ~2 = Q,   φ~3 = ¬P,   φ~4 = P ∧ Q,   …', { anchor: 'middle', col: CK, size: 'sm' }));

    const X0 = 118, SEG = 72, RY = 132, DY = 44;
    const TX = 428;

    b.push(ctxt(X0, 96, '자라나는 집합 — 시작은 Γ = { P → Q }', { col: CI, size: 'sm', bold: true }));
    b.push(ctxt(TX, 96, '그 단계에서 묻는 것 하나', { col: CI, size: 'sm', bold: true }));

    // 칸 하나를 그린다.
    const cell = (k, y, label, { col = CK, fill = 'none', op = 0, dash } = {}) =>
        box(X0 + SEG * k, y - 17, SEG, 27, { stroke: col, fill, op, dash })
        + ctxt(X0 + SEG * k + SEG / 2, y + 2, label, { anchor: 'middle', col: CI, size: 'sm' });

    const rows = [
        { name: 'Γ~0', segs: 0, add: null, skip: null, note: '아직 아무것도 묻지 않았다. 주어진 집합 그대로다' },
        { name: 'Γ~1', segs: 1, add: 'P', skip: null, note: 'φ~1 = P 를 넣어도 일관적인가 — 그렇다. 넣는다' },
        { name: 'Γ~2', segs: 2, add: 'Q', skip: null, note: 'φ~2 = Q 를 넣어도 일관적인가 — 그렇다. 넣는다' },
        { name: 'Γ~3', segs: 2, add: null, skip: '¬P', note: 'φ~3 = ¬P 는 어떤가 — Γ~2 에서 P 가 도출된다. ⊥ 가 나오므로 건너뛴다' },
        { name: 'Γ~4', segs: 3, add: 'P ∧ Q', skip: null, note: 'φ~4 = P ∧ Q 를 넣어도 일관적인가 — 그렇다. 넣는다' },
    ];

    const base = ['P → Q', 'P', 'Q', 'P ∧ Q'];
    rows.forEach((r, i) => {
        const y = RY + i * DY;
        b.push(ctxt(X0 - 12, y + 2, r.name, { anchor: 'end', col: CI, bold: true, size: 'sm' }));
        for (let k = 0; k <= r.segs; k += 1) {
            const isNew = r.add !== null && k === r.segs;
            b.push(cell(k, y, base[k], isNew ? { col: C1, fill: C1, op: 0.12 } : {}));
        }
        if (r.skip) {
            b.push(cell(r.segs + 1, y, r.skip + '  ✗', { col: C2, dash: '4 3' }));
        }
        b.push(ctxt(TX, y + 2, r.note, { col: r.skip ? C2 : CK, size: 'sm' }));
    });

    // 합집합
    const yU = RY + 5 * DY + 14;
    b.push(ctxt(X0 - 12, yU + 2, 'Γ*', { anchor: 'end', col: CI, bold: true }));
    for (let k = 0; k < 4; k += 1) b.push(cell(k, yU, base[k], { col: C3 }));
    b.push(ctxt(X0 + SEG * 4 + 4, yU + 2, '⋯', { col: CK, size: 'sm' }));
    b.push(ctxt(TX, yU - 5, '모든 단계를 합친다. Γ~0 ⊆ Γ~1 ⊆ Γ~2 ⊆ ⋯ 이므로', { col: C3, size: 'sm' }));
    b.push(ctxt(TX, yU + 11, '합집합 Γ* 는 이 사슬의 끝이다', { col: C3, size: 'sm' }));

    b.push(ln(40, H - 58, W - 40, H - 58, { stroke: CG, sw: 1 }));
    b.push(ctxt(40, H - 38, '합집합이 일관적인 까닭 — Γ* ⊢ ⊥ 이라면 그 도출이 유한하므로(7장) 인용한 가정도 유한하고, 그 유한 개는 어떤 한 단계 Γ~n 에 다 들어 있다.', { col: CK, size: 'sm' }));
    b.push(ctxt(40, H - 20, '그러면 Γ~n ⊢ ⊥ 이 되어 그 단계의 일관성에 어긋난다.   건너뛴 식도 버려지지 않는다 — φ 를 못 넣은 것은 ¬φ 가 이미 도출된다는 뜻이다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-l-lindenbaum-chain',
        svg: svg({
            width: W, height: H,
            title: '린덴바움 보조정리 — 일관적인 집합을 극대 일관 집합으로 키우는 계단',
            desc: '적형식을 번호 순으로 하나씩 물어 넣거나 건너뛴다. 계단이 오른쪽으로만 자라고 합집합이 그 끝이다.',
            body: b.join(''),
        }),
    };
})());

/* ================================================================== *
 * 14-2. 진리 보조정리 — 두 사다리의 가로대가 짝을 이룬다
 * ================================================================== */
add((() => {
    const W = 900, H = 496;
    const b = [];

    b.push(ctxt(W / 2, 28, '진리 보조정리 — 두 사다리의 가로대가 하나씩 짝을 이룬다', { anchor: 'middle', bold: true }));
    b.push(ctxt(W / 2, 52, 'Γ* 에서 배당을 읽어낸다:  v(P) = T 인 것은 P ∈ Γ* 일 때.  그러면 모든 적형식에서 값과 소속이 일치한다', { anchor: 'middle', col: CK, size: 'sm' }));

    const LX = 66, LW = 372, RX = 470, RW = 396, TY = 118, DY = 46;
    b.push(ctxt(LX + LW / 2, 96, '6장 — 확장 배당의 조항', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    b.push(ctxt(RX + RW / 2, 96, '앞 절 — 극대 일관 집합 Γ* 의 성질', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    const rungs = [
        ['P 의 값이 T 다', 'P ∈ Γ* 다 — 배당을 그렇게 정했다', true],
        ['⊥ 의 값은 어느 배당에서나 F 다', '⊥ ∉ Γ* 다 — 들었다면 비일관적이다', true],
        ['¬φ 가 T ⟺ φ 가 F', '¬φ ∈ Γ* ⟺ φ ∉ Γ*', false],
        ['φ ∧ ψ 가 T ⟺ 둘 다 T', 'φ ∧ ψ ∈ Γ* ⟺ 둘 다 Γ* 에 있다', false],
        ['φ ∨ ψ 가 T ⟺ 적어도 하나가 T', 'φ ∨ ψ ∈ Γ* ⟺ 적어도 하나가 Γ* 에 있다', false],
        ['φ → ψ 가 T ⟺ φ 가 F 이거나 ψ 가 T', 'φ → ψ ∈ Γ* ⟺ φ ∉ Γ* 이거나 ψ ∈ Γ*', false],
        ['φ ↔ ψ 가 T ⟺ 두 값이 같다', 'φ ↔ ψ ∈ Γ* ⟺ 둘 다 있거나 둘 다 없다', false],
    ];

    rungs.forEach(([l, r, basis], i) => {
        const y = TY + i * DY;
        b.push(box(LX, y - 17, LW, 32, { stroke: basis ? CG : C1, sw: basis ? 1 : 1.3, fill: C1, op: basis ? 0 : 0.07 }));
        b.push(ctxt(LX + 12, y + 4, l, { col: CI, size: 'sm' }));
        b.push(box(RX, y - 17, RW, 32, { stroke: basis ? CG : C2, sw: basis ? 1 : 1.3, fill: C2, op: basis ? 0 : 0.07 }));
        b.push(ctxt(RX + 12, y + 4, r, { col: CI, size: 'sm' }));
        b.push(ln(LX + LW, y - 1, RX, y - 1, { stroke: CK, sw: 1.2, dash: '3 3' }));
        b.push(ctxt((LX + LW + RX) / 2, y + 3, '⟺', { anchor: 'middle', col: CK, size: 'sm' }));
    });

    // 귀납 방향
    const yTop = TY - 20, yBot = TY + 6 * DY + 18;
    b.push(arw(38, yBot, 38, yTop, { col: C3, width: 1.8 }));
    b.push(ctxt(38, yTop - 8, '귀납', { anchor: 'middle', col: C3, size: 'xs' }));
    b.push(ctxt(38, yBot + 14, '기저', { anchor: 'middle', col: C3, size: 'xs' }));

    b.push(ctxt(LX, TY + 6 * DY + 48, '아래 두 칸은 정의와 일관성으로 직접 맞춘다. 위 다섯 칸은 짝이 맞으므로 귀납 가정에서 저절로 따라온다.', { col: CK, size: 'sm' }));
    b.push(ctxt(LX, TY + 6 * DY + 66, '왼쪽 사다리는 6장이 세웠고 오른쪽 사다리는 규칙 열일곱 개가 세웠는데 가로대가 하나도 어긋나지 않는다 — 이것이 완전성의 심장이다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-l-truth-lemma-climb',
        svg: svg({
            width: W, height: H,
            title: '진리 보조정리 — 진리표 조항과 극대 일관 집합의 성질이 한 줄씩 짝을 이룬다',
            desc: '왼쪽 사다리는 의미론의 조항, 오른쪽 사다리는 증명 체계가 준 소속 조건. 가로대가 하나씩 대응한다.',
            body: b.join(''),
        }),
    };
})());

export default figures;
