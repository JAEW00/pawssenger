package com.pawssenger.app.vehicle

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차량 실연동 진입점. 세 경로의 데이터를 앱 전용 [VehicleState] 하나로 모은다.
 *  - 차량 속도           : AAOS 표준 Car API `PERF_VEHICLE_SPEED` (Pleos Vehicle SDK 에 속도 API 없음)
 *  - 조향각·기어·브레이크·창문·좌석·공조·트립 : Pleos Vehicle SDK
 *  - 위치 / 경유지       : Pleos FusedLocation / NaviHelper
 *
 * 항목마다 지원 여부가 차종·에뮬레이터별로 다르므로, 실패는 예외로 올리지 않고 [Feature.link] 에 기록한다.
 * SDK 콜백은 바인더 스레드에서 오므로 상태 갱신은 모두 [MutableStateFlow.update] 로 한다.
 */
class VehicleGateway(context: Context) {
    private val context = context.applicationContext

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _speedEvents = MutableSharedFlow<SpeedEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** 속도 수신 스트림 (약 10Hz). 주행 분석용. */
    val speedEvents: SharedFlow<SpeedEvent> = _speedEvents.asSharedFlow()

    private var started = false
    private var car: Car? = null
    private var carProps: CarPropertyManager? = null
    private var speedRegistered = false
    private var vehicle: Vehicle? = null
    /** 제어(쓰기)가 가능한 공조 영역. capability 확인 뒤 채워진다. */
    @Volatile private var hvacPowerArea: SeatArea? = null
    @Volatile private var hvacTempAreas: Map<SeatArea, ClosedFloatingPointRange<Float>> = emptyMap()
    private var fused: FusedLocation? = null
    private var locationRegistered = false
    private var navi: NaviHelper? = null

    @Synchronized
    fun start() {
        if (started) return
        started = true
        connectSpeed()
        connectVehicleSdk()
        connectLocation()
        connectNavi()
    }

