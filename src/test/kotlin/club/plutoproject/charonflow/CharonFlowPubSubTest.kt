package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.CharonFlowConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试数据类 - 必须在文件级别定义，以便 TypeResolver 能找到
 */
@Serializable
data class TestMessage(
    val id: String,
    val content: String
)

@Serializable
data class AnotherMessage(
    val value: Int
)

/**
 * CharonFlow Pub/Sub 基础测试
 *
 * 这些测试需要 Redis 服务器运行在本地的 6379 端口。
 */
class CharonFlowPubSubTest {

    private lateinit var charonFlow: CharonFlow

    @BeforeEach
    fun setUp() {
        val config = CharonFlowConfig(
            redisUri = "redis://localhost:6379",
            serializersModule = SerializersModule {
                contextual(TestMessage::class, TestMessage.serializer())
                contextual(AnotherMessage::class, AnotherMessage.serializer())
            }
        )
        charonFlow = CharonFlow.create(config)
    }

    @AfterEach
    fun tearDown() {
        charonFlow.close()
    }

    @Test
    fun `test basic pub sub`() = runBlocking {
        val receivedMessages = mutableListOf<TestMessage>()
        val latch = java.util.concurrent.CountDownLatch(1)

        // 订阅
        val subscription = charonFlow.subscribe(
            topic = "test-topic",
            clazz = TestMessage::class
        ) { message: TestMessage ->
            receivedMessages.add(message)
            latch.countDown()
        }.getOrThrow()

        assertNotNull(subscription)
        assertTrue(subscription.isActive)

        // 发布消息
        val testMessage = TestMessage(id = "1", content = "Hello, World!")
        val publishResult = charonFlow.publish("test-topic", testMessage)
        assertTrue(publishResult.isSuccess)

        // 等待消息接收
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

        // 验证
        assertEquals(1, receivedMessages.size)
        assertEquals(testMessage.id, receivedMessages[0].id)
        assertEquals(testMessage.content, receivedMessages[0].content)

        println(receivedMessages.first())

        // 取消订阅
        subscription.unsubscribe()
        assertTrue(!subscription.isActive)
    }

    @Test
    fun `test subscribe any type`() {
        runBlocking {
            val receivedMessages = mutableListOf<Any>()
            val latch = java.util.concurrent.CountDownLatch(2)

            // 订阅 Any 类型（接收所有消息）
            val subscription = charonFlow.subscribe(
                topic = "test-any-topic"
            ) { message: Any ->
                receivedMessages.add(message)
                latch.countDown()
            }.getOrThrow()

            // 发布不同类型的消息
            val testMessage1 = TestMessage(id = "1", content = "Message 1")
            val testMessage2 = AnotherMessage(value = 42)

            charonFlow.publish("test-any-topic", testMessage1)
            charonFlow.publish("test-any-topic", testMessage2)

            // 等待消息接收
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

            // 验证
            assertEquals(2, receivedMessages.size)
            assertTrue(receivedMessages[0] is TestMessage)
            assertTrue(receivedMessages[1] is AnotherMessage)

            subscription.unsubscribe()
        }
    }

    @Test
    fun `test type mismatch silent ignore`() {
        runBlocking {
            val receivedMessages = mutableListOf<TestMessage>()

            // 只订阅 TestMessage 类型
            val subscription = charonFlow.subscribe(
                topic = "test-filter-topic",
                clazz = TestMessage::class
            ) { message: TestMessage ->
                receivedMessages.add(message)
            }.getOrThrow()

            // 发布不同类型的消息
            val testMessage = TestMessage(id = "1", content = "Should receive")
            val anotherMessage = AnotherMessage(value = 999)

            charonFlow.publish("test-filter-topic", testMessage)
            charonFlow.publish("test-filter-topic", anotherMessage)

            // 等待一段时间
            delay(1000)

            // 验证只收到了 TestMessage
            assertEquals(1, receivedMessages.size)
            assertEquals("1", receivedMessages[0].id)

            subscription.unsubscribe()
        }
    }

