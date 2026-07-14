package com.eink.screensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：按开关状态重排定时任务，开启时立即生成一张。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.applySchedule(context)
            if (Scheduler.isAutoEnabled(context)) Scheduler.runNow(context)
        }
    }
}
