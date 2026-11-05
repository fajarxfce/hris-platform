package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSummaryProjectionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryProjectionRepository
import dev.fajar.hris.collaboration.domain.usecases.RefreshCollaborationSummaries
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.worker.collaboration.CollaborationSummaryWorker
import java.time.*
import java.util.UUID
import java.util.concurrent.*
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
class CollaborationSummaryWorkerTest {
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

    @Autowired private lateinit var source: CollaborationSummaryProjectionDataSource
    @Autowired private lateinit var projections: CollaborationSummaryProjectionRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var bound: RefreshCollaborationSummaries
    private val now = Instant.parse("2026-11-05T00:00:00Z")

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun useCase(adapter: CollaborationSummaryProjectionDataSource = source) =
        RefreshCollaborationSummaries(
            StoredCollaborationSummaryProjectionRepository(adapter),
            transactions,
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private data class Fixture(
        val company: UUID,
        val conversation: UUID,
        val first: UUID,
        val second: UUID,
    )

    private fun company(): UUID =
        UUID.randomUUID().also {
            database()
                .update(
                    "insert into companies(id,code,name,timezone) values(?,?,'Summary fixture','UTC')",
                    it,
                    it.toString().take(20),
                )
        }

    private fun account(): UUID =
        UUID.randomUUID().also {
            database()
                .update(
                    "insert into accounts(id,email,display_name) values(?,?,'Participant')",
                    it,
                    "$it@example.test",
                )
        }

    private fun fixture(company: UUID = company(), first: UUID = account()): Fixture {
        val second = account()
        val id = UUID.randomUUID()
        val ordered = listOf(first, second).sortedBy { it.toString() }
        database()
            .update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                company,
                id,
                ordered[0],
                ordered[1],
                first,
            )
        return Fixture(company, id, first, second)
    }

    private fun records(f: Fixture): List<CollaborationSummaryRecord> =
        success(
            transactions.run(Actor(UUID(0, 0), f.company, emptySet(), now, UUID.randomUUID())) {
                projections.records(f.company, listOf(f.conversation))
            }
        )

    private fun changes(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from mobile_sync_changes where company_id=? and collection='COLLABORATION_DIRECT' and resource_id=?",
                Int::class.java,
                f.company,
                f.conversation,
            )!!

    private fun rename(account: UUID, name: String) {
        database().update("update accounts set display_name=? where id=?", name, account)
    }

    @BeforeEach
    fun clearPriorWork() {
        database().update("update collaboration_summary_refresh_queue set due_at=null")
        database()
            .update(
                "update collaboration_summary_account_queue set due_at=null,after_company_id=null,after_conversation_id=null"
            )
    }

