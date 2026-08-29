/**
 * 심리학 문서 3·4·5장의 그림.
 *   3장 — 심리학은 무엇을 하는 학문인가
 *   4장 — 어떻게 알아내는가
 *   5장 — 재는 일: 심리측정
 *
 * 이름은 모두 `psy-m-` 로 시작한다(3·4·5장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 * 그리스 문자와 기호는 유니코드로 적는다(σ, μ, ×, ≈, →).
 *
 * 이 파일의 그림 가운데 자료를 찍는 것은 전부 <b>도식</b>이다. 실재하는 연구의
 * 원자료를 옮긴 것이 아니라 관계의 모양만 보이는 그림이며, 문서의 캡션에도
 * 그렇게 적었다. 난수를 쓰는 곳은 씨앗을 고정한 선형합동생성기를 쓴다.
 * 빌드할 때마다 그림이 달라지면 안 되기 때문이다.
 *
 * 상자와 화살표만으로 되는 그림(분야 지도·계보·판정 흐름)은 d2/psychology/ 에 있다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)' };

/** lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에 그 클래스가 없어 선이 사라진다. */
function arw(x1, y1, x2, y2, { cls = 'ark', width = 2, dash } = {}) {
    const col = { s1: COL.s1, s2: COL.s2, s3: COL.s3, ark: COL.ink2 }[cls] ?? COL.ink2;
    const mk = cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark';
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = COL.ink2, sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 사각형. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.4, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 화소 좌표 원. */
function circ(cx, cy, r, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 채운 점. */
const pdot = (x, y, col = COL.s1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/** 씨앗 고정 난수. 빌드마다 그림이 달라지면 안 된다. */
function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

/** 표준정규 근사(박스-뮐러). 산포도의 흩어짐을 만드는 데만 쓴다. */
function gauss(rand) {
    const u = Math.max(rand(), 1e-9);
    const v = rand();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

/** 종 모양 곡선 하나. 높이는 1 로 맞춘다(면적이 아니라 모양만 보이는 그림이다). */
const bell = (mu, sd) => x => Math.exp(-((x - mu) ** 2) / (2 * sd * sd));

/* ================================================================== *
 * 3장 — 심리학은 무엇을 하는 학문인가
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * psy-m-hindsight — 사후과잉확신.
 *
 * 무엇을 읽어야 하나: 결과를 알기 전과 알고 난 뒤의 추정이 다르다는 것,
 * 그리고 뒤쪽 사람은 자기가 원래 그렇게 생각했다고 믿는다는 것.
 * 숫자는 가상의 값이며 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 330;
    const g = [];
    const rows = [
        { y: 96, name: '결과를 모르는 집단', sub: '‘이 실험이 성공할 확률은?’', vals: [30, 42, 48, 55, 62, 70], col: COL.s1 },
        { y: 200, name: '결과를 아는 집단', sub: '‘성공했다고 들었다. 미리 알았다면 얼마로 봤겠는가?’', vals: [62, 70, 74, 78, 84, 90], col: COL.s2 },
    ];
    const X = v => 200 + (v / 100) * 540;

    g.push(txt(W / 2, 34, '같은 물음, 다른 순서', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 54, '가상의 자료로 만든 도식이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    for (const r of rows) {
        g.push(txt(24, r.y - 4, r.name, { cls: 'ink bold' }));
        g.push(txt(24, r.y + 14, r.sub, { cls: 'ink2', size: 'sm' }));
        g.push(ln([[X(0), r.y + 30], [X(100), r.y + 30]], { stroke: COL.grid, sw: 1.4 }));
        for (const v of r.vals) g.push(pdot(X(v), r.y + 30, r.col, 5));
        const m = r.vals.reduce((a, b) => a + b, 0) / r.vals.length;
        g.push(ln([[X(m), r.y + 14], [X(m), r.y + 46]], { stroke: r.col, sw: 2.4 }));
        g.push(txt(X(m), r.y + 62, `평균 ${Math.round(m)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    for (const t of [0, 25, 50, 75, 100]) {
        g.push(txt(X(t), 290, `${t}%`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(ln([[X(t), 276], [X(t), 282]], { stroke: COL.grid, sw: 1.2 }));
    }
    g.push(ln([[X(0), 276], [X(100), 276]], { stroke: COL.ink2, sw: 1.4 }));
    g.push(txt(W / 2, 314, '결과를 아는 쪽은 자기 추정이 결과 때문에 올라갔다는 것을 알아채지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    return { name: 'psy-m-hindsight', svg: svg({ width: W, height: H, title: '사후과잉확신', desc: '결과를 모르는 집단과 아는 집단의 확률 추정 비교 도식', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-history-line — 무엇을 자료로 인정했는가의 변화.
 *
 * 무엇을 읽어야 하나: 세로축이 ‘무엇을 자료로 받아들이는가’ 다. 학파가 바뀐 것은
 * 주제가 바뀐 것이 아니라 자료로 인정하는 것의 폭이 바뀐 것이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 340;
    const g = [];
    const X = y => 70 + ((y - 1870) / 170) * 730;
    const marks = [
        { y: 1879, lab: '분트, 라이프치히 실험실', sub: '내성 보고를 자료로 삼는다', row: 0, col: COL.s1 },
        { y: 1885, lab: '에빙하우스, 기억 실험', sub: '기억을 재는 절차를 만든다', row: 1, col: COL.s1 },
        { y: 1913, lab: '왓슨, 행동주의 선언', sub: '내성을 자료에서 뺀다', row: 2, col: COL.s2 },
        { y: 1956, lab: '인지혁명이 시작된다', sub: '속의 처리 과정을 모형으로 세운다', row: 0, col: COL.s3 },
        { y: 1980, lab: '인지신경과학', sub: '뇌 측정이 자료로 들어온다', row: 1, col: COL.s3 },
        { y: 2011, lab: '재현 위기', sub: '자료 얻는 절차 자체를 검사한다', row: 2, col: COL.s2 },
    ];

    g.push(txt(W / 2, 30, '심리학이 자료로 인정한 것의 변화', { anchor: 'middle', cls: 'ink bold' }));
    g.push(ln([[50, 296], [830, 296]], { stroke: COL.ink2, sw: 1.6 }));
    for (const t of [1880, 1920, 1960, 2000, 2030]) {
        if (t > 2025) continue;
        g.push(ln([[X(t), 296], [X(t), 303]], { stroke: COL.ink2, sw: 1.3 }));
        g.push(txt(X(t), 320, String(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    const rowY = [70, 140, 210];
    for (const m of marks) {
        const y = rowY[m.row];
        g.push(ln([[X(m.y), y + 34], [X(m.y), 296]], { stroke: COL.grid, sw: 1.2, dash: '4 4' }));
        g.push(pdot(X(m.y), 296, m.col, 5));
        g.push(box(X(m.y) - 86, y - 16, 172, 50, { stroke: m.col, sw: 1.5 }));
        g.push(txt(X(m.y), y + 2, `${m.y}  ${m.lab}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(txt(X(m.y), y + 22, m.sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return { name: 'psy-m-history-line', svg: svg({ width: W, height: H, title: '심리학사의 자료 개념 변화', desc: '1879년부터 지금까지 자료로 인정된 것의 변화를 표시한 시간선', body: g.join('') }) };
})());

/* ================================================================== *
 * 4장 — 어떻게 알아내는가
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * psy-m-scatter-three — 상관계수가 잡는 것과 놓치는 것.
 *
 * 무엇을 읽어야 하나: 가운데와 오른쪽은 상관계수가 거의 같은데 관계는 전혀 다르다.
 * 계수 하나만 보고 자료를 안다고 하면 안 된다는 것.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 320;
    const g = [];
    const panels = [
        { x: 40, title: '강한 양의 관계', note: '계수가 크다', kind: 'lin' },
        { x: 320, title: '약한 관계', note: '계수가 0 에 가깝다', kind: 'weak' },
        { x: 600, title: '뚜렷한 곡선 관계', note: '계수는 여기서도 0 에 가깝다', kind: 'curve' },
    ];
    const rand = rng(20260829);

    for (const p of panels) {
        const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: p.x, y: 80, w: 200, h: 170 } });
        g.push(box(p.x - 6, 74, 212, 182, { stroke: COL.grid, sw: 1.2 }));
        g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
        for (let i = 0; i < 26; i += 1) {
            const xv = 0.6 + rand() * 8.8;
            let yv;
            if (p.kind === 'lin') yv = 0.9 * xv + 0.6 + gauss(rand) * 0.9;
            else if (p.kind === 'weak') yv = 5 + gauss(rand) * 2.2;
            else yv = 9.2 - 0.34 * (xv - 5) * (xv - 5) + gauss(rand) * 0.45;
            yv = Math.min(9.6, Math.max(0.4, yv));
            g.push(f.dot([xv, yv], { cls: 'f1', r: 3.4 }));
        }
        g.push(txt(p.x + 100, 64, p.title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(p.x + 100, 278, p.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, 30, '상관계수는 직선 관계의 세기만 잰다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 306, '가운데와 오른쪽은 계수가 비슷하지만 자료는 전혀 다르다. 계수를 보기 전에 그림을 그려야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-m-scatter-three', svg: svg({ width: W, height: H, title: '상관계수가 잡는 관계와 놓치는 관계', desc: '직선 관계, 관계 없음, 곡선 관계의 산점도 세 개', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-regress-mean — 평균으로의 회귀.
 *
 * 무엇을 읽어야 하나: 아무 처치도 하지 않았는데 극단값 집단의 평균이 두 번째 측정에서
 * 가운데로 움직인다. 처치 효과처럼 보이는 것이 여기서 저절로 만들어진다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 350;
    const g = [];
    const rand = rng(424242);
    const f = frame({ xRange: [0, 3], yRange: [16, 104], box: { x: 110, y: 60, w: 300, h: 220 } });

    const pts = [];
    for (let i = 0; i < 40; i += 1) {
        const t = 60 + gauss(rand) * 13;
        const a = t + gauss(rand) * 9;
        const b = t + gauss(rand) * 9;
        pts.push([Math.min(101, Math.max(19, a)), Math.min(101, Math.max(19, b))]);
    }
    const low = pts.filter(p => p[0] <= 48);

    g.push(f.axes({ xTicks: [], yTicks: [30, 50, 70, 90], grid: true }));
    g.push(txt(f.X(1), 300, '1차 측정', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(2), 300, '2차 측정', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(80, 52, '점수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    for (const p of pts) {
        const isLow = p[0] <= 48;
        g.push(ln([[f.X(1), f.Y(p[0])], [f.X(2), f.Y(p[1])]], { stroke: isLow ? COL.s2 : COL.grid, sw: isLow ? 1.8 : 1 }));
    }
    const m1 = low.reduce((a, p) => a + p[0], 0) / low.length;
    const m2 = low.reduce((a, p) => a + p[1], 0) / low.length;
    g.push(pdot(f.X(1), f.Y(m1), COL.s2, 6));
    g.push(pdot(f.X(2), f.Y(m2), COL.s2, 6));
    g.push(ln([[f.X(1), f.Y(m1)], [f.X(2), f.Y(m2)]], { stroke: COL.s2, sw: 3.2 }));
    g.push(txt(f.X(1) - 10, f.Y(m1) + 5, `${Math.round(m1)}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(2) + 10, f.Y(m2) + 5, `${Math.round(m2)}`, { cls: 'ink bold', size: 'sm' }));

    g.push(box(452, 78, 336, 90, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(468, 102, '굵은 선 — 1차에서 아래쪽만 골라낸 사람들', { cls: 'ink', size: 'sm' }));
    g.push(txt(468, 124, '아무 처치도 하지 않았다. 두 측정 사이에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(468, 144, '한 일이 하나도 없다', { cls: 'ink2', size: 'sm' }));

    g.push(box(452, 186, 336, 112, { stroke: COL.grid, sw: 1.4 }));
    g.push(txt(468, 210, '그런데 평균이 올라갔다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(468, 232, '점수에는 실력과 그날의 우연이 섞여 있고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(468, 252, '아래쪽만 고르면 우연이 나쁜 쪽이던 사람이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(468, 272, '많이 뽑힌다. 그 우연은 되풀이되지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 30, '평균으로의 회귀 — 씨앗을 고정한 난수로 만든 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 332, '처치를 준 뒤 이 그림을 보면 처치가 효과를 낸 것처럼 읽힌다. 통제 집단이 필요한 이유가 이것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-m-regress-mean', svg: svg({ width: W, height: H, title: '평균으로의 회귀', desc: '극단값 집단이 두 번째 측정에서 평균 쪽으로 움직이는 도식', body: g.join('') }) };
})());

/* ================================================================== *
 * 5장 — 재는 일: 심리측정
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * psy-m-target — 신뢰도와 타당도.
 *
 * 무엇을 읽어야 하나: 네 칸 가운데 왼쪽 아래가 가장 위험하다. 흩어짐이 없어서
 * 잘 재고 있는 것처럼 보이는데 과녁 한복판이 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 430;
    const g = [];
    const rand = rng(777);
    const panels = [
        { cx: 180, cy: 130, rel: false, val: true, t: '신뢰도 낮음 · 타당도 있음', s: '가운데를 겨누지만 흩어진다' },
        { cx: 520, cy: 130, rel: true, val: true, t: '신뢰도 높음 · 타당도 있음', s: '노리는 것' },
        { cx: 180, cy: 320, rel: false, val: false, t: '신뢰도 낮음 · 타당도 없음', s: '쓸 수 없다' },
        { cx: 520, cy: 320, rel: true, val: false, t: '신뢰도 높음 · 타당도 없음', s: '가장 위험한 칸' },
    ];
    for (const p of panels) {
        for (const r of [58, 40, 22]) g.push(circ(p.cx, p.cy, r, { stroke: COL.grid, sw: 1.3 }));
        g.push(pdot(p.cx, p.cy, COL.ink2, 2));
        const ox = p.val ? 0 : 32;
        const oy = p.val ? 0 : -26;
        const sd = p.rel ? 6 : 26;
        for (let i = 0; i < 8; i += 1) {
            g.push(pdot(p.cx + ox + gauss(rand) * sd, p.cy + oy + gauss(rand) * sd, p.rel && !p.val ? COL.s2 : COL.s1, 4.5));
        }
        g.push(txt(p.cx, p.cy + 84, p.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(p.cx, p.cy + 104, p.s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(box(660, 232, 148, 166, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(674, 258, '왜 위험한가', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(674, 280, '재검사 상관이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(674, 300, '높게 나오므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(674, 320, '‘좋은 도구’ 로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(674, 340, '보인다. 신뢰도는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(674, 360, '타당도를 보증하지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(674, 380, '않는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 34, '신뢰도와 타당도는 다른 것을 잰다', { anchor: 'middle', cls: 'ink bold' }));
    return { name: 'psy-m-target', svg: svg({ width: W, height: H, title: '신뢰도와 타당도', desc: '과녁 네 개로 신뢰도와 타당도의 네 조합을 보인 그림', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-standard-score — 표준점수와 백분위.
 *
 * 무엇을 읽어야 하나: 표준점수는 등간이고 백분위는 등간이 아니다. 가운데에서는
 * 같은 표준점수 차이가 큰 백분위 차이를, 바깥에서는 작은 백분위 차이를 만든다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 370;
    const g = [];
    const f = frame({ xRange: [-3.4, 3.4], yRange: [0, 1.16], box: { x: 70, y: 62, w: 700, h: 150 } });
    g.push(f.curve(bell(0, 1), { cls: 's1' }));
    g.push(ln([[f.X(-3.4), f.Y(0)], [f.X(3.4), f.Y(0)]], { stroke: COL.ink2, sw: 1.4 }));

    const pct = { '-2': 2, '-1': 16, 0: 50, 1: 84, 2: 98 };
    for (const z of [-3, -2, -1, 0, 1, 2, 3]) {
        g.push(ln([[f.X(z), f.Y(0)], [f.X(z), f.Y(0) + 7]], { stroke: COL.ink2, sw: 1.3 }));
        g.push(txt(f.X(z), f.Y(0) + 26, `${z}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (String(z) in pct) {
            g.push(ln([[f.X(z), f.Y(0)], [f.X(z), f.Y(bell(0, 1)(z))]], { stroke: COL.grid, sw: 1.2, dash: '4 4' }));
            g.push(txt(f.X(z), 268, `${pct[String(z)]}`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
    }
    g.push(txt(48, f.Y(0) + 26, 'z', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(48, 268, '백분위', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[70, 248], [770, 248]], { stroke: COL.grid, sw: 1.2 }));

    g.push(ln([[f.X(0), 292], [f.X(1), 292]], { stroke: COL.s2, sw: 3 }));
    g.push(txt(f.X(0.5), 316, 'z 가 0 에서 1 로 오르면 백분위는 34 만큼 오른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[f.X(2), 336], [f.X(3), 336]], { stroke: COL.s3, sw: 3 }));
    g.push(txt(640, 360, 'z 가 2 에서 3 으로 오르면 백분위는 2 만큼만 오른다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 32, '같은 표준점수 차이가 같은 백분위 차이를 뜻하지 않는다', { anchor: 'middle', cls: 'ink bold' }));
    return { name: 'psy-m-standard-score', svg: svg({ width: W, height: H, title: '표준점수와 백분위', desc: '표준점수 축과 백분위 축을 나란히 놓아 둘이 등간이 아님을 보인 그림', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-effect-overlap — 효과크기 d 가 눈에 보이는 모양.
 *
 * 무엇을 읽어야 하나: d = 0.2 는 거의 겹쳐 보이고 d = 0.8 도 상당히 겹친다.
 * ‘큰 효과’ 조차 두 집단을 갈라 놓지 못한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 860;
    const H = 300;
    const g = [];
    const ds = [
        { d: 0.2, lab: 'd = 0.2', note: '관례상 ‘작다’' },
        { d: 0.5, lab: 'd = 0.5', note: '관례상 ‘중간’' },
        { d: 0.8, lab: 'd = 0.8', note: '관례상 ‘크다’' },
    ];
    ds.forEach((it, i) => {
        const x0 = 30 + i * 276;
        const f = frame({ xRange: [-3.6, 4.2], yRange: [0, 1.25], box: { x: x0, y: 74, w: 240, h: 130 } });
        g.push(box(x0 - 10, 66, 256, 150, { stroke: COL.grid, sw: 1.2 }));
        g.push(f.curve(bell(0, 1), { cls: 's1' }));
        g.push(f.curve(bell(it.d, 1), { cls: 's2', dash: '6 4' }));
        g.push(ln([[f.X(-3.6), f.Y(0)], [f.X(4.2), f.Y(0)]], { stroke: COL.ink2, sw: 1.2 }));
        g.push(ln([[f.X(0), f.Y(0)], [f.X(0), f.Y(1.02)]], { stroke: COL.grid, sw: 1.1, dash: '3 3' }));
        g.push(ln([[f.X(it.d), f.Y(0)], [f.X(it.d), f.Y(1.02)]], { stroke: COL.grid, sw: 1.1, dash: '3 3' }));
        g.push(txt(x0 + 118, 56, it.lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x0 + 118, 236, it.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(W / 2, 30, '효과크기 d 가 실제로 어떻게 보이는가', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 268, '실선이 통제 집단, 점선이 처치 집단이다. d 는 두 봉우리 사이 거리를 흩어짐으로 나눈 값이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 288, '가장 오른쪽 칸조차 두 집단이 크게 겹친다. ‘큰 효과’ 는 개인을 가려내지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-m-effect-overlap', svg: svg({ width: W, height: H, title: '효과크기 d 의 크기 감각', desc: 'd 가 0.2, 0.5, 0.8 일 때 두 집단 분포가 얼마나 겹치는지 보인 그림', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-effect-inflation — 유의한 것만 남기면 효과크기가 부풀려진다.
 *
 * 무엇을 읽어야 하나: 유의성 문턱은 표본이 작을수록 높은 곳에 있다. 작은 연구가
 * 유의하려면 큰 효과가 나와야 하므로, 살아남은 작은 연구의 추정값은 참값보다 크다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 396;
    const g = [];
    const rand = rng(31337);
    const f = frame({ xRange: [8, 200], yRange: [-0.5, 1.5], box: { x: 96, y: 78, w: 500, h: 210 } });
    const TRUE = 0.3;

    g.push(f.axes({ xTicks: [20, 60, 100, 140, 180], yTicks: [-0.5, 0, 0.5, 1.0, 1.5], grid: true }));
    g.push(txt(26, 64, '관측된 효과크기 d', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(104), 322, '표본 크기', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 유의성 문턱은 대략 표본 크기의 제곱근에 반비례한다. 정확한 상수가 아니라 모양만 보인다.
    g.push(f.curve(x => 5.6 / Math.sqrt(x), { from: 13.95, to: 200, cls: 's2', dash: '7 4' }));
    g.push(f.line([[8, TRUE], [200, TRUE]], { cls: 's3' }));

    for (let i = 0; i < 90; i += 1) {
        const nn = 12 + Math.floor(rand() * 180);
        const d = TRUE + gauss(rand) * (2.6 / Math.sqrt(nn));
        const sig = d > 5.6 / Math.sqrt(nn);
        g.push(f.dot([nn, Math.max(-0.48, Math.min(1.48, d))], { cls: sig ? 'f2' : 'f1', r: sig ? 4 : 2.6 }));
    }
    g.push(ln([[120, 350], [156, 350]], { stroke: COL.s3, sw: 3 }));
    g.push(txt(166, 355, '참 효과크기', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[286, 350], [322, 350]], { stroke: COL.s2, sw: 2.4, dash: '7 4' }));
    g.push(txt(332, 355, '유의성 문턱 — 이 위로 올라간 연구가 유의하다고 보고된다', { cls: 'ink2', size: 'sm' }));

    g.push(box(624, 92, 200, 166, { stroke: COL.s2, sw: 1.5 }));
    g.push(txt(640, 118, '큰 점만 출판되면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(640, 142, '문헌에 남는 값이 참값보다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 162, '크다. 그리고 표본이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 182, '작을수록 더 크게', { cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 202, '부풀린다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(640, 232, '6장이 이것을 다시 쓴다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 30, '작은 표본에서 유의한 결과는 효과를 부풀린다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 386, '문턱 곡선의 정확한 위치는 검정 방법이 정한다. 여기서는 왼쪽에서 높고 오른쪽에서 낮다는 모양만 보인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-m-effect-inflation', svg: svg({ width: W, height: H, title: '작은 표본의 효과크기 부풀림', desc: '표본 크기와 관측 효과크기의 산포에 유의성 문턱 곡선을 얹은 도식', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-m-power-n — 표본 크기와 검정력.
 *
 * 무엇을 읽어야 하나: 작은 효과를 잡으려면 표본이 급격히 커져야 한다.
 * 심리학이 흔히 쓰던 표본 크기에서는 작은 효과에 대한 검정력이 매우 낮다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 386;
    const g = [];
    const f = frame({ xRange: [0, 320], yRange: [0, 1.0], box: { x: 96, y: 74, w: 470, h: 200 } });
    // 두 집단 t 검정의 검정력을 정규 근사로 그린 것이다. 정확한 값은 확률·통계 문서에서 구한다.
    const Phi = z => 0.5 * (1 + Math.sign(z) * Math.sqrt(1 - Math.exp(-2 * z * z / Math.PI)));
    const power = d => nPerGroup => Phi(d * Math.sqrt(nPerGroup / 2) - 1.96);

    g.push(f.axes({ yLabel: '검정력', xTicks: [0, 80, 160, 240, 320], yTicks: [0, 0.25, 0.5, 0.8, 1.0], grid: true }));
    g.push(txt(f.X(160), 308, '집단당 표본 크기', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.curve(power(0.8), { from: 2, to: 320, cls: 's3' }));
    g.push(f.curve(power(0.5), { from: 2, to: 320, cls: 's1' }));
    g.push(f.curve(power(0.2), { from: 2, to: 320, cls: 's2' }));
    g.push(f.line([[0, 0.8], [320, 0.8]], { cls: 'ax', dash: '5 4' }));

    const leg = [
        { x: 96, col: COL.s3, name: '효과크기 d = 0.8' },
        { x: 268, col: COL.s1, name: 'd = 0.5' },
        { x: 388, col: COL.s2, name: 'd = 0.2' },
    ];
    for (const it of leg) {
        g.push(ln([[it.x, 340], [it.x + 32, 340]], { stroke: it.col, sw: 3 }));
        g.push(txt(it.x + 40, 345, it.name, { cls: 'ink2', size: 'sm' }));
    }
    g.push(ln([[492, 340], [524, 340]], { stroke: COL.ink2, sw: 2, dash: '5 4' }));
    g.push(txt(532, 345, '검정력 0.8 — 흔히 잡는 목표선', { cls: 'ink2', size: 'sm' }));

    g.push(box(590, 96, 216, 132, { stroke: COL.grid, sw: 1.3 }));
    g.push(txt(606, 122, '읽을 것', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(606, 146, 'd = 0.2 곡선은 집단당', { cls: 'ink2', size: 'sm' }));
    g.push(txt(606, 166, '320명을 써도 목표선에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(606, 186, '닿지 못한다. 작은 효과는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(606, 206, '작은 연구로 잡을 수 없다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 30, '작은 효과를 잡으려면 표본이 몇 배로 커져야 한다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 376, '두 집단을 비교하는 흔한 상황을 정규 근사로 그린 곡선이다. 정확한 계산은 확률·통계 문서의 몫이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-m-power-n', svg: svg({ width: W, height: H, title: '표본 크기와 검정력', desc: '효과크기별 검정력 곡선 세 개', body: g.join('') }) };
})());

export default figures;
