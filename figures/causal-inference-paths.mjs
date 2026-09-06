/**
 * 사슬·갈림길·충돌부 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽. 예외를 두지 않는다
 *   - 통제한 변수는 상자로 둘러싼다. 상자는 노드보다 사방 9 만큼 크므로
 *     상자 친 노드의 중심을 화폭 가장자리에서 40 이상 떨어뜨려 두었다
 *   - 열린 경로는 실선, 막힌 경로는 회색 점선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 화살표 색의 뜻을 이 모듈 전체에서 고정한다. 9장 그림과 같은 뜻이다.
 *   s1      — 연관이 흐르는 열린 자리
 *   s3      — 통제 때문에 열린 자리 (충돌부와 그 후손)
 *   회색 점선 — 막힌 자리
 *   회색 실선 — 지금 보고 있는 경로 밖의 화살표
 */
import { svg } from './lib.mjs';
import { node, edge, tag, caseTitle, panel, key, R } from './causal-inference-graph-lib.mjs';

/* 이름 길이가 정하는 가로 반지름. edge 에 그대로 넘겨야 화살표가 노드를 파고들지 않는다. */
const R2 = R;     // 두 글자
const R3 = 31.6;  // 세 글자

/**
 * 세 마디를 삼각으로 놓는다. 세 구조의 차이가 화살표 방향뿐임을 보이려고
 * 배치를 하나로 고정했다.
 */
function triangle(cx, { kind, midY = 106, endY = 196, gap = 70, boxed = false, tone, blocked = false }) {
    const opt = { tone, blocked };
    let arrows;
    if (kind === 'chain') {
        arrows = edge(cx - gap, endY, cx, midY, { ...opt, from: R2, to: R3 })
            + edge(cx, midY, cx + gap, endY, { ...opt, from: R3, to: R2 });
    } else if (kind === 'fork') {
        arrows = edge(cx, midY, cx - gap, endY, { ...opt, from: R3, to: R2 })
            + edge(cx, midY, cx + gap, endY, { ...opt, from: R3, to: R2 });
    } else {
        arrows = edge(cx - gap, endY, cx, midY, { ...opt, from: R2, to: R3 })
            + edge(cx + gap, endY, cx, midY, { ...opt, from: R2, to: R3 });
    }
    return node(cx, midY, '가운데', { rx: R3, boxed })
        + node(cx - gap, endY, '처치', { role: 'x' })
        + node(cx + gap, endY, '결과', { role: 'y' })
        + arrows;
}

/* ── 1. 가운데 마디는 셋 중 하나다 ──────────────────────────── */

const small = { midY: 100, endY: 190, gap: 62 };

const threeStructures = panel(12, 46, 226, 196)
    + caseTitle(125, 34, '사슬')
    + triangle(125, { kind: 'chain', ...small })
    + tag(125, 232, '가운데가 영향을 나른다')

    + panel(247, 46, 226, 196)
    + caseTitle(360, 34, '갈림길')
    + triangle(360, { kind: 'fork', ...small })
    + tag(360, 232, '가운데가 둘을 함께 움직인다')

    + panel(482, 46, 226, 196)
    + caseTitle(595, 34, '충돌부')
    + triangle(595, { kind: 'collider', ...small })
    + tag(595, 232, '둘이 가운데에서 부딪힌다')

    + caseTitle(360, 258, '다른 것은 가운데 마디를 드나드는 화살표의 방향뿐이다');

/* ── 2. 사슬 ────────────────────────────────────────────────── */

const chainFig = panel(14, 50, 330, 196)
    + caseTitle(179, 38, '가운데를 통제하지 않는다')
    + triangle(179, { kind: 'chain', tone: 's1' })
    + tag(179, 236, '앞에서 뒤로 연관이 흐른다')

    + panel(356, 50, 330, 196)
    + caseTitle(521, 38, '가운데를 통제한다')
    + triangle(521, { kind: 'chain', blocked: true, boxed: true })
    + tag(521, 236, '가운데에서 끊긴다')

    + caseTitle(350, 272, '사슬은 가운데를 통제하면 막힌다');

