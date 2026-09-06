/**
 * 상관에서 멈춘 자리 장 그림. 원장 §5.2 의 규약을 따른다.
 *
 *   - 처치는 왼쪽, 결과는 오른쪽. 예외를 두지 않는다
 *   - 열린 화살표는 실선
 *   - 노드 이름은 짧은 한국어. SVG 안에는 수식을 쓸 수 없다
 *
 * 이 장에는 통제한 변수가 없다. 통제 표시(상자)는 9장부터 나오므로
 * 여기서는 boxed 를 쓰지 않는다.
 *
 * 표 모양 그림(합친 표와 나눈 표)은 인과 그래프가 아니지만, 칸과 글자를
 * 같은 규약으로 그리려고 graph-lib 의 panel·tag·caseTitle 을 그대로 쓴다.
 */
import { svg } from './lib.mjs';
import { node, edge, tag, caseTitle, panel } from './causal-inference-graph-lib.mjs';

/* 본문의 가상 표와 같은 숫자다. 합격률은 백분율. */
const POOLED = [
    { arm: '새 방식', rate: '58%' },
    { arm: '기존 방식', rate: '76%' },
];
const SPLIT = [
    { stratum: '가 과목', neu: '90%', old: '85%' },
    { stratum: '나 과목', neu: '50%', old: '40%' },
];

/* ── 1. 저장소가 멈춘 자리 ──────────────────────────────────── */

const RDOC = 62, RHERE = 66;

const stopped = caseTitle(120, 34, '여기까지 왔다')
    + caseTitle(560, 34, '여기서 시작한다')
    + node(120, 80, '확률 12장', { rx: RDOC })
    + tag(120, 122, '상관과 인과는 다르다')
    + node(120, 168, '이산수학', { rx: RDOC })
    + tag(120, 210, '숫자가 왜 뒤집히는가')
    + node(120, 252, '심리학 4장', { rx: RDOC })
    + tag(120, 294, '어떤 설계를 쓰는가')
    + node(560, 168, '이 문서 3장', { role: 'y', rx: RHERE })
    + tag(560, 212, '그러면 무엇을 봐야 하는가')
    + edge(120, 80, 560, 168, { from: RDOC, to: RHERE })
    + edge(120, 168, 560, 168, { from: RDOC, to: RHERE })
    + edge(120, 252, 560, 168, { from: RDOC, to: RHERE });

/* ── 2. 같은 상관과 들어맞는 이야기 셋 ──────────────────────── */

const threeStories = panel(10, 40, 225, 190)
    + caseTitle(122, 30, '처치가 결과를 바꾼다')
    + node(70, 118, '처치', { role: 'x' })
    + node(175, 118, '결과', { role: 'y' })
    + edge(70, 118, 175, 118, { tone: 's1' })
    + tag(122, 190, '상관이 생긴다')

    + panel(247, 40, 225, 190)
    + caseTitle(359, 30, '결과가 처치를 바꾼다')
    + node(307, 118, '처치', { role: 'x' })
    + node(412, 118, '결과', { role: 'y' })
    + edge(412, 118, 307, 118, { tone: 's2' })
    + tag(359, 190, '같은 상관이 생긴다')

    + panel(484, 40, 225, 190)
    + caseTitle(596, 30, '다른 것이 둘 다를 바꾼다')
    + node(596, 72, '다른 것', { rx: 42 })
    + node(544, 158, '처치', { role: 'x' })
    + node(649, 158, '결과', { role: 'y' })
    + edge(596, 72, 544, 158, { from: 42, tone: 's3' })
    + edge(596, 72, 649, 158, { from: 42, tone: 's3' })
    + tag(596, 212, '역시 같은 상관이 생긴다');

/* ── 3. 합친 표와 나눈 표에서 방향이 반대가 된다 ─────────────── */

