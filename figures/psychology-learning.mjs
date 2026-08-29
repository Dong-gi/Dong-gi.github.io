/**
 * 심리학 문서 7장(학습)의 그림.
 *
 * 이름은 모두 `psy-l-` 로 시작한다(7장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없고(그림이 <img> 로 들어가 MathJax 가 닿지 않는다),
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 쓰지 않는다.
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓰고, HTML 엔티티도 쓸 수 없다.
 *
 * 자료 규칙 — 이 파일에는 남의 원자료를 옮긴 그림이 하나도 없다. 모든 곡선은
 * 여러 연구가 보고한 모양의 방향만 옮긴 도식이고, 그래서 세로축에 눈금을 달지
 * 않은 것이 많다. 문서의 캡션에도 그렇게 적었다.
 *
 * 상자와 화살표만으로 되는 그림(용어 지도·2×2 표·설계 비교)은
 * d2/psychology/psy-l-*.d2 에 있다.
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

function ln(pts, { stroke = COL.ink2, sw = 1.6, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/* ------------------------------------------------------------------ *
 * psy-l-acquisition — 획득 · 소거 · 자발적 회복.
 *
 * 무엇을 읽어야 하나: 소거 뒤 반응이 0 으로 내려갔다가 쉬고 다시 재면
 * 일부가 돌아온다는 것. 소거는 배운 것을 지우는 것이 아니라 덮어쓰는 것이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: 82, y: 100, w: 700, h: 222 } });

    // 네 구간의 배경.
    const band = (a, b, label) => {
        g.push(box(f.X(a), 94, f.X(b) - f.X(a), 234, { stroke: COL.grid, sw: 1, rx: 2 }));
        g.push(txt((f.X(a) + f.X(b)) / 2, 86, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    };
    band(0, 34, '획득 — 조건자극 뒤에 무조건자극이 온다');
    band(34, 58, '소거 — 조건자극만 온다');
    band(58, 72, '쉼 (재지 않는다)');
    band(72, 100, '다시 조건자극만');

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));

    const acq = [];
    for (let t = 0; t <= 34; t += 1) acq.push([t, 0.95 * (1 - Math.exp(-t / 8.5))]);
    const ext = [];
    for (let t = 34; t <= 58; t += 1) ext.push([t, 0.93 * Math.exp(-(t - 34) / 6.2) + 0.03]);
    const rec = [];
    for (let t = 72; t <= 100; t += 1) rec.push([t, 0.46 * Math.exp(-(t - 72) / 7.5) + 0.03]);

    g.push(f.line(acq, { cls: 's1' }));
    g.push(f.line(ext, { cls: 's2' }));
    g.push(f.line(rec, { cls: 's2' }));
    g.push(f.line([[58, ext[ext.length - 1][1]], [72, 0.46]], { cls: 's3', dash: '5 4' }));

    g.push(f.dot([72, 0.46], { cls: 'f3', r: 5 }));
    g.push(f.label([72, 0.46], '자발적 회복', { dx: 8, dy: -12, cls: 'ink bold', size: 'sm' }));
    g.push(f.label([34, 0.94], '획득의 끝', { dx: -6, dy: -10, anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(f.label([100, 0.06], '다시 내려간다', { dx: -4, dy: -10, anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 62, '세로축 — 조건반응의 크기 (눈금 없음. 값이 아니라 모양만 읽는다)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(50), 350, '시행 (왼쪽에서 오른쪽으로 시간이 간다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 34, '소거는 지우는 것이 아니다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 394, '소거 뒤에 남는 것이 없다면 쉬고 다시 재도 아무 반응이 없어야 한다. 그렇지 않다는 것이 이 그림의 요점이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-l-acquisition',
        svg: svg({ width: W, height: H, title: '획득 · 소거 · 자발적 회복', desc: '조건반응이 올라갔다 내려간 뒤 쉬고 다시 재면 일부가 돌아오는 모양', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-l-gradient — 일반화 기울기와 변별 훈련의 효과.
 *
 * 무엇을 읽어야 하나: 훈련하지 않은 자극에도 반응이 나오되 비슷할수록 크다는 것,
 * 그리고 변별 훈련을 하면 그 퍼짐이 좁아진다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 380;
    const g = [];
    const f = frame({ xRange: [-4, 4], yRange: [0, 1], box: { x: 100, y: 74, w: 620, h: 216 } });

    g.push(f.axes({ xTicks: [-4, -3, -2, -1, 0, 1, 2, 3, 4], yTicks: [], grid: false }));

    const wide = x => Math.exp(-(x * x) / 4.2);
    const narrow = x => Math.exp(-(x * x) / 0.85);
    g.push(f.curve(wide, { cls: 's1' }));
    g.push(f.curve(narrow, { cls: 's2', dash: '6 4' }));

    g.push(f.line([[0, 0], [0, 1.02]], { cls: 'ax', dash: '3 3' }));
    g.push(f.label([0, 1.02], '훈련한 자극', { dx: 0, dy: -8, anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(f.label([1.9, wide(1.9)], '일반화만 한 경우', { dx: 10, dy: -6, cls: 'ink', size: 'sm' }));
    g.push(f.label([0.95, narrow(0.95)], '변별 훈련 뒤', { dx: 10, dy: 14, cls: 'ink', size: 'sm' }));

    g.push(txt(f.X(0), 320, '자극이 훈련한 것과 얼마나 다른가 (0 이 훈련한 자극)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(34, 70, '반응의 크기', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 34, '일반화는 저절로, 변별은 훈련으로 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 358, '변별 훈련은 훈련한 자극에만 무조건자극을 붙이고 이웃 자극에는 붙이지 않는 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-l-gradient',
        svg: svg({ width: W, height: H, title: '일반화 기울기', desc: '훈련 자극을 중심으로 퍼진 반응 곡선과 변별 훈련 뒤 좁아진 곡선', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-l-schedules — 네 강화계획의 누적 반응 기록.
 *
 * 무엇을 읽어야 하나: 세로로 얼마나 가파른가(비율이 간격보다 가파르다)와
 * 선이 매끄러운가 울퉁불퉁한가(고정이 변동보다 멈춤이 많다). 두 축이 다르다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 430;
    const g = [];
    const f = frame({ xRange: [0, 60], yRange: [0, 260], box: { x: 96, y: 96, w: 560, h: 244 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));

    // 변동비율 — 멈춤 없이 가장 가파르다.
    const vr = [];
    for (let t = 0; t <= 60; t += 2) vr.push([t, t * 4.1]);
    // 고정비율 — 가파르되 강화 뒤 짧게 멈춘다(계단).
    const fr = [];
    {
        let y = 0;
        for (let t = 0; t <= 60; t += 1) {
            const phase = t % 10;
            y += phase < 2 ? 0.3 : 4.4;
            fr.push([t, y * 0.78]);
        }
    }
    // 고정간격 — 간격 끝으로 갈수록 빨라지는 부채꼴.
    const fi = [];
    {
        let y = 0;
        for (let t = 0; t <= 60; t += 1) {
            const phase = t % 12;
            y += 0.25 + 2.6 * (phase / 12) ** 2.2;
            fi.push([t, y * 1.7]);
        }
    }
    // 변동간격 — 낮고 고르다.
    const vi = [];
    for (let t = 0; t <= 60; t += 2) vi.push([t, t * 1.05]);

    g.push(f.line(vr, { cls: 's1' }));
    g.push(f.line(fr, { cls: 's2' }));
    g.push(f.line(fi, { cls: 's3' }));
    g.push(f.line(vi, { cls: 'ax', dash: '7 4' }));

    g.push(f.label(vr[vr.length - 1], '변동비율', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));
    g.push(f.label(fr[fr.length - 1], '고정비율', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));
    g.push(f.label(fi[fi.length - 1], '고정간격', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));
    g.push(f.label(vi[vi.length - 1], '변동간격', { dx: 8, dy: 4, cls: 'ink bold', size: 'sm' }));

    g.push(txt(f.X(30), 366, '시간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 84, '세로축 — 그때까지 한 반응의 총수를 쌓아 그린다 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(96, 384, 700, 32, { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(112, 405, '비율 계획이 간격 계획보다 가파르다. 고정 계획에서만 선이 울퉁불퉁하다 — 강화 직후에 멈추기 때문이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '누적 기록 — 네 계획의 모양 (도식)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 58, '선이 가파를수록 그 순간 반응이 빠르다. 평평한 구간은 반응하지 않은 구간이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-l-schedules',
        svg: svg({ width: W, height: H, title: '강화계획별 누적 반응 기록', desc: '변동비율이 가장 가파르고 고정간격이 부채꼴 모양을 그리는 도식', body: g.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * psy-l-contingency — 짝지은 횟수가 같아도 조건형성이 달라진다.
 *
 * 무엇을 읽어야 하나: 가로축이 ‘조건자극이 없을 때 무조건자극이 올 확률’ 이고,
 * 그 값이 조건자극이 있을 때의 확률에 다가갈수록 조건반응이 사라진다는 것.
 * 짝지음(인접성)이 아니라 예측력(수반성)이 조건형성을 만든다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 400;
    const g = [];
    const f = frame({ xRange: [0, 0.4], yRange: [0, 1], box: { x: 110, y: 80, w: 470, h: 220 } });

    g.push(f.axes({ xTicks: [0, 0.1, 0.2, 0.3, 0.4], yTicks: [], grid: true }));

    const pts = [];
    for (let p = 0; p <= 0.4; p += 0.01) pts.push([p, Math.max(0.02, 0.95 * (1 - p / 0.4) ** 1.35)]);
    g.push(f.line(pts, { cls: 's1' }));

    g.push(f.line([[0.4, 0], [0.4, 1]], { cls: 'ax', dash: '4 3' }));
    g.push(f.label([0.4, 0.72], '두 확률이 같아지는 자리', { dx: -8, dy: 0, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(f.label([0.4, 0.6], '조건자극이 아무것도 알려 주지 않는다', { dx: -8, dy: 0, anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(f.dot([0, 0.95], { cls: 'f2', r: 6 }));
    g.push(f.label([0, 0.95], '조건자극이 있을 때만 무조건자극이 온다', { dx: 16, dy: 36, cls: 'ink', size: 'sm' }));

    g.push(txt(f.X(0.2), 332, '조건자극이 없을 때 무조건자극이 올 확률', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 64, '세로축 — 조건반응의 크기 (눈금 없음)', { cls: 'ink2', size: 'sm' }));

    g.push(box(602, 84, 236, 158, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(618, 110, '고정해 둔 것', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(618, 134, '조건자극이 있을 때 무조건자극이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(618, 152, '올 확률은 0.4 로 붙박아 둔다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(618, 176, '즉 어느 조건에서나 조건자극과', { cls: 'ink2', size: 'sm' }));
    g.push(txt(618, 194, '무조건자극이 짝지어진 비율은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(618, 212, '같다. 달라지는 것은 조건자극', { cls: 'ink2', size: 'sm' }));
    g.push(txt(618, 230, '없이 오는 몫뿐이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '짝지음이 같아도 예측력이 없으면 배우지 않는다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 372, '레스콜라(1968)가 보고한 방향을 옮긴 도식이다. 값이 아니라 내려가는 모양만 읽는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return {
        name: 'psy-l-contingency',
        svg: svg({ width: W, height: H, title: '수반성과 조건형성', desc: '조건자극 없이 무조건자극이 오는 비율이 올라갈수록 조건반응이 사라지는 도식', body: g.join('') }),
    };
})());

export default figures;
