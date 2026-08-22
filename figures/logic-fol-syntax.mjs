/**
 * 논리학 문서 9장(술어논리의 문법)의 그림.
 *
 * 이름은 모두 `log-g-` 로 시작한다(이 장에 배정된 접두어).
 * 상자와 화살표로 되는 것은 `d2/logic/log-g-*.d2` 에 두고, 여기에는 글자 하나하나의
 * 자리를 잡아야 하는 것 — 한정사의 범위 표시, 자유·속박 나타남, 대입 — 과 원 그림을 둔다.
 * d2 는 그런 것을 그리지 못한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 연결자와 한정사는 유니코드 ¬ ∧ ∨ → ↔ ∀ ∃ 로 적는다. lib 의 esc() 가 물결표를
 * 아래첨자로 바꾸고 큰따옴표를 이중 이스케이프하므로, 물결표는 쓰지 않고 따옴표는 ‘ ’ 를 쓴다.
 *
 * 이 장도 5장과 같은 규율을 지킨다 — 그림 어디에도 참·거짓이 없다. 마지막 그림(두 정형)만
 * 옮기기를 다루므로 ‘무엇을 요구하는가’ 를 말하는데, 진리조건 자체는 10장의 몫이다.
 */
import { svg } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';   // 속박된 나타남 · 전칭 쪽
const C2 = 'var(--s2)';   // 자유 나타남 · 어긋난 것
const C3 = 'var(--s3)';   // 맞는 것 · 강조
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구.
 *
 * lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 ar1/ark 클래스가 없어 선이
 * 사라지고 화살촉만 남는다. 색을 직접 넣는다. txt() 는 글자 크기를 두 가지만 주므로,
 * 식을 크게 적어야 하는 이 장에서는 크기를 지정할 수 있는 gt() 를 쓴다.
 * ------------------------------------------------------------------ */

const COL = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG };

function arw(x1, y1, x2, y2, { cls = 'ark', width = 1.7, dash } = {}) {
    const col = COL[cls] ?? CK;
    const mk = cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark';
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = CK, sw = 1.4, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * 글자. 크기와 색을 직접 준다. lib 의 esc() 를 거치지 않아 물결표가 안전하다.
 *
 * 크기는 반드시 style 로 준다. svg() 가 넣는 스타일시트에 `text{font-size:13px}` 이
 * 있어서, font-size 속성으로 주면 규칙이 이겨 버리고 모든 글자가 13px 로 나온다.
 * 인라인 style 은 그 규칙을 이긴다.
 */
function gt(x, y, s, { col = CI, size = 12, anchor = 'start', bold = false } = {}) {
    const e = String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" style="font-size:${size}px" fill="${col}"${bold ? ' font-weight="600"' : ''}>${e}</text>`;
}

/** 줄 간격이 일정한 글 묶음. 기본 크기는 겹침이 나지 않는 11px 로 둔다. */
function lines(x, y, arr, { lh = 19, size = 11, col = CK } = {}) {
    return arr.map((s, i) => (s === '' ? '' : gt(x, y + i * lh, s, { col, size }))).join('');
}

function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? gt(x + w / 2, y + 20, title, { anchor: 'middle', col: CI, size: 13, bold: true }) : '')
        + (sub ? gt(x + w / 2, y + 37, sub, { anchor: 'middle', col: CK, size: 11 }) : '');
}

function chip(cx, cy, w, h, label, col, { size = 13 } = {}) {
    return box(cx - w / 2, cy - h / 2, w, h, { fill: col, op: 0.14, stroke: col, sw: 1.5, rx: 5 })
        + gt(cx, cy + size * 0.36, label, { anchor: 'middle', col: CI, size, bold: true });
}

/* ------------------------------------------------------------------ *
 * 식을 글자 한 칸씩 늘어놓는다.
 *
 * 폰트가 고정폭이 아니므로 글자마다 자리를 잡아 주어야 밑줄·화살표가 글자와 맞는다.
 * 각 글자를 폭 cw 인 칸 한가운데에 놓고 칸의 중심 좌표를 함께 돌려준다. 나타남에
 * 라벨을 붙이고 범위를 표시하는 그림에서는 이 좌표가 전부다.
 * ------------------------------------------------------------------ */
