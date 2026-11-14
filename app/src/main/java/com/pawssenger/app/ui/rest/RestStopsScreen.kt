package com.pawssenger.app.ui.rest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawssenger.app.data.AppRepository
import com.pawssenger.app.data.NearbyPlace
import com.pawssenger.app.data.PlaceType
import com.pawssenger.app.ui.components.PawIcons
import com.pawssenger.app.ui.components.PillChip
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.ScreenHeader
import com.pawssenger.app.ui.components.StrokeIcon
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.vehicle.VehicleGateway
import kotlinx.coroutines.delay

/** 어디를 기준으로 주변 장소를 찾을지 */
private enum class Around(val label: String) { ME("내 주변"), DESTINATION("목적지 주변") }

/**
 * 휴식 추천.
 *  - 내 주변(기본): 내비게이션의 현재 위치 주변 장소
 *  - 목적지 주변: 내비게이션에 설정된 목적지 주변 장소. 목적지가 없으면 내비 검색 화면에서 고르게 한다.
 * 왼쪽은 실제 지도(불러오지 못하면 약식 지도), 오른쪽은 가까운 순 목록이며 선택한 장소만 펼쳐진다.
 */
@Composable
fun RestStopsScreen(repository: AppRepository, gateway: VehicleGateway, onBack: () -> Unit) {
    val vehicle by gateway.state.collectAsStateWithLifecycle()
    var around by rememberSaveable { mutableStateOf(Around.ME) }
    var filter by rememberSaveable { mutableStateOf<PlaceType?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var mapUnavailable by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(4000); notice = null } }
    LaunchedEffect(Unit) { gateway.requestNaviLocation() }
    val context = LocalContext.current

    // 기준점: 내비게이션이 알고 있는 위치를 우선한다 (차량 위치 센서와 좌표가 다를 수 있다).
    val me = vehicle.naviLocation ?: vehicle.location.takeIf { it.ok }?.value
    val reference = if (around == Around.ME) me else vehicle.destination
    val places = remember(reference?.lat, reference?.lng, filter) {
        reference?.let { repository.placesNear(it.lat, it.lng, filter) } ?: emptyList()
    }
    // 선택한 장소가 목록에서 사라지면(기준·필터 변경) 가장 가까운 곳을 선택한다.
    LaunchedEffect(places) { if (places.none { it.place.id == selectedId }) selectedId = places.firstOrNull()?.place?.id }
    val selected = places.firstOrNull { it.place.id == selectedId }
    val fromLabel = if (around == Around.ME) "현재 위치" else "목적지"

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("휴식 추천", onBack, center = {
            Row(Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(Paw.Lilac).padding(3.dp)) {
                Around.entries.forEach { a ->
                    val on = a == around
                    Box(
                        Modifier.fillMaxHeight().clip(RoundedCornerShape(50)).background(if (on) Paw.BrandStrong else Color.Transparent)
                            .clickable { around = a }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { T(a.label, 15, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Color.White else Paw.BrandStrong, maxLines = 1) }
                }
            }
            Spacer(Modifier.width(8.dp))
            PillChip("전체", filter == null, { filter = null })
            PlaceType.entries.forEach { t -> PillChip(t.label, filter == t, { filter = t }) }
        }) { T("자료: 한국관광공사", 13, color = Paw.InkSub) }

        Row(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 지도 ──
            Box(Modifier.weight(800f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color(0xFFEEF1F6))) {
                // 목적지가 없을 때도 지도는 내 위치 기준으로 보여 준다.
                val center = reference ?: me
                when {
                    center == null -> T("위치를 확인하는 중이에요", 16, color = Paw.InkSub, modifier = Modifier.align(Alignment.Center))
                    mapUnavailable -> SketchMap(places, selectedId, Modifier.fillMaxSize()) { selectedId = it }
                    else -> PlaceMapView(
                        refLat = center.lat, refLng = center.lng, places = places, selectedId = selectedId,
                        modifier = Modifier.fillMaxSize(), onSelect = { selectedId = it }, onUnavailable = { mapUnavailable = true },
                    )
                }
                Box(
                    Modifier.padding(18.dp).height(34.dp).clip(RoundedCornerShape(50)).background(Paw.White90).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        when {
                            mapUnavailable -> "지도를 불러오지 못해 약식 지도로 보여드려요"
                            around == Around.DESTINATION && reference != null -> "목적지 기준 · ${vehicle.destinationName ?: "목적지"}"
                            else -> "현재 위치 기준"
                        }, 13, color = Paw.InkSub, maxLines = 1,
                    )
                }
            }

            // ── 목록 ──
            Column(Modifier.weight(400f).fillMaxHeight().card().padding(20.dp)) {
                val title = around.label
                // 목적지 이름은 길 수 있어 제목이 아니라 아랫줄에 쓴다.
                val fromName = if (around == Around.ME) "현재 위치" else (vehicle.destinationName ?: "목적지")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(title, 20, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (reference != null) { Spacer(Modifier.width(8.dp)); T("${places.size}곳", 15, FontWeight.Medium, Paw.BrandStrong) }
                    Spacer(Modifier.weight(1f))
                    if (around == Around.DESTINATION && reference != null) {
                        Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { gateway.openNaviSearch("") }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            T("목적지 변경", 13, FontWeight.Medium, Paw.BrandStrong)
                        }
                    }
                }
                T(if (reference != null) "${fromName}에서 가까운 순 (직선 거리)" else "", 13, color = Paw.InkSub, maxLines = 1)
                Spacer(Modifier.height(12.dp))

                when {
                    reference == null && around == Around.DESTINATION -> NoDestination { gateway.openNaviSearch("") }
                    reference == null -> Centered("현재 위치를 아직 받지 못했어요")
                    places.isEmpty() -> Centered("조건에 맞는 장소가 없어요")
                    else -> {
                        val listState = rememberLazyListState()
                        // 지도에서 핀을 눌렀을 때 그 장소가 보이도록 목록을 옮긴다.
                        LaunchedEffect(selectedId) {
                            val i = places.indexOfFirst { it.place.id == selectedId }
                            if (i >= 0) listState.animateScrollToItem(i)
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(places, key = { it.place.id }) { p ->
                                if (p.place.id == selectedId) {
                                    SelectedPlace(
                                        p, fromLabel, notice,
                                        onCopy = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("주소", p.place.address))
                                            notice = "주소를 복사했어요"
                                        },
                                        onNavigate = {
                                            notice = if (gateway.setDestination(p.place.lat, p.place.lng, p.place.name, p.place.address)) "내비게이션에 목적지를 보냈어요"
                                            else "내비게이션에 연결할 수 없어요"
                                        },
                                    )
                                } else PlaceRow(p) { selectedId = p.place.id }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(text, 16, color = Paw.InkSub, align = TextAlign.Center) }
}

/** 목적지 주변 모드인데 내비에 목적지가 없을 때 */
@Composable
private fun NoDestination(onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        StrokeIcon(PawIcons.Pin, 48.dp, Paw.BrandLine, 1.6f)
        Spacer(Modifier.height(14.dp))
        T("내비게이션에 목적지가 없어요", 18, FontWeight.Bold, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        T("목적지를 설정하면 그 주변에서\n반려견과 쉬어 갈 곳을 보여드려요.", 14, color = Paw.InkSub, align = TextAlign.Center, lineHeight = 1.5f)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            "내비에서 목적지 검색", Modifier.fillMaxWidth(), height = 56,
            leading = { Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
            onClick = onSearch,
        )
    }
}

@Composable
private fun PlaceRow(p: NearbyPlace, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Paw.Tile).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Paw.Lilac), contentAlignment = Alignment.Center) {
            Icon(p.place.type.icon(), null, tint = Paw.BrandStrong, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(p.place.name, 16, FontWeight.Bold, maxLines = 1)
            T(p.place.type.label, 12, color = Paw.InkSub, maxLines = 1)
        }
        T("${"%.1f".format(p.distanceKm)}km", 15, FontWeight.Medium, Paw.InkSub)
        Spacer(Modifier.width(6.dp))
        StrokeIcon(PawIcons.ChevronRight, 18.dp, Paw.Ink, 2f)
    }
}

