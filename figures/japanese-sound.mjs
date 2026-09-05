/** 6장 「소리의 지도」 그림. 원장 §5 를 지킨다 — 그림 안에 한자를 넣지 않는다. */
import { svg, px, txt } from './lib.mjs';
import { JP_FONT, jpGroup } from './japanese-font.mjs';

/** 테두리만 있는 상자. cls 로 선 색을 고른다(gr·s1·s2·s3). */
const box = (x, y, w, h, cls = 'gr', r = 6) =>
    `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="none" class="${cls}" stroke-width="1.5"/>`;

/** 옅게 칠한 강조 칸. f1·f2·f3 이 채움 색을 고른다. */
const fillBox = (x, y, w, h, cls = 'f2', op = 0.15, r = 6) =>
    `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" class="${cls}" fill-opacity="${op}" stroke="none"/>`;

/** 그냥 선. */
const seg = (x1, y1, x2, y2, cls = 'gr', dash) =>
    `<path class="${cls}" fill="none" stroke-width="1.5"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${x1} ${y1} L${x2} ${y2}"/>`;

/** 점. */
const dot = (x, y, cls = 'f1', r = 5) => `<circle cx="${x}" cy="${y}" r="${r}" class="${cls}"/>`;

/** 속이 빈 점 — 낱말에 속하지 않는 자리(조사)에 쓴다. */
const ring = (x, y, cls = 's1', r = 5) =>
    `<circle cx="${x}" cy="${y}" r="${r}" fill="none" class="${cls}" stroke-width="1.5" stroke-dasharray="3 2"/>`;

/** 가나 한 글자를 크게. */
const kana = (x, y, s, cls = 'ink') =>
    `<text class="${cls} bold" x="${x}" y="${y}" text-anchor="middle" font-size="22">${s}</text>`;

