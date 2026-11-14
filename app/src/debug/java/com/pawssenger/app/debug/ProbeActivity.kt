package com.pawssenger.app.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.pawssenger.app.PawssengerApp
import com.pawssenger.app.vehicle.Feature
import com.pawssenger.app.vehicle.VehicleState

/**
 * 디버그 전용 차량 연동 진단 화면. [com.pawssenger.app.vehicle.VehicleGateway] 의 현재 상태를 1초마다 표시하고,
 * 내용이 바뀔 때마다 logcat(tag=PawProbe)에 남긴다.
 *
 *   adb shell am start -n com.pawssenger.app/.debug.ProbeActivity
 *   adb shell am start -n com.pawssenger.app/.debug.ProbeActivity --es waypoint "37.41,127.11,테스트 휴게소"
 */
class ProbeActivity : Activity() {
    private lateinit var tv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var last = ""
    private var speedEvents = 0

    private val tick = object : Runnable {
        override fun run() {
            val app = application as PawssengerApp
            val text = render(app.vehicleGateway.state.value)
            tv.text = text
            if (text != last) {
                last = text
                text.lines().forEach { Log.i("PawProbe", it) }
                Log.i("PawProbe", "----")
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tv = TextView(this).apply { textSize = 15f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(tv) })
        val app = application as PawssengerApp
        app.vehicleGateway.start()

        if (intent.getBooleanExtra("cancelRoute", false)) handler.postDelayed({ Log.i("PawProbe", "cancelRoute sent=" + app.vehicleGateway.cancelRoute()) }, 3000)
        intent.getStringExtra("waypoint")?.split(",")?.let { p ->
            val lat = p.getOrNull(0)?.toDoubleOrNull()
            val lng = p.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lng != null) handler.postDelayed({
                val sent = app.vehicleGateway.addWaypoint(lat, lng, p.getOrNull(2) ?: "경유지")
                Log.i("PawProbe", "addWaypoint sent=$sent")
            }, 4000)
        }
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun <T> f(name: String, x: Feature<T>, fmt: (T) -> String = { it.toString() }) =
        "%-14s %-6s %s%s".format(name, x.link.label, x.value?.let(fmt) ?: "-", x.detail?.let { "  ($it)" } ?: "")

    private fun render(s: VehicleState): String = buildString {
        appendLine("[주행]")
        appendLine(f("속도", s.speedKph) { "%.1f km/h".format(it) })
        appendLine(f("조향각", s.steeringAngleDeg) { "%.1f°".format(it) })
        appendLine(f("기어", s.gear) { it.label })
        appendLine(f("브레이크", s.brakePedal) { if (it) "밟음" else "뗌" })
        appendLine(f("트립 시간", s.tripTimeMin) { "${it}분" })
        appendLine(f("트립 거리", s.tripDistanceM) { "%.0f m".format(it) })
        appendLine("[탑승 환경]")
        if (s.windows.isEmpty()) appendLine("창문          (지원 영역 없음 또는 조회 중)")
        s.windows.toSortedMap().forEach { (k, v) ->
            appendLine(f("창문 $k", v) { "pos=${it.position} [${it.min}~${it.max}] ${if (it.isOpen) "열림" else "닫힘"}" })
        }
        s.doorOpen.toSortedMap().forEach { (k, v) -> appendLine(f("도어 $k", v) { "pos=${it.position} [${it.min}~${it.max}] ${if (it.isOpen) "열림" else "닫힘"}" }) }
        s.doorLocked.toSortedMap().forEach { (k, v) -> appendLine(f("잠금 $k", v) { if (it) "잠금" else "해제" }) }
        s.seatOccupied.toSortedMap().forEach { (k, v) -> appendLine(f("점유 $k", v) { if (it) "있음" else "없음" }) }
        s.seatBelt.toSortedMap().forEach { (k, v) -> appendLine(f("벨트 $k", v) { if (it) "체결" else "미체결" }) }
        appendLine("[공조]")
        appendLine(f("공조 전원", s.hvacPower) { if (it) "켜짐" else "꺼짐" })
        appendLine(f("설정 온도", s.hvacTempSetC) { "%.1f°C".format(it) })
        appendLine(f("실내 온도", s.hvacCabinTempC) { "%.1f°C".format(it) })
        appendLine(f("풍량", s.hvacFanSpeed) { "${it}단" })
        appendLine("[위치·내비]")
        appendLine(f("위치", s.location) { "%.5f, %.5f heading=%.0f".format(it.lat, it.lng, it.headingDeg) })
        appendLine("내비 위치       " + (s.naviLocation?.let { "%.5f, %.5f".format(it.lat, it.lng) } ?: "-") + "  목적지: " + (s.destinationName ?: "-"))
        append(f("내비", s.navi) { if (it) "경로 안내 중" else "경로 없음" })
    }
}
