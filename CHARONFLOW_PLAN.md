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
- **自动配置**: 默认 `maxTotal = max(CPU核心数/2, 4)`
- **可覆盖**: 通过 `ConnectionPoolConfig` 允许用户自定义
- **包装策略**: 所有 Lettuce 对象都包装，避免依赖冲突
- **健康检查**: 定期连接健康检查
- **连接泄露防护**: 自动检测和清理

### 2. 序列化策略
- **主要机制**: Kotlinx Serialization + CBOR（二进制格式，性能好）
- **类型安全**: 强制使用 `@Serializable` 注解，不提供 GSON fallback
- **注册机制**: 编译时自动发现 + 运行时注册自定义序列化器
- **严格验证**: 确保所有传输类型都可序列化

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

### 6. RPC 参数设计
- **单参数API**: `suspend fun <T, R> rpc(method: String, param: T): Result<R>`
- **多参数支持**: 通过 `RpcRequest` wrapper 类
- **可变参数辅助**: `suspend fun <R> rpc(method: String, vararg params: Any): Result<R>`

## API Demo 示例

### Demo 1: 基础 Pub/Sub
```kotlin
// 创建 CharonFlow 实例
val charon = CharonFlow.create {
    redis("redis://localhost:6379")
    serializer = CborSerializer
    connectionPool {
        maxTotal = 8  // 可覆盖自动配置
        minIdle = 2
    }
}

// 订阅者
val subscription = charon.subscribe("user-events") { event: UserEvent, cancel ->
    println("Received user event: $event")
    
    // 可以在 handler 内部取消订阅
    if (event.type == "shutdown") {
        cancel()
    }
}

// 发布者
charon.publish("user-events", UserCreated("user123", "Alice"))

// 稍后取消订阅
subscription.unsubscribe()

// 或者使用 use 语法自动管理
CharonFlow.create { ... }.use { charon ->
    charon.subscribe("events") { event, _ ->
        println("Event: $event")
    }
    // 退出 use 块时自动取消订阅
}
```

### Demo 2: 请求-响应模式
```kotlin
// 服务端：处理请求
charon.onRequest("get-user-info") { userId: String ->
    // 查询数据库等操作
    UserInfo(userId, "Alice", "alice@example.com")
}

// 客户端：发送请求
val userInfo: Result<UserInfo> = charon.request("get-user-info", "user123")

// 处理结果
userInfo.onSuccess { info ->
    println("User info: $info")
}.onFailure { error ->
    println("Failed to get user info: $error")
}
```

### Demo 3: RPC 调用
```kotlin
// 单参数 RPC
val result: Result<Int> = charon.rpc("calculate-sum", 42)

// 多参数 RPC（通过 wrapper）
val sum: Result<Int> = charon.rpc("add", RpcRequest(listOf(10, 20, 30)))

// 可变参数辅助函数
val product: Result<Int> = charon.rpc("multiply", 2, 3, 4)

// 自定义类型参数
val user: Result<User> = charon.rpc("create-user", 
    CreateUserRequest("Alice", "alice@example.com"))
```

### Demo 4: 流式 RPC
```kotlin
// 服务端：流式响应
charon.registerStreamRpc("stream-numbers") { count: Int ->
    flow {
        for (i in 1..count) {
            emit(i)
            delay(100)
        }
    }
}

// 客户端：消费流
charon.streamRpc<Int>("stream-numbers", 10).collect { number ->
    println("Received: $number")
}
```

### Demo 5: 组播和广播
```kotlin
// 加入组播组
charon.joinMulticastGroup("chat-room-1")

// 发送组播消息
charon.multicast("chat-room-1", ChatMessage("user1", "Hello everyone!"))

// 接收组播消息
charon.onMulticast("chat-room-1") { message: ChatMessage ->
    println("${message.sender}: ${message.text}")
}

// 广播消息（给所有节点）
charon.broadcast("system-alert", SystemAlert("Maintenance in 5 minutes"))
```

