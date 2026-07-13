package com.eink.screensaver

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** WorkManager 定时任务：生成并覆盖今日待机图。 */
class DailyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val file = runCatching { ImageGenerator.generate(applicationContext) }.getOrNull()
        return if (file != null) Result.success() else Result.retry()
    }
}
