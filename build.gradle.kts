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
    api(libs.lettuce.core)
    api(libs.bundles.kotlinx.serialization)
    api(libs.kotlinx.coroutines.core)
    api(libs.bundles.logging)
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

java {
    withSourcesJar()
}

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
