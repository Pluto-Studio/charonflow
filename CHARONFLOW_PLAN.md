# CharonFlow 通讯框架实现计划

## 项目概述

CharonFlow 是一个基于 Kotlin + Redis 的轻量级实时通讯框架，支持多种通讯范式：

- Pub/Sub（发布-订阅）
- RPC（单点和流式）
- Req/Rsp（请求-响应）
- Multicast（组播）
- Broadcast（广播）

**核心理念**：Redis Driver Wrapper + 通讯模式抽象，专注于实时、轻量、快速的消息传递。

## 技术栈

- **语言**: Kotlin 2.1.10
- **构建工具**: Gradle 9.0.0 (Kotlin DSL)
- **Java版本**: JDK 21
- **Redis客户端**: Lettuce 6.3.2.RELEASE
- **序列化**: Kotlinx Serialization 1.7.0 + CBOR 格式
- **协程**: Kotlinx Coroutines 1.8.1
- **日志**: SLF4J + Logback
- **错误处理**: Kotlin 标准库 `Result<T>`

## 设计决策

### 1. 连接管理

- **自动配置**: 默认 `maxTotal = max(CPU核心数/2, 6)`（MAX_CONNECTION_THREADS常量）
- **可覆盖**: 通过 `ConnectionPoolConfig` 允许用户自定义
- **包装策略**: 所有 Lettuce 对象都包装，避免依赖冲突
- **健康检查**: 定期连接健康检查
- **连接泄露防护**: 自动检测和清理

### 2. 序列化策略

- **主要机制**: Kotlinx Serialization + CBOR（固定格式，不提供其他格式选择）
- **类型安全**: 强制使用 `@Serializable` 注解，不提供 GSON fallback
- **注册机制**: 仅通过 `Config.serializersModule` 在初始化时配置，不支持运行时注册
- **统一数据格式**: 所有传输对象（Message、RpcRequest）内部使用 `ByteArray` 存储序列化数据
- **类型信息传递**: 使用 `payloadType: String` 字段存储类型的完全限定名（FQN）
- **序列化管理器**: 提供 `SerializationManager` 统一管理序列化/反序列化
- **缓存策略**: FQN → KSerializer 缓存（类加载不变，缓存不清空）
- **错误处理**: 反序列化失败打印日志不终止订阅；handler异常终止订阅并记录错误

### 3. 错误处理

- **统一API**: 所有公共方法返回 `Result<T>`
- **异常分类**:
    - `ConnectionException`: 连接相关错误
    - `TimeoutException`: 操作超时
    - `SerializationException`: 序列化错误
    - `CharonException`: 基础异常类
- **扩展函数**: 提供丰富的 `Result<T>` 扩展函数

### 4. 协程管理

- **调度器**: 固定使用 `Dispatchers.IO`
- **作用域**: 使用 `SupervisorJob` 防止异常传播
- **资源管理**: 实现 `Closeable` 接口，支持 `use` 语法

### 5. 订阅管理（四种取消方式）

1. **Subscription 对象**: `subscribe()` 返回 `Subscription`，调用 `unsubscribe()`
2. **Handler 内取消**: handler 参数包含 `cancel()` 函数
3. **协程作用域集成**: 订阅绑定到协程作用域
4. **自动清理**: `CharonFlow.close()` 时自动取消所有订阅
5. **pause/resume**: 支持暂停和恢复订阅（pause状态下忽略消息，不缓冲）

### 6. RPC 参数设计

- **单参数API**: `fun <T, R> rpc(method: String, param: T): Result<R>`（注册方法改为非suspend）
- **多参数支持**: 通过 `RpcRequest` wrapper 类，`serializedParams: List<ByteArray>` 存储每个参数的序列化数据
- **可变参数辅助**: `fun <R> rpc(method: String, vararg params: Any): Result<R>`

### 7. Message 统一数据格式

- **移除泛型**: `Message<T>` → `Message`
- **统一存储**: `payload: ByteArray` 存储用户数据的序列化结果
- **类型信息**: `payloadType: String` 必需，存储类型的完全限定名（FQN）
- **包装结构**: 所有通讯范式（Pub/Sub、RPC、Req/Rsp）都基于 Message 传输
- **RpcRequest包装**: RpcRequest 作为 Message.payload 的内容发送（Message套一层RpcRequest）

