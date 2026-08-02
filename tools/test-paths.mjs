/**
 * 빌드의 경로 처리가 Windows 에서도 성립하는지 검증한다.
 *
 * Windows 에서 path.join 은 '\' 를 쓴다. build.ts 는 경로를 문자열로 다루는 곳이
 * 많아('/pugs/' -> '/posts/' 치환, doc-dates.json 조회, URL 생성) 구분자가 섞이면
 * 산출물이 pugs/ 안에 쓰이거나 URL 에 '\' 가 들어간다.
 *
 * 이 테스트는 실제 Windows 없이도 그 상황을 재현한다. path.win32.join 으로 경로를
 * 만든 뒤 build.ts 와 같은 변환을 적용해 결과를 확인한다.
 *
 *   node tools/test-paths.mjs
 */

import path from 'node:path';
import { readFileSync } from 'node:fs';

// build.ts 의 구현을 그대로 옮겨 온다. 시그니처가 바뀌면 이 테스트도 함께 고친다.
const toPosix = (p) => p.split(/[\\/]/).join('/');
const SITE_ORIGIN = 'https://dong-gi.github.io';
const pageUrlOf = (htmlPath) => SITE_ORIGIN + htmlPath.replace(/^\./, '').replace(/\/index\.html$/, '/');

const cases = [];
let failed = 0;

/** 기대값과 실제값을 비교한다. */
function check(label, actual, expected) {
    const ok = actual === expected;
    if (!ok) failed += 1;
    cases.push(`  ${ok ? '✅' : '❌'} ${label}\n       기대 ${expected}\n       실제 ${actual}`);
}

for (const [platform, p] of [
    ['posix ', path.posix],
    ['win32 ', path.win32],
]) {
    // fsp.readdir(recursive, withFileTypes) 가 주는 parentPath 를 흉내낸다.
    const parentPath = p.join('.', 'pugs', 'dev');
    const filePath = './' + toPosix(p.join(parentPath, 'aws.pug'));
    const htmlPath = toPosix(filePath).replace('/pugs/', '/posts/').replace('.pug', '.html');

    check(`${platform} 소스 경로`, filePath, './pugs/dev/aws.pug');
    check(`${platform} 산출물 경로`, htmlPath, './posts/dev/aws.html');
    check(`${platform} 산출물이 posts/ 안인가`, String(htmlPath.startsWith('./posts/')), 'true');
    check(`${platform} canonical URL`, pageUrlOf(htmlPath), 'https://dong-gi.github.io/posts/dev/aws.html');
    check(`${platform} doc-dates 조회 키`, toPosix(filePath).replace(/^\.\//, ''), 'pugs/dev/aws.pug');

    // 이미지 출력 경로
    const imgOut = './' + toPosix(p.join(p.join('.', 'imgs', '201910'), 'a.png')).replace(/^imgs\//, 'imgs-generated/');
    check(`${platform} 이미지 출력 경로`, imgOut, './imgs-generated/201910/a.png');

    // 홈은 pugs/ 밖이라 치환 대상이 아니다
    const indexHtml = toPosix('./index.pug').replace('/pugs/', '/posts/').replace('.pug', '.html');
    check(`${platform} 홈 산출물`, indexHtml, './index.html');
    check(`${platform} 홈 canonical`, pageUrlOf(indexHtml), 'https://dong-gi.github.io/');
}

// 실제 doc-dates.json 의 키가 posix 형식인지도 확인한다.
const dates = JSON.parse(readFileSync(new URL('../source/doc-dates.json', import.meta.url), 'utf8')).dates;
const backslashKeys = Object.keys(dates).filter((k) => k.includes('\\'));
check('doc-dates.json 키에 역슬래시 없음', String(backslashKeys.length), '0');

console.log(cases.join('\n'));
console.log(failed === 0 ? `\n✅ ${cases.length}개 검사 전부 통과` : `\n❌ ${failed}건 실패`);
process.exitCode = failed === 0 ? 0 : 1;