// ── 1. 두 언어가 쓰는 차원이 다르다 ──────────────────────────
const dimension = () => {
    const b = [];
    const AX = 100, AY = 268, RIGHT = 676, TOP = 62;
    const HI = 108, LO = 226;
    b.push(seg(AX, AY, RIGHT, AY, 'ax'));
    b.push(seg(AX, AY, AX, TOP, 'ax'));
    b.push(txt(AX - 8, TOP - 20, '성대의 떨림', { cls: 'ink2', size: 'sm' }));
    b.push(txt(RIGHT - 4, AY + 22, '숨의 세기와 긴장', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(seg(AX, HI, RIGHT - 20, HI, 'gr', '4 3'));
    b.push(seg(AX, LO, RIGHT - 20, LO, 'gr', '4 3'));
    b.push(txt(AX - 8, HI + 4, '있음', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(txt(AX - 8, LO + 4, '없음', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    b.push(kana(168, LO + 8, 'か'));
    b.push(kana(168, HI + 8, 'が'));
    b.push(px(196, LO - 6, 196, HI + 10, { cls: 's1', marker: 'ar1', width: 2 }));
    b.push(txt(208, 156, '일본어는', { cls: 'f1', size: 'sm' }));
    b.push(txt(208, 172, '이 차이로 갈린다', { cls: 'f1', size: 'sm' }));

    b.push(kana(420, LO + 8, 'ㄱ'));
    b.push(kana(520, LO + 8, 'ㄲ'));
    b.push(kana(620, LO + 8, 'ㅋ'));
    b.push(px(408, LO - 30, 632, LO - 30, { cls: 's2', marker: 'ar2', width: 2 }));
    b.push(txt(520, LO - 42, '한국어는 이 차이로 갈린다', { anchor: 'middle', cls: 'f2', size: 'sm' }));
    b.push(txt(520, LO + 40, '셋 다 어두에서는 떨림이 없다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 2. 틀리는 칸은 대각선으로 마주 본다 ─────────────────────
const twoErrors = () => {
    const b = [];
    const CX = [214, 452], CW = 236, CY = [96, 190], CH = 76;
    b.push(txt(CX[0] + CW / 2, 74, '낱말 첫머리', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(CX[1] + CW / 2, 74, '낱말 가운데', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(kana(170, CY[0] + 46, 'か'));
    b.push(txt(190, CY[0] + 50, '무성', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    b.push(kana(170, CY[1] + 46, 'が'));
    b.push(txt(190, CY[1] + 50, '유성', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    const cell = (ci, ri, hit, l1, l2) => {
        const x = CX[ci], y = CY[ri];
        const out = [];
        if (hit) out.push(fillBox(x, y, CW, CH, 'f2', 0.15));
        out.push(box(x, y, CW, CH, hit ? 's2' : 'gr'));
        out.push(txt(x + CW / 2, y + 30, l1, { anchor: 'middle', cls: hit ? 'f2' : 'ink' }));
        if (l2) out.push(txt(x + CW / 2, y + 52, l2, { anchor: 'middle', cls: 'ink2', size: 'sm' }));
        return out.join('');
    };
    b.push(cell(0, 0, false, '그대로 된다', '한국어 어두 평음도 떨리지 않는다'));
    b.push(cell(1, 0, true, '유성으로 나온다', '한국어 규칙이 저절로 떨림을 넣는다'));
    b.push(cell(0, 1, true, '무성으로 나온다', '한국어에 어두 유성음이 없다'));
    b.push(cell(1, 1, false, '그대로 된다', '한국어 규칙과 결과가 맞는다'));
    b.push(seg(CX[1] + CW - 12, CY[0] + CH - 8, CX[0] + 12, CY[1] + 12, 's2', '5 4'));
    b.push(txt(360, 286, '칠한 두 칸이 한국어 화자가 놓치는 자리다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 3. 목에 손을 대면 무엇이 잡히는가 ───────────────────────
const throat = () => {
    const b = [];
    const X0 = 196, X1 = 636, W = (X1 - X0) / 3;
    b.push(txt(X0, 62, '손을 목에 대고 낱말을 끊지 않고 낸다', { cls: 'ink2', size: 'sm' }));
    const row = (y, label, gapAt, marks) => {
        const out = [txt(X0 - 16, y + 5, label, { anchor: 'end', cls: 'ink', size: 'sm' })];
        for (let i = 0; i < 3; i += 1) {
            const x = X0 + i * W;
            if (i === gapAt) {
                out.push(box(x, y - 15, W, 30, 's2', 3).replace('/>', ' stroke-dasharray="4 3"/>'));
                out.push(txt(x + W / 2, y + 5, '떨림 없음', { anchor: 'middle', cls: 'f2', size: 'sm' }));
            } else {
                out.push(fillBox(x, y - 15, W, 30, 'f1', 0.28, 3));
                out.push(box(x, y - 15, W, 30, 's1', 3));
            }
            out.push(kana(x + W / 2, y + 48, marks[i], 'ink2'));
        }
        return out.join('');
    };
    b.push(row(122, '무성 자음이 든 낱말', 1, ['に', 'か', 'い']));
    b.push(row(228, '유성 자음이 든 낱말', -1, ['に', 'が', 'い']));
    b.push(txt(X1 + 8, 127, '끊긴다', { cls: 'f2', size: 'sm' }));
    b.push(txt(X1 + 8, 233, '이어진다', { cls: 'f1', size: 'sm' }));
    b.push(txt(X0 - 16, 300, '칠한 칸이 떨림이 잡히는 구간이다', { cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 4. 격자에서 벗어난 칸 ───────────────────────────────────
const exceptionCells = () => {
    const b = [];
    const CW = 88, CH = 58, X0 = 200, Y0 = 94;
    const vowels = ['あ', 'い', 'う', 'え', 'お'];
    const rows = [
        ['さ', ['さ', 'し', 'す', 'せ', 'そ'], [1]],
        ['た', ['た', 'ち', 'つ', 'て', 'と'], [1, 2]],
        ['は', ['は', 'ひ', 'ふ', 'へ', 'ほ'], [2]],
    ];
    vowels.forEach((v, i) => b.push(kana(X0 + i * CW + CW / 2, Y0 - 14, v, 'ink2')));
    b.push(txt(X0 - 16, Y0 - 14, '모음', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    rows.forEach(([head, cells, hits], r) => {
        const y = Y0 + r * CH;
        b.push(kana(X0 - 44, y + CH / 2 + 8, head, 'ink2'));
        cells.forEach((c, i) => {
            const x = X0 + i * CW;
            const hit = hits.includes(i);
            const soft = head === 'は' && i === 1;
            if (hit) b.push(fillBox(x, y, CW, CH, 'f2', 0.15, 3));
            b.push(box(x, y, CW, CH, hit ? 's2' : 'gr', 3));
            if (soft) b.push(box(x + 5, y + 5, CW - 10, CH - 10, 's2', 3).replace('/>', ' stroke-dasharray="3 3"/>'));
            b.push(kana(x + CW / 2, y + CH / 2 + 8, c, hit ? 'f2' : 'ink'));
        });
    });
    b.push(txt(X0, Y0 + 3 * CH + 30, '칠한 칸 — 자리가 예상하는 소리와 실제 소리가 어긋난다', { cls: 'ink2', size: 'sm' }));
    b.push(txt(X0, Y0 + 3 * CH + 50, '점선 칸 — 어긋나는 정도가 그보다 덜하다', { cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 5. 어느 방향으로 벗어났는가 ─────────────────────────────
const exceptionWhy = () => {
    const b = [];
    b.push(txt(178, 66, '자리가 예상하는 것', { cls: 'ink2', size: 'sm' }));
    b.push(txt(404, 66, '실제로 나는 것', { cls: 'ink2', size: 'sm' }));
    b.push(txt(600, 66, '무엇이 일어났나', { cls: 'ink2', size: 'sm' }));
    const rows = [
        ['し', '혀끝의 마찰', '혀가 뒤로 당겨진 마찰', '구개음화'],
        ['ち', '혀끝의 터짐', '터진 뒤에 마찰이 붙는다', '파찰 + 구개음화'],
        ['つ', '혀끝의 터짐', '터진 뒤에 마찰이 붙는다', '파찰'],
        ['ふ', '목에서 나는 마찰', '두 입술 사이의 마찰', '입술로 자리를 옮김'],
    ];
    rows.forEach(([k, from, to, tag], i) => {
        const y = 108 + i * 56;
        b.push(kana(128, y + 7, k));
        b.push(txt(178, y + 5, from, { cls: 'ink', size: 'sm' }));
        b.push(px(348, y, 388, y, { cls: 's1', marker: 'ar1', width: 2 }));
        b.push(txt(404, y + 5, to, { cls: 'ink', size: 'sm' }));
        b.push(box(578, y - 16, 128, 32, 's2', 4));
        b.push(txt(642, y + 5, tag, { anchor: 'middle', cls: 'f2', size: 'sm' }));
    });
    b.push(txt(128, 340, '네 칸 모두 같은 자리에서 벗어난 것이 아니다', { cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 6. 네 글자가 두 소리로 ──────────────────────────────────
const yotsugana = () => {
    const b = [];
    const pair = (x, a, c) => {
        const out = [];
        out.push(box(x, 84, 74, 52, 'gr'));
        out.push(kana(x + 37, 118, a));
        out.push(box(x + 104, 84, 74, 52, 'gr'));
        out.push(kana(x + 141, 118, c));
        out.push(px(x + 37, 142, x + 76, 176, { cls: 's1', marker: 'ar1', width: 2 }));
        out.push(px(x + 141, 142, x + 102, 176, { cls: 's1', marker: 'ar1', width: 2 }));
        out.push(box(x + 14, 180, 150, 40, 's1'));
        out.push(txt(x + 89, 205, '한 소리', { anchor: 'middle', cls: 'f1' }));
        return out.join('');
    };
    b.push(txt(78, 66, '네 글자', { cls: 'ink2', size: 'sm' }));
    b.push(pair(96, 'じ', 'ぢ'));
    b.push(pair(450, 'ず', 'づ'));
    b.push(seg(70, 246, 650, 246, 'gr', '4 3'));
    b.push(txt(78, 272, '지금의 표기', { cls: 'ink2', size: 'sm' }));
    b.push(box(78, 284, 232, 58, 's3'));
    b.push(txt(194, 318, '기본은 짝의 왼쪽 글자', { anchor: 'middle', cls: 'ink' }));
    b.push(box(352, 284, 300, 58, 's2'));
    b.push(txt(502, 306, '짝의 오른쪽 글자는 두 자리에서만', { anchor: 'middle', cls: 'ink', size: 'sm' }));
    b.push(txt(502, 328, '같은 소리가 잇달을 때 · 낱말이 합쳐질 때', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    return b.join('');
};

// ── 7. 악센트의 형 ──────────────────────────────────────────
const accentShapes = () => {
    const b = [];
    const XS = [316, 392, 468, 566];
    b.push(txt(300, 56, '박 하나', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(txt(566, 56, '조사', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    b.push(seg(518, 70, 518, 344, 'gr', '4 3'));
    const rows = [
        ['평판형', [0, 1, 1, 1], -1],
        ['기복형 — 1박 뒤', [1, 0, 0, 0], 0],
        ['기복형 — 2박 뒤', [0, 1, 0, 0], 1],
        ['기복형 — 3박 뒤', [0, 1, 1, 0], 2],
    ];
    rows.forEach(([name, lv, drop], i) => {
        const base = 108 + i * 62;
        const Y = v => (v ? base - 15 : base + 15);
        b.push(txt(292, base + 5, name, { anchor: 'end', cls: 'ink' }));
        for (let k = 0; k < 4; k += 1) {
            b.push(seg(XS[k] - 22, Y(lv[k]), XS[k] + 22, Y(lv[k]), k === 3 ? 's3' : 's1'));
            if (k < 3) {
                const cls = k === drop ? 's2' : 'gr';
                const nx = k === 2 ? XS[3] - 22 : XS[k + 1] - 22;
                b.push(seg(XS[k] + 22, Y(lv[k]), nx, Y(lv[k + 1]), cls, k === drop ? undefined : '3 3'));
            }
            b.push(k === 3 ? ring(XS[k], Y(lv[k]), 's3') : dot(XS[k], Y(lv[k])));
        }
        if (drop >= 0) b.push(txt(XS[drop] + 30, Y(0) + 26, '여기서 내려간다', { cls: 'f2', size: 'sm' }));
        else b.push(txt(XS[3] + 26, Y(1) + 4, '끝까지 높다', { cls: 'f3', size: 'sm' }));
    });
    return b.join('');
};

// ── 8. 조사를 붙여야 갈리는 짝 ──────────────────────────────
const accentParticle = () => {
    const b = [];
    b.push(txt(60, 62, '낱말만 낼 때', { cls: 'ink2', size: 'sm' }));
    b.push(txt(400, 62, '조사를 붙였을 때', { cls: 'ink2', size: 'sm' }));
    b.push(seg(356, 76, 356, 268, 'gr', '4 3'));
    const rows = [[132, 0], [232, 1]];
    rows.forEach(([base, kind]) => {
        const Y = v => (v ? base - 15 : base + 15);
        const L = [180, 258];
        b.push(seg(L[0] - 22, Y(0), L[0] + 22, Y(0), 's1'));
        b.push(seg(L[0] + 22, Y(0), L[1] - 22, Y(1), 'gr', '3 3'));
        b.push(seg(L[1] - 22, Y(1), L[1] + 22, Y(1), 's1'));
        b.push(dot(L[0], Y(0)));
        b.push(dot(L[1], Y(1)));
        b.push(kana(L[0], base + 54, 'は', 'ink2'));
        b.push(kana(L[1], base + 54, 'し', 'ink2'));

        const R = [446, 524, 610];
        const last = kind === 0 ? 1 : 0;
        b.push(seg(R[0] - 22, Y(0), R[0] + 22, Y(0), 's1'));
        b.push(seg(R[0] + 22, Y(0), R[1] - 22, Y(1), 'gr', '3 3'));
        b.push(seg(R[1] - 22, Y(1), R[1] + 22, Y(1), 's1'));
        b.push(seg(R[1] + 22, Y(1), R[2] - 22, Y(last), last ? 'gr' : 's2', last ? '3 3' : undefined));
        b.push(seg(R[2] - 22, Y(last), R[2] + 22, Y(last), 's3'));
        b.push(dot(R[0], Y(0)));
        b.push(dot(R[1], Y(1)));
        b.push(ring(R[2], Y(last), 's3'));
        b.push(kana(R[0], base + 54, 'は', 'ink2'));
        b.push(kana(R[1], base + 54, 'し', 'ink2'));
        b.push(kana(R[2], base + 54, 'が', 'f3'));
        b.push(txt(R[2] + 26, Y(last) + 5, last ? '높다' : '내려간다', { cls: last ? 'f3' : 'f2', size: 'sm' }));
    });
    b.push(txt(60, 292, '왼쪽 두 줄은 모양이 같다', { cls: 'f2', size: 'sm' }));
    return b.join('');
};

// ── 9. 연탁이 일어나는 자리와 안 일어나는 자리 ───────────────
const rendaku = () => {
    const b = [];
    b.push(txt(70, 62, '앞 낱말', { cls: 'ink2', size: 'sm' }));
    b.push(txt(232, 62, '뒤 낱말', { cls: 'ink2', size: 'sm' }));
    b.push(txt(430, 62, '합친 것', { cls: 'ink2', size: 'sm' }));
    const rows = [
        ['て', 'かみ', ['て', 'が', 'み'], 1, '일어난다'],
        ['ふた', 'こ', ['ふ', 'た', 'ご'], 2, '일어난다'],
        ['おや', 'こ', ['お', 'や', 'こ'], -1, '안 일어난다'],
        ['はる', 'かぜ', ['は', 'る', 'か', 'ぜ'], -1, '안 일어난다'],
    ];
    rows.forEach(([a, c, res, hit, tag], i) => {
        const y = 108 + i * 58;
        b.push(kana(84, y + 7, a));
        b.push(txt(150, y + 6, '+', { anchor: 'middle', cls: 'ink2' }));
        b.push(kana(212, y + 7, c));
        b.push(px(300, y, 344, y, { cls: 's1', marker: 'ar1', width: 2 }));
        res.forEach((ch, k) => {
            const x = 400 + k * 34;
            if (k === hit) b.push(fillBox(x - 17, y - 20, 34, 34, 'f1', 0.22, 4));
            b.push(kana(x, y + 7, ch, k === hit ? 'f1' : 'ink'));
        });
        b.push(txt(590, y + 6, tag, { cls: hit >= 0 ? 'f1' : 'f2', size: 'sm' }));
    });
    b.push(txt(70, 348, '가운데 두 줄은 뒤 낱말이 같은데 결과가 갈린다', { cls: 'ink2', size: 'sm' }));
    return b.join('');
};

const make = (name, width, height, title, desc, body) => ({
    name,
    svg: svg({ width, height, title, desc, body: jpGroup(body) }),
});

export default [
    make('jp-s-voicing-dimension', 720, 320,
        '두 언어가 자음을 가르는 데 쓰는 차원',
        '일본어는 성대의 떨림 축으로 두 소리를 가르고, 한국어는 숨과 긴장 축으로 세 소리를 가른다.',
        dimension()),
    make('jp-s-voicing-two-errors', 720, 300,
        '한국어 화자가 틀리는 두 칸',
        '낱말 첫머리와 가운데, 무성과 유성의 네 칸 가운데 두 칸에서 한국어 규칙이 반대 결과를 만든다.',
        twoErrors()),
    make('jp-s-throat-check', 720, 320,
        '목에 손을 대면 잡히는 것',
        '무성 자음 자리에서는 떨림이 끊기고 유성 자음 자리에서는 이어진다.',
        throat()),
    make('jp-s-gojuon-exception', 720, 340,
        '격자에서 벗어난 칸',
        '세 줄의 열다섯 칸 가운데 네 칸이 자리가 예상하는 소리와 다르다.',
        exceptionCells()),
    make('jp-s-exception-why', 720, 360,
        '네 칸이 벗어난 방향',
        '벗어난 이유가 칸마다 다르다. 혀의 자리, 터짐 뒤의 마찰, 입술로의 이동이 섞여 있다.',
        exceptionWhy()),
    make('jp-s-yotsugana', 720, 360,
        '네 글자가 두 소리로 합쳐진 것과 지금의 표기',
        '두 짝이 각각 한 소리로 합쳐졌고, 표기는 한쪽을 기본으로 삼고 다른 쪽을 두 자리에만 남겼다.',
        yotsugana()),
    make('jp-s-accent-shapes', 720, 360,
        '악센트의 형 — 내려가는 자리가 형을 정한다',
        '내려가지 않는 형이 하나, 내려가는 자리가 다른 형이 셋이다. 조사 자리까지 보아야 마지막 형이 드러난다.',
        accentShapes()),
    make('jp-s-accent-particle', 720, 320,
        '조사를 붙여야 갈리는 짝',
        '낱말만 내면 두 줄의 높낮이가 같고, 조사를 붙이면 조사가 내려가는 쪽과 그대로 높은 쪽으로 갈린다.',
        accentParticle()),
    make('jp-s-rendaku', 720, 366,
        '연탁이 일어나는 자리와 안 일어나는 자리',
        '뒤 낱말의 첫 소리가 탁음이 되는 것이 연탁이고, 같은 뒤 낱말이라도 결과가 갈린다.',
        rendaku()),
];
