plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)  // ← ДОБАВЬТЕ ЭТУ СТРОКУ (вместо id("org.jetbrains.kotlin.kapt"))
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.activetour.wbbotcontroller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.activetour.wbbotcontroller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.3.21"  // ДОЛЖЕН СОВПАДАТЬ С КОТЛИНОМ
    }
}

// !!! НОВЫЙ БЛОК compilerOptions !!!
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)

    // Compose Runtime
    implementation(libs.androidx.compose.runtime.livedata)

    // Material Design & Views
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.databinding.runtime)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Ktor для HTTP запросов к WB API
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Telegram Bot API
    implementation(libs.telegrambots.longpolling)
    implementation(libs.telegrambots.client)

    implementation(libs.okhttp)

    // Gson для JSON
    implementation(libs.gson)

    // WorkManager для фоновых задач
    implementation(libs.androidx.work.runtime.ktx)

    // Preferences
    implementation(libs.androidx.preference.ktx)

    // QR Code генерация
//    implementation(libs.core.v354)
    implementation(libs.zxing.android.embedded)

    // PDF генерация для стикеров
    implementation(libs.itext7.core)

    // Permissions
    implementation(libs.permissionx)

    // Material Icons Extended
    implementation(libs.androidx.compose.material.icons.extended)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
