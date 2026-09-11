package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            loadTasks(context).filter { !it.done && it.due > System.currentTimeMillis() }.forEach { scheduleReminder(context, it) }
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "homework_reminders"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channelId, "یادآوری مشق‌ها", NotificationManager.IMPORTANCE_HIGH).apply { description = "یادآوری زمان انجام تکالیف" })
        val subject = intent.getStringExtra("subject") ?: "مشق"
        val body = intent.getStringExtra("title") ?: "یک تکلیف داری"
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 $subject")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify((intent.getLongExtra("id", System.currentTimeMillis())).toInt(), n)
    }
}
