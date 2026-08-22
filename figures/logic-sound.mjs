/**
 * 논리학 13장(건전성)의 그림 가운데 좌표를 손으로 잡아야 하는 것.
 *
 * 이름은 모두 `log-k-` 로 시작한다(13장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-k-*.d2` 에 있고, 여기에는
 * 사다리·나무·도출표처럼 줄을 정확히 맞춰야 하는 것만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ℳ ∅ 로 직접 적는다.
 * 아래첨자는 `Γ~i` 표기가 tspan 으로 내려간다. 큰따옴표는 ‘ ’ 로 대신한다.
 * 화살표 기호 ↦ 는 폰트에 없을 수 있어 쓰지 않는다 — 말로 풀어 적는다.
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
 * 화소 좌표 소도구. lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가
 * SVG 안에 없어 선이 사라진다. 색을 직접 넣는 것들을 따로 둔다.
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', width = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

function panel(x, y, w, h, title) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 22, title, { anchor: 'middle', col: CI, bold: true, size: 'sm' }) : '');
}

function node(x, y, str, { col = C1, r = 7 } = {}) {
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;
}

/* ================================================================== *
 * log-k-two-inductions — 자연수 귀납법과 구조적 귀납법을 나란히
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 428;
    const g = [];

    /* ---- 왼쪽: 자연수 사다리 ---- */
    g.push(panel(14, 30, 380, 322, '자연수에 대한 귀납법 — 사다리 한 줄'));

    const ys = [310, 268, 226, 184];
    const names = ['0', '1', '2', '3'];
    ys.forEach((y, i) => {
        g.push(node(62, y, '', { col: i === 0 ? C2 : C1 }));
        g.push(ctxt(44, y + 5, names[i], { anchor: 'end', col: CI, size: 'sm' }));
    });
    for (let i = 0; i < 3; i += 1) {
        g.push(arw(62, ys[i] - 9, 62, ys[i + 1] + 11, { col: CK }));
        g.push(ctxt(74, (ys[i] + ys[i + 1]) / 2 + 4, '+1', { col: CK, size: 'sm' }));
    }
    g.push(ctxt(62, 160, '⋮', { anchor: 'middle', col: CK }));

    g.push(ctxt(122, 95, '왜 이 둘로 충분한가', { col: CI, bold: true, size: 'sm' }));
    g.push(ctxt(122, 113, '자연수가 0 에서 출발해 +1 을', { col: CK, size: 'sm' }));
    g.push(ctxt(122, 131, '유한 번 적용해 만들어지기 때문', { col: CK, size: 'sm' }));
    g.push(ctxt(122, 149, '어떤 수든 0 까지 내려가는', { col: CK, size: 'sm' }));
    g.push(ctxt(122, 167, '유한한 사다리가 있다', { col: CK, size: 'sm' }));
    g.push(ctxt(122, 205, '귀납 단계', { col: C1, bold: true, size: 'sm' }));
    g.push(ctxt(122, 223, 'n 이 가지면 n+1 도 갖는다', { col: CI, size: 'sm' }));
    g.push(ctxt(122, 298, '기저 단계', { col: C2, bold: true, size: 'sm' }));
    g.push(ctxt(122, 316, '0 이 성질을 갖는다', { col: CI, size: 'sm' }));

    /* ---- 오른쪽: 적형식 나무 ---- */
    g.push(panel(406, 30, 460, 322, '적형식에 대한 구조적 귀납법 — 사다리가 나무가 된다'));

    const root = [636, 108];
    const l2a = [540, 176];
    const l2b = [742, 176];
    const l3a = [486, 244];
    const l3b = [594, 244];
    const l3c = [742, 244];

    g.push(ln([[l3a[0], l3a[1] - 12], [l2a[0], l2a[1] + 12]], { stroke: CG }));
    g.push(ln([[l3b[0], l3b[1] - 12], [l2a[0], l2a[1] + 12]], { stroke: CG }));
    g.push(ln([[l3c[0], l3c[1] - 12], [l2b[0], l2b[1] + 12]], { stroke: CG }));
    g.push(ln([[l2a[0], l2a[1] - 12], [root[0], root[1] + 12]], { stroke: CG }));
    g.push(ln([[l2b[0], l2b[1] - 12], [root[0], root[1] + 12]], { stroke: CG }));

    g.push(ctxt(root[0], root[1] + 5, '((P ∧ Q) → ¬R)', { anchor: 'middle', col: CI }));
    g.push(ctxt(l2a[0], l2a[1] + 5, '(P ∧ Q)', { anchor: 'middle', col: CI }));
    g.push(ctxt(l2b[0], l2b[1] + 5, '¬R', { anchor: 'middle', col: CI }));
    g.push(ctxt(l3a[0], l3a[1] + 5, 'P', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(l3b[0], l3b[1] + 5, 'Q', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(l3c[0], l3c[1] + 5, 'R', { anchor: 'middle', col: C2, bold: true }));

    g.push(ctxt(608, 152, '→ 조항', { anchor: 'end', col: C1, size: 'sm' }));
    g.push(ctxt(500, 218, '∧ 조항', { anchor: 'end', col: C1, size: 'sm' }));
    g.push(ctxt(760, 218, '¬ 조항', { col: C1, size: 'sm' }));

    g.push(ln([[440, 268], [830, 268]], { stroke: C2, sw: 1.2, dash: '5 4' }));
    g.push(ctxt(440, 288, '기저 갈래 — 문장문자와 ⊥ 가 여기 놓인다. 확인할 갈래 둘', { col: C2, size: 'sm' }));
    g.push(ctxt(440, 314, '조항 갈래 — 위로 올라가는 각 단계마다 하나씩. ¬ 와 이항 넷, 모두 다섯', { col: C1, size: 'sm' }));

    /* ---- 아래: 대응 ---- */
    g.push(ln([[40, 372], [840, 372]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 398, '두 사다리의 칸이 하나씩 대응한다. 0 자리에 문장문자와 ⊥ 가 오고, +1 자리에 조항 여섯이 온다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(W / 2, 420, '정당한 근거도 같다 — 자연수는 +1 을 유한 번, 적형식은 조항을 유한 번 적용해 만들어진다. 뒤엣것이 5장의 폐포 조항이다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-k-two-inductions',
        svg: svg({
            width: W, height: H,
            title: '자연수 귀납법과 구조적 귀납법의 대응',
            desc: '왼쪽에 0 에서 시작해 하나씩 올라가는 자연수 사다리를, 오른쪽에 문장문자에서 시작해 조항을 따라 올라가는 적형식 파스 트리를 나란히 놓고 기저 단계와 귀납 단계가 서로 대응함을 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-k-line-ledger — 도출의 줄마다 딸린 Γ 와 불변량
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 348;
    const g = [];

    const rows = [
        { no: '1', depth: 1, f: 'P', r: '가정', gam: 'Γ~1 = {P}', inv: '{P} ⊨ P' },
        { no: '2', depth: 2, f: 'Q', r: '가정', gam: 'Γ~2 = {P, Q}', inv: '{P, Q} ⊨ Q' },
        { no: '3', depth: 2, f: 'P', r: '1 R', gam: 'Γ~3 = {P, Q}', inv: '{P, Q} ⊨ P' },
        { no: '4', depth: 1, f: 'Q → P', r: '2–3 →I', gam: 'Γ~4 = {P}', inv: '{P} ⊨ Q → P' },
        { no: '5', depth: 0, f: 'P → (Q → P)', r: '1–4 →I', gam: 'Γ~5 = ∅', inv: '⊨ P → (Q → P)' },
    ];

    const x0 = 34;
    const colF = 26;
    const barGap = 14;
    const colR = 190;
    const colG = 300;
    const colI = 470;
    const y0 = 118;
    const rowH = 34;
    const Y = i => y0 + i * rowH;

    g.push(ctxt(x0, 44, '도출 한 벌과, 그 도출이 줄마다 지고 있는 장부', { col: CI, bold: true }));
    g.push(ctxt(x0, 66, '왼쪽 셋은 7장이 적던 것 그대로다. 오른쪽 둘이 이 장에서 새로 붙는 열이다', { col: CK, size: 'sm' }));

    g.push(ctxt(x0 + 14, 96, '줄', { anchor: 'end', col: CK, size: 'sm' }));
    g.push(ctxt(x0 + colF + 6, 96, '논리식', { col: CK, size: 'sm' }));
    g.push(ctxt(x0 + colR, 96, '근거', { col: CK, size: 'sm' }));
    g.push(ctxt(x0 + colG, 96, '기대고 있는 것', { col: C1, size: 'sm', bold: true }));
    g.push(ctxt(x0 + colI, 96, '줄의 불변량', { col: C1, size: 'sm', bold: true }));
    g.push(ln([[x0 - 6, 104], [840, 104]], { stroke: CG, sw: 1 }));

    /* 범위 막대 */
    const maxDepth = 2;
    for (let d = 1; d <= maxDepth; d += 1) {
        let start = -1;
        for (let i = 0; i <= rows.length; i += 1) {
            const inside = i < rows.length && rows[i].depth >= d;
            if (inside && start < 0) start = i;
            if (!inside && start >= 0) {
                const bx = x0 + colF + (d - 1) * barGap;
                g.push(ln([[bx + 9, Y(start) - 15], [bx, Y(start) - 15], [bx, Y(i - 1) + 8]], { stroke: C1, sw: 1.8 }));
                start = -1;
            }
        }
    }

    rows.forEach((r, i) => {
        const y = Y(i);
        g.push(ctxt(x0 + 14, y, r.no, { anchor: 'end', col: CK, size: 'sm' }));
        g.push(ctxt(x0 + colF + r.depth * barGap + 5, y, r.f, { col: CI }));
        g.push(ctxt(x0 + colR, y, r.r, { col: CK, size: 'sm' }));
        g.push(ctxt(x0 + colG, y, r.gam, { col: CI, size: 'sm' }));
        g.push(ctxt(x0 + colI, y, r.inv, { col: CI, size: 'sm' }));
    });

    /* 방출이 일어나는 두 줄을 표시한다 */
    g.push(box(x0 + colG - 8, Y(3) - 16, 118, 24, { stroke: C2, sw: 1.8, rx: 4 }));
    g.push(box(x0 + colG - 8, Y(4) - 16, 118, 24, { stroke: C2, sw: 1.8, rx: 4 }));
    g.push(arw(614, Y(3) - 4, 642, Y(3) - 4, { col: C2 }));
    g.push(ctxt(650, Y(3) - 32, '방출이 일어난 줄', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(650, Y(3) - 12, 'Γ 에서 식이 빠지고', { col: C2, size: 'sm' }));
    g.push(ctxt(650, Y(3) + 8, '그만큼 식이 무거워진다', { col: C2, size: 'sm' }));
    g.push(ctxt(650, Y(3) + 28, '값은 바뀌지 않는다', { col: C2, size: 'sm' }));

    g.push(ln([[x0 - 6, 288], [840, 288]], { stroke: CG, sw: 1 }));
    g.push(ctxt(x0, 310, '마지막 줄의 Γ 가 비었다. 그래서 그 줄의 불변량이 곧 ⊨ P → (Q → P) 라는 정리가 된다', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(x0, 330, '건전성 증명이 실제로 증명하는 것은 마지막 줄 하나가 아니라 이 열 전체다', { col: CK, size: 'sm' }));

    return {
        name: 'log-k-line-ledger',
        svg: svg({
            width: W, height: H,
            title: '도출의 줄마다 딸리는 가정 집합과 줄의 불변량',
            desc: '피치 스타일 도출표 오른쪽에 각 줄이 기대는 가정 집합과 그 줄의 의미론적 귀결 주장을 나란히 적어, 방출이 일어나는 줄에서 가정 집합이 줄어들고 마지막 줄에서 비게 됨을 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-k-discharge-shift — 방출은 같은 것을 다른 자리에 적는 일이다
 * ================================================================== */
add((() => {
    const W = 860;
    const H = 322;
    const g = [];

    /* 왼쪽 — 가정이 열려 있는 줄 */
    g.push(panel(20, 30, 372, 176, 'j 번 줄 — 가정 φ 가 아직 열려 있다'));
    g.push(box(44, 76, 150, 96, { stroke: CK, sw: 1.2 }));
    g.push(ctxt(119, 96, '기대고 있는 것', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(60, 122, 'Γ~k 의 식들', { col: CI, size: 'sm' }));
    g.push(ctxt(60, 150, 'φ', { col: C2, bold: true }));
    g.push(ctxt(214, 128, '⊨', { col: CK }));
    g.push(ctxt(246, 128, 'ψ', { col: CI }));
    g.push(ctxt(110, 194, '귀납 가설이 주는 것', { col: CK, size: 'sm' }));

    /* 화살표 */
    g.push(arw(400, 118, 456, 118, { col: C2, width: 2 }));
    g.push(ctxt(428, 104, '방출', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    /* 오른쪽 — 방출 뒤 */
    g.push(panel(468, 30, 372, 176, 'k 번 줄 — 가정을 닫았다'));
    g.push(box(492, 76, 150, 96, { stroke: CK, sw: 1.2 }));
    g.push(ctxt(567, 96, '기대고 있는 것', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(508, 122, 'Γ~k 의 식들', { col: CI, size: 'sm' }));
    g.push(ctxt(508, 150, '(φ 가 빠졌다)', { col: CK, size: 'sm' }));
    g.push(ctxt(662, 128, '⊨', { col: CK }));
    g.push(ctxt(694, 128, 'φ', { col: C2, bold: true }));
    g.push(ctxt(710, 128, '→ ψ', { col: CI }));
    g.push(ctxt(492, 194, '보여야 하는 것', { col: CK, size: 'sm' }));

    /* φ 가 옮겨 가는 경로 */
    g.push(ln([[68, 158], [68, 236], [694, 236], [694, 140]], { stroke: C2, sw: 1.4, dash: '5 4' }));
    g.push(ctxt(381, 254, 'φ 는 사라지지 않고 화살표의 앞자리로 옮겨 갔다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    g.push(ln([[40, 274], [820, 274]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 300, '두 줄이 같은 말이라는 보장이 6장의 의미론적 연역정리다. 7장이 ‘같은 문장이 두 번 나온 것’ 이라고 적어 둔 자리가 여기서 만난다', { anchor: 'middle', col: CI, size: 'sm' }));

    return {
        name: 'log-k-discharge-shift',
        svg: svg({
            width: W, height: H,
            title: '방출이 의미론에서 하는 일',
            desc: '가정이 열린 줄과 방출된 줄을 나란히 놓고, 방출된 가정이 기대는 목록에서 빠지는 대신 조건문의 앞자리로 옮겨 감을 점선 경로로 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * log-k-forall-i-step — ∀I 확인의 네 걸음과 조건 둘
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 402;
    const g = [];

    g.push(ctxt(34, 44, '∀I 가 건전함을 확인하는 네 걸음 — 모형을 한 번 갈아탔다가 되돌아온다', { col: CI, bold: true }));

    /* ① 출발 */
    g.push(box(34, 70, 226, 82, { stroke: C1, sw: 1.5 }));
    g.push(ctxt(147, 96, 'ℳ', { anchor: 'middle', col: C1, bold: true }));
    g.push(ctxt(147, 120, 'Γ~k 를 모두 참으로 만든다', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(147, 140, 'D 의 원소 d 를 아무거나 잡는다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* ①→② 화살표 */
    g.push(arw(266, 111, 330, 111, { col: C2, width: 2 }));
    g.push(ctxt(298, 102, '①', { anchor: 'middle', col: C2, bold: true, size: 'sm' }));

    /* ② 갈아탄 모형 */
    g.push(box(336, 70, 226, 82, { stroke: C2, sw: 1.5 }));
    g.push(ctxt(449, 96, 'ℳ*  —  c 만 d 로 해석', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(449, 120, 'Γ~k 를 여전히 참으로 만든다', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(449, 140, '근거 — 이름 바꾸기 보조정리', { anchor: 'middle', col: CK, size: 'sm' }));

    g.push(ctxt(576, 92, '조건 (가)', { col: C2, bold: true, size: 'sm' }));
    g.push(ctxt(576, 112, 'c 가 열린 가정에 없다.', { col: CK, size: 'sm' }));
    g.push(ctxt(576, 130, '없으니 해석을 바꿔도', { col: CK, size: 'sm' }));
    g.push(ctxt(576, 148, '전제가 흔들리지 않는다', { col: CK, size: 'sm' }));

    /* ②→③ 아래로 */
    g.push(arw(449, 158, 449, 196, { col: CK }));
    g.push(ctxt(462, 182, '② 귀납 가설 — ℳ* ⊨ φ[c/x]', { col: CK, size: 'sm' }));

    g.push(box(336, 202, 226, 62, { stroke: CK, sw: 1.2 }));
    g.push(ctxt(449, 226, '③ 대입 보조정리', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(449, 248, 'ℳ* 에서 x 자리에 d 를 꽂아 φ', { anchor: 'middle', col: CI, size: 'sm' }));

    /* ③→④ 되돌아오기 */
    g.push(ln([[336, 233], [300, 233]], { stroke: C3, sw: 1.6 }));
    g.push(arw(300, 233, 300, 168, { col: C3, width: 1.6 }));
    g.push(ln([[300, 168], [176, 168]], { stroke: C3, sw: 1.6 }));
    g.push(arw(176, 168, 176, 158, { col: C3, width: 1.6 }));
    g.push(ctxt(34, 200, '④ 되돌아온다 — ℳ 에서도 같은 결론', { col: C3, size: 'sm', bold: true }));

    g.push(ctxt(34, 236, '조건 (나)', { col: C3, bold: true, size: 'sm' }));
    g.push(ctxt(34, 254, 'c 가 결과 ∀x φ 에 없으니 φ 에도 없다.', { col: CK, size: 'sm' }));
    g.push(ctxt(34, 272, '없으니 c 의 해석을 되돌려도 값이 같다', { col: CK, size: 'sm' }));

    g.push(ln([[34, 322], [846, 322]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 348, 'd 를 아무거나 잡았는데 결론이 나왔으므로 모든 d 에 대해 성립하고, ∀ 조항에 의해 ℳ ⊨ ∀x φ 다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));
    g.push(ctxt(W / 2, 374, '조건 (가)가 없으면 ① 이 막히고 조건 (나)가 없으면 ④ 가 막힌다. 11장이 문법으로만 적어 둔 조건 하나에 걸음 하나가 대응한다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(W / 2, 396, '∃E 도 같은 그림이고, 조건이 셋이라 되돌아올 때 결론 ψ 에 대해서도 같은 확인을 한 번 더 한다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-k-forall-i-step',
        svg: svg({
            width: W, height: H,
            title: '전칭 일반화 규칙의 건전성 확인에서 변수 조건이 쓰이는 자리',
            desc: '원래 모형에서 임시 이름의 해석만 바꾼 모형으로 갈아탔다가 되돌아오는 네 걸음을 그리고, 첫 걸음이 변수 조건의 첫 항목을, 마지막 걸음이 둘째 항목을 쓴다는 것을 보인다',
            body: g.join(''),
        }),
    };
})());

export default figures;
