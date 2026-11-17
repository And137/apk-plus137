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
@file:Suppress("MagicNumber")

package org.meshtastic.core.fwplus

/** Port numbers FW+ firmware adds to `PortNum`. They are absent from the upstream enum. */
object FwPlusPortNum {
    /** [org.meshtastic.fwplus.proto.NodeMod] broadcasts: text status and emoji. */
    const val NODE_MOD_APP = 278

    /** Idle game; not implemented by firmware-plus137. */
    const val IDLE_GAME_APP = 322

    /** [org.meshtastic.fwplus.proto.OnDemand] requests and responses. */
    const val ON_DEMAND_APP = 354

    /** True for ports this app handles as FW+ traffic. */
    fun isFwPlusPort(port: Int): Boolean = port == NODE_MOD_APP || port == ON_DEMAND_APP
}

/** `AdminMessage.ModuleConfigType` values FW+ firmware adds. */
object FwPlusModuleConfigType {
    const val NODEMOD_CONFIG = 19
    const val NODEMOD_ADMIN_CONFIG = 20
    const val BROADCAST_ASSIST_CONFIG = 23
}

/** Field numbers of FW+ fields inside upstream messages. */
internal object FwPlusFieldNumbers {
    /** `Data.portnum`. */
    const val DATA_PORTNUM = 1

    /** `ModuleConfig.node_mod` and `LocalModuleConfig.node_mod`. */
    const val MODULE_NODE_MOD = 20

    /** `ModuleConfig.node_mod_admin` and `LocalModuleConfig.node_mod_admin`. */
    const val MODULE_NODE_MOD_ADMIN = 21

    /** `ModuleConfig.broadcast_assist` and `LocalModuleConfig.broadcast_assist`. */
    const val MODULE_BROADCAST_ASSIST = 24

    /** `Telemetry.local_stats_extended`. */
    const val TELEMETRY_LOCAL_STATS_EXTENDED = 20

    /** `DeviceMetadata.hasNodemod`. */
    const val DEVICE_METADATA_HAS_NODEMOD = 21

    /** `AdminMessage.get_module_config_request`. */
    const val ADMIN_GET_MODULE_CONFIG_REQUEST = 7

    /**
     * `Config.LoRaConfig.wifi_power` (sint32, dBm). firmware-plus137 used 108 until it was renumbered: 108 is
     * upstream's `transparent_mode`.
     */
    const val LORA_WIFI_POWER = 200

    /** `Config.LoRaConfig.bt_power` (sint32, dBm); 109 before the renumbering. */
    const val LORA_BT_POWER = 201
}
