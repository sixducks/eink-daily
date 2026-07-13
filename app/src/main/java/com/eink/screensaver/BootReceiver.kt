package com.eink.screensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：重排定时任务并立即生成一张。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.scheduleDaily(context)
            Scheduler.runNow(context)
        }
    }
}
