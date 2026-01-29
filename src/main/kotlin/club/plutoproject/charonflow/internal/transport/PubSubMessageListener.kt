package club.plutoproject.charonflow.internal.transport

import club.plutoproject.charonflow.internal.core.Message
import club.plutoproject.charonflow.internal.CharonFlowImpl
import club.plutoproject.charonflow.internal.logger
import io.lettuce.core.pubsub.RedisPubSubListener
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.slf4j.LoggerFactory

/**
 * Pub/Sub 消息监听器
 *
 * 监听 Redis Pub/Sub 消息，并将其路由到 CharonFlowImpl 处理。
 */
@OptIn(ExperimentalSerializationApi::class)
internal class PubSubMessageListener(
    private val charonFlowImpl: CharonFlowImpl
) : RedisPubSubListener<String, ByteArray> {

    private val cbor = Cbor {
        serializersModule = charonFlowImpl.config.serializersModule
    }

    override fun message(channel: String, message: ByteArray) {
        handleMessage(channel, message)
    }

    override fun message(pattern: String, channel: String, message: ByteArray) {
        handleMessage(channel, message)
    }

    private fun handleMessage(channel: String, messageBytes: ByteArray) {
        try {
            val message = cbor.decodeFromByteArray<Message>(messageBytes)
            logger.debug("Received message on channel '{}': type={}", channel, message.payloadType)

            // 使用 CharonFlowImpl 处理消息
            kotlinx.coroutines.runBlocking {
                charonFlowImpl.handleIncomingMessage(message)
            }
        } catch (e: Exception) {
            logger.error("Failed to process message on channel '{}': {}", channel, e.message, e)
        }
    }

    override fun subscribed(channel: String, count: Long) {
        logger.debug("Subscribed to channel '{}', total subscriptions: {}", channel, count)
    }

    override fun unsubscribed(channel: String, count: Long) {
        logger.debug("Unsubscribed from channel '{}', remaining subscriptions: {}", channel, count)
    }

    override fun psubscribed(pattern: String, count: Long) {
        logger.debug("Subscribed to pattern '{}', total subscriptions: {}", pattern, count)
    }

    override fun punsubscribed(pattern: String, count: Long) {
        logger.debug("Unsubscribed from pattern '{}', remaining subscriptions: {}", pattern, count)
    }
}
