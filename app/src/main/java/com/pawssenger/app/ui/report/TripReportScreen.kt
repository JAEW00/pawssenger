package com.pawssenger.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.analysis.EventType
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.dashboard.PetSafeId
import com.pawssenger.app.data.AppRepository
import com.pawssenger.app.data.DriveSessionRecord
import com.pawssenger.app.data.ReportBucket
import com.pawssenger.app.data.ReportPeriod
import com.pawssenger.app.data.ReportStats
import com.pawssenger.app.ui.components.LevelChip
import com.pawssenger.app.ui.components.LevelIcon
import com.pawssenger.app.ui.components.OutlineButton
import com.pawssenger.app.ui.components.PAGE_SIZE
import com.pawssenger.app.ui.components.Pager
import com.pawssenger.app.ui.components.PawIcons
import com.pawssenger.app.ui.components.PillChip
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.ScreenHeader
import com.pawssenger.app.ui.components.StrokeIcon
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.components.listScrollbar
import com.pawssenger.app.ui.components.pageCountOf
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.colors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun isToday(ms: Long): Boolean {
    val today = Calendar.getInstance(); val d = Calendar.getInstance().apply { timeInMillis = ms }
    return today.get(Calendar.YEAR) == d.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)
}

private fun clock(ms: Long): String = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date(ms))
private fun dayLabel(ms: Long): String = if (isToday(ms)) "오늘" else SimpleDateFormat("M/d", Locale.KOREAN).format(Date(ms))

private fun durationText(ms: Long): String {
    val min = (ms / 60_000).toInt()
    return if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
}

private fun DriveSessionRecord.count(type: EventType) = events.count { it.type == type }

/** "제동 2 · 회전 1" 처럼 있는 것만 짧게. 없으면 "없음". */
private fun DriveSessionRecord.eventSummary(): String = listOf(
    "가속" to count(EventType.HARD_ACCEL), "제동" to count(EventType.HARD_BRAKE), "회전" to count(EventType.SHARP_TURN),
).filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }.ifEmpty { "없음" }

@Composable
fun TripReportScreen(repository: AppRepository, onBack: () -> Unit) {
    val all by repository.sessions.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(ReportPeriod.MONTH) }
    var onlyIssues by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    val now = remember(all, period) { System.currentTimeMillis() }
    val start = remember(period, now) { ReportStats.start(period, now) }
    val sessions = remember(all, start) { all.filter { it.startedAtMs >= start } }
    val previous = remember(all, period, start) {
        if (period == ReportPeriod.ALL) emptyList()
        else ReportStats.previousStart(period, start).let { p -> all.filter { it.startedAtMs in p until start } }
    }
    val buckets = remember(all, period, now) { ReportStats.buckets(period, all, now) }
    val listed = remember(sessions, onlyIssues) { if (onlyIssues) sessions.filter { Level.ofScore(it.comfortScore) != Level.GOOD } else sessions }

    all.firstOrNull { it.id == detailId }?.let { record ->
        TripDetailDialog(record, onDelete = { repository.deleteSession(record.id); detailId = null }, onDismiss = { detailId = null })
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("이동 리포트", onBack, center = {
            ReportPeriod.entries.forEach { p -> PillChip(p.label, period == p, { period = p }) }
            Spacer(Modifier.width(6.dp))
            // 지금 어떤 범위를 보고 있는지
            T(ReportStats.rangeText(period, now), 14, FontWeight.Medium, Paw.InkSub, maxLines = 1)
        })
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryRow(period, sessions, previous)
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize().card(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T("${period.label}에는 주행 기록이 없어요", 20, FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        T("주행을 마치면 기록이 여기에 쌓여요.", 15, color = Paw.InkSub)
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChartCard(period, buckets, comparison(period, sessions, previous), Modifier.weight(400f).fillMaxHeight())
                    HistoryCard(listed, sessions.size, onlyIssues, { onlyIssues = it }, Modifier.weight(800f).fillMaxHeight()) { detailId = it }
                }
            }
        }
    }
}

