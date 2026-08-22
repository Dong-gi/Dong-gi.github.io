/**
 * 논리학 7장(자연연역 — 명제논리)의 그림 가운데 좌표를 손으로 잡아야 하는 것.
 *
 * 이름은 모두 `log-e-` 로 시작한다(7장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-e-*.d2` 에 있고, 여기에는
 * 피치 스타일 도출표처럼 줄과 세로 범위선의 위치를 정확히 맞춰야 하는 것만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ 로 직접 적는다. `~` 는 lib 의 esc 가
 * 아래첨자로 바꿔 버리므로 줄 범위는 en dash 로 적는다(1–4). 큰따옴표 대신 ‘ ’.
 */
import { svg, txt, esc } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구 — lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가
 * SVG 안에 없어 선이 사라진다. 그래서 색을 직접 넣는 것들을 따로 둔다.
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.6, dash } = {}) {
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

function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 21, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 38, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/**
 * 피치 스타일 도출표 한 벌.
 *
 * rows 는 { no, depth, formula, reason, dep } 목록이다. depth 가 1 이상이면
 * 그 줄이 가정의 범위 안에 있다는 뜻이고, 범위선은 depth 별로 따로 그린다.
 * 좌표를 돌려주므로 바깥에서 특정 줄에 화살표를 달 수 있다.
 */
function fitch(x, y, rows, { colF = 66, colR = 260, colD, rowH = 30, barGap = 14 } = {}) {
    const g = [];
    const Y = i => y + i * rowH;
    // 범위선 — 같은 깊이가 이어지는 구간마다 세로선 하나와 위쪽 꺾쇠
    const maxDepth = Math.max(0, ...rows.map(r => r.depth));
    for (let d = 1; d <= maxDepth; d += 1) {
        let start = -1;
        for (let i = 0; i <= rows.length; i += 1) {
            const inside = i < rows.length && rows[i].depth >= d;
            if (inside && start < 0) start = i;
            if (!inside && start >= 0) {
                const bx = x + colF + (d - 1) * barGap;
                g.push(ln([[bx + 8, Y(start) - 15], [bx, Y(start) - 15], [bx, Y(i - 1) + 8]], { stroke: C1, sw: 1.8 }));
                start = -1;
            }
        }
    }
    rows.forEach((r, i) => {
        const yy = Y(i);
        g.push(ctxt(x + 20, yy, String(r.no), { anchor: 'end', col: CK, size: 'sm' }));
        g.push(ctxt(x + colF + r.depth * barGap + 4, yy, r.formula, { col: CI }));
        g.push(ctxt(x + colR, yy, r.reason, { col: CK, size: 'sm' }));
        if (colD != null && r.dep != null) {
            g.push(ctxt(x + colD, yy, r.dep, { col: r.dep === '없음' ? C2 : CK, size: 'sm', bold: r.dep === '없음' }));
        }
    });
    return { g: g.join(''), Y };
}

/* ================================================================== *
 * log-e-discharge — 가정의 범위와 방출
 * ================================================================== */
add((() => {
    const W = 820;
    const H = 424;
    const g = [];

    g.push(panel(14, 34, 792, 252, '도출 한 벌 — 전제 없이 (P ∧ Q) → (Q ∧ P) 를 얻는다'));

    const rows = [
        { no: 1, depth: 1, formula: 'P ∧ Q', reason: '가정', dep: '가정 1' },
        { no: 2, depth: 1, formula: 'Q', reason: '1 ∧E', dep: '가정 1' },
        { no: 3, depth: 1, formula: 'P', reason: '1 ∧E', dep: '가정 1' },
        { no: 4, depth: 1, formula: 'Q ∧ P', reason: '2, 3 ∧I', dep: '가정 1' },
        { no: 5, depth: 0, formula: '(P ∧ Q) → (Q ∧ P)', reason: '1–4 →I', dep: '없음' },
    ];
    g.push(ctxt(64, 86, '줄', { anchor: 'end', col: CK, size: 'sm', bold: true }));
    g.push(ctxt(88, 86, '논리식', { col: CK, size: 'sm', bold: true }));
    g.push(ctxt(300, 86, '근거', { col: CK, size: 'sm', bold: true }));
    g.push(ctxt(396, 86, '이 줄이 기대고 있는 가정', { col: CK, size: 'sm', bold: true }));
    g.push(ln([[36, 94], [560, 94]], { stroke: CG, sw: 1 }));
    const F = fitch(44, 122, rows, { colF: 34, colR: 256, colD: 352, rowH: 30 });
    g.push(F.g);

    // 오른쪽 주석 — 줄 세 곳을 가리킨다
    g.push(arw(556, 118, 516, 118, { col: C1 }));
    g.push(ctxt(562, 122, '가정을 연다. 여기서 범위가 시작된다', { col: CI, size: 'sm' }));
    g.push(ctxt(562, 182, '이 세 줄도 모두 가정 1 아래에 있다', { col: CK, size: 'sm' }));
    g.push(arw(556, 238, 516, 238, { col: C2, marker: 'ar2' }));
    g.push(ctxt(562, 242, '방출. 범위가 닫히고 기댄 것이 없어진다', { col: CI, size: 'sm' }));
    g.push(box(388, 226, 58, 24, { stroke: C2, sw: 1.8, rx: 4 }));

    g.push(ctxt(24, 314, '1–4 번 줄은 전부 가정 1 아래서 얻은 것이다. 그 줄들만으로는 아직 아무것도 주장하지 못한다', { col: CK, size: 'sm' }));
    g.push(ctxt(24, 338, '5 번 줄에서 가정 1 이 방출된다. 기댄 가정이 없어졌으므로 이 줄은 전제 없이 주장할 수 있다 — 그런 식이 정리다', { col: CI, size: 'sm' }));
    g.push(ctxt(24, 366, '¬I 도 같은 일을 한다. 다른 것은 범위 안에서 ⊥ 를 얻어야 한다는 것뿐이다', { col: CK, size: 'sm' }));

    g.push(ctxt(W / 2, 404, '→I 가 하는 일은 참을 만들어 내는 것이 아니라 기댄 가정 하나를 화살표 왼쪽으로 옮겨 적는 것이다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-e-discharge',
        svg: svg({
            width: W, height: H,
            title: '가정의 범위와 방출을 피치 스타일 도출표로 보인 그림',
            desc: '네 줄이 가정 하나에 기대고 있고 마지막 줄에서 그 가정이 방출되어 기댄 가정이 없어지는 과정을 줄 번호와 범위선으로 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-e-scope-error — 범위를 어기는 두 가지
 * ================================================================== */
add((() => {
    const W = 840;
    const H = 372;
    const g = [];

    /* 왼쪽 — 닫힌 범위 안의 줄을 밖에서 인용한다 */
    g.push(panel(14, 34, 400, 250, '잘못 하나 — 닫힌 범위 안의 줄을 밖에서 인용한다'));
    const A = fitch(38, 92, [
        { no: 1, depth: 1, formula: 'P', reason: '가정' },
        { no: 2, depth: 1, formula: 'P ∨ Q', reason: '1 ∨I' },
        { no: 3, depth: 0, formula: 'P → (P ∨ Q)', reason: '1–2 →I' },
        { no: 4, depth: 0, formula: 'P ∨ Q', reason: '2 R' },
    ], { colF: 30, colR: 210, rowH: 32 });
    g.push(A.g);
    g.push(box(232, 172, 76, 26, { stroke: C2, sw: 2, rx: 4 }));
    g.push(ctxt(38, 226, '4 번 줄이 2 번 줄을 인용했다. 2 번 줄은 가정 1 아래', { col: CI, size: 'sm' }));
    g.push(ctxt(38, 246, '에서만 얻은 것이고, 3 번 줄에서 범위가 닫혔다.', { col: CK, size: 'sm' }));
    g.push(ctxt(38, 266, '닫힌 범위 안의 줄은 밖에서 없는 것과 같다.', { col: CK, size: 'sm' }));

    /* 오른쪽 — 가정을 열어 놓은 채 끝낸다 */
    g.push(panel(426, 34, 400, 250, '잘못 둘 — 가정을 열어 놓은 채 끝낸다'));
    const B = fitch(450, 92, [
        { no: 1, depth: 1, formula: 'P', reason: '가정' },
        { no: 2, depth: 1, formula: 'P ∨ Q', reason: '1 ∨I' },
    ], { colF: 30, colR: 210, rowH: 32 });
    g.push(B.g);
    g.push(ctxt(462, 168, '여기서 멈추고 ⊢ P ∨ Q 라고 적는다', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(450, 226, '가정 1 이 열린 채로 남아 있다. 이 도출이 보인 것은', { col: CI, size: 'sm' }));
    g.push(ctxt(450, 246, 'P ⊢ P ∨ Q 이지 ⊢ P ∨ Q 가 아니다. 열린 가정은', { col: CK, size: 'sm' }));
    g.push(ctxt(450, 266, '전제로 세거나 방출하거나 둘 중 하나여야 한다.', { col: CK, size: 'sm' }));

    g.push(ln([[40, 306], [800, 306]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 332, '두 잘못은 하나를 놓친 결과다 — 줄 하나가 무엇에 기대고 있는지', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(W / 2, 354, '도출을 검사할 때 줄마다 물을 것은 둘이다. 근거가 규칙 모양과 맞는가, 인용한 줄이 지금 손닿는 범위에 있는가', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-e-scope-error',
        svg: svg({
            width: W, height: H,
            title: '가정의 범위를 어기는 두 가지 잘못',
            desc: '닫힌 범위 안의 줄을 밖에서 인용하는 잘못과 가정을 방출하지 않고 결론으로 삼는 잘못을 도출표 두 벌로 나란히 보인다',
            body: g.join(''),
        }),
    };
})());

export default figures;
