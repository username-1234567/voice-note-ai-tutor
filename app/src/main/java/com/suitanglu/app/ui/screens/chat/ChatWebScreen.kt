package com.suitanglu.app.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewAssetLoader
import androidx.core.content.FileProvider
import com.suitanglu.app.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * 用 DeepSeek 官方 chat-demo（静态站）替换首页顶部的对话入口。
 *
 * 直接把 dist 丢进 assets 用 `file://` 打开是不行的：那个来源下 localStorage/IndexedDB
 * 常被禁用，异步 chunk 和字体的相对路径也不稳。WebViewAssetLoader 给它一个
 * `https://appassets.androidplatform.net` 的正常来源，存储与 fetch 都能按浏览器规则工作。
 */
private const val DOMAIN = "appassets.androidplatform.net"
private const val START_URL = "https://$DOMAIN/assets/chat/index.html"

/** 首帧要用的配置：API Key 与是否开启深度思考，取自应用自己的设置 */
data class ChatWebBoot(val apiKey: String, val thinking: Boolean)

/**
 * GetContent 只接受 MIME 型过滤条件：网页传来「image/*」这类就透传（相册只出图片），
 * 传来扩展名清单（.pdf,.doc…，文件按钮的白名单）时退回 */*，格式校验由网页端再做。
 */
private fun fileChooserMime(params: WebChromeClient.FileChooserParams): String =
    params.acceptTypes.firstOrNull { it.contains('/') } ?: "*/*"

class ChatWebViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppGraph.get(app)
    private val _boot = MutableStateFlow<ChatWebBoot?>(null)
    val boot: StateFlow<ChatWebBoot?> = _boot

    init {
        viewModelScope.launch {
            val ai = container.settings.aiConfigNow()
            _boot.value = ChatWebBoot(apiKey = ai.apiKey, thinking = ai.reasoningEffort != "off")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatWebScreen(mode: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ChatWebViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val boot by vm.boot.collectAsState()

    // Key 还没读出来就先别建 WebView：index.html 的注入脚本在建页那一刻就得带上它，
    // 否则 demo 会先弹一次「填 API Key」，用户还得在网页里再填一遍
    val ready = boot
    if (ready == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val loading = remember { mutableStateOf(true) }
    val fileCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // 附件选择：demo 的 <input type=file> 在 WebView 里必须由宿主接管，
    // 否则点「上传」什么也不会发生
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback.value?.onReceiveValue(if (uri != null) arrayOf(uri) else emptyArray())
        fileCallback.value = null
    }
    val pickMany =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            fileCallback.value?.onReceiveValue(uris.toTypedArray())
            fileCallback.value = null
        }

    // 网页里带 capture 的 <input type=file>（拍照）：调起系统相机把照片写进临时文件，
    // 结果以 FileProvider uri 回给网页。文件名带 .jpg，网页端按扩展名白名单校验才能过。
    val pendingCapture = remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture.value
        pendingCapture.value = null
        fileCallback.value?.onReceiveValue(if (ok && uri != null) arrayOf(uri) else null)
        fileCallback.value = null
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCapture.value
        if (!granted || uri == null) {
            pendingCapture.value = null
            fileCallback.value?.onReceiveValue(null)
            fileCallback.value = null
        } else {
            // pendingCapture 留给 TakePicture 的回调清
            takePicture.launch(uri)
        }
    }

    val assetLoader = remember(ready, mode) {
        val rawAssets = WebViewAssetLoader.AssetsPathHandler(context)
        WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.PathHandler { path ->
                    if (path == "chat/index.html") {
                        injectBoot(context, ready.apiKey, ready.thinking, mode)
                            ?: rawAssets.handle(path)
                    } else {
                        rawAssets.handle(path)
                    }
                },
            )
            .build()
    }

    // 网页里的「返回随堂录」按钮走这里回原生；用 rememberUpdatedState 是为了
    // 避免 remember 捕获到首次组合时的旧 lambda
    val backRef = rememberUpdatedState(onBack)

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            // IndexedDB（聊天记录、附件）与 localStorage（Key、主题）都靠这两个开关
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // 只暴露一个 back()：网页侧边栏顶部的「返回随堂录」。JS 在后台线程回调，
            // 必须 post 回主线程再导航
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun back() {
                        post { backRef.value() }
                    }
                },
                "Suitanglu",
            )

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (request.url.host == DOMAIN) return false
                    // 引用来源之类的站外链接交给系统浏览器，别把整个聊天页导航走
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading.value = false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    fileCallback.value?.onReceiveValue(null)
                    fileCallback.value = callback
                    runCatching {
                        when {
                            // 拍照入口：网页 input 带 capture。应用声明了 CAMERA 权限，
                            // 未授权时调起相机 intent 会直接 SecurityException，先申请
                            params.isCaptureEnabled -> {
                                val dir = File(context.cacheDir, "chat").apply { mkdirs() }
                                val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file,
                                )
                                pendingCapture.value = uri
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    takePicture.launch(uri)
                                } else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            }
                            params.mode == FileChooserParams.MODE_OPEN_MULTIPLE ->
                                pickMany.launch(fileChooserMime(params))
                            else -> pickOne.launch(fileChooserMime(params))
                        }
                    }.onFailure {
                        pendingCapture.value = null
                        fileCallback.value = null
                        callback.onReceiveValue(null)
                    }
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    // 只放行麦克风（语音输入），其余一律拒绝；应用本身已有录音权限
                    val audioOnly = request.resources.all {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    val granted = audioOnly && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) request.grant(request.resources) else request.deny()
                }
            }

            loadUrl(START_URL)
        }
    }

    BackHandler {
        // 页面内部有前进后退就退页面，否则退出这一屏
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    // 离开时停掉朗读/录音，别让声音在别的页面继续放
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            factory = { webView },
            // 应用是 edge-to-edge：不打这个内边距，网页自己的顶栏会钻到状态栏下面，
            // 输入框也会被手势条压住
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
        if (loading.value) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 在 index.html 的 <head> 里插一段启动脚本：把应用已保存的 Key、思考开关、语言
 * 直接写进 localStorage。demo 的设置模块在模块初始化时就读这些值，
 * 所以必须赶在 `defer` 的主脚本之前执行。
 * mode 是入口属性（默认聊天 / 学术研讨），每次进入都覆盖：同一 WebView 页面
 * 只承载一种模式的 System Prompt。
 */
private fun injectBoot(
    context: android.content.Context,
    apiKey: String,
    thinking: Boolean,
    mode: String?,
): WebResourceResponse? = runCatching {
    val html = context.assets.open("chat/index.html").bufferedReader().use { it.readText() }
    // 存储命名空间跟着入口模式走（demo 的 storageNs.ts 同款规则）：
    // 默认聊天与学术研讨的会话/附件/设置互不可见
    val ns = if (mode == "seminar") "chat-demo-seminar" else "chat-demo"
    val script = buildString {
        append("<script>try{")
        if (apiKey.isNotBlank()) {
            // Key 由应用设置管理：每次进来都以应用里的为准，避免两边不一致
            append("localStorage.setItem('$ns:api-key',")
            append(JSONObject.quote(apiKey))
            append(");")
        }
        // 语言和思考开关只在第一次给默认值：用户在网页里改过就尊重网页里的选择
        append("if(!localStorage.getItem('$ns:thinking'))")
        append("localStorage.setItem('$ns:thinking','")
        append(if (thinking) "true" else "false")
        append("');")
        // demo 默认按浏览器语言猜，WebView 里常是 en-US，与应用的中文界面不一致
        append("if(!localStorage.getItem('$ns:locale'))")
        append("localStorage.setItem('$ns:locale','zh-CN');")
        // 入口模式选择器：固定键名，非空即写入（目前只有 seminar），空则清掉回到默认身份
        if (mode.isNullOrBlank()) {
            append("localStorage.removeItem('chat-demo:mode');")
        } else {
            append("localStorage.setItem('chat-demo:mode',")
            append(JSONObject.quote(mode))
            append(");")
        }
        append("}catch(e){}</script>")
    }
    val patched = html.replaceFirst("<head>", "<head>$script")
    WebResourceResponse("text/html", "utf-8", patched.byteInputStream())
}.getOrNull()
