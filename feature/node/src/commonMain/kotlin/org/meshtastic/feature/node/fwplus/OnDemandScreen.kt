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
@file:Suppress("TooManyFunctions")

package org.meshtastic.feature.node.fwplus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_od_air_activity
import org.meshtastic.core.resources.fwplus_od_air_caption
import org.meshtastic.core.resources.fwplus_od_air_rx
import org.meshtastic.core.resources.fwplus_od_air_rx_bad
import org.meshtastic.core.resources.fwplus_od_air_tx
import org.meshtastic.core.resources.fwplus_od_average_last
import org.meshtastic.core.resources.fwplus_od_broadcast_assist
import org.meshtastic.core.resources.fwplus_od_empty
import org.meshtastic.core.resources.fwplus_od_fw_version
import org.meshtastic.core.resources.fwplus_od_fw_version_value
import org.meshtastic.core.resources.fwplus_od_hops
import org.meshtastic.core.resources.fwplus_od_last_contact
import org.meshtastic.core.resources.fwplus_od_no_response
import org.meshtastic.core.resources.fwplus_od_node_stats
import org.meshtastic.core.resources.fwplus_od_nodes
import org.meshtastic.core.resources.fwplus_od_nodes_direct
import org.meshtastic.core.resources.fwplus_od_noise_average
import org.meshtastic.core.resources.fwplus_od_noise_caption
import org.meshtastic.core.resources.fwplus_od_noise_floor
import org.meshtastic.core.resources.fwplus_od_noise_min_max
import org.meshtastic.core.resources.fwplus_od_noise_newest
import org.meshtastic.core.resources.fwplus_od_noise_no_data
import org.meshtastic.core.resources.fwplus_od_noise_now
import org.meshtastic.core.resources.fwplus_od_not_supported
import org.meshtastic.core.resources.fwplus_od_packet_log
import org.meshtastic.core.resources.fwplus_od_ping
import org.meshtastic.core.resources.fwplus_od_ping_ack
import org.meshtastic.core.resources.fwplus_od_ping_hops_direct
import org.meshtastic.core.resources.fwplus_od_ping_how_it_sees
import org.meshtastic.core.resources.fwplus_od_ping_how_we_see
import org.meshtastic.core.resources.fwplus_od_ping_relayed
import org.meshtastic.core.resources.fwplus_od_ping_result
import org.meshtastic.core.resources.fwplus_od_port_counters
import org.meshtastic.core.resources.fwplus_od_routing_errors
import org.meshtastic.core.resources.fwplus_od_routing_table
import org.meshtastic.core.resources.fwplus_od_rt_age
import org.meshtastic.core.resources.fwplus_od_rt_cost
import org.meshtastic.core.resources.fwplus_od_rt_dest
import org.meshtastic.core.resources.fwplus_od_rt_next_hop
import org.meshtastic.core.resources.fwplus_od_rt_source
import org.meshtastic.core.resources.fwplus_od_rx_avg_time
import org.meshtastic.core.resources.fwplus_od_rx_avg_time_caption
import org.meshtastic.core.resources.fwplus_od_rx_history
import org.meshtastic.core.resources.fwplus_od_rx_history_caption
import org.meshtastic.core.resources.fwplus_od_series_ma
import org.meshtastic.core.resources.fwplus_od_series_ma_noise
import org.meshtastic.core.resources.fwplus_od_series_noise
import org.meshtastic.core.resources.fwplus_od_series_packets
import org.meshtastic.core.resources.fwplus_od_signal
import org.meshtastic.core.resources.fwplus_od_waiting
import org.meshtastic.core.resources.fwplus_on_demand
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.fwplus.proto.OnDemandResponse
import org.meshtastic.fwplus.proto.RouteSource

private const val MA_WINDOW = 6
private const val WINDOW_MINUTES = 10
private const val MILLIS_PER_SECOND = 1000.0
private const val LAST_BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val RECENT_WINDOW = 20
private const val NOISE_MA_WINDOW = 3
private const val DBM = "dBm"

@Composable
fun OnDemandScreen(viewModel: OnDemandViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nodeName by viewModel.nodeName.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.fwplus_on_demand),
                subtitle = nodeName,
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(OnDemandQuery.entries.filter { it != OnDemandQuery.PING_ACK }) { query ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (query == OnDemandQuery.PING) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QueryButton(query, state, viewModel::onQueryClicked, Modifier.weight(1f))
                            QueryButton(OnDemandQuery.PING_ACK, state, viewModel::onQueryClicked, Modifier.weight(1f))
                        }
                        val pingOpen = state.expanded == OnDemandQuery.PING || state.expanded == OnDemandQuery.PING_ACK
                        state.expanded
                            ?.takeIf { pingOpen }
                            ?.let { open ->
                                ResultCard(open, state.results[open], nodes)
                            }
                    } else {
                        QueryButton(query, state, viewModel::onQueryClicked, Modifier.fillMaxWidth())
                        if (state.expanded == query) ResultCard(query, state.results[query], nodes)
                    }
                }
            }
        }
    }
}