### 8. 客户端ID管理

- **自动生成**: 默认使用 `UUID.randomUUID().toString()`
- **自定义支持**: 可通过 `Config.clientId` 指定自定义ID
- **用途**: 用于点对点通信（Message.target字段）
- **自动填充**: 发布消息时自动设置 Message.source = clientId

### 9. 点对点Req/Rsp设计

- **目标指定**: `request()` 和 `onRequest()` 支持 `targetClientId` 参数
- **冲突检测**: 同一channel仅允许一个处理器注册，冲突时抛出 `AlreadyRegisteredException`
- **查询方法**: 提供 `getRegisteredChannels()` 查询已注册的channel
- **广播兼容**: 初始阶段暂不支持广播模式

### 10. subscribe 类型匹配策略

- **严格匹配**: `payloadType` 必须与订阅时指定的 `KClass.qualifiedName` 完全相同
- **Any特殊处理**: `subscribe<Any>(Any::class, ...)` 接收所有类型消息
    - 内部不进行类型检查
    - 按实际 `payloadType` 反序列化后 cast 到 `Any`
    - 调用handler
- **类型不匹配处理**: 静默忽略（可选DEBUG日志）

### 11. SerializersModule集成

- **仅初始化配置**: 通过 `Config.serializersModule` 在创建 CharonFlow 时提供
- **不可运行时修改**: 不提供运行时注册序列化器的API
- **查找优先级**: SerializersModule注册 → 反射查找@Serializable类 → 失败
- **用户完全控制**: 支持多态类型、上下文序列化等高级配置

## 文件结构规划（修订版）

```
src/main/kotlin/club/plutoproject/charonflow/
├── CharonFlow.kt                  # 主入口类（接口定义）
├── CharonFlowFactory.kt           # 工厂类（✓ 已实现）
├── builder/
│   └── CharonFlowBuilder.kt      # DSL构建器（○ 未实现）
├── config/
│   ├── Config.kt                 # 主配置类（✓ 已实现：添加clientId、serializersModule）
│   ├── ConnectionPoolConfig.kt   # 连接池配置（✓ 已实现：MAX_CONNECTION_THREADS=6）
│   ├── RetryPolicyConfig.kt      # 重试策略配置（✓ 已实现）
│   └── SerializationConfig.kt    # 序列化配置（✓ 已实现：移除format枚举）
├── core/
│   ├── Message.kt                # 消息抽象（✓ 已实现：移除泛型，使用ByteArray，添加topic字段）
│   ├── RpcRequest.kt             # RPC请求包装（✓ 已实现）
│   ├── Subscription.kt           # 订阅接口（✓ 已实现：调整方法签名）
│   ├── PubSubSubscription.kt     # Pub/Sub订阅实现（✓ 已实现：内部类）
│   ├── PubSubManager.kt          # Pub/Sub管理器（✓ 已实现：内部类）
│   └── exceptions/
│       ├── CharonException.kt    # 基础异常（✓ 已实现）
│       ├── ConnectionException.kt
│       ├── OperationException.kt
│       ├── SerializationException.kt
│       └── AlreadyRegisteredException.kt  # （○ 未实现）
├── internal/
│   ├── serialization/
│   │   ├── SerializationManager.kt  # 核心序列化管理（✓ 已实现）
│   │   ├── TypeResolver.kt          # 类型解析（✓ 已实现）
│   │   └── SerializerCache.kt       # 序列化器缓存（✓ 已实现）
│   ├── CharonFlowImpl.kt            # CharonFlow实现（✓ 已实现）
│   ├── registry/
│   │   └── ChannelRegistry.kt   # channel注册表（○ 未实现：点对点）
│   └── handlers/
│       └── ErrorHandler.kt      # 错误处理策略（○ 未实现）
├── protocol/
│   ├── Serializer.kt             # 序列化接口（○ 未实现）
│   ├── CborSerializer.kt         # CBOR实现（○ 未实现）
│   └── TypeRegistry.kt           # 类型注册（○ 未实现）
├── transport/
│   ├── RedisTransport.kt         # Redis传输层（○ 未实现）
│   ├── ConnectionManager.kt      # 连接管理器（○ 未实现）
│   └── health/
│       └── HealthChecker.kt      # 健康检查（○ 未实现）
├── patterns/
│   ├── PubSub.kt                 # Pub/Sub实现（○ 未实现：部分逻辑在CharonFlowImpl）
│   ├── RequestResponse.kt        # 请求-响应实现（点对点）（○ 未实现）
│   ├── Rpc.kt                    # RPC实现（○ 未实现）
│   ├── Multicast.kt              # 组播实现（○ 未实现）
│   └── Broadcast.kt              # 广播实现（○ 未实现）
├── extensions/
│   ├── ResultExtensions.kt       # Result扩展函数（✓ 已实现）
│   └── ErrorHandlingExtensions.kt # 错误处理扩展（○ 未实现）
└── examples/
    └── BasicExamples.kt          # 使用示例（○ 未实现）
```

