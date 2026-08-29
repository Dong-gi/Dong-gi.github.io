/**
 * 빌드 가속을 담당하는 해시 매니페스트.
 *
 * 요소마다 `source/build/build-<이름>-sha.json` 을 하나 갖는다. 요소 스크립트
 * (`source/build/pug.ts` …) 바로 옆이다. 내용은
 * **(저장소 기준 정규화 경로) -> (내용 해시)** 하나뿐이다.
 *
 *     {
 *         "pugs/fundamental/physics.pug": "9f2c…",
 *         "source/img-map.json": "1ab7…"
 *     }
 *
 * 빌드는 지금 계산한 해시와 파일에 적힌 해시를 견줘, 같으면 그 항목의 작업을 건너뛴다.
 *
 * 왜 mtime 이 아니라 내용 해시인가. mtime 은 git 이 보존하지 않는다. 클론하거나
 * 브랜치를 오가면 바뀌지 않은 파일의 mtime 이 전부 갱신돼 250여 개 문서를 다시 렌더하게
 * 된다. 반대로 되돌리기(`git checkout -- file`)를 하면 내용은 예전인데 mtime 은 최신이라
 * 역시 어긋난다. 내용 해시는 두 경우 모두 옳게 판단한다.
 *
 * 전면 재빌드가 필요하면 **해당 sha.json 을 지우면 된다.** 매니페스트가 없으면 모든
 * 항목이 대상이 된다. 빌드 코드 자체를 고쳐 산출물의 모양이 달라졌을 때 쓰는 손잡이다.
 *
 * 산출물의 존재도 함께 확인한다. 해시가 같아도 산출 파일이 없으면 다시 만든다.
 * 그래야 `posts/` 를 손으로 지우거나 새로 클론했을 때 매니페스트가 거짓말을 하지 않는다.
 */
import { createHash } from 'node:crypto';
import fsp from 'node:fs/promises';
import type { BuildLog } from './log.ts';
import { fileExists, manifestPathOf, normalize, resolve, walkFiles } from './paths.ts';

/** (정규화 경로) -> (내용 해시) */
export type Manifest = Record<string, string>;

export async function readManifest(name: string): Promise<Manifest> {
    try {
        const text = await fsp.readFile(resolve(manifestPathOf(name)), 'utf8');
        const parsed: unknown = JSON.parse(text);
        // 손으로 지우다 만 파일이나 옛 형식이 들어와도 빌드를 멈추지 않는다.
        // 매니페스트를 못 읽는 것은 "전부 다시 만들라"는 뜻일 뿐이다.
        if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
        return parsed as Manifest;
    } catch {
        return {};
    }
}

export async function writeManifest(name: string, manifest: Manifest): Promise<void> {
    // 키 순서로 정렬해 쓴다. 이 파일은 저장소에 커밋되므로 diff 가 읽혀야 한다.
    const sorted = Object.fromEntries(Object.entries(manifest).sort(([a], [b]) => a.localeCompare(b)));
    await fsp.writeFile(resolve(manifestPathOf(name)), JSON.stringify(sorted, null, 4) + '\n');
}

/**
 * 파일들의 내용을 하나의 해시로 묶는다.
 *
 * 경로와 길이를 내용 사이에 끼워 넣는 이유는, 파일 경계가 사라져 서로 다른 조합이
 * 같은 해시를 갖는 것을 막기 위해서다(`["ab","c"]` 와 `["a","bc"]`).
 *
 * 없는 파일은 오류가 아니라 '없음'이라는 상태로 해시에 반영한다. pug 의 include 처럼
 * 있다가 사라질 수 있는 입력이 있고, 사라진 것 자체가 다시 만들 이유다.
 */
export async function hashOf(relPaths: string[]): Promise<string> {
    const h = createHash('sha256');
    for (const rel of relPaths) {
        h.update(rel);
        h.update('\0');
        try {
            const buf = await fsp.readFile(resolve(rel));
            h.update(String(buf.byteLength));
            h.update('\0');
            h.update(buf);
        } catch {
            h.update('없음\0');
        }
    }
    return h.digest('hex');
}

