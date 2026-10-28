package com.suitanglu.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

@kotlinx.serialization.Serializable
data class AiConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-flash",
    val stream: Boolean = true,
    val timeoutSeconds: Int = 180,
    val maxTokens: Int = 0,
    val prompt: String = "详细讲解内容",
    /**
     * 思考强度：off / low / high / max。
     *
     * off 会显式下发 `thinking: disabled`（服务端默认是开启的，不发参数等于没关）；
     * 其余三档下发 `thinking: enabled` + `reasoning_effort`。
     * 讲解课件照片属于"看图说清楚"，low 通常就够，max 留给推导密集的科目。
     */
    val reasoningEffort: String = "high",
)

@kotlinx.serialization.Serializable
data class GeneralConfig(
    val cameraMode: String = CameraMode.SYSTEM,
    val compressImages: Boolean = false,
    val compressQuality: Int = 80,
    val sizeLimitMb: Int = 0,
    val firstDeleteTipShown: Boolean = false,
)

object CameraMode {
    const val SYSTEM = "system"
    const val IN_APP = "in_app"
}

/** 单个课程时间段（分钟数从 00:00 起算） */
data class DaySlotTime(val index: Int, val startMinute: Int, val endMinute: Int) {
    fun label(): String = "%02d:%02d–%02d:%02d".format(
        startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60,
    )
}

class SettingsRepository(private val context: Context) {

    private object K {
        val aiKey = stringPreferencesKey("ai_api_key")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiModel = stringPreferencesKey("ai_model")
        val aiStream = booleanPreferencesKey("ai_stream")
        val aiTimeout = intPreferencesKey("ai_timeout_sec")
        val aiMaxTokens = intPreferencesKey("ai_max_tokens")
        val aiPrompt = stringPreferencesKey("ai_prompt")
        val aiReasoningEffort = stringPreferencesKey("ai_reasoning_effort")
        val cameraMode = stringPreferencesKey("camera_mode")
        val compressImages = booleanPreferencesKey("compress_images")
        val compressQuality = intPreferencesKey("compress_quality")
        val sizeLimitMb = intPreferencesKey("size_limit_mb")
        val onboarded = booleanPreferencesKey("onboarded")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderLead = intPreferencesKey("reminder_lead_min")
        val appIcon = stringPreferencesKey("app_icon")
        val dismissedUpdate = stringPreferencesKey("dismissed_update_version")
        val firstDeleteTipShown = booleanPreferencesKey("first_delete_tip_shown")
        fun slot(day: Int, slot: Int) = stringPreferencesKey("course_${day}_$slot")
        fun slotStart(day: Int, slot: Int) = intPreferencesKey("slot_${day}_${slot}_start")
        fun slotEnd(day: Int, slot: Int) = intPreferencesKey("slot_${day}_${slot}_end")
    }

    companion object {
        /** 默认五个固定时间段 */
        val DEFAULT_SLOTS = listOf(
            DaySlotTime(1, 8 * 60, 9 * 60 + 40),
            DaySlotTime(2, 10 * 60, 11 * 60 + 40),
            DaySlotTime(3, 14 * 60 + 30, 16 * 60),
            DaySlotTime(4, 16 * 60 + 10, 17 * 60 + 40),
            DaySlotTime(5, 19 * 60, 22 * 60),
        )
    }

    val aiConfig: Flow<AiConfig> = context.dataStore.data.map { p ->
        AiConfig(
            apiKey = p[K.aiKey] ?: "",
            baseUrl = p[K.aiBaseUrl] ?: "https://api.deepseek.com",
            model = p[K.aiModel] ?: "deepseek-flash",
            stream = p[K.aiStream] ?: true,
            timeoutSeconds = p[K.aiTimeout] ?: 180,
            maxTokens = p[K.aiMaxTokens] ?: 0,
            prompt = p[K.aiPrompt] ?: "详细讲解内容",
            reasoningEffort = p[K.aiReasoningEffort] ?: "high",
        )
    }

    val generalConfig: Flow<GeneralConfig> = context.dataStore.data.map { p ->
        GeneralConfig(
            cameraMode = p[K.cameraMode] ?: CameraMode.SYSTEM,
            compressImages = p[K.compressImages] ?: false,
            compressQuality = p[K.compressQuality] ?: 80,
            sizeLimitMb = p[K.sizeLimitMb] ?: 0,
            firstDeleteTipShown = p[K.firstDeleteTipShown] ?: false,
        )
    }

    fun slotsFlow(day: Int): Flow<List<DaySlotTime>> = context.dataStore.data.map { p ->
        readSlots(p, day)
    }

    suspend fun slotsNow(day: Int): List<DaySlotTime> =
        readSlots(context.dataStore.data.first(), day)

