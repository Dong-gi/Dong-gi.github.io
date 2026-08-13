/**
 * 6장(일·에너지·운동량)과 7장(회전 운동)의 그림.
 *
 * physics.mjs 와 형식이 같다. 이름이 `physics-` 로 시작하므로 build.mjs 가
 * physics 과목의 그림으로 함께 읽어 /figures/physics/ 아래에 쓴다.
 * 6장 그림은 `energy-`, 7장 그림은 `rot-` 로 시작한다.
 *
 * SVG 안에는 MathJax 가 닿지 않으므로 라벨은 유니코드로 적고 아래첨자는
 * lib.mjs 의 `v~0` 표기를 쓴다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);

const RAD = d => (d * Math.PI) / 180;
const f2 = v => Number.parseFloat(Number(v).toFixed(2));

/** 화소 좌표 다각형 채우기. */
const fillPoly = (pts, color, op = 0.18) =>
    `<path d="M${pts.map(p => `${f2(p[0])} ${f2(p[1])}`).join(' L')} Z" fill="${color}" fill-opacity="${op}" stroke="none"/>`;

/** 화소 좌표 호 화살표. 각은 도, 화면 기준 반시계가 양이다. */
const arcArrow = (cx, cy, r, a1, a2, { cls = 's1', marker = 'ar1', width = 2.5 } = {}) => {
    const p = a => `${f2(cx + r * Math.cos(RAD(a)))} ${f2(cy - r * Math.sin(RAD(a)))}`;
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    const sweep = a2 > a1 ? 0 : 1;
    return `<path class="${cls}" fill="none" stroke-width="${width}" stroke-linecap="round" `
        + `marker-end="url(#${marker})" d="M${p(a1)} A${r} ${r} 0 ${large} ${sweep} ${p(a2)}"/>`;
};

/** 공(원). cls 는 f1/f2/f3 같은 채움 클래스. */
const ball = (cx, cy, r, cls = 'f1', op = 0.8) =>
    `<circle class="${cls}" cx="${f2(cx)}" cy="${f2(cy)}" r="${f2(r)}" fill-opacity="${op}" stroke="var(--ink2)" stroke-width="1.2"/>`;

