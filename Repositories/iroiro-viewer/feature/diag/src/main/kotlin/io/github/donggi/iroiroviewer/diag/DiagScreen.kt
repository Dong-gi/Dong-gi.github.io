package io.github.donggi.iroiroviewer.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 2단계의 화면. 겉보기 진척은 느리지만, 여기 적힌 값들이 뒤 열두 단계의 전제다.
 *
 * 표시 규칙은 하나다 — **측정한 것만 적는다.** 모르는 값은 "모름" 이라고 쓰고
 * 그럴듯한 기본값으로 채우지 않는다.
 */
@Composable
fun DiagScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sections by produceState<List<Diagnostics.Section>?>(initialValue = null, context) {
        value = Diagnostics.collect(context)
    }

    val current = sections
    if (current == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.width(8.dp))
            Text("이 기기에서 값을 재는 중", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        current.forEach { section ->
            item(key = "h-${section.title}") {
                Column {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
            items(section.rows, key = { "${section.title}-${it.label}" }) { row ->
                DiagRowView(row)
            }
        }
    }
}

@Composable
private fun DiagRowView(row: Diagnostics.Row) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = when (row.ok) {
                true -> "O"
                false -> "X"
                null -> "-"
            },
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = when (row.ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.outline
            },
        )
        Text(
            text = row.label,
            modifier = Modifier.width(150.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = row.value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
