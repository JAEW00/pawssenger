package com.pawssenger.app.data

import kotlin.math.cos
import kotlin.math.sqrt

/** 내장 장소 목록에서 기준점 주변을 찾는다. */
object PlaceFinder {
    private const val KM_PER_DEG = 111.0

    /**
     * 기준점([lat], [lng])에서 가까운 순으로 돌려준다.
     *  - [radiusKm] 안의 장소를 최대 [limit] 곳
     *  - 그 안에 [minCount] 곳이 안 되면, 거리와 상관없이 가장 가까운 [fallbackCount] 곳
     * 거리는 직선 거리다 (도로 거리가 아니다).
     */
    fun near(
        all: List<Place>, lat: Double, lng: Double, type: PlaceType? = null,
        radiusKm: Double = 30.0, limit: Int = 30, minCount: Int = 5, fallbackCount: Int = 10,
    ): List<NearbyPlace> {
        val kmPerDegLng = KM_PER_DEG * cos(Math.toRadians(lat))
        val sorted = all.asSequence()
            .filter { type == null || it.type == type }
            .map { p ->
                val east = (p.lng - lng) * kmPerDegLng
                val north = (p.lat - lat) * KM_PER_DEG
                NearbyPlace(p, sqrt(east * east + north * north), east, north)
            }
            .sortedBy { it.distanceKm }
            .toList()
        val inRadius = sorted.takeWhile { it.distanceKm <= radiusKm }
        return if (inRadius.size >= minCount) inRadius.take(limit) else sorted.take(fallbackCount)
    }
}
