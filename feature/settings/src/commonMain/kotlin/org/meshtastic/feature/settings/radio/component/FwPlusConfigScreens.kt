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
@file:Suppress("MagicNumber", "TooManyFunctions")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.fwplus.broadcastAssist
import org.meshtastic.core.fwplus.fwPlusBtPowerDbm
import org.meshtastic.core.fwplus.fwPlusModuleConfig
import org.meshtastic.core.fwplus.fwPlusWifiPowerDbm
import org.meshtastic.core.fwplus.nodeMod
import org.meshtastic.core.fwplus.nodeModAdmin
import org.meshtastic.core.fwplus.withFwPlusRadioPower
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fwplus_airtime_limits
import org.meshtastic.core.resources.fwplus_auto_chanutil_limiter
import org.meshtastic.core.resources.fwplus_auto_chanutil_limiter_summary
import org.meshtastic.core.resources.fwplus_auto_chanutil_threshold
import org.meshtastic.core.resources.fwplus_auto_redirect
import org.meshtastic.core.resources.fwplus_auto_redirect_summary
import org.meshtastic.core.resources.fwplus_auto_redirect_target
import org.meshtastic.core.resources.fwplus_auto_responder_enabled
import org.meshtastic.core.resources.fwplus_auto_responder_enabled_summary
import org.meshtastic.core.resources.fwplus_auto_responder_text
import org.meshtastic.core.resources.fwplus_ba_airtime_guard
import org.meshtastic.core.resources.fwplus_ba_airtime_guard_summary
import org.meshtastic.core.resources.fwplus_ba_config
import org.meshtastic.core.resources.fwplus_ba_degree_threshold
import org.meshtastic.core.resources.fwplus_ba_degree_threshold_summary
import org.meshtastic.core.resources.fwplus_ba_dup_threshold
import org.meshtastic.core.resources.fwplus_ba_dup_threshold_summary
import org.meshtastic.core.resources.fwplus_ba_enabled
import org.meshtastic.core.resources.fwplus_ba_jitter_ms
import org.meshtastic.core.resources.fwplus_ba_max_extra_hops
import org.meshtastic.core.resources.fwplus_ba_window_ms
import org.meshtastic.core.resources.fwplus_broadcast_assist
import org.meshtastic.core.resources.fwplus_bt_power
import org.meshtastic.core.resources.fwplus_bt_power_summary
import org.meshtastic.core.resources.fwplus_chutil_limit
import org.meshtastic.core.resources.fwplus_configuration
import org.meshtastic.core.resources.fwplus_current_limits
import org.meshtastic.core.resources.fwplus_default
import org.meshtastic.core.resources.fwplus_general
import org.meshtastic.core.resources.fwplus_local_stats
import org.meshtastic.core.resources.fwplus_local_stats_extended
import org.meshtastic.core.resources.fwplus_local_stats_extended_summary
import org.meshtastic.core.resources.fwplus_local_stats_summary
import org.meshtastic.core.resources.fwplus_node_status
import org.meshtastic.core.resources.fwplus_opportunistic_auto
import org.meshtastic.core.resources.fwplus_opportunistic_auto_summary
import org.meshtastic.core.resources.fwplus_opportunistic_base_delay
import org.meshtastic.core.resources.fwplus_opportunistic_cancel_on_first_hear
import org.meshtastic.core.resources.fwplus_opportunistic_cancel_on_first_hear_summary
import org.meshtastic.core.resources.fwplus_opportunistic_enabled
import org.meshtastic.core.resources.fwplus_opportunistic_enabled_summary
import org.meshtastic.core.resources.fwplus_opportunistic_flooding
import org.meshtastic.core.resources.fwplus_opportunistic_flooding_summary
import org.meshtastic.core.resources.fwplus_opportunistic_hop_delay
import org.meshtastic.core.resources.fwplus_opportunistic_jitter
import org.meshtastic.core.resources.fwplus_opportunistic_snr_gain
import org.meshtastic.core.resources.fwplus_polite_duty_cycle_limit
import org.meshtastic.core.resources.fwplus_position_duplicate_window
import org.meshtastic.core.resources.fwplus_position_limiter
import org.meshtastic.core.resources.fwplus_position_limiter_enabled
import org.meshtastic.core.resources.fwplus_position_limiter_enabled_summary
import org.meshtastic.core.resources.fwplus_position_limiter_summary
import org.meshtastic.core.resources.fwplus_private_mqtt
import org.meshtastic.core.resources.fwplus_private_mqtt_summary
import org.meshtastic.core.resources.fwplus_radio_power
import org.meshtastic.core.resources.fwplus_sniffer_enabled
import org.meshtastic.core.resources.fwplus_sniffer_enabled_summary
import org.meshtastic.core.resources.fwplus_status_emoji
import org.meshtastic.core.resources.fwplus_status_text
import org.meshtastic.core.resources.fwplus_status_text_summary
import org.meshtastic.core.resources.fwplus_telemetry_limiter
import org.meshtastic.core.resources.fwplus_telemetry_limiter_enabled
import org.meshtastic.core.resources.fwplus_telemetry_limiter_enabled_summary
import org.meshtastic.core.resources.fwplus_telemetry_limiter_summary
import org.meshtastic.core.resources.fwplus_telemetry_packets_per_minute
import org.meshtastic.core.resources.fwplus_tx_util_limit
import org.meshtastic.core.resources.fwplus_value_dbm
import org.meshtastic.core.resources.fwplus_value_minutes
import org.meshtastic.core.resources.fwplus_value_ms
import org.meshtastic.core.resources.fwplus_value_ms_per_db
import org.meshtastic.core.resources.fwplus_value_percent
import org.meshtastic.core.resources.fwplus_wifi_power
import org.meshtastic.core.resources.fwplus_wifi_power_summary
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SliderPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.fwplus.proto.BroadcastAssistConfig
import org.meshtastic.fwplus.proto.NodeModAdminConfig
import org.meshtastic.fwplus.proto.NodeModConfig
import org.meshtastic.proto.Config