## 核心接口签名（修订版）

### 1. CharonFlow 主接口（新架构）

```kotlin
interface CharonFlow : Closeable {
    // ============ 配置和状态 ============
    val config: Config
    val isConnected: Boolean
    fun getClientId(): String

    // ============ 序列化器注册（仅初始化） ============
    // 注：序列化器通过Config.serializersModule配置，不支持运行时注册

    // ============ Pub/Sub ============
    // 发布（handler接收反序列化对象）
    suspend fun publish(topic: String, message: Any): Result<Unit>  // （✓ 已实现）

    // 订阅（非suspend注册，类型安全）
    fun subscribe(
        topic: String,
        handler: suspend (message: Any) -> Unit  // （✓ 已实现：接收反序列化对象）
    ): Result<Subscription>

    // 订阅类型安全版本（✓ 已实现）
    fun <T : Any> subscribe(
        topic: String,
        clazz: KClass<T>,
        handler: suspend (message: T) -> Unit
    ): Result<Subscription>

    // ============ 请求-响应模式（点对点） ============
    suspend fun <T : Any> request(
        channel: String,
        request: Any,
        targetClientId: String? = null  // 可选指定目标
    ): Result<T>

    fun <T : Any, R : Any> onRequest(
        channel: String,
        requestClass: KClass<T>,
        handler: suspend (request: T) -> R
    ): Result<Unit>

    // ============ RPC ============
    suspend fun <T : Any, R : Any> rpc(method: String, param: T): Result<R>
    suspend fun <R : Any> rpc(method: String, request: RpcRequest): Result<R>
    suspend fun <R : Any> rpc(method: String, vararg params: Any): Result<R>

    fun <T : Any, R : Any> registerRpc(
        method: String,
        requestClass: KClass<T>,
        handler: suspend (request: T) -> R
    ): Result<Unit>

    // ============ 流式RPC ============
    suspend fun <T : Any, R : Any> streamRpc(method: String, param: T): Flow<R>

    fun <T : Any, R : Any> registerStreamRpc(
        method: String,
        requestClass: KClass<T>,
        handler: suspend (param: T) -> Flow<R>
    ): Result<Unit>

    // ============ 组播/广播 ============
    suspend fun joinMulticastGroup(group: String): Result<Unit>
    suspend fun multicast(group: String, message: Any): Result<Unit>
    suspend fun onMulticast(
        group: String,
        kclass: KClass<Any>,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription>

    suspend fun broadcast(channel: String, message: Any): Result<Unit>
    suspend fun onBroadcast(
        channel: String,
        kclass: KClass<Any>,
        handler: suspend (message: Any) -> Unit
    ): Result<Subscription>

    // ============ 工具方法 ============
    fun getRegisteredChannels(): Set<String>
    fun getRegisteredRpcMethods(): Set<String>
    suspend fun healthCheck(): Boolean
}
```

### 2. 配置类签名（修订版）

