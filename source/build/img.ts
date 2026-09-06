/**
 * 빌드 요소: img
 *
 *     npm run build-img        → source/build/build-img.log
 *
 * `imgs/` 의 레거시 포맷 이미지들을 avif/webp 로 만들어 같은 위치에 둔다.
 */
import { rm, rename, stat } from 'node:fs/promises';
import sharp, { type Sharp } from 'sharp';
import { runComponent, ToolError, type BuildLog } from './lib/log.ts';
import { resolve, walkFiles } from './lib/paths.ts';

sharp.cache(false)

async function transform(log: BuildLog, rel: string) {
    if (rel.endsWith('webp')) {
        return 0;
    }
    const animated = rel.endsWith('gif');
    const img = sharp(resolve(rel), { animated });
    const { width } = await img.metadata()
    let copy = img.clone()
    if (width > 2000) {
        copy = copy.resize({ width: 2000 })
    }
    const candidates: [Sharp, number][] = [[img, (await stat(resolve(rel))).size]];
    await Promise.all(
        [copy.webp(), ...(animated ? [] : [copy.clone().avif()])].map(x => x.toBuffer().then(y => candidates.push([x, y.length])))
    )
    const winner = candidates.sort((a, b) => a[1] - b[1])[0][0];
    const out = rel.replace(/\.\w+$/, '.webp');
    if (winner !== img) {
        await winner.toFile(resolve(out));
        await rm(rel);
    } else {
        await rename(rel, resolve(out))
    }
    log.line(`${rel} — 변환 완료`);
    return 1;
}

await runComponent('img', async (log) => {
    const sources = await walkFiles('imgs');
    let changed = 0;
    const failed: string[] = [];
    for (const rel of sources) {
        try {
            changed += await transform(log, rel);
        } catch (e) {
            failed.push(rel);
            log.error(rel, e);
        }
    }

    if (failed.length !== 0) {
        throw new ToolError(`변환 실패 ${failed.length}개: ${failed.join(', ')}`);
    }
    const parts: string[] = [];
    if (changed !== 0) parts.push(`총 변환 개수: ${changed}`);
    return parts.length === 0 ? `변경 없음 (${sources.length}개)` : `${parts.join(' · ')} · 건너뜀 ${sources.length - changed}`;
});
