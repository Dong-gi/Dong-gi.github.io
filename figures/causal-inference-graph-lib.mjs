/**
 * 인과 그래프를 그리는 공통 함수. 원장 §5.2 의 규약을 코드로 굳힌 것이다.
 *
 * 모듈마다 그래프 그리는 코드를 베끼면 규약이 갈라진다. 노드 모양·화살표·상자·
 * 점선의 뜻이 그림마다 달라지면 독자가 그림마다 규칙을 새로 배워야 한다.
 *
 * 규약 (원장 §5.2)
 *   - 처치는 왼쪽, 결과는 오른쪽
 *   - 통제한 변수는 상자로 둘러싼다. 통제하지 않은 것은 맨 노드
 *   - 열린 경로는 실선, 막힌 경로는 회색 점선
 *   - 관측되지 않은 변수는 점선 원
 *   - 노드 이름은 짧은 한국어. SVG 에는 수식을 쓸 수 없다
 *
 * 이 파일은 그림을 내보내지 않는다. figure.ts 가 `causal-inference-*.mjs` 의
 * default 를 모두 읽으므로 빈 배열을 둔다.
 */
import { esc } from './lib.mjs';

/** 노드 반지름. 이름이 길면 타원으로 늘린다. */
export const R = 26;

const round = (v) => Number.parseFloat(v.toFixed(2));

/**
 * 노드 하나.
 *
 * @param {number} x 중심
 * @param {number} y 중심
 * @param {string} label 짧은 한국어. 두 줄이 필요하면 '\n' 으로 나눈다
 * @param {object} [o]
 * @param {boolean} [o.boxed]      통제한 변수 — 상자로 둘러싼다
 * @param {boolean} [o.unobserved] 관측되지 않은 변수 — 점선 원
 * @param {'x'|'y'|'plain'} [o.role] 처치·결과·그 밖. 색을 정한다
 * @param {number} [o.rx] 가로 반지름 (기본 R, 이름이 길면 늘린다)
 */
export function node(x, y, label, { boxed = false, unobserved = false, role = 'plain', rx } = {}) {
    const lines = String(label).split('\n');
    const w = rx ?? Math.max(R, 7.2 * Math.max(...lines.map((l) => l.length)) + 10);
    const h = R;
    const stroke = role === 'x' ? 'var(--s1)' : role === 'y' ? 'var(--s2)' : 'var(--ink2)';
    const fill = role === 'x' ? 'var(--s1)' : role === 'y' ? 'var(--s2)' : 'var(--ink)';
    const dash = unobserved ? ' stroke-dasharray="5 4"' : '';
    const shape = `<ellipse cx="${round(x)}" cy="${round(y)}" rx="${round(w)}" ry="${h}"`
        + ` fill="none" stroke="${stroke}" stroke-width="1.8"${dash}/>`;
    // 통제 표시 — 규약상 상자다. 원보다 넉넉히 잡아 겹치지 않게 한다.
    const box = boxed
        ? `<rect x="${round(x - w - 9)}" y="${round(y - h - 9)}" width="${round((w + 9) * 2)}"`
          + ` height="${round((h + 9) * 2)}" rx="4" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`
        : '';
    const dy = lines.length === 1 ? 4.5 : -3;
    const text = lines.map((l, i) =>
        `<tspan x="${round(x)}" dy="${i === 0 ? dy : 15}">${esc(l)}</tspan>`).join('');
    return box + shape
        + `<text x="${round(x)}" y="${round(y)}" text-anchor="middle" fill="${fill}"`
        + ` style="font-size:13px">${text}</text>`;
}

/** 노드 테두리에서 멈추도록 두 중심 사이 선분을 줄인다. */
function trim(x1, y1, x2, y2, r1, r2) {
    const dx = x2 - x1, dy = y2 - y1;
    const d = Math.hypot(dx, dy) || 1;
    return [x1 + (dx / d) * r1, y1 + (dy / d) * r1, x2 - (dx / d) * r2, y2 - (dy / d) * r2];
}

