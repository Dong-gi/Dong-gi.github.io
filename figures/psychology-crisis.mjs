/**
 * 심리학 문서 6장(재현 위기)의 그림.
 *
 * 이름은 모두 `psy-c-` 로 시작한다(6장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 `~` 를 그냥 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 자료를 다루는 규칙 — 이 파일에는 <b>남의 원자료를 옮긴 그림이 하나도 없다.</b>
 * Open Science Collaboration(2015)에서 가져온 것은 논문이 보고한 <b>요약값</b>
 * (원 연구 평균 r = 0.403, 재현 평균 r = 0.197, 82.8%)뿐이고, 흩어진 점들은
 * 씨앗을 고정한 난수로 만든 도식이다. 문서의 캡션에도 그렇게 적었다.
 *
 * 상자와 화살표만으로 되는 그림(유인 구조의 고리·갈림길·대응표)은
 * d2/psychology/psy-c-*.d2 에 있다.
 */
import { svg, frame, txt } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = { s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)' };

function ln(pts, { stroke = COL.ink2, sw = 1.8, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function box(x, y, w, h, { fill = 'none', op = 1, stroke = COL.ink2, sw = 1.4, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(Math.max(0, w))}" height="${r2(Math.max(0, h))}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = COL.s1, r = 4) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

function rng(seed) {
    let s = seed >>> 0;
    return () => {
        s = (s * 1664525 + 1013904223) >>> 0;
        return s / 4294967296;
    };
}

function gauss(rand) {
    const u = Math.max(rand(), 1e-9);
    const v = rand();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

/* ------------------------------------------------------------------ *
 * psy-c-osc — 원 연구와 재현 연구의 효과크기.
 *
 * 무엇을 읽어야 하나: 대각선 아래에 점이 몰려 있다는 것. 재현이 실패했다기보다
 * 재현했더니 효과가 작아졌다는 것이 이 그림이 말하는 바다.
 * 굵은 점 두 개(평균)만 논문이 보고한 값이고, 작은 점들은 도식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 400;
    const g = [];
    const rand = rng(20150828);
    const f = frame({ xRange: [0, 0.9], yRange: [-0.25, 0.9], box: { x: 100, y: 74, w: 340, h: 260 } });

    g.push(f.axes({ xTicks: [0, 0.3, 0.6, 0.9], yTicks: [-0.25, 0, 0.3, 0.6, 0.9], grid: true }));
    g.push(txt(f.X(0.45), 366, '원 연구의 효과크기', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 62, '재현 연구의 효과크기', { cls: 'ink2', size: 'sm' }));

    // 대각선: 원 연구와 재현이 같은 크기로 나온 자리.
    g.push(f.line([[0, 0], [0.9, 0.9]], { cls: 'ax', dash: '5 4' }));
    g.push(txt(f.X(0.78), f.Y(0.84), '같은 크기', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    for (let i = 0; i < 82; i += 1) {
        const o = 0.15 + rand() * 0.62;
        const r = 0.48 * o + gauss(rand) * 0.19;
        g.push(f.dot([o, Math.max(-0.23, Math.min(0.88, r))], { cls: 'f1', r: 3 }));
    }
    g.push(f.dot([0.403, 0.197], { cls: 'f2', r: 8 }));
    g.push(txt(f.X(0.403) + 14, f.Y(0.197) + 5, '평균 (0.403, 0.197)', { cls: 'ink bold', size: 'sm' }));

    g.push(box(478, 84, 330, 118, { stroke: COL.s2, sw: 1.6 }));
    g.push(txt(494, 110, '논문이 보고한 값', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(494, 134, '원 연구 100편의 평균 효과크기 0.403,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 154, '재현 연구의 평균 0.197. 100편 가운데 83편에서', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 174, '원 연구 쪽이 더 컸다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 194, 'Open Science Collaboration (2015)', { cls: 'ink2', size: 'sm' }));

    g.push(box(478, 220, 330, 114, { stroke: COL.grid, sw: 1.4 }));
    g.push(txt(494, 246, '작은 점은 도식이다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(494, 270, '개별 연구의 원자료를 옮긴 것이 아니라', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 290, '위의 요약값과 같은 모양이 되도록 만든', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 310, '점들이다. 대각선 아래로 쏠린다는 것만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(494, 326, '읽으면 된다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(W / 2, 34, '재현하면 효과가 작아졌다', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 390, '효과크기는 두 변수의 관계를 −1 에서 1 사이로 적은 값이다. 0 은 관계가 없다는 뜻이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-c-osc', svg: svg({ width: W, height: H, title: '원 연구와 재현 연구의 효과크기', desc: '대각선 아래로 쏠린 산포와 보고된 두 평균값', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-c-pcurve — p 값이 문턱 바로 아래에 쌓이는 모양.
 *
 * 무엇을 읽어야 하나: 참인 효과가 있으면 p 값은 0 쪽으로 갈수록 많아진다.
 * 문턱 바로 아래에 봉우리가 생기면 그것은 자연스러운 모양이 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 340;
    const g = [];
    const panels = [
        { x: 60, t: '효과가 실제로 있을 때', bars: [30, 21, 15, 10, 7], note: '0 쪽이 두껍다' },
        { x: 470, t: '자유도를 써서 문턱을 넘긴 문헌', bars: [11, 9, 8, 9, 31], note: '문턱 바로 아래가 두껍다' },
    ];
    for (const p of panels) {
        const bw = 56;
        const x0 = p.x;
        const base = 250;
        g.push(box(x0 - 16, 72, 5 * bw + 46, 200, { stroke: COL.grid, sw: 1.2 }));
        p.bars.forEach((v, i) => {
            const h = v * 5.4;
            const col = i === 4 ? COL.s2 : COL.s1;
            g.push(`<rect x="${x0 + i * bw}" y="${base - h}" width="${bw - 8}" height="${h}" rx="2" fill="${col}" fill-opacity="0.85"/>`);
        });
        g.push(ln([[x0 - 8, base], [x0 + 5 * bw + 12, base]], { stroke: COL.ink2, sw: 1.4 }));
        ['.01', '.02', '.03', '.04', '.05'].forEach((s, i) => {
            g.push(txt(x0 + i * bw + (bw - 8) / 2, base + 20, s, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        });
        g.push(txt(x0 + 2.5 * bw, base + 44, 'p 값 (0.05 아래만 그렸다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        g.push(txt(x0 + 2.5 * bw, 60, p.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(x0 + 2.5 * bw, 314, p.note, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(W / 2, 32, 'p 값이 쌓이는 모양이 다르다 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    return { name: 'psy-c-pcurve', svg: svg({ width: W, height: H, title: 'p 값 분포의 두 모양', desc: '효과가 있을 때와 자유도를 쓴 문헌에서 p 값이 쌓이는 모양의 대비', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-c-funnel — 출판 편향이 문헌에 남기는 자국.
 *
 * 무엇을 읽어야 하나: 작은 연구 쪽(아래)에서 왼쪽 조각이 통째로 비어 있다.
 * 없는 연구가 무엇인지가 보이는 그림이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 380;
    const g = [];
    const panels = [
        { x: 70, t: '출판 편향이 없다면', drop: false, note: '깔때기가 좌우 대칭이다' },
        { x: 460, t: '유의한 것만 실린다면', drop: true, note: '왼쪽 아래 조각이 비어 있다' },
    ];
    const TRUE = 0.28;
    for (const p of panels) {
        const f = frame({ xRange: [-0.55, 1.1], yRange: [0, 1], box: { x: p.x, y: 84, w: 300, h: 200 } });
        g.push(box(p.x - 14, 76, 322, 216, { stroke: COL.grid, sw: 1.2 }));
        g.push(f.line([[TRUE, 0], [TRUE, 1]], { cls: 's3', dash: '5 4' }));
        // 세로 위치를 고르게 깔아야 깔때기 모양이 보인다. 흩어짐만 난수로 준다.
        const rand2 = rng(5150);
        for (let i = 0; i < 72; i += 1) {
            const size = i / 71;
            const spread = 0.34 * (1 - size) + 0.02;
            const d = TRUE + gauss(rand2) * spread;
            const sig = d > 1.96 * spread;
            if (p.drop && !sig) continue;
            g.push(f.dot([Math.max(-0.53, Math.min(1.08, d)), 0.04 + size * 0.92], { cls: sig ? 'f2' : 'f1', r: 3.2 }));
        }
        g.push(txt(p.x + 150, 62, p.t, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(p.x + 150, 338, p.note, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        g.push(txt(p.x + 150, 312, '가로축 — 관측된 효과크기', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    }
    g.push(txt(32, 92, '표본이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(32, 110, '큰 연구', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(ln([[32, 122], [32, 250]], { stroke: COL.grid, sw: 1.4 }));
    g.push(txt(32, 268, '표본이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(32, 286, '작은 연구', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(W / 2, 32, '없는 연구가 남기는 자국 — 도식', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(W / 2, 366, '세로 점선이 참 효과크기다. 오른쪽 그림만 보고 평균을 내면 참값보다 큰 값이 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-c-funnel', svg: svg({ width: W, height: H, title: '출판 편향의 깔때기', desc: '출판 편향이 없을 때와 있을 때의 효과크기 산포 대비', body: g.join('') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-c-multilab — 연구 하나와 다중연구실.
 *
 * 무엇을 읽어야 하나: 연구실마다 구간이 넓어 제각각으로 보여도, 합치면
 * 구간이 좁아지면서 0 근처로 모인다. 다중연구실이 판정을 내리는 방식이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 414;
    const g = [];
    const rand = rng(20180101);
    const X = v => 286 + v * 330;
    const labs = [];
    for (let i = 0; i < 11; i += 1) {
        const est = gauss(rand) * 0.16;
        const half = 0.24 + rand() * 0.14;
        labs.push({ est, half });
    }
    g.push(txt(W / 2, 32, '연구실 하나가 아니라 여럿이 같은 절차로 잰다', { anchor: 'middle', cls: 'ink bold' }));

    // 원 연구
    g.push(txt(30, 76, '원 연구', { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[X(0.35), 72], [X(1.05), 72]], { stroke: COL.s2, sw: 2.4 }));
    g.push(ln([[X(0.35), 66], [X(0.35), 78]], { stroke: COL.s2, sw: 2.4 }));
    g.push(ln([[X(1.05), 66], [X(1.05), 78]], { stroke: COL.s2, sw: 2.4 }));
    g.push(pdot(X(0.7), 72, COL.s2, 5.5));
    g.push(txt(X(1.05) + 12, 77, '표본이 작아 구간이 넓다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(X(0), 106, '효과 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(ln([[24, 96], [796, 96]], { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(30, 120, '되풀이한 연구실들', { cls: 'ink bold', size: 'sm' }));

    labs.forEach((l, i) => {
        const y = 138 + i * 19;
        g.push(ln([[X(l.est - l.half), y], [X(l.est + l.half), y]], { stroke: COL.ink2, sw: 1.5 }));
        g.push(pdot(X(l.est), y, COL.s1, 3.6));
    });

    const yAll = 138 + 11 * 19 + 14;
    g.push(ln([[24, yAll - 14], [796, yAll - 14]], { stroke: COL.grid, sw: 1.2 }));
    g.push(txt(30, yAll + 5, '전부 합치면', { cls: 'ink bold', size: 'sm' }));
    g.push(ln([[X(-0.055), yAll], [X(0.055), yAll]], { stroke: COL.s3, sw: 3.4 }));
    g.push(pdot(X(0), yAll, COL.s3, 5.5));
    g.push(txt(X(0.075), yAll + 5, '구간이 좁아지고 0 을 감싼다', { cls: 'ink', size: 'sm' }));

    // 0 기준선
    g.push(ln([[X(0), 114], [X(0), yAll + 16]], { stroke: COL.grid, sw: 1.4, dash: '4 4' }));
    g.push(txt(W / 2, 400, '가로 막대는 추정값의 불확실성 구간이다. 구간이 0 을 감싸면 효과가 있다고 말할 수 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return { name: 'psy-c-multilab', svg: svg({ width: W, height: H, title: '다중연구실 재현의 모양', desc: '원 연구 하나와 여러 연구실의 추정 구간, 그리고 합친 구간', body: g.join('') }) };
})());

export default figures;
