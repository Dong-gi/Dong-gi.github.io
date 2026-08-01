/**
 * Spring 예제 프로젝트를 Jakarta EE 네임스페이스와 현행 Spring 으로 이관한다.
 *
 * Spring Boot 2.x 는 2023-11 에 OSS 지원이 끝났고 3.x 도 2026-06-30 에 끝났다.
 * Spring 6 / Boot 3 부터 javax.* 가 jakarta.* 로 바뀌었으므로 소스까지 함께 고쳐야 한다.
 *
 * 중요: javax 로 시작한다고 다 Jakarta EE 가 아니다. 아래 패키지들은 JDK 소속이라
 * 절대 변환하면 안 된다.
 *   javax.annotation.processing  java.compiler 모듈
 *   javax.lang.model             java.compiler 모듈
 *   javax.tools                  java.compiler 모듈
 *   javax.sql                    java.sql 모듈 (JDBC DataSource)
 *   javax.crypto                 java.base 모듈
 *   javax.naming                 java.naming 모듈
 *   javax.net / javax.xml / javax.imageio / javax.swing 등
 *
 * 따라서 "javax -> jakarta 전체 치환" 이 아니라 허용 목록 방식으로 처리한다.
 */

import fs from 'node:fs';

const ROOT = process.argv[2] ?? '.';
const DRY_RUN = process.argv.includes('--dry-run');

/** Jakarta EE 로 이름이 바뀐 패키지만 변환한다. */
const JAKARTA_PACKAGES = [
    'javax.servlet',
    'javax.persistence',
    'javax.validation',
    'javax.websocket',
    'javax.transaction',
    'javax.enterprise',
    'javax.inject',
    'javax.el',
    'javax.mail',
    'javax.ws.rs',
];
/** javax.annotation 은 Jakarta 로 간 것과 JDK 에 남은 것이 섞여 있다. */
const ANNOTATION_KEEP = /javax\.annotation\.(processing|Generated)/;

/**
 * 빌드 파일의 좌표 변경.
 *
 * 주의: 이 단계는 위의 패키지 변환 뒤에 실행되므로 그룹 ID 는 이미 jakarta.* 로
 * 바뀐 상태다. 아티팩트 ID 쪽에 남은 javax 를 기준으로 매칭해야 한다.
 * (groupId:artifactId 를 한 번에 매칭하려 하면 앞부분이 이미 바뀌어 있어 빗나간다)
 */
const COORDS = [
    [/jakarta\.servlet:javax\.servlet-api:[\d.]+/g, 'jakarta.servlet:jakarta.servlet-api:6.1.0'],
    [/jakarta\.servlet\.jsp:javax\.servlet\.jsp-api:[\d.]+/g, 'jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0'],
    [/jakarta\.websocket:javax\.websocket-api:[\d.]+/g, 'jakarta.websocket:jakarta.websocket-api:2.2.0'],
    [/jakarta\.servlet:jstl:[\d.]+/g, 'jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.2'],
    [/jakarta\.validation:validation-api:[\d.]+/g, 'jakarta.validation:jakarta.validation-api:3.1.0'],
    [/jakarta\.annotation:javax\.annotation-api:[\d.]+/g, 'jakarta.annotation:jakarta.annotation-api:3.0.0'],
    // Spring Framework 5.x -> 7.x
    [/(org\.springframework:spring-[\w-]+:)5\.[\d.]+(\.RELEASE)?/g, '$17.0.0'],
    // Spring Boot 2.x -> 4.0.0
    [/(org\.springframework\.boot['"]? version ['"])2\.[\d.]+(\.RELEASE)?/g, '$14.0.0'],
    [/(<artifactId>spring-boot-starter-parent<\/artifactId>\s*\n\s*<version>)2\.[\d.]+(\.RELEASE)?/g, '$14.0.0'],
    [/(io\.spring\.dependency-management['"]? version ['"])[\d.]+/g, '$11.1.7'],
];

/** Java 버전 상향. Spring Boot 4 는 Java 17 이상을 요구한다. */
const JAVA = [
    [/sourceCompatibility\s*=\s*(JavaVersion\.VERSION_[\w]+|'[\d.]+'|"[\d.]+"|[\d.]+)/g, 'sourceCompatibility = JavaVersion.VERSION_25'],
    [/targetCompatibility\s*=\s*(JavaVersion\.VERSION_[\w]+|'[\d.]+'|"[\d.]+"|[\d.]+)/g, 'targetCompatibility = JavaVersion.VERSION_25'],
    [/<java\.version>[\d.]+<\/java\.version>/g, '<java.version>25</java.version>'],
];

const files = fs
    .readFileSync('/tmp/tracked.txt', 'utf8')
    .trim()
    .split('\n')
    .filter((f) => /^Repositories\/(STS|Eclipse)\//.test(f))
    .filter((f) => /\.(java|xml|gradle|jsp|properties)$/.test(f))
    .filter((f) => fs.existsSync(f));

const stats = { javax: 0, coords: 0, java: 0, wrapper: 0 };
const touched = new Set();

for (const f of files) {
    const before = fs.readFileSync(f, 'utf8');
    const eol = before.includes('\r\n') ? '\r\n' : '\n';
    let s = before.split(/\r?\n/).join('\n');
    const orig = s;

    // 1. Jakarta EE 패키지만 골라 변환
    for (const p of JAKARTA_PACKAGES) {
        s = s.replace(new RegExp(p.replace(/\./g, '\\.') + '(?=[.\\s;:"\'<>)])', 'g'), p.replace('javax.', 'jakarta.'));
    }
    // javax.annotation 은 JDK 에 남은 것을 제외하고 변환
    s = s.replace(/javax\.annotation\.(\w+)/g, (m, sub) => (ANNOTATION_KEEP.test(m) ? m : `jakarta.annotation.${sub}`));

    if (s !== orig) stats.javax += 1;
    const afterJavax = s;

    // 2. 빌드 파일 좌표
    if (/\.(gradle|xml)$/.test(f)) {
        for (const [re, to] of COORDS) s = s.replace(re, to);
        if (s !== afterJavax) stats.coords += 1;
    }
    const afterCoords = s;

    // 3. Java 버전
    if (/build\.gradle$|pom\.xml$/.test(f)) {
        for (const [re, to] of JAVA) s = s.replace(re, to);
        if (s !== afterCoords) stats.java += 1;
    }

    if (s !== orig) {
        touched.add(f);
        if (!DRY_RUN) fs.writeFileSync(f, s.split('\n').join(eol));
    }
}

// 4. Gradle wrapper
for (const f of fs
    .readFileSync('/tmp/tracked.txt', 'utf8')
    .trim()
    .split('\n')
    .filter((f) => /^Repositories\/STS\/.*gradle-wrapper\.properties$/.test(f) && fs.existsSync(f))) {
    const b = fs.readFileSync(f, 'utf8');
    const a = b.replace(/gradle-[\d.]+-(bin|all)\.zip/, 'gradle-9.3.1-$1.zip');
    if (a !== b) {
        stats.wrapper += 1;
        touched.add(f);
        if (!DRY_RUN) fs.writeFileSync(f, a);
    }
}

console.log(`javax -> jakarta   ${stats.javax}개 파일`);
console.log(`빌드 좌표          ${stats.coords}개 파일`);
console.log(`Java 버전          ${stats.java}개 파일`);
console.log(`Gradle wrapper     ${stats.wrapper}개 파일`);
console.log(`총 변경            ${touched.size}개 파일${DRY_RUN ? ' (dry-run)' : ''}`);