/**
 * 인과 화살표. 원장 §5.2 — 열린 경로는 실선, 막힌 경로는 회색 점선.
 *
 * @param {object} [o]
 * @param {boolean} [o.blocked] 막힌 경로 — 회색 점선
 * @param {'s1'|'s2'|'s3'|'ink2'} [o.tone] 색. 기본은 회색
 * @param {number} [o.from] 출발 노드의 가로 반지름 (기본 R)
 * @param {number} [o.to]   도착 노드의 가로 반지름 (기본 R)
 * @param {number} [o.bow]  0 이 아니면 그만큼 휜다. 노드를 피해 갈 때
 */
export function edge(x1, y1, x2, y2, { blocked = false, tone = 'ink2', from = R, to = R, bow = 0 } = {}) {
    const [ax, ay, bx, by] = trim(x1, y1, x2, y2, from, to + 8);
    const color = blocked ? 'var(--grid)' : {
        s1: 'var(--s1)', s2: 'var(--s2)', s3: 'var(--s3)', ink2: 'var(--ink2)',
    }[tone];
    const marker = blocked ? 'ark' : { s1: 'ar1', s2: 'ar2', s3: 'ar3', ink2: 'ark' }[tone];
    const d = bow === 0
        ? `M${round(ax)} ${round(ay)} L${round(bx)} ${round(by)}`
        : `M${round(ax)} ${round(ay)} Q${round((ax + bx) / 2)} ${round((ay + by) / 2 + bow)} ${round(bx)} ${round(by)}`;
    return `<path d="${d}" fill="none" stroke="${color}" stroke-width="${blocked ? 1.6 : 2}"`
        + `${blocked ? ' stroke-dasharray="6 5"' : ''} stroke-linecap="round" marker-end="url(#${marker})"/>`;
}

/** 화살표 옆에 붙이는 짧은 딱지. */
export function tag(x, y, text, { anchor = 'middle' } = {}) {
    return `<text x="${round(x)}" y="${round(y)}" text-anchor="${anchor}" class="sm ink2">${esc(text)}</text>`;
}

/** 그림 안의 작은 제목. 한 그림에 여러 경우를 나란히 놓을 때 각 칸의 이름. */
export function caseTitle(x, y, text) {
    return `<text x="${round(x)}" y="${round(y)}" text-anchor="middle" class="sm bold ink">${esc(text)}</text>`;
}

/** 경우를 가르는 옅은 칸. */
export function panel(x, y, w, h) {
    return `<rect x="${round(x)}" y="${round(y)}" width="${round(w)}" height="${round(h)}"`
        + ` rx="6" fill="none" stroke="var(--grid)" stroke-width="1"/>`;
}

/**
 * 범례. 색만으로 구분하지 않도록 이름을 함께 적는다(규격 §4).
 * items: [{ kind: 'open'|'blocked'|'boxed'|'unobserved', name: string }]
 */
export function key(x, y, items) {
    return items.map((it, i) => {
        const yy = y + i * 19;
        let mark;
        if (it.kind === 'open') mark = `<path d="M${x} ${yy - 4} L${x + 26} ${yy - 4}" stroke="var(--ink2)" stroke-width="2" fill="none" marker-end="url(#ark)"/>`;
        else if (it.kind === 'blocked') mark = `<path d="M${x} ${yy - 4} L${x + 26} ${yy - 4}" stroke="var(--grid)" stroke-width="1.6" stroke-dasharray="6 5" fill="none" marker-end="url(#ark)"/>`;
        else if (it.kind === 'boxed') mark = `<rect x="${x + 4}" y="${yy - 11}" width="18" height="14" rx="3" fill="none" stroke="var(--ink2)" stroke-width="1.6"/>`;
        else mark = `<ellipse cx="${x + 13}" cy="${yy - 4}" rx="11" ry="8" fill="none" stroke="var(--ink2)" stroke-width="1.6" stroke-dasharray="5 4"/>`;
        return mark + `<text x="${x + 34}" y="${yy}" class="sm ink2">${esc(it.name)}</text>`;
    }).join('');
}

export default [];
