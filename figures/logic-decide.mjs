/**
 * 논리학 16장(결정불가능성)의 그림.
 *
 * 이름은 모두 `log-n-` 으로 시작한다(16장 담당자에게 배정된 접두어).
 * 상자와 화살표만으로 되는 도식은 `d2/logic/log-n-*.d2` 에 있고, 여기에는
 * 자리를 손으로 잡아야 하는 것(테이프 칸과 헤드, 한 걸음 앞뒤 비교,
 * 세 겹으로 포개진 문제의 영역)만 둔다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ ⊥ ⊢ ⊨ ∀ ∃ ℳ 로 직접 적는다.
 * 큰따옴표와 HTML 엔티티는 쓸 수 없으므로 ‘ ’ 를 쓴다.
 * lib 의 esc 가 `~` 를 아래첨자로 먹으므로 그 밖의 자리에 물결표를 쓰지 않는다.
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
 * 소도구 — lib 의 px() 는 색을 클래스로 넘기는데 그 클래스가 SVG 안에
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

function ell(cx, cy, rx, ry, { stroke = CG, sw = 1.4, fill = 'none', op = 1, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 색을 직접 넣는 글자. lib 의 txt 는 클래스만 받는다. */
function ctxt(x, y, str, { anchor = 'start', col = CI, size, bold } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" fill="${col}"`
        + `${size === 'sm' ? ' font-size="11"' : ''}${size === 'lg' ? ' font-size="16"' : ''}${bold ? ' font-weight="600"' : ''}>${esc(str)}</text>`;
}

/** 패널 테두리와 제목. */
function panel(x, y, w, h, title, sub, { stroke = CG } = {}) {
    return box(x, y, w, h, { stroke, sw: 1, rx: 6 })
        + (title ? ctxt(x + w / 2, y + 20, title, { anchor: 'middle', col: CI, size: 'sm', bold: true }) : '')
        + (sub ? ctxt(x + w / 2, y + 37, sub, { anchor: 'middle', col: CK, size: 'sm' }) : '');
}

/** 테이프 한 줄. cells 는 칸에 적을 글자 배열, mark 는 강조할 칸의 번호들. */
function tape(x0, y, cells, cw, ch, { mark = [], markCol = C2 } = {}) {
    const g = [];
    cells.forEach((s, i) => {
        const x = x0 + i * cw;
        const on = mark.includes(i);
        g.push(box(x, y, cw, ch, {
            fill: on ? markCol : 'none', op: on ? 0.18 : 1,
            stroke: on ? markCol : CG, sw: on ? 1.8 : 1.2, rx: 2,
        }));
        g.push(ctxt(x + cw / 2, y + ch / 2 + 6, s, { anchor: 'middle', col: on ? markCol : CI, bold: on }));
    });
    return g.join('');
}

/** 위를 가리키는 헤드 삼각형. */
function head(cx, yTop, { col = C1 } = {}) {
    const w = 11, h = 14;
    return `<path d="M${r2(cx)} ${r2(yTop)} L${r2(cx - w)} ${r2(yTop + h)} L${r2(cx + w)} ${r2(yTop + h)} z" fill="${col}"/>`;
}

/* ================================================================== *
 * 16-1. 튜링 기계의 부품 — 무엇이 유한하고 무엇이 무한한가
 * ================================================================== */
