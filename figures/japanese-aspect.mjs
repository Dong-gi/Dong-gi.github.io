/**
 * 13장(때와 모습) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 주요 그림은 시간선이다. 시제와 상을 두 축으로 놓은 것, 같은 꼴이 두 뜻으로
 * 갈리는 것, 동사의 성질에 따라 결과가 갈리는 것 셋이 중심이고 나머지가 거기 붙는다.
 *
 * 낱말은 전부 가나로 적었다. 이 장이 다루는 것은 꼬리에 붙는 꼴이고 꼬리는 전부
 * 가나여서, 한자를 넣지 않아도 그림이 보일 것을 다 보인다. 한자로 적어야 갈리는
 * 자리(같은 소리로 읽히는 두 동사)는 그림에 넣지 않고 본문 표로 보냈다.
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

/** 세로 눈금. 시간선 위의 한 점을 가리킬 때 쓴다. */
function tick(x, y1, y2, o = {}) {
    return `<path fill="none" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.6}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x} ${y1} V${y2}"/>`;
}

/** 시간선 위에서 일이 걸쳐 있는 구간. */
function span(x, y, w, h, o = {}) {
    return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.rx === undefined ? 4 : o.rx}"`
        + ` fill="${o.fill || 'none'}" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.6}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''}/>`;
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
        name: 'jp-x-tense-aspect-axes',
        svg: svg({
            width: 760,
            height: 326,
            title: '이 장이 쓰는 두 축',
            desc: '위는 말하는 때를 기준으로 언제인지를 재는 축, 아래는 일이 어느 단계에 있는지를 재는 축이다.',
            body: J(
                txt(40, 30, '시제 — 말하는 때를 기준으로 언제인가', { cls: 'f1' }),
                px(50, 92, 726, 92, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(390, 74, 110, { stroke: BLUE, sw: 2 }),
                txt(390, 130, '말하는 때', { anchor: 'middle', cls: 'f1' }),
                txt(200, 70, '지난 일', { anchor: 'middle', cls: 'ink2' }),
                txt(560, 70, '지금 · 앞일', { anchor: 'middle', cls: 'ink2' }),
                link(40, 158, 720, 158, { stroke: GRID, dash: '5 4' }),
                txt(40, 192, '상 — 그 일이 어느 단계에 있는가', { cls: 'f2' }),
                span(220, 226, 280, 26, { stroke: ORANGE }),
                txt(360, 244, '하는 중', { anchor: 'middle', cls: 'ink' }),
                tick(220, 214, 264, { stroke: ORANGE }),
                tick(500, 214, 264, { stroke: ORANGE }),
                txt(220, 208, '시작', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(500, 208, '끝', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                span(516, 226, 190, 26, { stroke: GREEN, dash: '5 4' }),
                txt(611, 244, '끝난 뒤의 상태', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(40, 292, '아래 축에는 어제인지 내일인지가 들어 있지 않다. 두 축은 서로 다른 것을 묻는다', {}),
                txt(40, 316, '그래서 한 문장은 두 축에서 각각 한 값을 가진다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-x-no-future-form',
        svg: svg({
            width: 760,
            height: 268,
            title: '지난 일에는 꼴이 따로 있고 앞일에는 없다',
            desc: '말하는 때 왼쪽만 따로 꼴을 가진다. 오른쪽은 지금과 앞일이 한 꼴을 함께 쓴다.',
            body: J(
                px(50, 96, 726, 96, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(330, 76, 116, { stroke: GREY, sw: 2 }),
                txt(330, 136, '말하는 때', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(170, 72, '지난 일', { anchor: 'middle', cls: 'ink2' }),
                txt(430, 72, '지금', { anchor: 'middle', cls: 'ink2' }),
                txt(600, 72, '앞일', { anchor: 'middle', cls: 'ink2' }),
                box(60, 158, 240, 44, 'た형', { stroke: BLUE, sw: 1.8 }),
                box(360, 158, 366, 44, '사전형 · ます형', { stroke: ORANGE, sw: 1.8 }),
                link(180, 116, 180, 158, { stroke: BLUE }),
                link(430, 116, 430, 158, { stroke: ORANGE }),
                link(600, 116, 600, 158, { stroke: ORANGE }),
                txt(40, 234, '앞일만 가리키는 꼴이 따로 없다. 오른쪽 상자 하나가 지금과 앞일을 함께 맡는다', {}),
                txt(40, 258, '한국어도 이 자리는 같다 — ‘읽는다’ 가 지금과 앞일을 함께 맡는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-x-ta-not-past',
        svg: svg({
            width: 760,
            height: 336,
            title: 'た형이 나타나는 자리 다섯',
            desc: '맨 위 하나만 지난 일이고 나머지 넷은 지난 일이 아니다.',
            body: J(
                box(40, 122, 130, 60, 'た형', { sw: 2, stroke: GREY }),
                px(174, 152, 246, 44, { cls: 's1', marker: 'ar1' }),
                px(174, 152, 246, 108, { cls: 's2', marker: 'ar2' }),
                px(174, 152, 246, 152, { cls: 's2', marker: 'ar2' }),
                px(174, 152, 246, 196, { cls: 's2', marker: 'ar2' }),
                px(174, 152, 246, 250, { cls: 's2', marker: 'ar2' }),
                box(250, 24, 470, 40, '지난 일 — きのう よんだ', { stroke: BLUE }),
                box(250, 88, 470, 40, '찾던 것을 찾은 순간 — あった', { stroke: ORANGE }),
                box(250, 132, 470, 40, '지금 막 알아차림 — あした は やすみ だった', { stroke: ORANGE }),
                box(250, 176, 470, 40, '명사를 꾸미는 자리의 상태 — まがった みち', { stroke: ORANGE }),
                box(250, 230, 470, 40, '입말에서 재촉하는 굳은 표현 — どいた どいた', { stroke: ORANGE, dash: '5 4' }),
                txt(40, 302, '주황 상자 넷은 지난 일이 아니다. 맨 아래는 쓰이는 낱말이 정해져 있어 따로 표시했다', {}),
                txt(40, 326, '그래서 이 꼴을 과거형이라고만 부르면 설명되지 않는 자리가 남는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-x-teiru-two-readings',
        svg: svg({
            width: 760,
            height: 300,
            title: 'ている 이 두 뜻으로 갈리는 짜임',
            desc: '꼴은 하나이고 갈래는 둘이다. 어느 쪽이 되는지는 앞에 오는 동사가 정한다.',
            body: J(
                box(268, 108, 200, 68, '', { sw: 2, stroke: GREY }),
                big(300, 150, 'ている', { size: 24 }),
                box(40, 216, 228, 46, '동사의 성질', { stroke: GREEN, sw: 1.8 }),
                px(154, 216, 340, 180, { cls: 's3', marker: 'ar3' }),
                txt(180, 200, '갈래를 정하는 것', { cls: 'f3', size: 'sm' }),
                px(472, 132, 536, 76, { cls: 'ax', marker: 'ark' }),
                px(472, 152, 536, 208, { cls: 'ax', marker: 'ark' }),
                box(540, 44, 190, 62, ['진행', '하는 중이다'], { stroke: BLUE }),
                box(540, 178, 190, 62, ['결과 상태', '끝난 뒤의 모습이다'], { stroke: ORANGE }),
                txt(40, 40, '꼴은 하나다', { cls: 'ink' }),
                txt(40, 64, '무엇이 붙었는지를 보고는', { cls: 'ink2', size: 'sm' }),
                txt(40, 82, '어느 쪽인지 알 수 없다', { cls: 'ink2', size: 'sm' }),
                txt(40, 292, '한국어는 이 갈래를 형태로 갈라 적는다. 그것이 이 장에서 가장 크게 어긋나는 자리다', {}),
            ),
        }),
    },
    {
        name: 'jp-x-verb-kinds-timeline',
        svg: svg({
            width: 760,
            height: 372,
            title: '동사의 성질이 ている 의 뜻을 정하는 방식',
            desc: '시간이 걸리는 동사에서는 일의 안쪽을, 한순간에 끝나는 동사에서는 끝난 뒤를 가리킨다.',
            body: J(
                txt(40, 30, '시간이 걸리는 동사 — よむ · かく · たべる · ふる', { cls: 'f1' }),
                px(50, 92, 726, 92, { cls: 'ax', marker: 'ark', width: 1.5 }),
                span(200, 78, 300, 28, { stroke: BLUE, sw: 2 }),
                txt(350, 72, '일이 걸쳐 있는 구간', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(350, 150, 350, 110, { cls: 's1', marker: 'ar1' }),
                txt(350, 168, 'ている 이 가리키는 곳 — 하는 중', { anchor: 'middle', cls: 'f1' }),
                link(40, 202, 720, 202, { stroke: GRID, dash: '5 4' }),
                txt(40, 236, '한순간에 끝나는 동사 — くる · しぬ · すわる · けっこんする', { cls: 'f2' }),
                px(50, 292, 726, 292, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(280, 272, 312, { stroke: ORANGE, sw: 2.4 }),
                txt(280, 266, '일이 일어난 한 점', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                span(280, 278, 260, 28, { stroke: ORANGE, dash: '5 4' }),
                txt(410, 296, '끝난 뒤의 상태', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(410, 350, 410, 310, { cls: 's2', marker: 'ar2' }),
                txt(410, 368, 'ている 이 가리키는 곳 — 결과 상태', { anchor: 'middle', cls: 'f2' }),
            ),
        }),
    },
    {
        name: 'jp-x-korean-one-form-many',
        svg: svg({
            width: 760,
            height: 330,
            title: '한국어 쪽에서는 넷으로 갈리는 것이 일본어에서는 한 꼴이다',
            desc: '왼쪽 네 가지가 오른쪽 한 꼴로 모인다. 그래서 형태가 갈래를 알려 주지 않는다.',
            body: J(
                txt(40, 30, '한국어', { cls: 'ink2', size: 'sm' }),
                txt(560, 30, '일본어', { cls: 'ink2', size: 'sm' }),
                box(40, 44, 300, 40, '…고 있다 — 읽고 있다', { stroke: BLUE }),
                box(40, 96, 300, 40, '…어 있다 — 앉아 있다', { stroke: ORANGE }),
                box(40, 148, 300, 40, '과거형 — 결혼했다', { stroke: GREEN }),
                box(40, 200, 300, 40, '현재형 — 안다', { stroke: GREY }),
                px(346, 64, 528, 140, { cls: 'ax', marker: 'ark' }),
                px(346, 116, 528, 150, { cls: 'ax', marker: 'ark' }),
                px(346, 168, 528, 160, { cls: 'ax', marker: 'ark' }),
                px(346, 220, 528, 170, { cls: 'ax', marker: 'ark' }),
                box(532, 122, 190, 66, '', { sw: 2 }),
                big(560, 164, 'ている', { size: 22 }),
                txt(40, 286, '왼쪽에서 어느 칸을 골라야 하는지는 일본어 쪽 형태가 알려 주지 않는다', {}),
                txt(40, 310, '그래서 옮길 때 손이 먼저 가는 칸을 쓰게 되고, 그 칸이 첫째 줄이라 오류가 굳는다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-x-tense-aspect-grid',
        svg: svg({
            width: 760,
            height: 314,
            title: '때와 모습이 따로 붙는다',
            desc: '가로로 가면 때가 붙고 세로로 가면 모습이 붙는다. 네 칸이 두 축의 조합이다.',
            body: J(
                txt(300, 44, '지금 · 앞일', { anchor: 'middle', cls: 'f1' }),
                txt(540, 44, '지난 일', { anchor: 'middle', cls: 'f1' }),
                txt(60, 44, '때 →', { cls: 'ink2', size: 'sm' }),
                txt(40, 104, '모습을', { cls: 'ink2', size: 'sm' }),
                txt(40, 122, '갈라 적지', { cls: 'ink2', size: 'sm' }),
                txt(40, 140, '않음', { cls: 'ink2', size: 'sm' }),
                txt(40, 216, '모습을', { cls: 'f2', size: 'sm' }),
                txt(40, 234, '갈라 적음', { cls: 'f2', size: 'sm' }),
                box(180, 62, 240, 92, 'よむ', { sw: 1.8 }),
                box(440, 62, 240, 92, 'よんだ', { stroke: BLUE, sw: 1.8 }),
                box(180, 174, 240, 92, 'よんでいる', { stroke: ORANGE, sw: 1.8 }),
                box(440, 174, 240, 92, 'よんでいた', { stroke: GREEN, sw: 1.8 }),
                px(424, 108, 436, 108, { cls: 's1', marker: 'ar1' }),
                px(424, 220, 436, 220, { cls: 's1', marker: 'ar1' }),
                px(300, 158, 300, 170, { cls: 's2', marker: 'ar2' }),
                px(560, 158, 560, 170, { cls: 's2', marker: 'ar2' }),
                txt(40, 300, '오른쪽 아래 칸에는 두 층이 함께 붙어 있다. 모습이 안쪽, 때가 바깥쪽이다', {}),
            ),
        }),
    },
    {
        name: 'jp-x-te-kuru-iku-direction',
        svg: svg({
            width: 760,
            height: 340,
            title: 'てくる 와 ていく 의 기준점',
            desc: '위는 곳의 방향, 아래는 때의 방향이다. 기준점은 어느 쪽에서도 말하는 사람 자신이다.',
            body: J(
                txt(40, 30, '곳 — 말하는 사람을 기준으로', { cls: 'f1' }),
                tick(380, 56, 118, { stroke: GREY, sw: 2 }),
                txt(380, 138, '말하는 사람', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(200, 88, 360, 88, { cls: 's1', marker: 'ar1' }),
                px(400, 88, 570, 88, { cls: 's2', marker: 'ar2' }),
                txt(200, 74, 'てくる — もってくる', { cls: 'f1', size: 'sm' }),
                txt(408, 74, 'ていく — もっていく', { cls: 'f2', size: 'sm' }),
                link(40, 172, 720, 172, { stroke: GRID, dash: '5 4' }),
                txt(40, 206, '때 — 지금을 기준으로', { cls: 'f3' }),
                px(50, 262, 726, 262, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(380, 240, 288, { stroke: GREY, sw: 2 }),
                txt(380, 308, '지금', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                px(200, 240, 366, 240, { cls: 's1', marker: 'ar1' }),
                px(400, 240, 570, 240, { cls: 's2', marker: 'ar2' }),
                txt(200, 226, 'てきた — ふえてきた', { cls: 'f1', size: 'sm' }),
                txt(408, 226, 'ていく — ふえていく', { cls: 'f2', size: 'sm' }),
                txt(40, 334, '방향이 정해져 있으므로 바꿔 쓸 수 없다. 앞일을 말하면서 왼쪽 꼴을 쓰지 않는다', {}),
            ),
        }),
    },
    {
        name: 'jp-x-clause-relative-time',
        svg: svg({
            width: 760,
            height: 344,
            title: '명사를 꾸미는 절 안의 꼴은 무엇을 기준으로 정해지는가',
            desc: '두 줄에서 말하는 때의 자리가 서로 다르다. 그래도 절 안의 꼴은 같은 규칙으로 정해진다.',
            body: J(
                txt(40, 30, '절이 사전형일 때 — 절의 일이 주절의 일보다 뒤', { cls: 'f1' }),
                px(50, 92, 726, 92, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(300, 74, 110, { stroke: BLUE, sw: 2.4 }),
                tick(470, 74, 110, { stroke: ORANGE, sw: 2.4, dash: '4 3' }),
                tick(630, 66, 118, { stroke: GREY, sw: 2 }),
                txt(300, 66, '주절의 일', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(470, 132, '절의 일', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                txt(630, 60, '말하는 때', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(300, 132, '여기서 문장이 끝난다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                link(40, 176, 720, 176, { stroke: GRID, dash: '5 4' }),
                txt(40, 210, '절이 た형일 때 — 절의 일이 주절의 일보다 앞', { cls: 'f2' }),
                px(50, 272, 726, 272, { cls: 'ax', marker: 'ark', width: 1.5 }),
                tick(140, 246, 298, { stroke: GREY, sw: 2 }),
                tick(360, 254, 290, { stroke: ORANGE, sw: 2.4, dash: '4 3' }),
                tick(540, 254, 290, { stroke: BLUE, sw: 2.4 }),
                txt(140, 240, '말하는 때', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(360, 240, '절의 일', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                txt(540, 240, '주절의 일', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(40, 334, '위는 주절이 지난 일이고 아래는 앞일이다. 절 안의 꼴은 말하는 때와 무관하게 정해진다', {}),
            ),
        }),
    },
];
