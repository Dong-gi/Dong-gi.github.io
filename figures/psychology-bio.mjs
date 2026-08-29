/**
 * 심리학 문서 13장(몸과 마음)·14장(의식과 수면)의 그림.
 *
 * 이름은 모두 `psy-b-` 로 시작한다(13·14장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * ─────────────────────────────────────────────────────────────────────
 * 이 파일에 <b>뇌 해부 그림이 없는 이유</b>
 *
 * lib.mjs 는 선과 도형만 만든다. 뇌의 겉모습과 속 구조는 겉모습 자체가
 * 자료인 자리라서, 선 몇 개로 그리면 <b>없는 것보다 나쁜 그림</b>이 된다.
 * 실제 해부와 어긋난 도식을 독자가 해부도로 읽기 때문이다. 원장 §5.2 가
 * 그 자리를 사진으로 채우라고 정해 두었고, 사진을 구하지 못한 자리는
 * <b>비워 두고 보고한다.</b> 그래서 이 파일이 그리는 것은 해부가 아니라
 * <b>추론의 구조</b>다 — 기저율, 다중비교, 민감기, 수면 단계의 진행.
 * ─────────────────────────────────────────────────────────────────────
 *
 * 자료 규칙 — 이 파일의 곡선과 격자는 전부 도식이다. 남의 원자료를 옮긴 것이
 * 하나도 없고, 기저율 그림의 숫자는 계산을 보이려고 <b>이 문서가 정한 예시값</b>이며
 * 본문에도 그렇게 적었다. 수면 그림에 세로축 눈금이 없는 것도 같은 이유다.
 *
 * 상자와 화살표만으로 되는 그림(이중 해리·역추론·분리뇌 절차·꿈 이론 비교)은
 * d2/psychology/psy-b-*.d2 에 있다.
 */
import { svg, frame, txt, px } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};

