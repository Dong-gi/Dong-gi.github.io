/**
 * 교재용 그림을 SVG 문자열로 만드는 최소 라이브러리.
 *
 * 왜 직접 만드나
 *   D2 는 상자와 화살표에는 좋지만 좌표계 위의 곡선·벡터·각도를 그리지 못한다.
 *   그렇다고 플로팅 라이브러리를 들이면 빌드 의존성이 늘고, 산출물이 이 사이트의
 *   나머지와 따로 놀게 된다. 필요한 도형이 축·곡선·화살표·라벨 네 가지뿐이라
 *   의존성 없이 문자열로 뽑는 편이 가볍고 결과를 통제하기 쉽다.
 *
 * 제약
 *   그림은 +w3img 를 통해 <img src="...svg"> 로 삽입된다. 즉 페이지의 CSS 도
 *   JavaScript 도 SVG 안에 닿지 않는다. 그래서 스타일은 SVG 안에 직접 넣고,
 *   다크 모드도 SVG 내부의 미디어 쿼리로 처리한다. 라벨에 수식을 쓸 수 없으므로
 *   (MathJax 가 관여할 수 없다) 유니코드 첨자·그리스 문자로 적는다.
 *
 * 색
 *   dataviz 스킬의 검증된 기본 팔레트를 쓴다. 밝은 배경 기준으로
 *   #2a78d6 / #eb6834 / #1baf7a 조합이 색각 이상 분리도와 명도 검사를 통과한다.
 *   세 번째 색은 배경 대비가 3:1 미만이라 반드시 직접 라벨을 붙여야 한다.
 */

export const PALETTE = {
    light: {
        s1: '#2a78d6', s2: '#eb6834', s3: '#1baf7a',
        ink: '#0b0b0b', ink2: '#52514e', grid: '#d8d7d2',
    },
    dark: {
        s1: '#3987e5', s2: '#d95926', s3: '#199e70',
        ink: '#ffffff', ink2: '#c3c2b7', grid: '#3a3a38',
    },
};

const FONT = "'Segoe UI', 'Noto Sans KR', system-ui, sans-serif";

/** 소수점 찌꺼기를 줄여 SVG 를 작게 유지한다. */
const n = v => Number.parseFloat(v.toFixed(2));

const escOnly = s => String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

/**
 * 텍스트를 SVG 로 안전하게 바꾼다. 유니코드에 없는 아래첨자(y, k 등)까지 쓰려면
 * 문자를 골라 쓸 수 없으므로 tspan 으로 내린다. 표기는 `v~0`, `A~{xy}`.
 * 먼저 이스케이프한 뒤 마커를 치환하므로 본문에 <, & 가 있어도 안전하다.
 */
const esc = s => escOnly(s)
    .replace(/~\{([^}]*)\}/g, (_, t) => `<tspan baseline-shift="-22%" font-size="76%">${t}</tspan>`)
    .replace(/~(.)/g, (_, t) => `<tspan baseline-shift="-22%" font-size="76%">${t}</tspan>`);

/**
 * 완성된 SVG 문서를 만든다.
 * @param {{width:number,height:number,title:string,desc?:string,body:string}} o
 */
