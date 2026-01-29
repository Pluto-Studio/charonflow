package club.plutoproject.charonflow

import kotlinx.coroutines.Deferred

/**
 * 订阅接口
 *
 * 表示一个活跃的订阅，提供取消订阅和管理订阅的方法。
 */
interface Subscription {
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
}
