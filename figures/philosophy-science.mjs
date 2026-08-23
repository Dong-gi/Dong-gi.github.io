/**
 * 철학 문서 8장(귀납은 정당한가)·9장(과학철학)의 그림.
 *
 * 이름은 모두 `phi-s-` 로 시작한다(8·9장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 두 장의 그림은 두 갈래다. 여기 있는 것은 좌표·영역·격자 쪽이고,
 * 논증 구조와 입장 지도는 d2/philosophy/phi-s-*.d2 에 있다.
 * 귀납과 과학철학은 ‘같은 자료가 여러 결론을 허락한다’ 가 되풀이되는 주제라,
 * 그 사실은 말보다 겹쳐 그린 곡선 하나가 훨씬 빨리 전달한다.
 */
import { svg, frame, txt, px, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const C1 = 'var(--s1)';
const C2 = 'var(--s2)';
const C3 = 'var(--s3)';
const CK = 'var(--ink2)';
const CG = 'var(--grid)';

/* ------------------------------------------------------------------ *
 * 화소 좌표 소도구
 * ------------------------------------------------------------------ */

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 4, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/** 꺾은선. 화살촉이 없다. */
function ln(pts, { stroke = CK, sw = 1.5, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}"`
        + ` stroke-linecap="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

const pdot = (x, y, col = C2, r = 5) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

/* ================================================================== *
 * 8장 — 귀납
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. 그루 — 같은 자료, 갈라지는 예측
 *
 * 그루 수수께끼에서 가장 자주 어긋나는 이해는 ‘그루 가설은 과거 자료와
 * 안 맞는다’ 는 것이다. 왼쪽 절반이 두 가설에 공통이라는 사실을 눈으로
 * 보여 주면 그 오해가 생기지 않는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 782;
    const H = 336;
    const g = [];

    g.push(txt(36, 26, '같은 자료에서 갈라지는 두 예측', { cls: 'ink bold' }));

    // 시간축
    g.push(px(58, 172, 742, 172, { cls: 'ax', marker: 'ark', width: 1.4 }));
    g.push(txt(742, 192, '시각', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    // 경계
    g.push(ln([[400, 74], [400, 266]], { stroke: CG, sw: 1.4, dash: '5 4' }));
    g.push(txt(400, 66, '시각 t', { anchor: 'middle', cls: 'ink bold' }));

    // t 이전의 관찰
    for (let i = 0; i < 8; i += 1) g.push(pdot(100 + i * 36, 172, C3, 5.5));
    g.push(txt(96, 150, '지금까지 관찰한 에메랄드 — 모두 초록이고, 모두 그루다', { cls: 'ink', size: 'sm' }));

    // 두 갈래
    g.push(ln([[400, 172], [700, 124]], { stroke: C3, sw: 2.2 }));
    g.push(ln([[400, 172], [700, 232]], { stroke: C1, sw: 2.2, dash: '7 5' }));
    for (const x of [520, 600, 680]) {
        g.push(pdot(x, 172 - (46 * (x - 400)) / 300, C3, 5.5));
        g.push(pdot(x, 172 + (60 * (x - 400)) / 300, C1, 5.5));
    }
    g.push(txt(556, 110, '‘초록’ 을 투사하면 — 앞으로도 초록', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(556, 258, '‘그루’ 를 투사하면 — 앞으로는 파랑', { anchor: 'middle', cls: 'ink', size: 'sm' }));

    g.push(txt(36, 296, '그루 — 시각 t 이전에 관찰되었고 초록이거나, 그렇지 않고 파랑인', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 316, '왼쪽 절반이 두 가설에 똑같다. 자료가 아니라 술어를 고르는 일이 예측을 가른다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-s-grue-timeline',
        svg: svg({
            width: W, height: H,
            title: '그루 — 같은 자료에서 갈라지는 두 예측',
            desc: '시각 t 이전의 관찰은 초록 가설과 그루 가설에 똑같이 맞고, t 이후의 예측만 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 까마귀의 역설 — 영역의 크기
 *
 * ‘흰 구두가 까마귀 가설을 확증한다’ 가 왜 견딜 만한 결론이 되는지는
 * 두 영역의 크기 차이를 보아야 납득된다. 표로는 전달되지 않는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 782;
    const H = 348;
    const g = [];

    g.push(txt(36, 26, '까마귀의 역설 — 어느 쪽을 훑고 있는가', { cls: 'ink bold' }));

    g.push(box(40, 52, 700, 190, { stroke: CK, sw: 1.4 }));
    g.push(txt(730, 72, '세상의 모든 것', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(box(60, 74, 250, 150, { fill: C1, op: 0.10, stroke: C1, sw: 1.6 }));
    g.push(txt(72, 96, '검은 것', { cls: 'ink bold', size: 'sm' }));

    g.push(box(84, 120, 160, 84, { fill: C3, op: 0.16, stroke: C3, sw: 1.6 }));
    g.push(txt(96, 146, '까마귀', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(96, 168, '가설이 참이면', { cls: 'ink2', size: 'sm' }));
    g.push(txt(96, 186, '이 안에 갇힌다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(360, 122, '검지 않은 것 — 흰 구두, 붉은 연필, 물, 하늘, …', { cls: 'ink', size: 'sm' }));
    g.push(txt(360, 146, '이 영역이 압도적으로 크다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(360, 172, '여기서 하나를 집어 까마귀가 아님을 확인하는 것도', { cls: 'ink2', size: 'sm' }));
    g.push(txt(360, 190, '가설을 시험한 것이기는 하다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(36, 274, '니코 조건 — 검은 까마귀 하나는 ‘모든 까마귀는 검다’ 를 확증한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 296, '동치 조건 — 같은 것을 말하는 두 문장은 같은 관찰로 확증된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 318, '둘을 합치면 흰 구두 하나도 확증한다. 남는 물음은 ‘얼마나’ 다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'phi-s-raven-domain',
        svg: svg({
            width: W, height: H,
            title: '까마귀의 역설에서 두 영역의 크기',
            desc: '까마귀는 좁고 검지 않은 것은 넓다. 확증의 몫이 갈리는 자리가 여기다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 베이즈 갱신 — 사전 확률이 다른 세 사람
 *
 * 수렴 정리가 무엇을 보장하고 무엇을 보장하지 않는지를 한 장에 담는다.
 * 맨 아래 평평한 선(사전 확률 0)이 이 그림의 요점이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 770;
    const H = 356;
    const f = frame({ xRange: [0, 24], yRange: [0, 1], box: { x: 86, y: 68, w: 596, h: 212 } });
    const g = [];

    g.push(txt(36, 24, '증거가 쌓일 때 사후 확률이 가는 곳', { cls: 'ink bold' }));
    g.push(f.axes({
        xLabel: '증거의 개수',
        yLabel: '사후 확률',
        xTicks: [0, 4, 8, 12, 16, 20, 24],
        yTicks: [0, 0.25, 0.5, 0.75, 1],
    }));

    const post = p0 => n => {
        const o = (p0 / (1 - p0)) * Math.pow(1.6, n);
        return o / (1 + o);
    };
    g.push(f.curve(post(0.85), { cls: 's3' }));
    g.push(f.curve(post(0.30), { cls: 's1' }));
    g.push(f.curve(post(0.02), { cls: 's2' }));

    // 사전 확률 0 — 축 위에 겹쳐 그린다.
    g.push(`<path class="cv" stroke="var(--ink2)" stroke-dasharray="6 5" d="M${f.X(0)} ${f.Y(0)} L${f.X(24)} ${f.Y(0)}"/>`);
    g.push(f.label([9.5, 0], '사전 확률이 0이면 증거가 아무리 쌓여도 0 그대로', { dy: -10, cls: 'ink2', size: 'sm' }));

    g.push(legend(430, 186, [
        { slot: 3, name: '사전 확률 0.85 로 시작한 사람' },
        { slot: 1, name: '사전 확률 0.30 로 시작한 사람' },
        { slot: 2, name: '사전 확률 0.02 로 시작한 사람' },
    ]));

    g.push(txt(36, 320, '세 사람이 같은 증거를 계속 받으면 값이 가까워진다. 증거 하나가 우도비 1.6배를 준다고 놓았다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 340, '그러나 처음 몇 번까지는 세 사람의 답이 크게 다르고, 0에서 시작한 사람은 영원히 움직이지 않는다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'phi-s-bayes-convergence',
        svg: svg({
            width: W, height: H,
            title: '사전 확률이 다른 세 사람의 사후 확률',
            desc: '증거가 쌓이면 값이 가까워지지만 초반의 차이는 크고 사전 확률 0은 움직이지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9장 — 과학철학
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 4. 미결정성 — 같은 점을 지나는 여러 곡선
 *
 * 미결정성을 말로 설명하면 반드시 ‘자료가 부족해서’ 로 오해된다.
 * 자료를 하나도 어기지 않는 곡선이 여럿이라는 것을 그림으로 못 박는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 770;
    const H = 348;
    const f = frame({ xRange: [0.4, 7.3], yRange: [0, 9], box: { x: 72, y: 68, w: 616, h: 204 } });
    const g = [];

    g.push(txt(36, 24, '자료가 고르지 못하는 것', { cls: 'ink bold' }));
    g.push(f.axes({
        xLabel: '입력',
        yLabel: '측정값',
        xTicks: [1, 2, 3, 4, 5, 6, 7],
        yTicks: [0, 2, 4, 6, 8],
    }));

    g.push(f.guide([5, 0], [5, 8.7]));
    g.push(f.label([5, 8.7], '자료가 끝나는 곳', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(f.curve(x => x + 1, { cls: 's1' }));
    g.push(f.curve(x => x + 1 + 0.7 * Math.sin(Math.PI * x), { cls: 's2' }));
    g.push(f.curve(x => (x <= 5 ? x + 1 : x + 1 - 1.5 * (x - 5) * (x - 5)), { cls: 's3', dash: '7 5' }));

    for (let k = 1; k <= 5; k += 1) g.push(f.dot([k, k + 1], { cls: 'ink', r: 4.5 }));

    g.push(legend(112, 100, [
        { slot: 1, name: '이론 가 — 곧게 간다' },
        { slot: 2, name: '이론 나 — 점 사이에서 출렁인다' },
        { slot: 3, name: '이론 다 — 자료가 끝난 뒤 꺾인다' },
    ]));

    g.push(txt(36, 312, '검은 점 다섯 개는 세 이론이 하나도 빠짐없이 맞힌다. 자료를 더 정밀하게 재도 이 사정은 그대로다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 332, '갈리는 곳은 점 사이와 점 너머다. 그러니 자료만으로는 셋 중 하나를 고를 수 없다', { cls: 'ink bold', size: 'sm' }));

    return {
        name: 'phi-s-underdetermination',
        svg: svg({
            width: W, height: H,
            title: '같은 자료를 지나는 여러 이론',
            desc: '자료가 부족해서가 아니라 자료를 다 맞히는 이론이 여럿이라 고를 수 없다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 반증의 비대칭과 그 한계
 * ------------------------------------------------------------------ */
add((() => {
    const W = 770;
    const H = 306;
    const g = [];

    g.push(txt(36, 26, '전칭 명제 — ‘모든 백조는 희다’', { cls: 'ink bold' }));
    g.push(txt(36, 50, '가로줄은 검사한 사례들이다. 오른쪽 끝은 아직 검사하지 않은 사례로 계속 이어진다', { cls: 'ink2', size: 'sm' }));

    for (let i = 0; i < 20; i += 1) {
        const x = 58 + i * 32;
        const bad = i === 12;
        const seen = i < 15;
        g.push(box(x, 74, 26, 26, {
            fill: bad ? C2 : (seen ? C3 : 'none'),
            op: bad ? 0.22 : 0.14,
            stroke: bad ? C2 : (seen ? C3 : CG),
            sw: bad ? 1.9 : 1.2,
        }));
        if (seen) g.push(txt(x + 13, 93, bad ? '✗' : '✓', { anchor: 'middle', cls: bad ? 'ink bold' : 'ink', size: 'sm' }));
    }
    g.push(txt(58, 122, '검사한 사례', { cls: 'ink2', size: 'sm' }));
    g.push(txt(700, 122, '…', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(box(58, 142, 320, 96, { stroke: C3, sw: 1.5, fill: C3, op: 0.07 }));
    g.push(txt(74, 166, '✓ 가 아무리 많아도', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(74, 190, '전칭 명제는 확립되지 않는다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(74, 212, '검사하지 않은 사례가 늘 남는다', { cls: 'ink', size: 'sm' }));

    g.push(box(410, 142, 300, 96, { stroke: C2, sw: 1.5, fill: C2, op: 0.07 }));
    g.push(txt(426, 166, '✗ 가 하나면', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(426, 190, '전칭 명제는 거짓이다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(426, 212, '이 걸음은 연역이다 — 후건 부정', { cls: 'ink', size: 'sm' }));

    g.push(txt(36, 268, '포퍼가 쓰는 비대칭은 여기까지다. 그런데 ✗ 라고 적는 일 자체가 관찰 진술이고, 관찰 진술도 틀릴 수 있다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 290, '게다가 실제 검사에서 ✗ 를 내놓는 것은 가설 하나가 아니라 가설과 보조 가정의 묶음이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-s-falsify-asymmetry',
        svg: svg({
            width: W, height: H,
            title: '확립과 반증의 비대칭',
            desc: '맞는 사례는 아무리 많아도 전칭 명제를 확립하지 못하지만 어긋나는 사례 하나는 거짓으로 만든다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 6. 실재론과 구성적 경험주의가 갈리는 자리
 *
 * 두 입장이 ‘같은 이론을 쓰고 같은 실험을 한다’ 는 것을 먼저 보여야
 * 반 프라센이 회의주의자로 오해되지 않는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 782;
    const H = 330;
    const g = [];

    g.push(txt(36, 26, '이론이 하는 주장을 둘로 나누면', { cls: 'ink bold' }));

    g.push(box(48, 50, 420, 214, { stroke: CK, sw: 1.4 }));
    g.push(box(64, 68, 388, 96, { fill: C2, op: 0.10, stroke: C2, sw: 1.5 }));
    g.push(txt(80, 94, '관찰 불가능한 것에 대한 주장', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(80, 118, '전자가 있다 / 장이 있다 /', { cls: 'ink', size: 'sm' }));
    g.push(txt(80, 140, '분자는 이런 모양으로 배열되어 있다', { cls: 'ink', size: 'sm' }));

    g.push(box(64, 178, 388, 70, { fill: C3, op: 0.13, stroke: C3, sw: 1.5 }));
    g.push(txt(80, 204, '관찰 가능한 것에 대한 주장', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(80, 228, '계기 바늘이 이만큼 움직인다 / 무늬가 여기 생긴다', { cls: 'ink', size: 'sm' }));

    // 실재론 괄호 — 전체
    g.push(ln([[486, 56], [498, 56], [498, 258], [486, 258]], { stroke: C1, sw: 1.8 }));
    g.push(txt(510, 104, '과학적 실재론 — 다수설', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(510, 126, '둘 다 참이라고 믿는다', { cls: 'ink', size: 'sm' }));

    // 구성적 경험주의 괄호 — 아래 띠만
    g.push(ln([[486, 182], [498, 182], [498, 244], [486, 244]], { stroke: C3, sw: 1.8 }));
    g.push(txt(510, 190, '구성적 경험주의 — 소수설', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(510, 212, '아래만 참이라고 믿는다.', { cls: 'ink', size: 'sm' }));
    g.push(txt(510, 232, '위는 받아들이되 믿지는 않는다', { cls: 'ink', size: 'sm' }));

    g.push(txt(36, 292, '두 입장은 같은 이론을 쓰고 같은 실험을 하고 같은 예측을 한다. 갈리는 것은 무엇을 믿느냐 하나다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 314, '위 상자를 ‘뜻이 없는 말’ 로 보는 것은 도구주의이고, 구성적 경험주의는 그렇게 보지 않는다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-s-observable-boundary',
        svg: svg({
            width: W, height: H,
            title: '실재론과 구성적 경험주의가 갈리는 자리',
            desc: '이론의 주장 가운데 관찰 불가능한 부분을 믿느냐에서만 두 입장이 갈린다',
            body: g.join(''),
        }),
    };
})());

export default figures;
