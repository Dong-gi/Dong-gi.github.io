/**
 * 심리학 문서 19장(임상·상담 개관)의 그림.
 *
 * 이름은 모두 `psy-k-` 로 시작한다(19장 담당자에게 배정된 접두어).
 *
 * 이 장은 원장 §0.3 의 선을 지킨다. 그래서 이 파일의 그림에는
 * <b>증상 이름도 진단명도 나오지 않는다.</b> 다루는 것은 분류 체계와
 * 효과 연구의 구조이지 무엇을 판별하는 방법이 아니다. 축과 항목은 전부
 * 이름 없는 보기(‘항목 1’, ‘어떤 연속된 특성의 점수’)로 두었다.
 *
 * 자료 규칙 — 남의 원자료를 옮긴 그림이 하나도 없다. 곡선과 구간은 눈금 없는
 * 도식이고, 조합의 개수만 실제로 셈한 값이다(순수한 산수다).
 *
 * 상자와 화살표만으로 되는 그림(다루는 것과 다루지 않는 것 · 이론 계보 ·
 * 공통요인과 특정성분 · 훈련 경로)은 d2/psychology/psy-k-*.d2 에 있다.
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

const bell = (mu, sd) => x => Math.exp(-0.5 * Math.pow((x - mu) / sd, 2));

/* ------------------------------------------------------------------ *
 * psy-k-threshold — 문턱의 자리가 이름을 받는 사람 수를 정한다.
 *
 * 무엇을 읽어야 하나: 문턱을 조금 옮기면 이름을 받는 사람 수가 크게
 * 달라진다는 것. 가로축은 이름 없는 보기이고 곡선은 눈금 없는 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 1.15], box: { x: 100, y: 70, w: 420, h: 230 } });
    const A = 6.0;
    const B = 6.9;

    // 두 문턱 사이의 띠 — 문턱을 옮기면 편이 바뀌는 사람들.
    g.push(box(f.X(A), f.Y(1.15), f.X(B) - f.X(A), f.Y(0) - f.Y(1.15), { fill: COL.s2, op: 0.18, stroke: 'none' }));

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 344, '어떤 연속된 특성의 점수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 56, '그 점수를 받은 사람 수', { cls: 'ink2', size: 'sm' }));

    g.push(f.curve(bell(4.6, 1.8), { cls: 's1' }));
    g.push(ln([[f.X(A), f.Y(0)], [f.X(A), f.Y(1.05)]], { stroke: COL.s2, sw: 2.2, dash: '6 4' }));
    g.push(ln([[f.X(B), f.Y(0)], [f.X(B), f.Y(1.05)]], { stroke: COL.s3, sw: 2.2, dash: '6 4' }));
    g.push(txt(f.X(A), f.Y(1.05) - 10, '문턱을 여기 두면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(B) + 6, f.Y(1.05) + 12, '여기 두면', { cls: 'ink2', size: 'sm' }));

    g.push(txt(f.X((A + B) / 2), f.Y(0) + 26, '이만큼의 사람이 편을 바꾼다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(556, 106, '문턱은 자료에서 발견된 것이', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 126, '아니라 정해진 것이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 158, '분포에 골짜기가 없으면 어디를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 178, '잘라도 자를 수 있다. 그리고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 198, '어디를 자르느냐가 이름을 받는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 218, '사람 수를 크게 바꾼다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 250, '그래서 분류 체계가 개정될 때마다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 270, '이 자리가 논쟁이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 302, '가로축은 이름 없는 보기다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 322, '특정한 무엇에 대한 그림이 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-k-threshold', svg: svg({ width: W, height: H, title: '문턱을 조금 옮기면 이름을 받는 사람 수가 크게 달라진다', desc: '골짜기 없는 분포에 그은 문턱의 자리가 분류의 경계를 정한다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-k-polythetic — ‘여러 항목 가운데 몇 개 이상’ 규칙의 성질.
 *
 * 무엇을 읽어야 하나: 같은 이름을 받은 두 사람이 공유하는 항목이 하나도
 * 없을 수 있다는 것. 항목은 전부 이름 없는 보기이고, 조합의 개수만 실제로
 * 셈한 값이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 380;
    const g = [];
    const x0 = 190;
    const cw = 46;
    const n = 10;

    g.push(txt(x0 + (cw * n) / 2, 48, '이름 없는 항목 열 개 가운데 다섯 개 이상이면 같은 이름을 붙이는 규칙', { anchor: 'middle', cls: 'ink bold' }));

    for (let i = 0; i < n; i += 1) {
        g.push(txt(x0 + cw * i + cw / 2, 92, String(i + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    const row = (y, label, set, cls) => {
        g.push(txt(x0 - 18, y + 24, label, { anchor: 'end', cls: 'ink bold' }));
        for (let i = 0; i < n; i += 1) {
            const on = set.includes(i + 1);
            g.push(box(x0 + cw * i + 4, y, cw - 8, 34, {
                fill: on ? `var(--${cls})` : 'none',
                op: on ? 0.85 : 1,
                stroke: on ? `var(--${cls})` : COL.grid,
            }));
        }
    };

    row(110, '사람 가', [1, 2, 3, 4, 5], 's1');
    row(160, '사람 나', [6, 7, 8, 9, 10], 's2');

    g.push(txt(x0, 232, '두 사람 다 다섯 개를 만족했으므로 같은 이름을 받는다.', { cls: 'ink' }));
    g.push(txt(x0, 256, '그런데 공유하는 항목이 하나도 없다.', { cls: 'ink bold' }));

    g.push(txt(x0, 296, '열 개 가운데 다섯 개 이상을 만족하는 조합은 638가지다.', { cls: 'ink' }));
    g.push(txt(x0, 320, '하나의 이름 아래 638가지가 들어 있다는 뜻이다.', { cls: 'ink2' }));
    g.push(txt(x0, 352, '항목은 전부 이름 없는 보기다. 이 그림은 분류 규칙의 성질에 대한 것이지 무엇을 판별하는 방법이 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-k-polythetic', svg: svg({ width: W, height: H, title: '여러 항목 가운데 몇 개 이상이라는 규칙의 성질', desc: '같은 이름을 받은 두 사람이 공유하는 항목이 하나도 없을 수 있다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-k-dodo — 계열끼리 견준 시험들의 차이.
 *
 * 무엇을 읽어야 하나: 구간 대부분이 0 을 감싸지만 전부는 아니라는 것.
 * ‘모두 같다’ 도 ‘어느 하나가 낫다’ 도 이 모양에서 곧바로 나오지 않는다.
 * 점과 구간은 전부 눈금 없는 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [-1, 1], yRange: [0, 9], box: { x: 190, y: 60, w: 300, h: 280 } });

    g.push(ln([[f.X(0), f.Y(0)], [f.X(0), f.Y(9)]], { stroke: COL.ink2, sw: 1.8, dash: '5 4' }));
    g.push(txt(f.X(0), f.Y(9) - 12, '차이가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(-0.62), 372, '왼쪽 계열이 낫다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0.62), 372, '오른쪽 계열이 낫다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[f.X(-1), f.Y(0)], [f.X(1), f.Y(0)]], { stroke: COL.ink2, sw: 1.4 }));

    const trials = [
        [0.06, 0.30, false], [-0.10, 0.26, false], [0.14, 0.34, false],
        [0.02, 0.20, false], [-0.05, 0.40, false], [0.31, 0.22, true],
        [-0.28, 0.24, true], [0.09, 0.28, false],
    ];
    trials.forEach(([m, se, sig], i) => {
        const y = 8.2 - i;
        const cls = sig ? 's2' : 's1';
        g.push(ln([[f.X(m - se), f.Y(y)], [f.X(m + se), f.Y(y)]], { stroke: `var(--${cls})`, sw: 2.4 }));
        g.push(f.dot([m, y], { cls: sig ? 'f2' : 'f1', r: 5 }));
        g.push(txt(180, f.Y(y) + 4, '비교 ' + (i + 1), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    });

    g.push(txt(536, 100, '구간 대부분이 0 을 감싼다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(536, 126, '계열이 달라도 결과가 크게 다르지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 146, '않다는 관찰이 이 모양이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 178, '그런데 전부는 아니다. 주황으로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 198, '표시한 두 비교는 0 을 감싸지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 218, '않는다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 250, '그러므로 이 모양에서 나오는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 270, '결론은 ‘모두 같다’ 가 아니라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 290, '‘차이가 있어도 대체로 작다’ 이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 322, '점과 구간은 눈금 없는 도식이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(536, 342, '어느 연구의 값도 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-k-dodo', svg: svg({ width: W, height: H, title: '계열끼리 견준 비교들의 차이와 그 구간', desc: '구간 대부분이 차이 없음을 감싸지만 전부는 아니다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-k-control — 무엇과 견주느냐가 효과의 크기를 바꾼다.
 *
 * 무엇을 읽어야 하나: 같은 개입인데 비교 조건에 따라 효과크기가 달라진다는 것.
 * 막대의 길이는 눈금 없는 도식이며 어느 연구의 값도 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 360;
    const g = [];
    const x0 = 300;
    const scale = 220;

    const bar = (y, len, cls, title, note) => {
        g.push(box(x0, y, len * scale, 34, { fill: `var(--${cls})`, op: 0.8, stroke: `var(--${cls})` }));
        g.push(txt(x0 - 16, y + 23, title, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x0 + len * scale + 12, y + 23, note, { cls: 'ink2', size: 'sm' }));
    };

    g.push(txt(60, 52, '같은 개입을 무엇과 견주었는가에 따라 나오는 효과크기', { cls: 'ink bold' }));
    g.push(ln([[x0, 82], [x0, 300]], { stroke: COL.ink2, sw: 1.4 }));
    g.push(txt(x0, 322, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 110, 322, '효과크기가 커지는 쪽', { cls: 'ink2', size: 'sm' }));

    bar(94, 1.00, 's2', '아무것도 하지 않은 집단과 견줌', '가장 크게 나온다');
    bar(148, 0.72, 's2', '대기자 명단과 견줌', '기다리는 동안 나빠지는 몫이 섞인다');
    bar(202, 0.40, 's1', '겉모양만 갖춘 다른 활동과 견줌', '기대와 관심의 몫이 빠진다');
    bar(256, 0.12, 's3', '이미 쓰이는 다른 처치와 견줌', '가장 작게 나온다');

    return { name: 'psy-k-control', svg: svg({ width: W, height: H, title: '비교 조건이 효과크기를 바꾼다', desc: '같은 개입이라도 무엇과 견주느냐에 따라 보고되는 효과크기가 달라진다', body: g.join('\n') }) };
})());

export default figures;
