/**
 * 철학 14·15장(마음과 몸 / 의식·지향성·기계의 마음)의 그림.
 *
 * 이름은 모두 `phi-d-` 로 시작한다(심리철학 두 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 여기 있는 것은 층·칸·축처럼 자리가 뜻을 갖는 그림뿐이다.
 * 입장의 사슬, 논증의 걸음, 응답과 되받음처럼 상자와 화살표로 그릴 것은
 * d2/philosophy/phi-d-*.d2 에 있다.
 *
 * 색에 대하여. 뒤집힌 스펙트럼 그림은 색을 쓰지만 그 색은 실제 색이 아니라
 * 두 어떠함을 가르는 표시일 뿐이다. 그림 안에 그렇게 적어 두었다.
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

/** 양쪽에 화살촉이 붙은 선. 서로 묶인다는 뜻으로 쓴다. */
function darw(x1, y1, x2, y2, { col = CK, marker = 'ark', sw = 1.5, dash } = {}) {
    return `<path d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}" fill="none" stroke="${col}"`
        + ` stroke-width="${sw}" stroke-linecap="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ` marker-start="url(#${marker})" marker-end="url(#${marker})"/>`;
}

/** 가위표. 성립하지 않는 짝, 또는 끊어진 걸음 위에 얹는다. */
function xmark(x, y, s = 8, col = C2) {
    return `<path d="M${r2(x - s)} ${r2(y - s)} L${r2(x + s)} ${r2(y + s)}`
        + ` M${r2(x + s)} ${r2(y - s)} L${r2(x - s)} ${r2(y + s)}"`
        + ` stroke="${col}" stroke-width="2.6" stroke-linecap="round" fill="none"/>`;
}

/** 체크 표시. 성립해도 되는 짝 위에 얹는다. */
function vmark(x, y, s = 8, col = C3) {
    return `<path d="M${r2(x - s)} ${r2(y)} L${r2(x - s * 0.2)} ${r2(y + s * 0.8)} L${r2(x + s)} ${r2(y - s * 0.9)}"`
        + ` stroke="${col}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>`;
}

/** 여러 줄 글. 줄 간격은 화소로 준다. */
function lines(x, y, arr, { gap = 19, anchor = 'start', cls = 'ink2', size = 'sm' } = {}) {
    return arr.map((s, i) => txt(x, y + i * gap, s, { anchor, cls, size })).join('');
}

/** 그림 아래에 붙이는 읽을거리. */
function notes(x, y, arr) {
    return lines(x, y, arr, { gap: 20, cls: 'ink2', size: 'sm' });
}

/** 머리글 한 줄과 내용 여러 줄을 담는 칸. */
function cell(x, y, w, h, head, body, { accent = null, dash, gap = 18 } = {}) {
    const g = [box(x, y, w, h, {
        fill: accent ?? 'none', op: accent ? 0.12 : 1,
        stroke: accent ?? CK, sw: accent ? 1.7 : 1.2, dash,
    })];
    g.push(txt(x + 12, y + 23, head, { cls: 'ink bold', size: 'sm' }));
    body.forEach((s, i) => g.push(txt(x + 12, y + 45 + i * gap, s, { cls: 'ink2', size: 'sm' })));
    return g.join('');
}

