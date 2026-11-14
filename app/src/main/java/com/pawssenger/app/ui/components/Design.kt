package com.pawssenger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawssenger.app.R
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.colors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DESIGN_WIDTH = 1280f
const val DESIGN_HEIGHT = 720f

/**
 * 디자인 시안(1280×720) 좌표를 그대로 dp/sp 로 쓸 수 있게 화면 밀도를 바꾼다.
 * 가로를 1280 단위로 맞추고, 세로로 남는 공간은 각 화면이 카드 높이를 늘려 채운다.
 * (Pleos 에뮬레이터의 앱 영역은 약 4:3 이라 세로가 남는다.)
 */
@Composable
fun DesignSurface(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(0f to Paw.BgStart, 0.55f to Paw.BgMid, 1f to Paw.BgEnd)
                )
            ) { content() }
        }
    }
}

/** 디자인 글자 스타일: 글꼴 여백을 없애 시안 좌표와 맞춘다. */
@Composable
fun T(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Paw.Ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: Float = 1.35f,
) {
    Text(
        text, modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
        style = TextStyle(
            fontSize = size.sp, fontWeight = weight, lineHeight = (size * lineHeight).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/** 24×24 기준 선 아이콘 (시안 SVG 의 path 를 그대로 쓴다) */
object PawIcons {
    val Home = listOf("M3 11l9-8 9 8", "M5 10v10h5v-6h4v6h5V10")
    val Pulse = listOf("M2 12h4l2.5-6 4 12 2.5-8 1.5 2H22")
    val Thermo = listOf("M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z", "M12 10v6")
    val Gauge = listOf("M3.5 17a9 9 0 1 1 17 0", "M12 15l4.5-5", "M11 15a1 1 0 1 0 2 0a1 1 0 1 0-2 0")
    val Waves = listOf("M2 9c2.5-3 4.5-3 7 0s4.5 3 7 0 4-2.5 6 0", "M2 16c2.5-3 4.5-3 7 0s4.5 3 7 0 4-2.5 6 0")
    val Bolt = listOf("M13 3L5 13h6l-1 8 8-10h-6l1-8z")
    val Shield = listOf("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z", "M8.5 12l2.5 2.5 4.5-5")
    val Carrier = listOf(
        "M5.5 8h13a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-7A2.5 2.5 0 0 1 5.5 8z",
        "M8.5 8V6.5A2.5 2.5 0 0 1 11 4h2a2.5 2.5 0 0 1 2.5 2.5V8", "M8 12v4.5", "M12 12v4.5", "M16 12v4.5",
    )
    val Harness = listOf("M7 3l5 6 5-6", "M12 9v4", "M5 13h14", "M7 13v5a3 3 0 0 0 3 3h4a3 3 0 0 0 3-3v-5")
    private val carBody = listOf(
        "M2.5 16.5v-3.5l3-5.5h9.5l4 5.5h2.5v3.5h-2", "M9.5 16.5h5",
        "M4.7 16.5a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0", "M14.7 16.5a2.3 2.3 0 1 0 4.6 0a2.3 2.3 0 1 0-4.6 0",
    )
    val CarWindow = carBody + "M7.5 9.5L6 12.5h10.5"
    val CarDoor = carBody + listOf("M11 7.5v6", "M12.5 13h2")
    val Speaker = listOf("M4 10v4h3l4.5 3.5v-11L7 10H4z", "M15.5 9.5a3.5 3.5 0 0 1 0 5", "M18 7a7 7 0 0 1 0 10")
    val CarFront = listOf(
        "M4 16v-3.5l2-5h12l2 5V16",
        "M4.5 12.5h15a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 16v-2a1.5 1.5 0 0 1 1.5-1.5z",
        "M6 17.5v2", "M18 17.5v2",
    )
    val Pin = listOf("M12 20s6-5.3 6-9.5a6 6 0 0 0-12 0C6 14.7 12 20 12 20z", "M9.8 10.5a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0")
    val ChevronRight = listOf("M9 5l7 7-7 7")
    val ChevronLeft = listOf("M15 5l-7 7 7 7")
}

@Composable
fun StrokeIcon(paths: List<String>, size: Dp, tint: Color = Paw.Brand, strokeWidth: Float = 1.8f, modifier: Modifier = Modifier) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            parsed.forEach { drawPath(it, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

/** 단계 표시 아이콘: 안정 = 체크, 주의 = 느낌표, 위험 = X (색만으로 구분하지 않는다) */
@Composable
fun LevelIcon(level: Level, size: Dp, modifier: Modifier = Modifier) {
    val c = level.colors.accent
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(c, r)
        val w = r * 0.24f
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = center.x; val cy = center.y
        when (level) {
            Level.GOOD -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - r * 0.45f, cy + r * 0.05f); lineTo(cx - r * 0.15f, cy + r * 0.35f); lineTo(cx + r * 0.42f, cy - r * 0.28f)
                }
                drawPath(p, Color.White, style = st)
            }
            Level.WARN -> {
                drawLine(Color.White, Offset(cx, cy - r * 0.5f), Offset(cx, cy + r * 0.1f), w, StrokeCap.Round)
                drawCircle(Color.White, w * 0.62f, Offset(cx, cy + r * 0.45f))
            }
            Level.BAD -> {
                val d = r * 0.36f
                drawLine(Color.White, Offset(cx - d, cy - d), Offset(cx + d, cy + d), w, StrokeCap.Round)
                drawLine(Color.White, Offset(cx + d, cy - d), Offset(cx - d, cy + d), w, StrokeCap.Round)
            }
        }
    }
}

/** 작은 단계 칩 (안정 / 주의 / 위험) */
@Composable
fun LevelChip(level: Level, text: String = level.label, height: Int = 24, fontSize: Int = 12, dot: Boolean = false, modifier: Modifier = Modifier) {
    val c = level.colors
    Row(
        modifier.height(height.dp).clip(RoundedCornerShape(50)).background(c.bg).padding(horizontal = (height * 0.42f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(6.dp))
        }
        T(text, fontSize, FontWeight.Bold, c.text, maxLines = 1)
    }
}

@Composable
fun PawTopBar(onHome: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(5_000) } }
    val time = remember(now.time / 60_000) { SimpleDateFormat("h:mm", Locale.US).format(now) }
    val ampm = remember(now.time / 60_000) { SimpleDateFormat("a", Locale.US).format(now) }
    val date = remember(now.time / 60_000) { SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(now) }

    Box(Modifier.fillMaxWidth().height(84.dp)) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onHome), contentAlignment = Alignment.Center) {
                StrokeIcon(PawIcons.Home, 30.dp, Paw.Ink)
            }
            Spacer(Modifier.width(5.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFFC9BFE6)))
            Spacer(Modifier.width(14.dp))
            Image(painterResource(R.drawable.logo_pawssenger), "Pawssenger", Modifier.height(32.dp).width(194.dp))
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
            T(time, 28, FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            T(ampm, 17, FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.padding(bottom = 6.dp).width(1.dp).height(20.dp).background(Color(0xFFB5A9D6)))
            Spacer(Modifier.width(18.dp))
            T(date, 17, color = Color(0xFF3F3857), modifier = Modifier.padding(bottom = 3.dp))
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).size(56.dp).clip(CircleShape).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Settings, "설정", tint = Paw.Ink, modifier = Modifier.size(30.dp)) }
    }
}

