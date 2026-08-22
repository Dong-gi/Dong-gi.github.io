/**
 * 논리학 17장(괴델 불완전성)의 그림.
 *
 * 이름은 모두 `log-o-` 로 시작한다(17장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-o-*.d2` 에 있고, 여기에는
 * 자리를 손으로 잡아야 하는 것 둘만 둔다 — 괴델 수를 실제로 계산해 보이는
 * 사다리와, 표준 모형 옆에 비표준 모형을 나란히 놓는 수직선.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ℳ ℕ 로 직접 적고, 위첨자는
 * ⁰¹²³⁴⁵⁶⁷⁸⁹ 를 쓴다. 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다.
 * lib 의 esc 가 `~` 를 아래첨자로 먹으므로 라벨에 물결표를 쓰지 않는다.
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

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 20, title, { anchor: 'middle', col: CI, bold: true, size: 'sm' }) : '')
        + (sub ? ctxt(x + w / 2, y + 37, sub, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/* ================================================================== *
 * 17-1. 괴델 수를 실제로 계산해 본다
 * ================================================================== */
add((() => {
    const W = 900, H = 428;
    const g = [];
    g.push(ctxt(W / 2, 26, '문장 하나에 번호를 붙여 본다 — 그리고 그 번호에서 문장을 되찾는다', { anchor: 'middle', col: CI, bold: true }));

    const sx = [230, 330, 430];
    const syms = ['0', '=', '0'];
    const codes = ['1', '9', '1'];
    const powers = ['2¹', '3⁹', '5¹'];
    const values = ['2', '19683', '5'];

    /* ---- 1단: 기호열 ---- */
    g.push(ctxt(26, 79, '① 기호열', { col: CK, size: 'sm', bold: true }));
    syms.forEach((s, i) => {
        g.push(box(sx[i] - 30, 58, 60, 30, { stroke: C1, sw: 1.5, rx: 4 }));
        g.push(ctxt(sx[i], 79, s, { anchor: 'middle', col: C1, bold: true, size: 'lg' }));
    });
    g.push(ctxt(505, 79, '— 산술의 언어로 적은 문장 0 = 0', { col: CK, size: 'sm' }));

    /* ---- 2단: 기호마다 코드 ---- */
    g.push(ctxt(26, 148, '② 기호 코드', { col: CK, size: 'sm', bold: true }));
    syms.forEach((_, i) => {
        g.push(arw(sx[i], 92, sx[i], 118, { col: CK, width: 1.4 }));
        g.push(ctxt(sx[i], 148, codes[i], { anchor: 'middle', col: CI, bold: true }));
    });
    g.push(ctxt(505, 148, '— 기호마다 홀수 하나를 미리 정해 둔다', { col: CK, size: 'sm' }));

    /* ---- 3단: 자리마다 소수 ---- */
    g.push(ctxt(26, 218, '③ 거듭제곱', { col: CK, size: 'sm', bold: true }));
    g.push(ctxt(330, 196, '자리 1 · 2 · 3 에 소수 2 · 3 · 5', { anchor: 'middle', col: CK, size: 'sm' }));
    syms.forEach((_, i) => {
        g.push(arw(sx[i], 160, sx[i], 178, { col: CK, width: 1.4 }));
        g.push(ctxt(sx[i], 224, powers[i], { anchor: 'middle', col: C2, bold: true, size: 'lg' }));
        g.push(ctxt(sx[i], 242, `= ${values[i]}`, { anchor: 'middle', col: CK, size: 'sm' }));
    });
    g.push(ctxt(505, 224, '— 자리 번호는 소수의 순서로, 코드는 지수로', { col: CK, size: 'sm' }));

    /* ---- 4단: 곱한다 ---- */
    g.push(ctxt(26, 297, '④ 곱한다', { col: CK, size: 'sm', bold: true }));
    g.push(arw(330, 254, 330, 274, { col: CK, width: 1.4 }));
    g.push(box(180, 272, 340, 38, { fill: C2, op: 0.13, stroke: C2, sw: 1.5, rx: 5 }));
    g.push(ctxt(350, 297, '2¹ · 3⁹ · 5¹  =  196830', { anchor: 'middle', col: C2, bold: true, size: 'lg' }));
    g.push(ctxt(540, 297, '— 이것이 괴델 수다', { col: CK, size: 'sm' }));

    /* ---- 되돌리기 ---- */
    g.push(ln([[180, 291], [152, 291], [152, 79], [188, 79]], { stroke: C3, sw: 1.8 }));
    g.push(arw(188, 79, 196, 79, { col: C3, marker: 'ar3', width: 1.8 }));
    g.push(ctxt(144, 168, '되돌리기', { anchor: 'end', col: C3, bold: true, size: 'sm' }));
    g.push(ctxt(144, 185, '유일 인수분해', { anchor: 'end', col: C3, size: 'sm' }));

    /* ---- 아래 메모 ---- */
    g.push(ln([[26, 334], [874, 334]], { stroke: CG, sw: 1 }));
    g.push(ctxt(26, 358, '196830 을 소인수로 쪼개는 길은 2¹ · 3⁹ · 5¹ 말고 없다. 그래서 번호에서 기호열이 하나로 정해진다 —', { col: CI, size: 'sm' }));
    g.push(ctxt(26, 376, '5장의 유일 가독성이 문법에서 한 일을, 유일 인수분해가 번호에서 한다.', { col: CI, size: 'sm', bold: true }));
    g.push(ctxt(26, 402, '수가 커지는 것은 문제가 아니다. 우리는 이 수를 계산할 일이 없다. 필요한 것은 대응이 서로 다른 기호열에 서로 다른 수를 주고,', { col: CK, size: 'sm' }));
    g.push(ctxt(26, 420, '기계가 양쪽으로 오갈 수 있다는 사실뿐이다.', { col: CK, size: 'sm' }));

    return {
        name: 'log-o-numbering',
        svg: svg({
            width: W, height: H,
            title: '괴델 수 매기기를 실제로 한 번 해 본다',
            desc: '기호열 0 = 0 에 기호 코드를 붙이고 자리마다 소수의 거듭제곱으로 묶어 196830 을 얻는다. 유일 인수분해가 이 대응을 되돌린다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 17-2. 표준 모형과 비표준 모형
 * ================================================================== */
add((() => {
    const W = 900, H = 442;
    const g = [];
    g.push(ctxt(W / 2, 26, '‘참인데 증명할 수 없다’ 의 참은 어느 모형에서의 참인가', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 위: 표준 모형 ---- */
    g.push(panel(20, 46, 860, 148, '표준 모형 ℕ — 우리가 뜻한 자연수', '0 에서 시작해 S 를 거듭 붙여 닿는 것만 있다'));
    const y1 = 146;
    g.push(ln([[64, y1], [700, y1]], { stroke: CK, sw: 1.6 }));
    [0, 1, 2, 3, 4, 5].forEach((k, i) => {
        const x = 90 + i * 80;
        g.push(`<circle cx="${x}" cy="${y1}" r="5" fill="${C1}"/>`);
        g.push(ctxt(x, y1 + 20, String(k), { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    });
    g.push(ctxt(600, y1 + 5, '· · ·', { col: CK, bold: true }));
    g.push(ctxt(714, y1 + 4, '끝이 없지만 빈틈이 없다', { col: CK, size: 'sm' }));
    g.push(ctxt(90, y1 - 18, 'G 는 여기서 참이다 — 이 줄의 어떤 점도 G 의 도출 번호가 아니다', { col: C1, size: 'sm', bold: true }));

    /* ---- 아래: 비표준 모형 ---- */
    g.push(panel(20, 208, 860, 172, '비표준 모형 — T ⊬ G 이므로 완전성 정리가 존재를 보장한다', 'T 의 공리를 모두 만족하지만 G 는 여기서 거짓이다'));
    const y2 = 306;
    g.push(ln([[64, y2], [420, y2]], { stroke: CK, sw: 1.6 }));
    [0, 1, 2, 3, 4].forEach((k, i) => {
        const x = 90 + i * 76;
        g.push(`<circle cx="${x}" cy="${y2}" r="5" fill="${C1}"/>`);
        g.push(ctxt(x, y2 + 20, String(k), { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    });
    g.push(ctxt(432, y2 + 5, '· · ·', { col: CK, bold: true }));
    g.push(ctxt(90, y2 - 24, '표준 부분 — 여기까지는 ℕ 과 똑같다', { col: C1, size: 'sm' }));

    // 비표준 덩어리
    g.push(box(516, y2 - 28, 344, 56, { fill: C2, op: 0.13, stroke: C2, sw: 1.5, rx: 6 }));
    g.push(ln([[536, y2], [844, y2]], { stroke: C2, sw: 1.6, dash: '6 4' }));
    ['c−1', 'c', 'c+1'].forEach((nm, i) => {
        const x = 600 + i * 90;
        g.push(`<circle cx="${x}" cy="${y2}" r="5" fill="${C2}"/>`);
        g.push(ctxt(x, y2 + 20, nm, { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    });
    g.push(arw(470, y2, 508, y2, { col: CK, width: 1.4, dash: '4 3' }));
    g.push(ctxt(489, y2 + 26, '틈', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(688, y2 + 52, '비표준 원소 — 0 에서 S 를 유한 번 붙여서는 닿을 수 없다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    /* ---- 맨 아래 메모 ---- */
    g.push(ctxt(26, 406, '이 모형에서는 ∃y Prf(y, ⌜G⌝) 가 참이다 — 그 y 가 저 상자 안의 원소이기 때문이다.', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(26, 426, '그 y 는 자연수가 아니므로 진짜 도출을 코딩하지 않는다. 종이에 적을 수 있는 G 의 도출은 여전히 하나도 없다.', { col: CI, size: 'sm' }));

    return {
        name: 'log-o-standard-nonstandard',
        svg: svg({
            width: W, height: H,
            title: '표준 모형과 비표준 모형에서 괴델 문장의 값이 갈린다',
            desc: '위 수직선은 표준 모형이고 아래 수직선은 표준 부분 뒤에 비표준 덩어리가 붙은 모형이다. 괴델 문장은 위에서 참이고 아래에서 거짓이다',
            body: g.join(''),
        }),
    };
})());

export default figures;
