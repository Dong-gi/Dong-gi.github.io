/**
 * 철학 17장(메타윤리)·18장(규범윤리 세 이론)·19장(응용윤리)의 그림.
 *
 * 이름은 모두 `phi-e-` 로 시작한다(이 세 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 여기 있는 것은 격자·축·배치도처럼 좌표가 필요한 그림뿐이다.
 * 상자와 화살표로 그릴 것(입장 지도, 논증 구조, 조건 흐름)은
 * d2/philosophy/phi-e-*.d2 에 있다.
 */
import { svg, txt, px } from './lib.mjs';

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

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 6) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 가위표. 성립하지 않는 짝 위에 얹는다. */
function xmark(x, y, s = 7, col = C2) {
    return `<path d="M${r2(x - s)} ${r2(y - s)} L${r2(x + s)} ${r2(y + s)} M${r2(x + s)} ${r2(y - s)} L${r2(x - s)} ${r2(y + s)}"`
        + ` stroke="${col}" stroke-width="2.4" stroke-linecap="round" fill="none"/>`;
}

/** 머리글 한 줄과 내용 여러 줄을 담는 격자 칸. */
function cell(x, y, w, h, head, lines, { accent = null, dash } = {}) {
    const g = [box(x, y, w, h, {
        fill: accent ?? 'none', op: accent ? 0.14 : 1,
        stroke: accent ?? CK, sw: accent ? 1.9 : 1.2, dash,
    })];
    g.push(txt(x + 11, y + 23, head, { cls: 'ink bold', size: 'sm' }));
    lines.forEach((s, i) => g.push(txt(x + 11, y + 44 + i * 17, s, { cls: 'ink2', size: 'sm' })));
    return g.join('');
}

/** 아래쪽 주석 줄. */
function notes(x, y, lines) {
    return lines.map((s, i) => txt(x, y + i * 21, s, { cls: 'ink2', size: 'sm' })).join('');
}

