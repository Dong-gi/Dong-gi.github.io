/**
 * 논리학 10장(술어논리의 의미론)의 그림.
 *
 * 이름은 모두 `log-h-` 로 시작한다(10장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-h-*.d2` 에 있고, 여기에는
 * 위치를 손으로 잡아야 하는 것(정의역의 점과 술어가 고르는 부분집합, 관계 화살표,
 * 배정을 갈아 끼우는 그림)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊨ ∀ ∃ ∈ ⊆ 로 직접 적고, 모형은 ℳ,
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

/** 휘어진 화살표. 두 점 사이를 이차 곡선으로 잇고 가운데를 bow 만큼 밀어낸다. */
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
        + txt(x, y + below, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
}

/* ================================================================== *
 * 10-1. 모형은 무엇으로 되어 있는가 — 그림과 목록이 같은 것이다
 * ================================================================== */
add((() => {
    const W = 800, H = 430;
    const g = [];
    g.push(txt(W / 2, 26, '모형 ℳ = ⟨ D, I ⟩ — 점들의 모임 하나와, 그 점들을 고르는 방법 하나', { anchor: 'middle', cls: 'ink bold' }));

    /* ---- 왼쪽: 그림으로 본 모형 ---- */
    g.push(panel(14, 44, 404, 366, '그림으로 본 모형', '상수는 점 하나를 가리키고 술어는 점들을 고른다'));

    // 정의역 상자
    g.push(box(96, 118, 300, 214, { stroke: CG, sw: 1.4, rx: 10, dash: '6 4' }));
    g.push(ctxt(386, 322, '정의역 D', { anchor: 'end', col: CK, size: 'sm' }));

    // I(F) 가 고른 부분집합
    g.push(ell(196, 176, 88, 40, { stroke: C1, sw: 1.6, fill: C1, op: 0.14 }));
    g.push(ctxt(196, 128, 'I(F) 가 고른 것', { anchor: 'middle', col: C1, size: 'sm', bold: true }));

    // 점 셋
    g.push(pt(148, 176, '1'));
    g.push(pt(244, 176, '2'));
    g.push(pt(300, 282, '3'));

    // I(G) 화살표
    g.push(carw(152, 190, 292, 270, 26, { col: C2, marker: 'ar2' }));
    g.push(carw(250, 190, 296, 268, 14, { col: C2, marker: 'ar2' }));
    g.push(loop(300, 272, 12, { col: C2, marker: 'ar2' }));
    g.push(ctxt(346, 152, 'I(G) 가 고른', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(346, 168, '화살표', { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    // 상수 이름표
    g.push(ctxt(52, 180, 'a', { col: CI, bold: true }));
    g.push(arw(64, 176, 136, 176, { col: C3, width: 1.5, marker: 'ar3' }));
    g.push(ctxt(52, 296, 'b', { col: CI, bold: true }));
    g.push(arw(64, 292, 286, 285, { col: C3, width: 1.5, marker: 'ar3' }));
    g.push(ctxt(30, 200, '상수', { col: CK, size: 'sm' }));

    /* ---- 오른쪽: 적어 본 모형 ---- */
    g.push(panel(432, 44, 354, 366, '적어 본 모형', '위 그림에 든 정보가 이것뿐이다'));
    const lines = [
        ['D = { 1, 2, 3 }', CI, true],
        ['— 비어 있으면 안 된다', CK, false],
        ['I(a) = 1', CI, true],
        ['I(b) = 3', CI, true],
        ['— 상수는 D 의 원소 하나', CK, false],
        ['I(F) = { 1, 2 }   ⊆ D', CI, true],
        ['— 1항 술어는 D 의 부분집합', CK, false],
        ['I(G) = { (1,3), (2,3), (3,3) }', CI, true],
        ['       ⊆ D × D', CI, true],
        ['— 2항 술어는 순서쌍의 집합', CK, false],
    ];
    let ly = 100;
    for (const [s, col, bold] of lines) {
        g.push(ctxt(452, ly, s, { col, bold, size: bold ? undefined : 'sm' }));
        ly += bold ? 27 : 24;
    }
    g.push(ln([[452, 344], [768, 344]], { stroke: CG, sw: 1 }));
    g.push(ctxt(452, 366, '술어의 뜻은 여기서 끝난다. ‘F 는 빨갛다는 뜻’ 같은', { col: CK, size: 'sm' }));
    g.push(ctxt(452, 384, '설명은 모형 안에 없다 — 고른 집합이 곧 뜻이다', { col: CK, size: 'sm' }));

    return {
        name: 'log-h-model-picture',
        svg: svg({
            width: W, height: H,
            title: '모형의 구성',
            desc: '왼쪽은 정의역의 점 셋과 술어가 고른 부분집합·화살표, 오른쪽은 같은 내용을 집합으로 적은 목록',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10-2. 배정을 한 자리만 갈아 끼운다
 * ================================================================== */
add((() => {
    const W = 800, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '정의역이 D = { 1, 2, 3 } 일 때의 배정 g 와, 그것을 한 자리만 고친 g[x ↦ 3]', { anchor: 'middle', cls: 'ink bold' }));

    const rows = [['x', '1', '3'], ['y', '2', '2'], ['z', '1', '1'], ['x₁', '3', '3']];

    function tbl(px0, py0, title, sub, colIdx, hi) {
        const out = [panel(px0, py0, 320, 214, title, sub)];
        const y0 = py0 + 58;
        out.push(ctxt(px0 + 78, y0, '변수', { anchor: 'middle', col: CK, size: 'sm' }));
        out.push(ctxt(px0 + 218, y0, 'D 안의 값', { anchor: 'middle', col: CK, size: 'sm' }));
        out.push(ln([[px0 + 30, y0 + 9], [px0 + 290, y0 + 9]], { stroke: CG, sw: 1 }));
        rows.forEach((row, i) => {
            const y = y0 + 36 + i * 30;
            const on = hi && i === 0;
            if (on) out.push(box(px0 + 30, y - 18, 260, 26, { fill: C2, op: 0.16, stroke: C2, sw: 1.2, rx: 4 }));
            out.push(ctxt(px0 + 78, y, row[0], { anchor: 'middle', col: CI, bold: true }));
            out.push(ctxt(px0 + 155, y, '↦', { anchor: 'middle', col: CK }));
            out.push(ctxt(px0 + 218, y, row[colIdx], { anchor: 'middle', col: on ? C2 : CI, bold: true }));
        });
        out.push(ctxt(px0 + 78, y0 + 156, '⋮', { anchor: 'middle', col: CK }));
        out.push(ctxt(px0 + 218, y0 + 156, '⋮', { anchor: 'middle', col: CK }));
        return out.join('');
    }

    g.push(tbl(14, 48, '배정 g', '변수는 무한히 많고 g 는 그 전부에 값을 준다', 1, false));
    g.push(tbl(466, 48, '고친 배정 g[x ↦ 3]', 'x 한 자리만 다르고 나머지는 g 와 같다', 2, true));

    g.push(arw(346, 152, 456, 152, { col: C2, marker: 'ar2', width: 2 }));
    g.push(ctxt(401, 138, 'x 자리만', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(401, 174, '나머지는', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(401, 190, '그대로', { anchor: 'middle', col: CK, size: 'sm' }));

    g.push(ln([[14, 286], [786, 286]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 308, '∀x 조항이 하는 일이 이 갈아 끼움이다 — D 의 원소 d 마다 g[x ↦ d] 를 만들어 확인한다.', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(W / 2, 326, '갈아 끼우는 자리가 하나뿐이므로 안쪽 한정사가 바깥 한정사의 변수를 흐트러뜨리지 않는다.', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-h-x-variant',
        svg: svg({
            width: W, height: H,
            title: '배정을 한 자리만 고치기',
            desc: '두 표가 x 행만 다르고 나머지 행은 같다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10-3. 한정사 조항은 정의역을 훑는 일이다
 * ================================================================== */
add((() => {
    const W = 800, H = 404;
    const g = [];
    g.push(txt(W / 2, 26, '한정사 조항은 이름이 아니라 D 의 원소를 훑는다', { anchor: 'middle', cls: 'ink bold' }));

    /* 왼쪽 — 확인에 쓰는 모형 */
    g.push(panel(14, 44, 218, 226, '쓰는 모형', 'D = { 1, 2, 3 },  I(F) = { 1, 2 }'));
    g.push(box(38, 96, 170, 160, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(ell(110, 152, 52, 28, { stroke: C1, sw: 1.6, fill: C1, op: 0.14 }));
    g.push(pt(88, 152, '1'));
    g.push(pt(132, 152, '2'));
    g.push(pt(170, 212, '3'));
    g.push(ctxt(110, 118, 'I(F)', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    g.push(ctxt(48, 216, '3 은 밖에 있다', { col: CK, size: 'sm' }));

    /* 오른쪽 — 훑기 표 */
    g.push(panel(248, 44, 538, 226, 'Fx 를 D 의 원소마다 확인한다 — 확인할 것은 세 줄뿐이다', null));
    const hx = [286, 420, 604, 742];
    g.push(ctxt(hx[0], 92, '훑는 원소 d', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(hx[1], 92, '갈아 낀 배정', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(hx[2], 92, '그 배정에서 Fx 는', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(hx[3], 92, '결과', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ln([[268, 102], [766, 102]], { stroke: CG, sw: 1 }));
    const scan = [
        ['1', 'g[x ↦ 1]', '1 ∈ I(F)', true],
        ['2', 'g[x ↦ 2]', '2 ∈ I(F)', true],
        ['3', 'g[x ↦ 3]', '3 ∉ I(F)', false],
    ];
    scan.forEach((row, i) => {
        const y = 134 + i * 42;
        g.push(box(268, y - 19, 498, 34, { fill: row[3] ? C1 : C2, op: 0.1, stroke: row[3] ? C1 : C2, sw: 1, rx: 4 }));
        g.push(ctxt(hx[0], y + 3, row[0], { anchor: 'middle', col: CI, bold: true }));
        g.push(ctxt(hx[1], y + 3, row[1], { anchor: 'middle', col: CI }));
        g.push(ctxt(hx[2], y + 3, row[2], { anchor: 'middle', col: CI }));
        g.push(ctxt(hx[3], y + 4, row[3] ? '✓' : '✗', { anchor: 'middle', col: row[3] ? C1 : C2, bold: true }));
    });

    /* 아래 — 두 한정사가 이 세 줄을 다르게 읽는다 */
    g.push(box(14, 288, 380, 100, { stroke: C2, sw: 1.4, rx: 6 }));
    g.push(ctxt(204, 314, '∀x Fx — 세 줄이 모두 ✓ 여야 한다', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(204, 338, '셋째 줄이 ✗ 이므로 거짓이다.', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(204, 358, '반례로 내놓을 것은 원소 3 하나다', { anchor: 'middle', col: CK, size: 'sm' }));

    g.push(box(406, 288, 380, 100, { stroke: C1, sw: 1.4, rx: 6 }));
    g.push(ctxt(596, 314, '∃x Fx — 한 줄만 ✓ 면 된다', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(596, 338, '첫째 줄에서 이미 끝났다. 참이다.', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(596, 358, '증거로 내놓을 것은 원소 1 하나다', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-h-satisfaction-scan',
        svg: svg({
            width: W, height: H,
            title: '한정사 조항의 훑기',
            desc: '정의역의 원소마다 배정을 갈아 끼워 확인한 세 줄을 ∀ 와 ∃ 가 서로 다르게 읽는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10-4. 정의역을 얼마나 키워야 하는가
 * ================================================================== */
add((() => {
    const W = 800, H = 388;
    const g = [];
    g.push(txt(W / 2, 26, '반례 모형의 정의역은 필요한 구별만큼만 키운다', { anchor: 'middle', cls: 'ink bold' }));

    function cell(x, title, sub) {
        return panel(x, 46, 250, 268, title, sub);
    }

    /* |D| = 1 */
    g.push(cell(14, '정의역이 한 점', '고를 수 있는 부분집합은 둘뿐'));
    g.push(box(46, 100, 186, 78, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(pt(139, 132, '1'));
    g.push(ctxt(139, 194, 'I(F) = ∅ 아니면 { 1 }', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ln([[36, 212], [242, 212]], { stroke: CG, sw: 1 }));
    g.push(ctxt(139, 234, '이것으로 깨지는 것', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(139, 258, '∃x Fx', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(139, 282, 'I(F) = ∅ 로 두면 거짓이다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* |D| = 2 */
    g.push(cell(275, '정의역이 두 점', '처음으로 안과 밖이 갈린다'));
    g.push(box(307, 100, 186, 78, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(ell(360, 138, 34, 26, { stroke: C1, sw: 1.5, fill: C1, op: 0.14 }));
    g.push(pt(360, 134, '1'));
    g.push(pt(452, 134, '2'));
    g.push(ctxt(400, 194, 'I(F) = { 1 } — 하나는 안, 하나는 밖', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ln([[297, 212], [503, 212]], { stroke: CG, sw: 1 }));
    g.push(ctxt(400, 234, '이것으로 깨지는 것', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(400, 258, '∃x Fx → ∀x Fx', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(400, 282, '앞은 참인데 뒤가 거짓이다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* |D| = 3 */
    g.push(cell(536, '정의역이 세 점', '처음으로 건너뛰기가 막힌다'));
    g.push(box(568, 100, 186, 78, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(pt(600, 134, '1'));
    g.push(pt(661, 134, '2'));
    g.push(pt(722, 134, '3'));
    g.push(arw(609, 132, 648, 132, { col: C2, marker: 'ar2', width: 1.6 }));
    g.push(arw(670, 132, 709, 132, { col: C2, marker: 'ar2', width: 1.6 }));
    g.push(`<path fill="none" stroke="${CG}" stroke-width="1.4" stroke-dasharray="5 4" d="M600 148 Q661 190 722 148"/>`);
    g.push(ctxt(661, 184, '✗', { anchor: 'middle', col: CK, bold: true }));
    g.push(ctxt(661, 194, '1 에서 3 으로 가는 화살표는 없다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ln([[558, 212], [764, 212]], { stroke: CG, sw: 1 }));
    g.push(ctxt(661, 234, '이것으로 깨지는 것', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(661, 258, '∀x∀y∀z (Gxy ∧ Gyz → Gxz)', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(661, 282, '두 점으로는 이 반례를 만들 수 없다', { anchor: 'middle', col: CK, size: 'sm' }));

    g.push(ln([[14, 334], [786, 334]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 356, '먼저 무엇을 구별해야 하는지 세고, 그만큼의 점만 놓는다. 대부분 셋을 넘지 않는다.', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(W / 2, 376, '큰 정의역이 필요해 보이면 대개 구별을 잘못 센 것이다.', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-h-small-countermodel',
        svg: svg({
            width: W, height: H,
            title: '작은 정의역으로 만드는 반례 모형',
            desc: '정의역의 점을 하나씩 늘리며 각 크기에서 처음으로 가릴 수 있게 되는 것',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10-5. 한정사 순서를 가르는 모형
 * ================================================================== */
add((() => {
    const W = 800, H = 430;
    const g = [];
    g.push(txt(W / 2, 26, '∀x∃y Gxy 와 ∃y∀x Gxy 를 가르는 것은 ‘모두를 받는 점’ 이 있는가다', { anchor: 'middle', cls: 'ink bold' }));

    /* 왼쪽 모형 */
    g.push(panel(14, 46, 380, 274, '모형 ℳ1', 'D = { 1, 2 },  I(G) = { (1,1), (2,2) }'));
    g.push(box(60, 106, 290, 106, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(pt(140, 170, '1'));
    g.push(pt(270, 170, '2'));
    g.push(loop(140, 162, 13, { col: C2, marker: 'ar2' }));
    g.push(loop(270, 162, 13, { col: C2, marker: 'ar2' }));
    g.push(ctxt(205, 124, '화살표가 제자리로만 돌아온다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(204, 238, '∀x∃y Gxy — 참', { anchor: 'middle', col: C1, bold: true }));
    g.push(ctxt(204, 258, 'x = 1 은 y = 1 로, x = 2 는 y = 2 로 받는다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(204, 284, '∃y∀x Gxy — 거짓', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(204, 304, 'y = 1 은 x = 2 에서, y = 2 는 x = 1 에서 막힌다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* 오른쪽 모형 */
    g.push(panel(406, 46, 380, 274, '모형 ℳ2', 'D = { 1, 2 },  I(G) = { (1,1), (2,1) }'));
    g.push(box(452, 106, 290, 106, { stroke: CG, sw: 1.2, rx: 8, dash: '6 4' }));
    g.push(ell(532, 170, 27, 25, { stroke: C1, sw: 1.6, fill: C1, op: 0.14 }));
    g.push(pt(532, 170, '1'));
    g.push(pt(662, 170, '2'));
    g.push(loop(532, 162, 13, { col: C2, marker: 'ar2' }));
    g.push(arw(650, 170, 566, 170, { col: C2, marker: 'ar2' }));
    g.push(ctxt(597, 124, '점 1 이 모든 화살표를 받는다', { anchor: 'middle', col: C1, size: 'sm', bold: true }));
    g.push(ctxt(596, 238, '∀x∃y Gxy — 참', { anchor: 'middle', col: C1, bold: true }));
    g.push(ctxt(596, 258, 'y = 1 하나로 x 둘을 모두 받는다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(596, 284, '∃y∀x Gxy — 참', { anchor: 'middle', col: C1, bold: true }));
    g.push(ctxt(596, 304, '먼저 고른 y 하나가 x 를 모두 받아 낸다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* 아래 결론 */
    g.push(box(14, 340, 772, 78, { stroke: C2, sw: 1.4, rx: 6 }));
    g.push(ctxt(400, 366, 'ℳ1 하나가 ∀x∃y Gxy ⊨ ∃y∀x Gxy 를 깬다 — 전제가 참인데 결론이 거짓인 모형이 실제로 있다', { anchor: 'middle', col: CI, bold: true }));
    g.push(ctxt(400, 390, '반대 방향 ∃y∀x Gxy ⊨ ∀x∃y Gxy 는 어떤 모형에서도 깨지지 않는다.', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(400, 408, '모두를 받는 점이 하나 있으면 각 x 는 그것을 고르면 되기 때문이다.', { anchor: 'middle', col: CK, size: 'sm' }));

    return {
        name: 'log-h-order-split',
        svg: svg({
            width: W, height: H,
            title: '한정사 순서를 가르는 두 모형',
            desc: '왼쪽 모형에는 모든 화살표를 받는 점이 없고 오른쪽 모형에는 있다',
            body: g.join(''),
        }),
    };
})());

export default figures;
