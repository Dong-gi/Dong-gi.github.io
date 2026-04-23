import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import child_process from 'node:child_process';
import { promisify } from 'node:util';
import { Worker, isMainThread, parentPort } from 'node:worker_threads';
import { createRequire } from 'node:module';
import { cpus } from 'node:os';
import sharp from 'sharp';
import * as svgo from 'svgo';

type WorkMessage =
    | { api: 'render-pug' | 'transform-img' | 'render-d2'; path: string }
    | { api: 'init'; generatedPaths: string[]; imgMap: Record<string, { width: number; height: number }> };

interface Post {
    category: string;
    file: string;
    title: string;
    mtimeMs?: number;
}

interface Posts {
    list: Post[];
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
parentPort?.on('message', async (o: WorkMessage) => {
    clearTimeout(unrefTimeout);
    remainWorkCount += 1;
    switch (o.api) {
        case 'init': {
            generatedImgSet = new Set(o.generatedPaths);
            workerImgMap = o.imgMap;
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
                const html = await renderFile(o.path, { cache: true, imgMap: workerImgMap });
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
            let img: sharp.Sharp | undefined;
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
            if (post != null && stats.birthtimeMs !== stats.mtimeMs) {
                post.mtimeMs = Math.floor(stats.mtimeMs);
            }
            pushWork({ api: 'render-pug', path: filePath });
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
        w.postMessage({ api: 'init', generatedPaths, imgMap });
    }
    await Promise.all([processImgs(), processPugs(), processD2s()]);
    const imgMapTxt = JSON.stringify(imgMap);
    await Promise.all([
        fsp.writeFile('./source/img-map.json', imgMapTxt),
        fsp.writeFile('./files/posts-compressed.json', JSON.stringify(posts)),
        fsp.writeFile(
            './files/sitemap.txt',
            posts.list
                .map((post) => `https://dong-gi.github.io${post.file}`)
                .sort()
                .join('\n'),
        ),
        exec(`chmod -R 644 d2/*`),
    ]);
}
