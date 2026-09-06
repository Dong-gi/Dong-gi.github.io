/**
 * 일반화학 9장(열화학) ~ 12장(화학 평형)의 그림.
 *
 * chemistry-basic.mjs / chemistry-reactions.mjs 와 같은 형식이다. 각 항목은
 * { name, title, desc, svg } 를 돌려주고 name 이 파일 이름(/figures/chemistry/<name>.svg)이 된다.
 * 다른 모듈과 이름이 겹치지 않게 chem-thermo- / chem-soln- / chem-eq- 접두어만 쓴다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 화학식은 유니코드 아래첨자(H₂O)와 위첨자(Na⁺)로 적고, 유니코드에 없는 아래첨자는
 * lib 의 esc 규칙 `k~H`, `ΔS~{주위}` 로 적는다.
 */
import { svg, frame, px, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.14, stroke = 'none', sw = 1 } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 회색(ink2) 화살표. px() 는 색 계열(s1/s2/s3)만 쓰므로 따로 둔다. */
function arrowK(x1, y1, x2, y2, { width = 1.5, dash } = {}) {
    return `<path fill="none" stroke="var(--ink2)" stroke-width="${width}" stroke-linecap="round"`
        + ` marker-end="url(#ark)"${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ` d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 패널 제목 + 테두리. */
function panel(x, y, w, h, title, { sub } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 21, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 38, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 에너지 준위 하나(가로 막대 + 이름). */
function level(x1, x2, y, label, { anchor = 'start', dy = -9, cls = 'ink', sw = 2.4, stroke = 'var(--ink)' } = {}) {
    const lx = anchor === 'end' ? x2 : anchor === 'middle' ? (x1 + x2) / 2 : x1;
    return line([[x1, y], [x2, y]], { stroke, sw, cap: 'butt' })
        + txt(lx, y + dy, label, { anchor, cls });
}

/** 비커 하나. 액체 높이 fillH(화소). */
function beaker(x, y, w, h, fillH, { cls = 's1', op = 0.12 } = {}) {
    return box(x + 2, y + h - fillH, w - 4, fillH - 2, { fill: `var(--${cls})`, op, stroke: 'none', sw: 0, rx: 2 })
        + box(x, y, w, h, { stroke: 'var(--ink2)', sw: 1.8, rx: 4 });
}

/* ================================================================== *
 * 9장 — 열화학
 * ================================================================== */

/* 9-1. 계와 주위, 부호 규약 */
add((() => {
    const W = 760, H = 400;
    const g = [];
    g.push(txt(26, 36, '계와 주위 — 에너지가 드나드는 두 통로', { cls: 'ink bold' }));
    g.push(box(40, 62, 680, 268, { stroke: 'var(--grid)', sw: 1.4, rx: 8, dash: '7 5' }));
    g.push(txt(56, 84, '주위 (surroundings) — 계 밖의 모든 것', { cls: 'ink2', size: 'sm' }));

    g.push(box(300, 148, 160, 112, { fill: 'var(--s1)', op: 0.12, stroke: 'var(--s1)', sw: 1.8, rx: 6 }));
    g.push(txt(380, 184, '계 (system)', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 208, '지금 관심 있는 부분', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(380, 236, '내부에너지 U', { anchor: 'middle', cls: 'ink' }));

    // 왼쪽 — 열
    g.push(txt(221, 128, '열 q', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(148, 172, 292, 172, { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(221, 164, 'q > 0   흡열', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(221, 192, '계가 열을 받았다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(px(292, 226, 148, 226, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(txt(221, 218, 'q < 0   발열', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(221, 246, '계가 열을 내놓았다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 일
    g.push(txt(539, 128, '일 w', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(612, 172, 468, 172, { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(539, 164, 'w > 0   압축', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(539, 192, '주위가 계에 일을 했다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(px(468, 226, 612, 226, { cls: 's1', marker: 'ar1', width: 2.2 }));
    g.push(txt(539, 218, 'w < 0   팽창', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(539, 246, '계가 주위에 일을 했다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(380, 302, '제1법칙   ΔU = q + w', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(380, 366, '부호는 언제나 계를 기준으로 정한다. 계로 들어오면 +, 계에서 나가면 −',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-system-surroundings',
        title: '계와 주위, 그리고 열과 일의 부호 규약',
        desc: '우주를 계와 주위 둘로 나눈다. 에너지는 열 q 와 일 w 두 통로로만 오간다. '
            + '부호는 언제나 계를 기준으로 정하며, 계로 들어오면 양수, 계에서 나가면 음수다. '
            + '둘을 더한 것이 계의 내부에너지 변화 ΔU 이고 이것이 열역학 제1법칙이다.',
        svg: svg({ width: W, height: H, title: '계와 주위', desc: '계 상자와 열·일 화살표', body: g.join('') }),
    };
})());

/* 9-2. 팽창 일 = P ΔV */
add((() => {
    const W = 760, H = 400;
    const g = [];
    g.push(txt(26, 36, '팽창하는 기체는 주위를 밀어낸다 — 그 밀어냄이 일이다', { cls: 'ink bold' }));

    const cyl = (x, gasTop, label, sub) => {
        const out = [];
        out.push(box(x + 3, gasTop, 90, 285 - gasTop, { fill: 'var(--s1)', op: 0.18, stroke: 'none', sw: 0, rx: 2 }));
        out.push(box(x, 92, 96, 196, { stroke: 'var(--ink2)', sw: 1.8, rx: 4 }));
        out.push(box(x + 3, gasTop - 11, 90, 11, { fill: 'var(--ink2)', op: 0.45, stroke: 'var(--ink2)', sw: 1, rx: 2 }));
        out.push(txt(x + 48, 310, label, { anchor: 'middle', cls: 'ink' }));
        out.push(txt(x + 48, 330, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(cyl(66, 206, '처음', 'V₁ = 5.0 L'));
    g.push(cyl(212, 146, '나중', 'V₂ = 15.0 L'));
    g.push(px(180, 200, 180, 146, { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(186, 176, '피스톤이 밀려 올라간다', { cls: 'ink2', size: 'sm' }));
    g.push(arrowK(114, 62, 114, 88, { width: 1.4 }));
    g.push(arrowK(260, 62, 260, 88, { width: 1.4 }));
    g.push(txt(187, 54, '바깥 압력 P = 100 kPa (일정)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const f = frame({ xRange: [0, 18], yRange: [0, 140], box: { x: 430, y: 100, w: 240, h: 175 } });
    g.push(f.axes({ xLabel: 'V (L)', yLabel: 'P (kPa)', xTicks: [0, 5, 10, 15], yTicks: [0, 50, 100] }));
    g.push(poly([[f.X(5), f.Y(0)], [f.X(5), f.Y(100)], [f.X(15), f.Y(100)], [f.X(15), f.Y(0)]],
        { fill: 'var(--s2)', op: 0.22 }));
    g.push(f.line([[0, 100], [17.4, 100]], { cls: 's2' }));
    g.push(f.label([10, 60], '넓이 = P ΔV', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.label([10, 32], '= 1000 J', { anchor: 'middle', cls: 'ink' }));
    g.push(f.label([5, 112], 'V₁', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.label([15, 112], 'V₂', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(550, 312, '계가 한 일이므로  w = −P ΔV = −1000 J', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W - 16, H - 14, '1 kPa · L = 1 J 이므로 환산 계수가 따로 필요 없다',
        { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-pv-work',
        title: '일정한 바깥 압력에서의 팽창 일',
        desc: '바깥 압력이 일정할 때 기체가 팽창하면, 계가 주위에 한 일은 P–V 그래프에서 압력선 아래의 직사각형 '
            + '넓이와 같다. 압력 100 kPa 에서 부피가 5.0 L 에서 15.0 L 로 늘면 넓이는 1000 kPa·L 이고, '
            + '1 kPa·L = 1 J 이므로 곧 1000 J 이다. 계가 내보낸 일이므로 부호를 붙여 w = −1000 J 로 적는다.',
        svg: svg({ width: W, height: H, title: '팽창 일', desc: '실린더 두 개와 P–V 그래프', body: g.join('') }),
    };
})());

/* 9-3. 발열과 흡열의 엔탈피 준위 */
add((() => {
    const W = 720, H = 380;
    const g = [];
    g.push(txt(26, 34, 'ΔH 의 부호는 그림에서 ‘내려가느냐 올라가느냐’다', { cls: 'ink bold' }));

    const drawPanel = (x, up, title, sub, note) => {
        const out = [panel(x, 56, 320, 244, title, { sub })];
        const yHi = 118, yLo = 240;
        const yR = up ? yLo : yHi;   // 반응물
        const yP = up ? yHi : yLo;   // 생성물
        out.push(level(x + 34, x + 138, yR, '반응물', { anchor: 'start', cls: 'ink' }));
        out.push(level(x + 186, x + 290, yP, '생성물', { anchor: 'end', cls: 'ink' }));
        out.push(line([[x + 138, yR], [x + 162, yR]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        out.push(line([[x + 162, yP], [x + 186, yP]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        out.push(px(x + 162, yR, x + 162, yP, { cls: up ? 's2' : 's1', marker: up ? 'ar2' : 'ar1', width: 2.4 }));
        out.push(txt(x + 172, (yHi + yLo) / 2 - 4, up ? 'ΔH > 0' : 'ΔH < 0', { cls: 'ink bold' }));
        out.push(txt(x + 172, (yHi + yLo) / 2 + 16, up ? '흡열' : '발열', { cls: 'ink2', size: 'sm' }));
        out.push(arrowK(x + 20, 250, x + 20, 108, { width: 1.3 }));
        out.push(txt(x + 14, 100, 'H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(x + 160, 288, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(drawPanel(30, false, '발열 반응', '계의 엔탈피가 줄어든다', '줄어든 만큼 주위로 나가 주위가 뜨거워진다'));
    g.push(drawPanel(370, true, '흡열 반응', '계의 엔탈피가 늘어난다', '늘어난 만큼 주위에서 빼앗아 주위가 차가워진다'));
    g.push(txt(360, 336, '세로축은 엔탈피다. 가로축은 눈금이 아니라 ‘반응 전 → 반응 후’라는 순서일 뿐이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 360, 'ΔH 는 두 준위의 높이 차이이고, 부호는 어느 쪽이 위인가가 정한다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-enthalpy-levels',
        title: '발열 반응과 흡열 반응의 엔탈피 준위',
        desc: '반응물과 생성물의 엔탈피를 높이로 그리면 ΔH 의 부호가 눈에 보인다. 생성물이 낮으면 ΔH 가 음수인 '
            + '발열 반응이고 그만큼의 에너지가 주위로 나가 주위가 뜨거워진다. 생성물이 높으면 ΔH 가 양수인 '
            + '흡열 반응이고 주위에서 그만큼 빼앗으므로 주위가 차가워진다.',
        svg: svg({ width: W, height: H, title: '엔탈피 준위 그림', desc: '발열과 흡열의 준위 비교', body: g.join('') }),
    };
})());

/* 9-4. 가열 곡선 — 상변화 구간에서는 온도가 오르지 않는다 */
add((() => {
    const W = 720, H = 400;
    const seg = [[0, -25], [0.94, 0], [6.95, 0], [14.49, 100], [55.19, 100], [56.03, 125]];
    const f = frame({ xRange: [0, 60], yRange: [-40, 140], box: { x: 84, y: 62, w: 520, h: 254 } });
    const g = [
        txt(26, 34, '물 1 mol 에 열을 넣으면서 온도를 잰다', { cls: 'ink bold' }),
        f.axes({
            xLabel: '넣은 열 (kJ)', yLabel: '온도 (°C)',
            xTicks: [0, 10, 20, 30, 40, 50], yTicks: [-25, 0, 25, 50, 75, 100, 125],
        }),
        f.guide([14.49, 0], [14.49, 100]),
        f.guide([55.19, 0], [55.19, 100]),
        f.line(seg, { cls: 's1' }),
    ];
    g.push(f.label([2.4, -26], '얼음', { cls: 'ink', size: 'sm' }));
    g.push(f.label([3.9, 12], '녹는 중', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.label([3.9, -12], '6.0 kJ', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.label([8.2, 74], '물', { cls: 'ink' }));
    g.push(f.label([34.8, 112], '끓는 중', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.label([34.8, 86], '40.7 kJ', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.label([57, 130], '수증기', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(f.dot([0.94, 0], { cls: 'f1', r: 3 }));
    g.push(f.dot([6.95, 0], { cls: 'f1', r: 3 }));
    g.push(f.dot([14.49, 100], { cls: 'f1', r: 3 }));
    g.push(f.dot([55.19, 100], { cls: 'f1', r: 3 }));
    g.push(txt(360, 356, '평평한 두 구간에서는 열을 넣어도 온도가 오르지 않는다. 그 열은 전부 분자를 떼어놓는 데 쓰인다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 380, '기화에 드는 열이 융해의 약 7배다. 땀이 식혀 주는 효과가 이만큼 크다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-heating-curve',
        title: '물의 가열 곡선 — 상변화 구간의 평평한 부분',
        desc: '얼음 1 mol 에 열을 꾸준히 넣으면 온도가 오르다가 0 °C 와 100 °C 에서 멈춘다. 그 구간에서 들어간 '
            + '열은 온도를 올리는 데 쓰이지 않고 분자 사이를 떼어놓는 데 전부 쓰인다. 그래서 그 구간에서는 '
            + 'q = mcΔT 를 쓸 수 없고 융해열과 기화열을 따로 세어야 한다. 기화열이 융해열의 약 7배로 훨씬 크다.',
        svg: svg({ width: W, height: H, title: '가열 곡선', desc: '넣은 열에 대한 물의 온도 변화', body: g.join('') }),
    };
})());

/* 9-5. 열량계 두 가지 */
add((() => {
    const W = 720, H = 390;
    const g = [];
    g.push(txt(26, 34, '무엇을 재느냐에 따라 그릇이 달라진다', { cls: 'ink bold' }));

    // 커피컵 열량계
    g.push(panel(30, 56, 320, 268, '커피컵 열량계', { sub: '뚜껑이 헐거워 대기에 열려 있다 — 압력이 일정' }));
    g.push(beaker(120, 130, 140, 130, 96, { cls: 's1', op: 0.16 }));
    g.push(beaker(106, 118, 168, 148, 0, { cls: 's1', op: 0 }));
    g.push(line([[190, 106], [190, 176]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(circ(190, 182, 7, { fill: 'var(--s2)', op: 0.9, stroke: 'var(--s2)', sw: 1 }));
    g.push(txt(200, 108, '온도계', { cls: 'ink2', size: 'sm' }));
    g.push(line([[146, 112], [146, 200], [166, 214]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(txt(96, 108, '젓개', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arrowK(126, 104, 143, 110, { width: 1.2 }));
    g.push(txt(190, 284, '재는 값이 곧 q~p = ΔH', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(190, 306, '중화열·용해열처럼 용액에서 하는 반응', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 봄베 열량계
    g.push(panel(370, 56, 320, 268, '봄베 열량계', { sub: '두꺼운 강철 통 — 부피가 고정된다' }));
    g.push(beaker(430, 118, 200, 160, 128, { cls: 's1', op: 0.12 }));
    g.push(box(474, 168, 112, 84, { fill: 'var(--s2)', op: 0.14, stroke: 'var(--ink2)', sw: 3, rx: 8 }));
    g.push(txt(530, 202, '시료 + O₂', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(530, 222, '(강철 통)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(line([[530, 168], [530, 132], [560, 132]], { stroke: 'var(--s2)', sw: 1.6 }));
    g.push(txt(566, 128, '점화선', { cls: 'ink2', size: 'sm' }));
    g.push(line([[464, 106], [464, 176]], { stroke: 'var(--s2)', sw: 2.4 }));
    g.push(circ(464, 182, 7, { fill: 'var(--s2)', op: 0.9, stroke: 'var(--s2)', sw: 1 }));
    g.push(txt(452, 108, '온도계', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(530, 284, '재는 값이 q~V = ΔU', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(530, 306, '연소열처럼 기체가 크게 드나드는 반응', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(360, 356, '어느 쪽이든 원리는 하나다. 계가 잃은 열 = 물과 그릇이 얻은 열',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 378, '두 값이 다른 것이 아니라, 정압에서 잰 것이 ΔH 이고 정적에서 잰 것이 ΔU 다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-calorimeters',
        title: '커피컵 열량계와 봄베 열량계',
        desc: '커피컵 열량계는 대기에 열려 있어 압력이 일정하므로 잰 열이 곧 ΔH 다. 봄베 열량계는 두꺼운 강철 통 '
            + '안에서 반응시켜 부피가 고정되므로 잰 열이 ΔU 다. 어느 쪽이든 계가 잃은 열이 물과 그릇이 얻은 열과 '
            + '같다는 원리는 같고, 다른 것은 무엇이 일정하게 유지되는가뿐이다.',
        svg: svg({ width: W, height: H, title: '두 종류의 열량계', desc: '커피컵 열량계와 봄베 열량계의 구조', body: g.join('') }),
    };
})());

/* 9-6. 헤스의 법칙 */
add((() => {
    const W = 700, H = 390;
    const g = [];
    g.push(txt(26, 34, '엔탈피는 상태함수다 — 어느 길로 가도 낙차는 같다', { cls: 'ink bold' }));
    g.push(arrowK(64, 300, 64, 92, { width: 1.3 }));
    g.push(txt(58, 84, 'H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const yTop = 108, yMid = 162, yBot = 300;
    g.push(level(120, 600, yTop, 'C(흑연) + O₂(g)', { anchor: 'start', dy: -10 }));
    g.push(level(330, 600, yMid, 'CO(g) + ½O₂(g)', { anchor: 'start', dy: -10 }));
    g.push(level(120, 600, yBot, 'CO₂(g)', { anchor: 'start', dy: 22 }));

    g.push(px(178, yTop, 178, yBot, { cls: 's1', marker: 'ar1', width: 2.4 }));
    g.push(txt(170, 206, '직접 가는 길', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(170, 228, '−393.5 kJ', { anchor: 'end', cls: 'ink' }));

    g.push(px(430, yTop, 430, yMid, { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(txt(440, 140, '1단계 — 구하려는 값', { cls: 'ink bold' }));
    g.push(px(430, yMid, 430, yBot, { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(txt(440, 236, '2단계  −283.0 kJ', { cls: 'ink' }));

    g.push(txt(350, 344, '두 길의 총 낙차가 같아야 하므로   ? + (−283.0) = −393.5',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 368, '? = −110.5 kJ — 직접 재기 어려운 값을 재기 쉬운 두 값으로 얻는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-hess-cycle',
        title: '헤스의 법칙 — 두 경로의 낙차가 같다',
        desc: '흑연을 태워 CO₂ 로 가는 길은 두 가지다. 곧장 가는 길과 CO 를 거쳐 가는 길. 엔탈피가 상태함수이므로 '
            + '두 길의 총 낙차가 같아야 한다. 이 성질을 이용하면 직접 재기 어려운 CO 의 생성엔탈피를 재기 쉬운 '
            + '두 연소열의 차이로 얻을 수 있다.',
        svg: svg({ width: W, height: H, title: '헤스의 법칙', desc: '두 경로를 비교한 엔탈피 준위 그림', body: g.join('') }),
    };
})());

/* 9-7. 결합엔탈피로 어림하기 */
add((() => {
    const W = 700, H = 380;
    const g = [];
    g.push(txt(26, 34, '결합엔탈피 계산은 ‘다 끊었다가 다시 붙이는’ 우회로다', { cls: 'ink bold' }));
    g.push(arrowK(64, 300, 64, 84, { width: 1.3 }));
    g.push(txt(58, 76, 'H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const yAtom = 96, yR = 252, yP = 300;
    g.push(level(240, 470, yAtom, 'H + H + Cl + Cl   (원자로 다 흩어진 상태)', { anchor: 'middle', dy: -10 }));
    g.push(level(110, 240, yR, 'H₂(g) + Cl₂(g)', { anchor: 'start', dy: -10 }));
    g.push(level(470, 640, yP, '2 HCl(g)', { anchor: 'end', dy: 22 }));

    g.push(px(196, yR, 196, yAtom, { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(txt(188, 162, '끊는다 (흡열)', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(188, 184, '+678 kJ', { anchor: 'end', cls: 'ink' }));

    g.push(px(546, yAtom, 546, yP, { cls: 's1', marker: 'ar1', width: 2.4 }));
    g.push(txt(556, 176, '만든다 (발열)', { cls: 'ink bold' }));
    g.push(txt(556, 198, '−862 kJ', { cls: 'ink' }));

    g.push(line([[240, yR], [430, yR]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(px(430, yR, 430, yP, { cls: 's3', marker: 'ar3', width: 2.4 }));
    g.push(txt(440, 284, '알짜 ΔH ≈ −184 kJ', { cls: 'ink bold' }));

    g.push(txt(350, 344, '끊는 데 든 것에서 만들며 나온 것을 뺀다.  ΔH ≈ 678 − 862 = −184 kJ',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 368, '표의 결합엔탈피가 여러 화합물의 평균값이므로 이 계산은 어디까지나 어림이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-bond-enthalpy',
        title: '결합엔탈피로 반응 엔탈피를 어림하는 경로',
        desc: '반응물의 결합을 모두 끊어 원자 상태로 올린 뒤 생성물의 결합을 만들며 내려온다고 상상한다. '
            + '올라가는 데 든 에너지(끊는 결합의 합)에서 내려오며 나온 에너지(만드는 결합의 합)를 빼면 ΔH 의 '
            + '어림값이 나온다. 표의 결합엔탈피가 평균값이므로 결과는 근사이며 보통 수십 kJ 의 오차가 있다.',
        svg: svg({ width: W, height: H, title: '결합엔탈피 경로', desc: '원자 상태를 거치는 우회 경로', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 10장 — 화학 열역학
 * ================================================================== */

/* 10-1. 미시상태 세기 */
add((() => {
    const W = 720, H = 410;
    const g = [];
    g.push(txt(26, 34, '알갱이 4개가 두 칸에 나뉘는 방법을 전부 세어 보면', { cls: 'ink bold' }));

    const counts = [1, 4, 6, 4, 1];
    const names = ['4 : 0', '3 : 1', '2 : 2', '1 : 3', '0 : 4'];
    const xs = [56, 186, 316, 446, 576];
    const bw = 88, by = 62, bh = 60;
    const spots = [[16, 18], [40, 18], [16, 40], [40, 40]];
    for (let i = 0; i < 5; i += 1) {
        const x = xs[i];
        g.push(box(x, by, bw, bh, { stroke: 'var(--ink2)', sw: 1.4, rx: 3 }));
        g.push(line([[x + bw / 2, by], [x + bw / 2, by + bh]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        const nLeft = 4 - i;
        for (let k = 0; k < 4; k += 1) {
            const side = k < nLeft ? 0 : 1;
            const idx = k < nLeft ? k : k - nLeft;
            const [dx, dy] = spots[idx];
            g.push(circ(x + side * (bw / 2) + dx, by + dy, 5.5,
                { fill: `var(--s${side ? 2 : 1})`, op: 0.85, stroke: 'none', sw: 0 }));
        }
        g.push(txt(x + bw / 2, by + bh + 20, names[i], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }

    const base = 316, unit = 26;
    for (let i = 0; i < 5; i += 1) {
        const h = counts[i] * unit;
        const x = xs[i] + bw / 2 - 22;
        g.push(box(x, base - h, 44, h, { fill: 'var(--s1)', op: 0.35, stroke: 'var(--s1)', sw: 1.2, rx: 2 }));
        g.push(txt(xs[i] + bw / 2, base - h - 8, String(counts[i]), { anchor: 'middle', cls: 'ink bold' }));
    }
    g.push(line([[40, base], [676, base]], { stroke: 'var(--ink2)', sw: 1.4 }));
    g.push(txt(36, base + 18, '가짓수 W', { cls: 'ink2', size: 'sm' }));

    g.push(txt(360, 366, '전부 16가지인데 그중 6가지가 반반으로 나뉜 상태다. 가장 많은 쪽이 관측된다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 390, '알갱이가 10²³ 개면 이 봉우리가 비교도 안 되게 뾰족해진다. 그래서 기체가 저절로 한쪽으로 몰리는 일은 없다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-microstates',
        title: '엔트로피의 뿌리 — 경우의 수 세기',
        desc: '구별되는 알갱이 4개를 두 칸에 나누는 방법은 모두 16가지다. 한쪽에 몰린 상태는 각각 1가지뿐이지만 '
            + '반반으로 나뉜 상태는 6가지다. 가짓수가 많은 쪽이 압도적으로 자주 관측되며, 이 가짓수를 W 라 하고 '
            + 'W 가 클수록 엔트로피가 크다고 한다. 알갱이 수가 늘어날수록 봉우리는 극단적으로 뾰족해진다.',
        svg: svg({ width: W, height: H, title: '미시상태 세기', desc: '알갱이 4개의 배치 다섯 가지와 가짓수 막대', body: g.join('') }),
    };
})());

/* 10-2. 상태에 따른 표준 몰 엔트로피 */
add((() => {
    const W = 720, H = 390;
    const items = [
        ['C(흑연)', 5.7, 1], ['CaO(s)', 38.1, 1], ['CaCO₃(s)', 91.7, 1],
        ['H₂O(l)', 70.0, 2], ['H₂(g)', 130.7, 3], ['H₂O(g)', 188.8, 3], ['CO₂(g)', 213.8, 3],
    ];
    const g = [];
    g.push(txt(26, 34, '표준 몰 엔트로피 S° — 흩어질 자유가 많을수록 크다', { cls: 'ink bold' }));
    const base = 296, scale = 0.94, x0 = 92, step = 84, bw = 52;
    g.push(line([[x0 - 22, base], [x0 - 22, 74]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const t of [0, 50, 100, 150, 200]) {
        const y = base - t * scale;
        g.push(line([[x0 - 27, y], [x0 - 22, y]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(x0 - 33, y + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 - 40, 62, 'S° (J/(mol·K))', { cls: 'ink2', size: 'sm' }));
    items.forEach(([name, v, slot], i) => {
        const cx = x0 + i * step + bw / 2;
        const h = v * scale;
        g.push(box(cx - bw / 2, base - h, bw, h, { fill: `var(--s${slot})`, op: 0.34, stroke: `var(--s${slot})`, sw: 1.3, rx: 2 }));
        g.push(txt(cx, base - h - 8, v.toFixed(1), { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(cx, base + 18, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(line([[x0 - 22, base], [x0 + 7 * step - 30, base]], { stroke: 'var(--ink2)', sw: 1.4 }));
    const brace = (i1, i2, label, slot) => {
        const a = x0 + i1 * step, b = x0 + i2 * step + bw;
        return line([[a, base + 34], [a, base + 40], [b, base + 40], [b, base + 34]], { stroke: `var(--s${slot})`, sw: 1.4 })
            + txt((a + b) / 2, base + 58, label, { anchor: 'middle', cls: 'ink bold' });
    };
    g.push(brace(0, 2, '고체', 1));
    g.push(brace(3, 3, '액체', 2));
    g.push(brace(4, 6, '기체', 3));
    g.push(txt(360, 382, '단단히 붙잡힌 고체가 가장 작고 마음대로 돌아다니는 기체가 가장 크다. 같은 상이면 무거울수록 크다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-entropy-phases',
        title: '상태에 따른 표준 몰 엔트로피',
        desc: '표준 몰 엔트로피는 고체에서 가장 작고 기체에서 가장 크다. 흑연은 5.7, 물(액체)은 70.0, '
            + '수증기는 188.8 J/(mol·K) 이다. 같은 상 안에서도 원자가 많고 무거운 분자일수록 크다. '
            + '이 때문에 기체가 늘어나는 반응이면 계산하기 전에 이미 ΔS 의 부호를 짐작할 수 있다.',
        svg: svg({ width: W, height: H, title: '표준 몰 엔트로피 막대그림', desc: '고체·액체·기체의 S° 비교', body: g.join('') }),
    };
})());

/* 10-3. 온도에 따른 엔트로피 — 제3법칙과 상변화의 도약 */
add((() => {
    const W = 680, H = 370;
    const f = frame({ xRange: [0, 500], yRange: [0, 240], box: { x: 86, y: 60, w: 470, h: 220 } });
    const g = [
        txt(26, 34, '0 K 에서 출발한다 — 열역학 제3법칙', { cls: 'ink bold' }),
        f.axes({ xLabel: 'T (K)', yLabel: 'S', xTicks: [0, 100, 200, 273, 373, 500], yTicks: [0] }),
    ];
    const solid = t => 52 * Math.sqrt(t / 273);
    const liquid = t => 78 + 26 * ((t - 273) / 100);
    const gas = t => 214 + 18 * ((t - 373) / 127);
    g.push(f.curve(solid, { from: 0, to: 273, cls: 's1' }));
    g.push(f.line([[273, solid(273)], [273, 78]], { cls: 's2' }));
    g.push(f.curve(liquid, { from: 273, to: 373, cls: 's1' }));
    g.push(f.line([[373, liquid(373)], [373, 214]], { cls: 's2' }));
    g.push(f.curve(gas, { from: 373, to: 500, cls: 's1' }));
    g.push(f.guide([273, 0], [273, 78]));
    g.push(f.guide([373, 0], [373, 214]));
    g.push(f.dot([0, 0], { cls: 'f1', r: 4 }));
    g.push(f.label([8, 12], '완전한 결정은 0 K 에서 S = 0', { cls: 'ink', size: 'sm' }));
    g.push(f.label([120, 66], '고체', { anchor: 'middle', cls: 'ink' }));
    g.push(f.label([320, 86], '액체', { anchor: 'middle', cls: 'ink' }));
    g.push(f.label([440, 196], '기체', { anchor: 'middle', cls: 'ink' }));
    g.push(f.label([283, 46], '녹으면서 뛴다', { cls: 'ink2', size: 'sm' }));
    g.push(f.label([368, 160], '끓으면서 훨씬 크게 뛴다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(340, 330, '엔탈피와 달리 엔트로피는 출발점이 정해져 있다. 그래서 ‘변화량’이 아닌 절대값을 표로 쓸 수 있다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(340, 354, '세로 눈금은 물질마다 다르므로 생략했다. 읽어야 할 것은 두 번의 도약과 그 크기 차이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-entropy-temperature',
        title: '온도가 오를 때 엔트로피가 자라는 모양',
        desc: '완전한 결정은 0 K 에서 엔트로피가 정확히 0 이다(제3법칙). 온도가 오르면 서서히 커지다가 녹는점과 '
            + '끓는점에서 계단처럼 뛴다. 끓을 때의 도약이 녹을 때보다 훨씬 크다. 출발점이 0 으로 못 박혀 있기 '
            + '때문에 엔트로피만은 변화량이 아니라 절대값을 표로 만들 수 있다.',
        svg: svg({ width: W, height: H, title: '엔트로피와 온도', desc: '0 K 에서 시작해 두 번 도약하는 곡선', body: g.join('') }),
    };
})());

/* 10-4. 우주의 엔트로피 수지 */
add((() => {
    const W = 740, H = 400;
    const g = [];
    g.push(txt(26, 34, '자발성은 계만 봐서는 판정할 수 없다 — 주위까지 더해야 한다', { cls: 'ink bold' }));
    g.push(txt(370, 58, 'CaCO₃(s) → CaO(s) + CO₂(g)   ΔH° = +179.2 kJ/mol,  ΔS°(계) = +160.2 J/(mol·K)',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const zero = 214, sc = 0.185;
    const drawPanel = (x, T, sSurr, title, verdict, slot) => {
        const out = [panel(x, 74, 330, 246, title)];
        const bars = [['ΔS~{계}', 160.2, 1], ['ΔS~{주위}', sSurr, 2], ['합', 160.2 + sSurr, slot]];
        const cx = [x + 78, x + 168, x + 262];
        out.push(line([[x + 24, zero], [x + 306, zero]], { stroke: 'var(--ink2)', sw: 1.2 }));
        bars.forEach(([nm, v, sl], i) => {
            const h = Math.abs(v) * sc;
            const top = v >= 0 ? zero - h : zero;
            out.push(box(cx[i] - 24, top, 48, h, { fill: `var(--s${sl})`, op: 0.34, stroke: `var(--s${sl})`, sw: 1.3, rx: 2 }));
            out.push(txt(cx[i], v >= 0 ? top - 8 : top + h + 16, (v > 0 ? '+' : '') + v.toFixed(1),
                { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(cx[i], v >= 0 ? zero + 18 : zero - 8, nm, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
        out.push(txt(x + 165, 300, verdict, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };
    g.push(drawPanel(24, 298, -601.0, '25 °C (298 K)', '우주 전체가 줄어든다 → 일어나지 않는다', 2));
    g.push(drawPanel(386, 1400, -128.0, '1127 °C (1400 K)', '우주 전체가 늘어난다 → 저절로 일어난다', 3));

    g.push(txt(370, 350, 'ΔS(주위) = −ΔH(계) / T 이므로, 온도가 오르면 주위가 잃는 몫이 작아진다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 374, '계의 몫은 그대로인데 주위의 몫만 줄어드는 것 — 이것이 고온에서 석회석이 분해되는 이유다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-universe-balance',
        title: '계와 주위의 엔트로피 수지',
        desc: '석회석 분해는 계의 엔트로피를 늘리지만(+160.2) 열을 빨아들이므로 주위의 엔트로피를 줄인다. '
            + '25 °C 에서는 주위가 잃는 601.0 이 압도적이라 우주 전체로는 줄어들어 일어나지 않는다. '
            + '1400 K 에서는 같은 열을 더 큰 온도로 나누므로 주위의 손실이 128.0 으로 작아지고, 합이 양수가 되어 '
            + '저절로 일어난다. ΔS(주위) = −ΔH(계)/T 라는 관계 하나가 온도 의존성을 전부 설명한다.',
        svg: svg({ width: W, height: H, title: '우주의 엔트로피 수지', desc: '두 온도에서의 엔트로피 막대 비교', body: g.join('') }),
    };
})());

/* 10-5. ΔG = ΔH − TΔS 의 네 가지 부호 조합 */
add((() => {
    const W = 780, H = 400;
    const f = frame({ xRange: [0, 1000], yRange: [-220, 220], box: { x: 92, y: 58, w: 420, h: 268 } });
    const g = [
        txt(26, 34, 'ΔG 는 온도의 일차함수다 — 세로 절편이 ΔH, 기울기가 −ΔS', { cls: 'ink bold' }),
        f.axes({ xLabel: 'T (K)', yLabel: 'ΔG (kJ/mol)', xTicks: [0, 250, 500, 750, 1000], yTicks: [-200, -100, 0, 100, 200] }),
    ];
    const cases = [
        [-100, 0.1, 's1', 'ΔH < 0,  ΔS > 0', '어느 온도에서나 자발'],
        [-100, -0.2, 's2', 'ΔH < 0,  ΔS < 0', '낮은 온도에서만 자발'],
        [100, 0.2, 's3', 'ΔH > 0,  ΔS > 0', '높은 온도에서만 자발'],
        [100, -0.1, 'ink-line', 'ΔH > 0,  ΔS < 0', '어느 온도에서도 비자발'],
    ];
    g.push(f.line([[0, 0], [1000, 0]], { cls: 's1' }).replace('class="cv s1"', 'class="cv" stroke="var(--ink)" stroke-width="1.4" stroke-dasharray="7 4"'));
    for (const [dH, dS, cls] of cases) {
        const fn = T => dH - T * dS;
        if (cls === 'ink-line') {
            g.push(f.line([[0, fn(0)], [1000, fn(1000)]], { cls: 's1', dash: '6 4' })
                .replace('class="cv s1"', 'class="cv" stroke="var(--ink2)"'));
        } else {
            g.push(f.line([[0, fn(0)], [1000, fn(1000)]], { cls }));
        }
        const Tc = dH / dS;
        if (Tc > 0 && Tc < 1000) {
            g.push(f.dot([Tc, 0], { cls: 'f1', r: 4 }));
            g.push(f.label([Tc, 0], '전환 온도', { dy: -12, anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    }
    g.push(f.label([20, -190], 'ΔG < 0 — 자발', { cls: 'ink bold' }));
    g.push(f.label([20, 190], 'ΔG > 0 — 비자발', { cls: 'ink2' }));

    let ly = 92;
    for (const [, , cls, name, note] of cases) {
        const stroke = cls === 'ink-line' ? 'var(--ink2)' : `var(--${cls})`;
        g.push(line([[540, ly - 5], [572, ly - 5]], { stroke, sw: 3, dash: cls === 'ink-line' ? '6 4' : undefined }));
        g.push(txt(582, ly, name, { cls: 'ink' }));
        g.push(txt(582, ly + 19, note, { cls: 'ink2', size: 'sm' }));
        ly += 54;
    }
    g.push(txt(390, 362, '가로 점선(ΔG = 0)을 지나는 지점이 전환 온도이고, 그 값이 ΔH / ΔS 다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(390, 386, '기울기의 부호를 정하는 것은 ΔS 다. 부호가 하나만 바뀌어도 그림이 완전히 달라진다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-gibbs-four-cases',
        title: 'ΔH 와 ΔS 의 부호 조합 네 가지',
        desc: 'ΔG = ΔH − TΔS 는 온도에 대한 직선이며 세로 절편이 ΔH, 기울기가 −ΔS 다. 두 부호의 조합에 따라 '
            + '네 가지 그림이 나온다. 둘의 부호가 유리하게 맞으면 모든 온도에서 자발적이고, 반대면 어느 온도에서도 '
            + '일어나지 않는다. 부호가 엇갈리는 두 경우에만 직선이 ΔG = 0 을 가로지르며, 그 지점이 전환 온도 ΔH/ΔS 다.',
        svg: svg({ width: W, height: H, title: 'ΔG 와 온도', desc: '네 가지 부호 조합의 직선', body: g.join('') }),
    };
})());

/* 10-6. 반응 진행에 따른 G — 평형은 골짜기 바닥 */
add((() => {
    const W = 700, H = 390;
    const f = frame({ xRange: [0, 1], yRange: [2.6, 8.6], box: { x: 92, y: 62, w: 470, h: 232 } });
    const G = x => 8 - 4 * x + 3 * (x * Math.log(x) + (1 - x) * Math.log(1 - x));
    const xe = 0.791;
    const g = [
        txt(26, 34, '반응이 진행되면 G 는 골짜기를 따라 내려가 바닥에서 멈춘다', { cls: 'ink bold' }),
        f.axes({ xLabel: '반응이 진행된 정도', yLabel: 'G', xTicks: [0, 0.5, 1], yTicks: [] }),
        f.curve(G, { from: 0.004, to: 0.996, cls: 's1' }),
        f.guide([xe, 2.6], [xe, G(xe)]),
        f.dot([xe, G(xe)], { cls: 'f2', r: 5 }),
    ];
    g.push(f.label([xe, G(xe)], '평형 — 여기서 ΔG = 0, Q = K', { dx: 8, dy: 22, cls: 'ink bold' }));
    g.push(f.label([0.02, 8.35], '순수한 반응물만', { cls: 'ink', size: 'sm' }));
    g.push(f.label([0.98, 4.35], '순수한 생성물만', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(f.dot([0.004, G(0.004)], { cls: 'f1', r: 4 }));
    g.push(f.dot([0.996, G(0.996)], { cls: 'f1', r: 4 }));
    g.push(px(f.X(0.3), f.Y(4.6), f.X(0.55), f.Y(4.6), { cls: 's3', marker: 'ar3', width: 2.2 }));
    g.push(f.label([0.3, 4.9], '정반응이 자발  (ΔG < 0)', { cls: 'ink' }));
    g.push(px(f.X(0.97), f.Y(3.35), f.X(0.86), f.Y(3.35), { cls: 's3', marker: 'ar3', width: 2.2 }));
    g.push(f.label([0.965, 3.6], '역반응이 자발', { anchor: 'end', cls: 'ink' }));
    g.push(f.guide([0.004, G(0.004)], [0.996, G(0.004)]));
    g.push(f.label([0.5, 8.35], 'ΔG° 는 양 끝의 높이 차이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(340, 344, 'ΔG° 는 양 끝의 높이 차이이고, ΔG 는 지금 서 있는 자리의 기울기다. 둘은 다른 양이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(340, 368, '바닥이 오른쪽 끝이 아니라 안쪽에 있는 것이 요점이다. 반응은 완결되지 않고 평형에서 멈춘다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-thermo-gibbs-minimum',
        title: '반응 진행에 따른 자유에너지 — 평형은 골짜기 바닥',
        desc: '순수한 반응물에서 순수한 생성물까지 가는 길에서 자유에너지 G 를 그리면 안쪽에 바닥이 있는 골짜기가 '
            + '된다. 계는 기울기를 따라 내려가다 바닥에서 멈추며, 그 자리가 평형이다. 바닥의 왼쪽에서는 정반응이 '
            + '자발적이고 오른쪽에서는 역반응이 자발적이다. ΔG° 는 양 끝의 높이 차이, ΔG 는 지금 자리의 기울기로 '
            + '서로 다른 양이라는 점이 중요하다.',
        svg: svg({ width: W, height: H, title: '자유에너지 골짜기', desc: '반응 진행도에 대한 G 곡선', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 11장 — 용액
 * ================================================================== */

/* 11-1. 용해를 에너지로 쪼개기 */
add((() => {
    const W = 750, H = 390;
    const g = [];
    g.push(txt(26, 34, '녹는다는 것은 두 큰 값의 줄다리기다', { cls: 'ink bold' }));
    g.push(arrowK(60, 300, 60, 84, { width: 1.3 }));
    g.push(txt(54, 76, 'H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const yHi = 100, y0 = 292, yEnd = 288;
    g.push(level(268, 492, yHi, 'K⁺(g) + Cl⁻(g)  +  물', { anchor: 'middle', dy: -10 }));
    g.push(level(104, 268, y0, 'KCl(s) + 물', { anchor: 'start', dy: 22 }));
    g.push(level(492, 660, yEnd, 'K⁺(aq) + Cl⁻(aq)', { anchor: 'end', dy: -12 }));

    g.push(px(216, y0, 216, yHi, { cls: 's2', marker: 'ar2', width: 2.4 }));
    g.push(txt(208, 176, '격자를 부순다', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(208, 198, '+715 kJ/mol', { anchor: 'end', cls: 'ink' }));
    g.push(txt(208, 218, '(흡열)', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(px(556, yHi, 556, yEnd, { cls: 's1', marker: 'ar1', width: 2.4 }));
    g.push(txt(566, 176, '물이 이온을 감싼다', { cls: 'ink bold' }));
    g.push(txt(566, 198, '−698 kJ/mol', { cls: 'ink' }));
    g.push(txt(566, 218, '(발열, 물을 벌리는 비용까지 포함)', { cls: 'ink2', size: 'sm' }));

    g.push(box(276, 250, 176, 40, { fill: 'var(--s3)', op: 0.14, stroke: 'var(--s3)', sw: 1.4, rx: 6 }));
    g.push(txt(364, 268, 'ΔH(용해) = +17 kJ/mol', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(364, 286, '겨우 이만큼만 남는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(360, 344, '700 가까운 두 값의 차이가 17 이다. 어느 쪽이 조금만 달라도 부호가 뒤집힌다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 368, '그래서 용해 엔탈피는 계산으로 예측하지 않고 직접 잰다. 흡열인데도 녹는 이유는 엔트로피에 있다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-dissolution-steps',
        title: '용해 과정의 에너지 수지',
        desc: '이온성 고체가 녹는 과정을 둘로 쪼갠다. 먼저 격자를 부수는 데 큰 에너지가 들고(흡열), 다음에 물이 '
            + '이온을 감싸면서 큰 에너지가 나온다(발열). KCl 에서는 +715 와 −698 의 차이인 +17 kJ/mol 만 남는다. '
            + '큰 두 값의 작은 차이라서 부호를 예측하기 어렵고, 흡열인데도 녹는 것은 엔트로피가 이기기 때문이다.',
        svg: svg({ width: W, height: H, title: '용해의 에너지 수지', desc: '격자 파괴와 수화의 준위 그림', body: g.join('') }),
    };
})());

/* 11-2. 용해도와 온도 */
add((() => {
    const W = 720, H = 360;
    const g = [txt(26, 34, '온도가 오르면 고체는 대개 더 녹고, 기체는 반드시 덜 녹는다', { cls: 'ink bold' })];
    const fa = frame({ xRange: [0, 100], yRange: [0, 10], box: { x: 84, y: 66, w: 234, h: 186 } });
    g.push(fa.axes({ xLabel: 'T (°C)', yLabel: '용해도', xTicks: [0, 25, 50, 75, 100], yTicks: [] }));
    g.push(fa.curve(t => 1 + 0.0008 * t * t, { cls: 's1' }));
    g.push(fa.curve(t => 3.2 - 0.021 * t, { cls: 's2', dash: '6 4' }));
    g.push(fa.label([52, 6.2], '대부분의 고체', { cls: 'ink' }));
    g.push(fa.label([52, 5.2], '(KNO₃ 등)', { cls: 'ink2', size: 'sm' }));
    g.push(fa.label([98, 1.9], '줄어드는 고체도 있다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(201, 284, '고체 — 규칙이 아니라 경향일 뿐이다', { anchor: 'middle', cls: 'ink' }));

    const fb = frame({ xRange: [0, 100], yRange: [0, 10], box: { x: 434, y: 66, w: 234, h: 186 } });
    g.push(fb.axes({ xLabel: 'T (°C)', yLabel: '용해도', xTicks: [0, 25, 50, 75, 100], yTicks: [] }));
    g.push(fb.curve(t => 8.4 * Math.exp(-t / 38), { cls: 's3' }));
    g.push(fb.label([32, 6.4], '모든 기체', { cls: 'ink' }));
    g.push(fb.label([98, 2.6], '녹는 것이 발열이므로', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(fb.label([98, 1.6], '온도를 올리면 빠져나간다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(551, 284, '기체 — 예외 없이 줄어든다', { anchor: 'middle', cls: 'ink' }));

    g.push(txt(360, 322, '세로 눈금은 물질마다 자릿수가 다르므로 생략했다. 읽어야 할 것은 기울기의 방향이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 346, '더운 물에 설탕이 잘 녹고, 데워진 강물에 산소가 모자라는 것이 이 두 그림이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-solubility-temp',
        title: '온도에 따른 고체와 기체의 용해도',
        desc: '고체의 용해도는 대개 온도가 오르면 커지지만 줄어드는 예도 있어 절대 규칙이 아니다. 반면 기체의 '
            + '용해도는 예외 없이 온도가 오르면 작아진다. 기체가 녹는 과정이 발열이기 때문이다. '
            + '데워진 강물의 용존 산소가 모자라는 것이 오른쪽 그림의 직접적인 결과다.',
        svg: svg({ width: W, height: H, title: '용해도와 온도', desc: '고체와 기체의 용해도 곡선 비교', body: g.join('') }),
    };
})());

/* 11-3. 헨리 법칙 */
add((() => {
    const W = 700, H = 370;
    const f = frame({ xRange: [0, 460], yRange: [0, 0.17], box: { x: 104, y: 62, w: 420, h: 216 } });
    const g = [
        txt(26, 34, '기체가 녹는 양은 그 기체의 분압에 비례한다', { cls: 'ink bold' }),
        f.axes({
            xLabel: 'CO₂ 분압 (kPa)', yLabel: 'c (mol/L)',
            xTicks: [0, 100, 200, 300, 400], yTicks: [0, 0.05, 0.1, 0.15],
        }),
        f.line([[0, 0], [440, 440 * 3.4e-4]], { cls: 's1' }),
    ];
    g.push(f.dot([400, 0.136], { cls: 'f2', r: 5 }));
    g.push(f.guide([400, 0], [400, 0.136]));
    g.push(f.guide([0, 0.136], [400, 0.136]));
    g.push(f.label([392, 0.147], '뚜껑을 닫아 둔 상태  0.136 mol/L', { anchor: 'end', cls: 'ink' }));
    g.push(f.dot([0.04, 1.4e-5], { cls: 'f2', r: 5 }));
    g.push(f.label([16, 0.018], '뚜껑을 열면 여기까지 내려온다 (거의 0)', { cls: 'ink' }));
    g.push(px(f.X(330), f.Y(0.120), f.X(60), f.Y(0.020), { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(f.label([230, 0.088], 'c = k~H P', { anchor: 'middle', cls: 'ink bold' }));
    g.push(f.label([230, 0.072], '기울기가 k~H', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 324, '차이만큼의 CO₂ 가 한꺼번에 빠져나온다. 500 mL 병이면 약 1.7 L 의 기체다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 348, '온도를 올리면 k~H 가 작아져 기울기가 눕는다. 미지근한 음료가 더 심하게 넘치는 이유다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-henry',
        title: '헨리 법칙 — 분압과 용해도의 직선 관계',
        desc: '기체의 용해도는 그 기체의 분압에 비례하며 기울기가 헨리 상수 k(H) 다. 탄산음료는 CO₂ 분압 '
            + '400 kPa 로 충전해 0.136 mol/L 를 녹여 두었다가, 뚜껑을 열면 대기의 CO₂ 분압에 맞춰 거의 0 까지 '
            + '내려온다. 그 차이만큼의 기체가 한꺼번에 빠져나오는 것이 거품이다. 온도가 오르면 기울기가 눕는다.',
        svg: svg({ width: W, height: H, title: '헨리 법칙', desc: '분압에 대한 용해도 직선', body: g.join('') }),
    };
})());

/* 11-4. 몰농도와 몰랄농도 */
add((() => {
    const W = 720, H = 380;
    const g = [];
    g.push(txt(26, 34, '왜 총괄성에서는 부피 대신 질량을 기준으로 삼는가', { cls: 'ink bold' }));
    const cup = (x, fillH, temp, vol, cVal) => {
        const out = [];
        out.push(beaker(x, 96, 150, 172, fillH, { cls: 's1', op: 0.18 }));
        out.push(line([[x - 8, 268 - fillH], [x + 158, 268 - fillH]], { stroke: 'var(--s2)', sw: 1.4, dash: '5 4' }));
        out.push(txt(x + 75, 82, temp, { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(x + 75, 292, vol, { anchor: 'middle', cls: 'ink' }));
        out.push(txt(x + 75, 314, cVal, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    g.push(cup(96, 104, '20 °C', '부피 1.02 L', '몰농도 c = 0.98 mol/L'));
    g.push(cup(474, 140, '80 °C', '부피 1.06 L (예시)', '몰농도 c = 0.94 mol/L'));
    g.push(px(300, 170, 456, 170, { cls: 's2', marker: 'ar2', width: 2.2 }));
    g.push(txt(378, 158, '데우면 부풀어 오른다', { anchor: 'middle', cls: 'ink' }));
    g.push(box(268, 196, 220, 76, { fill: 'var(--s3)', op: 0.12, stroke: 'var(--s3)', sw: 1.4, rx: 6 }));
    g.push(txt(378, 220, '들어 있는 것은 그대로', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(378, 242, 'NaCl 1.00 mol', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(378, 262, '물 1.000 kg', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(360, 348, '몰랄농도 b = 1.00 mol/kg — 두 그림에서 같다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(360, 372, '어는점·끓는점 실험은 온도를 바꾸면서 하므로 부피 기준 농도를 쓸 수 없다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-molality-vs-molarity',
        title: '몰농도와 몰랄농도의 차이',
        desc: '같은 용액을 데우면 부피가 늘어 몰농도가 달라지지만, 녹아 있는 물질의 양과 용매의 질량은 변하지 '
            + '않으므로 몰랄농도는 그대로다. 어는점 내림과 끓는점 오름은 온도를 크게 바꾸면서 재는 실험이므로 '
            + '부피 기준인 몰농도를 쓸 수 없고 질량 기준인 몰랄농도를 쓴다. 그림의 부피 값은 예시다.',
        svg: svg({ width: W, height: H, title: '몰농도와 몰랄농도', desc: '온도를 올린 같은 용액 두 개', body: g.join('') }),
    };
})());

/* 11-5. 라울 법칙과 증기압 내림 */
add((() => {
    const W = 770, H = 390;
    const g = [];
    g.push(txt(26, 34, '용질이 표면을 차지하면 빠져나가는 분자가 줄어든다', { cls: 'ink bold' }));

    const jar = (x, title, nSolute, nEsc) => {
        const out = [box(x, 64, 200, 200, { stroke: 'var(--ink2)', sw: 1.8, rx: 6 })];
        out.push(box(x + 4, 178, 192, 82, { fill: 'var(--s1)', op: 0.16, stroke: 'none', sw: 0, rx: 3 }));
        out.push(txt(x + 100, 56, title, { anchor: 'middle', cls: 'ink bold' }));
        const spots = [[26, 196], [64, 214], [102, 194], [140, 216], [170, 198], [46, 240], [122, 242]];
        for (let i = 0; i < nSolute; i += 1) {
            const [dx, dy] = spots[i];
            out.push(circ(x + dx, dy, 8, { fill: 'var(--s2)', op: 0.6, stroke: 'var(--s2)', sw: 1.2 }));
        }
        for (let i = 0; i < nEsc; i += 1) {
            const ex = x + 24 + i * (152 / Math.max(nEsc - 1, 1));
            out.push(px(ex, 172, ex, 112 - (i % 2) * 16, { cls: 's3', marker: 'ar3', width: 1.8 }));
        }
        return out.join('');
    };
    g.push(jar(46, '순수한 용매', 0, 5));
    g.push(jar(296, '비휘발성 용질을 녹인 용액', 7, 3));
    g.push(txt(146, 286, '증기압 P°', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(396, 286, '증기압 P = x P°  (더 낮다)', { anchor: 'middle', cls: 'ink' }));

    const f = frame({ xRange: [0, 1], yRange: [0, 1], box: { x: 570, y: 96, w: 130, h: 150 } });
    g.push(f.axes({ xLabel: 'x(용매)', yLabel: 'P / P°', xTicks: [0, 1], yTicks: [0, 1] }));
    g.push(f.line([[0, 0], [1, 1]], { cls: 's1' }));
    g.push(f.dot([0.8, 0.8], { cls: 'f2', r: 4 }));
    g.push(txt(635, 76, '라울 법칙', { anchor: 'middle', cls: 'ink bold' }));

    g.push(txt(370, 330, '용질이 비휘발성이면 증기에는 용매만 있다. 그런데 표면의 일부를 용질이 차지하므로 나가는 수가 준다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 356, '그래서 증기압이 용매의 몰분율만큼 줄어든다. 총괄성 네 가지가 전부 여기서 나온다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(W - 16, H - 10, '용질이 무엇인지는 상관없고 몇 개인지만 문제가 된다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-vapor-lowering',
        title: '증기압 내림 — 총괄성의 뿌리',
        desc: '비휘발성 용질을 녹이면 용매 분자가 표면에서 차지하는 몫이 줄어 빠져나가는 수가 준다. 그래서 '
            + '용액의 증기압이 순수한 용매보다 낮아지고, 그 값은 용매의 몰분율에 비례한다(라울 법칙). '
            + '용질이 무엇인지는 상관없고 몇 개인지만 문제가 되며, 총괄성 네 가지가 모두 이 사실에서 나온다.',
        svg: svg({ width: W, height: H, title: '증기압 내림', desc: '순수 용매와 용액의 증발 비교', body: g.join('') }),
    };
})());

/* 11-6. 어는점 내림과 끓는점 오름 */
add((() => {
    const W = 720, H = 380;
    const g = [];
    g.push(txt(26, 34, '증기압이 내려가면 액체로 있는 온도 구간이 양쪽으로 넓어진다', { cls: 'ink bold' }));

    // 전체 눈금
    const X0 = 70, X1 = 650, T0 = -6, T1 = 106;
    const XT = t => X0 + ((t - T0) / (T1 - T0)) * (X1 - X0);
    g.push(box(XT(0), 74, XT(100) - XT(0), 26, { fill: 'var(--s1)', op: 0.2, stroke: 'var(--s1)', sw: 1.2, rx: 3 }));
    g.push(line([[X0, 100], [X1, 100]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const t of [0, 25, 50, 75, 100]) {
        g.push(line([[XT(t), 100], [XT(t), 106]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(XT(t), 120, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(X1 + 12, 104, '°C', { cls: 'ink2', size: 'sm' }));
    g.push(txt(XT(50), 92, '물이 액체로 있는 구간', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(X0, 62, '전체 눈금에서는 변화가 보이지 않는다 — 그래서 양 끝을 확대한다', { cls: 'ink2', size: 'sm' }));

    // 확대 두 개
    const zoom = (x, w, a, b, ticks, marks, title) => {
        const out = [box(x, 158, w, 128, { stroke: 'var(--grid)', sw: 1, rx: 6 })];
        const Z = t => x + 30 + ((t - a) / (b - a)) * (w - 60);
        out.push(txt(x + w / 2, 180, title, { anchor: 'middle', cls: 'ink bold' }));
        out.push(line([[x + 18, 236], [x + w - 18, 236]], { stroke: 'var(--ink2)', sw: 1.4 }));
        for (const t of ticks) {
            out.push(line([[Z(t), 236], [Z(t), 242]], { stroke: 'var(--ink2)', sw: 1 }));
            out.push(txt(Z(t), 258, t.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        marks.forEach(([t, name, slot, dy]) => {
            out.push(circ(Z(t), 236, 5, { fill: `var(--s${slot})`, op: 0.9, stroke: 'none', sw: 0 }));
            out.push(line([[Z(t), 232], [Z(t), 236 + dy]], { stroke: `var(--s${slot})`, sw: 1.2, dash: '3 3' }));
            out.push(txt(Z(t), 236 + dy - 6, name, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
        return out.join('');
    };
    g.push(zoom(40, 300, -2.4, 0.8, [-2, -1, 0],
        [[0, '순수한 물  0.00', 1, -16], [-1.86, '용액  −1.86', 2, -40]], '어는점 쪽을 확대'));
    g.push(zoom(380, 300, 99.7, 100.9, [99.8, 100.2, 100.6],
        [[100, '순수한 물  100.00', 1, -16], [100.51, '용액  100.51', 2, -40]], '끓는점 쪽을 확대'));

    g.push(txt(360, 320, '1.00 mol/kg 비전해질 수용액의 값이다. 어는점은 1.86 내려가고 끓는점은 0.51 올라간다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(360, 346, '두 값이 다른 것은 물의 K~f 와 K~b 가 다르기 때문이다. 어는점 쪽이 3배 넘게 예민하다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 370, '전해질이면 갈라진 알갱이 수만큼 곱해야 한다 — 반트호프 인자 i',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-colligative-shift',
        title: '어는점 내림과 끓는점 오름의 크기',
        desc: '1.00 mol/kg 비전해질 수용액은 어는점이 1.86 °C 내려가고 끓는점이 0.51 °C 올라간다. 0 에서 100 까지의 '
            + '전체 눈금에서는 보이지 않을 만큼 작아 양 끝을 확대해 표시했다. 어는점 쪽 변화가 세 배 넘게 크며, '
            + '이는 물의 어는점 내림 상수가 끓는점 오름 상수보다 크기 때문이다. 전해질이면 갈라진 알갱이 수만큼 곱한다.',
        svg: svg({ width: W, height: H, title: '어는점 내림과 끓는점 오름', desc: '온도 눈금과 두 확대 그림', body: g.join('') }),
    };
})());

/* 11-7. 삼투 */
add((() => {
    const W = 700, H = 390;
    const g = [];
    g.push(txt(26, 34, '용매만 지나갈 수 있으면 한쪽으로 밀려간다', { cls: 'ink bold' }));

    // U 자관
    const yTop = 76, yBot = 300;
    const L = 190, R = 400, tubeW = 76;
    g.push(box(L, yTop, tubeW, yBot - yTop, { stroke: 'var(--ink2)', sw: 1.8, rx: 2 }));
    g.push(box(R, yTop, tubeW, yBot - yTop, { stroke: 'var(--ink2)', sw: 1.8, rx: 2 }));
    g.push(box(L + tubeW - 2, yBot - 52, R - L - tubeW + 4, 52, { stroke: 'var(--ink2)', sw: 1.8, rx: 2 }));

    const lvlL = 214, lvlR = 132;
    g.push(box(L + 3, lvlL, tubeW - 6, yBot - lvlL - 3, { fill: 'var(--s1)', op: 0.2, stroke: 'none', sw: 0 }));
    g.push(box(L + tubeW - 2, yBot - 49, (R - L - tubeW + 4) / 2, 46, { fill: 'var(--s1)', op: 0.2, stroke: 'none', sw: 0 }));
    g.push(box(R + 3, lvlR, tubeW - 6, yBot - lvlR - 3, { fill: 'var(--s2)', op: 0.2, stroke: 'none', sw: 0 }));
    g.push(box((L + R + tubeW) / 2, yBot - 49, (R - L - tubeW + 4) / 2, 46, { fill: 'var(--s2)', op: 0.2, stroke: 'none', sw: 0 }));

    const mx = (L + tubeW + R) / 2;
    g.push(line([[mx, yBot - 52], [mx, yBot]], { stroke: 'var(--s3)', sw: 3.2, dash: '5 3' }));
    g.push(txt(mx, yBot + 22, '반투막', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(mx, yBot + 42, '용매만 지나간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(px(mx - 26, yBot - 26, mx + 26, yBot - 26, { cls: 's1', marker: 'ar1', width: 2.2 }));

    g.push(txt(L + tubeW / 2, 60, '순수한 용매', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(R + tubeW / 2, 60, '용액', { anchor: 'middle', cls: 'ink bold' }));
    for (const [dx, dy] of [[16, 168], [46, 196], [22, 232], [52, 258], [30, 284]]) {
        g.push(circ(R + dx, dy, 7, { fill: 'var(--s2)', op: 0.6, stroke: 'var(--s2)', sw: 1.2 }));
    }
    g.push(line([[L - 12, lvlL], [R + tubeW + 60, lvlL]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(line([[R - 12, lvlR], [R + tubeW + 60, lvlR]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    g.push(arrowK(R + tubeW + 44, lvlL, R + tubeW + 44, lvlR, { width: 1.6 }));
    g.push(arrowK(R + tubeW + 44, lvlR, R + tubeW + 44, lvlL, { width: 1.6 }));
    g.push(txt(R + tubeW + 54, (lvlL + lvlR) / 2 - 6, '높이 차이가', { cls: 'ink' }));
    g.push(txt(R + tubeW + 54, (lvlL + lvlR) / 2 + 14, '삼투압 Π 다', { cls: 'ink bold' }));

    g.push(txt(350, 358, 'Π = i c R T — 이상기체 법칙과 꼴이 같지만 나온 길은 전혀 다르다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 380, '아주 묽은 용액에서도 압력이 크게 나오므로 단백질 같은 큰 분자의 몰질량을 재는 데 쓴다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-soln-osmosis',
        title: '삼투와 삼투압',
        desc: '반투막은 용매 분자만 지나가게 한다. 그러면 용매가 순수한 쪽에서 용액 쪽으로 밀려가 용액 쪽 액면이 '
            + '올라가고, 그 높이 차이가 만드는 압력이 삼투압 Π 다. Π = icRT 로 이상기체 법칙과 꼴이 같지만 유도된 '
            + '길은 전혀 다르다. 아주 묽은 용액에서도 큰 압력이 나오므로 큰 분자의 몰질량 측정에 쓴다.',
        svg: svg({ width: W, height: H, title: '삼투', desc: 'U 자관과 반투막', body: g.join('') }),
    };
})());

/* ================================================================== *
 * 12장 — 화학 평형
 * ================================================================== */

/* 12-1. 동적 평형 — 농도와 속도 */
add((() => {
    const W = 740, H = 370;
    const g = [txt(26, 34, '멈춘 것이 아니라 양쪽 속도가 같아진 것이다', { cls: 'ink bold' })];
    const fa = frame({ xRange: [0, 10], yRange: [0, 0.12], box: { x: 84, y: 66, w: 250, h: 196 } });
    g.push(fa.axes({ xLabel: '시간', yLabel: 'mol/L', xTicks: [0, 2, 4, 6, 8, 10], yTicks: [0, 0.04, 0.08, 0.12] }));
    g.push(fa.curve(t => 0.080 + 0.020 * Math.exp(-0.6 * t), { cls: 's1' }));
    g.push(fa.curve(t => 0.040 * (1 - Math.exp(-0.6 * t)), { cls: 's2' }));
    g.push(fa.label([9.7, 0.088], 'N₂O₄', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(fa.label([9.7, 0.046], 'NO₂', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(txt(209, 290, '농도는 어느 지점부터 변하지 않는다', { anchor: 'middle', cls: 'ink' }));

    const fb = frame({ xRange: [0, 10], yRange: [0, 1], box: { x: 434, y: 66, w: 250, h: 196 } });
    g.push(fb.axes({ xLabel: '시간', yLabel: '속도', xTicks: [0, 2, 4, 6, 8, 10], yTicks: [] }));
    g.push(fb.curve(t => 0.4 + 0.5 * Math.exp(-0.6 * t), { cls: 's1' }));
    g.push(fb.curve(t => 0.4 * (1 - Math.exp(-0.6 * t)), { cls: 's2' }));
    g.push(fb.guide([5.5, 0], [5.5, 0.62]));
    g.push(fb.label([9.7, 0.50], '정반응', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(fb.label([9.7, 0.30], '역반응', { anchor: 'end', cls: 'ink', size: 'sm' }));
    g.push(fb.label([5.6, 0.70], '여기서부터', { cls: 'ink2', size: 'sm' }));
    g.push(fb.label([5.6, 0.62], '두 속도가 같다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(559, 290, '두 속도가 만나 겹친다 — 그때가 평형', { anchor: 'middle', cls: 'ink' }));

    g.push(txt(370, 330, '평형에서도 정반응과 역반응은 계속 일어난다. 같은 빠르기로 서로를 지울 뿐이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(370, 356, '그래서 ‘동적’ 평형이라 부른다. 반응이 멈췄다고 생각하면 뒤의 르샤틀리에가 설명되지 않는다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-eq-dynamic',
        title: '동적 평형 — 농도가 멈추는 것과 반응이 멈추는 것은 다르다',
        desc: '왼쪽은 시간에 따른 농도, 오른쪽은 정반응과 역반응의 속도다. 농도가 변하지 않게 되는 시점은 두 속도가 '
            + '같아지는 시점과 정확히 일치한다. 평형에서도 두 반응은 계속 일어나며 같은 빠르기로 서로를 지울 뿐이다. '
            + '이것을 동적 평형이라 하고, 반응이 멈춘 것으로 오해하면 르샤틀리에 원리를 이해할 수 없다.',
        svg: svg({ width: W, height: H, title: '동적 평형', desc: '농도-시간과 속도-시간 두 그래프', body: g.join('') }),
    };
})());

/* 12-2. 어느 쪽에서 출발해도 같은 평형 */
add((() => {
    const W = 680, H = 360;
    const f = frame({ xRange: [0, 10], yRange: [0, 0.22], box: { x: 90, y: 62, w: 440, h: 208 } });
    const g = [
        txt(26, 34, '반응물에서 출발하든 생성물에서 출발하든 같은 자리에 선다', { cls: 'ink bold' }),
        f.axes({ xLabel: '시간', yLabel: '[NO₂] (mol/L)', xTicks: [0, 2, 4, 6, 8, 10], yTicks: [0, 0.05, 0.1, 0.15, 0.2] }),
        f.curve(t => 0.040 * (1 - Math.exp(-0.6 * t)), { cls: 's1' }),
        f.curve(t => 0.040 + 0.160 * Math.exp(-0.6 * t), { cls: 's2' }),
        f.line([[0, 0.04], [10, 0.04]], { cls: 's3', dash: '6 4' }),
    ];
    g.push(f.dot([0, 0], { cls: 'f1', r: 4 }));
    g.push(f.dot([0, 0.2], { cls: 'f2', r: 4 }));
    g.push(f.label([0.3, 0.012], 'N₂O₄ 만 넣고 출발', { cls: 'ink', size: 'sm' }));
    g.push(f.label([0.3, 0.208], 'NO₂ 만 넣고 출발', { cls: 'ink', size: 'sm' }));
    g.push(f.label([9.6, 0.058], '같은 평형 농도 0.040', { anchor: 'end', cls: 'ink bold' }));
    g.push(txt(340, 316, '평형의 위치를 정하는 것은 어디서 출발했는가가 아니라 온도와 K 다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(340, 342, '두 실험에서 넣은 원자의 총수가 같다는 것이 조건이다. 그것만 같으면 도착점이 같다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-eq-both-directions',
        title: '양쪽 어디서 출발해도 같은 평형에 이른다',
        desc: 'N₂O₄ 만 넣고 시작하든 그와 원자 수가 같은 만큼의 NO₂ 만 넣고 시작하든, 같은 온도라면 끝내 같은 '
            + '평형 농도에 도달한다. 평형의 위치를 정하는 것은 출발점이 아니라 온도와 평형상수다. '
            + '이 사실이 평형상수라는 하나의 수로 계를 기술할 수 있는 근거다.',
        svg: svg({ width: W, height: H, title: '같은 평형에 이르는 두 길', desc: '양쪽에서 출발한 두 곡선', body: g.join('') }),
    };
})());

/* 12-3. K 의 크기가 말해 주는 것 */
add((() => {
    const W = 700, H = 360;
    const g = [txt(26, 34, 'K 의 크기는 평형에서 어느 쪽이 많은지를 말한다', { cls: 'ink bold' })];
    const rows = [
        ['10⁻⁵', 1e-5, '거의 반응물만 — 반응이 일어나지 않은 것처럼 보인다'],
        ['10⁻²', 1e-2, '반응물 쪽으로 크게 치우친다'],
        ['1', 1, '양쪽이 비슷하다'],
        ['10²', 1e2, '생성물 쪽으로 크게 치우친다'],
        ['10⁵', 1e5, '거의 생성물만 — 다 갔다고 보아도 된다'],
    ];
    const x0 = 130, bw = 250, y0 = 76, step = 50;
    rows.forEach(([nm, K, note], i) => {
        const y = y0 + i * step;
        const fB = K / (1 + K);
        const wB = Math.max(bw * fB, 1.6);
        g.push(box(x0, y, bw - wB, 26, { fill: 'var(--s1)', op: 0.34, stroke: 'var(--s1)', sw: 1, rx: 2 }));
        g.push(box(x0 + bw - wB, y, wB, 26, { fill: 'var(--s2)', op: 0.5, stroke: 'var(--s2)', sw: 1, rx: 2 }));
        g.push(txt(x0 - 14, y + 18, `K = ${nm}`, { anchor: 'end', cls: 'ink' }));
        g.push(txt(x0 + bw + 14, y + 18, note, { cls: 'ink2', size: 'sm' }));
    });
    g.push(box(x0, y0 - 26, 60, 16, { fill: 'var(--s1)', op: 0.34, stroke: 'var(--s1)', sw: 1, rx: 2 }));
    g.push(txt(x0 + 68, y0 - 13, '반응물', { cls: 'ink', size: 'sm' }));
    g.push(box(x0 + 130, y0 - 26, 60, 16, { fill: 'var(--s2)', op: 0.5, stroke: 'var(--s2)', sw: 1, rx: 2 }));
    g.push(txt(x0 + 198, y0 - 13, '생성물', { cls: 'ink', size: 'sm' }));
    g.push(txt(350, 334, 'K 는 ‘어디까지 가는가’만 말한다. ‘얼마나 빨리 가는가’는 전혀 말하지 않는다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'chem-eq-k-magnitude',
        title: '평형상수의 크기와 평형 조성',
        desc: 'A ⇌ B 형 반응에서 평형상수의 크기에 따른 평형 조성을 막대로 나타냈다. K 가 10⁻⁵ 이면 거의 반응물만 '
            + '남고, 10⁵ 이면 거의 생성물만 남는다. K 가 1 근처일 때만 양쪽이 비슷하다. K 는 반응이 어디까지 '
            + '가는지를 말할 뿐이며 얼마나 빨리 가는지는 전혀 말하지 않는다.',
        svg: svg({ width: W, height: H, title: 'K 의 크기', desc: '다섯 가지 K 값의 평형 조성 막대', body: g.join('') }),
    };
})());

/* 12-4. Q 와 K 를 견주어 방향을 정한다 */
add((() => {
    const W = 700, H = 320;
    const g = [txt(26, 34, '지금 값 Q 를 목표 값 K 와 견주면 갈 방향이 정해진다', { cls: 'ink bold' })];
    const y = 150, x0 = 80, x1 = 620, xK = 350;
    g.push(line([[x0, y], [x1, y]], { stroke: 'var(--ink2)', sw: 1.6 }));
    g.push(line([[xK, y - 34], [xK, y + 34]], { stroke: 'var(--ink)', sw: 2.4 }));
    g.push(txt(xK, y - 44, 'K', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(xK, y + 56, '평형 (Q = K)', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(xK, y + 78, '알짜 변화가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(circ(190, y, 6, { fill: 'var(--s1)', op: 0.9, stroke: 'none', sw: 0 }));
    g.push(txt(190, y - 20, 'Q', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(200, y, 330, y, { cls: 's1', marker: 'ar1', width: 2.6 }));
    g.push(txt(190, y + 56, 'Q < K — 생성물이 모자란다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(190, y + 78, '정반응으로 간다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(circ(510, y, 6, { fill: 'var(--s2)', op: 0.9, stroke: 'none', sw: 0 }));
    g.push(txt(510, y - 20, 'Q', { anchor: 'middle', cls: 'ink bold' }));
    g.push(px(500, y, 370, y, { cls: 's2', marker: 'ar2', width: 2.6 }));
    g.push(txt(510, y + 56, 'Q > K — 생성물이 넘친다', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(510, y + 78, '역반응으로 간다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(txt(x0 - 8, y + 5, '작다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(x1 + 8, y + 5, '크다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 276, 'Q 는 K 와 똑같은 꼴의 식에 지금 이 순간의 농도(또는 분압)를 넣은 값이다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 300, '어느 쪽이든 반응은 Q 를 K 쪽으로 끌고 간다. 방향 판정은 이 한 줄로 끝난다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-eq-q-vs-k',
        title: '반응지수 Q 와 평형상수 K 의 비교',
        desc: 'Q 는 K 와 똑같은 꼴의 식에 지금 이 순간의 농도나 분압을 넣은 값이다. Q 가 K 보다 작으면 생성물이 '
            + '모자란 상태이므로 정반응으로 가고, 크면 생성물이 넘친 상태이므로 역반응으로 간다. 어느 쪽이든 '
            + '반응은 Q 를 K 쪽으로 끌고 가며 Q = K 가 되면 멈춘다.',
        svg: svg({ width: W, height: H, title: 'Q 와 K', desc: '수직선 위에서 방향을 판정하는 그림', body: g.join('') }),
    };
})());

/* 12-5. 압력을 올리면 몰수가 적은 쪽으로 */
add((() => {
    const W = 700, H = 380;
    const g = [txt(26, 34, '부피를 줄이면 기체 알갱이 수가 적은 쪽으로 옮겨 간다', { cls: 'ink bold' })];
    const put = (x, y, w, h, pairs, singles) => {
        const out = [box(x, y, w, h, { stroke: 'var(--ink2)', sw: 1.8, rx: 4 })];
        pairs.forEach(([dx, dy]) => {
            out.push(circ(x + dx, y + dy, 9, { fill: 'var(--s1)', op: 0.55, stroke: 'var(--s1)', sw: 1.2 }));
            out.push(circ(x + dx + 15, y + dy, 9, { fill: 'var(--s1)', op: 0.55, stroke: 'var(--s1)', sw: 1.2 }));
        });
        singles.forEach(([dx, dy]) => {
            out.push(circ(x + dx, y + dy, 9, { fill: 'var(--s2)', op: 0.7, stroke: 'var(--s2)', sw: 1.2 }));
        });
        return out.join('');
    };
    g.push(txt(150, 74, '누르기 전', { anchor: 'middle', cls: 'ink bold' }));
    g.push(put(60, 90, 180, 180, [[30, 34], [110, 40], [40, 106], [122, 112]], [[36, 158], [92, 150], [148, 166]]));
    g.push(txt(150, 292, '기체 알갱이 7개', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(150, 312, '2SO₂ + O₂ ⇌ 2SO₃', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(px(266, 180, 356, 180, { cls: 's3', marker: 'ar3', width: 2.4 }));
    g.push(txt(311, 164, '부피를 절반으로', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(311, 202, '모든 농도가 2배가 되어', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(311, 220, 'Q 가 K 보다 작아진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(470, 74, '누른 뒤 새 평형', { anchor: 'middle', cls: 'ink bold' }));
    g.push(put(400, 130, 140, 140, [[26, 30], [80, 36]], [[30, 88], [82, 94], [40, 122], [96, 124]]));
    g.push(txt(470, 292, '기체 알갱이 6개', { anchor: 'middle', cls: 'ink' }));
    g.push(txt(470, 312, '왼쪽 3몰이 오른쪽 2몰로 바뀌었다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(350, 348, '이 규칙은 양쪽 기체 몰수가 다를 때만 뜻이 있다. 같으면 눌러도 평형이 움직이지 않는다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 372, '부피를 그대로 두고 아르곤 같은 기체만 넣는 것은 아무 영향이 없다. 각 성분의 농도가 그대로이기 때문이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-eq-pressure-shift',
        title: '부피를 줄이면 기체 몰수가 적은 쪽으로 옮겨 간다',
        desc: '기체 몰수가 왼쪽 3, 오른쪽 2 인 반응에서 부피를 절반으로 줄이면 모든 농도가 두 배가 되고 Q 가 K 보다 '
            + '작아진다. 그래서 정반응이 진행되어 알갱이 수가 줄어드는 쪽으로 옮겨 간다. 양쪽 기체 몰수가 같으면 '
            + '눌러도 평형이 움직이지 않으며, 부피를 그대로 두고 반응하지 않는 기체만 넣는 것도 영향이 없다.',
        svg: svg({ width: W, height: H, title: '압력과 평형 이동', desc: '누르기 전과 뒤의 알갱이 그림', body: g.join('') }),
    };
})());

/* 12-6. 온도만이 K 자체를 바꾼다 */
add((() => {
    const W = 700, H = 360;
    const f = frame({ xRange: [300, 900], yRange: [0, 10], box: { x: 92, y: 62, w: 420, h: 200 } });
    const g = [
        txt(26, 34, '온도만이 K 라는 수 자체를 바꾼다', { cls: 'ink bold' }),
        f.axes({ xLabel: 'T (K)', yLabel: 'K', xTicks: [300, 500, 700, 900], yTicks: [] }),
        f.curve(T => 8.6 * (1 - Math.exp(-(T - 300) / 190)), { cls: 's1' }),
        f.curve(T => 8.6 * Math.exp(-(T - 300) / 190), { cls: 's2' }),
    ];
    g.push(f.label([700, 8.1], '흡열 반응', { cls: 'ink bold' }));
    g.push(f.label([700, 7.1], '열을 반응물처럼 보면 된다', { cls: 'ink2', size: 'sm' }));
    g.push(f.label([700, 1.9], '발열 반응', { cls: 'ink bold' }));
    g.push(f.label([700, 0.9], '열을 생성물처럼 보면 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 100, '온도를 올리면', { cls: 'ink' }));
    g.push(txt(600, 122, '흡열 쪽 K 는 커지고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 142, '발열 쪽 K 는 작아진다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 176, '농도·압력·촉매는', { cls: 'ink' }));
    g.push(txt(600, 198, 'K 를 건드리지 못한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 218, 'Q 만 옮길 뿐이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 306, '세로 눈금은 반응마다 크게 다르므로 생략했다. 읽어야 할 것은 기울기의 방향이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 334, '암모니아 합성이 발열인데도 고온에서 돌아가는 것은 수득률을 속도와 맞바꾼 결과다',
        { anchor: 'middle', cls: 'ink' }));
    return {
        name: 'chem-eq-temperature-k',
        title: '온도가 평형상수를 바꾸는 방향',
        desc: '온도를 올리면 흡열 반응의 평형상수는 커지고 발열 반응의 평형상수는 작아진다. 열을 반응물 또는 '
            + '생성물처럼 취급하면 방향을 외우지 않고도 알 수 있다. 농도 변화, 압력 변화, 촉매는 K 라는 수를 '
            + '바꾸지 못하고 Q 만 옮긴다. 온도만이 K 자체를 바꾼다는 것이 르샤틀리에 표에서 가장 중요한 항목이다.',
        svg: svg({ width: W, height: H, title: '온도와 K', desc: '흡열과 발열 반응의 K 곡선', body: g.join('') }),
    };
})());

/* 12-7. 공통 이온 효과 */
add((() => {
    const W = 700, H = 330;
    const g = [txt(26, 34, '이미 들어 있는 이온이 용해를 막는다 — 눈금 한 칸이 10배다', { cls: 'ink bold' })];
    const x0 = 96, x1 = 620, y = 168;
    const E0 = -8, E1 = -3;
    const X = e => x0 + ((e - E0) / (E1 - E0)) * (x1 - x0);
    g.push(line([[x0 - 14, y], [x1 + 14, y]], { stroke: 'var(--ink2)', sw: 1.6 }));
    for (let e = E0; e <= E1; e += 1) {
        g.push(line([[X(e), y - 6], [X(e), y + 6]], { stroke: 'var(--ink2)', sw: 1 }));
        g.push(txt(X(e), y + 26, `10${['⁻⁸', '⁻⁷', '⁻⁶', '⁻⁵', '⁻⁴', '⁻³'][e - E0]}`,
            { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(x1 + 20, y + 26, 'mol/L', { cls: 'ink2', size: 'sm' }));

    const mark = (val, name, sub, slot, dy) => {
        const e = Math.log10(val);
        return circ(X(e), y, 6, { fill: `var(--s${slot})`, op: 0.95, stroke: 'none', sw: 0 })
            + line([[X(e), y - 6], [X(e), y + dy + 8]], { stroke: `var(--s${slot})`, sw: 1.2, dash: '3 3' })
            + txt(X(e), y + dy, name, { anchor: 'middle', cls: 'ink bold' })
            + txt(X(e), y + dy + 20, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' });
    };
    g.push(mark(2.1e-4, '순수한 물에서', '2.1 × 10⁻⁴ mol/L', 1, -56));
    g.push(mark(3.9e-7, '0.010 mol/L NaF 에서', '3.9 × 10⁻⁷ mol/L', 2, -100));
    g.push(px(X(Math.log10(2.1e-4)) - 6, 132, X(Math.log10(3.9e-7)) + 6, 132, { cls: 's2', marker: 'ar2', width: 2 }));
    g.push(txt((X(Math.log10(2.1e-4)) + X(Math.log10(3.9e-7))) / 2, 124, '약 540배 줄었다',
        { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(350, 254, 'CaF₂ 의 몰 용해도. F⁻ 가 이미 있으면 Q 가 곧바로 K~{sp} 에 닿아 더 녹지 못한다',
        { anchor: 'middle', cls: 'ink' }));
    g.push(txt(350, 280, '눈금 한 칸이 10배인 자를 쓴 이유는, 보통 자에서는 오른쪽 값이 점 하나로도 보이지 않기 때문이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(350, 306, '침전을 남김없이 거두려는 실험에서 침전제를 넉넉히 넣는 근거가 이것이다',
        { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'chem-eq-common-ion',
        title: '공통 이온 효과의 크기',
        desc: 'CaF₂ 의 몰 용해도는 순수한 물에서 2.1 × 10⁻⁴ mol/L 이지만 F⁻ 가 0.010 mol/L 들어 있는 용액에서는 '
            + '3.9 × 10⁻⁷ mol/L 로 약 540배 줄어든다. 이미 있는 이온 때문에 Q 가 곧바로 용해도곱에 닿아 더 녹지 '
            + '못하기 때문이다. 두 값의 자릿수가 크게 달라 눈금 한 칸이 10배인 자에 표시했다.',
        svg: svg({ width: W, height: H, title: '공통 이온 효과', desc: '10의 거듭제곱 눈금 위의 두 용해도', body: g.join('') }),
    };
})());

export default figures;
