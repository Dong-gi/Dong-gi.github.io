/**
 * 4장 「가나를 읽는다」 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다.
 *
 * 이 장은 글자 모양 자체가 내용이라 가나를 크게 그리는 그림이 많다. 가나에는
 * 지역별 자형 치환이 없으므로(원장 §5.1) lang 이 닿지 않는 SVG 안에서도
 * 옳은 글자가 나온다. 다만 획의 모양은 폰트에 따라 달라지므로, 획의 방향을
 * 보여야 하는 그림은 폰트가 그린 글자 옆에 획을 선으로 따로 그린다.
 */
import { svg, px, txt, legend, esc, n } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

/**
 * 가나 글자. 크기를 바꿔야 해서 raw text 로 쓰고 JP 폰트 스택으로 감싼다.
 * 크기는 style 로 준다 — lib.mjs 의 <style> 에 `text{font-size:13px}` 이 있어서
 * font-size 를 속성으로 주면 선택자 쪽이 이겨 전부 13px 로 그려진다.
 */
const kana = (x, y, s, { size = 24, cls = 'ink', anchor = 'middle', weight, opacity } = {}) =>
    jpGroup(`<text class="${cls}" x="${n(x)}" y="${n(y)}" text-anchor="${anchor}"`
        + ` style="font-size:${size}px${weight ? `;font-weight:${weight}` : ''}"`
        + (opacity ? ` opacity="${opacity}"` : '')
        + `>${esc(s)}</text>`);

const box = (x, y, w, h, { fill, fo, stroke = 'var(--grid)', sw = 1, dash, rx = 0 } = {}) =>
    `<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}" rx="${rx}"`
    + ` fill="${fill || 'none'}"` + (fo ? ` fill-opacity="${fo}"` : '')
    + ` stroke="${stroke}" stroke-width="${sw}"`
    + (dash ? ` stroke-dasharray="${dash}"` : '') + '/>';

const seg = (x1, y1, x2, y2, { stroke = 'var(--ink2)', sw = 1.5, dash } = {}) =>
    `<path fill="none" stroke="${stroke}" stroke-width="${sw}"`
    + (dash ? ` stroke-dasharray="${dash}"` : '')
    + ` d="M${n(x1)} ${n(y1)} L${n(x2)} ${n(y2)}"/>`;

/** 어디를 견주라고 가리키는 점선 동그라미. */
const ring = (cx, cy, r) =>
    `<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(r)}" fill="none"`
    + ' stroke="var(--s2)" stroke-width="1.6" stroke-dasharray="4 3"/>';

/** 가나를 오른쪽 정렬로 놓고 그 뒤에 한국어를 잇는다. 자리가 어긋나지 않게. */
const tag = (x, y, k, ko, size = 15, cls = 'ink') =>
    kana(x, y, k, { size, anchor: 'end', cls }) + txt(x + 5, y, ko, { cls: 'ink2', size: 'sm' });

// ── 1. 오십음도 격자 ────────────────────────────────────────────
const ROWS = [
    ['あ', ['あ', 'い', 'う', 'え', 'お']],
    ['か', ['か', 'き', 'く', 'け', 'こ']],
    ['さ', ['さ', 'し', 'す', 'せ', 'そ']],
    ['た', ['た', 'ち', 'つ', 'て', 'と']],
    ['な', ['な', 'に', 'ぬ', 'ね', 'の']],
    ['は', ['は', 'ひ', 'ふ', 'へ', 'ほ']],
    ['ま', ['ま', 'み', 'む', 'め', 'も']],
    ['や', ['や', 'い', 'ゆ', 'え', 'よ']],
    ['ら', ['ら', 'り', 'る', 'れ', 'ろ']],
    ['わ', ['わ', 'ゐ', 'う', 'ゑ', 'を']],
];
const DUP = new Set(['7-1', '7-3', '9-2']);          // 첫 행과 같은 글자가 놓이는 칸
const OLD = new Set(['9-1', '9-3']);                 // 현대 표기에서 쓰지 않는 글자
const IRR = new Set(['2-1', '3-1', '3-2', '5-2']);   // 규칙에서 벗어나는 칸
const ONLY = new Set(['9-4']);                       // 조사로만 쓰는 글자

