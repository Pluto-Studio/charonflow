package club.plutoproject.charonflow.config

import kotlinx.serialization.modules.SerializersModule
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * CharonFlow 主配置类
 *
 * 包含所有可配置的选项，用于创建 CharonFlow 实例。
 */
data class CharonFlowConfig(
    /**
     * Redis 连接 URI
     * 格式: redis://[password@]host[:port][/database]
     * 示例: redis://localhost:6379, redis://:password@redis.example.com:6379/1
     */
    val redisUri: String,

    /**
     * 序列化配置
     * 固定使用 CBOR 格式
     */
    val serializationConfig: SerializationConfig = SerializationConfig(),

    /**
     * 序列化器模块
     * 用于注册自定义序列化器和多态类型
     */
    val serializersModule: SerializersModule = SerializersModule {},

    /**
     * 连接池配置
     * 控制 Redis 连接池的行为
     */
    val connectionPoolConfig: ConnectionPoolConfig = ConnectionPoolConfig(),

    /**
     * 重试策略配置
     * 控制连接和消息发送的重试行为
     */
    val retryPolicyConfig: RetryPolicyConfig = RetryPolicyConfig(),

    /**
     * 客户端标识
     * 用于点对点通信，默认自动生成 UUID
     */
    val clientId: String = UUID.randomUUID().toString(),

    /**
     * 操作超时时间
     * 默认 5 秒
     */
    val timeout: Duration = 5.seconds,

    /**
     * 是否启用指标收集
     * 默认禁用，按需开启
     */
    val enableMetrics: Boolean = false
) {
    init {
        require(redisUri.isNotBlank()) { "Redis URI cannot be blank" }
        require(timeout.isPositive()) { "Timeout must be positive" }
    }
}
