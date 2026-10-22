package com.suitanglu.app

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import com.suitanglu.app.ai.AiSendManager
import com.suitanglu.app.backup.BackupManager
import com.suitanglu.app.data.SettingsRepository
import com.suitanglu.app.data.db.AppDatabase
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.recording.RecordingRepository
import com.suitanglu.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.get(app)
    val settings: SettingsRepository = SettingsRepository(app)
    val recordingRepository = RecordingRepository(app, db, settings)
    val aiSendManager = AiSendManager(app, db, settings)
    val backupManager = BackupManager(app, db, settings)
    val updateChecker = UpdateChecker(app)
}

object AppGraph {
    @Volatile private var container: AppContainer? = null

    fun init(app: Application): AppContainer =
        container ?: synchronized(this) {
            container ?: AppContainer(app).also { container = it }
        }

    fun get(context: Context): AppContainer =
        container ?: init(context.applicationContext as Application)
}

class RecordApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppGraph.init(this)
        recoverUnfinishedRecordings()
    }

    /** 进程被杀后，启动时恢复未完成录音 */
    private fun recoverUnfinishedRecordings() {
        container.appScope.launch {
            val dao = container.db.recordingDao()
            // 进程被杀时中断的 AI 发送置为失败，避免永远卡在“发送中”
            container.db.markerDao().resetSending(System.currentTimeMillis())
            // 请求日志每次生成都会写一条，只在启动时收一次，避免数据库无限膨胀
            runCatching { container.db.aiRequestLogDao().trim(200) }
            val unfinished = dao.getUnfinished()
            for (rec in unfinished) {
                val markers = container.db.markerDao().countByRecording(rec.id)
                val fixed = rec.copy(
                    status = if (markers > 0) Recording.STATUS_INTERRUPTED else Recording.STATUS_COMPLETED,
                    endTime = rec.startTime + rec.durationMs,
                    updatedAt = System.currentTimeMillis(),
                )
                container.db.withTransaction {
                    dao.update(fixed)
                }
            }
            // 只在收尾调一次：放进循环里的话，恢复 3 条会通知 4 次
            if (unfinished.isNotEmpty()) {
                // 与正常停止一致：恢复出来的录音也**不**自动发送。
                // 标记留在待发送状态，用户在回放页确认图片后手动发。
                container.recordingRepository.notifyRecovered(unfinished.size)
            }
        }
    }
}
