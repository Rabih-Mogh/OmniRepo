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
    namespace = "app.spark"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.spark"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable APK.
            // Replace with your own signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The writer's brief ships with the app: it is what a model is told when the phone writes its own packs.
    sourceSets["main"].assets.srcDir("../content")

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

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // The screens are opened on the JVM (no phone needed) by the screens test.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    // A server on this machine standing in for GitHub.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
}
