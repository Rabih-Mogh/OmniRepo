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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Says whether the app will take a pack, before it is published:
// ./gradlew -q :core:checkPack -Ppack=/path/to/pack.json
tasks.register<JavaExec>("checkPack") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.spark.core.CheckPackKt")
    args(providers.gradleProperty("pack").orNull.orEmpty())
}
