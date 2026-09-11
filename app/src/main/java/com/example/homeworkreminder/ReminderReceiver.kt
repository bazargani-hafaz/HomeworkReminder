package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "homework_reminders"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channelId, "یادآوری مشق‌ها", NotificationManager.IMPORTANCE_HIGH))
        val title = intent.getStringExtra("subject") ?: "مشق"
        val body = intent.getStringExtra("title") ?: "یک تکلیف داری"
        val n = NotificationCompat.Builder(context, channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("🔔 $title").setContentText(body).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        manager.notify(System.currentTimeMillis().toInt(), n)
    }
}
