package club.plutoproject.charonflow.config

import io.lettuce.core.RedisCommandTimeoutException
import io.lettuce.core.RedisConnectionException
import kotlin.math.pow
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 重试策略配置
 *
 * 控制连接失败和消息发送失败的重试行为。
 */
data class RetryPolicyConfig(
    /**
     * 连接重试配置
     * 控制 Redis 连接失败时的重试行为
     */
    val connectionRetry: RetryConfig = RetryConfig(
        maxAttempts = 3,
        backoffStrategy = ExponentialBackoffStrategy(
            initialDelay = 1.seconds,
            multiplier = 2.0,
            maxDelay = 10.seconds
        )
    ),
    
    /**
     * 消息重试配置
     * 控制消息发送失败时的重试行为
     */
    val messageRetry: RetryConfig = RetryConfig(
        maxAttempts = 2,
        backoffStrategy = FixedBackoffStrategy(delay = 500.milliseconds)
    )
)

/**
 * 重试配置
 */
data class RetryConfig(
    /**
     * 最大重试次数（包括第一次尝试）
     * 例如：maxAttempts = 3 表示最多尝试 3 次（1次初始尝试 + 2次重试）
     */
    val maxAttempts: Int = 3,
    
    /**
     * 退避策略
     * 控制重试之间的延迟
     */
    val backoffStrategy: BackoffStrategy = ExponentialBackoffStrategy(),
    
    /**
     * 可重试的异常类型
     * 只有这些异常才会触发重试
     */
    val retryableExceptions: Set<KClass<out Throwable>> = setOf(
        // 连接相关异常
        RedisConnectionException::class,
        RedisCommandTimeoutException::class,
        // 网络相关异常
        java.net.ConnectException::class,
        java.net.SocketTimeoutException::class,
        java.io.IOException::class
    )
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }
}

/**
 * 退避策略接口
 */
sealed interface BackoffStrategy {
    /**
     * 计算第 n 次重试的延迟时间
     * @param attempt 重试次数（从 0 开始，0 表示第一次重试）
     */
    fun calculateDelay(attempt: Int): Duration
}

/**
 * 固定退避策略
 * 每次重试使用相同的延迟时间
 */
data class FixedBackoffStrategy(
    val delay: Duration = 1.seconds
) : BackoffStrategy {
    init {
        require(delay.isPositive()) { "Delay must be positive" }
    }
    
    override fun calculateDelay(attempt: Int): Duration = delay
}

/**
 * 指数退避策略
 * 延迟时间按指数增长：delay = initialDelay * (multiplier ^ attempt)
 */
data class ExponentialBackoffStrategy(
    val initialDelay: Duration = 1.seconds,
    val multiplier: Double = 2.0,
    val maxDelay: Duration = 30.seconds,
    val jitterFactor: Double = 0.1
) : BackoffStrategy {
    init {
        require(initialDelay.isPositive()) { "Initial delay must be positive" }
        require(multiplier > 1.0) { "Multiplier must be > 1.0" }
        require(maxDelay.isPositive()) { "Max delay must be positive" }
        require(jitterFactor >= 0.0 && jitterFactor <= 1.0) { "Jitter factor must be between 0.0 and 1.0" }
    }
    
    override fun calculateDelay(attempt: Int): Duration {
        val baseDelay = initialDelay * multiplier.pow(attempt)
        val delay = if (baseDelay > maxDelay) maxDelay else baseDelay
        
        // 添加随机抖动，避免多个客户端同时重试
        val jitter = delay * (1.0 + (Math.random() * 2 - 1) * jitterFactor)
        return if (jitter > maxDelay) maxDelay else jitter
    }
}

/**
 * 线性退避策略
 * 延迟时间线性增长：delay = initialDelay + increment * attempt
 */
data class LinearBackoffStrategy(
    val initialDelay: Duration = 1.seconds,
    val increment: Duration = 1.seconds,
    val maxDelay: Duration = 10.seconds
) : BackoffStrategy {
    init {
        require(initialDelay.isPositive()) { "Initial delay must be positive" }
        require(increment.isPositive()) { "Increment must be positive" }
        require(maxDelay.isPositive()) { "Max delay must be positive" }
    }
    
    override fun calculateDelay(attempt: Int): Duration {
        val delay = initialDelay + increment * attempt
        return if (delay > maxDelay) maxDelay else delay
    }
}
