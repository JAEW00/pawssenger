package com.pawssenger.app.analysis

import kotlin.math.abs
import kotlin.math.tan

/**
 * 차량이 가속도를 직접 주지 않으므로 속도·조향각에서 산출한다.
 *  - 종가속도: 최근 [windowMs] 구간의 속도 변화 / 시간. (10Hz 속도를 샘플 간 차분하면 양자화 잡음이 커서 구간 평균을 쓴다)
 *  - 횡가속도: 자전거 모델 a = v² · tan(δ) / L  (δ = 앞바퀴 조향각 = 핸들 각도 ÷ 조향비, L = 축거)
 */
class AccelEstimator(
    private val windowMs: Long = 1_000,
    private val wheelbaseM: Float = DEFAULT_WHEELBASE_M,
    private val steeringRatio: Float = DEFAULT_STEERING_RATIO,
) {
    private val history = ArrayDeque<Pair<Long, Float>>() // (timestampMs, speedKph)

    /** @return 종가속도 m/s² (감속은 음수). 구간이 아직 [windowMs] 의 절반도 안 찼으면 null. */
    fun longitudinal(timestampMs: Long, speedKph: Float): Float? {
        // 연속한 두 샘플 사이의 변화가 차로는 불가능한 크기면(약 1.2g 초과) 실제 가감속이 아니라
        // 값이 끊긴 것으로 본다 (에뮬레이터에서 속도를 한 번에 바꾸거나 신호가 튄 경우).
        // 그 지점을 가로질러 가속도를 계산하지 않도록 구간을 새로 시작한다.
        history.lastOrNull()?.let { (tPrev, vPrev) ->
            val dtPrev = (timestampMs - tPrev) / 1000f
            if (dtPrev > 0f && abs((speedKph - vPrev) / 3.6f) / dtPrev > MAX_STEP_ACCEL) history.clear()
        }
        history.addLast(timestampMs to speedKph)
        while (history.size > 2 && timestampMs - history[1].first >= windowMs) history.removeFirst()
        val (t0, v0) = history.first()
        val dt = (timestampMs - t0) / 1000f
        if (dt < windowMs / 2000f) return null
        return ((speedKph - v0) / 3.6f) / dt
    }

    /**
     * @param steeringWheelAngleDeg 핸들(스티어링 휠) 각도(도). 부호는 방향, 결과 부호도 같은 방향.
     * 차량이 주는 값은 앞바퀴 각도가 아니라 핸들 각도다 (에뮬레이터 범위 약 −470°~500°, QA 로 확인).
     * 조향비로 나눠 앞바퀴 각도로 바꾼 뒤 계산한다.
     */
    fun lateral(speedKph: Float, steeringWheelAngleDeg: Float): Float {
        val frontWheelAngleDeg = steeringWheelAngleDeg / steeringRatio
        val v = speedKph / 3.6f
        val delta = Math.toRadians(frontWheelAngleDeg.coerceIn(-MAX_WHEEL_ANGLE_DEG, MAX_WHEEL_ANGLE_DEG).toDouble())
        val a = (v * v * tan(delta) / wheelbaseM).toFloat()
        return if (abs(a) > MAX_LATERAL) MAX_LATERAL * if (a > 0) 1 else -1 else a
    }

    fun reset() = history.clear()

    companion object {
        /** 축거 가정값 (중형 SUV 기준). 차종별 값은 SDK 에서 얻을 수 없어 고정. */
        const val DEFAULT_WHEELBASE_M = 2.9f
        /** 조향비 가정값: 핸들 약 500° 에서 앞바퀴 약 35° (일반 승용차 14~16:1). 차종별 값은 얻을 수 없어 고정. */
        const val DEFAULT_STEERING_RATIO = 14.5f
        private const val MAX_WHEEL_ANGLE_DEG = 45f
        /** 연속 샘플 사이 가속도가 이 값(m/s², 약 1.2g)을 넘으면 실제 가감속이 아닌 값 끊김으로 본다. */
        private const val MAX_STEP_ACCEL = 12f
        /** 타이어 접지 한계를 넘는 값은 조향각 단위 불일치 등 입력 오류로 보고 자른다. */
        private const val MAX_LATERAL = 12f
    }
}
