/**
 * 9장 「조사」 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재가 조사 하나하나이므로 그림에 일본어가 많이 들어간다. 그래서
 * 예문을 전부 가나로만 적었다. 본문은 같은 예문을 한자로 적고 +jp 로 감싸므로,
 * 그림은 관계와 갈림만 보이고 표기는 본문이 맡는다.
 *
 * 여덟 장이 같은 어휘를 쓴다 — 강조색(s2)이 붙은 조사가 그 그림에서 문제가
 * 되는 자리다. 파란색(s1)은 견주는 상대편, 초록색(s3)은 한국어 쪽이다.
 */
import { svg, txt, px, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

const GREY = 'var(--ink2)';
const BLUE = 'var(--s1)';
const ORANGE = 'var(--s2)';
const GREEN = 'var(--s3)';

/**
 * 크기를 지정하는 글씨. lib.mjs 가 <style> 에 text{font-size:13px} 를 박으므로
 * 속성으로 주면 무시된다. 인라인 style 로만 바뀐다.
 */
function big(x, y, str, { size = 19, anchor = 'start', cls = 'ink', fill, weight } = {}) {
    const st = `font-size:${size}px${weight ? `;font-weight:${weight}` : ''}`;
    return `<text class="${cls}" x="${x}" y="${y}" text-anchor="${anchor}"`
        + `${fill ? ` fill="${fill}"` : ''} style="${st}">${esc(str)}</text>`;
}

/** 사각 상자 하나. lines 는 문자열 또는 문자열 배열. 라벨은 13px 한글용. */
function box(x, y, w, h, lines, o = {}) {
    const ls = (Array.isArray(lines) ? lines : [lines]).filter(t => t !== '');
    const gap = o.gap || 17;
    const top = y + h / 2 - ((ls.length - 1) * gap) / 2 + 4;
    const at = [
        `x="${x}"`, `y="${y}"`, `width="${w}"`, `height="${h}"`, `rx="${o.rx === undefined ? 6 : o.rx}"`,
        `fill="${o.fill || 'none'}"`, `stroke="${o.stroke || GREY}"`, `stroke-width="${o.sw || 1.4}"`,
    ];
    if (o.dash) at.push(`stroke-dasharray="${o.dash}"`);
    return `<rect ${at.join(' ')}/>`
        + ls.map((t, i) => txt(x + w / 2, top + i * gap, t, {
            anchor: 'middle', cls: o.cls || 'ink', size: o.size,
        })).join('');
}

/** 상자 안에 가나를 크게 하나 담는다. */
function kanaBox(x, y, w, h, kana, o = {}) {
    return box(x, y, w, h, '', o)
        + big(x + w / 2, y + h / 2 + 9, kana, { size: o.kanaSize || 26, anchor: 'middle' });
}

/**
 * 조사 한 자리를 강조색으로 그린 구. parts 의 한 항목은 [문자열, 색] 이고
 * 색이 없으면 본문색으로 그린다. tspan 을 쓰므로 글자 사이가 벌어지지 않는다.
 */
function phrase(x, y, parts, { size = 18, anchor = 'start' } = {}) {
    const inner = parts.map(([t, c]) => (c
        ? `<tspan fill="${c}" font-weight="700">${esc(t)}</tspan>`
        : esc(t))).join('');
    return `<text class="ink" x="${x}" y="${y}" text-anchor="${anchor}" style="font-size:${size}px">${inner}</text>`;
}

/** 같은 간격으로 늘어놓는 가나. */
function spread(x, y, items, step, o = {}) {
    return items.map((t, i) => big(x + i * step, y, t, o)).join('');
}

const sm = { cls: 'ink2', size: 'sm' };
const J = (...parts) => jpGroup(parts.flat().join(''));

/** 조사의 두 갈래. 자리가 둘이라는 것이 요점이다. */
function particleKinds() {
    return svg({
        width: 740,
        height: 342,
        title: '조사가 들어가는 두 자리',
        desc: '명사와 술어 사이에 격조사 자리와 부조사 자리가 차례로 있고, 각 자리에 들어가는 조사 목록이 아래에 있다.',
        body: J(
            txt(40, 34, '조사가 들어가는 자리', sm),
            box(40, 46, 120, 44, '명사'),
            box(172, 46, 120, 44, '격조사', { stroke: BLUE, sw: 1.8 }),
            box(304, 46, 120, 44, '부조사', { stroke: ORANGE, sw: 1.8, dash: '5 4' }),
            box(436, 46, 120, 44, '술어'),
            txt(364, 108, '없어도 된다', { anchor: 'middle', ...sm }),
            box(40, 138, 330, 162, '', { stroke: BLUE }),
            txt(60, 164, '격조사 — 관계를 정한다'),
            txt(60, 184, '갈아 끼우면 명사와 술어의 관계가 바뀐다', sm),
            spread(64, 232, ['が', 'を', 'に', 'で', 'へ'], 58, { size: 22 }),
            spread(64, 276, ['と', 'から', 'まで', 'より'], 78, { size: 22 }),
            box(396, 138, 316, 162, '', { stroke: ORANGE, dash: '5 4' }),
            txt(416, 164, '부조사 — 뜻을 더한다'),
            txt(416, 184, '갈아 끼워도 관계는 그대로다', sm),
            spread(420, 232, ['も', 'だけ'], 78, { size: 22 }),
            spread(420, 276, ['しか', 'ばかり'], 96, { size: 22 }),
            txt(40, 330, '문장 끝에 붙는 갈래가 따로 있고 이 장은 다루지 않는다', sm),
        ),
    });
}

/** に 와 で 의 용법 갈래를 마주 보게 늘어놓는다. 진한 테두리가 부딪히는 자리다. */
function niDeUsemap() {
    // 앞쪽 항목이 두 조사가 부딪히는 자리다 — に 는 셋, で 는 둘.
    const left = ['있는 곳', '도착점', '시각', '상대', '목적', '변화의 결과'];
    const right = ['동작이 일어나는 곳', '걸리는 시간', '수단 · 도구', '원인', '재료', '범위', '상태'];
    const y0 = 30;
    const step = 46;
    const mid = (list) => y0 + ((list.length - 1) * step) / 2 + 18;
    const cyL = mid(left);
    const cyR = mid(right);
    const parts = [
        txt(40, 22, '진한 테두리가 두 조사가 부딪히는 자리다', sm),
        kanaBox(40, cyL - 24, 80, 48, 'に', { stroke: BLUE, sw: 1.8 }),
        kanaBox(660, cyR - 24, 80, 48, 'で', { stroke: ORANGE, sw: 1.8 }),
    ];
    left.forEach((t, i) => {
        const y = y0 + i * step;
        const on = i < 3;
        parts.push(box(152, y, 194, 36, t, { stroke: on ? BLUE : GREY, sw: on ? 2 : 1.2 }));
        parts.push(px(148, y + 18, 124, cyL, { cls: 's1', marker: 'ar1', width: 1.4 }));
    });
    right.forEach((t, i) => {
        const y = y0 + i * step;
        const on = i < 2;
        parts.push(box(394, y, 194, 36, t, { stroke: on ? ORANGE : GREY, sw: on ? 2 : 1.2 }));
        parts.push(px(592, y + 18, 656, cyR, { cls: 's2', marker: 'ar2', width: 1.4 }));
    });
    parts.push(txt(152, y0 + 7 * step + 6, '나머지 갈래는 한쪽에만 있으므로 고를 일이 없다', sm));
    return svg({
        width: 760,
        height: y0 + 7 * step + 22,
        title: 'に 와 で 의 용법 갈래',
        desc: 'に 는 여섯 갈래, で 는 일곱 갈래를 가지고, 그중 진한 테두리를 두른 앞쪽 갈래에서만 서로 부딪힌다.',
        body: J(parts),
    });
}

/** 한국어 한 갈래가 술어에 따라 두 갈래로 갈린다. */
function niDePlace() {
    return svg({
        width: 800,
        height: 300,
        title: '한국어 한 갈래가 두 갈래로 갈리는 자리',
        desc: '왼쪽 한국어 조사 하나가 술어를 묻는 갈림을 지나 に 와 で 로 갈라진다.',
        body: J(
            txt(40, 32, '같은 한국어 조사가 술어에 따라 두 갈래로 갈린다', sm),
            box(40, 120, 130, 64, ['한국어', '에 · 에서'], { stroke: GREEN, sw: 1.8 }),
            px(174, 152, 224, 152, { cls: 's3', marker: 'ar3' }),
            box(230, 106, 190, 92, ['술어가 있음 · 없음 ·', '도착을 나타내는가'], { sw: 1.6 }),
            px(424, 132, 474, 92, { cls: 's1', marker: 'ar1' }),
            txt(438, 108, '그렇다', sm),
            px(424, 172, 474, 218, { cls: 's2', marker: 'ar2' }),
            txt(432, 212, '아니다', sm),
            box(480, 46, 300, 96, '', { stroke: BLUE, sw: 1.8 }),
            big(500, 82, 'に', { size: 26 }),
            txt(538, 78, '있는 곳 · 도착점'),
            phrase(500, 118, [['きょうしつ', ''], ['に', ORANGE], ['います', '']], { size: 17 }),
            box(480, 182, 300, 96, '', { stroke: ORANGE, sw: 1.8 }),
            big(500, 218, 'で', { size: 26 }),
            txt(538, 214, '동작이 일어나는 곳'),
            phrase(500, 254, [['きょうしつ', ''], ['で', ORANGE], ['べんきょうします', '']], { size: 17 }),
        ),
    });
}

/** に 와 へ — 겹치는 칸은 하나뿐이다. */
function niHe() {
    return svg({
        width: 780,
        height: 306,
        title: 'に 와 へ 가 겹치는 범위',
        desc: '가운데 칸에서만 두 조사를 바꿔 쓸 수 있고 양쪽 칸은 한쪽만 된다.',
        body: J(
            txt(40, 32, '세 칸으로 나뉜다', sm),
            box(40, 46, 226, 232, '', { stroke: BLUE, sw: 1.8 }),
            big(56, 78, 'に', { size: 24 }),
            txt(88, 74, '만 되는 자리'),
            txt(56, 116, '있는 곳'),
            txt(56, 140, '상대'),
            txt(56, 164, '시각'),
            txt(56, 188, '변화의 결과'),
            phrase(56, 226, [['とうきょう', ''], ['に', ORANGE], ['います', '']], { size: 16 }),
            txt(56, 252, '방향만 남으면 있다는 뜻이 사라진다', sm),
            box(278, 46, 226, 232, '', { stroke: GREEN, sw: 1.8 }),
            txt(294, 74, '둘 다 되는 자리'),
            txt(294, 116, '이동 동사의 도착점'),
            phrase(294, 156, [['とうきょう', ''], ['に', ORANGE], ['いきます', '']], { size: 16 }),
            phrase(294, 186, [['とうきょう', ''], ['へ', ORANGE], ['いきます', '']], { size: 16 }),
            txt(294, 226, '무게만 다르다', sm),
            txt(294, 248, '앞은 닿는 지점, 뒤는 향하는 방향', sm),
            box(516, 46, 226, 232, '', { stroke: ORANGE, sw: 1.8 }),
            big(532, 78, 'へ', { size: 24 }),
            txt(564, 74, '만 되는 자리'),
            txt(532, 116, '명사를 꾸미는 자리'),
            phrase(532, 156, [['はは', ''], ['への', ORANGE], ['てがみ', '']], { size: 16 }),
            txt(532, 194, '어머니에게 보내는 편지', sm),
            txt(532, 226, '앞의 조사는 이 자리에', sm),
            txt(532, 246, '들어가지 못한다', sm),
        ),
    });
}

/** 한국어는 넷이 같은 조사인데 일본어는 첫 줄만 다르다. */
function gaNotWo() {
    const rows = [
        ['공부한다', [['にほんご', ''], ['を', BLUE], ['べんきょうします', '']], '동작'],
        ['좋아한다', [['にほんご', ''], ['が', ORANGE], ['すきです', '']], '상태'],
        ['안다', [['にほんご', ''], ['が', ORANGE], ['わかります', '']], '상태'],
        ['할 수 있다', [['にほんご', ''], ['が', ORANGE], ['できます', '']], '상태'],
    ];
    const parts = [
        txt(40, 32, '왼쪽 네 줄은 한국어에서 모두 같은 조사다', sm),
        txt(40, 62, '한국어'),
        txt(330, 62, '일본어'),
        txt(690, 62, '술어'),
        `<path class="gr" d="M40 72 H744"/>`,
    ];
    rows.forEach(([ko, jp, kind], i) => {
        const y = 104 + i * 44;
        parts.push(phrase(40, y, [['일본어', ''], ['를 ', GREEN], [ko, '']], { size: 17 }));
        parts.push(phrase(330, y, jp, { size: 17 }));
        parts.push(txt(690, y - 3, kind));
    });
    parts.push(`<path class="gr" stroke-dasharray="4 3" d="M320 84 V292"/>`);
    parts.push(txt(40, 314, '술어가 동작인 첫 줄만 다른 조사를 받는다', sm));
    return svg({
        width: 760,
        height: 330,
        title: '한국어 을 · 를 자리에 が 가 오는 곳',
        desc: '한국어 네 줄이 같은 조사를 쓰는데 일본어에서는 첫 줄만 を 이고 나머지 셋은 が 다.',
        body: J(parts),
    });
}

/** を 가 대상을 표시하지 않는 두 자리, 그리고 で 와의 대조. */
function woPath() {
    const panel = (x, title) => box(x, 50, 232, 118, '', { stroke: GREY, sw: 1.2, dash: '5 4' })
        + txt(x + 12, 44, title, sm);
    return svg({
        width: 780,
        height: 250,
        title: 'を 가 표시하는 경로와 기점, 그리고 で 와의 대조',
        desc: '왼쪽은 선을 따라가는 움직임, 가운데는 영역을 벗어나는 움직임, 오른쪽은 영역 안에 머무는 동작이다.',
        body: J(
            panel(40, '경로 — 선을 따라간다'),
            `<path class="gr" d="M60 130 H252"/>`,
            px(70, 108, 244, 108, { cls: 's2', marker: 'ar2' }),
            txt(60, 152, '길 위를 지나간다', sm),
            phrase(40, 196, [['みち', ''], ['を', ORANGE], ['あるきます', '']], { size: 18 }),
            txt(40, 222, '길을 걷습니다', sm),
            panel(290, '기점 — 영역을 벗어난다'),
            box(306, 84, 62, 60, '', { stroke: GREY, sw: 1.4 }),
            txt(337, 158, '집', { anchor: 'middle', ...sm }),
            px(376, 114, 500, 114, { cls: 's2', marker: 'ar2' }),
            phrase(290, 196, [['いえ', ''], ['を', ORANGE], ['でます', '']], { size: 18 }),
            txt(290, 222, '집을 나섭니다', sm),
            panel(540, '장소 — 영역 안에 머문다'),
            box(576, 82, 160, 66, '', { stroke: BLUE, sw: 1.4, rx: 30, dash: '5 4' }),
            `<circle class="f1" cx="656" cy="115" r="5"/>`,
            txt(656, 158, '공원 안', { anchor: 'middle', ...sm }),
            phrase(540, 196, [['こうえん', ''], ['で', BLUE], ['あいます', '']], { size: 18 }),
            txt(540, 222, '공원에서 만납니다', sm),
        ),
    });
}

/** 겹칠 때의 순서. 앞자리가 비워지는 것과 뒤집히지 않는 것. */
function stacking() {
    const slot = (x, y, w, kana, o = {}) => kanaBox(x, y, w, 46, kana, { kanaSize: 20, ...o });
    const rowY = [76, 152, 228];
    return svg({
        width: 760,
        height: 306,
        title: '조사가 겹치는 순서',
        desc: '격조사 자리가 앞, 부조사 자리가 뒤이고, 앞자리가 が 나 を 이면 그 자리가 비워진다.',
        body: J(
            txt(40, 32, '붙는 순서가 고정되어 있다', sm),
            txt(214, 62, '격조사 자리', { anchor: 'middle', ...sm }),
            txt(292, 62, '부조사 자리', { anchor: 'middle', ...sm }),
            slot(40, rowY[0], 130, 'とうきょう', { kanaSize: 17 }),
            slot(178, rowY[0], 72, 'に', { stroke: BLUE, sw: 1.8 }),
            slot(258, rowY[0], 72, 'も', { stroke: ORANGE, sw: 1.8 }),
            px(340, rowY[0] + 23, 386, rowY[0] + 23, { cls: 'ax', marker: 'ark', width: 1.6 }),
            phrase(400, rowY[0] + 30, [['とうきょう', ''], ['にも', ORANGE]], { size: 20 }),
            txt(600, rowY[0] + 27, '도쿄에도'),
            slot(40, rowY[1], 130, 'ほん', { kanaSize: 20 }),
            slot(178, rowY[1], 72, 'を', { stroke: BLUE, sw: 1.8, dash: '5 4' }),
            big(214, rowY[1] + 34, '×', { size: 30, anchor: 'middle', fill: ORANGE }),
            slot(258, rowY[1], 72, 'も', { stroke: ORANGE, sw: 1.8 }),
            px(340, rowY[1] + 23, 386, rowY[1] + 23, { cls: 'ax', marker: 'ark', width: 1.6 }),
            phrase(400, rowY[1] + 30, [['ほん', ''], ['も', ORANGE]], { size: 20 }),
            txt(600, rowY[1] + 20, '앞자리가 비워진다'),
            txt(600, rowY[1] + 38, '주격 · 대격 조사만 그렇다', sm),
            slot(40, rowY[2], 130, 'とうきょう', { kanaSize: 17 }),
            slot(178, rowY[2], 72, 'も', { stroke: ORANGE, sw: 1.8 }),
            slot(258, rowY[2], 72, 'に', { stroke: BLUE, sw: 1.8 }),
            big(340, rowY[2] + 34, '×', { size: 30, fill: ORANGE }),
            txt(386, rowY[2] + 27, '이 순서로는 붙지 않는다'),
            txt(40, 296, '가운데 두 자리의 순서가 바뀌는 일이 없다', sm),
        ),
    });
}

/** 생략의 조건 — 관계가 하나로 정해지는가. */
function omission() {
    return svg({
        width: 780,
        height: 248,
        title: '조사가 생략되는 조건',
        desc: '조사를 빼도 명사와 술어의 관계가 하나로 정해지는 줄에서만 생략이 일어난다.',
        body: J(
            txt(40, 32, '생략이 되는 자리와 안 되는 자리', sm),
            txt(48, 62, '넣은 형태'),
            txt(334, 62, '뺀 형태'),
            txt(614, 62, '판정'),
            `<path class="gr" d="M40 72 H756"/>`,
            phrase(48, 112, [['じかん', ''], ['が', ORANGE], ['あります', '']], { size: 17 }),
            px(288, 106, 322, 106, { cls: 'ax', marker: 'ark', width: 1.6 }),
            phrase(334, 112, [['じかん', ''], ['あります', '']], { size: 17 }),
            txt(614, 104, '뜻이 복원된다'),
            txt(614, 124, '관계가 하나로 정해진다', sm),
            `<path class="gr" stroke-dasharray="4 3" d="M40 148 H756"/>`,
            phrase(48, 190, [['がっこう', ''], ['で', BLUE], ['べんきょうします', '']], { size: 17 }),
            px(288, 184, 322, 184, { cls: 'ax', marker: 'ark', width: 1.6 }),
            phrase(334, 190, [['がっこう', ''], ['べんきょうします', '']], { size: 17 }),
            big(596, 196, '×', { size: 24, fill: ORANGE }),
            txt(614, 182, '말이 되지 않는다'),
            txt(614, 202, '관계를 정해 줄 것이 없다', sm),
            txt(40, 238, '어느 조사인지가 아니라 관계가 정해지는지가 갈림의 기준이다', sm),
        ),
    });
}

export default [
    { name: 'jp-p-particle-kinds', svg: particleKinds() },
    { name: 'jp-p-ni-de-usemap', svg: niDeUsemap() },
    { name: 'jp-p-ni-de-place', svg: niDePlace() },
    { name: 'jp-p-ni-he', svg: niHe() },
    { name: 'jp-p-ga-not-wo', svg: gaNotWo() },
    { name: 'jp-p-wo-path', svg: woPath() },
    { name: 'jp-p-stacking', svg: stacking() },
    { name: 'jp-p-omission', svg: omission() },
];
