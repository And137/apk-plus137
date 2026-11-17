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
package org.meshtastic.feature.node.fwplus

import org.meshtastic.core.model.Node
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LooksFwPlusTest {

    private fun withVersion(version: String) =
        Node(num = 1, metadata = DeviceMetadata.Builder().also { it.firmware_version = version }.build())

    @Test
    fun aNodeSeenRunningFwPlusLooksFwPlus() {
        assertTrue(Node(num = 1, fwPlusSeen = true).looksFwPlus)
    }

    @Test
    fun aFwPlusStatusLooksFwPlus() {
        assertTrue(Node(num = 1, fwPlusStatus = "on air").looksFwPlus)
    }

    @Test
    fun firmwarePlus137VersionsLookFwPlus() {
        assertTrue(withVersion("2.8.3.1371").looksFwPlus)
        assertTrue(withVersion("2.8.3.1371PR").looksFwPlus)
        assertTrue(withVersion("2.8.4.13712P").looksFwPlus)
        // board suffixes from build 1372
        listOf("Hy", "HyP", "HyR", "HyPR", "nF", "Xn", "Xs", "HeW", "He3", "He4").forEach {
            assertTrue(withVersion("2.8.3.1372$it").looksFwPlus, it)
        }
    }

    @Test
    fun upstreamAndOlderHydraVersionsDoNot() {
        assertFalse(withVersion("2.8.3.40PR").looksFwPlus)
        assertFalse(withVersion("2.8.3.abc1234").looksFwPlus)
        assertFalse(withVersion("2.8.3.137abcd").looksFwPlus) // upstream commit hash starting with 137
        assertFalse(withVersion("2.7.13").looksFwPlus)
        assertFalse(Node(num = 1).looksFwPlus)
    }
}
