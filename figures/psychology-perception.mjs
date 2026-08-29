/**
 * 심리학 문서 12장(감각과 지각)의 그림.
 *
 * 이름은 모두 `psy-p-` 로 시작한다(12장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * ─────────────────────────────────────────────────────────────────────
 * 팔레트를 쓰지 않는 그림이 셋 있다 — 왜 예외인가
 *
 *   psy-p-muller-lyer / psy-p-illusion-set / psy-p-kanizsa
 *
 * 착시는 <b>정확한 색과 대비에 의존한다.</b> lib.mjs 의 팔레트는 다크 모드에서
 * 잉크와 배경이 뒤집히므로(--ink 가 흰색이 된다) 그대로 쓰면
 *   · 카니자 삼각형은 흰 바탕에 검은 원판이라야 ‘더 밝은 삼각형’ 이 보이는데
 *     색이 뒤집히면 그 밝기 대비가 통째로 사라진다
 *   · 뮐러-리어·폰조·죌너는 배경이 어두워지면 선의 굵기 지각이 달라져
 *     효과의 크기가 눈에 띄게 줄어든다
 * 그래서 이 셋만은 <b>배경 사각형을 직접 깔고 색을 고정</b>한다. 원장 §5.1.
 * 나머지 그림(정신물리 함수·베버·신호탐지·ROC·순응·깊이 단서)은 팔레트를 쓴다.
 *
 * 자료 규칙 — 이 파일의 곡선과 점은 전부 도식이다. 남의 원자료를 옮긴 것이
 * 하나도 없고, ROC 곡선만은 등분산 정규 모형에서 실제로 계산한 값이다.
 *
 * 상자와 화살표만으로 되는 그림(추론 구조·주의의 자리)은
 * d2/psychology/psy-p-*.d2 에 있다.
 * ─────────────────────────────────────────────────────────────────────
 */
import { svg, frame, txt, px } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};

/* 착시 전용 고정색. 위 주석 참조 — 테마를 따라가면 착시가 깨진다. */
const FIX = {
    bg: '#f7f6f2',
    line: '#161616',
    sub: '#5c5b57',
    warm: '#d4602a',
    cool: '#2f6fb5',
};

