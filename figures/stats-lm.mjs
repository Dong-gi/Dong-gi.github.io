/**
 * 통계학 11장(선형 모형)의 그림. 이름은 전부 `sta-lm-` 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(<img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 유니코드(x₁ β₂ …)로 적고, 라벨에 물결표 `~` 를 쓰지 않는다(lib 의 esc() 가
 * 아래첨자로 바꾼다). 따옴표는 ‘ ’, HTML 엔티티는 쓰지 않는다.
 * lib 의 px() 에 cls 'ark'·'ar1' 을 주면 선이 사라지므로 화살표는 이 모듈의 arw() 로 긋는다.
 * 그림 안의 수치(쿡 거리, VIF, 구간 끝점)는 모두 여기서 계산해 넣는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const neg = v => String(v).replace('-', '−');
const f2 = v => neg(v.toFixed(2));

/* ---------------- 공통 소도구 ---------------- */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink: 'var(--ink)', ink2: 'var(--ink2)' };

function arw(x1, y1, x2, y2, { c = 'ink2', width = 1.8, dash, both = false } = {}) {
    const mk = { s1: 'ar1', s2: 'ar2', s3: 'ar3' }[c] ?? 'ark';
    return `<path fill="none" stroke="${COL[c]}" stroke-width="${width}" stroke-linecap="round"`
        + `${both ? ` marker-start="url(#${mk})"` : ''} marker-end="url(#${mk})"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'none', op = 1, stroke = 'none', sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = 'var(--s1)', r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

function panel(x, y, w, h, title) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' });
}

/** 왼쪽·아래 가장자리에 세우는 축(데이터 0 이 범위 안에 있어도 가운데에 긋지 않는다). */
function axesEdge(fr, { x0, x1, y0, y1, xTicks = [], yTicks = [], fmtX = neg, fmtY = neg, xLabel, yLabel, grid = true }) {
    const out = [];
    if (grid) {
        for (const t of xTicks) out.push(`<path class="gr" d="M${fr.X(t)} ${fr.Y(y0)} V${fr.Y(y1)}"/>`);
        for (const t of yTicks) out.push(`<path class="gr" d="M${fr.X(x0)} ${fr.Y(t)} H${fr.X(x1)}"/>`);
    }
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} H${r2(fr.X(x1) + 10)}"/>`);
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`);
    for (const t of xTicks) out.push(txt(fr.X(t), fr.Y(y0) + 16, fmtX(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) out.push(txt(fr.X(x0) - 6, fr.Y(t) + 4, fmtY(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (xLabel) out.push(txt(fr.X(x1), fr.Y(y0) + 34, xLabel, { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (yLabel) out.push(txt(fr.X(x0) + 8, fr.Y(y1) - 4, yLabel, { cls: 'ink2', size: 'sm' }));
    return out.join('');
}

/* ------------------------------------------------------------------ *
 * 1. 정사영 — y 를 열공간에 수직으로 내린 그림자가 ŷ
 * ------------------------------------------------------------------ */
{
    const W = 660, H = 400;
    const P = [[60, 340], [430, 340], [600, 235], [230, 235]];
    const O = [170, 318];
    const Yh = [430, 282];
    const Yv = [430, 78];
    const Z = [322, 300];
    const c1 = [300, 330];
    const cx = [262, 262];
    const b = [];
    b.push(poly(P, { fill: 'var(--s1)', op: 0.1, stroke: 'var(--s1)', sw: 1.4 }));
    b.push(txt(76, 372, 'X 의 열공간 — 열들의 일차결합 Xb 를 모두 모은 평면', { cls: 'ink2', size: 'sm' }));
    // 열 벡터 둘
    b.push(arw(O[0], O[1], c1[0], c1[1], { c: 's1', width: 2 }));
    b.push(txt(c1[0] + 8, c1[1] - 2, '열 1 (모두 1)', { cls: 'ink2', size: 'sm' }));
    b.push(arw(O[0], O[1], cx[0], cx[1], { c: 's1', width: 2 }));
    b.push(txt(cx[0] - 70, cx[1] - 6, '열 x', { cls: 'ink2', size: 'sm' }));
    b.push(pdot(O[0], O[1], 'var(--ink2)', 3));
    b.push(txt(O[0] - 10, O[1] + 4, 'O', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // y, ŷ, e
    b.push(arw(O[0], O[1], Yv[0], Yv[1], { c: 'ink', width: 2.2 }));
    b.push(txt(Yv[0] + 10, Yv[1] + 4, 'y (관측값 n 개를 세운 벡터)', { cls: 'ink' }));
    b.push(arw(O[0], O[1], Yh[0], Yh[1], { c: 's2', width: 2.4 }));
    b.push(txt(Yh[0] + 12, Yh[1] + 22, 'ŷ = Hy = Xβ̂ (그림자)', { cls: 'ink' }));
    b.push(ln([Yv, Yh], { stroke: 'var(--s3)', sw: 2.2, dash: '6 4' }));
    b.push(txt(Yv[0] + 10, (Yv[1] + Yh[1]) / 2, 'e = y − ŷ (잔차)', { cls: 'ink' }));
    b.push(txt(Yv[0] + 10, (Yv[1] + Yh[1]) / 2 + 18, '평면의 모든 방향과 직교: Xᵀe = 0', { cls: 'ink2', size: 'sm' }));
    // 직각 표시
    b.push(ln([[Yh[0], Yh[1] - 14], [Yh[0] - 12, Yh[1] - 10], [Yh[0] - 12, Yh[1] + 4]], { stroke: 'var(--ink2)', sw: 1.2 }));
    // 평면 위 다른 점
    b.push(pdot(Z[0], Z[1], 'var(--ink2)', 3.5));
    b.push(ln([Yv, Z], { stroke: 'var(--ink2)', sw: 1.2, dash: '3 4' }));
    b.push(txt(Z[0] + 4, Z[1] - 10, 'Xb (다른 후보)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(250, 150, 'y 에서 평면 위 다른 점까지의', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(250, 166, '거리는 잔차보다 길다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-lm-projection',
        svg: svg({
            width: W, height: H, title: '최소제곱은 열공간으로의 정사영',
            desc: 'y 벡터를 설계행렬의 열공간 평면에 수직으로 내린 발이 적합값 ŷ 이고, 수직선이 잔차 e 다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 2. 가변수 — 평행선 둘 / 상호작용이면 기울기가 갈린다
 * ------------------------------------------------------------------ */
{
    const W = 780, H = 380;
    const b = [];
    const models = [
        { title: '가변수만: ŷ = 10 + 2x₁ + 3x₂', q: x => 13 + 2 * x },
        { title: '상호작용까지: … + 1.5x₁x₂', q: x => 13 + 3.5 * x },
    ];
    models.forEach((m, i) => {
        const ox = 10 + i * 385;
        b.push(panel(ox, 8, 375, 362, m.title));
        const fr = frame({ xRange: [0, 8], yRange: [0, 45], box: { x: ox + 48, y: 54, w: 220, h: 246 } });
        b.push(axesEdge(fr, { x0: 0, x1: 8, y0: 0, y1: 45, xTicks: [0, 2, 4, 6, 8], yTicks: [0, 10, 20, 30, 40], xLabel: 'x₁' }));
        const p = x => 10 + 2 * x;
        b.push(ln([[fr.X(0), fr.Y(p(0))], [fr.X(8), fr.Y(p(8))]], { stroke: 'var(--s1)', sw: 2.4 }));
        b.push(ln([[fr.X(0), fr.Y(m.q(0))], [fr.X(8), fr.Y(m.q(8))]], { stroke: 'var(--s2)', sw: 2.4, dash: '8 4' }));
        b.push(txt(fr.X(8) + 6, fr.Y(p(8)) + 4, '집단 P (x₂=0)', { cls: 'ink2', size: 'sm' }));
        b.push(txt(fr.X(8) + 6, fr.Y(m.q(8)) + 4, '집단 Q (x₂=1)', { cls: 'ink2', size: 'sm' }));
        // 간격 표시: x₁ = 4
        const g = m.q(4) - p(4);
        b.push(arw(fr.X(4), fr.Y(p(4)) - 2, fr.X(4), fr.Y(m.q(4)) + 2, { c: 'ink2', width: 1.4, both: true }));
        b.push(txt(fr.X(4) + 4, fr.Y(p(4)) + 22, `x₁=4 에서 간격 ${r2(g)}`, { cls: 'ink', size: 'sm' }));
        const g0 = m.q(0) - p(0);
        b.push(txt(fr.X(0) + 6, fr.Y(p(0)) + 20, `x₁=0 에서 간격 ${r2(g0)}`, { cls: 'ink2', size: 'sm' }));
        b.push(txt(ox + 187, 360, i === 0 ? '두 집단의 기울기가 같다(2)' : '집단 Q 의 기울기는 2 + 1.5 = 3.5', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    add({
        name: 'sta-lm-dummy',
        svg: svg({
            width: W, height: H, title: '가변수와 상호작용',
            desc: '가변수만 넣으면 두 집단의 직선이 평행하고, 상호작용 항을 넣으면 기울기가 갈려 간격이 x₁ 에 따라 달라진다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 3. 결합 신뢰영역(타원)과 따로 만든 두 구간(사각형)
 * ------------------------------------------------------------------ */
{
    const W = 640, H = 470;
    const bh = [1.2, 1.1], se = 0.8, rho = -0.9;
    const tCrit = 2.0518;          // t 분포 자유도 27 의 위쪽 2.5% 점
    const Fcrit = 3.3541;          // F(2, 27) 의 위쪽 5% 점
    const C = [[se * se, rho * se * se], [rho * se * se, se * se]];
    const det = C[0][0] * C[1][1] - C[0][1] ** 2;
    const Ci = [[C[1][1] / det, -C[0][1] / det], [-C[1][0] / det, C[0][0] / det]];
    const quad = (v) => {
        const d0 = v[0] - bh[0], d1 = v[1] - bh[1];
        return d0 * d0 * Ci[0][0] + 2 * d0 * d1 * Ci[0][1] + d1 * d1 * Ci[1][1];
    };
    const c = 2 * Fcrit;
    // 촐레스키
    const L11 = Math.sqrt(C[0][0]), L21 = C[1][0] / L11, L22 = Math.sqrt(C[1][1] - L21 * L21);
    const fr = frame({ xRange: [-1.2, 3.8], yRange: [-1.2, 3.8], box: { x: 70, y: 40, w: 380, h: 380 } });
    const b = [];
    b.push(axesEdge(fr, { x0: -1.2, x1: 3.8, y0: -1.2, y1: 3.8, xTicks: [-1, 0, 1, 2, 3], yTicks: [-1, 0, 1, 2, 3], xLabel: 'β₁', yLabel: 'β₂' }));
    b.push(ln([[fr.X(0), fr.Y(-1.2)], [fr.X(0), fr.Y(3.8)]], { stroke: 'var(--ink2)', sw: 0.8, dash: '2 3' }));
    b.push(ln([[fr.X(-1.2), fr.Y(0)], [fr.X(3.8), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 0.8, dash: '2 3' }));
    const ell = [];
    for (let i = 0; i <= 160; i += 1) {
        const a = (2 * Math.PI * i) / 160;
        const u = [Math.cos(a) * Math.sqrt(c), Math.sin(a) * Math.sqrt(c)];
        ell.push([fr.X(bh[0] + L11 * u[0]), fr.Y(bh[1] + L21 * u[0] + L22 * u[1])]);
    }
    b.push(poly(ell, { fill: 'var(--s1)', op: 0.18, stroke: 'var(--s1)', sw: 2 }));
    const lo = bh.map(v => v - tCrit * se), hi = bh.map(v => v + tCrit * se);
    b.push(box(fr.X(lo[0]), fr.Y(hi[1]), fr.X(hi[0]) - fr.X(lo[0]), fr.Y(lo[1]) - fr.Y(hi[1]), { stroke: 'var(--s2)', sw: 2, dash: '7 4', rx: 0 }));
    b.push(pdot(fr.X(bh[0]), fr.Y(bh[1]), 'var(--ink)', 4.5));
    b.push(txt(fr.X(bh[0]) + 8, fr.Y(bh[1]) - 8, `추정값 (${bh[0]}, ${bh[1]})`, { cls: 'ink', size: 'sm' }));
    // 원점
    b.push(`<circle cx="${fr.X(0)}" cy="${fr.Y(0)}" r="5" fill="none" stroke="var(--s2)" stroke-width="2"/>`);
    b.push(txt(fr.X(-1.1), fr.Y(-0.78), '원점: 사각형 안, 타원 밖', { cls: 'ink', size: 'sm' }));
    b.push(txt(fr.X(-1.1), fr.Y(-0.78) + 16, `(원점의 F = ${r2(quad([0, 0]) / 2)})`, { cls: 'ink2', size: 'sm' }));
    // 타원 안, 사각형 밖
    const q = [bh[0] + 2.5 / Math.SQRT2, bh[1] - 2.5 / Math.SQRT2];
    if (!(quad(q) <= c) || q[0] <= hi[0]) throw new Error('sta-lm-ellipse: 예시 점의 위치가 어긋났다');
    b.push(`<circle cx="${fr.X(q[0])}" cy="${fr.Y(q[1])}" r="4.5" fill="none" stroke="var(--s1)" stroke-width="2"/>`);
    b.push(txt(fr.X(3.2), fr.Y(q[1]) + 4, '타원 안, 사각형 밖', { cls: 'ink', size: 'sm' }));
    b.push(legend(470, 70, [{ slot: 2, name: '따로 만든 두 95% 구간' }, { slot: 1, name: '결합 95% 신뢰영역' }]));
    const notes = [
        `β₁ 구간 ${f2(lo[0])} … ${f2(hi[0])}`,
        `β₂ 구간 ${f2(lo[1])} … ${f2(hi[1])}`,
        '둘 다 0 을 품는다',
        '',
        '두 추정량의 상관 −0.9',
        '(두 설명변수의 상관이',
        ' 크고 양일 때 생긴다)',
    ];
    notes.forEach((s, i) => b.push(txt(470, 130 + i * 18, s, { cls: 'ink2', size: 'sm' })));
    add({
        name: 'sta-lm-ellipse',
        svg: svg({
            width: W, height: H, title: '결합 신뢰영역과 따로 만든 두 구간',
            desc: '두 계수의 결합 신뢰영역은 기울어진 좁은 타원이고, 따로 만든 두 구간의 사각형과 겹치지 않는 자리가 있다. 원점은 사각형 안에 있지만 타원 밖에 있다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 4. 쿡 거리 등고선
 * ------------------------------------------------------------------ */
{
    const W = 680, H = 440;
    const kp1 = 2, n = 20;
    const cook = (h, r) => (r * r / kp1) * h / (1 - h);
    const fr = frame({ xRange: [0, 0.8], yRange: [-3.5, 3.5], box: { x: 70, y: 40, w: 440, h: 340 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 0.8, y0: -3.5, y1: 3.5, xTicks: [0, 0.2, 0.4, 0.6, 0.8], yTicks: [-3, -2, -1, 0, 1, 2, 3], xLabel: '레버리지 h', yLabel: '표준화 잔차 r' }));
    b.push(ln([[fr.X(0), fr.Y(0)], [fr.X(0.8), fr.Y(0)]], { stroke: 'var(--ink2)', sw: 1 }));
    const lev = 2 * kp1 / n;
    b.push(ln([[fr.X(lev), fr.Y(-3.5)], [fr.X(lev), fr.Y(3.5)]], { stroke: 'var(--ink2)', sw: 1, dash: '3 3' }));
    b.push(txt(fr.X(lev) + 4, fr.Y(-3.2), `2(k+1)/n = ${lev}`, { cls: 'ink2', size: 'sm' }));
    for (const [D, dash, nm] of [[0.5, '7 4', 'D = 0.5'], [1, null, 'D = 1']]) {
        for (const s of [1, -1]) {
            const pts = [];
            const hMin = (2 * D) / (3.5 * 3.5 / 1 * 1 + 2 * D) * 1.0001;
            for (let i = 0; i <= 120; i += 1) {
                const h = hMin + ((0.8 - hMin) * i) / 120;
                const r = Math.sqrt(kp1 * D * (1 - h) / h);
                pts.push([fr.X(h), fr.Y(s * r)]);
            }
            b.push(ln(pts, { stroke: 'var(--s2)', sw: 1.8, dash }));
            if ((s === 1) === (D === 1)) b.push(txt(fr.X(0.8) + 6, fr.Y(s * Math.sqrt(kp1 * D * 0.2 / 0.8)) + 4, `쿡 거리 ${nm}`, { cls: 'ink2', size: 'sm' }));
        }
    }
    const pts = [['가', 0.05, 3.0], ['나', 0.60, 0.3], ['다', 0.45, 2.2], ['라', 0.25, -1.5]];
    for (const [nm, h, r] of pts) {
        const D = cook(h, r);
        b.push(pdot(fr.X(h), fr.Y(r), D >= 1 ? 'var(--s2)' : 'var(--s1)', 5));
        if (nm === '가') {
            b.push(ln([[fr.X(h), fr.Y(r) + 5], [fr.X(0.03), fr.Y(2.15)]], { stroke: 'var(--ink2)', sw: 0.8 }));
            b.push(txt(fr.X(0.01), fr.Y(2.0), `${nm}  D = ${f2(D)}`, { cls: 'ink', size: 'sm' }));
        } else {
            b.push(txt(fr.X(h) + 9, fr.Y(r) - 7, `${nm}  D = ${f2(D)}`, { cls: 'ink', size: 'sm' }));
        }
    }
    b.push(txt(fr.X(0.62), fr.Y(3.1), '오른쪽 위·아래 바깥:', { cls: 'ink2', size: 'sm' }));
    b.push(txt(fr.X(0.62), fr.Y(3.1) + 16, '힘과 어긋남이 함께 크다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 425, `n = ${n}, 계수 ${kp1}개. 쿡 거리 D = r² ÷ (k+1) × h ÷ (1 − h)`, { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-lm-cook',
        svg: svg({
            width: W, height: H, title: '레버리지와 잔차 평면 위의 쿡 거리',
            desc: '쿡 거리 등고선은 레버리지가 클수록 낮은 잔차에서 넘는다. 잔차만 큰 점, 레버리지만 큰 점은 영향이 작고 둘이 함께 큰 점이 영향점이다',
            body: b.join(''),
        }),
    });
}

/* ------------------------------------------------------------------ *
 * 5. 분산팽창인자
 * ------------------------------------------------------------------ */
{
    const W = 640, H = 400;
    const vif = r => 1 / (1 - r * r);
    const fr = frame({ xRange: [0, 1], yRange: [0, 30], box: { x: 64, y: 40, w: 440, h: 300 } });
    const b = [];
    b.push(axesEdge(fr, { x0: 0, x1: 1, y0: 0, y1: 30, xTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [0, 5, 10, 15, 20, 25, 30], xLabel: '두 설명변수의 상관 r', yLabel: 'VIF' }));
    const pts = [];
    for (let i = 0; i <= 300; i += 1) {
        const r = (0.983 * i) / 300;
        pts.push([fr.X(r), fr.Y(vif(r))]);
    }
    b.push(ln(pts, { stroke: 'var(--s1)', sw: 2.4 }));
    b.push(ln([[fr.X(0), fr.Y(10)], [fr.X(1), fr.Y(10)]], { stroke: 'var(--ink2)', sw: 1, dash: '4 3' }));
    b.push(txt(fr.X(0.02), fr.Y(10) - 6, '흔히 쓰는 관례적 문턱 10', { cls: 'ink2', size: 'sm' }));
    const marks = [[0.5, 'end', -8, -10], [0.8, 'end', -8, -10], [0.9, 'end', -10, -6], [0.95, 'end', -10, 16], [0.98, 'end', -10, 4]];
    for (const [r, anchor, dx, dy] of marks) {
        const v = vif(r);
        b.push(pdot(fr.X(r), fr.Y(v), 'var(--s2)', 4));
        b.push(txt(fr.X(r) + dx, fr.Y(v) + dy, `r=${r}: VIF ${v.toFixed(2)}, SE ×${Math.sqrt(v).toFixed(2)}`, { anchor, cls: 'ink', size: 'sm' }));
    }
    b.push(txt(64, 385, '설명변수가 둘이면 VIF = 1 ÷ (1 − r²). 표준오차는 √VIF 배로 늘어난다', { cls: 'ink2', size: 'sm' }));
    add({
        name: 'sta-lm-vif',
        svg: svg({
            width: W, height: H, title: '상관에 따른 분산팽창인자',
            desc: '두 설명변수의 상관이 0.9 를 넘으면 분산팽창인자가 가파르게 오른다',
            body: b.join(''),
        }),
    });
}

export default figures;
