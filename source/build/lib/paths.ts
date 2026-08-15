/**
 * 빌드 요소들이 공유하는 경로 규칙.
 *
 * 이 저장소의 빌드는 경로를 문자열로 다루는 곳이 많다('pugs/' -> 'posts/' 치환,
 * 해시 매니페스트의 키, 사이트맵 URL). 구분자나 기준 디렉터리가 곳곳에서 다르면
 * 그 치환들이 조용히 어긋나므로, 경로를 만드는 방법을 여기 한 곳으로 모은다.
 *
 * 규칙은 두 가지다.
 *
 *   1. 파일시스템에 넘기는 경로는 `resolve()` 가 만든 절대 경로다.
 *   2. 사람이 읽거나 파일에 적히는 경로는 `normalize()` 가 만든 **저장소 기준
 *      POSIX 상대 경로**다. 예: `pugs/fundamental/physics.pug`
 *
 * 매니페스트(`source/build/build-<요소>-sha.json`)의 키가 2번 형태다. 그래야 Windows 와
 * POSIX 에서 같은 파일이 같은 키를 갖고, 저장소를 다른 곳에 클론해도 매니페스트가
 * 그대로 쓰인다.
 */
import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 저장소 루트의 절대 경로.
 *
 * cwd 가 아니라 이 파일의 위치에서 구한다. cwd 를 믿으면 저장소 밖에서 부른 순간
 * `./pugs` 같은 상대 경로가 전부 엉뚱한 곳을 가리킨다. 이 파일은
 * `<루트>/source/build/lib/paths.ts` 이므로 세 단계 위가 루트다.
 */
export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');

/**
 * 빌드 요소가 사는 곳. 요소 스크립트와 **그 산출물(로그·해시 기록)이 함께** 여기 있다.
 *
 * 저장소 루트에 `build-*.log` 여섯 개가 흩어져 있으면, 루트를 열 때마다 사이트의 소스보다
 * 빌드 부산물이 먼저 눈에 들어온다. 빌드에 딸린 것은 빌드 폴더에 둔다.
 *
 * 이름에 `build-` 접두사를 남겨 두는 이유는 두 가지다. `npm run build-pug` ↔
 * `build-pug.log` ↔ `build-pug-sha.json` 의 대응이 한눈에 보이고, 정렬했을 때 산출물이
 * 요소 스크립트(`pug.ts` …)와 갈라져 모인다.
 */
export const BUILD_DIR = 'source/build';

/** 요소의 로그 파일 경로(저장소 기준). */
export function logPathOf(name: string): string {
    return `${BUILD_DIR}/build-${name}.log`;
}

/** 요소의 해시 기록 경로(저장소 기준). */
export function manifestPathOf(name: string): string {
    return `${BUILD_DIR}/build-${name}-sha.json`;
}

/**
 * 경로 구분자를 '/' 로 통일한다.
 *
 * Windows 에서 `path.join` 은 '\' 를 쓴다. `path.sep` 이 아니라 두 구분자를 모두
 * 받는 이유는, Windows 에서 '/' 와 '\' 가 섞인 경로가 흔하고 이 함수 자체를 어느
 * 플랫폼에서든 검증할 수 있어야 하기 때문이다. POSIX 파일명에 '\' 가 들어 있으면
 * 망가지지만 이 저장소에는 그런 파일이 없다.
 */
export function toPosix(p: string): string {
    return p.split(/[\\/]/).join('/');
}

/** 어떤 형태의 경로든 저장소 기준 POSIX 상대 경로로 바꾼다. */
export function normalize(p: string): string {
    return toPosix(path.relative(ROOT, path.resolve(ROOT, p)));
}

/** 저장소 기준 상대 경로를 파일시스템에 넘길 절대 경로로 되돌린다. */
export function resolve(rel: string): string {
    return path.resolve(ROOT, rel);
}

/** 파일이 있는가. 디렉터리는 false. */
export async function fileExists(rel: string): Promise<boolean> {
    try {
        return (await fsp.stat(resolve(rel))).isFile();
    } catch {
        return false;
    }
}

/**
 * 디렉터리 아래의 파일을 하위까지 훑어 정규 경로 배열로 준다.
 *
 * 디렉터리가 없으면 빈 배열이다. 아직 만들어지지 않은 산출 폴더(`imgs-generated/`)를
 * 훑는 자리가 있어서, 없는 것을 오류로 보지 않는다.
 */
export async function walkFiles(relDir: string): Promise<string[]> {
    let entries;
    try {
        entries = await fsp.readdir(resolve(relDir), { recursive: true, withFileTypes: true });
    } catch {
        return [];
    }
    return entries
        .filter((e) => e.isFile())
        .map((e) => normalize(path.join(e.parentPath, e.name)))
        .sort();
}

/** 디렉터리 아래의 하위 디렉터리를 정규 경로 배열로 준다. */
export async function walkDirs(relDir: string): Promise<string[]> {
    let entries;
    try {
        entries = await fsp.readdir(resolve(relDir), { recursive: true, withFileTypes: true });
    } catch {
        return [];
    }
    return entries
        .filter((e) => e.isDirectory())
        .map((e) => normalize(path.join(e.parentPath, e.name)))
        .sort();
}

/** 파일을 쓰기 전에 상위 디렉터리를 만든다. */
export async function ensureDirFor(rel: string): Promise<void> {
    await fsp.mkdir(path.dirname(resolve(rel)), { recursive: true });
}
