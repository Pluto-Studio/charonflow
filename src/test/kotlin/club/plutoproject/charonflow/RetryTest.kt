package club.plutoproject.charonflow

import club.plutoproject.charonflow.config.ExponentialBackoffStrategy
import club.plutoproject.charonflow.config.FixedBackoffStrategy
import club.plutoproject.charonflow.config.LinearBackoffStrategy
import club.plutoproject.charonflow.config.RetryConfig
import club.plutoproject.charonflow.internal.retry.RetryContext
import club.plutoproject.charonflow.internal.retry.RetryExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryTest {

    private val retryExecutor = RetryExecutor()

    @Test
    fun `test successful execution without retry`() = runBlocking {
        var executionCount = 0
        val config = RetryConfig(maxAttempts = 3)

        val result = retryExecutor.executeWithRetry(
            config = config,
            operationName = "Test operation"
        ) { context ->
            executionCount++
            assertTrue(context.isFirstAttempt)
            assertEquals(1, context.currentAttempt)
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, executionCount)
    }

    @Test
    fun `test retry on retryable exception`() = runBlocking {
        var executionCount = 0
        val config = RetryConfig(
            maxAttempts = 3,
            backoffStrategy = FixedBackoffStrategy(delay = 10.milliseconds)
        )

        val result = retryExecutor.executeWithRetry(
            config = config,
            operationName = "Test operation"
        ) { context ->
            executionCount++
            if (context.attempt < 2) {
                throw ConnectException("Connection failed")
            }
            "success after retry"
        }

        assertEquals("success after retry", result)
        assertEquals(3, executionCount)
    }

    @Test
    fun `test max attempts exceeded`() = runBlocking {
        var executionCount = 0
        val config = RetryConfig(
            maxAttempts = 3,
            backoffStrategy = FixedBackoffStrategy(delay = 10.milliseconds)
        )

        assertThrows<ConnectException> {
            retryExecutor.executeWithRetry(
                config = config,
                operationName = "Failing operation"
            ) { _ ->
                executionCount++
                throw ConnectException("Always fails")
            }
        }

        assertEquals(3, executionCount)
    }

    @Test
    fun `test non-retryable exception throws immediately`() = runBlocking {
        var executionCount = 0
        val config = RetryConfig(
            maxAttempts = 3,
            retryableExceptions = setOf(ConnectException::class)
        )

        assertThrows<IllegalStateException> {
            retryExecutor.executeWithRetry(
                config = config,
                operationName = "Test operation"
            ) { _ ->
                executionCount++
                throw IllegalStateException("Non-retryable error")
            }
        }

        assertEquals(1, executionCount)
    }

    @Test
    fun `test retry context values`() = runBlocking {
        val contexts = mutableListOf<RetryContext>()
        val config = RetryConfig(
            maxAttempts = 3,
            backoffStrategy = FixedBackoffStrategy(delay = 10.milliseconds)
        )

        try {
            retryExecutor.executeWithRetry(
                config = config,
                operationName = "Test operation"
            ) { context ->
                contexts.add(context)
                throw ConnectException("Always fails")
            }
        } catch (_: ConnectException) {
            // Expected
        }

        assertEquals(3, contexts.size)

        // First attempt
        assertEquals(0, contexts[0].attempt)
        assertEquals(1, contexts[0].currentAttempt)
        assertTrue(contexts[0].isFirstAttempt)
        assertEquals(2, contexts[0].remainingAttempts)

        // Second attempt
        assertEquals(1, contexts[1].attempt)
        assertEquals(2, contexts[1].currentAttempt)
        assertEquals(1, contexts[1].remainingAttempts)

        // Third attempt (last)
        assertEquals(2, contexts[2].attempt)
        assertEquals(3, contexts[2].currentAttempt)
        assertTrue(contexts[2].isLastAttempt)
        assertEquals(0, contexts[2].remainingAttempts)
    }

    @Test
    fun `test fixed backoff strategy`() = runBlocking {
        val strategy = FixedBackoffStrategy(delay = 100.milliseconds)

        assertEquals(100.milliseconds, strategy.calculateDelay(0))
        assertEquals(100.milliseconds, strategy.calculateDelay(1))
        assertEquals(100.milliseconds, strategy.calculateDelay(10))
    }

    @Test
    fun `test exponential backoff strategy`() = runBlocking {
        val strategy = ExponentialBackoffStrategy(
            initialDelay = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 1.seconds
        )

        // First retry: 100ms * 2^0 = 100ms (with jitter, so check range)
        val delay0 = strategy.calculateDelay(0)
        assertTrue(
            delay0 in 90.milliseconds..115.milliseconds,
            "First delay should be ~100ms, was $delay0"
        )

        // Second retry: 100ms * 2^1 = 200ms
        val delay1 = strategy.calculateDelay(1)
        assertTrue(
            delay1 in 180.milliseconds..230.milliseconds,
            "Second delay should be ~200ms, was $delay1"
        )

        // Third retry: 100ms * 2^2 = 400ms
        val delay2 = strategy.calculateDelay(2)
        assertTrue(
            delay2 in 360.milliseconds..460.milliseconds,
            "Third delay should be ~400ms, was $delay2"
        )
    }

    @Test
    fun `test exponential backoff max delay`() = runBlocking {
        val strategy = ExponentialBackoffStrategy(
            initialDelay = 100.milliseconds,
            multiplier = 2.0,
            maxDelay = 300.milliseconds
        )

        // Should be capped at maxDelay
        val delay = strategy.calculateDelay(10)
        assertTrue(
            delay in 270.milliseconds..330.milliseconds,
            "Delay should be capped at ~300ms (with jitter), was $delay"
        )
    }

    @Test
    fun `test linear backoff strategy`() = runBlocking {
        val strategy = LinearBackoffStrategy(
            initialDelay = 100.milliseconds,
            increment = 50.milliseconds,
            maxDelay = 500.milliseconds
        )

        assertEquals(100.milliseconds, strategy.calculateDelay(0))
        assertEquals(150.milliseconds, strategy.calculateDelay(1))
        assertEquals(200.milliseconds, strategy.calculateDelay(2))
        assertEquals(250.milliseconds, strategy.calculateDelay(3))
    }

    @Test
    fun `test linear backoff max delay`() = runBlocking {
        val strategy = LinearBackoffStrategy(
            initialDelay = 100.milliseconds,
            increment = 100.milliseconds,
            maxDelay = 300.milliseconds
        )

        // Should be capped at maxDelay
        assertEquals(300.milliseconds, strategy.calculateDelay(10))
    }

    @Test
    fun `test multiple retryable exception types`() = runBlocking {
        val config = RetryConfig(
            maxAttempts = 5,
            retryableExceptions = setOf(
                ConnectException::class,
                SocketTimeoutException::class,
                IOException::class
            ),
            backoffStrategy = FixedBackoffStrategy(delay = 10.milliseconds)
        )

        var executionCount = 0
        val exceptions = listOf(
            ConnectException("Connection failed"),
            SocketTimeoutException("Socket timeout"),
            IOException("IO error")
        )

        val result = retryExecutor.executeWithRetry(
            config = config,
            operationName = "Test operation"
        ) { _ ->
            executionCount++
            if (executionCount <= exceptions.size) {
                throw exceptions[executionCount - 1]
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(4, executionCount)
    }

    @Test
    fun `test exception cause chain`() = runBlocking {
        val config = RetryConfig(
            maxAttempts = 2,
            retryableExceptions = setOf(ConnectException::class),
            backoffStrategy = FixedBackoffStrategy(delay = 10.milliseconds)
        )

        var executionCount = 0

        // Wrap a ConnectException in another exception
        assertThrows<RuntimeException> {
            retryExecutor.executeWithRetry(
                config = config,
                operationName = "Test operation"
            ) { _ ->
                executionCount++
                throw RuntimeException("Outer exception", ConnectException("Inner exception"))
            }
        }

        // Should retry because cause is ConnectException
        assertEquals(2, executionCount)
    }

    @Test
    fun `test single attempt config`() = runBlocking {
        var executionCount = 0
        val config = RetryConfig(maxAttempts = 1)

        assertThrows<ConnectException> {
            retryExecutor.executeWithRetry(
                config = config,
                operationName = "Test operation"
            ) { context ->
                executionCount++
                assertTrue(context.isFirstAttempt)
                assertTrue(context.isLastAttempt)
                throw ConnectException("Fails immediately")
            }
        }

        assertEquals(1, executionCount)
    }
}
