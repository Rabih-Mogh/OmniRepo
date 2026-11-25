import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every published build needs a higher version than the last, or installed copies won't
// see it as an update. The repo's commit count gives that for free, because a build is
// always made just before its commit.
val buildNumber: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt() + 1 + 4 // four commits were taken out of the history on 2026-11-19; the 4 keeps versions climbing
} catch (_: Exception) {
    1 // not a git checkout
}

android {
    namespace = "app.recitation"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.recitation"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        // The speech runtime is native code; only 64-bit ARM phones are built for.
        ndk { abiFilters += "arm64-v8a" }
    }

    // ONNX Runtime's native library is 34 MB, nine tenths of the app, and the same in every
    // build. It is left out of the package and put on the phone once (see speech/EngineStore.kt).
    // The small library that joins it to the Java side (120 KB) stays in: on Android, ONNX
    // Runtime will only load that one from the package itself.
    packaging {
        jniLibs {
            excludes += setOf("**/libonnxruntime.so")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable APK.
            // Replace with your own signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipe Extractor uses newer Java library calls than Android 8 has.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // The screens test saves a picture of each screen to app/build/screens.
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "2g"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":yt"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Downloads and prepares recordings in the background, carrying on after interruptions.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Runs the speech model (ONNX) on the phone.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")

    // Plays the recitation, also with the screen off, with controls in the notification.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // The screens are opened on the JVM (no phone needed) by the screens test.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.work:work-testing:2.10.0")
    // A player that runs on the test's own clock, for what the playback service asks of it.
    testImplementation("androidx.media3:media3-test-utils:1.5.1")
    testImplementation("androidx.media3:media3-test-utils-robolectric:1.5.1")
    // A server on this machine standing in for Quran.com.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
}
