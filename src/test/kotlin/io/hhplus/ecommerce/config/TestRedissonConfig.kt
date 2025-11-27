package io.hhplus.ecommerce.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * 테스트 환경에서 작동하는 Redisson 클라이언트 제공
 *
 * 분산 락의 의미론을 보존하기 위해 ReentrantLock을 사용한 RLock 구현을 제공합니다.
 * 로컬 JVM 메모리의 ReentrantLock을 사용하여 테스트 환경에서의 동시성을 제어합니다.
 */
@TestConfiguration
class TestRedissonConfig {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private val mockLocks = ConcurrentHashMap<String, RLock>()

    @Bean
    @Primary
    fun redissonClient(): RedissonClient {
        return spyk(mockk<RedissonClient>(relaxed = true)) {
            every { getLock(any<String>()) } answers { lockKey ->
                val key = lockKey.invocation.args[0] as String

                // 같은 키에 대해 항상 같은 ReentrantLock을 사용합니다
                // true = fair lock: 스레드들이 요청 순서대로 락을 획득하도록 보장
                val reentrantLock = locks.getOrPut(key) { ReentrantLock(true) }

                // 같은 키에 대해 항상 같은 RLock 모의 객체를 반환합니다
                mockLocks.getOrPut(key) {
                    mockk<RLock>(relaxed = true).apply {
                        // tryLock(waitTime, leaseTime, unit) - Redisson의 주요 메서드
                        every { tryLock(any<Long>(), any<Long>(), any<TimeUnit>()) } answers {
                            val waitTime = it.invocation.args[0] as Long
                            val leaseTime = it.invocation.args[1] as Long
                            val unit = it.invocation.args[2] as TimeUnit
                            val waitTimeMillis = unit.toMillis(waitTime)

                            try {
                                val acquired = reentrantLock.tryLock(waitTimeMillis, TimeUnit.MILLISECONDS)
                                logger.debug("Lock tryLock: key={}, waitTime={}ms, acquired={}, thread={}",
                                    key, waitTimeMillis, acquired, Thread.currentThread().name)
                                acquired
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                logger.warn("Lock acquisition interrupted: key={}, thread={}", key, Thread.currentThread().name)
                                false
                            }
                        }

                        every { unlock() } answers {
                            try {
                                reentrantLock.unlock()
                                logger.debug("Lock released: key={}, thread={}", key, Thread.currentThread().name)
                            } catch (e: IllegalMonitorStateException) {
                                logger.warn("Lock not held by current thread: key={}, thread={}", key, Thread.currentThread().name)
                            }
                        }

                        every { isHeldByCurrentThread } propertyType Boolean::class answers {
                            reentrantLock.isHeldByCurrentThread
                        }

                        // 기타 필요한 메서드들
                        every { lock() } answers {
                            logger.debug("Lock acquired (blocking): key={}, thread={}", key, Thread.currentThread().name)
                            reentrantLock.lock()
                        }

                        every { tryLock(any<Long>(), any<TimeUnit>()) } answers {
                            val waitTime = it.invocation.args[0] as Long
                            val unit = it.invocation.args[1] as TimeUnit
                            val waitTimeMillis = unit.toMillis(waitTime)
                            reentrantLock.tryLock(waitTimeMillis, TimeUnit.MILLISECONDS)
                        }
                    }
                }
            }
        }
    }
}