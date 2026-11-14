package com.pawssenger.app.dashboard

import com.pawssenger.app.analysis.EventType
import com.pawssenger.app.analysis.ShakeAnalyzer
import com.pawssenger.app.comfort.ComfortCalculator
import com.pawssenger.app.comfort.ComfortInputs
import com.pawssenger.app.comfort.Level
import com.pawssenger.app.data.AppRepository
import com.pawssenger.app.data.PetSensorState
import com.pawssenger.app.drive.DriveSessionManager
import com.pawssenger.app.drive.DriveUiState
import com.pawssenger.app.drive.TripExtras
import com.pawssenger.app.vehicle.VehicleGateway
import com.pawssenger.app.vehicle.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 화면이 쓰는 [DashboardState] 를 만든다.
 * 입력: 차량 데이터([VehicleGateway]) + 주행 상태([DriveSessionManager]) + 가상 펫 센서 + 프로필.
 * 계산식은 [ComfortCalculator], 단계별 문구는 여기서 고른다.
 */
class DashboardEngine(
    private val scope: CoroutineScope,
    private val gateway: VehicleGateway,
    private val drive: DriveSessionManager,
    private val repository: AppRepository,
    private val cabinSimulator: CabinTempSimulator,
) {
    /**
     * 보호자가 이동장·하네스 타일을 눌렀을 때. 확인 ↔ 미확인을 바꾼다.
     * 주행 중(차가 움직이는 동안)에는 조작을 받지 않는다.
     * @return 반영했으면 true
     */
    fun toggleSafetyCheck(id: PetSafeId): Boolean {
        val d = drive.state.value
        val speed = gateway.state.value.speedKph.value ?: d.speedKph
        if (speed > CHECK_MAX_SPEED_KPH) return false
        when (id) {
            PetSafeId.CARRIER -> repository.updateSensors { it.copy(carrierLocked = !it.carrierLocked) }
            PetSafeId.HARNESS -> repository.updateSensors { it.copy(harnessFastened = !it.harnessFastened) }
            else -> return false
        }
        return true
    }

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // 현재 트립의 지수 변화 (elapsedMs, score). 메인 스레드에서만 접근.
    private val series = ArrayList<Pair<Long, Int>>()
    private var seriesTripId = -1
    private var safeStart = true
    private var worst: List<Level?> = List(4) { null }
    private var lastSampleWallMs = 0L

    init {
        drive.extrasProvider = {
            TripExtras(
                comfortScore = if (series.isEmpty()) _state.value.comfort.total else series.map { it.second }.average().roundToInt(),
                petSafe = worst,
                safeStart = safeStart,
                routeName = gateway.state.value.destinationName?.let { "→ $it" },
            )
        }
        val petName = combine(repository.pets, repository.selectedPetId) { pets, id ->
            (pets.firstOrNull { it.id == id } ?: pets.firstOrNull())?.name ?: "반려견"
        }
        scope.launch {
            // 시연 실내 온도가 바뀔 때도 화면이 갱신되도록 센서 흐름에 묶는다.
            val sensorsAndTemp = combine(repository.petSensors, cabinSimulator.temp) { s, t -> s to t }
            combine(gateway.state, drive.state, sensorsAndTemp, petName) { v, d, (sensors, simTemp), name ->
                buildLive(v, d, sensors, simTemp, name)
            }.conflate().collect {
                _state.value = it
                delay(UI_INTERVAL_MS) // 차량 데이터는 초당 10회 들어오지만 화면은 초당 4회면 충분하다
            }
        }
    }

    private fun buildLive(v: VehicleState, d: DriveUiState, sensors: PetSensorState, simTemp: Float?, pet: String): DashboardState {
        val speed = if (!v.speedKph.ok) d.speedKph else (v.speedKph.value ?: d.speedKph)
        // 시연 모드면 만들어 낸 온도, 아니면 차량이 주는 실제 실내 온도
        val temp = simTemp ?: v.hvacCabinTempC.takeIf { it.ok }?.value
        val vibration = if (d.active) d.vibration else null

        val windowValues = v.windows.values.mapNotNull { it.takeIf { f -> f.ok }?.value }
        val doorValues = v.doorLocked.values.mapNotNull { it.takeIf { f -> f.ok }?.value }
        val doorOpenValues = v.doorOpen.values.mapNotNull { it.takeIf { f -> f.ok }?.value }
        // 실내 소음은 센서가 없어 속도로 추정한다 (설정의 값은 정차 시 기준).
        val noiseDb = ComfortCalculator.estimateNoiseDb(sensors.noiseDb, speed)
        val petSafe = listOf(
            // 이동장·하네스는 센서가 없어 보호자가 출발 전에 타일을 눌러 확인한다. 미확인은 '주의'.
            if (sensors.carrierLocked) PetSafeItem(PetSafeId.CARRIER, "잠금", Level.GOOD) else PetSafeItem(PetSafeId.CARRIER, "확인 필요", Level.WARN),
            if (sensors.harnessFastened) PetSafeItem(PetSafeId.HARNESS, "체결", Level.GOOD) else PetSafeItem(PetSafeId.HARNESS, "확인 필요", Level.WARN),
            when {
                windowValues.isEmpty() -> PetSafeItem(PetSafeId.WINDOW, "미연동", null)
                windowValues.any { it.isOpen } -> PetSafeItem(PetSafeId.WINDOW, "열림", Level.WARN)
                else -> PetSafeItem(PetSafeId.WINDOW, "닫힘", Level.GOOD)
            },
            when {
                // 열림이 잠금 해제보다 우선한다: 문이 열려 있으면 잠금 상태와 무관하게 위험.
                doorOpenValues.any { it.isOpen } -> PetSafeItem(PetSafeId.DOOR, "열림", Level.BAD)
                doorValues.isEmpty() && doorOpenValues.isEmpty() -> PetSafeItem(PetSafeId.DOOR, "미연동", null)
                doorValues.isEmpty() -> PetSafeItem(PetSafeId.DOOR, "닫힘", Level.GOOD)
                doorValues.any { !it } -> PetSafeItem(PetSafeId.DOOR, "해제", Level.WARN)
                else -> PetSafeItem(PetSafeId.DOOR, "잠금", Level.GOOD)
            },
        )

        val comfort = ComfortCalculator.calculate(
            ComfortInputs(
                harshCount = d.harshCount, sharpTurnCount = d.sharpTurnCount, vibration = vibration,
                cabinTempC = temp, noiseDb = noiseDb, speedKph = speed,
                petSafe = petSafe.mapNotNull { it.level },
            )
        )
        val band = ComfortCalculator.vibrationBand(vibration)
        val tempLevel = ComfortCalculator.tempLevel(temp)
        val noiseLevel = ComfortCalculator.noiseLevel(noiseDb)
        val metrics = listOf(
            Metric(MetricId.TEMP, temp?.roundToInt()?.toString() ?: "-", "℃", tempLevel),
            Metric(MetricId.SPEED, speed.roundToInt().toString(), "km/h", ComfortCalculator.speedLevel(speed)),
            Metric(MetricId.VIBRATION, band.label, null, band.level),
            Metric(MetricId.HARSH, d.harshCount.toString(), "회", ComfortCalculator.harshLevel(d.harshCount)),
        )

        trackTrip(d, comfort.total, petSafe)
        sampleTrend(comfort.total)

        return DashboardState(
            petName = pet,
            comfort = comfort,
            headline = headlineFor(comfort.level, pet),
            advice = adviceFor(comfort.level, petSafe, metrics, noiseLevel, d.active, d.harshCount + d.sharpTurnCount > 0),
            metrics = metrics,
            petSafe = petSafe,
            cabinTemp = CabinValue(temp?.roundToInt(), tempLevel),
            cabinNoise = CabinValue(noiseDb, noiseLevel),
            trip = tripInfo(d, comfort.total),
        )
    }

    /** 가장 중요한 항목 하나를 골라 안내한다: 위험 펫 세이프 → 위험 지표 → 주의 펫 세이프 → 주의 지표. */
    private fun adviceFor(level: Level, safe: List<PetSafeItem>, metrics: List<Metric>, noise: Level, driving: Boolean, hadEvents: Boolean): String {
        fun safeMsg(i: PetSafeItem) = when (i.id) {
            PetSafeId.CARRIER -> "이동장 잠금을 확인한 뒤 이동장 버튼을 눌러 주세요."
            PetSafeId.HARNESS -> "하네스 체결을 확인한 뒤 하네스 버튼을 눌러 주세요."
            PetSafeId.WINDOW -> "창문이 열려 있어요. 창문을 닫아 주세요."
            PetSafeId.DOOR -> if (i.level == Level.BAD) "도어가 열려 있어요. 안전한 곳에 정차해 도어를 닫아 주세요." else "도어 잠금이 해제되어 있어요. 도어를 잠가 주세요."
        }
        fun metricMsg(m: Metric) = when (m.id) {
            MetricId.TEMP -> "실내 온도가 적정 범위를 벗어났어요. 온도를 조절해 주세요."
            MetricId.SPEED -> "속도가 높아요. 조금 천천히 주행해 주세요."
            MetricId.VIBRATION -> "차량 흔들림이 커요. 부드럽게 주행해 주세요."
            MetricId.HARSH -> "급가속·급제동이 반복됐어요. 여유 있게 가속하고 미리 감속해 주세요."
        }
        for (l in listOf(Level.BAD, Level.WARN)) {
            safe.firstOrNull { it.level == l }?.let { return safeMsg(it) }
            metrics.firstOrNull { it.level == l }?.let { return metricMsg(it) }
            if (noise == l) return "실내 소음이 커요. 음량을 줄이거나 창문을 닫아 주세요."
        }
        return when {
            level != Level.GOOD -> "주행 환경을 한 번 확인해 주세요."
            driving && !hadEvents -> "급가속과 급제동 없이 부드럽게 주행 중이에요."
            driving -> "대체로 부드럽게 주행하고 있어요."
            else -> "출발 준비가 잘 되어 있어요."
        }
    }

    private fun trackTrip(d: DriveUiState, score: Int, safe: List<PetSafeItem>) {
        if (!d.active) return
        val levels = safe.map { it.level }
        if (d.tripId != seriesTripId) {
            seriesTripId = d.tripId
            series.clear()
            safeStart = levels.all { it == null || it == Level.GOOD }
            worst = levels
        } else {
            worst = worst.zip(levels) { a, b -> if (a == null) b else if (b == null) a else if (b.ordinal > a.ordinal) b else a }
        }
        val last = series.lastOrNull()
        if (last == null || d.elapsedMs - last.first >= SERIES_STEP_MS) series += d.elapsedMs to score
    }

    private fun sampleTrend(score: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSampleWallMs < TREND_STEP_MS) return
        lastSampleWallMs = now
        repository.addComfortSample(now, score)
    }

    private fun tripInfo(d: DriveUiState, score: Int): TripInfo {
        if (!d.active) return TripInfo(false, 0, 0.0, emptyList(), emptyList(), emptyList(), EventCounts(), score, REST_AFTER_MIN)
        val span = d.elapsedMs.coerceAtLeast(1)
        val pts = series.map { TrendPoint((it.first.toFloat() / span).coerceIn(0f, 1f), it.second) } + TrendPoint(1f, score)
        val tripStart = d.lastSampleMs - d.elapsedMs
        val markers = d.events.map { e ->
            val x = ((e.timestampMs - tripStart).toFloat() / span).coerceIn(0f, 1f)
            TripMarker(x, pts.minByOrNull { kotlin.math.abs(it.x - x) }?.score ?: score, e.type.label)
        }
        // 개별 이벤트 목록 (최근 것부터). 시각은 샘플 시간 차이를 지금 시각에서 빼서 구한다.
        val now = System.currentTimeMillis()
        val clock = SimpleDateFormat("a h:mm", Locale.KOREAN)
        val events = d.events.asReversed().map { e ->
            val what = when (e.type) { EventType.HARD_BRAKE -> "감속"; EventType.HARD_ACCEL -> "가속"; EventType.SHARP_TURN -> "횡가속" }
            EventEntry(clock.format(Date(now - (d.lastSampleMs - e.timestampMs))), e.type.label, "$what ${e.gText()}")
        }
        val recentEvents = d.events.filter { d.lastSampleMs - it.timestampMs <= ShakeAnalyzer.RECENT_EVENT_WINDOW_MS }
        val counts = EventCounts(
            accel = recentEvents.count { it.type == EventType.HARD_ACCEL },
            brake = recentEvents.count { it.type == EventType.HARD_BRAKE },
            turn = recentEvents.count { it.type == EventType.SHARP_TURN },
        )
        val recent = series.filter { d.elapsedMs - it.first <= RECENT_WINDOW_MS }.map { it.second }
        return TripInfo(
            active = true,
            elapsedMin = (d.elapsedMs / 60_000).toInt(),
            distanceKm = d.distanceKm,
            series = pts,
            markers = markers,
            events = events,
            recentCounts = counts,
            recentAvg = if (recent.isEmpty()) score else recent.average().roundToInt(),
            restInMin = REST_AFTER_MIN - (d.drivingMs / 60_000).toInt(),
        )
    }

    /** 메인 추이 그래프: 최근 [hours] 시간의 지수 변화. */
    fun trend(hours: Int): Flow<List<TrendPoint>> {
        val windowMs = hours * 3_600_000L
        return repository.comfortSamplesSince(System.currentTimeMillis() - windowMs).map { rows ->
            val end = System.currentTimeMillis()
            val kept = rows.filter { it.first >= end - windowMs }
            // 기록이 창보다 짧으면(앱을 막 켠 직후 등) 쌓인 구간만큼만 펼쳐 그린다.
            val start = maxOf(end - windowMs, kept.firstOrNull()?.first ?: end)
            val span = (end - start).coerceAtLeast(1)
            kept.map { TrendPoint(((it.first - start).toFloat() / span).coerceIn(0f, 1f), it.second) }
        }
    }

    private companion object {
        const val UI_INTERVAL_MS = 250L
        const val CHECK_MAX_SPEED_KPH = 1f
        const val SERIES_STEP_MS = 5_000L
        const val TREND_STEP_MS = 30_000L
        const val RECENT_WINDOW_MS = 10 * 60_000L
        /** 연속 주행 2시간마다 휴식 권장 */
        const val REST_AFTER_MIN = 120
    }
}