// FW+ fields have no schema labels, so these screens carry no search field anchors; search finds the screens by title.
private val noSearchFields = emptyList<String>()

// Byte limits from the firmware's nanopb options (max_size includes the terminator).
private const val STATUS_TEXT_MAX_BYTES = 199
private const val STATUS_EMOJI_MAX_BYTES = 3
private const val AUTO_RESPONDER_MAX_BYTES = 59

// --- FW+ Node text status (ModuleConfig.node_mod) ----------------------------------------------------------

@Composable
fun FwPlusNodeStatusScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val formState = rememberConfigState(initialValue = state.moduleConfig.nodeMod ?: NodeModConfig())
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        modifier = modifier,
        rebootBehavior = RebootBehavior.NEVER,
        title = stringResource(Res.string.fwplus_node_status),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        showConnectPrompt = state.connectPromptVisible,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = { viewModel.setModuleConfig(fwPlusModuleConfig(it)) },
    ) {
        fieldItem(noSearchFields) {
            TitledCard(title = stringResource(Res.string.fwplus_node_status)) {
                EditTextPreference(
                    title = stringResource(Res.string.fwplus_status_text),
                    summary = stringResource(Res.string.fwplus_status_text_summary),
                    value = formState.value.text_status,
                    maxSize = STATUS_TEXT_MAX_BYTES,
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(text_status = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.fwplus_status_emoji),
                    value = formState.value.emoji,
                    maxSize = STATUS_EMOJI_MAX_BYTES,
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(emoji = it) },
                )
            }
        }
    }
}

// --- FW+ Broadcast Assist (ModuleConfig.broadcast_assist) --------------------------------------------------

