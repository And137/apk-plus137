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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.model.NetworkPlusAppRelease
import org.meshtastic.core.model.PlusAppVersion
import org.meshtastic.core.network.PlusAppUpdateRemoteDataSource

/** The About screen's "Check for updates" row. */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class UpToDate(val version: String) : AppUpdateState

    data class Available(val version: String, val releaseUrl: String, val release: NetworkPlusAppRelease) :
        AppUpdateState

    data class Downloading(val version: String, val progress: Float) : AppUpdateState

    /** The system installer is open with the downloaded update. */
    data class Handed(val version: String) : AppUpdateState

    data class Failed(val reason: Reason) : AppUpdateState

    enum class Reason {
        OFFLINE,
        DOWNLOAD,
        CHECKSUM,
    }
}

/** apk-plus137: checks the apk-plus137 GitHub releases for a newer Mesh+ 137 build and installs it on Android. */
@KoinViewModel
class AppUpdateViewModel(
    private val remote: PlusAppUpdateRemoteDataSource,
    private val buildConfig: BuildConfigProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** This build in release-tag form ("2.8.5-137.5"). */
    val currentVersion: String = PlusAppVersion.current(buildConfig.versionName, buildConfig.plusBuild)

    fun check() {
        if (_state.value is AppUpdateState.Checking || _state.value is AppUpdateState.Downloading) return
        _state.value = AppUpdateState.Checking
        viewModelScope.launch {
            _state.value =
                try {
                    val release = remote.latestRelease()
                    val latest = release?.tagName?.removePrefix("v")
                    if (release != null && latest != null && PlusAppVersion.isNewer(latest, currentVersion)) {
                        AppUpdateState.Available(latest, release.htmlUrl, release)
                    } else {
                        AppUpdateState.UpToDate(currentVersion)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Mesh+ 137 update check failed" }
                    AppUpdateState.Failed(AppUpdateState.Reason.OFFLINE)
                }
        }
    }

    /**
     * Downloads the release file [installer] picks and opens the installer. Returns false when the platform has no file
     * to install, so the caller opens the release page instead.
     */
    fun install(installer: AppUpdateInstaller?): Boolean {
        val available = _state.value as? AppUpdateState.Available
        val assets = available?.release?.assets.orEmpty()
        val name = installer?.pickAsset(assets.map { it.name })
        val asset = assets.firstOrNull { it.name == name }
        if (available == null || installer == null || name == null || asset == null) return false
        _state.value = AppUpdateState.Downloading(available.version, 0f)
        viewModelScope.launch {
            _state.value =
                try {
                    val sha256 =
                        assets
                            .firstOrNull { it.name == SUMS_FILE }
                            ?.let { remote.text(it.downloadUrl) }
                            ?.let { PlusAppVersion.sha256For(it, name) }
                    val outcome =
                        installer.downloadAndInstall(asset.downloadUrl, name, sha256) { progress ->
                            _state.value = AppUpdateState.Downloading(available.version, progress)
                        }
                    when (outcome) {
                        InstallOutcome.STARTED -> AppUpdateState.Handed(available.version)

                        // Allowing installs takes the user to settings; the row offers the update again on return.
                        InstallOutcome.NEEDS_PERMISSION -> available

                        InstallOutcome.CHECKSUM_MISMATCH -> AppUpdateState.Failed(AppUpdateState.Reason.CHECKSUM)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Mesh+ 137 update download failed" }
                    AppUpdateState.Failed(AppUpdateState.Reason.DOWNLOAD)
                }
        }
        return true
    }

    private companion object {
        const val SUMS_FILE = "SHA256SUMS.txt"
    }
}
