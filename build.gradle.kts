import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.kotlin.serialization).version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.detekt)
    alias(deps.plugins.nexus.release)
    `maven-publish`
    signing
}

group = "com.turbomates.ktor.openapi"
version = System.getenv("RELEASE_VERSION") ?: "0.0.1"

repositories {
    mavenCentral()
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
    kotlinOptions.jvmTarget = "1.8"
}
detekt {
    toolVersion = deps.versions.detekt.get()
    autoCorrect = false
    parallel = true
    config = files("detekt.yml")
}
tasks.named("check").configure {
    this.setDependsOn(this.dependsOn.filterNot {
        it is TaskProvider<*> && it.name == "detekt"
    })
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "openapi"
            groupId = "com.turbomates.ktor"
            from(components["java"])
            pom {
                packaging = "jar"
                name.set("Kotlin Ktor OpenAPI documentation builder")
                url.set("https://github.com/turbomates/ktor-openAPI")
                description.set("Kotlin Ktor OpenAPI documentation builder")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/turbomates/ktor-openAPI/blob/main/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:https://github.com/turbomates/ktor-openAPI.git")
                    developerConnection.set("scm:git@github.com:turbomates/ktor-openAPI.git")
                    url.set("https://github.com/turbomates/ktor-openAPI")
                }

                developers {
                    developer {
                        id.set("shustrik")
                        name.set("Vadim Golodko")
                        email.set("vadim.golodko@gmail.com")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME") ?: project.properties["ossrhUsername"].toString()
                password = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD") ?: project.properties["ossrhPassword"].toString()
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    username = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME") ?: project.properties["ossrhUsername"].toString()
    password = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD") ?: project.properties["ossrhPassword"].toString()
}
