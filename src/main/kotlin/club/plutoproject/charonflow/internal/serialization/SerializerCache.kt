package club.plutoproject.charonflow.internal.serialization

import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap

/**
 * 序列化器缓存
 * 
 * 使用 ConcurrentHashMap 缓存 FQN 到 KSerializer 的映射。
 * 缓存永不驱逐，因为类加载是不变的。
 */
internal class SerializerCache {
    private val cache = ConcurrentHashMap<String, KSerializer<*>>()

    /**
     * 获取序列化器
     * 
     * @param fqn 类的完全限定名
     * @return 缓存的序列化器，如果不存在则返回 null
     */
    fun get(fqn: String): KSerializer<*>? {
        return cache[fqn]
    }

    /**
     * 存储序列化器
     * 
     * @param fqn 类的完全限定名
     * @param serializer 序列化器实例
     */
    fun put(fqn: String, serializer: KSerializer<*>) {
        cache[fqn] = serializer
    }

    /**
     * 检查缓存中是否包含指定 FQN
     * 
     * @param fqn 类的完全限定名
     * @return 如果缓存包含该 FQN 则返回 true，否则返回 false
     */
    fun contains(fqn: String): Boolean {
        return cache.containsKey(fqn)
    }

    /**
     * 清空缓存
     * 注意：通常不应调用此方法，因为缓存永不驱逐。
     */
    fun clear() {
        cache.clear()
    }

    /**
     * 获取缓存大小
     * 
     * @return 当前缓存中的条目数
     */
    fun size(): Int {
        return cache.size
    }
}
