package com.GXR806.envhealth.util

/**
 * 统一结果封装
 *
 * 用于封装异步操作的三种状态：成功、失败、加载中
 * 适用于 Repository、UseCase、ViewModel 层的数据传递
 */
sealed class AppResult<out T> {

    /**
     * 加载中
     */
    object Loading : AppResult<Nothing>()

    /**
     * 成功
     *
     * @param data 结果数据
     */
    data class Success<T>(val data: T) : AppResult<T>()

    /**
     * 失败
     *
     * @param exception 异常信息
     * @param message 用户友好的错误消息
     */
    data class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "未知错误"
    ) : AppResult<Nothing>()

    /**
     * 是否成功
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * 是否失败
     */
    val isError: Boolean
        get() = this is Error

    /**
     * 是否加载中
     */
    val isLoading: Boolean
        get() = this is Loading

    /**
     * 获取数据（如果成功）
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * 获取错误消息（如果失败）
     */
    fun getErrorOrNull(): String? = when (this) {
        is Error -> message
        else -> null
    }
}