    @Test
    fun initialPairsAndRenamesPublishOnlyChangedOwnerRecords() {
        val f = fixture()
        assertEquals(CollaborationSummaryRefresh(0, 1, 2), success(bound.execute()))
        val initial = records(f)
        assertEquals(2, initial.size)
        assertTrue(initial.all { it.version == 0L && it.peerDisplayName == "Participant" })
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from collaboration_direct_conversations where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    f.conversation,
                ),
        )
        assertEquals(CollaborationSummaryRefresh(0, 0, 0), success(useCase().execute()))
        database()
            .update(
                "update collaboration_summary_refresh_queue set due_at='-infinity' where company_id=?",
                f.company,
            )
        assertEquals(CollaborationSummaryRefresh(0, 1, 0), success(useCase().execute()))
        assertEquals(initial, records(f))
        assertEquals(2, changes(f))
        rename(f.first, "Renamed")
        assertEquals(CollaborationSummaryRefresh(1, 1, 1), success(useCase().execute()))
        assertEquals(
            initial.single { it.ownerAccountId == f.first },
            records(f).single { it.ownerAccountId == f.first },
        )
        assertEquals("Renamed", records(f).single { it.ownerAccountId == f.second }.peerDisplayName)
        assertEquals(1L, records(f).single { it.ownerAccountId == f.second }.version)
        assertEquals(3, changes(f))
    }

    @Test
    fun boundedCrossCompanyFanoutUsesBatchSourceAndRecordReads() {
        val first = account()
        val companies = List(5) { company() }
        val fixtures = List(205) { fixture(companies[it % 5], first) }
        var reads = 0
        var recordReads = 0
        val adapter =
            object : CollaborationSummaryProjectionDataSource by source {
                override fun candidates(
                    company: UUID,
                    ids: List<UUID>,
                ): List<CollaborationSummaryCandidateRow> {
                    assertTrue(ids.size in 1..200)
                    reads++
                    return source.candidates(company, ids)
                }

                override fun records(
                    company: UUID,
                    ids: List<UUID>,
                ): List<
                    dev.fajar.hris.schema.tables.records.CollaborationDirectSummaryRecordsRecord
                > {
                    recordReads++
                    return source.records(company, ids)
                }
            }
        repeat(3) {
            val before = reads
            val pass = success(useCase(adapter).execute())
            assertTrue(pass.projectedPairs <= 200)
            assertTrue(reads - before <= 4)
        }
        assertEquals(5, reads)
        assertEquals(reads, recordReads)
        rename(first, "New public name")
        val firstPass = success(useCase(adapter).execute())
        assertEquals(200, firstPass.expandedTargets)
        assertNotNull(
            database()
                .queryForObject(
                    "select after_conversation_id from collaboration_summary_account_queue where account_id=?",
                    UUID::class.java,
                    first,
                )
        )
        val secondPass = success(useCase(adapter).execute())
        assertEquals(5, secondPass.expandedTargets)
        repeat(2) { success(useCase(adapter).execute()) }
        assertEquals(reads, recordReads)
        for (f in fixtures) {
            assertEquals(
                "New public name",
                records(f).single { it.ownerAccountId == f.second }.peerDisplayName,
            )
            assertEquals(3, changes(f))
        }
        assertEquals(CollaborationSummaryRefresh(0, 0, 0), success(useCase().execute()))
    }

    @Test
    fun aFalseCompletionRollsBackAllRecordsAndRawReferences() {
        val f = fixture()
        val adapter =
            object : CollaborationSummaryProjectionDataSource by source {
                override fun complete(company: UUID, ids: List<UUID>): Boolean {
                    source.complete(company, ids)
                    return false
                }
            }
        val result = useCase(adapter).execute() as Result.Failed
        assertEquals("collaboration_summary_projection_conflict", result.failure.code)
        assertTrue(records(f).isEmpty())
        assertEquals(0, changes(f))
        assertEquals(CollaborationSummaryRefresh(0, 1, 2), success(useCase().execute()))
    }

    @Test
    fun simultaneousWorkersSkipLockedPairsAndLateNameCaptureIsNotLost() {
        val f = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : CollaborationSummaryProjectionDataSource by source {
                override fun candidates(
                    company: UUID,
                    ids: List<UUID>,
                ): List<CollaborationSummaryCandidateRow> {
                    val rows = source.candidates(company, ids)
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return rows
                }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            val running =
                pool.submit<Result<CollaborationSummaryRefresh>> { useCase(adapter).execute() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(CollaborationSummaryRefresh(0, 0, 0), success(useCase().execute()))
                rename(f.first, "Captured after source read")
                release.countDown()
                assertEquals(2, success(running.get(5, TimeUnit.SECONDS)).changedRecords)
                assertEquals(CollaborationSummaryRefresh(1, 1, 1), success(useCase().execute()))
                assertEquals(
                    "Captured after source read",
                    records(f).single { it.ownerAccountId == f.second }.peerDisplayName,
                )
                assertEquals(3, changes(f))
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun stoppingTheOwnerCancelsTheTimerAndRollsBackItsPendingTransaction() {
        val f = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : CollaborationSummaryProjectionDataSource by source {
                override fun complete(company: UUID, ids: List<UUID>): Boolean {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return source.complete(company, ids)
                }
            }
        val timer =
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setRemoveOnCancelPolicy(true)
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(3)
                initialize()
            }
        val worker = CollaborationSummaryWorker(useCase(adapter), timer, Duration.ofMillis(20))
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertFalse(worker.isRunning)
            assertTrue(records(f).isEmpty())
            assertEquals(0, changes(f))
            assertEquals(2, success(useCase().execute()).changedRecords)
            assertThrows(IllegalStateException::class.java) { worker.start() }
        } finally {
            release.countDown()
            worker.stop()
        }
    }

    @Test
    fun theAccountMutationGuardDoesNotBlockInitialForeignKeyReferences() {
        val f = fixture()
        // Match Identity/CredentialStore's NO KEY UPDATE guard. Projection must not
        // take a source SHARE/UPDATE lock after holding its refresh queue rows.
        DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .connection
            .use { connection ->
                connection.autoCommit = false
                connection
                    .prepareStatement(
                        "select id from accounts where id in (?,?) order by id for no key update"
                    )
                    .use { statement ->
                        statement.setObject(1, f.first)
                        statement.setObject(2, f.second)
                        statement.executeQuery().use { rows ->
                            while (rows.next()) {
                                /* retain both guards */
                            }
                        }
                    }
                Executors.newSingleThreadExecutor().use { pool ->
                    val running =
                        pool.submit<Result<CollaborationSummaryRefresh>> { useCase().execute() }
                    try {
                        assertEquals(2, success(running.get(5, TimeUnit.SECONDS)).changedRecords)
                        assertEquals(2, records(f).size)
                    } finally {
                        connection.rollback()
                        running.cancel(true)
                    }
                }
            }
    }
}
