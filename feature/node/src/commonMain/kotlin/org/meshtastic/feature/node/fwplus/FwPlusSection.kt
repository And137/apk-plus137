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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.fwplus.hasNodeMod
import org.meshtastic.core.fwplus.localStatsExtended
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.fwPlusVersionOf
import org.meshtastic.core.navigation.NodeDetailRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_on_demand
import org.meshtastic.core.resources.fwplus_on_demand_summary
import org.meshtastic.core.resources.fwplus_plus_statistics
import org.meshtastic.core.resources.fwplus_ps_count_share
import org.meshtastic.core.resources.fwplus_ps_flash
import org.meshtastic.core.resources.fwplus_ps_heap
import org.meshtastic.core.resources.fwplus_ps_history
import org.meshtastic.core.resources.fwplus_ps_online_nodes
import org.meshtastic.core.resources.fwplus_ps_psram
import org.meshtastic.core.resources.fwplus_ps_relay_canceled
import org.meshtastic.core.resources.fwplus_ps_reported
import org.meshtastic.core.resources.fwplus_ps_rx_avg
import org.meshtastic.core.resources.fwplus_ps_rx_bad
import org.meshtastic.core.resources.fwplus_ps_rx_dupe
import org.meshtastic.core.resources.fwplus_ps_rx_packets
import org.meshtastic.core.resources.fwplus_ps_tx_packets
import org.meshtastic.core.resources.fwplus_ps_used_of
import org.meshtastic.core.resources.fwplus_section
import org.meshtastic.core.resources.ic_podcasts
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.feature.node.component.SectionCard
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry

private const val PERCENT = 100.0

/**
 * True when there is a sign the node runs FW+ firmware: it was seen doing something only FW+ does (answered OnDemand,
 * sent NodeMod or extended local stats), its metadata reports NodeMod, or its firmware version is a firmware-plus137
 * one. A FW+ node that has done none of these still answers OnDemand, which is why FW+ features also show when our own
 * node is FW+.
 */
val Node.looksFwPlus: Boolean
    get() =
        fwPlusSeen ||
            metadata?.hasNodeMod == true ||
            fwPlusStatusLine != null ||
            fwPlusVersionOf(metadata?.firmware_version) != null

/**
 * FW+ features for one node: the "Plus statistics" the node broadcasts (its newest local stats and extended local
 * stats, when it sends them) and the OnDemand statistics screen.
 */
@Composable
fun FwPlusSection(
    node: Node,
    localStats: Telemetry?,
    extendedStats: Telemetry?,
    onAction: (NodeDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = Res.string.fwplus_section, modifier = modifier) {
        if (localStats?.local_stats != null || extendedStats?.localStatsExtended != null) {
            PlusStatistics(localStats, extendedStats)
            HorizontalDivider()
        }
        ListItem(
            text = stringResource(Res.string.fwplus_on_demand),
            supportingText = stringResource(Res.string.fwplus_on_demand_summary),
            leadingIcon = vectorResource(Res.drawable.ic_podcasts),
            onClick = { onAction(NodeDetailAction.Navigate(NodeDetailRoute.FwPlusOnDemand(node.num))) },
        )
    }
}

@Composable
private fun PlusStatistics(localStats: Telemetry?, extendedStats: Telemetry?) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.fwplus_plus_statistics), style = MaterialTheme.typography.titleSmall)
        val newest = listOfNotNull(localStats, extendedStats).maxOfOrNull { it.time } ?: 0
        if (newest > 0) {
            val age = (nowSeconds - newest).coerceAtLeast(0)
            Text(
                stringResource(Res.string.fwplus_ps_reported, formatDuration(age)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        localStats?.local_stats?.let { LocalStatsRows(it) }
        extendedStats?.localStatsExtended?.let { ext ->
            val heapUsed = ext.memory_total?.let { total -> total - (ext.memory_free_cheap ?: 0) }
            UsageRow(Res.string.fwplus_ps_heap, heapUsed, ext.memory_total)
            ext.memory_psram_total
                ?.takeIf { it > 0 }
                ?.let { total ->
                    UsageRow(Res.string.fwplus_ps_psram, total - (ext.memory_psram_free ?: 0), total)
                }
            UsageRow(Res.string.fwplus_ps_flash, ext.flash_used_bytes, ext.flash_total_bytes)
            KeyValue(stringResource(Res.string.fwplus_ps_rx_avg), NumberFormatter.format(ext.rx_avg_60_min, 1))
            val history = ext.rx_packet_history.reversed() // firmware sends the in-progress bucket first
            if (history.size > 1) {
                Text(stringResource(Res.string.fwplus_ps_history), style = MaterialTheme.typography.bodySmall)
                BucketLineChart(values = history, description = stringResource(Res.string.fwplus_ps_history))
            }
        }
    }
}

@Composable
private fun LocalStatsRows(stats: LocalStats) {
    val rx = stats.num_packets_rx
    KeyValue(stringResource(Res.string.fwplus_ps_online_nodes), "${stats.num_online_nodes} / ${stats.num_total_nodes}")
    KeyValue(stringResource(Res.string.fwplus_ps_tx_packets), stats.num_packets_tx.toString())
    KeyValue(stringResource(Res.string.fwplus_ps_rx_packets), rx.toString())
    val rxBad = countShare(stats.num_packets_rx_bad, rx + stats.num_packets_rx_bad)
    KeyValue(stringResource(Res.string.fwplus_ps_rx_bad), rxBad)
    KeyValue(stringResource(Res.string.fwplus_ps_rx_dupe), countShare(stats.num_rx_dupe, rx))
    KeyValue(stringResource(Res.string.fwplus_ps_relay_canceled), stats.num_tx_relay_canceled.toString())
}

@Composable
private fun countShare(count: Int, of: Int): String {
    val share = if (of > 0) NumberFormatter.format(count * PERCENT / of, 0) else "0"
    return stringResource(Res.string.fwplus_ps_count_share, count, share)
}

/** "used / total (percent)" for a memory or storage pair; skipped when the node did not report the total. */
@Composable
private fun UsageRow(label: StringResource, used: Int?, total: Int?) {
    if (total == null || total <= 0) return
    val usedBytes = (used ?: 0).toUInt().toLong()
    val totalBytes = total.toUInt().toLong()
    val decimal = { v: Double -> NumberFormatter.format(v, 1) }
    KeyValue(
        stringResource(label),
        stringResource(
            Res.string.fwplus_ps_used_of,
            formatBytes(usedBytes, decimal),
            formatBytes(totalBytes, decimal),
            NumberFormatter.format(usedBytes * PERCENT / totalBytes, 0),
        ),
    )
}
