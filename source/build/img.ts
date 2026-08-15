/**
 * 빌드 요소: img
 *
 *     npm run build-img        → source/build/build-img.log
 *
 * `imgs/` 의 사진을 폭 500·1200·2000 의 avif/webp 로 만들어 `imgs-generated/` 에 두고,
 * 원본의 크기를 `source/img-map.json` 에 적는다.
 *
 * pug 의 `+w3img(src)` 가 그 두 가지를 함께 쓴다. img-map 에 등재된 경로면 반응형
 * `<picture>` 세트를 쓰고, 없으면 평범한 `<img>` 로 폴백한다. 그래서 이 요소는
 * **pug 보다 먼저** 끝나야 한다(`source/build.ts` 가 그 순서를 강제한다).
 *
 * `figures/`, `d2/` 가 만든 SVG 는 여기 오지 않는다. 벡터는 크기를 미리 알릴 필요가 없다.
 *
 * img-map 은 키 순서로 정렬해 쓴다. 순서가 흔들리면 내용이 같아도 해시가 달라져
 * pug 요소가 250여 개 문서를 통째로 다시 렌더한다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import sharp, { type Sharp } from 'sharp';
import { runComponent, ToolError, type BuildLog } from './lib/log.ts';
import { runIncremental, type Job } from './lib/manifest.ts';
import { fileExists, resolve, walkDirs, walkFiles } from './lib/paths.ts';

const IMG_MAP_FILE = 'source/img-map.json';
const WIDTHS = [500, 1200, 2000] as const;

interface Size {
    width: number;
    height: number;
}

/** `imgs/202303/a.png` → `/imgs/202303/a.png`. skeleton.pug 가 쓰는 키 형태다. */
function mapKey(rel: string): string {
    return '/' + rel;
}

/** 이 원본이 만들어야 할 산출물 목록. gif 는 avif 를 만들지 않는다(애니메이션 지원). */
function outputsOf(rel: string): string[] {
    const animated = rel.endsWith('gif');
    const outputs: string[] = [];
    for (const width of WIDTHS) {
        for (const ext of animated ? ['webp'] : ['webp', 'avif']) {
            outputs.push(rel.replace(/^imgs\//, 'imgs-generated/').replace(/\.\w+$/, `-${width}.${ext}`));
        }
    }
    return outputs;
}

async function transformOne(log: BuildLog, rel: string): Promise<void> {
    const animated = rel.endsWith('gif');
    let img: Sharp | undefined;
    let made = 0;
    for (const out of outputsOf(rel)) {
        // 이미 있는 변환본은 다시 만들지 않는다. 산출물 하나가 빠져 작업이 다시 도는
        // 경우에도 빠진 것만 채우면 된다.
        if (await fileExists(out)) continue;
        img ??= sharp(resolve(rel), { animated });
        const ext = out.endsWith('.avif') ? 'avif' : 'webp';
        const width = Number(out.match(/-(\d+)\.\w+$/)![1]);
        await img.clone().resize({ width, withoutEnlargement: true })[ext]().toFile(resolve(out));
        made += 1;
    }
    log.line(`${rel} — 변환본 ${made}개 생성`);
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

    const jobs: Job[] = sources.map((rel) => ({
        key: rel,
        inputs: [rel],
        outputs: outputsOf(rel),
        run: () => transformOne(log, rel),
    }));

    const report = await runIncremental({
        name: 'img',
        log,
        jobs,
        orphanScan: { dirs: ['imgs-generated'], match: () => true },
    });

    // ---- img-map.json
    //
    // 변환을 건너뛴 원본도 크기는 있어야 한다. 그래서 이 갱신은 작업 목록과 무관하게
    // 전체를 훑는다. 이미 아는 크기는 다시 재지 않으므로 비용은 무시할 만하다.
    let previousMap: Record<string, Size> = {};
    try {
        previousMap = JSON.parse(await fsp.readFile(resolve(IMG_MAP_FILE), 'utf8'));
    } catch {
        log.line(`${IMG_MAP_FILE} 이 없어 새로 만든다`);
    }
    const imgMap: Record<string, Size> = {};
    let measured = 0;
    for (const rel of sources) {
        const key = mapKey(rel);
        const known = previousMap[key];
        if (known != null && typeof known.width === 'number' && typeof known.height === 'number') {
            imgMap[key] = known;
            continue;
        }
        try {
            const metadata = await sharp(resolve(rel), { animated: rel.endsWith('gif') }).metadata();
            imgMap[key] = { width: metadata.width, height: metadata.height };
            measured += 1;
        } catch (e) {
            // 읽을 수 없는 파일 하나 때문에 img-map 전체를 못 쓰게 두지 않는다.
            // 등재되지 않은 경로는 `+w3img` 가 평범한 <img> 로 폴백한다.
            log.error(`${rel} 의 크기를 잴 수 없다`, e);
        }
    }
    const dropped = Object.keys(previousMap).filter((k) => imgMap[k] == null);
    if (dropped.length !== 0) log.line(`img-map 에서 사라진 원본 ${dropped.length}개를 지웠다`);

    // 정렬해 쓴다. 이 파일은 pug 요소의 공유 입력이라 순서가 흔들리면 전체 재렌더가 된다.
    const sortedMap = Object.fromEntries(Object.entries(imgMap).sort(([a], [b]) => a.localeCompare(b)));
    const text = JSON.stringify(sortedMap);
    const before = await fsp.readFile(resolve(IMG_MAP_FILE), 'utf8').catch(() => null);
    if (before !== text) {
        await fsp.writeFile(resolve(IMG_MAP_FILE), text);
        log.line(`${IMG_MAP_FILE} 갱신 — 항목 ${Object.keys(sortedMap).length}개 (새로 잰 것 ${measured}개)`);
    } else {
        log.line(`${IMG_MAP_FILE} 변경 없음 — 항목 ${Object.keys(sortedMap).length}개`);
    }

    if (report.failed.length !== 0) {
        throw new ToolError(`변환 실패 ${report.failed.length}개: ${report.failed.join(', ')}`);
    }
    return report.ran === 0 ? `변경 없음 (${report.skipped}개)` : `갱신 ${report.ran} · 건너뜀 ${report.skipped}`;
});