/* ── 3. 갈림길 ──────────────────────────────────────────────── */

const forkFig = panel(14, 50, 330, 196)
    + caseTitle(179, 38, '가운데를 통제하지 않는다')
    + triangle(179, { kind: 'fork', tone: 's1' })
    + tag(179, 236, '둘이 함께 움직인다')

    + panel(356, 50, 330, 196)
    + caseTitle(521, 38, '가운데를 통제한다')
    + triangle(521, { kind: 'fork', blocked: true, boxed: true })
    + tag(521, 236, '한 값으로 묶어 보면 함께 움직이지 않는다')

    + caseTitle(350, 272, '갈림길도 가운데를 통제하면 막힌다');

/* ── 4. 충돌부 — 여기만 반대다 ──────────────────────────────── */

const colliderFig = panel(14, 50, 330, 196)
    + caseTitle(179, 38, '충돌부를 통제하지 않는다')
    + triangle(179, { kind: 'collider', blocked: true })
    + tag(179, 236, '이미 막혀 있다')

    + panel(356, 50, 330, 196)
    + caseTitle(521, 38, '충돌부를 통제한다')
    + triangle(521, { kind: 'collider', tone: 's3', boxed: true })
    + tag(521, 236, '통제가 경로를 열었다')

    + caseTitle(350, 272, '앞의 둘과 반대로 움직이는 자리는 여기뿐이다')
    + key(70, 302, [{ kind: 'open', name: '열린 경로' }])
    + key(280, 302, [{ kind: 'blocked', name: '막힌 경로' }])
    + key(490, 302, [{ kind: 'boxed', name: '통제한 변수' }]);

/* ── 5. 충돌부의 후손 ───────────────────────────────────────── */

function colliderChain(cx, { boxedDesc = false, opened = false }) {
    const tone = opened ? 's3' : undefined;
    const blocked = !opened;
    return node(cx - 80, 100, '처치', { role: 'x' })
        + node(cx + 80, 100, '결과', { role: 'y' })
        + node(cx, 172, '충돌')
        + node(cx, 254, '후손', { boxed: boxedDesc })
        + edge(cx - 80, 100, cx, 172, { blocked, tone })
        + edge(cx + 80, 100, cx, 172, { blocked, tone })
        + edge(cx, 172, cx, 254);
}

const descendantFig = panel(14, 50, 330, 244)
    + caseTitle(179, 38, '후손을 통제하지 않는다')
    + colliderChain(179, {})

    + panel(356, 50, 330, 244)
    + caseTitle(521, 38, '후손만 통제한다')
    + colliderChain(521, { boxedDesc: true, opened: true })

    + caseTitle(350, 316, '충돌부를 건드리지 않아도 그 후손이 같은 일을 한다');

/* ── 6. 한 마디가 막히면 경로 전체가 막힌다 ─────────────────── */

function fourNodePath(cx, { boxedMid = false, midBlocked = false }) {
    return node(cx - 60, 110, '원인')
        + node(cx + 40, 110, '중간', { boxed: boxedMid })
        + node(cx - 120, 200, '처치', { role: 'x' })
        + node(cx + 120, 200, '결과', { role: 'y' })
        + edge(cx - 60, 110, cx - 120, 200, { tone: 's1' })
        + edge(cx - 60, 110, cx + 40, 110, { blocked: midBlocked, tone: 's1' })
        + edge(cx + 40, 110, cx + 120, 200, { blocked: midBlocked, tone: 's1' });
}

const blockedNodeFig = panel(14, 50, 330, 200)
    + caseTitle(179, 38, '아무것도 통제하지 않는다')
    + fourNodePath(179, {})
    + tag(179, 240, '마디 둘이 다 열려 경로가 열려 있다')

    + panel(356, 50, 330, 200)
    + caseTitle(521, 38, '중간만 통제한다')
    + fourNodePath(521, { boxedMid: true, midBlocked: true })
    + tag(521, 240, '원인 자리는 열려 있는데도 경로는 막혔다')

    + caseTitle(350, 276, '경로가 열려 있으려면 마디가 전부 열려 있어야 한다');

