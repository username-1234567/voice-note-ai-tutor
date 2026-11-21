package com.suitanglu.app.ui.screens.playback

import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.rememberUpdatedState
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
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    /** 按二级标题分节折叠：各级标题默认全部收起，点标题按需展开 */
    fold: Boolean = false,
    /**
     * 流式中（配合 fold=true）：最后一节保持展开——它正在被写进来，
     * 收起就看不到当前内容。生成结束时置 false，全部收起。
     */
    streaming: Boolean = false,
    /** 自测题点了某个选项（A/B/C/D）。null 表示这份内容不带选项 */
    onOption: ((String) -> Unit)? = null,
    /** 点了追问建议，参数是它在列表里的下标 */
    onSuggestion: ((Int) -> Unit)? = null,
    /** 高度变化做动画。流式内容每 300ms 长一截，开着会一直在追动画，反而显得卡 */
    animate: Boolean = true,
    /**
     * 是否让 WebView 自己接收触摸。
     * 放在固定高度的窗口里时要设 false：WebView 一旦吃掉竖向手势，外面的
     * Compose 滚动就再也收不到，表现就是"窗口滑不动"。这里的内容也不需要点。
     */
    touchable: Boolean = true,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var contentHeightDp by remember { mutableFloatStateOf(80f) }
    val state = remember { RenderState() }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // WebView 的 JS 接口只在首次组合时注册，必须通过 ref 拿到最新的回调
    val optionRef = rememberUpdatedState(onOption)
    val suggestionRef = rememberUpdatedState(onSuggestion)

    val webView = remember {
        // touchable=false 时把触摸整个让出去：WebView 不消费 DOWN，
        // 事件回到外层 Compose 的滚动容器，窗口才滑得动
        val view = if (touchable) WebView(context) else object : WebView(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean = false
            override fun onGenericMotionEvent(event: MotionEvent): Boolean = false
        }
        view.apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            // 跟随系统字体缩放：WebView 默认不保证按 fontScale 缩放正文，
            // 显式设 textZoom 才会和原生文字一起放大缩小（配置变化不实时刷新，重组时才生效，够用）
            settings.textZoom = (context.resources.configuration.fontScale * 100).toInt()
            isVerticalScrollBarEnabled = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    // 渲染容器只承载本地内容：外链（含 linkify 生成的裸链接）一律转交系统浏览器。
                    // 没有这道守卫的话，模型可控的链接会在无地址栏的回答卡片内导航——
                    // 应用主题还在、来源不明的页面就地展开，等于一个页内钓鱼面
                    if (request.url.toString().startsWith("file:///android_asset/render/")) return false
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    state.pageReady = true
                    state.pending?.let { p ->
                        view.evaluateJavascript(
                            "window.render(${JSONObject.quote(p.text)}, ${p.dark}, ${p.fold}, ${p.streaming})", null,
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

                @JavascriptInterface
                fun onOption(letter: String) {
                    mainHandler.post { optionRef.value?.invoke(letter) }
                }

                @JavascriptInterface
                fun onSuggestion(index: String) {
                    val i = index.toIntOrNull() ?: return
                    mainHandler.post { suggestionRef.value?.invoke(i) }
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
            .then(if (animate) Modifier.animateContentSize() else Modifier)
            .height(contentHeightDp.dp),
        factory = { webView },
        update = { wv ->
            // 只在文本/主题真的变了才重渲染：update 每次重组都会跑，
            // 无条件调用等于整块内容反复重建，看着闪
            val next = RenderState.Pending(text, dark, fold, streaming)
            if (state.pending != next) {
                state.pending = next
                if (state.pageReady) {
                    wv.evaluateJavascript(
                        "window.render(${JSONObject.quote(text)}, $dark, $fold, $streaming)", null,
                    )
                }
            }
        },
    )
}

/** 记录页面就绪状态与最近一次待渲染内容（跨回调传递） */
private class RenderState {
    data class Pending(val text: String, val dark: Boolean, val fold: Boolean, val streaming: Boolean)

    var pageReady: Boolean = false
    var pending: Pending? = null
}
