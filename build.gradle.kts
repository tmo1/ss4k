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
    id("java")
    kotlin("jvm")
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "com.github.tmo1"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("cz.mormegil:lazysodium-java:6.2.0")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}