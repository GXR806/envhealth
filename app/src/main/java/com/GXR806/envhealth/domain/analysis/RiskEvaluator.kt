package com.GXR806.envhealth.domain.analysis

/** 百分比不是声压级；这里的规则仅是产品提醒，不是医学安全保证。 */
enum class HeadphoneRisk { SAFE, LOW, MEDIUM, HIGH }
data class RiskResult(val level: HeadphoneRisk, val reasons: List<String>)

class RiskEvaluator {
    fun evaluate(durationMs: Long, volumePercent: Float, weekMs: Long): RiskResult {
        val reasons = mutableListOf<String>()
        var level = HeadphoneRisk.SAFE
        fun add(risk: HeadphoneRisk, reason: String) {
            if (risk.ordinal > level.ordinal) level = risk
            reasons += reason
        }
        if (volumePercent > 60f) add(HeadphoneRisk.LOW, "音量超过 60%，建议适当降低")
        if (durationMs > 60 * 60_000L) {
            add(HeadphoneRisk.MEDIUM, "本次累计收听超过 60 分钟，建议休息")
            if (volumePercent > 60f) add(HeadphoneRisk.HIGH, "长时间收听且当前音量超过 60%")
        }
        if (volumePercent > 80f) add(HeadphoneRisk.HIGH, "音量超过 80%，请及时降低")
        if (weekMs > 40 * 60 * 60_000L) add(HeadphoneRisk.HIGH, "近 7 天累计超过 40 小时（产品提醒阈值）")
        if (reasons.isEmpty()) reasons += "暂未触发提醒，仍建议定时休息"
        return RiskResult(level, reasons)
    }
}
