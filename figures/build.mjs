/**
 * figures/*.mjs 가 정의한 그림을 SVG 파일로 쓴다.
 *
 *   node figures/build.mjs
 *
 * 산출물은 /figures/<과목>/<이름>.svg 이고, pug 에서는
 *   +w3img('/figures/physics/kinematics-atvx.svg', '캡션')
 * 처럼 참조한다. img-map.json 에 등록하지 않아도 w3img 가 평범한 <img> 로 폴백한다.
 *
 * d2 와 달리 외부 바이너리가 필요 없어 어느 환경에서든 같은 결과가 나온다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SUBJECTS = ['physics', 'chemistry', 'biology', 'mathematics', 'linear-algebra', 'statistics'];

/**
 * 한 과목의 그림 정의는 여러 파일에 나눠 둘 수 있다.
 * physics.mjs, physics-rotation.mjs … 처럼 접두어가 같으면 모두 읽는다.
 * 장별로 파일을 나눠야 여러 사람이(또는 여러 작업이) 동시에 그림을 추가해도 충돌하지 않는다.
 */
function modulesOf(subject) {
    return fs.readdirSync(HERE)
        .filter(f => f.endsWith('.mjs') && (f === `${subject}.mjs` || f.startsWith(`${subject}-`)))
        .sort();
}

let total = 0;
for (const subject of SUBJECTS) {
    const mods = modulesOf(subject);
    if (mods.length === 0) continue;
    const outDir = path.join(HERE, subject);
    fs.mkdirSync(outDir, { recursive: true });

    const figs = [];
    for (const m of mods) figs.push(...(await import(`./${m}`)).default);

    const seen = new Set();
    for (const fig of figs) {
        if (seen.has(fig.name)) throw new Error(`그림 이름이 겹친다: ${subject}/${fig.name}`);
        seen.add(fig.name);
        const file = path.join(outDir, `${fig.name}.svg`);
        fs.writeFileSync(file, fig.svg);
        console.log(`  ${subject}/${fig.name}.svg  ${(fig.svg.length / 1024).toFixed(1)}KB`);
        total += 1;
    }
}
console.log(`그림 ${total}개 생성`);
