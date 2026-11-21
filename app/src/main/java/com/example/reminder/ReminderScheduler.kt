package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Course
import com.example.data.Exam
import com.example.data.UserPreferences
import com.example.data.computeNextClassReminderMillis
import com.example.data.dayOfWeekShortLabel
import com.example.data.formatExamShortDate
import com.example.data.formatMinuteOfDay
import com.example.data.formatReminderOffsetLabel
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ReminderScheduler"
private const val PREFS_NAME = "course_exam_reminders_store"
private const val KEY_ALARMS_JSON = "scheduled_alarms_v1"

const val CHANNEL_CLASS_REMINDERS = "class_reminders"
const val CHANNEL_EXAM_REMINDERS = "exam_reminders"

const val EXTRA_ALARM_TYPE = "extra_alarm_type"
const val EXTRA_REQUEST_CODE = "extra_request_code"
const val EXTRA_TITLE = "extra_title"
const val EXTRA_BODY = "extra_body"
const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
const val EXTRA_START_MINUTE = "extra_start_minute"
const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"

const val ALARM_TYPE_CLASS = "CLASS"
const val ALARM_TYPE_EXAM = "EXAM"

object ReminderScheduler {

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val classChannel = NotificationChannel(
                CHANNEL_CLASS_REMINDERS,
                "上课提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "根据课程表的日期与时间，在课前自动发送上课通知提醒"
                enableVibration(true)
            }

            val examChannel = NotificationChannel(
                CHANNEL_EXAM_REMINDERS,
                "考前自动提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在考试前的多个时间节点自动发送考试倒计时与考场信息通知"
                enableVibration(true)
            }

            manager.createNotificationChannel(classChannel)
            manager.createNotificationChannel(examChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun syncReminders(
        context: Context,
        courses: List<Course>,
        exams: List<Exam>,
        prefs: UserPreferences
    ): Int {
        ensureNotificationChannels(context)
        cancelAllStoredAlarms(context)

        val now = System.currentTimeMillis()
        val entries = JSONArray()
        var scheduledCount = 0

        if (prefs.classRemindersEnabled) {
            courses.filter { it.reminderEnabled && it.id.isNotBlank() }.forEach { course ->
                val triggerAt = computeNextClassReminderMillis(
                    dayOfWeek = course.dayOfWeek,
                    startMinuteOfDay = course.startMinuteOfDay,
                    reminderMinutesBefore = course.reminderMinutesBefore,
                    nowMillis = now
                )
                val reqCode = stableRequestCode("CLASS_${course.id}")
                val timeStr = formatMinuteOfDay(course.startMinuteOfDay)
                val locPart = if (course.location.isNotBlank()) " · 教室：${course.location}" else ""
                val leadText = if (course.reminderMinutesBefore > 0) {
                    "${course.reminderMinutesBefore}分钟后上课"
                } else {
                    "现在开始上课"
                }
                val title = "上课提醒：${course.courseName}"
                val body = "${dayOfWeekShortLabel(course.dayOfWeek)} $timeStr（$leadText）$locPart"

                val obj = JSONObject().apply {
                    put("type", ALARM_TYPE_CLASS)
                    put("requestCode", reqCode)
                    put("title", title)
                    put("body", body)
                    put("triggerAt", triggerAt)
                    put("dayOfWeek", course.dayOfWeek)
                    put("startMinute", course.startMinuteOfDay)
                    put("leadMinutes", course.reminderMinutesBefore)
                }
                entries.put(obj)
                scheduleAlarmInternal(context, obj)
                scheduledCount++
            }
        }

        if (prefs.examRemindersEnabled) {
            exams.filter { it.reminderEnabled && it.id.isNotBlank() && it.examEpochMillis > now }.forEach { exam ->
                val offsets = exam.reminderOffsetsMinutes.distinct().sortedDescending()
                offsets.forEach { offsetMinutes ->
                    val triggerAt = exam.examEpochMillis - offsetMinutes * 60_000L
                    if (triggerAt > now + 5_000L) {
                        val reqCode = stableRequestCode("EXAM_${exam.id}_$offsetMinutes")
                        val offsetLabel = formatReminderOffsetLabel(offsetMinutes)
                        val locPart = buildString {
                            if (exam.location.isNotBlank()) append(" · 考场：${exam.location}")
                            if (exam.seatNumber.isNotBlank()) append("（座位 ${exam.seatNumber}）")
                        }
                        val title = "$offsetLabel · ${exam.title}"
                        val body = "考试时间：${formatExamShortDate(exam.examEpochMillis)}$locPart"

                        val obj = JSONObject().apply {
                            put("type", ALARM_TYPE_EXAM)
                            put("requestCode", reqCode)
                            put("title", title)
                            put("body", body)
                            put("triggerAt", triggerAt)
                        }
                        entries.put(obj)
                        scheduleAlarmInternal(context, obj)
                        scheduledCount++
                    }
                }
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS_JSON, entries.toString())
            .apply()

        Log.d(TAG, "Synced $scheduledCount reminder alarms.")
        return scheduledCount
    }

    fun clearAllReminders(context: Context) {
        cancelAllStoredAlarms(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALARMS_JSON)
            .apply()
    }

    fun rescheduleFromStorage(context: Context) {
        ensureNotificationChannels(context)
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS_JSON, null) ?: return
        try {
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            val updated = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type", ALARM_TYPE_CLASS)
                var triggerAt = obj.optLong("triggerAt", 0L)
                if (type == ALARM_TYPE_CLASS) {
                    val day = obj.optInt("dayOfWeek", 1)
                    val startMin = obj.optInt("startMinute", 480)
                    val leadMin = obj.optInt("leadMinutes", 15)
                    triggerAt = computeNextClassReminderMillis(day, startMin, leadMin, now)
                    obj.put("triggerAt", triggerAt)
                    scheduleAlarmInternal(context, obj)
                    updated.put(obj)
                } else if (triggerAt > now + 5_000L) {
                    scheduleAlarmInternal(context, obj)
                    updated.put(obj)
                }
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALARMS_JSON, updated.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule stored alarms after boot", e)
        }
    }

    fun scheduleNextWeekClass(context: Context, intent: Intent) {
        val day = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)
        val startMin = intent.getIntExtra(EXTRA_START_MINUTE, -1)
        val leadMin = intent.getIntExtra(EXTRA_LEAD_MINUTES, 15)
        val reqCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        if (day !in 1..7 || startMin < 0 || reqCode == -1) return

        val nextTrigger = computeNextClassReminderMillis(
            dayOfWeek = day,
            startMinuteOfDay = startMin,
            reminderMinutesBefore = leadMin,
            nowMillis = System.currentTimeMillis() + 60_000L
        )
        val obj = JSONObject().apply {
            put("type", ALARM_TYPE_CLASS)
            put("requestCode", reqCode)
            put("title", title)
            put("body", body)
            put("triggerAt", nextTrigger)
            put("dayOfWeek", day)
            put("startMinute", startMin)
            put("leadMinutes", leadMin)
        }
        scheduleAlarmInternal(context, obj)
    }

