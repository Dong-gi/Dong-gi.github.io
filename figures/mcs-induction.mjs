/**
 * mcs 7장(귀납법) · 8장(상태 기계) · 9장(재귀적 자료형) · 10장(무한집합)의 그림.
 *
 * 이름은 모두 `mcs-i-` 로 시작한다(담당 B 에게 배정된 접두어).
 * build.mjs 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 첨자는 lib 의 `n~0` 표기를, 나머지는 유니코드(≤ ≥ ≠ → ↦ ∈ ∉ ⊆ ∅ √ ² ³ ₀ ₁ · × ℕ ℤ ℚ ℝ)로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 그냥 쓰면 안 되고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 블록의 주제는 ‘무한히 많은 것을 유한한 종이에 증명하는 법’ 이다. 그래서 그림도
 * 대개 좌표 곡선이 아니라 사슬·격자·표 — 어디서 정보가 흘러 들어오는가, 어느 칸이
 * 빠졌는가 — 을 보인다. 마지막 장의 대각선 그림 둘이 이 블록의 결론이다.
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
 * 화소 좌표 소도구. lib 의 px() 는 색을 CSS 클래스로 넘기는데 SVG 안에
 * ar1/ark 클래스가 없어 선이 사라지고 화살촉만 남는다. 색을 직접 넣는다.
 * (figures/mcs-proof.mjs 의 같은 헬퍼를 본떴다.)
 * ------------------------------------------------------------------ */

const COL = { s1: C1, s2: C2, s3: C3, ark: CK, ink: CI, grid: CG };

