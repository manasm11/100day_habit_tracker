package com.manasm.habit100.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.manasm.habit100.ui.theme.HabitColors

enum class GridSize(val gapFraction: Float, val cornerFraction: Float, val strokeDp: Float) {
    HERO(0.14f, 0.22f, 2f),
    TROPHY(0.12f, 0.22f, 2f),
    THUMBNAIL(0.10f, 0.18f, 1f),
}

@Composable
fun HabitGrid(cells: List<CellState>, size: GridSize, modifier: Modifier = Modifier) {
    val future = Color(0x1F000000)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cols = 10
        val cell = this.size.width / (cols + (cols - 1) * size.gapFraction)
        val gap = cell * size.gapFraction
        val radius = CornerRadius(cell * size.cornerFraction)
        cells.forEachIndexed { i, state ->
            val cx = (i % cols) * (cell + gap)
            val cy = (i / cols) * (cell + gap)
            val topLeft = Offset(cx, cy)
            val cs = Size(cell, cell)
            when (state) {
                CellState.DONE -> drawRoundRect(HabitColors.done, topLeft, cs, radius)
                CellState.MISSED -> drawRoundRect(HabitColors.missed, topLeft, cs, radius)
                CellState.FUTURE -> drawRoundRect(future, topLeft, cs, radius)
                CellState.TODAY -> drawRoundRect(
                    color = HabitColors.done.copy(alpha = 0.9f),
                    topLeft = topLeft, size = cs, cornerRadius = radius,
                    style = Stroke(width = size.strokeDp.dp.toPx()),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitGridHeroPreview() {
    val sampleCells = listOf(
        CellState.DONE, CellState.DONE, CellState.MISSED, CellState.TODAY,
        CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE,
        CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
        CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
    ) + List(80) { CellState.FUTURE }
    HabitGrid(cells = sampleCells, size = GridSize.HERO)
}

@Preview(showBackground = true)
@Composable
private fun HabitGridThumbnailPreview() {
    val sampleCells = listOf(
        CellState.DONE, CellState.DONE, CellState.MISSED, CellState.TODAY,
        CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE,
        CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
        CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
    ) + List(80) { CellState.FUTURE }
    HabitGrid(cells = sampleCells, size = GridSize.THUMBNAIL)
}
