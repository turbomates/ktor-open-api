import java.time.Duration
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.kotlin.serialization).version(deps.versions.kotlin.asProvider().get())
    alias(deps.plugins.detekt)
    alias(deps.plugins.nexus.release)
    `maven-publish`
    signing
}

group = "com.turbomates"
version = "0.1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(deps.bundles.ktor)
    testImplementation(kotlin("test"))
    implementation(deps.kotlin.serialization)
    implementation(deps.kotlin.serialization.json)
    implementation(deps.kotlin.reflect)
    implementation(deps.swagger.webjar)
    testImplementation(deps.ktor.test)
    testImplementation(deps.openapi.validator)
    detektPlugins(deps.detekt.formatting)
}

// Ensure Kotlin uses JDK 21 toolchain
kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
    // Ensure all Java-related tasks (including tests) use JDK 21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Explicitly ensure tests run with the configured toolchain JDK
val javaToolchains = project.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java)
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "ktor-open-api"
            groupId = "com.turbomates"
            version = System.getenv("RELEASE_VERSION") ?: "0.1.0"
            from(components["java"])
            pom {
                packaging = "jar"
                name.set("Ktor Openapi extensions")
                url.set("https://github.com/turbomates/ktor-open-api")
                description.set("Extensions for ktor to generate openapi documentation")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/turbomates/ktor-open-api/blob/main/LICENSE")
                    }
                }

                scm {
                    connection.set("scm:https://github.com/turbomates/ktor-open-api.git")
                    developerConnection.set("scm:git@github.com:turbomates/ktor-open-api.git")
                    url.set("https://github.com/turbomates/ktor-open-api")
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
}
nexusPublishing {
    repositories {
        sonatype {
            // Central Portal OSSRH Staging API URLs
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username.set(
                System.getenv("ORG_GRADLE_PROJECT_SONATYPE_USERNAME")
                    ?: project.findProperty("centralPortalUsername")?.toString()
            )
            password.set(
                System.getenv("ORG_GRADLE_PROJECT_SONATYPE_PASSWORD")
                    ?: project.findProperty("centralPortalPassword")?.toString()
            )
        }
    }

    // Настройки тайм-аутов (опционально)
    connectTimeout.set(Duration.ofMinutes(3))
    clientTimeout.set(Duration.ofMinutes(6))

    transitionCheckOptions {
        maxRetries.set(80)
        delayBetween.set(Duration.ofSeconds(10))
    }
}
signing {
    sign(publishing.publications["mavenJava"])
}
