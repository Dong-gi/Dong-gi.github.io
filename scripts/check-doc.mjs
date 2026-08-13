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

    // 7) 표 칸 안의 HTML — +ths/+tds 는 인자를 이스케이프하므로 태그가 그대로 찍힌다
    const tableHtml = lines
        .map((l, i) => [i + 1, l])
        .filter(([, l]) => /\+t(ds|hs)\(/.test(l) && /<\/?[a-z]+>|&#\d+;|&lt;|&amp;/.test(l));
    tableHtml.length === 0
        ? ok('표 칸에 HTML 없음')
        : fail(`표 칸의 HTML/엔티티 ${tableHtml.length}건: ${tableHtml.slice(0, 5).map(([n]) => n + '행').join(', ')}`);

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
        const raw = (h.match(/&lt;b&gt;|&amp;#34;/g) || []).length;
        raw === 0 ? ok('이스케이프 사고 없음') : fail(`화면에 태그가 찍히는 곳 ${raw}건`);
    } else {
        fail(`생성된 HTML 이 없다: ${html}`);
    }
}

console.log(bad === 0 ? '\n전부 통과' : `\n실패 ${bad}건`);
process.exit(bad ? 1 : 0);
