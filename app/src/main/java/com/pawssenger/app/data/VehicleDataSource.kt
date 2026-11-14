package com.pawssenger.app.data

import kotlinx.coroutines.flow.Flow

/** 한 시점의 차량 상태. 가속도는 SDK 미제공 시 null (속도 변화량으로 대체 계산). */
data class VehicleSample(
    val timestampMs: Long,
    val speedKph: Float,
    val longAccel: Float? = null, // m/s^2, 감속은 음수
    val latAccel: Float? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** 실연동(Pleos Vehicle SDK)과 로그 재생 구현체를 교체 가능하게 하는 인터페이스. */
interface VehicleDataSource {
    fun samples(): Flow<VehicleSample>
}
