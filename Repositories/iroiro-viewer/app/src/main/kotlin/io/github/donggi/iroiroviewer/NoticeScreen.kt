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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * 있다. 그리고 한컴 문서 명세의 사용 조건이 요구하는 문장(13단계가 넘긴 의무)을 맨 위에 둔다.
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
        item(key = "specs") {
            Text(
                stringResource(R.string.notice_specs),
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
    }
}
