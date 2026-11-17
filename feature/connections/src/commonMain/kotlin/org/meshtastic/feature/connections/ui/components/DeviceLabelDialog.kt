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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.fwplus_device_label_hint
import org.meshtastic.core.resources.fwplus_device_label_title
import org.meshtastic.core.resources.save
import org.meshtastic.feature.connections.model.DeviceListEntry

private const val MAX_LABEL_LENGTH = 40

/**
 * Edits the name the user gives [device] on the Connections lists. Saving a blank name, or "Clear", brings back the
 * advertised name. The address is shown for reference, since it stays visible under the name.
 */
@Composable
internal fun DeviceLabelDialog(
    device: DeviceListEntry,
    current: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(device.fullAddress) { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fwplus_device_label_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(device.address, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_LABEL_LENGTH) },
                    placeholder = { Text(device.name) },
                    singleLine = true,
                    keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.fwplus_device_label_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim().ifEmpty { null }) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) { Text(stringResource(Res.string.clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

/** Edits the user-given name of [target]; [onDone] closes the editor. */
@Composable
internal fun LabelEditor(target: DeviceListEntry, deviceLabels: DeviceLabels, onDone: () -> Unit) {
    DeviceLabelDialog(
        device = target,
        current = deviceLabels.labels[target.fullAddress],
        onSave = { label ->
            deviceLabels.onSetLabel(target.fullAddress, label)
            onDone()
        },
        onDismiss = onDone,
    )
}
