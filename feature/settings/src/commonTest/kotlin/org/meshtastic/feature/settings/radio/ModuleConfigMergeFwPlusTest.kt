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
package org.meshtastic.feature.settings.radio

import org.meshtastic.core.fwplus.broadcastAssist
import org.meshtastic.core.fwplus.fwPlusModuleConfig
import org.meshtastic.core.fwplus.nodeMod
import org.meshtastic.core.fwplus.nodeModAdmin
import org.meshtastic.feature.settings.radio.component.formatNodeId
import org.meshtastic.feature.settings.radio.component.parseNodeId
import org.meshtastic.fwplus.proto.BroadcastAssistConfig
import org.meshtastic.fwplus.proto.NodeModAdminConfig
import org.meshtastic.fwplus.proto.NodeModConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleConfigMergeFwPlusTest {

    private val mqtt = ModuleConfig.MQTTConfig.Builder().also { it.enabled = true }.build()

    @Test
    fun fwPlusSectionsAreMergedAlongsideUpstreamOnes() {
        val status = NodeModConfig(text_status = "pod Lesznem")
        val admin = NodeModAdminConfig(sniffer_enabled = true)
        val assist = BroadcastAssistConfig(enabled = true, window_ms = 600)

        val merged =
            LocalModuleConfig.Builder()
                .build()
                .mergedWith(ModuleConfig.Builder().also { it.mqtt = mqtt }.build())
                .mergedWith(fwPlusModuleConfig(status))
                .mergedWith(fwPlusModuleConfig(admin))
                .mergedWith(fwPlusModuleConfig(assist))

        assertEquals(mqtt, merged.mqtt)
        assertEquals(status, merged.nodeMod)
        assertEquals(admin, merged.nodeModAdmin)
        assertEquals(assist, merged.broadcastAssist)
    }

    @Test
    fun aNewerFwPlusSectionReplacesTheOlderOne() {
        val merged =
            LocalModuleConfig.Builder()
                .build()
                .mergedWith(fwPlusModuleConfig(NodeModConfig(text_status = "old")))
                .mergedWith(fwPlusModuleConfig(NodeModConfig(text_status = "new", emoji = "!")))

        assertEquals(NodeModConfig(text_status = "new", emoji = "!"), merged.nodeMod)
    }

    @Test
    fun anUpstreamMergeKeepsFwPlusSections() {
        val admin = NodeModAdminConfig(position_limiter_enabled = true)

        val merged =
            LocalModuleConfig.Builder()
                .build()
                .mergedWith(fwPlusModuleConfig(admin))
                .mergedWith(ModuleConfig.Builder().also { it.mqtt = mqtt }.build())

        assertEquals(admin, merged.nodeModAdmin)
        assertNull(merged.nodeMod)
    }

    @Test
    fun redirectTargetRoundTripsAsNodeId() {
        assertEquals("!abcdef12", formatNodeId(0xabcdef12.toInt()))
        assertEquals(0xabcdef12.toInt(), parseNodeId("!abcdef12"))
        assertEquals(0x12, parseNodeId("12"))
        assertNull(parseNodeId("!xyz"))
        assertNull(parseNodeId("!123456789"))
        assertNull(parseNodeId(""))
    }
}
