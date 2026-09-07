package com.manasm.habit100.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.manasm.habit100.ui.CellState
import java.io.File
import android.graphics.Canvas as AndroidCanvas

/**
 * Renders an offscreen PNG of the 10x10 grid and fires an ACTION_SEND chooser.
 * Best-effort: any failure (no share targets, IO) is swallowed.
 */
fun shareGrid(context: Context, cells: List<CellState>, caption: String) {
    val px = 1080
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(Color.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val gap = px * 0.012f
    val cell = (px - gap * 9) / 10f
    val future = Color.rgb(0xEC, 0xEC, 0xEC)

    cells.take(100).forEachIndexed { i, state ->
        val x = (i % 10) * (cell + gap)
        val y = (i / 10) * (cell + gap)
        paint.color = when (state) {
            CellState.DONE -> Color.rgb(0x1D, 0x9E, 0x75)
            CellState.MISSED -> Color.rgb(0xE2, 0x4B, 0x4A)
            else -> future
        }
        val r = cell * 0.2f
        canvas.drawRoundRect(x, y, x + cell, y + cell, r, r, paint)
    }

    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Unique name per share so a chooser holding a stale URI can't re-send an old grid;
        // cacheDir is OS-managed so leftover files get reclaimed under pressure.
        val cutoff = System.currentTimeMillis() - 5 * 60_000
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "grid-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your grid"))
    }
}
