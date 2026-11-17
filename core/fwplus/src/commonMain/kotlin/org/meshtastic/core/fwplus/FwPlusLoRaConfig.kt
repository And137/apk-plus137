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

import org.meshtastic.core.fwplus.FwPlusFieldNumbers.LORA_BT_POWER
import org.meshtastic.core.fwplus.FwPlusFieldNumbers.LORA_WIFI_POWER
import org.meshtastic.proto.Config

// sint32 travels zigzag-encoded: n -> (n << 1) ^ (n >> 31), read back as (v >>> 1) ^ -(v & 1).
private const val SIGN_SHIFT = 31
private const val UINT32_MASK = 0xFFFFFFFFL

private fun zigZagDecode(value: Long): Int = ((value ushr 1) xor -(value and 1)).toInt()

private fun zigZagEncode(value: Int): Long = ((value shl 1) xor (value shr SIGN_SHIFT)).toLong() and UINT32_MASK

/** FW+ Wi-Fi TX power in dBm (`LoRaConfig.wifi_power`); 0 means the firmware default. */
val Config.LoRaConfig.fwPlusWifiPowerDbm: Int
    get() = UnknownFields.readVarint(unknownFields, LORA_WIFI_POWER)?.let(::zigZagDecode) ?: 0

/** FW+ Bluetooth TX power in dBm (`LoRaConfig.bt_power`); 0 means the firmware default. */
val Config.LoRaConfig.fwPlusBtPowerDbm: Int
    get() = UnknownFields.readVarint(unknownFields, LORA_BT_POWER)?.let(::zigZagDecode) ?: 0

/** Returns a copy with the FW+ radio powers set. 0 is written as absent, as proto3 encodes a default sint32. */
fun Config.LoRaConfig.withFwPlusRadioPower(wifiDbm: Int, btDbm: Int): Config.LoRaConfig {
    val wifi = wifiDbm.takeIf { it != 0 }?.let(::zigZagEncode)
    val bt = btDbm.takeIf { it != 0 }?.let(::zigZagEncode)
    val fields =
        UnknownFields.writeVarint(UnknownFields.writeVarint(unknownFields, LORA_WIFI_POWER, wifi), LORA_BT_POWER, bt)
    return if (fields == unknownFields) this else newBuilder().apply { replaceUnknownFields(fields) }.build()
}
