/**
 * 철학 문서 16장(말은 어떻게 무언가를 가리키는가)의 그림.
 *
 * 이름은 모두 `phi-l-` 로 시작한다(언어철학 한 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 `w~1`, `H~2O` 는 첨자로 내려가고,
 * 그 밖의 자리에서는 물결표를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 * HTML 엔티티도 못 쓴다. 필요한 기호는 유니코드로 적거나 도형으로 그린다.
 *
 * 이 장의 그림이 하는 일은 대개 ‘같은 장면에서 두 층을 갈라 보이는 것’ 이다.
 * 뜻과 지시체, 머릿속과 환경, 말한 것과 전한 것 — 다툼이 층을 섞는 데서 생기므로
 * 층을 화면에서 갈라 놓으면 문단 여러 개보다 빨리 읽힌다. 입장 지도와 논증 구조는
 * d2/philosophy/phi-l-*.d2 에 있다.
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
 * 화소 좌표 소도구 — philosophy-metaphysics.mjs 와 같은 꼴로 맞춘다
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

const pdot = (x, y, col = C2, r = 5) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const circ = (cx, cy, r, { fill = 'none', op = 1, stroke = CK, sw = 1.5, dash } = {}) =>
    `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}"`
    + ` stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;

/** 번호 고리. 팔레트 밖의 색을 쓰지 않으려고 글자 대신 테두리에 색을 준다. */
const ring = (cx, cy, digit, col) =>
    circ(cx, cy, 11, { stroke: col, sw: 1.8 })
    + txt(cx, cy + 4.5, digit, { anchor: 'middle', cls: 'ink bold', size: 'sm' });

/** 유니코드 글리프에 기대지 않으려고 ✗ 와 ○ 를 도형으로 그린다. */
const cross = (cx, cy, col = C2, s = 8) =>
    ln([[cx - s, cy - s], [cx + s, cy + s]], { stroke: col, sw: 2.6 })
    + ln([[cx + s, cy - s], [cx - s, cy + s]], { stroke: col, sw: 2.6 });

const check = (cx, cy, col = C1, r = 9) => circ(cx, cy, r, { stroke: col, sw: 2.6 });

/** 여러 줄 글. 줄 간격은 화소로 준다. */
function lines(x, y, arr, { gap = 20, anchor = 'start', cls = 'ink2', size = 'sm' } = {}) {
    return arr.map((s, i) => txt(x, y + i * gap, s, { anchor, cls, size })).join('');
}

