package com.pawssenger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.dashboard.TrendPoint
import com.pawssenger.app.dashboard.TripMarker
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.colors

/** 점들을 부드러운 곡선으로 잇는다 (시안 SVG 와 같은 방식: 제어점을 구간 가운데 x 에 둔다). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        val mx = (a.x + b.x) / 2
        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
}

/**
 * 메인 추이 그래프. 선·면 색은 현재 단계를 따른다 (안정은 브랜드 보라).
 * 점이 2개 미만이면 현재 점수로 평평한 선을 그린다.
 */
@Composable
fun TrendLine(points: List<TrendPoint>, level: Level, currentScore: Int, modifier: Modifier = Modifier) {
    val line = if (level == Level.GOOD) Paw.BrandLine else level.colors.accent
    val area = if (level == Level.GOOD) Color(0xFFB9A2F0) else level.colors.accent
    Canvas(modifier) {
        val data = if (points.size >= 2) points else listOf(TrendPoint(0f, currentScore), TrendPoint(1f, currentScore))
        val lo = (data.minOf { it.score } - 6).coerceAtLeast(0)
        val hi = (data.maxOf { it.score } + 6).coerceAtMost(104)
        val span = (hi - lo).coerceAtLeast(12)
        val top = size.height * 0.12f
        val usable = size.height * 0.62f
        fun y(s: Int) = top + (1f - (s - lo).toFloat() / span) * usable
        val pts = data.map { Offset(it.x * size.width, y(it.score)) }
        val path = smoothPath(pts)
        val fill = Path().apply {
            addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(area.copy(alpha = 0.45f), area.copy(alpha = 0.04f))))
        drawPath(path, line, style = Stroke(2.dp.toPx()))
        val last = pts.last()
        // 현재 지점 표시: 세로선과 동그라미를 같은 x 에 둔다 (오른쪽 끝에서 잘리지 않게 안쪽으로)
        val mx = last.x - 6.dp.toPx()
        drawLine(Color(0xFFB5A9D6), Offset(mx, 0f), Offset(mx, size.height), 1.dp.toPx())
        drawCircle(Color.White, 5.dp.toPx(), Offset(mx, last.y))
        drawCircle(if (level == Level.GOOD) Paw.Brand else line, 5.dp.toPx(), Offset(mx, last.y), style = Stroke(2.dp.toPx()))
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, color: Color, size: Int, bold: Boolean = false, anchor: Float = 0f) {
    val l = tm.measure(text, TextStyle(fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(l, topLeft = Offset(x - l.size.width * anchor, y - l.size.height / 2f))
}

/**
 * 주행 케어: 출발 후 편안함 지수 변화. 배경에 안정/주의/위험 구간 띠, 이벤트 지점 표시.
 * 세로축은 50~100 고정.
 */
@Composable
fun TripChart(points: List<TrendPoint>, markers: List<TripMarker>, elapsedMin: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val left = 28.dp.toPx(); val bottom = 34.dp.toPx(); val top = 4.dp.toPx()
        val w = size.width - left; val h = size.height - bottom - top
        fun y(s: Float) = top + (1f - ((s - 50f) / 50f).coerceIn(0f, 1f)) * h
        fun x(f: Float) = left + f * (w - 56.dp.toPx())

        // 구간 띠
        drawRect(Level.GOOD.colors.bg.copy(alpha = 0.7f), Offset(left, y(100f)), Size(w, y(90f) - y(100f)))
        drawRect(Level.WARN.colors.bg.copy(alpha = 0.7f), Offset(left, y(90f)), Size(w, y(70f) - y(90f)))
        drawRect(Level.BAD.colors.bg.copy(alpha = 0.7f), Offset(left, y(70f)), Size(w, y(50f) - y(70f)))
        listOf(100, 90, 70, 50).forEach { label(tm, "$it", left - 8.dp.toPx(), y(it.toFloat()), Paw.InkSub, 12, anchor = 1f) }
        label(tm, "안정", size.width - 8.dp.toPx(), y(95f), Level.GOOD.colors.text, 12, true, 1f)
        label(tm, "주의", size.width - 8.dp.toPx(), y(80f), Level.WARN.colors.text, 12, true, 1f)
        label(tm, "위험", size.width - 8.dp.toPx(), y(60f), Level.BAD.colors.text, 12, true, 1f)

        // 가로축: 출발 … 현재
        val ticks = if (elapsedMin >= 8) {
            val step = when { elapsedMin >= 160 -> 60; elapsedMin >= 80 -> 30; elapsedMin >= 40 -> 10; elapsedMin >= 16 -> 5; else -> 2 }
            (step until elapsedMin step step).filter { elapsedMin - it >= step * 0.8f }.map { it.toFloat() / elapsedMin to "${it}분" }
        } else emptyList()
        val xy = size.height - 14.dp.toPx()
        label(tm, "출발", x(0f), xy, Paw.InkSub, 12, anchor = 0.5f)
        ticks.forEach { (f, t) -> label(tm, t, x(f), xy, Paw.InkSub, 12, anchor = 0.5f) }
        label(tm, "현재", x(1f), xy, Paw.InkSub, 12, anchor = 0.5f)

        if (points.size >= 2) {
            val pts = points.map { Offset(x(it.x), y(it.score.toFloat())) }
            drawPath(smoothPath(pts), Paw.BrandStrong, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // 점은 이벤트마다 모두 찍는다. 글자는 가까운 이벤트끼리 묶어 한 번만 쓴다 (예: "급가속 3 · 급제동 1").
            val gap = 56.dp.toPx()
            val groups = ArrayList<MutableList<TripMarker>>()
            markers.sortedBy { it.x }.forEach { m ->
                val g = groups.lastOrNull()
                if (g != null && x(m.x) - x(g.last().x) < gap) g += m else groups += mutableListOf(m)
            }
            groups.forEach { g ->
                val centers = g.map { Offset(x(it.x), y(it.score.toFloat())) }
                centers.forEach { c ->
                    drawCircle(Color.White, 6.dp.toPx(), c)
                    drawCircle(Level.WARN.colors.accent, 6.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                }
                val text = g.groupingBy { it.label }.eachCount().entries.joinToString(" · ") { (name, n) -> if (n > 1) "$name $n" else name }
                val cx = (centers.first().x + centers.last().x) / 2
                label(tm, text, cx, centers.maxOf { it.y } + 20.dp.toPx(), Level.WARN.colors.text, 12, true, 0.5f)
            }
            val last = pts.last()
            drawCircle(Color.White, 7.dp.toPx(), last)
            drawCircle(Paw.BrandStrong, 5.dp.toPx(), last)
        }
    }
}

/** 원형 게이지 (0~100) */
@Composable
fun ScoreRing(score: Int, level: Level, size: Dp, modifier: Modifier = Modifier) {
    val color = if (level == Level.GOOD) Paw.BrandStrong else level.colors.accent
    val textColor = if (level == Level.GOOD) Paw.Ink else level.colors.text
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.115f
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Paw.Lilac, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(color, -90f, 360f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        T("$score", (size.value * 0.31f).toInt(), FontWeight.Bold, textColor)
    }
}
