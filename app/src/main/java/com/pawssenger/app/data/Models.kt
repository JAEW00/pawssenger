package com.pawssenger.app.data

import com.pawssenger.app.analysis.DriveEvent
import com.pawssenger.app.comfort.Level

enum class PetSize(val label: String) { SMALL("소형"), MEDIUM("중형"), LARGE("대형") }

data class PetProfile(
    val id: Long,
    val name: String,
    val breed: String,
    val size: PetSize,
)

/** 휴식 장소 분류 (한국관광공사 콘텐츠 유형 기준) */
enum class PlaceType(val label: String) {
    ATTRACTION("관광지"), FOOD("음식·카페"), STAY("숙박"), LEISURE("레포츠·문화")
}

/**
 * 반려동물 동반 가능 장소. 한국관광공사 반려동물 동반여행 데이터에서 온 실제 장소다.
 * (원본: docs/data, 변환: tools/build_places.py → assets/places.json)
 */
data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val lat: Double,
    val lng: Double,
    val address: String,
    val tel: String,
    /** 동반 가능 구역 (예: "전구역 동반가능", "일부구역 동반가능") */
    val petArea: String,
    /** 동반 가능 동물·크기 (예: "전 견종 동반 가능", "소형견") */
    val petWho: String,
    /** 필요 사항 (예: "목줄 착용, 입마개 착용") */
    val petNeed: String,
    /** 유의사항 */
    val petNote: String,
    /** 보유 시설 (예: "어질리티, 배변공간, 음수대") */
    val petFacility: String,
    /** 비치 품목 */
    val petSupply: String,
) {
    /** 카드에 보여 줄 짧은 태그: 동반 구역, 동반 가능 크기, 필요 사항 */
    val tags: List<String>
        get() = (listOf(petArea, petWho.takeIf { it.length <= MAX_TAG_LEN }.orEmpty()) +
            petNeed.split(",").map { it.trim() }.filter { it != "기타" })
            .filter { it.isNotBlank() }.distinct().take(4)

    private companion object { const val MAX_TAG_LEN = 16 }
}

/** 기준점에서 본 장소: 직선 거리와 동쪽·북쪽 방향 거리(km). */
data class NearbyPlace(
    val place: Place,
    val distanceKm: Double,
    val eastKm: Double,
    val northKm: Double,
)

/**
 * 차량이 알 수 없는 펫 안전 항목.
 *  - 이동장·하네스: 보호자가 출발 전에 직접 확인하고 대시보드 타일을 눌러 표시한다. 주행이 끝나면 다시 미확인이 된다.
 *  - 실내 소음: 차량 API 가 없어 속도로 추정한다. [noiseDb] 는 정차 시 기준 소음(45dB 고정).
 */
data class PetSensorState(
    val carrierLocked: Boolean = false,
    val harnessFastened: Boolean = false,
    val noiseDb: Int = 45,
)

/** 종료된 주행 1건 (이동 리포트용) */
data class DriveSessionRecord(
    val id: Long,
    val startedAtMs: Long,
    val durationMs: Long,
    val distanceKm: Double,
    /** 주행 중 평균 펫 편안함 지수 */
    val comfortScore: Int,
    val events: List<DriveEvent>,
    /** 이동장·하네스·창문·도어 순서. 주행 중 가장 나빴던 단계. null = 미연동 */
    val petSafe: List<Level?>,
    /** 출발 시점에 펫 세이프가 모두 정상이었는지 */
    val safeStart: Boolean,
    val routeName: String?,
)