@Composable
fun FwPlusBroadcastAssistScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val formState = rememberConfigState(initialValue = state.moduleConfig.broadcastAssist ?: BroadcastAssistConfig())
    val enabled = state.connected

    RadioConfigScreenList(
        modifier = modifier,
        rebootBehavior = RebootBehavior.MAY_RESTART,
        title = stringResource(Res.string.fwplus_broadcast_assist),
        onBack = onBack,
        configState = formState,
        enabled = enabled,
        responseState = state.responseState,
        showConnectPrompt = state.connectPromptVisible,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = { viewModel.setModuleConfig(fwPlusModuleConfig(it)) },
    ) {
        fieldItem(noSearchFields) {
            val value = formState.value
            TitledCard(title = stringResource(Res.string.fwplus_ba_config)) {
                FwPlusSwitch(Res.string.fwplus_ba_enabled, null, value.enabled, enabled) {
                    formState.value = value.copy(enabled = it)
                }
                HorizontalDivider()
                FwPlusNumber(
                    Res.string.fwplus_ba_degree_threshold,
                    Res.string.fwplus_ba_degree_threshold_summary,
                    value.degree_threshold,
                    enabled,
                ) {
                    formState.value = value.copy(degree_threshold = it)
                }
                HorizontalDivider()
                FwPlusNumber(
                    Res.string.fwplus_ba_dup_threshold,
                    Res.string.fwplus_ba_dup_threshold_summary,
                    value.dup_threshold,
                    enabled,
                ) {
                    formState.value = value.copy(dup_threshold = it)
                }
                HorizontalDivider()
                FwPlusNumber(Res.string.fwplus_ba_window_ms, null, value.window_ms, enabled) {
                    formState.value = value.copy(window_ms = it)
                }
                HorizontalDivider()
                FwPlusNumber(Res.string.fwplus_ba_max_extra_hops, null, value.max_extra_hops, enabled) {
                    formState.value = value.copy(max_extra_hops = it)
                }
                HorizontalDivider()
                FwPlusNumber(Res.string.fwplus_ba_jitter_ms, null, value.jitter_ms, enabled) {
                    formState.value = value.copy(jitter_ms = it)
                }
                HorizontalDivider()
                FwPlusSwitch(
                    Res.string.fwplus_ba_airtime_guard,
                    Res.string.fwplus_ba_airtime_guard_summary,
                    value.airtime_guard,
                    enabled,
                ) {
                    formState.value = value.copy(airtime_guard = it)
                }
            }
        }
    }
}

// --- FW+ Configuration (ModuleConfig.node_mod_admin) -------------------------------------------------------

@Composable
fun FwPlusConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val formState = rememberConfigState(initialValue = state.moduleConfig.nodeModAdmin ?: NodeModAdminConfig())
    val enabled = state.connected

    RadioConfigScreenList(
        modifier = modifier,
        rebootBehavior = RebootBehavior.MAY_RESTART,
        title = stringResource(Res.string.fwplus_configuration),
        onBack = onBack,
        configState = formState,
        enabled = enabled,
        responseState = state.responseState,
        showConnectPrompt = state.connectPromptVisible,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = { viewModel.setModuleConfig(fwPlusModuleConfig(it)) },
    ) {
        fieldItem(noSearchFields) { FwPlusGeneralCard(formState, enabled) }
        fieldItem(noSearchFields) { FwPlusAirtimeCard(formState, enabled) }
        fieldItem(noSearchFields) { FwPlusTelemetryLimiterCard(formState, enabled) }
        fieldItem(noSearchFields) { FwPlusPositionLimiterCard(formState, enabled) }
        fieldItem(noSearchFields) { FwPlusOpportunisticCard(formState, enabled) }
    }
}

@Composable
private fun FwPlusGeneralCard(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    TitledCard(title = stringResource(Res.string.fwplus_general)) {
        FwPlusSwitch(
            Res.string.fwplus_sniffer_enabled,
            Res.string.fwplus_sniffer_enabled_summary,
            value.sniffer_enabled,
            enabled,
        ) {
            formState.value = value.copy(sniffer_enabled = it)
        }
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_private_mqtt,
            Res.string.fwplus_private_mqtt_summary,
            value.do_not_send_prv_over_mqtt,
            enabled,
        ) {
            formState.value = value.copy(do_not_send_prv_over_mqtt = it)
        }
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_local_stats,
            Res.string.fwplus_local_stats_summary,
            value.local_stats_over_mesh_enabled,
            enabled,
        ) {
            formState.value = value.copy(local_stats_over_mesh_enabled = it)
        }
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_local_stats_extended,
            Res.string.fwplus_local_stats_extended_summary,
            value.local_stats_extended_over_mesh_enabled,
            enabled,
        ) {
            formState.value = value.copy(local_stats_extended_over_mesh_enabled = it)
        }
        HorizontalDivider()
        FwPlusMessageHandlingRows(formState, enabled)
    }
}

