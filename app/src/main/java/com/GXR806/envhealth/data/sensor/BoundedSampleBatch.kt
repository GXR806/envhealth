package com.GXR806.envhealth.data.sensor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 单一有界特征批次；调用者每30s提交，第三条或最老条90s到期提交。
 * Room @Insert(List) 自身为事务，无需重复 @Transaction。提交与移除不可被 stop 的取消拆开。
 * 失败保留最多3条、停止接收；仅显式 flush 可重试。owner 停止必须 join 再 flush。
 */
class BoundedSampleBatch<T>(
    private val now: () -> Long,
    private val insert: suspend (List<T>) -> Unit
) {
    private val mutex = Mutex()
    private val pending = ArrayList<T>(3)
    private var oldest = 0L
    @Volatile var failed = false
        private set
    suspend fun offer(sample: T) = mutex.withLock {
        check(!failed) { "采样写入已暂停" }
        check(pending.size < 3)
        if (pending.isEmpty()) oldest = now()
        pending.add(sample)
        if (pending.size == 3 || now() - oldest >= 90_000L) commit()
    }
    suspend fun flushIfDue() = mutex.withLock {
        if (!failed && pending.isNotEmpty() && now() - oldest >= 90_000L) commit()
    }
    suspend fun flush() = mutex.withLock { commit() }
    private suspend fun commit() {
        if (pending.isEmpty()) return
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                insert(pending.toList())
                pending.clear()
                failed = false
            } catch (e: Exception) {
                failed = true
                throw e
            }
        }
    }
    // 仅在所有 writer join 后的维护清除使用，不自动重试清除前的特征。
    suspend fun discard() = mutex.withLock { pending.clear(); failed = false }
}
