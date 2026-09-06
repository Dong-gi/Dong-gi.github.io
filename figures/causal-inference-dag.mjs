/**
 * 인과 그래프 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽. 예외를 두지 않는다
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *   - 색만으로 구분하지 않고 늘 이름을 함께 적는다
 *
 * 이 장에는 통제한 변수도 관측되지 않은 변수도 없다. 상자(boxed)는 8장이
 * ‘통제한 변수’ 라는 뜻을 주고 점선 원(unobserved)은 9장이 쓰므로 여기서는
 * 둘 다 쓰지 않는다. 회색 점선(blocked)도 마찬가지다 — 8장이 ‘막힌 경로’ 라는
 * 뜻을 주는 표시라, 그 뜻이 서기 전에 다른 자리에 쓰면 규약이 갈라진다.
 * 그래서 이 장에서 없어진 화살표는 그리지 않고 딱지로만 알린다.
 *
 * 화살표 색의 뜻을 이 모듈 전체에서 고정한다.
 *   s1      — 지금 짚고 있는 화살표나 줄
 *   회색 실선 — 그래프에 있지만 지금 짚고 있지 않은 화살표
 */
import { svg } from './lib.mjs';
import { node, edge, tag, caseTitle, panel, R } from './causal-inference-graph-lib.mjs';

/* 이름 길이가 정하는 가로 반지름. edge 에 같은 값을 넘겨야 화살표가 노드를
   파고들지 않는다. node() 가 쓰는 식은 max(R, 7.2 * 글자수 + 10) 이다. */
const R1 = R;   // 한 글자 · 두 글자
const R5 = 46;  // 다섯 글자 (공백 포함)

/* ── 1. 화살표 하나가 무엇을 뜻하는가 ───────────────────────── */

const arrowMeaning = panel(15, 50, 320, 150)
    + caseTitle(175, 38, '화살표를 그렸다')
    + node(95, 110, '처치', { role: 'x' })
    + node(255, 110, '결과', { role: 'y' })
    + edge(95, 110, 255, 110, { tone: 's1' })
    + tag(175, 165, '처치를 바꾸면 결과가 바뀔 수 있다')
    + tag(175, 187, '있을 수 있다는 말이다')

    + panel(365, 50, 320, 150)
    + caseTitle(525, 38, '화살표를 그리지 않았다')
    + node(445, 110, '처치', { role: 'x' })
    + node(605, 110, '결과', { role: 'y' })
    + tag(525, 114, '아무것도 없다')
    + tag(525, 165, '처치를 바꿔도 결과는 그대로다')
    + tag(525, 187, '없다고 못 박은 말이다')

    + caseTitle(350, 238, '안 그린 자리가 더 센 주장을 한다');

/* ── 2. 성긴 그래프와 빽빽한 그래프 ─────────────────────────── */

function fourNodes(cx, { dense }) {
    const gap = 75;
    const top = 100, bot = 200;
    const a = [cx - gap, top], b = [cx + gap, top];
    const x = [cx - gap, bot], y = [cx + gap, bot];
    const arrows = [
        edge(a[0], a[1], x[0], x[1], { tone: 's1' }),
        edge(a[0], a[1], y[0], y[1], { tone: 's1' }),
        edge(b[0], b[1], y[0], y[1], { tone: 's1' }),
        edge(x[0], x[1], y[0], y[1], { tone: 's1' }),
    ];
    if (dense) {
        arrows.push(edge(a[0], a[1], b[0], b[1], { tone: 's1' }));
        arrows.push(edge(b[0], b[1], x[0], x[1], { tone: 's1' }));
    }
    return node(a[0], a[1], '갑') + node(b[0], b[1], '을')
        + node(x[0], x[1], '처치', { role: 'x' }) + node(y[0], y[1], '결과', { role: 'y' })
        + arrows.join('');
}

