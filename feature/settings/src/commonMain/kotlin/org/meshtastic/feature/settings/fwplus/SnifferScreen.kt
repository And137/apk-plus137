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
package org.meshtastic.feature.settings.fwplus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_sniffer
import org.meshtastic.core.resources.fwplus_sniffer_by_port
import org.meshtastic.core.resources.fwplus_sniffer_by_sender
import org.meshtastic.core.resources.fwplus_sniffer_empty
import org.meshtastic.core.resources.fwplus_sniffer_filter
import org.meshtastic.core.resources.fwplus_sniffer_hint
import org.meshtastic.core.resources.fwplus_sniffer_include_our_tx
import org.meshtastic.core.resources.fwplus_sniffer_meta
import org.meshtastic.core.resources.fwplus_sniffer_packets
import org.meshtastic.core.resources.fwplus_sniffer_stats
import org.meshtastic.core.resources.fwplus_sniffer_transit
import org.meshtastic.core.resources.fwplus_sniffer_transit_only
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.util.formatAgo

private const val MILLIS_PER_SECOND = 1000L
private const val TOP_STATS = 15

@Composable
fun SnifferScreen(viewModel: SnifferViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStats by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.fwplus_sniffer),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SnifferControls(
                    state = state,
                    showStats = showStats,
                    onShowStats = { showStats = it },
                    onText = viewModel::setText,
                    onTransitOnly = viewModel::setTransitOnly,
                    onIncludeOurTx = viewModel::setIncludeOurTx,
                )
            }
            when {
                state.entries.isEmpty() -> item { Text(stringResource(Res.string.fwplus_sniffer_empty)) }
                showStats -> item { SnifferStats(state) }
                else -> items(state.entries, key = { it.uuid }) { entry -> SnifferRow(entry, state.nodeNames) }
            }
        }
    }
}

@Composable
private fun SnifferControls(
    state: SnifferUiState,
    showStats: Boolean,
    onShowStats: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onTransitOnly: (Boolean) -> Unit,
    onIncludeOurTx: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.fwplus_sniffer_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = state.filters.text,
            onValueChange = onText,
            label = { Text(stringResource(Res.string.fwplus_sniffer_filter)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.filters.transitOnly,
                onClick = { onTransitOnly(!state.filters.transitOnly) },
                label = { Text(stringResource(Res.string.fwplus_sniffer_transit_only)) },
            )
            FilterChip(
                selected = state.filters.includeOurTx,
                onClick = { onIncludeOurTx(!state.filters.includeOurTx) },
                label = { Text(stringResource(Res.string.fwplus_sniffer_include_our_tx)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showStats,
                onClick = { onShowStats(false) },
                label = { Text(stringResource(Res.string.fwplus_sniffer_packets, state.entries.size)) },
            )
            FilterChip(
                selected = showStats,
                onClick = { onShowStats(true) },
                label = { Text(stringResource(Res.string.fwplus_sniffer_stats)) },
            )
        }
    }
}

@Composable
private fun SnifferRow(entry: SnifferEntry, names: Map<Int, String>) {
    fun name(num: Int) =
        if (num == NodeAddress.NODENUM_BROADCAST) "BROADCAST" else names[num] ?: NodeAddress.numToDefaultId(num)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    portLabel(entry.port),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatAgo((entry.receivedMillis / MILLIS_PER_SECOND).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${name(entry.from)} → ${name(entry.to)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    Res.string.fwplus_sniffer_meta,
                    entry.hops?.toString() ?: "?",
                    NumberFormatter.format(entry.snr, 1),
                    entry.rssi,
                    entry.channel,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (entry.transit) {
                Text(
                    stringResource(Res.string.fwplus_sniffer_transit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun SnifferStats(state: SnifferUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsBlock(stringResource(Res.string.fwplus_sniffer_by_port), state.byPort)
        StatsBlock(stringResource(Res.string.fwplus_sniffer_by_sender), state.bySender)
    }
}

@Composable
private fun StatsBlock(title: String, rows: List<Pair<String, Int>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            rows.take(TOP_STATS).forEach { (label, count) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
