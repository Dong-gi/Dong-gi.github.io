/**
 * 12장 자기, 13장 맥스웰 방정식과 전자기파의 그림.
 *
 * physics.mjs 와 같은 형식이다. 각 항목은 { name, title, desc, svg } 를 돌려주고
 * name 이 파일 이름(/figures/physics/<name>.svg)이 된다.
 * 이름은 mag- / em- 으로 시작한다.
 *
 * 자기는 3차원 방향 관계라 화면에 수직인 방향을 자주 그려야 한다.
 * 화살촉이 보이는 ⊙ 는 화면 밖으로, 화살깃이 보이는 ⊗ 는 화면 안으로 향하는 방향이다.
 * 이 두 표시가 이 파일의 거의 모든 그림에 나온다.
 *
 * SVG 안에는 수식을 쓸 수 없으므로(그림이 <img> 로 들어가 MathJax 가 닿지 않는다)
 * 라벨은 유니코드 그리스 문자와 `B~0` 꼴의 아래첨자 표기로 적는다.
 */
import { svg, frame, arc, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const RAD = Math.PI / 180;

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

/** 화면 안으로 들어가는 방향 ⊗ (화살의 깃) */
function into(x, y, o = {}) {
    const { r = 7, cls = 'ink2', w = 1.3 } = o;
    const k = r * 0.66;
    return `<g stroke="var(--${cls})" stroke-width="${w}" fill="none">`
        + `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}"/>`
        + `<path d="M${r2(x - k)} ${r2(y - k)} L${r2(x + k)} ${r2(y + k)} M${r2(x - k)} ${r2(y + k)} L${r2(x + k)} ${r2(y - k)}"/></g>`;
}

/** 화면 밖으로 나오는 방향 ⊙ (화살촉) */
function outof(x, y, o = {}) {
    const { r = 7, cls = 'ink2', w = 1.3 } = o;
    return `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="none" stroke="var(--${cls})" stroke-width="${w}"/>`
        + `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r2(r * 0.3)}" fill="var(--${cls})"/>`;
}

/** 균일한 장을 뜻하는 ⊗ 또는 ⊙ 격자 */
function fieldGrid(x0, y0, x1, y1, nx, ny, kind, o = {}) {
    const out = [];
    for (let i = 0; i < nx; i += 1) {
        for (let j = 0; j < ny; j += 1) {
            const x = x0 + ((x1 - x0) * (i + 0.5)) / nx;
            const y = y0 + ((y1 - y0) * (j + 0.5)) / ny;
            out.push(kind === 'in' ? into(x, y, o) : outof(x, y, o));
        }
    }
    return out.join('');
}

/** 나란한 화살표로 그린 균일한 장. dir 은 'right' 만 쓴다. */
function uniformArrows(x0, x1, ys, o = {}) {
    return ys.map(y => px(x0, y, x1, y, { width: 1.8, ...o })).join('');
}

/** 원호 화살표(전류의 회전 방향 등). 각은 도, 반시계가 양. */
function curl(cx, cy, r, a1, a2, o = {}) {
    const { cls = 's3', marker = 'ar3', width = 2.2, dash } = o;
    const p = a => `${r2(cx + r * Math.cos(a * RAD))} ${r2(cy - r * Math.sin(a * RAD))}`;
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    const sweep = a2 > a1 ? 0 : 1;   // SVG 는 y축이 뒤집혀 있어 반시계가 sweep 0
    return `<path class="${cls}" fill="none" stroke-width="${width}"${dash ? ` stroke-dasharray="${dash}"` : ''} marker-end="url(#${marker})" d="M${p(a1)} A${r} ${r} 0 ${large} ${sweep} ${p(a2)}"/>`;
}

/** 막대자석. 왼쪽이 S, 오른쪽이 N. */
function barMagnet(cx, cy, len, h) {
    const x = cx - len / 2;
    return `<rect x="${r2(x)}" y="${r2(cy - h / 2)}" width="${r2(len / 2)}" height="${h}" fill="var(--s1)" fill-opacity="0.16" stroke="var(--ink2)" stroke-width="1.5"/>`
        + `<rect x="${r2(cx)}" y="${r2(cy - h / 2)}" width="${r2(len / 2)}" height="${h}" fill="var(--s2)" fill-opacity="0.20" stroke="var(--ink2)" stroke-width="1.5"/>`
        + txt(cx - len / 4, cy + 6, 'S', { anchor: 'middle', cls: 'ink bold' })
        + txt(cx + len / 4, cy + 6, 'N', { anchor: 'middle', cls: 'ink bold' });
}

/** 테두리만 있는 상자. */
function boxr(x, y, w, h, label, cls = 'ink2') {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="3" fill="none" stroke="var(--${cls})" stroke-width="1.6"/>`
        + (label ? txt(x + w / 2, y + h / 2 + 4, label, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 사각 고리(닫힌 회로). */
function rectLoop(x, y, w, h, cls = 'ink2', width = 2) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" fill="none" stroke="var(--${cls})" stroke-width="${width}"/>`;
}

/** 화살표가 없는 굵은 선분. 고리를 옆에서 본 모습처럼 방향이 없는 대상에 쓴다. */
function seg(x1, y1, x2, y2, o = {}) {
    const { cls = 'ink', width = 4, dash } = o;
    return `<path stroke="var(--${cls})" fill="none" stroke-width="${width}" stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/* ================================================================== *
 * 12장 — 자기
 * ================================================================== */

/* 1. 막대자석의 자기력선 — 시작도 끝도 없는 닫힌 곡선 */
add((() => {
    const W = 660, H = 430;
    const cx = 330, cy = 178, len = 150, mh = 42;
    const sx = cx - len / 2, nx = cx + len / 2;
    const b = [txt(16, 26, '막대자석의 자기력선', { cls: 'ink bold' })];
    for (const k of [52, 88, 128, 172]) {
        const d = k * 1.9;
        for (const sgn of [-1, 1]) {
            b.push(`<path class="cv s1" d="M${nx} ${cy} C${r2(nx + d)} ${r2(cy + sgn * k)} ${r2(sx - d)} ${r2(cy + sgn * k)} ${sx} ${cy}"/>`);
            const ay = r2(cy + sgn * 0.75 * k);
            b.push(px(cx + 20, ay, cx - 20, ay, { cls: 's1', marker: 'ar1', width: 2 }));
        }
    }
    b.push(barMagnet(cx, cy, len, mh));
    b.push(px(sx + 16, cy, nx - 16, cy, { cls: 's2', marker: 'ar2', width: 2.4 }));
    b.push(txt(16, 52, '밖에서는 N 에서 나와 S 로 들어가고', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 68, '안에서는 S 에서 N 으로 이어진다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, 52, '어느 선을 따라가도 제자리로 돌아온다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, 68, '= 시작점도 끝점도 없다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 아래: 잘라도 홀극이 안 나온다
    const by = 390;
    b.push(`<path class="gr" d="M20 ${by - 48} H${W - 20}"/>`);
    b.push(txt(20, by - 26, '반으로 잘라도', { cls: 'ink2', size: 'sm' }));
    b.push(barMagnet(150, by, 110, 30));
    b.push(txt(150, by + 32, '자르기 전', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(232, by + 5, '→', { anchor: 'middle', cls: 'ink' }));
    b.push(barMagnet(305, by, 88, 30));
    b.push(barMagnet(410, by, 88, 30));
    b.push(txt(357, by + 32, 'N 만 있는 조각은 나오지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, by + 5, '자기 홀극은 없다', { anchor: 'end', cls: 'ink bold' }));
    return {
        name: 'mag-bar-magnet-field',
        title: '막대자석의 자기력선',
        desc: '자기력선은 자석 밖에서 N 극에서 나와 S 극으로 들어가고 자석 안에서 S 에서 N 으로 이어져 '
            + '닫힌 곡선을 이룬다. 전기력선과 달리 시작하는 점도 끝나는 점도 없다. '
            + '자석을 반으로 잘라도 N 극만 있는 조각은 나오지 않는다.',
        svg: svg({ width: W, height: H, title: '막대자석의 자기력선', desc: '닫힌 곡선을 이루는 자기력선과 홀극의 부재', body: b.join('') }),
    };
})());

/* 2. 로런츠 힘의 오른손 법칙 — 화면 안팎 표기까지 함께 소개한다 */
add((() => {
    const W = 660, H = 380;
    const b = [txt(16, 26, '자기력의 방향 — 오른손 법칙', { cls: 'ink bold' })];
    b.push(txt(16, 48, '오른손 네 손가락을 v 에서 B 쪽으로 감아쥐면 세운 엄지가 v × B 방향이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 64, '전하가 음수면 힘은 그 반대 방향이다.', { cls: 'ink2', size: 'sm' }));
    const panel = (x0, title, chargeLabel, chargeCls, kind, note, parallel) => {
        const cx = x0 + 92, cy = 200;
        const g = [`<rect x="${x0}" y="90" width="184" height="200" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        g.push(txt(cx, 112, title, { anchor: 'middle', cls: 'ink bold' }));
        g.push(`<circle cx="${cx - 44}" cy="${cy}" r="9" fill="var(--${chargeCls})" fill-opacity="0.85"/>`);
        g.push(txt(cx - 44, cy + 4, chargeLabel, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(px(cx - 32, cy, cx + 42, cy, { cls: 's1', marker: 'ar1' }));
        g.push(txt(cx + 46, cy + 5, 'v', { cls: 'ink' }));
        if (parallel) {
            g.push(px(cx - 32, cy + 26, cx + 42, cy + 26, { cls: 's2', marker: 'ar2' }));
            g.push(txt(cx + 46, cy + 31, 'B', { cls: 'ink' }));
            g.push(txt(cx, cy - 40, 'F = 0', { anchor: 'middle', cls: 'ink bold' }));
        } else {
            g.push(px(cx - 44, cy - 10, cx - 44, cy - 66, { cls: 's2', marker: 'ar2' }));
            g.push(txt(cx - 40, cy - 70, 'B', { cls: 'ink' }));
            g.push(kind === 'out' ? outof(cx + 6, cy + 48, { r: 13, cls: 's3', w: 2 }) : into(cx + 6, cy + 48, { r: 13, cls: 's3', w: 2 }));
            g.push(txt(cx + 26, cy + 53, 'F', { cls: 'ink' }));
        }
        g.push(txt(cx, 278, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return g.join('');
    };
    b.push(panel(22, '양전하', '+', 's2', 'out', '힘은 화면 밖으로'));
    b.push(panel(238, '음전하', '−', 's1', 'in', '힘은 화면 안으로'));
    b.push(panel(454, 'v 와 B 가 나란하면', '+', 's2', 'out', '감아쥘 각이 없다', true));
    b.push(txt(16, 322, '화면에 수직인 방향의 표기', { cls: 'ink bold' }));
    b.push(outof(40, 348, { r: 10, cls: 'ink2', w: 1.6 }));
    b.push(txt(58, 353, '화면 밖으로 (다가오는 화살촉)', { cls: 'ink2', size: 'sm' }));
    b.push(into(300, 348, { r: 10, cls: 'ink2', w: 1.6 }));
    b.push(txt(318, 353, '화면 안으로 (멀어지는 화살깃)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, 353, 'F = |q| v B sin θ', { anchor: 'end', cls: 'ink bold' }));
    return {
        name: 'mag-right-hand-vxb',
        title: '로런츠 힘의 방향과 오른손 법칙',
        desc: '속도 v 에서 자기장 B 쪽으로 오른손 네 손가락을 감아쥐면 엄지가 v × B 방향을 가리킨다. '
            + '양전하는 그 방향으로, 음전하는 반대 방향으로 힘을 받는다. 속도와 자기장이 나란하면 힘이 0이다. '
            + '화면에 수직인 방향은 밖으로 나오면 동그라미 안의 점, 안으로 들어가면 동그라미 안의 가위표로 그린다.',
        svg: svg({ width: W, height: H, title: '자기력의 방향', desc: 'v × B 의 오른손 법칙과 화면 안팎 표기', body: b.join('') }),
    };
})());

/* 3. 균일한 자기장 속 전하의 원운동 — 반지름은 속력에 비례하고 주기는 무관 */
add((() => {
    const W = 640, H = 400;
    const ex = 210, ey = 348;             // 들어오는 자리
    const r1 = 62, r2b = 108;
    const b = [fieldGrid(30, 96, 424, 368, 6, 5, 'in', { r: 6, cls: 'ink2', w: 1.1 })];
    b.unshift(txt(16, 26, '자기장에 수직으로 들어간 전하는 원을 그린다', { cls: 'ink bold' }));
    b.push(txt(16, 48, '⊗ 는 화면 안으로 향하는 균일한 자기장 B', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 72, '실선 = 느린 전하,  점선 = 빠른 전하', { cls: 'ink2', size: 'sm' }));
    for (const [r, cls, mk, dash] of [[r1, 's1', 'ar1', '0'], [r2b, 's2', 'ar2', '7 5']]) {
        b.push(`<circle cx="${ex}" cy="${r2(ey - r)}" r="${r}" fill="none" stroke="var(--${cls})" stroke-width="2.4" stroke-dasharray="${dash}"/>`);
        b.push(px(ex + r - 4, r2(ey - r), ex + r - 4, r2(ey - r - 26), { cls, marker: mk, width: 2 }));
    }
    b.push(`<circle cx="${ex}" cy="${ey}" r="6" fill="var(--s2)"/>`);
    b.push(px(ex + 8, ey, ex + 74, ey, { cls: 's1', marker: 'ar1' }));
    b.push(txt(ex + 80, ey + 5, 'v', { cls: 'ink' }));
    b.push(px(ex, ey - 8, ex, ey - 54, { cls: 's3', marker: 'ar3' }));
    b.push(txt(ex - 8, ey - 44, 'F (언제나 중심 쪽)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(ex + 6, ey + 26, '+q 가 여기로 들어온다', { cls: 'ink2', size: 'sm' }));
    // 오른쪽 요약
    b.push(`<path class="gr" d="M452 90 V368"/>`);
    b.push(txt(474, 118, '자기력은 속도에 수직이라', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 136, '속력을 바꾸지 못한다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 154, '방향만 계속 바꾸므로 원이 된다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 194, 'r = m v / (|q| B)', { cls: 'ink bold' }));
    b.push(txt(474, 214, '속력이 두 배면 원도 두 배', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 252, 'T = 2π m / (|q| B)', { cls: 'ink bold' }));
    b.push(txt(474, 272, '속력이 들어 있지 않다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 290, '= 한 바퀴 도는 시간은 같다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 330, '큰 원을 그리는 전하는', { cls: 'ink2', size: 'sm' }));
    b.push(txt(474, 348, '그만큼 빨리 돌기 때문이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-cyclotron',
        title: '균일한 자기장 속 전하의 원운동',
        desc: '자기력은 속도에 수직이므로 속력을 바꾸지 못하고 방향만 바꾼다. 그래서 궤도가 원이 된다. '
            + '원의 반지름은 속력에 비례하지만 한 바퀴 도는 시간은 속력과 무관하다. '
            + '빠른 전하는 큰 원을 그리는 대신 그만큼 빨리 돌기 때문이다.',
        svg: svg({ width: W, height: H, title: '자기장 속 전하의 원운동', desc: '반지름은 속력에 비례하고 주기는 속력과 무관하다', body: b.join('') }),
    };
})());

/* 4. 전류가 흐르는 도선이 받는 힘 — 각도가 조건이다 */
add((() => {
    const W = 640, H = 330;
    const b = [txt(16, 26, '자기장 안에 놓인 도선이 받는 힘', { cls: 'ink bold' })];
    b.push(txt(16, 48, 'B 는 오른쪽 방향(가는 화살표). 도선의 각도만 바꾼다.', { cls: 'ink2', size: 'sm' }));
    const panel = (x0, deg, tag, force) => {
        const cx = x0 + 92, cy = 190, L = 66;
        const g = [`<rect x="${x0}" y="88" width="184" height="212" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        g.push(uniformArrows(x0 + 12, x0 + 172, [128, 152, 176, 268], { cls: 's2', marker: 'ar2', width: 1.4 }));
        const a = deg * RAD;
        const dx = L * Math.cos(a), dy = -L * Math.sin(a);
        g.push(px(cx - dx, cy - dy, cx + dx, cy + dy, { cls: 's1', marker: 'ar1', width: 3 }));
        g.push(txt(cx + dx + 8, cy + dy + 4, 'I', { cls: 'ink' }));
        if (force) {
            g.push(into(cx + 48, cy + 34, { r: 12, cls: 's3', w: 2 }));
            g.push(txt(cx + 64, cy + 39, 'F', { cls: 'ink' }));
        } else {
            g.push(txt(cx, cy + 39, 'F = 0', { anchor: 'middle', cls: 'ink bold' }));
        }
        g.push(txt(cx, 112, tag, { anchor: 'middle', cls: 'ink bold' }));
        return g.join('');
    };
    b.push(panel(16, 90, 'θ = 90°  →  F = B I L', true));
    b.push(panel(228, 30, 'θ = 30°  →  F = 0.5 B I L', true));
    b.push(panel(440, 0, 'θ = 0°  →  F = 0', false));
    b.push(txt(16, H - 12, 'F = B I L sin θ.  θ 는 전류 방향과 B 사이의 각이고, 힘은 둘 모두에 수직이라 화면 안쪽(⊗)을 향한다',
        { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, 48, '나란하면 힘이 0이다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-force-on-wire',
        title: '전류가 흐르는 도선이 자기장에서 받는 힘',
        desc: '힘의 크기는 B I L sin θ 이고 θ 는 전류와 자기장 사이의 각이다. 수직일 때 가장 크고 '
            + '나란할 때 0이다. 힘의 방향은 전류와 자기장 모두에 수직이므로 이 그림에서는 화면 안쪽을 향한다.',
        svg: svg({ width: W, height: H, title: '도선이 받는 힘', desc: '각도에 따라 달라지는 B I L sin θ', body: b.join('') }),
    };
})());

/* 5. 전류 고리에 걸리는 토크 — 전동기의 원리 */
add((() => {
    const W = 660, H = 370;
    const b = [txt(16, 26, '전류 고리는 자기장 안에서 돌아간다', { cls: 'ink bold' })];
    // 왼쪽: 정면도
    const lx = 66, ly = 132, lw = 150, lh = 132;
    b.push(uniformArrows(28, 256, [110, 288], { cls: 's2', marker: 'ar2', width: 1.4 }));
    b.push(txt(260, 114, 'B', { cls: 'ink' }));
    b.push(rectLoop(lx, ly, lw, lh, 'ink2', 2.4));
    // 전류 방향 (반시계)
    b.push(px(lx, ly + lh - 18, lx, ly + 18, { cls: 's1', marker: 'ar1', width: 2 }));
    b.push(px(lx + lw, ly + 18, lx + lw, ly + lh - 18, { cls: 's1', marker: 'ar1', width: 2 }));
    b.push(txt(lx - 10, ly + 34, 'I', { anchor: 'end', cls: 'ink' }));
    b.push(into(lx, ly + lh / 2, { r: 12, cls: 's3', w: 2 }));
    b.push(outof(lx + lw, ly + lh / 2, { r: 12, cls: 's3', w: 2 }));
    b.push(txt(lx + 18, ly + lh / 2 - 14, '힘은 안으로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(lx + lw - 18, ly + lh / 2 + 26, '힘은 밖으로', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(`<path class="gr" stroke-dasharray="5 4" d="M${lx + lw / 2} ${ly - 24} V${ly + lh + 24}"/>`);
    b.push(txt(lx + lw / 2, ly - 30, '회전축', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 334, '세로 두 변만 힘을 받고 그 방향이 서로 반대라', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 352, '고리가 돈다', { cls: 'ink2', size: 'sm' }));
    // 오른쪽: 위에서 본 그림
    b.push(`<path class="gr" d="M334 64 V344"/>`);
    const cx = 486, cy = 196, half = 70, th = 40;
    b.push(txt(360, 92, '위에서 내려다보면', { cls: 'ink bold' }));
    b.push(uniformArrows(364, 636, [136, 256], { cls: 's2', marker: 'ar2', width: 1.4 }));
    b.push(txt(614, 128, 'B', { cls: 'ink' }));
    const ux = Math.cos((90 + th) * RAD), uy = -Math.sin((90 + th) * RAD);
    b.push(seg(cx - half * ux, cy - half * uy, cx + half * ux, cy + half * uy, { cls: 'ink', width: 4.5 }));
    b.push(txt(360, 288, '굵은 선이 고리를 옆에서 본 모습', { cls: 'ink2', size: 'sm' }));
    b.push(px(cx, cy, cx + 66 * Math.cos(th * RAD), cy - 66 * Math.sin(th * RAD), { cls: 's1', marker: 'ar1' }));
    b.push(txt(cx + 70 * Math.cos(th * RAD) + 4, cy - 66 * Math.sin(th * RAD), 'μ', { cls: 'ink' }));
    b.push(arc(cx, cy, 42, 0, th, 'θ'));
    b.push(`<circle cx="${cx}" cy="${cy}" r="3.5" class="f3"/>`);
    b.push(txt(360, 316, 'μ = N I A 는 고리 면에 수직인 화살표다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(360, 334, 'τ = μ B sin θ', { cls: 'ink bold' }));
    b.push(txt(360, 352, 'μ 가 B 와 나란해지면 토크가 0 이 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-current-loop-torque',
        title: '자기장 안의 전류 고리에 걸리는 토크',
        desc: '자기장과 나란한 두 변은 힘을 받지 않고, 자기장에 수직인 두 변만 서로 반대 방향의 힘을 받아 '
            + '고리를 돌린다. 고리 면에 수직인 자기모멘트 μ = N I A 를 쓰면 토크는 μ B sin θ 이고, '
            + 'μ 가 B 와 나란해진 자리에서 0이 된다. 전동기와 검류계가 이 토크로 움직인다.',
        svg: svg({ width: W, height: H, title: '전류 고리의 토크', desc: '반대 방향 두 힘이 만드는 회전', body: b.join('') }),
    };
})());

/* 6. 직선 도선이 만드는 자기장 — 오른손 법칙 두 번째 용법 */
add((() => {
    const W = 660, H = 366;
    const b = [txt(16, 26, '직선 도선 둘레의 자기장', { cls: 'ink bold' })];
    b.push(txt(16, 48, '오른손 엄지를 전류 방향으로 세우면, 감아쥔 네 손가락이 자기장의 방향이다.', { cls: 'ink2', size: 'sm' }));
    // 왼쪽: 도선이 화면에 수직
    const cx = 170, cy = 208;
    b.push(txt(16, 76, '도선이 화면에 수직일 때 (전류가 나온다)', { cls: 'ink2', size: 'sm' }));
    for (const [r, w] of [[32, 2.2], [58, 1.8], [86, 1.4], [116, 1.1]]) {
        b.push(`<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--s1)" stroke-width="${w}"/>`);
        b.push(curl(cx, cy, r, 84, 96, { cls: 's1', marker: 'ar1', width: w + 0.4 }));
    }
    b.push(outof(cx, cy, { r: 12, cls: 's2', w: 2.2 }));
    b.push(txt(cx + 20, cy + 5, 'I', { cls: 'ink' }));
    b.push(txt(16, 348, '멀수록 원이 성기다 — B 는 1/r 로 줄어든다', { cls: 'ink2', size: 'sm' }));
    // 오른쪽: 도선이 화면 안에
    b.push(`<path class="gr" d="M330 64 V352"/>`);
    const wx = 498;
    b.push(txt(360, 76, '도선이 화면 안에 있을 때', { cls: 'ink2', size: 'sm' }));
    b.push(px(wx, 296, wx, 112, { cls: 's2', marker: 'ar2', width: 3 }));
    b.push(txt(wx + 8, 110, 'I', { cls: 'ink' }));
    for (const dx of [44, 82, 120]) {
        b.push(into(wx + dx, 152, { r: 8, cls: 's1', w: 1.5 }));
        b.push(into(wx + dx, 254, { r: 8, cls: 's1', w: 1.5 }));
        b.push(outof(wx - dx, 152, { r: 8, cls: 's1', w: 1.5 }));
        b.push(outof(wx - dx, 254, { r: 8, cls: 's1', w: 1.5 }));
    }
    b.push(txt(wx + 44, 208, '오른쪽은 안으로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(wx - 44, 208, '왼쪽은 밖으로', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(360, 348, 'B = μ~0 I / (2π r)', { cls: 'ink bold' }));
    return {
        name: 'mag-wire-field',
        title: '직선 도선이 만드는 자기장',
        desc: '전류가 흐르는 곧은 도선 둘레의 자기장은 도선을 축으로 하는 동심원이다. 오른손 엄지를 전류 '
            + '방향으로 세우면 감아쥔 손가락이 자기장의 방향이다. 세기는 도선에서 멀어질수록 1/r 로 줄어든다.',
        svg: svg({ width: W, height: H, title: '직선 도선 둘레의 자기장', desc: '동심원과 오른손 법칙', body: b.join('') }),
    };
})());

/* 7. 앙페르 법칙 — 고리가 무엇을 감쌌는가만 따진다 */
add((() => {
    const W = 660, H = 340;
    const b = [txt(16, 26, '닫힌 고리를 한 바퀴 돌며 자기장을 더하면', { cls: 'ink bold' })];
    b.push(txt(16, 48, '그 값은 고리가 감싼 전류만으로 정해진다. 고리의 모양이나 크기는 상관없다.', { cls: 'ink2', size: 'sm' }));
    const panel = (x0, wires, result, note) => {
        const cx = x0 + 96, cy = 200;
        const g = [`<rect x="${x0}" y="86" width="192" height="188" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        // 찌그러진 닫힌 고리
        g.push(`<path d="M${cx - 64} ${cy} C${cx - 62} ${cy - 62} ${cx + 6} ${cy - 66} ${cx + 30} ${cy - 40} C${cx + 70} ${cy - 8} ${cx + 52} ${cy + 56} ${cx - 4} ${cy + 52} C${cx - 44} ${cy + 48} ${cx - 66} ${cy + 32} ${cx - 64} ${cy} Z" fill="var(--s1)" fill-opacity="0.07" stroke="var(--s1)" stroke-width="2" stroke-dasharray="6 4"/>`);
        g.push(curl(cx - 64, cy, 12, 250, 110, { cls: 's1', marker: 'ar1', width: 1.8 }));
        for (const [dx, dy, kind, lab] of wires) {
            g.push(kind === 'in' ? into(cx + dx, cy + dy, { r: 11, cls: 's2', w: 2 }) : outof(cx + dx, cy + dy, { r: 11, cls: 's2', w: 2 }));
            g.push(txt(cx + dx + 17, cy + dy + 5, lab, { cls: 'ink', size: 'sm' }));
        }
        g.push(txt(cx, 108, result, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(cx, 292, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return g.join('');
    };
    b.push(panel(16, [[-14, 0, 'out', 'I']], '합 = μ~0 I', '고리가 전류를 감쌌다'));
    b.push(panel(234, [[104, 6, 'out', 'I']], '합 = 0', '전류가 고리 밖에 있다'));
    b.push(panel(452, [[-30, -8, 'out', 'I'], [8, 22, 'in', 'I']], '합 = 0', '반대 방향 두 전류가 상쇄'));
    b.push(txt(16, H - 12, '전류가 고리 밖에 있으면 자기장이 0이라는 뜻이 아니다. 한 바퀴 돌며 더한 값이 0이라는 뜻이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-ampere-loop',
        title: '앙페르 법칙 — 고리가 감싼 전류',
        desc: '닫힌 고리를 따라 자기장을 한 바퀴 더한 값은 그 고리를 뚫고 지나가는 전류에만 달려 있다. '
            + '고리 밖의 전류는 들어오는 만큼 나가므로 합에 기여하지 않고, 반대 방향의 두 전류를 함께 감싸면 서로 상쇄된다. '
            + '합이 0이라는 것은 자기장이 0이라는 뜻이 아니라 한 바퀴 돌며 더한 값이 0이라는 뜻이다.',
        svg: svg({ width: W, height: H, title: '앙페르 법칙', desc: '고리가 감싼 전류가 합을 정한다', body: b.join('') }),
    };
})());

/* 8. 솔레노이드 내부의 균일한 자기장 */
add((() => {
    const W = 640, H = 360;
    const b = [txt(16, 26, '솔레노이드 — 도선을 촘촘히 감으면 안이 균일해진다', { cls: 'ink bold' })];
    const x0 = 130, x1 = 470, yTop = 148, yBot = 258, N = 8, cy = 203;
    // 양 끝에서 퍼져 나가는 성긴 바깥 장
    for (const [dy, sp] of [[-52, -34], [0, 0], [52, 34]]) {
        b.push(px(x1 + 8, cy + dy * 0.5, x1 + 78, cy + dy + sp, { cls: 's1', marker: 'ar1', width: 1.2 }));
        b.push(px(x0 - 78, cy + dy + sp, x0 - 8, cy + dy * 0.5, { cls: 's1', marker: 'ar1', width: 1.2 }));
    }
    for (let i = 0; i < N; i += 1) {
        const x = x0 + ((x1 - x0) * (i + 0.5)) / N;
        b.push(outof(x, yTop, { r: 9, cls: 's2', w: 1.7 }));
        b.push(into(x, yBot, { r: 9, cls: 's2', w: 1.7 }));
    }
    for (const y of [180, 203, 226]) b.push(px(x0 + 6, y, x1 - 6, y, { cls: 's1', marker: 'ar1', width: 2.2 }));
    b.push(txt(x0, yTop - 18, '위쪽 도선: 전류가 화면 밖으로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(x0, yBot + 26, '아래쪽 도선: 전류가 화면 안으로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 92, '안은 촘촘하고 고르다', { cls: 'ink bold' }));
    b.push(txt(W - 16, 92, '밖은 넓게 퍼져 성기다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 318, 'B = μ~0 n I    (n = 1 m 당 감은 수)', { cls: 'ink bold' }));
    b.push(txt(16, 340, '자기장의 세기가 위치에 따라 변하지 않는 것이 솔레노이드를 쓰는 이유다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W - 16, 318, '길이에 견주어 가늘수록 잘 맞는다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-solenoid',
        title: '솔레노이드 내부의 자기장',
        desc: '길게 감은 코일의 단면을 그린 것이다. 위쪽 도선은 전류가 화면 밖으로, 아래쪽은 안으로 흐른다. '
            + '두 줄이 만드는 자기장이 안에서는 서로 보태져 축 방향으로 고르게 되고 밖에서는 넓게 퍼져 성기다. '
            + '내부 자기장은 μ0 n I 로 위치에 무관하다.',
        svg: svg({ width: W, height: H, title: '솔레노이드 내부의 균일한 자기장', desc: '안은 고르고 밖은 성기다', body: b.join('') }),
    };
})());

/* 9. 나란한 두 도선 사이의 힘 */
add((() => {
    const W = 620, H = 356;
    const b = [txt(16, 26, '나란한 두 도선은 서로 힘을 주고받는다', { cls: 'ink bold' })];
    const panel = (x0, sameDir, verdict) => {
        const a = x0 + 66, c = x0 + 186, yT = 144, yB = 296;
        const g = [`<rect x="${x0}" y="86" width="252" height="236" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        g.push(px(a, yB, a, yT, { cls: 's2', marker: 'ar2', width: 3 }));
        g.push(txt(a - 8, yT + 2, 'I~1', { anchor: 'end', cls: 'ink' }));
        if (sameDir) g.push(px(c, yB, c, yT, { cls: 's2', marker: 'ar2', width: 3 }));
        else g.push(px(c, yT, c, yB, { cls: 's2', marker: 'ar2', width: 3 }));
        g.push(txt(c + 10, yT + 2, 'I~2', { cls: 'ink' }));
        // 1번 도선이 2번 자리에 만드는 자기장
        g.push(into(c, 186, { r: 10, cls: 's1', w: 1.7 }));
        g.push(txt(c - 16, 191, 'I~1 이 만든 B', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        // 2번 도선이 받는 힘
        const fy = 248;
        if (sameDir) {
            g.push(px(c - 4, fy, c - 52, fy, { cls: 's3', marker: 'ar3' }));
            g.push(px(a + 4, fy, a + 52, fy, { cls: 's3', marker: 'ar3' }));
        } else {
            g.push(px(c + 4, fy, c + 52, fy, { cls: 's3', marker: 'ar3' }));
            g.push(px(a - 4, fy, a - 52, fy, { cls: 's3', marker: 'ar3' }));
        }
        g.push(txt((a + c) / 2, fy - 12, 'F', { anchor: 'middle', cls: 'ink' }));
        g.push(`<path class="gr" stroke-dasharray="4 3" d="M${a} 308 H${c}"/>`);
        g.push(txt((a + c) / 2, 320, 'd', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 + 126, 110, verdict, { anchor: 'middle', cls: 'ink bold' }));
        return g.join('');
    };
    b.push(panel(16, true, '같은 방향  →  끌어당긴다'));
    b.push(panel(320, false, '반대 방향  →  밀어낸다'));
    b.push(txt(16, 56, '1번이 만든 자기장 속에 2번이 놓여 있다고 보면 된다. 두 단계를 따로 따진다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, 'F / L = μ~0 I~1 I~2 / (2π d).  같은 부호 전하가 밀어내는 것과 헷갈리기 쉬운 자리다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-two-wires',
        title: '나란한 두 도선 사이의 힘',
        desc: '한 도선이 만든 자기장 안에 다른 도선이 놓여 있다고 보면 된다. 전류가 같은 방향이면 서로 '
            + '끌어당기고 반대 방향이면 밀어낸다. 같은 부호의 전하가 서로 밀어내는 것과 방향이 반대라 헷갈리기 쉽다.',
        svg: svg({ width: W, height: H, title: '나란한 두 도선의 힘', desc: '같은 방향은 인력, 반대 방향은 척력', body: b.join('') }),
    };
})());

/* 10. 자기다발과 각도 — 면을 뚫고 지나가는 자기력선의 수 */
add((() => {
    const W = 660, H = 320;
    const b = [txt(16, 26, '자기다발 — 면을 뚫고 지나가는 자기력선의 수', { cls: 'ink bold' })];
    b.push(txt(16, 48, '고리를 옆에서 본 그림이라 고리가 선으로 보인다. 굵은 선이 고리, 가는 화살표가 B 다.', { cls: 'ink2', size: 'sm' }));
    const panel = (x0, th, tag, note) => {
        const cx = x0 + 96, cy = 190, half = 62;
        const g = [`<rect x="${x0}" y="82" width="192" height="196" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        for (const y of [136, 160, 190, 220, 244]) {
            g.push(px(x0 + 8, y, x0 + 184, y, { cls: 's2', marker: 'ar2', width: 1.3 }));
        }
        const ux = Math.cos((90 + th) * RAD), uy = -Math.sin((90 + th) * RAD);
        g.push(seg(cx - half * ux, cy - half * uy, cx + half * ux, cy + half * uy, { cls: 'ink', width: 4.5 }));
        g.push(px(cx, cy, cx + 50 * Math.cos(th * RAD), cy - 50 * Math.sin(th * RAD), { cls: 's1', marker: 'ar1', width: 2 }));
        g.push(txt(cx + 54 * Math.cos(th * RAD) + 4, cy - 50 * Math.sin(th * RAD) - 2, 'n', { cls: 'ink', size: 'sm' }));
        if (th > 0 && th < 90) g.push(arc(cx, cy, 32, 0, th, 'θ'));
        g.push(txt(cx, 104, tag, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(cx, 296, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return g.join('');
    };
    b.push(panel(16, 0, 'θ = 0°', '가장 많이 뚫는다'));
    b.push(panel(234, 60, 'θ = 60°', '절반만 뚫는다'));
    b.push(panel(452, 90, 'θ = 90°', '스쳐 지나간다 — 0'));
    b.push(txt(W - 16, 48, 'Φ~B = B A cos θ,  단위 Wb', { anchor: 'end', cls: 'ink bold' }));
    return {
        name: 'mag-flux-angle',
        title: '자기다발과 면의 기울기',
        desc: '자기다발은 면을 뚫고 지나가는 자기력선의 수로 읽는다. 면에 세운 수직선 n 과 자기장이 이루는 '
            + '각을 세타라 할 때 다발은 B A cos θ 다. 면이 자기장에 정면으로 놓이면 가장 크고, 자기장과 '
            + '나란해지면 선이 면을 스쳐 지나가므로 0이 된다.',
        svg: svg({ width: W, height: H, title: '자기다발과 각도', desc: 'B A cos θ 를 기울기로 읽는다', body: b.join('') }),
    };
})());

/* 11. 패러데이의 실험 — 변할 때만 생긴다 */
add((() => {
    const W = 660, H = 410;
    const b = [txt(16, 26, '자석과 코일 — 무엇이 전류를 만드는가', { cls: 'ink bold' })];
    const coil = (x, y) => {
        const g = [];
        for (let i = 0; i < 4; i += 1) {
            g.push(`<ellipse cx="${x + i * 15}" cy="${y}" rx="9" ry="30" fill="none" stroke="var(--ink2)" stroke-width="2"/>`);
        }
        return g.join('');
    };
    const meter = (x, y, defl) => {
        const g = [`<circle cx="${x}" cy="${y}" r="24" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`];
        const a = (90 + defl) * RAD;
        g.push(`<path stroke="var(--s3)" stroke-width="2.4" d="M${x} ${y + 12} L${r2(x + 20 * Math.cos(a))} ${r2(y + 12 - 20 * Math.sin(a))}"/>`);
        g.push(`<path class="gr" d="M${x - 16} ${y + 12} H${x + 16}"/>`);
        return g.join('');
    };
    const row = (y, dir, note, defl, mc, tag) => {
        const g = [coil(300, y)];
        g.push(`<path class="ax" d="M296 ${y - 30} H240 M296 ${y + 30} H240 M240 ${y - 30} V${y - 12} M240 ${y + 30} V${y + 12}"/>`);
        g.push(meter(240, y, defl));
        g.push(barMagnet(mc, y, 88, 30));
        if (dir > 0) {
            g.push(px(mc - 52, y, mc - 104, y, { cls: 's1', marker: 'ar1' }));
            g.push(txt(mc - 110, y + 5, 'v', { anchor: 'end', cls: 'ink' }));
        } else if (dir < 0) {
            g.push(px(mc + 52, y, mc + 104, y, { cls: 's1', marker: 'ar1' }));
            g.push(txt(mc + 110, y + 5, 'v', { cls: 'ink' }));
        }
        g.push(txt(16, y + 5, note, { cls: 'ink2', size: 'sm' }));
        g.push(txt(mc, y + 34, tag, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return g.join('');
    };
    b.push(row(122, 1, '가까이 가져가면', 42, 500, '바늘이 한쪽으로'));
    b.push(row(240, 0, '멈춰 두면', 0, 540, '아무 일도 없다'));
    b.push(row(358, -1, '멀리 치우면', -42, 500, '바늘이 반대쪽으로'));
    b.push(txt(16, 52, '자석의 세기가 아니라 다발이 변하는 빠르기가 기전력을 정한다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 70, '멈춘 자석은 아무리 세도 소용없다.', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-faraday-experiment',
        title: '패러데이의 실험',
        desc: '자석을 코일에 가까이 가져가면 검류계 바늘이 한쪽으로 튀고, 멀리 치우면 반대쪽으로 튄다. '
            + '자석을 코일 옆에 가만히 두면 아무리 센 자석이라도 바늘이 움직이지 않는다. '
            + '전류를 만드는 것은 자기장의 크기가 아니라 자기다발이 변하는 빠르기다.',
        svg: svg({ width: W, height: H, title: '패러데이의 실험', desc: '변할 때만 전류가 생긴다', body: b.join('') }),
    };
})());

/* 12. 렌츠 법칙 — 방향을 정하는 법 */
add((() => {
    const W = 640, H = 386;
    const b = [txt(16, 26, '렌츠 법칙 — 유도 전류는 변화를 방해한다', { cls: 'ink bold' })];
    const panel = (x0, dense, verdict, note) => {
        const cx = x0 + 140, cy = 182, R = 58;
        const g = [`<rect x="${x0}" y="86" width="280" height="234" rx="6" fill="none" stroke="var(--grid)" stroke-width="1.2"/>`];
        const n = dense ? 5 : 3;
        g.push(fieldGrid(x0 + 16, 116, x0 + 264, 250, n, n, 'in', { r: 6, cls: 'ink2', w: 1.1 }));
        g.push(`<circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="var(--s1)" stroke-width="3"/>`);
        g.push(dense
            ? curl(cx, cy, R, 20, 150, { cls: 's1', marker: 'ar1', width: 3 })
            : curl(cx, cy, R, 150, 20, { cls: 's1', marker: 'ar1', width: 3 }));
        g.push(dense ? outof(cx, cy, { r: 14, cls: 's3', w: 2.4 }) : into(cx, cy, { r: 14, cls: 's3', w: 2.4 }));
        g.push(px(cx + 18, cy, cx + 68, cy + 84, { cls: 's3', marker: 'ar3', width: 1.4 }));
        g.push(txt(cx + 72, cy + 90, '유도 전류가 고리 안에 만드는 B', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 + 140, 108, verdict, { anchor: 'middle', cls: 'ink bold' }));
        g.push(txt(x0 + 140, 344, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return g.join('');
    };
    b.push(panel(16, true, '⊗ 다발이 늘어난다', '반시계 — 늘어나는 것을 깎는다'));
    b.push(panel(336, false, '⊗ 다발이 줄어든다', '시계 — 줄어드는 것을 보탠다'));
    b.push(txt(16, 58, '고리를 뚫는 자기장은 화면 안쪽(⊗). 왼쪽은 촘촘해지는 중, 오른쪽은 성겨지는 중이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 12, '유도 전류가 만드는 자기장은 변화를 상쇄하는 쪽이지, 원래 자기장의 반대쪽이 아니다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-lenz',
        title: '렌츠 법칙으로 유도 전류의 방향 정하기',
        desc: '고리를 뚫는 자기다발이 늘어나면 유도 전류는 고리 안에 반대 방향의 자기장을 만들어 늘어나는 '
            + '몫을 깎고, 줄어들면 같은 방향의 자기장을 만들어 줄어드는 몫을 보탠다. '
            + '유도 전류가 상쇄하려는 것은 자기장 자체가 아니라 자기장의 변화다.',
        svg: svg({ width: W, height: H, title: '렌츠 법칙', desc: '늘면 깎고 줄면 보탠다', body: b.join('') }),
    };
})());

/* 13. 레일 위를 미끄러지는 막대 — 운동 기전력 */
add((() => {
    const W = 620, H = 340;
    const b = [txt(16, 26, '레일 위를 미끄러지는 막대', { cls: 'ink bold' })];
    const xL = 90, xR = 520, yT = 130, yB = 268, rod = 340;
    b.push(fieldGrid(xL + 10, yT + 8, xR - 10, yB - 8, 8, 4, 'in', { r: 6, cls: 'ink2', w: 1.1 }));
    b.push(`<path class="ax" stroke-width="2.4" d="M${xL} ${yT} H${xR} M${xL} ${yB} H${xR} M${xL} ${yT} V${yB}"/>`);
    b.push(boxr(xL - 14, (yT + yB) / 2 - 22, 28, 44, 'R'));
    b.push(px(rod, yB, rod, yT, { cls: 's2', marker: 'ar2', width: 4 }));
    b.push(txt(rod + 10, yT - 10, '막대', { cls: 'ink2', size: 'sm' }));
    b.push(px(rod + 12, (yT + yB) / 2, rod + 84, (yT + yB) / 2, { cls: 's1', marker: 'ar1' }));
    b.push(txt(rod + 88, (yT + yB) / 2 + 5, 'v', { cls: 'ink' }));
    b.push(px(rod - 12, (yT + yB) / 2, rod - 74, (yT + yB) / 2, { cls: 's3', marker: 'ar3' }));
    b.push(txt(rod - 78, (yT + yB) / 2 + 5, 'F (막는 힘)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 전류 방향 (반시계)
    b.push(px(rod - 40, yT, rod - 120, yT, { cls: 's1', marker: 'ar1', width: 2 }));
    b.push(px(xL + 60, yB, xL + 160, yB, { cls: 's1', marker: 'ar1', width: 2 }));
    b.push(txt(rod - 130, yT - 8, 'I', { anchor: 'end', cls: 'ink' }));
    b.push(`<path class="gr" stroke-dasharray="4 3" d="M${rod + 30} ${yT} V${yB}"/>`);
    b.push(txt(rod + 34, (yT + yB) / 2 - 34, 'L', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 56, '⊗ 는 화면 안으로 향하는 균일한 B. 막대가 오른쪽으로 미끄러지면 회로의 넓이가 늘어난다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 300, '넓이가 늘어난 만큼 다발이 늘고, 그 빠르기가 기전력이다:  ε = B L v', { cls: 'ink bold' }));
    b.push(txt(16, 322, '흐른 전류는 다시 자기력을 받는데, 그 방향이 늘 운동을 막는 쪽이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-rod-rails',
        title: '운동 기전력 — 레일 위의 막대',
        desc: '균일한 자기장 속에서 막대가 레일을 따라 미끄러지면 회로의 넓이가 늘어나 자기다발이 커진다. '
            + '넓이가 커지는 빠르기가 L v 이므로 기전력은 B L v 다. 흐른 전류는 다시 자기력을 받는데 '
            + '그 방향이 언제나 운동을 방해하는 쪽이라, 등속을 유지하려면 계속 밀어 주어야 한다.',
        svg: svg({ width: W, height: H, title: '레일 위의 막대와 운동 기전력', desc: '넓이 변화가 만드는 기전력', body: b.join('') }),
    };
})());

/* 14. 발전기 — 각도가 변해서 다발이 변한다 */
add((() => {
    const W = 680, H = 344;
    const b = [txt(16, 26, '발전기 — 코일을 돌리면 각도가 변한다', { cls: 'ink bold' })];
    // 왼쪽: 자극 사이의 코일
    const cx = 160, cy = 190;
    b.push(`<rect x="40" y="120" width="34" height="140" fill="var(--s2)" fill-opacity="0.20" stroke="var(--ink2)" stroke-width="1.4"/>`);
    b.push(`<rect x="246" y="120" width="34" height="140" fill="var(--s1)" fill-opacity="0.16" stroke="var(--ink2)" stroke-width="1.4"/>`);
    b.push(txt(57, 196, 'N', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(263, 196, 'S', { anchor: 'middle', cls: 'ink bold' }));
    for (const y of [146, 190, 234]) b.push(px(80, y, 240, y, { cls: 's2', marker: 'ar2', width: 1.4 }));
    const th = 34;
    const ux = Math.cos((90 + th) * RAD), uy = -Math.sin((90 + th) * RAD);
    b.push(seg(cx - 62 * ux, cy - 62 * uy, cx + 62 * ux, cy + 62 * uy, { cls: 'ink', width: 4.5 }));
    b.push(curl(cx, cy, 84, 300, 240, { cls: 's3', marker: 'ar3', width: 2 }));
    b.push(txt(cx, 300, '일정한 각속도 ω 로 돌린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(cx, 320, 'Φ~B = B A cos(ω t)', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx, 92, '코일을 옆에서 본 모습', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 오른쪽: 출력 그래프
    const g = frame({ xRange: [0, 2], yRange: [-1.4, 1.4], box: { x: 384, y: 118, w: 230, h: 148 } });
    b.push(g.axes({ xLabel: 't / T', yTicks: [-1, 1], grid: false }));
    b.push(g.curve(t => Math.sin(2 * Math.PI * t), { cls: 's1' }));
    b.push(txt(360, 92, '코일 양 끝의 기전력 ε', { cls: 'ink bold' }));
    b.push(txt(360, 300, 'ε = N B A ω sin(ω t)', { cls: 'ink bold' }));
    b.push(txt(360, 320, '한 바퀴에 부호가 두 번 바뀐다 — 교류가 나온다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'mag-generator',
        title: '발전기의 원리',
        desc: '자석 사이에서 코일을 일정한 각속도로 돌리면 자기장도 넓이도 그대로인데 각도만 변한다. '
            + '자기다발이 코사인 꼴로 변하므로 기전력은 사인 꼴이 되고, 한 바퀴마다 부호가 두 번 바뀐다. '
            + '발전소에서 나오는 전기가 교류인 이유가 이것이다.',
        svg: svg({ width: W, height: H, title: '발전기', desc: '각도 변화가 만드는 사인 꼴 기전력', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 13장 — 맥스웰 방정식과 전자기파
 * ================================================================== */

/* 15. 변위전류 — 같은 고리, 다른 면 */
add((() => {
    const W = 680, H = 380;
    const b = [txt(16, 26, '충전 중인 축전기 — 같은 고리에 두 개의 면', { cls: 'ink bold' })];
    const y = 208, pL = 396, pR = 452;
    b.push(px(70, y, pL, y, { cls: 's2', marker: 'ar2', width: 3 }));
    b.push(px(pR, y, 640, y, { cls: 's2', marker: 'ar2', width: 3 }));
    b.push(txt(112, y - 12, 'I', { cls: 'ink' }));
    b.push(txt(600, y - 12, 'I', { cls: 'ink' }));
    b.push(`<path class="ax" stroke-width="3.5" d="M${pL} ${y - 46} V${y + 46} M${pR} ${y - 46} V${y + 46}"/>`);
    b.push(txt(pL - 8, y - 54, '+', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(pR + 8, y - 54, '−', { cls: 'ink bold' }));
    for (const dy of [-28, 0, 28]) b.push(px(pL + 6, y + dy, pR - 6, y + dy, { cls: 's1', marker: 'ar1', width: 1.5 }));
    b.push(txt((pL + pR) / 2, y + 68, 'E 가 커지는 중', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 면 2: 판 사이로 부풀어 지나가는 면 (먼저 깔고 그 위에 고리를 그린다)
    const lx = 190;
    b.push(`<path d="M${lx} ${y - 74} C${lx + 130} ${y - 148} ${pL + 18} ${y - 104} ${pL + 26} ${y} C${pL + 18} ${y + 104} ${lx + 130} ${y + 148} ${lx} ${y + 74}" fill="var(--s2)" fill-opacity="0.10" stroke="var(--s2)" stroke-width="1.8" stroke-dasharray="5 4"/>`);
    // 면 1: 도선을 자르는 평평한 면
    b.push(`<ellipse cx="${lx}" cy="${y}" rx="16" ry="74" fill="var(--s1)" fill-opacity="0.16" stroke="none"/>`);
    // 앙페르 고리
    b.push(`<ellipse cx="${lx}" cy="${y}" rx="16" ry="74" fill="none" stroke="var(--s3)" stroke-width="2.6" stroke-dasharray="7 4"/>`);
    b.push(txt(lx - 26, y - 82, '같은 앙페르 고리', { anchor: 'end', cls: 'ink bold' }));
    b.push(px(lx - 24, y - 78, lx - 8, y - 66, { cls: 's3', marker: 'ar3', width: 1.6 }));
    b.push(txt(16, 322, '면 1 — 도선을 자른다', { cls: 'ink' }));
    b.push(txt(16, 340, '지나가는 전류 = I', { cls: 'ink2', size: 'sm' }));
    b.push(px(96, 314, lx - 10, y + 62, { cls: 's1', marker: 'ar1', width: 1.6 }));
    b.push(txt(W - 16, 322, '면 2 — 판 사이를 지난다', { anchor: 'end', cls: 'ink' }));
    b.push(txt(W - 16, 340, '지나가는 전류 = 0', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(px(W - 150, 314, pL + 18, y + 58, { cls: 's2', marker: 'ar2', width: 1.6 }));
    b.push(txt(16, 52, '고리는 하나인데 고리에 걸친 면은 무수히 많다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 70, '어느 면을 고르냐에 따라 답이 달라지면 법칙이 아니다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, '맥스웰의 보탬: 판 사이에서 E 가 커지는 몫을 전류처럼 세면 두 면의 답이 같아진다',
        { cls: 'ink bold' }));
    return {
        name: 'em-displacement-current',
        title: '변위전류가 필요한 이유',
        desc: '충전 중인 축전기에서 도선을 감싸는 앙페르 고리 하나에 두 가지 면을 걸칠 수 있다. '
            + '도선을 자르는 면에는 전류가 지나가지만 축전기 판 사이로 부풀린 면에는 지나가는 전류가 없다. '
            + '판 사이에서 전기장이 커지는 몫을 전류처럼 세어야 두 면이 같은 답을 준다.',
        svg: svg({ width: W, height: H, title: '변위전류', desc: '같은 고리에 걸친 두 면의 모순', body: b.join('') }),
    };
})());

/* 16. 전자기파에서 E 와 B 의 진동 */
add((() => {
    const W = 680, H = 380;
    const y0 = 208, x0 = 96, x1 = 596, A = 74;
    const skx = 0.58, sky = 0.42;        // 화면 안쪽 방향(B 축)을 비스듬히 그린다
    const cyc = 2;
    const b = [txt(16, 26, '전자기파 — E 와 B 가 나란히 흔들리며 나아간다', { cls: 'ink bold' })];
    b.push(`<path class="ax" marker-end="url(#ark)" d="M${x0 - 60} ${y0} H${x1 + 30}"/>`);
    b.push(txt(x1 + 36, y0 + 4, 'x', { cls: 'ink2', size: 'sm' }));
    const f = t => Math.sin(2 * Math.PI * cyc * t);
    const ptsE = [], ptsB = [];
    for (let i = 0; i <= 200; i += 1) {
        const t = i / 200, x = x0 + (x1 - x0) * t, s = f(t);
        ptsE.push(`${r2(x)} ${r2(y0 - A * s)}`);
        ptsB.push(`${r2(x + A * 0.7 * s * skx)} ${r2(y0 + A * 0.7 * s * sky)}`);
    }
    b.push(`<path class="cv s2" stroke-width="1.6" d="M${ptsB.join(' L')}"/>`);
    b.push(`<path class="cv s1" stroke-width="2.2" d="M${ptsE.join(' L')}"/>`);
    for (let i = 1; i <= 15; i += 1) {
        const t = i / 16, x = x0 + (x1 - x0) * t, s = f(t);
        if (Math.abs(s) < 0.12) continue;
        b.push(px(x, y0, x, r2(y0 - A * s), { cls: 's1', marker: 'ar1', width: 1.4 }));
        b.push(px(x, y0, r2(x + A * 0.7 * s * skx), r2(y0 + A * 0.7 * s * sky), { cls: 's2', marker: 'ar2', width: 1.2 }));
    }
    b.push(txt(x0 + 8, y0 - A - 16, 'E (y 방향, 화면 위아래)', { cls: 'ink' }));
    b.push(txt(x0 + 8, y0 + A * 0.7 * sky + 52, 'B (z 방향, 화면 앞뒤)', { cls: 'ink' }));
    b.push(px(x1 - 100, 62, x1 + 20, 62, { cls: 's3', marker: 'ar3', width: 2.6 }));
    b.push(txt(x1 - 106, 66, '진행 방향, 속력 c', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(16, 52, '두 진동은 위상이 같다. 같이 커지고 같이 0이 된다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 32, 'E ⊥ B 이고 둘 다 진행 방향에 수직이다 (횡파).  E × B 가 곧 진행 방향이다', { cls: 'ink bold' }));
    b.push(txt(16, H - 12, '크기의 비는 언제나 E / B = c.  매질이 필요 없다는 점이 소리와 결정적으로 다르다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'em-wave-eb',
        title: '전자기파의 전기장과 자기장',
        desc: '전기장은 y 방향으로, 자기장은 z 방향으로 흔들리고 파는 x 방향으로 나아간다. '
            + '두 진동은 위상이 같아 같이 커지고 같이 0이 되며, 크기의 비는 언제나 광속이다. '
            + '둘 다 진행 방향에 수직인 횡파이고, E 에서 B 로 오른손을 감으면 엄지가 진행 방향을 가리킨다.',
        svg: svg({ width: W, height: H, title: '전자기파의 E 와 B', desc: '서로 수직이고 위상이 같은 두 진동', body: b.join('') }),
    };
})());

/* 17. 포인팅 벡터와 세기 */
add((() => {
    const W = 640, H = 372;
    const b = [txt(16, 26, '전자기파가 나르는 에너지', { cls: 'ink bold' })];
    const x0 = 130, x1 = 424, cy = 178, hh = 70, dx = 46, dy = -32;
    const P = (a, c, d) => [x0 + a * (x1 - x0) + d * dx, cy - c * hh + d * dy];
    const poly = (pts, op) => `<path d="M${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s1)" fill-opacity="${op}" stroke="var(--ink2)" stroke-width="1.3"/>`;
    b.push(poly([P(0, -1, 0), P(0, 1, 0), P(0, 1, 1), P(0, -1, 1)], 0.16));
    b.push(poly([P(0, 1, 0), P(1, 1, 0), P(1, 1, 1), P(0, 1, 1)], 0.08));
    b.push(poly([P(1, -1, 0), P(1, 1, 0), P(1, 1, 1), P(1, -1, 1)], 0.16));
    b.push(`<path class="gr" d="M${P(0, -1, 0).map(r2).join(' ')} L${P(1, -1, 0).map(r2).join(' ')} L${P(1, 1, 0).map(r2).join(' ')}"/>`);
    b.push(txt(x0 - 62, cy + 4, '넓이 A', { anchor: 'end', cls: 'ink' }));
    b.push(px(x0 - 52, cy, x0 - 52, cy - hh, { cls: 'ax', marker: 'ark', width: 1.4 }));
    b.push(px(x0 - 52, cy, x0 - 52, cy + hh, { cls: 'ax', marker: 'ark', width: 1.4 }));
    b.push(`<path class="gr" stroke-dasharray="4 3" d="M${x0} ${cy + hh + 20} H${x1}"/>`);
    b.push(txt((x0 + x1) / 2, cy + hh + 38, '길이 = c Δt', { anchor: 'middle', cls: 'ink' }));
    b.push(px(x0 + 24, cy + 20, x0 + 24, cy - 52, { cls: 's1', marker: 'ar1' }));
    b.push(txt(x0 + 30, cy - 56, 'E', { cls: 'ink' }));
    b.push(outof(x0 + 74, cy + 24, { r: 11, cls: 's2', w: 2 }));
    b.push(txt(x0 + 90, cy + 29, 'B (화면 밖)', { cls: 'ink2', size: 'sm' }));
    b.push(px(x1 + 20, cy, x1 + 100, cy, { cls: 's3', marker: 'ar3', width: 3 }));
    b.push(txt(x1 + 106, cy + 5, 'S', { cls: 'ink bold' }));
    b.push(txt(16, 56, 'Δt 동안 이 상자 안의 에너지가 몽땅 오른쪽 면을 지나간다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 312, 'S = (1/μ~0) E × B  —  단위 넓이·단위 시간당 지나가는 에너지, 단위 W/m²', { cls: 'ink bold' }));
    b.push(txt(16, 332, '방향은 E 에서 B 로 오른손을 감을 때의 엄지, 즉 파가 나아가는 쪽이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 352, '시간 평균을 낸 값이 세기 I 이고, 흡수하는 면은 I / c 만큼의 압력을 받는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'em-poynting',
        title: '포인팅 벡터 — 에너지가 흐르는 방향과 양',
        desc: '단면적 A 인 면을 시간 델타 t 동안 지나가는 에너지는 길이 c 델타 t 인 상자 안에 담긴 에너지와 같다. '
            + '이것을 넓이와 시간으로 나눈 것이 포인팅 벡터의 크기이고, 방향은 E 에서 B 로 오른손을 감을 때의 '
            + '엄지 방향, 곧 파의 진행 방향이다.',
        svg: svg({ width: W, height: H, title: '포인팅 벡터', desc: '상자 안의 에너지가 한 면을 지나간다', body: b.join('') }),
    };
})());

/* 18. 전자기 스펙트럼 */
add((() => {
    const W = 700, H = 330;
    const bx0 = 60, bx1 = 660, by = 130, bh = 46;
    const L0 = -12, L1 = 4;                              // log10(λ / m)
    const X = v => r2(bx0 + ((v - L0) / (L1 - L0)) * (bx1 - bx0));
    const bands = [
        [L0, -11, '감마선', 0.30],
        [-11, -8, 'X선', 0.24],
        [-8, Math.log10(3.8e-7), '자외선', 0.18],
        [Math.log10(3.8e-7), Math.log10(7e-7), '가시광선', 0.55],
        [Math.log10(7e-7), -3, '적외선', 0.18],
        [-3, 0, '마이크로파', 0.24],
        [0, L1, '전파', 0.30],
    ];
    const b = [txt(16, 26, '전자기 스펙트럼 — 다른 것은 파장뿐이다', { cls: 'ink bold' })];
    b.push(txt(16, 48, '전부 같은 전자기파이고 진공에서의 속력도 모두 c 로 같다. 이름이 다른 것은 만드는 법과 쓰임이 다르기 때문이다.',
        { cls: 'ink2', size: 'sm' }));
    bands.forEach(([a, c, name, op], i) => {
        b.push(`<rect x="${X(a)}" y="${by}" width="${r2(X(c) - X(a))}" height="${bh}" fill="var(--s1)" fill-opacity="${op}" stroke="var(--ink2)" stroke-width="1"/>`);
        const mid = (X(a) + X(c)) / 2;
        if (name === '가시광선') {
            b.push(px(mid, by - 8, mid, by - 40, { cls: 's2', marker: 'ar2', width: 1.6 }));
            b.push(txt(mid, by - 46, name, { anchor: 'middle', cls: 'ink bold' }));
            b.push(txt(mid, by - 62, '380 nm 에서 700 nm 까지', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        } else {
            b.push(txt(mid, by + (i % 2 ? bh + 34 : bh + 18), name, { anchor: 'middle', cls: 'ink' }));
        }
    });
    // 파장 눈금. SVG 라벨에는 수식을 쓸 수 없으므로 지수는 유니코드 위첨자로 적는다.
    const SUP = { '-': '⁻', 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹' };
    const sup = e => String(e).split('').map(ch => SUP[ch]).join('');
    b.push(`<path class="ax" d="M${bx0} ${by + bh + 52} H${bx1}"/>`);
    for (const e of [-12, -10, -8, -6, -4, -2, 0, 2, 4]) {
        b.push(`<path class="ax" stroke-width="1" d="M${X(e)} ${by + bh + 52} V${by + bh + 60}"/>`);
        b.push(txt(X(e), by + bh + 76, `10${sup(e)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(bx1, by + bh + 96, '파장 λ (m) — 눈금 한 칸이 100배', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(bx0, by + bh + 96, '짧다 · 진동수가 높다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, H - 10, 'c = f λ 이므로 파장이 짧을수록 진동수가 높다. 가시광선이 차지하는 폭은 전체에서 이만큼뿐이다',
        { cls: 'ink2', size: 'sm' }));
    return {
        name: 'em-spectrum',
        title: '전자기 스펙트럼',
        desc: '전파에서 감마선까지 모두 같은 전자기파이고 진공에서의 속력도 같다. 다른 것은 파장과 진동수뿐이다. '
            + '가로 눈금은 한 칸이 100배인 로그 눈금이며, 사람이 볼 수 있는 380에서 700 나노미터 구간은 '
            + '전체에서 아주 좁은 띠에 지나지 않는다.',
        svg: svg({ width: W, height: H, title: '전자기 스펙트럼', desc: '파장에 따른 이름과 가시광선의 폭', body: b.join('') }),
    };
})());

export default figures;
