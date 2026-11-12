import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = providers.gradleProperty("GROUP").orElse("io.github.sorjoh").get()
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0").get()

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js(IR) {
        browser()
        nodejs {
            testTask {
                // High-precision transcendental tests can take longer than
                // Mocha's 2 second default on a cold JavaScript runtime.
                useMocha { timeout = "60s" }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KmpBigDecimal"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.sorjoh.bigdecimal"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("kmp-bigdecimal")
            description.set(
                "Dependency-free arbitrary-precision BigDecimal for Kotlin Multiplatform",
            )
            url.set("https://github.com/sorjoh/kmp-bigdecimal")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("sorjoh")
                    name.set("Sören Johansson")
                    email.set("soren@sojo.se")
                    url.set("https://github.com/sorjoh")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/sorjoh/kmp-bigdecimal.git")
                developerConnection.set("scm:git:ssh://github.com/sorjoh/kmp-bigdecimal.git")
                url.set("https://github.com/sorjoh/kmp-bigdecimal")
            }
        }
    }
}
