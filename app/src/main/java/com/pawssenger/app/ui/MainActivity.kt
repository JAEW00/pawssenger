package com.pawssenger.app.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pawssenger.app.PawssengerApp
import com.pawssenger.app.ui.components.DesignSurface
import com.pawssenger.app.ui.components.OutlineButton
import com.pawssenger.app.ui.components.PawTopBar
import com.pawssenger.app.ui.components.PrimaryButton
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.components.card
import com.pawssenger.app.ui.drive.DriveCareScreen
import com.pawssenger.app.ui.main.MainDashboardScreen
import com.pawssenger.app.ui.report.TripReportScreen
import com.pawssenger.app.ui.rest.RestStopsScreen
import com.pawssenger.app.ui.settings.SettingsScreen
import com.pawssenger.app.ui.stay.StayCareRunScreen
import com.pawssenger.app.ui.stay.StayCareSetupScreen
import com.pawssenger.app.ui.theme.Paw
import com.pawssenger.app.ui.theme.PawssengerTheme
import com.pawssenger.app.vehicle.GearPos
import com.pawssenger.app.vehicle.VehicleGateway

enum class Route { Main, DriveCare, Rest, Report, Settings, StaySetup, StayRun }

class MainActivity : ComponentActivity() {
    /** 아직 허용되지 않은 권한. 요청 결과가 올 때마다 다시 계산한다. */
    private var missingPermissions by mutableStateOf(emptyList<String>())

    private fun refreshMissing() {
        missingPermissions = VehicleGateway.RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PawssengerApp
        // 차량 속도·위치는 사용자 허용이 필요하다. 허용되면 해당 항목만 다시 연결한다.
        // 요청은 앱이 켜지자마자 하지 않는다: 시스템 권한 창은 주행 중(기어 P 가 아닐 때) 차단되어
        // "주행 중에는 사용할 수 없습니다" 화면이 앱을 덮기 때문이다. → PermissionIntro 가 주차 중일 때만 띄운다.
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshMissing()
            app.vehicleGateway.onPermissionsChanged()
        }
        refreshMissing()
        setContent {
            PawssengerTheme {
                DesignSurface {
                    AppNav(app)
                    PermissionIntro(app, missingPermissions, launcher)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMissing()
    }
}

/**
 * 권한 안내. 시스템 요청 창은 차량 속도 권한을 "알 수 없는 작업"으로 표시하므로(시스템 문구라 앱에서 바꿀 수 없다),
 * 요청 전에 무엇을 왜 허용하는지 앱이 먼저 알려 준다. 주차(P) 상태에서만 보인다.
 */
@Composable
private fun PermissionIntro(app: PawssengerApp, missing: List<String>, launcher: ActivityResultLauncher<Array<String>>) {
    val vehicle by app.vehicleGateway.state.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    if (missing.isEmpty() || dismissed || vehicle.gear.value != GearPos.PARK) return

    Box(
        Modifier.fillMaxSize().background(Color(0x66201A3A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(560.dp).card(28, Paw.Card).padding(32.dp)) {
            T("차량 정보 사용 허용이 필요해요", 24, FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            T("반려견의 이동 환경을 살피려면 아래 정보를 읽어야 해요. 다음 화면에서 허용해 주세요.", 15, color = Paw.InkSub)
            Spacer(Modifier.height(18.dp))
            if (VehicleGateway.PERMISSION_FUSED_LOCATION in missing) {
                PermissionRow("위치", "주변의 반려견 동반 장소를 찾고 내비게이션 목적지로 보낼 때 써요.")
            }
            if (VehicleGateway.PERMISSION_CAR_SPEED in missing) {
                PermissionRow("차량 속도", "급가속·급제동을 감지하고 편안함 지수를 계산할 때 써요. 요청 창에는 \"알 수 없는 작업\"으로 표시될 수 있어요.")
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("나중에", Modifier.weight(1f), height = 56) { dismissed = true }
                PrimaryButton("허용하러 가기", Modifier.weight(1.4f), height = 56) { launcher.launch(missing.toTypedArray()) }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).card(16, Paw.Tile).padding(horizontal = 16.dp, vertical = 12.dp)) {
        T(title, 16, FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        T(body, 13, color = Paw.InkSub, lineHeight = 1.5f)
    }
}

@Composable
private fun AppNav(app: PawssengerApp) {
    val nav = rememberNavController()
    val state by app.dashboard.state.collectAsStateWithLifecycle()

    val goHome: () -> Unit = { nav.popBackStack(Route.Main.name, inclusive = false) }
    val back: () -> Unit = { if (!nav.popBackStack()) goHome() }
    val open: (Route) -> Unit = { nav.navigate(it.name) { launchSingleTop = true } }

    val vehicle by app.vehicleGateway.state.collectAsStateWithLifecycle()
    val stay by app.stayCare.state.collectAsStateWithLifecycle()
    val tempDemo by app.repository.cabinTempDemo.collectAsStateWithLifecycle()
    val simTemp by app.cabinSimulator.temp.collectAsStateWithLifecycle()
    val parked = vehicle.gear.value == GearPos.PARK
    // 실내 온도: 설정의 실제/시연 선택을 따른다.
    val cabinTemp = if (tempDemo) simTemp else vehicle.hvacCabinTempC.takeIf { it.ok }?.value
    // 주정차 케어 실행 화면은 상단 바 없이 전체 화면으로 보여 준다.
    val entry by nav.currentBackStackEntryAsState()
    val fullScreen = entry?.destination?.route == Route.StayRun.name

    Column(Modifier.fillMaxSize()) {
        if (!fullScreen) PawTopBar(goHome, { open(Route.Settings) })
        NavHost(nav, startDestination = Route.Main.name) {
            composable(Route.Main.name) { MainDashboardScreen(state, app.dashboard, parked, open) }
            composable(Route.StaySetup.name) {
                StayCareSetupScreen(stay, cabinTemp, parked, app.stayCare, back) { if (app.stayCare.start()) open(Route.StayRun) }
            }
            composable(Route.StayRun.name) { StayCareRunScreen(stay, cabinTemp, app.stayCare, goHome) }
            composable(Route.DriveCare.name) { DriveCareScreen(state, back) }
            composable(Route.Rest.name) { RestStopsScreen(app.repository, app.vehicleGateway, back) }
            composable(Route.Report.name) { TripReportScreen(app.repository, back) }
            composable(Route.Settings.name) { SettingsScreen(app, back) }
        }
    }
}
