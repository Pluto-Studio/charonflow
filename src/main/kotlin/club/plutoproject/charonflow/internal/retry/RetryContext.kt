package club.plutoproject.charonflow.internal.retry

/**
 * 重试上下文
 *
 * 保存重试执行过程中的状态信息，传递给重试代码块。
 *
 * @property attempt 当前尝试次数（从 0 开始，0 表示第一次尝试）
 * @property maxAttempts 最大尝试次数
 * @property lastException 上一次尝试失败的异常（第一次尝试时为 null）
 */
internal data class RetryContext(
    val attempt: Int,
    val maxAttempts: Int,
    val lastException: Throwable?
) {
    /**
     * 当前是第几次尝试（从 1 开始）
     */
    val currentAttempt: Int
        get() = attempt + 1

    /**
     * 是否是第一次尝试
     */
    val isFirstAttempt: Boolean
        get() = attempt == 0

    /**
     * 是否是最后一次尝试
     */
    val isLastAttempt: Boolean
        get() = attempt == maxAttempts - 1

    /**
     * 剩余尝试次数
     */
    val remainingAttempts: Int
        get() = maxAttempts - currentAttempt
}
