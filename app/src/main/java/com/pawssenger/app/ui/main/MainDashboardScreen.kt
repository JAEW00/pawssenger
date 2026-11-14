package com.pawssenger.app.ui.main

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawssenger.app.R
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.dashboard.CabinValue
import com.pawssenger.app.dashboard.DashboardEngine
import com.pawssenger.app.dashboard.DashboardState
import com.pawssenger.app.dashboard.Metric
import com.pawssenger.app.dashboard.MetricId
import com.pawssenger.app.dashboard.PetSafeId
import com.pawssenger.app.dashboard.PetSafeItem
import com.pawssenger.app.dashboard.badgeFor
import com.pawssenger.app.ui.Route
import com.pawssenger.app.ui.components.LevelChip
import com.pawssenger.app.ui.components.LevelIcon
import com.pawssenger.app.ui.components.PawIcons
import com.pawssenger.app.ui.components.StrokeIcon
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.TrendLine
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.components.levelBorder
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.colors

private val PERIODS = listOf(1, 3, 6, 12)

/** 메인 대시보드. 안정/주의/위험 세 모습은 같은 화면이 [DashboardState] 에 따라 바뀐 것이다. */
@Composable
fun MainDashboardScreen(state: DashboardState, engine: DashboardEngine, parked: Boolean, onOpen: (Route) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 26.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ComfortCard(state, engine, Modifier.weight(856f).fillMaxHeight())
            PetSafeCard(state, parked, Modifier.weight(346f).fillMaxHeight(), { onOpen(Route.StaySetup) }) { engine.toggleSafetyCheck(it) }
        }
        Spacer(Modifier.height(20.dp))
        T("주요 서비스 바로가기", 16, FontWeight.Medium, modifier = Modifier.padding(start = 2.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Shortcut("주행 케어", "주행 상태 분석 및 안내", { StrokeIcon(PawIcons.CarFront, 44.dp, Paw.Brand, 1.7f) }) { onOpen(Route.DriveCare) }
            Shortcut("휴식 추천", "반려견 동반 휴식 장소", { StrokeIcon(PawIcons.Pin, 44.dp, Paw.Brand, 1.7f) }) { onOpen(Route.Rest) }
            Shortcut("이동 리포트", "주행 기록 및 분석 결과", { BarsIcon() }) { onOpen(Route.Report) }
        }
    }
}

// ───────────────────────── 왼쪽: 실시간 펫 편안함 지수 ─────────────────────────

