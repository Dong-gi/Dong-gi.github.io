/**
 * 논리학 15장(컴팩트성과 그 대가)의 그림.
 *
 * 이름은 모두 `log-m-` 로 시작한다(15장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-m-*.d2` 에 있고, 여기에는
 * 위치를 손으로 잡아야 하는 것(정의역의 점을 늘어놓기, 수직선 위의 은하,
 * 크기의 사다리)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ≠ ∈ ⊆ ℕ ℳ 로 직접 적는다.
 * 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다. lib 의 esc 가 `~` 를
 * 아래첨자로 먹으므로 라벨에 물결표를 쓰지 않는다(첨자는 유니코드 ₂ ₃ ₙ 로).
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
 * 화소 좌표 소도구. lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가
 * SVG 안에 없어 선이 사라진다. 그래서 색을 직접 넣는 것들을 따로 둔다.
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
        + `${size === 'sm' ? ' font-size="11"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 정의역의 점 하나. */
function pt(x, y, name, { col = CI, r = 5.5, below = 19 } = {}) {
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`
        + (name ? ctxt(x, y + below, name, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/* ================================================================== *
 * 15-1. λ 들을 다 모으면 무한 모형이 강제된다
 * ================================================================== */
add((() => {
    const W = 800, H = 396;
    const g = [];
    g.push(txt(W / 2, 26, 'Λ = { λ₂, λ₃, λ₄, … } — 유한 부분집합은 모두 모형을 갖고, 전체의 모형은 무한하다', { anchor: 'middle', cls: 'ink bold' }));

    /* ---- 왼쪽: 유한 부분집합 ---- */
    g.push(panel(14, 46, 380, 254, '유한 부분집합 Δ ⊆ Λ', 'Δ = { λ₂, λ₃, λ₅, λ₉ } — 가장 큰 첨자가 9'));
    g.push(box(44, 118, 320, 96, { stroke: CG, sw: 1.4, rx: 8, dash: '6 4' }));
    g.push(ctxt(356, 206, 'D', { anchor: 'end', col: CK, size: 'sm' }));
    for (let i = 0; i < 9; i += 1) g.push(pt(74 + i * 33, 158, String(i + 1), { col: C1 }));
    g.push(ctxt(204, 244, '크기 9 인 모형 하나가 Δ 의 네 문장을 전부 참으로 만든다', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(204, 264, '어떤 유한 부분집합을 잡아도 이렇게 된다 —', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    g.push(ctxt(204, 281, '가장 큰 첨자만큼 점을 찍으면 끝이다', { anchor: 'middle', col: C1, size: 'sm', bold: true }));

    /* ---- 오른쪽: 전체 ---- */
    g.push(panel(406, 46, 380, 254, 'Λ 전체', '첨자에 끝이 없다'));
    g.push(box(436, 118, 320, 96, { stroke: CG, sw: 1.4, rx: 8, dash: '6 4' }));
    for (let i = 0; i < 8; i += 1) g.push(pt(466 + i * 33, 158, String(i + 1), { col: C2 }));
    g.push(ctxt(736, 163, '…', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(748, 206, 'D', { anchor: 'end', col: CK, size: 'sm' }));
    g.push(ctxt(596, 244, '크기 n 인 모형은 λₙ₊₁ 을 거짓으로 만든다', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(596, 264, '그러므로 Λ 의 모형은 유한할 수 없다 —', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(596, 281, '무한 정의역이 강제된다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    /* ---- 가운데 화살표와 아래 띠 ---- */
    g.push(arw(396, 172, 404, 172, { col: CK, width: 0 }));
    g.push(box(14, 316, 772, 66, { stroke: C3, sw: 1.5, rx: 6 }));
    g.push(ctxt(400, 342, '컴팩트성이 하는 일은 왼쪽의 유한 모형들을 이어 붙이는 것이 아니다.', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(400, 364, '이어 붙이지 않고도 오른쪽이 존재한다고 보증하는 것이다 — 그래서 오른쪽 점들의 정체는 끝내 알 수 없다', { anchor: 'middle', col: C3, bold: true, size: 'sm' }));

    return {
        name: 'log-m-infinity-forced',
        svg: svg({
            width: W, height: H,
            title: '수를 세는 문장을 다 모으면 무한 모형이 강제된다',
            desc: '왼쪽은 유한 부분집합이 늘 유한 모형을 갖는다는 것, 오른쪽은 전체의 모형이 유한할 수 없다는 것을 보인다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 15-2. 비표준 모형 — ℕ 뒤에 은하가 붙는다
 * ================================================================== */
add((() => {
    const W = 800, H = 386;
    const g = [];
    g.push(txt(W / 2, 26, '산술의 비표준 모형 — 표준 부분 뒤에 앞뒤로 끝없는 사슬이 붙는다', { anchor: 'middle', cls: 'ink bold' }));

    /* ---- 위: 표준 모형 ---- */
    g.push(ctxt(28, 74, '표준 모형 ℕ', { col: CI, bold: true, size: 'sm' }));
    g.push(ln([[28, 112], [560, 112]], { stroke: CG, sw: 1.4 }));
    for (let i = 0; i < 7; i += 1) g.push(pt(58 + i * 62, 112, String(i), { col: C1 }));
    g.push(ctxt(510, 117, '…', { anchor: 'middle', col: C1, bold: true }));
    g.push(arw(536, 112, 574, 112, { col: CK, width: 1.4 }));
    g.push(ctxt(596, 116, '끝없이 이어지고, 이것이 전부다', { col: CK, size: 'sm' }));

    /* ---- 아래: 비표준 모형 ---- */
    g.push(ctxt(28, 186, '비표준 모형 ℳ*', { col: CI, bold: true, size: 'sm' }));
    g.push(ln([[28, 224], [368, 224]], { stroke: CG, sw: 1.4 }));
    for (let i = 0; i < 5; i += 1) g.push(pt(58 + i * 62, 224, String(i), { col: C1 }));
    g.push(ctxt(330, 229, '…', { anchor: 'middle', col: C1, bold: true }));
    g.push(ctxt(178, 260, '표준 부분 — 0 에서 유한 걸음으로 닿는 것', { anchor: 'middle', col: C1, size: 'sm', bold: true }));

    // 은하
    g.push(box(430, 194, 344, 62, { stroke: C2, sw: 1.5, rx: 8, fill: C2, op: 0.1 }));
    g.push(arw(452, 224, 424, 224, { col: C2, marker: 'ar2', width: 1.5 }));
    g.push(ln([[456, 224], [750, 224]], { stroke: CG, sw: 1.4 }));
    ['k−2', 'k−1', 'k', 'k+1', 'k+2'].forEach((s, i) => g.push(pt(486 + i * 62, 224, s, { col: C2 })));
    g.push(arw(752, 224, 782, 224, { col: C2, marker: 'ar2', width: 1.5 }));
    g.push(ctxt(602, 186, '은하 — 앞으로도 뒤로도 끝이 없다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(602, 276, 'k 는 0 보다도 1 보다도 2 보다도 … 크다.', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(602, 293, '그런데 0 에서 k 로 가는 유한 걸음이 없다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    // 두 부분 사이의 틈
    g.push(ln([[388, 224], [412, 224]], { stroke: CG, sw: 1.4, dash: '4 4' }));

    g.push(box(14, 316, 772, 58, { stroke: CG, sw: 1.2, rx: 6 }));
    g.push(ctxt(400, 340, '두 줄은 눈으로 보면 전혀 다르다. 그런데 1차 논리의 문장으로는 하나도 구별되지 않는다 —', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(400, 362, '산술의 참인 문장을 전부 모아도 아래 줄을 걸러 내지 못한다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-m-nonstandard-line',
        svg: svg({
            width: W, height: H,
            title: '산술의 비표준 모형',
            desc: '위는 표준 자연수, 아래는 표준 부분 뒤에 앞뒤로 끝없는 사슬이 붙은 모형이다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 15-3. 뢰벤하임-스콜렘 — 크기가 위아래로 미끄러진다
 * ================================================================== */
add((() => {
    const W = 800, H = 384;
    const g = [];
    g.push(txt(W / 2, 26, '뢰벤하임-스콜렘 — 한 번 무한 모형이 생기면 크기가 위아래로 미끄러진다', { anchor: 'middle', cls: 'ink bold' }));

    const rungs = [
        [96, '더 큰 것들 — 끝이 없다', C3],
        [162, '비가산 — ℝ 과 같은 크기', C3],
        [228, '가산 무한 — ℕ 과 같은 크기', C1],
        [300, '유한 — 크기 1, 2, 3, …', CK],
    ];
    const LX = 210;
    g.push(ln([[LX, 84], [LX, 316]], { stroke: CG, sw: 2 }));
    rungs.forEach(([y, label, col]) => {
        g.push(ln([[LX - 16, y], [LX + 16, y]], { stroke: col, sw: 2.2 }));
        g.push(ctxt(LX - 26, y + 5, label, { anchor: 'end', col, size: 'sm', bold: col !== CK }));
    });
    g.push(ln([[LX - 190, 264], [LX + 300, 264]], { stroke: CG, sw: 1, dash: '5 4' }));
    g.push(ctxt(LX + 306, 268, '무한과 유한의 경계', { col: CK, size: 'sm' }));

    // 출발 모형
    g.push(`<circle cx="${LX}" cy="162" r="7.5" fill="${C2}"/>`);
    g.push(ctxt(LX + 26, 150, 'ℳ — 손에 든 무한 모형 하나', { col: C2, size: 'sm', bold: true }));
    g.push(ctxt(LX + 26, 168, '이것이 Γ 를 참으로 만든다', { col: CK, size: 'sm' }));

    // 아래로
    g.push(arw(LX + 84, 176, LX + 84, 220, { col: C1, marker: 'ar1', width: 2.2 }));
    g.push(ctxt(LX + 96, 200, '아래로 — Γ 를 참으로 만드는 가산 모형이 있다', { col: C1, size: 'sm', bold: true }));

    // 위로
    g.push(arw(LX + 84, 150, LX + 84, 104, { col: C3, marker: 'ar3', width: 2.2 }));
    g.push(ctxt(LX + 96, 120, '위로 — 아무리 큰 크기를 잡아도 그 크기의 모형이 있다', { col: C3, size: 'sm', bold: true }));

    // 유한으로는 못 내려간다
    g.push(ln([[LX + 40, 240], [LX + 40, 292]], { stroke: CK, sw: 1.6, dash: '5 4' }));
    g.push(ln([[LX + 30, 262], [LX + 50, 278]], { stroke: C2, sw: 2 }));
    g.push(ln([[LX + 50, 262], [LX + 30, 278]], { stroke: C2, sw: 2 }));
    g.push(ctxt(LX + 60, 306, '아래로는 가산에서 멈춘다. 유한까지 내려가지는 않는다', { col: CK, size: 'sm' }));

    g.push(box(14, 330, 772, 44, { stroke: C2, sw: 1.5, rx: 6 }));
    g.push(ctxt(400, 357, '어떤 문장 집합도 자기 모형의 크기를 무한 안에서 한 칸으로 좁히지 못한다', { anchor: 'middle', col: C2, bold: true }));

    return {
        name: 'log-m-size-ladder',
        svg: svg({
            width: W, height: H,
            title: '뢰벤하임-스콜렘 정리가 모형의 크기에 대해 말하는 것',
            desc: '무한 모형 하나에서 아래로는 가산 모형이, 위로는 임의로 큰 모형이 따라 나온다',
            body: g.join(''),
        }),
    };
})());

export default figures;
