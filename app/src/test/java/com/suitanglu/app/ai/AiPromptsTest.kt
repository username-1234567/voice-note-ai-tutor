package com.suitanglu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptsTest {

    @Test
    fun `上下文块带上课程 时间 备注和图片数`() {
        val ctx = AiPrompts.contextBlock(
            courseName = "高等数学",
            title = "第 3 讲 矩阵的秩",
            shots = listOf(Shot(absoluteTime = 1_700_000_000_000, offsetMs = 37 * 60_000L)),
            imageCount = 3,
            notes = listOf("老师说这个定理必考"),
        )
        assertTrue(ctx.contains("课程：高等数学"))
        assertTrue(ctx.contains("录音：第 3 讲 矩阵的秩"))
        assertTrue(ctx.contains("课堂第 37 分"))
        assertTrue(ctx.contains("学生备注：老师说这个定理必考"))
        assertTrue(ctx.contains("共 3 张"))
        // 必须明确告诉模型"没听过录音"，否则它会假装知道老师讲了什么
        assertTrue(ctx.contains("没有听过这段录音"))
    }

    @Test
    fun `合并的多处时间点逐个列出`() {
        val ctx = AiPrompts.contextBlock(
            courseName = null,
            title = "第 1 讲",
            shots = listOf(
                Shot(1_700_000_000_000, 10 * 60_000L),
                Shot(1_700_000_300_000, 15 * 60_000L),
            ),
            imageCount = 5,
            notes = emptyList(),
            rangeEndOffsetMs = 15 * 60_000L,
        )
        assertTrue(ctx.contains("第 1 处"))
        assertTrue(ctx.contains("第 2 处"))
        assertTrue(ctx.contains("共 5 张"))
        assertTrue(!ctx.contains("学生备注"))
    }

    @Test
    fun `默认模式不追加任何说明`() {
        assertEquals("", AiPrompts.modeTask(AiPrompts.MODE_AUTO))
        assertTrue(AiPrompts.modeTask(AiPrompts.MODE_STEPS).contains("逐步"))
        assertTrue(AiPrompts.modeTask(AiPrompts.MODE_CHECK).contains("笔记"))
    }

    @Test
    fun `附加块拆出来后正文干净`() {
        val raw = """
            这里的秩不变，因为初等行变换是可逆的。

            ${AiPrompts.SUGGEST_OPEN}
            1. 为什么初等行变换不改变秩？
            2. 三种行变换分别有什么用？
            3. 行等价和等价矩阵是一回事吗？
            ${AiPrompts.QUIZ_OPEN}
            矩阵经初等行变换后，秩会怎样？
            A. 一定相等
            B. 一定变小
            C. 一定变大
            D. 无法确定
            答案：A
            解析：初等行变换对应可逆矩阵左乘，不改变秩。
            ${AiPrompts.EXTRA_END}
        """.trimIndent()

        val parsed = AiPrompts.splitExtras(raw)
        assertEquals("这里的秩不变，因为初等行变换是可逆的。", parsed.answer)
        assertEquals(3, parsed.extra?.suggestions?.size)
        assertEquals("为什么初等行变换不改变秩？", parsed.extra?.suggestions?.first())
        assertEquals("A", parsed.extra?.quiz?.answer)
        assertEquals(4, parsed.extra?.quiz?.options?.size)
        assertTrue(parsed.extra?.quiz?.explain.orEmpty().contains("可逆矩阵"))
    }

    @Test
    fun `没有附加块时整段都是正文`() {
        val raw = "这里只讲一个公式：\n\\[ a^2 + b^2 = c^2 \\]"
        val parsed = AiPrompts.splitExtras(raw)
        assertEquals(raw, parsed.answer)
        assertNull(parsed.extra)
    }

    @Test
    fun `模型忘了写结束标记也能拆`() {
        val raw = """
            正文到此结束。

            ${AiPrompts.SUGGEST_OPEN}
            1. 第一个问题？
            2. 第二个问题？
        """.trimIndent()
        val parsed = AiPrompts.splitExtras(raw)
        assertEquals("正文到此结束。", parsed.answer)
        assertEquals(2, parsed.extra?.suggestions?.size)
        assertNull(parsed.extra?.quiz)
    }

    @Test
    fun `自测题缺少答案就丢掉 但追问建议保留`() {
        val raw = """
            正文。

            ${AiPrompts.SUGGEST_OPEN}
            1. 问题一？
            ${AiPrompts.QUIZ_OPEN}
            题干
            A. 选项一
            B. 选项二
            ${AiPrompts.EXTRA_END}
        """.trimIndent()
        val parsed = AiPrompts.splitExtras(raw)
        assertEquals("正文。", parsed.answer)
        assertEquals(1, parsed.extra?.suggestions?.size)
        assertNull(parsed.extra?.quiz)
    }

    @Test
    fun `模型把标签加粗也能解析`() {
        val raw = """
            正文。

            ${AiPrompts.SUGGEST_OPEN}
            1. **为什么可以用这个定理？**
            ${AiPrompts.QUIZ_OPEN}
            **这道题的秩是多少？**
            **A.** 1
            **B.** 2
            **答案：B**
            **解析：** 行阶梯形有两个非零行。
            ${AiPrompts.EXTRA_END}
        """.trimIndent()

        val parsed = AiPrompts.splitExtras(raw)
        assertEquals("为什么可以用这个定理？", parsed.extra?.suggestions?.first())
        assertEquals("B", parsed.extra?.quiz?.answer)
        assertEquals(2, parsed.extra?.quiz?.options?.size)
        assertTrue(parsed.extra?.quiz?.explain.orEmpty().contains("非零行"))
    }

    @Test
    fun `流式显示在标记出现时截断`() {
        assertEquals("正文", AiPrompts.visibleStream("正文"))
        assertEquals("正文", AiPrompts.visibleStream("正文\n${AiPrompts.SUGGEST_OPEN}\n1. 问题"))
        // 标记只到了半个前缀：也要先藏起来，避免屏幕上闪一下又消失
        assertEquals("正文", AiPrompts.visibleStream("正文\n<<<FOLL"))
    }

    @Test
    fun `旧默认提示词升级为新默认`() {
        assertEquals(AiPrompts.DEFAULT_TASK, AiPrompts.normalizeTaskPrompt("详细讲解内容"))
        assertEquals(AiPrompts.DEFAULT_TASK, AiPrompts.normalizeTaskPrompt("  "))
        assertEquals(AiPrompts.DEFAULT_TASK, AiPrompts.normalizeTaskPrompt(null))
        assertEquals("请用英文讲", AiPrompts.normalizeTaskPrompt("请用英文讲"))
    }

    @Test
    fun `两套系统提示词都不为空且口径不同`() {
        val general = AiPrompts.systemPrompt(AiPrompts.STYLE_GENERAL)
        val math = AiPrompts.systemPrompt(AiPrompts.STYLE_MATH)
        assertTrue(general.contains("学科"))
        assertTrue(math.contains("数学"))
        assertTrue(general != math)
    }
}
