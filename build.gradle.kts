import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.kotlin.serialization).version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.detekt)
    alias(deps.plugins.gradle.versions)
    alias(deps.plugins.nexus.release)
    `maven-publish`
    signing
}

group = "com.github.turbomates"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(deps.ktor.server.core)
    implementation(deps.ktor.locations)
    implementation(deps.ktor.webjar)
    implementation(deps.kotlin.serialization)
    implementation(deps.kotlin.serialization.json)
    implementation(deps.kotlin.reflect)
    implementation(deps.swagger.webjar)
    testImplementation(deps.ktor.test)
    testImplementation(deps.openapi.validator)
    detektPlugins(deps.detekt.formatting)
}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        apiVersion = "1.8"
        languageVersion = "1.9"
    }
}
detekt {
    toolVersion = deps.versions.detekt.get()
    autoCorrect = false
    parallel = true
    config.setFrom(files("detekt.yml"))
}
tasks.named("check").configure {
    this.setDependsOn(this.dependsOn.filterNot {
        it is TaskProvider<*> && it.name == "detekt"
    })
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
