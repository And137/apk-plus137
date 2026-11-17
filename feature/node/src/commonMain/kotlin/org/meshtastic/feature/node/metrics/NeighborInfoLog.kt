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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.getNeighborInfoResponse
import org.meshtastic.core.model.neighborInfo
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_ni_broadcast
import org.meshtastic.core.resources.fwplus_ni_received
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.routing_error_no_response
import org.meshtastic.core.resources.success
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Groups
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.core.ui.util.annotateNeighborInfo
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.feature.node.model.MetricsState

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NeighborInfoLogScreen(modifier: Modifier = Modifier, viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    fun getUsername(nodeNum: Int): String = with(viewModel.getUser(nodeNum)) { "$long_name ($short_name)" }

    val statusGreen = MaterialTheme.colorScheme.StatusGreen
    val statusYellow = MaterialTheme.colorScheme.StatusYellow
    val statusOrange = MaterialTheme.colorScheme.StatusOrange
    val statusRed = MaterialTheme.colorScheme.StatusRed
    val modemPreset = LocalModemPreset.current

    Scaffold(
        topBar = {
            val lastRequestNeighborsTime by viewModel.lastRequestNeighborsTime.collectAsStateWithLifecycle()
            MainAppBar(
                title = state.node?.effectiveLongName.orEmpty(),
                subtitle = stringResource(Res.string.neighbor_info),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        CooldownIconButton(
                            onClick = { viewModel.requestNeighborInfo() },
                            cooldownTimestamp = lastRequestNeighborsTime,
                        ) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            val entries =
                state.neighborInfoRequests.map { it to false } + unsolicitedNeighborInfo(state).map { it to true }
            items(entries.sortedByDescending { it.first.received_date }, key = { it.first.uuid }) { (log, received) ->
                val result =
                    if (received) {
                        log
                    } else {
                        remember(state.neighborInfoResults, log.fromRadio.packet?.id) {
                            state.neighborInfoResults.find {
                                it.fromRadio.packet?.decoded?.request_id == log.fromRadio.packet?.id
                            }
                        }
                    }

                val time = DateFormatter.formatDateTime(log.received_date)
                val text =
                    when {
                        received && log.fromRadio.packet?.to == NodeAddress.NODENUM_BROADCAST ->
                            stringResource(Res.string.fwplus_ni_broadcast)

                        received -> stringResource(Res.string.fwplus_ni_received)

                        result != null -> stringResource(Res.string.success)

                        else -> stringResource(Res.string.routing_error_no_response)
                    }
                val icon = if (result != null) MeshtasticIcons.Groups else MeshtasticIcons.PersonOff
                val header = stringResource(Res.string.neighbor_info)
                var expanded by remember { mutableStateOf(false) }

                Box {
                    MetricLogItem(
                        icon = icon,
                        text = "$time - $text",
                        contentDescription = stringResource(Res.string.neighbor_info),
                        modifier =
                        Modifier.combinedClickable(onLongClick = { expanded = true }) {
                            result
                                ?.fromRadio
                                ?.packet
                                ?.getNeighborInfoResponse(::getUsername, header = header)
                                ?.let {
                                    val message =
                                        annotateNeighborInfo(
                                            it,
                                            statusGreen = statusGreen,
                                            statusYellow = statusYellow,
                                            statusOrange = statusOrange,
                                            statusRed = statusRed,
                                            modemPreset = modemPreset,
                                        )
                                    viewModel.showLogDetail(Res.string.neighbor_info, message)
                                }
                        },
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DeleteItem {
                            viewModel.deleteLog(log.uuid)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

/**
 * Neighbor info the node sent without our asking: the periodic broadcast (on FW+ firmware also on the open primary
 * channel) or a reply to another phone's request. Replies to our own requests are shown under the request instead.
 */
private fun unsolicitedNeighborInfo(state: MetricsState): List<MeshLog> {
    val ourRequestIds = state.neighborInfoRequests.mapNotNull { it.fromRadio.packet?.id }.toSet()
    return state.neighborInfoResults.filter { log ->
        val packet = log.fromRadio.packet ?: return@filter false
        val decoded = packet.decoded ?: return@filter false
        log.fromNum != MeshLog.NODE_NUM_LOCAL &&
            !decoded.want_response &&
            decoded.request_id !in ourRequestIds &&
            packet.neighborInfo != null
    }
}
