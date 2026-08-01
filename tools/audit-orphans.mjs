/**
 * 고아 HTML 감사 스크립트
 *
 * posts/ 하위 HTML을 아래 세 부류로 분류한다.
 *   - live    : 대응하는 pugs/*.pug 소스가 존재 (현행 문서)
 *   - orphan  : pug 소스도 없고 source/posts.json 색인에도 없음 (구 체계 잔재)
 *
 * 각 orphan에 대해 live 문서 중 후계 후보를 찾아 신뢰도와 함께 보고한다.
 * 부수 효과 없음 — 읽기 전용. 결과는 JSON으로 stdout 없이 파일에 기록한다.
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2];
if (!ROOT) {
    throw new Error('사용법: node audit-orphans.mjs <블로그 루트 경로> <출력 JSON 경로>');
}
const OUT = process.argv[3] ?? path.join(ROOT, 'orphan-audit.json');

const POSTS_DIR = path.join(ROOT, 'posts');
const PUGS_DIR = path.join(ROOT, 'pugs');

/** 디렉터리를 재귀 순회하며 확장자가 일치하는 파일의 상대 경로를 반환한다. */
function walk(dir, ext, base = dir) {
    const result = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            result.push(...walk(full, ext, base));
        } else if (entry.isFile() && entry.name.endsWith(ext)) {
            result.push(path.relative(base, full).split(path.sep).join('/'));
        }
    }
    return result;
}

// ---------------------------------------------------------------- 인벤토리

const htmlRels = walk(POSTS_DIR, '.html'); // 예: 'dev/aws.html'
const pugKeys = new Set(walk(PUGS_DIR, '.pug').map((p) => p.replace(/\.pug$/, '')));
const indexed = new Map(); // 'dev/aws.html' -> { category, title }
for (const post of JSON.parse(fs.readFileSync(path.join(ROOT, 'source/posts.json'), 'utf8')).list) {
    indexed.set(post.file, post);
}

/** 이미 리다이렉트 스텁으로 치환된 문서인지 판정한다. */
const REDIRECT_MARK = 'data-redirect-stub';

