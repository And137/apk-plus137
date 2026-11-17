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
package org.meshtastic.core.fwplus

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.fwplus.proto.BroadcastAssistConfig
import org.meshtastic.fwplus.proto.LocalStatsExtended
import org.meshtastic.fwplus.proto.NodeModAdminConfig
import org.meshtastic.fwplus.proto.NodeModConfig
import org.meshtastic.fwplus.proto.OnDemand
import org.meshtastic.fwplus.proto.OnDemandRequest
import org.meshtastic.fwplus.proto.OnDemandType
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FwPlusProtoExtensionsTest {

    /** Encodes fields the way the firmware's nanopb encoder does: in field-number order, one tag per field. */
    private fun encode(block: ProtoWriter.() -> Unit): ByteString {
        val buffer = Buffer()
        ProtoWriter(buffer).block()
        return buffer.readByteString()
    }

    private fun ProtoWriter.varint(tag: Int, value: Long) {
        writeTag(tag, FieldEncoding.VARINT)
        writeVarint64(value)
    }

    private fun ProtoWriter.bytes(tag: Int, value: ByteString) {
        writeTag(tag, FieldEncoding.LENGTH_DELIMITED)
        writeVarint32(value.size)
        writeBytes(value)
    }

    private fun mqttEnabled(): ModuleConfig.MQTTConfig =
        ModuleConfig.MQTTConfig.Builder().also { it.enabled = true }.build()

    private fun moduleConfigWithMqtt(): ModuleConfig = ModuleConfig.Builder().also { it.mqtt = mqttEnabled() }.build()

    // --- Data.portnum --------------------------------------------------------------------------------------

    @Test
    fun onDemandPortSurvivesDecodeAsUnknownEnumValue() {
        val payload = OnDemand(request = OnDemandRequest(OnDemandType.REQUEST_NODE_STATS)).encodeByteString()
        val wire = encode {
            varint(1, FwPlusPortNum.ON_DEMAND_APP.toLong())
            bytes(2, payload)
        }

        val data = Data.ADAPTER.decode(wire)

        assertEquals(PortNum.UNKNOWN_APP, data.portnum)
        assertEquals(FwPlusPortNum.ON_DEMAND_APP, data.portNumValue)
        assertEquals(payload, data.payload)
    }

    @Test
    fun knownPortIsReadFromTheEnum() {
        assertEquals(
            PortNum.TEXT_MESSAGE_APP.value,
            Data.Builder().also { it.portnum = PortNum.TEXT_MESSAGE_APP }.build().portNumValue,
        )
    }

    @Test
    fun missingPortReadsAsUnknownApp() {
        assertEquals(0, Data.Builder().build().portNumValue)
    }

    @Test
    fun withPortNumValueEncodesFwPlusPortLikeTheFirmware() {
        val payload = "x".encodeUtf8()
        val data = Data.Builder().also { it.payload = payload }.build().withPortNumValue(FwPlusPortNum.NODE_MOD_APP)

        val decoded = Data.ADAPTER.decode(data.encodeByteString())

        assertEquals(FwPlusPortNum.NODE_MOD_APP, decoded.portNumValue)
        assertEquals(payload, decoded.payload)
    }

    @Test
    fun withPortNumValueReplacesARawPortWithAKnownOne() {
        val data =
            Data.Builder()
                .build()
                .withPortNumValue(FwPlusPortNum.ON_DEMAND_APP)
                .withPortNumValue(PortNum.ADMIN_APP.value)

        assertEquals(PortNum.ADMIN_APP, data.portnum)
        assertEquals(ByteString.EMPTY, data.unknownFields)
    }

    // --- ModuleConfig / LocalModuleConfig ------------------------------------------------------------------

    @Test
    fun nodeModAdminIsReadFromAFirmwareModuleConfig() {
        val admin =
            NodeModAdminConfig(
                sniffer_enabled = true,
                telemetry_limiter_enabled = true,
                telemetry_limiter_packets_per_minute = 8,
                additional_txutil = 50f,
                opportunistic_base_delay_ms = 120,
            )
        val wire = encode { bytes(FwPlusFieldNumbers.MODULE_NODE_MOD_ADMIN, admin.encodeByteString()) }

        val config = ModuleConfig.ADAPTER.decode(wire)

        assertEquals(admin, config.nodeModAdmin)
        assertNull(config.nodeMod)
        assertNull(config.broadcastAssist)
        assertTrue(config.hasFwPlusPayload)
    }

    @Test
    fun upstreamModuleConfigHasNoFwPlusPayload() {
        assertFalse(moduleConfigWithMqtt().hasFwPlusPayload)
    }

    @Test
    fun fwPlusModuleConfigRoundTripsThroughTheUpstreamAdapter() {
        val status = NodeModConfig(text_status = "Lasocice pod Lesznem", emoji = "🐂")
        val assist =
            BroadcastAssistConfig(enabled = true, window_ms = 600, jitter_ms = 400, allowed_ports = listOf(1, 3))

        val statusBytes = fwPlusModuleConfig(status).encodeByteString()
        val assistBytes = fwPlusModuleConfig(assist).encodeByteString()

        assertEquals(status, ModuleConfig.ADAPTER.decode(statusBytes).nodeMod)
        assertEquals(assist, ModuleConfig.ADAPTER.decode(assistBytes).broadcastAssist)
        // Same bytes the firmware would put on the wire for ModuleConfig{node_mod: status}.
        assertEquals(encode { bytes(FwPlusFieldNumbers.MODULE_NODE_MOD, status.encodeByteString()) }, statusBytes)
    }

    @Test
    fun localModuleConfigMergesOneFwPlusSectionAtATime() {
        val status = NodeModConfig(text_status = "a")
        val admin = NodeModAdminConfig(position_limiter_enabled = true)
        val newStatus = NodeModConfig(text_status = "b", emoji = "!")

        val local =
            LocalModuleConfig.Builder()
                .also { it.mqtt = mqttEnabled() }
                .build()
                .withFwPlusSections(fwPlusModuleConfig(status))
                .withFwPlusSections(fwPlusModuleConfig(admin))
                .withFwPlusSections(fwPlusModuleConfig(newStatus))

        val decoded = LocalModuleConfig.ADAPTER.decode(local.encodeByteString())
        assertEquals(newStatus, decoded.nodeMod)
        assertEquals(admin, decoded.nodeModAdmin)
        assertEquals(true, decoded.mqtt?.enabled)
    }

    @Test
    fun mergingAnUpstreamSectionLeavesLocalModuleConfigUnchanged() {
        val local =
            LocalModuleConfig.Builder().build().withFwPlusSections(fwPlusModuleConfig(NodeModConfig(text_status = "a")))

        assertEquals(local, local.withFwPlusSections(moduleConfigWithMqtt()))
    }

    // --- Telemetry / DeviceMetadata / AdminMessage ---------------------------------------------------------

    @Test
    fun localStatsExtendedIsReadFromTelemetry() {
        val stats =
            LocalStatsExtended(
                memory_total = 243_000,
                memory_free_cheap = 109_000,
                flash_used_bytes = 40_960,
                flash_total_bytes = 1_048_576,
                rx_packet_history = listOf(33, 35, 36),
            )
        val wire = encode {
            writeTag(1, FieldEncoding.FIXED32) // Telemetry.time is fixed32
            writeFixed32(1_700_000_000)
            bytes(FwPlusFieldNumbers.TELEMETRY_LOCAL_STATS_EXTENDED, stats.encodeByteString())
        }

        val telemetry = Telemetry.ADAPTER.decode(wire)

        assertEquals(stats, telemetry.localStatsExtended)
    }

    @Test
    fun telemetryWithoutExtendedStatsReadsNull() {
        assertNull(Telemetry.Builder().also { it.time = 1 }.build().localStatsExtended)
    }

    @Test
    fun hasNodeModIsReadFromDeviceMetadata() {
        val wire = encode {
            bytes(1, "2.8.3.40PR".encodeUtf8()) // firmware_version
            varint(FwPlusFieldNumbers.DEVICE_METADATA_HAS_NODEMOD, 1)
        }

        val metadata = DeviceMetadata.ADAPTER.decode(wire)

        assertTrue(metadata.hasNodeMod)
        assertFalse(DeviceMetadata.Builder().also { it.firmware_version = "2.8.2" }.build().hasNodeMod)
    }

    @Test
    fun fwPlusModuleConfigRequestEncodesTheRawEnumValue() {
        val request =
            AdminMessage.Builder().fwPlusModuleConfigRequest(FwPlusModuleConfigType.NODEMOD_ADMIN_CONFIG).build()

        val bytes = request.encodeByteString()

        assertEquals(
            encode { varint(FwPlusFieldNumbers.ADMIN_GET_MODULE_CONFIG_REQUEST, 20) },
            bytes,
        )
        assertEquals(
            FwPlusModuleConfigType.NODEMOD_ADMIN_CONFIG,
            AdminMessage.ADAPTER.decode(bytes).moduleConfigRequestValue,
        )
    }

    @Test
    fun radioPowersAreReadAsFirmwareEncodesThem() {
        val wire = encode {
            varint(FwPlusFieldNumbers.LORA_WIFI_POWER, 34) // sint32 17 -> zigzag 34
            varint(FwPlusFieldNumbers.LORA_BT_POWER, 11) // sint32 -6 -> zigzag 11
        }

        val lora = Config.LoRaConfig.ADAPTER.decode(wire)

        assertEquals(17, lora.fwPlusWifiPowerDbm)
        assertEquals(-6, lora.fwPlusBtPowerDbm)
    }

    @Test
    fun radioPowersRoundTripAndZeroClearsTheField() {
        val lora = Config.LoRaConfig.Builder().also { it.hop_limit = 3 }.build().withFwPlusRadioPower(21, -12)

        val decoded = Config.LoRaConfig.ADAPTER.decode(lora.encodeByteString())
        assertEquals(21, decoded.fwPlusWifiPowerDbm)
        assertEquals(-12, decoded.fwPlusBtPowerDbm)
        assertEquals(3, decoded.hop_limit)

        val cleared = decoded.withFwPlusRadioPower(0, 0)
        assertEquals(ByteString.EMPTY, cleared.unknownFields)
        assertEquals(0, cleared.fwPlusWifiPowerDbm)
    }

    @Test
    fun malformedUnknownFieldsReadAsAbsent() {
        val config = ModuleConfig.ADAPTER.decode(encode { varint(FwPlusFieldNumbers.MODULE_NODE_MOD, 5) })

        assertNull(config.nodeMod)
    }
}
