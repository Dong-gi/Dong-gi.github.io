/**
 * 논리학 3장(논증 — 무엇이 좋은 논증인가)의 그림.
 *
 * 이름은 모두 `log-a-` 로 시작한다(3장 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 연결자는 유니코드 ¬ ∧ ∨ → ↔ ⊥ 로, 논리식 메타변수는 φ ψ χ 로 적는다.
 * lib 의 esc() 가 물결표를 아래첨자로 바꾸므로 라벨에 물결표를 쓰지 않고,
 * 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다. HTML 엔티티도 쓸 수 없다.
 *
 * 이 장의 그림은 셋 다 표·영역·격자 쪽이다. 판정 흐름도는 d2/logic/ 에 있다.
 * 타당성은 말로 설명하면 반드시 ‘전제가 실제로 참인가’ 와 섞이므로,
 * 네 조합을 눈으로 보게 하는 격자 하나가 문단 여러 개보다 낫다.
 */
import { svg, txt } from './lib.mjs';

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

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 3, dash } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}"`
        + ` fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

function ell(cx, cy, rx, ry, { fill = 'none', op = 1, stroke = CK, sw = 1.6, dash } = {}) {
    return `<ellipse cx="${r2(cx)}" cy="${r2(cy)}" rx="${r2(rx)}" ry="${r2(ry)}"`
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

/* ------------------------------------------------------------------ *
 * 1. 전제와 결론의 실제 참·거짓 네 조합
 *
 * 이 장에서 가장 많이 걸리는 자리를 겨냥한 그림이다. 타당성이 금지하는 칸이
 * 넷 중 하나뿐이라는 것을 보면 "전제가 실제로 참인가"가 왜 무관한지 눈에 들어온다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 782;
    const H = 352;
    const g = [];
    const cols = [[40, 190], [230, 130], [360, 382]];
    const heads = ['전제의 실제 참·거짓', '결론의 실제 참·거짓', '타당한 논증에서 일어날 수 있나'];
    const rows = [
        ['모두 참', '참', '있다 — 이것이 건전한 논증이다'],
        ['모두 참', '거짓', '없다 — 타당성이 막는 칸은 이 하나뿐이다'],
        ['하나라도 거짓', '참', '있다'],
        ['하나라도 거짓', '거짓', '있다'],
    ];

    g.push(txt(40, 26, '전제와 결론의 실제 참·거짓 — 네 조합', { cls: 'ink bold' }));

    const hy = 44;
    const hh = 34;
    cols.forEach(([x, w], i) => {
        g.push(box(x, hy, w, hh, { fill: CG, op: 0.4, stroke: CK, sw: 1.2 }));
        g.push(txt(x + 10, hy + 22, heads[i], { cls: 'ink bold', size: 'sm' }));
    });

    const ry = hy + hh;
    const rh = 42;
    rows.forEach((row, i) => {
        const y = ry + i * rh;
        const bad = i === 1;
        cols.forEach(([x, w], j) => {
            g.push(box(x, y, w, rh, {
                fill: bad ? C2 : 'none', op: bad ? 0.16 : 1,
                stroke: bad ? C2 : CK, sw: bad ? 1.9 : 1.2,
            }));
            g.push(txt(x + 10, y + rh / 2 + 5, row[j], { cls: bad ? 'ink bold' : 'ink', size: 'sm' }));
        });
    });

    g.push(txt(40, 286, '타당성이 금지하는 것은 둘째 줄 하나뿐이다. 나머지 세 줄은 타당한 논증에도 부당한 논증에도 그대로 나타난다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 308, '그래서 전제나 결론이 실제로 참인지만 보고는 타당한지 알 수 없다. 거꾸로 타당한지만 보고 전제가 참인지도 알 수 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 330, '부당한 논증은 네 줄 어디에나 나타날 수 있다. 둘째 줄에 나타날 수 있다는 것이 곧 부당함의 뜻이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'log-a-validity-grid',
        svg: svg({
            width: W, height: H,
            title: '전제와 결론의 실제 참·거짓 네 조합',
            desc: '타당성이 금지하는 조합은 전제가 모두 참인데 결론이 거짓인 한 칸뿐이다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 반례 — 두 영역이 겹치는가
 *
 * 타당성의 정의를 그림 하나로 바꾼 것이다. 왼쪽은 겹치지 않아 반례가 없고,
 * 오른쪽은 겹치는 점이 있어 그 점 하나가 반례가 된다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 802;
    const H = 352;
    const g = [];

    g.push(txt(206, 26, '타당한 논증 — 두 영역이 겹치지 않는다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(594, 26, '부당한 논증 — 겹치는 점이 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    // 왼쪽 패널 — 타당
    g.push(box(36, 44, 340, 216, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(46, 62, '상상할 수 있는 모든 상황', { cls: 'ink2', size: 'sm' }));
    g.push(ell(120, 158, 72, 58, { fill: C1, op: 0.16, stroke: C1 }));
    g.push(ell(296, 158, 66, 54, { fill: C2, op: 0.16, stroke: C2 }));
    g.push(txt(120, 162, '전제 모두 참', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(296, 162, '결론 거짓', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(206, 240, '겹치는 점이 하나도 없다 → 반례가 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 패널 — 부당
    g.push(box(424, 44, 340, 216, { stroke: CG, sw: 1, rx: 6 }));
    g.push(txt(434, 62, '상상할 수 있는 모든 상황', { cls: 'ink2', size: 'sm' }));
    g.push(ell(516, 158, 84, 58, { fill: C1, op: 0.16, stroke: C1 }));
    g.push(ell(648, 158, 72, 54, { fill: C2, op: 0.16, stroke: C2 }));
    g.push(txt(486, 162, '전제 모두 참', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(678, 162, '결론 거짓', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(pdot(588, 158));
    g.push(ln([[588, 166], [588, 226]], { stroke: C2, sw: 1.4, dash: '4 3' }));
    g.push(txt(588, 244, '이 점 하나가 반례다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(txt(36, 292, '파란 영역 — 전제를 모두 참으로 만드는 상황들. 주황 영역 — 결론을 거짓으로 만드는 상황들', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 314, '타당함을 보이려면 영역 전체가 겹치지 않음을 확인해야 하고, 부당함을 보이려면 겹치는 점 하나를 내놓으면 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(36, 336, '드는 수고가 이렇게 다르다는 것이 이 문서가 반례를 먼저 가르치는 이유다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'log-a-counterexample-space',
        svg: svg({
            width: W, height: H,
            title: '반례 — 두 영역이 겹치는가',
            desc: '전제를 모두 참으로 만드는 상황과 결론을 거짓으로 만드는 상황이 겹치면 그 점이 반례다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 조건문을 쓰는 네 형식 — 둘은 타당하고 둘은 부당하다
 *
 * 부당한 두 형식이 같은 반례로 무너진다는 것을 나란히 놓아 보인다.
 * 이 그림 하나가 후건 긍정·전건 부정을 외울 필요를 없앤다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 822;
    const H = 366;
    const g = [];
    const bw = 356;
    const bh = 132;

    const cards = [
        {
            x: 36, y: 30, col: C3, name: '전건 긍정 (modus ponens)',
            top: 'φ → ψ', mid: 'φ', bot: 'ψ', verdict: '타당하다',
            notes: ['전제를 모두 참으로 만들면서', '결론을 거짓으로 만드는 상황이 없다'],
        },
        {
            x: 428, y: 30, col: C3, name: '후건 부정 (modus tollens)',
            top: 'φ → ψ', mid: '¬ψ', bot: '¬φ', verdict: '타당하다',
            notes: ['ψ 가 거짓인데 φ 가 참이면', 'φ → ψ 가 거짓이 되어 전제와 어긋난다'],
        },
        {
            x: 36, y: 186, col: C2, name: '후건 긍정 (오류)',
            top: 'φ → ψ', mid: 'ψ', bot: 'φ', verdict: '부당하다',
            notes: ['반례 — φ 는 거짓, ψ 는 참', '전제는 둘 다 참, 결론은 거짓'],
        },
        {
            x: 428, y: 186, col: C2, name: '전건 부정 (오류)',
            top: 'φ → ψ', mid: '¬φ', bot: '¬ψ', verdict: '부당하다',
            notes: ['반례 — φ 는 거짓, ψ 는 참', '위 칸과 반례가 똑같다'],
        },
    ];

    for (const c of cards) {
        g.push(box(c.x, c.y, bw, bh, { fill: c.col, op: 0.07, stroke: c.col, sw: 1.6, rx: 6 }));
        g.push(txt(c.x + 16, c.y + 25, c.name, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(c.x + 62, c.y + 56, c.top, { anchor: 'middle', cls: 'ink' }));
        g.push(txt(c.x + 62, c.y + 78, c.mid, { anchor: 'middle', cls: 'ink' }));
        g.push(ln([[c.x + 26, c.y + 88], [c.x + 98, c.y + 88]], { stroke: CK, sw: 1.4 }));
        g.push(txt(c.x + 62, c.y + 110, c.bot, { anchor: 'middle', cls: 'ink' }));
        g.push(txt(c.x + 124, c.y + 56, c.verdict, { cls: 'ink bold', size: 'sm' }));
        g.push(txt(c.x + 124, c.y + 80, c.notes[0], { cls: 'ink2', size: 'sm' }));
        g.push(txt(c.x + 124, c.y + 100, c.notes[1], { cls: 'ink2', size: 'sm' }));
    }

    g.push(txt(36, 346, '아래 두 형식은 반례 하나로 함께 무너진다. φ 가 거짓이고 ψ 가 참이면 φ → ψ 는 참이 되기 때문이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'log-a-form-four',
        svg: svg({
            width: W, height: H,
            title: '조건문을 쓰는 네 형식',
            desc: '전건 긍정과 후건 부정은 타당하고 후건 긍정과 전건 부정은 같은 반례로 무너진다',
            body: g.join(''),
        }),
    };
})());

export default figures;
