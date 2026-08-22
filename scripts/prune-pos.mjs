/**
 * 헤딩이 이미 만들어 주는 앵커와 이름이 같은 `+pos()` 를 찾아 지운다.
 *
 *   node scripts/prune-pos.mjs          # 무엇을 지울지 보기만 한다
 *   node scripts/prune-pos.mjs --apply  # 실제로 지운다
 *
 * `source/default.js` 의 `updateMarkerList()` 가 페이지를 열 때 h1~h6 앞에
 * `pos-span` 을 심는다. 그러므로 헤딩 텍스트와 같은 이름의 `+pos()` 는
 *
 *   - 없어도 링크가 닿고
 *   - 있으면 같은 id 를 가진 요소가 둘이 되어 HTML 이 어긋난다
 *
 * **빌드가 끝난 뒤에 돌려야 한다.** 판단 근거가 렌더된 HTML 이다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { hashCode, runtimeMarkerIds } from './lib-markers.mjs';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const APPLY = process.argv.includes('--apply');

function collect(dir, ext) {
    const out = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) out.push(...collect(p, ext));
        else if (e.name.endsWith(ext)) out.push(p);
    }
    return out;
}

const pugs = [path.join(ROOT, 'index.pug'), ...collect(path.join(ROOT, 'pugs'), '.pug')]
    .filter((p) => fs.existsSync(p));

let removed = 0;
let kept = 0;
const keptSamples = [];

for (const pug of pugs) {
    const rel = path.relative(ROOT, pug).split(path.sep).join('/');
    const html = path.join(ROOT, rel.replace(/^pugs\//, 'posts/').replace(/\.pug$/, '.html'));
    if (!fs.existsSync(html)) continue;

    const runtime = runtimeMarkerIds(fs.readFileSync(html, 'utf8'));
    const lines = fs.readFileSync(pug, 'utf8').split('\n');
    const out = [];
    let fileRemoved = 0;

    for (const line of lines) {
        // 줄 하나가 통째로 `+pos('이름')` 인 경우만 다룬다. 산문 안에 인라인으로 부른
        // `#[+pos(...)]` 는 지우면 문장이 깨질 수 있어 손대지 않는다.
        const m = line.match(/^(\s*)\+pos\((['"])(.*?)\2\)\s*$/);
        if (m == null) {
            out.push(line);
            continue;
        }
        const name = m[3];
        if (runtime.has(`pos${hashCode(name)}`)) {
            fileRemoved += 1;
            removed += 1;
            continue;                       // 지운다
        }
        kept += 1;
        if (keptSamples.length < 12) keptSamples.push(`${rel}  +pos('${name}')`);
        out.push(line);
    }

    if (fileRemoved !== 0) {
        console.log(`${rel}  −${fileRemoved}`);
        if (APPLY) fs.writeFileSync(pug, out.join('\n'));
    }
}

console.log('');
console.log(`지울 것 ${removed}개, 남길 것 ${kept}개`);
if (keptSamples.length !== 0) {
    console.log('남기는 것(헤딩과 이름이 다르거나 헤딩이 아닌 자리):');
    for (const s of keptSamples) console.log(`  ${s}`);
}
if (!APPLY) console.log('\n실제로 지우려면 --apply');
