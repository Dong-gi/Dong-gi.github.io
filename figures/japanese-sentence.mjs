/**
 * 8장(문장의 뼈대) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 예문은 본문에서 한자로 적히지만 그림에서는 전부 가나로 적는다.
 * 뼈대를 보이는 데 필요한 것은 낱말의 자리와 조사이고, 둘 다 가나로 그려진다.
 * 한자는 본문의 +jp 로만 나간다(원장 §5.1).
 */
import { svg, px, txt } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

const GREY = 'var(--ink2)';
const GRID = 'var(--grid)';
const BLUE = 'var(--s1)';
const ORANGE = 'var(--s2)';
const GREEN = 'var(--s3)';

/** 사각 상자 하나. lines 는 문자열 또는 문자열 배열. */
function box(x, y, w, h, lines, o = {}) {
    const ls = Array.isArray(lines) ? lines : [lines];
    const gap = o.gap || 17;
    const top = y + h / 2 - ((ls.length - 1) * gap) / 2 + 4;
    const attrs = [
        `x="${x}"`, `y="${y}"`, `width="${w}"`, `height="${h}"`,
        `rx="${o.rx === undefined ? 6 : o.rx}"`,
        `fill="${o.fill || 'none'}"`,
        `stroke="${o.stroke || GREY}"`,
        `stroke-width="${o.sw || 1.4}"`,
    ];
    if (o.dash) attrs.push(`stroke-dasharray="${o.dash}"`);
    return `<rect ${attrs.join(' ')}/>`
        + ls.map((t, i) => txt(x + w / 2, top + i * gap, t, {
            anchor: 'middle', cls: o.cls || 'ink', size: o.size,
        })).join('');
}

