/**
 * 심리학 문서 17장(성격과 지능)의 그림.
 *
 * 이름은 모두 `psy-t-` 로 시작한다(17장 담당자에게 배정된 접두어).
 *
 * SVG 안에는 수식을 쓸 수 없고, lib 의 esc() 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 `~` 를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 자료 규칙 — 이 파일에는 남의 원자료를 옮긴 그림이 하나도 없다.
 *   · psy-t-aggregation 은 측정 이론의 공식을 그대로 그린 것이다(자료가 아니다).
 *   · psy-t-variance-split 의 숫자는 뜻을 보이려고 이 문서가 정한 보기다.
 *   · 나머지는 씨앗을 고정한 난수와 눈금 없는 곡선으로 만든 도식이다.
 * 문서의 캡션에도 그렇게 적었다.
 *
 * 상자와 화살표만으로 되는 그림(5요인 · CHC 구조 · 오용 지도)은
 * d2/psychology/psy-t-*.d2 에 있다.
 */
import { svg, frame, txt, px, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};

function box(x, y, w, h, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.4, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ln(pts, { stroke = COL.ink2, sw = 1.6, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

function gauss(rand) {
    const u = Math.max(rand(), 1e-9);
    const v = rand();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

const bell = (mu, sd) => x => Math.exp(-0.5 * Math.pow((x - mu) / sd, 2));

/* ------------------------------------------------------------------ *
 * psy-t-aggregation — 관찰을 여러 번 합치면 신뢰도가 오른다.
 *
 * 무엇을 읽어야 하나: 한 번의 관찰과 합친 관찰이 다른 이유가 자료가 아니라
 * 산수라는 것. 이 곡선은 측정 이론의 공식을 그대로 그린 것이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [1, 20], yRange: [0, 1], box: { x: 110, y: 60, w: 400, h: 260 } });

    g.push(f.axes({ xTicks: [1, 5, 10, 15, 20], yTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], grid: true }));
    g.push(txt(f.X(10.5), 364, '합친 관찰의 횟수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 46, '합친 점수의 신뢰도', { cls: 'ink2', size: 'sm' }));

    const sb = r1 => k => (k * r1) / (1 + (k - 1) * r1);
    g.push(f.curve(sb(0.30), { cls: 's1' }));
    g.push(f.curve(sb(0.15), { cls: 's2' }));
    g.push(f.curve(sb(0.06), { cls: 's3' }));

    g.push(f.dot([1, 0.30], { cls: 'f1', r: 4 }));
    g.push(f.dot([1, 0.15], { cls: 'f2', r: 4 }));
    g.push(f.dot([1, 0.06], { cls: 'f3', r: 4 }));

    g.push(legend(556, 92, [
        { slot: 1, name: '한 번 관찰의 신뢰도 0.30' },
        { slot: 2, name: '한 번 관찰의 신뢰도 0.15' },
        { slot: 3, name: '한 번 관찰의 신뢰도 0.06' },
    ]));
    g.push(txt(556, 176, '왼쪽 끝의 점 하나만 보면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 196, '‘일관성이 없다’ 로 읽힌다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 216, '같은 값을 스무 번 합치면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 236, '오른쪽 끝이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 270, '자료가 달라진 것이 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 290, '세는 단위를 바꾼 것이다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-t-aggregation', svg: svg({ width: W, height: H, title: '관찰을 합친 횟수와 합친 점수의 신뢰도', desc: '한 번의 관찰이 일관되지 않아도 여러 번 합치면 신뢰도가 크게 오른다는 측정 이론의 공식', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-t-bimodal — 한 덩어리를 잘라 두 유형을 만든다.
 *
 * 무엇을 읽어야 하나: 골짜기가 없는 분포를 가운데서 자르면 두 유형이
 * 생기지만 그 경계는 자료에 있던 것이 아니라는 것. 곡선은 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 1.15], box: { x: 100, y: 66, w: 460, h: 240 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 352, '어떤 특성의 점수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 52, '그 점수를 받은 사람 수', { cls: 'ink2', size: 'sm' }));

    // 흔들리는 띠 — 다시 재면 유형이 바뀌는 구간.
    const bandL = f.X(4.3);
    const bandR = f.X(5.7);
    g.push(box(bandL, f.Y(1.15), bandR - bandL, f.Y(0) - f.Y(1.15), { fill: COL.s2, op: 0.13, stroke: 'none' }));

    g.push(f.curve(bell(5, 1.7), { cls: 's1' }));
    g.push(ln([[f.X(5), f.Y(0)], [f.X(5), f.Y(1.1)]], { stroke: COL.s2, sw: 2.2, dash: '6 4' }));

    g.push(txt(f.X(2.4), f.Y(0) + 26, '유형 가', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(f.X(7.6), f.Y(0) + 26, '유형 나', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(f.X(5), f.Y(1.1) - 12, '여기서 자른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(f.X(5), f.Y(0.06), '흔들리는 구간', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(596, 108, '분포에 골짜기가 없다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(596, 134, '두 덩어리라면 가운데가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 154, '비어 있어야 한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 174, '가장 사람이 많은 자리가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 194, '바로 경계다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 228, '경계 바로 왼쪽과 오른쪽은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 248, '서로 거의 같고, 같은 유형의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 268, '양 끝끼리는 아주 다르다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 302, '주황 띠 안의 사람은 다시 재면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(596, 322, '유형이 바뀐다. 곡선은 도식이다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-t-bimodal', svg: svg({ width: W, height: H, title: '한 덩어리 분포를 잘라 두 유형을 만드는 일', desc: '골짜기 없는 분포를 가운데서 자르면 유형이 생기지만 경계는 자료에 있던 것이 아니다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-t-variance-split — 환경이 고르면 유전율이 오른다.
 *
 * 무엇을 읽어야 하나: 유전의 몫이 그대로인데 유전율이 달라진다는 것.
 * 유전율이 분수이고, 분모가 줄면 값이 오르기 때문이다.
 * 숫자는 뜻을 보이려고 이 문서가 정한 보기다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 420;
    const g = [];
    const base = 320;
    const unit = 2.6;

    const drawBar = (x, gen, env, title, note) => {
        const hG = gen * unit;
        const hE = env * unit;
        g.push(box(x, base - hG, 120, hG, { fill: COL.s1, op: 0.85, stroke: COL.s1 }));
        g.push(box(x, base - hG - hE, 120, hE, { fill: COL.s2, op: 0.35, stroke: COL.s2 }));
        g.push(txt(x + 60, base - hG / 2 + 5, '유전 ' + gen, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + 60, base - hG - hE / 2 + 5, '환경 ' + env, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + 60, base + 24, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + 60, base + 44, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x + 60, base - hG - hE - 14, '총분산 ' + (gen + env), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    };

    drawBar(120, 40, 60, '환경이 제각각인 집단', '유전율 = 40 나누기 100 = 0.4');
    drawBar(340, 40, 10, '환경이 고른 집단', '유전율 = 40 나누기 50 = 0.8');

    g.push(ln([[100, base], [480, base]], { stroke: COL.ink2, sw: 1.5 }));

    g.push(txt(556, 104, '두 막대에서 파란 칸의 높이가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 124, '똑같다는 것을 먼저 보라.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 158, '유전의 몫은 그대로인데 유전율이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 178, '두 배가 되었다. 분모가 줄었기', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 198, '때문이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 232, '그래서 ‘유전율이 높다’ 는 말은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 252, '‘환경이 무력하다’ 는 뜻이 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 272, '오히려 환경을 고르게 만들수록', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 292, '유전율은 올라간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 326, '숫자는 뜻을 보이려고 정한 보기다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-t-variance-split', svg: svg({ width: W, height: H, title: '환경이 고른 집단에서 유전율이 더 높게 나오는 까닭', desc: '유전의 몫이 그대로여도 총분산이 줄면 그 비율인 유전율은 올라간다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-t-within-between — 집단 안의 차이와 집단 사이의 차이.
 *
 * 무엇을 읽어야 하나: 집단 안의 차이가 전부 유전이어도 집단 사이의 차이는
 * 전부 환경일 수 있다는 것. 곡선은 눈금 없는 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 430;
    const g = [];
    const f = frame({ xRange: [0, 14], yRange: [0, 1.3], box: { x: 100, y: 88, w: 470, h: 210 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(7), 386, '다 자란 뒤의 키', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 74, '그만큼 자란 그루 수', { cls: 'ink2', size: 'sm' }));

    g.push(f.curve(bell(4.2, 1.25), { cls: 's2' }));
    g.push(f.curve(bell(9.6, 1.25), { cls: 's3' }));

    g.push(f.label([4.2, 1.05], '척박한 흙', { anchor: 'middle', cls: 'ink bold', dy: -8 }));
    g.push(f.label([9.6, 1.05], '기름진 흙', { anchor: 'middle', cls: 'ink bold', dy: -8 }));

    // 집단 안의 퍼짐.
    g.push(px(f.X(2.95), f.Y(0.42), f.X(5.45), f.Y(0.42), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(px(f.X(5.45), f.Y(0.42), f.X(2.95), f.Y(0.42), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(f.X(4.2), f.Y(0.42) - 12, '집단 안의 차이', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 집단 사이의 거리.
    g.push(px(f.X(4.2), f.Y(-0.16), f.X(9.6), f.Y(-0.16), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(px(f.X(9.6), f.Y(-0.16), f.X(4.2), f.Y(-0.16), { cls: 's1', marker: 'ar1', width: 2 }));
    g.push(txt(f.X(6.9), f.Y(-0.16) + 24, '집단 사이의 차이', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(608, 116, '같은 씨앗 봉지에서', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(608, 136, '한 줌씩 나눠 심었다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(608, 170, '한 화분 안에서 키가 다른 것은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 190, '흙이 같으므로 씨앗의 차이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 210, '즉 집단 안의 유전율이 1 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 244, '그런데 두 화분의 평균이 다른', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 264, '것은 전부 흙 때문이다. 씨앗은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 284, '같은 봉지에서 나왔다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 318, '집단 안의 유전율이 아무리', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(608, 338, '높아도 집단 사이의 차이에', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(608, 358, '대해서는 아무 말도 하지 않는다.', { cls: 'ink bold', size: 'sm' }));

    return { name: 'psy-t-within-between', svg: svg({ width: W, height: H, title: '집단 안의 차이와 집단 사이의 차이는 다른 물음이다', desc: '같은 씨앗을 두 흙에 나눠 심으면 집단 안의 차이는 유전이고 집단 사이의 차이는 흙이다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-t-flynn — 원점수는 올라가고 보고되는 점수는 그대로다.
 *
 * 무엇을 읽어야 하나: 두 선이 다르다는 것. 아래 선이 평평한 것은 아무것도
 * 변하지 않아서가 아니라 규준을 다시 잡기 때문이다. 눈금 없는 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [1930, 2000], yRange: [0, 10], box: { x: 116, y: 62, w: 400, h: 250 } });

    g.push(f.axes({ xTicks: [1930, 1950, 1970, 1990], yTicks: [], grid: false }));
    g.push(txt(f.X(1965), 356, '검사를 받은 해', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(44, 48, '점수', { cls: 'ink2', size: 'sm' }));

    g.push(f.line([[1930, 3.4], [1945, 4.3], [1960, 5.4], [1975, 6.6], [1990, 7.7], [2000, 8.4]], { cls: 's1' }));
    g.push(f.line([[1930, 2.2], [2000, 2.2]], { cls: 's2', dash: '6 4' }));

    g.push(f.label([1993, 8.4], '같은 문항에서 맞힌 개수', { anchor: 'end', cls: 'ink bold', dy: -12 }));
    g.push(f.label([1993, 2.2], '보고되는 표준화 점수', { anchor: 'end', cls: 'ink bold', dy: -12 }));

    g.push(txt(556, 104, '아래 선이 평평한 것은', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 124, '아무것도 안 변해서가 아니다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 158, '표준화 점수는 그 시대 사람들', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 178, '가운데 어디쯤인지를 적은 값이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 198, '규준을 다시 잡으면 평균이 다시', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 218, '가운데로 온다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 252, '그래서 이 상승은 규준을', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 272, '다시 잡는 작업에서 드러났다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 306, '눈금 없는 도식이다. 기울기는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 326, '어느 자료의 값도 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-t-flynn', svg: svg({ width: W, height: H, title: '세대에 걸친 원점수 상승과 평평한 표준화 점수', desc: '같은 문항에서 맞힌 개수는 올라가는데 보고되는 표준화 점수는 그대로다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-t-cutoff — 문턱 근처에서 판정이 뒤집힌다.
 *
 * 무엇을 읽어야 하나: 같은 검사를 두 번 재면 문턱 근처의 사람들이 반대쪽으로
 * 넘어간다는 것. 점은 씨앗 고정 난수로 만든 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 800;
    const H = 470;
    const g = [];
    const rand = rng(19270502);
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 120, y: 64, w: 330, h: 330 } });
    const CUT = 6.2;

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 438, '처음 잰 점수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 50, '다시 잰 점수', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[f.X(CUT), f.Y(0)], [f.X(CUT), f.Y(10)]], { stroke: COL.s2, sw: 2, dash: '6 4' }));
    g.push(ln([[f.X(0), f.Y(CUT)], [f.X(10), f.Y(CUT)]], { stroke: COL.s2, sw: 2, dash: '6 4' }));
    g.push(txt(f.X(CUT) + 6, f.Y(10) + 4, '문턱', { cls: 'ink2', size: 'sm' }));

    let flipped = 0;
    for (let i = 0; i < 150; i += 1) {
        const t = 4.9 + gauss(rand) * 1.9;
        const a = Math.max(0.2, Math.min(9.8, t + gauss(rand) * 0.72));
        const b = Math.max(0.2, Math.min(9.8, t + gauss(rand) * 0.72));
        const flip = (a >= CUT) !== (b >= CUT);
        if (flip) flipped += 1;
        g.push(f.dot([a, b], { cls: flip ? 'f2' : 'f1', r: flip ? 4.2 : 3 }));
    }


    g.push(txt(492, 104, '주황 점이 뒤집힌 사람이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(492, 130, '한 번은 문턱을 넘고 한 번은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 150, '못 넘었다. 전부 문턱 가까이에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 170, '몰려 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 204, '검사가 나빠서가 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 224, '어떤 검사에도 오차가 있고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 244, '한 점으로 자르면 오차가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 264, '합격과 불합격을 가른다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 298, '이 그림에서 뒤집힌 사람은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 318, '150명 가운데 ' + flipped + '명이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 352, '점은 씨앗 고정 난수로 만든', { cls: 'ink2', size: 'sm' }));
    g.push(txt(492, 372, '도식이다. 실제 자료가 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-t-cutoff', svg: svg({ width: W, height: H, title: '같은 검사를 두 번 잴 때 문턱 근처에서 뒤집히는 사람들', desc: '문턱 가까이에 있던 사람들이 다시 재면 반대쪽으로 넘어간다', body: g.join('\n') }) };
})());

export default figures;
