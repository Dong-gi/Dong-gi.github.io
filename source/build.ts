import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline/promises';
import { promisify } from 'node:util';
import { Worker, isMainThread, parentPort } from 'node:worker_threads';
import { createRequire } from 'node:module';
import { cpus } from 'node:os';
import sharp, { type Sharp } from 'sharp';
import * as svgo from 'svgo';
import { $ } from 'zx';

type WorkMessage =
    | { api: 'render-pug' | 'transform-img' | 'render-d2'; path: string }
    | {
        api: 'init';
        generatedPaths: string[];
        imgMap: Record<string, { width: number; height: number }>;
        docDates: Record<string, number>;
    };

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
 * 경로 구분자를 '/' 로 통일한다.
 *
 * Windows 에서 path.join 은 '\' 를 쓴다. 이 스크립트는 경로를 문자열로 다루는 곳이
 * 많아('/pugs/' -> '/posts/' 치환, doc-dates.json 조회, URL 생성) 구분자가 섞이면
 * 전부 어긋난다. 파일시스템에서 경로를 받는 즉시 이 함수를 통과시킨다.
 * Node 의 fs API 는 Windows 에서도 '/' 를 그대로 받아들이므로 안전하다.
 *
 * path.sep 이 아니라 두 구분자를 모두 받는 이유는 두 가지다. Windows 에서는 '/' 와
 * '\' 가 섞인 경로가 흔히 나오고, 이 함수 자체를 어느 플랫폼에서든 검증할 수 있다.
 * POSIX 파일명에 '\' 가 들어 있으면 망가지지만 이 저장소에는 그런 파일이 없다.
 */
function toPosix(p: string): string {
    return p.split(/[\\/]/).join('/');
}

/** 사이트 오리진. 사이트맵 등 절대 URL 생성에 쓴다. */
const SITE_ORIGIN = 'https://dong-gi.github.io';
/** posts.json 의 file 값은 'dev/aws.html' 형태이므로 URL 생성 시 이 접두사가 필요하다. */
const POSTS_URL_PREFIX = '/posts/';

const require = createRequire(import.meta.url);
const renderFile = promisify(require('pug').renderFile) as (path: string, options?: Record<string, unknown>) => Promise<string>;
const workers = isMainThread ? cpus().map(() => new Worker(import.meta.filename)) : [];
const imgMap: Record<string, { width: number; height: number }> = require('./img-map.json');
const posts: Post[] = require('./posts.json');

/**
 * skeleton.pug 가 useMath 인 문서에 심는 표식. 이 문자열이 있는 페이지만 수식을 조판한다.
 * 본문에서 구분자를 찾아 헤매는 대신 표식 하나로 판단한다.
 */
const MATH_MARKER = '<meta name="mathjax-prerender" content="1">';
/** 자체 호스팅 폰트 위치. @font-face 의 src 가 이 경로를 기준으로 만들어진다. */
const MATH_FONT_URL = '/fonts/mathjax-newcm';

// 워커 스레드 영역
let remainWorkCount = 0;
let unrefTimeout: NodeJS.Timeout;
let generatedImgSet: Set<string> = new Set();
let workerImgMap: Record<string, { width: number; height: number }> = {};

/**
 * MathJax 기동은 1초 가까이 걸리므로 워커당 한 번만 한다.
 * 수식 문서가 없는 워커는 아예 불러오지 않는다.
 */
let mathJaxPromise: Promise<any> | undefined;
function getMathJax(): Promise<any> {
    mathJaxPromise ??= import('@mathjax/src/components/mjs/node-main/node-main.mjs').then(({ init }) =>
        init({
            loader: { load: ['input/tex', 'adaptors/liteDOM', 'output/chtml', 'a11y/assistive-mml'] },
            // 구분자는 skeleton.pug 가 쓰던 런타임 설정과 같아야 한다. 어긋나면 조판이 안 된다.
            tex: { tags: 'ams', inlineMath: [['식[', ']식']], displayMath: [['\\[', '\\]']] },
            chtml: { fontURL: MATH_FONT_URL },
            startup: { typeset: false },
        }),
    );
    return mathJaxPromise;
}

