/**
 * 3장 「글자가 세 벌인 언어」 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 한자를 보여야 하는 자리가 이 장에 많다(문장이 세 벌로 갈리는 모습, 낱말 경계,
 * 오쿠리가나, 후리가나, 세로쓰기). 원장 §5.1 이 그림 안의 한자를 금지하므로
 * <b>한자 한 자가 놓일 자리를 채운 사각형으로 대신 그린다.</b> 가나는 지역별
 * 자형 치환이 없으므로 그대로 그린다. 사각형이 무엇인지는 그림마다 아래에
 * 한 줄로 적고, 실제 한자는 본문의 표가 보여 준다.
 */
import { svg, txt, px, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

/** 사각형. cls 로 lib.mjs 의 스타일 클래스를 고른다. */
const rect = (x, y, w, h, cls = 'ax', rx = 3) =>
    `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" class="${cls}"/>`;

/** 선. 데이터 좌표가 없는 그림이라 화소 좌표로 바로 긋는다. */
const seg = (d, cls = 'gr') => `<path class="${cls}" fill="none" d="${d}"/>`;

/** 가나·구두점 한 글자. 그림 안에서는 본문 폰트가 닿지 않으므로 JP 스택을 직접 건다. */
const jp = (x, y, s, { size = 20, cls = 'ink', anchor = 'middle' } = {}) =>
    jpGroup(`<text class="${cls}" x="${x}" y="${y}" text-anchor="${anchor}" font-size="${size}">${esc(s)}</text>`);

/** 글자 한 칸. 'b' 는 한자 한 자가 놓일 자리(사각형), 그 밖은 가나·구두점이다. */
const cell = (cx, base, ch, size) => ch === 'b'
    ? rect(cx - size * 0.55, base - size * 0.9, size * 1.1, size * 1.1, 'f1')
    : jp(cx, base, ch, { size });

/** 글자들을 가로로 늘어놓는다. */
const rowOf = (x0, base, items, { cw = 30, size = 20 } = {}) =>
    items.map((ch, i) => cell(x0 + cw * i + cw / 2, base, ch, size)).join('');

/** 글자들을 세로로 늘어놓는다. 세로쓰기 그림에 쓴다. */
const colOf = (cx, base0, items, { ch = 34, size = 22 } = {}) =>
    items.map((c, i) => cell(cx, base0 + ch * i, c, size)).join('');

const NOTE = '사각형 하나가 한자 한 자를 대신한다';

// 문장 A — ‘저는 매일 학교에 갑니다’. 한자 자리 여섯, 히라가나 다섯, 마침표 하나.
const A = ['b', 'は', 'b', 'b', 'b', 'b', 'へ', 'b', 'き', 'ま', 'す', '。'];
// 같은 문장을 히라가나만으로 적은 것.
const A_KANA = ['わ', 'た', 'し', 'は', 'ま', 'い', 'に', 'ち', 'が', 'っ',
    'こ', 'う', 'へ', 'い', 'き', 'ま', 'す', '。'];
// 문장 A 의 낱말 경계. [시작 칸, 끝 칸, 뜻]
const A_WORDS = [[0, 1, '나'], [1, 2, '는'], [2, 4, '매일'], [4, 6, '학교'],
    [6, 7, '로'], [7, 11, '갑니다'], [11, 12, '마침']];

// 문장 B — ‘야마다 씨는 편의점에서 빵을 샀습니다’. 세 벌이 다 나온다.
// 둘째 값은 어느 벌인가 — k 한자, h 히라가나, K 가타카나, p 구두점.
const B = [
    ['b', 'k'], ['b', 'k'],
    ['さ', 'h'], ['ん', 'h'], ['は', 'h'],
    ['コ', 'K'], ['ン', 'K'], ['ビ', 'K'], ['ニ', 'K'],
    ['で', 'h'],
    ['パ', 'K'], ['ン', 'K'],
    ['を', 'h'],
    ['b', 'k'],
    ['い', 'h'], ['ま', 'h'], ['し', 'h'], ['た', 'h'],
    ['。', 'p'],
];

export default [
    {
        name: 'jp-w-three-sets',
        svg: (() => {
            const x0 = 190, cw = 26, x1 = x0 + cw * B.length;
            const lanes = [
                ['k', 145, '한자 — 뜻'],
                ['h', 200, '히라가나 — 문법'],
                ['K', 255, '가타카나 — 외래어'],
            ];
            const body = [
                txt(178, 76, '쓰인 대로', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                rowOf(x0, 76, B.map(([c]) => c), { cw }),
                ...lanes.map(([set, y, name]) => [
                    seg(`M${x0} ${y + 9} H${x1}`),
                    txt(178, y, name, { anchor: 'end', cls: 'ink2', size: 'sm' }),
                    B.map(([c, s], i) => (s === set ? cell(x0 + cw * i + cw / 2, y, c, 20) : '')).join(''),
                ].join('')),
                txt(x0, 294, NOTE + '. 마침표는 어느 벌에도 넣지 않았다', { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 720, height: 310,
                title: '한 문장이 세 벌로 갈리는 모습',
                desc: '위에 쓰인 대로의 문장을 두고, 아래 세 줄에 한자·히라가나·가타카나를 각각 제 자리에만 남겨 그린 것',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-shape-contrast',
        svg: (() => {
            const pairs = [['あ', 'ア'], ['か', 'カ'], ['せ', 'セ'], ['ま', 'マ'], ['り', 'リ']];
            const cx = i => 220 + i * 90;
            const body = [
                txt(40, 46, '같은 소리를 적는 두 벌', { cls: 'ink2', size: 'sm' }),
                txt(190, 100, '히라가나', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                txt(190, 175, '가타카나', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                pairs.map(([h, k], i) => [
                    rect(cx(i) - 30, 62, 60, 52, 'gr', 5),
                    rect(cx(i) - 30, 137, 60, 52, 'gr', 5),
                    jp(cx(i), 103, h, { size: 34 }),
                    jp(cx(i), 178, k, { size: 34 }),
                ].join('')).join(''),
                txt(40, 218, '히라가나는 획이 이어지며 휘고, 가타카나는 짧은 직선이 각을 이룬다',
                    { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 700, height: 240,
                title: '히라가나와 가타카나의 모양 대조',
                desc: '위아래로 짝지은 다섯 쌍이 각각 같은 소리를 적는다. 위는 히라가나, 아래는 가타카나',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-word-boundary',
        svg: (() => {
            const x0 = 80, cw = 38;
            const at = i => x0 + cw * i;
            const body = [
                rowOf(x0, 92, A, { cw }),
                A_WORDS.map(([a]) => seg(`M${at(a)} 102 V116`, 'ax')).join(''),
                seg(`M${at(A.length)} 102 V116`, 'ax'),
                A_WORDS.map(([a, b]) => seg(`M${at(a) + 3} 132 V139 H${at(b) - 3} V132`, 'ax')).join(''),
                A_WORDS.map(([a, b, name]) =>
                    txt((at(a) + at(b)) / 2, 157, name, { anchor: 'middle', cls: 'ink2', size: 'sm' })).join(''),
                txt(x0, 196, '한자 덩어리가 시작되는 자리가 낱말의 시작이다'),
                txt(x0, 222, '히라가나로 넘어가면 그 낱말의 문법 부분이다'),
                txt(x0, 252, NOTE, { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 620, height: 270,
                title: '띄어쓰기 없이 낱말 경계가 보이는 짜임',
                desc: '띄어쓰기 없이 붙여 쓴 문장 아래에 낱말 경계를 표시하고, 경계가 한자와 히라가나가 바뀌는 자리에 놓인다는 것을 보인 것',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-kana-only',
        svg: (() => {
            const x0 = 162, cw = 28;
            const at = i => x0 + cw * i;
            const body = [
                txt(150, 84, '한자를 섞으면', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                rowOf(x0, 84, A, { cw }),
                A_WORDS.map(([a]) => seg(`M${at(a)} 94 V106`, 'ax')).join(''),
                seg(`M${at(A.length)} 94 V106`, 'ax'),
                txt(x0, 126, '끊어 읽을 자리가 글자에 드러난다', { cls: 'ink2', size: 'sm' }),
                txt(150, 190, '가나만으로 적으면', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                rowOf(x0, 190, A_KANA, { cw }),
                seg(`M${x0} 202 H${at(A_KANA.length)}`, 'ax'),
                txt(x0, 224, '같은 모양이 계속되어 끊을 자리가 없다', { cls: 'ink2', size: 'sm' }),
                txt(x0, 254, '두 줄은 같은 문장이다. ' + NOTE, { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 700, height: 272,
                title: '한자를 섞은 표기와 가나만의 표기',
                desc: '같은 문장을 두 줄로 적어, 위에는 낱말 경계 표시가 들어가고 아래에는 들어갈 자리가 없음을 보인 것',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-okurigana',
        svg: (() => {
            const line = (x0, base, items, gloss) => [
                rowOf(x0, base, items, { cw: 30 }),
                px(x0 + 30 * items.length + 8, base - 6, x0 + 30 * items.length + 46, base - 6,
                    { cls: 'ax', marker: 'ark' }),
                txt(x0 + 30 * items.length + 54, base - 1, gloss),
            ].join('');
            const body = [
                txt(50, 58, '같은 한자에 붙는 가나가 다르면', { cls: 'ink2', size: 'sm' }),
                txt(50, 76, '다른 낱말이 된다', { cls: 'ink2', size: 'sm' }),
                line(60, 128, ['b', 'く'], '가다'),
                line(60, 176, ['b', 'う'], '행하다'),
                seg('M355 46 V254'),
                txt(390, 58, '같은 낱말이라도 꼬리가 바뀌면', { cls: 'ink2', size: 'sm' }),
                txt(390, 76, '붙는 가나가 바뀐다', { cls: 'ink2', size: 'sm' }),
                line(400, 128, ['b', 'く'], '간다'),
                line(400, 176, ['b', 'き', 'ま', 'す'], '갑니다'),
                line(400, 224, ['b', 'か', 'な', 'い'], '가지 않는다'),
                txt(50, 278, NOTE + '. 그 뒤에 붙은 가나가 오쿠리가나다', { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 740, height: 296,
                title: '오쿠리가나가 하는 두 가지 일',
                desc: '왼쪽은 같은 한자에 다른 가나가 붙어 다른 낱말이 되는 것, 오른쪽은 같은 낱말의 꼬리만 바뀌는 것',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-furigana',
        svg: (() => {
            const ruby = ['と', 'う', 'き', 'ょ', 'う'];
            const body = [
                txt(50, 58, '가로쓰기 — 글자 위에 단다', { cls: 'ink2', size: 'sm' }),
                rowOf(110, 152, ['b', 'b'], { cw: 30, size: 26 }),
                ruby.map((c, i) => jp(116 + i * 12, 124, c, { size: 11 })).join(''),
                px(250, 120, 182, 120, { cls: 'ax', marker: 'ark' }),
                txt(258, 124, '후리가나', { size: 'sm' }),
                px(250, 148, 182, 148, { cls: 'ax', marker: 'ark' }),
                txt(258, 152, '본문 글자', { size: 'sm' }),
                seg('M370 46 V210'),
                txt(420, 58, '세로쓰기 — 글자 오른쪽에 단다', { cls: 'ink2', size: 'sm' }),
                colOf(470, 112, ['b', 'b'], { ch: 32, size: 26 }),
                ruby.map((c, i) => jp(500, 96 + i * 13, c, { size: 11, anchor: 'start' })).join(''),
                px(624, 122, 520, 122, { cls: 'ax', marker: 'ark' }),
                txt(632, 126, '후리가나', { size: 'sm' }),
                txt(50, 216, NOTE + '. 후리가나는 본문보다 작고, 낱말 전체에 걸쳐 붙는다',
                    { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 740, height: 240,
                title: '후리가나가 붙는 자리',
                desc: '가로쓰기에서는 한자 위에, 세로쓰기에서는 한자 오른쪽에 작은 가나가 붙는 모습',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-romaji-split',
        svg: (() => {
            const cols = [
                ['か', 'ka', 'ka'],
                ['し', 'shi', 'si'],
                ['つ', 'tsu', 'tu'],
                ['ち', 'chi', 'ti'],
                ['ふ', 'fu', 'hu'],
                ['じ', 'ji', 'zi'],
            ];
            const cx = i => 210 + i * 80;
            const body = [
                txt(40, 46, '같은 가나를 로마자로 적는 두 방식', { cls: 'ink2', size: 'sm' }),
                txt(190, 100, '가나', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                txt(190, 155, '헵번식', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                txt(190, 200, '훈령식', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                cols.map(([k], i) => jp(cx(i), 100, k, { size: 28 })).join(''),
                cols.map(([, h], i) => txt(cx(i), 155, h, { anchor: 'middle' })).join(''),
                cols.map(([, , k], i) => txt(cx(i), 200, k, { anchor: 'middle' })).join(''),
                `<rect x="255" y="130" width="395" height="88" rx="6" class="s2" fill="none" stroke-width="1.5" stroke-dasharray="5 4"/>`,
                txt(452, 240, '왼쪽 한 칸만 같고 나머지 다섯 칸은 갈린다',
                    { anchor: 'middle', cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 700, height: 262,
                title: '로마자 표기가 두 갈래로 갈리는 자리',
                desc: '가나 여섯 칸에 두 로마자 표기 체계를 나란히 적어, 여섯 칸 가운데 다섯 칸이 서로 다르게 적힌다는 것을 보인 것',
                body,
            });
        })(),
    },
    {
        name: 'jp-w-vertical',
        svg: (() => {
            const cols = [
                [497, ['b', 'は', 'b', 'b'], '①'],
                [417, ['b', 'b', 'へ', 'b'], '②'],
                [337, ['き', 'ま', 'す', '。'], '③'],
            ];
            const body = [
                px(548, 66, 316, 66, { cls: 'ax', marker: 'ark' }),
                txt(432, 52, '줄은 오른쪽에서 왼쪽으로 나아간다',
                    { anchor: 'middle', cls: 'ink2', size: 'sm' }),
                cols.map(([cx, items, no]) => [
                    colOf(cx, 114, items),
                    px(cx - 26, 96, cx - 26, 226, { cls: 'ax', marker: 'ark' }),
                    txt(cx, 262, no, { anchor: 'middle', cls: 'ink2' }),
                ].join('')).join(''),
                txt(250, 262, '읽는 순서', { anchor: 'end', cls: 'ink2', size: 'sm' }),
                txt(60, 296, NOTE + '. 앞 그림의 문장을 세로로 옮겨 적은 것이다',
                    { cls: 'ink2', size: 'sm' }),
            ].join('');
            return svg({
                width: 620, height: 320,
                title: '세로쓰기의 진행 방향',
                desc: '글자는 위에서 아래로, 줄은 오른쪽에서 왼쪽으로 나아가는 것을 화살표로 보인 것',
                body,
            });
        })(),
    },
];
