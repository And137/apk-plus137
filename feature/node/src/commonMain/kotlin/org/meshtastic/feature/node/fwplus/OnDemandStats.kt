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
@file:Suppress("MagicNumber") // power channel numbers 1..3

package org.meshtastic.feature.node.fwplus

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_no
import org.meshtastic.core.resources.fwplus_od_ba_attempts
import org.meshtastic.core.resources.fwplus_od_ba_enabled
import org.meshtastic.core.resources.fwplus_od_ba_last_reflood
import org.meshtastic.core.resources.fwplus_od_ba_sent
import org.meshtastic.core.resources.fwplus_od_ba_suppressed_airtime
import org.meshtastic.core.resources.fwplus_od_ba_suppressed_degree
import org.meshtastic.core.resources.fwplus_od_ba_suppressed_dup
import org.meshtastic.core.resources.fwplus_od_ns_airutil
import org.meshtastic.core.resources.fwplus_od_ns_battery
import org.meshtastic.core.resources.fwplus_od_ns_bytes
import org.meshtastic.core.resources.fwplus_od_ns_channel
import org.meshtastic.core.resources.fwplus_od_ns_chutil
import org.meshtastic.core.resources.fwplus_od_ns_cpu
import org.meshtastic.core.resources.fwplus_od_ns_dupes
import org.meshtastic.core.resources.fwplus_od_ns_firmware
import org.meshtastic.core.resources.fwplus_od_ns_flash
import org.meshtastic.core.resources.fwplus_od_ns_floods
import org.meshtastic.core.resources.fwplus_od_ns_heap
import org.meshtastic.core.resources.fwplus_od_ns_nodes
import org.meshtastic.core.resources.fwplus_od_ns_noise_floor
import org.meshtastic.core.resources.fwplus_od_ns_opportunistic
import org.meshtastic.core.resources.fwplus_od_ns_packets
import org.meshtastic.core.resources.fwplus_od_ns_psram
import org.meshtastic.core.resources.fwplus_od_ns_reboots
import org.meshtastic.core.resources.fwplus_od_ns_relays
import org.meshtastic.core.resources.fwplus_od_ns_uptime
import org.meshtastic.core.resources.fwplus_yes
import org.meshtastic.fwplus.proto.BroadcastAssistStats
import org.meshtastic.fwplus.proto.NodeStats

private fun bytes(value: Int?): String = formatBytes(value?.toUInt()?.toLong() ?: 0L) { NumberFormatter.format(it, 1) }

private fun pair(a: Int?, b: Int?): String = "${a ?: 0} / ${b ?: 0}"

/** The node's own counters, one labelled row each; rows the firmware left unset are skipped. */
@Composable
internal fun NodeStatsList(stats: NodeStats) {
    val yes = stringResource(Res.string.fwplus_yes)
    val no = stringResource(Res.string.fwplus_no)
    stats.uptime_seconds?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_uptime), formatDuration(it.toUInt().toLong()))
    }
    stats.reboots?.let { KeyValue(stringResource(Res.string.fwplus_od_ns_reboots), it.toString()) }
    stats.battery_level?.let { level ->
        val voltage = stats.voltage?.let { " (${NumberFormatter.format(it, 2)} V)" }.orEmpty()
        KeyValue(stringResource(Res.string.fwplus_od_ns_battery), "$level %$voltage")
    }
    KeyValue(stringResource(Res.string.fwplus_od_ns_nodes), pair(stats.num_online_nodes, stats.num_total_nodes))
    stats.channel_utilization?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_chutil), "${NumberFormatter.format(it, 1)} %")
    }
    stats.air_util_tx?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_airutil), "${NumberFormatter.format(it, 1)} %")
    }
    KeyValue(
        stringResource(Res.string.fwplus_od_ns_packets),
        "${stats.num_packets_tx ?: 0} / ${stats.num_packets_rx ?: 0} / ${stats.num_packets_rx_bad ?: 0}",
    )
    stats.num_rx_dupe?.let { KeyValue(stringResource(Res.string.fwplus_od_ns_dupes), it.toString()) }
    KeyValue(stringResource(Res.string.fwplus_od_ns_relays), pair(stats.num_tx_relay, stats.num_tx_relay_canceled))
    stats.memory_total?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_heap), "${bytes(stats.memory_free_cheap)} / ${bytes(it)}")
    }
    stats.memory_psram_total
        ?.takeIf { it > 0 }
        ?.let {
            KeyValue(stringResource(Res.string.fwplus_od_ns_psram), "${bytes(stats.memory_psram_free)} / ${bytes(it)}")
        }
    stats.flash_total_bytes?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_flash), "${bytes(stats.flash_used_bytes)} / ${bytes(it)}")
    }
    stats.cpu_usage_percent?.let { KeyValue(stringResource(Res.string.fwplus_od_ns_cpu), "$it %") }
    if (stats.rx_total_bytes != null || stats.tx_total_bytes != null) {
        KeyValue(
            stringResource(Res.string.fwplus_od_ns_bytes),
            "${bytes(stats.rx_total_bytes)} / ${bytes(stats.tx_total_bytes)}",
        )
    }
    listOf(
        Triple(1, stats.ch1_voltage, stats.ch1_current),
        Triple(2, stats.ch2_voltage, stats.ch2_current),
        Triple(3, stats.ch3_voltage, stats.ch3_current),
    )
        .forEach { (channel, volts, amps) ->
            if (volts != null && volts != 0f) {
                val current = amps?.let { ", ${NumberFormatter.format(it, 2)} mA" }.orEmpty()
                KeyValue(
                    stringResource(Res.string.fwplus_od_ns_channel, channel),
                    "${NumberFormatter.format(volts, 2)} V$current",
                )
            }
        }
    stats.opportunistic_enabled?.let {
        KeyValue(stringResource(Res.string.fwplus_od_ns_opportunistic), if (it) yes else no)
    }
    if (stats.flood_counter != null) {
        KeyValue(
            stringResource(Res.string.fwplus_od_ns_floods),
            "${stats.flood_counter} / ${stats.nexthop_counter ?: 0} / ${stats.blocked_by_hoplimit ?: 0}",
        )
    }
    stats.noise_floor?.let { KeyValue(stringResource(Res.string.fwplus_od_ns_noise_floor), "$it dBm") }
    stats.firmware_version?.let { KeyValue(stringResource(Res.string.fwplus_od_ns_firmware), it) }
}

@Composable
internal fun BroadcastAssistList(stats: BroadcastAssistStats) {
    val yes = stringResource(Res.string.fwplus_yes)
    val no = stringResource(Res.string.fwplus_no)
    KeyValue(stringResource(Res.string.fwplus_od_ba_enabled), if (stats.enabled == true) yes else no)
    KeyValue(stringResource(Res.string.fwplus_od_ba_attempts), (stats.reflood_attempts ?: 0).toString())
    KeyValue(stringResource(Res.string.fwplus_od_ba_sent), (stats.reflood_sent ?: 0).toString())
    KeyValue(stringResource(Res.string.fwplus_od_ba_suppressed_dup), (stats.suppressed_dup ?: 0).toString())
    KeyValue(stringResource(Res.string.fwplus_od_ba_suppressed_degree), (stats.suppressed_degree ?: 0).toString())
    KeyValue(stringResource(Res.string.fwplus_od_ba_suppressed_airtime), (stats.suppressed_airtime ?: 0).toString())
    stats.last_reflood_age_secs
        ?.takeIf { it > 0 }
        ?.let {
            KeyValue(stringResource(Res.string.fwplus_od_ba_last_reflood), formatDuration(it.toUInt().toLong()))
        }
}
