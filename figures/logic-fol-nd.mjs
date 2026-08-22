/**
 * 논리학 11장(자연연역 — 술어논리)의 그림 가운데 좌표를 손으로 잡아야 하는 것.
 *
 * 이름은 모두 `log-i-` 로 시작한다(11장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-i-*.d2` 에 있고, 여기에는
 * 피치 스타일 도출표처럼 줄과 세로 범위선을 정확히 맞춰야 하는 것만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ℳ 로 직접 적는다.
 * 아래첨자는 `c~1` 표기가 tspan 으로 내려간다. 줄 범위는 en dash 로 적는다(3–6).
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
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

/** 점 하나와 자기 자신으로 돌아오는 화살표(자기 고리). */
function dot(x, y, label, { col = C1 } = {}) {
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="5" fill="${col}"/>`
        + ctxt(x, y + 22, label, { anchor: 'middle', col: CI, size: 'sm' });
}

function selfLoop(x, y, { col = CK } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="1.5" marker-end="url(#ark)"`
        + ` d="M${r2(x - 6)} ${r2(y - 6)} A 15 15 0 1 1 ${r2(x + 7)} ${r2(y - 5)}"/>`;
}

/**
 * 피치 스타일 도출표 한 벌.
 *
 * rows 는 { no, depth, formula, reason } 목록이다. depth 가 1 이상이면 그 줄이
 * 가정의 범위 안에 있다는 뜻이고, 범위선은 depth 별로 따로 그린다.
 * 좌표를 돌려주므로 바깥에서 특정 줄에 화살표나 강조 테를 달 수 있다.
 */
function fitch(x, y, rows, { colF = 34, colR = 210, rowH = 28, barGap = 13 } = {}) {
    const g = [];
    const Y = i => y + i * rowH;
    const maxDepth = Math.max(0, ...rows.map(r => r.depth));
    for (let d = 1; d <= maxDepth; d += 1) {
        let start = -1;
        for (let i = 0; i <= rows.length; i += 1) {
            const inside = i < rows.length && rows[i].depth >= d;
            if (inside && start < 0) start = i;
            if (!inside && start >= 0) {
                const bx = x + colF + (d - 1) * barGap;
                g.push(ln([[bx + 8, Y(start) - 14], [bx, Y(start) - 14], [bx, Y(i - 1) + 7]], { stroke: C1, sw: 1.8 }));
                start = -1;
            }
        }
    }
    rows.forEach((r, i) => {
        const yy = Y(i);
        g.push(ctxt(x + 18, yy, String(r.no), { anchor: 'end', col: CK, size: 'sm' }));
        g.push(ctxt(x + colF + r.depth * barGap + 4, yy, r.formula, { col: CI }));
        g.push(ctxt(x + colR, yy, r.reason, { col: CK, size: 'sm' }));
    });
    return { g: g.join(''), Y };
}

/* ================================================================== *
 * log-i-forall-i-wrong — ∀I 를 이름 하나 때문에 못 쓰는 자리
 * ================================================================== */
