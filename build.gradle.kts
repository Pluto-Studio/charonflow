plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
}

group = "club.plutoproject.charonflow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Redis 客户端
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // Kotlinx Serialization (CBOR 格式)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.0")

    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // 日志框架
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Apache Commons Pool (连接池)
    implementation("org.apache.commons:commons-pool2:2.12.0")
}

kotlin {
    jvmToolchain(21)
}
