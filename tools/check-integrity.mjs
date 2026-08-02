/**
 * 사이트 정합성 검사 (읽기 전용)
 *
 * 빌드는 pug -> html 단방향 생성만 하므로, pug를 지워도 html이 남고
 * posts.json 과의 어긋남도 드러나지 않는다. 실제로 이 때문에 고아 HTML이
 * 355개까지 쌓인 적이 있다. 그 재발을 막기 위한 검사다.
 *
 * 검사 항목
 *   E1 posts.json 항목의 HTML 파일이 실제로 존재하는가
 *   E2 posts.json 항목에 대응하는 pug 소스가 존재하는가
 *   E3 posts.json 에 중복 file 항목이 없는가
 *   E4 pug 소스도 리다이렉트 스텁도 아니고 보존 목록에도 없는 고아 HTML이 없는가
 *   E5 리다이렉트 스텁의 3중 신호가 서로 일치하고, 대상이 존재하며, 체인/자기참조가 없는가
 *   E6 +codeBtn 이 가리키는 /Repositories/... 경로가 실제 파일인가 (디렉터리도 오류)
 *   E7 사이트맵의 모든 URL이 올바른 형식이고 실제 파일과 대응하는가
 *   W1 pug 소스는 있으나 posts.json 에 등록되지 않은 문서 (경고)
 *   W2 보존 목록에 있으나 파일이 사라진 항목 (경고)
 *   W4 source/doc-dates.json 에 날짜가 없는 pug (경고) — npm run dates 필요
 *
 * 사용법
 *   node tools/check-integrity.mjs [저장소 루트] [--strict]
 *   --strict 를 주면 경고도 실패로 처리한다.
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : '.';
const STRICT = process.argv.includes('--strict');

const SITE_ORIGIN = 'https://dong-gi.github.io';
const POSTS_URL_PREFIX = '/posts/';
const REDIRECT_MARK = 'data-redirect-stub';

const errors = [];
const warnings = [];

/** 디렉터리를 재귀 순회해 확장자가 맞는 파일의 상대 경로를 반환한다. */
function walk(dir, ext, base = dir) {
    if (!fs.existsSync(dir)) return [];
    const out = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) out.push(...walk(full, ext, base));
        else if (entry.name.endsWith(ext)) out.push(path.relative(base, full).split(path.sep).join('/'));
    }
    return out;
}

const rel = (...p) => path.join(ROOT, ...p);

// ---------------------------------------------------------------- 인벤토리 수집

const htmlRels = walk(rel('posts'), '.html');
const pugKeys = new Set(walk(rel('pugs'), '.pug').map((p) => p.replace(/\.pug$/, '')));
const posts = JSON.parse(fs.readFileSync(rel('source/posts.json'), 'utf8')).list;

/**
 * posts/<rel> 이 리다이렉트 스텁인지, 그렇다면 3중 신호가 각각 어디를 가리키는지.
 * meta refresh 하나만 봐서는 canonical 이나 JS 가 엇갈려도 알 수 없다.
 */
const stubTarget = new Map();
for (const r of htmlRels) {
    const html = fs.readFileSync(rel('posts', r), 'utf8');
    if (!html.includes(REDIRECT_MARK)) continue;
    stubTarget.set(r, {
        refresh: html.match(/<meta\s+http-equiv="refresh"\s+content="0;\s*url=\/posts\/([^"]+)"/)?.[1] ?? null,
        canonical: html.match(new RegExp(`<link\\s+rel="canonical"\\s+href="${SITE_ORIGIN}/posts/([^"]+)"`))?.[1] ?? null,
        script: html.match(/location\.replace\("\/posts\/([^"]+)"/)?.[1] ?? null,
    });
}

// ---------------------------------------------------------------- E1~E3 posts.json

const seen = new Set();
for (const post of posts) {
    if (seen.has(post.file)) errors.push(`E3 posts.json 중복 항목: ${post.file}`);
    seen.add(post.file);

    if (!fs.existsSync(rel('posts', post.file))) errors.push(`E1 posts.json 항목의 HTML 없음: ${post.file}`);
    if (!pugKeys.has(post.file.replace(/\.html$/, ''))) errors.push(`E2 posts.json 항목의 pug 소스 없음: ${post.file}`);
}

