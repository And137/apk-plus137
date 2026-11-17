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

import com.squareup.wire.Message
import okio.ByteString
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.ADMIN_GET_MODULE_CONFIG_REQUEST
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.DATA_PORTNUM
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.DEVICE_METADATA_HAS_NODEMOD
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.MODULE_BROADCAST_ASSIST
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.MODULE_NODE_MOD
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.MODULE_NODE_MOD_ADMIN
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.TELEMETRY_LOCAL_STATS_EXTENDED
import org.meshtastic.fwplus.proto.BroadcastAssistConfig
import org.meshtastic.fwplus.proto.LocalStatsExtended
import org.meshtastic.fwplus.proto.NodeModAdminConfig
import org.meshtastic.fwplus.proto.NodeModConfig
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

// The upstream models are generated builders-only (no constructor, no copy()), so every write goes through a Builder
// and replaces its unknown fields wholesale.
internal fun Message.Builder<*, *>.replaceUnknownFields(fields: ByteString) {
    clearUnknownFields()
    addUnknownFields(fields)
}

// --- Data.portnum ------------------------------------------------------------------------------------------

/**
 * The packet's port number, including ports the upstream [PortNum] enum lacks. Wire decodes an unknown enum value as
 * the default ([PortNum.UNKNOWN_APP]) and keeps the real number in `unknownFields`; this reads it back from there.
 */
val Data.portNumValue: Int
    get() =
        if (portnum != PortNum.UNKNOWN_APP) {
            portnum.value
        } else {
            UnknownFields.readVarint(unknownFields, DATA_PORTNUM)?.toInt() ?: PortNum.UNKNOWN_APP.value
        }

/**
 * Sets the port to [port]. A port the upstream enum lacks is written as a raw `portnum` field, which encodes exactly as
 * the firmware's own enum value would.
 */
fun Data.Builder.portNumValue(port: Int): Data.Builder {
    val known = PortNum.fromValue(port)
    val current = build().unknownFields
    portnum = known ?: PortNum.UNKNOWN_APP
    val raw = if (known == null) port.toLong() else null
    replaceUnknownFields(UnknownFields.writeVarint(current, DATA_PORTNUM, raw))
    return this
}

/** Returns a copy addressed to [port]; see [Data.Builder.portNumValue]. */
fun Data.withPortNumValue(port: Int): Data = newBuilder().portNumValue(port).build()

// --- ModuleConfig (admin get/set payload) ------------------------------------------------------------------

val ModuleConfig.nodeMod: NodeModConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_NODE_MOD, NodeModConfig.ADAPTER)

val ModuleConfig.nodeModAdmin: NodeModAdminConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_NODE_MOD_ADMIN, NodeModAdminConfig.ADAPTER)

val ModuleConfig.broadcastAssist: BroadcastAssistConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_BROADCAST_ASSIST, BroadcastAssistConfig.ADAPTER)

/** True when this ModuleConfig carries any FW+ payload. */
val ModuleConfig.hasFwPlusPayload: Boolean
    get() = nodeMod != null || nodeModAdmin != null || broadcastAssist != null

private fun moduleConfigWith(fields: ByteString): ModuleConfig =
    ModuleConfig.Builder().apply { replaceUnknownFields(fields) }.build()

/** A `set_module_config` payload carrying [config] as `ModuleConfig.node_mod`. */
fun fwPlusModuleConfig(config: NodeModConfig): ModuleConfig =
    moduleConfigWith(UnknownFields.writeMessage(ByteString.EMPTY, MODULE_NODE_MOD, NodeModConfig.ADAPTER, config))

/** A `set_module_config` payload carrying [config] as `ModuleConfig.node_mod_admin`. */
fun fwPlusModuleConfig(config: NodeModAdminConfig): ModuleConfig = moduleConfigWith(
    UnknownFields.writeMessage(ByteString.EMPTY, MODULE_NODE_MOD_ADMIN, NodeModAdminConfig.ADAPTER, config),
)

