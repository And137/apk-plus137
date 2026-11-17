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
package org.meshtastic.feature.settings.navigation

import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.ExcludedModules
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModuleRouteTest {

    private fun metadata(excluded: Int = 0, firmware: String = "2.8.0") = DeviceMetadata.Builder()
        .also { wb ->
            wb.firmware_version = firmware
            wb.excluded_modules = excluded
        }
        .build()

    private fun routes(excluded: Int = 0, role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.TAK) =
        ModuleRoute.filterExcludedFrom(metadata(excluded), role)

    /** Every route a non-FW+ node can be offered; the FW+ routes need `hasNodemod`. */
    private val upstreamRoutes = ModuleRoute.entries.filterNot { it.requiresFwPlus }

    @Test
    fun `every route carries its own excluded_modules bit`() {
        val bits = upstreamRoutes.map { it.excludedAs }

        assertEquals(bits.size, bits.toSet().size)
        assertFalse(ExcludedModules.EXCLUDED_NONE in bits)
    }

    @Test
    fun `fw plus routes carry no excluded_modules bit`() {
        assertEquals(listOf(null, null, null), ModuleRoute.entries.filter { it.requiresFwPlus }.map { it.excludedAs })
    }

    @Test
    fun `fw plus routes are listed only for nodes reporting hasNodemod`() {
        val fwPlusRoutes = ModuleRoute.entries.filter { it.requiresFwPlus }
        // DeviceMetadata.hasNodemod (field 21) set the way FW+ firmware encodes it.
        val fwPlusMetadata = DeviceMetadata.ADAPTER.decode(metadata().encode() + byteArrayOf(0xA8.toByte(), 0x01, 0x01))

        assertEquals(emptyList(), routes().filter { it.requiresFwPlus })
        assertEquals(
            fwPlusRoutes,
            ModuleRoute.filterExcludedFrom(fwPlusMetadata, Config.DeviceConfig.Role.TAK).filter { it.requiresFwPlus },
        )
    }

    @Test
    fun `a TAK role node on 2 8 firmware lists the TAK and Mesh Beacon modules`() {
        val listed = routes()

        assertContains(listed, ModuleRoute.TAK)
        assertContains(listed, ModuleRoute.MESH_BEACON)
    }

    @Test
    fun `the TAK bit hides the TAK module and nothing else`() {
        val listed = routes(excluded = ExcludedModules.TAK_CONFIG.value)

        assertEquals(upstreamRoutes - ModuleRoute.TAK, listed)
    }

    @Test
    fun `the Mesh Beacon bit hides the Mesh Beacon module and nothing else`() {
        val listed = routes(excluded = ExcludedModules.MESHBEACON_CONFIG.value)

        assertEquals(upstreamRoutes - ModuleRoute.MESH_BEACON, listed)
    }

    @Test
    fun `the status message and traffic management bits hide no module route`() {
        val excluded = ExcludedModules.STATUSMESSAGE_CONFIG.value or ExcludedModules.TRAFFICMANAGEMENT_CONFIG.value

        assertEquals(upstreamRoutes, routes(excluded = excluded))
    }

    @Test
    fun `an older bit still hides its module`() {
        val listed = routes(excluded = ExcludedModules.MQTT_CONFIG.value or ExcludedModules.PAXCOUNTER_CONFIG.value)

        assertEquals(upstreamRoutes - ModuleRoute.MQTT - ModuleRoute.PAXCOUNTER, listed)
    }

    @Test
    fun `metadata not read yet excludes nothing and leaves only the version gates`() {
        val listed = ModuleRoute.filterExcludedFrom(null, Config.DeviceConfig.Role.TAK)

        assertEquals(upstreamRoutes - ModuleRoute.TAK - ModuleRoute.MESH_BEACON, listed)
    }
}
