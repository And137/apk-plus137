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
package org.meshtastic.feature.node.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.detail.NodeRequestActions

/**
 * apk-plus137: the node actions of the Nodes tab's long-press menu for screens outside the node list (a sender's chip
 * in a conversation). Confirmations go through the app-wide alert, as on the Nodes tab.
 */
@KoinViewModel
class NodeQuickActionsViewModel(
    private val nodeRepository: NodeRepository,
    private val nodeManagementActions: NodeManagementActions,
    private val nodeRequestActions: NodeRequestActions,
) : ViewModel() {
    val ourNode: StateFlow<Node?> = nodeRepository.ourNodeInfo
    val nodesByNum: StateFlow<Map<Int, Node>> = nodeRepository.nodeDBbyNum
    val lastTracerouteTime: StateFlow<Long?> = nodeRequestActions.lastTracerouteTime

    fun favorite(node: Node) = nodeManagementActions.requestFavoriteNode(viewModelScope, node)

    fun mute(node: Node) = nodeManagementActions.requestMuteNode(viewModelScope, node)

    fun ignore(node: Node) = nodeManagementActions.requestIgnoreNode(viewModelScope, node)

    fun remove(node: Node) = nodeManagementActions.requestRemoveNodes(viewModelScope, listOf(node))

    fun traceRoute(node: Node) {
        safeLaunch(tag = "requestTraceroute") { nodeRequestActions.requestTraceroute(node.num, node.effectiveLongName) }
    }

    fun setDisplayName(nodeNum: Int, displayName: String) {
        safeLaunch(tag = "setNodeDisplayName") { nodeManagementActions.setNodeDisplayName(nodeNum, displayName) }
    }

    /** The contact key of a direct conversation with [node], as the Nodes tab opens it. */
    fun directMessageKey(node: Node): String {
        val hasPkc = ourNode.value?.hasPKC == true && node.hasPKC
        val channel = if (hasPkc) NodeAddress.PKC_CHANNEL_INDEX else node.channel
        return "${channel}${node.user.id}"
    }
}

/**
 * The Nodes tab's context menu for [node], ending with "More…" ([onMore], the node's details). Our own node has nothing
 * to offer here, so it goes straight to its details instead.
 */
@Composable
fun NodeQuickMenu(
    node: Node,
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: NodeQuickActionsViewModel,
    onMessage: (contactKey: String) -> Unit,
    onMore: () -> Unit,
    onOpenCompass: () -> Unit = onMore,
    onOpenMeshView: () -> Unit = onMore,
) {
    val ourNode by viewModel.ourNode.collectAsStateWithLifecycle()
    val nodes by viewModel.nodesByNum.collectAsStateWithLifecycle()
    val lastTracerouteTime by viewModel.lastTracerouteTime.collectAsStateWithLifecycle()
    // The chip's node is a snapshot from the message; favorite, mute and ignore follow the live record.
    val current = nodes[node.num] ?: node
    var showDisplayNameDialog by remember { mutableStateOf(false) }

    if (showDisplayNameDialog) {
        DisplayNameDialog(
            node = current,
            onSave = viewModel::setDisplayName,
            onDismiss = { showDisplayNameDialog = false },
        )
    }

    NodeContextMenu(
        expanded = expanded,
        node = current,
        ourNode = ourNode,
        onFavorite = { viewModel.favorite(current) },
        onMute = { viewModel.mute(current) },
        onMessage = { onMessage(viewModel.directMessageKey(current)) },
        onTraceRoute = { viewModel.traceRoute(current) },
        lastTracerouteTime = lastTracerouteTime,
        onIgnore = { viewModel.ignore(current) },
        onRemove = { viewModel.remove(current) },
        onDismiss = onDismiss,
        onOpenMeshView = onOpenMeshView,
        onOpenCompass = onOpenCompass,
        onSetDisplayName = { showDisplayNameDialog = true },
        onMore = onMore,
    )
}
