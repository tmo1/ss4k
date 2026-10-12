/*
 * SecretStream for Kotlin (SS4K): A simple, high level API for
 * streaming authenticated encryption
 *
 * Copyright (c) 2026 Thomas More
 *
 * This file is part of SS4K.
 *
 * SS4K is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SS4K is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SS4K.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.4.1"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "com.github.tmo1"
version = "0.1.0"

repositories {
    google() // required by the Android plugin and lazysodium-android's AndroidX dependency
    mavenCentral()
}

val jnaVersion = "5.19.1"
val lazySodiumVersion = "6.2.0"

kotlin {
    jvmToolchain(17)

    // Standard Java target: produces a regular JAR.
    jvm()

    // Android target: produces an AAR.
    android {
        namespace = "com.github.tmo1.ss4k"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
        }
    }
}

// Dependencies are declared per source set via configuration names. LazySodium is JNA-based,
// so it cannot go in commonMain: use expect/actual with implementations in jvmMain/androidMain.
dependencies {
    // Shared tests
    "commonTestImplementation"(kotlin("test"))

    // Standard Java
    "jvmMainApi"("cz.mormegil:lazysodium-java:$lazySodiumVersion")
    "jvmMainImplementation"("net.java.dev.jna:jna:$jnaVersion")

    "jvmTestImplementation"("org.slf4j:slf4j-simple:2.0.9")
    "jvmTestImplementation"(platform("org.junit:junit-bom:6.0.0"))
    "jvmTestImplementation"("org.junit.jupiter:junit-jupiter")
    "jvmTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    // Android: AAR variants bundle the native libraries. Do NOT also put a lazysodium-java
    // artifact on the Android classpath; the two libraries contain overlapping classes.
    "androidMainApi"("cz.mormegil:lazysodium-android:$lazySodiumVersion@aar")
    "androidMainImplementation"("net.java.dev.jna:jna:$jnaVersion@aar")
}

// KMP has no `test` task; this configures jvmTest (and any other Test task).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
