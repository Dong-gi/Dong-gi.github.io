/**
 * 문서별 갱신일(dateModified)을 git 이력에서 계산해 source/doc-dates.json 에 기록한다.
 *
 * 왜 필요한가
 *   빌드는 원래 pug 파일의 mtime 을 dateModified 로 썼다. 그런데 새로 클론한 저장소는
 *   모든 파일의 mtime 이 체크아웃 시각이므로, 다른 머신에서 빌드를 한 번만 돌려도
 *   263개 페이지의 날짜가 그날로 덮이고 대량 diff 가 생긴다.
 *
 * 왜 git log -1 로는 부족한가
 *   이 저장소에는 pug 를 100개 이상 한꺼번에 건드린 커밋이 여럿 있다. mixin 교체,
 *   후행 쉼표 추가, 디렉터리 이동 같은 것들이라 내용은 그대로인데, 단순히 마지막
 *   커밋 날짜를 쓰면 문서 대부분의 갱신일이 그 리팩터링 날짜로 뭉개진다.
 *
 * 그래서 두 단계로 거른다
 *   1. tools/refactor-commits.json 에 선언된 커밋은 통째로 제외한다.
 *      문자는 바뀌었지만 의미가 같은 경우(mixin 교체, 경로 조정 등)를 사람이 판단해 등록한다.
 *   2. 나머지 커밋에서도 해당 파일의 변경이 공백뿐이면 제외한다.
 *      +legacy 로 본문을 접을 때처럼 들여쓰기만 바뀐 경우가 여기 해당한다.
 *      혼합 커밋(일부 파일만 실제 수정) 안에서도 파일 단위로 정확히 판정된다.
 *
 * 결과를 JSON 으로 커밋해두므로 빌드 시점에는 git 이 필요 없다. CI, tarball,
 * 어느 머신에서 빌드하든 같은 날짜가 나온다.
 *
 * 사용법
 *   node tools/build-doc-dates.mjs [저장소 루트]
 *   문서를 추가하거나 수정한 뒤 커밋하고 나서 실행한다. npm run dates 로도 실행된다.
 */

import fs from 'node:fs';
import path from 'node:path';
import child_process from 'node:child_process';

const ROOT = process.argv[2] ?? '.';
const OUT = path.join(ROOT, 'source/doc-dates.json');

const excluded = new Set(
    JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/refactor-commits.json'), 'utf8')).commits.map((c) => c.sha),
);

/**
 * pugs/ 를 건드린 모든 커밋의 패치를 한 번에 받는다.
 * -M 으로 rename 을 인식시켜 단순 이동이 add+delete 로 잡히지 않게 한다.
 * -U0 으로 문맥 줄을 빼 파싱을 단순화한다.
 */
const raw = child_process.execFileSync(
    'git',
    ['-C', ROOT, 'log', '--format=__COMMIT__%H|%aI', '-U0', '-M', '-p', '--', 'pugs'],
    { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 },
);

/** 파일 경로 -> { semantic: ISO|null, any: ISO|null } */
const dates = new Map();
/** 통계 */
const stat = { commits: 0, excludedHits: 0, whitespaceOnly: 0, semantic: 0, renames: 0 };

/**
 * 옛 경로 -> 현재 경로.
 *
 * 로그를 최신순으로 읽으므로, rename 을 만난 시점에 그보다 오래된 커밋들은 아직
 * 옛 경로를 쓰고 있다. 옛 경로를 현재 경로로 접어 이력 사슬이 끊기지 않게 한다.
 * 이 처리가 없으면 경로가 바뀐 문서는 이동 이전 이력을 통째로 잃고, 하필 그 이동
 * 커밋이 리팩터링이라 갱신일이 엉뚱해진다.
 */
const alias = new Map();

/** 옛 경로를 현재 경로로 접는다. 여러 번 이동한 경우도 따라간다. */
function canonical(p) {
    const seen = new Set();
    while (alias.has(p) && !seen.has(p)) {
        seen.add(p);
        p = alias.get(p);
    }
    return p;
}

let sha = null;
let iso = null;
let file = null;
let add = [];
let del = [];

