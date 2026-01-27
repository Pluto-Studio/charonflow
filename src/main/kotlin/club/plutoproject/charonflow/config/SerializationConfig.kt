package club.plutoproject.charonflow.config

/**
 * 序列化配置
 *
 * 控制序列化器的行为，固定使用 CBOR 格式。
 */
data class SerializationConfig(

    
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

