import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(props::load)
}

android {
    namespace = "com.jarvisai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvisai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL",
            "\"http://10.0.2.2:8000/\"")
        buildConfigField("String", "BASE_URL_DEBUG",
            "\"http://10.0.2.2:8000/\"")
        buildConfigField("String", "BASE_URL_RELEASE",
            "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"\"")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    configurations.all {
        resolutionStrategy {
            force(libs.androidx.fragment.ktx.get().toString())
            force("androidx.fragment:fragment:${libs.versions.fragment.get()}")
            force(libs.androidx.room.runtime.get().toString())
            force(libs.androidx.room.ktx.get().toString())
            force("androidx.navigation:navigation-common:${libs.versions.navigation.get()}")
            force("androidx.navigation:navigation-runtime:${libs.versions.navigation.get()}")

            dependencySubstitution {
                substitute(module("org.jetbrains:annotations-java5")).using(module("org.jetbrains:annotations:23.0.0"))
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.livedata)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.fragment.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Biometric
    implementation(libs.androidx.biometric)

    // Security - Encrypted SharedPreferences
    implementation(libs.androidx.security.crypto)

    // Picovoice Porcupine - Offline Wake Word
    implementation(libs.porcupine.android)

    // Markdown Rendering
    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax)

    // Streaming - Server-Sent Events
    implementation(libs.okhttp.sse)

    // Animations - Lottie
    implementation(libs.lottie)

    // Google ML Kit - On-device vision
    implementation(libs.mlkit.ocr)
    implementation(libs.mlkit.labeling)
    implementation(libs.mlkit.labeling.custom)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