// ---------------------------------------------------------------- E4 고아 HTML

/**
 * 검색엔진에 색인되어 있어 의도적으로 보존하는 구 HTML.
 * 후계 문서가 없어 리다이렉트할 곳이 없는 것들이다.
 */
const preservedPath = rel('tools/preserved-orphans.json');
const preserved = new Set(
    fs.existsSync(preservedPath) ? JSON.parse(fs.readFileSync(preservedPath, 'utf8')).files.map((f) => f.file) : [],
);

const orphans = htmlRels.filter((r) => !pugKeys.has(r.replace(/\.html$/, '')) && !stubTarget.has(r));
for (const o of orphans) {
    if (preserved.has(o)) continue;
    errors.push(`E4 새 고아 HTML (pug 소스도 리다이렉트 스텁도 아님): posts/${o}`);
}

for (const p of preserved) {
    if (!fs.existsSync(rel('posts', p))) warnings.push(`W2 보존 목록에 있으나 파일이 없음: posts/${p}`);
    else if (stubTarget.has(p)) warnings.push(`W2 보존 목록에 있으나 리다이렉트 스텁이 됨 (목록에서 제거 필요): posts/${p}`);
}

// ---------------------------------------------------------------- E5 리다이렉트 스텁

for (const [source, signals] of stubTarget) {
    const missing = Object.entries(signals)
        .filter(([, v]) => v == null)
        .map(([k]) => k);
    if (missing.length) {
        errors.push(`E5 리다이렉트 신호 누락 (${missing.join(', ')}): posts/${source}`);
        continue;
    }
    const distinct = new Set(Object.values(signals));
    if (distinct.size !== 1) {
        errors.push(
            `E5 리다이렉트 3중 신호 불일치: posts/${source} — refresh=${signals.refresh}, canonical=${signals.canonical}, script=${signals.script}`,
        );
        continue;
    }

    const target = signals.refresh;
    if (target === source) errors.push(`E5 리다이렉트 자기참조: posts/${source}`);
    if (!fs.existsSync(rel('posts', target))) errors.push(`E5 리다이렉트 대상 없음: posts/${source} -> posts/${target}`);
    if (stubTarget.has(target)) errors.push(`E5 리다이렉트 체인: posts/${source} -> posts/${target}`);
}

// ---------------------------------------------------------------- E6 코드 참조

const sourceFiles = [
    ...walk(rel('pugs'), '.pug').map((p) => rel('pugs', p)),
    ...walk(rel('source'), '.pug').map((p) => rel('source', p)),
    rel('index.pug'),
].filter((f) => fs.existsSync(f));

/**
 * +codeBtn 이 여는 경로만 모은다.
 *
 * 단순히 본문에서 '/Repositories/...' 를 전부 긁으면
 * +asA('https://github.com/.../tree/master/Repositories/...') 같은 외부 링크까지
 * 걸려 오탐이 난다. codeBtn 호출만 대상으로 한다.
 *
 * 두 가지 호출 형태를 지원한다.
 *   +codeBtn('/Repositories/x/y.java', 'java')
 *   +codeBtn({ path: '/Repositories/x/y.java', lan: 'java', ... })
 */
