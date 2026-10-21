package com.suitanglu.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.suitanglu.app.ui.theme.rememberReducedMotion
import com.suitanglu.app.ui.screens.camera.InAppCameraScreen
import com.suitanglu.app.ui.screens.home.HomeScreen
import com.suitanglu.app.ui.screens.playback.PlaybackScreen
import com.suitanglu.app.ui.screens.recording.RecordingScreen
import com.suitanglu.app.ui.screens.recycle.RecycleBinScreen
import com.suitanglu.app.ui.screens.settings.AiSettingsScreen
import com.suitanglu.app.ui.screens.settings.DataSettingsScreen
import com.suitanglu.app.ui.screens.settings.ScheduleScreen
import com.suitanglu.app.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RECORDING = "recording"
    const val PLAYBACK = "playback/{recordingId}"
    const val SETTINGS = "settings"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_DATA = "settings/data"
    const val SCHEDULE = "schedule"
    const val RECYCLE = "recycle"
    const val CAMERA = "camera"

    fun playback(id: Long) = "playback/$id"
}

@Composable
fun RecordNavHost(
    hasRecordPermission: Boolean,
    hasCameraPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val nav = rememberNavController()
    // 页面切换：淡入 + 轻微横移。进入 360ms 落在"整页切换 350–500ms"的区间内，
    // 退出略短（240ms）——注意力在进入，离开要快。位移只有 1/12 屏宽，够表达方向又不至于像抽屉。
    // 系统关闭动画时直接到位。
    val reducedMotion = rememberReducedMotion()
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            if (reducedMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(360)) + slideInHorizontally(tween(360)) { it / 12 }
            }
        },
        exitTransition = {
            if (reducedMotion) ExitTransition.None else fadeOut(tween(240))
        },
        popEnterTransition = {
            if (reducedMotion) EnterTransition.None else fadeIn(tween(360))
        },
        popExitTransition = {
            if (reducedMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(240)) + slideOutHorizontally(tween(240)) { it / 12 }
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
            )
        }
        composable(Routes.SETTINGS_AI) {
            AiSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_DATA) {
            DataSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SCHEDULE) {
            ScheduleScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.RECYCLE) {
            RecycleBinScreen(onBack = { nav.popBackStack() })
        }
    }
}
