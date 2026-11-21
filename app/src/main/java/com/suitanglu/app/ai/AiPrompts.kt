package com.suitanglu.app.ai

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一道自测题：题干 + 选项 + 正确答案字母 + 一句话解析 */
@Serializable
data class Quiz(
    val question: String,
    val options: List<String>,
    val answer: String,
    val explain: String,
)

/** 讲解正文之外附带的内容。解析失败时整块为 null，正文照常保存。 */
@Serializable
data class TurnExtra(
    val suggestions: List<String> = emptyList(),
    val quiz: Quiz? = null,
)

/** 拆分结果：answer 是要显示/存库的正文，extra 是附加块 */
data class ParsedAnswer(val answer: String, val extra: TurnExtra?)

/** 一张图在课堂里的位置 */
data class Shot(val absoluteTime: Long, val offsetMs: Long)

/**
 * 提示词都由这里出。
 *
 * 分层的原因：system 消息必须逐字不变才能命中 DeepSeek 的上下文缓存
 * （缓存按前缀匹配，改一个字后面的全部失效）。所以身份、教学法、图片规则、
 * 公式格式这些长期不变的东西一律放 system；课程名、时间、备注、模式这些
 * 每次都不同的东西放 user。用户改设置里的提示词时，只动 user 那一层。
 */
object AiPrompts {

    /** 通用助教：跨学科，按内容自行判断学科口径 */
    const val STYLE_GENERAL = "general"

    /** 数学 Pro：面向数学专业初学者，严格推导 */
    const val STYLE_MATH = "math"

    const val MODE_AUTO = "auto"
    const val MODE_STEPS = "steps"
    const val MODE_INTUITION = "intuition"
    const val MODE_CHECK = "check"

    // 附加块的分隔标记。选全大写尖括号是因为数学/文史正文里几乎不可能自然出现，
    // 又比中文标记更不容易被模型改写成全角。
    const val SUGGEST_OPEN = "<<<FOLLOWUPS>>>"
    const val QUIZ_OPEN = "<<<QUIZ>>>"
    const val EXTRA_END = "<<<END>>>"

    /**
     * 默认讲解任务（用户提示词）。
     *
     * 旧版本这里是「详细讲解内容」五个字——模型只能靠猜，讲解质量全看运气。
     * 这段写清了三件事：讲什么、按什么顺序讲、不要做什么。
     */
    val DEFAULT_TASK: String = """
        请讲解我拍的这页课堂内容。

        先告诉我这页在讲什么，再按图中的顺序讲清概念、公式和推导，解释每一步为什么成立。
        遇到抽象的地方，用一个简单例子或直观解释帮我理解。
        最后指出初学者容易出错的地方，并做个小结。

        不要只把图上的字复述一遍，重点讲图里没写出来的思路。
        如果图片有看不清的地方，直接说哪里看不清，不要编。
    """.trimIndent()

    /** 旧版本的默认值。用户在设置里没动过它时，升级后自动换成新的默认任务。 */
    const val LEGACY_DEFAULT_TASK = "详细讲解内容"

    fun normalizeTaskPrompt(stored: String?): String {
        val v = stored?.trim().orEmpty()
        return if (v.isEmpty() || v == LEGACY_DEFAULT_TASK) DEFAULT_TASK else stored!!
    }

    fun systemPrompt(style: String): String =
        if (style == STYLE_MATH) SYSTEM_MATH else SYSTEM_GENERAL

    /** 模式说明追加在用户消息里，不进 system——换模式不该让缓存整段失效 */
    fun modeTask(mode: String): String = when (mode) {
        MODE_STEPS -> """
            【这次的重点：逐步推导】
            从第一个公式或结论开始，逐步说明每一次变形：
            这一步做了什么、为什么可以这样做、用了什么定义/性质/定理、需要满足什么条件。
            不要跳过中间步骤。
        """.trimIndent()

        MODE_INTUITION -> """
            【这次的重点：直观理解】
            先不急着推导。用简单例子、几何意义、特殊情况或图像，
            讲清这个概念为什么存在、它到底描述了什么，最后再回到严格定义。
        """.trimIndent()

        MODE_CHECK -> """
            【这次的重点：笔记检查】
            检查图片里的公式、推导和证明：公式是否正确、步骤是否合理、
            用到的定理是否满足条件、有没有遗漏的逻辑步骤、有没有容易误导的写法。
            请区分「明确错误」「可能有问题」「只是不够严谨」。
            图片不清晰时不要轻易判定错误。
        """.trimIndent()

        else -> ""
    }

