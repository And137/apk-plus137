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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.fwplus

import okio.Buffer
import okio.ByteString
import org.meshtastic.core.fwplus.FwPlusPortNum
import org.meshtastic.fwplus.proto.OnDemand
import org.meshtastic.fwplus.proto.OnDemandRequest
import org.meshtastic.fwplus.proto.OnDemandResponse
import org.meshtastic.fwplus.proto.OnDemandType
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing

/** One button on the OnDemand screen: the request it sends and the response type that answers it. */
enum class OnDemandQuery(
    val request: OnDemandType,
    val response: OnDemandType,
    /** The lowest FW+ version (OnDemand REQUEST_FW_PLUS_VERSION) that answers it; null when every FW+ build does. */
    val minFwPlusVersion: Int? = null,
) {
    RX_HISTORY(OnDemandType.REQUEST_PACKET_RX_HISTORY, OnDemandType.RESPONSE_PACKET_RX_HISTORY),
    RX_AVG_TIME(OnDemandType.REQUEST_RX_AVG_TIME, OnDemandType.RESPONSE_RX_AVG_TIME),
    PORT_COUNTERS(OnDemandType.REQUEST_PORT_COUNTER_HISTORY, OnDemandType.RESPONSE_PORT_COUNTER_HISTORY),
    NODES(OnDemandType.REQUEST_NODES_ONLINE, OnDemandType.RESPONSE_NODES_ONLINE),

    // The firmware answers the direct-only list with the same response type as the full one.
    NODES_DIRECT(OnDemandType.REQUEST_NODES_DIRECT_ONLINE, OnDemandType.RESPONSE_NODES_ONLINE),
    PACKET_LOG(OnDemandType.REQUEST_PACKET_EXCHANGE_HISTORY, OnDemandType.RESPONSE_PACKET_EXCHANGE_HISTORY),
    AIR_ACTIVITY(OnDemandType.REQUEST_AIR_ACTIVITY_HISTORY, OnDemandType.RESPONSE_AIR_ACTIVITY_HISTORY),

    // Build 1372+ only: older FW+ answers UNKNOWN_TYPE, and musznik's FW+ used these numbers for the DTN overlay.
    NOISE_FLOOR(
        OnDemandType.REQUEST_NOISE_FLOOR_HISTORY,
        OnDemandType.RESPONSE_NOISE_FLOOR_HISTORY,
        minFwPlusVersion = NOISE_FLOOR_MIN_FW_PLUS_VERSION,
    ),
    NODE_STATS(OnDemandType.REQUEST_NODE_STATS, OnDemandType.RESPONSE_NODE_STATS),
    BROADCAST_ASSIST(OnDemandType.REQUEST_BROADCAST_ASSIST_STATS, OnDemandType.RESPONSE_BROADCAST_ASSIST_STATS),
    ROUTING_ERRORS(OnDemandType.REQUEST_ROUTING_ERRORS, OnDemandType.RESPONSE_ROUTING_ERRORS),
    FW_VERSION(OnDemandType.REQUEST_FW_PLUS_VERSION, OnDemandType.RESPONSE_FW_PLUS_VERSION),
    PING(OnDemandType.REQUEST_PING, OnDemandType.RESPONSE_PING),
    PING_ACK(OnDemandType.REQUEST_PING_ACK, OnDemandType.RESPONSE_PING_ACK),
    ROUTING_TABLE(OnDemandType.REQUEST_ROUTING_TABLE, OnDemandType.RESPONSE_ROUTING_TABLE),
    ;

    /** The payload to send on [FwPlusPortNum.ON_DEMAND_APP]. */
    fun requestPayload(): ByteString = OnDemand(request = OnDemandRequest(request)).encodeByteString()
}

/**
 * Reassembles OnDemand responses the firmware splits over several packets (node lists, routing tables, logs).
 *
 * Each packet carries `packet_index` (1-based) and `packet_total`. Decoding the concatenation of the parts merges them
 * the protobuf way: repeated fields append and the response type is kept, which is exactly how the firmware splits a
 * list. A response without `packet_total` is a single packet.
 */
class OnDemandAssembler {
    private val parts = mutableMapOf<OnDemandType, MutableMap<Int, ByteString>>()

    /** Returns the complete response once every part of it has arrived, otherwise null. */
    fun accept(onDemand: OnDemand): OnDemandResponse? {
        val response = onDemand.response
        val total = (onDemand.packet_total ?: 1).coerceAtLeast(1)
        return when {
            response == null -> null
            total == 1 -> response
            else -> collect(response, onDemand.packet_index, total)
        }
    }

    private fun collect(response: OnDemandResponse, index: Int?, total: Int): OnDemandResponse? {
        val received = parts.getOrPut(response.response_type) { mutableMapOf() }
        received[index ?: (received.size + 1)] = response.encodeByteString()
        if (received.size < total) return null
        parts.remove(response.response_type)
        val merged = Buffer()
        received.entries.sortedBy { it.key }.forEach { merged.write(it.value) }
        return OnDemandResponse.ADAPTER.decode(merged.readByteString())
    }