export function svg({ width, height, title, desc, body }) {
    const L = PALETTE.light;
    const D = PALETTE.dark;
    const vars = p => Object.entries(p).map(([k, v]) => `--${k}:${v}`).join(';');
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img" aria-labelledby="t d" font-family="${FONT}">
<title id="t">${esc(title)}</title><desc id="d">${esc(desc ?? title)}</desc>
<style>
svg{${vars(L)}}
@media (prefers-color-scheme:dark){svg{${vars(D)}}}
.ink{fill:var(--ink)}.ink2{fill:var(--ink2)}
.ax{stroke:var(--ink2);stroke-width:1.5;fill:none}
.gr{stroke:var(--grid);stroke-width:1;fill:none}
.s1{stroke:var(--s1)}.s2{stroke:var(--s2)}.s3{stroke:var(--s3)}
.f1{fill:var(--s1)}.f2{fill:var(--s2)}.f3{fill:var(--s3)}
.cv{fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
text{font-size:13px}
.sm{font-size:11px}
.bold{font-weight:600}
</style>
<defs>
<marker id="ar1" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="7" markerHeight="6" orient="auto-start-reverse"><path d="M0 0 L10 4 L0 8 z" fill="var(--s1)"/></marker>
<marker id="ar2" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="7" markerHeight="6" orient="auto-start-reverse"><path d="M0 0 L10 4 L0 8 z" fill="var(--s2)"/></marker>
<marker id="ar3" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="7" markerHeight="6" orient="auto-start-reverse"><path d="M0 0 L10 4 L0 8 z" fill="var(--s3)"/></marker>
<marker id="ark" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="7" markerHeight="6" orient="auto-start-reverse"><path d="M0 0 L10 4 L0 8 z" fill="var(--ink2)"/></marker>
</defs>
${body}
</svg>`;
}

/**
 * 좌표계를 만든다. 데이터 좌표 → 화소 좌표 변환기와 축 그리기를 함께 돌려준다.
 *
 * @param {object} o
 * @param {[number,number]} o.xRange 데이터 x 범위
 * @param {[number,number]} o.yRange 데이터 y 범위
 * @param {{x:number,y:number,w:number,h:number}} o.box 화소 기준 그리기 영역
 */
export function frame({ xRange, yRange, box }) {
    const [x0, x1] = xRange;
    const [y0, y1] = yRange;
    const X = v => n(box.x + ((v - x0) / (x1 - x0)) * box.w);
    const Y = v => n(box.y + box.h - ((v - y0) / (y1 - y0)) * box.h);

    /** 축 두 개와 눈금·축 이름. 눈금은 데이터 값 배열로 준다. */
    function axes({ xLabel, yLabel, xTicks = [], yTicks = [], grid = true }) {
        const out = [];
        if (grid) {
            for (const t of xTicks) out.push(`<path class="gr" d="M${X(t)} ${Y(y0)} V${Y(y1)}"/>`);
            for (const t of yTicks) out.push(`<path class="gr" d="M${X(x0)} ${Y(t)} H${X(x1)}"/>`);
        }
        // 축은 데이터 0 이 범위 안에 있으면 0 에, 아니면 가장자리에 둔다.
        const ax = y0 <= 0 && 0 <= y1 ? 0 : y0;
        const ay = x0 <= 0 && 0 <= x1 ? 0 : x0;
        out.push(`<path class="ax" marker-end="url(#ark)" d="M${X(x0)} ${Y(ax)} H${X(x1) + 10}"/>`);
        out.push(`<path class="ax" marker-end="url(#ark)" d="M${X(ay)} ${Y(y0)} V${Y(y1) - 10}"/>`);
        for (const t of xTicks) {
            if (t === ay) continue;
            out.push(`<text class="sm ink2" x="${X(t)}" y="${Y(ax) + 15}" text-anchor="middle">${esc(t)}</text>`);
        }
        for (const t of yTicks) {
            if (t === ax) continue;
            out.push(`<text class="sm ink2" x="${X(ay) - 6}" y="${Y(t) + 4}" text-anchor="end">${esc(t)}</text>`);
        }
        if (xLabel) out.push(`<text class="sm ink2" x="${X(x1) + 14}" y="${Y(ax) + 4}">${esc(xLabel)}</text>`);
        if (yLabel) out.push(`<text class="sm ink2" x="${X(ay)}" y="${Y(y1) - 18}" text-anchor="middle">${esc(yLabel)}</text>`);
        return out.join('');
    }

    /** 함수 곡선. f 는 데이터 x → 데이터 y. */
    function curve(f, { from = x0, to = x1, cls = 's1', dash, steps = 120 } = {}) {
        const pts = [];
        for (let i = 0; i <= steps; i += 1) {
            const xv = from + ((to - from) * i) / steps;
            pts.push(`${X(xv)} ${Y(f(xv))}`);
        }
        return `<path class="cv ${cls}" ${dash ? `stroke-dasharray="${dash}" ` : ''}d="M${pts.join(' L')}"/>`;
    }

    /** 점들을 잇는 꺾은선. */
    function line(points, { cls = 's1', dash } = {}) {
        const d = points.map(([a, b]) => `${X(a)} ${Y(b)}`).join(' L');
        return `<path class="cv ${cls}" ${dash ? `stroke-dasharray="${dash}" ` : ''}d="M${d}"/>`;
    }

    /** 데이터 좌표 화살표(벡터). */
    function vector(p1, p2, { cls = 's1', marker = 'ar1', width = 2.5 } = {}) {
        return `<path class="${cls}" fill="none" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})" d="M${X(p1[0])} ${Y(p1[1])} L${X(p2[0])} ${Y(p2[1])}"/>`;
    }

    function dot(p, { cls = 'f1', r = 3.5 } = {}) {
        return `<circle class="${cls}" cx="${X(p[0])}" cy="${Y(p[1])}" r="${r}"/>`;
    }

    /** 데이터 좌표에 라벨. dx/dy 는 화소 단위 미세 조정. */
    function label(p, str, { dx = 0, dy = 0, anchor = 'start', cls = 'ink', size } = {}) {
        return `<text class="${cls}${size === 'sm' ? ' sm' : ''}" x="${n(X(p[0]) + dx)}" y="${n(Y(p[1]) + dy)}" text-anchor="${anchor}">${esc(str)}</text>`;
    }

    /** 보조선(점선). */
    function guide(p1, p2) {
        return `<path class="gr" stroke-dasharray="4 3" d="M${X(p1[0])} ${Y(p1[1])} L${X(p2[0])} ${Y(p2[1])}"/>`;
    }

    return { X, Y, axes, curve, line, vector, dot, label, guide };
}

