/**
 * 심리학 문서 16장(정서와 동기)의 그림.
 *
 * 이름은 모두 `psy-n-` 로 시작한다(16장 담당자에게 배정된 접두어).
 *
 * SVG 안에는 수식을 쓸 수 없고, lib 의 esc() 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 `~` 를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 자료 규칙 — 이 파일의 곡선과 점은 전부 도식이다. 남의 원자료를 옮긴 것이
 * 하나도 없고, 세로축에 눈금을 넣지 않은 그림은 일부러 그렇게 한 것이다.
 *
 * 상자와 화살표만으로 되는 그림(이론 비교·절차 도식·과정 모형)은
 * d2/psychology/psy-n-*.d2 에 있다.
 */
import { svg, frame, txt, px, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

const COL = {
    s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)',
    ink: 'var(--ink)', ink2: 'var(--ink2)', grid: 'var(--grid)',
};

function ln(pts, { stroke = COL.ink2, sw = 1.6, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/* ------------------------------------------------------------------ *
 * psy-n-arousal-valence — 두 축 위에 놓은 정서 낱말.
 *
 * 무엇을 읽어야 하나: 정서를 몇 개의 목록으로 세는 대신 두 축 위의 자리로
 * 보는 방식이 있다는 것. 낱말의 자리는 이 방식이 어떻게 생겼는지 보이려고
 * 찍은 도식이며 특정 연구의 좌표가 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 780;
    const H = 500;
    const g = [];
    const cx = 380;
    const cy = 250;
    const R = 178;

    g.push(`<circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="${COL.grid}" stroke-width="1.4" stroke-dasharray="5 4"/>`);
    g.push(px(cx, cy + R + 44, cx, cy - R - 44, { cls: '', marker: 'ark', width: 1.5 }));
    g.push(px(cx - R - 66, cy, cx + R + 66, cy, { cls: '', marker: 'ark', width: 1.5 }));
    g.push(px(cx, cy - R - 44, cx, cy + R + 44, { cls: '', marker: 'ark', width: 1.5 }));
    g.push(px(cx + R + 66, cy, cx - R - 66, cy, { cls: '', marker: 'ark', width: 1.5 }));

    g.push(txt(cx, cy - R - 56, '각성이 높다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx, cy + R + 66, '각성이 낮다', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx - R - 74, cy - 10, '불쾌하다', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(cx + R + 74, cy - 10, '유쾌하다', { cls: 'ink2', size: 'sm' }));

    const words = [
        [80, '흥분', 's3'], [50, '기쁨', 's3'], [20, '즐거움', 's3'],
        [-20, '흐뭇함', 's1'], [-50, '느긋함', 's1'], [-80, '나른함', 's1'],
        [-110, '지루함', 's1'], [-140, '처짐', 's1'],
        [168, '언짢음', 's2'], [140, '짜증', 's2'], [112, '긴장', 's2'],
    ];
    for (const [deg, word, cls] of words) {
        const rad = (deg * Math.PI) / 180;
        const x = cx + R * Math.cos(rad);
        const y = cy - R * Math.sin(rad);
        g.push(`<circle cx="${r2(x)}" cy="${r2(y)}" r="4.5" fill="var(--${cls})"/>`);
        const out = 20;
        const lx = cx + (R + out) * Math.cos(rad);
        const ly = cy - (R + out) * Math.sin(rad) + 4;
        const anchor = Math.cos(rad) > 0.25 ? 'start' : (Math.cos(rad) < -0.25 ? 'end' : 'middle');
        g.push(txt(lx, ly, word, { anchor, cls: 'ink' }));
    }

    g.push(txt(24, 434, '낱말의 자리는 이 방식이 어떻게', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 452, '생겼는지 보이려고 찍은 도식이다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(24, 470, '특정 연구의 좌표가 아니다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-n-arousal-valence', svg: svg({ width: W, height: H, title: '각성과 유쾌함 두 축 위에 놓은 정서 낱말', desc: '정서를 몇 개의 목록으로 세는 대신 두 축 위의 자리로 보는 방식', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-n-loose-coupling — 정서의 세 성분이 서로 잘 맞지 않는다.
 *
 * 무엇을 읽어야 하나: 세 곡선의 꼭대기가 서로 다른 자리에 있고 모양도 다르다.
 * 셋 다 도식이며 세로축에 눈금을 넣지 않았다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 820;
    const H = 420;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 110, y: 62, w: 460, h: 250 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 356, '사건이 일어난 뒤 흐른 시간', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(44, 48, '반응의 세기', { cls: 'ink2', size: 'sm' }));
    g.push(ln([[f.X(2), f.Y(0)], [f.X(2), f.Y(10)]], { stroke: COL.grid, sw: 1.4, dash: '4 4' }));
    g.push(txt(f.X(2), f.Y(10) - 10, '사건', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    const bump = (peak, width, height) => x => (x < 2 ? 0.4 : height * Math.exp(-Math.pow((x - peak) / width, 2)) + 0.4);
    g.push(f.curve(bump(3.0, 1.0, 7.6), { cls: 's1' }));
    g.push(f.curve(bump(3.9, 2.6, 5.2), { cls: 's2' }));
    g.push(f.curve(bump(5.4, 1.6, 6.4), { cls: 's3' }));

    g.push(legend(600, 96, [
        { slot: 1, name: '몸의 반응 — 심장 박동' },
        { slot: 2, name: '얼굴에 나타난 것' },
        { slot: 3, name: '스스로 보고한 세기' },
    ]));
    g.push(txt(600, 176, '세 꼭대기가 같은 자리에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 196, '있지 않다. 그래서 어느', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 216, '하나를 재고 ‘정서를', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 236, '쟀다’ 고 할 수 없다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(600, 270, '세 곡선 다 도식이다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-n-loose-coupling', svg: svg({ width: W, height: H, title: '정서의 세 성분이 서로 맞지 않는 모양', desc: '몸의 반응, 얼굴, 자기보고의 꼭대기가 서로 다른 자리에 있다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-n-inverted-u — 각성과 수행의 뒤집힌 U.
 *
 * 무엇을 읽어야 하나: 곡선 자체보다 오른쪽의 단서들. 이 곡선은 아주 널리
 * 그려지지만 그것을 뒷받침하는 자료의 범위는 그림보다 훨씬 좁다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 410;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 110, y: 62, w: 400, h: 250 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 352, '각성 수준', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(44, 48, '수행', { cls: 'ink2', size: 'sm' }));

    g.push(f.curve(x => 9 - 0.34 * Math.pow(x - 5, 2), { from: 0.6, to: 9.4, cls: 's1' }));
    g.push(f.curve(x => 9 - 0.62 * Math.pow(x - 3.4, 2), { from: 0.4, to: 7.0, cls: 's2', dash: '6 4' }));

    g.push(f.label([5, 9.2], '쉬운 과제', { anchor: 'middle', cls: 'ink bold', dy: -10 }));
    g.push(f.label([1.3, 8.2], '어려운 과제', { anchor: 'end', cls: 'ink bold' }));
    g.push(f.guide([5, 0], [5, 9]));
    g.push(f.guide([3.4, 0], [3.4, 9]));

    g.push(txt(556, 96, '이 곡선을 쓸 때 조심할 것', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(556, 124, '원 연구는 20세기 초의 동물', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 144, '실험이었다. 그 뒤 사람의', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 164, '온갖 상황으로 넓혀 쓰였다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 198, '‘각성’ 이 무엇인지가 자리마다', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 218, '다르게 조작적으로 정의된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 252, '꼭대기가 어디인지는 사후에만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 272, '말할 수 있어서, 어떤 결과가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 292, '나와도 설명이 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 326, '축에 눈금이 없는 것은', { cls: 'ink2', size: 'sm' }));
    g.push(txt(556, 346, '도식이기 때문이다.', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-n-inverted-u', svg: svg({ width: W, height: H, title: '각성과 수행의 뒤집힌 U 곡선', desc: '꼭대기가 과제 난이도에 따라 옮겨 간다는 그림. 눈금 없는 도식이며 오른쪽에 이 곡선을 쓸 때의 단서가 있다', body: g.join('\n') }) };
})());

export default figures;
