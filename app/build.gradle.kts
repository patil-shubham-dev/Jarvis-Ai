import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
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
        buildConfigField("String", "OPENAI_API_KEY",
            "\"${localProps.getProperty("OPENAI_API_KEY", "")}\"")
        buildConfigField("String", "BASE_URL",
            "\"https://api.openai.com/v1/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            force("androidx.fragment:fragment-ktx:1.6.2")
            force("androidx.fragment:fragment:1.6.2")
            force("androidx.room:room-runtime:2.6.1")
            force("androidx.room:room-ktx:2.6.1")
            force("androidx.navigation:navigation-common:2.7.7")
            force("androidx.navigation:navigation-runtime:2.7.7")
            
            // Final fix for Duplicate Class found
            dependencySubstitution {
                substitute(module("org.jetbrains:annotations-java5")).using(module("org.jetbrains:annotations:23.0.0"))
            }
        }
    }
}

val lifecycleVersion  = "2.6.2"
val navVersion        = "2.7.7"
val roomVersion       = "2.6.1"
val hiltVersion       = "2.48.1"
val coroutinesVersion = "1.7.3"

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Hilt
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-android-compiler:$hiltVersion")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Picovoice Porcupine - Offline Wake Word ("Hey Jarvis")
    implementation("ai.picovoice:porcupine-android:3.0.1")

    // ONNX Runtime - On-device Vector Embeddings (REMOVED)

    // Markdown Rendering - Pro AI text display
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2")

    // Streaming - Server-Sent Events
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Animations - Lottie
    implementation("com.airbnb.android:lottie:6.3.0")

    // Testing

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}