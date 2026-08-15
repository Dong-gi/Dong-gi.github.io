/**
 * 빌드 요소: d2
 *
 *     npm run build-d2         → source/build/build-d2.log
 *
 * `d2/<과목>/<이름>.d2` 를 같은 자리의 `.svg` 로 렌더한 뒤, 저장소가 쓰기에 맞게 다듬는다.
 * 상자와 화살표로 된 그림(흐름·분류·구조)이 여기 있고, 좌표계와 곡선은 `figure` 요소에 있다.
 *
 * 외부 바이너리 `d2` 가 필요한 유일한 요소다. 없으면 아무것도 하지 않고 성공으로 끝낸다 —
 * 다이어그램을 못 그리는 것이 문서 250개의 렌더를 막을 이유는 아니다. 대신 해시를
 * 기록하지 않으므로, d2 를 설치한 뒤 다시 돌리면 그때 전부 만들어진다.
 *
 * 렌더 자체가 파일당 1초를 넘는다(140여 개면 수 분). 해시 검사로 바뀐 것만 다시 그리는
 * 이득이 이 요소에서 가장 크다.
 */
import fsp from 'node:fs/promises';
import * as svgo from 'svgo';
import { $ } from 'zx';
import { runComponent, ToolError, type BuildLog } from './lib/log.ts';
import { runIncremental, type Job } from './lib/manifest.ts';
import { resolve, walkFiles } from './lib/paths.ts';

// zx 는 기본으로 자식의 출력을 부모 콘솔에 그대로 흘린다. 그러면 d2 가 찍는 줄이
// BuildLog 를 거치지 않아 build-d2.log 에 남지 않는다. 조용히 받아서 우리가 기록한다.
$.quiet = true;

/** d2 바이너리가 쓸 수 있는 상태인가. */
async function hasD2(): Promise<boolean> {
    try {
        const result = await $({ nothrow: true })`d2 --version`;
        return result.exitCode === 0;
    } catch {
        return false;
    }
}

/**
 * d2 가 낸 SVG 를 저장소에 넣기 좋게 줄인다.
 *
 * d2 는 편집기에서 다시 열 것을 전제로 메타데이터와 쓰지 않는 클래스를 잔뜩 남긴다.
 * 여기서는 브라우저가 그리기만 하면 되므로 그것들을 걷어낸다.
 */
function optimizeD2Svg(raw: string): string {
    let svgTxt = svgo.optimize(
        raw
            // 불필요 속성 제거
            .replace(/data-d2-version="[^"]+"/, '')
            .replace(/\{[^}]*font-family[^}]*\}/g, '{}')
            .replace(/stroke-width: *0;?/g, '')
            .replace(/ rx="0"/g, '')
            .replace(/ stroke-width="0"/g, ''),
    ).data;

    // 미사용 클래스 제거. <style> 이 없는 SVG 는 견줄 대상이 없으므로 이 단계를 건너뛴다.
    const styleTxt = svgTxt.match(/<style>.+<\/style>/)?.[0];
    if (styleTxt != null) {
        for (const classMatch of svgTxt.matchAll(/class="([^ ]+?)"/g)) {
            if (styleTxt.includes(classMatch[1])) continue;
            svgTxt = svgTxt.replaceAll(classMatch[0], '');
        }
    }
    // 공백 정규화
    svgTxt = svgTxt.replace(/\s+/g, ' ');
    // 미사용 클래스 제거
    svgTxt = svgTxt.replace(/class="text /g, 'class="');
    // 외부 중복 <svg> 래퍼 제거, xmlns 를 내부 svg 로 이동
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
        if (svgTxt.length === beforeLength) break;
    }
    // 반복되는 inline style 을 CSS class 로 압축
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
            // 태그 단위로 style 을 class 에 병합
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
    return svgTxt;
}

