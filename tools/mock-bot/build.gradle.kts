import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    application
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

application {
    mainClass.set("com.proxy.mockbot.MainKt")
}

/** Native WebRTC-build voor het platform waarop de mock draait. */
val webrtcClassifier: String =
    run {
        val os: String = System.getProperty("os.name").lowercase()
        val arch: String = System.getProperty("os.arch").lowercase()
        val arm: Boolean = arch.contains("aarch64") || arch.contains("arm64")
        when {
            os.contains("mac") -> if (arm) "macos-aarch64" else "macos-x86_64"
            os.contains("win") -> "windows-x86_64"
            else -> if (arm) "linux-aarch64" else "linux-x86_64"
        }
    }

dependencies {
    implementation(project(":protocol"))
    implementation("dev.onvoid.webrtc:webrtc-java:0.20.0")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:0.20.0:$webrtcClassifier")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    testImplementation("junit:junit:4.13.2")
}