/** HTML에서 제목·키워드·본문 텍스트를 추출한다. 정규식 기반의 경량 파서. */
function extract(html) {
    const title = (html.match(/<title>([\s\S]*?)<\/title>/i)?.[1] ?? '').trim();
    const keywords = (html.match(/<meta\s+name="keywords"\s+content="([^"]*)"/i)?.[1] ?? '').trim();
    const description = (html.match(/<meta\s+name="description"\s+content="([^"]*)"/i)?.[1] ?? '').trim();
    const body = html
        .replace(/<script[\s\S]*?<\/script>/gi, ' ')
        .replace(/<style[\s\S]*?<\/style>/gi, ' ')
        .replace(/<head[\s\S]*?<\/head>/gi, ' ')
        .replace(/<[^>]+>/g, ' ')
        .replace(/&[a-z]+;|&#\d+;/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    return { title, keywords, description, body };
}

const docs = new Map(); // rel -> { rel, kind, title, keywords, description, body, tokens }
for (const rel of htmlRels) {
    const html = fs.readFileSync(path.join(POSTS_DIR, rel), 'utf8');
    const key = rel.replace(/\.html$/, '');
    const meta = extract(html);
    docs.set(rel, {
        rel,
        key,
        kind: pugKeys.has(key) ? 'live' : 'orphan',
        indexed: indexed.has(rel),
        alreadyStub: html.includes(REDIRECT_MARK),
        bytes: Buffer.byteLength(html),
        ...meta,
    });
}

const orphans = [...docs.values()].filter((d) => d.kind === 'orphan' && !d.alreadyStub);
const lives = [...docs.values()].filter((d) => d.kind === 'live');

// ---------------------------------------------------------------- 토큰화 / TF-IDF

/** 한글·영숫자 토큰으로 분해하고 소문자화한다. */
function tokenize(text) {
    return (text.toLowerCase().match(/[a-z0-9]{2,}|[가-힣]{2,}/g) ?? []).slice(0, 4000);
}

/** 토큰 배열을 term -> 빈도 맵으로 만든다. */
function termFreq(tokens) {
    const tf = new Map();
    for (const t of tokens) tf.set(t, (tf.get(t) ?? 0) + 1);
    return tf;
}

const corpus = [...orphans, ...lives];
for (const d of corpus) {
    d.tf = termFreq(tokenize(`${d.title} ${d.keywords} ${d.description} ${d.body}`));
}

const df = new Map();
for (const d of corpus) for (const term of d.tf.keys()) df.set(term, (df.get(term) ?? 0) + 1);

const N = corpus.length;
/** TF-IDF 가중 벡터를 만들고 L2 정규화한다. */
function vectorize(d) {
    const vec = new Map();
    let norm = 0;
    for (const [term, freq] of d.tf) {
        const w = (1 + Math.log(freq)) * Math.log(N / (df.get(term) ?? 1) + 1);
        vec.set(term, w);
        norm += w * w;
    }
    norm = Math.sqrt(norm) || 1;
    for (const [term, w] of vec) vec.set(term, w / norm);
    return vec;
}
for (const d of corpus) d.vec = vectorize(d);

/** 두 정규화 벡터의 코사인 유사도. 짧은 쪽을 순회한다. */
function cosine(a, b) {
    const [small, large] = a.size <= b.size ? [a, b] : [b, a];
    let sum = 0;
    for (const [term, w] of small) {
        const w2 = large.get(term);
        if (w2 !== undefined) sum += w * w2;
    }
    return sum;
}

// ---------------------------------------------------------------- 매칭

/** 경로에서 파일명(확장자 제외)을 뽑는다. */
const baseOf = (rel) => path.posix.basename(rel, '.html');

/** 제목 정규화 — 공백/대소문자/구두점 차이를 흡수한다. */
const normTitle = (t) => t.toLowerCase().replace(/[^a-z0-9가-힣]/g, '');

const liveByBase = new Map();
for (const l of lives) {
    const b = baseOf(l.rel).toLowerCase();
    if (!liveByBase.has(b)) liveByBase.set(b, []);
    liveByBase.get(b).push(l);
}
const liveByTitle = new Map();
for (const l of lives) {
    const t = normTitle(l.title);
    if (!t) continue;
    if (!liveByTitle.has(t)) liveByTitle.set(t, []);
    liveByTitle.get(t).push(l);
}

const results = [];
for (const o of orphans) {
    // 본문 유사도 상위 후보
    const scored = lives
        .map((l) => ({ target: l.rel, targetTitle: l.title, cosine: +cosine(o.vec, l.vec).toFixed(4) }))
        .sort((a, b) => b.cosine - a.cosine)
        .slice(0, 3);

    const baseHits = liveByBase.get(baseOf(o.rel).toLowerCase()) ?? [];
    const titleHits = normTitle(o.title) ? (liveByTitle.get(normTitle(o.title)) ?? []) : [];

    const signals = [];
    if (baseHits.length === 1) signals.push('basename');
    if (titleHits.length === 1) signals.push('title');

    // 후보 결정: 파일명/제목 단일 일치가 최우선, 없으면 본문 유사도 1위
    let best = null;
    if (baseHits.length === 1) best = baseHits[0].rel;
    else if (titleHits.length === 1) best = titleHits[0].rel;
    else if (scored.length) best = scored[0].target;

    const bestCos = scored.find((s) => s.target === best)?.cosine ?? +cosine(o.vec, docs.get(best)?.vec ?? new Map()).toFixed(4);
    const runnerUp = scored.find((s) => s.target !== best)?.cosine ?? 0;
    const margin = +(bestCos - runnerUp).toFixed(4);

    // 신뢰도 판정
    let confidence;
    if (signals.length === 2) confidence = 'high';
    else if (signals.length === 1 && bestCos >= 0.35) confidence = 'high';
    else if (signals.length === 1) confidence = 'medium';
    else if (bestCos >= 0.6 && margin >= 0.1) confidence = 'high';
    else if (bestCos >= 0.4) confidence = 'medium';
    else confidence = 'low';

    results.push({
        orphan: o.rel,
        orphanTitle: o.title,
        orphanBytes: o.bytes,
        target: best,
        targetTitle: docs.get(best)?.title ?? '',
        cosine: bestCos,
        margin,
        signals,
        confidence,
        top3: scored,
    });
}

results.sort((a, b) => a.orphan.localeCompare(b.orphan));

const summary = {
    totalHtml: htmlRels.length,
    live: lives.length,
    orphan: orphans.length,
    alreadyStub: [...docs.values()].filter((d) => d.alreadyStub).length,
    byConfidence: results.reduce((acc, r) => ((acc[r.confidence] = (acc[r.confidence] ?? 0) + 1), acc), {}),
};

fs.writeFileSync(OUT, JSON.stringify({ summary, results }, null, 2));
console.log(JSON.stringify(summary, null, 2));