/* ================================================================== *
 * 1. 소박한 그림과 거기서 터지는 세 자리
 *
 * 화살표를 하나만 그리는 것이 요점이다. 소박한 그림이 가진 것이 그것뿐이라는
 * 사실이 세 수수께끼의 출처이므로, 그림에 층을 더 얹으면 이 장의 출발점이 흐려진다.
 * 세 수수께끼가 각각 화살표의 다른 자리를 친다는 것을 번호 고리로 맞춰 둔다.
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 474;
    const g = [];

    g.push(txt(36, 26, '소박한 그림 — 이름의 의미는 그것이 가리키는 것이다', { cls: 'ink bold' }));

    // 위 — 화살표 하나가 전부인 그림
    g.push(box(36, 44, 808, 150, { stroke: CG, sw: 1, rx: 6 }));

    g.push(box(96, 92, 178, 52, { fill: C1, op: 0.12, stroke: C1, sw: 1.6 }));
    g.push(txt(185, 124, '이름 ‘샛별’', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ring(66, 118, '1', C1));

    g.push(arw(284, 118, 628, 118, { col: C1, marker: 'ar1', sw: 2.4 }));
    g.push(txt(456, 106, '가리킨다 — 그리고 이것이 의미의 전부다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(ring(456, 146, '2', C2));

    g.push(circ(680, 118, 38, { fill: C2, op: 0.24, stroke: C2, sw: 1.6 }));
    g.push(txt(680, 124, '금성', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ring(756, 118, '3', C3));

    g.push(txt(440, 180, '이름이 문장에 보태는 것은 저 대상 하나뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 아래 — 이 화살표만으로는 설명되지 않는 세 문장
    const rows = [
        {
            d: '1', col: C1, tag: '치는 자리 — 이름 칸',
            t: '두 이름이 든 두 문장의 정보값이 다르다',
            s: '‘샛별은 샛별이다’ 는 알려 주는 것이 없는데 ‘샛별은 개밥바라기다’ 는 알려 준다',
        },
        {
            d: '2', col: C2, tag: '치는 자리 — 화살표',
            t: '바꿔 넣으면 문장의 값이 바뀐다',
            s: '‘영수는 샛별이 새벽에 뜬다고 믿는다’ 가 참인데 이름만 바꾸면 거짓이 된다',
        },
        {
            d: '3', col: C3, tag: '치는 자리 — 대상 칸',
            t: '가리킬 것이 없는 이름이 있다',
            s: '‘페가수스는 없다’ 를 우리는 이해하고 참이라 여긴다. 그런데 화살표 끝이 비어 있다',
        },
    ];
    rows.forEach((r, i) => {
        const y0 = 214 + i * 72;
        g.push(ln([[36, y0], [844, y0]], { stroke: CG, sw: 1 }));
        g.push(ring(60, y0 + 30, r.d, r.col));
        g.push(txt(86, y0 + 26, r.t, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(86, y0 + 50, r.s, { cls: 'ink2', size: 'sm' }));
        g.push(txt(844, y0 + 26, r.tag, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[36, 430], [844, 430]], { stroke: CG, sw: 1 }));
    g.push(lines(36, 452, [
        '세 문장이 겨누는 것은 하나다 — ‘의미는 곧 지시체다’ 라는 등식. 다만 치는 자리가 각각 다르다',
    ]));

    return {
        name: 'phi-l-naive-picture',
        svg: svg({
            width: W, height: H,
            title: '소박한 그림과 프레게의 세 수수께끼',
            desc: '이름에서 대상으로 가는 화살표 하나가 소박한 그림의 전부이고, 세 수수께끼가 그 화살표의 서로 다른 자리를 친다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 2. 뜻과 지시체 — 두 갈래 길이 한 점에서 만난다
 *
 * 관념을 오른쪽에 따로 떼어 점선 상자에 가둔다. 넷째 절에서 퍼트넘이 치는 것이
 * ‘뜻을 각자의 머릿속 상태로 읽은 그림’ 이므로, 여기서 층을 갈라 두지 않으면
 * 그 논증이 프레게까지 무너뜨리는 것처럼 읽힌다.
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 426;
    const g = [];

    g.push(txt(36, 26, '뜻과 지시체 — 두 갈래 길이 한 점에서 만난다', { cls: 'ink bold' }));

    g.push(txt(126, 60, '표현', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(360, 60, '뜻 — 제시되는 방식', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(596, 60, '지시체', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));
    g.push(txt(767, 60, '관념 — 다른 층', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));

    // 위 갈래
    g.push(box(44, 88, 164, 52, { fill: C1, op: 0.12, stroke: C1, sw: 1.6 }));
    g.push(txt(126, 120, '‘샛별’', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(250, 82, 220, 64, { fill: C1, op: 0.08, stroke: C1, sw: 1.5 }));
    g.push(txt(360, 108, '새벽에 가장 밝게', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(360, 128, '빛나는 것으로 붙든다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(208, 114, 244, 114, { col: C1, marker: 'ar1' }));
    g.push(arw(470, 114, 552, 140, { col: C1, marker: 'ar1' }));

    // 아래 갈래
    g.push(box(44, 176, 164, 52, { fill: C2, op: 0.12, stroke: C2, sw: 1.6 }));
    g.push(txt(126, 208, '‘개밥바라기’', { anchor: 'middle', cls: 'ink bold' }));
    g.push(box(250, 170, 220, 64, { fill: C2, op: 0.08, stroke: C2, sw: 1.5 }));
    g.push(txt(360, 196, '저녁에 가장 밝게', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(360, 216, '빛나는 것으로 붙든다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(208, 202, 244, 202, { col: C2, marker: 'ar2' }));
    g.push(arw(470, 202, 552, 176, { col: C2, marker: 'ar2' }));

    // 만나는 한 점
    g.push(circ(596, 158, 44, { fill: C3, op: 0.2, stroke: C3, sw: 1.8 }));
    g.push(txt(596, 164, '금성', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(596, 228, '두 뜻이 한 점에서 만난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 관념 — 층이 다르다
    g.push(box(690, 76, 154, 244, { stroke: CG, sw: 1.4, dash: '6 4', rx: 8 }));
    ['영수의 관념', '민아의 관념', '철수의 관념'].forEach((s, i) => {
        g.push(box(706, 96 + i * 56, 122, 40, { stroke: CK, sw: 1.1, rx: 4 }));
        g.push(txt(767, 121 + i * 56, s, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(767, 282, '사람마다 다르고', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(767, 302, '서로 견줄 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[36, 340], [844, 340]], { stroke: CG, sw: 1 }));
    g.push(lines(36, 362, [
        '두 이름은 뜻이 다르고 지시체가 같다. 그래서 ‘샛별은 개밥바라기다’ 가 알려 주는 것이 있다',
        '화살표는 한쪽으로만 간다. 뜻이 지시체를 정하지만, 지시체가 같다고 두 뜻이 같아지지는 않는다',
        '망원경에 비유하면 달이 지시체, 렌즈에 맺힌 상이 뜻, 각자의 망막에 맺힌 상이 관념이다. 뜻은 여럿이 함께 붙들 수 있다',
    ]));

    return {
        name: 'phi-l-sense-reference',
        svg: svg({
            width: W, height: H,
            title: '표현 · 뜻 · 지시체의 삼각 구도',
            desc: '샛별과 개밥바라기는 뜻이 다른 두 갈래로 갈라져 금성이라는 한 지시체에서 만나고, 관념은 그와 다른 층에 있다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 3. 고정지시어 — 세계를 옮겨 다니며 무엇을 집는가
 *
 * 세 줄을 같은 꼴로 그린다. 셋째 줄이 없으면 ‘이름은 고정, 기술은 비고정’ 이라는
 * 오해가 그대로 굳는다. 고정·비고정의 구분과 이름·기술의 구분은 다른 구분이다.
 * ================================================================== */
