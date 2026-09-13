package io.github.donggi.iroiroviewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 오픈소스 고지.
 *
 * ## 왜 8단계에서 만드는가
 *
 * 마감(14단계)에 넣기로 했던 화면이지만, **RAR 읽기가 이 단계에서 제품이 된다.**
 * junrar 가 승계한 UnRAR 라이선스는 배포할 때 고지를 요구하고, 특히 "이 코드는 RAR
 * 호환 압축기를 만드는 데 쓸 수 없다" 는 사실을 문서와 소스 주석에 밝히라고 못 박는다.
 * 기능을 먼저 내고 고지를 나중에 붙이는 것은 순서가 거꾸로다.
 *
 * ## 전문을 어디서 가져왔는가
 *
 * **지어내지 않았다.** Apache-2.0 과 NOTICE 는 `commons-compress` jar 의
 * `META-INF/` 에서 그대로 꺼냈고(그 배포물이 담고 있는 정본이다), UnRAR 라이선스는
 * junrar 저장소의 `LICENSE`, XZ 는 xz-java 저장소의 `COPYING` 에서 내려받았다.
 * 네 파일 모두 `res/raw/` 에 바이트 그대로 들어 있다.
 *
 * 라이선스 전문은 공개해도 되는 글이므로 이 저장소가 통째로 웹에 올라가는 것과
 * 부딪치지 않는다 — 오히려 그것이 라이선스가 요구하는 바다.
 *
 * (여기 적힌 것은 법률 자문이 아니다.)
 */
@Composable
fun NoticeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sections = remember {
        listOf(
            Section(
                title = "Apache Commons Compress",
                subtitle = "Apache License 2.0 · ZIP·7z 읽기",
                bodyRes = R.raw.notice_commons_compress,
            ),
            Section(
                title = "XZ for Java",
                subtitle = "0BSD · 7z 의 LZMA·LZMA2 해제",
                bodyRes = R.raw.notice_xz,
            ),
            Section(
                title = "junrar",
                subtitle = "UnRAR License · RAR 읽기",
                bodyRes = R.raw.notice_unrar,
            ),
            Section(
                title = "Apache License 2.0 전문",
                subtitle = "Commons Compress·AndroidX·Kotlin 이 이 라이선스다",
                bodyRes = R.raw.notice_apache2,
            ),
        ).map { it to context.resources.openRawResource(it.bodyRes).bufferedReader().use { r -> r.readText() } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.notice_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.notice_unrar_restriction),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        for ((section, body) in sections) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(
                section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = body,
                // 라이선스 전문은 **줄바꿈이 뜻을 가진다.** 고정폭으로 그려 원문 모양을 지킨다.
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
        }
    }
}

private data class Section(val title: String, val subtitle: String, val bodyRes: Int)
