package dev.fajar.hris.worker

import dev.fajar.hris.administration.data.datasources.WorkerIntegrationDataSource
import dev.fajar.hris.administration.data.dto.WorkerIntegrationWriteRow
import dev.fajar.hris.administration.data.repositories.ConfiguredIntegrationRepository
import dev.fajar.hris.administration.data.repositories.StoredWorkerIntegrationRepository
import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
import dev.fajar.hris.administration.domain.usecases.PublishWorkerIntegrationObservation
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.worker.integrations.WorkerIntegrationReporter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
class WorkerIntegrationObservationTest {
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

    @Autowired private lateinit var source: WorkerIntegrationDataSource
    @Autowired private lateinit var registry: WorkerIntegrationRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val clock = Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val worker = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun count() =
        database()
            .queryForObject("SELECT count(*) FROM worker_integration_observations", Int::class.java)

    private fun publish(
        adapter: WorkerIntegrationDataSource = source,
        flags: Set<IntegrationKind> = setOf(IntegrationKind.STORAGE),
    ) =
        PublishWorkerIntegrationObservation(
            ConfiguredIntegrationRepository(flags),
            StoredWorkerIntegrationRepository(adapter),
            transactions,
            clock,
        )

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @BeforeEach
    fun emptyRegistry() {
        database().update("DELETE FROM worker_integration_observations")
    }

    private fun seed(count: Int, expired: Boolean = false) {
        val age = if (expired) "interval '6 minutes'" else "interval '0 seconds'"
        database()
            .update(
                """
            INSERT INTO worker_integration_observations(instance_id,first_seen_at,last_seen_at,mail_enabled,push_enabled,storage_enabled,scanner_enabled)
            SELECT gen_random_uuid(),now()-interval '10 minutes',now()-$age,false,false,true,false FROM generate_series(1,?)
        """
                    .trimIndent(),
                count,
            )
    }

    @Test
    fun workerRolePublishesDatabaseTimeAndCannotRewriteItsStartupFlags() {
        val id = UUID.randomUUID()
        success(publish().execute(id))
        val first = success(transactions.run(worker) { registry.read() })
        assertEquals(1, first.workers.size)
        assertTrue(first.observedAt.isAfter(clock.instant().plusSeconds(86400)))
        assertTrue(first.workers.single().flags.storage)
        success(publish().execute(id))
        val later = success(transactions.run(worker) { registry.read() })
        assertEquals(first.workers.single().firstSeenAt, later.workers.single().firstSeenAt)
        assertFalse(later.workers.single().lastSeenAt.isBefore(first.workers.single().lastSeenAt))
        assertEquals(
            "worker_configuration_changed",
            (publish(flags = setOf(IntegrationKind.PUSH)).execute(id) as Result.Failed).failure.code,
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "UPDATE worker_integration_observations SET push_enabled=true WHERE instance_id=?",
                    id,
                )
        }
        assertEquals(1, count())
    }

    @Test
    fun concurrentNewProcessesCannotExceedTheRegistryCapacity() {
        seed(63)
        val entered = CountDownLatch(2)
        val gated =
            object : WorkerIntegrationDataSource by source {
                override fun lockRegistry() {
                    entered.countDown()
                    check(entered.await(5, TimeUnit.SECONDS))
                    source.lockRegistry()
                }
            }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts =
                (1..2).map {
                    pool.submit<Result<Unit>> { publish(gated).execute(UUID.randomUUID()) }
                }
            val results = attempts.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(
                listOf("worker_observation_capacity"),
                results.filterIsInstance<Result.Failed>().map { it.failure.code },
            )
            assertEquals(64, count())
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun expiredReportsAreReclaimedUsingDatabaseTimeAndFailureRollsBackTheirRemoval() {
        seed(64, expired = true)
        val rejected =
            object : WorkerIntegrationDataSource by source {
                override fun publish(row: WorkerIntegrationWriteRow) = 0
            }
        assertEquals(
            "worker_configuration_changed",
            (publish(rejected).execute(UUID.randomUUID()) as Result.Failed).failure.code,
        )
        assertEquals(64, count())
        success(publish().execute(UUID.randomUUID()))
        assertEquals(1, count())
    }

    @Test
    fun stoppingTheReporterInterruptsPublicationAndJoinsItsTimerWithoutLateWrites() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocked =
            object : WorkerIntegrationDataSource by source {
                override fun publish(row: WorkerIntegrationWriteRow): Int {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return source.publish(row)
                }
            }
        val timer =
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setRemoveOnCancelPolicy(true)
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(3)
                initialize()
            }
        val reporter = WorkerIntegrationReporter(publish(blocked), timer, Duration.ofMillis(10))
        try {
            reporter.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            reporter.stop()
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertFalse(reporter.isRunning)
            assertEquals(0, count())
            assertThrows(IllegalStateException::class.java) { reporter.start() }
            success(publish().execute(UUID.randomUUID()))
            assertEquals(1, count())
        } finally {
            release.countDown()
            reporter.stop()
        }
    }
}
