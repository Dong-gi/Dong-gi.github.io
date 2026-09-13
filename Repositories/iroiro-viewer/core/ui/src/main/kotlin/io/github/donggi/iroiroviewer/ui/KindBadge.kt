package io.github.donggi.iroiroviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.donggi.iroiroviewer.model.FileKind

/**
 * 목록의 아이콘.
 *
 * 아이콘 글꼴 라이브러리를 넣지 않는다. `material-icons-extended` 는 이 앱이 쓰는
 * 열 몇 개를 위해 수천 개를 끌고 오고, 무엇보다 **확장자 자체가 가장 많은 정보를 준다** —
 * `MKV` 와 `MP4` 를 같은 필름 아이콘으로 그리면 사용자가 구분하지 못한다.
 *
 * 그래서 폴더는 폴더 모양으로 그리고, 파일은 종류 색을 입힌 사각형에 확장자를 적는다.
 */
@Composable
fun KindBadge(
    kind: FileKind,
    extension: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    locked: Boolean = false,
) {
    val color = kindColor(kind)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (kind == FileKind.FOLDER) {
            FolderGlyph(color, locked)
        } else {
            Canvas(Modifier.size(size)) {
                val pad = this.size.minDimension * 0.1f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, pad * 0.6f),
                    size = Size(this.size.width - pad * 2, this.size.height - pad * 1.2f),
                    cornerRadius = CornerRadius(pad * 1.6f, pad * 1.6f),
                )
            }
            Text(
                text = extension.uppercase().take(4).ifEmpty { "·" },
                color = Color.White,
                fontSize = when (extension.length) {
                    0, 1, 2 -> 12.sp
                    3 -> 11.sp
                    else -> 9.sp
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun FolderGlyph(color: Color, locked: Boolean) {
    Canvas(Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val r = w * 0.08f
        val path = Path().apply {
            // 탭이 달린 폴더. 왼쪽 위의 작은 턱이 폴더로 읽히게 하는 핵심이다.
            moveTo(w * 0.08f, h * 0.28f)
            lineTo(w * 0.42f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.38f)
            lineTo(w * 0.92f, h * 0.80f)
            lineTo(w * 0.08f, h * 0.80f)
            close()
        }
        drawPath(path, color = if (locked) color.copy(alpha = 0.45f) else color)
        if (locked) {
            // 자물쇠 몸통과 고리. 들어갈 수 없다는 것을 아이콘만으로 알리는 자리다.
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.40f, h * 0.52f),
                size = Size(w * 0.20f, h * 0.18f),
                cornerRadius = CornerRadius(r, r),
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.07f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.035f),
            )
        }
    }
}

/**
 * 종류별 색.
 *
 * 테마의 primary 를 쓰지 않고 고정 색을 쓰는 것은, 다이나믹 컬러가 배경화면에 따라
 * 바뀌어서 "노란 건 폴더, 파란 건 영상" 같은 사용자의 기억이 매번 깨지기 때문이다.
 * 종류 구분은 안정적이어야 한다.
 */
fun kindColor(kind: FileKind): Color = when (kind) {
    FileKind.FOLDER -> Color(0xFFF6B83C)
    FileKind.IMAGE -> Color(0xFF4CAF50)
    FileKind.AUDIO -> Color(0xFFE0709F)
    FileKind.VIDEO -> Color(0xFF5C7CFA)
    FileKind.TEXT -> Color(0xFF78909C)
    FileKind.CODE -> Color(0xFF546E7A)
    FileKind.PDF -> Color(0xFFD64545)
    FileKind.EBOOK -> Color(0xFF8E6BC7)
    FileKind.ARCHIVE -> Color(0xFF8D6E63)
    FileKind.COMIC -> Color(0xFFAA7B3D)
    FileKind.DOCUMENT -> Color(0xFF2B579A)
    FileKind.SHEET -> Color(0xFF217346)
    FileKind.SLIDE -> Color(0xFFD24726)
    FileKind.HWP -> Color(0xFF1A73E8)
    FileKind.APK -> Color(0xFF3DDC84)
    FileKind.FONT -> Color(0xFF6D4C41)
    FileKind.OTHER -> Color(0xFF9E9E9E)
}
