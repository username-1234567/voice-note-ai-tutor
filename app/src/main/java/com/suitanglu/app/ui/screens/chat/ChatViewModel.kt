package com.suitanglu.app.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.RecordApp
import com.suitanglu.app.ai.ChatMessage
import com.suitanglu.app.ai.DeepSeekClient
import com.suitanglu.app.data.db.ChatSession
import com.suitanglu.app.data.db.DirectMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全局对话：随手问一个问题、拍几张图，答完存成一条会话。
 *
 * 和 PlaybackViewModel 里那套"整节对话"的区别：那边上下文来自某节课的标记，
 * 这边什么都不依赖，是学生自己发起的一次独立提问。
 */
class ChatViewModel(app: Application, private val sessionId: Long) : AndroidViewModel(app) {

    private val container = (app as RecordApp).container

    val session: StateFlow<ChatSession?> = container.db.chatSessionDao()
        .observeById(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<DirectMessage>> = container.db.directMessageDao()
        .observeBySession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 已选好但还没发出去的图片（本地绝对路径） */
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending: StateFlow<List<String>> = _pending

    fun clearError() {
        _error.value = null
    }

    /** 相册选中的图片复制进私有目录：content:// 的授权是临时的，过一会儿就读不到了 */
    fun addImages(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val dir = File(getApplication<Application>().filesDir, "chat_images").apply { mkdirs() }
                uris.mapNotNull { uriString ->
                    runCatching {
                        val src = android.net.Uri.parse(uriString)
                        val dest = File(dir, "chat_${System.currentTimeMillis()}_${System.nanoTime() % 1000}.jpg")
                        getApplication<Application>().contentResolver.openInputStream(src)?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                        dest.takeIf { it.exists() && it.length() > 0 }?.absolutePath
                    }.getOrNull()
                }
            }
            _pending.value = _pending.value + saved
        }
    }

    fun removePending(path: String) {
        _pending.value = _pending.value - path
        runCatching { File(path).delete() }
    }

    fun send(text: String) {
        val q = text.trim()
        val images = _pending.value.filter { File(it).exists() }
        // 允许"只发图不说话"：拍一张题直接问是最常见的用法
        if ((q.isEmpty() && images.isEmpty()) || _busy.value) return
        container.appScope.launch {
            _busy.value = true
            _error.value = null
            _draft.value = ""
            try {
                val ai = container.settings.aiConfigNow()
                if (ai.apiKey.isBlank()) {
                    _error.value = "还没填 API Key，先去设置里配一下"
                    return@launch
                }
                val dao = container.db.directMessageDao()
                val firstMessage = dao.countBySession(sessionId) == 0

                dao.insert(
                    DirectMessage(
                        sessionId = sessionId,
                        role = DirectMessage.ROLE_USER,
                        content = q.ifBlank { "（发了一张图）" },
                        imagePaths = DirectMessage.joinPaths(images),
                    ),
                )
                _pending.value = emptyList()

                val history = dao.getBySession(sessionId)
                val messages = buildList {
                    add(ChatMessage("system", SYSTEM_PROMPT))
                    // 最近 20 条：更早的追问对当前问题帮助有限，全带上会一直变慢
                    history.dropLast(1).takeLast(20).forEach { m ->
                        add(
                            ChatMessage(
                                role = m.role,
                                text = m.content.take(2000),
                                // 历史也带图：DeepSeek 的前缀缓存会命中，重复的图片不计费
                                imageFiles = DirectMessage.splitPaths(m.imagePaths).map { File(it) }
                                    .filter { it.exists() },
                            ),
                        )
                    }
                    add(
                        ChatMessage(
                            role = "user",
                            text = q.ifBlank { "请讲解这张图。" },
                            imageFiles = images.map { File(it) },
                        ),
                    )
                }

                val draft = StringBuilder()
                var lastFlush = 0L
                val (answer, _) = DeepSeekClient(ai, compressImages = true, compressQuality = 80).chat(messages) { delta ->
                    draft.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastFlush > 300) {
                        lastFlush = now
                        _draft.value = draft.toString()
                    }
                }
                dao.insert(
                    DirectMessage(
                        sessionId = sessionId,
                        role = DirectMessage.ROLE_AI,
                        content = answer,
                    ),
                )
                container.db.chatSessionDao().touch(sessionId, System.currentTimeMillis())

                // 首轮结束后补一个能认出来的标题（失败就保留截取版）
                if (firstMessage) generateTitle(q, images.isNotEmpty())
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: "回答失败"
            } finally {
                _draft.value = ""
                _busy.value = false
            }
        }
    }

    /**
     * 用一次低成本的调用换一个短标题。
     *
     * 列表里如果全是"这道题怎么做…""这个公式…"这种截断，十几次之后就没法区分了，
     * 所以花这一点 token 换"能一眼认出来"是值的。thinking 关掉，只求快。
     */
    private suspend fun generateTitle(question: String, hasImage: Boolean) {
        val ai = container.settings.aiConfigNow()
        if (ai.apiKey.isBlank()) return
        val prompt = buildString {
            append("给下面这段对话起一个标题，要求：不超过 12 个汉字，直接输出标题本身，")
            append("不要引号、不要句号、不要解释。\n\n")
            if (hasImage) append("（用户还发了一张图片）\n")
            append("用户：").append(question.ifBlank { "讲一下这张图" })
        }
        val title = runCatching {
            val (text, _) = DeepSeekClient(ai.copy(reasoningEffort = "off"))
                .chat(listOf(ChatMessage("user", prompt))) {}
            text.trim().trim('"', '“', '”', '。', '.', '\n').take(24)
        }.getOrNull()
        if (!title.isNullOrBlank()) {
            container.db.chatSessionDao().setTitle(sessionId, title, System.currentTimeMillis())
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是一个学习助手，面对的是大学生。回答要准确、简洁，能画图就用文字描述清楚，" +
                "公式用 LaTeX。学生可能发课件照片或题目截图，先看清楚图里的内容再回答，" +
                "不确定的地方直接说，不要编。"
    }
}
