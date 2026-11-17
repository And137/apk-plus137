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
import org.meshtastic.core.model.fwPlusVersionOf
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.fwplus.proto.OnDemand
import org.meshtastic.fwplus.proto.OnDemandResponse
import org.meshtastic.fwplus.proto.OnDemandType
import org.meshtastic.proto.MeshPacket
import kotlin.time.Duration.Companion.seconds

/** What the screen shows under one query button. */
sealed interface OnDemandResult {
    data object Waiting : OnDemandResult

    data object NoResponse : OnDemandResult

    /** The node's firmware does not answer this query (too old a FW+ build, or it answered UNKNOWN_TYPE). */
    data object NotSupported : OnDemandResult

    data class Received(
        val response: OnDemandResponse,
        val ping: PingMeasurement? = null,
        /** When the response arrived (epoch millis); ages in the response count back from here. */
        val receivedAtMillis: Long = 0L,
    ) : OnDemandResult
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
    /** When each query may be sent again (epoch millis); before that a tap only shows or hides its last result. */
    val cooldownUntil: Map<OnDemandQuery, Long> = emptyMap(),
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

    /** The FW+ version the node reported in this session (OnDemand REQUEST_FW_PLUS_VERSION). */
    private var reportedFwPlusVersion: Int? = null

    /** Queries waiting for the node's FW+ version before they may be sent. */
    private val awaitingVersion = mutableSetOf<OnDemandQuery>()

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
        val current = _state.value
        val coolingDown = (current.cooldownUntil[query] ?: 0L) > nowMillis
        when {
            current.expanded == query && current.results[query] !is OnDemandResult.Waiting ->
                _state.update { it.copy(expanded = null) }

            // Already on its way (or waiting for the version answer): never a second request.
            current.results[query] is OnDemandResult.Waiting -> _state.update { it.copy(expanded = query) }

            // Still cooling down: show the last result again instead of loading the mesh with a repeat request.
            coolingDown && current.results[query] != null -> _state.update { it.copy(expanded = query) }

            else -> send(query)
        }
    }

    private fun send(query: OnDemandQuery) {
        val minVersion = query.minFwPlusVersion
        val node = nodeRepository.nodeDBbyNum.value[destNum]
        // This session's answer, else the version stored from an earlier answer, else the firmware string.
        val version = reportedFwPlusVersion ?: node?.fwPlusVersion ?: fwPlusVersionOf(node?.metadata?.firmware_version)
        when {
            minVersion == null || (version != null && version >= minVersion) -> sendRequest(query)

            // A version from this session's FW_VERSION answer is authoritative; a stored one may predate an update, so
            // a too-old stored version is asked again below.
            reportedFwPlusVersion != null -> setResult(query, OnDemandResult.NotSupported, expand = true)

            else -> {
                // Ask for the version first; the query goes out once the answer allows it.
                awaitingVersion += query
                _state.update { it.copy(expanded = query, results = it.results + (query to OnDemandResult.Waiting)) }
                if (OnDemandQuery.FW_VERSION !in pendingSince) sendRequest(OnDemandQuery.FW_VERSION, expand = false)
            }
        }
    }

    private fun setResult(query: OnDemandQuery, result: OnDemandResult, expand: Boolean = false) {
        _state.update {
            it.copy(expanded = if (expand) query else it.expanded, results = it.results + (query to result))
        }
    }

    /** The FW_VERSION answer (or its absence) decides the queries that waited for it. */
    private fun onVersionKnown(version: Int?) {
        val waiting = awaitingVersion.toList()
        awaitingVersion.clear()
        waiting.forEach { query ->
            val minVersion = query.minFwPlusVersion ?: 0
            when {
                version == null -> setResult(query, OnDemandResult.NoResponse)

                // A short gap after the version answer keeps the request from meeting the tail of that exchange.
                version >= minVersion ->
                    viewModelScope.launch {
                        delay(FOLLOW_UP_DELAY)
                        sendRequest(query, expand = _state.value.expanded == query)
                    }

                else -> setResult(query, OnDemandResult.NotSupported)
            }
        }
    }

    private fun sendRequest(query: OnDemandQuery, expand: Boolean = true) {
        // NODES and NODES_DIRECT share a response type: only one of them may wait at a time.
        OnDemandQuery.entries.filter { it != query && it.response == query.response }.forEach { cancel(it) }
        assembler.reset(query.response)
        val sentAt = nowMillis
        pendingSince[query] = sentAt
        val toOwnNode = destNum == nodeRepository.myNodeInfo.value?.myNodeNum
        _state.update {
            it.copy(
                expanded = if (expand) query else it.expanded,
                results = it.results + (query to OnDemandResult.Waiting),
                cooldownUntil = it.cooldownUntil + (query to sentAt + query.cooldownMillis(toOwnNode)),
            )
        }

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
                if (query == OnDemandQuery.FW_VERSION) onVersionKnown(null)
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
        if (response.response_type == OnDemandType.UNKNOWN_TYPE) {
            setResult(query, OnDemandResult.NotSupported)
        } else {
            onAnswered(packet, query, response, sentAt)
        }
    }

    private fun onAnswered(packet: MeshPacket, query: OnDemandQuery, response: OnDemandResponse, sentAt: Long) {
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
        _state.update {
            it.copy(results = it.results + (query to OnDemandResult.Received(response, ping, nowMillis)))
        }
        if (query == OnDemandQuery.FW_VERSION) {
            reportedFwPlusVersion = response.fw_plus_version?.version_number
            onVersionKnown(reportedFwPlusVersion ?: 0)
        }
    }

    /** The pending query [packet] completes, with its (reassembled) response; null for anything else. */
    private fun completedResponse(packet: MeshPacket): Pair<OnDemandQuery, OnDemandResponse>? {
        val payload =
            packet.decoded?.takeIf { it.portNumValue == FwPlusPortNum.ON_DEMAND_APP && isFromTarget(packet) }?.payload
        val response = payload?.let { OnDemand.ADAPTER.decodeOrNull(it, Logger) }?.let(assembler::accept)
        val query = response?.let { r ->
            OnDemandQuery.entries.firstOrNull { it.response == r.response_type && it in pendingSince }
                ?: unknownTypeAnswer(r)
        }
        return query?.let { it to checkNotNull(response) }
    }

    /**
     * A firmware that does not know a request answers it with UNKNOWN_TYPE, which names no query. Only version-gated
     * queries can meet that from a FW+ node, so it is matched to the one waiting.
     */
    private fun unknownTypeAnswer(response: OnDemandResponse): OnDemandQuery? =
        if (response.response_type == OnDemandType.UNKNOWN_TYPE) {
            OnDemandQuery.entries.firstOrNull { it.minFwPlusVersion != null && it in pendingSince }
        } else {
            null
        }

    // A request to our own node is answered over the phone link, where the reply may carry from = 0.
    private fun isFromTarget(packet: MeshPacket): Boolean {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
        return packet.from == destNum || (packet.from == 0 && destNum == myNum)
    }

    private companion object {
        val RESPONSE_TIMEOUT = 45.seconds
        val FOLLOW_UP_DELAY = 2.seconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
