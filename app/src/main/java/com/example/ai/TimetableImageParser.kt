package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.DraftCourse
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "TimetableImageParser"

data class TimetableParseResult(
    val courses: List<DraftCourse>,
    val summary: String
)

class TimetableImageParser {

    private val courseItemSchema = Schema.obj(
        properties = mapOf(
            "courseName" to Schema.string(description = "Course title or subject name"),
            "dayOfWeek" to Schema.integer(description = "Day of week as integer: 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday"),
            "startHour" to Schema.integer(description = "Start hour in 24-hour format (0-23)"),
            "startMinute" to Schema.integer(description = "Start minute (0-59)"),
            "endHour" to Schema.integer(description = "End hour in 24-hour format (0-23)"),
            "endMinute" to Schema.integer(description = "End minute (0-59)"),
            "location" to Schema.string(description = "Classroom, building, or lab name; empty string if unavailable"),
            "teacher" to Schema.string(description = "Teacher or professor name; empty string if unavailable"),
            "weeksText" to Schema.string(description = "Weeks range such as 1-16周 or 单周/双周; empty string if unavailable")
        )
    )

    private val timetableResponseSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Short Chinese summary of the extracted schedule, e.g., 共识别到 12 节课程"),
            "courses" to Schema.array(
                items = courseItemSchema,
                description = "All individual class sessions recognized from the timetable image"
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = timetableResponseSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun parseFromUri(context: Context, imageUri: Uri): TimetableParseResult {
        val bitmap = withContext(Dispatchers.IO) {
            decodeScaledBitmap(context, imageUri, maxDimension = 1600)
        } ?: throw IllegalArgumentException("无法读取所选图片，请重新选择清晰的课程表图片。")

        val prompt = """
            你是一位精准的课程表图片识别助手。请仔细分析这张课程表图片，提取每一节课程的完整信息。
            规则要求：
            1. dayOfWeek 必须是 1 到 7 的整数（1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日）。
            2. 如果图片中仅标注了节次（如第1-2节、第3-4节）而未写具体时钟时间，请按中国高校常用作息时间合理换算：
               第1-2节: 08:00-09:40，第3-4节: 10:00-11:40，第5-6节: 14:00-15:40，第7-8节: 16:00-17:40，第9-10节: 19:00-20:40。
               若图片中已标明具体时间，严格使用图片中的实际起止时间！
            3. 如果同一门课在一周内出现多次，请拆分为多条记录，每条对应具体的星期与时间段。
            4. 尽量提取教室地点（location）、教师姓名（teacher）和周次信息（weeksText）；若图片未标注则填空字符串 ""。
            5. 如果图片不是课程表或未识别到任何课程，courses 返回空数组，并在 summary 中用中文说明原因。
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val jsonString = response.text
                ?: throw IllegalStateException("AI 返回了空结果，请重试。")

            parseJsonResponse(jsonString)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timetable parsing timed out", e)
            throw IllegalStateException("课表识别超时（60秒），请检查网络后重试。", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Timetable parsing failed", e)
            throw e
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun parseJsonResponse(jsonString: String): TimetableParseResult {
        val root = JSONObject(jsonString)
        val summary = root.optString("summary", "课表识别完成")
        val coursesArray = root.optJSONArray("courses")
        val drafts = mutableListOf<DraftCourse>()
        val colorMap = mutableMapOf<String, Int>()
        var nextColor = 0

        if (coursesArray != null) {
            for (i in 0 until coursesArray.length()) {
                val obj = coursesArray.optJSONObject(i) ?: continue
                val name = obj.optString("courseName", "").trim().take(80)
                if (name.isBlank()) continue

                val day = obj.optInt("dayOfWeek", 1).coerceIn(1, 7)
                val startHour = obj.optInt("startHour", 8).coerceIn(0, 23)
                val startMinute = obj.optInt("startMinute", 0).coerceIn(0, 59)
                val endHour = obj.optInt("endHour", (startHour + 1).coerceAtMost(23)).coerceIn(0, 23)
                val endMinute = obj.optInt("endMinute", 40).coerceIn(0, 59)

                val startTotal = (startHour * 60 + startMinute).coerceIn(0, 1420)
                val rawEndTotal = endHour * 60 + endMinute
                val endTotal = if (rawEndTotal > startTotal) {
                    rawEndTotal.coerceAtMost(1440)
                } else {
                    (startTotal + 90).coerceAtMost(1440)
                }

                val colorIdx = colorMap.getOrPut(name) {
                    val assigned = nextColor % 8
                    nextColor++
                    assigned
                }

                drafts.add(
                    DraftCourse(
                        tempId = UUID.randomUUID().toString(),
                        courseName = name,
                        dayOfWeek = day,
                        startMinuteOfDay = startTotal,
                        endMinuteOfDay = endTotal,
                        location = obj.optString("location", "").trim().take(100),
                        teacher = obj.optString("teacher", "").trim().take(60),
                        weeksText = obj.optString("weeksText", "").trim().take(60),
                        colorIndex = colorIdx,
                        selected = true
                    )
                )
            }
        }

        val sorted = drafts.sortedWith(
            compareBy<DraftCourse> { it.dayOfWeek }
                .thenBy { it.startMinuteOfDay }
                .thenBy { it.courseName }
        )
        return TimetableParseResult(courses = sorted, summary = summary)
    }

    private fun decodeScaledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDimension) return decoded

        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }
}
