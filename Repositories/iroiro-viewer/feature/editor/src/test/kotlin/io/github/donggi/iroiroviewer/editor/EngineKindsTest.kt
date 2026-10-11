package io.github.donggi.iroiroviewer.editor

import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 형식이 네 곳에 적혀 있다 — [Kind], [Engine.forKind]·[Engine.saves], `build.gradle.kts` 의 `editorKinds()`(Files 가 읽는 매니페스트의 목록),
 * ONLYOFFICE 어댑터(iroiro-onlyoffice.js)의 SPECS. 하나만 고치면 Files 가 넘긴 문서를 엔진이 모르거나, 엔진이 저장한 형식을 앱이 받지 않는다.
 * 여기서 서로 견준다(어댑터와 gradle 파일은 글로 읽는다).
 */
class EngineKindsTest {

    private val adapter = File("src/main/assets/engines/onlyoffice/iroiro-onlyoffice.js").readText()
    private val gradle = File("build.gradle.kts").readText()

    /** 어댑터의 SPECS — 형식 → 저장할 형식(없으면 같은 형식). */
    private val specs: Map<String, String> by lazy {
        val block = adapter.substringAfter("const SPECS = {").substringBefore("\n  };")
        Regex("""^\s+(\w+): \{ doc: [WCS], base: '(\w+)'(?:, save: '(\w+)')?""", RegexOption.MULTILINE).findAll(block)
            .associate { m -> m.groupValues[1] to m.groupValues[3].ifEmpty { m.groupValues[1] } }
    }

    private val onlyoffice = Kind.entries.filter { Engine.forKind(it) == Engine.ONLYOFFICE }.map { it.id }.toSet()

    @Test
    fun theAdapterKnowsEveryOnlyofficeKind() {
        assertTrue(specs.size > 20, "SPECS 를 읽지 못했다: $specs")
        assertEquals(onlyoffice, specs.keys)
    }

    @Test
    fun theAppTakesWhatTheAdapterSaves() {
        for ((kind, save) in specs) {
            assertTrue(Kind.of(save) in Engine.ONLYOFFICE.saves, "$kind 를 $save 로 저장하는데 앱이 받지 않는다")
            // 같은 형식으로 저장하지 않는 형식(이전 형식·PDF)은 앱도 그 형식의 저장을 받지 않는다.
            if (save != kind) assertFalse(Kind.of(kind) in Engine.ONLYOFFICE.saves, "$kind 는 같은 형식으로 저장하지 않는다")
        }
        assertEquals(Engine.ONLYOFFICE.saves.map { it.id }.toSet(), specs.values.toSet())
    }

    @Test
    fun theManifestListsTheSameKinds() {
        val block = gradle.substringAfter("onlyoffice/web-apps/apps/api/documents/api.js").substringAfter("kinds += listOf(").substringBefore(")")
        val listed = Regex(""""(\w+)"""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(onlyoffice, listed)
        val rhwp = gradle.substringAfter("rhwp/index.html").substringAfter("kinds += listOf(").substringBefore(")")
        assertEquals(setOf("hwp", "hwpx"), Regex(""""(\w+)"""").findAll(rhwp).map { it.groupValues[1] }.toSet())
    }

    @Test
    fun kindIdsAreTheirExtensions() {
        for (k in Kind.entries) if (k != Kind.PROBE) assertEquals(k.id, k.ext)
        // MIME 은 소문자다 — 안드로이드의 MIME 대조는 대소문자를 가린다.
        for (k in Kind.entries) assertEquals(k.mime.lowercase(), k.mime)
    }
}
