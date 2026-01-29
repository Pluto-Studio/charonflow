package club.plutoproject.charonflow.internal.core

import club.plutoproject.charonflow.internal.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Pub/Sub 管理器
 *
 * 管理所有的 Pub/Sub 订阅，提供订阅的增删改查功能。
 */
internal class PubSubManager(
    private val redisHandler: RedisSubscriptionHandler? = null
) {

    private val subscriptions = ConcurrentHashMap<String, PubSubSubscription>()
    private val topicIndex = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * 添加订阅
     */
    fun addSubscription(subscription: PubSubSubscription): Boolean {
        val existing = subscriptions.putIfAbsent(subscription.id, subscription)
        if (existing != null) {
            return false
        }

        topicIndex.computeIfAbsent(subscription.topic) { mutableSetOf() }.add(subscription.id)

        // 设置取消订阅回调，自动从管理器中移除
        subscription.setOnUnsubscribeCallback {
            removeSubscription(subscription.id)

            // 如果该主题没有其他活跃订阅，取消 Redis 订阅
            if (!hasActiveSubscriptions(subscription.topic)) {
                try {
                    redisHandler?.unsubscribeFromRedis(subscription.topic)
                } catch (e: Exception) {
                    logger.error("Failed to unsubscribe from Redis topic: {}", subscription.topic, e)
                }
            }
        }

        return true
    }

    /**
     * 移除订阅
     */
    fun removeSubscription(subscriptionId: String): PubSubSubscription? {
        val subscription = subscriptions.remove(subscriptionId)
        subscription?.let { sub ->
            topicIndex.compute(sub.topic) { _, ids ->
                ids?.apply { remove(subscriptionId) }
                    ?.takeIf { it.isNotEmpty() }
            }
        }
        return subscription
    }

    /**
     * 获取订阅
     */
    fun getSubscription(subscriptionId: String): PubSubSubscription? {
        return subscriptions[subscriptionId]
    }

    /**
     * 根据主题获取订阅
     */
    fun getSubscriptionsByTopic(topic: String): List<PubSubSubscription> {
        return topicIndex[topic]?.mapNotNull { subscriptions[it] } ?: emptyList()
    }

    /**
     * 获取所有订阅
     */
    val allSubscriptions: Collection<PubSubSubscription>
        get() = subscriptions.values

    /**
     * 取消所有订阅
     */
    suspend fun unsubscribeAll(): Map<String, Result<Unit>> {
        return subscriptions.mapValues { (id, subscription) ->
            runCatching { subscription.unsubscribe() }
                .getOrElse { Result.failure(it) }
        }
    }

    /**
     * 暂停所有订阅
     */
    suspend fun pauseAll(): Map<String, Result<Unit>> {
        return subscriptions.mapValues { (id, subscription) ->
            runCatching { subscription.pause() }
                .getOrElse { Result.failure(it) }
        }
    }

    /**
     * 恢复所有订阅
     */
    suspend fun resumeAll(): Map<String, Result<Unit>> {
        return subscriptions.mapValues { (id, subscription) ->
            runCatching { subscription.resume() }
                .getOrElse { Result.failure(it) }
        }
    }

    /**
     * 检查主题是否还有活跃的订阅
     */
    fun hasActiveSubscriptions(topic: String): Boolean {
        return topicIndex[topic]?.any { id ->
            subscriptions[id]?.isActive == true
        } ?: false
    }

    /**
     * 获取所有活跃的主题
     */
    fun getActiveTopics(): Set<String> {
        return topicIndex.filter { (_, ids) ->
            ids.any { id -> subscriptions[id]?.isActive == true }
        }.keys
    }

    /**
     * 关闭管理器
     */
    fun close() {
        subscriptions.clear()
        topicIndex.clear()
    }
}
