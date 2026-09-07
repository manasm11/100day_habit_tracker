package com.manasm.habit100.rollover

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.manasm.habit100.HabitApplication

/**
 * Daily backstop for the rollover engine. Runs even when the app is never brought to the
 * foreground so elapsed misses are still filled and terminal transitions persisted.
 */
class RolloverWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as HabitApplication
            app.container.runRolloverNow()
            Result.success()
        } catch (e: Exception) {
            Log.w("RolloverWorker", "rollover failed; will retry", e)
            Result.retry()
        }
    }
}
