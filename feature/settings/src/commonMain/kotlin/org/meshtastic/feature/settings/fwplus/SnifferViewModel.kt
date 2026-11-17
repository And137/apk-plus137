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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.fwplus.FwPlusPortNum
import org.meshtastic.core.fwplus.portNumValue
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.PortNum

/** One received mesh packet as the sniffer lists it. */
data class SnifferEntry(
    val uuid: String,
    val receivedMillis: Long,
    val from: Int,
    val to: Int,
    val port: Int,
    /** Hops travelled, or null when the packet does not say. */
    val hops: Int?,
    val snr: Float,
    val rssi: Int,
    val channel: Int,
    val relayNode: Int,
    /** A FW+ sniffer copy: neither to us, from us, nor a broadcast. */
    val transit: Boolean,
    val fromUs: Boolean,
)

data class SnifferFilters(val text: String = "", val transitOnly: Boolean = false, val includeOurTx: Boolean = true)

data class SnifferUiState(
    val entries: List<SnifferEntry> = emptyList(),
    val filters: SnifferFilters = SnifferFilters(),
    /** Packet counts per port name, busiest first, over the filtered entries. */
    val byPort: List<Pair<String, Int>> = emptyList(),
    /** Packet counts per sender name, busiest first, over the filtered entries. */
    val bySender: List<Pair<String, Int>> = emptyList(),
    val nodeNames: Map<Int, String> = emptyMap(),
)

/**
 * The FW+ sniffer: every decoded mesh packet the radio handed us, newest first, from the packet log. With "Sniffer
 * enabled" in FW+ Configuration the radio also forwards transit packets addressed to other nodes; those are flagged.
 */
@KoinViewModel
class SnifferViewModel(meshLogRepository: MeshLogRepository, nodeRepository: NodeRepository) : ViewModel() {

    private val filters = MutableStateFlow(SnifferFilters())

    val state: StateFlow<SnifferUiState> =
        combine(
            meshLogRepository.getAllLogs(MAX_PACKETS),
            nodeRepository.nodeDBbyNum,
            nodeRepository.myNodeInfo,
            filters,
        ) { logs, nodes, myInfo, f ->
            val myNum = myInfo?.myNodeNum ?: 0
            val names = nodes.mapValues { (num, node) -> displayName(num, node) }
            val all = logs.mapNotNull { it.toEntry(myNum) }
            val filtered = all.filter { it.matches(f, names) }
            SnifferUiState(
                entries = filtered,
                filters = f,
                byPort =
                filtered
                    .groupingBy { portLabel(it.port) }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second },
                bySender =
                filtered
                    .groupingBy { names[it.from] ?: NodeAddress.numToDefaultId(it.from) }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second },
                nodeNames = names,
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SnifferUiState())

    fun setText(text: String) = filters.value.let { filters.value = it.copy(text = text) }

    fun setTransitOnly(on: Boolean) = filters.value.let { filters.value = it.copy(transitOnly = on) }

    fun setIncludeOurTx(on: Boolean) = filters.value.let { filters.value = it.copy(includeOurTx = on) }

    private fun SnifferEntry.matches(f: SnifferFilters, names: Map<Int, String>): Boolean {
        val text = f.text.trim()
        val textMatches =
            text.isEmpty() ||
                listOf(
                    names[from].orEmpty(),
                    names[to].orEmpty(),
                    NodeAddress.numToDefaultId(from),
                    NodeAddress.numToDefaultId(to),
                    portLabel(port),
                )
                    .any { it.contains(text, ignoreCase = true) }
        return (!f.transitOnly || transit) && (f.includeOurTx || !fromUs) && textMatches
    }

    private companion object {
        const val MAX_PACKETS = 1000
        const val STOP_TIMEOUT_MS = 5_000L

        fun displayName(num: Int, node: Node): String =
            node.effectiveLongName.takeIf { it.isNotBlank() } ?: NodeAddress.numToDefaultId(num)

        fun MeshLog.toEntry(myNum: Int): SnifferEntry? {
            val packet = meshPacket?.takeIf { message_type == "Packet" && it.decoded != null } ?: return null
            val decoded = checkNotNull(packet.decoded)
            // The log keys our own packets under NODE_NUM_LOCAL; show them under our real number.
            val from = if (fromNum == MeshLog.NODE_NUM_LOCAL) myNum else packet.from
            val fromUs = from == myNum
            val broadcast = packet.to == NodeAddress.NODENUM_BROADCAST || packet.to == 0
            return SnifferEntry(
                uuid = uuid,
                receivedMillis = received_date,
                from = from,
                to = packet.to,
                port = decoded.portNumValue,
                hops = (packet.hop_start - packet.hop_limit).takeIf { packet.hop_start > 0 && it >= 0 },
                snr = packet.rx_snr,
                rssi = packet.rx_rssi ?: 0,
                channel = packet.channel,
                relayNode = packet.relay_node,
                transit = !fromUs && packet.to != myNum && !broadcast,
                fromUs = fromUs,
            )
        }
    }
}

/** A readable name for any port number, including FW+ ones; never fails for an unknown value. */
fun portLabel(port: Int): String = when (port) {
    FwPlusPortNum.NODE_MOD_APP -> "NODE_MOD_APP"
    FwPlusPortNum.ON_DEMAND_APP -> "ON_DEMAND_APP"
    else -> PortNum.fromValue(port)?.name ?: "PORT_$port"
}
