/**
 * 조립이 끝난 문서(pugs/fundamental/<과목>.pug)를 통째로 검사한다.
 *
 *   node check-doc.mjs pugs/fundamental/biology.pug
 *
 * 장 조각 검사(check-chapter.mjs)가 잡지 못하는 것 — 장 사이의 번호 연속성,
 * 문서 전체의 그림 참조, 환산표가 한 곳뿐인지 — 을 본다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 이 스크립트는 scripts/ 아래에 있으므로 저장소 루트는 한 단계 위다.
// 경로를 박아 두면 다른 기계에서 그대로 쓸 수 없다.
const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
let bad = 0;
const fail = m => { console.log('  X  ' + m); bad += 1; };
const ok = m => console.log('  OK ' + m);

for (const file of process.argv.slice(2)) {
    const src = fs.readFileSync(path.join(ROOT, file), 'utf8');
    const lines = src.split('\n');
    console.log(`\n== ${path.basename(file)} (${lines.length}줄) ==`);

    // 1) 장 수와 제목
    const h1 = [...src.matchAll(/^ {4}h1 (.+)$/gm)].map(m => m[1]);
    console.log(`     장 ${h1.length}개: ${h1.join(' / ')}`);

    // 2) 헤딩 레벨 건너뜀
    let prev = 0;
    const skips = [];
    lines.forEach((l, i) => {
        const m = /^ {4,}h([1-6])(?=[\s.#(:]|$)/.exec(l);
        if (!m) return;
        const lv = Number(m[1]);
        if (lv > prev + 1) skips.push(`${i + 1}행 h${prev}→h${lv}`);
        prev = lv;
    });
    skips.length === 0 ? ok('헤딩 레벨 연속') : fail(`헤딩 건너뜀: ${skips.join(', ')}`);

    // 3) 수식 구분자
    const o = (src.match(/식\[/g) || []).length;
    const c = (src.match(/\]식/g) || []).length;
    o === c ? ok(`수식 구분자 ${o}쌍`) : fail(`수식 구분자 불일치 ${o}/${c}`);

    // 4) 예제와 풀이
    const ex = (src.match(/\+example\(/g) || []).length;
    const so = (src.match(/\+solution/g) || []).length;
    ex === so ? ok(`예제 ${ex}개`) : fail(`예제 ${ex} / 풀이 ${so}`);

    // 5) 그림 파일 실재
    const imgs = [...src.matchAll(/\+w3img\('([^']+)'/g)].map(m => m[1]);
    const missing = imgs.filter(u => !fs.existsSync(path.join(ROOT, u.replace(/^\//, ''))));
    missing.length === 0 ? ok(`그림 ${imgs.length}개 전부 존재`) : fail(`없는 그림: ${missing.join(', ')}`);

    // 6) 마크다운 잔재 / 탭 / 수동 번호
    const md = (src.match(/\*\*[^*\n]+\*\*/g) || []).length;
    md === 0 ? ok('마크다운 잔재 없음') : fail(`**굵게** ${md}건`);
    src.includes('\t') ? fail('탭 문자') : ok('탭 없음');
    const manual = (src.match(/예제\s*\d+/g) || []).length;
    manual === 0 ? ok('수동 예제 번호 없음') : fail(`수동 번호 ${manual}건`);

    // 7) 인자를 이스케이프하는 믹스인 안의 HTML/엔티티
    //    +ths/+tds/+asA/+w3img 는 인자를 이스케이프한다. 인자 안에 태그나 엔티티를
    //    쓰면 화면에 글자 그대로 찍힌다. 따옴표는 ‘ ’ 를 쓴다.
    //    한 줄 전체를 보면 안 된다. 산문 속 <b> 와 인라인 #[+asA(...)] 가 같은 줄에
    //    있는 것이 정상이기 때문이다. 호출의 인자만 떼어 검사한다.
    //    +asA 와 +w3img 의 첫 인자는 URL·경로다. URL 의 & 는 href 로 나갈 때
    //    &amp; 가 되는 것이 정상이므로 첫 인자는 뺀다.
    const escapeArtifact = /<\/?[a-z]+>|&#\d+;|&lt;|&amp;/;
    // 한 줄에서 이스케이프 믹스인 호출들의 '검사 대상 인자'만 모은다.
    const escapedArgs = line => {
        const out = [];
        const call = /\+(ths|tds|asA|w3img)\(/g;
        let m;
        while ((m = call.exec(line)) !== null) {
            const skipFirst = m[1] === 'asA' || m[1] === 'w3img';
            let depth = 1;
            let k = m.index + m[0].length;
            let seen = 0;
            while (k < line.length && depth > 0) {
                const ch = line[k];
                if (ch === "'") {
                    const end = line.indexOf("'", k + 1);
                    if (end < 0) break;
                    seen += 1;
                    if (!(skipFirst && seen === 1)) out.push(line.slice(k + 1, end));
                    k = end + 1;
                    continue;
                }
                if (ch === '(') depth += 1;
                else if (ch === ')') depth -= 1;
                k += 1;
            }
        }
        return out;
    };
    const argHits = lines
        .map((l, i) => [i + 1, l])
        .filter(([, l]) => escapedArgs(l).some(a => escapeArtifact.test(a)));
    argHits.length === 0
        ? ok('이스케이프 믹스인 인자에 HTML 없음')
        : fail(`이스케이프되는 인자 안의 HTML/엔티티 ${argHits.length}건: ${argHits.slice(0, 5).map(([n]) => n + '행').join(', ')}`);

    // 8) 금지 단위 (환산표 절 안은 예외)
    const convStart = lines.findIndex(l => /h3 SI 허용 단위/.test(l));
    const convEnd = convStart < 0 ? -1 : lines.findIndex((l, i) => i > convStart && /^ {4}h[123] /.test(l));
    const banned = /\\mathrm\{(atm|bar|mmHg|Torr|cal|kcal|M|mM|nM|rpm|ft|lb|psi|hp|Btu)\}|°F|Å|0\.08206/;
    const hits = lines
        .map((l, i) => [i + 1, l])
        .filter(([n, l]) => banned.test(l) && !(convStart >= 0 && n > convStart && (convEnd < 0 || n < convEnd)));
    hits.length === 0
        ? ok(`금지 단위 없음 (환산표: ${convStart < 0 ? '없음' : convStart + 1 + '행'})`)
        : fail(`금지 단위 ${hits.length}건: ${hits.slice(0, 5).map(([n]) => n + '행').join(', ')}`);

    // 9) 환산표는 문서당 한 곳
    const convCount = lines.filter(l => /h3 SI 허용 단위|h2 SI 허용 단위/.test(l)).length;
    convCount <= 1 ? ok(`환산표 ${convCount}곳`) : fail(`환산표가 ${convCount}곳 — 한 곳이어야 한다`);

    // 10) d2 도식을 /figures/ 경로로 가리키는 실수
    // 빌드는 .d2 를 제자리(d2/<과목>/)에 렌더한다. 그림 생성기 산출물과 경로가
    // 달라서, 같은 과목 안에 이름이 있으면 사람이 헷갈려 잘못 적는다.
    const d2Stems = new Set();
    (function walk(dir) {
        if (!fs.existsSync(dir)) return;
        for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
            const q = path.join(dir, e.name);
            if (e.isDirectory()) walk(q);
            else if (e.name.endsWith('.d2')) d2Stems.add(path.basename(e.name, '.d2'));
        }
    })(path.join(ROOT, 'd2'));
    const misrouted = imgs.filter((u) => {
        const m = /^\/figures\/[^/]+\/(.+)\.svg$/.exec(u);
        return m != null && d2Stems.has(m[1]);
    });
    misrouted.length === 0
        ? ok('d2 도식 경로 정상')
        : fail(`d2 도식을 /figures/ 로 가리킨다: ${misrouted.join(', ')}`);

    // 12) 일본어 웹폰트 서브셋이 낡았는지
    //     fonts/japanese/*.woff2 는 이 문서가 쓰는 글자만 담고 있다. 새 글자를
    //     쓰면 폰트에 없어 폴백되는데, 폴백은 아무 경고도 내지 않고 한국 자형으로
    //     그려진 한자는 무심히 보면 알아채기 어렵다. 그래서 여기서 잡는다.
    //     다시 만드는 법은 fonts/japanese/README.md.
    const coverageFile = path.join(ROOT, 'fonts', 'japanese', 'COVERAGE.txt');
    const jpCalls = [...src.matchAll(/\+jp\(\s*'([^']*)'(?:\s*,\s*'([^']*)')?\s*\)/g)];
    if (jpCalls.length !== 0) {
        if (!fs.existsSync(coverageFile)) {
            fail(`+jp 를 ${jpCalls.length}번 쓰는데 ${path.relative(ROOT, coverageFile)} 가 없다`);
        } else {
            // 첫 줄들의 주석(#)을 뺀 나머지가 문자 목록이다.
            const covered = new Set(
                fs.readFileSync(coverageFile, 'utf8')
                    .split('\n').filter((l) => !l.startsWith('#')).join(''),
                // 줄바꿈만 없애야 한다. /\s/ 는 전각 공백(U+3000)까지 지우는데
                // 그것은 이 문서가 실제로 쓰고 폰트에도 들어 있는 글자다.
            );
            const used = new Set();
            for (const m of jpCalls) for (const ch of (m[1] ?? '') + (m[2] ?? '')) used.add(ch);
            // 라틴·숫자·공백은 서브셋에 일부러 넣지 않았다. 사이트 기본 폰트가 그린다.
            const missing = [...used].filter((c) => c.codePointAt(0) > 0x2000 && !covered.has(c));
            missing.length === 0
                ? ok(`일본어 폰트 서브셋이 문서를 덮는다 (쓰는 글자 ${used.size}종)`)
                : fail(`폰트 서브셋에 없는 글자 ${missing.length}자 — 조용히 한국 자형으로 그려진다. `
                    + `fonts/japanese/README.md 의 절차로 다시 만들어라: ${missing.join('')}`);
        }
    }

    // 13) 일본어가 +jp 밖에 있는지
    //     맨 span(lang='ja') 이나 그냥 적은 일본어는 lang 이 붙지 않아 한국 자형으로
    //     그려진다. 옆 글자와 견주면 보이지만 혼자 있으면 안 보인다.
    if (jpCalls.length !== 0) {
        const JP = /[ぁ-ヿ一-鿿]/;
        const naked = lines
            .map((l, i) => [i + 1, l])
            .filter(([, l]) => JP.test(l) && !/^\s*\/\/-?/.test(l))
            .filter(([, l]) => JP.test(l.replace(/\+jp\(\s*'[^']*'(\s*,\s*'[^']*')?\s*\)/g, '')));
        naked.length === 0
            ? ok('일본어가 전부 +jp 안에 있다')
            : fail(`+jp 밖의 일본어 ${naked.length}건 — 한국 자형으로 그려진다: `
                + naked.slice(0, 5).map(([n]) => n + '행').join(', '));
    }

    // 11) 생성된 HTML 안의 자원 참조
    const html = path.join(ROOT, 'posts', file.replace(/^pugs\//, '').replace(/\.pug$/, '.html'));
    if (fs.existsSync(html)) {
        const h = fs.readFileSync(html, 'utf8');
        // 경로는 인코딩된 상태로 적혀 있다(공백이 %20). 디코드하지 않으면
        // 파일이 있는데도 없다고 나온다.
        const srcs = [...h.matchAll(/(?:src|href)="(\/[^"#?]+\.(?:svg|png|jpg|webp|css|js|woff2))"/g)]
            .map(m => decodeURIComponent(m[1]));
        const gone = [...new Set(srcs)].filter(u => !fs.existsSync(path.join(ROOT, u.slice(1))));
        gone.length === 0
            ? ok(`HTML 자원 ${new Set(srcs).size}종 전부 존재`)
            : fail(`HTML 에서 없는 자원: ${gone.join(', ')}`);
        // 이스케이프 사고는 렌더된 HTML 에서 이중 이스케이프로 나타난다.
        // <b> 는 &lt;b&gt; 로, &#34; 는 &amp;#34; 로, & 는 &amp;amp; 로 찍힌다.
        const rawHits = h.match(/&lt;\/?[a-z]+&gt;|&amp;#\d+;|&amp;amp;|&amp;lt;|&amp;gt;/g) || [];
        rawHits.length === 0
            ? ok('이스케이프 사고 없음')
            : fail(`화면에 태그·엔티티가 찍히는 곳 ${rawHits.length}건: ${[...new Set(rawHits)].slice(0, 5).join(' ')}`);
    } else {
        fail(`생성된 HTML 이 없다: ${html}`);
    }
}

console.log(bad === 0 ? '\n전부 통과' : `\n실패 ${bad}건`);
process.exit(bad ? 1 : 0);
