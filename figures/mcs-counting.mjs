/**
 * mcs 16장(합과 점근) · 17장(셈의 규칙) · 18장(생성함수)의 그림.
 *
 * 이름은 모두 `mcs-c-` 로 시작한다(담당 E 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `n~0` 표기를, 나머지는 유니코드(≤ ≥ ≠ ⌊ ⌋ Σ ∫ √ ² ³ · × ⋯ → ⟶)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 주제는 셈이다. 그래서 그림도 대개 ‘같은 것을 두 방식으로 센다’는
 * 구조 — 어느 쪽에서 세면 무엇이 보이는가 — 를 나란히 놓는 모양이 된다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CI = 'var(--ink)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구 — lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * (figures/mcs-proof.mjs 의 같은 헬퍼를 본떴다.)
 * ------------------------------------------------------------------ */

function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG }[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 곡선 경로(베지어). 화살촉을 붙일 수 있다. */
function curvePath(d, { stroke = CK, sw = 1.5, dash, marker } = {}) {
    return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}${marker ? ` marker-end="url(#${marker})"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const ring = (x, y, r, col = C2, sw = 2) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="none" stroke="${col}" stroke-width="${sw}"/>`;

const disc = (x, y, r, col = C1, op = 0.16, stroke = null) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="${col}" fill-opacity="${op}"${stroke ? ` stroke="${stroke}" stroke-width="1.4"` : ''}/>`;

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 칸 한 줄. items 의 값이 null 이면 빈 칸. */
function cells(x, y, w, h, items, { hl = {}, idx = null, idxTop = false, sw = 1.2, small = false } = {}) {
    const g = [];
    items.forEach((v, i) => {
        const cx = x + i * w;
        const col = hl[i];
        g.push(box(cx, y, w, h, { fill: col ?? 'none', op: col ? 0.22 : 1, stroke: col ?? CK, sw: col ? 1.9 : sw, rx: 2 }));
        if (v !== null && v !== undefined && v !== '') {
            g.push(txt(cx + w / 2, y + h / 2 + 5, String(v), { anchor: 'middle', cls: 'ink', size: small || w < 34 ? 'sm' : undefined }));
        }
        if (idx) {
            const ty = idxTop ? y - 6 : y + h + 14;
            g.push(txt(cx + w / 2, ty, String(idx[i]), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    });
    return g.join('');
}

/** 작은 패널용 축. lib 의 axes 는 글자가 커서 좁은 칸에서 겹친다. */
function axes2(f, { xRange, yRange, xTicks = [], yTicks = [], xLabel, yLabel, fmt = String } = {}) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const ax = y0 <= 0 && 0 <= y1 ? f.Y(0) : f.Y(y0);
    const ay = x0 <= 0 && 0 <= x1 ? f.X(0) : f.X(x0);
    const g = [arw(f.X(x0), ax, f.X(x1) + 12, ax, { cls: 'ark', width: 1.2 }),
        arw(ay, f.Y(y0), ay, f.Y(y1) - 12, { cls: 'ark', width: 1.2 })];
    for (const t of xTicks) {
        g.push(ln([[f.X(t), ax], [f.X(t), ax + 4]], { sw: 1 }));
        g.push(txt(f.X(t), ax + 17, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const t of yTicks) {
        g.push(ln([[ay - 4, f.Y(t)], [ay, f.Y(t)]], { sw: 1 }));
        g.push(txt(ay - 8, f.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    if (xLabel) g.push(txt(f.X(x1) + 16, ax + 5, xLabel, { cls: 'ink2', size: 'sm' }));
    if (yLabel) g.push(txt(ay + 6, f.Y(y1) - 16, yLabel, { anchor: 'start', cls: 'ink2', size: 'sm' }));
    return g.join('');
}

/** 좌표계 위 곡선을 직접 색을 넣어 그린다. lib 의 curve 는 클래스만 받는다. */
function fcurve(f, fn, { from, to, steps = 200, stroke = C1, sw = 2.2, dash } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const xv = from + ((to - from) * i) / steps;
        pts.push([f.X(xv), f.Y(fn(xv))]);
    }
    return ln(pts, { stroke, sw, dash });
}

/* ================================================================== *
 * 16장 — 합과 점근
 * ================================================================== */

/* ---- 16-1. 연금: 나중에 받는 돈은 기하급수적으로 값이 깎인다 ---- */
add((() => {
    const W = 780, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '해마다 같은 돈을 받아도 오늘 값어치는 기하급수로 줄어든다', { anchor: 'middle', cls: 'ink bold' }));

    const p = 0.08, n = 20;
    const xR = [0, 21], yR = [0, 1.08];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 66, y: 58, w: 420, h: 202 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [1, 5, 10, 15, 20], yTicks: [0, 0.5, 1],
        xLabel: '몇 해 뒤에 받는가', yLabel: '오늘 값어치(첫 지급 = 1)',
        fmt: t => (t === 0.5 ? '0.5' : String(t)),
    }));
    const bw = (f.X(1) - f.X(0)) * 0.62;
    for (let i = 1; i <= n; i += 1) {
        const v = 1 / (1 + p) ** (i - 1);
        const bx = f.X(i) - bw / 2;
        g.push(box(bx, f.Y(v), bw, f.Y(0) - f.Y(v), { fill: i <= 3 ? C1 : CK, op: i <= 3 ? 0.5 : 0.3, stroke: 'none', rx: 1 }));
    }
    g.push(fcurve(f, v => (1 + p) ** (1 - v), { from: 1, to: 20, stroke: C2, sw: 2 }));
    g.push(txt(f.X(11.5), f.Y(0.56), 'x^(i−1),  x = 1/(1 + p)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(20.8), f.Y(0.46), '스무째 해 지급은 0.23', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(arw(f.X(19.6), f.Y(0.42), f.X(20), f.Y(0.26), { cls: 'ark', width: 1.2 }));

    const px0 = 512, pw = 250;
    g.push(panel(px0, 58, pw, 202, '이자율 p = 0.08 일 때', '해마다 m 을 n 해 받는 연금의 오늘 값 V'));
    const rows = [
        ['n = 20', 'V ≈ 10.6 m'],
        ['n = 40', 'V ≈ 12.9 m'],
        ['n = ∞', 'V = 13.5 m'],
    ];
    rows.forEach((r, i) => {
        const yy = 126 + i * 32;
        g.push(box(px0 + 18, yy - 19, 66, 27, { stroke: C3, sw: 1.4, fill: C3, op: 0.16 }));
        g.push(txt(px0 + 51, yy, r[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px0 + 96, yy, r[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[px0 + 18, 218], [px0 + pw - 18, 218]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 18, 240, '영원히 받아도 스무 해의 1.3배뿐이다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(24, 292, '막대의 높이가 등비수열이므로 합에 닫힌 형태가 있다. 그래서 ‘영원히’ 받는 경우에도 값이 유한하다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-annuity-decay',
        svg: svg({
            width: W, height: H,
            title: '연금 지급액의 현재가치는 등비수열로 줄어든다',
            desc: '왼쪽은 해마다 받는 돈의 현재가치를 막대로 그린 것, 오른쪽은 지급 기간에 따른 연금의 총 현재가치',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-2. 합을 적분으로 근사한다 — 이 장의 중심 그림 ---- */
add((() => {
    const W = 880, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '표본점의 어느 쪽에 칸을 세우느냐가 부등호의 방향을 정한다', { anchor: 'middle', cls: 'ink bold' }));

    const n = 6;
    const fn = v => Math.sqrt(v);
    const xR = [0, 7.4], yR = [0, 2.9];

    // 왼쪽 패널 — 표본점의 오른쪽에 칸: 곡선 아래
    const A = frame({ xRange: xR, yRange: yR, box: { x: 58, y: 82, w: 348, h: 194 } });
    g.push(panel(22, 52, 412, 258, 'ⓐ 칸을 표본점의 오른쪽에 세우면 곡선 아래로 들어간다'));
    for (let i = 1; i <= n - 1; i += 1) {
        g.push(box(A.X(i), A.Y(fn(i)), A.X(i + 1) - A.X(i), A.Y(0) - A.Y(fn(i)),
            { fill: C1, op: 0.24, stroke: C1, sw: 1.1, rx: 0 }));
    }
    g.push(fcurve(A, fn, { from: 0.02, to: 7.2, stroke: C2, sw: 2.4 }));
    g.push(axes2(A, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4, 5, 6], yTicks: [], xLabel: 'x' }));
    g.push(txt(A.X(6.6), A.Y(fn(6.6)) - 12, 'f(x)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(A.X(3.5), A.Y(2.62), '칸의 넓이 합 = f(1) + ⋯ + f(n−1) = S − f(n)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(A.X(3.5), A.Y(2.28), '칸이 모두 곡선 아래이므로  S − f(n) ≤ I', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽 패널 — 표본점의 왼쪽에 칸: 곡선 위
    const B = frame({ xRange: xR, yRange: yR, box: { x: 504, y: 82, w: 348, h: 194 } });
    g.push(panel(446, 52, 412, 258, 'ⓑ 칸을 표본점의 왼쪽에 세우면 곡선 위로 올라간다'));
    for (let i = 2; i <= n; i += 1) {
        g.push(box(B.X(i - 1), B.Y(fn(i)), B.X(i) - B.X(i - 1), B.Y(0) - B.Y(fn(i)),
            { fill: C3, op: 0.24, stroke: C3, sw: 1.1, rx: 0 }));
    }
    g.push(fcurve(B, fn, { from: 0.02, to: 7.2, stroke: C2, sw: 2.4 }));
    g.push(axes2(B, { xRange: xR, yRange: yR, xTicks: [1, 2, 3, 4, 5, 6], yTicks: [], xLabel: 'x' }));
    g.push(txt(B.X(6.6), B.Y(fn(6.6)) - 12, 'f(x)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(B.X(3.5), B.Y(2.62), '칸의 넓이 합 = f(2) + ⋯ + f(n) = S − f(1)', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(B.X(3.5), B.Y(2.28), '칸이 모두 곡선을 덮으므로  S − f(1) ≥ I', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[22, 326], [W - 22, 326]], { stroke: CG, sw: 1 }));
    g.push(txt(W / 2, 348, '두 그림을 붙이면  I + f(1) ≤ S ≤ I + f(n).  I 는 1 부터 n 까지의 적분이고, 두 한계의 차이는 f(n) − f(1) 뿐이다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    return {
        name: 'mcs-c-integral-bound',
        svg: svg({
            width: W, height: H,
            title: '합을 적분으로 위아래에서 감싼다',
            desc: '왼쪽은 칸을 표본점 오른쪽에 세워 곡선 아래에 넣은 그림, 오른쪽은 칸을 왼쪽에 세워 곡선을 덮은 그림. 두 부등식을 합치면 적분에 첫 항이나 마지막 항을 더한 값이 합의 상하 한계가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-3. 조화수는 로그에 오일러 상수만큼 붙는다 ---- */
add((() => {
    const W = 780, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '조화수와 로그의 차이는 상수 하나로 굳는다', { anchor: 'middle', cls: 'ink bold' }));

    const N = 24;
    const H_ = [0];
    for (let i = 1; i <= N; i += 1) H_.push(H_[i - 1] + 1 / i);
    const gamma = 0.5772156649;

    const xR = [0, N + 1.4], yR = [0, 4.3];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 62, y: 58, w: 400, h: 202 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [1, 5, 10, 15, 20], yTicks: [1, 2, 3, 4],
        xLabel: 'n', yLabel: '값',
    }));
    // 조화수의 계단
    const step = [];
    for (let i = 1; i <= N; i += 1) {
        step.push([f.X(i), f.Y(H_[i])]);
        if (i < N) step.push([f.X(i + 1), f.Y(H_[i])]);
    }
    g.push(ln(step, { stroke: C1, sw: 2.2 }));
    g.push(fcurve(f, v => Math.log(v), { from: 1, to: N + 0.6, stroke: C2, sw: 2 }));
    g.push(fcurve(f, v => Math.log(v) + gamma, { from: 1, to: N + 0.6, stroke: C3, sw: 2, dash: '5 4' }));
    g.push(legend(196, 84, [
        { slot: 1, name: 'H~n — 조화수의 계단' },
        { slot: 3, name: 'ln n + γ' },
        { slot: 2, name: 'ln n' },
    ]));
    // 간격 표시
    const gx = f.X(21.6);
    g.push(ln([[gx, f.Y(Math.log(21.6))], [gx, f.Y(H_[22])]], { stroke: CI, sw: 1.4 }));
    g.push(ln([[gx - 5, f.Y(Math.log(21.6))], [gx + 5, f.Y(Math.log(21.6))]], { stroke: CI, sw: 1.4 }));
    g.push(ln([[gx - 5, f.Y(H_[22])], [gx + 5, f.Y(H_[22])]], { stroke: CI, sw: 1.4 }));
    g.push(txt(gx - 10, f.Y(1.05), '이 간격', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const px0 = 492, pw = 268;
    g.push(panel(px0, 58, pw, 202, 'H~n − ln n 은 γ 로 다가간다', 'γ = 0.577215…  (오일러 상수)'));
    const rows = [2, 4, 10, 24].map(k => [`n = ${k}`, (H_[k] - Math.log(k)).toFixed(4)]);
    rows.forEach((r, i) => {
        const yy = 128 + i * 32;
        g.push(box(px0 + 20, yy - 18, 62, 26, { stroke: C1, sw: 1.3, fill: C1, op: 0.14 }));
        g.push(txt(px0 + 51, yy, r[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px0 + 96, yy, r[1], { cls: 'ink2', size: 'sm' }));
    });

    g.push(txt(24, 296, '적분 한계는 ln n ≤ H~n ≤ ln n + 1 까지만 말해 준다. 그림은 그 안에서 차이가 한 값으로 굳는 것을 보인다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 316, '계단이 로그 곡선과 나란히 올라가는 것 — 이것이 H~n ∼ ln n 의 뜻이고, 차이가 상수라 ∼ 로는 γ 가 보이지 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-harmonic-ln',
        svg: svg({
            width: W, height: H,
            title: '조화수와 자연로그의 차이는 오일러 상수로 수렴한다',
            desc: '조화수의 계단 그래프가 자연로그 곡선과 그 곡선을 오일러 상수만큼 올린 곡선 사이를 따라 올라간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-4. 책 쌓기 — 조화수가 실제로 나오는 자리 ---- */
add((() => {
    const W = 780, H = 360;
    const g = [];
    g.push(txt(W / 2, 26, '위에서부터 1/2, 1/4, 1/6, 1/8 씩 밀면 넘어지지 않는다', { anchor: 'middle', cls: 'ink bold' }));

    const bw = 232, bh = 32, y0 = 76;
    const edge = 392;           // 책상 모서리의 화소 x
    const unit = bw;            // 책 한 권 길이 = bw
    // over[i] = i 번째 책(0 이 맨 위)이 바로 아래 책보다 더 나간 거리. 맨 아래 책은 책상 모서리 기준.
    const over = [1 / 2, 1 / 4, 1 / 6, 1 / 8];
    const frac = ['1/2', '1/4', '1/6', '1/8'];
    const rx = [];
    rx[3] = edge + over[3] * unit;
    for (let i = 2; i >= 0; i -= 1) rx[i] = rx[i + 1] + over[i] * unit;
    for (let i = 0; i < 4; i += 1) {
        const y = y0 + i * bh;
        g.push(box(rx[i] - bw, y, bw, bh - 4, { fill: i === 0 ? C1 : CK, op: i === 0 ? 0.2 : 0.12, stroke: i === 0 ? C1 : CK, sw: 1.4, rx: 2 }));
        g.push(txt(rx[i] - bw + 10, y + 20, i === 0 ? '맨 위 책' : `${i + 1}번째 책`, { cls: 'ink2', size: 'sm' }));
        // 아래 책(또는 책상)보다 더 나간 몫을 책 안쪽 오른쪽에 표시한다.
        const left = i === 3 ? edge : rx[i + 1];
        g.push(box(left, y + 6, rx[i] - left, bh - 16, { fill: C2, op: 0.3, stroke: C2, sw: 1.1, rx: 1 }));
        g.push(txt(rx[i] + 8, y + 20, frac[i], { cls: 'ink bold', size: 'sm' }));
    }
    // 책상
    const ty = y0 + 4 * bh;
    g.push(box(edge - 340, ty, 340, 22, { fill: CK, op: 0.22, stroke: CK, sw: 1.4, rx: 2 }));
    g.push(txt(edge - 170, ty + 16, '책상', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[edge, 62], [edge, ty + 34]], { stroke: CI, sw: 1.4, dash: '5 4' }));
    g.push(txt(edge - 6, ty + 40, '책상 모서리', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    // 총 내민 길이
    g.push(arw(edge, 56, rx[0], 56, { cls: 's2', width: 1.8 }));
    g.push(arw(rx[0], 56, edge, 56, { cls: 's2', width: 1.8 }));
    g.push(txt((edge + rx[0]) / 2, 48, '내민 길이 = H~4 / 2 = 25/24 > 1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 오른쪽 메모
    const px0 = 24, pw = 320;
    g.push(panel(px0, 256, pw, 86, '내민 길이는 H~n / 2 로 자란다'));
    g.push(txt(px0 + 16, 300, 'B~n = B~{n−1} + 1/(2n)  이므로  B~n = H~n / 2', { cls: 'ink', size: 'sm' }));
    g.push(txt(px0 + 16, 322, 'H~n ∼ ln n 이므로 책을 늘리면 얼마든지 멀리 나간다', { cls: 'ink2', size: 'sm' }));

    const qx = 372, qw = 384;
    g.push(panel(qx, 256, qw, 86, '그러나 매우 느리다'));
    g.push(txt(qx + 16, 300, '책 한 권 길이만큼 내밀려면 4권, 세 권 길이면 227권', { cls: 'ink', size: 'sm' }));
    g.push(txt(qx + 16, 322, '내민 길이가 로그로 자라니 책 수는 지수로 늘어난다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-c-book-stack',
        svg: svg({
            width: W, height: H,
            title: '책 네 권으로 책 한 권 길이보다 멀리 내밀 수 있다',
            desc: '책 네 권을 위에서부터 절반, 사분의 일, 육분의 일, 팔분의 일씩 밀어 쌓으면 맨 위 책이 책상 모서리에서 25/24 책 길이만큼 나간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-5. 스털링 근사의 오차 ---- */
add((() => {
    const W = 780, H = 316;
    const g = [];
    g.push(txt(W / 2, 26, '스털링 근사는 n 이 작아도 이미 잘 맞는다', { anchor: 'middle', cls: 'ink bold' }));

    // n! / (√(2πn)(n/e)^n) − 1 을 백분율로.
    const err = [];
    let fact = 1;
    for (let n = 1; n <= 10; n += 1) {
        fact *= n;
        const s = Math.sqrt(2 * Math.PI * n) * (n / Math.E) ** n;
        err.push(100 * (fact / s - 1));
    }
    const xR = [0.4, 10.8], yR = [0, 9.4];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 66, y: 62, w: 380, h: 178 } });
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [1, 2, 4, 6, 8, 10], yTicks: [0, 2, 4, 6, 8],
        xLabel: 'n', yLabel: '오차(%)',
    }));
    const bw = 20;
    err.forEach((e, i) => {
        const n = i + 1;
        g.push(box(f.X(n) - bw / 2, f.Y(e), bw, f.Y(0) - f.Y(e), { fill: C1, op: 0.55, stroke: 'none', rx: 1 }));
        if (n === 1) g.push(txt(f.X(n) + 14, f.Y(e) - 4, `${e.toFixed(2)}%`, { cls: 'ink bold', size: 'sm' }));
        if (n === 10) g.push(txt(f.X(n), f.Y(e) - 10, `${e.toFixed(2)}%`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(ln([[f.X(0.4), f.Y(1)], [f.X(10.8), f.Y(1)]], { stroke: C2, sw: 1.4, dash: '5 4' }));
    g.push(txt(f.X(10.8) + 14, f.Y(1) + 4, '1%', { cls: 'ink2', size: 'sm' }));

    const px0 = 486, pw = 272;
    g.push(panel(px0, 62, pw, 178, '오차의 정체', 'n! = √(2πn) (n/e)ⁿ · e^ε(n)'));
    g.push(txt(px0 + 18, 116, '1/(12n + 1) ≤ ε(n) ≤ 1/(12n)', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(px0 + 18, 142, 'ε(n) 이 늘 양수이므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 162, 'n! 은 언제나 √(2πn)(n/e)ⁿ 보다 크다', { cls: 'ink', size: 'sm' }));
    g.push(ln([[px0 + 18, 178], [px0 + pw - 18, 178]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 18, 200, 'ε(n) → 0 이므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 220, 'n! ∼ √(2πn)(n/e)ⁿ', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(24, 278, '적분 한계만으로는 n 배까지 벌어지는 상하한밖에 얻지 못했다. 스털링 공식은 그 틈을 백분율 단위로 좁힌다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 300, '놀라운 것은 정수만 곱해 만든 n! 의 근사식에 π 와 e 가 함께 나온다는 점이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-stirling-error',
        svg: svg({
            width: W, height: H,
            title: '스털링 근사의 상대 오차',
            desc: 'n 이 1 일 때 약 8퍼센트, 10 일 때 1퍼센트 아래로 줄어드는 상대 오차 막대와 오차항의 정확한 범위',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-6. 큰 O 의 c 와 x0 ---- */
add((() => {
    const W = 780, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '큰 O 는 상수배와 작은 x 를 눈감아 주는 약속이다', { anchor: 'middle', cls: 'ink bold' }));

    const fx = v => 4 * v + 30;
    const xR = [0, 62], yR = [0, 300];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 62, y: 62, w: 396, h: 196 } });
    // x ≥ 30 구간 색칠
    g.push(box(f.X(30), f.Y(300), f.X(62) - f.X(30), f.Y(0) - f.Y(300), { fill: C3, op: 0.12, stroke: 'none', rx: 0 }));
    g.push(axes2(f, {
        xRange: xR, yRange: yR, xTicks: [10, 20, 30, 40, 50, 60], yTicks: [100, 200, 300],
        xLabel: 'x', yLabel: '값',
    }));
    g.push(fcurve(f, fx, { from: 0, to: 60, stroke: C1, sw: 2.4 }));
    g.push(fcurve(f, v => 5 * v, { from: 0, to: 60, stroke: C2, sw: 2.4 }));
    g.push(fcurve(f, v => v, { from: 0, to: 60, stroke: CK, sw: 1.4, dash: '4 4' }));
    g.push(txt(f.X(48), f.Y(136), 'f(x) = 4x + 30', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(38), f.Y(252), 'c·g(x) = 5x', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(58), f.Y(58) + 16, 'g(x) = x', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(pdot(f.X(30), f.Y(150), CI, 4.5));
    g.push(ln([[f.X(30), f.Y(0)], [f.X(30), f.Y(150)]], { stroke: CI, sw: 1.2, dash: '4 3' }));
    g.push(txt(f.X(29), f.Y(104), 'x~0 = 30', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(31), f.Y(285), 'x ≥ x~0 에서 f ≤ c·g', { cls: 'ink', size: 'sm' }));
    g.push(txt(f.X(1), f.Y(285), '왼쪽에서는 어겨도 좋다', { cls: 'ink2', size: 'sm' }));

    const px0 = 486, pw = 272;
    g.push(panel(px0, 62, pw, 196, 'c 와 x~0 은 둘이 맞바꾸는 값이다'));
    const rows = [['c = 5', 'x~0 = 30'], ['c = 10', 'x~0 = 5'], ['c = 34', 'x~0 = 1']];
    rows.forEach((r, i) => {
        const yy = 112 + i * 34;
        g.push(box(px0 + 20, yy - 19, 62, 27, { stroke: C2, sw: 1.3, fill: C2, op: 0.14 }));
        g.push(txt(px0 + 51, yy, r[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(arw(px0 + 90, yy - 5, px0 + 122, yy - 5, { cls: 'ark', width: 1.4 }));
        g.push(txt(px0 + 130, yy, r[1], { cls: 'ink', size: 'sm' }));
    });
    g.push(ln([[px0 + 20, 222], [px0 + pw - 20, 222]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 20, 244, '하나만 있으면 되므로 아무 쌍이나 대면 된다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(24, 292, '정의가 요구하는 것은 c 와 x~0 이 존재한다는 것뿐이다. 가장 작은 값을 찾을 필요가 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 312, 'g 를 c 배로 늘려도 되므로 4x + 30 = O(x) 이고, 거꾸로 x = O(4x + 30) 이기도 하다. 둘은 Θ 로 묶인다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-bigo-window',
        svg: svg({
            width: W, height: H,
            title: '큰 O 정의의 상수 c 와 문턱 x0',
            desc: '4x 더하기 30 이 5x 아래로 내려가는 지점이 30 이고, 상수 c 를 키우면 그 문턱이 왼쪽으로 옮겨간다',
            body: g.join(''),
        }),
    };
})());

/* ---- 16-7. 이중 합 — 행으로 더하나 열로 더하나 ---- */
add((() => {
    const W = 780, H = 358;
    const g = [];
    g.push(txt(W / 2, 26, '삼각형을 행으로 더하는 것과 열로 더하는 것은 같은 값이다', { anchor: 'middle', cls: 'ink bold' }));

    const n = 5, cw = 62, ch = 34, x0 = 108, y0 = 84;
    // 머리줄
    for (let j = 1; j <= n; j += 1) {
        g.push(txt(x0 + (j - 1) * cw + cw / 2, y0 - 10, `j = ${j}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (let k = 1; k <= n; k += 1) {
        g.push(txt(x0 - 12, y0 + (k - 1) * ch + 22, `k = ${k}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        for (let j = 1; j <= k; j += 1) {
            const cx = x0 + (j - 1) * cw, cy = y0 + (k - 1) * ch;
            const hl = j === 2;
            g.push(box(cx, cy, cw, ch, { fill: hl ? C2 : C1, op: hl ? 0.2 : 0.1, stroke: hl ? C2 : CK, sw: hl ? 1.7 : 1, rx: 2 }));
            g.push(txt(cx + cw / 2, cy + 22, j === 1 ? '1' : `1/${j}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        g.push(txt(x0 + n * cw + 14, y0 + (k - 1) * ch + 22, `= H~${k}`, { cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(x0 + n * cw + 14, y0 - 10, '행의 합', { cls: 'ink2', size: 'sm' }));

    // 열 합
    const yb = y0 + n * ch;
    for (let j = 1; j <= n; j += 1) {
        g.push(txt(x0 + (j - 1) * cw + cw / 2, yb + 20, `${n - j + 1}·(1/${j})`, { anchor: 'middle', cls: j === 2 ? 'ink bold' : 'ink2', size: 'sm' }));
    }
    g.push(txt(x0 - 12, yb + 20, '열의 합', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(arw(x0 + n * cw + 92, y0 + 8, x0 + n * cw + 92, yb - 8, { cls: 's1', width: 1.6 }));
    g.push(txt(x0 + n * cw + 100, y0 + 40, '행을 먼저', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + n * cw + 100, y0 + 58, '더하면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + n * cw + 100, y0 + 76, 'Σ H~k', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, yb + 44], [W - 24, yb + 44]], { stroke: CG, sw: 1 }));
    g.push(txt(24, yb + 68, '열의 합은 계산하기 쉽다. j 번째 열에는 k = j 부터 k = n 까지 n − j + 1 개의 1/j 이 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, yb + 90, '그래서 Σ H~k = Σ (n+1)/j − Σ 1 = (n+1) H~n − n 이 나온다. 안쪽 합에 닫힌 형태가 없어도 순서만 바꾸면 된다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'mcs-c-double-sum',
        svg: svg({
            width: W, height: H,
            title: '이중 합의 순서를 바꾼다',
            desc: '1 부터 1/j 까지 늘어놓은 삼각형 표에서 행의 합은 조화수이고 열의 합은 개수 곱하기 항이다. 두 방향의 총합이 같으므로 조화수의 합에 닫힌 형태가 나온다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 17장 — 셈의 규칙
 * ================================================================== */

/* ---- 17-1. 도넛 고르기와 비트열 사이의 전단사(막대와 별) ---- */
add((() => {
    const W = 840, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '맛의 경계에 막대를 세우면 도넛 고르기가 비트열이 된다', { anchor: 'middle', cls: 'ink bold' }));

    const counts = [2, 0, 6, 2, 2];
    const names = ['초콜릿', '레몬', '설탕', '글레이즈드', '플레인'];
    const dr = 9, dgap = 24, sep = 32, x0 = 60;

    // 1단: 맛마다 사이를 띄워 늘어놓는다
    let x = x0;
    const yA = 82;
    g.push(txt(24, yA - 30, '① 도넛 열두 개를 맛별로 늘어놓는다 — 사이가 넉 군데 벌어진다', { cls: 'ink', size: 'sm' }));
    const boundary = [];
    counts.forEach((c, k) => {
        const start = x;
        for (let i = 0; i < c; i += 1) { g.push(ring(x + dr, yA, dr, C1, 2)); x += dgap; }
        const end = c === 0 ? start + 16 : x - dgap + 2 * dr;
        g.push(txt((start + end) / 2, yA + 32, names[k], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (k < 4) { boundary.push(x + sep / 2 - 4); x += sep; }
    });
    for (const bx of boundary) g.push(ln([[bx, yA - 20], [bx, yA + 16]], { stroke: CG, sw: 1, dash: '3 3' }));

    // 2단: 벌어진 자리에 막대를 세운다
    const yB = 164;
    g.push(txt(24, yB - 30, '② 벌어진 넉 군데에 막대를 세운다', { cls: 'ink', size: 'sm' }));
    x = x0;
    counts.forEach((c, k) => {
        for (let i = 0; i < c; i += 1) { g.push(ring(x + dr, yB, dr, C1, 2)); x += dgap; }
        if (k < 4) {
            g.push(box(x + sep / 2 - 10, yB - 13, 12, 26, { fill: C2, op: 0.45, stroke: C2, sw: 1.4, rx: 2 }));
            x += sep;
        }
    });

    // 3단: 도넛을 0, 막대를 1 로 적는다
    const yC = 234;
    g.push(txt(24, yC - 12, '③ 도넛을 0, 막대를 1 로 적으면 길이 16 인 비트열이고 1 이 정확히 넉 개다', { cls: 'ink', size: 'sm' }));
    const bits = [];
    const hl = {};
    counts.forEach((c, k) => {
        for (let i = 0; i < c; i += 1) bits.push('0');
        if (k < 4) { hl[bits.length] = C2; bits.push('1'); }
    });
    g.push(cells(x0, yC + 4, 28, 28, bits, { hl, small: true }));

    g.push(ln([[24, 292], [W - 24, 292]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 314, '거꾸로도 된다. 1 이 넉 개인 길이 16 비트열이 주어지면 0 의 덩어리 길이가 맛별 개수다 — 짝짓기가 양방향이므로 전단사다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 332, '그래서 도넛 n 개를 k 가지 맛에서 고르는 방법의 수는 0 이 n 개, 1 이 k−1 개인 비트열의 수와 같다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'mcs-c-donut-bits',
        svg: svg({
            width: W, height: H,
            title: '도넛 고르기와 비트열 사이의 전단사',
            desc: '맛별로 늘어놓은 도넛 열두 개의 경계 넉 군데에 막대를 세우고, 도넛을 0 막대를 1 로 적으면 1 이 넉 개인 길이 16 비트열이 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 17-2. 나눗셈 규칙 — 여러 번 세고 나눈다 ---- */
add((() => {
    const W = 800, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, '같은 것을 k 번씩 세었다면 k 로 나눈다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(panel(22, 50, 448, 258, 'ⓐ 룩 두 개 놓기 — 수열은 배치를 두 번씩 센다'));
    const seqs = [
        ['(1, a, 8, h)', '(8, h, 1, a)'],
        ['(3, c, 5, f)', '(5, f, 3, c)'],
        ['(2, b, 7, g)', '(7, g, 2, b)'],
    ];
    const lx = 44, rx = 320;
    g.push(txt(lx, 90, '수열 (r~1, c~1, r~2, c~2)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx, 90, '룩 두 개의 배치', { cls: 'ink2', size: 'sm' }));
    seqs.forEach((pair, i) => {
        const yy = 116 + i * 62;
        g.push(box(lx, yy - 18, 118, 24, { stroke: C1, sw: 1.3, fill: C1, op: 0.12 }));
        g.push(txt(lx + 59, yy - 1, pair[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(box(lx, yy + 14, 118, 24, { stroke: C1, sw: 1.3, fill: C1, op: 0.12 }));
        g.push(txt(lx + 59, yy + 31, pair[1], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(box(rx, yy - 2, 106, 28, { stroke: C3, sw: 1.6, fill: C3, op: 0.16 }));
        g.push(txt(rx + 53, yy + 17, '배치 하나', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(arw(lx + 124, yy - 6, rx - 6, yy + 6, { cls: 's2', width: 1.5 }));
        g.push(arw(lx + 124, yy + 26, rx - 6, yy + 15, { cls: 's2', width: 1.5 }));
    });
    g.push(txt(lx, 296, '수열이 (8 · 7)² 개이고 사상이 2 대 1 이므로 배치는 그 절반이다', { cls: 'ink bold', size: 'sm' }));

    const px0 = 492, pw = 286;
    g.push(panel(px0, 50, pw, 258, 'ⓑ k 는 상황마다 다르다', '|A| = k |B| 이므로 |B| = |A| / k'));
    const rows = [
        ['귀 → 사람', 'k = 2'],
        ['손가락 → 사람', 'k = 10'],
        ['자리 배치 → 원탁 배열', 'k = n'],
        ['순열 → 크기 k 부분집합', 'k!(n−k)!'],
        ['수열 → 두 쌍 포커 손', 'k = 2'],
    ];
    rows.forEach((r, i) => {
        const yy = 110 + i * 36;
        g.push(txt(px0 + 20, yy, r[0], { cls: 'ink', size: 'sm' }));
        g.push(box(px0 + pw - 96, yy - 17, 78, 24, { stroke: C2, sw: 1.3, fill: C2, op: 0.14 }));
        g.push(txt(px0 + pw - 57, yy, r[1], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(txt(px0 + 20, 296, 'k 를 잘못 세는 것이 가장 흔한 실수다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-division-rule',
        svg: svg({
            width: W, height: H,
            title: '나눗셈 규칙',
            desc: '룩 두 개의 배치 하나에 수열이 둘씩 대응하므로 수열의 수를 둘로 나누면 배치의 수가 된다. 오른쪽은 여러 상황에서의 k 값 목록',
            body: g.join(''),
        }),
    };
})());

/* ---- 17-3. 원탁 — n 개의 자리 배치가 배열 하나로 ---- */
add((() => {
    const W = 800, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '돌려서 같아지는 자리 배치 넷이 배열 하나다', { anchor: 'middle', cls: 'ink bold' }));

    const R = 38;
    const seat = (cx, cy, order, { mark = false } = {}) => {
        const out = [ring(cx, cy, R, CG, 1.4)];
        const pos = [[0, -R], [R, 0], [0, R], [-R, 0]];   // 위에서 시계 방향
        order.forEach((k, i) => {
            const [dx, dy] = pos[i];
            const on = mark && i === 0;
            out.push(box(cx + dx - 15, cy + dy - 11, 30, 22, { fill: on ? C2 : C1, op: on ? 0.3 : 0.14, stroke: on ? C2 : C1, sw: 1.4, rx: 3 }));
            out.push(txt(cx + dx, cy + dy + 5, `k~${k}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        });
        return out.join('');
    };

    const seq = [[2, 4, 1, 3], [4, 1, 3, 2], [1, 3, 2, 4], [3, 2, 4, 1]];
    const cxs = [96, 238, 380, 522];
    g.push(txt(24, 62, '① 자리 배치 = 맨 위 자리에서 시계 방향으로 읽은 순열. 넷은 서로 다른 순열이다', { cls: 'ink', size: 'sm' }));
    seq.forEach((o, i) => {
        g.push(seat(cxs[i], 138, o));
        g.push(txt(cxs[i], 200, `(k~${o[0]}, k~${o[1]}, k~${o[2]}, k~${o[3]})`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    const gx = 706;
    g.push(seat(gx, 138, [1, 3, 2, 4], { mark: true }));
    g.push(txt(gx, 200, '배열 하나', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ln([[592, 96], [592, 180]], { stroke: C2, sw: 1.6 }));
    g.push(arw(592, 138, 638, 138, { cls: 's2', width: 1.8 }));
    g.push(txt(619, 126, '돌리면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(619, 168, '같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[24, 226], [W - 24, 226]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 250, '② 옆에 누가 앉았는지만 따지면 넷이 모두 같다. 순환 이동 n 가지가 한 배열로 가므로 사상이 n 대 1 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 274, '③ 그래서 원탁 배열은 n! / n = (n−1)! 개다. n = 4 이면 4! / 4 = 6 개의 배열이 있다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'mcs-c-round-table',
        svg: svg({
            width: W, height: H,
            title: '원탁 배열은 순열을 n 으로 나눈 것',
            desc: '기사 넷의 자리 배치 넷이 돌리면 서로 같아져 하나의 배열이 된다. 순환 이동이 n 가지이므로 사상이 n 대 1 이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 17-4. 두 쌍 포커 손 — 2 대 1 을 놓치면 두 배로 센다 ---- */
add((() => {
    const W = 820, H = 316;
    const g = [];
    g.push(txt(W / 2, 26, '첫 쌍과 둘째 쌍을 구별할 근거가 없으므로 수열이 손을 두 번 센다', { anchor: 'middle', cls: 'ink bold' }));

    const card = (x, y, label, col = C1) => box(x, y, 44, 34, { fill: col, op: 0.16, stroke: col, sw: 1.4, rx: 4 })
        + txt(x + 22, y + 22, label, { anchor: 'middle', cls: 'ink', size: 'sm' });

    const lx = 34;
    g.push(txt(lx, 66, '수열 두 개', { cls: 'ink2', size: 'sm' }));
    g.push(box(lx, 76, 322, 42, { stroke: C1, sw: 1.3, fill: C1, op: 0.08 }));
    g.push(txt(lx + 14, 102, '( 3, {◆,♠}, Q, {◆,♥}, A, ♣ )', { cls: 'ink', size: 'sm' }));
    g.push(box(lx, 132, 322, 42, { stroke: C1, sw: 1.3, fill: C1, op: 0.08 }));
    g.push(txt(lx + 14, 158, '( Q, {◆,♥}, 3, {◆,♠}, A, ♣ )', { cls: 'ink', size: 'sm' }));
    g.push(txt(lx + 2, 196, '첫 쌍의 끗수 · 무늬 · 둘째 쌍의 끗수 · 무늬 · 남는 카드의 끗수 · 무늬', { cls: 'ink2', size: 'sm' }));

    const rx2 = 486;
    g.push(txt(rx2, 66, '손 하나', { cls: 'ink2', size: 'sm' }));
    const labels = ['3◆', '3♠', 'Q◆', 'Q♥', 'A♣'];
    labels.forEach((L, i) => g.push(card(rx2 + i * 52, 100, L, i < 4 ? C3 : CK)));
    g.push(box(rx2 - 10, 84, 274, 66, { stroke: C3, sw: 1.6, rx: 6 }));
    g.push(arw(lx + 326, 92, rx2 - 18, 110, { cls: 's2', width: 1.6 }));
    g.push(arw(lx + 326, 152, rx2 - 18, 126, { cls: 's2', width: 1.6 }));
    g.push(txt(rx2 - 8, 176, '손은 집합이므로 순서가 없다 — 어느 쪽이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(rx2 - 8, 196, '‘첫’ 쌍인지 가릴 방법이 없다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[24, 218], [W - 24, 218]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 242, '틀린 답 — 사상을 전단사라 여긴 것', { cls: 'ink2', size: 'sm' }));
    g.push(txt(392, 242, '13 · C(4,2) · 12 · C(4,2) · 11 · 4', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 268, '옳은 답 — 2 대 1 이므로 둘로 나눈다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(392, 268, '13 · C(4,2) · 12 · C(4,2) · 11 · 4 / 2', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 294, '다른 길로 검산 — 두 끗수를 한꺼번에 고른다', { cls: 'ink', size: 'sm' }));
    g.push(txt(392, 294, 'C(13,2) · C(4,2) · C(4,2) · 11 · 4      두 값이 같다', { cls: 'ink', size: 'sm' }));
    return {
        name: 'mcs-c-poker-twopair',
        svg: svg({
            width: W, height: H,
            title: '두 쌍 손을 세는 사상은 2 대 1 이다',
            desc: '첫 쌍과 둘째 쌍의 자리를 맞바꾼 두 수열이 같은 손으로 가므로 수열의 수를 둘로 나누어야 한다',
            body: g.join(''),
        }),
    };
})());

/* ---- 17-5. 포함배제 — 세 집합 ---- */
add((() => {
    const W = 800, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '두 번 센 것을 빼면 세 번 센 것까지 지워지므로 다시 더한다', { anchor: 'middle', cls: 'ink bold' }));

    const cx = 214, cy = 170, R = 78, d = 46;
    const P = [[cx, cy - d], [cx - d * 0.92, cy + d * 0.6], [cx + d * 0.92, cy + d * 0.6]];
    const cols = [C1, C2, C3];
    P.forEach((p, i) => g.push(disc(p[0], p[1], R, cols[i], 0.2, cols[i])));
    g.push(txt(cx, 78, '수학 60', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx - 58, 240, '전산 200', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx + 58, 240, '물리 40', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx - 30, 160, '6', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx + 30, 160, '5', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx, 216, '13', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(cx, 184, '2', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const px0 = 400, pw = 376;
    g.push(panel(px0, 58, pw, 222, '한 사람이 몇 번 세어지는가', '더하기와 빼기를 번갈아 하면 딱 한 번이다'));
    const rows = [
        ['전공이 하나뿐', '+1', '= 1'],
        ['이중 전공', '+1 +1 −1', '= 1'],
        ['삼중 전공', '+3 −3 +1', '= 1'],
    ];
    rows.forEach((r, i) => {
        const yy = 108 + i * 32;
        g.push(txt(px0 + 20, yy, r[0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(px0 + 158, yy, r[1], { cls: 'ink', size: 'sm' }));
        g.push(txt(px0 + pw - 24, yy, r[2], { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    });
    g.push(ln([[px0 + 20, 216], [px0 + pw - 20, 216]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 20, 240, 'k 중 전공이면 C(k,1) − C(k,2) + ⋯ 이고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 20, 262, '이 값은 1 − (1 − 1)^k = 1 이다 — 이 세 줄이 증명이다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, 296], [W - 24, 296]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 322, '합집합 = 60 + 200 + 40 − 6 − 5 − 13 + 2 = 278.  요령은 두 방향으로 세는 것이 아니라 한 사람이 세어지는 횟수를 맞추는 것이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 342, '겹치지 않으면 합의 규칙으로 끝나지만, 겹치면 겹친 만큼을 빼고 그 과정에서 지나치게 빠진 만큼을 되돌려야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-inclusion-exclusion',
        svg: svg({
            width: W, height: H,
            title: '세 집합의 포함배제',
            desc: '세 전공 집합이 겹친 벤 다이어그램과, 한 사람이 몇 번 세어지는지를 전공 수별로 확인한 표',
            body: g.join(''),
        }),
    };
})());

/* ---- 17-6. 조합적 증명 — 같은 것을 두 방식으로 센다 ---- */
add((() => {
    const W = 800, H = 296;
    const g = [];
    g.push(txt(W / 2, 26, '한 집합을 두 방식으로 세면 항등식이 공짜로 나온다', { anchor: 'middle', cls: 'ink bold' }));

    const n = 7, cw = 34, x0 = 96, yy = 74;
    const items = [], hl = {};
    for (let i = 0; i < n; i += 1) { items.push(i === 0 ? 'Bob' : ''); }
    hl[0] = C2;
    g.push(cells(x0, yy, cw, 30, items, { hl, small: true }));
    g.push(txt(x0 + n * cw + 16, yy + 20, 'n 명에서 k 명을 뽑는 방법의 집합 S', { cls: 'ink', size: 'sm' }));

    g.push(panel(40, 130, 348, 100, '세는 법 ①  Bob 을 기준으로 가른다'));
    g.push(txt(58, 172, 'Bob 이 뽑힌다 → 남은 n−1 에서 k−1 명', { cls: 'ink2', size: 'sm' }));
    g.push(txt(58, 194, 'Bob 이 빠진다 → 남은 n−1 에서 k 명', { cls: 'ink2', size: 'sm' }));
    g.push(txt(58, 220, 'C(n−1, k−1) + C(n−1, k)', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(412, 130, 348, 100, '세는 법 ②  가르지 않고 그냥 센다'));
    g.push(txt(430, 172, '크기가 k 인 부분집합의 수를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 194, '곧바로 적는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(430, 220, 'C(n, k)', { cls: 'ink bold', size: 'sm' }));

    g.push(arw(214, 110, 214, 126, { cls: 's1', width: 1.6 }));
    g.push(arw(586, 110, 586, 126, { cls: 's1', width: 1.6 }));
    g.push(txt(W / 2, 256, '둘이 같은 집합을 센 값이므로  C(n, k) = C(n−1, k−1) + C(n−1, k).  대수 계산은 한 줄도 하지 않았다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(W / 2, 280, '두 경우가 겹치지 않는지와 빠뜨린 경우가 없는지를 확인하는 것이 이 증명의 알맹이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-pascal-count',
        svg: svg({
            width: W, height: H,
            title: '파스칼 항등식의 조합적 증명',
            desc: 'n 명에서 k 명을 뽑는 방법을 Bob 이 뽑히는지로 가른 셈과 그냥 센 셈이 같다는 것이 파스칼 항등식이다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 18장 — 생성함수
 * ================================================================== */

/* ---- 18-1. 수열을 하나의 함수로 접는다 ---- */
add((() => {
    const W = 800, H = 296;
    const g = [];
    g.push(txt(W / 2, 26, '수열의 항을 계수 자리에 꽂으면 무한 수열이 함수 하나가 된다', { anchor: 'middle', cls: 'ink bold' }));

    const seq = ['0', '1', '1', '2', '3', '5', '8', '13', '⋯'];
    const cw = 52, x0 = 108, yy = 72;
    g.push(cells(x0, yy, cw, 32, seq, { idx: ['f~0', 'f~1', 'f~2', 'f~3', 'f~4', 'f~5', 'f~6', 'f~7', ''], idxTop: true, small: true }));
    g.push(txt(x0 - 14, yy + 22, '수열', { anchor: 'end', cls: 'ink bold', size: 'sm' }));

    for (let i = 0; i < 8; i += 1) g.push(arw(x0 + i * cw + cw / 2, yy + 38, x0 + i * cw + cw / 2, yy + 62, { cls: 's1', width: 1.2 }));

    const yB = 166;
    g.push(txt(x0 - 14, yB, '함수', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0, yB, 'F(x) = 0 + 1x + 1x² + 2x³ + 3x⁴ + 5x⁵ + 8x⁶ + 13x⁷ + ⋯', { cls: 'ink', size: 'sm' }));
    g.push(txt(x0, yB + 28, '계수를 꺼내는 표기        [xⁿ] F(x) = f~n', { cls: 'ink bold', size: 'sm' }));

    g.push(panel(40, 216, 720, 62, ''));
    g.push(txt(58, 242, '수열에 대한 물음이 함수에 대한 대수 계산으로 바뀐다. 점화식은 방정식이 되고, 닫힌 형태를 얻는 일은 계수를 꺼내는 일이 된다', { cls: 'ink', size: 'sm' }));
    g.push(txt(58, 266, 'x 는 자리를 표시하는 표식일 뿐이어서 값을 대입하지 않는다. 수렴을 따지지 않아도 되는 까닭이 이것이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-gf-fold',
        svg: svg({
            width: W, height: H,
            title: '수열을 생성함수로 접는다',
            desc: '피보나치 수열의 항을 차례로 거듭제곱의 계수 자리에 넣어 함수 하나로 만든다',
            body: g.join(''),
        }),
    };
})());

/* ---- 18-2. 곱셈이 왜 합성곱인가 — 대각선 ---- */
add((() => {
    const W = 800, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '같은 차수의 항이 대각선 위에 모인다', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 96, ch = 40, x0 = 134, y0 = 88;
    const A = ['a~0', 'a~1', 'a~2', 'a~3'];
    const B = ['b~0', 'b~1', 'b~2', 'b~3'];
    const sup = ['⁰', '¹', '²', '³', '⁴', '⁵', '⁶'];
    for (let j = 0; j < 4; j += 1) g.push(txt(x0 + j * cw + cw / 2, y0 - 12, `${B[j]} x${sup[j]}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 4; i += 1) {
        g.push(txt(x0 - 12, y0 + i * ch + 26, `${A[i]} x${sup[i]}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        for (let j = 0; j < 4; j += 1) {
            const on = i + j === 3;
            g.push(box(x0 + j * cw, y0 + i * ch, cw, ch, { fill: on ? C2 : C1, op: on ? 0.24 : 0.07, stroke: on ? C2 : CG, sw: on ? 1.8 : 1, rx: 2 }));
            g.push(txt(x0 + j * cw + cw / 2, y0 + i * ch + 25, `${A[i]}${B[j]} x${sup[i + j]}`, { anchor: 'middle', cls: on ? 'ink bold' : 'ink2', size: 'sm' }));
        }
    }
    g.push(txt(x0 + 4 * cw + 14, y0 + 16, 'x³ 의 항이', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0 + 4 * cw + 14, y0 + 36, '이 대각선에', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(x0 + 4 * cw + 14, y0 + 56, '모여 있다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, 268], [W - 24, 268]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 292, '[x³](A·B) = a~0 b~3 + a~1 b~2 + a~2 b~1 + a~3 b~0 — 이것이 합성곱이고, 세는 말로는 ‘사과 k 개와 바나나 3−k 개’를 k 마다 더한 것이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 314, '그래서 서로 겹치지 않는 두 종류에서 고르는 방법의 생성함수는 두 생성함수의 곱이다(합성곱 규칙)', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-convolution-diagonal',
        svg: svg({
            width: W, height: H,
            title: '생성함수의 곱과 합성곱',
            desc: '두 급수의 항을 표로 늘어놓으면 차수가 같은 항이 대각선에 모이고, 그 대각선의 합이 곱의 계수가 된다',
            body: g.join(''),
        }),
    };
})());

/* ---- 18-3. 말도 안 되는 셈 문제가 약분된다 ---- */
add((() => {
    const W = 800, H = 306;
    const g = [];
    g.push(txt(W / 2, 26, '조건이 복잡해도 곱해 놓으면 약분된다', { anchor: 'middle', cls: 'ink bold' }));

    const items = [
        ['사과', '개수가 짝수', '1 / (1 − x²)'],
        ['바나나', '개수가 5 의 배수', '1 / (1 − x⁵)'],
        ['오렌지', '넷 이하', '(1 − x⁵) / (1 − x)'],
        ['배', '하나 이하', '1 + x'],
    ];
    const bw2 = 176, bx = 30;
    items.forEach((it, i) => {
        const x = bx + i * (bw2 + 12);
        g.push(box(x, 56, bw2, 96, { stroke: CG, sw: 1, rx: 6 }));
        g.push(txt(x + bw2 / 2, 80, it[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x + bw2 / 2, 104, it[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(box(x + 14, 118, bw2 - 28, 26, { fill: C1, op: 0.14, stroke: C1, sw: 1.3, rx: 3 }));
        g.push(txt(x + bw2 / 2, 136, it[2], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        if (i < 3) g.push(txt(x + bw2 + 6, 108, '×', { anchor: 'middle', cls: 'ink bold' }));
    });

    g.push(arw(W / 2, 158, W / 2, 178, { cls: 's2', width: 1.8 }));
    g.push(txt(W / 2, 206, '1/(1 − x²) · 1/(1 − x⁵) · (1 − x⁵)/(1 − x) · (1 + x)  =  1/(1 − x)²', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 232, '1 − x² = (1 − x)(1 + x) 이므로 (1 + x) 가 지워지고, (1 − x⁵) 는 서로 지워진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[24, 252], [W - 24, 252]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 276, '[xⁿ] 1/(1 − x)² = n + 1 이므로 과일 n 개를 담는 방법은 n + 1 가지다. n = 6 이면 7 가지이고 손으로 세어 확인할 수 있다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 298, '네 조건을 손으로 따지면 경우가 뒤엉키지만, 곱으로 적어 두면 약분이 그 일을 대신한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-fruit-cancel',
        svg: svg({
            width: W, height: H,
            title: '네 가지 제약이 붙은 셈 문제가 약분된다',
            desc: '사과 바나나 오렌지 배에 각각 붙은 제약의 생성함수를 곱하면 거의 모두 약분되어 1 나누기 1 빼기 x 의 제곱만 남는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 18-4. 비네 공식 — 둘째 뿌리는 곧 사라진다 ---- */
add((() => {
    const W = 800, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '피보나치 수는 큰 뿌리 하나로 거의 다 설명된다', { anchor: 'middle', cls: 'ink bold' }));

    const phi = (1 + Math.sqrt(5)) / 2, psi = (1 - Math.sqrt(5)) / 2, s5 = Math.sqrt(5);
    const N = 10;
    const xR = [0, N + 0.9], yR = [0, 62];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 62, y: 62, w: 344, h: 172 } });
    g.push(axes2(f, { xRange: xR, yRange: yR, xTicks: [2, 4, 6, 8, 10], yTicks: [20, 40, 60], xLabel: 'n', yLabel: '값' }));
    const bw3 = 14;
    for (let k = 1; k <= N; k += 1) {
        const fib = Math.round((phi ** k - psi ** k) / s5);
        g.push(box(f.X(k) - bw3, f.Y(fib), bw3, f.Y(0) - f.Y(fib), { fill: C1, op: 0.5, stroke: 'none', rx: 1 }));
        const lead = phi ** k / s5;
        g.push(box(f.X(k), f.Y(lead), bw3, f.Y(0) - f.Y(lead), { fill: C2, op: 0.5, stroke: 'none', rx: 1 }));
    }
    g.push(legend(96, 86, [{ slot: 1, name: 'f~n' }, { slot: 2, name: 'φⁿ / √5' }]));

    const px0 = 438, pw = 322;
    g.push(panel(px0, 62, pw, 172, '두 뿌리의 크기가 다르다'));
    g.push(txt(px0 + 18, 104, 'φ = (1 + √5)/2 = 1.618…', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(px0 + 18, 128, 'ψ = (1 − √5)/2 = −0.618…', { cls: 'ink', size: 'sm' }));
    g.push(ln([[px0 + 18, 144], [px0 + pw - 18, 144]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 18, 168, '|ψ| < 1 이므로 ψⁿ → 0 이고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 190, 'n = 10 에서 ψⁿ/√5 는 0.004 뿐이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 218, '그래서 f~n ∼ φⁿ / √5', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, 254], [W - 24, 254]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 278, 'f~n = (φⁿ − ψⁿ)/√5.  정수만 더해 만든 수열의 닫힌 형태에 √5 가 두 번 나오지만 서로 지워져 언제나 정수가 된다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 300, '부분분수가 두 뿌리를 갈라 주었고 그중 큰 것이 자라는 속도를 정한다 — 지수적으로 자란다는 것이 이 식에서 곧바로 보인다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-c-fibonacci-binet',
        svg: svg({
            width: W, height: H,
            title: '비네 공식과 두 뿌리의 크기',
            desc: '피보나치 수와 황금비의 거듭제곱을 5의 제곱근으로 나눈 값이 거의 겹친다. 둘째 뿌리의 절댓값이 1 보다 작아 금방 사라지기 때문이다',
            body: g.join(''),
        }),
    };
})());

/* ---- 18-5. 하노이 탑 — 점화식이 어디서 오는가 ---- */
add((() => {
    const W = 800, H = 292;
    const g = [];
    g.push(txt(W / 2, 26, '가장 큰 원판을 옮기려면 나머지가 한 기둥에 모여 있어야 한다', { anchor: 'middle', cls: 'ink bold' }));

    const stage = (px, title, sub, layout) => {
        const out = [panel(px, 52, 244, 168, title, sub)];
        const base = 190, xs = [px + 52, px + 122, px + 192];
        out.push(ln([[px + 20, base], [px + 224, base]], { stroke: CK, sw: 2 }));
        for (const x of xs) out.push(ln([[x, base], [x, base - 66]], { stroke: CK, sw: 2 }));
        layout.forEach((stack, k) => {
            stack.forEach((size, h) => {
                const w = 20 + size * 11;
                const big = size === 3;
                out.push(box(xs[k] - w / 2, base - 14 * (h + 1), w, 12,
                    { fill: big ? C2 : C1, op: big ? 0.45 : 0.28, stroke: big ? C2 : C1, sw: 1.2, rx: 2 }));
            });
        });
        return out.join('');
    };

    // 위에서 아래로 쌓는 순서로 적는다. 3 이 가장 큰 원판.
    g.push(stage(22, '1단계  작은 것 n−1 개를 옮긴다', 't~{n−1} 번', [[3], [2, 1, 0], []]));
    g.push(stage(278, '2단계  가장 큰 것을 옮긴다', '1 번', [[], [2, 1, 0], [3]]));
    g.push(stage(534, '3단계  작은 것을 다시 얹는다', 't~{n−1} 번', [[], [], [3, 2, 1, 0]]));
    g.push(arw(266, 136, 276, 136, { cls: 'ark', width: 1.4 }));
    g.push(arw(522, 136, 532, 136, { cls: 'ark', width: 1.4 }));

    g.push(ln([[24, 238], [W - 24, 238]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 262, '위가 방법을 주므로 t~n ≤ 2 t~{n−1} + 1 이고, 가장 큰 원판을 옮기기 전후에 반드시 이 일이 필요하므로 t~n ≥ 2 t~{n−1} + 1 이다', { cls: 'ink', size: 'sm' }));
    g.push(txt(24, 284, '두 부등식이 만나 t~n = 2 t~{n−1} + 1 이 되고, 생성함수로 풀면 t~n = 2ⁿ − 1 이다', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'mcs-c-hanoi-stages',
        svg: svg({
            width: W, height: H,
            title: '하노이 탑의 재귀적 해법 세 단계',
            desc: '작은 원판을 모두 가운데로 옮기고 가장 큰 원판을 옮긴 뒤 작은 원판을 다시 얹는 세 단계에서 점화식이 나온다',
            body: g.join(''),
        }),
    };
})());

export default figures;
