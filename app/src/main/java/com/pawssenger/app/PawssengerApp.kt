package com.pawssenger.app

import android.app.Application
import com.pawssenger.app.dashboard.CabinTempSimulator
import com.pawssenger.app.dashboard.DashboardEngine
import com.pawssenger.app.data.AppRepository
import com.pawssenger.app.data.PleosVehicleDataSource
import com.pawssenger.app.data.db.PawDatabase
import com.pawssenger.app.drive.DriveSessionManager
import com.pawssenger.app.stay.StayCareController
import com.pawssenger.app.vehicle.GearPos
import com.pawssenger.app.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 간단한 수동 DI 컨테이너. 화면 이동과 무관하게 차량 연동·주행 기록이 유지되도록 Application 범위에 둔다. */
class PawssengerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: AppRepository
        private set
    lateinit var vehicleGateway: VehicleGateway
        private set
    lateinit var driveManager: DriveSessionManager
        private set
    lateinit var cabinSimulator: CabinTempSimulator
        private set
    lateinit var dashboard: DashboardEngine
        private set
    lateinit var stayCare: StayCareController
        private set

    override fun onCreate() {
        super.onCreate()
        vehicleGateway = VehicleGateway(this).also { it.start() }
        repository = AppRepository(this, appScope, PawDatabase.create(this).dao())
        driveManager = DriveSessionManager(
            scope = appScope,
            repository = repository,
            liveSource = { PleosVehicleDataSource(vehicleGateway) },
            isParked = { vehicleGateway.state.value.gear.value == GearPos.PARK },
        )
        cabinSimulator = CabinTempSimulator(appScope, vehicleGateway, repository.cabinTempDemo)
        dashboard = DashboardEngine(appScope, vehicleGateway, driveManager, repository, cabinSimulator)
        stayCare = StayCareController(appScope, vehicleGateway)
        // 차량 데이터를 항상 듣고 있다가 움직이면 주행이 시작된다.
        driveManager.startLive()
    }
}
