package com.suitanglu.app.ai

import com.suitanglu.app.data.AiConfig
import com.suitanglu.app.util.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

    /**
     * 只有官方端点认 thinking / reasoning_effort，其他 OpenAI 兼容服务下发会被拒。
     *
     * 判定与学术研讨入口、AI 配置页提示共用 [isOfficialDeepSeekEndpoint]（host 精确等于
     * api.deepseek.com），不再按 baseUrl 是否包含 "deepseek" 字样判断——中转地址常带
     * deepseek 字样却没有 thinking 能力，误下发会被 400。
     */
    private fun supportsThinking(): Boolean =
        isOfficialDeepSeekEndpoint(config.baseUrl)

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
     * onDelta 回传正文增量，onReasoning 回传思考过程增量（仅在 thinking 模式下有）；
     * 返回 完整回复 + token 用量。
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        // onDelta 放最后：现有的调用点都是尾随 lambda，参数顺序变了会全部报错
        onReasoning: ((String) -> Unit)? = null,
        onDelta: (String) -> Unit,
    ): Pair<String, ChatUsage> =
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
                    // DeepSeek 的流式接口默认不返回 usage，拿不到 prompt_tokens 的话
                    // 成本只能算到输出部分，价格会低一个数量级。
                    // 只对官方端点加：部分第三方兼容服务不认 stream_options，会直接 400。
                    if (config.stream && config.model.contains("deepseek", ignoreCase = true)) {
                        put(
                            "stream_options",
                            buildJsonObject { put("include_usage", true) },
                        )
                    }
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

            val call = client().newCall(request)
            val response = call.await()
            // 「停止生成」必须能立刻掐断读流：SSE 的 while 循环是阻塞 IO，不检查协程取消，
            // 只 cancel Job 的话会把整个回复读完才停——按钮看起来就是坏的。
            // 协程被取消时同步 cancel 这个 call，socket 断开让阻塞读取立刻抛 IOException，
            // readSse 再转成 CancellationException，上层走「保留已生成的部分文本」的落库路径
            val job = coroutineContext[Job]
            val cancelHook = job?.invokeOnCompletion { cause -> if (cause != null) runCatching { call.cancel() } }
            try {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val err = runCatching { resp.body?.string() }.getOrNull().orEmpty().take(500)
                        throw IOException("HTTP ${resp.code}: ${err.ifBlank { resp.message }}")
                    }
                    if (config.stream) {
                        readSse(response, job, onDelta, onReasoning)
                    } else {
                        // 非流式只在对端一次性返回完整 JSON，思考过程也只能整段回传
                        readFull(response, job, onReasoning)
                    }
                }
            } finally {
                cancelHook?.dispose()
            }
        }

    internal data class Slice(
        val text: String,
        val finishReason: String?,
        val usage: ChatUsage?,
        /** 思考过程增量。DeepSeek 的 thinking 模式会在 content 之前先吐 reasoning_content */
        val reasoning: String = "",
    )

    /** SSE 与非流式共用的分片解析：choices[0].delta/message.content + finish_reason + usage；error 分片抛出 */
    internal fun parseChunk(payload: String): Slice? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        val obj = root as? JsonObject ?: return null

        // 全部用 as? 取，不要用 ?.jsonObject / ?.jsonPrimitive。
        // 那两个取值器遇到类型不对会扔 IllegalArgumentException("Element class X is not a JsonObject")，
        // 而 X 是 R8 混淆后的类名；这条异常又被 failTurn 原样存成错误提示，
        // 用户看到的就是"element class M5.u is not a jsonobject"这种天书。
        // 触发场景很平常：{"error":null}、{"usage":null}、{"delta":null} 都是合法 JSON。
        val errorNode = obj["error"]
        if (errorNode != null && errorNode !is JsonNull) {
            val message = when (errorNode) {
                is JsonObject -> (errorNode["message"] as? JsonPrimitive)?.contentOrNull
                // 少数 OpenAI 兼容网关用 {"error":"字符串"} 报错
                is JsonPrimitive -> errorNode.contentOrNull
                else -> null
            }
            throw IOException(message?.takeIf { it.isNotBlank() } ?: "API 返回错误")
        }

        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val deltaObj = choice?.get("delta") as? JsonObject
        val messageObj = choice?.get("message") as? JsonObject
        val contentNode = deltaObj?.get("content") ?: messageObj?.get("content")
        val text = (contentNode as? JsonPrimitive)?.contentOrNull.orEmpty()
        // 思考过程：官方 thinking 模式下与 content 并列返回；不开思考时没有这个字段
        val reasoningNode = deltaObj?.get("reasoning_content") ?: messageObj?.get("reasoning_content")
        val reasoning = (reasoningNode as? JsonPrimitive)?.contentOrNull.orEmpty()
        val finishReason = (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull
        val usageObj = obj["usage"] as? JsonObject
        val usage = usageObj?.let {
            ChatUsage(
                promptTokens = (it["prompt_tokens"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
                cacheHitTokens = (it["prompt_cache_hit_tokens"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
                completionTokens = (it["completion_tokens"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
                finishReason = finishReason,
            )
        }
        return Slice(text, finishReason, usage, reasoning)
    }

    private fun readSse(
        response: Response,
        job: Job?,
        onDelta: (String) -> Unit,
        onReasoning: ((String) -> Unit)? = null,
    ): Pair<String, ChatUsage> {
        val source = response.body?.source() ?: throw IOException("空响应")
        val sb = StringBuilder()
        var usage: ChatUsage? = null
        // 协程被取消（用户点了停止生成）就立刻停手。转成 CancellationException 是刻意的：
        // AiSendManager 靠它走「保留已生成的部分文本」的取消落库路径，普通 IOException 会变成失败
        fun checkCancelled() {
            if (job != null && !job.isActive) throw CancellationException("已手动停止生成")
        }
        while (true) {
            checkCancelled()
            val line = try {
                source.readUtf8Line()
            } catch (e: IOException) {
                // call.cancel() 掐断 socket 也以 IOException 冒出来；确认确实是取消再转写，
                // 真正的网络错误（断网、超时）原样抛出走失败路径
                checkCancelled()
                throw e
            } ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val slice = parseChunk(payload) ?: continue
            if (slice.reasoning.isNotEmpty()) onReasoning?.invoke(slice.reasoning)
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

    private fun readFull(
        response: Response,
        job: Job?,
        onReasoning: ((String) -> Unit)? = null,
    ): Pair<String, ChatUsage> {
        if (job != null && !job.isActive) throw CancellationException("已手动停止生成")
        val text = try {
            response.body?.string().orEmpty()
        } catch (e: IOException) {
            // 与 readSse 同理：取消表现为 socket 断开的 IOException，确认后转写
            if (job != null && !job.isActive) throw CancellationException("已手动停止生成")
            throw e
        }
        val slice = parseChunk(text) ?: throw IOException("响应解析失败: ${text.take(300)}")
        if (slice.reasoning.isNotEmpty()) onReasoning?.invoke(slice.reasoning)
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
                    val root = json.parseToJsonElement(body) as? JsonObject
                    (root?.get("data") as? JsonArray)?.mapNotNull {
                        ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content
                    }
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
                            val obj = json.parseToJsonElement(bbody) as? JsonObject
                            val info = (obj?.get("balance_infos") as? JsonArray)?.firstOrNull() as? JsonObject
                            val total = (info?.get("total_balance") as? JsonPrimitive)?.contentOrNull
                            val currency = (info?.get("currency") as? JsonPrimitive)?.contentOrNull.orEmpty()
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
