/**
 * 19장(실제로 읽어 본다) 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 주요 그림은 문장 구조도다. 긴 문장을 절로 자르고, 절마다 술어를 짚고,
 * 절과 절 사이에 놓인 잇는 말을 표시한다. 본문에서는 그 문장들이 한자로 적히지만
 * 그림에서는 전부 가나로 적었다 — 절의 경계를 보이는 데 필요한 것은 술어의 꼬리와
 * 잇는 말이고, 둘 다 가나이기 때문이다.
 *
 * 색의 약속을 그림 사이에서 지킨다.
 *   주황 = 절의 술어, 파랑 = 잇는 말, 초록 = 도착점(사전형·마지막 단계).
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

/** 세로 경계선. 절이 갈리는 자리를 표시한다. */
function cut(x, y1, y2, o = {}) {
    return `<path fill="none" stroke="${o.stroke || GRID}" stroke-width="${o.sw || 1.3}"`
        + ` stroke-dasharray="${o.dash || '5 4'}" d="M${x} ${y1} V${y2}"/>`;
}

/** 아래가 열린 대괄호. 구간의 범위를 표시한다. */
function span(x1, x2, y, o = {}) {
    const d = o.depth === undefined ? 7 : o.depth;
    return `<path fill="none" stroke="${o.stroke || GREY}" stroke-width="${o.sw || 1.3}"`
        + ` d="M${x1} ${y - d} V${y} H${x2} V${y - d}"/>`;
}

/**
 * 큰 글자. lib.mjs 가 <style> 에 text{font-size:13px} 를 박으므로
 * 속성으로 크기를 주면 무시된다. 인라인 style 로 준다.
 */
