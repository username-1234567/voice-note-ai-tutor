package com.suitanglu.app.ai

/**
 * 是不是官方 DeepSeek 端点。只看 host 是否等于 api.deepseek.com：
 * https://api.deepseek.com/v1、带尾斜杠、带自定义路径都算官方。
 *
 * 内置 chat-demo 走 /anthropic 兼容协议，还依赖官方的联网搜索工具，
 * 第三方中转基本没有这两样，硬接过去只会一直报错。
 * 首页对话入口与学术研讨入口共用这一个判定，显隐口径必须一致。
 */
fun isOfficialDeepSeekEndpoint(baseUrl: String): Boolean {
    val raw = baseUrl.trim()
    if (raw.isEmpty()) return false
    val normalized = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
    val host = runCatching { java.net.URI(normalized).host }.getOrNull()
    return host?.equals("api.deepseek.com", ignoreCase = true) == true
}
