package com.pawssenger.app.ui.stay

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pawssenger.app.stay.BatteryStage
import com.pawssenger.app.stay.StayCare
import com.pawssenger.app.stay.StayCareController
import com.pawssenger.app.stay.StayContent
import com.pawssenger.app.stay.StayUiState
import com.pawssenger.app.ui.components.PawIcons
import com.pawssenger.app.ui.components.PillChip
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.ScreenHeader
import com.pawssenger.app.ui.components.StrokeIcon
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.theme.Paw
import kotlinx.coroutines.launch

/** 콘텐츠 파일 자리: app/src/main/res/raw/stay_video.mp4, stay_sound.mp3 (없으면 안내만 보여 준다) */
private const val RAW_VIDEO = "stay_video"
private const val RAW_SOUND = "stay_sound"
private const val HOLD_TO_END_MS = 1500

private val Warn = Color(0xFF8A4500)
private val Danger = Color(0xFFC62828)

@Composable
private fun rawId(name: String): Int {
    val context = LocalContext.current
    return remember(name) { context.resources.getIdentifier(name, "raw", context.packageName) }
}

// ───────────────────────── 설정 화면 ─────────────────────────

/** 주정차 케어 모드 설정: 목표 온도와 콘텐츠를 고르고 시작한다. */
@Composable
fun StayCareSetupScreen(s: StayUiState, cabinTempC: Float?, parked: Boolean, controller: StayCareController, onBack: () -> Unit, onStart: () -> Unit) {
    val hasVideo = rawId(RAW_VIDEO) != 0
    val hasSound = rawId(RAW_SOUND) != 0
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("주정차 케어 모드", onBack)
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 26.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 목표 온도
                Column(Modifier.weight(1f).fillMaxHeight().card().padding(28.dp)) {
                    CardTitle(PawIcons.Thermo, "실내 온도 유지")
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−", s.targetTempC > StayCare.MIN_TEMP) { controller.setTargetTemp(s.targetTempC - StayCare.TEMP_STEP) }
                        Row(Modifier.width(250.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                            T("%.1f".format(s.targetTempC), 84, FontWeight.Bold, lineHeight = 1.0f)
                            Spacer(Modifier.width(4.dp))
                            T("℃", 30, FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        StepButton("+", s.targetTempC < StayCare.MAX_TEMP) { controller.setTargetTemp(s.targetTempC + StayCare.TEMP_STEP) }
                    }
                    Spacer(Modifier.height(10.dp))
                    T("목표 온도", 15, color = Paw.InkSub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    InfoRow("현재 실내 온도", cabinTempC?.let { "%.1f℃".format(it) } ?: "-")
                    Spacer(Modifier.height(8.dp))
                    T("시작하면 공조를 켜고 목표 온도로 맞춰요. 끝내면 이전 설정으로 되돌려요.", 13, color = Paw.InkSub)
                }
                // 콘텐츠 + 배터리
                Column(Modifier.weight(1f).fillMaxHeight().card().padding(28.dp)) {
                    CardTitle(PawIcons.Speaker, "반려견을 위한 콘텐츠")
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StayContent.entries.forEach { c -> PillChip(c.label, s.content == c, { controller.setContent(c) }, height = 44) }
                    }
                    Spacer(Modifier.height(10.dp))
                    T(
                        when (s.content) {
                            StayContent.VIDEO -> if (hasVideo) "안내 문구 뒤로 영상을 반복해서 틀어요." else "영상 파일이 아직 없어요. 지금은 안내 화면만 보여요."
                            StayContent.SOUND -> if (hasSound) "화면에는 안내만 보이고 편안한 소리를 틀어요." else "사운드 파일이 아직 없어요. 지금은 안내 화면만 보여요."
                            StayContent.NONE -> "안내 문구와 실내 온도만 보여요."
                        }, 13, color = Paw.InkSub,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Paw.Divider))
                    Spacer(Modifier.height(16.dp))
                    InfoRow(
                        "배터리 잔량", s.batteryPct?.let { "$it%" } ?: "-",
                        when (s.stage) { BatteryStage.NORMAL -> Paw.Ink; BatteryStage.SAVE -> Warn; BatteryStage.CRITICAL -> Danger },
                    )
                    Spacer(Modifier.height(8.dp))
                    T("${StayCare.SAVE_PCT}% 이하: 영상을 끄고 사운드만 틀어요.", 13, color = if (s.stage == BatteryStage.SAVE) Warn else Paw.InkSub)
                    Spacer(Modifier.height(3.dp))
                    T("${StayCare.CRITICAL_PCT}% 이하: 콘텐츠를 끄고 공조와 안내만 유지해요.", 13, color = if (s.stage == BatteryStage.CRITICAL) Danger else Paw.InkSub)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(
                    when {
                        !parked -> "주차(P) 상태에서만 시작할 수 있어요."
                        !s.canStart -> "배터리가 ${StayCare.CRITICAL_PCT}% 이하라서 시작할 수 없어요."
                        else -> "차량이 켜져 있는 동안 동작해요. 차량 전원이 꺼지면 함께 멈춰요."
                    }, 14, color = if (parked && s.canStart) Paw.InkSub else Danger, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                PrimaryButton("시작", Modifier.width(280.dp), enabled = parked && s.canStart, height = 64, onClick = onStart)
            }
        }
    }
}

@Composable
private fun CardTitle(icon: List<String>, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StrokeIcon(icon, 30.dp, Paw.Brand)
        Spacer(Modifier.width(10.dp))
        T(title, 20, FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Paw.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, 16, FontWeight.Medium, modifier = Modifier.weight(1f))
        T(value, 20, FontWeight.Bold, color)
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(if (enabled) Paw.Tile else Color.Transparent)
            .border(1.5.dp, if (enabled) Paw.BrandLine else Paw.Lilac, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 40, FontWeight.Medium, if (enabled) Paw.BrandStrong else Paw.Lilac, lineHeight = 1.0f) }
}