const flip = panel(15, 55, 300, 200)
    + caseTitle(165, 42, '합쳐서 세면')
    + caseTitle(95, 88, '학습 방식')
    + caseTitle(245, 88, '합격률')
    + POOLED.map((r, i) => tag(95, 126 + i * 38, r.arm) + tag(245, 126 + i * 38, r.rate)).join('')
    + tag(165, 215, '기존 방식이 높다')

    + panel(340, 55, 345, 200)
    + caseTitle(512, 42, '과목으로 나눠서 세면')
    + caseTitle(400, 88, '과목')
    + caseTitle(510, 88, '새 방식')
    + caseTitle(615, 88, '기존 방식')
    + SPLIT.map((r, i) => tag(400, 126 + i * 38, r.stratum)
        + tag(510, 126 + i * 38, r.neu) + tag(615, 126 + i * 38, r.old)).join('')
    + tag(512, 215, '두 과목 다 새 방식이 높다')

    + caseTitle(350, 285, '같은 사람들을 센 것인데 대소가 반대다');

/* ── 4. 나눈 기준이 처치보다 먼저 정해진 경우 ───────────────── */

const RARM = 52, ROUT = 34;

const before = node(110, 165, '학습 방식', { role: 'x', rx: RARM })
    + node(490, 165, '합격', { role: 'y', rx: ROUT })
    + node(300, 62, '응시 과목', { rx: RARM })
    + edge(300, 62, 110, 165, { from: RARM, to: RARM })
    + edge(300, 62, 490, 165, { from: RARM, to: ROUT })
    + edge(110, 165, 490, 165, { from: RARM, to: ROUT, tone: 's1' })
    + tag(150, 112, '방식 선택을 바꾼다')
    + tag(442, 108, '합격률을 바꾼다')
    + tag(300, 196, '알고 싶은 것은 이 화살표다')
    + caseTitle(300, 232, '과목이 방식보다 먼저 정해졌다면 과목으로 나눠서 본다');

/* ── 5. 나눈 기준이 처치의 결과로 생긴 경우 ─────────────────── */

const after = node(105, 105, '학습 방식', { role: 'x', rx: RARM })
    + node(305, 105, '응시 과목', { rx: RARM })
    + node(500, 105, '합격', { role: 'y', rx: ROUT })
    + edge(105, 105, 305, 105, { from: RARM, to: RARM, tone: 's1' })
    + edge(305, 105, 500, 105, { from: RARM, to: ROUT, tone: 's1' })
    + edge(105, 105, 500, 105, { from: RARM, to: ROUT, tone: 's1', bow: 110 })
    + tag(205, 88, '뒤에 정해진다')
    + tag(403, 88, '합격률을 바꾼다')
    + tag(305, 192, '방식이 곧바로 바꾸는 몫')
    + caseTitle(300, 232, '과목이 방식의 결과로 생긴 것이라면 나누지 않는다');

/* ── 6. 같은 표, 두 이야기, 두 답 ───────────────────────────── */

const sameNumbers = caseTitle(105, 78, '표의 숫자는 하나뿐')
    + panel(20, 92, 170, 108)
    + tag(105, 128, '합치면 58% 대 76%')
    + tag(105, 158, '나누면 새 방식이 높다')

    + panel(300, 30, 380, 100)
    + caseTitle(490, 22, '과목이 방식보다 먼저 정해졌다')
    + tag(490, 66, '나눈 표를 본다')
    + tag(490, 96, '새 방식이 낫다')

    + panel(300, 175, 380, 100)
    + caseTitle(490, 167, '과목이 방식의 결과로 생겼다')
    + tag(490, 211, '합친 표를 본다')
    + tag(490, 241, '새 방식이 낫지 않다')

    + edge(195, 138, 298, 82, { from: 4, to: 4 })
    + edge(195, 152, 298, 216, { from: 4, to: 4 })
    + caseTitle(350, 292, '갈림을 만든 것은 표가 아니라 표 밖에서 가져온 이야기다');

/* ── 7. 자료와 자료 밖의 것이 둘 다 있어야 한다 ─────────────── */

const RDATA = 40, RKNOW = 95, RANS = 78;

const outside = node(120, 70, '자료', { rx: RDATA })
    + node(120, 175, '자료 밖에서 아는 것', { rx: RKNOW })
    + node(470, 122, '어느 표를 볼지', { role: 'y', rx: RANS })
    + edge(120, 70, 470, 122, { from: RDATA, to: RANS, tone: 's1' })
    + edge(120, 175, 470, 122, { from: RKNOW, to: RANS, tone: 's2' })
    + tag(300, 78, '숫자를 준다')
    + tag(310, 186, '무엇이 먼저 오는지를 준다')
    + caseTitle(320, 222, '둘 중 하나만으로는 답이 나오지 않는다');

