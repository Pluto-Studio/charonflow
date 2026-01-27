package club.plutoproject.charonflow.core

import club.plutoproject.charonflow.config.Config
import club.plutoproject.charonflow.core.exceptions.SubscriptionNotFoundException
import kotlinx.serialization.Serializable

/**
 * Pub/Sub 消息
 *
 * 包含发布者信息、消息内容和主题信息。
 */
@Serializable
data class PubSubMessage(
    val topic: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val publisher: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    init {
        require(topic.isNotBlank()) { "Topic cannot be blank" }
        require(message.isNotBlank()) { "Message content cannot be blank" }
        require(timestamp >= 0) { "Timestamp must be non-negative" }
    }
    
    fun withHeader(key: String, value: String): PubSubMessage {
        return copy(headers = headers + (key to value))
    }
    
    fun withPublisher(publisher: String): PubSubMessage {
        return copy(publisher = publisher)
    }
}