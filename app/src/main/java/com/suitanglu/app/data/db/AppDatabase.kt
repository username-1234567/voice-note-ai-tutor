package com.suitanglu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Recording::class,
        Marker::class,
        MarkerImage::class,
        PauseEvent::class,
        AiRequestLog::class,
        MarkerTurn::class,
        RecordingDigest::class,
        RecordingChat::class,
        ChatSession::class,
        DirectMessage::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun markerDao(): MarkerDao
    abstract fun markerImageDao(): MarkerImageDao
    abstract fun pauseEventDao(): PauseEventDao
    abstract fun aiRequestLogDao(): AiRequestLogDao
    abstract fun markerTurnDao(): MarkerTurnDao
    abstract fun recordingDigestDao(): RecordingDigestDao
    abstract fun recordingChatDao(): RecordingChatDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun directMessageDao(): DirectMessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2：新增 marker_turns 多轮追问表（历史数据保留）；
         *  已有成功回复的老标记回填为 turn 0，在新对话 UI 中可见、可重新生成 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `marker_turns` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`markerId` INTEGER NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`question` TEXT, " +
                        "`answer` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`error` TEXT, " +
                        "`promptTokens` INTEGER NOT NULL, " +
                        "`cacheHitTokens` INTEGER NOT NULL, " +
                        "`completionTokens` INTEGER NOT NULL, " +
                        "`finishReason` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_turns_markerId` ON `marker_turns` (`markerId`)")
                db.execSQL(
                    "INSERT INTO marker_turns " +
                        "(markerId, orderIndex, question, answer, status, error, promptTokens, cacheHitTokens, completionTokens, finishReason, createdAt, updatedAt) " +
                        "SELECT id, 0, NULL, aiResponse, 'success', NULL, 0, 0, 0, NULL, createdAt, updatedAt " +
                        "FROM markers WHERE aiStatus = 'success' AND aiResponse IS NOT NULL",
                )
            }
        }

        /** v2 → v3：标记新增待复习星标；新增整节汇总与整节对话两张表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `markers` ADD COLUMN `needsReview` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_digests` (" +
                        "`recordingId` INTEGER NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recordingId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_chats` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recording_chats_recordingId` " +
                        "ON `recording_chats` (`recordingId`)",
                )
            }
        }

        /** v3 → v4：新增全局对话（会话 + 消息）两张表 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`imagePaths` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                // 实体上有 indices = [Index("sessionId")]，这里必须同步建，
                // 少一条 Room 就会在启动时判定 schema 不匹配
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                        "ON `chat_messages` (`sessionId`)",
                )
            }
        }

        /**
         * 全部迁移，按顺序。
         *
         * 导入备份时也要带上：备份文件可能来自旧版本（v1/v2），
         * 用 createFromFile 打开却没注册迁移的话，Room 会直接抛
         * "A migration from 2 to 3 was required but not found"，用户看到的就是"导入失败"。
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "app.db",
            ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
        }
    }
}
