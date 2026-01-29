package club.plutoproject.charonflow

import kotlinx.coroutines.Deferred

/**
 * 订阅接口
 *
 * 表示一个活跃的订阅，提供取消订阅和管理订阅的方法。
 */
interface Subscription {

    // region 属性

    /**
     * 订阅 ID
     */
    val id: String

    /**
     * 订阅的主题/通道名称
     */
    val topic: String

    /**
     * 订阅创建时间（毫秒）
     */
    val createdAt: Long

    /**
     * 最后活动时间（毫秒）
     */
    val lastActivityTime: Long

    /**
     * 订阅是否活跃
     */
    val isActive: Boolean

    /**
     * 收到的消息数量
     */
    val messageCount: Long

    /**
     * 订阅统计信息
     */
    val stats: SubscriptionStats

    // endregion

    // region 取消订阅方法

    /**
     * 取消订阅
     *
     * 停止接收消息并释放相关资源。
     *
     * @return 取消结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun unsubscribe(): Result<Unit>

    /**
     * 异步取消订阅
     *
     * 立即返回 Deferred，在后台取消订阅。
     *
     * @return Deferred 对象，可通过 await() 获取取消结果
     */
    fun unsubscribeAsync(): Deferred<Result<Unit>>

    // endregion

    // region 订阅管理方法

    /**
     * 暂停订阅
     *
     * 暂停接收消息，但保持订阅状态。暂停状态下会忽略所有消息，不进行缓冲。
     *
     * @return 暂停结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun pause(): Result<Unit>

    /**
     * 恢复订阅
     *
     * 恢复接收消息。
     *
     * @return 恢复结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun resume(): Result<Unit>

    /**
     * 检查订阅是否暂停
     */
    val isPaused: Boolean

    /**
     * 更新消息处理器
     *
     * @param handler 新的消息处理函数
     * @return 更新结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun updateHandler(handler: suspend (message: Any) -> Unit): Result<Unit>

    // endregion

    // region 统计信息

    /**
     * 重置统计信息
     */
    fun resetStats()

    // endregion

    // region 工具方法

    /**
     * 等待订阅完成
     *
     * 阻塞当前协程，直到订阅被取消或发生错误。
     *
     * @return 完成结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun await(): Result<Unit>

    /**
     * 添加完成回调
     *
     * @param callback 完成回调函数
     */
    fun onComplete(callback: (Result<Unit>) -> Unit)

    /**
     * 添加错误回调
     *
     * @param callback 错误回调函数
     */
    fun onError(callback: (Throwable) -> Unit)

    // endregion
}

/**
 * 订阅统计信息
 */
data class SubscriptionStats(
    val messageCount: Long = 0L,
    val errorCount: Long = 0L,
    val lastMessageTime: Long? = null,
    val averageProcessingTime: Double = 0.0,
)

/**
 * 订阅管理器
 *
 * 管理多个订阅，提供批量操作。
 */
interface SubscriptionManager {

    /**
     * 获取所有订阅
     */
    val subscriptions: List<Subscription>

    /**
     * 获取活跃订阅数量
     */
    val activeSubscriptionCount: Int

    /**
     * 根据 ID 获取订阅
     */
    fun getSubscription(id: String): Subscription?

    /**
     * 根据主题获取订阅
     */
    fun getSubscriptionsByTopic(topic: String): List<Subscription>

    /**
     * 取消所有订阅
     *
     * @return 取消结果，包含每个订阅的取消结果
     */
    suspend fun unsubscribeAll(): Map<String, Result<Unit>>

    /**
     * 暂停所有订阅
     *
     * @return 暂停结果，包含每个订阅的暂停结果
     */
    suspend fun pauseAll(): Map<String, Result<Unit>>

    /**
     * 恢复所有订阅
     *
     * @return 恢复结果，包含每个订阅的恢复结果
     */
    suspend fun resumeAll(): Map<String, Result<Unit>>

    /**
     * 获取所有订阅的统计信息
     */
    fun getAllStats(): Map<String, SubscriptionStats>
}
