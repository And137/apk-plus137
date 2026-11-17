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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FwPlusFirmwareTest {

    @Test
    fun fwPlusVersionIsReadFromOldAndBoardSuffixedVersions() {
        assertEquals(1371, fwPlusVersionOf("2.8.3.1371PR"))
        assertEquals(1373, fwPlusVersionOf("2.8.3.1373He3"))
        assertEquals(1373, fwPlusVersionOf("2.8.3.1373nF"))
        assertNull(fwPlusVersionOf("2.8.2.d5f1619"))
        assertNull(fwPlusVersionOf("2.8.3.1372abc"))
        assertNull(fwPlusVersionOf(null))
    }

    @Test
    fun aFwPlusNodeMatchesTheReleaseVersionWithItsBoardSuffix() {
        assertTrue(firmwareVersionMatches("2.8.3.1373He3", "2.8.3.1373"))
        assertTrue(firmwareVersionMatches("2.8.3.1373HyPR", "2.8.3.1373"))
        assertTrue(firmwareVersionMatches("2.7.26.54e0d8d", "2.7.26.54e0d8d"))
        assertFalse(firmwareVersionMatches("2.8.3.1372He3", "2.8.3.1373"))
        assertFalse(firmwareVersionMatches("2.8.3.13730He3", "2.8.3.1373")) // another build, not a suffix
        assertFalse(firmwareVersionMatches(null, "2.8.3.1373"))
    }

    @Test
    fun aGitHubReleaseBecomesAFirmwareRelease() {
        val release =
            NetworkFwPlusRelease(
                tagName = "v2.8.3.1373",
                name = "firmware+ build 1373 (2.8.3.1373)",
                body = "notes",
                htmlUrl = "https://github.com/And137/firmware-plus137/releases/tag/v2.8.3.1373",
            )
                .asFirmwareRelease()
        assertEquals("v2.8.3.1373", release?.id)
        assertEquals("firmware+ build 1373 (2.8.3.1373)", release?.title)
        assertEquals("", release?.zipUrl)
        assertNull(NetworkFwPlusRelease(tagName = "v2.8.3.1374", draft = true).asFirmwareRelease())
    }

    @Test
    fun privateHwFwPlusBoardsGetAFallbackEntry() {
        val hw = fwPlusFallbackHardware(255, "hydraE22P_psram")
        assertEquals("hydraE22P_psram", hw?.platformioTarget)
        assertEquals(true, hw?.isEsp32Arc)
        assertNull(fwPlusFallbackHardware(255, "unknown-board"))
        assertNull(fwPlusFallbackHardware(255, null))
    }
}