/** A `set_module_config` payload carrying [config] as `ModuleConfig.broadcast_assist`. */
fun fwPlusModuleConfig(config: BroadcastAssistConfig): ModuleConfig = moduleConfigWith(
    UnknownFields.writeMessage(ByteString.EMPTY, MODULE_BROADCAST_ASSIST, BroadcastAssistConfig.ADAPTER, config),
)

// --- LocalModuleConfig (the app's cached copy of the radio's module config) ---------------------------------

val LocalModuleConfig.nodeMod: NodeModConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_NODE_MOD, NodeModConfig.ADAPTER)

val LocalModuleConfig.nodeModAdmin: NodeModAdminConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_NODE_MOD_ADMIN, NodeModAdminConfig.ADAPTER)

val LocalModuleConfig.broadcastAssist: BroadcastAssistConfig?
    get() = UnknownFields.readMessage(unknownFields, MODULE_BROADCAST_ASSIST, BroadcastAssistConfig.ADAPTER)

/**
 * Returns this LocalModuleConfig with every FW+ section present in [config] copied in. Upstream sections and FW+
 * sections that [config] does not carry are left untouched, mirroring how upstream sections are merged one at a time.
 */
fun LocalModuleConfig.withFwPlusSections(config: ModuleConfig): LocalModuleConfig {
    var fields = unknownFields
    config.nodeMod?.let { fields = UnknownFields.writeMessage(fields, MODULE_NODE_MOD, NodeModConfig.ADAPTER, it) }
    config.nodeModAdmin?.let {
        fields = UnknownFields.writeMessage(fields, MODULE_NODE_MOD_ADMIN, NodeModAdminConfig.ADAPTER, it)
    }
    config.broadcastAssist?.let {
        fields = UnknownFields.writeMessage(fields, MODULE_BROADCAST_ASSIST, BroadcastAssistConfig.ADAPTER, it)
    }
    return if (fields == unknownFields) this else newBuilder().apply { replaceUnknownFields(fields) }.build()
}

// --- Telemetry ---------------------------------------------------------------------------------------------

/** `Telemetry.local_stats_extended`, sent by FW+ nodes with "Local stats extended over mesh" enabled. */
val Telemetry.localStatsExtended: LocalStatsExtended?
    get() = UnknownFields.readMessage(unknownFields, TELEMETRY_LOCAL_STATS_EXTENDED, LocalStatsExtended.ADAPTER)

// --- DeviceMetadata ----------------------------------------------------------------------------------------

/** True when the radio reports FW+ NodeMod support (`DeviceMetadata.hasNodemod`). */
val DeviceMetadata.hasNodeMod: Boolean
    get() = (UnknownFields.readVarint(unknownFields, DEVICE_METADATA_HAS_NODEMOD) ?: 0L) != 0L

// --- AdminMessage ------------------------------------------------------------------------------------------

/**
 * The raw `get_module_config_request` value, including FW+ [FwPlusModuleConfigType] values the upstream enum lacks, or
 * null when the field is absent.
 */
val AdminMessage.moduleConfigRequestValue: Int?
    get() =
        get_module_config_request?.value
            ?: UnknownFields.readVarint(unknownFields, ADMIN_GET_MODULE_CONFIG_REQUEST)?.toInt()

/**
 * Requests the FW+ module config [type] (one of [FwPlusModuleConfigType]). Any other payload in the admin oneof must be
 * left unset, since the request is written as a raw field the generated oneof cannot see.
 */
fun AdminMessage.Builder.fwPlusModuleConfigRequest(type: Int): AdminMessage.Builder {
    val current = build().unknownFields
    get_module_config_request = null
    replaceUnknownFields(UnknownFields.writeVarint(current, ADMIN_GET_MODULE_CONFIG_REQUEST, type.toLong()))
    return this
}
