package io.github.donggi.iroirobrowser.torrent.meta

import io.github.donggi.iroirobrowser.core.SafeFileName
import java.text.Normalizer
import java.util.Locale

/**
 * 토렌트가 준 경로를 이 기기의 상대 경로로. **남이 지은 경로**라 받기 폴더 밖을 가리키거나 다른 파일을 덮지 못하게 한다.
 *
 * 성분마다: UTF-8 로 풀고(깨진 바이트는 U+FFFD) NFC 로 모은 뒤 — `.`·`..`·빈 성분은 버리고, `:core` 의 [SafeFileName] 으로
 * 경로 구분자·제어 문자·방향 제어 문자(`txt.exe` 를 `exe.txt` 로 보이게 하는 것)·FAT 이 받지 않는 글자를 `_` 로 바꾸고 앞뒤의 점·빈칸을 떼고
 * 200 바이트로 줄인다. Windows 의 예약 이름(`CON`·`com1.txt` …)은 뒤에 `_` 를 붙인다(PC 로 옮길 때 깨지지 않게).
 *
 * 그 뒤 파일들 사이의 충돌을 푼다 — **NFC 뒤 대소문자를 무시하고** 같은 경로(안드로이드의 공용 저장소·FAT·Windows 는 대소문자를 가리지
 * 않는다), 파일과 같은 이름의 폴더. 뒤에 나온 것에 ` (1)` 을 붙인다. 심볼릭 링크는 만들지 않고(BEP 47 의 `l`), 패드 파일(`p`)은 쓰지 않는다.
 */
internal object TorrentPaths {

    /** 성분 수 상한 — 정상 토렌트는 이 근처에도 가지 않는다. */
    const val MAX_DEPTH = 64

    /** 상대 경로 하나의 UTF-8 바이트 상한(안드로이드의 PATH_MAX 4096 에서 앱 저장소의 앞부분을 뺀 여유). */
    const val MAX_PATH_BYTES = 2048

    private val WINDOWS_RESERVED = Regex("^(con|prn|aux|nul|com[0-9¹²³]|lpt[0-9¹²³])(\\..*)?$", RegexOption.IGNORE_CASE)

    /** 성분 하나 — 버릴 성분(`.`·`..`·빈 것)이면 null. */
    fun component(raw: ByteArray): String? = component(raw.toString(Charsets.UTF_8))

    fun component(text: String): String? {
        val s = Normalizer.normalize(text, Normalizer.Form.NFC)
        if (s.isBlank() || s == "." || s == "..") return null
        val clean = SafeFileName.clean(s)
        val m = WINDOWS_RESERVED.matchEntire(clean) ?: return clean
        val stem = m.groupValues[1]
        return stem + "_" + clean.substring(stem.length)
    }

    /** 이름(한 파일 토렌트의 파일 이름·여러 파일 토렌트의 폴더 이름). 남는 것이 없으면 [fallback]. */
    fun name(raw: String, fallback: String): String = component(raw) ?: fallback

    /**
     * 파일들의 경로가 서로 겹치지 않게 고친다. [skip] 인 파일(패드·심볼릭 링크 — 디스크에 만들지 않는다)은 그대로 둔다.
     * 같은 원래 폴더는 같은 실제 폴더로 간다(대소문자만 다른 폴더는 한 폴더가 된다 — 대소문자를 가리지 않는 파일 시스템과 같다).
     */
    fun resolveCollisions(paths: List<List<String>>, skip: (Int) -> Boolean): List<List<String>> {
        val taken = HashMap<String, Boolean>() // 열쇠 → 파일인가(거짓이면 폴더)
        val dirMap = HashMap<String, List<String>>() // 원래 폴더 경로의 열쇠 → 실제 폴더 경로
        val out = ArrayList<List<String>>(paths.size)
        for ((index, comps) in paths.withIndex()) {
            if (skip(index) || comps.isEmpty()) {
                out.add(comps)
                continue
            }
            var prefix: List<String> = emptyList()
            for (k in 0 until comps.size - 1) {
                val origKey = key(comps.subList(0, k + 1))
                val mapped = dirMap[origKey]
                if (mapped != null) {
                    prefix = mapped
                    continue
                }
                var name = comps[k]
                var n = 1
                while (taken[key(prefix + name)] == true) name = withSuffix(comps[k], n++, isDir = true)
                taken.putIfAbsent(key(prefix + name), false)
                prefix = prefix + name
                dirMap[origKey] = prefix
            }
            val last = comps[comps.size - 1]
            var name = last
            var n = 1
            while (taken.containsKey(key(prefix + name))) name = withSuffix(last, n++, isDir = false)
            taken[key(prefix + name)] = true
            out.add(prefix + name)
        }
        return out
    }

    private fun key(comps: List<String>): String =
        comps.joinToString("/") { Normalizer.normalize(it, Normalizer.Form.NFC).lowercase(Locale.ROOT) }

    private fun withSuffix(name: String, n: Int, isDir: Boolean): String {
        if (isDir) return "$name ($n)"
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)} ($n)${name.substring(dot)}" else "$name ($n)"
    }

    fun byteLength(comps: List<String>): Int = comps.sumOf { it.toByteArray(Charsets.UTF_8).size } + maxOf(0, comps.size - 1)
}
