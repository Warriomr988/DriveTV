plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.panda.drivetv"
    // NOTA Termux: compileSdk/targetSdk 34 (no 35) porque el aapt2 de Termux
    // es v2.19 y no puede leer el android.jar de API 35. Mismo criterio que
    // FiltroLlamadas y PS4Bridge.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.panda.drivetv"
        // v0.8.0: libmpv exige minSdk 26 (el AAR lo declara).
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.8.1"

        // Web client ID de Google Cloud: se lee de local.properties
        // (ver local.properties.example). Nunca va en el repo.
        // NOTA: no se usa java.util.Properties porque el Kotlin DSL de
        // Gradle no resuelve java.util en los scripts de este entorno.
        val localFile = rootProject.file("local.properties")
        val webClientId = localFile
            .takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("drivetv.webClientId=") }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()
            .ifEmpty { System.getenv("DRIVETV_WEB_CLIENT_ID") ?: "" }
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // platform (no enforced): sin androidx.tv ya nada arrastra Compose 1.10.3;
    // el BOM fija Compose 1.7.6, compatible con compileSdk 34.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // NOTA Termux: core-ktx 1.13.1 y activity-compose 1.9.3 porque las
    // versiones 1.15.0/1.10.1 exigen compileSdk 35 y el aapt2 de Termux
    // (v2.19) no puede leer el android.jar de API 35. Se usa SDK 34.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // SIN androidx.tv: tv-material/tv-foundation exigen compileSdk 35
    // (verificado en sus AAR: minCompileSdk=35 hasta en alpha12).
    // La UI de TV usa material3 + foco propio (ui/tv/TvComponents.kt).

    // Reproductor (etapas 5-6)
    // v0.8.0: libmpv precompilado (FFmpeg + mpv con .so para arm64-v8a, etc.)
    implementation("dev.jdtech.mpv:libmpv:0.4.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")

    // Login con Google (etapa 2)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Carátulas de la biblioteca (v0.6.0): Coil 2.5.0, compatible con
    // compileSdk 34 y Compose BOM 2024.12.01. Cachea en disco solo.
    implementation("io.coil-kt:coil-compose:2.5.0")
}
