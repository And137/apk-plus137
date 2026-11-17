/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Firmware-plus (FW+) protocol support: models generated from the FW+ firmware protos in
// src/commonMain/proto, plus accessors for FW+ fields carried inside upstream messages.
// The upstream org.meshtastic:protobufs SDK stays the single source of the standard models.
plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.wire)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.meshtastic.protobufs)
            api(libs.wire.runtime)
            api(libs.okio)
            implementation(libs.kermit)
        }
    }
}

wire {
    // Same shape as the upstream SDK: oneof members as nullable properties on the parent message.
    kotlin { oneofMode = "flat" }
}
