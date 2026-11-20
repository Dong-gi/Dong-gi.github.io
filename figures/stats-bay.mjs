/**
 * 통계학 9장(베이즈 추론)의 그림. 이름은 전부 `sta-bay-` 로 시작한다.
 *
 * 함정(statistics-infer.mjs 머리 주석과 같다):
 *   라벨에 수식·HTML 엔티티를 쓸 수 없다. 첨자는 lib 의 `x~0` 표기, 그리스 문자는 유니코드.
 *   그래서 라벨에 물결표를 그냥 쓰면 아래첨자로 먹힌다 — 범위는 ‘에서’ 나 ‘–’ 로 적는다.
 *   lib 의 px() 에 cls 'ark' 를 주면 선이 사라지므로 색을 직접 넣는 arw() 를 둔다.
 *   그림 안에 한자를 쓰지 않는다.
 *
 * 그림 속 수치(사후평균·구간·베이즈 인자)는 모두 이 파일이 그 자리에서 계산한다.
 * 난수는 신뢰구간 칸의 반복 표본에만 쓰고, 씨앗을 고정한 선형합동생성기로 뽑는다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));
const fx = (v, d = 2) => v.toFixed(d);

/* ------------------------------------------------------------------ *
 * 수치 도구
 * ------------------------------------------------------------------ */

/** 로그 감마(Lanczos 근사, g=7). 이 그림들의 정밀도에는 넉넉하다. */
function lgamma(z) {
    const c = [0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
        1.5056327351493116e-7];
    if (z < 0.5) return Math.log(Math.PI / Math.sin(Math.PI * z)) - lgamma(1 - z);
    z -= 1;
    let x = c[0];
    for (let i = 1; i < 9; i += 1) x += c[i] / (z + i);
    const t = z + 7.5;
    return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
}
const lbeta = (a, b) => lgamma(a) + lgamma(b) - lgamma(a + b);
const betaPdf = (a, b) => x => (x <= 0 || x >= 1 ? 0
    : Math.exp((a - 1) * Math.log(x) + (b - 1) * Math.log(1 - x) - lbeta(a, b)));
const normPdf = (m, s) => x => Math.exp(-((x - m) ** 2) / (2 * s * s)) / (Math.sqrt(2 * Math.PI) * s);

/** 씨앗 고정 선형합동생성기와 박스-뮬러 정규난수. */
function lcg(seed) {
    let s = seed >>> 0;
    return () => {
        s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
        return (s + 0.5) / 4294967296;
    };
}
function normals(rand) {
    return () => Math.sqrt(-2 * Math.log(rand())) * Math.cos(2 * Math.PI * rand());
}

/* ------------------------------------------------------------------ *
 * 그리기 소도구
 * ------------------------------------------------------------------ */

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink: 'var(--ink)', ink2: 'var(--ink2)' };

