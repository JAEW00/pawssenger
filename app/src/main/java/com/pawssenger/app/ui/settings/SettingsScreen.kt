package com.pawssenger.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pawssenger.app.PawssengerApp
import com.pawssenger.app.data.PetProfile
import com.pawssenger.app.drive.DriveUiState
import com.pawssenger.app.data.PetSize
import com.pawssenger.app.ui.components.OutlineButton
import com.pawssenger.app.ui.components.PillChip
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.ScreenHeader
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.vehicle.Feature
import com.pawssenger.app.vehicle.Link
import com.pawssenger.app.vehicle.VehicleState

@Composable
fun SettingsScreen(app: PawssengerApp, onBack: () -> Unit) {
    val repo = app.repository
    val pets by repo.pets.collectAsStateWithLifecycle()
    val selectedId by repo.selectedPetId.collectAsStateWithLifecycle()
    val tempDemo by repo.cabinTempDemo.collectAsStateWithLifecycle()
    val simTemp by app.cabinSimulator.temp.collectAsStateWithLifecycle()
    val drive by app.driveManager.state.collectAsStateWithLifecycle()
    val vehicle by app.vehicleGateway.state.collectAsStateWithLifecycle()
    val sessions by repo.sessions.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("설정", onBack)
        Row(
            Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 왼쪽: 반려견 프로필 + 실내 환경 + 예시 데이터 ──
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("반려견 프로필", Modifier.fillMaxWidth().weight(1f)) {
                var editing by remember { mutableStateOf<PetProfile?>(null) }
                var adding by remember { mutableStateOf(false) }
                T("선택한 반려견의 이름이 화면 안내 문구에 쓰여요.", 13, color = Paw.InkSub)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pets.forEach { pet ->
                        val sel = pet.id == selectedId || (selectedId == null && pet == pets.first())
                        if (editing?.id == pet.id) {
                            PetForm(pet, onCancel = { editing = null }) { n, b, s -> repo.updatePet(pet.id, n, b, s); editing = null }
                        } else Row(
                            Modifier.fillMaxWidth().height(68.dp).card(18, if (sel) Paw.Lilac else Paw.Tile)
                                .then(if (sel) Modifier.border(2.dp, Paw.BrandStrong, RoundedCornerShape(18.dp)) else Modifier)
                                .clickable { repo.selectPet(pet.id) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, Paw.BrandStrong, CircleShape), contentAlignment = Alignment.Center) {
                                if (sel) Box(Modifier.size(11.dp).clip(CircleShape).background(Paw.BrandStrong))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                T(pet.name, 17, FontWeight.Bold, maxLines = 1)
                                T("${pet.breed} · ${pet.size.label}", 13, color = Paw.InkSub, maxLines = 1)
                            }
                            SmallAction("수정") { editing = pet; adding = false }
                            if (pets.size > 1) { Spacer(Modifier.width(6.dp)); SmallAction("삭제") { repo.deletePet(pet.id) } }
                        }
                    }
                    if (adding) PetForm(null, onCancel = { adding = false }) { n, b, s -> repo.addPet(n, b, s); adding = false }
                }
                if (!adding) {
                    Spacer(Modifier.height(12.dp))
                    OutlineButton("+ 프로필 추가", Modifier.fillMaxWidth()) { adding = true; editing = null }
                }
            }

                Section("실내 환경", Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("실내 온도", 16, FontWeight.Medium, modifier = Modifier.width(92.dp))
                        PillChip("실제", !tempDemo, { repo.setCabinTempDemo(false) }, height = 36)
                        Spacer(Modifier.width(6.dp))
                        PillChip("시연", tempDemo, { repo.setCabinTempDemo(true) }, height = 36)
                        Spacer(Modifier.weight(1f))
                        T(
                            (if (tempDemo) simTemp else vehicle.hvacCabinTempC.value)?.let { "%.1f℃".format(it) } ?: "-",
                            16, FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    T(
                        if (tempDemo) "시연: 희망 온도(${vehicle.hvacTempSetC.value?.let { "%.0f℃".format(it) } ?: "-"})를 향해 서서히 변해요. 풍량이 셀수록 빨라요. 실제 측정값이 아니에요."
                        else "실제: 차량이 알려 주는 실내 온도를 그대로 써요.",
                        13, color = if (tempDemo) Paw.BrandStrong else Paw.InkSub,
                    )
                }
                Section("예시 데이터", Modifier.fillMaxWidth()) {
                    T("이동 리포트를 살펴볼 수 있도록 예시 주행 기록을 넣거나 지워요.", 13, color = Paw.InkSub)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("예시 기록 넣기", Modifier.weight(1f), height = 48) { repo.seedDemoSessions() }
                        OutlineButton("기록 지우기 (${sessions.size})", Modifier.weight(1f), height = 48, color = Color(0xFFC62828)) { repo.clearSessions() }
                    }
                }
            }

            // ── 오른쪽: 차량 연동 상태 ──
            Section("차량 연동 상태", Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { VehicleStatus(vehicle, drive) }
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.card().padding(22.dp)) {
        T(title, 18, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        T(text, 14, FontWeight.Medium, Paw.BrandStrong)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 16, FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Paw.BrandStrong, uncheckedTrackColor = Paw.Lilac, uncheckedBorderColor = Paw.Lilac))
    }
}