/** Auto responder and auto redirect: what the node does with direct messages on its own. */
@Composable
private fun FwPlusMessageHandlingRows(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val focusManager = LocalFocusManager.current
    val value = formState.value
    Column {
        FwPlusSwitch(
            Res.string.fwplus_auto_responder_enabled,
            Res.string.fwplus_auto_responder_enabled_summary,
            value.auto_responder_enabled,
            enabled,
        ) {
            formState.value = value.copy(auto_responder_enabled = it)
        }
        EditTextPreference(
            title = stringResource(Res.string.fwplus_auto_responder_text),
            value = value.auto_responder_text,
            maxSize = AUTO_RESPONDER_MAX_BYTES,
            enabled = enabled && value.auto_responder_enabled,
            isError = false,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = { formState.value = formState.value.copy(auto_responder_text = it) },
        )
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_auto_redirect,
            Res.string.fwplus_auto_redirect_summary,
            value.auto_redirect_messages,
            enabled,
        ) {
            formState.value = value.copy(auto_redirect_messages = it)
        }
        RedirectTargetField(
            nodeNum = value.auto_redirect_target_node_id,
            enabled = enabled && value.auto_redirect_messages,
            onNodeNum = { formState.value = formState.value.copy(auto_redirect_target_node_id = it) },
        )
    }
}

/** Edits a node number as `!abcdef12`, the way node IDs are shown everywhere else. */
@Composable
private fun RedirectTargetField(nodeNum: Int, enabled: Boolean, onNodeNum: (Int) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(nodeNum) { mutableStateOf(formatNodeId(nodeNum)) }
    EditTextPreference(
        title = stringResource(Res.string.fwplus_auto_redirect_target),
        value = text,
        enabled = enabled,
        isError = parseNodeId(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onValueChanged = { input ->
            text = input
            parseNodeId(input)?.let(onNodeNum)
        },
    )
}

internal fun formatNodeId(nodeNum: Int): String = "!" + nodeNum.toUInt().toString(16).padStart(8, '0')

/** Parses `!abcdef12` or `abcdef12` into a node number; null when it is not 1-8 hex digits. */
internal fun parseNodeId(text: String): Int? {
    val hex = text.trim().removePrefix("!")
    if (hex.isEmpty() || hex.length > 8) return null
    return hex.toUIntOrNull(16)?.toInt()
}

@Composable
private fun FwPlusAirtimeCard(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    TitledCard(title = stringResource(Res.string.fwplus_airtime_limits)) {
        if (value.current_tx_util_limit > 0f) {
            val limit = NumberFormatter.format(value.current_tx_util_limit, 1)
            FwPlusDescription(stringResource(Res.string.fwplus_current_limits, limit))
        }
        FwPlusRangeSlider(
            Res.string.fwplus_tx_util_limit,
            value.additional_txutil.toInt(),
            0..100,
            1,
            Res.string.fwplus_value_percent,
            enabled,
        ) {
            formState.value = value.copy(additional_txutil = it.toFloat())
        }
        HorizontalDivider()
        FwPlusRangeSlider(
            Res.string.fwplus_chutil_limit,
            value.additional_chutil,
            0..100,
            1,
            Res.string.fwplus_value_percent,
            enabled,
        ) {
            formState.value = value.copy(additional_chutil = it)
        }
        HorizontalDivider()
        FwPlusRangeSlider(
            Res.string.fwplus_polite_duty_cycle_limit,
            value.additional_polite_duty_cycle_percent,
            0..100,
            1,
            Res.string.fwplus_value_percent,
            enabled,
        ) {
            formState.value = value.copy(additional_polite_duty_cycle_percent = it)
        }
    }
}

@Composable
private fun FwPlusTelemetryLimiterCard(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    TitledCard(title = stringResource(Res.string.fwplus_telemetry_limiter)) {
        FwPlusDescription(stringResource(Res.string.fwplus_telemetry_limiter_summary))
        FwPlusSwitch(
            Res.string.fwplus_telemetry_limiter_enabled,
            Res.string.fwplus_telemetry_limiter_enabled_summary,
            value.telemetry_limiter_enabled,
            enabled,
        ) {
            formState.value = value.copy(telemetry_limiter_enabled = it)
        }
        FwPlusRangeSlider(
            Res.string.fwplus_telemetry_packets_per_minute,
            value.telemetry_limiter_packets_per_minute,
            1..120,
            1,
            null,
            enabled && value.telemetry_limiter_enabled,
        ) {
            formState.value = value.copy(telemetry_limiter_packets_per_minute = it)
        }
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_auto_chanutil_limiter,
            Res.string.fwplus_auto_chanutil_limiter_summary,
            value.telemetry_limiter_auto_chanutil_enabled,
            enabled,
        ) {
            formState.value = value.copy(telemetry_limiter_auto_chanutil_enabled = it)
        }
        FwPlusRangeSlider(
            Res.string.fwplus_auto_chanutil_threshold,
            value.telemetry_limiter_auto_chanutil_threshold,
            1..100,
            1,
            Res.string.fwplus_value_percent,
            enabled && value.telemetry_limiter_auto_chanutil_enabled,
        ) {
            formState.value = value.copy(telemetry_limiter_auto_chanutil_threshold = it)
        }
    }
}