```kotlin
// 主配置
data class Config(
    val redisUri: String,
    val serializationConfig: SerializationConfig = SerializationConfig(),
    val serializersModule: SerializersModule = SerializersModule {},  // 新增
    val connectionPoolConfig: ConnectionPoolConfig = ConnectionPoolConfig(),
    val retryPolicyConfig: RetryPolicyConfig = RetryPolicyConfig(),
    val clientId: String = UUID.randomUUID().toString(),  // 新增
    val timeout: Duration = 5.seconds,
    val enableHealthCheck: Boolean = true,
    val healthCheckInterval: Duration = 30.seconds,
    val enableMetrics: Boolean = false
) {
    init {
        require(redisUri.isNotBlank())
        require(timeout.isPositive())
        require(healthCheckInterval.isPositive())
    }
}

// 序列化配置（移除format字段，固定CBOR）
data class SerializationConfig(
    val encodeDefaults: Boolean = true,
    val ignoreUnknownKeys: Boolean = false,
    val isLenient: Boolean = false,
    val allowSpecialFloatingPointValues: Boolean = false,
    val useClassDiscriminator: Boolean = true,
    val classDiscriminator: String = "type"
)

// 连接池配置（MAX_CONNECTION_THREADS = 6）
private const val MAX_CONNECTION_THREADS = 6

data class ConnectionPoolConfig(
    val maxTotal: Int = max(Runtime.getRuntime().availableProcessors() / 2, MAX_CONNECTION_THREADS),
    val maxIdle: Int = maxTotal,
    val minIdle: Int = 2,
    val testOnBorrow: Boolean = true,
    val testWhileIdle: Boolean = true,
    val timeBetweenEvictionRuns: Long = 30_000L,
    val minEvictableIdleTime: Long = 60_000L,
    val maxWaitMillis: Long = 5_000L
)
```

### 3. Message（新架构，移除泛型）

```kotlin
@Serializable
data class Message(
    // 核心数据字段（必需）
    val payload: ByteArray,           // 用户数据的序列化结果
    val payloadType: String,          // 类型的完全限定名（FQN）

    // 元数据字段
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val headers: Map<String, String> = emptyMap(),
    val source: String? = null,       // 发送方clientId（自动填充）
    val target: String? = null,       // 接收方clientId（用于点对点）
    val priority: Int = 5,
    val ttl: Long = 0L,
    val correlationId: String? = null,
    val replyTo: String? = null
) {
    init {
        require(priority in 0..9)
        require(ttl >= 0)
    }

    // 工具方法
    fun withHeader(key: String, value: String): Message
    fun withTarget(target: String): Message
    fun withCorrelationId(correlationId: String): Message
    fun isExpired(): Boolean

    companion object {
        fun request(body: Any, replyTo: String, correlationId: String? = null): Message
        fun response(body: Any, correlationId: String): Message
        fun error(error: String, correlationId: String): Message
    }
}
```

### 4. RpcRequest（新架构）

```kotlin
@Serializable
data class RpcRequest(
    val serializedParams: List<ByteArray>,  // 每个参数单独序列化
    val paramTypes: List<String>,           // 每个参数的类型FQN
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(serializedParams.size == paramTypes.size)
    }

    // 便捷方法
    fun <T> getParam(index: Int): T?
    fun <T> deserializeParams(): List<T>
    fun withMetadata(key: String, value: String): RpcRequest

    companion object {
        fun of(vararg params: Any): RpcRequest
        fun single(param: Any): RpcRequest
    }
}
```

### 5. Subscription 接口

```kotlin
interface Subscription {
    // 属性
    val id: String
    val topic: String
    val createdAt: Long
    val lastActivityTime: Long
    val isActive: Boolean
    val isPaused: Boolean
    val messageCount: Long
    val stats: SubscriptionStats

    // 取消订阅
    suspend fun unsubscribe(): Result<Unit>
    fun unsubscribeAsync()

    // 订阅管理
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun updateHandler(handler: suspend (message: Any) -> Unit): Result<Unit>

    // 统计信息
    fun resetStats()
    fun getDetailedStats(): DetailedSubscriptionStats

    // 工具方法
    suspend fun await(): Result<Unit>
    fun onComplete(callback: (Result<Unit>) -> Unit)
    fun onError(callback: (Throwable) -> Unit)
}
```

### 6. SerializationManager（新核心组件）

```kotlin
class SerializationManager(
    private val serializersModule: SerializersModule,
    private val config: SerializationConfig
) {
    // 缓存：FQN → KSerializer（不清空）
    private val serializerCache: ConcurrentHashMap<String, KSerializer<*>> = ...
    private val classCache: ConcurrentHashMap<String, KClass<*>> = ...

    // 序列化/反序列化
    fun serialize(obj: Any): ByteArray
    fun deserialize(bytes: ByteArray, typeName: String): Any?

    // 类型处理
    fun canDeserialize(typeName: String): Boolean
    fun isTypeCompatible(messageType: String, subscribedClass: KClass<*>): Boolean

    // Any特殊处理
    fun createAnySerializer(): KSerializer<Any>
}
```

