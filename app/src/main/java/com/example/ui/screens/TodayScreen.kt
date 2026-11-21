package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Course
import com.example.data.Exam
import com.example.data.currentIsoDayOfWeek
import com.example.data.currentMinuteOfDay
import com.example.data.dayOfWeekFullLabel
import com.example.data.dayOfWeekShortLabel
import com.example.data.formatExamCountdownBadge
import com.example.data.formatExamShortDate
import com.example.data.formatMinuteOfDay
import com.example.data.formatTimeRange
import com.example.ui.theme.coursePaletteFor
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    coursesState: UiState<List<Course>>,
    examsState: UiState<List<Exam>>,
    scheduledAlarmCount: Int,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onImportImageClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    onAddExamClick: () -> Unit,
    onEditCourseClick: (Course) -> Unit,
    onToggleCourseReminder: (String, Boolean) -> Unit,
    onEditExamClick: (Exam) -> Unit,
    onNavigateToExams: () -> Unit,
    onNavigateToTimetable: () -> Unit
) {
    val todayDayOfWeek = remember { currentIsoDayOfWeek() }
    val nowMinute = currentMinuteOfDay()
    val nowMillis = System.currentTimeMillis()
    val todayDateStr = remember {
        SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date())
    }

    val courses = (coursesState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()

    val todayCourses = remember(courses, todayDayOfWeek) {
        courses.filter { it.dayOfWeek == todayDayOfWeek }
            .sortedBy { it.startMinuteOfDay }
    }
    val nextTodayCourse = remember(todayCourses, nowMinute) {
        todayCourses.firstOrNull { it.endMinuteOfDay > nowMinute }
    }
    val upcomingExams = remember(exams, nowMillis) {
        exams.filter { it.examEpochMillis > nowMillis }.sortedBy { it.examEpochMillis }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Editorial Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$todayDateStr · ${dayOfWeekFullLabel(todayDayOfWeek)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "今日日程",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${scheduledAlarmCount}个提醒运行中",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Notification Permission Prompt Banner (Android 13+)
            if (!hasNotificationPermission) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddAlert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "尚未开启系统通知权限",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "开启后才能准时收到上课和考前自动提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onRequestNotificationPermission,
                                modifier = Modifier.testTag("grant_notification_permission_button"),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("开启", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Next Up Spotlight Card
            item {
                NextUpSpotlightCard(
                    nextCourse = nextTodayCourse,
                    nextExam = upcomingExams.firstOrNull(),
                    nowMinute = nowMinute,
                    nowMillis = nowMillis,
                    onCourseClick = { nextTodayCourse?.let(onEditCourseClick) },
                    onExamClick = { upcomingExams.firstOrNull()?.let(onEditExamClick) }
                )
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onImportImageClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("today_import_image_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("AI导入课表图", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        onClick = onAddExamClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("today_add_exam_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("添加考试时间", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Today's Classes Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日课程（${todayCourses.size}节）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToTimetable) {
                        Text("查看全周课表")
                    }
                }
            }

            // Courses State
            when (coursesState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        ErrorBannerCard(message = coursesState.message)
                    }
                }

                is UiState.Success -> {
                    if (courses.isEmpty()) {
                        item {
                            EmptyTimetableHeroCard(
                                onImportClick = onImportImageClick,
                                onManualAddClick = onAddCourseClick
                            )
                        }
                    } else if (todayCourses.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "今天没有排课，好好复习或休息一下吧",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "本周共录入 ${courses.size} 节课程，点击右上角可查看完整周课表",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(todayCourses, key = { it.id }) { course ->
                            TodayCourseCard(
                                course = course,
                                nowMinute = nowMinute,
                                onClick = { onEditCourseClick(course) },
                                onToggleReminder = { enabled ->
                                    onToggleCourseReminder(course.id, enabled)
                                }
                            )
                        }
                    }
                }
            }

            // Upcoming Exams Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "近期考试倒计时（${upcomingExams.size}场）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToExams) {
                        Text("全部考试")
                    }
                }
            }

            if (upcomingExams.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddExamClick),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "还没有即将到来的考试",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "点击添加考试时间，系统将在考前多阶段自动推送提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(upcomingExams.take(3), key = { it.id }) { exam ->
                    CompactUpcomingExamCard(
                        exam = exam,
                        nowMillis = nowMillis,
                        onClick = { onEditExamClick(exam) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NextUpSpotlightCard(
    nextCourse: Course?,
    nextExam: Exam?,
    nowMinute: Int,
    nowMillis: Long,
    onCourseClick: () -> Unit,
    onExamClick: () -> Unit
) {
    val examWithin48h = nextExam != null && (nextExam.examEpochMillis - nowMillis) in 1..(48L * 3600L * 1000L)
    val showExam = nextCourse == null && nextExam != null || (examWithin48h && nextCourse == null)

    val gradientColors = if (showExam) {
        listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f))
    } else {
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.84f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = nextCourse != null || nextExam != null) {
                if (showExam) onExamClick() else onCourseClick()
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp)
        ) {
            when {
                nextCourse != null && !showExam -> {
                    val isOngoing = nowMinute in nextCourse.startMinuteOfDay until nextCourse.endMinuteOfDay
                    val minsUntil = (nextCourse.startMinuteOfDay - nowMinute).coerceAtLeast(0)
                    val statusBadge = when {
                        isOngoing -> "正在上课"
                        minsUntil < 60 -> "${minsUntil} 分钟后上课"
                        else -> "${minsUntil / 60}小时${minsUntil % 60}分后上课"
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "下一节课",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = statusBadge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = nextCourse.courseName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = formatTimeRange(nextCourse.startMinuteOfDay, nextCourse.endMinuteOfDay),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            if (nextCourse.location.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = nextCourse.location,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                nextExam != null -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "最近一场考试",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = formatExamCountdownBadge(nextExam.examEpochMillis, nowMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = nextExam.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append(formatExamShortDate(nextExam.examEpochMillis))
                                if (nextExam.location.isNotBlank()) append(" · ${nextExam.location}")
                                if (nextExam.seatNumber.isNotBlank()) append("（${nextExam.seatNumber}）")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.92f)
                        )
                    }
                }

                else -> {
                    Column {
                        Text(
                            text = "今日日程清爽",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "导入你的课程表图片或添加考试时间，系统会自动为你排好所有课前与考前提醒。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCourseCard(
    course: Course,
    nowMinute: Int,
    onClick: () -> Unit,
    onToggleReminder: (Boolean) -> Unit
) {
    val dark = isSystemInDarkTheme()
    val palette = coursePaletteFor(course.colorIndex)
    val isPast = nowMinute >= course.endMinuteOfDay

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("today_course_card_${course.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color accent pill
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPast) MaterialTheme.colorScheme.outline else palette.accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.width(62.dp)) {
                Text(
                    text = formatMinuteOfDay(course.startMinuteOfDay),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatMinuteOfDay(course.endMinuteOfDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (course.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = course.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (course.teacher.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = course.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (course.reminderEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "课前 ${course.reminderMinutesBefore} 分钟提醒",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) palette.darkOnContainer else palette.accent
                    )
                }
            }
            IconButton(
                onClick = { onToggleReminder(!course.reminderEnabled) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (course.reminderEnabled) {
                        Icons.Rounded.NotificationsActive
                    } else {
                        Icons.Rounded.NotificationsOff
                    },
                    contentDescription = if (course.reminderEnabled) "关闭该课程提醒" else "开启该课程提醒",
                    tint = if (course.reminderEnabled) palette.accent else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun CompactUpcomingExamCard(
    exam: Exam,
    nowMillis: Long,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = formatExamCountdownBadge(exam.examEpochMillis, nowMillis),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exam.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(formatExamShortDate(exam.examEpochMillis))
                        if (exam.location.isNotBlank()) append(" · ${exam.location}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (exam.reminderEnabled && exam.reminderOffsetsMinutes.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = "已开启考前提醒",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyTimetableHeroCard(
    onImportClick: () -> Unit,
    onManualAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_schedule_1795240700498),
                contentDescription = "导入课程表图片插画",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "还没有导入课程表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "从相册选择教务系统课表截图或课程表照片，AI 将自动识别所有课程与时间并开启上课提醒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_import_timetable_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("选择课表图片")
                }
                FilledTonalButton(
                    onClick = onManualAddClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_add_course_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("手动添加课程")
                }
            }
        }
    }
}

@Composable
fun ErrorBannerCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}
