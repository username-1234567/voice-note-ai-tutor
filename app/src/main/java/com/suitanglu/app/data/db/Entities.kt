package com.suitanglu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "未命名",
    val courseName: String? = null,
    val courseSlotId: Int? = null,
    val filePath: String,
    val fileHash: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long = 0,
    val status: String = STATUS_RECORDING,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_RECORDING = "recording"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_INTERRUPTED = "interrupted"
        const val STATUS_AUDIO_DELETED = "audio_deleted"
    }
}

@Serializable
@Entity(tableName = "markers")
data class Marker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val offsetMs: Long,
    val absoluteTime: Long,
    val userText: String? = null,
    val aiStatus: String = AI_PENDING,
    val aiResponse: String? = null,
    val aiError: String? = null,
    val sendOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * 待复习：语义是"这条我还没弄懂，回头要再看一遍"，不是收藏。
     * 所以它的入口是星标而不是心形，列表里也按"未懂"而不是"喜欢"来解释。
     *
     * defaultValue 必须写：迁移里是 `ADD COLUMN ... NOT NULL DEFAULT 0`，
     * 而 Room 启动时会比对实体的 defaultValue 与实际表结构，两边不一致会直接抛
     * "Room cannot verify the data integrity" —— 老用户升级时必崩。
     */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val needsReview: Boolean = false,

    /**
     * 发送前自定义的提示词。为空时用设置页里的全局提示词。
     * 和 userText（涉及知识点）不是一回事：那是"这页讲的是什么"，这是"要 AI 怎么讲"。
     */
    val customPrompt: String? = null,

    /**
     * 并入哪一条标记的合并讲解（存对方的 markerId）。
     *
     * 几张图拍的是同一处内容时，一起发一次请求、共享一条回答，
     * 比每张各发一次既省钱又能让 AI 看到上下文。非空表示这条自己不再单独发。
     */
    val mergedInto: Long? = null,

    /**
     * 合并后这一条标记在播放条上画成的时间段终点（毫秒，相对于录音开始）。
     *
     * 合并几张图时，组首标记的 offsetMs 是"最早那张图的拍摄时刻"，这里存"最晚那张图的拍摄时刻"，
     * 两者之间就是这条合并标记覆盖的时间段。非合并的单条标记此值为 null，播放条画成一个点。
     */
    val mergedEndOffsetMs: Long? = null,
) {
    companion object {
        const val AI_PENDING = "pending"
        const val AI_SENDING = "sending"
        const val AI_SUCCESS = "success"
        const val AI_FAILED = "failed"
    }
}

@Serializable
@Entity(tableName = "marker_images")
data class MarkerImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markerId: Long,
    val filePath: String,
    val fileHash: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "marker_turns", indices = [androidx.room.Index("markerId")])
data class MarkerTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markerId: Long,
    val orderIndex: Int, // 0 = 录音结束的自动讲解轮，其后为追问轮
    val question: String? = null, // 追问内容（首轮为 null，使用标记的主提示词）
    val answer: String? = null,
    val status: String = TURN_PENDING,
    val error: String? = null,
    val promptTokens: Int = 0, // 输入 token 数（含缓存命中）
    val cacheHitTokens: Int = 0, // 命中上下文缓存的 token 数（计费大幅降低）
    val completionTokens: Int = 0,
    val finishReason: String? = null, // length = 达到最大输出被截断
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TURN_PENDING = "pending"
        const val TURN_SENDING = "sending"
        const val TURN_SUCCESS = "success"
        const val TURN_FAILED = "failed"
    }
}

@Serializable
@Entity(tableName = "pause_events")
data class PauseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val offsetMs: Long,
    val type: String,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_USER_PAUSE = "user_pause"
        const val TYPE_AUTO_INTERRUPT = "auto_interrupt"
        const val TYPE_RESUME = "resume"
    }
}

@Serializable
@Entity(tableName = "ai_request_logs")
data class AiRequestLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markerId: Long,
    val model: String,
    val prompt: String,
    val status: String,
    val response: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

/**
 * 一节课的 AI 汇总（分点提纲 + 术语表 + 自测问题）。
 *
 * 每个标记都已经有讲解，但复习时要的是"这节课讲了什么"，
 * 而不是十条互不相干的片段。一个 recordingId 只保留一份，重新生成即覆盖。
 */
@Serializable
@Entity(tableName = "recording_digests")
data class RecordingDigest(
    @PrimaryKey val recordingId: Long,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 就"这节课"直接对话的消息。
 *
 * 和 MarkerTurn 的区别：那边一问一图，这边是整节课的上下文，
 * 用来问"第 20 分钟讲的那个公式是什么意思"这类跨标记的问题。
 */
@Serializable
// indices 必须声明：迁移 SQL 里建了 index_recording_chats_recordingId，
// Room 校验时会把"实际有索引、实体没声明"也判为不匹配
@Entity(tableName = "recording_chats", indices = [androidx.room.Index("recordingId")])
data class RecordingChat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    /** user / assistant */
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_AI = "assistant"
    }
}

/**
 * 直接对话的会话。
 *
 * 和 RecordingChat 的区别：那个挂在某条录音下、上下文是那节课；
 * 这个是全局的，可以随手问一个问题、拍一张图，答完存成一条会话，下次能找回来。
 */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 自动生成：首条消息先截取，随后尝试用 AI 改写成一个能一眼认出的短标题 */
    val title: String,
    /** 用户自己补的备注，可以为空 */
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 直接对话的消息。
 *
 * imagePaths 用 \n 分隔多张图：一次发多张课件照片问"这几页有什么区别"是常见用法，
 * 单独建表反而让"哪张图属于哪句话"变得难查。
 */
@Entity(tableName = "chat_messages", indices = [androidx.room.Index("sessionId")])
data class DirectMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** user / assistant */
    val role: String,
    val content: String,
    val imagePaths: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_AI = "assistant"

        fun splitPaths(raw: String?): List<String> =
            raw?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

        fun joinPaths(paths: List<String>): String? =
            paths.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
