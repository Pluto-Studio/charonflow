plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

group = "club.plutoproject.charonflow"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.lettuce.core)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.logging)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}

val publishUrl = uri(
    if (version.toString().endsWith("SNAPSHOT")) {
        "https://maven.nostal.ink/repository/maven-snapshots/"
    } else {
        "https://maven.nostal.ink/repository/maven-releases/"
    }
)

publishing {
    repositories {
        maven {
            name = "nostal"
            url = publishUrl
            credentials(PasswordCredentials::class)
        }
    }

    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
