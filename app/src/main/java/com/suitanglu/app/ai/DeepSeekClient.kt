package com.suitanglu.app.ai

import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class ChatPart(val type: String, val text: String? = null, val imageUrl: String? = null)

/** 一轮对话消息：content 为纯文本，images 仅在带图消息上使用（转 data URI） */
data class ChatMessage(
    val role: String,
    val text: String,
    val imageFiles: List<File> = emptyList(),
)

/** 一次对话的统计信息 */
data class ChatUsage(
    val promptTokens: Int,
    val cacheHitTokens: Int,
    val completionTokens: Int,
    val finishReason: String?,
)

class DeepSeekClient(
    private val config: AiConfig,
    private val compressImages: Boolean = false,
    private val compressQuality: Int = 80,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun base(): String = config.baseUrl.trimEnd('/')

    private fun chatUrl(): String {
        val b = base()
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    /** 只有官方端点认 thinking / reasoning_effort，其他 OpenAI 兼容服务下发会被拒 */
    private fun supportsThinking(): Boolean =
        base().contains("deepseek", ignoreCase = true)

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.coerceAtLeast(30).toLong(), TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 单条消息的 content：纯文本或 文本+多图（data URI） */
    private fun messageContent(message: ChatMessage) = buildJsonArray {
        add(buildJsonObject { put("type", "text"); put("text", message.text) })
        for ((bytes, mime) in message.imageFiles.map { ImageUtil.loadForUpload(it, compressImages, compressQuality) }) {
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", "data:$mime;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}")
                        },
                    )
                },
            )
        }
    }

    /**
     * 多轮对话请求。带图的首条消息逐字节稳定（同文件 → 同 base64），
     * 配合历史轮次按序拼接可命中 DeepSeek 上下文缓存，追问成本大幅降低。
     * onDelta 回传增量文本；返回 完整回复 + token 用量。
     */
    suspend fun chat(messages: List<ChatMessage>, onDelta: (String) -> Unit): Pair<String, ChatUsage> =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("model", config.model.ifBlank { "deepseek-flash" })
                    put(
                        "messages",
                        buildJsonArray {
                            for (m in messages) {
                                add(
                                    buildJsonObject {
                                        put("role", m.role)
                                        if (m.imageFiles.isEmpty()) {
                                            put("content", m.text)
                                        } else {
                                            put("content", messageContent(m))
                                        }
                                    },
                                )
                            }
                        },
                    )
                    put("stream", config.stream)
                    // 思考强度：只在官方端点上发。
                    // 第三方 OpenAI 兼容服务大多不认 thinking / reasoning_effort，硬塞会被 400 掉，
                    // 所以按 host 判断，而不是让用户再配一个"兼容模式"开关。
                    // 注意 thinking 默认是开启的——想关必须显式下发 disabled，不发参数等于没关。
                    if (supportsThinking()) {
                        val effort = config.reasoningEffort.lowercase()
                        put(
                            "thinking",
                            buildJsonObject { put("type", if (effort == "off") "disabled" else "enabled") },
                        )
                        if (effort != "off") put("reasoning_effort", effort)
                    }
                    // 0 = 不限制（模型按自身上限输出），避免长回复被截断
                    if (config.maxTokens > 0) put("max_tokens", config.maxTokens.coerceAtLeast(256))
                },
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(chatUrl())
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", if (config.stream) "text/event-stream" else "application/json")
                .post(body)
                .build()

            val response = client().newCall(request).await()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val err = runCatching { resp.body?.string() }.getOrNull().orEmpty().take(500)
                    throw IOException("HTTP ${resp.code}: ${err.ifBlank { resp.message }}")
                }
                if (config.stream) {
                    readSse(response, onDelta)
                } else {
                    readFull(response)
                }
            }
        }

    private data class Slice(val text: String, val finishReason: String?, val usage: ChatUsage?)

    /** SSE 与非流式共用的分片解析：choices[0].delta/message.content + finish_reason + usage；error 分片抛出 */
    private fun parseChunk(payload: String): Slice? {
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        obj["error"]?.jsonObject?.let { e ->
            throw IOException(e["message"]?.jsonPrimitive?.contentOrNull ?: "API 返回错误")
        }
        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val contentNode = choice?.get("delta")?.jsonObject?.get("content")
            ?: choice?.get("message")?.jsonObject?.get("content")
        val text = contentNode?.jsonPrimitive?.contentOrNull.orEmpty()
        val usageObj = obj["usage"]?.jsonObject
        val usage = usageObj?.let {
            ChatUsage(
                promptTokens = it["prompt_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                cacheHitTokens = it["prompt_cache_hit_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                completionTokens = it["completion_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                finishReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull,
            )
        }
        return Slice(text, choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull, usage)
    }

    private fun readSse(response: Response, onDelta: (String) -> Unit): Pair<String, ChatUsage> {
        val source = response.body?.source() ?: throw IOException("空响应")
        val sb = StringBuilder()
        var usage: ChatUsage? = null
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val slice = parseChunk(payload) ?: continue
            if (slice.text.isNotEmpty()) {
                sb.append(slice.text)
                onDelta(slice.text)
            }
            if (slice.usage != null) usage = slice.usage
        }
        val result = usage ?: ChatUsage(0, 0, sb.length / 4, null)
        if (sb.isEmpty() && result.completionTokens == 0) throw IOException("AI 返回了空回复")
        return sb.toString() to result
    }

    private fun readFull(response: Response): Pair<String, ChatUsage> {
        val text = response.body?.string().orEmpty()
        val slice = parseChunk(text) ?: throw IOException("响应解析失败: ${text.take(300)}")
        if (slice.text.isEmpty()) throw IOException("AI 返回了空回复")
        return slice.text to (slice.usage ?: ChatUsage(0, 0, slice.text.length / 4, slice.finishReason))
    }

    /** 测试连接：GET /models；成功后附带账户余额（DeepSeek 专有接口，其他服务自动跳过） */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/models")
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        val response = client().newCall(request).await()
        response.use { resp ->
            val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
            if (resp.isSuccessful) {
                val models = runCatching {
                    json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                }.getOrNull().orEmpty()
                var msg = "连接成功，可用模型：${models.joinToString("、").ifBlank { "(未返回列表)" }}"
                runCatching {
                    val breq = Request.Builder()
                        .url("${base()}/user/balance")
                        .header("Authorization", "Bearer ${config.apiKey}")
                        .get()
                        .build()
                    client().newCall(breq).execute().use { bresp ->
                        val bbody = bresp.body?.string().orEmpty()
                        if (bresp.isSuccessful) {
                            val obj = json.parseToJsonElement(bbody).jsonObject
                            val info = obj["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
                            val total = info?.get("total_balance")?.jsonPrimitive?.contentOrNull
                            val currency = info?.get("currency")?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (total != null) msg += "\n账户余额：$currency $total"
                        }
                    }
                }
                msg
            } else {
                "失败 HTTP ${resp.code}: ${body.take(300).ifBlank { resp.message }}"
            }
        }
    }

    suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
