import fsp from 'node:fs/promises';
import path from 'node:path';
import child_process from 'node:child_process';
import { promisify } from 'node:util';
import { Worker, isMainThread, parentPort } from 'node:worker_threads';
import { createRequire } from 'node:module';
import { cpus } from 'node:os';
import sharp, { type Sharp } from 'sharp';
import * as svgo from 'svgo';

type WorkMessage =
    | { api: 'render-pug' | 'transform-img' | 'render-d2'; path: string }
    | {
          api: 'init';
          generatedPaths: string[];
          imgMap: Record<string, { width: number; height: number }>;
          docDates: Record<string, string>;
      };

interface Post {
    /** 단일 소속은 문자열, 다중 소속은 문자열 배열. 계층은 '/' 로 표현한다. */
    category: string | string[];
    file: string;
    title: string;
    mtimeMs?: number;
}

interface Posts {
    list: Post[];
}

/** 사이트 오리진. 사이트맵 등 절대 URL 생성에 쓴다. */
const SITE_ORIGIN = 'https://dong-gi.github.io';
/** posts.json 의 file 값은 'dev/aws.html' 형태이므로 URL 생성 시 이 접두사가 필요하다. */
const POSTS_URL_PREFIX = '/posts/';

/** 포스트의 사이트 절대 URL을 만든다. */
function postUrl(post: Post): string {
    return SITE_ORIGIN + POSTS_URL_PREFIX + post.file;
}

/**
 * 렌더 산출물 경로를 사이트 절대 URL로 바꾼다.
 * './posts/dev/aws.html' -> 'https://dong-gi.github.io/posts/dev/aws.html'
 * './index.html'         -> 'https://dong-gi.github.io/'
 */
function pageUrlOf(htmlPath: string): string {
    const absolute = htmlPath.replace(/^\./, '').replace(/\/index\.html$/, '/');
    return SITE_ORIGIN + absolute;
}

/**
 * 문서의 갱신일을 구한다.
 *
 * 기본은 source/doc-dates.json 에 기록된 git 이력 기반 날짜다. 대량 리팩터링 커밋과
 * 공백만 바뀐 변경을 걸러낸 값이라 머신·클론과 무관하게 항상 같다.
 *
 * 그 파일에 없는 문서(새로 만들고 아직 npm run dates 를 안 돌린 경우)만 mtime 으로
 * 대체한다. mtime 은 새로 클론하면 체크아웃 시각이 되므로 신뢰할 수 없다.
 *
 * @param pugPath './pugs/dev/aws.pug' 형태
 */
