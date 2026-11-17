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

import org.meshtastic.core.fwplus.FwPlusPortNum
import org.meshtastic.core.model.fwPlusVersionOf
import org.meshtastic.fwplus.proto.NodeEntry
import org.meshtastic.fwplus.proto.NodesList
import org.meshtastic.fwplus.proto.NoiseFloorHistory
import org.meshtastic.fwplus.proto.OnDemand
import org.meshtastic.fwplus.proto.OnDemandRequest
import org.meshtastic.fwplus.proto.OnDemandResponse
import org.meshtastic.fwplus.proto.OnDemandType
import org.meshtastic.fwplus.proto.RxPacketHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnDemandProtocolTest {

    private fun nodesPart(index: Int, total: Int, vararg ids: Int) = OnDemand(
        packet_index = index,
        packet_total = total,
        response =
        OnDemandResponse(
            response_type = OnDemandType.RESPONSE_NODES_ONLINE,
            node_list = NodesList(ids.map { NodeEntry(node_id = it, long_name = "n$it") }),
        ),
    )

    @Test
    fun singlePacketResponseIsCompleteAtOnce() {
        val response =
            OnDemandResponse(
                response_type = OnDemandType.RESPONSE_PACKET_RX_HISTORY,
                rx_packet_history = RxPacketHistory(listOf(3, 2, 1)),
            )

        val assembled = OnDemandAssembler().accept(OnDemand(packet_index = 1, packet_total = 1, response = response))

        assertEquals(response, assembled)
    }

    @Test
    fun segmentedNodeListIsMergedInPacketOrderWhateverTheArrivalOrder() {
        val assembler = OnDemandAssembler()

        assertNull(assembler.accept(nodesPart(2, 3, 4, 5)))
        assertNull(assembler.accept(nodesPart(1, 3, 1, 2, 3)))
        val merged = assembler.accept(nodesPart(3, 3, 6))

        assertEquals(listOf(1, 2, 3, 4, 5, 6), merged?.node_list?.node_list?.map { it.node_id })
        assertEquals(OnDemandType.RESPONSE_NODES_ONLINE, merged?.response_type)
    }

    @Test
    fun aRepeatedPartIsNotCountedTwice() {
        val assembler = OnDemandAssembler()

        assertNull(assembler.accept(nodesPart(1, 2, 1)))
        assertNull(assembler.accept(nodesPart(1, 2, 1)))
        assertEquals(listOf(1, 2), assembler.accept(nodesPart(2, 2, 2))?.node_list?.node_list?.map { it.node_id })
    }

    @Test
    fun resetDropsAHalfReceivedResponse() {
        val assembler = OnDemandAssembler()
        assembler.accept(nodesPart(1, 2, 1))

        assembler.reset(OnDemandType.RESPONSE_NODES_ONLINE)

        assertNull(assembler.accept(nodesPart(2, 2, 2)))
    }

    @Test
    fun aRequestIsIgnoredByTheAssembler() {
        assertNull(OnDemandAssembler().accept(OnDemand(request = OnDemandRequest(OnDemandType.REQUEST_PING))))
    }

    @Test
    fun requestPayloadEncodesTheRequestType() {
        val decoded = OnDemand.ADAPTER.decode(OnDemandQuery.NODES_DIRECT.requestPayload())

        assertEquals(OnDemandType.REQUEST_NODES_DIRECT_ONLINE, decoded.request?.request_type)
    }

    @Test
    fun portNamesNeverFailForUnknownPorts() {
        assertEquals("TEXT_MESSAGE_APP", portName(1))
        assertEquals("ON_DEMAND_APP", portName(FwPlusPortNum.ON_DEMAND_APP))
        assertEquals("NODE_MOD_APP", portName(FwPlusPortNum.NODE_MOD_APP))
        assertEquals("PORT_9999", portName(9999))
        assertEquals("PORT_-1", portName(-1))
    }

    @Test
    fun routingErrorNamesNeverFailForUnknownErrors() {
        assertEquals("NO_ROUTE", routingErrorName(1))
        assertEquals("ERROR_250", routingErrorName(250))
    }

    @Test
    fun movingAverageUsesTheAvailablePointsAtTheStart() {
        assertEquals(listOf(2.0, 3.0, 4.0, 6.0), movingAverage(listOf(2, 4, 6, 8), window = 3))
    }

    @Test
    fun averageOfLastTakesTheNewestValues() {
        assertEquals(7.0, averageOfLast(listOf(1, 1, 6, 8), 2))
        assertEquals(4.0, averageOfLast(listOf(1, 1, 6, 8), 10))
        assertNull(averageOfLast(emptyList(), 6))
    }

    @Test
    fun durationsDropLeadingZeroUnits() {
        assertEquals("17s", formatDuration(17))
        assertEquals("5m 30s", formatDuration(330))
        assertEquals("1d 1h 9m 46s", formatDuration(90_586))
    }

    @Test
    fun airtimeIsAPercentOfTheTenMinuteWindow() {
        assertEquals(10.0, airPercent(60_000))
        assertEquals(100.0, airPercent(900_000))
        assertEquals(0.0, airPercent(0))
    }

    @Test
    fun noiseFloorHistoryTurnsNewestFirstListsIntoOldestFirstPoints() {
        val points = noiseFloorPoints(noiseFloor = listOf(-110, -112, -115), ageSecs = listOf(5, 105, 230))
        assertEquals(listOf(230L, 105L, 5L), points.map { it.secondsAgo })
        assertEquals(listOf(-115, -112, -110), points.map { it.dbm })
    }

    @Test
    fun noiseFloorHistoryDropsUnpairedEntries() {
        assertEquals(1, noiseFloorPoints(listOf(-110, -111), listOf(7)).size)
        assertEquals(emptyList(), noiseFloorPoints(emptyList(), emptyList()))
    }

    @Test
    fun noiseFloorHistoryRoundTripsThroughTheWire() {
        val bytes =
            OnDemand(
                response =
                OnDemandResponse(
                    response_type = OnDemandType.RESPONSE_NOISE_FLOOR_HISTORY,
                    noise_floor_history =
                    NoiseFloorHistory(noise_floor = listOf(-127, -1), age_secs = listOf(0, 99)),
                ),
            )
                .encode()
        val decoded = OnDemand.ADAPTER.decode(bytes).response?.noise_floor_history
        assertEquals(listOf(-127, -1), decoded?.noise_floor)
        assertEquals(listOf(0, 99), decoded?.age_secs)
    }

    @Test
    fun noiseFloorQueryNeedsBuild1372() {
        assertEquals(26, OnDemandQuery.NOISE_FLOOR.request.value)
        assertEquals(27, OnDemandQuery.NOISE_FLOOR.response.value)
        assertEquals(1372, OnDemandQuery.NOISE_FLOOR.minFwPlusVersion)
        assertNull(OnDemandQuery.AIR_ACTIVITY.minFwPlusVersion)
    }

    @Test
    fun fwPlusVersionIsReadFromFirmwarePlus137Versions() {
        assertEquals(1371, fwPlusVersionOf("2.8.3.1371PR"))
        assertEquals(1372, fwPlusVersionOf("2.8.4.1372"))
        assertNull(fwPlusVersionOf("2.8.3.40PR"))
        assertNull(fwPlusVersionOf("2.8.3.137abcd"))
        assertEquals(1372, fwPlusVersionOf("2.8.3.1372HyPR"))
        assertEquals(1372, fwPlusVersionOf("2.8.3.1372nF"))
        assertEquals(1373, fwPlusVersionOf("2.8.3.1373HeW"))
        assertNull(fwPlusVersionOf("2.8.3.1372abc")) // upstream hash that happens to start with 1372
        assertNull(fwPlusVersionOf(null))
    }

    @Test
    fun cooldownIsLongerForMultiPacketQueriesAndNoneForOurOwnNode() {
        assertEquals(30_000L, OnDemandQuery.NODE_STATS.cooldownMillis(toOwnNode = false))
        assertEquals(30_000L, OnDemandQuery.NODES.cooldownMillis(toOwnNode = false)) // one packet from build 1373
        assertEquals(60_000L, OnDemandQuery.ROUTING_TABLE.cooldownMillis(toOwnNode = false))
        assertEquals(60_000L, OnDemandQuery.NODES_DIRECT.cooldownMillis(toOwnNode = false))
        assertEquals(0L, OnDemandQuery.ROUTING_TABLE.cooldownMillis(toOwnNode = true))
    }
}
