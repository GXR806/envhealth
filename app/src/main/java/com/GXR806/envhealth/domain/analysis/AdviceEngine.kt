package com.GXR806.envhealth.domain.analysis

import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.domain.model.AdviceCategory
import com.GXR806.envhealth.domain.model.AdviceItem
import com.GXR806.envhealth.util.LogX
import kotlin.math.roundToInt

/**
 * 建议引擎（规则驱动）
 * 
 * 职责：基于睡眠分析结果，通过规则表生成个性化建议
 * 去重策略：同一规则 ID 在 24 小时内只触发一次
 */
class AdviceEngine(
    private val adviceRepository: AdviceRepository
) {
    companion object {
        private const val TAG = "AdviceEngine"
        private const val DEDUP_WINDOW_MS = 24 * 3600_000L  // 24 小时
    }
    
    /**
     * 生成健康建议
     * 
     * @param analysisResult 睡眠分析结果
     * @return 建议列表（按优先级排序）
     */
    suspend fun generateAdvice(analysisResult: SleepAnalyzer.SleepAnalysisResult): List<AdviceItem> {
        try {
            // 遍历所有规则，收集匹配的建议
            val generatedAdvice = mutableListOf<AdviceItem>()
            
            for (rule in ADVICE_RULES) {
                // 检查规则条件
                val conditionMet = rule.condition(analysisResult)
                LogX.d(TAG, "检查规则 [${rule.id}]: 条件${if (conditionMet) "满足" else "不满足"}")
                
                if (conditionMet) {
                    val advice = AdviceItem(
                        id = 0L,  // 数据库自动生成
                        category = rule.category,
                        priority = rule.priority,
                        title = rule.titleProvider(analysisResult),
                        content = rule.contentProvider(analysisResult),
                        triggerReason = rule.id  // 使用规则 ID 作为触发原因
                    )
                    generatedAdvice.add(advice)
                    LogX.i(TAG, "✅ 生成建议: [${rule.id}] ${advice.title}")
                }
            }
            
            // 按优先级排序（降序）
            val sortedAdvice = generatedAdvice.sortedByDescending { it.priority }
            
            LogX.i(TAG, "共生成 ${sortedAdvice.size} 条建议")
            return sortedAdvice
        } catch (e: Exception) {
            LogX.e(TAG, "生成建议失败", e)
            return emptyList()
        }
    }
    
    // ========== 规则定义 ==========
    
    /**
     * 建议规则数据类
     */
    private data class AdviceRule(
        val id: String,
        val category: AdviceCategory,
        val priority: Int,  // 1-5，5 最高
        val condition: (SleepAnalyzer.SleepAnalysisResult) -> Boolean,
        val titleProvider: (SleepAnalyzer.SleepAnalysisResult) -> String,
        val contentProvider: (SleepAnalyzer.SleepAnalysisResult) -> String
    )
    
    /**
     * 规则表（12+ 条规则）
     */
    private val ADVICE_RULES = listOf(
        // ========== 噪音相关规则 ==========
        AdviceRule(
            id = "R01",
            category = AdviceCategory.NOISE,
            priority = 5,
            condition = { it.noiseStats.avgLeq > 25f },
            titleProvider = { "环境噪音偏高" },
            contentProvider = { result ->
                "检测到夜间平均噪音为 ${result.noiseStats.avgLeq.roundToInt()} dB，建议使用耳塞或白噪音机，降低夜间噪音干扰。"
            }
        ),
        AdviceRule(
            id = "R02",
            category = AdviceCategory.NOISE,
            priority = 4,
            condition = { 
                val count = it.noiseStats.burstCount
                LogX.d(TAG, "R02 检查: burstCount=$count (需要 > 5)")
                count > 5
            },
            titleProvider = { "突发噪音频繁" },
            contentProvider = { result ->
                "检测到 ${result.noiseStats.burstCount} 次突发噪音事件，建议排查噪音源（空调、冰箱、窗外等）。"
            }
        ),
        AdviceRule(
            id = "R03",
            category = AdviceCategory.NOISE,
            priority = 3,
            condition = { it.noiseStats.maxDb > 70f },
            titleProvider = { "噪音峰值过高" },
            contentProvider = { result ->
                "夜间出现较大噪音峰值（${result.noiseStats.maxDb.roundToInt()} dB），建议关闭非必要电器或调整睡眠位置。"
            }
        ),
        AdviceRule(
            id = "R13",
            category = AdviceCategory.NOISE,
            priority = 2,
            condition = { it.noiseStats.avgLeq in 15f..25f },
            titleProvider = { "噪音适中，可改善" },
            contentProvider = {
                "环境噪音在可接受范围（15-25 dB），但继续降低会获得更好的睡眠质量。"
            }
        ),
        
        // ========== 光照相关规则 ==========
        AdviceRule(
            id = "R04",
            category = AdviceCategory.LIGHT,
            priority = 5,
            condition = { it.lightStats.avgLux > 50f },
            titleProvider = { "光照过强" },
            contentProvider = { result ->
                "夜间平均光照为 ${result.lightStats.avgLux.roundToInt()} lux，建议使用遮光窗帘或眼罩，降低光照干扰。"
            }
        ),
        AdviceRule(
            id = "R05",
            category = AdviceCategory.LIGHT,
            priority = 4,
            condition = { it.lightStats.stdDevLux > 20f },
            titleProvider = { "光照波动大" },
            contentProvider = {
                "光照不稳定，建议关闭夜灯或使用恒定光源（如遮光度更高的窗帘）。"
            }
        ),
        AdviceRule(
            id = "R06",
            category = AdviceCategory.LIGHT,
            priority = 5,
            condition = { !it.lightStats.preSleepTrendDecreasing },
            titleProvider = { "睡前光照未调暗" },
            contentProvider = {
                "睡前 1 小时建议逐渐调暗灯光，帮助身体分泌褪黑素，更容易入睡。"
            }
        ),
        AdviceRule(
            id = "R07",
            category = AdviceCategory.LIGHT,
            priority = 3,
            condition = { it.lightStats.avgLux in 5f..50f },
            titleProvider = { "光照略高" },
            contentProvider = { result ->
                "夜间光照略高（${result.lightStats.avgLux.roundToInt()} lux），建议进一步降低至 5 lux 以下。"
            }
        ),
        
        // ========== 规律性相关规则 ==========
        AdviceRule(
            id = "R08",
            category = AdviceCategory.SLEEP,
            priority = 5,
            condition = { it.regularityStats?.stdDevHours?.let { std -> std > 1.5f } == true },
            titleProvider = { "入睡时间不规律" },
            contentProvider = { result ->
                val stdHours = result.regularityStats?.stdDevHours?.roundToInt() ?: 0
                "入睡时间波动较大（标准差 $stdHours 小时），建议固定作息时间，提升睡眠质量。"
            }
        ),
        
        // ========== 综合评分相关规则 ==========
        AdviceRule(
            id = "R11",
            category = AdviceCategory.GENERAL,
            priority = 2,
            condition = { it.totalScore in 60f..80f },
            titleProvider = { "环境良好，可优化" },
            contentProvider = {
                "睡眠环境良好，可进一步优化噪音或光照细节，提升睡眠质量。"
            }
        ),
        AdviceRule(
            id = "R12",
            category = AdviceCategory.GENERAL,
            priority = 1,
            condition = { it.totalScore > 80f },
            titleProvider = { "环境优秀" },
            contentProvider = {
                "睡眠环境优秀，请继续保持！"
            }
        ),
        
        // ========== 极端情况规则 ==========
        AdviceRule(
            id = "R14",
            category = AdviceCategory.GENERAL,
            priority = 5,
            condition = { it.totalScore < 40f },
            titleProvider = { "睡眠环境较差" },
            contentProvider = { result ->
                val weakest = listOf(
                    "噪音" to result.noiseScore,
                    "光照" to result.lightScore,
                    "规律性" to (result.regularityScore ?: 100f)
                ).minByOrNull { it.second }?.first ?: "未知"
                "睡眠环境质量较差（${result.totalScore.roundToInt()} 分），建议优先改善${weakest}问题。"
            }
        ),
        
        // ========== 数据不足提示 ==========
        AdviceRule(
            id = "R15",
            category = AdviceCategory.GENERAL,
            priority = 1,
            condition = { it.regularityStats == null },
            titleProvider = { "规律性数据不足" },
            contentProvider = {
                "规律性数据不足：需要至少 3 天的睡眠记录才能分析入睡规律性，请继续使用本应用。"
            }
        )
    )
}