const sparseDense = panel(15, 50, 320, 200)
    + caseTitle(175, 38, '성긴 그래프 — 화살표 넷')
    + fourNodes(175, { dense: false })
    + tag(175, 268, '그리지 않은 자리가 둘 남아 있다')
    + tag(175, 288, '그 둘은 없다고 주장하는 중이다')

    + panel(365, 50, 320, 200)
    + caseTitle(525, 38, '빽빽한 그래프 — 화살표 여섯')
    + fourNodes(525, { dense: true })
    + tag(525, 268, '그리지 않은 자리가 하나도 없다')
    + tag(525, 288, '아무것도 없다고 주장하지 않는다')

    + caseTitle(350, 318, '성길수록 강한 가정이다. 빽빽한 그래프는 거의 아무 말도 하지 않는다');

/* ── 3. 되먹임은 시간을 펼쳐 그린다 ─────────────────────────── */

const cycleUnroll = panel(15, 60, 250, 150)
    + caseTitle(140, 48, '되먹임')
    + node(78, 130, '재고')
    + node(202, 130, '판매')
    + edge(78, 130, 202, 130, { bow: -30, tone: 's1' })
    + edge(202, 130, 78, 130, { bow: 30, tone: 's1' })
    + tag(140, 190, '서로가 서로의 원인이다')

    + panel(290, 60, 395, 150)
    + caseTitle(487, 48, '시간을 펼쳐 그린다')
    + node(362, 130, '오늘 재고', { rx: R5 })
    + node(487, 130, '오늘 판매', { rx: R5 })
    + node(612, 130, '내일 재고', { rx: R5 })
    + edge(362, 130, 487, 130, { from: R5, to: R5, tone: 's1' })
    + edge(487, 130, 612, 130, { from: R5, to: R5, tone: 's1' })
    + tag(487, 190, '같은 이야기인데 돌아오는 자리가 없다')

    + caseTitle(350, 240, '시각이 다르면 다른 마디다. 그래서 순환이 사라진다');

/* ── 4. 부모·자식·조상·후손 ─────────────────────────────────── */

const family = caseTitle(320, 36, '가운데 병을 기준으로 자리에 이름을 붙인다')
    + node(90, 130, '갑') + node(200, 130, '을') + node(200, 215, '무')
    + node(320, 170, '병') + node(440, 170, '정') + node(550, 170, '기')
    + edge(90, 130, 200, 130)
    + edge(200, 130, 320, 170, { tone: 's1' })
    + edge(200, 215, 320, 170, { tone: 's1' })
    + edge(320, 170, 440, 170, { tone: 's1' })
    + edge(440, 170, 550, 170)
    + tag(90, 96, '부모가 아닌 조상')
    + tag(200, 96, '부모이면서 조상')
    + tag(200, 253, '부모이면서 조상')
    + tag(320, 212, '기준 마디')
    + tag(440, 136, '자식이면서 후손')
    + tag(550, 136, '후손')
    + caseTitle(320, 272, '부모는 조상에 들어가고 자식은 후손에 들어간다');

/* ── 5. 경로와 인과 경로 ────────────────────────────────────── */

function diamond(cx, { highlight }) {
    const top = highlight === 'up' ? 's1' : 'ink2';
    const bottom = highlight === 'down' ? 's1' : 'ink2';
    return node(cx, 92, '갑')
        + node(cx - 80, 168, '처치', { role: 'x' })
        + node(cx + 80, 168, '결과', { role: 'y' })
        + node(cx, 244, '을')
        + edge(cx, 92, cx - 80, 168, { tone: top })
        + edge(cx, 92, cx + 80, 168, { tone: top })
        + edge(cx - 80, 168, cx, 244, { tone: bottom })
        + edge(cx, 244, cx + 80, 168, { tone: bottom });
}

