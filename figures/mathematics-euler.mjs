/**
 * 기초수학 7장(함수)의 「자연상수 e 와 자연로그」 절 그림.
 *
 * 이름은 전부 `math-eul-` 로 시작한다. 기존 블록(math-log- / math-fn- / math-cal- /
 * math-int- / math-ser-)과 겹치지 않게 새로 배정한 접두어다.
 * source/build/figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 그래서 아래첨자는 lib 의 `N~0` 표기를, 위첨자와 나머지 기호는 유니코드
 * (ˣ, ⁻, ¹, τ, ×, −, …)로 적는다. lib 의 esc() 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 `~` 를 그냥 쓰면 안 되고, 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/** 화소 좌표 사각형 — 패널 테두리에 쓴다. */
function panel(x, y, w, h) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="6" fill="none" stroke="var(--grid)" stroke-width="1"/>`;
}

/* ================================================================== *
 * 7-A. (0, 1) 에서의 접선 기울기가 딱 1 이 되는 밑
 * ================================================================== */
add((() => {
    const W = 640, H = 350;
    const g = [];
    const bases = [
        { a: 2, name: 'y = 2ˣ', slope: '0.69', note: '45° 보다 완만하다' },
        { a: Math.E, name: 'y = eˣ', slope: '1.00', note: '45° 선과 겹친다' },
        { a: 3, name: 'y = 3ˣ', slope: '1.10', note: '45° 보다 가파르다' },
    ];
    bases.forEach((b, i) => {
        const px = 16 + i * 204;
        g.push(panel(px, 30, 190, 256));
        const f = frame({
            xRange: [-1.3, 1.3], yRange: [0, 3.4],
            box: { x: px + 30, y: 68, w: 140, h: 190 },
        });
        g.push(f.axes({ xTicks: [-1, 1], yTicks: [1, 2, 3], grid: false }));
        // 접선
        const m = Math.log(b.a);
        g.push(f.curve(x => 1 + m * x, { from: -1.0, to: 1.0, cls: 's2' }));
        // 기울기 1 인 기준선. 세 패널에 똑같이 두고 접선 위에 그린다.
        // 위에 그려야 밑이 e 인 패널에서 두 선이 겹친다는 것이 점선 사이로 보인다.
        g.push(f.curve(x => 1 + x, { from: -1.0, to: 1.0, cls: 'gr', dash: '5 5' }));
        // 곡선
        g.push(f.curve(x => Math.pow(b.a, x), { from: -1.3, to: Math.min(1.3, Math.log(3.4) / Math.log(b.a)), cls: 's1' }));
        g.push(f.dot([0, 1], { cls: 'f1', r: 4.5 }));
        g.push(txt(px + 95, 52, b.name, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(px + 95, 302, `접선 기울기 ${b.slope}`, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(px + 95, 320, b.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(16, 344, '주황 실선이 점 (0, 1) 에서의 접선, 회색 점선이 기울기 1 인 기준선이다. 두 선이 정확히 겹치는 밑은 하나뿐이고 그 값이 e 다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'math-eul-tangent',
        svg: svg({
            width: W, height: H,
            title: '(0, 1) 에서의 접선 기울기가 1 이 되는 밑이 e 다',
            desc: '밑이 2, e, 3 인 지수함수의 (0,1) 에서의 접선을 기울기 1 인 기준선과 견주는 세 패널 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 7-B. e^(−x) 의 감쇠 — 0.368 배씩, 절반은 0.693 에서
 * ================================================================== */
add((() => {
    const W = 620, H = 340;
    const f = frame({ xRange: [0, 4.3], yRange: [0, 1.1], box: { x: 58, y: 34, w: 430, h: 234 } });
    const g = [];
    g.push(f.axes({
        xTicks: [1, 2, 3, 4], yTicks: [0.5, 1],
        xLabel: 't / τ', yLabel: '남은 비율',
    }));
    g.push(f.curve(x => Math.exp(-x), { from: 0, to: 4.3, cls: 's1' }));

    const marks = [[1, 0.368], [2, 0.135], [3, 0.050]];
    for (const [x, y] of marks) {
        g.push(f.guide([x, 0], [x, y]));
        g.push(f.guide([0, y], [x, y]));
        g.push(f.dot([x, y], { cls: 'f1', r: 4.5 }));
    }
    g.push(f.label([1, 0.368], '0.368', { dx: 8, dy: -8, cls: 'ink bold' }));
    g.push(f.label([2, 0.135], '0.135', { dx: 8, dy: -8, cls: 'ink bold' }));
    g.push(f.label([3, 0.050], '0.050', { dx: 8, dy: -8, cls: 'ink bold' }));

    // 반감기
    g.push(f.line([[0, 0.5], [0.693, 0.5]], { cls: 's2', dash: '5 4' }));
    g.push(f.line([[0.693, 0], [0.693, 0.5]], { cls: 's2', dash: '5 4' }));
    g.push(f.dot([0.693, 0.5], { cls: 'f2', r: 4.5 }));
    g.push(f.label([0.82, 0.63], '절반이 되는 곳 — 0.693', { cls: 'ink bold' }));

    g.push(f.label([4.25, 0.23], '0 에 닿지는 않는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(58, 300, '가로 한 칸(시간상수 τ)이 지날 때마다 0.368 배가 남는다. 값이 0 이 되는 시각은 없고, 얼마든지 작아질 뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(58, 320, '절반이 되는 시각은 한 칸이 아니라 0.693 칸이다. 반감기와 시간상수를 같은 것으로 읽는 것이 가장 흔한 실수다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'math-eul-decay',
        svg: svg({
            width: W, height: H,
            title: '지수적 감쇠에서 한 칸마다 남는 비율은 0.368 이고 절반이 되는 곳은 0.693 이다',
            desc: 'e 의 음의 지수 곡선에 1, 2, 3 에서의 값과 절반이 되는 지점을 표시한 그래프',
            body: g.join(''),
        }),
    };
})());

export default figures;
