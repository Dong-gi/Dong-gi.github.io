/**
 * 장 조각(.pug)이 문법적으로 온전한지 확인한다.
 *
 *   node check-chapter.mjs /root/work/rewrite/physics/ch04-kinematics.pug
 *
 * 조각은 `    h1 ...` 처럼 4칸 들여쓴 상태여야 한다(문서에 그대로 붙일 수 있게).
 * 최소 뼈대에 끼워 렌더해 보고, 문서 규칙 위반도 함께 검사한다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const pug = require('pug');
// 이 스크립트는 scripts/ 아래에 있으므로 저장소 루트는 한 단계 위다.
// 경로를 박아 두면 다른 기계에서 그대로 쓸 수 없다.
const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');

let bad = 0;
const fail = m => { console.log('  ❌ ' + m); bad += 1; };
const ok = m => console.log('  ✅ ' + m);

for (const file of process.argv.slice(2)) {
    const src = fs.readFileSync(file, 'utf8');
    console.log(`\n== ${path.basename(file)} (${src.split('\n').length}줄) ==`);

    // 1) 렌더
    // 뼈대는 pugs/ 안에서와 같은 상대 경로로 include 해야 한다.
    const wrapper = `include ../../source/skeleton.pug\n+post({ title: '검사', useMath: true })\n${src}\n`;
    // 파일 이름에 pid 를 넣는다. 여러 담당자가 동시에 이 스크립트를 돌리면
    // 고정 이름은 서로의 임시 파일을 덮어써 검사 결과가 뒤섞인다.
    const tmp = path.join(ROOT, `pugs/fundamental/.check-tmp-${process.pid}.pug`);
    fs.writeFileSync(tmp, wrapper);
    let html = '';
    try {
        html = pug.renderFile(tmp, { cache: false });
        ok(`렌더 성공 (${(html.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
        fail(`렌더 실패: ${String(e.message).split('\n').slice(0, 3).join(' | ')}`);
        fs.unlinkSync(tmp);
        continue;
    }
    fs.unlinkSync(tmp);

    // 2) 수식 구분자
    const o = (src.match(/식\[/g) || []).length;
    const c = (src.match(/\]식/g) || []).length;
    o === c ? ok(`수식 구분자 ${o}쌍`) : fail(`수식 구분자 불일치: 열림 ${o} / 닫힘 ${c}`);

    // 3) 예제와 풀이가 1:1
    const ex = (src.match(/\+example\(/g) || []).length;
    const so = (src.match(/\+solution/g) || []).length;
    ex === so ? ok(`예제 ${ex}개, 풀이 ${so}개`) : fail(`예제 ${ex} / 풀이 ${so} — 짝이 안 맞는다`);
    if (ex === 0) fail('예제가 하나도 없다');

    // 4) 헤딩 레벨 건너뜀
    let prev = 0, skips = 0;
    for (const m of src.matchAll(/^ *h([1-6])(?=[\s.#(:]|$)/gm)) {
        const lv = Number(m[1]);
        if (lv > prev + 1) skips += 1;
        prev = lv;
    }
    skips === 0 ? ok('헤딩 레벨 연속') : fail(`헤딩 레벨 건너뜀 ${skips}건 (h1 다음 h3 등)`);

    // 5) 마크다운 굵게 표기(pug 는 처리하지 않는다)
    const md = (src.match(/\*\*[^*\n]+\*\*/g) || []).length;
    md === 0 ? ok('마크다운 잔재 없음') : fail(`**굵게** 표기 ${md}건 — <b></b> 를 써야 한다`);

    // 6) 수동 예제 번호
    const manual = (src.match(/예제\s*\d+/g) || []).length;
    manual === 0 ? ok('수동 예제 번호 없음') : fail(`"예제 N" 수동 번호 ${manual}건`);

    // 7) 단위 정책
    const unit = src.match(/\\mathrm\{(atm|bar|mmHg|Torr|cal|kcal|M|mM|nM|rpm|ft|lb|psi|hp|Btu)\}|°F|Å|0\.08206/g) || [];
    unit.length === 0 ? ok('금지 단위 없음') : fail(`금지 단위 ${unit.length}건: ${[...new Set(unit)].join(', ')}`);

    // 8) 그림 참조가 실재하는가
    const imgs = [...src.matchAll(/\+w3img\('([^']+)'/g)].map(m => m[1]);
    const missing = imgs.filter(u => !fs.existsSync(path.join(ROOT, u.replace(/^\//, ''))));
    imgs.length === 0
        ? fail('그림이 하나도 없다 — SPEC2 §4 는 그림을 아끼지 말라고 한다')
        : (missing.length === 0 ? ok(`그림 ${imgs.length}개, 파일 모두 존재`) : fail(`없는 그림 파일: ${missing.join(', ')}`));

    // 9) 탭 문자
    (src.includes('\t')) ? fail('탭 문자가 있다 — 들여쓰기는 공백만') : ok('탭 없음');
}

console.log(bad === 0 ? '\n전부 통과' : `\n실패 ${bad}건`);
process.exit(bad ? 1 : 0);
