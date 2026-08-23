/**
 * 철학 문서 3·4장의 그림.
 *   3장 — 철학은 무엇을 하는 일인가
 *   4장 — 논증을 다루는 기술
 *
 * 이름은 모두 `phi-m-` 로 시작한다(3·4장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 상자와 화살표만으로 되는 그림(판정 흐름·계보·시대 지도)은 d2/philosophy/ 에 있다.
 * 여기 있는 것은 격자·영역·축처럼 d2 가 그리지 못하는 것들이다.
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

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = CK, sw = 1.6, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화살촉 없는 꺾은선. */
function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * 화살표. marker 와 stroke 를 짝지어 넘겨야 색이 맞는다
 * (ar1/ar2/ar3 의 화살촉 색은 각각 s1/s2/s3, ark 는 ink2 로 고정되어 있다).
 */
function arw(d, { stroke = CK, marker = 'ark', sw = 1.7, dash } = {}) {
    return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round" marker-end="url(#${marker})"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C2, r = 5.5) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/* ------------------------------------------------------------------ *
 * 1. 세 등급 — 정설 / 다수설·소수설 / 미결
 *
 * 이 문서가 합의되지 않은 내용을 어떻게 적는지를 독자에게 처음 알리는 그림이다.
 * ‘철학은 답이 없다’ 는 반응에 대한 대답이 여기 들어 있다. 세 칸 중 하나만
 * 정해지지 않은 칸이고, 나머지 둘은 정해져 있다는 것이 요점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 824;
    const H = 356;
    const g = [];
    const cols = [[36, 152], [188, 300], [488, 300]];
    const heads = ['등급', '이 문서가 쓰는 방식', '이 문서 안의 예'];
    const rows = [
        {
            col: C1,
            name: '정설',
            how: ['그냥 서술한다.', '아무 표시도 붙이지 않는다'],
            ex: ['게티어 사례가 정당화된 참인 믿음을', '무너뜨린다 (5장)'],
        },
        {
            col: C2,
            name: '다수설·소수설',
            how: ['다수설을 먼저 쓰고', '소수설과 그 근거를 반드시 잇는다'],
            ex: ['물리주의와 이원론 (14장)', '과학적 실재론과 반실재론 (9장)'],
        },
        {
            col: C3,
            name: '미결',
            how: ['입장들을 나란히 놓는다.', '저자가 고르지 않는다'],
            ex: ['인격 동일성의 기준 (13장)', '결과주의·의무론·덕 윤리 (18장)'],
        },
    ];

    g.push(txt(36, 28, '이 문서가 쓰는 세 등급', { cls: 'ink bold' }));

    const hy = 46;
    const hh = 32;
    cols.forEach(([x, w], i) => {
        g.push(box(x, hy, w, hh, { fill: CG, op: 0.45, stroke: CK, sw: 1.2 }));
        g.push(txt(x + 12, hy + 21, heads[i], { cls: 'ink bold', size: 'sm' }));
    });

    const ry = hy + hh;
    const rh = 68;
    rows.forEach((row, i) => {
        const y = ry + i * rh;
        cols.forEach(([x, w], j) => {
            g.push(box(x, y, w, rh, {
                fill: j === 0 ? row.col : 'none', op: j === 0 ? 0.14 : 1,
                stroke: j === 0 ? row.col : CK, sw: j === 0 ? 1.8 : 1.2,
            }));
        });
        g.push(txt(cols[0][0] + 12, y + 40, row.name, { cls: 'ink bold' }));
        g.push(txt(cols[1][0] + 12, y + 27, row.how[0], { cls: 'ink', size: 'sm' }));
        g.push(txt(cols[1][0] + 12, y + 48, row.how[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(cols[2][0] + 12, y + 27, row.ex[0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(cols[2][0] + 12, y + 48, row.ex[1], { cls: 'ink2', size: 'sm' }));
    });

    const ny = ry + 3 * rh + 26;
    g.push(txt(36, ny, '‘답이 없다’ 와 ‘아직 정해지지 않았다’ 는 다르다. 세 줄 가운데 아래 하나만 정해지지 않은 것이고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, ny + 19, '그 줄에서도 무엇이 다투어지는지·각 입장이 무엇을 대가로 치르는지는 정해져 있다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-three-grades',
        svg: svg({
            width: W, height: H,
            title: '합의 정도의 세 등급',
            desc: '정설·다수설과 소수설·미결 셋을 이 문서가 각각 어떻게 적는지',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 타당·건전 되짚기 격자
 *
 * 정의는 논리학 문서 3장에 있다. 이 문서는 이 네 칸만 쓴다.
 * 두 물음이 서로 다른 물음이라는 것을 눈으로 붙잡아 두려는 그림이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 806;
    const H = 350;
    const g = [];

    g.push(txt(36, 28, '타당한가와 전제가 참인가는 다른 물음이다', { cls: 'ink bold' }));

    const cx = [36, 176, 470];
    const cw = [140, 294, 294];
    const hy = 48;
    const hh = 42;

    g.push(box(cx[0], hy, cw[0], hh, { fill: CG, op: 0.45, sw: 1.2 }));
    g.push(box(cx[1], hy, cw[1], hh, { fill: CG, op: 0.45, sw: 1.2 }));
    g.push(box(cx[2], hy, cw[2], hh, { fill: CG, op: 0.45, sw: 1.2 }));
    g.push(txt(cx[1] + 12, hy + 26, '전제가 실제로 모두 참', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx[2] + 12, hy + 26, '전제 가운데 거짓이 있다', { cls: 'ink bold', size: 'sm' }));

    const cells = [
        {
            name: '타당', col: C1,
            a: ['건전하다', '결론은 반드시 참이다'],
            b: ['건전하지 않다', '결론이 참인지는 이 논증으로 알 수 없다'],
        },
        {
            name: '부당', col: C2,
            a: ['건전하지 않다', '반례가 있다. 결론이 참이더라도 이 논증 덕분이 아니다'],
            b: ['건전하지 않다', '전제도 어긋났고 짜임도 어긋났다'],
        },
    ];

    const ry = hy + hh;
    const rh = 84;
    cells.forEach((c, i) => {
        const y = ry + i * rh;
        g.push(box(cx[0], y, cw[0], rh, { fill: c.col, op: 0.14, stroke: c.col, sw: 1.8 }));
        g.push(txt(cx[0] + 14, y + 48, c.name, { cls: 'ink bold' }));
        g.push(box(cx[1], y, cw[1], rh, { fill: i === 0 ? C1 : 'none', op: i === 0 ? 0.07 : 1, sw: 1.2 }));
        g.push(box(cx[2], y, cw[2], rh, { sw: 1.2 }));
        g.push(txt(cx[1] + 12, y + 32, c.a[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx[1] + 12, y + 56, c.a[1], { cls: 'ink2', size: 'sm' }));
        g.push(txt(cx[2] + 12, y + 32, c.b[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx[2] + 12, y + 56, c.b[1], { cls: 'ink2', size: 'sm' }));
    });

    const ny = ry + 2 * rh + 28;
    g.push(txt(36, ny, '왼쪽 물음(타당한가)은 전제를 참이라고 쳤을 때의 물음이고, 위쪽 물음(전제가 참인가)은 세상에 대한 물음이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, ny + 19, '반례 하나 — 전제를 모두 참으로 만들면서 결론을 거짓으로 만드는 상황 — 가 있으면 아래 줄이고, 없으면 위 줄이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-valid-sound',
        svg: svg({
            width: W, height: H,
            title: '타당·건전 되짚기',
            desc: '타당한가와 전제가 실제로 참인가를 두 축으로 놓은 네 칸',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 필요조건과 충분조건
 *
 * 개념 분석이 무엇을 내놓으려 하는지 보이는 그림. 분석은 필요충분조건을
 * 노리고, 반례는 두 방향 가운데 어느 쪽이 무너졌는지로 갈린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 848;
    const H = 320;
    const g = [];

    g.push(txt(30, 28, '조건 C 와 개념 X 사이에 있을 수 있는 세 가지 관계', { cls: 'ink bold' }));

    const panels = [
        {
            cx: 160, col: C1,
            head: 'C 는 X 의 필요조건',
            outer: 'C 를 만족하는 것', inner: 'X 인 것',
            note: ['X 이면 반드시 C 다.', 'C 가 아니면 X 가 아니다.', '여기서 나올 반례 — C 인데 X 가 아닌 것'],
        },
        {
            cx: 430, col: C2,
            head: 'C 는 X 의 충분조건',
            outer: 'X 인 것', inner: 'C 를 만족하는 것',
            note: ['C 이면 반드시 X 다.', 'X 라고 C 인 것은 아니다.', '여기서 나올 반례 — X 인데 C 가 아닌 것'],
        },
        {
            cx: 700, col: C3,
            head: 'C 는 X 의 필요충분조건',
            outer: 'X 인 것 = C 를 만족하는 것', inner: null,
            note: ['두 방향이 다 성립한다.', '개념 분석이 노리는 것이 이것이다.', '반례가 어느 쪽에서도 나오지 않아야 한다'],
        },
    ];

    for (const p of panels) {
        g.push(txt(p.cx, 58, p.head, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(ell(p.cx, 140, 118, 58, { fill: p.col, op: 0.1, stroke: p.col, sw: 1.8 }));
        if (p.inner) {
            g.push(ell(p.cx, 152, 62, 32, { fill: p.col, op: 0.24, stroke: p.col, sw: 1.6 }));
            g.push(txt(p.cx, 100, p.outer, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            g.push(txt(p.cx, 157, p.inner, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        } else {
            g.push(ell(p.cx, 140, 108, 48, { stroke: p.col, sw: 1.4, dash: '5 4' }));
            g.push(txt(p.cx, 137, 'X 인 것', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            g.push(txt(p.cx, 158, '= C 를 만족하는 것', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        p.note.forEach((s, i) => {
            g.push(txt(p.cx, 226 + i * 20, s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
    }

    g.push(txt(30, 302, '분석안을 내놓는다는 것은 오른쪽 그림이 되기를 바라며 조건 C 를 제안하는 일이고, 반례를 든다는 것은 왼쪽이나 가운데 그림임을 보이는 일이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-necessary-sufficient',
        svg: svg({
            width: W, height: H,
            title: '필요조건·충분조건·필요충분조건',
            desc: '조건과 개념의 포함 관계 세 가지와 각각에서 나오는 반례의 방향',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 반성적 평형
 *
 * 어느 쪽도 고정점이 아니라는 것이 요점이다. 화살표가 양방향인 그림이
 * ‘직관이 최종 심판이다’ 와 ‘원리가 최종 심판이다’ 를 함께 막는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 828;
    const H = 366;
    const g = [];

    g.push(txt(36, 28, '반성적 평형 — 양쪽을 서로 고쳐 가며 맞춘다', { cls: 'ink bold' }));

    g.push(box(56, 62, 288, 66, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
    g.push(txt(200, 90, '개별 사례에 대한 판단', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(200, 111, '‘이 경우에는 이렇다’ 는 직관', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(box(484, 62, 288, 66, { fill: C2, op: 0.1, stroke: C2, sw: 1.8 }));
    g.push(txt(628, 90, '일반 원리', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(628, 111, '모든 경우를 한꺼번에 판정하는 규칙', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw('M344 82 L474 82', { stroke: C1, marker: 'ar1' }));
    g.push(arw('M484 110 L354 110', { stroke: C2, marker: 'ar2' }));

    g.push(box(268, 186, 292, 62, { fill: C3, op: 0.1, stroke: C3, sw: 1.8 }));
    g.push(txt(414, 212, '배경 이론과 다른 분야의 지식', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(414, 233, '과학·수학·역사에서 이미 알아낸 것', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw('M300 186 L222 134', { stroke: C3, marker: 'ar3', sw: 1.5 }));
    g.push(arw('M528 186 L606 134', { stroke: C3, marker: 'ar3', sw: 1.5 }));

    const ny = 284;
    g.push(pdot(42, ny - 5, C1, 4.5));
    g.push(txt(56, ny, '판단이 원리를 시험한다 — 원리가 어떤 사례를 도저히 받아들일 수 없게 판정하면 원리를 고친다.', { cls: 'ink2', size: 'sm' }));
    g.push(pdot(42, ny + 19, C2, 4.5));
    g.push(txt(56, ny + 24, '원리가 판단을 시험한다 — 원리가 다른 곳에서 잘 버티면 어긋난 그 판단 쪽을 버린다.', { cls: 'ink2', size: 'sm' }));
    g.push(pdot(42, ny + 43, C3, 4.5));
    g.push(txt(56, ny + 48, '배경 이론이 둘 다 민다 — 판단이 어디서 나왔는지 알고 나면 그 판단의 무게가 달라진다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, ny + 74, '고정점이 없다는 것이 요점이다. 직관도 원리도 무조건 이기지 않는다. 그래서 결과는 증명이 아니라 잠정적인 균형이다.', { cls: 'ink', size: 'sm' }));

    return {
        name: 'phi-m-equilibrium',
        svg: svg({
            width: W, height: H,
            title: '반성적 평형',
            desc: '개별 판단과 일반 원리가 서로를 고치고 배경 이론이 둘 다 미는 구조',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 재구성 전과 후
 *
 * 4장에서 가장 실용적인 기술이 무엇을 산출하는지 한눈에 보이는 그림.
 * 왼쪽에서 오른쪽으로 가면서 무엇이 사라지고 무엇이 새로 나타나는지가 요점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 884;
    const H = 396;
    const g = [];

    g.push(txt(30, 28, '재구성 — 산문 한 문단에서 번호 붙은 논증으로', { cls: 'ink bold' }));

    g.push(txt(30, 54, '원래 글', { cls: 'ink bold', size: 'sm' }));
    g.push(box(30, 64, 372, 244, { fill: CG, op: 0.3, sw: 1.2 }));
    const prose = [
        '도서관 개방 시간을 늘려야 한다. 시험 기간이면',
        '자리가 모자라 학생들이 복도에 앉아 있다. 학교는',
        '학생이 공부할 자리를 마련할 책임이 있다.',
        '예산이 든다는 반대가 있지만, 지난해 예산은',
        '쓰지 못하고 남았다.',
    ];
    prose.forEach((s, i) => g.push(txt(46, 96 + i * 26, s, { cls: 'ink', size: 'sm' })));
    g.push(txt(46, 250, '걷어낸 것 — ‘복도에 앉아 있다’ 는 자리가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 270, '모자란다는 전제의 예시일 뿐 따로 된 전제가 아니다.', { cls: 'ink2', size: 'sm' }));

    g.push(arw('M406 186 L440 186', { stroke: CK, marker: 'ark', sw: 2 }));

    g.push(txt(452, 54, '재구성한 논증', { cls: 'ink bold', size: 'sm' }));
    g.push(box(452, 64, 402, 244, { fill: C1, op: 0.07, stroke: C1, sw: 1.6 }));
    const lines = [
        ['(P1)', '시험 기간에 도서관 자리가 모자란다'],
        ['(P2)', '학교는 학생이 공부할 자리를 마련할 책임이 있다'],
        ['(P3)', '개방 시간을 늘리면 자리 부족이 줄어든다'],
        ['(P4)', '남은 예산으로 비용을 댈 수 있다'],
    ];
    lines.forEach(([tag, s], i) => {
        const y = 96 + i * 30;
        g.push(txt(468, y, tag, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(508, y, s, { cls: 'ink', size: 'sm' }));
    });
    g.push(txt(508, 235, '(P3) 은 글에 없다 — 채워 넣은 숨은 전제', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[468, 252], [838, 252]], { stroke: C1, sw: 1.6 }));
    g.push(txt(468, 276, '(C)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(508, 276, '도서관 개방 시간을 늘려야 한다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(30, 340, '오른쪽 형태가 되어야 비로소 물을 수 있는 것들이 있다. 어느 전제를 공격할 것인가, 전제가 다 참이면 결론이 정말 따라 나오는가,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 360, '그리고 (P3) 처럼 글쓴이가 말하지 않고 기대고 있던 것이 무엇인가. 산문 상태에서는 이 물음들이 서로 엉켜 있다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-reconstruct-before-after',
        svg: svg({
            width: W, height: H,
            title: '논증 재구성의 전과 후',
            desc: '산문 한 문단을 번호 붙은 전제와 결론으로 옮긴 결과',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 허수아비와 강철인간
 *
 * 자비의 원리를 축 하나로 보인다. 왼쪽으로 옮겨 놓고 때리는 것이 허수아비고,
 * 오른쪽 끝까지 밀어 올린 뒤 때리는 것이 강철인간이다. 다만 오른쪽으로도
 * 너무 나가면 상대의 주장이 아니게 된다는 것을 함께 표시한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 372;
    const g = [];

    g.push(txt(34, 28, '같은 주장의 여러 판본 — 공격은 어디에 맞아야 하는가', { cls: 'ink bold' }));

    const ax = 200;
    g.push(arw(`M70 ${ax} L800 ${ax}`, { stroke: CK, marker: 'ark', sw: 1.6 }));
    g.push(txt(806, ax + 5, '강함', { cls: 'ink2', size: 'sm' }));
    g.push(txt(70, ax + 24, '약함', { cls: 'ink2', size: 'sm' }));
    g.push(txt(70, ax + 48, '주장의 강도 — 오른쪽으로 갈수록 방어하기 쉽고 반박하기 어렵다', { cls: 'ink2', size: 'sm' }));

    const marks = [
        {
            x: 180, col: C2, name: '허수아비 판본',
            up: ['상대가 말하지 않은', '약한 형태로 고쳐 놓은 것'],
            down: ['여기를 때리면 상대는', '아무것도 잃지 않는다'],
        },
        {
            x: 428, col: CK, name: '글에 적힌 그대로',
            up: ['상대가 실제로 쓴 문장', '읽기가 갈리는 자리가 남아 있다'],
            down: ['여기부터가', '정당한 공격 범위다'],
        },
        {
            x: 656, col: C1, name: '강철인간 판본',
            up: ['상대가 받아들일 수 있는', '가장 강한 형태'],
            down: ['여기를 무너뜨려야 상대가', '물러설 이유가 생긴다'],
        },
    ];

    for (const m of marks) {
        g.push(pdot(m.x, ax, m.col, 6));
        g.push(txt(m.x, 74, m.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(m.x, 100, m.up[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(m.x, 120, m.up[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(ln([[m.x, 132], [m.x, ax - 12]], { stroke: m.col, sw: 1.2, dash: '4 4' }));
        g.push(ln([[m.x, ax + 12], [m.x, 268]], { stroke: m.col, sw: 1.2, dash: '4 4' }));
        g.push(txt(m.x, 292, m.down[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(m.x, 312, m.down[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    g.push(ln([[744, ax - 16], [744, ax + 16]], { stroke: C3, sw: 1.6, dash: '4 3' }));
    g.push(txt(800, ax + 48, '이 오른쪽은 상대의 주장이 아니다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(34, 356, '오른쪽으로 옮기는 것이 언제나 옳은 것은 아니다. 상대가 실제로 지지할 수 없는 판본을 세워 주는 것도 상대의 주장을 바꿔치기하는 일이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-straw-steel',
        svg: svg({
            width: W, height: H,
            title: '허수아비와 강철인간',
            desc: '주장의 강도 축 위에서 허수아비 판본·원래 주장·강철인간 판본의 자리',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 일곱 오류가 각각 어느 관문에서 걸리는가
 *
 * 오류 이름을 외우는 대신 평가 절차의 어느 단계가 깨졌는지로 정리한다.
 * 이름이 아니라 깨진 조건을 대야 판정이 된다는 것이 4장의 주장이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 452;
    const g = [];

    g.push(txt(34, 28, '오류 이름 대신 깨진 관문을 짚는다', { cls: 'ink bold' }));
    g.push(txt(34, 50, '왼쪽은 재구성을 마친 논증에 차례로 던지는 물음이고, 오른쪽은 그 물음에서 걸리는 흔한 실패다.', { cls: 'ink2', size: 'sm' }));

    const rows = [
        {
            col: C1, n: '1',
            q: ['결론과 전제를 제대로 짚었는가'],
            f: ['허수아비 — 상대가 내놓지 않은 결론을 무너뜨린다.', '무너진 것은 상대의 논증이 아니다'],
        },
        {
            col: C2, n: '2',
            q: ['전제가 결론과 관련이 있는가'],
            f: ['인신공격 — 말한 사람의 성질을 전제로 쓴다.', '증언의 신빙성을 다투는 자리에서는 관련이 생긴다'],
        },
        {
            col: C3, n: '3',
            q: ['전제가 결론을 얼마나 뒷받침하는가'],
            f: ['성급한 일반화 — 표본이 결론의 범위를 못 받친다.', '미끄러운 비탈 — 고리마다의 기제를 대지 못한다'],
        },
        {
            col: C1, n: '4',
            q: ['전제를 받아들일 이유가 있는가'],
            f: ['거짓 딜레마 — 선택지를 둘로 좁힌 전제가 거짓이다.', '권위 호소 — 네 조건을 못 채운 권위를 전제로 쓴다'],
        },
        {
            col: C2, n: '5',
            q: ['아직 결론을 믿지 않는 사람에게', '이유가 되는가'],
            f: ['선결문제 요구 — 결론을 이미 받아들인 사람만', '전제를 받아들일 수 있다. 타당해도 소용이 없다'],
        },
    ];

    const y0 = 74;
    const rh = 68;
    rows.forEach((r, i) => {
        const y = y0 + i * rh;
        g.push(box(34, y, 336, 58, { fill: r.col, op: 0.1, stroke: r.col, sw: 1.6 }));
        g.push(txt(50, y + 35, r.n, { cls: 'ink bold' }));
        if (r.q.length === 1) {
            g.push(txt(74, y + 35, r.q[0], { cls: 'ink', size: 'sm' }));
        } else {
            g.push(txt(74, y + 24, r.q[0], { cls: 'ink', size: 'sm' }));
            g.push(txt(74, y + 44, r.q[1], { cls: 'ink', size: 'sm' }));
        }
        g.push(arw(`M370 ${y + 29} L400 ${y + 29}`, { stroke: CK, marker: 'ark', sw: 1.4 }));
        g.push(box(408, y, 458, 58, { sw: 1.2 }));
        g.push(txt(422, y + 24, r.f[0], { cls: 'ink', size: 'sm' }));
        g.push(txt(422, y + 44, r.f[1], { cls: 'ink2', size: 'sm' }));
    });

    const ny = y0 + 5 * rh + 16;
    g.push(txt(34, ny, '오른쪽 칸의 이름들은 어느 것도 그 자체로 판정이 아니다. 판정은 왼쪽 물음 가운데 무엇이 왜 깨졌는지를 대는 것이고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(34, ny + 20, '같은 이름이 붙는 논증이라도 관문을 통과하면 좋은 논증이다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-m-fallacy-where',
        svg: svg({
            width: W, height: H,
            title: '일곱 오류가 걸리는 관문',
            desc: '논증 평가의 다섯 물음과 각 물음에서 걸리는 비형식 오류',
            body: g.join(''),
        }),
    };
})());

export default figures;