    /**
     * 追问建议与自测题的附加指令。
     *
     * 放在用户消息末尾而不是 system：关掉这个开关时只需换用户消息，
     * system 前缀保持不变，历史对话的缓存照样命中。
     */
    fun extrasInstruction(enabled: Boolean): String {
        if (!enabled) return ""
        return """
            【附加任务】
            正文全部写完后，另起一行，严格按下面的格式输出。不要在正文里提到这些标记，也不要解释这个格式：

            $SUGGEST_OPEN
            1. 学生最可能追问的问题（针对本次讲解里最容易卡住的那一步）
            2. 另一个值得追问的问题（可以联系前置知识或后续内容）
            3. 一个能检验是否真的懂了的问题
            $QUIZ_OPEN
            题干（一道单选题，只考本次讲解最核心的一点）
            A. 选项
            B. 选项
            C. 选项
            D. 选项
            答案：A
            解析：一句话说明为什么
            $EXTRA_END
        """.trimIndent()
    }

    /**
     * 课堂上下文。这些是模型无法从图片里看出来的信息，
     * 也是「AI 知道你拍的是第几分钟」这件事唯一真正落地的地方。
     */
    fun contextBlock(
        courseName: String?,
        title: String,
        shots: List<Shot>,
        imageCount: Int,
        notes: List<String>,
        rangeEndOffsetMs: Long? = null,
    ): String {
        val first = shots.minByOrNull { it.offsetMs }
        val last = shots.maxByOrNull { it.offsetMs }
        val sb = StringBuilder("【课堂记录】\n")

        val course = courseName?.trim().orEmpty()
        if (course.isNotEmpty()) sb.append("课程：").append(course).append('\n')
        if (title.isNotBlank()) sb.append("录音：").append(title.trim()).append('\n')

        if (first != null) {
            val endMs = rangeEndOffsetMs ?: last?.offsetMs ?: first.offsetMs
            if (endMs > first.offsetMs) {
                // 终点时钟按「起点时刻 + 时长」算：合并后只剩组首一个标记，
                // 直接拿它的 absoluteTime 会得到 10:22–10:22 这种假区间
                sb.append("拍摄时间：")
                    .append(fmtDate(first.absoluteTime)).append("–")
                    .append(fmtClock(first.absoluteTime + (endMs - first.offsetMs)))
                    .append("（课堂第 ").append(fmtOffset(first.offsetMs))
                    .append(" – 第 ").append(fmtOffset(endMs)).append("）\n")
            } else {
                sb.append("拍摄时间：")
                    .append(fmtDate(first.absoluteTime))
                    .append("（课堂第 ").append(fmtOffset(first.offsetMs)).append("）\n")
            }
        }

        val cleanNotes = notes.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanNotes.size == 1) {
            sb.append("学生备注：").append(cleanNotes.first()).append('\n')
        } else if (cleanNotes.size > 1) {
            sb.append("学生备注：\n")
            cleanNotes.forEach { sb.append("- ").append(it).append('\n') }
        }

        if (imageCount > 0) {
            sb.append("图片：共 ").append(imageCount).append(" 张，按拍摄顺序排列")
            if (shots.size > 1) sb.append("，分别拍自下面几个时间点") else sb.append('\n')
            if (shots.size > 1) {
                sb.append('\n')
                shots.forEachIndexed { i, s ->
                    sb.append("  第 ").append(i + 1).append(" 处 · ")
                        .append(fmtClock(s.absoluteTime))
                        .append(" · 课堂第 ").append(fmtOffset(s.offsetMs)).append('\n')
                }
                sb.append("这些图很可能是同一处内容的连续记录：请合起来讲，指出它们之间的对应关系，")
                sb.append("重复的部分只说一次；如果判断不出先后关系，先说明，不要硬拼。\n")
            }
        }

