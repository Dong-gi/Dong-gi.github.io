/**
 * 기초수학 9장(극한과 연속)의 「등비수열과 등비급수」 절 그림.
 *
 * 이름은 전부 `math-ser-` 로 시작한다. 기존 블록(math-log- / math-fn- / math-cal- /
 * math-int-)과 겹치지 않게 새로 배정한 접두어다.
 * source/build/figure.ts 가 이름 충돌을 오류로 잡으므로 접두어를 반드시 지킨다.
 *
 * SVG 안에는 수식을 쓸 수 없다(그림이 <img> 로 들어가 MathJax 가 닿지 않는다).
 * 그래서 아래첨자는 lib 의 `a~n` 표기를, 위첨자와 나머지 기호는 유니코드
 * (ⁿ, ⁻, ¹, ², ×, −, …)로 적는다. lib 의 esc() 가 물결표를 아래첨자로 바꾸므로
 * 라벨에 `~` 를 그냥 쓰면 안 되고, 따옴표는 이중 이스케이프되므로 ‘ ’ 를 쓴다.
 * HTML 엔티티도 쓸 수 없다.
 */
import { svg, frame, txt, legend } from './lib.mjs';

const figures = [];
const add = f => figures.push(f);
const r2 = v => Number.parseFloat(v.toFixed(2));

/** 화소 좌표 사각형. 채움은 색과 불투명도를 따로 준다. */
function box(x, y, w, h, { fill = 'none', op = 1, stroke = 'var(--ink2)', sw = 1.4, rx = 0 } = {}) {
    return `<rect x="${r2(x)}" y="${r2(y)}" width="${r2(w)}" height="${r2(h)}" rx="${rx}" fill="${fill}" fill-opacity="${op}" stroke="${stroke}" stroke-width="${sw}"/>`;
}

/** 화소 좌표 꺾은선. */
function ln(pts, { stroke = 'var(--ink2)', sw = 1.6, dash } = {}) {
    const d = pts.map(p => `${r2(p[0])} ${r2(p[1])}`).join(' L');
    return `<path d="M${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`;
}

/* ================================================================== *
 * 9-A. 등차수열과 등비수열 — 더하기와 곱하기의 차이
 * ================================================================== */
