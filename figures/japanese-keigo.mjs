/**
 * 16장(경어) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 그림거리는 거의 다 관계다. 누가 안에 있고 누가 바깥에 있는지,
 * 높임이 어느 방향으로 걸리는지, 같은 꼴이 몇 갈래로 읽히는지. 사람과
 * 자리는 한글 라벨로 적고, 일본어를 보여야 하는 자리는 가나로만 적는다.
 * 경어 낱말은 한자로 적는 것이 보통이지만(召し上がる) 그림에서는 읽는 법
 * (めしあがる)으로 적었다. 같은 낱말의 한자 표기는 본문 표에 있다.
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
    const ls = (Array.isArray(lines) ? lines : [lines]).filter(t => t !== null);
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

/** 안과 바깥을 가르는 굵은 세로선. */
function divider(x, y1, y2, color) {
    return `<path fill="none" stroke="${color}" stroke-width="2.4" stroke-dasharray="8 5"`
        + ` d="M${x} ${y1} V${y2}"/>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-e-absolute-vs-relative',
        svg: svg({
            width: 760,
            height: 336,
            title: '두 언어에서 높임의 대상이 무엇으로 정해지는가',
            desc: '같은 사람에 대해 두 자리에서 말할 때, 왼쪽 언어는 두 자리에서 같은 높임을 쓰고 오른쪽 언어는 자리에 따라 갈린다.',
            body: J(
                txt(40, 30, '같은 사람(직장 상사)에 대해 말한다. 듣는 사람만 바꾼다', { cls: 'ink2', size: 'sm' }),

                txt(40, 62, '한국어 — 기준이 사람에게 있다', { cls: 'ink2', size: 'sm' }),
                box(40, 74, 150, 46, ['듣는 사람이', '같은 직장 사람'], { stroke: ORANGE, size: 'sm', gap: 16 }),
                box(40, 132, 150, 46, ['듣는 사람이', '다른 회사 사람'], { stroke: ORANGE, size: 'sm', gap: 16 }),
                px(196, 97, 236, 97, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(196, 155, 236, 155, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(242, 74, 118, 46, '높인다', { stroke: ORANGE }),
                box(242, 132, 118, 46, '높인다', { stroke: ORANGE }),
                box(40, 196, 320, 40, '두 자리에서 같다', { stroke: ORANGE, dash: '5 4' }),

                divider(388, 56, 250, GRID),

                txt(416, 62, '일본어 — 기준이 자리에 있다', { cls: 'ink2', size: 'sm' }),
                box(416, 74, 150, 46, ['듣는 사람이', '같은 직장 사람'], { stroke: BLUE, size: 'sm', gap: 16 }),
                box(416, 132, 150, 46, ['듣는 사람이', '다른 회사 사람'], { stroke: BLUE, size: 'sm', gap: 16 }),
                px(572, 97, 612, 97, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(572, 155, 612, 155, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(618, 74, 102, 46, '높인다', { stroke: BLUE }),
                box(618, 132, 102, 46, ['높이지', '않는다'], { stroke: GREEN, gap: 16, size: 'sm' }),
                box(416, 196, 304, 40, '두 자리에서 갈린다', { stroke: BLUE, dash: '5 4' }),

                txt(40, 268, '갈리는 것은 상사의 지위가 아니다. 지위는 두 자리에서 같다', {}),
                txt(40, 292, '갈리는 것은 그 상사가 말하는 자리에서 안에 있는지 바깥에 있는지다', {}),
                txt(40, 318, '왼쪽 열은 자리를 보지 않고 오른쪽 열은 자리를 먼저 본다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-boundary-moves',
        svg: svg({
            width: 760,
            height: 366,
            title: '안과 바깥을 가르는 선이 듣는 사람에 따라 옮겨간다',
            desc: '같은 상사가 왼쪽 자리에서는 선 바깥에 놓이고 오른쪽 자리에서는 선 안에 놓인다. 선의 자리를 정하는 것은 듣는 사람이다.',
            body: J(
                txt(40, 30, '선을 어디에 긋는지는 듣는 사람이 정한다. 나와 같은 쪽에 남는 사람이 안이다', { cls: 'ink2', size: 'sm' }),

                txt(40, 62, '자리 1 — 듣는 사람이 같은 직장 사람', { cls: 'ink2', size: 'sm' }),
                box(40, 76, 130, 40, '나', { stroke: GREEN }),
                box(40, 124, 130, 40, '안', { stroke: GREEN, dash: '5 4', size: 'sm' }),
                divider(190, 70, 210, ORANGE),
                box(210, 76, 130, 40, '상사', { stroke: BLUE }),
                box(210, 124, 130, 40, '듣는 사람', { stroke: BLUE }),
                box(210, 172, 130, 34, '바깥', { stroke: BLUE, dash: '5 4', size: 'sm' }),
                txt(40, 196, '겸양어', { cls: 'ink2', size: 'sm' }),
                txt(275, 226, '존경어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

                divider(388, 62, 250, GRID),

                txt(416, 62, '자리 2 — 듣는 사람이 다른 회사 사람', { cls: 'ink2', size: 'sm' }),
                box(416, 76, 130, 40, '나', { stroke: GREEN }),
                box(416, 124, 130, 40, '상사', { stroke: GREEN }),
                box(416, 172, 130, 34, '안', { stroke: GREEN, dash: '5 4', size: 'sm' }),
                divider(566, 70, 210, ORANGE),
                box(586, 76, 134, 40, '듣는 사람', { stroke: BLUE }),
                box(586, 124, 134, 40, '바깥', { stroke: BLUE, dash: '5 4', size: 'sm' }),
                txt(416, 226, '겸양어', { cls: 'ink2', size: 'sm' }),
                txt(653, 196, '존경어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),

                txt(40, 274, '상사는 자리 1 에서 선 바깥에 있고 자리 2 에서 선 안에 있다', {}),
                txt(40, 298, '그래서 자리 1 에서는 상사의 행동에 존경어가 걸리고 자리 2 에서는 걸리지 않는다', {}),
                txt(40, 324, '안쪽 사람의 행동에는 겸양어가 걸린다. 자리 2 에서 상사가 그 안에 들어온다', {}),
                txt(40, 350, '굵은 점선이 선이고 그 자리가 옮겨간 것이 이 그림의 전부다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-three-kinds',
        svg: svg({
            width: 760,
            height: 318,
            title: '세 갈래가 각각 무엇을 향하는가',
            desc: '정중어는 듣는 사람을 향하고, 존경어는 상대의 행동을 올리고, 겸양어는 자기 행동을 내린다.',
            body: J(
                txt(40, 30, '세 갈래는 걸리는 자리가 다르다. 정중어만 화제의 인물과 무관하다', { cls: 'ink2', size: 'sm' }),

                box(40, 52, 120, 44, '정중어', { stroke: GREEN }),
                box(196, 52, 130, 44, '나의 말투', { stroke: GREY, size: 'sm' }),
                px(332, 74, 424, 74, { cls: 's3', marker: 'ar3', width: 2 }),
                box(430, 52, 150, 44, '듣는 사람', { stroke: GREEN }),
                txt(600, 68, 'です・ます', { cls: 'ink2', size: 'sm' }),
                txt(600, 86, '화제와 무관하다', { cls: 'ink2', size: 'sm' }),

                box(40, 118, 120, 44, '존경어', { stroke: BLUE }),
                box(196, 118, 130, 44, ['상대의 행동'], { stroke: GREY, size: 'sm' }),
                px(400, 152, 400, 116, { cls: 's1', marker: 'ar1', width: 2 }),
                txt(414, 138, '올린다', { cls: 'ink2', size: 'sm' }),
                txt(600, 134, 'いらっしゃる', { cls: 'ink2', size: 'sm' }),
                txt(600, 152, 'おかきになる', { cls: 'ink2', size: 'sm' }),

                box(40, 184, 120, 44, '겸양어', { stroke: ORANGE }),
                box(196, 184, 130, 44, ['나의 행동'], { stroke: GREY, size: 'sm' }),
                px(400, 190, 400, 226, { cls: 's2', marker: 'ar2', width: 2 }),
                txt(414, 212, '내린다', { cls: 'ink2', size: 'sm' }),
                txt(600, 200, 'うかがう', { cls: 'ink2', size: 'sm' }),
                txt(600, 218, 'おもちする', { cls: 'ink2', size: 'sm' }),

                txt(40, 264, '아래 둘은 행동에 걸리므로 갈래를 가리려면 행동의 주인을 먼저 찾아야 한다', {}),
                txt(40, 288, '내 행동을 내리는 것과 상대를 올리는 것은 결과가 같다. 그래서 둘이 한 자리에 함께 온다', {}),
                txt(40, 312, '오른쪽 끝은 그 갈래에 드는 낱말의 보기다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-whose-action',
        svg: svg({
            width: 760,
            height: 330,
            title: '경어의 갈래를 가리는 물음의 순서',
            desc: '높임의 꼴을 찾은 다음 그 꼴이 걸린 행동의 주인을 묻고, 주인이 누구인지에 따라 세 갈래로 갈린다.',
            body: J(
                box(40, 30, 250, 44, '문장에서 높임의 꼴을 찾는다', { stroke: GREY, size: 'sm' }),
                px(165, 78, 165, 104, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(40, 108, 250, 60, ['그 꼴이 문장 끝의 말투인가', '행동에 걸린 것인가'], { stroke: GREY, size: 'sm', gap: 18 }),

                px(294, 138, 350, 138, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(322, 130, '말투', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(356, 116, 150, 44, '정중어', { stroke: GREEN }),
                txt(520, 142, 'です・ます', { cls: 'ink2', size: 'sm' }),

                px(165, 172, 165, 200, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(176, 190, '행동', { cls: 'ink2', size: 'sm' }),
                box(40, 204, 250, 44, '그 행동의 주인은 누구인가', { stroke: GREY, size: 'sm' }),

                px(294, 214, 350, 200, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(356, 178, 150, 44, '존경어', { stroke: BLUE }),
                txt(520, 204, '주인이 높이는 쪽', { cls: 'ink2', size: 'sm' }),

                px(294, 238, 350, 252, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(356, 230, 150, 44, '겸양어', { stroke: ORANGE }),
                txt(520, 256, '주인이 나이거나 안쪽', { cls: 'ink2', size: 'sm' }),

                txt(40, 296, '갈래는 형태에서 바로 나오지 않는다. 행동의 주인을 찾는 물음이 앞에 온다', {}),
                txt(40, 320, '두 갈래를 섞는 오류는 거의 다 이 물음을 건너뛴 데서 나온다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-same-form-three-ways',
        svg: svg({
            width: 760,
            height: 318,
            title: '한 꼴이 여러 갈래로 읽히는 것',
            desc: '수동과 같은 꼴을 존경어로 쓰면 한 형태가 여러 갈래로 읽히고, 형태만으로는 갈리지 않는다.',
            body: J(
                txt(40, 30, '수동과 같은 꼴을 존경어로 쓰면 형태가 겹친다', { cls: 'ink2', size: 'sm' }),

                box(40, 52, 160, 44, 'かかれる', { stroke: GREY }),
                px(206, 68, 262, 58, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(206, 80, 262, 100, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(268, 40, 110, 36, '수동', { stroke: ORANGE, size: 'sm' }),
                box(268, 82, 110, 36, '존경', { stroke: BLUE, size: 'sm' }),
                txt(396, 74, '두 갈래', { cls: 'ink2', size: 'sm' }),

                box(40, 138, 160, 44, 'たべられる', { stroke: GREY }),
                px(206, 150, 262, 136, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(206, 160, 262, 178, { cls: 'ax', marker: 'ark', width: 1.6 }),
                px(206, 170, 262, 220, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(268, 118, 110, 36, '수동', { stroke: ORANGE, size: 'sm' }),
                box(268, 160, 110, 36, '가능', { stroke: GREEN, size: 'sm' }),
                box(268, 202, 110, 36, '존경', { stroke: BLUE, size: 'sm' }),
                txt(396, 160, '세 갈래', { cls: 'ink2', size: 'sm' }),

                link(470, 44, 470, 244, { stroke: GRID, dash: '4 4' }),
                txt(492, 60, '무엇이 가르는가', { cls: 'ink2', size: 'sm' }),
                txt(492, 84, '주어가 누구인가', {}),
                txt(492, 108, '행동을 하는 쪽이 표시되어 있는가', {}),
                txt(492, 132, '앞뒤 문장', {}),
                txt(492, 168, '갈래를 뚜렷이 하려면', { cls: 'ink2', size: 'sm' }),
                txt(492, 192, '특별한 낱말 — めしあがる', {}),
                txt(492, 216, '틀 — おかきになる', {}),

                txt(40, 272, '형태가 겹치는 것은 결함이 아니라 이 꼴의 성질이다. 갈래는 문장 밖에서 정해진다', {}),
                txt(40, 296, '오른쪽 아래 두 줄이 겹침을 피하는 방법이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-give-receive-levels',
        svg: svg({
            width: 760,
            height: 336,
            title: '주고받기 동사와 그 경어형',
            desc: '주고받기 동사의 방향은 그대로 있고 경어형은 그 위에 누구를 높이는지만 얹는다.',
            body: J(
                txt(40, 30, '방향은 그대로다. 경어형이 더하는 것은 누구를 높이는지뿐이다', { cls: 'ink2', size: 'sm' }),
                txt(40, 58, '보통 꼴', { cls: 'ink2', size: 'sm' }),
                txt(200, 58, '방향', { cls: 'ink2', size: 'sm' }),
                txt(440, 58, '경어형', { cls: 'ink2', size: 'sm' }),
                txt(600, 58, '올라가는 쪽', { cls: 'ink2', size: 'sm' }),

                box(40, 70, 130, 40, 'あげる', { stroke: GREY }),
                box(196, 70, 76, 40, '안', { stroke: GREEN, size: 'sm' }),
                px(280, 90, 348, 90, { cls: 's1', marker: 'ar1', width: 2 }),
                box(354, 70, 76, 40, '바깥', { stroke: BLUE, size: 'sm' }),
                box(440, 70, 140, 40, 'さしあげる', { stroke: ORANGE }),
                txt(600, 94, '받는 쪽', {}),

                box(40, 130, 130, 40, 'くれる', { stroke: GREY }),
                box(196, 130, 76, 40, '안', { stroke: GREEN, size: 'sm' }),
                px(348, 150, 280, 150, { cls: 's1', marker: 'ar1', width: 2 }),
                box(354, 130, 76, 40, '바깥', { stroke: BLUE, size: 'sm' }),
                box(440, 130, 140, 40, 'くださる', { stroke: BLUE }),
                txt(600, 154, '주는 쪽', {}),

                box(40, 190, 130, 40, 'もらう', { stroke: GREY }),
                box(196, 190, 76, 40, '안', { stroke: GREEN, size: 'sm' }),
                px(348, 210, 280, 210, { cls: 's1', marker: 'ar1', width: 2 }),
                box(354, 190, 76, 40, '바깥', { stroke: BLUE, size: 'sm' }),
                box(440, 190, 140, 40, 'いただく', { stroke: ORANGE }),
                txt(600, 214, '주는 쪽', {}),

                txt(40, 268, '주는 쪽이 올라가면 존경어가 되고 받는 쪽이 나이면 겸양어가 된다', {}),
                txt(40, 292, '그래서 어느 갈래가 붙을지는 방향이 이미 정해 놓는다. 고를 것이 없다', {}),
                txt(40, 318, '가운데 화살표가 15장이 세운 방향이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-o-go-prefix',
        svg: svg({
            width: 760,
            height: 350,
            title: '두 접두사를 가리는 기준과 접두사가 하는 두 가지 일',
            desc: '어느 접두사가 붙는지는 낱말의 읽는 법이 대체로 정하고, 그 접두사가 무엇을 하는지는 그 낱말이 누구의 것인지가 정한다.',
            body: J(
                txt(40, 30, '물음이 둘이다. 어느 것이 붙는가, 그리고 그것이 무엇을 하는가', { cls: 'ink2', size: 'sm' }),

                txt(40, 60, '물음 1 — 어느 것이 붙는가', { cls: 'ink2', size: 'sm' }),
                box(40, 74, 130, 40, '훈독 낱말', { stroke: GREEN, size: 'sm' }),
                px(176, 94, 216, 94, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(222, 74, 66, 40, 'お', { stroke: GREEN }),
                txt(300, 98, 'おなまえ・おてがみ', {}),
                box(40, 124, 130, 40, '음독 낱말', { stroke: BLUE, size: 'sm' }),
                px(176, 144, 216, 144, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(222, 124, 66, 40, 'ご', { stroke: BLUE }),
                txt(300, 148, 'ごじゅうしょ・ごいけん', {}),
                box(40, 174, 248, 36, '예외 — 음독인데 お 가 붙는다', { stroke: ORANGE, dash: '5 4', size: 'sm' }),
                txt(300, 196, 'おでんわ・おじかん・おさけ', {}),

                link(40, 228, 720, 228, { stroke: GRID, dash: '4 4' }),

                txt(40, 252, '물음 2 — 무엇을 하는가. 그 낱말이 누구의 것인지를 물으면 갈린다', { cls: 'ink2', size: 'sm' }),
                box(40, 264, 300, 34, '상대의 것이다 — 높인다', { stroke: BLUE, size: 'sm' }),
                txt(352, 286, 'おなまえ・ごいけん', {}),
                box(40, 304, 300, 34, '누구의 것도 아니다 — 곱게 만든다', { stroke: ORANGE, size: 'sm' }),
                txt(352, 326, 'おさけ・おちゃ', {}),
            ),
        }),
    },
    {
        name: 'jp-e-over-honorific',
        svg: svg({
            width: 760,
            height: 318,
            title: '같은 갈래를 몇 겹 쌓았는지 세는 법',
            desc: '특별한 낱말과 틀과 수동과 같은 꼴을 겹쳐 쌓으면 같은 갈래가 세 번 붙는다. 규범과 실사용을 나눠 적는 자리다.',
            body: J(
                txt(40, 30, '존경어를 만드는 방법 셋을 한 낱말에 겹쳐 쌓았다', { cls: 'ink2', size: 'sm' }),

                box(40, 52, 240, 38, 'めしあがる', { stroke: BLUE }),
                txt(300, 76, '1겹 — 특별한 낱말', { cls: 'ink2', size: 'sm' }),
                px(160, 92, 160, 108, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(40, 112, 240, 38, 'おめしあがりになる', { stroke: BLUE }),
                txt(300, 136, '2겹 — 틀을 더 씌웠다', { cls: 'ink2', size: 'sm' }),
                px(160, 152, 160, 168, { cls: 'ax', marker: 'ark', width: 1.6 }),
                box(40, 172, 240, 38, 'おめしあがりになられる', { stroke: ORANGE }),
                txt(300, 196, '3겹 — 수동과 같은 꼴까지', { cls: 'ink2', size: 'sm' }),

                box(40, 232, 330, 66, ['규범이 적는 것', '같은 갈래를 겹쳐 쌓는 것을 인정하지 않는다'],
                    { stroke: GREEN, size: 'sm', gap: 19 }),
                box(390, 232, 330, 66, ['관찰이 적는 것', '손님을 상대하는 자리에서 실제로 쓰인다'],
                    { stroke: ORANGE, size: 'sm', gap: 19 }),
                txt(40, 314, '아래 두 칸은 서로를 지우지 않는다. 둘을 한 칸에 적으면 어느 하나가 지워진다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-e-three-vs-five',
        svg: svg({
            width: 760,
            height: 342,
            title: '세 갈래와 다섯 갈래가 갈리는 자리',
            desc: '다섯으로 나눈 쪽은 겸양어를 둘로 가르고, 높임이 아닌 접두사를 따로 세워 이름을 준다.',
            body: J(
                txt(40, 30, '이 문서가 쓰는 세 갈래와 2007년 답신의 다섯 갈래를 나란히 놓았다', { cls: 'ink2', size: 'sm' }),
                txt(40, 58, '세 갈래', { cls: 'ink2', size: 'sm' }),
                txt(430, 58, '다섯 갈래', { cls: 'ink2', size: 'sm' }),

                box(40, 70, 170, 40, '정중어', { stroke: GREEN }),
                link(216, 90, 424, 90, { stroke: GREEN, sw: 1.4 }),
                box(430, 70, 150, 40, '정중어', { stroke: GREEN }),
                txt(600, 94, 'です・ます', { cls: 'ink2', size: 'sm' }),

                box(40, 130, 170, 40, '존경어', { stroke: BLUE }),
                link(216, 150, 424, 150, { stroke: BLUE, sw: 1.4 }),
                box(430, 130, 150, 40, '존경어', { stroke: BLUE }),
                txt(600, 154, 'いらっしゃる', { cls: 'ink2', size: 'sm' }),

                box(40, 190, 170, 40, '겸양어', { stroke: ORANGE }),
                link(216, 204, 424, 190, { stroke: ORANGE, sw: 1.4 }),
                link(216, 216, 424, 250, { stroke: ORANGE, sw: 1.4 }),
                box(430, 190, 150, 40, '겸양어 I', { stroke: ORANGE }),
                txt(600, 214, 'もうしあげる', { cls: 'ink2', size: 'sm' }),
                box(430, 250, 150, 40, '겸양어 II', { stroke: ORANGE }),
                txt(600, 274, 'もうす', { cls: 'ink2', size: 'sm' }),

                box(430, 300, 150, 34, '미화어', { stroke: GREY, dash: '5 4' }),
                txt(600, 322, 'おさけ', { cls: 'ink2', size: 'sm' }),
                txt(40, 300, '세 갈래에는 이 자리가 없다', { cls: 'ink2', size: 'sm' }),
                txt(40, 322, '높임이 아니라고 보아 따로 세운 것이다', { cls: 'ink2', size: 'sm' }),

                txt(40, 264, '갈리는 자리는 두 곳뿐이다', {}),
            ),
        }),
    },
];
