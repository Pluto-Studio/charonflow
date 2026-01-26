package club.plutoproject.charonflow.config

/**
 * 序列化器配置
 *
 * 控制序列化器的行为，支持多种配置选项。
 */
data class SerializerConfig(
    /**
     * 序列化格式
     * 默认使用 CBOR（二进制格式，性能好）
     */
    val format: SerializationFormat = SerializationFormat.CBOR,
    
    /**
     * 是否编码默认值
     * 默认 true，序列化时会包含字段的默认值
     */
    val encodeDefaults: Boolean = true,
    
    /**
     * 是否忽略未知键
     * 默认 false，反序列化时遇到未知字段会抛出异常
     */
    val ignoreUnknownKeys: Boolean = false,
    
    /**
     * 是否使用宽松解析
     * 默认 false，严格的 JSON/CBOR 解析
     */
    val isLenient: Boolean = false,
    
    /**
     * 是否美化输出（仅对 JSON 格式有效）
     * 默认 false，紧凑格式
     */
    val prettyPrint: Boolean = false,
    
    /**
     * 是否允许特殊浮点值（NaN, Infinity）
     * 默认 false，不允许特殊浮点值
     */
    val allowSpecialFloatingPointValues: Boolean = false,
    
    /**
     * 是否使用类鉴别器
     * 默认 true，用于多态序列化
     */
    val useClassDiscriminator: Boolean = true,
    
    /**
     * 类鉴别器字段名
     * 默认 "type"
     */
    val classDiscriminator: String = "type"
)

/**
 * 序列化格式枚举
 */
enum class SerializationFormat {
    /**
     * CBOR 格式（Concise Binary Object Representation）
     * 二进制格式，性能好，体积小
     */
    CBOR,
    
    /**
     * JSON 格式
     * 文本格式，可读性好，兼容性强
     */
    JSON,
    
    /**
     * Protocol Buffers 格式
     * 二进制格式，类型安全，性能最佳
     */
    PROTOBUF
}