/**
 * 빌드 요소: js
 *
 *     npm run build-js        → source/build/build-js.log
 *
 * `source/default.js` 를 압축해 `source/default.min.js` 로 쓴다. 페이지가 실제로
 * 불러가는 것은 압축본이므로, 원본만 고치고 이 요소를 돌리지 않으면 화면이 바뀌지 않는다.
 *
 * 예전에는 `npx terser …` 를 셸로 불렀다. npx 는 매번 패키지 해석을 다시 하고,
 * 네트워크가 없는 환경에서는 캐시가 없으면 실패한다. terser 는 이미 devDependency 이므로
 * 라이브러리로 직접 부른다.
 */
import fsp from 'node:fs/promises';
import { minify } from 'terser';
import { runComponent, ToolError } from './lib/log.ts';
import { runIncremental } from './lib/manifest.ts';
import { resolve } from './lib/paths.ts';

const SOURCE = 'source/default.js';
const OUTPUT = 'source/default.min.js';

await runComponent('js', async (log) => {
    const report = await runIncremental({
        name: 'js',
        log,
        jobs: [
            {
                key: SOURCE,
                inputs: [SOURCE],
                outputs: [OUTPUT],
                async run() {
                    const code = await fsp.readFile(resolve(SOURCE), 'utf8');
                    const result = await minify(code, { compress: true, mangle: true });
                    if (result.code == null) throw new Error('terser 가 코드를 돌려주지 않았다');
                    await fsp.writeFile(resolve(OUTPUT), result.code);
                    const ratio = ((1 - result.code.length / code.length) * 100).toFixed(0);
                    log.line(`${OUTPUT} — ${(code.length / 1024).toFixed(1)}KB → ${(result.code.length / 1024).toFixed(1)}KB (${ratio}% 감소)`);
                },
            },
        ],
    });

    if (report.failed.length !== 0) throw new ToolError(`${OUTPUT} 생성 실패`);
    return report.ran === 0 ? '변경 없음' : '압축 1개';
});