@Composable
private fun FwPlusPositionLimiterCard(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    TitledCard(title = stringResource(Res.string.fwplus_position_limiter)) {
        FwPlusDescription(stringResource(Res.string.fwplus_position_limiter_summary))
        FwPlusSwitch(
            Res.string.fwplus_position_limiter_enabled,
            Res.string.fwplus_position_limiter_enabled_summary,
            value.position_limiter_enabled,
            enabled,
        ) {
            formState.value = value.copy(position_limiter_enabled = it)
        }
        FwPlusRangeSlider(
            Res.string.fwplus_position_duplicate_window,
            value.position_limiter_time_minutes_threshold,
            1..120,
            1,
            Res.string.fwplus_value_minutes,
            enabled && value.position_limiter_enabled,
        ) {
            formState.value = value.copy(position_limiter_time_minutes_threshold = it)
        }
    }
}

@Composable
private fun FwPlusOpportunisticCard(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    val manual = enabled && value.opportunistic_flooding_enabled && !value.opportunistic_auto
    TitledCard(title = stringResource(Res.string.fwplus_opportunistic_flooding)) {
        FwPlusDescription(stringResource(Res.string.fwplus_opportunistic_flooding_summary))
        FwPlusSwitch(
            Res.string.fwplus_opportunistic_enabled,
            Res.string.fwplus_opportunistic_enabled_summary,
            value.opportunistic_flooding_enabled,
            enabled,
        ) {
            formState.value = value.copy(opportunistic_flooding_enabled = it)
        }
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_opportunistic_auto,
            Res.string.fwplus_opportunistic_auto_summary,
            value.opportunistic_auto,
            enabled && value.opportunistic_flooding_enabled,
        ) {
            formState.value = value.copy(opportunistic_auto = it)
        }
        HorizontalDivider()
        OpportunisticDelaySliders(formState, enabled = manual)
        HorizontalDivider()
        FwPlusSwitch(
            Res.string.fwplus_opportunistic_cancel_on_first_hear,
            Res.string.fwplus_opportunistic_cancel_on_first_hear_summary,
            value.opportunistic_cancel_on_first_hear,
            enabled && value.opportunistic_flooding_enabled,
        ) {
            formState.value = value.copy(opportunistic_cancel_on_first_hear = it)
        }
    }
}

