package com.echo.recorder

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real end-to-end check on an emulator: start -> record ~4 s -> Stop -> the memory must be saved. */
@RunWith(AndroidJUnit4::class)
class RecordFlowTest {
    @get:Rule val perm: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(200) }
        return cond()
    }

    @Test fun stopSavesTheRecording() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Repository.init(ctx)
        val before = Repository.items.value.map { it.id }.toSet()

        RecorderService.send(ctx, RecorderService.ACTION_START)
        assertTrue("recording never started", waitFor(10_000) {
            RecorderState.state.value.status == RecorderState.Status.Recording
        })
        Thread.sleep(4_000)
        assertTrue("timer did not advance", RecorderState.state.value.elapsedMs >= 3_000)

        RecorderService.send(ctx, RecorderService.ACTION_STOP)
        assertTrue("recording was NOT saved after Stop", waitFor(15_000) {
            Repository.items.value.any { it.id !in before }
        })
        val rec = Repository.items.value.first { it.id !in before }
        assertTrue("duration wrong: ${rec.durationMs}", rec.durationMs in 3_000..8_000)
        val f = File(rec.path)
        assertTrue("audio file missing/empty", f.exists() && f.length() > 1_000)
        assertEquals(RecorderState.Status.Idle, RecorderState.state.value.status)
    }
}
