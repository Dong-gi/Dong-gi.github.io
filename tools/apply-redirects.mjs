/**
 * 고아 HTML → 후계 문서 리다이렉트 스텁 적용 스크립트
 *
 * audit-orphans.mjs 가 만든 결과에 수동 큐레이션(오버라이드/제외)을 반영한 뒤,
 * 확정된 건에 대해서만 정적 리다이렉트 스텁을 기록한다.
 *
 * 리다이렉트 방식 (GitHub Pages는 서버 측 301을 쓸 수 없으므로):
 *   1. <meta http-equiv="refresh" content="0; url=...">  — 검색엔진이 301에 준해 처리
 *   2. <link rel="canonical" href="...">                 — 색인 대상을 후계 문서로 지정
 *   3. <script>location.replace(...)</script>            — 실사용자 즉시 이동
 * rel=canonical 과 noindex 는 상충하는 신호이므로 noindex 는 넣지 않는다.
 *
 * 멱등성: 이미 스텁인 파일(MARKER 포함)은 건너뛴다.
 * --dry-run 으로 파일을 쓰지 않고 계획만 출력할 수 있다.
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2];
const AUDIT = process.argv[3];
const DRY_RUN = process.argv.includes('--dry-run');
if (!ROOT || !AUDIT) {
    throw new Error('사용법: node apply-redirects.mjs <블로그 루트> <감사 JSON> [--dry-run]');
}

const SITE_ORIGIN = 'https://dong-gi.github.io';
const POSTS_URL_PREFIX = '/posts/';
const MARKER = 'data-redirect-stub';

// ---------------------------------------------------------------- 수동 큐레이션

/** 자동 매칭이 틀렸거나 후계 문서가 없다고 판단해 원본을 보존할 고아 경로. */
const EXCLUDE = new Set([
    // 유사도는 높았으나 주제가 무관한 오탐
    'infra/heroku.html',
    // 후계 문서에 해당 내용이 없어 보존
    'db/psql_to_sqlite.html',
    'dev/DB/psql-to-sqlite.html',
    'infra/db/psql_to_sqlite.html',
    'dotnet/csharp_library.html',
    'language/.net/csharp_library.html',
    'project/snippets.html',
]);

/**
 * 자동 매칭이 놓친 통합 사례. 후계 문서 목차에서 해당 내용을 직접 확인한 건만 등록한다.
 * key: 고아 경로, value: 후계 경로 (posts/ 기준 상대 경로)
 */
const OVERRIDE = {
    // PostgreSQL 분할 문서 4종이 dev/DB/PostgreSQL.pug 의 h1 섹션으로 통합됨
    // (h1 SQL / h1 서버 관리 / h1 서버 프로그래밍 / h1 참고 - psql)
    'db/psql_sql.html': 'dev/DB/PostgreSQL.html',
    'db/psql_admin.html': 'dev/DB/PostgreSQL.html',
    'db/psql_programming.html': 'dev/DB/PostgreSQL.html',
    'db/psql_tutorial.html': 'dev/DB/PostgreSQL.html',
    'dev/DB/psql-sql.html': 'dev/DB/PostgreSQL.html',
    'dev/DB/psql-admin.html': 'dev/DB/PostgreSQL.html',
    'dev/DB/psql-programming.html': 'dev/DB/PostgreSQL.html',
    'dev/DB/psql-tutorial.html': 'dev/DB/PostgreSQL.html',
    'infra/db/psql_sql.html': 'dev/DB/PostgreSQL.html',
    'infra/db/psql_admin.html': 'dev/DB/PostgreSQL.html',
    'infra/db/psql_programming.html': 'dev/DB/PostgreSQL.html',
    'infra/db/psql_tutorial.html': 'dev/DB/PostgreSQL.html',

    // dev/web/network.pug 에 h1 HTTP / h1 프록시 존재
    'front/http.html': 'dev/web/network.html',
    'infra/web/http.html': 'dev/web/network.html',

    // dev/software-design.pug 에 h1 코드 디자인 패턴 / h1 코드 리팩터링 존재
    'dev/refactoring.html': 'dev/software-design.html',
    'single/refactoring.html': 'dev/software-design.html',
    'topic/refactoring.html': 'dev/software-design.html',

    // 코어 JavaScript → 현행 JavaScript 문서
    'javascript/basic.html': 'dev/JavaScript/JavaScript.html',

    // 파일명 일치가 명확한 건
    'db/redis.html': 'dev/DB/redis.html',
    'single/redis.html': 'dev/DB/redis.html',
    'java/gradle.html': 'dev/gradle.html',
    'topic/docker_mailserver.html': 'dev/docker-mailserver.html',
};

