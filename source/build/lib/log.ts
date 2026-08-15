/**
 * 빌드 요소 하나의 로그.
 *
 * 요소마다 `source/build/build-<이름>.log` 를 하나씩 쓴다. 요소 스크립트 바로 옆이다. 예전에는 모든 요소가 한 프로세스에서
 * 섞여 돌아 로그 한 줄이 어디서 나온 것인지 알 수 없었다. 지금은 요소가 곧 프로세스이고
 * 프로세스가 곧 로그 파일이므로, 파일 이름만 보면 출처가 정해진다.
 *
 * 콘솔과 파일에 **동시에** 쓴다. 셸 리다이렉션(`> build-pug.log 2>&1`)을 쓰지 않는
 * 이유는 두 가지다. 하나는 손으로 `npm run build-pug` 를 돌렸을 때 화면이 비어 있으면
 * 곤란해서고, 다른 하나는 `2>&1` 의 동작이 셸마다 달라(Windows PowerShell 5.1) 이식성이
 * 없기 때문이다.
 *
 * 예외가 하나 있다. `npm run build` 로 불릴 때는 오케스트레이터가 `BLOG_BUILD_QUIET`
 * 를 켜고, 그러면 콘솔에는 쓰지 않는다. 여섯 요소의 출력이 다시 뒤섞이면 요소를
 * 나눈 뜻이 없다. 콘솔에는 요소별 성패 한 줄씩만 남는다.
 *
 * 파일 쓰기는 동기다. 요소가 중간에 죽어도 그때까지의 로그가 파일에 남아야 원인을
 * 찾을 수 있다. 줄 수가 수천 단위라 동기 쓰기의 비용은 무시할 수 있다.
 */
import fs from 'node:fs';
import { logPathOf, resolve } from './paths.ts';

/** 요약 줄의 접두사. 오케스트레이터가 이 접두사로 요약을 뽑아 콘솔에 한 줄로 보고한다. */
export const SUMMARY_PREFIX = '[요약] ';

/** 켜져 있으면 로그를 파일에만 쓴다. 오케스트레이터가 자식에게 넘긴다. */
export const QUIET_ENV = 'BLOG_BUILD_QUIET';

/**
 * 외부 도구가 낸 진단. 스택 추적을 붙이지 않는다.
 *
 * `d2` 가 "3행에서 닫는 괄호가 없다" 고 알려 줄 때, 그 뒤에 우리 코드의 호출 스택을
 * 열 줄 붙여 봐야 고칠 곳을 찾는 데 도움이 되지 않는다. 오히려 실패가 여럿이면
 * 로그가 스택으로 뒤덮여 정작 진단이 안 보인다.
 */
export class ToolError extends Error {}

export class BuildLog {
    readonly name: string;
    readonly file: string;
    private readonly fd: number;
    private readonly startedAt = Date.now();
    private warnCount = 0;
    private errorCount = 0;

    constructor(name: string) {
        this.name = name;
        this.file = resolve(logPathOf(name));
        // 'w' — 매 실행마다 새로 쓴다. 이어 붙이면 어느 실행의 로그인지 알 수 없다.
        this.fd = fs.openSync(this.file, 'w');
        this.line(`# build-${name}  ${new Date().toISOString()}`);
    }

    /** 한 줄 기록. */
    line(message: string): void {
        const text = message + '\n';
        fs.writeSync(this.fd, text);
        if (process.env[QUIET_ENV] !== '1') process.stdout.write(text);
    }

    /** 빌드를 멈추지는 않지만 사람이 봐야 하는 것. */
    warn(message: string): void {
        this.warnCount += 1;
        this.line(`경고: ${message}`);
    }

    /** 실패한 작업. 요소 전체의 성패는 호출자가 판단한다. */
    error(message: string, cause?: unknown): void {
        this.errorCount += 1;
        this.line(`오류: ${message}`);
        if (cause === undefined) return;
        const detail =
            cause instanceof ToolError ? cause.message
                : cause instanceof Error ? (cause.stack ?? cause.message)
                    : String(cause);
        this.line(detail.replace(/^/gm, '    '));
    }

    get warns(): number {
        return this.warnCount;
    }

    get errors(): number {
        return this.errorCount;
    }

    get elapsedMs(): number {
        return Date.now() - this.startedAt;
    }

    /** 마지막 한 줄. 오케스트레이터가 콘솔에 그대로 옮긴다. */
    summary(text: string): void {
        this.line(SUMMARY_PREFIX + text);
    }

    close(): void {
        fs.closeSync(this.fd);
    }
}

/**
 * 요소 스크립트의 공통 껍데기.
 *
 * 로그를 열고, 본문을 돌리고, 요약 한 줄과 종료 코드를 남긴다. 성패를 종료 코드로
 * 표현하는 이유는 오케스트레이터가 자식 프로세스로 요소를 돌리기 때문이다. 화면에
 * 무엇이 찍혔는지를 파싱해 성패를 판단하는 대신, 프로세스의 계약을 그대로 쓴다.
 *
 * 본문은 요약 문구를 돌려준다. 던지면 실패다.
 */
export async function runComponent(name: string, body: (log: BuildLog) => Promise<string>): Promise<void> {
    const log = new BuildLog(name);
    try {
        const text = await body(log);
        log.summary(text);
        process.exitCode = 0;
    } catch (e) {
        log.error(`${name} 빌드가 중단됐다`, e);
        log.summary('실패');
        process.exitCode = 1;
    } finally {
        // 걸린 시간은 요약 뒤에 따로 적는다. 요약 문구 자체는 오케스트레이터가 콘솔에
        // 옮겨 적는데, 거기에는 오케스트레이터가 잰 시간이 이미 붙기 때문이다.
        log.line(`# 끝 — ${(log.elapsedMs / 1000).toFixed(1)}초, 경고 ${log.warns}개, 오류 ${log.errors}개`);
        log.close();
    }
}