/**
 * 페이지의 TeX 를 빌드 시점에 CHTML 로 바꾼다.
 *
 * 이렇게 하면 브라우저가 MathJax 를 받아 조판할 필요가 없다. physics 기준으로
 * 첫 수식이 보이기까지 16.5초에서 1.8초로 줄었고, tex-chtml.js 884KB 도 사라진다.
 *
 * updateDocument() 가 `<style id="MJX-CHTML-styles">` 를 head 에 직접 넣으므로
 * 여기서 CSS 를 따로 삽입하면 안 된다. 그러면 30KB 가 두 번 들어간다.
 */
async function prerenderMath(html: string): Promise<string> {
    const MathJax = await getMathJax();
    const { mathjax, adaptor, input, output, handler } = MathJax.startup;

    // MathJax 4 는 필요한 자원을 비동기로 더 불러온다. 폰트 데이터뿐 아니라 HTML
    // 엔티티 표도 그렇다. 동기 코드에서는 그 지점에서 예외를 던지므로 재시도 래퍼로 감싼다.
    //
    // 파싱을 먼저 해 두는 이유: 문자열을 그대로 넘기면 MathJax 가 핸들러를 고르며
    // 스스로 파싱하는데, 그 안의 try/catch 가 위 재시도 예외까지 삼켜 버려
    // "Can't find handler for document" 로 둔갑한다(dev/web/html.pug 가 실제로 그랬다).
    // 미리 파싱해 문서 객체를 넘기면 그 경로를 타지 않고, 이중 파싱도 없어진다.
    let lite: any;
    await mathjax.handleRetriesFor(() => {
        lite = adaptor.parse(html, 'text/html');
    });

    let doc: any;
    await mathjax.handleRetriesFor(() => {
        // 문서 사이에 상태가 새지 않도록 초기화한다. 셋 중 하나라도 빠지면 앞 문서의
        // 수식 번호나 CSS 가 뒤 문서에 섞여, 같은 문서인데 빌드 순서에 따라 결과가 달라진다.
        MathJax.texReset();
        output.clearCache();
        output.reset();

        // startup.handler 를 직접 쓴다. a11y/assistive-mml 같은 확장은 핸들러를 감싸는
        // 방식으로 붙으므로, mathjax.document() 로 전역 목록에서 핸들러를 새로 고르면
        // 확장이 빠진 문서가 만들어진다.
        doc = handler.create(lite, { InputJax: input, OutputJax: output });
        // 개별 단계를 손으로 부르지 않고 render() 를 쓴다. 스타일시트 삽입과
        // 보조 MathML 부착이 렌더 액션으로 등록돼 있어서, 손으로 부르면 그것들이 빠진다.
        doc.render();
    });

    // outerHTML 은 <html> 부터만 돌려준다. DOCTYPE 을 다시 붙이지 않으면 브라우저가
    // 쿼크 모드로 렌더해서 박스 모델이 달라진다. 실제로 상단바의 Home 링크가
    // 15px 아래로 밀려 내려갔다. 파서는 DOCTYPE 을 갖고 있으므로 그대로 되돌린다.
    const doctype = adaptor.doctype(doc.document);
    return `${doctype ? doctype + '\n' : ''}${adaptor.outerHTML(adaptor.root(doc.document))}`;
}
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
            // 외부 바이너리 호출은 이것 하나뿐이다. 인용은 zx 가 셸에 맞게 처리한다.
            // d2 가 없거나 실패해도 여기서 끝낸다. 예전에는 이 예외가 워커의 error
            // 이벤트로 올라가 메인 스레드까지 죽었다. 다이어그램 하나 때문에 pug
            // 251개 렌더를 잃을 이유는 없다. render-pug 와 같은 처리다.
            try {
                await $`d2 ${o.path} ${svgPath}`;
            } catch (e) {
                console.log(`${o.path} failed to render`);
                console.error(e instanceof Error ? e.message : e);
                break;
            }
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
            const htmlPath = toPosix(o.path).replace('/pugs/', '/posts/').replace('.pug', '.html');
            // 경로 치환이 어긋나면 pugs/ 안에 HTML 을 쓰게 된다. 조용히 소스를 오염시키는
            // 대신 즉시 실패시킨다. Windows 에서 실제로 이 상태였다.
            if (htmlPath !== './index.html' && !htmlPath.startsWith('./posts/')) {
                console.error(`${o.path} -> ${htmlPath} : 산출물 경로가 posts/ 밖이라 렌더를 중단한다`);
                break;
            }
            try {
                // canonical, Open Graph, JSON-LD 생성에 필요한 페이지 단위 정보.
                let html = await renderFile(o.path, {
                    cache: true,
                    imgMap: workerImgMap,
                });
                if (html.includes(MATH_MARKER)) {
                    const t0 = Date.now();
                    html = await prerenderMath(html);
                    console.log(`${o.path} math prerendered (${Date.now() - t0}ms)`);
                }
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
                for (const ext of animated ? (['webp'] as const) : (['webp', 'avif'] as const)) {
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

async function processImgs() {
    const entries = await fsp.readdir('./imgs', { recursive: true, withFileTypes: true });
    for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const outPath = './' + toPosix(path.join(entry.parentPath, entry.name)).replace(/^imgs\//, 'imgs-generated/');
        await fsp.mkdir(outPath, { recursive: true });
    }
    const activeKeySet = new Set<string>();
    for (const entry of entries) {
        if (!entry.isFile()) continue;
        const filePath = './' + toPosix(path.join(entry.parentPath, entry.name));
        pushWork({ api: 'transform-img', path: filePath });
        const absolutePath = filePath.slice(1);
        activeKeySet.add(absolutePath);
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
    for (const key in imgMap) {
        if (!activeKeySet.has(key)) {
            delete imgMap[key];
        }
    }
}

// ---------------------------------------------------------------- 문서 갱신일
//
// 파일 mtime 을 그대로 쓰지 않는 이유는, 새로 클론하면 전부 체크아웃 시각이 되어
// 250여 페이지의 날짜가 한꺼번에 덮이기 때문이다. git 이력에서 구한 값을
// source/doc-dates.json 에 적어두고, lastSha 이후의 커밋만 증분으로 확인한다.
// 여기에 없는 문서만 mtime 으로 대체한다.

/** source/doc-dates.json */
interface DocDates {
    /** 마지막으로 갱신일 계산에 반영한 커밋. 이 커밋 이후만 다시 본다. */
    lastSha: string | null;
    /** 'pugs/dev/aws.pug' -> UNIX timestamp */
    dates: Record<string, number>;
}

const DOC_DATES_FILE = './source/doc-dates.json';

/** 한 커밋 안에서 pug 파일 하나에 일어난 변경. */
interface Change {
    /** 변경 후 경로 */
    path: string;
    /** rename 이면 변경 전 경로 */
    from?: string;
    /** 내용이 실제로 바뀌었는가. 순수 rename 이면 false */
    edited: boolean;
}

/**
 * git 을 부른다. 실패하면 null.
 *
 * 저장소가 아니거나(tarball), git 이 없거나, zx 가 쓸 셸이 없는 환경을 모두 여기서
 * 흡수한다. 갱신일을 못 구하는 것이 빌드를 멈출 이유는 아니다.
 */
async function git(...args: string[]): Promise<string | null> {
    try {
        const result = await $({ nothrow: true })`git ${args}`;
        return result.exitCode === 0 ? result.stdout : null;
    } catch {
        return null;
    }
}

/**
 * `git show --numstat -z` 출력을 푼다.
 *
 * 보통은 `추가\t삭제\t경로\0` 이지만, rename 은 경로 자리가 비고 옛 경로와 새 경로가
 * 뒤이어 두 개의 NUL 필드로 온다. (`1\t1\t\0old\0new\0`)
 */
function parseNumstat(out: string): Change[] {
    const tokens = out.split('\0');
    const changes: Change[] = [];
    for (let i = 0; i < tokens.length; i += 1) {
        if (tokens[i] === '') continue;
        const [add, del, p] = tokens[i].split('\t');
        const edited = add !== '0' || del !== '0';
        if (p === '' || p == null) {
            const from = tokens[i + 1];
            const to = tokens[i + 2];
            i += 2;
            if (to?.endsWith('.pug')) changes.push({ path: to, from, edited });
        } else if (p.endsWith('.pug')) {
            changes.push({ path: p, edited });
        }
    }
    return changes;
}

/**
 * 질문 하나에 인터페이스 하나를 만들면 안 된다. readline 은 stdin 을 청크 단위로
 * 읽어 남는 입력을 자기 버퍼에 들고 있다가 close 할 때 버린다. 커밋이 여러 개면
 * 두 번째 질문부터 답을 잃는다. 그래서 하나를 만들어 끝까지 쓴다.
 */
let prompt: readline.Interface | null = null;
/** 물을 수 있는 상태인가. 비대화형이거나 입력이 끊기면 기본값으로 넘어간다. */
let interactive = process.stdin.isTTY === true;

/** [Y/n] 을 묻는다. 엔터만 치면 y. */
async function confirm(message: string): Promise<boolean> {
    if (!interactive) {
        console.log(`${message} [Y/n] y (비대화형이라 기본값)`);
        return true;
    }
    prompt ??= readline.createInterface({ input: process.stdin, output: process.stdout });
    let answer: string;
    try {
        answer = await prompt.question(`${message} [Y/n] `);
    } catch {
        // Ctrl+D 등으로 입력이 끊긴 경우. 여기서 빌드를 죽이는 것보다 기본값이 낫다.
        interactive = false;
        console.log('\n  입력이 끊겨 남은 커밋은 기본값(y)으로 처리한다');
        return true;
    }
    const normalized = answer.trim().toLowerCase();
    return normalized === '' || normalized === 'y' || normalized === 'yes';
}

/**
 * lastSha 이후의 커밋을 하나씩 확인해 갱신일을 반영하고, 그 결과를 돌려준다.
 *
 * rename 은 답변과 무관하게 항상 따라간다. 경로가 바뀌었다고 이력을 잃을 이유는 없다.
 * 사라진 pug 의 항목은 정리한다.
 *
 * git 을 쓸 수 없으면 저장된 값을 그대로 쓴다. 빌드는 계속되고, 날짜가 없는 문서는
 * mtime 으로 대체된다.
 */
async function updateDocDates(): Promise<DocDates['dates']> {
    const state = JSON.parse(fs.readFileSync(DOC_DATES_FILE, 'utf8')) as DocDates;
    const head = (await git('rev-parse', 'HEAD'))?.trim();
    if (!head) {
        console.log('⚠ git 이력을 읽을 수 없어 저장된 갱신일을 그대로 쓴다');
        return state.dates;
    }
    if (state.lastSha === head) {
        return state.dates;
    }

    const log = (await git('log', '--reverse', '--format=%H%x09%aI%x09%s', `${state.lastSha}..HEAD`, '--', 'pugs')) ?? '';
    const commits = log
        .split('\n')
        .filter((l) => l !== '')
        .map((l) => {
            const [sha, iso, subject] = l.split('\t');
            return { sha, iso, subject };
        });

    try {
        if (commits.length !== 0) console.log(`문서 갱신일: 확인할 커밋 ${commits.length}개`);
        for (const { sha, iso, subject } of commits) {
            const numstat = (await git('show', '-w', '--format=', '--numstat', '-M', '-z', sha, '--', 'pugs')) ?? '';
            const changes = parseNumstat(numstat);

            // rename 은 판단 대상이 아니다. 답변과 무관하게 이력을 새 경로로 옮긴다.
            for (const c of changes) {
                if (c.from == null || state.dates[c.from] == null) continue;
                state.dates[c.path] = state.dates[c.from];
                delete state.dates[c.from];
            }

            const edited = changes.filter((c) => c.edited);
            // -w 로 봤으므로 공백만 바뀐 파일은 여기 없다. 남은 게 없으면 물을 것도 없다.
            if (edited.length === 0) continue;

            const day = iso.slice(0, 10);
            const answer = await confirm(`  ${sha.slice(0, 8)} ${day} 문서 ${edited.length}개 — ${subject}\n  갱신일로 쓸까?`);
            if (!answer) continue;
            for (const c of edited) state.dates[c.path] = new Date(iso).getTime();
        }
    } finally {
        // 안 닫으면 stdin 이 열린 채로 남아 빌드가 끝나지 않는다.
        prompt?.close();
    }

    // 경로순으로 써야 diff 가 잘 잡힌다
    const activeKeySet = new Set(posts.map(x => 'pugs/' + x.file.replace(/\.html$/, '.pug')));
    const payload: DocDates = {
        lastSha: head,
        dates: Object.fromEntries(
            Object.entries(state.dates)
                .filter(x => activeKeySet.has(x[0]))
                .sort(([a], [b]) => a.localeCompare(b))
        ),
    };
    fs.writeFileSync(DOC_DATES_FILE, JSON.stringify(payload, null, 4) + '\n');
    return state.dates;
}

// ---------------------------------------------------------------- pug 렌더

/** 문서별 갱신일. 메인 스레드가 updateDocDates() 로 채운다. */
let docDates: DocDates['dates'] = {};
async function processPugs() {
    pushWork({ api: 'render-pug', path: './index.pug' });
    const postMap = new Map<string, Post>();
    posts.forEach((p) => postMap.set('./posts/' + p.file, p));
    posts.sort((a, b) => a.file.localeCompare(b.file));

    const entries = await fsp.readdir('./pugs', { recursive: true, withFileTypes: true });
    const postDirs = new Set<string>();
    for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        postDirs.add('./' + toPosix(path.join(entry.parentPath, entry.name)).replace(/^pugs\//, 'posts/'));
    }
    await Promise.all([...postDirs].map((d) => fsp.mkdir(d, { recursive: true })));
    await Promise.all(
        entries.map(async (entry) => {
            if (!entry.isFile()) return;
            const filePath = './' + toPosix(path.join(entry.parentPath, entry.name));
            const stats = await fsp.stat(filePath);
            const htmlPath = filePath.replace('/pugs/', '/posts/').replace('.pug', '.html');
            const post = postMap.get(htmlPath);
            if (post != null) {
                // 홈의 "최근 갱신" 목록도 docModified() 와 같은 기준을 쓴다.
                const recorded = docDates[filePath.replace(/^\.\//, '')];
                post.mtimeMs = recorded != null ? recorded : Math.floor(stats.mtimeMs);
            }
            if (isProcessNewFileOnly === false || stats.mtimeMs >= Date.now() - 600000) {
                pushWork({ api: 'render-pug', path: filePath });
            }
        }),
    );
}

/**
 * d2 산출물의 파일 모드를 644 로 맞춘다.
 *
 * 예전에는 exec('chmod -R 644 d2/*') 를 썼는데, Windows 에는 chmod 도 셸 글로브도
 * 없어 빌드 마지막 단계가 통째로 실패했다. Node 의 fsp.chmod 로 바꾸면 POSIX 에서는
 * 같은 동작을 하고 Windows 에서는 읽기 전용 비트만 다루는 무해한 호출이 된다.
 */
async function chmodD2() {
    const names = await fsp.readdir('./d2');
    await Promise.all(names.map((name) => fsp.chmod('./d2/' + name, 0o644)));
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
    // 워커에 넘기기 전에 끝내야 한다. 새 커밋이 있으면 여기서 사람에게 묻는다.
    docDates = await updateDocDates();
    const generatedPaths = (await fsp.readdir('./imgs-generated', { recursive: true })).map(
        (p) => './imgs-generated/' + toPosix(p),
    );
    for (const w of workers) {
        w.postMessage({ api: 'init', generatedPaths, imgMap, docDates });
    }
    await Promise.all([processImgs(), processPugs(), processD2s()]);
    const imgMapTxt = JSON.stringify(imgMap);
    await Promise.all([
        fsp.writeFile('./source/img-map.json', imgMapTxt),
        fsp.writeFile('./source/posts-compressed.json', JSON.stringify(posts)),
        fsp.writeFile(
            './files/sitemap.txt',
            posts
                .map((p) => SITE_ORIGIN + POSTS_URL_PREFIX + p.file)
                .sort()
                .join('\n'),
        ),
        chmodD2(),
    ]);
}
