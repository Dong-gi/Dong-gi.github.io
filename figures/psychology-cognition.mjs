/**
 * 심리학 문서 10장(생각하기)과 11장(언어와 사고)의 그림.
 *
 * 이름은 모두 `psy-g-` 로 시작한다(10·11장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없고(그림이 <img> 로 들어가 MathJax 가 닿지 않는다),
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 쓰지 않는다.
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓰고, HTML 엔티티도 쓸 수 없다.
 *
 * 자료 규칙 — 남의 원자료를 옮긴 그림은 없다. 곡선과 막대는 여러 연구가 보고한
 * 방향만 옮긴 도식이라 세로축에 눈금을 달지 않았다. 예외가 하나 있는데
 * psy-g-icon-array 의 개수는 <b>본문 예제가 스스로 정한 값</b>이라 그대로 셀 수 있다.
 *
 * 상자와 화살표만으로 되는 그림(개념의 세 관점·문제 공간·자연빈도 나무·워프 판본)은
 * d2/psychology/psy-g-*.d2 에 있다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};

function box(x, y, w, h, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.4, rx = 5, dash } = {}) {
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
 * psy-g-typicality — 전형성이 판단 시간에 남기는 자국.
 *
 * 무엇을 읽어야 하나: 같은 범주의 구성원인데도 판단이 걸리는 시간이 다르다는 것.
 * 범주가 ‘들어가거나 안 들어가거나’ 의 두 값이면 이런 차이가 생길 이유가 없다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 380;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 1], box: { x: 108, y: 82, w: 480, h: 208 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));

    const pts = [];
    for (let x = 0; x <= 10; x += 0.25) pts.push([x, 0.9 - 0.062 * x]);
    g.push(f.line(pts, { cls: 's1' }));

    const marks = [
        [1.2, '참새'],
        [4.4, '오리'],
        [8.4, '펭귄'],
    ];
    for (const [x, name] of marks) {
        const y = 0.9 - 0.062 * x;
        g.push(f.dot([x, y], { cls: 'f2', r: 6 }));
        g.push(f.label([x, y], name, { dx: 0, dy: -14, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }

    g.push(txt(f.X(5), 318, '전형적이라고 평정된 정도 (오른쪽으로 갈수록 덜 전형적)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 62, '세로축 — ‘새인가’ 에 답하는 데 걸린 시간. 위가 빠르다 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(612, 96, 228, 140, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(628, 122, '왜 이것이 문제인가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(628, 146, '셋 다 새다. 정의를 만족하는지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(628, 164, '따지는 일이라면 걸리는 시간이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(628, 182, '이렇게 갈릴 이유가 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(628, 206, '범주 안에도 가운데와 가장자리가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(628, 224, '있다는 뜻이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 36, '범주 안에도 가운데와 가장자리가 있다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 356, '세로축에 눈금을 달지 않았다. 값이 아니라 내려가는 방향만 읽는 그림이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-g-typicality',
        svg: svg({ width: W, height: H, title: '전형성과 판단 시간', desc: '전형적인 사례일수록 범주 판단이 빠른 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-g-icon-array — 기저율을 눈으로 세는 그림.
 *
 * 무엇을 읽어야 하나: 경보가 울린 22칸 가운데 찾던 위조는 2칸뿐이라는 것.
 * 검사가 잘 맞아도 찾는 것이 드물면 경보의 대부분은 헛것이 된다.
 * 개수는 본문 예제가 정한 값이라 그대로 셀 수 있다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 530;
    const g = [];
    const cell = 14;
    const cols = 40;
    const rows = 25;
    const x0 = 96;
    const y0 = 86;

    g.push(`<defs><pattern id="psyGcell" width="${cell}" height="${cell}" patternUnits="userSpaceOnUse"><rect x="2" y="2" width="${cell - 4}" height="${cell - 4}" rx="2" fill="${COL.grid}"/></pattern></defs>`);
    g.push(`<rect x="${x0}" y="${y0}" width="${cols * cell}" height="${rows * cell}" fill="url(#psyGcell)"/>`);

    // 경보가 울린 칸 22개를 고정된 난수로 흩는다.
    const rand = rng(20260829);
    const chosen = new Set();
    while (chosen.size < 22) chosen.add(Math.floor(rand() * cols * rows));
    const list = [...chosen];
    const fake = list.slice(0, 2);
    const alarmOnly = list.slice(2);

    const at = i => [x0 + (i % cols) * cell, y0 + Math.floor(i / cols) * cell];
    for (const i of alarmOnly) {
        const [x, y] = at(i);
        g.push(`<rect x="${x + 1}" y="${y + 1}" width="${cell - 2}" height="${cell - 2}" rx="2" fill="${COL.s2}" fill-opacity="0.9"/>`);
    }
    for (const i of fake) {
        const [x, y] = at(i);
        g.push(`<rect x="${x + 1}" y="${y + 1}" width="${cell - 2}" height="${cell - 2}" rx="2" fill="${COL.s1}"/>`);
        g.push(`<rect x="${x - 2}" y="${y - 2}" width="${cell + 2}" height="${cell + 2}" rx="3" fill="none" stroke="${COL.ink}" stroke-width="2"/>`);
    }

    g.push(txt(W / 2, 38, '지폐 1,000장 — 경보가 울린 22장 가운데 진짜 위조는 2장', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 62, '작은 칸 하나가 지폐 한 장이다. 세로 25줄 × 가로 40칸 = 1,000장', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const ly = y0 + rows * cell + 34;
    g.push(`<rect x="${x0}" y="${ly - 10}" width="14" height="14" rx="2" fill="${COL.s1}"/>`);
    g.push(`<rect x="${x0 - 3}" y="${ly - 13}" width="20" height="20" rx="3" fill="none" stroke="${COL.ink}" stroke-width="2"/>`);
    g.push(txt(x0 + 30, ly + 2, '위조이고 경보도 울렸다 — 2장', { cls: 'ink', size: 'sm' }));
    g.push(`<rect x="${x0 + 260}" y="${ly - 10}" width="14" height="14" rx="2" fill="${COL.s2}" fill-opacity="0.9"/>`);
    g.push(txt(x0 + 290, ly + 2, '진짜인데 경보가 울렸다 — 20장', { cls: 'ink', size: 'sm' }));
    g.push(`<rect x="${x0 + 540}" y="${ly - 10}" width="14" height="14" rx="2" fill="${COL.grid}"/>`);
    g.push(txt(x0 + 570, ly + 2, '경보가 울리지 않았다 — 978장', { cls: 'ink', size: 'sm' }));

    g.push(txt(W / 2, ly + 34, '검사가 위조를 놓치지 않고 진짜를 2%만 잘못 걸러도, 경보의 열에 아홉은 진짜 지폐다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-g-icon-array',
        svg: svg({ width: W, height: H, title: '기저율을 눈으로 세기', desc: '1000칸 가운데 경보가 울린 22칸과 그중 진짜 위조인 2칸', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-g-anchoring — 먼저 본 수가 추정값을 끌어당긴다.
 *
 * 무엇을 읽어야 하나: 두 집단이 같은 것을 추정했는데 분포 전체가 옆으로
 * 밀렸다는 것. 몇몇 사람이 이상하게 답한 것이 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 380;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 1], box: { x: 104, y: 88, w: 540, h: 196 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));

    const bell = (mu, sd) => x => Math.exp(-((x - mu) ** 2) / (2 * sd * sd));
    g.push(f.curve(bell(3.4, 1.35), { cls: 's1' }));
    g.push(f.curve(bell(6.6, 1.35), { cls: 's2' }));

    g.push(f.label([3.4, 1], '작은 수를 먼저 본 집단', { dx: 0, dy: -12, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([6.6, 1], '큰 수를 먼저 본 집단', { dx: 0, dy: -12, anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(f.line([[3.4, 0], [3.4, 1]], { cls: 'ax', dash: '3 3' }));
    g.push(f.line([[6.6, 0], [6.6, 1]], { cls: 'ax', dash: '3 3' }));

    g.push(txt(f.X(5), 312, '참가자가 적어 낸 추정값', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(34, 80, '그렇게 답한 사람의 수', { cls: 'ink2', size: 'sm' }));

    g.push(box(96, 330, 668, 34, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(112, 351, '먼저 본 수는 답과 아무 관계가 없다고 참가자에게 알려 주어도 이 밀림이 남는다는 보고가 많다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 38, '먼저 본 수가 추정값 전체를 끌어당긴다 — 도식', { anchor: 'middle', cls: 'ink bold' }));

    return {
        name: 'psy-g-anchoring',
        svg: svg({ width: W, height: H, title: '기준점 효과', desc: '먼저 본 수에 따라 추정값 분포 전체가 옆으로 밀린 두 봉우리', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-g-framing — 같은 선택지, 다른 표현.
 *
 * 무엇을 읽어야 하나: 두 막대가 담은 선택지는 수치까지 똑같은데 고르는 비율이
 * 뒤집힌다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 380;
    const g = [];
    const barW = 380;
    const barH = 54;
    const x0 = 60;

    const stacked = (yTitle, title, sub, pSure) => {
        const y = yTitle + 30;
        g.push(txt(x0, yTitle, title, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(x0, yTitle + 20, sub, { cls: 'ink2', size: 'sm' }));
        g.push(`<rect x="${x0}" y="${y}" width="${r2(barW * pSure)}" height="${barH}" rx="3" fill="${COL.s1}" fill-opacity="0.88"/>`);
        g.push(`<rect x="${r2(x0 + barW * pSure)}" y="${y}" width="${r2(barW * (1 - pSure))}" height="${barH}" rx="3" fill="${COL.s2}" fill-opacity="0.88"/>`);
        g.push(box(x0, y, barW, barH, { stroke: COL.ink2, sw: 1.2, rx: 3 }));
    };

    stacked(106, '살릴 수 있는 수로 적었을 때', '‘200명이 산다’ 대 ‘3분의 1 확률로 600명이 산다’', 0.72);
    stacked(226, '잃는 수로 적었을 때', '‘400명이 죽는다’ 대 ‘3분의 2 확률로 600명이 죽는다’', 0.22);

    g.push(`<rect x="${x0}" y="326" width="16" height="12" rx="2" fill="${COL.s1}" fill-opacity="0.88"/>`);
    g.push(txt(x0 + 24, 337, '확실한 쪽을 골랐다', { cls: 'ink2', size: 'sm' }));
    g.push(`<rect x="${x0 + 190}" y="326" width="16" height="12" rx="2" fill="${COL.s2}" fill-opacity="0.88"/>`);
    g.push(txt(x0 + 214, 337, '확률에 거는 쪽을 골랐다', { cls: 'ink2', size: 'sm' }));

    g.push(box(486, 96, 366, 196, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(506, 124, '두 문제는 같은 문제다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(506, 152, '600명 가운데 200명이 사는 것과 400명이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(506, 172, '죽는 것은 같은 결과다. 확률 쪽도 마찬가지다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(506, 202, '달라진 것은 그 결과를 어느 쪽에서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(506, 222, '적었는가뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(506, 252, '막대 길이는 방향만 보이는 도식이고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(506, 272, '비율에는 눈금을 달지 않았다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 44, '같은 선택지를 어느 쪽에서 적는가가 선택을 바꾼다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 366, '두 줄이 담고 있는 선택지는 수치까지 같다. 다른 것은 그것을 적은 방향뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-g-framing',
        svg: svg({ width: W, height: H, title: '틀 효과', desc: '같은 선택지를 이득으로 적을 때와 손실로 적을 때 선택 비율이 뒤집히는 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-g-critical — 결정적 시기 논쟁의 두 모양.
 *
 * 무엇을 읽어야 하나: 같은 자료도 ‘절벽’ 으로 그릴 수 있고 ‘완만한 내리막’ 으로
 * 그릴 수 있다는 것. 논쟁의 자리가 바로 이 모양이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 40], yRange: [0, 1], box: { x: 100, y: 84, w: 520, h: 214 } });

    g.push(f.axes({ xTicks: [0, 5, 10, 15, 20, 25, 30, 35, 40], yTicks: [], grid: true }));

    const cliff = [];
    const gentle = [];
    for (let a = 0; a <= 40; a += 0.5) {
        cliff.push([a, a < 12 ? 0.93 : Math.max(0.16, 0.93 - (a - 12) * 0.075)]);
        gentle.push([a, Math.max(0.16, 0.93 - a * 0.019)]);
    }
    g.push(f.line(cliff, { cls: 's2' }));
    g.push(f.line(gentle, { cls: 's1', dash: '6 4' }));

    g.push(f.label([13.4, 0.83], '끊긴 시기가 있다는 그림', { dx: 10, dy: -8, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([31, 0.35], '처음부터 완만히 내려간다는 그림', { dx: -2, dy: -10, anchor: 'end', cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(20), 330, '그 언어를 처음 접한 나이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 62, '세로축 — 어른이 된 뒤의 문법 숙달도 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(644, 96, 216, 176, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(660, 122, '무엇이 다투어지나', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(660, 146, '늦게 접할수록 도달점이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 164, '낮다는 것은 다투어지지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 182, '않는다. 다투어지는 것은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 206, '꺾이는 자리가 있는가,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 224, '있다면 언제인가다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 248, '자료를 많이 모을수록', { cls: 'ink2', size: 'sm' }));
    g.push(txt(660, 266, '이 물음이 예리해진다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 38, '같은 자료, 두 가지 모양 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 374, '세로축에 눈금을 달지 않았다. 두 곡선은 실제 자료가 아니라 논쟁의 두 입장을 그린 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-g-critical',
        svg: svg({ width: W, height: H, title: '결정적 시기 논쟁의 두 모양', desc: '절벽처럼 꺾이는 곡선과 완만하게 내려가는 곡선의 대비', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-g-statlearn — 낱말 경계에서 이어짐이 약해진다.
 *
 * 무엇을 읽어야 하나: 소리의 흐름 안에 띄어쓰기는 없지만 ‘어떤 소리 다음에
 * 어떤 소리가 오는가’ 의 규칙성에는 경계가 남아 있다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 380;
    const g = [];
    const syl = ['비', '다', '쿠', '파', '도', '티', '고', '라', '부', '비', '다', '쿠'];
    const cw = 56;
    const x0 = 96;
    const y = 150;

    syl.forEach((s, i) => {
        g.push(box(x0 + i * cw, y - 26, cw - 6, 44, { stroke: COL.ink2, sw: 1.3 }));
        g.push(txt(x0 + i * cw + (cw - 6) / 2, y + 4, s, { anchor: 'middle', cls: 'ink' }));
    });

    // 이어짐의 세기를 잇는 선의 굵기로 보인다. 낱말 안은 굵게, 경계는 가늘게.
    for (let i = 0; i < syl.length - 1; i += 1) {
        const boundary = i === 2 || i === 5 || i === 8;
        const cx = x0 + i * cw + (cw - 6);
        const nx = x0 + (i + 1) * cw;
        const my = y - 44;
        g.push(`<path d="M${r2(cx - 8)} ${y - 26} C ${r2(cx)} ${my} ${r2(nx + 6)} ${my} ${r2(nx + 14)} ${y - 26}" fill="none" stroke="${boundary ? COL.s2 : COL.s1}" stroke-width="${boundary ? 1.4 : 4}"${boundary ? ' stroke-dasharray="4 3"' : ''}/>`);
    }

    g.push(txt(x0 + 1.5 * cw, y + 46, '낱말 하나', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 4.5 * cw, y + 46, '낱말 하나', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 7.5 * cw, y + 46, '낱말 하나', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(x0, y - 66, '굵은 선 — 앞 소리가 나오면 뒤 소리가 거의 늘 따라온다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 430, y - 66, '가는 점선 — 무엇이 따라올지 정해져 있지 않다', { cls: 'ink2', size: 'sm' }));

    g.push(box(x0, 232, 720, 86, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(x0 + 18, 258, '들리는 것은 끊김 없는 소리의 줄이다. 띄어쓰기도 쉼도 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 18, 280, '그런데 이어짐이 약해지는 자리가 낱말 경계와 겹친다. 그 자리를 찾아내는 것만으로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 18, 302, '낱말의 후보가 나온다 — 뜻을 하나도 모르는 채로.', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 44, '소리의 줄에서 낱말을 오려 내는 법', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 68, '실험에서 쓰는 소리는 뜻이 없는 인공 음절이다. 위의 글자는 그 자리를 대신한 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-g-statlearn',
        svg: svg({ width: W, height: H, title: '통계적 학습', desc: '음절의 이어짐이 약해지는 자리가 낱말 경계와 겹친다는 도식', body: g.join('') }),
    };
})());

export default figures;