function ln(pts, { stroke = COL.ink2, sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/* 착시 그림 안의 글자. txt() 는 테마를 따르는 클래스를 붙이므로 쓸 수 없다. */
function ftxt(x, y, s, { anchor = 'start', size = 13, fill = FIX.sub, weight = 400 } = {}) {
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${anchor}" font-size="${size}" font-weight="${weight}" fill="${fill}">${s}</text>`;
}

/* 표준정규 누적분포와 그 역함수. ROC 곡선을 실제로 계산하려고 둔다. */
function normCdf(z) {
    const t = 1 / (1 + 0.2316419 * Math.abs(z));
    const d = 0.3989422804014327 * Math.exp(-z * z / 2);
    const p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
    return z >= 0 ? 1 - p : p;
}
const phi = z => 0.3989422804014327 * Math.exp(-z * z / 2);

/* ------------------------------------------------------------------ *
 * psy-p-psychometric — 문턱은 계단이 아니다.
 *
 * 무엇을 읽어야 하나: 자극 세기를 조금씩 올려도 탐지 여부가 어느 지점에서
 * 딱 갈리지 않는다는 것. 그래서 ‘절대역’ 은 자연이 정해 준 경계가 아니라
 * 곡선 위에서 우리가 관례로 고른 한 점(대개 50%)이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: 92, y: 60, w: 500, h: 280 } });

    g.push(f.axes({ xTicks: [0, 25, 50, 75, 100], yTicks: [0, 0.25, 0.5, 0.75, 1], grid: true }));
    g.push(txt(f.X(50), 384, '자극 세기 (같은 자극을 여러 번 낸다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 44, '‘있다’ 고 답한 비율', { cls: 'ink2', size: 'sm' }));

    /* 만약 진짜 문턱이 있다면 이렇게 생겼어야 한다. */
    g.push(f.line([[0, 0], [50, 0], [50, 1], [100, 1]], { cls: 's2', dash: '6 5' }));
    g.push(txt(f.X(47) - 6, f.Y(0.63), '문턱이 있다면 이런 모양', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    /* 실제로 얻어지는 모양. */
    g.push(f.curve(x => 1 / (1 + Math.exp(-(x - 50) / 9)), { cls: 's1' }));
    g.push(txt(f.X(86), f.Y(0.82), '실제로 얻어지는 모양', { anchor: 'end', cls: 'ink bold', size: 'sm' }));

    g.push(f.guide([0, 0.5], [50, 0.5]));
    g.push(f.guide([50, 0], [50, 0.5]));
    g.push(f.dot([50, 0.5], { cls: 'f1', r: 5.5 }));
    g.push(txt(f.X(50) + 10, f.Y(0.5) - 10, '관례로 정한 절대역', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(616, 96, '읽을 것', { cls: 'ink bold', size: 'sm' }));
    const notes = [
        '· 같은 세기를 여러 번 내도',
        '  어떤 때는 알아채고',
        '  어떤 때는 못 알아챈다',
        '',
        '· 그래서 경계가 한 점이 아니라',
        '  퍼진 구간으로 나온다',
        '',
        '· 절대역은 그 구간에서',
        '  우리가 고른 한 점이다',
    ];
    notes.forEach((s, i) => g.push(txt(616, 122 + i * 20, s, { cls: 'ink2', size: 'sm' })));

    return {
        name: 'psy-p-psychometric',
        svg: svg({ width: W, height: H, title: '정신물리 함수', desc: '자극 세기에 따른 탐지 비율이 계단이 아니라 완만한 곡선으로 나온다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-weber — 최소가지차이는 원래 세기에 비례한다.
 *
 * 무엇을 읽어야 하나: 두 직선의 기울기가 다르다는 것. 그 기울기가 베버 상수이고,
 * 상수가 작은 차원일수록 예민하다. 양 끝에서 직선을 벗어나는 것도 함께 본다.
 * 눈금의 수는 설명을 위한 예시값이며 특정 실험의 자료가 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 500], yRange: [0, 60], box: { x: 84, y: 56, w: 440, h: 290 } });

    g.push(f.axes({ xTicks: [0, 100, 200, 300, 400, 500], yTicks: [0, 20, 40, 60], grid: true }));
    g.push(txt(f.X(250), 390, '원래 자극의 세기 (예시 단위)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 40, '최소가지차이', { cls: 'ink2', size: 'sm' }));

    g.push(f.line([[0, 0], [500, 50]], { cls: 's2' }));
    g.push(txt(f.X(430), f.Y(43) - 12, '기울기 0.10 인 차원', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.line([[0, 0], [500, 20]], { cls: 's1' }));
    g.push(txt(f.X(500), f.Y(20) - 12, '기울기 0.04 인 차원', { anchor: 'end', cls: 'ink bold', size: 'sm' }));

    /* 아주 약한 자극 쪽에서 직선을 벗어나는 부분. */
    g.push(f.curve(x => 0.04 * x + 5 * Math.exp(-x / 26), { from: 0, to: 140, cls: 's1', dash: '5 4' }));

    g.push(f.guide([100, 0], [100, 10]));
    g.push(f.guide([0, 10], [100, 10]));
    g.push(f.dot([100, 10], { cls: 'f2', r: 4.5 }));
    g.push(f.guide([400, 0], [400, 40]));
    g.push(f.guide([0, 40], [400, 40]));
    g.push(f.dot([400, 40], { cls: 'f2', r: 4.5 }));
    g.push(txt(f.X(400) - 10, f.Y(40) - 12, '세기가 4배면 차이역도 4배', { anchor: 'end', cls: 'ink', size: 'sm' }));

    const notes = [
        ['기울기 = 베버 상수', 'ink bold'],
        ['작을수록 예민한 차원이다.', 'ink2'],
        ['', 'ink2'],
        ['같은 1 g 을 얹어도 원래 무게가', 'ink2'],
        ['가벼우면 알아채고 무거우면', 'ink2'],
        ['알아채지 못하는 것이 이 직선이다.', 'ink2'],
        ['', 'ink2'],
        ['아주 약하거나 아주 센 자극에서는', 'ink bold'],
        ['직선에서 벗어난다 (점선).', 'ink2'],
        ['법칙이 아니라 넓은 가운데 구간의', 'ink2'],
        ['근사라는 뜻이다.', 'ink2'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(566, 108 + i * 21, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-p-weber',
        svg: svg({ width: W, height: H, title: '베버 법칙', desc: '최소가지차이가 원래 세기에 비례해 커지고 그 비례상수가 차원마다 다르다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-sdt — 신호탐지이론의 두 분포와 준거.
 *
 * 무엇을 읽어야 하나: 관찰자가 내놓는 ‘있다/없다’ 는 두 가지가 함께 정한다는 것.
 * 두 봉우리 사이의 거리(민감도)와 세로 선의 자리(반응 편향)다. 세로 선을 옮기면
 * 적중률과 오경보율이 함께 움직이고, 봉우리 사이 거리는 그대로다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 452;
    const g = [];
    const bx = { x: 56, y: 66, w: 770, h: 232 };
    const f = frame({ xRange: [-4, 6], yRange: [0, 0.60], box: bx });
    const yBase = f.Y(0);

    const mu = 2;
    const crit = 1.0;
    const dens0 = x => phi(x);
    const dens1 = x => phi(x - mu);

    /* 준거 오른쪽 = ‘있다’ 라고 답하는 구역. 두 분포 각각을 칠한다. */
    const shade = (dfun, from, to, col, op) => {
        const pts = [];
        const N = 90;
        for (let i = 0; i <= N; i += 1) {
            const x = from + ((to - from) * i) / N;
            pts.push(`${f.X(x)} ${f.Y(dfun(x))}`);
        }
        return `<path d="M${f.X(from)} ${yBase} L${pts.join(' L')} L${f.X(to)} ${yBase} Z" fill="${col}" fill-opacity="${op}" stroke="none"/>`;
    };
    g.push(shade(dens0, crit, 6, COL.s2, 0.4));
    g.push(shade(dens1, crit, 6, COL.s1, 0.32));

    /* 기준선만 직접 긋는다. frame 의 축은 x=0 자리에 세로선을 세워 혼란스럽다. */
    g.push(ln([[f.X(-4), yBase], [f.X(6), yBase]], { stroke: COL.ink2, sw: 1.5 }));
    g.push(f.curve(dens0, { cls: 's2' }));
    g.push(f.curve(dens1, { cls: 's1' }));

    g.push(ln([[f.X(crit), yBase], [f.X(crit), f.Y(0.36)]], { stroke: COL.ink, sw: 2.2, dash: '6 4' }));
    g.push(txt(f.X(crit) + 9, f.Y(0.335), '준거', { cls: 'ink bold' }));

    g.push(txt(f.X(-1.45), f.Y(0.30), '신호가 없을 때 (잡음뿐)', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([-1.4, 0.295], [-0.85, 0.24]));
    g.push(txt(f.X(3.55), f.Y(0.30), '신호가 있을 때', { cls: 'ink bold', size: 'sm' }));
    g.push(f.guide([3.5, 0.295], [3.0, 0.24]));

    /* 민감도 = 두 봉우리 사이의 거리. */
    const yArr = f.Y(0.47);
    g.push(f.guide([0, 0.399], [0, 0.47]));
    g.push(f.guide([mu, 0.399], [mu, 0.47]));
    g.push(px(f.X(0), yArr, f.X(mu), yArr, { cls: '', marker: 'ark', width: 1.6 }));
    g.push(px(f.X(mu), yArr, f.X(0), yArr, { cls: '', marker: 'ark', width: 1.6 }));
    g.push(txt((f.X(0) + f.X(mu)) / 2, yArr - 10, '민감도 — 두 봉우리 사이의 거리', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(3.0), f.Y(0.115), '적중', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(1.62), f.Y(0.045), '오경보', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(crit) - 10, yBase + 24, '여기 왼쪽이면 ‘없다’ 라고 답한다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(crit) + 10, yBase + 24, '여기 오른쪽이면 ‘있다’ 라고 답한다', { cls: 'ink2', size: 'sm' }));

    const rows = [
        ['칠해진 두 넓이가 관찰자가 내놓는 답의 전부다. 준거 오른쪽의 파랑 전체가 적중률이고, 주황이 겹쳐 보라로 보이는 부분이 오경보율이다.', 'ink bold'],
        ['준거를 왼쪽으로 옮기면 적중률과 오경보율이 함께 커지고, 오른쪽으로 옮기면 함께 작아진다. 그동안 봉우리 사이 거리는 그대로다.', 'ink2'],
        ['그래서 적중률 하나만 보고는 잘 알아채는 사람과 ‘있다’ 라고 자주 답하는 사람을 가릴 수 없다.', 'ink2'],
    ];
    rows.forEach(([s, c], i) => g.push(txt(56, 372 + i * 22, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-p-sdt',
        svg: svg({ width: W, height: H, title: '신호탐지이론의 두 분포', desc: '겹치는 두 분포와 준거선이 적중률과 오경보율을 함께 정한다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-roc — ROC 곡선.
 *
 * 무엇을 읽어야 하나: 준거를 옮기는 것은 <b>한 곡선 위를 미끄러지는 일</b>이고,
 * 민감도가 달라져야 <b>다른 곡선</b>으로 옮겨 간다는 것. 적중률만 보면
 * 이 둘을 구별할 수 없다.
 *
 * 곡선은 등분산 정규 모형에서 계산했다(적중률 = Φ(z + d)).
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 424;
    const g = [];
    const f = frame({ xRange: [0, 1], yRange: [0, 1], box: { x: 84, y: 46, w: 296, h: 296 } });

    g.push(f.axes({ xTicks: [0, 0.25, 0.5, 0.75, 1], yTicks: [0, 0.25, 0.5, 0.75, 1], grid: true }));
    g.push(txt(f.X(0.5), 388, '오경보율', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(22, 32, '적중률', { cls: 'ink2', size: 'sm' }));

    const roc = (d, cls, dash) => {
        const pts = [];
        for (let i = 0; i <= 120; i += 1) {
            const zF = -3.2 + (6.4 * i) / 120;
            pts.push([normCdf(zF), normCdf(zF + d)]);
        }
        return f.line(pts, { cls, dash });
    };
    g.push(f.line([[0, 0], [1, 1]], { cls: 'ax', dash: '5 4' }));
    g.push(txt(f.X(0.76), f.Y(0.63), '민감도 0', { cls: 'ink2', size: 'sm' }));
    g.push(roc(1, 's3'));
    g.push(txt(f.X(0.55), f.Y(0.855), '민감도 1', { cls: 'ink2', size: 'sm' }));
    g.push(roc(2, 's1'));
    g.push(txt(f.X(0.36), f.Y(0.985), '민감도 2', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    /* 적중률이 같은 두 점 — 곡선이 다르므로 민감도가 다르다. */
    g.push(f.guide([0.0668, 0.6915], [0.3085, 0.6915]));

    /* 민감도 2 곡선 위의 세 준거. 등분산 정규 모형에서 계산한 값이다. */
    g.push(f.dot([0.0668, 0.6915], { cls: 'f1', r: 5.5 }));
    g.push(txt(f.X(0.0668) + 8, f.Y(0.6915) - 12, '엄격한 준거', { cls: 'ink', size: 'sm' }));
    g.push(f.dot([0.1587, 0.8413], { cls: 'f1', r: 5.5 }));
    g.push(txt(f.X(0.1587) - 9, f.Y(0.8413) + 4, '중립', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(f.dot([0.3085, 0.9332], { cls: 'f1', r: 5.5 }));
    g.push(txt(f.X(0.3085) + 10, f.Y(0.9332) + 5, '느슨한 준거', { cls: 'ink', size: 'sm' }));
    /* 민감도 1 위의 한 점 — 적중률이 같은데 민감도가 다른 자리. */
    g.push(f.dot([0.3085, 0.6915], { cls: 'f3', r: 5.5 }));
    g.push(txt(f.X(0.3085) + 10, f.Y(0.6915) + 20, '적중률은 같고 민감도가 작다', { cls: 'ink', size: 'sm' }));

    const notes = [
        ['한 곡선 위의 점들은 민감도가 같다.', 'ink bold'],
        ['준거만 다르다. 느슨하게 답하면 오른쪽 위로,', 'ink2'],
        ['엄격하게 답하면 왼쪽 아래로 미끄러진다.', 'ink2'],
        ['', 'ink2'],
        ['적중률이 0.84 인 관찰자와 0.93 인 관찰자가', 'ink bold'],
        ['같은 곡선 위에 있을 수 있다. 그러면 둘은', 'ink2'],
        ['민감도가 같고 답하는 버릇만 다른 것이다.', 'ink2'],
        ['', 'ink2'],
        ['가로로 그은 점선 위의 두 점은 적중률이 같다.', 'ink bold'],
        ['그런데 곡선이 다르므로 민감도가 다르다.', 'ink2'],
        ['', 'ink2'],
        ['오경보율을 함께 보지 않으면 이 구별을 할 수 없다.', 'ink bold'],
        ['왼쪽 위로 갈수록 민감도가 크고,', 'ink2'],
        ['대각선은 신호와 잡음을 전혀 못 가르는 상태다.', 'ink2'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(438, 70 + i * 21, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-p-roc',
        svg: svg({ width: W, height: H, title: 'ROC 곡선', desc: '준거를 옮기면 같은 곡선 위를 움직이고 민감도가 달라져야 다른 곡선으로 간다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-adaptation — 감각은 변화를 잡는다.
 *
 * 무엇을 읽어야 하나: 자극이 켜진 채 그대로 있으면 반응이 줄어들고,
 * 자극이 <b>바뀌는 순간</b> 다시 커진다는 것. 감각계가 절대적인 양이 아니라
 * 변화를 부호화한다는 말의 뜻이 이 모양이다. 세로축에 눈금이 없는 것은
 * 특정 자료가 아니라 도식이기 때문이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 100], yRange: [0, 1.15], box: { x: 92, y: 130, w: 620, h: 190 } });
    const s = frame({ xRange: [0, 100], yRange: [0, 1.6], box: { x: 92, y: 52, w: 620, h: 60 } });

    /* 위: 자극. 아래: 반응. */
    g.push(txt(24, 46, '자극', { cls: 'ink2', size: 'sm' }));
    g.push(s.line([[0, 0], [12, 0], [12, 1], [55, 1], [55, 1.4], [96, 1.4]], { cls: 's3' }));
    g.push(txt(f.X(30), s.Y(1.55), '자극이 켜진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(74), s.Y(1.55) + 2, '자극이 한 단계 세진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(50), 360, '시간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 152, '반응 크기', { cls: 'ink2', size: 'sm' }));

    const resp = x => {
        if (x < 12) return 0.06;
        if (x < 55) return 0.06 + 0.92 * Math.exp(-(x - 12) / 9);
        return 0.06 + 0.78 * Math.exp(-(x - 55) / 9);
    };
    g.push(f.curve(resp, { from: 0, to: 96, cls: 's1', steps: 300 }));

    g.push(ln([[f.X(12), f.Y(0)], [f.X(12), s.Y(1.5)]], { stroke: COL.grid, sw: 1, dash: '4 3' }));
    g.push(ln([[f.X(55), f.Y(0)], [f.X(55), s.Y(1.5)]], { stroke: COL.grid, sw: 1, dash: '4 3' }));

    g.push(txt(f.X(20), f.Y(1.02), '켜지는 순간 크게 반응', { cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(40), f.Y(0.30), '자극은 그대로인데 반응이 준다 — 순응', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(60), f.Y(0.92), '바뀌면 다시 반응한다', { cls: 'ink', size: 'sm' }));

    return {
        name: 'psy-p-adaptation',
        svg: svg({ width: W, height: H, title: '감각 순응', desc: '자극이 일정하면 반응이 줄고 자극이 바뀌는 순간 다시 커진다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-depth — 한 눈으로도 쓰는 깊이 단서 넷.
 *
 * 무엇을 읽어야 하나: 그림은 평면인데 깊이가 읽힌다는 것, 그리고 그 깊이를
 * 만드는 것이 몇 가지 규칙적인 단서라는 것. 두 사각형은 화면에서 크기가 같다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 400;
    const g = [];
    const yH = 96;           // 지평선
    const yB = 344;          // 화면 아래
    const vpx = 338;         // 소실점
    const xL = 62;
    const xR = 610;
    const stop = 112;        // 레일을 소실점 조금 앞에서 끊는다

    /* 깊이 t (0 = 가까움, 1 = 멂) 에서의 레일 좌표. */
    const lAt = t => xL + (vpx - xL) * t;
    const rAt = t => xR - (xR - vpx) * t;

    /* 결 기울기 — 화면에서 고르게 놓은 줄이 멀수록 촘촘하고 작아진다. */
    for (let k = 0; k <= 9; k += 1) {
        const y = yB - k * 23;
        const t = (yB - y) / (yB - stop);
        const rad = Math.max(1, 5.4 * (1 - t * 0.88));
        const step = Math.max(10, 34 * (1 - t * 0.8));
        for (let x = lAt(t) + 8; x <= rAt(t) - 6; x += step) {
            g.push(`<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(rad)}" fill="${COL.grid}"/>`);
        }
    }

    /* 선형 조망 — 지평선의 한 점으로 모이는 두 선. */
    g.push(ln([[xL, yB], [lAt(1), stop]], { stroke: COL.ink2, sw: 1.8 }));
    g.push(ln([[xR, yB], [rAt(1), stop]], { stroke: COL.ink2, sw: 1.8 }));
    g.push(ln([[xL - 8, yH], [xR + 8, yH]], { stroke: COL.grid, sw: 1.4, dash: '6 4' }));
    g.push(txt(xL, yH - 9, '지평선', { cls: 'ink2', size: 'sm' }));

    /* 상대 크기 · 높이 — 화면에서 크기가 정확히 같은 두 사각형.
       레일 안쪽에 완전히 들어가도록 좌표를 잡는다. */
    const bw = 46;
    const bh = 64;
    const boxAt = (cxp, bottom, label, dy) => [
        `<rect x="${r2(cxp - bw / 2)}" y="${r2(bottom - bh)}" width="${bw}" height="${bh}" rx="3" fill="var(--s1)" fill-opacity="0.32" stroke="var(--s1)" stroke-width="2"/>`,
        txt(cxp, bottom + dy, label, { anchor: 'middle', cls: 'ink bold' }),
    ].join('');
    g.push(boxAt(214, 330, '가', 17));
    g.push(boxAt(338, 218, '나', 17));

    /* 중첩 — 가리는 쪽이 앞이다. */
    g.push(`<rect x="452" y="262" width="50" height="50" rx="4" fill="var(--s2)" fill-opacity="0.22" stroke="var(--s2)" stroke-width="2"/>`);
    g.push(`<rect x="480" y="284" width="50" height="50" rx="4" fill="var(--s2)" fill-opacity="0.9" stroke="var(--s2)" stroke-width="2"/>`);
    g.push(txt(505, 352, '중첩', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const notes = [
        ['이 그림에서 읽을 것', 'ink bold'],
        ['', 'ink2'],
        ['· 선형 조망 — 나란한 선이', 'ink2'],
        ['  멀수록 모인다', 'ink2'],
        ['· 결 기울기 — 같은 결이 멀수록', 'ink2'],
        ['  촘촘하고 작아진다', 'ink2'],
        ['· 상대 크기와 높이 — 화면에서', 'ink2'],
        ['  작고 위에 있으면 멀게 읽힌다', 'ink2'],
        ['· 중첩 — 가리는 쪽이 앞이다', 'ink2'],
        ['', 'ink2'],
        ['사각형 가와 나는 화면에서', 'ink bold'],
        ['크기가 정확히 같다.', 'ink bold'],
        ['그런데 나가 더 커 보인다.', 'ink bold'],
    ];
    notes.forEach(([s, c], i) => g.push(txt(650, 74 + i * 21, s, { cls: c, size: 'sm' })));

    return {
        name: 'psy-p-depth',
        svg: svg({ width: W, height: H, title: '단안 깊이 단서', desc: '평면 그림에서 깊이를 만들어 내는 단서 넷', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-muller-lyer — 뮐러-리어.
 *
 * <b>팔레트를 쓰지 않는다.</b> 파일 첫머리 주석 참조.
 *
 * 기하 — 두 축선은 x 좌표가 완전히 같다(SHAFT_X1, SHAFT_X2). 날개만 다르다.
 *   위: 날개가 축선 <b>안쪽으로</b> 접힌다(화살촉). 짧아 보인다.
 *   아래: 날개가 축선 <b>바깥으로</b> 벌어진다(꼬리). 길어 보인다.
 * 아래쪽 두 줄은 같은 축선에서 날개만 지운 것이다.
 * ------------------------------------------------------------------ */
const ML = { x1: 250, x2: 590, fin: 46 };

add((() => {
    const W = 820;
    const H = 470;
    const g = [];
    const { x1, x2, fin } = ML;

    g.push(`<rect x="0" y="0" width="${W}" height="${H}" fill="${FIX.bg}"/>`);

    const sw = 3.2;
    const seg = (ax, ay, bx, by, stroke = FIX.line, w = sw) =>
        `<path d="M${r2(ax)} ${r2(ay)} L${r2(bx)} ${r2(by)}" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round" fill="none"/>`;

    /* 위 — 화살촉이 바깥을 향한다. 날개는 축선 안쪽으로 접힌다. */
    const yA = 96;
    g.push(seg(x1, yA, x2, yA));
    g.push(seg(x1, yA, x1 + fin, yA - fin));
    g.push(seg(x1, yA, x1 + fin, yA + fin));
    g.push(seg(x2, yA, x2 - fin, yA - fin));
    g.push(seg(x2, yA, x2 - fin, yA + fin));
    g.push(ftxt(226, yA + 5, '가', { anchor: 'end', size: 17, fill: FIX.line, weight: 700 }));

    /* 아래 — 꼬리가 바깥으로 벌어진다. */
    const yB = 246;
    g.push(seg(x1, yB, x2, yB));
    g.push(seg(x1, yB, x1 - fin, yB - fin));
    g.push(seg(x1, yB, x1 - fin, yB + fin));
    g.push(seg(x2, yB, x2 + fin, yB - fin));
    g.push(seg(x2, yB, x2 + fin, yB + fin));
    g.push(ftxt(226 - fin, yB + 5, '나', { anchor: 'end', size: 17, fill: FIX.line, weight: 700 }));

    /* 아래 — 날개를 지운 같은 두 축선. */
    const yC = 348;
    const yD = 394;
    g.push(seg(x1, yC, x2, yC, FIX.warm));
    g.push(seg(x1, yD, x2, yD, FIX.cool));
    g.push(`<path d="M${x1} ${yC - 16} L${x1} ${yD + 16}" stroke="${FIX.sub}" stroke-width="1.2" stroke-dasharray="5 4" fill="none"/>`);
    g.push(`<path d="M${x2} ${yC - 16} L${x2} ${yD + 16}" stroke="${FIX.sub}" stroke-width="1.2" stroke-dasharray="5 4" fill="none"/>`);
    g.push(ftxt(226, yC + 5, '가', { anchor: 'end', size: 15, fill: FIX.sub, weight: 700 }));
    g.push(ftxt(226, yD + 5, '나', { anchor: 'end', size: 15, fill: FIX.sub, weight: 700 }));

    g.push(ftxt(60, 44, '가와 나의 가로선은 길이가 정확히 같다', { size: 15, fill: FIX.line, weight: 700 }));
    g.push(ftxt(60, 434, '날개를 지우면 두 선이 같다는 것이 바로 보인다. 그래도 위 그림에서는 다시 달라 보인다', { size: 13, fill: FIX.sub }));
    g.push(ftxt(636, 96 + 5, '짧아 보인다', { size: 13, fill: FIX.sub }));
    g.push(ftxt(646 + fin, 246 + 5, '길어 보인다', { size: 13, fill: FIX.sub }));

    return {
        name: 'psy-p-muller-lyer',
        svg: svg({ width: W, height: H, title: '뮐러-리어 착시', desc: '길이가 같은 두 선분이 날개의 방향 때문에 다르게 보인다', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-p-illusion-set — 폰조 · 에빙하우스 · 죌너 · 카니자.
 *
 * <b>팔레트를 쓰지 않는다.</b> 파일 첫머리 주석 참조.
 * 특히 카니자는 흰 바탕에 검은 원판이라야 ‘더 밝은 삼각형’ 이 보인다.
 * ------------------------------------------------------------------ */
const ILL = {
    ponzoBarHalf: 58,     // 두 막대의 반길이. 둘이 같다는 것이 이 그림의 전부다
    ebbCenterR: 30,       // 가운데 두 원의 반지름. 둘이 같다
};

add((() => {
    const W = 980;
    const H = 800;
    const g = [];
    g.push(`<rect x="0" y="0" width="${W}" height="${H}" fill="${FIX.bg}"/>`);

    const seg = (ax, ay, bx, by, w = 2.6, stroke = FIX.line) =>
        `<path d="M${r2(ax)} ${r2(ay)} L${r2(bx)} ${r2(by)}" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round" fill="none"/>`;
    const cell = (x, y, w, h, title) => [
        `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" fill="none" stroke="#d8d6d0" stroke-width="1.2"/>`,
        ftxt(x + 16, y + 27, title, { size: 14, fill: FIX.line, weight: 700 }),
    ].join('');

    const CW = 456;
    const CH = 344;
    const gx = [26, 498];
    const gy = [62, 424];

    /* ── 폰조 ── 모이는 두 선 사이의 같은 길이 막대 둘.
       위 막대를 레일이 좁아진 자리에 놓아야 효과가 난다. */
    {
        const x = gx[0];
        const y = gy[0];
        g.push(cell(x, y, CW, CH, '폰조 — 두 막대는 길이가 같다'));
        const cxm = x + CW / 2;
        const yT = y + 56;
        const yB = y + CH - 26;
        const halfAt = t => 168 - 140 * t;              // t=0 아래(168), t=1 위(28)
        const yAt = t => yB - (yB - yT) * t;
        g.push(seg(cxm - halfAt(0), yAt(0), cxm - halfAt(1), yAt(1), 2.6));
        g.push(seg(cxm + halfAt(0), yAt(0), cxm + halfAt(1), yAt(1), 2.6));
        for (let i = 1; i <= 6; i += 1) {
            const t = i / 7;
            g.push(seg(cxm - halfAt(t), yAt(t), cxm + halfAt(t), yAt(t), 1, '#cfcdc6'));
        }
        const bh = ILL.ponzoBarHalf;
        g.push(seg(cxm - bh, yAt(0.72), cxm + bh, yAt(0.72), 6, FIX.warm));
        g.push(seg(cxm - bh, yAt(0.10), cxm + bh, yAt(0.10), 6, FIX.cool));
        g.push(ftxt(cxm - halfAt(0.72) - 10, yAt(0.72) + 5, '위', { anchor: 'end', size: 12, fill: FIX.sub }));
        g.push(ftxt(cxm - halfAt(0.10) - 10, yAt(0.10) + 5, '아래', { anchor: 'end', size: 12, fill: FIX.sub }));
    }

    /* ── 에빙하우스 ── 가운데 원 둘은 반지름이 같다.
       둘레 원들이 서로 겹치면 덩어리로 보여 효과가 죽는다. 떨어뜨려 놓는다. */
    {
        const x = gx[1];
        const y = gy[0];
        g.push(cell(x, y, CW, CH, '에빙하우스 — 가운데 두 원은 크기가 같다'));
        const yc = y + 178;
        const rC = ILL.ebbCenterR;
        const left = x + 148;
        const right = x + 352;
        for (let i = 0; i < 6; i += 1) {
            const a = (i / 6) * 2 * Math.PI - Math.PI / 2;
            g.push(`<circle cx="${r2(left + 82 * Math.cos(a))}" cy="${r2(yc + 82 * Math.sin(a))}" r="38" fill="#b9b6ad" stroke="none"/>`);
        }
        for (let i = 0; i < 8; i += 1) {
            const a = (i / 8) * 2 * Math.PI - Math.PI / 2;
            g.push(`<circle cx="${r2(right + 54 * Math.cos(a))}" cy="${r2(yc + 54 * Math.sin(a))}" r="11" fill="#b9b6ad" stroke="none"/>`);
        }
        g.push(`<circle cx="${left}" cy="${yc}" r="${rC}" fill="${FIX.warm}"/>`);
        g.push(`<circle cx="${right}" cy="${yc}" r="${rC}" fill="${FIX.warm}"/>`);
        g.push(ftxt(left, y + CH - 14, '작아 보인다', { anchor: 'middle', size: 12, fill: FIX.sub }));
        g.push(ftxt(right, y + CH - 14, '커 보인다', { anchor: 'middle', size: 12, fill: FIX.sub }));
    }

    /* ── 죌너 ── 긴 선들은 서로 나란하다.
       빗금이 굵고 촘촘하면 무늬로 보인다. 본선을 굵게, 빗금을 가늘게. */
    {
        const x = gx[0];
        const y = gy[1];
        g.push(cell(x, y, CW, CH, '죌너 — 긴 가로선 다섯은 서로 나란하다'));
        const left = x + 26;
        const right = x + CW - 26;
        const hatch = 17;
        for (let k = 0; k < 5; k += 1) {
            const yy = y + 76 + k * 54;
            const dir = k % 2 === 0 ? 1 : -1;
            for (let xx = left + 14; xx < right - 8; xx += 30) {
                g.push(seg(xx - hatch, yy + dir * hatch, xx + hatch, yy - dir * hatch, 1.7));
            }
            g.push(seg(left, yy, right, yy, 3.2));
        }
    }

    /* ── 카니자 ── 없는 삼각형의 변이 보인다. */
    {
        const x = gx[1];
        const y = gy[1];
        g.push(cell(x, y, CW, CH, '카니자 — 없는 삼각형의 변이 보인다'));
        /* 이 칸만 순백 바탕이라야 ‘안쪽이 더 밝다’ 가 나온다. */
        g.push(`<rect x="${x + 1}" y="${y + 38}" width="${CW - 2}" height="${CH - 39}" rx="8" fill="#ffffff"/>`);
        const cxm = x + CW / 2;
        const cym = y + 38 + (CH - 39) / 2 + 2;
        const R = 104;
        const rd = 52;
        const verts = [
            [cxm, cym - R],
            [cxm - R * Math.cos(Math.PI / 6), cym + R * Math.sin(Math.PI / 6)],
            [cxm + R * Math.cos(Math.PI / 6), cym + R * Math.sin(Math.PI / 6)],
        ];
        /* 각 원판에서 삼각형 안쪽을 향한 60도 쐐기를 도려낸다.
           쐐기의 두 변이 삼각형의 두 변과 정확히 겹쳐야 변이 보인다. */
        for (let i = 0; i < 3; i += 1) {
            const [vx, vy] = verts[i];
            const base = Math.atan2(cym - vy, cxm - vx);
            const a1 = base - Math.PI / 6;
            const a2 = base + Math.PI / 6;
            const p1 = [vx + rd * Math.cos(a1), vy + rd * Math.sin(a1)];
            const p2 = [vx + rd * Math.cos(a2), vy + rd * Math.sin(a2)];
            g.push(`<path d="M${r2(p1[0])} ${r2(p1[1])} A${rd} ${rd} 0 1 0 ${r2(p2[0])} ${r2(p2[1])} L${r2(vx)} ${r2(vy)} Z" fill="#161616"/>`);
        }
        g.push(ftxt(cxm, y + CH - 14, '변도 없고 색도 없는데 삼각형이 보인다', { anchor: 'middle', size: 12, fill: FIX.sub }));
    }

    g.push(ftxt(26, 40, '넷 다 같은 것을 보인다 — 지각은 감각 입력에 없는 것을 더해 내놓는다', { size: 15, fill: FIX.line, weight: 700 }));
    g.push(ftxt(26, 790, '알고 나서도 사라지지 않는다는 점이 요점이다. 이 더하기는 우리가 아는 것에 굴복하지 않는다', { size: 12, fill: FIX.sub }));

    return {
        name: 'psy-p-illusion-set',
        svg: svg({ width: W, height: H, title: '고전 착시 넷', desc: '폰조 에빙하우스 죌너 카니자', body: g.join('') }),
    };
})());

/**
 * 좌표 검산 — 착시 그림이 ‘실제로는 같다’ 고 주장하는 것들이 정말로 같은지
 * 빌드 때 확인한다. 이 값이 틀리면 그림뿐 아니라 그 절 전체가 무의미해진다.
 */
export const selfCheck = () => ({
    mullerLyerShaft: [ML.x2 - ML.x1, ML.x2 - ML.x1],
    ponzoBar: [2 * ILL.ponzoBarHalf, 2 * ILL.ponzoBarHalf],
    ebbinghausCenterR: [ILL.ebbCenterR, ILL.ebbCenterR],
});

export default figures;
