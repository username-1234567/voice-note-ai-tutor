package com.suitanglu.app.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.suitanglu.app.data.SettingsRepository
import com.suitanglu.app.data.db.AppDatabase
import com.suitanglu.app.data.db.AiRequestLog
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerTurn
import com.suitanglu.app.util.ImageUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 流式输出实时状态（供 UI 直接展示正在生成的回复） */
data class StreamingState(val markerId: Long, val turnId: Long, val text: String)

class AiSendManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming

    private val _activeRecordingId = MutableStateFlow<Long?>(null)
    val activeRecordingId: StateFlow<Long?> = _activeRecordingId

    /** 进行中的生成任务（按轮次 id），支持用户手动停止以控制 token 消耗 */
    private val activeTurnJobs = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()

    /** 手动停止某一轮的生成：保留已生成的部分文本，可通过追问“继续”接上 */
    fun stopTurn(turnId: Long) {
        activeTurnJobs.remove(turnId)?.cancel()
    }

    /** 录音结束后自动发送（pending + failed） */
    fun enqueueAutoSend(recordingId: Long) {
        val request = OneTimeWorkRequestBuilder<AiSendWorker>()
            .setInputData(workDataOf(AiSendWorker.KEY_RECORDING_ID to recordingId))
            // 无网时等待而不是立刻失败标 failed
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("ai_send_$recordingId", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * 发送队列。@param onlyFailed true=重发失败；false=全部重发（failed + pending）
     * 某条失败后停止后续发送。
     */
    suspend fun sendQueue(recordingId: Long, onlyFailed: Boolean): SendResult {
        if (!busy.compareAndSet(false, true)) {
            return SendResult(0, 0, 0, busy = true)
        }
        _activeRecordingId.value = recordingId
        try {
            val markers = db.markerDao().getByRecording(recordingId)
                .filter { if (onlyFailed) it.aiStatus == Marker.AI_FAILED else it.aiStatus in setOf(Marker.AI_FAILED, Marker.AI_PENDING) }
            var ok = 0
            var failed = 0
            var skipped = 0
            for (marker in markers) {
                val success = try {
                    ensureTurn0AndGenerate(marker)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    runCatching { failTurn0(marker, e.message ?: e.javaClass.simpleName) }
                    failed++
                    false
                }
                if (success) {
                    ok++
                } else {
                    // 某条失败后跳过后续自动发送
                    skipped = markers.size - ok - failed
                    break
                }
            }
            return SendResult(markers.size, ok, failed, skipped = skipped)
        } finally {
            _activeRecordingId.value = null
            _streaming.value = null
            busy.set(false)
        }
    }

    /** 确保首轮（orderIndex=0）存在并生成；自动讲解与“单条重发”都走这里 */
    suspend fun ensureTurn0AndGenerate(marker: Marker): Boolean {
        val existing = db.markerTurnDao().getActiveByMarker(marker.id).firstOrNull { it.orderIndex == 0 }
        // 注意：insert 后必须用返回的 rowId 构造对象，否则后续按 id 的 update 全部落空
        val turn = existing ?: db.markerTurnDao().insert(
            MarkerTurn(markerId = marker.id, orderIndex = 0),
        ).let { newId -> MarkerTurn(markerId = marker.id, orderIndex = 0, id = newId) }
        return generateTurn(marker, turn)
    }

    /** 追问：按已有轮次拼接上下文，新增一轮问答 */
    suspend fun followUp(markerId: Long, question: String): Boolean {
        val marker = db.markerDao().getById(markerId) ?: return false
        val turns = db.markerTurnDao().getByMarker(markerId)
        val newTurn = MarkerTurn(
            markerId = markerId,
            orderIndex = (turns.maxOfOrNull { it.orderIndex } ?: -1) + 1,
            question = question,
        )
        val id = db.markerTurnDao().insert(newTurn)
        return try {
            generateTurn(marker, newTurn.copy(id = id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 重新生成某一轮。
     *
     * 旧版不删：新建一版并置为生效，界面上用「1/2」切回。
     * 原来的做法是 deleteAfter + 覆盖同一行，等于把旧答案和它的花费一起抹掉，
     * 用户想对比两版讲了什么就没机会了。
     */
    suspend fun regenerateTurn(markerId: Long, turnId: Long): Boolean {
        val marker = db.markerDao().getById(markerId) ?: return false
        val turn = db.markerTurnDao().getById(turnId) ?: return false
        // 失败的空版本不值得留：直接复用这一行。
        // 否则每点一次「重试」就多出一个"生成失败"的空壳，1/2、1/3… 越积越多
        if (turn.answer.isNullOrBlank()) {
            db.markerTurnDao().deactivateVariants(markerId, turn.orderIndex)
            val revived = turn.copy(active = true, status = MarkerTurn.TURN_PENDING, error = null)
            db.markerTurnDao().update(revived)
            return try {
                generateTurn(marker, revived)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
        db.markerTurnDao().deactivateVariants(markerId, turn.orderIndex)
        val fresh = MarkerTurn(
            markerId = markerId,
            orderIndex = turn.orderIndex,
            question = turn.question,
            active = true,
        )
        val id = db.markerTurnDao().insert(fresh)
        return try {
            generateTurn(marker, fresh.copy(id = id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** 编辑某轮问题（含首轮提示词）并重新答复：同样保留旧版，后续追问不再丢弃 */
    suspend fun editTurn(markerId: Long, turnId: Long, newQuestion: String): Boolean {
        val marker = db.markerDao().getById(markerId) ?: return false
        val turn = db.markerTurnDao().getById(turnId) ?: return false
        db.markerTurnDao().deactivateVariants(markerId, turn.orderIndex)
        val fresh = MarkerTurn(
            markerId = markerId,
            orderIndex = turn.orderIndex,
            question = newQuestion,
            active = true,
        )
        val id = db.markerTurnDao().insert(fresh)
        return try {
            generateTurn(marker, fresh.copy(id = id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** 切到某一轮的另一版：整组先失效，再点亮目标 */
    suspend fun selectVariant(markerId: Long, turnId: Long) {
        val turn = db.markerTurnDao().getById(turnId) ?: return
        db.markerTurnDao().deactivateVariants(markerId, turn.orderIndex)
        db.markerTurnDao().activateVariant(turnId)
        // 首轮换版后，标记卡片上那句"讲解"要跟着换，否则列表和详情各说各的
        if (turn.orderIndex == 0) {
            turn.answer?.let { answer ->
                db.markerDao().updateAi(
                    markerId,
                    if (turn.status == MarkerTurn.TURN_FAILED) Marker.AI_FAILED else Marker.AI_SUCCESS,
                    answer,
                    turn.error,
                    System.currentTimeMillis(),
                )
            }
        }
    }

    /**
     * 生成一轮回复。上下文 = 此前各轮问答原文（首轮问题为持久化的实际提示词，含图片），
     * 前缀与上一轮请求完全一致以命中 DeepSeek 上下文缓存。
     * 首轮问题 = 设置提示词 + 「涉及知识点」（标记文字备注），生成时持久化，可在对话中查看与编辑。
     */
    private suspend fun generateTurn(marker: Marker, turn: MarkerTurn): Boolean {
        val now = System.currentTimeMillis()
        // 这条要自己讲了，之前"并入别处"的标记必须摘掉，
        // 否则界面上会同时显示"已并入合并讲解"和自己的回答
        db.markerDao().clearMerged(marker.id)
        val ai = settings.aiConfigNow()
        if (ai.apiKey.isBlank()) {
            failTurn(turn, "未配置 API Key，请到设置页填写")
            if (turn.orderIndex == 0) db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, "未配置 API Key", now)
            return false
        }
        val images = db.markerImageDao().getByMarker(marker.id).map { File(it.filePath) }.filter { it.exists() }
        if (images.isEmpty()) {
            failTurn(turn, "标记下没有图片")
            if (turn.orderIndex == 0) db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, "标记下没有图片", now)
            return false
        }

        var current = turn
        if (current.orderIndex == 0 && current.question.isNullOrBlank()) {
            // 首轮问题 = 提示词 + 涉及知识点（标记备注），持久化以便对话中显示与编辑。
            // 标记自己设过提示词就用它，否则退回设置页的全局提示词
            val basePrompt = marker.customPrompt?.takeIf { it.isNotBlank() } ?: ai.prompt
            val knowledge = marker.userText?.takeIf { it.isNotBlank() }?.let { "\n涉及知识点：$it" }.orEmpty()
            current = current.copy(question = basePrompt + knowledge)
            db.markerTurnDao().update(current)
        }

        // 上下文：此前各轮问答原文（首轮的 user 消息带图）。
        // 只取 active 版本——同一轮可能有多版，全塞进去模型会看到重复问答
        val history = db.markerTurnDao().getActiveByMarker(marker.id)
            .filter { it.orderIndex < current.orderIndex }
        val messages = mutableListOf<ChatMessage>()
        for (t in history) {
            messages += ChatMessage(
                role = "user",
                text = t.question.orEmpty(),
                imageFiles = if (t.orderIndex == 0) images else emptyList(),
            )
            messages += ChatMessage("assistant", t.answer.orEmpty())
        }
        messages += ChatMessage(
            role = "user",
            text = current.question.orEmpty(),
            imageFiles = if (current.orderIndex == 0) images else emptyList(),
        )

        db.markerTurnDao().update(current.copy(status = MarkerTurn.TURN_SENDING, updatedAt = now))
        if (turn.orderIndex == 0) db.markerDao().updateAi(marker.id, Marker.AI_SENDING, null, null, now)
        val logId = db.aiRequestLogDao().insert(
            AiRequestLog(markerId = marker.id, model = ai.model, prompt = ai.prompt, status = "sending"),
        )

        var buffer = StringBuilder()
        var lastFlush = 0L
        // 重复输出防护：模型退化时会对同一分片无限重复，命中阈值立即终止（用户也可手动停止）
        var repeatCount = 0
        var lastDelta: String? = null
        coroutineContext[Job]?.let { activeTurnJobs[turn.id] = it }
        try {
            val general = settings.generalConfigNow()
            val client = DeepSeekClient(ai, general.compressImages, general.compressQuality)
            val (answer, usage) = client.chat(messages) { delta ->
                if (delta.isNotEmpty() && delta == lastDelta) {
                    repeatCount++
                    if (repeatCount >= 20) {
                        throw IOException("检测到模型重复输出，已自动终止（可点重新生成重试）")
                    }
                } else {
                    repeatCount = 0
                    lastDelta = delta
                }
                if (buffer.length > 200_000) {
                    throw IOException("输出超出长度上限，已自动终止")
                }
                buffer.append(delta)
                val t = System.currentTimeMillis()
                if (t - lastFlush > 300) {
                    lastFlush = t
                    _streaming.value = StreamingState(marker.id, turn.id, buffer.toString())
                }
            }
            _streaming.value = null
            // 成功路径也要摘掉：只在 catch 里 remove 的话，每成功一条就留一个 Job 在 map 里
            activeTurnJobs.remove(turn.id)
            val t = System.currentTimeMillis()
            db.markerTurnDao().update(
                current.copy(
                    status = MarkerTurn.TURN_SUCCESS,
                    answer = answer,
                    promptTokens = usage.promptTokens,
                    cacheHitTokens = usage.cacheHitTokens,
                    completionTokens = usage.completionTokens,
                    finishReason = usage.finishReason,
                    updatedAt = t,
                ),
            )
            if (turn.orderIndex == 0) {
                db.markerDao().updateAi(marker.id, Marker.AI_SUCCESS, answer, null, t)
            }
            db.aiRequestLogDao().finish(logId, "success", answer.take(20000), null, t)
            return true
        } catch (e: CancellationException) {
            // 用户手动停止或协程被取消：已生成的部分保留下来（可通过追问“继续”接上），
            // 全程落库避免轮次卡在“发送中”
            withContext(NonCancellable) {
                _streaming.value = null
                activeTurnJobs.remove(turn.id)
                val partial = buffer.toString()
                val t = System.currentTimeMillis()
                if (partial.isNotEmpty()) {
                    db.markerTurnDao().update(
                        current.copy(
                            status = MarkerTurn.TURN_SUCCESS,
                            answer = partial,
                            finishReason = "stopped_by_user",
                            updatedAt = t,
                        ),
                    )
                    if (turn.orderIndex == 0) {
                        db.markerDao().updateAi(marker.id, Marker.AI_SUCCESS, partial, null, t)
                    }
                    db.aiRequestLogDao().finish(logId, "stopped", partial.take(20000), null, t)
                } else {
                    failTurn(current, "已手动停止生成")
                    if (turn.orderIndex == 0) {
                        db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, "已手动停止生成", t)
                    }
                    db.aiRequestLogDao().finish(logId, "failed", null, "已手动停止生成", t)
                }
            }
            throw e
        } catch (e: Exception) {
            _streaming.value = null
            activeTurnJobs.remove(turn.id)
            failTurn(current, e.message ?: e.javaClass.simpleName)
            if (turn.orderIndex == 0) {
                db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, e.message ?: e.javaClass.simpleName, System.currentTimeMillis())
            }
            db.aiRequestLogDao().finish(
                logId, "failed", null, (e.message ?: e.javaClass.simpleName).take(2000), System.currentTimeMillis(),
            )
            return false
        }
    }

    private suspend fun failTurn(turn: MarkerTurn, error: String) {
        db.markerTurnDao().update(
            turn.copy(status = MarkerTurn.TURN_FAILED, error = error, updatedAt = System.currentTimeMillis()),
        )
    }

    private suspend fun failTurn0(marker: Marker, error: String) {
        val turn0 = db.markerTurnDao().getByMarker(marker.id).firstOrNull { it.orderIndex == 0 }
        if (turn0 != null) failTurn(turn0, error)
        db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, error, System.currentTimeMillis())
    }

    /** 单条重发入口：有首轮则重新生成，没有则补建首轮 */
    /**
     * 合并发送：把 N 个标记的图片放进同一次请求，共用一条讲解。
     *
     * 拍一页课件常常要分两三张（左半页、右半页、补充公式、老师的板书），
     * 各发各的模型看不到彼此，只能各讲一段；合并之后它面对的是同一处内容，
     * 能指出"第二张那个符号就是第一张里的 μ"。
     *
     * 结果只落在组首那一轮上，其余成员记 [Marker.mergedInto] 指过来，
     * 不重复存一份回答——否则同一段话会在列表里出现三四次。
     */
    suspend fun sendMerged(markerIds: List<Long>, prompt: String): Boolean {
        if (markerIds.size < 2) return false
        if (!busy.compareAndSet(false, true)) return false
        _activeRecordingId.value = db.markerDao().getById(markerIds.first())?.recordingId
        try {
            // 按 offsetMs 排序：最早那张所属的标记作为组首，最晚那张的 offsetMs 是时间段终点
            val members = markerIds.mapNotNull { db.markerDao().getById(it) }.sortedBy { it.offsetMs }
            if (members.size < 2) return false
            val head = members.first()
            val images = members
                .flatMap { db.markerImageDao().getByMarker(it.id) }
                .map { File(it.filePath) }
                .filter { it.exists() }
                .distinctBy { it.absolutePath }
            if (images.isEmpty()) {
                val msg = "这些标记下没有图片"
                members.forEach {
                    db.markerDao().updateAi(it.id, Marker.AI_FAILED, null, msg, System.currentTimeMillis())
                }
                return false
            }

            val ai = settings.aiConfigNow()
            if (ai.apiKey.isBlank()) {
                val msg = "未配置 API Key"
                members.forEach {
                    db.markerDao().updateAi(it.id, Marker.AI_FAILED, null, msg, System.currentTimeMillis())
                }
                return false
            }

            val effectivePrompt = prompt.ifBlank { ai.prompt }
            val question = buildString {
                append(effectivePrompt)
                val knowledge = members.mapNotNull { it.userText?.takeIf { t -> t.isNotBlank() } }
                if (knowledge.isNotEmpty()) {
                    append("\n\n涉及知识点：")
                    knowledge.forEach { append("\n- ").append(it) }
                }
                if (images.size > 1) {
                    append("\n\n（附件是同一处内容的 ")
                    append(images.size)
                    append(" 张图，请合起来讲；重复的部分不用逐张复述。）")
                }
            }

            // 合并即重做，但旧讲解不删：它已经花过钱，删掉等于把真实支出从账上抹掉。
            // 把旧版置为非生效、新讲解作为当前版，用户还能用 1/2 切回看单条讲解
            db.markerTurnDao().deactivateVariants(head.id, 0)
            val turnId = db.markerTurnDao().insert(
                MarkerTurn(markerId = head.id, orderIndex = 0, question = question),
            )
            var current = MarkerTurn(markerId = head.id, orderIndex = 0, question = question, id = turnId)

            val now = System.currentTimeMillis()
            db.markerTurnDao().update(current.copy(status = MarkerTurn.TURN_SENDING, updatedAt = now))
            db.markerDao().updateAi(head.id, Marker.AI_SENDING, null, null, now)
            members.drop(1).forEach {
                db.markerDao().updateAi(it.id, Marker.AI_SENDING, null, null, now)
            }

            val buffer = StringBuilder()
            var lastFlush = 0L
            try {
                val general = settings.generalConfigNow()
                val client = DeepSeekClient(ai, general.compressImages, general.compressQuality)
                val (answer, usage) = client.chat(
                    listOf(ChatMessage("user", question, images)),
                ) { delta ->
                    buffer.append(delta)
                    val t = System.currentTimeMillis()
                    if (t - lastFlush > 300) {
                        lastFlush = t
                        _streaming.value = StreamingState(head.id, turnId, buffer.toString())
                    }
                }
                _streaming.value = null
                val t = System.currentTimeMillis()
                db.markerTurnDao().update(
                    current.copy(
                        status = MarkerTurn.TURN_SUCCESS,
                        answer = answer,
                        promptTokens = usage.promptTokens,
                        cacheHitTokens = usage.cacheHitTokens,
                        completionTokens = usage.completionTokens,
                        finishReason = usage.finishReason,
                        updatedAt = t,
                    ),
                )
                db.markerDao().updateAi(head.id, Marker.AI_SUCCESS, answer, null, t)
                // 真正合并成一个：把成员的图片挪到组首标记下，成员记 mergedInto 指过来（列表里隐藏）。
                // members 已按 offsetMs 排序，head 是"最早那张"、last 是"最晚那张"，
                // 播放条据此画成 [head.offsetMs, last.offsetMs] 的区间，而不是几个散点。
                val headOffset = head.offsetMs
                val endOffset = members.last().offsetMs
                members.drop(1).forEach { m ->
                    db.markerImageDao().reassignTo(m.id, head.id)
                    // 成员的旧讲解也保留：它们是真实发生过的调用，
                    // 汇总"这节课花了多少"时必须算进去（成员标记本身在列表里已隐藏）
                    db.markerDao().mergeInto(m.id, head.id, Marker.AI_SUCCESS, t)
                }
                db.markerDao().setMergedEnd(
                    head.id,
                    if (endOffset > headOffset) endOffset else headOffset,
                    t,
                )
                return true
            } catch (e: CancellationException) {
                _streaming.value = null
                return false
            } catch (e: Exception) {
                _streaming.value = null
                val t = System.currentTimeMillis()
                val msg = e.message ?: e.javaClass.simpleName
                failTurn(current, msg)
                db.markerDao().updateAi(head.id, Marker.AI_FAILED, null, msg, t)
                members.drop(1).forEach {
                    db.markerDao().updateAi(it.id, Marker.AI_FAILED, null, msg, t)
                }
                return false
            }
        } finally {
            _activeRecordingId.value = null
            _streaming.value = null
            busy.set(false)
        }
    }

    suspend fun sendSingle(markerId: Long): Boolean {
        // 和批量发送共用同一个 busy：
        // 两边并发时会各自查到"还没有 orderIndex=0 的轮次"，然后各插一条，
        // 结果同一个标记出现两条首轮对话，_streaming 也会被两边交替覆盖
        if (!busy.compareAndSet(false, true)) return false
        try {
            val marker = db.markerDao().getById(markerId) ?: return false
            return ensureTurn0AndGenerate(marker)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        } finally {
            _streaming.value = null
            busy.set(false)
        }
    }
}

data class SendResult(val total: Int, val success: Int, val failed: Int, val skipped: Int = 0, val busy: Boolean = false) {
    fun message(): String = when {
        busy -> "已有发送任务在进行中，请稍后"
        total == 0 -> "没有需要发送的标记"
        failed == 0 -> "发送完成：$success 条成功"
        else -> "第 ${success + 1} 条发送失败已停止${if (skipped > 0) "，跳过 $skipped 条" else ""}，成功 $success 条。网络良好时请重试"
    }
}
