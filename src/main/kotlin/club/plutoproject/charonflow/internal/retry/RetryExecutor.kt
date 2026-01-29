package club.plutoproject.charonflow.internal.retry

import club.plutoproject.charonflow.config.RetryConfig
import club.plutoproject.charonflow.internal.logger
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

/**
 * 重试执行器
 *
 * 根据 [RetryConfig] 执行带重试的代码块。
 */
internal class RetryExecutor {

    /**
     * 执行带重试的代码块
     *
     * @param config 重试配置
     * @param operationName 操作名称（用于日志）
     * @param block 要执行的代码块
     * @return 代码块的执行结果
     * @throws Throwable 如果所有重试都失败，则抛出最后一次的异常
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig,
        operationName: String,
        block: suspend (context: RetryContext) -> T
    ): T {
        var lastException: Throwable? = null

        for (attempt in 0 until config.maxAttempts) {
            val context = RetryContext(
                attempt = attempt,
                maxAttempts = config.maxAttempts,
                lastException = lastException
            )

            try {
                logger.debug("Executing {} (attempt {}/{})", operationName, attempt + 1, config.maxAttempts)
                val result = block(context)

                if (attempt > 0) {
                    logger.info("{} succeeded after {} attempt(s)", operationName, attempt + 1)
                }

                return result
            } catch (e: Throwable) {
                lastException = e

                // 检查是否是可重试的异常
                if (!isRetryableException(e, config.retryableExceptions)) {
                    logger.warn("{} failed with non-retryable exception: {}", operationName, e.message)
                    throw e
                }

                // 如果是最后一次尝试，则抛出异常
                if (attempt == config.maxAttempts - 1) {
                    logger.error(
                        "{} failed after {} attempt(s). Last error: {}",
                        operationName,
                        config.maxAttempts,
                        e.message,
                        e
                    )
                    throw e
                }

                // 计算延迟并等待
                val delayDuration = config.backoffStrategy.calculateDelay(attempt)
                logger.debug(
                    "{} failed (attempt {}/{}), retrying in {}ms. Error: {}",
                    operationName,
                    attempt + 1,
                    config.maxAttempts,
                    delayDuration.inWholeMilliseconds,
                    e.message
                )
                delay(delayDuration)
            }
        }

        // 理论上不会到达这里，但为了编译通过
        throw lastException ?: IllegalStateException("Unexpected state in retry logic")
    }

    /**
     * 检查异常是否是可重试的
     */
    private fun isRetryableException(
        exception: Throwable,
        retryableExceptions: Set<KClass<out Throwable>>
    ): Boolean {
        // 检查异常本身或其任何父类是否在可重试列表中
        var current: Throwable? = exception
        while (current != null) {
            if (retryableExceptions.any { it.isInstance(current) }) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
