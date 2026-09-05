/**
 * 5장 「소리의 단위 — 음절이 아니라 박」 그림.
 *
 * 원장 §5.1 을 지킨다 — 그림 안에 한자를 넣지 않는다. 여기 쓰는 글자는
 * 가나·한글·숫자뿐이다. 가나에는 지역별 자형 치환이 없으므로 lang 이 닿지
 * 않는 SVG 안에서도 틀린 글자가 나오지 않는다.
 *
 * 그림 여덟 장이 같은 어휘를 쓴다 — 박 하나가 칸 하나다. 강조색(s2) 칸은
 * 한국어 화자가 앞 칸에 붙여 읽어 잃어버리는 자리다.
 *
 * 화폭은 내용에 맞춰 좁게 잡는다. SVG 는 <img> 로 실려 제 크기로 그려지므로,
 * 화폭을 넓게 잡으면 칸이 작아 보이는 대신 오른쪽이 통째로 빈다.
 * 대신 칸 크기를 여덟 장에서 똑같이 두어 그림끼리 견줄 수 있게 한다.
 */
import { svg, txt, px, esc } from './lib.mjs';
import { jpGroup } from './japanese-font.mjs';

/** 박 칸 하나의 크기와 간격. 칸 하나가 차지하는 폭은 CW + GAP 이다. */
const CW = 56;
const CH = 52;
const GAP = 8;
const SIZE = 28;
/** 줄 하나가 차지하는 높이(칸 + 줄 사이). */
const RH = CH + 12;

/**
 * 박 칸을 한 줄로 그린다. items 의 한 항목이 한 칸이다.
 * 두 글자짜리 항목(요음이 붙은 칸)은 글자를 줄여 한 칸에 담는다.
 * mark 에 든 자리는 강조색으로 그린다.
 */
function row(x, y, items, { cw = CW, ch = CH, gap = GAP, size = SIZE, mark = [] } = {}) {
    const out = [];
    items.forEach((t, i) => {
        const bx = x + i * (cw + gap);
        const on = mark.includes(i);
        out.push(`<rect x="${bx}" y="${y}" width="${cw}" height="${ch}" rx="7" class="${on ? 's2' : 's1'}" fill="none" stroke-width="${on ? 2.6 : 1.6}"/>`);
        if (on) out.push(`<rect x="${bx}" y="${y}" width="${cw}" height="${ch}" rx="7" class="f2" fill-opacity="0.12" stroke="none"/>`);
        out.push(`<text class="ink" x="${bx + cw / 2}" y="${y + ch * 0.68}" text-anchor="middle" font-size="${t.length > 1 ? Math.round(size * 0.68) : size}">${esc(t)}</text>`);
    });
    return out.join('');
}

/** 칸 줄의 오른쪽 끝 x. 라벨을 그 뒤에 붙일 때 쓴다. */
const rowEnd = (x, len, cw = CW, gap = GAP) => x + len * (cw + gap) - gap;

/** 칸 몇 개를 아래에서 묶는 꺾쇠와 그 이름. */
function brace(x1, x2, y, label) {
    return `<path class="ax" fill="none" d="M${x1} ${y} v6 H${x2} v-6"/>`
        + txt((x1 + x2) / 2, y + 24, label, { anchor: 'middle', cls: 'ink2', size: 'sm' });
}

/** 작은 설명 글씨. */
const sm = { cls: 'ink2', size: 'sm' };
/** 줄 오른쪽에 붙이는 박 수. 줄의 세로 가운데에 맞춘다. */
const count = (x, y, label) => txt(x, y + CH / 2 + 5, label, { cls: 'ink' });

/** 박 넷과 음절 둘이 같은 낱말에서 어긋난다. */
function moraVsSyllable() {
    const b = [];
    b.push(txt(40, 32, '일본어 — 가나 한 글자가 한 칸', sm));
    b.push(row(40, 44, ['が', 'っ', 'こ', 'う']));
    for (let i = 0; i < 4; i += 1) {
        b.push(txt(40 + i * (CW + GAP) + CW / 2, 114, String(i + 1), { anchor: 'middle', ...sm }));
    }
    b.push(count(rowEnd(40, 4) + 24, 44, '박 넷'));
    b.push(brace(40, rowEnd(40, 2), 128, '한 음절'));
    b.push(brace(40 + 2 * (CW + GAP), rowEnd(40, 4), 128, '한 음절'));
    b.push(txt(40, 200, '한국어 — 글자 한 칸이 한 음절', sm));
    b.push(row(40, 212, ['학', '교']));
    b.push(count(rowEnd(40, 2) + 24, 212, '음절 둘'));
    return svg({
        width: 400, height: 295,
        title: '박으로는 넷, 음절로는 둘',
        desc: '같은 낱말을 박으로 세면 네 칸이고 음절로 묶으면 두 덩이다',
        body: jpGroup(b.join('')),
    });
}

