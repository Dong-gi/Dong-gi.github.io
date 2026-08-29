/**
 * 심리학 문서 18장(사회심리학)의 그림.
 *
 * 이름은 모두 `psy-s-` 로 시작한다(18장 담당자에게 배정된 접두어).
 *
 * SVG 안에는 수식을 쓸 수 없고, lib 의 esc() 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 `~` 를 쓰지 않는다. 큰따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 *
 * 자료 규칙 — 이 파일에는 남의 원자료를 옮긴 그림이 하나도 없다.
 *   · psy-s-bystander-math 는 확률 계산을 그대로 그린 것이다(자료가 아니다).
 *   · psy-s-verdicts 는 이 문서가 본문에서 내린 판정을 배치한 지도이며,
 *     두 축 다 눈금이 없다. 어느 연구의 수치도 아니다.
 * 문서의 캡션에도 그렇게 적었다.
 *
 * 상자와 화살표만으로 되는 그림(재촉 단계 · 설계 요건 · 두 이론의 예측)은
 * d2/psychology/psy-s-*.d2 에 있다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

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
 * psy-s-bystander-math — 두 물음의 답이 반대 방향으로 간다.
 *
 * 무엇을 읽어야 하나: ‘이 사람이 나설 확률’ 이 내려가는 것과 ‘누군가 나설
 * 확률’ 이 올라가는 것이 함께 일어날 수 있다는 것. 이 그림은 확률 계산이지
 * 자료가 아니다. 한 사람이 나설 확률의 모양은 보기로 정한 것이다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 840;
    const H = 410;
    const g = [];
    const f = frame({ xRange: [1, 20], yRange: [0, 1], box: { x: 116, y: 60, w: 380, h: 260 } });

    g.push(f.axes({ xTicks: [1, 5, 10, 15, 20], yTicks: [0, 0.25, 0.5, 0.75, 1], grid: true }));
    g.push(txt(f.X(10.5), 366, '그 자리에 함께 있는 사람 수', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 46, '확률', { cls: 'ink2', size: 'sm' }));

    const p = n => 0.85 * Math.pow(n, -0.45);
    g.push(f.curve(n => p(n), { cls: 's2' }));
    g.push(f.curve(n => 1 - Math.pow(1 - p(n), n), { cls: 's1' }));

    g.push(legend(544, 96, [
        { slot: 1, name: '누군가 한 사람이라도 나설 확률' },
        { slot: 2, name: '지목된 한 사람이 나설 확률' },
    ]));

    g.push(txt(544, 158, '두 곡선이 반대 방향으로 간다', { cls: 'ink bold', size: 'sm' }));
    g.push(txt(544, 186, '아래 곡선이 내려가는 것이', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 206, '방관자 효과다. 사람이 많을수록', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 226, '한 사람이 나설 확률은 준다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 258, '그런데 사람 수가 함께 늘므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 278, '누군가는 나설 확률이 오른다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 310, '‘도움을 받을 확률’ 을 물었는지', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 330, '‘이 사람이 도울 확률’ 을 물었는지에', { cls: 'ink2', size: 'sm' }));
    g.push(txt(544, 350, '따라 답이 반대가 된다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 392, '아래 곡선의 모양은 보기로 정한 것이고, 위 곡선은 그것에서 계산한 값이다. 자료가 아니다', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-s-bystander-math', svg: svg({ width: W, height: H, title: '한 사람이 나설 확률과 누군가 나설 확률', desc: '사람이 많아지면 개인이 나설 확률은 내려가고 누군가 나설 확률은 올라간다', body: g.join('\n') }) };
})());

/* ------------------------------------------------------------------ *
 * psy-s-verdicts — 이 장이 다루는 결과들의 자리.
 *
 * 무엇을 읽어야 하나: 오른쪽 위와 오른쪽 아래가 둘 다 있다는 것. 검사를
 * 많이 받은 것이 곧 살아남은 것이 아니다. 그리고 왼쪽 아래는 검사를 받을
 * 수 있는 형태가 아니었던 것이다.
 *
 * 두 축 다 눈금이 없다. 이 배치는 본문의 판정을 옮긴 것이고 수치가 아니다.
 * ------------------------------------------------------------------ */
add((() => {
    const W = 900;
    const H = 440;
    const g = [];
    const f = frame({ xRange: [0, 10], yRange: [0, 10], box: { x: 116, y: 56, w: 380, h: 300 } });

    g.push(f.axes({ xTicks: [], yTicks: [], grid: false }));
    g.push(txt(f.X(5), 404, '독립적인 재현 검사를 얼마나 받았는가', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(0), 420, '적게', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(f.X(10), 420, '많이', { anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(txt(40, 42, '그 검사에서 어떻게 되었는가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(108, f.Y(9.6), '버팀', { anchor: 'end', cls: 'ink2', size: 'sm' }));
    g.push(txt(108, f.Y(0.4), '무너짐', { anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(ln([[f.X(0), f.Y(5)], [f.X(10), f.Y(5)]], { stroke: COL.grid, sw: 1.2, dash: '4 4' }));

    const items = [
        [1.0, 0.8, '스탠퍼드 감옥 실험 — 연구로 성립하지 않는다', 's2'],
        [9.0, 1.0, '자아 고갈 — 다중연구실에서 효과 거의 0', 's2'],
        [8.2, 1.6, '사회적 점화 — 재현 실패', 's2'],
        [8.6, 2.4, '파워 포즈, 호르몬과 행동 — 재현되지 않음', 's2'],
        [9.4, 2.9, '행위자-관찰자 비대칭 — 메타분석에서 거의 0', 's2'],
        [8.8, 3.6, '암묵적 연합 검사의 개인 행동 예측력 — 작다', 's2'],
        [5.0, 5.2, '밀그램 복종 — 결과는 되풀이, 해석은 논쟁', 's1'],
        [4.2, 6.0, '인지부조화 — 틀은 살아 있고 대안 설명과 겨룬다', 's1'],
        [8.6, 6.4, '파워 포즈, 주관적 보고 — 재현되었다', 's3'],
        [8.4, 7.4, '방관자 효과 — 메타분석에서 지지됨', 's3'],
        [7.6, 8.9, '대응 편향 — 다중연구실에서 되풀이됨', 's3'],
        [8.8, 8.4, '애쉬 동조 — 여러 나라에서 되풀이됨', 's3'],
    ];

    items.forEach(([x, y, , cls], i) => {
        g.push(`<circle cx="${r2(f.X(x))}" cy="${r2(f.Y(y))}" r="10" fill="var(--${cls})"/>`);
        g.push(`<text x="${r2(f.X(x))}" y="${r2(f.Y(y)) + 4}" text-anchor="middle" font-size="11" font-weight="700" fill="#ffffff">${i + 1}</text>`);
    });

    items.forEach(([, , name], i) => {
        const yy = 84 + i * 24;
        g.push(txt(548, yy, (i + 1) + '. ' + name, { cls: 'ink', size: 'sm' }));
    });

    g.push(txt(46, 434, '두 축 다 눈금이 없다. 이 배치는 본문의 판정을 옮긴 지도이고 어느 연구의 수치도 아니다', { cls: 'ink2', size: 'sm' }));

    return { name: 'psy-s-verdicts', svg: svg({ width: W, height: H, title: '이 장이 다루는 결과들이 재현 검사에서 어떻게 되었는가', desc: '검사를 많이 받은 결과 가운데 살아남은 것과 무너진 것이 함께 있다', body: g.join('\n') }) };
})());

export default figures;
