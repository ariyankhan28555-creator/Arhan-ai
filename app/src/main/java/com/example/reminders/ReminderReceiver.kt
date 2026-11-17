package com.example.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "NOVA Smart Reminder"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Personal"
        val notificationId = intent.getIntExtra(EXTRA_NOTIF_ID, title.hashCode())

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart reminders scheduled via NOVA AI"
            }
            manager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NOVA AI • $category")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingTap)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "nova_smart_reminders"
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_CATEGORY = "extra_reminder_category"
        const val EXTRA_NOTIF_ID = "extra_reminder_notif_id"
    }
}