/** 파일 하나의 내용 해시. */
export function hashText(text: string): string {
    return createHash('sha256').update(text).digest('hex');
}

/** 입력 파일 하나(또는 몇 개)를 산출물로 바꾸는 최소 작업 단위. */
export interface Job {
    /** 매니페스트의 키. 보통 입력 파일의 정규화 경로다. */
    key: string;
    /** 해시에 들어갈 파일들. 보통 `[key]` 이고, pug 처럼 include 가 있으면 그것까지. */
    inputs: string[];
    /** 존재를 확인할 산출물. 하나라도 없으면 해시가 같아도 다시 만든다. */
    outputs: string[];
    run(): Promise<void>;
    /**
     * 해시와 상관없이 매번 돈다. 산출물이 저장소 밖의 것(예: git 이력)에 딸려 있어
     * 입력 해시로는 낡음을 알 수 없는 작업에만 쓴다. 값이 싼 작업이어야 한다.
     */
    always?: boolean;
}

export interface IncrementalReport {
    /** 실제로 다시 만든 작업 수. */
    ran: number;
    /** 해시가 같아 건너뛴 작업 수. */
    skipped: number;
    /** 실패한 작업의 키. 이 항목들은 매니페스트에 기록하지 않아 다음 빌드가 다시 시도한다. */
    failed: string[];
    /** 공유 입력이 바뀌어 전체를 다시 만들었는가. */
    rebuiltAll: boolean;
}

export interface IncrementalOptions {
    /** 요소 이름. 매니페스트 파일 이름이 된다. */
    name: string;
    log: BuildLog;
    jobs: Job[];
    /**
     * 모든 작업이 함께 의존하는 파일들. 하나라도 바뀌면 전체를 다시 만든다.
     * pug 의 `source/img-map.json` 처럼 어느 한 작업의 입력이라고 하기 어려운 것들이다.
     */
    shared?: string[];
    /**
     * 이 요소가 독점하는 산출 디렉터리. 어떤 작업도 만들지 않는 파일이 남아 있으면
     * 경고한다. 지우지는 않는다 — 빌드가 사용자의 파일을 지우기 시작하면
     * 규칙 하나가 어긋났을 때 잃는 것이 너무 크다.
     */
    orphanScan?: { dirs: string[]; match: (rel: string) => boolean };
}

/**
 * 매니페스트를 보고 달라진 작업만 순서대로 돌린다.
 *
 * 병렬 처리를 하지 않는다. 요소 **사이**의 병렬은 오케스트레이터(`source/build.ts`)가
 * 맡고, 요소 **안**은 단순한 순차 실행으로 둔다. 규칙을 한 곳에만 두면 로그의 출처가
 * 항상 분명하고, 해시 덕분에 평소 빌드는 대상이 한두 개라 병렬로 얻을 것도 거의 없다.
 */