/** The manual delay terms; the firmware ignores them while automatic tuning is on. */
@Composable
private fun OpportunisticDelaySliders(formState: ConfigState<NodeModAdminConfig>, enabled: Boolean) {
    val value = formState.value
    FwPlusRangeSlider(
        Res.string.fwplus_opportunistic_base_delay,
        value.opportunistic_base_delay_ms,
        0..500,
        10,
        Res.string.fwplus_value_ms,
        enabled,
    ) {
        formState.value = value.copy(opportunistic_base_delay_ms = it)
    }
    FwPlusRangeSlider(
        Res.string.fwplus_opportunistic_hop_delay,
        value.opportunistic_hop_delay_ms,
        0..200,
        5,
        Res.string.fwplus_value_ms,
        enabled,
    ) {
        formState.value = value.copy(opportunistic_hop_delay_ms = it)
    }
    FwPlusRangeSlider(
        Res.string.fwplus_opportunistic_snr_gain,
        value.opportunistic_snr_gain_ms,
        0..20,
        1,
        Res.string.fwplus_value_ms_per_db,
        enabled,
    ) {
        formState.value = value.copy(opportunistic_snr_gain_ms = it)
    }
    FwPlusRangeSlider(
        Res.string.fwplus_opportunistic_jitter,
        value.opportunistic_jitter_ms,
        0..200,
        5,
        Res.string.fwplus_value_ms,
        enabled,
    ) {
        formState.value = value.copy(opportunistic_jitter_ms = it)
    }
}

// --- FW+ radio power (LoRaConfig.wifi_power / bt_power), shown on the LoRa screen ---------------------------

private val WIFI_POWER_DBM = 2..21
private val BT_POWER_DBM = -12..9

/** Wi-Fi and Bluetooth TX power of FW+ nodes. 0 stands for the firmware default in both fields. */
@Composable
internal fun FwPlusRadioPowerCard(formState: ConfigState<Config.LoRaConfig>, enabled: Boolean) {
    val lora = formState.value
    val default = stringResource(Res.string.fwplus_default)
    val wifiItems = listOf(0 to default) + WIFI_POWER_DBM.map { it to stringResource(Res.string.fwplus_value_dbm, it) }
    val btItems = BT_POWER_DBM.map { dbm ->
        dbm to if (dbm == 0) default else stringResource(Res.string.fwplus_value_dbm, dbm)
    }
    TitledCard(title = stringResource(Res.string.fwplus_radio_power)) {
        SliderPreference(
            title = stringResource(Res.string.fwplus_wifi_power),
            summary = stringResource(Res.string.fwplus_wifi_power_summary),
            enabled = enabled,
            items = wifiItems,
            selectedValue = lora.fwPlusWifiPowerDbm,
            onValueChange = { formState.value = lora.withFwPlusRadioPower(it, lora.fwPlusBtPowerDbm) },
        )
        HorizontalDivider()
        SliderPreference(
            title = stringResource(Res.string.fwplus_bt_power),
            summary = stringResource(Res.string.fwplus_bt_power_summary),
            enabled = enabled,
            items = btItems,
            selectedValue = lora.fwPlusBtPowerDbm,
            onValueChange = { formState.value = lora.withFwPlusRadioPower(lora.fwPlusWifiPowerDbm, it) },
        )
    }
}

// --- Shared rows -------------------------------------------------------------------------------------------

@Composable
private fun FwPlusSwitch(
    title: StringResource,
    summary: StringResource?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = stringResource(title),
        summary = summary?.let { stringResource(it) }.orEmpty(),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        containerColor = CardDefaults.cardColors().containerColor,
    )
}

@Composable
private fun FwPlusNumber(
    title: StringResource,
    summary: StringResource?,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    EditTextPreference(
        title = stringResource(title),
        summary = summary?.let { stringResource(it) },
        value = value,
        enabled = enabled,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onValueChanged = onValueChange,
    )
}

/**
 * A slider over [range] in steps of [step]. A stored value off the step grid (set by another client) is kept as an
 * extra stop, so opening and saving the screen never rewrites it.
 */
@Composable
private fun FwPlusRangeSlider(
    title: StringResource,
    value: Int,
    range: IntRange,
    step: Int,
    unit: StringResource?,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val stops = (range step step).toList().let { grid -> if (value in grid) grid else (grid + value).sorted() }
    val items = mutableListOf<Pair<Int, String>>()
    for (stop in stops) {
        items += stop to (if (unit != null) stringResource(unit, stop) else stop.toString())
    }
    SliderPreference(
        title = stringResource(title),
        enabled = enabled,
        items = items,
        selectedValue = value,
        onValueChange = onValueChange,
    )
}

@Composable
private fun FwPlusDescription(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}
