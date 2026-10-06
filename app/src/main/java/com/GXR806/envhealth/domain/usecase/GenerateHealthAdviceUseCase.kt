package com.GXR806.envhealth.domain.usecase

import com.GXR806.envhealth.data.repository.AdviceRepository
import com.GXR806.envhealth.domain.analysis.AdviceEngine
import com.GXR806.envhealth.domain.analysis.SleepAnalyzer
import com.GXR806.envhealth.domain.model.AdviceItem
import com.GXR806.envhealth.util.LogX

/**
 * 生成健康建议用例
 * 
 * 职责：
 * 1. 调用 AdviceEngine 生成建议
 * 2. 批量存储建议到数据库
 * 3. 返回生成的建议列表
 */
class GenerateHealthAdviceUseCase(
    private val adviceEngine: AdviceEngine,
    private val adviceRepository: AdviceRepository
) {
    companion object {
        private const val TAG = "GenerateHealthAdviceUseCase"
    }
    
    /**
     * 执行用例
     * 
     * @param analysisResult 睡眠分析结果
     * @return Result<List<AdviceItem>> 成功返回建议列表，失败返回错误
     */
    suspend operator fun invoke(analysisResult: SleepAnalyzer.SleepAnalysisResult): Result<List<AdviceItem>> {
        return try {
            // 1. 生成建议
            val adviceList = adviceEngine.generateAdvice(analysisResult)
            
            if (adviceList.isEmpty()) {
                LogX.i(TAG, "未生成任何建议（规则未匹配或已去重）")
                return Result.success(emptyList())
            }
            
            // 2. 批量存储建议
            val adviceIds = adviceRepository.insertAllAdvice(adviceList)
            
            // 3. 更新建议 ID
            val savedAdvice = adviceList.mapIndexed { index, advice ->
                advice.copy(id = adviceIds[index])
            }
            
            LogX.i(TAG, "生成健康建议成功: 共 ${savedAdvice.size} 条")
            Result.success(savedAdvice)
        } catch (e: Exception) {
            LogX.e(TAG, "生成健康建议失败", e)
            Result.failure(e)
        }
    }
}
