# CharonFlow

> [!WARNING]
> 本项目仍处于施工状态，可能会有破坏性更改。

🚤 渡送消息，穿行数据之海。

一个轻量级的消息通讯库，基于 Redis 打造。

## ✨ 示例

### 基本使用

```kotlin
@Serializable
data class Message(
    val id: Int,
    val content: String
)

fun main() = runBlocking {
    // 创建 CharonFlow 实例
    val charon = CharonFlow.create {
        redisUri = "redis://localhost:6379"
        timeout = 10.seconds
    }

    // 订阅主题
    val subscription = charon.subscribe("my-topic", Message::class) { message ->
        println("收到消息: ${message.content}")
    }.getOrThrow()

    // 发布消息
    val msg = Message(id = 1, content = "Hello, CharonFlow!")
    charon.publish("my-topic", msg).getOrThrow()

    // 取消订阅
    subscription.unsubscribe()
    charon.close()
}
```

### 使用 DSL 配置

```kotlin
val config = charonFlow {
    redisUri = "redis://localhost:6379"
    timeout = 10.seconds
    ignoreSelfPubSubMessages = true

    serialization {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    connectionPool {
        maxTotal = 10
        maxIdle = 8
        minIdle = 2
    }
}

val charon = CharonFlow.create(config)
```

### 忽略自身消息

```kotlin
// 全局配置：忽略自身发布的消息
val charon = CharonFlow.create {
    redisUri = "redis://localhost:6379"
    ignoreSelfPubSubMessages = true  // 全局设置
}

// 或在订阅时单独配置
charon.subscribe("topic", Message::class, ignoreSelf = true) { message ->
    // 不会收到自己发布的消息
}
```

### 📄️ 许可

[Pluto-Studio/charonflow](https://github.com/Pluto-Studio/charonflow)
在 [MIT License](https://opensource.org/license/mit) 下许可。
