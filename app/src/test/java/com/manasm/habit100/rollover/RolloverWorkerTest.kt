package com.manasm.habit100.rollover

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.manasm.habit100.HabitApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HabitApplication::class)
class RolloverWorkerTest {

    @Test
    fun worker_runs_and_succeeds_with_no_active_habit() {
        val ctx = ApplicationProvider.getApplicationContext<HabitApplication>()
        val worker = TestListenableWorkerBuilder<RolloverWorker>(ctx).build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
