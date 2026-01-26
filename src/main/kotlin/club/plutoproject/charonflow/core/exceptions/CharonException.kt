package club.plutoproject.charonflow.core.exceptions

import io.lettuce.core.RedisConnectionException

/**
 * CharonFlow 基础异常类
 *
 * 所有 CharonFlow 相关异常的基类。
 */
sealed class CharonException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * 错误码
     */
    abstract val code: String
    
    /**
     * 错误严重程度
     */
    abstract val severity: Severity
    
    /**
     * 是否可重试
     */
    abstract val retryable: Boolean
    
    /**
     * 错误严重程度枚举
     */
    enum class Severity {
        /** 低严重程度，通常不影响正常操作 */
        LOW,
        
        /** 中等严重程度，可能影响部分功能 */
        MEDIUM,
        
        /** 高严重程度，影响核心功能 */
        HIGH,
        
        /** 严重程度，需要立即处理 */
        CRITICAL
    }
}

// region 连接相关异常

/**
 * 连接异常基类
 */
sealed class ConnectionException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.HIGH
) : CharonException(message, cause) {
    override val retryable: Boolean = true
}

/**
 * Redis 连接失败异常
 */
class RedisConnectionException(
    message: String = "Failed to connect to Redis",
    cause: Throwable? = null,
    val redisUri: String? = null
) : ConnectionException(message, cause) {
    override val code: String = "CONNECTION_REDIS_FAILED"
}

/**
 * 连接超时异常
 */
class ConnectionTimeoutException(
    message: String = "Connection timeout",
    cause: Throwable? = null,
    val timeoutMs: Long? = null
) : ConnectionException(message, cause) {
    override val code: String = "CONNECTION_TIMEOUT"
}

/**
 * 连接被拒绝异常
 */
class ConnectionRefusedException(
    message: String = "Connection refused",
    cause: Throwable? = null,
    val host: String? = null,
    val port: Int? = null
) : ConnectionException(message, cause) {
    override val code: String = "CONNECTION_REFUSED"
}

/**
 * 连接丢失异常
 */
class ConnectionLostException(
    message: String = "Connection lost",
    cause: Throwable? = null,
    val lastActivityTime: Long? = null
) : ConnectionException(message, cause) {
    override val code: String = "CONNECTION_LOST"
}

/**
 * 连接池耗尽异常
 */
class ConnectionPoolExhaustedException(
    message: String = "Connection pool exhausted",
    cause: Throwable? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null
) : ConnectionException(message, cause) {
    override val code: String = "CONNECTION_POOL_EXHAUSTED"
}

// endregion

// region 序列化相关异常

/**
 * 序列化异常基类
 */
sealed class SerializationException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.MEDIUM
) : CharonException(message, cause) {
    override val retryable: Boolean = false
}

/**
 * 序列化失败异常
 */
class SerializeFailedException(
    message: String = "Failed to serialize object",
    cause: Throwable? = null,
    val targetType: String? = null
) : SerializationException(message, cause) {
    override val code: String = "SERIALIZATION_FAILED"
}

/**
 * 反序列化失败异常
 */
class DeserializeFailedException(
    message: String = "Failed to deserialize object",
    cause: Throwable? = null,
    val sourceType: String? = null,
    val targetType: String? = null
) : SerializationException(message, cause) {
    override val code: String = "DESERIALIZATION_FAILED"
}

/**
 * 类型未注册异常
 */
class TypeNotRegisteredException(
    message: String = "Type not registered for serialization",
    cause: Throwable? = null,
    val typeName: String? = null
) : SerializationException(message, cause) {
    override val code: String = "TYPE_NOT_REGISTERED"
}

/**
 * 类型不匹配异常
 */
class TypeMismatchException(
    message: String = "Type mismatch",
    cause: Throwable? = null,
    val expectedType: String? = null,
    val actualType: String? = null
) : SerializationException(message, cause) {
    override val code: String = "TYPE_MISMATCH"
}

// endregion

// region 操作相关异常

/**
 * 操作异常基类
 */
sealed class OperationException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.MEDIUM
) : CharonException(message, cause) {
    override val retryable: Boolean = true
}

/**
 * 操作超时异常
 */
class OperationTimeoutException(
    message: String = "Operation timeout",
    cause: Throwable? = null,
    val operation: String? = null,
    val timeoutMs: Long? = null
) : OperationException(message, cause) {
    override val code: String = "OPERATION_TIMEOUT"
}

/**
 * 操作失败异常
 */
class OperationFailedException(
    message: String = "Operation failed",
    cause: Throwable? = null,
    val operation: String? = null
) : OperationException(message, cause) {
    override val code: String = "OPERATION_FAILED"
}

/**
 * 主题不存在异常
 */
class TopicNotFoundException(
    message: String = "Topic not found",
    cause: Throwable? = null,
    val topic: String? = null
) : OperationException(message, cause) {
    override val code: String = "TOPIC_NOT_FOUND"
}

/**
 * 订阅不存在异常
 */
class SubscriptionNotFoundException(
    message: String = "Subscription not found",
    cause: Throwable? = null,
    val subscriptionId: String? = null,
    val topic: String? = null
) : OperationException(message, cause) {
    override val code: String = "SUBSCRIPTION_NOT_FOUND"
}

/**
 * 消息太大异常
 */
class MessageTooLargeException(
    message: String = "Message too large",
    cause: Throwable? = null,
    val messageSize: Long? = null,
    val maxSize: Long? = null
) : OperationException(message, cause) {
    override val code: String = "MESSAGE_TOO_LARGE"
}

