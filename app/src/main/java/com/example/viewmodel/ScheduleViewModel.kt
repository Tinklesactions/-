package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TimetableImageParser
import com.example.data.Course
import com.example.data.DraftCourse
import com.example.data.Exam
import com.example.data.ScheduleRepository
import com.example.data.UserPreferences
import com.example.reminder.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ScheduleViewModel"
private const val STOP_TIMEOUT_MILLIS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Analyzing(val imageUri: Uri) : ImportUiState
    data class Preview(
        val imageUri: Uri,
        val summary: String,
        val drafts: List<DraftCourse>,
        val leadMinutes: Int,
        val isSaving: Boolean = false
    ) : ImportUiState
    data class Error(val imageUri: Uri?, val message: String) : ImportUiState
}

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val parser: TimetableImageParser,
    private val currentUserId: String,
    private val appContext: Context
) : ViewModel() {

    val coursesState: StateFlow<UiState<List<Course>>> = repository.observeCourses(currentUserId)
        .map<List<Course>, UiState<List<Course>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing courses", rootError)
            emit(UiState.Error(rootError.message ?: "加载课程表失败，请检查网络"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val examsState: StateFlow<UiState<List<Exam>>> = repository.observeExams(currentUserId)
        .map<List<Exam>, UiState<List<Exam>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing exams", rootError)
            emit(UiState.Error(rootError.message ?: "加载考试安排失败，请检查网络"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val preferencesState: StateFlow<UserPreferences> = repository.observePreferences(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing preferences", firestoreError ?: error)
            emit(UserPreferences(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UserPreferences(userId = currentUserId)
        )

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _scheduledAlarmCount = MutableStateFlow(0)
    val scheduledAlarmCount: StateFlow<Int> = _scheduledAlarmCount.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        // Automatically keep local Android AlarmManager notifications in sync with cloud data
        viewModelScope.launch {
            combine(coursesState, examsState, preferencesState) { courses, exams, prefs ->
                Triple(courses, exams, prefs)
            }.collect { (courses, exams, prefs) ->
                if (courses is UiState.Success && exams is UiState.Success) {
                    val count = ReminderScheduler.syncReminders(
                        context = appContext,
                        courses = courses.data,
                        exams = exams.data,
                        prefs = prefs
                    )
                    _scheduledAlarmCount.value = count
                }
            }
        }
    }

    fun consumeFeedback() {
        _feedbackMessage.value = null
    }

    fun showFeedback(message: String) {
        _feedbackMessage.value = message
    }

    // --- AI Timetable Image Import ---

    fun analyzeTimetableImage(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState.Analyzing(uri)
            try {
                val result = parser.parseFromUri(appContext, uri)
                if (result.courses.isEmpty()) {
                    _importState.value = ImportUiState.Error(
                        imageUri = uri,
                        message = result.summary.ifBlank { "未在图片中识别到有效课程，请上传更清晰的课程表截图或照片。" }
                    )
                } else {
                    _importState.value = ImportUiState.Preview(
                        imageUri = uri,
                        summary = result.summary,
                        drafts = result.courses,
                        leadMinutes = preferencesState.value.defaultClassReminderMinutes
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI timetable import failed", e)
                _importState.value = ImportUiState.Error(
                    imageUri = uri,
                    message = e.localizedMessage ?: "识别课表图片时出现错误，请稍后重试。"
                )
            }
        }
    }

    fun toggleDraftSelection(tempId: String) {
        val current = _importState.value as? ImportUiState.Preview ?: return
        _importState.value = current.copy(
            drafts = current.drafts.map {
                if (it.tempId == tempId) it.copy(selected = !it.selected) else it
            }
        )
    }

    fun updateDraftCourse(updated: DraftCourse) {
        val current = _importState.value as? ImportUiState.Preview ?: return
        _importState.value = current.copy(
            drafts = current.drafts.map {
                if (it.tempId == updated.tempId) updated else it
            }
        )
    }

    fun removeDraftCourse(tempId: String) {
        val current = _importState.value as? ImportUiState.Preview ?: return
        _importState.value = current.copy(
            drafts = current.drafts.filterNot { it.tempId == tempId }
        )
    }

    fun updateImportLeadMinutes(minutes: Int) {
        val current = _importState.value as? ImportUiState.Preview ?: return
        _importState.value = current.copy(leadMinutes = minutes.coerceIn(0, 180))
    }

    fun confirmImportDrafts() {
        val current = _importState.value as? ImportUiState.Preview ?: return
        if (current.isSaving) return
        val selectedCount = current.drafts.count { it.selected }
        if (selectedCount == 0) {
            _feedbackMessage.value = "请至少勾选一门课程再导入"
            return
        }
        viewModelScope.launch {
            _importState.value = current.copy(isSaving = true)
            val result = repository.addCoursesBatch(
                userId = currentUserId,
                drafts = current.drafts,
                reminderMinutesBefore = current.leadMinutes,
                reminderEnabled = true
            )
            result.onSuccess { count ->
                _importState.value = ImportUiState.Idle
                _feedbackMessage.value = "已成功导入 $count 节课程并开启自动上课提醒"
            }.onFailure { error ->
                _importState.value = current.copy(isSaving = false)
                _feedbackMessage.value = "导入失败：${error.localizedMessage ?: "请检查网络"}"
            }
        }
    }

    fun dismissImportFlow() {
        _importState.value = ImportUiState.Idle
    }

    // --- Course CRUD ---

    fun saveCourse(course: Course) {
        viewModelScope.launch {
            val res = if (course.id.isBlank()) {
                repository.addCourse(currentUserId, course).map { Unit }
            } else {
                repository.updateCourse(currentUserId, course)
            }
            res.onSuccess {
                _feedbackMessage.value = if (course.id.isBlank()) "已添加课程并安排提醒" else "课程已更新"
            }.onFailure { error ->
                _feedbackMessage.value = "保存课程失败：${error.localizedMessage ?: "请重试"}"
            }
        }
    }

    fun toggleCourseReminder(courseId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleCourseReminder(currentUserId, courseId, enabled)
                .onSuccess {
                    _feedbackMessage.value = if (enabled) "已开启该课程上课提醒" else "已暂停该课程提醒"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "更新提醒状态失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repository.deleteCourse(currentUserId, courseId)
                .onSuccess {
                    _feedbackMessage.value = "已删除该课程"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "删除课程失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    // --- Exam CRUD ---

    fun saveExam(exam: Exam) {
        viewModelScope.launch {
            val res = if (exam.id.isBlank()) {
                repository.addExam(currentUserId, exam).map { Unit }
            } else {
                repository.updateExam(currentUserId, exam)
            }
            res.onSuccess {
                _feedbackMessage.value = if (exam.id.isBlank()) {
                    "已添加考试并开启考前自动通知提醒"
                } else {
                    "考试日程与考前提醒已更新"
                }
            }.onFailure { error ->
                _feedbackMessage.value = "保存考试失败：${error.localizedMessage ?: "请重试"}"
            }
        }
    }

    fun toggleExamReminder(examId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleExamReminder(currentUserId, examId, enabled)
                .onSuccess {
                    _feedbackMessage.value = if (enabled) "已开启考前自动提醒" else "已暂停该考试提醒"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "更新考试提醒失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExam(currentUserId, examId)
                .onSuccess {
                    _feedbackMessage.value = "已删除考试日程"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "删除考试失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    // --- Preferences & Manual Sync ---

    fun updatePreferences(updated: UserPreferences) {
        viewModelScope.launch {
            repository.savePreferences(currentUserId, updated)
                .onSuccess {
                    _feedbackMessage.value = "提醒设置已保存并同步"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "保存设置失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun forceResyncReminders() {
        val courses = (coursesState.value as? UiState.Success)?.data.orEmpty()
        val exams = (examsState.value as? UiState.Success)?.data.orEmpty()
        val count = ReminderScheduler.syncReminders(
            context = appContext,
            courses = courses,
            exams = exams,
            prefs = preferencesState.value
        )
        _scheduledAlarmCount.value = count
        _feedbackMessage.value = "已重新同步 $count 个上课与考前提醒任务"
    }

    fun sendTestNotification() {
        val sent = ReminderScheduler.sendTestNotification(appContext)
        _feedbackMessage.value = if (sent) {
            "测试通知已发送，请在系统通知栏查看"
        } else {
            "请先授予系统通知权限后再发送测试通知"
        }
    }

    fun clearLocalAlarmsOnSignOut() {
        ReminderScheduler.clearAllReminders(appContext)
    }
}
