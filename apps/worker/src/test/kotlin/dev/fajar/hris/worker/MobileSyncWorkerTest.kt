package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.data.datasources.SyncDataSource
import dev.fajar.hris.sync.data.repositories.StoredSyncRepository
import dev.fajar.hris.sync.domain.usecases.MaintainMobileSync
import dev.fajar.hris.worker.sync.MobileSyncWorker
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class MobileSyncWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configuration(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
        }
    }

    @Autowired private lateinit var source: SyncDataSource
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var maintain: MaintainMobileSync

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    @BeforeEach
    fun drainPreviousFixtureWork() {
        repeat(30) {
            val result = maintain.execute()
            assertTrue(result is Result.Success, result.toString())
            if ((result as Result.Success).value.published == 0) return
        }
        fail<Unit>("Bounded fixture drain exhausted")
    }

    private fun fixture(count: Int): UUID {
        val company = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Sync worker fixture','UTC')",
                company,
                "S${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation) select ?,'EXPENSE_CLAIMS',gen_random_uuid(),gen_random_uuid(),n,'UPSERT' from generate_series(1,?) n",
                company,
                count,
            )
        return company
    }

    private fun timer(threads: MutableList<Thread>) =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadFactory { task ->
                Thread(task, "sync-worker-test-${UUID.randomUUID()}").also { threads += it }
            }
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(3)
            initialize()
        }

    private fun useCase(adapter: SyncDataSource) =
        MaintainMobileSync(StoredSyncRepository(adapter), transactions, Clock.systemUTC())

    @Test
    fun stoppingDuringPublicationRollsBackTheBatchAndReleasesItsTimerAndDatabaseGuards() {
        val company = fixture(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val calls = AtomicInteger()
        val probe =
            object : SyncDataSource by source {
                override fun publish(limit: Int): Int {
                    calls.incrementAndGet()
                    val published = source.publish(limit)
                    entered.countDown()
                    try {
                        check(release.await(5, TimeUnit.SECONDS))
                        return published
                    } finally {
                        exited.countDown()
                    }
                }
            }
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        val worker = MobileSyncWorker(useCase(probe), timer, Duration.ofMillis(20))
        val callback = CountDownLatch(1)
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.stop(Runnable { callback.countDown() })
            assertTrue(callback.await(3, TimeUnit.SECONDS))
            assertTrue(exited.await(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            threads.forEach { it.join(1000) }
            assertTrue(threads.none { it.isAlive })
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertFalse(worker.isRunning)
            assertEquals(1, calls.get())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select head_position from mobile_sync_heads where company_id=?",
                        Long::class.java,
                        company,
                    ),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from mobile_sync_changes where company_id=? and sequence is null",
                        Int::class.java,
                        company,
                    ),
            )
            val recovered = maintain.execute()
            assertTrue(recovered is Result.Success, recovered.toString())
            assertEquals(1, (recovered as Result.Success).value.published)
            assertThrows(IllegalStateException::class.java) { worker.start() }
        } finally {
            release.countDown()
            worker.stop()
        }
    }

    @Test
    fun eachPassHasAFiniteBatchAndTheWorkerDoesNotQueueATaskPerChange() {
        val company = fixture(201)
        val second = CountDownLatch(1)
        val release = CountDownLatch(1)
        val committed = CountDownLatch(1)
        val calls = AtomicInteger()
        val probe =
            object : SyncDataSource by source {
                override fun publish(limit: Int): Int {
                    assertEquals(200, limit)
                    val result = source.publish(limit)
                    if (calls.incrementAndGet() == 2) {
                        second.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        TransactionSynchronizationManager.registerSynchronization(
                            object : TransactionSynchronization {
                                override fun afterCommit() {
                                    committed.countDown()
                                }
                            }
                        )
                    }
                    return result
                }
            }
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        val worker = MobileSyncWorker(useCase(probe), timer, Duration.ofMillis(20))
        try {
            worker.start()
            assertTrue(second.await(5, TimeUnit.SECONDS))
            assertEquals(
                200,
                database()
                    .queryForObject(
                        "select head_position from mobile_sync_heads where company_id=?",
                        Long::class.java,
                        company,
                    ),
            )
            assertEquals(1, threads.size)
            assertTrue(timer.scheduledThreadPoolExecutor.queue.size <= 1)
            release.countDown()
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertEquals(
                201,
                database()
                    .queryForObject(
                        "select head_position from mobile_sync_heads where company_id=?",
                        Long::class.java,
                        company,
                    ),
            )
        } finally {
            release.countDown()
            worker.stop()
        }
    }
}
