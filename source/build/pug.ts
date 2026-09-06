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
 *   - `sitemap.txt`
 *
 * 해시 검사의 입력에는 **그 문서가 include 하는 파일까지** 넣는다.
 */
import { existsSync } from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
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

const POSTS_FILE = 'source/posts.json';
/** 저장소의 GitHub 주소. 홈의 최근 커밋 표가 sha 를 여기로 건다. */
const REPO_URL = 'https://github.com/Dong-gi/Dong-gi.github.io';

/** source/posts.json 의 항목. 파일 자체는 이 항목의 배열이다. */
interface Post {
    /** 소속 카테고리. 계층은 '/' 로 표현한다. */
    category: string;
    file: string;
    title: string;
}

/** 홈에 싣는 커밋 한 건. */
interface Commit {
    sha: string;
    shortSha: string;
    url: string;
    /** KST `yyyy-MM-dd HH:mm`. */
    when: string;
    posts: { href: string; title: string }[];
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

const run = promisify(execFile);

/** KST 기준 `yyyy-MM-dd HH:mm`. 커밋 시각은 고정된 자료라 시각까지 적는다. */
function kstDateTime(iso: string): string {
    const d = new Date(iso);
    const date = d.toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });
    const time = d.toLocaleTimeString('en-GB', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit' });
    return `${date} ${time}`;
}

/**
 * 홈의 &#34;최근 수정&#34; 표에 실을 커밋을 git 이력에서 뽑는다.
 *
 * 예전에는 브라우저가 `posts-compressed.json` 의 갱신일로 목록을 만들었고, 그 갱신일을
 * 채우려고 git 이력을 훑는 빌드 요소(dates)가 따로 있었다. 이제 빌드 때 한 번 뽑아
 * HTML 에 박는다.
 *
 * **문서를 많이 건드린 커밋은 뺀다.** 규격을 손보거나 일괄 치환한 커밋이 목록을
 * 통째로 차지해 &#34;무엇이 새로 쓰였는가&#34; 를 가리기 때문이다.
 *
 * git 이 없거나 저장소가 아니면 빈 배열이다. 표가 비는 것이 빌드를 세우는 것보다 낫다.
 */
async function recentCommitsOf(posts: Post[], limit = 5, maxFiles = 5): Promise<Commit[]> {
    const titleOf = new Map(posts.map((p) => [p.file, p.title]));
    let stdout: string;
    try {
        ({ stdout } = await run(
            'git',
            // 경로를 pugs/ 와 index.pug 로 좁힌다. source/skeleton.pug 같은 틀은 포스트가
            // 아니고, 틀을 고친 커밋은 어차피 문서 전부를 건드린 것으로 세어져 걸러진다.
            ['log', '--format=%H%x09%cI', '--name-only', '--diff-filter=ACMR', '--', 'pugs', 'index.pug'],
            { cwd: resolve('.'), maxBuffer: 64 * 1024 * 1024 },
        ));
    } catch {
        return [];
    }

    const out: Commit[] = [];
    let sha = '';
    let iso = '';
    let files: string[] = [];
    const flush = (): void => {
        if (sha === '' || files.length === 0 || files.length > maxFiles) return;
        if (out.length >= limit) return;
        out.push({
            sha,
            shortSha: sha.slice(0, 10),
            url: `${REPO_URL}/commit/${sha}`,
            when: kstDateTime(iso),
            posts: files.map((f) => {
                if (f === 'index.pug') return { href: '/', title: '홈' };
                const file = f.replace(/^pugs\//, '').replace(/\.pug$/, '.html');
                // posts.json 에 없는 문서는 경로를 그대로 보인다. 등록을 빠뜨린 것이
                // 눈에 띄는 편이 낫다.
                return { href: POSTS_URL_PREFIX + file, title: titleOf.get(file) ?? file };
            }),
        });
    };
    for (const line of stdout.split('\n')) {
        const head = line.match(/^([0-9a-f]{40})\t(.+)$/);
        if (head != null) {
            flush();
            if (out.length >= limit) break;
            sha = head[1];
            iso = head[2];
            files = [];
            continue;
        }
        const file = line.trim();
        if (file.endsWith('.pug') && (file === 'index.pug' || file.startsWith('pugs/'))) {
            files.push(normalize(file));
        }
    }
    flush();
    return out;
}

/** 모든 문서에 함께 넘기는 값. 렌더 시작 전에 한 번 채운다. */
let locals: Record<string, unknown> = {};

async function renderOne(log: BuildLog, source: string, target: string): Promise<void> {
    const t0 = Date.now();
    let html = pug.renderFile(resolve(source), { cache: true, ...locals });
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

await runComponent('pug', async (log) => {
    const posts: Post[] = JSON.parse(await fsp.readFile(resolve(POSTS_FILE), 'utf8'));
    const recentCommits = await recentCommitsOf(posts);
    if (recentCommits.length === 0) log.line('git 이력을 읽지 못해 홈의 최근 커밋 표를 비운다');
    locals = { recentCommits };

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
        const included = await includedFiles(source);
        jobs.push({
            key: source,
            // 홈의 최근 커밋 표는 git 이력에서 나온다. 커밋을 해도 index.pug 자체는
            // 바뀌지 않으므로 해시로는 낡음을 알 수 없다. 한 장이라 매번 렌더한다.
            always: source === 'index.pug',
            // 참조하는 그림도 입력이다. 없는 파일도 그대로 넣는다 — hashOf 가 '없음'
            // 으로 다루므로, 나중에 그림이 도착하면 해시가 달라져 이 문서만 다시 돈다.
            inputs: [source, ...included],
            outputs: [target],
            run: () => renderOne(log, source, target),
        });
    }

    const report = await runIncremental({
        name: 'pug',
        log,
        jobs,
        orphanScan: { dirs: ['posts'], match: (f) => f.endsWith('.html') },
    });

    // ---- posts-compressed.json 과 sitemap.txt
    //
    // 렌더 결과와 무관하게 매번 다시 쓴다. posts.json 만 있으면 만들 수 있고 비용이
    // 없어서, 건너뛸 조건을 따지는 쪽이 오히려 틀리기 쉽다.
    posts.sort((a, b) => a.file.localeCompare(b.file));
    const missing = posts.filter((p) => !existsSync(resolve('pugs/' + p.file.replace(/\.html$/, '.pug'))));
    // 한 줄로 묶는다. 하나씩 경고하면 로그 끝이 경고로 뒤덮여 정작 오류가 안 보인다.
    if (missing.length !== 0) {
        log.warn(`${POSTS_FILE} 에 등록됐지만 원본이 없는 문서 ${missing.length}개: ${missing.slice(0, 5).map((p) => p.file).join(', ')}${missing.length > 5 ? ' …' : ''}`);
    }

    await fsp.writeFile(resolve('source/posts-compressed.json'), JSON.stringify(posts));
    await ensureDirFor('sitemap.txt');
    await fsp.writeFile(
        resolve('sitemap.txt'),
        posts.map((p) => SITE_ORIGIN + POSTS_URL_PREFIX + p.file).sort().join('\n'),
    );
    log.line(`source/posts-compressed.json, sitemap.txt 갱신 — 문서 ${posts.length}개`);

    if (report.failed.length !== 0) {
        throw new ToolError(`렌더 실패 ${report.failed.length}개: ${report.failed.join(', ')}`);
    }
    return report.ran === 0 ? `변경 없음 (${report.skipped}개)` : `갱신 ${report.ran} · 건너뜀 ${report.skipped}`;
});
