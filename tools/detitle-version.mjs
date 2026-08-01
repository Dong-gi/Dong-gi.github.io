/**
 * 제목에 박제된 버전을 본문 상단의 "기준 버전" 표기로 옮긴다.
 *
 * 제목을 바꿔도 URL(파일 경로)은 그대로이므로 리다이렉트가 필요 없다.
 * 다만 source/posts.json 의 title 도 함께 고쳐야 목록·검색에 반영된다.
 *
 * 각 항목은 아래를 수행한다.
 *   1. pug 의 +post({ title, description }) 에서 버전 제거
 *   2. 본문 첫 줄 앞에 기준 버전 문단 삽입
 *   3. posts.json 의 title 갱신
 *
 * 이미 처리된 파일(기준 버전 문단이 있는 경우)은 건너뛴다.
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2] ?? '.';
const DRY_RUN = process.argv.includes('--dry-run');

/** 본문 상단에 삽입할 기준 버전 문단의 표식. 멱등성 판정에 쓴다. */
const MARK = 'p.version-note';

/**
 * @typedef {object} Entry
 * @property {string} pug        pugs/ 기준 상대 경로
 * @property {string} title      바꿀 제목
 * @property {string} desc       바꿀 description
 * @property {string} basis      기준 버전 문단 본문
 */

/** @type {Entry[]} */
const ENTRIES = [
    {
        pug: 'dev/JVM/freemarker_built_in.pug',
        title: 'FreeMarker Built-in 목록',
        desc: 'FreeMarker Built-in 목록 정리',
        basis: 'FreeMarker 2.3.28 기준입니다.',
    },
    {
        pug: 'dev/JVM/freemarker_programming_guide.pug',
        title: 'FreeMarker 프로그래밍 가이드',
        desc: 'FreeMarker 프로그래밍 가이드 정리',
        basis: 'FreeMarker 2.3.28 기준입니다.',
    },
    {
        pug: 'dev/JVM/freemarker_template_guide.pug',
        title: 'FreeMarker 템플릿 작성 가이드',
        desc: 'FreeMarker 템플릿 작성 가이드 정리',
        basis: 'FreeMarker 2.3.28 기준입니다.',
    },
    {
        pug: 'dev/JVM/freemarker_xml_guide.pug',
        title: 'FreeMarker XML 처리 가이드',
        desc: 'FreeMarker XML 처리 가이드 정리',
        basis: 'FreeMarker 2.3.28 기준입니다.',
    },
    {
        pug: 'dev/python/basic.pug',
        title: 'Python',
        desc: 'Python 시작하기',
        basis: 'Python 3.8 기준입니다.',
    },
    {
        pug: 'dev/python/data_model.pug',
        title: 'Python 데이터 모델',
        desc: 'Python 데이터 모델 정리',
        basis: 'Python 3.8 기준입니다.',
    },
    {
        pug: 'dev/JVM/java.base.pug',
        title: 'java.base 모듈',
        desc: 'JDK java.base 모듈 정리',
        basis: 'JDK 16 기준입니다.',
    },
    {
        pug: 'dev/JVM/java.net.http.pug',
        title: 'java.net.http 모듈',
        desc: 'JDK java.net.http 모듈 정리',
        basis: 'JDK 16 기준입니다.',
    },
    {
        pug: 'dev/JVM/guava.pug',
        title: 'Guava',
        desc: 'Guava 정리',
        basis: 'Guava 30.1 기준입니다.',
    },
    {
        pug: 'dev/JVM/apache.commons.lang.pug',
        title: 'Apache Commons Lang',
        desc: 'Apache Commons Lang 정리',
        basis: 'Apache Commons Lang 3.9 기준입니다.',
    },
    {
        pug: 'dev/JVM/apache.commons.collections.pug',
        title: 'Apache Commons Collections',
        desc: 'Apache Commons Collections 정리',
        basis: 'Apache Commons Collections 4.4 기준입니다.',
    },
    {
        pug: 'dev/JVM/apache.commons.math.pug',
        title: 'Apache Commons Math',
        desc: 'Apache Commons Math 정리',
        basis: 'Apache Commons Math 3.6.1 기준입니다.',
    },
    {
        pug: 'dev/JVM/apache.commons.rng.pug',
        title: 'Apache Commons RNG',
        desc: 'Apache Commons RNG 정리',
        basis: 'Apache Commons RNG 1.2 기준입니다.',
    },
];

const postsPath = path.join(ROOT, 'source/posts.json');
const postsRaw = fs.readFileSync(postsPath, 'utf8');
const posts = JSON.parse(postsRaw);
const byFile = new Map(posts.list.map((p) => [p.file, p]));

// 이 저장소의 작업 트리는 CRLF 다. 줄바꿈만 바뀐 대량 diff 를 막기 위해 원래 방식을 유지한다.
const postsEol = postsRaw.includes('\r\n') ? '\r\n' : '\n';

let changed = 0;
const skipped = [];

for (const e of ENTRIES) {
    const pugPath = path.join(ROOT, 'pugs', e.pug);
    const htmlKey = e.pug.replace(/\.pug$/, '.html');
    const before = fs.readFileSync(pugPath, 'utf8');

    if (before.includes(MARK)) {
        skipped.push(`${e.pug} — 이미 처리됨`);
        continue;
    }

    // +post({ ... }) 블록에서 title / description 을 교체한다.
    const eol = before.includes('\r\n') ? '\r\n' : '\n';
    const head = before.match(/\+post\(\{[\s\S]*?\}\)\r?\n/);
    if (!head) {
        skipped.push(`${e.pug} — +post 블록을 찾지 못함`);
        continue;
    }
    const newHead = head[0]
        .replace(/(\n[^\S\n]*title:\s*)'[^']*'/, `$1'${e.title}'`)
        .replace(/(\n[^\S\n]*description:\s*)'[^']*'/, `$1'${e.desc}'`);
    if (newHead === head[0]) {
        skipped.push(`${e.pug} — title/description 교체 실패`);
        continue;
    }

    // 헤더 직후 첫 본문 줄 앞에 기준 버전 문단을 넣는다.
    const rest = before.slice(head.index + head[0].length);
    // 헤더 뒤 빈 줄들을 건너뛰고, 첫 본문 줄의 들여쓰기를 그대로 쓴다.
    const firstLine = rest.match(/^(?:[^\S\n]*\r?\n)*([^\S\n]+)\S/);
    if (!firstLine) {
        skipped.push(`${e.pug} — 본문 시작 위치를 찾지 못함`);
        continue;
    }
    const indent = firstLine[1];
    const insertAt = firstLine.index + firstLine[0].length - indent.length - 1;
    const note = `${indent}${MARK} ${e.basis}${eol}${eol}`;
    const after = before.slice(0, head.index) + newHead + rest.slice(0, insertAt) + note + rest.slice(insertAt);

    if (!DRY_RUN) fs.writeFileSync(pugPath, after);

    const post = byFile.get(htmlKey);
    if (post == null) {
        skipped.push(`${e.pug} — posts.json 에 항목이 없음 (pug 만 수정됨)`);
    } else {
        post.title = e.title;
    }
    changed += 1;
    console.log(`  ${e.pug}\n    제목 → ${e.title}\n    기준 → ${e.basis}`);
}

if (!DRY_RUN) {
    fs.writeFileSync(postsPath, (JSON.stringify(posts, null, 4) + '\n').replace(/\n/g, postsEol));
}

console.log(`\n처리 ${changed} / 건너뜀 ${skipped.length}${DRY_RUN ? ' (dry-run)' : ''}`);
for (const s of skipped) console.log(`  ⚠ ${s}`);
