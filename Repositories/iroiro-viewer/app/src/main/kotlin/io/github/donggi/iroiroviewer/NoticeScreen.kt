package io.github.donggi.iroiroviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.res.AssetManager
import java.io.IOException

/**
 * 오픈소스 고지.
 *
 * ## 왜 8단계에서 만들었는가
 *
 * 마감(14단계)에 넣기로 했던 화면이지만, **RAR 읽기가 8단계에서 제품이 됐다.**
 * junrar 가 승계한 UnRAR 라이선스는 배포할 때 고지를 요구하고, 특히 "이 코드는 RAR
 * 호환 압축기를 만드는 데 쓸 수 없다" 는 사실을 문서와 소스 주석에 밝히라고 못 박는다.
 * 기능을 먼저 내고 고지를 나중에 붙이는 것은 순서가 거꾸로다.
 *
 * ## 14단계가 채운 것
 *
 * 릴리스 APK 에 실리는 서드파티 **전부**를 적는다 — 무엇이 어느 라이선스인지, 어디서 확인했는지는 [NoticeCatalog] 에
 * 있다. 그리고 한컴 문서 명세의 사용 조건이 요구하는 문장(편집기에 실린 rhwp 가 그 명세를 참고했다)을 맨
 * 위에 둔다.
 *
 * ## 전문을 어디서 가져왔는가
 *
 * **지어내지 않았다.** Apache-2.0 과 Commons 넷의 NOTICE 는 각 jar 의 `META-INF/` 에서, SLF4J 는 그 jar 의
 * `META-INF/LICENSE.txt` 에서, Protocol Buffers 는 DataStore 가 옮겨 담은 jar 의 `LICENSE.txt` 에서 그대로 꺼냈다
 * (14단계는 전부 이 기계의 Gradle 캐시에서 — 내려받지 않았다). UnRAR 라이선스는 junrar 저장소의 `LICENSE`, XZ 는
 * xz-java 저장소의 `COPYING` 에서 8단계가 내려받았다. 모두 `res/raw/` 에 바이트 그대로 들어 있다.
 *
 * 라이선스 전문은 공개해도 되는 글이므로 이 저장소가 통째로 웹에 올라가는 것과
 * 부딪치지 않는다 — 오히려 그것이 라이선스가 요구하는 바다.
 *
 * ## 편집기 엔진
 *
 * 편집기(`feature:editor`)가 묶은 웹 편집기 둘(rhwp — MIT, ONLYOFFICE 편집기와 x2t — GNU AGPL 3.0)과 그 글꼴(OFL·Apache·GUST)은 Maven
 * 좌표가 아니라 엔진 묶음(`vendor/editor` → APK 의 `assets/engines/`)으로 실린다. 묶음의 도구(tools/editor)가 각 엔진의 라이선스 글을
 * `assets/engines/<엔진>/licenses/` 에 두므로 **그 글을 그대로** 펼쳐 보인다(누를 때 읽는다 — AGPL 전문만 34 KB 다). 묶음 없이 지은 빌드는
 * 그 절이 없고, 맨 위에서 두 엔진과 그 절을 말하는 문장(`notice_specs`)도 없다. ONLYOFFICE 편집기 안의 정보(i) 칸도 같은 글을 보인다.
 *
 * **제목 막대는 `app` 의 Root 가 씌운다**(뒤로 가는 곳이 연 자리마다 달라서 — 첫 화면·설정·압축 목록).
 *
 * (여기 적힌 것은 법률 자문이 아니다.)
 */
@Composable
fun NoticeScreen(modifier: Modifier = Modifier) {
    // `LocalContext.current.resources` 는 구성 변경을 따라가지 않는다(lint `LocalContextResourcesRead`).
    val resources = LocalResources.current
    // 전문 열 벌(약 17 KB)을 읽는 일이라 주 스레드에서 하지 않는다. 읽는 동안에는 제목과 목록만 먼저 보인다.
    val bodies by produceState<Map<Int, String>>(initialValue = emptyMap(), resources) {
        value = withContext(Dispatchers.IO) {
            NoticeCatalog.sections.mapNotNull { it.body }.associateWith { id ->
                resources.openRawResource(id).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
    }

    // 편집기 엔진의 라이선스 글 — 엔진 묶음이 든 빌드에만 있다(위 '편집기 엔진').
    val assets = LocalContext.current.assets
    val engineLicenses by produceState<List<String>>(initialValue = emptyList(), assets) {
        value = withContext(Dispatchers.IO) { engineLicenseFiles(assets) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "hancom") {
            Text(
                stringResource(R.string.notice_hancom),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // 한글·오피스 문서를 여는 엔진과 '아래 편집기 엔진 절' 을 말하는 문장 — 그 절이 있는 빌드(엔진 묶음이 든 것)에만.
        if (engineLicenses.isNotEmpty()) {
            item(key = "specs") {
                Text(
                    stringResource(R.string.notice_specs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item(key = "trademarks") {
            Text(
                stringResource(R.string.notice_trademarks),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item(key = "unrar") {
            Text(
                stringResource(R.string.notice_unrar_restriction),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
        }
        items(NoticeCatalog.sections, key = { it.title }) { section ->
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(section.subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                section.components?.let { res ->
                    Text(stringResource(res), style = MaterialTheme.typography.bodySmall)
                }
                section.body?.let { id -> bodies[id] }?.let { body ->
                    Text(
                        text = body,
                        // 라이선스 전문은 **줄바꿈이 뜻을 가진다.** 고정폭으로 그려 원문 모양을 지킨다.
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }
        }
        if (engineLicenses.isNotEmpty()) {
            item(key = "editor") {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notice_editor_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.notice_editor_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(stringResource(R.string.notice_editor_components), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(engineLicenses, key = { it }) { path -> EngineLicense(assets, path) }
        }
    }
}

/** 엔진 묶음이 든 라이선스 글의 자산 자리(`engines/<엔진>/licenses/<파일>`). 묶음이 없으면 빈 것. */
private fun engineLicenseFiles(assets: AssetManager): List<String> = ENGINES.flatMap { engine ->
    val dir = "engines/$engine/licenses"
    try {
        assets.list(dir).orEmpty().sorted().map { "$dir/$it" }
    } catch (e: IOException) {
        emptyList()
    }
}

/** 라이선스 글 하나 — 이름을 누르면 펼친다(그때 읽는다). 줄바꿈이 뜻을 가지므로 고정폭으로 원문 그대로. */
@Composable
private fun EngineLicense(assets: AssetManager, path: String) {
    var open by rememberSaveable(path) { mutableStateOf(false) }
    val text by produceState<String?>(initialValue = null, path, open) {
        value = if (!open) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (e: IOException) {
                    null
                }
            }
        }
    }
    val engine = path.removePrefix("engines/").substringBefore('/')
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }) {
            Text(stringResource(R.string.notice_editor_file, engine, path.substringAfterLast('/')))
        }
        text?.let { body ->
            Text(
                text = body,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}

/** 라이선스 글을 싣는 엔진(묶음의 폴더 이름). */
private val ENGINES = listOf("rhwp", "onlyoffice")
