package com.pawssenger.app.dashboard

import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.comfort.ComfortBreakdown
import com.pawssenger.app.comfort.Level

enum class PetSafeId(val label: String) { CARRIER("이동장"), HARNESS("하네스"), WINDOW("창문"), DOOR("도어") }

/** [level] 이 null 이면 데이터 미연동(판단 불가) */
data class PetSafeItem(val id: PetSafeId, val state: String, val level: Level?)

enum class MetricId(val label: String) { TEMP("실내 온도"), SPEED("차량 속도"), VIBRATION("주행 진동"), HARSH("급가속·급제동") }

data class Metric(val id: MetricId, val value: String, val unit: String?, val level: Level)

data class CabinValue(val value: Int?, val level: Level)

/** 추이 그래프의 한 점. [x] 는 0..1 로 정규화한 가로 위치. */
data class TrendPoint(val x: Float, val score: Int)

/** 주행 케어 그래프의 이벤트 표시 */
data class TripMarker(val x: Float, val score: Int, val label: String)

/** 감지된 이벤트 1건 (주행 케어의 목록 한 줄) */
data class EventEntry(val time: String, val title: String, val detail: String)

/** 종류별 이벤트 횟수 (최근 10분) */
data class EventCounts(val accel: Int = 0, val brake: Int = 0, val turn: Int = 0)

data class TripInfo(
    val active: Boolean,
    val elapsedMin: Int,
    val distanceKm: Double,
    /** 출발 후 지수 변화 */
    val series: List<TrendPoint>,
    val markers: List<TripMarker>,
    /** 이번 주행에서 감지된 이벤트, 최근 것부터 */
    val events: List<EventEntry>,
    /** 최근 10분 동안의 종류별 횟수 */
    val recentCounts: EventCounts,
    val recentAvg: Int,
    /** 다음 휴식 권장까지 남은 시간(분). 0 이하면 지금 권장. */
    val restInMin: Int,
)

data class DashboardState(
    val petName: String = "반려견",
    val comfort: ComfortBreakdown = ComfortBreakdown(100, 100, 100, 100, 100),
    val headline: String = "",
    val advice: String = "",
    val metrics: List<Metric> = emptyList(),
    val petSafe: List<PetSafeItem> = emptyList(),
    val cabinTemp: CabinValue = CabinValue(null, Level.GOOD),
    val cabinNoise: CabinValue = CabinValue(null, Level.GOOD),
    val trip: TripInfo = TripInfo(false, 0, 0.0, emptyList(), emptyList(), emptyList(), EventCounts(), 100, 120),
) {
    val level: Level get() = comfort.level
    val petSafeWorst: Level get() = Level.worst(petSafe.mapNotNull { it.level })
    val petSafeIssueCount: Int get() = petSafe.count { it.level == petSafeWorst && petSafeWorst != Level.GOOD }
}

fun headlineFor(level: Level, pet: String): String = when (level) {
    Level.GOOD -> "${pet}${josa(pet, "이", "가")} 편안하게 이동하고 있어요"
    Level.WARN -> "${pet}${josa(pet, "이", "가")} 조금 불편할 수 있어요"
    Level.BAD -> "${pet}${josa(pet, "이", "가")} 불안해하고 있어요"
}

fun badgeFor(level: Level): String = when (level) {
    Level.GOOD -> "안정적인 주행"
    Level.WARN -> "주의가 필요해요"
    Level.BAD -> "즉시 확인 필요"
}

/** 받침 유무에 따라 조사를 고른다 (한글이 아니면 받침 없는 쪽). */
fun josa(word: String, withBatchim: String, without: String): String {
    val c = word.lastOrNull() ?: return without
    if (c !in '가'..'힣') return without
    return if ((c - '가') % 28 != 0) withBatchim else without
}

internal fun DriveEvent.gText(): String = "%.2fg".format(kotlin.math.abs(magnitude) / 9.81f)
