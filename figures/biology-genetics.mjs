/**
 * 생물학 8~12장(세포 주기와 분열, 멘델 유전학, 중심원리, 발현 조절, 생명공학)의 그림.
 *
 * 이름은 모두 bio-gen- 으로 시작한다. 다른 담당자의 모듈(biology-cell.mjs,
 * biology-organism.mjs)과 이름이 겹치면 build.mjs 가 오류를 낸다.
 *
 * 라벨에 수식을 쓸 수 없다(<img> 로 들어가 MathJax 가 닿지 않는다).
 * 아래첨자는 lib.mjs 의 `C~t` 표기를 쓰고 나머지는 유니코드로 적는다.
 */
import { svg, frame, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.6, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.16, stroke = 'none', sw = 1 } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/**
 * 화살표. lib.mjs 의 px() 에 cls:'ark' 나 'ar1' 을 넘기면 그 CSS 클래스가 SVG 안에
 * 없어 선이 사라지고 화살촉만 남는다. 여기서는 stroke 를 직접 지정해 그 함정을 피한다.
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 2, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
        ar1: 'var(--s1)', ar2: 'var(--s2)', ar3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/** 원 위의 부채꼴. 각은 12시에서 시계 방향으로 잰 도(degree). */
function sector(cx, cy, r, a1, a2, { fill = 'var(--s1)', op = 0.28, stroke = 'var(--ink2)', sw = 1.2 } = {}) {
    const P = a => [cx + r * Math.sin((a * Math.PI) / 180), cy - r * Math.cos((a * Math.PI) / 180)];
    const [x1, y1] = P(a1); const [x2, y2] = P(a2);
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    return `<path d="M${r2(cx)} ${r2(cy)} L${r2(x1)} ${r2(y1)} A${r2(r)} ${r2(r)} 0 ${large} 1 ${r2(x2)} ${r2(y2)} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 제목이 붙은 얇은 테두리 상자. 여러 장면을 한 그림에 늘어놓을 때 쓴다. */
function panel(x, y, w, h, title, { sub, titleCls = 'ink bold' } = {}) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + (title ? txt(x + w / 2, y + 19, title, { anchor: 'middle', cls: titleCls }) : '')
        + (sub ? txt(x + w / 2, y + 35, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 세로로 선 막대 하나(염색분체 한 가닥). */
function vbar(cx, top, h, w, cls, op = 0.9) {
    return `<rect x="${r2(cx - w / 2)}" y="${r2(top)}" width="${r2(w)}" height="${r2(h)}" rx="${r2(w / 2)}" fill="var(--${cls})" fill-opacity="${op}"/>`;
}

/**
 * 염색체 하나. 세로로 서 있고 동원체는 위에서 cent 비율 되는 자리에 찍는다.
 * rep=true 면 자매염색분체 둘이 동원체에서 붙은 모양이 된다.
 */
function chrom(cx, cy, h, { w = 11, cls = 's1', rep = false, cent = 0.34, op = 0.9 } = {}) {
    const top = cy - h / 2;
    const yc = top + h * cent;
    const out = [];
    if (rep) {
        const d = w * 0.8;
        out.push(vbar(cx - d, top, h, w, cls, op), vbar(cx + d, top, h, w, cls, op));
        out.push(`<rect x="${r2(cx - d - w / 2)}" y="${r2(yc - w * 0.26)}" width="${r2(2 * d + w)}" height="${r2(w * 0.52)}" fill="var(--${cls})" fill-opacity="${op}"/>`);
    } else {
        out.push(vbar(cx, top, h, w, cls, op));
    }
    out.push(circ(cx, yc, Math.max(2.2, w * 0.29), { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
    return out.join('');
}

/** 세포 테두리(타원)와 필요하면 점선 핵막. */
function cell(cx, cy, rx, ry, { nucleus } = {}) {
    return ell(cx, cy, rx, ry, { stroke: 'var(--ink2)', sw: 1.6 })
        + (nucleus ? ell(cx, cy, rx * 0.74, ry * 0.74, { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }) : '');
}

/** 방추사. 두 극에서 가운데의 점들로 뻗은 가는 선. */
function spindle(cx, cy, rx, targets) {
    const out = [];
    for (const t of targets) {
        out.push(line([[cx - rx, cy], t], { stroke: 'var(--grid)', sw: 1 }));
        out.push(line([[cx + rx, cy], t], { stroke: 'var(--grid)', sw: 1 }));
    }
    out.push(circ(cx - rx, cy, 3.2, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    out.push(circ(cx + rx, cy, 3.2, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    return out.join('');
}

/* ================================================================== *
 * 8장 — 세포 주기와 분열
 * ================================================================== */

/* 8-1. 염색체 용어 */
add((() => {
    const W = 700, H = 336;
    const b = [];
    const py = 66, ph = 232, pw = 216;
    const X0 = [16, 242, 468];
    b.push(txt(W / 2, 28, '세는 규칙 — 동원체 하나가 염색체 하나다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 48, '복제해도 염색체 수는 늘지 않는다. 늘어나는 것은 염색분체 수와 DNA 양이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(panel(X0[0], py, pw, ph, '복제 전 (G1)'));
    b.push(panel(X0[1], py, pw, ph, '복제 뒤 (S기를 지남)'));
    b.push(panel(X0[2], py, pw, ph, '상동염색체 한 쌍'));
    const cy1 = py + 112;

    // 패널 1
    let cx = X0[0] + 60;
    b.push(chrom(cx, cy1, 108, { w: 13, cls: 's1' }));
    b.push(line([[cx + 10, cy1 - 54 + 36.7], [cx + 42, cy1 + 4]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(txt(cx + 46, cy1 + 8, '동원체', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[0] + 14, py + ph - 44, '염색분체 1 · 동원체 1', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[0] + 14, py + ph - 24, '염색체 1개 · DNA 1몫', { cls: 'ink', size: 'sm' }));

    // 패널 2
    cx = X0[1] + 64;
    b.push(chrom(cx, cy1, 108, { w: 13, cls: 's1', rep: true }));
    b.push(line([[cx - 20, cy1 - 44], [cx + 38, cy1 - 36]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(txt(cx + 42, cy1 - 32, '자매염색분체', { cls: 'ink2', size: 'sm' }));
    b.push(txt(cx + 42, cy1 - 16, '(똑같은 복사본)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[1] + 14, py + ph - 44, '염색분체 2 · 동원체 1', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[1] + 14, py + ph - 24, '염색체 1개 · DNA 2몫', { cls: 'ink', size: 'sm' }));

    // 패널 3
    const c3 = X0[2] + 62;
    b.push(txt(c3, py + 48, '어머니 유래', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(c3 + 78, py + 48, '아버지 유래', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(chrom(c3, cy1 + 2, 84, { w: 13, cls: 's1', rep: true }));
    b.push(chrom(c3 + 78, cy1 + 2, 84, { w: 13, cls: 's2', rep: true }));
    b.push(txt(X0[2] + 14, py + ph - 58, '염색분체 4 · 동원체 2', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[2] + 14, py + ph - 38, '염색체 2개', { cls: 'ink', size: 'sm' }));
    b.push(txt(X0[2] + 14, py + ph - 18, '같은 유전자가 같은 순서로', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-chromosome-terms',
        svg: svg({ width: W, height: H, title: '염색체 · 염색분체 · 동원체 · 상동염색체', desc: '복제 전후와 상동염색체 쌍에서 각각 무엇을 몇 개로 세는지', body: b.join('') }),
    };
})());

/* 8-2. 세포 주기 시계 */
add((() => {
    const W = 680, H = 362;
    const cx = 170, cy = 162, R = 104;
    const b = [];
    const spans = [
        { a1: 0, a2: 165, name: 'G1기', cls: 's1', op: 0.20, note: '11 h' },
        { a1: 165, a2: 285, name: 'S기', cls: 's2', op: 0.26, note: '8 h' },
        { a1: 285, a2: 345, name: 'G2기', cls: 's1', op: 0.34, note: '4 h' },
        { a1: 345, a2: 360, name: 'M기', cls: 's3', op: 0.45, note: '1 h' },
    ];
    for (const s of spans) b.push(sector(cx, cy, R, s.a1, s.a2, { fill: `var(--${s.cls})`, op: s.op }));
    b.push(circ(cx, cy, R, { stroke: 'var(--ink2)', sw: 1.6 }));
    const tx0 = 300;
    const P = (a, r) => [cx + r * Math.sin((a * Math.PI) / 180), cy - r * Math.cos((a * Math.PI) / 180)];
    for (const s of spans) {
        if (s.name === 'M기') continue;   // 부채꼴이 좁아 안에 들어가지 않는다
        const [lx, ly] = P((s.a1 + s.a2) / 2, R * 0.66);
        b.push(txt(lx, ly - 2, s.name, { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(lx, ly + 15, s.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    // M기는 좁으므로 라벨을 원 밖으로 뺀다
    const [mx, my] = P(352.5, R + 30);
    b.push(line([[cx - 3, cy - R + 4], [mx - 4, my + 6]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(txt(mx - 2, my, 'M기 (분열기) 1 h', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(tx0, 232, '간기 = G1 + S + G2. 현미경으로 보면', { cls: 'ink', size: 'sm' }));
    b.push(txt(tx0, 250, '아무 일도 없어 보이는 구간이다.', { cls: 'ink', size: 'sm' }));
    const [ex, ey] = P(148, R + 4);
    b.push(px(ex, ey, 196, 292, { cls: 's2', marker: 'ar2', width: 2 }));
    b.push(box(60, 296, 176, 50, { stroke: 'var(--ink2)', sw: 1.2, rx: 5 }));
    b.push(txt(148, 316, 'G0 — 주기에서 빠져나옴', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(148, 334, '분열하지 않고 제 일만 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const tx = tx0;
    b.push(txt(tx, 44, '한 바퀴가 세포 하나의 일생이다', { cls: 'ink bold' }));
    const rows = [
        ['G1기', '자라고 소기관을 늘린다. 여기서'],
        ['', '주기를 계속 돌지 결정한다'],
        ['S기', 'DNA 를 통째로 한 번 복제한다.'],
        ['', '염색체 수는 그대로다'],
        ['G2기', '복제가 제대로 됐는지 점검하고'],
        ['', '분열 장치를 준비한다'],
        ['M기', '핵과 세포질을 둘로 나눈다.'],
        ['', '가장 짧지만 눈에 보이는 시기다'],
    ];
    rows.forEach((r, i) => {
        b.push(txt(tx, 72 + i * 20, r[0], { cls: 'ink bold', size: 'sm' }));
        b.push(txt(tx + 46, 72 + i * 20, r[1], { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(tx, 278, '적어 둔 시간은 24시간 만에 한 바퀴를', { cls: 'ink2', size: 'sm' }));
    b.push(txt(tx, 296, '도는 배양 세포의 예다. 세포 종류마다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(tx, 314, '크게 다르고 특히 G1 이 많이 변한다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(tx, 338, '신경세포처럼 G0 에서 평생 머무는 세포도 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-cellcycle-clock',
        svg: svg({ width: W, height: H, title: '세포 주기의 네 시기', desc: '24시간 주기를 예로 든 G1, S, G2, M 의 상대적 길이와 G0', body: b.join('') }),
    };
})());

/* 8-3. 주기에 따른 DNA 양 */
add((() => {
    const W = 660, H = 340;
    const b = [];
    const mk = (top, pts, stages, title, cls) => {
        const g = frame({ xRange: [0, 10], yRange: [0, 4.7], box: { x: 66, y: top, w: 372, h: 96 } });
        const out = [g.axes({ yLabel: 'DNA 양 (C)', xTicks: [], yTicks: [1, 2, 4] })];
        out.push(g.line(pts, { cls }));
        out.push(txt(122, top - 14, title, { cls: 'ink bold' }));
        for (const s of stages) {
            out.push(txt(g.X((s[1] + s[2]) / 2), top + 96 + 18, s[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            if (s[2] < 10) out.push(line([[g.X(s[2]), top], [g.X(s[2]), top + 96]], { stroke: 'var(--grid)', sw: 1, dash: '3 3' }));
        }
        return out.join('');
    };
    b.push(mk(48,
        [[0, 2], [2, 2], [4, 4], [7.6, 4], [7.6, 2], [10, 2]],
        [['G1', 0, 2], ['S', 2, 4], ['G2', 4, 6.4], ['M', 6.4, 7.6], ['딸세포', 7.6, 10]],
        '체세포분열 — 2C 로 시작해 2C 로 끝난다', 's1'));
    b.push(mk(214,
        [[0, 2], [2, 2], [4, 4], [6.6, 4], [6.6, 2], [8.2, 2], [8.2, 1], [10, 1]],
        [['G1', 0, 2], ['S', 2, 4], ['G2', 4, 5.6], ['감수 I', 5.6, 6.6], ['감수 II', 6.6, 8.2], ['배우자', 8.2, 10]],
        '감수분열 — 복제는 한 번, 분열은 두 번이라 1C 로 끝난다', 's2'));
    b.push(txt(460, 74, 'S기에 DNA 양은 두 배가 되지만', { cls: 'ink', size: 'sm' }));
    b.push(txt(460, 92, '염색체 수는 그대로다. 자매염색분체가', { cls: 'ink', size: 'sm' }));
    b.push(txt(460, 110, '한 동원체에 붙어 있기 때문이다.', { cls: 'ink', size: 'sm' }));
    b.push(txt(460, 240, '두 그래프의 앞부분은 완전히 같다.', { cls: 'ink', size: 'sm' }));
    b.push(txt(460, 258, '갈라지는 곳은 두 번째 분열이 있느냐', { cls: 'ink', size: 'sm' }));
    b.push(txt(460, 276, '뿐이고, 그 한 번이 2C 를 1C 로 만든다.', { cls: 'ink', size: 'sm' }));
    return {
        name: 'bio-gen-dna-amount',
        svg: svg({ width: W, height: H, title: '세포 주기에 따른 DNA 양', desc: '체세포분열과 감수분열에서 C 값이 어떻게 오르내리는지 비교', body: b.join('') }),
    };
})());

/* 8-4. 체세포분열의 단계 */
add((() => {
    const W = 700, H = 284;
    const b = [];
    const cy = 132, rx = 62, ry = 66;
    const cxs = [76, 214, 352, 490, 628];
    const names = ['전기', '전중기', '중기', '후기', '말기·세포질분열'];
    const notes = ['염색체가 응축한다', '핵막이 무너지고', '적도면에 한 줄로', '자매염색분체가', '핵막이 다시 생기고'];
    const notes2 = ['핵막은 아직 있다', '방추사가 동원체를 잡는다', '늘어선다', '갈라져 양극으로', '세포질이 나뉜다'];
    // 염색체 4개(2n=4): 긴 것 두 개(파랑/주황), 짧은 것 두 개
    const sizes = [40, 40, 26, 26];
    const cls = ['s1', 's2', 's1', 's2'];
    for (let i = 0; i < 5; i += 1) {
        const cx = cxs[i];
        b.push(txt(cx, 30, names[i], { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(cx, 224, notes[i], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(cx, 240, notes2[i], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (i === 4) {
            b.push(ell(cx - 30, cy, 34, ry * 0.92, { stroke: 'var(--ink2)', sw: 1.6 }));
            b.push(ell(cx + 30, cy, 34, ry * 0.92, { stroke: 'var(--ink2)', sw: 1.6 }));
        } else {
            b.push(cell(cx, cy, rx, ry, { nucleus: i === 0 }));
        }
        if (i === 0) {
            const spots = [[-22, -18], [16, -22], [-16, 22], [20, 18]];
            spots.forEach((s, k) => b.push(chrom(cx + s[0], cy + s[1], sizes[k], { w: 6, cls: cls[k], rep: true })));
        } else if (i === 1) {
            const spots = [[-22, -14], [16, -20], [-14, 22], [20, 14]];
            b.push(spindle(cx, cy, rx - 4, spots.map(s => [cx + s[0], cy + s[1]])));
            spots.forEach((s, k) => b.push(chrom(cx + s[0], cy + s[1], sizes[k], { w: 6, cls: cls[k], rep: true })));
        } else if (i === 2) {
            const ys = [-40, -12, 14, 38];
            b.push(spindle(cx, cy, rx - 4, ys.map(y => [cx, cy + y])));
            b.push(line([[cx, cy - 58], [cx, cy + 58]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
            ys.forEach((y, k) => b.push(chrom(cx, cy + y, sizes[k] * 0.62, { w: 6, cls: cls[k], rep: true })));
        } else {
            const ys = [-40, -12, 14, 38];
            const dx = i === 3 ? 30 : 30;
            b.push(spindle(cx, cy, rx - 4, []));
            ys.forEach((y, k) => {
                b.push(chrom(cx - dx, cy + y, sizes[k] * 0.62, { w: 6, cls: cls[k] }));
                b.push(chrom(cx + dx, cy + y, sizes[k] * 0.62, { w: 6, cls: cls[k] }));
            });
            if (i === 3) {
                b.push(px(cx - 12, cy - 58, cx - 40, cy - 58, { cls: 's3', marker: 'ar3', width: 2 }));
                b.push(px(cx + 12, cy - 58, cx + 40, cy - 58, { cls: 's3', marker: 'ar3', width: 2 }));
            }
        }
    }
    b.push(txt(W / 2, 272, '2n = 4 인 세포로 그렸다. 후기에 갈라지는 것은 자매염색분체이고, 그 순간 세포 안의 염색체 수는 잠깐 8이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-mitosis-stages',
        svg: svg({ width: W, height: H, title: '체세포분열의 다섯 단계', desc: '2n = 4 인 세포에서 전기부터 세포질분열까지', body: b.join('') }),
    };
})());

/* 8-5. 감수분열의 흐름 */
add((() => {
    const W = 700, H = 412;
    const b = [];
    const rx = 58, ry = 60;
    const drawPair = (cx, cy, dy, repl, gap) => {
        b.push(chrom(cx - gap, cy + dy, 40, { w: 6, cls: 's1', rep: repl }));
        b.push(chrom(cx + gap, cy + dy, 40, { w: 6, cls: 's2', rep: repl }));
    };
    // 1행: 감수분열 I
    const r1 = 126;
    const c1 = [82, 246, 410];
    b.push(txt(24, 28, '감수분열 I — 상동염색체가 갈라진다 (환원분열: 2n → n)', { cls: 'ink bold' }));
    b.push(txt(c1[0], 54, '전기 I', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(c1[1], 54, '중기 I', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(c1[2], 54, '후기 I', { anchor: 'middle', cls: 'ink bold' }));
    // 전기 I: 사분체와 키아즈마
    b.push(cell(c1[0], r1, rx, ry));
    b.push(chrom(c1[0] - 9, r1 - 6, 62, { w: 7, cls: 's1', rep: true }));
    b.push(chrom(c1[0] + 21, r1 - 6, 62, { w: 7, cls: 's2', rep: true }));
    b.push(line([[c1[0] - 3, r1 + 8], [c1[0] + 15, r1 + 8]], { stroke: 'var(--ink)', sw: 2 }));
    b.push(txt(c1[0], r1 + 76, '상동염색체가 짝을 짓고', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(c1[0], r1 + 92, '교차가 일어난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 중기 I
    b.push(cell(c1[1], r1, rx, ry));
    b.push(spindle(c1[1], r1, rx - 4, [[c1[1] - 13, r1 - 26], [c1[1] + 13, r1 - 26], [c1[1] - 13, r1 + 26], [c1[1] + 13, r1 + 26]]));
    b.push(line([[c1[1], r1 - 54], [c1[1], r1 + 54]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    drawPair(c1[1], r1, -26, true, 13);
    drawPair(c1[1], r1, 26, true, 13);
    b.push(txt(c1[1], r1 + 78, '적도면에 놓이는 것은', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(c1[1], r1 + 94, '상동염색체 쌍이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 후기 I
    b.push(cell(c1[2], r1, rx, ry));
    b.push(chrom(c1[2] - 30, r1 - 24, 34, { w: 6, cls: 's1', rep: true }));
    b.push(chrom(c1[2] - 30, r1 + 24, 34, { w: 6, cls: 's1', rep: true }));
    b.push(chrom(c1[2] + 30, r1 - 24, 34, { w: 6, cls: 's2', rep: true }));
    b.push(chrom(c1[2] + 30, r1 + 24, 34, { w: 6, cls: 's2', rep: true }));
    b.push(txt(c1[2], r1 + 78, '자매염색분체는 아직', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(c1[2], r1 + 94, '붙어 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(c1[2] + rx + 12, r1, c1[2] + rx + 46, r1, { width: 2 }));
    b.push(txt(c1[2] + rx + 56, r1 - 6, '딸세포 2개', { cls: 'ink', size: 'sm' }));
    b.push(txt(c1[2] + rx + 56, r1 + 12, '각각 n = 2, 2C', { cls: 'ink2', size: 'sm' }));

    // 2행: 감수분열 II
    const r2y = 330;
    b.push(txt(24, 254, '감수분열 II — 자매염색분체가 갈라진다 (등수분열: n → n)', { cls: 'ink bold' }));
    b.push(txt(c1[0], 280, '중기 II', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(c1[1], 280, '후기 II', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(c1[2] + 46, 280, '배우자 4개', { anchor: 'middle', cls: 'ink bold' }));
    b.push(cell(c1[0], r2y, rx * 0.8, ry * 0.72));
    b.push(line([[c1[0], r2y - 40], [c1[0], r2y + 40]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    b.push(chrom(c1[0], r2y - 18, 30, { w: 6, cls: 's1', rep: true }));
    b.push(chrom(c1[0], r2y + 18, 30, { w: 6, cls: 's1', rep: true }));
    b.push(txt(c1[0], r2y + 66, '개별 염색체가 한 줄로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(cell(c1[1], r2y, rx * 0.8, ry * 0.72));
    for (const s of [-1, 1]) {
        b.push(chrom(c1[1] + s * 26, r2y - 18, 30, { w: 6, cls: 's1' }));
        b.push(chrom(c1[1] + s * 26, r2y + 18, 30, { w: 6, cls: 's1' }));
    }
    b.push(txt(c1[1], r2y + 66, '체세포분열의 후기와 같은 일', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gx = [c1[2] - 26, c1[2] + 22, c1[2] + 70, c1[2] + 118];
    const gc = ['s1', 's1', 's2', 's2'];
    gx.forEach((x, i) => {
        b.push(ell(x, r2y, 20, 26, { stroke: 'var(--ink2)', sw: 1.4 }));
        b.push(chrom(x, r2y - 8, 22, { w: 5, cls: gc[i] }));
        b.push(chrom(x, r2y + 16, 14, { w: 5, cls: i % 2 === 0 ? 's2' : 's1' }));
    });
    b.push(txt(c1[2] + 46, r2y + 66, '각각 n = 2, 1C — 조합이 서로 다르다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-meiosis-stages',
        svg: svg({ width: W, height: H, title: '감수분열 I 과 II', desc: '2n = 4 인 세포에서 두 번의 분열이 무엇을 갈라놓는지', body: b.join('') }),
    };
})());

/* 8-6. 독립적 배열 */
add((() => {
    const W = 640, H = 320;
    const b = [];
    b.push(txt(W / 2, 28, '중기 I 에서 상동염색체 쌍이 어느 쪽을 보고 서느냐가 배우자의 조합을 정한다', { anchor: 'middle', cls: 'ink bold' }));
    const cxs = [166, 470];
    const titles = ['배열 ①', '배열 ②'];
    const cy = 128;
    for (let i = 0; i < 2; i += 1) {
        const cx = cxs[i];
        b.push(txt(cx, 58, titles[i], { anchor: 'middle', cls: 'ink bold' }));
        b.push(cell(cx, cy, 78, 54));
        b.push(line([[cx, cy - 52], [cx, cy + 52]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
        // 위쪽 쌍: 긴 염색체, 아래쪽 쌍: 짧은 염색체
        b.push(chrom(cx - 13, cy - 26, 34, { w: 6, cls: 's1', rep: true }));
        b.push(chrom(cx + 13, cy - 26, 34, { w: 6, cls: 's2', rep: true }));
        b.push(chrom(cx - 13, cy + 26, 22, { w: 6, cls: i === 0 ? 's1' : 's2', rep: true }));
        b.push(chrom(cx + 13, cy + 26, 22, { w: 6, cls: i === 0 ? 's2' : 's1', rep: true }));
        // 배우자
        const gy = 238;
        const gxs = [cx - 62, cx + 62];
        for (let k = 0; k < 2; k += 1) {
            b.push(ell(gxs[k], gy, 30, 34, { stroke: 'var(--ink2)', sw: 1.4 }));
            const longCls = k === 0 ? 's1' : 's2';
            const shortCls = i === 0 ? longCls : (k === 0 ? 's2' : 's1');
            b.push(chrom(gxs[k] - 10, gy, 30, { w: 6, cls: longCls }));
            b.push(chrom(gxs[k] + 10, gy + 4, 20, { w: 6, cls: shortCls }));
        }
        b.push(arw(cx - 20, cy + 62, cx - 56, gy - 40, { width: 1.8 }));
        b.push(arw(cx + 20, cy + 62, cx + 56, gy - 40, { width: 1.8 }));
    }
    b.push(line([[318, 50], [318, 282]], { stroke: 'var(--grid)', sw: 1, dash: '5 4' }));
    b.push(txt(W / 2, 302, '쌍이 2개면 배열이 2² = 4 가지, 사람처럼 23쌍이면 2²³ ≈ 8.4 × 10⁶ 가지다 (교차는 아직 세지 않았다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-independent-assortment',
        svg: svg({ width: W, height: H, title: '상동염색체의 독립적 배열', desc: '쌍마다 어느 극을 보는지가 독립이라 배우자 종류가 2의 거듭제곱으로 늘어난다', body: b.join('') }),
    };
})());

/* 8-7. 교차와 재조합 */
add((() => {
    const W = 640, H = 300;
    const b = [];
    b.push(txt(W / 2, 28, '교차 — 상동염색체의 비자매 염색분체끼리 같은 자리를 맞바꾼다', { anchor: 'middle', cls: 'ink bold' }));
    // 왼쪽: 교차 전 사분체
    const lx = 130, ty = 66, hh = 150;
    const bar = (x, segs) => segs.map(s => `<rect x="${r2(x - 5)}" y="${r2(ty + s[0] * hh)}" width="10" height="${r2((s[1] - s[0]) * hh)}" fill="var(--${s[2]})" fill-opacity="0.9"/>`).join('');
    b.push(txt(lx, 54, '교차 전', { anchor: 'middle', cls: 'ink bold' }));
    const xs0 = [lx - 34, lx - 16, lx + 16, lx + 34];
    const c0 = ['s1', 's1', 's2', 's2'];
    xs0.forEach((x, i) => b.push(bar(x, [[0, 1, c0[i]]])));
    b.push(line([[lx - 16, ty + 0.62 * hh], [lx + 16, ty + 0.62 * hh]], { stroke: 'var(--ink)', sw: 2.4 }));
    b.push(txt(lx, ty + hh + 22, '가운데 두 가닥이 겹친 곳', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(lx, ty + hh + 38, '= 키아즈마', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(lx - 62, ty + 8, 'A', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(lx - 62, ty + 0.8 * hh, 'B', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(lx + 62, ty + 8, 'a', { cls: 'ink', size: 'sm' }));
    b.push(txt(lx + 62, ty + 0.8 * hh, 'b', { cls: 'ink', size: 'sm' }));
    b.push(arw(232, 150, 286, 150, { width: 2 }));
    // 오른쪽: 결과 4가닥
    const rx0 = 400;
    b.push(txt(rx0 + 54, 54, '만들어지는 염색분체 4가닥', { anchor: 'middle', cls: 'ink bold' }));
    const res = [
        { x: rx0 - 36, segs: [[0, 1, 's1']], lab: 'A B', kind: '부모형' },
        { x: rx0 + 20, segs: [[0, 0.62, 's1'], [0.62, 1, 's2']], lab: 'A b', kind: '재조합형' },
        { x: rx0 + 76, segs: [[0, 0.62, 's2'], [0.62, 1, 's1']], lab: 'a B', kind: '재조합형' },
        { x: rx0 + 132, segs: [[0, 1, 's2']], lab: 'a b', kind: '부모형' },
    ];
    for (const r of res) {
        b.push(bar(r.x, r.segs));
        b.push(txt(r.x, ty + hh + 22, r.lab, { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(r.x, ty + hh + 40, r.kind, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(line([[rx0 - 52, ty + 0.62 * hh], [rx0 + 148, ty + 0.62 * hh]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    b.push(txt(rx0 + 156, ty + 0.62 * hh + 4, '교차 지점', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '한 번의 교차는 네 가닥 중 두 가닥만 바꾼다. 그래서 재조합률의 상한이 50%다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-crossover',
        svg: svg({ width: W, height: H, title: '교차와 재조합형 염색분체', desc: '사분체에서 교차가 한 번 일어나면 부모형 둘과 재조합형 둘이 나온다', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 9장 — 멘델 유전학
 * ================================================================== */

/** 퍼넷 사각형. top/left 는 배우자 라벨, fill(i,j) 는 칸 내용을 돌려준다. */
function punnett(x, y, cw, ch, top, left, fill) {
    const out = [];
    for (let j = 0; j < top.length; j += 1) {
        out.push(txt(x + cw * (j + 0.5), y - 9, top[j], { anchor: 'middle', cls: 'ink bold' }));
    }
    for (let i = 0; i < left.length; i += 1) {
        out.push(txt(x - 11, y + ch * (i + 0.5) + 5, left[i], { anchor: 'end', cls: 'ink bold' }));
    }
    for (let i = 0; i < left.length; i += 1) {
        for (let j = 0; j < top.length; j += 1) {
            const c = fill(i, j);
            out.push(box(x + cw * j, y + ch * i, cw, ch, {
                fill: c.fill ?? 'none', op: c.op ?? 1, stroke: 'var(--ink2)', sw: 1.2, rx: 3,
            }));
            const yc = y + ch * (i + 0.5) + (c.sub ? -1 : 5);
            out.push(txt(x + cw * (j + 0.5), yc, c.text, { anchor: 'middle', cls: 'ink bold' }));
            if (c.sub) out.push(txt(x + cw * (j + 0.5), yc + 17, c.sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
    }
    return out.join('');
}

/* 9-1. 단성잡종 교배와 퍼넷 사각형 */
add((() => {
    const W = 700, H = 372;
    const b = [];
    b.push(txt(W / 2, 26, '한 형질만 따라가기 — 사라졌던 형질이 F₂ 에서 4분의 1로 돌아온다', { anchor: 'middle', cls: 'ink bold' }));
    // 왼쪽: 교배 흐름
    const cx = 170;
    b.push(txt(46, 76, 'P', { cls: 'ink bold' }));
    b.push(circ(cx - 58, 70, 21, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)' }));
    b.push(circ(cx + 58, 70, 21, { fill: 'var(--s2)', op: 0.30, stroke: 'var(--ink2)' }));
    b.push(txt(cx - 58, 75, 'RR', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx + 58, 75, 'rr', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx, 76, '×', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx - 58, 108, '둥근 (순계)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(cx + 58, 108, '주름 (순계)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(cx, 120, cx, 148, { width: 1.8 }));
    b.push(txt(46, 180, 'F~1', { cls: 'ink bold' }));
    b.push(circ(cx, 176, 21, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)' }));
    b.push(txt(cx, 181, 'Rr', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(cx, 214, '전부 둥근 — 주름이 보이지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(cx, 226, cx, 254, { width: 1.8 }));
    b.push(txt(cx + 10, 246, '자가수분', { cls: 'ink2', size: 'sm' }));
    b.push(txt(46, 292, 'F~2', { cls: 'ink bold' }));
    const bx = 82, bw = 176;
    b.push(box(bx, 274, bw * 0.75, 26, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)', sw: 1.2, rx: 3 }));
    b.push(box(bx + bw * 0.75, 274, bw * 0.25, 26, { fill: 'var(--s2)', op: 0.30, stroke: 'var(--ink2)', sw: 1.2, rx: 3 }));
    b.push(txt(bx + bw * 0.375, 292, '둥근 3', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(bx + bw * 0.875, 292, '1', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(bx, 322, '표현형 3 : 1 — 주름이 돌아온 것이 열성이 사라지지 않았다는 증거다', { cls: 'ink2', size: 'sm' }));
    b.push(line([[336, 54], [336, 336]], { stroke: 'var(--grid)', sw: 1, dash: '5 4' }));
    // 오른쪽: 퍼넷 사각형
    b.push(txt(510, 76, 'F~1 끼리의 교배 — 퍼넷 사각형', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(510, 96, '가로·세로에 배우자를 적고 칸마다 합친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gx = 446, gy = 130, cwc = 64;
    b.push(punnett(gx, gy, cwc, cwc, ['R', 'r'], ['R', 'r'], (i, j) => {
        const g = [['RR', 'Rr'], ['Rr', 'rr']][i][j];
        const rec = g === 'rr';
        return { text: g, sub: rec ? '주름' : '둥근', fill: rec ? 'var(--s2)' : 'var(--s1)', op: rec ? 0.30 : 0.85 };
    }));
    b.push(txt(510, 288, 'RR : Rr : rr = 1 : 2 : 1  (유전자형)', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(510, 310, '둥근 : 주름 = 3 : 1  (표현형)', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(510, 336, '유전자형 비와 표현형 비는 다른 수다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-monohybrid',
        svg: svg({ width: W, height: H, title: '단성잡종 교배와 퍼넷 사각형', desc: 'F1 에서 사라진 열성 형질이 F2 에서 4분의 1로 다시 나타난다', body: b.join('') }),
    };
})());

/* 9-2. 양성잡종 — 9:3:3:1 */
add((() => {
    const W = 690, H = 464;
    const b = [];
    b.push(txt(W / 2, 26, '두 형질을 함께 볼 때 — 배우자가 4종류라서 칸이 16개가 된다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, 'RrYy × RrYy. 두 유전자좌가 서로 다른 염색체에 있다고 본다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gam = ['RY', 'Ry', 'rY', 'ry'];
    const gx = 150, gy = 92, cwc = 72;
    // 대문자를 앞에 오게 짝짓는다. Rr, RR, rr 처럼 관례대로 적기 위해서다.
    const merge = (u, v) => (u === u.toUpperCase() ? u + v : v + u);
    const styles = [
        { fill: 'var(--s1)', op: 0.30, name: '둥근·노랑', cnt: 9 },
        { fill: 'var(--s2)', op: 0.28, name: '둥근·초록', cnt: 3 },
        { fill: 'var(--s3)', op: 0.30, name: '주름·노랑', cnt: 3 },
        { fill: 'var(--ink2)', op: 0.14, name: '주름·초록', cnt: 1 },
    ];
    b.push(punnett(gx, gy, cwc, cwc, gam, gam, (i, j) => {
        const a = gam[j], c = gam[i];
        const r = merge(a[0], c[0]);
        const y = merge(a[1], c[1]);
        const hasR = r.includes('R'), hasY = y.includes('Y');
        const k = hasR && hasY ? 0 : hasR ? 1 : hasY ? 2 : 3;
        return { text: r + y, fill: styles[k].fill, op: styles[k].op };
    }));
    // 오른쪽 요약
    let ly = 132;
    b.push(txt(478, 108, '표현형 네 갈래', { cls: 'ink bold' }));
    for (const s of styles) {
        b.push(box(478, ly - 11, 16, 16, { fill: s.fill, op: s.op, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
        b.push(txt(502, ly + 2, `${s.name}  ${s.cnt}칸`, { cls: 'ink' }));
        ly += 26;
    }
    b.push(txt(478, ly + 14, '9 : 3 : 3 : 1', { cls: 'ink bold' }));
    b.push(txt(478, ly + 38, '= (3 : 1) × (3 : 1)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(478, ly + 58, '한 형질씩 따로 구한 뒤', { cls: 'ink2', size: 'sm' }));
    b.push(txt(478, ly + 76, '곱해도 같은 값이 나온다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 14, '칸을 세는 것보다 형질마다 3 : 1 을 구해 곱하는 편이 빠르다. 그 곱셈이 성립하는 근거가 독립의 법칙이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-dihybrid',
        svg: svg({ width: W, height: H, title: '양성잡종 교배의 9:3:3:1', desc: '두 형질을 함께 보면 16칸이 되고 표현형은 9:3:3:1 로 나뉜다', body: b.join('') }),
    };
})());

/* 9-3. 카이제곱 분포와 임계값 */
add((() => {
    const W = 670, H = 372;
    const b = [];
    const F = frame({ xRange: [0, 14], yRange: [0, 0.55], box: { x: 66, y: 62, w: 540, h: 236 } });
    b.push(txt(W / 2, 26, '카이제곱 통계량이 우연만으로 얼마나 커질 수 있는가', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, '가로축은 통계량, 곡선 아래 넓이는 확률이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(F.axes({
        xLabel: 'χ²', yLabel: '', xTicks: [0, 2, 4, 6, 8, 10, 12, 14], yTicks: [0, 0.1, 0.2, 0.3, 0.4, 0.5], grid: true,
    }));
    const f1 = x => Math.exp(-x / 2) / Math.sqrt(2 * Math.PI * x);
    const f2 = x => 0.5 * Math.exp(-x / 2);
    const f3 = x => Math.sqrt(x / (2 * Math.PI)) * Math.exp(-x / 2);
    // 기각역(자유도 3, α = 0.05) 음영
    const crit = 7.815;
    const sh = [`M${F.X(crit)} ${F.Y(0)}`];
    for (let i = 0; i <= 60; i += 1) {
        const xv = crit + ((14 - crit) * i) / 60;
        sh.push(`L${F.X(xv)} ${F.Y(f3(xv))}`);
    }
    sh.push(`L${F.X(14)} ${F.Y(0)} Z`);
    b.push(`<path d="${sh.join(' ')}" fill="var(--s3)" fill-opacity="0.32" stroke="none"/>`);
    b.push(F.curve(f1, { from: 0.42, cls: 's1' }));
    b.push(F.curve(f2, { from: 0.02, cls: 's2' }));
    b.push(F.curve(f3, { from: 0.02, cls: 's3' }));
    b.push(legend(452, 92, [{ slot: 1, name: '자유도 1' }, { slot: 2, name: '자유도 2' }, { slot: 3, name: '자유도 3' }]));
    // 임계값과 관찰값 표시
    b.push(line([[F.X(crit), F.Y(0)], [F.X(crit), F.Y(0.20)]], { stroke: 'var(--s3)', sw: 1.4, dash: '5 4' }));
    b.push(txt(F.X(crit) + 6, F.Y(0.225), '임계값 7.815', { cls: 'ink' }));
    b.push(txt(F.X(crit) + 6, F.Y(0.195), '(자유도 3, α = 0.05)', { cls: 'ink2', size: 'sm' }));
    b.push(line([[F.X(3.911), F.Y(0)], [F.X(3.911), F.Y(0.30)]], { stroke: 'var(--ink2)', sw: 1.4, dash: '5 4' }));
    b.push(txt(F.X(3.911), F.Y(0.325), '관찰 3.911', { anchor: 'middle', cls: 'ink bold' }));
    b.push(arw(F.X(11.6), F.Y(0.115), F.X(10.2), F.Y(0.022), { width: 1.6 }));
    b.push(txt(F.X(11.8), F.Y(0.125), '오른쪽 꼬리 넓이 = 5%', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 34, '관찰값이 임계값보다 왼쪽이면 우연으로 설명되는 범위 안이므로 가설을 버리지 않는다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 14, '오른쪽이면 그만한 어긋남이 우연으로는 20번에 한 번도 안 나온다는 뜻이므로 가설을 버린다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-chisquare',
        svg: svg({ width: W, height: H, title: '카이제곱 분포와 임계값', desc: '자유도별 분포 곡선과 유의수준 5%의 기각역', body: b.join('') }),
    };
})());

/* 9-4. 우열 관계 세 가지 */
add((() => {
    const W = 690, H = 348;
    const b = [];
    b.push(txt(W / 2, 26, '이형접합이 어떻게 보이는가 — 세 경우', { anchor: 'middle', cls: 'ink bold' }));
    const py = 46, ph = 266, pw = 216;
    const X0 = [16, 238, 460];
    const titles = ['완전우성', '불완전우성', '공우성'];
    const subs = ['한쪽만 보인다', '중간이 보인다', '둘 다 보인다'];
    const ratios = ['3 : 1', '1 : 2 : 1', '1 : 2 : 1'];
    for (let k = 0; k < 3; k += 1) {
        const x = X0[k], c = x + pw / 2;
        b.push(panel(x, py, pw, ph, titles[k], { sub: subs[k] }));
        b.push(txt(x + 14, py + 84, 'P', { cls: 'ink bold' }));
        b.push(circ(c - 34, py + 80, 19, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)' }));
        b.push(circ(c + 34, py + 80, 19, { fill: k === 2 ? 'var(--s2)' : 'var(--ink2)', op: k === 2 ? 0.85 : 0.10, stroke: 'var(--ink2)' }));
        b.push(txt(c, py + 85, '×', { anchor: 'middle', cls: 'ink bold' }));
        b.push(arw(c, py + 106, c, py + 130, { width: 1.6 }));
        b.push(txt(x + 14, py + 158, 'F~1', { cls: 'ink bold' }));
        if (k === 0) b.push(circ(c, py + 154, 19, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)' }));
        if (k === 1) b.push(circ(c, py + 154, 19, { fill: 'var(--s1)', op: 0.38, stroke: 'var(--ink2)' }));
        if (k === 2) {
            b.push(circ(c, py + 154, 19, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)' }));
            b.push(`<path d="M${r2(c)} ${r2(py + 135)} A19 19 0 0 1 ${r2(c)} ${r2(py + 173)} Z" fill="var(--s2)" fill-opacity="0.85"/>`);
            b.push(circ(c, py + 154, 19, { fill: 'none', stroke: 'var(--ink2)' }));
        }
        b.push(txt(c, py + 192, ['진한 색', '중간 색', '두 색이 함께'][k], { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        // F2 비율 막대
        const bx = x + 42, bw = pw - 60, byy = py + 208, bh = 24;
        const seg = k === 0 ? [[0.75, 'a'], [0.25, 'b']] : [[0.25, 'a'], [0.5, 'm'], [0.25, 'b']];
        let acc = 0;
        for (const [f, kind] of seg) {
            const sx = bx + bw * acc, sw2 = bw * f;
            if (kind === 'a') b.push(box(sx, byy, sw2, bh, { fill: 'var(--s1)', op: 0.85, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
            if (kind === 'b') b.push(box(sx, byy, sw2, bh, { fill: k === 2 ? 'var(--s2)' : 'var(--ink2)', op: k === 2 ? 0.85 : 0.10, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
            if (kind === 'm' && k === 1) b.push(box(sx, byy, sw2, bh, { fill: 'var(--s1)', op: 0.38, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
            if (kind === 'm' && k === 2) {
                b.push(box(sx, byy, sw2, bh / 2, { fill: 'var(--s1)', op: 0.85, stroke: 'none', sw: 0, rx: 0 }));
                b.push(box(sx, byy + bh / 2, sw2, bh / 2, { fill: 'var(--s2)', op: 0.85, stroke: 'none', sw: 0, rx: 0 }));
                b.push(box(sx, byy, sw2, bh, { fill: 'none', stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
            }
            acc += f;
        }
        b.push(txt(x + 14, byy + 17, 'F~2', { cls: 'ink bold' }));
        b.push(txt(c, byy + 46, ratios[k], { anchor: 'middle', cls: 'ink bold' }));
    }
    b.push(txt(W / 2, H - 10, '유전자형 비는 셋 다 1 : 2 : 1 로 같다. 달라지는 것은 그것이 몇 가지 표현형으로 보이느냐뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-dominance-types',
        svg: svg({ width: W, height: H, title: '완전우성 · 불완전우성 · 공우성', desc: '유전자형 비는 같고 표현형으로 드러나는 방식만 다르다', body: b.join('') }),
    };
})());

/* 9-5. 다인자 유전 — 좌가 늘면 계급이 촘촘해진다 */
add((() => {
    const W = 690, H = 368;
    const b = [];
    b.push(txt(W / 2, 26, '효과가 더해지는 유전자좌가 늘어나면 어떻게 되는가', { anchor: 'middle', cls: 'ink bold' }));
    const comb = (n, k) => { let v = 1; for (let i = 0; i < k; i += 1) v = (v * (n - i)) / (i + 1); return v; };
    const py = 46, ph = 284, pw = 216;
    const X0 = [16, 238, 460];
    for (let n = 1; n <= 3; n += 1) {
        const x = X0[n - 1], base = py + 214, top = py + 62, hmax = base - top;
        b.push(panel(x, py, pw, ph, `유전자좌 ${n}개`, { sub: `계급 ${2 * n + 1}가지` }));
        const K = 2 * n;
        const tot = 4 ** n;
        const inner = pw - 44;
        const step = inner / (K + 1);
        const barw = Math.min(30, step * 0.78);
        const pts = [];
        for (let k = 0; k <= K; k += 1) {
            const p = comb(K, k) / tot;
            const h = (p / 0.5) * hmax;
            const cxk = x + 22 + step * (k + 0.5);
            b.push(box(cxk - barw / 2, base - h, barw, h, { fill: 'var(--s1)', op: 0.5, stroke: 'var(--ink2)', sw: 1, rx: 2 }));
            pts.push([cxk, base - h]);
            b.push(txt(cxk, base + 16, String(k), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        b.push(line([[x + 16, base], [x + pw - 16, base]], { stroke: 'var(--ink2)', sw: 1.2 }));
        if (n === 3) b.push(line(pts, { stroke: 'var(--s2)', sw: 1.8, dash: '5 4' }));
        b.push(txt(x + pw / 2, base + 36, '표현형을 키우는 대립유전자 수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(x + pw / 2, base + 56, `양 끝 계급의 비율 = 1/${tot}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
    }
    b.push(txt(W / 2, H - 10, '계급이 촘촘해지고 양 끝이 드물어진다. 여기에 환경 차이가 더해지면 경계가 뭉개져 연속 분포로 보인다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-polygenic',
        svg: svg({ width: W, height: H, title: '다인자 유전에서 계급이 늘어나는 모습', desc: '유전자좌가 늘수록 표현형 계급이 촘촘해지고 극단값이 드물어진다', body: b.join('') }),
    };
})());

