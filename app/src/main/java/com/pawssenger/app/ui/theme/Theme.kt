package com.pawssenger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pawssenger.app.comfort.Level

/** 디자인 시안의 색상 토큰 */
object Paw {
    val Ink = Color(0xFF1B1730)
    val InkSub = Color(0xFF554E6B)
    val InkMuted = Color(0xFF8A84A0)
    val Brand = Color(0xFF7C52E0)        // 아이콘
    val BrandStrong = Color(0xFF6B3FD4)  // 버튼, 선택 상태
    val BrandLine = Color(0xFF9A7BE8)
    val Lilac = Color(0xFFE6DDFA)        // 트랙, 칩
    val Card = Color(0xFFFBF9FE)
    val Tile = Color(0xFFF4F0FC)
    val Divider = Color(0xFFE6DFF5)
    val IconDark = Color(0xFF4A3F7A)
    val BgStart = Color(0xFFF4F0FC)
    val BgMid = Color(0xFFE7DFF8)
    val BgEnd = Color(0xFFF1ECFB)
    val White90 = Color(0xE6FFFFFF)
}

/** 3단계 상태 색 (글자 / 강조 / 배경 / 테두리) */
data class LevelColors(val text: Color, val accent: Color, val bg: Color, val border: Color)

val Level.colors: LevelColors
    get() = when (this) {
        Level.GOOD -> LevelColors(Color(0xFF17592F), Color(0xFF1E7A40), Color(0xFFE3F4E7), Color(0xFFBFE3C8))
        Level.WARN -> LevelColors(Color(0xFF8A4500), Color(0xFFA85800), Color(0xFFFFF0DC), Color(0xFFF2BE85))
        Level.BAD -> LevelColors(Color(0xFF9B1C1C), Color(0xFFC62828), Color(0xFFFDE8E8), Color(0xFFEBA0A0))
    }

@Composable
fun PawssengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Paw.BrandStrong,
            onPrimary = Color.White,
            primaryContainer = Paw.Lilac,
            onPrimaryContainer = Paw.BrandStrong,
            background = Paw.BgStart,
            surface = Paw.Card,
            onSurface = Paw.Ink,
            onBackground = Paw.Ink,
            outline = Paw.Divider,
        ),
        content = content,
    )
}
