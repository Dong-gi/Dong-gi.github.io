/**
 * 빌드 요소: img
 *
 *     npm run build-img        → source/build/build-img.log
 *
 * `imgs/` 의 사진을 폭 500·1200·2000 의 avif/webp 로 만들어 `imgs-generated/` 에 둔다.
 * 그것이 전부다.
 *
 * **pug 요소와는 서로 독립이다.** `+w3img` 가 쓰는 원본 크기는 pug 쪽이
 * `lib/image-size.ts` 로 원본 헤더에서 직접 읽는다. 예전에는 이 요소가
 * `source/img-map.json` 을 만들어 넘겼는데, 사전 하나를 통째로 의존하는 바람에
 * **그림 하나가 늘면 문서 250여 개가 전부 다시 렌더되었다.** 변환본 경로는 규약이라
 * pug 가 존재를 확인하지 않고 만들어 내므로, 두 요소가 같은 원본을 각자 보면 된다.
 *
 * `figures/`, `d2/` 가 만든 SVG 는 여기 오지 않는다. 벡터는 크기를 미리 알릴 필요가 없다.
 *
 * ## 무엇을 다시 만드는가 — 해시 파일 대신 git
 *
 * 다른 요소와 달리 이 요소는 `build-img-sha.json` 을 두지 않는다. **원본과 변환본이
 * 둘 다 저장소에 커밋되어 있어서**, 무엇이 달라졌는지는 git 이 이미 알고 있다.
 * 해시를 따로 적어 두면 같은 사실을 두 곳에 두는 셈이다.
 *
 * 그래서 규칙이 둘이다.
 *
 *   - **HEAD 와 달라진 원본** — 변환본을 전부 다시 만든다. 제자리에서 그림을 바꾼
 *     경우가 여기 걸린다. 예전에는 &#34;이미 있으면 건너뛴다&#34; 였고, 그래서 그림을
 *     바꿔 넣어도 변환본이 옛것으로 남았다.
 *   - **나머지** — 빠진 변환본만 채운다. 새 폭을 더했거나 산출물을 지운 경우다.
 *
 * git 을 쓸 수 없으면(내려받기만 한 사본 등) 뒤쪽 규칙만 적용하고 그 사실을 로그에
 * 남긴다. **원본을 고치고 빌드하지 않은 채 커밋하면** 다음 빌드가 그것을 모른다.
 * 그때는 해당 변환본을 지우고 다시 돌리면 된다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import sharp, { type Sharp } from 'sharp';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { runComponent, ToolError, type BuildLog } from './lib/log.ts';
import { warnOrphans } from './lib/manifest.ts';
import { fileExists, normalize, resolve, walkDirs, walkFiles } from './lib/paths.ts';

const run = promisify(execFile);

const WIDTHS = [500, 1200, 2000] as const;

/**
 * 이 원본이 만들어야 할 산출물 목록. **폭마다 한 형식씩**이다.
 *
 * 정지 그림은 avif 만 만든다. 예전에는 webp 도 함께 만들어 `<picture>` 로 골라 쓰게
 * 했는데, avif 가 널리 쓰이게 되어(2026-08 기준 Baseline, 전 세계 94% 남짓) 두 벌을
 * 둘 이유가 없어졌다. 같은 화질에서 avif 가 더 작다.
 *
 * gif 만 webp 다. 움직이는 그림을 avif 로 만들지 않기 때문이다.
 */
function extOf(rel: string): 'webp' | 'avif' {
    return rel.endsWith('gif') ? 'webp' : 'avif';
}

