package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.Config
import club.plutoproject.charonflow.internal.serialization.SerializationManager

/**
 * CharonFlow 工厂类
 *
 * 提供 CharonFlow 实例的创建方法。
 */
object CharonFlow {

    /**
     * 创建 CharonFlow 实例
     *
     * @param config 配置对象
     * @return CharonFlow 实例
     */
    fun create(config: Config): CharonFlow {
        return CharonFlowImpl(config)
    }
}