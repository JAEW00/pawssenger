package com.pawssenger.app.data

import com.pawssenger.app.analysis.AccelEstimator
import com.pawssenger.app.vehicle.VehicleGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 실차 데이터 소스. [VehicleGateway] 의 속도 스트림(약 10Hz)을 [VehicleSample] 로 바꾼다.
 *  - 종가속도: 속도 변화로 산출
 *  - 횡가속도: 속도 × 핸들 각도로 추정 (조향각 미연동이면 null → 급조향 감지 안 함)
 *  - 위치    : FusedLocation (미연동이면 null)
 * 로그 재생과 달리 스스로 끝나지 않으며, 주행 종료는 사용자가 한다.
 */
class PleosVehicleDataSource(private val gateway: VehicleGateway) : VehicleDataSource {
    override fun samples(): Flow<VehicleSample> = flow {
        gateway.start()
        val estimator = AccelEstimator()
        gateway.speedEvents.collect { e ->
            val st = gateway.state.value
            val steering = st.steeringAngleDeg.takeIf { it.ok }?.value
            val loc = st.location.takeIf { it.ok }?.value
            emit(
                VehicleSample(
                    timestampMs = e.timestampMs,
                    speedKph = e.speedKph,
                    longAccel = estimator.longitudinal(e.timestampMs, e.speedKph) ?: 0f,
                    latAccel = steering?.let { estimator.lateral(e.speedKph, it) },
                    lat = loc?.lat,
                    lng = loc?.lng,
                )
            )
        }
    }
}
