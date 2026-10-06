package com.GXR806.envhealth.domain.sleep

import com.GXR806.envhealth.data.repository.SleepRepository
import com.GXR806.envhealth.domain.model.SleepSession
import com.GXR806.envhealth.domain.model.SleepSessionStatus
import com.GXR806.envhealth.domain.model.SleepSessionType
import com.GXR806.envhealth.util.LogX
import com.GXR806.envhealth.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 睡眠会话管理器
 * 
 * 职责：
 * 1. 手动开始/结束睡眠会话
 * 2. 会话状态管理：ONGOING → COMPLETED / CANCELLED
 * 
 * 注意：自动睡眠判定功能未在 P5 实现，留待 P6 扩展
 */
class SleepSessionManager(
    private val sleepRepository: SleepRepository
) {
    companion object {
        private const val TAG = "SleepSessionManager"
    }
    
    /**
     * 手动开始睡眠会话
     * 
     * @return Result<Long> 成功返回会话 ID，失败返回错误
     */
    suspend fun startManualSession(): Result<Long> {
        return try {
            // 检查是否已有进行中的会话
            val ongoingSessions = sleepRepository.getOngoingSessions().first()
            if (ongoingSessions.isNotEmpty()) {
                LogX.w(TAG, "已有进行中的睡眠会话，无法开始新会话")
                return Result.failure(IllegalStateException("已有进行中的睡眠会话"))
            }
            
            // 创建新会话
            val session = SleepSession(
                id = 0L,  // 数据库自动生成
                startTime = TimeUtils.nowMillis(),
                endTime = null,
                type = SleepSessionType.MANUAL,
                status = SleepSessionStatus.ONGOING
            )
            
            val sessionId = sleepRepository.insertSession(session)
            LogX.i(TAG, "手动开始睡眠会话，sessionId=$sessionId")
            Result.success(sessionId)
        } catch (e: Exception) {
            LogX.e(TAG, "开始睡眠会话失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 结束睡眠会话
     * 
     * @param sessionId 会话 ID
     * @return Result<Unit> 成功返回 Unit，失败返回错误
     */
    suspend fun endSession(sessionId: Long): Result<Unit> {
        return try {
            val session = sleepRepository.getSessionById(sessionId)
            if (session == null) {
                LogX.w(TAG, "会话不存在，sessionId=$sessionId")
                return Result.failure(IllegalArgumentException("会话不存在"))
            }
            
            if (session.status != SleepSessionStatus.ONGOING) {
                LogX.w(TAG, "会话已结束，无法重复结束，sessionId=$sessionId")
                return Result.failure(IllegalStateException("会话已结束"))
            }
            
            // 更新会话状态
            val updatedSession = session.copy(
                endTime = TimeUtils.nowMillis(),
                status = SleepSessionStatus.COMPLETED
            )
            
            sleepRepository.updateSession(updatedSession)
            LogX.i(TAG, "睡眠会话已结束，sessionId=$sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            LogX.e(TAG, "结束睡眠会话失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 取消睡眠会话
     * 
     * @param sessionId 会话 ID
     * @return Result<Unit> 成功返回 Unit，失败返回错误
     */
    suspend fun cancelSession(sessionId: Long): Result<Unit> {
        return try {
            val session = sleepRepository.getSessionById(sessionId)
            if (session == null) {
                LogX.w(TAG, "会话不存在，sessionId=$sessionId")
                return Result.failure(IllegalArgumentException("会话不存在"))
            }
            
            if (session.status != SleepSessionStatus.ONGOING) {
                LogX.w(TAG, "会话已结束，无法取消，sessionId=$sessionId")
                return Result.failure(IllegalStateException("会话已结束"))
            }
            
            // 更新会话状态
            val updatedSession = session.copy(
                endTime = TimeUtils.nowMillis(),
                status = SleepSessionStatus.CANCELLED
            )
            
            sleepRepository.updateSession(updatedSession)
            LogX.i(TAG, "睡眠会话已取消，sessionId=$sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            LogX.e(TAG, "取消睡眠会话失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取当前进行中的会话（Flow）
     * 
     * @return Flow<SleepSession?> 进行中的会话，如果没有则为 null
     */
    fun getOngoingSession(): Flow<SleepSession?> {
        return sleepRepository.getOngoingSessions().map { sessions ->
            sessions.firstOrNull()
        }
    }
    
    /**
     * 获取当前进行中的会话（一次性）
     * 
     * @return SleepSession? 进行中的会话，如果没有则为 null
     */
    suspend fun getOngoingSessionOnce(): SleepSession? {
        return sleepRepository.getOngoingSessions().first().firstOrNull()
    }
}