// ---------------------------------------------------------------- 계획 수립

const { results } = JSON.parse(fs.readFileSync(AUDIT, 'utf8'));
const byOrphan = new Map(results.map((r) => [r.orphan, r]));

/** HTML 속성값에 넣기 위해 큰따옴표만 추가 이스케이프한다. 원문은 이미 엔티티 인코딩됨. */
const attr = (s) => s.replace(/"/g, '&quot;');

/** 후계 문서의 <title>을 읽어온다. 없으면 경로를 대신 쓴다. */
function titleOf(rel) {
    const p = path.join(ROOT, 'posts', rel);
    if (!fs.existsSync(p)) return null;
    return (fs.readFileSync(p, 'utf8').match(/<title>([\s\S]*?)<\/title>/i)?.[1] ?? rel).trim();
}

const plan = [];
const skipped = [];

for (const r of results) {
    const orphan = r.orphan;
    if (EXCLUDE.has(orphan)) {
        skipped.push({ orphan, reason: 'excluded', target: r.target, confidence: r.confidence });
        continue;
    }
    const target = OVERRIDE[orphan] ?? (r.confidence === 'high' ? r.target : null);
    if (!target) {
        skipped.push({ orphan, reason: 'no-successor', target: r.target, confidence: r.confidence });
        continue;
    }
    if (target === orphan) {
        skipped.push({ orphan, reason: 'self-reference', target, confidence: r.confidence });
        continue;
    }
    const targetTitle = titleOf(target);
    if (targetTitle == null) {
        skipped.push({ orphan, reason: 'target-missing', target, confidence: r.confidence });
        continue;
    }
    plan.push({
        orphan,
        orphanTitle: r.orphanTitle,
        target,
        targetTitle,
        source: OVERRIDE[orphan] ? 'manual' : 'auto',
        confidence: r.confidence,
        cosine: r.cosine,
    });
}

// 리다이렉트 체인 방지 — 후계 문서가 다시 리다이렉트 대상이 되는 경우를 차단한다.
const redirectSources = new Set(plan.map((p) => p.orphan));
const chained = plan.filter((p) => redirectSources.has(p.target));
if (chained.length) {
    throw new Error(`리다이렉트 체인 발견 (${chained.length}건): ${chained.map((c) => `${c.orphan}->${c.target}`).join(', ')}`);
}

// ---------------------------------------------------------------- 스텁 생성

/** 정적 리다이렉트 스텁 HTML을 만든다. */
function buildStub({ orphanTitle, target, targetTitle }) {
    const url = POSTS_URL_PREFIX + target;
    const title = orphanTitle || targetTitle;
    return `<!doctype html>
<html lang="ko" ${MARKER}>

<head>
    <meta charset="UTF-8">
    <title>${title}</title>
    <link rel="canonical" href="${attr(SITE_ORIGIN + url)}">
    <meta http-equiv="refresh" content="0; url=${attr(url)}">
    <meta name="description" content="${attr(title)} 문서는 ${attr(targetTitle)}(으)로 통합되었습니다">
    <script>location.replace(${JSON.stringify(url)} + location.hash);</script>
</head>

<body>
    <h1>${title}</h1>
    <p>이 문서는 <a href="${attr(url)}">${targetTitle}</a>(으)로 통합되었습니다.</p>
</body>

</html>
`;
}

let written = 0;
for (const item of plan) {
    const filePath = path.join(ROOT, 'posts', item.orphan);
    const current = fs.readFileSync(filePath, 'utf8');
    if (current.includes(MARKER)) {
        item.action = 'already-stub';
        continue;
    }
    item.action = 'write';
    if (!DRY_RUN) {
        fs.writeFileSync(filePath, buildStub(item));
        written += 1;
    }
}

// ---------------------------------------------------------------- 리포트

const bySkipReason = skipped.reduce((acc, s) => ((acc[s.reason] = (acc[s.reason] ?? 0) + 1), acc), {});
const report = {
    dryRun: DRY_RUN,
    planned: plan.length,
    written,
    alreadyStub: plan.filter((p) => p.action === 'already-stub').length,
    preserved: skipped.length,
    bySkipReason,
    manualOverrides: plan.filter((p) => p.source === 'manual').length,
};
fs.writeFileSync(path.join(path.dirname(AUDIT), 'redirect-plan.json'), JSON.stringify({ report, plan, skipped }, null, 2));
console.log(JSON.stringify(report, null, 2));