private val OnDemandQuery.title: StringResource
    get() =
        when (this) {
            OnDemandQuery.RX_HISTORY -> Res.string.fwplus_od_rx_history
            OnDemandQuery.RX_AVG_TIME -> Res.string.fwplus_od_rx_avg_time
            OnDemandQuery.PORT_COUNTERS -> Res.string.fwplus_od_port_counters
            OnDemandQuery.NODES -> Res.string.fwplus_od_nodes
            OnDemandQuery.NODES_DIRECT -> Res.string.fwplus_od_nodes_direct
            OnDemandQuery.PACKET_LOG -> Res.string.fwplus_od_packet_log
            OnDemandQuery.AIR_ACTIVITY -> Res.string.fwplus_od_air_activity
            OnDemandQuery.NOISE_FLOOR -> Res.string.fwplus_od_noise_floor
            OnDemandQuery.NODE_STATS -> Res.string.fwplus_od_node_stats
            OnDemandQuery.BROADCAST_ASSIST -> Res.string.fwplus_od_broadcast_assist
            OnDemandQuery.ROUTING_ERRORS -> Res.string.fwplus_od_routing_errors
            OnDemandQuery.FW_VERSION -> Res.string.fwplus_od_fw_version
            OnDemandQuery.PING -> Res.string.fwplus_od_ping
            OnDemandQuery.PING_ACK -> Res.string.fwplus_od_ping_ack
            OnDemandQuery.ROUTING_TABLE -> Res.string.fwplus_od_routing_table
        }

