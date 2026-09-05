/**
 * 12장(형용사) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재는 활용 꼬리라서 그림에 들어가는 일본어가 거의 다 가나다.
 * 한자로 적는 낱말(高い·静か 등)은 그림에서 가나 표기로만 적고, 한자 표기는
 * 본문의 +jp 가 맡는다. 갈래를 가리는 절차의 첫 물음이 &#39;표기&#39;에 걸려 있는데
 * 그림에서 한자 표기를 보여 줄 수 없으므로, 그 물음은 본문 표가 함께 받는다.
 */
import { svg, px, txt, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

const GREY = 'var(--ink2)';
const BLUE = 'var(--s1)';
const ORANGE = 'var(--s2)';
const GREEN = 'var(--s3)';

/** 큰 글자. lib.mjs 가 <style> 로 text{font-size:13px} 를 박으므로 인라인으로 준다. */
function big(x, y, s, o = {}) {
    const style = `font-size:${o.px || 18}px` + (o.bold ? ';font-weight:600' : '');
    return `<text x="${x}" y="${y}" text-anchor="${o.anchor || 'start'}"`
        + ` class="${o.cls || 'ink'}" style="${style}">${esc(s)}</text>`;
}

/**
 * 사각 상자와 가운데 맞춤 글줄. lines 는 문자열 또는 {t, px, cls, bold} 객체.
 * 글줄 높이를 글자 크기에서 잡으므로 큰 글자와 작은 글자를 섞을 수 있다.
 */
function box(x, y, w, h, lines = [], o = {}) {
    const ls = (Array.isArray(lines) ? lines : [lines]).map(l => (typeof l === 'string' ? { t: l } : l));
    const step = l => (l.px || 13) + 6;
    const total = ls.reduce((a, l) => a + step(l), 0) - 6;
    let cy = y + h / 2 - total / 2;
    const out = [`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.rx === undefined ? 6 : o.rx}"`
        + ` fill="${o.fill || 'none'}" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.4}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''}/>`];
    for (const l of ls) {
        cy += l.px || 13;
        out.push(big(x + w / 2, cy - 2, l.t, { px: l.px, anchor: 'middle', cls: l.cls, bold: l.bold }));
        cy += 6;
    }
    return out.join('');
}

/**
 * 어간과 꼬리를 색으로 갈라 적는다. 가나는 글자당 폭이 글꼴 크기와 거의 같으므로
 * 그만큼 밀어 두면 붙어 보인다. 가나로만 된 문자열에만 쓴다.
 */
function stemTail(x, y, stem, tail, o = {}) {
    const p = o.px || 18;
    return big(x, y, stem, { px: p })
        + big(x + stem.length * p, y, tail, { px: p, cls: o.cls || 'f1', bold: true });
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-a-two-classes',
        svg: svg({
            width: 760,
            height: 300,
            title: '형용사의 갈래 — 한국어 하나, 일본어 둘',
            desc: '왼쪽은 갈래가 하나인 한국어, 오른쪽은 갈래가 둘인 일본어. 오른쪽 두 줄에서 바뀌는 자리가 서로 다르다.',
            body: J(
                txt(36, 28, '한국어 — 갈래가 하나다', { cls: 'ink2', size: 'sm' }),
                box(36, 42, 262, 50, [{ t: '높다 → 높았다', px: 16 }], { stroke: GREEN }),
                box(36, 102, 262, 50, [{ t: '높다 → 높지 않다', px: 16 }], { stroke: GREEN }),
                txt(36, 184, '형용사가 동사와 같은 방식으로 활용한다', { cls: 'ink2', size: 'sm' }),
                txt(36, 204, '갈래를 고를 일이 없다', { cls: 'ink2', size: 'sm' }),
                `<path fill="none" stroke="${GREY}" stroke-width="1" stroke-dasharray="4 4" d="M322 22 L322 262"/>`,
                txt(352, 28, '일본어 — 갈래가 둘이다', { cls: 'ink2', size: 'sm' }),
                box(352, 42, 372, 92, [
                    { t: 'い형용사', px: 12, cls: 'ink2' },
                    { t: 'たかい → たかかった', px: 17 },
                    { t: '낱말이 스스로 바뀐다', px: 12, cls: 'ink2' },
                ], { stroke: BLUE }),
                box(352, 152, 372, 92, [
                    { t: 'な형용사', px: 12, cls: 'ink2' },
                    { t: 'しずかだ → しずかだった', px: 17 },
                    { t: 'だ 가 바뀌고 낱말은 그대로다', px: 12, cls: 'ink2' },
                ], { stroke: ORANGE }),
                txt(36, 284, '먼저 어느 갈래인지 알아야 활용을 만들 수 있다 — 한국어에는 없던 단계다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-a-i-stem-tail',
        svg: svg({
            width: 760,
            height: 300,
            title: 'い형용사의 어간과 꼬리',
            desc: '왼쪽 어간은 그대로 있고 오른쪽 꼬리 다섯 개가 갈아 붙는다. 꼬리마다 활용형의 이름이 붙어 있다.',
            body: J(
                txt(40, 26, 'い형용사 — 어간은 그대로 있고 꼬리만 갈린다', { cls: 'ink2', size: 'sm' }),
                box(40, 60, 140, 160, [
                    { t: 'たか', px: 24, bold: true },
                    { t: '어간', px: 12, cls: 'ink2' },
                ], { sw: 1.8 }),
                ...[
                    ['い', '사전형', 'たかい'],
                    ['かった', 'た형', 'たかかった'],
                    ['くない', 'ない형', 'たかくない'],
                    ['くて', 'て형', 'たかくて'],
                    ['く', '부사형', 'たかく'],
                ].flatMap(([tail, name, form], i) => {
                    const y = 44 + i * 42;
                    return [
                        px(184, 140, 286, y + 17, { cls: 's1', marker: 'ar1', width: 1.6 }),
                        box(290, y, 110, 34, [{ t: tail, px: 17, cls: 'f1', bold: true }], { stroke: BLUE }),
                        txt(418, y + 22, name, { cls: 'ink2' }),
                        big(510, y + 23, form, { px: 16 }),
                    ];
                }),
                txt(40, 268, 'ない형은 그 자체가 い 로 끝나므로 다시 같은 규칙으로 활용한다 — たかくなかった',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-a-na-like-noun',
        svg: svg({
            width: 760,
            height: 330,
            title: 'な형용사의 활용과 명사문의 활용',
            desc: '왼쪽은 명사, 오른쪽은 な형용사. 낱말은 그대로이고 뒤에 붙은 だ 가 같은 꼴로 바뀐다.',
            body: J(
                txt(50, 28, '명사 — 8장의 명사문', { cls: 'ink2', size: 'sm' }),
                txt(470, 28, 'な형용사', { cls: 'ink2', size: 'sm' }),
                ...[
                    ['だ', '사전형'],
                    ['だった', 'た형'],
                    ['ではない', 'ない형'],
                    ['で', 'て형'],
                ].flatMap(([tail, name], i) => {
                    const y = 42 + i * 50;
                    return [
                        box(50, y, 250, 40, [], { stroke: GREY }),
                        stemTail(70, y + 27, 'くるま', tail),
                        txt(380, y + 25, name, { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                        box(460, y, 250, 40, [], { stroke: GREY }),
                        stemTail(480, y + 27, 'しずか', tail, { cls: 'f2' }),
                    ];
                }),
                box(50, 252, 660, 56, [
                    { t: '바뀌는 것은 낱말이 아니라 뒤에 붙은 だ 다', px: 14 },
                    { t: '그 だ 는 명사 뒤에서도 같은 꼴로 바뀐다', px: 13, cls: 'ink2' },
                ], { stroke: GREEN, dash: '5 4' }),
            ),
        }),
    },
    {
        name: 'jp-a-which-class',
        svg: svg({
            width: 760,
            height: 392,
            title: '어느 갈래인지 가리는 절차',
            desc: '표기가 い 로 끝나는지 먼저 묻고, 그 다음 예외 목록을 묻는다. 확실하지 않을 때의 시험을 아래에 적었다.',
            body: J(
                box(50, 26, 330, 46, [{ t: '사전형의 표기가 い 로 끝나는가', px: 14 }], { stroke: BLUE }),
                px(384, 49, 442, 49, { cls: 'ax', marker: 'ark' }),
                txt(413, 40, '아니오', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(446, 26, 264, 46, [{ t: 'い형용사가 아니다', px: 14 }], { stroke: GREEN }),
                px(215, 76, 215, 104, { cls: 'ax', marker: 'ark' }),
                txt(223, 96, '예', { cls: 'ink2', size: 'sm' }),
                box(50, 106, 330, 48, [
                    { t: '예외 목록에 드는 낱말인가', px: 14 },
                    { t: 'きらい · きれい · ていねい', px: 12, cls: 'ink2' },
                ], { stroke: BLUE }),
                px(384, 130, 442, 130, { cls: 'ax', marker: 'ark' }),
                txt(413, 121, '예', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(446, 108, 264, 46, [{ t: 'な형용사', px: 14 }], { stroke: GREEN }),
                px(215, 158, 215, 186, { cls: 'ax', marker: 'ark' }),
                txt(223, 178, '아니오', { cls: 'ink2', size: 'sm' }),
                box(50, 188, 330, 46, [{ t: 'い형용사', px: 14 }], { stroke: GREEN }),
                txt(400, 214, '가나로만 적는 낱말에는 첫 물음이 통하지 않는다', { cls: 'ink2', size: 'sm' }),
                box(50, 268, 660, 100, [
                    { t: '확실하지 않으면 시험한다', px: 14, bold: true },
                    { t: '명사 앞에 놓아 본다 — な 가 필요하면 な형용사', px: 13 },
                    { t: '과거로 만들어 본다 — かった 가 붙으면 い형용사', px: 13 },
                    { t: '처음 보는 낱말은 사전이 갈래를 적어 준다', px: 13 },
                ], { stroke: ORANGE, dash: '5 4' }),
            ),
        }),
    },
    {
        name: 'jp-a-modifier-before',
        svg: svg({
            width: 760,
            height: 340,
            title: '꾸미는 말이 앞에 오는 네 경우',
            desc: '네 줄 모두 꾸미는 말이 왼쪽에 있고, 사이에 끼는 것만 다르다. な, の, 그리고 아무것도 끼지 않는 두 줄.',
            body: J(
                txt(40, 26, '꾸미는 말은 앞에 온다 — 갈리는 것은 사이에 무엇이 끼는가뿐이다',
                    { cls: 'ink2', size: 'sm' }),
                ...[
                    ['い형용사', 'たかい', '—', 'やま', '높은 산', BLUE],
                    ['な형용사', 'しずか', 'な', 'へや', '조용한 방', ORANGE],
                    ['동사', 'たべる  たべた', '—', 'ひと', '먹는 사람 · 먹은 사람', BLUE],
                    ['명사', 'にほんご', 'の', 'ほん', '일본어 책', ORANGE],
                ].flatMap(([kind, mod, join, head, gloss, color], i) => {
                    const y = 44 + i * 58;
                    return [
                        txt(40, y + 27, kind, { cls: 'ink2', size: 'sm' }),
                        box(120, y, 195, 42, [{ t: mod, px: 16 }], { stroke: color }),
                        box(325, y + 5, 40, 32, [{ t: join, px: 16, cls: join === '—' ? 'ink2' : 'f3', bold: join !== '—' }],
                            { stroke: GREY, dash: join === '—' ? '4 3' : undefined }),
                        box(375, y, 145, 42, [{ t: head, px: 16 }], { stroke: GREY, dash: '4 3' }),
                        txt(540, y + 27, gloss, { cls: 'ink2', size: 'sm' }),
                    ];
                }),
                txt(40, 296, '동사는 사전형과 た형이 그대로 붙는다. 두 꼴의 뜻이 갈리는 방식은 13장이 다룬다',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 318, '8장이 세운 어순 규칙 하나로 네 줄이 함께 설명된다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-a-adverb',
        svg: svg({
            width: 760,
            height: 292,
            title: '부사로 바꾸는 두 갈래의 꼴',
            desc: 'い형용사는 い 를 く 로, な형용사는 だ 를 に 로 바꾼다. 그 꼴에 なる 를 이은 결과를 오른쪽에 두었다.',
            body: J(
                txt(40, 26, '부사로 바꾸는 꼴 — 갈래마다 다르다', { cls: 'ink2', size: 'sm' }),
                ...[
                    ['たかい', 'い → く', 'たかく', 'たかくなる', BLUE, 's1', 'ar1', 'f1'],
                    ['しずかだ', 'だ → に', 'しずかに', 'しずかになる', ORANGE, 's2', 'ar2', 'f2'],
                ].flatMap(([from, rule, adv, become, color, cls, marker, fill], i) => {
                    const y = 50 + i * 78;
                    return [
                        box(40, y, 170, 44, [{ t: from, px: 18 }], { stroke: color }),
                        px(214, y + 22, 286, y + 22, { cls, marker, width: 1.8 }),
                        txt(250, y + 12, rule, { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                        box(290, y, 170, 44, [{ t: adv, px: 18, cls: fill, bold: true }], { stroke: color }),
                        px(464, y + 22, 536, y + 22, { cls: 'ax', marker: 'ark', width: 1.6 }),
                        txt(500, y + 12, '+ なる', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                        box(540, y, 180, 44, [{ t: become, px: 18 }], { stroke: GREY, dash: '4 3' }),
                    ];
                }),
                txt(40, 224, '부사가 된 꼴은 뒤에 오는 동사를 꾸민다 — はやく おきる · しずかに はなす',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 248, 'なる 를 이으면 ‘그렇게 되다’가 된다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-a-comparison',
        svg: svg({
            width: 760,
            height: 264,
            title: '견주는 표현에서 두 조사가 붙는 자리',
            desc: '가로축의 왼쪽이 기준이고 오른쪽이 더하다고 말하는 쪽이다. 아래에 문장의 짜임을 적었다.',
            body: J(
                txt(40, 28, '두 조사가 각각 어느 자리에 붙는가', { cls: 'ink2', size: 'sm' }),
                px(60, 116, 700, 116, { cls: 'ax', marker: 'ark', width: 1.6 }),
                txt(700, 100, '이쪽이 더 그렇다', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                `<circle cx="220" cy="116" r="6" class="f1"/>`,
                `<circle cx="560" cy="116" r="6" class="f2"/>`,
                big(220, 88, 'バス', { anchor: 'middle', px: 18 }),
                big(560, 88, 'でんしゃ', { anchor: 'middle', px: 18 }),
                txt(220, 144, 'より 가 붙는다', { anchor: 'middle' }),
                txt(220, 164, '견주는 기준', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(560, 144, 'のほうが 가 붙는다', { anchor: 'middle' }),
                txt(560, 164, '더하다고 말하는 쪽', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                box(140, 200, 480, 46, [{ t: 'バスより でんしゃ のほうが べんりだ', px: 16 }], { stroke: GREEN }),
            ),
        }),
    },
    {
        name: 'jp-a-wrong-joint',
        svg: svg({
            width: 760,
            height: 340,
            title: '꼬리를 잘못 붙이는 두 방향',
            desc: '왼쪽 두 몸통과 오른쪽 두 꼬리 묶음. 곧은 선이 옳은 결합이고 점선이 어긋난 결합이다.',
            body: J(
                txt(40, 26, '꼬리를 어느 몸통에 붙이는가', { cls: 'ink2', size: 'sm' }),
                box(40, 66, 160, 48, [
                    { t: 'たか', px: 18 },
                    { t: 'い형용사의 어간', px: 11, cls: 'ink2' },
                ], { stroke: BLUE }),
                box(40, 196, 160, 48, [
                    { t: 'しずか', px: 18 },
                    { t: 'な형용사', px: 11, cls: 'ink2' },
                ], { stroke: ORANGE }),
                box(430, 48, 290, 76, [
                    { t: 'かった · くない · く', px: 16, cls: 'f1' },
                    { t: 'い형용사의 꼬리', px: 11, cls: 'ink2' },
                ], { stroke: BLUE }),
                box(430, 188, 290, 76, [
                    { t: 'だった · ではない · に', px: 16, cls: 'f2' },
                    { t: 'な형용사의 꼬리', px: 11, cls: 'ink2' },
                ], { stroke: ORANGE }),
                px(204, 86, 424, 86, { cls: 's3', marker: 'ar3', width: 2 }),
                px(204, 224, 424, 224, { cls: 's3', marker: 'ar3', width: 2 }),
                px(204, 100, 424, 200, { cls: 'ax', marker: 'ark', width: 1.2, dash: '5 4' }),
                px(204, 212, 424, 112, { cls: 'ax', marker: 'ark', width: 1.2, dash: '5 4' }),
                big(272, 137, '×', { px: 17, anchor: 'middle', cls: 'ink2' }),
                big(272, 186, '×', { px: 17, anchor: 'middle', cls: 'ink2' }),
                txt(40, 294, '× たかいだ  × たかいな — い형용사에 な형용사 쪽의 꼬리를 붙인 것',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 316, '× しずかくない — な형용사에 い형용사 쪽의 꼬리를 붙인 것',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
