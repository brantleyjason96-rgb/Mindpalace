package com.mindpalace.app.ai

import android.content.Context
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mindpalace.app.MindPalaceApplication
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The WorkManager wrapper [AiNightRunner]'s doc comment calls "the next piece, not built yet"
 * (D13/D22/D23): runs once a day, only while charging, idle, and battery not low — the
 * standard "maintenance window" shape so the night loop never costs the user anything they'd
 * notice. No model-file picker yet (CLAUDE.md backlog "AI setup"), so if nothing's at
 * [AiModelConfig.modelPath] this is a silent no-op rather than a failure to retry forever.
 */
class AiNightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WorkManager's own constraints already gate charging/idle/battery; this is an extra,
        // best-effort guard against running a multi-minute completion while the phone is hot —
        // there's no Constraints flag for thermal state, so retry later and let the next
        // maintenance window re-check.
        val powerManager = applicationContext.getSystemService(PowerManager::class.java)
        if (powerManager != null && powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            return Result.retry()
        }

        val modelFile = File(AiModelConfig.modelPath(applicationContext))
        if (!modelFile.exists()) return Result.success()

        val container = (applicationContext as MindPalaceApplication).container
        val since = container.database.aiRunDao().lastSucceededFinishedAt() ?: 0L
        val summary = container.aiNightRunner.run(
            context = applicationContext,
            modelPath = modelFile.path,
            modelName = AiModelConfig.MODEL_NAME,
            since = since,
        )
        return if (summary.outcome == "SUCCEEDED") Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ai_night_loop"
        private const val MANUAL_WORK_NAME = "ai_night_loop_manual"

        /** Idempotent — safe to call on every app start ([MindPalaceApplication.onCreate]);
         *  KEEP means an already-scheduled periodic run is left alone, not reset. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AiNightWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** AI Setup's "Run now" — no constraints, runs as soon as WorkManager can schedule it,
         *  ignoring charging/idle/battery (the user explicitly asked for it right now). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiNightWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
