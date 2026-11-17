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
package org.meshtastic.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_update_available
import org.meshtastic.core.resources.fwplus_update_available_page
import org.meshtastic.core.resources.fwplus_update_check
import org.meshtastic.core.resources.fwplus_update_checking
import org.meshtastic.core.resources.fwplus_update_downloading
import org.meshtastic.core.resources.fwplus_update_failed_checksum
import org.meshtastic.core.resources.fwplus_update_failed_download
import org.meshtastic.core.resources.fwplus_update_failed_offline
import org.meshtastic.core.resources.fwplus_update_handed
import org.meshtastic.core.resources.fwplus_update_idle
import org.meshtastic.core.resources.fwplus_update_up_to_date
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SystemUpdate

private const val PERCENT = 100

/**
 * apk-plus137: "Check for updates" on the About screen. A newer release is downloaded and installed in place on
 * Android; elsewhere the row opens the release page.
 */
@Composable
fun AppUpdateRow(viewModel: AppUpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installer = rememberAppUpdateInstaller()
    val uriHandler = LocalUriHandler.current
    val current = state

    val supportingText =
        when (current) {
            AppUpdateState.Idle -> stringResource(Res.string.fwplus_update_idle, viewModel.currentVersion)

            AppUpdateState.Checking -> stringResource(Res.string.fwplus_update_checking)

            is AppUpdateState.UpToDate -> stringResource(Res.string.fwplus_update_up_to_date, current.version)

            is AppUpdateState.Available ->
                if (installer != null) {
                    stringResource(Res.string.fwplus_update_available, current.version)
                } else {
                    stringResource(Res.string.fwplus_update_available_page, current.version)
                }

            is AppUpdateState.Downloading ->
                stringResource(
                    Res.string.fwplus_update_downloading,
                    current.version,
                    "${(current.progress * PERCENT).toInt()} %",
                )

            is AppUpdateState.Handed -> stringResource(Res.string.fwplus_update_handed, current.version)

            is AppUpdateState.Failed ->
                stringResource(
                    when (current.reason) {
                        AppUpdateState.Reason.OFFLINE -> Res.string.fwplus_update_failed_offline
                        AppUpdateState.Reason.DOWNLOAD -> Res.string.fwplus_update_failed_download
                        AppUpdateState.Reason.CHECKSUM -> Res.string.fwplus_update_failed_checksum
                    },
                )
        }
    val busy = current is AppUpdateState.Checking || current is AppUpdateState.Downloading

    Column(modifier = modifier) {
        ListItem(
            text = stringResource(Res.string.fwplus_update_check),
            leadingIcon = MeshtasticIcons.SystemUpdate,
            supportingText = supportingText,
            enabled = !busy,
            trailingIcon = null,
            onClick = {
                when (current) {
                    is AppUpdateState.Available ->
                        if (!viewModel.install(installer)) uriHandler.openUri(current.releaseUrl)

                    else -> viewModel.check()
                }
            },
        )
        if (current is AppUpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { current.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
