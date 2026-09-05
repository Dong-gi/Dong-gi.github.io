/**
 * 18장(글말과 입말) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재는 문말의 꼴과 문체의 대응이다. 문말에 오는 것은 거의 다 가나로
 * 적히므로 한자를 뺀다고 잃는 것이 없다. 예문에 든 낱말(태풍·규슈·학생 같은 것)은
 * 본문에서 한자로 나가지만 그림에서는 가나로만 적는다. 그림이 보여야 하는 것은
 * 낱말의 뜻이 아니라 문장 끝이 어떻게 갈리는가이기 때문이다.
 *
 * 한글은 그림에 써도 된다(원장 §5.1 — 가나·로마자·숫자·한글). 한국어 등급과
 * 일본어 문체를 잇는 그림(jp-r-korean-mismatch)이 그것을 쓴다.
 *
 * 확인하지 못한 것을 그림으로 단정하지 않는다. 1인칭 대명사 그림
 * (jp-r-person-two-axes)의 세로축은 이 문서가 확인하지 못한 축이라서
 * 점선으로 긋고 그 사실을 그림 안에 적어 둔다.
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
        name: 'jp-r-two-styles-endings',
        svg: svg({
            width: 760,
            height: 300,
            title: '두 문체가 갈리는 자리',
            desc: '같은 문장의 앞부분은 그대로이고 마지막 덩어리만 바뀐다. 문체는 문장 끝에서만 갈린다.',
            body: J(
                txt(40, 30, '왼쪽 칸은 두 줄이 같다. 갈리는 것은 오른쪽 칸 하나뿐이다', { cls: 'ink2', size: 'sm' }),
                txt(60, 60, '문장의 앞부분', { cls: 'ink2', size: 'sm' }),
                txt(470, 60, '문장 끝', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(660, 60, '문체', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 70, 340, 40, 'ほんを', { stroke: GRID }),
                box(392, 70, 156, 40, 'よむ', { stroke: BLUE }),
                txt(660, 94, '보통체', { anchor: 'middle', cls: 'f1' }),
                box(40, 120, 340, 40, 'ほんを', { stroke: GRID }),
                box(392, 120, 156, 40, 'よみます', { stroke: ORANGE }),
                txt(660, 144, '정중체', { anchor: 'middle', cls: 'f2' }),
                box(40, 186, 340, 40, 'たなかさんは がくせい', { stroke: GRID }),
                box(392, 186, 156, 40, 'だ', { stroke: BLUE }),
                txt(660, 210, '보통체', { anchor: 'middle', cls: 'f1' }),
                box(40, 236, 340, 40, 'たなかさんは がくせい', { stroke: GRID }),
                box(392, 236, 156, 40, 'です', { stroke: ORANGE }),
                txt(660, 260, '정중체', { anchor: 'middle', cls: 'f2' }),
                link(40, 288, 380, 288, { stroke: GREY }),
                link(40, 282, 40, 288, { stroke: GREY }),
                link(380, 282, 380, 288, { stroke: GREY }),
                txt(392, 292, '이만큼은 문체를 고르는 일과 상관이 없다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-style-keigo-grid',
        svg: svg({
            width: 760,
            height: 300,
            title: '문체 축과 경어 축이 만드는 네 칸',
            desc: '가로가 문체, 세로가 존경어를 쓰는가다. 네 칸이 모두 채워지므로 두 축은 따로 움직인다.',
            body: J(
                txt(40, 30, '가로와 세로가 서로 다른 축이다. 네 칸이 모두 실제로 쓰이는 꼴이다', { cls: 'ink2', size: 'sm' }),
                txt(300, 68, '보통체', { anchor: 'middle', cls: 'f1' }),
                txt(560, 68, '정중체', { anchor: 'middle', cls: 'f2' }),
                txt(40, 112, '높이지 않는다', { cls: 'ink2', size: 'sm' }),
                box(180, 82, 240, 52, 'いく', { stroke: GRID }),
                box(440, 82, 240, 52, 'いきます', { stroke: GRID }),
                txt(40, 182, '존경어를 쓴다', { cls: 'f3', size: 'sm' }),
                box(180, 152, 240, 52, 'いらっしゃる', { stroke: GREEN }),
                box(440, 152, 240, 52, 'いらっしゃいます', { stroke: GREEN }),
                link(180, 220, 680, 220, { stroke: GRID }),
                txt(180, 246, '왼쪽 아래 칸이 비어 있으리라고 생각하기 쉽지만 비어 있지 않다', { cls: 'ink2', size: 'sm' }),
                txt(180, 268, '문체는 문장 끝의 층에서 갈리고 존경어는 뜻을 지는 부분에서 갈린다', { cls: 'ink2', size: 'sm' }),
                txt(180, 290, '층이 다르므로 어느 조합도 막히지 않는다', { cls: 'f3', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-korean-mismatch',
        svg: svg({
            width: 760,
            height: 330,
            title: '한국어 등급과 일본어 문체를 잇는 선',
            desc: '선이 하나씩 이어지지 않는다. 여럿이 하나로 모이는 자리와 하나가 둘로 갈라지는 자리가 함께 있다.',
            body: J(
                txt(40, 30, '선이 하나씩 이어지지 않는다는 것이 이 그림에서 읽어낼 것이다', { cls: 'ink2', size: 'sm' }),
                txt(150, 62, '한국어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(610, 62, '일본어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 76, 220, 42, '합쇼체', { stroke: ORANGE }),
                box(40, 128, 220, 42, '해요체', { stroke: ORANGE }),
                box(40, 190, 220, 42, '해체', { stroke: BLUE }),
                box(40, 242, 220, 42, '해라체', { stroke: BLUE }),
                box(500, 102, 180, 42, 'です · ます', { stroke: ORANGE }),
                txt(692, 127, '정중체', { cls: 'f2', size: 'sm' }),
                box(500, 190, 180, 42, 'だ', { stroke: BLUE }),
                txt(692, 215, '보통체', { cls: 'f1', size: 'sm' }),
                box(500, 250, 180, 42, 'である', { stroke: GREEN }),
                txt(692, 275, '글말', { cls: 'f3', size: 'sm' }),
                px(266, 97, 494, 120, { cls: 's2', marker: 'ar2' }),
                px(266, 149, 494, 130, { cls: 's2', marker: 'ar2' }),
                px(266, 211, 494, 209, { cls: 's1', marker: 'ar1' }),
                px(266, 258, 494, 218, { cls: 's1', marker: 'ar1' }),
                px(266, 272, 494, 271, { cls: 's3', marker: 'ar3' }),
                txt(40, 312, '위의 둘은 하나로 모이고, 맨 아래 하나는 둘로 갈라진다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-da-dearu',
        svg: svg({
            width: 760,
            height: 300,
            title: '두 보통체가 갈리는 문형과 갈리지 않는 문형',
            desc: '명사문과 な형용사문에서만 두 꼴이 다르고, い형용사문과 동사문에서는 같은 꼴이다.',
            body: J(
                txt(40, 30, '아래 두 줄은 두 칸이 같다. 그래서 문체를 알아보려면 앞의 두 줄을 봐야 한다', { cls: 'ink2', size: 'sm' }),
                txt(60, 66, '문형', { cls: 'ink2', size: 'sm' }),
                txt(360, 66, 'だ 쪽', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(600, 66, 'である 쪽', { anchor: 'middle', cls: 'f3', size: 'sm' }),
                txt(40, 100, '명사문', { cls: 'f3', size: 'sm' }),
                txt(40, 118, '갈린다', { cls: 'f3', size: 'sm' }),
                box(240, 76, 230, 40, 'がくせいだ', { stroke: BLUE }),
                box(500, 76, 230, 40, 'がくせいである', { stroke: GREEN }),
                txt(40, 152, 'な형용사문', { cls: 'f3', size: 'sm' }),
                txt(40, 170, '갈린다', { cls: 'f3', size: 'sm' }),
                box(240, 128, 230, 40, 'しずかだ', { stroke: BLUE }),
                box(500, 128, 230, 40, 'しずかである', { stroke: GREEN }),
                txt(40, 210, 'い형용사문', { cls: 'ink2', size: 'sm' }),
                box(240, 186, 230, 40, 'たかい', { stroke: GRID }),
                box(500, 186, 230, 40, 'たかい', { stroke: GRID }),
                txt(40, 262, '동사문', { cls: 'ink2', size: 'sm' }),
                box(240, 238, 230, 40, 'よむ', { stroke: GRID }),
                box(500, 238, 230, 40, 'よむ', { stroke: GRID }),
                txt(40, 296, '회색으로 그린 두 줄은 두 쪽이 글자까지 같은 자리다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-headline-steps',
        svg: svg({
            width: 760,
            height: 320,
            title: '기사 본문에서 표제로 가는 세 단계',
            desc: '정중함을 떼고, 조사를 빼고, 문장 끝을 명사로 바꾼다. 매 단계에서 사라지는 것이 다르다.',
            body: J(
                txt(40, 30, '위에서 아래로 내려가면서 무엇이 사라지는지 오른쪽에 적었다', { cls: 'ink2', size: 'sm' }),
                box(40, 56, 440, 44, 'たいふうが きゅうしゅうに じょうりくしました', { stroke: ORANGE }),
                txt(500, 74, '정중체로 쓴 문장', { cls: 'f2', size: 'sm' }),
                txt(500, 92, '기사 본문에는 이 꼴이 안 나온다', { cls: 'ink2', size: 'sm' }),
                px(120, 104, 120, 132, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(136, 124, '정중함을 뗀다', { cls: 'ink2', size: 'sm' }),
                box(40, 138, 440, 44, 'たいふうが きゅうしゅうに じょうりくした', { stroke: BLUE }),
                txt(500, 164, '기사 본문의 꼴', { cls: 'f1', size: 'sm' }),
                px(120, 186, 120, 214, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(136, 206, '조사를 빼고 문장 끝을 명사로 바꾼다', { cls: 'ink2', size: 'sm' }),
                box(40, 220, 440, 44, 'たいふう、きゅうしゅうに じょうりく', { stroke: GREEN }),
                txt(500, 238, '표제의 꼴', { cls: 'f3', size: 'sm' }),
                txt(500, 256, '읽는 쪽이 빠진 것을 채운다', { cls: 'ink2', size: 'sm' }),
                txt(40, 292, '빠진 조사는 아무거나가 아니다. 사라진 것은 앞의 낱말에 붙어 있던 が 하나다', { cls: 'ink2', size: 'sm' }),
                txt(40, 312, 'に 는 남았다. 빼면 어느 곳인지가 정해지지 않기 때문이다', { cls: 'f3', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-contractions',
        svg: svg({
            width: 760,
            height: 330,
            title: '입말에서 줄어드는 꼴과 줄어든 만큼의 박',
            desc: '왼쪽이 원래 꼴, 가운데가 줄어든 꼴이다. 오른쪽 숫자는 박이 몇에서 몇으로 줄었는지다.',
            body: J(
                txt(40, 30, '줄어든 꼴은 원래 꼴보다 박이 하나에서 둘 적다', { cls: 'ink2', size: 'sm' }),
                txt(60, 62, '원래 꼴', { cls: 'ink2', size: 'sm' }),
                txt(430, 62, '줄어든 꼴', { cls: 'ink2', size: 'sm' }),
                txt(700, 62, '박', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 72, 300, 38, 'よんでいる', { stroke: GRID }),
                px(348, 91, 414, 91, { cls: 's1', marker: 'ar1', width: 1.6 }),
                box(424, 72, 220, 38, 'よんでる', { stroke: BLUE }),
                txt(700, 96, '5 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 122, 300, 38, 'よんでおく', { stroke: GRID }),
                px(348, 141, 414, 141, { cls: 's1', marker: 'ar1', width: 1.6 }),
                box(424, 122, 220, 38, 'よんどく', { stroke: BLUE }),
                txt(700, 146, '5 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 172, 300, 38, 'たべてしまう', { stroke: GRID }),
                px(348, 191, 414, 191, { cls: 's2', marker: 'ar2', width: 1.6 }),
                box(424, 172, 220, 38, 'たべちゃう', { stroke: ORANGE }),
                txt(700, 196, '6 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 222, 300, 38, 'よんでしまう', { stroke: GRID }),
                px(348, 241, 414, 241, { cls: 's2', marker: 'ar2', width: 1.6 }),
                box(424, 222, 220, 38, 'よんじゃう', { stroke: ORANGE }),
                txt(700, 246, '6 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(40, 272, 300, 38, 'よまなければ', { stroke: GRID }),
                px(348, 291, 414, 291, { cls: 's3', marker: 'ar3', width: 1.6 }),
                box(424, 272, 220, 38, 'よまなきゃ', { stroke: GREEN }),
                txt(700, 296, '6 → 4', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-rareru-split',
        svg: svg({
            width: 760,
            height: 320,
            title: '한 꼴이 세 갈래를 겸하는 것과 그 가운데 하나만 가리키는 꼴',
            desc: '위의 꼴은 세 갈래로 읽히고 아래의 꼴은 한 갈래만 가리킨다. 규범이 인정하지 않는 쪽에서 세 갈래가 갈라진다.',
            body: J(
                txt(40, 30, '위는 화살표가 셋, 아래는 하나다. 이것이 이 그림에서 읽어낼 전부다', { cls: 'ink2', size: 'sm' }),
                box(40, 76, 230, 44, 'たべられる', { stroke: BLUE }),
                txt(40, 68, '규범이 싣는 꼴', { cls: 'f1', size: 'sm' }),
                px(278, 98, 406, 61, { cls: 's1', marker: 'ar1' }),
                px(278, 98, 406, 105, { cls: 's1', marker: 'ar1' }),
                px(278, 98, 406, 149, { cls: 's1', marker: 'ar1' }),
                box(416, 40, 200, 38, '수동', { stroke: GRID }),
                box(416, 86, 200, 38, '가능', { stroke: GRID }),
                box(416, 132, 200, 38, '존경', { stroke: GRID }),
                txt(630, 64, '14장', { cls: 'ink2', size: 'sm' }),
                txt(630, 110, '11장', { cls: 'ink2', size: 'sm' }),
                txt(630, 156, '16장', { cls: 'ink2', size: 'sm' }),
                box(40, 216, 230, 44, 'たべれる', { stroke: ORANGE, dash: '5 4' }),
                txt(40, 208, '규범이 싣지 않는 꼴', { cls: 'f2', size: 'sm' }),
                px(278, 238, 406, 238, { cls: 's2', marker: 'ar2' }),
                box(416, 218, 200, 40, '가능', { stroke: ORANGE }),
                txt(630, 242, '이것만', { cls: 'f2', size: 'sm' }),
                txt(40, 290, '아래 꼴을 쓰는 사람의 말에서는 가능과 나머지 둘이 형태로 갈린다', { cls: 'f2', size: 'sm' }),
                txt(40, 312, '규범이 싣지 않는 꼴이 체계적으로는 구별을 늘린다는 뜻이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-r-person-two-axes',
        svg: svg({
            width: 760,
            height: 320,
            title: '1인칭을 가리키는 말이 놓이는 두 축',
            desc: '가로축은 글과 공식 자리에서 확인되는 축이고, 세로축은 이 문서가 확인하지 못한 축이라 점선으로 그렸다.',
            body: J(
                txt(40, 30, '가로축은 확인되고 세로축은 확인되지 않았다. 두 축을 겹쳐 놓은 것이 문제다', { cls: 'ink2', size: 'sm' }),
                px(70, 150, 720, 150, { cls: 'ax', marker: 'ark' }),
                txt(70, 176, '격식이 없는 자리', { cls: 'ink2', size: 'sm' }),
                txt(710, 176, '격식 있는 자리', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                txt(400, 196, '이 축은 글과 공식 자리에서 확인된다', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                link(160, 134, 160, 150, { stroke: GREY }),
                txt(160, 126, 'おれ', { anchor: 'middle' }),
                link(320, 134, 320, 150, { stroke: GREY }),
                txt(320, 126, 'ぼく', { anchor: 'middle' }),
                link(480, 134, 480, 150, { stroke: GREY }),
                txt(480, 126, 'わたし', { anchor: 'middle' }),
                link(640, 134, 640, 150, { stroke: GREY }),
                txt(640, 126, 'わたくし', { anchor: 'middle' }),
                txt(160, 106, '글에 안 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(320, 106, '글에 안 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(480, 106, '글에 나온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(640, 106, '가장 격식', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(250, 302, 250, 168, { cls: 's2', marker: 'ar2', dash: '5 4' }),
                txt(266, 250, '교재가 성별 축이라고 적어 온 축', { cls: 'f2', size: 'sm' }),
                txt(266, 272, '이 문서는 실제 사용을 확인하지 못했다', { cls: 'f2', size: 'sm' }),
                txt(266, 294, '그래서 점선으로 두고 눈금을 적지 않는다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
