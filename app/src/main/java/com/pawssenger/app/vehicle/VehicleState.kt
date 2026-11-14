package com.pawssenger.app.vehicle

/** 차량 데이터 항목 하나의 연동 상태 */
enum class Link(val label: String) {
    IDLE("대기"),
    CONNECTING("연결 중"),
    OK("연동됨"),
    UNSUPPORTED("미지원"),
    NO_PERMISSION("권한 필요"),
    ERROR("오류"),
}

/** 값 + 연동 상태. [detail] 은 실패 사유나 범위 같은 부가 정보. */
data class Feature<T>(val link: Link = Link.IDLE, val value: T? = null, val detail: String? = null) {
    val ok: Boolean get() = link == Link.OK
}

enum class GearPos(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), OTHER("?") }

data class GeoPoint(val lat: Double, val lng: Double, val headingDeg: Float, val timestampMs: Long)

/** 창문 하나. [openRatio] 0 = 완전히 닫힘, 1 = 완전히 열림 (범위를 모르면 null). */
data class WindowInfo(val position: Int, val min: Int?, val max: Int?) {
    val openRatio: Float?
        get() = if (min != null && max != null && max > min) ((position - min).toFloat() / (max - min)).coerceIn(0f, 1f) else null
    val isOpen: Boolean get() = openRatio?.let { it > 0.02f } ?: (position != 0)
}

/** 좌석·창문 위치 이름 (SDK 상수명과 동일: ROW_1_LEFT, ROW_2_RIGHT …) */
typealias AreaName = String

data class VehicleState(
    // 주행
    val speedKph: Feature<Float> = Feature(),
    val steeringAngleDeg: Feature<Float> = Feature(),
    val gear: Feature<GearPos> = Feature(),
    val brakePedal: Feature<Boolean> = Feature(),
    val tripTimeMin: Feature<Int> = Feature(),
    val tripDistanceM: Feature<Float> = Feature(),
    // 탑승 환경
    val windows: Map<AreaName, Feature<WindowInfo>> = emptyMap(),
    val seatOccupied: Map<AreaName, Feature<Boolean>> = emptyMap(),
    val seatBelt: Map<AreaName, Feature<Boolean>> = emptyMap(),
    /** 도어별 잠금 여부 (true = 잠금) */
    val doorLocked: Map<AreaName, Feature<Boolean>> = emptyMap(),
    /** 도어별 열림 정도 (WindowInfo 와 같은 형식: position 이 min 보다 크면 열림) */
    val doorOpen: Map<AreaName, Feature<WindowInfo>> = emptyMap(),
    // 공조
    val hvacTempSetC: Feature<Float> = Feature(),
    val hvacCabinTempC: Feature<Float> = Feature(),
    val hvacFanSpeed: Feature<Int> = Feature(),
    /** 공조 전원 (꺼져 있으면 설정 온도·풍량을 올려도 실내 온도는 변하지 않는다) */
    val hvacPower: Feature<Boolean> = Feature(),
    /** 구동 배터리 잔량(%) */
    val batteryPct: Feature<Int> = Feature(),
    // 위치·내비
    val location: Feature<GeoPoint> = Feature(),
    /** value = 경로 안내 중 여부 */
    val navi: Feature<Boolean> = Feature(),
    /** 내비에 설정된 목적지 이름 (없으면 null) */
    val destinationName: String? = null,
    /** 내비에 설정된 목적지의 좌표 (없거나 좌표를 받지 못했으면 null). '목적지 주변' 장소를 찾는 기준점. */
    val destination: GeoPoint? = null,
    /** 내비게이션이 알고 있는 현재 위치. 목적지 좌표를 만들 때는 이 값을 기준으로 한다 (내비와 같은 좌표계). */
    val naviLocation: GeoPoint? = null,
)

/** 속도 수신 이벤트 1건 (주행 분석용) */
data class SpeedEvent(val timestampMs: Long, val speedKph: Float)
