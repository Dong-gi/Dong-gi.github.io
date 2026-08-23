/**
 * 철학 12장(자유의지와 결정론)·13장(나는 왜 같은 사람인가)의 그림.
 *
 * 이름은 모두 `phi-f-` 로 시작한다(이 두 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 여기 있는 것은 표·격자·시간선처럼 좌표가 필요한 그림뿐이다.
 * 상자와 화살표로 그릴 것(결과 논증의 구조, 프랭크퍼트 사례의 두 계열,
 * 기준 지도, 실천적 함의)은 d2/philosophy/phi-f-*.d2 에 있다.
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

/** 두 점을 잇는 위로 볼록한 호. 기억 관계를 그리는 데 쓴다. */
function bow(x1, x2, y, lift, { stroke = CK, sw = 1.5, dash } = {}) {
    const mx = (x1 + x2) / 2;
    return `<path d="M${r2(x1)} ${r2(y)} Q${r2(mx)} ${r2(y - lift)} ${r2(x2)} ${r2(y)}"`
        + ` fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 6) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 가위표. 성립하지 않는 관계 위에 얹는다. */
function xmark(x, y, s = 7, col = C2) {
    return `<path d="M${r2(x - s)} ${r2(y - s)} L${r2(x + s)} ${r2(y + s)} M${r2(x + s)} ${r2(y - s)} L${r2(x - s)} ${r2(y + s)}"`
        + ` stroke="${col}" stroke-width="2.4" stroke-linecap="round" fill="none"/>`;
}

/** 머리글 한 줄과 내용 여러 줄을 담는 격자 칸. */
function cell(x, y, w, h, head, lines, { accent = null, headCls = 'ink bold' } = {}) {
    const g = [box(x, y, w, h, {
        fill: accent ?? 'none', op: accent ? 0.14 : 1,
        stroke: accent ?? CK, sw: accent ? 1.9 : 1.2,
    })];
    g.push(txt(x + 12, y + 24, head, { cls: headCls, size: 'sm' }));
    lines.forEach((s, i) => g.push(txt(x + 12, y + 46 + i * 18, s, { cls: 'ink2', size: 'sm' })));
    return g.join('');
}

/* ================================================================== *
 * 12장
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 결정론이 참인 세계와 거짓인 세계
 *
 * 이 장에서 가장 먼저 무너지는 자리가 ‘갈래가 여럿이면 자유가 있다’ 는
 * 생각이다. 두 그림 어디에도 자유가 그려져 있지 않다는 것을 눈으로 본다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 800;
    const H = 348;
    const g = [];

    g.push(txt(40, 28, '결정론이 참인 세계와 거짓인 세계 — 미래의 갈래', { cls: 'ink bold' }));

    // 왼쪽 판
    g.push(box(40, 46, 350, 176, { stroke: CG, sw: 1.2 }));
    g.push(txt(58, 72, '결정론이 참인 세계', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(58, 92, '과거의 상태와 자연법칙이 미래를 하나로 못 박는다', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[196, 112], [196, 196]], { stroke: CG, sw: 1.2, dash: '4 3' }));
    g.push(ln([[62, 150], [196, 150]], { stroke: CK, sw: 2.6 }));
    g.push(px(196, 150, 372, 150, { cls: 's1', marker: 'ar1', width: 2.6 }));
    g.push(pdot(196, 150, C2, 5.5));
    g.push(txt(62, 212, '과거', { cls: 'ink2', size: 'sm' }));
    g.push(txt(196, 212, '지금', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(txt(372, 212, '미래', { cls: 'ink2', size: 'sm', anchor: 'end' }));

    // 오른쪽 판
    g.push(box(410, 46, 350, 176, { stroke: CG, sw: 1.2 }));
    g.push(txt(428, 72, '결정론이 거짓인 세계', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(428, 92, '같은 과거와 같은 법칙에서 여러 미래가 열려 있다', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[566, 112], [566, 196]], { stroke: CG, sw: 1.2, dash: '4 3' }));
    g.push(ln([[432, 150], [566, 150]], { stroke: CK, sw: 2.6 }));
    g.push(px(566, 150, 742, 118, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(px(566, 150, 742, 150, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(px(566, 150, 742, 182, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(pdot(566, 150, C2, 5.5));
    g.push(txt(432, 212, '과거', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 212, '지금', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(txt(742, 212, '미래', { cls: 'ink2', size: 'sm', anchor: 'end' }));

    g.push(txt(40, 258, '두 그림 어디에도 자유는 그려져 있지 않다. 갈래가 여럿이라는 것과 그 갈래를 내가 고른다는 것은 다른 말이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 280, '왼쪽에서 자유가 사라지는지, 오른쪽에서 자유가 생기는지 — 그림만 보아서는 정해지지 않는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 302, '어느 쪽이 우리 세계인가는 첫째 물음이고, 자유의지가 그것과 양립하는가는 둘째 물음이다. 둘은 서로 다른 물음이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 330, '결정론은 예측 가능성이 아니다. 아무도 계산할 수 없어도 미래가 하나로 정해져 있으면 결정론은 참이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-branching-futures',
        svg: svg({
            width: W, height: H,
            title: '결정론이 참인 세계와 거짓인 세계',
            desc: '왼쪽은 지금에서 미래로 가는 길이 하나뿐인 세계, 오른쪽은 셋으로 갈라지는 세계다. 어느 쪽에도 자유는 그려져 있지 않다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 두 물음의 답 조합이 만드는 네 자리
 *
 * ‘결정론이 참인가’ 를 축으로 삼지 않는 것이 이 표의 요점이다.
 * 양립가능론은 결정론이 참이라고 주장하는 입장이 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 414;
    const g = [];

    g.push(txt(40, 28, '두 물음의 답을 조합하면 네 자리가 나온다', { cls: 'ink bold' }));

    const rx = 40;
    const rw = 168;
    const c1 = 208;
    const c2 = 524;
    const cw = 316;
    const hy = 48;
    const hh = 42;

    g.push(box(rx, hy, rw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(rx + 10, hy + 18, '아래로 — 양립하는가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, hy + 34, '옆으로 — 자유의지가', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c1, hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(c1 + 12, hy + 26, '우리에게 자유의지가 있다', { cls: 'ink bold', size: 'sm' }));
    g.push(box(c2, hy, cw, hh, { fill: CG, op: 0.4, sw: 1.2 }));
    g.push(txt(c2 + 12, hy + 26, '우리에게 자유의지가 없다', { cls: 'ink bold', size: 'sm' }));

    const r1 = hy + hh;
    const r2h = 116;
    g.push(box(rx, r1, rw, r2h, { sw: 1.2 }));
    g.push(txt(rx + 10, r1 + 26, '양립한다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, r1 + 48, '결정론이 참이어도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r1 + 66, '자유는 있을 수 있다', { cls: 'ink2', size: 'sm' }));

    g.push(cell(c1, r1, cw, r2h, '양립가능론 — 다수설', [
        '결정론이 참이어도 자유는 있을 수 있고,',
        '실제로 우리에게 있다고 본다',
        '자유를 강제·강박의 부재로 다시 잡는다',
    ], { accent: C1 }));

    g.push(cell(c2, r1, cw, r2h, '양립가능론적 회의론 — 드물다', [
        '양립은 하지만 자유의 조건이 실제로는',
        '충족되지 않는다고 본다',
        '자리는 비어 있지 않으나 드물게 채워진다',
    ]));

    const r2y = r1 + r2h;
    g.push(box(rx, r2y, rw, r2h, { sw: 1.2 }));
    g.push(txt(rx + 10, r2y + 26, '양립하지 않는다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(rx + 10, r2y + 48, '결정론이 참이면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx + 10, r2y + 66, '자유는 없다', { cls: 'ink2', size: 'sm' }));

    g.push(cell(c1, r2y, cw, r2h, '자유지상론', [
        '자유의지가 있으므로 결정론은 거짓이다',
        '비결정성이 어디에 어떻게 놓여야',
        '자유가 되는지를 말해야 한다',
    ], { accent: C2 }));

    g.push(cell(c2, r2y, cw, r2h, '강한 결정론 · 강한 비양립가능론', [
        '자유의지는 없다. 결정론이 참이어서든,',
        '거짓이더라도 비결정성이 자유를',
        '만들어 주지 않아서든',
    ], { accent: C3 }));

    const ny = r2y + r2h + 26;
    g.push(txt(40, ny, '‘결정론은 실제로 참인가’ 는 이 표의 축이 아니다. 그것이 셋째 물음이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 22, '양립가능론은 결정론이 참이라고 주장하는 입장이 아니다. 참이든 거짓이든 자유는 무사하다고 말할 뿐이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 44, '셋째 물음에 걸려 있는 것은 자유지상론뿐이다. 자유가 있다는 주장에서 결정론이 거짓이라는 결론이 곧바로 따라 나온다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-position-grid',
        svg: svg({
            width: W, height: H,
            title: '자유의지 논쟁의 네 자리',
            desc: '양립하는가와 자유의지가 있는가, 두 물음의 답 조합이 양립가능론·양립가능론적 회의론·자유지상론·강한 결정론을 만든다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 스트로슨의 두 관점
 *
 * 책임을 ‘형이상학이 먼저 허락해야 생기는 것’ 으로 보지 않는 길이
 * 어떤 모양인지 한눈에 보이게 한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 372;
    const g = [];

    g.push(txt(40, 28, '사람을 대하는 두 관점 — 스트로슨', { cls: 'ink bold' }));

    const y0 = 48;
    const cw = 370;
    const ch = 214;
    const x1 = 40;
    const x2 = 430;

    g.push(box(x1, y0, cw, ch, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
    g.push(txt(x1 + 14, y0 + 26, '참여자 관점', { cls: 'ink bold', size: 'sm' }));
    [
        '상대를 함께 사는 사람으로 대한다',
        '',
        '여기서 나오는 것 — 반응적 태도',
        '분개, 원한, 감사, 용서, 죄책감, 자랑',
        '',
        '이 태도들은 상대의 행위에 담긴',
        '선의·악의·무관심에 대한 반응이다',
        '',
        '‘책임이 있다’ 는 말은 이 태도의',
        '대상이 된다는 뜻이다',
    ].forEach((s, i) => { if (s) g.push(txt(x1 + 14, y0 + 52 + i * 17, s, { cls: 'ink2', size: 'sm' })); });

    g.push(box(x2, y0, cw, ch, { fill: C3, op: 0.1, stroke: C3, sw: 1.8 }));
    g.push(txt(x2 + 14, y0 + 26, '객관적 관점', { cls: 'ink bold', size: 'sm' }));
    [
        '상대를 다루고 관리할 대상으로 본다',
        '',
        '여기서 나오는 것 — 치료, 훈련, 격리',
        '분개는 자리를 잃는다',
        '',
        '우리는 이미 이 관점으로 넘어간다',
        '어린아이, 중한 정신질환, 강요된 행위',
        '',
        '지쳤을 때 잠시 넘어가기도 한다',
        '그러나 오래 머무르기는 어렵다',
    ].forEach((s, i) => { if (s) g.push(txt(x2 + 14, y0 + 52 + i * 17, s, { cls: 'ink2', size: 'sm' })); });

    g.push(px(x1 + cw, y0 + ch / 2, x2, y0 + ch / 2, { cls: 's2', marker: 'ar2', width: 2.2 }));

    const ny = y0 + ch + 30;
    g.push(txt(40, ny, '스트로슨의 주장 — 우리가 참여자 관점에서 객관적 관점으로 넘어가는 것은 사례별 사정 때문이지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 20, '결정론이 참이라는 일반적 이유 때문이 아니다. 결정론은 어느 한 사례를 면제해 주는 종류의 사실이 아니다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 46, '반론 — 그것은 우리가 무엇을 하는지에 대한 서술일 뿐, 우리가 그렇게 해도 되는지에 대한 정당화가 아니다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-reactive-stance',
        svg: svg({
            width: W, height: H,
            title: '참여자 관점과 객관적 관점',
            desc: '반응적 태도가 나오는 참여자 관점과, 사람을 다룰 대상으로 보는 객관적 관점. 둘 사이를 오가는 이유가 무엇인지가 요점이다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 13장
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 4. 한 사람이 시간을 지나는 그림 — 10장의 물음을 사람에 대해 다시
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 352;
    const g = [];

    g.push(txt(40, 28, '성질은 전부 바뀌었는데 우리는 하나로 센다', { cls: 'ink bold' }));

    const ax = 70;
    const bx = 760;
    const ay = 176;
    g.push(ln([[ax, ay], [bx - 14, ay]], { stroke: CG, sw: 1.6 }));
    g.push(px(bx - 20, ay, bx, ay, { cls: 's1', marker: 'ar1', width: 1.6 }));
    g.push(txt(bx, ay + 26, '시간', { cls: 'ink2', size: 'sm', anchor: 'end' }));

    const stops = [
        { x: 150, t: '다섯 살', a: ['몸의 물질이 지금과 거의', '겹치지 않는다'] },
        { x: 400, t: '스무 살', a: ['믿음도 성격도', '그때와 다르다'] },
        { x: 650, t: '예순 살', a: ['다섯 살 때 일을', '거의 기억하지 못한다'] },
    ];
    for (const s of stops) {
        g.push(pdot(s.x, ay, C2, 7));
        g.push(txt(s.x, ay - 18, s.t, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
        s.a.forEach((line, i) => g.push(txt(s.x, ay + 30 + i * 17, line, { cls: 'ink2', size: 'sm', anchor: 'middle' })));
    }

    // 위쪽 묶음 괄호
    g.push(ln([[150, 118], [150, 104], [650, 104], [650, 118]], { stroke: C1, sw: 1.8 }));
    g.push(txt(400, 94, '그런데도 한 사람이다 — 수적으로 하나', { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
    g.push(txt(400, 74, '무엇이 이 셋을 하나로 묶는가', { cls: 'ink bold', anchor: 'middle' }));

    g.push(txt(40, 264, '10장이 배에 대해 물은 것을 여기서는 사람에 대해 묻는다. 달라지는 것이 둘 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 286, '첫째, 사람은 안에서 자기를 바라본다. 어느 쪽이 나인지가 내게는 남의 일이 아니다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 308, '둘째, 답이 실천을 움직인다. 책임을 누구에게 묻고, 누구의 앞날을 미리 걱정할지가 여기에 걸려 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 336, '질적으로 같다는 말과 수적으로 하나라는 말을 섞지 않는 것이 이 장의 첫 단추다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-person-timeline',
        svg: svg({
            width: W, height: H,
            title: '시간을 지나는 한 사람',
            desc: '다섯 살·스무 살·예순 살에서 몸도 믿음도 기억도 달라지는데 우리는 그것을 한 사람으로 센다. 무엇이 하나로 묶는지가 물음이다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 리드의 용감한 장교 — 기억 관계는 이행적이지 않다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 480;
    const g = [];

    g.push(txt(40, 28, '용감한 장교 — 기억 관계는 이행적이지 않다', { cls: 'ink bold' }));

    const ay = 200;
    const P = [
        { x: 150, t: '소년', a: '과수원에서 매를 맞는다' },
        { x: 410, t: '장교', a: '전투에서 깃발을 빼앗는다' },
        { x: 670, t: '장군', a: '군대를 지휘한다' },
    ];
    g.push(ln([[90, ay], [740, ay]], { stroke: CG, sw: 1.6 }));
    for (const p of P) {
        g.push(pdot(p.x, ay, C2, 7));
        g.push(txt(p.x, ay + 26, p.t, { cls: 'ink bold', size: 'sm', anchor: 'middle' }));
        g.push(txt(p.x, ay + 44, p.a, { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    }

    // 직접 기억 둘 — 마루가 y = 165 근처에 온다
    g.push(bow(410, 150, ay - 12, 46, { stroke: C1, sw: 2 }));
    g.push(txt(280, 152, '매 맞은 일을 기억한다', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(bow(670, 410, ay - 12, 46, { stroke: C1, sw: 2 }));
    g.push(txt(540, 152, '깃발 빼앗은 일을 기억한다', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    // 성립하지 않는 관계 — 파란 호보다 한 층 위로 띄운다. 마루가 y = 109
    g.push(ln([[150, 140], [150, 178]], { stroke: C2, sw: 1.4, dash: '4 3' }));
    g.push(ln([[670, 140], [670, 178]], { stroke: C2, sw: 1.4, dash: '4 3' }));
    g.push(bow(670, 150, 140, 62, { stroke: C2, sw: 2, dash: '6 4' }));
    g.push(xmark(410, 109, 8, C2));
    g.push(txt(410, 86, '매 맞은 일은 기억하지 못한다', { cls: 'ink2', size: 'sm', anchor: 'middle' }));

    g.push(txt(40, 296, '직접 기억을 기준으로 삼으면 — 장교는 소년과 같은 사람이고, 장군은 장교와 같은 사람인데,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 316, '장군은 소년과 같은 사람이 아니다. 동일성은 이런 식으로 끊길 수 없다', { cls: 'ink2', size: 'sm' }));

    // 사슬로 고친 그림
    g.push(txt(40, 356, '고침 — 직접 기억 대신 겹치는 사슬을 쓴다', { cls: 'ink bold', size: 'sm' }));
    const cy = 400;
    g.push(ln([[150, cy], [670, cy]], { stroke: CG, sw: 1.4 }));
    g.push(pdot(150, cy, CK, 5));
    g.push(pdot(410, cy, CK, 5));
    g.push(pdot(670, cy, CK, 5));
    g.push(ln([[150, cy - 13], [410, cy - 13]], { stroke: C3, sw: 3 }));
    g.push(ln([[410, cy + 13], [670, cy + 13]], { stroke: C3, sw: 3 }));
    g.push(txt(280, cy - 22, '소년 — 장교', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(txt(540, cy + 34, '장교 — 장군', { cls: 'ink2', size: 'sm', anchor: 'middle' }));
    g.push(txt(700, cy + 5, '두 마디가 장교에서 겹친다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 466, '사슬로 잇는 순간 기준의 이름이 바뀐다. 기억 기준이 아니라 심리 연속성 기준이 된다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-memory-chain',
        svg: svg({
            width: W, height: H,
            title: '용감한 장교와 기억의 사슬',
            desc: '장교는 소년을 기억하고 장군은 장교를 기억하지만 장군은 소년을 기억하지 못한다. 겹치는 사슬로 고치면 이어진다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 분열 — 같은 관계가 두 번 성립한다
 *
 * 이 그림의 요점은 왼쪽과 오른쪽에서 ‘나와 그 사람 사이의 관계’ 가
 * 조금도 달라지지 않는다는 것이다. 달라진 것은 저쪽에서 일어난 일뿐이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 380;
    const g = [];

    g.push(txt(40, 28, '한쪽만 옮길 때와 양쪽을 옮길 때', { cls: 'ink bold' }));

    // 왼쪽 판 — 한쪽만
    g.push(box(40, 46, 360, 210, { stroke: CG, sw: 1.2 }));
    g.push(txt(58, 72, '한쪽만 옮긴다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(70, 148, '수술 전의 나', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[70, 160], [200, 160]], { stroke: CK, sw: 2.8 }));
    g.push(pdot(200, 160, C2, 6));
    g.push(px(200, 160, 350, 130, { cls: 's1', marker: 'ar1', width: 2.6 }));
    g.push(txt(356, 128, 'A', { cls: 'ink bold', size: 'sm', anchor: 'end' }));
    g.push(ln([[200, 160], [330, 200]], { stroke: CG, sw: 1.6, dash: '5 4' }));
    g.push(txt(336, 208, '버려진 반구', { cls: 'ink2', size: 'sm', anchor: 'end' }));
    g.push(txt(58, 236, 'A 가 나다 — 여기까지는 다툴 것이 적다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 판 — 양쪽
    g.push(box(420, 46, 360, 210, { stroke: CG, sw: 1.2 }));
    g.push(txt(438, 72, '양쪽을 다 옮긴다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(450, 148, '수술 전의 나', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[450, 160], [580, 160]], { stroke: CK, sw: 2.8 }));
    g.push(pdot(580, 160, C2, 6));
    g.push(px(580, 160, 730, 122, { cls: 's1', marker: 'ar1', width: 2.6 }));
    g.push(px(580, 160, 730, 198, { cls: 's3', marker: 'ar3', width: 2.6 }));
    g.push(txt(736, 120, 'A', { cls: 'ink bold', size: 'sm', anchor: 'end' }));
    g.push(txt(736, 204, 'B', { cls: 'ink bold', size: 'sm', anchor: 'end' }));
    g.push(txt(438, 236, '심리 연속성이 A 로도 B 로도 이어진다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(40, 292, '나와 A 사이의 관계는 두 그림에서 조금도 다르지 않다. 달라진 것은 B 쪽에서 일어난 일뿐이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 314, '그런데 동일성은 하나에 하나만 성립한다. A 도 나이고 B 도 나이면 A 와 B 가 서로 같아야 하는데,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 336, 'A 와 B 는 서로 다른 방에서 따로 살아간다. 그러므로 심리 연속성 기준이 이대로는 동일성의 기준일 수 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 366, '이것은 기준이 답을 못 맞힌 것이 아니라, 기준이 동일성이 아닌 다른 것을 재고 있었다는 신호다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-fission',
        svg: svg({
            width: W, height: H,
            title: '반구 분할 이식 — 한쪽만 옮길 때와 양쪽을 옮길 때',
            desc: '한쪽만 옮기면 그 사람이 나다. 양쪽을 옮기면 같은 관계가 두 번 성립하는데 동일성은 하나에 하나만 성립한다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 분열 사례의 네 답과 그 대가
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 368;
    const g = [];

    g.push(txt(40, 28, '분열 사례에 줄 수 있는 답과 그 대가', { cls: 'ink bold' }));

    const cols = [[40, 214], [254, 546]];
    const heads = ['답', '무엇을 치러야 하는가'];
    const rows = [
        ['A 만 나다', 'A 와 B 는 대칭이다. 왜 A 인지 말할 방법이 없다'],
        ['B 만 나다', '같은 이유로 말할 방법이 없다'],
        ['둘 다 나다', '동일성은 하나에 하나만 성립한다는 성질을 버려야 한다'],
        ['둘 다 내가 아니다', '한쪽만 옮겼으면 살아남았을 텐데, 성공을 하나 더 보태자 죽었다는 말이 된다'],
        ['갈라지지 않을 때만 나다', '내 생존이 저쪽 수술실에서 무슨 일이 일어나는가에 달리게 된다'],
    ];

    const hy = 48;
    const hh = 32;
    cols.forEach(([x, w], i) => {
        g.push(box(x, hy, w, hh, { fill: CG, op: 0.4, sw: 1.2 }));
        g.push(txt(x + 10, hy + 21, heads[i], { cls: 'ink bold', size: 'sm' }));
    });

    const ry = hy + hh;
    const rh = 38;
    rows.forEach((row, i) => {
        const y = ry + i * rh;
        const mark = i === 4;
        cols.forEach(([x, w], j) => {
            g.push(box(x, y, w, rh, {
                fill: mark ? C1 : 'none', op: mark ? 0.12 : 1,
                stroke: mark ? C1 : CK, sw: mark ? 1.7 : 1.2,
            }));
            g.push(txt(x + 10, y + rh / 2 + 5, row[j], { cls: mark ? 'ink bold' : 'ink2', size: 'sm' }));
        });
    });

    const ny = ry + rows.length * rh + 30;
    g.push(txt(40, ny, '마지막 줄이 비분지 조항이다. 형식으로는 잘 듣지만 대가가 이상하다 — 내가 살아남는지가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 20, '나와 그 사람 사이의 관계만으로 정해지지 않고, 나와 아무 관계 없는 곳의 사실에 좌우된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 46, '파핏은 여기서 다른 길로 간다. 다섯 답 중 하나를 고르는 대신, 고를 것이 없다고 말한다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-f-fission-options',
        svg: svg({
            width: W, height: H,
            title: '분열 사례의 답들과 그 대가',
            desc: '다섯 답 각각이 무엇을 포기해야 하는지. 비분지 조항은 형식으로는 듣지만 생존을 외부 사실에 맡기게 된다',
            body: g.join(''),
        }),
    };
})());

export default figures;
