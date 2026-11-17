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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.core.ui.util.DiscoveryNeighborType
import org.meshtastic.core.ui.util.LocalDiscoveryMapProvider
import org.meshtastic.fwplus.proto.NodeEntry
import org.meshtastic.fwplus.proto.OnDemandResponse

private const val DIRECT_MAP_HEIGHT_DP = 240
private const val DEGREES_PER_UNIT = 1e-7

/**
 * fw+: mini map for the "Nodes direct list" answer, like the old Mesh+ app: the queried node in the middle and a line
 * to every direct neighbour with a known position. Positions come from the answer (latitude_i / longitude_i), else from
 * our NodeDB. Draws nothing while the queried node has no position or no neighbour can be placed.
 */
@Composable
internal fun DirectNodesMap(response: OnDemandResponse, center: Node?, nodes: Map<Int, Node>) {
    val origin = center?.takeIf { it.validPosition != null } ?: return
    val mapNodes =
        remember(response, nodes) {
            response.node_list?.node_list.orEmpty().mapNotNull { it.toMapNode(nodes[it.node_id]) }
        }
    if (mapNodes.isEmpty()) return
    val centerLabel = origin.user.short_name.ifBlank { origin.user.long_name }.ifBlank { null }
    Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(DIRECT_MAP_HEIGHT_DP.dp)) {
        LocalDiscoveryMapProvider.current(
            origin.latitude,
            origin.longitude,
            mapNodes,
            Modifier.fillMaxSize(),
            centerLabel,
        )
    }
}

private fun NodeEntry.toMapNode(known: Node?): DiscoveryMapNode? {
    val lat = latitude_i
    val lon = longitude_i
    val (latitude, longitude) =
        when {
            lat != null && lon != null && (lat != 0 || lon != 0) -> lat * DEGREES_PER_UNIT to lon * DEGREES_PER_UNIT
            known?.validPosition != null -> known.latitude to known.longitude
            else -> return null
        }
    return DiscoveryMapNode(
        latitude = latitude,
        longitude = longitude,
        shortName = short_name.ifBlank { known?.user?.short_name },
        longName = long_name.ifBlank { known?.user?.long_name },
        neighborType = if (hops == 0) DiscoveryNeighborType.DIRECT else DiscoveryNeighborType.MESH,
        snr = snr.takeIf { rx_rssi != 0 },
        rssi = rx_rssi.takeIf { it != 0 },
    )
}
