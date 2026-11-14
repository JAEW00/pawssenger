plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pawssenger.app"
    compileSdk = 35
    useLibrary("android.car") // AAOS 표준 Car API (차량 속도)
    defaultConfig {
        applicationId = "com.pawssenger.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // 지도 라이브러리(MapLibre)의 네이티브 코드는 차량용 CPU 두 종류만 넣는다 (에뮬레이터 x86_64, 실차 arm64).
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Room: 펫 프로필 · 주행 세션 · 이벤트 이력 · 저장한 휴식지
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    // Pleos Vehicle SDK (공식 샘플 Pleos-Playground/pleos-sample-vehicle-app 기준 버전)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3") // 위치
    implementation("ai.pleos.playground:NaviHelper:3.0.5")    // 경유지 추가
    // 오픈소스 지도 (키 불필요). 지도 타일은 OpenFreeMap 에서 받는다.
    implementation("org.maplibre.gl:android-sdk:12.3.1")
}
