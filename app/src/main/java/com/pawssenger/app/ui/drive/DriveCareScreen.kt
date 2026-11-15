package com.pawssenger.app.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.dashboard.DashboardState
import com.pawssenger.app.dashboard.josa
import com.pawssenger.app.ui.components.LevelChip
import com.pawssenger.app.ui.components.LevelIcon
import com.pawssenger.app.ui.components.PAGE_SIZE
import com.pawssenger.app.ui.components.Pager
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.ScoreRing
import com.pawssenger.app.ui.components.ScreenHeader
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.TripChart
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.components.tripEventColor
import com.pawssenger.app.ui.components.listScrollbar
import com.pawssenger.app.ui.components.pageCountOf
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.colors

@Composable
fun DriveCareScreen(s: DashboardState, onBack: () -> Unit) {
    val trip = s.trip
    var showGuide by rememberSaveable { mutableStateOf(false) }
    if (showGuide) GuideDialog(s) { showGuide = false }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("주행 케어", onBack) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                T(if (trip.active) "주행 ${trip.elapsedMin}분 · ${"%.0f".format(trip.distanceKm)}km" else "주행 대기 중", 15, color = Paw.InkSub)
            }
            // 주행 안내는 고정 문구라 화면에 늘 두지 않고, 눌렀을 때 팝업으로 보여 준다.
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(Color.White)
                    .border(1.5.dp, Paw.BrandLine, RoundedCornerShape(50)).clickable { showGuide = true }.padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = Paw.BrandStrong, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                T("주행 안내", 15, FontWeight.Bold, Paw.BrandStrong, maxLines = 1)
            }
          }
        }
        Row(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScoreCard(s, Modifier.weight(420f).fillMaxHeight())
            Column(Modifier.weight(780f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 이벤트 카드에 요약과 목록이 함께 들어가므로 그래프와 높이를 반씩 나눈다.
                ChartCard(s, Modifier.fillMaxWidth().weight(1f))
                EventsCard(s, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun ScoreCard(s: DashboardState, modifier: Modifier) {
    val level = s.level
    Column(modifier.card().padding(24.dp)) {
        T("펫 편안함 지수", 18, FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(s.comfort.total, level, 140.dp, Modifier.padding(start = 14.dp))
            Spacer(Modifier.width(26.dp))
            Column {
                LevelChip(level, height = 30, fontSize = 14, dot = true)
                Spacer(Modifier.height(12.dp))
                T(shortHeadline(level, s.petName), 17, FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                T(if (s.trip.active) "최근 10분 평균 ${s.trip.recentAvg}점" else "주행을 시작하면 평균이 표시돼요", 14, color = Paw.InkSub)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
        Spacer(Modifier.height(16.dp))
        T("세부 항목", 13, FontWeight.Medium, Paw.InkSub)
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            SubScore("급가속 · 급제동", s.comfort.harsh)
            SubScore("급회전 · 진동", s.comfort.turnVibration)
            SubScore("실내 환경", s.comfort.cabin)
            SubScore("주행 안정성", s.comfort.stability)
        }
    }
}

private fun shortHeadline(level: Level, pet: String) = when (level) {
    Level.GOOD -> "${pet}${josa(pet, "이", "가")} 편안해하고 있어요"
    Level.WARN -> "${pet}${josa(pet, "이", "가")} 조금 불편할 수 있어요"
    Level.BAD -> "${pet}${josa(pet, "이", "가")} 불안해하고 있어요"
}

@Composable
private fun SubScore(label: String, score: Int) {
    val level = Level.ofScore(score)
    val color = if (level == Level.GOOD) Paw.BrandStrong else level.colors.accent
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, 16, modifier = Modifier.weight(1f), maxLines = 1)
            T("${score}점", 16, FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            LevelChip(level)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Paw.Lilac)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(score.coerceIn(0, 100) / 100f).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun ChartCard(s: DashboardState, modifier: Modifier) {
    val trip = s.trip
    Column(modifier.card().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("편안함 지수 변화", 18, FontWeight.Bold, modifier = Modifier.weight(1f))
            if (trip.active) {
                // 그래프 아래 이벤트 눈금의 색 설명
                listOf("급가속", "급제동", "급회전").forEach { name ->
                    Box(Modifier.width(4.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(tripEventColor(name)))
                    Spacer(Modifier.width(5.dp))
                    T(name, 12, color = Paw.InkSub)
                    Spacer(Modifier.width(12.dp))
                }
                Spacer(Modifier.width(4.dp))
                T("출발 후 ${trip.elapsedMin}분", 13, color = Paw.InkSub)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            TripChart(trip.series, trip.markers, trip.elapsedMin, Modifier.fillMaxSize())
            if (!trip.active) {
                T("차량이 움직이면 주행이 시작되고\n지수 변화가 여기에 그려져요", 14, color = Paw.InkSub, align = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** 감지된 이벤트: 위는 종류별 횟수(최근 10분), 아래는 이번 주행의 개별 이벤트 목록(최근 것부터). */
@Composable
private fun EventsCard(s: DashboardState, modifier: Modifier) {
    val trip = s.trip
    Column(modifier.card().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("감지된 이벤트", 18, FontWeight.Bold, modifier = Modifier.weight(1f))
            T("요약은 최근 10분 · 목록은 이번 주행", 13, color = Paw.InkSub)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CountCell("급가속", trip.recentCounts.accel)
            CountCell("급제동", trip.recentCounts.brake)
            CountCell("급회전", trip.recentCounts.turn)
        }
        Spacer(Modifier.height(10.dp))
        if (trip.events.isEmpty()) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                LevelIcon(Level.GOOD, 22.dp)
                Spacer(Modifier.width(12.dp))
                T(if (trip.active) "감지된 이벤트가 없어요" else "주행 중 감지된 이벤트가 여기에 표시돼요", 16, FontWeight.Medium)
            }
        } else {
            // 20건씩 나눠 보여 준다 (최근 것이 첫 페이지).
            var page by rememberSaveable { mutableIntStateOf(0) }
            val pageCount = pageCountOf(trip.events.size)
            val current = page.coerceIn(0, pageCount - 1)
            val shown = trip.events.drop(current * PAGE_SIZE).take(PAGE_SIZE)
            val listState = rememberLazyListState()
            LaunchedEffect(current) { listState.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).listScrollbar(listState), state = listState) {
                itemsIndexed(shown) { i, e ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
                    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(e.time, 14, color = Paw.InkSub, modifier = Modifier.width(96.dp), maxLines = 1)
                        LevelIcon(Level.WARN, 20.dp)
                        Spacer(Modifier.width(10.dp))
                        T(e.title, 16, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        T(e.detail, 14, color = Paw.InkSub, maxLines = 1)
                    }
                }
            }
            Pager(current, pageCount, { page = it })
        }
    }
}

@Composable
private fun RowScope.CountCell(label: String, count: Int) {
    Column(
        Modifier.weight(1f).fillMaxHeight().card(16, if (count > 0) Level.WARN.colors.bg else Paw.Tile),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(label, 13, color = Paw.InkSub, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        T("${count}회", 22, FontWeight.Bold, if (count > 0) Level.WARN.colors.text else Paw.Ink, lineHeight = 1.1f)
    }
}

/** 주행 안내 팝업. 바깥을 누르거나 "닫기"를 누르면 닫힌다. */
@Composable
private fun GuideDialog(s: DashboardState, onDismiss: () -> Unit) {
    val rest = s.trip.restInMin
    // 팝업은 별도 창이라 화면 배율(DesignSurface)을 이어받지 못한다 → 바깥의 배율을 그대로 넘겨준다.
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      CompositionLocalProvider(LocalDensity provides density) {
        Column(Modifier.width(560.dp).card(28, Paw.Card).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = Paw.BrandStrong, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                T("${s.petName}${josa(s.petName, "을", "를")} 위한 주행 안내", 22, FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tip(1, "정지 3초 전부터 감속", "브레이크를 나눠 밟으면 ${s.petName} 몸이 쏠리지 않아요.")
                Tip(2, "코너 전에 속도 낮추기", "회전 중에는 일정한 속도를 유지해 주세요.")
                when {
                    !s.trip.active -> Tip(3, "2시간마다 휴식", "주행이 길어지면 중간에 쉬어 가는 것이 좋아요.")
                    rest > 0 -> Tip(3, "${rest}분 뒤 휴식 권장", "누적 2시간이 되기 전에 쉬어 가면 좋아요.")
                    else -> Tip(3, "지금 휴식을 권해요", "주행한 지 2시간이 넘었어요. 가까운 곳에서 쉬어 가세요.")
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("닫기", Modifier.fillMaxWidth(), height = 56, onClick = onDismiss)
        }
      }
    }
}

@Composable
private fun Tip(n: Int, title: String, body: String) {
    Column(Modifier.fillMaxWidth().card(18, Paw.Tile).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Paw.BrandStrong), contentAlignment = Alignment.Center) {
                T("$n", 15, FontWeight.Bold, Color.White)
            }
            Spacer(Modifier.width(10.dp))
            T(title, 17, FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        T(body, 14, color = Paw.InkSub, modifier = Modifier.padding(start = 38.dp), lineHeight = 1.5f)
    }
}
