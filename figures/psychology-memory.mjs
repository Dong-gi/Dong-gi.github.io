/**
 * 심리학 문서 8장(기억)과 9장(기억은 어떻게 어긋나는가)의 그림.
 *
 * 이름은 모두 `psy-e-` 로 시작한다(8·9장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없고(그림이 <img> 로 들어가 MathJax 가 닿지 않는다),
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 쓰지 않는다.
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓰고, HTML 엔티티도 쓸 수 없다.
 *
 * 자료 규칙 — 이 파일에는 남의 원자료를 옮긴 그림이 하나도 없다. 곡선은 여러
 * 연구가 보고한 <b>방향</b>만 옮긴 도식이고, 그래서 세로축에 눈금을 달지 않았다.
 * 값을 읽는 그림이 아니라 두 선의 순서가 뒤집히는지를 읽는 그림이다.
 *
 * 상자와 화살표만으로 되는 그림(기억 체계의 구조·설계 흐름·절차 목록)은
 * d2/psychology/psy-e-*.d2 에 있다.
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

/* ------------------------------------------------------------------ *
 * psy-e-serial-position — 계열위치곡선.
 *
 * 무엇을 읽어야 하나: 처음과 끝이 잘 회상된다는 것, 그리고 회상 전에 딴 일을
 * 시키면 <b>끝쪽 이점만</b> 사라진다는 것. 두 이점의 출처가 다르다는 뜻이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [1, 15], yRange: [0, 1], box: { x: 96, y: 80, w: 560, h: 224 } });

    g.push(f.axes({ xTicks: [1, 3, 5, 7, 9, 11, 13, 15], yTicks: [], grid: false }));

    const prim = i => 0.62 * Math.exp(-(i - 1) / 2.4);
    const rec = i => 0.72 * Math.exp(-(15 - i) / 2.0);
    const immediate = [];
    const delayed = [];
    for (let i = 1; i <= 15; i += 1) {
        immediate.push([i, Math.min(0.95, 0.2 + prim(i) + rec(i))]);
        delayed.push([i, Math.min(0.95, 0.16 + prim(i) * 0.95)]);
    }
    g.push(f.line(immediate, { cls: 's1' }));
    g.push(f.line(delayed, { cls: 's2', dash: '6 4' }));
    for (const p of immediate) g.push(f.dot(p, { cls: 'f1', r: 3 }));
    for (const p of delayed) g.push(f.dot(p, { cls: 'f2', r: 3 }));

    g.push(f.label([7, immediate[6][1]], '바로 회상', { dx: 0, dy: -14, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([7, delayed[6][1]], '딴 일을 시키고 회상', { dx: 0, dy: 24, anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([1, 0.86], '처음쪽 이점', { dx: 4, dy: -12, cls: 'ink2', size: 'sm' }));
    g.push(f.label([15, 0.94], '끝쪽 이점', { dx: -2, dy: -12, anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(f.X(8), 334, '목록에서 몇 번째로 제시된 낱말인가', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 66, '세로축 — 회상된 비율 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(676, 92, 166, 120, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(692, 116, '읽는 법', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(692, 140, '30초쯤 딴 일을', { cls: 'ink2', size: 'sm' }));
    g.push(txt(692, 158, '시키면 끝쪽만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(692, 176, '무너진다. 처음쪽은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(692, 194, '거의 그대로다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '목록의 처음과 끝이 잘 남는다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 372, '세로축에 눈금을 달지 않았다. 값이 아니라 두 곡선의 모양 차이를 읽는 그림이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-serial-position',
        svg: svg({ width: W, height: H, title: '계열위치곡선', desc: '처음과 끝이 잘 회상되고 지연을 두면 끝쪽 이점만 사라지는 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-e-chunk — 같은 자료를 몇 덩어리로 세는가.
 *
 * 무엇을 읽어야 하나: 항목 수가 정해진 값이 아니라 <b>세는 단위에 딸린 값</b>
 * 이라는 것. 그래서 ‘몇 개를 담는가’ 에 하나의 수를 붙이기가 어렵다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 340;
    const g = [];
    const digits = ['1', '9', '4', '5', '1', '9', '5', '0', '1', '9', '8', '8'];
    const cw = 46;
    const x0 = 130;

    const row = (y, label, groups) => {
        g.push(txt(x0 - 22, y - 26, label, { cls: 'ink bold', size: 'sm' }));
        let idx = 0;
        for (const gsz of groups) {
            g.push(box(x0 + idx * cw - 6, y - 20, gsz * cw - 4, 40, { stroke: COL.s1, sw: 1.8 }));
            idx += gsz;
        }
        digits.forEach((d, i) => {
            g.push(txt(x0 + i * cw + cw / 2 - 6, y + 7, d, { anchor: 'middle', cls: 'ink' }));
        });
        const count = groups.length;
        g.push(txt(x0 + 12 * cw + 18, y + 6, `${count} 덩어리`, { cls: 'ink bold', size: 'sm' }));
    };

    row(112, '한 자리씩 세면', Array.from({ length: 12 }, () => 1));
    row(212, '네 자리 연도로 묶으면', [4, 4, 4]);

    g.push(box(x0 - 24, 258, 660, 44, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(x0 - 8, 278, '같은 열두 글자다. 몇 개인지는 자료가 정하는 것이 아니라 아는 것이 정한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 - 8, 296, '그래서 ‘몇 개를 담는가’ 의 답은 세는 단위를 정하기 전에는 정해지지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 44, '덩이짓기 — 같은 자료, 다른 개수', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 68, '연도라는 것을 알아보는 사람에게만 아래쪽 묶음이 가능하다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-chunk',
        svg: svg({ width: W, height: H, title: '덩이짓기', desc: '열두 자리 숫자를 한 자리씩 셀 때와 연도로 묶어 셀 때의 개수 차이', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-e-spacing — 간격 효과.
 *
 * 무엇을 읽어야 하나: 검사가 가까울 때와 멀 때 두 선의 <b>순서가 뒤집힌다</b>는 것.
 * 몰아서 한 쪽이 바로 뒤에는 나아 보인다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 30], yRange: [0, 1], box: { x: 108, y: 82, w: 500, h: 220 } });

    g.push(f.axes({ xTicks: [0, 5, 10, 15, 20, 25, 30], yTicks: [], grid: true }));

    const massed = [];
    const spaced = [];
    for (let t = 0; t <= 30; t += 0.5) {
        massed.push([t, 0.93 * Math.exp(-t / 5.5) + 0.04]);
        spaced.push([t, 0.80 * Math.exp(-t / 17) + 0.06]);
    }
    g.push(f.line(massed, { cls: 's2' }));
    g.push(f.line(spaced, { cls: 's1' }));

    g.push(f.label([2.6, 0.60], '몰아서 한 쪽', { dx: 10, dy: -10, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([20, 0.30], '나눠서 한 쪽', { dx: 6, dy: -12, cls: 'ink bold', size: 'sm' }));

    // 교차점 표시.
    g.push(f.line([[3.4, 0], [3.4, 0.62]], { cls: 'ax', dash: '4 3' }));
    g.push(f.label([3.4, 0.06], '여기서 뒤집힌다', { dx: 8, dy: 0, cls: 'ink2', size: 'sm' }));

    g.push(txt(f.X(15), 334, '학습이 끝난 뒤 시험까지 흐른 시간 (단위 없는 도식)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 66, '세로축 — 시험에서 맞힌 양 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(632, 96, 210, 146, { stroke: COL.s1, sw: 1.6 }));
    g.push(txt(648, 122, '왜 오해가 생기나', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(648, 146, '학습 직후에 스스로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(648, 164, '점검하면 몰아서 한 쪽이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(648, 182, '더 잘된 것으로 보인다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(648, 206, '판단하는 시점이 곡선의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(648, 224, '왼쪽 끝에 있기 때문이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '나눠서 하면 나중에 더 남는다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 372, '두 조건의 총 학습 시간은 같다. 다른 것은 그 시간을 언제 썼는가뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-spacing',
        svg: svg({ width: W, height: H, title: '간격 효과', desc: '몰아서 한 조건과 나눠서 한 조건의 파지 곡선이 교차하는 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-e-testing — 인출 연습과 학습자의 예측.
 *
 * 무엇을 읽어야 하나: 실제 성적(왼쪽 두 쌍)과 학습자의 예측(오른쪽)이
 * 서로 반대 방향을 가리킨다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 400;
    const g = [];
    const base = 300;
    const bw = 54;

    const group = (x0, title, a, b, note) => {
        g.push(txt(x0 + bw + 6, 92, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(`<rect x="${x0}" y="${base - a * 190}" width="${bw}" height="${r2(a * 190)}" rx="3" fill="${COL.s2}" fill-opacity="0.85"/>`);
        g.push(`<rect x="${x0 + bw + 12}" y="${base - b * 190}" width="${bw}" height="${r2(b * 190)}" rx="3" fill="${COL.s1}" fill-opacity="0.85"/>`);
        g.push(txt(x0 + bw + 6, base + 22, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    };

    group(120, '5분 뒤 시험', 0.88, 0.80, '다시 읽기가 조금 낫다');
    group(360, '한 주 뒤 시험', 0.42, 0.72, '인출 연습이 크게 낫다');
    group(600, '학습자 스스로의 예측', 0.85, 0.62, '다시 읽기 쪽을 높게 본다');

    g.push(`<rect x="132" y="342" width="20" height="9" rx="2" fill="${COL.s2}" fill-opacity="0.85"/>`);
    g.push(txt(160, 351, '같은 시간 동안 다시 읽기', { cls: 'ink2', size: 'sm' }));
    g.push(`<rect x="382" y="342" width="20" height="9" rx="2" fill="${COL.s1}" fill-opacity="0.85"/>`);
    g.push(txt(410, 351, '같은 시간 동안 인출 연습(보지 않고 떠올려 적기)', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '나중에 남는 것과 지금 남을 것 같은 느낌이 어긋난다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 60, '두 조건의 학습 시간은 같다. 다른 것은 그 시간에 무엇을 했는가뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 380, '막대 높이에 눈금을 달지 않았다. 값이 아니라 어느 쪽이 높은지만 읽는 그림이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-testing',
        svg: svg({ width: W, height: H, title: '인출 연습과 학습자의 예측', desc: '지연 시험에서는 인출 연습이 낫지만 학습자는 반대로 예측하는 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-e-forgetting — 망각 곡선과 다시 배울 때의 절약.
 *
 * 무엇을 읽어야 하나: 처음에 가파르게 떨어지고 뒤로 갈수록 완만해진다는 것,
 * 그리고 다시 배우면 같은 시간이 지나도 남는 양이 더 많다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 31], yRange: [0, 1], box: { x: 104, y: 80, w: 520, h: 226 } });

    g.push(f.axes({ xTicks: [0, 1, 2, 7, 14, 21, 31], yTicks: [], grid: true }));

    const first = [];
    const second = [];
    for (let t = 0; t <= 31; t += 0.25) {
        first.push([t, 1 / (1 + 0.92 * Math.log(1 + t * 2.2))]);
        second.push([t, 1 / (1 + 0.42 * Math.log(1 + t * 2.2))]);
    }
    g.push(f.line(first, { cls: 's2' }));
    g.push(f.line(second, { cls: 's1', dash: '6 4' }));

    g.push(f.label([10, first[40][1]], '한 번 배운 뒤', { dx: 10, dy: 20, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([12, second[48][1]], '한 번 더 배운 뒤', { dx: 10, dy: -10, cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(15), 338, '배운 뒤 흐른 날 수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 66, '세로축 — 남아 있는 양 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(646, 92, 196, 156, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(662, 116, '어떻게 재는가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(662, 140, '얼마나 기억나는지 묻지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(662, 158, '않는다. 같은 목록을 다시', { cls: 'ink2', size: 'sm' }));
    g.push(txt(662, 176, '외우게 하고, 처음보다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(662, 194, '시간이 얼마나 줄었는지를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(662, 212, '센다. 자기 보고에 기대지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(662, 230, '않는 지표다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '망각은 처음에 빠르고 뒤에 느리다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 374, '에빙하우스가 자기 자신에게 무의미 철자로 얻은 모양이다. 의미 있는 자료의 망각은 이보다 훨씬 느리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-forgetting',
        svg: svg({ width: W, height: H, title: '망각 곡선', desc: '처음에 가파르고 뒤로 갈수록 완만해지는 곡선과 다시 배운 뒤의 더 높은 곡선', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-e-flashbulb — 확신과 일치도가 따로 논다.
 *
 * 무엇을 읽어야 하나: 두 선이 갈라진다는 것. 생생하다는 느낌과 처음 적은 것과
 * 맞는 정도가 같이 가지 않는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 390;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 1], box: { x: 108, y: 84, w: 500, h: 216 } });

    g.push(f.axes({ xTicks: [0, 1, 3, 5, 10], yTicks: [], grid: true }));

    const conf = [];
    const acc = [];
    for (let t = 0; t <= 10; t += 0.2) {
        conf.push([t, 0.92 - 0.05 * t / 10]);
        acc.push([t, 0.9 * Math.exp(-t / 3.2) + 0.12]);
    }
    g.push(f.line(conf, { cls: 's1' }));
    g.push(f.line(acc, { cls: 's2' }));

    g.push(f.label([7.4, 0.885], '얼마나 생생하고 확신하는가', { dx: -4, dy: -12, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([7.4, acc[37][1]], '처음 적은 내용과 맞는 정도', { dx: -4, dy: -12, anchor: 'end', cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(5), 332, '사건이 있고 나서 흐른 해 수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(34, 76, '높음', { cls: 'ink2', size: 'sm' }));

    g.push(box(630, 96, 212, 128, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(646, 122, '어떻게 알 수 있나', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(646, 146, '사건 바로 다음 날 적어 둔', { cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 164, '기록이 있어야 한다. 그것과', { cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 182, '뒤의 보고를 맞춰 보는 것이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(646, 200, '이 연구들의 설계다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 36, '생생함은 정확함이 아니다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 366, '여러 추적 연구가 보고한 방향을 옮긴 도식이다. 값이 아니라 두 선이 갈라진다는 것만 읽는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-e-flashbulb',
        svg: svg({ width: W, height: H, title: '섬광기억의 확신과 일치도', desc: '확신은 거의 그대로인데 처음 기록과의 일치도는 떨어지는 두 곡선', body: g.join('') }),
    };
})());

export default figures;