/* ================================================================== *
 * 14장 — 마음과 몸
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 두 목록과 그 사이의 화살표
 *
 * 이 장을 여는 그림이다. 심신 문제가 문제로 보이는 이유가 두 목록이 어긋나는데도
 * 둘이 촘촘히 붙어 있다는 데 있으므로, 목록 둘을 나란히 놓고 그 사이에 화살표를
 * 그리는 것이 요점을 그대로 보인다. 그림이 보이는 것은 붙어 있음까지이고,
 * 같음은 보이지 않는다 — 그 차이가 이 장 전체의 물음이다.
 * 오른쪽 줄이 다시 두 갈래로 갈리는 것은 15장이 두 절로 갈리는 이유이므로
 * 같은 그림 안에 붙여 둔다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 890;
    const H = 462;
    const g = [];

    g.push(txt(40, 28, '두 목록이 어긋나는데도 촘촘히 붙어 있다', { cls: 'ink bold' }));

    // 왼쪽 줄 — 물리적인 것
    g.push(box(40, 46, 316, 190, { stroke: C1, sw: 1.6 }));
    g.push(txt(56, 72, '물리적인 것을 아는 방식', { cls: 'ink bold', size: 'sm' }));
    g.push(lines(56, 100, [
        '공간의 어느 자리를 차지한다',
        '크기와 모양과 질량이 있다',
        '누구나 볼 수 있다',
        '물리학 · 화학 · 생물학의 말로 적힌다',
        '뇌도 그렇다 — 무게가 있고 자리가 있고',
        '열어 보면 누구에게나 보인다',
    ], { gap: 21 }));

    // 오른쪽 줄 — 마음
    g.push(box(534, 46, 316, 190, { stroke: C2, sw: 1.6 }));
    g.push(txt(550, 72, '마음을 아는 방식', { cls: 'ink bold', size: 'sm' }));
    g.push(lines(550, 100, [
        '모양도 무게도 없다',
        '머릿속 어느 자리냐는 물음이',
        '이상하게 들린다',
        '겪는 이에게만 그 방식으로 주어진다',
        '남은 표정과 말과 뇌 영상을 볼 뿐',
        '내가 겪는 방식으로 겪지는 못한다',
    ], { gap: 21 }));

    // 가운데 — 붙어 있음
    g.push(txt(445, 86, '촘촘히 붙어 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(374, 106, 516, 106, { col: C3, marker: 'ar3' }));
    g.push(arw(516, 128, 374, 128, { col: C3, marker: 'ar3' }));
    g.push(lines(445, 158, [
        '마취제를 넣으면',
        '아픔이 사라진다',
        '뇌의 어느 부위가 상하면',
        '얼굴을 알아보지 못한다',
    ], { gap: 20, anchor: 'middle' }));

    // 오른쪽 줄이 다시 갈린다
    g.push(txt(40, 270, '오른쪽 줄은 다시 두 갈래로 갈린다 — 물리주의가 겪는 어려움이 갈래마다 다르기 때문이다',
        { cls: 'ink bold', size: 'sm' }));
    g.push(cell(40, 282, 390, 92, '지향적 상태 — 무언가에 관한다', [
        '비가 온다는 믿음, 물을 마시고 싶다는 욕구',
        '관하는 그 무엇을 내용이라 부른다',
        '이쪽의 어려움은 15장의 지향성 절에서 다룬다',
    ], { accent: C1 }));
    g.push(cell(460, 282, 390, 92, '감각적 상태 — 겪어지는 어떠함이 있다', [
        '치통, 빨강을 봄, 커피 냄새를 맡음',
        '겪는 쪽에서 어떠함이 있다',
        '이쪽의 어려움은 15장의 감각질 절에서 다룬다',
    ], { accent: C2 }));

    g.push(notes(40, 402, [
        '두 갈래가 깨끗이 갈리지는 않는다. 무언가를 무서워하는 것은 내용도 갖고 느낌도 있다',
        '이 그림이 보이는 것은 붙어 있음까지다. 붙어 있음과 같음은 다르다 —',
        '붙어 있는 둘이 같은 것인지 다른 것인지를 두고 이 장의 입장들이 갈린다',
    ]));

    return {
        name: 'phi-d-mental-two-kinds',
        svg: svg({
            width: W, height: H,
            title: '물리적인 것과 마음을 아는 두 방식, 그리고 마음 상태의 두 갈래',
            desc: '왼쪽 줄과 오른쪽 줄이 어긋나는데도 가운데 화살표로 촘촘히 붙어 있고, 오른쪽 줄은 지향적 상태와 감각적 상태로 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 유형 동일론과 사례 동일론
 *
 * 10장의 유형·사례 구분이 이 장에서 결정적으로 쓰이는 자리다. 위 줄과 아래 줄을
 * 같은 그림에 놓아야 다음 절의 논증이 위 줄만 끊는다는 것이 보인다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 476;
    const g = [];

    g.push(txt(40, 28, '무엇과 무엇을 묶는가 — 두 주장의 세기가 다르다', { cls: 'ink bold' }));

    // 위 줄 — 유형 동일론
    g.push(box(40, 44, 820, 152, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(58, 70, '유형 동일론 — 유형끼리 묶는다', { cls: 'ink bold', size: 'sm' }));

    g.push(box(64, 92, 212, 48, { fill: C1, op: 0.14, stroke: C1, sw: 1.6 }));
    g.push(txt(170, 122, '심적 유형 — 고통', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(box(412, 92, 232, 48, { fill: C1, op: 0.14, stroke: C1, sw: 1.6 }));
    g.push(txt(528, 122, '물리 유형 — C-섬유 발화', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(344, 100, '같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(darw(282, 122, 406, 122, { col: C1, marker: 'ar1' }));
    g.push(xmark(344, 122, 11, C2));
    g.push(txt(344, 164, '다수 실현 논증이 여기를 끊는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(lines(668, 106, [
        '강하다',
        '고통이 있는 곳에는 어디나',
        '그 물리 유형이 있어야 한다',
    ], { gap: 20 }));

    // 아래 줄 — 사례 동일론
    g.push(box(40, 210, 820, 178, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(58, 236, '사례 동일론 — 낱낱의 사건끼리 묶는다', { cls: 'ink bold', size: 'sm' }));

    const pairs = [
        ['지금 나의 이 아픔', '지금 내 뇌의 이 사건', C1],
        ['어제 그의 그 아픔', '그때 그의 뇌의 그 사건', C2],
        ['이 문어의 이 아픔', '이 문어의 신경계의 이 사건', C3],
    ];
    pairs.forEach(([a, b, col], i) => {
        const y = 254 + i * 42;
        g.push(box(64, y, 212, 32, { stroke: CK, sw: 1.2 }));
        g.push(txt(170, y + 21, a, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(box(412, y, 232, 32, { fill: col, op: 0.14, stroke: col, sw: 1.5 }));
        g.push(txt(528, y + 21, b, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(darw(282, y + 16, 406, y + 16, { col: CK }));
    });
    g.push(lines(668, 268, [
        '약하다',
        '짝마다 오른쪽이 서로 다른',
        '종류의 사건이어도 된다',
        '(칸 색이 다른 것이 그 뜻이다)',
    ], { gap: 20 }));
    g.push(vmark(676, 356, 8, C3));
    g.push(txt(694, 361, '이 줄은 그대로 남는다', { cls: 'ink bold', size: 'sm' }));

    g.push(notes(40, 416, [
        '위 줄이 참이면 아래 줄도 참이다. 그러나 아래 줄이 참이라고 위 줄이 참이 되지는 않는다',
        '다음 절의 다수 실현 논증은 위 줄만 끊는다. 아래 줄은 조금도 다치지 않으므로 물리주의도 다치지 않는다',
        '아래 줄만 남으면 잃는 것이 있다 — ‘고통이란 무엇인가’ 에 대한 일반적인 답이 없어진다',
    ]));

    return {
        name: 'phi-d-type-vs-token',
        svg: svg({
            width: W, height: H,
            title: '유형 동일론과 사례 동일론이 묶는 것',
            desc: '위 줄은 심적 유형과 물리 유형을 하나씩 묶고 아래 줄은 낱낱의 사건끼리 묶으며 다수 실현 논증은 위 줄만 끊는다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 다수 실현
 *
 * 층을 둘로 그려야 ‘위에서 하나, 아래에서 여럿’ 이 눈에 들어온다.
 * 넷째 칸(사람 안의 변이)을 반드시 넣는다. 이 논증이 공상에 기대지 않는다는 것이
 * 이 논증의 힘이기 때문이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 436;
    const g = [];

    g.push(txt(40, 28, '같은 마음 유형이 여러 물리 유형으로 실현된다', { cls: 'ink bold' }));

    g.push(txt(40, 66, '위쪽 층', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(40, 86, '마음 유형은 하나', { cls: 'ink2', size: 'sm' }));
    g.push(box(350, 72, 200, 54, { fill: C2, op: 0.16, stroke: C2, sw: 1.8 }));
    g.push(txt(450, 105, '고통', { anchor: 'middle', cls: 'ink bold' }));

    g.push(ln([[40, 158], [860, 158]], { stroke: CG, sw: 1.2, dash: '5 4' }));
    g.push(txt(40, 182, '아래쪽 층', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(40, 202, '물리 유형은 여럿', { cls: 'ink2', size: 'sm' }));

    const real = [
        ['사람', ['어떤 신경 상태', '(C-섬유 발화라는', '자리 표시)']],
        ['문어', ['구조가 아주 다른', '신경계의 다른 상태']],
        ['규소로 만든 존재', ['신경계가 아닌 것으로', '이루어진 상태']],
        ['사람 안의 변이', ['뇌 손상 뒤 다른 부위가', '그 일을 떠맡은 상태']],
    ];
    const bx = [64, 268, 472, 676];
    const bw = 186;
    real.forEach(([head, body], i) => {
        g.push(cell(bx[i], 226, bw, 90, head, body, { accent: C1, gap: 17 }));
        g.push(arw(450, 130, bx[i] + bw / 2, 222, { col: C2, marker: 'ar2' }));
    });

    g.push(notes(40, 348, [
        '위쪽 층에서 하나인 것이 아래쪽 층에서 여럿이면 유형끼리의 동일성은 성립할 수 없다',
        '넷째 칸이 있어서 이 논증은 공상에 기대지 않는다. 규소 생물을 물리쳐도 문어가 남고, 문어를 물리쳐도 사람 안의 변이가 남는다',
        '무너지는 것은 유형 동일론까지다. 낱낱의 사건끼리 묶는 사례 동일론과 물리주의 자체는 다치지 않는다',
        '유형 동일론 쪽 되받음 — 동일화를 종에 상대적으로 한다. 대가는 ‘고통 그 자체’ 에 대한 일반적인 답이 없어지는 것이다',
    ]));

    return {
        name: 'phi-d-multiple-realization',
        svg: svg({
            width: W, height: H,
            title: '다수 실현 가능성',
            desc: '가운데 위의 마음 유형 하나에서 사람 문어 규소 생물 사람 안의 변이라는 네 물리 유형으로 화살표가 뻗는다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 수반이 금지하는 짝과 허용하는 짝
 *
 * 수반은 방향이 있는 관계인데 정의만 읽으면 그 방향이 잘 잡히지 않는다.
 * 금지되는 짝과 허용되는 짝을 나란히 놓는 것이 정의를 한 번 더 적는 것보다 낫다.
 * 오른쪽 칸이 다수 실현과 같은 그림이라는 것도 여기서 드러난다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 452;
    const g = [];

    g.push(txt(40, 28, '수반이 금지하는 짝과 허용하는 짝', { cls: 'ink bold' }));

    /** 세계 하나를 그린다. */
    function world(x, y, name, phys, mind, mindCol) {
        const out = [box(x, y, 178, 152, { stroke: CK, sw: 1.3 })];
        out.push(txt(x + 89, y + 24, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + 14, y + 46, '물리적 사실', { cls: 'ink2', size: 'sm' }));
        phys.forEach((s, i) => out.push(txt(x + 14, y + 66 + i * 17, s, { cls: 'ink', size: 'sm' })));
        out.push(ln([[x + 14, y + 104], [x + 164, y + 104]], { stroke: CG, sw: 1.1, dash: '4 3' }));
        out.push(txt(x + 14, y + 124, '마음의 사실', { cls: 'ink2', size: 'sm' }));
        out.push(txt(x + 14, y + 143, mind, { cls: 'ink bold', size: 'sm' }));
        out.push(box(x + 8, y + 112, 162, 34, { fill: mindCol, op: 0.13, stroke: 'none', sw: 0 }));
        // 배경 칠이 글자를 덮지 않도록 글자를 한 번 더 얹는다.
        out.push(txt(x + 14, y + 124, '마음의 사실', { cls: 'ink2', size: 'sm' }));
        out.push(txt(x + 14, y + 143, mind, { cls: 'ink bold', size: 'sm' }));
        return out.join('');
    }

    // 왼쪽 — 금지
    g.push(box(40, 46, 400, 262, { stroke: C2, sw: 1.7, rx: 6 }));
    g.push(txt(240, 72, '수반이 금지하는 짝', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(world(58, 84, '세계 A', ['원자 배치가 같다', '신경 상태가 같다'], '아프다', C2));
    g.push(world(244, 84, '세계 B', ['원자 배치가 같다', '신경 상태가 같다'], '아프지 않다', C1));
    g.push(xmark(240, 254, 11, C2));
    g.push(txt(240, 288, '아래가 같은데 위가 다르다 — 이런 짝은 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 허용
    g.push(box(470, 46, 390, 262, { stroke: C3, sw: 1.7, rx: 6 }));
    g.push(txt(665, 72, '수반이 허용하는 짝', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(world(484, 84, '세계 C', ['사람의 신경계', '그 안의 어떤 상태'], '아프다', C2));
    g.push(world(670, 84, '세계 D', ['문어의 신경계', '구조가 다른 상태'], '아프다', C2));
    g.push(vmark(665, 252, 11, C3));
    g.push(txt(665, 288, '위가 같은데 아래가 다르다 — 이런 짝은 있어도 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(notes(40, 344, [
        '수반은 한 방향만 막는다. 아래에서 차이가 없으면 위에서도 차이가 있을 수 없다는 것이 전부다',
        '오른쪽 칸을 막지 않는 것이 이 정식화의 값어치다 — 그래서 물리주의를 다수 실현과 함께 적을 수 있다',
        '그런데 수반만으로는 부족하다. 수반은 함께 변한다는 것만 말하고 어느 쪽이 어느 쪽 때문인지를 말하지 않는다',
        '그래서 조항이 하나 붙는다 — 물리적 사실을 복제하고 그 이상 아무것도 더하지 않으면 세계 전체가 복제된다',
    ]));

    return {
        name: 'phi-d-supervenience-worlds',
        svg: svg({
            width: W, height: H,
            title: '수반이 금지하는 세계 쌍과 허용하는 세계 쌍',
            desc: '물리적으로 같은데 마음이 다른 두 세계는 금지되고 물리적으로 다른데 마음이 같은 두 세계는 허용된다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 15장 — 의식 · 지향성 · 기계의 마음
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 5. 쉬운 문제들과 어려운 문제
 *
 * 두 무더기를 나란히 적어 놓기만 하면 목록이 되고 만다. 같은 축 위에 놓고
 * ‘물음이 닫히는 자리’ 라는 선을 하나 그으면, 왼쪽 줄들은 선에 닿고 아래 줄만
 * 닿지 못한다는 것이 그림 자체로 보인다. 닿지 못한 그 구간이 설명적 간극이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 458;
    const g = [];

    g.push(txt(40, 28, '한 축에 놓으면 설명적 간극이 생기는 자리가 보인다', { cls: 'ink bold' }));

    const LINE = 706;
    g.push(ln([[LINE, 62], [LINE, 366]], { stroke: C3, sw: 2 }));
    g.push(txt(LINE + 12, 66, '물음이', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(LINE + 12, 84, '닫히는 자리', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(40, 76, '쉬운 문제 — 무엇을 하면 풀리는지 안다', { cls: 'ink bold', size: 'sm' }));
    const easy = [
        '자극을 갈라낸다',
        '주의가 옮겨 간다',
        '정보가 여러 체계에 쓰이게 된다',
        '자기 내부 상태를 보고한다',
        '잠과 깸이 갈린다',
    ];
    easy.forEach((s, i) => {
        const y = 108 + i * 30;
        g.push(txt(46, y, s, { cls: 'ink', size: 'sm' }));
        g.push(arw(292, y - 4, LINE - 4, y - 4, { col: C1, marker: 'ar1', sw: 1.6 }));
    });
    g.push(txt(495, 262, '메커니즘을 찾아내면 그 줄은 선에 닿는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[40, 296], [860, 296]], { stroke: CG, sw: 1.1, dash: '5 4' }));

    g.push(txt(40, 326, '어려운 문제 — 하나뿐이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(46, 352, '왜 그 모든 처리에 겪어짐이 따라붙는가', { cls: 'ink', size: 'sm' }));
    g.push(arw(292, 348, 520, 348, { col: C2, marker: 'ar2', sw: 1.6 }));
    g.push(ln([[528, 348], [672, 348]], { stroke: C2, sw: 1.6, dash: '4 5' }));
    g.push(xmark(614, 348, 10, C2));
    g.push(txt(614, 324, '설명적 간극', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(614, 378, '메커니즘을 아무리 가리켜도', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(614, 396, '‘그런데 왜 그것이 겪어지는가’ 가 다시 물어진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(notes(40, 428, [
        '쉽다는 말은 풀기 쉽다는 뜻이 아니라 무엇을 찾아야 하는지가 정해져 있다는 뜻이다',
        '이 가름 자체가 중립적인 분류는 아니다. 어려운 문제가 잘못 세워진 물음이라고 보는 쪽은 아래 줄을 별개의 줄로 받아들이지 않는다',
    ]));

    return {
        name: 'phi-d-easy-hard',
        svg: svg({
            width: W, height: H,
            title: '쉬운 문제들과 어려운 문제, 그리고 설명적 간극',
            desc: '쉬운 문제 다섯 줄은 물음이 닫히는 선에 닿고 어려운 문제 한 줄만 선에 닿지 못한 채 간극이 남는다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 뒤집힌 스펙트럼
 *
 * 두 사람과 두 자극으로 격자를 만들면 가운데 칸만 뒤바뀌고 나머지가 그대로라는
 * 것이 한눈에 보인다. 색은 팔레트의 두 색으로만 쓰고, 실제 색이 아니라 두 어떠함을
 * 가르는 표시일 뿐이라고 그림 안에 적어 둔다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 502;
    const g = [];

    g.push(txt(40, 28, '뒤집힌 스펙트럼 — 밖에서 재는 것은 모두 같다', { cls: 'ink bold' }));

    const cx = [128, 330, 532];
    const cw = 184;
    const ow = 320;
    g.push(txt(cx[0] + cw / 2, 64, '입력 ① 잘 익은 토마토', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx[1] + cw / 2, 64, '입력 ② 잎사귀', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx[2] + ow / 2, 64, '출력 — 말과 행동', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const out = [
        '토마토를 ‘빨갛다’ 고 부른다',
        '잎사귀를 ‘초록이다’ 고 부른다',
        '빨간 신호등에서 선다',
        '빨강을 따뜻한 색이라 말한다',
    ];

    /** 겪어지는 어떠함을 나타내는 칸. */
    function qual(x, y, col, name) {
        return box(x, y, cw, 90, { fill: col, op: 0.3, stroke: col, sw: 1.7 })
            + txt(x + cw / 2, y + 52, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    }

    [['갑', C2, C3, 78], ['을', C3, C2, 208]].forEach(([who, c1, c2, y]) => {
        g.push(txt(58, y + 52, who, { cls: 'ink bold' }));
        g.push(qual(cx[0], y, c1, who === '갑' ? '어떠함 가' : '어떠함 나'));
        g.push(qual(cx[1], y, c2, who === '갑' ? '어떠함 나' : '어떠함 가'));
        g.push(box(cx[2], y, ow, 90, { stroke: CK, sw: 1.2 }));
        out.forEach((s, i) => g.push(txt(cx[2] + 14, y + 25 + i * 19, s, { cls: 'ink2', size: 'sm' })));
    });

    // 가운데 두 칸만 어긋나 있다는 것을 엇갈린 선으로 보인다.
    g.push(arw(cx[0] + cw / 2, 172, cx[1] + cw / 2, 204, { col: CK, dash: '4 3' }));
    g.push(arw(cx[1] + cw / 2, 172, cx[0] + cw / 2, 204, { col: CK, dash: '4 3' }));
    g.push(txt(44, 194, '서로 바뀜', { cls: 'ink bold', size: 'sm' }));
    g.push(vmark(cx[2] + 24, 189, 8, C3));
    g.push(txt(cx[2] + 42, 194, '이 칸은 글자 하나 다르지 않다', { cls: 'ink bold', size: 'sm' }));

    g.push(cell(40, 320, 400, 78, '같은 것', [
        '입력 · 출력 · 낱말 쓰임 · 행동',
        '밖에서 잴 수 있는 것 가운데 다른 것이 없다',
    ], { accent: C3 }));
    g.push(cell(460, 320, 400, 78, '다른 것', [
        '겪어지는 어떠함 하나뿐',
        '두 사람 다 이 사실을 알아낼 방법이 없다',
    ], { accent: C2 }));

    g.push(notes(40, 426, [
        '역할이 같은데 감각질이 다르다면 마음 상태는 역할로 정해지지 않는다. 이 사례의 표적은 기능주의다',
        '기능주의 쪽 대응 — 색 공간은 대칭이 아니어서 실제로 뒤집으면 밝기와 대비 판단이 어긋나고 그것이 행동에 드러난다',
        '그 대응이 보이는 것은 사람의 색에서 뒤집기가 어렵다는 것까지다. 대칭인 감각 차원을 가진 존재를 생각하면 사례가 되살아난다',
        '칸의 색은 실제 색이 아니라 두 어떠함이 다르다는 표시일 뿐이다',
    ]));

    return {
        name: 'phi-d-inverted-spectrum',
        svg: svg({
            width: W, height: H,
            title: '뒤집힌 스펙트럼',
            desc: '두 사람의 겪어지는 어떠함만 서로 바뀌어 있고 입력과 출력과 낱말 쓰임은 모두 같다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 메리의 방
 *
 * 방 안에 무엇이 들어 있는지를 실제로 적어 놓아야 (P1) 이 무엇을 요구하는지가 보인다.
 * 문을 그리되 오른쪽 벽에 틈을 내어 그린다. 나온 뒤의 물음에서 두 갈래가 갈린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 480;
    const g = [];

    g.push(txt(40, 28, '메리의 방 — 문을 나선 순간 무엇이 늘어나는가', { cls: 'ink bold' }));

    // 방. 오른쪽 벽에 문틈을 낸다.
    g.push(ln([[40, 52], [372, 52]], { stroke: CK, sw: 1.8 }));
    g.push(ln([[40, 52], [40, 262]], { stroke: CK, sw: 1.8 }));
    g.push(ln([[40, 262], [372, 262]], { stroke: CK, sw: 1.8 }));
    g.push(ln([[372, 52], [372, 133]], { stroke: CK, sw: 1.8 }));
    g.push(ln([[372, 181], [372, 262]], { stroke: CK, sw: 1.8 }));
    g.push(txt(382, 196, '문', { cls: 'ink2', size: 'sm' }));

    g.push(txt(58, 80, '흑백 방 안', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(58, 102, '메리가 가진 것 — 색에 관한 물리적 사실 전부', { cls: 'ink2', size: 'sm' }));
    g.push(lines(58, 130, [
        '빛의 파장',
        '망막의 수용체',
        '시각 피질에서 일어나는 일',
        '사람이 색을 볼 때 뇌에서 벌어지는',
        '모든 물리적 사실',
    ], { gap: 20, cls: 'ink' }));
    g.push(txt(58, 242, '겪은 적은 한 번도 없다 — 흑백 화면으로만 공부했다', { cls: 'ink2', size: 'sm' }));

    g.push(arw(316, 157, 452, 157, { col: C2, marker: 'ar2', sw: 2 }));
    g.push(txt(394, 145, '문을 나선다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(cell(462, 56, 398, 88, '문 밖 — 처음으로 빨간 것을 본다', [
        '그는 무언가를 새로 아는가',
        '대부분의 사람이 그렇다고 답한다',
    ], { accent: C2 }));
    g.push(arw(661, 148, 661, 176, { col: CK }));
    g.push(cell(462, 180, 398, 108, '그렇다고 하면 걸음이 이렇게 간다', [
        '(P1) 방 안에서 물리적 사실을 모두 알았다',
        '(P2) 나온 뒤 새로운 것을 안다',
        '(C1) 물리적 사실이 아닌 사실이 있다',
        '(C) 물리주의는 거짓이다',
    ], { accent: null, gap: 17 }));

    g.push(txt(40, 320, '물리주의 쪽 응답 둘 — 둘 다 (P2) 를 다시 읽는다', { cls: 'ink bold', size: 'sm' }));
    g.push(cell(40, 330, 400, 92, '능력 가설', [
        '얻은 것은 사실이 아니라 능력이다 —',
        '상상하고 기억하고 다시 알아보는 능력',
        '자전거 타는 법을 익히는 것과 같은 종류다',
    ], { accent: C1 }));
    g.push(cell(460, 330, 400, 92, '현상 개념 전략', [
        '얻은 것은 옛 사실을 붙잡는 새 개념이다',
        '헤스페루스와 포스포러스가 같은 모양이다',
        '겪음으로만 얻어지는 개념이 있다면 그렇게 된다',
    ], { accent: C3 }));

    g.push(notes(40, 448, [
        '두 응답은 같지 않다. 능력 가설은 얻은 것이 앎이 아니라 하고, 현상 개념 전략은 앎이지만 새 사실은 아니라 한다',
        '잭슨 자신이 나중에 이 논증을 거부하고 물리주의 쪽으로 옮겼다. 만든 사람이 버렸다는 것이 논증을 반박하지는 않는다',
    ]));

    return {
        name: 'phi-d-mary-room',
        svg: svg({
            width: W, height: H,
            title: '메리의 방과 지식 논증',
            desc: '흑백 방 안의 물리적 사실 전부와 문을 나선 뒤의 물음, 그리고 물리주의 쪽 두 응답',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 기계에 대해 묻게 되는 세 물음
 *
 * 셋이 한 덩어리로 보이므로 먼저 하나에서 갈라져 나오는 그림을 그리고,
 * 칸 사이의 화살표를 끊어 놓는다. 끊긴 화살표가 이 그림의 요점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 448;
    const g = [];

    g.push(txt(40, 28, '한 덩어리로 보이는 물음이 실은 셋이다', { cls: 'ink bold' }));

    g.push(box(320, 46, 260, 42, { fill: CG, op: 0.5, stroke: CK, sw: 1.4 }));
    g.push(txt(450, 73, '기계가 생각할 수 있는가', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const colX = [40, 325, 610];
    const colW = 250;
    const cols = [
        ['물음 ① 행동', '사람과 구별되지 않게', ['행동할 수 있는가', '누구의 물음인가 — 튜링', '재는 것 — 글로 주고받는 대화', '통과해도 오른쪽 두 칸은 남는다'], C1],
        ['물음 ② 이해', '계산이 이해에', ['충분한가', '누구의 물음인가 — 설', '재는 것 — 내용, 곧 무언가에 관함', '중국어 방이 겨눈 물음이다'], C2],
        ['물음 ③ 겪어짐', '기계에 겪어지는 것이', ['있는가', '누구의 물음인가 — 차머스 · 네이글', '재는 것 — 어떠함', '어려운 문제가 그대로 온다'], C3],
    ];
    cols.forEach(([head, l1, body, col], i) => {
        const x = colX[i];
        g.push(box(x, 132, colW, 136, { fill: col, op: 0.1, stroke: col, sw: 1.7 }));
        g.push(txt(x + 12, 156, head, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + 12, 178, l1, { cls: 'ink bold', size: 'sm' }));
        body.forEach((s, j) => g.push(txt(x + 12, 198 + j * 18, s, { cls: 'ink2', size: 'sm' })));
        g.push(arw(450, 92, x + colW / 2, 128, { col: CK }));
    });

    const c0 = colX[0] + colW / 2;
    const c1 = colX[1] + colW / 2;
    const c2 = colX[2] + colW / 2;
    g.push(arw(c0 + 42, 310, c1 - 42, 310, { col: CK, dash: '5 4' }));
    g.push(xmark((c0 + c1) / 2, 310, 10, C2));
    g.push(txt((c0 + c1) / 2, 292, '따라 나오지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(c1 + 42, 310, c2 - 42, 310, { col: CK, dash: '5 4' }));
    g.push(xmark((c1 + c2) / 2, 310, 10, C2));
    g.push(txt((c1 + c2) / 2, 292, '따라 나오지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(450, 336, '가운데 칸을 통과해도 오른쪽 칸은 그대로 남는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(notes(40, 372, [
        '셋을 섞으면 논쟁 전체가 헛돈다. 설 자신이 ② 와 ‘기계가 생각할 수 있는가’ 를 갈라 두었다 — 뒤엣것에는 그렇다고 답한다. 뇌가 기계이고 생각하기 때문이다',
        '② 에 ‘충분하지 않다’ 가 참이어도 그것은 계산으로 만든 체계에 대한 판정일 뿐, 다른 방식으로 만든 기계에 대한 판정은 아니다',
        '③ 은 사람에 대해서도 풀리지 않은 물음이다. 기계에서 먼저 풀릴 이유가 없다',
        '기계를 어떻게 대해야 하는가는 또 다른 층의 물음이고 이 그림에 없다. 그것은 19장의 일이다',
    ]));

    return {
        name: 'phi-d-ai-three-questions',
        svg: svg({
            width: W, height: H,
            title: '기계를 놓고 묻게 되는 세 물음',
            desc: '한 물음이 행동 이해 겪어짐이라는 세 물음으로 갈리고 칸과 칸을 잇는 화살표가 끊겨 있다',
            body: g.join(''),
        }),
    };
})());

export default figures;
