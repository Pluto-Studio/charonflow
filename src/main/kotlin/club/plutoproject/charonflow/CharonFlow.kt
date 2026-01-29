package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.CharonFlowConfig
import club.plutoproject.charonflow.dsl.CharonFlowConfigDsl
import club.plutoproject.charonflow.dsl.serialization
import club.plutoproject.charonflow.internal.CharonFlowImpl
import kotlinx.serialization.modules.SerializersModule
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * CharonFlow 主接口
 *
 * 提供多种通讯模式的统一 API：
 * - Pub/Sub（发布-订阅）
 * - Req/Rsp（请求-响应）
 * - RPC（远程过程调用）
 * - Multicast/Broadcast（组播/广播）
 * - Stream RPC（流式 RPC）
 */
interface CharonFlow : Closeable {

    companion object {
        /**
         * 创建 CharonFlow 实例
         *
         * @param config 配置对象
         * @return CharonFlow 实例
         */
        fun create(config: CharonFlowConfig): CharonFlow {
            return CharonFlowImpl(config)
        }

        /**
         * 使用 DSL 创建 CharonFlow 实例
         *
         * @param block DSL 配置块
         * @return CharonFlow 实例
         *
         * 示例：
         * ```kotlin
         * val charonFlow = CharonFlow.create {
         *     redisUri = "redis://localhost:6379"
         *     timeout = 10.seconds
         *
         *     serialization {
         *         encodeDefaults = true
         *     }
         *
         *     connectionPool {
         *         maxTotal = 10
         *     }
         * }
         * ```
         */
        fun create(block: CharonFlowConfigDsl.() -> Unit): CharonFlow {
            val config = CharonFlowConfigDsl().apply(block).build()
            return CharonFlowImpl(config)
        }
    }

    // region 配置和状态

    /**
     * 获取当前配置
     */
    val config: CharonFlowConfig

    /**
     * 检查是否已连接
     */
    val isConnected: Boolean

    // endregion

    // region Pub/Sub 模式

    /**
     * 发布消息到指定主题
     *
     * @param topic 主题名称
     * @param message 消息内容（必须是可序列化的类型）
     * @return 发布结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun publish(topic: String, message: Any): Result<Unit>

    /**
     * 订阅指定主题（接收所有类型）
     *
     * @param topic 主题名称
     * @param handler 消息处理函数，接收反序列化后的消息对象
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun subscribe(
        topic: String,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription>

    /**
     * 订阅指定主题（类型安全版本）
     *
     * @param T 消息类型
     * @param topic 主题名称
     * @param handler 消息处理函数，接收类型安全的消息对象
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription>

    // endregion

    // region 组播和广播模式

    /**
     * 加入组播组
     *
     * @param group 组播组名称
     * @return 加入结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun joinMulticastGroup(group: String): Result<Unit>

    /**
     * 离开组播组
     *
     * @param group 组播组名称
     * @return 离开结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun leaveMulticastGroup(group: String): Result<Unit>

    /**
     * 发送组播消息
     *
     * @param group 组播组名称
     * @param message 消息内容
     * @return 发送结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun multicast(group: String, message: Any): Result<Unit>

    /**
     * 监听组播消息
     *
     * @param group 组播组名称
     * @param handler 消息处理函数
     * @return 监听结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun onMulticast(
        group: String,
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription>

    /**
     * 发送广播消息
     *
     * @param channel 广播通道名称
     * @param message 消息内容
     * @return 发送结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun broadcast(channel: String, message: Any): Result<Unit>

    /**
     * 监听广播消息
     *
     * @param channel 广播通道名称
     * @param handler 消息处理函数
     * @return 监听结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun onBroadcast(
        channel: String,
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription>

    // endregion

    // region 工具方法

    /**
     * 获取统计信息
     *
     * @return 当前统计信息
     */
    fun getStats(): Stats

    /**
     * 重置统计信息
     */
    fun resetStats()

    // endregion
}

/**
 * 统计信息
 */
data class Stats(
    val messagesPublished: Long,
    val messagesReceived: Long,
    val requestsSent: Long,
    val requestsReceived: Long,
    val rpcCalls: Long,
    val errors: Long,
    val connectionAttempts: Long,
    val reconnections: Long
)