/** 색 코드를 지운다. d2 는 파이프로 받아도 ANSI 이스케이프를 붙여 보낸다. */
function stripAnsi(text: string): string {
    return text.replace(/\u001b\[[0-9;]*m/g, '');
}

async function renderOne(log: BuildLog, source: string, target: string): Promise<void> {
    const t0 = Date.now();
    // ────────────────────────────────────────────────────────────────────
    // d2 에 넘기는 경로는 **반드시 저장소 기준 상대 경로(구분자 '/')** 여야 한다.
    //
    // zx 는 명령을 셸에 넘기고, 이 기기(Windows + bash)에서는 인자를 bash 의
    // `$'…'` 꼴로 감싼다. 그 안에서 역슬래시는 **이스케이프 문자**다. 그래서
    // 절대 경로를 넘기면 경로가 조용히 망가진다.
    //
    //   C:\…\d2\algorithm\alg-b-x.d2  →  \a 가 BEL(0x07) 로  →  d2␇lgorithm␇lg-b-x.d2
    //   C:\…\d2\1.d2                  →  \1 이 8진 이스케이프로  →  d2␁.d2
    //
    // 더 고약한 것은 `\m`(mcs) `\p`(physics) 처럼 bash 가 모르는 이스케이프는
    // 그대로 남는다는 점이다. 그래서 **일부 과목만** 실패해 원인이 잘 드러나지 않는다.
    //
    // 상대 경로에는 역슬래시가 없고, zx 는 그런 인자를 아예 감싸지 않고 넘긴다.
    // cwd 를 명시하는 이유는 이 스크립트를 저장소 밖에서 불러도 되게 하기 위해서다.
    // ────────────────────────────────────────────────────────────────────
    // 조용히 망가지는 대신 크게 실패하게 한다. 위 실수는 과목에 따라 되기도 하고
    // 안 되기도 해서, 검사가 없으면 원인을 찾는 데 오래 걸린다.
    if (source.includes('\\') || target.includes('\\')) {
        throw new ToolError(`셸에 넘길 경로에 역슬래시가 있다: ${source} → ${target}`);
    }
    const result = await $({ nothrow: true, cwd: resolve('.') })`d2 ${source} ${target}`;
    if (result.exitCode !== 0) {
        // d2 의 진단은 그 자체로 읽을 만하다. 그대로 옮기되 스택은 붙이지 않는다.
        const detail = stripAnsi(`${result.stderr}${result.stdout}`).trim();
        throw new ToolError(detail === '' ? `d2 가 ${result.exitCode} 로 끝났다` : detail);
    }
    const raw = await fsp.readFile(resolve(target), 'utf8');
    const optimized = optimizeD2Svg(raw);
    await fsp.writeFile(resolve(target), optimized);
    const saved = ((1 - optimized.length / raw.length) * 100).toFixed(0);
    log.line(`${target}  ${(optimized.length / 1024).toFixed(1)}KB (${saved}% 감소, ${Date.now() - t0}ms)`);
}

/**
 * d2 산출물의 파일 모드를 644 로 맞춘다.
 *
 * 예전에는 `chmod -R 644 d2/*` 를 셸로 불렀는데, Windows 에는 chmod 도 셸 글로브도
 * 없어 빌드 마지막 단계가 통째로 실패했다. Node 의 fsp.chmod 는 POSIX 에서 같은
 * 동작을 하고 Windows 에서는 읽기 전용 비트만 다루는 무해한 호출이 된다.
 */
async function chmodAll(files: string[]): Promise<void> {
    await Promise.all(files.map((f) => fsp.chmod(resolve(f), 0o644).catch(() => undefined)));
}

await runComponent('d2', async (log) => {
    const sources = (await walkFiles('d2')).filter((f) => f.endsWith('.d2'));
    if (sources.length === 0) {
        log.line('d2/ 아래에 .d2 파일이 없다');
        return '대상 없음';
    }

    if (!(await hasD2())) {
        log.warn(`d2 바이너리가 없어 ${sources.length}개를 건너뛴다. 해시를 기록하지 않으므로 설치 후 다시 돌리면 만들어진다`);
        return `건너뜀 (d2 없음, ${sources.length}개)`;
    }

    const jobs: Job[] = sources.map((source) => {
        const target = source.replace(/\.d2$/, '.svg');
        return {
            key: source,
            inputs: [source],
            outputs: [target],
            run: () => renderOne(log, source, target),
        };
    });

    const report = await runIncremental({
        name: 'd2',
        log,
        jobs,
        orphanScan: { dirs: ['d2'], match: (f) => f.endsWith('.svg') },
    });

    await chmodAll(jobs.flatMap((j) => j.outputs));

    if (report.failed.length !== 0) {
        throw new ToolError(`렌더 실패 ${report.failed.length}개: ${report.failed.join(', ')}`);
    }
    return report.ran === 0 ? `변경 없음 (${report.skipped}개)` : `갱신 ${report.ran} · 건너뜀 ${report.skipped}`;
});
