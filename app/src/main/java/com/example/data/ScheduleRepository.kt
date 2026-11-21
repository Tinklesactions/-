package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ScheduleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("请先使用 Google 账号登录")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("登录账号已变更，拒绝跨账号写入")
        }
        return uid
    }

    fun observeCourses(userId: String): Flow<List<Course>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/courses"
        emitAll(
            db.collection("users").document(uid).collection("courses")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Course::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<Course> { it.dayOfWeek }
                            .thenBy { it.startMinuteOfDay }
                            .thenBy { it.courseName }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeExams(userId: String): Flow<List<Exam>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/exams"
        emitAll(
            db.collection("users").document(uid).collection("exams")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Exam::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.examEpochMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePreferences(userId: String): Flow<UserPreferences> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/preferences/settings"
        emitAll(
            db.collection("users").document(uid)
                .collection("preferences").document("settings")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferences(userId = uid)
                    } else {
                        UserPreferences(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    private fun validateCourseFields(
        courseName: String,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        location: String,
        teacher: String,
        weeksText: String,
        colorIndex: Int,
        reminderMinutesBefore: Int
    ) {
        require(courseName.trim().length in 1..80) { "课程名称长度需在 1-80 字之间" }
        require(dayOfWeek in 1..7) { "星期必须在周一至周日之间" }
        require(startMinuteOfDay in 0..1439) { "上课时间无效" }
        require(endMinuteOfDay in 1..1440 && endMinuteOfDay > startMinuteOfDay) { "下课时间必须晚于上课时间" }
        require(location.trim().length <= 100) { "教室地点不能超过 100 字" }
        require(teacher.trim().length <= 60) { "教师姓名不能超过 60 字" }
        require(weeksText.trim().length <= 60) { "周次说明不能超过 60 字" }
        require(colorIndex in 0..7) { "颜色索引无效" }
        require(reminderMinutesBefore in 0..180) { "课前提醒时间需在 0-180 分钟之间" }
    }

    suspend fun addCourse(userId: String, course: Course): Result<String> {
        var docPath = "users/$userId/courses"
        return try {
            val uid = requireUserId(userId)
            val cleanName = course.courseName.trim().take(80)
            val cleanLocation = course.location.trim().take(100)
            val cleanTeacher = course.teacher.trim().take(60)
            val cleanWeeks = course.weeksText.trim().take(60)
            val startMin = course.startMinuteOfDay.coerceIn(0, 1438)
            val endMin = course.endMinuteOfDay.coerceIn(startMin + 1, 1440)
            val color = course.colorIndex.coerceIn(0, 7)
            val lead = course.reminderMinutesBefore.coerceIn(0, 180)

            validateCourseFields(
                cleanName, course.dayOfWeek, startMin, endMin,
                cleanLocation, cleanTeacher, cleanWeeks, color, lead
            )

            val docRef = db.collection("users").document(uid).collection("courses").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "courseName" to cleanName,
                "dayOfWeek" to course.dayOfWeek,
                "startMinuteOfDay" to startMin,
                "endMinuteOfDay" to endMin,
                "location" to cleanLocation,
                "teacher" to cleanTeacher,
                "weeksText" to cleanWeeks,
                "colorIndex" to color,
                "reminderEnabled" to course.reminderEnabled,
                "reminderMinutesBefore" to lead,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCoursesBatch(
        userId: String,
        drafts: List<DraftCourse>,
        reminderMinutesBefore: Int,
        reminderEnabled: Boolean = true
    ): Result<Int> {
        val collectionPath = "users/$userId/courses"
        return try {
            val uid = requireUserId(userId)
            val selectedDrafts = drafts.filter { it.selected && it.courseName.isNotBlank() }
            if (selectedDrafts.isEmpty()) return Result.success(0)

            val lead = reminderMinutesBefore.coerceIn(0, 180)
            // Chunk in batches of 400 to stay well within Firestore's 500 writes/batch limit
            selectedDrafts.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { draft ->
                    val cleanName = draft.courseName.trim().take(80)
                    val cleanLocation = draft.location.trim().take(100)
                    val cleanTeacher = draft.teacher.trim().take(60)
                    val cleanWeeks = draft.weeksText.trim().take(60)
                    val day = draft.dayOfWeek.coerceIn(1, 7)
                    val startMin = draft.startMinuteOfDay.coerceIn(0, 1438)
                    val endMin = draft.endMinuteOfDay.coerceIn(startMin + 1, 1440)
                    val color = draft.colorIndex.coerceIn(0, 7)

                    val docRef = db.collection("users").document(uid).collection("courses").document()
                    val payload = mapOf(
                        "userId" to uid,
                        "courseName" to cleanName,
                        "dayOfWeek" to day,
                        "startMinuteOfDay" to startMin,
                        "endMinuteOfDay" to endMin,
                        "location" to cleanLocation,
                        "teacher" to cleanTeacher,
                        "weeksText" to cleanWeeks,
                        "colorIndex" to color,
                        "reminderEnabled" to reminderEnabled,
                        "reminderMinutesBefore" to lead,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                batch.commit().await()
            }
            Result.success(selectedDrafts.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun updateCourse(userId: String, course: Course): Result<Unit> {
        var docPath = "users/$userId/courses/${course.id}"
        return try {
            val uid = requireUserId(userId)
            require(course.id.isNotBlank()) { "课程 ID 不能为空" }
            val cleanName = course.courseName.trim().take(80)
            val cleanLocation = course.location.trim().take(100)
            val cleanTeacher = course.teacher.trim().take(60)
            val cleanWeeks = course.weeksText.trim().take(60)
            val startMin = course.startMinuteOfDay.coerceIn(0, 1438)
            val endMin = course.endMinuteOfDay.coerceIn(startMin + 1, 1440)
            val color = course.colorIndex.coerceIn(0, 7)
            val lead = course.reminderMinutesBefore.coerceIn(0, 180)

            validateCourseFields(
                cleanName, course.dayOfWeek, startMin, endMin,
                cleanLocation, cleanTeacher, cleanWeeks, color, lead
            )

            val docRef = db.collection("users").document(uid)
                .collection("courses").document(course.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "courseName" to cleanName,
                    "dayOfWeek" to course.dayOfWeek,
                    "startMinuteOfDay" to startMin,
                    "endMinuteOfDay" to endMin,
                    "location" to cleanLocation,
                    "teacher" to cleanTeacher,
                    "weeksText" to cleanWeeks,
                    "colorIndex" to color,
                    "reminderEnabled" to course.reminderEnabled,
                    "reminderMinutesBefore" to lead,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleCourseReminder(
        userId: String,
        courseId: String,
        enabled: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/courses/$courseId"
        return try {
            val uid = requireUserId(userId)
            require(courseId.isNotBlank()) { "课程 ID 不能为空" }
            db.collection("users").document(uid)
                .collection("courses").document(courseId)
                .update(
                    mapOf(
                        "reminderEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCourse(userId: String, courseId: String): Result<Unit> {
        val docPath = "users/$userId/courses/$courseId"
        return try {
            val uid = requireUserId(userId)
            require(courseId.isNotBlank()) { "课程 ID 不能为空" }
            db.collection("users").document(uid)
                .collection("courses").document(courseId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun validateExamFields(
        title: String,
        courseName: String,
        examEpochMillis: Long,
        durationMinutes: Int,
        location: String,
        seatNumber: String,
        notes: String,
        offsets: List<Long>
    ) {
        require(title.trim().length in 1..100) { "考试名称长度需在 1-100 字之间" }
        require(courseName.trim().length <= 80) { "关联课程名称不能超过 80 字" }
        require(examEpochMillis > 0L) { "请选择有效的考试日期与时间" }
        require(durationMinutes in 15..600) { "考试时长需在 15-600 分钟之间" }
        require(location.trim().length <= 100) { "考场地点不能超过 100 字" }
        require(seatNumber.trim().length <= 40) { "座位号不能超过 40 字" }
        require(notes.trim().length <= 300) { "备注不能超过 300 字" }
        require(offsets.size <= 10) { "最多支持 10 个考前提醒节点" }
    }

    suspend fun addExam(userId: String, exam: Exam): Result<String> {
        var docPath = "users/$userId/exams"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = exam.title.trim().take(100)
            val cleanCourse = exam.courseName.trim().take(80)
            val cleanLocation = exam.location.trim().take(100)
            val cleanSeat = exam.seatNumber.trim().take(40)
            val cleanNotes = exam.notes.trim().take(300)
            val cleanDuration = exam.durationMinutes.coerceIn(15, 600)
            val cleanOffsets = exam.reminderOffsetsMinutes
                .filter { it > 0L }
                .distinct()
                .sortedDescending()
                .take(10)

            validateExamFields(
                cleanTitle, cleanCourse, exam.examEpochMillis, cleanDuration,
                cleanLocation, cleanSeat, cleanNotes, cleanOffsets
            )

            val docRef = db.collection("users").document(uid).collection("exams").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "courseName" to cleanCourse,
                "examEpochMillis" to exam.examEpochMillis,
                "durationMinutes" to cleanDuration,
                "location" to cleanLocation,
                "seatNumber" to cleanSeat,
                "notes" to cleanNotes,
                "reminderEnabled" to exam.reminderEnabled,
                "reminderOffsetsMinutes" to cleanOffsets,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateExam(userId: String, exam: Exam): Result<Unit> {
        val docPath = "users/$userId/exams/${exam.id}"
        return try {
            val uid = requireUserId(userId)
            require(exam.id.isNotBlank()) { "考试 ID 不能为空" }
            val cleanTitle = exam.title.trim().take(100)
            val cleanCourse = exam.courseName.trim().take(80)
            val cleanLocation = exam.location.trim().take(100)
            val cleanSeat = exam.seatNumber.trim().take(40)
            val cleanNotes = exam.notes.trim().take(300)
            val cleanDuration = exam.durationMinutes.coerceIn(15, 600)
            val cleanOffsets = exam.reminderOffsetsMinutes
                .filter { it > 0L }
                .distinct()
                .sortedDescending()
                .take(10)

            validateExamFields(
                cleanTitle, cleanCourse, exam.examEpochMillis, cleanDuration,
                cleanLocation, cleanSeat, cleanNotes, cleanOffsets
            )

            db.collection("users").document(uid)
                .collection("exams").document(exam.id)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "courseName" to cleanCourse,
                        "examEpochMillis" to exam.examEpochMillis,
                        "durationMinutes" to cleanDuration,
                        "location" to cleanLocation,
                        "seatNumber" to cleanSeat,
                        "notes" to cleanNotes,
                        "reminderEnabled" to exam.reminderEnabled,
                        "reminderOffsetsMinutes" to cleanOffsets,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleExamReminder(
        userId: String,
        examId: String,
        enabled: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/exams/$examId"
        return try {
            val uid = requireUserId(userId)
            require(examId.isNotBlank()) { "考试 ID 不能为空" }
            db.collection("users").document(uid)
                .collection("exams").document(examId)
                .update(
                    mapOf(
                        "reminderEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(userId: String, examId: String): Result<Unit> {
        val docPath = "users/$userId/exams/$examId"
        return try {
            val uid = requireUserId(userId)
            require(examId.isNotBlank()) { "考试 ID 不能为空" }
            db.collection("users").document(uid)
                .collection("exams").document(examId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun savePreferences(userId: String, prefs: UserPreferences): Result<Unit> {
        val docPath = "users/$userId/preferences/settings"
        return try {
            val uid = requireUserId(userId)
            val lead = prefs.defaultClassReminderMinutes.coerceIn(0, 180)
            val offsets = prefs.defaultExamReminderOffsets
                .filter { it > 0L }
                .distinct()
                .sortedDescending()
                .take(10)

            val docRef = db.collection("users").document(uid)
                .collection("preferences").document("settings")
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "defaultClassReminderMinutes" to lead,
                        "defaultExamReminderOffsets" to offsets,
                        "classRemindersEnabled" to prefs.classRemindersEnabled,
                        "examRemindersEnabled" to prefs.examRemindersEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "defaultClassReminderMinutes" to lead,
                        "defaultExamReminderOffsets" to offsets,
                        "classRemindersEnabled" to prefs.classRemindersEnabled,
                        "examRemindersEnabled" to prefs.examRemindersEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