    @Test
    fun `test pause and resume`() {
        runBlocking {
            val receivedMessages = mutableListOf<TestMessage>()

            val subscription = charonFlow.subscribe(
                topic = "test-pause-topic",
                clazz = TestMessage::class
            ) { message: TestMessage ->
                receivedMessages.add(message)
            }.getOrThrow()

            // 发布第一条消息
            charonFlow.publish("test-pause-topic", TestMessage(id = "1", content = "Before pause"))
            delay(500)
            assertEquals(1, receivedMessages.size)

            // 暂停订阅
            subscription.pause()
            assertTrue(subscription.isPaused)

            // 发布第二条消息（应该被忽略）
            charonFlow.publish("test-pause-topic", TestMessage(id = "2", content = "During pause"))
            delay(500)
            assertEquals(1, receivedMessages.size) // 数量不变

            // 恢复订阅
            subscription.resume()
            assertTrue(!subscription.isPaused)

            // 发布第三条消息
            charonFlow.publish("test-pause-topic", TestMessage(id = "3", content = "After resume"))
            delay(500)
            assertEquals(2, receivedMessages.size)

            subscription.unsubscribe()
        }
    }

    @Test
    fun `test handler exception cancels subscription`() = runBlocking {
        var messageCount = 0

        val subscription = charonFlow.subscribe(
            topic = "test-error-topic",
            clazz = TestMessage::class
        ) { message: TestMessage ->
            messageCount++
            if (messageCount == 1) {
                throw RuntimeException("Test exception")
            }
        }.getOrThrow()

        // 发布第一条消息（触发异常）
        charonFlow.publish("test-error-topic", TestMessage(id = "1", content = "Trigger error"))
        delay(500)

        // 验证订阅已被取消
        assertTrue(!subscription.isActive)

        // 发布第二条消息（不应被处理）
        charonFlow.publish("test-error-topic", TestMessage(id = "2", content = "After error"))
        delay(500)

        assertEquals(1, messageCount)
    }

    @Test
    fun `test publisher receives own messages in same connection`() {
        runBlocking {
            val receivedMessages = mutableListOf<TestMessage>()

            // 同一个客户端订阅并发布
            val subscription = charonFlow.subscribe(
                topic = "test-self-publish-topic",
                clazz = TestMessage::class
            ) { message: TestMessage ->
                receivedMessages.add(message)
            }.getOrThrow()

            // 发布消息
            val testMessage = TestMessage(id = "1", content = "Self published")
            charonFlow.publish("test-self-publish-topic", testMessage)

            // 等待一段时间（给 Redis 处理时间）
            delay(1000)

            // 验证：在同一个 Redis 连接中，订阅者会收到所有消息，包括自己发布的
            // 这是因为 Lettuce 使用同一个连接进行订阅和发布
            assertEquals(1, receivedMessages.size, 
                "In same Redis connection, subscriber should receive all messages including self-published")
            assertEquals("Self published", receivedMessages[0].content)

            subscription.unsubscribe()
        }
    }

    @Test
    fun `test cross client messaging`() {
        runBlocking {
        // 注意：在同一个 JVM 进程中，两个 CharonFlow 实例实际上共享同一个 Redis 连接
        // 但在 Redis 层面，Pub/Sub 不会将消息发送给发布者自己
        // 这个测试验证了当两个不同的连接（不同的 client）存在时，消息传递行为

        val receivedByClient1 = mutableListOf<TestMessage>()
        val receivedByClient2 = mutableListOf<TestMessage>()

        // 两个订阅都使用同一个 charonFlow 实例（模拟两个逻辑客户端）
        val subscription1 = charonFlow.subscribe(
            topic = "test-cross-client-topic",
            clazz = TestMessage::class
        ) { message: TestMessage ->
            receivedByClient1.add(message)
        }.getOrThrow()

        val subscription2 = charonFlow.subscribe(
            topic = "test-cross-client-topic",
            clazz = TestMessage::class
        ) { message: TestMessage ->
            receivedByClient2.add(message)
        }.getOrThrow()

        // 等待订阅建立
        delay(500)

        // 发布消息
        val testMessage = TestMessage(id = "1", content = "Test Message")
        charonFlow.publish("test-cross-client-topic", testMessage)

        // 等待消息传递
        delay(1000)

        // 验证：
        // 在同一个 Redis 连接中，订阅者会收到消息，无论谁发布的
        // 所以两个订阅都应该收到消息（因为它们共享同一个连接）
        assertEquals(1, receivedByClient1.size, 
            "Subscriber 1 should receive the message")
        assertEquals(1, receivedByClient2.size, 
            "Subscriber 2 should receive the message")
        assertEquals("Test Message", receivedByClient1[0].content)
        assertEquals("Test Message", receivedByClient2[0].content)

            subscription1.unsubscribe()
            subscription2.unsubscribe()
        }
    }

}
