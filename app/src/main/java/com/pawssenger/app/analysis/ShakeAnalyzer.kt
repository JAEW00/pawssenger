package com.pawssenger.app.analysis

import com.pawssenger.app.data.VehicleSample
import kotlin.math.abs
import kotlin.math.sqrt

enum class EventType(val label: String) {
    HARD_BRAKE("급제동"), HARD_ACCEL("급가속"), SHARP_TURN("급회전")
}

/** 하나의 흔들림 구간. 임계값을 넘는 연속 샘플을 1건으로 묶는다. magnitude = 구간 내 최대 절대값(m/s²). */
data class DriveEvent(
    val type: EventType,
    val timestampMs: Long,
    val magnitude: Float,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * 급감속·급가속·급조향 감지 + 주행 진동 지표.
 * ※ 반려견의 멀미·스트레스를 진단하는 의료적 지표가 아님.
 * 임계값은 초기값이며 튜닝 대상.
 */
class ShakeAnalyzer(
    private val brakeThreshold: Float = -3.0f, // m/s^2
    private val accelThreshold: Float = 3.0f,
    /** 급회전 기준 횡가속도 (약 0.46g). 일상적인 차선 변경·완만한 커브는 넘지 않는 수준. */
    private val lateralThreshold: Float = 4.5f,
    /** 이 시간 이상 기준을 넘어야 급회전으로 본다 (핸들을 잠깐 움직였다 돌아오는 경우·값이 튀는 경우 제외). */
    private val lateralMinMs: Long = 500,
    private val vibrationWindowMs: Long = 30_000,
) {
    private var prev: VehicleSample? = null
    private var active: EventType? = null
    private var activeIndex = -1
    private val recent = ArrayDeque<Triple<Long, Float, Float>>() // (t, long, lat) — 이벤트 구간 제외
    private var lastSampleMs = 0L
    private var turnSinceMs: Long? = null

    /** 가장 최근 샘플의 종가속도(m/s², 감속은 음수). 화면 표시·기준값 조정용. */
    var lastLongAccel = 0f
        private set

    /** 가장 최근 샘플의 횡가속도(m/s²). */
    var lastLatAccel = 0f
        private set

    val events = mutableListOf<DriveEvent>()
    var distanceKm = 0.0
        private set
    var drivingMs = 0L
        private set

    /** 새 이벤트 구간이 시작될 때만 이벤트를 반환한다 (구간이 이어지는 동안은 null). */
    fun onSample(s: VehicleSample): DriveEvent? {
        val p = prev
        val long = s.longAccel ?: p?.let {
            val dt = (s.timestampMs - it.timestampMs) / 1000f
            if (dt > 0) ((s.speedKph - it.speedKph) / 3.6f) / dt else null
        }
        val lat = s.latAccel ?: 0f
        if (p != null) {
            val dtMs = s.timestampMs - p.timestampMs
            distanceKm += (s.speedKph + p.speedKph) / 2.0 * dtMs / 3_600_000.0
            if (s.speedKph > 1f) drivingMs += dtMs
        }
        prev = s
        lastSampleMs = s.timestampMs
        lastLongAccel = long ?: 0f
        lastLatAccel = lat

        // 급회전은 기준을 넘는 상태가 lateralMinMs 이상 이어져야 인정한다.
        val turning = abs(lat) >= lateralThreshold
        if (!turning) turnSinceMs = null else if (turnSinceMs == null) turnSinceMs = s.timestampMs
        val turnHeld = turning && s.timestampMs - (turnSinceMs ?: s.timestampMs) >= lateralMinMs

        val type = when {
            long != null && long <= brakeThreshold -> EventType.HARD_BRAKE
            long != null && long >= accelThreshold -> EventType.HARD_ACCEL
            turnHeld -> EventType.SHARP_TURN
            else -> null
        }
        val mag = when (type) {
            EventType.SHARP_TURN -> lat
            null -> 0f
            else -> long ?: 0f
        }

        // 급제동·급가속·급회전으로 이미 집계되는 구간은 진동 계산에서 뺀다.
        // (넣으면 급회전 한 번이 '횟수 감점'과 '진동 감점'을 동시에 일으킨다)
        if (type == null && !turning) recent.addLast(Triple(s.timestampMs, long ?: 0f, lat))
        while (recent.isNotEmpty() && s.timestampMs - recent.first().first > vibrationWindowMs) recent.removeFirst()

        if (type == null) { active = null; return null }
        if (type == active) {
            val cur = events[activeIndex]
            if (abs(mag) > abs(cur.magnitude)) events[activeIndex] = cur.copy(magnitude = mag)
            return null
        }
        val e = DriveEvent(type, s.timestampMs, mag, s.lat, s.lng)
        events += e
        active = type
        activeIndex = events.lastIndex
        return e
    }

    /**
     * 주행 진동 지표: 최근 [vibrationWindowMs] 동안 종·횡가속도의 흔들림(표준편차를 합성한 값, m/s²).
     * 진동 센서가 없어 가속도 변화로 대신한다. 노면의 잔진동은 잡지 못한다.
     * 급제동·급가속·급회전으로 집계된 구간은 포함하지 않는다.
     */
    fun vibration(): Float {
        if (recent.size < 5) return 0f
        fun sd(sel: (Triple<Long, Float, Float>) -> Float): Float {
            val m = recent.sumOf { sel(it).toDouble() } / recent.size
            return sqrt(recent.sumOf { (sel(it) - m) * (sel(it) - m) } / recent.size).toFloat()
        }
        val l = sd { it.second }
        val t = sd { it.third }
        return sqrt(l * l + t * t)
    }

    private fun recentEvents() = events.filter { lastSampleMs - it.timestampMs <= RECENT_EVENT_WINDOW_MS }

    /** 최근 10분 동안의 급가속 + 급제동 횟수 (오래된 이벤트 때문에 계속 '위험'으로 남지 않도록) */
    fun harshCount(): Int = recentEvents().count { it.type != EventType.SHARP_TURN }

    /** 최근 10분 동안의 급회전 횟수 */
    fun sharpTurnCount(): Int = recentEvents().count { it.type == EventType.SHARP_TURN }

    companion object {
        const val RECENT_EVENT_WINDOW_MS = 10 * 60_000L
    }
}
