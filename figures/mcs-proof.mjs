/**
 * mcs 3장(증명이란 무엇인가) · 4장(정렬성 원리) · 5장(논리식) · 6장(수학적 자료형)의 그림.
 *
 * 이름은 모두 `mcs-p-` 로 시작한다(담당 A 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `n~0` 표기를, 나머지는 유니코드(≤ ≥ ≠ ∀ ∃ ¬ ∧ ∨ ∈ ⊆ ∅ √ ² ³ ₀ ₁ · ×)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 주제는 증명이다. 그래서 그림도 대개 좌표 곡선이 아니라
 * ‘증명의 모양’ — 어느 줄이 무슨 일을 하는가, 어디서 모순이 나오는가 — 을 보인다.
 */
import { svg, frame, txt } from './lib.mjs';

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

/**
 * 진리표를 그린다. cols 는 열 이름, rows 는 문자열 배열의 배열.
 * hlCol 에 든 열 번호는 배경을 옅게 칠한다(그 열이 결론이라는 표시).
 */
function truthTable(x, y, cols, rows, { cw = 54, rh = 26, hlCol = [], hlColor = C1, note = null } = {}) {
    const g = [];
    const W = cw * cols.length;
    for (const c of hlCol) {
        g.push(box(x + c * cw, y, cw, rh * (rows.length + 1), { fill: hlColor, op: 0.14, stroke: 'none', rx: 2 }));
    }
    cols.forEach((c, i) => {
        g.push(txt(x + i * cw + cw / 2, y + 18, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    g.push(ln([[x, y + rh], [x + W, y + rh]], { stroke: CK, sw: 1.3 }));
    rows.forEach((row, r) => {
        row.forEach((v, i) => {
            const col = v === 'T' ? C3 : v === 'F' ? C2 : CI;
            g.push(txt(x + i * cw + cw / 2, y + rh * (r + 2) - 8, v, {
                anchor: 'middle', cls: 'ink', size: 'sm',
            }).replace('class="ink sm"', `class="sm" fill="${col}"`));
        });
        if (r < rows.length - 1) g.push(ln([[x, y + rh * (r + 2)], [x + W, y + rh * (r + 2)]], { stroke: CG, sw: 0.8 }));
    });
    g.push(box(x, y, W, rh * (rows.length + 1), { stroke: CK, sw: 1.2, rx: 3 }));
    if (note) g.push(txt(x + W / 2, y + rh * (rows.length + 1) + 18, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
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
 * 3장 — 증명이란 무엇인가
 * ================================================================== */

/* ---- 3-1. 표본을 아무리 늘려도 전칭명제는 세워지지 않는다 ---- */
add((() => {
    const W = 780, H = 300;
    const g = [];
    g.push(txt(W / 2, 26, '마흔 번 맞은 것과 언제나 맞는 것은 다르다', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 17, x0 = 24, y0 = 62, ch = 30;
    const items = [], hl = {}, idx = [];
    for (let n = 0; n <= 40; n += 1) {
        items.push('');
        idx.push(n % 10 === 0 ? String(n) : '');
        hl[n] = n === 40 ? C2 : C3;
    }
    g.push(cells(x0, y0, cw, ch, items, { hl, idx }));
    g.push(txt(x0, y0 - 12, 'n = 0 부터 40 까지 p(n) = n² + n + 41 을 하나씩 소수인지 검사한 결과', { cls: 'ink2', size: 'sm' }));

    // 40 번 칸을 가리킨다.
    const x40 = x0 + 40 * cw + cw / 2;
    g.push(arw(x40 + 40, y0 + ch + 56, x40 + 3, y0 + ch + 22, { cls: 's2', width: 1.8 }));
    g.push(txt(x40 + 46, y0 + ch + 64, 'n = 40 에서 깨진다', { anchor: 'end', cls: 'ink bold', size: 'sm' }));
    g.push(txt(x40 + 46, y0 + ch + 82, 'p(40) = 1681 = 41 × 41', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(x0, y0 + ch + 64, '초록 칸 마흔 개는 모두 소수다', { cls: 'ink2', size: 'sm' }));

    // 오일러 추측
    g.push(ln([[24, 208], [W - 24, 208]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 234, '오일러가 1769년에 내놓은 추측 — a⁴ + b⁴ + c⁴ = d⁴ 인 양의 정수는 없다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 254, '218년 뒤에 반례가 나왔다. a = 95800, b = 217519, c = 414560, d = 422481', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 278, '검사할 수 있는 범위 안에 반례가 없다는 것은 아무것도 보장하지 않는다. 그래서 증명이 필요하다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'mcs-p-sample-check',
        svg: svg({
            width: W, height: H,
            title: '유한한 표본 검사는 전칭명제의 증명이 아니다',
            desc: 'n 제곱 더하기 n 더하기 41 은 n 이 39 까지 소수지만 40 에서 소수가 아니게 된다. 오일러 추측은 218년 만에 아주 큰 반례로 무너졌다',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-2. 밑작업이 곧 증명은 아니다 — 인수분해로 부호를 읽는다 ---- */
add((() => {
    const W = 780, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '왜 양수인지 보이려면 세 인수의 부호를 읽으면 된다', { anchor: 'middle', cls: 'ink bold' }));

    const xR = [-1, 2.7], yR = [-5, 5.4];
    const f = frame({ xRange: xR, yRange: yR, box: { x: 78, y: 52, w: 330, h: 270 } });
    // 0 ≤ x ≤ 2 구간을 옅게 칠한다.
    g.push(box(f.X(0), f.Y(5.4), f.X(2) - f.X(0), f.Y(-5) - f.Y(5.4), { fill: C1, op: 0.1, stroke: 'none', rx: 0 }));
    g.push(axes2(f, { xRange: xR, yRange: yR, xTicks: [-1, 1, 2], yTicks: [-4, -2, 2, 4], xLabel: 'x', yLabel: 'y' }));
    g.push(fcurve(f, v => -(v ** 3) + 4 * v + 1, { from: -1, to: 2.5, stroke: C1, sw: 2.4 }));
    g.push(pdot(f.X(0), f.Y(1), C2, 4));
    g.push(pdot(f.X(2), f.Y(1), C2, 4));
    g.push(txt(f.X(0) + 6, f.Y(1) - 10, '(0, 1)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(2) + 6, f.Y(1) - 10, '(2, 1)', { cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(1.05), f.Y(4.6), '−x³ + 4x + 1', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(f.X(1), f.Y(-4.4), '색칠한 구간이 0 ≤ x ≤ 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 인수의 부호
    const px0 = 440, pw = 316;
    g.push(panel(px0, 52, pw, 270, '0 ≤ x ≤ 2 에서 세 인수는 모두 0 이상이다'));
    g.push(txt(px0 + pw / 2, 96, '−x³ + 4x = x(2 − x)(2 + x)', { anchor: 'middle', cls: 'ink bold' }));
    const rows = [['x', '0 ≤ x 이므로 0 이상'], ['2 − x', 'x ≤ 2 이므로 0 이상'], ['2 + x', 'x ≥ 0 이므로 2 이상']];
    rows.forEach((r, i) => {
        const yy = 132 + i * 34;
        g.push(box(px0 + 18, yy - 18, 74, 26, { stroke: C3, sw: 1.5, fill: C3, op: 0.16 }));
        g.push(txt(px0 + 55, yy, r[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(px0 + 102, yy, r[1], { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[px0 + 18, 244], [px0 + pw - 18, 244]], { stroke: CG, sw: 1 }));
    g.push(txt(px0 + 18, 266, '0 이상인 것들의 곱은 0 이상이고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 284, '거기에 1 을 더하면 양수다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(px0 + 18, 308, '이 세 줄이 증명의 전부다', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(24, 348, '왼쪽 그림은 밑작업이다. 그림은 어디를 봐야 하는지 알려 주지만, 0 ≤ x ≤ 2 라는 무한히 많은 x 를 다 확인해 주지는 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-cubic-sign',
        svg: svg({
            width: W, height: H,
            title: '인수분해로 부호를 읽어 증명을 만든다',
            desc: '왼쪽은 곡선 그림, 오른쪽은 x, 2 빼기 x, 2 더하기 x 세 인수가 구간 안에서 모두 0 이상이라는 표',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-3. 여섯 명이면 3인 클럽 또는 3인 낯선 무리 — 경우 나누기 ---- */
add((() => {
    const W = 780, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '경우를 빠짐없이 나누었는지 확인하는 것이 증명의 일부다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 244, py = 48, ph = 244;
    const cxs = [16, 268, 520];

    // 패널 1 — x 를 고르고 나머지 다섯을 둘로 가른다
    g.push(panel(cxs[0], py, pw, ph, '① 한 명 x 를 고른다', 'x 를 만난 쪽 / 안 만난 쪽'));
    const c1 = cxs[0] + pw / 2;
    g.push(pdot(c1, py + 92, CI, 6));
    g.push(txt(c1, py + 82, 'x', { anchor: 'middle', cls: 'ink bold' }));
    const metPts = [[c1 - 74, py + 168], [c1 - 40, py + 190], [c1 - 6, py + 168]];
    const notPts = [[c1 + 34, py + 176], [c1 + 72, py + 194]];
    for (const p of metPts) { g.push(ln([[c1, py + 96], p], { stroke: C1, sw: 1.8 })); }
    for (const p of notPts) { g.push(ln([[c1, py + 96], p], { stroke: CG, sw: 1.6, dash: '4 4' })); }
    for (const p of metPts) g.push(pdot(p[0], p[1], C1, 5));
    for (const p of notPts) g.push(pdot(p[0], p[1], CK, 5));
    g.push(txt(c1 - 40, py + 218, '만난 쪽 3명', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(c1 + 54, py + 218, '아닌 쪽 2명', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxs[0] + 14, py + 238, '한쪽은 반드시 3명 이상이다', { cls: 'ink2', size: 'sm' }));

    // 패널 2 — 만난 쪽 셋 중 두 명이 서로 만났다
    g.push(panel(cxs[1], py, pw, ph, '② 그 셋 중 두 명이 서로 만났다', '→ x 를 더해 3인 클럽'));
    const c2 = cxs[1] + pw / 2;
    const t2 = [[c2 - 56, py + 172], [c2 + 4, py + 196], [c2 + 60, py + 160]];
    g.push(pdot(c2, py + 96, CI, 6));
    g.push(txt(c2, py + 86, 'x', { anchor: 'middle', cls: 'ink bold' }));
    for (const p of t2) g.push(ln([[c2, py + 100], p], { stroke: C1, sw: 1.8 }));
    g.push(ln([t2[0], t2[1]], { stroke: C2, sw: 2.6 }));
    for (const p of t2) g.push(pdot(p[0], p[1], C1, 5));
    g.push(txt(c2 - 26, py + 224, '이 한 변이 있으면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cxs[1] + 14, py + 238, 'x 와 이 두 명이 3인 클럽이 된다', { cls: 'ink2', size: 'sm' }));

    // 패널 3 — 셋이 서로 아무도 안 만났다
    g.push(panel(cxs[2], py, pw, ph, '③ 그 셋이 서로 아무도 안 만났다', '→ 그 셋이 곧 3인 낯선 무리'));
    const c3 = cxs[2] + pw / 2;
    const t3 = [[c3 - 56, py + 172], [c3 + 4, py + 196], [c3 + 60, py + 160]];
    g.push(pdot(c3, py + 96, CI, 6));
    g.push(txt(c3, py + 86, 'x', { anchor: 'middle', cls: 'ink bold' }));
    for (const p of t3) g.push(ln([[c3, py + 100], p], { stroke: C1, sw: 1.8 }));
    g.push(ln([t3[0], t3[1]], { stroke: CG, sw: 1.6, dash: '4 4' }));
    g.push(ln([t3[1], t3[2]], { stroke: CG, sw: 1.6, dash: '4 4' }));
    g.push(ln([t3[0], t3[2]], { stroke: CG, sw: 1.6, dash: '4 4' }));
    for (const p of t3) g.push(pdot(p[0], p[1], C3, 5));
    g.push(txt(cxs[2] + 14, py + 238, '셋은 서로 모르는 사이다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(16, 320, '②와 ③ 말고 다른 경우는 없다. 셋 중 서로 만난 짝이 있거나 없거나 둘 중 하나이기 때문이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(16, 340, '안 만난 쪽이 3명 이상인 경우도 같은 방식으로 갈라진다 — 실선과 점선을 맞바꾸면 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-friends-cases',
        svg: svg({
            width: W, height: H,
            title: '여섯 명이면 3인 클럽 또는 3인 낯선 무리가 있다',
            desc: '한 사람을 고르고 나머지 다섯을 만난 쪽과 아닌 쪽으로 가른 다음, 만난 쪽 셋에서 다시 두 경우로 나누는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 3-4. 증명 한 편의 해부 ---- */
add((() => {
    const W = 780, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '읽는 사람을 위해 쓰면 줄마다 하는 일이 다르다', { anchor: 'middle', cls: 'ink bold' }));

    const bx = 20, bw = 470, by = 46;
    const lines = [
        ['귀류법을 쓴다.', '계획을 먼저 밝힌다', C1],
        ['√2 가 유리수라 하자. 그러면 기약분수 n / d 로 쓸 수 있다.', '가정을 식으로 적는다', C1],
        ['양변을 제곱하면 2d² = n² 이다.', null, null],
        ['n² 이 짝수이므로 n 도 짝수다. n = 2k 라 하자.', '한 줄마다 근거를 댄다', C3],
        ['2d² = 4k² 이므로 d² = 2k² 이고, 따라서 d 도 짝수다.', null, null],
        ['n 과 d 가 모두 짝수인데 n / d 는 기약분수였다. 모순이다.', '모순을 명시한다', C2],
        ['따라서 √2 는 무리수다.', '결론을 직접 적고 끝맺는다', C2],
    ];
    const lh = 38;
    g.push(box(bx, by, bw, lh * lines.length + 16, { stroke: CK, sw: 1.3, rx: 6 }));
    lines.forEach((L, i) => {
        const yy = by + 30 + i * lh;
        g.push(txt(bx + 16, yy, L[0], { cls: 'ink', size: 'sm' }));
        if (L[1]) {
            g.push(ln([[bx + bw + 6, yy - 5], [bx + bw + 26, yy - 5]], { stroke: L[2], sw: 1.6 }));
            g.push(txt(bx + bw + 34, yy - 1, L[1], { cls: 'ink bold', size: 'sm' }));
        }
    });
    g.push(txt(bx, by - 8, '증명 (√2 는 무리수다)', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, 356, '기호를 늘어놓은 계산이 아니라 문장으로 이어진 글이어야 한다. ‘자명하다’ 로 넘어간 자리가 대개 증명이 무너진 자리다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-proof-anatomy',
        svg: svg({
            width: W, height: H,
            title: '증명 한 편에서 각 줄이 하는 일',
            desc: '루트 2 가 무리수라는 귀류법 증명의 일곱 줄에, 계획 선언·가정·근거·모순·끝맺음이라는 역할을 붙인 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 4장 — 정렬성 원리
 * ================================================================== */

/* ---- 4-1. 왜 하필 음이 아닌 정수인가 ---- */
add((() => {
    const W = 780, H = 406;
    const g = [];
    g.push(txt(W / 2, 26, '조건 하나만 빼도 최소 원소는 사라진다', { anchor: 'middle', cls: 'ink bold' }));

    const ax0 = 40, ax1 = 700, zero = ax0 + 20;
    const rows = [
        {
            title: '음이 아닌 정수 — 정렬되어 있다', ok: true, kind: 'right',
            pts: [0, 1, 2, 3, 4, 5, 6, 7],
            note: '왼쪽 끝이 있다. 아무 부분집합을 잡아도 그 안에서 왼쪽 끝이 있다',
        },
        {
            title: '음의 정수 — 정렬되어 있지 않다', ok: false, kind: 'left',
            pts: [-1, -2, -3, -4, -5, -6, -7],
            note: '왼쪽으로 끝없이 이어진다. 최소 원소가 없다',
        },
        {
            title: '양의 유리수 — 정렬되어 있지 않다', ok: false, kind: 'rational',
            note: '0 에 가까워지지만 0 은 이 집합에 없다. 최소 원소가 없다',
        },
    ];

    rows.forEach((r, i) => {
        const yy = 68 + i * 104;
        const my = yy + 36;
        g.push(txt(20, yy, r.title, { cls: 'bold sm', size: 'sm' })
            .replace('class="ink sm"', `class="sm bold" fill="${r.ok ? C3 : C2}"`));
        g.push(ln([[ax0, my], [ax1, my]], { stroke: CK, sw: 1.2 }));

        if (r.kind === 'rational') {
            for (let k = 1; k <= 14; k += 1) g.push(pdot(zero + 620 / k, my, C2, 4));
            g.push(ln([[zero, my - 9], [zero, my + 9]], { stroke: CG, sw: 1.4, dash: '3 3' }));
            for (const [x, lb] of [[zero, '0'], [zero + 620 / 3, '1/3'], [zero + 310, '1/2'], [zero + 620, '1']]) {
                g.push(txt(x, my + 22, lb, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
            g.push(txt(300, my - 26, '끝없이 왼쪽으로 몰린다', { cls: 'ink2', size: 'sm' }));
            g.push(arw(296, my - 22, zero + 34, my - 10, { cls: 's2', width: 1.6 }));
        } else {
            r.pts.forEach((v, k) => {
                const x = r.kind === 'right' ? zero + k * 78 : ax1 - 20 - k * 78;
                g.push(pdot(x, my, r.ok ? C3 : C2, 5));
                g.push(txt(x, my + 22, String(v), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            });
            if (r.kind === 'right') {
                g.push(txt(ax1 - 6, my - 14, '계속', { anchor: 'end', cls: 'ink2', size: 'sm' }));
                g.push(ring(zero, my, 11, C3, 2.2));
                g.push(txt(zero + 18, my - 12, '최소 원소', { cls: 'bold sm', size: 'sm' })
                    .replace('class="ink sm"', `class="sm bold" fill="${C3}"`));
            } else {
                g.push(txt(ax0 + 4, my - 14, '계속', { cls: 'ink2', size: 'sm' }));
                g.push(arw(ax0 + 62, my, ax0 + 2, my, { cls: 's2', width: 1.6 }));
            }
        }
        g.push(txt(20, yy + 82, r.note, { cls: 'ink2', size: 'sm' }));
    });

    g.push(ln([[20, 366], [W - 20, 366]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 392, '정렬성 원리가 말하는 것은 첫째 줄의 성질이다. 나머지 두 줄은 그 성질이 공짜가 아니라는 증거다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-wop-scope',
        svg: svg({
            width: W, height: H,
            title: '정렬성 원리가 성립하는 곳과 성립하지 않는 곳',
            desc: '음이 아닌 정수에는 최소 원소가 있고, 음의 정수와 양의 유리수에는 없다는 것을 수직선으로 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-2. 가장 작은 반례를 잡는다 ---- */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '가장 작은 반례를 잡아 놓고, 그보다 작은 반례를 만들어 낸다', { anchor: 'middle', cls: 'ink bold' }));

    const cw = 46, x0 = 40, y0 = 88, ch = 40;
    const vals = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13];
    const counter = new Set([7, 9, 12, 13]);
    const items = vals.map(() => '');
    const hl = {};
    vals.forEach((v, i) => { if (counter.has(v)) hl[i] = C2; });
    g.push(cells(x0, y0, cw, ch, items, { hl, idx: vals.map(String) }));
    g.push(txt(x0, y0 - 14, '주황 칸이 반례 — P(n) 이 거짓인 n. 아직 어디에 있는지 모른다', { cls: 'ink2', size: 'sm' }));

    const cIdx = 7;
    const cx = x0 + cIdx * cw + cw / 2;
    g.push(ring(cx, y0 + ch / 2, 25, C2, 2.4));
    g.push(txt(cx, y0 - 34, '가장 작은 반례 c', { anchor: 'middle', cls: 'bold sm' })
        .replace('class="ink bold sm"', `class="bold sm" fill="${C2}"`));
    g.push(arw(cx, y0 - 28, cx, y0 - 6, { cls: 's2', width: 1.6 }));

    // c 왼쪽은 전부 참
    g.push(box(x0, y0 + ch + 26, cIdx * cw, 26, { fill: C3, op: 0.16, stroke: C3, sw: 1.4, rx: 4 }));
    g.push(txt(x0 + (cIdx * cw) / 2, y0 + ch + 44, 'c 보다 작은 n 에서는 P(n) 이 참이다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(x0 + cIdx * cw + 14, y0 + ch + 44, '여기가 쓸 수 있는 정보다', { cls: 'ink2', size: 'sm' }));

    // 모순 화살표
    g.push(curvePath(`M${cx} ${y0 + ch + 76} C ${cx} ${y0 + ch + 112} ${x0 + 90} ${y0 + ch + 112} ${x0 + 90} ${y0 + ch + 82}`, { stroke: C2, sw: 2, marker: 'ar2' }));
    g.push(txt(cx + 10, y0 + ch + 122, 'c 에서 출발해 c 보다 작은 반례를 만들어 낸다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(cx + 10, y0 + ch + 142, '그런 것이 있으면 c 가 가장 작다는 데 어긋난다 — 모순', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 306], [W - 20, 306]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 330, '결론은 ‘반례 집합이 비어 있다’ 다. 반례를 하나 찾아 없애는 것이 아니라, 최소 반례가 있을 수 없다는 것을 보인다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-least-counterexample',
        svg: svg({
            width: W, height: H,
            title: '최소 반례를 잡는다는 발상',
            desc: '반례들 중 가장 작은 것을 c 라 하면 c 보다 작은 곳에서는 명제가 참이고, 그 정보로 c 보다 작은 반례를 만들어 모순을 얻는다',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-3. 소인수분해의 존재 ---- */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '최소 반례를 두 조각으로 쪼개면 두 조각은 반례가 아니다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 실제 분해 나무
    const nodes = [
        [150, 66, '360'], [96, 130, '8'], [212, 130, '45'],
        [58, 194, '2'], [124, 194, '4'], [176, 194, '5'], [252, 194, '9'],
        [96, 254, '2'], [152, 254, '2'], [222, 254, '3'], [282, 254, '3'],
    ];
    const edges = [[0, 1], [0, 2], [1, 3], [1, 4], [2, 5], [2, 6], [4, 7], [4, 8], [6, 9], [6, 10]];
    for (const [a, b] of edges) {
        g.push(ln([[nodes[a][0], nodes[a][1] + 15], [nodes[b][0], nodes[b][1] - 15]], { stroke: CK, sw: 1.3 }));
    }
    nodes.forEach((nd, i) => {
        const prime = ['2', '3', '5'].includes(nd[2]);
        g.push(`<circle cx="${nd[0]}" cy="${nd[1]}" r="17" fill="${prime ? C3 : 'none'}" fill-opacity="${prime ? 0.2 : 0}" stroke="${prime ? C3 : CK}" stroke-width="${prime ? 1.9 : 1.3}"/>`);
        g.push(txt(nd[0], nd[1] + 5, nd[2], { anchor: 'middle', cls: 'ink', size: 'sm' }));
    });
    g.push(txt(20, 300, '360 = 2 · 2 · 2 · 3 · 3 · 5. 초록 동그라미가 더 쪼갤 수 없는 소수다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 320, '나무가 언제나 이렇게 끝난다는 것이 증명해야 할 내용이다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽: WOP 논증
    const px0 = 356, pw = 404;
    g.push(panel(px0, 52, pw, 236, '왜 나무가 반드시 끝나는가'));
    const bxw = 340;
    const steps = [
        ['소수의 곱으로 못 쓰는 정수가 있다고 하자', CK],
        ['그중 가장 작은 것을 n 이라 한다', C2],
        ['n 은 소수가 아니다 — 소수는 길이 1 인 곱이니까', CK],
        ['그러면 1 < a, b < n 인 두 정수의 곱 n = a · b 다', CK],
        ['a, b 는 n 보다 작으니 각각 소수의 곱으로 쓸 수 있다', C3],
        ['두 곱을 이어 붙이면 n 도 소수의 곱이다 — 모순', C2],
    ];
    steps.forEach((s, i) => {
        const yy = 96 + i * 33;
        g.push(box(px0 + 18, yy - 17, bxw, 26, { stroke: s[1], sw: s[1] === CK ? 1.1 : 1.7, fill: s[1] === CK ? 'none' : s[1], op: s[1] === CK ? 1 : 0.14, rx: 4 }));
        g.push(txt(px0 + 30, yy, s[0], { cls: 'ink', size: 'sm' }));
        if (i < steps.length - 1) g.push(arw(px0 + 18 + bxw / 2, yy + 9, px0 + 18 + bxw / 2, yy + 15, { cls: 'ark', width: 1.2 }));
    });
    g.push(txt(px0, 314, '초록 줄이 ‘n 보다 작은 곳에서는 참’ 이라는 정보를 쓰는 자리다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-prime-factor-tree',
        svg: svg({
            width: W, height: H,
            title: '소인수분해가 존재한다는 것의 증명 구조',
            desc: '360 의 분해 나무와, 최소 반례를 두 조각으로 쪼개어 모순을 얻는 여섯 단계',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-4. 무한 감소열이 없다 ---- */
add((() => {
    const W = 780, H = 350;
    const g = [];
    g.push(txt(W / 2, 26, '정렬집합에서는 내려가는 계단이 언젠가 멈춘다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 360, py = 48, ph = 234;
    // 왼쪽: 정수 위의 감소열
    g.push(panel(16, py, pw, ph, '음이 아닌 정수 위에서', '내려가다 반드시 바닥에 닿는다'));
    const seqA = [9, 7, 6, 3, 1, 0];
    seqA.forEach((v, i) => {
        const x = 56 + i * 54, y = py + 84 + (9 - v) * 11;
        g.push(pdot(x, y, C3, 5));
        g.push(txt(x, y - 12, String(v), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i > 0) {
            const px = 56 + (i - 1) * 54, pyy = py + 84 + (9 - seqA[i - 1]) * 11;
            g.push(ln([[px, pyy], [x, y]], { stroke: C3, sw: 1.6 }));
        }
    });
    g.push(ln([[46, py + 194], [346, py + 194]], { stroke: CG, sw: 1, dash: '4 4' }));
    g.push(txt(30, py + 214, '0 에 닿으면 더 내려갈 곳이 없다', { cls: 'bold sm', size: 'sm' })
        .replace('class="ink sm"', `class="sm bold" fill="${C3}"`));

    // 오른쪽: 양의 유리수 위의 감소열
    g.push(panel(400, py, pw, ph, '양의 유리수 위에서', '끝없이 내려갈 수 있다'));
    const seqB = [1, 1 / 2, 1 / 3, 1 / 4, 1 / 5, 1 / 6, 1 / 7];
    seqB.forEach((v, i) => {
        const x = 436 + i * 46, y = py + 96 + (1 - v) * 82;
        g.push(pdot(x, y, C2, 5));
        if (i > 0) {
            const px = 436 + (i - 1) * 46, pyy = py + 96 + (1 - seqB[i - 1]) * 82;
            g.push(ln([[px, pyy], [x, y]], { stroke: C2, sw: 1.6 }));
        }
    });
    g.push(txt(436, py + 84, '1', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[420, py + 194], [740, py + 194]], { stroke: CG, sw: 1, dash: '4 4' }));
    g.push(txt(414, py + 214, '0 에 닿지 않으니 끝없이 내려간다', { cls: 'bold sm', size: 'sm' })
        .replace('class="ink sm"', `class="sm bold" fill="${C2}"`));

    g.push(txt(20, 316, '이 차이가 컴퓨터과학에서 쓰인다. 한 걸음마다 값이 줄고 그 값이 정렬집합에 있으면 계산은 반드시 끝난다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 338, '끝나지 않는다면 줄어드는 값들이 최소 원소 없는 부분집합을 만들 텐데, 정렬집합에서는 그럴 수 없다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-infinite-descent',
        svg: svg({
            width: W, height: H,
            title: '정렬집합에는 무한히 내려가는 열이 없다',
            desc: '왼쪽은 음이 아닌 정수에서 감소열이 0 에 닿아 멈추는 그림, 오른쪽은 양의 유리수에서 끝없이 내려가는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 4-5. 8 이상은 3 과 5 의 배수의 합 ---- */
add((() => {
    const W = 780, H = 330;
    const g = [];
    g.push(txt(W / 2, 26, 'c − 3 을 쓸 수 있으면 c 도 쓸 수 있다', { anchor: 'middle', cls: 'ink bold' }));

    const x0 = 40, cw = 52, y0 = 106, ch = 34;
    const ns = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
    const rep = ['3+5', '3·3', '5·5', '3+3+5', '3·4', '3+5+5', '3·3+5', '5·3', '3+3+5+5', '3·4+5', '3·6', '3+3+3+5+5', '5·4'];
    g.push(cells(x0, y0, cw, ch, ns.map(String), { idx: null }));
    g.push(txt(x0, y0 - 12, '8 이상의 정수를 3 의 배수와 5 의 배수의 합으로 적어 본 것', { cls: 'ink2', size: 'sm' }));
    ns.forEach((v, i) => {
        g.push(txt(x0 + i * cw + cw / 2, y0 + ch + 18, rep[i], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });

    // c = 14 예시로 c-3 → c
    const iC = 6, iP = 3;
    const xc = x0 + iC * cw + cw / 2, xp = x0 + iP * cw + cw / 2;
    g.push(ring(xc, y0 + ch / 2, 22, C2, 2.2));
    g.push(ring(xp, y0 + ch / 2, 22, C3, 2.2));
    g.push(curvePath(`M${xc} ${y0 - 22} C ${xc} ${y0 - 52} ${xp} ${y0 - 52} ${xp} ${y0 - 22}`, { stroke: C2, sw: 1.8, marker: 'ar2' }));
    g.push(txt((xc + xp) / 2, y0 - 58, '3 을 뺀다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[20, 186], [W - 20, 186]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 212, '증명이 하는 일은 이 화살표를 거꾸로 타는 것이다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 234, '표현할 수 없는 8 이상의 정수 중 가장 작은 것을 c 라 하면 c ≥ 11 이다 (8, 9, 10 은 위에 적혀 있으니까).', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 256, 'c − 3 은 8 이상이고 c 보다 작으므로 c − 3 = 3a + 5b 로 쓸 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 278, '양변에 3 을 더하면 c = 3(a + 1) + 5b 다. c 를 표현했으니 c 가 반례라는 데 어긋난다 — 모순', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 304, '8, 9, 10 을 먼저 확인해 둔 것이 c ≥ 11 을 보장하고, 그래서 c − 3 ≥ 8 이 된다. 이 확인을 빼면 증명이 무너진다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-wop-stamps',
        svg: svg({
            width: W, height: H,
            title: '8 이상의 정수는 3 의 배수와 5 의 배수의 합이다',
            desc: '8 부터 20 까지의 표현을 늘어놓고, c 에서 3 을 빼면 더 작은 수가 되어 이미 표현된다는 논증을 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 5장 — 논리식
 * ================================================================== */

/* ---- 5-1. 진리표를 한 열씩 채운다 ---- */
add((() => {
    const W = 780, H = 354;
    const g = [];
    g.push(txt(W / 2, 26, '복잡한 식은 안에서 바깥으로 한 열씩 채운다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(truthTable(24, 50,
        ['A', 'B', '¬A', '¬A ∧ B', 'A ∨ (¬A ∧ B)', 'A ∨ B'],
        [
            ['T', 'T', 'F', 'F', 'T', 'T'],
            ['T', 'F', 'F', 'F', 'T', 'T'],
            ['F', 'T', 'T', 'T', 'T', 'T'],
            ['F', 'F', 'T', 'F', 'F', 'F'],
        ],
        { cw: 96, rh: 30, hlCol: [4, 5], hlColor: C1 }));

    g.push(arw(504, 226, 504, 206, { cls: 's1', width: 1.8 }));
    g.push(txt(24, 248, '색칠한 두 열이 네 줄 모두 같다. 그래서 두 식은 동치다', { cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, 266], [W - 24, 266]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 290, '채우는 순서: ¬A → ¬A ∧ B → A ∨ (¬A ∧ B). 안쪽 괄호가 먼저다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 312, '변수가 n 개면 줄 수는 2ⁿ 이다. 변수 30 개면 10억 줄이 넘어 손으로는 끝낼 수 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 338, '그래서 뒤에서 대수 규칙으로 식을 바꾸는 방법과, 그마저 어렵다는 SAT 이야기가 나온다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-truth-table-calc',
        svg: svg({
            width: W, height: H,
            title: '진리표를 한 열씩 채워 동치를 확인한다',
            desc: 'A 또는 (A 가 아니고 B) 와 A 또는 B 의 진리표를 나란히 채워 두 열이 같음을 보인 표',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-2. 함의의 네 줄 ---- */
add((() => {
    const W = 780, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '거짓이 되는 줄은 하나뿐이다', { anchor: 'middle', cls: 'ink bold' }));

    g.push(truthTable(24, 50, ['P', 'Q', 'P → Q', '줄 이름'],
        [['T', 'T', 'T', '(참참)'], ['T', 'F', 'F', '(참거)'], ['F', 'T', 'T', '(거참)'], ['F', 'F', 'T', '(거거)']],
        { cw: 78, rh: 30, hlCol: [2], hlColor: C1 }));
    g.push(box(24, 50 + 30 * 2, 78 * 4, 30, { stroke: C2, sw: 2.2, rx: 3 }));
    g.push(arw(370, 50 + 30 * 2 + 15, 322, 50 + 30 * 2 + 15, { cls: 's2', width: 1.8 }));
    g.push(txt(378, 50 + 30 * 2 + 19, '약속을 어긴 유일한 줄', { cls: 'ink bold', size: 'sm' }));

    g.push(txt(378, 84, '가정이 참이고 결론도 참 — 지켰다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(378, 174, '가정이 거짓이면 어겼다고 할 수 없으니', { cls: 'ink2', size: 'sm' }));
    g.push(txt(378, 194, '아래 두 줄은 자동으로 참이다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[24, 212], [W - 24, 212]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 236, '왜 이렇게 정하는가 — 명세 열두 줄을 하나의 식으로 묶고 싶기 때문이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(24, 258, '(C₁ → A₁) ∧ (C₂ → A₂) ∧ … ∧ (C₁₂ → A₁₂)', { cls: 'ink', size: 'sm' }));
    g.push(txt(24, 280, '조건 C₂ 와 C₅ 만 성립하고 시스템이 A₂ 와 A₅ 만 했다면, 명세를 지킨 것이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 300, '그러려면 조건이 성립하지 않은 열 개의 함의가 모두 참이어야 한다. 위의 아래 두 줄이 그 일을 한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 326, '인과관계는 없다. ‘돼지가 날면 계정이 털리지 않는다’ 도 수학적으로는 참인 함의다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-implies-rows',
        svg: svg({
            width: W, height: H,
            title: '함의의 진리표 네 줄과 그 이유',
            desc: 'P 이면 Q 의 진리표에서 거짓인 줄이 참-거짓 한 줄뿐임을 보이고, 시스템 명세를 한 식으로 묶는 데 그 규칙이 쓰이는 것을 설명한 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-3. 타당 · 만족가능 · 불만족 ---- */
add((() => {
    const W = 780, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '식은 세 무리로 갈린다', { anchor: 'middle', cls: 'ink bold' }));

    const bx = 40, bw = 700, by = 54, bh = 100;
    g.push(box(bx, by, bw, bh, { stroke: CK, sw: 1.3, rx: 6 }));
    // 세 구역
    const parts = [
        { w: 200, col: C2, name: '불만족', sub: '참이 되는 배정이 하나도 없다', ex: 'P ∧ ¬P' },
        { w: 300, col: CK, name: '만족가능하지만 타당하지 않다', sub: '참이 되는 배정도, 거짓이 되는 배정도 있다', ex: 'P ∨ Q' },
        { w: 200, col: C3, name: '타당', sub: '어떤 배정에서도 참', ex: 'P ∨ ¬P' },
    ];
    let cx = bx;
    for (const p of parts) {
        g.push(box(cx, by, p.w, bh, { fill: p.col, op: 0.15, stroke: p.col, sw: 1.6, rx: 6 }));
        g.push(txt(cx + p.w / 2, by + 30, p.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx + p.w / 2, by + 54, p.sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(cx + p.w / 2, by + 82, p.ex, { anchor: 'middle', cls: 'ink bold' }));
        cx += p.w;
    }
    g.push(txt(bx, by - 8, '변수에 참·거짓을 배정하는 모든 방법을 놓고 본 식의 분류', { cls: 'ink2', size: 'sm' }));

    // 부정하면 좌우가 뒤집힌다
    g.push(curvePath(`M${bx + 100} ${by + bh + 16} C ${bx + 100} ${by + bh + 60} ${bx + 600} ${by + bh + 60} ${bx + 600} ${by + bh + 16}`, { stroke: C1, sw: 1.8, marker: 'ar1' })
        .replace('<path ', '<path marker-start="url(#ar1)" '));
    g.push(txt(bx + 350, by + bh + 76, '¬ 를 붙이면 양 끝이 서로 바뀐다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(ln([[24, 254], [W - 24, 254]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 278, 'P 가 타당하다 ⟺ ¬P 가 불만족이다. P 가 만족가능하다 ⟺ ¬P 가 타당하지 않다', { cls: 'ink', size: 'sm' }));
    g.push(txt(24, 300, '두 식 F, G 가 동치라는 것은 (F ↔ G) 가 타당하다는 것이다. 동치는 타당성의 특수한 경우다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-valid-satisfiable',
        svg: svg({
            width: W, height: H,
            title: '타당·만족가능·불만족의 세 무리',
            desc: '식 전체를 불만족, 만족가능하지만 타당하지 않음, 타당의 세 구역으로 나누고 부정이 양 끝을 뒤바꾸는 것을 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-4. 진리표에서 DNF 와 CNF 를 읽는다 ---- */
add((() => {
    const W = 780, H = 392;
    const g = [];
    g.push(txt(W / 2, 26, '참인 줄에서 DNF, 거짓인 줄에서 CNF 를 읽는다', { anchor: 'middle', cls: 'ink bold' }));

    const rows = [
        ['T', 'T', 'T', 'T'], ['T', 'T', 'F', 'T'], ['T', 'F', 'T', 'T'], ['T', 'F', 'F', 'F'],
        ['F', 'T', 'T', 'F'], ['F', 'T', 'F', 'F'], ['F', 'F', 'T', 'F'], ['F', 'F', 'F', 'F'],
    ];
    g.push(txt(24, 48, 'A ∧ (B ∨ C) 의 진리표. 마지막 열이 그 값이다', { cls: 'ink2', size: 'sm' }));
    g.push(truthTable(24, 56, ['A', 'B', 'C', '값'], rows, { cw: 62, rh: 27, hlCol: [3], hlColor: C1 }));

    const rowY = r => 56 + 27 * (r + 2) - 13;
    // 참인 줄 → AND 절
    const dnf = [[0, 'A ∧ B ∧ C'], [1, 'A ∧ B ∧ ¬C'], [2, 'A ∧ ¬B ∧ C']];
    for (const [r, c] of dnf) {
        g.push(arw(272, rowY(r), 308, rowY(r), { cls: 's3', width: 1.5 }));
        g.push(txt(316, rowY(r) + 4, c, { cls: 'ink', size: 'sm' }));
    }
    // 거짓인 줄 → OR 절
    const cnf = [[3, '¬A ∨ B ∨ C'], [4, 'A ∨ ¬B ∨ ¬C']];
    for (const [r, c] of cnf) {
        g.push(arw(272, rowY(r), 308, rowY(r), { cls: 's2', width: 1.5 }));
        g.push(txt(316, rowY(r) + 4, c, { cls: 'ink', size: 'sm' }));
    }

    g.push(txt(462, rowY(0) + 4, '참인 줄마다 AND 절 하나', { cls: 'bold sm', size: 'sm' })
        .replace('class="ink sm"', `class="sm bold" fill="${C3}"`));
    g.push(txt(462, rowY(1) + 4, 'DNF = 이 셋을 OR 로 묶은 것', { cls: 'ink', size: 'sm' }));
    g.push(txt(462, rowY(3) + 4, '거짓인 줄마다 OR 절 하나', { cls: 'bold sm', size: 'sm' })
        .replace('class="ink sm"', `class="sm bold" fill="${C2}"`));
    g.push(txt(462, rowY(4) + 4, '리터럴의 부호를 모두 뒤집는다', { cls: 'ink', size: 'sm' }));
    g.push(txt(462, rowY(5) + 4, '남은 세 줄에서도 같은 방식으로 얻는다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(462, rowY(6) + 4, 'CNF = 그 다섯 절을 AND 로 묶은 것', { cls: 'ink', size: 'sm' }));

    g.push(ln([[24, 324], [W - 24, 324]], { stroke: CG, sw: 1 }));
    g.push(txt(24, 350, 'DNF 는 ‘참이 되는 경우를 다 늘어놓은 것’ 이고 CNF 는 ‘거짓이 되는 경우를 다 막은 것’ 이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 372, '이 방법이 언제나 통하므로 모든 논리식은 DNF 로도, CNF 로도 쓸 수 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-dnf-cnf',
        svg: svg({
            width: W, height: H,
            title: '진리표에서 DNF 와 CNF 를 직접 읽어내는 법',
            desc: 'A 그리고 (B 또는 C) 의 여덟 줄 진리표에서, 참인 세 줄이 AND 절이 되고 거짓인 줄이 부호를 뒤집은 OR 절이 되는 것을 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-5. 식을 줄이면 게이트가 줄어든다 ---- */
add((() => {
    const W = 780, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '같은 값을 내면서 게이트 셋이 하나가 된다', { anchor: 'middle', cls: 'ink bold' }));

    /** 게이트 하나. 이름을 안에 적는다. */
    const gate = (x, y, name, col = CK) => box(x, y, 58, 34, { stroke: col, sw: 1.5, rx: 6 })
        + txt(x + 29, y + 22, name, { anchor: 'middle', cls: 'ink bold', size: 'sm' });

    // 위: A OR (NOT A AND B)
    g.push(panel(16, 46, 740, 138, 'A ∨ (¬A ∧ B) — 게이트 세 개'));
    g.push(txt(40, 124, 'A', { cls: 'ink bold' }));
    g.push(txt(40, 166, 'B', { cls: 'ink bold' }));
    g.push(ln([[54, 119], [90, 119]], { stroke: CK, sw: 1.4 }));      // A → 분기점
    g.push(pdot(70, 119, CK, 3));
    g.push(ln([[70, 119], [70, 84], [292, 84], [292, 104]], { stroke: CK, sw: 1.4 })); // A → OR
    g.push(gate(90, 102, 'NOT', C2));
    g.push(ln([[148, 119], [190, 128]], { stroke: CK, sw: 1.4 }));    // NOT → AND
    g.push(ln([[54, 161], [190, 145]], { stroke: CK, sw: 1.4 }));     // B → AND
    g.push(gate(190, 120, 'AND', C2));
    g.push(ln([[248, 137], [292, 126]], { stroke: CK, sw: 1.4 }));    // AND → OR
    g.push(gate(292, 104, 'OR', C2));
    g.push(arw(350, 121, 400, 121, { cls: 'ark', width: 1.4 }));
    g.push(txt(408, 126, '출력', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 116, '게이트 3 개, 배선 6 개', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 140, '칩 면적·전력·불량률이 모두 여기에 붙는다', { cls: 'ink2', size: 'sm' }));

    // 아래: A OR B
    g.push(panel(16, 196, 740, 88, 'A ∨ B — 게이트 한 개. 진리표가 같다'));
    g.push(txt(40, 240, 'A', { cls: 'ink bold' }));
    g.push(txt(40, 268, 'B', { cls: 'ink bold' }));
    g.push(ln([[54, 235], [190, 240]], { stroke: CK, sw: 1.4 }));
    g.push(ln([[54, 263], [190, 256]], { stroke: CK, sw: 1.4 }));
    g.push(gate(190, 231, 'OR', C3));
    g.push(arw(248, 248, 400, 248, { cls: 'ark', width: 1.4 }));
    g.push(txt(408, 253, '출력', { cls: 'ink2', size: 'sm' }));
    g.push(txt(474, 253, '게이트 1 개, 배선 3 개', { cls: 'ink2', size: 'sm' }));

    g.push(txt(20, 316, '식을 줄이는 일이 곧 회로를 줄이는 일이다. 다만 ‘가장 짧은 동치식’ 을 찾는 문제 자체가 어렵다는 것이 이 장 뒷부분의 SAT 이야기다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-gate-count',
        svg: svg({
            width: W, height: H,
            title: '논리식을 줄이면 회로가 줄어든다',
            desc: 'A 또는 (A 가 아니고 B) 를 게이트 세 개로 만든 회로와, 동치인 A 또는 B 를 게이트 한 개로 만든 회로를 나란히 놓은 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 5-6. 한정사 순서가 뜻을 바꾼다 ---- */
add((() => {
    const W = 780, H = 358;
    const g = [];
    g.push(txt(W / 2, 26, '∀ 와 ∃ 의 순서를 바꾸면 다른 주장이 된다', { anchor: 'middle', cls: 'ink bold' }));

    const drawPanel = (x0, title, formula, mode, note1, note2) => {
        const out = [];
        const pw = 366, py = 46, ph = 244;
        out.push(panel(x0, py, pw, ph, title, formula));
        const ax = x0 + 66, bx = x0 + 266;
        const people = ['a₁', 'a₂', 'a₃'];
        const dreams = mode === 'one' ? ['d'] : ['d₁', 'd₂', 'd₃'];
        const py0 = py + 82;
        people.forEach((p, i) => {
            const y = py0 + i * 42;
            out.push(pdot(ax, y, C1, 5));
            out.push(txt(ax - 12, y + 5, p, { anchor: 'end', cls: 'ink', size: 'sm' }));
        });
        dreams.forEach((d, i) => {
            const y = mode === 'one' ? py0 + 42 : py0 + i * 42;
            out.push(pdot(bx, y, C2, 5));
            out.push(txt(bx + 12, y + 5, d, { cls: 'ink', size: 'sm' }));
        });
        people.forEach((p, i) => {
            const y1 = py0 + i * 42;
            const y2 = mode === 'one' ? py0 + 42 : y1;
            out.push(arw(ax + 8, y1, bx - 8, y2, { cls: 's1', width: 1.4 }));
        });
        out.push(txt(x0 + 20, py + 214, note1, { cls: 'ink2', size: 'sm' }));
        out.push(txt(x0 + 20, py + 234, note2, { cls: 'ink2', size: 'sm' }));
        return out.join('');
    };

    g.push(drawPanel(16, '먼저 꿈 하나를 고른다', '∃d ∀a. H(a, d)',
        'one', '모두가 같은 꿈을 공유한다.', '훨씬 강한 주장이다'));
    g.push(drawPanel(398, '먼저 사람을 고른다', '∀a ∃d. H(a, d)',
        'each', '사람마다 제 꿈이 있으면 된다.', '꿈이 서로 달라도 참이다'));

    g.push(txt(20, 314, '왼쪽이 참이면 오른쪽도 참이다. 반대는 성립하지 않는다 — ∃x∀y. P(x,y) → ∀y∃x. P(x,y) 는 타당하지만 역은 타당하지 않다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 340, '골드바흐 추측에서 순서를 바꾸면 ‘같은 두 소수의 합으로 모든 짝수가 된다’ 는 노골적인 거짓이 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-quantifier-order',
        svg: svg({
            width: W, height: H,
            title: '한정사 순서가 뜻을 바꾼다',
            desc: '왼쪽은 모든 사람이 하나의 대상을 가리키는 그림, 오른쪽은 사람마다 다른 대상을 가리키는 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6장 — 수학적 자료형
 * ================================================================== */

/* ---- 6-1. 집합 연산은 논리 연산의 번역이다 ---- */
add((() => {
    const W = 780, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '집합 등식의 증명은 논리 동치를 한 번 쓰는 일이다', { anchor: 'middle', cls: 'ink bold' }));

    /** 세 원 벤 다이어그램. 칠할 영역을 clipPath 로 만든다. */
    const venn = (cx, cy, r, idPrefix, shade) => {
        const A = [cx - r * 0.52, cy - r * 0.3];
        const B = [cx + r * 0.52, cy - r * 0.3];
        const C = [cx, cy + r * 0.62];
        const out = [];
        out.push(`<defs>
<clipPath id="${idPrefix}A"><circle cx="${r2(A[0])}" cy="${r2(A[1])}" r="${r}"/></clipPath>
<clipPath id="${idPrefix}B"><circle cx="${r2(B[0])}" cy="${r2(B[1])}" r="${r}"/></clipPath>
<clipPath id="${idPrefix}C"><circle cx="${r2(C[0])}" cy="${r2(C[1])}" r="${r}"/></clipPath>
</defs>`);
        // A ∩ B : A 안에서 B 를 클립
        if (shade.includes('AB')) {
            out.push(`<g clip-path="url(#${idPrefix}A)"><g clip-path="url(#${idPrefix}B)"><circle cx="${r2(A[0])}" cy="${r2(A[1])}" r="${r}" fill="${C1}" fill-opacity="0.4"/></g></g>`);
        }
        if (shade.includes('AC')) {
            out.push(`<g clip-path="url(#${idPrefix}A)"><g clip-path="url(#${idPrefix}C)"><circle cx="${r2(A[0])}" cy="${r2(A[1])}" r="${r}" fill="${C1}" fill-opacity="0.4"/></g></g>`);
        }
        for (const [p, nm] of [[A, 'A'], [B, 'B'], [C, 'C']]) {
            out.push(`<circle cx="${r2(p[0])}" cy="${r2(p[1])}" r="${r}" fill="none" stroke="${CK}" stroke-width="1.4"/>`);
        }
        out.push(txt(A[0] - r * 0.72, A[1] - r * 0.66, 'A', { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(B[0] + r * 0.72, B[1] - r * 0.66, 'B', { anchor: 'middle', cls: 'ink bold' }));
        out.push(txt(C[0], C[1] + r * 1.16, 'C', { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };

    g.push(panel(16, 46, 200, 202, 'A ∩ (B ∪ C)'));
    g.push(venn(116, 154, 46, 'v1', ['AB', 'AC']));
    g.push(panel(240, 46, 200, 202, '(A ∩ B) ∪ (A ∩ C)'));
    g.push(venn(340, 154, 46, 'v2', ['AB', 'AC']));
    g.push(txt(228, 154, '=', { anchor: 'middle', cls: 'ink bold' }));

    g.push(panel(456, 46, 308, 202, '무엇이 실제로 증명하는가'));
    const chain = [
        'z ∈ A ∩ (B ∪ C)',
        '⟺ z∈A ∧ (z∈B ∨ z∈C)',
        '⟺ (z∈A ∧ z∈B) ∨ (z∈A ∧ z∈C)',
        '⟺ z ∈ (A∩B) ∪ (A∩C)',
    ];
    chain.forEach((c, i) => {
        g.push(txt(472, 92 + i * 28, c, { cls: i === 2 ? 'ink bold' : 'ink', size: 'sm' }));
    });
    g.push(txt(472, 212, '굵은 줄에서 쓴 것은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(472, 232, '논리의 분배법칙 하나뿐이다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 266], [W - 20, 266]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 288, '벤 다이어그램은 확인이지 증명이 아니다. 원 세 개로 모든 배치를 다 그릴 수 있다는 보장이 없기 때문이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 310, '반면 오른쪽 사슬은 임의의 원소 z 하나를 잡고 동치로만 이었으므로 증명이다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 332, '집합 연산에 ∧ 를 쓰거나 논리식에 ∪ 를 쓰면 형이 맞지 않는다. 대응하는 것과 같은 것은 다르다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-set-logic',
        svg: svg({
            width: W, height: H,
            title: '집합의 분배법칙과 논리의 분배법칙',
            desc: '두 벤 다이어그램이 같은 영역을 칠하는 것과, 원소 하나를 잡고 동치 사슬로 이어 증명하는 네 줄',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-2. 화살표 다섯 성질 ---- */
add((() => {
    const W = 780, H = 442;
    const g = [];
    g.push(txt(W / 2, 26, '관계의 성질은 화살표를 세는 일이다', { anchor: 'middle', cls: 'ink bold' }));

    /**
     * 관계 도식 하나. left/right 는 점 개수, arrows 는 [왼쪽 번호, 오른쪽 번호].
     * bad 에 든 화살표는 위반을 강조한다.
     */
    const diagram = (x0, y0, left, right, arrows, { badLeft = [], badRight = [], w = 128 } = {}) => {
        const out = [];
        const ax = x0 + 22, bx = x0 + w - 22;
        const ys = k => y0 + 18 + k * 30;
        for (let i = 0; i < left; i += 1) {
            out.push(pdot(ax, ys(i), badLeft.includes(i) ? C2 : C1, 4.5));
        }
        for (let j = 0; j < right; j += 1) {
            out.push(pdot(bx, ys(j), badRight.includes(j) ? C2 : C3, 4.5));
        }
        for (const [i, j] of arrows) {
            out.push(arw(ax + 7, ys(i), bx - 7, ys(j), { cls: 'ark', width: 1.4 }));
        }
        out.push(txt(ax, y0 + 4, 'A', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(bx, y0 + 4, 'B', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };

    const specs = [
        {
            name: '함수', mark: '나가는 화살표 ≤ 1',
            good: { left: 3, right: 3, arrows: [[0, 0], [1, 1], [2, 1]] },
            bad: { left: 3, right: 3, arrows: [[0, 0], [0, 1], [2, 2]], badLeft: [0] },
            badWhy: ['한 점에서', '둘이 나갔다'],
        },
        {
            name: '전(total)', mark: '나가는 화살표 ≥ 1',
            good: { left: 3, right: 3, arrows: [[0, 0], [1, 1], [2, 1]] },
            bad: { left: 3, right: 3, arrows: [[0, 0], [2, 2]], badLeft: [1] },
            badWhy: ['아무 데도', '안 가는 점이 있다'],
        },
        {
            name: '전사', mark: '들어오는 화살표 ≥ 1',
            good: { left: 3, right: 2, arrows: [[0, 0], [1, 1], [2, 1]] },
            bad: { left: 3, right: 3, arrows: [[0, 0], [1, 1], [2, 1]], badRight: [2] },
            badWhy: ['아무도 안 가리키는', '점이 있다'],
        },
        {
            name: '단사', mark: '들어오는 화살표 ≤ 1',
            good: { left: 2, right: 3, arrows: [[0, 0], [1, 2]] },
            bad: { left: 3, right: 3, arrows: [[0, 1], [1, 1], [2, 2]], badRight: [1] },
            badWhy: ['한 점으로', '둘이 들어왔다'],
        },
        {
            name: '전단사', mark: '양쪽 모두 정확히 1',
            good: { left: 3, right: 3, arrows: [[0, 0], [1, 1], [2, 2]] },
            bad: { left: 3, right: 3, arrows: [[0, 0], [1, 1], [2, 1]], badRight: [1, 2] },
            badWhy: ['한쪽은 겹치고', '한쪽은 비었다'],
        },
    ];

    specs.forEach((s, i) => {
        const x0 = 12 + i * 152;
        g.push(panel(x0, 44, 144, 344, s.name, s.mark));
        g.push(txt(x0 + 72, 100, '이렇다', { anchor: 'middle', cls: 'bold sm', size: 'sm' })
            .replace('class="ink sm"', `class="sm bold" fill="${C3}"`));
        g.push(diagram(x0 + 8, 108, s.good.left, s.good.right, s.good.arrows, { w: 128 }));
        g.push(ln([[x0 + 14, 220], [x0 + 130, 220]], { stroke: CG, sw: 1, dash: '4 4' }));
        g.push(txt(x0 + 72, 242, '이러면 아니다', { anchor: 'middle', cls: 'bold sm', size: 'sm' })
            .replace('class="ink sm"', `class="sm bold" fill="${C2}"`));
        g.push(diagram(x0 + 8, 250, s.bad.left, s.bad.right, s.bad.arrows, { badLeft: s.bad.badLeft ?? [], badRight: s.bad.badRight ?? [], w: 128 }));
        s.badWhy.forEach((t, k) => {
            g.push(txt(x0 + 72, 350 + k * 18, t, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
    });

    g.push(txt(12, 424, '화살표만으로는 ‘전’ 인지 알 수 없다. 화살표가 하나도 없는 정의역 점을 화살표 그림에서는 볼 수 없기 때문이다 — 정의역이 무엇인지 함께 밝혀야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-relation-arrows',
        svg: svg({
            width: W, height: H,
            title: '이항관계의 다섯 가지 성질을 화살표로',
            desc: '함수, 전, 전사, 단사, 전단사 각각에 대해 성질을 만족하는 그림과 만족하지 않는 그림을 나란히 놓았다',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-3. 관계의 상과 역상 ---- */
add((() => {
    const W = 780, H = 378;
    const g = [];
    g.push(txt(W / 2, 26, '상은 화살표를 따라가고, 역상은 거꾸로 온다', { anchor: 'middle', cls: 'ink bold' }));

    const ax = 190, bx = 560, y0 = 94, dy = 44;
    const A = ['갑', '을', '병', '정', '무'];
    const B = ['수학', '논리', '알고리즘', '통계'];
    const arrows = [[0, 0], [0, 1], [1, 1], [2, 2], [2, 3], [4, 3]];

    // 상 R(Y) : Y = {갑, 을}
    const Y = [0, 1];
    const RY = [0, 1];
    g.push(box(ax - 64, y0 - 22, 128, dy * 2 + 8, { fill: C1, op: 0.14, stroke: C1, sw: 1.6, rx: 6 }));
    g.push(txt(ax - 74, y0 - 6, 'Y', { anchor: 'end', cls: 'bold' })
        .replace('class="ink"', `class="bold" fill="${C1}"`));
    g.push(box(bx - 64, y0 - 22, 132, dy * 2 + 8, { fill: C3, op: 0.14, stroke: C3, sw: 1.6, rx: 6 }));
    g.push(txt(bx + 78, y0 - 6, 'R(Y)', { cls: 'bold' })
        .replace('class="ink"', `class="bold" fill="${C3}"`));

    A.forEach((a, i) => {
        const y = y0 + i * dy;
        g.push(pdot(ax, y, Y.includes(i) ? C1 : CK, 5));
        g.push(txt(ax - 14, y + 5, a, { anchor: 'end', cls: 'ink', size: 'sm' }));
    });
    B.forEach((b, j) => {
        const y = y0 + j * dy;
        g.push(pdot(bx, y, RY.includes(j) ? C3 : CK, 5));
        g.push(txt(bx + 14, y + 5, b, { cls: 'ink', size: 'sm' }));
    });
    for (const [i, j] of arrows) {
        const hot = Y.includes(i);
        g.push(arw(ax + 9, y0 + i * dy, bx - 9, y0 + j * dy, { cls: hot ? 's1' : 'grid', width: hot ? 1.8 : 1.3 }));
    }
    g.push(txt(ax, y0 - 44, '담당자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx, y0 - 44, '과목', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(375, y0 - 44, '‘담당한다’ 관계', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 296], [W - 20, 296]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 320, 'R(Y) = 갑과 을이 담당한 과목 전체 = { 수학, 논리 }. 역상 R⁻¹({ 통계 }) = 통계를 담당한 사람 전체 = { 병, 무 }', { cls: 'ink', size: 'sm' }));
    g.push(txt(20, 342, '치역 range(R) 은 R(A) 다 — 위 그림에서 화살표가 하나라도 들어오는 과목들. 알고리즘도 여기 든다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 364, 'support(R) = R⁻¹(B) 는 화살표가 하나라도 나가는 담당자들이다. ‘정’ 은 여기서 빠진다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-relation-image',
        svg: svg({
            width: W, height: H,
            title: '관계의 상과 역상',
            desc: '담당자와 과목 사이의 관계 도식에서 부분집합 Y 의 상과, 어떤 과목의 역상을 표시한 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-4. 전함수와 부분함수 ---- */
add((() => {
    const W = 780, H = 352;
    const g = [];
    g.push(txt(W / 2, 26, '정의역을 바꾸면 같은 식이 전함수가 되기도 한다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 1/x² 을 R 에서
    const pw = 364;
    g.push(panel(16, 46, pw, 208, 'f(x) = 1 / x² 을 실수 전체에서', 'x = 0 에 값이 없다 — 부분함수'));
    const xR = [-3, 3], yR = [0, 3.4];
    const f1 = frame({ xRange: xR, yRange: yR, box: { x: 60, y: 100, w: 280, h: 98 } });
    g.push(axes2(f1, { xRange: xR, yRange: yR, xTicks: [-2, 2], yTicks: [1, 3], xLabel: 'x' }));
    g.push(fcurve(f1, v => Math.min(1 / (v * v), 3.4), { from: -3, to: -0.545, stroke: C1, sw: 2.2 }));
    g.push(fcurve(f1, v => Math.min(1 / (v * v), 3.4), { from: 0.545, to: 3, stroke: C1, sw: 2.2 }));
    g.push(`<circle cx="${f1.X(0)}" cy="${f1.Y(0)}" r="6.5" fill="none" stroke="${C2}" stroke-width="2.4"/>`);
    g.push(txt(34, 240, '가운데 뚫린 동그라미가 값이 없는 자리다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽: 문자열에서 1 을 찾는 함수
    g.push(panel(400, 46, pw, 208, '왼쪽부터 1 을 찾는 함수', '몇 번째 자리에서 처음 1 이 나오는가'));
    const rows = [['0010', '3'], ['100', '1'], ['0000', '없다'], ['1', '1']];
    rows.forEach((r, i) => {
        const y = 112 + i * 32;
        const bad = r[1] === '없다';
        g.push(box(424, y - 18, 90, 26, { stroke: bad ? C2 : CK, sw: bad ? 1.7 : 1.1, rx: 4 }));
        g.push(txt(469, y, r[0], { anchor: 'middle', cls: 'ink', size: 'sm' }));
        g.push(arw(520, y - 5, 556, y - 5, { cls: bad ? 's2' : 'ark', width: 1.4 }));
        g.push(txt(566, y, r[1], { cls: bad ? 'bold' : 'ink', size: 'sm' })
            .replace('class="bold sm"', `class="sm bold" fill="${C2}"`));
        if (bad) g.push(txt(608, y, '1 이 없으니 셀 것이 없다', { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(414, 240, '0 만으로 된 문자열에서는 값이 정해지지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 268], [W - 20, 268]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 292, '값이 정해진 정의역 원소들의 모임을 support 라 하고, support 가 정의역과 같으면 전함수(total function) 다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 314, '공역을 바꿔서는 부분함수가 전함수가 되지 않는다. 정의역을 support 까지 줄여야 한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 340, '이 문서에서 그냥 ‘함수’ 라고 쓰면 부분함수일 수도 있다. 전함수라고 말해야 모든 원소에 값이 있다는 뜻이 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-total-partial',
        svg: svg({
            width: W, height: H,
            title: '부분함수와 전함수',
            desc: '1 나누기 x 제곱이 0 에서 값을 갖지 않는 것과, 이진 문자열에서 1 을 찾는 함수가 0 만으로 된 문자열에서 값을 갖지 않는 것',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-5. 부분집합과 비트열의 전단사 ---- */
add((() => {
    const W = 780, H = 306;
    const g = [];
    g.push(txt(W / 2, 26, '부분집합 하나가 비트열 하나다', { anchor: 'middle', cls: 'ink bold' }));

    const x0 = 60, cw = 62, y0 = 88, ch = 34;
    const names = ['a₁', 'a₂', 'a₃', 'a₄', 'a₅', 'a₆', 'a₇', 'a₈', 'a₉', 'a₁₀'];
    const inSet = [0, 1, 1, 0, 1, 0, 1, 0, 0, 1];
    g.push(txt(x0 - 14, y0 + 22, '원소', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(cells(x0, y0, cw, ch, names, { hl: Object.fromEntries(inSet.map((b, i) => [i, b ? C1 : undefined]).filter(e => e[1])) }));
    g.push(txt(x0 - 14, y0 + ch + 44, '비트', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(cells(x0, y0 + ch + 22, cw, ch, inSet.map(String), { hl: Object.fromEntries(inSet.map((b, i) => [i, b ? C1 : undefined]).filter(e => e[1])) }));
    for (let i = 0; i < 10; i += 1) {
        g.push(arw(x0 + i * cw + cw / 2, y0 + ch + 2, x0 + i * cw + cw / 2, y0 + ch + 18, { cls: inSet[i] ? 's1' : 'grid', width: 1.3 }));
    }
    g.push(txt(x0, y0 - 14, 'S = { a₂, a₃, a₅, a₇, a₁₀ } 을 비트열로 옮긴 것', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0, y0 + ch + 22 + ch + 26, '0 1 1 0 1 0 1 0 0 1 — i 번째 비트가 1 인 것이 aᵢ ∈ S 라는 뜻이다', { cls: 'ink', size: 'sm' }));

    g.push(ln([[20, 226], [W - 20, 226]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 250, '이 대응은 전단사다. 비트열에서 부분집합을 되돌릴 수 있고, 서로 다른 부분집합은 서로 다른 비트열이 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 272, '길이 n 인 비트열은 2ⁿ 개다. 전단사가 있으면 크기가 같으므로 원소 n 개인 집합의 부분집합은 2ⁿ 개다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 298, '세지 않고 짝을 지어 세는 것 — 이 수법이 10장에서 무한집합의 크기를 재는 유일한 방법이 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-subset-bits',
        svg: svg({
            width: W, height: H,
            title: '부분집합과 비트열 사이의 전단사',
            desc: '원소 열 개 중 다섯 개를 고른 부분집합을 길이 10 의 비트열로 옮긴 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 6-6. 비둘기집 원리 ---- */
add((() => {
    const W = 780, H = 322;
    const g = [];
    g.push(txt(W / 2, 26, '넣을 것이 칸보다 많으면 어느 칸에는 둘이 들어간다', { anchor: 'middle', cls: 'ink bold' }));

    const bw = 96, bh = 62, by = 116, bx0 = 60;
    const holes = 4;
    for (let j = 0; j < holes; j += 1) {
        g.push(box(bx0 + j * (bw + 18), by, bw, bh, { stroke: CK, sw: 1.4, rx: 6 }));
    }
    // 다섯 개를 네 칸에 — 두 번째 칸에 둘
    const place = [[0, 0], [1, 0], [1, 1], [2, 0], [3, 0]];
    const items = ['b₁', 'b₂', 'b₃', 'b₄', 'b₅'];
    place.forEach((p, i) => {
        const cx = bx0 + p[0] * (bw + 18) + (p[1] === 0 && place.filter(q => q[0] === p[0]).length > 1 ? 28 : bw / 2);
        const cy = by + bh / 2;
        const shift = place.filter(q => q[0] === p[0]).length > 1 ? (p[1] === 0 ? -22 : 22) : 0;
        g.push(pdot(bx0 + p[0] * (bw + 18) + bw / 2 + shift, cy, shift === 0 ? C1 : C2, 12));
        g.push(txt(bx0 + p[0] * (bw + 18) + bw / 2 + shift, cy + 5, items[i], { anchor: 'middle', cls: 'sm' })
            .replace('class="sm"', 'class="sm" fill="#ffffff"'));
    });
    g.push(box(bx0 + 1 * (bw + 18) - 4, by - 6, bw + 8, bh + 12, { stroke: C2, sw: 2.2, rx: 8 }));
    g.push(txt(bx0 + 1 * (bw + 18) + bw / 2, by - 14, '여기에 둘', { anchor: 'middle', cls: 'bold sm', size: 'sm' })
        .replace('class="ink sm"', `class="sm bold" fill="${C2}"`));
    g.push(txt(bx0, by + bh + 26, '넣을 것 5 개 → 칸 4 개', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[20, 214], [W - 20, 214]], { stroke: CG, sw: 1 }));
    g.push(txt(20, 238, '사상 규칙으로 옮기면 이렇다 — A 에서 B 로 가는 전함수가 단사이면 |A| ≤ |B| 다.', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(20, 260, '이 함의의 대우가 비둘기집 원리다. |A| > |B| 이면 A 에서 B 로 가는 어떤 전함수도 단사가 아니다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 282, '즉 값이 같아지는 서로 다른 두 원소가 반드시 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 308, '증명은 화살표를 세는 것이 전부다. 전함수라 화살표가 |A| 개 이상이고, 단사라면 화살표가 |B| 개 이하다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-p-pigeonhole',
        svg: svg({
            width: W, height: H,
            title: '비둘기집 원리는 사상 규칙의 대우다',
            desc: '다섯 개를 네 칸에 넣으면 어느 칸에 둘이 들어가는 그림과, 그것이 단사 조건의 대우라는 설명',
            body: g.join(''),
        }),
    };
})());

export default figures;
