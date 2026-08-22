/**
 * 빌드 요소: pug
 *
 *     npm run build-pug        → source/build/build-pug.log
 *
 * `index.pug` 와 `pugs/**​/*.pug` 를 `index.html` 과 `posts/**​/*.html` 로 렌더한다.
 * `useMath` 인 문서는 이 자리에서 수식까지 조판한다(`lib/math.ts`).
 *
 * 곁들여 두 가지를 더 쓴다. 둘 다 `source/posts.json` 에서 나오므로 여기 있는 것이 맞다.
 *
 *   - `source/posts-compressed.json` — 브라우저가 받아 사이드바 목록을 만든다.
 *   - `files/sitemap.txt`
 *
 * 이 요소가 읽는 다른 요소의 산출물은 `source/img-map.json`(img 요소)과
 * `source/doc-dates.json`(dates 요소)이다. 그래서 `source/build.ts` 는 이 요소를
 * 그 둘 뒤에 돌린다.
 *
 * 해시 검사의 입력에는 **그 문서가 include 하는 파일까지** 넣는다. 거의 모든 문서가
 * `source/skeleton.pug` 를 include 하므로, skeleton 을 고치면 전체가 다시 렌더된다.
 * 그 판단을 사람이 기억할 필요가 없게 하려는 것이다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { runComponent, ToolError, type BuildLog } from './lib/log.ts';
import { runIncremental, type Job } from './lib/manifest.ts';
import { MATH_MARKER, prerenderMath, recycleMathJaxIfHeavy } from './lib/math.ts';
import { ensureDirFor, fileExists, normalize, resolve, walkFiles } from './lib/paths.ts';

const require = createRequire(import.meta.url);
const pug = require('pug') as { renderFile(file: string, options?: Record<string, unknown>): string };

/** 사이트 오리진. 사이트맵 등 절대 URL 생성에 쓴다. */
const SITE_ORIGIN = 'https://dong-gi.github.io';
/** posts.json 의 file 값은 'dev/aws.html' 형태이므로 URL 생성 시 이 접두사가 필요하다. */
const POSTS_URL_PREFIX = '/posts/';

const IMG_MAP_FILE = 'source/img-map.json';
const DOC_DATES_FILE = 'source/doc-dates.json';
const POSTS_FILE = 'source/posts.json';

/** source/posts.json 의 항목. 파일 자체는 이 항목의 배열이다. */
interface Post {
    /** 소속 카테고리. 계층은 '/' 로, 다중 소속은 원소 여러 개로 표현한다. */
    category: string[];
    file: string;
    title: string;
    /** 빌드가 doc-dates 에서 채운다. 파일에는 없다. */
    mtimeMs?: number;
}

/**
 * `include` / `extends` 로 끌어오는 파일을 재귀로 모은다.
 *
 * pug 의 렉서를 쓰지 않고 줄 단위로 훑는다. 대신 **실제로 존재하는 파일만** 의존으로
 * 인정한다. 이 저장소의 문서에는 코드 예제 안에 `include '**​/*UT.class'` 같은 줄이
 * 있어서, 이 확인이 없으면 있지도 않은 파일을 의존으로 잡는다. 반대 방향의 실수는
 * 일어나지 않는다 — 진짜 include 는 언제나 존재하는 파일을 가리키기 때문이다.
 */
async function includedFiles(rel: string, seen = new Set<string>()): Promise<string[]> {
    if (seen.has(rel)) return [];
    seen.add(rel);

    let text: string;
    try {
        text = await fsp.readFile(resolve(rel), 'utf8');
    } catch {
        return [];
    }
    const found: string[] = [];
    for (const m of text.matchAll(/^[ \t]*(?:include|extends)(?::\S+)?[ \t]+(\S.*?)[ \t]*$/gm)) {
        let target = m[1];
        if (path.extname(target) === '') target += '.pug';
        const dep = normalize(path.join(path.dirname(rel), target));
        if (seen.has(dep) || !(await fileExists(dep))) continue;
        found.push(dep, ...(await includedFiles(dep, seen)));
    }
    return found;
}

