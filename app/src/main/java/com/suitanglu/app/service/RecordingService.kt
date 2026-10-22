package com.suitanglu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.suitanglu.app.R
import com.suitanglu.app.RecordApp
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {

    companion object {
        /** 心跳间隔，静音累计按这个粒度加 */
        private const val HEARTBEAT_MS = 500L

        /** 峰值低于此值视为"没声音"（满量程 32767，约 3%） */
        private const val QUIET_AMPLITUDE = 1000

        /** 连续安静多久提醒一次 */
        private const val QUIET_ALERT_MS = 60_000L

        const val ACTION_START = "com.suitanglu.app.action.START"
        const val ACTION_PAUSE = "com.suitanglu.app.action.PAUSE"
        const val ACTION_RESUME = "com.suitanglu.app.action.RESUME"
        const val ACTION_STOP = "com.suitanglu.app.action.STOP"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, RecordingService::class.java).apply { this.action = action })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: MediaRecorder? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var timerJob: Job? = null
    private var recordingId: Long = 0
    private var startTime: Long = 0
    private var segmentStart: Long = 0
    private var accumulatedMs: Long = 0
    private var paused = false
    private var interrupted = false
    private var interruptReason: String? = null

    /** 连续安静的累计时长（每 500ms 心跳累加） */
    private var quietMs = 0L

    /** 本轮安静是否已提醒过；声音恢复后重置，允许下一次再提醒 */
    private var quietAlerted = false
    private var sizeLimitBytes = 0L
    private val busy = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    /** 录音尚在启动时收到停止请求：启动完成后立即停止 */
    @Volatile private var stopRequested = false

    /** 录音尚在启动时收到用户暂停请求：启动完成后执行 */
    @Volatile private var pauseRequested = false

    /**
     * 用户是"主动"暂停的。
     *
     * 与 [interrupted]（被来电/其他应用打断）区分开：音频焦点恢复时只自动续录"被打断"的录音。
     * 没有这个标志时会出现——用户按下暂停 → 来了条语音 → 焦点恢复 → 录音自己又开始了。
     */
    @Volatile private var userPaused = false

    /** 收尾期间收到新的开始请求：收尾完成后自动开始新录音 */
    @Volatile private var restartAfterStop = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseByUser()
            ACTION_RESUME -> resumeByUser()
            ACTION_STOP -> stopRecording()
        }
        // 进程被杀后的恢复由 RecordApp.recoverUnfinishedRecordings 负责，服务不需要系统拉起
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "录音状态", NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startRecording() {
        if (recorder != null) return
        if (!busy.compareAndSet(false, true)) {
            // 上一段录音收尾期间收到开始请求：收尾完成后自动开始，避免请求被静默丢弃
            if (recorder == null && stopping.get()) restartAfterStop = true
            return
        }
        stopping.set(false)
        stopRequested = false
        pauseRequested = false
        userPaused = false
        RecordingBus.clearStartError()
        val container = (application as RecordApp).container
        scope.launch {
            try {
                audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                sizeLimitBytes = container.settings.generalConfigNow().sizeLimitMb.takeIf { it > 0 }
                    ?.let { it.toLong() * 1024 * 1024 } ?: 0L

                startTime = System.currentTimeMillis()
                val rec = container.recordingRepository.startRecording(startTime)
                recordingId = rec.id
                File(rec.filePath).parentFile?.mkdirs()

                // 先建局部引用再赋值给属性：apply 里 prepare()/start() 抛异常时，
                // 赋值语句根本不会执行，那个 MediaRecorder 就永远没人 release——
                // 麦克风被占用导致 prepare 失败时，之后每次录音都会跟着失败
                val mr = MediaRecorder(applicationContext)
                try {
                    mr.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        // 设备支持的最高音质：AAC 48kHz 立体声 256kbps
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(48000)
                        setAudioEncodingBitRate(256000)
                        setAudioChannels(2)
                        setOutputFile(rec.filePath)
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    runCatching { mr.release() }
                    throw e
                }
                recorder = mr
                segmentStart = System.currentTimeMillis()
                requestAudioFocus()
                RecordingBus.update(
                    RecordingState(
                        recordingId = recordingId,
                        title = rec.title,
                        filePath = rec.filePath,
                        startTime = startTime,
                        elapsedMs = 0,
                        paused = false,
                        interrupted = false,
                    ),
                )
                startForegroundCompat(buildNotification(0, false))
                startTimer()
                if (stopRequested) {
                    stopRecording()
                    return@launch
                }
                if (pauseRequested) {
                    pauseRequested = false
                    doPause(PauseEvent.TYPE_USER_PAUSE, null)
                }
            } catch (e: Exception) {
                busy.set(false)
                releaseRecorder()
                // startForegroundService 启动后必须先进入前台再停止
                runCatching { startForegroundCompat(buildNotification(0, true)) }
                // 启动失败：清掉预占的数据库行和残留文件，避免幽灵录音 / 0 字节孤儿文件
                runCatching {
                    if (recordingId > 0) container.recordingRepository.discardRecording(recordingId)
                }
                RecordingBus.update(null)
                RecordingBus.setStartError("录音启动失败：${e.message ?: e.javaClass.simpleName}")
                delay(300)
                stopSelf()
            }
        }
    }

    /**
     * 安静检测：老师走到后排、或者手机被书挡住，录出来是一整段静音，
     * 而这种事往往要回放时才发现——那时课已经上完了。
     * 连续 60 秒峰值低于 3% 提醒一次；声音回来后重置，允许之后再提醒。
     */
    private fun checkSilence(active: Boolean, amplitude: Int) {
        if (!active) {
            // 暂停期间不计入，恢复后从头计时——否则"静音 50 秒 → 暂停一节课 → 恢复 10 秒"会立刻误报
            quietMs = 0
            return
        }
        if (amplitude < QUIET_AMPLITUDE) {
            quietMs += HEARTBEAT_MS
            if (quietMs >= QUIET_ALERT_MS && !quietAlerted) {
                quietAlerted = true
                RecordingBus.setLevelWarning("已经 1 分钟几乎没有声音，看看手机是不是被挡住了")
            }
        } else {
            quietMs = 0
            quietAlerted = false
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var lastDbSave = 0L
            var lastSizeCheck = 0L
            var lastNotify = 0L
            while (isActive && recorder != null) {
                val now = System.currentTimeMillis()
                val elapsed = if (!paused) accumulatedMs + (now - segmentStart) else accumulatedMs
                // maxAmplitude 读取后峰值归零，所以整个心跳只能读一次：
                // 声纹环和静音检测都吃这一份原始值
                val rawAmplitude = if (paused || interrupted) {
                    0
                } else {
                    runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                }
                checkSilence(!paused && !interrupted, rawAmplitude)
                RecordingBus.update(
                    currentState().copy(
                        elapsedMs = elapsed,
                        paused = paused,
                        interrupted = interrupted,
                        interruptReason = interruptReason,
                        level = (rawAmplitude / 32767f).coerceIn(0f, 1f),
                    ),
                )
                if (now - lastNotify > 5_000) {
                    // 通知栏时长定期刷新
                    runCatching { updateNotification() }
                    lastNotify = now
                }
                if (now - lastDbSave > 10_000 && !stopping.get()) {
                    // 进程被杀时用于恢复；收尾开始后不再写，避免覆盖最终时长
                    (application as RecordApp).container.db.recordingDao()
                        .updateDuration(recordingId, elapsed, now)
                    lastDbSave = now
                }
                if (sizeLimitBytes > 0 && now - lastSizeCheck > 5_000) {
                    val size = File(currentState().filePath).length()
                    if (size >= sizeLimitBytes) {
                        // 停止流程统一回主线程执行，避免与暂停/恢复并发操作 recorder
                        mainHandler.post { stopRecording() }
                        return@launch
                    }
                    lastSizeCheck = now
                }
                delay(500)
            }
        }
    }

    private fun currentState(): RecordingState =
        RecordingBus.state.value ?: RecordingState(recordingId, "", "", startTime, 0, paused, interrupted)

    private fun pauseByUser() {
        doPause(PauseEvent.TYPE_USER_PAUSE, null)
    }

    private fun doPause(type: String, reason: String?) {
        // 先记账再判状态：已经因打断暂停时用户再按暂停，也说明"用户希望保持暂停"
        if (type == PauseEvent.TYPE_USER_PAUSE) userPaused = true
        val rec = recorder
        if (rec == null) {
            // 录音尚在启动：记住用户暂停请求，启动完成后执行
            if (type == PauseEvent.TYPE_USER_PAUSE) pauseRequested = true
            return
        }
        if (paused) return
        try {
            rec.pause()
            val now = System.currentTimeMillis()
            accumulatedMs += now - segmentStart
            paused = true
            if (type == PauseEvent.TYPE_AUTO_INTERRUPT) interrupted = true
            interruptReason = reason
            publishState()
            scope.launch {
                (application as RecordApp).container.recordingRepository.insertPauseEvent(
                    recordingId, accumulatedMs, type, reason,
                )
            }
            updateNotification()
        } catch (_: Exception) {
        }
    }

    private fun resumeByUser() {
        doResume()
    }

    /** 恢复（用户手动或焦点恢复）：两者都视为打断结束 */
    private fun doResume() {
        val rec = recorder ?: return
        if (!paused) return
        try {
            rec.resume()
            paused = false
            userPaused = false
            segmentStart = System.currentTimeMillis()
            interrupted = false
            interruptReason = null
            publishState()
            scope.launch {
                (application as RecordApp).container.recordingRepository.insertPauseEvent(
                    recordingId, accumulatedMs, PauseEvent.TYPE_RESUME, null,
                )
            }
            updateNotification()
        } catch (_: Exception) {
        }
    }

    /** 暂停/打断状态同步到 UI 共享流，否则录音页与通知栏永远看不到暂停态、也无法恢复 */
    private fun publishState() {
        RecordingBus.update(
            currentState().copy(
                paused = paused,
                interrupted = interrupted,
                interruptReason = interruptReason,
            ),
        )
    }

    private fun stopRecording() {
        val rec = recorder
        if (rec == null) {
            // 服务尚未完成启动：记录请求，启动流程完成后立即停止（不能此时 stopSelf，FGS 必须先进前台）
            stopRequested = true
            return
        }
        if (!stopping.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        val elapsed = if (!paused) accumulatedMs + (now - segmentStart) else accumulatedMs
        val id = recordingId
        val interruptedFlag = interrupted
        // 立刻把属性置空再把对象交给后台释放：
        // 否则 release 还没跑完时 startRecording 会看到 recorder != null 而直接 return，
        // 用户点"开始"就没反应了
        val toRelease = recorder
        recorder = null
        releaseFocus()
        timerJob?.cancel()
        // 释放动作挪到后台：MediaRecorder.stop() 要把 MP4 的 moov 写完，
        // 90 分钟的录音可能卡几百毫秒到几秒；这条路径是 onStartCommand 直接调的，
        // 留在主线程就是实打实的 ANR 风险
        scope.launch {
            try {
                toRelease?.stop()
            } catch (_: Exception) {
            }
            try {
                toRelease?.release()
            } catch (_: Exception) {
            }
            RecordingBus.update(null)
            // 本轮结束，静音累计与未消费的提醒都清掉，避免下一段录音一开就弹
            quietMs = 0
            quietAlerted = false
            RecordingBus.clearLevelWarning()
            try {
                val container = (application as RecordApp).container
                container.recordingRepository.finalizeRecording(id, now, elapsed, interruptedFlag)
                // 不再自动发送：拍下来的课件往往还要在课后裁一下再发，
                // 自动发送会把"原图"直接送给 AI，改图就白花钱了。
                // 发送入口在回放页（有未发送标记时会显示「发送全部」）。
            } catch (_: Exception) {
            }
            if (restartAfterStop) {
                restartAfterStop = false
                busy.set(false)
                startRecording()
            } else {
                stopSelf()
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> doPause(
                        PauseEvent.TYPE_AUTO_INTERRUPT,
                        when (change) {
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "其他应用短暂占用音频"
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "来电或其他应用打断"
                            else -> "音频焦点丢失"
                        },
                    )
                    // 焦点被别处抢走后再还回来，只续录"被打断"的那种；
                    // 用户手动按的暂停不该被系统悄悄撤销
                    AudioManager.AUDIOFOCUS_GAIN -> if (!userPaused) doResume()
                }
            }
            .build()
        am.requestAudioFocus(focusRequest!!)
    }

    private fun releaseFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val s = currentState()
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, buildNotification(s.elapsedMs, s.paused),
        )
    }

    private fun buildNotification(elapsedMs: Long, isPaused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseOrResume = if (isPaused) {
            NotificationCompat.Action(
                R.drawable.ic_notif_play, "继续",
                actionPendingIntent(ACTION_RESUME, 1),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_notif_pause, "暂停",
                actionPendingIntent(ACTION_PAUSE, 2),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(if (isPaused) "录音已暂停" else "正在录音")
            .setContentText(formatElapsed(elapsedMs))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(pauseOrResume)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notif_stop, "停止",
                    actionPendingIntent(ACTION_STOP, 3),
                ),
            )
            .build()
    }

    private fun actionPendingIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, RecordingService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatElapsed(ms: Long): String {
        val total = ms / 1000
        return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
    }

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        releaseFocus()
        releaseRecorder()
        RecordingBus.update(null)
        super.onDestroy()
    }
}