    /** Drops any half-received response of [type], so a repeated request starts clean. */
    fun reset(type: OnDemandType) {
        parts.remove(type)
    }
}

/**
 * A readable name for a port number, safe for any value. Ports the firmware counts may be absent from the upstream enum
 * (FW+ ports, private or future ones); an unknown port must never break the list.
 */
fun portName(port: Int): String = when (port) {
    FwPlusPortNum.NODE_MOD_APP -> "NODE_MOD_APP"
    FwPlusPortNum.ON_DEMAND_APP -> "ON_DEMAND_APP"
    FwPlusPortNum.IDLE_GAME_APP -> "IDLE_GAME_APP"
    else -> PortNum.fromValue(port)?.name ?: "PORT_$port"
}

/** A readable name for a routing error number, safe for any value. */
fun routingErrorName(error: Int): String = Routing.Error.fromValue(error)?.name ?: "ERROR_$error"

/** Moving average over the previous [window] points (fewer at the start), aligned with [values]. */
fun movingAverage(values: List<Int>, window: Int): List<Double> = movingAverageOf(values.map(Int::toDouble), window)

/** Average of the newest [count] values, where the newest value is the last one. */
fun averageOfLast(values: List<Int>, count: Int): Double? = values.takeLast(count).takeIf { it.isNotEmpty() }?.average()

/** Formats [seconds] as "1d 2h 3m 4s", leaving out leading zero units. */
fun formatDuration(seconds: Long): String {
    val d = seconds / 86_400
    val h = seconds % 86_400 / 3_600
    val m = seconds % 3_600 / 60
    val s = seconds % 60
    return buildList {
        if (d > 0) add("${d}d")
        if (d > 0 || h > 0) add("${h}h")
        if (d > 0 || h > 0 || m > 0) add("${m}m")
        add("${s}s")
    }
        .joinToString(" ")
}

/** Formats a byte count as B / KB / MB with one decimal for the larger units. */
fun formatBytes(bytes: Long, decimal: (Double) -> String): String = when {
    bytes >= 1_048_576 -> "${decimal(bytes / 1_048_576.0)} MB"
    bytes >= 1_024 -> "${decimal(bytes / 1_024.0)} KB"
    else -> "$bytes B"
}

/** Air-activity windows are 10 minutes long; the firmware reports milliseconds of airtime in each. */
const val AIR_WINDOW_MS = 600_000.0

/** Percent of a 10-minute window that [ms] of airtime fills, clamped to 0..100. */
fun airPercent(ms: Int): Double = (ms / AIR_WINDOW_MS * 100.0).coerceIn(0.0, 100.0)

/** The first FW+ version that answers [OnDemandQuery.NOISE_FLOOR]. */
const val NOISE_FLOOR_MIN_FW_PLUS_VERSION = 1372

/**
 * firmware-plus137 firmware versions end in "137<build>" plus a board suffix: up to build 1371 only P (E22P) / R
 * (PSRAM), e.g. "2.8.3.1371PR"; from 1372 a board code first ("Hy", "HyPR", "nF", "Xn", "Xs", "HeW", "He3", ...). A
 * board code starts with a capital letter (or is "nF"), which keeps upstream versions ending in a lowercase hex commit
 * hash out.
 */
private val FW_PLUS_FIRMWARE_VERSION = Regex("""^\d+\.\d+\.\d+\.(137\d+)(?:nF|[A-Z][A-Za-z0-9]{0,4})?$""")

/** The FW+ version ("1371") inside a firmware-plus137 firmware version string, or null for any other firmware. */
fun fwPlusVersionOf(firmwareVersion: String?): Int? =
    firmwareVersion?.let { FW_PLUS_FIRMWARE_VERSION.matchEntire(it) }?.groupValues?.get(1)?.toIntOrNull()

/** One noise floor average placed in time: [secondsAgo] before the response was built. */
data class NoiseFloorPoint(val secondsAgo: Long, val dbm: Int)

/**
 * The history oldest first, from the parallel newest-first lists the firmware sends. Entries beyond the shorter list
 * are dropped, so a malformed response cannot misalign values and ages.
 */
fun noiseFloorPoints(noiseFloor: List<Int>, ageSecs: List<Int>): List<NoiseFloorPoint> = noiseFloor
    .zip(ageSecs) { dbm, age -> NoiseFloorPoint(age.toUInt().toLong(), dbm) }
    .sortedByDescending { it.secondsAgo }

/** Moving average over the previous [window] points (fewer at the start), aligned with [values]. */
fun movingAverageOf(values: List<Double>, window: Int): List<Double> = values.indices.map { i ->
    val from = (i - window + 1).coerceAtLeast(0)
    values.subList(from, i + 1).average()
}
