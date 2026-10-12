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

pluginManagement {
    /*plugins {
        kotlin("jvm") version "2.4.0"
    }*/
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "ss4k"
include("ssef")