/** 하위 화면 머리줄: [뒤로] 제목 + (가운데 슬롯) + (오른쪽 슬롯) */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, center: @Composable RowScope.() -> Unit = {}, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            StrokeIcon(PawIcons.ChevronLeft, 24.dp, Paw.Ink, 2f)
        }
        Spacer(Modifier.width(14.dp))
        T(title, 26, FontWeight.Bold)
        Spacer(Modifier.width(24.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = center)
        trailing()
    }
}

/** 화면 공통 골격: 상단 바 + 본문 */
@Composable
fun PawScreen(onHome: () -> Unit, onSettings: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PawTopBar(onHome, onSettings)
        content()
    }
}

fun Modifier.card(radius: Int = 28, color: Color = Color.White): Modifier =
    clip(RoundedCornerShape(radius.dp)).background(color)

fun Modifier.levelBorder(level: Level?, radius: Int, width: Float = 1.5f): Modifier =
    if (level == null || level == Level.GOOD) this else border(width.dp, level.colors.border, RoundedCornerShape(radius.dp))

/** 선택형 알약 칩 (필터, 기간 선택) */
@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, height: Int = 40) {
    Box(
        Modifier.height(height.dp).clip(RoundedCornerShape(50)).background(if (selected) Paw.BrandStrong else Color.White)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 15, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Color.White else Paw.InkSub, maxLines = 1) }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, height: Int = 60, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier.height(height.dp).clip(RoundedCornerShape(20.dp)).background(if (enabled) Paw.BrandStrong else Paw.Lilac)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        T(text, 18, FontWeight.Bold, if (enabled) Color.White else Paw.InkMuted, maxLines = 1)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, height: Int = 52, leading: (@Composable () -> Unit)? = null, color: Color = Paw.BrandStrong, onClick: () -> Unit) {
    Row(
        modifier.height(height.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.5.dp, color.copy(alpha = 0.75f), RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        T(text, 16, FontWeight.Bold, color, maxLines = 1)
    }
}
