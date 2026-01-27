package club.plutoproject.charonflow.internal.serialization

import club.plutoproject.charonflow.core.exceptions.SerializeFailedException
import club.plutoproject.charonflow.core.exceptions.TypeNotRegisteredException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializerOrNull
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(SerializationManager::class.java)

/**
 * 序列化管理器
 * 
 * 负责对象的序列化和反序列化操作，使用 CBOR 格式。
 */
@OptIn(ExperimentalSerializationApi::class)
internal class SerializationManager(
    private val serializersModule: SerializersModule,
    private val cache: SerializerCache = SerializerCache()
) {

    private val cbor = Cbor {
        serializersModule = this@SerializationManager.serializersModule
    }

    /**
     * 序列化对象为字节数组
     * 
     * @param value 要序列化的对象
     * @return 序列化后的字节数组
     * @throws SerializeFailedException 序列化失败时抛出
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun <T : Any> serialize(value: T): ByteArray {
        @Suppress("UNCHECKED_CAST")
        val kClass = value::class as KClass<T>
        val fqn = TypeResolver.getFQN(kClass)

        return try {
            val serializer = findSerializer(kClass, fqn)
            cbor.encodeToByteArray(serializer, value)
        } catch (e: SerializeFailedException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to serialize object of type '$fqn': {}", e.message, e)
            throw SerializeFailedException(
                message = "Failed to serialize object of type '$fqn'",
                cause = e,
                targetType = fqn
            )
        }
    }

    /**
     * 反序列化字节数组为对象
     * 
     * @param bytes 字节数组
     * @param payloadType 消息中携带的类型信息（FQN）
     * @param targetType 期望的目标类型
     * @return 反序列化后的对象，如果类型不匹配则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> deserialize(bytes: ByteArray, payloadType: String, targetType: KClass<T>): T? {
        if (targetType == Any::class) {
            return deserializeAsAnyInternal(bytes, payloadType) as T?
        }

        if (payloadType != TypeResolver.getFQN(targetType)) {
            logger.debug("Type mismatch: payload type '{}' does not match target type '{}'. Skipping message.", payloadType, TypeResolver.getFQN(targetType))
            return null
        }

        return try {
            val serializer = findSerializer(targetType, payloadType)
            cbor.decodeFromByteArray(serializer, bytes)
        } catch (e: Exception) {
            logger.warn("Failed to deserialize message to type '{}': {}", payloadType, e.message, e)
            null
        }
    }

    /**
     * 将消息反序列化为 Any 类型（内部实现）
     *
     * 特殊处理：不进行类型检查，直接按 payloadType 反序列化，然后转换为 Any。
     *
     * @param bytes 字节数组
     * @param payloadType 消息中携带的类型信息（FQN）
     * @return 反序列化后的对象，如果失败则返回 null
     */
    private fun deserializeAsAnyInternal(bytes: ByteArray, payloadType: String): Any? {
        return try {
            val kClass = TypeResolver.getKClass<Any>(payloadType)
                ?: throw TypeNotRegisteredException(
                    message = "Type '$payloadType' not found for deserialization",
                    typeName = payloadType
                )

            val serializer = findSerializer(kClass, payloadType)
            cbor.decodeFromByteArray(serializer, bytes)
        } catch (e: TypeNotRegisteredException) {
            logger.warn("Type '$payloadType' not found for deserialization: {}", e.message, e)
            null
        } catch (e: Exception) {
            logger.warn("Failed to deserialize message to type '$payloadType' as Any: {}", e.message, e)
            null
        }
    }

    /**
     * 查找序列化器
     * 
     * 查找顺序：
     * 1. 缓存
     * 2. SerializersModule（使用 getContextual 尝试）
     * 3. 反射（使用 serializerOrNull() 函数）
     * 4. 失败，抛出 TypeNotRegisteredException
     * 
     * @param kClass 类对象
     * @param fqn 类的完全限定名
     * @return 序列化器实例
     * @throws TypeNotRegisteredException 如果找不到序列化器
     */
    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> findSerializer(kClass: KClass<T>, fqn: String): KSerializer<T> {
        cache.get(fqn)?.let { return it as KSerializer<T> }

        val serializer: KSerializer<T> = serializersModule.getContextual(kClass)
            ?: kClass.serializerOrNull()
                ?: throw TypeNotRegisteredException(
                    message = "No serializer found for type '$fqn'",
                    typeName = fqn
                )

        cache.put(fqn, serializer as KSerializer<*>)
        return serializer
    }

    /**
     * 检查类型是否可序列化
     * 
     * @param kClass 类对象
     * @return 如果可序列化则返回 true，否则返回 false
     */
    fun <T : Any> isSerializable(kClass: KClass<T>): Boolean {
        val fqn = TypeResolver.getFQN(kClass)
        return try {
            findSerializer(kClass, fqn)
            true
        } catch (e: TypeNotRegisteredException) {
            false
        }
    }

    /**
     * 将消息反序列化为 Any 类型（公开版本）
     * 
     * 特殊处理：不进行类型检查，直接按 payloadType 反序列化。
     * 
     * @param bytes 字节数组
     * @param payloadType 消息中携带的类型信息（FQN）
     * @return 反序列化后的对象，如果失败则返回 null
     */
    fun deserializeAsAny(bytes: ByteArray, payloadType: String): Any? {
        return deserializeAsAnyInternal(bytes, payloadType)
    }
}