async function docModified(pugPath: string): Promise<string> {
    const key = pugPath.replace(/^\.\//, '');
    const recorded = workerDocDates[key];
    if (recorded != null) return recorded;
    const { mtime } = await fsp.stat(pugPath);
    return mtime.toISOString();
}

const require = createRequire(import.meta.url);
const exec = promisify(child_process.exec);
const renderFile = promisify(require('pug').renderFile) as (path: string, options?: Record<string, unknown>) => Promise<string>;
const workers = isMainThread ? cpus().map(() => new Worker(import.meta.filename)) : [];

// 워커 스레드 영역
let remainWorkCount = 0;
let unrefTimeout: NodeJS.Timeout;
let generatedImgSet: Set<string> = new Set();
let workerImgMap: Record<string, { width: number; height: number }> = {};
/** 'pugs/dev/aws.pug' -> ISO 날짜. source/doc-dates.json 의 내용. */
let workerDocDates: Record<string, string> = {};
parentPort?.on('message', async (o: WorkMessage) => {
    clearTimeout(unrefTimeout);
    remainWorkCount += 1;
    switch (o.api) {
        case 'init': {
            generatedImgSet = new Set(o.generatedPaths);
            workerImgMap = o.imgMap;
            workerDocDates = o.docDates;
            break;
        }
        case 'render-d2': {
            const svgPath = o.path.replace(/\.d2$/, '.svg');
            await exec(`d2 "${o.path}" "${svgPath}"`);
            console.log(`${o.path} rendered`);

            const svg = await fsp.readFile(svgPath);
            let svgTxt = svgo.optimize(
                svg
                    .toString()
                    // 불필요 속성 제거
                    .replace(/data-d2-version="[^"]+"/, '')
                    .replace(/\{[^}]*font-family[^}]*\}/g, '{}')
                    .replace(/stroke-width: *0;?/g, '')
                    .replace(/ rx="0"/g, '')
                    .replace(/ stroke-width="0"/g, ''),
            ).data;
            const styleTxt = svgTxt.match(/<style>.+<\/style>/)![0];
            for (const classMatch of svgTxt.matchAll(/class="([^ ]+?)"/g)) {
                if (styleTxt.includes(classMatch[1])) {
                    continue;
                }
                // 미사용 클래스 제거
                svgTxt = svgTxt.replaceAll(classMatch[0], '');
            }
            // 공백 정규화
            svgTxt = svgTxt.replace(/\s+/g, ' ');
            // 미사용 클래스 제거
            svgTxt = svgTxt.replace(/class="text /g, 'class="');
            // 외부 중복 <svg> 래퍼 제거, xmlns를 내부 svg로 이동
            svgTxt = svgTxt.replace(/<svg (xmlns="[^"]*")[^>]*?><svg /, '<svg $1 ');
            svgTxt = svgTxt.replace(/<\/svg><\/svg>/, '</svg>');
            // CSS 클래스와 중복되는 인라인 fill/stroke 속성 제거
            svgTxt = svgTxt.replace(/<[^>]+>/g, (tag) => {
                const cls = tag.match(/class="([^"]*)"/);
                if (cls == null) return tag;
                if (/\bfill-/.test(cls[1])) tag = tag.replace(/ fill="[^"]*"/, '');
                if (/\bstroke-/.test(cls[1])) tag = tag.replace(/ stroke="[^"]*"/, '');
                return tag;
            });
            // 속성 없는 빈 <g> 래퍼 제거 (안쪽부터 반복)
            while (true) {
                const beforeLength = svgTxt.length;
                svgTxt = svgTxt.replace(/<g *>((?:(?!<g[ >]).)*?)<\/g *>/g, '$1');
                if (svgTxt.length === beforeLength) {
                    break;
                }
            }
            // 반복되는 inline style을 CSS class로 압축
            const styleCounts = new Map<string, number>();
            for (const m of svgTxt.matchAll(/ style="([^"]+)"/g)) {
                styleCounts.set(m[1], (styleCounts.get(m[1]) ?? 0) + 1);
            }
            if (styleCounts.size !== 0 && /<style>(.+?)<\/style>/.test(svgTxt)) {
                let classIdx = 0;
                let cssInsert = '';
                const styleToClass = new Map<string, string>();
                for (const [style, count] of styleCounts) {
                    if (count < 2) continue;
                    const cls = `s${classIdx++}`;
                    cssInsert += `.${cls}{${style}}`;
                    styleToClass.set(style, cls);
                }
                if (cssInsert) {
                    svgTxt = svgTxt.replace('</style>', cssInsert + '</style>');
                    // 태그 단위로 style을 class에 병합
                    svgTxt = svgTxt.replace(/<[^>]+ style="[^"]*"[^>]*>/g, (tag) => {
                        const styleMatch = tag.match(/ style="([^"]*)"/)!;
                        if (!styleToClass.has(styleMatch[1])) return tag;
                        const cls = styleToClass.get(styleMatch[1])!;
                        tag = tag.replace(styleMatch[0], '');
                        const classMatch = tag.match(/class="([^"]*)"/);
                        if (classMatch) {
                            tag = tag.replace(classMatch[0], `class="${classMatch[1]} ${cls}"`);
                        } else {
                            tag = tag.replace(/>$/, ` class="${cls}">`);
                        }
                        return tag;
                    });
                }
            }
            fsp.writeFile(svgPath, svgTxt);
            break;
        }
        case 'render-pug': {
            const htmlPath = o.path.replace('/pugs/', '/posts/').replace('.pug', '.html');
            try {
                // canonical, Open Graph, JSON-LD 생성에 필요한 페이지 단위 정보.
                const html = await renderFile(o.path, {
                    cache: true,
                    imgMap: workerImgMap,
                    siteOrigin: SITE_ORIGIN,
                    pageUrl: pageUrlOf(htmlPath),
                    pageModified: await docModified(o.path),
                });
                await fsp.writeFile(htmlPath, html);
                console.log(`${o.path} rendered`);
            } catch (e) {
                console.log(`${o.path} failed to render`);
                console.error(e);
            }
            break;
        }
        case 'transform-img': {
            const animated = o.path.endsWith('gif');
            let img: Sharp | undefined;
            for (const width of [500, 1200, 2000]) {
                for (const ext of animated ? (['gif', 'webp'] as const) : (['jpeg', 'webp', 'avif'] as const)) {
                    const outPath = o.path.replace('/imgs/', '/imgs-generated/').replace(/\.\w+$/, `-${width}.${ext}`);
                    if (generatedImgSet.has(outPath)) {
                        continue;
                    }
                    img ??= sharp(o.path, { animated });
                    await img.clone().resize({ width, withoutEnlargement: true })[ext]().toFile(outPath);
                    console.log(`${outPath} generated`);
                }
            }
            break;
        }
    }
    remainWorkCount -= 1;
    if (remainWorkCount === 0) {
        unrefTimeout = setTimeout(() => parentPort?.unref(), 500);
    }
});

