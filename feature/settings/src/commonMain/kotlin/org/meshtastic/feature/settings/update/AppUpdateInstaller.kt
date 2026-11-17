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

import androidx.compose.runtime.Composable

/** What became of an attempt to download and install a Mesh+ 137 update. */
enum class InstallOutcome {
    /** The system installer was opened with the downloaded package. */
    STARTED,

    /** The user has to allow installs from this app first; the system settings page was opened. */
    NEEDS_PERMISSION,

    /** The downloaded file did not match the release's SHA256SUMS.txt and was deleted. */
    CHECKSUM_MISMATCH,
}

/** apk-plus137: downloads a release file and hands it to the platform installer (Android only for now). */
interface AppUpdateInstaller {
    /** The release file this device installs, from the release's [assetNames]; null when none fits. */
    fun pickAsset(assetNames: List<String>): String?

    /** Downloads [url] as [fileName], checks it against [sha256] when known, and opens the installer. */
    suspend fun downloadAndInstall(
        url: String,
        fileName: String,
        sha256: String?,
        onProgress: (Float) -> Unit,
    ): InstallOutcome
}

/** The platform's installer, or null where updates come from the release page (desktop, iOS). */
@Composable expect fun rememberAppUpdateInstaller(): AppUpdateInstaller?