    fun postNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String
    ): Boolean {
        ensureNotificationChannels(context)
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; skipping notification.")
            return false
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification", e)
            false
        }
    }

    fun sendTestNotification(context: Context): Boolean {
        return postNotification(
            context = context,
            notificationId = 900001,
            channelId = CHANNEL_EXAM_REMINDERS,
            title = "课表提醒 · 测试通知成功",
            body = "上课提醒与考前自动通知均已就绪，将按计划准时推送给你！"
        )
    }

    private fun cancelAllStoredAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS_JSON, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val reqCode = obj.optInt("requestCode", -1)
                if (reqCode != -1) {
                    val intent = Intent(context, ReminderReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        context,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pending != null) {
                        alarmManager.cancel(pending)
                        pending.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel previous alarms", e)
        }
    }

    private fun scheduleAlarmInternal(context: Context, obj: JSONObject) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reqCode = obj.getInt("requestCode")
        val triggerAt = obj.getLong("triggerAt")
        val type = obj.getString("type")

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, type)
            putExtra(EXTRA_REQUEST_CODE, reqCode)
            putExtra(EXTRA_TITLE, obj.getString("title"))
            putExtra(EXTRA_BODY, obj.getString("body"))
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAt)
            if (type == ALARM_TYPE_CLASS) {
                putExtra(EXTRA_DAY_OF_WEEK, obj.optInt("dayOfWeek", 1))
                putExtra(EXTRA_START_MINUTE, obj.optInt("startMinute", 480))
                putExtra(EXTRA_LEAD_MINUTES, obj.optInt("leadMinutes", 15))
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied; falling back to inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun stableRequestCode(key: String): Int {
        return (key.hashCode() and 0x7FFFFFFF).coerceAtLeast(1000)
    }
}
