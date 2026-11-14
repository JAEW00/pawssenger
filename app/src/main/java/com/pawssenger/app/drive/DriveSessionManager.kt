package com.pawssenger.app.drive

import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.analysis.ShakeAnalyzer
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.data.AppRepository
import com.pawssenger.app.data.DriveSessionRecord
import com.pawssenger.app.data.VehicleDataSource
import com.pawssenger.app.data.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriveUiState(
    /** 주행(트립) 진행 중인지. 실차는 시속 5km 이상으로 움직인 순간 시작된다. */
    val active: Boolean = false,
    /** 트립이 새로 시작될 때마다 1씩 증가 */
    val tripId: Int = 0,
    val speedKph: Float = 0f,
    val elapsedMs: Long = 0,
    val distanceKm: Double = 0.0,
    val drivingMs: Long = 0,
    val events: List<DriveEvent> = emptyList(),
    val harshCount: Int = 0,
    val sharpTurnCount: Int = 0,
    val vibration: Float = 0f,
    /** 가장 최근 종·횡가속도(m/s²). 주행 중이 아니면 0. */
    val longAccel: Float = 0f,
    val latAccel: Float = 0f,
    val lastSampleMs: Long = 0,
)

/** 주행 종료 시 기록에 함께 저장할 값. 대시보드 쪽에서 공급한다. */
data class TripExtras(
    val comfortScore: Int,
    val petSafe: List<Level?>,
    val safeStart: Boolean,
    val routeName: String?,
)

/**
 * 주행(트립) 자동 관리.
 * 항상 차량 데이터를 듣고 있다가 움직이기 시작하면 트립 시작, 정차 후 P단(또는 장시간 정지)이면 종료·저장.
 */
class DriveSessionManager(
    private val scope: CoroutineScope,
    private val repository: AppRepository,
    private val liveSource: () -> VehicleDataSource,
    private val isParked: () -> Boolean,
) {
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    var extrasProvider: (() -> TripExtras)? = null

    private var job: Job? = null
    private var analyzer = ShakeAnalyzer()
    private var movedAtMs = 0L
    private var startedAtMs = 0L
    private var slowSinceMs: Long? = null
    private var tripCounter = 0

    /** 차량 데이터를 듣기 시작한다. */
    fun startLive() {
        job?.cancel()
        saveTripIfAny()
        _state.value = DriveUiState(tripId = tripCounter)
        job = scope.launch { liveSource().samples().collect { onSample(it) } }
    }

    private fun onSample(s: VehicleSample) {
        if (!_state.value.active) {
            if (s.speedKph < START_SPEED_KPH) {
                _state.value = _state.value.copy(speedKph = s.speedKph, lastSampleMs = s.timestampMs)
                return
            }
            analyzer = ShakeAnalyzer()
            movedAtMs = s.timestampMs
            startedAtMs = System.currentTimeMillis()
            slowSinceMs = null
            tripCounter++
        }
        analyzer.onSample(s)
        _state.value = DriveUiState(
            active = true,
            tripId = tripCounter,
            speedKph = s.speedKph,
            elapsedMs = s.timestampMs - movedAtMs,
            distanceKm = analyzer.distanceKm,
            drivingMs = analyzer.drivingMs,
            events = analyzer.events.toList(),
            harshCount = analyzer.harshCount(),
            sharpTurnCount = analyzer.sharpTurnCount(),
            vibration = analyzer.vibration(),
            longAccel = analyzer.lastLongAccel,
            latAccel = analyzer.lastLatAccel,
            lastSampleMs = s.timestampMs,
        )
        checkLiveEnd(s)
    }

    /** 실차 트립 종료 판단: 멈춘 채 P단이면 바로, 아니면 오래 멈춰 있을 때. */
    private fun checkLiveEnd(s: VehicleSample) {
        if (s.speedKph > STOP_SPEED_KPH) { slowSinceMs = null; return }
        val since = slowSinceMs ?: s.timestampMs.also { slowSinceMs = it }
        val stoppedMs = s.timestampMs - since
        if ((isParked() && stoppedMs >= PARK_HOLD_MS) || stoppedMs >= IDLE_END_MS) endTrip()
    }

    /** 현재 트립을 끝내고 저장한다. 차량 데이터는 계속 듣는다. */
    fun endTrip() {
        saveTripIfAny()
        _state.value = DriveUiState(tripId = tripCounter, speedKph = _state.value.speedKph)
    }

    private fun saveTripIfAny() {
        val cur = _state.value
        if (!cur.active) return
        _state.value = cur.copy(active = false)
        repository.resetSafetyChecks()
        // 너무 짧은 이동(주차장 내 이동 등)은 기록하지 않는다.
        if (cur.distanceKm < MIN_TRIP_KM) return
        val extras = extrasProvider?.invoke()
        repository.addSession(
            DriveSessionRecord(
                id = startedAtMs,
                startedAtMs = startedAtMs,
                durationMs = cur.elapsedMs,
                distanceKm = cur.distanceKm,
                comfortScore = extras?.comfortScore ?: 100,
                // 이벤트 시각은 차량 내부 시간 기준이라 그대로 두면 나중에 시계 시각으로 바꿀 수 없다.
                // 출발 시각(시계) + 출발 후 경과 시간으로 바꿔서 저장한다.
                events = cur.events.map { it.copy(timestampMs = startedAtMs + (it.timestampMs - movedAtMs)) },
                petSafe = extras?.petSafe ?: List(4) { null },
                safeStart = extras?.safeStart ?: true,
                routeName = extras?.routeName,
            )
        )
    }

    private companion object {
        const val START_SPEED_KPH = 5f
        const val STOP_SPEED_KPH = 1f
        const val PARK_HOLD_MS = 3_000L
        const val IDLE_END_MS = 5 * 60_000L
        const val MIN_TRIP_KM = 0.3
    }
}
