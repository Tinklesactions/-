package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Keep
@IgnoreExtraProperties
data class Course(
    @DocumentId val id: String = "",
    val userId: String = "",
    val courseName: String = "",
    val dayOfWeek: Int = 1, // 1 = Monday .. 7 = Sunday
    val startMinuteOfDay: Int = 480, // 08:00
    val endMinuteOfDay: Int = 575,   // 09:35
    val location: String = "",
    val teacher: String = "",
    val weeksText: String = "",
    val colorIndex: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 15,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Exam(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val courseName: String = "",
    val examEpochMillis: Long = 0L,
    val durationMinutes: Int = 120,
    val location: String = "",
    val seatNumber: String = "",
    val notes: String = "",
    val reminderEnabled: Boolean = true,
    val reminderOffsetsMinutes: List<Long> = DEFAULT_EXAM_REMINDER_OFFSETS,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "settings",
    val userId: String = "",
    val defaultClassReminderMinutes: Int = 15,
    val defaultExamReminderOffsets: List<Long> = DEFAULT_EXAM_REMINDER_OFFSETS,
    val classRemindersEnabled: Boolean = true,
    val examRemindersEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DraftCourse(
    val tempId: String,
    val courseName: String,
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val location: String,
    val teacher: String,
    val weeksText: String,
    val colorIndex: Int,
    val selected: Boolean = true
)

val DEFAULT_EXAM_REMINDER_OFFSETS: List<Long> = listOf(4320L, 1440L, 120L, 30L)

val AVAILABLE_EXAM_REMINDER_OFFSETS: List<Pair<Long, String>> = listOf(
    10080L to "考前7天",
    4320L to "考前3天",
    1440L to "考前1天",
    120L to "考前2小时",
    60L to "考前1小时",
    30L to "考前30分钟"
)

val AVAILABLE_CLASS_REMINDER_MINUTES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60)

fun formatMinuteOfDay(minuteOfDay: Int): String {
    val safe = minuteOfDay.coerceIn(0, 1439)
    val h = safe / 60
    val m = safe % 60
    return String.format(Locale.getDefault(), "%02d:%02d", h, m)
}

fun formatTimeRange(startMinute: Int, endMinute: Int): String {
    return "${formatMinuteOfDay(startMinute)} - ${formatMinuteOfDay(endMinute)}"
}

fun dayOfWeekShortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周一"
}

fun dayOfWeekFullLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    7 -> "星期日"
    else -> "星期一"
}

fun currentIsoDayOfWeek(): Int {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}

fun currentMinuteOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

fun formatReminderOffsetLabel(offsetMinutes: Long): String = when {
    offsetMinutes >= 1440L && offsetMinutes % 1440L == 0L -> "考前${offsetMinutes / 1440L}天"
    offsetMinutes >= 60L && offsetMinutes % 60L == 0L -> "考前${offsetMinutes / 60L}小时"
    else -> "考前${offsetMinutes}分钟"
}

fun formatExamDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "未设置时间"
    val sdf = SimpleDateFormat("yyyy年MM月dd日 (EEE) HH:mm", Locale.CHINA)
    return sdf.format(Date(epochMillis))
}

fun formatExamShortDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "--"
    val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
    return sdf.format(Date(epochMillis))
}

fun formatExamCountdownBadge(examEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = examEpochMillis - nowMillis
    if (diff <= 0L) return "已结束"
    val totalMinutes = diff / 60_000L
    val totalHours = totalMinutes / 60L
    val days = totalHours / 24L
    return when {
        days >= 1L -> "还剩 ${days} 天"
        totalHours >= 1L -> "还剩 ${totalHours} 小时"
        else -> "还剩 ${totalMinutes.coerceAtLeast(1L)} 分钟"
    }
}

fun computeNextClassStartMillis(
    dayOfWeek: Int,
    startMinuteOfDay: Int,
    nowMillis: Long = System.currentTimeMillis()
): Long {
    val targetCalendarDay = when (dayOfWeek) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> Calendar.MONDAY
    }
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, (startMinuteOfDay / 60).coerceIn(0, 23))
        set(Calendar.MINUTE, (startMinuteOfDay % 60).coerceIn(0, 59))
    }
    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
    var daysUntil = (targetCalendarDay - currentDay + 7) % 7
    if (daysUntil == 0 && cal.timeInMillis <= nowMillis) {
        daysUntil = 7
    }
    cal.add(Calendar.DAY_OF_YEAR, daysUntil)
    return cal.timeInMillis
}

fun computeNextClassReminderMillis(
    dayOfWeek: Int,
    startMinuteOfDay: Int,
    reminderMinutesBefore: Int,
    nowMillis: Long = System.currentTimeMillis()
): Long {
    val leadMillis = reminderMinutesBefore.coerceIn(0, 180) * 60_000L
    var classStart = computeNextClassStartMillis(dayOfWeek, startMinuteOfDay, nowMillis)
    var triggerAt = classStart - leadMillis
    if (triggerAt <= nowMillis + 5_000L) {
        classStart += 7L * 24L * 60L * 60L * 1000L
        triggerAt = classStart - leadMillis
    }
    return triggerAt
}