/** 한 글자 한 박 규칙의 다섯 자리. */
function oneBoxRule() {
    const rows = [
        ['보통 가나', ['さ', 'か', 'な'], [], '박 셋'],
        ['요음', ['きょ', 'う'], [0], '박 둘'],
        ['촉음', ['き', 'っ', 'て'], [1], '박 셋'],
        ['ん 박', ['パ', 'ン'], [1], '박 둘'],
        ['장음', ['ビ', 'ー', 'ル'], [1], '박 셋'],
    ];
    const b = [];
    rows.forEach(([name, items, mark, label], i) => {
        const y = 40 + i * RH;
        b.push(count(40, y, name));
        b.push(row(180, y, items, { mark }));
        b.push(count(rowEnd(180, 3) + 24, y, label));
    });
    return svg({
        width: 470, height: 40 + 4 * RH + CH + 26,
        title: '한 글자 한 박 규칙',
        desc: '작게 쓴 글자만 앞 칸에 들어가고 나머지는 한 글자가 한 칸을 차지한다',
        body: jpGroup(b.join('')),
    });
}

/** 촉음은 소리가 없는데 한 칸을 차지한다. */
function sokuon() {
    const b = [];
    b.push(txt(40, 32, '촉음이 없으면', sm));
    b.push(row(40, 44, ['き', 'て']));
    b.push(count(rowEnd(40, 2) + 24, 44, '박 둘'));
    b.push(txt(40, 140, '촉음이 있으면', sm));
    b.push(row(40, 152, ['き', 'っ', 'て'], { mark: [1] }));
    b.push(count(rowEnd(40, 3) + 24, 152, '박 셋'));
    b.push(px(40 + CW + GAP + CW / 2, 210, 40 + CW + GAP + CW / 2, 230, { cls: 's2', marker: 'ar2', width: 2 }));
    b.push(txt(40, 254, '소리가 나지 않는 칸이다. 그래도 한 박이다', sm));
    return svg({
        width: 340, height: 280,
        title: '촉음이 차지하는 칸',
        desc: '소리가 나지 않는 칸이 하나 늘어 아래가 위보다 한 박 길다',
        body: jpGroup(b.join('')),
    });
}

/** ん 박은 닫는 자리가 달라져도 언제나 한 칸이다. */
function nMora() {
    const b = [];
    b.push(txt(40, 32, '한 낱말에 두 번 나오기도 한다', sm));
    b.push(row(40, 44, ['し', 'ん', 'ぶ', 'ん'], { mark: [1, 3] }));
    b.push(count(rowEnd(40, 4) + 24, 44, '박 넷'));
    b.push(txt(40, 140, '닫는 자리는 뒤에 오는 소리에 따라 달라진다', sm));
    const cases = [
        [['さ', 'ん', 'ぽ'], '입술을 닫는다'],
        [['あ', 'ん', 'な', 'い'], '혀끝으로 막는다'],
        [['か', 'ん', 'こ', 'く'], '혀 뒤로 막는다'],
    ];
    cases.forEach(([items, note], i) => {
        const y = 152 + i * RH;
        b.push(row(40, y, items, { mark: [1] }));
        b.push(count(340, y, note));
    });
    b.push(txt(40, 364, '닫는 자리는 세 낱말에서 다르지만 칸은 언제나 하나다', sm));
    return svg({
        width: 480, height: 390,
        title: 'ん 박은 언제나 한 칸',
        desc: '닫는 자리가 낱말마다 달라도 차지하는 칸은 하나다',
        body: jpGroup(b.join('')),
    });
}

/** 장음 한 칸이 낱말을 가른다. */
function longVowel() {
    const b = [];
    b.push(txt(40, 32, '가타카나 — 장음 기호가 한 칸이다', sm));
    b.push(row(40, 44, ['ビ', 'ル']));
    b.push(count(rowEnd(40, 2) + 24, 44, '박 둘'));
    b.push(row(40, 108, ['ビ', 'ー', 'ル'], { mark: [1] }));
    b.push(count(rowEnd(40, 3) + 24, 108, '박 셋'));
    b.push(txt(40, 204, '히라가나 — 모음 글자를 이어 적는다', sm));
    b.push(row(40, 216, ['お', 'ば', 'さ', 'ん']));
    b.push(count(rowEnd(40, 4) + 24, 216, '박 넷'));
    b.push(row(40, 280, ['お', 'ば', 'あ', 'さ', 'ん'], { mark: [2] }));
    b.push(count(rowEnd(40, 5) + 24, 280, '박 다섯'));
    return svg({
        width: 480, height: 365,
        title: '장음이 한 칸을 더한다',
        desc: '칸 하나가 늘어나는 것만으로 다른 낱말이 된다',
        body: jpGroup(b.join('')),
    });
}

