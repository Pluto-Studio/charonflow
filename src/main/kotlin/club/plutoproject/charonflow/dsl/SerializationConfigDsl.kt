package club.plutoproject.charonflow.dsl

import club.plutoproject.charonflow.config.SerializationConfig

/**
 * SerializationConfig 的 DSL 构建器
 */
@CharonFlowDsl
class SerializationConfigDsl {
    var encodeDefaults: Boolean = true
    var ignoreUnknownKeys: Boolean = false
    var isLenient: Boolean = false
    var allowSpecialFloatingPointValues: Boolean = false
    var useClassDiscriminator: Boolean = true
    var classDiscriminator: String = "type"

    fun build(): SerializationConfig = SerializationConfig(
        encodeDefaults = encodeDefaults,
        ignoreUnknownKeys = ignoreUnknownKeys,
        isLenient = isLenient,
        allowSpecialFloatingPointValues = allowSpecialFloatingPointValues,
        useClassDiscriminator = useClassDiscriminator,
        classDiscriminator = classDiscriminator
    )
}

/**
 * 配置序列化选项的 DSL 块
 */
fun CharonFlowConfigDsl.serialization(block: SerializationConfigDsl.() -> Unit) {
    serializationConfigDsl = SerializationConfigDsl().apply(block)
}
