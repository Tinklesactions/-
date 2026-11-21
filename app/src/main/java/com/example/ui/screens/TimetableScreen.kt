package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Course
import com.example.data.currentIsoDayOfWeek
import com.example.data.dayOfWeekShortLabel
import com.example.data.formatTimeRange
import com.example.ui.theme.coursePaletteFor
import com.example.viewmodel.UiState

@Composable
fun TimetableScreen(
    coursesState: UiState<List<Course>>,
    onImportImageClick: () -> Unit,
    onAddCourseClick: (Int) -> Unit,
    onEditCourseClick: (Course) -> Unit,
    onDeleteCourseClick: (String) -> Unit,
    onToggleCourseReminder: (String, Boolean) -> Unit
) {
    val today = remember { currentIsoDayOfWeek() }
    var isGridView by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableIntStateOf(today) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .widthIn(max = 840.dp)
                .padding(top = 14.dp)
        ) {
            // Header + AI Import Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "每周课程表",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val total = (coursesState as? UiState.Success)?.data?.size ?: 0
                    Text(
                        text = "共 $total 节课程 · 点击任意课程可编辑或设置提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onImportImageClick,
                    modifier = Modifier.testTag("timetable_import_image_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("导入图片", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            // View Mode Toggle (Weekly Grid vs Day List)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isGridView,
                        onClick = { isGridView = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarViewWeek,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text("周课表网格")
                    }
                    SegmentedButton(
                        selected = !isGridView,
                        onClick = { isGridView = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ViewAgenda,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text("按星期列表")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (coursesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ErrorBannerCard(message = coursesState.message)
                    }
                }

                is UiState.Success -> {
                    val courses = coursesState.data
                    if (courses.isEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyTimetableHeroCard(
                                onImportClick = onImportImageClick,
                                onManualAddClick = { onAddCourseClick(selectedDay) }
                            )
                        }
                    } else if (isGridView) {
                        WeeklyTimetableGrid(
                            courses = courses,
                            today = today,
                            onCourseClick = onEditCourseClick
                        )
                    } else {
                        DayCourseListView(
                            courses = courses,
                            selectedDay = selectedDay,
                            today = today,
                            onSelectDay = { selectedDay = it },
                            onEditCourse = onEditCourseClick,
                            onDeleteCourse = onDeleteCourseClick,
                            onToggleReminder = onToggleCourseReminder
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onAddCourseClick(selectedDay) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_course_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "手动添加课程"
            )
        }
    }
}

@Composable
private fun WeeklyTimetableGrid(
    courses: List<Course>,
    today: Int,
    onCourseClick: (Course) -> Unit
) {
    val dark = isSystemInDarkTheme()
    val hasWeekend = remember(courses) { courses.any { it.dayOfWeek >= 6 } }
    val days = if (hasWeekend) (1..7).toList() else (1..5).toList()

    val minStartHour = remember(courses) {
        val earliest = courses.minOfOrNull { it.startMinuteOfDay / 60 } ?: 8
        earliest.coerceIn(6, 8)
    }
    val maxEndHour = remember(courses) {
        val latest = courses.maxOfOrNull { (it.endMinuteOfDay + 59) / 60 } ?: 21
        latest.coerceIn(18, 23)
    }

    val hourHeightDp = 58.dp
    val timeAxisWidth = 44.dp
    val dayColMinWidth = if (days.size <= 5) 68.dp else 56.dp
    val horizontalScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Day Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(timeAxisWidth))
            days.forEach { day ->
                val isToday = day == today
                Box(
                    modifier = Modifier
                        .widthIn(min = dayColMinWidth)
                        .weight(1f, fill = false)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        }
                    ) {
                        Text(
                            text = dayOfWeekShortLabel(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Scrollable Grid Body
        val totalHours = (maxEndHour - minStartHour).coerceAtLeast(10)
        val totalGridHeight = hourHeightDp * totalHours

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(horizontalScroll)
                .padding(start = 12.dp, end = 12.dp, bottom = 88.dp)
        ) {
            // Time axis labels
            Column(modifier = Modifier.width(timeAxisWidth)) {
                for (h in minStartHour until maxEndHour) {
                    Box(
                        modifier = Modifier
                            .height(hourHeightDp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = String.format("%02d:00", h),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Day Columns with proportional course blocks
            days.forEach { day ->
                val dayCourses = courses.filter { it.dayOfWeek == day }
                Box(
                    modifier = Modifier
                        .width(dayColMinWidth)
                        .height(totalGridHeight)
                        .padding(horizontal = 2.dp)
                ) {
                    // Hour divider lines
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (h in minStartHour until maxEndHour) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(hourHeightDp)
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    thickness = 0.6.dp
                                )
                            }
                        }
                    }

                    // Course blocks
                    dayCourses.forEach { course ->
                        val startOffsetHours = (course.startMinuteOfDay - minStartHour * 60) / 60f
                        val durationHours = ((course.endMinuteOfDay - course.startMinuteOfDay).coerceAtLeast(35)) / 60f
                        val topOffset = hourHeightDp * startOffsetHours.coerceAtLeast(0f)
                        val blockHeight = (hourHeightDp * durationHours).coerceAtLeast(38.dp)
                        val palette = coursePaletteFor(course.colorIndex)

                        Box(
                            modifier = Modifier
                                .offset(y = topOffset)
                                .fillMaxWidth()
                                .height(blockHeight)
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (dark) palette.darkContainer else palette.lightContainer)
                                .border(
                                    width = 1.dp,
                                    color = palette.accent.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text(
                                    text = course.courseName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dark) palette.darkOnContainer else palette.lightOnContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp
                                )
                                if (course.location.isNotBlank() && blockHeight >= 52.dp) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "@${course.location}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dark) {
                                            palette.darkOnContainer.copy(alpha = 0.85f)
                                        } else {
                                            palette.lightOnContainer.copy(alpha = 0.82f)
                                        },
                                        fontSize = 9.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCourseListView(
    courses: List<Course>,
    selectedDay: Int,
    today: Int,
    onSelectDay: (Int) -> Unit,
    onEditCourse: (Course) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onToggleReminder: (String, Boolean) -> Unit
) {
    val dayCourses = remember(courses, selectedDay) {
        courses.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinuteOfDay }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 7-Day Selector Pill Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..7).forEach { day ->
                val isSelected = day == selectedDay
                val count = courses.count { it.dayOfWeek == day }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectDay(day) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    tonalElevation = if (isSelected) 0.dp else 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayOfWeekShortLabel(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (day == today) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.secondary
                                        )
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "${count}节",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (dayCourses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${dayOfWeekShortLabel(selectedDay)}暂无课程，点击右下角 + 号添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dayCourses, key = { it.id }) { course ->
                    val palette = coursePaletteFor(course.colorIndex)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditCourse(course) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.courseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatTimeRange(course.startMinuteOfDay, course.endMinuteOfDay) +
                                        if (course.weeksText.isNotBlank()) " · ${course.weeksText}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (course.location.isNotBlank() || course.teacher.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (course.location.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = palette.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = course.location,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (course.teacher.isNotBlank()) {
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = course.teacher,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (course.reminderEnabled) {
                                        "自动上课提醒：提前 ${course.reminderMinutesBefore} 分钟"
                                    } else {
                                        "提醒已关闭"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (course.reminderEnabled) palette.accent else MaterialTheme.colorScheme.outline
                                )
                            }

                            IconButton(
                                onClick = { onToggleReminder(course.id, !course.reminderEnabled) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (course.reminderEnabled) {
                                        Icons.Rounded.NotificationsActive
                                    } else {
                                        Icons.Rounded.NotificationsOff
                                    },
                                    contentDescription = "切换课程提醒",
                                    tint = if (course.reminderEnabled) palette.accent else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = { onDeleteCourse(course.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除课程",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
