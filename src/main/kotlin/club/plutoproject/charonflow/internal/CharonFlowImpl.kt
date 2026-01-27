package club.plutoproject.charonflow.internal

import club.plutoproject.charonflow.CharonFlow
import club.plutoproject.charonflow.ConnectionInfo
import club.plutoproject.charonflow.Stats
import club.plutoproject.charonflow.config.Config
import club.plutoproject.charonflow.core.Message
import club.plutoproject.charonflow.core.PubSubManager
import club.plutoproject.charonflow.core.PubSubSubscription
import club.plutoproject.charonflow.core.RpcRequest
import club.plutoproject.charonflow.core.Subscription
import club.plutoproject.charonflow.core.exceptions.SerializeFailedException
import club.plutoproject.charonflow.core.exceptions.TypeNotRegisteredException
import club.plutoproject.charonflow.internal.serialization.SerializationManager
import club.plutoproject.charonflow.internal.serialization.TypeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(CharonFlowImpl::class.java)

/**
 * CharonFlow 公共实现类
 *
 * 实现 CharonFlow 接口，提供多种通讯模式的统一实现。
 */
internal class CharonFlowImpl(
    override val config: Config
) : CharonFlow {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serializationManager = SerializationManager(config.serializersModule)
    private val pubSubManager = PubSubManager()

    // region 状态管理

    override val isConnected: Boolean
        get() = TODO("实际实现连接状态检查")

    override val connectionInfo: ConnectionInfo
        get() = TODO("实际实现连接信息获取")

    // endregion

    // region Pub/Sub 模式

    override fun subscribe(
        topic: String,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, Any::class, handler)
    }

    override fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription> {
        return subscribeInternal(topic, clazz, handler)
    }

    /**
     * 订阅的内部实现
     */
    private fun <T : Any> subscribeInternal(
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
            messageType = messageType
        )

        pubSubManager.addSubscription(subscription)
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

            logger.debug("Publishing message to topic {}: type={}, size={}", topic, messageToSend.payloadType, bytes.size)
            TODO("实际实现发布逻辑")
        } catch (e: SerializeFailedException) {
            logger.error("Failed to serialize message for topic {}: {}", topic, e.message, e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Error publishing message to topic {}: {}", topic, e.message, e)
            Result.failure(e)
        }
    }

    // endregion

    // region 其他模式（MVP 暂不实现）

    override suspend fun <T : Any> request(channel: String, request: Any): Result<T> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun onRequest(channel: String, handler: suspend (request: Any) -> Any): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <T : Any, R : Any> onRequest(
        channel: String,
        requestClass: Class<T>,
        handler: suspend (request: T) -> R
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <T : Any, R : Any> rpc(method: String, param: T): Result<R> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <R : Any> rpc(method: String, request: RpcRequest): Result<R> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <R : Any> rpc(method: String, vararg params: Any): Result<R> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <T : Any, R : Any> registerRpc(method: String, handler: suspend (param: T) -> R): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

    override suspend fun <T : Any, R : Any> streamRpc(method: String, param: T): Flow<R> = TODO("Not implemented yet")

    override suspend fun <T : Any, R : Any> registerStreamRpc(
        method: String,
        handler: suspend (param: T) -> Flow<R>
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not implemented yet"))

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

    override suspend fun healthCheck(): Boolean = false

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
                logger.warn("Failed to deserialize message of type '{}' for subscription {}", message.payloadType, subscription.id)
                return@forEach
            }

            // 分发到 handler
            val processed = subscription.handleReceivedMessage(deserializedMessage)
            if (!processed) {
                logger.debug("Message not processed by subscription {} (possibly paused or handler threw exception)", subscription.id)
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
    }
}
