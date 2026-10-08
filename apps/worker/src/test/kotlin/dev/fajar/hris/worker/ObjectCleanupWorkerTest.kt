package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import dev.fajar.hris.storage.data.repositories.PrivateObjectStorageRepository
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import dev.fajar.hris.worker.storage.ObjectCleanupWorker
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
class ObjectCleanupWorkerTest {
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

    @Autowired private lateinit var queue: ObjectCleanupRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val ids = mutableListOf<UUID>()

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun fixture(): UUID {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val id = UUID.randomUUID()
        ids += id
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "G${company.toString().take(8)}",
                "Cleanup fixture",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Former account')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into object_cleanup_queue(id,company_id,resource_id,object_key,object_bytes,created_by,eligible_at) values(?,?,?,?,128,?,clock_timestamp()-interval '1 second')",
                id,
                company,
                UUID.randomUUID(),
                "$company/$id",
                account,
            )
        return id
    }

    @AfterEach
    fun removeFixtureEntries() {
        ids.forEach { database().update("delete from object_cleanup_queue where id=?", it) }
    }

    private class Probe(val block: Boolean = false) : ObjectStorageDataSource {
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()

        override fun delete(key: String) {
            check(!TransactionSynchronizationManager.isActualTransactionActive())
            calls.incrementAndGet()
            entered.countDown()
            try {
                if (block) check(release.await(5, TimeUnit.SECONDS))
            } finally {
                exited.countDown()
            }
        }

        override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun metadata(key: String): ObjectMetadataData =
            throw UnsupportedOperationException()

        override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray =
            throw UnsupportedOperationException()

        override fun close() {}
    }

    private fun timer(threads: MutableList<Thread>): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadFactory { task ->
                Thread(task, "hris-object-cleanup-test-${UUID.randomUUID()}").also { threads += it }
            }
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(3)
            initialize()
        }

    @Test
    fun stoppingPendingIoLeavesAFencedLeaseAndReleasesThePollingThread() {
        val id = fixture()
        val probe = Probe(true)
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        val collect =
            CollectObjectGarbage(
                queue,
                PrivateObjectStorageRepository(probe),
                journal,
                transactions,
                Clock.systemUTC(),
            )
        val worker = ObjectCleanupWorker(collect, timer, Duration.ofMillis(20))
        val callback = CountDownLatch(1)
        try {
            worker.start()
            assertTrue(probe.entered.await(5, TimeUnit.SECONDS))
            worker.stop(Runnable { callback.countDown() })
            assertTrue(callback.await(3, TimeUnit.SECONDS))
            assertTrue(probe.exited.await(3, TimeUnit.SECONDS))
            assertFalse(worker.isRunning)
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            threads.forEach { it.join(1000) }
            assertTrue(threads.none { it.isAlive })
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertEquals(1, probe.calls.get())
            assertEquals(
                "RUNNING",
                database()
                    .queryForObject(
                        "select status from object_cleanup_queue where id=?",
                        String::class.java,
                        id,
                    ),
            )
            database()
                .update(
                    "update object_cleanup_queue set lease_until=clock_timestamp()-interval '1 second',version=version+1 where id=?",
                    id,
                )
            val replacement = Probe()
            assertEquals(
                Result.Success(1),
                CollectObjectGarbage(
                        queue,
                        PrivateObjectStorageRepository(replacement),
                        journal,
                        transactions,
                        Clock.systemUTC(),
                    )
                    .execute(UUID.randomUUID(), 1),
            )
            assertEquals(1, replacement.calls.get())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where id=?",
                        Int::class.java,
                        id,
                    ),
            )
        } finally {
            probe.release.countDown()
            worker.stop()
        }
    }

    @Test
    fun expiryCleanupDoesNotRequireTheFormerUploaderToKeepAccountAccess() {
        val id = fixture()
        database()
            .update(
                "update accounts set active=false where id=(select created_by from object_cleanup_queue where id=?)",
                id,
            )
        val probe = Probe()
        val collect =
            CollectObjectGarbage(
                queue,
                PrivateObjectStorageRepository(probe),
                journal,
                transactions,
                Clock.systemUTC(),
            )
        assertEquals(Result.Success(1), collect.execute(UUID.randomUUID(), 1))
        assertEquals(1, probe.calls.get())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='storage.object_deleted'",
                    Int::class.java,
                    id,
                ),
        )
    }
}
