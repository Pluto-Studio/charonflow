package club.plutoproject.charonflow.internal

import club.plutoproject.charonflow.CharonFlow
import club.plutoproject.charonflow.Stats
import club.plutoproject.charonflow.config.CharonFlowConfig
import club.plutoproject.charonflow.internal.core.Message
import club.plutoproject.charonflow.internal.core.PubSubManager
import club.plutoproject.charonflow.internal.core.PubSubSubscription
import club.plutoproject.charonflow.internal.core.RedisSubscriptionHandler
import club.plutoproject.charonflow.Subscription
import club.plutoproject.charonflow.SerializeFailedException
import club.plutoproject.charonflow.SubscriptionFailedException
import club.plutoproject.charonflow.TypeNotRegisteredException
import club.plutoproject.charonflow.internal.serialization.SerializationManager
import club.plutoproject.charonflow.internal.serialization.TypeResolver
import club.plutoproject.charonflow.internal.transport.PubSubMessageListener
import club.plutoproject.charonflow.internal.transport.RedisConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass

internal val logger = LoggerFactory.getLogger("CharonFlow")

/**
 * CharonFlow 公共实现类
 *
 * 实现 CharonFlow 接口，提供多种通讯模式的统一实现。
 */
@OptIn(ExperimentalSerializationApi::class)
internal class CharonFlowImpl(
    override val config: CharonFlowConfig
) : CharonFlow, RedisSubscriptionHandler {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serializationManager = SerializationManager(config.serializersModule)
    private val pubSubManager = PubSubManager(this)
    private val connectionManager = RedisConnectionManager(config)
    private val cbor = Cbor {
        serializersModule = config.serializersModule
    }

    // 已订阅的主题集合（避免重复订阅）
    private val subscribedTopics = mutableSetOf<String>()

    // region 状态管理

    override val isConnected: Boolean
        get() = connectionManager.isConnected

    // endregion

    // region Pub/Sub 模式

    override suspend fun subscribe(
        topic: String,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, Any::class, handler)
    }

    override suspend fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, clazz, handler)
    }

    /**
     * 订阅的内部实现
     */
    private suspend fun <T : Any> subscribeInternal(
        topic: String,
        clazz: KClass<T>,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription> {
        if (clazz != Any::class && !serializationManager.isSerializable(clazz)) {
            return Result.failure(
                TypeNotRegisteredException(
                    message = "Type '${clazz.qualifiedName}' is not serializable",
                    typeName = clazz.qualifiedName
                )
            )
        }

        val messageType = clazz.qualifiedName ?: "kotlin.Any"
        val subscription = PubSubSubscription(
            id = UUID.randomUUID().toString(),
            topic = topic,
            handler = { msg ->
                @Suppress("UNCHECKED_CAST")
                handler(msg as T)
            },
            messageType = messageType,
            coroutineScope = coroutineScope
        )

        pubSubManager.addSubscription(subscription)

        // 订阅 Redis（如果是该主题的第一个订阅）
        val redisResult = subscribeToRedis(topic)
        if (redisResult.isFailure) {
            pubSubManager.removeSubscription(subscription.id)
            val exception = redisResult.exceptionOrNull()
                ?: Exception("Unknown error occurred during Redis subscription")
            return Result.failure(
                SubscriptionFailedException(
                    message = "Failed to subscribe to Redis topic: $topic",
                    cause = exception,
                    topic = topic,
                    operation = "subscribe"
                )
            )
        }

        return Result.success(subscription)
    }

    override suspend fun publish(topic: String, message: Any): Result<Unit> {
        return try {
            val bytes = serializationManager.serialize(message)
            val messageToSend = Message(
                topic = topic,
                payload = bytes,
                payloadType = message::class.qualifiedName ?: throw SerializeFailedException(
                    message = "Cannot determine type for message",
                    targetType = null
                ),
                source = config.clientId
            )

            // 序列化整个 Message 对象
            val messageBytes = cbor.encodeToByteArray(messageToSend)

            logger.debug(
                "Publishing message to topic {}: type={}, size={}",
                topic,
                messageToSend.payloadType,
                bytes.size
            )

            // 使用 Redis 发布
            val connection = connectionManager.getConnection()
            connection.sync().publish(topic, messageBytes)

            Result.success(Unit)
        } catch (e: SerializeFailedException) {
            logger.error("Failed to serialize message for topic {}: {}", topic, e.message, e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Error publishing message to topic {}: {}", topic, e.message, e)
            Result.failure(e)
        }
    }

    // endregion

    // region 组播和广播模式（MVP 暂不实现）

    override suspend fun joinMulticastGroup(group: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun leaveMulticastGroup(group: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun multicast(group: String, message: Any): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun onMulticast(
        group: String,
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun broadcast(channel: String, message: Any): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun onBroadcast(
        channel: String,
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    // endregion

    // region 工具方法

    override fun getStats(): Stats = Stats(
        messagesPublished = 0L,
        messagesReceived = 0L,
        requestsSent = 0L,
        requestsReceived = 0L,
        rpcCalls = 0L,
        errors = 0L,
        connectionAttempts = 0L,
        reconnections = 0L
    )

    override fun resetStats() {
        // TODO: 实现统计重置
    }

    // endregion

    // region 内部消息处理

    /**
     * 处理接收到的消息
     *
     * 此方法由 Redis 订阅回调调用，负责：
     * 1. 查找匹配的订阅
     * 2. 进行类型匹配检查
     * 3. 反序列化消息
     * 4. 分发到订阅的 handler
     *
     * @param message 接收到的消息
     */
    internal suspend fun handleIncomingMessage(message: Message) {
        val subscriptions = pubSubManager.getSubscriptionsByTopic(message.topic)
        if (subscriptions.isEmpty()) {
            logger.debug("No subscriptions found for topic: {}", message.topic)
            return
        }

        subscriptions.forEach { subscription ->
            // 类型匹配检查
            if (!isTypeMatch(message.payloadType, subscription.messageType)) {
                logger.debug(
                    "Type mismatch for subscription {}: payload type '{}' does not match subscription type '{}'. Skipping.",
                    subscription.id, message.payloadType, subscription.messageType
                )
                return@forEach
            }

            // 反序列化消息
            val deserializedMessage = when (subscription.messageType) {
                Any::class.qualifiedName -> {
                    // Any 类型特殊处理：按实际 payloadType 反序列化
                    serializationManager.deserializeAsAny(message.payload, message.payloadType)
                }

                else -> {
                    // 获取目标类型的 KClass
                    @Suppress("UNCHECKED_CAST")
                    val targetClass = TypeResolver.getKClass<Any>(subscription.messageType)
                    if (targetClass == null) {
                        logger.warn("Cannot find class for type: {}", subscription.messageType)
                        return@forEach
                    }
                    serializationManager.deserialize(message.payload, message.payloadType, targetClass)
                }
            }

            if (deserializedMessage == null) {
                logger.warn(
                    "Failed to deserialize message of type '{}' for subscription {}",
                    message.payloadType,
                    subscription.id
                )
                return@forEach
            }

            // 分发到 handler
            val processed = subscription.handleReceivedMessage(deserializedMessage)
            if (!processed) {
                logger.debug(
                    "Message not processed by subscription {} (possibly paused or handler threw exception)",
                    subscription.id
                )
            }
        }
    }

    /**
     * 检查类型是否匹配
     *
     * @param payloadType 消息中的类型（FQN）
     * @param subscriptionType 订阅的类型（FQN）
     * @return 如果匹配返回 true
     */
    private fun isTypeMatch(payloadType: String, subscriptionType: String): Boolean {
        // Any 类型订阅接收所有消息
        if (subscriptionType == "kotlin.Any") {
            return true
        }
        // 严格匹配
        return payloadType == subscriptionType
    }

    // endregion

    override fun close() {
        coroutineScope.cancel()
        pubSubManager.close()
        connectionManager.close()
    }

    // region RedisSubscriptionHandler 实现

    override suspend fun subscribeToRedis(topic: String): Result<Unit> {
        return try {
            if (subscribedTopics.add(topic)) {
                val pubSubConn = connectionManager.getPubSubConnection()
                pubSubConn.addListener(PubSubMessageListener(this))
                pubSubConn.async().subscribe(topic).await()
                logger.debug("Subscribed to Redis topic: {}", topic)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            subscribedTopics.remove(topic)
            logger.error("Failed to subscribe to Redis topic: {}", topic, e)
            Result.failure(e)
        }
    }

    override suspend fun unsubscribeFromRedis(topic: String): Result<Unit> {
        return try {
            if (subscribedTopics.remove(topic)) {
                val pubSubConn = connectionManager.getPubSubConnection()
                pubSubConn.async().unsubscribe(topic).await()
                logger.debug("Unsubscribed from Redis topic: {}", topic)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe from Redis topic: {}", topic, e)
            Result.failure(e)
        }
    }

    // endregion
}
