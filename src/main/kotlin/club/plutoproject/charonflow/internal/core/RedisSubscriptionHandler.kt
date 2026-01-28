package club.plutoproject.charonflow.internal.core

/**
 * Redis 订阅处理器接口
 *
 * 处理 Redis 主题的订阅和取消订阅操作。
 */
interface RedisSubscriptionHandler {
    /**
     * 订阅 Redis 主题
     *
     * @param topic 主题名称
     * @return 操作结果
     */
    suspend fun subscribeToRedis(topic: String): Result<Unit>

    /**
     * 取消订阅 Redis 主题
     *
     * @param topic 主题名称
     * @return 操作结果
     */
    suspend fun unsubscribeFromRedis(topic: String): Result<Unit>
}
