package club.plutoproject.charonflow.internal.serialization

import kotlin.reflect.KClass

/**
 * 类型解析器
 * 
 * 负责类的完全限定名（FQN）与 KClass 之间的双向映射。
 */
internal object TypeResolver {
    /**
     * 根据完全限定名获取 KClass
     * 
     * @param fqn 类的完全限定名
     * @return 对应的 KClass，如果类未找到则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getKClass(fqn: String): KClass<T>? {
        return try {
            Class.forName(fqn).kotlin as KClass<T>
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    /**
     * 根据 KClass 获取完全限定名
     * 
     * @param kClass Kotlin 类对象
     * @return 类的完全限定名
     */
    fun getFQN(kClass: KClass<*>): String {
        return kClass.qualifiedName
            ?: throw IllegalArgumentException("Class ${kClass.simpleName} does not have a qualified name (likely a local or anonymous class)")
    }
}