### Demo 6: 错误处理
```kotlin
// 统一使用 Result<T> 处理错误
val result: Result<Unit> = charon.publish("events", myEvent)

// 链式处理
result
    .onSuccess { 
        println("Published successfully")
    }
    .onFailure { error ->
        when (error) {
            is ConnectionException -> println("Redis connection failed")
            is TimeoutException -> println("Operation timed out")
            else -> println("Unknown error: $error")
        }
    }

// 安全获取值
val value: String? = result.getOrNull()
val valueOrDefault: String = result.getOrDefault("default")
```

### Demo 7: 配置和构建器模式
```kotlin
// 完整配置示例
val charon = CharonFlow.create {
    redis("redis://localhost:6379") {
        timeout = 10.seconds
        ssl = true
        database = 1
    }
    
    serializer = CborSerializer {
        prettyPrint = false
        encodeDefaults = true
    }
    
    retryPolicy {
        connection {
            maxAttempts = 3
            backoffStrategy = ExponentialBackoff(initialDelay = 1.seconds)
        }
        message {
            maxAttempts = 2
            backoffStrategy = FixedBackoff(delay = 500.milliseconds)
        }
    }
    
    connectionPool {
        maxTotal = Runtime.getRuntime().availableProcessors() / 2
        minIdle = 2
        testOnBorrow = true
    }
    
    // 高级配置
    enableHealthCheck = true
    healthCheckInterval = 30.seconds
    enableMetrics = false  // 按需开启监控
}
```

## 文件结构规划

```
src/main/kotlin/club/plutoproject/charonflow/
├── CharonFlow.kt                    # 主入口类
├── builder/
│   └── CharonFlowBuilder.kt        # DSL构建器
├── config/
│   ├── Config.kt                   # 主配置类
│   ├── ConnectionPoolConfig.kt     # 连接池配置
│   ├── RetryPolicy.kt             # 重试策略配置
│   └── SerializerConfig.kt        # 序列化配置
├── core/
│   ├── Message.kt                  # 消息抽象
│   ├── Subscription.kt            # 订阅接口
│   └── exceptions/
│       ├── CharonException.kt     # 基础异常
│       ├── ConnectionException.kt # 连接异常
│       ├── TimeoutException.kt    # 超时异常
│       └── SerializationException.kt # 序列化异常
├── transport/
│   ├── RedisTransport.kt          # Redis传输层
│   ├── ConnectionManager.kt       # 连接管理器
│   └── health/
│       └── HealthChecker.kt       # 健康检查
├── protocol/
│   ├── Serializer.kt              # 序列化接口
│   ├── CborSerializer.kt          # CBOR实现
│   ├── MessageCodec.kt           # 消息编解码器
│   └── TypeRegistry.kt           # 类型注册
├── patterns/
│   ├── PubSub.kt                  # Pub/Sub实现
│   ├── RequestResponse.kt         # 请求-响应实现
│   ├── Rpc.kt                     # RPC实现
│   ├── Multicast.kt               # 组播实现
│   └── Broadcast.kt               # 广播实现
├── internal/
│   └── utils/
│       └── CoroutineUtils.kt     # 协程工具
├── extensions/
│   ├── ResultExtensions.kt       # Result扩展函数
│   └── ErrorHandlingExtensions.kt # 错误处理扩展
└── examples/
    └── BasicExamples.kt          # 使用示例
```

## 核心接口签名

### 1. CharonFlow 主接口
```kotlin
interface CharonFlow : Closeable {
    // Pub/Sub
    suspend fun publish(topic: String, message: Any): Result<Unit>
    suspend fun subscribe(
        topic: String, 
        handler: suspend (message: Any, cancel: () -> Unit) -> Unit
    ): Result<Subscription>
    
    // 请求-响应
    suspend fun <T> request(channel: String, request: Any): Result<T>
    suspend fun onRequest(
        channel: String, 
        handler: suspend (request: Any) -> Any
    ): Result<Unit>
    
    // RPC
    suspend fun <T, R> rpc(method: String, param: T): Result<R>
    suspend fun <R> rpc(method: String, request: RpcRequest): Result<R>
    suspend fun <R> rpc(method: String, vararg params: Any): Result<R>
    
    // 组播/广播
    suspend fun joinMulticastGroup(group: String): Result<Unit>
    suspend fun multicast(group: String, message: Any): Result<Unit>
    suspend fun broadcast(channel: String, message: Any): Result<Unit>
    
    // 流式RPC
    suspend fun <T, R> registerStreamRpc(
        method: String, 
        handler: suspend (param: T) -> Flow<R>
    ): Result<Unit>
    
    suspend fun <T> streamRpc(method: String, param: Any): Flow<T>
}
```