const pathVsCausal = panel(15, 50, 320, 225)
    + caseTitle(175, 38, '경로')
    + diamond(175, { highlight: 'up' })
    + tag(175, 296, '갑에서는 화살표가 둘 다 나간다')
    + tag(175, 316, '방향이 어긋나도 두 마디가 이어져 있다')

    + panel(365, 50, 320, 225)
    + caseTitle(525, 38, '인과 경로')
    + diamond(525, { highlight: 'down' })
    + tag(525, 296, '처치에서 을을 거쳐 결과로 간다')
    + tag(525, 316, '내내 화살표 방향을 따라간다')

    + caseTitle(350, 348, '경로는 방향을 따지지 않는다. 방향을 따지는 것만 인과 경로다');

/* ── 6. 마르코프 가정 ───────────────────────────────────────── */

const markov = caseTitle(320, 38, '마르코프 가정이 하는 말')
    + node(110, 120, '갑') + node(250, 120, '을') + node(250, 205, '무') + node(420, 160, '병')
    + edge(110, 120, 250, 120)
    + edge(250, 120, 420, 160, { tone: 's1' })
    + edge(250, 205, 420, 160, { tone: 's1' })
    + tag(110, 86, '부모가 아닌 조상')
    + tag(250, 86, '부모')
    + tag(250, 243, '부모')
    + tag(420, 126, '이 마디')
    + caseTitle(320, 274, '을과 무의 값을 알고 나면 병은 갑과 무관해진다');

/* ── 7. 개입은 들어오는 화살표만 끊는다 ─────────────────────── */

function withCauses(cx, { intervened }) {
    const inArrows = intervened ? ''
        : edge(cx - 60, 88, cx - 75, 192, { tone: 's1' })
          + edge(cx + 55, 88, cx - 75, 192, { tone: 's1' });
    return node(cx - 60, 88, '을') + node(cx + 55, 88, '갑')
        + node(cx - 75, 192, '처치', { role: 'x' })
        + node(cx + 75, 192, '결과', { role: 'y' })
        + inArrows
        + edge(cx + 55, 88, cx + 75, 192)
        + edge(cx - 75, 192, cx + 75, 192);
}

const intervention = panel(15, 50, 320, 195)
    + caseTitle(175, 38, '관찰 — 그대로 두고 본다')
    + withCauses(175, { intervened: false })
    + tag(175, 236, '처치로 화살표가 둘 들어온다')

    + panel(365, 50, 320, 195)
    + caseTitle(525, 38, '개입 — 우리가 정한다')
    + withCauses(525, { intervened: true })
    + tag(492, 142, '둘 다 끊었다')
    + tag(525, 236, '나가는 화살표는 그대로다')

    + caseTitle(350, 274, '들어오는 화살표만 사라진다. 마디도 나머지 화살표도 그대로다');

/* ── 8. 같은 상관에 맞는 그래프가 여럿이다 ──────────────────── */

const threeStories = panel(12, 52, 220, 152)
    + caseTitle(122, 42, '첫째 이야기')
    + node(67, 132, '처치', { role: 'x' })
    + node(177, 132, '결과', { role: 'y' })
    + edge(67, 132, 177, 132, { tone: 's1' })
    + tag(122, 222, '처치가 결과를 바꾼다')

    + panel(240, 52, 220, 152)
    + caseTitle(350, 42, '둘째 이야기')
    + node(295, 132, '처치', { role: 'x' })
    + node(405, 132, '결과', { role: 'y' })
    + edge(405, 132, 295, 132, { tone: 's1' })
    + tag(350, 222, '결과 쪽이 처치를 정한다')

    + panel(468, 52, 220, 152)
    + caseTitle(578, 42, '셋째 이야기')
    + node(578, 96, '갑')
    + node(522, 166, '처치', { role: 'x' })
    + node(634, 166, '결과', { role: 'y' })
    + edge(578, 96, 522, 166, { tone: 's1' })
    + edge(578, 96, 634, 166, { tone: 's1' })
    + tag(578, 222, '갑이 둘 다를 움직인다')

    + caseTitle(350, 248, '세 그래프가 같은 상관을 낳는다. 자료는 셋을 가려 주지 않는다');