/* ── 8. 예측하는 물음과 바꾸는 물음 ─────────────────────────── */

const RPAST = 62, RNEXT = 62, RCUT = 62, RSALE = 34;

const predictAct = panel(15, 45, 340, 165)
    + caseTitle(185, 32, '예측하는 물음')
    + node(95, 105, '지난 달 매출', { rx: RPAST })
    + node(275, 105, '다음 달 매출', { role: 'y', rx: RNEXT })
    + edge(95, 105, 275, 105, { from: RPAST, to: RNEXT })
    + tag(185, 178, '세상을 그대로 두고 본다')

    + panel(375, 45, 310, 165)
    + caseTitle(530, 32, '바꾸는 물음')
    + node(455, 105, '가격을 내린다', { role: 'x', rx: RCUT })
    + node(615, 105, '매출', { role: 'y', rx: RSALE })
    + edge(455, 105, 615, 105, { from: RCUT, to: RSALE, tone: 's1' })
    + tag(530, 178, '세상에 손을 대고 본다')

    + caseTitle(350, 238, '왼쪽은 자료만으로 답할 수 있고 오른쪽은 그렇지 않다');

export default [
    {
        name: 'ci-w-stopped',
        svg: svg({
            width: 700, height: 310,
            title: '다른 문서 셋이 멈춘 자리와 이 장이 이어받는 물음',
            desc: '확률 12장, 이산수학, 심리학 4장에서 각각 화살표가 나와 이 문서 3장으로 모인다',
            body: stopped,
        }),
    },
    {
        name: 'ci-w-three-stories',
        svg: svg({
            width: 720, height: 250,
            title: '같은 상관을 낳는 서로 다른 이야기 셋',
            desc: '처치가 결과를 바꾸는 경우, 결과가 처치를 바꾸는 경우, 다른 것이 둘 다를 바꾸는 경우',
            body: threeStories,
        }),
    },
    {
        name: 'ci-w-simpson-flip',
        svg: svg({
            width: 700, height: 300,
            title: '합쳐서 센 표와 나눠서 센 표의 대소가 반대가 된다',
            desc: '합치면 기존 방식이 높고 과목으로 나누면 두 과목 모두 새 방식이 높다',
            body: flip,
        }),
    },
    {
        name: 'ci-w-before',
        svg: svg({
            width: 620, height: 250,
            title: '나눈 기준이 처치보다 먼저 정해진 경우',
            desc: '응시 과목에서 학습 방식과 합격 양쪽으로 화살표가 나가고 학습 방식에서 합격으로도 화살표가 간다',
            body: before,
        }),
    },
    {
        name: 'ci-w-after',
        svg: svg({
            width: 620, height: 250,
            title: '나눈 기준이 처치의 결과로 생긴 경우',
            desc: '학습 방식에서 응시 과목으로, 응시 과목에서 합격으로 화살표가 가고 학습 방식에서 합격으로 가는 화살표가 따로 있다',
            body: after,
        }),
    },
    {
        name: 'ci-w-same-numbers',
        svg: svg({
            width: 700, height: 300,
            title: '같은 표에서 배경 이야기에 따라 답이 갈린다',
            desc: '한 표에서 화살표 둘이 갈라져 서로 반대되는 두 답으로 간다',
            body: sameNumbers,
        }),
    },
    {
        name: 'ci-w-outside',
        svg: svg({
            width: 640, height: 240,
            title: '자료와 자료 밖에서 아는 것이 함께 답을 정한다',
            desc: '자료와 자료 밖에서 아는 것 두 노드에서 어느 표를 볼지로 화살표가 모인다',
            body: outside,
        }),
    },
    {
        name: 'ci-w-predict-act',
        svg: svg({
            width: 700, height: 250,
            title: '예측하는 물음과 세상을 바꾸는 물음',
            desc: '왼쪽 칸은 지난 달 매출에서 다음 달 매출로, 오른쪽 칸은 가격을 내리는 일에서 매출로 화살표가 간다',
            body: predictAct,
        }),
    },
];