    private fun readSlots(p: androidx.datastore.preferences.core.Preferences, day: Int): List<DaySlotTime> =
        (1..5).map { i ->
            val def = DEFAULT_SLOTS[i - 1]
            DaySlotTime(
                index = i,
                startMinute = p[K.slotStart(day, i)] ?: def.startMinute,
                endMinute = p[K.slotEnd(day, i)] ?: def.endMinute,
            )
        }

    suspend fun setSlotTime(day: Int, index: Int, startMinute: Int, endMinute: Int) {
        context.dataStore.edit { p ->
            p[K.slotStart(day, index)] = startMinute
            p[K.slotEnd(day, index)] = endMinute
        }
    }

    fun courseName(day: Int, slot: Int): Flow<String> =
        context.dataStore.data.map { p -> p[K.slot(day, slot)] ?: "" }

    suspend fun courseNameNow(day: Int, slot: Int): String =
        courseName(day, slot).first()

    /** 所有已配置的课程名（去重、去空），供重命名时反查归属用 */
    suspend fun allCourseNamesNow(): List<String> =
        allCourseNames().first().values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** 是否走完新手引导 */
    suspend fun isOnboarded(): Boolean = context.dataStore.data.first()[K.onboarded] ?: false

    suspend fun setOnboarded() {
        context.dataStore.edit { it[K.onboarded] = true }
    }

    /** 上课提醒：开关 + 提前分钟数 */
    val reminderConfig: Flow<Pair<Boolean, Int>> = context.dataStore.data.map { p ->
        (p[K.reminderEnabled] ?: false) to (p[K.reminderLead] ?: 10)
    }

    suspend fun reminderConfigNow(): Pair<Boolean, Int> = reminderConfig.first()

    /** 当前启动图标的 alias 名 */
    val appIcon: Flow<String> = context.dataStore.data.map { p ->
        p[K.appIcon] ?: com.suitanglu.app.util.AppIconManager.INDIGO
    }

    suspend fun appIconNow(): String = appIcon.first()

    suspend fun setAppIcon(alias: String) {
        context.dataStore.edit { it[K.appIcon] = alias }
    }

    /**
     * 用户点过"稍后"的版本号。
     *
     * 只记一个版本而不是布尔值：下一个版本发布时应该重新提醒一次，
     * 用布尔开关的话用户点一次"稍后"就永久收不到任何更新提示了。
     */
    val dismissedUpdateVersion: Flow<String> = context.dataStore.data.map { p ->
        p[K.dismissedUpdate].orEmpty()
    }

    suspend fun dismissedUpdateVersionNow(): String = dismissedUpdateVersion.first()

    suspend fun dismissUpdateVersion(version: String) {
        context.dataStore.edit { it[K.dismissedUpdate] = version }
    }

    suspend fun setReminder(enabled: Boolean, leadMinutes: Int) {
        context.dataStore.edit {
            it[K.reminderEnabled] = enabled
            it[K.reminderLead] = leadMinutes.coerceIn(1, 60)
        }
    }

    fun allCourseNames(): Flow<Map<Pair<Int, Int>, String>> =
        context.dataStore.data.map { p ->
            p.asMap().mapNotNull { (k, v) ->
                val key = k.name
                if (key.startsWith("course_")) {
                    val parts = key.removePrefix("course_").split("_")
                    val day = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val slot = parts[1].toIntOrNull() ?: return@mapNotNull null
                    (day to slot) to (v as? String ?: "")
                } else null
            }.toMap()
        }

    suspend fun setAiConfig(config: AiConfig) {
        context.dataStore.edit { p ->
            p[K.aiKey] = config.apiKey
            p[K.aiBaseUrl] = config.baseUrl
            p[K.aiModel] = config.model
            p[K.aiStream] = config.stream
            p[K.aiTimeout] = config.timeoutSeconds
            p[K.aiMaxTokens] = config.maxTokens
            p[K.aiPrompt] = config.prompt
            p[K.aiReasoningEffort] = config.reasoningEffort
        }
    }

    suspend fun setGeneralConfig(config: GeneralConfig) {
        context.dataStore.edit { p ->
            p[K.cameraMode] = config.cameraMode
            p[K.compressImages] = config.compressImages
            p[K.compressQuality] = config.compressQuality
            p[K.sizeLimitMb] = config.sizeLimitMb
            p[K.firstDeleteTipShown] = config.firstDeleteTipShown
        }
    }

    suspend fun setCourseName(day: Int, slot: Int, name: String) {
        context.dataStore.edit { it[K.slot(day, slot)] = name }
    }

    suspend fun setAllCourseNames(names: Map<Pair<Int, Int>, String>) {
        context.dataStore.edit { p ->
            for (d in 1..7) for (s in 1..5) p[K.slot(d, s)] = names[d to s] ?: ""
        }
    }

    suspend fun aiConfigNow(): AiConfig = aiConfig.first()
    suspend fun generalConfigNow(): GeneralConfig = generalConfig.first()
}