/* ── 7. d-분리 판정 ─────────────────────────────────────────── */

function twoPaths(cx, { boxedFork = false }) {
    return node(cx, 104, '혼입', { boxed: boxedFork })
        + node(cx - 78, 186, '처치', { role: 'x' })
        + node(cx + 78, 186, '결과', { role: 'y' })
        + node(cx, 268, '충돌')
        + edge(cx, 104, cx - 78, 186, { blocked: boxedFork, tone: 's1' })
        + edge(cx, 104, cx + 78, 186, { blocked: boxedFork, tone: 's1' })
        + edge(cx - 78, 186, cx, 268, { blocked: true })
        + edge(cx + 78, 186, cx, 268, { blocked: true });
}

const dsepFig = panel(14, 50, 330, 250)
    + caseTitle(179, 38, '아무것도 통제하지 않는다')
    + twoPaths(179, {})
    + caseTitle(179, 320, '위 경로가 열려 있으므로 d-분리가 아니다')

    + panel(356, 50, 330, 250)
    + caseTitle(521, 38, '혼입만 통제한다')
    + twoPaths(521, { boxedFork: true })
    + caseTitle(521, 320, '두 경로가 다 막혔으므로 d-분리다');

/* ── 8. 열려 있는데도 독립이 나올 수 있다 ───────────────────── */

const cancelFig = caseTitle(280, 36, '처치에서 결과로 가는 갈래가 둘이다')
    + tag(280, 68, '거쳐 가는 갈래')
    + node(280, 105, '매개')
    + node(110, 195, '처치', { role: 'x' })
    + node(450, 195, '결과', { role: 'y' })
    + edge(110, 195, 280, 105, { tone: 's1' })
    + edge(280, 105, 450, 195, { tone: 's1' })
    + edge(110, 195, 450, 195, { tone: 's1' })
    + tag(280, 225, '곧바로 가는 갈래')
    + caseTitle(280, 252, '두 갈래가 서로 반대로 움직이면 합이 0 이 될 수 있다');

/* ── 9. 읽는 절차 ───────────────────────────────────────────── */

const steps = [
    ['하나', '두 변수 사이의', '경로를 전부 찾는다'],
    ['둘', '경로마다 마디의', '구조와 통제를 본다'],
    ['셋', '한 마디라도 막혔으면', '그 경로는 막힘'],
    ['넷', '경로가 전부 막혔으면', '두 변수는 d-분리'],
];

const procedureFig = caseTitle(350, 32, '그래프에서 독립을 읽어내는 네 단계')
    + steps.map(([no, l1, l2], i) => {
        const x = 18 + i * 176;
        const cx = x + 68;
        return panel(x, 62, 136, 88) + tag(cx, 56, no)
            + caseTitle(cx, 100, l1) + caseTitle(cx, 122, l2);
    }).join('')
    + edge(154, 106, 194, 106, { from: 0, to: 0 })
    + edge(330, 106, 370, 106, { from: 0, to: 0 })
    + edge(506, 106, 546, 106, { from: 0, to: 0 })
    + caseTitle(350, 180, '9장부터 이 순서를 그대로 쓴다');

