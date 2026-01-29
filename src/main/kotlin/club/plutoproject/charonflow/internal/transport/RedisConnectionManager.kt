package club.plutoproject.charonflow.internal.transport

import club.plutoproject.charonflow.RedisConnectionException
import club.plutoproject.charonflow.config.CharonFlowConfig
import club.plutoproject.charonflow.internal.logger
import club.plutoproject.charonflow.internal.retry.RetryExecutor
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import java.nio.ByteBuffer

/**
 * Redis 连接管理器
 *
 * 管理 Redis 连接的生命周期，包括普通连接和 Pub/Sub 连接。
 */
internal class RedisConnectionManager(
    private val config: CharonFlowConfig,
    private val retryExecutor: RetryExecutor = RetryExecutor()
) : AutoCloseable {

    private val redisClient: RedisClient
    private var connection: StatefulRedisConnection<String, ByteArray>? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<String, ByteArray>? = null

    init {
        try {
            val redisURI = RedisURI.create(config.redisUri)
            redisClient = RedisClient.create(redisURI)
            logger.info("Redis client created for URI: {}", config.redisUri)
        } catch (e: Exception) {
            throw RedisConnectionException(
                message = "Failed to create Redis client",
                cause = e,
                redisUri = config.redisUri
            )
        }
    }

    /**
     * 获取普通连接（带重试）
     */
    suspend fun getConnection(): StatefulRedisConnection<String, ByteArray> {
        return retryExecutor.executeWithRetry(
            config = config.retryPolicyConfig.connectionRetry,
            operationName = "Redis connection"
        ) {
            if (connection == null || !connection!!.isOpen) {
                try {
                    connection = redisClient.connect(ByteArrayCodec())
                    logger.debug("Redis connection established")
                } catch (e: Exception) {
                    throw RedisConnectionException(
                        message = "Failed to connect to Redis",
                        cause = e,
                        redisUri = config.redisUri
                    )
                }
            }
            connection!!
        }
    }

    /**
     * 获取 Pub/Sub 连接（带重试）
     */
    suspend fun getPubSubConnection(): StatefulRedisPubSubConnection<String, ByteArray> {
        return retryExecutor.executeWithRetry(
            config = config.retryPolicyConfig.connectionRetry,
            operationName = "Redis Pub/Sub connection"
        ) {
            if (pubSubConnection == null || !pubSubConnection!!.isOpen) {
                try {
                    pubSubConnection = redisClient.connectPubSub(ByteArrayCodec())
                    logger.debug("Redis Pub/Sub connection established")
                } catch (e: Exception) {
                    throw RedisConnectionException(
                        message = "Failed to connect to Redis Pub/Sub",
                        cause = e,
                        redisUri = config.redisUri
                    )
                }
            }
            pubSubConnection!!
        }
    }

    /**
     * 检查连接状态
     */
    val isConnected: Boolean
        get() = connection?.isOpen == true

    /**
     * 检查 Pub/Sub 连接状态
     */
    val isPubSubConnected: Boolean
        get() = pubSubConnection?.isOpen == true

    /**
     * 关闭所有连接
     */
    override fun close() {
        try {
            connection?.close()
            pubSubConnection?.close()
            redisClient.shutdown()
            logger.info("Redis connections closed")
        } catch (e: Exception) {
            logger.error("Error closing Redis connections: {}", e.message, e)
        }
    }
}

/**
 * ByteArray 编解码器
 *
 * 用于在 Redis 中存储 ByteArray 数据。
 */
private class ByteArrayCodec : RedisCodec<String, ByteArray> {
    private val stringCodec = StringCodec.UTF8

    override fun decodeKey(bytes: ByteBuffer?): String {
        return stringCodec.decodeKey(bytes)
    }

    override fun decodeValue(bytes: ByteBuffer?): ByteArray? {
        return bytes?.let {
            val array = ByteArray(it.remaining())
            it.get(array)
            array
        }
    }

    override fun encodeKey(key: String?): ByteBuffer? {
        return stringCodec.encodeKey(key)
    }

    override fun encodeValue(value: ByteArray?): ByteBuffer? {
        return value?.let { ByteBuffer.wrap(it) }
    }
}
