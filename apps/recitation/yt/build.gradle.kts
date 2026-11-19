import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Reads playlists and finds a video's audio. YouTube changes often; bump this when it stops working.
    api("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // LiveCheck talks to YouTube, so it only runs when asked for: YT_LIVE=1 ./gradlew :yt:test
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}
