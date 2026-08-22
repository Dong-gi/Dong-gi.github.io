/**
 * 브라우저가 실행 시점에 만드는 앵커(`pos-span`)를 빌드 시점에 미리 계산한다.
 *
 * `source/default.js` 의 `updateMarkerList()` 는 페이지가 열릴 때
 * `h1~h6` 과 `.marker`(표·이미지·details) 앞에 `<span class="pos-span" id="pos<해시>">`
 * 를 심는다. 즉 **헤딩 텍스트와 같은 이름의 앵커는 이미 공짜로 생긴다.**
 *
 * 그래서 두 가지가 필요하다.
 *
 *   1. 헤딩과 같은 이름의 `+pos()` 는 없어도 된다 — `scripts/prune-pos.mjs` 가 지운다
 *   2. 링크 검사기가 그 앵커를 알아야 한다 — 모르면 멀쩡한 링크를 깨졌다고 한다
 *
 * 이 파일이 그 계산을 한곳에 둔다. **`default.js` 의 `makeMarkerName` /
 * `stringHashCode` 와 같은 결과를 내야 한다.** 한쪽만 고치면 검사기가 거짓말을 시작한다.
 */

/** skeleton.pug 의 String.prototype.hashCode, default.js 의 stringHashCode 와 같아야 한다. */
export function hashCode(s) {
    let h = 0;
    for (let i = 0; i < s.length; i += 1) {
        h = (h << 5) - h + s.charCodeAt(i);
        h |= 0;
    }
    return h;
}

/** DOM 의 텍스트 노드는 엔티티가 풀린 상태다. 같은 상태로 맞춘다. */
function decodeEntities(s) {
    return s
        .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
        .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
        .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
        .replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&');
}

/**
 * 요소의 **직계** 텍스트 노드 중 첫 번째로 비어 있지 않은 것.
 *
 * `makeMarkerName` 의 기본 갈래가 이것이다. 중첩된 태그 안의 글자는 세지 않는다 —
 * `<h2><b>굵게</b> 나머지</h2>` 의 마커 이름은 ` 나머지` 이지 `굵게 나머지` 가 아니다.
 * 길이만 보고 자르므로(`length !== 0`) 공백 한 칸도 이름이 된다. 원본이 그렇다.
 */
function firstDirectText(innerHtml) {
    let depth = 0;
    let buf = '';
    for (let i = 0; i < innerHtml.length; i += 1) {
        const c = innerHtml[i];
        if (c === '<') {
            if (depth === 0 && buf.length !== 0) return decodeEntities(buf);
            const close = innerHtml.indexOf('>', i);
            if (close === -1) break;
            const tag = innerHtml.slice(i, close + 1);
            if (tag.startsWith('</')) depth -= 1;
            else if (!tag.endsWith('/>') && !/^<(br|img|hr|input|meta|link)\b/i.test(tag)) depth += 1;
            i = close;
            buf = '';
            continue;
        }
        if (depth === 0) buf += c;
    }
    return buf.length !== 0 ? decodeEntities(buf) : null;
}

/** 태그를 모두 걷어낸 글자. `textContent` 폴백에 쓴다. */
function allText(innerHtml) {
    return decodeEntities(innerHtml.replace(/<[^>]*>/g, ''));
}

/**
 * 페이지 HTML 에서 실행 시점에 생길 앵커 id 를 전부 구한다.
 *
 * 마커 이름이 겹치면 두 번째부터 `-1`, `-2` 가 붙는다(`updateMarkerList` 와 같다).
 * 따라서 `+pos(이름)` 이 만드는 것과 같은 id 는 **그 이름의 첫 마커**뿐이다.
 */
export function runtimeMarkerIds(html) {
    const ids = new Set();
    const counter = new Map();
    // 헤딩과 .marker 를 문서 순서대로. no-marker 는 제외된다.
    const re = /<(h[1-6])\b([^>]*)>([\s\S]*?)<\/\1>|<(table|img|details)\b([^>]*)>/gi;
    for (const m of html.matchAll(re)) {
        const attrs = (m[2] ?? m[5] ?? '');
        if (/\bclass="[^"]*\bno-marker\b/.test(attrs)) continue;
        let name;
        if (m[1]) {
            name = firstDirectText(m[3]) ?? allText(m[3]);
        } else {
            // 표·이미지·details 는 `.marker` 인 것만 대상이다.
            if (!/\bclass="[^"]*\bmarker\b/.test(attrs)) continue;
            // 이름은 캡션에 달려 있어 이 자리에서 정확히 못 구한다. 세기만 하고 넘어간다.
            continue;
        }
        if (name == null) continue;
        let id = `pos${hashCode(name)}`;
        const seen = counter.get(name);
        if (seen != null) id += `-${seen}`;
        counter.set(name, (seen ?? 0) + 1);
        ids.add(id);
    }
    return ids;
}
