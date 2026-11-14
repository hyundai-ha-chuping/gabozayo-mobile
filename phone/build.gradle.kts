import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// local.properties 에 TOUR_API_KEY=발급받은키 를 적어 두면 BuildConfig 로 들어갑니다. (git 에 올라가지 않아요)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.gabozayo.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gabozayo.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TOUR_API_KEY", "\"${localProps.getProperty("TOUR_API_KEY", "")}\"")
        buildConfigField("String", "FIREBASE_DB_URL", "\"${localProps.getProperty("FIREBASE_DB_URL", "")}\"")
    }
    // 제출용 release: 별도 키스토어 없이 디버그 키로 서명해서 바로 설치할 수 있어요
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-splashscreen:1.0.1")   // 시작 화면 (밴 + 가보자요)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("io.coil-kt:coil-compose:2.7.0")        // 실제 이미지 로딩
    implementation("org.osmdroid:osmdroid-android:6.1.20") // 앱 안 지도 (API 키 불필요)
}
