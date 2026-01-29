package club.plutoproject.charonflow.dsl

import club.plutoproject.charonflow.config.*
import io.lettuce.core.RedisCommandTimeoutException
import io.lettuce.core.RedisConnectionException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * BackoffStrategy 的 DSL 构建器基类
 */
sealed class BackoffStrategyDsl {
    abstract fun build(): BackoffStrategy
}

/**
 * FixedBackoffStrategy 的 DSL 构建器
 */
@CharonFlowDsl
class FixedBackoffStrategyDsl : BackoffStrategyDsl() {
    var delay: Duration = 1.seconds

    override fun build(): FixedBackoffStrategy = FixedBackoffStrategy(delay)
}

/**
 * ExponentialBackoffStrategy 的 DSL 构建器
 */
@CharonFlowDsl
class ExponentialBackoffStrategyDsl : BackoffStrategyDsl() {
    var initialDelay: Duration = 1.seconds
    var multiplier: Double = 2.0
    var maxDelay: Duration = 30.seconds
    var jitterFactor: Double = 0.1

    override fun build(): ExponentialBackoffStrategy = ExponentialBackoffStrategy(
        initialDelay = initialDelay,
        multiplier = multiplier,
        maxDelay = maxDelay,
        jitterFactor = jitterFactor
    )
}

/**
 * LinearBackoffStrategy 的 DSL 构建器
 */
@CharonFlowDsl
class LinearBackoffStrategyDsl : BackoffStrategyDsl() {
    var initialDelay: Duration = 1.seconds
    var increment: Duration = 1.seconds
    var maxDelay: Duration = 10.seconds

    override fun build(): LinearBackoffStrategy = LinearBackoffStrategy(
        initialDelay = initialDelay,
        increment = increment,
        maxDelay = maxDelay
    )
}

/**
 * RetryConfig 的 DSL 构建器
 */
@CharonFlowDsl
class RetryConfigDsl {
    var maxAttempts: Int = 3
    private var backoffStrategyDsl: BackoffStrategyDsl? = null
    var retryableExceptions: MutableSet<KClass<out Throwable>> = mutableSetOf(
        RedisConnectionException::class,
        RedisCommandTimeoutException::class,
        ConnectException::class,
        SocketTimeoutException::class,
        IOException::class
    )

    /**
     * 使用固定退避策略
     */
    fun fixedBackoff(block: FixedBackoffStrategyDsl.() -> Unit) {
        backoffStrategyDsl = FixedBackoffStrategyDsl().apply(block)
    }

    /**
     * 使用指数退避策略
     */
    fun exponentialBackoff(block: ExponentialBackoffStrategyDsl.() -> Unit) {
        backoffStrategyDsl = ExponentialBackoffStrategyDsl().apply(block)
    }

    /**
     * 使用线性退避策略
     */
    fun linearBackoff(block: LinearBackoffStrategyDsl.() -> Unit) {
        backoffStrategyDsl = LinearBackoffStrategyDsl().apply(block)
    }

    fun build(): RetryConfig = RetryConfig(
        maxAttempts = maxAttempts,
        backoffStrategy = backoffStrategyDsl?.build() ?: ExponentialBackoffStrategy(),
        retryableExceptions = retryableExceptions
    )
}

/**
 * RetryPolicyConfig 的 DSL 构建器
 */
@CharonFlowDsl
class RetryPolicyConfigDsl {
    private var connectionRetryDsl: RetryConfigDsl? = null
    private var messageRetryDsl: RetryConfigDsl? = null

    /**
     * 配置连接重试策略
     */
    fun connectionRetry(block: RetryConfigDsl.() -> Unit) {
        connectionRetryDsl = RetryConfigDsl().apply(block)
    }

    /**
     * 配置消息重试策略
     */
    fun messageRetry(block: RetryConfigDsl.() -> Unit) {
        messageRetryDsl = RetryConfigDsl().apply(block)
    }

    fun build(): RetryPolicyConfig = RetryPolicyConfig(
        connectionRetry = connectionRetryDsl?.build() ?: RetryConfig(
            maxAttempts = 3,
            backoffStrategy = ExponentialBackoffStrategy(
                initialDelay = 1.seconds,
                multiplier = 2.0,
                maxDelay = 10.seconds
            )
        ),
        messageRetry = messageRetryDsl?.build() ?: RetryConfig(
            maxAttempts = 2,
            backoffStrategy = FixedBackoffStrategy(delay = 500.milliseconds)
        )
    )
}

/**
 * 配置重试策略的 DSL 块
 */
fun CharonFlowConfigDsl.retryPolicy(block: RetryPolicyConfigDsl.() -> Unit) {
    retryPolicyConfigDsl = RetryPolicyConfigDsl().apply(block)
}