function big(x, y, s, o = {}) {
    return `<text class="${o.cls || 'ink'}" x="${x}" y="${y}"`
        + ` text-anchor="${o.anchor || 'start'}" style="font-size:${o.size || 16}px${o.bold ? ';font-weight:600' : ''}">${esc(s)}</text>`;
}

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-g-clause-chain',
        svg: svg({
            width: 760,
            height: 288,
            title: '문장 하나를 절 셋으로 자른 구조도',
            desc: '절마다 술어가 하나씩 있고, 절이 끝나는 자리에 잇는 말이 놓여 앞 절과 뒤 절의 관계를 적는다.',
            body: J(
                txt(46, 30, '실제 글의 문장은 절이 여럿이다. 잘라서 읽는다', { cls: 'ink2', size: 'sm' }),
                cut(318, 56, 142),
                cut(478, 56, 142),
                big(46, 80, 'ほんはにしゅうかん'),
                big(190, 80, 'かりられます', { cls: 'f2' }),
                big(286, 80, 'が、', { cls: 'f1', bold: true }),
                big(318, 80, 'かえすひを'),
                big(398, 80, 'すぎる', { cls: 'f2' }),
                big(446, 80, 'と、', { cls: 'f1', bold: true }),
                big(478, 80, 'つぎのほんが'),
                big(574, 80, 'かりられません。', { cls: 'f2' }),
                span(46, 316, 104),
                span(320, 476, 104),
                span(480, 702, 104),
                txt(181, 126, '절 1', { anchor: 'middle', cls: 'ink' }),
                txt(398, 126, '절 2', { anchor: 'middle', cls: 'ink' }),
                txt(591, 126, '절 3 — 주절', { anchor: 'middle', cls: 'ink' }),
                link(302, 134, 302, 152, { stroke: BLUE }),
                link(462, 134, 462, 152, { stroke: BLUE }),
                box(266, 152, 72, 28, '뒤집기', { stroke: BLUE, size: 'sm' }),
                box(426, 152, 72, 28, '조건', { stroke: BLUE, size: 'sm' }),
                `<rect x="46" y="200" width="14" height="4" rx="2" class="f2"/>`,
                txt(68, 206, '절의 술어', { cls: 'ink2', size: 'sm' }),
                `<rect x="160" y="200" width="14" height="4" rx="2" class="f1"/>`,
                txt(182, 206, '잇는 말', { cls: 'ink2', size: 'sm' }),
                txt(46, 240, '절마다 술어가 하나씩 있다. 문장 전체의 술어는 맨 끝의 것이다', {}),
                txt(46, 266, '잇는 말은 앞 절의 술어 뒤에 놓인다. 그 자리가 절의 경계다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-g-connective-map',
        svg: svg({
            width: 760,
            height: 338,
            title: '절을 잇는 말의 갈래 넷',
            desc: '이유 · 뒤집기 · 조건 · 그 밖의 네 갈래와 각 갈래에 드는 말들. 넷 모두 앞 절의 술어 뒤에 붙는다.',
            body: J(
                txt(20, 30, '잇는 말은 갈래가 넷이다', { cls: 'ink2', size: 'sm' }),
                [
                    { x: 20, name: '이유', items: ['から', 'ので'], note: ['앞 절이 뒤 절의', '이유가 된다'], c: BLUE },
                    { x: 204, name: '뒤집기', items: ['が', 'けど', 'のに'], note: ['앞에서 기대되는 것과', '뒤가 어긋난다'], c: ORANGE },
                    { x: 388, name: '조건', items: ['と', 'ば', 'たら', 'なら'], note: ['앞이 성립하면', '뒤가 성립한다'], c: GREEN },
                    { x: 572, name: '그 밖', items: ['ながら', 'たり'], note: ['동시에 함 · 여럿을', '늘어놓음'], c: GREY },
                ].map(col => [
                    box(col.x, 48, 168, 200, '', { stroke: col.c }),
                    txt(col.x + 84, 74, col.name, { anchor: 'middle', cls: 'ink' }),
                    link(col.x + 20, 86, col.x + 148, 86, { stroke: col.c }),
                    col.items.map((it, i) => big(col.x + 84, 116 + i * 26, it, { anchor: 'middle' })).join(''),
                    txt(col.x + 84, 218, col.note[0], { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                    txt(col.x + 84, 236, col.note[1], { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                ].join('')).join(''),
                box(20, 268, 720, 46, [
                    '넷 다 앞 절의 술어 뒤에 붙는다 — 명사 뒤에 붙는 조사와 자리가 다르다',
                ], { dash: '6 4' }),
            ),
        }),
    },
    {
        name: 'jp-g-noun-or-predicate',
        svg: svg({
            width: 760,
            height: 286,
            title: '같은 글자가 명사 뒤와 술어 뒤에서 다른 일을 한다',
            desc: '왼쪽은 명사 뒤에 붙은 격조사이고 오른쪽은 술어 뒤에 붙은 잇는 말이다. 가르는 것은 앞에 놓인 것의 종류다.',
            body: J(
                txt(88, 34, '명사 뒤 — 격조사 (9장)', { cls: 'ink' }),
                txt(410, 34, '술어 뒤 — 잇는 말 (19장)', { cls: 'ink' }),
                cut(390, 48, 232, { dash: '6 5' }),
                box(20, 70, 52, 34, 'から', { stroke: BLUE, size: 'sm' }),
                big(88, 94, 'えきからあるきます', { size: 15 }),
                txt(88, 118, '걷기 시작하는 곳을 표시한다', { cls: 'ink2', size: 'sm' }),
                big(410, 94, 'あめがふるから、でかけません', { size: 15 }),
                txt(410, 118, '나가지 않는 이유를 표시한다', { cls: 'ink2', size: 'sm' }),
                link(20, 140, 740, 140, { stroke: GRID }),
                box(20, 158, 52, 34, 'が', { stroke: ORANGE, size: 'sm' }),
                big(88, 182, 'あめがふります', { size: 15 }),
                txt(88, 206, '내리는 주체를 표시한다', { cls: 'ink2', size: 'sm' }),
                big(410, 182, 'たかいですが、かいます', { size: 15 }),
                txt(410, 206, '앞뒤가 어긋난다는 것을 표시한다', { cls: 'ink2', size: 'sm' }),
                txt(20, 258, '가르는 것은 뜻이 아니라 앞에 놓인 것이 명사인가 술어인가다', {}),
            ),
        }),
    },
    {
        name: 'jp-g-four-conditionals',
        svg: svg({
            width: 760,
            height: 344,
            title: '조건의 잇는 말 넷과 겹치는 자리',
            desc: '넷이 가운데의 넓은 겹침을 함께 쓰고, 바깥쪽에 저마다 자기만 하는 일을 하나씩 가진다.',
            body: J(
                txt(40, 30, '넷이 나눠 가진 영역은 가운데에서 크게 겹친다', { cls: 'ink2', size: 'sm' }),
                box(280, 130, 200, 76, ['겹치는 자리', '둘 이상이 다 되는', '문장이 많다'],
                    { rx: 38, dash: '6 4', gap: 20, size: 'sm' }),
                [
                    {
                        x: 40, y: 48, k: 'と', c: BLUE,
                        t: ['앞이 성립하면 언제나', '뒤가 따라온다'],
                        lx: 330, ly: 92, tx: 300, ty: 150,
                    },
                    {
                        x: 430, y: 48, k: 'ば', c: BLUE,
                        t: ['일반적인 조건.', '속담이 이 꼴이다'],
                        lx: 430, ly: 92, tx: 460, ty: 150,
                    },
                    {
                        x: 40, y: 240, k: 'たら', c: GREEN,
                        t: ['그 일이 일어난 뒤.', '제약이 가장 적다'],
                        lx: 330, ly: 260, tx: 300, ty: 194,
                    },
                    {
                        x: 430, y: 240, k: 'なら', c: GREEN,
                        t: ['상대가 말한 것을 받는다.', '앞뒤의 때가 뒤집힐 수 있다'],
                        lx: 430, ly: 260, tx: 460, ty: 194,
                    },
                ].map(b => [
                    link(b.lx, b.ly, b.tx, b.ty, { stroke: GRID, dash: '4 4' }),
                    box(b.x, b.y, 290, 64, '', { stroke: b.c }),
                    big(b.x + 20, b.y + 42, b.k, { size: 21, bold: true }),
                    txt(b.x + 74, b.y + 28, b.t[0], { size: 'sm' }),
                    txt(b.x + 74, b.y + 50, b.t[1], { size: 'sm' }),
                ].join('')).join(''),
                txt(40, 326, '가르는 시험은 뒤 절에 무엇이 오는가다. 부탁이나 명령이 오면 왼쪽 위가 빠진다', {}),
            ),
        }),
    },
    {
        name: 'jp-g-marked-once',
        svg: svg({
            width: 760,
            height: 306,
            title: '층은 문장 끝 한 곳에만 쌓인다',
            desc: '이어진 절이 끝나는 자리에는 층이 쌓이지 않고, 문장의 끝에서 한 번 쌓인다. 예외는 정중함 하나뿐이다.',
            body: J(
                txt(40, 32, '한 문장 안에서 층이 쌓이는 자리는 하나다', { cls: 'ink2', size: 'sm' }),
                box(40, 62, 200, 46, '절 1', {}),
                box(250, 62, 160, 46, '절 2', {}),
                box(420, 62, 140, 46, '주절', {}),
                box(570, 62, 170, 46, '층이 여기 쌓인다', { stroke: ORANGE, size: 'sm' }),
                cut(245, 56, 114),
                cut(415, 56, 114),
                link(560, 85, 570, 85, { stroke: ORANGE }),
                txt(140, 134, '표시하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(330, 134, '표시하지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(655, 134, '정중함 · 부정 · 때 · 태도', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                box(40, 172, 700, 62, '', { dash: '6 4', stroke: BLUE }),
                txt(60, 198, '예외 넷 — 앞에 정중체가 올 수 있다', { cls: 'f1' }),
                big(326, 200, 'から · ので · が · けど'),
                txt(60, 222, '그 경우에도 앞 절에 쌓이는 것은 정중함 하나뿐이다. 때와 태도는 끝에서만 쌓인다',
                    { cls: 'ink2', size: 'sm' }),
                txt(40, 268, '중간의 짧은 꼴을 보고 문장의 때나 말투를 판정하지 않는다', {}),
                txt(40, 292, '앞은 보통체인데 뒤는 정중체라고 보이면 말투가 바뀐 것이 아니라 절이 이어진 것이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-g-read-long-procedure',
        svg: svg({
            width: 760,
            height: 400,
            title: '절이 여럿인 문장을 읽는 절차',
            desc: '8장의 다섯 단계 가운데에 절을 자르고 관계를 정하는 두 단계가 끼어든다.',
            body: J(
                txt(40, 28, '8장의 절차에 두 단계가 끼어든다. 나머지는 그대로다', { cls: 'ink2', size: 'sm' }),
                [
                    '문장의 끝을 본다 — 거기 있는 것이 문장 전체의 술어다',
                    '술어가 무엇인지 보고 갈래를 정한다',
                    '술어에 붙은 층을 벗긴다 — 정중함 · 부정 · 때 · 태도',
                    '절의 경계를 자른다 — 잇는 말이 놓인 자리가 경계다',
                    '절마다 술어를 찾고 잇는 말로 관계를 정한다',
                    '앞으로 돌아가 조사를 보고 덩어리의 구실을 짚는다',
                    '표시되지 않은 자리를 문맥으로 채운다',
                ].map((s, i) => {
                    const y = 46 + i * 44;
                    const isNew = i === 3 || i === 4;
                    return [
                        txt(120, y + 22, String(i + 1), { anchor: 'middle', cls: 'ink2' }),
                        box(140, y, 500, 34, s, {
                            stroke: isNew ? BLUE : (i === 6 ? GREEN : GREY),
                            sw: isNew ? 1.9 : 1.4,
                        }),
                        i < 6 ? px(390, y + 36, 390, y + 42, { cls: 'ax', marker: 'ark', width: 1.4 }) : '',
                    ].join('');
                }).join(''),
                `<path fill="none" stroke="${BLUE}" stroke-width="1.4" d="M652 178 H662 V266 H652"/>`,
                txt(670, 212, '이 장이', { cls: 'f1', size: 'sm' }),
                txt(670, 230, '붙인 것', { cls: 'f1', size: 'sm' }),
                txt(40, 372, '절을 자르기 전에 조사를 보면 어느 절의 조사인지 모르는 채로 관계를 정하게 된다', {}),
            ),
        }),
    },
    {
        name: 'jp-g-news-lead-shape',
        svg: svg({
            width: 760,
            height: 290,
            title: '뉴스 리드 첫 문장의 짜임',
            desc: '한 문장이 때 · 곳 · 일어난 일 · 그 결과의 넷으로 나뉜다. 리드가 긴 이유가 여기 있다.',
            body: J(
                txt(50, 30, '리드는 한 문장에 넷을 몰아넣는다', { cls: 'ink2', size: 'sm' }),
                cut(226, 58, 100),
                cut(402, 58, 100),
                cut(498, 58, 100),
                big(50, 82, 'よっかごごさんじごろ、'),
                big(226, 82, 'とうきょうのえきまえで'),
                big(402, 82, 'かじが'),
                big(450, 82, 'あり、', { cls: 'f2' }),
                big(498, 82, 'みせがふたつ'),
                big(594, 82, 'やけた。', { cls: 'f2' }),
                span(50, 224, 100),
                span(228, 400, 100),
                span(404, 496, 100),
                span(500, 658, 100),
                txt(137, 122, '언제', { anchor: 'middle', cls: 'ink' }),
                txt(314, 122, '어디서', { anchor: 'middle', cls: 'ink' }),
                txt(450, 122, '무엇이 있었고', { anchor: 'middle', cls: 'ink' }),
                txt(579, 122, '어떻게 되었다', { anchor: 'middle', cls: 'ink' }),
                link(466, 130, 466, 156, { stroke: ORANGE }),
                box(346, 156, 240, 46, '', { stroke: ORANGE, dash: '5 4' }),
                big(366, 176, 'ます', { size: 14 }),
                txt(398, 176, '를 뗀 꼴이 절을 잇는다', { cls: 'ink2', size: 'sm' }),
                txt(366, 194, '글말에서 쓰는 이음이다', { cls: 'ink2', size: 'sm' }),
                txt(50, 236, '문장 전체의 술어는 맨 끝의 것 하나이고 때의 층도 거기에만 붙어 있다', {}),
                txt(50, 262, '리드가 긴 것은 낱말이 어려워서가 아니라 넷을 한 문장에 담기 때문이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-g-dictionary-backtrack',
        svg: svg({
            width: 760,
            height: 364,
            title: '활용된 꼴을 사전형으로 되돌리는 길',
            desc: '바깥쪽 층부터 하나씩 벗기면 사전에 실려 있는 꼴에 이른다. 벗기는 순서는 붙인 순서의 반대다.',
            body: J(
                txt(60, 32, '글에 나온 꼴', { cls: 'ink2', size: 'sm' }),
                txt(420, 32, '글에 나온 꼴', { cls: 'ink2', size: 'sm' }),
                [
                    {
                        x: 60,
                        steps: ['かりられません', 'かりられる', 'かりる'],
                        peels: ['ません 을 뗀다', 'られる 를 뗀다'],
                        tail: '사전형 · 2류',
                    },
                    {
                        x: 420,
                        steps: ['よんだり', 'よんだ', 'よむ'],
                        peels: ['たり 를 た 로 되돌린다', 'た형에서 사전형으로'],
                        tail: '사전형 · 1류',
                    },
                ].map(col => [
                    col.steps.map((s, i) => {
                        const y = 50 + i * 92;
                        const last = i === col.steps.length - 1;
                        return [
                            box(col.x, y, 280, 44, '', { stroke: last ? GREEN : GREY, sw: last ? 1.9 : 1.4 }),
                            big(col.x + 140, y + 29, s, { anchor: 'middle', size: 17 }),
                            last ? '' : px(col.x + 40, y + 46, col.x + 40, y + 88, { cls: 'ax', marker: 'ark', width: 1.4 }),
                            last ? '' : txt(col.x + 56, y + 72, col.peels[i], { cls: 'ink2', size: 'sm' }),
                        ].join('');
                    }).join(''),
                    txt(col.x + 140, 300, col.tail, { anchor: 'middle', cls: 'f3' }),
                ].join('')).join(''),
                txt(60, 332, '사전에 실려 있는 것은 사전형뿐이다. 되돌리지 않으면 찾을 수 없다', {}),
                txt(60, 356, '되돌리는 방법은 11장의 활용표를 거꾸로 읽는 것이다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
