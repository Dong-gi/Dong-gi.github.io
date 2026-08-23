/**
 * 철학 문서 10·11장(무엇이 있는가 / 인과·법칙·양상)의 그림.
 *
 * 이름은 모두 `phi-p-` 로 시작한다(형이상학 두 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 양상 기호는 유니코드 □ ◇ 로, 한정사는 ∃ ∀ 로 적는다. 반사실 조건문은 □→ 로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 `w~1`, `H~2O` 는 첨자로 내려가고,
 * 그 밖의 자리에서는 물결표를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 이 두 장의 그림은 상자와 화살표보다 ‘칸을 갈라 보여 주는 것’ 이 많다. 형이상학의
 * 다툼은 대개 같은 장면을 다르게 읽는 다툼이라, 장면을 한 번 그려 놓고 읽기를
 * 나란히 놓는 것이 문단 여러 개보다 낫다. 계보와 입장 지도는 d2/philosophy/ 에 있다.
 */
import { svg, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = CK, sw = 1.5, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * 화살표. lib 의 px() 는 클래스로 색을 주는데 여기서는 색을 직접 넣는 편이
 * 마커 색과 맞추기 쉽다. 마커 넷은 lib 의 svg() 가 defs 에 넣어 둔 것이다.
 */
function arw(x1, y1, x2, y2, { col = CK, marker = 'ark', sw = 1.5, dash } = {}) {
    return `<path d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}" fill="none" stroke="${col}"`
        + ` stroke-width="${sw}" stroke-linecap="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} marker-end="url(#${marker})"/>`;
}

const pdot = (x, y, col = C2, r = 5) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const circ = (cx, cy, r, { fill = 'none', op = 1, stroke = CK, sw = 1.5 } = {}) =>
    `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}"`
    + ` stroke="${stroke}" stroke-width="${sw}"/>`;

/** 여러 줄 글. 줄 간격은 화소로 준다. */
function lines(x, y, arr, { gap = 19, anchor = 'start', cls = 'ink2', size = 'sm' } = {}) {
    return arr.map((s, i) => txt(x, y + i * gap, s, { anchor, cls, size })).join('');
}

/* ================================================================== *
 * 10장 — 무엇이 있는가
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 빨간 사과 둘 — 같은 장면, 두 가지 읽기
 *
 * 이 장을 여는 그림이다. 두 입장이 관찰에서 갈리는 것이 아니라 관찰을 무엇으로
 * 설명하느냐에서 갈린다는 것을 보이려는 것이다. 그래서 사과는 양쪽에 똑같이 그린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 398;
    const g = [];

    g.push(txt(36, 26, '빨간 사과 둘 — 같은 장면을 두 가지로 읽는다', { cls: 'ink bold' }));

    // 왼쪽 — 실재론
    g.push(box(36, 44, 376, 252, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(224, 70, '실재론 — 하나가 여럿에 걸쳐 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(box(139, 86, 170, 34, { fill: C1, op: 0.14, stroke: C1, sw: 1.6 }));
    g.push(txt(224, 108, '보편자 ‘빨강’ 하나', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(190, 122, 158, 166, { col: C1, marker: 'ar1' }));
    g.push(arw(258, 122, 290, 166, { col: C1, marker: 'ar1' }));
    g.push(circ(150, 198, 28, { fill: C2, op: 0.3, stroke: C2 }));
    g.push(circ(298, 198, 28, { fill: C2, op: 0.3, stroke: C2 }));
    g.push(txt(150, 248, '사과 a', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(298, 248, '사과 b', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(224, 278, '같은 것 하나를 둘이 나눠 가진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 유명론
    g.push(box(444, 44, 376, 252, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(632, 70, '유명론 — 그런 것은 없다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(632, 108, '보편자가 놓일 자리를 비워 둔다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(box(500, 158, 264, 104, { stroke: C3, sw: 1.6, dash: '6 4', rx: 10 }));
    g.push(circ(558, 198, 28, { fill: C2, op: 0.3, stroke: C2 }));
    g.push(circ(706, 198, 28, { fill: C2, op: 0.3, stroke: C2 }));
    g.push(txt(558, 248, '사과 a', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(706, 248, '사과 b', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(632, 278, '‘빨강’ 은 이 둘을 함께 부르는 말이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(lines(36, 328, [
        '두 입장은 보이는 것을 두고 다투지 않는다. 사과가 둘 있고 둘 다 빨갛다는 데는 합의한다',
        '다투는 것은 그 사실을 참으로 만드는 목록에 항목이 하나 더 있느냐다. 실재론은 둔다. 유명론은 두지 않는다',
        '그래서 이 다툼은 사과를 더 자세히 들여다본다고 풀리지 않는다. 여기가 형이상학이 경험 과학과 갈리는 자리다',
    ]));

    return {
        name: 'phi-p-two-apples',
        svg: svg({
            width: W, height: H,
            title: '빨간 사과 둘을 읽는 두 가지 방식',
            desc: '실재론은 두 사과 위에 보편자 하나를 두고 유명론은 그 자리를 비워 둔다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 유형과 사례 — ‘몇 개 있는가’ 는 두 물음이다
 *
 * 이 구분은 14장에서 유형 동일론·사례 동일론을 가를 때 그대로 쓰인다.
 * 세는 물음을 실제로 두 번 던져 보게 하는 것이 정의를 적는 것보다 낫다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 372;
    const g = [];

    g.push(txt(36, 26, '‘몇 개 있는가’ 는 사실 두 물음이다', { cls: 'ink bold' }));

    // 왼쪽 — 글자
    g.push(box(36, 44, 366, 244, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(219, 70, '낱말 ‘바나나’ 의 글자를 센다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const cells = [103, 187, 271];
    const chs = ['바', '나', '나'];
    cells.forEach((x, i) => {
        g.push(box(x, 88, 64, 48, { fill: C2, op: 0.12, stroke: C2, sw: 1.4 }));
        g.push(txt(x + 32, 118, chs[i], { anchor: 'middle', cls: 'ink' }));
    });
    g.push(txt(219, 156, '사례 셋', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(219, 166, 219, 194, { col: CK }));
    [143, 231].forEach((x, i) => {
        g.push(box(x, 198, 64, 44, { fill: C1, op: 0.12, stroke: C1, sw: 1.4 }));
        g.push(txt(x + 32, 226, ['바', '나'][i], { anchor: 'middle', cls: 'ink' }));
    });
    g.push(txt(219, 266, '유형 둘', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽 — 책
    g.push(box(434, 44, 366, 244, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(617, 70, '서점에 놓인 같은 책을 센다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < 5; i += 1) {
        g.push(box(499 + i * 50, 88, 36, 48, { fill: C2, op: 0.12, stroke: C2, sw: 1.4 }));
    }
    g.push(txt(617, 156, '사례 다섯', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(617, 166, 617, 194, { col: CK }));
    g.push(box(557, 198, 120, 44, { fill: C1, op: 0.12, stroke: C1, sw: 1.4 }));
    g.push(txt(617, 226, '그 책', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(617, 266, '유형 하나', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(lines(36, 318, [
        '왼쪽에 셋이라 답하면 사례를 센 것이고 둘이라 답하면 유형을 센 것이다. 둘 다 맞다 — 다른 것을 센 것뿐이다',
        '유형은 보편자 쪽에, 사례는 개별자 쪽에 놓인다. 그래서 유명론자는 유형을 말하는 문장을 다시 써야 할 빚을 진다',
        '이 구분은 14장에서 유형 동일론과 사례 동일론을 가를 때 그대로 쓰인다',
    ]));

    return {
        name: 'phi-p-type-token',
        svg: svg({
            width: W, height: H,
            title: '유형과 사례 — 세는 물음이 둘이다',
            desc: '바나나의 글자는 사례로 셋 유형으로 둘이고 서점의 같은 책은 사례로 다섯 유형으로 하나다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 구체와 추상 — 기준 둘이 어긋나는 칸이 있다
 *
 * 추상적 대상을 ‘시공간 밖에 있는 것’ 하나로만 정의하면 다투는 사례가 보이지 않는다.
 * 기준을 둘로 갈라 격자를 만들면 대각선이 편안하고 반대각선이 싸움터라는 것이 드러난다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 382;
    const g = [];
    const cx = [196, 512];
    const cw = 300;
    const ry = [92, 192];
    const rh = 96;

    g.push(txt(36, 26, '구체와 추상을 가르는 기준은 둘인데 늘 같이 가지는 않는다', { cls: 'ink bold' }));

    g.push(txt(346, 74, '시공간에 자리가 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(662, 74, '시공간에 자리가 없다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(178, 138, '인과에 관여한다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(178, 238, '인과에 관여하지 않는다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));

    const cell = (c, r, col, head, body) => {
        const x = cx[c];
        const y = ry[r];
        g.push(box(x, y, cw, rh, { fill: col, op: col === CG ? 0.3 : 0.1, stroke: col === CG ? CK : col, sw: col === CG ? 1.2 : 1.8 }));
        g.push(txt(x + 14, y + 26, head, { cls: 'ink bold', size: 'sm' }));
        g.push(lines(x + 14, y + 48, body, { gap: 18 }));
    };

    cell(0, 0, C1, '구체적 대상', ['이 사과, 이 책상,', '어제 친 천둥']);
    cell(1, 0, CG, '다투는 자리', ['시공간 밖에서 인과에 관여한다는 것 —', '신, 데카르트의 정신(14장)']);
    cell(0, 1, CG, '다투는 자리', ['그림자·구멍처럼 자리는 있는데', '스스로 무엇을 하지는 않아 보이는 것']);
    cell(1, 1, C2, '추상적 대상', ['수 7, 집합, 명제,', '삼각형 그 자체']);

    g.push(lines(36, 320, [
        '대각선 두 칸은 편안하다. 사과는 여기 있고 무엇을 하며, 수 7 은 어디에도 없고 아무것도 하지 않는다',
        '문제는 반대각선이다. 기준 둘이 어긋나는 사례가 있으므로 ‘추상적’ 은 한 낱말로 정의되지 않는다',
        '10장에서 다투는 것은 오른쪽 아래 칸이 비어 있느냐다. 유명론은 비었다고 하고 실재론은 차 있다고 한다',
    ]));

    return {
        name: 'phi-p-abstract-concrete',
        svg: svg({
            width: W, height: H,
            title: '구체와 추상을 가르는 두 기준',
            desc: '시공간 자리와 인과 관여를 두 축으로 놓으면 대각선은 편안하고 반대각선이 다투는 자리다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 테세우스의 배 — 두 계보가 갈라진다
 *
 * 줄거리만 적으면 아무 일도 일어나지 않는다. 시간축 위에 계보를 둘 그려 놓고
 * 끝에서 후보가 둘이 된다는 것을 눈으로 보게 하는 것이 이 그림의 목적이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 852;
    const H = 344;
    const g = [];

    g.push(txt(36, 26, '테세우스의 배 — 계보가 둘로 갈라진다', { cls: 'ink bold' }));
    g.push(txt(36, 50, '수리 계보 — 해마다 널빤지 하나씩을 새것으로 간다', { cls: 'ink2', size: 'sm' }));

    const ships = [
        { x: 40, t: '시점 t0', s: '원래 널빤지 전부', k: 0 },
        { x: 182, t: '시점 t1', s: '하나만 새것', k: 1 },
        { x: 324, t: '시점 t2', s: '절반이 새것', k: 2 },
        { x: 466, t: '시점 t3', s: '전부 새것', k: 4 },
    ];
    for (const s of ships) {
        g.push(box(s.x, 62, 110, 62, { stroke: CK, sw: 1.3 }));
        g.push(txt(s.x + 55, 82, s.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(s.x + 55, 100, s.s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const bw = 86;
        const bx = s.x + 12;
        const oldw = (bw * (4 - s.k)) / 4;
        if (oldw > 0) g.push(box(bx, 108, oldw, 8, { fill: C1, op: 0.75, stroke: C1, sw: 0.8, rx: 2 }));
        if (bw - oldw > 0) g.push(box(bx + oldw, 108, bw - oldw, 8, { fill: C2, op: 0.75, stroke: C2, sw: 0.8, rx: 2 }));
    }
    g.push(arw(150, 93, 180, 93, { col: CK }));
    g.push(arw(292, 93, 322, 93, { col: CK }));
    g.push(arw(434, 93, 464, 93, { col: CK }));
    g.push(arw(576, 93, 622, 93, { col: C1, marker: 'ar1' }));

    g.push(box(624, 62, 190, 62, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
    g.push(txt(719, 86, '배 A', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(719, 108, '계속 고쳐 온 배', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(237, 128, 237, 178, { col: CK, dash: '4 3' }));
    g.push(arw(379, 128, 379, 178, { col: CK, dash: '4 3' }));
    g.push(arw(521, 128, 521, 178, { col: CK, dash: '4 3' }));
    g.push(txt(196, 154, '빼낸 널빤지', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(box(182, 180, 394, 48, { stroke: CK, sw: 1.3, dash: '5 4' }));
    g.push(txt(379, 209, '창고 — 빼낸 널빤지를 하나도 버리지 않고 모아 둔다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(578, 204, 622, 204, { col: C2, marker: 'ar2' }));

    g.push(box(624, 180, 190, 48, { fill: C2, op: 0.1, stroke: C2, sw: 1.8 }));
    g.push(txt(719, 200, '배 B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(719, 219, '모아 둔 널빤지로 조립한 배', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(lines(36, 262, [
        '배 A 는 자리와 쓰임과 역사가 한 번도 끊기지 않았고, 배 B 는 재료가 처음 그대로다. 둘 다 ‘그 배’ 라 부를 이유가 있다',
        '그런데 t3 에는 둘이 함께 있다. 한 대상이 둘일 수는 없으므로 적어도 하나는 그 배가 아니다',
        '이 그림이 묻는 것은 배가 아니라 시간을 통한 동일성의 기준이다. 13장이 같은 물음을 사람에 대해 되풀이한다',
    ]));

    return {
        name: 'phi-p-ship-timeline',
        svg: svg({
            width: W, height: H,
            title: '테세우스의 배의 두 계보',
            desc: '고쳐 온 배와 빼낸 널빤지로 조립한 배가 마지막 시점에 함께 있어 후보가 둘이 된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 11장 — 인과·법칙·양상
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 5. 당구공 — 보이는 것과 보이지 않는 것
 *
 * 흄의 논점은 ‘인과가 없다’ 가 아니라 ‘필연적 연결에 해당하는 인상이 없다’ 이다.
 * 목록에 체크 셋과 엑스 하나를 놓아 그 비대칭을 한눈에 보이게 한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 384;
    const g = [];

    g.push(txt(36, 26, '당구공이 부딪친다 — 무엇이 보이는가', { cls: 'ink bold' }));

    g.push(box(36, 44, 758, 118, { stroke: CG, sw: 1, rx: 6 }));
    g.push(ln([[415, 50], [415, 156]], { stroke: CG, sw: 1, dash: '4 4' }));
    g.push(txt(226, 68, '부딪치기 전', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(604, 68, '부딪친 뒤', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(circ(150, 118, 22, { fill: C1, op: 0.28, stroke: C1 }));
    g.push(txt(150, 123, 'A', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(178, 118, 244, 118, { col: C1, marker: 'ar1' }));
    g.push(circ(300, 118, 22, { fill: C2, op: 0.28, stroke: C2 }));
    g.push(txt(300, 123, 'B', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(circ(500, 118, 22, { fill: C1, op: 0.28, stroke: C1 }));
    g.push(txt(500, 123, 'A', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(circ(600, 118, 22, { fill: C2, op: 0.28, stroke: C2 }));
    g.push(txt(600, 123, 'B', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(628, 118, 720, 118, { col: C2, marker: 'ar2' }));

    const rows = [
        ['✓', '닿았다 — 두 공이 한자리에서 만났다', C3],
        ['✓', '앞뒤가 있다 — A 의 움직임이 먼저고 B 의 움직임이 나중이다', C3],
        ['✓', '늘 그랬다 — 이런 장면을 여러 번 보았고 어긋난 적이 없다', C3],
        ['✗', '‘A 때문에 B 가 움직일 수밖에 없었다’ — 이것에 해당하는 광경은 없다', C2],
    ];
    rows.forEach((r, i) => {
        const y = 196 + i * 34;
        g.push(`<rect x="36" y="${y - 20}" width="758" height="28" rx="4" fill="${r[2]}" fill-opacity="${r[0] === '✗' ? 0.14 : 0.07}"/>`);
        g.push(txt(44, y, r[0], { cls: 'ink bold' }));
        g.push(txt(70, y, r[1], { cls: 'ink', size: 'sm' }));
    });

    g.push(lines(36, 352, [
        '흄의 논점은 인과가 없다는 것이 아니라, 넷째 줄에 해당하는 것을 감각에서 찾을 수 없다는 것이다',
    ]));

    return {
        name: 'phi-p-hume-billiard',
        svg: svg({
            width: W, height: H,
            title: '당구공 충돌에서 관찰되는 것과 되지 않는 것',
            desc: '접촉과 선행과 반복은 관찰되지만 필연적 연결에 해당하는 광경은 없다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 유사성 순서 — 가장 가까운 A 세계
 *
 * 접근관계 그림이 아니다. 세계 사이에 ‘더 닮았다’ 는 순서를 얹은 그림이고,
 * 그 순서가 어디서 오는지를 이 이론이 말해 주지 않는다는 것까지 캡션에 적는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 862;
    const H = 396;
    const g = [];
    const CXW = 290;
    const CYW = 170;

    g.push(txt(36, 26, '반사실 조건문을 가능세계로 읽는다', { cls: 'ink bold' }));

    g.push(ell(CXW, CYW, 168, 118, { stroke: CG, sw: 1.4 }));
    g.push(ell(CXW, CYW, 112, 80, { stroke: CG, sw: 1.4 }));
    g.push(ell(CXW, CYW, 58, 42, { stroke: CG, sw: 1.4 }));

    g.push(ell(540, 170, 110, 112, { fill: C2, op: 0.12, stroke: C2, sw: 1.8, dash: '6 4' }));
    g.push(txt(566, 176, 'A 가 참인 세계들', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(pdot(CXW, CYW, C1, 5.5));
    g.push(txt(CXW, 192, '실제 세계', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(pdot(444, 170, C2, 5.5));
    g.push(txt(444, 152, 'w~1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(CXW, 228, '가장 닮은 세계들', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(CXW, 266, '그다음으로 닮은 세계들', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(CXW, 304, '덜 닮은 세계들', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(lines(36, 340, [
        'A □→ C 가 참인가는, A 가 참인 세계 가운데 실제 세계와 가장 닮은 것 w~1 에서 C 가 참인가로 정해진다',
        '가까움은 거리가 아니라 닮음의 순서다. 무엇이 얼마나 닮았는지를 이 이론은 정해 주지 않는다 — 여기가 약한 곳이다',
        '이 그림은 가능세계를 형이상학의 도구로 쓴 것이다. 접근관계와 공리 체계는 논리학 문서 18장이 세운다',
    ]));

    return {
        name: 'phi-p-closest-worlds',
        svg: svg({
            width: W, height: H,
            title: '반사실 조건문과 가장 가까운 세계',
            desc: '실제 세계를 중심으로 닮음의 순서를 매기고 A 가 참인 가장 가까운 세계에서 C 를 확인한다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 선점과 과잉결정 — 반사실 분석이 무너지는 두 자리
 *
 * 두 반례를 한 장에 나란히 두는 이유는, 둘이 같은 조건을 서로 반대쪽에서
 * 공격한다는 점이 나란히 놓아야만 보이기 때문이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 872;
    const H = 372;
    const g = [];

    g.push(txt(36, 26, '반사실 분석이 무너지는 두 자리', { cls: 'ink bold' }));

    const nb = (x, y, w, h, label, col) => {
        g.push(box(x, y, w, h, { fill: col, op: 0.1, stroke: col, sw: 1.5 }));
        g.push(txt(x + w / 2, y + h / 2 + 5, label, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    };

    // 왼쪽 — 선점
    g.push(box(36, 44, 396, 236, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(234, 70, '선점 — 대기하던 원인이 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    nb(54, 92, 96, 38, '갑이 쏜다', C1);
    nb(190, 92, 96, 38, '총알이 간다', C1);
    nb(326, 112, 90, 38, '죽는다', C2);
    nb(54, 176, 96, 38, '을이 겨눈다', CG);
    nb(190, 176, 96, 38, '쏘지 않는다', CG);
    g.push(arw(150, 111, 188, 111, { col: C1, marker: 'ar1' }));
    g.push(arw(286, 111, 324, 126, { col: C1, marker: 'ar1' }));
    g.push(arw(102, 132, 102, 174, { col: CK }));
    g.push(txt(112, 158, '갑이 쏘았으므로', { cls: 'ink2', size: 'sm' }));
    g.push(arw(286, 195, 324, 152, { col: CK, dash: '5 4' }));
    g.push(lines(54, 240, [
        '갑이 쏘지 않았다면 을이 쏘았을 것이므로 죽음은 그대로다',
        '반사실 분석은 갑이 원인이 아니라고 한다 — 틀린 답이다',
    ], { gap: 20 }));

    // 오른쪽 — 과잉결정
    g.push(box(452, 44, 384, 236, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(644, 70, '과잉결정 — 각각으로 충분한 원인이 둘', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    nb(470, 92, 96, 38, '갑이 쏜다', C1);
    nb(600, 92, 96, 38, '총알 A', C1);
    nb(470, 176, 96, 38, '을이 쏜다', C3);
    nb(600, 176, 96, 38, '총알 B', C3);
    nb(730, 134, 90, 38, '죽는다', C2);
    g.push(arw(566, 111, 598, 111, { col: C1, marker: 'ar1' }));
    g.push(arw(696, 111, 728, 146, { col: C1, marker: 'ar1' }));
    g.push(arw(566, 195, 598, 195, { col: C3, marker: 'ar3' }));
    g.push(arw(696, 195, 728, 162, { col: C3, marker: 'ar3' }));
    g.push(lines(470, 240, [
        '갑이 없었어도 을 때문에 죽고, 을이 없었어도 갑 때문에 죽는다',
        '반사실 분석은 둘 다 원인이 아니라고 한다 — 역시 틀린 답이다',
    ], { gap: 20 }));

    g.push(lines(36, 314, [
        '두 반례가 치는 곳은 같다. ‘c 가 없었다면 e 도 없었을 것이다’ 라는 조건이 원인을 가려내기에 너무 셌던 것이다',
        '5장에서 게티어 사례가 정당화된 참인 믿음을 무너뜨린 것과 같은 모양이다. 분석을 세우면 반례가 따라온다',
    ]));

    return {
        name: 'phi-p-preempt-overdet',
        svg: svg({
            width: W, height: H,
            title: '선점과 과잉결정',
            desc: '대기하던 원인이 있을 때와 충분한 원인이 둘일 때 반사실 의존이 끊어진다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 법칙 같은 일반화와 우연한 일반화
 *
 * 두 문장을 같은 모양으로 적어 놓고 시험을 셋 통과시킨다. 겉모양이 같은데
 * 세 시험에서 갈린다는 것이 ‘법칙은 규칙성 이상이다’ 라는 주장의 출발점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 392;
    const g = [];
    const c1x = 36;
    const c1w = 208;
    const c2x = 250;
    const c2w = 296;
    const c3x = 552;
    const c3w = 296;

    g.push(txt(36, 26, '겉모양이 같은 두 일반화가 세 시험에서 갈린다', { cls: 'ink bold' }));

    g.push(box(c1x, 46, c1w, 52, { fill: CG, op: 0.35, stroke: CK, sw: 1.2 }));
    g.push(txt(c1x + 14, 78, '시험', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c2x, 46, c2w, 52, { fill: C2, op: 0.1, stroke: C2, sw: 1.6 }));
    g.push(txt(c2x + 14, 68, '지름 1 km 가 넘는', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(c2x + 14, 86, '금 덩어리는 없다', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c3x, 46, c3w, 52, { fill: C1, op: 0.1, stroke: C1, sw: 1.6 }));
    g.push(txt(c3x + 14, 68, '지름 1 km 가 넘는', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(c3x + 14, 86, '우라늄 235 덩어리는 없다', { cls: 'ink bold', size: 'sm' }));

    const rows = [
        ['아직 보지 않은 사례에도', '걸치는가', ['걸친다고 볼 근거가 없다.', '아직 안 본 곳에 있을 수 있다'], ['걸친다. 어디를 보든', '있을 수 없다']],
        ['반사실 조건문을', '떠받치는가', ['금을 더 모았더라면', '그런 덩어리가 있었을 것이다'], ['우라늄을 더 모았더라도', '먼저 터졌을 것이다']],
        ['왜 없는지를', '설명하는가', ['설명하지 못한다.', '그냥 없을 뿐이다'], ['설명한다. 임계질량을', '넘으면 유지되지 않는다']],
    ];
    rows.forEach((r, i) => {
        const y = 102 + i * 88;
        g.push(box(c1x, y, c1w, 82, { stroke: CK, sw: 1.1 }));
        g.push(txt(c1x + 14, y + 32, r[0], { cls: 'ink', size: 'sm' }));
        g.push(txt(c1x + 14, y + 52, r[1], { cls: 'ink', size: 'sm' }));
        g.push(box(c2x, y, c2w, 82, { stroke: CK, sw: 1.1 }));
        g.push(txt(c2x + 14, y + 32, r[2][0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(c2x + 14, y + 52, r[2][1], { cls: 'ink2', size: 'sm' }));
        g.push(box(c3x, y, c3w, 82, { stroke: CK, sw: 1.1 }));
        g.push(txt(c3x + 14, y + 32, r[3][0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(c3x + 14, y + 52, r[3][1], { cls: 'ink2', size: 'sm' }));
    });

    g.push(lines(36, 384, [
        '두 문장은 ‘그런 것은 하나도 없다’ 는 같은 모양이다. 규칙성만으로는 오른쪽이 왼쪽보다 나을 이유가 없다',
    ]));

    return {
        name: 'phi-p-law-vs-accident',
        svg: svg({
            width: W, height: H + 8,
            title: '법칙 같은 일반화와 우연한 일반화',
            desc: '겉모양이 같은 두 전칭 문장이 미확인 사례와 반사실 조건문과 설명이라는 세 시험에서 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 9. 필연·우연 × 선험·후험 교차표
 *
 * 이 장의 마지막 그림이다. 대각선은 전통적으로 편안했던 칸이고 반대각선이
 * 크립키가 채운 칸이다. 두 구분이 서로 다른 축이라는 것이 그림의 전부다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 396;
    const g = [];
    const cx = [190, 505];
    const cw = 300;
    const ry = [92, 196];
    const rh = 100;

    g.push(txt(36, 26, '양상의 축과 인식의 축은 서로 다른 축이다', { cls: 'ink bold' }));

    g.push(txt(340, 74, '필연적 — 달리 될 수 없었다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(655, 74, '우연적 — 달리 될 수 있었다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(174, 132, '선험적', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(174, 152, '경험 없이 안다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(174, 236, '후험적', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(174, 256, '경험으로 안다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const cell = (c, r, col, head, body) => {
        const x = cx[c];
        const y = ry[r];
        g.push(box(x, y, cw, rh, { fill: col, op: 0.1, stroke: col, sw: 1.8 }));
        g.push(txt(x + 14, y + 26, head, { cls: 'ink bold', size: 'sm' }));
        g.push(lines(x + 14, y + 50, body, { gap: 19 }));
    };

    cell(0, 0, C1, '전통적으로 편안한 칸', ['2 + 2 = 4', '총각은 미혼이다']);
    cell(1, 0, C2, '크립키가 채운 칸', ['그 막대의 길이는 1 m 다', '— 그 정의를 세운 사람에게']);
    cell(0, 1, C2, '크립키가 채운 칸', ['물은 H~2O 다', '헤스페루스는 포스포러스다']);
    cell(1, 1, C1, '전통적으로 편안한 칸', ['에베레스트가 가장 높다', '지금 비가 온다']);

    g.push(lines(36, 330, [
        '전통적으로는 대각선 두 칸만 있다고 보았다. 필연은 생각만으로 알고 우연은 겪어야 안다는 것이다',
        '크립키는 반대각선 두 칸을 채웠다. 무엇이 달리 될 수 있었느냐와 무엇을 어떻게 아느냐는 다른 물음이다',
        '왼쪽 아래 칸을 떠받치는 장치가 고정지시어이고, 그것은 16장이 세운다',
    ]));

    return {
        name: 'phi-p-modal-epistemic-grid',
        svg: svg({
            width: W, height: H,
            title: '필연·우연과 선험·후험의 교차표',
            desc: '두 구분이 서로 다른 축이며 크립키가 반대각선 두 칸을 채웠다',
            body: g.join(''),
        }),
    };
})());

export default figures;
