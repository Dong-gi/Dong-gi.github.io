/**
 * 15장 「주고받기」 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장의 소재가 방향이므로 그림이 거의 다 화살이다. 사람의 이름과 예문은
 * 전부 가나로 적었고, 사람을 가리키는 말(나·남·동생)은 한글로 적었다.
 * 본문은 같은 예문을 한자로 적고 +jp 로 감싸므로, 그림은 방향과 자리만 보인다.
 *
 * 색 규약 — 이 모듈 안에서 일관되게 쓴다.
 *   파랑(s1) : 안쪽으로 오는 것. 바깥에서 안으로 지나는 화살
 *   주황(s2) : 안이 아닌 쪽으로 가는 것, 그리고 막히는 자리
 *   초록(s3) : 되는 자리를 표시하는 데만 쓴다
 * 색만으로 구분하지 않도록 화살마다 이름을 함께 적는다.
 */
import { svg, txt, px, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

const GREY = 'var(--ink2)';
const GRID = 'var(--grid)';
const BLUE = 'var(--s1)';
const ORANGE = 'var(--s2)';
const GREEN = 'var(--s3)';

/**
 * 크기를 지정하는 글씨. lib.mjs 가 <style> 에 text{font-size:13px} 를 박으므로
 * 속성으로 주면 무시된다. 인라인 style 로만 바뀐다.
 */
function big(x, y, str, { size = 18, anchor = 'start', cls = 'ink', fill, weight } = {}) {
    const st = `font-size:${size}px${weight ? `;font-weight:${weight}` : ''}`;
    return `<text class="${cls}" x="${x}" y="${y}" text-anchor="${anchor}"`
        + `${fill ? ` fill="${fill}"` : ''} style="${st}">${esc(str)}</text>`;
}

/** 사각 상자 하나. lines 는 문자열 또는 문자열 배열(13px 한글용). */
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

/** 상자 안에 가나를 한 줄 크게 담는다. */
function jpBox(x, y, w, h, kana, o = {}) {
    return box(x, y, w, h, '', o)
        + big(x + w / 2, y + h / 2 + (o.kanaSize || 16) / 2 - 1, kana,
            { size: o.kanaSize || 16, anchor: 'middle', cls: o.textCls || 'ink' });
}

/** 화살표 없는 이음선. */
function link(x1, y1, x2, y2, o = {}) {
    return `<path fill="none" stroke="${o.stroke || GRID}" stroke-width="${o.sw || 1.2}"`
        + `${o.dash ? ` stroke-dasharray="${o.dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;
}

/** 안과 바깥을 가르는 세로 경계선. 이 장의 모든 그림이 같은 모양으로 그린다. */
function border(x, y1, y2) {
    return link(x, y1, x, y2, { stroke: GREY, sw: 1.6, dash: '6 5' });
}

/**
 * 주어가 어느 쪽인지 표시하는 작은 고리. above 를 주면 이름을 위에 적는다.
 * 오른쪽 끝의 고리는 옆에 오는 라벨과 부딪히므로 위에 적는다.
 */
function subjectMark(x, y, o = {}) {
    return `<circle cx="${x}" cy="${y}" r="6" fill="none" stroke="${o.stroke || GREY}" stroke-width="2"/>`
        + txt(x, y + (o.above ? -14 : 22), '주어', { anchor: 'middle', cls: 'ink2', size: 'sm' });
}

/**
 * 색을 정하지 않은 화살. lib.mjs 의 <style> 에 stroke 를 주는 클래스는
 * s1·s2·s3·ax·gr 뿐이다. ark 는 마커 id 이지 클래스가 아니므로
 * cls 로 넘기면 선이 stroke:none 으로 그려져 머리만 남는다.
 */
const greyArrow = (x1, y1, x2, y2) => px(x1, y1, x2, y2, { cls: 'ax', marker: 'ark' });

const J = (...parts) => jpGroup(parts.flat().join(''));

export default [
    {
        name: 'jp-b-three-verbs',
        svg: svg({
            width: 760,
            height: 322,
            title: '세 동사를 가르는 두 가지 — 화살의 방향과 주어의 자리',
            desc: '안과 바깥을 가르는 선 하나를 세 화살이 지난다. 지나는 방향과 주어로 세운 쪽이 세 동사를 정한다.',
            body: J(
                txt(44, 32, '안 — 나와 내 쪽', { cls: 'f1' }),
                txt(372, 32, '바깥 — 그 밖', { cls: 'ink2' }),
                border(340, 46, 274),
                // あげる — 안에서 바깥으로, 주어는 주는 쪽(안)
                px(208, 96, 472, 96, { cls: 's2', marker: 'ar2' }),
                subjectMark(194, 96, { stroke: ORANGE }),
                big(520, 102, 'あげる', { size: 19, cls: 'f2' }),
                txt(520, 124, '도착점이 안이 아니다', { cls: 'ink2', size: 'sm' }),
                // くれる — 바깥에서 안으로, 주어는 주는 쪽(바깥)
                px(472, 168, 208, 168, { cls: 's1', marker: 'ar1' }),
                subjectMark(486, 168, { stroke: BLUE, above: true }),
                big(520, 174, 'くれる', { size: 19, cls: 'f1' }),
                txt(520, 196, '도착점이 반드시 안이다', { cls: 'ink2', size: 'sm' }),
                // もらう — 바깥에서 안으로, 주어는 받는 쪽(안)
                px(472, 240, 208, 240, { cls: 's1', marker: 'ar1' }),
                subjectMark(194, 240, { stroke: BLUE }),
                big(520, 246, 'もらう', { size: 19, cls: 'f1' }),
                txt(520, 268, '도착점이 반드시 안이다', { cls: 'ink2', size: 'sm' }),
                txt(44, 302, '아래 두 줄은 화살이 같다. 갈리는 것은 고리가 어느 쪽에 붙었는가뿐이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-same-event',
        svg: svg({
            width: 760,
            height: 316,
            title: '한 사건에 두 동사가 붙는 까닭',
            desc: '물건이 옮겨 가는 화살은 하나뿐이고, 말하는 사람이 어느 쪽에 서는가에 따라 동사가 갈린다.',
            body: J(
                box(112, 36, 250, 62, '', { stroke: ORANGE }),
                txt(237, 60, '말하는 사람이 이쪽에 서면', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                big(237, 88, 'あげる', { size: 19, anchor: 'middle', cls: 'f2' }),
                link(237, 98, 237, 136, { stroke: ORANGE, dash: '4 4' }),
                jpBox(180, 136, 116, 46, 'たなか', { stroke: GREY, kanaSize: 17 }),
                jpBox(470, 136, 116, 46, 'やまだ', { stroke: GREY, kanaSize: 17 }),
                greyArrow(306, 159, 462, 159),
                txt(384, 130, '책 하나 — 사건은 하나뿐이다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                link(528, 182, 528, 220, { stroke: BLUE, dash: '4 4' }),
                box(403, 220, 250, 62, '', { stroke: BLUE }),
                txt(528, 244, '말하는 사람이 이쪽에 서면', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                big(528, 272, 'くれる', { size: 19, anchor: 'middle', cls: 'f1' }),
                txt(44, 306, '화살은 그대로다. 옮겨 간 것은 말하는 사람의 자리이고, 그것이 동사를 바꾼다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-kureru-blocked',
        svg: svg({
            width: 760,
            height: 300,
            title: '한 동사가 못 오는 두 자리',
            desc: '세 줄 모두 같은 동사를 쓰려 한 것이고, 화살 머리가 안에 있는 줄만 성립한다.',
            body: J(
                txt(44, 32, '세 줄 모두 くれる 를 쓰려 한 자리다', {}),
                txt(322, 60, '안', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(560, 60, '바깥', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                border(430, 70, 250),
                // 되는 자리 — 바깥에서 안으로
                txt(44, 104, '바깥에서 안으로', { cls: 'ink2', size: 'sm' }),
                big(196, 110, '○', { size: 19, cls: 'f3' }),
                px(700, 100, 262, 100, { cls: 's3', marker: 'ar3' }),
                txt(262, 84, '도착점', { anchor: 'middle', cls: 'f3', size: 'sm' }),
                // 남이 남에게
                txt(44, 162, '바깥에서 바깥으로', { cls: 'ink2', size: 'sm' }),
                big(196, 168, '×', { size: 19, cls: 'f2' }),
                px(700, 158, 480, 158, { cls: 's2', marker: 'ar2' }),
                txt(480, 142, '도착점', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                // 내가 남에게
                txt(44, 220, '안에서 바깥으로', { cls: 'ink2', size: 'sm' }),
                big(196, 226, '×', { size: 19, cls: 'f2' }),
                px(262, 216, 700, 216, { cls: 's2', marker: 'ar2' }),
                txt(700, 200, '도착점', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                txt(44, 272, '막힌 두 줄의 공통점은 하나다 — 화살 머리가 선의 바깥쪽에 있다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-uchi-soto-line',
        svg: svg({
            width: 760,
            height: 296,
            title: '경계가 움직인다 — 같은 사람이 두 문장에서 다른 쪽에 놓인다',
            desc: '가운데 사람은 그대로이고, 문장에 함께 나온 사람이 달라지면서 경계선이 옮겨 간다.',
            body: J(
                // 위 줄 — 나와 동생
                txt(240, 52, '안', { anchor: 'end', cls: 'f1', size: 'sm' }),
                txt(262, 52, '바깥', { cls: 'ink2', size: 'sm' }),
                border(250, 58, 132),
                box(126, 68, 88, 46, '나'),
                box(288, 68, 88, 46, '동생', { stroke: ORANGE, cls: 'f2' }),
                big(410, 92, 'わたしは おとうとに あげました', { size: 15 }),
                txt(410, 114, '동생이 바깥이다', { cls: 'f2', size: 'sm' }),
                link(40, 148, 720, 148, { stroke: GRID, dash: '4 4' }),
                px(252, 148, 342, 148, { cls: 's2', marker: 'ar2', width: 1.6 }),
                txt(358, 142, '경계가 이만큼 옮겨 간다', { cls: 'f2', size: 'sm' }),
                // 아래 줄 — 나와 동생, 그리고 친구
                txt(338, 186, '안', { anchor: 'end', cls: 'f1', size: 'sm' }),
                txt(360, 186, '바깥', { cls: 'ink2', size: 'sm' }),
                border(348, 192, 266),
                box(126, 202, 88, 46, '나'),
                box(226, 202, 88, 46, '동생', { stroke: ORANGE, cls: 'f2' }),
                box(384, 202, 88, 46, '친구'),
                big(500, 226, 'ともだちが おとうとに くれました', { size: 14 }),
                txt(500, 248, '동생이 안이다', { cls: 'f1', size: 'sm' }),
                txt(44, 284, '주황 상자는 두 줄에서 같은 사람이다. 선을 옮긴 것은 함께 나온 사람이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-particles',
        svg: svg({
            width: 760,
            height: 322,
            title: '세 조사의 자리와 그 자리에 오는 사람',
            desc: '조사 세 개의 순서는 두 줄에서 같은데, 주는 쪽과 받는 쪽이 자리를 맞바꾼다.',
            body: J(
                txt(44, 34, '자리의 순서는 두 줄이 같다. 바뀌는 것은 그 자리에 오는 사람이다', {}),
                // 윗줄
                box(56, 76, 150, 44, '주는 쪽', { stroke: BLUE, cls: 'f1' }),
                big(216, 105, 'が', { size: 17, cls: 'ink2' }),
                box(248, 76, 150, 44, '받는 쪽', { stroke: BLUE, cls: 'f1' }),
                big(408, 105, 'に', { size: 17, cls: 'ink2' }),
                box(438, 76, 106, 44, '물건'),
                big(554, 105, 'を', { size: 17, cls: 'ink2' }),
                big(586, 105, 'あげる · くれる', { size: 15, cls: 'f1' }),
                // 아랫줄
                box(56, 184, 150, 44, '받는 쪽', { stroke: ORANGE, cls: 'f2' }),
                big(216, 213, 'が', { size: 17, cls: 'ink2' }),
                box(248, 184, 150, 44, '주는 쪽', { stroke: ORANGE, cls: 'f2' }),
                big(408, 213, 'に', { size: 17, cls: 'ink2' }),
                box(438, 184, 106, 44, '물건'),
                big(554, 213, 'を', { size: 17, cls: 'ink2' }),
                big(586, 213, 'もらう', { size: 15, cls: 'f2' }),
                link(408, 232, 408, 242, { stroke: GREY, sw: 1 }),
                txt(408, 256, 'から 도 이 자리에 온다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                // 맞바뀌는 것을 엇갈린 점선으로
                link(131, 120, 323, 184, { stroke: ORANGE, dash: '5 4' }),
                link(323, 120, 131, 184, { stroke: ORANGE, dash: '5 4' }),
                txt(340, 158, '두 자리가 맞바뀐다', { cls: 'f2', size: 'sm' }),
                txt(44, 284, 'に 가 붙은 사람이 주는 쪽인지 받는 쪽인지는 동사가 정한다', {}),
                txt(44, 306, '물건에 붙는 を 는 세 동사에서 그대로다', { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-te-benefit',
        svg: svg({
            width: 760,
            height: 320,
            title: '옮겨 가는 것이 물건에서 이득으로 바뀐다',
            desc: '위와 아래가 같은 모양이다. 선을 지나는 방식은 그대로이고 옮겨 가는 것만 다르다.',
            body: J(
                txt(300, 40, '안', { anchor: 'middle', cls: 'f1' }),
                txt(560, 40, '바깥', { anchor: 'middle', cls: 'ink2' }),
                border(420, 52, 278),
                // 물건이 옮겨 가는 쪽
                txt(44, 100, '물건이', { cls: 'ink2', size: 'sm' }),
                txt(44, 118, '옮겨 간다', { cls: 'ink2', size: 'sm' }),
                px(280, 96, 600, 96, { cls: 's2', marker: 'ar2' }),
                big(620, 102, 'あげる', { size: 16, cls: 'f2' }),
                px(600, 138, 280, 138, { cls: 's1', marker: 'ar1' }),
                big(620, 144, 'くれる', { size: 16, cls: 'f1' }),
                link(40, 172, 720, 172, { stroke: GRID, dash: '4 4' }),
                // 이득이 옮겨 가는 쪽
                txt(44, 210, '이득이', { cls: 'ink2', size: 'sm' }),
                txt(44, 228, '옮겨 간다', { cls: 'ink2', size: 'sm' }),
                px(280, 206, 600, 206, { cls: 's2', marker: 'ar2' }),
                big(620, 212, '〜てあげる', { size: 16, cls: 'f2' }),
                px(600, 248, 280, 248, { cls: 's1', marker: 'ar1' }),
                big(620, 254, '〜てくれる', { size: 16, cls: 'f1' }),
                txt(44, 300, '한국어의 -어 주다 는 이 두 방향을 한 꼴로 적는다. 방향은 조사가 나른다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
    {
        name: 'jp-b-mark-or-not',
        svg: svg({
            width: 760,
            height: 268,
            title: '은혜의 방향을 담는 자리가 비는 것',
            desc: '두 문장을 같은 칸으로 잘라 놓았다. 아래 문장은 셋째 칸이 비어 있고 그래도 문장이 성립한다.',
            body: J(
                txt(131, 48, '주어', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(311, 48, '뜻을 지는 부분', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(482, 48, '은혜의 방향', { anchor: 'middle', cls: 'f2', size: 'sm' }),
                txt(633, 48, '정중함', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                // 표시한 문장
                jpBox(56, 62, 150, 46, 'ともだちが'),
                jpBox(226, 62, 170, 46, 'てつだって'),
                jpBox(412, 62, 140, 46, 'くれ', { stroke: ORANGE, textCls: 'f2' }),
                jpBox(568, 62, 130, 46, 'ました'),
                txt(56, 130, '도움이 나에게 왔다는 것이 칸 하나로 적혀 있다', { cls: 'ink2', size: 'sm' }),
                // 표시하지 않은 문장
                jpBox(56, 150, 150, 46, 'ともだちが'),
                jpBox(226, 150, 170, 46, 'てつだい'),
                box(412, 150, 140, 46, '비어 있다', { stroke: ORANGE, dash: '5 4', cls: 'f2', size: 'sm' }),
                jpBox(568, 150, 130, 46, 'ました'),
                txt(56, 218, '문장이 깨지지 않는다. 그래서 빈 칸을 알아채기 어렵다', { cls: 'ink2', size: 'sm' }),
                txt(56, 248, '칸을 비워 두는 것과 그 칸이 없는 것은 다르다', {}),
            ),
        }),
    },
    {
        name: 'jp-b-request-ladder',
        svg: svg({
            width: 760,
            height: 306,
            title: '부탁하는 네 꼴 — 걸린 조작의 수',
            desc: '왼쪽에서 오른쪽으로 갈수록 조작이 하나씩 더 걸린다. 조작의 수가 부드러움의 정도와 함께 간다.',
            body: J(
                txt(40, 34, '같은 동작을 부탁하는 네 꼴. 위에 적은 것이 그 꼴에 새로 걸린 조작이다', {}),
                // 새로 걸리는 조작
                txt(101, 108, '조작 없음', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(264, 90, '은혜의 방향', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(264, 108, '물음', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(450, 108, '받는 쪽으로', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(647, 90, '부정 물음', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                txt(647, 108, '정중한 꼴', { anchor: 'middle', cls: 'f1', size: 'sm' }),
                // 네 꼴
                jpBox(36, 126, 130, 52, 'まって。', { kanaSize: 15 }),
                jpBox(182, 126, 164, 52, 'まってくれる？', { kanaSize: 15 }),
                jpBox(362, 126, 176, 52, 'まってもらえる？', { kanaSize: 15 }),
                jpBox(554, 126, 186, 52, 'まってもらえませんか。', { kanaSize: 14 }),
                // 겹친 조작의 수
                txt(101, 200, '조작 0', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(264, 200, '조작 2', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(450, 200, '조작 3', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(647, 200, '조작 5', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                greyArrow(40, 226, 730, 226),
                txt(385, 250, '조작이 겹칠수록 부드러워진다', { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                txt(40, 288, '조작이 적은 꼴이 틀린 꼴은 아니다 — 가까운 사이에서 쓰는 짧은 꼴이다',
                    { cls: 'ink2', size: 'sm' }),
            ),
        }),
    },
];
