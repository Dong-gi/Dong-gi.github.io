/**
 * 빌드 요소: dates
 *
 *     npm run build-dates      → source/build/build-dates.log
 *
 * `source/doc-dates.json` 을 git 이력에서 갱신한다. 문서마다 "언제 고쳤는가" 를 적어 두는
 * 파일이고, 홈의 "최근 갱신" 목록과 각 문서의 갱신일 표시가 이 값을 쓴다.
 *
 * 파일 mtime 을 그대로 쓰지 않는 이유는, 새로 클론하면 전부 체크아웃 시각이 되어
 * 250여 페이지의 날짜가 한꺼번에 덮이기 때문이다. git 이력에서 구한 값을 적어 두고
 * `lastSha` 이후의 커밋만 증분으로 확인한다. 여기 없는 문서만 mtime 으로 대체한다.
 *
 * **이 요소만 사람에게 묻는다.** 커밋 하나하나에 대해 "이걸 갱신일로 쓸까?" 를 묻는다.
 * 오타 수정 커밋까지 갱신일이 되면 목록이 쓸모없어지기 때문이다. 그래서
 * `source/build.ts` 는 이 요소를 다른 요소보다 **먼저, 혼자** 돌린다. 물음과 다른
 * 요소의 진행 출력이 섞이면 무엇에 답하는지 알 수 없다.
 *
 * 해시 매니페스트를 쓰지 않는다. `lastSha` 가 같은 일을 이미 하고 있다.
 */
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import readline from 'node:readline/promises';
import { $ } from 'zx';
import { runComponent, type BuildLog } from './lib/log.ts';
import { resolve } from './lib/paths.ts';

$.quiet = true;

const DOC_DATES_FILE = 'source/doc-dates.json';
const POSTS_FILE = 'source/posts.json';

/** source/doc-dates.json */
interface DocDates {
    /** 마지막으로 갱신일 계산에 반영한 커밋. 이 커밋 이후만 다시 본다. */
    lastSha: string | null;
    /** 'pugs/dev/aws.pug' -> UNIX timestamp */
    dates: Record<string, number>;
}

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
        const result = await $({ nothrow: true, cwd: resolve('.') })`git ${args}`;
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
/**
 * 물을 수 있는 상태인가. 비대화형이거나 입력이 끊기면 기본값으로 넘어간다.
 *
 * stdout 까지 확인하는 이유가 있다. `npm run build > build.log` 처럼 출력만 파일로
 * 돌리면 stdin 은 여전히 터미널이라, 예전 빌드는 **보이지 않는 질문**을 던져 놓고
 * 멈춰 있었다. 물음이 사람에게 닿지 않는 상황이면 묻지 않는 편이 낫다.
 */
let interactive = process.stdin.isTTY === true && process.stdout.isTTY === true;

/** [Y/n] 을 묻는다. 엔터만 치면 y. */
async function confirm(log: BuildLog, message: string): Promise<boolean> {
    if (!interactive) {
        log.line(`${message} [Y/n] y (비대화형이라 기본값)`);
        return true;
    }
    prompt ??= readline.createInterface({ input: process.stdin, output: process.stdout });
    let answer: string;
    try {
        answer = await prompt.question(`${message} [Y/n] `);
    } catch {
        // Ctrl+D 등으로 입력이 끊긴 경우. 여기서 빌드를 죽이는 것보다 기본값이 낫다.
        interactive = false;
        log.line('입력이 끊겨 남은 커밋은 기본값(y)으로 처리한다');
        return true;
    }
    const normalized = answer.trim().toLowerCase();
    return normalized === '' || normalized === 'y' || normalized === 'yes';
}

await runComponent('dates', async (log) => {
    const state = JSON.parse(fs.readFileSync(resolve(DOC_DATES_FILE), 'utf8')) as DocDates;
    const posts: { file: string }[] = JSON.parse(await fsp.readFile(resolve(POSTS_FILE), 'utf8'));

    const head = (await git('rev-parse', 'HEAD'))?.trim();
    if (!head) {
        log.warn('git 이력을 읽을 수 없어 저장된 갱신일을 그대로 쓴다');
        return '건너뜀 (git 없음)';
    }
    if (state.lastSha === head) {
        log.line(`HEAD(${head.slice(0, 8)}) 가 이미 반영돼 있다`);
        return '변경 없음';
    }

    const log_ = (await git('log', '--reverse', '--format=%H%x09%aI%x09%s', `${state.lastSha}..HEAD`, '--', 'pugs')) ?? '';
    const commits = log_
        .split('\n')
        .filter((l) => l !== '')
        .map((l) => {
            const [sha, iso, subject] = l.split('\t');
            return { sha, iso, subject };
        });

    let applied = 0;
    try {
        if (commits.length !== 0) log.line(`확인할 커밋 ${commits.length}개`);
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
            const answer = await confirm(log, `  ${sha.slice(0, 8)} ${day} 문서 ${edited.length}개 — ${subject}\n  갱신일로 쓸까?`);
            if (!answer) {
                log.line(`  ${sha.slice(0, 8)} 건너뜀`);
                continue;
            }
            for (const c of edited) state.dates[c.path] = new Date(iso).getTime();
            applied += 1;
            log.line(`  ${sha.slice(0, 8)} ${day} 반영 — 문서 ${edited.length}개`);
        }
    } finally {
        // 안 닫으면 stdin 이 열린 채로 남아 빌드가 끝나지 않는다.
        prompt?.close();
    }

    // 경로순으로 써야 diff 가 잘 잡힌다
    const activeKeySet = new Set(posts.map((x) => 'pugs/' + x.file.replace(/\.html$/, '.pug')));
    const payload: DocDates = {
        lastSha: head,
        dates: Object.fromEntries(
            Object.entries(state.dates)
                .filter((x) => activeKeySet.has(x[0]))
                .sort(([a], [b]) => a.localeCompare(b)),
        ),
    };
    await fsp.writeFile(resolve(DOC_DATES_FILE), JSON.stringify(payload, null, 4) + '\n');
    log.line(`${DOC_DATES_FILE} 갱신 — 문서 ${Object.keys(payload.dates).length}개, lastSha ${head.slice(0, 8)}`);

    return commits.length === 0 ? 'lastSha 만 갱신' : `커밋 ${commits.length}개 중 ${applied}개 반영`;
});
