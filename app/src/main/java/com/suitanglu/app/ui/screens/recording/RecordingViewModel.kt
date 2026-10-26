package com.suitanglu.app.ui.screens.recording

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)

    val recordingState = RecordingBus.state

    val generalConfig: StateFlow<com.suitanglu.app.data.GeneralConfig> = container.settings.generalConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.suitanglu.app.data.GeneralConfig())

    private val _pendingImages = MutableStateFlow<List<File>>(emptyList())
    val pendingImages: StateFlow<List<File>> = _pendingImages

    private val _markerCount = MutableStateFlow(0)
    val markerCount: StateFlow<Int> = _markerCount

    val streaming = container.aiSendManager.streaming

    private val recId: Long get() = RecordingBus.state.value?.recordingId ?: 0L

    /** 录音途中回看：按时间排序的标记 + 首图缩略图 */
    suspend fun loadMarkerStrip(): List<Pair<Marker, String?>> {
        val id = recId
        if (id == 0L) return emptyList()
        return container.db.markerDao().getByRecording(id)
            .sortedBy { it.offsetMs }
            .map { m -> m to container.db.markerImageDao().getByMarker(m.id).firstOrNull()?.filePath }
    }

    init {
        viewModelScope.launch {
            // 只在录音 ID 变化时查库，避免跟随计时器每 500ms 查一次
            RecordingBus.state
                .map { it?.recordingId }
                .distinctUntilChanged()
                .collect { id ->
                    _markerCount.value = if (id != null) {
                        container.db.markerDao().countByRecording(id)
                    } else 0
                }
        }
    }

    /** 裁剪输出是 FileProvider 的 content:// Uri，统一复制到待用目录再使用 */
    fun addCroppedImage(uri: Uri) {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { copyToPending(uri) } ?: return@launch
            _pendingImages.value = _pendingImages.value + target
        }
    }

    /** 把 content:// 或 file:// 复制成缓存里的独立文件，供后续移动入库 */
    private fun copyToPending(uri: Uri): File? {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "pending").apply { mkdirs() }
        // 纳秒后缀：连续两次快拍可能落在同一毫秒里
        val f = File(dir, "P_${System.currentTimeMillis()}_${System.nanoTime() % 1000}.jpg")
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
        }
        return if (f.exists() && f.length() > 0) f else null
    }

    fun removePending(file: File) {
        _pendingImages.value = _pendingImages.value - file
        file.delete()
    }

    /**
     * 关掉标记面板但没保存：把暂存的图片扔掉。
     *
     * 不扔的话它们会一直躺在 `_pendingImages` 里，等下一次保存时跟着一起入库——
     * 用户会看到一张自己以为已经放弃的照片出现在新标记里。
     */
    fun discardPending() {
        _pendingImages.value.forEach { runCatching { it.delete() } }
        _pendingImages.value = emptyList()
    }

    /** 保存标记：图片移动到 images 目录，立即入库 */
    fun saveMarker(text: String, onDone: (Boolean) -> Unit) {
        val state = RecordingBus.state.value
        if (state == null) {
            onDone(false)
            return
        }
        val images = _pendingImages.value.filter { it.exists() }
        if (images.isEmpty()) {
            onDone(false)
            return
        }
        persistMarker(state.recordingId, state.elapsedMs, images, text, clearPending = true, onDone)
    }

    /**
     * 长按快拍：拍完直接入库，不裁剪、不写字。
     *
     * 刻意只保存自己这一张，不走 pendingImages——那条路会把"上次拍完没保存就关掉面板"的图
     * 一起卷进来，用户以为是新标记，其实带了一张旧的。
     */
    fun saveQuickMarker(uri: Uri, onDone: (Boolean) -> Unit) {
        val state = RecordingBus.state.value
        if (state == null) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { copyToPending(uri) } ?: run {
                onDone(false)
                return@launch
            }
            persistMarker(state.recordingId, state.elapsedMs, listOf(target), "", clearPending = false, onDone)
        }
    }

    /** 把缓存文件搬到 images 目录并落库；[clearPending] 决定是否清空面板里暂存的图片 */
    private fun persistMarker(
        recordingId: Long,
        offsetMs: Long,
        images: List<File>,
        text: String,
        clearPending: Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            // 文件可能在这期间被 discardPending 删掉（保存中关面板），
            // 不包起来的话异常会直接冒到 viewModelScope 上，标记创建静默失败
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val imagesDir = container.recordingRepository.imagesDir
                    val moved = images.map { f ->
                        val target = File(
                            imagesDir,
                            "marker_${recordingId}_${System.currentTimeMillis()}_${f.name.hashCode()}.jpg",
                        )
                        if (f.renameTo(target)) {
                            target
                        } else {
                            f.copyTo(target, overwrite = true)
                            f.delete()
                            target
                        }
                    }
                    container.recordingRepository.saveMarker(
                        recordingId = recordingId,
                        offsetMs = offsetMs,
                        userText = text.trim().takeIf { it.isNotEmpty() },
                        imageFiles = moved,
                    )
                }.isSuccess
            }
            if (ok) {
                if (clearPending) _pendingImages.value = emptyList()
                _markerCount.value += 1
            }
            onDone(ok)
        }
    }

    fun pauseOrResume() {
        val ctx = getApplication<Application>()
        val s = RecordingBus.state.value ?: return
        if (s.paused) RecordingService.sendAction(ctx, RecordingService.ACTION_RESUME)
        else RecordingService.sendAction(ctx, RecordingService.ACTION_PAUSE)
    }

    fun stop(onStopped: (Long) -> Unit) {
        val ctx = getApplication<Application>()
        val id = recId
        RecordingService.sendAction(ctx, RecordingService.ACTION_STOP)
        onStopped(id)
    }

    fun aiStatusSummary(): String {
        val count = _markerCount.value
        return when {
            count == 0 -> "尚无标记"
            else -> "$count 个标记，课后确认图片后到回放页手动发送"
        }
    }

    suspend fun markerStatusCounts(recordingId: Long): Triple<Int, Int, Int> {
        val ms = container.db.markerDao().getByRecording(recordingId)
        return Triple(
            ms.count { it.aiStatus == Marker.AI_SUCCESS },
            ms.count { it.aiStatus == Marker.AI_FAILED },
            ms.count { it.aiStatus == Marker.AI_PENDING || it.aiStatus == Marker.AI_SENDING },
        )
    }

    fun isSystemCamera(mode: String) = mode != CameraMode.IN_APP
}