add((() => {
    const W = 620, H = 360;
    const f = frame({ xRange: [0.4, 8.6], yRange: [0, 36], box: { x: 56, y: 34, w: 470, h: 262 } });
    const g = [];
    g.push(f.axes({
        xTicks: [1, 2, 3, 4, 5, 6, 7, 8], yTicks: [10, 20, 30],
        xLabel: 'n', yLabel: '항의 값',
    }));

    const ar = n => 2 + 3 * (n - 1);
    const ge = n => 2 * Math.pow(1.5, n - 1);
    const ns = [1, 2, 3, 4, 5, 6, 7, 8];

    g.push(f.line(ns.map(n => [n, ar(n)]), { cls: 's1', dash: '5 4' }));
    g.push(f.line(ns.map(n => [n, ge(n)]), { cls: 's2', dash: '5 4' }));
    for (const n of ns) g.push(f.dot([n, ar(n)], { cls: 'f1', r: 4.5 }));
    for (const n of ns) g.push(f.dot([n, ge(n)], { cls: 'f2', r: 4.5 }));

    // 규칙은 두 점 사이에 적는다. 서로 겹치지 않는 자리만 골랐다.
    g.push(f.label([2.5, 6.5], '+3', { dy: -8, anchor: 'middle', cls: 'ink2', size: 'sm' }));
    g.push(f.label([5.5, ge(5.5)], '×1.5', { dy: 18, anchor: 'middle', cls: 'ink2', size: 'sm' }));

    g.push(legend(f.X(1.15), f.Y(34.2), [
        { slot: 1, name: '등차수열  a~n = 2 + 3(n − 1)' },
        { slot: 2, name: '등비수열  b~n = 2 × 1.5ⁿ⁻¹' },
    ]));

    // 두 수열이 갈리는 자리
    g.push(f.guide([7.15, 0], [7.15, 30]));
    g.push(f.label([7.15, 30.6], '여기서 앞지른다', { dx: -6, anchor: 'end', cls: 'ink2', size: 'sm' }));

    g.push(txt(56, 330, '등차는 같은 값을 더하므로 점이 직선 위에 놓인다. 등비는 같은 값을 곱하므로 처음에는 뒤처지다가', { cls: 'ink2', size: 'sm' }));
    g.push(txt(56, 348, '어느 지점부터 걷잡을 수 없이 벌어진다. 공비가 1 보다 크면 이 추월은 반드시 일어난다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'math-ser-arith-geom',
        svg: svg({
            width: W, height: H,
            title: '등차수열과 등비수열의 항이 자라는 모양',
            desc: '같은 값을 더하는 등차수열의 점은 직선 위에 놓이고, 같은 값을 곱하는 등비수열의 점은 처음에 뒤처졌다가 결국 앞지른다',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-B. 1 + 2 + 4 + … + 2ⁿ⁻¹ = 2ⁿ − 1
 * ================================================================== */
add((() => {
    const W = 620, H = 330;
    const g = [];
    const U = 16;              // 1 을 나타내는 너비(화소)
    const x0 = 46;
    const S1 = 'var(--s1)', S2 = 'var(--s2)';

    // 위 — 막대 하나로 그린 2⁵
    const topY = 62, barH = 38;
    g.push(box(x0, topY, 32 * U, barH, { fill: S2, op: 0.16, stroke: S2, sw: 1.8 }));
    g.push(txt(x0 + 16 * U, topY + 25, '2⁵ = 32', { anchor: 'middle', cls: 'ink bold' }));
    g.push(txt(x0, topY - 12, '다음 막대 하나', { cls: 'ink2', size: 'sm' }));

    // 아래 — 1, 2, 4, 8, 16 을 이어 붙인 것
    const botY = 152;
    let cx = x0;
    for (const v of [1, 2, 4, 8, 16]) {
        g.push(box(cx, botY, v * U, barH, { fill: S1, op: 0.16, stroke: S1, sw: 1.8 }));
        g.push(txt(cx + (v * U) / 2, botY + 25, String(v), { anchor: 'middle', cls: 'ink bold' }));
        cx += v * U;
    }
    g.push(txt(x0, botY - 12, '앞의 막대를 모두 이어 붙인 것 — 1 + 2 + 4 + 8 + 16 = 31', { cls: 'ink2', size: 'sm' }));

    // 모자라는 1 칸
    const gapX = x0 + 31 * U;
    g.push(box(gapX, botY, U, barH, { fill: 'var(--s3)', op: 0.3, stroke: 'var(--s3)', sw: 1.8 }));
    g.push(ln([[gapX + U / 2, botY + barH + 8], [gapX + U / 2, botY + barH + 30]], { stroke: 'var(--s3)', sw: 1.6 }));
    g.push(txt(gapX + U / 2, botY + barH + 46, '1 만큼 모자란다', { anchor: 'middle', cls: 'ink bold' }));

    // 두 막대의 오른쪽 끝을 잇는 보조선
    g.push(ln([[x0 + 32 * U, topY + barH], [x0 + 32 * U, botY]], { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }));
    g.push(ln([[gapX, topY + barH], [gapX, botY]], { stroke: 'var(--grid)', sw: 1.2, dash: '4 3' }));

    g.push(txt(46, 272, '1 + 2 + 4 + 8 + 16 = 2⁵ − 1', { cls: 'ink bold' }));
    g.push(txt(46, 296, '어느 항이든 그 앞의 모든 항을 합친 것보다 정확히 1 만큼 크다. 그래서 부분합은 다음 항에서 1 을 뺀 값이고,', { cls: 'ink2', size: 'sm' }));
    g.push(txt(46, 314, '배열을 두 배씩 늘리며 옮긴 총 횟수가 마지막 크기의 두 배를 넘지 못하는 이유도 이 그림 하나다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'math-ser-doubling',
        svg: svg({
            width: W, height: H,
            title: '두 배씩 늘어나는 항의 합은 다음 항보다 1 작다',
            desc: '길이 1, 2, 4, 8, 16 인 막대를 이어 붙이면 길이 32 인 막대 하나에 1 만큼 모자란다는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

/* ================================================================== *
 * 9-C. 1/2 + 1/4 + 1/8 + … = 1 을 정사각형으로
 * ================================================================== */
add((() => {
    const W = 620, H = 340;
    const g = [];
    const L = 250;                    // 한 변
    const ox = 46, oy = 40;
    const S = ['var(--s1)', 'var(--s2)', 'var(--s3)'];

    // 조각을 차례로 잘라 나간다. 남은 직사각형의 절반을 떼는 것을 되풀이한다.
    let rx = ox, ry = oy, rw = L, rh = L;
    let vertical = true;              // 세로로 자를 차례인가
    const names = ['1/2', '1/4', '1/8', '1/16', '1/32', '1/64'];
    names.forEach((name, i) => {
        let px, py, pw, ph;
        if (vertical) {
            px = rx; py = ry; pw = rw / 2; ph = rh;
            rx = rx + rw / 2; rw = rw / 2;
        } else {
            px = rx; py = ry + rh / 2; pw = rw; ph = rh / 2;
            rh = rh / 2;
        }
        vertical = !vertical;
        g.push(box(px, py, pw, ph, { fill: S[i % 3], op: 0.2, stroke: S[i % 3], sw: 1.4 }));
        if (i <= 3) g.push(txt(px + pw / 2, py + ph / 2 + 5, name, { anchor: 'middle', cls: 'ink bold' }));
    });
    // 마지막에 남은 빈칸
    g.push(box(rx, ry, rw, rh, { fill: 'none', stroke: 'var(--ink2)', sw: 1.4 }));
    g.push(box(ox, oy, L, L, { fill: 'none', stroke: 'var(--ink2)', sw: 2 }));
    g.push(txt(ox + L / 2, oy - 12, '넓이가 1 인 정사각형', { anchor: 'middle', cls: 'ink2', size: 'sm' }));

    // 오른쪽 설명
    const tx = ox + L + 40;
    g.push(txt(tx, oy + 26, '조각을 넣을 때마다', { cls: 'ink bold' }));
    g.push(txt(tx, oy + 48, '남은 빈칸의 넓이가', { cls: 'ink bold' }));
    g.push(txt(tx, oy + 70, '방금 넣은 조각과 같다.', { cls: 'ink bold' }));
    g.push(txt(tx, oy + 104, 'S~n = 1 − (1/2)ⁿ', { cls: 'ink' }));
    g.push(txt(tx, oy + 128, '빈칸이 0 으로 가므로', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx, oy + 146, '부분합은 1 로 간다.', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx, oy + 180, '공비가 1 보다 작을 때만', { cls: 'ink2', size: 'sm' }));
    g.push(txt(tx, oy + 198, '이 그림을 그릴 수 있다.', { cls: 'ink2', size: 'sm' }));

    g.push(txt(46, 316, '무한히 더해도 정사각형을 넘지 않는다. 넘칠 수 없다는 것이 눈에 보이는 것이 이 그림의 값어치다.', { cls: 'ink2', size: 'sm' }));

    return {
        name: 'math-ser-half-square',
        svg: svg({
            width: W, height: H,
            title: '1/2 + 1/4 + 1/8 + … 은 정사각형 하나를 채운다',
            desc: '넓이 1 인 정사각형을 절반씩 잘라 채워 가면 남은 빈칸이 언제나 방금 넣은 조각과 같다는 것을 보이는 그림',
            body: g.join(''),
        }),
    };
})());

export default figures;