### 7. 新增异常类

```kotlin
// channel/RPC方法注册冲突
class AlreadyRegisteredException(
    val channel: String,
    val existingClientId: String,
    val existingSince: Long
) : CharonException("Channel '$channel' is already registered by client '$existingClientId'")

// 序列化异常
sealed class SerializationException(message: String, cause: Throwable? = null) : CharonException(message, cause) {
    class NotSerializable(val typeName: String) : SerializationException("Type not serializable: $typeName")
    class DeserializeFailed(val typeName: String, cause: Throwable) :
        SerializationException("Failed to deserialize: $typeName", cause)
    class SerializerNotFound(val typeName: String) : SerializationException("No serializer found for type: $typeName")
}
```

## MVP（最小可行产品）范围定义

**首要目标**：实现一个包含 **Pub/Sub（发布-订阅）** 这一种通讯范式的轻量级框架。

### MVP 包含的功能

1. **Pub/Sub 基础功能**：
    - 发布消息（自动序列化）
    - 订阅消息（类型安全，支持 Any::class 接收所有类型）
    - 订阅管理（四种取消方式 + pause/resume）
    - 消息路由和传递
    - 序列化管理（固定 CBOR 格式，仅初始化时配置 SerializersModule）

2. **基础架构**：
    - 配置系统（包含 clientId、serializersModule 等）
    - 消息数据结构（Message 类重构）
    - 序列化管理器（SerializationManager）
    - 错误处理策略
    - 连接池管理

### MVP 不包含的功能（后续讨论和实现）

1. **多参数 RPC 设计**：相关讨论延后，保留 RpcRequest 类和 Demo 代码
2. **Req/Rsp（请求-响应）模式**：点对点设计延后，保留 API 接口定义
3. **Multicast（组播）和 Broadcast（广播）**：高级通讯模式延后
4. **流式 RPC**：高级功能延后
5. **多序列化格式支持**：固定 CBOR，移除 SerializationFormat 枚举

### 实现优先级

1. **最高**：完成基础重构（阶段3.1-3.4）和 Pub/Sub 实现（阶段4）
2. **延后**：阶段3.5（点对点Req/Rsp）和阶段5（其他通讯模式）标记为 post-MVP
3. **保留**：所有相关 To Do 项和 Demo 代码保留不变，但状态标记为 post-MVP

---

## 当前待办事项 (TODO)

### MVP 阶段（必须完成）

#### 阶段1：基础架构搭建 (已完成)

- [x] 更新构建配置（添加依赖和插件）
- [x] 创建基础包结构目录
- [x] 创建日志配置文件（logback.xml）

#### 阶段2：核心接口和配置定义 (已完成)

- [x] 创建Config配置类体系
- [x] 定义核心接口（CharonFlow、Message等）
- [x] 创建异常类体系
- [x] 定义Result扩展函数

#### 阶段3：配置和数据结构重构 (已完成)

- [x] 重命名SerializerConfig.kt → SerializationConfig.kt
- [x] 移除SerializationFormat枚举，固定使用CBOR
- [x] Config添加clientId字段（UUID默认，可自定义）
- [x] Config添加serializersModule字段
- [x] 重构Message类（移除泛型，payload: ByteArray，payloadType: String必需）

#### 阶段4：序列化管理器实现 (已完成)

- [x] 创建SerializationManager类
- [x] 实现TypeResolver（FQN → KClass映射）
- [x] 实现SerializerCache（FQN → KSerializer缓存，不清空）
- [x] 实现findSerializer流程（SerializersModule → 反射 → 失败）
- [x] 实现Any::class特殊处理
- [x] 实现反序列化失败日志记录

#### 阶段5：API接口重构（Pub/Sub相关）

- [x] CharonFlow.subscribe改为非suspend，handler接收反序列化对象
- [x] 添加Any::class特殊处理逻辑
- [x] Subscription调整updateHandler等方法签名
- [x] Subscription实现pause状态忽略消息逻辑
- [x] 修复设计缺陷：添加 topic 字段到 Message 类
- [x] 修复设计缺陷：删除 PubSubMessage，统一使用 Message
- [x] 修复实现缺陷：Subscription 持有 handler，添加消息处理逻辑
- [x] 修复实现缺陷：正确使用异常类型和 Logger

#### 阶段6：异常和错误处理 (已完成)

