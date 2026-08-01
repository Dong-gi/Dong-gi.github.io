/**
 * 보존 대상 구 HTML에서 죽은 Universal Analytics 태그를 제거한다.
 *
 * 이 파일들은 pug 소스가 없어 빌드로 재생성되지 않으므로 직접 편집해야 한다.
 * UA는 2023-07-01 데이터 수집을 중단했다.
 *
 * 안전장치
 *   - tools/preserved-orphans.json 에 선언된 파일만 건드린다
 *   - UA 블록을 제거한 것 외에 다른 차이가 없는지 검사한 뒤에만 기록한다
 *   - 매칭 실패 시 해당 파일은 건너뛰고 보고한다 (멱등)
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2] ?? '.';
const DRY_RUN = process.argv.includes('--dry-run');

/** UA 태그 로더 + gtag 초기화 스크립트 한 덩어리. */
const UA_BLOCK =
    /<script id="google-analytics" src="https:\/\/www\.googletagmanager\.com\/gtag\/js\?id=UA-143098403-1" async><\/script><script>window\.dataLayer = window\.dataLayer \|\| \[\];\s*function gtag\(\)\{dataLayer\.push\(arguments\);\}\s*gtag\('js', new Date\(\)\);\s*gtag\('config', 'UA-143098403-1'\);\s*<\/script>/;

const preserved = JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/preserved-orphans.json'), 'utf8')).files;

let changed = 0;
let clean = 0;
const skipped = [];

for (const { file } of preserved) {
    const full = path.join(ROOT, 'posts', file);
    const before = fs.readFileSync(full, 'utf8');

    if (!before.includes('UA-143098403-1')) {
        clean += 1;
        continue;
    }

    const after = before.replace(UA_BLOCK, '');
    if (after === before) {
        skipped.push(`${file} — UA 문자열은 있으나 예상 블록과 형태가 다름`);
        continue;
    }
    if (after.includes('UA-143098403-1')) {
        skipped.push(`${file} — 제거 후에도 UA 문자열이 남음`);
        continue;
    }

    // 제거된 부분이 정확히 UA 블록 하나인지 확인한다.
    const removed = before.length - after.length;
    const block = before.match(UA_BLOCK)[0];
    if (removed !== block.length) {
        skipped.push(`${file} — 제거 길이(${removed})가 블록 길이(${block.length})와 다름`);
        continue;
    }
    if (before.replace(block, '') !== after) {
        skipped.push(`${file} — 블록 외 다른 변경이 발생`);
        continue;
    }

    if (!DRY_RUN) fs.writeFileSync(full, after);
    changed += 1;
}

console.log(`보존 대상 ${preserved.length}건 / 제거 ${changed} / 원래 없음 ${clean} / 건너뜀 ${skipped.length}${DRY_RUN ? ' (dry-run)' : ''}`);
for (const s of skipped) console.log(`  ⚠ ${s}`);