    /** 런타임 권한(속도·위치)이 허용된 뒤 호출하면 해당 항목만 다시 연결한다. */
    @Synchronized
    fun onPermissionsChanged() {
        if (!started) return
        connectSpeed()
        connectLocation()
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ───────────────────────── 차량 속도 (AAOS Car API) ─────────────────────────

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float) ?: return
            val kph = mps * 3.6f
            _state.update { it.copy(speedKph = Feature(Link.OK, kph)) }
            _speedEvents.tryEmit(SpeedEvent(value.timestamp / 1_000_000, kph))
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            _state.update { it.copy(speedKph = it.speedKph.copy(link = Link.ERROR, detail = "속도 수신 오류")) }
        }
    }

    private fun connectSpeed() {
        if (speedRegistered) return
        if (!granted(PERMISSION_CAR_SPEED)) {
            _state.update { it.copy(speedKph = Feature(Link.NO_PERMISSION, detail = "차량 속도 권한을 허용해 주세요")) }
            return
        }
        try {
            val pm = carProps ?: (Car.createCar(context).also { car = it }
                .getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).also { carProps = it }
            val config = pm.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)
            if (config == null) {
                _state.update { it.copy(speedKph = Feature(Link.UNSUPPORTED, detail = "PERF_VEHICLE_SPEED 미제공")) }
                return
            }
            val rate = SPEED_RATE_HZ.coerceIn(config.minSampleRate, config.maxSampleRate.coerceAtLeast(config.minSampleRate))
            speedRegistered = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, rate)
            _state.update {
                it.copy(speedKph = if (speedRegistered) Feature(Link.CONNECTING, detail = "${rate.toInt()}Hz") else Feature(Link.ERROR, detail = "콜백 등록 실패"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "speed connect failed", e)
            _state.update { it.copy(speedKph = Feature(linkOf(e), detail = e.shortMessage())) }
        }
    }

    // ───────────────────────── Pleos Vehicle SDK ─────────────────────────

    private fun connectVehicleSdk() {
        val v = try {
            Vehicle(context).also { it.initialize(); vehicle = it }
        } catch (e: Throwable) {
            Log.w(TAG, "Vehicle SDK init failed", e)
            val f = linkOf(e) to e.shortMessage()
            _state.update {
                it.copy(
                    steeringAngleDeg = Feature(f.first, detail = f.second), gear = Feature(f.first, detail = f.second),
                    brakePedal = Feature(f.first, detail = f.second),
                )
            }
            return
        }
        // 호출은 서비스 연결 전이라도 SDK 내부 큐에 쌓였다가 연결 후 실행된다.
        guarded("steering") { connectSteering(v) }
        guarded("gear") { connectGear(v) }
        guarded("brake") { connectBrake(v) }
        guarded("trip") { connectTrip(v) }
        guarded("windows") { connectWindows(v) }
        guarded("seats") { connectSeats(v) }
        guarded("doors") { connectDoors(v) }
        guarded("doorPositions") { connectDoorPositions(v) }
        guarded("hvac") { connectHvac(v) }
        guarded("battery") { connectBattery(v) }
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { Log.w(TAG, "$what connect failed", e) }
    }

    private fun connectSteering(v: Vehicle) {
        _state.update { it.copy(steeringAngleDeg = Feature(Link.CONNECTING)) }
        val fail = { e: Exception -> _state.update { it.copy(steeringAngleDeg = Feature(linkOf(e), detail = e.shortMessage())) } }
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ a -> setSteering(a) }, fail)
        v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) = setSteering(angle)
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    private fun setSteering(angle: Float?) {
        if (angle != null) _state.update { it.copy(steeringAngleDeg = Feature(Link.OK, angle)) }
    }

    private fun connectGear(v: Vehicle) {
        _state.update { it.copy(gear = Feature(Link.CONNECTING)) }
        val fail = { e: Exception -> _state.update { it.copy(gear = Feature(linkOf(e), detail = e.shortMessage())) } }
        val set = { g: VehicleGear? -> if (g != null) _state.update { it.copy(gear = Feature(Link.OK, g.toGearPos())) } }
        v.getGear().getCurrentGearSelectionState(set, fail)
        v.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = set(gear)
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    private fun connectBrake(v: Vehicle) {
        _state.update { it.copy(brakePedal = Feature(Link.CONNECTING)) }
        val fail = { e: Exception -> _state.update { it.copy(brakePedal = Feature(linkOf(e), detail = e.shortMessage())) } }
        val set = { b: Boolean? -> if (b != null) _state.update { it.copy(brakePedal = Feature(Link.OK, b)) } }
        v.getBrake().getCurrentBrakePedalStatus(set, fail)
        v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) = set(status)
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    /** 트립 주행 시간(분)·거리(m). capability 를 먼저 확인하고 미지원이면 구독하지 않는다. */
    private fun connectTrip(v: Vehicle) {
        val odo = v.getOdometer()

        _state.update { it.copy(tripTimeMin = Feature(Link.CONNECTING), tripDistanceM = Feature(Link.CONNECTING)) }
        val timeFail = { e: Exception -> _state.update { it.copy(tripTimeMin = Feature(linkOf(e), detail = e.shortMessage())) } }
        val timeSet = { t: Int? -> if (t != null) _state.update { it.copy(tripTimeMin = Feature(Link.OK, t)) } }
        odo.checkTripCurrentDrivingTimeCapabilities({ cfg ->
            if (!cfg.accessType.readable()) {
                _state.update { it.copy(tripTimeMin = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType}")) }
            } else guarded("tripTime") {
                odo.getTripCurrentDrivingTime(timeSet, timeFail)
                odo.registerTripCurrentDrivingTime(object : DrivingTimeListener {
                    override fun onDrivingTimeUpdated(value: Int?) = timeSet(value)
                    override fun onFailed(e: Exception) = timeFail(e)
                })
            }
        }, timeFail)

        val distFail = { e: Exception -> _state.update { it.copy(tripDistanceM = Feature(linkOf(e), detail = e.shortMessage())) } }
        val distSet = { d: Float? -> if (d != null) _state.update { it.copy(tripDistanceM = Feature(Link.OK, d)) } }
        odo.checkTripCurrentDrivingDistanceCapabilities({ cfg ->
            if (!cfg.accessType.readable()) {
                _state.update { it.copy(tripDistanceM = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType}")) }
            } else guarded("tripDistance") {
                odo.getTripCurrentDrivingDistance(distSet, distFail)
                odo.registerTripCurrentDrivingDistance(object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) = distSet(value)
                    override fun onFailed(e: Exception) = distFail(e)
                })
            }
        }, distFail)
    }

    /** 창문 위치. capability 가 알려 준 영역만 구독한다. */
    private fun connectWindows(v: Vehicle) {
        val win = v.getWindow()
        win.checkWindowPositionCapabilities({ cfg, bounds ->
            if (!cfg.accessType.readable() || bounds.isEmpty()) {
                Log.i(TAG, "window position unsupported access=${cfg.accessType} areas=${bounds.size}")
                return@checkWindowPositionCapabilities
            }
            bounds.forEach { (area, range) ->
                val name = area.name()
                val set = { pos: Int? ->
                    if (pos != null) setWindow(name, Feature(Link.OK, WindowInfo(pos, range.minValue, range.maxValue)))
                }
                val fail = { e: Exception -> setWindow(name, Feature(linkOf(e), detail = e.shortMessage())) }
                setWindow(name, Feature(Link.CONNECTING))
                guarded("window $name") {
                    win.getCurrentWindowPosition(area, set, fail)
                    win.registerWindowPosition(area, object : WindowPositionListener {
                        override fun onWindowPositionUpdated(position: Int?) = set(position)
                        override fun onFailed(e: Exception) = fail(e)
                    })
                }
            }
        }, { e -> Log.w(TAG, "window capability failed: ${e.shortMessage()}") })
    }

    private fun setWindow(name: AreaName, f: Feature<WindowInfo>) =
        _state.update { it.copy(windows = it.windows + (name to f)) }

    /** 도어 잠금. capability 가 알려 준 도어만 구독한다. */
    private fun connectDoors(v: Vehicle) {
        val door = v.getDoor()
        door.checkDoorLockCapabilities({ cfg, areas ->
            if (!cfg.accessType.readable()) return@checkDoorLockCapabilities
            areas.forEach { area ->
                val name = area.name()
                val set = { b: Boolean? -> if (b != null) _state.update { it.copy(doorLocked = it.doorLocked + (name to Feature(Link.OK, b))) } }
                val fail = { e: Exception -> _state.update { it.copy(doorLocked = it.doorLocked + (name to Feature(linkOf(e), detail = e.shortMessage()))) } }
                guarded("doorLock $name") {
                    door.getDoorLockState(area, set, fail)
                    door.registerDoorLockState(area, object : DoorLockStateListener {
                        override fun onDoorLockStateUpdated(state: Boolean?) = set(state)
                        override fun onFailed(e: Exception) = fail(e)
                    })
                }
            }
        }, { e -> Log.w(TAG, "door lock capability failed: ${e.shortMessage()}") })
    }

    /** 도어 열림(위치). 잠금과는 별개 값이다 — 잠겨 있어도 열려 있을 수 있다. */
    private fun connectDoorPositions(v: Vehicle) {
        val door = v.getDoor()
        door.checkDoorPositionCapabilities({ cfg, bounds ->
            if (!cfg.accessType.readable()) return@checkDoorPositionCapabilities
            bounds.forEach { (area, range) ->
                val name = area.name()
                val set = { pos: Int? ->
                    if (pos != null) _state.update { it.copy(doorOpen = it.doorOpen + (name to Feature(Link.OK, WindowInfo(pos, range.minValue, range.maxValue)))) }
                }
                val fail = { e: Exception -> _state.update { it.copy(doorOpen = it.doorOpen + (name to Feature(linkOf(e), detail = e.shortMessage()))) } }
                guarded("doorPosition $name") {
                    door.getCurrentDoorPosition(area, set, fail)
                    door.registerDoorPosition(area, object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) = set(pos)
                        override fun onFailed(e: Exception) = fail(e)
                    })
                }
            }
        }, { e -> Log.w(TAG, "door position capability failed: ${e.shortMessage()}") })
    }

    /** 좌석 점유·안전벨트. capability 가 알려 준 좌석만 구독한다. */
    private fun connectSeats(v: Vehicle) {
        val seat = v.getSeat()
        seat.checkSeatOccupancyCapabilities({ cfg, areas ->
            if (!cfg.accessType.readable()) return@checkSeatOccupancyCapabilities
            areas.forEach { area ->
                val name = area.name()
                val set = { b: Boolean? -> if (b != null) _state.update { it.copy(seatOccupied = it.seatOccupied + (name to Feature(Link.OK, b))) } }
                val fail = { e: Exception -> _state.update { it.copy(seatOccupied = it.seatOccupied + (name to Feature(linkOf(e), detail = e.shortMessage()))) } }
                guarded("seatOccupancy $name") {
                    seat.getCurrentSeatOccupancy(area, set, fail)
                    seat.registerSeatOccupancy(area, object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) = set(occupancy)
                        override fun onFailed(e: Exception) = fail(e)
                    })
                }
            }
        }, { e -> Log.w(TAG, "seat occupancy capability failed: ${e.shortMessage()}") })

        seat.checkSeatBeltBuckledCapabilities({ cfg, areas ->
            if (!cfg.accessType.readable()) return@checkSeatBeltBuckledCapabilities
            areas.forEach { area ->
                val name = area.name()
                val set = { b: Boolean? -> if (b != null) _state.update { it.copy(seatBelt = it.seatBelt + (name to Feature(Link.OK, b))) } }
                val fail = { e: Exception -> _state.update { it.copy(seatBelt = it.seatBelt + (name to Feature(linkOf(e), detail = e.shortMessage()))) } }
                guarded("seatBelt $name") {
                    seat.getCurrentSeatBeltBuckledState(area, set, fail)
                    seat.registerSeatBeltBuckledState(area, object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = set(state)
                        override fun onFailed(e: Exception) = fail(e)
                    })
                }
            }
        }, { e -> Log.w(TAG, "seat belt capability failed: ${e.shortMessage()}") })
    }

    /**
     * 공조 설정 온도·풍량·실내 온도.
     * 좌석 영역은 차종마다 묶음이 달라(예: 1열 좌우가 한 영역) 임의로 고르면 ERROR_NOT_SUPPORTED_AREA 가 난다.
     * capability 가 돌려준 영역 키를 그대로 조회에 쓴다. 운전석을 포함한 영역을 우선한다.
     */
    private fun connectHvac(v: Vehicle) {
        val hvac = v.getHVAC()
        _state.update { it.copy(hvacTempSetC = Feature(Link.CONNECTING), hvacFanSpeed = Feature(Link.CONNECTING), hvacCabinTempC = Feature(Link.CONNECTING)) }

        val tempFail = { e: Exception -> _state.update { it.copy(hvacTempSetC = Feature(linkOf(e), detail = e.shortMessage())) } }
        hvac.checkHvacTemperatureCapabilities({ cfg, bounds ->
            val area = bounds.keys.preferDriver()
            if (cfg.accessType == AccessType.READ_WRITE) hvacTempAreas = bounds.mapValues { it.value.minValue..it.value.maxValue }
            if (!cfg.accessType.readable() || area == null) {
                _state.update { it.copy(hvacTempSetC = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType} areas=${bounds.size}")) }
            } else {
                val range = bounds[area]
                val detail = "${area.name()} ${range?.minValue}~${range?.maxValue}"
                val set = { c: Float? ->
                    if (c != null) _state.update {
                        // 지원 범위 밖 값(에뮬레이터에서 0.0)은 설정 온도로 쓰지 않는다. 공조가 꺼져 있을 때로 추정.
                        val valid = range == null || c in range.minValue..range.maxValue
                        it.copy(hvacTempSetC = Feature(Link.OK, c.takeIf { valid }, if (valid) detail else "$detail · 범위 밖 값 $c (공조 꺼짐 추정)"))
                    }
                }
                guarded("hvacTemp") {
                    hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, set, tempFail)
                    hvac.registerHvacTemperature(area, object : HvacTemperatureValueListener {
                        override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) = set(celsius)
                        override fun onFailed(e: Exception) = tempFail(e)
                    })
                }
            }
        }, tempFail)

        val fanFail = { e: Exception -> _state.update { it.copy(hvacFanSpeed = Feature(linkOf(e), detail = e.shortMessage())) } }
        hvac.checkHvacFanSpeedCapabilities({ cfg, bounds ->
            val area = bounds.keys.preferDriver()
            if (!cfg.accessType.readable() || area == null) {
                _state.update { it.copy(hvacFanSpeed = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType} areas=${bounds.size}")) }
            } else {
                val range = bounds[area]
                val detail = "${area.name()} ${range?.minValue}~${range?.maxValue}"
                val set = { s: Int? -> if (s != null) _state.update { it.copy(hvacFanSpeed = Feature(Link.OK, s, detail)) } }
                guarded("hvacFan") {
                    hvac.getHvacFanSpeed(area, set, fanFail)
                    hvac.registerHvacFanSpeed(area, object : HvacFanSpeedListener {
                        override fun onHvacFanSpeedUpdated(speed: Int?) = set(speed)
                        override fun onFailed(e: Exception) = fanFail(e)
                    })
                }
            }
        }, fanFail)

        val powerFail = { e: Exception -> _state.update { it.copy(hvacPower = Feature(linkOf(e), detail = e.shortMessage())) } }
        hvac.checkHvacPowerCapabilities({ cfg, areas ->
            val area = areas.preferDriver()
            if (cfg.accessType == AccessType.READ_WRITE) hvacPowerArea = area
            if (!cfg.accessType.readable() || area == null) {
                _state.update { it.copy(hvacPower = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType} areas=${areas.size}")) }
            } else guarded("hvacPower") {
                val set = { on: Boolean? -> if (on != null) _state.update { it.copy(hvacPower = Feature(Link.OK, on, area.name())) } }
                hvac.getHvacPowerState(area, set, powerFail)
                hvac.registerHvacPowerState(area, object : HvacPowerStateListener {
                    override fun onHvacPowerStateUpdated(state: Boolean?) = set(state)
                    override fun onFailed(e: Exception) = powerFail(e)
                })
            }
        }, powerFail)

        val cabinFail = { e: Exception -> _state.update { it.copy(hvacCabinTempC = Feature(linkOf(e), detail = e.shortMessage())) } }
        hvac.checkHvacTemperatureCurrentCapabilities({ cfg, areas ->
            val area = areas.preferDriver()
            if (!cfg.accessType.readable() || area == null) {
                _state.update { it.copy(hvacCabinTempC = Feature(Link.UNSUPPORTED, detail = "access=${cfg.accessType} areas=${areas.size}")) }
            } else guarded("hvacCabin") {
                val set = { c: Float? -> if (c != null) _state.update { it.copy(hvacCabinTempC = Feature(Link.OK, c, area.name())) } }
                hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, set, cabinFail)
                // 실내 온도는 계속 변하는 값이라 구독해야 한다 (처음 한 번만 읽으면 화면이 갱신되지 않는다).
                hvac.registerHvacTemperatureCurrent(area, object : HvacTemperatureValueListener {
                    override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) = set(celsius)
                    override fun onFailed(e: Exception) = cabinFail(e)
                })
            }
        }, cabinFail)
    }

    // ───────────────────────── 위치 (Pleos FusedLocation) ─────────────────────────

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            _state.update {
                it.copy(location = Feature(Link.OK, GeoPoint(location.latitude, location.longitude, location.heading, location.timestampMillis)))
            }
        }

        override fun onError(exception: Exception) {
            _state.update { it.copy(location = it.location.copy(link = linkOf(exception), detail = exception.shortMessage())) }
        }
    }

    private fun connectLocation() {
        if (locationRegistered) return
        try {
            val f = fused ?: FusedLocation(context).also { fused = it }
            if (!f.isSupported()) {
                _state.update { it.copy(location = Feature(Link.UNSUPPORTED, detail = "FusedLocation 서비스 없음")) }
                return
            }
            if (!granted(PERMISSION_FUSED_LOCATION)) {
                _state.update { it.copy(location = Feature(Link.NO_PERMISSION, detail = "위치 권한을 허용해 주세요")) }
                return
            }
            f.initialize()
            _state.update { it.copy(location = Feature(Link.CONNECTING)) }
            f.getLastKnownLocation(locationListener)
            f.registerFusedLocationCallback(locationListener)
            locationRegistered = true
        } catch (e: Throwable) {
            Log.w(TAG, "location connect failed", e)
            _state.update { it.copy(location = Feature(linkOf(e), detail = e.shortMessage())) }
        }
    }

    // ───────────────────────── 내비 (Pleos NaviHelper) ─────────────────────────

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            // isRouting 은 안내 중에도 false 로 올 때가 있다(에뮬레이터에서 확인) → 내비 모드도 함께 본다.
            val guiding = routeStateInfo.isRouting ||
                routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            _state.update { it.copy(navi = Feature(Link.OK, guiding, it.navi.detail), destinationName = if (guiding) it.destinationName else null, destination = if (guiding) it.destination else null) }
            if (guiding) guarded("destinationInfo") { navi?.getDestinationInfo() }
        }

        override fun onRoutingCompleted() {
            _state.update { it.copy(navi = Feature(Link.OK, true, "경로 탐색 완료")) }
            guarded("destinationInfo") { navi?.getDestinationInfo() }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            _state.update {
                it.copy(naviLocation = GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude, currentLocationInfo.heading.toFloat(), System.currentTimeMillis()))
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 목적지 이름은 경로 안내 중일 때만 쓴다 (안내가 끝난 뒤 남은 이전 목적지를 기록에 붙이지 않도록)
            _state.update {
                val guiding = it.navi.value == true
                // 좌표가 0,0 이면 값이 없는 것으로 본다.
                val hasPoint = destinationInfo.latitude != 0.0 || destinationInfo.longitude != 0.0
                it.copy(
                    destinationName = destinationInfo.name.takeIf { n -> n.isNotBlank() && guiding },
                    destination = if (guiding && hasPoint) GeoPoint(destinationInfo.latitude, destinationInfo.longitude, 0f, System.currentTimeMillis()) else null,
                )
            }
        }

        override fun onRouteCancelled() {
            _state.update { it.copy(navi = Feature(Link.OK, false, "경로 취소됨"), destinationName = null, destination = null) }
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            _state.update { it.copy(navi = it.navi.copy(link = Link.OK, detail = "경유지 변경: ${waypointChangedInfo.cmdType}")) }
        }

        override fun onError(errorCode: NaviErrorCode) {
            _state.update { it.copy(navi = it.navi.copy(detail = "내비 오류: $errorCode")) }
        }
    }

    private fun connectNavi() {
        try {
            val n = NaviHelper(context).also { navi = it }
            _state.update { it.copy(navi = Feature(Link.CONNECTING)) }
            n.addListener(naviListener)
            n.initialize(
                onConnected = {
                    _state.update { it.copy(navi = Feature(Link.OK, it.navi.value ?: false, "내비 서비스 연결됨")) }
                    guarded("routeState") { n.getRouteStateInfo() }
                    guarded("naviLocation") { n.getCurrentLocationInfo() }
                    guarded("destinationInfo") { n.getDestinationInfo() }
                },
                onFailed = { e -> _state.update { it.copy(navi = Feature(linkOf(e), detail = e.shortMessage())) } },
            )
        } catch (e: Throwable) {
            Log.w(TAG, "navi connect failed", e)
            _state.update { it.copy(navi = Feature(linkOf(e), detail = e.shortMessage())) }
        }
    }

    /**
     * 차량 내비게이션의 목적지로 설정한다 (경로 탐색 요청).
     * 호출 성공이 탐색 완료를 뜻하지는 않으며, 결과는 [state] 의 navi (onRoutingCompleted / onError) 로 온다.
     * @return 요청을 보냈으면 true
     */
    /** 내비게이션의 현재 위치를 다시 요청한다. 결과는 [state] 의 naviLocation 으로 온다. */
    fun requestNaviLocation() {
        val n = navi ?: return
        if (_state.value.navi.ok) guarded("naviLocation") { n.getCurrentLocationInfo() }
    }

    /** 진행 중인 경로 안내를 취소한다. */
    fun cancelRoute(): Boolean {
        val n = navi ?: return false
        return try { n.cancelRoute(); true } catch (e: Throwable) { Log.w(TAG, "cancelRoute failed", e); false }
    }

    fun setDestination(lat: Double, lng: Double, name: String, address: String): Boolean {
        val n = navi ?: return false
        if (!_state.value.navi.ok) return false
        return try {
            n.requestRoute(
                RouteInfo(
                    latitude = lat, longitude = lng, displayLatitude = null, displayLongitude = null,
                    poiName = name, poiId = "", address = address, poiSubId = "",
                    routeOption = RouteOption.RECOMMENDED,
                )
            )
            _state.update { it.copy(navi = it.navi.copy(detail = "목적지 요청: $name")) }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "setDestination failed", e)
            _state.update { it.copy(navi = it.navi.copy(detail = "목적지 요청 실패: ${e.shortMessage()}")) }
            false
        }
    }

    /**
     * 내비게이션 앱의 장소 검색 화면을 [keyword] 로 연다 (내비 앱이 앞으로 나온다).
     * [keyword] 가 비어 있으면 검색 없이 내비 앱만 앞으로 가져온다.
     * 검색 결과를 앱이 받아 오지는 못한다. 사용자가 목적지를 고르면 [state] 의 destinationName·destination 으로 반영된다.
     * @return 요청을 보냈으면 true
     */
    fun openNaviSearch(keyword: String): Boolean {
        val n = navi ?: return false
        if (!_state.value.navi.ok) return false
        return try {
            // 빈 키워드로는 내비가 검색 화면을 열지 않는다(에뮬레이터에서 확인) → 내비 앱을 앞으로 가져온다.
            n.requestNavigationScreen(
                if (keyword.isBlank()) NavigationScreenRequest.Foreground else NavigationScreenRequest.SearchPlace(keyword)
            )
            true
        } catch (e: Throwable) {
            Log.w(TAG, "openNaviSearch failed", e)
            false
        }
    }

    /**
     * 현재 경로에 경유지를 추가한다. 호출 성공이 곧 경로 반영을 뜻하지는 않으며,
     * 최종 결과는 [state] 의 navi.detail (onWaypointChanged / onError) 로 온다.
     * @return 요청을 보냈으면 true
     */
    fun addWaypoint(lat: Double, lng: Double, name: String): Boolean {
        val n = navi ?: return false
        if (!_state.value.navi.ok) return false
        return try {
            // 자체 장소 데이터라 내비 POI 식별자는 없다 → 좌표·이름만 넘긴다.
            n.addWaypoint(
                RequestWaypointInfo(
                    latitude = lat, longitude = lng, poiName = name, waypointIndex = WaypointIndex.FIRST,
                    poiId = "", address = "", poiSubId = "",
                )
            )
            _state.update { it.copy(navi = it.navi.copy(detail = "경유지 요청: $name")) }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "addWaypoint failed", e)
            _state.update { it.copy(navi = it.navi.copy(detail = "경유지 요청 실패: ${e.shortMessage()}")) }
            false
        }
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        runCatching { if (speedRegistered) carProps?.unregisterCallback(speedCallback) }
        speedRegistered = false
        runCatching { car?.disconnect() }
        car = null; carProps = null
        runCatching { vehicle?.release() }
        vehicle = null
        runCatching { if (locationRegistered) fused?.unregisterFusedLocationCallback(locationListener) }
        locationRegistered = false
        runCatching { fused?.release() }
        fused = null
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        navi = null
        _state.value = VehicleState()
    }

    // ───────────────────────── 배터리 / 공조 제어 (주정차 케어 모드) ─────────────────────────

    private fun connectBattery(v: Vehicle) {
        val ev = v.getEvBattery()
        _state.update { it.copy(batteryPct = Feature(Link.CONNECTING)) }
        val set = { pct: Int? -> if (pct != null) _state.update { it.copy(batteryPct = Feature(Link.OK, pct)) } }
        val fail = { e: Exception -> _state.update { it.copy(batteryPct = Feature(linkOf(e), detail = e.shortMessage())) } }
        ev.getEvBatteryStateOfCharge(set, fail)
        ev.registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(soc: Int?) = set(soc)
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    /**
     * 공조 전원을 켜거나 끈다. 요청을 보냈으면 true (제어를 지원하지 않으면 false).
     * 차량이 받아들인 값은 응답으로 상태에 반영한다 (에뮬레이터는 되읽기 값이 늦게 바뀐다).
     * [onResult] 는 차량의 응답이 오면 한 번 불린다: 요청한 상태가 됐으면 true, 거절·오류면 false.
     * 요청을 보내지 못한 경우(false 반환)에는 불리지 않는다. SDK 스레드에서 불릴 수 있다.
     */
    fun setHvacPower(on: Boolean, onResult: ((Boolean) -> Unit)? = null): Boolean {
        val v = vehicle ?: return false
        val area = hvacPowerArea ?: return false
        return try {
            v.getHVAC().setHvacPower(area, on, { _, r ->
                _state.update { it.copy(hvacPower = Feature(Link.OK, r, area.name())) }
                onResult?.invoke(r == on)
            }) { e ->
                Log.w(TAG, "setHvacPower failed: ${e.shortMessage()}")
                onResult?.invoke(false)
            }
            true
        } catch (e: Throwable) { Log.w(TAG, "setHvacPower failed", e); false }
    }

    /** 공조 설정 온도(℃)를 바꾼다. 지원 범위 안으로 맞춰 보낸다. 요청을 보냈으면 true. */
    fun setHvacTemperature(celsius: Float): Boolean {
        val v = vehicle ?: return false
        val areas = hvacTempAreas
        if (areas.isEmpty()) return false
        return try {
            areas.forEach { (area, range) ->
                v.getHVAC().setHvacTemperature(area, celsius.coerceIn(range), TemperatureUnit.CELSIUS, { a, r ->
                    if (SeatValue.ROW_1_LEFT in a.values || areas.size == 1) _state.update { it.copy(hvacTempSetC = it.hvacTempSetC.copy(link = Link.OK, value = r)) }
                }) { e -> Log.w(TAG, "setHvacTemperature failed: ${e.shortMessage()}") }
            }
            true
        } catch (e: Throwable) { Log.w(TAG, "setHvacTemperature failed", e); false }
    }

    companion object {
        private const val TAG = "VehicleGateway"
        const val PERMISSION_CAR_SPEED = "android.car.permission.CAR_SPEED"
        const val PERMISSION_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        /** 사용자에게 허용을 받아야 하는 권한 (나머지 Pleos 권한은 매니페스트 선언만으로 부여된다) */
        val RUNTIME_PERMISSIONS = arrayOf(PERMISSION_CAR_SPEED, PERMISSION_FUSED_LOCATION)
        private const val SPEED_RATE_HZ = 10f
    }
}

