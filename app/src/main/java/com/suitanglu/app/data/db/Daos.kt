package com.suitanglu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    // audio_deleted 也保留在首页：需求要求条目显示“音频已删除”且标记/AI 回复仍可查看
    @Query("SELECT * FROM recordings WHERE deletedAt IS NULL ORDER BY startTime DESC")
    fun observeActive(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE deletedAt IS NULL ORDER BY startTime DESC")
    suspend fun getAllActive(): List<Recording>

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<Recording>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE status = 'recording'")
    suspend fun getUnfinished(): List<Recording>

    @Query("SELECT * FROM recordings WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("UPDATE recordings SET durationMs = :durationMs, updatedAt = :now WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long, now: Long)

    @Query("UPDATE recordings SET fileHash = :hash, updatedAt = :now WHERE id = :id")
    suspend fun updateHash(id: Long, hash: String, now: Long)

    @Query("UPDATE recordings SET filePath = :path, updatedAt = :now WHERE id = :id")
    suspend fun updateFilePath(id: Long, path: String, now: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}

data class MarkerWithImages(
    val marker: Marker,
    val images: List<MarkerImage>,
)

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers WHERE recordingId = :recordingId ORDER BY offsetMs ASC")
    fun observeByRecording(recordingId: Long): Flow<List<Marker>>

    @Query("SELECT * FROM markers WHERE recordingId = :recordingId ORDER BY sendOrder ASC, offsetMs ASC")
    suspend fun getByRecording(recordingId: Long): List<Marker>

    @Query("SELECT * FROM markers ORDER BY offsetMs ASC")
    fun observeAll(): Flow<List<Marker>>

    @Query("SELECT * FROM markers ORDER BY offsetMs ASC")
    suspend fun getAll(): List<Marker>

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getById(id: Long): Marker?

    @Query("SELECT COUNT(*) FROM markers WHERE recordingId = :recordingId")
    suspend fun countByRecording(recordingId: Long): Int

    @Query("SELECT COUNT(*) FROM markers WHERE recordingId = :recordingId AND aiStatus IN ('failed','pending')")
    suspend fun countUnsent(recordingId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: Marker): Long

    @Update
    suspend fun update(marker: Marker)

    /** 待复习星标：只动这一列，避免整行覆盖掉并发生成的 AI 内容 */
    @Query("UPDATE markers SET needsReview = :value, updatedAt = :now WHERE id = :id")
    suspend fun setNeedsReview(id: Long, value: Boolean, now: Long)

    /**
     * 原子翻转星标。
     * 用"读当前值再取反"的写法时，连点两下会两次都读到旧值，第二次等于没点。
     */
    @Query(
        "UPDATE markers SET needsReview = CASE WHEN needsReview = 1 THEN 0 ELSE 1 END, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun toggleNeedsReview(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM markers WHERE recordingId = :recordingId AND needsReview = 1")
    suspend fun countNeedsReview(recordingId: Long): Int

    @Query("UPDATE markers SET aiStatus = :status, aiResponse = COALESCE(:response, aiResponse), aiError = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateAi(id: Long, status: String, response: String?, error: String?, now: Long)

    /**
     * 直接覆盖 aiResponse（含置 null）。
     * updateAi 用 COALESCE 保留旧回复，改图后需要真正清空——否则会出现
     * "状态是待发送、但身上还挂着基于旧图的讲解"这种自相矛盾的记录。
     */
    @Query("UPDATE markers SET aiStatus = :status, aiResponse = :response, aiError = :error, updatedAt = :now WHERE id = :id")
    suspend fun setAiState(id: Long, status: String, response: String?, error: String?, now: Long)

    @Query("DELETE FROM markers WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)

    @Query("UPDATE markers SET aiStatus = 'failed', aiError = '应用中断，未完成发送', updatedAt = :now WHERE aiStatus = 'sending'")
    suspend fun resetSending(now: Long)
}

@Dao
interface MarkerImageDao {
    @Query("SELECT * FROM marker_images WHERE markerId = :markerId ORDER BY orderIndex ASC")
    suspend fun getByMarker(markerId: Long): List<MarkerImage>

    @Query("SELECT * FROM marker_images")
    suspend fun getAll(): List<MarkerImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<MarkerImage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: MarkerImage): Long

    @Query("DELETE FROM marker_images WHERE markerId = :markerId")
    suspend fun deleteByMarker(markerId: Long)
}

@Dao
interface PauseEventDao {
    @Query("SELECT * FROM pause_events WHERE recordingId = :recordingId ORDER BY offsetMs ASC")
    fun observeByRecording(recordingId: Long): Flow<List<PauseEvent>>

    @Query("SELECT * FROM pause_events")
    suspend fun getAll(): List<PauseEvent>

    @Insert
    suspend fun insert(event: PauseEvent): Long

    @Query("DELETE FROM pause_events WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)
}

@Dao
interface MarkerTurnDao {
    @Query("SELECT * FROM marker_turns WHERE markerId = :markerId ORDER BY orderIndex ASC, id ASC")
    fun observeByMarker(markerId: Long): Flow<List<MarkerTurn>>

    @Query("SELECT * FROM marker_turns ORDER BY markerId ASC, orderIndex ASC, id ASC")
    fun observeAll(): Flow<List<MarkerTurn>>

    @Query("SELECT * FROM marker_turns WHERE markerId = :markerId ORDER BY orderIndex ASC, id ASC")
    suspend fun getByMarker(markerId: Long): List<MarkerTurn>

    @Query("SELECT * FROM marker_turns ORDER BY markerId ASC, orderIndex ASC, id ASC")
    suspend fun getAll(): List<MarkerTurn>

    /**
     * 只取某条录音的所有轮次。
     *
     * 整节汇总/对话每次都要这份材料，用 getAll() 再在内存里 filter 的话，
     * 每问一句都要把全库的轮次读一遍。
     */
    @Query(
        "SELECT t.* FROM marker_turns t INNER JOIN markers m ON t.markerId = m.id " +
            "WHERE m.recordingId = :recordingId ORDER BY t.markerId ASC, t.orderIndex ASC, t.id ASC",
    )
    suspend fun getByRecording(recordingId: Long): List<MarkerTurn>

    @Query("SELECT * FROM marker_turns WHERE id = :id")
    suspend fun getById(id: Long): MarkerTurn?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turn: MarkerTurn): Long

    @Update
    suspend fun update(turn: MarkerTurn)

    // 编辑某轮追问后，丢弃其后的所有轮次（重新生成时上下文以它为准）
    @Query("DELETE FROM marker_turns WHERE markerId = :markerId AND orderIndex > :orderIndex")
    suspend fun deleteAfter(markerId: Long, orderIndex: Int)

    @Query("DELETE FROM marker_turns WHERE markerId = :markerId")
    suspend fun deleteByMarker(markerId: Long)
}

@Dao
interface AiRequestLogDao {
    @Insert
    suspend fun insert(log: AiRequestLog): Long

    @Query("SELECT * FROM ai_request_logs")
    suspend fun getAll(): List<AiRequestLog>

    @Query("UPDATE ai_request_logs SET status = :status, response = :response, error = :error, finishedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: Long, status: String, response: String?, error: String?, finishedAt: Long)

    @Query("DELETE FROM ai_request_logs WHERE markerId IN (SELECT id FROM markers WHERE recordingId = :recordingId)")
    suspend fun deleteForRecording(recordingId: Long)

    /**
     * 只保留最近 [keep] 条。
     *
     * 每次生成都会写一条（成功时带最多 20KB 的回答），而备份是整库打包——
     * 不清理的话这个表会一直长，最后拖慢备份、撑大文件。
     */
    @Query(
        "DELETE FROM ai_request_logs WHERE id NOT IN " +
            "(SELECT id FROM ai_request_logs ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}


@Dao
interface RecordingDigestDao {
    @Query("SELECT * FROM recording_digests WHERE recordingId = :recordingId")
    fun observe(recordingId: Long): Flow<RecordingDigest?>

    @Query("SELECT * FROM recording_digests WHERE recordingId = :recordingId")
    suspend fun get(recordingId: Long): RecordingDigest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(digest: RecordingDigest)

    @Query("DELETE FROM recording_digests WHERE recordingId = :recordingId")
    suspend fun delete(recordingId: Long)
}

@Dao
interface RecordingChatDao {
    @Query("SELECT * FROM recording_chats WHERE recordingId = :recordingId ORDER BY id ASC")
    fun observe(recordingId: Long): Flow<List<RecordingChat>>

    @Query("SELECT * FROM recording_chats WHERE recordingId = :recordingId ORDER BY id ASC")
    suspend fun getAll(recordingId: Long): List<RecordingChat>

    @Insert
    suspend fun insert(message: RecordingChat): Long

    @Query("DELETE FROM recording_chats WHERE recordingId = :recordingId")
    suspend fun clear(recordingId: Long)
}

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: Long): ChatSession?

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ChatSession?>

    @Insert
    suspend fun insert(session: ChatSession): Long

    @Update
    suspend fun update(session: ChatSession)

    @Query("UPDATE chat_sessions SET title = :title, note = :note, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, note: String?, now: Long)

    /** 自动生成标题时只动 title，别把用户写的备注一起覆盖掉 */
    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun setTitle(id: Long, title: String, now: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DirectMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeBySession(sessionId: Long): Flow<List<DirectMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getBySession(sessionId: Long): List<DirectMessage>

    @Insert
    suspend fun insert(message: DirectMessage): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int
}
