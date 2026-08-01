/**
 * 리다이렉트 스텁 검증 스크립트 (읽기 전용)
 *
 * 검사 항목
 *   1. 스텁 수가 계획과 일치하는가
 *   2. 각 스텁의 canonical / meta refresh / JS 대상이 서로 동일한가
 *   3. 대상 파일이 실제로 존재하는가
 *   4. 대상이 또 다른 스텁을 가리키는 체인/루프가 없는가
 *   5. 보존하기로 한 파일이 변경되지 않았는가 (스텁이 아닌 상태 유지)
 *   6. live 문서(pug 소스 보유)가 오염되지 않았는가
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2];
const PLAN_FILE = process.argv[3];
const MARKER = 'data-redirect-stub';
const POSTS_DIR = path.join(ROOT, 'posts');

const { plan, skipped } = JSON.parse(fs.readFileSync(PLAN_FILE, 'utf8'));

function walk(dir, ext, base = dir) {
    const out = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const f = path.join(dir, e.name);
        if (e.isDirectory()) out.push(...walk(f, ext, base));
        else if (e.name.endsWith(ext)) out.push(path.relative(base, f).split(path.sep).join('/'));
    }
    return out;
}

const errors = [];
const stubs = new Set();

for (const rel of walk(POSTS_DIR, '.html')) {
    if (fs.readFileSync(path.join(POSTS_DIR, rel), 'utf8').includes(MARKER)) stubs.add(rel);
}

// 1. 개수 일치
if (stubs.size !== plan.length) {
    errors.push(`스텁 수 불일치: 실제 ${stubs.size} vs 계획 ${plan.length}`);
}

// 2~4. 스텁 내용 검증
for (const item of plan) {
    const rel = item.orphan;
    if (!stubs.has(rel)) {
        errors.push(`${rel}: 스텁이 생성되지 않음`);
        continue;
    }
    const html = fs.readFileSync(path.join(POSTS_DIR, rel), 'utf8');

    const canonical = html.match(/<link rel="canonical" href="([^"]+)"/)?.[1];
    const refresh = html.match(/<meta http-equiv="refresh" content="0; url=([^"]+)"/)?.[1];
    const js = html.match(/location\.replace\("([^"]+)"/)?.[1];
    const expectedUrl = '/posts/' + item.target;

    if (canonical !== 'https://dong-gi.github.io' + expectedUrl) errors.push(`${rel}: canonical 불일치 (${canonical})`);
    if (refresh !== expectedUrl) errors.push(`${rel}: meta refresh 불일치 (${refresh})`);
    if (js !== expectedUrl) errors.push(`${rel}: JS 대상 불일치 (${js})`);

    // 3. 대상 존재 여부
    if (!fs.existsSync(path.join(POSTS_DIR, item.target))) errors.push(`${rel}: 대상 파일 없음 (${item.target})`);

    // 4. 체인/루프
    if (stubs.has(item.target)) errors.push(`${rel}: 대상이 또 다른 스텁 (${item.target}) — 체인 발생`);
    if (item.target === rel) errors.push(`${rel}: 자기 자신을 가리킴`);
}

// 5. 보존 대상이 스텁으로 바뀌지 않았는지
for (const s of skipped) {
    if (stubs.has(s.orphan)) errors.push(`${s.orphan}: 보존 대상인데 스텁으로 변경됨`);
    if (!fs.existsSync(path.join(POSTS_DIR, s.orphan))) errors.push(`${s.orphan}: 보존 대상 파일이 사라짐`);
}

// 6. live 문서 오염 검사
const pugKeys = new Set(walk(path.join(ROOT, 'pugs'), '.pug').map((p) => p.replace(/\.pug$/, '')));
for (const rel of stubs) {
    if (pugKeys.has(rel.replace(/\.html$/, ''))) errors.push(`${rel}: pug 소스가 있는 현행 문서인데 스텁으로 덮어씀`);
}

// 대상 분포 요약
const targetCount = new Map();
for (const item of plan) targetCount.set(item.target, (targetCount.get(item.target) ?? 0) + 1);
const topTargets = [...targetCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);

console.log(`검사 대상 스텁: ${stubs.size}`);
console.log(`보존 파일: ${skipped.length}`);
console.log(`고유 대상 문서: ${targetCount.size}`);
console.log(`유입 상위 대상:`);
for (const [t, c] of topTargets) console.log(`  ${c.toString().padStart(3)}건 <- ${t}`);
console.log();
if (errors.length === 0) {
    console.log('✅ 오류 없음 — 모든 검사 통과');
} else {
    console.log(`❌ 오류 ${errors.length}건`);
    errors.slice(0, 40).forEach((e) => console.log('  - ' + e));
    process.exitCode = 1;
}
