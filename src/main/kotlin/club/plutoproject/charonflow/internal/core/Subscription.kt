package club.plutoproject.charonflow.internal.core

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
     * 立即返回，在后台取消订阅。
     */
    fun unsubscribeAsync()

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

    /**
     * 获取详细统计信息
     */
    fun getDetailedStats(): DetailedSubscriptionStats

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
    val messageCount: Long,
    val errorCount: Long,
    val lastMessageTime: Long?,
    val averageProcessingTime: Double,
    val isActive: Boolean,
    val isPaused: Boolean
)

/**
 * 详细订阅统计信息
 */
data class DetailedSubscriptionStats(
    val basicStats: SubscriptionStats,
    val messageRate: Double, // 消息/秒
    val errorRate: Double,   // 错误/秒
    val processingTimes: List<Long>, // 最近 N 条消息的处理时间
    val messageTypes: Map<String, Int>, // 消息类型分布
    val lastErrors: List<Pair<Long, String>> // 最近错误（时间戳，错误信息）
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