@Composable
private fun PetForm(initial: PetProfile?, onCancel: () -> Unit, onSave: (String, String, PetSize) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var breed by remember { mutableStateOf(initial?.breed ?: "") }
    var size by remember { mutableStateOf(initial?.size ?: PetSize.SMALL) }
    Column(Modifier.fillMaxWidth().card(18, Paw.Tile).border(2.dp, Paw.BrandLine, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("이름", name) { name = it.take(12) }
        Field("견종", breed) { breed = it.take(16) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PetSize.entries.forEach { s -> PillChip(s.label, size == s, { size = s }, height = 36) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("취소", Modifier.weight(1f), height = 44, onClick = onCancel)
            PrimaryButton("저장", Modifier.weight(1f), enabled = name.isNotBlank(), height = 44) {
                onSave(name.trim(), breed.trim().ifEmpty { "견종 미입력" }, size)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp).card(12, Color.White).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 13, color = Paw.InkSub, modifier = Modifier.width(44.dp))
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Paw.Ink, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun <V> StatusRow(name: String, f: Feature<V>, fmt: (V) -> String) {
    val color = when (f.link) {
        Link.OK -> Color(0xFF17592F); Link.UNSUPPORTED, Link.IDLE, Link.CONNECTING -> Paw.InkMuted; else -> Color(0xFF8A4500)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(name, 14, modifier = Modifier.width(96.dp), maxLines = 1)
        T(f.link.label, 12, FontWeight.Bold, color, modifier = Modifier.width(58.dp), maxLines = 1)
        T(f.value?.let(fmt) ?: (f.detail ?: "-"), 13, color = Paw.InkSub, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VehicleStatus(v: VehicleState, d: DriveUiState) {
    StatusRow("차량 속도", v.speedKph) { "%.0f km/h".format(it) }
    StatusRow("조향각", v.steeringAngleDeg) { "%.1f°".format(it) }
    StatusRow("기어", v.gear) { it.label }
    StatusRow("브레이크", v.brakePedal) { if (it) "밟음" else "뗌" }
    // 앱이 속도·핸들 각도로 계산한 값 (차량이 직접 주는 값이 아님). 주행 중에만 계산된다.
    val calc = if (d.active) Link.OK else Link.IDLE
    StatusRow("종가속도", Feature(calc, d.longAccel.takeIf { d.active })) { "%+.1f m/s²".format(it) }
    StatusRow("횡가속도", Feature(calc, d.latAccel.takeIf { d.active })) { "%+.1f m/s²".format(it) }
    StatusRow("주행 진동", Feature(calc, d.vibration.takeIf { d.active })) { "%.1f".format(it) }
    StatusRow("공조 전원", v.hvacPower) { if (it) "켜짐" else "꺼짐" }
    StatusRow("설정 온도", v.hvacTempSetC) { "%.1f℃".format(it) }
    StatusRow("실내 온도", v.hvacCabinTempC) { "%.1f℃".format(it) }
    StatusRow("공조 풍량", v.hvacFanSpeed) { "${it}단" }
    StatusRow("배터리", v.batteryPct) { "$it%" }
    val openWindows = v.windows.values.count { it.value?.isOpen == true }
    StatusRow("창문", Feature(if (v.windows.values.any { it.ok }) Link.OK else Link.UNSUPPORTED, v.windows.size)) { "${it}개 중 ${openWindows}개 열림" }
    val unlocked = v.doorLocked.values.count { it.value == false }
    StatusRow("도어 잠금", Feature(if (v.doorLocked.values.any { it.ok }) Link.OK else Link.UNSUPPORTED, v.doorLocked.size)) { "${it}개 중 ${unlocked}개 해제" }
    val openDoors = v.doorOpen.values.count { it.value?.isOpen == true }
    StatusRow("도어 열림", Feature(if (v.doorOpen.values.any { it.ok }) Link.OK else Link.UNSUPPORTED, v.doorOpen.size)) { "${it}개 중 ${openDoors}개 열림" }
    StatusRow("위치", v.location) { "%.4f, %.4f".format(it.lat, it.lng) }
    StatusRow("내비", v.navi) { if (it) "경로 안내 중${v.destinationName?.let { d -> " · $d" } ?: ""}" else "경로 없음" }
    StatusRow("트립 시간", v.tripTimeMin) { "${it}분" }
    StatusRow("트립 거리", v.tripDistanceM) { "%.0f m".format(it) }
}
