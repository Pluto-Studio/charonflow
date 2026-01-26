package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.Config
import club.plutoproject.charonflow.core.Subscription
import club.plutoproject.charonflow.core.RpcRequest
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

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
    
    // region 配置和状态
    
    /**
     * 获取当前配置
     */
    val config: Config
    
    /**
     * 检查是否已连接
     */
    val isConnected: Boolean
    
    /**
     * 获取连接状态信息
     */
    val connectionInfo: ConnectionInfo
    
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
     * 订阅指定主题
     *
     * @param topic 主题名称
     * @param handler 消息处理函数，接收消息和取消函数
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun subscribe(
        topic: String,
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription>
    
    /**
     * 订阅指定主题（类型安全版本）
     *
     * @param T 消息类型
     * @param topic 主题名称
     * @param handler 消息处理函数，接收类型安全的消息和取消函数
     * @return 订阅结果，成功返回 Subscription，失败返回错误信息
     */
    suspend fun <T : Any> subscribe(
        topic: String,
        clazz: Class<T>,
        handler: suspend (message: T, cancel: () -> Unit) -> Unit
    ): Result<Subscription>
    
    // endregion
    
    // region 请求-响应模式
    
    /**
     * 发送请求并等待响应
     *
     * @param T 响应类型
     * @param channel 请求通道名称
     * @param request 请求内容
     * @return 响应结果，成功返回响应数据，失败返回错误信息
     */
    suspend fun <T : Any> request(channel: String, request: Any): Result<T>
    
    /**
     * 注册请求处理器
     *
     * @param channel 请求通道名称
     * @param handler 请求处理函数，接收请求并返回响应
     * @return 注册结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun onRequest(
        channel: String,
        handler: suspend (request: Any) -> Any
    ): Result<Unit>
    
    /**
     * 注册请求处理器（类型安全版本）
     *
     * @param T 请求类型
     * @param R 响应类型
     * @param channel 请求通道名称
     * @param handler 请求处理函数，接收类型安全的请求并返回响应
     * @return 注册结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun <T : Any, R : Any> onRequest(
        channel: String,
        requestClass: Class<T>,
        handler: suspend (request: T) -> R
    ): Result<Unit>
    
    // endregion
    
    // region RPC 模式
    
    /**
     * 远程过程调用（单参数）
     *
     * @param T 参数类型
     * @param R 返回值类型
     * @param method 方法名称
     * @param param 参数值
     * @return 调用结果，成功返回返回值，失败返回错误信息
     */
    suspend fun <T : Any, R : Any> rpc(method: String, param: T): Result<R>
    
    /**
     * 远程过程调用（多参数）
     *
     * @param R 返回值类型
     * @param method 方法名称
     * @param request 包含多个参数的请求对象
     * @return 调用结果，成功返回返回值，失败返回错误信息
     */
    suspend fun <R : Any> rpc(method: String, request: RpcRequest): Result<R>
    
    /**
     * 远程过程调用（可变参数）
     *
     * @param R 返回值类型
     * @param method 方法名称
     * @param params 可变参数列表
     * @return 调用结果，成功返回返回值，失败返回错误信息
     */
    suspend fun <R : Any> rpc(method: String, vararg params: Any): Result<R>
    
    /**
     * 注册 RPC 方法处理器
     *
     * @param T 参数类型
     * @param R 返回值类型
     * @param method 方法名称
     * @param handler 方法处理函数
     * @return 注册结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun <T : Any, R : Any> registerRpc(
        method: String,
        handler: suspend (param: T) -> R
    ): Result<Unit>
    
    // endregion
    
    // region 流式 RPC 模式
    
    /**
     * 流式远程过程调用
     *
     * @param T 参数类型
     * @param R 流元素类型
     * @param method 方法名称
     * @param param 参数值
     * @return 流式响应
     */
    suspend fun <T : Any, R : Any> streamRpc(method: String, param: T): Flow<R>
    
    /**
     * 注册流式 RPC 方法处理器
     *
     * @param T 参数类型
     * @param R 流元素类型
     * @param method 方法名称
     * @param handler 流式方法处理函数
     * @return 注册结果，成功返回 Unit，失败返回错误信息
     */
    suspend fun <T : Any, R : Any> registerStreamRpc(
        method: String,
        handler: suspend (param: T) -> Flow<R>
    ): Result<Unit>
    
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
     * 健康检查
     *
     * @return 健康状态，true 表示健康，false 表示不健康
     */
    suspend fun healthCheck(): Boolean
    
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
 * 连接信息
 */
data class ConnectionInfo(
    val redisUri: String,
    val isConnected: Boolean,
    val connectionTime: Long,
    val lastActivityTime: Long,
    val activeSubscriptions: Int,
    val activeRequests: Int
)

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