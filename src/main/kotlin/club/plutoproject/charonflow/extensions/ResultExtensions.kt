package club.plutoproject.charonflow.extensions

import club.plutoproject.charonflow.core.exceptions.*
import io.lettuce.core.RedisCommandTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result<T> 扩展函数
 *
 * 提供丰富的错误处理和转换功能，简化 CharonFlow 中的错误处理。
 */

// region 基础扩展

/**
 * 获取值或抛出异常
 *
 * @param message 自定义错误消息（可选）
 * @throws IllegalStateException 如果 Result 是失败状态
 */
fun <T> Result<T>.getOrThrow(message: String? = null): T {
    return getOrElse { throwable ->
        val errorMessage = message ?: throwable.message ?: "Operation failed"
        throw IllegalStateException(errorMessage, throwable)
    }
}

/**
 * 获取值或返回 null
 */
fun <T> Result<T>.getOrNull(): T? = getOrNull()

/**
 * 获取值或返回默认值
 *
 * @param defaultValue 默认值
 */
fun <T> Result<T>.getOrDefault(defaultValue: T): T = getOrDefault(defaultValue)

/**
 * 获取值或执行动作并返回默认值
 *
 * @param defaultValue 默认值
 * @param onFailure 失败时执行的动作
 */
fun <T> Result<T>.getOrElse(defaultValue: T, onFailure: (Throwable) -> Unit): T {
    return onFailure { onFailure(it) }.getOrDefault(defaultValue)
}

/**
 * 如果成功执行动作
 *
 * @param action 成功时执行的动作
 */
fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    return also { result ->
        result.onSuccess(action)
    }
}

/**
 * 如果失败执行动作
 *
 * @param action 失败时执行的动作
 */
fun <T> Result<T>.onFailure(action: (Throwable) -> Unit): Result<T> {
    return also { result ->
        result.onFailure(action)
    }
}

/**
 * 转换成功值
 *
 * @param transform 转换函数
 */
fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return mapCatching(transform)
}

/**
 * 转换成功值（可能抛出异常）
 *
 * @param transform 转换函数（可能抛出异常）
 */
fun <T, R> Result<T>.mapCatching(transform: (T) -> R): Result<R> {
    return fold(
        onSuccess = { value -> runCatching { transform(value) } },
        onFailure = { exception -> Result.failure(exception) }
    )
}

/**
 * 扁平化转换
 *
 * @param transform 转换函数，返回另一个 Result
 */
fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return fold(
        onSuccess = { value -> transform(value) },
        onFailure = { exception -> Result.failure(exception) }
    )
}

// endregion

// region CharonFlow 特定扩展

/**
 * 如果是连接错误执行动作
 *
 * @param action 连接错误时执行的动作
 */
fun <T> Result<T>.onConnectionError(action: (ConnectionException) -> Unit): Result<T> {
    return onFailure { throwable ->
        if (throwable is ConnectionException) {
            action(throwable)
        }
    }
}

/**
 * 如果是序列化错误执行动作
 *
 * @param action 序列化错误时执行的动作
 */
fun <T> Result<T>.onSerializationError(action: (SerializationException) -> Unit): Result<T> {
    return onFailure { throwable ->
        if (throwable is SerializationException) {
            action(throwable)
        }
    }
}

/**
 * 如果是操作错误执行动作
 *
 * @param action 操作错误时执行的动作
 */
fun <T> Result<T>.onOperationError(action: (OperationException) -> Unit): Result<T> {
    return onFailure { throwable ->
        if (throwable is OperationException) {
            action(throwable)
        }
    }
}

/**
 * 如果是 RPC 错误执行动作
 *
 * @param action RPC 错误时执行的动作
 */
fun <T> Result<T>.onRpcError(action: (RpcException) -> Unit): Result<T> {
    return onFailure { throwable ->
        if (throwable is RpcException) {
            action(throwable)
        }
    }
}

/**
 * 如果是超时错误执行动作
 *
 * @param action 超时错误时执行的动作
 */
fun <T> Result<T>.onTimeoutError(action: (Throwable) -> Unit): Result<T> {
    return onFailure { throwable ->
        when (throwable) {
            is club.plutoproject.charonflow.core.exceptions.ConnectionTimeoutException,
            is club.plutoproject.charonflow.core.exceptions.OperationTimeoutException,
            is RedisCommandTimeoutException,
            is java.net.SocketTimeoutException -> action(throwable)
        }
    }
}

/**
 * 检查是否是特定类型的错误
 *
 * @param exceptionClass 异常类型
 */
fun <T> Result<T>.isFailureOf(exceptionClass: Class<out Throwable>): Boolean {
    return exceptionOrNull()?.let { exceptionClass.isInstance(it) } ?: false
}

/**
 * 获取错误码（如果是 CharonException）
 */
fun <T> Result<T>.getErrorCode(): String? {
    return exceptionOrNull()?.let { throwable ->
        when (throwable) {
            is CharonException -> throwable.code
            else -> null
        }
    }
}

/**
 * 获取错误严重程度（如果是 CharonException）
 */
fun <T> Result<T>.getSeverity(): CharonException.Severity? {
    return exceptionOrNull()?.let { throwable ->
        when (throwable) {
            is CharonException -> throwable.severity
            else -> null
        }
    }
}

/**
 * 检查错误是否可重试
 */
fun <T> Result<T>.isRetryable(): Boolean {
    return exceptionOrNull()?.let { throwable ->
        when (throwable) {
            is CharonException -> throwable.retryable
            else -> club.plutoproject.charonflow.core.exceptions.ExceptionUtils.isRetryable(throwable)
        }
    } ?: false
}

// endregion

// region 重试相关扩展