/** 화소 좌표에서의 각도호. 각은 도(degree), 수학 관례대로 반시계 방향. */
export function arc(cx, cy, r, a1, a2, label, { cls = 'gr' } = {}) {
    const rad = a => (a * Math.PI) / 180;
    const p = a => `${n(cx + r * Math.cos(rad(a)))} ${n(cy - r * Math.sin(rad(a)))}`;
    const large = Math.abs(a2 - a1) > 180 ? 1 : 0;
    const mid = (a1 + a2) / 2;
    const lx = n(cx + (r + 14) * Math.cos(rad(mid)));
    const ly = n(cy - (r + 14) * Math.sin(rad(mid)) + 4);
    return `<path class="${cls}" d="M${p(a1)} A${r} ${r} 0 ${large} 0 ${p(a2)}"/>`
        + (label ? `<text class="sm ink2" x="${lx}" y="${ly}" text-anchor="middle">${esc(label)}</text>` : '');
}

/** 화소 좌표 직선 화살표. 자유물체도처럼 좌표계가 없는 그림에 쓴다. */
export function px(x1, y1, x2, y2, { cls = 's1', marker = 'ar1', width = 2.5, dash } = {}) {
    return `<path class="${cls}" fill="none" stroke-width="${width}" stroke-linecap="round" marker-end="url(#${marker})"${dash ? ` stroke-dasharray="${dash}"` : ''} d="M${n(x1)} ${n(y1)} L${n(x2)} ${n(y2)}"/>`;
}

/** 화소 좌표 텍스트. */
export function txt(x, y, str, { anchor = 'start', cls = 'ink', size } = {}) {
    return `<text class="${cls}${size === 'sm' ? ' sm' : ''}" x="${n(x)}" y="${n(y)}" text-anchor="${anchor}">${esc(str)}</text>`;
}

/** 여러 계열을 쓸 때의 범례. 색만으로 구분하지 않도록 항상 이름을 함께 적는다. */
export function legend(x, y, items) {
    return items.map((it, i) => {
        const yy = y + i * 18;
        return `<rect x="${x}" y="${yy - 8}" width="14" height="3" rx="1.5" class="f${it.slot}"/>`
            + `<text class="sm ink2" x="${x + 20}" y="${yy - 2}">${esc(it.name)}</text>`;
    }).join('');
}

export { n, esc };