private fun AccessType.readable() = this == AccessType.READ || this == AccessType.READ_WRITE

private fun SeatArea.name(): AreaName = values.joinToString("+") { it.name }
private fun WindowArea.name(): AreaName = values.joinToString("+") { it.name }
private fun DoorArea.name(): AreaName = values.joinToString("+") { it.name }

/** 운전석(1열 좌측)을 포함한 영역 우선, 없으면 첫 영역 */
private fun Collection<SeatArea>.preferDriver(): SeatArea? =
    firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: firstOrNull()

private fun VehicleGear.toGearPos() = when (this) {
    VehicleGear.GEAR_PARK -> GearPos.PARK
    VehicleGear.GEAR_REVERSE -> GearPos.REVERSE
    VehicleGear.GEAR_NEUTRAL -> GearPos.NEUTRAL
    VehicleGear.GEAR_DRIVE -> GearPos.DRIVE
    else -> GearPos.OTHER
}

private fun Throwable.shortMessage(): String = (message ?: javaClass.simpleName).take(120)

private fun linkOf(e: Throwable): Link {
    val m = (e.message ?: "") + e.javaClass.simpleName
    return when {
        e is SecurityException || m.contains("PERMISSION", ignoreCase = true) -> Link.NO_PERMISSION
        m.contains("NOT_SUPPORTED") || m.contains("not supported", ignoreCase = true) || m.contains("NOT_AVAILABLE") -> Link.UNSUPPORTED
        else -> Link.ERROR
    }
}
