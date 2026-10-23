plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alexshell.raspisanie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alexshell.raspisanie"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "2.5.1"
    }

    // Постоянный ключ, чтобы новые сборки устанавливались поверх старых.
    signingConfigs {
        create("neon") {
            storeFile = file("neon-release.jks")
            storePassword = "neonraspisanie"
            keyAlias = "neon"
            keyPassword = "neonraspisanie"
            // Все схемы подписи — некоторые установщики (MIUI) капризны к APK только с v2.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("neon")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "xlsx"
    }
    packaging {
        // Нейросеть нужна только на телефонах (arm64): версия для эмуляторов x86_64 лишь утяжеляет APK.
        jniLibs.excludes += "lib/x86_64/liblitertlm_jni.so"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Локальная нейросеть (Gemma 4) — тот же движок, что в Google AI Edge Gallery.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
