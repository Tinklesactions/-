package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AVAILABLE_CLASS_REMINDER_MINUTES
import com.example.data.AVAILABLE_EXAM_REMINDER_OFFSETS
import com.example.data.UserPreferences
import com.example.reminder.ReminderScheduler

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userEmail: String?,
    userDisplayName: String?,
    preferences: UserPreferences,
    scheduledAlarmCount: Int,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onUpdatePreferences: (UserPreferences) -> Unit,
    onSendTestNotification: () -> Unit,
    onResyncReminders: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val canExactAlarm = ReminderScheduler.canScheduleExactAlarms(context)

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
            item {
                Column {
                    Text(
                        text = "提醒与账号设置",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "管理上课提醒、考前自动通知节点与同步账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // System Notification Status Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasNotificationPermission) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.WarningAmber
                                },
                                contentDescription = null,
                                tint = if (hasNotificationPermission) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasNotificationPermission) {
                                        "系统通知权限已开启"
                                    } else {
                                        "尚未开启系统通知权限"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "当前已排程 $scheduledAlarmCount 个上课/考前自动提醒闹钟",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = onRequestNotificationPermission,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("立即授权")
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarm) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AlarmOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "精准闹钟权限（可选）",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "开启后可在手机休眠时按分钟级准点推送上课与考前通知",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        runCatching { context.startActivity(intent) }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("去设置")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onSendTestNotification,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("send_test_notification_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("测试发送通知")
                            }
                            OutlinedButton(
                                onClick = onResyncReminders,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resync_alarms_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("同步全部提醒")
                            }
                        }
                    }
                }
            }

            // Global Reminder Toggles & Default Lead Times
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "自动提醒总开关与默认规则",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("上课自动提醒", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "根据课程表星期与时间，每周自动课前通知",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.classRemindersEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdatePreferences(preferences.copy(classRemindersEnabled = enabled))
                                }
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "默认课前提前提醒时间",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AVAILABLE_CLASS_REMINDER_MINUTES.forEach { mins ->
                                    FilterChip(
                                        selected = preferences.defaultClassReminderMinutes == mins,
                                        onClick = {
                                            onUpdatePreferences(
                                                preferences.copy(defaultClassReminderMinutes = mins)
                                            )
                                        },
                                        label = { Text("提前${mins}分钟") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("考前自动提醒通知", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "在添加的考试前多个时间节点自动发送倒计时通知",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.examRemindersEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdatePreferences(preferences.copy(examRemindersEnabled = enabled))
                                }
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "新建考试默认考前提醒节点",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AVAILABLE_EXAM_REMINDER_OFFSETS.forEach { (offsetMin, label) ->
                                    val selected = preferences.defaultExamReminderOffsets.contains(offsetMin)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            val next = if (selected) {
                                                preferences.defaultExamReminderOffsets - offsetMin
                                            } else {
                                                (preferences.defaultExamReminderOffsets + offsetMin)
                                                    .distinct()
                                                    .sortedDescending()
                                            }
                                            onUpdatePreferences(
                                                preferences.copy(defaultExamReminderOffsets = next)
                                            )
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Account Profile & Sign Out
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userDisplayName?.takeIf { it.isNotBlank() } ?: "已登录用户",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = userEmail ?: "Google 云端同步已开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOutClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("退出 Google 账号")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
