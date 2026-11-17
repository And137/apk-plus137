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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val CHART_HEIGHT = 180.dp
private const val GRID_LINES = 4

/**
 * A line over equally spaced 10-minute buckets, oldest on the left, with an optional dashed overlay (a moving average).
 * One y-axis: the overlay shares the series' scale. Gridlines are recessive; text stays in text colors.
 *
 * [positions] places the points on the x-axis (0 = left edge, 1 = right edge) for irregularly spaced samples; without
 * it the points are equally spaced. [floorAtZero] keeps counts from scaling below zero; signal levels (negative dBm)
 * turn it off. [unit] follows the axis labels.
 */
@Composable
internal fun BucketLineChart(
    values: List<Int>,
    description: String,
    modifier: Modifier = Modifier,
    overlay: List<Double>? = null,
    positions: List<Float>? = null,
    floorAtZero: Boolean = true,
    unit: String = "",
) {
    if (values.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val overlayColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue: Float
    val minValue: Float
    if (floorAtZero) {
        maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        minValue = (values.minOrNull() ?: 0).toFloat().coerceAtMost(maxValue - 1f).coerceAtLeast(0f)
    } else {
        // A flat series still needs a band to draw in: pad it by a unit either side.
        maxValue = values.max().toFloat() + 1f
        minValue = values.min().toFloat() - 1f
    }
    val suffix = if (unit.isEmpty()) "" else " $unit"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(maxValue.toInt().toString() + suffix, style = MaterialTheme.typography.labelSmall)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = description }) {
            drawGrid(gridColor)
            val xStep = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun x(i: Int) = positions?.getOrNull(i)?.let { it.coerceIn(0f, 1f) * size.width } ?: (i * xStep)
            fun y(v: Float) = size.height - (v - minValue) / (maxValue - minValue) * size.height
            val path = Path()
            values.forEachIndexed { i, v ->
                val point = Offset(x(i), y(v.toFloat()))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
            values.forEachIndexed { i, v -> drawCircle(lineColor, 3.dp.toPx(), Offset(x(i), y(v.toFloat()))) }
            overlay?.let { ma ->
                val maPath = Path()
                ma.forEachIndexed { i, v ->
                    val point = Offset(x(i), y(v.toFloat()))
                    if (i == 0) maPath.moveTo(point.x, point.y) else maPath.lineTo(point.x, point.y)
                }
                drawPath(
                    maPath,
                    overlayColor,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            }
        }
        Text(minValue.toInt().toString() + suffix, style = MaterialTheme.typography.labelSmall)
    }
}

/** One stacked bar per 10-minute window: RX, TX and bad RX as a percent of the window, the rest idle. */
@Composable
internal fun AirActivityChart(windows: List<AirWindow>, description: String, modifier: Modifier = Modifier) {
    if (windows.isEmpty()) return
    val rxColor = MaterialTheme.colorScheme.primary
    val txColor = MaterialTheme.colorScheme.tertiary
    val badColor = MaterialTheme.colorScheme.error
    val idleColor = MaterialTheme.colorScheme.surfaceVariant
    // The busiest window sets the scale, so a quiet channel is still readable; never below 10 %.
    val scaleMax = (windows.maxOf { it.rx + it.tx + it.rxBad }).coerceAtLeast(10.0)

    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = description }) {
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (windows.size - 1)) / windows.size
        val radius = CornerRadius(4.dp.toPx())
        windows.forEachIndexed { i, w ->
            val left = i * (barWidth + gap)
            drawRoundRect(idleColor, Offset(left, 0f), Size(barWidth, size.height), radius)
            var top = size.height
            listOf(w.rx to rxColor, w.tx to txColor, w.rxBad to badColor).forEach { (pct, color) ->
                val h = (pct / scaleMax * size.height).toFloat()
                if (h > 0f) {
                    top -= h
                    drawRect(color, Offset(left, top), Size(barWidth, (h - gap).coerceAtLeast(1f)))
                }
            }
        }
    }
}

/** Percent of one window spent in each state. */
data class AirWindow(val rx: Double, val tx: Double, val rxBad: Double)

private fun DrawScope.drawGrid(color: Color) {
    for (i in 0..GRID_LINES) {
        val y = size.height * i / GRID_LINES
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/** A legend row: a swatch and a label per series, so identity is never color alone. */
@Composable
internal fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
