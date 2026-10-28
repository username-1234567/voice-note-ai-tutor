package com.suitanglu.app.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.data.GeneralConfig
import com.suitanglu.app.update.UpdateCheckResult
import com.suitanglu.app.update.UpdateInfo
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

    private val _backupResult = MutableStateFlow<String?>(null)
    val backupResult: StateFlow<String?> = _backupResult

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
                onSuccess = { "导出完成，共 $it 个文件" },
                onFailure = { "导出失败：${it.message}" },
            )
            _backupProgress.value = null
        }
    }

    fun importFrom(uri: Uri) {
        container.appScope.launch {
            val result = container.backupManager.importFrom(uri) { _backupProgress.value = it }
            _backupResult.value = result.fold(
                onSuccess = { it.message() },
                onFailure = { "导入失败：${it.message}" },
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

    fun clearTempFiles() {
        viewModelScope.launch {
            val appDir = getApplication<Application>().cacheDir
            File(appDir, "crops").deleteRecursively()
            File(appDir, "photos").deleteRecursively()
            File(appDir, "pending").deleteRecursively()
            refreshStorage()
            _backupResult.value = "临时文件已清理"
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

    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = when (val r = container.updateChecker.check()) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(r.current)
                is UpdateCheckResult.Available -> UpdateUiState.Available(r.info)
                is UpdateCheckResult.Error -> UpdateUiState.Error(r.message)
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
                onFailure = { UpdateUiState.Error("下载失败：${it.message}") },
            )
        }
    }

    fun installUpdate(file: File) {
        val ctx = getApplication<Application>()
        // 统一走 UpdateInstaller：它会在缺「安装未知应用」权限时跳授权页，
        // 而不是像原来那样直接 startActivity —— 那种情况下系统会静默拦下，
        // 用户看到的就是"点了没反应"
        when (val result = com.suitanglu.app.update.UpdateInstaller.install(ctx, file)) {
            is com.suitanglu.app.update.InstallResult.Started -> Unit
            is com.suitanglu.app.update.InstallResult.NeedPermission ->
                _updateState.value = UpdateUiState.Error("请先允许「安装未知应用」，返回后再点一次安装")
            is com.suitanglu.app.update.InstallResult.Failed ->
                _updateState.value = UpdateUiState.Error(result.message)
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
    data class ReadyToInstall(val file: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
