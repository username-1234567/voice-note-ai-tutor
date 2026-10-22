package com.suitanglu.app.recording

import android.content.Context
import com.suitanglu.app.data.CourseSchedule
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.data.SettingsRepository
import com.suitanglu.app.data.db.AppDatabase
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerImage
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    val recordingsDir: File get() = File(context.filesDir, "recordings").apply { mkdirs() }
    val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    private val _recoveredCount = MutableStateFlow<Int?>(null)
    val recoveredCount: StateFlow<Int?> = _recoveredCount

    fun notifyRecovered(count: Int) {
        _recoveredCount.value = count
    }

    fun clearRecovered() {
        _recoveredCount.value = null
    }

    fun uniqueBaseName(base: String, ext: String, dir: File): String {
        var name = base
        var i = 2
        while (File(dir, "$name.$ext").exists()) {
            name = "${base}_$i"
            i++
        }
        return name
    }

    /** 开始录音：匹配课程表，创建数据库记录和文件 */
    suspend fun startRecording(startTime: Long): Recording {
        val (courseName, slotIndex) = CourseSchedule.resolveCourseName(
            startTime, null,
            slotsOf = { day -> settings.slotsNow(day) },
        ) { day, slot ->
            settings.courseNameNow(day, slot)
        }
        val title = CourseSchedule.displayName(courseName, null)
        val base = CourseSchedule.baseFileName(title, startTime)
        val name = uniqueBaseName(base, "m4a", recordingsDir)
        val file = File(recordingsDir, "$name.m4a")
        val rec = Recording(
            title = title,
            courseName = courseName,
            courseSlotId = slotIndex,
            filePath = file.absolutePath,
            startTime = startTime,
            status = Recording.STATUS_RECORDING,
        )
        val id = db.recordingDao().insert(rec)
        return rec.copy(id = id)
    }

    /** 录音启动失败：删除预占的记录与残留文件，避免幽灵录音和 0 字节孤儿文件 */
    suspend fun discardRecording(recordingId: Long) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        File(rec.filePath).takeIf { it.exists() }?.delete()
        db.recordingDao().delete(recordingId)
    }

    /** 结束录音：跨段重判课程名、必要时重命名文件、更新状态 */
    suspend fun finalizeRecording(recordingId: Long, endTime: Long, durationMs: Long, interrupted: Boolean) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        val (courseName, slotIndex) = CourseSchedule.resolveCourseName(
            rec.startTime, endTime,
            slotsOf = { day -> settings.slotsNow(day) },
        ) { day, slot ->
            settings.courseNameNow(day, slot)
        }
        val title = CourseSchedule.displayName(courseName, null)
        var path = rec.filePath
        val oldFile = File(rec.filePath)
        if (title != rec.title && oldFile.exists()) {
            val base = CourseSchedule.baseFileName(title, rec.startTime)
            val name = uniqueBaseName(base, "m4a", recordingsDir)
            val target = File(recordingsDir, "$name.m4a")
            if (oldFile.renameTo(target)) path = target.absolutePath
        }
        db.recordingDao().update(
            rec.copy(
                title = title,
                courseName = courseName,
                courseSlotId = slotIndex,
                filePath = path,
                endTime = endTime,
                durationMs = durationMs,
                status = if (interrupted) Recording.STATUS_INTERRUPTED else Recording.STATUS_COMPLETED,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        // 后台计算文件 Hash
        val file = File(path)
        if (file.exists()) {
            withContext(Dispatchers.IO) {
                val hash = HashUtil.sha256(file)
                db.recordingDao().updateHash(recordingId, hash, System.currentTimeMillis())
            }
        }
    }

    /** 手动重命名展示名，实际文件同步重命名并保留开始时间戳 */
    suspend fun rename(recordingId: Long, newTitle: String): Boolean {
        val rec = db.recordingDao().getById(recordingId) ?: return false
        val cleaned = CourseSchedule.sanitize(newTitle.trim())
        if (cleaned.isEmpty()) return false
        var path = rec.filePath
        val oldFile = File(rec.filePath)
        if (oldFile.exists()) {
            val base = CourseSchedule.baseFileName(cleaned, rec.startTime)
            val name = uniqueBaseName(base, "m4a", recordingsDir)
            val target = File(recordingsDir, "$name.m4a")
            if (oldFile.renameTo(target)) path = target.absolutePath
        }
        db.recordingDao().update(
            rec.copy(title = cleaned, filePath = path, updatedAt = System.currentTimeMillis()),
        )
        return true
    }

    suspend fun deleteToRecycle(recordingId: Long) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        db.recordingDao().update(rec.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun restore(recordingId: Long) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        db.recordingDao().update(rec.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
    }

    /** 彻底删除：音频、图片、标记、AI 请求日志等全部删除 */
    suspend fun deleteForever(recordingId: Long) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        File(rec.filePath).takeIf { it.exists() }?.delete()
        val markers = db.markerDao().getByRecording(recordingId)
        for (m in markers) {
            for (img in db.markerImageDao().getByMarker(m.id)) {
                File(img.filePath).takeIf { it.exists() }?.delete()
            }
            db.markerImageDao().deleteByMarker(m.id)
            // 多轮追问引用 markerId，不删就会留下一堆指向不存在标记的孤儿行
            db.markerTurnDao().deleteByMarker(m.id)
        }
        // 日志表引用标记 id，需在删除标记前清理，否则成为孤儿数据无限累积
        db.aiRequestLogDao().deleteForRecording(recordingId)
        db.markerDao().deleteByRecording(recordingId)
        db.pauseEventDao().deleteByRecording(recordingId)
        // 整节汇总和整节对话按 recordingId 关联，同样要清干净——
        // 用户点"彻底删除"时期望的是这条录音的一切都没了
        db.recordingDigestDao().delete(recordingId)
        db.recordingChatDao().clear(recordingId)
        db.recordingDao().delete(recordingId)
    }

    /** 仅删除录音文件，保留标记、图片、AI 回复 */
    suspend fun deleteAudioOnly(recordingId: Long) {
        val rec = db.recordingDao().getById(recordingId) ?: return
        File(rec.filePath).takeIf { it.exists() }?.delete()
        db.recordingDao().update(
            rec.copy(
                status = Recording.STATUS_AUDIO_DELETED,
                filePath = "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveMarker(
        recordingId: Long,
        offsetMs: Long,
        userText: String?,
        imageFiles: List<File>,
    ): Long {
        val now = System.currentTimeMillis()
        val existing = db.markerDao().getByRecording(recordingId)
        val marker = Marker(
            recordingId = recordingId,
            offsetMs = offsetMs,
            absoluteTime = now,
            userText = userText?.takeIf { it.isNotBlank() },
            sendOrder = (existing.maxOfOrNull { it.sendOrder } ?: 0) + 1,
            createdAt = now,
            updatedAt = now,
        )
        val markerId = db.markerDao().insert(marker)
        val images = imageFiles.mapIndexed { i, f ->
            MarkerImage(
                markerId = markerId,
                filePath = f.absolutePath,
                orderIndex = i,
                createdAt = now,
            )
        }
        db.markerImageDao().insertAll(images)
        return markerId
    }

    suspend fun insertPauseEvent(recordingId: Long, offsetMs: Long, type: String, reason: String?) {
        db.pauseEventDao().insert(
            PauseEvent(recordingId = recordingId, offsetMs = offsetMs, type = type, reason = reason),
        )
    }

    suspend fun storageUsage(): Pair<Long, Long> {
        val recordings = recordingsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val images = imagesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return recordings to images
    }

    fun markerCountSummary(markers: List<Marker>): String {
        val success = markers.count { it.aiStatus == Marker.AI_SUCCESS }
        val failed = markers.count { it.aiStatus == Marker.AI_FAILED }
        val pending = markers.count { it.aiStatus == Marker.AI_PENDING || it.aiStatus == Marker.AI_SENDING }
        return when {
            markers.isEmpty() -> "无标记"
            failed > 0 -> "${markers.size} 个标记，$success 成功 $failed 失败"
            pending > 0 -> "${markers.size} 个标记，$pending 待发送"
            else -> "${markers.size} 个标记"
        }
    }

    fun recordingSubtitle(rec: Recording, markers: List<Marker>): String {
        val parts = mutableListOf(
            "${Formatters.dateTime(rec.startTime)} · ${Formatters.duration(rec.durationMs)}",
        )
        if (rec.status == Recording.STATUS_AUDIO_DELETED) parts.add("音频已删除")
        else parts.add(markerCountSummary(markers))
        return parts.joinToString("\n")
    }
}