@Composable
private fun ComfortCard(s: DashboardState, engine: DashboardEngine, modifier: Modifier) {
    val level = s.level
    val scoreColor = if (level == Level.GOOD) Paw.Ink else level.colors.text
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    val trendFlow = remember(periodIndex) { engine.trend(PERIODS[periodIndex]) }
    val trend by trendFlow.collectAsState(initial = emptyList())
    val photo = when (level) { Level.GOOD -> R.drawable.bori_good; Level.WARN -> R.drawable.bori_warn; Level.BAD -> R.drawable.bori_bad }

    Column(modifier.card(28, Paw.Card).border(1.dp, Color.White, RoundedCornerShape(28.dp))) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // 사진: 오른쪽에 두고 왼쪽·아래쪽을 카드 배경색으로 흐린다
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.585f).fillMaxHeight()) {
                Image(painterResource(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(0.36f)
                        .background(Brush.horizontalGradient(0f to Paw.Card, 0.3f to Paw.Card, 1f to Paw.Card.copy(alpha = 0f)))
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.36f)
                        .background(Brush.verticalGradient(0f to Paw.Card.copy(alpha = 0f), 0.75f to Paw.Card, 1f to Paw.Card))
                )
            }
            Column(Modifier.fillMaxSize().padding(start = 30.dp, top = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StrokeIcon(PawIcons.Pulse, 30.dp, Paw.Brand, 2f)
                    Spacer(Modifier.width(10.dp))
                    T("실시간 펫 편안함 지수", 20, FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${s.comfort.total}", 104, FontWeight.Bold, scoreColor, lineHeight = 1.0f)
                    Spacer(Modifier.width(6.dp))
                    T("점", 34, FontWeight.Medium, scoreColor, modifier = Modifier.padding(bottom = 12.dp))
                    Spacer(Modifier.width(40.dp))
                    Row(
                        Modifier.padding(bottom = 16.dp).height(48.dp).clip(RoundedCornerShape(50)).background(level.colors.bg)
                            .border(1.dp, Color.White, RoundedCornerShape(50)).padding(start = 14.dp, end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LevelIcon(level, 24.dp)
                        Spacer(Modifier.width(10.dp))
                        T(badgeFor(level), 18, FontWeight.Bold, level.colors.text)
                    }
                }
                Spacer(Modifier.height(10.dp))
                T(s.headline, 18, FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                T(s.advice, 14, color = Paw.InkSub, modifier = Modifier.fillMaxWidth(0.52f), maxLines = 2)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleChip("안정 90점 이상", Level.GOOD, level)
                    ScaleChip("주의 71~89점", Level.WARN, level)
                    ScaleChip("위험 70점 이하", Level.BAD, level)
                }
                Spacer(Modifier.weight(1.3f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(86.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            s.metrics.forEach { MetricTile(it) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 26.dp).height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelChip(level, if (level == Level.GOOD) "안정적" else level.label, height = 36, fontSize = 15, dot = true)
            Spacer(Modifier.width(18.dp))
            TrendLine(trend, level, s.comfort.total, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(50)).background(Paw.White90).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PERIODS.forEachIndexed { i, h ->
                    val sel = i == periodIndex
                    Box(
                        Modifier.width(48.dp).height(44.dp).clip(RoundedCornerShape(50)).background(if (sel) Paw.BrandStrong else Color.Transparent)
                            .clickable { periodIndex = i },
                        contentAlignment = Alignment.Center,
                    ) { T("${h}h", 15, if (sel) FontWeight.Bold else FontWeight.Medium, if (sel) Color.White else Paw.InkSub) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ScaleChip(text: String, chip: Level, current: Level) {
    val sel = chip == current
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (sel) chip.colors.accent else Color.White)
            .then(if (sel) Modifier else Modifier.border(1.dp, Paw.Divider, RoundedCornerShape(50))).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 13, if (sel) FontWeight.Bold else FontWeight.Medium, if (sel) Color.White else Paw.InkSub, maxLines = 1) }
}

@Composable
private fun RowScope.MetricTile(m: Metric) {
    val icon = when (m.id) {
        MetricId.TEMP -> PawIcons.Thermo; MetricId.SPEED -> PawIcons.Gauge
        MetricId.VIBRATION -> PawIcons.Waves; MetricId.HARSH -> PawIcons.Bolt
    }
    Box(Modifier.weight(1f).fillMaxHeight().card(20, Color.White).levelBorder(m.level, 20)) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StrokeIcon(icon, 32.dp, Paw.Brand)
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    T(m.value, 26, FontWeight.Bold, lineHeight = 1.1f, maxLines = 1)
                    if (m.unit != null) {
                        Spacer(Modifier.width(2.dp))
                        T(m.unit, 15, FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(2.dp))
                T(m.id.label, 13, color = Paw.InkSub, maxLines = 1)
            }
        }
        LevelChip(m.level, modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp))
    }
}

// ───────────────────────── 오른쪽: 펫 세이프 대시보드 ─────────────────────────

@Composable
private fun PetSafeCard(s: DashboardState, parked: Boolean, modifier: Modifier, onStay: () -> Unit, onCheck: (PetSafeId) -> Unit) {
    val worst = s.petSafeWorst
    Column(modifier.card(28, Paw.White90).levelBorder(worst, 28, 2f).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            StrokeIcon(PawIcons.Shield, 30.dp, Paw.Brand)
            Spacer(Modifier.width(8.dp))
            T("펫 세이프 대시보드", 18, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            LevelChip(worst, if (worst == Level.GOOD) "정상" else "${worst.label} ${s.petSafeIssueCount}건", height = 30, fontSize = 14, dot = true)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            s.petSafe.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { PetSafeTile(it, onCheck) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StayCareButton(parked, onStay)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
        Row(Modifier.fillMaxWidth().height(74.dp), verticalAlignment = Alignment.CenterVertically) {
            CabinItem(PawIcons.Thermo, "실내 온도", s.cabinTemp, "℃", Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(48.dp).background(Paw.Divider))
            Spacer(Modifier.width(12.dp))
            CabinItem(PawIcons.Speaker, "실내 소음", s.cabinNoise, "dB", Modifier.weight(1f))
        }
    }
}

/** 주정차 케어 모드 진입. 주차(P) 중에만 누를 수 있다. */
@Composable
private fun StayCareButton(parked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(if (parked) Paw.BrandStrong else Paw.Lilac)
            .clickable(enabled = parked, onClick = onClick).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeIcon(PawIcons.Thermo, 26.dp, if (parked) Color.White else Paw.InkMuted)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T("주정차 케어 모드", 16, FontWeight.Bold, if (parked) Color.White else Paw.InkMuted, maxLines = 1)
            T(if (parked) "잠시 내릴 때 실내 온도 유지" else "주차 후 사용할 수 있어요", 12, color = if (parked) Color(0xD9FFFFFF) else Paw.InkMuted, maxLines = 1)
        }
        StrokeIcon(PawIcons.ChevronRight, 20.dp, if (parked) Color.White else Paw.InkMuted, 2f)
    }
}

@Composable
private fun RowScope.PetSafeTile(item: PetSafeItem, onCheck: (PetSafeId) -> Unit) {
    val level = item.level
    val alert = level != null && level != Level.GOOD
    val icon = when (item.id) {
        PetSafeId.CARRIER -> PawIcons.Carrier; PetSafeId.HARNESS -> PawIcons.Harness
        PetSafeId.WINDOW -> PawIcons.CarWindow; PetSafeId.DOOR -> PawIcons.CarDoor
    }
    Column(
        Modifier.weight(1f).fillMaxHeight().card(18, if (alert) level!!.colors.bg else Paw.Tile).levelBorder(level, 18)
            // 이동장·하네스는 보호자가 눌러서 확인하는 항목
            .then(if (item.id == PetSafeId.CARRIER || item.id == PetSafeId.HARNESS) Modifier.clickable { onCheck(item.id) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        StrokeIcon(icon, 44.dp, if (alert) level!!.colors.text else Paw.IconDark, 1.7f)
        Spacer(Modifier.height(12.dp))
        T(item.id.label, 17, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (level != null) {
                LevelIcon(level, 18.dp)
                Spacer(Modifier.width(6.dp))
                T(item.state, 16, FontWeight.Bold, level.colors.text)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Paw.InkMuted))
                Spacer(Modifier.width(6.dp))
                T(item.state, 15, FontWeight.Medium, Paw.InkMuted)
            }
        }
        if ((item.id == PetSafeId.CARRIER || item.id == PetSafeId.HARNESS) && level != Level.GOOD) {
            Spacer(Modifier.height(6.dp))
            T("확인했으면 눌러 주세요", 12, color = Paw.InkSub)
        }
    }
}

@Composable
private fun CabinItem(icon: List<String>, label: String, v: CabinValue, unit: String, modifier: Modifier) {
    val color = if (v.level == Level.GOOD) Paw.Ink else v.level.colors.text
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StrokeIcon(icon, 30.dp, Paw.Brand)
        Spacer(Modifier.width(8.dp))
        Column {
            T(label, 12, color = Paw.InkSub, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                T(v.value?.toString() ?: "-", 22, FontWeight.Bold, color, lineHeight = 1.1f)
                Spacer(Modifier.width(3.dp))
                T(unit, 13, FontWeight.Medium, color, modifier = Modifier.padding(bottom = 2.dp))
                if (v.level != Level.GOOD) {
                    Spacer(Modifier.width(8.dp))
                    LevelChip(v.level, height = 22, fontSize = 11)
                }
            }
        }
    }
}

// ───────────────────────── 하단: 바로가기 ─────────────────────────

@Composable
private fun RowScope.Shortcut(title: String, sub: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).fillMaxHeight().card(20, Paw.White90).border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(start = 26.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            T(title, 18, FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            T(sub, 14, color = Paw.InkSub, maxLines = 1)
        }
        StrokeIcon(PawIcons.ChevronRight, 22.dp, Paw.Ink, 2f)
    }
}

@Composable
private fun BarsIcon() {
    Row(Modifier.size(44.dp).padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(16, 26, 36).forEach { h -> Box(Modifier.width(9.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(Paw.BrandLine)) }
    }
}