function ln(pts, { stroke = 'var(--ink2)', sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function panel(x, y, w, h, title, sub) {
    return box(x, y, w, h, { stroke: 'var(--grid)', sw: 1, rx: 6 })
        + txt(x + w / 2, y + 20, title, { anchor: 'middle', cls: 'ink bold' })
        + (sub ? txt(x + w / 2, y + 37, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }) : '');
}

/** 데이터 좌표 곡선. 색을 직접 넣어 점선·실선을 고른다. */
function crv(fr, f, from, to, { col = 's1', dash, sw = 2.2, steps = 220 } = {}) {
    const pts = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    return ln(pts, { stroke: COL[col], sw, dash });
}

function area(fr, f, from, to, col, op = 0.22, steps = 120) {
    const pts = [[fr.X(from), fr.Y(0)]];
    for (let i = 0; i <= steps; i += 1) {
        const x = from + ((to - from) * i) / steps;
        pts.push([fr.X(x), fr.Y(f(x))]);
    }
    pts.push([fr.X(to), fr.Y(0)]);
    return `<path d="M${pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="${COL[col]}" fill-opacity="${op}" stroke="none"/>`;
}

/** 가로축 + 눈금. */
function xaxis(fr, x0, x1, ticks, { y0 = 0, fmt = String } = {}) {
    const y = fr.Y(y0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${fr.X(x0)} ${y} H${r2(fr.X(x1) + 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[fr.X(t), y], [fr.X(t), y + 5]], { sw: 1 }));
        out.push(txt(fr.X(t), y + 18, fmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 세로축 + 눈금(왼쪽 가장자리). */
function yaxis(fr, x0, y0, y1, ticks, { fmt = String } = {}) {
    const x = fr.X(x0);
    const out = [`<path class="ax" marker-end="url(#ark)" d="M${x} ${fr.Y(y0)} V${r2(fr.Y(y1) - 10)}"/>`];
    for (const t of ticks) {
        out.push(ln([[x - 5, fr.Y(t)], [x, fr.Y(t)]], { sw: 1 }));
        out.push(txt(x - 8, fr.Y(t) + 4, fmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    }
    return out.join('');
}

/** 범례 한 줄 — 선 모양(실선·점선)까지 함께 보여 준다. */
function key(x, y, items) {
    return items.map((it, i) => {
        const yy = y + i * 19;
        return ln([[x, yy - 4], [x + 26, yy - 4]], { stroke: COL[it.col], sw: 2.4, dash: it.dash })
            + txt(x + 33, yy, it.name, { cls: 'ink2', size: 'sm' });
    }).join('');
}

/* ------------------------------------------------------------------ *
 * sta-bay-update — 같은 자료에 사전 셋, 자료가 쌓이면 사후가 모인다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900, H = 330;
    const PRI = [
        { a: 1, b: 1, col: 's1', name: '평평한 사전 Beta(1, 1)' },
        { a: 8, b: 2, col: 's2', dash: '8 5', name: '높게 본 사전 Beta(8, 2)' },
        { a: 2, b: 8, col: 's3', dash: '2 4', name: '낮게 본 사전 Beta(2, 8)' },
    ];
    const stages = [
        { n: 0, x: 0, title: '자료를 보기 전', yMax: 4 },
        { n: 10, x: 6, title: '10번 중 6번 성공 뒤', yMax: 4 },
        { n: 100, x: 60, title: '100번 중 60번 성공 뒤', yMax: 10 },
    ];
    const out = [];
    const pw = 270, gap = 15, top = 20, ph = 260;
    stages.forEach((st, k) => {
        const px0 = 15 + k * (pw + gap);
        out.push(panel(px0, top, pw, ph, st.title, st.n ? `사후분포 셋 — 자료 ${st.n}개` : '사전분포 셋'));
        const fr = frame({ xRange: [0, 1], yRange: [0, st.yMax], box: { x: px0 + 38, y: top + 58, w: pw - 62, h: ph - 100 } });
        out.push(xaxis(fr, 0, 1, [0, 0.2, 0.4, 0.6, 0.8, 1]));
        const yt = st.yMax === 4 ? [0, 1, 2, 3, 4] : [0, 2, 4, 6, 8, 10];
        out.push(yaxis(fr, 0, 0, st.yMax, yt));
        if (st.n) out.push(ln([[fr.X(0.6), fr.Y(0)], [fr.X(0.6), fr.Y(st.yMax)]], { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }));
        for (const p of PRI) {
            const a = p.a + st.x, b = p.b + st.n - st.x;
            out.push(crv(fr, betaPdf(a, b), 0.002, 0.998, { col: p.col, dash: p.dash }));
        }
        if (st.n) out.push(txt(px0 + pw / 2, fr.Y(0) + 36, '세로 점선: 관측 비율 0.6', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        out.push(txt(fr.X(1) + 14, fr.Y(0) + 4, 'θ', { cls: 'ink2' }));
    });
    out.push(key(30, top + ph + 30, [PRI[0]]));
    out.push(key(320, top + ph + 30, [PRI[1]]));
    out.push(key(610, top + ph + 30, [PRI[2]]));
    return {
        name: 'sta-bay-update',
        svg: svg({ width: W, height: H, title: '같은 자료에 사전분포 셋', desc: '자료가 쌓일수록 서로 다른 사전에서 출발한 사후분포가 관측 비율 근처로 모인다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-bay-shrink — 정규-정규에서 사후평균이 사전평균과 자료 평균 사이
 * ------------------------------------------------------------------ */
add((() => {
    const W = 720, H = 340;
    const mu0 = 500, v0 = 4, sig = 4, xb = 506;
    const prec = n => 1 / v0 + n / (sig * sig);
    const pm = n => (mu0 / v0 + (n * xb) / (sig * sig)) / prec(n);
    const half = n => 1.96 / Math.sqrt(prec(n));
    const fr = frame({ xRange: [0, 60], yRange: [495, 510], box: { x: 70, y: 40, w: 560, h: 240 } });
    const out = [];
    for (const t of [496, 498, 500, 502, 504, 506, 508, 510]) out.push(ln([[fr.X(0), fr.Y(t)], [fr.X(60), fr.Y(t)]], { stroke: 'var(--grid)', sw: 1 }));
    // 95% 신용구간 띠
    const up = [], dn = [];
    for (let i = 0; i <= 120; i += 1) {
        const n = (60 * i) / 120;
        up.push([fr.X(n), fr.Y(pm(n) + half(n))]);
        dn.push([fr.X(n), fr.Y(pm(n) - half(n))]);
    }
    out.push(`<path d="M${up.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} L${dn.reverse().map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L')} Z" fill="var(--s1)" fill-opacity="0.15" stroke="none"/>`);
    out.push(ln([[fr.X(0), fr.Y(mu0)], [fr.X(60), fr.Y(mu0)]], { stroke: COL.s2, sw: 2, dash: '7 5' }));
    out.push(ln([[fr.X(0), fr.Y(xb)], [fr.X(60), fr.Y(xb)]], { stroke: COL.s3, sw: 2, dash: '2 4' }));
    out.push(crv(fr, pm, 0, 60, { col: 's1', sw: 2.6 }));
    out.push(xaxis(fr, 0, 60, [0, 4, 10, 20, 30, 40, 50, 60], { y0: 495 }));
    out.push(yaxis(fr, 0, 495, 510, [496, 498, 500, 502, 504, 506, 508, 510]));
    out.push(txt(fr.X(60) + 14, fr.Y(495) + 4, 'n', { cls: 'ink2' }));
    out.push(txt(fr.X(0) - 8, fr.Y(510) - 14, 'μ (g)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // n=4 점
    out.push(`<circle cx="${fr.X(4)}" cy="${fr.Y(pm(4))}" r="4.5" fill="var(--s1)"/>`);
    out.push(txt(fr.X(4) + 8, fr.Y(pm(4)) + 18, `n = 4 에서 사후평균 ${fx(pm(4), 1)}`, { cls: 'ink', size: 'sm' }));
    out.push(txt(fr.X(60) + 6, fr.Y(mu0) + 4, '사전평균 μ~0 = 500', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(60) + 6, fr.Y(xb) + 4, '자료 평균 x̄ = 506', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(22), fr.Y(pm(22)) + 20, '사후평균', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    out.push(txt(fr.X(48), fr.Y(pm(48) - half(48)) + 16, '연한 띠: 95% 신용구간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(30), H - 12, `사전 N(500, 2²), σ = 4, 자료 평균을 506 에 고정하고 표본 크기 n 만 바꾼다. n = 60 에서 사후평균 ${fx(pm(60), 2)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-bay-shrink',
        svg: svg({ width: W + 90, height: H, title: '정규-정규 모형의 축소', desc: '사후평균은 사전평균 500 과 자료 평균 506 사이에 있고 n 이 클수록 자료 평균 쪽으로 간다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-bay-cred-conf — 같은 수치의 두 구간, 붙는 확률 진술이 다르다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 960, H = 430;
    const se = 2, xb = 506, z = 1.96;
    const lo = xb - z * se, hi = xb + z * se;
    const out = [];
    // 신뢰구간 칸
    out.push(panel(15, 15, 300, 400, '신뢰구간 칸', '참값 μ 는 고정, 구간이 흔들린다'));
    const muTrue = 504;
    const rand = normals(lcg(20261120));
    const fr1 = frame({ xRange: [494, 514], yRange: [0, 21], box: { x: 35, y: 62, w: 260, h: 280 } });
    out.push(ln([[fr1.X(muTrue), fr1.Y(0)], [fr1.X(muTrue), fr1.Y(21)]], { stroke: 'var(--ink2)', sw: 1.6, dash: '5 4' }));
    let miss = 0;
    for (let i = 1; i <= 19; i += 1) {
        const m = muTrue + se * rand();
        const ok = m - z * se <= muTrue && muTrue <= m + z * se;
        if (!ok) miss += 1;
        out.push(ln([[fr1.X(m - z * se), fr1.Y(i)], [fr1.X(m + z * se), fr1.Y(i)]], { stroke: ok ? COL.s1 : COL.s2, sw: 2.2, dash: ok ? undefined : '4 3' }));
    }
    // 이번 자료의 구간(맨 위)
    out.push(ln([[fr1.X(lo), fr1.Y(20.3)], [fr1.X(hi), fr1.Y(20.3)]], { stroke: COL.ink, sw: 3.4 }));
    out.push(txt(fr1.X(hi) + 4, fr1.Y(20.3) + 4, '이번 자료', { cls: 'ink bold', size: 'sm' }));
    out.push(xaxis(fr1, 494, 514, [496, 500, 504, 508, 512]));
    out.push(txt(fr1.X(muTrue), fr1.Y(0) + 34, `세로 점선: 모르는 참값 μ (여기서는 ${muTrue})`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    out.push(txt(fr1.X(muTrue), fr1.Y(0) + 50, `같은 방법의 다른 표본 19번 중 놓친 것 ${miss}개(점선)`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 진술 칸
    out.push(panel(330, 15, 300, 400, '진술 칸', `두 구간의 수치는 같다: ${fx(lo)}–${fx(hi)}`));
    const say = (y, head, lines, col) => {
        out.push(txt(480, y, head, { anchor: 'middle', cls: 'ink bold' }));
        out.push(ln([[400, y + 6], [560, y + 6]], { stroke: COL[col], sw: 2 }));
        lines.forEach((s, i) => out.push(txt(480, y + 28 + i * 20, s, { anchor: 'middle', cls: 'ink2', size: 'sm' })));
    };
    say(85, '신뢰구간에 붙는 말', ['이 방법으로 만든 구간의 95%가', '참값 μ 를 품는다.', '이번 구간 하나에는', '확률을 붙이지 않는다.'], 's1');
    say(235, '신용구간에 붙는 말', ['평평한 사전과 이 모형을 받아들이면', '이 자료를 본 뒤 μ 가 이 구간에', '있을 확률이 0.95 다.', '사전이 바뀌면 이 말도 바뀐다.'], 's3');

    // 신용구간 칸
    out.push(panel(645, 15, 300, 400, '신용구간 칸', '자료는 고정, μ 에 분포를 준다'));
    const f = normPdf(xb, se);
    const fr3 = frame({ xRange: [496, 516], yRange: [0, 0.22], box: { x: 665, y: 80, w: 260, h: 262 } });
    out.push(area(fr3, f, lo, hi, 's3', 0.28));
    out.push(crv(fr3, f, 496, 516, { col: 's3', sw: 2.4 }));
    out.push(xaxis(fr3, 496, 516, [498, 502, 506, 510, 514]));
    out.push(txt(fr3.X(xb), fr3.Y(0.07), '넓이 0.95', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    out.push(txt(fr3.X(xb), fr3.Y(0.21), 'μ 의 사후분포 N(506, 2²)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    out.push(txt(fr3.X(506), fr3.Y(0) + 34, '평평한 사전, σ/√n = 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-bay-cred-conf',
        svg: svg({ width: W, height: H, title: '신뢰구간과 신용구간', desc: '수치가 같은 두 구간에 붙는 확률 진술이 다르다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-bay-sensitivity — 사전에 따른 사후평균, 자료가 많아지면 수렴
 * ------------------------------------------------------------------ */
add((() => {
    const W = 760, H = 340;
    const PRI = [
        { a: 1, b: 1, col: 's1', name: '평평한 사전 Beta(1, 1)' },
        { a: 8, b: 2, col: 's2', dash: '8 5', name: '높게 본 사전 Beta(8, 2)' },
        { a: 2, b: 8, col: 's3', dash: '2 4', name: '낮게 본 사전 Beta(2, 8)' },
    ];
    const ph = 0.6;
    const pm = (p, n) => (p.a + ph * n) / (p.a + p.b + n);
    // 가로축은 n 의 제곱근 눈금 — 작은 n 의 변화와 큰 n 의 수렴을 한 그림에 담는다.
    const sx = n => Math.sqrt(n);
    const fr = frame({ xRange: [0, sx(400)], yRange: [0.1, 0.9], box: { x: 70, y: 30, w: 470, h: 250 } });
    const out = [];
    for (const t of [0.2, 0.4, 0.6, 0.8]) out.push(ln([[fr.X(0), fr.Y(t)], [fr.X(sx(400)), fr.Y(t)]], { stroke: 'var(--grid)', sw: 1 }));
    for (const p of PRI) {
        const pts = [];
        for (let i = 0; i <= 200; i += 1) {
            const s = (sx(400) * i) / 200;
            pts.push([fr.X(s), fr.Y(pm(p, s * s))]);
        }
        out.push(ln(pts, { stroke: COL[p.col], sw: 2.4, dash: p.dash }));
    }
    const nt = [0, 5, 20, 50, 100, 200, 400];
    out.push(xaxis(fr, 0, sx(400), nt.map(sx), { y0: 0.1, fmt: v => String(Math.round(v * v)) }));
    out.push(yaxis(fr, 0, 0.1, 0.9, [0.2, 0.4, 0.6, 0.8]));
    out.push(txt(fr.X(sx(400)) + 14, fr.Y(0.1) + 4, 'n', { cls: 'ink2' }));
    out.push(txt(fr.X(0) - 8, fr.Y(0.9) - 14, '사후평균', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // n=5 와 n=400 에서 퍼짐
    const at = n => PRI.map(p => pm(p, n));
    const s5 = at(5), s400 = at(400);
    out.push(ln([[fr.X(sx(5)), fr.Y(Math.min(...s5))], [fr.X(sx(5)), fr.Y(Math.max(...s5))]], { stroke: COL.ink, sw: 1.4 }));
    out.push(txt(fr.X(sx(5)) + 6, fr.Y(0.88), `n = 5: ${fx(Math.min(...s5))}–${fx(Math.max(...s5))}`, { cls: 'ink', size: 'sm' }));
    out.push(txt(fr.X(sx(400)) - 4, fr.Y(0.75), `n = 400: ${fx(Math.min(...s400), 3)}–${fx(Math.max(...s400), 3)}`, { anchor: 'end', cls: 'ink', size: 'sm' }));
    out.push(key(570, 60, PRI));
    out.push(txt(570, 140, '관측 비율은 언제나 0.6', { cls: 'ink2', size: 'sm' }));
    out.push(txt(570, 158, '가로축 눈금은 n 의 제곱근 간격', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-bay-sensitivity',
        svg: svg({ width: W, height: H, title: '사전분포에 대한 민감도', desc: '자료가 적으면 사전에 따라 사후평균이 크게 갈리고 자료가 많으면 모인다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-bay-grid — 격자 근사가 칸을 늘릴수록 켤레 사후에 붙는다
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900, H = 330;
    const f = betaPdf(4, 2);
    const out = [];
    [5, 10, 40].forEach((k, j) => {
        const px0 = 15 + j * 295;
        out.push(panel(px0, 15, 280, 290, `격자 ${k}칸`, '막대: 칸의 사후확률 ÷ 칸 폭'));
        const fr = frame({ xRange: [0, 1], yRange: [0, 2.4], box: { x: px0 + 40, y: 70, w: 215, h: 190 } });
        const th = Array.from({ length: k }, (_, i) => (i + 0.5) / k);
        const w = th.map(t => t ** 3 * (1 - t));
        const s = w.reduce((a, b) => a + b, 0);
        const bw = 1 / k;
        th.forEach((t, i) => {
            const hgt = (w[i] / s) / bw;
            out.push(box(fr.X(t - bw / 2) + 0.6, fr.Y(hgt), fr.X(t + bw / 2) - fr.X(t - bw / 2) - 1.2, fr.Y(0) - fr.Y(hgt), { fill: COL.s1, op: 0.28, stroke: COL.s1, sw: 1, rx: 1 }));
        });
        out.push(crv(fr, f, 0, 1, { col: 's2', sw: 2.4 }));
        out.push(xaxis(fr, 0, 1, [0, 0.2, 0.4, 0.6, 0.8, 1]));
        out.push(yaxis(fr, 0, 0, 2.4, [0, 1, 2]));
        const mean = th.reduce((a, t, i) => a + t * w[i] / s, 0);
        out.push(txt(fr.X(0.02), fr.Y(2.25), `격자 사후평균 ${fx(mean, 3)}`, { cls: 'ink', size: 'sm' }));
        out.push(txt(fr.X(1) + 14, fr.Y(0) + 4, 'θ', { cls: 'ink2' }));
    });
    out.push(txt(W / 2, H - 6, '곡선: 켤레 계산으로 얻은 사후분포 Beta(4, 2), 평균 0.667 — 평평한 사전, 4번 중 3번 성공', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-bay-grid',
        svg: svg({ width: W, height: H, title: '격자 근사', desc: '칸을 늘릴수록 격자 근사가 켤레 사후분포 곡선에 붙는다', body: out.join('') }),
    };
})());

/* ------------------------------------------------------------------ *
 * sta-bay-lindley — p값을 고정하고 n 을 늘리면 베이즈 인자가 귀무 쪽으로
 * ------------------------------------------------------------------ */
add((() => {
    const W = 760, H = 330;
    // H0: μ = 0, H1: μ ~ N(0, σ0²) 이고 σ0 = σ. 자료 평균은 z 값이 고정되도록 둔다.
    const bf10 = (n, z) => 1 / (Math.sqrt(1 + n) * Math.exp(-(z * z / 2) * (n / (1 + n))));
    const L = Math.log10;
    const fr = frame({ xRange: [0, 5], yRange: [-2, 1.5], box: { x: 80, y: 40, w: 470, h: 260 } });
    const out = [];
    for (const t of [-2, -1, 0, 1]) out.push(ln([[fr.X(0), fr.Y(t)], [fr.X(5), fr.Y(t)]], { stroke: t === 0 ? 'var(--ink2)' : 'var(--grid)', sw: t === 0 ? 1.4 : 1, dash: t === 0 ? '5 4' : undefined }));
    const series = [
        { z: 1.959964, col: 's1', name: 'p값 0.05 에 고정 (z = 1.96)' },
        { z: 2.575829, col: 's2', dash: '8 5', name: 'p값 0.01 에 고정 (z = 2.58)' },
    ];
    for (const s of series) {
        const pts = [];
        for (let i = 0; i <= 200; i += 1) {
            const lx = (5 * i) / 200;
            pts.push([fr.X(lx), fr.Y(L(bf10(10 ** lx, s.z)))]);
        }
        out.push(ln(pts, { stroke: COL[s.col], sw: 2.5, dash: s.dash }));
    }
    out.push(xaxis(fr, 0, 5, [0, 1, 2, 3, 4, 5], { y0: -2, fmt: v => ['1', '10', '100', '1000', '10⁴', '10⁵'][v] }));
    out.push(yaxis(fr, 0, -2, 1.5, [-2, -1, 0, 1], { fmt: v => ({ '-2': '0.01', '-1': '0.1', 0: '1', 1: '10' })[v] }));
    out.push(txt(fr.X(5) + 14, fr.Y(-2) + 4, 'n', { cls: 'ink2' }));
    out.push(txt(12, fr.Y(1.5) - 14, 'BF~{10} (로그 눈금)', { cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(5) - 4, fr.Y(0) - 8, '1 보다 위: 대립 쪽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    out.push(txt(fr.X(5) - 4, fr.Y(0) + 18, '1 보다 아래: 귀무 쪽', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const marks = [10, 100, 10000];
    for (const n of marks) {
        const v = bf10(n, 1.959964);
        out.push(`<circle cx="${fr.X(L(n))}" cy="${fr.Y(L(v))}" r="4" fill="var(--s1)"/>`);
        out.push(txt(fr.X(L(n)) + 6, fr.Y(L(v)) + (n === 100 ? 18 : -8), fx(v, n >= 1000 ? 3 : 2), { cls: 'ink', size: 'sm' }));
    }
    out.push(key(575, 60, series));
    out.push(txt(575, 120, 'H~0: μ = 0', { cls: 'ink2', size: 'sm' }));
    out.push(txt(575, 138, 'H~1: μ 의 사전분포 N(0, σ²)', { cls: 'ink2', size: 'sm' }));
    out.push(txt(575, 156, '자료의 표준편차 σ 는 안다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'sta-bay-lindley',
        svg: svg({ width: W, height: H, title: '린들리 역설', desc: 'p값을 0.05 에 고정한 채 표본을 늘리면 베이즈 인자가 귀무가설 쪽으로 기운다', body: out.join('') }),
    };
})());

export default figures;
