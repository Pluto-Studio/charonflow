package club.plutoproject.charonflow.internal.core

import kotlinx.serialization.Serializable
import java.util.*

/**
 * 消息抽象
 *
 * 所有通过 CharonFlow 传输的消息都包装在此类中。
 * 包含消息元数据和实际内容。
 */
@Serializable
internal data class Message(
    /**
     * 消息主题
     * 用于 Pub/Sub 模式，标识消息所属的频道/主题
     */
    val topic: String,

    /**
     * 消息负载
     * 用户数据的序列化结果（ByteArray）
     */
    val payload: ByteArray,

    /**
     * 负载类型
     * 类型的完全限定名（FQN），必需
     */
    val payloadType: String,

    /**
     * 消息 ID
     * 用于消息追踪和去重
     */
    val id: String = UUID.randomUUID().toString(),

    /**
     * 消息时间戳（毫秒）
     * 消息创建的时间
     */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * 消息头
     * 包含额外的元数据
     */
    val headers: Map<String, String> = emptyMap(),

    /**
     * 消息来源
     * 发送消息的客户端标识
     */
    val source: String? = null,
) {
    init {
        require(topic.isNotBlank()) { "topic must not be blank" }
        require(payloadType.isNotBlank()) { "payloadType must not be blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (timestamp != other.timestamp) return false
        if (topic != other.topic) return false
        if (!payload.contentEquals(other.payload)) return false
        if (payloadType != other.payloadType) return false
        if (id != other.id) return false
        if (headers != other.headers) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + payloadType.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (source?.hashCode() ?: 0)
        return result
    }

}
