/**
 * 논리학 19장(고전 논리 밖)의 그림.
 *
 * 이름은 모두 `log-q-` 로 시작한다(19장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-q-*.d2` 에 있고, 여기에는
 * 자리를 손으로 잡아야 하는 것 둘만 둔다 — 크립키 반례 모형 둘을 나란히
 * 놓은 그림과, 클레이니 세 값 진리표 넷을 붙여 놓은 격자.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ □ ◇ 로 직접 적는다. 아래첨자는
 * lib 의 esc 가 처리하는 `w~0` 표기를 쓰고, 그래서 라벨에 다른 물결표를 쓰지
 * 않는다. 큰따옴표와 HTML 엔티티도 쓸 수 없으므로 ‘ ’ 를 쓴다.
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

function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.7, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function dot(cx, cy, r, { fill = C1, stroke, sw = 1.6 } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="${sw}"` : ''}/>`;
}

/** 색을 직접 넣는 글자. lib 의 txt 는 클래스만 받는다. */
function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${size === 'lg' ? ' font-size="16"' : ''}`
        + `${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 21, title, { anchor: 'middle', col: CI, bold: true, size: 'sm' }) : '')
        + (sub ? ctxt(x + w / 2, y + 38, sub, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/* ================================================================== *
 * 19-1. 크립키 반례 모형 둘
 *
 * 왼쪽 — 세계 둘. 배중률과 이중부정 제거가 함께 떨어진다.
 * 오른쪽 — 갈래 하나. 드모르간의 막힌 방향이 떨어진다.
 * ================================================================== */
add((() => {
    const W = 900, H = 470;
    const g = [];
    g.push(ctxt(W / 2, 26, '반례 모형 둘 — 세계를 ‘지금까지 증명해 놓은 것’ 으로 읽는다', { anchor: 'middle', col: CI, bold: true }));

    /* ---------------- 왼쪽 패널 ---------------- */
    g.push(panel(24, 44, 400, 320, '세계 둘 — 배중률과 이중부정 제거가 함께 떨어진다', 'w~0 ≤ w~1 이고 P 는 w~1 에서만 성립한다'));

    const L = { x0: 150, y0: 300, y1: 160 };
    g.push(arw(L.x0, L.y0 - 18, L.x0, L.y1 + 20, { col: CK, width: 1.8 }));
    g.push(ctxt(L.x0 + 12, (L.y0 + L.y1) / 2 - 2, '더 알게 된다', { col: CK, size: 'sm' }));

    g.push(dot(L.x0, L.y1, 9, { fill: C2 }));
    g.push(ctxt(L.x0 - 18, L.y1 + 5, 'w~1', { anchor: 'end', col: CI, bold: true }));
    g.push(ctxt(L.x0 + 24, L.y1 - 12, 'P 를 강제한다', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(L.x0 + 24, L.y1 + 6, '그러므로 ¬P 는 강제하지 않는다', { col: CK, size: 'sm' }));

    g.push(dot(L.x0, L.y0, 9, { fill: 'none', stroke: C1, sw: 2 }));
    g.push(ctxt(L.x0 - 18, L.y0 + 5, 'w~0', { anchor: 'end', col: CI, bold: true }));
    g.push(ctxt(L.x0 + 24, L.y0 - 4, 'P 를 강제하지 않는다', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(L.x0 + 24, L.y0 + 14, '¬P 도 아니다 — 위에서 P 가 나온다', { col: CK, size: 'sm' }));

    const lrows = [
        ['w~0 ⊮ P ∨ ¬P', '양쪽 다 아니다', C1],
        ['w~0 ⊩ ¬¬P', '¬P 를 강제하는 세계가 없다', C3],
        ['w~0 ⊮ ¬¬P → P', '자기 자신이 반례다', C1],
    ];
    lrows.forEach(([a, b, col], i) => {
        g.push(ctxt(44, 392 + i * 22, a, { col, bold: true, size: 'sm' }));
        g.push(ctxt(180, 392 + i * 22, b, { col: CK, size: 'sm' }));
    });

    /* ---------------- 오른쪽 패널 ---------------- */
    g.push(panel(452, 44, 424, 320, '갈래 하나 — 드모르간의 막힌 방향이 떨어진다', 'w~1 에서만 P, w~2 에서만 Q 가 성립한다'));

    const R = { xc: 620, y0: 300, y1: 160, dx: 90 };
    g.push(arw(R.xc - 8, R.y0 - 16, R.xc - R.dx + 6, R.y1 + 18, { col: CK, width: 1.8 }));
    g.push(arw(R.xc + 8, R.y0 - 16, R.xc + R.dx - 6, R.y1 + 18, { col: CK, width: 1.8 }));

    g.push(dot(R.xc - R.dx, R.y1, 9, { fill: C2 }));
    g.push(ctxt(R.xc - R.dx, R.y1 - 18, 'w~1 — P 만', { anchor: 'middle', col: C2, bold: true, size: 'sm' }));
    g.push(dot(R.xc + R.dx, R.y1, 9, { fill: C3 }));
    g.push(ctxt(R.xc + R.dx, R.y1 - 18, 'w~2 — Q 만', { anchor: 'middle', col: C3, bold: true, size: 'sm' }));

    g.push(dot(R.xc, R.y0, 9, { fill: 'none', stroke: C1, sw: 2 }));
    g.push(ctxt(R.xc, R.y0 + 24, 'w~0 — 아무 원자도 강제하지 않는다', { anchor: 'middle', col: C1, bold: true, size: 'sm' }));

    const rrows = [
        ['w~0 ⊩ ¬(P ∧ Q)', 'P ∧ Q 를 강제하는 세계가 하나도 없다', C3],
        ['w~0 ⊮ ¬P', 'w~1 이 P 를 강제한다', C1],
        ['w~0 ⊮ ¬Q', 'w~2 가 Q 를 강제한다', C1],
    ];
    rrows.forEach(([a, b, col], i) => {
        g.push(ctxt(472, 392 + i * 22, a, { col, bold: true, size: 'sm' }));
        g.push(ctxt(618, 392 + i * 22, b, { col: CK, size: 'sm' }));
    });

    /* ---------------- 아래 한 줄 ---------------- */
    g.push(ctxt(24, 456, '→ 와 ¬ 의 조항이 ‘더 아는 모든 세계’ 를 내다보기 때문에 위쪽 세계가 아래쪽 세계의 부정을 막는다. 그것이 두 모형이 하는 일 전부다.',
        { col: CK, size: 'sm' }));

    return {
        name: 'log-q-kripke-countermodel',
        svg: svg({
            width: W, height: H,
            title: '직관주의 크립키 반례 모형 둘',
            desc: '세계 둘짜리 모형이 배중률과 이중부정 제거를 떨어뜨리고, 갈래가 있는 세 세계짜리 모형이 드모르간의 한 방향을 떨어뜨린다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 19-2. 클레이니 세 값 진리표 넷
 *
 * 색이 다른 칸이 ‘미정을 받고도 값이 정해지는 자리’ 다.
 * ================================================================== */
add((() => {
    const W = 900, H = 430;
    const g = [];
    g.push(ctxt(W / 2, 26, '클레이니의 세 값 — 미정 자리에 T 를 넣든 F 를 넣든 결과가 같으면 그 값을 쓴다', { anchor: 'middle', col: CI, bold: true }));

    const V = ['T', 'U', 'F'];
    const cw = 42, ch = 30;

    /** 이항 연산 표 하나. f(a, b) 가 값을 준다. */
    function table(ox, oy, title, f) {
        const out = [];
        out.push(ctxt(ox + (cw * 4) / 2, oy - 12, title, { anchor: 'middle', col: CI, bold: true, size: 'sm' }));
        // 머리줄
        out.push(ctxt(ox + cw / 2, oy + 20, '', {}));
        V.forEach((b, j) => {
            out.push(ctxt(ox + cw * (j + 1.5), oy + 20, b, { anchor: 'middle', col: CK, bold: true, size: 'sm' }));
        });
        V.forEach((a, i) => {
            const y = oy + ch * (i + 1);
            out.push(ctxt(ox + cw / 2, y + 20, a, { anchor: 'middle', col: CK, bold: true, size: 'sm' }));
            V.forEach((b, j) => {
                const x = ox + cw * (j + 1);
                const v = f(a, b);
                // 입력에 U 가 있는데도 값이 정해진 칸을 표시한다.
                const hasU = a === 'U' || b === 'U';
                const decided = hasU && v !== 'U';
                out.push(box(x, y + 4, cw, ch, {
                    stroke: decided ? C2 : CG, sw: decided ? 1.6 : 1,
                    fill: decided ? C2 : 'none', op: decided ? 0.14 : 0, rx: 2,
                }));
                out.push(ctxt(x + cw / 2, y + 24, v, {
                    anchor: 'middle', col: decided ? C2 : (hasU ? CK : CI), bold: decided,
                }));
            });
        });
        return out.join('');
    }

    const AND = (a, b) => (a === 'F' || b === 'F') ? 'F' : (a === 'U' || b === 'U') ? 'U' : 'T';
    const OR = (a, b) => (a === 'T' || b === 'T') ? 'T' : (a === 'U' || b === 'U') ? 'U' : 'F';
    const NOT = a => a === 'T' ? 'F' : a === 'F' ? 'T' : 'U';
    const IMP = (a, b) => OR(NOT(a), b);

    g.push(table(60, 76, 'φ ∧ ψ', AND));
    g.push(table(268, 76, 'φ ∨ ψ', OR));
    g.push(table(476, 76, 'φ → ψ', IMP));

    /* ---- ¬ 표는 한 줄짜리라 따로 ---- */
    g.push(ctxt(760, 64, '¬φ', { anchor: 'middle', col: CI, bold: true, size: 'sm' }));
    V.forEach((a, i) => {
        const y = 76 + ch * (i + 1);
        g.push(ctxt(736, y + 24, a, { anchor: 'middle', col: CK, bold: true, size: 'sm' }));
        g.push(box(756, y + 4, cw, ch, { stroke: CG, sw: 1, rx: 2 }));
        g.push(ctxt(756 + cw / 2, y + 24, NOT(a), { anchor: 'middle', col: a === 'U' ? CK : CI }));
    });

    /* ---- 읽는 법 ---- */
    g.push(box(60, 220, 780, 60, { stroke: C2, sw: 1.4, rx: 5, fill: C2, op: 0.08 }));
    g.push(ctxt(76, 243, '색이 든 칸 — 입력에 U 가 있는데도 값이 정해진 자리다.', { col: C2, bold: true, size: 'sm' }));
    g.push(ctxt(76, 263, 'F ∧ U = F 인 것은 F 한쪽이 이미 결과를 정해 버렸기 때문이고, T ∨ U = T 도 같은 까닭이다. 프로그램의 단축 평가와 같다.', { col: CK, size: 'sm' }));

    /* ---- 배중률이 떨어지는 줄 ---- */
    g.push(box(60, 296, 380, 108, { stroke: CG, sw: 1, rx: 5 }));
    g.push(ctxt(76, 320, '배중률에 U 를 넣어 본다', { col: CI, bold: true, size: 'sm' }));
    g.push(ctxt(76, 344, 'P = U 이면 ¬P = U 이고 U ∨ U = U 다.', { col: CK, size: 'sm' }));
    g.push(ctxt(76, 364, '지정값은 T 하나뿐이므로 P ∨ ¬P 는 항진명제가 아니다.', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(76, 388, '첫 절이 예고한 ‘세 번째 경우’ 가 정확히 이 줄이다.', { col: CK, size: 'sm' }));

    g.push(box(460, 296, 380, 108, { stroke: CG, sw: 1, rx: 5 }));
    g.push(ctxt(476, 320, '항진명제가 하나도 없다', { col: CI, bold: true, size: 'sm' }));
    g.push(ctxt(476, 344, '문장문자에 전부 U 를 주면 표 어디를 지나도 U 가 나온다.', { col: CK, size: 'sm' }));
    g.push(ctxt(476, 364, '그러므로 문장문자와 연결자만으로 만든 식은 모두 U 를 받는다.', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(476, 388, '값어치 있는 것은 정리 목록이 아니라 타당한 논증의 목록이다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-q-kleene-tables',
        svg: svg({
            width: W, height: H,
            title: '클레이니 세 값 진리표',
            desc: '¬ ∧ ∨ → 의 세 값 진리표. 미정을 입력으로 받고도 값이 정해지는 칸을 표시했고, 배중률이 미정 값을 받아 떨어지는 과정을 아래에 적었다',
            body: g.join(''),
        }),
    };
})());

export default figures;
