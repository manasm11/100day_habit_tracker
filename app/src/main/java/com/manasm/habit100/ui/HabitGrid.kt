package com.manasm.habit100.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.manasm.habit100.ui.theme.HabitColors
import com.manasm.habit100.ui.theme.HabitTheme

enum class GridSize(val gapFraction: Float, val cornerFraction: Float, val strokeDp: Float) {
    HERO(0.14f, 0.22f, 2f),
    TROPHY(0.12f, 0.22f, 2f),
    THUMBNAIL(0.10f, 0.18f, 1f),
}

@Composable
fun HabitGrid(cells: List<CellState>, size: GridSize, modifier: Modifier = Modifier) {
    // Capture theme colors in the composable body — the Canvas draw block is not @Composable.
    val futureColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val todayStroke = HabitColors.done.copy(alpha = 0.9f)
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
                CellState.FUTURE -> drawRoundRect(futureColor, topLeft, cs, radius)
                CellState.TODAY -> drawRoundRect(
                    color = todayStroke,
                    topLeft = topLeft, size = cs, cornerRadius = radius,
                    style = Stroke(width = size.strokeDp.dp.toPx()),
                )
            }
        }
    }
}

private val previewCells = listOf(
    CellState.DONE, CellState.DONE, CellState.MISSED, CellState.TODAY,
    CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE, CellState.FUTURE,
    CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
    CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE, CellState.DONE,
) + List(80) { CellState.FUTURE }

@Preview(showBackground = true)
@Composable
private fun HabitGridHeroPreview() {
    HabitTheme(darkTheme = false) { HabitGrid(cells = previewCells, size = GridSize.HERO) }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HabitGridHeroDarkPreview() {
    HabitTheme(darkTheme = true) { HabitGrid(cells = previewCells, size = GridSize.HERO) }
}

@Preview(showBackground = true)
@Composable
private fun HabitGridThumbnailPreview() {
    HabitTheme(darkTheme = false) { HabitGrid(cells = previewCells, size = GridSize.THUMBNAIL) }
}
