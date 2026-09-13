package io.github.donggi.iroiroviewer.safety

import java.io.File

/**
 * 아카이브 엔트리 이름을 파일시스템 경로로 바꾸기 전에 통과해야 하는 관문.
 *
 * 엔트리 이름은 **아카이브를 만든 쪽이 적은 문자열**이다. `../../../etc/passwd`,
 * `C:\Windows\...`, `/data/data/다른앱/...` 같은 것이 그대로 들어온다. 이 앱은 모든
 * 파일 접근 권한을 가지고 돌기 때문에 보통 앱이라면 샌드박스가 막아 줄 것을 우리가 막아야 한다.
 *
 * **문자열 `startsWith` 만으로 가두는 것은 실제로 뚫린다.** junrar 의 2026년 CVE 가 정확히
 * 그 사례였다 — 목적지가 `/out` 일 때 `/outx/...` 가 접두어 검사를 통과했다. 그래서
 * 정규화한 뒤 canonical 경로를 구하고 **구분자까지 붙여** 비교한다.
 */
object ArchivePath {

    /**
     * 엔트리 이름을 상대 경로로 정규화한다. 쓸 수 없는 이름이면 null.
     *
     * - 백슬래시를 슬래시로 바꾼다(윈도우에서 만든 아카이브)
     * - 드라이브 문자(`C:`)와 선행 슬래시를 떼어 절대경로를 상대경로로 만든다
     * - `.` 은 버리고 `..` 은 **거부한다**(위로 올라가려는 의도가 정상일 수 없다)
     * - 제어문자가 든 이름은 거부한다
     */
    fun sanitize(rawName: String): String? {
        if (rawName.isEmpty()) return null
        if (rawName.any { it.code < 0x20 || it.code == 0x7F }) return null

        var s = rawName.replace('\\', '/')
        // "C:/..." 또는 "C:..." 의 드라이브 문자를 뗀다
        if (s.length >= 2 && s[1] == ':' && s[0].isLetter()) s = s.substring(2)
        s = s.trimStart('/')

        val parts = s.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        // 끝의 점과 공백은 파일시스템마다 다르게 다뤄져 의도치 않은 덮어쓰기를 만든다.
        // (윈도우 예약 이름 CON·NUL 따위는 검사하지 않는다 — 안드로이드에는 그 개념이
        // 없고, 있지도 않은 검사를 주석으로 약속하면 다음 사람이 속는다.)
        if (parts.any { it.endsWith('.') || it.endsWith(' ') }) return null
        return parts.joinToString("/")
    }

    /**
     * [destDir] 안에 엔트리를 풀 때 쓸 실제 파일. 밖으로 나가면 null.
     *
     * canonical 경로로 비교하는 것은 심볼릭 링크를 통한 우회를 막기 위해서다 —
     * `destDir` 안에 이미 바깥을 가리키는 링크가 있으면 문자열 검사는 통과한다.
     */
    fun resolveInside(destDir: File, rawName: String): File? {
        val rel = sanitize(rawName) ?: return null
        val root = destDir.canonicalFile
        val target = File(root, rel).canonicalFile
        val rootPath = root.path
        val targetPath = target.path
        if (targetPath == rootPath) return null
        // 구분자를 붙여 비교한다. 이것이 없으면 /out 과 /outx 를 구분하지 못한다.
        if (!targetPath.startsWith(rootPath + File.separator)) return null
        return target
    }
}
