/**
 * 빌드 오케스트레이터.
 *
 *     npm run build
 *
 * 빌드 **요소**들을 각각 자식 프로세스로 돌리고, 성공과 실패만 콘솔에 한 줄씩 보고한다.
 * 자세한 내용은 요소마다 자기 로그 파일에 남는다.
 *
 *     npm run build-dates     source/build/dates.ts      source/build/build-dates.log
 *     npm run build-js        source/build/js.ts         source/build/build-js.log
 *     npm run build-figure    source/build/figure.ts     source/build/build-figure.log
 *     npm run build-d2        source/build/d2.ts         source/build/build-d2.log
 *     npm run build-img       source/build/img.ts        source/build/build-img.log
 *     npm run build-pug       source/build/pug.ts        source/build/build-pug.log
 *
 * 로그와 해시 기록은 요소 스크립트와 같은 폴더에 둔다. 저장소 루트에 부산물이 흩어져
 * 있으면 루트를 열 때마다 사이트의 소스보다 그것들이 먼저 눈에 들어온다.
 *
 * 요소는 따로 돌려도 결과가 같다. `npm run build` 는 그 명령들을 대신 불러 줄 뿐이다.
 *
 * ## 왜 프로세스인가
 *
 * 예전 빌드는 워커 스레드 풀 하나에 pug·d2·img 작업을 번갈아 밀어 넣었다. 그래서
 * 로그 한 줄이 어느 작업의 것인지 알 수 없었다. 지금은 요소 하나가 프로세스 하나이고
 * 프로세스 하나가 로그 파일 하나다. 출처를 따질 일이 없다.
 *
 * ## 왜 요소 안은 단일 스레드인가
 *
 * 병렬은 여기 한 곳에만 있다. 요소 안은 순차 실행이다. 해시 매니페스트
 * (`source/build/build-<요소>-sha.json`) 덕분에 평소 빌드의 대상은 방금 고친 문서 한두 개라,
 * 요소 안에서 더 쪼개도 얻을 것이 거의 없다. 전면 재빌드는 드물게 일어나고, 그때의
 * 몇 분을 아끼자고 로그의 출처를 다시 잃을 이유는 없다.
 *
 * ## 순서
 *
 * 요소 사이의 의존은 두 개뿐이고 `needs` 에 적혀 있다. 나머지는 모두 동시에 돈다.
 *
 *   img   → pug   `source/img-map.json` (문서가 반응형 이미지를 쓰려면 원본 크기를 알아야 한다)
 *   dates → pug   `source/doc-dates.json` (문서의 갱신일)
 *
 * `needs` 는 **순서만** 뜻한다. 선행 요소가 실패해도 뒤 요소는 돈다. 사진 하나가
 * 깨졌다고 문서 250개의 렌더를 막을 이유는 없고, 그때 pug 가 읽는 것은 지난번의
 * 온전한 산출물이기 때문이다. 나중에 선행 요소가 성공하면 그 산출물의 해시가 바뀌므로
 * 뒤 요소가 스스로 다시 돈다 — 손으로 챙길 것이 없다.
 *
 * `dates` 는 사람에게 묻는 유일한 요소라 다른 요소보다 먼저, 혼자 돈다. 물음과 다른
 * 요소의 출력이 섞이면 무엇에 답하는지 알 수 없다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { QUIET_ENV, SUMMARY_PREFIX } from './build/lib/log.ts';
import { ROOT, logPathOf, resolve } from './build/lib/paths.ts';

interface Component {
    name: string;
    /** 저장소 기준 스크립트 경로. package.json 의 `build-<이름>` 이 부르는 것과 같다. */
    script: string;
    /** 이 요소보다 먼저 성공해야 하는 요소들. */
    needs: string[];
    /**
     * 사람에게 묻는가. 묻는 요소는 다른 요소가 하나도 돌지 않는 동안 혼자 돈다.
     * 그래야 물음과 답이 다른 출력에 묻히지 않는다.
     */
    interactive?: boolean;
}

const COMPONENTS: Component[] = [
    { name: 'dates', script: 'source/build/dates.ts', needs: [], interactive: true },
    { name: 'js', script: 'source/build/js.ts', needs: [] },
    { name: 'figure', script: 'source/build/figure.ts', needs: [] },
    { name: 'd2', script: 'source/build/d2.ts', needs: [] },
    { name: 'img', script: 'source/build/img.ts', needs: [] },
    { name: 'pug', script: 'source/build/pug.ts', needs: ['img', 'dates'] },
];

interface Result {
    name: string;
    ok: boolean;
    /** 요소가 로그 끝에 남긴 요약 한 줄. */
    summary: string;
    elapsedMs: number;
}

const byName = new Map(COMPONENTS.map((c) => [c.name, c]));
const NAME_WIDTH = Math.max(...COMPONENTS.map((c) => c.name.length));

function logPath(name: string): string {
    return resolve(logPathOf(name));
}

/**
 * 요소가 남긴 `[요약] …` 줄을 로그에서 읽는다.
 *
 * 자식의 stdout 을 가로채지 않고 파일에서 읽는 이유는, 대화형 요소는 stdio 를
 * 그대로 물려받아 가로챌 수 없기 때문이다. 파일에서 읽으면 두 경우를 같게 다룬다.
 */
async function readSummary(name: string): Promise<string> {
    try {
        const text = await fsp.readFile(logPath(name), 'utf8');
        const lines = text.split('\n').filter((l) => l.startsWith(SUMMARY_PREFIX));
        return lines.length === 0 ? '' : lines[lines.length - 1].slice(SUMMARY_PREFIX.length);
    } catch {
        return '';
    }
}