function formula(x0, y, spec, { cw = 17, size = 20 } = {}) {
    const out = [];
    const cx = [];
    spec.forEach((it, i) => {
        const c = x0 + i * cw + cw / 2;
        cx.push(c);
        if (it.c === ' ') return;
        out.push(gt(c, y, it.c, { anchor: 'middle', col: it.col ?? CI, size, bold: true }));
    });
    return { body: out.join(''), cx, right: x0 + spec.length * cw };
}

/** 문자열을 formula() 가 받는 모양으로. 자리별로 색을 덧칠한다. */
function chars(str, paint = {}) {
    return [...str].map((c, i) => ({ c, col: paint[i] }));
}

/** 범위를 나타내는 아래쪽 꺾쇠. 양 끝이 위로 꺾인다. */
function scopeBar(xa, xb, y, label, col) {
    return ln([[xa, y - 6], [xa, y], [xb, y], [xb, y - 6]], { stroke: col, sw: 1.6 })
        + gt((xa + xb) / 2, y + 14, label, { anchor: 'middle', col, size: 10, bold: true });
}

/* ================================================================== *
 * 9-1. 명제논리는 원자 안을 보지 못한다
 * ================================================================== */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(gt(W / 2, 26, '같은 논증을 두 언어로 옮긴 것 — 오른쪽에서만 낱말의 되풀이가 보인다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const py = 42, ph = 212;

    /* 왼쪽 — 명제논리 */
    g.push(panel(20, py, 358, ph, '명제논리로 옮기면', '원자는 더 쪼개지지 않는다'));
    const rowsL = [
        ['모든 사람은 죽는다', 'P', '전제'],
        ['소크라테스는 사람이다', 'Q', '전제'],
        ['소크라테스는 죽는다', 'R', '결론'],
    ];
    rowsL.forEach((r, i) => {
        const y = py + 70 + i * 42;
        g.push(gt(38, y + 4, r[0], { col: CK, size: 12 }));
        g.push(chip(232, y, 40, 30, r[1], C1, { size: 16 }));
        g.push(gt(262, y + 4, r[2], { col: CK, size: 11 }));
    });
    g.push(gt(38, py + 196, '세 상자 사이에 아무 연결이 없다', { col: C2, size: 12, bold: true }));

    /* 오른쪽 — 술어논리 */
    g.push(panel(398, py, 362, ph, '술어논리로 옮기면', '원자 안이 술어와 이름으로 갈린다'));
    const rowsR = [['∀x(Fx → Gx)', '전제'], ['Fa', '전제'], ['Ga', '결론']];
    rowsR.forEach((r, i) => {
        const y = py + 70 + i * 42;
        g.push(gt(420, y + 6, r[0], { col: CI, size: 18, bold: true }));
        g.push(gt(700, y + 4, r[1], { col: CK, size: 11 }));
    });
    // F · G · a 가 여러 줄에 되풀이된다는 것을 잇는 점선
    g.push(ln([[436, py + 82], [436, py + 106], [426, py + 106]], { stroke: C1, sw: 1.4, dash: '4 3' }));
    g.push(ln([[482, py + 82], [500, py + 82], [500, py + 148], [428, py + 148]], { stroke: C2, sw: 1.4, dash: '4 3' }));
    g.push(ln([[440, py + 120], [560, py + 120], [560, py + 154], [442, py + 154]], { stroke: C3, sw: 1.4, dash: '4 3' }));
    g.push(gt(578, py + 86, 'F 가 두 곳에', { col: C1, size: 11, bold: true }));
    g.push(gt(578, py + 106, 'G 가 두 곳에', { col: C2, size: 11, bold: true }));
    g.push(gt(578, py + 126, 'a 가 두 곳에', { col: C3, size: 11, bold: true }));
    g.push(gt(420, py + 196, '되풀이가 논증을 떠받친다', { col: C3, size: 12, bold: true }));

    g.push(lines(24, py + ph + 24, [
        '읽어낼 것 — 왼쪽 세 상자는 서로 아무 관계가 없는 별개의 문자다. 그래서 앞의 둘이 참이면서 셋째가 거짓인 배당이 있고,',
        '6장의 뜻으로 [P, Q ∴ R] 은 부당한 형식이다. 그런데 원래 논증은 타당하다. 명제논리가 틀린 것이 아니라 논증을 떠받치는',
        '것이 원자 안에 있어 보이지 않는 것이다. 오른쪽은 그 안을 열어 술어 F · G 와 이름 a 로 갈랐고, 그러자 되풀이가 드러난다.',
        '이 장이 하는 일은 오른쪽 기호열들이 무엇인지를 모양만으로 정하는 것이다 — 타당성 자체는 10장과 11장의 몫이다',
    ]));

    return {
        name: 'log-g-inside-atom',
        svg: svg({
            width: W, height: H,
            title: '같은 논증을 명제논리와 술어논리로 옮긴 것의 비교',
            desc: '왼쪽은 세 문장을 서로 무관한 문장문자 P Q R 로 옮겨 아무 연결이 없고, 오른쪽은 술어와 이름으로 갈라 F 와 G 와 a 가 여러 문장에 되풀이되는 것이 드러난다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-2. 어휘 — 무엇이 늘고 무엇이 빠졌는가
 * ================================================================== */
add((() => {
    const W = 780, H = 396;
    const g = [];
    g.push(gt(W / 2, 26, '5장의 어휘에서 문장문자가 빠지고 세 무리가 들어왔다 — 목록에 없는 기호는 하나도 쓸 수 없다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const py = 42, ph = 206;

    g.push(panel(20, py, 240, ph, '대상을 가리키는 기호', '이 둘이 항을 이룬다'));
    g.push(gt(38, py + 62, '개체상수', { col: C1, size: 12, bold: true }));
    g.push(gt(112, py + 64, 'a  b  c  a₁  a₂  …', { col: CI, size: 15, bold: true }));
    g.push(gt(38, py + 84, '정해진 하나를 가리킨다', { col: CK, size: 11 }));
    g.push(gt(38, py + 122, '변수', { col: C2, size: 12, bold: true }));
    g.push(gt(112, py + 124, 'x  y  z  x₁  x₂  …', { col: CI, size: 15, bold: true }));
    g.push(gt(38, py + 144, '혼자서는 무엇을 가리키는지 정해지지 않는다', { col: CK, size: 11 }));

    g.push(panel(274, py, 236, ph, '문장을 만드는 기호', '술어에는 항수가 붙어 있다'));
    const pr = [['F', '1항', 'Fa   Fx'], ['G', '2항', 'Gab   Gxy'], ['H', '3항', 'Habc']];
    pr.forEach((r, i) => {
        const y = py + 64 + i * 23;
        g.push(gt(294, y, r[0], { col: C3, size: 15, bold: true }));
        g.push(gt(320, y, r[1], { col: CK, size: 11 }));
        g.push(gt(364, y, r[2], { col: CI, size: 14, bold: true }));
    });
    g.push(gt(294, py + 142, '한정사    ∀    ∃', { col: C1, size: 15, bold: true }));
    g.push(gt(294, py + 164, '술어의 항수는 미리 정해지고 바뀌지 않는다', { col: CK, size: 11 }));

    g.push(panel(524, py, 236, ph, '5장에서 그대로 오는 것', '뜻도 항수도 그대로다'));
    g.push(gt(544, py + 62, '¬   ∧   ∨   →   ↔', { col: C2, size: 17, bold: true }));
    g.push(gt(544, py + 82, '연결자 다섯. ¬ 만 1항이다', { col: CK, size: 11 }));
    g.push(gt(544, py + 120, '(        )', { col: C3, size: 17, bold: true }));
    g.push(gt(544, py + 140, '괄호. 뜻이 없는 구두점이다', { col: CK, size: 11 }));
    g.push(gt(544, py + 178, '⊥', { col: C1, size: 17, bold: true }));
    g.push(gt(544, py + 196, '7장이 어휘에 더한 것. 항수 0 이다', { col: CK, size: 11 }));

    const qy = py + ph + 14;
    g.push(panel(20, qy, 740, 106, '어휘에 없는 것 — 이 장에서 가장 자주 섞여 들어오는 셋', null));
    g.push(lines(40, qy + 46, [
        '함수기호 f g — 이 문서는 쓰지 않기로 정했다. 그래서 항의 재귀 조항이 비어 있고, 항은 개체상수 아니면 변수 둘뿐이다',
        '문장문자 P Q R — 술어논리의 어휘에서 빠졌다. 그래서 술어에 P 를 쓰지 않는다. 술어는 F G H 로만 적는다',
        '쉼표 — 5장과 같다. 원자식은 Gab 처럼 항을 나란히 붙여 적으므로 Gab 안에 쉼표도 괄호도 없다',
    ], { lh: 20, size: 12 }));

    return {
        name: 'log-g-vocabulary',
        svg: svg({
            width: W, height: H,
            title: '술어논리의 어휘와 어휘가 아닌 기호들',
            desc: '개체상수와 변수가 항을 이루고 항수가 붙은 술어와 한정사 두 개가 더해지며 연결자 다섯과 괄호와 ⊥ 는 5장과 7장에서 그대로 온다. 함수기호와 문장문자와 쉼표는 어휘가 아니다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-3. 자유 나타남과 속박 나타남 — 이 장에서 가장 걸리는 자리
 * ================================================================== */
add((() => {
    const W = 780, H = 424;
    const g = [];
    g.push(gt(W / 2, 26, '자유와 속박은 변수에 붙는 성질이 아니라 변수의 나타남 하나하나에 붙는 성질이다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const rows = [
        {
            y: 96, str: '∀x(Fx → Gx)',
            bound: [1, 4, 9], free: [], scope: [2, 10],
            note: ['x 의 나타남이 셋인데 모두 ∀x 의 범위 안에 있다.', '자유 나타남이 하나도 없으므로 이것은 문장이다'],
        },
        {
            y: 198, str: '(∀x Fx ∧ Gx)',
            bound: [2, 5], free: [10], scope: [4, 5],
            note: ['∀x 의 범위는 Fx 까지다. Gx 의 x 는 범위 밖이라 자유다.', '같은 변수 x 가 한 식 안에서 속박이기도 자유이기도 하다'],
        },
        {
            y: 300, str: '∃y(Gxy ∧ ∀x Fx)',
            bound: [1, 5, 10, 13], free: [4], scope: [2, 14], bar: '∃y 의 범위',
            lift: { 4: 15 },
            note: ['y 의 나타남 둘은 ∃y 가 잡고 오른쪽 x 둘은 ∀x 가 잡는다.', 'Gxy 의 x 는 그것을 잡는 한정사가 없어 자유다'],
        },
    ];

    const built = rows.map((row) => {
        const paint = {};
        for (const i of row.bound) paint[i] = C1;
        for (const i of row.free) paint[i] = C2;
        const lift = row.lift ?? {};
        const f = formula(40, row.y, chars(row.str, paint));
        g.push(f.body);
        g.push(scopeBar(f.cx[row.scope[0]] - 8, f.cx[row.scope[1]] + 8, row.y + 16, row.bar ?? '∀x 의 범위', C3));
        for (const i of row.bound) g.push(gt(f.cx[i], row.y - 20 - (lift[i] ?? 0), '속박', { anchor: 'middle', col: C1, size: 10, bold: true }));
        for (const i of row.free) g.push(gt(f.cx[i], row.y - 20 - (lift[i] ?? 0), '자유', { anchor: 'middle', col: C2, size: 10, bold: true }));
        g.push(lines(354, row.y - 4, row.note, { lh: 19, size: 12 }));
        return f;
    });

    // 첫 줄에만 ‘무엇이 무엇을 잡는가’ 를 화살표로 그린다. 세 줄 다 그리면 붐빈다.
    const f0 = built[0];
    g.push(arw(f0.cx[4], 96 - 34, f0.cx[1] + 4, 96 - 34, { cls: 's1', width: 1.4, dash: '4 3' }));
    g.push(arw(f0.cx[9], 96 - 48, f0.cx[1] + 4, 96 - 48, { cls: 's1', width: 1.4, dash: '4 3' }));
    g.push(gt(216, 96 - 44, '∀x 가 잡는다', { col: C1, size: 10, bold: true }));

    g.push(ln([[20, 348], [760, 348]], { stroke: CG, sw: 1 }));
    g.push(lines(24, 368, [
        '읽어낼 것 — 판정은 나타남마다 하나씩 한다. 그 자리를 감싸는 한정사들을 안쪽에서 바깥쪽으로 훑어, 같은 변수를 쓴 한정사가',
        '하나라도 있으면 속박이고 하나도 없으면 자유다. 둘 있으면 가장 가까운 것이 잡는다. 한정사에 붙어 있는 변수 자신도 속박이다.',
        '자유 나타남이 하나도 없는 적형식을 문장이라 하고, 10장에서 참·거짓이 붙는 것은 문장뿐이다',
    ]));

    return {
        name: 'log-g-free-bound',
        svg: svg({
            width: W, height: H,
            title: '자유 나타남과 속박 나타남, 그리고 한정사의 범위',
            desc: '세 적형식에서 변수의 나타남마다 자유인지 속박인지 표시하고 한정사의 범위를 꺾쇠로 나타낸 그림. 둘째 식에서는 같은 변수 x 가 속박된 나타남과 자유 나타남을 함께 갖는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-4. 대입 — 안전한 경우와 포획이 일어나는 경우
 * ================================================================== */
add((() => {
    const W = 780, H = 432;
    const g = [];
    g.push(gt(W / 2, 26, '대입은 자유 나타남만 바꾼다 — 바꿔 넣은 자리에서 새 변수가 잡히면 대입이 망가진다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const py = 42, ph = 138;

    /* 안전한 대입 */
    g.push(panel(20, py, 740, ph, '안전한 대입 — 개체상수를 넣는다', '넣는 것에 변수가 없으므로 잡힐 것이 없다'));
    const a1 = formula(60, py + 88, chars('(∀x Fx ∧ Gx)', { 2: C1, 5: C1, 10: C2 }));
    g.push(a1.body);
    g.push(gt(a1.cx[2], py + 66, '속박', { anchor: 'middle', col: C1, size: 10, bold: true }));
    g.push(gt(a1.cx[5], py + 66, '속박', { anchor: 'middle', col: C1, size: 10, bold: true }));
    g.push(gt(a1.cx[10], py + 66, '자유', { anchor: 'middle', col: C2, size: 10, bold: true }));
    g.push(gt(a1.right + 12, py + 88, '[a/x]', { col: C3, size: 19, bold: true }));
    g.push(arw(a1.right + 72, py + 82, a1.right + 114, py + 82, { cls: 'ark', width: 1.6 }));
    const a2 = formula(a1.right + 130, py + 88, chars('(∀x Fx ∧ Ga)', { 2: C1, 5: C1, 10: C3 }));
    g.push(a2.body);
    g.push(gt(a2.cx[10], py + 66, '바뀐 자리', { anchor: 'middle', col: C3, size: 10, bold: true }));
    g.push(gt(a2.right + 16, py + 88, '속박은 그대로다', { col: CK, size: 11 }));
    g.push(gt(60, py + 118, '대입 표기 [a/x] 는 위가 들어가는 것, 아래가 밀려나는 것이다. 방향을 반대로 적는 책도 있으니 이 문서의 약속을 기억하라', { col: CK, size: 11 }));

    /* 포획 */
    const qy = py + ph + 16;
    g.push(panel(20, qy, 740, 152, '포획 — 변수를 넣었더니 한정사에 잡혔다', '넣은 y 는 원래 자유였는데 ∃y 의 범위 안으로 들어가 버린다'));
    const b1 = formula(60, qy + 90, chars('∃y Gxy', { 1: C1, 4: C2, 5: C1 }));
    g.push(b1.body);
    g.push(gt(b1.cx[4], qy + 68, '자유', { anchor: 'middle', col: C2, size: 10, bold: true }));
    g.push(gt(b1.right + 12, qy + 90, '[y/x]', { col: C2, size: 19, bold: true }));
    g.push(arw(b1.right + 72, qy + 84, b1.right + 114, qy + 84, { cls: 's2', width: 1.6 }));
    const b2 = formula(b1.right + 130, qy + 90, chars('∃y Gyy', { 1: C1, 4: C2, 5: C1 }));
    g.push(b2.body);
    g.push(arw(b2.cx[4], qy + 68, b2.cx[1], qy + 68, { cls: 's2', width: 1.4, dash: '4 3' }));
    g.push(gt(b2.cx[4] + 18, qy + 64, '∃y 가 잡아 버렸다', { col: C2, size: 11, bold: true }));

    g.push(ln([[60, qy + 108], [700, qy + 108]], { stroke: CG, sw: 1, dash: '4 4' }));
    g.push(gt(60, qy + 128, '피하는 법 — 속박 변수의 이름을 먼저 바꾼다.', { col: C3, size: 12, bold: true }));
    g.push(gt(316, qy + 128, '∃y Gxy 를 ∃z Gxz 로 고치면 넣은 y 가 자유로 남는다', { col: CK, size: 11 }));

    g.push(lines(24, qy + 180, [
        '읽어낼 것 — 개체상수에는 변수가 없어 언제나 안전하다. 그래서 11장은 한정사 규칙을 개체상수로만 진술하고 포획이 아예 일어나지 않게 한다.',
        '대신 그 자리에 다른 종류의 조건이 붙는다 — 쓴 이름이 열린 가정에 나타나지 않아야 한다는 조건이다. 목적은 같다.',
        '대입한 결과가 원래 식이 말하지 않은 것을 말하게 두지 않는 것이다',
    ]));

    return {
        name: 'log-g-substitution',
        svg: svg({
            width: W, height: H,
            title: '대입의 두 경우 — 개체상수를 넣는 안전한 대입과 변수 포획',
            desc: '위쪽은 개체상수를 넣어 자유 나타남만 바뀌고 속박 나타남은 그대로인 안전한 대입, 아래쪽은 변수를 넣었더니 존재 한정사에 잡혀 뜻이 달라지는 포획과 속박 변수의 이름을 바꿔 피하는 방법',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-5. 두 정형 — ∀ 는 → 를, ∃ 는 ∧ 를 데리고 온다
 * ================================================================== */
add((() => {
    const W = 780, H = 500;
    const g = [];
    g.push(gt(W / 2, 26, '한정사는 논의 영역 전체를 훑는다 — 영역을 F 인 것으로 좁히는 일을 ∀ 는 → 로, ∃ 는 ∧ 로 한다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    /** 영역 사각형 안에 F 원과 G 원을 그린다. dot 은 판정을 가르는 대상 하나다. */
    function world(x, y, w, h, { fIn = false, dot, dotCol = C2 } = {}) {
        const out = [box(x, y, w, h, { stroke: CK, sw: 1.3, rx: 5 })];
        out.push(gt(x + 6, y + 14, 'D', { col: CK, size: 11, bold: true }));
        const cy = y + h / 2 + 4;
        if (fIn) {
            out.push(`<circle cx="${r2(x + 76)}" cy="${r2(cy)}" r="32" fill="${C3}" fill-opacity="0.1" stroke="${C3}" stroke-width="1.5"/>`);
            out.push(`<circle cx="${r2(x + 68)}" cy="${r2(cy)}" r="16" fill="${C1}" fill-opacity="0.18" stroke="${C1}" stroke-width="1.5"/>`);
            out.push(gt(x + 68, cy + 4, 'F', { anchor: 'middle', col: C1, size: 12, bold: true }));
            out.push(gt(x + 96, cy - 19, 'G', { anchor: 'middle', col: C3, size: 12, bold: true }));
        } else {
            out.push(`<circle cx="${r2(x + 52)}" cy="${r2(cy)}" r="28" fill="${C1}" fill-opacity="0.14" stroke="${C1}" stroke-width="1.5"/>`);
            out.push(`<circle cx="${r2(x + 90)}" cy="${r2(cy)}" r="28" fill="${C3}" fill-opacity="0.14" stroke="${C3}" stroke-width="1.5"/>`);
            out.push(gt(x + 34, cy + 4, 'F', { anchor: 'middle', col: C1, size: 12, bold: true }));
            out.push(gt(x + 108, cy + 4, 'G', { anchor: 'middle', col: C3, size: 12, bold: true }));
        }
        if (dot) out.push(`<circle cx="${r2(x + dot[0] * w)}" cy="${r2(y + dot[1] * h)}" r="4.5" fill="${dotCol}"/>`);
        return out.join('');
    }

    const cells = [
        {
            x: 20, y: 44, f: '∀x(Fx → Gx)', ok: true, head: '모든 F 는 G 다', fIn: true,
            memo: ['F 원이 통째로 G 원 안에 들어가면 된다. F 가 아닌 것에 대해서는', '→ 의 전건이 거짓이라 이 식이 아무것도 요구하지 않는다'],
        },
        {
            x: 400, y: 44, f: '∀x(Fx ∧ Gx)', ok: false, head: '너무 세다', fIn: false, dot: [0.12, 0.84],
            memo: ['영역 전체가 F 이면서 G 여야 한다. 점 하나만 있어도 무너진다.', '‘모든 것이 F 다’ 까지 말해 버리는 것이 잘못이다'],
        },
        {
            x: 20, y: 262, f: '∃x(Fx ∧ Gx)', ok: true, head: '어떤 F 는 G 다', fIn: false, dot: [0.5, 0.55], dotCol: C3,
            memo: ['겹치는 자리에 초록 점 하나만 있으면 된다. ∧ 이 F 인 것과', 'G 인 것을 한 대상에게 동시에 요구하기 때문이다'],
        },
        {
            x: 400, y: 262, f: '∃x(Fx → Gx)', ok: false, head: '너무 약하다', fIn: false, dot: [0.9, 0.84],
            memo: ['F 가 아닌 점 하나만 있어도 → 의 전건이 거짓이라 성립한다.', 'F 와 G 에 대해 사실상 아무 말도 하지 않는 식이다'],
        },
    ];

    for (const c of cells) {
        const w = 360, h = 194;
        g.push(panel(c.x, c.y, w, h, null, null));
        g.push(gt(c.x + 16, c.y + 30, c.f, { col: CI, size: 18, bold: true }));
        g.push(chip(c.x + w - 62, c.y + 24, 86, 24, c.ok ? '맞는 옮김' : '틀린 옮김', c.ok ? C3 : C2, { size: 11 }));
        g.push(world(c.x + 16, c.y + 44, 140, 96, { fIn: c.fIn, dot: c.dot, dotCol: c.dotCol }));
        g.push(gt(c.x + 176, c.y + 94, c.head, { col: c.ok ? C3 : C2, size: 14, bold: true }));
        g.push(lines(c.x + 16, c.y + 162, c.memo, { lh: 18, size: 11 }));
    }

    g.push(lines(24, 472, [
        '읽어낼 것 — 점 하나가 무엇을 말하는지가 칸마다 다르다. 아래 왼쪽의 초록 점은 식을 성립시키는 증인이고, 오른쪽 두 칸의',
        '주황 점은 그 옮김이 틀렸다는 증거다. 정확한 진리조건은 10장에서 정의한다',
    ]));

    return {
        name: 'log-g-two-schemas',
        svg: svg({
            width: W, height: H,
            title: '전칭에는 함의를 존재에는 논리곱을 쓰는 이유',
            desc: '논의 영역 안에 F 원과 G 원을 그려 네 후보 식을 견주는 그림. 전칭에 논리곱을 쓰면 요구가 너무 세지고 존재에 함의를 쓰면 요구가 너무 약해진다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-6. 5장의 정의와 9장의 정의를 나란히
 * ================================================================== */
add((() => {
    const W = 780, H = 466;
    const g = [];
    g.push(gt(W / 2, 26, '조항은 여전히 셋이다 — 달라진 곳이 정확히 두 군데뿐이라는 것이 이 그림의 전부다', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const LX = 20, RX = 396, CW = 364;
    g.push(gt(LX + CW / 2, 52, '5장 — 명제논리의 적형식', { anchor: 'middle', col: CK, size: 13, bold: true }));
    g.push(gt(RX + CW / 2, 52, '9장 — 술어논리의 적형식', { anchor: 'middle', col: CI, size: 13, bold: true }));

    const rows = [
        {
            y: 64, h: 54, changed: true,
            L: ['기저 — 모든 문장문자는 적형식이다'],
            R: ['기저 — 모든 원자식은 적형식이다', '⊥ 도 적형식이다 — 7장에서 그대로 온다'],
            tag: '바뀐 첫째 자리',
        },
        {
            y: 126, h: 148, changed: true,
            L: ['재귀 — φ 가 적형식이면 ¬φ 도 적형식이다',
                'φ 와 ψ 가 적형식이면 (φ ∧ ψ), (φ ∨ ψ),',
                '(φ → ψ), (φ ↔ ψ) 도 적형식이다'],
            R: ['재귀 — φ 가 적형식이면 ¬φ 도 적형식이다',
                'φ 와 ψ 가 적형식이면 (φ ∧ ψ), (φ ∨ ψ),',
                '(φ → ψ), (φ ↔ ψ) 도 적형식이다',
                '',
                'φ 가 적형식이고 x 가 변수이면',
                '∀xφ 와 ∃xφ 도 적형식이다'],
            tag: '바뀐 둘째 자리',
        },
        {
            y: 282, h: 62, changed: false,
            L: ['폐포 — 위 두 조항을 유한 번 적용해', '얻어지는 기호열만이 적형식이다'],
            R: ['폐포 — 위 두 조항을 유한 번 적용해', '얻어지는 기호열만이 적형식이다'],
            tag: '글자 하나 바뀌지 않는다',
        },
        {
            y: 352, h: 58, changed: true,
            L: ['‘모든 적형식에 대하여’ 를 증명할 때 확인할 갈래 여섯', '문장문자 · ¬ · ∧ · ∨ · → · ↔ (7장의 ⊥ 을 넣으면 일곱)'],
            R: ['‘모든 적형식에 대하여’ 를 증명할 때 확인할 갈래 아홉', '원자식 · ⊥ · ¬ · ∧ · ∨ · → · ↔ · ∀ · ∃'],
            tag: null,
        },
    ];

    for (const r of rows) {
        g.push(box(LX, r.y, CW, r.h, { stroke: CG, sw: 1, rx: 5 }));
        g.push(box(RX, r.y, CW, r.h, {
            fill: r.changed ? C1 : 'none', op: r.changed ? 0.08 : 1,
            stroke: r.changed ? C1 : CG, sw: r.changed ? 1.4 : 1, rx: 5,
        }));
        g.push(lines(LX + 14, r.y + 24, r.L, { lh: 19, size: 11.5 }));
        g.push(lines(RX + 14, r.y + 24, r.R, { lh: 19, size: 11.5, col: CI }));
        if (r.tag) g.push(gt(RX + CW - 12, r.y + r.h - 10, r.tag, { anchor: 'end', col: r.changed ? C2 : C3, size: 10, bold: true }));
        g.push(arw(LX + CW + 4, r.y + r.h / 2, RX - 6, r.y + r.h / 2, { cls: r.changed ? 's2' : 'ark', width: 1.4 }));
    }

    g.push(lines(24, 432, [
        '읽어낼 것 — 폐포 조항이 글자 하나 바뀌지 않는다는 것이 요점이다. 그래서 13장의 구조적 귀납법이 그대로 쓰이고, 5장에서',
        '여섯 갈래를 확인하던 증명이 여기서는 아홉 갈래가 된다. ⊥ 은 7장이 더한 것이고 ∀ · ∃ 가 이 장이 더한 둘이다',
    ]));

    return {
        name: 'log-g-wff-clauses',
        svg: svg({
            width: W, height: H,
            title: '5장의 적형식 정의와 9장의 적형식 정의를 나란히 놓은 것',
            desc: '기저 재귀 폐포 세 조항이 두 정의에 그대로 있고, 기저의 문장문자가 원자식으로 바뀌고 재귀에 한정사 조항이 붙는 두 곳만 달라진다. 폐포 조항은 같고 확인할 갈래가 여섯에서 아홉으로 는다',
            body: g.join(''),
        }),
    };
})());

export default figures;
