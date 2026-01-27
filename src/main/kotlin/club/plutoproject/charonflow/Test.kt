package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.Config
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@Serializable
data class TestMessage1(
    val name: String = "TestMessage1"
)

@OptIn(InternalSerializationApi::class)
fun main(): Unit = runBlocking {
    val config = Config(
        redisUri = "redis://localhost:6379",
        serializersModule = SerializersModule {
            contextual(TestMessage1::class, TestMessage1::class.serializer())
        },
    )
    val charonFlow = CharonFlow.create(config)

    charonFlow.subscribe("test-message", TestMessage1::class) {
        println("Received Test Message at thread: ${Thread.currentThread().name}, content: $it")
    }

    charonFlow.publish("test-message", TestMessage1())
    println("TestMessagePublished")
    delay(Long.MAX_VALUE)
}