add((() => {
    const W = 860;
    const H = 396;
    const g = [];

    /* 왼쪽 — 전제에 든 이름을 그대로 일반화한다 */
    g.push(panel(14, 30, 404, 244, '틀린 도출 — 전제에 이미 든 이름 a 를 일반화한다'));
    const A = fitch(40, 90, [
        { no: 1, depth: 0, formula: 'Fa', reason: '전제' },
        { no: 2, depth: 0, formula: '∀x Fx', reason: '1 ∀I' },
    ], { colF: 26, colR: 216, rowH: 30 });
    g.push(A.g);
    g.push(box(232, 100, 74, 26, { stroke: C2, sw: 2, rx: 4 }));
    g.push(ctxt(40, 170, '2 번 줄이 기대고 있는 열린 가정은 1 번 줄 Fa 다.', { col: CI, size: 'sm' }));
    g.push(ctxt(40, 190, '그 안에 a 가 들어 있다. a 는 아무 대상이 아니라', { col: CK, size: 'sm' }));
    g.push(ctxt(40, 210, '전제가 특별히 지목한 대상이므로 일반화할 수 없다.', { col: CK, size: 'sm' }));
    g.push(ctxt(40, 238, 'D = {1, 2}, I(a) = 1, I(F) = {1} 에서 Fa 는 참', { col: C2, size: 'sm' }));
    g.push(ctxt(40, 256, '이고 ∀x Fx 는 거짓이다 — 거짓을 증명한 셈이다', { col: C2, size: 'sm' }));

    /* 오른쪽 — 새 이름을 만들어 쓴다 */
    g.push(panel(438, 30, 404, 244, '옳은 도출 — 새 이름 c 를 만들어 쓴다'));
    const B = fitch(464, 90, [
        { no: 1, depth: 0, formula: '∀x (Fx ∧ Gx)', reason: '전제' },
        { no: 2, depth: 0, formula: 'Fc ∧ Gc', reason: '1 ∀E' },
        { no: 3, depth: 0, formula: 'Fc', reason: '2 ∧E' },
        { no: 4, depth: 0, formula: '∀x Fx', reason: '3 ∀I' },
    ], { colF: 26, colR: 216, rowH: 30 });
    g.push(B.g);
    g.push(box(656, 160, 74, 26, { stroke: C3, sw: 2, rx: 4 }));
    g.push(ctxt(464, 236, '열린 가정은 1 번 줄뿐이고 거기에 c 가 없다.', { col: CI, size: 'sm' }));
    g.push(ctxt(464, 256, 'c 에 대해 우리가 따로 아는 것이 하나도 없으므로 통과다.', { col: CK, size: 'sm' }));

    g.push(ln([[40, 300], [820, 300]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 328, '두 도출의 차이는 이름 하나다 — a 는 전제가 이미 지목한 이름이고 c 는 우리가 방금 만든 이름이다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(W / 2, 352, '∀I 가 요구하는 것은 그 줄의 이름이 아무 대상을 가리켜도 상관없어야 한다는 것이고,', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(W / 2, 372, '그것을 문법으로 확인하는 방법이 ‘열린 가정 목록에 그 이름이 없는가’ 하나다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-i-forall-i-wrong',
        svg: svg({
            width: W, height: H,
            title: '전칭 일반화가 이름 하나 때문에 막히는 자리',
            desc: '전제에 든 이름 a 를 그대로 일반화한 틀린 도출과 새 이름 c 를 만들어 쓴 옳은 도출을 나란히 놓아 변수 조건이 무엇을 확인하는지 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-i-var-conditions — 변수 조건 네 개를 한 그림으로
 * ================================================================== */
add((() => {
    const W = 900;
    const H = 500;
    const g = [];

    /* 왼쪽 — 도출 한 벌에서 살펴볼 세 곳을 표시한다 */
    g.push(panel(14, 30, 470, 310, '살펴볼 곳은 셋이다 — 도출 한 벌에 표시해 둔다'));
    const A = fitch(32, 92, [
        { no: 1, depth: 0, formula: '∃x Fx', reason: '전제' },
        { no: 2, depth: 0, formula: '∀x (Fx → Gx)', reason: '전제' },
        { no: 3, depth: 1, formula: 'Fc', reason: '가정' },
        { no: 4, depth: 1, formula: 'Fc → Gc', reason: '2 ∀E' },
        { no: 5, depth: 1, formula: 'Gc', reason: '3, 4 →E' },
        { no: 6, depth: 1, formula: '∃x Gx', reason: '5 ∃I' },
        { no: 7, depth: 0, formula: '∃x Gx', reason: '1, 3–6 ∃E' },
    ], { colF: 28, colR: 224, rowH: 29 });
    g.push(A.g);

    // 줄마다 붙는 표시 — ① 열린 가정, ② 재료가 된 ∃x φ, ③ 결론 ψ
    g.push(ctxt(190, 92, '①', { col: C1, bold: true }));
    g.push(ctxt(212, 92, '②', { col: C2, bold: true }));
    g.push(ctxt(190, 121, '①', { col: C1, bold: true }));
    g.push(ctxt(190, 266, '③', { col: C3, bold: true }));
    g.push(ctxt(32, 306, '이 도출에서 c 는 ①②③ 어디에도 없다. 그래서 7 번 줄을 적을 수 있다', { col: CI, size: 'sm' }));

    /* 오른쪽 — 규칙별로 어디를 보는가 */
    g.push(panel(500, 30, 386, 310, '규칙 넷이 각각 어디를 보는가'));
    const rows = [
        ['∀E', '∀x φ 에서 φ[a/x]', '아무 데도 안 본다', CK],
        ['∃I', 'φ[a/x] 에서 ∃x φ', '아무 데도 안 본다', CK],
        ['∀I', 'φ[c/x] 에서 ∀x φ', '① 과 결론 ∀x φ', C1],
        ['∃E', '∃x φ 와 갈래에서 ψ', '① ② ③ 셋 다', C2],
    ];
    g.push(ctxt(524, 74, '규칙', { col: CK, size: 'sm', bold: true }));
    g.push(ctxt(578, 74, '모양', { col: CK, size: 'sm', bold: true }));
    g.push(ctxt(742, 74, 'c 가 없어야 하는 곳', { col: CK, size: 'sm', bold: true }));
    g.push(ln([[516, 84], [872, 84]], { stroke: CG, sw: 1 }));
    rows.forEach((r, i) => {
        const yy = 112 + i * 40;
        g.push(ctxt(524, yy, r[0], { col: CI, bold: true }));
        g.push(ctxt(578, yy, r[1], { col: CI, size: 'sm' }));
        g.push(ctxt(742, yy, r[2], { col: r[3], size: 'sm', bold: i >= 2 }));
        if (i < 3) g.push(ln([[516, yy + 14], [872, yy + 14]], { stroke: CG, sw: 0.8 }));
    });
    g.push(ctxt(524, 290, '위 둘은 이름을 내가 골라 쓴다. 아래 둘은 이름이', { col: CK, size: 'sm' }));
    g.push(ctxt(524, 310, '아무 것이어도 되어야 하므로 조건이 붙는다.', { col: CK, size: 'sm' }));

    /* 아래 — 표시 셋의 뜻과 실전 요령 */
    g.push(box(14, 356, 872, 128, { stroke: C3, sw: 1.4, rx: 6 }));
    g.push(ctxt(38, 384, '① 열린 가정 — 그 줄이 기대고 있는 전제와 아직 방출되지 않은 가정 전부', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(38, 406, '② 규칙의 재료가 된 ∃x φ', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(330, 406, '③ 규칙이 내놓는 결론 ψ', { col: C3, size: 'sm', bold: true }));
    g.push(ctxt(38, 438, '실전 요령 — ∀I 나 ∃E 를 쓸 때마다 도출 어디에도 없던 새 이름을 만들어 쓰면 ①②③ 셋이 저절로 통과된다.', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(38, 462, '조건을 실제로 따지는 일이 남는 것은 남의 도출을 검사할 때이고, 그때 볼 곳이 위 표의 오른쪽 칸이다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-i-var-conditions',
        svg: svg({
            width: W, height: H,
            title: '한정사 규칙 넷의 변수 조건을 한자리에 모은 그림',
            desc: '도출 한 벌에서 이름을 살펴볼 세 곳을 표시하고 규칙 넷이 각각 어느 곳을 보는지 표로 정리한 뒤 새 이름을 쓰면 조건이 저절로 통과된다는 요령을 적었다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-i-broken — 조건을 빼면 거짓이 증명된다
 * ================================================================== */
add((() => {
    const W = 900;
    const H = 502;
    const g = [];

    /* 왼쪽 — 조건을 무시한 도출 */
    g.push(panel(14, 30, 458, 334, '조건을 무시한 도출 — 한정사 순서를 뒤바꾼다'));
    const A = fitch(44, 92, [
        { no: 1, depth: 0, formula: '∀x ∃y Gxy', reason: '전제' },
        { no: 2, depth: 0, formula: '∃y Gcy', reason: '1 ∀E' },
        { no: 3, depth: 1, formula: 'Gcc~1', reason: '가정' },
        { no: 4, depth: 1, formula: '∀x Gxc~1', reason: '3 ∀I' },
        { no: 5, depth: 1, formula: '∃y ∀x Gxy', reason: '4 ∃I' },
        { no: 6, depth: 0, formula: '∃y ∀x Gxy', reason: '2, 3–5 ∃E' },
    ], { colF: 28, colR: 230, rowH: 29 });
    g.push(A.g);
    g.push(box(82, 160, 82, 25, { stroke: C2, sw: 2, rx: 4 }));
    g.push(ctxt(44, 266, '4 번 줄이 위반이다. ∀I 는 c 가 열린 가정에 없기를 요구하는데,', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(44, 286, '그때 열린 가정에 3 번 줄 Gcc~1 이 있고 거기에 c 가 들어 있다.', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(44, 316, '나머지 다섯 줄은 규칙을 옳게 썼다. 6 번 줄의 ∃E 도 조건을 다 지켰다', { col: CK, size: 'sm' }));
    g.push(ctxt(44, 336, '— c~1 이 열린 가정에도 ∃y Gcy 에도 결론에도 없다. 무너뜨린 것은 줄 하나다.', { col: CK, size: 'sm' }));

    /* 오른쪽 — 10장의 모형으로 확인 */
    g.push(panel(490, 30, 396, 334, '10장의 모형 ℳ~1 로 결론을 확인한다'));
    g.push(ctxt(688, 76, 'D = {1, 2},  I(G) = {⟨1,1⟩, ⟨2,2⟩}', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(dot(618, 130, '1'));
    g.push(selfLoop(618, 130));
    g.push(dot(758, 130, '2'));
    g.push(selfLoop(758, 130));
    g.push(ctxt(688, 182, '화살표가 제자리로만 돌아온다', { anchor: 'middle', col: CK, size: 'sm' }));

    g.push(ln([[516, 200], [860, 200]], { stroke: CG, sw: 1 }));
    g.push(ctxt(524, 226, '전제  ∀x ∃y Gxy', { col: CI, size: 'sm' }));
    g.push(ctxt(830, 226, '참', { anchor: 'end', col: C3, size: 'sm', bold: true }));
    g.push(ctxt(524, 250, '각 점이 자기에게 가는 화살표를 하나씩 갖는다', { col: CK, size: 'sm' }));
    g.push(ctxt(524, 278, '결론  ∃y ∀x Gxy', { col: CI, size: 'sm' }));
    g.push(ctxt(830, 278, '거짓', { anchor: 'end', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(524, 302, '모든 화살표를 받는 점이 없다', { col: CK, size: 'sm' }));

    /* 아래 */
    g.push(box(14, 382, 872, 104, { stroke: C2, sw: 1.4, rx: 6 }));
    g.push(ctxt(38, 410, '조건을 빼면 무엇이 무너지는가', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(38, 436, '전제가 참인 모형에서 결론이 거짓이다. 즉 이 도출은 ⊨ 가 인정하지 않는 것을 ⊢ 로 얻어 냈다.', { col: CI, size: 'sm' }));
    g.push(ctxt(38, 460, '‘⊢ 이면 ⊨ 이다’ 를 지키는 것이 13장의 건전성이고, 그 증명은 규칙마다 이 확인을 한 번씩 한다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-i-broken',
        svg: svg({
            width: W, height: H,
            title: '변수 조건을 어긴 도출과 그 결론이 거짓임을 보이는 모형',
            desc: '한정사 순서를 뒤바꾸는 도출에서 전칭 일반화가 조건을 어긴 줄을 표시하고 정의역이 둘인 모형에서 전제가 참이고 결론이 거짓임을 확인한다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-i-two-shifts — 어느 방향이 되고 어느 방향이 안 되는가
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 424;
    const g = [];

    /* 왼쪽 — 되는 방향 */
    g.push(panel(14, 30, 418, 292, '되는 방향 — ∃y ∀x Gxy 에서 ∀x ∃y Gxy'));
    const A = fitch(42, 90, [
        { no: 1, depth: 0, formula: '∃y ∀x Gxy', reason: '전제' },
        { no: 2, depth: 1, formula: '∀x Gxc~1', reason: '가정' },
        { no: 3, depth: 1, formula: 'Gcc~1', reason: '2 ∀E' },
        { no: 4, depth: 1, formula: '∃y Gcy', reason: '3 ∃I' },
        { no: 5, depth: 1, formula: '∀x ∃y Gxy', reason: '4 ∀I' },
        { no: 6, depth: 0, formula: '∀x ∃y Gxy', reason: '1, 2–5 ∃E' },
    ], { colF: 28, colR: 230, rowH: 28 });
    g.push(A.g);
    g.push(box(70, 190, 96, 25, { stroke: C3, sw: 2, rx: 4 }));
    g.push(ctxt(42, 264, '5 번 줄의 ∀I 에서 c 를 찾아본다. 열린 가정은 1 번 줄과', { col: CI, size: 'sm' }));
    g.push(ctxt(42, 284, '2 번 줄이고 둘 다 c 가 없다. 통과.', { col: C3, size: 'sm', bold: true }));

    /* 오른쪽 — 안 되는 방향 */
    g.push(panel(448, 30, 418, 292, '안 되는 방향 — ∀x ∃y Gxy 에서 ∃y ∀x Gxy'));
    const B = fitch(476, 90, [
        { no: 1, depth: 0, formula: '∀x ∃y Gxy', reason: '전제' },
        { no: 2, depth: 0, formula: '∃y Gcy', reason: '1 ∀E' },
        { no: 3, depth: 1, formula: 'Gcc~1', reason: '가정' },
        { no: 4, depth: 1, formula: '∀x Gxc~1', reason: '3 ∀I' },
        { no: 5, depth: 1, formula: '∃y ∀x Gxy', reason: '4 ∃I' },
        { no: 6, depth: 0, formula: '∃y ∀x Gxy', reason: '2, 3–5 ∃E' },
    ], { colF: 28, colR: 230, rowH: 28 });
    g.push(B.g);
    g.push(box(504, 162, 96, 25, { stroke: C2, sw: 2, rx: 4 }));
    g.push(ctxt(476, 264, '4 번 줄의 ∀I 에서 c 를 찾아본다. 열린 가정에 3 번 줄', { col: CI, size: 'sm' }));
    g.push(ctxt(476, 284, 'Gcc~1 이 있고 거기에 c 가 있다. 막힌다.', { col: C2, size: 'sm', bold: true }));

    g.push(ln([[42, 346], [840, 346]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 372, '두 도출의 줄 수와 규칙 이름이 똑같다. 갈라지는 것은 ∀I 를 쓰는 줄에서 c 가 열린 가정에 있는가 하나다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(W / 2, 396, '왼쪽은 임시 이름 c~1 이 가정에 들어 있고 일반화하려는 c 는 들어 있지 않다. 오른쪽은 가정 하나에 둘이 함께 들어 있다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-i-two-shifts',
        svg: svg({
            width: W, height: H,
            title: '한정사 순서를 바꾸는 두 방향의 도출 비교',
            desc: '되는 방향과 안 되는 방향의 도출을 나란히 놓고 전칭 일반화를 쓰는 줄에서 임시 이름이 열린 가정에 들어 있는지 여부가 둘을 가른다는 것을 보인다',
            body: g.join(''),
        }),
    };
})());

export default figures;