/**
 * 실패했을 때 콘솔에 보여 줄 부분.
 *
 * 그냥 마지막 몇 줄을 잘라 오면 안 된다. 요소가 마지막에 경고를 여럿 남기면 정작
 * 원인인 오류가 화면 밖으로 밀려난다. 오류 줄과 그에 딸린 들여쓴 상세만 모으고,
 * 오류가 하나도 없을 때만 꼬리로 물러선다.
 */
async function readFailureDetail(name: string, lines: number): Promise<string[]> {
    let text: string;
    try {
        text = await fsp.readFile(logPath(name), 'utf8');
    } catch {
        return [];
    }
    const all = text.split('\n').filter((l) => l !== '');
    const picked: string[] = [];
    let inError = false;
    for (const line of all) {
        if (line.startsWith('오류: ')) {
            inError = true;
            picked.push(line);
        } else if (inError && /^\s/.test(line)) {
            picked.push(line);
        } else {
            inError = false;
        }
    }
    return (picked.length === 0 ? all : picked).slice(-lines);
}

function spawnComponent(c: Component): Promise<{ code: number | null; stderr: string }> {
    return new Promise((settle) => {
        // 요소의 진행 로그는 콘솔로 내보내지 않는다. 여섯 개가 뒤섞이면 요소를 나눈
        // 뜻이 없다. 각자 자기 source/build/build-<이름>.log 에 남긴다.
        //
        // 대화형 요소만 stdio 를 물려받는다. readline 의 물음은 BuildLog 를 거치지 않고
        // stdout 으로 바로 나가므로, QUIET 를 켜도 질문은 화면에 그대로 보인다.
        const child = spawn(process.execPath, [path.join(ROOT, c.script)], {
            cwd: ROOT,
            env: { ...process.env, [QUIET_ENV]: '1' },
            stdio: c.interactive ? 'inherit' : ['ignore', 'ignore', 'pipe'],
        });
        let stderr = '';
        child.stderr?.on('data', (chunk) => {
            stderr += String(chunk);
        });
        child.on('error', (e) => {
            stderr += e instanceof Error ? e.message : String(e);
            settle({ code: 1, stderr });
        });
        child.on('close', (code) => settle({ code, stderr }));
    });
}

/**
 * 요소 하나를 돌린다. 선행 요소가 있으면 그것부터 돌린다.
 *
 * 같은 요소를 두 번 부르면 같은 약속을 돌려준다. 그래서 의존 그래프를 따로 정렬하지
 * 않아도 각 요소가 정확히 한 번 실행되고, 의존이 없는 요소들은 자연히 동시에 돈다.
 */
const started = new Map<string, Promise<Result>>();
function launch(c: Component): Promise<Result> {
    const existing = started.get(c.name);
    if (existing != null) return existing;

    const promise = (async (): Promise<Result> => {
        const deps = await Promise.all(c.needs.map((n) => launch(byName.get(n)!)));
        const broken = deps.filter((d) => !d.ok).map((d) => d.name);
        const t0 = Date.now();
        const { code, stderr } = await spawnComponent(c);
        const elapsedMs = Date.now() - t0;
        const summary = (await readSummary(c.name)) || (code === 0 ? '완료' : stderr.trim().split('\n').at(-1) ?? `종료 코드 ${code}`);
        const note = broken.length === 0 ? '' : ` — 선행 ${broken.join(', ')} 실패, 지난 산출물로 진행`;
        return { name: c.name, ok: code === 0, summary: summary + note, elapsedMs };
    })();

    started.set(c.name, promise);
    return promise;
}

function report(r: Result): void {
    const time = `${(r.elapsedMs / 1000).toFixed(1)}초`.padStart(7);
    console.log(`  ${r.ok ? '✔' : '✘'} ${r.name.padEnd(NAME_WIDTH)} ${time}  ${r.summary}`);
}

// ---------------------------------------------------------------- 실행

const startedAt = Date.now();
const interactive = COMPONENTS.filter((c) => c.interactive);
const concurrent = COMPONENTS.filter((c) => !c.interactive);

console.log(`빌드 시작 — 요소 ${COMPONENTS.length}개. 자세한 내용은 source/build/build-<요소>.log\n`);

// 묻는 요소를 먼저, 하나씩. 결과가 started 에 남으므로 뒤의 의존이 그대로 쓴다.
for (const c of interactive) report(await launch(c));

// 나머지는 의존이 풀리는 대로 동시에. 끝나는 순서대로 보고한다.
const done = await Promise.all(
    concurrent.map((c) =>
        launch(c).then((r) => {
            report(r);
            return r;
        }),
    ),
);
const all = [...(await Promise.all(interactive.map((c) => launch(c)))), ...done];

const failed = all.filter((r) => !r.ok);
console.log('');
if (failed.length === 0) {
    console.log(`${all.length}개 요소 모두 성공 — ${((Date.now() - startedAt) / 1000).toFixed(1)}초`);
} else {
    console.log(
        `${all.length - failed.length}개 성공, ${failed.length}개 실패: ${failed.map((r) => r.name).join(', ')}` +
        ` — ${((Date.now() - startedAt) / 1000).toFixed(1)}초`,
    );
    for (const r of failed) {
        const detail = await readFailureDetail(r.name, 20);
        console.log(`\n--- source/build/build-${r.name}.log ---`);
        for (const line of detail) console.log(`  ${line}`);
    }
    process.exitCode = 1;
}
