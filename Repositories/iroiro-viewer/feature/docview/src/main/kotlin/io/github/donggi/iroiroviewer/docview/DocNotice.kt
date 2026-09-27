package io.github.donggi.iroiroviewer.docview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.donggi.iroiroviewer.format.FlowWarnings
import io.github.donggi.iroiroviewer.format.ParseWarning
import io.github.donggi.iroiroviewer.format.epub.EpubOpener

/**
 * **무엇을 버렸는지 말한다.**
 *
 * `UnsupportedFeatures` 의 주석이 2단계부터 적어 둔 계약이다 — "사용자는 원문에 무엇이
 * 있었는지 모르므로, 빠진 것이 있다는 사실 자체를 앱이 말해 주어야 한다". 11단계가 그
 * 계약의 첫 소비자다.
 *
 * ## 숫자를 세는 쪽과 문장을 만드는 쪽을 가른다
 *
 * 세는 것은 `format:*`(순수 JVM)이 하고 문장은 여기서 만든다. 순수 JVM 모듈은 화면
 * 문구를 만들지 않는다는 저장소 규칙 그대로다 — `UnsupportedFeatures` 의 상수가
 * 한국어인 것은 그것이 **종류의 이름**이지 문장이 아니기 때문이다.
 *
 * ## PDF 에서는 이 화면이 뜨지 않는다
 *
 * pdfium 이 **무엇을 못 그렸는지 알려 주지 않기** 때문이다(`PdfDocument` 의 주석).
 * 0 을 보여 주면 그 0 이 거짓말을 한다.
 */
@Composable
fun DocNoticeList(
    warnings: List<ParseWarning>,
    dropped: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_notice_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.doc_notice_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        for ((kind, count) in dropped) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(kind, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.doc_notice_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // **같은 문장은 한 번만.** 보조 부분 둘(스타일·공유 문자열)이 깨지면 코드는 둘이지만 문장은 같다.
        // `ParseWarning.detail` 은 예외 메시지가 아니라 파서가 만든 값이라 경로가 없다(`OpenFailure.detail`
        // 과 다르다). 다만 흐름 문서에서는 **문서의 이름**(시트 이름·슬라이드 제목)이 들어온다 — 변환기가
        // 제어 문자·방향 바꾸기 문자를 걷고 길이를 자른 값이다.
        for (line in warnings.map { warningText(it) }.distinct()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 경고 하나의 문장.
 *
 * **코드를 여기 다시 적지 않고 `EpubOpener` 의 상수를 그대로 본다.** 문자열을 두 벌
 * 두면 한쪽을 고칠 때 다른 쪽이 남고, 그때 화면에는 우리가 쓴 문장 대신 파서가 만든
 * 짧은 말이 뜬다 — 5단계의 '문구 두 벌' 과 같은 형태다.
 */
@Composable
private fun warningText(warning: ParseWarning): String = when (warning.code) {
    EpubOpener.WARN_MIMETYPE -> stringResource(R.string.doc_warn_mimetype)
    EpubOpener.WARN_DUPLICATE -> stringResource(R.string.doc_warn_duplicate, warning.detail)
    EpubOpener.WARN_OBFUSCATED -> stringResource(R.string.doc_warn_obfuscated)
    FlowWarnings.TRUNCATED ->
        if (warning.detail.isBlank()) stringResource(R.string.doc_warn_truncated_document)
        else stringResource(R.string.doc_warn_truncated, partLabel(warning.detail))
    FlowWarnings.PART_FAILED -> stringResource(R.string.doc_warn_part_failed, partLabel(warning.detail))
    FlowWarnings.MACROS -> stringResource(R.string.doc_warn_macros)
    FlowWarnings.DUPLICATE_PART -> stringResource(R.string.doc_warn_duplicate_part, warning.detail)
    FlowWarnings.BROKEN_RELATIONSHIPS -> stringResource(R.string.doc_warn_broken_rels)
    FlowWarnings.AUX_FAILED -> stringResource(R.string.doc_warn_aux_failed)
    FlowWarnings.NO_CONTENT_TYPES -> stringResource(R.string.doc_warn_no_content_types)
    // 모르는 코드의 `detail` 은 파서가 만든 짧은 말이라 사람에게 보일 문장이 아니다. 뜻만 전한다.
    else -> stringResource(R.string.doc_warn_other)
}

/**
 * 부분의 이름. 흐름 문서는 이름 없는 부분(제목 없는 슬라이드·긴 글의 조각)에 **번호**를 싣되
 * [FlowWarnings.PART_NUMBER_PREFIX] 를 붙인다(`OpcFlowDocument.partName`). **앞머리가 있을 때만**
 * 번호로 읽는다 — 숫자만 보고 읽으면 '2024' 라는 시트가 '2024번째 부분' 이 된다.
 */
@Composable
private fun partLabel(detail: String): String {
    if (!detail.startsWith(FlowWarnings.PART_NUMBER_PREFIX)) return detail
    val n = detail.removePrefix(FlowWarnings.PART_NUMBER_PREFIX).toIntOrNull() ?: return detail
    return stringResource(R.string.doc_part_number, n)
}
