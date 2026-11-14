package com.pawssenger.app.stay

import com.pawssenger.app.vehicle.GearPos
import com.pawssenger.app.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 주정차 케어 모드에서 틀어 줄 콘텐츠 */
enum class StayContent(val label: String) { VIDEO("영상"), SOUND("사운드만"), NONE("없음") }

/** 배터리 단계. 잔량이 줄수록 전기를 덜 쓰는 쪽으로 바꾼다. */
enum class BatteryStage { NORMAL, SAVE, CRITICAL }

data class StayUiState(
    val running: Boolean = false,
    val targetTempC: Float = StayCare.DEFAULT_TEMP,
    val content: StayContent = StayContent.VIDEO,
    /** 배터리 잔량(%). 차량이 알려 주지 않으면 null. */
    val batteryPct: Int? = null,
    /** 공조 제어 요청이 차량에 전달됐는지 (false 면 화면 안내만 한다) */
    val hvacControlled: Boolean = false,
) {
    val stage: BatteryStage get() = StayCare.stageOf(batteryPct)
    /** 배터리 단계를 반영해 실제로 트는 콘텐츠 */
    val playing: StayContent get() = StayCare.contentFor(content, stage)
    val canStart: Boolean get() = stage != BatteryStage.CRITICAL
}

/** 주정차 케어 모드의 기준값과 판정 (화면·차량과 무관한 순수 계산) */
object StayCare {
    const val MIN_TEMP = 15f
    const val MAX_TEMP = 30f
    const val TEMP_STEP = 0.5f
    const val DEFAULT_TEMP = 22f
    /** 이 값 이하면 영상을 끄고 사운드만 튼다 */
    const val SAVE_PCT = 20
    /** 이 값 이하면 콘텐츠를 모두 끄고 공조와 안내만 남긴다. 새로 시작할 수도 없다. */
    const val CRITICAL_PCT = 10

    fun stageOf(batteryPct: Int?): BatteryStage = when {
        batteryPct == null -> BatteryStage.NORMAL
        batteryPct <= CRITICAL_PCT -> BatteryStage.CRITICAL
        batteryPct <= SAVE_PCT -> BatteryStage.SAVE
        else -> BatteryStage.NORMAL
    }

    fun contentFor(choice: StayContent, stage: BatteryStage): StayContent = when (stage) {
        BatteryStage.NORMAL -> choice
        BatteryStage.SAVE -> if (choice == StayContent.VIDEO) StayContent.SOUND else choice
        BatteryStage.CRITICAL -> StayContent.NONE
    }

    fun clampTemp(c: Float): Float = (Math.round(c / TEMP_STEP) * TEMP_STEP).coerceIn(MIN_TEMP, MAX_TEMP)
}

/**
 * 주정차 케어 모드: 주차 중에 공조를 켜서 목표 온도를 유지하고, 안내 화면과 콘텐츠를 보여 준다.
 * 차량이 켜져 있는 동안만 동작한다 (차량 전원을 유지하는 것은 앱이 할 수 없다).
 */
class StayCareController(scope: CoroutineScope, private val gateway: VehicleGateway) {
    private val _state = MutableStateFlow(StayUiState())
    val state: StateFlow<StayUiState> = _state.asStateFlow()

    /** 시작 전 공조 상태. 종료할 때 되돌린다. */
    private var priorPower: Boolean? = null
    private var priorTempC: Float? = null

    init {
        scope.launch {
            gateway.state.collect { v ->
                _state.update { it.copy(batteryPct = v.batteryPct.takeIf { f -> f.ok }?.value) }
                // 기어가 P 에서 벗어나면 모드를 끝낸다.
                if (_state.value.running && v.gear.ok && v.gear.value != GearPos.PARK) stop()
            }
        }
    }

    fun setTargetTemp(c: Float) {
        val t = StayCare.clampTemp(c)
        _state.update { it.copy(targetTempC = t) }
        if (_state.value.running) gateway.setHvacTemperature(t)
    }

    fun setContent(content: StayContent) = _state.update { it.copy(content = content) }

    /** 모드를 시작한다. 주차 중이 아니거나 배터리가 부족하면 시작하지 않고 false. */
    fun start(): Boolean {
        val s = _state.value
        val v = gateway.state.value
        if (s.running) return true
        if (v.gear.value != GearPos.PARK || !s.canStart) return false
        priorPower = v.hvacPower.takeIf { it.ok }?.value
        priorTempC = v.hvacTempSetC.takeIf { it.ok }?.value
        _state.update { it.copy(running = true, hvacControlled = true) }
        // 전원이 켜졌다는 응답을 받은 뒤에 온도를 설정한다 (꺼진 상태에서는 온도 설정을 받지 않는 차량이 있을 수 있다).
        val sent = gateway.setHvacPower(true) { on ->
            // 응답이 오기 전에 모드가 끝났으면 아무것도 하지 않는다.
            if (_state.value.running) {
                val tempSet = on && gateway.setHvacTemperature(_state.value.targetTempC)
                _state.update { it.copy(hvacControlled = tempSet) }
            }
        }
        if (!sent) _state.update { it.copy(hvacControlled = false) }
        return true
    }

    /** 모드를 끝내고 공조를 시작 전 상태로 되돌린다. */
    fun stop() {
        if (!_state.value.running) return
        priorTempC?.let { gateway.setHvacTemperature(it) }
        priorPower?.let { gateway.setHvacPower(it) }
        priorPower = null
        priorTempC = null
        _state.update { it.copy(running = false, hvacControlled = false) }
    }
}
