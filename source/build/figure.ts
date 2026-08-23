/**
 * 빌드 요소: figure
 *
 *     npm run build-figure     → source/build/build-figure.log
 *
 * `figures/*.mjs` 가 정의한 그림을 `figures/<과목>/<이름>.svg` 로 쓴다. pug 에서는
 *   +w3img('/figures/physics/kinematics-atvx.svg', '캡션')
 * 처럼 참조한다. `source/img-map.json` 에 등록할 필요는 없다 — 등재되지 않은 경로는
 * `+w3img` 가 평범한 `<img>` 로 폴백한다.
 *
 * d2 와 달리 외부 바이너리가 필요 없어 어느 환경에서든 같은 결과가 나온다.
 *
 * 증분 방식이 다른 요소와 조금 다르다. 그림 하나를 만드는 비용은 문자열 조립뿐이라
 * 거의 공짜인 반면, 어떤 그림이 나오는지는 모듈을 실제로 불러 봐야 안다. 그래서
 *
 *   - 모듈(`figures/*.mjs`)이 하나도 바뀌지 않았고 기록된 SVG 가 전부 제자리에 있으면
 *     모듈을 불러오지도 않고 끝낸다.
 *   - 그렇지 않으면 전부 만들어 보고, **내용이 달라진 것만** 파일에 쓴다.
 *
 * 두 번째 규칙이 중요하다. 모듈 하나를 고쳤다고 900개 SVG 의 mtime 을 전부 갱신하면
 * git 이 바뀌지 않은 파일까지 변경으로 잡는다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import { runComponent } from './lib/log.ts';
import { hashOf, hashText, readManifest, writeManifest, type Manifest } from './lib/manifest.ts';
import { fileExists, normalize, resolve, walkFiles } from './lib/paths.ts';

/** 그림을 둘 과목. 여기 없는 접두어의 모듈은 무시된다. */
const SUBJECTS = ['physics', 'chemistry', 'biology', 'mathematics',
    'linear-algebra', 'statistics', 'algorithm', 'mcs', 'logic', 'philosophy'];

interface Figure {
    name: string;
    svg: string;
}

/**
 * 한 과목의 그림 정의는 여러 파일에 나눠 둘 수 있다.
 * physics.mjs, physics-rotation.mjs … 처럼 접두어가 같으면 모두 읽는다.
 * 장별로 파일을 나눠야 여러 사람이(또는 여러 작업이) 동시에 그림을 추가해도 충돌하지 않는다.
 */
function modulesOf(subject: string, all: string[]): string[] {
    return all.filter((f) => {
        const base = path.basename(f);
        return base === `${subject}.mjs` || base.startsWith(`${subject}-`);
    });
}

await runComponent('figure', async (log) => {
    // lib.mjs 처럼 과목에 속하지 않는 모듈도 입력이다. 모든 그림이 그것을 쓴다.
    const modules = (await walkFiles('figures')).filter((f) => f.endsWith('.mjs') && !f.slice('figures/'.length).includes('/'));
    if (modules.length === 0) throw new Error('figures/*.mjs 를 하나도 찾지 못했다');

    const previous = await readManifest('figure');
    const next: Manifest = {};
    let moduleChanged = Object.keys(previous).length === 0;
    for (const m of modules) {
        const h = await hashOf([m]);
        next[m] = h;
        if (previous[m] !== h) moduleChanged = true;
    }
    // 사라진 모듈도 변경이다. 그 모듈이 만들던 그림이 이제 주인이 없으므로 알려야 한다.
    const gone = Object.keys(previous).filter((k) => k.endsWith('.mjs') && next[k] == null);
    if (gone.length !== 0) {
        log.line(`사라진 모듈 ${gone.length}개: ${gone.join(', ')}`);
        moduleChanged = true;
    }
    // 매니페스트에 기록돼 있던 산출물이 사라졌으면 모듈이 그대로여도 다시 만들어야 한다.
    const recorded = Object.keys(previous).filter((k) => k.endsWith('.svg'));
    let outputMissing = false;
    for (const svg of recorded) {
        if (!(await fileExists(svg))) {
            log.line(`${svg} 가 없어 다시 만든다`);
            outputMissing = true;
            break;
        }
    }

    if (!moduleChanged && !outputMissing) {
        log.line(`모두 최신이다 — 모듈 ${modules.length}개, 그림 ${recorded.length}개 건너뜀`);
        return '변경 없음';
    }

    const expected = new Set<string>();
    let written = 0;
    for (const subject of SUBJECTS) {
        const mods = modulesOf(subject, modules);
        if (mods.length === 0) continue;
        await fsp.mkdir(resolve(`figures/${subject}`), { recursive: true });

        const figures: Figure[] = [];
        for (const m of mods) {
            const loaded = (await import(new URL(`file://${resolve(m)}`).href)).default as Figure[];
            figures.push(...loaded);
        }

        const seen = new Set<string>();
        for (const fig of figures) {
            if (seen.has(fig.name)) throw new Error(`그림 이름이 겹친다: ${subject}/${fig.name}`);
            seen.add(fig.name);

            const key = `figures/${subject}/${fig.name}.svg`;
            expected.add(key);
            const h = hashText(fig.svg);
            next[key] = h;
            // 내용이 같으면 쓰지 않는다. 파일 mtime 을 건드리지 않아야 git 이 조용하다.
            if (previous[key] === h && (await fileExists(key))) continue;
            await fsp.writeFile(resolve(key), fig.svg);
            written += 1;
            log.line(`${key}  ${(fig.svg.length / 1024).toFixed(1)}KB`);
        }
        log.line(`${subject} — 그림 ${figures.length}개 (모듈 ${mods.length}개)`);
    }

    await writeManifest('figure', next);

    // 이름을 바꾸거나 지운 그림의 SVG 가 남아 있으면 알린다. 지우지는 않는다.
    const orphans: string[] = [];
    for (const subject of SUBJECTS) {
        for (const file of await walkFiles(`figures/${subject}`)) {
            if (file.endsWith('.svg') && !expected.has(normalize(file))) orphans.push(file);
        }
    }
    if (orphans.length !== 0) {
        log.warn(`정의가 사라진 SVG ${orphans.length}개가 남아 있다. 필요 없으면 손으로 지워라`);
        for (const o of orphans.slice(0, 20)) log.line(`    ${o}`);
        if (orphans.length > 20) log.line(`    … 외 ${orphans.length - 20}개`);
    }

    return `그림 ${expected.size}개 중 ${written}개 갱신`;
});
