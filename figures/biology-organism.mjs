/**
 * 생물학 13장(진화) ~ 17장(생태학)의 그림.
 *
 * 이름은 모두 bio-org- 로 시작한다(담당 C 배정 접두어). 산출 경로는
 * /figures/biology/<name>.svg 이고 pug 에서 +w3img 로 참조한다.
 *
 * SVG 안에는 수식을 쓸 수 없다(<img> 로 들어가 MathJax 가 닿지 않는다).
 * 아래첨자는 lib.mjs 의 `p~0`, `E~{Na}` 표기를 쓰고 나머지는 유니코드로 적는다.
 * 색은 PALETTE 만 쓰며 다크 모드는 svg() 가 처리한다.
 */
import { svg, frame, px, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/* ------------------------------------------------------------------ *
 * 공통 소도구 (화소 좌표계)
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}"`
        + ` fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function circ(cx, cy, r, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, dash } = {}) {
    return `<circle cx="${r2(cx)}" cy="${r2(cy)}" r="${r2(r)}" fill="${fill}" fill-opacity="${op}"`
        + ` stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.5, rot = 0, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}" fill="${fill}"`
        + ` fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}${rot ? ` transform="rotate(${r2(rot)} ${r2(cx)} ${r2(cy)})"` : ''}/>`;
}

function line(pts, { stroke = 'var(--ink2)', sw = 1.8, dash, cap = 'round' } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="${cap}"`
        + ` stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function poly(pts, { fill = 'var(--s1)', op = 0.15, stroke = 'none', sw = 1, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d} Z" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 정규분포 모양 종 곡선을 화소 좌표 점 배열로 준다. */
function bellPts(xc, sd, amp, x0, x1, yb, steps = 90) {
    const out = [];
    for (let i = 0; i <= steps; i += 1) {
        const x = x0 + ((x1 - x0) * i) / steps;
        const z = (x - xc) / sd;
        out.push([x, yb - amp * Math.exp(-0.5 * z * z)]);
    }
    return out;
}

/** 재현 가능한 의사난수(그림이 빌드마다 바뀌면 안 된다). */
function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

/* ================================================================== *
 * 13장 — 진화
 * ================================================================== */

/* 13-1. 선택의 세 유형 */
add((() => {
    const W = 640, H = 250;
    const panels = [
        { x: 30, name: '방향성 선택', shift: 26, sd: 26, note: '평균이 옮겨간다', zone: [1, 0] },
        { x: 235, name: '안정화 선택', shift: 0, sd: 16, note: '폭이 좁아진다', zone: [0.5, 0.5] },
        { x: 440, name: '분단성 선택', shift: 0, sd: 26, note: '두 봉우리로 갈린다', zone: [0, 1] },
    ];
    const b = [];
    const PW = 170, yb = 190, amp = 84;
    for (const p of panels) {
        const x0 = p.x, x1 = p.x + PW, xc = p.x + PW / 2;
        // 선택압이 걸리는 곳을 옅게 칠한다
        if (p.name === '방향성 선택') b.push(poly([[xc + 10, yb], [x1, yb], [x1, yb - 100], [xc + 10, yb - 100]], { fill: 'var(--s3)', op: 0.1 }));
        if (p.name === '안정화 선택') b.push(poly([[xc - 22, yb], [xc + 22, yb], [xc + 22, yb - 100], [xc - 22, yb - 100]], { fill: 'var(--s3)', op: 0.1 }));
        if (p.name === '분단성 선택') {
            b.push(poly([[x0, yb], [x0 + 40, yb], [x0 + 40, yb - 100], [x0, yb - 100]], { fill: 'var(--s3)', op: 0.1 }));
            b.push(poly([[x1 - 40, yb], [x1, yb], [x1, yb - 100], [x1 - 40, yb - 100]], { fill: 'var(--s3)', op: 0.1 }));
        }
        b.push(line(bellPts(xc, 26, amp, x0, x1, yb), { stroke: 'var(--ink2)', sw: 1.6, dash: '5 4' }));
        if (p.name === '분단성 선택') {
            const pts = [];
            for (let i = 0; i <= 90; i += 1) {
                const x = x0 + (PW * i) / 90;
                const a = Math.exp(-0.5 * ((x - (xc - 34)) / 14) ** 2);
                const c = Math.exp(-0.5 * ((x - (xc + 34)) / 14) ** 2);
                pts.push([x, yb - amp * Math.max(a, c)]);
            }
            b.push(line(pts, { stroke: 'var(--s1)', sw: 2.2 }));
        } else {
            b.push(line(bellPts(xc + p.shift, p.sd, amp, x0, x1, yb), { stroke: 'var(--s1)', sw: 2.2 }));
        }
        b.push(line([[x0, yb], [x1, yb]], { stroke: 'var(--ink2)', sw: 1.4 }));
        b.push(txt(xc, 28, p.name, { anchor: 'middle', cls: 'ink bold' }));
        b.push(txt(xc, yb + 20, p.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        b.push(txt(xc, yb + 38, '형질값 →', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(30, 46, '점선 = 선택 전, 실선 = 여러 세대 뒤. 옅게 칠한 띠가 자손을 많이 남긴 구간', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-selection-types',
        svg: svg({
            width: W, height: H, title: '선택의 세 유형',
            desc: '같은 출발 분포에서 어느 구간이 유리한가에 따라 평균이 옮겨가거나 폭이 좁아지거나 두 봉우리로 갈린다',
            body: b.join(''),
        }),
    };
})());

/* 13-2. 하디-바인베르크 정사각형 */
add((() => {
    const W = 560, H = 300;
    const p = 0.7, q = 0.3, S = 190, ox = 120, oy = 60;
    const b = [];
    const wp = S * p, wq = S * q;
    b.push(poly([[ox, oy], [ox + wp, oy], [ox + wp, oy + wp], [ox, oy + wp]], { fill: 'var(--s1)', op: 0.22 }));
    b.push(poly([[ox + wp, oy], [ox + S, oy], [ox + S, oy + wp], [ox + wp, oy + wp]], { fill: 'var(--s3)', op: 0.22 }));
    b.push(poly([[ox, oy + wp], [ox + wp, oy + wp], [ox + wp, oy + S], [ox, oy + S]], { fill: 'var(--s3)', op: 0.22 }));
    b.push(poly([[ox + wp, oy + wp], [ox + S, oy + wp], [ox + S, oy + S], [ox + wp, oy + S]], { fill: 'var(--s2)', op: 0.22 }));
    b.push(box(ox, oy, S, S, { sw: 1.6 }));
    b.push(line([[ox + wp, oy], [ox + wp, oy + S]], { sw: 1.4 }));
    b.push(line([[ox, oy + wp], [ox + S, oy + wp]], { sw: 1.4 }));
    b.push(txt(ox + wp / 2, oy + wp / 2 + 5, 'p²', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(ox + wp + wq / 2, oy + wp / 2 + 5, 'pq', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(ox + wp / 2, oy + wp + wq / 2 + 5, 'pq', { anchor: 'middle', cls: 'ink' }));
    b.push(txt(ox + wp + wq / 2, oy + wp + wq / 2 + 4, 'q²', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(ox + wp / 2, oy - 26, 'A 정자 (p)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + wp + wq / 2, oy - 26, 'a (q)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + S / 2, oy - 44, '아버지 쪽 배우자 주머니', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox - 10, oy + wp / 2 + 4, 'A 난자 (p)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox - 10, oy + wp + wq / 2 + 4, 'a (q)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + S + 18, oy + 22, '한 변이 1 인 정사각형을', { cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + S + 18, oy + 40, 'p 와 q 로 자른 것뿐이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + S + 18, oy + 66, '넓이의 합 = 1 이므로', { cls: 'ink', size: 'sm' }));
    b.push(txt(ox + S + 18, oy + 86, 'p² + 2pq + q² = 1', { cls: 'ink bold' }));
    b.push(txt(ox + S + 18, oy + 116, '가운데 두 칸이 같은', { cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + S + 18, oy + 134, '이형접합자라 2pq 가 된다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 16, '그림은 p = 0.7, q = 0.3 인 경우. 칸의 넓이가 곧 그 유전자형의 비율이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-hw-square',
        svg: svg({
            width: W, height: H, title: '하디-바인베르크 식은 정사각형의 넓이다',
            desc: '한 변이 1 인 정사각형을 p 와 q 로 가로세로 자르면 네 칸의 넓이가 p², pq, pq, q² 가 된다',
            body: b.join(''),
        }),
    };
})());

/* 13-3. 유전적 부동 — 집단 크기에 따른 궤적 */
add((() => {
    const W = 664, H = 280;
    const b = [];
    const mk = (bx, N, seed, label) => {
        const g = frame({ xRange: [0, 100], yRange: [0, 1], box: { x: bx, y: 46, w: 236, h: 176 } });
        b.push(g.axes({ xLabel: '세대', yLabel: '대립유전자 빈도 p', xTicks: [0, 50, 100], yTicks: [0, 0.5, 1] }));
        const cls = ['s1', 's2', 's3', 's1', 's2'];
        for (let k = 0; k < 5; k += 1) {
            const rand = rng(seed + k * 977);
            let p = 0.5;
            const pts = [[0, 0.5]];
            for (let t = 1; t <= 100; t += 1) {
                // 이항 표집을 정규 근사로 흉내낸다(그림용).
                const sd = Math.sqrt((p * (1 - p)) / (2 * N));
                const z = Math.sqrt(-2 * Math.log(rand() + 1e-9)) * Math.cos(2 * Math.PI * rand());
                p = Math.min(1, Math.max(0, p + sd * z));
                pts.push([t, p]);
                if (p === 0 || p === 1) { pts.push([100, p]); break; }
            }
            b.push(g.line(pts, { cls: cls[k] }));
        }
        b.push(txt(bx + 118, 30, label, { anchor: 'middle', cls: 'ink bold' }));
    };
    mk(56, 10, 20260807, '작은 집단 (N = 10)');
    mk(378, 500, 777001, '큰 집단 (N = 500)');
    b.push(txt(56, H - 12, '같은 0.5 에서 출발했다. 작은 집단에서는 몇십 세대 만에 0 또는 1 에 붙어(소실·고정) 되돌아오지 못한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-drift',
        svg: svg({
            width: W, height: H, title: '유전적 부동은 집단이 작을수록 빠르다',
            desc: '같은 초기 빈도에서 출발한 다섯 집단의 궤적. N = 10 에서는 빠르게 고정되거나 사라지고 N = 500 에서는 거의 움직이지 않는다',
            body: b.join(''),
        }),
    };
})());

/* 13-4. 계통수 읽는 법 — 단계통·측계통·다계통 */
add((() => {
    const W = 620, H = 296;
    const b = [];
    const tipY = [64, 104, 144, 196, 236];
    const names = ['A', 'B', 'C', 'D', 'E'];
    const xTip = 300;
    const nAB = 210, nABC = 156, nDE = 210, nRoot = 96;
    // 가지
    b.push(line([[nAB, tipY[0]], [xTip, tipY[0]]], { sw: 2 }));
    b.push(line([[nAB, tipY[1]], [xTip, tipY[1]]], { sw: 2 }));
    b.push(line([[nAB, tipY[0]], [nAB, tipY[1]]], { sw: 2 }));
    const yAB = (tipY[0] + tipY[1]) / 2;
    b.push(line([[nABC, yAB], [nAB, yAB]], { sw: 2 }));
    b.push(line([[nABC, tipY[2]], [xTip, tipY[2]]], { sw: 2 }));
    b.push(line([[nABC, yAB], [nABC, tipY[2]]], { sw: 2 }));
    const yABC = (yAB + tipY[2]) / 2;
    b.push(line([[nDE, tipY[3]], [xTip, tipY[3]]], { sw: 2 }));
    b.push(line([[nDE, tipY[4]], [xTip, tipY[4]]], { sw: 2 }));
    b.push(line([[nDE, tipY[3]], [nDE, tipY[4]]], { sw: 2 }));
    const yDE = (tipY[3] + tipY[4]) / 2;
    b.push(line([[nRoot, yABC], [nABC, yABC]], { sw: 2 }));
    b.push(line([[nRoot, yDE], [nDE, yDE]], { sw: 2 }));
    b.push(line([[nRoot, yABC], [nRoot, yDE]], { sw: 2 }));
    b.push(line([[56, (yABC + yDE) / 2], [nRoot, (yABC + yDE) / 2]], { sw: 2 }));
    for (let i = 0; i < 5; i += 1) b.push(txt(xTip + 9, tipY[i] + 5, names[i], { cls: 'ink bold' }));
    for (const [x, y] of [[nAB, yAB], [nABC, yABC], [nDE, yDE], [nRoot, (yABC + yDE) / 2]]) {
        b.push(circ(x, y, 4, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    }
    b.push(txt(nABC - 8, yABC - 10, '마디 2', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(nRoot - 8, (yABC + yDE) / 2 - 10, '뿌리', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    // 묶음은 세로 대괄호로 표시한다(타원끼리 겹치지 않게).
    const bracket = (bx, y0, y1, color) => line(
        [[bx - 7, y0], [bx, y0], [bx, y1], [bx - 7, y1]], { stroke: color, sw: 2.2 },
    );
    b.push(bracket(330, tipY[0], tipY[2], 'var(--s3)'));
    b.push(bracket(356, tipY[1], tipY[2], 'var(--s2)'));
    b.push(txt(376, 84, '{A,B,C} = 단계통군', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(376, 102, '마디 2 와 그 후손을 다 담았다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(376, 132, '{B,C} = 측계통군', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(376, 150, '같은 마디에서 나온 A 를 빠뜨렸다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(376, 180, '{A,D} = 다계통군', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(376, 198, '공통 조상이 뿌리뿐이라', { cls: 'ink2', size: 'sm' }));
    b.push(txt(376, 214, 'B, C, E 가 통째로 딸려온다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 28, '마디를 축으로 위아래를 뒤집어도 같은 나무다. 잎이 그려진 순서는 정보가 아니다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-tree-read',
        svg: svg({
            width: W, height: H, title: '계통수에서 단계통군을 가려내는 법',
            desc: '((A,B),C) 와 (D,E) 가 뿌리에서 만나는 나무. 어떤 마디와 그 후손 전부를 담아야 단계통군이다',
            body: b.join(''),
        }),
    };
})());

/* 13-5. 분자시계 */
add((() => {
    const W = 600, H = 300;
    const b = [];
    const g = frame({ xRange: [0, 60], yRange: [0, 0.18], box: { x: 70, y: 44, w: 356, h: 196 } });
    b.push(g.axes({
        xLabel: '시간 (백만 년)', yLabel: '자리당 치환 수 d',
        xTicks: [0, 20, 40, 60], yTicks: [0, 0.05, 0.1, 0.15],
    }));
    b.push(poly([[g.X(0), g.Y(0)], [g.X(60), g.Y(0.168)], [g.X(60), g.Y(0.072)]], { fill: 'var(--s2)', op: 0.12 }));
    b.push(g.line([[0, 0], [60, 0.168]], { cls: 's2', dash: '5 4' }));
    b.push(g.line([[0, 0], [60, 0.072]], { cls: 's2', dash: '5 4' }));
    b.push(g.line([[0, 0], [60, 0.12]], { cls: 's1' }));
    b.push(g.dot([40, 0.08], { cls: 'f3', r: 5 }));
    b.push(g.guide([40, 0], [40, 0.08]));
    b.push(g.label([40, 0.08], '연대를 아는 보정점', { dx: -10, dy: -8, anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(g.label([56, 0.112], '기울기 = 2μ', { dx: 0, dy: 16, anchor: 'end', cls: 'ink bold', size: 'sm' }));
    b.push(txt(438, 92, '가운데 실선이', { cls: 'ink2', size: 'sm' }));
    b.push(txt(438, 110, '일정한 속도를 가정한', { cls: 'ink2', size: 'sm' }));
    b.push(txt(438, 128, '이상적인 시계다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(438, 156, '계통마다 속도가 달라', { cls: 'ink2', size: 'sm' }));
    b.push(txt(438, 174, '실제 값은 옅은 띠만큼', { cls: 'ink2', size: 'sm' }));
    b.push(txt(438, 192, '퍼진다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, H - 30, 'd = 2μt — 두 계통이 갈라진 뒤 각자 쌓으므로 시간에 2 가 곱해진다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(70, H - 12, '보정점 하나로 기울기를 정하고, 그 기울기로 다른 분기의 연대를 거꾸로 읽는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-molclock',
        svg: svg({
            width: W, height: H, title: '분자시계는 직선의 기울기를 정하는 일이다',
            desc: '서열 차이 d 가 시간에 비례한다고 보고, 연대를 아는 보정점 하나로 기울기 2μ 를 정한다. 계통마다 속도가 달라 실제 값은 띠처럼 퍼진다',
            body: b.join(''),
        }),
    };
})());

/* ================================================================== *
 * 14장 — 생물의 다양성
 * ================================================================== */

/* 14-1. 세 영역 계통수와 두 번의 세포내공생 */
add((() => {
    const W = 640, H = 320;
    const b = [];
    const xr = 70;            // 뿌리
    const yB = 88, yA = 176, yE = 250;
    const xSplit1 = 150, xSplit2 = 250, xTip = 470;
    b.push(line([[xr, (yB + yE) / 2], [xSplit1, (yB + yE) / 2]], { sw: 2.4 }));
    b.push(line([[xSplit1, yB], [xSplit1, (yA + yE) / 2]], { sw: 2.4 }));
    b.push(line([[xSplit1, yB], [xTip, yB]], { sw: 2.4, stroke: 'var(--s1)' }));
    b.push(line([[xSplit1, (yA + yE) / 2], [xSplit2, (yA + yE) / 2]], { sw: 2.4 }));
    b.push(line([[xSplit2, yA], [xSplit2, yE]], { sw: 2.4 }));
    b.push(line([[xSplit2, yA], [xTip, yA]], { sw: 2.4, stroke: 'var(--s2)' }));
    b.push(line([[xSplit2, yE], [xTip, yE]], { sw: 2.4, stroke: 'var(--s3)' }));
    b.push(txt(xTip + 10, yB + 5, '세균 (Bacteria)', { cls: 'ink bold' }));
    b.push(txt(xTip + 10, yA + 5, '고세균 (Archaea)', { cls: 'ink bold' }));
    b.push(txt(xTip + 10, yE + 5, '진핵생물 (Eukarya)', { cls: 'ink bold' }));
    b.push(txt(xr - 4, (yB + yE) / 2 - 10, '공통 조상', { anchor: 'start', cls: 'ink2', size: 'sm' }));
    // 공생 화살표
    b.push(px(300, yB, 336, yE - 6, { cls: 's1', marker: 'ar1', width: 2, dash: '5 4' }));
    b.push(txt(258, 152, '미토콘드리아', { cls: 'ink2', size: 'sm' }));
    b.push(txt(258, 168, '(알파프로테오박테리아)', { cls: 'ink2', size: 'sm' }));
    b.push(px(390, yB, 420, yE - 6, { cls: 's1', marker: 'ar1', width: 2, dash: '5 4' }));
    b.push(txt(404, 196, '엽록체', { cls: 'ink2', size: 'sm' }));
    b.push(txt(404, 212, '(시아노박테리아)', { cls: 'ink2', size: 'sm' }));
    b.push(circ(xSplit2, (yA + yE) / 2, 5, { fill: 'var(--ink2)', stroke: 'none', sw: 0 }));
    b.push(txt(xSplit2 - 8, (yA + yE) / 2 + 4, '?', { anchor: 'end', cls: 'ink bold' }));
    b.push(txt(30, 28, '세로 막대는 갈라진 자리다. 잎이 그려진 위아래 순서에는 뜻이 없다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 46, '물음표 마디 — 진핵생물이 고세균의 한 갈래 안에서 나왔다는 견해도 유력하다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 30, '점선 화살표는 갈라짐이 아니라 삼켜 들인 것이다. 계통수의 가지와 성질이 전혀 다르다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 12, '그래서 진핵세포의 족보는 나무 하나로 그려지지 않는다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-domain-tree',
        svg: svg({
            width: W, height: H, title: '세 영역과 두 번의 세포내공생',
            desc: '리보솜 RNA 비교로 세균과 고세균이 갈리고 진핵생물이 고세균 쪽에 가깝게 놓인다. 미토콘드리아와 엽록체는 갈라진 것이 아니라 세균을 삼켜 들인 결과다',
            body: b.join(''),
        }),
    };
})());

/* 14-2. 대사 유형의 두 축 */
add((() => {
    const W = 620, H = 300;
    const b = [];
    const ox = 150, oy = 74, cw = 200, ch = 88;
    const cells = [
        ['광독립영양', '빛 + CO₂', '식물, 조류, 시아노박테리아', 's3'],
        ['광종속영양', '빛 + 유기물', '일부 세균과 고세균만', 's2'],
        ['화학무기독립영양', '무기물 산화 + CO₂', '원핵생물만. 심해 열수구 생태계의 바탕', 's3'],
        ['화학유기종속영양', '유기물 산화 + 유기물', '동물, 균류, 대부분의 세균', 's2'],
    ];
    for (let i = 0; i < 4; i += 1) {
        const cx = ox + (i % 2) * cw, cy = oy + Math.floor(i / 2) * ch;
        b.push(box(cx, cy, cw - 8, ch - 8, { fill: `var(--${cells[i][3]})`, op: 0.12, sw: 1.4 }));
        b.push(txt(cx + 12, cy + 24, cells[i][0], { cls: 'ink bold', size: 'sm' }));
        b.push(txt(cx + 12, cy + 44, cells[i][1], { cls: 'ink2', size: 'sm' }));
        b.push(txt(cx + 12, cy + 62, cells[i][2], { cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(ox + cw / 2 - 4, oy - 30, '탄소를 CO₂ 에서', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(ox + cw / 2 - 4, oy - 14, '(독립영양)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox + cw + cw / 2 - 4, oy - 30, '탄소를 유기물에서', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(ox + cw + cw / 2 - 4, oy - 14, '(종속영양)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(ox - 14, oy + 36, '에너지를', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(ox - 14, oy + 52, '빛에서', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(ox - 14, oy + ch + 36, '에너지를', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(ox - 14, oy + ch + 52, '화학 반응에서', { anchor: 'end', cls: 'ink', size: 'sm' }));
    b.push(txt(30, 30, '가로축은 탄소를 어디서 얻는가, 세로축은 에너지를 어디서 얻는가다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 44, '진핵생물이 채우는 칸은 왼쪽 위와 오른쪽 아래 둘뿐이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 26, '네 칸을 모두 채우는 것은 원핵생물이며, 왼쪽 아래 칸은 원핵생물만의 것이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 8, '대사의 발명은 거의 전부 원핵생물이 했다는 뜻이다.', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'bio-org-metabolism-axes',
        svg: svg({
            width: W, height: H, title: '대사 유형은 두 축의 조합이다',
            desc: '에너지원(빛/화학 반응)과 탄소원(CO2/유기물)의 두 축을 조합하면 네 가지 영양 방식이 나온다. 진핵생물은 그중 둘만 쓴다',
            body: b.join(''),
        }),
    };
})());

/* 14-3. 세대교번 */
add((() => {
    const W = 640, H = 330;
    const b = [];
    const cx = 250, cy = 178, R = 108;
    const at = (deg, r = R) => [cx + r * Math.cos((deg * Math.PI) / 180), cy - r * Math.sin((deg * Math.PI) / 180)];
    b.push(circ(cx, cy, R, { stroke: 'var(--grid)', sw: 1.4, dash: '5 5' }));
    // 이배체 반원 / 반수체 반원 배경
    b.push(`<path d="M${cx - R - 26} ${cy} A${R + 26} ${R + 26} 0 0 1 ${cx + R + 26} ${cy} Z" fill="var(--s1)" fill-opacity="0.08"/>`);
    b.push(`<path d="M${cx + R + 26} ${cy} A${R + 26} ${R + 26} 0 0 1 ${cx - R - 26} ${cy} Z" fill="var(--s3)" fill-opacity="0.08"/>`);
    b.push(line([[cx - R - 26, cy], [cx + R + 26, cy]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 4' }));
    const nodes = [
        [90, '포자체 (2n)', '우리가 보는 양치식물'],
        [0, '포자모세포 (2n)', ''],
        [-60, '포자 (n)', ''],
        [-120, '배우체 (n)', '독립된 다세포 개체'],
        [180, '배우자 (n)', '정자와 난자'],
    ];
    for (const [deg, name, sub] of nodes) {
        const [x, y] = at(deg);
        b.push(circ(x, y, 7, { fill: 'var(--ink)', stroke: 'none', sw: 0 }));
        const anchor = Math.cos((deg * Math.PI) / 180) > 0.2 ? 'start' : Math.cos((deg * Math.PI) / 180) < -0.2 ? 'end' : 'middle';
        const dx = anchor === 'start' ? 12 : anchor === 'end' ? -12 : 0;
        const dy = deg === 90 ? -14 : 5;
        b.push(txt(x + dx, y + dy, name, { anchor, cls: 'ink bold', size: 'sm' }));
        if (sub) b.push(txt(x + dx, y + dy + 16, sub, { anchor, cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(cx, cy - 14, '위쪽 = 이배체 (2n)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(cx, cy + 20, '아래쪽 = 반수체 (n)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const evt = (d1, d2, label, color) => {
        const [x1, y1] = at((d1 + d2) / 2, R + 4);
        return txt(x1 + (Math.cos((((d1 + d2) / 2) * Math.PI) / 180) > 0 ? 6 : -6), y1 + 4, label,
            { anchor: Math.cos((((d1 + d2) / 2) * Math.PI) / 180) > 0 ? 'start' : 'end', cls: color, size: 'sm' });
    };
    b.push(evt(0, -60, '감수분열 ★', 'ink bold'));
    b.push(evt(90, 0, '체세포분열', 'ink2'));
    b.push(evt(-60, -120, '체세포분열', 'ink2'));
    b.push(evt(-120, -180, '체세포분열', 'ink2'));
    b.push(evt(180, 90, '수정 ★', 'ink bold'));
    b.push(txt(410, 74, '동물과 무엇이 다른가', { cls: 'ink bold' }));
    b.push(txt(410, 98, '동물: 감수분열의 산물이', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 114, '곧 배우자다. 반수체 상태가', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 130, '세포 하나로 끝난다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 158, '식물: 감수분열의 산물은', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 174, '포자다. 포자가 자라 다세포', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 190, '개체(배우체)가 되고,', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 206, '그 개체가 체세포분열로', { cls: 'ink2', size: 'sm' }));
    b.push(txt(410, 222, '배우자를 만든다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 30, '한 바퀴에 감수분열이 딱 한 번, 수정이 딱 한 번이다. 표를 채우고 나서 이것으로 검산한다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, H - 12, '진화의 방향은 배우체가 작아지고 포자체가 커지는 쪽이었다. 종자식물의 배우체가 꽃가루와 배낭이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-alternation',
        svg: svg({
            width: W, height: H, title: '식물의 세대교번',
            desc: '반수체 배우체와 이배체 포자체가 번갈아 나타난다. 감수분열의 산물이 배우자가 아니라 포자라는 점이 동물과 결정적으로 다르다',
            body: b.join(''),
        }),
    };
})());

/* 14-4. 바이러스의 크기와 구조 */
add((() => {
    const W = 640, H = 300;
    const b = [];
    // 왼쪽: 구조
    b.push(txt(30, 30, '구조 — 세포에 있는 것 가운데 무엇이 없는지를 보라', { cls: 'ink2', size: 'sm' }));
    const vx = 132, vy = 130;
    b.push(circ(vx, vy, 62, { stroke: 'var(--s2)', sw: 2, dash: '6 4', fill: 'var(--s2)', op: 0.07 }));
    b.push(circ(vx, vy, 44, { stroke: 'var(--s1)', sw: 2, fill: 'var(--s1)', op: 0.1 }));
    for (let i = 0; i < 8; i += 1) {
        const a = (i * 45 * Math.PI) / 180;
        b.push(line([[vx + 62 * Math.cos(a), vy - 62 * Math.sin(a)], [vx + 74 * Math.cos(a), vy - 74 * Math.sin(a)]], { stroke: 'var(--s2)', sw: 2 }));
    }
    b.push(line([[vx - 22, vy + 6], [vx - 10, vy - 14], [vx + 4, vy + 10], [vx + 18, vy - 12], [vx + 26, vy + 4]], { stroke: 'var(--s3)', sw: 2.6 }));
    b.push(txt(vx, vy + 96, '유전체 (DNA 또는 RNA)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(vx, vy + 114, '캡시드 = 단백질 껍질', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(vx, vy + 132, '외피 = 숙주 막에서 빌린 것 (없는 것도 있다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 오른쪽: 크기 사다리(로그 눈금)
    const bx = 330, by0 = 70, by1 = 250;
    const L = v => by1 - ((Math.log10(v) - 1) / (Math.log10(2e4) - 1)) * (by1 - by0);
    b.push(line([[bx, by0 - 10], [bx, by1]], { stroke: 'var(--ink2)', sw: 1.6 }));
    const items = [
        [25, '리보솜 25 nm'],
        [100, '보통의 바이러스 100 nm'],
        [1000, '세균 1 µm = 1000 nm'],
        [15000, '사람의 세포 15 µm'],
    ];
    for (const [v, label] of items) {
        const y = L(v);
        b.push(line([[bx - 6, y], [bx + 6, y]], { stroke: 'var(--ink2)', sw: 1.6 }));
        b.push(circ(bx, y, 4, { fill: 'var(--s1)', stroke: 'none', sw: 0 }));
        b.push(txt(bx + 14, y + 4, label, { cls: 'ink', size: 'sm' }));
    }
    b.push(txt(bx - 10, by0 - 16, '크게', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(bx - 10, by1 + 4, '작게', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(330, 44, '세로 눈금은 한 칸이 10배인 로그 눈금이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(330, 276, '바이러스는 세균보다 열 배 작고 리보솜보다 몇 배 클 뿐이다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(330, 294, '이 크기에는 리보솜도 대사 효소도 담을 자리가 없다.', { cls: 'ink bold', size: 'sm' }));
    return {
        name: 'bio-org-virus-structure',
        svg: svg({
            width: W, height: H, title: '바이러스의 구조와 크기',
            desc: '유전체와 캡시드, 때로는 외피가 전부다. 크기 사다리에서 보듯 리보솜을 담을 공간조차 없으므로 숙주 세포 밖에서는 증식할 수 없다',
            body: b.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 추가 소도구 — 15~17장에서 쓴다
 * ------------------------------------------------------------------ */

/**
 * 화소 좌표 화살표. lib 의 px() 는 class 로 색을 주는데 'ark' 같은 마커 이름을
 * class 로 넘기면 선이 사라지고 화살촉만 남는다. 여기서는 stroke 를 직접 지정해
 * 그 함정을 피한다(figures/biology-cell.mjs 와 같은 방식).
 */
function arw(x1, y1, x2, y2, { cls = 'ark', marker, width = 2, dash } = {}) {
    const col = {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ark: 'var(--ink2)',
    }[cls] ?? 'var(--ink2)';
    const mk = marker ?? (cls === 's1' ? 'ar1' : cls === 's2' ? 'ar2' : cls === 's3' ? 'ar3' : 'ark');
    return `<path fill="none" stroke="${col}" stroke-width="${width}" stroke-linecap="round"`
        + ` marker-end="url(#${mk})"${dash ? ` stroke-dasharray="${dash}"` : ''}`
        + ` d="M${r2(x1)} ${r2(y1)} L${r2(x2)} ${r2(y2)}"/>`;
}

/**
 * 축을 언제나 상자의 왼쪽 아래에 그린다. lib 의 axes() 는 데이터 0 이 범위 안에
 * 있으면 축을 0 에 놓아 눈금 글자가 그래프 한가운데 찍히므로, 0 을 가로지르는
 * 그림(막전위, 수분퍼텐셜 등)에서는 이쪽을 쓴다.
 */
function gridAxes(g, bx, o = {}) {
    const { xTicks = [], yTicks = [], xFmt = v => `${v}`, yFmt = v => `${v}`,
        xTitle, yTitle, grid = true } = o;
    const out = [];
    const L = bx.x, R = bx.x + bx.w, B = bx.y + bx.h, T = bx.y;
    if (grid) {
        for (const t of xTicks) out.push(`<path class="gr" d="M${r2(g.X(t))} ${r2(B)} V${r2(T)}"/>`);
        for (const t of yTicks) out.push(`<path class="gr" d="M${r2(L)} ${r2(g.Y(t))} H${r2(R)}"/>`);
    }
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${r2(L)} ${r2(B)} H${r2(R + 12)}"/>`);
    out.push(`<path class="ax" marker-end="url(#ark)" d="M${r2(L)} ${r2(B)} V${r2(T - 12)}"/>`);
    for (const t of xTicks) out.push(txt(g.X(t), B + 16, xFmt(t), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    for (const t of yTicks) out.push(txt(L - 6, g.Y(t) + 4, yFmt(t), { anchor: 'end', cls: 'ink2', size: 'sm' }));
    if (xTitle) out.push(txt((L + R) / 2, B + 34, xTitle, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    if (yTitle) out.push(txt(L, T - 20, yTitle, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return out.join('');
}

/* ================================================================== *
 * 14장 — 생물의 다양성 (추가분)
 * ================================================================== */

/* 14-5. 육상식물의 계통과 가지에 찍은 혁신 */
add((() => {
    const W = 700, H = 352;
    const b = [];
    const tipY = [58, 110, 162, 214, 266];
    const xTip = 430;
    const nodeX = [130, 200, 270, 340];
    const nodeY = [107, 156, 201, 240];
    // 가지
    b.push(line([[70, nodeY[0]], [nodeX[0], nodeY[0]]], { sw: 2.2 }));
    b.push(line([[nodeX[0], tipY[0]], [nodeX[0], nodeY[1]]], { sw: 2.2 }));
    b.push(line([[nodeX[1], tipY[1]], [nodeX[1], nodeY[2]]], { sw: 2.2 }));
    b.push(line([[nodeX[2], tipY[2]], [nodeX[2], nodeY[3]]], { sw: 2.2 }));
    b.push(line([[nodeX[3], tipY[3]], [nodeX[3], tipY[4]]], { sw: 2.2 }));
    b.push(line([[nodeX[0], tipY[0]], [xTip, tipY[0]]], { sw: 2.2 }));
    b.push(line([[nodeX[1], tipY[1]], [xTip, tipY[1]]], { sw: 2.2 }));
    b.push(line([[nodeX[2], tipY[2]], [xTip, tipY[2]]], { sw: 2.2 }));
    b.push(line([[nodeX[3], tipY[3]], [xTip, tipY[3]]], { sw: 2.2 }));
    b.push(line([[nodeX[3], tipY[4]], [xTip, tipY[4]]], { sw: 2.2 }));
    b.push(line([[nodeX[0], nodeY[1]], [nodeX[1], nodeY[1]]], { sw: 2.2 }));
    b.push(line([[nodeX[1], nodeY[2]], [nodeX[2], nodeY[2]]], { sw: 2.2 }));
    b.push(line([[nodeX[2], nodeY[3]], [nodeX[3], nodeY[3]]], { sw: 2.2 }));
    // 잎 이름과 우세 세대
    const names = ['녹조류 (외군)', '선태류 — 이끼', '양치류', '겉씨식물', '속씨식물'];
    const gens = ['배우체 (n)', '배우체 (n)', '포자체 (2n)', '포자체 (2n)', '포자체 (2n)'];
    for (let i = 0; i < 5; i += 1) {
        b.push(txt(xTip + 9, tipY[i] + 5, names[i], { cls: 'ink bold', size: 'sm' }));
        b.push(txt(560, tipY[i] + 5, gens[i], { cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(560, 40, '우세한 세대', { cls: 'ink', size: 'sm' }));
    // 혁신 표시
    const mark = (x, y, n) => box(x - 4, y - 9, 8, 18, { fill: 'var(--s3)', op: 0.85, stroke: 'var(--s3)', sw: 1, rx: 2 })
        + txt(x, y - 14, n, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    b.push(mark(165, nodeY[1], '①'));
    b.push(mark(235, nodeY[2], '②'));
    b.push(mark(305, nodeY[3], '③'));
    b.push(mark(380, tipY[4], '④'));
    b.push(txt(30, 26, '가지에 번호를 찍으면 계통수가 ‘언제 무엇이 새로 생겼는가’의 지도가 된다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 300, '① 큐티클 · 기공 · 배 보호 — 마르지 않고 살기', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 318, '② 관다발(물관부 · 체관부)과 리그닌 — 세우고 나르기', { cls: 'ink', size: 'sm' }));
    b.push(txt(374, 300, '③ 종자와 꽃가루 — 물 없이 수정하기', { cls: 'ink', size: 'sm' }));
    b.push(txt(374, 318, '④ 꽃 · 열매 · 중복수정 — 수분 효율과 산포', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 340, '외울 것은 이름이 아니라 순서다. 각 혁신은 그 앞 단계가 풀지 못한 문제를 하나씩 푼다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-plant-lineage',
        svg: svg({
            width: W, height: H, title: '육상식물의 계통과 네 가지 혁신',
            desc: '녹조류에서 속씨식물까지의 계통수에 큐티클, 관다발, 종자, 꽃이 생긴 자리를 표시했다. 진화 방향은 배우체의 축소와 포자체의 우세다',
            body: b.join(''),
        }),
    };
})());

/* 14-6. 동물의 몸 구조로 읽는 계통 */
add((() => {
    const W = 700, H = 320;
    const b = [];
    const tipY = [60, 112, 164, 216, 268];
    const xTip = 300;
    const nX = [70, 120, 180, 240];
    const nY = [109, 158, 203, 242];
    b.push(line([[30, nY[0]], [nX[0], nY[0]]], { sw: 2.2 }));
    b.push(line([[nX[0], tipY[0]], [nX[0], nY[1]]], { sw: 2.2 }));
    b.push(line([[nX[1], tipY[1]], [nX[1], nY[2]]], { sw: 2.2 }));
    b.push(line([[nX[2], tipY[2]], [nX[2], nY[3]]], { sw: 2.2 }));
    b.push(line([[nX[3], tipY[3]], [nX[3], tipY[4]]], { sw: 2.2 }));
    b.push(line([[nX[0], tipY[0]], [xTip, tipY[0]]], { sw: 2.2 }));
    b.push(line([[nX[1], tipY[1]], [xTip, tipY[1]]], { sw: 2.2 }));
    b.push(line([[nX[2], tipY[2]], [xTip, tipY[2]]], { sw: 2.2 }));
    b.push(line([[nX[3], tipY[3]], [xTip, tipY[3]]], { sw: 2.2 }));
    b.push(line([[nX[3], tipY[4]], [xTip, tipY[4]]], { sw: 2.2 }));
    b.push(line([[nX[0], nY[1]], [nX[1], nY[1]]], { sw: 2.2 }));
    b.push(line([[nX[1], nY[2]], [nX[2], nY[2]]], { sw: 2.2 }));
    b.push(line([[nX[2], nY[3]], [nX[3], nY[3]]], { sw: 2.2 }));
    const names = ['해면동물', '자포동물', '후구동물', '탈피동물', '촉수담륜동물'];
    const subs = ['참된 조직이 없다', '히드라 · 해파리 · 산호', '극피동물 · 척삭동물', '절지동물 · 선형동물', '연체동물 · 환형동물'];
    for (let i = 0; i < 5; i += 1) {
        b.push(txt(xTip + 9, tipY[i] + 1, names[i], { cls: 'ink bold', size: 'sm' }));
        b.push(txt(xTip + 9, tipY[i] + 16, subs[i], { cls: 'ink2', size: 'sm' }));
    }
    const mark = (x, y, n) => box(x - 4, y - 9, 8, 18, { fill: 'var(--s3)', op: 0.85, stroke: 'var(--s3)', sw: 1, rx: 2 })
        + txt(x, y - 14, n, { anchor: 'middle', cls: 'ink bold', size: 'sm' });
    b.push(mark(95, nY[1], '①'));
    b.push(mark(150, nY[2], '②'));
    b.push(mark(210, nY[3], '③'));
    b.push(mark(268, tipY[2], '④'));
    const T = [
        ['가지에 찍은 번호가 그 자리에서', 'ink2'],
        ['새로 생긴 몸 구조다.', 'ink2'],
        ['', 'ink2'],
        ['① 참된 조직과 방사대칭, 이배엽', 'ink'],
        ['② 좌우대칭과 삼배엽, 앞뒤 축', 'ink'],
        ['③ 선구동물 — 먼저 뚫린 구멍이 입', 'ink'],
        ['④ 후구동물 — 먼저 뚫린 구멍이 항문', 'ink'],
        ['', 'ink2'],
        ['형태로 세운 옛 분류가 서열로 크게', 'ink2'],
        ['수정된 자리가 ③ 이다. 선구동물이', 'ink2'],
        ['탈피동물과 촉수담륜동물로 갈린다는', 'ink2'],
        ['결론은 몸 구조만 보아서는 나오지', 'ink2'],
        ['않았다.', 'ink2'],
    ];
    T.forEach(([s, c], i) => { if (s) b.push(txt(462, 66 + i * 17, s, { cls: c, size: 'sm' })); });
    b.push(txt(30, 26, '동물을 나누는 축은 대칭성, 배엽 수, 그리고 입과 항문 중 무엇이 먼저 생기는가다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 304, '잎이 위아래로 놓인 순서에는 뜻이 없다. 정보는 오직 어떤 잎들이 한 마디를 이루는가에 있다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-animal-tree',
        svg: svg({
            width: W, height: H, title: '동물의 몸 구조로 읽는 계통',
            desc: '해면동물부터 촉수담륜동물까지의 계통수에 조직, 좌우대칭, 선구와 후구가 갈린 자리를 표시했다',
            body: b.join(''),
        }),
    };
})());

/* ================================================================== *
 * 15장 — 식물의 구조와 생리
 * ================================================================== */

/* 15-1. 수분퍼텐셜 — 합을 비교해야 한다 */
add((() => {
    const W = 680, H = 322;
    const b = [];
    const y0 = 170, SC = 62;
    const Y = v => y0 - v * SC;
    const group = (gx, vals, names) => {
        const [ps, pp] = vals;
        const tot = ps + pp;
        const bars = [[ps, 's1'], [pp, 's2'], [tot, 's3']];
        bars.forEach(([v, c], i) => {
            const bxx = gx + i * 30;
            if (Math.abs(v) < 0.001) {
                b.push(line([[bxx, y0], [bxx + 24, y0]], { stroke: `var(--${c})`, sw: 3 }));
                b.push(txt(bxx + 12, y0 - 8, '0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            } else if (v < 0) {
                b.push(box(bxx, y0, 24, Y(v) - y0, { fill: `var(--${c})`, op: 0.3, stroke: `var(--${c})`, sw: 1.5, rx: 2 }));
                b.push(txt(bxx + 12, Y(v) + 14, v.toFixed(2), { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            } else {
                b.push(box(bxx, Y(v), 24, y0 - Y(v), { fill: `var(--${c})`, op: 0.3, stroke: `var(--${c})`, sw: 1.5, rx: 2 }));
                b.push(txt(bxx + 12, Y(v) - 7, `+${v.toFixed(2)}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            }
        });
        names.forEach((s, i) => b.push(txt(gx + 42, 250 + i * 15, s, { anchor: 'middle', cls: i ? 'ink2' : 'ink', size: 'sm' })));
    };
    b.push(box(24, 46, 316, 226, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
    b.push(box(356, 46, 300, 226, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
    b.push(line([[32, y0], [332, y0]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 4' }));
    b.push(line([[364, y0], [648, y0]], { stroke: 'var(--ink2)', sw: 1.2, dash: '4 4' }));
    b.push(txt(30, 166, '0', { cls: 'ink2', size: 'sm' }));
    b.push(txt(362, 166, '0', { cls: 'ink2', size: 'sm' }));
    b.push(txt(182, 66, '세포를 Ψ = −0.50 MPa 인 용액에 넣었다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(506, 66, '같은 세포를 순수한 물에 넣었다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    group(60, [-0.50, 0], ['0.20 mol/L 자당 용액', '(열린 비커라 압력이 없다)']);
    group(196, [-0.80, 0.30], ['세포', '(세포벽이 팽압을 만든다)']);
    group(392, [0, 0], ['순수한 물', '']);
    group(528, [-0.80, 0.30], ['같은 세포', '']);
    b.push(txt(182, 92, '두 Ψ 가 같다 → 물의 순 이동이 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(478, 120, 524, 120, { cls: 's3' }));
    b.push(txt(501, 108, '물이 세포로', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '물은 Ψ 가 높은 쪽에서 낮은 쪽으로 간다. 비교할 것은 Ψs 하나가 아니라 합인 Ψ 다', { cls: 'ink2', size: 'sm' }));
    b.push(legend(34, H - 12, [{ slot: 1, name: 'Ψs 용질퍼텐셜 (0 이하)' }]));
    b.push(legend(216, H - 12, [{ slot: 2, name: 'Ψp 압력퍼텐셜' }]));
    b.push(legend(360, H - 12, [{ slot: 3, name: 'Ψ = Ψs + Ψp — 이동 방향을 정하는 값' }]));
    return {
        name: 'bio-org-water-potential',
        svg: svg({
            width: W, height: H, title: '수분퍼텐셜은 두 항의 합이다',
            desc: '세포의 용질퍼텐셜이 용액보다 낮아도 팽압이 그만큼을 상쇄하면 물은 움직이지 않는다. 비교해야 할 것은 합인 수분퍼텐셜이다',
            body: b.join(''),
        }),
    };
})());

/* 15-2. 토양에서 잎까지의 수분퍼텐셜 사다리 */
add((() => {
    const W = 660, H = 300;
    const b = [];
    const bx = { x: 92, y: 56, w: 300, h: 178 };
    const g = frame({ xRange: [0, 4.4], yRange: [-2.6, 0], box: bx });
    const cats = ['토양', '뿌리', '줄기 아래', '줄기 위', '잎'];
    b.push(gridAxes(g, bx, {
        xTicks: [0, 1, 2, 3, 4], yTicks: [0, -0.5, -1, -1.5, -2, -2.5],
        xFmt: v => cats[v], yFmt: v => v.toFixed(1),
        yTitle: 'Ψ (MPa)',
    }));
    const pts = [[0, -0.30], [1, -0.55], [2, -1.10], [3, -1.70], [4, -2.30]];
    b.push(g.line(pts, { cls: 's1' }));
    for (const p of pts) b.push(g.dot(p, { cls: 'f1', r: 4.5 }));
    b.push(g.label([0, -0.30], '−0.30', { dx: 4, dy: -8, cls: 'ink2', size: 'sm' }));
    b.push(g.label([4, -2.30], '−2.30', { dx: -6, dy: 16, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(arw(g.X(4) + 8, g.Y(-2.3) + 8, g.X(4.35), g.Y(-2.55), { cls: 's2' }));
    const T = [
        '물이 위로 가려면 위쪽 Ψ 가 더 낮아야',
        '한다. 그래서 이 선은 반드시 내려간다.',
        '',
        '내려가는 이유는 둘이다.',
        '· 높이 1 m 마다 약 9.8 kPa',
        '· 관을 지나며 생기는 마찰 손실',
        '',
        '가장 큰 낙차는 잎에서 대기로 넘어갈',
        '때 생긴다. 물을 끌어올리는 힘이',
        '거기서 나온다.',
    ];
    T.forEach((s, i) => { if (s) b.push(txt(430, 66 + i * 17, s, { cls: i < 2 ? 'ink' : 'ink2', size: 'sm' })); });
    b.push(txt(30, 26, '한 칸씩 내려가는 계단이다. 어느 칸에서 얼마나 떨어지는지가 곧 그 구간의 저항이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(150, 272, '오른쪽 아래로 빠져나가는 화살표가 대기다. 상대습도 50% 면 약 −100 MPa 로 뚝 떨어진다', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 292, '뿌리압으로 만들 수 있는 값은 +0.1 MPa 규모다. 이 계단을 거슬러 오를 크기가 못 된다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-psi-ladder',
        svg: svg({
            width: W, height: H, title: '토양에서 잎까지 이어지는 수분퍼텐셜의 계단',
            desc: '토양에서 잎으로 갈수록 수분퍼텐셜이 낮아지고, 잎에서 대기로 넘어갈 때 낙차가 가장 크다',
            body: b.join(''),
        }),
    };
})());

/* 15-3. 공변세포 — 벽의 구조가 변형의 방향을 정한다 */
add((() => {
    const W = 660, H = 322;
    const b = [];
    const ry = 66, cw = 34;
    const pair = (cx, cy, gap, ib, open) => {
        const s = [];
        const cellL = `M${r2(cx - gap)} ${r2(cy - ry)} Q${r2(cx - gap - cw - ib)} ${r2(cy)} ${r2(cx - gap)} ${r2(cy + ry)}`
            + ` Q${r2(cx - gap - ib)} ${r2(cy)} ${r2(cx - gap)} ${r2(cy - ry)} Z`;
        const cellR = `M${r2(cx + gap)} ${r2(cy - ry)} Q${r2(cx + gap + cw + ib)} ${r2(cy)} ${r2(cx + gap)} ${r2(cy + ry)}`
            + ` Q${r2(cx + gap + ib)} ${r2(cy)} ${r2(cx + gap)} ${r2(cy - ry)} Z`;
        const pore = `M${r2(cx - gap)} ${r2(cy - ry)} Q${r2(cx - gap - ib)} ${r2(cy)} ${r2(cx - gap)} ${r2(cy + ry)}`
            + ` L${r2(cx + gap)} ${r2(cy + ry)} Q${r2(cx + gap + ib)} ${r2(cy)} ${r2(cx + gap)} ${r2(cy - ry)} Z`;
        s.push(`<path d="${pore}" fill="var(--ink)" fill-opacity="0.16" stroke="none"/>`);
        for (const d of [cellL, cellR]) {
            s.push(`<path d="${d}" fill="var(--s3)" fill-opacity="0.16" stroke="var(--s3)" stroke-width="1.8"/>`);
        }
        // 안쪽 벽을 두껍게 표시한다
        s.push(`<path d="M${r2(cx - gap)} ${r2(cy - ry)} Q${r2(cx - gap - ib)} ${r2(cy)} ${r2(cx - gap)} ${r2(cy + ry)}" fill="none" stroke="var(--ink2)" stroke-width="3.4"/>`);
        s.push(`<path d="M${r2(cx + gap)} ${r2(cy - ry)} Q${r2(cx + gap + ib)} ${r2(cy)} ${r2(cx + gap)} ${r2(cy + ry)}" fill="none" stroke="var(--ink2)" stroke-width="3.4"/>`);
        if (open) {
            s.push(txt(cx, cy + 5, '기공', { anchor: 'middle', cls: 'ink', size: 'sm' }));
        }
        return s.join('');
    };
    b.push(pair(150, 165, 2, 0, false));
    b.push(pair(380, 165, 2, 17, true));
    b.push(txt(150, 70, '닫힘', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(380, 70, '열림', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(150, 262, 'K⁺ 이 빠져나가 팽압이 낮다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(380, 262, '물이 들어와 팽압이 높다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(arw(288, 150, 322, 157, { cls: 's1' }));
    b.push(txt(284, 154, 'K⁺ 과 물', { anchor: 'end', cls: 'ink', size: 'sm' }));
    const T = [
        ['공변세포의 안쪽 벽이 바깥쪽보다', 'ink2'],
        ['두껍고, 셀룰로스 미세섬유가', 'ink2'],
        ['둘레 방향으로 감겨 있다.', 'ink2'],
        ['', 'ink2'],
        ['그래서 물이 들어와 부풀 때 늘어날', 'ink'],
        ['수 있는 방향은 길이뿐이고, 길어진', 'ink'],
        ['세포가 바깥으로 휘면서 가운데가', 'ink'],
        ['벌어진다.', 'ink'],
        ['', 'ink2'],
        ['여는 신호: 청색광, 잎 안 CO₂ 감소', 'ink2'],
        ['닫는 신호: 앱시스산, 건조, 고온', 'ink2'],
    ];
    T.forEach(([s, c], i) => { if (s) b.push(txt(486, 92 + i * 17, s, { cls: c, size: 'sm' })); });
    b.push(txt(30, 26, '같은 세포가 물을 얻었을 뿐인데 구멍이 생긴다. 벽의 구조가 변형의 방향을 정한다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 306, '기공이 열리면 CO₂ 가 들어오고 물이 나간다. 잎은 두 요구를 동시에 만족시킬 수 없다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-guard-cell',
        svg: svg({
            width: W, height: H, title: '기공은 왜 부풀면 열리는가',
            desc: '공변세포는 안쪽 벽이 두껍고 미세섬유가 둘레로 감겨 있어, 팽압이 오르면 길이 방향으로만 늘어나 바깥으로 휘고 가운데가 벌어진다',
            body: b.join(''),
        }),
    };
})());

/* 15-4. 광주기 — 재는 것은 밤의 길이다 */
add((() => {
    const W = 720, H = 336;
    const b = [];
    const x0 = 100, hpx = 15;
    const X = h => x0 + h * hpx;
    const rows = [84, 134, 184, 234];
    const bh = 28;
    for (const h of [0, 6, 12, 18, 24]) {
        b.push(line([[X(h), 66], [X(h), 70]], { stroke: 'var(--ink2)', sw: 1.2 }));
        b.push(txt(X(h), 62, `${h}`, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(X(24) + 8, 62, 'h', { cls: 'ink2', size: 'sm' }));
    const bar = (y, lightH) => {
        b.push(box(X(0), y, X(lightH) - X(0), bh, { fill: 'var(--s2)', op: 0.22, stroke: 'var(--s2)', sw: 1.2, rx: 2 }));
        b.push(box(X(lightH), y, X(24) - X(lightH), bh, { fill: 'var(--ink2)', op: 0.55, stroke: 'var(--ink2)', sw: 1.2, rx: 2 }));
    };
    const flash = (y, h, filled) => {
        b.push(line([[X(h), y - 8], [X(h), y + bh + 4]], { stroke: 'var(--s1)', sw: 2.2 }));
        b.push(circ(X(h), y - 11, 4.5, { fill: filled ? 'var(--s1)' : 'none', op: 1, stroke: 'var(--s1)', sw: 2 }));
    };
    const desc = ['(가) 명기 16 h + 암기 8 h', '(나) 명기 12 h + 암기 12 h',
        '(다) (나) 의 암기 한가운데에 적색광 섬광', '(라) (다) 직후 원적색광 섬광'];
    const nights = ['8 h', '12 h', '6 h', '12 h'];
    const shortDay = ['✕', '○', '✕', '○'];
    const longDay = ['○', '✕', '○', '✕'];
    bar(rows[0], 16); bar(rows[1], 12); bar(rows[2], 12); bar(rows[3], 12);
    flash(rows[2], 18, true);
    flash(rows[3], 18, true); flash(rows[3], 19.4, false);
    for (let i = 0; i < 4; i += 1) {
        b.push(txt(x0, rows[i] - 7, desc[i], { cls: 'ink', size: 'sm' }));
        b.push(txt(490, rows[i] + 19, nights[i], { cls: 'ink2', size: 'sm' }));
        b.push(txt(578, rows[i] + 19, shortDay[i], { cls: 'ink bold' }));
        b.push(txt(654, rows[i] + 19, longDay[i], { cls: 'ink bold' }));
    }
    b.push(txt(490, 62, '최장 암기', { cls: 'ink', size: 'sm' }));
    b.push(txt(566, 62, '단일식물', { cls: 'ink', size: 'sm' }));
    b.push(txt(642, 62, '장일식물', { cls: 'ink', size: 'sm' }));
    b.push(circ(112, 288, 4.5, { fill: 'var(--s1)', stroke: 'var(--s1)', sw: 2 }));
    b.push(txt(124, 292, '적색광 섬광 (약 660 nm)', { cls: 'ink2', size: 'sm' }));
    b.push(circ(292, 288, 4.5, { fill: 'none', stroke: 'var(--s1)', sw: 2 }));
    b.push(txt(304, 292, '원적색광 섬광 (약 730 nm) — 앞의 효과를 취소한다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '두 식물의 임계 암기를 11 h 로 놓았다. 단일식물은 그보다 길면, 장일식물은 짧으면 꽃이 핀다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 44, '(나)와 (다)를 견주는 것이 이 실험의 핵심이다. 명기의 총량은 같고 암기의 연속성만 다르다', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 318, '결과가 갈린다는 것은 식물이 낮의 길이가 아니라 연속된 밤의 길이를 잰다는 뜻이다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-photoperiod',
        svg: svg({
            width: W, height: H, title: '광주기 실험 — 식물이 재는 것은 밤의 길이다',
            desc: '명기 총량이 같아도 암기 한가운데에 섬광을 주면 결과가 뒤집힌다. 원적색광을 이어서 주면 그 효과가 취소된다',
            body: b.join(''),
        }),
    };
})());

/* ================================================================== *
 * 16장 — 동물의 구조와 생리
 * ================================================================== */

/* 16-1. 평형전위 — 농도차와 전기력이 균형을 이룬다 */
add((() => {
    const W = 700, H = 384;
    const b = [];
    const panels = [
        { x: 16, title: '1. 처음' },
        { x: 246, title: '2. K⁺ 이 조금 나간 뒤' },
        { x: 476, title: '3. 균형' },
    ];
    const PW = 208;
    for (let i = 0; i < 3; i += 1) {
        const p = panels[i];
        const xc = p.x + PW / 2;
        b.push(box(p.x, 56, PW, 196, { stroke: 'var(--grid)', sw: 1, rx: 6 }));
        b.push(txt(xc, 44, p.title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        // 막 (가운데에 K⁺ 누출 통로 자리를 비워 둔다)
        for (const dx of [-4, 4]) {
            b.push(line([[xc + dx, 74], [xc + dx, 146]], { stroke: 'var(--ink2)', sw: 2.4 }));
            b.push(line([[xc + dx, 196], [xc + dx, 238]], { stroke: 'var(--ink2)', sw: 2.4 }));
        }
        b.push(txt(xc - 14, 70, '밖', { anchor: 'end', cls: 'ink2', size: 'sm' }));
        b.push(txt(xc + 14, 70, '안', { cls: 'ink2', size: 'sm' }));
        b.push(txt(xc - 14, 90, 'K⁺ 5', { anchor: 'end', cls: 'ink', size: 'sm' }));
        b.push(txt(xc + 14, 90, 'K⁺ 140', { cls: 'ink', size: 'sm' }));
    }
    // 패널 1
    let xc = panels[0].x + PW / 2;
    b.push(arw(xc + 52, 170, xc - 52, 170, { cls: 's1', width: 3 }));
    b.push(txt(xc - 14, 226, 'K⁺ 누출 통로', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(xc, 268, '막전위 0 mV', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(xc, 290, '농도차가 K⁺ 을 밖으로 민다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 패널 2
    xc = panels[1].x + PW / 2;
    for (const yy of [116, 140, 208, 232]) {
        b.push(txt(xc + 12, yy, '−', { cls: 'ink2' }));
        b.push(txt(xc - 12, yy, '+', { anchor: 'end', cls: 'ink2' }));
    }
    b.push(arw(xc + 52, 162, xc - 52, 162, { cls: 's1', width: 3 }));
    b.push(arw(xc - 34, 186, xc + 34, 186, { cls: 's2', width: 2 }));
    b.push(txt(xc, 268, '막전위 −40 mV', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(xc, 290, '나간 만큼 안쪽에 음전하가 쌓이고', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(xc, 306, '전기력이 도로 당기기 시작한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 패널 3
    xc = panels[2].x + PW / 2;
    for (const yy of [106, 128, 150, 200, 222, 244]) {
        b.push(txt(xc + 12, yy, '−', { cls: 'ink2' }));
        b.push(txt(xc - 12, yy, '+', { anchor: 'end', cls: 'ink2' }));
    }
    b.push(arw(xc + 52, 162, xc - 52, 162, { cls: 's1', width: 3 }));
    b.push(arw(xc - 52, 186, xc + 52, 186, { cls: 's2', width: 3 }));
    b.push(txt(xc, 268, '막전위 = E~K = −89 mV', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(xc, 290, '두 힘의 크기가 같아져', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(xc, 306, '순 이동이 멈춘다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(legend(16, 332, [{ slot: 1, name: '농도차가 미는 힘' }]));
    b.push(legend(200, 332, [{ slot: 2, name: '전기력이 당기는 힘' }]));
    b.push(txt(16, 26, '가로로 놓인 셋은 시간 순서다. 농도는 적어 둔 값(mmol/L)에서 사실상 변하지 않는다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(16, 356, '평형전위란 ‘농도차가 미는 힘과 전기력이 정확히 상쇄되는 막전위’다. 네른스트 식은 그 값을 준다', { cls: 'ink', size: 'sm' }));
    b.push(txt(16, 374, '실제로 이동한 K⁺ 은 안쪽 총량의 십만 분의 일 수준이다. 그래서 농도는 그대로이고 전위만 바뀐다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-membrane-balance',
        svg: svg({
            width: W, height: H, title: '평형전위는 두 힘이 균형을 이루는 지점이다',
            desc: '농도차가 칼륨을 밖으로 밀고, 밖으로 나간 양전하가 남긴 안쪽 음전하가 다시 안으로 당긴다. 두 힘이 같아지는 막전위가 평형전위다',
            body: b.join(''),
        }),
    };
})());

/* 16-2. 활동전위와 그 원인인 투과성 변화 */
add((() => {
    const W = 680, H = 410;
    const b = [];
    const bx1 = { x: 74, y: 60, w: 340, h: 172 };
    const g1 = frame({ xRange: [0, 6], yRange: [-100, 60], box: bx1 });
    b.push(gridAxes(g1, bx1, {
        xTicks: [0, 1, 2, 3, 4, 5, 6], yTicks: [-100, -50, 0, 50],
        yTitle: '막전위 (mV)',
    }));
    b.push(g1.line([[0, 61], [6, 61]], { cls: 's2', dash: '4 4' }));
    b.push(g1.line([[0, -89], [6, -89]], { cls: 's1', dash: '4 4' }));
    b.push(g1.line([[0, -55], [6, -55]], { cls: 's3', dash: '2 3' }));
    b.push(g1.label([6, 61], 'E~{Na} +61', { dx: -2, dy: -6, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g1.label([6, -89], 'E~K −89', { dx: -2, dy: -6, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(g1.label([0.1, -55], '역치 −55', { dx: 2, dy: -6, cls: 'ink2', size: 'sm' }));
    const ap = [[0, -70], [0.6, -70], [0.9, -62], [1.0, -55], [1.15, -40], [1.3, -10], [1.45, 20],
        [1.6, 36], [1.75, 40], [1.9, 30], [2.1, 0], [2.3, -40], [2.5, -68], [2.7, -80],
        [3.0, -82], [3.6, -78], [4.5, -73], [6, -70]];
    b.push(g1.line(ap, { cls: 's1' }));
    b.push(g1.dot([1.0, -55], { cls: 'f3', r: 4 }));
    const bx2 = { x: 74, y: 282, w: 340, h: 68 };
    const g2 = frame({ xRange: [0, 6], yRange: [0, 1.1], box: bx2 });
    b.push(gridAxes(g2, bx2, {
        xTicks: [0, 1, 2, 3, 4, 5, 6], yTicks: [0, 1],
        yTitle: '상대 투과성', xTitle: '시간 (ms)',
    }));
    b.push(g2.line([[0, 0.02], [0.85, 0.02], [1.1, 0.4], [1.35, 1.0], [1.6, 0.75], [1.9, 0.3],
        [2.2, 0.08], [2.6, 0.02], [6, 0.02]], { cls: 's2' }));
    b.push(g2.line([[0, 0.05], [1.2, 0.06], [1.6, 0.2], [2.0, 0.5], [2.4, 0.78], [2.8, 0.7],
        [3.4, 0.4], [4.2, 0.15], [5.2, 0.06], [6, 0.05]], { cls: 's1' }));
    b.push(g2.label([1.35, 1.0], 'P~{Na}', { dx: 4, dy: 2, cls: 'ink2', size: 'sm' }));
    b.push(g2.label([2.5, 0.78], 'P~K', { dx: 6, dy: -4, cls: 'ink2', size: 'sm' }));
    const T = [
        ['읽는 순서', 'ink bold'],
        ['① 자극이 막을 역치까지 밀어 올린다', 'ink'],
        ['② Na⁺ 통로가 열리고, 그 탈분극이', 'ink'],
        ['   더 많은 통로를 연다 (양성 되먹임)', 'ink2'],
        ['③ Na⁺ 통로가 스스로 닫히고(불활성화)', 'ink'],
        ['   느린 K⁺ 통로가 열려 내려온다', 'ink2'],
        ['④ K⁺ 통로가 늦게 닫혀 잠시 E~K 쪽으로', 'ink'],
        ['   더 내려간다 (과분극)', 'ink2'],
        ['', 'ink2'],
        ['아래 그림이 위 그림의 원인이다.', 'ink bold'],
        ['막전위는 그때그때 투과성이 큰 이온의', 'ink2'],
        ['평형전위 쪽으로 끌려간다. 정점이', 'ink2'],
        ['E~{Na} 에 못 미치는 것은 K⁺ 투과성이', 'ink2'],
        ['0 이 아니기 때문이다.', 'ink2'],
        ['', 'ink2'],
        ['자극이 아무리 세도 정점의 높이는', 'ink'],
        ['같다. 세기는 높이가 아니라 발화', 'ink'],
        ['빈도로 전달된다.', 'ink'],
    ];
    T.forEach(([s, c], i) => { if (s) b.push(txt(444, 66 + i * 18, s, { cls: c, size: 'sm' })); });
    b.push(txt(30, 26, '가로 점선 둘은 앞 그림에서 구한 평형전위다. 곡선은 그 사이를 오간다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 402, '불응기 동안에는 새 활동전위가 생기지 않는다. 그래서 신호가 한 방향으로만 진행한다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-action-potential',
        svg: svg({
            width: W, height: H, title: '활동전위와 그것을 만드는 투과성 변화',
            desc: '막전위는 나트륨과 칼륨의 평형전위 사이를 오가며, 어느 쪽으로 끌려가는지는 그때그때의 상대 투과성이 정한다',
            body: b.join(''),
        }),
    };
})());

/* 16-3. 헤모글로빈 산소해리곡선 */
add((() => {
    const W = 680, H = 340;
    const b = [];
    const bx = { x: 74, y: 60, w: 330, h: 206 };
    const g = frame({ xRange: [0, 14], yRange: [0, 1], box: bx });
    b.push(g.axes({
        xLabel: '', yLabel: '포화도 Y',
        xTicks: [0, 2, 4, 6, 8, 10, 12, 14], yTicks: [0, 0.25, 0.5, 0.75, 1],
    }));
    const hill = (p50, n) => p => (p ** n) / (p50 ** n + p ** n);
    b.push(g.curve(hill(3.5, 2.8), { cls: 's1', steps: 160 }));
    b.push(g.curve(hill(4.5, 2.8), { cls: 's2', dash: '5 4', steps: 160 }));
    b.push(g.curve(hill(0.4, 1.0), { cls: 's3', steps: 160 }));
    for (const [p, l1, l2] of [[2.7, '운동 중', '조직'], [5.3, '안정 시', '조직'], [13.3, '폐', '']]) {
        b.push(g.guide([p, 0], [p, 1]));
        b.push(txt(g.X(p), 36, l1, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        if (l2) b.push(txt(g.X(p), 50, l2, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    for (const p of [2.7, 5.3, 13.3]) b.push(g.dot([p, hill(3.5, 2.8)(p)], { cls: 'f1', r: 4.5 }));
    b.push(g.dot([2.7, hill(4.5, 2.8)(2.7)], { cls: 'f2', r: 4.5 }));
    b.push(txt(bx.x + 165, 300, '산소 분압 (kPa)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const T = [
        ['같은 곡선을 두 지점에서 읽는다.', 'ink bold'],
        ['폐 13.3 kPa → 0.98', 'ink2'],
        ['안정 시 조직 5.3 kPa → 0.76', 'ink2'],
        ['차이는 0.21. 실은 것의 21% 만', 'ink'],
        ['내려놓고 돌아온다.', 'ink'],
        ['', 'ink2'],
        ['운동 중 조직은 2.7 kPa 로 내려가고,', 'ink2'],
        ['pH 가 낮아져 곡선이 오른쪽으로', 'ink2'],
        ['옮겨간다(점선). 두 효과를 합치면', 'ink2'],
        ['0.78 을 내려놓는다.', 'ink'],
        ['', 'ink2'],
        ['마이오글로빈은 협동성이 없어', 'ink2'],
        ['쌍곡선이고, 아주 낮은 분압에서야', 'ink2'],
        ['내놓는다. 그래서 저장고 노릇을 한다.', 'ink2'],
    ];
    T.forEach(([s, c], i) => { if (s) b.push(txt(430, 78 + i * 17, s, { cls: c, size: 'sm' })); });
    b.push(legend(74, 328, [{ slot: 1, name: '성인 헤모글로빈' }]));
    b.push(legend(220, 328, [{ slot: 2, name: '오른쪽으로 옮겨간 상태' }]));
    b.push(legend(430, 328, [{ slot: 3, name: '마이오글로빈' }]));
    b.push(txt(30, 20, 'S 자 곡선의 가파른 구간이 조직의 분압 범위에 놓여 있다. 그래서 작은 변화가 큰 방출을 만든다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-hb-curve',
        svg: svg({
            width: W, height: H, title: '산소해리곡선을 두 지점에서 읽는다',
            desc: '헤모글로빈의 S 자 곡선에서 폐와 조직의 분압을 읽어 그 차이가 곧 내려놓는 양이 된다. pH 가 낮아지면 곡선이 오른쪽으로 옮겨가 방출이 늘어난다',
            body: b.join(''),
        }),
    };
})());

/* 16-4. 근절의 활주와 길이-장력 곡선 */
add((() => {
    const W = 700, H = 328;
    const b = [];
    const M = 170, A = 110;
    const sarco = (x, y, L) => {
        const s = [];
        s.push(line([[x, y - 34], [x, y + 34]], { sw: 3.4 }));
        s.push(line([[x + L, y - 34], [x + L, y + 34]], { sw: 3.4 }));
        const m0 = x + (L - M) / 2, m1 = m0 + M;
        s.push(line([[m0, y], [m1, y]], { stroke: 'var(--s2)', sw: 7 }));
        for (let t = m0 + 10; t < m1 - 8; t += 15) {
            s.push(line([[t, y - 3], [t + 5, y - 10]], { stroke: 'var(--s2)', sw: 1.6 }));
            s.push(line([[t, y + 3], [t + 5, y + 10]], { stroke: 'var(--s2)', sw: 1.6 }));
        }
        for (const dy of [-13, 13]) {
            s.push(line([[x, y + dy], [x + A, y + dy]], { stroke: 'var(--s1)', sw: 2.6 }));
            s.push(line([[x + L, y + dy], [x + L - A, y + dy]], { stroke: 'var(--s1)', sw: 2.6 }));
        }
        return s.join('');
    };
    b.push(sarco(46, 106, 280));
    b.push(sarco(46, 196, 200));
    b.push(txt(46, 76, 'A. 이완 — 근절 2.2 µm', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(46, 166, 'B. 수축 — 근절 1.8 µm', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(340, 111, '겹침이 좁다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(260, 201, '겹침이 넓다', { cls: 'ink2', size: 'sm' }));
    b.push(legend(40, 250, [{ slot: 1, name: '액틴 (가는 필라멘트)' }]));
    b.push(legend(206, 250, [{ slot: 2, name: '미오신 (굵은 필라멘트)' }]));
    b.push(txt(40, 272, '두 필라멘트의 길이는 A 와 B 에서 똑같다. 변하는 것은 겹치는 정도뿐이다', { cls: 'ink', size: 'sm' }));
    b.push(txt(40, 290, '근절이 너무 길면 겹침이 모자라고, 너무 짧으면 필라멘트가 서로 방해한다', { cls: 'ink2', size: 'sm' }));
    const bx = { x: 452, y: 74, w: 196, h: 150 };
    const g = frame({ xRange: [1.2, 3.8], yRange: [0, 1.1], box: bx });
    b.push(g.axes({ xTicks: [1.5, 2, 2.5, 3, 3.5], yTicks: [0, 0.5, 1] }));
    b.push(g.line([[1.3, 0], [1.6, 0.5], [1.9, 0.95], [2.0, 1.0], [2.25, 1.0], [3.65, 0]], { cls: 's3' }));
    b.push(g.label([2.12, 1.0], '고원', { dy: -8, anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(550, 250, '근절 길이 (µm)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(452, 56, '상대 장력', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '장력은 동시에 힘을 내는 교차다리의 수, 곧 겹침 구간의 길이에 비례한다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 312, '오른쪽 곡선의 내림 구간 기울기에서 얻은 필라멘트 길이가 전자현미경 값과 맞는다. 활주설의 강한 증거다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-sarcomere',
        svg: svg({
            width: W, height: H, title: '활주설과 길이-장력 곡선',
            desc: '필라멘트의 길이는 변하지 않고 겹치는 정도만 변한다. 겹침이 최대인 중간 길이에서 장력이 가장 크다',
            body: b.join(''),
        }),
    };
})());

/* ================================================================== *
 * 17장 — 생태학
 * ================================================================== */

/* 17-1. 지수와 로지스틱, 그리고 한 해의 증가량 */
add((() => {
    const W = 700, H = 334;
    const b = [];
    const bx1 = { x: 68, y: 76, w: 240, h: 186 };
    const g1 = frame({ xRange: [0, 50], yRange: [0, 1250], box: bx1 });
    b.push(g1.axes({ xTicks: [0, 10, 20, 30, 40, 50], yTicks: [0, 500, 1000] }));
    b.push(g1.line([[0, 1000], [50, 1000]], { cls: 's3', dash: '5 4' }));
    b.push(g1.curve(t => 1000 / (1 + 19 * Math.exp(-0.2 * t)), { cls: 's1', steps: 140 }));
    b.push(g1.curve(t => 50 * Math.exp(0.2 * t), { from: 0, to: 16, cls: 's2', dash: '5 4', steps: 100 }));
    b.push(g1.dot([15, 500], { cls: 'f1', r: 4.5 }));
    b.push(g1.label([2, 1000], 'K = 1000', { dy: -7, cls: 'ink2', size: 'sm' }));
    b.push(g1.label([15, 500], 'N = K/2', { dx: 6, dy: 14, cls: 'ink', size: 'sm' }));
    b.push(g1.label([19, 1180], '자원이 무제한이면', { dx: 0, dy: 0, cls: 'ink2', size: 'sm' }));
    b.push(txt(188, 296, '시간 (년)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(68, 62, '개체 수 N', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const bx2 = { x: 420, y: 76, w: 216, h: 186 };
    const g2 = frame({ xRange: [0, 1100], yRange: [0, 62], box: bx2 });
    b.push(g2.axes({ xTicks: [0, 500, 1000], yTicks: [0, 25, 50] }));
    b.push(g2.curve(N => 0.2 * N * (1 - N / 1000), { from: 0, to: 1000, cls: 's1', steps: 140 }));
    b.push(g2.dot([500, 50], { cls: 'f1', r: 4.5 }));
    b.push(g2.guide([500, 0], [500, 50]));
    b.push(g2.label([500, 50], '가장 많이 느는 지점', { dy: -9, anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(g2.label([1000, 2], 'N = K 에서 0', { dx: -4, dy: -6, anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(528, 296, '현재 개체 수 N', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(420, 62, '한 해의 증가량', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(legend(68, 322, [{ slot: 1, name: '로지스틱' }]));
    b.push(legend(178, 322, [{ slot: 2, name: '지수 (상한이 없다)' }]));
    b.push(legend(360, 322, [{ slot: 3, name: 'K — 환경수용력' }]));
    b.push(txt(30, 26, '왼쪽은 개체 수의 시간 변화, 오른쪽은 같은 개체군이 한 해에 얼마나 느는가다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 44, '오른쪽 그래프의 높이를 읽어 더해 나가면 왼쪽 곡선이 나온다. 미분을 쓰지 않아도 된다', { cls: 'ink', size: 'sm' }));
    return {
        name: 'bio-org-logistic',
        svg: svg({
            width: W, height: H, title: '로지스틱 성장을 증가량으로 읽는다',
            desc: '개체 수가 K/2 일 때 한 해의 증가량이 최대가 되고, K 에 닿으면 0 이 된다. 증가량 곡선을 더해 나가면 S 자 곡선이 나온다',
            body: b.join(''),
        }),
    };
})());

/* 17-2. 생존곡선 세 유형 */
add((() => {
    const W = 680, H = 326;
    const b = [];
    const bx = { x: 84, y: 68, w: 300, h: 186 };
    const g = frame({ xRange: [0, 100], yRange: [0, 3], box: bx });
    b.push(gridAxes(g, bx, {
        xTicks: [0, 25, 50, 75, 100], yTicks: [0, 1, 2, 3],
        xFmt: v => `${v}%`, yFmt: v => ['1', '10', '100', '1000'][v],
        yTitle: '살아남은 수',
    }));
    b.push(g.line([[0, 3], [30, 2.98], [50, 2.93], [70, 2.78], [82, 2.5], [90, 1.9], [96, 1.0], [100, 0]], { cls: 's1' }));
    b.push(g.line([[0, 3], [100, 0]], { cls: 's2' }));
    b.push(g.line([[0, 3], [2, 1.75], [5, 1.35], [10, 1.12], [30, 0.88], [60, 0.62], [100, 0]], { cls: 's3' }));
    b.push(g.label([60, 2.86], 'I 형', { cls: 'ink bold', size: 'sm' }));
    b.push(g.label([62, 1.35], 'II 형', { cls: 'ink bold', size: 'sm' }));
    b.push(g.label([30, 0.9], 'III 형', { dy: -8, cls: 'ink bold', size: 'sm' }));
    b.push(txt(234, 290, '수명 대비 나이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const T = [
        ['세로 눈금은 한 칸이 10배인 로그', 'ink2'],
        ['눈금이다. 그래서 사망률이 늘 일정한', 'ink2'],
        ['II 형이 곧은 직선으로 보인다.', 'ink2'],
        ['', 'ink2'],
        ['I 형 — 어릴 때 거의 죽지 않고 노년에', 'ink'],
        ['   몰려 죽는다. 자손이 적고 오래 돌본다.', 'ink2'],
        ['III 형 — 어릴 때 대부분 죽고 살아남은', 'ink'],
        ['   소수가 오래 산다. 자손이 아주 많다.', 'ink2'],
        ['', 'ink2'],
        ['곡선의 모양이 곧 그 종이 어디에', 'ink'],
        ['투자했는지를 말해 준다.', 'ink'],
    ];
    T.forEach(([s, c], i) => { if (s) b.push(txt(404, 84 + i * 18, s, { cls: c, size: 'sm' })); });
    b.push(txt(30, 26, '세로축은 처음 1000 마리 중 그 나이까지 살아남은 수다. 로그 눈금이라야 세 유형이 모양으로 갈린다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 314, '유형은 연속적이며, 한 종의 곡선이 구간마다 다른 유형을 보이는 것도 흔하다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-survivorship',
        svg: svg({
            width: W, height: H, title: '생존곡선 세 유형',
            desc: '세로축을 로그 눈금으로 두면 사망률이 일정한 II 형이 직선이 되고, I 형과 III 형이 그 위아래로 갈린다',
            body: b.join(''),
        }),
    };
})());

/* 17-3. 두 종류의 피라미드 */
add((() => {
    const W = 700, H = 336;
    const b = [];
    const trap = (cx, yb, h, wb, wt, cls) => poly(
        [[cx - wb / 2, yb], [cx + wb / 2, yb], [cx + wt / 2, yb - h], [cx - wt / 2, yb - h]],
        { fill: `var(--${cls})`, op: 0.25, stroke: `var(--${cls})`, sw: 1.4 },
    );
    // 에너지 피라미드 — 값에 비례한 폭
    const cx = 130;
    const evals = [[10000, '생산자 1.0 × 10⁴'], [1000, '1차 소비자 1.0 × 10³'],
        [100, '2차 소비자 1.0 × 10²'], [10, '3차 소비자 1.0 × 10¹']];
    evals.forEach(([v, name], i) => {
        const w = Math.max((v / 10000) * 200, 1.6);
        const yb = 276 - i * 34;
        b.push(box(cx - w / 2, yb - 30, w, 30, { fill: 'var(--s1)', op: 0.28, stroke: 'var(--s1)', sw: 1.4, rx: 2 }));
        b.push(line([[cx + w / 2 + 2, yb - 15], [246, yb - 15]], { stroke: 'var(--grid)', sw: 1 }));
        b.push(txt(250, yb - 11, name, { cls: 'ink2', size: 'sm' }));
    });
    b.push(txt(130, 66, '에너지 피라미드', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(130, 84, '단위 kJ/(m² · yr)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(130, 102, '뒤집힐 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    // 생물량 / 생산량
    b.push(trap(470, 276, 30, 20, 20, 's3'));
    b.push(trap(470, 242, 30, 100, 100, 's3'));
    b.push(txt(470, 265, '식물플랑크톤 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(470, 231, '동물플랑크톤 20', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(trap(612, 276, 30, 100, 100, 's2'));
    b.push(trap(612, 242, 30, 20, 20, 's2'));
    b.push(txt(612, 265, '400', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(612, 231, '80', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(470, 194, '생물량 g/m²', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(470, 210, '(뒤집혀 있다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(612, 194, '생산량 g/(m² · yr)', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(612, 210, '(정상이다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 130, '같은 바다에서 잰 두 피라미드', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(540, 150, '재는 대상이 다르다.', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(540, 166, '하나는 순간의 재고, 하나는 시간당 흐름이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '왼쪽은 값에 비례해 그렸다. 위의 두 칸이 선처럼 얇은 것이 이 그림의 요점이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 306, '열역학이 제약하는 것은 흐름이다. 생물량이 뒤집혀도 생산량이 뒤집히지 않으면 모순이 없다', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 324, '식물플랑크톤은 나흘이 못 되어 몸 전체가 교체된다. 적은 재고로 큰 흐름을 낸다', { cls: 'ink2', size: 'sm' }));
    return {
        name: 'bio-org-pyramid',
        svg: svg({
            width: W, height: H, title: '에너지 피라미드와 뒤집힌 생물량 피라미드',
            desc: '영양단계마다 10분의 1 만 남으므로 에너지 피라미드는 뒤집힐 수 없다. 반면 회전이 빠른 생산자는 적은 현존량으로 큰 생산량을 내므로 생물량 피라미드는 뒤집힐 수 있다',
            body: b.join(''),
        }),
    };
})());

/* 17-4. 종-면적 관계 */
add((() => {
    const W = 660, H = 322;
    const b = [];
    const bx1 = { x: 76, y: 74, w: 214, h: 176 };
    const g1 = frame({ xRange: [0, 1], yRange: [0, 1.05], box: bx1 });
    b.push(g1.axes({ xTicks: [0, 0.25, 0.5, 0.75, 1], yTicks: [0, 0.5, 1] }));
    for (const [z, cls, dash] of [[0.15, 's2', '5 4'], [0.25, 's1', null], [0.35, 's3', '5 4']]) {
        b.push(g1.curve(A => A ** z, { from: 0.002, to: 1, cls, dash, steps: 160 }));
        b.push(g1.dot([0.10, 0.10 ** z], { cls: cls === 's1' ? 'f1' : cls === 's2' ? 'f2' : 'f3', r: 4 }));
    }
    b.push(g1.guide([0.1, 0], [0.1, 0.72]));
    b.push(g1.label([0.30, 0.30], '면적을 10% 로 줄이면', { cls: 'ink', size: 'sm' }));
    b.push(g1.label([0.30, 0.19], '종은 절반 안팎이 남는다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(183, 48, '보통 축 — 곡선이 휜다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(76, 66, 'S/S₀', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(183, 284, '남은 면적의 비 A/A₀', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    const bx2 = { x: 410, y: 74, w: 214, h: 176 };
    const g2 = frame({ xRange: [-2, 0], yRange: [-0.8, 0], box: bx2 });
    b.push(gridAxes(g2, bx2, {
        xTicks: [-2, -1, 0], yTicks: [-0.8, -0.6, -0.4, -0.2, 0],
        xFmt: v => ['1%', '10%', '100%'][v + 2], yFmt: v => (10 ** v).toFixed(2),
        xTitle: '남은 면적의 비 (한 칸이 10배)',
    }));
    for (const [z, cls] of [[0.15, 's2'], [0.25, 's1'], [0.35, 's3']]) {
        b.push(g2.line([[-2, -2 * z], [0, 0]], { cls }));
        b.push(g2.label([-2, -2 * z], `z = ${z.toFixed(2)}`, { dx: 6, dy: -5, cls: 'ink2', size: 'sm' }));
    }
    b.push(txt(517, 48, '로그-로그 축 — 직선이 되고 기울기가 z 다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(410, 66, 'S/S₀', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '지수가 1 보다 훨씬 작아서, 면적을 90% 잃어도 종은 그만큼 잃지 않는다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 310, '문제는 z 를 하나로 정할 수 없다는 것이다. 답은 언제나 폭과 함께 말해야 한다', { cls: 'ink', size: 'sm' }));
    return {
        name: 'bio-org-species-area',
        svg: svg({
            width: W, height: H, title: '종-면적 관계와 지수 z',
            desc: '종 수는 면적의 거듭제곱을 따르며, 로그-로그 축에서 직선이 되고 그 기울기가 지수 z 다. z 의 폭이 그대로 추정치의 폭이 된다',
            body: b.join(''),
        }),
    };
})());

/* 17-5. 기본 지위와 실현 지위 */
add((() => {
    const W = 680, H = 336;
    const b = [];
    b.push(arw(118, 276, 118, 84, { cls: 'ark', width: 1.6 }));
    b.push(txt(110, 90, '높은 곳', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(110, 106, '(오래 드러난다)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(110, 262, '낮은 곳', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(110, 278, '(거의 잠겨 있다)', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    const bar = (x, y0v, y1v, cls, dash) => box(x, y0v, 44, y1v - y0v,
        { fill: dash ? 'none' : `var(--${cls})`, op: 0.25, stroke: `var(--${cls})`, sw: 1.8, rx: 3, dash });
    b.push(bar(170, 92, 250, 's1'));
    b.push(bar(240, 152, 272, 's2'));
    b.push(bar(410, 92, 152, 's1'));
    b.push(bar(410, 152, 250, 's1', '5 4'));
    b.push(bar(480, 152, 272, 's2'));
    b.push(txt(192, 84, 'A', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(262, 144, 'B', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(432, 84, 'A', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(502, 144, 'B', { anchor: 'middle', cls: 'ink bold' }));
    b.push(txt(227, 62, '따로 두었을 때 = 기본 지위', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(467, 62, '함께 있을 때 = 실현 지위', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    b.push(txt(404, 200, '경쟁으로 잃은 구간', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(548, 100, '판정 규칙', { cls: 'ink bold', size: 'sm' }));
    b.push(txt(548, 122, '상대를 없앴을 때', { cls: 'ink2', size: 'sm' }));
    b.push(txt(548, 138, '넓어지면 경쟁이,', { cls: 'ink2', size: 'sm' }));
    b.push(txt(548, 154, '그대로면 물리적', { cls: 'ink2', size: 'sm' }));
    b.push(txt(548, 170, '조건이 정한', { cls: 'ink2', size: 'sm' }));
    b.push(txt(548, 186, '경계다.', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 26, '실현 지위는 언제나 기본 지위의 부분집합이다. 두 지위의 차이를 만든 것이 무엇인지가 질문이다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(30, 300, 'A 의 아래쪽 경계 — B 를 없애면 A 가 아래로 퍼졌다. 경쟁이 정한 경계다', { cls: 'ink', size: 'sm' }));
    b.push(txt(30, 320, 'B 의 위쪽 경계 — A 를 없애도 B 는 올라가지 않았다. 건조를 견디지 못하는 것이다', { cls: 'ink', size: 'sm' }));
    return {
        name: 'bio-org-niche',
        svg: svg({
            width: W, height: H, title: '기본 지위와 실현 지위',
            desc: '조간대 따개비 두 종의 분포. 경쟁자를 제거했을 때 분포가 넓어지면 그 경계는 경쟁이, 넓어지지 않으면 물리적 조건이 만든 것이다',
            body: b.join(''),
        }),
    };
})());

export default figures;
