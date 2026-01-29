package club.plutoproject.charonflow.dsl

import club.plutoproject.charonflow.config.ConnectionPoolConfig

/**
 * ConnectionPoolConfig 的 DSL 构建器
 */
@CharonFlowDsl
class ConnectionPoolConfigDsl {
    var maxTotal: Int = ConnectionPoolConfig().maxTotal
    var maxIdle: Int = maxTotal
    var minIdle: Int = 2
    var testOnBorrow: Boolean = true
    var testWhileIdle: Boolean = true
    var timeBetweenEvictionRuns: Long = 30_000L
    var minEvictableIdleTime: Long = 60_000L
    var maxWaitMillis: Long = 5_000L

    fun build(): ConnectionPoolConfig = ConnectionPoolConfig(
        maxTotal = maxTotal,
        maxIdle = maxIdle,
        minIdle = minIdle,
        testOnBorrow = testOnBorrow,
        testWhileIdle = testWhileIdle,
        timeBetweenEvictionRuns = timeBetweenEvictionRuns,
        minEvictableIdleTime = minEvictableIdleTime,
        maxWaitMillis = maxWaitMillis
    )
}

/**
 * 配置连接池选项的 DSL 块
 */
fun CharonFlowConfigDsl.connectionPool(block: ConnectionPoolConfigDsl.() -> Unit) {
    connectionPoolConfigDsl = ConnectionPoolConfigDsl().apply(block)
}