/** 한국어 화자가 잃는 박. */
function koreanLoss() {
    const cols = [
        [40, ['し', 'ん', 'ぶ', 'ん'], ['しん', 'ぶん']],
        [360, ['が', 'っ', 'こ', 'う'], ['がっ', 'こう']],
    ];
    const b = [];
    cols.forEach(([x, four, two]) => {
        b.push(txt(x, 32, '박으로 세면 넷', sm));
        b.push(row(x, 44, four, { mark: [1, 3] }));
        b.push(px(x + (CW + GAP) + CW / 2, 100, x + 60, 174, { cls: 's2', marker: 'ar2', width: 2 }));
        b.push(px(x + 3 * (CW + GAP) + CW / 2, 100, x + 188, 174, { cls: 's2', marker: 'ar2', width: 2 }));
        b.push(row(x, 180, two, { cw: 120 }));
        b.push(txt(x, 258, '앞 칸에 넣어 읽으면 둘 — 두 박을 잃는다', sm));
    });
    return svg({
        width: 640, height: 280,
        title: '한국어 화자가 잃는 박',
        desc: '강조된 칸이 앞 칸 안으로 흡수되어 네 박이 두 박으로 줄어든다',
        body: jpGroup(b.join('')),
    });
}

/** 칸 하나가 뜻을 가르는 짝들. */
function minimalPair() {
    const P = { cw: 44, ch: 44, gap: 6, size: 24 };
    const pairs = [
        [['ビ', 'ル'], ['ビ', 'ー', 'ル'], [1], '박 둘', '박 셋'],
        [['お', 'と'], ['お', 'っ', 'と'], [1], '박 둘', '박 셋'],
        [['い', 'しょ'], ['い', 'っ', 'しょ'], [1], '박 둘', '박 셋'],
        [['お', 'ば', 'さ', 'ん'], ['お', 'ば', 'あ', 'さ', 'ん'], [2], '박 넷', '박 다섯'],
    ];
    const b = [];
    b.push(txt(40, 30, '왼쪽과 오른쪽은 칸 수만 다르다', sm));
    pairs.forEach(([short, long, mark, c1, c2], i) => {
        const y = 46 + i * 64;
        b.push(row(40, y, short, P));
        b.push(txt(250, y + 28, c1, { cls: 'ink' }));
        b.push(row(320, y, long, { ...P, mark }));
        b.push(txt(580, y + 28, c2, { cls: 'ink' }));
    });
    return svg({
        width: 680, height: 315,
        title: '칸 하나가 뜻을 가른다',
        desc: '네 짝 모두 칸 수만 다른데 서로 다른 낱말이다',
        body: jpGroup(b.join('')),
    });
}

/** 하이쿠의 다섯 · 일곱 · 다섯은 박으로 센 것이다. */
function haiku() {
    const lines = [
        [['ふ', 'る', 'い', 'け', 'や'], '박 다섯'],
        [['か', 'わ', 'ず', 'と', 'び', 'こ', 'む'], '박 일곱'],
        [['み', 'ず', 'の', 'お', 'と'], '박 다섯'],
    ];
    const b = [];
    b.push(txt(40, 30, '잘 알려진 하이쿠 한 수를 칸으로 나눈 것', sm));
    lines.forEach(([items, label], i) => {
        const y = 48 + i * RH;
        b.push(row(40, y, items));
        b.push(count(500, y, label));
    });
    return svg({
        width: 600, height: 48 + 2 * RH + CH + 26,
        title: '하이쿠의 세 줄',
        desc: '세 줄의 칸 수가 다섯 일곱 다섯이다',
        body: jpGroup(b.join('')),
    });
}

export default [
    { name: 'jp-m-mora-vs-syllable', svg: moraVsSyllable() },
    { name: 'jp-m-one-box-rule', svg: oneBoxRule() },
    { name: 'jp-m-sokuon', svg: sokuon() },
    { name: 'jp-m-n-mora', svg: nMora() },
    { name: 'jp-m-long-vowel', svg: longVowel() },
    { name: 'jp-m-korean-loss', svg: koreanLoss() },
    { name: 'jp-m-minimal-pair', svg: minimalPair() },
    { name: 'jp-m-haiku', svg: haiku() },
];
