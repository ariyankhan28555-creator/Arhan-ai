package com.example.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(id: String, title: String, category: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val notifId = id.hashCode()
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_CATEGORY, category)
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    fun cancelReminder(id: String) {
        val notifId = id.hashCode()
        val intent = Intent(appContext, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            appContext,
            notifId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
        }
    }
}
