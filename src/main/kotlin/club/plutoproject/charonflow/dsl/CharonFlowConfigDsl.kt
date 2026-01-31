package club.plutoproject.charonflow.dsl

import club.plutoproject.charonflow.config.CharonFlowConfig
import club.plutoproject.charonflow.config.ConnectionPoolConfig
import club.plutoproject.charonflow.config.RetryPolicyConfig
import club.plutoproject.charonflow.config.SerializationConfig
import kotlinx.serialization.modules.SerializersModule
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * CharonFlow DSL 标记注解
 */
@DslMarker
annotation class CharonFlowDsl

/**
 * CharonFlowConfig 的 DSL 构建器
 */
@CharonFlowDsl
class CharonFlowConfigDsl {
    var redisUri: String = ""
    internal var serializationConfigDsl: SerializationConfigDsl? = null
    var serializersModule: SerializersModule = SerializersModule {}
    internal var connectionPoolConfigDsl: ConnectionPoolConfigDsl? = null
    internal var retryPolicyConfigDsl: RetryPolicyConfigDsl? = null
    var clientId: String = UUID.randomUUID().toString()
    var timeout: Duration = 5.seconds
    var enableMetrics: Boolean = false
    var ignoreSelfPubSubMessages: Boolean = false
    var classLoader: ClassLoader? = null

    fun build(): CharonFlowConfig {
        require(redisUri.isNotBlank()) { "Redis URI cannot be blank" }
        require(timeout.isPositive()) { "Timeout must be positive" }

        return CharonFlowConfig(
            redisUri = redisUri,
            serializationConfig = serializationConfigDsl?.build() ?: SerializationConfig(),
            serializersModule = serializersModule,
            connectionPoolConfig = connectionPoolConfigDsl?.build() ?: ConnectionPoolConfig(),
            retryPolicyConfig = retryPolicyConfigDsl?.build() ?: RetryPolicyConfig(),
            clientId = clientId,
            timeout = timeout,
            enableMetrics = enableMetrics,
            ignoreSelfPubSubMessages = ignoreSelfPubSubMessages,
            classLoader = classLoader
        )
    }
}

/**
 * 使用 DSL 构建 CharonFlowConfig
 *
 * 示例：
 * ```kotlin
 * val config = charonFlow {
 *     redisUri = "redis://localhost:6379"
 *     timeout = 10.seconds
 *
 *     serialization {
 *         encodeDefaults = true
 *         ignoreUnknownKeys = false
 *     }
 *
 *     connectionPool {
 *         maxTotal = 10
 *         maxIdle = 8
 *         minIdle = 2
 *     }
 *
 *     retryPolicy {
 *         connectionRetry {
 *             maxAttempts = 5
 *             exponentialBackoff {
 *                 initialDelay = 1.seconds
 *                 multiplier = 2.0
 *                 maxDelay = 30.seconds
 *             }
 *         }
 *         messageRetry {
 *             maxAttempts = 3
 *             fixedBackoff {
 *                 delay = 500.milliseconds
 *             }
 *         }
 *     }
 * }
 * ```
 */
fun charonFlow(block: CharonFlowConfigDsl.() -> Unit): CharonFlowConfig {
    return CharonFlowConfigDsl().apply(block).build()
}
