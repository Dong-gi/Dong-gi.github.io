/**
 * 14장 광학, 15장 현대물리 입문의 그림.
 *
 * physics.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를 돌려주고
 * name 이 파일 이름(/figures/physics/<name>.svg)이 된다.
 * 이름은 opt- (광학) / qm- (현대물리) 로 시작한다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 라벨은 유니코드 그리스 문자와 `n~1` 꼴의 아래첨자 표기로 적는다.
 *
 * 광선 작도 그림은 눈대중으로 그리지 않는다. 초점거리와 물체 거리를 정해 두고
 * 결상 공식으로 상의 자리를 계산한 다음, 세 광선이 그 자리에서 실제로 만나도록
 * 좌표를 잡는다. 그림과 식이 어긋나면 독자가 둘 중 무엇을 믿을지 알 수 없다.
 */
import { svg, frame, arc, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const D2R = Math.PI / 180;

const STROKE = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};
const MARK = { s1: 'ar1', s2: 'ar2', s3: 'ar3', ink: 'ark', ink2: 'ark', grid: 'ark' };

/** 화소 좌표 직선. lib 의 클래스가 굵기를 고정해 버리므로 stroke 를 직접 준다. */
function line(x1, y1, x2, y2, { c = 'ink2', w = 1.6, dash } = {}) {
    return `<path fill="none" stroke="${STROKE[c]}" stroke-width="${w}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} stroke-linecap="round"`
        + ` d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 점 목록을 잇는 꺾은선/닫힌 도형. */
function poly(pts, { c = 'ink2', w = 1.6, dash, close = false, fill = 'none', op = 1 } = {}) {
    const d = 'M' + pts.map(([x, y]) => `${r2(x)} ${r2(y)}`).join(' L') + (close ? ' Z' : '');
    return `<path d="${d}" fill="${fill === 'none' ? 'none' : STROKE[fill]}" fill-opacity="${op}"`
        + ` stroke="${STROKE[c]}" stroke-width="${w}"${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ' stroke-linejoin="round"/>';
}

/** 끝에 화살촉이 붙는 직선. */
function arrow(x1, y1, x2, y2, { c = 's1', w = 2.2, dash } = {}) {
    return `<path fill="none" stroke="${STROKE[c]}" stroke-width="${w}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''} stroke-linecap="round"`
        + ` marker-end="url(#${MARK[c]})" d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 광선. 진행 방향을 알 수 있게 도중에 화살촉을 하나 둔다. */
function ray(x1, y1, x2, y2, { c = 's1', w = 2, dash, at = 0.55 } = {}) {
    const mx = x1 + (x2 - x1) * at, my = y1 + (y2 - y1) * at;
    return arrow(x1, y1, mx, my, { c, w, dash }) + line(mx, my, x2, y2, { c, w, dash });
}

/** 벽·거울 뒷면 같은 고정면. 빗금으로 표시한다. dir 은 빗금이 뻗는 쪽. */
function hatch(x1, y1, x2, y2, dir = 1, gap = 10, len = 8) {
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy);
    const ux = dx / L, uy = dy / L, nx = -uy * dir, ny = ux * dir;
    const out = [line(x1, y1, x2, y2, { c: 'ink2', w: 2 })];
    for (let s = 0; s <= L; s += gap) {
        const bx = x1 + ux * s, by = y1 + uy * s;
        out.push(line(bx, by, bx + nx * len - ux * len, by + ny * len - uy * len, { c: 'grid', w: 1 }));
    }
    return out.join('');
}

/** 양쪽 화살표 + 가운데 라벨. 길이를 재는 표시. */
function span(x1, y1, x2, y2, label, { c = 'ink2', dy = -6 } = {}) {
    const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
    return arrow(mx, my, x1, y1, { c, w: 1.3 }) + arrow(mx, my, x2, y2, { c, w: 1.3 })
        + (label ? txt(mx, my + dy, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 물체·상을 나타내는 세로 화살표. */
function upright(x, yBase, h, { c = 's3', label, dash } = {}) {
    return arrow(x, yBase, x, yBase - h, { c, w: 2.6, dash })
        + (label ? txt(x, yBase - h + (h > 0 ? -8 : 16), label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 원호를 꺾은선으로 만든다. 각은 도, 수학 관례(반시계). */
function arcPts(cx, cy, R, a1, a2, steps = 48) {
    const p = [];
    for (let i = 0; i <= steps; i += 1) {
        const a = (a1 + ((a2 - a1) * i) / steps) * D2R;
        p.push([cx + R * Math.cos(a), cy - R * Math.sin(a)]);
    }
    return p;
}

/** 볼록렌즈(양볼록) 모양. */
function lensConvex(cx, cy, h, bulge = 15) {
    return `<path d="M${r2(cx)} ${r2(cy - h)} Q${r2(cx + bulge)} ${r2(cy)} ${r2(cx)} ${r2(cy + h)}`
        + ` Q${r2(cx - bulge)} ${r2(cy)} ${r2(cx)} ${r2(cy - h)} Z"`
        + ' fill="var(--s1)" fill-opacity="0.13" stroke="var(--s1)" stroke-width="1.8"/>';
}

/** 오목렌즈(양오목) 모양. */
function lensConcave(cx, cy, h, w = 13, waist = 5) {
    return `<path d="M${r2(cx - w)} ${r2(cy - h)} L${r2(cx + w)} ${r2(cy - h)}`
        + ` Q${r2(cx + waist)} ${r2(cy)} ${r2(cx + w)} ${r2(cy + h)} L${r2(cx - w)} ${r2(cy + h)}`
        + ` Q${r2(cx - waist)} ${r2(cy)} ${r2(cx - w)} ${r2(cy - h)} Z"`
        + ' fill="var(--s1)" fill-opacity="0.13" stroke="var(--s1)" stroke-width="1.8"/>';
}

/** 굴절 벡터식. d 는 진행 방향(단위), N 은 경계면 바깥 법선(단위), eta = n1/n2. */
function refract(d, N, eta) {
    const c1 = -(N[0] * d[0] + N[1] * d[1]);
    const k = 1 - eta * eta * (1 - c1 * c1);
    if (k < 0) return null;                     // 전반사
    const c2 = Math.sqrt(k);
    return [eta * d[0] + (eta * c1 - c2) * N[0], eta * d[1] + (eta * c1 - c2) * N[1]];
}

/* ================================================================== *
 * 14장 — 기하광학
 * ================================================================== */

/* 1. 반사 법칙 — 각은 법선에서 잰다 */
add((() => {
    const W = 640, H = 356;
    const b = [txt(16, 26, '반사 법칙 — 각은 거울면이 아니라 법선에서 잰다', { cls: 'ink bold' })];
    const P = [168, 250], R = 142, th = 35 * D2R;
    b.push(hatch(48, 250, 292, 250, 1, 11, 8));
    b.push(line(P[0], P[1], P[0], 96, { c: 'grid', dash: '5 4' }));
    b.push(txt(P[0] + 7, 96, '법선', { cls: 'ink2', size: 'sm' }));
    b.push(ray(P[0] - R * Math.sin(th), P[1] - R * Math.cos(th), P[0], P[1], { c: 's1' }));
    b.push(ray(P[0], P[1], P[0] + R * Math.sin(th), P[1] - R * Math.cos(th), { c: 's2' }));
    b.push(arc(P[0], P[1], 50, 90, 125, 'θ~i'));
    b.push(arc(P[0], P[1], 78, 55, 90, 'θ~r'));
    b.push(txt(56, 128, '들어오는 빛', { cls: 'ink2', size: 'sm' }));
    b.push(txt(284, 128, '나가는 빛', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(168, 286, 'θ~i = θ~r', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(168, 308, '두 광선과 법선은 한 평면 위에 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽: 정반사와 난반사
    const drawBeam = (y0, rough) => {
        const out = [];
        const seed = rough ? [10, -14, 6] : [0, 0, 0];
        for (let i = 0; i < 3; i += 1) {
            const x = 372 + i * 54;
            out.push(ray(x - 46, y0 - 56, x, y0, { c: 's1', w: 1.8, at: 0.7 }));
            const a = (35 + seed[i]) * D2R;
            out.push(ray(x, y0, x + 62 * Math.sin(a), y0 - 62 * Math.cos(a), { c: 's2', w: 1.8, at: 0.7 }));
        }
        return out.join('');
    };
    b.push(txt(360, 70, '매끄러운 면 — 정반사', { cls: 'ink' }));
    b.push(hatch(348, 150, 592, 150, 1, 11, 7));
    b.push(drawBeam(150, false));
    b.push(txt(360, 232, '거친 면 — 난반사', { cls: 'ink' }));
    const zig = [];
    for (let x = 348; x <= 592; x += 16) zig.push([x, 312 + ((x / 16) % 2 ? 4 : -4)]);
    b.push(poly(zig, { c: 'ink2', w: 2 }));
    b.push(drawBeam(312, true));
    b.push(txt(16, 344, '거친 면에서는 점마다 법선 방향이 달라 반사광이 흩어진다. 반사 법칙이 깨진 것은 아니다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-reflection-law',
        title: '반사 법칙과 정반사·난반사',
        desc: '입사각과 반사각은 거울면이 아니라 법선에서 재며 서로 같다. 면이 거칠면 각 점의 법선 방향이 '
            + '제각각이라 반사광이 사방으로 흩어진다. 반사 법칙은 두 경우 모두에서 똑같이 성립한다.',
        svg: svg({ width: W, height: H, title: '반사 법칙', desc: '입사각과 반사각이 같다', body: b.join('') }),
    };
})());

/* 2. 굴절과 스넬 법칙 — 파장이 줄어들어 방향이 꺾인다 */
add((() => {
    const W = 640, H = 370;
    const yb = 208, n1 = 1.0, n2 = 1.5;
    const t1 = 45 * D2R, t2 = Math.asin(Math.sin(t1) * n1 / n2);
    const Q = [286, yb];
    const b = [txt(16, 26, '굴절 — 느린 매질에서 파장이 줄고, 그 때문에 방향이 꺾인다', { cls: 'ink bold' })];
    b.push(`<rect x="0" y="${yb}" width="${W}" height="${H - yb}" fill="var(--s1)" fill-opacity="0.08"/>`);
    b.push(line(24, yb, 520, yb, { c: 'ink2', w: 2 }));
    b.push(line(Q[0], yb - 116, Q[0], yb + 108, { c: 'grid', dash: '5 4' }));
    b.push(txt(Q[0] + 7, yb - 112, '법선', { cls: 'ink2', size: 'sm' }));

    // 파면: 경계 위 같은 간격 s 로 만나고, 매질마다 파장이 다르다
    const lam1 = 36, s = lam1 / Math.sin(t1);
    for (let k = 0; k <= 3; k += 1) {
        const Px = Q[0] - k * s;
        if (Px < 60) break;
        b.push(line(Px, yb, Px + 58 * Math.cos(t1), yb - 58 * Math.sin(t1), { c: 'ink2', w: 1.3 }));
        b.push(line(Px, yb, Px - 58 * Math.cos(t2), yb + 58 * Math.sin(t2), { c: 'ink2', w: 1.3 }));
    }
    b.push(ray(Q[0] - 150 * Math.sin(t1), yb - 150 * Math.cos(t1), Q[0], yb, { c: 's1', w: 2.4 }));
    b.push(ray(Q[0], yb, Q[0] + 132 * Math.sin(t2), yb + 132 * Math.cos(t2), { c: 's2', w: 2.4 }));
    b.push(line(Q[0], yb, Q[0] + 96 * Math.sin(t1), yb - 96 * Math.cos(t1), { c: 'grid', dash: '3 4', w: 1.4 }));
    b.push(txt(Q[0] + 100 * Math.sin(t1) + 4, yb - 96 * Math.cos(t1), '약하게 반사', { cls: 'ink2', size: 'sm' }));
    b.push(arc(Q[0], yb, 54, 90, 135, 'θ~1'));
    b.push(arc(Q[0], yb, 58, 270, 298, 'θ~2'));
    b.push(txt(24, 78, 'n~1 = 1.00 — 빠르다, 파장이 길다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(24, H - 44, 'n~2 = 1.50 — 느리다, 파장이 짧다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 104, 'n = c / v', { cls: 'ink bold' }));
    b.push(txt(540, 132, 'n~1 sin θ~1', { cls: 'ink' }));
    b.push(txt(540, 152, '= n~2 sin θ~2', { cls: 'ink' }));
    b.push(txt(540, 186, '진동수 f 는', { cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 202, '그대로다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 226, 'λ = v / f 이므로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 242, 'λ~2 = λ~1 / n~2', { cls: 'ink2', size: 'sm' }));
    b.push(txt(24, H - 16, '파면이 경계에 비스듬히 닿으면 먼저 닿은 쪽이 먼저 느려져 대열 전체가 꺾인다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-refraction-snell',
        title: '굴절과 스넬 법칙',
        desc: '느린 매질에서는 진동수가 그대로이고 속력이 줄므로 파장이 줄어든다. 파면이 경계에 비스듬히 '
            + '닿으면 먼저 닿은 쪽이 먼저 느려지고, 그 결과 진행 방향이 법선 쪽으로 꺾인다. '
            + '경계를 따라 잰 파면 간격이 양쪽에서 같아야 한다는 조건이 곧 스넬 법칙이다.',
        svg: svg({ width: W, height: H, title: '굴절과 스넬 법칙', desc: '파장이 줄면서 꺾이는 파면', body: b.join('') }),
    };
})());

/* 3. 전반사와 임계각, 광섬유 */
add((() => {
    const W = 640, H = 430;
    const yb = 152, nd = 1.5, deep = 300;
    const tc = Math.asin(1 / nd);
    const S = [104, 288];
    const b = [txt(16, 26, '전반사 — 임계각을 넘으면 굴절광이 사라진다', { cls: 'ink bold' })];
    b.push(`<rect x="0" y="${yb}" width="${W}" height="${deep - yb}" fill="var(--s1)" fill-opacity="0.08"/>`);
    b.push(line(24, yb, 616, yb, { c: 'ink2', w: 2 }));
    b.push(txt(24, 60, 'n~2 = 1.00 (공기)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(24, 176, 'n~1 = 1.50 (밀한 매질)', { cls: 'ink2', size: 'sm' }));

    const cases = [
        { th: 25 * D2R, tag: '작다' },
        { th: tc, tag: '= θ~c' },
        { th: 56 * D2R, tag: '크다' },
    ];
    for (const cs of cases) {
        const Px = S[0] + (S[1] - yb) * Math.tan(cs.th);
        b.push(ray(S[0], S[1], Px, yb, { c: 's1', w: 2 }));
        b.push(line(Px, yb - 74, Px, yb + 26, { c: 'grid', dash: '4 4', w: 1.2 }));
        const sinR = nd * Math.sin(cs.th);
        if (sinR < 0.995) {
            const tr = Math.asin(sinR);
            b.push(ray(Px, yb, Px + 84 * Math.sin(tr), yb - 84 * Math.cos(tr), { c: 's2', w: 2 }));
        } else if (sinR < 1.02) {
            b.push(ray(Px, yb, Px + 92, yb - 4, { c: 's2', w: 2 }));
        } else {
            b.push(txt(Px + 16, yb - 16, '굴절광 없음', { cls: 'ink2', size: 'sm' }));
        }
        const back = sinR >= 1.02 ? 2.6 : 1.1;
        b.push(ray(Px, yb, Px + 84 * Math.sin(cs.th), yb + 84 * Math.cos(cs.th), { c: 's3', w: back }));
        b.push(txt(Px, yb - 80, cs.tag, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(`<circle cx="${S[0]}" cy="${S[1]}" r="4" fill="var(--s1)"/>`);
    b.push(txt(S[0] - 8, S[1] + 6, '광원', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(452, 206, 'sin θ~c = n~2 / n~1', { cls: 'ink bold' }));
    b.push(txt(452, 228, 'n~1 = 1.50 → θ~c = 41.8°', { cls: 'ink2', size: 'sm' }));
    b.push(txt(452, 254, '입사각이 커질수록', { cls: 'ink2', size: 'sm' }));
    b.push(txt(452, 272, '굴절광은 수면에 눕고', { cls: 'ink2', size: 'sm' }));
    b.push(txt(452, 290, '반사광은 밝아진다', { cls: 'ink2', size: 'sm' }));

    // 광섬유
    b.push(txt(16, 340, '광섬유 — 코어 안에서 전반사를 되풀이한다', { cls: 'ink' }));
    const y1 = 362, y2 = 410;
    b.push(`<rect x="40" y="${y1}" width="520" height="${y2 - y1}" fill="var(--s1)" fill-opacity="0.08"/>`);
    b.push(line(40, y1, 560, y1, { c: 'ink2', w: 2 }));
    b.push(line(40, y2, 560, y2, { c: 'ink2', w: 2 }));
    const pts = [[40, y2 - 10]];
    let up = true;
    for (let x = 88; x <= 560; x += 48) { pts.push([x, up ? y1 : y2]); up = !up; }
    b.push(poly(pts, { c: 's2', w: 2 }));
    b.push(txt(568, y1 - 6, '클래딩', { cls: 'ink2', size: 'sm' }));
    b.push(txt(568, y1 + 10, '(n 작다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(48, y1 - 8, '코어 (n 크다)', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-total-internal',
        title: '임계각과 전반사, 광섬유',
        desc: '밀한 매질에서 소한 매질로 갈 때 입사각을 키우면 굴절각이 먼저 90도에 이른다. 그 입사각이 '
            + '임계각이고, 그보다 크면 굴절광이 아예 없어 빛이 100% 되돌아온다. 광섬유는 이 반사를 '
            + '되풀이해 빛을 가둔 채 실어 나른다.',
        svg: svg({ width: W, height: H, title: '전반사와 임계각', desc: '임계각을 넘으면 빛이 전부 되돌아온다', body: b.join('') }),
    };
})());

/* 4. 분산 — 굴절률이 파장에 따라 다르다 */
add((() => {
    const W = 620, H = 340;
    const s = 168, a = s / 2, hh = s * Math.sqrt(3) / 2;
    const T = [252, 58], BL = [T[0] - a, T[1] + hh], BR = [T[0] + a, T[1] + hh];
    const b = [txt(16, 26, '분산 — 파장이 짧을수록 더 크게 꺾인다', { cls: 'ink bold' })];
    b.push(poly([T, BL, BR], { c: 'ink2', w: 1.8, close: true, fill: 's1', op: 0.08 }));
    const NL = [-Math.sqrt(3) / 2, -0.5];       // 왼쪽 면 바깥 법선
    const NR = [Math.sqrt(3) / 2, -0.5];        // 오른쪽 면 바깥 법선
    const enter = [T[0] - a * 0.5, T[1] + hh * 0.5];
    // 최소편각 근처가 되도록 살짝 올려 쏜다 (입사각 약 48.6°)
    const phi = -18.6 * D2R, din = [Math.cos(phi), Math.sin(phi)];
    const cols = [
        { n: 1.44, c: 's2', label: '빨강 (긴 파장, n 작다)' },
        { n: 1.52, c: 's3', label: '초록' },
        { n: 1.60, c: 's1', label: '파랑 (짧은 파장, n 크다)' },
    ];
    b.push(ray(enter[0] - 176 * din[0], enter[1] - 176 * din[1], enter[0], enter[1], { c: 'ink2', w: 2.4 }));
    b.push(txt(enter[0] - 172 * din[0], enter[1] - 176 * din[1] - 10, '백색광', { cls: 'ink2', size: 'sm' }));
    for (const col of cols) {
        const d1 = refract(din, NL, 1 / col.n);
        // 오른쪽 면과의 교점: (P + t d1 - T)·NR = 0
        const w = (T[0] - enter[0]) * NR[0] + (T[1] - enter[1]) * NR[1];
        const t = w / (d1[0] * NR[0] + d1[1] * NR[1]);
        const hit = [enter[0] + t * d1[0], enter[1] + t * d1[1]];
        const d2 = refract(d1, [-NR[0], -NR[1]], col.n);
        b.push(line(enter[0], enter[1], hit[0], hit[1], { c: col.c, w: 2 }));
        if (d2) {
            const L = 250;
            b.push(ray(hit[0], hit[1], hit[0] + L * d2[0], hit[1] + L * d2[1], { c: col.c, w: 2, at: 0.75 }));
        }
    }
    let ly = 92;
    for (const col of cols) {
        b.push(line(430, ly - 4, 454, ly - 4, { c: col.c, w: 3 }));
        b.push(txt(460, ly, col.label, { cls: 'ink2', size: 'sm' }));
        ly += 20;
    }
    b.push(txt(16, H - 32, '유리의 굴절률은 파장에 따라 조금 달라진다. 실제로 벌어지는 각은 1° 남짓이라',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 14, '이 그림에서는 알아보기 쉽게 굴절률 차이를 크게 잡았다(1.44 / 1.52 / 1.60).',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-dispersion-prism',
        title: '프리즘의 분산',
        desc: '굴절률이 파장에 따라 다르기 때문에 백색광이 프리즘을 지나면 색깔별로 다른 각으로 꺾여 갈라진다. '
            + '보통의 유리에서는 파장이 짧은 파랑 쪽이 더 크게 꺾인다. 무지개도 물방울에서 같은 일이 일어난 것이다.',
        svg: svg({ width: W, height: H, title: '프리즘의 분산', desc: '파장에 따라 다른 각으로 꺾이는 빛', body: b.join('') }),
    };
})());

/* 5. 평면거울의 상 */
add((() => {
    const W = 620, H = 340;
    const mx = 300, axis = 210;
    const O = [180, 120], I = [mx + (mx - O[0]), O[1]];
    const E = [148, 274], pupil = 15;
    const b = [txt(16, 26, '평면거울 — 상은 거울 뒤 같은 거리에 생긴다', { cls: 'ink bold' })];
    b.push(hatch(mx, 62, mx, 300, -1, 11, 8));
    b.push(line(70, axis, 560, axis, { c: 'grid', dash: '5 4', w: 1.2 }));
    b.push(upright(O[0], axis, axis - O[1], { c: 's3', label: '물체' }));
    b.push(upright(I[0], axis, axis - I[1], { c: 's2', label: '상 (허상)', dash: '6 4' }));
    // 상에서 눈의 위아래 끝으로 가는 두 직선이 거울과 만나는 곳이 실제 반사점이다
    for (const off of [-pupil, pupil]) {
        const Ey = E[1] + off;
        const t = (mx - I[0]) / (E[0] - I[0]);
        const hy = I[1] + t * (Ey - I[1]);
        b.push(ray(O[0], O[1], mx, hy, { c: 's1', w: 1.8, at: 0.7 }));
        b.push(ray(mx, hy, E[0], Ey, { c: 's1', w: 1.8, at: 0.7 }));
        b.push(line(mx, hy, I[0], I[1], { c: 'grid', dash: '4 4', w: 1.3 }));
    }
    b.push(`<circle cx="${E[0]}" cy="${E[1]}" r="15" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`);
    b.push(`<circle cx="${E[0]}" cy="${E[1]}" r="5" fill="var(--ink2)"/>`);
    b.push(txt(E[0] - 22, E[1] + 5, '눈', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(span(O[0], 306, mx, 306, 'p', { c: 'ink2' }));
    b.push(span(mx, 306, I[0], 306, '|q| = p', { c: 'ink2' }));
    b.push(txt(348, 240, '빛은 거울 뒤로 가지 않는다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(348, 258, '되돌아간 광선을 뒤로 늘였을 때', { cls: 'ink2', size: 'sm' }));
    b.push(txt(348, 276, '만나는 자리일 뿐이다 — 허상', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, 'q = −p, 배율 m = +1. 크기가 같고 똑바로 서 있으며, 앞뒤가 뒤집힌다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-plane-mirror',
        title: '평면거울이 만드는 허상',
        desc: '눈에 들어온 두 광선을 거울 뒤로 곧게 늘이면 한 점에서 만난다. 그 점이 상이며, 빛이 실제로 '
            + '모인 자리가 아니므로 허상이다. 물체와 거울 사이 거리만큼 거울 뒤에 있고 크기는 같다.',
        svg: svg({ width: W, height: H, title: '평면거울의 상', desc: '거울 뒤 같은 거리의 허상', body: b.join('') }),
    };
})());

/* 6. 부호 규약 — 빛이 실제로 지나가는 쪽이 + */
add((() => {
    const W = 640, H = 392;
    const cx = 300;
    const b = [txt(16, 26, '부호 규약 — 빛이 실제로 지나가는 쪽을 +로 잡는다', { cls: 'ink bold' })];

    // 렌즈
    let y = 130;
    b.push(txt(16, 58, '렌즈', { cls: 'ink bold' }));
    b.push(arrow(66, 78, 150, 78, { c: 's1', w: 1.8 }));
    b.push(txt(158, 82, '빛이 이쪽에서 온다', { cls: 'ink2', size: 'sm' }));
    b.push(line(60, y, 590, y, { c: 'grid', dash: '5 4', w: 1.2 }));
    b.push(lensConvex(cx, y, 46));
    b.push(span(170, y + 44, cx, y + 44, 'p > 0 (물체)', { c: 'ink2' }));
    b.push(span(cx, y + 44, 470, y + 44, 'q > 0 : 실상', { c: 'ink2' }));
    b.push(txt(170, y + 74, 'q < 0 : 허상 (물체 쪽)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(484, y - 34, 'f > 0 볼록(모으는 렌즈)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(484, y - 16, 'f < 0 오목(퍼뜨리는 렌즈)', { cls: 'ink2', size: 'sm' }));

    // 거울
    y = 306;
    b.push(txt(16, 240, '거울', { cls: 'ink bold' }));
    b.push(arrow(66, 262, 150, 262, { c: 's1', w: 1.8 }));
    b.push(txt(60, 242, '빛이 이쪽에서 와서 되돌아간다', { cls: 'ink2', size: 'sm' }));
    b.push(line(60, y, 590, y, { c: 'grid', dash: '5 4', w: 1.2 }));
    b.push(hatch(cx, y - 48, cx, y + 34, -1, 10, 8));
    b.push(span(170, y + 46, cx, y + 46, 'p > 0, q > 0 : 실상 (거울 앞)', { c: 'ink2' }));
    b.push(txt(322, y + 22, 'q < 0 : 허상 (거울 뒤)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(484, y - 34, 'f > 0 오목거울(모으는 거울)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(484, y - 16, 'f < 0 볼록거울(퍼뜨리는 거울)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, '렌즈는 빛이 통과해 나가는 뒤쪽이 +, 거울은 빛이 되돌아 나오는 앞쪽이 +다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-sign-convention',
        title: '거울과 렌즈의 부호 규약',
        desc: '거울과 렌즈에서 상거리 q 의 + 방향이 서로 반대쪽인 것처럼 보이지만 규칙은 하나다. '
            + '빛이 실제로 지나가는 쪽이 +이고, 그쪽에 상이 맺히면 실상이다. 반대쪽이면 허상이고 q 가 음수다.',
        svg: svg({ width: W, height: H, title: '부호 규약', desc: '빛이 실제로 지나가는 쪽이 +', body: b.join('') }),
    };
})());

/* 7. 볼록렌즈의 광선 작도 (f = 2, p = 3 → q = 6, m = −2) */
add((() => {
    const W = 640, H = 330;
    const unit = 52, cx = 214, yAxis = 150, f = 2, p = 3, q = 6, h = 1;
    const X = v => cx + v * unit, Y = v => yAxis - v * unit;
    const hi = -h * q / p;
    const b = [txt(16, 26, '볼록렌즈 — 세 광선이 만나는 곳에 상이 생긴다', { cls: 'ink bold' })];
    b.push(line(X(-3.6), yAxis, X(7), yAxis, { c: 'grid', dash: '5 4', w: 1.2 }));
    b.push(lensConvex(cx, yAxis, 74));
    for (const [v, nm] of [[-f, 'F'], [f, "F'"]]) {
        b.push(`<circle cx="${r2(X(v))}" cy="${r2(yAxis)}" r="3.2" fill="var(--ink2)"/>`);
        b.push(txt(X(v), yAxis + 20, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(upright(X(-p), yAxis, h * unit, { c: 's3', label: '물체' }));
    // (1) 축에 나란히 → 반대쪽 초점을 지난다
    b.push(ray(X(-p), Y(h), cx, Y(h), { c: 's1', w: 1.8, at: 0.7 }));
    b.push(ray(cx, Y(h), X(q), Y(hi), { c: 's1', w: 1.8, at: 0.6 }));
    // (2) 렌즈 중심 → 그대로 직진
    b.push(ray(X(-p), Y(h), cx, yAxis, { c: 's2', w: 1.8, at: 0.7 }));
    b.push(ray(cx, yAxis, X(q), Y(hi), { c: 's2', w: 1.8, at: 0.6 }));
    // (3) 앞쪽 초점을 지나 → 축에 나란히 나간다
    b.push(ray(X(-p), Y(h), cx, Y(hi), { c: 's3', w: 1.8, at: 0.7 }));
    b.push(ray(cx, Y(hi), X(q), Y(hi), { c: 's3', w: 1.8, at: 0.6 }));
    b.push(upright(X(q), yAxis, hi * unit, { c: 'ink2' }));
    b.push(txt(X(q) + 8, Y(hi) + 4, '상 (실상, 거꾸로)', { cls: 'ink2', size: 'sm' }));
    b.push(span(X(-p), yAxis + 62, cx, yAxis + 62, 'p = 3f/2', { c: 'ink2' }));
    b.push(span(cx, yAxis + 62, X(q), yAxis + 62, 'q = 3f', { c: 'ink2' }));
    let ty = 262;
    for (const [c, s] of [['s1', '① 축에 나란히 들어온 광선은 반대쪽 초점 F′ 을 지난다'],
        ['s2', '② 렌즈 중심을 지나는 광선은 꺾이지 않는다'],
        ['s3', '③ 앞쪽 초점 F 를 지나온 광선은 축에 나란히 나간다']]) {
        b.push(line(20, ty - 4, 44, ty - 4, { c, w: 3 }));
        b.push(txt(52, ty, s, { cls: 'ink2', size: 'sm' }));
        ty += 20;
    }
    b.push(txt(W - 14, 60, '1/f = 1/p + 1/q', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(W - 14, 80, 'm = −q/p = −2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-lens-convex-rays',
        title: '볼록렌즈의 광선 작도',
        desc: '물체를 초점 바깥에 두면 세 개의 주요 광선이 렌즈 반대쪽 한 점에서 실제로 만난다. '
            + '그 자리에 거꾸로 선 실상이 생기며, 그림에서 읽은 위치는 렌즈 공식이 주는 값과 정확히 같다.',
        svg: svg({ width: W, height: H, title: '볼록렌즈의 광선 작도', desc: '세 주요 광선과 실상', body: b.join('') }),
    };
})());

/* 8. 오목렌즈의 광선 작도 (f = −2, p = 3 → q = −1.2, m = +0.4) */
add((() => {
    const W = 620, H = 320;
    const unit = 64, cx = 380, yAxis = 148, f = -2, p = 3, h = 1;
    const q = 1 / (1 / f - 1 / p);                     // = −1.2
    const hi = -h * q / p;                             // = +0.4
    const X = v => cx + v * unit, Y = v => yAxis - v * unit;
    const b = [txt(16, 26, '오목렌즈 — 상은 언제나 작고 똑바로 선 허상이다', { cls: 'ink bold' })];
    b.push(line(X(-3.6), yAxis, X(1.9), yAxis, { c: 'grid', dash: '5 4', w: 1.2 }));
    b.push(lensConcave(cx, yAxis, 70));
    for (const [v, nm] of [[f, 'F'], [-f, "F'"]]) {
        b.push(`<circle cx="${r2(X(v))}" cy="${r2(yAxis)}" r="3.2" fill="var(--ink2)"/>`);
        b.push(txt(X(v), yAxis + 20, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(upright(X(-p), yAxis, h * unit, { c: 's3', label: '물체' }));
    // (1) 축에 나란히 → 앞쪽 초점에서 나온 것처럼 퍼진다
    b.push(ray(X(-p), Y(h), cx, Y(h), { c: 's1', w: 1.8, at: 0.7 }));
    b.push(ray(cx, Y(h), X(1.7), Y(h + (1.7 / 2) * h), { c: 's1', w: 1.8, at: 0.6 }));
    b.push(line(cx, Y(h), X(f), yAxis, { c: 's1', w: 1.4, dash: '5 4' }));
    // (2) 중심 직진
    b.push(ray(X(-p), Y(h), cx, yAxis, { c: 's2', w: 1.8, at: 0.7 }));
    b.push(ray(cx, yAxis, X(1.7), Y(-h * 1.7 / p), { c: 's2', w: 1.8, at: 0.6 }));
    b.push(line(cx, yAxis, X(q), Y(hi), { c: 's2', w: 1.4, dash: '5 4' }));
    // (3) 반대쪽 초점을 향해 들어간 광선 → 축에 나란히 나간다
    b.push(ray(X(-p), Y(h), cx, Y(hi), { c: 's3', w: 1.8, at: 0.7 }));
    b.push(ray(cx, Y(hi), X(1.7), Y(hi), { c: 's3', w: 1.8, at: 0.6 }));
    b.push(line(cx, Y(hi), X(q), Y(hi), { c: 's3', w: 1.4, dash: '5 4' }));
    b.push(upright(X(q), yAxis, hi * unit, { c: 'ink2' }));
    b.push(txt(X(q), yAxis + 34, '상 (허상, 작고 똑바로)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 54, '점선은 나가는 광선을 뒤로 늘인 것이다. 빛이 실제로 지나가지 않으므로',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 36, '스크린을 놓아도 아무것도 맺히지 않는다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 12, 'f = −2, p = 3 → q = −1.2, m = +0.4', { cls: 'ink bold' }));
    return {
        name: 'opt-lens-concave-rays',
        title: '오목렌즈의 광선 작도',
        desc: '오목렌즈를 지난 광선은 퍼져 나가므로 실제로 만나지 않는다. 뒤로 늘인 연장선이 만나는 자리에 '
            + '허상이 생기고, 물체가 어디 있든 언제나 물체보다 작고 똑바로 선 허상이 된다.',
        svg: svg({ width: W, height: H, title: '오목렌즈의 광선 작도', desc: '퍼지는 광선과 축소 허상', body: b.join('') }),
    };
})());

/* 9. 오목거울의 광선 작도 (f = 2, p = 5 → q = 10/3, m = −2/3) */
add((() => {
    const W = 620, H = 330;
    const unit = 54, vx = 500, yAxis = 158, f = 2, p = 5, h = 1;
    const q = 1 / (1 / f - 1 / p), hi = -h * q / p;
    const X = v => vx - v * unit, Y = v => yAxis - v * unit;   // 물체 쪽이 왼쪽(+)
    const R = 2 * f;
    const b = [txt(16, 26, '오목거울 — 초점 바깥의 물체는 거꾸로 선 실상을 만든다', { cls: 'ink bold' })];
    b.push(line(X(6.4), yAxis, vx + 26, yAxis, { c: 'grid', dash: '5 4', w: 1.2 }));
    // 거울: 곡률중심이 X(R) 인 원의 일부
    const Rpx = R * unit, ccx = X(R);
    const half = Math.asin(96 / Rpx) / D2R;
    b.push(poly(arcPts(ccx, yAxis, Rpx, -half, half), { c: 'ink2', w: 2.4 }));
    for (const [v, nm] of [[f, 'F'], [R, 'C']]) {
        b.push(`<circle cx="${r2(X(v))}" cy="${r2(yAxis)}" r="3.2" fill="var(--ink2)"/>`);
        b.push(txt(X(v), yAxis + 20, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(upright(X(p), yAxis, h * unit, { c: 's3', label: '물체' }));
    // ① 축에 나란히 → 초점을 지나 반사
    b.push(ray(X(p), Y(h), vx, Y(h), { c: 's1', w: 1.8, at: 0.6 }));
    b.push(ray(vx, Y(h), X(q + 1.2), Y(hi * (q + 1.2) / q), { c: 's1', w: 1.8, at: 0.6 }));
    // ② 초점을 지나 → 축에 나란히 반사
    b.push(ray(X(p), Y(h), vx, Y(hi), { c: 's2', w: 1.8, at: 0.6 }));
    b.push(ray(vx, Y(hi), X(q + 1.2), Y(hi), { c: 's2', w: 1.8, at: 0.6 }));
    // ③ 거울 꼭짓점 → 광축을 대칭축으로 반사
    b.push(ray(X(p), Y(h), vx, yAxis, { c: 's3', w: 1.8, at: 0.6 }));
    b.push(ray(vx, yAxis, X(q + 1.2), Y(-h * (q + 1.2) / p), { c: 's3', w: 1.8, at: 0.6 }));
    b.push(upright(X(q), yAxis, hi * unit, { c: 'ink2' }));
    b.push(txt(X(q) + 8, Y(hi) + 14, '상', { cls: 'ink bold' }));
    b.push(txt(16, H - 78, 'f = R/2 = 2, p = 5 → q = 3.33, m = −0.67', { cls: 'ink bold' }));
    b.push(txt(16, H - 56, '상은 F 와 C 사이에 거꾸로, 물체보다 작게 맺힌다. 스크린을 놓으면 실제로 비친다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 34, '① 축에 나란히 들어오면 초점을 지나 반사하고', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 14, '② 초점을 지나 들어오면 축에 나란히 반사한다. ③ 꼭짓점에서는 광축 기준으로 대칭이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-mirror-concave-rays',
        title: '오목거울의 광선 작도',
        desc: '오목거울은 초점거리가 곡률반지름의 절반이다. 물체가 초점 바깥에 있으면 반사광이 거울 앞에서 '
            + '실제로 만나 거꾸로 선 실상을 만든다. 물체가 곡률중심 바깥이면 상은 물체보다 작다.',
        svg: svg({ width: W, height: H, title: '오목거울의 광선 작도', desc: '반사광이 만나 생기는 실상', body: b.join('') }),
    };
})());

/* 10. 볼록거울의 광선 작도 (f = −2, p = 4 → q = −4/3, m = +1/3) */
add((() => {
    const W = 620, H = 320;
    const unit = 52, vx = 396, yAxis = 152, f = -2, p = 4, h = 1;
    const q = 1 / (1 / f - 1 / p), hi = -h * q / p;
    const X = v => vx - v * unit, Y = v => yAxis - v * unit;
    const b = [txt(16, 26, '볼록거울 — 상은 언제나 작고 똑바로 선 허상이다', { cls: 'ink bold' })];
    b.push(line(X(5), yAxis, X(-3.4), yAxis, { c: 'grid', dash: '5 4', w: 1.2 }));
    const Rpx = 2 * Math.abs(f) * unit, ccx = X(2 * f);
    const half = Math.asin(92 / Rpx) / D2R;
    b.push(poly(arcPts(ccx, yAxis, Rpx, 180 - half, 180 + half), { c: 'ink2', w: 2.4 }));
    for (const [v, nm] of [[f, 'F'], [2 * f, 'C']]) {
        b.push(`<circle cx="${r2(X(v))}" cy="${r2(yAxis)}" r="3.2" fill="var(--ink2)"/>`);
        b.push(txt(X(v), yAxis + 20, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(upright(X(p), yAxis, h * unit, { c: 's3', label: '물체' }));
    b.push(ray(X(p), Y(h), vx, Y(h), { c: 's1', w: 1.8, at: 0.6 }));
    b.push(ray(vx, Y(h), X(p), Y(h + (p / 2) * h), { c: 's1', w: 1.8, at: 0.6 }));
    b.push(line(vx, Y(h), X(f), yAxis, { c: 's1', w: 1.3, dash: '5 4' }));
    b.push(ray(X(p), Y(h), vx, Y(hi), { c: 's2', w: 1.8, at: 0.6 }));
    b.push(ray(vx, Y(hi), X(p), Y(hi), { c: 's2', w: 1.8, at: 0.6 }));
    b.push(line(vx, Y(hi), X(q), Y(hi), { c: 's2', w: 1.3, dash: '5 4' }));
    b.push(ray(X(p), Y(h), vx, yAxis, { c: 's3', w: 1.8, at: 0.6 }));
    b.push(ray(vx, yAxis, X(p), Y(-h), { c: 's3', w: 1.8, at: 0.6 }));
    b.push(line(vx, yAxis, X(q), Y(hi), { c: 's3', w: 1.3, dash: '5 4' }));
    b.push(upright(X(q), yAxis, hi * unit, { c: 'ink2' }));
    b.push(txt(X(q) + 10, Y(hi) - 8, '상 (허상)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 78, 'f = −2, p = 4 → q = −1.33, m = +0.33', { cls: 'ink bold' }));
    b.push(txt(16, H - 54, '반사광은 퍼져 나간다. 뒤로 늘인 점선이 거울 뒤에서 만나는 자리가 상이다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 30, '물체가 아무리 가까워도 |q| < |f| 이므로 상은 늘 작다. 그 대신 넓은 범위가 한 번에 보인다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 8, '편의점 감시거울과 차량 사이드미러가 이것이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-mirror-convex-rays',
        title: '볼록거울의 광선 작도',
        desc: '볼록거울에서는 반사광이 퍼지므로 실제로 만나지 않는다. 연장선이 거울 뒤에서 만나 작고 똑바로 선 '
            + '허상을 만든다. 상이 작은 대신 훨씬 넓은 범위가 한 화면에 들어온다.',
        svg: svg({ width: W, height: H, title: '볼록거울의 광선 작도', desc: '거울 뒤에 생기는 축소 허상', body: b.join('') }),
    };
})());

/* 11. 이중슬릿 — 경로차와 무늬 */
add((() => {
    const W = 660, H = 352;
    const b = [txt(16, 26, '이중슬릿 — 두 길의 길이 차이가 밝고 어두움을 정한다', { cls: 'ink bold' })];
    const S1 = [150, 148], S2 = [150, 244], d = S2[1] - S1[1];
    const th = 30 * D2R, u = [Math.cos(th), -Math.sin(th)];
    b.push(line(150, 62, 150, S1[1] - 12, { c: 'ink2', w: 4 }));
    b.push(line(150, S1[1] + 12, 150, S2[1] - 12, { c: 'ink2', w: 4 }));
    b.push(line(150, S2[1] + 12, 150, 318, { c: 'ink2', w: 4 }));
    b.push(ray(84, S1[1], 144, S1[1], { c: 'grid', w: 1.6 }));
    b.push(ray(84, S2[1], 144, S2[1], { c: 'grid', w: 1.6 }));
    const L = 190;
    b.push(ray(S1[0], S1[1], S1[0] + L * u[0], S1[1] + L * u[1], { c: 's1', w: 2 }));
    b.push(ray(S2[0], S2[1], S2[0] + L * u[0], S2[1] + L * u[1], { c: 's1', w: 2 }));
    const dot = d * Math.sin(th);
    const F = [S2[0] + dot * u[0], S2[1] + dot * u[1]];
    b.push(line(S1[0], S1[1], F[0], F[1], { c: 'grid', w: 1.4, dash: '4 3' }));
    b.push(line(S2[0], S2[1], F[0], F[1], { c: 's2', w: 3.6 }));
    b.push(span(126, S1[1], 126, S2[1], null, { c: 'ink2' }));
    b.push(txt(118, (S1[1] + S2[1]) / 2 + 4, 'd', { anchor: 'end', cls: 'ink' }));
    b.push(line(S2[0], S2[1], S2[0] + 120, S2[1], { c: 'grid', dash: '4 4', w: 1.2 }));
    b.push(arc(S2[0], S2[1], 74, 0, 30, 'θ'));
    b.push(txt(176, 276, '경로차 = d sin θ', { cls: 'ink' }));
    b.push(txt(320, 244, '보강(밝다) : d sin θ = mλ', { cls: 'ink2', size: 'sm' }));
    b.push(txt(320, 266, '상쇄(어둡다) : d sin θ = (m + 1/2) λ', { cls: 'ink2', size: 'sm' }));
    b.push(txt(320, 288, 'm = 0, ±1, ±2, …', { cls: 'ink2', size: 'sm' }));

    // 스크린과 무늬
    const sx = 546, yc = 176, dy = 44;
    b.push(line(sx, 56, sx, 300, { c: 'ink2', w: 2 }));
    b.push(txt(sx + 4, 50, '스크린', { cls: 'ink2', size: 'sm' }));
    for (let m = -2; m <= 2; m += 1) {
        const y = yc - m * dy;
        b.push(`<rect x="${sx + 4}" y="${r2(y - 7)}" width="32" height="14" rx="3" fill="var(--s1)" fill-opacity="${m === 0 ? 0.9 : 0.6}"/>`);
        b.push(txt(sx + 44, y + 4, `m = ${m}`, { cls: 'ink2', size: 'sm' }));
    }
    b.push(span(sx + 20, yc, sx + 20, yc - dy, null, { c: 'ink2' }));
    b.push(txt(16, H - 12, '스크린이 멀면 sin θ ≈ y / L 이므로 무늬 간격은 Δy = λL / d 로 고르게 벌어진다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-double-slit',
        title: '이중슬릿의 경로차와 간섭무늬',
        desc: '두 슬릿에서 스크린의 같은 점까지 가는 두 길의 길이 차이가 d sin θ 다. 이 차이가 파장의 '
            + '정수배이면 마루끼리 겹쳐 밝고, 반파장의 홀수배이면 마루와 골이 겹쳐 어둡다. '
            + '스크린이 멀면 무늬는 λL/d 간격으로 고르게 늘어선다.',
        svg: svg({ width: W, height: H, title: '이중슬릿 간섭', desc: '경로차 d sin θ 와 무늬 간격', body: b.join('') }),
    };
})());

/* 12. 단일슬릿 회절 — 좁을수록 넓게 퍼진다 */
add((() => {
    const W = 600, H = 330;
    const g = frame({ xRange: [-1, 1], yRange: [0, 1.15], box: { x: 62, y: 60, w: 470, h: 200 } });
    const I = (s, r) => {
        const beta = Math.PI * r * s;
        if (Math.abs(beta) < 1e-6) return 1;
        return (Math.sin(beta) / beta) ** 2;
    };
    const b = [
        txt(16, 26, '단일슬릿 회절 — 슬릿이 좁을수록 넓게 퍼진다', { cls: 'ink bold' }),
        g.axes({ xLabel: 'sin θ', yLabel: 'I / I~0', xTicks: [-1, -0.5, 0, 0.5, 1], yTicks: [] }),
        g.guide([-1, 0.5], [1, 0.5]), g.guide([-1, 1], [1, 1]),
        txt(56, g.Y(0.5) + 4, '0.5', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(56, g.Y(1) + 4, '1', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.curve(s => I(s, 4), { cls: 's1' }),
        g.curve(s => I(s, 2), { cls: 's2', dash: '6 4' }),
    ];
    for (const s of [-0.25, 0.25]) b.push(g.guide([s, 0], [s, 0.9]));
    b.push(g.label([0.25, 0.94], 'a = 4λ 의 첫 어두운 점', { dx: 4, cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.5, 0.42], 'a = 2λ', { dx: 6, cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 286, '어두운 점 : a sin θ = mλ  (m = ±1, ±2, … — m = 0 은 없다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 308, '중앙 극대의 반각폭 sin θ ≈ λ / a. 슬릿 폭 a 가 파장에 가까워질수록 크게 퍼진다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-single-slit',
        title: '단일슬릿 회절의 세기 분포',
        desc: '슬릿 하나만 지나도 빛은 퍼진다. 중앙 극대가 가장 밝고 폭이 나머지의 두 배이며, '
            + '어두운 점은 a sin θ = mλ 에 나타난다. 슬릿 폭 a 가 파장에 가까울수록 중앙 극대가 넓어진다.',
        svg: svg({ width: W, height: H, title: '단일슬릿 회절', desc: '슬릿 폭과 퍼짐의 관계', body: b.join('') }),
    };
})());

/* 13. 편광 — 말뤼스 법칙 */
add((() => {
    const W = 640, H = 350;
    const b = [txt(16, 26, '편광 — 두 번째 편광판은 각도에 따라 세기를 cos²θ 로 줄인다', { cls: 'ink bold' })];
    const yc = 128;
    const plate = (x, ang, label) => {
        const out = [`<rect x="${x - 16}" y="${yc - 56}" width="32" height="112" rx="4" fill="var(--s1)" fill-opacity="0.12" stroke="var(--s1)" stroke-width="1.6"/>`];
        for (let k = -2; k <= 2; k += 1) {
            const off = k * 11;
            const a = ang * D2R;
            out.push(line(x + off * Math.cos(a) - 44 * Math.sin(a), yc + off * Math.sin(a) + 44 * Math.cos(a),
                x + off * Math.cos(a) + 44 * Math.sin(a), yc + off * Math.sin(a) - 44 * Math.cos(a),
                { c: 'grid', w: 1.4 }));
        }
        out.push(txt(x, yc + 78, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    b.push(ray(40, yc, 154, yc, { c: 'ink2', w: 2.4 }));
    for (const k of [-3, -1.5, 0, 1.5, 3]) {
        const a = (k * 30) * D2R;
        b.push(line(70 - 12 * Math.cos(a), yc - 12 * Math.sin(a), 70 + 12 * Math.cos(a), yc + 12 * Math.sin(a), { c: 'grid', w: 1.2 }));
    }
    b.push(txt(40, yc - 40, '무편광 (I~0)', { cls: 'ink2', size: 'sm' }));
    b.push(plate(180, 0, '편광판 1 (세로)'));
    b.push(ray(200, yc, 322, yc, { c: 's2', w: 2.4 }));
    b.push(txt(212, yc - 26, 'I = I~0 / 2', { cls: 'ink' }));
    b.push(plate(348, 45, '편광판 2 (45° 기울임)'));
    b.push(ray(368, yc, 470, yc, { c: 's3', w: 2.4 }));
    b.push(txt(378, yc - 26, 'I = (I~0/2) cos²θ', { cls: 'ink' }));
    b.push(txt(500, yc - 24, '무편광은 절반이', { cls: 'ink2', size: 'sm' }));
    b.push(txt(500, yc - 6, '깎여 나간다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(500, yc + 20, '편광된 빛은', { cls: 'ink2', size: 'sm' }));
    b.push(txt(500, yc + 38, 'cos²θ 배', { cls: 'ink2', size: 'sm' }));
    const g = frame({ xRange: [0, 90], yRange: [0, 1.1], box: { x: 76, y: 232, w: 300, h: 84 } });
    b.push(g.axes({ xLabel: 'θ (°)', yLabel: 'I / I~입사', xTicks: [0, 30, 45, 60, 90], yTicks: [0, 0.5, 1] }));
    b.push(g.curve(t => Math.cos(t * D2R) ** 2, { cls: 's1' }));
    b.push(g.dot([45, 0.5], { cls: 'f2' }));
    b.push(txt(418, 262, '말뤼스 법칙 I = I~입사 cos²θ', { cls: 'ink bold' }));
    b.push(txt(418, 286, '45° 에서 정확히 절반이 남는다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(418, 306, '편광은 횡파에서만 생기는 현상이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'opt-polarization',
        title: '편광판과 말뤼스 법칙',
        desc: '무편광 빛이 첫 편광판을 지나면 세기가 절반이 되고 한 방향으로만 진동하는 빛이 된다. '
            + '두 번째 편광판은 두 축이 이루는 각 θ 에 대해 cos²θ 배로 줄인다. '
            + '빛이 횡파이기 때문에 생기는 현상이며, 종파인 소리에는 편광이 없다.',
        svg: svg({ width: W, height: H, title: '편광과 말뤼스 법칙', desc: '편광판 두 장과 cos²θ 곡선', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 15장 — 현대물리
 * ================================================================== */

/* 14. 빛시계 — 시간 팽창을 피타고라스 정리로 */
add((() => {
    const W = 680, H = 350;
    const b = [txt(16, 26, '빛시계 — 옆에서 보면 빛이 더 먼 길을 간다', { cls: 'ink bold' })];
    // 왼쪽: 정지한 시계
    const x0 = 120, yT = 96, yB = 256;
    b.push(hatch(x0 - 40, yT, x0 + 40, yT, -1, 10, 7));
    b.push(hatch(x0 - 40, yB, x0 + 40, yB, 1, 10, 7));
    b.push(ray(x0 - 8, yB, x0 - 8, yT, { c: 's1', w: 2.2 }));
    b.push(ray(x0 + 8, yT, x0 + 8, yB, { c: 's1', w: 2.2 }));
    b.push(span(x0 + 54, yT, x0 + 54, yB, null, { c: 'ink2' }));
    b.push(txt(x0 + 62, (yT + yB) / 2 + 4, 'L', { cls: 'ink' }));
    b.push(txt(x0, 292, '시계와 함께 있는 사람', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(x0, 314, 'Δt~0 = 2L / c', { anchor: 'middle', cls: 'ink bold' }));

    // 오른쪽: 움직이는 시계
    const a = 376, mid = 486, c2 = 596;
    for (const [xx, op] of [[a, 0.35], [mid, 0.35], [c2, 1]]) {
        b.push(`<g opacity="${op}">${hatch(xx - 34, yT, xx + 34, yT, -1, 10, 7)}${hatch(xx - 34, yB, xx + 34, yB, 1, 10, 7)}</g>`);
    }
    b.push(ray(a, yB, mid, yT, { c: 's1', w: 2.2 }));
    b.push(ray(mid, yT, c2, yB, { c: 's1', w: 2.2 }));
    b.push(line(mid, yT, mid, yB, { c: 's3', w: 2.2 }));
    b.push(line(a, yB, c2, yB, { c: 's2', w: 2.6 }));
    b.push(`<path fill="none" stroke="var(--ink2)" stroke-width="1.2" d="M${mid - 14} ${yB} V${yB - 14} H${mid}"/>`);
    b.push(txt(mid + 8, (yT + yB) / 2 + 4, 'L', { cls: 'ink' }));
    b.push(txt((a + mid) / 2, yB + 20, 'v Δt / 2', { anchor: 'middle', cls: 'ink' }));
    b.push(txt((a + mid) / 2 - 26, (yT + yB) / 2 - 10, 'c Δt / 2', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(a - 8, 292, '땅에 서서 보는 사람', { cls: 'ink2', size: 'sm' }));
    b.push(txt(a - 8, 316, '(c Δt/2)² = L² + (v Δt/2)²', { cls: 'ink bold' }));
    b.push(txt(a - 8, 338, '→ Δt = Δt~0 / √(1 − v²/c²) = γ Δt~0', { cls: 'ink bold' }));
    b.push(txt(300, 176, 'v', { cls: 'ink' }));
    b.push(arrow(276, 190, 336, 190, { c: 's2', w: 2 }));
    return {
        name: 'qm-light-clock',
        title: '빛시계와 시간 팽창',
        desc: '거울 두 장 사이를 빛이 왕복하는 시계를 생각한다. 시계와 함께 움직이는 사람에게 빛은 곧게 '
            + '오르내리지만, 땅에 선 사람에게는 비스듬한 더 긴 길을 간다. 빛의 속력이 두 사람에게 같으므로 '
            + '더 긴 길은 더 긴 시간을 뜻한다. 직각삼각형에 피타고라스 정리를 쓰면 그 비가 정확히 γ 다.',
        svg: svg({ width: W, height: H, title: '빛시계', desc: '피타고라스 정리로 얻는 시간 팽창', body: b.join('') }),
    };
})());

/* 15. 로런츠 인자 γ */
add((() => {
    const W = 560, H = 330;
    const g = frame({ xRange: [0, 1.02], yRange: [0, 8], box: { x: 62, y: 46, w: 400, h: 210 } });
    const gam = v => 1 / Math.sqrt(1 - v * v);
    const b = [
        txt(16, 26, '로런츠 인자 γ — 광속 가까이에서만 폭발한다', { cls: 'ink bold' }),
        g.axes({ xLabel: 'v / c', yLabel: 'γ', xTicks: [0, 0.2, 0.4, 0.6, 0.8, 1], yTicks: [0, 2, 4, 6, 8] }),
        g.curve(gam, { from: 0, to: 0.99, cls: 's1' }),
        g.guide([1, 0], [1, 8]),
    ];
    for (const v of [0.5, 0.8, 0.9, 0.99]) {
        b.push(g.dot([v, gam(v)], { cls: 'f2' }));
    }
    b.push(g.label([0.5, gam(0.5)], '1.15', { dx: -6, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.8, gam(0.8)], '1.67', { dx: -6, dy: -8, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.9, gam(0.9)], '2.29', { dx: -8, dy: 2, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.99, gam(0.99)], '7.09', { dx: -8, dy: 4, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.52, 5.0], 'v = c 는 넘을 수 없다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 288, 'v / c 가 0.1 이하면 γ = 1.005 이내다. 일상 속도에서 상대론 보정이 보이지 않는 이유다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 310, 'γ 는 시간 팽창·길이 수축·에너지 증가에 모두 같은 인자로 들어간다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-gamma-curve',
        title: '속력에 따른 로런츠 인자',
        desc: 'γ 는 속력이 광속의 절반일 때도 1.15밖에 되지 않다가 광속에 다가가면서 급격히 커진다. '
            + '일상 속도에서 뉴턴 역학이 잘 맞는 이유이자, 물체를 광속까지 가속할 수 없는 이유다.',
        svg: svg({ width: W, height: H, title: '로런츠 인자 γ', desc: 'v/c 에 따른 γ 곡선', body: b.join('') }),
    };
})());

/* 16. 질량과 에너지 */
add((() => {
    const W = 660, H = 340;
    const b = [txt(16, 26, '질량은 에너지의 한 형태다', { cls: 'ink bold' })];
    const yB = 250, w = 56;
    const bars = [[86, 168, '따로 있을 때', '부분들의 질량 합'], [190, 140, '결합한 뒤', '전체의 질량']];
    for (const [x, h, t1, t2] of bars) {
        b.push(`<rect x="${x}" y="${yB - h}" width="${w}" height="${h}" rx="3" fill="var(--s1)" fill-opacity="0.2" stroke="var(--s1)" stroke-width="1.6"/>`);
        b.push(txt(x + w / 2, yB + 20, t1, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(x + w / 2, yB + 36, t2, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(line(86, yB - 168, 268, yB - 168, { c: 'grid', dash: '4 4', w: 1.2 }));
    b.push(span(272, yB - 168, 272, yB - 140, null, { c: 'ink2' }));
    b.push(txt(280, yB - 150, 'Δm', { cls: 'ink bold' }));
    b.push(arrow(280, yB - 130, 280, yB - 92, { c: 's2', w: 2 }));
    b.push(txt(292, yB - 98, 'Δm c² 만큼', { cls: 'ink2', size: 'sm' }));
    b.push(txt(292, yB - 82, '에너지로 나갔다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 306, '차이 Δm 은 실제보다 크게 그렸다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 328, 'E = γmc²,  E~0 = mc²,  K = (γ − 1)mc²', { cls: 'ink bold' }));

    const g = frame({ xRange: [0, 1], yRange: [0, 4], box: { x: 396, y: 66, w: 206, h: 180 } });
    b.push(g.axes({ xLabel: 'v / c', yLabel: 'K / mc²', xTicks: [0, 0.5, 1], yTicks: [0, 1, 2, 3, 4] }));
    b.push(g.curve(v => 1 / Math.sqrt(1 - v * v) - 1, { from: 0, to: 0.98, cls: 's1' }));
    b.push(g.curve(v => 0.5 * v * v, { from: 0, to: 1, cls: 's2', dash: '6 4' }));
    b.push(g.label([0.42, 0.62], '고전 ½mv²', { cls: 'ink2', size: 'sm' }));
    b.push(g.label([0.55, 2.4], '상대론', { cls: 'ink2', size: 'sm' }));
    b.push(txt(340, 288, '느릴 때는 두 곡선이 겹친다. 광속에 다가가면', { cls: 'ink2', size: 'sm' }));
    b.push(txt(340, 308, '아무리 에너지를 넣어도 속력이 c 를 넘지 못한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-mass-energy',
        title: '질량 결손과 상대론적 운동에너지',
        desc: '부분들이 결합하면 전체의 질량이 부분들의 질량 합보다 작아지고, 그 차이 Δm 이 Δm c² 만큼의 '
            + '에너지로 방출된다. 오른쪽은 운동에너지를 정지에너지로 나눈 값이다. 느릴 때는 고전식과 겹치고 '
            + '광속 근처에서는 무한대로 발산한다.',
        svg: svg({ width: W, height: H, title: '질량과 에너지', desc: '질량 결손과 상대론적 운동에너지', body: b.join('') }),
    };
})());

/* 17. 흑체복사 */
add((() => {
    const W = 600, H = 352;
    const h = 6.62607015e-34, cc = 2.99792458e8, kB = 1.380649e-23;
    const planck = (nm, T) => {
        const lam = nm * 1e-9;
        return (2 * h * cc * cc) / (lam ** 5) / (Math.exp((h * cc) / (lam * kB * T)) - 1);
    };
    const rj = (nm, T) => (2 * cc * kB * T) / ((nm * 1e-9) ** 4);
    const peak = planck(2.897771955e6 / 5800, 5800);
    const g = frame({ xRange: [0, 2000], yRange: [0, 1.3], box: { x: 66, y: 74, w: 440, h: 196 } });
    const b = [
        txt(16, 26, '흑체복사 — 고전 이론은 짧은 파장에서 무너진다', { cls: 'ink bold' }),
        g.axes({ xLabel: 'λ (nm)', yLabel: '세기 (상댓값)', xTicks: [0, 500, 1000, 1500, 2000], yTicks: [0, 0.5, 1] }),
        g.curve(nm => Math.min(planck(Math.max(nm, 20), 5800) / peak, 1.3), { from: 20, to: 2000, cls: 's1' }),
        g.curve(nm => Math.min(planck(Math.max(nm, 20), 4000) / peak, 1.3), { from: 20, to: 2000, cls: 's3' }),
        g.curve(nm => rj(nm, 5800) / peak, { from: 1085, to: 2000, cls: 's2', dash: '6 4' }),
    ];
    b.push(g.guide([499.6, 0], [499.6, 1]));
    b.push(g.label([500, 1.14], '5800 K', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(g.label([840, 0.22], '4000 K', { cls: 'ink2', size: 'sm' }));
    b.push(g.label([1130, 1.14], '고전 이론 (자외 파탄)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 300, '고전 이론은 파장이 짧아질수록 세기가 끝없이 커진다고 예측했다. 관측은 그렇지 않다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 320, '에너지가 hf 덩어리로만 오간다고 두면(플랑크, 1900) 관측 곡선이 정확히 나온다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 340, '봉우리 파장 500 nm (5800 K), 724 nm (4000 K). λ~max T = 2.90 × 10⁻³ m·K (빈 변위 법칙)', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-blackbody',
        title: '흑체복사 스펙트럼',
        desc: '뜨거운 물체가 내는 빛의 세기를 파장별로 그린 것이다. 온도가 높을수록 봉우리가 짧은 파장 쪽으로 '
            + '옮겨 가고 전체 세기가 커진다. 고전 이론은 짧은 파장에서 세기가 발산한다고 예측했지만 '
            + '관측은 그렇지 않았고, 에너지가 hf 단위로만 오간다고 가정해야 관측이 설명된다.',
        svg: svg({ width: W, height: H, title: '흑체복사', desc: '플랑크 곡선과 고전 예측', body: b.join('') }),
    };
})());

/* 18. 광전효과 — 문턱 진동수와 기울기 h */
add((() => {
    const W = 600, H = 372;
    const hev = 4.135667696e-15 * 1e14;        // eV per (10^14 Hz)
    const g = frame({ xRange: [0, 14], yRange: [-5, 4], box: { x: 66, y: 66, w: 430, h: 226 } });
    const metals = [{ phi: 2.30, cls: 's1', nm: '금속 A (φ = 2.30 eV)' }, { phi: 4.30, cls: 's2', nm: '금속 B (φ = 4.30 eV)' }];
    const b = [
        txt(16, 26, '광전효과 — 최대 운동에너지는 진동수의 일차함수다', { cls: 'ink bold' }),
        g.axes({ xLabel: 'f (10¹⁴ Hz)', yLabel: 'K~max (eV)', xTicks: [0, 2, 4, 6, 8, 10, 12, 14], yTicks: [-4, -2, 0, 2, 4] }),
    ];
    for (const m of metals) {
        const f0 = m.phi / hev;
        b.push(g.curve(f => hev * f - m.phi, { from: f0, to: 14, cls: m.cls }));
        b.push(g.curve(f => hev * f - m.phi, { from: 0, to: f0, cls: m.cls, dash: '5 4' }));
        b.push(g.dot([f0, 0], { cls: m.cls === 's1' ? 'f1' : 'f2' }));
        b.push(g.label([f0, 0], `f~0 = ${f0.toFixed(1)}`, { dx: 6, dy: -13, cls: 'ink2', size: 'sm' }));
        b.push(g.label([0, -m.phi], `−φ = −${m.phi.toFixed(2)} eV`, { dx: 14, dy: 4, cls: 'ink2', size: 'sm' }));
    }
    b.push(g.label([10.6, 3.2], '금속 A', { cls: 'ink2', size: 'sm' }));
    b.push(g.label([13.2, 1.2], '금속 B', { dx: -4, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 322, 'K~max = h f − φ. 두 직선의 기울기가 같고, 그 기울기가 플랑크 상수 h 다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 342, '점선 부분은 실제로 전자가 나오지 않는 구간이다. 세로 절편이 −φ 임을 보이려고 늘였다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, 362, '세기를 올리면 나오는 전자의 개수만 늘고 K~max 는 그대로다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-photoelectric',
        title: '광전효과의 문턱 진동수',
        desc: '방출 전자의 최대 운동에너지를 빛의 진동수에 대해 그리면 직선이 된다. 기울기는 금속 종류와 '
            + '무관하게 플랑크 상수 h 이고, 세로 절편이 일함수의 음수다. 가로 절편이 문턱 진동수이며 '
            + '그보다 낮은 진동수에서는 세기를 아무리 키워도 전자가 나오지 않는다.',
        svg: svg({ width: W, height: H, title: '광전효과', desc: '진동수에 대한 최대 운동에너지', body: b.join('') }),
    };
})());

/* 19. 이중성 — 광자를 하나씩 보내도 간섭무늬가 쌓인다 */
add((() => {
    const W = 620, H = 330;
    let seed = 20240917;
    const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
    const x0 = 70, wBox = 480;
    const prof = u => (Math.cos(Math.PI * 5 * (u - 0.5)) ** 2) * Math.exp(-(((u - 0.5) / 0.34) ** 2));
    const rows = [[70, 20, '광자 20개'], [150, 90, '광자 90개'], [230, 420, '광자 420개']];
    const b = [txt(16, 26, '빛을 하나씩 보내도 간섭무늬가 쌓인다', { cls: 'ink bold' })];
    for (const [y, n, label] of rows) {
        b.push(`<rect x="${x0}" y="${y}" width="${wBox}" height="44" rx="3" fill="none" stroke="var(--ink2)" stroke-width="1.2"/>`);
        let placed = 0, guard = 0;
        while (placed < n && guard < n * 60) {
            guard += 1;
            const u = rnd();
            if (rnd() > prof(u)) continue;
            const px2 = x0 + u * wBox, py = y + 4 + rnd() * 36;
            b.push(`<circle cx="${r2(px2)}" cy="${r2(py)}" r="1.7" fill="var(--s1)"/>`);
            placed += 1;
        }
        b.push(txt(x0 + wBox + 10, y + 26, label, { cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(16, 300, '처음에는 무작위로 보이지만 수가 쌓이면 무늬가 드러난다. 자국의 자리는 확률이 정한다.',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 320, '어느 슬릿으로 갔는지 알아내면 무늬는 사라진다. 알갱이냐 파동이냐는 질문 방식이 정한다.',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-duality-buildup',
        title: '광자를 하나씩 보낸 이중슬릿',
        desc: '빛을 아주 약하게 해서 광자를 한 번에 하나씩 보내도, 스크린에는 점이 하나씩 찍히다가 결국 '
            + '간섭무늬가 나타난다. 낱낱은 알갱이처럼 도착하고 도착할 확률은 파동처럼 간섭한다.',
        svg: svg({ width: W, height: H, title: '광자 하나씩의 이중슬릿', desc: '점이 쌓여 무늬가 된다', body: b.join('') }),
    };
})());

/* 20. 드브로이 파장의 규모 비교 */
add((() => {
    const W = 680, H = 320;
    const x0 = 214, x1 = 632, lo = -36, hi = -5;
    const X = e => x0 + ((e - lo) / (hi - lo)) * (x1 - x0);
    const yA = 236;
    const b = [txt(16, 26, '드브로이 파장 — 무거울수록 터무니없이 짧아진다', { cls: 'ink bold' })];
    b.push(line(x0 - 10, yA, x1 + 16, yA, { c: 'ink2', w: 1.6 }));
    for (const e of [-36, -30, -24, -18, -12, -6]) {
        const sup = String(-e).split('').map(d => '⁰¹²³⁴⁵⁶⁷⁸⁹'[Number(d)]).join('');
        b.push(line(X(e), yA, X(e), yA + 6, { c: 'ink2', w: 1.3 }));
        b.push(txt(X(e), yA + 22, `10⁻${sup}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(x1 + 20, yA + 4, 'λ (m)', { cls: 'ink2', size: 'sm' }));
    for (const [e, nm, ty] of [[-10, '원자 지름', 56], [-14, '원자핵 지름', 40]]) {
        b.push(line(X(e), ty + 6, X(e), yA, { c: 'grid', dash: '5 4', w: 1.2 }));
        b.push(txt(X(e), ty, nm, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    const items = [
        [-6.30, '가시광선 광자 (500 nm)', 's1'],
        [-9.91, '100 eV 로 가속한 전자 (0.12 nm)', 's1'],
        [-10.56, '상온의 질소 분자 (28 pm)', 's3'],
        [-33.94, '야구공 0.145 kg, 40 m/s', 's2'],
    ];
    let y = 84;
    for (const [e, nm, c] of items) {
        b.push(line(x0 - 6, y, X(e), y, { c: 'grid', w: 1.2, dash: '3 4' }));
        b.push(`<circle cx="${r2(X(e))}" cy="${y}" r="5.5" fill="var(--${c})"/>`);
        b.push(txt(x0 - 14, y + 4, nm, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        y += 36;
    }
    b.push(txt(16, 288, 'λ = h / p. 야구공의 파장은 원자핵보다 10¹⁹배 넘게 작아 잴 방법이 없다.',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 308, '전자의 파장이 원자 간격과 같은 자리 수라는 것이 전자 회절과 전자현미경의 근거다.',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-debroglie-scale',
        title: '여러 물체의 드브로이 파장',
        desc: '가로축은 파장의 자릿수다. 전자의 파장은 원자 크기와 같은 자리 수라 결정 격자에서 회절하지만, '
            + '야구공의 파장은 원자핵보다 훨씬 작아 어떤 실험으로도 드러나지 않는다. '
            + '거시 세계에서 파동성이 보이지 않는 이유가 이 그림에 다 있다.',
        svg: svg({ width: W, height: H, title: '드브로이 파장 비교', desc: '자릿수로 본 물질파의 크기', body: b.join('') }),
    };
})());

/* 21. 보어 모형과 에너지 준위 */
add((() => {
    const W = 680, H = 390;
    const b = [txt(16, 26, '보어 모형 — 정해진 궤도, 정해진 에너지', { cls: 'ink bold' })];
    const cx = 160, cy = 210, u = 10.6;
    for (const n of [1, 2, 3]) {
        const R = u * n * n;
        b.push(`<circle cx="${cx}" cy="${cy}" r="${r2(R)}" fill="none" stroke="var(--ink2)" stroke-width="1.4" stroke-dasharray="4 4"/>`);
        const lx = n === 1 ? cx + 26 : cx + R * 0.71 + 6;
        const ly = n === 1 ? cy + 26 : cy - R * 0.71 - 4;
        b.push(txt(lx, ly, `n = ${n}`, { cls: 'ink2', size: 'sm' }));
    }
    b.push(`<circle cx="${cx}" cy="${cy}" r="5" fill="var(--s2)"/>`);
    b.push(`<circle cx="${cx + u}" cy="${cy}" r="3.6" fill="var(--s1)"/>`);
    b.push(txt(cx, cy + 130, 'r~n = n² a~0,  a~0 = 52.9 pm', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx, cy + 150, '반지름은 n² 에 비례해 빠르게 커진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const xL = 360, xR = 560, yTop = 76, yBot = 322, E1 = 13.606;
    const Y = E => yTop + ((0 - E) / E1) * (yBot - yTop);
    b.push(line(xL - 16, Y(0), xR + 40, Y(0), { c: 'ink2', w: 1.6, dash: '5 4' }));
    b.push(txt(xR + 44, Y(0) + 4, 'E = 0 (이온화)', { cls: 'ink2', size: 'sm' }));
    for (const n of [1, 2, 3, 4, 5, 6]) {
        const E = -E1 / (n * n);
        b.push(line(xL, Y(E), xR, Y(E), { c: 'ink2', w: 2 }));
        if (n <= 3) {
            b.push(txt(xL - 8, Y(E) + 4, `n = ${n}`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
            b.push(txt(xR + 8, Y(E) + 4, `${E.toFixed(2)} eV`, { cls: 'ink2', size: 'sm' }));
        }
    }
    b.push(txt(xL - 8, Y(-E1 / 25) + 4, 'n = 4, 5, 6 …', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const tr = [[3, 2, 392, '656 nm', 'end', -5, 's2'], [4, 2, 434, '486 nm', 'start', 5, 's1'],
        [2, 1, 484, '122 nm', 'start', 5, 's3']];
    for (const [ni, nf, x, nm, an, dx, c] of tr) {
        b.push(arrow(x, Y(-E1 / (ni * ni)), x, Y(-E1 / (nf * nf)) - 3, { c, w: 2 }));
        b.push(txt(x + dx, (Y(-E1 / (ni * ni)) + Y(-E1 / (nf * nf))) / 2, nm, { anchor: an, cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(360, 352, 'E~n = −13.606 eV / n². 위로 갈수록 준위가 촘촘해진다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(360, 372, '떨어질 때 나오는 광자의 에너지가 두 준위의 차다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-bohr-levels',
        title: '보어 모형의 궤도와 에너지 준위',
        desc: '허용된 궤도의 반지름은 n² 에 비례하고 에너지는 −13.606 eV 를 n² 으로 나눈 값이다. '
            + 'n 이 커질수록 준위 간격이 좁아져 0 에 몰린다. 전자가 위 준위에서 아래 준위로 떨어질 때 '
            + '두 준위의 에너지 차만큼의 광자가 하나 나온다.',
        svg: svg({ width: W, height: H, title: '보어 모형과 에너지 준위', desc: 'n² 궤도와 에너지 사다리', body: b.join('') }),
    };
})());

/* 22. 수소의 가시광선 스펙트럼선 */
add((() => {
    const W = 660, H = 306;
    const x0 = 60, x1 = 620, lo = 380, hi = 760;
    const X = nm => x0 + ((nm - lo) / (hi - lo)) * (x1 - x0);
    const yT = 70, yB = 132;
    const stops = [[380, '#6a00a8'], [420, '#2200ff'], [465, '#0090ff'], [500, '#00c8b4'],
        [530, '#22c000'], [575, '#ffd800'], [610, '#ff8000'], [680, '#e01000'], [760, '#6b0000']];
    const grad = stops.map(([nm, c]) => `<stop offset="${r2(((nm - lo) / (hi - lo)) * 100)}%" stop-color="${c}"/>`).join('');
    const b = [
        txt(16, 26, '수소가 내는 빛 — 이어진 띠가 아니라 몇 개의 선뿐이다', { cls: 'ink bold' }),
        `<defs><linearGradient id="vis" x1="0" y1="0" x2="1" y2="0">${grad}</linearGradient></defs>`,
        `<rect x="${x0}" y="${yT}" width="${x1 - x0}" height="${yB - yT}" fill="url(#vis)" stroke="var(--ink2)" stroke-width="1.4"/>`,
    ];
    const lines = [[656.3, 'Hα', 'n = 3 → 2', 0], [486.1, 'Hβ', 'n = 4 → 2', 0],
        [434.0, 'Hγ', 'n = 5 → 2', 0], [410.2, 'Hδ', 'n = 6 → 2', 62]];
    for (const [nm, nameK, trK, off] of lines) {
        b.push(`<rect x="${r2(X(nm) - 1.6)}" y="${yT}" width="3.2" height="${yB - yT}" fill="#111"/>`);
        b.push(line(X(nm), yB, X(nm), yB + 18 + off, { c: 'ink2', w: 1.2 }));
        b.push(txt(X(nm), yB + 34 + off, nameK, { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(X(nm), yB + 52 + off, `${nm} nm`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(X(nm), yB + 68 + off, trK, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const nm of [400, 500, 600, 700]) {
        b.push(txt(X(nm), yT - 8, `${nm} nm`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(16, 278, '선의 자리가 곧 에너지 준위 차다. 원소마다 선의 무늬가 달라 별빛만 보고도 성분을 안다.',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 298, 'n → 1 (라이먼)은 자외선, n → 3 (파셴)은 적외선이라 눈에 보이는 것은 발머 계열뿐이다.',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-hydrogen-spectrum',
        title: '수소의 발머 계열 스펙트럼선',
        desc: '수소를 방전시키면 연속된 무지개가 아니라 정해진 파장에서만 빛이 나온다. 눈에 보이는 네 선은 '
            + '모두 n = 2 로 떨어질 때 나오며, 파장은 두 에너지 준위의 차로 정확히 계산된다.',
        svg: svg({ width: W, height: H, title: '수소 스펙트럼선', desc: '발머 계열 네 선의 파장', body: b.join('') }),
    };
})());

/* 23. 불확정성 — 위치를 좁히면 파장이 흐려진다 */
add((() => {
    const W = 620, H = 340;
    const b = [txt(16, 26, '위치를 좁히면 파장이 흐려진다', { cls: 'ink bold' })];
    const panel = (top, sigma, note1, note2) => {
        const g = frame({ xRange: [0, 1], yRange: [-1.2, 1.2], box: { x: 66, y: top, w: 380, h: 90 } });
        const env = x => Math.exp(-(((x - 0.5) / sigma) ** 2));
        return g.axes({ xLabel: 'x', yLabel: '', xTicks: [], yTicks: [], grid: false })
            + g.curve(x => env(x), { cls: 's2', dash: '5 4' })
            + g.curve(x => -env(x), { cls: 's2', dash: '5 4' })
            + g.curve(x => env(x) * Math.cos(70 * (x - 0.5)), { cls: 's1', steps: 480 })
            + txt(462, top + 30, note1, { cls: 'ink', size: 'sm' })
            + txt(462, top + 50, note2, { cls: 'ink2', size: 'sm' });
    };
    b.push(panel(58, 0.33, '파장이 뚜렷하다', 'Δp 작다 / Δx 크다'));
    b.push(panel(190, 0.055, '위치가 뚜렷하다', 'Δp 크다 / Δx 작다'));
    b.push(txt(16, 306, 'Δx Δp~x ≥ ħ / 2. 두 폭의 곱에 하한이 있어 한쪽을 좁히면 다른 쪽이 넓어진다.',
        { cls: 'ink bold' }));
    b.push(txt(16, 328, '측정 기술의 한계가 아니라 파동 묶음이라는 것에서 오는 성질이다.',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'qm-uncertainty',
        title: '파동 묶음과 불확정성',
        desc: '위쪽은 길게 이어진 파라 파장을 정확히 읽을 수 있지만 어디에 있는지 말하기 어렵다. '
            + '아래쪽은 좁은 묶음이라 위치는 분명하지만 몇 파장짜리인지 말하기 어렵다. '
            + '파장은 운동량과 λ = h/p 로 묶여 있으므로, 이 성질이 곧 위치와 운동량의 불확정성이다.',
        svg: svg({ width: W, height: H, title: '파동 묶음과 불확정성', desc: '긴 파열과 짧은 묶음', body: b.join('') }),
    };
})());

export default figures;
