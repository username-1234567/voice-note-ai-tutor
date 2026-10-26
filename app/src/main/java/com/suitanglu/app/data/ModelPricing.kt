package com.suitanglu.app.data

import com.suitanglu.app.data.db.MarkerTurn
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * DeepSeek 价目与成本估算（人民币 / 百万 tokens）。
 *
 * 数据来源：https://api-docs.deepseek.com/zh-cn/quick_start/pricing/
 * 抓取日期 2026-10-26，官方原文：
 *
 * | 模型            | 输入(命中) 空闲/高峰 | 输入(未命中) 空闲/高峰 | 输出 空闲/高峰 |
 * | deepseek-flash  | 0.02 / 0.04         | 1 / 2                 | 4 / 8         |
 * | deepseek-v4-pro | 0.15 / 0.30         | 4.5 / 9.0             | 13.5 / 27.0   |
 *
 * 峰谷规则：北京时间**周一至周五（不含法定节假日）** 9:00–12:00、14:00–18:00 为高峰，
 * 其余时段以及周末全天都是空闲（空闲价 = 高峰的一半）。
 *
 * 两点已知近似：
 * - 法定节假日没有内置日历，这里按工作日计算，会略微高估（多算成高峰价）；
 * - 第三方兼容端点价格不同，[supports] 会直接排除，不显示花费。
 *
 * 官方调价时改这张表即可。界面上只作「大概花了多少」的量级参考，不是账单。
 */
object ModelPricing {

    private class Rate(val hit: Double, val miss: Double, val out: Double)

    private val FLASH_PEAK = Rate(hit = 0.04, miss = 2.0, out = 8.0)
    private val FLASH_OFF = Rate(hit = 0.02, miss = 1.0, out = 4.0)
    private val PRO_PEAK = Rate(hit = 0.30, miss = 9.0, out = 27.0)
    private val PRO_OFF = Rate(hit = 0.15, miss = 4.5, out = 13.5)

    private val BEIJING = ZoneId.of("Asia/Shanghai")

    /** 只有走 DeepSeek 官方端点才谈得上"多少钱"；第三方兼容服务价格五花八门，不显示 */
    fun supports(model: String): Boolean = model.contains("deepseek", ignoreCase = true)

    private fun isPeak(atMillis: Long): Boolean {
        val zoned = Instant.ofEpochMilli(atMillis).atZone(BEIJING)
        // 周末全天空闲；工作日只在这两段里算高峰
        if (zoned.dayOfWeek.value > 5) return false
        val hour = zoned.hour
        return hour in 9 until 12 || hour in 14 until 18
    }

    /**
     * 只有 v4-pro 走 Pro 价。
     * deepseek-reasoner / deepseek-chat 是旧名字，官方说明分别对应 Flash 的思考与非思考模式，
     * 所以按 Flash 计价。
     */
    private fun rateOf(model: String, atMillis: Long): Rate {
        val isPro = model.contains("pro", ignoreCase = true)
        val peak = isPeak(atMillis)
        return when {
            isPro && peak -> PRO_PEAK
            isPro -> PRO_OFF
            peak -> FLASH_PEAK
            else -> FLASH_OFF
        }
    }

    fun costYuan(
        model: String,
        promptTokens: Int,
        cacheHitTokens: Int,
        completionTokens: Int,
        atMillis: Long,
    ): Double {
        if (!supports(model)) return 0.0
        if (promptTokens <= 0 && completionTokens <= 0) return 0.0
        val r = rateOf(model, atMillis)
        val hit = cacheHitTokens.coerceIn(0, promptTokens)
        val miss = (promptTokens - hit).coerceAtLeast(0)
        return (miss * r.miss + hit * r.hit + completionTokens * r.out) / 1_000_000.0
    }

    /** 每轮按自己的完成时间判断峰谷：一节课可能横跨 12:00 这个分界 */
    fun costOf(turns: List<MarkerTurn>, model: String): Double = turns.sumOf { t ->
        costYuan(model, t.promptTokens, t.cacheHitTokens, t.completionTokens, t.updatedAt)
    }

    /** 空字符串表示"没有可显示的量"，调用方据此决定是否渲染，避免满屏 ¥0.00 */
    fun format(yuan: Double): String = when {
        yuan <= 0.0 -> ""
        yuan < 0.01 -> "<¥0.01"
        else -> String.format(Locale.US, "¥%.2f", yuan)
    }
}
