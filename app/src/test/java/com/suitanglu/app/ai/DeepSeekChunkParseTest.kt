package com.suitanglu.app.ai

import com.suitanglu.app.data.AiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * SSE 分片解析的回归测试。
 *
 * 起因：给 DeepSeek 加上 `stream_options.include_usage` 之后，**每个分片都带 usage 字段，
 * 除最后一个外全是 null**（DeepSeek 官方文档明确写了）。旧代码用 `obj["usage"]?.jsonObject`
 * 取，而 JSON 的 null 是 JsonNull 对象、不是 Kotlin null，`?.` 挡不住，取值器直接抛
 * IllegalArgumentException("Element class … is not a JsonObject")；
 * release 包被 R8 混淆后类名成了 `M5.u`，用户看到的错误提示就是那句天书。
 *
 * 所以这里把这几种「值是 null / 不是对象」的形态全部钉死。
 */
class DeepSeekChunkParseTest {

    private val client = DeepSeekClient(AiConfig())

    @Test
    fun `usage 为 null 的正常分片照常取到正文`() {
        val slice = client.parseChunk(
            """{"id":"1","choices":[{"index":0,"delta":{"content":"同学们好"},"finish_reason":null}],"usage":null}""",
        )
        assertNotNull(slice)
        assertEquals("同学们好", slice!!.text)
        assertNull(slice.usage)
    }

    @Test
    fun `最后一个分片的 usage 能读到`() {
        val slice = client.parseChunk(
            """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],
               "usage":{"prompt_tokens":1200,"prompt_cache_hit_tokens":800,"completion_tokens":300}}""",
        )
        assertEquals("stop", slice?.finishReason)
        assertEquals(1200, slice?.usage?.promptTokens)
        assertEquals(800, slice?.usage?.cacheHitTokens)
        assertEquals(300, slice?.usage?.completionTokens)
    }

    @Test
    fun `delta 为 null 时不抛异常`() {
        val slice = client.parseChunk(
            """{"choices":[{"index":0,"delta":null,"finish_reason":"stop"}],"usage":null}""",
        )
        assertEquals("", slice?.text)
        assertEquals("stop", slice?.finishReason)
    }

    @Test
    fun `error 为 null 不当作错误`() {
        val slice = client.parseChunk("""{"error":null,"choices":[{"delta":{"content":"嗯"}}]}""")
        assertEquals("嗯", slice?.text)
    }

    @Test
    fun `error 是对象时抛出它自己的提示`() {
        val e = assertThrows(IOException::class.java) {
            client.parseChunk("""{"error":{"message":"Insufficient Balance","type":"invalid_request_error"}}""")
        }
        assertEquals("Insufficient Balance", e.message)
    }

    @Test
    fun `error 是字符串时也要抛出可读提示`() {
        val e = assertThrows(IOException::class.java) {
            client.parseChunk("""{"error":"upstream timeout"}""")
        }
        assertEquals("upstream timeout", e.message)
    }

    @Test
    fun `非对象分片直接忽略`() {
        assertNull(client.parseChunk("\"keep-alive\""))
        assertNull(client.parseChunk("[1,2,3]"))
        assertNull(client.parseChunk("not json at all"))
    }

    @Test
    fun `content 是数组等异常形态时不崩`() {
        val slice = client.parseChunk("""{"choices":[{"delta":{"content":[{"type":"text"}]}}]}""")
        assertEquals("", slice?.text)
        assertTrue(slice != null)
    }
}