export default [
    {
        name: 'ci-s-three-structures',
        svg: svg({
            width: 720, height: 268,
            title: '경로 위의 가운데 마디가 가질 수 있는 세 가지 모양',
            desc: '같은 삼각 배치를 세 번 그렸다. 왼쪽은 처치에서 가운데를 거쳐 결과로 화살표가 이어지고, 가운데는 가운데에서 처치와 결과로 화살표가 나가며, 오른쪽은 처치와 결과에서 가운데로 화살표가 들어온다',
            body: threeStructures,
        }),
    },
    {
        name: 'ci-s-chain',
        svg: svg({
            width: 700, height: 290,
            title: '사슬의 가운데를 통제하기 전과 후',
            desc: '처치에서 가운데를 거쳐 결과로 화살표가 이어진다. 왼쪽은 두 화살표가 실선이고, 오른쪽은 가운데에 상자를 쳐서 두 화살표가 회색 점선이 된다',
            body: chainFig,
        }),
    },
    {
        name: 'ci-s-fork',
        svg: svg({
            width: 700, height: 290,
            title: '갈림길의 가운데를 통제하기 전과 후',
            desc: '가운데에서 처치와 결과로 화살표가 나간다. 왼쪽은 두 화살표가 실선이고, 오른쪽은 가운데에 상자를 쳐서 두 화살표가 회색 점선이 된다',
            body: forkFig,
        }),
    },
    {
        name: 'ci-s-collider',
        svg: svg({
            width: 700, height: 322,
            title: '충돌부를 통제하기 전과 후',
            desc: '처치와 결과에서 가운데로 화살표가 들어온다. 왼쪽은 상자가 없고 두 화살표가 회색 점선이며, 오른쪽은 가운데에 상자를 쳐서 두 화살표가 실선이 된다. 앞의 두 그림과 방향이 반대다',
            body: colliderFig,
        }),
    },
    {
        name: 'ci-s-descendant',
        svg: svg({
            width: 700, height: 330,
            title: '충돌부의 후손만 통제한 그래프',
            desc: '처치와 결과에서 충돌로 화살표가 들어가고 충돌에서 후손으로 화살표가 나간다. 왼쪽은 상자가 없어 위쪽 두 화살표가 회색 점선이고, 오른쪽은 맨 아래 후손에만 상자를 쳤는데 위쪽 두 화살표가 실선이 된다',
            body: descendantFig,
        }),
    },
    {
        name: 'ci-s-blocked-node',
        svg: svg({
            width: 700, height: 300,
            title: '마디가 둘인 경로에서 한 마디만 막은 경우',
            desc: '원인에서 처치로, 원인에서 중간으로, 중간에서 결과로 화살표가 간다. 왼쪽은 세 화살표가 모두 실선이고, 오른쪽은 중간에만 상자를 쳐서 중간에 붙은 두 화살표가 회색 점선이 된다',
            body: blockedNodeFig,
        }),
    },
    {
        name: 'ci-s-dsep',
        svg: svg({
            width: 700, height: 340,
            title: '두 변수를 잇는 경로가 둘인 그래프에서의 판정',
            desc: '혼입에서 처치와 결과로 화살표가 나가고 처치와 결과에서 충돌로 화살표가 들어간다. 왼쪽은 위쪽 두 화살표가 실선 아래쪽 두 화살표가 회색 점선이고, 오른쪽은 혼입에 상자를 쳐서 네 화살표가 모두 회색 점선이 된다',
            body: dsepFig,
        }),
    },
    {
        name: 'ci-s-cancel',
        svg: svg({
            width: 560, height: 268,
            title: '열린 갈래가 둘이라 서로 상쇄될 수 있는 그래프',
            desc: '처치에서 매개를 거쳐 결과로 가는 갈래와 처치에서 결과로 곧바로 가는 갈래가 있고 세 화살표가 모두 실선이다',
            body: cancelFig,
        }),
    },
    {
        name: 'ci-s-procedure',
        svg: svg({
            width: 700, height: 200,
            title: '그래프에서 독립을 읽어내는 네 단계의 순서',
            desc: '네 개의 칸이 왼쪽에서 오른쪽으로 화살표로 이어져 있다. 경로를 전부 찾는 칸, 마디의 구조와 통제를 보는 칸, 한 마디라도 막혔으면 그 경로를 막힌 것으로 두는 칸, 경로가 전부 막혔으면 두 변수가 분리된 것으로 두는 칸이다',
            body: procedureFig,
        }),
    },
];