/**
 * 带重试的执行
 *
 * @param maxAttempts 最大尝试次数（包括第一次）
 * @param retryDelay 重试延迟
 * @param retryCondition 重试条件（默认所有可重试错误都重试）
 * @param block 要执行的代码块
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    retryDelay: Duration? = null,
    retryCondition: (Throwable) -> Boolean = {
        club.plutoproject.charonflow.core.exceptions.ExceptionUtils.isRetryable(
            it
        )
    },
    block: suspend (attempt: Int) -> T
): Result<T> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }

    var lastException: Throwable? = null

    for (attempt in 1..maxAttempts) {
        val result = runCatching { block(attempt) }

        if (result.isSuccess) {
            return result
        }

        lastException = result.exceptionOrNull()!!

        // 检查是否应该重试
        if (attempt < maxAttempts && retryCondition(lastException)) {
            // 等待重试延迟
            retryDelay?.let { delay ->
                kotlinx.coroutines.delay(delay)
            }
            continue
        }

        break
    }

    return Result.failure(lastException!!)
}

/**
 * 带指数退避的重试
 *
 * @param maxAttempts 最大尝试次数
 * @param initialDelay 初始延迟
 * @param maxDelay 最大延迟
 * @param multiplier 退避乘数
 * @param retryCondition 重试条件
 * @param block 要执行的代码块
 */
suspend fun <T> withExponentialBackoff(
    maxAttempts: Int = 3,
    initialDelay: Duration,
    maxDelay: Duration,
    multiplier: Double = 2.0,
    retryCondition: (Throwable) -> Boolean = {
        club.plutoproject.charonflow.core.exceptions.ExceptionUtils.isRetryable(
            it
        )
    },
    block: suspend (attempt: Int) -> T
): Result<T> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    require(initialDelay.isPositive()) { "initialDelay must be positive" }
    require(maxDelay.isPositive()) { "maxDelay must be positive" }
    require(multiplier > 1.0) { "multiplier must be > 1.0" }

    var lastException: Throwable? = null
    var currentDelay = initialDelay

    for (attempt in 1..maxAttempts) {
        val result = runCatching { block(attempt) }

        if (result.isSuccess) {
            return result
        }

        lastException = result.exceptionOrNull()!!

        // 检查是否应该重试
        if (attempt < maxAttempts && retryCondition(lastException)) {
            // 等待当前延迟
            kotlinx.coroutines.delay(currentDelay)

            // 计算下一次延迟
            currentDelay = (currentDelay * multiplier).let { delay ->
                if (delay > maxDelay) maxDelay else delay
            }

            continue
        }

        break
    }

    return Result.failure(lastException!!)
}

// endregion

// region 组合扩展

/**
 * 组合多个 Result
 *
 * 所有 Result 都成功时返回成功，否则返回第一个失败
 */
fun <T1, T2, R> combine(
    result1: Result<T1>,
    result2: Result<T2>,
    transform: (T1, T2) -> R
): Result<R> {
    return result1.flatMap { value1 ->
        result2.map { value2 ->
            transform(value1, value2)
        }
    }
}

/**
 * 组合多个 Result
 *
 * 所有 Result 都成功时返回成功，否则返回第一个失败
 */
fun <T1, T2, T3, R> combine(
    result1: Result<T1>,
    result2: Result<T2>,
    result3: Result<T3>,
    transform: (T1, T2, T3) -> R
): Result<R> {
    return result1.flatMap { value1 ->
        result2.flatMap { value2 ->
            result3.map { value3 ->
                transform(value1, value2, value3)
            }
        }
    }
}

/**
 * 收集所有 Result（无论成功失败）
 *
 * @return Pair(成功列表, 失败列表)
 */
fun <T> Collection<Result<T>>.partitionResults(): Pair<List<T>, List<Throwable>> {
    val successes = mutableListOf<T>()
    val failures = mutableListOf<Throwable>()

    forEach { result ->
        result.fold(
            onSuccess = { successes.add(it) },
            onFailure = { failures.add(it) }
        )
    }

    return successes to failures
}

/**
 * 收集所有成功的 Result
 */
fun <T> Collection<Result<T>>.collectSuccesses(): List<T> {
    return mapNotNull { it.getOrNull() }
}

/**
 * 收集所有失败的 Result
 */
fun <T> Collection<Result<T>>.collectFailures(): List<Throwable> {
    return mapNotNull { it.exceptionOrNull() }
}

// endregion

// region 调试和日志扩展

/**
 * 记录结果到日志
 *
 * @param logger 日志记录器
 * @param successMessage 成功消息模板（可以使用 {value} 占位符）
 * @param failureMessage 失败消息模板（可以使用 {error} 占位符）
 */
fun <T> Result<T>.logTo(
    logger: org.slf4j.Logger,
    successMessage: String = "Operation succeeded: {value}",
    failureMessage: String = "Operation failed: {error}"
): Result<T> {
    return also { result ->
        result.fold(
            onSuccess = { value ->
                val message = successMessage.replace("{value}", value.toString())
                logger.info(message)
            },
            onFailure = { error ->
                val message = failureMessage.replace("{error}", error.message ?: error.toString())
                logger.error(message, error)
            }
        )
    }
}

/**
 * 跟踪执行时间
 *
 * @param block 要执行的代码块
 * @return Pair(执行结果, 执行时间)
 */
suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Duration> {
    val startTime = System.currentTimeMillis()
    val result = block()
    val endTime = System.currentTimeMillis()
    val duration = (endTime - startTime).milliseconds
    return result to duration
}

/**
 * 带时间测量的执行
 *
 * @param block 要执行的代码块
 * @return Result 包含结果和执行时间
 */
suspend fun <T> runCatchingWithTime(block: suspend () -> T): Result<Pair<T, Duration>> {
    return runCatching {
        measureTime(block)
    }
}

// endregion