// ───────────────────────── 요약 ─────────────────────────

@Composable
private fun SummaryRow(period: ReportPeriod, sessions: List<DriveSessionRecord>, previous: List<DriveSessionRecord>) {
    val prevName = period.previousLabel
    val has = prevName != null && previous.isNotEmpty()
    val totalMin = (sessions.sumOf { it.durationMs } / 60_000).toInt()
    val avg = if (sessions.isEmpty()) null else sessions.map { it.comfortScore }.average().roundToInt()
    val prevAvg = if (previous.isEmpty()) null else previous.map { it.comfortScore }.average().roundToInt()

    Row(Modifier.fillMaxWidth().height(118.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Summary("총 주행", if (has) "$prevName ${previous.size}회" else null) { Big("${sessions.size}"); Unit_("회") }
        Summary(
            "평균 펫 편안함 지수",
            if (has && avg != null && prevAvg != null) (avg - prevAvg).let { d -> if (d == 0) "${prevName}와 같아요" else "${prevName}보다 ${if (d > 0) "+" else "−"}${abs(d)}점" } else null,
        ) { Big(avg?.toString() ?: "-"); Unit_("점") }
        Summary("총 이동 시간", if (has) "$prevName ${durationText(previous.sumOf { it.durationMs })}" else null) {
            if (totalMin >= 60) { Big("${totalMin / 60}"); Unit_("시간"); Spacer(Modifier.width(8.dp)) }
            Big("${totalMin % 60}"); Unit_("분")
        }
        Summary("펫 세이프 정상 출발", if (has) "$prevName ${previous.count { it.safeStart }} / ${previous.size}회" else null) {
            Big("${sessions.count { it.safeStart }}"); Unit_("/ ${sessions.size}회", pad = 8)
        }
    }
}

@Composable
private fun RowScope.Summary(label: String, compare: String?, value: @Composable RowScope.() -> Unit) {
    Column(Modifier.weight(1f).fillMaxHeight().card().padding(start = 24.dp, top = 18.dp, end = 16.dp)) {
        T(label, 14, color = Paw.InkSub, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom, content = value)
        if (compare != null) {
            Spacer(Modifier.height(4.dp))
            T(compare, 12, FontWeight.Medium, Paw.BrandStrong, maxLines = 1)
        }
    }
}

@Composable private fun Big(text: String) = T(text, 34, FontWeight.Bold, lineHeight = 1.1f)
@Composable private fun Unit_(text: String, pad: Int = 4) = T(text, 16, FontWeight.Medium, modifier = Modifier.padding(start = pad.dp, bottom = 4.dp))

/** 직전 기간과 비교한 한 줄 요약. 비교할 기록이 없으면 null. */
private fun comparison(period: ReportPeriod, current: List<DriveSessionRecord>, prev: List<DriveSessionRecord>): String? {
    val name = period.previousLabel ?: return null
    if (current.isEmpty() || prev.isEmpty()) return null
    fun harsh(l: List<DriveSessionRecord>) = l.sumOf { s -> s.events.count { it.type != EventType.SHARP_TURN } }.toDouble() / l.size
    return when {
        harsh(current) < harsh(prev) -> "${name}보다 급가속·급제동이 줄었어요."
        harsh(current) > harsh(prev) -> "${name}보다 급가속·급제동이 늘었어요."
        else -> null
    }
}

// ───────────────────────── 그래프 ─────────────────────────

private fun barColor(level: Level): Color = if (level == Level.GOOD) Paw.BrandStrong else level.colors.accent

/** 기간에 맞춰 묶은 평균 지수 막대: 이번 주 = 요일별, 이번 달 = 주별, 전체 = 월별 */
@Composable
private fun ChartCard(period: ReportPeriod, buckets: List<ReportBucket>, note: String?, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    Column(modifier.card().padding(24.dp)) {
        T("${period.bucketLabel} 평균 편안함 지수", 18, FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Level.entries.forEach { l ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(barColor(l)))
                Spacer(Modifier.width(5.dp))
                T(l.label, 12, color = Paw.InkSub)
                Spacer(Modifier.width(12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 30.dp.toPx(); val bottom = 46.dp.toPx(); val top = 26.dp.toPx()
            val h = size.height - bottom - top
            val base = size.height - bottom
            // 세로축은 40~100. 낮은 점수도 막대 차이가 보이도록 0 이 아닌 40 에서 시작하며, 눈금으로 그 사실을 표시한다.
            fun y(score: Int) = base - ((score - AXIS_MIN).coerceIn(0, 100 - AXIS_MIN) / (100f - AXIS_MIN)) * h
            fun text(s: String, x: Float, yy: Float, color: Color, size: Int, bold: Boolean, anchor: Float) {
                val l = tm.measure(s, TextStyle(fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
                drawText(l, topLeft = Offset(x - l.size.width * anchor, yy - l.size.height / 2f))
            }
            listOf(90, 70).forEach { g ->
                drawLine(Color(0xFFD9D2EC), Offset(left, y(g)), Offset(size.width, y(g)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                text("$g", left - 10.dp.toPx(), y(g), Paw.InkSub, 12, false, 1f)
            }
            drawLine(Color(0xFFD9D2EC), Offset(left, base), Offset(size.width, base), 1.dp.toPx())
            text("$AXIS_MIN", left - 10.dp.toPx(), base, Paw.InkSub, 12, false, 1f)

            val slot = (size.width - left) / buckets.size.coerceAtLeast(1)
            val bw = minOf(slot * 0.6f, 44.dp.toPx())
            buckets.forEachIndexed { i, b ->
                val cx = left + slot * (i + 0.5f)
                val avg = b.average
                if (avg != null) {
                    val level = Level.ofScore(avg)
                    val yy = y(avg)
                    drawRoundRect(barColor(level), Offset(cx - bw / 2, yy), Size(bw, base - yy), CornerRadius(8.dp.toPx()))
                    text("$avg", cx, yy - 12.dp.toPx(), if (level == Level.GOOD) Paw.Ink else level.colors.text, 13, true, 0.5f)
                }
                text(b.label, cx, base + 14.dp.toPx(), if (b.current) Paw.Ink else Paw.InkSub, 13, b.current, 0.5f)
                // 그 구간의 주행 횟수 (없으면 "-")
                text(if (b.count > 0) "${b.count}회" else "-", cx, base + 32.dp.toPx(), Paw.InkMuted, 11, false, 0.5f)
            }
        }
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            T(note, 13, FontWeight.Medium, Paw.BrandStrong, maxLines = 2)
        }
    }
}

private const val AXIS_MIN = 40

// ───────────────────────── 주행 이력 ─────────────────────────

@Composable
private fun HistoryCard(
    sessions: List<DriveSessionRecord>, total: Int, onlyIssues: Boolean, onOnlyIssues: (Boolean) -> Unit,
    modifier: Modifier, onOpen: (Long) -> Unit,
) {
    Column(modifier.card().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("주행 이력", 18, FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            T(if (onlyIssues) "${sessions.size} / ${total}건" else "${total}건", 14, FontWeight.Medium, Paw.BrandStrong)
            Spacer(Modifier.weight(1f))
            // 주의·위험 주행만 보기
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (onlyIssues) Level.WARN.colors.bg else Paw.Tile)
                    .clickable { onOnlyIssues(!onlyIssues) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelIcon(Level.WARN, 18.dp)
                Spacer(Modifier.width(6.dp))
                T("주의·위험만", 14, if (onlyIssues) FontWeight.Bold else FontWeight.Medium, if (onlyIssues) Level.WARN.colors.text else Paw.InkSub)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("출발", COL_DATE); HeaderCell("경로", 0, weight = true); HeaderCell("시간 · 거리", COL_TRIP)
            HeaderCell("이벤트", COL_EVENT); HeaderCell("지수", COL_SCORE)
            PetSafeId.entries.forEach { T(it.label, 11, color = Paw.InkSub, align = TextAlign.Center, modifier = Modifier.width(COL_SAFE.dp), maxLines = 1) }
            Spacer(Modifier.width(COL_ARROW.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                T("주의·위험 단계의 주행이 없어요", 15, color = Paw.InkSub)
            }
            return@Column
        }
        // 20건씩 나눠 보여 준다. 필터·기간이 바뀌어 목록이 달라지면 첫 페이지로 돌아간다.
        var page by rememberSaveable(sessions.size, sessions.firstOrNull()?.id) { mutableIntStateOf(0) }
        val pageCount = pageCountOf(sessions.size)
        val shown = sessions.drop(page.coerceIn(0, pageCount - 1) * PAGE_SIZE).take(PAGE_SIZE)
        val listState = rememberLazyListState()
        LaunchedEffect(page) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).listScrollbar(listState), state = listState) {
            items(shown, key = { it.id }) { s ->
                val level = Level.ofScore(s.comfortScore)
                Row(Modifier.fillMaxWidth().height(64.dp).clickable { onOpen(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(COL_DATE.dp)) {
                        T(dayLabel(s.startedAtMs), 15, FontWeight.Bold, maxLines = 1)
                        T(clock(s.startedAtMs), 12, color = Paw.InkSub, maxLines = 1)
                    }
                    T(s.routeName ?: "목적지 없는 주행", 15, FontWeight.Medium, if (s.routeName != null) Paw.Ink else Paw.InkSub, modifier = Modifier.weight(1f), maxLines = 1)
                    T("${durationText(s.durationMs)} · ${"%.0f".format(s.distanceKm)}km", 13, color = Paw.InkSub, modifier = Modifier.width(COL_TRIP.dp), maxLines = 1)
                    T(s.eventSummary(), 13, color = if (s.events.isEmpty()) Paw.InkMuted else Level.WARN.colors.text, modifier = Modifier.width(COL_EVENT.dp), maxLines = 1)
                    Box(Modifier.width(COL_SCORE.dp)) {
                        Box(Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(level.colors.bg).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                            T("${s.comfortScore}점", 14, FontWeight.Bold, level.colors.text, maxLines = 1)
                        }
                    }
                    (0 until 4).forEach { i ->
                        Box(Modifier.width(COL_SAFE.dp), contentAlignment = Alignment.Center) {
                            val l = s.petSafe.getOrNull(i)
                            if (l != null) LevelIcon(l, 20.dp) else Box(Modifier.size(8.dp).clip(CircleShape).background(Paw.Lilac))
                        }
                    }
                    Box(Modifier.width(COL_ARROW.dp), contentAlignment = Alignment.CenterEnd) { StrokeIcon(PawIcons.ChevronRight, 16.dp, Paw.InkMuted, 2f) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
            }
        }
        Pager(page.coerceIn(0, pageCount - 1), pageCount, { page = it })
    }
}

private const val COL_DATE = 92
private const val COL_TRIP = 108
private const val COL_EVENT = 112
private const val COL_SCORE = 66
private const val COL_SAFE = 38
private const val COL_ARROW = 20

@Composable
private fun RowScope.HeaderCell(text: String, width: Int, weight: Boolean = false) =
    T(text, 12, color = Paw.InkSub, modifier = if (weight) Modifier.weight(1f) else Modifier.width(width.dp), maxLines = 1)

// ───────────────────────── 주행 상세 ─────────────────────────

/** 이벤트 시각이 실제 시계 기준으로 저장된 기록인지 (이전 버전의 기록은 차량 내부 시간이라 시각을 보여 줄 수 없다) */
private fun DriveSessionRecord.hasClockTimes(): Boolean =
    events.all { it.timestampMs in (startedAtMs - 60_000)..(startedAtMs + durationMs + 60_000) }

private fun DriveEvent.detailText(): String {
    val what = when (type) { EventType.HARD_BRAKE -> "감속"; EventType.HARD_ACCEL -> "가속"; EventType.SHARP_TURN -> "횡가속" }
    return "$what ${"%.2f".format(abs(magnitude) / 9.81f)}g"
}

@Composable
private fun TripDetailDialog(r: DriveSessionRecord, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val level = Level.ofScore(r.comfortScore)
    var confirmDelete by remember { mutableStateOf(false) }
    val clockTimes = remember(r) { r.hasClockTimes() }
    // 팝업은 별도 창이라 화면 배율을 이어받지 못한다 → 바깥의 배율을 넘겨준다.
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(Modifier.width(640.dp).card(28, Paw.Card).padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(r.routeName ?: "목적지 없는 주행", 22, FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        T(
                            "${SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(r.startedAtMs))} ${clock(r.startedAtMs)} 출발 · " +
                                "${durationText(r.durationMs)} · ${"%.1f".format(r.distanceKm)}km",
                            14, color = Paw.InkSub, maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            T("${r.comfortScore}", 40, FontWeight.Bold, if (level == Level.GOOD) Paw.Ink else level.colors.text, lineHeight = 1.0f)
                            T("점", 16, FontWeight.Medium, modifier = Modifier.padding(start = 3.dp, bottom = 5.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        LevelChip(level, dot = true)
                    }
                }
                Spacer(Modifier.height(18.dp))

                T("펫 세이프", 14, FontWeight.Bold, Paw.InkSub)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PetSafeId.entries.forEachIndexed { i, id ->
                        val l = r.petSafe.getOrNull(i)
                        Column(
                            Modifier.weight(1f).fillMaxHeight().card(14, if (l == null || l == Level.GOOD) Paw.Tile else l.colors.bg),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            T(id.label, 13, color = Paw.InkSub, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (l != null) { LevelIcon(l, 16.dp); Spacer(Modifier.width(5.dp)) }
                                T(l?.label ?: "미연동", 14, FontWeight.Bold, l?.colors?.text ?: Paw.InkMuted, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                T(if (r.safeStart) "출발할 때 네 항목이 모두 정상이었어요." else "출발할 때 확인이 필요한 항목이 있었어요.", 13, color = Paw.InkSub)
                Spacer(Modifier.height(18.dp))

                T("감지된 이벤트", 14, FontWeight.Bold, Paw.InkSub)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountCell("급가속", r.count(EventType.HARD_ACCEL))
                    CountCell("급제동", r.count(EventType.HARD_BRAKE))
                    CountCell("급회전", r.count(EventType.SHARP_TURN))
                }
                if (r.events.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val eventScroll = rememberScrollState()
                    Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).listScrollbar(eventScroll).verticalScroll(eventScroll)) {
                        r.events.forEachIndexed { i, e ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
                            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (clockTimes) T(clock(e.timestampMs), 13, color = Paw.InkSub, modifier = Modifier.width(92.dp), maxLines = 1)
                                LevelIcon(Level.WARN, 18.dp)
                                Spacer(Modifier.width(8.dp))
                                T(e.type.label, 15, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                                T(e.detailText(), 13, color = Paw.InkSub, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 실수로 지우지 않도록 두 번 눌러야 삭제된다.
                    OutlineButton(
                        if (confirmDelete) "한 번 더 누르면 삭제돼요" else "기록 삭제", Modifier.weight(1f), height = 54, color = Color(0xFFC62828),
                    ) { if (confirmDelete) onDelete() else confirmDelete = true }
                    PrimaryButton("닫기", Modifier.weight(1f), height = 54, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RowScope.CountCell(label: String, count: Int) {
    Column(
        Modifier.weight(1f).fillMaxHeight().card(14, if (count > 0) Level.WARN.colors.bg else Paw.Tile),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(label, 12, color = Paw.InkSub, maxLines = 1)
        T("${count}회", 19, FontWeight.Bold, if (count > 0) Level.WARN.colors.text else Paw.Ink, lineHeight = 1.15f)
    }
}