// ───────────────────────── 실행 화면 ─────────────────────────

/** 주정차 케어 모드 실행 화면 (전체 화면). 밖에서도 읽히도록 큰 글씨로 안내와 실내 온도를 보여 준다. */
@Composable
fun StayCareRunScreen(s: StayUiState, cabinTempC: Float?, controller: StayCareController, onExit: () -> Unit) {
    // 기어가 P 에서 벗어나는 등으로 모드가 끝나면 화면도 닫는다.
    LaunchedEffect(s.running) { if (!s.running) onExit() }
    // 실행 중에는 화면이 꺼지지 않게 한다.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val videoId = rawId(RAW_VIDEO)
    val soundId = rawId(RAW_SOUND)
    val playing = s.playing
    val showVideo = playing == StayContent.VIDEO && videoId != 0

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241A4A), Color(0xFF4A35A0))))) {
        if (showVideo) {
            LoopingVideo(videoId, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x73120C2B)))   // 글씨가 읽히도록 어둡게 덮는다
        }
        if (playing == StayContent.SOUND && soundId != 0) LoopingSound(soundId)

        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            T("보호자가 곧 돌아와요", 68, FontWeight.Bold, Color.White, align = TextAlign.Center, lineHeight = 1.2f)
            Spacer(Modifier.height(10.dp))
            T("걱정하지 마세요! 실내 온도를 관리하고 있어요.", 30, FontWeight.Medium, Color(0xE6FFFFFF), align = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Row(
                Modifier.clip(RoundedCornerShape(36.dp)).background(Color(0x29FFFFFF)).border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(36.dp))
                    .padding(horizontal = 44.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeIcon(PawIcons.Thermo, 64.dp, Color.White, 1.6f)
                Spacer(Modifier.width(18.dp))
                Column {
                    T("현재 실내 온도", 18, FontWeight.Medium, Color(0xCCFFFFFF))
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(cabinTempC?.let { "%.1f".format(it) } ?: "-", 96, FontWeight.Bold, Color.White, lineHeight = 1.0f)
                        Spacer(Modifier.width(6.dp))
                        T("℃", 40, FontWeight.Medium, Color.White, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                Spacer(Modifier.width(44.dp))
                Box(Modifier.width(1.dp).height(96.dp).background(Color(0x4DFFFFFF)))
                Spacer(Modifier.width(44.dp))
                Column {
                    T("목표 온도", 18, FontWeight.Medium, Color(0xCCFFFFFF))
                    Spacer(Modifier.height(8.dp))
                    T("%.1f℃".format(s.targetTempC), 44, FontWeight.Bold, Color.White, lineHeight = 1.0f)
                }
            }
            Spacer(Modifier.height(22.dp))
            val notice = when {
                s.stage == BatteryStage.CRITICAL -> "배터리가 ${StayCare.CRITICAL_PCT}% 이하예요. 콘텐츠를 끄고 공조와 안내만 유지하고 있어요."
                s.stage == BatteryStage.SAVE && s.content == StayContent.VIDEO -> "배터리가 ${StayCare.SAVE_PCT}% 이하라서 영상을 끄고 사운드만 틀고 있어요."
                !s.hvacControlled -> "차량 공조를 제어하지 못했어요. 공조 상태를 직접 확인해 주세요."
                s.content == StayContent.VIDEO && videoId == 0 -> "영상 준비 중"
                s.content == StayContent.SOUND && soundId == 0 -> "사운드 준비 중"
                else -> null
            }
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                if (notice != null) {
                    val alert = s.stage != BatteryStage.NORMAL || !s.hvacControlled
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (alert) Color(0xFFFFE9C7) else Color(0x29FFFFFF)).padding(horizontal = 20.dp, vertical = 9.dp),
                    ) { T(notice, 16, FontWeight.Bold, if (alert) Warn else Color(0xCCFFFFFF)) }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 36.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            T("배터리 ${s.batteryPct?.let { "$it%" } ?: "-"}", 18, FontWeight.Bold, Color(0xCCFFFFFF), modifier = Modifier.weight(1f))
            HoldToEndButton { controller.stop() }
        }
    }
}

/** 길게 눌러야 동작하는 종료 버튼 (반려견이 화면을 건드려도 꺼지지 않게) */
@Composable
private fun HoldToEndButton(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier.width(240.dp).height(64.dp).clip(RoundedCornerShape(50)).background(Color(0x29FFFFFF))
            .border(1.5.dp, Color(0x80FFFFFF), RoundedCornerShape(50))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(HOLD_TO_END_MS, easing = LinearEasing))
                        end()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    progress.snapTo(0f)
                })
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color(0x66FFFFFF)))
        T("길게 눌러 종료", 18, FontWeight.Bold, Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun LoopingVideo(rawId: Int, modifier: Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            VideoView(it).apply {
                setOnPreparedListener { mp -> mp.isLooping = true; start() }
                setOnErrorListener { _, _, _ -> true }   // 재생하지 못해도 안내 화면은 유지한다
                setVideoURI(Uri.parse("android.resource://${context.packageName}/$rawId"))
            }
        },
        modifier = modifier,
        onRelease = { it.stopPlayback() },
    )
}

@Composable
private fun LoopingSound(rawId: Int) {
    val context = LocalContext.current
    DisposableEffect(rawId) {
        val player = runCatching { MediaPlayer.create(context, rawId)?.apply { isLooping = true; start() } }.getOrNull()
        onDispose { runCatching { player?.release() } }
    }
}
