package club.plutoproject.charonflow.internal.core

import club.plutoproject.charonflow.DetailedSubscriptionStats
import club.plutoproject.charonflow.Subscription
import club.plutoproject.charonflow.SubscriptionNotFoundException
import club.plutoproject.charonflow.SubscriptionStats
import club.plutoproject.charonflow.internal.logger
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
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
    override val messageCount: Long = 0L,
    stats: SubscriptionStats = SubscriptionStats(
        messageCount = 0L,
        errorCount = 0L,
        lastMessageTime = null,
        averageProcessingTime = 0.0,
    ),
    private val handler: suspend (message: Any) -> Unit,
    val messageType: String
) : Subscription {

    private var _lastActivityTime: Long = lastActivityTime
    private val _stats = AtomicReference(stats)
    private val _isActive = AtomicBoolean(true)
    private val _isPaused = AtomicBoolean(false)
    private val _handler = AtomicReference(handler)

    // region 属性

    override val stats: SubscriptionStats
        get() = _stats.get()

    override val isActive: Boolean
        get() = _isActive.get() && !_isPaused.get()

    override val isPaused: Boolean
        get() = _isPaused.get()

    // endregion

    // region 订阅管理方法

    override suspend fun pause(): Result<Unit> {
        if (!_isActive.get()) {
            return Result.failure(
                SubscriptionNotFoundException(
                    "Subscription $id is not active",
                    subscriptionId = id,
                    topic = topic
                )
            )
        }

        if (_isPaused.get()) {
            return Result.success(Unit)
        }

        _isPaused.compareAndSet(false, true)
        updateLastActivityTime()
        return Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> {
        if (!_isActive.get()) {
            return Result.failure(
                SubscriptionNotFoundException(
                    "Subscription $id is not active",
                    subscriptionId = id,
                    topic = topic
                )
            )
        }

        if (!_isPaused.get()) {
            return Result.success(Unit)
        }

        _isPaused.compareAndSet(true, false)
        updateLastActivityTime()
        return Result.success(Unit)
    }

    override suspend fun updateHandler(handler: suspend (message: Any) -> Unit): Result<Unit> {
        if (!_isActive.get()) {
            return Result.failure(
                SubscriptionNotFoundException(
                    "Subscription $id is not active",
                    subscriptionId = id,
                    topic = topic
                )
            )
        }
        
        _handler.set(handler)
        updateLastActivityTime()
        logger.debug("Handler updated for subscription {}", id)
        return Result.success(Unit)
    }

    // endregion

    // region 取消订阅方法

    override suspend fun unsubscribe(): Result<Unit> {
        _isActive.compareAndSet(true, false)
        updateLastActivityTime()
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
            _handler.get()(message)
            incrementMessageCount()
            true
        } catch (e: Exception) {
            logger.error(
                "Handler threw exception in subscription {}, cancelling subscription. Error: {}", id, e.message, e
            )
            _isActive.compareAndSet(true, false)
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
        return _isActive.get() && !_isPaused.get()
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
