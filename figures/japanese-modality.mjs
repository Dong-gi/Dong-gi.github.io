/**
 * 17장(문말의 태도) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재는 문말에 붙는 짧은 말들이고 그것들은 모두 가나로 적힌다.
 * 예문에 든 낱말(전철·비·학생 같은 것)은 본문에서 한자로 나가지만 그림에서는
 * 가나로만 적는다. 그림이 보여야 하는 것은 낱말의 뜻이 아니라 문말의 자리이므로
 * 가나로 적어도 잃는 것이 없다.
 *
 * 이 장의 주요 그림은 둘이다 — 층의 순서(jp-d-layer-map-complete)와
 * 확신의 정도 축(jp-d-certainty-axis). 뒤의 것은 재어 얻은 값이 아니라
 * 교재의 관행을 늘어놓은 것이므로 그림 안에 그 사실을 적어 둔다.
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
    return `<path fill="none" stroke="${o.stroke || GRID}" stroke-width="${o.sw || 1.2}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-d-noda-explains',
        svg: svg({
            width: 760,
            height: 300,
            title: '설명을 대는 꼴이 무엇을 받는가',
            desc: '앞의 말이나 상황이 있을 때만 설명을 대는 꼴이 쓰인다. 받을 것이 없으면 설명할 것 없는 설명이 된다.',
            body: J(
                txt(40, 30, '왼쪽에 받을 것이 있을 때 오른쪽 꼴이 쓰인다', { cls: 'ink2', size: 'sm' }),
                txt(40, 56, '받을 것이 있다', { cls: 'f3', size: 'sm' }),
                box(40, 64, 250, 56, ['어째서 늦었습니까', '— 앞의 말'], { stroke: GREEN }),
                px(298, 92, 378, 92, { cls: 's3', marker: 'ar3' }),
                txt(338, 82, '설명한다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(388, 64, 330, 56, ['でんしゃがこなかったんです', '— 사정을 댄 말이 된다'], { stroke: GREEN }),
                txt(40, 176, '받을 것이 없다', { cls: 'f2', size: 'sm' }),
                box(40, 184, 250, 56, ['앞의 말도 상황도 없다'], { stroke: ORANGE, dash: '5 4' }),
                px(298, 212, 378, 212, { cls: 's2', marker: 'ar2', dash: '5 4' }),
                box(388, 184, 330, 56, ['わたしはがくせいなんです', '— 따지거나 변명하는 투가 된다'], { stroke: ORANGE }),
                txt(40, 274, '이 꼴이 하는 일은 문장을 정중하게 만드는 것이 아니라 앞을 설명하는 것이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-noda-outer-predicate',
        svg: svg({
            width: 760,
            height: 320,
            title: '설명을 대는 층이 새 술어가 되는 모양',
            desc: '안쪽 문장은 보통체로 끝난 꼴로 들어가고, 정중함은 그 층 뒤에서 다시 표시된다.',
            body: J(
                txt(40, 30, '안쪽 문장이 통째로 들어가고, 정중함은 그 뒤에서 다시 붙는다', { cls: 'ink2', size: 'sm' }),
                txt(60, 60, '안쪽 — 보통체로 끝난 꼴', { cls: 'ink2', size: 'sm' }),
                txt(408, 60, '태도', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(510, 60, '정중함', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(60, 70, 300, 40, 'よむ'),
                box(370, 70, 76, 40, 'の', { stroke: BLUE }),
                box(452, 70, 116, 40, 'です', { stroke: ORANGE }),
                txt(586, 94, '읽는 사정이다', { cls: 'ink2', size: 'sm' }),
                box(60, 130, 300, 40, 'よまない'),
                box(370, 130, 76, 40, 'の', { stroke: BLUE }),
                box(452, 130, 116, 40, 'です', { stroke: ORANGE }),
                txt(586, 154, '부정이 안쪽에 있다', { cls: 'ink2', size: 'sm' }),
                box(60, 190, 300, 40, 'よまなかった'),
                box(370, 190, 76, 40, 'の', { stroke: BLUE }),
                box(452, 190, 116, 40, 'です', { stroke: ORANGE }),
                txt(586, 214, '때도 안쪽에 있다', { cls: 'ink2', size: 'sm' }),
                link(60, 246, 360, 246, { stroke: GREY }),
                link(60, 240, 60, 246, { stroke: GREY }),
                link(360, 240, 360, 246, { stroke: GREY }),
                txt(60, 268, '이 자리가 바뀌어도 오른쪽 두 칸은 그대로다', { cls: 'ink2', size: 'sm' }),
                txt(60, 292, '그래서 이 꼴을 쓰면 정중함이 문장 끝으로 한 번 더 옮겨 간다', { cls: 'f2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-grounds-split',
        svg: svg({
            width: 760,
            height: 340,
            title: '짐작 표현을 고를 때 먼저 보는 것',
            desc: '근거가 어디서 왔는지에 따라 쓰는 표현이 갈린다. 확신의 세기보다 근거의 종류가 먼저다.',
            body: J(
                txt(40, 30, '근거가 어디서 왔는가를 먼저 본다', { cls: 'ink2', size: 'sm' }),
                txt(60, 56, '근거', { cls: 'ink2', size: 'sm' }),
                txt(470, 56, '쓰는 표현', { cls: 'ink2', size: 'sm' }),
                box(40, 66, 250, 48, '아는 사실 · 약속 · 시간표', { stroke: BLUE }),
                px(296, 90, 456, 90, { cls: 's1', marker: 'ar1' }),
                box(466, 66, 254, 48, 'はずだ', { stroke: BLUE }),
                box(40, 128, 250, 48, '내 눈에 보이는 것', { stroke: GREEN }),
                px(296, 152, 456, 152, { cls: 's3', marker: 'ar3' }),
                box(466, 128, 254, 48, ['ようだ · 어간에 붙는 そうだ'], { stroke: GREEN }),
                box(40, 190, 250, 48, '남에게 들은 것', { stroke: ORANGE }),
                px(296, 214, 456, 214, { cls: 's2', marker: 'ar2' }),
                box(466, 190, 254, 48, ['사전형에 붙는 そうだ · らしい'], { stroke: ORANGE }),
                box(40, 252, 250, 48, '근거를 대지 않는다', { stroke: GRID }),
                px(296, 276, 456, 276, { cls: 'ax', marker: 'ark' }),
                box(466, 252, 254, 48, 'かもしれない · でしょう', { stroke: GRID }),
                txt(40, 328, '같은 확신이라도 근거가 다르면 다른 표현을 쓴다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-certainty-axis',
        svg: svg({
            width: 760,
            height: 280,
            title: '짐작 표현을 확신의 세기로 늘어놓은 것',
            desc: '왼쪽이 약하고 오른쪽이 강하다. 자리는 재어 얻은 값이 아니고, 가운데 셋은 교재마다 자리가 다르다.',
            body: J(
                txt(40, 30, '재어 얻은 값이 아니다. 교재가 대개 이 순서로 늘어놓는다', { cls: 'ink2', size: 'sm' }),
                px(70, 120, 715, 120, { cls: 'ax', marker: 'ark' }),
                txt(70, 146, '약함', { cls: 'ink2', size: 'sm' }),
                txt(700, 146, '강함', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                link(150, 104, 150, 120, { stroke: GREY }),
                txt(150, 96, 'かもしれない', { anchor: 'middle' }),
                txt(150, 76, '가능성만 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                link(330, 104, 330, 120, { stroke: GREY }),
                txt(330, 96, 'でしょう · だろう', { anchor: 'middle' }),
                txt(330, 76, '근거를 대지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                link(520, 104, 520, 120, { stroke: GREY }),
                txt(520, 96, 'はずだ', { anchor: 'middle' }),
                txt(520, 76, '아는 사실에서 따라 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                link(660, 104, 660, 120, { stroke: GREY }),
                txt(660, 96, 'にちがいない', { anchor: 'middle' }),
                txt(660, 76, '단정에 가깝다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(300, 176, 300, 44, ['ようだ · らしい · みたいだ'], { stroke: ORANGE, dash: '5 4' }),
                px(450, 176, 450, 132, { cls: 's2', marker: 'ar2', dash: '5 4' }),
                txt(620, 202, '이 셋의 자리는', { cls: 'f2', size: 'sm' }),
                txt(620, 220, '교재마다 다르다', { cls: 'f2', size: 'sm' }),
                txt(40, 258, '이 축만으로는 고르지 못한다. 근거의 종류를 함께 보아야 한다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-sou-two-attachments',
        svg: svg({
            width: 760,
            height: 330,
            title: '같은 문말 표현이 붙는 자리로 갈리는 모양',
            desc: '왼쪽 칸이 무엇인지에 따라 뜻이 달라진다. 사전형에 붙으면 들은 것이고 어간에 붙으면 보이는 것이다.',
            body: J(
                txt(40, 30, '오른쪽 칸은 같다. 갈리는 것은 왼쪽 칸이 무엇인가다', { cls: 'ink2', size: 'sm' }),
                txt(60, 58, '붙는 자리', { cls: 'ink2', size: 'sm' }),
                txt(300, 58, '결과', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(560, 58, '무슨 뜻인가', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 68, 130, 38, 'ふる', { stroke: ORANGE }),
                box(174, 68, 60, 38, 'そうだ', { stroke: GRID, size: 'sm' }),
                box(250, 68, 180, 38, 'ふるそうだ', { stroke: ORANGE }),
                txt(450, 92, '사전형 — 들은 것', { cls: 'ink2', size: 'sm' }),
                box(40, 116, 130, 38, 'ふり', { stroke: GREEN }),
                box(174, 116, 60, 38, 'そうだ', { stroke: GRID, size: 'sm' }),
                box(250, 116, 180, 38, 'ふりそうだ', { stroke: GREEN }),
                txt(450, 140, '어간 쪽 — 보이는 것', { cls: 'ink2', size: 'sm' }),
                box(40, 174, 130, 38, 'たかい', { stroke: ORANGE }),
                box(174, 174, 60, 38, 'そうだ', { stroke: GRID, size: 'sm' }),
                box(250, 174, 180, 38, 'たかいそうだ', { stroke: ORANGE }),
                txt(450, 198, '사전형 — 들은 것', { cls: 'ink2', size: 'sm' }),
                box(40, 222, 130, 38, 'たか', { stroke: GREEN }),
                box(174, 222, 60, 38, 'そうだ', { stroke: GRID, size: 'sm' }),
                box(250, 222, 180, 38, 'たかそうだ', { stroke: GREEN }),
                txt(450, 246, '어간 — 보이는 것', { cls: 'ink2', size: 'sm' }),
                txt(40, 288, '주황이 들은 것, 초록이 보이는 것이다. 한 글자 차이로 갈린다',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 312, '들은 것인지 보이는 것인지를 정하는 것은 뜻이 아니라 붙는 자리다', { cls: 'f2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-ne-yo-information',
        svg: svg({
            width: 760,
            height: 300,
            title: '문장 끝의 두 짧은 말이 갈리는 기준',
            desc: '정보가 나에게만 있으면 알려 주는 쪽을, 둘 다 가지고 있으면 함께 확인하는 쪽을 쓴다.',
            body: J(
                txt(40, 30, '정보가 누구에게 있는가로 갈린다', { cls: 'ink2', size: 'sm' }),
                box(40, 60, 330, 170, '', { stroke: GRID }),
                txt(205, 84, 'よ — 알려 준다', { anchor: 'middle', cls: 'f1' }),
                box(70, 104, 100, 44, '나', { stroke: BLUE }),
                box(240, 104, 100, 44, '상대', { stroke: GRID }),
                px(172, 126, 238, 126, { cls: 's1', marker: 'ar1' }),
                txt(205, 174, '정보가 내게만 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(205, 198, 'かぎがおちましたよ', { anchor: 'middle' }),
                box(390, 60, 330, 170, '', { stroke: GRID }),
                txt(555, 84, 'ね — 함께 확인한다', { anchor: 'middle', cls: 'f3' }),
                box(420, 104, 100, 44, '나', { stroke: GREEN }),
                box(590, 104, 100, 44, '상대', { stroke: GREEN }),
                px(522, 120, 588, 120, { cls: 's3', marker: 'ar3' }),
                px(588, 132, 522, 132, { cls: 's3', marker: 'ar3' }),
                txt(555, 174, '정보를 둘 다 가지고 있다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(555, 198, 'きょうはあついですね', { anchor: 'middle' }),
                txt(40, 262, '자리를 바꿔 쓰면 상대가 아는 것을 가르치는 말이 되거나', { cls: 'f2', size: 'sm' }),
                txt(40, 284, '상대에게 없는 동의를 요구하는 말이 된다', { cls: 'f2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-question-forms',
        svg: svg({
            width: 760,
            height: 310,
            title: '문체에 따라 묻는 꼴이 다르다',
            desc: '정중체는 문말에 짧은 말을 붙여 묻고, 보통체는 그것을 붙이지 않고 표기의 물음표가 그 자리를 맡는다.',
            body: J(
                txt(40, 30, '무엇이 물음을 표시하는지가 문체에 따라 다르다', { cls: 'ink2', size: 'sm' }),
                txt(40, 60, '정중체', { cls: 'ink2', size: 'sm' }),
                box(120, 66, 200, 40, 'いきます'),
                box(324, 66, 60, 40, 'か', { stroke: BLUE }),
                box(388, 66, 44, 40, '。', { stroke: GRID }),
                txt(450, 90, '짧은 말이 물음을 표시한다', { cls: 'ink2', size: 'sm' }),
                txt(40, 130, '보통체', { cls: 'ink2', size: 'sm' }),
                box(120, 136, 200, 40, 'いく'),
                box(324, 136, 60, 40, '', { stroke: GRID, dash: '5 4' }),
                box(388, 136, 44, 40, '？', { stroke: ORANGE }),
                txt(450, 160, '짧은 말이 없고 표기가 표시한다', { cls: 'ink2', size: 'sm' }),
                txt(40, 200, '명사문', { cls: 'ink2', size: 'sm' }),
                box(120, 206, 200, 40, 'がくせい'),
                box(324, 206, 60, 40, '', { stroke: GRID, dash: '5 4' }),
                box(388, 206, 44, 40, '？', { stroke: ORANGE }),
                txt(450, 230, 'だ 가 나타나지 않는다', { cls: 'ink2', size: 'sm' }),
                txt(40, 278, '이 문서는 소리를 줄 수 없다. 억양이 하는 일은 표기의 물음표로만 보인다',
                    { cls: 'f2', size: 'sm' }),
                txt(40, 300, '점선 칸은 아무것도 오지 않는 자리다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-d-layer-map-complete',
        svg: svg({
            width: 760,
            height: 350,
            title: '문장 끝의 층 지도가 완성된 모양',
            desc: '태도의 층이 붙지 않은 문장은 층이 한 줄로 늘어서고, 붙은 문장은 안쪽 한 줄을 통째로 받은 뒤 정중함을 다시 받는다.',
            body: J(
                txt(40, 30, '위는 태도의 층이 없는 문장, 아래는 있는 문장이다', { cls: 'ink2', size: 'sm' }),
                txt(40, 60, '태도의 층이 없다 — 층이 한 줄이다', { cls: 'ink2', size: 'sm' }),
                box(40, 70, 130, 40, 'よみ'),
                box(174, 70, 100, 40, 'ませ', { stroke: BLUE }),
                box(278, 70, 70, 40, 'ん', { stroke: ORANGE }),
                box(352, 70, 120, 40, 'でした', { stroke: GREEN }),
                box(476, 70, 70, 40, 'ね', { stroke: GRID }),
                txt(105, 128, '뜻', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(224, 128, '정중함', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(313, 128, '부정', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(412, 128, '때', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(511, 128, '종조사', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(40, 186, '태도의 층이 있다 — 안쪽 한 줄이 통째로 들어간다', { cls: 'ink2', size: 'sm' }),
                box(40, 196, 308, 40, 'よまなかった', { sw: 1.8 }),
                box(352, 196, 60, 40, 'の', { stroke: BLUE }),
                box(416, 196, 100, 40, 'です', { stroke: ORANGE }),
                box(520, 196, 60, 40, 'よ', { stroke: GRID }),
                box(584, 196, 60, 40, 'ね', { stroke: GRID }),
                txt(194, 254, '안쪽 — 보통체로 끝난 꼴', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(382, 254, '태도', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(466, 254, '정중함', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(582, 254, '종조사 둘', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(40, 300, '태도의 층이 붙으면 정중함이 그 뒤로 간다. 이 순서는 바꿀 수 없다',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 324, '종조사는 어느 쪽에서나 맨 바깥이고, 둘이 겹칠 때의 순서도 정해져 있다',
                    { cls: 'f2', size: 'sm' }),
            ),
        }),
    },
];
