package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.LOCKED_BOOT_COMPLETED" -> {
                loadTasks(context).filter { !it.done && it.due > System.currentTimeMillis() }.forEach { scheduleReminder(context, it) }
                return
            }
            "ACTION_DONE" -> {
                val id = intent.getLongExtra("id", -1L)
                if (id != -1L) toggleTask(context, id)
                cancelNotification(context, id)
                return
            }
            "ACTION_SNOOZE" -> {
                val id = intent.getLongExtra("id", -1L)
                val task = loadTasks(context).firstOrNull { it.id == id }
                if (task != null && !task.done) {
                    val snoozed = task.copy(due = System.currentTimeMillis() + 15 * 60_000L, reminderMinutes = 0)
                    saveTask(context, snoozed)
                }
                cancelNotification(context, id)
                return
            }
            "REMINDER" -> showReminder(context, intent)
        }
    }

    private fun showReminder(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "homework_reminders"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "یادآوری مشق‌ها", NotificationManager.IMPORTANCE_HIGH).apply { description = "یادآوری زمان انجام تکالیف" })
        }
        val id = intent.getLongExtra("id", System.currentTimeMillis())
        val subject = intent.getStringExtra("subject") ?: "مشق"
        val title = intent.getStringExtra("title") ?: "یک تکلیف داری"
        val doneIntent = Intent(context, ReminderReceiver::class.java).apply { action = "ACTION_DONE"; putExtra("id", id) }
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply { action = "ACTION_SNOOZE"; putExtra("id", id) }
        val donePi = PendingIntent.getBroadcast(context, (id + 1000001L).hashCode(), doneIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snoozePi = PendingIntent.getBroadcast(context, (id + 2000001L).hashCode(), snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val contentIntent = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 $subject")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n\nموعد این تکلیف رسیده یا نزدیک است."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_save, "انجام شد", donePi)
            .addAction(android.R.drawable.ic_menu_recent_history, "۱۵ دقیقه بعد", snoozePi)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    private fun cancelNotification(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }
}
