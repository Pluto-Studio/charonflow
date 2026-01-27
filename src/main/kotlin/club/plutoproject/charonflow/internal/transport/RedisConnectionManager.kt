package club.plutoproject.charonflow.internal.transport

import club.plutoproject.charonflow.config.Config
import club.plutoproject.charonflow.core.exceptions.ConnectionException
import club.plutoproject.charonflow.core.exceptions.RedisConnectionException
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RedisConnectionManager::class.java)

/**
 * Redis 连接管理器
 *
 * 管理 Redis 连接的生命周期，包括普通连接和 Pub/Sub 连接。
 */
internal class RedisConnectionManager(
    private val config: Config
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
     * 获取普通连接
     */
    fun getConnection(): StatefulRedisConnection<String, ByteArray> {
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
        return connection!!
    }

    /**
     * 获取 Pub/Sub 连接
     */
    fun getPubSubConnection(): StatefulRedisPubSubConnection<String, ByteArray> {
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
        return pubSubConnection!!
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
private class ByteArrayCodec : io.lettuce.core.codec.RedisCodec<String, ByteArray> {
    private val stringCodec = io.lettuce.core.codec.StringCodec.UTF8

    override fun decodeKey(bytes: java.nio.ByteBuffer?): String {
        return stringCodec.decodeKey(bytes)
    }

    override fun decodeValue(bytes: java.nio.ByteBuffer?): ByteArray? {
        return bytes?.let {
            val array = ByteArray(it.remaining())
            it.get(array)
            array
        }
    }

    override fun encodeKey(key: String?): java.nio.ByteBuffer? {
        return stringCodec.encodeKey(key)
    }

    override fun encodeValue(value: ByteArray?): java.nio.ByteBuffer? {
        return value?.let { java.nio.ByteBuffer.wrap(it) }
    }
}