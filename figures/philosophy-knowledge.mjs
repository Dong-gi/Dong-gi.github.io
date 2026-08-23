/**
 * 철학 문서 5·6·7장(인식론 세 장)의 그림.
 *
 * 이름은 모두 `phi-k-` 로 시작한다(인식론 담당자에게 배정된 접두어).
 * figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 논리 기호는 유니코드 ¬ ∧ ∨ → ↔ 로 적고, lib 의 esc() 가 물결표를 아래첨자로
 * 바꾸므로 라벨에 물결표를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 * HTML 엔티티도 쓸 수 없다.
 *
 * 이 세 장의 그림은 대부분 ‘영역과 그 안팎’ 이다. 인식론의 논쟁은 거의 전부
 * 무엇이 어떤 울타리 안에 들어가느냐를 두고 벌어지기 때문이다 — 정당화가 닿는
 * 범위, 아는 것들의 집합, 배제해야 하는 대안의 범위. 분류와 계보는 d2/philosophy/
 * 의 phi-k-*.d2 가 맡는다.
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

function box(x, y, w, h, { fill = 'none', op = 1, stroke = CK, sw = 1.3, rx = 4, dash } = {}) {
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

/** 베지어 곡선 하나. */
function cur(x1, y1, cx, cy, x2, y2, { stroke = CK, sw = 1.5, dash } = {}) {
    return `<path d="M${r2(x1)} ${r2(y1)} Q${r2(cx)} ${r2(cy)} ${r2(x2)} ${r2(y2)}"`
        + ` fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round"`
        + `${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/**
 * 화살표. lib 의 px() 는 marker 색이 클래스와 따로 놀아서 직접 만든다.
 * 화살촉을 선 끝에 삼각형으로 그려 붙인다.
 */
function arw(x1, y1, x2, y2, { stroke = CK, sw = 1.6, dash, head = 7 } = {}) {
    const a = Math.atan2(y2 - y1, x2 - x1);
    const bx = x2 - head * Math.cos(a);
    const by = y2 - head * Math.sin(a);
    const wx = (head * 0.62) * Math.sin(a);
    const wy = (head * 0.62) * Math.cos(a);
    return ln([[x1, y1], [bx, by]], { stroke, sw, dash })
        + `<path d="M${r2(x2)} ${r2(y2)} L${r2(bx - wx)} ${r2(by + wy)} L${r2(bx + wx)} ${r2(by - wy)} z" fill="${stroke}"/>`;
}

const pdot = (x, y, col = C2, r = 4.5) => `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="${col}"/>`;

const ring = (x, y, col = CK, r = 4.5, sw = 1.5) =>
    `<circle cx="${r2(x)}" cy="${r2(y)}" r="${r}" fill="none" stroke="${col}" stroke-width="${sw}"/>`;

/* ================================================================== *
 * 5장 — 안다는 것은 무엇인가
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 1. JTB 의 세 조건 — 하나씩 빼 보면 왜 필요한지 드러난다
 *
 * 세 조건을 늘어놓기만 하면 외울 것이 셋 늘 뿐이다. 각 조건을 빼면 어떤
 * 사례가 통과해 버리는지를 같은 칸에 놓아야 조건의 존재 이유가 보인다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 356;
    const g = [];
    const cols = [
        {
            x: 34, col: C1, name: '믿음',
            claim: '갑은 P 라고 믿는다',
            miss: '이 조건만 빼면',
            case: '갑은 답을 맞혔지만\n스스로는 찍었다고 여긴다',
            why: '자기가 안 믿는 것을\n안다고 하지 않는다',
        },
        {
            x: 296, col: C2, name: '참',
            claim: 'P 가 참이다',
            miss: '이 조건만 빼면',
            case: '갑은 확신을 가지고\n거짓을 주장한다',
            why: '거짓을 안다고\n하지 않는다',
        },
        {
            x: 558, col: C3, name: '정당화',
            claim: '갑은 P 를 믿을\n정당한 근거를 가진다',
            miss: '이 조건만 빼면',
            case: '갑은 아무 근거 없이 찍었고\n우연히 맞았다',
            why: '운으로 맞힌 것을\n안다고 하지 않는다',
        },
    ];
    const bw = 228;

    g.push(txt(34, 26, '앎의 세 조건 — 하나씩 빼 보면 그 조건이 무엇을 막고 있는지 드러난다', { cls: 'ink bold' }));

    for (const c of cols) {
        g.push(box(c.x, 44, bw, 66, { fill: c.col, op: 0.12, stroke: c.col, sw: 1.7 }));
        g.push(txt(c.x + 14, 66, c.name, { cls: 'ink bold' }));
        c.claim.split('\n').forEach((s, i) => {
            g.push(txt(c.x + 14, 88 + i * 15, s, { cls: 'ink2', size: 'sm' }));
        });

        g.push(arw(c.x + bw / 2, 112, c.x + bw / 2, 140, { stroke: CK, sw: 1.4 }));
        g.push(txt(c.x + bw / 2 + 8, 132, c.miss, { cls: 'ink2', size: 'sm' }));

        g.push(box(c.x, 146, bw, 68, { stroke: CG, sw: 1.2, rx: 5 }));
        c.case.split('\n').forEach((s, i) => {
            g.push(txt(c.x + 14, 170 + i * 17, s, { cls: 'ink', size: 'sm' }));
        });

        g.push(arw(c.x + bw / 2, 216, c.x + bw / 2, 240, { stroke: CK, sw: 1.4 }));

        g.push(box(c.x, 246, bw, 58, { fill: CG, op: 0.35, stroke: CG, sw: 1.2, rx: 5 }));
        c.why.split('\n').forEach((s, i) => {
            g.push(txt(c.x + 14, 268 + i * 16, s, { cls: 'ink', size: 'sm' }));
        });
    }

    g.push(txt(34, 330, '셋 다 필요하다는 것이 여기까지의 결론이다. 셋을 합치면 충분한가 — 그것을 무너뜨리는 것이 게티어 사례다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-jtb-three',
        svg: svg({
            width: W, height: H,
            title: '앎의 세 조건과 각각을 빼면 통과해 버리는 사례',
            desc: '믿음·참·정당화 각 조건을 하나씩 제거하면 앎이 아닌 사례가 통과한다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 2. 게티어 구조 — 정당화가 가리키는 길과 믿음을 참으로 만든 길이 갈린다
 *
 * ‘운’ 이라는 말만으로는 무엇이 어긋났는지 잡히지 않는다. 두 화살표가
 * 어디서 갈라지는지를 보여야 한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 826;
    const H = 372;
    const g = [];

    const panel = (x, title, split) => {
        const out = [];
        out.push(box(x, 44, 372, 250, { stroke: CG, sw: 1.1, rx: 6 }));
        out.push(txt(x + 186, 30, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

        // 믿음 (왼쪽) 과 참 (오른쪽)
        out.push(box(x + 20, 148, 92, 44, { fill: C1, op: 0.14, stroke: C1, sw: 1.6 }));
        out.push(txt(x + 66, 168, '믿음', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + 66, 184, 'P 라고 믿는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

        out.push(box(x + 262, 148, 92, 44, { fill: C3, op: 0.14, stroke: C3, sw: 1.6 }));
        out.push(txt(x + 308, 168, 'P 가 참', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(x + 308, 184, '이라는 사실', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

        if (!split) {
            out.push(arw(x + 116, 170, x + 258, 170, { stroke: C1, sw: 2.2 }));
            out.push(txt(x + 186, 132, '정당화가 대는 근거', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(x + 186, 208, '그 근거가 곧 P 를 참이게 한 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
            out.push(txt(x + 186, 240, '두 길이 하나다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        } else {
            // 정당화의 길 — 위로 돌아 헛도는 길
            out.push(cur(x + 116, 160, x + 186, 92, x + 258, 160, { stroke: C1, sw: 2.2 }));
            out.push(`<path d="M${r2(x + 258)} ${r2(160)} L${r2(x + 246)} ${r2(152)} L${r2(x + 244)} ${r2(166)} z" fill="${C1}"/>`);
            out.push(txt(x + 186, 86, '정당화가 대는 근거', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(x + 186, 104, '(사실은 거짓이었다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

            // 실제로 참이게 만든 길 — 아래로 돌아가는 다른 길
            out.push(cur(x + 116, 182, x + 186, 252, x + 258, 182, { stroke: C2, sw: 2.2, dash: '6 4' }));
            out.push(`<path d="M${r2(x + 258)} ${r2(182)} L${r2(x + 246)} ${r2(190)} L${r2(x + 244)} ${r2(176)} z" fill="${C2}"/>`);
            out.push(txt(x + 186, 268, 'P 를 실제로 참이게 만든 것', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(x + 186, 284, '(믿는 사람은 이것을 모른다)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

            out.push(pdot(x + 186, 171, C2, 5));
            out.push(txt(x + 186, 226, '두 길이 갈라졌다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        return out.join('');
    };

    g.push(panel(20, '앎일 때', false));
    g.push(panel(434, '게티어 사례일 때', true));

    g.push(txt(20, 326, '왼쪽에서는 내가 대는 근거가 곧 그 명제를 참이게 한 것이다. 오른쪽에서는 근거가 헛다리를 짚었는데도 명제가 따로 참이 되었다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(20, 348, '정당화는 왼쪽 화살표까지만 보증한다. 오른쪽 화살표가 어디로 가는지는 정당화가 관여하지 않는다 — 그 틈으로 운이 들어온다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-luck-gap',
        svg: svg({
            width: W, height: H,
            title: '게티어 사례에서 갈라지는 두 길',
            desc: '정당화가 대는 근거와 명제를 실제로 참이게 만든 것이 서로 다른 길일 때 게티어 사례가 된다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 3. 반례의 모양은 논리학에서나 개념 분석에서나 같다
 *
 * 4장까지 익힌 도구가 실제로 작동하는 첫 자리라는 점을 그림으로 못 박는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 340;
    const g = [];

    const rows = [
        ['무엇을 주장하는가',
            '이 논증 형식은 타당하다',
            '앎이기 위한 필요충분조건은 JTB 다'],
        ['그 주장이 금지하는 것',
            '전제가 모두 참인데\n결론이 거짓인 상황',
            '오른쪽은 만족하는데\n왼쪽은 아닌 사례'],
        ['반례가 하는 일',
            '금지된 그 상황을\n실제로 하나 내놓는다',
            '금지된 그 사례를\n실제로 하나 내놓는다'],
        ['하나면 되는가',
            '하나면 부당함이 확립된다',
            '하나면 분석이 무너진다'],
    ];

    g.push(txt(30, 26, '반례는 두 곳에서 같은 모양으로 작동한다', { cls: 'ink bold' }));

    const xs = [30, 226, 522];
    const ws = [190, 290, 278];
    const heads = ['묻는 것', '논리학 — 논증 형식', '인식론 — 개념 분석'];
    const cols = [CG, C1, C2];

    heads.forEach((h, j) => {
        g.push(box(xs[j], 44, ws[j], 32, { fill: cols[j], op: j === 0 ? 0.4 : 0.14, stroke: j === 0 ? CG : cols[j], sw: 1.5 }));
        g.push(txt(xs[j] + 12, 65, h, { cls: 'ink bold', size: 'sm' }));
    });

    let y = 76;
    for (const row of rows) {
        const lines = Math.max(...row.map(s => s.split('\n').length));
        const h = 24 + lines * 17;
        row.forEach((cell, j) => {
            g.push(box(xs[j], y, ws[j], h, { stroke: CG, sw: 1.1, rx: 4 }));
            cell.split('\n').forEach((s, i) => {
                g.push(txt(xs[j] + 12, y + 22 + i * 17, s, { cls: j === 0 ? 'ink2' : 'ink', size: 'sm' }));
            });
        });
        y += h;
    }

    g.push(txt(30, y + 26, '왼쪽 칸은 논리학 문서 3장에서 정의한 그대로다. 오른쪽 칸에서 달라진 것은 판정 대상뿐이다 —', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, y + 46, '논증 대신 정의를 걸고, 상황 대신 사례를 내놓는다. 게티어가 한 일이 정확히 오른쪽 칸이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-counterexample-mirror',
        svg: svg({
            width: W, height: H,
            title: '논증의 반례와 개념 분석의 반례',
            desc: '반례는 주장이 금지한 조합을 실제로 하나 내놓는 일이며 논리학과 개념 분석에서 같은 모양이다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 4. 가짜 헛간 들판
 *
 * 거짓 전제 조건이 왜 부족한지는 말로만 하면 잘 안 잡힌다. 참인 헛간 하나와
 * 가짜 여럿이 같은 들판에 있는 그림이 그 자리를 만든다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 800;
    const H = 288;
    const g = [];

    const barn = (x, y, s, fake) => {
        const col = fake ? C2 : C3;
        const out = [];
        out.push(`<path d="M${r2(x - 22 * s)} ${r2(y)} L${r2(x - 22 * s)} ${r2(y - 26 * s)} L${r2(x)} ${r2(y - 44 * s)} L${r2(x + 22 * s)} ${r2(y - 26 * s)} L${r2(x + 22 * s)} ${r2(y)} z"`
            + ` fill="${col}" fill-opacity="${fake ? 0.1 : 0.24}" stroke="${col}" stroke-width="1.7"${fake ? ' stroke-dasharray="5 4"' : ''}/>`);
        if (fake) {
            // 앞면뿐이라는 것 — 뒤로 가는 얇은 선이 없다
            out.push(txt(x, y + 16, '앞면뿐', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        } else {
            out.push(ln([[x - 22 * s, y], [x - 14 * s, y - 8 * s], [x + 30 * s, y - 8 * s], [x + 22 * s, y]], { stroke: col, sw: 1.4 }));
            out.push(ln([[x + 22 * s, y - 26 * s], [x + 30 * s, y - 34 * s], [x + 30 * s, y - 8 * s]], { stroke: col, sw: 1.4 }));
            out.push(txt(x + 4, y + 16, '진짜', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        }
        return out.join('');
    };

    g.push(txt(30, 26, '가짜 헛간 들판 — 하필 진짜를 보았다', { cls: 'ink bold' }));

    g.push(ln([[30, 124], [770, 124]], { stroke: CG, sw: 1.4 }));

    const spots = [110, 210, 310, 410, 510, 610, 710];
    const realIdx = 3;
    spots.forEach((x, i) => g.push(barn(x, 120, 1, i !== realIdx)));

    // 보는 사람
    g.push(ring(410, 208, C1, 9, 2));
    g.push(txt(410, 236, '지나가는 사람', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(arw(410, 196, 410, 152, { stroke: C1, sw: 1.8 }));
    g.push(txt(392, 178, '이 하나만 본다', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(box(486, 164, 284, 76, { stroke: CG, sw: 1.1, rx: 5 }));
    g.push(txt(498, 186, '믿음 — 저기 헛간이 있다', { cls: 'ink', size: 'sm' }));
    g.push(txt(498, 204, '참인가 — 참이다. 진짜 헛간이다', { cls: 'ink', size: 'sm' }));
    g.push(txt(498, 222, '정당화되었는가 — 눈으로 보았다', { cls: 'ink', size: 'sm' }));

    g.push(txt(30, 170, '거짓인 전제를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 188, '거친 적이 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 206, '그런데도 앎이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 224, '아닌 것 같다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 272, '조금만 왼쪽이나 오른쪽을 보았다면 같은 방식으로 거짓을 믿었을 것이다 — 참이 된 것이 그 사람의 공이 아니다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-barn',
        svg: svg({
            width: W, height: H,
            title: '가짜 헛간 들판',
            desc: '가짜 헛간 여럿 사이에서 하필 진짜를 본 사람의 믿음은 참이고 정당화되었지만 앎으로 보이지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 5. 안전성과 민감성이 보는 곳이 다르다
 *
 * 두 조건 다 ‘가까운 경우’ 를 보지만 보는 방향이 반대다. 이것을 표로 적으면
 * 반드시 헷갈린다. 같은 점 무리 위에 두 개의 다른 시선을 그려야 갈린다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 374;
    const g = [];

    const cloud = (cx, cy, title, mode) => {
        const out = [];
        out.push(box(cx - 184, cy - 116, 368, 244, { stroke: CG, sw: 1.1, rx: 6 }));
        out.push(txt(cx, cy - 126, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

        // 가까운 경우들 전체
        out.push(ell(cx, cy, 152, 78, { stroke: CG, sw: 1.3, dash: '5 4' }));
        out.push(txt(cx - 148, cy - 92, '가까운 경우들', { cls: 'ink2', size: 'sm' }));

        // P 가 거짓인 영역 — 오른쪽 끝, 테두리를 넘어 걸친다
        out.push(ell(cx + 116, cy, 52, 52, { fill: C2, op: 0.12, stroke: C2, sw: 1.6 }));
        out.push(txt(cx + 116, cy + 72, 'P 가 거짓인 경우', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

        out.push(pdot(cx - 108, cy, C1, 6));
        out.push(txt(cx - 108, cy - 16, '실제 경우', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        out.push(txt(cx - 108, cy + 24, 'P 참 · 믿는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

        if (mode === 'sens') {
            out.push(txt(cx + 4, cy - 44, '가장 가까운 P 거짓 경우로 한 걸음', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(arw(cx - 92, cy, cx + 56, cy, { stroke: C2, sw: 2 }));
            out.push(ring(cx + 78, cy, C2, 7, 2));
            out.push(txt(cx, cy + 100, '거기서도 여전히 믿는다면 민감하지 않다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(cx, cy + 118, '보는 곳은 그 한 점이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        } else {
            out.push(ell(cx - 58, cy, 90, 62, { stroke: C3, sw: 2 }));
            out.push(txt(cx - 60, cy - 72, '내가 P 를 믿는 가까운 경우들', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            for (const [dx, dy] of [[-130, -30], [-124, 32], [-40, -34], [-22, 36], [-6, -6], [-64, -42], [-70, 44]]) {
                out.push(ring(cx + dx, cy + dy, C3, 5, 1.6));
            }
            out.push(txt(cx, cy + 100, '초록과 주황이 한 점도 겹치지 않으면 안전하다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
            out.push(txt(cx, cy + 118, '보는 곳은 초록 영역 전체다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        }
        return out.join('');
    };

    g.push(txt(30, 24, '두 조건 다 ‘가까운 경우’ 를 보지만 보는 방향이 반대다', { cls: 'ink bold' }));

    g.push(cloud(216, 176, '민감성 — P 가 거짓이었다면 믿지 않았을 것이다', 'sens'));
    g.push(cloud(614, 176, '안전성 — 믿었다면 참이었을 것이다', 'safe'));

    g.push(txt(30, 336, '민감성은 실제 경우에서 밖으로 한 걸음 나간 지점을 보고, 안전성은 내가 믿는 가까운 경우 전체를 본다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 358, '그래서 한쪽만 통과하는 믿음이 생긴다 — 두 조건은 서로 다른 조건이지 같은 말의 다른 표현이 아니다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-safety-sensitivity',
        svg: svg({
            width: W, height: H,
            title: '민감성과 안전성이 보는 범위',
            desc: '민감성은 명제가 거짓이 되는 가장 가까운 경우를 보고 안전성은 실제 경우 둘레 전체를 본다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 6장 — 정당화의 구조
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 6. 후퇴와 그 끝 세 가지
 *
 * 후퇴 논증은 말로 하면 길고, 세 갈래가 어떻게 다른지가 잘 안 보인다.
 * 같은 사슬에 세 개의 다른 꼬리를 붙여 나란히 놓는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 366;
    const g = [];

    g.push(txt(30, 26, '‘왜 그렇게 믿는가’ 를 계속 물으면 사슬이 생긴다. 그 사슬의 끝은 셋뿐이다', { cls: 'ink bold' }));

    const bw = 74;
    const bh = 36;
    const chain = (y, n, col) => {
        const out = [];
        for (let i = 0; i < n; i += 1) {
            const x = 118 + i * (bw + 34);
            out.push(box(x, y, bw, bh, { fill: col, op: 0.1, stroke: col, sw: 1.5 }));
            out.push(txt(x + bw / 2, y + 23, i === 0 ? '믿음 P' : `근거 ${i}`, { anchor: 'middle', cls: 'ink', size: 'sm' }));
            if (i > 0) out.push(arw(x - 6, y + bh / 2, x - 28, y + bh / 2, { stroke: CK, sw: 1.4 }));
        }
        return out.join('');
    };

    // (1) 무한주의
    g.push(txt(30, 74, '무한주의', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(30, 92, '멈추지 않는다', { cls: 'ink2', size: 'sm' }));
    g.push(chain(60, 4, C1));
    g.push(txt(556, 84, '· · · · ·', { cls: 'ink', size: 'sm' }));
    g.push(txt(608, 78, '끝없이 새로운 근거가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(608, 94, '계속 이어진다', { cls: 'ink2', size: 'sm' }));

    // (2) 토대론
    g.push(txt(30, 174, '토대론', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(30, 192, '어디선가 멈춘다', { cls: 'ink2', size: 'sm' }));
    g.push(chain(160, 3, C2));
    g.push(box(442, 160, 118, 36, { fill: C2, op: 0.28, stroke: C2, sw: 2 }));
    g.push(txt(501, 183, '기초 믿음', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(436, 178, 414, 178, { stroke: CK, sw: 1.4 }));
    g.push(txt(576, 172, '다른 믿음에 기대지 않고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(576, 188, '정당화된다고 말한다', { cls: 'ink2', size: 'sm' }));

    // (3) 정합론
    g.push(txt(30, 274, '정합론', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(30, 292, '되돌아온다', { cls: 'ink2', size: 'sm' }));
    g.push(chain(260, 3, C3));
    g.push(cur(118, 296, 260, 340, 410, 296, { stroke: C3, sw: 2, dash: '6 4' }));
    g.push(`<path d="M${r2(118)} ${r2(296)} L${r2(130)} ${r2(304)} L${r2(132)} ${r2(290)} z" fill="${C3}"/>`);
    g.push(txt(264, 340, '서로가 서로를 받친다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 272, '사슬이 아니라 그물이라고', { cls: 'ink2', size: 'sm' }));
    g.push(txt(486, 288, '고쳐 말한다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 336, '넷째 길도 있다 —', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 354, '정당화된 믿음이 없다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-regress',
        svg: svg({
            width: W, height: H,
            title: '정당화의 후퇴와 그 끝 세 가지',
            desc: '근거를 계속 묻는 사슬은 무한히 이어지거나 기초에서 멈추거나 되돌아온다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 7. 피라미드와 그물
 *
 * 두 입장의 차이는 ‘정당화가 한 방향으로만 흐르는가’ 하나다. 그림으로
 * 화살표 방향을 보면 그 하나가 눈에 들어온다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 348;
    const g = [];

    g.push(txt(30, 26, '정당화가 한 방향으로 흐르는가, 서로를 받치는가', { cls: 'ink bold' }));

    // 왼쪽 — 피라미드
    g.push(box(30, 42, 372, 232, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(216, 64, '토대론', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const pyr = [
        { y: 88, xs: [216], w: 96, label: ['그 밖의 믿음'] },
        { y: 148, xs: [146, 286], w: 96, label: ['중간 믿음', '중간 믿음'] },
        { y: 208, xs: [96, 216, 336], w: 96, label: ['기초', '기초', '기초'] },
    ];
    pyr.forEach((row, ri) => {
        row.xs.forEach((cx, ci) => {
            const basic = ri === 2;
            g.push(box(cx - row.w / 2, row.y, row.w, 34, {
                fill: basic ? C2 : C1, op: basic ? 0.26 : 0.11,
                stroke: basic ? C2 : C1, sw: basic ? 1.9 : 1.4,
            }));
            g.push(txt(cx, row.y + 22, row.label[ci], { anchor: 'middle', cls: basic ? 'ink bold' : 'ink', size: 'sm' }));
        });
    });
    g.push(arw(146, 148, 190, 126, { stroke: CK, sw: 1.4 }));
    g.push(arw(286, 148, 242, 126, { stroke: CK, sw: 1.4 }));
    g.push(arw(96, 208, 130, 186, { stroke: CK, sw: 1.4 }));
    g.push(arw(216, 208, 176, 186, { stroke: CK, sw: 1.4 }));
    g.push(arw(216, 208, 268, 186, { stroke: CK, sw: 1.4 }));
    g.push(arw(336, 208, 302, 186, { stroke: CK, sw: 1.4 }));
    g.push(txt(216, 262, '화살표가 아래에서 위로만 간다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 그물
    g.push(box(428, 42, 372, 232, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(614, 64, '정합론', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    const nodes = [[520, 106], [700, 100], [468, 190], [614, 166], [744, 196], [566, 240], [690, 246]];
    const edges = [[0, 1], [0, 2], [0, 3], [1, 3], [1, 4], [2, 3], [2, 5], [3, 4], [3, 5], [3, 6], [4, 6], [5, 6]];
    for (const [a, b] of edges) {
        g.push(ln([nodes[a], nodes[b]], { stroke: C3, sw: 1.4 }));
    }
    for (const [x, y] of nodes) {
        g.push(`<circle cx="${x}" cy="${y}" r="15" fill="var(--s3)" fill-opacity="0.22" stroke="${C3}" stroke-width="1.6"/>`);
    }
    g.push(txt(614, 262, '어느 마디도 특별하지 않다. 서로가 서로의 근거다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 300, '토대론이 사는 값 — 기초 믿음이 어떻게 근거 없이 정당화되는지를 설명해야 한다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 322, '정합론이 사는 값 — 잘 짜였지만 세계와 어긋난 그물을 어떻게 걸러 낼지를 설명해야 한다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-pyramid-web',
        svg: svg({
            width: W, height: H,
            title: '토대론의 피라미드와 정합론의 그물',
            desc: '토대론은 정당화가 기초에서 위로만 흐른다고 보고 정합론은 믿음들이 서로를 받친다고 본다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 8. 고립 반론 — 정합만으로는 세계와 이어지지 않는다
 *
 * 정합론에 대한 가장 강한 반론이다. 약하게 요약하지 않으려면 ‘두 그물이
 * 정합의 정도에서 똑같다’ 는 점이 그림에 그대로 있어야 한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 340;
    const g = [];

    g.push(txt(30, 26, '정합의 정도가 똑같은 두 그물 — 그물 안만 보아서는 가를 수 없다', { cls: 'ink bold' }));

    const web = (x, y, col, title, sub) => {
        const out = [];
        out.push(box(x, y, 300, 172, { stroke: CG, sw: 1.1, rx: 6 }));
        out.push(txt(x + 150, y + 24, title, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
        const nd = [[x + 74, y + 66], [x + 168, y + 54], [x + 240, y + 92], [x + 62, y + 128], [x + 154, y + 136], [x + 226, y + 148]];
        const eg = [[0, 1], [0, 3], [1, 2], [1, 4], [2, 5], [3, 4], [4, 5], [0, 4], [1, 3]];
        for (const [a, b] of eg) out.push(ln([nd[a], nd[b]], { stroke: col, sw: 1.4 }));
        for (const [nx, ny] of nd) {
            out.push(`<circle cx="${nx}" cy="${ny}" r="12" fill="${col}" fill-opacity="0.22" stroke="${col}" stroke-width="1.5"/>`);
        }
        out.push(txt(x + 150, y + 166, sub, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };

    g.push(web(30, 44, C3, '세계와 맞물린 그물', '어긋남 없이 짜여 있다'));
    g.push(web(452, 44, C2, '잘 지어낸 이야기', '어긋남 없이 짜여 있다'));

    // 세계에서 들어오는 입력
    g.push(box(346, 96, 76, 64, { fill: CG, op: 0.4, stroke: CK, sw: 1.4, rx: 5 }));
    g.push(txt(384, 122, '세계', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(384, 142, '(바깥)', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(344, 116, 282, 108, { stroke: C3, sw: 2 }));
    g.push(arw(424, 116, 486, 108, { stroke: C2, sw: 2, dash: '5 4' }));
    g.push(ln([[446, 102], [464, 120]], { stroke: C2, sw: 2.4 }));
    g.push(ln([[464, 102], [446, 120]], { stroke: C2, sw: 2.4 }));
    g.push(txt(430, 90, '이 화살표가 없다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 250, '오른쪽 그물은 왼쪽만큼 정합적이다. 마디끼리 어긋나지 않고 새 믿음도 앞뒤가 맞게 들어간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 272, '정합만으로 정당화를 정의하면 두 그물이 똑같이 정당화된다고 말해야 한다 — 이것이 고립 반론이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 294, '정합론자의 대답은 ‘경험에서 온 믿음에 더 큰 무게를 준다’ 는 것인데, 그렇게 하는 순간 무게의 근거를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 316, '그물 바깥에서 끌어오는 것 아니냐는 되물음이 따라붙는다. 이 주고받음은 아직 끝나지 않았다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-isolation',
        svg: svg({
            width: W, height: H,
            title: '고립 반론',
            desc: '정합의 정도가 같은 두 믿음 체계 중 하나만 세계와 맞물려 있다면 정합만으로는 둘을 가를 수 없다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 9. 신빙론의 잣대와 일반성 문제
 *
 * 신빙론을 소개만 하고 넘어가면 함정 2번(입장 목록)이 된다. 이 입장이 무엇을
 * 사고 무엇을 파는지는 일반성 문제에서 가장 잘 드러난다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 830;
    const H = 360;
    const g = [];

    g.push(txt(30, 26, '신빙론의 잣대 — 그리고 그 잣대를 어디에 대는가', { cls: 'ink bold' }));

    // 왼쪽 — 잣대
    g.push(box(30, 42, 344, 180, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(box(52, 70, 112, 44, { fill: C1, op: 0.14, stroke: C1, sw: 1.6 }));
    g.push(txt(108, 90, '믿음을 만든', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(108, 106, '과정', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(168, 92, 206, 92, { stroke: CK, sw: 1.5 }));

    const outs = ['참', '참', '참', '거짓', '참', '참', '참', '거짓', '참', '참'];
    outs.forEach((o, i) => {
        const cx = 218 + (i % 5) * 30;
        const cy = 78 + Math.floor(i / 5) * 30;
        g.push(o === '참' ? pdot(cx, cy, C3, 8) : ring(cx, cy, C2, 8, 2));
    });
    g.push(txt(218, 138, '이 과정이 내놓는 믿음들', { cls: 'ink2', size: 'sm' }));
    g.push(txt(52, 138, '참의 비율이 충분히 높으면', { cls: 'ink', size: 'sm' }));
    g.push(txt(52, 158, '그 과정이 만든 믿음은 정당화되었다고 본다', { cls: 'ink', size: 'sm' }));
    g.push(txt(52, 190, '믿는 사람이 그 비율을 알 필요는 없다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(52, 208, '— 그래서 외재주의다', { cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 일반성 문제
    g.push(box(402, 42, 398, 180, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(422, 66, '한 번의 그 믿음을 어느 넓이의 과정으로 셀 것인가', { cls: 'ink bold', size: 'sm' }));

    const bands = [
        ['시각', '아주 넓게 잡으면 — 참의 비율이 높다'],
        ['어두울 때의 시각', '중간으로 잡으면 — 비율이 뚝 떨어진다'],
        ['이 날 이 자리에서의 시각', '아주 좁게 잡으면 — 비율이 0 또는 1 이다'],
    ];
    bands.forEach(([name, note], i) => {
        const y = 102 + i * 34;
        g.push(ln([[422, y], [422 + 150 - i * 44, y]], { stroke: [C1, C2, C3][i], sw: 4 }));
        g.push(txt(422, y - 8, name, { cls: 'ink', size: 'sm' }));
        g.push(txt(586, y + 4, note, { cls: 'ink2', size: 'sm' }));
    });
    g.push(txt(422, 208, '어느 넓이가 옳은지를 신빙론 자체가 정해 주지 않는다', { cls: 'ink', size: 'sm' }));

    g.push(txt(30, 256, '왼쪽이 신빙론이 사는 것이다 — 믿는 사람이 자기 근거를 들여다보지 못해도 정당화를 말할 수 있다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 278, '아이가 눈으로 보고 믿는 것, 기억이 떠올려 주는 것에 정당화를 줄 수 있는 것이 이 입장의 힘이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 306, '오른쪽이 그 대가다 — 잣대를 대려면 과정을 하나로 지목해야 하는데 같은 사건이 여러 과정에 속한다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 328, '이것을 일반성 문제라 부른다. 신빙론자들은 답을 여럿 내놓았지만 합의된 답은 아직 없다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-reliable-process',
        svg: svg({
            width: W, height: H,
            title: '신빙론의 잣대와 일반성 문제',
            desc: '신빙론은 믿음을 만든 과정의 참 비율로 정당화를 재는데 그 과정을 어느 넓이로 잡을지가 정해지지 않는다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 7장 — 회의주의
 * ================================================================== */

/* ------------------------------------------------------------------ *
 * 10. 꿈 논증 — 두 상황이 같은 증거를 남긴다
 *
 * 꿈 논증의 힘은 ‘꿈이 생생하다’ 가 아니라 ‘내가 가진 증거가 두 상황에서
 * 구별되지 않는다’ 에 있다. 그림은 그 지점만 보이면 된다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 812;
    const H = 340;
    const g = [];

    g.push(txt(30, 26, '두 상황이 안에서 같아 보인다면, 안에 있는 증거로는 어느 쪽인지 가를 수 없다', { cls: 'ink bold' }));

    g.push(box(48, 52, 250, 96, { fill: C3, op: 0.1, stroke: C3, sw: 1.7, rx: 6 }));
    g.push(txt(173, 80, '깨어서 손을 보고 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(173, 104, '눈앞에 손이 있고 빛이 들어온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(173, 124, '손을 쥐면 감각이 온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(box(48, 178, 250, 96, { fill: C2, op: 0.1, stroke: C2, sw: 1.7, rx: 6, dash: '6 4' }));
    g.push(txt(173, 206, '손을 보는 꿈을 꾸고 있다', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(173, 230, '눈앞에 손이 있고 빛이 들어온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(173, 250, '손을 쥐면 감각이 온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(302, 96, 400, 148, { stroke: C3, sw: 2 }));
    g.push(arw(302, 230, 400, 178, { stroke: C2, sw: 2, dash: '5 4' }));

    g.push(box(406, 128, 208, 70, { fill: C1, op: 0.14, stroke: C1, sw: 1.8, rx: 6 }));
    g.push(txt(510, 156, '내가 지금 가진 증거', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(txt(510, 178, '두 상황에서 똑같다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(arw(618, 163, 668, 163, { stroke: CK, sw: 1.5 }));
    g.push(txt(676, 148, '그러니 이 증거로는', { cls: 'ink', size: 'sm' }));
    g.push(txt(676, 166, '두 상황을 가를 수', { cls: 'ink', size: 'sm' }));
    g.push(txt(676, 184, '없다', { cls: 'ink', size: 'sm' }));

    g.push(txt(30, 300, '회의주의자가 주장하는 것은 ‘너는 꿈을 꾸고 있다’ 가 아니다. 어느 쪽인지 가릴 수단이 안에 없다는 것뿐이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 322, '그래서 ‘꿈에서는 볼을 꼬집어도 아프지 않다’ 같은 반박은 과녁을 빗나간다 — 꿈은 그 반박도 함께 담을 수 있다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-dream-evidence',
        svg: svg({
            width: W, height: H,
            title: '꿈 논증의 핵심',
            desc: '깨어 있는 상황과 꿈꾸는 상황이 같은 증거를 남기면 그 증거로는 두 상황을 가를 수 없다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 11. 폐쇄 원리와 그 대우
 *
 * 회의주의 논증이 폐쇄 원리를 ‘지렛대’ 로 쓴다는 것이 이 장의 뼈대다.
 * 원리 자체와 그것을 뒤집어 쓴 모습을 나란히 놓는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 826;
    const H = 372;
    const g = [];

    g.push(txt(30, 26, '폐쇄 원리는 앎의 울타리가 함의 아래에서 새지 않는다고 말한다', { cls: 'ink bold' }));

    // 왼쪽 — 원리
    g.push(box(30, 44, 372, 240, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(216, 68, '원리가 말하는 것', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ell(216, 172, 150, 66, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
    g.push(txt(56, 96, '내가 아는 것들', { cls: 'ink2', size: 'sm' }));
    g.push(pdot(160, 172, C1, 6));
    g.push(txt(160, 156, 'P', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(pdot(272, 172, C1, 6));
    g.push(txt(272, 156, 'Q', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(arw(170, 172, 262, 172, { stroke: CK, sw: 1.5 }));
    g.push(txt(216, 200, 'P → Q 를 알고 있다면', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(216, 218, 'Q 도 울타리 안에 있어야 한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 — 뒤집어 쓴 모습
    g.push(box(428, 44, 372, 240, { stroke: CG, sw: 1.1, rx: 6 }));
    g.push(txt(614, 68, '회의주의자가 쓰는 방향', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));
    g.push(ell(572, 172, 118, 58, { fill: C1, op: 0.1, stroke: C1, sw: 1.8 }));
    g.push(txt(454, 96, '내가 아는 것들', { cls: 'ink2', size: 'sm' }));

    g.push(ring(556, 172, C2, 7, 2));
    g.push(txt(556, 152, 'P — 손이 있다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(ln([[548, 164], [564, 180]], { stroke: C2, sw: 2.2 }));
    g.push(ln([[564, 164], [548, 180]], { stroke: C2, sw: 2.2 }));

    g.push(pdot(748, 172, C2, 6));
    g.push(txt(748, 138, 'Q — 나는 통 속의', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(748, 154, '뇌가 아니다', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    g.push(txt(748, 194, '울타리 밖', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(arw(600, 172, 734, 172, { stroke: C2, sw: 1.6, dash: '5 4' }));

    g.push(txt(614, 248, 'Q 를 모른다면 P 도 모르는 것이 된다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(614, 266, '이것이 폐쇄 원리를 뒤집어 쓴 것이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 310, '왼쪽은 아주 그럴듯하다 — 아는 것에서 따라 나오는 것을 알아차렸다면 그것도 아는 것 아닌가.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 332, '오른쪽은 같은 원리를 반대 방향으로 읽었을 뿐이다. 그래서 원리를 지키면서 결론만 피하기가 어렵다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 354, '논리학 문서 3장의 후건 부정이 그대로 쓰인 자리다 — 뒤가 아니면 앞도 아니다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-closure',
        svg: svg({
            width: W, height: H,
            title: '폐쇄 원리와 그것을 뒤집어 쓴 회의주의',
            desc: '앎이 함의 아래 닫혀 있다면 함의된 것을 모를 때 원래 명제도 모른다는 결론이 따라 나온다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 12. 같은 세 명제, 세 가지 선택
 *
 * 이 장의 요점 그 자체다. 어떤 논증이든 전제를 버리는 길과 결론을 받는 길
 * 가운데 하나를 고르는 일이라는 것을 한 장에 담는다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 838;
    const H = 366;
    const g = [];

    const P1 = '(1) 나는 내가 통 속의 뇌가\n     아님을 알지 못한다';
    const P2 = '(2) 손이 있음을 안다면\n     통 속의 뇌가 아님도 안다';
    const P3 = '(3) 나는 내가 손이\n     있음을 안다';

    const opts = [
        { x: 24, name: '회의주의', drop: 2, note: ['(3) 을 버린다', '값 — 우리가 안다고 여기는', '거의 전부를 내놓는다'] },
        { x: 296, name: '무어 · 외재주의', drop: 0, note: ['(1) 을 버린다', '값 — 통 속의 뇌가 아님을', '어떻게 아는지 말해야 한다'] },
        { x: 568, name: '노직 · 드레츠키', drop: 1, note: ['(2) 를 버린다', '값 — 폐쇄 원리를 포기한다', '아는 것의 귀결을 모를 수 있게 된다'] },
    ];

    g.push(txt(24, 26, '이 세 명제는 함께 참일 수 없다. 어느 것을 버리든 값을 치른다', { cls: 'ink bold' }));

    for (const o of opts) {
        const bw = 246;
        g.push(box(o.x, 42, bw, 258, { stroke: CG, sw: 1.1, rx: 6 }));
        g.push(txt(o.x + bw / 2, 66, o.name, { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

        [P1, P2, P3].forEach((s, i) => {
            const y = 82 + i * 56;
            const gone = i === o.drop;
            g.push(box(o.x + 14, y, bw - 28, 46, {
                fill: gone ? C2 : C1, op: gone ? 0.08 : 0.12,
                stroke: gone ? C2 : C1, sw: 1.5, dash: gone ? '5 4' : undefined,
            }));
            s.split('\n').forEach((t, k) => {
                g.push(txt(o.x + 24, y + 20 + k * 16, t, { cls: gone ? 'ink2' : 'ink', size: 'sm' }));
            });
            if (gone) {
                g.push(ln([[o.x + 20, y + 8], [o.x + bw - 34, y + 38]], { stroke: C2, sw: 2 }));
            }
        });

        o.note.forEach((t, i) => {
            g.push(txt(o.x + 14, 260 + i * 16, t, { cls: 'ink2', size: 'sm' }));
        });
    }

    g.push(txt(24, 330, '이 그림에는 정답 칸이 없다. 셋 다 살아 있는 길이고, 어느 값을 치르는 것이 나은지가 아직 정해지지 않았다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 352, '맥락주의는 넷째 길을 낸다 — 세 명제의 ‘안다’ 가 같은 뜻이 아니라서 애초에 부딪치지 않는다고 말한다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-three-propositions',
        svg: svg({
            width: W, height: H,
            title: '회의주의 논증의 세 명제와 세 가지 선택',
            desc: '함께 참일 수 없는 세 명제 가운데 무엇을 버리느냐로 회의주의 무어 노직의 길이 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 13. 맥락에 따라 움직이는 기준선
 *
 * 맥락주의를 ‘상대주의’ 로 오해하지 않게 하려면, 움직이는 것이 명제가
 * 아니라 기준선이라는 것이 그림에 있어야 한다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 812;
    const H = 340;
    const g = [];

    g.push(txt(30, 26, '움직이는 것은 세계도 나의 증거도 아니다. ‘안다’ 가 요구하는 높이다', { cls: 'ink bold' }));

    const bx = 96;
    const bw = 620;
    const top = 62;
    const bot = 230;

    g.push(box(bx, top, bw, bot - top, { stroke: CG, sw: 1.1, rx: 5 }));
    g.push(txt(bx - 10, top + 14, '높음', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx - 10, bot - 6, '낮음', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx - 10, (top + bot) / 2, '요구되는', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(bx - 10, (top + bot) / 2 + 16, '기준', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    // 내 증거의 높이 — 고정
    const ev = 158;
    g.push(ln([[bx, ev], [bx + bw, ev]], { stroke: C1, sw: 3 }));
    g.push(txt(bx + bw + 8, ev + 5, '내 증거', { cls: 'ink bold', size: 'sm' }));

    // 두 맥락의 기준선
    g.push(ln([[bx + 24, 196], [bx + 280, 196]], { stroke: C3, sw: 2.6, dash: '7 4' }));
    g.push(txt(bx + 24, 216, '일상 맥락 — 은행이 토요일에 여는지 묻는 자리', { cls: 'ink', size: 'sm' }));
    g.push(txt(bx + 24, 186, '기준선', { cls: 'ink2', size: 'sm' }));

    g.push(ln([[bx + 336, 96], [bx + 596, 96]], { stroke: C2, sw: 2.6, dash: '7 4' }));
    g.push(txt(bx + 336, 86, '기준선', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 336, 124, '회의주의 논의가 시작된 맥락 —', { cls: 'ink', size: 'sm' }));
    g.push(txt(bx + 336, 142, '통 속의 뇌 가능성이 화제에 오른 자리', { cls: 'ink', size: 'sm' }));

    g.push(txt(bx + 40, 254, '증거가 기준을 넘는다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 40, 272, '‘나는 안다’ 가 참이 된다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(bx + 352, 254, '증거가 기준에 못 미친다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(bx + 352, 272, '‘나는 안다’ 가 거짓이 된다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 306, '두 문장이 모순되지 않는 것은 ‘안다’ 가 두 자리에서 다른 것을 요구하기 때문이다. 사람이 달라진 것도,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 328, '증거가 달라진 것도, 사실이 달라진 것도 아니다 — 이것이 맥락주의가 붙드는 하나의 주장이다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-context-bar',
        svg: svg({
            width: W, height: H,
            title: '맥락에 따라 움직이는 앎의 기준선',
            desc: '증거의 높이는 그대로인데 맥락이 요구하는 기준선이 오르내려 같은 문장의 참 거짓이 갈린다',
            body: g.join(''),
        }),
    };
})());

/* ------------------------------------------------------------------ *
 * 14. 관련 대안 — 배제해야 하는 것과 그러지 않아도 되는 것
 *
 * 관련 대안 이론은 ‘가까운 것만 배제하면 된다’ 로 요약되곤 하는데, 정작
 * 논쟁은 무엇이 가까운가에서 벌어진다. 그림이 그 다툼을 감추면 안 된다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 356;
    const g = [];

    g.push(txt(30, 26, '안다고 하려면 어디까지의 대안을 배제해야 하는가', { cls: 'ink bold' }));

    const cx = 250;
    const cy = 178;

    g.push(ell(cx, cy, 200, 110, { stroke: CG, sw: 1.3, dash: '6 4' }));
    g.push(ell(cx, cy, 112, 66, { fill: C3, op: 0.12, stroke: C3, sw: 1.9 }));

    g.push(txt(cx, cy - 90, '배제하지 않아도 되는 대안', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, cy - 46, '배제해야 하는 대안', { anchor: 'middle', cls: 'ink bold', size: 'sm' }));

    g.push(pdot(cx - 50, cy - 6, C3, 5));
    g.push(txt(cx - 40, cy - 1, '저것은 말이다', { cls: 'ink', size: 'sm' }));
    g.push(pdot(cx - 50, cy + 24, C3, 5));
    g.push(txt(cx - 40, cy + 29, '저것은 얼룩 그림자다', { cls: 'ink', size: 'sm' }));

    g.push(pdot(cx, cy + 80, C2, 5));
    g.push(txt(cx + 12, cy + 85, '내가 통 속의 뇌다', { cls: 'ink', size: 'sm' }));

    // 다투는 자리 — 안쪽 원의 테두리 위
    g.push(ring(cx + 72, cy - 51, C2, 7, 2.2));
    g.push(ln([[cx + 82, cy - 55], [464, 108]], { stroke: C2, sw: 1.2, dash: '4 3' }));
    g.push(txt(472, 96, '얼룩말로 칠한 노새다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(472, 116, '이것이 안쪽인가 바깥쪽인가 —', { cls: 'ink2', size: 'sm' }));
    g.push(txt(472, 134, '바로 여기서 다툰다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(472, 186, '관련 대안 이론', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(472, 204, '바깥쪽이다. 배제하지 않아도 안다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(472, 234, '회의주의자', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(472, 252, '원을 그을 근거가 없다', { cls: 'ink2', size: 'sm' }));

    g.push(txt(30, 314, '동물원 우리 앞에서 ‘저것은 얼룩말이다’ 를 안다고 말하는 데는 무리가 없다. 그런데 ‘얼룩말로 칠한 노새가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(30, 336, '아님을 아는가’ 라고 따로 물으면 대답이 궁해진다. 두 대답이 다 자연스럽다는 것 자체가 이 절의 문제다', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'phi-k-relevant-alt',
        svg: svg({
            width: W, height: H,
            title: '관련 대안의 안과 밖',
            desc: '앎이 요구하는 것은 관련된 대안의 배제뿐이라는 주장과 그 경계를 어디에 그을지의 다툼',
            body: g.join(''),
        }),
    };
})());

export default figures;
