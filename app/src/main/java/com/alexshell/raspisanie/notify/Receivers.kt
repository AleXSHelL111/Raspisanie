package com.alexshell.raspisanie.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Срабатывание будильника: показать уведомления и запланировать следующее. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                Notifier.onAlarm(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** После перезагрузки, обновления приложения или смены времени будильники нужно поставить заново. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.reschedule(context)
    }
}
