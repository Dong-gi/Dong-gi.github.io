/**
 * 11장(동사의 활용) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장은 한자를 넣지 않기가 쉬운 장이다. 활용은 꼬리에서 일어나는 일이고
 * 꼬리는 전부 가나이기 때문이다. 낱말을 가려야 하는 자리(같은 소리로 읽히는
 * 두 동사)에는 한자 대신 한글 뜻을 적었다.
 *
 * 학교문법 용어(五段·上一段 …)는 한자어라 그림에 넣을 수 없다. 그래서 그림은
 * 그 용어가 왜 그런 이름인지를 ‘다섯 단’·‘한 단’ 이라는 한글 말로만 그린다.
 * 용어 자체의 대응표는 본문에 있다.
 */
import { svg, px, txt, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

const GREY = 'var(--ink2)';
const GRID = 'var(--grid)';
const BLUE = 'var(--s1)';
const ORANGE = 'var(--s2)';
const GREEN = 'var(--s3)';

/** 사각 상자 하나. lines 는 문자열 또는 문자열 배열. */
function box(x, y, w, h, lines, o = {}) {
    const ls = (Array.isArray(lines) ? lines : [lines]).filter(t => t !== '');
    const gap = o.gap || 18;
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

/**
 * 큰 글자. lib.mjs 가 <style> 에 text{font-size:13px} 를 박으므로
 * 속성으로 크기를 주면 무시된다. 인라인 style 로 준다.
 */
function big(x, y, s, o = {}) {
    return `<text class="${o.cls || 'ink'}" x="${x}" y="${y}"`
        + ` text-anchor="${o.anchor || 'start'}" style="font-size:${o.size || 19}px">${esc(s)}</text>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-v-stem-and-tail',
        svg: svg({
            width: 760,
            height: 286,
            title: '낱말에서 바뀌지 않는 부분과 바뀌는 부분',
            desc: '한국어와 일본어를 나란히 놓았다. 두 언어 모두 앞부분이 고정되고 꼬리만 갈린다.',
            body: J(
                txt(46, 32, '한국어 — 가다', { cls: 'ink2', size: 'sm' }),
                box(46, 44, 62, 148, '가', { stroke: GREEN }),
                box(120, 44, 168, 34, '다'),
                box(120, 82, 168, 34, '고'),
                box(120, 120, 168, 34, '서'),
                box(120, 158, 168, 34, '지 않는다'),
                link(370, 40, 370, 200, { stroke: GRID, dash: '4 4' }),
                txt(452, 32, '일본어 — かく', { cls: 'ink2', size: 'sm' }),
                box(452, 44, 62, 148, 'か', { stroke: GREEN }),
                box(526, 44, 168, 34, 'く'),
                box(526, 82, 168, 34, 'きます'),
                box(526, 120, 168, 34, 'いて'),
                box(526, 158, 168, 34, 'かない'),
                txt(46, 226, '초록 상자는 어느 꼴에서도 그대로다. 바뀌는 것은 오른쪽 꼬리뿐이다', {}),
                txt(46, 252, '개념은 두 언어에서 같다. 새로 배울 것은 꼬리의 목록과 만드는 법이다',
                    { cls: 'ink2', size: 'sm' }),
                txt(46, 274, '가운데 두 줄이 서로 대응한다 — 문장을 잇는 자리다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-v-five-steps-vs-one',
        svg: svg({
            width: 760,
            height: 340,
            title: '갈래를 나누는 근거 — 꼬리가 몇 개의 단을 쓰는가',
            desc: '왼쪽은 꼬리의 첫 글자가 다섯 단을 다 옮겨 다니고, 오른쪽은 한 단에 머문다.',
            body: J(
                txt(40, 30, '1류 — 꼬리의 첫 글자가 다섯 단을 옮겨 다닌다', { cls: 'f1' }),
                ...['あ', 'い', 'う', 'え', 'お'].map((d, i) => txt(44, 74 + i * 42, d + '단', { cls: 'ink2', size: 'sm' })),
                ...[['か', 'かかない'], ['き', 'かきます'], ['く', 'かく'], ['け', 'かけば · かける'], ['こ', 'かこう']]
                    .map(([k, form], i) => box(86, 56 + i * 42, 52, 30, k, { stroke: BLUE })
                        + txt(154, 76 + i * 42, form, {})),
                link(370, 24, 370, 300, { stroke: GRID, dash: '4 4' }),
                txt(400, 30, '2류 — 어간이 한 단에 머문다', { cls: 'f2' }),
                txt(404, 74, 'い단에 머무는 것', { cls: 'ink2', size: 'sm' }),
                box(404, 86, 62, 30, 'み', { stroke: ORANGE }),
                txt(478, 106, 'みない · みます · みる', {}),
                txt(478, 128, 'みれば · みよう', {}),
                txt(404, 176, 'え단에 머무는 것', { cls: 'ink2', size: 'sm' }),
                box(404, 188, 62, 30, 'たべ', { stroke: ORANGE }),
                txt(478, 208, 'たべない · たべます · たべる', {}),
                txt(478, 230, 'たべれば · たべよう', {}),
                txt(40, 320, '학교문법이 이 둘을 ‘다섯 단’ 과 ‘한 단’ 으로 이름 붙인 까닭이 이 그림이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-v-class-decision',
        svg: svg({
            width: 760,
            height: 356,
            title: '동사가 몇 류인지 가리는 절차',
            desc: '세 번 물으면 갈린다. 마지막 물음에서만 답이 하나로 정해지지 않는다.',
            body: J(
                box(250, 18, 260, 34, '사전형을 본다'),
                px(380, 56, 380, 76, { cls: 'ax', marker: 'ark' }),
                box(150, 78, 340, 38, 'する · くる 이거나 명사 + する 인가', { stroke: BLUE }),
                px(494, 97, 552, 97, { cls: 'ax', marker: 'ark' }),
                txt(523, 89, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(556, 74, 180, 46, ['3류', '이 둘뿐이다'], { stroke: GREEN }),
                px(380, 120, 380, 140, { cls: 'ax', marker: 'ark' }),
                txt(388, 137, '아니오', { cls: 'ink2', size: 'sm' }),
                box(150, 142, 340, 38, '끝이 る 인가', { stroke: BLUE }),
                px(494, 161, 552, 161, { cls: 'ax', marker: 'ark' }),
                txt(523, 153, '아니오', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(556, 138, 180, 46, ['1류', '갈릴 여지가 없다'], { stroke: GREEN }),
                px(380, 184, 380, 204, { cls: 'ax', marker: 'ark' }),
                txt(388, 201, '예', { cls: 'ink2', size: 'sm' }),
                box(150, 206, 340, 38, 'る 앞이 い단이나 え단인가', { stroke: BLUE }),
                px(494, 225, 552, 225, { cls: 'ax', marker: 'ark' }),
                txt(523, 217, '아니오', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(556, 202, 180, 46, ['1류', '예외가 없다'], { stroke: GREEN }),
                px(380, 248, 380, 268, { cls: 'ax', marker: 'ark' }),
                txt(388, 265, '예', { cls: 'ink2', size: 'sm' }),
                box(90, 270, 580, 52,
                    ['2류인 것이 많다. 그러나 1류인 낱말이 섞여 있다',
                        '이 자리에서만 사전이나 ます형으로 확인해야 한다'],
                    { stroke: ORANGE }),
                txt(90, 344, '섞여 있는 1류 — かえる · はいる · きる · しる · いる 처럼 낱말째 익힌다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-v-ru-ending-split',
        svg: svg({
            width: 760,
            height: 320,
            title: 'る 로 끝나는 동사가 두 갈래로 갈리는 자리',
            desc: 'る 앞 글자의 단에 따라 갈리고, 한쪽에만 예외가 있다. 아래는 사전형이 같은 두 낱말이다.',
            body: J(
                box(40, 62, 150, 44, 'る 로 끝난다', { sw: 1.8 }),
                px(194, 84, 250, 56, { cls: 'ax', marker: 'ark' }),
                px(194, 84, 250, 128, { cls: 'ax', marker: 'ark' }),
                box(254, 34, 176, 44, ['る 앞이', 'あ · う · お단']),
                box(254, 106, 176, 44, ['る 앞이', 'い · え단']),
                px(434, 56, 486, 56, { cls: 's3', marker: 'ar3' }),
                box(490, 32, 240, 48, ['1류 — 예외가 없다', 'わかる · つくる · のる'], { stroke: GREEN }),
                px(434, 128, 486, 110, { cls: 's3', marker: 'ar3' }),
                px(434, 128, 486, 158, { cls: 's2', marker: 'ar2' }),
                box(490, 88, 240, 44, '2류가 많다 — みる · たべる', { stroke: GREEN }),
                box(490, 138, 240, 44, '1류인 낱말도 있다 — かえる', { stroke: ORANGE }),
                link(40, 208, 720, 208, { stroke: GRID }),
                txt(40, 234, '사전형이 같으면 갈래도 같은가 — 아니다', {}),
                box(40, 248, 330, 46, ['きる — 자르다 → きって', '1류'], { stroke: BLUE, gap: 17 }),
                box(390, 248, 330, 46, ['きる — 입다 → きて', '2류'], { stroke: ORANGE, gap: 17 }),
                txt(40, 314, '사전형의 모양만으로는 이 둘이 갈리지 않는다. て형이 갈라 준다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-v-te-hub',
        svg: svg({
            width: 760,
            height: 320,
            title: 'て형이 중심이 되는 짜임',
            desc: 'て형 뒤의 빈 자리에 무엇이 붙느냐로 여러 표현이 만들어진다. 이 장은 자리를 만들고 뜻은 뒤 장들이 채운다.',
            body: J(
                box(40, 118, 150, 50, '사전형'),
                px(194, 143, 268, 143, { cls: 's1', marker: 'ar1' }),
                txt(231, 133, '음편 규칙', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(231, 168, '이 장의 일', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(272, 108, 190, 70, '', { sw: 2, stroke: BLUE }),
                big(300, 152, 'て형 +', { size: 22 }),
                big(420, 152, '?', { size: 22, cls: 'ink2', anchor: 'middle' }),
                px(466, 132, 528, 60, { cls: 'ax', marker: 'ark' }),
                px(466, 138, 528, 108, { cls: 'ax', marker: 'ark' }),
                px(466, 148, 528, 156, { cls: 'ax', marker: 'ark' }),
                px(466, 154, 528, 204, { cls: 'ax', marker: 'ark' }),
                px(466, 160, 528, 252, { cls: 'ax', marker: 'ark' }),
                box(532, 36, 200, 40, '문장을 잇는다'),
                box(532, 86, 200, 40, '때와 모습 — 13장'),
                box(532, 136, 200, 40, '주고받기 — 15장'),
                box(532, 186, 200, 40, '허가 · 의뢰'),
                box(532, 236, 200, 40, '14 · 16 · 17장의 여러 표현'),
                txt(40, 226, '뒤의 다섯 장은', { cls: 'ink2', size: 'sm' }),
                txt(40, 244, '대부분 이 빈 자리를', { cls: 'ink2', size: 'sm' }),
                txt(40, 262, '채우는 이야기다', { cls: 'ink2', size: 'sm' }),
                txt(40, 306, '빈 자리를 정확히 만드는 것이 이 장의 목표다. 무엇이 붙어 무슨 뜻이 되는지는 뒤 장들의 것이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-v-onbin-groups',
        svg: svg({
            width: 760,
            height: 340,
            title: '1류 동사의 て형에서 소리가 바뀌는 규칙',
            desc: '사전형의 끝 아홉 갈래가 て형의 끝 다섯 갈래로 모인다. 이름이 셋 붙어 있다.',
            body: J(
                txt(120, 32, '1류 사전형의 끝', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(400, 32, 'て형의 끝', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(640, 32, '이름', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(60, 46, 120, 34, 'く', { stroke: BLUE }),
                box(60, 86, 120, 34, 'ぐ', { stroke: BLUE }),
                box(60, 132, 120, 34, 'う · つ · る', { stroke: ORANGE }),
                box(60, 178, 120, 34, 'ぬ · ぶ · む', { stroke: GREEN }),
                box(60, 224, 120, 34, 'す'),
                px(184, 63, 336, 63, { cls: 's1', marker: 'ar1' }),
                px(184, 103, 336, 103, { cls: 's1', marker: 'ar1' }),
                px(184, 149, 336, 149, { cls: 's2', marker: 'ar2' }),
                px(184, 195, 336, 195, { cls: 's3', marker: 'ar3' }),
                px(184, 241, 336, 241, { cls: 'ax', marker: 'ark' }),
                box(340, 46, 120, 34, 'いて', { stroke: BLUE }),
                box(340, 86, 120, 34, 'いで', { stroke: BLUE }),
                box(340, 132, 120, 34, 'って', { stroke: ORANGE }),
                box(340, 178, 120, 34, 'んで', { stroke: GREEN }),
                box(340, 224, 120, 34, 'して'),
                box(500, 46, 220, 74, ['い음편', 'い 로 바뀐다'], { stroke: BLUE, dash: '5 4' }),
                box(500, 132, 220, 34, '촉음편 — 촉음이 생긴다', { stroke: ORANGE, dash: '5 4', size: 'sm' }),
                box(500, 178, 220, 34, 'ん음편 — ん 박이 생긴다', { stroke: GREEN, dash: '5 4', size: 'sm' }),
                box(500, 224, 220, 34, '바뀌지 않는다', { dash: '5 4', size: 'sm' }),
                box(60, 278, 660, 44,
                    ['いく 하나만 예외다 — 규칙대로라면 いいて 인데 실제는 いって 다'],
                    { stroke: ORANGE }),
            ),
        }),
    },
    {
        name: 'jp-v-onbin-scope',
        svg: svg({
            width: 760,
            height: 288,
            title: '음편이 나타나는 자리',
            desc: '한 동사의 여러 꼴 가운데 소리가 바뀌는 것은 두 꼴뿐이고 나머지는 꼬리만 갈아 붙는다.',
            body: J(
                box(40, 106, 130, 54, 'かく', { sw: 1.8 }),
                px(174, 118, 328, 78, { cls: 'ax', marker: 'ark' }),
                px(174, 148, 328, 200, { cls: 's2', marker: 'ar2' }),
                box(332, 40, 388, 78,
                    ['かかない · かきます · かけば', 'かける · かこう · かけ'], { gap: 24 }),
                txt(332, 138, '꼬리만 갈아 붙는다. 앞의 か 는 그대로다', { cls: 'ink2', size: 'sm' }),
                box(332, 170, 388, 58, 'かいて · かいた', { stroke: ORANGE, sw: 1.8 }),
                txt(332, 248, '앞 소리까지 바뀌었다 — 이것이 음편이다', { cls: 'f2', size: 'sm' }),
                txt(40, 278, '음편은 て형과 た형에서만 일어난다. 다른 꼴에서는 소리가 바뀌지 않는다', {}),
            ),
        }),
    },
    {
        name: 'jp-v-suru-noun',
        svg: svg({
            width: 760,
            height: 300,
            title: '명사에 する 를 붙여 동사를 만드는 짜임',
            desc: '명사 부분은 그대로 있고 する 쪽만 활용한다. 아는 명사의 수가 그대로 동사의 수가 된다.',
            body: J(
                txt(40, 32, '한자어 명사', { cls: 'ink2', size: 'sm' }),
                box(40, 44, 150, 40, '공부', { stroke: GREEN }),
                box(40, 92, 150, 40, '전화', { stroke: GREEN }),
                box(40, 140, 150, 40, '연습', { stroke: GREEN }),
                box(40, 188, 150, 40, '설명', { stroke: GREEN }),
                txt(215, 32, '붙이면', { cls: 'ink2', size: 'sm' }),
                ...[64, 112, 160, 208].map(y => big(212, y + 4, '+ する', { size: 17, cls: 'f1' })),
                px(300, 64, 356, 64, { cls: 'ax', marker: 'ark' }),
                px(300, 112, 356, 112, { cls: 'ax', marker: 'ark' }),
                px(300, 160, 356, 160, { cls: 'ax', marker: 'ark' }),
                px(300, 208, 356, 208, { cls: 'ax', marker: 'ark' }),
                box(360, 44, 360, 184,
                    ['3류 동사가 된다', '명사 부분은 어느 꼴에서도 그대로다',
                        '활용은 する 쪽에서만 일어난다', 'しない · します · して · した'],
                    { stroke: BLUE, gap: 34 }),
                txt(40, 262, '아는 한자어 명사의 수가 그대로 동사의 수가 된다', {}),
                txt(40, 288, '한국어에서 ‘-하다’ 가 붙는 자리와 대체로 겹친다. 다만 뜻이 갈린 낱말이 있다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
