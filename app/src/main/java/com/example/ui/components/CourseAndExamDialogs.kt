package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AVAILABLE_CLASS_REMINDER_MINUTES
import com.example.data.AVAILABLE_EXAM_REMINDER_OFFSETS
import com.example.data.Course
import com.example.data.Exam
import com.example.data.dayOfWeekShortLabel
import com.example.data.formatMinuteOfDay
import com.example.ui.theme.CoursePalettes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditorSheet(
    initialCourse: Course?,
    defaultDayOfWeek: Int,
    defaultReminderMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var courseName by remember(initialCourse) { mutableStateOf(initialCourse?.courseName ?: "") }
    var dayOfWeek by remember(initialCourse) { mutableIntStateOf(initialCourse?.dayOfWeek ?: defaultDayOfWeek) }
    var startMinute by remember(initialCourse) { mutableIntStateOf(initialCourse?.startMinuteOfDay ?: 480) }
    var endMinute by remember(initialCourse) { mutableIntStateOf(initialCourse?.endMinuteOfDay ?: 575) }
    var location by remember(initialCourse) { mutableStateOf(initialCourse?.location ?: "") }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher ?: "") }
    var weeksText by remember(initialCourse) { mutableStateOf(initialCourse?.weeksText ?: "1-16周") }
    var colorIndex by remember(initialCourse) { mutableIntStateOf(initialCourse?.colorIndex ?: 0) }
    var reminderEnabled by remember(initialCourse) { mutableStateOf(initialCourse?.reminderEnabled ?: true) }
    var reminderMinutes by remember(initialCourse) {
        mutableIntStateOf(initialCourse?.reminderMinutesBefore ?: defaultReminderMinutes)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    var pickingStartTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialCourse == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (initialCourse != null && initialCourse.id.isNotBlank() && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete(initialCourse.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }

            OutlinedTextField(
                value = courseName,
                onValueChange = {
                    courseName = it.take(80)
                    validationError = null
                },
                label = { Text("课程名称 *") },
                placeholder = { Text("例如：高等数学、大学英语") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("course_name_input")
            )

            // Day of Week Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("上课星期", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text(dayOfWeekShortLabel(day)) }
                        )
                    }
                }
            }

            // Start & End Time Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { pickingStartTime = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开始 ${formatMinuteOfDay(startMinute)}")
                }
                OutlinedButton(
                    onClick = { pickingEndTime = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("结束 ${formatMinuteOfDay(endMinute)}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(100) },
                    label = { Text("教室地点") },
                    placeholder = { Text("教三 204") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it.take(60) },
                    label = { Text("任课教师") },
                    placeholder = { Text("张教授") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = weeksText,
                onValueChange = { weeksText = it.take(60) },
                label = { Text("周次说明（可选）") },
                placeholder = { Text("例如：1-16周、单周") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Color Accent Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("课表颜色标记", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CoursePalettes.forEachIndexed { idx, palette ->
                        val selected = colorIndex == idx
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(palette.accent)
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorIndex = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选颜色",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reminder Toggle & Lead Minutes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启上课自动提醒", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "每周按时在课前发送系统通知提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            if (reminderEnabled) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AVAILABLE_CLASS_REMINDER_MINUTES.forEach { mins ->
                        FilterChip(
                            selected = reminderMinutes == mins,
                            onClick = { reminderMinutes = mins },
                            label = { Text("课前${mins}分") }
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val trimmed = courseName.trim()
                    if (trimmed.isEmpty()) {
                        validationError = "请输入课程名称"
                        return@Button
                    }
                    if (endMinute <= startMinute) {
                        validationError = "下课时间必须晚于上课时间"
                        return@Button
                    }
                    onSave(
                        Course(
                            id = initialCourse?.id ?: "",
                            userId = initialCourse?.userId ?: "",
                            courseName = trimmed,
                            dayOfWeek = dayOfWeek,
                            startMinuteOfDay = startMinute,
                            endMinuteOfDay = endMinute,
                            location = location.trim(),
                            teacher = teacher.trim(),
                            weeksText = weeksText.trim(),
                            colorIndex = colorIndex,
                            reminderEnabled = reminderEnabled,
                            reminderMinutesBefore = reminderMinutes
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_course_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (initialCourse == null) "保存课程并开启提醒" else "保存修改")
            }
        }
    }

    if (pickingStartTime) {
        TimePickerModalDialog(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            title = "选择上课时间",
            onDismiss = { pickingStartTime = false },
            onConfirm = { h, m ->
                val newStart = h * 60 + m
                startMinute = newStart
                if (endMinute <= newStart) {
                    endMinute = (newStart + 95).coerceAtMost(1435)
                }
                pickingStartTime = false
            }
        )
    }

    if (pickingEndTime) {
        TimePickerModalDialog(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            title = "选择下课时间",
            onDismiss = { pickingEndTime = false },
            onConfirm = { h, m ->
                endMinute = h * 60 + m
                pickingEndTime = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamEditorSheet(
    initialExam: Exam?,
    existingCourseNames: List<String>,
    defaultExamOffsets: List<Long>,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTimeMillis = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 3)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    var title by remember(initialExam) { mutableStateOf(initialExam?.title ?: "") }
    var courseName by remember(initialExam) { mutableStateOf(initialExam?.courseName ?: "") }
    var examMillis by remember(initialExam) {
        mutableLongStateOf(initialExam?.examEpochMillis?.takeIf { it > 0L } ?: defaultTimeMillis)
    }
    var durationMinutes by remember(initialExam) { mutableIntStateOf(initialExam?.durationMinutes ?: 120) }
    var location by remember(initialExam) { mutableStateOf(initialExam?.location ?: "") }
    var seatNumber by remember(initialExam) { mutableStateOf(initialExam?.seatNumber ?: "") }
    var notes by remember(initialExam) { mutableStateOf(initialExam?.notes ?: "") }
    var reminderEnabled by remember(initialExam) { mutableStateOf(initialExam?.reminderEnabled ?: true) }
    var selectedOffsets by remember(initialExam) {
        mutableStateOf(
            initialExam?.reminderOffsetsMinutes ?: defaultExamOffsets
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val examCal = remember(examMillis) {
        Calendar.getInstance().apply { timeInMillis = examMillis }
    }
    val dateText = remember(examMillis) {
        SimpleDateFormat("yyyy年MM月dd日 (EEE)", Locale.CHINA).format(Date(examMillis))
    }
    val timeText = remember(examMillis) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(examMillis))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialExam == null) "添加考试日程" else "编辑考试日程",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (initialExam != null && initialExam.id.isNotBlank() && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete(initialExam.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it.take(100)
                    validationError = null
                },
                label = { Text("考试名称 *") },
                placeholder = { Text("例如：高等数学期末考试") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exam_title_input")
            )

            // Quick pick from imported courses
            if (existingCourseNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "从已导入课表快速选择关联课程",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingCourseNames.distinct().take(8).forEach { name ->
                            FilterChip(
                                selected = courseName == name,
                                onClick = {
                                    courseName = name
                                    if (title.isBlank()) {
                                        title = "${name}考试"
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            // Date & Time Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("exam_date_picker_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dateText, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(0.9f)
                        .height(50.dp)
                        .testTag("exam_time_picker_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(timeText)
                }
            }

            // Duration Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("考试时长", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(60, 90, 120, 150, 180).forEach { dur ->
                        FilterChip(
                            selected = durationMinutes == dur,
                            onClick = { durationMinutes = dur },
                            label = { Text("${dur}分钟") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(100) },
                    label = { Text("考场地点") },
                    placeholder = { Text("主楼 301") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = seatNumber,
                    onValueChange = { seatNumber = it.take(40) },
                    label = { Text("座位号") },
                    placeholder = { Text("A-18") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(300) },
                label = { Text("考试备注（选填）") },
                placeholder = { Text("如：开卷、需带学生证与计算器") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Multi-stage Pre-Exam Auto Reminder Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启考前自动提醒通知", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "支持多阶段自动推送考前倒计时与考场提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            if (reminderEnabled) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AVAILABLE_EXAM_REMINDER_OFFSETS.forEach { (offsetMin, label) ->
                        val selected = selectedOffsets.contains(offsetMin)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedOffsets = if (selected) {
                                    selectedOffsets - offsetMin
                                } else {
                                    (selectedOffsets + offsetMin).distinct().sortedDescending()
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        validationError = "请输入考试名称"
                        return@Button
                    }
                    onSave(
                        Exam(
                            id = initialExam?.id ?: "",
                            userId = initialExam?.userId ?: "",
                            title = trimmed,
                            courseName = courseName.trim().ifBlank { trimmed }.take(80),
                            examEpochMillis = examMillis,
                            durationMinutes = durationMinutes,
                            location = location.trim(),
                            seatNumber = seatNumber.trim(),
                            notes = notes.trim(),
                            reminderEnabled = reminderEnabled,
                            reminderOffsetsMinutes = selectedOffsets
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_exam_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (initialExam == null) "保存考试并开启考前提醒" else "保存修改")
            }
        }
    }

    if (showDatePicker) {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                examCal.get(Calendar.YEAR),
                examCal.get(Calendar.MONTH),
                examCal.get(Calendar.DAY_OF_MONTH)
            )
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = utcCal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtc ->
                            val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtc
                            }
                            val updatedLocal = Calendar.getInstance().apply {
                                timeInMillis = examMillis
                                set(Calendar.YEAR, picked.get(Calendar.YEAR))
                                set(Calendar.MONTH, picked.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            examMillis = updatedLocal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerModalDialog(
            initialHour = examCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = examCal.get(Calendar.MINUTE),
            title = "选择开考时间",
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = examMillis
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                examMillis = updated.timeInMillis
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModalDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
