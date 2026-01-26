package club.plutoproject.charonflow.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * CharonFlow 主配置类
 *
 * 包含所有可配置的选项，用于创建 CharonFlow 实例。
 */
data class Config(
    /**
     * Redis 连接 URI
     * 格式: redis://[password@]host[:port][/database]
     * 示例: redis://localhost:6379, redis://:password@redis.example.com:6379/1
     */
    val redisUri: String,
    
    /**
     * 序列化器配置
     * 默认使用 CBOR 序列化器
     */
    val serializerConfig: SerializerConfig = SerializerConfig(),
    
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
     * 操作超时时间
     * 默认 5 秒
     */
    val timeout: Duration = 5.seconds,
    
    /**
     * 是否启用健康检查
     * 默认启用，定期检查连接状态
     */
    val enableHealthCheck: Boolean = true,
    
    /**
     * 健康检查间隔
     * 默认 30 秒
     */
    val healthCheckInterval: Duration = 30.seconds,
    
    /**
     * 是否启用指标收集
     * 默认禁用，按需开启
     */
    val enableMetrics: Boolean = false
) {
    init {
        require(redisUri.isNotBlank()) { "Redis URI cannot be blank" }
        require(timeout.isPositive()) { "Timeout must be positive" }
        require(healthCheckInterval.isPositive()) { "Health check interval must be positive" }
    }
}