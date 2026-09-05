/**
 * 7장(한자) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장은 한자를 다루는 장이지만 그림에는 한자가 한 자도 없다. 한자는 본문의
 * +jp 로만 나가고, 그림은 관계·절차·비율만 그린다. 부수 구성과 자형 차이도
 * 한글·가나·빈 사각형으로 대신한다(원장 §5.1).
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
    return `<path fill="none" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.2}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-j-one-char-many-readings',
        svg: svg({
            width: 760,
            height: 344,
            title: '한 글자에서 읽는 법이 갈라지는 구조',
            desc: '가운데 한자 한 자에서 왼쪽으로 한국 한자음 하나가, 오른쪽으로 음독 셋과 훈독 셋이 뻗는다.',
            body: J(
                box(45, 150, 185, 56, ['한국 한자음은 하나', '행'], { stroke: GREEN }),
                box(320, 150, 150, 56, '한자 한 자', { sw: 1.8 }),
                txt(560, 30, '음독 — 중국음에서 온 것', { cls: 'ink2', size: 'sm' }),
                box(560, 40, 165, 32, 'こう — 한음', { stroke: BLUE }),
                box(560, 80, 165, 32, 'ぎょう — 오음', { stroke: BLUE }),
                box(560, 120, 165, 32, 'あん — 당음', { stroke: BLUE }),
                txt(560, 192, '훈독 — 일본어 고유어', { cls: 'ink2', size: 'sm' }),
                box(560, 202, 165, 30, 'いく', { stroke: ORANGE }),
                box(560, 238, 165, 30, 'ゆく', { stroke: ORANGE }),
                box(560, 274, 165, 30, 'おこなう', { stroke: ORANGE }),
                px(316, 178, 236, 178, { cls: 's3', marker: 'ar3' }),
                px(474, 172, 554, 56, { cls: 's1', marker: 'ar1' }),
                px(474, 176, 554, 96, { cls: 's1', marker: 'ar1' }),
                px(474, 180, 554, 136, { cls: 's1', marker: 'ar1' }),
                px(474, 184, 554, 217, { cls: 's2', marker: 'ar2' }),
                px(474, 188, 554, 253, { cls: 's2', marker: 'ar2' }),
                px(474, 192, 554, 289, { cls: 's2', marker: 'ar2' }),
                txt(45, 240, '한국어 쪽에서는', { cls: 'ink2', size: 'sm' }),
                txt(45, 258, '고를 것이 없다', { cls: 'ink2', size: 'sm' }),
                txt(45, 330, '어느 것을 쓰는지는 낱말마다 따로 정해져 있다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-onyomi-layers',
        svg: svg({
            width: 760,
            height: 306,
            title: '음독이 여럿이 된 경로',
            desc: '중국음이 시기를 달리해 세 번 들어오고, 앞의 음이 지워지지 않아 셋이 함께 남는다.',
            body: J(
                box(40, 32, 680, 40, '중국의 한자음 — 들여온 시기마다 달랐다', { dash: '5 4' }),
                box(90, 148, 150, 58, ['오음', 'ぎょう'], { stroke: BLUE }),
                box(305, 148, 150, 58, ['한음', 'こう'], { stroke: BLUE }),
                box(520, 148, 150, 58, ['당음', 'あん'], { stroke: BLUE }),
                px(165, 76, 165, 144, { cls: 'ax', marker: 'ark' }),
                px(380, 76, 380, 144, { cls: 'ax', marker: 'ark' }),
                px(595, 76, 595, 144, { cls: 'ax', marker: 'ark' }),
                px(165, 208, 165, 230, { cls: 's3', marker: 'ar3' }),
                px(380, 208, 380, 230, { cls: 's3', marker: 'ar3' }),
                px(595, 208, 595, 230, { cls: 's3', marker: 'ar3' }),
                box(40, 234, 680, 40, '일본어에 남은 음 — 셋이 함께 남았다', { stroke: GREEN }),
                txt(40, 298, '왼쪽이 이른 시기다. 새 음이 들어와도 이미 자리 잡은 낱말의 음은 바뀌지 않았다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-on-kun-decision',
        svg: svg({
            width: 760,
            height: 362,
            title: '음독인지 훈독인지 가리는 절차',
            desc: '한자가 둘 이상 붙었는지, 오쿠리가나가 붙었는지를 차례로 묻고 답을 얻는다.',
            body: J(
                box(50, 26, 300, 44, '한자가 둘 이상 붙어 있는가', { stroke: BLUE }),
                box(430, 26, 280, 44, '대개 음독', { stroke: GREEN }),
                px(354, 48, 426, 48, { cls: 'ax', marker: 'ark' }),
                txt(390, 40, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(200, 74, 200, 106, { cls: 'ax', marker: 'ark' }),
                txt(208, 96, '아니오', { cls: 'ink2', size: 'sm' }),
                box(50, 108, 300, 44, '오쿠리가나가 붙어 있는가', { stroke: BLUE }),
                box(430, 108, 280, 44, '훈독', { stroke: GREEN }),
                px(354, 130, 426, 130, { cls: 'ax', marker: 'ark' }),
                txt(390, 122, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(200, 156, 200, 188, { cls: 'ax', marker: 'ark' }),
                txt(208, 178, '아니오', { cls: 'ink2', size: 'sm' }),
                box(50, 190, 300, 44, '한자 하나가 낱말 전체인가', { stroke: BLUE }),
                box(430, 184, 280, 56, ['대개 훈독', '음독으로 읽는 낱말도 있다'], { stroke: GREEN }),
                px(354, 212, 426, 212, { cls: 'ax', marker: 'ark' }),
                txt(390, 204, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(50, 278, 660, 60,
                    ['예외 — 앞과 뒤가 음독과 훈독으로 섞이는 낱말이 있다',
                        '규칙으로 가려지지 않으므로 낱말째 익힌다'],
                    { stroke: ORANGE, dash: '5 4' }),
            ),
        }),
    },
    {
        name: 'jp-j-korean-coda-map',
        svg: svg({
            width: 760,
            height: 344,
            title: '한글 받침과 음독 끝의 대응',
            desc: '받침 여섯 갈래가 음독의 끝 다섯 갈래로 이어진다. 두 받침이 한 곳으로 모인다.',
            body: J(
                txt(150, 32, '한국 한자음의 받침', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(575, 32, '음독의 끝', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(90, 46, 120, 32, '받침 없음'),
                box(90, 88, 120, 32, 'ㄱ'),
                box(90, 130, 120, 32, 'ㄴ'),
                box(90, 172, 120, 32, 'ㅁ'),
                box(90, 214, 120, 32, 'ㄹ'),
                box(90, 256, 120, 32, 'ㅇ'),
                box(470, 46, 210, 32, '모음으로 끝난다', { stroke: BLUE }),
                box(470, 88, 210, 32, 'く · き', { stroke: BLUE }),
                box(470, 151, 210, 32, 'ん', { stroke: ORANGE }),
                box(470, 214, 210, 32, 'つ · ち', { stroke: BLUE }),
                box(470, 256, 210, 32, 'う · い — 장음이 된다', { stroke: GREEN }),
                link(214, 62, 466, 62),
                link(214, 104, 466, 104),
                link(214, 146, 466, 167, { stroke: ORANGE, sw: 1.6 }),
                link(214, 188, 466, 167, { stroke: ORANGE, sw: 1.6 }),
                link(214, 230, 466, 230),
                link(214, 272, 466, 272, { stroke: GREEN, sw: 1.6 }),
                txt(230, 306, 'ㄴ 과 ㅁ 은 한곳으로 모이고, ㅇ 은 거기로 가지 않는다', { cls: 'ink2', size: 'sm' }),
                txt(230, 326, 'ㅂ 은 갈래가 갈려서 이 그림에 넣지 않았다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-onyomi-mora',
        svg: svg({
            width: 760,
            height: 268,
            title: '한국 한자음 한 음절과 음독의 박 수',
            desc: '받침이 없으면 음독이 한 박, 받침이 있으면 두 박이 된다. 요음은 글자 둘에 한 박이다.',
            body: J(
                txt(24, 84, '한국', { cls: 'ink2', size: 'sm' }),
                txt(24, 100, '한자음', { cls: 'ink2', size: 'sm' }),
                txt(24, 156, '음독의', { cls: 'ink2', size: 'sm' }),
                txt(24, 172, '박', { cls: 'ink2', size: 'sm' }),
                // 받침 없는 것
                box(155, 62, 64, 40, '기', { stroke: GREEN }),
                px(187, 106, 187, 130, { cls: 'ax', marker: 'ark' }),
                box(157, 134, 60, 40, 'き', { stroke: BLUE }),
                txt(187, 206, '1박', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                // 받침 ㄱ
                box(383, 62, 64, 40, '학', { stroke: GREEN }),
                px(415, 106, 415, 130, { cls: 'ax', marker: 'ark' }),
                box(355, 134, 56, 40, 'が', { stroke: BLUE }),
                box(419, 134, 56, 40, 'く', { stroke: BLUE }),
                txt(415, 206, '2박', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                // 받침 ㅇ
                box(611, 62, 64, 40, '강', { stroke: GREEN }),
                px(643, 106, 643, 130, { cls: 'ax', marker: 'ark' }),
                box(575, 134, 66, 40, 'きょ', { stroke: BLUE }),
                box(647, 134, 56, 40, 'う', { stroke: BLUE }),
                txt(643, 206, '2박 — 가나는 셋', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(150, 244, '요음은 가나 두 자를 써도 한 박이다. 받침이 있으면 음독은 두 박이 된다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-phonetic-part',
        svg: svg({
            width: 760,
            height: 306,
            title: '글자를 뜻 부분과 소리 부분으로 쪼개 보기',
            desc: '한 글자가 뜻 부분과 소리 부분으로 나뉘고, 소리 부분이 같은 글자들이 같은 음독으로 모인다.',
            body: J(
                txt(40, 34, '글자 하나를 쪼갠다', { cls: 'ink2', size: 'sm' }),
                box(60, 56, 190, 104, '', { sw: 1.8 }),
                link(155, 56, 155, 160, { stroke: GRID, sw: 1.4 }),
                txt(107, 112, '뜻 부분', { anchor: 'middle' }),
                txt(203, 112, '소리 부분', { anchor: 'middle', cls: 'f1' }),
                px(107, 164, 92, 194, { cls: 'ax', marker: 'ark' }),
                px(203, 164, 224, 194, { cls: 's1', marker: 'ar1' }),
                box(20, 198, 140, 54, ['무엇에 관한', '글자인지 짐작한다']),
                box(176, 198, 130, 54, ['음독을', '짐작한다'], { stroke: BLUE }),
                txt(400, 34, '소리 부분이 같은 글자 셋', { cls: 'ink2', size: 'sm' }),
                box(375, 56, 90, 66, '', { sw: 1.6 }),
                box(505, 56, 90, 66, '', { sw: 1.6 }),
                box(635, 56, 90, 66, '', { sw: 1.6 }),
                link(420, 56, 420, 122, { stroke: GRID, sw: 1.4 }),
                link(550, 56, 550, 122, { stroke: GRID, sw: 1.4 }),
                link(680, 56, 680, 122, { stroke: GRID, sw: 1.4 }),
                txt(397, 94, '뜻1', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(527, 94, '뜻2', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(657, 94, '뜻3', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(443, 94, '소리', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(573, 94, '소리', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(703, 94, '소리', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                px(420, 126, 520, 194, { cls: 's1', marker: 'ar1' }),
                px(550, 126, 550, 194, { cls: 's1', marker: 'ar1' }),
                px(680, 126, 580, 194, { cls: 's1', marker: 'ar1' }),
                box(470, 198, 160, 54, ['같은 음독으로 모인다', 'はく'], { stroke: BLUE }),
                txt(340, 288, '소리 부분이 같아도 어긋나는 글자가 있으므로 사전에서 확인한다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-glyph-difference-scope',
        svg: svg({
            width: 760,
            height: 300,
            title: '일본 자형과 한국 자형이 다른 한자의 비율',
            desc: '전체로는 3퍼센트지만 흔한 한자만 골라 보면 비율이 훨씬 높다.',
            body: J(
                txt(40, 30, '같은 폰트의 일본 면과 한국 면이 같은 코드포인트에 붙인 모양을 견줬다',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 62, '두 면이 함께 덮는 한자 20,976자', { size: 'sm' }),
                `<rect x="40" y="72" width="680" height="26" rx="3" fill="none" stroke="${GRID}" stroke-width="1.4"/>`,
                `<rect x="40" y="72" width="20" height="26" rx="3" fill="var(--s2)"/>`,
                txt(70, 90, '이 가운데 630자, 곧 3.0퍼센트가 모양이 다르다', { size: 'sm' }),
                txt(40, 142, '흔한 한자 57자를 표본으로 골라 보면', { size: 'sm' }),
                ...Array.from({ length: 57 }, (_, i) => {
                    const x = 40 + (i % 19) * 24;
                    const y = 154 + Math.floor(i / 19) * 24;
                    const on = i < 10;
                    return `<rect x="${x}" y="${y}" width="19" height="19" rx="2" `
                        + `fill="${on ? 'var(--s2)' : 'none'}" stroke="${on ? 'var(--s2)' : GRID}" stroke-width="1.2"/>`;
                }),
                txt(520, 180, '10자가 달랐다', { size: 'sm' }),
                txt(520, 200, '약 18퍼센트다', { cls: 'ink2', size: 'sm' }),
                txt(40, 258, '비율이 낮다고 넘길 수 없는 이유가 이 두 줄의 차이다', {}),
                txt(40, 282, '자형이 갈리는 글자가 자주 쓰는 글자에 몰려 있다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-j-lookup-flow',
        svg: svg({
            width: 760,
            height: 362,
            title: '모르는 한자를 찾는 절차',
            desc: '글이 디지털인지, 부수를 짚을 수 있는지에 따라 길이 갈리고, 마지막에 낱말을 다시 찾는다.',
            body: J(
                box(250, 22, 260, 38, '모르는 한자를 만났다'),
                px(380, 64, 380, 86, { cls: 'ax', marker: 'ark' }),
                box(250, 88, 260, 38, '글이 디지털인가', { stroke: BLUE }),
                px(514, 107, 588, 107, { cls: 'ax', marker: 'ark' }),
                txt(551, 99, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(592, 83, 155, 48, ['복사해서 사전에', '붙여 넣는다'], { stroke: GREEN }),
                px(380, 130, 380, 152, { cls: 'ax', marker: 'ark' }),
                txt(388, 148, '아니오', { cls: 'ink2', size: 'sm' }),
                box(230, 154, 300, 38, '부수를 짚을 수 있는가', { stroke: BLUE }),
                px(534, 173, 588, 173, { cls: 'ax', marker: 'ark' }),
                txt(561, 165, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(592, 149, 155, 48, ['부수 색인에서', '획수 순으로 찾는다'], { stroke: GREEN }),
                px(380, 196, 380, 218, { cls: 'ax', marker: 'ark' }),
                txt(388, 214, '아니오', { cls: 'ink2', size: 'sm' }),
                box(180, 220, 400, 48, ['총획수 색인으로 찾거나', '기기의 손글씨 입력을 쓴다'], { stroke: GREEN }),
                px(380, 272, 380, 294, { cls: 'ax', marker: 'ark' }),
                box(120, 296, 520, 54,
                    ['여기까지가 글자 찾기다. 얻는 것은 음독과 훈독의 목록이다',
                        '낱말이 그중 어느 것을 쓰는지는 낱말로 다시 찾는다'],
                    { stroke: ORANGE }),
            ),
        }),
    },
];
