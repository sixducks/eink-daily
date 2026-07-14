package com.eink.screensaver

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Scheduler {

    private const val PREFS = "eink_daily"
    private const val KEY_HOUR = "refresh_hour"
    private const val KEY_MIN = "refresh_min"
    private const val KEY_AUTO = "auto_enabled"
    private const val PERIODIC_NAME = "eink_daily_periodic"

    const val DEFAULT_HOUR = 7
    const val DEFAULT_MIN = 0

    fun getHour(ctx: Context): Int = prefs(ctx).getInt(KEY_HOUR, DEFAULT_HOUR)
    fun getMinute(ctx: Context): Int = prefs(ctx).getInt(KEY_MIN, DEFAULT_MIN)

    /** 设定每天刷新时间并按开关状态重排任务。 */
    fun setTime(ctx: Context, hour: Int, minute: Int) {
        prefs(ctx).edit().putInt(KEY_HOUR, hour).putInt(KEY_MIN, minute).apply()
        applySchedule(ctx)
    }

    /** 自动刷新总开关。关：不再覆盖，掌阅每天 00:00 恢复自带系统日历。 */
    fun isAutoEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO, true)

    fun setAutoEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO, enabled).apply()
        applySchedule(ctx)
    }

    /** 按开关状态启用或取消每日任务。 */
    fun applySchedule(ctx: Context) {
        if (isAutoEnabled(ctx)) scheduleDaily(ctx)
        else WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC_NAME)
    }

    /**
     * 每天在设定时间刷新一次。
     * WorkManager 近似准点：设备休眠/无网时会顺延到下次醒来且联网时执行。
     */
    fun scheduleDaily(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs(context), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    /** 立即跑一次（联网时）。 */
    fun runNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<DailyWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    /** 距离下一个"设定时间"的毫秒数（今天已过就排到明天）。 */
    private fun initialDelayMs(context: Context): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, getHour(context))
            set(Calendar.MINUTE, getMinute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