add((() => {
    const W = 890;
    const H = 492;
    const g = [];

    const cols = [272, 472, 672];
    const CW = 182;
    const heads = [
        ['실제 세계', '그가 알렉산더를 가르쳤다'],
        ['가능세계 w~1', '그는 정치로 갔고', '다른 이가 가르쳤다'],
        ['가능세계 w~2', '알렉산더에게', '스승이 없었다'],
    ];

    g.push(txt(36, 26, '고정지시어 — 세계가 바뀌어도 같은 것을 가리키는가', { cls: 'ink bold' }));

    cols.forEach((x, i) => {
        g.push(box(x, 68, CW, 348, { stroke: CG, sw: 1, rx: 6 }));
        g.push(txt(x + CW / 2, 92, heads[i][0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        for (let k = 1; k < heads[i].length; k += 1) {
            g.push(txt(x + CW / 2, 92 + k * 18, heads[i][k], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });

    const rows = [
        {
            kind: '이름', expr: '‘아리스토텔레스’', col: C1, verdict: '고정지시어',
            cells: ['아리스토텔레스', '아리스토텔레스', '아리스토텔레스'],
        },
        {
            kind: '확정 기술', expr: '‘알렉산더를 가르친 사람’', col: C2, verdict: '비고정지시어',
            cells: ['아리스토텔레스', '그 다른 사람', null],
        },
        {
            kind: '확정 기술', expr: '‘가장 작은 소수’', col: C3, verdict: '고정지시어 — 확정 기술인데도',
            cells: ['2', '2', '2'],
        },
    ];

    rows.forEach((r, i) => {
        const y0 = 152 + i * 90;
        const yc = y0 + 36;
        g.push(box(36, y0 + 8, 214, 56, { fill: r.col, op: 0.1, stroke: r.col, sw: 1.6 }));
        g.push(txt(143, y0 + 30, r.kind, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(143, y0 + 52, r.expr, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(143, y0 + 82, r.verdict, { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));

        g.push(ln([[250, yc], [854, yc]], { stroke: CG, sw: 1, dash: '4 4' }));
        cols.forEach((x, k) => {
            const hit = r.cells[k];
            if (hit === null) {
                g.push(arw(x + 30, yc, x + 116, yc, { col: CK, marker: 'ark', sw: 1.8, dash: '5 4' }));
                g.push(circ(x + 134, yc, 7, { stroke: CK, sw: 1.6, dash: '3 3' }));
                g.push(txt(x + CW / 2, yc + 30, '가리킬 것이 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            } else {
                const mk = r.col === C1 ? 'ar1' : r.col === C2 ? 'ar2' : 'ar3';
                g.push(arw(x + 30, yc, x + 116, yc, { col: r.col, marker: mk, sw: 2 }));
                g.push(pdot(x + 134, yc, r.col, 7));
                g.push(txt(x + CW / 2, yc + 30, hit, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            }
        });
    });

    g.push(ln([[36, 432], [854, 432]], { stroke: CG, sw: 1 }));
    g.push(lines(36, 454, [
        '고정지시어는 그 대상이 있는 세계라면 어디서나 그 대상을 가리키고 다른 것을 가리키는 일이 없다',
        '갈리는 것은 표현의 문법이 아니라 세계마다 다른 것을 골라내느냐다. 아래 줄이 그것을 보인다',
    ]));

    return {
        name: 'phi-l-rigid',
        svg: svg({
            width: W, height: H,
            title: '고정지시어와 비고정지시어',
            desc: '이름은 세 세계에서 모두 같은 대상을 집고 확정 기술은 세계마다 다른 것을 집지만, 가장 작은 소수처럼 고정인 확정 기술도 있다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4. 쌍둥이 지구
 *
 * 두 상자를 글자 하나까지 같게 적는 것이 이 그림의 전부다. 오른쪽 상자를
 * 조금이라도 다르게 적으면 논증이 성립하지 않는다.
 * ================================================================== */
add((() => {
    const W = 880;
    const H = 444;
    const g = [];

    g.push(txt(36, 26, '쌍둥이 지구 — 머릿속은 같은데 낱말이 닿는 곳이 다르다', { cls: 'ink bold' }));

    const head = ['머릿속 — 심리 상태', '맑고 마실 수 있는 액체', '강과 바다를 채우는 것', '조성은 아직 아무도 모른다'];

    const panel = (x, world, person, stuff, col) => {
        const cx = x + 175;
        const out = [];
        out.push(box(x, 48, 350, 300, { stroke: CG, sw: 1, rx: 6 }));
        out.push(txt(cx, 74, world, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(cx, 98, person, { anchor: 'middle', cls: 'ink bold' }));

        out.push(box(x + 18, 110, 314, 104, { fill: C1, op: 0.1, stroke: C1, sw: 1.6 }));
        out.push(txt(cx, 130, head[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(x + 40, 154, head[1], { cls: 'ink', size: 'sm' }));
        out.push(txt(x + 40, 174, head[2], { cls: 'ink', size: 'sm' }));
        out.push(txt(x + 40, 194, head[3], { cls: 'ink', size: 'sm' }));

        out.push(box(x + 80, 228, 190, 38, { stroke: CK, sw: 1.3 }));
        out.push(txt(cx, 253, '낱말 ‘물’', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(arw(cx, 266, cx, 290, { col, marker: col === C2 ? 'ar2' : 'ar3', sw: 2.2 }));
        out.push(box(x + 110, 294, 130, 38, { fill: col, op: 0.2, stroke: col, sw: 1.6 }));
        out.push(txt(cx, 319, stuff, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };

    g.push(panel(36, '지구, 1750년', '오스카', 'H~2O', C2));
    g.push(panel(494, '쌍둥이 지구, 1750년', '쌍둥이 오스카', 'XYZ', C3));

    // 가운데 — 같다 / 다르다
    g.push(ln([[416, 156], [464, 156]], { stroke: CK, sw: 2.6 }));
    g.push(ln([[416, 168], [464, 168]], { stroke: CK, sw: 2.6 }));
    g.push(txt(440, 196, '분자 하나까지', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(440, 214, '같은 상태다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[416, 307], [464, 307]], { stroke: C2, sw: 2.6 }));
    g.push(ln([[416, 319], [464, 319]], { stroke: C2, sw: 2.6 }));
    g.push(ln([[424, 328], [456, 298]], { stroke: C2, sw: 2.6 }));
    g.push(txt(440, 356, '외연이 다르다', { anchor: 'middle', cls: 'ink2 bold', size: 'sm' }));

    g.push(ln([[36, 384], [844, 384]], { stroke: CG, sw: 1 }));
    g.push(lines(36, 406, [
        '설정 — 두 행성은 모든 면에서 같고 다른 것은 하나뿐이다. 그곳의 강과 바다를 채우는 액체는 H~2O 가 아니라 XYZ 이며 겉보기 · 맛 · 쓰임은 똑같다',
        '위 상자가 같다는 것과 아래 화살표가 다른 곳에 닿는다는 것. 이 둘이 한 그림에 같이 있으므로 머릿속이 외연을 혼자서 정하지 못한다',
    ]));

    return {
        name: 'phi-l-twin-earth',
        svg: svg({
            width: W, height: H,
            title: '쌍둥이 지구 사고실험',
            desc: '오스카와 쌍둥이 오스카의 심리 상태는 같은데 두 사람의 낱말 물은 각각 H2O 와 XYZ 에 닿는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5. 취소 가능성 검사
 *
 * 두 칸을 같은 꼴로 만들고 붙이는 한마디까지 같은 자리에 놓는다. 검사가 같아야
 * 결과의 차이가 문장에서 온 것이 된다. 아래 칸이 모순이 아니라는 판정 자체가
 * 이 그림이 보여야 하는 것이므로 문장을 통째로 적어 눈으로 읽게 한다.
 * ================================================================== */
add((() => {
    const W = 900;
    const H = 414;
    const g = [];

    g.push(txt(36, 26, '취소 가능성 검사 — 뒤에 한마디를 붙여 모순이 되는지 본다', { cls: 'ink bold' }));
    g.push(txt(36, 50, '두 문장에 같은 검사를 건다. 갈리는 것은 검사가 아니라 문장이다', { cls: 'ink2', size: 'sm' }));

    const card = (y0, tag, tagCol, carry, tested, ok, verdict, notes) => {
        const out = [];
        out.push(box(36, y0, 828, 112, { stroke: CG, sw: 1, rx: 6 }));
        out.push(txt(56, y0 + 28, tag, { cls: (tagCol === C1 ? 'f1' : 'f2') + ' bold' }));
        out.push(txt(104, y0 + 28, carry, { cls: 'ink', size: 'sm' }));

        out.push(box(56, y0 + 46, 380, 50, { stroke: CK, sw: 1.3 }));
        out.push(txt(246, y0 + 68, '붙여 본다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(246, y0 + 88, tested, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

        out.push(arw(446, y0 + 71, 476, y0 + 71, { col: CK, sw: 1.8 }));

        const col = ok ? C1 : C2;
        out.push(box(486, y0 + 46, 172, 50, { fill: col, op: 0.12, stroke: col, sw: 1.6 }));
        out.push(ok ? check(516, y0 + 71, col) : cross(516, y0 + 71, col));
        out.push(txt(546, y0 + 76, verdict, { cls: 'ink bold', size: 'sm' }));

        out.push(txt(676, y0 + 64, notes[0], { cls: 'ink2', size: 'sm' }));
        out.push(txt(676, y0 + 84, notes[1], { cls: 'ink2', size: 'sm' }));
        return out.join('');
    };

    g.push(card(
        70, '함의', C2,
        '‘학생 셋이 왔다’ 가 딸고 오는 것 — 학생이 왔다',
        '‘학생 셋이 왔다. 사실은 아무도 안 왔다’',
        false, '모순이다',
        ['취소되지 않는다', '문장의 뜻에 들어 있다'],
    ));
    g.push(card(
        200, '함축', C1,
        '‘어떤 학생이 왔다’ 가 딸고 오는 것 — 전부가 온 것은 아니다',
        '‘어떤 학생이 왔다. 사실은 전부 왔다’',
        true, '모순이 아니다',
        ['취소된다', '맥락과 협력 가정이 만든 것이다'],
    ));

    g.push(ln([[36, 340], [864, 340]], { stroke: CG, sw: 1 }));
    g.push(lines(36, 362, [
        '아래 칸의 문장은 얄밉게 들릴지언정 모순으로 들리지 않는다. 그것이 함축이라는 판정의 근거다',
        '함의는 문장의 뜻만으로 정해지고, 함축은 문장의 뜻에 맥락과 협력 가정이 더해져 정해진다',
        '예외 — 관습적 함축은 취소되지 않는다. ‘그는 가난하지만 정직하다’ 가 실어 나르는 것이 그렇다. 이 검사는 대화상 함축에 대한 것이다',
    ]));

    return {
        name: 'phi-l-cancel-test',
        svg: svg({
            width: W, height: H,
            title: '취소 가능성 검사',
            desc: '함의는 취소하면 모순이 되고 함축은 취소해도 모순이 되지 않는다',
            body: g.join(''),
        }),
    };
})());

export default figures;
