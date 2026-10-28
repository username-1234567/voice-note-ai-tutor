package com.suitanglu.app.ui.screens.playback

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * 离线 markdown + KaTeX(LaTeX 公式) 渲染，观感对齐 DeepSeek 网页端。
 * 高度按内容自适应（JS 上报 body 高度；CSS 像素与 dp 等值）。
 * 注意：页面加载是异步的，onPageFinished 前调用 render() 会被静默丢弃，
 * 因此在页面就绪时用最近一次的文本重发渲染，否则生成完成后内容直接空白。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var contentHeightDp by remember { mutableFloatStateOf(80f) }
    val state = remember { RenderState() }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    state.pageReady = true
                    state.pending?.let { pending ->
                        view.evaluateJavascript(
                            "window.render(${JSONObject.quote(pending.first)}, ${pending.second})", null,
                        )
                    }
                }
            }
            addJavascriptInterface(object {
                @JavascriptInterface
                fun reportHeight(h: Float) {
                    // JS 桥回调在 WebView 线程，Compose 状态必须回主线程写
                    mainHandler.post { contentHeightDp = h }
                }
            }, "android")
            loadDataWithBaseURL(
                "file:///android_asset/render/",
                context.assets.open("render/index.html").bufferedReader().use { it.readText() },
                "text/html", "utf-8", null,
            )
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .height(contentHeightDp.dp),
        factory = { webView },
        update = { wv ->
            // 只在文本/主题真的变了才重渲染：update 每次重组都会跑，
            // 无条件调用等于整块内容反复重建，看着闪
            val next = text to dark
            if (state.pending != next) {
                state.pending = next
                if (state.pageReady) {
                    wv.evaluateJavascript(
                        "window.render(${JSONObject.quote(text)}, $dark)", null,
                    )
                }
            }
        },
    )
}

/** 记录页面就绪状态与最近一次待渲染内容（跨回调传递） */
private class RenderState {
    var pageReady: Boolean = false
    var pending: Pair<String, Boolean>? = null
}