function outputsOf(rel: string): string[] {
    const ext = extOf(rel);
    return WIDTHS.map((width) =>
        rel.replace(/^imgs\//, 'imgs-generated/').replace(/\.\w+$/, `-${width}.${ext}`),
    );
}

async function transformOne(log: BuildLog, rel: string, force: boolean): Promise<number> {
    const animated = rel.endsWith('gif');
    let img: Sharp | undefined;
    let made = 0;
    for (const out of outputsOf(rel)) {
        // force 면 있어도 다시 만든다. 원본이 바뀌었다는 뜻이기 때문이다.
        if (!force && (await fileExists(out))) continue;
        img ??= sharp(resolve(rel), { animated });
        const ext = out.endsWith('.avif') ? 'avif' : 'webp';
        const width = Number(out.match(/-(\d+)\.\w+$/)![1]);
        // eslint 없는 저장소라 적어 둔다 — ext 는 out 에서 되읽는다. outputsOf 가 정한
        // 형식과 언제나 같지만, 파일 이름이 곧 계약이므로 이름을 믿는 편이 안전하다.
        await img.clone().resize({ width, withoutEnlargement: true })[ext]().toFile(resolve(out));
        made += 1;
    }
    if (made !== 0) log.line(`${rel} — 변환본 ${made}개 ${force ? '갱신' : '생성'}`);
    return made;
}

/**
 * HEAD 와 내용이 다른 `imgs/` 아래 파일들. 추가·수정·이름 변경·미추적을 모두 센다.
 *
 * git 을 쓸 수 없으면 `null` 이다. 빈 집합과 구별해야 한다 — 빈 집합은 &#34;바뀐 것이
 * 없다&#34; 이고 null 은 &#34;알 수 없다&#34; 여서 로그가 달라진다.
 */
async function changedSources(): Promise<Set<string> | null> {
    let stdout: string;
    try {
        ({ stdout } = await run('git', ['status', '--porcelain', '--untracked-files=all', '--', 'imgs'], {
            cwd: resolve('.'),
            maxBuffer: 16 * 1024 * 1024,
        }));
    } catch {
        return null;
    }
    const out = new Set<string>();
    for (const line of stdout.split('\n')) {
        if (line.length < 4) continue;
        // `XY <경로>`. 이름 변경은 `R  옛 -> 새` 형태라 화살표 뒤가 지금 파일이다.
        const p = line.slice(3).trim();
        const now = p.includes(' -> ') ? p.slice(p.indexOf(' -> ') + 4) : p;
        out.add(normalize(now.replace(/^\"|\"$/g, '')));
    }
    return out;
}

await runComponent('img', async (log) => {
    const sources = await walkFiles('imgs');
    if (sources.length === 0) {
        log.line('imgs/ 아래에 파일이 없다');
    }

    // 산출 디렉터리를 원본 구조 그대로 미리 만든다.
    for (const dir of await walkDirs('imgs')) {
        await fsp.mkdir(resolve(dir.replace(/^imgs\//, 'imgs-generated/')), { recursive: true });
    }
    await fsp.mkdir(resolve('imgs-generated'), { recursive: true });

    const changed = await changedSources();
    if (changed == null) {
        log.line('git 을 쓸 수 없어 바뀐 원본을 가리지 못한다 — 빠진 변환본만 채운다');
    } else {
        log.line(`HEAD 와 달라진 원본 ${changed.size}개`);
    }

    let updated = 0;
    let filled = 0;
    const failed: string[] = [];
    for (const rel of sources) {
        const force = changed?.has(rel) === true;
        try {
            const made = await transformOne(log, rel, force);
            if (made === 0) continue;
            if (force) updated += 1;
            else filled += 1;
        } catch (e) {
            failed.push(rel);
            log.error(rel, e);
        }
    }

    const expected = new Set<string>();
    for (const rel of sources) for (const out of outputsOf(rel)) expected.add(normalize(out));
    await warnOrphans({ dirs: ['imgs-generated'], match: () => true }, expected, log);

    if (failed.length !== 0) {
        throw new ToolError(`변환 실패 ${failed.length}개: ${failed.join(', ')}`);
    }
    const parts: string[] = [];
    if (updated !== 0) parts.push(`갱신 ${updated}`);
    if (filled !== 0) parts.push(`채움 ${filled}`);
    return parts.length === 0 ? `변경 없음 (${sources.length}개)` : `${parts.join(' · ')} · 건너뜀 ${sources.length - updated - filled}`;
});
