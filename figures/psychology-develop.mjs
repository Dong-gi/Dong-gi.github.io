/**
 * 심리학 문서 15장(발달)의 그림.
 *
 * 이름은 모두 `psy-d-` 로 시작한다(15장 담당자에게 배정된 접두어).
 *
 * SVG 안에는 수식을 쓸 수 없고(그림이 <img> 로 들어가 MathJax 가 닿지 않는다),
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 쓰지 않는다.
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓰고, HTML 엔티티도 쓸 수 없다.
 *
 * 자료 규칙 — 이 파일에는 남의 원자료를 옮긴 그림이 하나도 없다. 흩어진 점은
 * 씨앗을 고정한 난수로 만든 도식이고, 곡선은 여러 연구가 보고한 모양의
 * 방향만 옮긴 도식이다. 문서의 캡션에도 그렇게 적었다.
 *
 * 상자와 화살표만으로 되는 그림(설계 비교·단계 표·분류 체계)은
 * d2/psychology/psy-d-*.d2 에 있다.
 */
import { svg, frame, txt, px } from './lib.mjs';

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

const pdot = (x, y, col = COL.s1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

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

/* ------------------------------------------------------------------ *
 * psy-d-design-grid — 출생 연도 × 측정 연도 표.
 *
 * 무엇을 읽어야 하나: 한 칸을 정하는 것이 셋(나이 · 출생 연도 · 측정 연도)
 * 인데 그중 둘만 자유롭다는 것. 그래서 어느 설계를 골라도 나머지 하나가
 * 나이와 붙어 다닌다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 880;
    const H = 430;
    const g = [];
    const cols = [2000, 2010, 2020];
    const rows = [1950, 1970, 1990];
    const x0 = 210;
    const y0 = 118;
    const cw = 178;
    const ch = 74;

    g.push(txt(x0 + cw * 1.5, 46, '측정 연도', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(38, y0 + ch * 1.5 + 5, '출생 연도', { cls: 'ink bold' }));

    // 세로로 한 줄 — 횡단 설계가 잡는 자리.
    g.push(box(x0 + cw * 2 - 6, y0 - 12, cw + 12, ch * 3 + 24, { stroke: COL.s1, sw: 2.4, dash: '6 4' }));
    // 가로로 한 줄 — 종단 설계가 잡는 자리.
    g.push(box(x0 - 8, y0 + ch - 8, cw * 3 + 16, ch + 16, { stroke: COL.s2, sw: 2.4, dash: '6 4' }));

    for (let c = 0; c < cols.length; c += 1) {
        g.push(txt(x0 + cw * c + cw / 2, 92, String(cols[c]), { anchor: 'middle', cls: 'ink2' }));
    }
    for (let r = 0; r < rows.length; r += 1) {
        g.push(txt(196, y0 + ch * r + ch / 2 + 5, String(rows[r]), { anchor: 'end', cls: 'ink2' }));
        for (let c = 0; c < cols.length; c += 1) {
            const age = cols[c] - rows[r];
            g.push(box(x0 + cw * c, y0 + ch * r, cw - 8, ch - 8, { stroke: COL.grid, sw: 1.2 }));
            g.push(txt(x0 + cw * c + (cw - 8) / 2, y0 + ch * r + 44, age + '세', { anchor: 'middle', cls: 'ink bold' }));
        }
    }

    // 같은 나이가 두 번 나오는 자리 — 계열 설계가 견주는 두 칸.
    const mark = (r, c) => `<circle cx="${r2(x0 + cw * c + (cw - 8) / 2)}" cy="${r2(y0 + ch * r + 38)}" r="30" fill="none" stroke="${COL.s3}" stroke-width="2.4"/>`;
    g.push(mark(0, 0));
    g.push(mark(1, 2));

    g.push(txt(x0 + cw * 2 + cw / 2 - 4, y0 + ch * 3 + 34, '횡단 — 한 시점에 나이가 다른 사람', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + cw * 3 + 24, y0 + ch + ch / 2, '종단', { cls: 'ink2', size: 'sm' }));
    g.push(txt(60, 396, '초록 동그라미 두 칸은 나이가 같고 출생 연도와 측정 연도가 다르다. 계열 설계가 견주는 자리다', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-d-design-grid', svg: svg({ width: W, height: H, title: '출생 연도와 측정 연도로 만든 표', desc: '세로 한 줄은 횡단, 가로 한 줄은 종단, 같은 나이가 나오는 두 칸은 계열 설계가 견주는 자리다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-d-attrition — 종단 연구의 탈락.
 *
 * 무엇을 읽어야 하나: 남은 사람들의 평균이 올라간 것이 ‘사람이 좋아진 것’ 이
 * 아니라 ‘낮은 쪽이 빠진 것’ 일 수 있다는 것. 점은 씨앗 고정 난수다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 400;
    const g = [];
    const rand = rng(1509);
    const top = 70;
    const bot = 330;
    const val = v => bot - (v / 10) * (bot - top);

    g.push(ln([[130, top - 16], [130, bot + 12]], { stroke: COL.ink2, sw: 1.4 }));
    g.push(txt(118, top - 24, '점수', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(118, top + 6, '높다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(118, bot, '낮다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const people = [];
    for (let i = 0; i < 46; i += 1) {
        const v = Math.max(0.4, Math.min(9.6, 5 + gauss(rand) * 1.9));
        people.push(v);
    }
    const jit = i => ((i * 37) % 11) - 5;

    // 왼쪽 — 처음 표본 전부.
    people.forEach((v, i) => g.push(pdot(230 + jit(i) * 4.4, val(v), COL.s1, 4)));
    const m0 = people.reduce((a, b) => a + b, 0) / people.length;
    g.push(ln([[186, val(m0)], [286, val(m0)]], { stroke: COL.ink, sw: 2.4 }));
    g.push(txt(292, val(m0) + 5, '평균', { cls: 'ink bold', size: 'sm' }));

    // 오른쪽 — 낮은 쪽이 더 많이 빠진 뒤 남은 사람.
    const stay = people.filter((v, i) => v > 4.4 || (i % 5 === 0 && v > 3.2));
    stay.forEach((v, i) => g.push(pdot(600 + jit(i) * 4.4, val(v), COL.s3, 4)));
    const m1 = stay.reduce((a, b) => a + b, 0) / stay.length;
    g.push(ln([[556, val(m1)], [656, val(m1)]], { stroke: COL.ink, sw: 2.4 }));
    g.push(txt(662, val(m1) + 5, '평균', { cls: 'ink bold', size: 'sm' }));

    // 빠진 사람.
    const gone = people.filter(v => !(v > 4.4));
    gone.slice(0, 12).forEach((v, i) => g.push(pdot(420 + jit(i) * 3.2, val(v), COL.grid, 3.4)));
    g.push(txt(420, val(1.1), '중간에 빠진 사람', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(px(300, 190, 400, 190, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(px(452, 190, 552, 190, { cls: 's2', marker: 'ar2', width: 2 }));

    g.push(txt(230, 360, '첫 측정 — 모두 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(618, 360, '몇 해 뒤 — 남은 사람만', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(60, 388, '평균이 올라갔다. 그런데 개인의 점수는 아무도 오르지 않았다', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-d-attrition', svg: svg({ width: W, height: H, title: '종단 연구에서 탈락이 평균을 움직인다', desc: '낮은 점수 쪽이 더 많이 빠지면 남은 사람의 평균이 올라간다. 개인은 아무도 변하지 않았다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-d-attach-dim — 두 차원 위의 아이들과 그 위에 그은 네 칸.
 *
 * 무엇을 읽어야 하나: 점이 네 칸 안에 뭉쳐 있지 않다는 것. 경계 가까이에
 * 있는 아이가 어느 유형이 되는지는 아주 작은 차이가 정한다. 점은 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 800;
    const H = 460;
    const g = [];
    const rand = rng(770412);
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 130, y: 60, w: 400, h: 320 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 424, '분리 때 불편해하는 정도', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 46, '재회 때 달래져 다시 노는 정도', { cls: 'ink2', size: 'sm' }));

    // 네 칸으로 자르는 경계선.
    g.push(ln([[f.X(5), f.Y(0)], [f.X(5), f.Y(10)]], { stroke: COL.s2, sw: 2, dash: '6 4' }));
    g.push(ln([[f.X(0), f.Y(5)], [f.X(10), f.Y(5)]], { stroke: COL.s2, sw: 2, dash: '6 4' }));

    for (let i = 0; i < 120; i += 1) {
        const x = Math.max(0.3, Math.min(9.7, 5.4 + gauss(rand) * 2.1));
        const y = Math.max(0.3, Math.min(9.7, 5.9 + gauss(rand) * 2.0 - (x - 5) * 0.25));
        g.push(f.dot([x, y], { cls: 'f1', r: 3 }));
    }

    g.push(f.label([0.4, 9.4], '가', { cls: 'ink bold' }));
    g.push(f.label([9.5, 9.4], '나', { cls: 'ink bold', anchor: 'end' }));
    g.push(f.label([0.4, 0.5], '다', { cls: 'ink bold' }));
    g.push(f.label([9.5, 0.5], '라', { cls: 'ink bold', anchor: 'end' }));

    g.push(txt(566, 96, '네 칸은 나중에 그은 것이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(566, 122, '점이 칸마다 뭉쳐 있지 않다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 142, '한 덩어리로 퍼져 있고 그 위에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 162, '선을 그은 모양이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 196, '그래서 경계 가까이의 아이는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 216, '다시 재면 칸이 바뀔 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 250, '점은 씨앗을 고정한 난수로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 270, '만든 도식이다. 실제 자료가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(566, 290, '아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-d-attach-dim', svg: svg({ width: W, height: H, title: '두 차원 위에 흩어진 아이들과 그 위에 그은 네 칸', desc: '점이 칸마다 뭉쳐 있지 않고 한 덩어리로 퍼져 있다. 경계는 나중에 그은 것이다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-d-aging — 같은 능력을 횡단으로 잰 모양과 종단으로 잰 모양.
 *
 * 무엇을 읽어야 하나: 두 곡선이 다르다는 것. 세로축에 눈금을 일부러 넣지
 * 않았다. 이 그림은 여러 종단 연구가 보고한 방향만 옮긴 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [25, 85], yRange: [0, 10], box: { x: 110, y: 66, w: 480, h: 264 } });

    g.push(f.axes({ xTicks: [25, 40, 55, 70, 85], yTicks: [], grid: false }));
    g.push(txt(f.X(55), 374, '나이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 52, '검사 점수', { cls: 'ink2', size: 'sm' }));

    // 횡단 — 젊은 쪽이 계속 높다.
    g.push(f.curve(a => 8.6 - (a - 25) * 0.072, { cls: 's1' }));
    // 종단 — 같은 사람을 따라가면 오래 유지되다가 늦게 내려간다.
    g.push(f.curve(a => (a <= 58 ? 7.5 + (a - 25) * 0.012 : 7.9 - Math.pow(a - 58, 2) * 0.004527), { cls: 's2' }));

    g.push(f.label([71, 5.15], '횡단', { cls: 'ink bold', anchor: 'middle', dy: 22 }));
    g.push(f.label([62, 7.83], '종단', { cls: 'ink bold', anchor: 'middle', dy: -12 }));

    g.push(txt(624, 108, '두 곡선이 갈리는 까닭', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(624, 134, '횡단에서 80세와 30세는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 154, '나이만 다른 것이 아니라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 174, '자란 시대가 다르다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 208, '종단에서는 시대가 같지만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 228, '탈락과 연습이 끼어든다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 262, '세로축 눈금은 일부러', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 282, '넣지 않았다. 모양만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(624, 302, '옮긴 도식이다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-d-aging', svg: svg({ width: W, height: H, title: '같은 능력을 횡단으로 잰 곡선과 종단으로 잰 곡선', desc: '횡단은 이른 나이부터 꾸준히 내려가고 종단은 오래 유지되다 늦게 내려간다. 눈금 없는 도식이다', body: g.join('\n') }) };
})());

export default figures;
