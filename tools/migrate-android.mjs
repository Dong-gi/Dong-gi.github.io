/**
 * Android 예제 프로젝트를 AGP 9 / compileSdk 36 으로 이관한다.
 *
 * Google Play 가 2026-08-31 부터 targetSdk 36 을 요구한다.
 *
 * 적용하는 변경
 *   settings.gradle       pluginManagement / dependencyResolutionManagement 블록 추가
 *   build.gradle (root)   buildscript + classpath -> plugins DSL, jcenter 제거,
 *                         clean 태스크를 tasks.register + layout.buildDirectory 로
 *   app/build.gradle      apply plugin -> plugins DSL, namespace 추가,
 *                         compileSdkVersion/minSdkVersion/targetSdkVersion ->
 *                         compileSdk/minSdk/targetSdk, 36 으로 상향,
 *                         compileOptions 를 Java 17 로, 의존성 버전 상향
 *   AndroidManifest.xml   package 속성 제거 (AGP 8 부터 namespace 로 대체)
 *   gradle-wrapper        9.3.1
 *
 * 이 스크립트는 빌드로 검증되지 않았다. docs/modernization-log.md 의
 * 검증 체크리스트를 참조할 것.
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.argv[2] ?? '.';
const DRY_RUN = process.argv.includes('--dry-run');

const AGP = '9.0.0';
const GRADLE = '9.3.1';
const COMPILE_SDK = 36;
const TARGET_SDK = 36;
/** androidx 1.7 계열이 minSdk 21 을 요구한다. Android 5.0 미만은 지원 대상에서 빠진다. */
const MIN_SDK = 21;

/** 상향할 의존성. 좌변이 group:artifact, 우변이 목표 버전. */
const DEPS = {
    'androidx.appcompat:appcompat': '1.7.0',
    'androidx.constraintlayout:constraintlayout': '2.2.1',
    'com.google.android.material:material': '1.12.0',
    'androidx.test.ext:junit': '1.2.1',
    'androidx.test.espresso:espresso-core': '3.6.1',
    'junit:junit': '4.13.2',
    'org.projectlombok:lombok': '1.18.34',
};
/** 변수로 선언된 버전. 예: def room_version = "2.4.1" */
const VAR_DEPS = { room_version: '2.6.1' };

const ANDROID_DIR = path.join(ROOT, 'Repositories/Android');
const projects = fs
    .readdirSync(ANDROID_DIR, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name)
    .sort();

/** 파일을 읽어 변환 함수를 적용하고, 바뀐 경우에만 기록한다. */
function edit(file, fn) {
    if (!fs.existsSync(file)) return null;
    const before = fs.readFileSync(file, 'utf8');
    const eol = before.includes('\r\n') ? '\r\n' : '\n';
    const after = fn(before.split(/\r?\n/).join('\n'), eol).split('\n').join(eol);
    if (after === before) return false;
    if (!DRY_RUN) fs.writeFileSync(file, after);
    return true;
}

const changes = [];
const record = (p, what, done) => done !== null && changes.push(`${done ? '  ✓' : '  ·'} ${p} ${what}`);

for (const proj of projects) {
    const dir = path.join(ANDROID_DIR, proj);
    const appGradle = path.join(dir, 'app/build.gradle');
    if (!fs.existsSync(appGradle)) continue;

    const manifest = path.join(dir, 'app/src/main/AndroidManifest.xml');
    const pkg = fs.readFileSync(manifest, 'utf8').match(/package="([^"]+)"/)?.[1];
    if (!pkg) {
        changes.push(`  ⚠ ${proj} — AndroidManifest 에서 package 를 찾지 못해 건너뜀`);
        continue;
    }

    // ---- settings.gradle : 저장소 선언을 중앙화한다
    record(
        proj,
        'settings.gradle',
        edit(path.join(dir, 'settings.gradle'), (t) => {
            if (t.includes('pluginManagement')) return t;
            return (
                `pluginManagement {\n` +
                `    repositories {\n        google()\n        mavenCentral()\n        gradlePluginPortal()\n    }\n}\n\n` +
                `dependencyResolutionManagement {\n` +
                `    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n` +
                `    repositories {\n        google()\n        mavenCentral()\n    }\n}\n\n` +
                t.trimStart()
            );
        }),
    );

    // ---- 루트 build.gradle : plugins DSL 로 교체
    record(
        proj,
        'build.gradle (root)',
        edit(path.join(dir, 'build.gradle'), (t) => {
            if (t.includes('plugins {')) return t;
            return (
                `// Top-level build file where you can add configuration options common to all sub-projects/modules.\n\n` +
                `plugins {\n    id 'com.android.application' version '${AGP}' apply false\n}\n\n` +
                `tasks.register('clean', Delete) {\n    delete rootProject.layout.buildDirectory\n}\n`
            );
        }),
    );

    // ---- app/build.gradle
    record(
        proj,
        'app/build.gradle',
        edit(appGradle, (t) => {
            let s = t;
            s = s.replace(/^apply plugin: 'com\.android\.application'\n/m, `plugins {\n    id 'com.android.application'\n}\n`);
            // namespace 는 android 블록 첫 줄에
            if (!/\n\s*namespace\s/.test(s)) s = s.replace(/\nandroid \{\n/, `\nandroid {\n    namespace '${pkg}'\n`);
            s = s.replace(/compileSdkVersion\s+\d+/, `compileSdk ${COMPILE_SDK}`);
            s = s.replace(/minSdkVersion\s+\d+/, `minSdk ${MIN_SDK}`);
            s = s.replace(/targetSdkVersion\s+\d+/, `targetSdk ${TARGET_SDK}`);
            // Java 17 : AGP 8 이상이 요구하는 최소치
            s = s.replace(/sourceCompatibility\s*=?\s*(1\.8|JavaVersion\.\w+)/g, 'sourceCompatibility JavaVersion.VERSION_17');
            s = s.replace(/targetCompatibility\s*=?\s*(1\.8|JavaVersion\.\w+)/g, 'targetCompatibility JavaVersion.VERSION_17');
            if (!s.includes('compileOptions')) {
                s = s.replace(
                    /\n\}\n\ndependencies \{/,
                    `\n    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }\n}\n\ndependencies {`,
                );
            }
            for (const [ga, v] of Object.entries(DEPS)) {
                s = s.replace(new RegExp(`${ga.replace('.', '\\.')}:[\\d.]+`, 'g'), `${ga}:${v}`);
            }
            for (const [name, v] of Object.entries(VAR_DEPS)) {
                s = s.replace(new RegExp(`(def ${name}\\s*=\\s*")[^"]+(")`), `$1${v}$2`);
            }
            return s;
        }),
    );

    // ---- AndroidManifest : package 속성 제거
    record(
        proj,
        'AndroidManifest.xml',
        edit(manifest, (t) => t.replace(/\n\s*package="[^"]+"/, '')),
    );

    // ---- wrapper
    record(
        proj,
        'gradle-wrapper.properties',
        edit(path.join(dir, 'gradle/wrapper/gradle-wrapper.properties'), (t) =>
            t.replace(/gradle-[\d.]+-(bin|all)\.zip/, `gradle-${GRADLE}-$1.zip`),
        ),
    );
}

console.log(changes.join('\n'));
console.log(`\n프로젝트 ${projects.length}개 처리${DRY_RUN ? ' (dry-run)' : ''}`);
