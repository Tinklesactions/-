package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: ALARM_TYPE_CLASS
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 1001)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "课表提醒"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "你有即将开始的日程"

        val channelId = if (type == ALARM_TYPE_EXAM) {
            CHANNEL_EXAM_REMINDERS
        } else {
            CHANNEL_CLASS_REMINDERS
        }

        ReminderScheduler.postNotification(
            context = context,
            notificationId = requestCode,
            channelId = channelId,
            title = title,
            body = body
        )

        if (type == ALARM_TYPE_CLASS) {
            ReminderScheduler.scheduleNextWeekClass(context, intent)
        }
    }
}