/** `pugs/fundamental/physics.pug` → `posts/fundamental/physics.html`, `index.pug` → `index.html` */
function outputOf(rel: string): string {
    return rel.replace(/^pugs\//, 'posts/').replace(/\.pug$/, '.html');
}

async function renderOne(log: BuildLog, source: string, target: string): Promise<void> {
    const t0 = Date.now();
    let html = pug.renderFile(resolve(source), { cache: true, imgMap });
    let mathMs = 0;
    if (html.includes(MATH_MARKER)) {
        const t1 = Date.now();
        html = await prerenderMath(html);
        mathMs = Date.now() - t1;
    }
    // 큰 수식 문서를 이어서 조판하면 MathJax 가 붙든 것이 쌓여 프로세스가 죽는다.
    // 자세한 사정은 lib/math.ts 의 RSS_LIMIT_BYTES 주석에 있다.
    const recycled = recycleMathJaxIfHeavy();
    if (recycled != null) {
        log.line(`MathJax 를 새로 세운다 — RSS ${(recycled / 1024 / 1024).toFixed(0)}MB`);
    }
    await ensureDirFor(target);
    await fsp.writeFile(resolve(target), html);
    const math = mathMs === 0 ? '' : `, 수식 ${mathMs}ms`;
    log.line(`${target}  ${(html.length / 1024).toFixed(0)}KB (${Date.now() - t0}ms${math})`);
}

/** renderOne 이 pug 에 넘길 전역. 문서가 `+w3img` 에서 쓴다. */
let imgMap: Record<string, { width: number; height: number }> = {};

await runComponent('pug', async (log) => {
    imgMap = JSON.parse(await fsp.readFile(resolve(IMG_MAP_FILE), 'utf8').catch(() => '{}'));
    const posts: Post[] = JSON.parse(await fsp.readFile(resolve(POSTS_FILE), 'utf8'));

    const sources = ['index.pug', ...(await walkFiles('pugs'))].filter((f) => f.endsWith('.pug'));
    // pugs/ 는 문서의 원본만 두는 곳이다. 그림은 figures/ 와 d2/ 에 있고 문서는 경로로
    // 참조한다. 다른 확장자가 있으면 렌더되지 않으므로, 실수인지 알 수 있게 알린다.
    const nonPug = (await walkFiles('pugs')).filter((f) => !f.endsWith('.pug'));
    if (nonPug.length !== 0) {
        log.warn(`pugs/ 아래에 .pug 가 아닌 파일이 ${nonPug.length}개 있다(렌더하지 않는다): ${nonPug.slice(0, 5).join(', ')}`);
    }

    const jobs: Job[] = [];
    for (const source of sources) {
        const target = outputOf(source);
        // 경로 치환이 어긋나면 pugs/ 안에 HTML 을 쓰게 된다. 조용히 소스를 오염시키는
        // 대신 즉시 실패시킨다. Windows 에서 실제로 이 상태였다.
        if (target !== 'index.html' && !target.startsWith('posts/')) {
            throw new Error(`${source} -> ${target} : 산출물 경로가 posts/ 밖이다`);
        }
        jobs.push({
            key: source,
            inputs: [source, ...(await includedFiles(source))],
            outputs: [target],
            run: () => renderOne(log, source, target),
        });
    }

    const report = await runIncremental({
        name: 'pug',
        log,
        jobs,
        // 모든 문서가 함께 보는 입력. 어느 문서의 것이라고 말하기 어렵다.
        shared: [IMG_MAP_FILE],
        orphanScan: { dirs: ['posts'], match: (f) => f.endsWith('.html') },
    });

    // ---- posts-compressed.json 과 sitemap.txt
    //
    // 렌더 결과와 무관하게 매번 다시 쓴다. posts.json 과 갱신일만 있으면 만들 수 있고
    // 비용이 없어서, 건너뛸 조건을 따지는 쪽이 오히려 틀리기 쉽다.
    const docDates: Record<string, number> = JSON.parse(
        await fsp.readFile(resolve(DOC_DATES_FILE), 'utf8').catch(() => '{"dates":{}}'),
    ).dates ?? {};

    posts.sort((a, b) => a.file.localeCompare(b.file));
    const missing: string[] = [];
    for (const post of posts) {
        const source = 'pugs/' + post.file.replace(/\.html$/, '.pug');
        // 홈의 "최근 갱신" 목록도 docModified() 와 같은 기준을 쓴다. git 이력에 없는
        // 문서만 파일 mtime 으로 대체한다.
        const recorded = docDates[source];
        if (recorded != null) {
            post.mtimeMs = recorded;
            continue;
        }
        const stats = await fsp.stat(resolve(source)).catch(() => null);
        if (stats == null) {
            missing.push(source);
            continue;
        }
        post.mtimeMs = Math.floor(stats.mtimeMs);
    }
    // 한 줄로 묶는다. 하나씩 경고하면 로그 끝이 경고로 뒤덮여 정작 오류가 안 보인다.
    if (missing.length !== 0) {
        log.warn(`${POSTS_FILE} 에 등록됐지만 원본이 없는 문서 ${missing.length}개: ${missing.slice(0, 5).join(', ')}${missing.length > 5 ? ' …' : ''}`);
    }

    await fsp.writeFile(resolve('source/posts-compressed.json'), JSON.stringify(posts));
    await ensureDirFor('files/sitemap.txt');
    await fsp.writeFile(
        resolve('files/sitemap.txt'),
        posts.map((p) => SITE_ORIGIN + POSTS_URL_PREFIX + p.file).sort().join('\n'),
    );
    log.line(`source/posts-compressed.json, files/sitemap.txt 갱신 — 문서 ${posts.length}개`);

    if (report.failed.length !== 0) {
        throw new ToolError(`렌더 실패 ${report.failed.length}개: ${report.failed.join(', ')}`);
    }
    return report.ran === 0 ? `변경 없음 (${report.skipped}개)` : `갱신 ${report.ran} · 건너뜀 ${report.skipped}`;
});
