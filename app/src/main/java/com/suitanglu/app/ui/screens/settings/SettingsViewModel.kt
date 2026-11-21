package com.suitanglu.app.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.data.GeneralConfig
import com.suitanglu.app.data.Formatters
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.update.InstallResult
import com.suitanglu.app.update.UpdateCheckResult
import com.suitanglu.app.update.UpdateInfo
import com.suitanglu.app.update.UpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)

    val appIcon: StateFlow<String> = container.settings.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.suitanglu.app.util.AppIconManager.INDIGO)

    fun saveAppIcon(alias: String) {
        viewModelScope.launch {
            container.settings.setAppIcon(alias)
            com.suitanglu.app.util.AppIconManager.apply(getApplication(), alias)
        }
    }

    val reminderConfig: StateFlow<Pair<Boolean, Int>> = container.settings.reminderConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false to 10)

    fun saveReminder(enabled: Boolean, leadMinutes: Int) {
        viewModelScope.launch {
            container.settings.setReminder(enabled, leadMinutes)
            runCatching {
                com.suitanglu.app.reminder.ReminderScheduler.reschedule(
                    getApplication(), container.settings,
                )
            }
        }
    }

    val aiConfig: StateFlow<AiConfig> = container.settings.aiConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiConfig())

    val generalConfig: StateFlow<GeneralConfig> = container.settings.generalConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeneralConfig())

    private val _aiTestResult = MutableStateFlow<String?>(null)
    val aiTestResult: StateFlow<String?> = _aiTestResult

    private val _aiTesting = MutableStateFlow(false)
    val aiTesting: StateFlow<Boolean> = _aiTesting

    private val _backupProgress = MutableStateFlow<String?>(null)
    val backupProgress: StateFlow<String?> = _backupProgress

    private val _backupResult = MutableStateFlow<BackupUiResult?>(null)
    val backupResult: StateFlow<BackupUiResult?> = _backupResult

    private val _storageUsage = MutableStateFlow<Pair<Long, Long>?>(null)
    val storageUsage: StateFlow<Pair<Long, Long>?> = _storageUsage

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState

    init {
        refreshStorage()
    }

    // 保存走 appScope 而不是 viewModelScope：点完保存立刻按返回时，
    // viewModelScope 会随页面销毁而取消，DataStore 写入可能被丢掉——
    // 表现就是"明明点了保存，回来发现没生效"。同文件里的导入导出也是这个理由。
    fun saveAi(config: AiConfig) {
        container.appScope.launch { container.settings.setAiConfig(config) }
    }

    fun saveGeneral(config: GeneralConfig) {
        container.appScope.launch { container.settings.setGeneralConfig(config) }
    }

    /** 测试连接 = 先保存界面上的配置，再用同一份配置测试，一步到位 */
    fun saveAndTestAi(config: AiConfig) {
        container.appScope.launch { container.settings.setAiConfig(config) }
        viewModelScope.launch {
            _aiTesting.value = true
            _aiTestResult.value = try {
                com.suitanglu.app.ai.DeepSeekClient(config).testConnection()
            } catch (e: Exception) {
                "失败：${e.message}"
            }
            _aiTesting.value = false
        }
    }

    fun clearAiTest() {
        _aiTestResult.value = null
    }

    // 导出/导入是长耗时阻塞操作，放在 appScope：离开设置页不会取消，
    // 操作一定会完整落盘；结果仍写回本 VM 状态，留在本页时可见
    fun exportTo(uri: Uri) {
        container.appScope.launch {
            val result = container.backupManager.exportTo(uri) { _backupProgress.value = it }
            _backupResult.value = result.fold(
                onSuccess = { BackupUiResult.Success("导出完成，共 $it 个文件") },
                onFailure = { BackupUiResult.Failure("导出失败：${it.message}") },
            )
            _backupProgress.value = null
        }
    }

    fun importFrom(uri: Uri) {
        container.appScope.launch {
            val result = container.backupManager.importFrom(uri) { _backupProgress.value = it }
            _backupResult.value = result.fold(
                onSuccess = { BackupUiResult.Success(it.message()) },
                onFailure = { BackupUiResult.Failure("导入失败：${it.message}") },
            )
            _backupProgress.value = null
        }
    }

    fun consumeBackupResult() {
        _backupResult.value = null
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storageUsage.value = container.recordingRepository.storageUsage()
        }
    }

    /**
     * 清理临时缓存：裁剪输出（crops）、拍摄原图（photos）、待发送暂存（pending）、
     * 网页版拍照缓存（chat，ChatWebScreen 的 TakePicture 临时文件）。
     *
     * 录音会话进行中时，pending 里压着标记面板正在编辑的图片（RecordingViewModel
     * 的 _pendingImages 指向这里），删掉会让正在编辑的照片失效——录音中整个目录跳过。
     * 返回给界面的释放量按被删目录的实际字节数计算（storageUsage 只统计
     * recordings/images 两个目录，不含 cache，用它的差值会永远是 0）。
     */
    fun clearTempFiles() {
        viewModelScope.launch {
            val appDir = getApplication<Application>().cacheDir
            val recording = RecordingBus.state.value != null
            val dirs = buildList {
                add(File(appDir, "crops"))
                add(File(appDir, "photos"))
                add(File(appDir, "chat"))
                if (!recording) add(File(appDir, "pending"))
            }
            val freed = dirs.sumOf { dir ->
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
                size
            }
            refreshStorage()
            _backupResult.value = BackupUiResult.Success(
                if (recording) {
                    "已释放 ${Formatters.bytes(freed)}；录音进行中，待发送暂存已保留"
                } else {
                    "临时文件已清理，释放 ${Formatters.bytes(freed)}"
                },
            )
        }
    }

    fun requestIgnoreBatteryOptimization() {
        val ctx = getApplication<Application>()
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openAppWhitelistSettings() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 直达本应用的通知设置页（API 26+ 可用；minSdk 29，无需低版本回退分支） */
    fun openNotificationSettings() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = when (val r = container.updateChecker.check()) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(r.current)
                is UpdateCheckResult.Available -> UpdateUiState.Available(r.info)
                // 失败来源记进状态里：重试按钮按来源重发，而不是一律重新检查
                is UpdateCheckResult.Error -> UpdateUiState.Error(r.message) { checkUpdate() }
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(0)
            val result = container.updateChecker.download(info) { pct ->
                _updateState.value = UpdateUiState.Downloading(pct)
            }
            _updateState.value = result.fold(
                onSuccess = { UpdateUiState.ReadyToInstall(it) },
                // 记住失败来自下载：重试 = 用同一条版本信息重新下载
                onFailure = { UpdateUiState.Error("下载失败：${it.message}") { downloadUpdate(info) } },
            )
        }
    }

    fun installUpdate(file: File) {
        val ctx = getApplication<Application>()
        // 统一走 UpdateInstaller：它会在缺「安装未知应用」权限时跳授权页，
        // 而不是像原来那样直接 startActivity —— 那种情况下系统会静默拦下，
        // 用户看到的就是"点了没反应"
        when (val result = UpdateInstaller.install(ctx, file)) {
            is InstallResult.Started -> Unit
            is InstallResult.NeedPermission ->
                // 缺权限不再吃掉 ReadyToInstall 态：已下载的安装包还在，界面上方
                // 提示去授权；授权回来直接重按「安装更新」，不用重新下载
                _updateState.value = when (val cur = _updateState.value) {
                    is UpdateUiState.ReadyToInstall -> cur.copy(needPermission = true)
                    else -> cur
                }
            is InstallResult.Failed ->
                // 记住失败来自安装：重试 = 对同一个文件重新拉起安装器
                _updateState.value = UpdateUiState.Error(result.message) { installUpdate(file) }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class UpToDate(val version: String) : UpdateUiState()
    data class Available(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val percent: Int) : UpdateUiState()

    /** needPermission：缺「安装未知应用」权限、已跳授权页；文件保留，回来直接重按安装 */
    data class ReadyToInstall(val file: File, val needPermission: Boolean = false) : UpdateUiState()

    /** retry 记住失败来源：检查失败→重新检查、下载失败→重新下载、安装失败→重新安装 */
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UpdateUiState()
}

/** 导出/导入/清理的结果：成败用类型表达，UI 不再用字符串 contains 猜 */
sealed class BackupUiResult {
    data class Success(val message: String) : BackupUiResult()
    data class Failure(val message: String) : BackupUiResult()
}