/* 9-6. 연관 — 검정교배 결과가 1:1:1:1 에서 벗어난다 */
add((() => {
    const W = 660, H = 366;
    const b = [];
    b.push(txt(W / 2, 26, '검정교배 AaBb × aabb 의 자손 1000 개체', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, '두 유전자좌가 독립이라면 네 갈래가 모두 250 이어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const base = 284, top = 74, hmax = base - top, smax = 400;
    const cats = [
        { lab: 'A B', n: 340, kind: '부모형', cls: 's1' },
        { lab: 'A b', n: 160, kind: '재조합형', cls: 's2' },
        { lab: 'a B', n: 150, kind: '재조합형', cls: 's2' },
        { lab: 'a b', n: 350, kind: '부모형', cls: 's1' },
    ];
    b.push(line([[70, base], [520, base]], { stroke: 'var(--ink2)', sw: 1.4 }));
    b.push(line([[70, base], [70, top - 6]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const t of [0, 100, 200, 300, 400]) {
        const y = base - (t / smax) * hmax;
        b.push(line([[70, y], [520, y]], { stroke: 'var(--grid)', sw: 1 }));
        b.push(txt(64, y + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    cats.forEach((c, i) => {
        const cxk = 122 + i * 100;
        const h = (c.n / smax) * hmax;
        b.push(box(cxk - 37, base - h, 74, h, { fill: `var(--${c.cls})`, op: c.cls === 's1' ? 0.55 : 0.45, stroke: 'var(--ink2)', sw: 1.2, rx: 3 }));
        b.push(txt(cxk, base - h - 8, String(c.n), { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(cxk, base + 20, c.lab, { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(cxk, base + 38, c.kind, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    const y250 = base - (250 / smax) * hmax;
    b.push(line([[70, y250], [540, y250]], { stroke: 'var(--ink)', sw: 1.6, dash: '6 4' }));
    b.push(txt(546, y250 - 6, '독립이면', { cls: 'ink', size: 'sm' }));
    b.push(txt(546, y250 + 10, '기대 250', { cls: 'ink', size: 'sm' }));
    b.push(txt(546, 200, '재조합률', { cls: 'ink bold' }));
    b.push(txt(546, 222, '(160+150)/1000', { cls: 'ink2', size: 'sm' }));
    b.push(txt(546, 244, '= 0.31', { cls: 'ink bold' }));
    b.push(txt(546, 266, '→ 31 cM', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 32, '부모형이 많고 재조합형이 적다. 두 유전자좌가 같은 염색체에 있고 그 사이에서 교차가 가끔만 일어난다는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '어느 쪽이 부모형인지는 개수가 많은 쪽으로 판정한다. 미리 알고 있을 필요가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-linkage-testcross',
        svg: svg({ width: W, height: H, title: '연관된 두 유전자좌의 검정교배 결과', desc: '부모형 조합이 기대보다 많고 재조합형이 적게 나온다', body: b.join('') }),
    };
})());

/* 9-7. 유전자 지도 */
add((() => {
    const W = 660, H = 278;
    const b = [];
    b.push(txt(W / 2, 26, '재조합률을 이어 붙여 만든 지도 — 1 cM 은 재조합률 1%', { anchor: 'middle', cls: 'ink bold' }));
    const y = 108, x0 = 120;
    const pos = { a: x0, b: x0 + 125, c: x0 + 310 };
    b.push(box(70, y - 9, 500, 18, { fill: 'var(--ink2)', op: 0.10, stroke: 'var(--ink2)', sw: 1.2, rx: 9 }));
    for (const [k, xk] of Object.entries(pos)) {
        b.push(line([[xk, y - 14], [xk, y + 14]], { stroke: 'var(--s1)', sw: 2.6 }));
        b.push(txt(xk, y - 24, k, { anchor: 'middle', cls: 'ink bold' }));
    }
    const span = (xa, xb, lab, yy) => {
        const out = [];
        out.push(line([[xa, yy - 7], [xa, yy + 7]], { stroke: 'var(--ink2)', sw: 1.2 }));
        out.push(line([[xb, yy - 7], [xb, yy + 7]], { stroke: 'var(--ink2)', sw: 1.2 }));
        out.push(line([[xa, yy], [xb, yy]], { stroke: 'var(--ink2)', sw: 1.2 }));
        out.push(box((xa + xb) / 2 - 40, yy - 11, 80, 22, { fill: 'var(--ink2)', op: 0.001, stroke: 'none', sw: 0 }));
        out.push(txt((xa + xb) / 2, yy - 14, lab, { anchor: 'middle', cls: 'ink' }));
        return out.join('');
    };
    b.push(span(pos.a, pos.b, '12.5 cM', y + 52));
    b.push(span(pos.b, pos.c, '18.5 cM', y + 52));
    b.push(span(pos.a, pos.c, '합 31.0 cM', y + 100));
    b.push(txt(W / 2, H - 34, '두 끝을 직접 재면 29.0% 가 나온다. 사이에서 교차가 두 번 일어난 자손이 부모형으로 되돌아가 빠졌기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 14, '그래서 지도는 짧은 구간을 재서 이어 붙인다. 멀리 떨어진 두 좌를 한 번에 재면 늘 과소평가된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-genemap',
        svg: svg({ width: W, height: H, title: '세 유전자좌의 지도', desc: '짧은 구간의 재조합률을 이어 붙여 만든 유전자 지도', body: b.join('') }),
    };
})());

/* 9-8. 가계도 읽기 */
add((() => {
    const W = 700, H = 374;
    const b = [];
    const sq = (x, y, aff, car) => box(x - 12, y - 12, 24, 24, { fill: aff ? 'var(--s1)' : 'none', op: aff ? 0.85 : 1, stroke: 'var(--ink)', sw: 1.6, rx: 2 })
        + (car ? circ(x, y, 3.4, { fill: 'var(--ink)', stroke: 'none', sw: 0 }) : '');
    const ci = (x, y, aff, car) => circ(x, y, 12, { fill: aff ? 'var(--s1)' : 'none', op: aff ? 0.85 : 1, stroke: 'var(--ink)', sw: 1.6 })
        + (car ? circ(x, y, 3.4, { fill: 'var(--ink)', stroke: 'none', sw: 0 }) : '');
    const mate = (x1, x2, y) => line([[x1 + 13, y], [x2 - 13, y]], { stroke: 'var(--ink)', sw: 1.4 });
    const drop = (x, y1, y2) => line([[x, y1], [x, y2]], { stroke: 'var(--ink)', sw: 1.4 });
    const sib = (xa, xb, y, kids) => [line([[xa, y], [xb, y]], { stroke: 'var(--ink)', sw: 1.4 })]
        .concat(kids.map(k => line([[k, y], [k, y + 22]], { stroke: 'var(--ink)', sw: 1.4 }))).join('');
    b.push(txt(W / 2, 26, '가계도 — 기호부터', { anchor: 'middle', cls: 'ink bold' }));
    // 범례
    b.push(sq(36, 60, false, false));
    b.push(txt(54, 65, '남자', { cls: 'ink2', size: 'sm' }));
    b.push(ci(112, 60, false, false));
    b.push(txt(130, 65, '여자', { cls: 'ink2', size: 'sm' }));
    b.push(sq(190, 60, true, false));
    b.push(txt(208, 65, '발현', { cls: 'ink2', size: 'sm' }));
    b.push(ci(268, 60, false, true));
    b.push(txt(286, 65, '보인자 (추정)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(400, 65, '가로선은 부부, 그 아래로 내려간 세로선은 자녀를 뜻한다', { cls: 'ink2', size: 'sm' }));
    // 두 가계
    const py = 84, ph = 276;
    b.push(panel(16, py, 330, ph, '상염색체 우성', { sub: '세대를 건너뛰지 않는다' }));
    b.push(panel(354, py, 330, ph, '상염색체 열성', { sub: '정상인 부모에게서 발현자가 나온다' }));
    const gy = [154, 224, 294];
    // 왼쪽 가계
    b.push(sq(145, gy[0], true, false)); b.push(ci(217, gy[0], false, false));
    b.push(mate(145, 217, gy[0])); b.push(drop(181, gy[0], gy[0] + 24));
    b.push(sib(109, 253, gy[0] + 24, [109, 181, 253]));
    b.push(ci(109, gy[1], true, false)); b.push(sq(181, gy[1], false, false)); b.push(sq(253, gy[1], true, false));
    b.push(ci(313, gy[1], false, false)); b.push(mate(253, 313, gy[1])); b.push(drop(283, gy[1], gy[1] + 24));
    b.push(sib(259, 307, gy[1] + 24, [259, 307]));
    b.push(sq(259, gy[2], false, false)); b.push(ci(307, gy[2], true, false));
    b.push(txt(30, gy[0] + 4, 'I', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, gy[1] + 4, 'II', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, gy[2] + 4, 'III', { cls: 'ink2', size: 'sm' }));
    // 오른쪽 가계
    b.push(sq(483, gy[0], false, true)); b.push(ci(555, gy[0], false, true));
    b.push(mate(483, 555, gy[0])); b.push(drop(519, gy[0], gy[0] + 24));
    b.push(sib(447, 591, gy[0] + 24, [447, 519, 591]));
    b.push(ci(447, gy[1], false, true)); b.push(sq(519, gy[1], true, false)); b.push(sq(591, gy[1], false, false));
    b.push(ci(651, gy[1], false, false)); b.push(mate(591, 651, gy[1])); b.push(drop(621, gy[1], gy[1] + 24));
    b.push(sib(597, 645, gy[1] + 24, [597, 645]));
    b.push(sq(597, gy[2], false, false)); b.push(ci(645, gy[2], false, false));
    b.push(txt(368, gy[0] + 4, 'I', { cls: 'ink2', size: 'sm' }));
    b.push(txt(368, gy[1] + 4, 'II', { cls: 'ink2', size: 'sm' }));
    b.push(txt(368, gy[2] + 4, 'III', { cls: 'ink2', size: 'sm' }));
    b.push(txt(181, py + ph - 14, '발현자마다 부모 한쪽이 발현자다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(519, py + ph - 14, 'II-2 의 부모는 둘 다 정상이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-pedigree',
        svg: svg({ width: W, height: H, title: '가계도 기호와 두 유전 양식', desc: '상염색체 우성과 열성 가계도의 겉모습 차이', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 10장 — 분자생물학의 중심원리
 * ================================================================== */

/* 10-1. DNA 이중나선의 기하 */
add((() => {
    const W = 700, H = 404;
    const b = [];
    b.push(txt(W / 2, 26, '이중나선 — 사다리를 꼬아 놓은 것', { anchor: 'middle', cls: 'ink bold' }));
    b.push(panel(16, 46, 336, 336, '펼쳐 놓으면 사다리다', { sub: '두 가닥의 방향이 서로 반대다' }));
    const lx = 118, rx = 250, y0 = 130, dy = 42;
    const pairs = [['A', 'T', 2], ['G', 'C', 3], ['T', 'A', 2], ['C', 'G', 3], ['A', 'T', 2]];
    b.push(line([[lx, y0 - 22], [lx, y0 + dy * 4 + 22]], { stroke: 'var(--s1)', sw: 5 }));
    b.push(line([[rx, y0 - 22], [rx, y0 + dy * 4 + 22]], { stroke: 'var(--s2)', sw: 5 }));
    b.push(txt(lx, y0 - 30, '5′', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(lx, y0 + dy * 4 + 42, '3′', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(rx, y0 - 30, '3′', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(rx, y0 + dy * 4 + 42, '5′', { anchor: 'middle', cls: 'ink bold' }));
    pairs.forEach((pr, i) => {
        const y = y0 + dy * i;
        b.push(txt(lx + 15, y + 5, pr[0], { cls: 'ink bold' }));
        b.push(txt(rx - 15, y + 5, pr[1], { anchor: 'end', cls: 'ink bold' }));
        for (let k = 0; k < pr[2]; k += 1) {
            const yy = y - (pr[2] - 1) * 4 + k * 8;
            b.push(line([[lx + 32, yy], [rx - 32, yy]], { stroke: 'var(--ink2)', sw: 1.5, dash: '3 3' }));
        }
        b.push(txt(rx + 14, y + 5, `결합 ${pr[2]}개`, { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(34, 366, '한 가닥의 차례를 알면 나머지가 저절로 정해진다', { cls: 'ink2', size: 'sm' }));
    b.push(panel(364, 46, 320, 336, '꼬아 놓으면 나선이다'));
    const cx = 510, A = 32, P = 112, top = 92, bot = 344;
    const strand = (ph, cls) => {
        const pts = [];
        for (let y = top; y <= bot; y += 3) pts.push([cx + A * Math.sin((2 * Math.PI * (y - top)) / P + ph), y]);
        return line(pts, { stroke: `var(--${cls})`, sw: 3.4 });
    };
    b.push(strand(0, 's1'));
    b.push(strand(2.3, 's2'));
    for (let y = top + 8; y < bot; y += 13) {
        const xa = cx + A * Math.sin((2 * Math.PI * (y - top)) / P);
        const xb = cx + A * Math.sin((2 * Math.PI * (y - top)) / P + 2.3);
        b.push(line([[xa, y], [xb, y]], { stroke: 'var(--ink2)', sw: 1.2 }));
    }
    b.push(arw(cx - A - 24, 120, cx - A - 24, 120 + P, { width: 1.5 }));
    b.push(arw(cx - A - 24, 120 + P, cx - A - 24, 120, { width: 1.5 }));
    b.push(txt(cx - A - 32, 160, '한 바퀴', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(cx - A - 32, 176, '약 3.4 nm', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(cx - A - 32, 192, '(약 10 염기쌍)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(arw(cx - A - 4, 306, cx + A + 4, 306, { width: 1.5 }));
    b.push(arw(cx + A + 4, 306, cx - A - 4, 306, { width: 1.5 }));
    b.push(txt(cx + A + 14, 300, '지름 약 2 nm', { cls: 'ink', size: 'sm' }));
    b.push(txt(cx + A + 14, 210, '주홈 — 전사인자가', { cls: 'ink2', size: 'sm' }));
    b.push(txt(cx + A + 14, 226, '서열을 읽는 자리', { cls: 'ink2', size: 'sm' }));
    b.push(txt(382, 366, '염기쌍 하나가 축 방향으로 0.34 nm 를 차지한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-dna-structure',
        svg: svg({ width: W, height: H, title: 'DNA 이중나선의 구조와 치수', desc: '역평행한 두 가닥, 정해진 염기쌍, 그리고 나선의 크기', body: b.join('') }),
    };
})());

/* 10-2. 메셀슨-스탈 */
add((() => {
    const W = 660, H = 456;
    const b = [];
    b.push(txt(W / 2, 26, '세 가설은 서로 다른 띠를 예측한다 — 그래서 한 번의 실험으로 갈린다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, '무거운 질소로 키운 세균을 가벼운 배지로 옮긴 뒤 DNA 를 밀도에 따라 가라앉힌다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const cols = [210, 340, 470];
    ['0세대', '1세대', '2세대'].forEach((g, i) => b.push(txt(cols[i], 78, g, { anchor: 'middle', cls: 'ink bold' })));
    const rows = [
        { name: '반보존적', note: '관찰과 일치', bands: [[['H', 1]], [['M', 1]], [['M', 0.5], ['L', 0.5]]] },
        { name: '보존적', note: '1세대에서 탈락', bands: [[['H', 1]], [['H', 0.5], ['L', 0.5]], [['H', 0.25], ['L', 0.75]]] },
        { name: '분산적', note: '2세대에서 탈락', bands: [[['H', 1]], [['M', 1]], [['ML', 1]]] },
    ];
    const ty = 92, th = 112, tubeW = 48, tubeH = 92;
    const posOf = { H: 0.80, M: 0.52, ML: 0.36, L: 0.22 };
    rows.forEach((r, ri) => {
        const yy = ty + ri * th;
        b.push(txt(30, yy + 40, r.name, { cls: 'ink bold' }));
        b.push(txt(30, yy + 60, r.note, { cls: 'ink2', size: 'sm' }));
        r.bands.forEach((set, ci) => {
            const x = cols[ci] - tubeW / 2;
            b.push(box(x, yy + 6, tubeW, tubeH, { fill: 'var(--ink2)', op: 0.06, stroke: 'var(--ink2)', sw: 1.2, rx: 6 }));
            for (const [kind, amt] of set) {
                const by = yy + 6 + tubeH * posOf[kind];
                b.push(box(x + 3, by - 4, tubeW - 6, 8, { fill: 'var(--s1)', op: 0.25 + 0.6 * amt, stroke: 'none', sw: 0, rx: 2 }));
            }
        });
        if (ri < rows.length - 1) b.push(line([[26, yy + th - 8], [634, yy + th - 8]], { stroke: 'var(--grid)', sw: 1 }));
    });
    b.push(txt(566, ty + 26, '가벼움', { cls: 'ink2', size: 'sm' }));
    b.push(txt(566, ty + 92, '무거움', { cls: 'ink2', size: 'sm' }));
    b.push(arw(554, ty + 34, 554, ty + 82, { width: 1.4 }));
    b.push(txt(W / 2, H - 32, '1세대에서 띠가 하나이고 중간 밀도면 보존적 가설이 죽고, 2세대에서 띠가 둘로 갈라지면 분산적 가설이 죽는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '남는 것은 반보존적 하나뿐이다. 좋은 실험이란 이렇게 여러 가설을 한꺼번에 갈라놓는 실험이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-meselson',
        svg: svg({ width: W, height: H, title: '메셀슨-스탈 실험이 예측하는 띠', desc: '세 복제 가설이 세대마다 서로 다른 띠 무늬를 예측한다', body: b.join('') }),
    };
})());

/* 10-3. 복제 분기점 */
add((() => {
    const W = 700, H = 392;
    const b = [];
    b.push(txt(W / 2, 26, '복제 분기점 — 5′→3′ 로만 붙일 수 있다는 제약 하나가 모든 비대칭을 만든다', { anchor: 'middle', cls: 'ink bold' }));
    const FX = 440, FY = 200;
    // 아직 안 풀린 이중나선
    b.push(line([[FX, FY - 10], [670, FY - 10]], { stroke: 'var(--s1)', sw: 4 }));
    b.push(line([[FX, FY + 10], [670, FY + 10]], { stroke: 'var(--s2)', sw: 4 }));
    for (let x = FX + 16; x < 666; x += 14) b.push(line([[x, FY - 8], [x, FY + 8]], { stroke: 'var(--grid)', sw: 1.2 }));
    b.push(txt(600, FY - 22, '아직 안 풀린 어미 이중나선', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 풀린 어미 가닥 둘
    b.push(line([[110, 100], [350, 100], [FX, FY - 10]], { stroke: 'var(--s1)', sw: 4 }));
    b.push(line([[110, 300], [350, 300], [FX, FY + 10]], { stroke: 'var(--s2)', sw: 4 }));
    b.push(txt(102, 104, '3′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(102, 304, '5′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(116, 64, '지연가닥 — 짧게 여러 번 만들고 나중에 잇는다 (오카자키 조각)', { cls: 'ink', size: 'sm' }));
    b.push(txt(116, 86, '어미 가닥 (지연가닥의 주형)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(116, 322, '어미 가닥 (선도가닥의 주형)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(116, 344, '선도가닥 — 분기점을 따라가며 한 번에 이어 만든다', { cls: 'ink', size: 'sm' }));
    // 지연가닥 조각
    [[124, 196], [212, 284], [300, 372]].forEach((f, i) => {
        b.push(box(f[0], 128, 24, 9, { fill: 'var(--s2)', op: 0.85, stroke: 'none', sw: 0, rx: 3 }));
        b.push(line([[f[0] + 26, 132], [f[1], 132]], { stroke: 'var(--s3)', sw: 4 }));
        b.push(txt((f[0] + f[1]) / 2, 156, `조각 ${3 - i}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(30, 136, 'RNA 프라이머', { cls: 'ink2', size: 'sm' }));
    b.push(arw(200, 178, 160, 178, { cls: 's3', width: 2.2 }));
    b.push(arw(288, 178, 248, 178, { cls: 's3', width: 2.2 }));
    b.push(txt(120, 204, '조각 하나하나도 5′→3′ 다', { cls: 'ink2', size: 'sm' }));
    // 선도가닥
    b.push(line([[150, 272], [372, 272]], { stroke: 'var(--s3)', sw: 4 }));
    b.push(arw(372, 272, 408, 240, { cls: 's3', width: 2.4 }));
    b.push(txt(142, 276, '5′', { anchor: 'end', cls: 'ink bold' }));
    // 헬리케이스
    b.push(poly([[436, 174], [472, 200], [436, 226]], { fill: 'var(--ink2)', op: 0.30 }));
    b.push(txt(478, 248, '헬리케이스', { cls: 'ink', size: 'sm' }));
    b.push(txt(478, 264, '(여기서 풀린다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '합성 방향은 언제나 5′→3′ 다. 지연가닥이 조각나는 것은 그 방향이 분기점 진행 방향과 반대이기 때문이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-replication-fork',
        svg: svg({ width: W, height: H, title: '복제 분기점의 선도가닥과 지연가닥', desc: '5′→3′ 제약에서 두 가닥의 합성 방식이 갈라진다', body: b.join('') }),
    };
})());

/* 10-4. 전사 — 어느 가닥을 읽는가 */
add((() => {
    const W = 700, H = 332;
    const b = [];
    b.push(txt(W / 2, 26, '전사 — 한 가닥만 읽고, 만들어진 RNA 는 반대쪽 가닥과 서열이 같다', { anchor: 'middle', cls: 'ink bold' }));
    const x0 = 158, cw = 26;
    const rowY = [118, 160, 238];
    const drawSeq = (y, seq, cls) => {
        const out = [];
        out.push(box(x0 - 8, y - 16, cw * seq.length + 16, 29, { fill: `var(--${cls})`, op: 0.12, stroke: 'var(--ink2)', sw: 1.1, rx: 4 }));
        for (let i = 0; i < seq.length; i += 1) out.push(txt(x0 + cw * i + cw / 2, y + 4, seq[i], { anchor: 'middle', cls: 'ink bold' }));
        return out.join('');
    };
    b.push(txt(x0 - 16, 106, '5′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(x0 - 16, 168, '3′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(x0 - 16, 246, '5′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(x0 + cw * 12 + 12, 122, '3′', { cls: 'ink bold' }));
    b.push(txt(x0 + cw * 12 + 12, 164, '5′', { cls: 'ink bold' }));
    b.push(txt(x0 + cw * 12 + 12, 242, '3′', { cls: 'ink bold' }));
    b.push(txt(x0 - 16, 92, '암호가닥', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(drawSeq(rowY[0], 'ATGCCGTTAGCA', 's1'));
    b.push(drawSeq(rowY[1], 'TACGGCAATCGT', 's2'));
    b.push(txt(x0 - 16, 186, '주형가닥', { anchor: 'end', cls: 'ink', size: 'sm' }));
    for (let i = 0; i < 12; i += 1) b.push(line([[x0 + cw * i + cw / 2, rowY[0] + 14], [x0 + cw * i + cw / 2, rowY[1] - 18]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(txt(x0 - 16, 212, 'RNA', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(drawSeq(rowY[2], 'AUGCCGUUAGCA', 's3'));
    b.push(arw(x0 + 60, 196, x0 + 60, 220, { cls: 's3', width: 2 }));
    b.push(txt(x0 + cw * 12 + 34, rowY[2] + 4, 'T 자리에 U', { cls: 'ink2', size: 'sm' }));
    b.push(txt(x0 + cw * 12 + 34, rowY[0] + 4, '서열이 같다', { cls: 'ink2', size: 'sm' }));
    b.push(poly([[36, 106], [96, 106], [110, 134], [96, 162], [36, 162]], { fill: 'var(--ink2)', op: 0.18 }));
    b.push(txt(70, 130, '프로모터', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(70, 148, '(방향을 정한다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 34, 'RNA 는 주형가닥과 상보적이므로 결과적으로 암호가닥과 같은 서열이 된다(T 자리에 U).', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 14, '어느 가닥이 주형이 될지는 프로모터가 어느 쪽을 보고 앉아 있느냐로 정해진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-transcription-strands',
        svg: svg({ width: W, height: H, title: '주형가닥과 암호가닥', desc: '전사에서 읽는 가닥과 만들어진 RNA 의 서열 관계', body: b.join('') }),
    };
})());

/* 10-5. RNA 가공과 대체 스플라이싱 */
add((() => {
    const W = 700, H = 348;
    const b = [];
    b.push(txt(W / 2, 26, 'RNA 가공 — 만든 것을 그대로 쓰지 않는다', { anchor: 'middle', cls: 'ink bold' }));
    const gx = 136, unit = 42;
    const segs = [
        { k: 'E', n: '엑손1', w: 1.4 }, { k: 'I', w: 1.6 }, { k: 'E', n: '엑손2', w: 1.0 },
        { k: 'I', w: 1.8 }, { k: 'E', n: '엑손3', w: 1.2 }, { k: 'I', w: 1.4 }, { k: 'E', n: '엑손4', w: 1.6 },
    ];
    const drawGene = (y, list, names) => {
        const out = []; let x = gx;
        for (const s of list) {
            const w = s.w * unit;
            if (s.k === 'E') {
                out.push(box(x, y - 13, w, 26, { fill: 'var(--s1)', op: 0.5, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
                if (names && s.n) out.push(txt(x + w / 2, y + 5, s.n, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            } else {
                out.push(line([[x, y], [x + w, y]], { stroke: 'var(--ink2)', sw: 1.6 }));
                if (names) out.push(txt(x + w / 2, y - 17, '인트론', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
            x += w;
        }
        return { body: out.join(''), end: x };
    };
    b.push(txt(28, 100, '1차 전사체', { cls: 'ink bold' }));
    b.push(drawGene(100, segs, true).body);
    b.push(arw(360, 122, 360, 152, { width: 2 }));
    b.push(txt(374, 144, '모자와 꼬리를 붙이고 인트론을 잘라 낸다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(28, 186, '성숙 mRNA', { cls: 'ink bold' }));
    const mature = [{ k: 'E', n: '1', w: 1.4 }, { k: 'E', n: '2', w: 1.0 }, { k: 'E', n: '3', w: 1.2 }, { k: 'E', n: '4', w: 1.6 }];
    const g2 = drawGene(186, mature, true);
    b.push(g2.body);
    b.push(circ(gx - 16, 186, 9, { fill: 'var(--s2)', op: 0.85, stroke: 'var(--ink2)', sw: 1 }));
    b.push(txt(gx - 16, 164, '모자', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(line([[g2.end + 2, 186], [g2.end + 56, 186]], { stroke: 'var(--s2)', sw: 3, dash: '3 3' }));
    b.push(txt(g2.end + 30, 164, '폴리(A) 꼬리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(line([[26, 218], [674, 218]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(txt(28, 244, '대체 스플라이싱', { cls: 'ink bold' }));
    b.push(txt(146, 244, '어떤 엑손을 넣을지 골라 한 유전자에서 여러 단백질을 만든다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(28, 290, '이형체 A', { cls: 'ink', size: 'sm' }));
    b.push(drawGene(286, [{ k: 'E', n: '1', w: 1.4 }, { k: 'E', n: '2', w: 1.0 }, { k: 'E', n: '4', w: 1.6 }], true).body);
    b.push(txt(28, 326, '이형체 B', { cls: 'ink', size: 'sm' }));
    b.push(drawGene(322, [{ k: 'E', n: '1', w: 1.4 }, { k: 'E', n: '3', w: 1.2 }, { k: 'E', n: '4', w: 1.6 }], true).body);
    b.push(txt(400, 290, '엑손 3 이 빠졌다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(400, 326, '엑손 2 가 빠졌다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-splicing',
        svg: svg({ width: W, height: H, title: 'RNA 가공과 대체 스플라이싱', desc: '1차 전사체에서 인트론을 잘라 mRNA 를 만들고, 고르는 엑손을 바꾸면 산물이 달라진다', body: b.join('') }),
    };
})());

/* 10-6. 유전암호표 */
add((() => {
    const W = 706, H = 508;
    const b = [];
    const bases = ['U', 'C', 'A', 'G'];
    const code = {
        UU: ['Phe', 'Phe', 'Leu', 'Leu'], UC: ['Ser', 'Ser', 'Ser', 'Ser'],
        UA: ['Tyr', 'Tyr', '종결', '종결'], UG: ['Cys', 'Cys', '종결', 'Trp'],
        CU: ['Leu', 'Leu', 'Leu', 'Leu'], CC: ['Pro', 'Pro', 'Pro', 'Pro'],
        CA: ['His', 'His', 'Gln', 'Gln'], CG: ['Arg', 'Arg', 'Arg', 'Arg'],
        AU: ['Ile', 'Ile', 'Ile', 'Met'], AC: ['Thr', 'Thr', 'Thr', 'Thr'],
        AA: ['Asn', 'Asn', 'Lys', 'Lys'], AG: ['Ser', 'Ser', 'Arg', 'Arg'],
        GU: ['Val', 'Val', 'Val', 'Val'], GC: ['Ala', 'Ala', 'Ala', 'Ala'],
        GA: ['Asp', 'Asp', 'Glu', 'Glu'], GG: ['Gly', 'Gly', 'Gly', 'Gly'],
    };
    b.push(txt(W / 2, 24, '유전암호표 — mRNA 의 세 글자로 아미노산 하나를 찾는다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 44, '① 왼쪽에서 첫 글자, ② 위에서 둘째 글자, ③ 오른쪽에서 셋째 글자를 찾으면 칸이 하나로 정해진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const cx0 = 90, cw = 142, ry0 = 92, rh = 22;
    b.push(txt(cx0 + cw * 2, 68, '② 둘째 글자', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(52, 84, '① 첫', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(676, 84, '③ 셋', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    bases.forEach((s, j) => b.push(txt(cx0 + cw * j + cw / 2, 86, s, { anchor: 'middle', cls: 'ink bold' })));
    bases.forEach((f, i) => {
        const by = ry0 + i * 4 * rh;
        b.push(box(30, by, 44, 4 * rh, { fill: 'var(--s1)', op: i % 2 ? 0.10 : 0.18, stroke: 'var(--grid)', sw: 1, rx: 3 }));
        b.push(txt(52, by + 2 * rh + 5, f, { anchor: 'middle', cls: 'ink bold' }));
        bases.forEach((s, j) => {
            const cxk = cx0 + cw * j;
            b.push(box(cxk, by, cw, 4 * rh, { fill: 'var(--ink2)', op: (i + j) % 2 ? 0.05 : 0.10, stroke: 'var(--grid)', sw: 1, rx: 3 }));
            code[f + s].forEach((aa, k) => {
                const yy = by + k * rh;
                const codon = f + s + bases[k];
                const stop = aa === '종결';
                const start = codon === 'AUG';
                if (stop || start) b.push(box(cxk + 2, yy + 2, cw - 4, rh - 3, { fill: stop ? 'var(--s2)' : 'var(--s3)', op: 0.32, stroke: 'none', sw: 0, rx: 3 }));
                b.push(txt(cxk + 12, yy + rh - 6, codon, { cls: 'ink bold' }));
                b.push(txt(cxk + 66, yy + rh - 6, aa, { cls: 'ink' }));
                if (start) b.push(txt(cxk + 108, yy + rh - 6, '개시', { cls: 'ink2', size: 'sm' }));
            });
        });
        bases.forEach((t, k) => b.push(txt(676, by + k * rh + rh - 6, t, { anchor: 'middle', cls: 'ink2' })));
    });
    const yEnd = ry0 + 16 * rh;
    b.push(box(30, yEnd + 14, 16, 12, { fill: 'var(--s3)', op: 0.32, stroke: 'var(--grid)', sw: 1, rx: 2 }));
    b.push(txt(54, yEnd + 24, '개시코돈 AUG (메싸이오닌을 겸한다)', { cls: 'ink2', size: 'sm' }));
    b.push(box(310, yEnd + 14, 16, 12, { fill: 'var(--s2)', op: 0.32, stroke: 'var(--grid)', sw: 1, rx: 2 }));
    b.push(txt(334, yEnd + 24, '종결코돈 세 개 — 아미노산을 지정하지 않는다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, yEnd + 46, '같은 아미노산을 지정하는 코돈은 대개 셋째 글자만 다르다. 그래서 셋째 자리가 바뀌어도 단백질이 그대로인 일이 잦다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-codon-table',
        svg: svg({ width: W, height: H, title: '표준 유전암호표', desc: '코돈 64개와 그것이 지정하는 아미노산', body: b.join('') }),
    };
})());

/* 10-7. 번역 — 리보솜의 세 자리 */
add((() => {
    const W = 690, H = 348;
    const b = [];
    b.push(txt(W / 2, 26, '번역 — 리보솜은 코돈을 세 칸짜리 창으로 훑는다', { anchor: 'middle', cls: 'ink bold' }));
    const mx = 96, cw = 54, my = 246;
    const codons = ['AUG', 'GCA', 'UUC', 'GAU', 'AAG', 'UGA'];
    b.push(line([[mx - 40, my], [mx + cw * 6 + 40, my]], { stroke: 'var(--ink2)', sw: 3 }));
    codons.forEach((c, i) => {
        b.push(box(mx + cw * i, my - 15, cw - 4, 30, { fill: 'var(--s1)', op: 0.14, stroke: 'var(--ink2)', sw: 1, rx: 3 }));
        b.push(txt(mx + cw * i + (cw - 4) / 2, my + 5, c, { anchor: 'middle', cls: 'ink bold' }));
    });
    b.push(txt(mx - 46, my + 5, '5′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(mx + cw * 6 + 46, my + 5, '3′', { cls: 'ink bold' }));
    b.push(txt(mx + cw * 6 + 46, my + 24, 'mRNA', { cls: 'ink2', size: 'sm' }));
    const rx0 = mx + cw - 6, rw = cw * 3 + 4;
    b.push(box(rx0, my - 98, rw, 84, { fill: 'var(--ink2)', op: 0.12, stroke: 'var(--ink2)', sw: 1.4, rx: 14 }));
    ['E', 'P', 'A'].forEach((s, i) => {
        const w = (rw - 16) / 3 - 4;
        const x = rx0 + 8 + i * (rw - 16) / 3;
        b.push(box(x, my - 90, w, 68, { fill: 'none', stroke: 'var(--grid)', sw: 1, rx: 6 }));
        b.push(txt(x + w / 2, my - 76, `${s} 자리`, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    });
    b.push(txt(rx0 + rw - 8, my - 108, '리보솜', { anchor: 'end', cls: 'ink bold' }));
    const tRNA = (x, y, lab) => poly([[x - 17, y], [x + 17, y], [x + 9, y + 32], [x - 9, y + 32]], { fill: 'var(--s3)', op: 0.38 })
        + txt(x, y + 24, lab, { anchor: 'middle', cls: 'ink', size: 'sm' });
    b.push(tRNA(rx0 + 8 + (rw - 16) / 6, my - 54, 'CGU'));
    b.push(tRNA(rx0 + 8 + (rw - 16) / 2, my - 54, 'AAG'));
    for (let i = 0; i < 3; i += 1) b.push(circ(rx0 + 28 + i * 16, my - 120 - i * 5, 8, { fill: 'var(--s1)', op: 0.75, stroke: 'var(--ink2)', sw: 1 }));
    b.push(txt(rx0 + 84, my - 132, '만들어지고 있는 펩타이드', { cls: 'ink2', size: 'sm' }));
    ['① A 자리에 코돈과 맞는 tRNA 가 들어온다', '② P 자리의 사슬이 A 자리 아미노산에 이어진다', '③ 리보솜이 코돈 하나만큼 움직인다'].forEach((s, i) => b.push(txt(40, 292 + i * 18, s, { cls: 'ink2', size: 'sm' })));
    b.push(txt(420, 292, 'tRNA 의 세 글자(안티코돈)가 코돈과', { cls: 'ink2', size: 'sm' }));
    b.push(txt(420, 310, '상보적으로 짝지어 어느 아미노산인지를', { cls: 'ink2', size: 'sm' }));
    b.push(txt(420, 328, '정한다 (안티코돈은 3′→5′ 로 적었다)', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-translation',
        svg: svg({ width: W, height: H, title: '리보솜의 E · P · A 자리와 번역의 세 단계', desc: '코돈과 안티코돈의 짝짓기로 아미노산이 차례로 이어진다', body: b.join('') }),
    };
})());

/* 10-8. 돌연변이 유형 */
add((() => {
    const W = 700, H = 400;
    const b = [];
    b.push(txt(W / 2, 26, '한 글자가 바뀌면 어디까지 달라지는가', { anchor: 'middle', cls: 'ink bold' }));
    const rows = [
        { n: '야생형', s: 'AUG GCA UUC GAU AAG UGA', a: 'Met Ala Phe Asp Lys 종결', m: '', hi: -1, cls: 'ink2' },
        { n: '침묵', s: 'AUG GCG UUC GAU AAG UGA', a: 'Met Ala Phe Asp Lys 종결', m: '코돈은 바뀌었는데 아미노산은 그대로', hi: 1, cls: 's3' },
        { n: '미스센스', s: 'AUG GAA UUC GAU AAG UGA', a: 'Met Glu Phe Asp Lys 종결', m: '아미노산 하나가 다른 것으로', hi: 1, cls: 's1' },
        { n: '넌센스', s: 'AUG GCA UAA GAU AAG UGA', a: 'Met Ala 종결', m: '여기서 잘린다', hi: 2, cls: 's2' },
        { n: '틀 이동', s: 'AUG GAU UCG AUA AGU GA', a: 'Met Asp Ser Ile Ser …', m: 'C 하나가 빠져 뒤가 전부 밀렸다', hi: 1, cls: 's2' },
    ];
    const y0 = 66, rh = 60, sx = 126, cwc = 62;
    rows.forEach((r, i) => {
        const y = y0 + i * rh;
        b.push(txt(28, y + 18, r.n, { cls: 'ink bold' }));
        r.s.split(' ').forEach((c, k) => {
            const strong = k === r.hi;
            const after = r.hi >= 0 && k > r.hi;
            b.push(box(sx + cwc * k, y, cwc - 5, 26, {
                fill: strong || after ? `var(--${r.cls})` : 'var(--ink2)',
                op: strong ? 0.40 : (after && r.n === '틀 이동' ? 0.18 : 0.06),
                stroke: 'var(--grid)', sw: 1, rx: 3,
            }));
            b.push(txt(sx + cwc * k + (cwc - 5) / 2, y + 18, c, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        });
        b.push(txt(sx, y + 44, r.a, { cls: 'ink', size: 'sm' }));
        if (r.m) b.push(txt(sx + 236, y + 44, r.m, { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(W / 2, H - 30, '길이가 3의 배수만큼 바뀌지 않으면 읽는 틀 자체가 밀려 그 뒤가 전부 무의미해진다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 10, '그래서 한 글자 결실이 한 글자 치환보다 훨씬 파괴적이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-mutation-types',
        svg: svg({ width: W, height: H, title: '돌연변이 유형과 단백질에 미치는 결과', desc: '같은 한 글자 변화라도 결과가 크게 달라진다', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 11장 — 유전자 발현 조절
 * ================================================================== */

/** DNA 위의 구간 하나. */
function seg(x, y, w, lab, { cls = 'ink2', op = 0.14, h = 26 } = {}) {
    return box(x, y, w, h, { fill: `var(--${cls})`, op, stroke: 'var(--ink2)', sw: 1.1, rx: 3 })
        + txt(x + w / 2, y + h / 2 + 5, lab, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
}

/* 11-1. lac 오페론 */
add((() => {
    const W = 700, H = 456;
    const b = [];
    b.push(txt(W / 2, 26, 'lac 오페론 — 신호 두 개를 받아 하나의 답을 낸다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, '젖당이 있고 포도당이 없을 때만 강하게 전사한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gy = 84;
    b.push(line([[24, gy + 13], [676, gy + 13]], { stroke: 'var(--ink2)', sw: 2 }));
    b.push(seg(28, gy, 84, 'lacI', { cls: 's3', op: 0.24 }));
    b.push(seg(160, gy, 78, 'CAP 자리', { cls: 's1', op: 0.24 }));
    b.push(seg(240, gy, 52, '프로모터', { cls: 'ink2', op: 0.18 }));
    b.push(seg(294, gy, 52, '오퍼레이터', { cls: 's2', op: 0.26 }));
    b.push(seg(350, gy, 106, 'lacZ', { cls: 'ink2', op: 0.10 }));
    b.push(seg(458, gy, 106, 'lacY', { cls: 'ink2', op: 0.10 }));
    b.push(seg(566, gy, 106, 'lacA', { cls: 'ink2', op: 0.10 }));
    b.push(txt(70, gy - 8, '억압자를 만든다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(199, gy - 8, '활성자가 앉는 자리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(320, gy - 8, '억압자가 막는 자리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(511, gy + 44, '세 유전자가 함께 전사되어 하나의 mRNA 가 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(line([[24, 156], [676, 156]], { stroke: 'var(--grid)', sw: 1 }));
    const colx = [46, 122, 220, 380, 512];
    ['포도당', '젖당', '억압자가 오퍼레이터에', 'CAP 이 프로모터에', '전사량'].forEach((h, i) => b.push(txt(colx[i], 184, h, { cls: 'ink bold', size: 'sm' })));
    const cases = [
        { g: '있음', l: '없음', r: '붙어 있다 (차단)', c: '못 붙는다', t: 0 },
        { g: '있음', l: '있음', r: '떨어졌다', c: '못 붙는다', t: 0.08 },
        { g: '없음', l: '없음', r: '붙어 있다 (차단)', c: '붙었다', t: 0 },
        { g: '없음', l: '있음', r: '떨어졌다', c: '붙었다', t: 1 },
    ];
    cases.forEach((c, i) => {
        const y = 218 + i * 44;
        if (i === 3) b.push(box(30, y - 18, 640, 38, { fill: 'var(--s3)', op: 0.16, stroke: 'none', sw: 0, rx: 5 }));
        b.push(txt(colx[0], y + 4, c.g, { cls: 'ink' }));
        b.push(txt(colx[1], y + 4, c.l, { cls: 'ink' }));
        b.push(txt(colx[2], y + 4, c.r, { cls: 'ink2', size: 'sm' }));
        b.push(txt(colx[3], y + 4, c.c, { cls: 'ink2', size: 'sm' }));
        b.push(box(colx[4], y - 9, 108, 18, { fill: 'var(--ink2)', op: 0.06, stroke: 'var(--grid)', sw: 1, rx: 3 }));
        if (c.t > 0) b.push(box(colx[4], y - 9, Math.max(108 * c.t, 6), 18, { fill: 'var(--s3)', op: 0.7, stroke: 'none', sw: 0, rx: 3 }));
        b.push(txt(colx[4] + 116, y + 4, c.t === 0 ? '없음' : (c.t < 0.5 ? '아주 낮음' : '강함'), { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(W / 2, H - 32, '억압자를 떼는 것은 젖당이고, CAP 을 켜는 것은 포도당이 없다는 사실이다. 두 조건이 다 맞아야 강한 전사가 나온다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '셋째 줄을 눈여겨볼 것. CAP 이 붙어 있어도 억압자가 남아 있으면 전사는 0 이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-lac-operon',
        svg: svg({ width: W, height: H, title: 'lac 오페론의 구조와 네 가지 조합', desc: '억압자와 활성자가 직렬로 걸린 조절 논리', body: b.join('') }),
    };
})());

/* 11-2. trp 오페론의 억압과 감쇠 */
add((() => {
    const W = 700, H = 402;
    const b = [];
    b.push(txt(W / 2, 26, 'trp 오페론 — 두 층으로 조절한다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(panel(16, 46, 330, 322, '1층 · 억압', { sub: '트립토판이 많으면 억압자를 켠다' }));
    b.push(panel(354, 46, 330, 322, '2층 · 감쇠', { sub: '리보솜이 어디서 멈추는지가 정한다' }));
    const ly = 112;
    b.push(line([[36, ly + 13], [330, ly + 13]], { stroke: 'var(--ink2)', sw: 2 }));
    b.push(seg(40, ly, 56, '프로모터', { cls: 'ink2', op: 0.18 }));
    b.push(seg(98, ly, 60, '오퍼레이터', { cls: 's2', op: 0.26 }));
    b.push(seg(160, ly, 164, 'trp 유전자들', { cls: 'ink2', op: 0.10 }));
    const rep = (x, y, on) => circ(x, y, 17, { fill: on ? 'var(--s2)' : 'var(--ink2)', op: on ? 0.7 : 0.16, stroke: 'var(--ink2)', sw: 1.2 })
        + txt(x, y + 5, '억압', { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    b.push(txt(36, 200, '트립토판이 많을 때', { cls: 'ink bold', size: 'sm' }));
    b.push(rep(70, 240, true));
    b.push(txt(102, 232, '트립토판이 억압자에 붙어', { cls: 'ink2', size: 'sm' }));
    b.push(txt(102, 250, '오퍼레이터를 막는다 → 전사 없음', { cls: 'ink2', size: 'sm' }));
    b.push(txt(36, 292, '트립토판이 적을 때', { cls: 'ink bold', size: 'sm' }));
    b.push(rep(70, 328, false));
    b.push(txt(102, 322, '억압자가 떨어져 있어', { cls: 'ink2', size: 'sm' }));
    b.push(txt(102, 340, '전사가 진행된다', { cls: 'ink2', size: 'sm' }));
    const rx0 = 380, sw2 = 66;
    const drawLead = (y, pair, note, stop) => {
        const out = [];
        out.push(line([[rx0 - 8, y + 12], [rx0 + sw2 * 4 + 4, y + 12]], { stroke: 'var(--ink2)', sw: 2 }));
        for (let i = 0; i < 4; i += 1) {
            const on = pair.includes(i + 1);
            out.push(box(rx0 + sw2 * i, y, sw2 - 6, 24, { fill: on ? 'var(--s1)' : 'var(--ink2)', op: on ? 0.35 : 0.10, stroke: 'var(--ink2)', sw: 1, rx: 3 }));
            out.push(txt(rx0 + sw2 * i + (sw2 - 6) / 2, y + 17, String(i + 1), { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        const ax = rx0 + sw2 * (pair[0] - 1) + (sw2 - 6) / 2;
        const bx2 = rx0 + sw2 * (pair[1] - 1) + (sw2 - 6) / 2;
        out.push(`<path d="M${r2(ax)} ${r2(y)} Q${r2((ax + bx2) / 2)} ${r2(y - 32)} ${r2(bx2)} ${r2(y)}" fill="none" stroke="var(--s1)" stroke-width="2"/>`);
        out.push(txt((ax + bx2) / 2, y - 20, `${pair[0]}-${pair[1]} 결합`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
        out.push(txt(rx0 - 8, y + 46, note, { cls: 'ink2', size: 'sm' }));
        out.push(txt(rx0 - 8, y + 64, stop, { cls: 'ink', size: 'sm' }));
        return out.join('');
    };
    b.push(txt(rx0 - 8, 122, '트립토판이 많을 때 — 리보솜이 안 멈춘다', { cls: 'ink bold', size: 'sm' }));
    b.push(drawLead(164, [3, 4], '2번 구역이 리보솜에 덮여 3-4 머리핀이 생긴다', '→ 전사가 여기서 끊긴다'));
    b.push(txt(rx0 - 8, 260, '트립토판이 적을 때 — 리보솜이 멈춘다', { cls: 'ink bold', size: 'sm' }));
    b.push(drawLead(300, [2, 3], '2번이 드러나 2-3 결합이 먼저 생긴다', '→ 종결 머리핀이 못 생겨 계속 간다'));
    b.push(txt(W / 2, H - 10, '두 층은 서로 독립이다. 한쪽을 망가뜨려도 다른 쪽은 그대로 작동한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-trp-attenuation',
        svg: svg({ width: W, height: H, title: 'trp 오페론의 억압과 감쇠', desc: '억압자에 의한 조절 위에 리보솜 위치에 따른 조기 종결이 겹쳐 있다', body: b.join('') }),
    };
})());

/* 11-3. 크로마틴의 접힘과 접근성 */
add((() => {
    const W = 700, H = 348;
    const b = [];
    b.push(txt(W / 2, 26, '크로마틴 — 접혀 있는 정도가 곧 읽을 수 있는 정도다', { anchor: 'middle', cls: 'ink bold' }));
    const py = 50, ph = 216, pw = 208;
    const X0 = [16, 246, 476];
    b.push(panel(X0[0], py, pw, ph, '벌거벗은 DNA', { sub: '지름 약 2 nm' }));
    b.push(panel(X0[1], py, pw, ph, '뉴클레오솜 목걸이', { sub: '8량체에 약 147 염기쌍이 감긴다' }));
    b.push(panel(X0[2], py, pw, ph, '더 접힌 상태', { sub: '읽을 수 없다' }));
    b.push(line([[X0[0] + 24, 150], [X0[0] + 184, 150]], { stroke: 'var(--s1)', sw: 4 }));
    b.push(line([[X0[0] + 24, 162], [X0[0] + 184, 162]], { stroke: 'var(--s2)', sw: 4 }));
    b.push(txt(X0[0] + 104, 208, '전사인자가 자유롭게 닿는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 3; i += 1) {
        const cxk = X0[1] + 48 + i * 56;
        b.push(circ(cxk, 156, 19, { fill: 'var(--s3)', op: 0.45, stroke: 'var(--ink2)', sw: 1.2 }));
        b.push(circ(cxk, 156, 25, { fill: 'none', stroke: 'var(--s1)', sw: 3 }));
        for (let k = 0; k < 3; k += 1) b.push(line([[cxk - 12 + k * 12, 133], [cxk - 18 + k * 15, 120]], { stroke: 'var(--ink2)', sw: 1.4 }));
    }
    b.push(line([[X0[1] + 22, 156], [X0[1] + 30, 156]], { stroke: 'var(--s1)', sw: 3 }));
    b.push(line([[X0[1] + 74, 156], [X0[1] + 102, 156]], { stroke: 'var(--s1)', sw: 3 }));
    b.push(line([[X0[1] + 130, 156], [X0[1] + 158, 156]], { stroke: 'var(--s1)', sw: 3 }));
    b.push(line([[X0[1] + 186, 156], [X0[1] + 194, 156]], { stroke: 'var(--s1)', sw: 3 }));
    b.push(txt(X0[1] + 104, 110, '히스톤 꼬리 — 여기에 표지가 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(X0[1] + 104, 208, '느슨하면 아직 닿을 수 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < 12; i += 1) {
        b.push(circ(X0[2] + 48 + (i % 4) * 38, 128 + Math.floor(i / 4) * 26, 15, { fill: 'var(--ink2)', op: 0.36, stroke: 'var(--ink2)', sw: 1 }));
    }
    b.push(txt(X0[2] + 104, 208, '빽빽이 쌓이면 닿지 못한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(X0[0] + pw + 4, 156, X0[1] - 4, 156, { width: 2 }));
    b.push(arw(X0[1] + pw + 4, 156, X0[2] - 4, 156, { width: 2 }));
    b.push(txt(30, 300, '여는 수단', { cls: 'ink bold' }));
    b.push(txt(114, 300, '히스톤 꼬리의 아세틸화(양전하를 없애 결합을 약하게 한다), 리모델러가 뉴클레오솜 밀어내기', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 326, '닫는 수단', { cls: 'ink bold' }));
    b.push(txt(114, 326, '탈아세틸화, 억압과 연관된 메틸 표지, DNA 메틸화가 부르는 억압 복합체', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-nucleosome',
        svg: svg({ width: W, height: H, title: '크로마틴의 접힘 단계와 접근성', desc: '접힌 정도가 전사인자의 접근 가능성을 정한다', body: b.join('') }),
    };
})());

/* 11-4. 인핸서와 고리 */
add((() => {
    const W = 700, H = 344;
    const b = [];
    b.push(txt(W / 2, 26, '인핸서 — 멀리 떨어져 있어도 고리를 만들어 프로모터에 닿는다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(30, 66, '서열 위의 배치', { cls: 'ink bold', size: 'sm' }));
    b.push(line([[30, 100], [670, 100]], { stroke: 'var(--ink2)', sw: 2.4 }));
    b.push(seg(60, 87, 84, '인핸서', { cls: 's2', op: 0.30 }));
    b.push(seg(300, 87, 74, '프로모터', { cls: 'ink2', op: 0.18 }));
    b.push(seg(378, 87, 130, '유전자', { cls: 's1', op: 0.24 }));
    b.push(seg(566, 87, 84, '다른 유전자', { cls: 'ink2', op: 0.10 }));
    b.push(line([[538, 78], [538, 122]], { stroke: 'var(--s3)', sw: 3, dash: '4 3' }));
    b.push(txt(538, 138, '경계', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(102, 138, '수만 염기쌍 떨어져 있어도 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 182, '실제 공간에서', { cls: 'ink bold', size: 'sm' }));
    b.push('<path d="M120 254 C150 176, 262 176, 302 254" fill="none" stroke="var(--ink2)" stroke-width="2.4"/>');
    b.push(line([[302, 254], [560, 254]], { stroke: 'var(--ink2)', sw: 2.4 }));
    b.push(circ(120, 254, 18, { fill: 'var(--s2)', op: 0.45, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(txt(120, 259, '인핸서', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(circ(150, 220, 14, { fill: 'var(--s2)', op: 0.7, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(circ(176, 204, 14, { fill: 'var(--s2)', op: 0.7, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(txt(196, 196, '전사인자 (서열을 알아본다)', { cls: 'ink2', size: 'sm' }));
    b.push(ell(250, 218, 42, 22, { fill: 'var(--s3)', op: 0.35, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(txt(250, 223, '매개자', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(circ(310, 236, 20, { fill: 'var(--s1)', op: 0.5, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(txt(310, 241, 'Pol', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(arw(334, 254, 404, 254, { cls: 's1', width: 2.4 }));
    b.push(txt(412, 250, '전사 시작', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 308, '인핸서는 방향과 거리에 거의 무관하다. 대신 경계를 넘어서는 잘 작용하지 못한다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 330, '그래서 경계가 망가지면 엉뚱한 유전자가 남의 인핸서를 가져다 쓰는 일이 생긴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-enhancer-loop',
        svg: svg({ width: W, height: H, title: '인핸서와 프로모터의 고리 형성', desc: '멀리 있는 조절 서열이 고리를 통해 프로모터에 작용한다', body: b.join('') }),
    };
})());

/* 11-5. DNA 메틸화의 유지 */
add((() => {
    const W = 700, H = 340;
    const b = [];
    b.push(txt(W / 2, 26, 'CpG 가 대칭이기 때문에 메틸 표지가 복사된다', { anchor: 'middle', cls: 'ink bold' }));
    const stage = (x, y, tops, bots, title, note) => {
        const out = [];
        out.push(txt(x + 66, y - 36, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(box(x, y, 132, 26, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
        out.push(box(x, y + 34, 132, 26, { fill: 'var(--s2)', op: 0.16, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
        out.push(txt(x + 66, y + 18, '5′- C G -3′', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + 66, y + 52, '3′- G C -5′', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        if (tops) out.push(circ(x + 50, y - 10, 8, { fill: 'var(--s3)', op: 0.8, stroke: 'var(--ink2)', sw: 1 }));
        if (bots) out.push(circ(x + 82, y + 70, 8, { fill: 'var(--s3)', op: 0.8, stroke: 'var(--ink2)', sw: 1 }));
        out.push(txt(x + 66, y + 100, note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    const y0 = 120;
    b.push(stage(50, y0, true, true, '복제 전', '양쪽 가닥에 표지'));
    b.push(stage(284, y0, true, false, '복제 직후', '새 가닥에는 없다'));
    b.push(stage(518, y0, true, true, '유지 효소가 일한 뒤', '한쪽만 붙은 것을 알아보고 채운다'));
    b.push(arw(196, y0 + 30, 274, y0 + 30, { width: 2 }));
    b.push(txt(235, y0 + 20, '복제', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(430, y0 + 30, 508, y0 + 30, { width: 2 }));
    b.push(txt(469, y0 + 20, '유지 효소', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(circ(40, 292, 8, { fill: 'var(--s3)', op: 0.8, stroke: 'var(--ink2)', sw: 1 }));
    b.push(txt(56, 296, '메틸기', { cls: 'ink2', size: 'sm' }));
    b.push(txt(126, 296, 'CpG 는 반대 가닥에서도 CpG 다. 그래서 한쪽만 남아 있어도 어디에 표지가 있었는지 알 수 있다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(126, 318, '이 대칭성 하나가 발현 상태를 세포분열 너머로 전달하는 장치다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-methylation',
        svg: svg({ width: W, height: H, title: 'DNA 메틸화가 복제를 건너 유지되는 방식', desc: 'CpG 의 대칭성 덕분에 반쪽만 메틸화된 상태에서 원래 표지를 복원할 수 있다', body: b.join('') }),
    };
})());

/* 11-6. X 불활성화와 모자이크 */
add((() => {
    const W = 690, H = 344;
    const b = [];
    b.push(txt(W / 2, 26, '세포마다 무작위로 하나를 끄면 개체는 모자이크가 된다', { anchor: 'middle', cls: 'ink bold' }));
    const xchr = (x, y, cls, off) => chrom(x, y, 50, { w: 10, cls, op: off ? 0.28 : 0.9 })
        + (off ? line([[x - 13, y - 13], [x + 13, y + 13]], { stroke: 'var(--ink2)', sw: 2 }) : '');
    b.push(txt(40, 76, '발생 초기', { cls: 'ink bold', size: 'sm' }));
    b.push(cell(112, 130, 40, 36));
    b.push(xchr(99, 130, 's1', false));
    b.push(xchr(125, 130, 's2', false));
    b.push(txt(112, 184, '두 X 가 모두 켜져 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(162, 126, 208, 104, { width: 1.8 }));
    b.push(arw(162, 134, 208, 172, { width: 1.8 }));
    b.push(txt(186, 92, '무작위', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(cell(252, 104, 38, 32));
    b.push(xchr(240, 104, 's1', false));
    b.push(xchr(264, 104, 's2', true));
    b.push(cell(252, 176, 38, 32));
    b.push(xchr(240, 176, 's1', true));
    b.push(xchr(264, 176, 's2', false));
    b.push(txt(296, 100, '이쪽 X 만 쓴다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(296, 118, '(선택은 자손 세포에 그대로 이어진다)', { cls: 'ink2', size: 'sm' }));
    b.push(txt(296, 180, '저쪽 X 만 쓴다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(536, 76, '자라난 뒤의 표면', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const pat = [1, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1];
    pat.forEach((k, idx) => {
        const i = idx % 4, j = Math.floor(idx / 4);
        b.push(box(452 + i * 42, 92 + j * 34, 40, 32, { fill: k ? 'var(--s1)' : 'var(--s2)', op: 0.55, stroke: 'var(--grid)', sw: 1, rx: 3 }));
    });
    b.push(txt(536, 250, '한 세포에서 자란 무리가 한 조각이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 298, '선택이 무작위이고 그 뒤의 세포 이동과 증식도 우연에 좌우되므로, 유전자형이 같아도 무늬는 개체마다 다르다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 320, '삼색 고양이가 거의 모두 암컷인 것과 복제 고양이의 무늬가 원본과 다른 것이 같은 이유에서 나온다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-x-inactivation',
        svg: svg({ width: W, height: H, title: 'X 염색체 불활성화와 모자이크', desc: '세포마다 독립적인 무작위 선택이 조각 무늬를 만든다', body: b.join('') }),
    };
})());

/* 11-7. miRNA 와 siRNA */
add((() => {
    const W = 690, H = 318;
    const b = [];
    b.push(txt(W / 2, 26, '짧은 RNA 가 표적을 고르고, 얼마나 잘 맞는지가 결과를 정한다', { anchor: 'middle', cls: 'ink bold' }));
    const one = (x, title, sub, full) => {
        const out = [];
        out.push(panel(x, 50, 322, 204, title, { sub }));
        const mx = x + 44, cw = 15;
        out.push(line([[mx - 18, 148], [mx + cw * 16 + 18, 148]], { stroke: 'var(--ink2)', sw: 3 }));
        out.push(box(mx, 126, cw * 16, 22, { fill: 'var(--s1)', op: 0.16, stroke: 'var(--ink2)', sw: 1, rx: 3 }));
        out.push(txt(mx + cw * 8, 118, 'mRNA 의 3′ 쪽 구간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(box(mx, 156, cw * 16, 22, { fill: 'var(--s2)', op: 0.28, stroke: 'var(--ink2)', sw: 1, rx: 3 }));
        out.push(txt(mx + cw * 8, 171, full ? '완전히 들어맞는다' : '앞쪽 일곱 글자만 맞는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        for (let i = 0; i < 16; i += 1) {
            const ok = full || i < 7;
            out.push(line([[mx + cw * i + cw / 2, 148], [mx + cw * i + cw / 2, 156]], { stroke: ok ? 'var(--s3)' : 'var(--grid)', sw: ok ? 2 : 1 }));
        }
        out.push(ell(mx + cw * 8, 202, 68, 17, { fill: 'var(--s3)', op: 0.30, stroke: 'var(--ink2)', sw: 1.2 }));
        out.push(txt(mx + cw * 8, 207, '단백질 복합체', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(mx + cw * 8, 240, full ? '표적을 잘라 버린다' : '번역을 막고 분해를 재촉한다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        return out.join('');
    };
    b.push(one(16, 'miRNA', '부분적으로만 맞는다', false));
    b.push(one(354, 'siRNA', '완전히 맞는다', true));
    b.push(txt(30, 282, '맞는 자리가 짧다는 것이 miRNA 의 힘이자 문제다. 한 miRNA 가 수백 개의 mRNA 를 건드릴 수 있는 대신,', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 304, '아무 기능이 없어도 우연히 맞는 자리가 유전체 안에 아주 많이 생긴다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-small-rna',
        svg: svg({ width: W, height: H, title: 'miRNA 와 siRNA 의 작용 차이', desc: '짝짓기가 얼마나 완전한가에 따라 번역 억제와 절단으로 갈린다', body: b.join('') }),
    };
})());

/* ================================================================== *
 * 12장 — 생명공학
 * ================================================================== */

/* 12-1. PCR 의 세 온도와 지수 증폭 */
add((() => {
    const W = 700, H = 372;
    const b = [];
    b.push(txt(W / 2, 26, 'PCR — 세 온도를 되풀이하면 사본이 두 배씩 는다', { anchor: 'middle', cls: 'ink bold' }));
    const T2Y = t => 258 - (t - 45) * 2.7;
    const x0 = 70;
    b.push(line([[x0 - 8, T2Y(45)], [640, T2Y(45)]], { stroke: 'var(--ink2)', sw: 1.4 }));
    b.push(line([[x0 - 8, T2Y(45)], [x0 - 8, T2Y(104)]], { stroke: 'var(--ink2)', sw: 1.4 }));
    for (const t of [50, 60, 70, 80, 90, 100]) {
        b.push(line([[x0 - 12, T2Y(t)], [x0 - 8, T2Y(t)]], { stroke: 'var(--ink2)', sw: 1 }));
        b.push(txt(x0 - 16, T2Y(t) + 4, String(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(x0 - 44, T2Y(104) - 4, '온도 (°C)', { cls: 'ink2', size: 'sm' }));
    const cycleW = 168;
    const pts = [[x0, T2Y(95)]];
    for (let c = 0; c < 3; c += 1) {
        const s = x0 + c * cycleW;
        pts.push([s + 34, T2Y(95)], [s + 46, T2Y(58)], [s + 90, T2Y(58)], [s + 102, T2Y(72)], [s + 148, T2Y(72)], [s + 158, T2Y(95)], [s + 168, T2Y(95)]);
    }
    b.push(line(pts, { stroke: 'var(--s1)', sw: 2.6 }));
    b.push(txt(x0 + 6, T2Y(95) - 10, '변성 95', { cls: 'ink', size: 'sm' }));
    b.push(txt(x0 + 68, T2Y(58) + 18, '결합 58', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(x0 + 124, T2Y(72) - 10, '신장 72', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(x0 + 190, T2Y(95) - 24, '두 가닥이 떨어진다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(x0 + 236, T2Y(58) + 18, '프라이머가 붙는다 — 이 온도가 특이성을 정한다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(x0 + 296, T2Y(72) - 10, '내열성 중합효소가 늘린다', { cls: 'ink2', size: 'sm' }));
    for (let c = 1; c < 3; c += 1) b.push(line([[x0 + c * cycleW, T2Y(45)], [x0 + c * cycleW, T2Y(100)]], { stroke: 'var(--grid)', sw: 1, dash: '4 3' }));
    b.push(txt(x0 + cycleW / 2, 288, '1순환', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(x0 + cycleW * 1.5, 288, '2순환', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(x0 + cycleW * 2.5, 288, '3순환', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const cy = 328;
    b.push(txt(24, cy - 22, '사본 수', { cls: 'ink bold', size: 'sm' }));
    ['1', '2', '4', '8'].forEach((v, i) => {
        b.push(box(x0 + i * cycleW - 18, cy - 14, 36, 22, { fill: 'var(--s3)', op: 0.35, stroke: 'var(--ink2)', sw: 1, rx: 4 }));
        b.push(txt(x0 + i * cycleW, cy + 2, v, { anchor: 'middle', cls: 'ink bold' }));
        if (i < 3) b.push(arw(x0 + i * cycleW + 22, cy - 3, x0 + (i + 1) * cycleW - 22, cy - 3, { width: 1.6 }));
    });
    b.push(txt(W / 2, H - 8, '앞 순환의 산물이 다음 순환의 주형이 되므로 사본이 두 배씩 늘어난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-pcr-cycles',
        svg: svg({ width: W, height: H, title: 'PCR 의 온도 순환과 사본 수', desc: '세 온도를 되풀이하면 표적 구간이 지수적으로 늘어난다', body: b.join('') }),
    };
})());

/* 12-2. qPCR 의 증폭 곡선과 Ct */
add((() => {
    const W = 680, H = 396;
    const b = [];
    b.push(txt(W / 2, 26, '실시간 정량 PCR — 끝난 뒤의 양이 아니라 언제 문턱을 넘는지를 본다', { anchor: 'middle', cls: 'ink bold' }));
    const F = frame({ xRange: [0, 40], yRange: [0, 1.15], box: { x: 66, y: 56, w: 540, h: 232 } });
    b.push(F.axes({ xLabel: '순환 수', yLabel: '', xTicks: [0, 5, 10, 15, 20, 25, 30, 35, 40], yTicks: [0, 0.5, 1], grid: true }));
    b.push(txt(56, 52, '형광', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const sig = n0 => x => 1.0 / (1 + Math.exp(-(x - n0) / 1.6));
    const sets = [
        { n0: 20.0, cls: 's1', ct: 16.5 },
        { n0: 23.3, cls: 's2', ct: 19.8 },
        { n0: 26.6, cls: 's3', ct: 23.1 },
    ];
    const thr = 0.1;
    for (const s of sets) b.push(F.curve(sig(s.n0), { from: 0, to: 40, cls: s.cls }));
    b.push(line([[F.X(0), F.Y(thr)], [F.X(40), F.Y(thr)]], { stroke: 'var(--ink)', sw: 1.6, dash: '6 4' }));
    b.push(txt(F.X(40) + 8, F.Y(thr) + 4, '문턱', { cls: 'ink', size: 'sm' }));
    for (const s of sets) {
        b.push(line([[F.X(s.ct), F.Y(0)], [F.X(s.ct), F.Y(thr)]], { stroke: `var(--${s.cls})`, sw: 1.4, dash: '4 3' }));
        b.push(txt(F.X(s.ct), F.Y(0) + 32, 'C~t', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    b.push(legend(96, 84, [{ slot: 1, name: '처음 주형 100배' }, { slot: 2, name: '10배' }, { slot: 3, name: '1배' }]));
    b.push(txt(F.X(24), F.Y(1.02), '정체기 — 여기서는 처음 양을 알 수 없다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(F.X(2), F.Y(0.34), '지수 구간에서만', { cls: 'ink2', size: 'sm' }));
    b.push(txt(F.X(2), F.Y(0.25), '정량이 가능하다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 54, '세 곡선의 최종 높이는 거의 같다. 다른 것은 문턱을 넘는 순환 수뿐이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 34, '처음 주형이 10배면 문턱을 약 3.3 순환 먼저 넘는다. 2 를 3.3 번 곱해야 10 이 되기 때문이다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 14, '두 배면 정확히 1 순환 빠르다. 이것이 문턱 순환수로 처음 양을 되짚는 근거다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-qpcr-ct',
        svg: svg({ width: W, height: H, title: 'qPCR 증폭 곡선과 임계 순환수', desc: '처음 주형량이 많을수록 문턱을 일찍 넘는다', body: b.join('') }),
    };
})());

/* 12-3. 전기영동과 제한효소 절단 지도 */
add((() => {
    const W = 690, H = 402;
    const b = [];
    b.push(txt(W / 2, 26, '전기영동 — 작은 조각일수록 멀리 간다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(W / 2, 46, '같은 시료를 세 가지로 잘라 밴드를 견주면 자른 자리를 되짚을 수 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const gx = 76, gy = 78, gw = 300, gh = 240;
    b.push(box(gx, gy, gw, gh, { fill: 'var(--ink2)', op: 0.05, stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    const Y = kb => gy + 22 + (Math.log10(9) - Math.log10(kb)) / (Math.log10(9) - Math.log10(0.8)) * (gh - 46);
    const lanes = [
        { x: gx + 42, name: '크기 표지', bands: [8, 6, 5, 3, 2, 1], lab: true },
        { x: gx + 122, name: '효소 A', bands: [5, 3] },
        { x: gx + 194, name: '효소 B', bands: [6, 2] },
        { x: gx + 266, name: 'A + B', bands: [5, 2, 1] },
    ];
    for (const ln of lanes) {
        b.push(txt(ln.x, gy - 8, ln.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        b.push(box(ln.x - 24, gy + 8, 48, 8, { fill: 'var(--ink2)', op: 0.20, stroke: 'none', sw: 0, rx: 2 }));
        for (const s of ln.bands) {
            b.push(box(ln.x - 24, Y(s) - 4, 48, 8, { fill: ln.lab ? 'var(--ink2)' : 'var(--s1)', op: ln.lab ? 0.35 : 0.7, stroke: 'none', sw: 0, rx: 2 }));
            if (ln.lab) b.push(txt(ln.x - 30, Y(s) + 4, `${s} kb`, { anchor: 'end', cls: 'ink2', size: 'sm' }));
        }
    }
    b.push(arw(gx + gw + 12, gy + 30, gx + gw + 12, gy + gh - 16, { width: 1.6 }));
    b.push(txt(gx + gw + 20, gy + 84, '작을수록', { cls: 'ink2', size: 'sm' }));
    b.push(txt(gx + gw + 20, gy + 100, '멀리 간다', { cls: 'ink2', size: 'sm' }));
    const mx = 470, my = 214;
    b.push(txt(mx, 116, '되짚은 절단 지도', { cls: 'ink bold' }));
    b.push(txt(mx, 140, '어느 소화에서도 합이 8.0 kb 다', { cls: 'ink2', size: 'sm' }));
    b.push(box(mx, my - 10, 190, 20, { fill: 'var(--ink2)', op: 0.10, stroke: 'var(--ink2)', sw: 1.2, rx: 10 }));
    b.push(line([[mx + 47, my - 16], [mx + 47, my + 16]], { stroke: 'var(--s2)', sw: 2.6 }));
    b.push(line([[mx + 71, my - 16], [mx + 71, my + 16]], { stroke: 'var(--s3)', sw: 2.6 }));
    b.push(txt(mx + 47, my - 22, 'B', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(mx + 71, my - 40, 'A', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(mx, my + 34, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(mx + 47, my + 34, '2.0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(mx + 71, my + 52, '3.0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(mx + 190, my + 34, '8.0 kb', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(mx, my + 86, '좌우를 뒤집은 지도도 같은 밴드를 낸다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(mx, my + 104, '방향은 이 실험만으로 정할 수 없다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '이동 거리는 크기의 로그에 대체로 비례한다. 그래서 큰 조각끼리는 잘 안 갈리고 작은 조각끼리는 잘 갈린다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-gel',
        svg: svg({ width: W, height: H, title: '제한효소 소화의 전기영동과 절단 지도', desc: '단일 소화와 이중 소화의 밴드를 견주어 자른 자리를 정한다', body: b.join('') }),
    };
})());

/* 12-4. 생어 시퀀싱 */
add((() => {
    const W = 700, H = 392;
    const b = [];
    b.push(txt(W / 2, 26, '생어 시퀀싱 — 길이가 하나씩 다른 조각을 만들어 크기순으로 읽는다', { anchor: 'middle', cls: 'ink bold' }));
    b.push(panel(16, 48, 372, 302, '사슬 종결', { sub: '3′ 끝이 막힌 뉴클레오타이드가 붙으면 거기서 멈춘다' }));
    const seq = 'ACGTTGCA';
    const sx = 58, cw = 33, ty = 118;
    b.push(txt(50, ty + 4, '주형', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    for (let i = 0; i < seq.length; i += 1) {
        b.push(box(sx + cw * i, ty - 12, cw - 4, 24, { fill: 'var(--ink2)', op: 0.08, stroke: 'var(--grid)', sw: 1, rx: 3 }));
        b.push(txt(sx + cw * i + (cw - 4) / 2, ty + 5, seq[i], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    }
    const comp = { A: 'T', C: 'G', G: 'C', T: 'A' };
    for (let k = 0; k < 5; k += 1) {
        const y = ty + 44 + k * 34;
        for (let i = 0; i <= k + 2; i += 1) {
            const last = i === k + 2;
            b.push(box(sx + cw * i, y - 10, cw - 4, 20, { fill: last ? 'var(--s2)' : 'var(--s1)', op: last ? 0.8 : 0.35, stroke: 'var(--grid)', sw: 1, rx: 3 }));
            b.push(txt(sx + cw * i + (cw - 4) / 2, y + 5, comp[seq[i]], { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        b.push(txt(sx + cw * (k + 3) + 6, y + 5, `${k + 3} 글자`, { cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(50, ty + 48, '조각', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(28, 336, '주황색이 멈춤을 일으킨 뉴클레오타이드다', { cls: 'ink2', size: 'sm' }));
    b.push(panel(396, 48, 288, 302, '크기순으로 늘어놓으면', { sub: '아래에서 위로 읽는다' }));
    const gx2 = 476, gy2 = 100, gh2 = 216;
    b.push(box(gx2 - 32, gy2, 64, gh2, { fill: 'var(--ink2)', op: 0.05, stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    ['T', 'G', 'C', 'A', 'A', 'C', 'G', 'T'].forEach((base, i) => {
        const y = gy2 + gh2 - 16 - i * 25;
        b.push(box(gx2 - 26, y - 5, 52, 10, { fill: 'var(--s1)', op: 0.7, stroke: 'none', sw: 0, rx: 2 }));
        b.push(txt(gx2 + 40, y + 4, base, { cls: 'ink bold' }));
        b.push(txt(gx2 + 58, y + 4, `${i + 1}번째`, { cls: 'ink2', size: 'sm' }));
    });
    b.push(arw(gx2 - 46, gy2 + 24, gx2 - 46, gy2 + gh2 - 10, { width: 1.6 }));
    b.push(txt(gx2 - 52, gy2 + 120, '작다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(408, 336, '가장 짧은 조각이 첫 글자다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 12, '길이 n 과 n+1 을 구별해야 하므로, 길어질수록 상대적 차이가 작아져 읽기 길이에 한계가 생긴다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-sanger',
        svg: svg({ width: W, height: H, title: '생어 시퀀싱의 사슬 종결 원리', desc: '길이가 하나씩 다른 조각을 크기순으로 분리해 서열을 읽는다', body: b.join('') }),
    };
})());

/* 12-5. 시퀀싱 깊이 */
add((() => {
    const W = 690, H = 342;
    const b = [];
    b.push(txt(W / 2, 26, '깊이 — 같은 자리를 몇 번 읽었는가', { anchor: 'middle', cls: 'ink bold' }));
    const gx = 56, gw = 566, base = 272;
    b.push(box(gx, base, gw, 20, { fill: 'var(--ink2)', op: 0.14, stroke: 'var(--ink2)', sw: 1.2, rx: 4 }));
    b.push(txt(gx + gw / 2, base + 14, '유전체 (참조 서열)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    const starts = [0, 25, 40, 60, 72, 96, 130, 150, 168, 210, 250, 262, 288, 300, 316, 330, 340, 350, 380, 396, 420, 452, 470, 496];
    const rowY = [250, 230, 210, 190, 170, 150];
    const used = rowY.map(() => []);
    for (const s of starts) {
        const w = 66;
        let row = 0;
        while (row < rowY.length && used[row].some(([a, e]) => s < e + 8 && a < s + w + 8)) row += 1;
        if (row >= rowY.length) continue;
        used[row].push([s, s + w]);
        b.push(box(gx + s, rowY[row] - 6, w, 12, { fill: 'var(--s1)', op: 0.55, stroke: 'var(--grid)', sw: 1, rx: 3 }));
    }
    b.push(line([[gx + 336, 138], [gx + 336, base]], { stroke: 'var(--s2)', sw: 2, dash: '5 4' }));
    b.push(txt(gx + 342, 134, '이 자리는 여러 번 읽혔다', { cls: 'ink', size: 'sm' }));
    b.push(line([[gx + 552, 116], [gx + 552, base]], { stroke: 'var(--s3)', sw: 2, dash: '5 4' }));
    b.push(txt(gx + 546, 112, '이 자리는 한 번도 안 읽혔다', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(34, 312, '평균 깊이는 읽기 수 × 읽기 길이 ÷ 유전체 크기다. 읽기가 고르게 흩어진다면 한 번도 안 읽히는 자리의 비율은', { cls: 'ink2', size: 'sm' }));
    b.push(txt(34, 332, '깊이가 커질수록 급격히 줄어든다. 다만 실제 읽기는 고르게 흩어지지 않으므로 이 계산은 늘 낙관적인 하한이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-sequencing-depth',
        svg: svg({ width: W, height: H, title: '시퀀싱 깊이와 빈 자리', desc: '짧은 읽기가 겹쳐 쌓이면서 각 위치의 깊이가 정해진다', body: b.join('') }),
    };
})());

/* 12-6. CRISPR-Cas9 */
add((() => {
    const W = 700, H = 424;
    const b = [];
    b.push(txt(W / 2, 26, 'CRISPR-Cas9 — 자르는 데까지가 이 도구의 일이다', { anchor: 'middle', cls: 'ink bold' }));
    const dx = 130, cw = 21, n = 20;
    const y1 = 122, y2 = 150;
    b.push(box(dx, y1 - 12, cw * n, 24, { fill: 'var(--s1)', op: 0.14, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
    b.push(box(dx, y2 - 12, cw * n, 24, { fill: 'var(--s2)', op: 0.14, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
    b.push(txt(dx + cw * 7.5, y1 + 5, '표적 서열 20 글자', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(box(dx + cw * 15, y1 - 12, cw * 3, 24, { fill: 'var(--s3)', op: 0.45, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
    b.push(txt(dx + cw * 16.5, y1 + 5, 'PAM', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(dx + cw * 18.5, y1 - 20, 'Cas9 이 확인하는 짧은 서열', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(dx - 10, y1 + 5, '5′', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(dx - 10, y2 + 5, '3′', { anchor: 'end', cls: 'ink bold' }));
    b.push(box(dx, 74, cw * 15, 22, { fill: 'var(--s2)', op: 0.35, stroke: 'var(--ink2)', sw: 1.1, rx: 3 }));
    b.push(txt(dx + cw * 7.5, 90, '안내 RNA — 이 20 글자가 표적을 고른다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    for (let i = 0; i < 15; i += 1) b.push(line([[dx + cw * i + cw / 2, 96], [dx + cw * i + cw / 2, y1 - 12]], { stroke: 'var(--s3)', sw: 1.6 }));
    const cutX = dx + cw * 12;
    b.push(line([[cutX, y1 - 24], [cutX, y2 + 24]], { stroke: 'var(--ink)', sw: 2.4, dash: '5 3' }));
    b.push(txt(cutX + 10, y2 + 42, 'PAM 에서 세 글자 앞을 자른다', { cls: 'ink', size: 'sm' }));
    b.push(ell(dx + cw * 3.5, 192, 72, 19, { fill: 'var(--ink2)', op: 0.16, stroke: 'var(--ink2)', sw: 1.2 }));
    b.push(txt(dx + cw * 3.5, 197, 'Cas9 (가위)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(line([[30, 222], [670, 222]], { stroke: 'var(--grid)', sw: 1 }));
    b.push(arw(310, 228, 200, 262, { width: 2 }));
    b.push(arw(390, 228, 500, 262, { width: 2 }));
    b.push(panel(30, 268, 300, 130, '주형이 없으면', { sub: '비상동 말단 연결' }));
    b.push(panel(370, 268, 300, 130, '공여 DNA 를 함께 넣으면', { sub: '상동 재조합 수선' }));
    b.push(txt(46, 334, '작은 삽입이나 결실이 남는다', { cls: 'ink', size: 'sm' }));
    b.push(txt(46, 354, '대개 틀 이동 → 기능 상실', { cls: 'ink', size: 'sm' }));
    b.push(txt(46, 378, '쉽고 효율이 높다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(386, 334, '지정한 서열로 정확히 바뀐다', { cls: 'ink', size: 'sm' }));
    b.push(txt(386, 354, '점변이 도입, 표지 삽입', { cls: 'ink', size: 'sm' }));
    b.push(txt(386, 378, '효율이 낮고 세포 주기를 탄다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(W / 2, H - 8, '자른 다음에 무엇이 되는지는 세포의 수선 경로가 정한다. 그래서 망가뜨리기는 쉽고 원하는 대로 바꾸기는 어렵다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-gen-crispr',
        svg: svg({ width: W, height: H, title: 'CRISPR-Cas9 의 표적 인식과 절단 뒤의 두 갈래', desc: '안내 RNA 가 자리를 정하고 수선 경로가 결과를 정한다', body: b.join('') }),
    };
})());

export default figures;