/** 현재까지 모은 파일 하나의 변경을 판정해 기록한다. */
function flush() {
    if (file == null) return;
    const key = canonical(file);
    const entry = dates.get(key) ?? { semantic: null, any: null };
    // git log 는 최신순이므로 처음 만난 것이 가장 최신이다.
    entry.any ??= iso;

    if (excluded.has(sha)) {
        stat.excludedHits += 1;
    } else {
        // 공백을 모두 제거한 뒤 추가/삭제 줄의 다중집합을 비교한다.
        const a = add.map((l) => l.replace(/\s+/g, '')).sort().join('\n');
        const d = del.map((l) => l.replace(/\s+/g, '')).sort().join('\n');
        if (a === d) stat.whitespaceOnly += 1;
        else {
            stat.semantic += 1;
            entry.semantic ??= iso;
        }
    }
    dates.set(key, entry);
    file = null;
    add = [];
    del = [];
}

for (const line of raw.split('\n')) {
    if (line.startsWith('__COMMIT__')) {
        flush();
        const [h, d] = line.slice('__COMMIT__'.length).split('|');
        sha = h;
        iso = d;
        stat.commits += 1;
        continue;
    }
    if (line.startsWith('diff --git ')) {
        flush();
        // "diff --git a/x b/y" 에서 b 쪽(변경 후 경로)을 쓴다.
        file = line.slice(line.indexOf(' b/') + 3) || null;
        if (file != null && !/^pugs\/.*\.pug$/.test(file)) file = null;
        continue;
    }
    if (file == null) continue;
    // rename 을 만나면 옛 경로를 현재 경로로 연결해둔다.
    if (line.startsWith('rename from ')) {
        const from = line.slice('rename from '.length);
        if (/^pugs\/.*\.pug$/.test(from) && from !== file) {
            alias.set(from, canonical(file));
            stat.renames += 1;
        }
        continue;
    }
    if (line.startsWith('+++') || line.startsWith('---') || line.startsWith('@@')) continue;
    if (line.startsWith('+')) add.push(line.slice(1));
    else if (line.startsWith('-')) del.push(line.slice(1));
}
flush();

// ---------------------------------------------------------------- 결과 조립

/** 현재 존재하는 pug 만 대상으로 한다. */
function walk(dir, base = dir) {
    const out = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const f = path.join(dir, e.name);
        if (e.isDirectory()) out.push(...walk(f, base));
        else if (e.name.endsWith('.pug')) out.push('pugs/' + path.relative(base, f).split(path.sep).join('/'));
    }
    return out;
}

const result = {};
const fallbacks = [];
let missing = 0;

for (const rel of walk(path.join(ROOT, 'pugs')).sort()) {
    const e = dates.get(rel);
    if (e?.semantic) {
        result[rel] = e.semantic;
    } else if (e?.any) {
        // 실질 변경으로 판정된 커밋이 하나도 없는 문서. 이력상 최신 커밋을 쓴다.
        result[rel] = e.any;
        fallbacks.push(rel);
    } else {
        missing += 1;
    }
}

const payload = {
    $comment:
        '문서별 dateModified. tools/build-doc-dates.mjs 가 git 이력에서 계산한다. ' +
        '대량 리팩터링 커밋(tools/refactor-commits.json)과 공백만 바뀐 변경은 제외한 뒤의 최신 커밋 날짜다. ' +
        '문서를 추가·수정해 커밋한 뒤 npm run dates 로 갱신한다. 직접 편집하지 않는다.',
    generatedAt: new Date().toISOString(),
    dates: result,
};
fs.writeFileSync(OUT, JSON.stringify(payload, null, 4) + '\n');

console.log(`커밋 ${stat.commits}개 분석`);
console.log(`  제외된 리팩터링 커밋의 파일 변경   ${stat.excludedHits}`);
console.log(`  공백만 바뀐 파일 변경              ${stat.whitespaceOnly}`);
console.log(`  실질 변경으로 판정                 ${stat.semantic}`);
console.log(`\n문서 ${Object.keys(result).length}개에 날짜 기록`);
if (fallbacks.length) console.log(`  실질 변경 이력이 없어 최신 커밋으로 대체: ${fallbacks.length}개`);
if (missing) console.log(`  ⚠ git 이력에 없어 날짜를 못 구한 문서: ${missing}개 (빌드에서 mtime 으로 대체된다)`);