// endregion

// region RPC 相关异常

/**
 * RPC 异常基类
 */
sealed class RpcException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.MEDIUM
) : CharonException(message, cause) {
    override val retryable: Boolean = false
}

/**
 * RPC 方法未找到异常
 */
class RpcMethodNotFoundException(
    message: String = "RPC method not found",
    cause: Throwable? = null,
    val method: String? = null
) : RpcException(message, cause) {
    override val code: String = "RPC_METHOD_NOT_FOUND"
}

/**
 * RPC 调用失败异常
 */
class RpcCallFailedException(
    message: String = "RPC call failed",
    cause: Throwable? = null,
    val method: String? = null,
    val error: String? = null
) : RpcException(message, cause) {
    override val code: String = "RPC_CALL_FAILED"
}

/**
 * RPC 参数无效异常
 */
class RpcInvalidArgumentException(
    message: String = "Invalid RPC argument",
    cause: Throwable? = null,
    val method: String? = null,
    val argument: String? = null
) : RpcException(message, cause) {
    override val code: String = "RPC_INVALID_ARGUMENT"
}

// endregion

// region 配置相关异常

/**
 * 配置异常基类
 */
sealed class ConfigurationException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.HIGH
) : CharonException(message, cause) {
    override val retryable: Boolean = false
}

/**
 * 配置无效异常
 */
class InvalidConfigurationException(
    message: String = "Invalid configuration",
    cause: Throwable? = null,
    val configKey: String? = null,
    val configValue: Any? = null
) : ConfigurationException(message, cause) {
    override val code: String = "CONFIGURATION_INVALID"
}

/**
 * 配置缺失异常
 */
class MissingConfigurationException(
    message: String = "Missing required configuration",
    cause: Throwable? = null,
    val configKey: String? = null
) : ConfigurationException(message, cause) {
    override val code: String = "CONFIGURATION_MISSING"
}

// endregion

// region 资源相关异常

/**
 * 资源异常基类
 */
sealed class ResourceException(
    message: String,
    cause: Throwable? = null,
    override val severity: CharonException.Severity = CharonException.Severity.HIGH
) : CharonException(message, cause) {
    override val retryable: Boolean = false
}

/**
 * 资源耗尽异常
 */
class ResourceExhaustedException(
    message: String = "Resource exhausted",
    cause: Throwable? = null,
    val resourceType: String? = null,
    val limit: Long? = null
) : ResourceException(message, cause) {
    override val code: String = "RESOURCE_EXHAUSTED"
}

/**
 * 资源泄漏异常
 */
class ResourceLeakException(
    message: String = "Resource leak detected",
    cause: Throwable? = null,
    val resourceType: String? = null,
    val count: Int? = null
) : ResourceException(message, cause) {
    override val code: String = "RESOURCE_LEAK"
}

// endregion

// region 工具函数

/**
 * 异常工具函数
 */
object ExceptionUtils {
    
    /**
     * 检查异常是否可重试
     */
    fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is CharonException -> exception.retryable
            is io.lettuce.core.RedisConnectionException -> true
            is java.net.ConnectException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> false
        }
    }
    
    /**
     * 获取异常的错误码
     */
    fun getErrorCode(exception: Throwable): String {
        return when (exception) {
            is CharonException -> exception.code
            is io.lettuce.core.RedisConnectionException -> "REDIS_CONNECTION_ERROR"
            is java.net.ConnectException -> "NETWORK_CONNECT_ERROR"
            is java.net.SocketTimeoutException -> "NETWORK_TIMEOUT_ERROR"
            is java.io.IOException -> "IO_ERROR"
            else -> "UNKNOWN_ERROR"
        }
    }
    
    /**
     * 获取异常的严重程度
     */
    fun getSeverity(exception: Throwable): CharonException.Severity {
        return when (exception) {
            is CharonException -> exception.severity
            is io.lettuce.core.RedisConnectionException -> CharonException.Severity.HIGH
            is java.net.ConnectException -> CharonException.Severity.HIGH
            is java.net.SocketTimeoutException -> CharonException.Severity.MEDIUM
            is java.io.IOException -> CharonException.Severity.MEDIUM
            else -> CharonException.Severity.MEDIUM
        }
    }
    
    /**
     * 创建详细的异常信息
     */
    fun createDetailedMessage(exception: Throwable, context: Map<String, Any> = emptyMap()): String {
        val sb = StringBuilder()
        
        // 基本异常信息
        sb.append("Exception: ${exception.javaClass.simpleName}\n")
        sb.append("Message: ${exception.message ?: "No message"}\n")
        
        // 错误码和严重程度
        sb.append("Error Code: ${getErrorCode(exception)}\n")
        sb.append("Severity: ${getSeverity(exception)}\n")
        sb.append("Retryable: ${isRetryable(exception)}\n")
        
        // 上下文信息
        if (context.isNotEmpty()) {
            sb.append("Context:\n")
            context.forEach { (key, value) ->
                sb.append("  $key: $value\n")
            }
        }
        
        // 堆栈跟踪（仅前5行）
        val stackTrace = exception.stackTrace.take(5)
        if (stackTrace.isNotEmpty()) {
            sb.append("Stack Trace (top 5):\n")
            stackTrace.forEach { element ->
                sb.append("  at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n")
            }
        }
        
        return sb.toString()
    }
}

// endregion