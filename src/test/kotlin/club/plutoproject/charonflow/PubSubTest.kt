package club.plutoproject.charonflow

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
data class PubSubTestMessage(
    val id: Int,
    val content: String,
    val sendTimestamp: Long = 0
)

@Testcontainers
class PubSubTest {

    companion object {
        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
    }

    private lateinit var charon: CharonFlow
    private val receivedMessages = mutableListOf<Pair<PubSubTestMessage, Long>>()

    @BeforeEach
    fun setup() {
        val redisUri = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        charon = CharonFlow.create {
            this.redisUri = redisUri
        }
        receivedMessages.clear()
    }

    @AfterEach
    fun tearDown() {
        charon.close()
    }

    @Test
    fun `test pub-sub with latency measurement`(): Unit = runBlocking {
        val messageCount = 5
        val topic = "test-topic"

        // Subscribe
        val subscription = charon.subscribe(topic, PubSubTestMessage::class) { message ->
            val receiveTime = System.currentTimeMillis()
            receivedMessages.add(message to receiveTime)
        }.getOrThrow()

        // Publish messages
        val sendTimes = mutableListOf<Long>()
        repeat(messageCount) { i ->
            val sendTime = System.currentTimeMillis()
            sendTimes.add(sendTime)
            val msg = PubSubTestMessage(
                id = i + 1,
                content = "Test message #$i",
                sendTimestamp = sendTime
            )
            charon.publish(topic, msg).getOrThrow()
            delay(100) // Small delay between messages
        }

        // Wait for all messages to be received
        withTimeout(5000) {
            while (receivedMessages.size < messageCount) {
                delay(50)
            }
        }

        // Verify
        assertEquals(messageCount, receivedMessages.size, "All messages should be received")

        // Verify message content and latency
        receivedMessages.forEachIndexed { index, (message, receiveTime) ->
            assertEquals(index + 1, message.id, "Message ID should match")
            assertEquals("Test message #$index", message.content, "Message content should match")

            val latency = receiveTime - message.sendTimestamp
            assertTrue(latency >= 0, "Latency should be non-negative")
            assertTrue(latency < 1000, "Latency should be less than 1 second, was ${latency}ms")

            println("Message ${message.id}: latency=${latency}ms")
        }

        // Cleanup
        subscription.unsubscribe()
    }

    @Test
    fun `test pub-sub without subscribers`() = runBlocking {
        val topic = "empty-topic"
        val msg = PubSubTestMessage(id = 1, content = "No subscribers")

        // Publishing without subscribers should succeed
        val result = charon.publish(topic, msg)
        assertTrue(result.isSuccess, "Publishing without subscribers should succeed")
    }

    @Test
    fun `test multiple subscribers receive same message`(): Unit = runBlocking {
        val topic = "broadcast-topic"
        val subscriber1Messages = mutableListOf<PubSubTestMessage>()
        val subscriber2Messages = mutableListOf<PubSubTestMessage>()

        val sub1 = charon.subscribe<PubSubTestMessage>(topic, PubSubTestMessage::class) { message ->
            subscriber1Messages.add(message)
        }.getOrThrow()

        val sub2 = charon.subscribe<PubSubTestMessage>(topic, PubSubTestMessage::class) { message ->
            subscriber2Messages.add(message)
        }.getOrThrow()

        val msg = PubSubTestMessage(id = 1, content = "Broadcast message")
        charon.publish(topic, msg).getOrThrow()

        // Wait for messages
        withTimeout(3000) {
            while (subscriber1Messages.isEmpty() || subscriber2Messages.isEmpty()) {
                delay(50)
            }
        }

        assertEquals(1, subscriber1Messages.size, "Subscriber 1 should receive message")
        assertEquals(1, subscriber2Messages.size, "Subscriber 2 should receive message")
        assertEquals(msg.id, subscriber1Messages[0].id)
        assertEquals(msg.id, subscriber2Messages[0].id)

        sub1.unsubscribe()
        sub2.unsubscribe()
    }
}
