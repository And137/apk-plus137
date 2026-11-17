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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_packet_counter
import org.meshtastic.core.resources.fwplus_pc_summary
import org.meshtastic.core.resources.fwplus_pc_value
import org.meshtastic.core.resources.fwplus_pc_window_1h
import org.meshtastic.core.resources.fwplus_pc_window_24h
import org.meshtastic.core.resources.fwplus_pc_window_6h
import org.meshtastic.feature.node.component.SectionCard
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The windows the packet counter can count over; picking one plays the role of the old "reset interval". */
enum class PacketCounterWindow(val duration: Duration, val label: StringResource) {
    HOUR(1.hours, Res.string.fwplus_pc_window_1h),
    SIX_HOURS(6.hours, Res.string.fwplus_pc_window_6h),
    DAY(24.hours, Res.string.fwplus_pc_window_24h),
}

/** Counts packets received from one node over a chosen window, from the packet log. */
@KoinViewModel
class PacketCounterViewModel(@InjectedParam val nodeNum: Int, meshLogRepository: MeshLogRepository) : ViewModel() {

    private val _window = MutableStateFlow(PacketCounterWindow.HOUR)
    val window: StateFlow<PacketCounterWindow> = _window.asStateFlow()

    // The window slides: its start is recomputed every minute, not only when the selection changes.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val count: StateFlow<Int> =
        combine(_window, ticker) { w, _ -> w }
            .flatMapLatest { w ->
                meshLogRepository.countPacketsFrom(nodeNum, nowMillis - w.duration.inWholeMilliseconds)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    fun select(window: PacketCounterWindow) {
        _window.value = window
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val REFRESH = 1.minutes
    }
}

/** How many packets this app has received from [nodeNum] recently. Works for any node, FW+ or not. */
@Composable
fun PacketCounterSection(nodeNum: Int, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<PacketCounterViewModel>(key = "packetCounter-$nodeNum") { parametersOf(nodeNum) }
    val window by viewModel.window.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    SectionCard(title = Res.string.fwplus_packet_counter, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.fwplus_pc_value, count), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PacketCounterWindow.entries.forEach { option ->
                    FilterChip(
                        selected = option == window,
                        onClick = { viewModel.select(option) },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }
            Text(stringResource(Res.string.fwplus_pc_summary), style = MaterialTheme.typography.bodySmall)
        }
    }
}
