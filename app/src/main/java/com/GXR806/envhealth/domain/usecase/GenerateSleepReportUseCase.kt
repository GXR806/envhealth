package com.GXR806.envhealth.domain.usecase

import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.analysis.SleepAnalyzer
import com.GXR806.envhealth.domain.model.SleepReport
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.util.LogX

/**
 * 生成睡眠报告用例
 * 
 * 职责：
 * 1. 调用 SleepAnalyzer 分析睡眠会话
 * 2. 创建 SleepReport 并存储到数据库
 * 3. 返回生成的报告
 */
class GenerateSleepReportUseCase(
    private val sleepAnalyzer: SleepAnalyzer,
    private val sleepRepository: SleepRepository
) {
    companion object {
        private const val TAG = "GenerateSleepReportUseCase"
    }
    
    /**
     * 执行用例
     * 
     * @param session 睡眠会话
     * @return Result<SleepReport> 成功返回报告，失败返回错误
     */
    suspend operator fun invoke(session: SleepSession): Result<Pair<SleepReport, SleepAnalyzer.SleepAnalysisResult>> {
        return try {
            // 1. 分析睡眠会话
            val analysisResult = sleepAnalyzer.analyze(session)
            
            // 2. 创建报告
            val report = SleepReport(
                sessionId = session.id,
                totalScore = analysisResult.totalScore,
                noiseScore = analysisResult.noiseScore,
                lightScore = analysisResult.lightScore,
                regularityScore = analysisResult.regularityScore,
                summary = generateSummary(analysisResult)
            )
            
            // 3. 存储报告
            val reportId = sleepRepository.insertReport(report)
            val savedReport = report.copy(id = reportId)
            
            LogX.i(TAG, "生成睡眠报告成功: reportId=$reportId, totalScore=${report.totalScore}")
            Result.success(savedReport to analysisResult)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            LogX.e(TAG, "生成睡眠报告失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 生成报告摘要
     */
    private fun generateSummary(result: SleepAnalyzer.SleepAnalysisResult): String {
        val scoreLevel = when {
            result.totalScore >= 80f -> "优秀"
            result.totalScore >= 60f -> "良好"
            result.totalScore >= 40f -> "一般"
            else -> "较差"
        }
        
        val weakestArea = listOf(
            "噪音" to result.noiseScore,
            "光照" to result.lightScore,
            "规律性" to (result.regularityScore ?: 100f)
        ).minByOrNull { it.second }?.first ?: "未知"
        
        return "睡眠环境${scoreLevel}（${result.totalScore.toInt()}分），建议优先改善${weakestArea}。"
    }
}