function gridFigure() {
    const gx = 152, gy = 76, cw = 62, ch = 42;
    let b = txt(280, 30, '가로 한 줄 = 행(자음)  ·  세로 한 칸 줄 = 단(모음)',
        { anchor: 'middle', cls: 'ink2', size: 'sm' });
    ROWS[0][1].forEach((v, c) => { b += kana(gx + c * cw + cw / 2, 66, v, { size: 20, weight: 600 }); });
    ROWS.forEach(([name, cells], r) => {
        const y = gy + r * ch;
        b += tag(140, y + 27, name, '행', 18);
        cells.forEach((v, c) => {
            const x = gx + c * cw, k = `${r}-${c}`;
            if (IRR.has(k)) b += box(x, y, cw, ch, { fill: 'var(--s2)', fo: 0.2, stroke: 'var(--s2)', sw: 1.6 });
            else if (ONLY.has(k)) b += box(x, y, cw, ch, { fill: 'var(--s3)', fo: 0.2, stroke: 'var(--s3)', sw: 1.6 });
            else if (DUP.has(k)) b += box(x, y, cw, ch, { fill: 'var(--grid)', fo: 0.8 });
            else b += box(x, y, cw, ch);
            const faint = DUP.has(k) || OLD.has(k);
            b += kana(x + cw / 2, y + 29, v, { size: 24, cls: faint ? 'ink2' : 'ink', opacity: faint ? 0.55 : undefined });
            if (OLD.has(k)) b += seg(x + 12, y + ch / 2, x + cw - 12, y + ch / 2, { sw: 1.3 });
        });
    });
    b += kana(168, 534, 'ん', { size: 24 }) + txt(186, 534, '— 격자 어느 행에도 속하지 않는다', { cls: 'ink2', size: 'sm' });
    const key = (x, y, style, name) => {
        let o = '';
        if (style === 'irr') o += box(x, y - 10, 15, 13, { fill: 'var(--s2)', fo: 0.2, stroke: 'var(--s2)', sw: 1.6 });
        if (style === 'only') o += box(x, y - 10, 15, 13, { fill: 'var(--s3)', fo: 0.2, stroke: 'var(--s3)', sw: 1.6 });
        if (style === 'dup') o += box(x, y - 10, 15, 13, { fill: 'var(--grid)', fo: 0.8 });
        if (style === 'old') o += box(x, y - 10, 15, 13) + seg(x + 2, y - 3.5, x + 13, y - 3.5, { sw: 1.3 });
        return o + txt(x + 21, y, name, { cls: 'ink2', size: 'sm' });
    };
    b += key(40, 566, 'irr', '규칙에서 벗어나는 칸 넷');
    b += key(40, 588, 'dup', '첫 행과 같은 글자가 놓이는 칸 셋');
    b += key(300, 566, 'old', '지금 쓰지 않는 글자 둘');
    b += key(300, 588, 'only', '조사로만 쓰는 글자 하나');
    return svg({
        width: 560, height: 610,
        title: '오십음도 격자',
        desc: '자음 열 줄과 모음 다섯 칸이 만나는 격자. 새 글자가 없는 칸과 규칙에서 벗어나는 칸을 표시했다.',
        body: jpGroup(b),
    });
}

