/**
 * 14장(자동사·타동사와 태) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재는 대부분 화살표로 그려진다. 누가 하고 누가 받는지, 짝을 이루는
 * 두 동사의 꼬리가 어떻게 대응하는지, 피해의 수동에서 영향이 누구에게 가는지,
 * 층이 어느 순서로 겹쳐 붙는지가 모두 방향을 가진 관계다.
 *
 * 한자를 피하는 방법은 11장과 같다 — 동사를 가나로만 적는다. 자동사와 타동사의
 * 짝은 표기에서 같은 한자를 공유하는 것이 눈에 띄는 자리인데, 그 사실은 그림이
 * 아니라 본문의 표가 보인다.
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

/** 곡선 화살표. 자기 자신으로 돌아오는 화살표를 그릴 때 쓴다. */
function curveArrow(d, o = {}) {
    return `<path class="${o.cls || 's1'}" fill="none" stroke-width="${o.sw || 2.4}"`
        + ` stroke-linecap="round" marker-end="url(#${o.marker || 'ar1'})" d="${d}"/>`;
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
        name: 'jp-o-arrow-direction',
        svg: svg({
            width: 760,
            height: 310,
            title: '자동사와 타동사에서 화살표가 어디로 가는가',
            desc: '자동사는 변화가 그 낱말 안에서 끝나고, 타동사는 화살표가 다른 것에 가 닿는다. 닿는 곳이 を 로 표시된다.',
            body: J(
                txt(40, 32, '자동사 — 화살표가 밖으로 나가지 않는다', { cls: 'f1' }),
                curveArrow('M80 92 C80 48 170 48 170 88', { cls: 's1', marker: 'ar1' }),
                txt(125, 84, 'あく', { anchor: 'middle', cls: 'f1' }),
                box(60, 94, 130, 50, 'まど', { stroke: BLUE, sw: 1.8 }),
                big(60, 184, 'まどが あく', { size: 17 }),
                txt(60, 212, '변화를 겪는 것이 하나뿐이다', { cls: 'ink2', size: 'sm' }),
                link(370, 24, 370, 232, { stroke: GRID, dash: '4 4' }),
                txt(400, 32, '타동사 — 화살표가 다른 것에 가 닿는다', { cls: 'f2' }),
                box(400, 94, 120, 50, 'わたし', { stroke: ORANGE, sw: 1.8 }),
                px(524, 119, 594, 119, { cls: 's2', marker: 'ar2' }),
                txt(559, 108, 'あける', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                box(598, 94, 120, 50, 'まど', { stroke: ORANGE, sw: 1.8 }),
                big(400, 184, 'わたしが まどを あける', { size: 17 }),
                txt(400, 212, '화살표가 닿는 곳이 を 로 표시된다', { cls: 'ink2', size: 'sm' }),
                box(40, 240, 680, 56,
                    ['を 가 붙었다고 다 타동사인 것은 아니다',
                        'みちを あるく 의 あるく 는 자동사다 — 9장이 경로의 を 라 이름 붙인 자리다'],
                    { stroke: GREEN, gap: 20 }),
            ),
        }),
    },
    {
        name: 'jp-o-pair-shapes',
        svg: svg({
            width: 760,
            height: 366,
            title: '자동사와 타동사 짝의 꼬리가 대응하는 방식',
            desc: '왼쪽 꼬리와 오른쪽 꼬리 사이에 되풀이되는 대응이 있다. 다만 예측 규칙은 아니고 예외가 섞여 있다.',
            body: J(
                txt(128, 32, '자동사', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(378, 32, '타동사', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(500, 32, '꼬리의 대응', { cls: 'ink2', size: 'sm' }),
                ...[
                    ['はじまる', 'はじめる', '〜ある  ⇒  〜える'],
                    ['あく', 'あける', '〜く · 〜つ  ⇒  〜える'],
                    ['なおる', 'なおす', '〜る  ⇒  〜す'],
                    ['おちる', 'おとす', '〜ちる  ⇒  〜とす'],
                    ['われる', 'わる', '〜れる  ⇒  〜る'],
                ].map(([a, b, lab], i) => {
                    const y = 44 + i * 44;
                    return box(40, y, 176, 34, a, { stroke: BLUE })
                        + px(220, y + 17, 282, y + 17, { cls: 's3', marker: 'ar3' })
                        + box(290, y, 176, 34, b, { stroke: ORANGE })
                        + txt(492, y + 22, lab, {});
                }),
                link(40, 278, 720, 278, { stroke: GRID }),
                box(40, 292, 680, 62,
                    ['예외를 같은 자리에 적어 둔다',
                        'あずかる 는 〜ある 로 끝나는데 타동사다 · しぬ 와 たべる 는 짝이 아예 없다'],
                    { stroke: ORANGE, gap: 20 }),
            ),
        }),
    },
    {
        name: 'jp-o-ga-two-jobs',
        svg: svg({
            width: 760,
            height: 322,
            title: '같은 조사가 서로 다른 두 자리를 표시한다',
            desc: '자동사 문장에서 が 는 변화를 겪는 것을 표시하고, 9장이 이름 붙인 대상의 が 는 상태가 향하는 대상을 표시한다.',
            body: J(
                txt(40, 30, '자동사의 が — 변화를 겪는 것 자체', { cls: 'f1' }),
                box(40, 44, 170, 44, 'まどが', { stroke: BLUE }),
                px(214, 66, 268, 66, { cls: 'ax', marker: 'ark' }),
                box(272, 44, 150, 44, 'あく', { stroke: BLUE }),
                txt(444, 62, '사람이 들어갈 자리가 없다', {}),
                txt(444, 82, '넣으면 타동사 문장이 된다', { cls: 'ink2', size: 'sm' }),
                link(40, 108, 720, 108, { stroke: GRID }),
                txt(40, 136, '대상의 が — 상태가 향하는 대상. 9장이 이름 붙였다', { cls: 'f2' }),
                box(40, 150, 150, 44, 'わたしは', { stroke: ORANGE, dash: '5 4' }),
                box(200, 150, 180, 44, 'にほんごが', { stroke: ORANGE }),
                px(384, 172, 438, 172, { cls: 'ax', marker: 'ark' }),
                box(442, 150, 150, 44, 'わかる', { stroke: ORANGE }),
                txt(40, 218, '아는 것은 사람이고, が 가 표시한 것은 아는 대상이다', {}),
                box(40, 240, 680, 62,
                    ['가리는 시험 — は 자리에 사람을 넣어 보라',
                        '넣을 자리가 있으면 대상의 が 이고, 없으면 자동사의 が 다'],
                    { stroke: GREEN, gap: 20 }),
            ),
        }),
    },
    {
        name: 'jp-o-passive-flip',
        svg: svg({
            width: 760,
            height: 336,
            title: '수동에서 무엇이 뒤집히고 무엇이 그대로인가',
            desc: '화살표의 방향은 그대로다. 바뀌는 것은 어느 쪽을 주어로 세우는가와 조사다.',
            body: J(
                txt(40, 30, '능동', { cls: 'ink2', size: 'sm' }),
                box(40, 42, 180, 54, ['せんせいが', '주어'], { stroke: BLUE, sw: 1.8, gap: 19 }),
                px(224, 69, 304, 69, { cls: 's1', marker: 'ar1' }),
                txt(264, 58, 'ほめる', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                box(308, 42, 180, 54, 'わたしを', { stroke: GREY }),
                big(40, 128, 'せんせいが わたしを ほめる', { size: 17 }),
                link(40, 152, 720, 152, { stroke: GRID }),
                txt(40, 180, '수동', { cls: 'ink2', size: 'sm' }),
                box(40, 192, 180, 54, 'せんせいに', { stroke: GREY }),
                px(224, 219, 304, 219, { cls: 's1', marker: 'ar1' }),
                txt(264, 208, 'ほめる', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                box(308, 192, 180, 54, ['わたしが', '주어'], { stroke: ORANGE, sw: 1.8, gap: 19 }),
                big(40, 278, 'わたしが せんせいに ほめられる', { size: 17 }),
                txt(510, 218, '하는 쪽은 に 로 표시된다', { cls: 'f2' }),
                txt(40, 312, '두 줄에서 화살표는 같은 방향이다. 갈리는 것은 주어와 조사뿐이다', {}),
            ),
        }),
    },
    {
        name: 'jp-o-damage-passive',
        svg: svg({
            width: 760,
            height: 336,
            title: '피해의 수동에서 영향이 누구에게 가는가',
            desc: 'を 로 이어질 대상이 없는 사건에서도 수동이 만들어지고, 영향을 받은 사람이 주어가 된다.',
            body: J(
                txt(40, 30, '자동사의 수동 — 사건에 を 로 이어질 대상이 없다', { cls: 'f1' }),
                box(40, 44, 200, 46, 'あめが ふる', { stroke: BLUE }),
                px(244, 67, 316, 67, { cls: 's2', marker: 'ar2' }),
                txt(280, 56, '영향', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                box(320, 44, 160, 46, 'わたし', { stroke: ORANGE, sw: 1.8 }),
                txt(500, 62, '곤란해진 사람이', {}),
                txt(500, 82, '주어가 된다', {}),
                big(40, 122, 'あめに ふられた', { size: 17 }),
                link(40, 146, 720, 146, { stroke: GRID }),
                txt(40, 174, '타동사의 수동 — を 가 그대로 남는 자리', { cls: 'f1' }),
                box(40, 188, 170, 46, 'となりの ひと', { stroke: BLUE }),
                px(214, 211, 252, 211, { cls: 's1', marker: 'ar1' }),
                txt(233, 200, 'すう', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                box(256, 188, 130, 46, 'たばこ', { stroke: BLUE }),
                px(390, 211, 452, 211, { cls: 's2', marker: 'ar2' }),
                txt(421, 200, '영향', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                box(456, 188, 160, 46, 'わたし', { stroke: ORANGE, sw: 1.8 }),
                big(40, 266, 'たばこを すわれた', { size: 17 }),
                txt(40, 300, '주어는 곤란해진 사람이고 を 는 자리를 그대로 지킨다', {}),
                txt(40, 324, '한국어에는 이 두 줄에 대응하는 수동이 없다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-o-causative-particle',
        svg: svg({
            width: 760,
            height: 306,
            title: '사역에서 시킴을 받는 쪽에 붙는 조사',
            desc: '타동사의 사역에서는 に 만 되고, 자동사의 사역에서는 に 와 を 가 모두 된다.',
            body: J(
                txt(40, 30, '타동사의 사역 — 시킴을 받는 쪽은 に', { cls: 'f1' }),
                box(40, 46, 160, 44, 'せんせいが', { stroke: GREY }),
                box(208, 46, 160, 44, 'がくせいに', { stroke: BLUE, sw: 1.8 }),
                box(376, 46, 130, 44, 'ほんを', { stroke: ORANGE, sw: 1.8 }),
                box(514, 46, 160, 44, 'よませる', { stroke: GREY }),
                txt(40, 116, 'を 로 표시된 것이 한 문장에 둘 오는 꼴은 만들지 않는다. 그래서 이 자리는 に 다', {}),
                link(40, 140, 720, 140, { stroke: GRID }),
                txt(40, 168, '자동사의 사역 — 둘 다 된다', { cls: 'f2' }),
                box(40, 184, 240, 44, 'こどもを いかせる', { stroke: ORANGE }),
                box(300, 184, 240, 44, 'こどもに いかせる', { stroke: BLUE }),
                txt(566, 210, 'を 자리가 비어 있다', { cls: 'ink2', size: 'sm' }),
                txt(40, 256, 'を 쪽은 시키는 힘이 강한 쪽으로, に 쪽은 상대의 뜻을 인정하는 쪽으로', {}),
                txt(40, 278, '읽힌다고 적는 교재가 많다. 형태가 뜻을 하나로 정해 주지는 않는다', {}),
            ),
        }),
    },
    {
        name: 'jp-o-layer-stack',
        svg: svg({
            width: 760,
            height: 320,
            title: '사역수동에서 층이 겹쳐 붙는 것',
            desc: '한 술어 안에 다섯 층이 정해진 순서로 붙어 있고, 층마다 다른 장이 다룬다.',
            body: J(
                txt(50, 34, '8장이 말한 층이 실제로 층이라는 것이 이 꼴에서 드러난다', {}),
                ...[
                    [50, 130, 'よま', '뜻', '11장'],
                    [190, 100, 'せ', '사역', '14장'],
                    [300, 110, 'られ', '수동', '14장'],
                    [420, 110, 'まし', '정중함', '16 · 18장'],
                    [540, 90, 'た', '때', '13장'],
                ].map(([x, w, k, role, ch]) => box(x, 56, w, 48, k, { stroke: BLUE, sw: 1.6 })
                    + txt(x + w / 2, 126, role, { anchor: 'middle' })
                    + txt(x + w / 2, 148, ch, { anchor: 'middle', cls: 'ink2', size: 'sm' })),
                px(50, 180, 630, 180, { cls: 'ax', marker: 'ark' }),
                txt(50, 204, '안쪽 — 뜻에 가깝다', { cls: 'ink2', size: 'sm' }),
                txt(630, 204, '바깥쪽 — 문장 끝', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                box(40, 226, 680, 62,
                    ['순서는 뒤집히지 않는다',
                        '수동을 안에 넣고 사역을 밖에 붙인 꼴은 만들지 않는다'],
                    { stroke: ORANGE, gap: 20 }),
                txt(40, 310, '층을 하나씩 벗기면 よむ 가 남는다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-o-teiru-tearu',
        svg: svg({
            width: 760,
            height: 320,
            title: '같은 창문을 세 가지로 말한다',
            desc: '자동사와 ている, 타동사와 てある, 타동사와 ている 가 각각 다른 것을 나타낸다.',
            body: J(
                txt(40, 30, '같은 창문을 세 가지로 말한다', {}),
                ...[
                    [40, '자동사 + ている', ['まどが', 'あいて いる'], BLUE,
                        '결과 상태', '어떻게 열렸는지 말하지 않는다'],
                    [285, '타동사 + てある', ['まどが', 'あけて ある'], ORANGE,
                        '누가 그렇게 해 두었다', '사람의 손길이 문장에 들어온다'],
                    [530, '타동사 + ている', ['まどを', 'あけて いる'], GREEN,
                        '진행', '지금 여는 중이다'],
                ].map(([x, head, lines, color, m1, m2]) => txt(x + 95, 60, head, {
                    anchor: 'middle', cls: 'ink2', size: 'sm',
                })
                    + box(x, 72, 190, 66, lines, { stroke: color, sw: 1.8, gap: 24 })
                    + txt(x + 95, 164, m1, { anchor: 'middle' })
                    + txt(x + 95, 186, m2, { anchor: 'middle', cls: 'ink2', size: 'sm' })),
                box(40, 208, 680, 62,
                    ['왼쪽 둘은 같은 창문을 가리킬 수 있다',
                        '갈리는 것은 사람의 손길을 문장에 넣는가다'],
                    { stroke: GREEN, gap: 20 }),
                txt(40, 300, '진행과 결과 상태라는 이름은 13장이 세운 것이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
