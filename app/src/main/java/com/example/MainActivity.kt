package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.TimetableImageParser
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.Course
import com.example.data.Exam
import com.example.data.ScheduleRepository
import com.example.data.currentIsoDayOfWeek
import com.example.firebase.configureAppCheck
import com.example.reminder.ReminderScheduler
import com.example.ui.components.CourseEditorSheet
import com.example.ui.components.ExamEditorSheet
import com.example.ui.components.TimetableImportSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExamsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimetableScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.CourseReminderTheme
import com.example.viewmodel.ImportUiState
import com.example.viewmodel.ScheduleViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TODAY("今日", Icons.Rounded.Today, Icons.Outlined.Today, "nav_today"),
    TIMETABLE("课表", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_timetable"),
    EXAMS("考试", Icons.Rounded.EventNote, Icons.Outlined.EventNote, "nav_exams"),
    SETTINGS("提醒", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        ReminderScheduler.ensureNotificationChannels(this)

        setContent {
            CourseReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow reacts automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedMainShell(
                currentUserId = user.uid,
                user = user
            )
        }
    }
}

@Composable
private fun AuthenticatedMainShell(
    currentUserId: String,
    user: FirebaseUser
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ScheduleViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ScheduleViewModel(
                    repository = ScheduleRepository(db),
                    parser = TimetableImageParser(),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val coursesState by viewModel.coursesState.collectAsStateWithLifecycle()
    val examsState by viewModel.examsState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val scheduledAlarmCount by viewModel.scheduledAlarmCount.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by remember { mutableStateOf(AppTab.TODAY) }

    // Course / Exam modal editors
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var showCourseEditor by remember { mutableStateOf(false) }
    var courseEditorDefaultDay by remember { mutableStateOf(currentIsoDayOfWeek()) }

    var editingExam by remember { mutableStateOf<Exam?>(null) }
    var showExamEditor by remember { mutableStateOf(false) }

    // Notification permission state (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(ReminderScheduler.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || ReminderScheduler.hasNotificationPermission(context)
        if (granted) {
            viewModel.forceResyncReminders()
        } else {
            viewModel.showFeedback("未授予通知权限，可在系统设置中随时开启")
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
        }
    }

    // Re-check permission on resume
    DisposableEffect(Unit) {
        hasNotificationPermission = ReminderScheduler.hasNotificationPermission(context)
        onDispose {}
    }

    // Zero-permission Android Photo Picker for importing timetable image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.analyzeTimetableImage(uri)
        }
    }

    val launchTimetablePhotoPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeFeedback()
    }

    BackHandler(enabled = activeTab != AppTab.TODAY) {
        activeTab = AppTab.TODAY
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        AppTab.TODAY -> TodayScreen(
                            coursesState = coursesState,
                            examsState = examsState,
                            scheduledAlarmCount = scheduledAlarmCount,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = requestNotificationPermission,
                            onImportImageClick = launchTimetablePhotoPicker,
                            onAddCourseClick = {
                                editingCourse = null
                                courseEditorDefaultDay = currentIsoDayOfWeek()
                                showCourseEditor = true
                            },
                            onAddExamClick = {
                                editingExam = null
                                showExamEditor = true
                            },
                            onEditCourseClick = { course ->
                                editingCourse = course
                                showCourseEditor = true
                            },
                            onToggleCourseReminder = viewModel::toggleCourseReminder,
                            onEditExamClick = { exam ->
                                editingExam = exam
                                showExamEditor = true
                            },
                            onNavigateToExams = { activeTab = AppTab.EXAMS },
                            onNavigateToTimetable = { activeTab = AppTab.TIMETABLE }
                        )

                        AppTab.TIMETABLE -> TimetableScreen(
                            coursesState = coursesState,
                            onImportImageClick = launchTimetablePhotoPicker,
                            onAddCourseClick = { day ->
                                editingCourse = null
                                courseEditorDefaultDay = day
                                showCourseEditor = true
                            },
                            onEditCourseClick = { course ->
                                editingCourse = course
                                showCourseEditor = true
                            },
                            onDeleteCourseClick = viewModel::deleteCourse,
                            onToggleCourseReminder = viewModel::toggleCourseReminder
                        )

                        AppTab.EXAMS -> ExamsScreen(
                            examsState = examsState,
                            onAddExamClick = {
                                editingExam = null
                                showExamEditor = true
                            },
                            onEditExamClick = { exam ->
                                editingExam = exam
                                showExamEditor = true
                            },
                            onDeleteExamClick = viewModel::deleteExam,
                            onToggleExamReminder = viewModel::toggleExamReminder
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            userEmail = user.email,
                            userDisplayName = user.displayName,
                            preferences = preferences,
                            scheduledAlarmCount = scheduledAlarmCount,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = requestNotificationPermission,
                            onUpdatePreferences = viewModel::updatePreferences,
                            onSendTestNotification = {
                                if (!hasNotificationPermission) {
                                    requestNotificationPermission()
                                } else {
                                    viewModel.sendTestNotification()
                                }
                            },
                            onResyncReminders = viewModel::forceResyncReminders,
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = { viewModel.clearLocalAlarmsOnSignOut() }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // AI Timetable Import Bottom Sheet
    TimetableImportSheet(
        importState = importState,
        onDismiss = viewModel::dismissImportFlow,
        onPickAnotherImage = launchTimetablePhotoPicker,
        onRetryAnalyze = {
            val uri = (importState as? ImportUiState.Error)?.imageUri
            if (uri != null) {
                viewModel.analyzeTimetableImage(uri)
            }
        },
        onToggleDraft = viewModel::toggleDraftSelection,
        onRemoveDraft = viewModel::removeDraftCourse,
        onLeadMinutesChange = viewModel::updateImportLeadMinutes,
        onConfirmImport = viewModel::confirmImportDrafts
    )

    // Manual Add / Edit Course Sheet
    if (showCourseEditor) {
        CourseEditorSheet(
            initialCourse = editingCourse,
            defaultDayOfWeek = courseEditorDefaultDay,
            defaultReminderMinutes = preferences.defaultClassReminderMinutes,
            onDismiss = { showCourseEditor = false },
            onSave = viewModel::saveCourse,
            onDelete = { id -> viewModel.deleteCourse(id) }
        )
    }

    // Add / Edit Exam Sheet
    if (showExamEditor) {
        val existingCourseNames = (coursesState as? UiState.Success)?.data
            ?.map { it.courseName }
            .orEmpty()
        ExamEditorSheet(
            initialExam = editingExam,
            existingCourseNames = existingCourseNames,
            defaultExamOffsets = preferences.defaultExamReminderOffsets,
            onDismiss = { showExamEditor = false },
            onSave = viewModel::saveExam,
            onDelete = { id -> viewModel.deleteExam(id) }
        )
    }
}