/** 화살표 없는 이음선. */
function link(x1, y1, x2, y2, o = {}) {
    return `<path fill="none" stroke="${o.stroke || GRID}" stroke-width="${o.sw || 1.2}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-t-word-order-mirror',
        svg: svg({
            width: 760,
            height: 292,
            title: '한국어 문장과 일본어 문장의 자리 대응',
            desc: '주어 · 목적어 · 술어의 자리와 조사가 붙는 자리가 두 언어에서 하나씩 맞물린다.',
            body: J(
                txt(145, 54, '주어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(226, 54, '조사', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(350, 54, '목적어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(426, 54, '조사', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(605, 54, '술어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(20, 98, '한국어', { cls: 'ink2', size: 'sm' }),
                box(90, 74, 110, 38, '나'),
                box(204, 74, 44, 38, '가', { stroke: ORANGE }),
                box(300, 74, 100, 38, '책'),
                box(404, 74, 44, 38, '을', { stroke: ORANGE }),
                box(510, 74, 190, 38, '읽습니다', { stroke: BLUE }),
                txt(20, 198, '일본어', { cls: 'ink2', size: 'sm' }),
                box(90, 174, 110, 38, 'わたし'),
                box(204, 174, 44, 38, 'が', { stroke: ORANGE }),
                box(300, 174, 100, 38, 'ほん'),
                box(404, 174, 44, 38, 'を', { stroke: ORANGE }),
                box(510, 174, 190, 38, 'よみます', { stroke: BLUE }),
                link(145, 112, 145, 174, { dash: '4 3' }),
                link(226, 112, 226, 174, { dash: '4 3', stroke: ORANGE }),
                link(350, 112, 350, 174, { dash: '4 3' }),
                link(426, 112, 426, 174, { dash: '4 3', stroke: ORANGE }),
                link(605, 112, 605, 174, { dash: '4 3', stroke: BLUE }),
                txt(90, 248, '자리가 하나씩 맞물린다. 조사가 붙는 자리까지 같다', { cls: 'ink2', size: 'sm' }),
                txt(90, 270, '어순을 새로 익힐 것이 없다는 뜻이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-t-order-free-predicate-last',
        svg: svg({
            width: 760,
            height: 332,
            title: '순서를 바꿀 수 있는 자리와 바꿀 수 없는 자리',
            desc: '같은 문장을 세 순서로 놓았다. 앞의 둘은 성립하고, 술어를 끝에 두지 않은 셋째는 성립하지 않는다.',
            body: J(
                txt(60, 30, '같은 문장을 순서만 바꿔 본다. 색이 든 칸이 술어다', { cls: 'ink2', size: 'sm' }),
                txt(60, 54, '기본 순서', { cls: 'ink2', size: 'sm' }),
                box(60, 60, 175, 40, 'やまださんが'),
                box(245, 60, 150, 40, 'ほんを'),
                box(405, 60, 180, 40, 'よみます', { stroke: BLUE }),
                txt(600, 84, '뜻이 정해진다', { cls: 'ink2', size: 'sm' }),
                txt(60, 134, '덩어리 순서를 바꾼 것', { cls: 'ink2', size: 'sm' }),
                box(60, 140, 150, 40, 'ほんを'),
                box(220, 140, 175, 40, 'やまださんが'),
                box(405, 140, 180, 40, 'よみます', { stroke: BLUE }),
                txt(600, 164, '뜻이 같다', { cls: 'ink2', size: 'sm' }),
                txt(60, 214, '술어를 끝에 두지 않은 것', { cls: 'f2', size: 'sm' }),
                box(60, 220, 175, 40, 'やまださんが'),
                box(245, 220, 180, 40, 'よみます', { stroke: ORANGE }),
                box(435, 220, 150, 40, 'ほんを'),
                txt(600, 244, '성립하지 않는다', { cls: 'f2', size: 'sm' }),
                txt(60, 294, '바꿀 수 있는 것은 조사가 붙은 덩어리끼리의 순서다', { cls: 'ink2', size: 'sm' }),
                txt(60, 316, '술어의 자리는 바꿀 수 없다', { cls: 'f2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-t-case-particles',
        svg: svg({
            width: 760,
            height: 304,
            title: '조사가 낱말과 술어의 관계를 정한다',
            desc: '세 조사가 붙은 낱말이 각각 다른 구실로 술어에 이어진다. 화살표를 정하는 것은 자리가 아니라 조사다.',
            body: J(
                txt(50, 32, '낱말 뒤에 붙은 조사가 그 낱말과 술어의 관계를 정한다', { cls: 'ink2', size: 'sm' }),
                box(50, 60, 170, 42, '낱말 + が', { stroke: BLUE }),
                box(50, 124, 170, 42, '낱말 + を', { stroke: ORANGE }),
                box(50, 188, 170, 42, '낱말 + に', { stroke: GREEN }),
                px(224, 81, 516, 140, { cls: 's1', marker: 'ar1' }),
                px(224, 145, 516, 152, { cls: 's2', marker: 'ar2' }),
                px(224, 209, 516, 166, { cls: 's3', marker: 'ar3' }),
                box(520, 118, 190, 62, '술어', { sw: 1.8 }),
                txt(250, 74, '하는 쪽', { cls: 'f1', size: 'sm' }),
                txt(250, 136, '동작이 미치는 쪽', { cls: 'f2', size: 'sm' }),
                txt(250, 226, '도착하는 곳이나 상대', { cls: 'f3', size: 'sm' }),
                txt(50, 266, '덩어리의 자리를 바꾸어도 이 화살표는 바뀌지 않는다', { cls: 'ink2', size: 'sm' }),
                txt(50, 288, '화살표를 정하는 것은 자리가 아니라 조사다', {}),
            ),
        }),
    },
    {
        name: 'jp-t-three-sentence-types',
        svg: svg({
            width: 760,
            height: 292,
            title: '술어 자리에 오는 것이 문장의 갈래를 정한다',
            desc: '앞부분은 같고 술어 자리만 다른 세 문장이 명사문 · 형용사문 · 동사문으로 갈린다.',
            body: J(
                txt(50, 32, '앞부분이 무엇이든 갈래는 끝의 술어가 정한다', { cls: 'ink2', size: 'sm' }),
                box(50, 58, 190, 44, '앞부분', { dash: '5 4' }),
                box(50, 122, 190, 44, '앞부분', { dash: '5 4' }),
                box(50, 186, 190, 44, '앞부분', { dash: '5 4' }),
                box(260, 58, 215, 44, 'がくせいです', { stroke: BLUE }),
                box(260, 122, 215, 44, 'おもしろいです', { stroke: ORANGE }),
                box(260, 186, 215, 44, 'よみます', { stroke: GREEN }),
                txt(495, 85, '명사문 — 명사가 술어', { cls: 'f1' }),
                txt(495, 149, '형용사문 — 형용사가 술어', { cls: 'f2' }),
                txt(495, 213, '동사문 — 동사가 술어', { cls: 'f3' }),
                txt(50, 266, '위의 두 갈래는 끝 글자가 같다. 갈래를 가르는 것은 그 앞의 낱말이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-t-predicate-layers',
        svg: svg({
            width: 760,
            height: 344,
            title: '술어 뒤에 층이 쌓이는 모양',
            desc: '네 문장에서 뜻을 지는 부분은 그대로이고 그 뒤에 정중함 · 부정 · 때의 칸이 차례로 쌓인다.',
            body: J(
                txt(14, 32, '한국어', { cls: 'ink2', size: 'sm' }),
                txt(120, 32, '일본어 — 뜻을 지는 부분은 왼쪽에 한 번만 나온다', { cls: 'ink2', size: 'sm' }),
                txt(14, 94, '읽습니다', { cls: 'ink2', size: 'sm' }),
                box(120, 70, 90, 38, 'よみ'),
                box(210, 70, 74, 38, 'ます', { stroke: BLUE }),
                txt(480, 94, '정중함', { cls: 'ink2', size: 'sm' }),
                txt(14, 154, '읽지 않습니다', { cls: 'ink2', size: 'sm' }),
                box(120, 130, 90, 38, 'よみ'),
                box(210, 130, 74, 38, 'ませ', { stroke: BLUE }),
                box(284, 130, 46, 38, 'ん', { stroke: ORANGE }),
                txt(480, 154, '정중함 + 부정', { cls: 'ink2', size: 'sm' }),
                txt(14, 214, '읽었습니다', { cls: 'ink2', size: 'sm' }),
                box(120, 190, 90, 38, 'よみ'),
                box(210, 190, 74, 38, 'まし', { stroke: BLUE }),
                box(284, 190, 46, 38, 'た', { stroke: GREEN }),
                txt(480, 214, '정중함 + 때', { cls: 'ink2', size: 'sm' }),
                txt(14, 274, '읽지 않았습니다', { cls: 'ink2', size: 'sm' }),
                box(120, 250, 90, 38, 'よみ'),
                box(210, 250, 74, 38, 'ませ', { stroke: BLUE }),
                box(284, 250, 46, 38, 'ん', { stroke: ORANGE }),
                box(330, 250, 94, 38, 'でした', { stroke: GREEN }),
                txt(480, 274, '정중함 + 부정 + 때', { cls: 'ink2', size: 'sm' }),
                txt(120, 322, '파란 칸이 정중함, 주황 칸이 부정, 초록 칸이 때다. 왼쪽 끝은 바뀌지 않는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-t-layer-map',
        svg: svg({
            width: 760,
            height: 352,
            title: '문장 끝에 붙는 층의 순서와 그 층을 다루는 장',
            desc: '여덟 층이 안쪽에서 바깥쪽으로 정해진 순서로 붙고, 층마다 다루는 장이 다르다.',
            body: J(
                txt(30, 30, '위가 뜻에 가까운 안쪽, 아래가 문장 끝이다', { cls: 'ink2', size: 'sm' }),
                px(78, 48, 78, 320, { cls: 'ax', marker: 'ark' }),
                txt(30, 52, '안쪽', { cls: 'ink2', size: 'sm' }),
                txt(24, 318, '바깥쪽', { cls: 'ink2', size: 'sm' }),
                box(110, 44, 500, 28, '뜻을 지는 부분', { sw: 1.8 }),
                txt(626, 62, '11장 · 12장', { cls: 'ink2', size: 'sm' }),
                box(110, 78, 500, 28, '하는 쪽과 받는 쪽의 관계', { stroke: GRID }),
                txt(626, 96, '14장', { cls: 'ink2', size: 'sm' }),
                box(110, 112, 500, 28, '모습 — 진행 중인가, 끝난 뒤의 상태인가', { stroke: GRID }),
                txt(626, 130, '13장', { cls: 'ink2', size: 'sm' }),
                box(110, 146, 500, 28, '정중함', { stroke: BLUE }),
                txt(626, 164, '16장 · 18장', { cls: 'ink2', size: 'sm' }),
                box(110, 180, 500, 28, '부정', { stroke: ORANGE }),
                txt(626, 198, '11장 · 12장', { cls: 'ink2', size: 'sm' }),
                box(110, 214, 500, 28, '때 — 지난 일인가', { stroke: GREEN }),
                txt(626, 232, '13장', { cls: 'ink2', size: 'sm' }),
                box(110, 248, 500, 28, '태도 — 단정인가, 추측인가', { stroke: GRID }),
                txt(626, 266, '17장', { cls: 'ink2', size: 'sm' }),
                box(110, 282, 500, 28, '문장 끝의 짧은 말 — 물음도 여기 든다', { stroke: GRID }),
                txt(626, 300, '17장', { cls: 'ink2', size: 'sm' }),
                txt(110, 340, '이 순서는 정해져 있다. 층을 바꿔 붙일 수 없다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-t-ellipsis-restore',
        svg: svg({
            width: 760,
            height: 306,
            title: '표시되지 않은 자리를 무엇으로 되찾는가',
            desc: '문장에 빈 자리가 있고, 문맥과 문장의 다른 층이 그 자리를 채운다.',
            body: J(
                txt(40, 32, '문장에 주어가 표시되지 않았다', { cls: 'ink2', size: 'sm' }),
                txt(295, 54, '표시되지 않은 자리', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                box(230, 62, 130, 42, '', { dash: '5 4', stroke: ORANGE }),
                box(364, 62, 130, 42, 'ほんを'),
                box(498, 62, 170, 42, 'よみました', { stroke: BLUE }),
                px(120, 166, 270, 110, { cls: 's3', marker: 'ar3' }),
                px(350, 166, 300, 110, { cls: 's3', marker: 'ar3' }),
                px(570, 166, 330, 110, { cls: 's3', marker: 'ar3' }),
                box(40, 170, 175, 56, ['앞뒤 문맥', '이미 나온 것'], { stroke: GREEN }),
                box(262, 170, 175, 56, ['높임의 표시', '16장'], { stroke: GREEN }),
                box(484, 170, 175, 56, ['주고받기 표현', '15장'], { stroke: GREEN }),
                txt(40, 268, '빈 자리를 채우는 근거는 문장 밖과 문장의 다른 층에 있다', { cls: 'ink2', size: 'sm' }),
                txt(40, 290, '없는 것이 아니라 표시되지 않은 것이다', { cls: 'f2' }),
            ),
        }),
    },
    {
        name: 'jp-t-read-procedure',
        svg: svg({
            width: 760,
            height: 350,
            title: '일본어 문장을 읽는 순서',
            desc: '문장의 끝을 먼저 보고 술어를 잡은 뒤, 갈래와 층을 확인하고 앞의 덩어리로 되돌아온다.',
            body: J(
                txt(40, 26, '앞에서부터 읽어 나가지 않는다. 끝을 먼저 본다', { cls: 'ink2', size: 'sm' }),
                txt(92, 62, '1', { anchor: 'middle', cls: 'ink2' }),
                box(120, 40, 520, 38, '문장의 끝을 본다 — 거기 있는 것이 술어다', { stroke: BLUE }),
                px(380, 82, 380, 98, { cls: 'ax', marker: 'ark' }),
                txt(92, 122, '2', { anchor: 'middle', cls: 'ink2' }),
                box(120, 100, 520, 38, '술어가 무엇인지 보고 갈래를 정한다', {}),
                px(380, 142, 380, 158, { cls: 'ax', marker: 'ark' }),
                txt(92, 182, '3', { anchor: 'middle', cls: 'ink2' }),
                box(120, 160, 520, 38, '술어에 붙은 층을 벗겨 읽는다 — 부정인가, 지난 일인가', {}),
                px(380, 202, 380, 218, { cls: 'ax', marker: 'ark' }),
                txt(92, 242, '4', { anchor: 'middle', cls: 'ink2' }),
                box(120, 220, 520, 38, '앞으로 되돌아가 조사를 보고 덩어리의 구실을 짚는다', {}),
                px(380, 262, 380, 278, { cls: 'ax', marker: 'ark' }),
                txt(92, 302, '5', { anchor: 'middle', cls: 'ink2' }),
                box(120, 280, 520, 38, '표시되지 않은 자리가 있으면 문맥으로 채운다', { stroke: GREEN }),
                txt(120, 342, '술어를 잡기 전에는 앞의 덩어리가 무엇에 대한 것인지 정해지지 않는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