- [x] 添加SerializationException子类（代码中已存在：SerializeFailedException, DeserializeFailedException, TypeNotRegisteredException, TypeMismatchException）
- [x] 实现handler异常终止订阅逻辑（PubSubSubscription.handleReceivedMessage捕获异常后设置_isActive=false）
- [x] 实现类型不匹配静默忽略逻辑（CharonFlowImpl.handleIncomingMessage中检查类型匹配，不匹配则DEBUG日志并跳过）
- [x] 添加消息分发逻辑（CharonFlowImpl.handleIncomingMessage统一处理接收到的消息）

#### 阶段7：Pub/Sub模式实现（MVP核心 - 已完成）

- [x] 实现Pub/Sub核心功能
  - 创建 RedisConnectionManager（管理 Redis 连接和 Pub/Sub 连接）
  - 创建 PubSubMessageListener（监听 Redis Pub/Sub 消息）
  - 实现 publish 方法（使用 Lettuce 发布消息到 Redis）
  - 实现 subscribe 方法（订阅 Redis 主题并注册消息处理器）
- [x] 实现订阅管理（四种取消方式 + pause/resume）
  - unsubscribe() 取消订阅
  - pause() 暂停接收消息（忽略消息，不缓冲）
  - resume() 恢复接收消息
  - close() 时自动取消所有订阅
- [x] 创建测试用例验证Pub/Sub
  - CharonFlowPubSubTest.kt 包含 6 个测试用例
  - 测试基础 Pub/Sub、Any 类型订阅、类型过滤、pause/resume、handler 异常

### Post-MVP阶段（后续实现）

#### 阶段8：RPC和Req/Rsp基础架构 (post-MVP)

- [ ] 重构RpcRequest类（serializedData → serializedParams: List<ByteArray>）
- [ ] CharonFlow.onRequest改为非suspend
- [ ] CharonFlow.registerRpc改为非suspend
- [ ] 添加getRegisteredChannels()、getRegisteredRpcMethods()、getClientId()
- [ ] 添加AlreadyRegisteredException

#### 阶段9：点对点Req/Rsp实现 (post-MVP)

- [ ] 创建ChannelRegistry（channel注册表，冲突检测）
- [ ] 实现Message.source自动填充clientId
- [ ] 实现Message.target路由逻辑
- [ ] 更新Message工厂方法支持target字段

#### 阶段10：其他通讯模式实现 (post-MVP)

- [ ] 实现请求-响应模式（点对点）
- [ ] 实现RPC系统（单参数+多参数，嵌套Message）
- [ ] 实现组播和广播

#### 阶段11：API完善和DSL构建器 (post-MVP)

- [ ] 实现流畅的DSL构建器
- [ ] 添加错误处理扩展函数
- [ ] 编写基础使用示例
- [ ] 重构Message为内部类，提供DSL API用于消息构建

## 依赖版本

```kotlin
// build.gradle.kts 中需要添加的依赖
dependencies {
    // Redis 客户端
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // 日志
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}
```

## 构建配置要点

```kotlin
// 需要添加的插件
plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
}

// Java 工具链配置
kotlin {
    jvmToolchain(21)
}
```

## 日志配置要点

