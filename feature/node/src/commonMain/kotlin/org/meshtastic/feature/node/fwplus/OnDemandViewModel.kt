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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.fwplus.FwPlusPortNum
import org.meshtastic.core.fwplus.portNumValue
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.fwplus.proto.OnDemand
import org.meshtastic.fwplus.proto.OnDemandResponse
import org.meshtastic.proto.MeshPacket
import kotlin.time.Duration.Companion.seconds

/** What the screen shows under one query button. */
sealed interface OnDemandResult {
    data object Waiting : OnDemandResult

    data object NoResponse : OnDemandResult

    data class Received(val response: OnDemandResponse, val ping: PingMeasurement? = null) : OnDemandResult
}

/** Round trip of a ping and how we heard the reply. */
data class PingMeasurement(
    val roundTripMillis: Long,
    /** Hops the reply travelled, or null when the packet did not say. */
    val hops: Int?,
    val rxRssi: Int,
    val rxSnr: Float,
)

data class OnDemandUiState(
    /** The query whose result is open; one at a time, as FW+ clients show it. */
    val expanded: OnDemandQuery? = null,
    val results: Map<OnDemandQuery, OnDemandResult> = emptyMap(),
)

@KoinViewModel
class OnDemandViewModel(
    @InjectedParam val destNum: Int,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    serviceRepository: ServiceRepository,
) : ViewModel() {

    private val assembler = OnDemandAssembler()
    private val pendingSince = mutableMapOf<OnDemandQuery, Long>()
    private val timeouts = mutableMapOf<OnDemandQuery, Job>()

    private val _state = MutableStateFlow(OnDemandUiState())
    val state: StateFlow<OnDemandUiState> = _state.asStateFlow()

    /** Known nodes, for naming node numbers in lists and the routing table. */
    val nodes: StateFlow<Map<Int, Node>> = nodeRepository.nodeDBbyNum

    val nodeName: StateFlow<String> =
        nodeRepository.nodeDBbyNum
            .map { it[destNum]?.effectiveLongName ?: NodeAddress.numToDefaultId(destNum) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                NodeAddress.numToDefaultId(destNum),
            )

    init {
        viewModelScope.launch { serviceRepository.meshPacketFlow.collect(::onPacket) }
    }

    /** Opens [query]'s result and sends the request; tapping the open query again closes it. */
    fun onQueryClicked(query: OnDemandQuery) {
        if (_state.value.expanded == query && _state.value.results[query] !is OnDemandResult.Waiting) {
            _state.update { it.copy(expanded = null) }
            return
        }
        send(query)
    }

    private fun send(query: OnDemandQuery) {
        // NODES and NODES_DIRECT share a response type: only one of them may wait at a time.
        OnDemandQuery.entries.filter { it != query && it.response == query.response }.forEach { cancel(it) }
        assembler.reset(query.response)
        pendingSince[query] = nowMillis
        _state.update { it.copy(expanded = query, results = it.results + (query to OnDemandResult.Waiting)) }

        val packet =
            DataPacket(
                to = NodeAddress.numToDefaultId(destNum),
                bytes = query.requestPayload(),
                dataType = FwPlusPortNum.ON_DEMAND_APP,
                channel = nodeRepository.nodeDBbyNum.value[destNum]?.channel ?: 0,
                wantAck = false,
            )
        viewModelScope.launch {
            safeCatching { commandSender.sendData(packet) }
                .onFailure { Logger.w(it) { "OnDemand ${query.name} send failed" } }
        }
        timeouts[query]?.cancel()
        timeouts[query] = viewModelScope.launch {
            delay(RESPONSE_TIMEOUT)
            if (pendingSince.remove(query) != null) {
                assembler.reset(query.response)
                _state.update { it.copy(results = it.results + (query to OnDemandResult.NoResponse)) }
            }
        }
    }

    private fun cancel(query: OnDemandQuery) {
        timeouts.remove(query)?.cancel()
        if (pendingSince.remove(query) != null) {
            _state.update { it.copy(results = it.results - query) }
        }
    }

    private fun onPacket(packet: MeshPacket) {
        val (query, response) = completedResponse(packet) ?: return
        val sentAt = pendingSince.remove(query) ?: return
        timeouts.remove(query)?.cancel()

        val ping =
            if (query == OnDemandQuery.PING || query == OnDemandQuery.PING_ACK) {
                PingMeasurement(
                    roundTripMillis = nowMillis - sentAt,
                    hops = (packet.hop_start - packet.hop_limit).takeIf { packet.hop_start > 0 && it >= 0 },
                    rxRssi = packet.rx_rssi ?: 0,
                    rxSnr = packet.rx_snr,
                )
            } else {
                null
            }
        _state.update { it.copy(results = it.results + (query to OnDemandResult.Received(response, ping))) }
    }

    /** The pending query [packet] completes, with its (reassembled) response; null for anything else. */
    private fun completedResponse(packet: MeshPacket): Pair<OnDemandQuery, OnDemandResponse>? {
        val payload =
            packet.decoded?.takeIf { it.portNumValue == FwPlusPortNum.ON_DEMAND_APP && isFromTarget(packet) }?.payload
        val response = payload?.let { OnDemand.ADAPTER.decodeOrNull(it, Logger) }?.let(assembler::accept)
        val query = response?.let { r ->
            OnDemandQuery.entries.firstOrNull { it.response == r.response_type && it in pendingSince }
        }
        return query?.let { it to checkNotNull(response) }
    }

    // A request to our own node is answered over the phone link, where the reply may carry from = 0.
    private fun isFromTarget(packet: MeshPacket): Boolean {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
        return packet.from == destNum || (packet.from == 0 && destNum == myNum)
    }

    private companion object {
        val RESPONSE_TIMEOUT = 45.seconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
