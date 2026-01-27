package club.plutoproject.charonflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Pub/Sub 管理器
 *
 * 管理所有的 Pub/Sub 订阅，提供订阅的增删改查功能。
 */
internal class PubSubManager {

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
     * 关闭管理器
     */
    fun close() {
        subscriptions.clear()
        topicIndex.clear()
    }
}