```xml
<!-- logback.xml 配置（仅控制台输出） -->
<configuration>
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- CharonFlow 相关包日志级别 -->
    <!-- 默认使用 INFO 级别，用户可以在自己的配置中覆盖 -->
    <logger name="club.plutoproject.charonflow" level="INFO"/>

    <!-- 第三方库日志级别控制 -->
    <logger name="io.lettuce" level="WARN"/>
    <logger name="io.netty" level="WARN"/>
    <logger name="kotlinx.coroutines" level="WARN"/>

    <!-- 根日志配置 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## 实现优先级（MVP聚焦）

1. **最高优先级**：完成阶段3-6（架构重构）和阶段7（Pub/Sub实现）
2. **延后/Post-MVP**：阶段8-11（RPC、Req/Rsp、其他通讯模式等高级功能）
3. **保留设计**：多参数 RPC、Req/Rsp 等复杂设计保留 Demo 代码和 To Do 项，后续讨论

## 注意事项

1. 所有公共 API 必须返回 `Result<T>` 类型
2. 序列化必须使用 Kotlinx Serialization，强制类型安全
3. 连接池默认基于 CPU 核心数自动配置
4. 订阅管理提供四种取消方式
5. RPC 支持单参数和多参数两种方式

## 更新记录

- **2025-01-26**: 创建初始计划文档，包含完整的设计决策、API Demo 和实现计划
- **2025-01-27**: 完成阶段2代码，编译检查通过
- **2025-01-27**: Code Review反馈，架构重构（重大变更）
    - 序列化格式固定为CBOR，移除用户配置
    - Message/RpcRequest统一使用ByteArray存储
    - 添加clientId和serializersModule配置
    - API改为非suspend注册，handler接收反序列化对象
    - 实现点对点Req/Rsp（基于clientId）
    - 订阅支持pause/resume，pause状态忽略消息
    - 添加类型缓存和序列化器查找优先级
    - 添加AlreadyRegisteredException冲突检测
- **2025-01-27**: 聚焦MVP范围，明确仅实现Pub/Sub基础功能
    - 添加MVP范围定义，明确包含和不包含的功能
    - 调整实现优先级，聚焦阶段3-6（架构重构）和阶段7（Pub/Sub实现）
    - 标记阶段8-11为post-MVP，保留相关设计和Demo代码
- **2025-01-27**: 优化To Do结构，将RPC相关重构移至Post-MVP
    - 重新编号阶段为连续递增（阶段3-11）
    - 将RpcRequest重构、onRequest、registerRpc等任务移至阶段8
- **2025-01-27**: 完成阶段4（序列化管理器实现）
    - 创建SerializationManager类（固定CBOR格式）
    - 实现TypeResolver（FQN ↔ KClass映射）
    - 实现SerializerCache（FQN → KSerializer缓存，不清空）
    - 实现findSerializer流程（SerializersModule → 反射 → 失败）
    - 实现Any::class特殊处理（不进行类型检查）
    - 实现反序列化失败日志记录（WARN级别）
- **2025-01-27**: 完成阶段5（API接口重构）
    - CharonFlow.subscribe改为非suspend
    - handler接收反序列化对象，不再接收cancel函数
    - Subscription.updateHandler移除cancel参数
    - Subscription实现pause/resume逻辑（pause状态忽略消息，不缓冲）
    - 创建CharonFlowImpl、PubSubSubscription、PubSubManager
    - Code Review反馈修复：
        - 添加topic字段到Message类（Pub/Sub基础传输类）
        - 删除PubSubMessage，统一使用Message
        - Subscription持用handler，实现handleReceivedMessage
        - 修复异常类型（TypeNotRegisteredException而非SubscriptionNotFoundException）
        - 替换println为Logger
- **当前状态**: **MVP已完成！** 阶段7完成，所有MVP功能已实现
- **2025-01-27**: 完成阶段7（Pub/Sub模式实现）
    - 创建 RedisConnectionManager（管理 Redis 连接和 Pub/Sub 连接，使用 ByteArrayCodec 编解码）
    - 创建 PubSubMessageListener（实现 RedisPubSubListener，接收消息并路由到 CharonFlowImpl）
    - 实现 publish 方法（序列化 Message 为 CBOR 并使用 Lettuce 发布到 Redis）
    - 实现 subscribe 方法（订阅 Redis 主题，添加 PubSubMessageListener，管理订阅生命周期）
    - 完善订阅管理（unsubscribe、pause、resume、close 自动清理）
    - 创建 CharonFlowPubSubTest.kt 测试类（6 个测试用例覆盖主要场景）
    - 修复 CharonFlow.create 工厂方法（移到接口 companion object 中）
- **2025-01-27**: 完成阶段6（异常和错误处理）
    - SerializationException子类已存在（SerializeFailedException, DeserializeFailedException, TypeNotRegisteredException, TypeMismatchException）
    - 实现handler异常终止订阅逻辑（PubSubSubscription.handleReceivedMessage捕获异常后设置_isActive=false并更新stats）
    - 实现类型不匹配静默忽略逻辑（CharonFlowImpl.handleIncomingMessage中检查类型匹配，不匹配则DEBUG日志并跳过）
    - 添加消息分发逻辑（CharonFlowImpl.handleIncomingMessage统一处理接收到的消息）
    - PubSubSubscription添加messageType字段用于类型匹配

---

*此文档用于确保开发连续性，新的会话可以基于此文档了解项目状态和设计决策。*
