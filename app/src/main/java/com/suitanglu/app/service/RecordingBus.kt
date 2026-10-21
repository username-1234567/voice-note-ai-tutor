package com.suitanglu.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 录音实时状态，前台服务与 UI 共享 */
data class RecordingState(
    val recordingId: Long,
    val title: String,
    val filePath: String,
    val startTime: Long,
    val elapsedMs: Long,
    val paused: Boolean,
    val interrupted: Boolean,
    val interruptReason: String? = null,
    /**
     * 当前输入电平 0..1（取自 MediaRecorder.maxAmplitude，每次读取会自动重置峰值）。
     * 只用于录音页的声纹环，不参与任何业务判断，取不到时保持 0。
     */
    val level: Float = 0f,
)

object RecordingBus {
    private val _state = MutableStateFlow<RecordingState?>(null)
    val state: StateFlow<RecordingState?> = _state

    private val _startError = MutableStateFlow<String?>(null)
    val startError: StateFlow<String?> = _startError

    fun update(state: RecordingState?) {
        _state.value = state
    }

    fun setStartError(message: String) {
        _startError.value = message
    }

    fun clearStartError() {
        _startError.value = null
    }
}
