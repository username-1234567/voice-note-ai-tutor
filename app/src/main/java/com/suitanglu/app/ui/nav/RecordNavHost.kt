package com.suitanglu.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.suitanglu.app.AppGraph
import com.suitanglu.app.ai.isOfficialDeepSeekEndpoint
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.suitanglu.app.ui.theme.MotionSpec
import com.suitanglu.app.ui.theme.rememberReducedMotion
import com.suitanglu.app.ui.screens.camera.InAppCameraScreen
import com.suitanglu.app.ui.screens.chat.ChatListScreen
import com.suitanglu.app.ui.screens.chat.ChatScreen
import com.suitanglu.app.ui.screens.chat.ChatWebScreen
import com.suitanglu.app.ui.screens.home.HomeScreen
import com.suitanglu.app.ui.screens.onboarding.OnboardingGate
import com.suitanglu.app.ui.screens.render.RenderScreen
import com.suitanglu.app.ui.screens.playback.PlaybackScreen
import com.suitanglu.app.ui.screens.recording.RecordingScreen
import com.suitanglu.app.ui.screens.recycle.RecycleBinScreen
import com.suitanglu.app.ui.screens.settings.AboutScreen
import com.suitanglu.app.ui.screens.settings.AiSettingsScreen
import com.suitanglu.app.ui.screens.settings.DataSettingsScreen
import com.suitanglu.app.ui.screens.settings.RecordingSettingsScreen
import com.suitanglu.app.ui.screens.settings.ScheduleScreen
import com.suitanglu.app.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RECORDING = "recording"
    const val PLAYBACK = "playback/{recordingId}"
    const val SETTINGS = "settings"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_RECORDING = "settings/recording"
    const val SETTINGS_ABOUT = "settings/about"
    const val SCHEDULE = "schedule"
    const val RECYCLE = "recycle"
    const val CAMERA = "camera"
    const val RENDER = "render"
    const val CHAT_LIST = "chats"
    const val CHAT = "chats/{sessionId}"
    /** 首页顶部的对话入口：内嵌官方 chat-demo 静态页；mode 可选（seminar = 学术研讨） */
    const val CHAT_WEB = "chat/web?mode={mode}"

    fun chatWeb(mode: String? = null): String =
        if (mode.isNullOrBlank()) "chat/web" else "chat/web?mode=$mode"

    fun playback(id: Long) = "playback/$id"
    fun chat(sessionId: Long) = "chats/$sessionId"
}

@Composable
fun RecordNavHost(
    hasRecordPermission: Boolean,
    hasCameraPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
) {
    OnboardingGate {
    val nav = rememberNavController()
    // 页面切换：淡入 + 轻微横移。进入 320ms，退出 210ms（约 65%，离开要比进入快）；
    // 位移只有 1/12 屏宽，够表达方向又不至于像抽屉。
    // 退出挂 40ms 延迟：新页面先动、旧页面后走，两层不会同时"用力"。
    // 系统关闭动画时直接到位。
    val reducedMotion = rememberReducedMotion()

    // 首页对话入口走哪条链路，取决于 API 地址：内置网页版只对接官方端点
    // （它用的是 /anthropic 兼容协议与联网搜索工具），第三方中转没有这些，退回原生聊天
    val context = LocalContext.current
    val settings = remember(context) {
        AppGraph.get(context.applicationContext as Application).settings
    }
    val aiConfig by settings.aiConfig.collectAsState(initial = null)
    val officialEndpoint = aiConfig?.baseUrl?.let(::isOfficialDeepSeekEndpoint) == true

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            if (reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(MotionSpec.PAGE_ENTER_MS, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        tween(MotionSpec.PAGE_ENTER_MS, easing = FastOutSlowInEasing),
                    ) { it / 12 }
            }
        },
        exitTransition = {
            if (reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(MotionSpec.PAGE_EXIT_MS, delayMillis = MotionSpec.FOLLOW_MS))
            }
        },
        popEnterTransition = {
            if (reducedMotion) {
                EnterTransition.None
            } else {
                // 返回时从左侧进来：方向和"进入"相反，用户才知道自己是往回走
                fadeIn(tween(MotionSpec.PAGE_ENTER_MS, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(
                        tween(MotionSpec.PAGE_ENTER_MS, easing = FastOutSlowInEasing),
                    ) { -it / 14 }
            }
        },
        popExitTransition = {
            if (reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(MotionSpec.PAGE_EXIT_MS, delayMillis = MotionSpec.FOLLOW_MS)) +
                    slideOutHorizontally(
                        tween(MotionSpec.PAGE_EXIT_MS, easing = FastOutSlowInEasing),
                    ) { it / 12 }
            }
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = onRequestPermissions,
                onStartRecording = {
                    if (hasRecordPermission) {
                        nav.navigate(Routes.RECORDING)
                    } else {
                        onRequestPermissions()
                    }
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenRecording = { id -> nav.navigate(Routes.playback(id)) },
                onOpenRecycle = { nav.navigate(Routes.RECYCLE) },
                onOpenChat = {
                    nav.navigate(if (officialEndpoint) Routes.chatWeb() else Routes.CHAT_LIST)
                },
                onOpenSeminar = { nav.navigate(Routes.chatWeb("seminar")) },
            )
        }
        composable(Routes.RECORDING) {
            RecordingScreen(
                hasCameraPermission = hasCameraPermission,
                onRequestPermissions = onRequestPermissions,
                onFinished = { nav.popBackStack() },
                onOpenInAppCamera = { nav.navigate(Routes.CAMERA) },
            )
        }
        composable(Routes.CAMERA) {
            InAppCameraScreen(
                hasCameraPermission = hasCameraPermission,
                onRequestPermissions = onRequestPermissions,
                onCaptured = { nav.popBackStack() },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            Routes.PLAYBACK,
            arguments = listOf(navArgument("recordingId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("recordingId") ?: 0L
            PlaybackScreen(recordingId = id, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenSchedule = { nav.navigate(Routes.SCHEDULE) },
                onOpenAi = { nav.navigate(Routes.SETTINGS_AI) },
                onOpenData = { nav.navigate(Routes.SETTINGS_DATA) },
                onOpenRecording = { nav.navigate(Routes.SETTINGS_RECORDING) },
                onOpenAbout = { nav.navigate(Routes.SETTINGS_ABOUT) },
            )
        }
        composable(Routes.SETTINGS_AI) {
            AiSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_DATA) {
            DataSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_RECORDING) {
            RecordingSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.RENDER) {
            RenderScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Routes.CHAT_WEB,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ChatWebScreen(
                mode = entry.arguments?.getString("mode"),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onBack = { nav.popBackStack() },
                onOpenSession = { id -> nav.navigate(Routes.chat(id)) },
            )
        }
        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            ChatScreen(
                sessionId = entry.arguments?.getLong("sessionId") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.RECYCLE) {
            RecycleBinScreen(onBack = { nav.popBackStack() })
        }
    }
    }
}
