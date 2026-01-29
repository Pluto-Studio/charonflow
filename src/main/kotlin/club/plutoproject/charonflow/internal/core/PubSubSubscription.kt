package club.plutoproject.charonflow.internal.core

import club.plutoproject.charonflow.Subscription
import club.plutoproject.charonflow.SubscriptionNotFoundException
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
    handler: suspend (message: Any) -> Unit,
    val messageType: String,
    private val coroutineScope: CoroutineScope,
    val ignoreSelf: Boolean = false,
    val clientId: String = ""
) : Subscription {

    private var _lastActivityTime: Long = lastActivityTime
    private val _isActive = AtomicBoolean(true)
    private val _isPaused = AtomicBoolean(false)
    private val _handler = AtomicReference(handler)
    private var _onUnsubscribeCallback: (suspend () -> Unit)? = null
    private val _completionDeferred = CompletableDeferred<Result<Unit>>()
    private val _onCompleteCallbacks = CopyOnWriteArrayList<(Result<Unit>) -> Unit>()
    private val _onErrorCallbacks = CopyOnWriteArrayList<(Throwable) -> Unit>()

    override val isActive: Boolean
        get() = _isActive.get() && !_isPaused.get()
    override val isPaused: Boolean
        get() = _isPaused.get()

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

    /**
     * 处理接收到的消息
     *
     * @param message 消息内容
     * @param messageSource 消息来源的 clientId，用于 ignoreSelf 检查
     * @return 处理结果，true 表示成功处理，false 表示消息被忽略（如暂停状态或自身消息）
     */
    internal suspend fun handleReceivedMessage(message: Any, messageSource: String? = null): Boolean {
        if (!canProcessMessages()) {
            return false
        }

        // 检查是否需要忽略自身消息
        if (ignoreSelf && messageSource != null && messageSource == clientId) {
            logger.debug("Ignoring self message in subscription {} from client {}", id, clientId)
            return false
        }

        updateLastActivityTime()
        return try {
            _handler.get()(message)
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

    private fun canProcessMessages(): Boolean {
        return _isActive.get() && !_isPaused.get()
    }

    private fun updateLastActivityTime() {
        _lastActivityTime = System.currentTimeMillis()
    }
}