/* ================================================================== *
 * 17장 — 메타윤리
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 두 물음으로 가른 메타윤리 지도
 *
 * 실재론과 반실재론을 가르는 선이 세로선이 아니라는 것이 요점이다.
 * 왼쪽 위 칸 안에서 한 번 더 갈린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 864;
    const H = 452;
    const g = [];

    g.push(txt(40, 28, '메타윤리 입장을 두 물음으로 가른다', { cls: 'ink bold' }));

    const rx = 40;
    const rw = 176;
    const c1 = 216;
    const c2 = 520;
    const cw = 304;
    const hy = 48;
    const hh = 44;

    g.push(box(rx, hy, rw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(rx + 10, hy + 18, '아래로 — 도덕 문장은', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, hy + 34, '옆으로 — 도덕 사실이', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c1, hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(c1 + 11, hy + 27, '그 문장을 참으로 만들 것이 있다', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c2, hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(c2 + 11, hy + 27, '그런 것은 없다', { cls: 'ink bold', size: 'sm' }));

    const r1 = hy + hh;
    const rh = 124;
    g.push(box(rx, r1, rw, rh, { sw: 1.2 }));
    g.push(txt(rx + 10, r1 + 26, '믿음을 적는다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, r1 + 48, '참·거짓을 물을 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r1 + 68, '이 줄을 인지주의라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r1 + 86, '부른다', { cls: 'ink2', size: 'sm' }));

    g.push(cell(c1, r1, cw, rh, '도덕 실재론 · 주관주의 · 상대주의', [
        '참인 도덕 문장이 실제로 있다',
        '여기서 한 번 더 갈린다 — 그 참이',
        '우리 태도에 의존하는가 아닌가',
        '의존하지 않는다면 실재론이다',
    ], { accent: C1 }));

    g.push(cell(c2, r1, cw, rh, '오류 이론', [
        '도덕 문장은 사실을 적으려 하지만',
        '적을 사실이 없어 모두 거짓이다',
        '매키',
    ], { accent: C2 }));

    const r2y = r1 + rh;
    g.push(box(rx, r2y, rw, rh, { sw: 1.2 }));
    g.push(txt(rx + 10, r2y + 26, '태도를 드러낸다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, r2y + 48, '참·거짓을 묻는 것이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r2y + 68, '자리를 잘못 잡은 것이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r2y + 88, '이 줄이 비인지주의다', { cls: 'ink2', size: 'sm' }));

    g.push(cell(c1, r2y, cw, rh, '거의 비어 있던 칸', [
        '태도를 드러낼 뿐인 문장을 참으로',
        '만드는 사실이 따로 있다고 말하기',
        '어렵다. 준실재론이 이 칸을 다시',
        '채우려는 시도로 읽힌다',
    ], { dash: '5 4' }));

    g.push(cell(c2, r2y, cw, rh, '비인지주의', [
        '정서주의 · 규정주의 · 표현주의',
        '도덕 문장이 하는 일은 사실 보고가',
        '아니라 태도의 표현이다',
    ], { accent: C3 }));

    const ny = r2y + rh + 30;
    g.push(notes(40, ny, [
        '실재론과 반실재론을 가르는 선은 이 표의 세로선이 아니다. 왼쪽 위 칸 안에 한 번 더 그어진다',
        '상대주의는 왼쪽 위에 있다 — 참인 도덕 문장이 있되 그 참이 틀에 상대적이라고 본다',
        '어느 칸도 지워지지 않았다. 도덕 실재론과 반실재론의 다툼은 미결이고, 근소한 차이다',
    ]));

    return {
        name: 'phi-e-cognitivism-grid',
        svg: svg({
            width: W, height: H,
            title: '메타윤리 입장을 두 물음으로 가른 표',
            desc: '세로는 도덕 문장이 믿음을 적는가 태도를 드러내는가, 가로는 그 문장을 참으로 만들 사실이 있는가로 나눈 네 칸',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 열린 물음 논증
 *
 * 검사 자체와, 그 검사가 결정적이지 않다는 오늘의 평가를 한 그림에 담는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 384;
    const g = [];

    g.push(txt(40, 28, '열린 물음 검사 — 두 물음을 나란히 놓는다', { cls: 'ink bold' }));

    g.push(cell(40, 48, 396, 116, '닫힌 물음', [
        '‘그는 결혼하지 않은 성인 남자다.',
        '그런데 그는 총각인가?’',
        '물을 것이 남아 있지 않다. 두 말의 뜻이 같다',
    ], { accent: C3 }));

    g.push(cell(464, 48, 376, 116, '열린 물음', [
        '‘그 행위는 쾌락의 총량을 늘린다.',
        '그런데 그 행위는 좋은가?’',
        '여전히 물을 것이 남아 보인다',
    ], { accent: C2 }));

    g.push(px(436, 106, 462, 106, { cls: 's1', marker: 'ar1', width: 2 }));

    g.push(box(40, 182, 800, 66, { stroke: C1, sw: 1.6 }));
    g.push(txt(52, 206, '무어의 논증 — 어떤 자연적 성질을 넣어도 물음이 열려 있다면, 좋음은 그 성질이 아니다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(52, 230, '그러므로 좋음은 자연적 성질이 아니고, 더 쪼갤 수 없는 단순한 것이다', { cls: 'ink bold', size: 'sm' }));

    g.push(notes(40, 284, [
        '오늘의 표준 평가 — 이 논증은 결정적이지 않다. 두 자리에서 새어 나간다',
        '① 옳은 분석이라 해도 그것이 말하는 사람에게 곧바로 보이리라는 보장이 없다. 보이면 시시하고 안 보이면 열려 보인다',
        '② 11장의 후험적 필연 — 참인 동일성인데도 물음이 열려 보이는 일이 실제로 있다. 물과 그 화학적 구성이 그렇다',
        '결정적이지 않다는 것과 자연주의가 이겼다는 것은 다르다. 물음이 왜 열려 보이는지는 여전히 설명되어야 한다',
    ]));

    return {
        name: 'phi-e-open-question',
        svg: svg({
            width: W, height: H,
            title: '열린 물음 검사와 그 검사에 대한 오늘의 평가',
            desc: '닫힌 물음과 열린 물음을 나란히 놓고, 무어의 결론과 그 결론이 결정적이지 않은 두 이유를 적었다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 도덕 속성의 수반
 *
 * 11장의 수반을 도덕에 대었을 때 무엇이 금지되는가를 그린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 400;
    const g = [];

    g.push(txt(40, 28, '도덕 속성은 자연적 속성에 수반한다', { cls: 'ink bold' }));

    const py = 48;
    const ph = 176;
    g.push(box(40, py, 356, ph, { stroke: CG, sw: 1.2 }));
    g.push(txt(58, py + 26, '경우 A', { cls: 'ink bold', size: 'sm' }));
    ['같은 행위', '같은 결과', '같은 동기', '같은 사정'].forEach((s, i) => {
        g.push(txt(58, py + 52 + i * 20, s, { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[58, py + 140], [376, py + 140]], { stroke: CG, sw: 1.2, dash: '4 3' }));
    g.push(txt(58, py + 162, '도덕 판정 — 허용된다', { cls: 'ink bold', size: 'sm' }));

    g.push(box(484, py, 356, ph, { stroke: CG, sw: 1.2 }));
    g.push(txt(502, py + 26, '경우 B', { cls: 'ink bold', size: 'sm' }));
    ['같은 행위', '같은 결과', '같은 동기', '같은 사정'].forEach((s, i) => {
        g.push(txt(502, py + 52 + i * 20, s, { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[502, py + 140], [820, py + 140]], { stroke: CG, sw: 1.2, dash: '4 3' }));
    g.push(txt(502, py + 162, '도덕 판정 — 금지된다', { cls: 'ink bold', size: 'sm' }));

    g.push(xmark(440, py + 88, 16, C2));
    g.push(txt(440, py + 128, '이런 짝은 없다', { cls: 'ink2', size: 'sm', anchor: 'middle' }));

    g.push(notes(40, 264, [
        '11장이 정의한 수반이다 — 아래에서 차이가 없으면 위에서도 차이가 있을 수 없다',
        '이 자리는 실재론 쪽과 반실재론 쪽이 대체로 함께 받아들인다. 그래서 논쟁의 심판이 못 된다',
        '오히려 양쪽이 각자 설명해야 하는 것이 된다 — 자연주의는 쉽게 설명하고, 비자연주의는 부담을 지고,',
        '표현주의는 우리 태도가 자연적 특징에 반응한다는 것으로 설명한다',
        '수반은 의존을 말할 뿐 정체를 말하지 않는다. 수반한다고 해서 같은 것이 되지는 않는다',
    ]));

    return {
        name: 'phi-e-supervenience-moral',
        svg: svg({
            width: W, height: H,
            title: '도덕 속성의 수반이 금지하는 짝',
            desc: '자연적 사실이 완전히 같은 두 경우에 도덕 판정만 다른 짝은 성립하지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 18장 — 규범윤리 세 이론
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 4. 중용
 *
 * 산술적 중간이 아니라는 것, 그리고 모든 것에 중용이 있지는 않다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 372;
    const g = [];

    g.push(txt(40, 28, '중용은 산술적 중간이 아니다', { cls: 'ink bold' }));

    const bar = (y, left, right, name, t) => {
        const x0 = 150;
        const x1 = 760;
        const cx = x0 + (x1 - x0) * t;
        const out = [];
        out.push(ln([[x0, y], [x1, y]], { stroke: CK, sw: 2 }));
        out.push(ln([[x0, y - 7], [x0, y + 7]], { stroke: CK, sw: 2 }));
        out.push(ln([[x1, y - 7], [x1, y + 7]], { stroke: CK, sw: 2 }));
        out.push(pdot(cx, y, C1, 7));
        out.push(txt(x0, y + 24, left, { cls: 'ink2', size: 'sm' }));
        out.push(txt(x1, y + 24, right, { cls: 'ink2', size: 'sm', anchor: 'end' }));
        out.push(txt(cx, y - 16, name, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
        out.push(ln([[x0 + (x1 - x0) / 2, y - 26], [x0 + (x1 - x0) / 2, y + 8]], { stroke: CG, sw: 1.2, dash: '4 3' }));
        return out.join('');
    };

    g.push(txt(40, 74, '두려움과', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 90, '자신감', { cls: 'ink2', size: 'sm' }));
    g.push(bar(80, '결핍 — 비겁', '과잉 — 무모', '중용 — 용기', 0.62));

    g.push(txt(40, 166, '몸의 즐거움', { cls: 'ink2', size: 'sm' }));
    g.push(bar(170, '결핍 — 무감각', '과잉 — 방종', '중용 — 절제', 0.38));

    g.push(txt(150, 214, '점선이 산술적 중간이다. 중용은 거기 있지 않다', { cls: 'ink2', size: 'sm' }));

    g.push(notes(40, 254, [
        '중용의 자리는 사람마다 상황마다 다르다. 그래서 표로 미리 적어 둘 수 없다',
        '어디가 중용인지 그 자리에서 짚어 내는 능력이 실천지다. 규칙 목록으로 대신하려 하면 덕 윤리가 아니게 된다',
        '이것이 강점이자 약점이다 — 상황을 살리는 대신, 무엇을 하라는 지침을 주지 못한다는 반론을 받는다',
        '그리고 모든 것에 중용이 있는 것도 아니다. 잔인함이나 시기에는 알맞은 양이 없다',
    ]));

    return {
        name: 'phi-e-mean',
        svg: svg({
            width: W, height: H,
            title: '중용은 두 극단의 산술적 중간이 아니다',
            desc: '두 축 위에서 중용의 자리가 각각 중간이 아닌 곳에 찍혀 있다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 행위의 도덕적 칸이 이론마다 다르게 그어진다
 *
 * 엄격한 행위 공리주의에는 ‘해도 되고 안 해도 되는’ 칸이 없다.
 * 19장의 요구 과잉 논쟁이 가운데 띠의 너비 다툼이라는 것을 미리 보인다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 396;
    const g = [];

    g.push(txt(40, 28, '어떤 행위가 어느 칸에 들어가는가 — 이론마다 선이 다르다', { cls: 'ink bold' }));

    const x0 = 232;
    const x1 = 840;
    const wtot = x1 - x0;

    const seg = (y, h, from, to, label, col, { dash } = {}) => {
        const a = x0 + wtot * from;
        const b = x0 + wtot * to;
        const out = [box(a, y, b - a, h, { fill: col, op: col === 'none' ? 1 : 0.16, stroke: col === 'none' ? CK : col, sw: 1.4, dash })];
        out.push(txt((a + b) / 2, y + h / 2 + 4, label, { cls: 'ink', size: 'sm', anchor: 'middle' }));
        return out.join('');
    };

    const rowLabel = (y, a, b) => txt(40, y, a, { cls: 'ink bold', size: 'sm' })
        + (b ? txt(40, y + 16, b, { cls: 'ink2', size: 'sm' }) : '');

    let y = 58;
    const h = 40;

    g.push(rowLabel(y + 18, '엄격한 행위 공리주의'));
    g.push(seg(y, h, 0, 0.62, '금지 — 최선이 아닌 것은 모두', C2));
    g.push(seg(y, h, 0.62, 1, '의무 — 최선인 것', C1));

    y += 74;
    g.push(rowLabel(y + 12, '문턱을 둔 결과주의', '충분히 좋으면 허용한다'));
    g.push(seg(y, h, 0, 0.3, '금지', C2));
    g.push(seg(y, h, 0.3, 0.62, '의무', C1));
    g.push(seg(y, h, 0.62, 1, '해도 되고 안 해도 된다', 'none'));

    y += 74;
    g.push(rowLabel(y + 12, '의무론', '제약과 선택지가 함께 있다'));
    g.push(seg(y, h, 0, 0.24, '제약 — 결과가 좋아도 금지', C2));
    g.push(seg(y, h, 0.24, 0.42, '의무', C1));
    g.push(seg(y, h, 0.42, 0.84, '선택지 — 해도 되고 안 해도 된다', 'none'));
    g.push(seg(y, h, 0.84, 1, '초과 의무', C3));

    y += 74;
    g.push(rowLabel(y + 12, '덕 윤리', '선을 규칙으로 긋지 않는다'));
    g.push(seg(y, h, 0, 1, '유덕한 사람이 그 상황에서 무엇을 할지로 답한다', 'none', { dash: '6 4' }));

    g.push(notes(40, 350, [
        '가장 눈에 띄는 것은 첫째 줄이다. 엄격한 행위 공리주의에는 ‘해도 되고 안 해도 되는’ 칸이 아예 없다',
        '19장의 요구 과잉 논쟁은 이 가운데 띠가 얼마나 넓어야 하는가에 대한 다툼이다',
    ]));

    return {
        name: 'phi-e-deontic-bands',
        svg: svg({
            width: W, height: H,
            title: '행위의 도덕적 칸을 이론마다 다르게 나눈 띠 그림',
            desc: '금지·의무·허용·초과 의무의 경계가 네 이론에서 각각 다른 자리에 그어진다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 셋이 서로에게 던지는 반론
 *
 * 대칭이어야 한다. 대각선에는 각 이론이 스스로 감당하는 부담을 적는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 470;
    const g = [];

    g.push(txt(40, 28, '셋이 서로에게 던지는 반론 — 세로가 던지는 쪽, 가로가 받는 쪽', { cls: 'ink bold' }));

    const rx = 40;
    const rw = 132;
    const cw = 240;
    const hy = 48;
    const hh = 32;
    const rh = 104;
    const cxs = [rx + rw, rx + rw + cw, rx + rw + 2 * cw];
    const names = ['결과주의', '의무론', '덕 윤리'];

    g.push(box(rx, hy, rw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    names.forEach((nm, i) => {
        g.push(box(cxs[i], hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
        g.push(txt(cxs[i] + cw / 2, hy + 21, nm, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
    });

    const rows = [
        ['결과주의', [
            ['자기 부담', ['좋음의 이론을 갈아 끼울수록', '내용이 옅어진다'], null],
            ['더 나쁜 결과를 왜 택하나', ['제약의 역설 — 살인 다섯을', '막는 살인 하나가 왜 금지인가'], C1],
            ['무엇을 하라는 말이 없다', ['성품을 말할 뿐 행위 지침을', '주지 않는다'], C1],
        ]],
        ['의무론', [
            ['사람을 총합의 그릇으로 쓴다', ['한 사람의 손해를 다른 사람의', '이득으로 갚을 수 있게 된다'], C2],
            ['자기 부담', ['준칙을 어떻게 적느냐로 답이', '바뀐다. 의무끼리 충돌한다'], null],
            ['덕을 고르려면 옳음이 먼저다', ['무엇이 덕인지 정하는 데', '이미 옳음의 기준이 쓰인다'], C2],
        ]],
        ['덕 윤리', [
            ['동기와 성품을 밖에 둔다', ['마지못해 도운 사람과 기꺼이', '도운 사람이 같아진다'], C3],
            ['도덕을 규칙표로 좁힌다', ['규칙이 닿지 않는 자리를', '판단할 능력을 설명 못 한다'], C3],
            ['자기 부담', ['덕 목록이 문화마다 다르다.', '안정된 성품이 있는지도 다툰다'], null],
        ]],
    ];

    rows.forEach((row, ri) => {
        const y = hy + hh + ri * rh;
        g.push(box(rx, y, rw, rh, { sw: 1.2 }));
        g.push(txt(rx + 10, y + rh / 2 - 2, row[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(rx + 10, y + rh / 2 + 16, '가 던진다', { cls: 'ink2', size: 'sm' }));
        row[1].forEach((c, ci) => {
            g.push(cell(cxs[ci], y, cw, rh, c[0], c[1], { accent: c[2], dash: c[2] ? null : '5 4' }));
        });
    });

    g.push(notes(40, hy + hh + 3 * rh + 28, [
        '표가 대칭이다. 어느 줄도 아직 지워지지 않았고, 대각선의 자기 부담도 아직 해결되지 않았다',
        '실제 판정은 셋이 크게 갈리지 않는 경우가 많다. 갈리는 것은 판정이 아니라 그 판정을 대는 이유다',
    ]));

    return {
        name: 'phi-e-objection-matrix',
        svg: svg({
            width: W, height: H,
            title: '결과주의 · 의무론 · 덕 윤리가 서로에게 던지는 반론의 격자',
            desc: '세로가 반론을 던지는 쪽, 가로가 받는 쪽이며 대각선에는 각 이론이 스스로 감당하는 부담을 적었다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 19장 — 사례
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 7. 트롤리 두 사례의 배치
 *
 * 셈이 같다는 것을 눈으로 보여야 판단 차이가 자료가 된다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 372;
    const g = [];

    g.push(txt(40, 28, '갈림길과 육교 — 결과의 셈은 같다', { cls: 'ink bold' }));

    /* 왼쪽 판 — 갈림길 */
    g.push(box(40, 46, 396, 218, { stroke: CG, sw: 1.2 }));
    g.push(txt(58, 72, '갈림길', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(58, 92, '스위치를 당기면 전차가 옆 선로로 간다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[62, 150], [416, 150]], { stroke: CK, sw: 2.4 }));
    g.push(ln([[196, 150], [280, 214], [416, 214]], { stroke: CK, sw: 2.4 }));
    g.push(box(64, 140, 26, 18, { fill: C1, op: 0.85, stroke: C1, sw: 1.4, rx: 3 }));
    g.push(txt(77, 132, '전차', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    for (let i = 0; i < 5; i += 1) g.push(pdot(330 + i * 18, 150, C2, 5));
    g.push(txt(378, 136, '다섯 사람', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(pdot(384, 214, C3, 5));
    g.push(txt(384, 236, '한 사람', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(pdot(196, 150, CK, 4));
    g.push(txt(196, 178, '스위치', { cls: 'ink bold', size: 'sm', anchor: 'middle' }));

    /* 오른쪽 판 — 육교 */
    g.push(box(464, 46, 376, 218, { stroke: CG, sw: 1.2 }));
    g.push(txt(482, 72, '육교', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(482, 92, '옆 사람을 밀어 떨어뜨리면 전차가 멈춘다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[486, 214], [822, 214]], { stroke: CK, sw: 2.4 }));
    g.push(box(488, 204, 26, 18, { fill: C1, op: 0.85, stroke: C1, sw: 1.4, rx: 3 }));
    g.push(txt(501, 196, '전차', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    for (let i = 0; i < 5; i += 1) g.push(pdot(736 + i * 18, 214, C2, 5));
    g.push(txt(784, 200, '다섯 사람', { cls: 'ink2', size: 'sm', anchor: 'middle' }));

    g.push(ln([[578, 152], [682, 152]], { stroke: CK, sw: 2 }));
    g.push(ln([[586, 152], [586, 190]], { stroke: CG, sw: 1.4, dash: '4 3' }));
    g.push(ln([[674, 152], [674, 190]], { stroke: CG, sw: 1.4, dash: '4 3' }));
    g.push(pdot(600, 143, CK, 5));
    g.push(txt(600, 128, '나', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(pdot(654, 142, C3, 6.5));
    g.push(txt(654, 128, '한 사람', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(px(654, 160, 654, 202, { cls: 's3', marker: 'ar3', width: 2.2 }));

    g.push(notes(40, 296, [
        '두 사례에서 셈은 같다. 한 사람이 죽고 다섯 사람이 산다',
        '그런데 같은 사람이 두 사례를 다르게 판단한다. 왼쪽은 대체로 허용된다고, 오른쪽은 대체로 안 된다고 답한다',
        '그 차이가 이 절의 자료다. 무엇이 두 사례를 갈라 주는지가 물음이 된다',
    ]));

    return {
        name: 'phi-e-trolley-two',
        svg: svg({
            width: W, height: H,
            title: '트롤리의 두 사례 — 갈림길과 육교',
            desc: '왼쪽은 스위치로 전차를 옆 선로로 돌리는 사례, 오른쪽은 육교에서 사람을 밀어 전차를 멈추는 사례다. 죽는 사람과 사는 사람의 수는 같다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 사례 × 원리 격자
 *
 * 어긋나는 칸에만 색을 준다. 어느 원리도 전부를 맞히지 못한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 396;
    const g = [];

    g.push(txt(40, 28, '세 원리가 예측하는 판정과 실제로 나오는 판정', { cls: 'ink bold' }));

    const rx = 40;
    const rw = 196;
    const cw = 166;
    const hy = 50;
    const hh = 54;
    const rh = 62;
    const heads = ['대다수의 판단', '이중효과', '수단으로 씀', '위협의 방향 전환'];

    g.push(box(rx, hy, rw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(rx + 10, hy + 22, '아래로 — 사례', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, hy + 40, '옆으로 — 무엇이 말하는가', { cls: 'ink bold', size: 'sm' }));
    heads.forEach((s, i) => {
        const x = rx + rw + i * cw;
        g.push(box(x, hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
        g.push(txt(x + cw / 2, hy + 32, s, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
    });

    const rows = [
        ['갈림길', '스위치를 당긴다', ['허용', '허용', '허용', '허용'], [false, false, false, false]],
        ['육교', '사람을 민다', ['안 된다', '안 된다', '안 된다', '안 된다'], [false, false, false, false]],
        ['고리 궤도', '옆 선로가 본선으로 돌아온다', ['대체로 허용', '안 된다', '안 된다', '허용'], [false, true, true, false]],
    ];

    rows.forEach((row, ri) => {
        const y = hy + hh + ri * rh;
        g.push(box(rx, y, rw, rh, { sw: 1.2 }));
        g.push(txt(rx + 10, y + 24, row[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(rx + 10, y + 44, row[1], { cls: 'ink2', size: 'sm' }));
        row[2].forEach((v, ci) => {
            const x = rx + rw + ci * cw;
            const off = row[3][ci];
            g.push(box(x, y, cw, rh, {
                fill: off ? C2 : 'none', op: off ? 0.18 : 1,
                stroke: off ? C2 : CK, sw: off ? 1.9 : 1.2,
            }));
            g.push(txt(x + cw / 2, y + 30, v, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
            if (off) g.push(txt(x + cw / 2, y + 50, '판단과 어긋난다', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
        });
    });

    g.push(notes(40, hy + hh + 3 * rh + 30, [
        '고리 궤도에서 세 원리 가운데 둘이 실제 판단과 어긋난다. 남은 하나도 다른 변형에서 걸린다',
        '표를 다 채우는 원리를 아직 아무도 내놓지 못했다. 그것이 이 사례들이 남기는 결과다',
        '결과주의는 세 사례를 모두 같게 본다. 그래서 판단의 차이를 원리로 설명하는 대신 편향으로 설명해야 한다',
    ]));

    return {
        name: 'phi-e-trolley-grid',
        svg: svg({
            width: W, height: H,
            title: '트롤리 사례와 세 원리의 예측을 맞춰 본 격자',
            desc: '갈림길과 육교에서는 세 원리가 실제 판단과 맞지만 고리 궤도에서 둘이 어긋난다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 9. 요구의 세기 축
 *
 * 어느 판본을 잡든 우리가 실제로 하는 자리보다 오른쪽이라는 것이 요점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 336;
    const g = [];

    g.push(txt(40, 28, '기근 구제가 요구하는 것 — 얼마나 강한가', { cls: 'ink bold' }));

    const x0 = 96;
    const x1 = 770;
    const y = 126;
    g.push(ln([[x0, y], [x1, y]], { stroke: CK, sw: 2 }));
    g.push(px(x1 - 2, y, x1 + 16, y, { cls: 's1', marker: 'ark', width: 2 }));
    g.push(txt(x0 - 8, y + 30, '아무 의무도 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1 + 18, y + 30, '가진 것을 다 내놓을 때까지', { cls: 'ink2', size: 'sm', anchor: 'end' }));

    const mark = (t, name, lines, col) => {
        const x = x0 + (x1 - x0) * t;
        const out = [ln([[x, y - 12], [x, y + 12]], { stroke: col, sw: 2.4 })];
        out.push(pdot(x, y, col, 6));
        out.push(txt(x, y - 22, name, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
        lines.forEach((s, i) => out.push(txt(x, y + 58 + i * 18, s, { cls: 'ink2', size: 'sm', anchor: 'middle' })));
        return out.join('');
    };

    g.push(mark(0.12, '우리가 실제로 하는 것', ['논증의 결론이', '어디에 있든', '이 왼쪽이다'], CK));
    g.push(mark(0.44, '약한 판본', ['도덕적으로 중요한', '무엇도 희생하지', '않는 선까지'], C3));
    g.push(mark(0.66, '문턱을 둔 응답', ['일정한 몫까지가', '의무이고 그 위는', '초과 의무다'], C1));
    g.push(mark(0.88, '강한 판본', ['비슷하게 중요한 것을', '희생하기 직전까지'], C2));

    g.push(notes(40, 258, [
        '요구가 크다는 것 자체는 반론이 아니다. 도덕이 편하리라는 보장은 어디에도 없다',
        '그래도 우리 판단은 어느 지점 너머에서 저항한다. 그 저항이 자료인지 편의인지가 다툼이고, 아직 결판나지 않았다',
        '어느 판본을 잡아도 우리가 실제로 하는 자리보다 오른쪽이다. 그것이 이 논증이 남기는 것이다',
    ]));

    return {
        name: 'phi-e-demandingness',
        svg: svg({
            width: W, height: H,
            title: '기근 구제 논증이 요구하는 세기를 한 축에 늘어놓은 그림',
            desc: '아무 의무도 없음에서 전부 내놓음까지의 축 위에 약한 판본, 문턱 응답, 강한 판본, 그리고 우리가 실제로 하는 자리를 찍었다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 10. 능력을 기준으로 선을 그으면 무슨 일이 일어나는가
 *
 * 가장자리 사례 논증의 형태를 축 하나로 보인다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 368;
    const g = [];

    g.push(txt(40, 28, '능력을 지위의 근거로 삼고 선을 그어 본다', { cls: 'ink bold' }));
    g.push(txt(40, 50, '가로축 — 지위의 근거로 흔히 드는 능력. 이성 · 언어 · 도덕적 행위 능력', { cls: 'ink2', size: 'sm' }));

    const x0 = 70;
    const x1 = 820;
    const y = 148;
    const at = t => x0 + (x1 - x0) * t;

    g.push(ln([[x0, y], [x1, y]], { stroke: CK, sw: 2 }));
    g.push(txt(x0, y + 24, '낮다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x1, y + 24, '높다', { cls: 'ink2', size: 'sm', anchor: 'end' }));

    const point = (t, name, up, col) => {
        const x = at(t);
        return pdot(x, y, col, 5.5)
            + txt(x, y - up, name, { cls: 'ink', size: 'sm', anchor: 'middle' });
    };

    g.push(point(0.10, '물고기', 16, C3));
    g.push(point(0.26, '개 · 돼지', 38, C3));
    g.push(point(0.42, '갓 태어난 아기', 16, C1));
    g.push(point(0.52, '유인원', 38, C3));
    g.push(point(0.62, '심한 인지 장애가 있는 사람', 16, C1));
    g.push(point(0.88, '보통의 성인', 16, C1));

    const thr = (t, col) => ln([[at(t), y - 10], [at(t), y + 40]], { stroke: col, sw: 2.2, dash: '6 4' });
    g.push(thr(0.34, C1));
    g.push(thr(0.72, C2));
    g.push(txt(at(0.34), y + 56, '선 B', { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
    g.push(txt(at(0.72), y + 56, '선 A', { cls: 'ink bold', size: 'sm', anchor: 'middle' }));

    g.push(txt(60, y + 86, '선 B — 모든 사람을 안에 두려면 여기쯤이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(60, y + 104, '그러면 많은 동물이 함께 안으로 들어온다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(460, y + 86, '선 A — 모든 동물을 밖에 두려면 여기쯤이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(460, y + 104, '그러면 왼쪽에 있는 사람들도 함께 밖으로 나간다', { cls: 'ink2', size: 'sm' }));

    g.push(notes(40, y + 142, [
        '이 형태가 가장자리 사례 논증이다. 능력을 근거로 삼으면 어느 높이에 그어도 한쪽이 어긋난다',
        '응답들 — 종의 구성원임 자체가 근거라는 것, 관계와 소속이 의무를 낳는다는 것, 정상적인 종의 능력을 본다는 것',
        '이 틀 자체에 대한 반론도 있다. 어떤 사람들을 ‘가장자리’ 로 놓고 논증의 재료로 쓰는 것이 옳으냐는 것이다',
        '쾌고 감수 능력을 근거로 삼으면 축이 아예 달라진다. 그때는 선이 사람과 동물 사이에 그어지지 않는다',
    ]));

    return {
        name: 'phi-e-marginal-cases',
        svg: svg({
            width: W, height: H,
            title: '능력을 도덕적 지위의 근거로 삼고 선을 그어 본 축',
            desc: '이성과 언어와 도덕적 행위 능력의 축 위에 사람과 동물을 놓고 두 개의 후보 경계선을 그었다. 어느 선도 사람과 동물을 깔끔히 가르지 못한다',
            body: g.join(''),
        }),
    };
})());

export default figures;