function ln(pts, { stroke = COL.ink2, sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.4, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

/* ------------------------------------------------------------------ *
 * psy-b-base-rate — 역추론의 기저율 문제. (13장)
 *
 * 무엇을 읽어야 하나: 어떤 영역이 그 과정에서 자주 켜진다는 사실만으로는
 * ‘켜졌으니 그 과정이 일어났다’ 가 따라 나오지 않는다는 것. 넓이로 보면
 * 뒤쪽 넓이(그 과정이 아닌데도 켜진 경우)가 그만큼 크기 때문이다.
 * 숫자는 계산을 보이려고 정한 예시값이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 430;
    const g = [];

    const X0 = 70;
    const Y0 = 86;
    const BW = 360;
    const BH = 240;
    const pP = 0.30;        // 그 과정이 관여하는 과제의 비율
    const hitP = 0.70;      // 그 과정이 있을 때 영역이 켜지는 비율
    const hitN = 0.40;      // 그 과정이 없을 때도 켜지는 비율

    const wP = BW * pP;
    const wN = BW - wP;

    g.push(box(X0, Y0, wP, BH, { stroke: COL.ink2, sw: 1.6 }));
    g.push(box(X0 + wP, Y0, wN, BH, { stroke: COL.ink2, sw: 1.6 }));
    g.push(box(X0, Y0 + BH * (1 - hitP), wP, BH * hitP, { fill: COL.s1, op: 0.55, stroke: COL.s1, sw: 1.6 }));
    g.push(box(X0 + wP, Y0 + BH * (1 - hitN), wN, BH * hitN, { fill: COL.s2, op: 0.5, stroke: COL.s2, sw: 1.6 }));

    g.push(txt(X0 + wP / 2, Y0 - 30, '그 과정이', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(X0 + wP / 2, Y0 - 14, '있는 과제 30%', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(X0 + wP + wN / 2, Y0 - 30, '그 과정이 없는 과제 70%', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(X0 + wP / 2, Y0 + BH * (1 - hitP) + 26, '켜짐', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(X0 + wP / 2, Y0 + BH * (1 - hitP) + 44, '70%', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(X0 + wP + wN / 2, Y0 + BH * (1 - hitN) + 26, '그래도 켜짐 40%', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(X0 + BW / 2, Y0 + BH + 22, '가로 폭 = 과제의 비율 · 세로 높이 = 켜지는 비율', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(X0 + BW / 2, Y0 + BH + 40, '그래서 칠해진 넓이가 실제 건수의 비율이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    /* 오른쪽 — 켜진 경우만 모아 다시 나눈다. */
    const aP = pP * hitP;          // 0.21
    const aN = (1 - pP) * hitN;    // 0.28
    const tot = aP + aN;
    const RX = 560;
    const RW = 250;
    const RY = 150;
    const RH = 58;
    g.push(txt(RX, RY - 44, '이제 켜진 경우만 모아 본다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(RX, RY - 24, '넓이의 비가 그대로 답이다', { cls: 'ink2', size: 'sm' }));
    g.push(box(RX, RY, (RW * aP) / tot, RH, { fill: COL.s1, op: 0.55, stroke: COL.s1, sw: 1.6 }));
    g.push(box(RX + (RW * aP) / tot, RY, (RW * aN) / tot, RH, { fill: COL.s2, op: 0.5, stroke: COL.s2, sw: 1.6 }));
    g.push(txt(RX + (RW * aP) / tot / 2, RY + 35, '43%', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(RX + (RW * aP) / tot + (RW * aN) / tot / 2, RY + 35, '57%', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(RX, RY + RH + 24, '그 과정이었을 확률 43%', { cls: 'ink', size: 'sm' }));
    g.push(txt(RX, RY + RH + 44, '아니었을 확률 57%', { cls: 'ink', size: 'sm' }));

    g.push(txt(RX, RY + RH + 82, '켜졌다는 사실만으로는', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(RX, RY + RH + 102, '동전 던지기보다 나을 것이 없다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(70, 32, '어떤 영역이 켜졌다. 그 심리 과정이 일어난 것인가', { cls: 'ink bold' }));

    return {
        name: 'psy-b-base-rate',
        svg: svg({ width: W, height: H, title: '역추론과 기저율', desc: '영역이 켜졌다는 사실에서 심리 과정을 되짚을 때 기저율이 답을 정한다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-b-voxels — 많이 재면 우연히 유의한 것이 나온다. (13장)
 *
 * 무엇을 읽어야 하나: 두 격자 모두 <b>신호가 하나도 없는 자료</b>라는 것.
 * 칠해진 칸은 전부 우연이며, 문턱을 낮추면 그 수가 줄지만 0 이 되지는 않는다.
 * 실제 영상 자료의 칸 수는 이 격자보다 몇 자릿수 많다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 960;
    const H = 420;
    const g = [];
    const cols = 36;
    const rows = 22;
    const cell = 10;
    const gap = 1.4;
    const rand = rng(20331271);

    /* 같은 난수 자료를 두 문턱으로 자른다. */
    const vals = [];
    for (let i = 0; i < cols * rows; i += 1) vals.push(rand());

    const panel = (ox, oy, thr, title, sub) => {
        const out = [];
        let hits = 0;
        for (let r = 0; r < rows; r += 1) {
            for (let c = 0; c < cols; c += 1) {
                const v = vals[r * cols + c];
                const on = v < thr;
                if (on) hits += 1;
                out.push(`<rect x="${r2(ox + c * (cell + gap))}" y="${r2(oy + r * (cell + gap))}" width="${cell}" height="${cell}" rx="1.5" fill="${on ? COL.s2 : COL.grid}" fill-opacity="${on ? 0.95 : 0.55}"/>`);
            }
        }
        out.push(txt(ox, oy - 30, title, { cls: 'ink bold', size: 'sm' }));
        out.push(txt(ox, oy - 12, sub, { cls: 'ink2', size: 'sm' }));
        out.push(txt(ox, oy + rows * (cell + gap) + 22, `칠해진 칸 ${hits}개 — 전부 우연이다`, { cls: 'ink bold', size: 'sm' }));
        return out.join('');
    };

    g.push(txt(56, 40, '두 격자에 든 자료는 같고, 그 자료에는 신호가 하나도 없다. 칸은 792개다', { cls: 'ink bold' }));
    g.push(panel(56, 108, 0.05, '문턱을 0.05 로 두면', '칸마다 따로 검사하고 보정하지 않았다'));
    g.push(panel(510, 108, 0.005, '문턱을 0.005 로 낮추면', '크게 줄지만 0 이 되지는 않는다'));
    g.push(txt(56, 400, '실제 뇌영상 한 장의 칸 수는 이 격자보다 몇 자릿수 많다. 그래서 보정하지 않으면 문턱을 아무리 낮춰도 색이 남고, 남은 칸이 서로 붙어 있으면 덩어리처럼 보인다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-b-voxels',
        svg: svg({ width: W, height: H, title: '다중비교', desc: '신호가 없는 자료에서도 문턱을 넘는 칸이 나온다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-b-sensitive-period — 같은 경험이 나이에 따라 다른 크기의 변화를 만든다. (13장)
 *
 * 무엇을 읽어야 하나: 두 곡선이 서로 다른 자리에서 내려온다는 것,
 * 그리고 어느 곡선도 0 으로 떨어지지 않는다는 것. 창이 닫히는 시기는
 * 회로마다 다르고, 닫힌 뒤에도 변화의 여지가 남는다.
 * 세로축에 눈금이 없는 것은 이 그림이 도식이기 때문이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 100], yRange: [0, 1.08], box: { x: 84, y: 56, w: 440, h: 270 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(50), 370, '나이 (왼쪽이 어릴 때)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 40, '같은 경험이 회로에 만드는 변화의 크기', { cls: 'ink2', size: 'sm' }));

    const early = x => 0.14 + 0.86 / (1 + Math.exp((x - 16) / 4.5));
    const late = x => 0.24 + 0.76 / (1 + Math.exp((x - 46) / 11));
    g.push(f.curve(early, { cls: 's2' }));
    g.push(f.curve(late, { cls: 's1' }));
    g.push(txt(f.X(30), f.Y(0.30), '일찍 닫히는 회로', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(62), f.Y(0.62), '늦게까지 열려 있는 회로', { cls: 'ink bold', size: 'sm' }));

    g.push(f.guide([0, 0.14], [100, 0.14]));
    g.push(txt(f.X(100) + 8, f.Y(0.14) + 4, '0 이 아니다', { cls: 'ink2', size: 'sm' }));

    const notes = [
        ['읽을 것', 'ink bold'],
        ['', 'ink2'],
        ['· 창이 닫히는 시기가', 'ink2'],
        ['  회로마다 다르다', 'ink2'],
        ['· 어느 곡선도 0 으로', 'ink2'],
        ['  떨어지지 않는다', 'ink2'],
        ['· 그래서 ‘결정적 시기’ 보다', 'ink2'],
        ['  ‘민감기’ 가 나은 이름이다', 'ink2'],
        ['', 'ink2'],
        ['이 그림은 도식이며', 'ink bold'],
        ['가로축에 나이의 눈금이', 'ink bold'],
        ['없는 것은 그 값이', 'ink bold'],
        ['회로마다 다르기 때문이다', 'ink bold'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(600, 84 + i * 21, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-b-sensitive-period',
        svg: svg({ width: W, height: H, title: '민감기', desc: '같은 경험이 나이에 따라 다른 크기의 변화를 만든다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-b-hypnogram — 하룻밤의 수면 단계. (14장)
 *
 * 무엇을 읽어야 하나: 잠이 한 덩어리가 아니라 <b>되풀이되는 주기</b>라는 것,
 * 깊은 잠이 앞쪽에 몰려 있고 REM 이 뒤로 갈수록 길어진다는 것.
 * 이 선은 전형적인 모양을 보이려고 그린 도식이며 어느 개인의 기록이 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 7.6], yRange: [0.5, 5.6], box: { x: 96, y: 60, w: 660, h: 230 } });

    /* 단계를 세로 자리로. 위에서부터 깸 · REM · N1 · N2 · N3. */
    const LV = { 깸: 5, REM: 4, N1: 3, N2: 2, N3: 1 };
    const seq = [
        [0.00, 0.12, '깸'], [0.12, 0.28, 'N1'], [0.28, 0.62, 'N2'], [0.62, 1.05, 'N3'],
        [1.05, 1.24, 'N2'], [1.24, 1.40, 'REM'], [1.40, 1.62, 'N2'], [1.62, 2.05, 'N3'],
        [2.05, 2.26, 'N2'], [2.26, 2.52, 'REM'], [2.52, 2.92, 'N2'], [2.92, 3.12, 'N3'],
        [3.12, 3.32, 'N2'], [3.32, 3.74, 'REM'], [3.74, 4.20, 'N2'], [4.20, 4.34, 'N1'],
        [4.34, 4.42, '깸'], [4.42, 4.84, 'N2'], [4.84, 5.38, 'REM'], [5.38, 5.86, 'N2'],
        [5.86, 6.48, 'REM'], [6.48, 6.76, 'N2'], [6.76, 7.44, 'REM'], [7.44, 7.56, '깸'],
    ];

    for (const [a, b] of [[0.5, 1], [1.5, 2], [2.5, 3], [3.5, 4], [4.5, 5]].map(p => p)) {
        g.push(ln([[f.X(0), f.Y(a)], [f.X(7.6), f.Y(a)]], { stroke: COL.grid, sw: 1 }));
        void b;
    }
    for (const [name, lv] of Object.entries(LV)) {
        g.push(txt(f.X(0) - 10, f.Y(lv) + 4, name, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }

    /* REM 구간을 세로 띠로 먼저 깔아 눈에 띄게 한다. */
    for (const [a, b, st] of seq) {
        if (st !== 'REM') continue;
        g.push(`<rect x="${f.X(a)}" y="${f.Y(5.6)}" width="${r2(f.X(b) - f.X(a))}" height="${r2(f.Y(0.5) - f.Y(5.6))}" fill="${COL.s2}" fill-opacity="0.14"/>`);
    }

    const pts = [];
    let prev = null;
    for (const [a, b, st] of seq) {
        const lv = LV[st];
        if (prev !== null) pts.push([a, prev]);
        pts.push([a, lv], [b, lv]);
        prev = lv;
    }
    g.push(f.line(pts, { cls: 's1' }));
    for (const [a, b, st] of seq) {
        if (st !== 'REM') continue;
        g.push(f.line([[a, LV.REM], [b, LV.REM]], { cls: 's2' }));
    }

    for (const t of [0, 1, 2, 3, 4, 5, 6, 7]) {
        g.push(ln([[f.X(t), f.Y(0.5)], [f.X(t), f.Y(0.5) + 5]], { stroke: COL.ink2, sw: 1 }));
        g.push(txt(f.X(t), f.Y(0.5) + 20, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(px(f.X(0.12), 52, f.X(1.40), 52, { cls: '', marker: 'ark', width: 1.4 }));
    g.push(px(f.X(1.40), 52, f.X(0.12), 52, { cls: '', marker: 'ark', width: 1.4 }));
    g.push(txt(f.X(0.76), 42, '한 주기', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const notes = [
        ['· 가로축은 잠든 뒤 지난 시간이다. 앞쪽 주기에 깊은 잠(N3)이 몰려 있고 뒤로 갈수록 사라진다', 'ink bold'],
        ['· 주황으로 표시한 REM 은 뒤로 갈수록 길어진다', 'ink bold'],
        ['· 밤중에 잠깐 깨는 일은 정상이며 대개 기억되지 않는다', 'ink2'],
        ['· 주기의 길이와 개수는 사람과 밤에 따라 다르다. 이 그림은 전형적인 모양을 보이는 도식이다', 'ink2'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(70, 322 + i * 20, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-b-hypnogram',
        svg: svg({ width: W, height: H, title: '하룻밤의 수면 단계', desc: '깊은 잠이 앞쪽에 몰리고 REM 이 뒤로 갈수록 길어진다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-b-two-process — 잠을 정하는 두 가지가 따로 있다. (14장)
 *
 * 무엇을 읽어야 하나: 곡선 하나(깨어 있는 동안 쌓이는 압력)와 위아래로
 * 출렁이는 두 문턱(하루 주기)이 <b>서로 다른 것</b>이라는 점. 그래서
 * ‘얼마나 오래 깨어 있었나’ 와 ‘지금이 하루 중 언제인가’ 가 각각 따로 잠을 정한다.
 * 곡선은 도식이며 세로축에 눈금이 없다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 41], yRange: [0, 1], box: { x: 84, y: 66, w: 660, h: 220 } });

    const wave = t => 0.085 * Math.sin((2 * Math.PI * (t - 5)) / 24);
    const hi = t => 0.76 + wave(t);
    const lo = t => 0.30 + wave(t);
    const S = t => {
        if (t <= 16) return 0.884 - 0.604 * Math.exp(-t / 9);
        if (t <= 24) return 0.1675 + 0.6145 * Math.exp(-(t - 16) / 3.2);
        return 0.884 - 0.666 * Math.exp(-(t - 24) / 9);
    };

    /* 잠든 구간을 띠로. */
    g.push(`<rect x="${f.X(16)}" y="${f.Y(1)}" width="${r2(f.X(24) - f.X(16))}" height="${r2(f.Y(0) - f.Y(1))}" fill="${COL.ink2}" fill-opacity="0.08"/>`);
    g.push(txt(f.X(20), f.Y(1) - 8, '자는 동안', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(f.curve(hi, { cls: 's3', dash: '6 4' }));
    g.push(f.curve(lo, { cls: 's3', dash: '6 4' }));
    g.push(f.curve(S, { from: 0, to: 16, cls: 's1', steps: 200 }));
    g.push(f.curve(S, { from: 16, to: 24, cls: 's1', steps: 200 }));
    g.push(f.curve(S, { from: 24, to: 41, cls: 's1', steps: 200 }));

    g.push(f.dot([16, S(16)], { cls: 'f1', r: 5 }));
    g.push(f.dot([24, S(24)], { cls: 'f1', r: 5 }));
    g.push(txt(f.X(16) - 10, f.Y(S(16)) - 14, '여기서 잠든다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(24) + 10, f.Y(S(24)) + 26, '여기서 깬다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(5.4), f.Y(0.93), '깨어 있는 동안 쌓이는 압력', { cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([7.5, 0.90], [7.5, 0.66]));
    g.push(txt(f.X(28), f.Y(0.92), '하루 주기가 정하는 위 문턱', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.guide([28, 0.89], [28, 0.775]));
    g.push(txt(f.X(33), f.Y(0.14), '아래 문턱', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.guide([33, 0.17], [33, 0.355]));

    for (const [t, lab] of [[0, '07시'], [8, '15시'], [16, '23시'], [24, '07시'], [32, '15시'], [40, '23시']]) {
        g.push(ln([[f.X(t), f.Y(0)], [f.X(t), f.Y(0) + 5]], { stroke: COL.ink2, sw: 1 }));
        g.push(txt(f.X(t), f.Y(0) + 20, lab, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    const notes = [
        ['· 파란 곡선은 깨어 있는 시간이 길수록 올라가고 자는 동안 내려간다. ‘얼마나 오래 깨어 있었나’ 다', 'ink bold'],
        ['· 초록 점선 두 개는 하루 주기다. 시간이 지나기만 해도 위아래로 출렁인다. ‘지금이 하루 중 언제인가’ 다', 'ink bold'],
        ['· 둘이 따로 움직이므로, 같은 시간을 깨어 있어도 하루 중 언제인가에 따라 졸림이 다르다', 'ink2'],
        ['· 이 그림은 잠을 정하는 두 갈래를 보이는 도식이며, 잠의 기능에 대해서는 아무 말도 하지 않는다', 'ink2'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(56, 320 + i * 20, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-b-two-process',
        svg: svg({ width: W, height: H, title: '잠을 정하는 두 갈래', desc: '깨어 있는 시간이 쌓는 압력과 하루 주기가 따로 움직인다', body: g.join('') }),
    };
})());

export default figures;