function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 1.8, dash } = {}) {
    const col = COL[cls] ?? CK;
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 곡선 경로(베지어). 화살촉을 붙일 수 있다. */
function cv(d, { stroke = CK, sw = 1.5, dash, marker } = {}) {
    return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}${marker ? ` marker-end="url(#${marker})"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const dot = (x, y, col = C1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const ring = (x, y, r, col = C2, sw = 2) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r)}" fill="none" stroke="${col}" stroke-width="${sw}"/>`;

/** 엑스 표. 도달할 수 없는 자리를 표시한다. */
function ex(x, y, r = 6, col = C2, sw = 2.2) {
    return ln([[x - r, y - r], [x + r, y + r]], { stroke: col, sw })
        + ln([[x - r, y + r], [x + r, y - r]], { stroke: col, sw });
}

/** 패널 테두리와 제목. 제목은 테두리 안쪽 위에 둔다. */
function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: CG, sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }) : '')
        + (sub ? txt(x + w / 2, y + 36, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 칸 한 줄. items 의 값이 null 이면 빈 칸. hl 은 칸 번호 → 색. */
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

/** 색을 직접 지정하는 글자. txt() 의 class 를 fill 로 갈아 끼운다. */
function ctxt(x, y, s, col, { anchor = 'start', size = 'sm', bold = false } = {}) {
    const t = txt(x, y, s, { anchor, cls: 'ink', size });
    return t.replace(/class="ink( sm)?"/, `class="${size === 'sm' ? 'sm' : ''}${bold ? ' bold' : ''}" fill="${col}"`);
}

/** 줄 간격이 일정한 글 묶음. */
function lines(x, y, arr, { lh = 18, size = 'sm', cls = 'ink2' } = {}) {
    return arr.map((s, i) => txt(x, y + i * lh, s, { cls, size })).join('');
}

/** 격자. 칸 크기 c, 좌상단 (x, y), 가로 nx 칸 세로 ny 칸. */
function grid(x, y, c, nx, ny, { stroke = CG, sw = 0.9 } = {}) {
    const g = [];
    for (let i = 0; i <= nx; i += 1) g.push(ln([[x + i * c, y], [x + i * c, y + ny * c]], { stroke, sw }));
    for (let j = 0; j <= ny; j += 1) g.push(ln([[x, y + j * c], [x + nx * c, y + j * c]], { stroke, sw }));
    return g.join('');
}

/* ================================================================== *
 * 7장 — 귀납법
 * ================================================================== */

/* ---- 7-1. 귀납법이 왜 통하는가 — 사슬과 끊긴 사슬 ---- */
add((() => {
    const W = 780, H = 344;
    const g = [];
    g.push(txt(W / 2, 26, '귀납법은 첫 칸 하나와 다리 하나로 무한히 많은 칸을 채운다', { anchor: 'middle', cls: 'ink bold' }));

    // 위: 온전한 사슬
    const bx = 36, by = 62, bw = 74, bh = 40, gap = 100;
    g.push(txt(bx, by - 12, '온전한 귀납 증명', { cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 7; i += 1) {
        const cx = bx + i * gap;
        const last = i === 6;
        g.push(box(cx, by, bw, bh, { fill: C3, op: 0.18, stroke: C3, sw: 1.8, rx: 4 }));
        g.push(txt(cx + bw / 2, by + 26, last ? 'P(n) …' : `P(${i})`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        if (i < 6) g.push(arw(cx + bw + 4, by + bh / 2, cx + gap - 6, by + bh / 2, { cls: 's3', width: 1.8 }));
    }
    g.push(ring(bx + bw / 2, by + bh / 2, 30, C1, 2));
    g.push(ctxt(bx + bw / 2, by + bh + 34, '기저 단계', C1, { anchor: 'middle', bold: true }));
    g.push(txt(bx + 152, by + bh + 34, '귀납 단계 — 화살표 하나를 모든 n 에 대해 한꺼번에 증명한다', { cls: 'ink2', size: 'sm' }));

    // 아래: 화살표 하나가 빠진 사슬
    const cy = 202;
    g.push(txt(bx, cy - 12, '화살표 하나가 빠지면 그 뒤 전부가 무너진다 (말 색깔 가짜 증명이 이 모양이다)', { cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 7; i += 1) {
        const cx = bx + i * gap;
        const dead = i >= 2;
        const col = dead ? C2 : C3;
        g.push(box(cx, cy, bw, bh, { fill: col, op: dead ? 0.12 : 0.18, stroke: col, sw: 1.8, rx: 4, dash: dead ? '5 4' : undefined }));
        g.push(txt(cx + bw / 2, cy + 26, i === 6 ? 'P(n) …' : `P(${i})`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        if (i < 6) {
            if (i === 1) {
                g.push(ex(cx + bw + (gap - bw) / 2, cy + bh / 2, 8, C2, 2.4));
            } else {
                g.push(arw(cx + bw + 4, cy + bh / 2, cx + gap - 6, cy + bh / 2, { cls: dead ? 's2' : 's3', width: 1.8 }));
            }
        }
    }
    g.push(ctxt(bx + 150, cy + bh + 32, 'P(1) → P(2) 만 증명되지 않았다', C2, { bold: true }));
    g.push(txt(bx + 6, cy + bh + 58, '이 자리 하나가 비면 P(2) 부터는 아무 근거가 없다. 다른 화살표를 아무리 많이 증명해도 소용없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 6, cy + bh + 78, '그래서 귀납 단계의 논증이 정말 모든 n 에서 통하는지 — 특히 가장 작은 n 에서 — 확인하는 일이 증명의 일부다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-i-domino-chain',
        svg: svg({
            width: W, height: H,
            title: '귀납법의 사슬과, 화살표 하나가 빠진 사슬',
            desc: '위쪽은 P(0) 에서 시작해 화살표로 이어지는 온전한 사슬, 아래쪽은 P(1) 에서 P(2) 로 가는 화살표가 빠져 그 뒤가 모두 근거를 잃은 사슬',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-2. 코트야드 타일 덮기 — 귀납 가정을 강하게 만든다 ---- */
add((() => {
    const W = 780, H = 372;
    const g = [];
    g.push(txt(W / 2, 26, '가정을 강하게 만들면 네 사분면 모두에 쓸 수 있다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 약한 가정 — 가운데에만 놓을 수 있다
    const c = 17;
    const p1 = 20, py = 48, pw = 232, ph = 268;
    g.push(panel(p1, py, pw, ph, '약한 가정: 상은 가운데에만', 'P(n) 을 P(n+1) 에 쓸 수 없다'));
    const g1x = p1 + 48, g1y = py + 60;
    g.push(grid(g1x, g1y, c, 8, 8));
    g.push(box(g1x + 4 * c, g1y + 4 * c, c, c, { fill: C1, op: 0.85, stroke: C1, sw: 1.5, rx: 1 }));
    g.push(ln([[g1x + 4 * c, g1y], [g1x + 4 * c, g1y + 8 * c]], { stroke: C2, sw: 2 }));
    g.push(ln([[g1x, g1y + 4 * c], [g1x + 8 * c, g1y + 4 * c]], { stroke: C2, sw: 2 }));
    g.push(txt(g1x + 2 * c, g1y + 2 * c + 5, '?', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(g1x + 6 * c, g1y + 2 * c + 5, '?', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(g1x + 2 * c, g1y + 6 * c + 5, '?', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(p1 + 16, py + 214, '상이 든 사분면 말고 나머지 셋에는', { cls: 'ink2', size: 'sm' }));
    g.push(txt(p1 + 16, py + 232, '상이 없어서 가정을 쓸 자격이 없다', { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(p1 + 16, py + 254, '가정이 모자라서 막힌다', C2, { bold: true }));

    // 오른쪽: 강한 가정 — 임시 상 셋
    const p2 = 274, pw2 = 232;
    g.push(panel(p2, py, pw2, ph, '강한 가정: 상이 어디 있어도', '임시 상 셋으로 사분면을 채운다'));
    const g2x = p2 + 48, g2y = py + 60;
    g.push(grid(g2x, g2y, c, 8, 8));
    g.push(box(g2x + 6 * c, g2y + c, c, c, { fill: C1, op: 0.85, stroke: C1, sw: 1.5, rx: 1 }));
    // 임시 상 셋 — 가운데 네 칸 중 상이 든 사분면 밖의 셋
    const tmp = [[3, 3], [4, 3], [3, 4]];
    for (const [i, j] of tmp) g.push(box(g2x + i * c, g2y + j * c, c, c, { fill: C3, op: 0.8, stroke: C3, sw: 1.5, rx: 1 }));
    g.push(ln([[g2x + 4 * c, g2y], [g2x + 4 * c, g2y + 8 * c]], { stroke: C2, sw: 2 }));
    g.push(ln([[g2x, g2y + 4 * c], [g2x + 8 * c, g2y + 4 * c]], { stroke: C2, sw: 2 }));
    g.push(txt(p2 + 16, py + 214, '이 셋을 L 타일 하나로 바꾸면 끝난다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(p2 + 16, py + 232, '네 사분면 모두 가정을 쓸 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(p2 + 16, py + 254, '더 센 주장이 더 쉽게 증명된다', C3, { bold: true }));

    // 오른쪽 끝: L 타일과 범례
    const p3 = 528;
    g.push(panel(p3, py, 232, ph, 'L 타일과 임시 상', null));
    const tx = p3 + 40, ty = py + 48;
    for (const [i, j] of [[0, 0], [1, 0], [0, 1]]) {
        g.push(box(tx + i * 22, ty + j * 22, 22, 22, { fill: C3, op: 0.35, stroke: C3, sw: 1.6, rx: 2 }));
    }
    g.push(txt(tx + 78, ty + 20, '칸 세 개를 덮는 L 타일', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 78, ty + 38, '(회전은 자유롭다)', { cls: 'ink2', size: 'sm' }));
    g.push(box(tx, ty + 78, 22, 22, { fill: C1, op: 0.85, stroke: C1, sw: 1.5, rx: 2 }));
    g.push(txt(tx + 32, ty + 94, '상 — 덮지 않는 한 칸', { cls: 'ink2', size: 'sm' }));
    g.push(box(tx, ty + 112, 22, 22, { fill: C3, op: 0.8, stroke: C3, sw: 1.5, rx: 2 }));
    g.push(txt(tx + 32, ty + 128, '임시 상 — 증명을 위한 장치', { cls: 'ink2', size: 'sm' }));
    g.push(lines(p3 + 16, py + 204, [
        '증명이 알고리즘을 준다. 사분면으로 쪼개고',
        '임시 상 셋을 놓고 재귀로 내려가면',
        '실제 타일 배치가 나온다 — 귀납 증명의',
        '부수 효과 중 가장 값나가는 것이다',
    ]));
    return {
        name: 'mcs-i-tiling-quadrant',
        svg: svg({
            width: W, height: H,
            title: '코트야드 타일 덮기 — 귀납 가정을 강하게 만드는 수법',
            desc: '왼쪽은 상이 가운데에만 있다고 가정하면 세 사분면에서 막히는 그림, 가운데는 상 위치를 자유롭게 둔 강한 가정과 임시 상 셋, 오른쪽은 L 타일 범례',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-3. 말 색깔 가짜 증명이 무너지는 자리 ---- */
add((() => {
    const W = 780, H = 328;
    const g = [];
    g.push(txt(W / 2, 26, '겹치는 부분이 있다는 전제가 가장 작은 곳에서 깨진다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 360, py = 48, ph = 200;
    // 왼쪽: n = 3 (통한다)
    g.push(panel(20, py, pw, ph, 'n = 3 → 4 : 논증이 통한다', '두 무리가 말 두 마리를 공유한다'));
    const hx = 60, hy = py + 92, hs = 62;
    const names = ['h₁', 'h₂', 'h₃', 'h₄'];
    for (let i = 0; i < 4; i += 1) {
        g.push(box(hx + i * hs, hy, 48, 42, { stroke: CK, sw: 1.3, rx: 4 }));
        g.push(txt(hx + i * hs + 24, hy + 27, names[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(ln([[hx - 6, hy - 14], [hx + 3 * hs - 14, hy - 14]], { stroke: C1, sw: 2.4 }));
    g.push(ctxt(hx - 6, hy - 22, '앞 세 마리 — 같은 색', C1));
    g.push(ln([[hx + hs - 6, hy + 56], [hx + 3 * hs + 54, hy + 56]], { stroke: C3, sw: 2.4 }));
    g.push(ctxt(hx + hs - 6, hy + 72, '뒤 세 마리 — 같은 색', C3));
    g.push(box(hx + hs - 4, hy - 6, 2 * hs - 10, 54, { stroke: C2, sw: 2, rx: 4, dash: '5 4' }));
    g.push(ctxt(hx + hs + 54, hy + 100, '겹치는 두 마리가 두 색을 잇는다', C2, { anchor: 'middle', bold: true }));

    // 오른쪽: n = 1 (깨진다)
    g.push(panel(400, py, pw, ph, 'n = 1 → 2 : 논증이 깨진다', '공유하는 말이 하나도 없다'));
    const kx = 500, ky = py + 92;
    for (let i = 0; i < 2; i += 1) {
        g.push(box(kx + i * hs, ky, 48, 42, { stroke: CK, sw: 1.3, rx: 4 }));
        g.push(txt(kx + i * hs + 24, ky + 27, names[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(ln([[kx - 6, ky - 14], [kx + 42, ky - 14]], { stroke: C1, sw: 2.4 }));
    g.push(ctxt(kx - 6, ky - 22, '앞 한 마리', C1));
    g.push(ln([[kx + hs - 6, ky + 56], [kx + hs + 48, ky + 56]], { stroke: C3, sw: 2.4 }));
    g.push(ctxt(kx + hs - 6, ky + 72, '뒤 한 마리', C3));
    g.push(ex(kx + 55, ky + 21, 7, C2, 2.4));
    g.push(ctxt(kx + 30, ky + 100, '겹치는 말이 하나도 없다', C2, { anchor: 'middle', bold: true }));

    g.push(lines(24, 276, [
        '가짜 증명의 문장 ‘h₁ 은 나머지 h₂ … h_n 과 같은 색이다’ 가 범인이다. 줄임표가 말이 몇 마리 있는 것처럼 보이게 하지만 n = 1 이면 하나도 없다.',
        'P(1) 은 참이고 P(2) → P(3), P(3) → P(4) … 도 모두 참이다. 빠진 것은 P(1) → P(2) 하나뿐이고, 그 하나로 사슬 전체가 끊긴다.',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-horses-gap',
        svg: svg({
            width: W, height: H,
            title: '말 색깔 가짜 증명이 무너지는 자리',
            desc: '왼쪽은 말 네 마리에서 앞 세 마리와 뒤 세 마리가 두 마리를 공유하는 그림, 오른쪽은 말 두 마리에서 공유하는 말이 없어 논증이 끊기는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-4. 3 과 5 로 만들 수 있는 금액 ---- */
add((() => {
    const W = 780, H = 292;
    const g = [];
    g.push(txt(W / 2, 26, '8 부터는 빈틈이 없고, 세 칸씩 건너뛰어 무한히 이어진다', { anchor: 'middle', cls: 'ink bold' }));

    const x0 = 40, y0 = 66, cw = 44, ch = 40;
    const nums = [];
    for (let i = 0; i <= 15; i += 1) nums.push(i);
    const ok = new Set([0, 3, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15]);
    const hl = {};
    nums.forEach((v, i) => { hl[i] = ok.has(v) ? C3 : C2; });
    g.push(cells(x0, y0, cw, ch, nums.map(String), { hl, small: true }));
    g.push(txt(x0, y0 - 10, '금액', { cls: 'ink2', size: 'sm' }));

    // 기저 세 칸과 +3 화살표
    for (const b of [8, 9, 10]) {
        const i = nums.indexOf(b);
        g.push(ring(x0 + i * cw + cw / 2, y0 + ch / 2, 22, C1, 2));
    }
    g.push(ctxt(x0 + 9 * cw, y0 - 10, '확인해 두는 세 칸: 8 = 3+5, 9 = 3+3+3, 10 = 5+5', C1, { anchor: 'middle', bold: true }));

    const ay = y0 + ch + 4;
    for (let k = 11; k <= 15; k += 1) {
        const from = nums.indexOf(k - 3), to = nums.indexOf(k);
        const fx = x0 + from * cw + cw / 2, tx2 = x0 + to * cw + cw / 2;
        g.push(cv(`M${fx} ${ay} C${fx} ${ay + 40} ${tx2} ${ay + 40} ${tx2} ${ay + 2}`, { stroke: C3, sw: 1.6, marker: 'ar3' }));
    }
    g.push(ctxt(x0, ay + 34, '초록 = 3 과 5 로 만들 수 있다', C3, { bold: true }));
    g.push(ctxt(x0 + 210, ay + 34, '주황 = 만들 수 없다 (1, 2, 4, 7)', C2, { bold: true }));
    g.push(txt(x0 + 4, ay + 66, '동전 하나를 더 얹는 것이 귀납 단계다. 3 을 얹으면 세 칸 오른쪽으로 간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(x0 + 4, ay + 86, '그래서 8, 9, 10 세 칸을 확보하면 그 오른쪽 전부가 따라온다. 확인할 초기값의 개수는 걸음의 크기가 정한다', { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(x0 + 4, ay + 110, '한 칸(n → n+1)이 아니라 세 칸을 뛰므로 보통 귀납법이 아니라 강한 귀납법이 필요하다', C1, { bold: true }));
    return {
        name: 'mcs-i-stamps-reach',
        svg: svg({
            width: W, height: H,
            title: '3 과 5 로 만들 수 있는 금액',
            desc: '0 부터 15 까지의 칸에 만들 수 있는 금액을 초록, 만들 수 없는 1 2 4 7 을 주황으로 칠하고, 8 9 10 에서 3 을 더해 오른쪽으로 가는 화살표를 그린 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 7-5. 상자 쪼개기 게임 — 전략이 점수를 바꾸지 못한다 ---- */
add((() => {
    const W = 780, H = 340;
    const g = [];
    g.push(txt(W / 2, 26, '어떻게 쪼개도 총점이 같다 — 첫 쪼갬의 점수와 두 조각의 점수가 서로 벌충한다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽: 두 가지 첫 쪼갬
    const pw = 236, py = 48, ph = 208;
    const drawSplit = (px, a, b, label) => {
        const out = [];
        const cx = px + pw / 2;
        out.push(panel(px, py, pw, ph, label, `첫 쪼갬 점수 ${a} · ${b} = ${a * b}`));
        // 원 상자
        out.push(box(cx - 44, py + 52, 88, 26, { fill: C1, op: 0.2, stroke: C1, sw: 1.6, rx: 4 }));
        out.push(txt(cx, py + 70, `상자 ${a + b} 개`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(arw(cx - 20, py + 82, px + 56, py + 116, { cls: 'ark', width: 1.5 }));
        out.push(arw(cx + 20, py + 82, px + pw - 56, py + 116, { cls: 'ark', width: 1.5 }));
        const leaf = (lx, k) => box(lx - 30, py + 120, 60, 26, { fill: C3, op: 0.2, stroke: C3, sw: 1.6, rx: 4 })
            + txt(lx, py + 138, `${k} 개`, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
        out.push(leaf(px + 56, a));
        out.push(leaf(px + pw - 56, b));
        out.push(txt(px + 56, py + 168, `${a}(${a} − 1)/2 = ${a * (a - 1) / 2}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(px + pw - 56, py + 168, `${b}(${b} − 1)/2 = ${b * (b - 1) / 2}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const tot = a * b + a * (a - 1) / 2 + b * (b - 1) / 2;
        out.push(ctxt(cx, py + 192, `합계 ${a * b} + ${a * (a - 1) / 2} + ${b * (b - 1) / 2} = ${tot}`, C2, { anchor: 'middle', bold: true }));
        return out.join('');
    };
    g.push(drawSplit(20, 5, 5, '반씩 쪼갠다'));
    g.push(drawSplit(272, 1, 9, '한 개만 떼어 낸다'));

    // 오른쪽: 결론
    g.push(panel(524, py, 236, ph, '어느 쪽이든 45 점', 'n = 10 일 때 n(n−1)/2 = 45'));
    g.push(lines(540, py + 60, [
        '강한 귀납법이 필요한 까닭:',
        '10 개를 쪼개면 다음 판은 5 와 5 이거나',
        '1 과 9 이거나 … 무엇이 될지 모른다.',
        '한 칸 아래(9)만 가정해서는 안 되고',
        '1 부터 9 까지 전부 가정해야 한다.',
    ], { lh: 19 }));
    g.push(ctxt(540, py + 172, '점수를 계산하지 않고 증명한다', C1, { bold: true }));
    g.push(txt(540, py + 192, '(a+b)² − (a+b) 로 묶이는 것이 요점', { cls: 'ink2', size: 'sm' }));

    g.push(lines(24, 288, [
        '따라야 할 감각: 총점이 전략과 무관하다는 것은 쪼개는 순서를 아무리 바꿔도 같은 항들이 다시 나타난다는 뜻이다.',
        '실제 증명에서 계산한 것은 ab + a(a−1)/2 + b(b−1)/2 = (a+b)((a+b)−1)/2 한 줄뿐이다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-unstacking',
        svg: svg({
            width: W, height: H,
            title: '상자 쪼개기 게임 — 어떻게 쪼개도 총점이 같다',
            desc: '상자 열 개를 5 와 5 로 쪼갠 경우와 1 과 9 로 쪼갠 경우의 점수를 각각 더해 둘 다 45 가 되는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 8장 — 상태 기계
 * ================================================================== */

/* ---- 8-1. 대각선으로만 움직이는 로봇 ---- */
add((() => {
    const W = 780, H = 348;
    const g = [];
    g.push(txt(W / 2, 26, '한 걸음마다 좌표의 합이 0, 2, −2 만큼 바뀐다 — 짝수는 짝수로만 간다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽 — 격자와 도달 가능한 점
    const px0 = 20, py = 46, pw = 386, ph = 258;
    g.push(panel(px0, py, pw, ph, '도달 가능한 자리는 좌표의 합이 짝수인 자리뿐이다', null));
    const gx = px0 + 74, gy = py + 40, c = 42;
    // -1 .. 5 를 화면 좌표로
    const X = m => gx + (m + 1) * c;
    const Y = nn => gy + (3 - nn) * c;
    g.push(grid(gx, gy, c, 6, 4, { stroke: CG, sw: 0.8 }));
    for (let m = -1; m <= 5; m += 1) {
        for (let nn = -1; nn <= 3; nn += 1) {
            const even = (m + nn) % 2 === 0;
            if (m === 1 && nn === 0) continue;
            g.push(even ? dot(X(m), Y(nn), C3, 4.5) : `<circle cx="${X(m)}" cy="${Y(nn)}" r="3.2" fill="none" stroke="${CG}" stroke-width="1.4"/>`);
        }
    }
    // 시작 상태와 첫 걸음 넷
    g.push(ring(X(0), Y(0), 10, C1, 2));
    for (const [dm, dn] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
        g.push(arw(X(0) + dm * 8, Y(0) - dn * 8, X(dm) - dm * 9, Y(dn) + dn * 9, { cls: 's1', width: 1.6 }));
    }
    // 목표
    g.push(ex(X(1), Y(0), 8, C2, 2.6));
    g.push(ctxt(X(1) + 14, Y(0) - 12, '(1, 0) — 합이 1 이라 갈 수 없다', C2, { bold: true }));
    // 축 이름
    g.push(txt(X(5) + 16, Y(-1) + 5, 'x', { cls: 'ink2', size: 'sm' }));
    g.push(txt(X(-1) - 8, Y(3) - 8, 'y', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 범례
    g.push(dot(px0 + 30, py + 224, C3, 4.5));
    g.push(txt(px0 + 44, py + 228, '합이 짝수 — 도달 가능', { cls: 'ink2', size: 'sm' }));
    g.push(`<circle cx="${px0 + 210}" cy="${py + 224}" r="3.2" fill="none" stroke="${CG}" stroke-width="1.4"/>`);
    g.push(txt(px0 + 224, py + 228, '합이 홀수 — 도달 불가', { cls: 'ink2', size: 'sm' }));
    g.push(ring(px0 + 30, py + 246, 7, C1, 2));
    g.push(txt(px0 + 44, py + 250, '시작 상태 (0, 0) — 첫 걸음 네 갈래를 화살표로 그렸다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 왜 유지되는가
    const q0 = 424, qw = 336;
    g.push(panel(q0, py, qw, ph, '전이가 합을 어떻게 바꾸는가', '네 가지 걸음이 전부다'));
    const rows = [
        ['(m+1, n+1)', '+2'],
        ['(m+1, n−1)', '0'],
        ['(m−1, n+1)', '0'],
        ['(m−1, n−1)', '−2'],
    ];
    rows.forEach((r, i) => {
        const yy = py + 62 + i * 30;
        g.push(txt(q0 + 24, yy, `(m, n) →  ${r[0]}`, { cls: 'ink', size: 'sm' }));
        g.push(box(q0 + 214, yy - 15, 46, 22, { fill: C3, op: 0.18, stroke: C3, sw: 1.4, rx: 3 }));
        g.push(txt(q0 + 237, yy, r[1], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(q0 + 272, yy, '만큼 변한다', { cls: 'ink2', size: 'sm' }));
    });
    g.push(ln([[q0 + 24, py + 188], [q0 + qw - 24, py + 188]], { stroke: CG, sw: 1 }));
    g.push(lines(q0 + 24, py + 210, [
        '짝수에 0, 2, −2 를 더해도 짝수다. 그래서',
        '‘합이 짝수다’ 는 보존되는 불변량이고,',
        '시작 상태에서 참이므로 모든 도달 가능한 상태',
        '에서 참이다',
    ], { lh: 18 }));
    g.push(txt(24, 332, '불변량 논증에서 실제로 하는 일은 이 네 줄을 확인하는 것이다. 전이가 몇 가지인지 빠짐없이 세는 것이 증명의 절반이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-i-robot-diagonal',
        svg: svg({
            width: W, height: H,
            title: '대각선으로만 움직이는 로봇과 짝합 불변량',
            desc: '왼쪽은 좌표의 합이 짝수인 격자점만 도달 가능하고 (1,0) 은 갈 수 없음을 보이는 격자, 오른쪽은 네 가지 전이가 합을 0 이나 2 나 −2 만큼 바꾼다는 표',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-2. 물통 두 개 — 도달 가능한 것과 불가능한 것 ---- */
add((() => {
    const W = 780, H = 362;
    const g = [];
    g.push(txt(W / 2, 26, '5 L 물통으로는 되고 9 L 물통으로는 안 된다 — 불변량이 그 차이를 설명한다', { anchor: 'middle', cls: 'ink bold' }));

    // 위 — 3 L 과 5 L 로 4 L 만들기
    const py = 46;
    g.push(panel(20, py, 740, 128, '3 L 과 5 L : 여덟 걸음으로 큰 통에 4 L', '실행 하나를 제시하면 도달 가능성이 증명된다'));
    const path = [
        ['(0, 0)', '시작'],
        ['(0, 3)', '작은 통 채움'],
        ['(3, 0)', '작은 → 큰'],
        ['(3, 3)', '작은 통 채움'],
        ['(5, 1)', '작은 → 큰'],
        ['(0, 1)', '큰 통 비움'],
        ['(1, 0)', '작은 → 큰'],
        ['(1, 3)', '작은 통 채움'],
        ['(4, 0)', '작은 → 큰'],
    ];
    const sw2 = 76, sx = 34, sy = py + 58;
    path.forEach((s, i) => {
        const cx = sx + i * sw2;
        const last = i === path.length - 1;
        g.push(box(cx, sy, 66, 28, { fill: last ? C2 : C1, op: last ? 0.24 : 0.14, stroke: last ? C2 : C1, sw: last ? 1.9 : 1.3, rx: 4 }));
        g.push(txt(cx + 33, sy + 19, s[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(cx + 33, sy + 46, s[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i < path.length - 1) g.push(arw(cx + 68, sy + 14, cx + sw2 - 4, sy + 14, { cls: 'ark', width: 1.4 }));
    });
    g.push(txt(34, sy - 22, '상태는 (큰 통의 물, 작은 통의 물) 이고 단위는 L', { cls: 'ink2', size: 'sm' }));

    // 아래 왼쪽 — 3 L 과 9 L 의 도달 가능한 상태
    const qy = 190;
    g.push(panel(20, qy, 386, 148, '3 L 과 9 L : 두 값이 언제나 3 의 배수다', null));
    const lx = 106, ly = qy + 52, lc = 42;
    for (let bi = 0; bi <= 3; bi += 1) {
        for (let li = 0; li <= 1; li += 1) {
            g.push(dot(lx + bi * lc, ly + (1 - li) * lc, C3, 5));
        }
    }
    for (let bi = 0; bi <= 3; bi += 1) g.push(txt(lx + bi * lc, ly + lc + 20, String(bi * 3), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx - 16, ly + 5, '3', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx - 16, ly + lc + 5, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx + 2 * lc, ly + lc + 40, '큰 통 (L)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(lx - 42, ly + lc / 2 + 4, '작은 통', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 4 L 자리 — 격자에 없는 자리를 축 위에 표시한다
    const fx = lx + (4 / 3) * lc;
    g.push(ex(fx, ly + lc, 7, C2, 2.4));
    g.push(ctxt(fx + 44, ly + lc - 22, '4 L 자리는 격자에 없다', C2, { anchor: 'middle', bold: true }));

    // 아래 오른쪽 — 불변량과 주의
    g.push(panel(424, qy, 336, 148, '불변량: 두 값이 3 의 배수다', null));
    g.push(lines(444, qy + 50, [
        '여섯 가지 전이를 모두 확인한다. 3 의 배수를',
        '채우거나 비우거나 옮겨도 3 의 배수다.',
        '시작 상태 (0, 0) 에서 참이므로 모든 도달 가능한',
        '상태에서 참이고, 4 는 3 의 배수가 아니다',
    ], { lh: 18 }));
    g.push(ctxt(444, qy + 130, '주의: 불변량의 부정은 불변량이 아니다', C2, { bold: true }));
    g.push(txt(20, 356, '(1, 0) 은 ‘3 의 배수’ 를 어기지만 거기서 (0, 0) 으로 갈 수 있다. 보존은 참인 쪽에서만 요구된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-i-diehard-jugs',
        svg: svg({
            width: W, height: H,
            title: '물통 두 개 — 도달 가능한 상태와 불가능한 상태',
            desc: '위쪽은 3 L 과 5 L 물통으로 큰 통에 4 L 를 만드는 여덟 걸음, 아래쪽은 3 L 과 9 L 일 때 도달 가능한 상태가 3 의 배수 격자뿐이어서 4 L 를 만들 수 없다는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-3. 빠른 거듭제곱 — 불변량이 정말 일정하다 ---- */
add((() => {
    const W = 780, H = 336;
    const g = [];
    g.push(txt(W / 2, 26, '오른쪽 열이 한 줄도 바뀌지 않는다 — 그것이 이 프로그램이 옳은 이유다', { anchor: 'middle', cls: 'ink bold' }));

    const cols = ['걸음', 'x', 'y', 'z', 'z 가', 'y · xᶻ'];
    const rows = [
        ['0', '3', '1', '5', '홀수', '1 · 3⁵ = 243'],
        ['1', '9', '3', '2', '짝수', '3 · 9² = 243'],
        ['2', '81', '3', '1', '홀수', '3 · 81¹ = 243'],
        ['3', '6561', '243', '0', '멈춤', '243 · 1 = 243'],
    ];
    const cwid = [56, 62, 62, 46, 62, 150];
    const x0 = 40, y0 = 58, rh = 32;
    let acc = x0;
    const cx = cwid.map(w => { const v = acc; acc += w; return v; });
    const tw = acc - x0;
    // 마지막 열 강조
    g.push(box(cx[5] - 6, y0, cwid[5] + 12, rh * (rows.length + 1), { fill: C3, op: 0.14, stroke: 'none', rx: 3 }));
    cols.forEach((cname, i) => g.push(txt(cx[i] + cwid[i] / 2, y0 + 21, cname, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    g.push(ln([[x0, y0 + rh], [x0 + tw, y0 + rh]], { stroke: CK, sw: 1.3 }));
    rows.forEach((r, ri) => {
        r.forEach((v, i) => g.push(txt(cx[i] + cwid[i] / 2, y0 + rh * (ri + 2) - 10, v, { anchor: 'middle', cls: 'ink', size: 'sm' })));
        if (ri < rows.length - 1) g.push(ln([[x0, y0 + rh * (ri + 2)], [x0 + tw, y0 + rh * (ri + 2)]], { stroke: CG, sw: 0.8 }));
    });
    g.push(box(x0, y0, tw, rh * (rows.length + 1), { stroke: CK, sw: 1.2, rx: 3 }));
    g.push(txt(x0, y0 - 10, 'a = 3, b = 5 로 aᵇ 를 구한다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 설명
    const q0 = 512;
    g.push(panel(q0, y0, 248, rh * 6, '두 가지를 따로 증명한다', null));
    g.push(ctxt(q0 + 18, y0 + 48, '부분 정당성 — 불변량', C1, { bold: true }));
    g.push(lines(q0 + 18, y0 + 68, [
        'y · xᶻ = aᵇ 가 걸음마다 유지된다.',
        'z = 0 에서 멈추면 y = aᵇ 다',
    ], { lh: 18 }));
    g.push(ctxt(q0 + 18, y0 + 116, '종료성 — 측도', C2, { bold: true }));
    g.push(lines(q0 + 18, y0 + 136, [
        'z 가 음이 아닌 정수이고 걸음마다',
        '적어도 절반으로 줄어든다. 그래서',
        '곱셈 횟수가 b 가 아니라 log b 규모다',
    ], { lh: 18 }));

    g.push(txt(40, 264, '넷째 줄에서 x 가 6561 까지 커졌는데도 답은 243 이다. y · xᶻ 에서 z = 0 이 되어 x 가 답에 아무 영향을 주지 않기 때문이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 284, '그래서 이 프로그램은 마지막 걸음에서 쓸데없는 제곱을 한 번 한다 — 옳음과 효율은 다른 문제다.', { cls: 'ink2', size: 'sm' }));
    g.push(ctxt(40, 312, '표는 확인이지 증명이 아니다. 증명은 두 경우(z 가 짝수 · 홀수)에서 등식을 다시 세우는 것이다', C1, { bold: true }));
    return {
        name: 'mcs-i-fast-exp-trace',
        svg: svg({
            width: W, height: H,
            title: '빠른 거듭제곱의 실행 추적과 불변량',
            desc: 'a 는 3 b 는 5 일 때 x y z 의 값이 걸음마다 바뀌지만 y 곱하기 x 의 z 제곱은 243 으로 일정함을 보이는 표와, 부분 정당성과 종료성을 따로 증명한다는 설명',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-4. 유도 변수 세 가지와 종료성 ---- */
add((() => {
    const W = 780, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '종료성을 보장하는 것은 ‘강하게 감소’ 와 ‘정렬집합’ 둘 다다 — 하나만으로는 안 된다', { anchor: 'middle', cls: 'ink bold' }));

    const pw = 240, py = 48, ph = 200;
    const pxs = [20, 272, 524];
    const titles = [
        ['강하게 감소 · 값은 ℕ', '끝난다'],
        ['약하게 감소 · 값은 ℕ', '끝나지 않을 수 있다'],
        ['강하게 감소 · 값은 ℚ', '끝나지 않을 수 있다'],
    ];
    const seqs = [
        [7, 5, 4, 2, 1, 0],
        [7, 5, 5, 5, 5, 5],
        [7, 3.5, 1.75, 0.9, 0.45, 0.24],
    ];
    const oks = [true, false, false];
    for (let p = 0; p < 3; p += 1) {
        const px0 = pxs[p];
        g.push(panel(px0, py, pw, ph, titles[p][0], null));
        const col = oks[p] ? C3 : C2;
        g.push(ctxt(px0 + pw / 2, py + 38, titles[p][1], col, { anchor: 'middle', bold: true }));
        const bx = px0 + 44, by = py + 58, bh = 76, bwid = 168;
        // 축
        g.push(arw(bx, by + bh, bx + bwid, by + bh, { cls: 'ark', width: 1.2 }));
        g.push(arw(bx, by + bh, bx, by - 6, { cls: 'ark', width: 1.2 }));
        g.push(txt(bx + bwid - 2, by + bh + 16, '걸음', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(bx - 12, by - 8, 'f', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        const s = seqs[p];
        const sx2 = (i) => bx + 12 + i * 27;
        const sy2 = (v) => by + bh - (v / 8) * bh;
        g.push(ln(s.map((v, i) => [sx2(i), sy2(v)]), { stroke: col, sw: 1.8 }));
        s.forEach((v, i) => g.push(dot(sx2(i), sy2(v), col, 3.6)));
        const note = [
            ['값이 0 에 닿으면 더 갈 수 없다.', '실행 길이가 f(q) 이하로 묶인다'],
            ['평평한 구간에서 영원히 돌 수 있다.', '‘반드시 줄어든다’ 가 필요하다'],
            ['0 에 다가가지만 닿지 않는다.', 'ℚ 는 정렬집합이 아니다 (4장)'],
        ][p];
        g.push(lines(px0 + 16, py + 168, note, { lh: 18 }));
    }
    g.push(lines(24, 274, [
        '넓혀 쓰는 법: 값이 ℕ 이 아니어도 정렬집합이면 된다. 그러면 실행 길이의 상한은 말할 수 없지만 언젠가 끝난다는 것은 보장된다.',
        '4장에서 ‘정렬집합에는 무한 감소열이 없다’ 를 증명해 둔 것이 정확히 이 자리에 쓰인다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-derived-variable',
        svg: svg({
            width: W, height: H,
            title: '유도 변수 세 가지와 종료성',
            desc: '강하게 감소하는 자연수 값 측도는 종료를 보장하고, 약하게 감소하는 것과 유리수 값으로 강하게 감소하는 것은 보장하지 않음을 세 꺾은선으로 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-5. 불량 짝이 무엇인가 ---- */
add((() => {
    const W = 780, H = 320;
    const g = [];
    g.push(txt(W / 2, 26, '불량 짝은 서로를 배우자보다 좋아하는 한 쌍이다 — 그런 쌍이 없으면 안정하다', { anchor: 'middle', cls: 'ink bold' }));

    // 선호 목록
    const py = 48;
    g.push(panel(20, py, 236, 230, '선호 (선분에 적힌 수가 순위)', null));
    g.push(lines(36, py + 42, [
        '남자 둘은 모두 A 를 1순위로,',
        '여자 둘은 모두 갑 을 1순위로 둔다',
    ], { lh: 18 }));
    const men = ['갑', '을'];
    const women = ['A', 'B'];
    const mx = 66, wx = 200, ry = py + 96, rgap = 56;
    for (let i = 0; i < 2; i += 1) {
        g.push(box(mx - 22, ry + i * rgap - 16, 44, 30, { fill: C1, op: 0.16, stroke: C1, sw: 1.5, rx: 4 }));
        g.push(txt(mx, ry + i * rgap + 4, men[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(box(wx - 22, ry + i * rgap - 16, 44, 30, { fill: C3, op: 0.16, stroke: C3, sw: 1.5, rx: 4 }));
        g.push(txt(wx, ry + i * rgap + 4, women[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    // 순위 라벨이 붙은 선분 네 개
    const rank = [
        [0, 0, '1', '1'], [0, 1, '2', '2'], [1, 0, '1', '2'], [1, 1, '2', '1'],
    ];
    for (const [mi, wi, mr, wr] of rank) {
        const y1 = ry + mi * rgap, y2 = ry + wi * rgap;
        g.push(ln([[mx + 24, y1], [wx - 24, y2]], { stroke: CG, sw: 1.2 }));
        const t = 0.2;
        g.push(txt(mx + 24 + (wx - mx - 48) * t, y1 + (y2 - y1) * t - 5, mr, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(mx + 24 + (wx - mx - 48) * (1 - t), y1 + (y2 - y1) * (1 - t) - 5, wr, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }

    // 불안정한 짝짓기
    const draw = (px0, pairs, label, sub, bad) => {
        const out = [];
        out.push(panel(px0, py, 236, 230, label, sub));
        const ax = px0 + 66, bx2 = px0 + 200;
        for (let i = 0; i < 2; i += 1) {
            out.push(box(ax - 22, ry + i * rgap - 16, 44, 30, { fill: C1, op: 0.16, stroke: C1, sw: 1.5, rx: 4 }));
            out.push(txt(ax, ry + i * rgap + 4, men[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            out.push(box(bx2 - 22, ry + i * rgap - 16, 44, 30, { fill: C3, op: 0.16, stroke: C3, sw: 1.5, rx: 4 }));
            out.push(txt(bx2, ry + i * rgap + 4, women[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        for (const [mi, wi] of pairs) {
            out.push(ln([[ax + 24, ry + mi * rgap], [bx2 - 24, ry + wi * rgap]], { stroke: CK, sw: 2.6 }));
        }
        if (bad) {
            out.push(ln([[ax + 24, ry], [bx2 - 24, ry]], { stroke: C2, sw: 2.6, dash: '6 4' }));
            out.push(ctxt(px0 + 118, ry - 26, '갑 과 A 가 불량 짝', C2, { anchor: 'middle', bold: true }));
        }
        return out.join('');
    };
    g.push(draw(272, [[0, 1], [1, 0]], '불안정한 짝짓기', '갑 −B, 을 −A'));
    g.push(ln([[272 + 90, ry], [272 + 178, ry]], { stroke: C2, sw: 2.6, dash: '6 4' }));
    g.push(ctxt(272 + 118, ry - 26, '갑 과 A 가 불량 짝', C2, { anchor: 'middle', bold: true }));
    g.push(txt(288, py + 210, '둘 다 서로를 배우자보다 좋아한다', { cls: 'ink2', size: 'sm' }));
    g.push(draw(524, [[0, 0], [1, 1]], '안정한 짝짓기', '갑 −A, 을 −B'));
    g.push(txt(540, py + 210, '갑 과 A 는 1순위끼리라 흔들리지 않는다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(24, 302, '을 과 B 는 둘 다 상대를 2순위로 여기지만 그것은 문제가 아니다. 불량 짝이 되려면 ‘서로’ 를 배우자보다 좋아해야 한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-i-rogue-couple',
        svg: svg({
            width: W, height: H,
            title: '불량 짝과 안정한 짝짓기',
            desc: '남자 둘과 여자 둘의 선호를 순위로 적고, 서로를 배우자보다 좋아하는 불량 짝이 생기는 짝짓기와 그렇지 않은 안정한 짝짓기를 나란히 보인 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 8-6. 구혼 의식 나흘 ---- */
add((() => {
    const W = 780, H = 376;
    const g = [];
    g.push(txt(W / 2, 26, '남자가 세레나데하는 상대는 나빠지기만 하고, 여자의 최선 구혼자는 좋아지기만 한다', { anchor: 'middle', cls: 'ink bold' }));

    // 선호 목록
    const py = 46;
    g.push(panel(20, py, 250, 176, '선호 목록 (왼쪽이 1순위)', null));
    const ml = [['갑', 'A B C'], ['을', 'A C B'], ['병', 'B A C']];
    const wl = [['A', '을 갑 병'], ['B', '갑 병 을'], ['C', '병 갑 을']];
    ml.forEach((r, i) => {
        g.push(txt(44, py + 62 + i * 24, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(70, py + 62 + i * 24, `: ${r[1]}`, { cls: 'ink2', size: 'sm' }));
    });
    wl.forEach((r, i) => {
        g.push(txt(160, py + 62 + i * 24, r[0], { cls: 'ink bold', size: 'sm' }));
        g.push(txt(180, py + 62 + i * 24, `: ${r[1]}`, { cls: 'ink2', size: 'sm' }));
    });
    g.push(ctxt(44, py + 44, '남자', C1, { bold: true }));
    g.push(ctxt(160, py + 44, '여자', C3, { bold: true }));
    g.push(lines(36, py + 142, [
        '남자 셋이 모두 A 나 B 를 1순위로 두었다.',
        'C 를 1순위로 둔 남자는 없다',
    ], { lh: 18 }));

    // 나흘의 표
    g.push(panel(288, py, 472, 176, '누구의 발코니 아래에서 노래하는가', null));
    const dx = 380, dy = py + 40, cwid = 82, rh = 28;
    const days = ['1일', '2일', '3일', '4일'];
    days.forEach((d, i) => g.push(txt(dx + i * cwid + cwid / 2, dy + 14, d, { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    const tbl = [
        ['갑', ['A', 'B', 'B', 'B'], [0]],
        ['을', ['A', 'A', 'A', 'A'], []],
        ['병', ['B', 'B', 'A', 'C'], [1, 2]],
    ];
    tbl.forEach((r, ri) => {
        g.push(txt(dx - 18, dy + rh * (ri + 1) + 20, r[0], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        r[1].forEach((v, ci) => {
            const rej = r[2].includes(ci);
            const bxx = dx + ci * cwid + 16, byy = dy + rh * (ri + 1) + 4;
            g.push(box(bxx, byy, 50, 24, {
                fill: rej ? C2 : C3, op: rej ? 0.16 : 0.14,
                stroke: rej ? C2 : C3, sw: rej ? 1.7 : 1.2, rx: 3,
            }));
            g.push(txt(bxx + 25, byy + 17, v, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
            if (rej) g.push(ln([[bxx + 6, byy + 12], [bxx + 44, byy + 12]], { stroke: C2, sw: 1.8 }));
        });
    });
    g.push(ctxt(dx + 12, dy + rh * 4 + 18, '줄이 그어진 칸 = 그날 거절당했다', C2, { bold: true }));
    g.push(ctxt(dx + 3 * cwid + 41, dy + rh * 4 + 18, '4일에 끝난다', C1, { anchor: 'middle', bold: true }));
    g.push(box(dx + 3 * cwid + 10, dy + rh - 2, 62, rh * 3 + 4, { stroke: C1, sw: 1.9, rx: 4, dash: '5 4' }));

    // 아래 — 종료 측도와 결과
    const ry2 = 238;
    g.push(panel(20, ry2, 366, 122, '왜 반드시 끝나는가 — 측도', null));
    g.push(lines(40, ry2 + 46, [
        '아직 끝나지 않은 날에는 어떤 여자에게',
        '구혼자가 둘 이상 있고, 그날 저녁 적어도',
        '한 명이 목록에서 이름을 지운다.',
        '남자 n 명의 목록에 든 이름은 처음 n² 개이고',
        '늘어나는 일이 없다 → n² 일 안에 끝난다',
    ], { lh: 18 }));

    g.push(panel(404, ry2, 356, 122, '결과와 두 불변량', null));
    g.push(ctxt(424, ry2 + 44, '결과: 갑 −B, 을 −A, 병 −C', C1, { bold: true }));
    g.push(lines(424, ry2 + 64, [
        'P: 지워진 여자는 그 남자보다 좋은 구혼자를',
        '   이미 갖고 있다 → 모두 결혼하고 안정하다',
        'Q: 지워진 여자는 그 남자에게 가망 없는 상대다',
        '   → 남자는 가망 있는 최선의 짝을 얻는다',
    ], { lh: 18 }));
    return {
        name: 'mcs-i-marriage-ritual',
        svg: svg({
            width: W, height: H,
            title: '구혼 의식 나흘의 진행',
            desc: '남자 셋과 여자 셋의 선호 목록, 나흘 동안 각 남자가 누구에게 노래하고 언제 거절당하는지의 표, 그리고 종료 측도와 두 불변량의 요약',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9장 — 재귀적 자료형
 * ================================================================== */

/* ---- 9-1. 문자열의 재귀 구조와 길이 계산 ---- */
add((() => {
    const W = 780, H = 312;
    const g = [];
    g.push(txt(W / 2, 26, '문자열은 ‘한 글자 더하기 남은 문자열’ 이고, 길이도 그 모양대로 정의된다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽 — 중첩 쌍
    const py = 46;
    g.push(panel(20, py, 366, 148, '1011 을 재귀적 정의대로 펼치면', null));
    g.push(txt(40, py + 52, '⟨1, ⟨0, ⟨1, ⟨1, λ⟩⟩⟩⟩', { cls: 'ink bold' }));
    g.push(lines(40, py + 82, [
        'λ 는 빈 문자열(6장)이고, ⟨a, s⟩ 는 글자 a 를',
        '문자열 s 앞에 붙인 것이다. 프로그래밍 언어의',
        '연결 리스트가 정확히 이 자료형이다',
    ], { lh: 19 }));

    // 오른쪽 — 정의 두 줄
    g.push(panel(404, py, 356, 148, '정의는 두 줄뿐이다', null));
    g.push(ctxt(424, py + 50, '자료형', C1, { bold: true }));
    g.push(lines(424, py + 70, [
        '기저: λ ∈ A*',
        '구성자: a ∈ A 이고 s ∈ A* 이면 ⟨a, s⟩ ∈ A*',
    ], { lh: 19 }));
    g.push(ctxt(424, py + 118, '함수도 같은 두 줄로 정의한다', C3, { bold: true }));
    g.push(txt(424, py + 138, '|λ| ::= 0 ,   |⟨a, s⟩| ::= 1 + |s|', { cls: 'ink2', size: 'sm' }));

    // 아래 — 길이 계산 풀어 보기
    const qy = 208;
    g.push(panel(20, qy, 740, 86, '|1011| 을 정의만 써서 계산한다', null));
    const steps = ['|1011|', '1 + |011|', '2 + |11|', '3 + |1|', '4 + |λ|', '4'];
    const bw = 96, bx = 44;
    steps.forEach((s, i) => {
        const cxx = bx + i * (bw + 18);
        const last = i === steps.length - 1;
        g.push(box(cxx, qy + 40, bw, 28, { fill: last ? C3 : C1, op: last ? 0.22 : 0.12, stroke: last ? C3 : C1, sw: last ? 1.9 : 1.3, rx: 4 }));
        g.push(txt(cxx + bw / 2, qy + 59, s, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        if (!last) g.push(arw(cxx + bw + 2, qy + 54, cxx + bw + 16, qy + 54, { cls: 'ark', width: 1.4 }));
    });
    g.push(txt(24, 306, '한 걸음마다 구성자를 한 번 벗긴다. 그래서 구성자를 벗기는 횟수가 유한하다는 것이 곧 계산이 끝난다는 것이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mcs-i-string-cons',
        svg: svg({
            width: W, height: H,
            title: '문자열의 재귀적 구조와 길이 함수',
            desc: '1011 을 중첩된 쌍으로 펼친 모양, 자료형과 길이 함수의 정의 두 줄씩, 그리고 정의만으로 길이 4 를 계산해 나가는 여섯 단계',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-2. 괄호 문자열의 높이 ---- */
add((() => {
    const W = 780, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '왼쪽 괄호는 한 칸 올리고 오른쪽 괄호는 한 칸 내린다 — 짝이 맞는 것은 0 아래로 안 내려간다', { anchor: 'middle', cls: 'ink bold' }));

    const drawWalk = (px0, py, str, title, ok) => {
        const out = [];
        const pw = 366, ph = 216;
        out.push(panel(px0, py, pw, ph, title, null));
        const bx = px0 + 46, by = py + 44, cwid = 26, unit = 22, base = by + 5 * unit;
        // 0 선
        out.push(ln([[bx - 8, base], [bx + str.length * cwid + 8, base]], { stroke: CK, sw: 1.4 }));
        out.push(txt(bx - 16, base + 5, '0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        for (let k = 1; k <= 3; k += 1) {
            out.push(ln([[bx - 4, base - k * unit], [bx + str.length * cwid + 4, base - k * unit]], { stroke: CG, sw: 0.7, dash: '3 3' }));
            out.push(txt(bx - 16, base - k * unit + 5, String(k), { anchor: 'end', cls: 'ink2', size: 'sm' }));
        }
        out.push(ln([[bx - 4, base + unit], [bx + str.length * cwid + 4, base + unit]], { stroke: CG, sw: 0.7, dash: '3 3' }));
        out.push(txt(bx - 16, base + unit + 5, '−1', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        // 걸음
        let h = 0;
        const pts = [[bx, base]];
        let low = 0, lowAt = -1;
        for (let i = 0; i < str.length; i += 1) {
            h += str[i] === '[' ? 1 : -1;
            pts.push([bx + (i + 1) * cwid, base - h * unit]);
            if (h < low) { low = h; if (lowAt < 0) lowAt = i; }
            out.push(txt(bx + i * cwid + cwid / 2, base + 2.4 * unit, str[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        const col = ok ? C3 : C2;
        out.push(ln(pts, { stroke: col, sw: 2.2 }));
        for (const p of pts) out.push(dot(p[0], p[1], col, 3.2));
        if (!ok) {
            const bad = pts[lowAt + 1];
            out.push(ring(bad[0], bad[1], 9, C2, 2.2));
            out.push(ctxt(bx, by + 10, '어느 접두사에서 0 아래로 내려간다', C2, { bold: true }));
        } else {
            out.push(ctxt(bx, by + 10, '0 아래로 안 내려가고 끝에서 다시 0', C3, { bold: true }));
        }
        return out.join('');
    };
    g.push(drawWalk(20, 46, '[[[]][]][]', '짝이 맞는 문자열', true));
    g.push(drawWalk(404, 46, '[]][[[[]]', '짝이 맞지 않는 문자열', false));

    g.push(lines(24, 288, [
        '구조적 귀납법으로 증명할 것은 두 가지다 — 왼쪽과 오른쪽 괄호의 개수가 같다(끝에서 0 으로 돌아온다),',
        '그리고 모든 접두사에서 왼쪽이 오른쪽 이상이다(0 아래로 안 내려간다).',
        '거꾸로 이 두 조건을 만족하면 반드시 짝이 맞는데, 그쪽은 강한 귀납법이 편하다 — 길이를 쪼개기 때문이다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-bracket-height',
        svg: svg({
            width: W, height: H,
            title: '괄호 문자열의 높이 걸음',
            desc: '짝이 맞는 괄호 문자열의 높이가 0 이상에 머물다 끝에서 0 으로 돌아오는 꺾은선과, 짝이 맞지 않는 문자열의 높이가 0 아래로 내려가는 꺾은선',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-3. 모호한 정의는 함수를 정하지 못한다 ---- */
add((() => {
    const W = 780, H = 316;
    const g = [];
    g.push(txt(W / 2, 26, '같은 문자열을 두 길로 만들 수 있으면 그 위의 재귀 함수가 두 값을 갖는다', { anchor: 'middle', cls: 'ink bold' }));

    // 왼쪽 — 두 갈래 유도
    const py = 46;
    g.push(panel(20, py, 386, 190, 'λ 를 두 가지로 만들 수 있다', null));
    // 길 1
    g.push(ctxt(44, py + 48, '길 1 — 기저를 쓴다', C1, { bold: true }));
    g.push(box(44, py + 60, 120, 28, { fill: C1, op: 0.14, stroke: C1, sw: 1.4, rx: 4 }));
    g.push(txt(104, py + 79, 'λ ∈ 집합', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(170, py + 74, 208, py + 74, { cls: 's1', width: 1.5 }));
    g.push(box(214, py + 60, 140, 28, { fill: C1, op: 0.14, stroke: C1, sw: 1.4, rx: 4 }));
    g.push(txt(284, py + 79, 'f(λ) = 0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    // 길 2
    g.push(ctxt(44, py + 118, '길 2 — 이어붙이기 구성자를 쓴다', C2, { bold: true }));
    g.push(box(44, py + 130, 120, 28, { fill: C2, op: 0.14, stroke: C2, sw: 1.4, rx: 4 }));
    g.push(txt(104, py + 149, 'λ = λ λ', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(170, py + 144, 208, py + 144, { cls: 's2', width: 1.5 }));
    g.push(box(214, py + 130, 140, 28, { fill: C2, op: 0.14, stroke: C2, sw: 1.4, rx: 4 }));
    g.push(txt(284, py + 149, 'f(λ) = 1 + 0 + 0', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ctxt(200, py + 178, '0 = 1 이 되었다', C2, { anchor: 'middle', bold: true }));

    // 오른쪽 — 두 정의 비교
    g.push(panel(424, py, 336, 190, '두 정의의 차이', null));
    g.push(ctxt(444, py + 46, '모호하지 않은 정의', C3, { bold: true }));
    g.push(lines(444, py + 66, [
        '기저: λ',
        '구성자: s, t 가 들어 있으면 [s]t 도',
        '→ 문자열마다 만드는 길이 하나뿐이다',
    ], { lh: 18 }));
    g.push(ctxt(444, py + 128, '모호한 정의', C2, { bold: true }));
    g.push(lines(444, py + 148, [
        '기저: λ',
        '구성자: [s] 도, st 도',
        '→ 읽기는 쉬운데 함수를 얹을 수 없다',
    ], { lh: 18 }));

    g.push(lines(24, 262, [
        '두 정의가 정하는 집합은 같다. 그런데도 모호하지 않은 쪽을 골라 두는 이유가 이 그림이다 — 재귀 함수를 정의할 자리를 남겨 두기 위해서다.',
        '모호한 정의로도 집합에 대한 구조적 귀납법은 쓸 수 있다. 못 쓰는 것은 재귀 함수 정의뿐이다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-bracket-ambiguous',
        svg: svg({
            width: W, height: H,
            title: '모호한 재귀적 정의가 함수를 정하지 못하는 까닭',
            desc: '빈 문자열을 기저로 만드는 길과 두 빈 문자열을 이어붙여 만드는 길에서 같은 함수가 0 과 1 이라는 두 값을 갖게 되는 그림, 그리고 모호한 정의와 그렇지 않은 정의의 비교',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-4. 산술식의 구조와 값 ---- */
add((() => {
    const W = 780, H = 356;
    const g = [];
    g.push(txt(W / 2, 26, '구성자마다 규칙이 하나씩 — 그것이 eval 의 정의이고, 계산은 나무를 잎에서 뿌리로 올라오는 일이다', { anchor: 'middle', cls: 'ink bold' }));

    // 나무
    const py = 46;
    g.push(panel(20, py, 470, 250, '[ [3*[x*x] ] + [ [2*x] + 1] ] 을 x = 2 에서 계산한다', null));
    const node = (x, y, lab, val, col = C1) => box(x - 32, y - 15, 64, 30, { fill: col, op: 0.14, stroke: col, sw: 1.5, rx: 5 })
        + txt(x, y + 5, lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' })
        + (val !== null ? ctxt(x, y + 30, val, C2, { anchor: 'middle', bold: true }) : '');
    const L = [
        // [x, y, label, value]
        [255, py + 50, '+', '17'],
        [150, py + 122, '*', '12'],
        [360, py + 122, '+', '5'],
        [96, py + 194, '3', '3'],
        [204, py + 194, '*', '4'],
        [306, py + 194, '*', '4'],
        [414, py + 194, '1', '1'],
    ];
    const edges = [[0, 1], [0, 2], [1, 3], [1, 4], [2, 5], [2, 6]];
    for (const [a, b] of edges) g.push(ln([[L[a][0], L[a][1] + 16], [L[b][0], L[b][1] - 16]], { stroke: CK, sw: 1.3 }));
    for (const nd of L) g.push(node(nd[0], nd[1], nd[2], nd[3]));
    g.push(txt(204, py + 236, 'x · x', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(306, py + 236, '2 · x', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ctxt(44, py + 50, '주황 숫자가 그 부분식의 값', C2, { bold: true }));

    // eval 정의
    g.push(panel(508, py, 252, 250, 'eval 의 정의', 'x 의 값을 n 이라 한다'));
    g.push(ctxt(526, py + 60, '기저', C1, { bold: true }));
    g.push(lines(526, py + 80, [
        'eval(x, n) ::= n',
        'eval(k, n) ::= k',
    ], { lh: 19 }));
    g.push(ctxt(526, py + 126, '구성자', C3, { bold: true }));
    g.push(lines(526, py + 146, [
        'eval([e₁+e₂], n) ::=',
        '    eval(e₁, n) + eval(e₂, n)',
        'eval([e₁*e₂], n) ::=',
        '    eval(e₁, n) · eval(e₂, n)',
        'eval(-[e₁], n) ::= − eval(e₁, n)',
    ], { lh: 19 }));

    g.push(lines(24, 316, [
        '3x² + 2x + 1 은 Aexp 가 아니라 Aexp 의 줄임말이다. 지수도 우선순위도 정의에 없으므로 괄호를 다 적어야 하고, 그 덕에 나무 모양이 하나로 정해진다.',
        '식을 나무로 보는 눈이 이 장의 소득이다 — 컴파일러가 표현식을 다루는 방식이 정확히 이것이다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-aexp-tree',
        svg: svg({
            width: W, height: H,
            title: '산술식의 나무 구조와 eval 계산',
            desc: '3 곱하기 x 제곱 더하기 2x 더하기 1 을 완전히 괄호로 적은 식의 나무와 각 부분식의 값, 그리고 eval 함수의 기저와 구성자 정의',
            body: g.join(''),
        }),
    };
})());

/* ---- 9-5. 님 게임의 나무와 필승 전략 ---- */
add((() => {
    const W = 780, H = 344;
    const g = [];
    g.push(txt(W / 2, 26, '자기 차례에 ‘상대가 지는 판’ 이 하나라도 있으면 이긴다 — 없으면 진다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    g.push(panel(20, py, 500, 250, '님 ⟨1, 2⟩ 의 게임 나무', '돌을 마지막에 집는 사람이 이긴다'));
    // 노드: [x, y, 라벨, 승패]
    const N = [
        [270, py + 62, '⟨1, 2⟩', '승'],
        [110, py + 132, '⟨2⟩', '승'],
        [270, py + 132, '⟨1, 1⟩', '패'],
        [430, py + 132, '⟨1⟩', '승'],
        [64, py + 202, '⟨1⟩', '승'],
        [156, py + 202, '⟨ ⟩', '패'],
        [270, py + 202, '⟨1⟩', '승'],
        [430, py + 202, '⟨ ⟩', '패'],
    ];
    const E = [[0, 1], [0, 2], [0, 3], [1, 4], [1, 5], [2, 6], [3, 7]];
    for (const [a, b] of E) {
        const win = N[b][3] === '패';
        g.push(ln([[N[a][0], N[a][1] + 16], [N[b][0], N[b][1] - 16]], { stroke: win ? C3 : CG, sw: win ? 2.4 : 1.2 }));
    }
    for (const nd of N) {
        const col = nd[3] === '승' ? C1 : C2;
        g.push(box(nd[0] - 34, nd[1] - 16, 68, 32, { fill: col, op: 0.14, stroke: col, sw: 1.6, rx: 5 }));
        g.push(txt(nd[0], nd[1] + 1, nd[2], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(ctxt(nd[0], nd[1] + 14, nd[3], col, { anchor: 'middle', bold: true }));
    }
    g.push(ctxt(40, py + 238, '굵은 초록 선 = 필승 수 (상대를 패 상태로 보낸다)', C3, { bold: true }));

    // 오른쪽 — 재귀적 정의와 정리
    g.push(panel(538, py, 222, 250, '게임도 재귀적 자료형이다', null));
    g.push(ctxt(556, py + 48, '정의', C1, { bold: true }));
    g.push(lines(556, py + 68, [
        '기저: 승, 패 는 게임이다',
        '구성자: 게임들의 공집합이',
        '아닌 모임 G 도 게임이다.',
        'G 의 원소가 첫 수 하나씩이다',
    ], { lh: 18 }));
    g.push(ctxt(556, py + 156, '기본 정리', C3, { bold: true }));
    g.push(lines(556, py + 176, [
        '어떤 게임에서도 두 사람 중',
        '한쪽에게 필승 전략이 있다.',
        '구조적 귀납법 한 번으로',
        '증명된다',
    ], { lh: 18 }));

    g.push(lines(24, 312, [
        '라벨의 뜻: ‘승’ 은 그 판에서 둘 차례인 사람이 이긴다는 것이다. 그래서 자식이 모두 ‘승’ 이면 부모는 ‘패’ 이고, 자식 하나라도 ‘패’ 면 부모는 ‘승’ 이다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-nim-tree',
        svg: svg({
            width: W, height: H,
            title: '님 게임의 나무와 필승 전략',
            desc: '돌이 1 개와 2 개인 님 게임의 모든 진행을 나무로 그리고 각 판에서 둘 차례인 사람이 이기는지 지는지 표시한 그림, 그리고 게임의 재귀적 정의와 기본 정리',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 10장 — 무한집합
 * ================================================================== */

/* ---- 10-1. 무한집합에 원소를 하나 더해도 크기가 같다 ---- */
add((() => {
    const W = 780, H = 306;
    const g = [];
    g.push(txt(W / 2, 26, '한 칸씩 밀면 자리가 남는다 — 유한집합에서는 결코 안 되는 일이다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    // 왼쪽 — 무한집합
    g.push(panel(20, py, 386, 200, '무한집합 A 와 A ∪ {b}', '전단사가 있다 → 같은 크기'));
    const lx = 96, rx = 300, ry = py + 58, gap = 30;
    g.push(ctxt(lx, py + 42, 'A ∪ {b}', C2, { anchor: 'middle', bold: true }));
    g.push(ctxt(rx, py + 42, 'A', C3, { anchor: 'middle', bold: true }));
    const names = ['b', 'a₀', 'a₁', 'a₂', '⋮'];
    const rnames = ['a₀', 'a₁', 'a₂', 'a₃', '⋮'];
    for (let i = 0; i < 5; i += 1) {
        const yy = ry + i * gap;
        if (i < 4) {
            g.push(box(lx - 26, yy - 12, 52, 24, { fill: i === 0 ? C2 : CG, op: i === 0 ? 0.2 : 0.25, stroke: i === 0 ? C2 : CK, sw: 1.3, rx: 4 }));
            g.push(box(rx - 26, yy - 12, 52, 24, { fill: CG, op: 0.25, stroke: CK, sw: 1.3, rx: 4 }));
            g.push(arw(lx + 30, yy, rx - 30, yy, { cls: 's1', width: 1.5 }));
        }
        g.push(txt(lx, yy + 5, names[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(rx, yy + 5, rnames[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    g.push(txt(36, py + 184, 'e(b) ::= a₀ ,   e(aₙ) ::= aₙ₊₁ ,   나머지는 그대로', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 유한집합
    g.push(panel(424, py, 336, 200, '유한집합에서는 안 된다', '자리가 하나 모자란다'));
    const fx = 506, fx2 = 664, fy = py + 62;
    const fn = ['b', 'a₀', 'a₁'];
    const fr = ['a₀', 'a₁', '?'];
    for (let i = 0; i < 3; i += 1) {
        const yy = fy + i * gap;
        const miss = i === 2;
        g.push(box(fx - 26, yy - 12, 52, 24, { fill: i === 0 ? C2 : CG, op: i === 0 ? 0.2 : 0.25, stroke: i === 0 ? C2 : CK, sw: 1.3, rx: 4 }));
        g.push(txt(fx, yy + 5, fn[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(box(fx2 - 26, yy - 12, 52, 24, { fill: miss ? 'none' : CG, op: 0.25, stroke: miss ? C2 : CK, sw: miss ? 1.7 : 1.3, rx: 4, dash: miss ? '4 3' : undefined }));
        g.push(txt(fx2, yy + 5, fr[i], { anchor: 'middle', cls: miss ? 'ink2' : 'ink bold', size: 'sm' }));
        g.push(arw(fx + 30, yy, fx2 - 30, yy, { cls: miss ? 's2' : 's1', width: 1.5 }));
    }
    g.push(ctxt(440, py + 158, '마지막 화살표가 갈 곳이 없다', C2, { bold: true }));
    g.push(txt(440, py + 184, '|A ∪ {b}| = |A| + 1 이라 전단사가 없다', { cls: 'ink2', size: 'sm' }));

    g.push(lines(24, 274, [
        '왼쪽 논증에 숨은 전제: A 가 무한이면 서로 다른 원소의 무한 열 a₀, a₁, a₂, … 를 뽑을 수 있다.',
        '이것은 선택 공리에 기대는 대목이고, 이 장 마지막 절의 이야기다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-shift-bijection',
        svg: svg({
            width: W, height: H,
            title: '무한집합에 원소를 하나 더해도 크기가 같다',
            desc: '왼쪽은 무한집합에서 b 를 a0 으로 a0 을 a1 로 밀어 전단사를 만드는 그림, 오른쪽은 유한집합에서 마지막 화살표가 갈 곳이 없어 전단사가 없다는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-2. 쌍과 유리수를 줄 세우기 ---- */
add((() => {
    const W = 780, H = 364;
    const g = [];
    g.push(txt(W / 2, 26, '대각선을 따라 훑으면 어떤 쌍도 유한한 걸음 안에 번호를 받는다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    g.push(panel(20, py, 440, 286, 'ℕ × ℕ 의 모든 쌍에 번호를 붙인다', null));
    const gx = 118, gy = py + 76, c = 48;
    for (let i = 0; i < 5; i += 1) {
        g.push(txt(gx + i * c, gy - 18, String(i), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(gx - 24, gy + i * c + 5, String(i), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(gx + 2 * c, gy - 38, '두 번째 성분', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(gx - 64, gy + 2 * c + 5, '첫 번째', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const order = [];
    for (let d = 0; d <= 8; d += 1) {
        for (let i = 0; i <= d; i += 1) {
            const j = d - i;
            if (i < 5 && j < 5) order.push([i, j]);
        }
    }
    const pos = new Map();
    order.forEach(([i, j], k) => pos.set(`${i},${j}`, k));
    g.push(ln(order.map(([i, j]) => [gx + j * c, gy + i * c]), { stroke: C1, sw: 1.3, dash: '4 3' }));
    for (let i = 0; i < 5; i += 1) {
        for (let j = 0; j < 5; j += 1) {
            g.push(dot(gx + j * c, gy + i * c, C3, 4));
            g.push(ctxt(gx + j * c + 9, gy + i * c - 8, String(pos.get(`${i},${j}`)), C1, { bold: true }));
        }
    }
    g.push(txt(24, 352, '점 옆의 파란 수가 그 쌍이 받는 번호다. 가로로 훑으면 첫 줄에서 영원히 못 벗어나므로 대각선으로 훑어야 한다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(478, py, 282, 286, '같은 줄 세우기로 ℚ⁺ 도 가산이다', null));
    g.push(lines(496, py + 46, [
        '쌍 (i, j) 를 분수 i / j 로 읽으면',
        'ℕ × ℕ 의 줄 세우기가 곧 유리수의 줄',
        '세우기가 된다. 중복(1/2 와 2/4)이',
        '나오지만 문제가 아니다 — 가산이라는 것은',
        '중복을 허용한 목록에 다 나온다는 뜻이고,',
        '그것이 ℕ surj C 다',
    ], { lh: 19 }));
    g.push(ctxt(496, py + 172, '되돌아볼 것', C2, { bold: true }));
    g.push(lines(496, py + 192, [
        '유리수는 두 정수 사이를 빽빽이 메우는데도',
        '정수보다 많지 않다. 촘촘함과 크기는',
        '다른 이야기다',
    ], { lh: 19 }));
    return {
        name: 'mcs-i-count-pairs',
        svg: svg({
            width: W, height: H,
            title: '쌍과 유리수를 줄 세우기',
            desc: '자연수 쌍의 격자를 대각선 방향으로 훑어 각 쌍에 번호를 붙이는 경로와, 같은 방법으로 양의 유리수도 가산이 되는 설명',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-3. 대각선 논법 ---- */
add((() => {
    const W = 780, H = 386;
    const g = [];
    g.push(txt(W / 2, 26, '목록이 무엇이든 대각선을 뒤집으면 목록에 없는 것이 만들어진다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    g.push(panel(20, py, 452, 320, '무한 비트열을 모두 줄 세웠다고 하자', null));
    const rows = [
        [1, 0, 0, 0, 1, 1],
        [0, 1, 1, 1, 0, 1],
        [1, 1, 1, 1, 1, 1],
        [0, 1, 0, 0, 1, 0],
        [0, 0, 1, 0, 0, 0],
        [1, 0, 0, 1, 1, 1],
    ];
    const tx = 140, ty = py + 48, cw = 40, rh = 30;
    for (let i = 0; i < 6; i += 1) {
        g.push(box(tx + i * cw - 15, ty + i * rh - 15, 30, 30, { fill: C2, op: 0.2, stroke: C2, sw: 1.6, rx: 4 }));
    }
    rows.forEach((r, i) => {
        g.push(ctxt(tx - 36, ty + i * rh + 5, `A${'₀₁₂₃₄₅'[i]} =`, C1, { anchor: 'end', bold: true }));
        r.forEach((v, j) => g.push(txt(tx + j * cw, ty + i * rh + 5, String(v), { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
        g.push(txt(tx + 6 * cw - 6, ty + i * rh + 5, '⋯', { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(tx - 36, ty + 6 * rh + 6, '⋮', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const dy = ty + 6 * rh + 20;
    g.push(ln([[tx - 100, dy - 14], [tx + 6 * cw + 10, dy - 14]], { stroke: CG, sw: 1 }));
    g.push(ctxt(tx - 36, dy + 12, 'D =', C2, { anchor: 'end', bold: true }));
    g.push(ctxt(tx - 36, dy + 40, 'C =', C3, { anchor: 'end', bold: true }));
    for (let i = 0; i < 6; i += 1) {
        const d = rows[i][i];
        g.push(ctxt(tx + i * cw, dy + 12, String(d), C2, { anchor: 'middle', bold: true }));
        g.push(ctxt(tx + i * cw, dy + 40, String(1 - d), C3, { anchor: 'middle', bold: true }));
    }
    g.push(txt(tx + 6 * cw - 6, dy + 12, '⋯', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx + 6 * cw - 6, dy + 40, '⋯', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, dy + 68, 'D 는 대각선, C 는 D 의 각 자리를 뒤집은 것', { cls: 'ink2', size: 'sm' }));

    g.push(panel(490, py, 270, 320, '왜 C 가 목록에 없는가', null));
    g.push(lines(508, py + 48, [
        'C 가 목록의 n 번째 줄 Aₙ 이라고 하자.',
        '두 비트열이 같으면 모든 자리가 같으므로',
        '특히 n 번째 자리가 같아야 한다.',
        '',
        '그런데 C 의 n 번째 자리는 정의상',
        'Aₙ 의 n 번째 자리를 뒤집은 것이다.',
        '같으면서 다르다 — 모순이다',
    ], { lh: 19 }));
    g.push(ctxt(508, py + 204, '어느 목록에서도 통한다', C1, { bold: true }));
    g.push(lines(508, py + 224, [
        '줄 세우는 방법을 아무리 잘 고안해도',
        'C 를 그 방법에 넣어 다시 뒤집으면 또',
        '빠진 것이 나온다. 그래서 무한 비트열',
        '전체는 가산이 아니다',
    ], { lh: 19 }));
    g.push(ctxt(508, py + 306, '이 그림이 이 장의 뼈대다', C2, { bold: true }));
    return {
        name: 'mcs-i-diagonal',
        svg: svg({
            width: W, height: H,
            title: '대각선 논법',
            desc: '무한 비트열을 여섯 줄 늘어놓고 대각선 자리를 표시한 표, 그 대각선 D 와 각 자리를 뒤집은 C, 그리고 C 가 어떤 줄과도 다를 수밖에 없다는 논증',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-4. 칸토어 정리의 대각선 집합 ---- */
add((() => {
    const W = 780, H = 352;
    const g = [];
    g.push(txt(W / 2, 26, '‘자기가 자기 상에 들어 있는가’ 를 모두 뒤집으면 어느 상과도 다른 집합이 나온다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    g.push(panel(20, py, 452, 270, 'A = {1, 2, 3, 4} 이고 g : A → pow(A) 라 하자', '칸은 x 가 g(a) 에 들어 있는가 (1 = 예)'));
    const M = [
        [0, 1, 1, 0],
        [0, 1, 0, 0],
        [0, 0, 0, 0],
        [1, 0, 0, 1],
    ];
    const gs = ['{2, 3}', '{2}', '∅', '{1, 4}'];
    const tx = 226, ty = py + 80, cw = 44, rh = 30;
    for (let j = 0; j < 4; j += 1) g.push(txt(tx + j * cw, ty - 22, String(j + 1), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 4; i += 1) {
        g.push(box(tx + i * cw - 15, ty + i * rh - 15, 30, 30, { fill: C2, op: 0.2, stroke: C2, sw: 1.6, rx: 4 }));
    }
    M.forEach((r, i) => {
        g.push(txt(tx - 40, ty + i * rh + 5, `g(${i + 1}) = ${gs[i]}`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        r.forEach((v, j) => g.push(txt(tx + j * cw, ty + i * rh + 5, String(v), { anchor: 'middle', cls: 'ink bold', size: 'sm' })));
    });
    const dy = ty + 4 * rh + 16;
    g.push(ln([[tx - 186, dy - 14], [tx + 3 * cw + 20, dy - 14]], { stroke: CG, sw: 1 }));
    g.push(ctxt(tx - 40, dy + 10, '대각선 a ∈ g(a)', C2, { anchor: 'end', bold: true }));
    g.push(ctxt(tx - 40, dy + 38, '뒤집으면 a ∈ A_g', C3, { anchor: 'end', bold: true }));
    for (let i = 0; i < 4; i += 1) {
        g.push(ctxt(tx + i * cw, dy + 10, String(M[i][i]), C2, { anchor: 'middle', bold: true }));
        g.push(ctxt(tx + i * cw, dy + 38, String(1 - M[i][i]), C3, { anchor: 'middle', bold: true }));
    }
    g.push(ctxt(tx + 4 * cw + 8, dy + 38, '= {1, 3}', C3, { bold: true }));

    g.push(panel(490, py, 270, 270, '정의와 결론', null));
    g.push(ctxt(508, py + 46, '정의', C1, { bold: true }));
    g.push(txt(508, py + 66, 'A_g ::= { a ∈ A : a ∉ g(a) }', { cls: 'ink bold', size: 'sm' }));
    g.push(ctxt(508, py + 98, '왜 상에 없는가', C1, { bold: true }));
    g.push(lines(508, py + 118, [
        'A_g = g(a) 인 a 가 있다고 하자.',
        '그 a 에서 두 집합을 비교하면',
        'a ∈ A_g ↔ a ∉ g(a) = A_g 다.',
        '‘들어 있으면 안 들어 있다’ — 모순',
    ], { lh: 19 }));
    g.push(ctxt(508, py + 210, 'A strict pow(A)', C2, { bold: true }));
    g.push(txt(508, py + 230, '어떤 A 에 대해서도 성립한다', { cls: 'ink2', size: 'sm' }));

    g.push(lines(24, 336, [
        '이 표와 앞의 비트열 표는 같은 표다. 부분집합 하나가 비트열 하나이므로(6장) pow(ℕ) 이 무한 비트열 전체와 전단사다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-cantor-power',
        svg: svg({
            width: W, height: H,
            title: '칸토어 정리의 대각선 집합',
            desc: '원소 네 개인 집합에서 g 가 주는 부분집합들의 소속 여부를 행렬로 적고, 대각선을 뒤집어 얻은 집합이 어느 상과도 다르다는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* ---- 10-5. 정지 문제의 대각선 ---- */
add((() => {
    const W = 780, H = 374;
    const g = [];
    g.push(txt(W / 2, 26, '프로그램도 문자열이므로 프로그램과 입력을 같은 축에 놓을 수 있다', { anchor: 'middle', cls: 'ink bold' }));

    const py = 46;
    g.push(panel(20, py, 470, 300, '칸은 ‘그 프로그램을 그 문자열에 돌리면 멈추는가’', '행은 프로그램, 열은 입력 문자열'));
    const M = [
        ['멈춤', '안멈춤', '멈춤', '안멈춤'],
        ['안멈춤', '안멈춤', '멈춤', '멈춤'],
        ['멈춤', '멈춤', '안멈춤', '멈춤'],
        ['안멈춤', '멈춤', '멈춤', '안멈춤'],
    ];
    const tx = 224, ty = py + 80, cw = 62, rh = 32;
    for (let j = 0; j < 4; j += 1) g.push(txt(tx + j * cw, ty - 22, `s${'₀₁₂₃'[j]}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 4; i += 1) {
        g.push(box(tx + i * cw - 30, ty + i * rh - 15, 60, 30, { fill: C2, op: 0.2, stroke: C2, sw: 1.6, rx: 4 }));
    }
    M.forEach((r, i) => {
        g.push(txt(tx - 40, ty + i * rh + 5, `P(s${'₀₁₂₃'[i]})`, { anchor: 'end', cls: 'ink bold', size: 'sm' }));
        r.forEach((v, j) => g.push(txt(tx + j * cw, ty + i * rh + 5, v, { anchor: 'middle', cls: 'ink', size: 'sm' })));
    });
    const dy = ty + 4 * rh + 16;
    g.push(ln([[tx - 140, dy - 14], [tx + 3 * cw + 30, dy - 14]], { stroke: CG, sw: 1 }));
    g.push(ctxt(tx - 40, dy + 10, '대각선', C2, { anchor: 'end', bold: true }));
    g.push(ctxt(tx - 40, dy + 38, '뒤집으면 안멈춤', C3, { anchor: 'end', bold: true }));
    for (let i = 0; i < 4; i += 1) {
        const d = M[i][i];
        g.push(ctxt(tx + i * cw, dy + 10, d, C2, { anchor: 'middle', bold: true }));
        g.push(ctxt(tx + i * cw, dy + 38, d === '멈춤' ? '아니오' : '예', C3, { anchor: 'middle', bold: true }));
    }
    g.push(txt(36, dy + 66, '아래 줄이 집합 안멈춤 의 소속 여부다. 어느 행과도 대각선 자리에서 다르다', { cls: 'ink2', size: 'sm' }));

    g.push(panel(508, py, 252, 300, '결론', null));
    g.push(ctxt(526, py + 46, '정의', C1, { bold: true }));
    g.push(lines(526, py + 66, [
        'lang(P) ::= P 가 멈추는 문자열 전체',
        '안멈춤 ::= { s : 프로그램 P(s) 가',
        '            s 에서 멈추지 않는다 }',
    ], { lh: 19 }));
    g.push(ctxt(526, py + 140, '정리', C2, { bold: true }));
    g.push(lines(526, py + 160, [
        '어떤 프로그램도 집합 안멈춤 을',
        '인식하지 못한다. 인식한다면 그 프로그램',
        '자신의 코드 s₀ 에서',
        's₀ ∈ 안멈춤 ↔ s₀ ∉ 안멈춤 이 된다',
    ], { lh: 19 }));
    g.push(ctxt(526, py + 248, '언어를 바꿔도 안 된다', C1, { bold: true }));

    g.push(lines(24, 360, [
        '이 표는 칸토어 정리의 표와 같은 모양이다. 다른 것은 축의 이름뿐이고, 그래서 계산의 한계가 무한집합의 크기 이야기에서 나온다',
    ], { lh: 20 }));
    return {
        name: 'mcs-i-halting-table',
        svg: svg({
            width: W, height: H,
            title: '정지 문제의 대각선',
            desc: '프로그램을 행, 입력 문자열을 열로 놓고 멈추는지를 적은 표에서 대각선을 뒤집어 만든 집합이 어느 프로그램이 인식하는 집합과도 다르다는 그림',
            body: g.join(''),
        }),
    };
})());

export default figures;