/** 선택한 장소: 그 자리에서 펼쳐서 동반 조건·주소·내비 버튼을 보여 준다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedPlace(p: NearbyPlace, fromLabel: String, notice: String?, onCopy: () -> Unit, onNavigate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(2.dp, Paw.BrandStrong, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(p.place.type.label, Paw.Lilac, Paw.BrandStrong, bold = true)
            Spacer(Modifier.weight(1f))
            T("${fromLabel}에서 ${"%.1f".format(p.distanceKm)}km", 13, color = Paw.InkSub, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        T(p.place.name, 22, FontWeight.Bold, maxLines = 2)
        if (p.place.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.place.tags.forEach { Tag(it, Paw.Tile, Paw.BrandStrong) }
            }
        }
        // 반려동물 동반 정보 (있는 것만)
        if (p.place.petFacility.isNotBlank()) { Spacer(Modifier.height(10.dp)); InfoLine("보유 시설", p.place.petFacility, 2) }
        if (p.place.petNote.isNotBlank()) { Spacer(Modifier.height(6.dp)); InfoLine("유의사항", p.place.petNote, 3) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(48.dp).card(14, Paw.Tile).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StrokeIcon(PawIcons.Pin, 20.dp, Paw.Brand)
            Spacer(Modifier.width(8.dp))
            T(p.place.address, 13, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onCopy), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ContentCopy, "주소 복사", tint = Paw.BrandStrong, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            "내비게이션 목적지로 설정", Modifier.fillMaxWidth(), height = 54,
            leading = { Icon(Icons.Outlined.Navigation, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
            onClick = onNavigate,
        )
        if (notice != null) {
            Spacer(Modifier.height(8.dp))
            T(notice, 13, FontWeight.Medium, Paw.BrandStrong, maxLines = 1)
        }
    }
}

@Composable
private fun InfoLine(label: String, text: String, maxLines: Int) {
    Row {
        T(label, 12, FontWeight.Bold, Paw.InkSub, modifier = Modifier.width(58.dp))
        T(text, 12, color = Paw.InkSub, maxLines = maxLines, lineHeight = 1.45f, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color, bold: Boolean = false) {
    Box(Modifier.height(26.dp).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        T(text, 12, if (bold) FontWeight.Bold else FontWeight.Medium, fg, maxLines = 1)
    }
}
