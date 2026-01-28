package club.plutoproject.charonflow.internal.core

import club.plutoproject.charonflow.DetailedSubscriptionStats
import club.plutoproject.charonflow.Subscription
import club.plutoproject.charonflow.SubscriptionNotFoundException
import club.plutoproject.charonflow.SubscriptionStats
import club.plutoproject.charonflow.internal.logger
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

/**
 * PubSub 实现的订阅接口
 *
 * 提供针对 Pub/Sub 订阅的具体实现。
 */
internal class PubSubSubscription(
    override val id: String,
    override val topic: String,
    override val createdAt: Long = System.currentTimeMillis(),
    override val lastActivityTime: Long = System.currentTimeMillis(),
    private var _isActive: Boolean = true,
    private var _isPaused: Boolean = false,
    override val messageCount: Long = 0L,
    stats: SubscriptionStats = SubscriptionStats(
        messageCount = 0L,
        errorCount = 0L,
        lastMessageTime = null,
        averageProcessingTime = 0.0,
        isActive = true,
        isPaused = false
    ),
    /**
     * 消息处理函数
     */
    private val handler: suspend (message: Any) -> Unit,
    /**
     * 订阅的消息类型（FQN）
     * 用于类型匹配，Any::class 时使用 "kotlin.Any"
     */
    val messageType: String
) : Subscription {

    private var _lastActivityTime: Long = lastActivityTime
    private val _stats = AtomicReference(stats)

    // region 属性

    override val stats: SubscriptionStats
        get() = _stats.get()

    override val isActive: Boolean
        get() = _isActive && !_isPaused

    override val isPaused: Boolean
        get() = _isPaused

    // endregion

    // region 订阅管理方法

    override suspend fun pause(): Result<Unit> {
        if (!_isActive) {
            return Result.failure(
                SubscriptionNotFoundException(
                    "Subscription $id is not active",
                    subscriptionId = id,
                    topic = topic
                )
            )
        }

        _isPaused = true
        updateLastActivityTime()
        updateStats { it.copy(isPaused = true) }
        return Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> {
        if (!_isActive) {
            return Result.failure(
                SubscriptionNotFoundException(
                    "Subscription $id is not active",
                    subscriptionId = id,
                    topic = topic
                )
            )
        }

        _isPaused = false
        updateLastActivityTime()
        updateStats { it.copy(isPaused = false) }
        return Result.success(Unit)
    }

    override suspend fun updateHandler(handler: suspend (message: Any) -> Unit): Result<Unit> {
        // TODO: 实现更新逻辑（需要将 handler 存储为成员变量）
        updateLastActivityTime()
        logger.warn("updateHandler is not yet implemented")
        return Result.failure(UnsupportedOperationException("updateHandler is not yet implemented"))
    }

    // endregion

    // region 取消订阅方法

    override suspend fun unsubscribe(): Result<Unit> {
        _isActive = false
        updateLastActivityTime()
        updateStats { it.copy(isActive = false) }
        // TODO: 实际的订阅取消逻辑
        return Result.success(Unit)
    }

    override fun unsubscribeAsync() {
        TODO("实际实现异步取消逻辑")
    }

    // endregion

    // region 统计信息

    override fun resetStats() {
        // TODO: 实现统计重置
    }

    override fun getDetailedStats(): DetailedSubscriptionStats {
        val basicStats = stats
        return DetailedSubscriptionStats(
            basicStats = basicStats,
            messageRate = 0.0,
            errorRate = 0.0,
            processingTimes = emptyList(),
            messageTypes = emptyMap(),
            lastErrors = emptyList()
        )
    }

    // endregion

    // region 工具方法

    override suspend fun await(): Result<Unit> {
        while (isActive) {
            // TODO: 实现等待逻辑
            delay(100)
        }
        return Result.success(Unit)
    }

    override fun onComplete(callback: (Result<Unit>) -> Unit) {
        TODO("实现完成回调")
    }

    override fun onError(callback: (Throwable) -> Unit) {
        TODO("实现错误回调")
    }

    // endregion

    // region 内部方法

    /**
     * 处理接收到的消息
     *
     * @param message 消息内容
     * @return 处理结果，true 表示成功处理，false 表示消息被忽略（如暂停状态）
     */
    internal suspend fun handleReceivedMessage(message: Any): Boolean {
        if (!canProcessMessages()) {
            return false
        }

        updateLastActivityTime()
        return try {
            handler(message)
            incrementMessageCount()
            true
        } catch (e: Exception) {
            logger.error(
                "Handler threw exception in subscription {}, cancelling subscription. Error: {}", id, e.message, e
            )
            _isActive = false
            updateStats { it.copy(isActive = false) }
            false
        }
    }

    /**
     * 增加消息计数
     */
    internal fun incrementMessageCount() {
        updateStats { it.copy(messageCount = it.messageCount + 1) }
    }

    private fun canProcessMessages(): Boolean {
        return _isActive && !_isPaused
    }

    private fun updateLastActivityTime() {
        _lastActivityTime = System.currentTimeMillis()
    }

    private fun updateStats(update: (SubscriptionStats) -> SubscriptionStats) {
        // CAS 并发安全地更新 stats
        while (true) {
            val current = _stats.get()
            val newStats = update(current)
            if (_stats.compareAndSet(current, newStats)) {
                break
            }
        }
    }

    // endregion
}
