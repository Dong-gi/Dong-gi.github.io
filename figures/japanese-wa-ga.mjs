/**
 * 10장(は 와 が) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장은 조사 두 개를 다루는 장이라 그림거리가 모두 구조다. 조사 자체는
 * 가나이므로 그림에 그대로 넣을 수 있고, 예문에 쓰이는 낱말은 가나로만 적었다.
 * 낱말의 뜻을 보여야 하는 자리는 한글 라벨로 대신한다.
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
    const ls = (Array.isArray(lines) ? lines : [lines]).filter(t => t !== '');
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
    return `<path fill="none" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.2}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;
}

/** 채운 동그라미와 빈 동그라미. 후보 가운데 하나만 골랐음을 보인다. */
function dotFilled(cx, cy, r, color) {
    return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}"/>`;
}
function dotEmpty(cx, cy, r, o = {}) {
    return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${o.stroke || GRID}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} stroke-width="1.4"/>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-h-wa-covers-case',
        svg: svg({
            width: 760,
            height: 336,
            title: '주제 표시가 격 자리에 놓일 때의 형태',
            desc: '격을 표시하는 조사 여섯 갈래가 주제 표시와 만날 때, 위의 둘은 사라지고 아래 넷은 남아 뒤에 주제 표시가 붙는다.',
            body: J(
                txt(60, 38, '격을 표시하는 조사가 붙은 자리', { cls: 'ink2', size: 'sm' }),
                txt(285, 38, '주제로 올리면', { cls: 'ink2', size: 'sm' }),
                txt(465, 38, '무엇이 일어나는가', { cls: 'ink2', size: 'sm' }),
                box(60, 50, 110, 30, 'が', { stroke: BLUE }),
                box(60, 90, 110, 30, 'を', { stroke: BLUE }),
                box(60, 130, 110, 30, 'に', { stroke: GREEN }),
                box(60, 170, 110, 30, 'で', { stroke: GREEN }),
                box(60, 210, 110, 30, 'へ', { stroke: GREEN }),
                box(60, 250, 110, 30, 'から', { stroke: GREEN }),
                px(176, 65, 274, 65, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(176, 105, 274, 105, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(176, 145, 274, 145, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(176, 185, 274, 185, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(176, 225, 274, 225, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(176, 265, 274, 265, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(280, 50, 150, 30, 'は', { stroke: BLUE }),
                box(280, 90, 150, 30, 'は', { stroke: BLUE }),
                box(280, 130, 150, 30, 'には', { stroke: GREEN }),
                box(280, 170, 150, 30, 'では', { stroke: GREEN }),
                box(280, 210, 150, 30, 'へは', { stroke: GREEN }),
                box(280, 250, 150, 30, 'からは', { stroke: GREEN }),
                box(455, 50, 265, 70, ['격을 표시하던 조사가 사라진다', '자리는 그대로다'],
                    { stroke: BLUE, size: 'sm' }),
                box(455, 130, 265, 150, ['격을 표시하던 조사가 남고', '그 뒤에 주제 표시가 붙는다'],
                    { stroke: GREEN, size: 'sm' }),
                txt(60, 312, '주제로 올릴 때 격 표시가 지워지는 것과 남는 것이 갈린다. 자리 자체는 어느 쪽도 바뀌지 않는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-overlap-map',
        svg: svg({
            width: 760,
            height: 352,
            title: '한국어의 두 대립과 일본어의 두 조사가 겹치는 범위',
            desc: '가운데 칸이 두 언어에 함께 있는 범위이고 양쪽 칸이 한쪽에만 있는 것이다. 가운데 칸이 넓다.',
            body: J(
                txt(40, 32, '한국어의 대립과 일본어의 두 조사를 나란히 놓고 겹치는 만큼을 가운데에 모았다',
                    { cls: 'ink2', size: 'sm' }),
                `<path fill="none" stroke="${ORANGE}" stroke-width="1.8" d="M40 78 V66 H502 V78"/>`,
                txt(271, 58, '한국어 — 은/는 대 이/가', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 86, 200, 150, ['한국어 쪽만', ' ', '아님 앞에', '이/가 가 온다', ' ', '좋아함을', '을/를 로 나타낸다'],
                    { stroke: ORANGE, size: 'sm', gap: 19 }),
                box(252, 86, 250, 150, ['겹치는 범위', ' ', '주제 자리에 은/는 과 は', '주어 자리에 이/가 와 が', ' ', '대비도 양쪽이 함께 한다'],
                    { stroke: GREEN, gap: 19 }),
                box(514, 86, 206, 150, ['일본어 쪽만', ' ', '절 안에서는', 'が 만 온다', ' ', '대상에도 が 가 온다', '아님을 말할 때 では'],
                    { stroke: BLUE, size: 'sm', gap: 19 }),
                `<path fill="none" stroke="${BLUE}" stroke-width="1.8" d="M252 244 V256 H720 V244"/>`,
                txt(486, 276, '일본어 — は 대 が', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(40, 312, '겹치는 범위가 넓다는 것이 이득이고, 같은 이유로 함정이다', {}),
                txt(40, 338, '양쪽 칸이 좁아서 알아채지 못하고 지나가게 된다. 이 장은 그 두 칸에 지면을 쓴다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-clause-inside',
        svg: svg({
            width: 760,
            height: 326,
            title: '절 안과 절 밖에 무엇이 오는가',
            desc: '명사를 꾸미는 절 안에는 주격 표시만 오고 주제 표시는 오지 못한다. 절 밖의 주제 표시는 문장 전체에 걸린다.',
            body: J(
                txt(40, 34, '한 문장 안에 절이 들어 있을 때 어느 쪽에 무엇이 오는가', { cls: 'ink2', size: 'sm' }),
                box(40, 54, 680, 150, '', { sw: 1.8 }),
                txt(58, 78, '문장', { cls: 'ink2', size: 'sm' }),
                box(70, 92, 330, 94, '', { dash: '5 4' }),
                txt(88, 112, '절 — 명사를 꾸미는 자리', { cls: 'ink2', size: 'sm' }),
                box(90, 126, 130, 44, ['が', '온다'], { stroke: GREEN }),
                box(250, 126, 130, 44, ['は', '못 온다'], { stroke: ORANGE, dash: '5 4' }),
                txt(450, 112, '절 밖 — 문장의 주제 자리', { cls: 'ink2', size: 'sm' }),
                box(450, 126, 150, 44, ['は', '온다'], { stroke: GREEN }),
                link(404, 148, 446, 148, { stroke: GRID, dash: '4 3' }),
                txt(40, 240, '절 안에서는 고를 여지가 없다. 아는 것이든 처음 나오는 것이든 が 가 온다', {}),
                txt(40, 268, '한국어도 이 자리에 은/는 을 넣지 못한다. 규칙의 모양은 겹친다', { cls: 'ink2', size: 'sm' }),
                txt(40, 294, '어긋나는 것은 판단의 근거다. 아는 것이니 은/는 이라는 감각이 여기서 헛돈다', { cls: 'ink2', size: 'sm' }),
                txt(40, 318, '문장 앞의 は 는 절 안으로 들어가지 않고 문장 전체에 걸린다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-exclusive-ga',
        svg: svg({
            width: 760,
            height: 316,
            title: '같은 물음에 두 조사로 답할 때 갈리는 뜻',
            desc: '주격 표시로 답하면 다른 후보를 닫고, 주제 표시로 답하면 다른 후보에 대해서는 아무 말도 하지 않는다.',
            body: J(
                txt(40, 32, '누가 하는가를 묻는 물음에 두 가지로 답할 수 있다', { cls: 'ink2', size: 'sm' }),
                box(40, 46, 190, 38, 'だれが', { stroke: BLUE }),
                txt(244, 70, '물음이 한 자리를 비워 두고 묻는다', { cls: 'ink2', size: 'sm' }),
                box(40, 118, 190, 40, 'わたしが', { stroke: GREEN }),
                dotFilled(290, 138, 15, 'var(--s3)'),
                dotEmpty(336, 138, 15),
                dotEmpty(382, 138, 15),
                txt(290, 172, '나', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(320, 172, '다른 후보', { cls: 'ink2', size: 'sm' }),
                txt(430, 134, '나이고 다른 사람은 아니다', {}),
                txt(430, 154, '비워 둔 자리를 채운다. 다른 후보를 닫는다', { cls: 'ink2', size: 'sm' }),
                box(40, 216, 190, 40, 'わたしは', { stroke: ORANGE }),
                dotFilled(290, 236, 15, 'var(--s2)'),
                dotEmpty(336, 236, 15, { dash: '4 3' }),
                dotEmpty(382, 236, 15, { dash: '4 3' }),
                txt(290, 270, '나', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(320, 270, '다른 후보', { cls: 'ink2', size: 'sm' }),
                txt(430, 232, '나에 대해 말하면 그렇다', {}),
                txt(430, 252, '다른 후보에 대해서는 아무 말도 하지 않는다', { cls: 'ink2', size: 'sm' }),
                txt(40, 302, '이것이 두 조사의 뜻 차이가 가장 뚜렷하게 드러나는 자리다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-topic-scope',
        svg: svg({
            width: 760,
            height: 320,
            title: '주제가 걸치는 범위와 주격이 걸치는 범위',
            desc: '주제는 첫 문장에서 한 번 표시되고 뒤 문장까지 걸친다. 뒤 문장의 빈 자리는 그 주제가 채운다.',
            body: J(
                txt(40, 34, '이어지는 세 문장', { cls: 'ink2', size: 'sm' }),
                box(150, 52, 510, 46, '', { dash: '4 3' }),
                box(162, 62, 160, 26, '주제 + は', { stroke: GREEN, rx: 4, size: 'sm' }),
                box(336, 62, 150, 26, '무엇을 + を', { stroke: GRID, rx: 4, size: 'sm' }),
                box(500, 62, 148, 26, '술어', { rx: 4, size: 'sm' }),
                box(150, 116, 510, 46, '', { dash: '4 3' }),
                box(162, 126, 160, 26, '빈 자리', { stroke: ORANGE, dash: '4 3', rx: 4, size: 'sm' }),
                box(336, 126, 150, 26, '무엇을 + を', { stroke: GRID, rx: 4, size: 'sm' }),
                box(500, 126, 148, 26, '술어', { rx: 4, size: 'sm' }),
                box(150, 180, 510, 46, '', { dash: '4 3' }),
                box(162, 190, 160, 26, '빈 자리', { stroke: ORANGE, dash: '4 3', rx: 4, size: 'sm' }),
                box(336, 190, 150, 26, '무엇 + が', { stroke: BLUE, rx: 4, size: 'sm' }),
                box(500, 190, 148, 26, '술어', { rx: 4, size: 'sm' }),
                `<path fill="none" stroke="${GREEN}" stroke-width="1.8" d="M140 52 L128 52 L128 226 L140 226"/>`,
                txt(30, 130, '주제가', { cls: 'ink2', size: 'sm' }),
                txt(30, 148, '걸치는', { cls: 'ink2', size: 'sm' }),
                txt(30, 166, '범위', { cls: 'ink2', size: 'sm' }),
                `<path fill="none" stroke="${BLUE}" stroke-width="1.8" d="M670 180 L682 180 L682 226 L670 226"/>`,
                txt(692, 208, 'が 는', { cls: 'ink2', size: 'sm' }),
                txt(692, 226, '이 문장뿐', { cls: 'ink2', size: 'sm' }),
                txt(40, 262, '주제는 한 문장에 갇히지 않는다. 그래서 뒤 문장의 주어 자리가 빈다', {}),
                txt(40, 288, '빈 자리를 채우는 것은 앞 문장의 주제다. 새 주제가 나올 때까지 이어진다', { cls: 'ink2', size: 'sm' }),
                txt(40, 312, '주격 표시는 그 문장 하나에만 걸린다. 다음 문장으로 넘어가지 않는다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-new-old-flow',
        svg: svg({
            width: 760,
            height: 300,
            title: '처음 알리는 것과 이미 아는 것의 흐름',
            desc: '처음 내놓을 때는 주격 표시로 내고, 같은 것이 아는 것이 된 다음 문장에서는 주제 표시로 받는다.',
            body: J(
                txt(40, 32, '이야기가 시작될 때와 이어질 때 표시가 갈린다', { cls: 'ink2', size: 'sm' }),
                box(45, 52, 195, 56, ['처음 알리는 것', 'が 로 내놓는다'], { stroke: BLUE }),
                px(248, 80, 306, 80, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(312, 52, 190, 56, ['이제 서로', '아는 것이 되었다'], { dash: '5 4' }),
                px(510, 80, 568, 80, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(574, 52, 145, 56, ['は 로', '받는다'], { stroke: GREEN }),
                txt(45, 150, '첫 문장', { cls: 'ink2', size: 'sm' }),
                box(130, 132, 320, 32, '처음 나온 것 + が', { stroke: BLUE, size: 'sm' }),
                box(462, 132, 130, 32, '술어', { size: 'sm' }),
                txt(45, 198, '다음 문장', { cls: 'ink2', size: 'sm' }),
                box(130, 180, 320, 32, '같은 것 + は', { stroke: GREEN, size: 'sm' }),
                box(462, 180, 130, 32, '술어', { size: 'sm' }),
                link(290, 166, 290, 178, { stroke: GREEN, sw: 1.6 }),
                txt(40, 250, '거꾸로 하면 어긋난다. 처음 나오는 것을 주제로 내놓으면', {}),
                txt(40, 276, '읽는 사람은 그것이 무엇을 가리키는지 알 수 없다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-h-double-subject',
        svg: svg({
            width: 760,
            height: 318,
            title: '주제 하나 안에 주격 자리가 따로 있는 구조',
            desc: '주제가 문장 전체를 덮고 그 안에서 주격 표시가 붙은 자리와 술어가 하나의 말이 된다. 두 자리의 관계는 문장마다 다르다.',
            body: J(
                txt(40, 32, '두 자리가 겹쳐 보이지만 층이 다르다', { cls: 'ink2', size: 'sm' }),
                box(40, 50, 390, 130, '', { stroke: GREEN, sw: 1.8 }),
                txt(58, 74, '주제 + は', { cls: 'f3', size: 'sm' }),
                box(66, 88, 340, 76, '', { dash: '5 4' }),
                txt(84, 108, '이 안에서 무엇이 그런지 말한다', { cls: 'ink2', size: 'sm' }),
                box(86, 120, 140, 34, '무엇 + が', { stroke: BLUE, size: 'sm' }),
                box(246, 120, 140, 34, '술어', { size: 'sm' }),
                txt(456, 62, '두 자리의 관계는 문장마다 다르다', { cls: 'ink2', size: 'sm' }),
                box(456, 74, 264, 30, 'ぞう · はな — 가진 것', { stroke: ORANGE, size: 'sm' }),
                box(456, 112, 264, 30, 'みせ · ケーキ — 있는 곳', { stroke: ORANGE, size: 'sm' }),
                box(456, 150, 264, 30, 'わたし · あたま — 느끼는 사람', { stroke: ORANGE, size: 'sm' }),
                txt(40, 216, '두 자리를 갈라 주는 것이 주제와 주격의 구별이다', {}),
                txt(40, 242, '그러나 두 자리 사이의 관계가 무엇인지는 이 구별이 말해 주지 않는다', { cls: 'ink2', size: 'sm' }),
                txt(40, 266, '관계는 낱말의 뜻과 문맥이 정한다. 세 줄이 서로 다른 관계다', { cls: 'ink2', size: 'sm' }),
                txt(40, 300, '주어가 둘인 문장이 아니다. 층이 둘인 문장이다', {}),
            ),
        }),
    },
    {
        name: 'jp-h-decision-flow',
        svg: svg({
            width: 760,
            height: 424,
            title: '두 조사 가운데 하나를 고르는 절차',
            desc: '구조가 정해 놓은 자리를 먼저 걸러 내고, 남으면 정보의 흐름과 대비로 가른다. 마지막 칸은 근사다.',
            body: J(
                box(40, 26, 330, 40, '절 안의 주어인가 · 의문사인가', { stroke: BLUE }),
                box(430, 20, 290, 52, ['が', '고를 여지가 없다'], { stroke: GREEN }),
                px(374, 46, 426, 46, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(400, 38, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(205, 70, 205, 88, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(213, 86, '아니오', { cls: 'ink2', size: 'sm' }),
                box(40, 92, 330, 40, '처음 알리는 것인가 · 있음이나 날씨인가', { stroke: BLUE }),
                box(430, 86, 290, 52, ['が', '새로 내놓는다'], { stroke: GREEN }),
                px(374, 112, 426, 112, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(400, 104, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(205, 136, 205, 154, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(213, 152, '아니오', { cls: 'ink2', size: 'sm' }),
                box(40, 158, 330, 40, '앞에 나왔거나 서로 아는 것인가', { stroke: BLUE }),
                box(430, 152, 290, 52, ['は', '주제로 받는다'], { stroke: GREEN }),
                px(374, 178, 426, 178, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(400, 170, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(205, 202, 205, 220, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(213, 218, '아니오', { cls: 'ink2', size: 'sm' }),
                box(40, 224, 330, 40, '여러 개를 견주고 있는가', { stroke: BLUE }),
                box(430, 218, 290, 52, ['は', '대비'], { stroke: GREEN }),
                px(374, 244, 426, 244, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(400, 236, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(205, 268, 205, 286, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(213, 284, '아니오', { cls: 'ink2', size: 'sm' }),
                box(40, 290, 330, 40, '그것뿐이라고 못 박는가', { stroke: BLUE }),
                box(430, 284, 290, 52, ['が', '배타'], { stroke: GREEN }),
                px(374, 310, 426, 310, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(400, 302, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(205, 334, 205, 352, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(213, 350, '아니오', { cls: 'ink2', size: 'sm' }),
                box(40, 356, 680, 44,
                    ['여기까지 갈리지 않으면 は 를 놓고 문맥을 다시 본다 — 이 마지막 칸이 절차의 한계다'],
                    { stroke: ORANGE, dash: '5 4' }),
                txt(40, 418, '첫 칸만 구조가 정한다. 아래로 갈수록 문맥이 정하므로 절차가 근사가 된다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