// ── 2. 격자의 한 칸은 행과 단이 만나는 자리 ──────────────────────
function productFigure() {
    const gx = 344, gy = 96, cw = 74, ch = 56;
    const rows = [['か', ['か', 'き', 'く']], ['さ', ['さ', 'し', 'す']], ['た', ['た', 'ち', 'つ']]];
    let b = txt(360, 30, '읽는 법은 낱개로 외우는 것이 아니라 자리가 정해 준다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(40, 74, '외울 것 하나 — 단 다섯', { cls: 'ink2', size: 'sm' });
    ['あ', 'い', 'う', 'え', 'お'].forEach((v, i) => { b += kana(52 + i * 34, 114, v, { size: 24 }); });
    b += txt(40, 162, '외울 것 둘 — 행 열 개', { cls: 'ink2', size: 'sm' });
    ['あ', 'か', 'さ', 'た', 'な'].forEach((v, i) => { b += kana(52 + i * 34, 200, v, { size: 24 }); });
    ['は', 'ま', 'や', 'ら', 'わ'].forEach((v, i) => { b += kana(52 + i * 34, 238, v, { size: 24 }); });
    b += txt(gx + 111, 60, '격자의 왼쪽 위 아홉 칸', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    ['あ', 'い', 'う'].forEach((v, c) => {
        b += kana(gx + c * cw + cw / 2, 88, v, { size: 21, weight: 700, cls: c === 2 ? 'f1' : 'ink' });
    });
    rows.forEach(([name, cells], r) => {
        const y = gy + r * ch;
        b += tag(gx - 26, y + 36, name, '행', 19, r === 0 ? 'f1' : 'ink');
        cells.forEach((v, c) => {
            const x = gx + c * cw, hit = r === 0 && c === 2, band = r === 0 || c === 2;
            b += box(x, y, cw, ch, hit
                ? { fill: 'var(--s1)', fo: 0.3, stroke: 'var(--s1)', sw: 2 }
                : (band ? { fill: 'var(--s1)', fo: 0.1 } : {}));
            b += kana(x + cw / 2, y + 38, v, { size: 28 });
        });
    });
    b += txt(360, 288, '색이 겹치는 칸이 그 행과 그 단이 만나는 자리다. 한 칸이 정해지려면 둘이 다 필요하다.',
        { anchor: 'middle', cls: 'ink2', size: 'sm' });
    return svg({
        width: 720, height: 320,
        title: '행과 단이 만나 한 칸이 된다',
        desc: '왼쪽에 외울 것 둘, 오른쪽에 격자의 아홉 칸. 한 칸은 행과 단이 만나는 자리다.',
        body: jpGroup(b),
    });
}

// ── 3. 점 두 개와 동그라미가 붙는 행 ─────────────────────────────
function dakutenFigure() {
    const ry = 96, rh = 36;
    const data = [
        ['あ', null, null], ['か', 'が', null], ['さ', 'ざ', null], ['た', 'だ', null],
        ['な', null, null], ['は', 'ば', 'ぱ'], ['ま', null, null], ['や', null, null],
        ['ら', null, null], ['わ', null, null],
    ];
    let b = txt(260, 30, '점 두 개는 네 행에만 붙고 동그라미는 한 행에만 붙는다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(120, 74, '행', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(280, 74, '점 두 개 (탁음)', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(420, 74, '동그라미 (반탁음)', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    data.forEach(([name, d, h], r) => {
        const y = ry + r * rh;
        if (d) b += box(28, y, 464, rh, { fill: 'var(--s1)', fo: 0.09, stroke: 'var(--grid)' });
        else b += box(28, y, 464, rh, {});
        b += tag(132, y + 25, name, '행', 19);
        b += d ? kana(280, y + 26, d, { size: 22 }) : txt(280, y + 24, '붙지 않는다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
        b += h ? kana(420, y + 26, h, { size: 22 }) : txt(420, y + 24, '—', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    });
    return svg({
        width: 520, height: 478,
        title: '점 두 개와 동그라미가 붙는 자리',
        desc: '열 행 가운데 네 행이 점 두 개를 받고, 그중 한 행만 동그라미도 받는다.',
        body: jpGroup(b),
    });
}

// ── 4. 작은 글자를 붙여 만든다 ──────────────────────────────────
function youonFigure() {
    let b = txt(360, 30, '작은 글자를 붙이면 글자 둘이 한 소리를 적는다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    // 구성
    b += box(24, 48, 320, 118, { rx: 4 });
    b += kana(66, 116, 'き', { size: 44 });
    b += txt(96, 116, '+', { cls: 'ink2' });
    b += kana(126, 122, 'ゃ', { size: 24 });
    b += px(150, 108, 190, 108, { cls: 'ax', marker: 'ark', width: 1.8 });
    b += kana(238, 116, 'きゃ', { size: 40 });
    b += seg(208, 130, 272, 130, { stroke: 'var(--s1)', sw: 2 });
    b += txt(240, 152, '글자 둘, 소리 하나', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(40, 70, '큰 글자 + 작은 글자', { cls: 'ink2', size: 'sm' });
    // 크기 대조
    b += box(368, 48, 328, 118, { rx: 4 });
    b += txt(384, 70, '크기가 다르면 다른 것이다', { cls: 'ink2', size: 'sm' });
    b += kana(452, 116, 'きや', { size: 40 });
    b += txt(452, 152, '같은 크기 — 두 소리', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += seg(532, 96, 532, 140, { dash: '4 3' });
    b += kana(612, 116, 'きゃ', { size: 40 });
    b += txt(612, 152, '작은 글자 — 한 소리', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    // 붙는 자리
    b += txt(24, 200, '붙는 자리는 한 단뿐이다 — 이 글자들 뒤', { cls: 'ink2', size: 'sm' });
    ['き', 'し', 'ち', 'に', 'ひ', 'み', 'り'].forEach((v, i) => {
        b += box(24 + i * 46, 212, 38, 44, { stroke: 'var(--s1)' });
        b += kana(43 + i * 46, 243, v, { size: 25 });
    });
    b += txt(368, 200, '점과 동그라미가 붙은 글자에도 붙는다', { cls: 'ink2', size: 'sm' });
    ['ぎ', 'じ', 'び', 'ぴ'].forEach((v, i) => {
        b += box(368 + i * 46, 212, 38, 44, { stroke: 'var(--s1)', dash: '3 3' });
        b += kana(387 + i * 46, 243, v, { size: 25 });
    });
    b += txt(24, 284, '붙일 수 있는 작은 글자는 셋뿐이다.', { cls: 'ink2', size: 'sm' });
    ['ゃ', 'ゅ', 'ょ'].forEach((v, i) => { b += kana(266 + i * 36, 286, v, { size: 24 }); });
    return svg({
        width: 720, height: 306,
        title: '작은 글자를 붙여 만드는 소리',
        desc: '큰 글자 뒤에 작은 글자를 붙인다. 붙는 자리는 한 단에 한정되고, 붙일 수 있는 작은 글자는 셋이다.',
        body: jpGroup(b),
    });
}

// ── 5. 짧은 획의 각도와 긴 획의 방향 ────────────────────────────
function directionFigure() {
    const panels = [
        [20, 46, 'ソ', '두 획', [[138, 26, 152, 54, 's2', 'ar2'], [186, 24, 148, 78, 's1', 'ar1']],
            '짧은 획은 세로에 가깝다 · 긴 획은 왼쪽 아래로'],
        [368, 46, 'ン', '두 획', [[134, 30, 166, 40, 's2', 'ar2'], [136, 46, 180, 78, 's1', 'ar1']],
            '짧은 획은 가로에 가깝다 · 긴 획은 오른쪽 아래로'],
        [20, 206, 'ツ', '세 획', [[134, 24, 139, 48, 's2', 'ar2'], [156, 24, 161, 48, 's2', 'ar2'], [190, 26, 146, 80, 's1', 'ar1']],
            '짧은 획 둘이 세로에 가깝다 · 긴 획은 왼쪽 아래로'],
        [368, 206, 'シ', '세 획', [[130, 28, 156, 34, 's2', 'ar2'], [130, 48, 156, 54, 's2', 'ar2'], [134, 60, 182, 82, 's1', 'ar1']],
            '짧은 획 둘이 가로에 가깝다 · 긴 획은 오른쪽 아래로'],
    ];
    let b = txt(360, 28, '헷갈리는 넷은 짧은 획의 각도와 긴 획이 내려가는 쪽으로 갈린다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    for (const [ox, oy, ch, cnt, strokes, note] of panels) {
        b += box(ox, oy, 332, 150, { rx: 4 });
        b += kana(ox + 52, oy + 92, ch, { size: 58 });
        b += txt(ox + 52, oy + 120, cnt, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        for (const [x1, y1, x2, y2, cls, mk] of strokes) {
            b += px(ox + x1, oy + y1, ox + x2, oy + y2, { cls, marker: mk, width: 2.2 });
        }
        b += txt(ox + 14, oy + 140, note, { cls: 'ink2', size: 'sm' });
    }
    b += legend(30, 388, [{ slot: 2, name: '짧은 획 — 각도를 본다' }]);
    b += legend(300, 388, [{ slot: 1, name: '긴 획 — 내려가는 쪽을 본다' }]);
    return svg({
        width: 720, height: 400,
        title: '짧은 획의 각도와 긴 획의 방향',
        desc: '네 글자를 폰트가 그린 모양과 획을 따로 그린 그림으로 나란히 놓았다. 짧은 획이 세로에 가까우면 긴 획은 왼쪽 아래로, 가로에 가까우면 오른쪽 아래로 간다.',
        body: jpGroup(b),
    });
}

// ── 6. 획 하나가 더 있는가 ─────────────────────────────────────
function extraStrokeFigure() {
    let b = txt(360, 30, '가르는 것은 획 하나다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    const cards = [
        [16, 52, [['さ', '가로획 하나'], ['き', '가로획 둘']], 1, '가로획을 센다'],
        [368, 52, [['は', '가로획 하나'], ['ほ', '가로획 둘']], 1, '오른쪽 부분의 가로획을 센다'],
        [16, 190, [['ク', '두 획'], ['タ', '획이 하나 더']], 2, '안쪽을 가로지르는 획이 있는지 본다'],
        [368, 190, [['ノ', '한 획'], ['メ', '가로지름'], ['ヌ', '위에 가로획']], 0, '위의 가로획과 가로지르는 획을 본다'],
    ];
    for (const [ox, oy, items, icon, note] of cards) {
        b += box(ox, oy, 336, 124, { rx: 4 });
        items.forEach(([chr, lab], i) => {
            const cx = ox + 52 + i * 94;
            b += kana(cx, oy + 60, chr, { size: 42 });
            b += txt(cx, oy + 90, lab, { anchor: 'middle', cls: 'ink2', size: 'sm' });
            if (icon === 1) {
                for (let k = 0; k <= i; k += 1) b += seg(cx + 28, oy + 40 + k * 14, cx + 54, oy + 40 + k * 14, { stroke: 'var(--s2)', sw: 2.6 });
            }
            if (icon === 2 && i === 1) b += seg(cx + 30, oy + 36, cx + 54, oy + 58, { stroke: 'var(--s2)', sw: 2.6 });
        });
        b += txt(ox + 16, oy + 114, note, { cls: 'ink2', size: 'sm' });
    }
    return svg({
        width: 720, height: 344,
        title: '획 하나가 가르는 짝',
        desc: '가로획의 개수, 안쪽을 가로지르는 획, 위의 가로획. 이 셋이 닮은 글자를 가른다.',
        body: jpGroup(b),
    });
}

// ── 7. 고리가 있는가 ───────────────────────────────────────────
function loopFigure() {
    const rows = [
        [52, [['ぬ', '끝이 고리를 만든다'], ['め', '고리 없이 끝난다']], '고리가 있으면 앞의 글자다'],
        [142, [['る', '끝이 고리를 만든다'], ['ろ', '고리 없이 끝난다']], '고리가 있으면 앞의 글자다'],
        [232, [['ね', '고리를 만든다'], ['れ', '밖으로 뻗는다'], ['わ', '안으로 말려 닫힌다']], '오른쪽 끝이 어떻게 끝나는지만 본다'],
    ];
    let b = txt(360, 30, '오른쪽 아래 끝을 견준다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    for (const [oy, items, note] of rows) {
        b += box(16, oy, 688, 82, { rx: 4 });
        items.forEach(([chr, lab], i) => {
            const cx = 66 + i * 132;
            b += kana(cx, oy + 52, chr, { size: 40 });
            b += ring(cx + 9, oy + 44, 14);
            b += txt(cx, oy + 74, lab, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        });
        b += txt(474, oy + 46, note, { cls: 'ink2', size: 'sm' });
    }
    b += txt(16, 344, '점선 동그라미 안만 견주면 된다. 나머지는 세 글자가 거의 같다.', { cls: 'ink2', size: 'sm' });
    return svg({
        width: 720, height: 360,
        title: '고리가 가르는 짝',
        desc: '닮은 글자들이 오른쪽 아래 끝에서 갈린다. 고리를 만드는가, 밖으로 뻗는가, 안으로 말려 닫히는가.',
        body: jpGroup(b),
    });
}

// ── 8. 작은 크기에서 뭉갠다 ────────────────────────────────────
function smallSizeFigure() {
    const groups = [
        [150, '점과 동그라미', ['ホ', 'ボ', 'ポ']],
        [352, '획의 방향', ['ソ', 'ン']],
        [500, '고리', ['ぬ', 'め']],
    ];
    let b = txt(320, 30, '같은 글자를 본문 크기와 후리가나 크기로 놓았다', { anchor: 'middle', cls: 'ink2', size: 'sm' });
    b += txt(126, 112, '본문 크기', { anchor: 'end', cls: 'ink2', size: 'sm' });
    b += txt(126, 172, '후리가나 크기', { anchor: 'end', cls: 'ink2', size: 'sm' });
    b += seg(136, 128, 610, 128, { stroke: 'var(--grid)', sw: 1, dash: '4 3' });
    for (const [x0, name, chars] of groups) {
        b += txt(x0 + (chars.length - 1) * 24, 62, name, { anchor: 'middle', cls: 'ink2', size: 'sm' });
        chars.forEach((c, i) => {
            b += kana(x0 + i * 48, 112, c, { size: 34 });
            b += kana(x0 + i * 48, 172, c, { size: 17 });
        });
    }
    b += txt(28, 208, '후리가나는 본문의 절반 크기로 나온다. 위에서 쉽게 갈리던 것이 아래에서 갈리지 않는다.', { cls: 'ink2', size: 'sm' });
    return svg({
        width: 640, height: 228,
        title: '작은 크기에서 갈리지 않는 것',
        desc: '점 두 개와 동그라미, 획의 방향, 고리는 크기가 절반으로 줄면 구별이 어려워진다.',
        body: jpGroup(b),
    });
}

export default [
    { name: 'jp-k-gojuon-grid', svg: gridFigure() },
    { name: 'jp-k-row-column-product', svg: productFigure() },
    { name: 'jp-k-dakuten-rows', svg: dakutenFigure() },
    { name: 'jp-k-small-kana-build', svg: youonFigure() },
    { name: 'jp-k-stroke-direction', svg: directionFigure() },
    { name: 'jp-k-extra-stroke', svg: extraStrokeFigure() },
    { name: 'jp-k-loop-pairs', svg: loopFigure() },
    { name: 'jp-k-small-size', svg: smallSizeFigure() },
];