add((() => {
    const W = 820, H = 500;
    const g = [];
    g.push(ctxt(W / 2, 26, '튜링 기계 M — 부품은 넷이고 그중 무한한 것은 테이프 하나뿐이다', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 테이프 ---- */
    const cells = ['…', '□', '1', '0', '1', '1', '0', '□', '□', '□', '…'];
    const cw = 54, chh = 46, x0 = 113, ty = 62;
    g.push(tape(x0, ty, cells, cw, chh, { mark: [4] }));
    g.push(ctxt(x0 - 12, ty + 30, '왼쪽으로', { anchor: 'end', col: CK, size: 'sm' }));
    g.push(ctxt(x0 + cells.length * cw + 12, ty + 30, '오른쪽으로', { col: CK, size: 'sm' }));
    g.push(ctxt(W / 2, ty - 12, '테이프 — 칸마다 기호 하나. 거의 모든 칸이 빈칸 □ 이고 양쪽으로 끝이 없다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* ---- 헤드와 상태 ---- */
    const hx = x0 + 4 * cw + cw / 2;
    g.push(head(hx, ty + chh + 4));
    g.push(ln([[hx, ty + chh + 18], [hx, ty + chh + 40]], { stroke: C1, sw: 1.8 }));
    g.push(box(hx - 92, ty + chh + 40, 184, 34, { stroke: C1, sw: 1.6, rx: 5 }));
    g.push(ctxt(hx, ty + chh + 62, '헤드 — 이 칸 하나를 본다', { anchor: 'middle', col: C1, size: 'sm', bold: true }));

    g.push(ell(72, ty + chh + 57, 46, 26, { stroke: C3, sw: 1.8, fill: C3, op: 0.14 }));
    g.push(ctxt(72, ty + chh + 55, '상태', { anchor: 'middle', col: C3, size: 'sm' }));
    g.push(ctxt(72, ty + chh + 72, 'q~1', { anchor: 'middle', col: C3, bold: true }));
    g.push(ctxt(72, ty + chh + 104, '유한 개 가운데 하나', { anchor: 'middle', col: CK, size: 'sm' }));

    /* ---- 전이표 ---- */
    const px0 = 14, py0 = 226, pw = 400, ph = 250;
    g.push(panel(px0, py0, pw, ph, '전이표 δ — 기계 M 은 이 표가 전부다', '지금 상태와 읽은 기호로 줄 하나가 정해진다'));
    const cols = [46, 122, 210, 288, 356];
    g.push(ctxt(cols[0], py0 + 66, '상태', { col: CK, size: 'sm' }));
    g.push(ctxt(cols[1], py0 + 66, '읽음', { col: CK, size: 'sm' }));
    g.push(ctxt(cols[2], py0 + 66, '새 상태', { col: CK, size: 'sm' }));
    g.push(ctxt(cols[3], py0 + 66, '쓸 기호', { col: CK, size: 'sm' }));
    g.push(ctxt(cols[4], py0 + 66, '이동', { col: CK, size: 'sm' }));
    g.push(ln([[px0 + 20, py0 + 76], [px0 + pw - 20, py0 + 76]], { stroke: CG, sw: 1 }));
    const rows = [
        ['q~0', '1', 'q~1', '1', '오른쪽'],
        ['q~1', '1', 'q~2', '0', '오른쪽'],
        ['q~1', '0', 'q~1', '0', '오른쪽'],
        ['q~2', '□', 'q~{halt}', '1', '왼쪽'],
    ];
    rows.forEach((r, i) => {
        const y = py0 + 106 + i * 28;
        const on = i === 1;
        if (on) g.push(box(px0 + 20, y - 18, pw - 40, 26, { fill: C2, op: 0.16, stroke: C2, sw: 1.2, rx: 3 }));
        r.forEach((s, j) => g.push(ctxt(cols[j], y, s, { col: on ? C2 : CI, size: 'sm', bold: on })));
    });
    g.push(ctxt(px0 + pw / 2, py0 + ph - 16, '지금 q~1 이고 1 을 읽었으므로 색칠한 줄이 다음 걸음이다',
        { anchor: 'middle', col: C2, size: 'sm', bold: true }));

    /* ---- 유한과 무한 ---- */
    const qx = 428, qw = 378;
    g.push(panel(qx, py0, qw, ph, '무엇이 유한하고 무엇이 무한한가', '이 갈라짐이 다음 절 전체를 떠받친다'));
    const items = [
        ['상태 집합 Q', '유한', C3],
        ['기호 집합 Σ (□ 포함)', '유한', C3],
        ['전이표 δ 의 줄 수', '유한', C3],
        ['테이프의 칸', '무한', C2],
        ['한 계산이 밟는 걸음 수', '유한할 수도, 아닐 수도', C2],
    ];
    items.forEach(([name, kind, col], i) => {
        const y = py0 + 80 + i * 25;
        g.push(ctxt(qx + 22, y, name, { col: CI, size: 'sm' }));
        g.push(ctxt(qx + qw - 22, y, kind, { anchor: 'end', col, size: 'sm', bold: true }));
    });
    g.push(ln([[qx + 18, py0 + 194], [qx + qw - 18, py0 + 194]], { stroke: CG, sw: 1 }));
    g.push(ctxt(qx + qw / 2, py0 + 216, '유한한 것만 적으면 기계 하나가 온전히 적힌다.', { anchor: 'middle', col: CI, size: 'sm' }));
    g.push(ctxt(qx + qw / 2, py0 + 234, '그래서 기계도 문자열이고, 다른 기계의 입력이 된다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-n-turing-machine',
        svg: svg({
            width: W, height: H,
            title: '튜링 기계의 네 부품',
            desc: '테이프와 헤드와 상태와 전이표를 한 장에 놓고, 유한한 것과 무한한 것을 갈라 적었다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 16-2. 한 걸음이 바꾸는 것은 셋뿐이다
 * ================================================================== */
add((() => {
    const W = 900, H = 372;
    const g = [];
    g.push(ctxt(W / 2, 26, '전이 한 번 — 바뀌는 것은 칸 하나와 헤드 자리와 상태, 이 셋뿐이다', { anchor: 'middle', col: CI, bold: true }));

    const cw = 52, chh = 44, x0 = 168;
    const before = ['□', '1', '0', '1', '1', '0', '□', '□'];
    const after = ['□', '1', '0', '1', '0', '0', '□', '□'];

    /* ---- 앞 ---- */
    const y1 = 66;
    g.push(ctxt(x0 - 24, y1 + 28, '앞', { anchor: 'end', col: CK, bold: true }));
    g.push(tape(x0, y1, before, cw, chh, { mark: [4] }));
    const hx = x0 + 4 * cw + cw / 2;
    g.push(head(hx, y1 + chh + 4));
    g.push(ell(x0 - 96, y1 + 22, 34, 20, { stroke: C3, sw: 1.7, fill: C3, op: 0.14 }));
    g.push(ctxt(x0 - 96, y1 + 27, 'q~1', { anchor: 'middle', col: C3, bold: true }));

    /* ---- 규칙 ---- */
    const ry = 176;
    g.push(box(x0 + 12, ry - 24, 356, 46, { stroke: C2, sw: 1.6, rx: 5, fill: C2, op: 0.1 }));
    g.push(ctxt(x0 + 190, ry, 'δ(q~1, 1) = (q~2, 0, 오른쪽)', { anchor: 'middle', col: C2, bold: true }));
    g.push(ctxt(x0 + 190, ry + 17, '읽은 것이 1 이므로 이 줄이 쓰인다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(arw(hx, y1 + chh + 20, hx, ry - 30, { col: CK, width: 1.5 }));
    g.push(arw(hx, ry + 26, hx, 240, { col: CK, width: 1.5 }));

    /* ---- 뒤 ---- */
    const y2 = 250;
    g.push(ctxt(x0 - 24, y2 + 28, '뒤', { anchor: 'end', col: CK, bold: true }));
    g.push(tape(x0, y2, after, cw, chh, { mark: [4] }));
    g.push(head(x0 + 5 * cw + cw / 2, y2 + chh + 4));
    g.push(ell(x0 - 96, y2 + 22, 34, 20, { stroke: C3, sw: 1.7, fill: C3, op: 0.14 }));
    g.push(ctxt(x0 - 96, y2 + 27, 'q~2', { anchor: 'middle', col: C3, bold: true }));

    /* ---- 셋 짚기 ---- */
    const notes = [
        ['1 을 0 으로 고쳐 썼다', C2],
        ['헤드가 한 칸 오른쪽으로 갔다', C1],
        ['상태가 q~1 에서 q~2 로 바뀌었다', C3],
    ];
    notes.forEach(([s, col], i) => g.push(ctxt(x0 + 8 * cw + 26, y2 + 6 + i * 20, s, { col, size: 'sm', bold: true })));
    g.push(ctxt(x0 + 8 * cw + 26, y2 + 72, '나머지 칸은 손대지 않는다', { col: CK, size: 'sm' }));

    g.push(ln([[20, 344], [W - 20, 344]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 364, '이 걸음을 되풀이한다. 정지 상태에 닿으면 멈추고, 닿지 않으면 영원히 이어진다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-n-tm-step',
        svg: svg({
            width: W, height: H,
            title: '튜링 기계의 한 걸음',
            desc: '전이표의 줄 하나가 칸 하나와 헤드 자리와 상태를 바꾸는 모습을 앞뒤로 나란히 놓았다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 16-3. 세 겹 — 결정 가능 ⊂ 반결정 가능 ⊂ 모든 판정 문제
 * ================================================================== */
add((() => {
    const W = 830, H = 484;
    const g = [];
    g.push(ctxt(W / 2, 26, '판정 문제의 세 겹 — 여집합을 취하면 자리가 어떻게 움직이는가', { anchor: 'middle', col: CI, bold: true }));

    /* ---- 세 겹 ---- */
    g.push(box(26, 46, 560, 386, { stroke: CK, sw: 1.5, rx: 14 }));
    g.push(ctxt(44, 70, '모든 판정 문제', { col: CK, size: 'sm', bold: true }));

    g.push(box(52, 88, 508, 322, { stroke: C2, sw: 1.8, rx: 12, fill: C2, op: 0.08 }));
    g.push(ctxt(70, 112, '반결정 가능 — ‘예’ 이면 언젠가 멈춘다', { col: C2, size: 'sm', bold: true }));

    g.push(box(80, 130, 452, 176, { stroke: C3, sw: 1.8, rx: 10, fill: C3, op: 0.12 }));
    g.push(ctxt(98, 154, '결정 가능 — 어느 입력에서도 반드시 멈춘다', { col: C3, size: 'sm', bold: true }));

    /* 안쪽 항목 */
    const inner = [
        '명제논리에서 φ 가 항진명제인가',
        '적힌 도출 한 벌이 규칙을 지키는가',
        '단항 술어논리에서 ⊨ φ 인가',
    ];
    inner.forEach((s, i) => g.push(ctxt(104, 184 + i * 24, '· ' + s, { col: CI, size: 'sm' })));
    g.push(ctxt(104, 284, '여집합도 여기 남는다 — 답을 뒤집으면 된다', { col: C3, size: 'sm', bold: true }));

    /* 가운데 띠 항목 */
    const mid = [
        '정지 문제 — M 을 w 에 돌리면 멈추는가',
        '술어논리에서 ⊨ φ 인가 — 결정 문제',
    ];
    mid.forEach((s, i) => g.push(ctxt(104, 336 + i * 24, '· ' + s, { col: CI, size: 'sm' })));
    g.push(ctxt(104, 392, '여기 있는 것의 여집합은 바깥으로 튕겨 나간다', { col: C2, size: 'sm', bold: true }));

    /* ---- 바깥 항목 ---- */
    g.push(panel(594, 46, 222, 392, '바깥 — 반결정조차 안 된다', ''));
    const outer = [
        ['M 이 w 에서 안 멈추는가', '정지 문제의 여집합'],
        ['⊨ φ 가 아닌가', '결정 문제의 여집합'],
        ['φ 가 만족가능한가', '⊨ ¬φ 의 여집합'],
    ];
    outer.forEach(([s, sub], i) => {
        const y = 92 + i * 62;
        g.push(box(606, y, 196, 48, { stroke: CG, sw: 1.2, rx: 5 }));
        g.push(ctxt(704, y + 20, s, { anchor: 'middle', col: CI, size: 'sm' }));
        g.push(ctxt(704, y + 37, sub, { anchor: 'middle', col: CK, size: 'sm' }));
    });
    g.push(ctxt(704, 306, '이 셋은 mcs 가 대각선 논법으로', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(704, 322, '건드린 자리와 같은 층이다', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(box(606, 338, 196, 86, { stroke: C2, sw: 1.4, rx: 5 }));
    g.push(ctxt(704, 360, '둘 다 반결정 가능이면', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(704, 377, '결정 가능해진다', { anchor: 'middle', col: C2, size: 'sm', bold: true }));
    g.push(ctxt(704, 398, '그래서 가운데 띠의 여집합은', { anchor: 'middle', col: CK, size: 'sm' }));
    g.push(ctxt(704, 414, '반드시 바깥이다', { anchor: 'middle', col: CK, size: 'sm' }));

    /* 여집합 화살표 */
    g.push(arw(452, 348, 598, 116, { col: C2, marker: 'ar2', width: 1.6, dash: '6 4' }));
    g.push(arw(452, 360, 598, 178, { col: C2, marker: 'ar2', width: 1.6, dash: '6 4' }));

    g.push(ln([[26, 454], [W - 26, 454]], { stroke: CG, sw: 1 }));
    g.push(ctxt(W / 2, 474, '읽어야 할 것은 세 겹이 진짜로 겹겹이라는 것 — 가운데 띠가 비어 있지 않다는 사실이 이 장의 결과다', { anchor: 'middle', col: CI, size: 'sm', bold: true }));

    return {
        name: 'log-n-three-classes',
        svg: svg({
            width: W, height: H,
            title: '결정 가능과 반결정 가능의 세 겹',
            desc: '결정 가능이 반결정 가능 안에 있고 그 바깥에 반결정조차 안 되는 문제가 있다. 여집합을 취하면 가운데 띠의 문제는 바깥으로 나간다',
            body: g.join(''),
        }),
    };
})());

export default figures;