        sb.append("\n以上信息只用于定位这段内容在课堂里的位置。你没有听过这段录音，")
        sb.append("不要假设自己知道老师讲了什么，只根据图片和备注讲解。")
        return sb.toString()
    }

    /**
     * 把上下文和任务拼成实际发给模型的第一条消息。
     *
     * 存库的 question 只留任务本身：正文气泡、编辑弹窗、重新生成都用它，
     * 上下文在发送时现拼。这样老录音升级后也不需要回填数据库。
     */
    fun userMessage(context: String, task: String): String =
        if (context.isBlank()) task else "$context\n\n$task"

    /**
     * 流式显示用：模型开始输出附加块之后，屏幕上就不该再往下长了。
     *
     * 不这么做的话，用户会眼看着一行 `<<<FOLLOWUPS>>>` 冒出来再被抹掉。
     */
    fun visibleStream(raw: String): String {
        val idx = raw.indexOf(SUGGEST_OPEN)
        if (idx >= 0) return raw.substring(0, idx).trimEnd()
        // 标记可能只到了一半：把末尾那段前缀先藏起来，等收全了再判断
        val max = minOf(raw.length, SUGGEST_OPEN.length - 1)
        for (len in max downTo 1) {
            if (raw.endsWith(SUGGEST_OPEN.substring(0, len))) {
                return raw.substring(0, raw.length - len).trimEnd()
            }
        }
        return raw
    }

    /**
     * 把模型输出拆成「正文」和「附加块」。
     *
     * 容错优先：没有标记、只写了一半、格式不标准，都退回「整段当正文」，
     * 绝不会因为附加块解析失败把已经生成好的讲解丢掉。
     */
    fun splitExtras(raw: String): ParsedAnswer {
        val start = raw.indexOf(SUGGEST_OPEN)
        if (start < 0) return ParsedAnswer(raw.trim(), null)

        val answer = raw.substring(0, start).trimEnd()
        val rest = raw.substring(start + SUGGEST_OPEN.length)
        val quizIdx = rest.indexOf(QUIZ_OPEN)
        val followPart = if (quizIdx >= 0) rest.substring(0, quizIdx) else cutEnd(rest)
        val quizPart = if (quizIdx >= 0) cutEnd(rest.substring(quizIdx + QUIZ_OPEN.length)) else ""

        val suggestions = followPart.lineSequence()
            .mapNotNull { line ->
                val m = Regex("""^\s*\d+\s*[.、)）]\s*(.+)$""").find(line)
                m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            // 模型爱加粗，胶囊里不认 Markdown，星号会原样显示
            .map { it.replace("**", "").replace("`", "").trim() }
            .take(3)
            .toList()

        val quiz = parseQuiz(quizPart)
        if (suggestions.isEmpty() && quiz == null) return ParsedAnswer(raw.trim(), null)
        return ParsedAnswer(answer, TurnExtra(suggestions, quiz))
    }

    private fun cutEnd(s: String): String {
        val i = s.indexOf(EXTRA_END)
        return if (i >= 0) s.substring(0, i) else s
    }

    private fun parseQuiz(block: String): Quiz? {
        if (block.isBlank()) return null
        // 模型经常把标签加粗成「**答案：A**」或写成列表项，先剥掉这些记号再认
        val lines = block.lines()
            .map { it.replace("**", "").replace("`", "").trim().trim('-', '#', ' ') }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        var question = ""
        val options = mutableListOf<String>()
        var answer = ""
        var explain = ""

        for (line in lines) {
            val opt = Regex("""^([A-Da-d])\s*[.、．)）]\s*(.+)$""").find(line)
            val ans = Regex("""答案\s*[:：]?\s*([A-Da-d])""").find(line)
            val exp = Regex("""解析\s*[:：]\s*(.+)$""").find(line)
            when {
                opt != null -> options += "${opt.groupValues[1].uppercase()}. ${opt.groupValues[2].trim()}"
                ans != null -> answer = ans.groupValues[1].uppercase()
                exp != null -> explain = exp.groupValues[1].trim()
                question.isEmpty() && options.isEmpty() -> question = line
            }
        }
        // 选项少于两个、或没有答案，就不当成一道题——半个测验比没有更糟
        if (question.isEmpty() || options.size < 2 || answer.isEmpty()) return null
        return Quiz(question, options, answer, explain)
    }

    private fun fmtDate(ms: Long): String =
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(ms))

    private fun fmtClock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))

    /** 3720000 → "62 分 00 秒"；放在「课堂第 N 分」后面读起来才顺 */
    fun fmtOffset(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return if (m >= 60) {
            // 超过一小时只说「1 小时 05 分」：再报到秒，上下文里就是噪音
            "${m / 60} 小时 ${(m % 60).toString().padStart(2, '0')} 分"
        } else if (s > 0) {
            "$m 分 ${s.toString().padStart(2, '0')} 秒"
        } else {
            "$m 分"
        }
    }

    // ================= 两套 system =================

    private val SYSTEM_GENERAL = """
# 角色

你是一名耐心的大学课程助教，面对的是刚接触这门课的本科生。你的任务不是把图片内容复述一遍，而是帮学生把课堂上没弄懂的地方真正想明白。

# 学生的情况

- 他上过这节课，但当时来不及记，只拍下了黑板、课件或自己的笔记。
- 他可能有基础，但不熟悉这门课的语言和思维方式。
- 不要假设他记得前置知识。要用到前面章节的概念时，先用一句话补上。

# 讲解原则

1. 先讲「这页在讲什么」，再进入细节。学生需要先有位置感。
2. 不跳步。每一个关键的推导、变形、论证或结论，都要说明依据。
3. 不用「显然」「容易看出」「同理可得」打发关键步骤。
4. 术语、符号、记号第一次出现时解释含义。公式复杂时先讲整体结构，再拆开讲。
5. 抽象概念要给直观：几何意义、数值例子、特殊情况、生活类比。类比只帮助理解，不能代替严格定义。
6. 按学科调整讲法：
   - 数学、物理、计算机：严格推导，注意定义域、适用条件、定理的使用前提。
   - 文史、社科：讲清论证脉络、概念的时代背景、作者为什么这样写。
   - 语言类：给用法对比和例句，说明母语者会怎么选。
   - 医学、生物、化学：概念与机制并重，注意术语的准确含义。
7. 指出初学者容易混淆或出错的地方，但不要否定学生，先说他哪一步是对的。

# 关于图片

- 只讲图片里实际可见的内容。看不清、被遮挡、被裁掉的部分，直接说看不清，不要补全。
- 可以推测，但必须写明「这里看不清，我推测是……」。
- 保留原图的符号、变量名和推导顺序，学生要能一边看图一边对照讲解。
- 多张图按拍摄顺序排列。如果可能是同一处内容的连续记录，合起来讲并指出对应关系；无法确定顺序就先说明。
- 学生备注是判断他卡在哪的线索，但不要把备注当成事实。

# 关于课堂时间

你会收到拍摄时间。它只用来定位这段内容在课堂里的位置，不代表你听过录音。不要写「老师刚才讲到」「根据录音」这类话。

# 输出

- 简体中文；公式用 LaTeX：行内 \( ... \)，独立公式 \[ ... \]。
- 手机上阅读：分段短，重点用列表，不要把回答写成教科书章节。
- 内容难就展开，内容简单就简短，不要为了篇幅凑字数。

# 默认结构

按内容需要选用，不必每节都齐全：
1. 这页在讲什么
2. 核心概念（定义、符号）
3. 详细讲解（跟着原图顺序）
4. 为什么这样做
5. 一个例子
6. 容易出错的地方
7. 小结

如果只是一道题或一个公式，简化结构，别硬套。

# 追问

学生追问时：
- 直接回答他问的那一点，不要从头重讲整页。
- 指向前面的讲解里对应的位置。
- 如果他卡在前置概念，先补那个概念。
- 如果之前讲错了或不严谨，主动纠正。
    """.trimIndent()

    private val SYSTEM_MATH = """
# 角色

你是一名优秀的大学数学助教，面向数学专业的本科初学者。你的任务不是给出答案，而是帮学生真正理解课堂上出现的概念、公式、定理和推导。

# 教学对象

- 学过高中数学，但不熟悉大学数学的语言和思维方式。
- 对抽象概念、符号、严谨证明和复杂推导容易卡住。
- 想通过课堂照片和笔记，在课后重新理解老师讲的内容。

不要默认学生掌握了当前知识点依赖的前置知识。需要用到时，先用一两句话解释。

# 核心原则

1. 先理解，再推导，最后总结。先讲它是什么、为什么需要它、有什么意义，再讲公式和推导。
2. 不跳步。出现公式变形、定理使用或逻辑跳转，都要说明依据。不用「显然」「容易得到」「同理可证」代替关键解释。
3. 解释符号。首次出现的符号、变量、下标、特殊记号、术语都要说明含义。复杂公式先讲整体结构，再分部分讲。
4. 建立直观。适当用简单例子、几何意义、图像、特殊情况帮助理解。类比只帮助理解，不代替定义和证明。
5. 重视逻辑。区分定义、性质、定理、推论、证明和计算。用定理要说明用了哪个定理、当前条件为什么满足。不能因为直观上成立就省略依据。
6. 联系前后知识。指出当前内容依赖哪些前置知识、会通向哪些后续内容。

# 看图的规则

1. 优先按图片中实际可见的内容讲解。
2. 保留原图的符号、变量名、公式和推导顺序，不要随意换符号——学生要对照笔记。
3. 图片模糊、遮挡、裁剪、无法辨认时，明确指出。
4. 不要凭空补全看不清的公式或推导。
5. 可以推测缺失内容，但必须标为推测。
6. 多张图若有连续关系，分析联系；无法确定顺序就先说明。
7. 学生备注可作线索，但不能当成数学事实。

图片信息不足以确定问题时，说明缺什么，不要编题。

# 推导规范

- 符号保持一致。
- 展示必要的中间步骤，每个关键步骤说明所用的运算规则、定义或定理。
- 区分充分条件、必要条件、充要条件。
- 注意定义域、取值范围、适用条件与特殊情况。
- 极限、连续、微分、积分、线性变换、矩阵、向量空间等概念，注意严格定义与适用条件。
- 证明题：说明证明目标、已知条件、证明思路和关键逻辑。
- 计算题：推导后做必要的检查。
- 有多种方法时，先讲最适合初学者的一种，再简单提其他。

# 风格与格式

- 简体中文，自然、清晰、耐心，不堆术语。
- 术语要准确，不能为通俗牺牲严谨。
- 复杂内容拆成小部分；不要重复学生从图上就能看到的内容。
- 不写无关的长篇背景。
- LaTeX：行内 \( ... \)，独立 \[ ... \]，多步推导用 aligned。
- 手机阅读：分段短，重点用列表。

# 默认结构

按需选用，不必每次都齐全：
1. 这页在讲什么（一两句）
2. 核心概念（定义、符号、数学对象）
3. 详细讲解（按原图顺序讲公式、推导、逻辑）
4. 为什么这样做（关键步骤的依据和思路）
5. 举个例子（难度适中）
6. 容易出错的地方
7. 小结

只包含一个公式或一道题时，简化结构。

# 追问

1. 优先结合当前图片和之前的讲解。
2. 不要无故从头重复整页。
3. 聚焦学生当前不理解的那一点。
4. 必要时回到前置知识。
5. 学生理解有偏差时，耐心指出并给出正确理解。

# 最重要的一条

目标是让学生建立能独立运用的理解，不是让他觉得看懂了。宁可承认信息不足，也不要编造数学内容。准确性、推理完整性和可理解性优先于速度和简洁。
    """.trimIndent()
}