@Composable
private fun QueryButton(
    query: OnDemandQuery,
    state: OnDemandUiState,
    onClick: (OnDemandQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting = state.results[query] is OnDemandResult.Waiting
    FilledTonalButton(onClick = { onClick(query) }, enabled = !waiting, modifier = modifier) {
        Text(
            stringResource(query.title),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (waiting) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun ResultCard(query: OnDemandQuery, result: OnDemandResult?, nodes: Map<Int, Node>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (result) {
                null,
                OnDemandResult.Waiting,
                -> Text(stringResource(Res.string.fwplus_od_waiting))

                OnDemandResult.NoResponse -> Text(stringResource(Res.string.fwplus_od_no_response))

                OnDemandResult.NotSupported -> Text(stringResource(Res.string.fwplus_od_not_supported))

                is OnDemandResult.Received -> ResultContent(query, result, nodes)
            }
        }
    }
}

@Composable
private fun ResultContent(query: OnDemandQuery, result: OnDemandResult.Received, nodes: Map<Int, Node>) {
    val r = result.response
    when (query) {
        OnDemandQuery.RX_HISTORY -> RxHistory(r.rx_packet_history?.rx_packet_history.orEmpty().reversed())

        OnDemandQuery.RX_AVG_TIME -> RxAvgTime(r.rx_avg_time_history?.rx_avg_history.orEmpty())

        OnDemandQuery.PORT_COUNTERS -> PortCounters(r)

        OnDemandQuery.NODES,
        OnDemandQuery.NODES_DIRECT,
        -> NodeList(r)

        OnDemandQuery.PACKET_LOG -> PacketLog(r, nodes)

        OnDemandQuery.AIR_ACTIVITY -> AirActivity(r)

        OnDemandQuery.NOISE_FLOOR -> NoiseFloorHistory(r)

        OnDemandQuery.NODE_STATS -> r.node_stats?.let { NodeStatsList(it) } ?: Empty()

        OnDemandQuery.BROADCAST_ASSIST -> r.broadcast_assist_stats?.let { BroadcastAssistList(it) } ?: Empty()

        OnDemandQuery.ROUTING_ERRORS -> RoutingErrors(r)

        OnDemandQuery.FW_VERSION ->
            r.fw_plus_version?.let {
                Text(stringResource(Res.string.fwplus_od_fw_version_value, it.version_number))
            } ?: Empty()

        OnDemandQuery.PING,
        OnDemandQuery.PING_ACK,
        -> PingResult(r, result.ping)

        OnDemandQuery.ROUTING_TABLE -> RoutingTable(r, nodes)
    }
}

@Composable private fun Empty() = Text(stringResource(Res.string.fwplus_od_empty))

@Composable private fun NoNoiseData() = Text(stringResource(Res.string.fwplus_od_noise_no_data))

@Composable
private fun RxHistory(oldestFirst: List<Int>) {
    if (oldestFirst.isEmpty()) return Empty()
    val packets = stringResource(Res.string.fwplus_od_series_packets)
    val ma = stringResource(Res.string.fwplus_od_series_ma)
    BucketLineChart(values = oldestFirst, description = packets, overlay = movingAverage(oldestFirst, MA_WINDOW))
    ChartLegend(listOf(MaterialTheme.colorScheme.primary to packets, MaterialTheme.colorScheme.tertiary to ma))
    Text(stringResource(Res.string.fwplus_od_rx_history_caption), style = MaterialTheme.typography.bodySmall)
    listOf(MA_WINDOW, RECENT_WINDOW, oldestFirst.size).distinct().forEach { n ->
        averageOfLast(oldestFirst, n)?.let { avg ->
            Text(stringResource(Res.string.fwplus_od_average_last, n, NumberFormatter.format(avg, 2)))
        }
    }
}

@Composable
private fun RxAvgTime(oldestFirst: List<Int>) {
    if (oldestFirst.isEmpty()) return Empty()
    BucketLineChart(values = oldestFirst, description = stringResource(Res.string.fwplus_od_rx_avg_time_caption))
    Text(stringResource(Res.string.fwplus_od_rx_avg_time_caption), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PortCounters(r: OnDemandResponse) {
    val entries = r.port_counter_history?.port_counter_history.orEmpty().sortedByDescending { it.count }
    if (entries.isEmpty()) return Empty()
    entries.forEach { KeyValue(portName(it.port), it.count.toString()) }
}

@Composable
private fun NodeList(r: OnDemandResponse) {
    val entries = r.node_list?.node_list.orEmpty().sortedBy { it.last_heard }
    if (entries.isEmpty()) return Empty()
    entries.forEach { entry ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val name = entry.long_name.ifBlank { NodeAddress.numToDefaultId(entry.node_id) }
            Text(
                if (entry.short_name.isNotBlank()) "$name (${entry.short_name})" else name,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.fwplus_od_last_contact, formatDuration(entry.last_heard.toUInt().toLong())),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(Res.string.fwplus_od_hops, entry.hops), style = MaterialTheme.typography.bodySmall)
            if (entry.hops == 0 && entry.rx_rssi != 0) {
                Text(
                    stringResource(Res.string.fwplus_od_signal, NumberFormatter.format(entry.snr, 2), entry.rx_rssi),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PacketLog(r: OnDemandResponse, nodes: Map<Int, Node>) {
    val entries = r.exchange_packet_log?.exchange_list.orEmpty()
    if (entries.isEmpty()) return Empty()
    entries.forEach {
        KeyValue("${nodeLabel(it.from_node, nodes)} → ${nodeLabel(it.to_node, nodes)}", portName(it.port_num))
    }
}

@Composable
private fun AirActivity(r: OnDemandResponse) {
    val windows =
        r.air_activity_history?.air_activity_history.orEmpty().map {
            AirWindow(rx = airPercent(it.rx_time), tx = airPercent(it.tx_time), rxBad = airPercent(it.rxBad_time))
        }
    if (windows.isEmpty()) return Empty()
    val caption = stringResource(Res.string.fwplus_od_air_caption)
    AirActivityChart(windows = windows, description = caption)
    ChartLegend(
        listOf(
            MaterialTheme.colorScheme.primary to stringResource(Res.string.fwplus_od_air_rx),
            MaterialTheme.colorScheme.tertiary to stringResource(Res.string.fwplus_od_air_tx),
            MaterialTheme.colorScheme.error to stringResource(Res.string.fwplus_od_air_rx_bad),
        ),
    )
    Text(caption, style = MaterialTheme.typography.bodySmall)
    windows.forEachIndexed { i, w ->
        val busy = NumberFormatter.format(w.rx + w.tx + w.rxBad, 1)
        KeyValue("+${(i + 1) * WINDOW_MINUTES} min", "$busy %")
    }
}

@Composable
private fun NoiseFloorHistory(r: OnDemandResponse) {
    val history = r.noise_floor_history
    val points = noiseFloorPoints(history?.noise_floor.orEmpty(), history?.age_secs.orEmpty())
    if (points.isEmpty()) NoNoiseData() else NoiseFloorChart(points)
}

@Composable
private fun NoiseFloorChart(points: List<NoiseFloorPoint>) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val values = points.map { it.dbm }
    // Time runs from the oldest average (left) to the moment the node built the response (right).
    val span = points.first().secondsAgo.coerceAtLeast(1)
    val positions = points.map { (span - it.secondsAgo).toFloat() / span }
    val series = stringResource(Res.string.fwplus_od_series_noise)
    val ma = stringResource(Res.string.fwplus_od_series_ma_noise)
    BucketLineChart(
        values = values,
        description = series,
        overlay = movingAverage(values, NOISE_MA_WINDOW).takeIf { values.size >= NOISE_MA_WINDOW },
        positions = positions.takeIf { points.size > 1 },
        floorAtZero = false,
        unit = DBM,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("-${formatDuration(points.first().secondsAgo)}", style = MaterialTheme.typography.labelSmall)
        Text(stringResource(Res.string.fwplus_od_noise_now), style = MaterialTheme.typography.labelSmall)
    }
    ChartLegend(listOf(MaterialTheme.colorScheme.primary to series, MaterialTheme.colorScheme.tertiary to ma))
    Text(stringResource(Res.string.fwplus_od_noise_caption), style = MaterialTheme.typography.bodySmall)
    KeyValue(stringResource(Res.string.fwplus_od_noise_newest), "${points.last().dbm} $DBM")
    KeyValue(stringResource(Res.string.fwplus_od_noise_min_max), "${values.min()} / ${values.max()} $DBM")
    KeyValue(
        stringResource(Res.string.fwplus_od_noise_average),
        "${NumberFormatter.format(values.average(), 1)} $DBM",
    )
}

@Composable
private fun RoutingErrors(r: OnDemandResponse) {
    val entries = r.routing_errors?.routing_errors.orEmpty()
    if (entries.isEmpty()) return Empty()
    entries.forEach { KeyValue(routingErrorName(it.num), it.counter.toString()) }
}

@Composable
private fun PingResult(r: OnDemandResponse, ping: PingMeasurement?) {
    if (ping == null) {
        Empty()
    } else {
        PingDetails(r, ping)
    }
}

@Composable
private fun PingDetails(r: OnDemandResponse, ping: PingMeasurement) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val seconds = NumberFormatter.format(ping.roundTripMillis / MILLIS_PER_SECOND, 2)
        Text(stringResource(Res.string.fwplus_od_ping_result, seconds), style = MaterialTheme.typography.titleMedium)
        if (ping.hops == 0) {
            val snr = NumberFormatter.format(ping.rxSnr, 1)
            Text(stringResource(Res.string.fwplus_od_ping_hops_direct))
            Text(stringResource(Res.string.fwplus_od_ping_how_we_see, ping.rxRssi, snr))
        } else {
            ping.hops?.let { Text(stringResource(Res.string.fwplus_od_hops, it)) }
        }
        val seenRssi = r.ping?.rx_rssi
        val seenSnr = r.ping?.snr
        if (seenRssi != null && seenSnr != null) {
            val snr = NumberFormatter.format(seenSnr, 1)
            Text(stringResource(Res.string.fwplus_od_ping_how_it_sees, seenRssi, snr))
        } else {
            Text(stringResource(Res.string.fwplus_od_ping_relayed), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RoutingTable(r: OnDemandResponse, nodes: Map<Int, Node>) {
    val routes = r.routing_table?.routes.orEmpty()
    if (routes.isEmpty()) return Empty()
    TableRow(
        stringResource(Res.string.fwplus_od_rt_dest),
        stringResource(Res.string.fwplus_od_rt_next_hop),
        stringResource(Res.string.fwplus_od_rt_cost),
        stringResource(Res.string.fwplus_od_rt_age),
        stringResource(Res.string.fwplus_od_rt_source),
        header = true,
    )
    routes.forEach { route ->
        TableRow(
            nodeLabel(route.dest, nodes),
            nextHopLabel(route.next_hop, nodes),
            NumberFormatter.format(route.cost, 2),
            formatDuration(route.age_secs.toUInt().toLong()),
            when (route.source) {
                RouteSource.ROUTE_SOURCE_DV_ETX -> "DV-ETX"
                RouteSource.ROUTE_SOURCE_LEGACY -> "Legacy"
                else -> "?"
            },
        )
    }
}

@Composable
private fun TableRow(vararg cells: String, header: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(if (i < 2) 2f else 1f).padding(end = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.SemiBold else null,
            )
        }
    }
}

@Composable
internal fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun nodeLabel(num: Int, nodes: Map<Int, Node>): String =
    nodes[num]?.effectiveLongName?.takeIf { it.isNotBlank() } ?: NodeAddress.numToDefaultId(num)

/**
 * The firmware reports the next hop as the last byte of its node number. Name it when exactly one known node ends in
 * that byte; otherwise show the byte and how many known nodes share it.
 */
private fun nextHopLabel(lastByte: Int, nodes: Map<Int, Node>): String {
    val matches = nodes.values.filter { (it.num and LAST_BYTE_MASK) == (lastByte and LAST_BYTE_MASK) }
    return when (matches.size) {
        1 -> matches.first().effectiveLongName
        else -> "?${lastByte.toString(HEX_RADIX).padStart(2, '0')} (${matches.size})"
    }
}
