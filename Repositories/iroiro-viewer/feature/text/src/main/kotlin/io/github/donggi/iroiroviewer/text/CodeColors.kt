package io.github.donggi.iroiroviewer.text

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.donggi.iroiroviewer.format.text.TokenKind

/**
 * 코드 색.
 *
 * ## 왜 Material 색을 그대로 쓰지 않는가
 *
 * Material 의 색 얼개는 **역할**(primary·error·surface)로 되어 있고 그 역할은 여덟 종류가
 * 못 된다. 억지로 맞추면 주석과 문자열이 같은 계열이 되어 코드가 안 읽힌다. 그래서 토큰
 * 색만 직접 고른다. 배경·본문색은 Material 것을 쓴다 — 그쪽은 역할이 맞는다.
 *
 * ## 밝은 화면과 어두운 화면을 따로 고른다
 *
 * 같은 색을 두 배경에 쓰면 한쪽에서 반드시 대비가 모자란다. 특히 주석은 일부러 흐리게
 * 하는 색이라 밝은 배경에서 회색을 그대로 쓰면 보이지 않는다.
 */
@Immutable
data class CodeColors(
    val foreground: Color,
    val keyword: Color,
    val literal: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val tag: Color,
    val attribute: Color,
    /** 줄 번호. 본문보다 확실히 흐려야 눈이 본문으로 간다. */
    val gutter: Color,
    /** 찾은 자리. 지금 보고 있는 것만 더 진하다. */
    val hit: Color,
    val currentHit: Color,
) {
    fun of(kind: TokenKind): Color = when (kind) {
        TokenKind.PLAIN -> foreground
        TokenKind.KEYWORD -> keyword
        TokenKind.LITERAL -> literal
        TokenKind.STRING -> string
        TokenKind.NUMBER -> number
        TokenKind.COMMENT -> comment
        TokenKind.TAG -> tag
        TokenKind.ATTRIBUTE -> attribute
    }
}

@Composable
fun rememberCodeColors(): CodeColors {
    val dark = isSystemInDarkTheme()
    val fg = MaterialTheme.colorScheme.onSurface
    return remember(dark, fg) {
        if (dark) {
            CodeColors(
                foreground = fg,
                keyword = Color(0xFFC678DD),
                literal = Color(0xFF56B6C2),
                string = Color(0xFF98C379),
                number = Color(0xFFD19A66),
                comment = Color(0xFF8A9199),
                tag = Color(0xFFE06C75),
                attribute = Color(0xFFD19A66),
                gutter = Color(0xFF5C6370),
                hit = Color(0x553B82F6),
                currentHit = Color(0xAAF59E0B),
            )
        } else {
            CodeColors(
                foreground = fg,
                keyword = Color(0xFFA626A4),
                literal = Color(0xFF0184BC),
                string = Color(0xFF2E7D32),
                number = Color(0xFF986801),
                comment = Color(0xFF8C8C94),
                tag = Color(0xFFC2371F),
                attribute = Color(0xFF986801),
                gutter = Color(0xFFA0A1A7),
                hit = Color(0x553B82F6),
                currentHit = Color(0xAAF59E0B),
            )
        }
    }
}