### 2. 配置类签名
```kotlin
// 主配置
data class Config(
    val redisUri: String,
    val serializer: Serializer = CborSerializer(),
    val connectionPool: ConnectionPoolConfig = ConnectionPoolConfig(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val timeout: Duration = 5.seconds,
    val enableHealthCheck: Boolean = true,
    val healthCheckInterval: Duration = 30.seconds
)

// 连接池配置（基于CPU核心数）
data class ConnectionPoolConfig(
    val maxTotal: Int = max(Runtime.getRuntime().availableProcessors() / 2, 4),
    val maxIdle: Int = maxTotal,
    val minIdle: Int = 2,
    val testOnBorrow: Boolean = true
)

// 重试策略
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoffStrategy: BackoffStrategy = ExponentialBackoff(),
    val retryableExceptions: Set<KClass<out Throwable>> = setOf(
        ConnectionException::class,
        TimeoutException::class
    )
)
```

### 3. 序列化接口
```kotlin
interface Serializer {
    fun <T> serialize(value: T): ByteArray
    fun <T> deserialize(bytes: ByteArray, clazz: KClass<T>): T
    fun registerType<T : Any>(clazz: KClass<T>, serializer: KSerializer<T>)
}
```

## 当前待办事项 (TODO)

### 阶段1：基础架构搭建 (已完成)
- [x] 更新构建配置（添加依赖和插件）
- [x] 创建基础包结构目录
- [x] 创建日志配置文件（logback.xml）

### 阶段2：核心接口和配置定义
- [ ] 创建Config配置类体系
- [ ] 定义核心接口（CharonFlow、Message等）
- [ ] 创建异常类体系
- [ ] 定义Result扩展函数

### 阶段3：连接管理和序列化实现
- [ ] 实现Redis连接管理（基于CPU核心数）
- [ ] 实现序列化层（Kotlinx Serialization + CBOR）
- [ ] 实现类型注册机制

### 阶段4：Pub/Sub模式实现（优先）
- [ ] 实现Pub/Sub核心功能
- [ ] 实现订阅管理（四种取消方式）
- [ ] 创建测试用例验证Pub/Sub

### 阶段5：其他通讯模式实现
- [ ] 实现请求-响应模式
- [ ] 实现RPC系统（单参数+多参数）
- [ ] 实现组播和广播

### 阶段6：API完善和DSL构建器
- [ ] 实现流畅的DSL构建器
- [ ] 添加错误处理扩展函数
- [ ] 编写基础使用示例

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

## 实现优先级
1. **高优先级**：Pub/Sub 基础功能（阶段4）
2. **中优先级**：连接管理和序列化（阶段3）
3. **中优先级**：请求-响应模式（阶段5-1）
4. **低优先级**：其他高级功能（阶段5-2, 5-3）

## 注意事项
1. 所有公共 API 必须返回 `Result<T>` 类型
2. 序列化必须使用 Kotlinx Serialization，强制类型安全
3. 连接池默认基于 CPU 核心数自动配置
4. 订阅管理提供四种取消方式
5. RPC 支持单参数和多参数两种方式

## 更新记录
- **2025-01-26**: 创建初始计划文档，包含完整的设计决策、API Demo 和实现计划
- **当前状态**: 阶段1已完成，等待开始阶段2

---

*此文档用于确保开发连续性，新的会话可以基于此文档了解项目状态和设计决策。*