/** 윤곽선만 있는 상자. */
const boxRect = (x, y, w, h, { dash, cls = 'var(--ink2)' } = {}) =>
    `<rect x="${f2(x)}" y="${f2(y)}" width="${f2(w)}" height="${f2(h)}" rx="3" fill="none" `
    + `stroke="${cls}" stroke-width="1.5"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;

/** 바닥면(빗금 포함). */
const ground = (x1, x2, y) => {
    const out = [`<path class="ax" d="M${f2(x1)} ${f2(y)} H${f2(x2)}"/>`];
    for (let x = x1; x < x2; x += 14) out.push(`<path class="gr" d="M${f2(x)} ${f2(y)} L${f2(x - 7)} ${f2(y + 8)}"/>`);
    return out.join('');
};

/** 지그재그 용수철. (x1,y1)에서 (x2,y2)까지, turns 번 접힌다. */
const coil = (x1, y1, x2, y2, turns = 8, amp = 12) => {
    const dx = x2 - x1, dy = y2 - y1;
    const len = Math.hypot(dx, dy);
    const ux = dx / len, uy = dy / len;
    const nx = -uy, ny = ux;
    const pts = [[x1, y1]];
    const lead = 12;
    const span = len - 2 * lead;
    pts.push([x1 + ux * lead, y1 + uy * lead]);
    for (let i = 0; i < turns * 2; i += 1) {
        const s = lead + (span * (i + 0.5)) / (turns * 2);
        const side = i % 2 === 0 ? 1 : -1;
        pts.push([x1 + ux * s + nx * amp * side, y1 + uy * s + ny * amp * side]);
    }
    pts.push([x2 - ux * lead, y2 - uy * lead], [x2, y2]);
    return `<path fill="none" stroke="var(--ink2)" stroke-width="1.6" stroke-linejoin="round" `
        + `d="M${pts.map(p => `${f2(p[0])} ${f2(p[1])}`).join(' L')}"/>`;
};

/* ================================================================== *
 * 6장 — 일·에너지·운동량
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 비스듬한 힘 중 이동 방향 성분만 일을 한다 (스칼라곱의 도입)
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 320;
    const gy = 214;                      // 바닥
    const bx = 110, bw = 62, bh = 42;    // 상자
    const cx = bx + bw / 2, cy = gy - bh / 2;
    const th = 35, L = 132;
    const ex = cx + L * Math.cos(RAD(th)), ey = cy - L * Math.sin(RAD(th));
    const d = 268;
    const body = [
        ground(50, 540, gy),
        boxRect(bx, gy - bh, bw, bh),
        txt(cx, cy + 5, 'm', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        boxRect(bx + d, gy - bh, bw, bh, { dash: '5 4' }),
        // 힘과 그 두 성분
        `<path class="gr" stroke-dasharray="4 3" d="M${f2(ex)} ${f2(ey)} V${gy - bh / 2}"/>`,
        `<path class="gr" stroke-dasharray="4 3" d="M${f2(ex)} ${f2(ey)} H${f2(cx)}"/>`,
        px(cx, cy, ex, ey, { cls: 's1', marker: 'ar1', width: 3 }),
        px(cx, cy, ex, cy, { cls: 's2', marker: 'ar2', width: 2.2 }),
        px(cx, cy, cx, ey, { cls: 's3', marker: 'ar3', width: 2.2 }),
        txt(ex + 8, ey - 4, 'F', { cls: 'ink' }),
        arc(cx, cy, 54, 0, th, 'θ'),
        txt(ex + 8, cy + 5, 'F cos θ', { cls: 'ink2', size: 'sm' }),
        txt(cx - 8, ey + 4, 'F sin θ', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        // 변위
        px(bx, gy + 44, bx + d, gy + 44, { cls: 'ax', marker: 'ark', width: 1.5 }),
        txt(bx + d / 2, gy + 38, '변위 d', { anchor: 'middle', cls: 'ink' }),
        // 설명
        txt(46, 40, '이동 방향 성분만 일을 한다', { cls: 'ink bold' }),
        txt(46, 60, 'W = (F cos θ) × d = F d cos θ', { cls: 'ink' }),
        txt(W - 16, 40, 'F sin θ 는 이동 방향과 수직이라', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        txt(W - 16, 58, '아무리 커도 일은 0 이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        legend(W - 168, 84, [{ slot: 1, name: '작용한 힘 F' }, { slot: 2, name: '일을 하는 성분' }, { slot: 3, name: '일을 못 하는 성분' }]),
    ].join('');
    return {
        name: 'energy-work-component',
        title: '비스듬히 당길 때 일을 하는 성분',
        desc: '수평과 세타의 각을 이루는 힘으로 상자를 끌면, 힘을 이동 방향 성분과 수직 성분으로 나눌 수 있다. '
            + '일을 하는 것은 이동 방향 성분 F cos θ 뿐이고 수직 성분 F sin θ 는 일을 하지 않는다.',
        svg: svg({ width: W, height: H, title: '일을 하는 힘의 성분', desc: 'F 를 이동 방향과 수직 방향으로 나눈 그림', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 일의 부호 — 같은 쪽/수직/반대쪽
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 292;
    const gy = 150, bw = 52, bh = 36;
    const panel = (ox, dir, note, sign, cls, marker) => {
        const bx = ox + 40;
        const cx = bx + bw / 2, cy = gy - bh / 2;
        const arrow = dir === 'right' ? px(cx, cy, cx + 62, cy, { cls, marker, width: 2.8 })
            : dir === 'up' ? px(cx, cy, cx, cy - 62, { cls, marker, width: 2.8 })
                : px(cx, cy, cx - 62, cy, { cls, marker, width: 2.8 });
        const lab = dir === 'right' ? txt(cx + 68, cy + 5, 'F', { cls: 'ink' })
            : dir === 'up' ? txt(cx, cy - 70, 'F', { anchor: 'middle', cls: 'ink' })
                : txt(cx - 68, cy + 5, 'F', { anchor: 'end', cls: 'ink' });
        return [
            ground(ox + 14, ox + 186, gy),
            boxRect(bx, gy - bh, bw, bh),
            arrow, lab,
            px(ox + 26, gy + 34, ox + 172, gy + 34, { cls: 'ax', marker: 'ark', width: 1.5 }),
            txt(ox + 100, gy + 28, '변위 d', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
            txt(ox + 100, gy + 70, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }),
            txt(ox + 100, gy + 92, sign, { anchor: 'middle', cls: 'ink bold' }),
        ].join('');
    };
    const body = [
        txt(20, 32, '일의 부호는 힘과 변위 사이의 각이 정한다', { cls: 'ink bold' }),
        panel(10, 'right', 'θ = 0°,  cos 0° = 1', 'W > 0', 's2', 'ar2'),
        panel(210, 'up', 'θ = 90°,  cos 90° = 0', 'W = 0', 's3', 'ar3'),
        panel(410, 'left', 'θ = 180°,  cos 180° = −1', 'W < 0', 's1', 'ar1'),
        txt(W / 2, H - 12, '수직항력은 언제나 가운데 경우이고, 운동을 방해하는 마찰력은 언제나 오른쪽 경우다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'energy-work-sign',
        title: '일의 부호 세 가지',
        desc: '힘이 변위와 같은 쪽이면 양의 일, 수직이면 일이 0, 반대쪽이면 음의 일이다. '
            + '수직항력은 언제나 변위와 수직이라 일을 하지 않고, 운동을 방해하는 마찰력은 언제나 음의 일을 한다.',
        svg: svg({ width: W, height: H, title: '일의 부호', desc: '같은 쪽·수직·반대쪽 세 경우', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 일은 F-x 그래프의 넓이다 (일정한 힘 / 변하는 힘)
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const mk = (bx, f, from, to, head, strips) => {
        const g = frame({ xRange: [0, 4], yRange: [0, 10], box: { x: bx, y: 56, w: 200, h: 170 } });
        const out = [g.axes({ xLabel: 'x (m)', yLabel: 'F (N)', xTicks: [0, 1, 2, 3, 4], yTicks: [0, 4, 8] })];
        if (strips) {
            const c = 6, w = (to - from) / c;
            for (let i = 0; i < c; i += 1) {
                const xa = from + i * w, h = f(xa + w / 2);
                out.push(`<rect x="${g.X(xa)}" y="${g.Y(h)}" width="${f2(g.X(xa + w) - g.X(xa))}" `
                    + `height="${f2(g.Y(0) - g.Y(h))}" fill="var(--s2)" fill-opacity="0.16" stroke="var(--s2)" stroke-width="0.9"/>`);
            }
        } else {
            out.push(fillPoly([[g.X(from), g.Y(0)], [g.X(from), g.Y(f(from))], [g.X(to), g.Y(f(to))], [g.X(to), g.Y(0)]], 'var(--s1)', 0.2));
        }
        out.push(g.curve(f, { from: 0, to: 4, cls: strips ? 's2' : 's1' }));
        out.push(txt(bx + 100, 38, head, { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };
    const body = [
        mk(56, () => 6, 0, 3, '일정한 힘', false),
        txt(156, 268, '넓이 = 6 N × 3 m = 18 J', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        mk(346, x => 1.5 + 1.8 * x, 0, 3, '변하는 힘', true),
        txt(446, 268, '가는 띠로 쪼개 더하면 역시 넓이', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W / 2, H - 6, '어느 쪽이든 일은 F-x 그래프 아래 넓이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'energy-force-distance-area',
        title: '일은 F-x 그래프 아래 넓이',
        desc: '힘이 일정하면 일은 직사각형의 넓이다. 힘이 변하면 구간을 가는 띠로 쪼개 각 띠에서 힘을 '
            + '일정하다고 보고 더한다. 띠를 얇게 할수록 그 합은 곡선 아래 넓이에 가까워진다.',
        svg: svg({ width: W, height: H, title: '일 = F-x 그래프의 넓이', desc: '직사각형과 띠로 쪼갠 곡선 아래 넓이', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 용수철 — 삼각형 넓이가 곧 ½kx²
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 320;
    // 왼쪽: 벽에 붙인 용수철을 x 만큼 늘린 그림
    const wallX = 46, y0 = 110;
    const natural = 150, stretch = 62;
    const left = [
        `<path class="ax" d="M${wallX} ${y0 - 40} V${y0 + 46}"/>`,
        ...[0, 1, 2, 3, 4].map(i => `<path class="gr" d="M${wallX} ${y0 - 34 + i * 20} L${wallX - 10} ${y0 - 24 + i * 20}"/>`),
        coil(wallX, y0, wallX + natural, y0, 7, 11),
        boxRect(wallX + natural, y0 - 18, 34, 36),
        txt(wallX + natural + 17, y0 + 5, 'm', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(wallX + natural / 2, y0 - 30, '자연 길이', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        // 늘린 상태
        coil(wallX, y0 + 78, wallX + natural + stretch, y0 + 78, 7, 11),
        boxRect(wallX + natural + stretch, y0 + 60, 34, 36),
        txt(wallX + natural + stretch + 17, y0 + 83, 'm', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(wallX + natural + stretch + 40, y0 + 78, wallX + natural + stretch + 96, y0 + 78, { cls: 's2', marker: 'ar2', width: 2.4 }),
        txt(wallX + natural + stretch + 68, y0 + 62, '당기는 힘', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(wallX + natural, y0 + 122, wallX + natural + stretch, y0 + 122, { cls: 'ax', marker: 'ark', width: 1.5 }),
        txt(wallX + natural + stretch / 2, y0 + 140, 'x', { anchor: 'middle', cls: 'ink' }),
        `<path class="gr" stroke-dasharray="4 3" d="M${wallX + natural} ${y0 + 60} V${y0 + 128}"/>`,
        txt(wallX, 40, '용수철은 늘어난 만큼 세게 되당긴다:  F = k x', { cls: 'ink bold' }),
    ];
    // 오른쪽: F-x 그래프의 삼각형
    const g = frame({ xRange: [0, 0.5], yRange: [0, 50], box: { x: 404, y: 100, w: 148, h: 148 } });
    const k = 100, xm = 0.4;
    const right = [
        txt(392, 64, 'k = 100 N/m 인 용수철의 F-x 그래프', { cls: 'ink2', size: 'sm' }),
        g.axes({ xLabel: 'x (m)', yLabel: 'F (N)', xTicks: [0, 0.2, 0.4], yTicks: [0, 20, 40] }),
        fillPoly([[g.X(0), g.Y(0)], [g.X(xm), g.Y(k * xm)], [g.X(xm), g.Y(0)]], 'var(--s1)', 0.22),
        g.curve(x => k * x, { from: 0, to: 0.5, cls: 's1' }),
        g.guide([xm, 0], [xm, k * xm]),
        g.dot([xm, k * xm], { cls: 'f1' }),
        txt(478, 284, '넓이 = ½ · x · (k x) = ½ k x²', { anchor: 'middle', cls: 'ink' }),
        txt(478, 304, 'x = 0.4 m 이면 ½(100)(0.16) = 8.0 J', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ];
    return {
        name: 'energy-spring-area',
        title: '용수철을 늘이는 데 드는 일',
        desc: '용수철의 힘은 늘어난 길이에 비례하므로 F-x 그래프가 원점을 지나는 직선이다. '
            + '따라서 그 아래 넓이는 삼각형이고, 밑변 x 에 높이 kx 를 곱해 반으로 나눈 ½kx² 가 곧 한 일이다.',
        svg: svg({ width: W, height: H, title: '용수철의 F-x 그래프와 삼각형 넓이', desc: '후크 법칙과 ½kx²', body: [...left, ...right].join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 역학적 에너지 보존 — 트랙과 에너지 막대
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 340;
    const track = x => (x <= 6 ? 2.5 * (1 + Math.cos(Math.PI * x / 6)) : 1.5 * (1 - Math.cos(Math.PI * (x - 6) / 5)));
    const g = frame({ xRange: [-0.6, 11.6], yRange: [-0.6, 6.4], box: { x: 52, y: 72, w: 340, h: 196 } });
    const pts = [[0, 5, 'A'], [6, 0, 'B'], [11, 3, 'C']];
    const bars = (() => {
        const base = 280, hTot = 132, w = 30;
        const out = [];
        const data = [['A', 0], ['B', 1], ['C', 0.4]];
        data.forEach(([name, kf], i) => {
            const x = 448 + i * 58;
            const hK = hTot * kf, hU = hTot - hK;
            out.push(`<rect x="${x}" y="${base - hTot}" width="${w}" height="${f2(hU)}" fill="var(--s1)" fill-opacity="0.75"/>`);
            out.push(`<rect x="${x}" y="${f2(base - hK)}" width="${w}" height="${f2(hK)}" fill="var(--s2)" fill-opacity="0.75"/>`);
            out.push(`<rect x="${x}" y="${base - hTot}" width="${w}" height="${hTot}" fill="none" stroke="var(--ink2)" stroke-width="1"/>`);
            out.push(txt(x + w / 2, base + 16, name, { anchor: 'middle', cls: 'ink' }));
        });
        return out.join('');
    })();
    const body = [
        g.axes({ xLabel: 'x (m)', yLabel: 'y (m)', xTicks: [0, 4, 8], yTicks: [0, 2, 4] }),
        g.curve(track, { from: 0, to: 11, cls: 's3' }),
        ...pts.map(([x, y, n]) => g.dot([x, y], { cls: 'f2', r: 5 }) + g.label([x, y], n, { dx: 8, dy: -8, cls: 'ink' })),
        g.guide([0, 0], [0, 5]),
        g.guide([11, 0], [11, 3]),
        txt(24, 34, '마찰이 없으면 K 와 U 의 합은 어디서나 같다', { cls: 'ink bold' }),
        txt(448, 74, '에너지 구성', { cls: 'ink' }),
        bars,
        legend(448, 100, [{ slot: 1, name: '위치에너지 U' }, { slot: 2, name: '운동에너지 K' }]),
        txt(W - 12, H - 10, 'A 에서 정지, B 에서 가장 빠르다. 높이가 같으면 속력도 같다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'energy-conservation-track',
        title: '트랙 위의 운동에너지와 위치에너지',
        desc: '마찰 없는 트랙에서 공이 내려오면 위치에너지가 줄어든 만큼 운동에너지가 늘어난다. '
            + '막대의 전체 높이(둘의 합)는 A, B, C 어디서나 같고, 트랙의 모양은 답에 들어가지 않는다.',
        svg: svg({ width: W, height: H, title: '역학적 에너지 보존', desc: '트랙 세 지점의 K, U 막대그래프', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 충격량 — F-t 그래프의 넓이와 평균 힘
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 300;
    const F = t => 9 * Math.sin(Math.PI * t / 2) ** 2;
    const g = frame({ xRange: [-0.1, 2.6], yRange: [0, 11], box: { x: 62, y: 70, w: 330, h: 180 } });
    const poly = [];
    for (let i = 0; i <= 60; i += 1) { const t = (2 * i) / 60; poly.push([g.X(t), g.Y(F(t))]); }
    poly.push([g.X(2), g.Y(0)], [g.X(0), g.Y(0)]);
    const body = [
        g.axes({ xLabel: 't (ms)', yLabel: 'F (kN)', xTicks: [0, 1, 2], yTicks: [0, 4.5, 9] }),
        fillPoly(poly, 'var(--s1)', 0.2),
        g.curve(F, { from: 0, to: 2, cls: 's1' }),
        g.line([[0, 4.5], [2, 4.5]], { cls: 's2', dash: '6 4' }),
        g.line([[2, 0], [2, 4.5]], { cls: 's2', dash: '6 4' }),
        g.label([1, 4.5], '평균 힘 F̄', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([1, 2], '넓이 = 충격량', { anchor: 'middle', cls: 'ink' }),
        txt(24, 34, '충돌하는 동안 힘은 순간마다 다르다', { cls: 'ink bold' }),
        txt(410, 96, '넓이가 곧 운동량 변화', { cls: 'ink' }),
        txt(410, 118, 'F̄ Δt = Δp', { cls: 'ink' }),
        txt(410, 150, '같은 Δp 를 만들 때', { cls: 'ink2', size: 'sm' }),
        txt(410, 168, 'Δt 를 늘리면 F̄ 가 줄어든다', { cls: 'ink2', size: 'sm' }),
        txt(410, 190, '(에어백, 무릎 굽히기)', { cls: 'ink2', size: 'sm' }),
        txt(W - 12, H - 10, '점선 직사각형은 곡선과 넓이가 같다 — 그것이 평균 힘의 뜻이다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'energy-impulse-area',
        title: '충격량은 F-t 그래프의 넓이',
        desc: '충돌 중 힘은 짧은 시간에 솟았다 사라진다. 그 곡선 아래 넓이가 충격량이고 운동량 변화와 같다. '
            + '같은 넓이를 갖는 직사각형의 높이가 평균 힘이므로, 접촉 시간을 늘리면 평균 힘이 줄어든다.',
        svg: svg({ width: W, height: H, title: '충격량과 평균 힘', desc: 'F-t 곡선 아래 넓이와 같은 넓이의 직사각형', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 1차원 탄성 충돌 — 질량비가 결과를 정한다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 388;
    const S = 32;                     // 속도 1 당 화살표 길이(화소)
    const row = (y, r1, r2, u1, u2, tag1, tag2, note) => {
        const out = [];
        out.push(txt(24, y - 6, tag1, { cls: 'ink2', size: 'sm' }));
        out.push(txt(24, y + 11, tag2, { cls: 'ink2', size: 'sm' }));
        // 충돌 전
        out.push(ball(120, y, r1, 'f1'));
        out.push(px(120 + r1 + 8, y, 120 + r1 + 8 + 48, y, { cls: 's1', marker: 'ar1', width: 2.4 }));
        out.push(txt(120 + r1 + 32, y - 12, 'v', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(ball(272, y, r2, 'f3'));
        // 충돌 후
        out.push(ball(410, y, r1, 'f1'));
        out.push(u1 === 0
            ? txt(410, y - r1 - 10, '멈춤', { anchor: 'middle', cls: 'ink2', size: 'sm' })
            : px(410 + Math.sign(u1) * (r1 + 8), y, 410 + Math.sign(u1) * (r1 + 8 + S * Math.abs(u1)), y,
                { cls: 's1', marker: 'ar1', width: 2.4 }));
        out.push(ball(524, y, r2, 'f3'));
        out.push(px(524 + r2 + 8, y, 524 + r2 + 8 + S * u2, y, { cls: 's3', marker: 'ar3', width: 2.4 }));
        out.push(txt(470, y + 42, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    const body = [
        txt(196, 46, '충돌 전', { anchor: 'middle', cls: 'ink bold' }),
        txt(470, 46, '충돌 후', { anchor: 'middle', cls: 'ink bold' }),
        txt(24, 74, '파란 공 = 속도 v 로 부딪혀 오는 m~1,   초록 공 = 처음에 정지해 있던 m~2', { cls: 'ink2', size: 'sm' }),
        `<path class="gr" stroke-dasharray="5 4" d="M344 88 V${H - 20}"/>`,
        row(126, 12, 30, -0.95, 0.35, '가벼운 것이', '무거운 것에', '거의 그대로 되튄다 (−v 에 가깝다)'),
        `<path class="gr" d="M20 182 H${W - 20}"/>`,
        row(220, 20, 20, 0, 1, '질량이', '같으면', '속도를 통째로 맞바꾼다'),
        `<path class="gr" d="M20 278 H${W - 20}"/>`,
        row(320, 30, 12, 0.6, 1.8, '무거운 것이', '가벼운 것에', '가벼운 쪽이 최대 2v 로 튕겨 나간다'),
    ].join('');
    return {
        name: 'energy-collision-mass-ratio',
        title: '질량비에 따른 1차원 탄성 충돌',
        desc: '정지한 물체에 부딪히는 탄성 충돌의 결과는 질량비가 정한다. 가벼운 쪽이 부딪히면 거의 그대로 되튀고, '
            + '질량이 같으면 속도를 맞바꾸며, 무거운 쪽이 부딪히면 가벼운 쪽이 최대 2배 속도로 튕겨 나간다.',
        svg: svg({ width: W, height: H, title: '질량비와 탄성 충돌', desc: '세 가지 질량비의 충돌 전후 비교', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 질량중심 — 공중에서 터져도 질량중심은 포물선을 간다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 300;
    const y0 = x => 12 - 0.03 * (x - 20) ** 2;
    const frag = c => x => 12 - 0.03 * ((x - 20) / c) ** 2;
    const g = frame({ xRange: [-2, 56], yRange: [-1.5, 15], box: { x: 52, y: 46, w: 500, h: 190 } });
    const body = [
        g.axes({ xLabel: 'x (m)', yLabel: 'y (m)', xTicks: [0, 10, 20, 30, 50], yTicks: [0, 6, 12] }),
        g.curve(y0, { from: 0, to: 20, cls: 's1' }),
        g.curve(y0, { from: 20, to: 40, cls: 's1', dash: '7 5' }),
        g.curve(frag(0.4), { from: 20, to: 28, cls: 's2' }),
        g.curve(frag(1.6), { from: 20, to: 52, cls: 's3' }),
        `<circle cx="${g.X(20)}" cy="${g.Y(12)}" r="9" fill="var(--s2)" fill-opacity="0.35" stroke="var(--s2)" stroke-width="1.5"/>`,
        g.label([20, 12], '여기서 터진다', { dy: -16, anchor: 'middle', cls: 'ink' }),
        g.dot([28, 0], { cls: 'f2' }),
        g.dot([52, 0], { cls: 'f3' }),
        g.dot([40, 0], { cls: 'f1', r: 5 }),
        g.label([40, 0], '질량중심이 닿는 곳', { dy: 34, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        g.label([28, 0], '조각 1', { dx: -6, dy: -10, anchor: 'end', cls: 'ink2', size: 'sm' }),
        g.label([52, 0], '조각 2', { dy: -12, anchor: 'middle', cls: 'ink2', size: 'sm' }),
        legend(96, 70, [{ slot: 1, name: '질량중심의 경로' }, { slot: 2, name: '조각 1' }, { slot: 3, name: '조각 2' }]),
        txt(W - 14, H - 10, '두 조각이 어디로 날아가든 질량중심은 원래 포물선을 벗어나지 않는다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'energy-center-of-mass',
        title: '공중에서 터진 물체의 질량중심',
        desc: '포물선을 그리던 물체가 공중에서 두 조각으로 터져도, 폭발은 내부력이므로 질량중심에는 중력만 작용한다. '
            + '따라서 질량중심은 원래의 포물선을 그대로 따라가고 조각들은 그 양쪽으로 갈라진다.',
        svg: svg({ width: W, height: H, title: '폭발과 질량중심', desc: '두 조각의 경로와 질량중심의 포물선', body }),
    };
})());

/* ================================================================== *
 * 7장 — 회전 운동
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 9. 라디안의 정의
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 330;
    const circle = (cx, cy, r, extra = '') => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--grid)" stroke-width="1.4"${extra}/>`;
    const one = 180 / Math.PI;   // 1 rad ≈ 57.3°
    const c1 = [150, 180], R1 = 80;
    const p1 = [c1[0] + R1, c1[1]];
    const p2 = [c1[0] + R1 * Math.cos(RAD(one)), c1[1] - R1 * Math.sin(RAD(one))];
    const c2 = [432, 180], R2 = 80;
    const body = [
        txt(24, 34, '1 라디안이란', { cls: 'ink bold' }),
        txt(24, 58, 's = r θ  (θ 는 반드시 라디안)', { cls: 'ink' }),
        circle(...c1, R1),
        `<path class="s2" fill="none" stroke-width="4.5" d="M${p1[0]} ${p1[1]} A${R1} ${R1} 0 0 0 ${f2(p2[0])} ${f2(p2[1])}"/>`,
        px(c1[0], c1[1], p1[0], p1[1], { cls: 'ax', marker: 'ark', width: 1.5 }),
        px(c1[0], c1[1], p2[0], p2[1], { cls: 'ax', marker: 'ark', width: 1.5 }),
        txt(c1[0] + 40, c1[1] + 20, 'r', { anchor: 'middle', cls: 'ink' }),
        txt(164, 122, 'r', { anchor: 'middle', cls: 'ink' }),
        arc(c1[0], c1[1], 34, 0, one, 'θ = 1 rad'),
        txt(238, 130, '호의 길이 s = r', { cls: 'ink2', size: 'sm' }),
        txt(24, 288, '호의 길이가 반지름과 같아지는 각을 1 rad 이라 한다', { cls: 'ink2', size: 'sm' }),
        txt(24, 308, '1 rad ≈ 57.3°', { cls: 'ink2', size: 'sm' }),
        txt(330, 34, '한 바퀴는 몇 라디안인가', { cls: 'ink bold' }),
        `<circle cx="${c2[0]}" cy="${c2[1]}" r="${R2}" fill="none" stroke="var(--s3)" stroke-width="4.5"/>`,
        px(c2[0], c2[1], c2[0] + R2, c2[1], { cls: 'ax', marker: 'ark', width: 1.5 }),
        txt(c2[0] + 40, c2[1] + 20, 'r', { anchor: 'middle', cls: 'ink' }),
        txt(c2[0], c2[1] - R2 - 12, '호 = 원둘레 2πr', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(330, 288, '한 바퀴의 호는 원둘레 2πr 이므로 θ = 2πr / r = 2π', { cls: 'ink2', size: 'sm' }),
        txt(330, 308, '360° = 2π rad,  180° = π rad', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-radian',
        title: '라디안의 정의',
        desc: '호의 길이가 반지름과 같아지는 중심각이 1 라디안이다. 각을 호의 길이를 반지름으로 나눈 값으로 정의하면 '
            + '길이 단위가 서로 지워지므로 s = rθ 라는 간단한 관계가 성립한다. 한 바퀴는 2π 라디안이다.',
        svg: svg({ width: W, height: H, title: '라디안', desc: '호의 길이와 반지름이 같은 각이 1 rad', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 10. 토크 — 문을 미는 세 가지 방법 (위에서 본 그림)
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 384;
    const hx = 70, len = 330, hy1 = 168, hy2 = 300;
    const door = yy => `<rect x="${hx}" y="${yy - 8}" width="${len}" height="16" rx="3" fill="var(--grid)" fill-opacity="0.5" stroke="var(--ink2)" stroke-width="1.3"/>`
        + `<circle cx="${hx}" cy="${yy}" r="7" fill="none" stroke="var(--ink2)" stroke-width="2"/>`
        + `<circle cx="${hx}" cy="${yy}" r="2.5" class="ink2"/>`;
    const body = [
        txt(24, 32, '문을 위에서 내려다본 그림. 왼쪽 동그라미가 경첩(회전축)이다', { cls: 'ink bold' }),
        door(hy1),
        txt(hx, hy1 + 30, '축', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        // (가) 끝을 수직으로 민다 — 최대
        px(hx + len, hy1, hx + len, hy1 - 76, { cls: 's1', marker: 'ar1', width: 3 }),
        txt(hx + len + 8, hy1 - 40, 'F', { cls: 'ink' }),
        txt(hx + len + 8, hy1 - 76, '(가) τ = r F 로 최대', { cls: 'ink2', size: 'sm' }),
        // (나) 축 가까이 같은 힘 — 작다
        px(hx + 80, hy1, hx + 80, hy1 - 76, { cls: 's2', marker: 'ar2', width: 3 }),
        txt(hx + 88, hy1 - 40, 'F', { cls: 'ink' }),
        txt(hx + 88, hy1 - 76, '(나) 같은 F 라도 r 이 작으면 τ 도 작다', { cls: 'ink2', size: 'sm' }),
        // r 치수선
        px(hx, hy1 + 56, hx + len, hy1 + 56, { cls: 'ax', marker: 'ark', width: 1.4 }),
        txt(hx + len / 2, hy1 + 50, 'r (축에서 작용점까지)', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        `<path class="gr" stroke-dasharray="4 3" d="M${hx + len} ${hy1} V${hy1 + 62}"/>`,
        // (다) 문을 따라 미는 힘 — 작용선이 축을 지난다
        door(hy2),
        px(hx + len, hy2, hx + len + 96, hy2, { cls: 's3', marker: 'ar3', width: 3 }),
        txt(hx + len + 100, hy2 + 5, 'F', { cls: 'ink' }),
        `<path class="s3" fill="none" stroke-width="1.6" stroke-dasharray="6 4" d="M${hx - 26} ${hy2} H${hx + len + 108}"/>`,
        txt(24, hy2 - 32, '(다) 힘이 문을 따라 향하면 작용선이 축을 지난다 → τ = 0', { cls: 'ink2', size: 'sm' }),
        txt(hx - 30, hy2 + 26, '작용선을 늘이면 축을 그대로 지난다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 10, '손잡이를 경첩 반대쪽에 다는 이유, 그리고 문을 옆으로 밀면 안 열리는 이유', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-torque-arm',
        title: '토크는 힘만이 아니라 어디에 어떻게 거는가로 정해진다',
        desc: '같은 크기의 힘이라도 축에서 먼 곳에 수직으로 걸면 토크가 크고, 축 가까이 걸면 작다. '
            + '힘의 작용선을 늘였을 때 그 선이 축을 지나면 토크는 0 이라 문이 열리지 않는다.',
        svg: svg({ width: W, height: H, title: '토크와 모멘트 팔', desc: '문을 미는 세 가지 방법', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 11. 벡터곱과 오른손 법칙 — 모멘트 팔 r sin φ 도 함께
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 390;
    const ox = 150, oy = 306;                                  // 축 O 의 화소 위치
    const P = (u, v) => [ox + u + 0.35 * v, oy - 0.72 * v];    // 면 좌표 → 화소(비스듬히 본 평면)
    const plane = [P(-84, -46), P(272, -46), P(272, 150), P(-84, 150)];
    const rLen = 160, phi = 115;
    const fDir = [Math.cos(RAD(phi)), Math.sin(RAD(phi))];
    const rEnd = P(rLen, 0);
    const fTip = P(rLen + 100 * fDir[0], 100 * fDir[1]);
    const s = -rLen * fDir[0];                                 // O 에서 작용선에 내린 발까지의 매개변수
    const foot = P(rLen + s * fDir[0], s * fDir[1]);
    const ext = P(rLen + 150 * fDir[0], 150 * fDir[1]);
    const fAngPx = Math.atan2(oy - fTip[1] - (oy - rEnd[1]), fTip[0] - rEnd[0]) * 180 / Math.PI;
    const body = [
        fillPoly(plane, 'var(--grid)', 0.35),
        `<path d="M${plane.map(p => `${f2(p[0])} ${f2(p[1])}`).join(' L')} Z" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`,
        txt(plane[0][0] + 4, plane[0][1] + 18, 'r 과 F 가 놓인 면', { cls: 'ink2', size: 'sm' }),
        // 작용선(연장)과 모멘트 팔
        `<path class="gr" stroke-dasharray="5 4" d="M${f2(rEnd[0])} ${f2(rEnd[1])} L${f2(ext[0])} ${f2(ext[1])}"/>`,
        `<path class="s3" fill="none" stroke-width="2" stroke-dasharray="5 4" d="M${ox} ${oy} L${f2(foot[0])} ${f2(foot[1])}"/>`,
        txt((ox + foot[0]) / 2 - 4, (oy + foot[1]) / 2 - 10, 'd⊥ = r sin φ', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        // r 과 F
        px(ox, oy, rEnd[0], rEnd[1], { cls: 's1', marker: 'ar1', width: 3 }),
        txt((ox + rEnd[0]) / 2, oy + 22, 'r', { anchor: 'middle', cls: 'ink' }),
        px(rEnd[0], rEnd[1], fTip[0], fTip[1], { cls: 's2', marker: 'ar2', width: 3 }),
        txt(fTip[0] + 10, fTip[1] - 2, 'F', { cls: 'ink' }),
        arc(rEnd[0], rEnd[1], 32, 0, f2(fAngPx), 'φ'),
        `<circle cx="${ox}" cy="${oy}" r="4" class="ink2"/>`,
        txt(ox - 10, oy + 20, '축 O', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        // 감아쥐는 방향과 토크 벡터
        arcArrow(ox, oy, 74, 6, 104, { cls: 's3', marker: 'ar3', width: 2.2 }),
        txt(ox + 84, oy - 92, 'r 에서 F 쪽으로 감는다', { cls: 'ink2', size: 'sm' }),
        px(ox, oy, ox, oy - 176, { cls: 's3', marker: 'ar3', width: 3.2 }),
        txt(ox + 8, oy - 172, 'τ = r × F', { cls: 'ink' }),
        txt(ox + 8, oy - 154, '(면에 수직)', { cls: 'ink2', size: 'sm' }),
        // 설명
        txt(346, 54, '오른손 법칙', { cls: 'ink bold' }),
        txt(346, 78, '오른손 네 손가락을 r 에서 F 쪽으로', { cls: 'ink2', size: 'sm' }),
        txt(346, 96, '감아쥐면 세운 엄지가 τ 의 방향이다', { cls: 'ink2', size: 'sm' }),
        txt(346, 124, '크기: τ = r F sin φ = F d⊥', { cls: 'ink' }),
        txt(346, 150, 'φ = 90° 이면 τ = r F 로 가장 크고', { cls: 'ink2', size: 'sm' }),
        txt(346, 168, 'φ = 0° 또는 180° 이면 τ = 0 이다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 10, '평면 문제에서는 방향 대신 부호로 쓴다: 반시계 +, 시계 −', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-right-hand-rule',
        title: '벡터곱과 오른손 법칙',
        desc: '토크는 위치벡터 r 과 힘 F 의 벡터곱이다. 크기는 r F sin φ 이고 이는 힘의 작용선까지의 수직거리 '
            + 'd⊥ 에 힘을 곱한 것과 같다. 방향은 오른손 네 손가락을 r 에서 F 로 감을 때 엄지가 가리키는 쪽으로, '
            + 'r 과 F 가 놓인 면에 수직이다.',
        svg: svg({ width: W, height: H, title: '토크의 방향과 크기', desc: 'r, F, 모멘트 팔, 그리고 면에 수직인 토크 벡터', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 12. 관성모멘트 — 같은 질량도 퍼진 정도가 다르면 다르다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 600, H = 310;
    const unit = (cx, cy, arm, label, note) => [
        `<path class="gr" stroke-dasharray="6 4" d="M${cx} ${cy - 78} V${cy + 76}"/>`,
        `<path d="M${cx - arm} ${cy} H${cx + arm}" stroke="var(--ink2)" stroke-width="2.5" fill="none"/>`,
        ball(cx - arm, cy, 16, 'f1'),
        ball(cx + arm, cy, 16, 'f1'),
        txt(cx - arm, cy - 26, 'm', { anchor: 'middle', cls: 'ink' }),
        txt(cx + arm, cy - 26, 'm', { anchor: 'middle', cls: 'ink' }),
        px(cx, cy + 46, cx + arm, cy + 46, { cls: 'ax', marker: 'ark', width: 1.4 }),
        txt(cx + arm / 2, cy + 64, label, { anchor: 'middle', cls: 'ink' }),
        txt(cx, cy - 88, '회전축', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(cx, cy + 100, note, { anchor: 'middle', cls: 'ink bold' }),
    ].join('');
    const body = [
        txt(24, 32, '질량이 같아도 축에서 멀리 퍼져 있으면 돌리기 어렵다', { cls: 'ink bold' }),
        unit(160, 160, 46, 'r', 'I = 2 m r²'),
        unit(430, 160, 92, '2r', 'I = 8 m r²  (4배)'),
        txt(W / 2, H - 10, '거리가 제곱으로 들어가므로 두 배 벌리면 관성모멘트는 네 배가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-inertia-distribution',
        title: '관성모멘트는 질량이 축에서 얼마나 멀리 퍼졌는가로 정해진다',
        desc: '같은 두 질량을 축에서 r 만큼 떨어뜨린 경우와 2r 만큼 떨어뜨린 경우를 비교한다. '
            + '관성모멘트는 거리의 제곱에 비례하므로 거리를 두 배로 하면 네 배가 된다.',
        svg: svg({ width: W, height: H, title: '관성모멘트와 질량 분포', desc: '같은 질량, 다른 반지름의 아령 둘', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 13. 미끄러짐 없이 구르기 — 접촉점의 속도는 0
 * ------------------------------------------------------------------ */
add((() => {
    const W = 640, H = 344;
    const cx = 200, cy = 190, R = 92, gy = cy + R;
    const spokes = [0, 45, 90, 135].map(a =>
        `<path class="gr" d="M${f2(cx - R * Math.cos(RAD(a)))} ${f2(cy + R * Math.sin(RAD(a)))} L${f2(cx + R * Math.cos(RAD(a)))} ${f2(cy - R * Math.sin(RAD(a)))}"/>`).join('');
    const body = [
        ground(40, 400, gy),
        `<circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="var(--ink2)" stroke-width="2"/>`,
        spokes,
        `<circle cx="${cx}" cy="${cy}" r="4" class="ink2"/>`,
        arcArrow(cx, cy, 50, 132, -56, { cls: 's3', marker: 'ar3', width: 2.4 }),
        txt(cx - 64, cy - 36, 'ω', { cls: 'ink' }),
        // 속도 화살표
        px(cx, cy, cx + 72, cy, { cls: 's1', marker: 'ar1', width: 2.8 }),
        txt(cx + 78, cy + 5, 'v = Rω', { cls: 'ink' }),
        px(cx, cy - R, cx + 144, cy - R, { cls: 's2', marker: 'ar2', width: 2.8 }),
        txt(cx + 150, cy - R + 5, '2v — 맨 위가 가장 빠르다', { cls: 'ink' }),
        `<circle cx="${cx}" cy="${gy}" r="6" fill="var(--s3)" fill-opacity="0.9"/>`,
        txt(cx + 14, gy + 22, '0 — 접촉점은 그 순간 정지해 있다', { cls: 'ink' }),
        txt(24, 34, '구르기 = 질량중심의 병진 + 중심 둘레의 회전', { cls: 'ink bold' }),
        txt(24, 56, '미끄러지지 않으면 v = R ω', { cls: 'ink' }),
        txt(420, 176, '구를 때의 운동에너지', { cls: 'ink bold' }),
        txt(420, 200, 'K = ½ M v² + ½ I ω²', { cls: 'ink' }),
        txt(420, 230, '접촉점이 정지해 있으므로', { cls: 'ink2', size: 'sm' }),
        txt(420, 248, '정지마찰은 일을 하지 않는다', { cls: 'ink2', size: 'sm' }),
        txt(420, 266, '→ 에너지 보존을 그대로 쓸 수 있다', { cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-rolling',
        title: '미끄러짐 없이 구르는 바퀴의 속도',
        desc: '구르는 바퀴에서 중심은 v = Rω 로 나아가고, 맨 위 점은 2v 로 가장 빠르며, 바닥에 닿은 점은 '
            + '그 순간 속도가 0 이다. 접촉점이 정지해 있으므로 정지마찰이 일을 하지 않고 에너지가 보존된다.',
        svg: svg({ width: W, height: H, title: '구르는 바퀴의 속도 분포', desc: '중심 v, 꼭대기 2v, 접촉점 0', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 14. 각운동량 보존 — 팔을 당기면 빨라진다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 620, H = 372;
    const unit = (cx, cy, arm, spin, cap, sub) => [
        `<circle cx="${cx}" cy="${cy}" r="${arm + 24}" fill="var(--grid)" fill-opacity="0.25" stroke="none"/>`,
        `<path d="M${cx - arm} ${cy} H${cx + arm}" stroke="var(--ink2)" stroke-width="2.5"/>`,
        ball(cx - arm, cy, 15, 'f1'),
        ball(cx + arm, cy, 15, 'f1'),
        `<circle cx="${cx}" cy="${cy}" r="4" class="ink2"/>`,
        arcArrow(cx, cy, arm + 38, 40, 40 + spin, { cls: 's2', marker: 'ar2', width: 3 }),
        txt(cx, 296, cap, { anchor: 'middle', cls: 'ink bold' }),
        txt(cx, 316, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    const body = [
        txt(24, 32, '축 방향으로 외부 토크가 없으면 L = I ω 는 변하지 않는다', { cls: 'ink bold' }),
        unit(162, 170, 66, 70, '팔을 벌리면', 'I 크다 → ω 작다'),
        unit(440, 170, 28, 200, '팔을 당기면', 'I 작다 → ω 크다'),
        px(280, 170, 356, 170, { cls: 'ax', marker: 'ark', width: 1.6 }),
        txt(318, 158, '팔을 당긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        txt(W / 2, H - 26, 'L 은 그대로지만 K = L² / (2 I) 는 커진다', { anchor: 'middle', cls: 'ink' }),
        txt(W / 2, H - 6, '그 에너지는 팔을 당기는 근육이 한 일에서 온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-angular-momentum-spin',
        title: '각운동량 보존 — 팔을 당기면 빨리 돈다',
        desc: '회전축 방향의 외부 토크가 없으면 각운동량 I 곱하기 오메가가 일정하다. 팔을 당겨 관성모멘트를 줄이면 '
            + '각속도가 그만큼 커진다. 각운동량은 보존되지만 회전 운동에너지는 늘어나며, 그 에너지는 팔을 당긴 근육이 한 일이다.',
        svg: svg({ width: W, height: H, title: '팔을 당기면 빨라지는 이유', desc: '관성모멘트가 줄면 각속도가 커진다', body }),
    };
})());

/* ------------------------------------------------------------------ *
 * 15. 강체의 평형 — 받침점을 축으로 잡으면 미지수가 사라진다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 580, H = 300;
    const px0 = 290, by = 150;      // 받침점, 막대 높이
    const x1 = px0 - 160, x2 = px0 + 96;
    const body = [
        `<rect x="${px0 - 220}" y="${by - 7}" width="440" height="14" rx="3" fill="var(--grid)" fill-opacity="0.5" stroke="var(--ink2)" stroke-width="1.3"/>`,
        `<path d="M${px0} ${by + 8} L${px0 - 22} ${by + 52} L${px0 + 22} ${by + 52} Z" fill="none" stroke="var(--ink2)" stroke-width="1.5"/>`,
        ground(px0 - 70, px0 + 70, by + 52),
        px(px0, by + 4, px0, by - 78, { cls: 's3', marker: 'ar3', width: 2.6 }),
        txt(px0 - 10, by - 68, 'N (받침점이 미는 힘)', { anchor: 'end', cls: 'ink2', size: 'sm' }),
        // 두 무게
        boxRect(x1 - 22, by - 46, 44, 40),
        txt(x1, by - 22, 'm~1', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(x1, by + 10, x1, by + 78, { cls: 's1', marker: 'ar1', width: 2.6 }),
        txt(x1 - 8, by + 74, 'm~1 g', { anchor: 'end', cls: 'ink' }),
        boxRect(x2 - 26, by - 58, 52, 52),
        txt(x2, by - 28, 'm~2', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
        px(x2, by + 10, x2, by + 96, { cls: 's2', marker: 'ar2', width: 2.6 }),
        txt(x2 + 8, by + 92, 'm~2 g', { cls: 'ink' }),
        // 거리
        px(px0, by + 112, x1, by + 112, { cls: 'ax', marker: 'ark', width: 1.4 }),
        txt((px0 + x1) / 2, by + 106, 'd~1', { anchor: 'middle', cls: 'ink' }),
        px(px0, by + 112, x2, by + 112, { cls: 'ax', marker: 'ark', width: 1.4 }),
        txt((px0 + x2) / 2, by + 106, 'd~2', { anchor: 'middle', cls: 'ink' }),
        `<path class="gr" stroke-dasharray="4 3" d="M${x1} ${by} V${by + 118}"/>`,
        `<path class="gr" stroke-dasharray="4 3" d="M${x2} ${by} V${by + 118}"/>`,
        `<path class="gr" stroke-dasharray="4 3" d="M${px0} ${by} V${by + 118}"/>`,
        txt(24, 34, '평형의 두 조건: 힘의 합도 0, 토크의 합도 0', { cls: 'ink bold' }),
        txt(24, 60, '받침점을 축으로 잡으면 N 의 토크가 0 이라 식에서 사라진다', { cls: 'ink2', size: 'sm' }),
        txt(W - 14, H - 30, 'm~1 g d~1 = m~2 g d~2', { anchor: 'end', cls: 'ink' }),
        txt(W - 14, H - 10, '반시계 토크와 시계 토크가 같으면 기울지 않는다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
    ].join('');
    return {
        name: 'rot-lever-balance',
        title: '지레의 평형과 축 선택',
        desc: '받침점 위의 막대가 기울지 않으려면 힘의 합과 토크의 합이 모두 0 이어야 한다. '
            + '토크를 셀 축을 받침점으로 잡으면 받침점이 미는 힘의 모멘트 팔이 0 이 되어 그 미지수가 식에서 사라진다.',
        svg: svg({ width: W, height: H, title: '지레의 평형', desc: '받침점을 축으로 잡은 토크 평형', body }),
    };
})());

export default figures;