// 메인 스레드 영역
const isProcessNewFileOnly = process.argv[2] === 'new';
let workCount = 0;
function pushWork(o: WorkMessage): void {
    workers[workCount % workers.length].postMessage(o);
    workCount += 1;
}

const imgMap: Record<string, { width: number; height: number }> = require('./img-map.json');
async function processImgs() {
    const entries = await fsp.readdir('./imgs', { recursive: true, withFileTypes: true });
    for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const outPath = './' + path.join(entry.parentPath, entry.name).replace(/^imgs\//, 'imgs-generated/');
        await fsp.mkdir(outPath, { recursive: true });
    }
    for (const entry of entries) {
        if (!entry.isFile()) continue;
        const filePath = './' + path.join(entry.parentPath, entry.name);
        pushWork({ api: 'transform-img', path: filePath });
        const absolutePath = filePath.slice(1);
        if (imgMap[absolutePath] == null) {
            const animated = filePath.endsWith('gif');
            const img = sharp(filePath, { animated });
            const metadata = await img.metadata();
            imgMap[absolutePath] = {
                width: metadata.width,
                height: metadata.height,
            };
        }
    }
}

const posts: Posts = require('./posts.json');
/**
 * 문서별 갱신일. tools/build-doc-dates.mjs 가 git 이력에서 계산해 커밋해둔 값이다.
 * 빌드 시점에 git 을 호출하지 않으므로 CI·tarball 어디서든 같은 결과가 나온다.
 */
const docDates: Record<string, string> = require('./doc-dates.json').dates;
async function processPugs() {
    pushWork({ api: 'render-pug', path: './index.pug' });
    const postMap = new Map<string, Post>();
    posts.list.forEach((p) => postMap.set('./posts/' + p.file, p));
    posts.list = posts.list.sort((a, b) => a.file.localeCompare(b.file));

    const entries = await fsp.readdir('./pugs', { recursive: true, withFileTypes: true });
    const postDirs = new Set<string>();
    for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        postDirs.add('./' + path.join(entry.parentPath, entry.name).replace(/^pugs\//, 'posts/'));
    }
    await Promise.all([...postDirs].map((d) => fsp.mkdir(d, { recursive: true })));
    await Promise.all(
        entries.map(async (entry) => {
            if (!entry.isFile()) return;
            const filePath = './' + path.join(entry.parentPath, entry.name);
            const stats = await fsp.stat(filePath);
            const htmlPath = filePath.replace('/pugs/', '/posts/').replace('.pug', '.html');
            const post = postMap.get(htmlPath);
            if (post != null) {
                // 홈의 "최근 갱신" 목록도 같은 기준을 쓴다. mtime 은 클론할 때마다 바뀐다.
                const recorded = docDates[filePath.replace(/^\.\//, '')];
                if (recorded != null) post.mtimeMs = Date.parse(recorded);
                else if (stats.birthtimeMs !== stats.mtimeMs) post.mtimeMs = Math.floor(stats.mtimeMs);
            }
            if (isProcessNewFileOnly === false || stats.mtimeMs >= Date.now() - 600000) {
                pushWork({ api: 'render-pug', path: filePath });
            }
        }),
    );
}

async function processD2s() {
    const names = await fsp.readdir('./d2');
    names.forEach((name) => {
        if (!name.endsWith('.d2')) {
            return;
        }
        pushWork({ api: 'render-d2', path: './d2/' + name });
    });
}

if (isMainThread) {
    const generatedPaths = (await fsp.readdir('./imgs-generated', { recursive: true })).map((p) => './imgs-generated/' + p);
    for (const w of workers) {
        w.postMessage({ api: 'init', generatedPaths, imgMap, docDates });
    }
    await Promise.all([processImgs(), processPugs(), processD2s()]);
    const imgMapTxt = JSON.stringify(imgMap);
    await Promise.all([
        fsp.writeFile('./source/img-map.json', imgMapTxt),
        fsp.writeFile('./files/posts-compressed.json', JSON.stringify(posts)),
        fsp.writeFile(
            './files/sitemap.txt',
            posts.list.map(postUrl).sort().join('\n'),
        ),
        exec(`chmod -R 644 d2/*`),
    ]);
}
