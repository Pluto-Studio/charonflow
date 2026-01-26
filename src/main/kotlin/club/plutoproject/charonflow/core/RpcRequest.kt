package club.plutoproject.charonflow.core

import kotlinx.serialization.Serializable

/**
 * RPC 请求包装类
 *
 * 用于包装多个参数，支持类型安全的序列化和反序列化。
 * 注意：参数在传输前需要先序列化为字节数组。
 */
@Serializable
data class RpcRequest(
    /**
     * 序列化后的参数数据
     */
    val serializedData: ByteArray = ByteArray(0),
    
    /**
     * 参数类型信息（用于反序列化）
     */
    val paramTypes: List<String> = emptyList(),
    
    /**
     * 请求元数据
     */
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(serializedData.isNotEmpty() || paramTypes.isEmpty()) {
            "If serializedData is empty, paramTypes must also be empty"
        }
    }
    
    /**
     * 参数数量
     */
    val size: Int get() = paramTypes.size
    
    /**
     * 是否为空请求
     */
    val isEmpty: Boolean get() = serializedData.isEmpty()
    
    /**
     * 是否为单参数请求
     */
    val isSingleParam: Boolean get() = paramTypes.size == 1
    
    /**
     * 获取元数据值
     *
     * @param key 元数据键
     * @return 元数据值，如果不存在返回 null
     */
    fun getMetadata(key: String): String? = metadata[key]
    
    /**
     * 检查是否包含指定元数据
     *
     * @param key 元数据键
     * @return 如果包含返回 true
     */
    fun hasMetadata(key: String): Boolean = metadata.containsKey(key)
    
    /**
     * 添加元数据
     *
     * @param key 元数据键
     * @param value 元数据值
     * @return 新的 RpcRequest 实例
     */
    fun withMetadata(key: String, value: String): RpcRequest {
        val newMetadata = metadata.toMutableMap()
        newMetadata[key] = value
        return copy(metadata = newMetadata)
    }
    
    /**
     * 移除元数据
     *
     * @param key 要移除的元数据键
     * @return 新的 RpcRequest 实例
     */
    fun withoutMetadata(key: String): RpcRequest {
        val newMetadata = metadata.toMutableMap()
        newMetadata.remove(key)
        return copy(metadata = newMetadata)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as RpcRequest
        
        if (!serializedData.contentEquals(other.serializedData)) return false
        if (paramTypes != other.paramTypes) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = serializedData.contentHashCode()
        result = 31 * result + paramTypes.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
    
    companion object {
        /**
         * 创建空请求
         */
        fun empty(): RpcRequest = RpcRequest()
        
        /**
         * 创建单参数请求
         *
         * @param serializedData 序列化后的参数数据
         * @param paramType 参数类型
         */
        fun single(serializedData: ByteArray, paramType: String): RpcRequest {
            return RpcRequest(
                serializedData = serializedData,
                paramTypes = listOf(paramType)
            )
        }
        
        /**
         * 创建多参数请求
         *
         * @param serializedData 序列化后的参数数据
         * @param paramTypes 参数类型列表
         */
        fun of(serializedData: ByteArray, paramTypes: List<String>): RpcRequest {
            return RpcRequest(serializedData = serializedData, paramTypes = paramTypes)
        }
    }
}

/**
 * RPC 响应包装类
 *
 * 用于包装 RPC 调用的响应，包含结果和可能的错误信息。
 */
@Serializable
data class RpcResponse<T>(
    /**
     * 调用结果
     * 如果调用成功，包含返回值；如果调用失败，为 null
     */
    val result: T? = null,
    
    /**
     * 错误信息
     * 如果调用失败，包含错误信息；如果调用成功，为 null
     */
    val error: String? = null,
    
    /**
     * 调用是否成功
     */
    val success: Boolean = error == null,
    
    /**
     * 调用耗时（毫秒）
     */
    val duration: Long = 0,
    
    /**
     * 响应元数据
     */
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(!(result != null && error != null)) {
            "RpcResponse cannot have both result and error"
        }
        require(duration >= 0) {
            "Duration must be non-negative"
        }
    }
    
    /**
     * 获取结果或抛出异常
     *
     * @throws IllegalStateException 如果调用失败
     */
    fun getOrThrow(): T {
        return if (success && result != null) {
            result
        } else {
            throw IllegalStateException(error ?: "RPC call failed")
        }
    }
    
    /**
     * 获取结果或返回默认值
     *
     * @param defaultValue 默认值
     */
    fun getOrDefault(defaultValue: T): T {
        return if (success && result != null) result else defaultValue
    }
    
    /**
     * 获取结果或 null
     */
    fun getOrNull(): T? = if (success) result else null
    
    /**
     * 转换结果类型
     *
     * @param transform 转换函数
     */
    fun <R> map(transform: (T) -> R): RpcResponse<R> {
        return if (success && result != null) {
            RpcResponse(
                result = transform(result),
                duration = duration,
                metadata = metadata
            )
        } else {
            RpcResponse(
                error = error,
                duration = duration,
                metadata = metadata
            )
        }
    }
    
    companion object {
        /**
         * 创建成功响应
         */
        fun <T> success(result: T, duration: Long = 0, metadata: Map<String, String> = emptyMap()): RpcResponse<T> {
            return RpcResponse(result = result, duration = duration, metadata = metadata)
        }
        
        /**
         * 创建失败响应
         */
        fun <T> error(error: String, duration: Long = 0, metadata: Map<String, String> = emptyMap()): RpcResponse<T> {
            return RpcResponse(error = error, duration = duration, metadata = metadata)
        }
        
        /**
         * 从 Result 创建响应
         */
        fun <T> fromResult(result: Result<T>, duration: Long = 0): RpcResponse<T> {
            return result.fold(
                onSuccess = { success(it, duration) },
                onFailure = { error(it.message ?: "Unknown error", duration) }
            )
        }
    }
}