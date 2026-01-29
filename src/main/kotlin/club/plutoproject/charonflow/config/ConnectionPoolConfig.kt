package club.plutoproject.charonflow.config

private const val MAX_CONNECTION_THREADS = 6

/**
 * 连接池配置
 *
 * 控制 Redis 连接池的行为，基于 CPU 核心数自动配置。
 */
data class ConnectionPoolConfig(
    /**
     * 连接池最大连接数
     * 默认: max(CPU核心数/2, 4)
     */
    val maxTotal: Int = calculateDefaultMaxConnections(),

    /**
     * 连接池最大空闲连接数
     * 默认与 maxTotal 相同
     */
    val maxIdle: Int = maxTotal,

    /**
     * 连接池最小空闲连接数
     * 默认 2
     */
    val minIdle: Int = 2,

    /**
     * 从连接池获取连接时是否测试连接
     * 默认 true，确保获取的连接是有效的
     */
    val testOnBorrow: Boolean = true,

    /**
     * 连接池中的空闲连接是否定期测试
     * 默认 true，定期检查空闲连接的健康状态
     */
    val testWhileIdle: Boolean = true,

    /**
     * 空闲连接测试间隔（毫秒）
     * 默认 30 秒
     */
    val timeBetweenEvictionRuns: Long = 30_000L,

    /**
     * 连接最小空闲时间（毫秒）
     * 默认 60 秒，超过此时间的空闲连接可能被回收
     */
    val minEvictableIdleTime: Long = 60_000L,

    /**
     * 获取连接的最大等待时间（毫秒）
     * 默认 5 秒，超时抛出异常
     */
    val maxWaitMillis: Long = 5_000L
) {
    init {
        require(maxTotal > 0) { "maxTotal must be positive" }
        require(maxIdle >= 0) { "maxIdle must be non-negative" }
        require(minIdle >= 0) { "minIdle must be non-negative" }
        require(maxIdle >= minIdle) { "maxIdle must be >= minIdle" }
        require(maxTotal >= maxIdle) { "maxTotal must be >= maxIdle" }
        require(timeBetweenEvictionRuns > 0) { "timeBetweenEvictionRuns must be positive" }
        require(minEvictableIdleTime >= 0) { "minEvictableIdleTime must be non-negative" }
        require(maxWaitMillis >= 0) { "maxWaitMillis must be non-negative" }
    }

    companion object {
        /**
         * 计算默认最大连接数
         * 公式: max(CPU核心数/2, 4)
         */
        private fun calculateDefaultMaxConnections(): Int {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            return maxOf(cpuCores / 2, MAX_CONNECTION_THREADS)
        }
    }
}
