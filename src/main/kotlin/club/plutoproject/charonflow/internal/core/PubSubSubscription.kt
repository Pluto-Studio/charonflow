package club.plutoproject.charonflow.internal.core

import club.plutoproject.charonflow.DetailedSubscriptionStats
import club.plutoproject.charonflow.Subscription
import club.plutoproject.charonflow.SubscriptionNotFoundException
import club.plutoproject.charonflow.SubscriptionStats
import club.plutoproject.charonflow.internal.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.CopyOnWriteArrayList
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
    val messageType: String,
    private val coroutineScope: CoroutineScope
) : Subscription {

    private var _lastActivityTime: Long = lastActivityTime
    private val _stats = AtomicReference(stats)
    private val _isActive = AtomicBoolean(true)
    private val _isPaused = AtomicBoolean(false)
    private val _handler = AtomicReference(handler)
    private var _onUnsubscribeCallback: (suspend () -> Unit)? = null
    private val _completionDeferred = CompletableDeferred<Result<Unit>>()
    private val _onCompleteCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()
    private val _onErrorCallbacks = CopyOnWriteArrayList<(Throwable) -> Unit>()

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
        if (!_isActive.compareAndSet(true, false)) {
            _completionDeferred.complete(Result.success(Unit))
            return Result.success(Unit)
        }
        updateLastActivityTime()
        _onUnsubscribeCallback?.invoke()
        _completionDeferred.complete(Result.success(Unit))
        // 触发完成回调
        _onCompleteCallbacks.forEach { it(Result.success(Unit)) }
        logger.debug("Subscription {} unsubscribed", id)
        return Result.success(Unit)
    }

    /**
     * 设置取消订阅时的回调
     */
    internal fun setOnUnsubscribeCallback(callback: suspend () -> Unit) {
        _onUnsubscribeCallback = callback
    }

    override fun unsubscribeAsync(): Deferred<Result<Unit>> {
        return coroutineScope.async {
            unsubscribe()
        }
    }

    // endregion

    // region 统计信息

    override fun resetStats() {
        _stats.set(SubscriptionStats())
        logger.debug("Stats reset for subscription {}", id)
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
        return _completionDeferred.await()
    }

    override fun onComplete(callback: (Result<Unit>) -> Unit) {
        _onCompleteCallbacks.add(callback)
        // 如果订阅已经完成，立即触发回调
        if (!_isActive.get() && _completionDeferred.isCompleted) {
            callback(Result.success(Unit))
        }
    }

    override fun onError(callback: (Throwable) -> Unit) {
        _onErrorCallbacks.add(callback)
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
            // 触发错误回调
            _onErrorCallbacks.forEach { it(e) }
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
