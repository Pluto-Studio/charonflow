package club.plutoproject.charonflow.internal

import club.plutoproject.charonflow.CharonFlow
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
import club.plutoproject.charonflow.internal.retry.RetryExecutor
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
    private val serializationManager = SerializationManager(serializersModule = config.serializersModule, config = config)
    private val pubSubManager = PubSubManager(this)
    private val retryExecutor = RetryExecutor()
    private val connectionManager = RedisConnectionManager(config, retryExecutor)
    private val cbor = Cbor {
        serializersModule = config.serializersModule
    }
    // 已订阅的主题集合（避免重复订阅）
    private val subscribedTopics = mutableSetOf<String>()

    override val isConnected: Boolean
        get() = connectionManager.isConnected

    override suspend fun subscribe(
        topic: String,
        ignoreSelf: Boolean?,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, Any::class, ignoreSelf, handler)
    }

    override suspend fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        ignoreSelf: Boolean?,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, clazz, ignoreSelf, handler)
    }

    /**
     * 订阅的内部实现
     */
    private suspend fun <T : Any> subscribeInternal(
        topic: String,
        clazz: KClass<T>,
        ignoreSelf: Boolean?,
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
        // 使用传入值或全局默认值
        val shouldIgnoreSelf = ignoreSelf ?: config.ignoreSelfPubSubMessages
        val subscription = PubSubSubscription(
            id = UUID.randomUUID().toString(),
            topic = topic,
            handler = { msg ->
                @Suppress("UNCHECKED_CAST")
                handler(msg as T)
            },
            messageType = messageType,
            coroutineScope = coroutineScope,
            ignoreSelf = shouldIgnoreSelf,
            clientId = config.clientId
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

            // 使用 Redis 发布（带重试）
            retryExecutor.executeWithRetry(
                config = config.retryPolicyConfig.messageRetry,
                operationName = "Publish message to topic '$topic'"
            ) {
                val connection = connectionManager.getConnection()
                connection.sync().publish(topic, messageBytes)
            }

            Result.success(Unit)
        } catch (e: SerializeFailedException) {
            logger.error("Failed to serialize message for topic {}: {}", topic, e.message, e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Error publishing message to topic {}: {}", topic, e.message, e)
            Result.failure(e)
        }
    }

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
                    // 若没有配置则为 null，在 TypeResolver 里会调用默认行为
                    val classLoader = config.classLoader
                    // 获取目标类型的 KClass
                    @Suppress("UNCHECKED_CAST")
                    val targetClass = TypeResolver.getKClass<Any>(subscription.messageType, classLoader)
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

            // 分发到 handler，传递消息来源用于 ignoreSelf 检查
            val processed = subscription.handleReceivedMessage(deserializedMessage, message.source)
            if (!processed) {
                logger.debug(
                    "Message not processed by subscription {} (possibly paused, ignored self message, or handler threw exception)",
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

    override fun close() {
        coroutineScope.cancel()
        pubSubManager.close()
        connectionManager.close()
    }

    override suspend fun subscribeToRedis(topic: String): Result<Unit> {
        return try {
            if (subscribedTopics.add(topic)) {
                // 使用重试执行器执行订阅操作
                retryExecutor.executeWithRetry(
                    config = config.retryPolicyConfig.connectionRetry,
                    operationName = "Subscribe to Redis topic '$topic'"
                ) {
                    val pubSubConn = connectionManager.getPubSubConnection()
                    pubSubConn.addListener(PubSubMessageListener(this))
                    pubSubConn.async().subscribe(topic).await()
                    logger.debug("Subscribed to Redis topic: {}", topic)
                }
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
                // 使用重试执行器执行取消订阅操作
                retryExecutor.executeWithRetry(
                    config = config.retryPolicyConfig.connectionRetry,
                    operationName = "Unsubscribe from Redis topic '$topic'"
                ) {
                    val pubSubConn = connectionManager.getPubSubConnection()
                    pubSubConn.async().unsubscribe(topic).await()
                    logger.debug("Unsubscribed from Redis topic: {}", topic)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe from Redis topic: {}", topic, e)
            Result.failure(e)
        }
    }
}
