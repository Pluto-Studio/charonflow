package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.CharonFlowConfig
import club.plutoproject.charonflow.dsl.CharonFlowConfigDsl
import club.plutoproject.charonflow.internal.CharonFlowImpl
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * CharonFlow 主接口。
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

    /**
     * 获取当前配置
     */
    val config: CharonFlowConfig

    /**
     * 检查是否已连接
     */
    val isConnected: Boolean

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
     * @param ignoreSelf 是否忽略自身发送的消息，null 表示使用全局配置
     * @param handler 消息处理函数，接收反序列化后的消息对象
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun subscribe(
        topic: String,
        ignoreSelf: Boolean? = null,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription>

    /**
     * 订阅指定主题（类型安全版本）
     *
     * @param T 消息类型
     * @param topic 主题名称
     * @param ignoreSelf 是否忽略自身发送的消息，null 表示使用全局配置
     * @param handler 消息处理函数，接收类型安全的消息对象
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        ignoreSelf: Boolean? = null,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription>
}
