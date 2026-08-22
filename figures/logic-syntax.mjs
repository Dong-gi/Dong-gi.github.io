/**
 * 논리학 문서 5장(명제논리의 문법)의 그림.
 *
 * 이름은 모두 `log-c-` 로 시작한다(이 장에 배정된 접두어).
 * 파스 트리와 구조도는 `d2/logic/log-c-*.d2` 에 있고, 여기에는 표·격자·사다리처럼
 * d2 로 그리기 번거로운 것만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 연결자는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ∀ ∃ ⊢ ⊨ 로 적고, 아래첨자는 lib 의 `P~1` 표기를 쓴다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 장의 주제는 ‘뜻을 전혀 보지 않고 모양만으로 문장을 정하는 일’ 이다. 그래서 그림도
 * 참·거짓을 하나도 담지 않는다. 진리표는 6장의 몫이다.
 */
import { svg, txt } from './lib.mjs';

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
 * 화소 좌표 소도구. lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * (figures/mcs-induction.mjs 의 같은 헬퍼를 본떴다.)
 * ------------------------------------------------------------------ */

const COL = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG };

function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.8, dash } = {}) {
    const col = COL[cls] ?? CK;
    const mk = cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark';
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 색을 직접 지정하는 글자. txt() 의 class 를 fill 로 갈아 끼운다. */
function ctxt(x, y, s, col, { anchor = 'start', size = 'sm', bold = false } = {}) {
    const t = txt(x, y, s, { anchor, cls: 'ink', size });
    return t.replace(/class="ink( sm)?"/, `class="${size === 'sm' ? 'sm' : ''}${bold ? ' bold' : ''}" fill="${col}"`);
}

/** 줄 간격이 일정한 글 묶음. */
function lines(x, y, arr, { lh = 18, size = 'sm', cls = 'ink2' } = {}) {
    return arr.map((s, i) => txt(x, y + i * lh, s, { cls, size })).join('');
}

/** 라벨이 든 둥근 상자 하나. 나무 마디와 사다리 칸에 함께 쓴다. */
function chip(cx, cy, w, h, label, col, { bold = true, size = 'sm' } = {}) {
    return box(cx - w / 2, cy - h / 2, w, h, { fill: col, op: 0.14, stroke: col, sw: 1.6, rx: 5 })
        + txt(cx, cy + (size === 'sm' ? 4 : 5), label, { anchor: 'middle', cls: bold ? 'ink bold' : 'ink', size });
}

/* ================================================================== *
 * 5-1. 어휘 — 이 언어에 있는 기호가 전부 몇 개인가
 * ================================================================== */
add((() => {
    const W = 780, H = 364;
    const g = [];
    g.push(txt(W / 2, 26, '어휘는 세 무리뿐이고, 이 목록에 없는 기호는 논리식 안에 나타날 수 없다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 44, ph = 176;

    // 문장문자
    g.push(panel(20, py, 232, ph, '문장문자', '무한히 많다'));
    ['P', 'Q', 'R'].forEach((s, i) => g.push(ctxt(44 + i * 42, py + 68, s, C1, { bold: true, size: undefined })));
    ['P~1', 'P~2', 'P~3', 'P~4', '…'].forEach((s, i) => g.push(ctxt(44 + i * 38, py + 98, s, C1, { bold: true, size: undefined })));
    g.push(lines(38, py + 128, [
        '번호를 붙일 수 있으므로',
        '모자랄 일이 없고, 그러면서도',
        '하나씩 세어 나갈 수 있다',
    ], { lh: 17 }));

    // 연결자
    g.push(panel(266, py, 268, ph, '연결자 다섯', '항수가 정해져 있다'));
    const rows = [
        ['¬', '부정', '1항'],
        ['∧', '논리곱', '2항'],
        ['∨', '논리합', '2항'],
        ['→', '함의', '2항'],
        ['↔', '동치', '2항'],
    ];
    rows.forEach((r, i) => {
        const y = py + 62 + i * 21;
        g.push(ctxt(292, y, r[0], C2, { bold: true, size: undefined }));
        g.push(txt(330, y, r[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(430, y, r[2], { cls: 'ink2', size: 'sm' }));
        g.push(txt(478, y, i === 0 ? '재료 하나' : '재료 둘', { cls: 'ink2', size: 'sm' }));
    });

    // 괄호
    g.push(panel(548, py, 212, ph, '괄호', '구두점이다'));
    g.push(ctxt(624, py + 82, '(', C3, { bold: true, size: undefined }));
    g.push(ctxt(676, py + 82, ')', C3, { bold: true, size: undefined }));
    g.push(lines(566, py + 110, [
        '뜻을 가진 기호가 아니다.',
        '어느 조항이 어디에 쓰였는지를',
        '종이에 남기는 표시일 뿐이다',
    ], { lh: 17 }));

    // 여기 없는 것
    const qy = py + ph + 14;
    g.push(panel(20, qy, 740, 96, '어휘에 없는 것 — 섞여 들어오기 쉬운 기호들', null));
    g.push(lines(40, qy + 44, [
        '⊥ — 7장에서 어휘에 더한다. 어휘를 늘리는 일이 곧 정의에 조항 하나를 더하는 일이다',
        '∀ ∃ — 9장에서 다른 언어를 세울 때 쓴다. 명제논리의 어휘에는 없다',
        'φ ψ Γ — 메타언어의 변수다. 논리식 안에 이 글자가 적히는 일은 없다. 마지막 절에서 다룬다',
    ], { lh: 18 }));

    return {
        name: 'log-c-vocabulary',
        svg: svg({
            width: W, height: H,
            title: '명제논리의 어휘 세 무리와 어휘가 아닌 기호들',
            desc: '문장문자는 무한히 많고 번호를 붙일 수 있으며, 연결자는 부정 논리곱 논리합 함의 동치 다섯 개로 항수가 정해져 있고, 괄호는 구두점이다. 모순 기호와 귀결 기호, 한정사, 메타언어 변수는 어휘가 아니다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5-2. 괄호가 없으면 나무가 갈린다
 * ================================================================== */
add((() => {
    const W = 780, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '같은 기호열에서 나무가 둘 나온다 — 정의가 이항 조항마다 괄호를 두르는 것이 이것을 막는다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(ctxt(W / 2, 52, 'P ∧ Q ∨ R', C2, { anchor: 'middle', bold: true, size: undefined }));

    const py = 66, ph = 208;

    // 왼쪽 나무 — ((P ∧ Q) ∨ R)
    g.push(panel(20, py, 366, ph, '읽기 하나 — ((P ∧ Q) ∨ R)', '주연결자가 ∨ 이다'));
    const A = [
        [212, py + 58, '∨', C1],
        [140, py + 112, '∧', C1],
        [312, py + 112, 'R', C3],
        [94, py + 166, 'P', C3],
        [188, py + 166, 'Q', C3],
    ];
    const AE = [[0, 1], [0, 2], [1, 3], [1, 4]];
    for (const [a, b] of AE) g.push(ln([[A[a][0], A[a][1] + 15], [A[b][0], A[b][1] - 15]], { stroke: CK, sw: 1.3 }));
    for (const nd of A) g.push(chip(nd[0], nd[1], 46, 30, nd[2], nd[3], { size: undefined }));
    g.push(ctxt(40, py + 62, '뿌리가 ∨', C1, { bold: true }));
    g.push(ctxt(40, py + 196, '먼저 묶이는 것은 P 와 Q 다', C1, { bold: true }));

    // 오른쪽 나무 — (P ∧ (Q ∨ R))
    g.push(panel(400, py, 360, ph, '읽기 둘 — (P ∧ (Q ∨ R))', '주연결자가 ∧ 이다'));
    const B = [
        [568, py + 58, '∧', C2],
        [496, py + 112, 'P', C3],
        [656, py + 112, '∨', C2],
        [612, py + 166, 'Q', C3],
        [704, py + 166, 'R', C3],
    ];
    const BE = [[0, 1], [0, 2], [2, 3], [2, 4]];
    for (const [a, b] of BE) g.push(ln([[B[a][0], B[a][1] + 15], [B[b][0], B[b][1] - 15]], { stroke: CK, sw: 1.3 }));
    for (const nd of B) g.push(chip(nd[0], nd[1], 46, 30, nd[2], nd[3], { size: undefined }));
    g.push(ctxt(418, py + 62, '뿌리가 ∧', C2, { bold: true }));
    g.push(ctxt(418, py + 196, '먼저 묶이는 것은 Q 와 R 다', C2, { bold: true }));

    g.push(lines(24, py + ph + 22, [
        '읽어낼 것 — 두 나무는 마디의 개수도 잎의 순서도 같고 오직 묶는 순서만 다르다. 그런데 묶는 순서가 그 식이 무엇인지를 정한다.',
        '정의를 따르면 두 식은 처음부터 서로 다른 기호열이다. ‘P ∧ Q ∨ R’ 은 정의에 따른 적형식이 아니라 괄호를 지운 줄임말이고,',
        '어느 쪽 줄임말인지는 우선순위 관습이 정한다. 두 식의 뜻이 실제로 다른지는 6장에서 확인한다 — 이 장은 모양만 본다',
    ], { lh: 20 }));

    return {
        name: 'log-c-ambiguous-tree',
        svg: svg({
            width: W, height: H,
            title: '괄호가 없는 기호열이 두 개의 나무로 갈린다',
            desc: 'P 논리곱 Q 논리합 R 이라는 기호열이 논리합을 뿌리로 하는 나무와 논리곱을 뿌리로 하는 나무 두 가지로 읽히는 그림. 묶는 순서만 다르고 잎의 순서는 같다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5-3. 우선순위 사다리와 괄호 복원
 * ================================================================== */
add((() => {
    const W = 780, H = 378;
    const g = [];
    g.push(txt(W / 2, 26, '세게 묶는 것이 먼저 괄호를 갖는다 — 위에서 아래로 한 칸씩 내려가며 괄호를 채운다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 44, ph = 250;

    // 사다리
    g.push(panel(20, py, 212, ph, '결합 우선순위', '위가 세고 아래가 약하다'));
    const rungs = [
        ['¬', '가장 세게 묶는다', C2],
        ['∧', '', C1],
        ['∨', '', C1],
        ['→', '', C1],
        ['↔', '가장 약하게 묶는다', C3],
    ];
    rungs.forEach((r, i) => {
        const y = py + 66 + i * 30;
        g.push(chip(62, y, 42, 24, r[0], r[2], { size: undefined }));
        if (r[1]) g.push(ctxt(92, y + 4, r[1], r[2], { bold: true }));
        if (i < 4) g.push(arw(62, y + 13, 62, y + 30 - 13, { cls: 'ark', width: 1.4 }));
    });
    g.push(lines(36, py + 218, [
        '∧ 과 ∨ 가 이어지면 왼쪽으로 묶고',
        '→ 와 ↔ 가 이어지면 오른쪽으로 묶는다',
    ], { lh: 17 }));

    // 복원 단계
    g.push(panel(246, py, 514, ph, '괄호를 되돌리는 세 걸음', '¬P ∧ Q → R ∨ S 가 가리키는 적형식을 찾는다'));
    const steps = [
        ['0', '¬P ∧ Q → R ∨ S', '줄임말이다. 적형식이 아니다', CK],
        ['1', '(¬P ∧ Q) → R ∨ S', '¬ 가 가장 세고 ∧ 이 다음이다', C2],
        ['2', '(¬P ∧ Q) → (R ∨ S)', '∨ 가 그다음이다', C1],
        ['3', '((¬P ∧ Q) → (R ∨ S))', '→ 가 가장 약하다. 적형식이다', C3],
    ];
    steps.forEach((s, i) => {
        const y = py + 70 + i * 36;
        g.push(ctxt(266, y + 4, s[0], CK, { bold: true }));
        g.push(txt(288, y + 5, s[1], { cls: i === 3 ? 'ink bold' : 'ink' }));
        g.push(ctxt(482, y + 4, s[2], s[3]));
        if (i < 3) g.push(arw(272, y + 11, 272, y + 26, { cls: 'ark', width: 1.3 }));
    });
    g.push(lines(266, py + 208, [
        '3 번 줄만 정의가 인정하는 기호열이고 위 세 줄은 그것을 가리키는 줄임말이다.',
        '¬P 에는 어느 단계에서도 괄호가 붙지 않는다 — ¬ 조항은 괄호를 두르지 않기 때문이다',
    ], { lh: 18 }));

    g.push(lines(24, py + ph + 22, [
        '읽어낼 것 — 이 사다리는 문법이 아니라 관습이다. 정의에는 우선순위라는 조항이 없고, 이항 조항마다 괄호를 두르라는 말만 있다.',
        '그래서 괄호를 지운 줄임말은 적형식이 아니고, 적형식을 가리키는 이름이다. 13장 이후 ‘모든 적형식에 대하여’ 를 증명할 때',
        '확인할 조항이 여섯 개(기저 하나와 연결자 다섯)로 끝나는 것이 이 구분 덕이다 — 관습까지 조항으로 치면 증명이 늘어난다',
    ], { lh: 20 }));

    return {
        name: 'log-c-precedence',
        svg: svg({
            width: W, height: H,
            title: '결합 우선순위 사다리와 괄호 복원 네 걸음',
            desc: '부정이 가장 세게 묶고 논리곱 논리합 함의 동치 순으로 약해지는 사다리와, 괄호를 지운 기호열에 우선순위를 따라 괄호를 하나씩 되돌리는 네 단계',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5-4. 적형식 전체에 번호를 붙일 수 있다
 * ================================================================== */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '줄마다 무한히 많고 줄도 무한히 많지만, 대각선으로 훑으면 빠짐없이 번호가 붙는다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 44, ph = 228;
    g.push(panel(20, py, 470, ph, '기호 수로 줄을 나눈다', '각 줄 안에서는 문장문자 번호 순으로 늘어놓는다'));

    const rowLab = [
        ['기호 1개', 'P, Q, R, P~1, …'],
        ['기호 2개', '¬P, ¬Q, ¬R, …'],
        ['기호 3개', '¬¬P, ¬¬Q, …'],
        ['기호 4개', '¬¬¬P, …'],
        ['기호 5개', '(P ∧ Q), (P ∨ Q), …'],
    ];
    const gx = 236, gy = py + 54, cw = 46, chh = 30;
    rowLab.forEach((r, i) => {
        g.push(txt(38, gy + i * chh + 20, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(104, gy + i * chh + 20, r[1], { cls: 'ink2', size: 'sm' }));
    });
    // 대각선 번호. (행 i, 열 j) 에 붙는 번호는 대각선 순서로 센다.
    const num = (i, j) => {
        const d = i + j;
        return (d * (d + 1)) / 2 + i + 1;
    };
    // 칸 → 대각선 선 → 번호 순서로 그린다. 번호가 선 위에 와야 읽힌다.
    for (let i = 0; i < 5; i += 1) {
        for (let j = 0; j < 5; j += 1) {
            const onDiag = i + j <= 3;
            g.push(box(gx + j * cw, gy + i * chh, cw, chh,
                { fill: onDiag ? C1 : 'none', op: onDiag ? 0.16 : 1, stroke: onDiag ? C1 : CG, sw: onDiag ? 1.5 : 0.9, rx: 3 }));
        }
    }
    for (let d = 1; d <= 3; d += 1) {
        g.push(arw(gx + d * cw + cw / 2 - 8, gy + chh / 2 + 8, gx + cw / 2 + 8, gy + d * chh + chh / 2 - 8,
            { cls: 's2', width: 1.5, dash: '5 4' }));
    }
    for (let i = 0; i < 5; i += 1) {
        for (let j = 0; j < 5; j += 1) {
            const onDiag = i + j <= 3;
            g.push(txt(gx + j * cw + cw / 2, gy + i * chh + 20, String(num(i, j)),
                { anchor: 'middle', cls: onDiag ? 'ink bold' : 'ink2', size: 'sm' }));
        }
    }
    g.push(ctxt(38, gy + 5 * chh + 24, '주황 점선이 번호를 붙여 나가는 순서다', C2, { bold: true }));

    // 오른쪽 메모
    g.push(panel(504, py, 256, ph, '이 그림이 쓰이는 곳', null));
    g.push(ctxt(522, py + 56, '왜 줄을 나눌 수 있는가', C1, { bold: true }));
    g.push(lines(522, py + 76, [
        '적형식은 조항을 유한 번 적용해',
        '만든 것이므로 기호가 유한 개다.',
        '무한히 긴 연언은 애초에 없다',
    ], { lh: 17 }));
    g.push(ctxt(522, py + 144, '어디에 쓰는가', C3, { bold: true }));
    g.push(lines(522, py + 164, [
        '15장 컴팩트성. 증명도 유한하고',
        '논리식도 유한하다는 두 사실이',
        '그 정리를 떠받친다',
    ], { lh: 17 }));

    g.push(lines(24, py + ph + 22, [
        '읽어낼 것 — 어휘가 무한한데도 전체를 한 줄로 세울 수 있다. 문장문자에 번호가 있어 각 줄을 늘어놓을 수 있고, 줄의 개수도 셀 수 있기 때문이다.',
        '‘셀 수 있다’ 는 이 장에서 ‘1, 2, 3, … 을 빠짐없이 붙일 수 있다’ 는 뜻으로만 쓴다. 정확한 정의는 15장에서 다시 세운다',
    ], { lh: 20 }));

    return {
        name: 'log-c-countable',
        svg: svg({
            width: W, height: H,
            title: '적형식 전체에 번호를 붙이는 대각선 훑기',
            desc: '기호 수로 줄을 나눈 격자에서 각 줄이 무한하고 줄도 무한하지만 대각선 순서로 훑으면 모든 칸에 번호가 붙는 그림, 그리고 이 사실이 컴팩트성에 쓰인다는 메모',
            body: g.join(''),
        }),
    };
})());

export default figures;