const codeRefs = new Set();
for (const f of sourceFiles) {
    const text = fs.readFileSync(f, 'utf8');
    for (const m of text.matchAll(/\+codeBtn\(\s*'(\/Repositories\/[^']+)'/g)) codeRefs.add(m[1].slice(1));
    for (const m of text.matchAll(/\+codeBtn\(\s*\{[\s\S]{0,400}?path:\s*'(\/Repositories\/[^']+)'/g)) codeRefs.add(m[1].slice(1));
}
for (const r of [...codeRefs].sort()) {
    // 코드 버튼은 파일 하나를 여는 용도다. 디렉터리를 가리키면 버튼이 동작하지 않는다.
    if (!fs.existsSync(rel(r))) errors.push(`E6 존재하지 않는 코드 참조: /${r}`);
    else if (fs.statSync(rel(r)).isDirectory()) errors.push(`E6 코드 참조가 파일이 아닌 디렉터리: /${r}`);
}

// ---------------------------------------------------------------- E7 사이트맵

const sitemapPath = rel('files/sitemap.txt');
if (!fs.existsSync(sitemapPath)) {
    errors.push('E7 files/sitemap.txt 없음');
} else {
    const lines = fs.readFileSync(sitemapPath, 'utf8').trim().split('\n').filter(Boolean);
    const expectedPrefix = SITE_ORIGIN + POSTS_URL_PREFIX;
    for (const line of lines) {
        // 홈은 posts/ 아래가 아니므로 별도로 허용한다
        if (line === SITE_ORIGIN + '/') continue;
        if (!line.startsWith(expectedPrefix)) {
            errors.push(`E7 사이트맵 URL 형식 오류 (${expectedPrefix}... 이어야 함): ${line}`);
            continue;
        }
        const file = line.slice(expectedPrefix.length);
        if (!fs.existsSync(rel('posts', file))) errors.push(`E7 사이트맵 URL의 파일 없음: ${line}`);
        if (stubTarget.has(file)) errors.push(`E7 사이트맵이 리다이렉트 스텁을 가리킴: ${line}`);
    }
    if (lines.length !== posts.length) {
        errors.push(`E7 사이트맵 항목 수(${lines.length})가 posts.json(${posts.length})과 다름`);
    }
}

// ---------------------------------------------------------------- W1 미등록 문서

const indexed = new Set(posts.map((p) => p.file));
for (const key of [...pugKeys].sort()) {
    if (!indexed.has(key + '.html')) warnings.push(`W1 pug는 있으나 posts.json 미등록: pugs/${key}.pug`);
}

// ---------------------------------------------------------------- W4 문서 갱신일

/**
 * doc-dates.json 에 없는 pug 는 빌드에서 mtime 으로 대체된다.
 * mtime 은 클론할 때마다 바뀌므로 대량 diff 의 원인이 된다.
 */
const docDatesPath = rel('source/doc-dates.json');
if (fs.existsSync(docDatesPath)) {
    const recorded = new Set(Object.keys(JSON.parse(fs.readFileSync(docDatesPath, 'utf8')).dates));
    const missingDates = [...pugKeys].filter((k) => !recorded.has(`pugs/${k}.pug`)).sort();
    for (const k of missingDates) warnings.push(`W4 doc-dates.json 에 갱신일 없음 (npm run dates 필요): pugs/${k}.pug`);
    for (const r of recorded) {
        if (!fs.existsSync(rel(r))) warnings.push(`W4 doc-dates.json 에 있으나 파일이 없음: ${r}`);
    }
} else {
    warnings.push('W4 source/doc-dates.json 이 없다. npm run dates 로 생성한다');
}

// ---------------------------------------------------------------- 결과

// 보존 목록에 선언된 것과 새로 생긴 고아를 구분해서 보여준다.
const preservedCount = orphans.filter((o) => preserved.has(o)).length;
const newOrphanCount = orphans.length - preservedCount;
const summary = [
    `HTML ${htmlRels.length}개 (현행 ${htmlRels.length - orphans.length - stubTarget.size}, 리다이렉트 ${stubTarget.size}, 보존 ${preservedCount}, 새 고아 ${newOrphanCount})`,
    `posts.json ${posts.length}개`,
    `코드 참조 ${codeRefs.size}개`,
];
console.log(summary.join(' / '));

for (const w of warnings) console.log(`  ⚠  ${w}`);
for (const e of errors) console.log(`  ✖  ${e}`);

if (errors.length) {
    console.log(`\n❌ 오류 ${errors.length}건, 경고 ${warnings.length}건`);
    process.exitCode = 1;
} else if (STRICT && warnings.length) {
    console.log(`\n❌ 경고 ${warnings.length}건 (--strict)`);
    process.exitCode = 1;
} else {
    console.log(`\n✅ 오류 없음${warnings.length ? ` (경고 ${warnings.length}건)` : ''}`);
}