export default [
    {
        name: 'ci-g-arrow-meaning',
        svg: svg({
            width: 700, height: 260,
            title: '화살표를 그린 경우와 그리지 않은 경우',
            desc: '왼쪽 칸에는 처치에서 결과로 화살표가 하나 있고, 오른쪽 칸에는 같은 두 마디가 있는데 그 사이에 아무 화살표도 없다',
            body: arrowMeaning,
        }),
    },
    {
        name: 'ci-g-sparse-dense',
        svg: svg({
            width: 700, height: 330,
            title: '마디가 넷인 그래프에 화살표를 넷 그린 경우와 여섯 다 그린 경우',
            desc: '두 칸 모두 갑과 을이 위에, 처치와 결과가 아래에 놓여 있다. 왼쪽 칸에는 화살표가 넷이고 오른쪽 칸에는 그릴 수 있는 자리 여섯이 모두 화살표로 채워져 있다',
            body: sparseDense,
        }),
    },
    {
        name: 'ci-g-cycle-unroll',
        svg: svg({
            width: 700, height: 260,
            title: '되먹임을 시간으로 펼쳐 다시 그린 그래프',
            desc: '왼쪽 칸에서는 재고와 판매 사이에 화살표가 양쪽으로 하나씩 있어 돌아온다. 오른쪽 칸에서는 오늘 재고에서 오늘 판매로, 오늘 판매에서 내일 재고로 화살표가 한 방향으로만 이어진다',
            body: cycleUnroll,
        }),
    },
    {
        name: 'ci-g-family',
        svg: svg({
            width: 640, height: 290,
            title: '한 마디를 기준으로 본 부모와 자식과 조상과 후손',
            desc: '갑에서 을로, 을과 무에서 병으로, 병에서 정으로, 정에서 기로 화살표가 이어진다. 병 옆에는 기준 마디라는 딱지가, 을과 무 옆에는 부모라는 딱지가, 갑 옆에는 부모가 아닌 조상이라는 딱지가 붙어 있다',
            body: family,
        }),
    },
    {
        name: 'ci-g-path-vs-causal',
        svg: svg({
            width: 700, height: 360,
            title: '같은 그래프에서 짚은 두 가지 줄',
            desc: '두 칸 모두 갑이 위에 을이 아래에 있고 처치와 결과가 가운데에 있다. 왼쪽 칸에서는 갑에서 양쪽으로 나가는 두 화살표가 짙게, 오른쪽 칸에서는 처치에서 을을 거쳐 결과로 가는 두 화살표가 짙게 그려져 있다',
            body: pathVsCausal,
        }),
    },
    {
        name: 'ci-g-markov',
        svg: svg({
            width: 640, height: 290,
            title: '부모가 둘인 마디와 그 위쪽 조상',
            desc: '갑에서 을로 화살표가 가고 을과 무에서 병으로 화살표가 들어온다. 을과 무에는 부모라는 딱지가, 갑에는 부모가 아닌 조상이라는 딱지가 붙어 있다',
            body: markov,
        }),
    },
    {
        name: 'ci-g-intervention',
        svg: svg({
            width: 700, height: 290,
            title: '개입하기 전과 개입한 뒤의 그래프',
            desc: '왼쪽 칸에서는 을과 갑에서 처치로 화살표가 들어오고 갑에서 결과로도 화살표가 간다. 오른쪽 칸에서는 처치로 들어오던 화살표 둘이 없고 갑에서 결과로 가는 화살표와 처치에서 결과로 가는 화살표만 남아 있다',
            body: intervention,
        }),
    },
    {
        name: 'ci-g-three-stories',
        svg: svg({
            width: 700, height: 260,
            title: '같은 상관과 들어맞는 세 개의 그래프',
            desc: '첫째 칸에는 처치에서 결과로 가는 화살표가, 둘째 칸에는 결과에서 처치로 가는 화살표가, 셋째 칸에는 갑에서 처치와 결과로 각각 나가는 화살표 둘이 있다',
            body: threeStories,
        }),
    },
];
