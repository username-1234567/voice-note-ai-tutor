package com.suitanglu.app.ui.screens.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.suitanglu.app.AppGraph
import com.suitanglu.app.ai.ChatMessage
import com.suitanglu.app.ai.DeepSeekClient
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerImage
import com.suitanglu.app.data.db.MarkerTurn
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.data.db.RecordingChat
import com.suitanglu.app.data.db.RecordingDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackViewModel(app: Application, val recordingId: Long) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording

    val markers: StateFlow<List<Marker>> = container.db.markerDao().observeByRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pauseEvents: StateFlow<List<PauseEvent>> = container.db.pauseEventDao().observeByRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaming = container.aiSendManager.streaming

    /** 当前模型：只有 DeepSeek 官方端点才展示花费，第三方兼容服务价格不可知 */
    val aiModel: StateFlow<String> = container.settings.aiConfig
        .map { it.model }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    private val _sendResult = MutableStateFlow<String?>(null)
    val sendResult: StateFlow<String?> = _sendResult

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError

    private var ticker: Job? = null

    /** 展开卡片时的图片查询缓存：回放期间图片不会变化 */
    private val imageCache = mutableMapOf<Long, List<MarkerImage>>()

    init {
        viewModelScope.launch {
            val rec = container.db.recordingDao().getById(recordingId)
            _recording.value = rec
            val file = rec?.filePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (file != null && file.exists()) {
                val p = ExoPlayer.Builder(getApplication()).build().apply {
                    setMediaItem(MediaItem.fromUri(file.toURI().toString()))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playing.value = isPlaying
                            if (isPlaying) startTicker() else tickOnce()
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            // prepare() 是异步的：构造完立刻读 duration 只会拿到 C.TIME_UNSET 折算出的 0。
                            // 不在这里补一刀，用户不点播放时进度条总时长就一直是录音表里的旧值。
                            if (playbackState == Player.STATE_READY) tickOnce()
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            // seek / 轨道切换后立即同步，否则 UI 位置要等下一个 200ms tick 才跟上
                            tickOnce()
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            // 进程被杀中断的录音未写 MP4 索引，可能无法播放；明确提示而不是静默无响应
                            _playerError.value = if (
                                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                            ) {
                                "音频文件不存在"
                            } else {
                                "文件可能已损坏（录音被系统中断时会产生此类文件）"
                            }
                        }
                    })
                }
                _player.value = p
                _duration.value = p.duration.coerceAtLeast(0)
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                tickOnce()
                delay(200)
            }
        }
    }

    private fun tickOnce() {
        val p = _player.value ?: return
        _position.value = p.currentPosition.coerceAtLeast(0)
        _duration.value = p.duration.coerceAtLeast(0)
    }

    fun togglePlay() {
        val p = _player.value ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    /** keepPlaying=null 沿用当前状态；true=跳转后继续播；false=跳转后暂停 */
    fun seekTo(ms: Long, keepPlaying: Boolean? = null) {
        val p = _player.value ?: return
        val max = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = ms.coerceIn(0, max)
        val wasPlaying = p.isPlaying
        p.seekTo(target)
        when (keepPlaying) {
            null -> if (wasPlaying && !p.isPlaying) p.play()
            true -> if (!p.isPlaying) p.play()
            false -> if (p.isPlaying) p.pause()
        }
        tickOnce()
    }

    fun skip(deltaMs: Long) {
        val p = _player.value ?: return
        seekTo(p.currentPosition + deltaMs)
    }

    fun setSpeed(v: Float) {
        _speed.value = v
        _player.value?.let { it.playbackParameters = it.playbackParameters.withSpeed(v) }
    }

    /** 点击标记：跳转并沿用点击前的播放状态 */
    fun jumpToMarker(marker: Marker) {
        seekTo(marker.offsetMs, keepPlaying = _player.value?.isPlaying)
    }

    /**
     * 某条标记的多轮对话。
     *
     * 按录音订阅，不是 observeAll()：那个会把全库轮次读进来，
     * 页面上的"本节课花费"于是变成全库总价，每条录音都显示同一个数。
     */
    val allTurns: StateFlow<List<MarkerTurn>> = container.db.markerTurnDao()
        .observeTurnsByRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 发送操作跑在 appScope：离开页面取消 viewModelScope 不再打断生成；
    // 结果仍写回本 VM 状态供当前页面展示
    fun resendFailed() {
        container.appScope.launch {
            _sendResult.value = container.aiSendManager.sendQueue(recordingId, onlyFailed = true).message()
        }
    }

    fun resendAll() {
        container.appScope.launch {
            _sendResult.value = container.aiSendManager.sendQueue(recordingId, onlyFailed = false).message()
        }
    }

    fun resendOne(markerId: Long) {
        container.appScope.launch {
            val ok = container.aiSendManager.sendSingle(markerId)
            _sendResult.value = if (ok) "重新生成完成" else "生成失败，请稍后再试"
        }
    }

    /**
     * 合并发送：选中的多个标记共用一次请求、一条讲解。
     *
     * [prompt] 是这次请求的提示词，界面会预填全局提示词让用户改。
     */
    fun sendMerged(markerIds: List<Long>, prompt: String) {
        if (markerIds.size < 2) return
        container.appScope.launch {
            val ok = container.aiSendManager.sendMerged(markerIds, prompt)
            _sendResult.value = if (ok) {
                "已把 ${markerIds.size} 个标记合并成一条讲解"
            } else {
                "合并发送失败，请检查网络或图片后重试"
            }
        }
    }

    /** 保存发送前的自定义提示词；传空则恢复使用全局提示词 */
    fun saveCustomPrompt(markerId: Long, prompt: String) {
        container.appScope.launch {
            container.db.markerDao().setCustomPrompt(
                markerId,
                prompt.trim().takeIf { it.isNotEmpty() },
                System.currentTimeMillis(),
            )
        }
    }

    /** 追问：带完整上下文 */
    fun followUp(markerId: Long, question: String) {
        container.appScope.launch {
            val ok = container.aiSendManager.followUp(markerId, question)
            _sendResult.value = if (ok) null else "追问发送失败，请稍后再试"
        }
    }

    /** 单轮重新生成 */
    fun regenerateTurn(markerId: Long, turnId: Long) {
        container.appScope.launch {
            val ok = container.aiSendManager.regenerateTurn(markerId, turnId)
            _sendResult.value = if (ok) null else "重新生成失败，请稍后再试"
        }
    }

    /** 编辑某轮追问并重新答复 */
    fun editTurn(markerId: Long, turnId: Long, newQuestion: String) {
        container.appScope.launch {
            val ok = container.aiSendManager.editTurn(markerId, turnId, newQuestion)
            _sendResult.value = if (ok) null else "编辑失败，请稍后再试"
        }
    }

    /** 切到某一轮的另一版（编辑/重新生成留下的历史版本） */
    fun selectVariant(markerId: Long, turnId: Long) {
        container.appScope.launch {
            container.aiSendManager.selectVariant(markerId, turnId)
        }
    }

    /** 手动停止生成：保留已生成的部分，可追问“继续”接上 */
    fun stopTurn(turnId: Long) {
        container.aiSendManager.stopTurn(turnId)
    }

    suspend fun imagesOf(marker: Marker): List<MarkerImage> =
        imageCache.getOrPut(marker.id) { container.db.markerImageDao().getByMarker(marker.id) }

    /**
     * 课后修图：用裁剪结果替换原图，并把这条标记退回"未发送"。
     *
     * 图变了，之前的讲解就对不上了——留着旧讲解比没有更糟。
     * 旧文件在这种场景下已经没有引用，直接删掉，避免 images 目录越积越大。
     */
    fun replaceMarkerImage(image: MarkerImage, croppedUri: android.net.Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            val newFile = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = container.recordingRepository.imagesDir
                val f = java.io.File(dir, "marker_${image.markerId}_edit_${System.currentTimeMillis()}.jpg")
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(croppedUri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    }
                }
                if (f.exists() && f.length() > 0) f else null
            } ?: return@launch

            withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.db.markerImageDao().insert(image.copy(filePath = newFile.absolutePath))
                if (image.filePath != newFile.absolutePath) {
                    runCatching { java.io.File(image.filePath).delete() }
                }
                // 旧讲解是基于旧图生成的，留着会和新图对不上——整条作废
                container.db.markerTurnDao().deleteByMarker(image.markerId)
                container.db.markerDao().setAiState(
                    image.markerId,
                    Marker.AI_PENDING,
                    null,
                    null,
                    System.currentTimeMillis(),
                )
            }
            imageCache.remove(image.markerId)
            onDone()
        }
    }

    fun consumeSendResult() {
        _sendResult.value = null
    }

    // ===== 待复习 =====

    /** 星标只动这一列，不整行覆盖，避免把并发生成的讲解写没 */
    fun toggleReview(marker: Marker) {
        container.appScope.launch {
            // 交给 SQL 翻转，而不是"把 marker.needsReview 取反再写回"：
            // 后者在连点时第二次读到的还是旧值，等于没点
            container.db.markerDao().toggleNeedsReview(marker.id, System.currentTimeMillis())
        }
    }

    // ===== 整节汇总 =====

    val digest: StateFlow<RecordingDigest?> =
        container.db.recordingDigestDao().observe(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _digestBusy = MutableStateFlow(false)
    val digestBusy: StateFlow<Boolean> = _digestBusy

    /** 流式生成中的草稿；非空且 busy 时 UI 直接显示它，不用等整段返回 */
    private val _digestDraft = MutableStateFlow("")
    val digestDraft: StateFlow<String> = _digestDraft

    private val _digestError = MutableStateFlow<String?>(null)
    val digestError: StateFlow<String?> = _digestError

    fun clearDigestError() {
        _digestError.value = null
    }

    private fun clock(ms: Long): String = com.suitanglu.app.data.Formatters.clockTime(ms)

    /**
     * 把一节课的材料压成一段纯文本上下文。
     *
     * 每个标记只取前 800 字、总计不超过 2 万字：一节课十几个标记，
     * 全量塞进去既慢又贵，而提纲只需要"讲了什么"，不需要完整推导。
     */
    private suspend fun buildCourseContext(): String {
        val markers = container.db.markerDao().getByRecording(recordingId)
        if (markers.isEmpty()) return ""
        // 按录音查轮次，不要 getAll() 再在内存里筛：追问一次就要读一遍全库
        val allTurns = container.db.markerTurnDao().getByRecording(recordingId)
        val byMarker = allTurns.groupBy { it.markerId }
        val sb = StringBuilder()
        for ((index, m) in markers.withIndex()) {
            if (sb.length > 20_000) break
            sb.append("【").append(index + 1).append("】").append(clock(m.offsetMs))
            m.userText?.takeIf { it.isNotBlank() }?.let { sb.append(" · ").append(it) }
            sb.append('\n')
            val turns = byMarker[m.id].orEmpty()
            if (turns.isEmpty()) {
                m.aiResponse?.let { sb.append(it.take(800)).append('\n') }
            } else {
                turns.forEach { t ->
                    t.question?.let { sb.append("问：").append(it.take(300)).append('\n') }
                    t.answer?.take(800)?.let { sb.append("答：").append(it).append('\n') }
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** 生成这节课的复习提纲：分点提纲 + 术语表 + 三个自测问题 */
    fun generateDigest() {
        if (_digestBusy.value) return
        container.appScope.launch {
            _digestBusy.value = true
            _digestError.value = null
            _digestDraft.value = ""
            try {
                val ai = container.settings.aiConfigNow()
                if (ai.apiKey.isBlank()) {
                    _digestError.value = "还没填 API Key，先去设置里配一下"
                    return@launch
                }
                val context = buildCourseContext()
                if (context.isBlank()) {
                    _digestError.value = "这节课还没有可以汇总的讲解"
                    return@launch
                }
                val title = _recording.value?.title ?: "这节课"
                val prompt = buildString {
                    append("下面是一节课堂录音里记下的全部标记，每个标记包含时间点、学生备注和 AI 讲解原文。\n\n")
                    append("<material>\n").append(context).append("</material>\n\n")
                    append("请把这些材料整理成《").append(title).append("》的复习提纲，直接输出 Markdown，不要开场白。严格用这三个二级标题：\n")
                    append("## 核心脉络\n按课堂顺序列 3–6 条，每条一句话说清这段讲了什么。\n")
                    append("## 关键概念\n术语表，每行「**术语** — 一句话解释」，只收材料里真正出现过的。\n")
                    append("## 自测问题\n三个问题，分别考记忆、理解、应用，不要给答案。\n\n")
                    append("材料里没提到的内容不要补充，宁可少写。")
                }
                // 流式增量要节流：MarkdownView 每次重组都会把整段文本重新丢给 WebView 渲染，
                // 逐 token 刷新会让对话卡成幻灯片。攒 300ms 再推一次，和标记生成保持一致。
                val draft = StringBuilder()
                var lastFlush = 0L
                val (text, _) = DeepSeekClient(ai).chat(
                    listOf(ChatMessage("user", prompt)),
                ) { delta ->
                    draft.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastFlush > 300) {
                        lastFlush = now
                        _digestDraft.value = draft.toString()
                    }
                }
                // createdAt 保留首次生成时间：REPLACE 会整行覆盖，不显式带上就会被重置
                val existing = container.db.recordingDigestDao().get(recordingId)
                container.db.recordingDigestDao().upsert(
                    RecordingDigest(
                        recordingId = recordingId,
                        content = text,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                _digestError.value = e.message?.take(200) ?: "生成失败"
            } finally {
                _digestDraft.value = ""
                _digestBusy.value = false
            }
        }
    }

    // ===== 整节对话 =====

    val chats: StateFlow<List<RecordingChat>> =
        container.db.recordingChatDao().observe(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy

    private val _chatDraft = MutableStateFlow("")
    val chatDraft: StateFlow<String> = _chatDraft

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError

    fun clearChatError() {
        _chatError.value = null
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _chatBusy.value) return
        container.appScope.launch {
            _chatBusy.value = true
            _chatError.value = null
            _chatDraft.value = ""
            try {
                val ai = container.settings.aiConfigNow()
                if (ai.apiKey.isBlank()) {
                    _chatError.value = "还没填 API Key，先去设置里配一下"
                    return@launch
                }
                container.db.recordingChatDao().insert(
                    RecordingChat(recordingId = recordingId, role = RecordingChat.ROLE_USER, content = q),
                )
                val history = container.db.recordingChatDao().getAll(recordingId)
                val digestText = container.db.recordingDigestDao().get(recordingId)?.content
                val material = buildCourseContext()
                val system = buildString {
                    append("你是这门课的学习助手。下面给出课堂录音里记下的标记与讲解，")
                    append("以及一份已生成的复习提纲。回答时优先引用这些材料，")
                    append("材料里没有的内容直接说「课上没记到」，不要编造。\n")
                    digestText?.let { append("\n<digest>\n").append(it.take(4000)).append("\n</digest>\n") }
                    if (material.isNotBlank()) {
                        append("\n<material>\n").append(material.take(12_000)).append("\n</material>\n")
                    }
                }
                val messages = buildList {
                    add(ChatMessage("system", system))
                    // 刚插入的这条就是当前问题，历史里去掉，末尾再单独补。
                    // 只带最近 6 轮、每条截断：材料已经占了大半上下文，历史再长会顶爆 token
                    history.dropLast(1).takeLast(6).forEach { m ->
                        add(ChatMessage(m.role, m.content.take(1500)))
                    }
                    add(ChatMessage("user", q))
                }
                val draft = StringBuilder()
                var lastFlush = 0L
                val (text, _) = DeepSeekClient(ai).chat(messages) { delta ->
                    draft.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastFlush > 300) {
                        lastFlush = now
                        _chatDraft.value = draft.toString()
                    }
                }
                container.db.recordingChatDao().insert(
                    RecordingChat(recordingId = recordingId, role = RecordingChat.ROLE_AI, content = text),
                )
            } catch (e: Exception) {
                _chatError.value = e.message?.take(200) ?: "回答失败"
            } finally {
                _chatDraft.value = ""
                _chatBusy.value = false
            }
        }
    }

    fun clearChatHistory() {
        container.appScope.launch {
            container.db.recordingChatDao().clear(recordingId)
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        _player.value?.release()
        super.onCleared()
    }
}
