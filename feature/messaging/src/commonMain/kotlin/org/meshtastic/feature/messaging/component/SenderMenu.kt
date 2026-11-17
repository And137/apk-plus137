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
package org.meshtastic.feature.messaging.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.meshtastic.core.model.Node

/** A node menu anchored to the sender of a message: the sender, whether it is open, and how to close it. */
typealias SenderMenu = @Composable (node: Node, expanded: Boolean, onDismiss: () -> Unit) -> Unit

/**
 * apk-plus137: when provided, tapping a sender in a conversation opens this menu (the Nodes tab's actions) instead of
 * the node's details. Absent in previews and tests, where the tap keeps calling `onClickChip`.
 */
@Suppress("CompositionLocalAllowlist") // Host-provided, like LocalDeviceModelArViewer.
val LocalSenderMenu = staticCompositionLocalOf<SenderMenu?> { null }