export async function runIncremental(opts: IncrementalOptions): Promise<IncrementalReport> {
    const { name, log, jobs } = opts;
    const shared = opts.shared ?? [];

    const seen = new Set<string>();
    for (const job of jobs) {
        if (seen.has(job.key)) throw new Error(`작업 키가 겹친다: ${job.key}`);
        seen.add(job.key);
    }
    for (const p of shared) {
        if (seen.has(p)) throw new Error(`공유 입력이 작업 키와 겹친다: ${p}`);
    }

    const previous = await readManifest(name);
    const next: Manifest = {};

    // 공유 입력 검사. 하나라도 바뀌면 전체가 대상이다.
    const changedShared: string[] = [];
    for (const p of shared) {
        const h = await hashOf([p]);
        next[p] = h;
        if (previous[p] !== h) changedShared.push(p);
    }
    const rebuiltAll = changedShared.length !== 0 || Object.keys(previous).length === 0;
    if (changedShared.length !== 0) {
        log.line(`공유 입력이 바뀌어 전체를 다시 만든다: ${changedShared.join(', ')}`);
    } else if (rebuiltAll) {
        log.line(`${manifestPathOf(name)} 가 없어 전체를 다시 만든다`);
    }

    // 어떤 작업이 대상인지 먼저 모두 판정한다. 실행과 판정을 섞지 않으면
    // "대상 N개" 를 시작할 때 알려 줄 수 있고, 진행 상황을 셀 수 있다.
    const hashes = new Map<string, string>();
    const dirty: Job[] = [];
    for (const job of jobs) {
        const h = await hashOf(job.inputs);
        hashes.set(job.key, h);
        if (rebuiltAll || job.always === true || previous[job.key] !== h) {
            dirty.push(job);
            continue;
        }
        let outputMissing = false;
        for (const out of job.outputs) {
            if (!(await fileExists(out))) {
                outputMissing = true;
                break;
            }
        }
        if (outputMissing) {
            log.line(`${job.key} — 산출물이 없어 다시 만든다`);
            dirty.push(job);
        }
    }

    // 끝난 작업의 해시를 담아 둔다. 실패한 작업은 넣지 않는다 — 넣으면 다음 빌드가
    // "최신"이라 판단해 실패한 산출물이 영영 갱신되지 않는다.
    const done: Manifest = { ...next };
    for (const job of jobs) {
        if (!dirty.includes(job)) done[job.key] = hashes.get(job.key)!;
    }

    const failed: string[] = [];
    if (dirty.length === 0) {
        log.line(`모두 최신이다 — ${jobs.length}개 건너뜀`);
    } else {
        log.line(`대상 ${dirty.length}개, 건너뜀 ${jobs.length - dirty.length}개`);
        let index = 0;
        let lastSaved = Date.now();
        for (const job of dirty) {
            index += 1;
            try {
                await job.run();
                done[job.key] = hashes.get(job.key)!;
            } catch (e) {
                failed.push(job.key);
                log.error(`[${index}/${dirty.length}] ${job.key}`, e);
            }
            // 도중에 끊겨도 여기까지의 진행이 남게 한다. 수식 문서 하나가 20초씩
            // 걸리는 빌드에서 프로세스가 죽으면(원격 셸의 시간 제한, Ctrl+C, 절전)
            // 매니페스트를 끝에서 한 번만 쓰는 구조는 **전부를 잃는다.**
            // 5초 간격이면 25KB 쓰기가 빌드에 보태는 값은 무시할 만하다.
            if (Date.now() - lastSaved >= 5000) {
                await writeManifest(name, done);
                lastSaved = Date.now();
            }
        }
    }

    Object.assign(next, done);
    await writeManifest(name, next);

    if (opts.orphanScan) await reportOrphans(opts, jobs, log);

    return { ran: dirty.length - failed.length, skipped: jobs.length - dirty.length, failed, rebuiltAll };
}

/** 어떤 작업도 만들지 않는 산출물이 남아 있는지 본다. 지우지 않고 알리기만 한다. */
async function reportOrphans(opts: IncrementalOptions, jobs: Job[], log: BuildLog): Promise<void> {
    const expected = new Set<string>();
    for (const job of jobs) for (const out of job.outputs) expected.add(normalize(out));
    await warnOrphans(opts.orphanScan!, expected, log);
}

/**
 * 만들어질 것으로 예정되지 않은 산출물이 남아 있는지 본다.
 *
 * `runIncremental` 을 쓰지 않는 요소(img)도 같은 검사가 필요해서 따로 뺐다.
 */
export async function warnOrphans(
    { dirs, match }: { dirs: string[]; match: (rel: string) => boolean },
    expected: Set<string>,
    log: BuildLog,
): Promise<void> {
    const orphans: string[] = [];
    for (const dir of dirs) {
        for (const file of await walkFiles(dir)) {
            if (match(file) && !expected.has(file)) orphans.push(file);
        }
    }
    if (orphans.length === 0) return;
    log.warn(`원본이 없는 산출물 ${orphans.length}개가 남아 있다. 필요 없으면 손으로 지워라`);
    for (const o of orphans.slice(0, 20)) log.line(`    ${o}`);
    if (orphans.length > 20) log.line(`    … 외 ${orphans.length - 20}개`);
}
