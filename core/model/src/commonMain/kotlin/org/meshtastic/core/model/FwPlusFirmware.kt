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
package org.meshtastic.core.model

/**
 * firmware-plus137 firmware versions end in "137<build>" plus a board suffix: up to build 1371 only P (E22P) / R
 * (PSRAM), e.g. "2.8.3.1371PR"; from 1372 a board code first ("Hy", "HyPR", "nF", "Xn", "Xs", "HeW", "He3", ...). A
 * board code starts with a capital letter (or is "nF"), which keeps upstream versions ending in a lowercase hex commit
 * hash out.
 */
private val FW_PLUS_FIRMWARE_VERSION = Regex("""^\d+\.\d+\.\d+\.(137\d+)(?:nF|[A-Z][A-Za-z0-9]{0,4})?$""")

/** The FW+ version ("1371") inside a firmware-plus137 firmware version string, or null for any other firmware. */
fun fwPlusVersionOf(firmwareVersion: String?): Int? =
    firmwareVersion?.let { FW_PLUS_FIRMWARE_VERSION.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()

/**
 * True when a node reporting [reported] runs the release whose version is [expected]: equal, or (FW+) the release
 * version followed by the node's board suffix ("2.8.3.1373" -> "2.8.3.1373He3").
 */
fun firmwareVersionMatches(reported: String?, expected: String): Boolean = reported == expected ||
    (
        reported != null &&
            fwPlusVersionOf(reported) != null &&
            reported.startsWith(expected) &&
            reported.getOrNull(expected.length)?.isLetter() == true
        )

/** FW+ boards built as PRIVATE_HW (255) that the hardware catalog does not list, by PlatformIO env. */
private val FW_PLUS_PRIVATE_BOARDS = mapOf("hydraE22P" to "HydraE22P", "hydraE22P_psram" to "HydraE22P PSRAM")

/**
 * A stand-in catalog entry for a FW+ board the hardware catalog does not know ([hwModel] 255 builds such as
 * "hydraE22P_psram"), so firmware updates can still pick the release file named after [pioEnv]; null for any other
 * board.
 */
fun fwPlusFallbackHardware(hwModel: Int, pioEnv: String?): DeviceHardware? {
    val name = pioEnv?.let { FW_PLUS_PRIVATE_BOARDS[it] } ?: return null
    return DeviceHardware(
        activelySupported = true,
        architecture = "esp32",
        displayName = name,
        hwModel = hwModel,
        hwModelSlug = pioEnv.uppercase(),
        platformioTarget = pioEnv,
        tags = listOf("DIY"),
    )
}
