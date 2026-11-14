package com.pawssenger.app.dashboard

import com.pawssenger.app.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * 시연용 실내 온도.
 *
 * Pleos 에뮬레이터는 히터·에어컨을 틀어도 실내 온도 값(HVAC_TEMPERATURE_CURRENT)을 바꾸지 않는다.
 * 시연 모드에서는 실내 온도가 희망(설정) 온도를 향해 서서히 따라가는 값을 만들어 쓴다.
 *  - 풍량이 셀수록 빨리 변한다.
 *  - 공조 전원이 꺼지면 차량이 알려 준 실제 실내 온도로 천천히 돌아간다.
 * 실제 측정값이 아니므로 실차에서는 '실제' 모드를 써야 한다.
 */
class CabinTempSimulator(
    scope: CoroutineScope,
    private val gateway: VehicleGateway,
    private val enabled: StateFlow<Boolean>,
) {
    private val _temp = MutableStateFlow<Float?>(null)
    /** 시연 모드일 때의 실내 온도(℃). 실제 모드면 null. */
    val temp: StateFlow<Float?> = _temp.asStateFlow()

    init {
        scope.launch {
            while (true) {
                step()
                delay(STEP_MS)
            }
        }
    }

    private fun step() {
        if (!enabled.value) { _temp.value = null; return }
        val v = gateway.state.value
        val actual = v.hvacCabinTempC.takeIf { it.ok }?.value ?: DEFAULT_TEMP
        val current = _temp.value ?: actual
        val powerOn = v.hvacPower.takeIf { it.ok }?.value ?: true
        val setTemp = v.hvacTempSetC.takeIf { it.ok }?.value
        val target = if (powerOn && setTemp != null) setTemp else actual
        val tau = if (powerOn) tauSeconds(v.hvacFanSpeed.takeIf { it.ok }?.value) else TAU_OFF_S
        _temp.value = next(current, target, tau, STEP_MS / 1000f)
    }

    companion object {
        private const val STEP_MS = 1_000L
        private const val DEFAULT_TEMP = 20f
        private const val TAU_FAST_S = 20f   // 풍량 최대
        private const val TAU_SLOW_S = 60f   // 풍량 최소
        private const val TAU_OFF_S = 120f   // 공조 꺼짐: 원래 온도로 복귀
        private const val MAX_FAN = 8

        /** 풍량(1~8) → 시정수(초). 풍량을 모르면 중간값. */
        fun tauSeconds(fan: Int?): Float {
            val f = ((fan ?: 4).coerceIn(1, MAX_FAN) - 1) / (MAX_FAN - 1f)
            return TAU_SLOW_S + (TAU_FAST_S - TAU_SLOW_S) * f
        }

        /** 1차 지연: 목표와의 차이가 [tauSec] 초마다 약 63% 줄어든다. */
        fun next(current: Float, target: Float, tauSec: Float, dtSec: Float): Float {
            val n = current + (target - current) * (1f - exp(-dtSec / tauSec))
            return if (abs(target - n) < 0.05f) target else n
        }
    }
}
