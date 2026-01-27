package club.plutoproject.charonflow.core

import kotlinx.serialization.Serializable
import java.util.*

/**
 * 消息抽象
 *
 * 所有通过 CharonFlow 传输的消息都包装在此类中。
 * 包含消息元数据和实际内容。
 */
@Serializable
data class Message(
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

    /**
     * 消息目标
     * 接收消息的客户端标识（用于点对点通信）
     */
    val target: String? = null,

    /**
     * 消息优先级
     * 0-9，数字越大优先级越高
     */
    val priority: Int = 5,

    /**
     * 消息过期时间（毫秒）
     * 0 表示永不过期
     */
    val ttl: Long = 0L,

    /**
     * 关联 ID
     * 用于关联请求和响应
     */
    val correlationId: String? = null,

    /**
     * 回复地址
     * 用于请求-响应模式，指定响应发送到哪里
     */
    val replyTo: String? = null
) {
    init {
        require(topic.isNotBlank()) { "topic must not be blank" }
        require(payloadType.isNotBlank()) { "payloadType must not be blank" }
        require(priority in 0..9) { "Priority must be between 0 and 9" }
        require(ttl >= 0) { "TTL must be non-negative" }
    }

    /**
     * 检查消息是否已过期
     */
    fun isExpired(): Boolean {
        if (ttl == 0L) return false
        val currentTime = System.currentTimeMillis()
        return currentTime > timestamp + ttl
    }

    /**
     * 获取消息剩余存活时间
     *
     * @return 剩余时间（毫秒），0 表示已过期或永不过期
     */
    fun getRemainingTtl(): Long {
        if (ttl == 0L) return 0L
        val currentTime = System.currentTimeMillis()
        val remaining = timestamp + ttl - currentTime
        return maxOf(remaining, 0)
    }

    /**
     * 添加消息头
     *
     * @param key 头键
     * @param value 头值
     * @return 新的 Message 实例
     */
    fun withHeader(key: String, value: String): Message {
        val newHeaders = headers.toMutableMap()
        newHeaders[key] = value
        return copy(headers = newHeaders)
    }

    /**
     * 添加多个消息头
     *
     * @param newHeaders 要添加的消息头
     * @return 新的 Message 实例
     */
    fun withHeaders(newHeaders: Map<String, String>): Message {
        val mergedHeaders = headers.toMutableMap()
        mergedHeaders.putAll(newHeaders)
        return copy(headers = mergedHeaders)
    }

    /**
     * 移除消息头
     *
     * @param key 要移除的头键
     * @return 新的 Message 实例
     */
    fun withoutHeader(key: String): Message {
        val newHeaders = headers.toMutableMap()
        newHeaders.remove(key)
        return copy(headers = newHeaders)
    }

    /**
     * 获取消息头值
     *
     * @param key 头键
     * @return 头值，如果不存在返回 null
     */
    fun getHeader(key: String): String? = headers[key]

    /**
     * 检查是否包含指定消息头
     *
     * @param key 头键
     * @return 如果包含返回 true
     */
    fun hasHeader(key: String): Boolean = headers.containsKey(key)

    /**
     * 设置消息来源
     *
     * @param source 消息来源
     * @return 新的 Message 实例
     */
    fun withSource(source: String): Message = copy(source = source)

    /**
     * 设置消息目标
     *
     * @param target 消息目标
     * @return 新的 Message 实例
     */
    fun withTarget(target: String): Message = copy(target = target)

    /**
     * 设置关联 ID
     *
     * @param correlationId 关联 ID
     * @return 新的 Message 实例
     */
    fun withCorrelationId(correlationId: String): Message = copy(correlationId = correlationId)

    /**
     * 设置回复地址
     *
     * @param replyTo 回复地址
     * @return 新的 Message 实例
     */
    fun withReplyTo(replyTo: String): Message = copy(replyTo = replyTo)

    /**
     * 设置消息主题
     *
     * @param topic 消息主题
     * @return 新的 Message 实例
     */
    fun withTopic(topic: String): Message = copy(topic = topic)

    /**
     * 设置消息优先级
     *
     * @param priority 优先级（0-9）
     * @return 新的 Message 实例
     */
    fun withPriority(priority: Int): Message = copy(priority = priority)

    /**
     * 设置消息过期时间
     *
     * @param ttl 过期时间（毫秒）
     * @return 新的 Message 实例
     */
    fun withTtl(ttl: Long): Message = copy(ttl = ttl)


}
