/**
 * 철학 20장(정의와 국가)의 그림.
 *
 * 이름은 모두 `phi-t-` 로 시작한다(이 장에 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 여기 있는 것은 격자·막대·곡선처럼 좌표가 필요한 그림뿐이다.
 * 상자와 화살표로 그릴 것(사회계약의 계보, 동의의 형태, 자격 이론의 구조,
 * 두 자유, 평등 논쟁 지도)은 d2/philosophy/phi-t-*.d2 에 있다.
 */
import { svg, frame, txt, px, PALETTE } from './lib.mjs';

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

function ln(pts, { stroke = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 머리글 한 줄과 내용 여러 줄을 담는 칸. */
function cell(x, y, w, h, head, lines, { accent = null } = {}) {
    const g = [box(x, y, w, h, {
        fill: accent ?? 'none', op: accent ? 0.12 : 1,
        stroke: accent ?? CK, sw: accent ? 1.9 : 1.2,
    })];
    g.push(txt(x + 12, y + 24, head, { cls: 'ink bold', size: 'sm' }));
    lines.forEach((s, i) => g.push(txt(x + 12, y + 46 + i * 18, s, { cls: 'ink2', size: 'sm' })));
    return g.join('');
}

/* ================================================================== *
 * 1. 무지의 베일 — 무엇을 가리고 무엇을 남기는가
 *
 * 베일은 아무것이나 가리는 장치가 아니다. 가린 것과 남긴 것을 나란히
 * 놓으면 그 기준이 보인다. 위험 성향까지 가린다는 것이 하사니 논쟁의
 * 열쇠라서 그 줄만 강조해 둔다.
 * ================================================================== */
add((() => {
    const W = 840;
    const H = 452;
    const g = [];

    g.push(txt(40, 28, '무지의 베일 — 가리는 것과 남기는 것', { cls: 'ink bold' }));
    g.push(txt(40, 50, '가른 기준: 원칙을 자기에게 유리하게 기울이는 데 쓸 수 있는 것은 가리고, 원칙을 고르려면 있어야 하는 일반 지식은 남긴다', { cls: 'ink2', size: 'sm' }));

    const cx1 = 40;
    const cx2 = 440;
    const cw = 360;
    const cy = 68;
    const ch = 250;

    g.push(box(cx1, cy, cw, ch, { stroke: C2, sw: 1.9, fill: C2, op: 0.1 }));
    g.push(txt(cx1 + 14, cy + 26, '가린다 — 당사자가 알 수 없다', { cls: 'ink bold', size: 'sm' }));
    const hide = [
        '자기 계급과 사회적 지위',
        '타고난 재능·능력·체력·지능',
        '자기가 무엇을 좋은 삶으로 여기는지',
        '자기 사회의 경제·정치 사정',
        '자기가 어느 세대에 속하는지',
        '위험을 얼마나 싫어하는 사람인지',
    ];
    hide.forEach((s, i) => {
        g.push(txt(cx1 + 20, cy + 56 + i * 30, '·', { cls: 'ink2', size: 'sm' }));
        g.push(txt(cx1 + 32, cy + 56 + i * 30, s, { cls: i === 5 ? 'ink bold' : 'ink2', size: 'sm' }));
    });

    g.push(box(cx2, cy, cw, ch, { stroke: C1, sw: 1.9, fill: C1, op: 0.1 }));
    g.push(txt(cx2 + 14, cy + 26, '남긴다 — 당사자가 알고 있다', { cls: 'ink bold', size: 'sm' }));
    const keep = [
        '정치와 경제에 대한 일반 이론',
        '사회 조직이 어떻게 굴러가는지',
        '인간 심리의 일반 법칙',
        '자원이 넘치지도 절망적으로 모자라지도',
        '않다는 것 — 정의의 여건',
        '자기가 기본 가치를 더 많이 원한다는 것',
    ];
    keep.forEach((s, i) => {
        if (i !== 4) g.push(txt(cx2 + 20, cy + 56 + i * 30, '·', { cls: 'ink2', size: 'sm' }));
        g.push(txt(cx2 + 32, cy + 56 + i * 30, s, { cls: 'ink2', size: 'sm' }));
    });

    const ny = cy + ch + 30;
    g.push(txt(40, ny, '굵게 적은 줄이 뒤에 다툼거리가 된다. 위험 성향을 가려 버리면 당사자는 확률에 기대어 계산할 발판이 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 22, '롤스는 그래서 최소극대화가 합리적이라고 본다. 하사니는 모를 때는 모든 자리를 같은 확률로 놓고 평균을 재는 것이 합리적이라고 본다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 44, '베일은 정보를 없애는 장치가 아니라 협상력을 없애는 장치다. 남긴 목록이 모두 ‘누구에게나 같은’ 지식이라는 점을 확인하라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 72, '이것은 실제로 있었던 회의가 아니다. 원칙을 고를 때 어떤 정보를 근거로 삼는 것이 부당한지를 걸러 내는 사고실험이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-t-veil',
        svg: svg({
            width: W, height: H,
            title: '무지의 베일이 가리는 것과 남기는 것',
            desc: '왼쪽 칸은 원초적 입장의 당사자가 알 수 없는 것, 오른쪽 칸은 알고 있는 것이다. 위험 성향까지 가린다는 점이 최소극대화 논쟁의 열쇠다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 2. 최소극대화와 평균이 서로 다른 안을 고른다
 *
 * 숫자는 설명을 위해 지어낸 것이다. 요점은 두 결정 규칙이 실제로
 * 갈린다는 것, 그리고 어느 쪽이 합리적인지가 그림만으로는 정해지지
 * 않는다는 것이다.
 * ================================================================== */
add((() => {
    const W = 840;
    const H = 388;
    const g = [];

    g.push(txt(40, 28, '최소극대화는 C 를 고르고 평균 최대는 B 를 고른다', { cls: 'ink bold' }));
    g.push(txt(40, 50, '다섯 사회적 자리에 돌아가는 기본 가치의 양(설명을 위해 지어낸 수. 단위 없는 지수로 읽는다)', { cls: 'ink2', size: 'sm' }));

    const x0 = 40;
    const colW = 96;
    const rowH = 42;
    const y0 = 70;
    const heads = [['분배안', ''], ['가장 낮은', '자리'], ['둘째', ''], ['셋째', ''], ['넷째', ''], ['가장 높은', '자리'], ['평균', '']];
    const rows = [
        ['A', '30', '30', '30', '30', '30', '30'],
        ['B', '20', '40', '60', '90', '140', '70'],
        ['C', '35', '45', '55', '70', '95', '60'],
    ];

    heads.forEach((h, i) => {
        g.push(box(x0 + i * colW, y0, colW, rowH, { fill: CG, op: 0.4, sw: 1.1 }));
        const cxm = x0 + i * colW + colW / 2;
        if (h[1]) {
            g.push(txt(cxm, y0 + 18, h[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            g.push(txt(cxm, y0 + 34, h[1], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        } else {
            g.push(txt(cxm, y0 + 26, h[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    });

    rows.forEach((r, ri) => {
        const y = y0 + rowH + ri * rowH;
        r.forEach((v, ci) => {
            const hi = (ri === 2 && ci === 1) || (ri === 1 && ci === 6);
            const acc = ri === 2 && ci === 1 ? C1 : (ri === 1 && ci === 6 ? C2 : null);
            g.push(box(x0 + ci * colW, y, colW, rowH, {
                sw: hi ? 1.9 : 1.1,
                stroke: acc ?? CK,
                fill: acc ?? 'none',
                op: acc ? 0.16 : 1,
            }));
            g.push(txt(x0 + ci * colW + colW / 2, y + 26, v, {
                anchor: 'middle', cls: hi || ci === 0 ? 'ink bold' : 'ink2', size: 'sm',
            }));
        });
    });

    const ly = y0 + rowH * 4 + 34;
    g.push(box(40, ly - 20, 14, 3, { fill: C1, stroke: C1, sw: 0, rx: 1 }));
    g.push(txt(62, ly - 15, '최소극대화 — 각 안의 가장 낮은 자리만 보고, 그중 가장 높은 것을 고른다. 20 · 30 · 35 가운데 35 이므로 C', { cls: 'ink2', size: 'sm' }));
    g.push(box(40, ly + 4, 14, 3, { fill: C2, stroke: C2, sw: 0, rx: 1 }));
    g.push(txt(62, ly + 9, '평균 최대 — 어느 자리에 놓일지 모르니 모든 자리를 같은 확률로 보고 기댓값을 잰다. 30 · 70 · 60 가운데 70 이므로 B', { cls: 'ink2', size: 'sm' }));

    g.push(txt(40, ly + 42, '롤스: 확률을 댈 근거가 없고, 가장 낮은 자리에서의 20 과 35 의 차이는 받아들일 만한 삶과 아닌 삶의 차이일 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ly + 64, '하사니: 그렇다면 어떤 비행기도 타서는 안 된다. 최악만 보는 규칙은 아주 작은 위험 때문에 아주 큰 이득을 늘 버리게 만든다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ly + 86, '두 말이 다 그럴듯하다는 것이 이 자리가 미결인 이유다. 표만으로는 어느 규칙이 합리적인지 정해지지 않는다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'phi-t-maximin',
        svg: svg({
            width: W, height: H,
            title: '최소극대화와 평균 최대가 서로 다른 분배안을 고른다',
            desc: '세 분배안 A·B·C 를 다섯 자리로 적은 표. 가장 낮은 자리만 보면 C 가 가장 낫고, 평균을 보면 B 가 가장 낫다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 3. 축차적 서열 — 앞 단이 먼저다
 *
 * 두 원칙을 순서 없이 요약하면 롤스를 잘못 그리게 된다. 사다리로
 * 그려서 ‘아래 단의 이익으로 위 단을 살 수 없다’ 를 눈에 보이게 한다.
 * ================================================================== */
add((() => {
    const W = 840;
    const H = 424;
    const g = [];

    g.push(txt(40, 28, '두 원칙은 순서를 갖는다 — 축차적 서열', { cls: 'ink bold' }));
    g.push(txt(40, 50, '위 단이 충족되기 전에는 아래 단으로 내려가지 않는다. 아래 단의 이익으로 위 단을 사지 못한다', { cls: 'ink2', size: 'sm' }));

    const bx = 40;
    const bw = 620;
    const bh = 74;
    const gap = 14;
    const y1 = 72;

    g.push(cell(bx, y1, bw, bh, '1단 — 제1원칙: 평등한 기본적 자유', [
        '각자가 같은 기본적 자유의 체계에 대해 동등한 권리를 갖는다.',
        '양심·사상·정치 참여·신체의 자유, 법의 지배. 자유는 자유를 위해서만 제한된다',
    ], { accent: C1 }));

    const y2 = y1 + bh + gap;
    g.push(cell(bx, y2, bw, bh, '2단 — 제2원칙 (a): 공정한 기회균등', [
        '직위와 직책이 형식적으로만이 아니라 실질적으로 모두에게 열려 있어야 한다.',
        '같은 재능과 같은 의지를 가진 사람은 태어난 집안과 무관하게 같은 전망을 가져야 한다',
    ], { accent: C3 }));

    const y3 = y2 + bh + gap;
    g.push(cell(bx, y3, bw, bh, '3단 — 제2원칙 (b): 차등 원칙', [
        '남는 사회경제적 불평등은 가장 불리한 처지에 있는 사람들에게',
        '가장 큰 이익이 될 때에만 허용된다',
    ], { accent: C2 }));

    // 오른쪽 화살표: 위에서 아래로 내려가는 순서
    const ax = bx + bw + 34;
    g.push(px(ax, y1 + 20, ax, y3 + bh - 12, { cls: 's1', marker: 'ark', width: 2 }));
    g.push(txt(ax + 12, y1 + 40, '먼저', { cls: 'ink2', size: 'sm' }));
    g.push(txt(ax + 12, y2 + 40, '그 다음', { cls: 'ink2', size: 'sm' }));
    g.push(txt(ax + 12, y3 + 40, '마지막', { cls: 'ink2', size: 'sm' }));

    const ny = y3 + bh + 32;
    g.push(txt(40, ny, '이 순서를 빼고 요약하면 롤스가 아닌 다른 이론이 된다. 차등 원칙만 떼어 놓으면 자유를 팔아 몫을 사는 일이 허용되어 버린다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 22, '롤스가 든 예 — 가난한 사람의 몫을 크게 늘려 준다는 대가로 투표권을 거두는 안은 이 서열 아래에서 시작부터 후보가 되지 못한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 44, '차등 원칙이 재는 것은 개인이 아니라 가장 불리한 처지라는 자리이고, 재는 눈금은 한 시점의 소득이 아니라 평생에 걸친 기본 가치의 전망이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-t-lexical-order',
        svg: svg({
            width: W, height: H,
            title: '롤스의 두 원칙과 축차적 서열',
            desc: '세 단이 위에서 아래로 놓인 사다리. 평등한 기본적 자유가 첫째, 공정한 기회균등이 둘째, 차등 원칙이 셋째다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4. 윌트 체임벌린 — 정형이 어떻게 깨지는가
 *
 * 이 그림이 보여야 할 것은 ‘부자가 생겼다’ 가 아니라 ‘중간의 어느
 * 단계에서도 부당한 일이 일어나지 않았다’ 는 것이다.
 * ================================================================== */
add((() => {
    const W = 840;
    const H = 396;
    const g = [];

    g.push(txt(40, 28, '자발적인 이전만으로 정형이 깨진다', { cls: 'ink bold' }));

    const base = 214;
    const bw = 22;
    const n = 8;

    // 왼쪽 D1
    g.push(txt(40, 62, 'D1 — 당신이 고른 정형 원리가 정의롭다고 판정한 분배', { cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < n; i += 1) {
        const x = 46 + i * 32;
        g.push(box(x, base - 70, bw, 70, { fill: C1, op: 0.55, stroke: C1, sw: 1.2 }));
    }
    g.push(ln([[40, base], [46 + n * 32, base]], { stroke: CK, sw: 1.4 }));
    g.push(txt(40, base + 20, '여덟 사람이 똑같이 가졌다고 하자', { cls: 'ink2', size: 'sm' }));

    // 가운데 화살표
    const mx = 330;
    g.push(px(mx, base - 40, mx + 92, base - 40, { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(txt(mx + 46, base - 56, '각자 25센트', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(mx + 46, base - 18, '자발적으로 낸다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(mx + 46, base + 2, '속임도 강요도 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 D2
    const rx = 470;
    g.push(txt(rx, 62, 'D2 — 이전이 끝난 뒤의 분배', { cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < n; i += 1) {
        const x = rx + 6 + i * 32;
        g.push(box(x, base - 58, bw, 58, { fill: C1, op: 0.55, stroke: C1, sw: 1.2 }));
    }
    const wx = rx + 6 + n * 32 + 14;
    g.push(box(wx, base - 152, bw + 8, 152, { fill: C2, op: 0.55, stroke: C2, sw: 1.6 }));
    g.push(txt(wx + 15, base - 164, '체임벌린', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ln([[rx, base], [wx + bw + 20, base]], { stroke: CK, sw: 1.4 }));
    g.push(txt(rx, base + 20, '정형이 깨졌다. 그런데 어느 단계에서 부당한 일이 있었는가', { cls: 'ink2', size: 'sm' }));

    const ny = base + 58;
    g.push(txt(40, ny, '이 논증이 말하는 것은 ‘재분배는 나쁘다’ 가 아니다. 분배의 모양을 정해 두는 원리와 자발적 이전이 함께 갈 수 없다는 것이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(40, ny + 24, '정형을 지키려면 둘 중 하나를 해야 한다. 사람들이 자기 몫으로 하려는 일을 계속 막거나, 하고 난 뒤에 계속 되돌리거나', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 46, '반론이 들어오는 자리도 정해져 있다. D1 의 몫이 어떻게 정당해졌는가, 무엇을 자유의 침해로 셀 것인가, 같은 이전이 되풀이되면 어떻게 되는가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 68, '노직이 든 원래 숫자는 관중 100만 명에 25만 달러였다. 여기서는 그림으로 보이려고 사람 수를 여덟으로 줄였다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-t-chamberlain',
        svg: svg({
            width: W, height: H,
            title: '윌트 체임벌린 사례에서 정형이 깨지는 과정',
            desc: '왼쪽은 모두가 똑같이 가진 분배, 오른쪽은 각자가 조금씩 넘겨준 뒤 한 사람만 크게 가진 분배다. 중간의 어느 단계에서도 강요나 속임이 없었다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5. 콩도르세 배심 정리 — 전제가 지켜질 때와 깨질 때
 *
 * 정리의 결론만 외우면 ‘사람이 많을수록 옳다’ 로 오해한다. p 가
 * 0.5 아래로 내려가면 같은 정리가 정반대 결론을 낸다는 것을 한 그림에
 * 같이 그려야 그 오해가 막힌다.
 * ================================================================== */
add((() => {
    const W = 800;
    const H = 456;

    /** n 명이 각자 확률 p 로 옳을 때 다수결이 옳을 확률(n 은 홀수). */
    const majority = (n, p) => {
        let logFact = [0];
        for (let i = 1; i <= n; i += 1) logFact[i] = logFact[i - 1] + Math.log(i);
        let s = 0;
        for (let k = Math.floor(n / 2) + 1; k <= n; k += 1) {
            const lg = logFact[n] - logFact[k] - logFact[n - k]
                + k * Math.log(p) + (n - k) * Math.log(1 - p);
            s += Math.exp(lg);
        }
        return s;
    };

    const ns = [];
    for (let k = 1; k <= 61; k += 2) ns.push(k);

    const f = frame({
        xRange: [1, 61],
        yRange: [0, 1],
        box: { x: 70, y: 82, w: 480, h: 250 },
    });

    const g = [];
    g.push(txt(40, 28, '콩도르세 배심 정리 — 사람이 많아지면 다수결이 어디로 가는가', { cls: 'ink bold' }));
    g.push(f.axes({
        xLabel: '투표자 수', yLabel: '다수결이 옳을 확률',
        xTicks: [1, 11, 21, 31, 41, 51, 61],
        yTicks: [0, 0.25, 0.5, 0.75, 1],
    }));

    const series = [
        { p: 0.6, cls: 's1', name: '각자 옳을 확률 0.6' },
        { p: 0.51, cls: 's3', name: '각자 옳을 확률 0.51' },
        { p: 0.45, cls: 's2', name: '각자 옳을 확률 0.45' },
    ];
    for (const s of series) {
        g.push(f.line(ns.map(k => [k, majority(k, s.p)]), { cls: s.cls }));
    }
    g.push(f.line([[1, 0.5], [61, 0.5]], { cls: 'gr', dash: '4 3' }));

    // 오른쪽 범례 — legend() 대신 곡선 끝에 직접 붙인다
    g.push(txt(560, f.Y(majority(61, 0.6)) + 4, '0.6 → 1 로 간다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(560, f.Y(majority(61, 0.51)) + 4, '0.51 → 느리지만 1 로 간다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(560, f.Y(majority(61, 0.45)) + 4, '0.45 → 0 으로 간다', { cls: 'ink2', size: 'sm' }));

    const ny = 368;
    g.push(txt(40, ny, '위 두 곡선이 정리의 좋은 소식이고, 맨 아래 곡선이 같은 정리의 나쁜 소식이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(40, ny + 22, '각자가 옳을 확률이 절반을 조금이라도 넘으면 수가 많아질수록 다수결은 거의 확실히 옳아진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 44, '절반에 못 미치면 수가 많아질수록 다수결은 거의 확실히 틀린다. 큰 집단은 옳음도 그름도 함께 증폭한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, ny + 66, '게다가 이 곡선은 투표가 서로 독립이라야 나온다. 같은 정보원을 보는 사람들의 오류는 서로 붙어 다니고, 그러면 곡선이 훨씬 눕는다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-t-condorcet',
        svg: svg({
            width: W, height: H,
            title: '콩도르세 배심 정리의 두 얼굴',
            desc: '투표자 수에 따른 다수결의 정확도 곡선 셋. 각자 옳을 확률이 0.5 를 넘으면 1 로 수렴하고, 밑돌면 0 으로 수렴한다',
            body: g.join(''),
        }),
    };
})());

export default figures;
