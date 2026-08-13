/**
 * 사이트 전체의 내부 링크가 실제 목적지를 가리키는지 확인한다.
 *
 *   node check-links.mjs           # posts/ 아래 전부
 *   node check-links.mjs posts/fundamental/physics.html
 *
 * `+goto(name, href)` 는 `href#pos<해시>` 로 가는 링크를 만들고 그 목적지에는
 * `+pos(name)` 이 있어야 한다. 헤딩만 있고 앵커가 없으면 링크가 빈 곳으로 떨어지는데,
 * 브라우저는 아무 오류도 내지 않으므로 사람이 눌러 보기 전까지 드러나지 않는다.
 * 그래서 기계가 본다.
 *
 * 빌드가 끝난 뒤(posts/*.html 이 최신인 상태에서) 돌려야 한다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');

/** posts/ 아래 모든 .html 을 모은다. */
function collect(dir) {
    const out = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) out.push(...collect(p));
        else if (e.name.endsWith('.html')) out.push(p);
    }
    return out;
}

const files = process.argv.length > 2
    ? process.argv.slice(2).map((f) => path.join(ROOT, f))
    : collect(path.join(ROOT, 'posts'));

/** 문서마다 가진 id 집합을 미리 만들어 둔다. */
const idsOf = new Map();
for (const f of files) {
    const html = fs.readFileSync(f, 'utf8');
    idsOf.set(f, new Set([...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1])));
}

/**
 * `+goto(name)` 이 만드는 해시를 되짚어 이름을 보여 주기 위한 표.
 * 해시만 적힌 오류 메시지("#pos1399817165 없음")로는 어디를 고쳐야 할지 알 수 없다.
 * skeleton.pug 의 String.prototype.hashCode 와 같은 계산이어야 한다.
 */
function hashCode(s) {
    let h = 0;
    for (let i = 0; i < s.length; i += 1) {
        h = (h << 5) - h + s.charCodeAt(i);
        h |= 0;
    }
    return h;
}

const nameOfHash = new Map();
(function collectNames(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) collectNames(p);
        else if (e.name.endsWith('.pug')) {
            const src = fs.readFileSync(p, 'utf8');
            for (const m of src.matchAll(/\+(?:goto|pos)\('([^']+)'/g)) {
                nameOfHash.set(`pos${hashCode(m[1])}`, m[1]);
            }
        }
    }
})(path.join(ROOT, 'pugs'));

const describe = (hash) => (nameOfHash.has(hash) ? ` (‘${nameOfHash.get(hash)}’)` : '');

let bad = 0;
let checked = 0;

for (const f of files) {
    const html = fs.readFileSync(f, 'utf8');
    const rel = path.relative(ROOT, f);
    const broken = [];

    for (const m of html.matchAll(/href="(\/[^"]*|#[^"]*)"/g)) {
        const href = m[1];
        const [rawPath, hash] = href.split('#');
        // 목적지 문서를 정한다. 경로가 비어 있으면 자기 자신이다.
        const targetFile = rawPath === '' ? f : path.join(ROOT, decodeURIComponent(rawPath).slice(1));

        // 이 스크립트가 보는 것은 앵커다. 그림·폰트 등 자원의 존재는
        // check-doc.mjs 와 verify-pages.mjs 가 각각 다른 각도에서 이미 본다.
        if (!targetFile.endsWith('.html')) continue;
        if (rawPath !== '' && !fs.existsSync(targetFile)) { broken.push(`${href} — 문서 없음`); continue; }
        if (!hash) continue;
        checked += 1;

        let ids = idsOf.get(targetFile);
        if (ids == null) {
            ids = new Set([...fs.readFileSync(targetFile, 'utf8').matchAll(/\sid="([^"]+)"/g)].map((x) => x[1]));
            idsOf.set(targetFile, ids);
        }
        if (!ids.has(hash)) broken.push(`${href}${describe(hash)} — 앵커 없음`);
    }

    if (broken.length !== 0) {
        console.log(`\n== ${rel} ==`);
        for (const b of [...new Set(broken)]) console.log('  X  ' + b);
        bad += new Set(broken).size;
    }
}

console.log(bad === 0
    ? `\n앵커 링크 ${checked}개 전부 목적지가 있다 (문서 ${files.length}개)`
    : `\n깨진 링크 ${bad}건`);
process.exit(bad ? 1 : 0);
