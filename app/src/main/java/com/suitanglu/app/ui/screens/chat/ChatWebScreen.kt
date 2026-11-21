package com.suitanglu.app.ui.screens.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.suitanglu.app.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

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
fun ChatWebScreen(onBack: () -> Unit) {
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

    val assetLoader = remember(ready) {
        val rawAssets = WebViewAssetLoader.AssetsPathHandler(context)
        WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.PathHandler { path ->
                    if (path == "chat/index.html") {
                        injectBoot(context, ready.apiKey, ready.thinking) ?: rawAssets.handle(path)
                    } else {
                        rawAssets.handle(path)
                    }
                },
            )
            .build()
    }

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
                    val multiple = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    runCatching {
                        if (multiple) pickMany.launch("*/*") else pickOne.launch("*/*")
                    }.onFailure {
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
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
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
 */
private fun injectBoot(
    context: android.content.Context,
    apiKey: String,
    thinking: Boolean,
): WebResourceResponse? = runCatching {
    val html = context.assets.open("chat/index.html").bufferedReader().use { it.readText() }
    val script = buildString {
        append("<script>try{")
        if (apiKey.isNotBlank()) {
            // Key 由应用设置管理：每次进来都以应用里的为准，避免两边不一致
            append("localStorage.setItem('chat-demo:api-key',")
            append(JSONObject.quote(apiKey))
            append(");")
        }
        // 语言和思考开关只在第一次给默认值：用户在网页里改过就尊重网页里的选择
        append("if(!localStorage.getItem('chat-demo:thinking'))")
        append("localStorage.setItem('chat-demo:thinking','")
        append(if (thinking) "true" else "false")
        append("');")
        // demo 默认按浏览器语言猜，WebView 里常是 en-US，与应用的中文界面不一致
        append("if(!localStorage.getItem('chat-demo:locale'))")
        append("localStorage.setItem('chat-demo:locale','zh-CN');")
        append("}catch(e){}</script>")
    }
    val patched = html.replaceFirst("<head>", "<head>$script")
    WebResourceResponse("text/html", "utf-8", patched.byteInputStream())
}.getOrNull()
