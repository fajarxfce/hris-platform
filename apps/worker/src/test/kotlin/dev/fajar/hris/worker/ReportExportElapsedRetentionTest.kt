package dev.fajar.hris.worker

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.reporting.domain.usecases.MaintainReportExports
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.*
import dev.fajar.hris.storage.domain.usecases.CollectObjectGarbage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

/** Opt-in real elapsed-hour acceptance; no clock or immutable deadline is rewritten. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "HRIS_RUN_ELAPSED_RETENTION", matches = "true")
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class ReportExportElapsedRetentionTest {
    class RetentionGarage : GenericContainer<RetentionGarage>("dxflrs/garage:v2.4.1")

    companion object {
        private const val access = "GK0123456789abcdef0123456789abcdef"
        private const val secret =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private const val bucket = "elapsed-retention"
        private val config =
            """
            metadata_dir = "/tmp/meta"
            data_dir = "/tmp/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "$secret"
            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            root_domain = ".s3.garage.localhost"
            [admin]
            api_bind_addr = "[::]:3903"
            admin_token = "test-fixture-only"
        """
                .trimIndent()

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")
        @Container
        @JvmStatic
        val garage =
            RetentionGarage()
                .withCopyToContainer(Transferable.of(config), "/etc/garage.toml")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", access)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", secret)
                .withEnv("GARAGE_DEFAULT_BUCKET", bucket)
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withExposedPorts(3900, 3903)
                .waitingFor(Wait.forHttp("/health").forPort(3903))
                .withStartupTimeout(Duration.ofSeconds(60))
                .withCreateContainerCmdModifier {
                    it.hostConfig!!.withMemory(512L * 1024 * 1024).withPidsLimit(128)
                }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("HRIS_STORAGE_ENABLED") { true }
            registry.add("HRIS_STORAGE_ENDPOINT") {
                "http://${garage.host}:${garage.getMappedPort(3900)}"
            }
            registry.add("HRIS_STORAGE_REGION") { "garage" }
            registry.add("HRIS_STORAGE_BUCKET") { bucket }
            registry.add("HRIS_STORAGE_ACCESS_KEY") { access }
            registry.add("HRIS_STORAGE_SECRET_KEY") { secret }
            registry.add("HRIS_STORAGE_ALLOW_PLAINTEXT") { true }
        }
    }

    @Autowired private lateinit var exports: ReportExportRepository
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var expiry: ReportExportExpiryRepository
    @Autowired private lateinit var queue: ObjectCleanupRepository
    @Autowired private lateinit var storage: ObjectStorageRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var json: ObjectMapper
    private val clock = Clock.systemUTC()
    private val started = System.nanoTime()
    private val evidence = Path.of("build/reports/elapsed-retention.json")

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, "$result")
        return (result as Result.Success).value
    }

    private fun digest(bytes: ByteArray) =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private data class Fixture(
        val actor: Actor,
        val request: ReportExportRequest,
        val snapshot: ReportSnapshot,
        val keys: List<String>,
        val bytes: ByteArray,
        val ready: ReportExport,
    )

    private fun <T> run(actor: Actor, block: () -> Result<T>) =
        transactions.run(actor) {
            exports.lockOwner(actor.companyId!!, actor.accountId).flatMap { block() }
        }

    private fun find(f: Fixture) =
        value(
            run(f.actor) { exports.find(f.request.companyId, f.request.accountId, f.request.id) }
        )!!

    private fun maintain(time: Clock = clock) =
        MaintainReportExports(expiry, exports, snapshots, jobs, journal, transactions, time)

    private fun collect(provider: ObjectStorageRepository = storage) =
        CollectObjectGarbage(queue, provider, journal, transactions, clock)

    private fun retained(f: Fixture) =
        value(run(f.actor) { exports.quota(f.request.companyId, f.request.accountId) }).retained

    private fun rowCount(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from report_snapshot_rows where snapshot_id=?",
                Int::class.java,
                f.request.id,
            )!!

    private fun fixture(lifetimeSeconds: Long, abandonedAttempt: Boolean): Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val id = UUID.randomUUID()
        val job = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Elapsed retention','UTC')",
                company,
                "R${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Retention fixture owner')",
                account,
                "$account@example.invalid",
            )
        val at = now()
        val actor = Actor(account, company, emptySet(), at, UUID.randomUUID())
        val request =
            value(
                validateReportExportRequest(
                    ReportExportRequest(
                        id,
                        company,
                        account,
                        ReportSelection(
                            ReportKind.HEADCOUNT,
                            "headcount.v1",
                            ReportFilterParameters(asOf = "2026-10-01"),
                        ),
                        ReportExportDataset.SUMMARY,
                        mapOf(company to setOf("reports.read", "people.read")),
                        ClientRequest(null, false),
                        at,
                        at.plusSeconds(lifetimeSeconds),
                    )
                )
            )
        database()
            .update(
                """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,total_items)
            values(?,?,?,'REPORT_EXPORT',?,?::jsonb,?::timestamptz,0,?,4)""",
                job,
                company,
                account,
                UUID.randomUUID(),
                json.writeValueAsString(mapOf("exportId" to id.toString())),
                at.toString(),
                UUID.randomUUID(),
            )
        assertTrue(value(run(actor) { exports.create(request, job) }))
        val lease =
            value(jobs.claim(UUID.randomUUID(), 1, 120, setOf(JobKind.REPORT_EXPORT))).single()
        assertEquals(job, lease.job.request.id)
        val capture =
            ReportSnapshotCapture(
                id,
                company,
                account,
                request.selection,
                request.dataset,
                value(reportExportColumns(request.selection.kind, request.dataset)),
                now(),
                request.expiresAt,
            )
        val counts = HeadcountCounts(1, 1, 1, 0, 0, 1, 0)
        val rows =
            value(
                reportSummaryRows(
                    capture,
                    GroupHeadcountCounts(counts, listOf(CompanyHeadcount(company, counts))),
                )
            )
        val snapshot =
            value(
                run(actor) {
                    snapshots
                        .begin(capture)
                        .flatMap { snapshots.append(capture, 0, rows) }
                        .flatMap { snapshots.seal(capture) }
                        .flatMap { saved ->
                            exports
                                .find(company, account, id, true)
                                .flatMap { exports.captured(it!!, now()) }
                                .map {
                                    assertTrue(it)
                                    saved
                                }
                        }
                }
            )
        val csv = value(encodeReportCsvPart(capture.columns, rows, true))
        val bytes = csv.bytes()
        val keys = mutableListOf<String>()
        val attempts = if (abandonedAttempt) 2 else 1
        repeat(attempts) { index ->
            val current = value(run(actor) { exports.find(company, account, id) })!!
            val upload =
                ReportExportUpload(
                    UUID.randomUUID(),
                    company,
                    account,
                    id,
                    1,
                    0,
                    rows.size,
                    0,
                    bytes.size,
                    digest(bytes),
                    now(),
                )
            val key = reportExportObjectKey(upload)
            value(
                run(actor) {
                    queue
                        .schedule(
                            ObjectCleanupRequest(
                                upload.id,
                                company,
                                id,
                                key,
                                bytes.size.toLong(),
                                account,
                                request.expiresAt.plus(REPORT_EXPORT_DELETE_GRACE),
                            )
                        )
                        .flatMap { exports.reserve(current, upload) }
                        .map { assertTrue(it) }
                }
            )
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            val stored = value(storage.put(key, bytes))
            keys += key
            assertArrayEquals(bytes, value(storage.read(key, 0, bytes.size, stored.etag)))
            if (index == attempts - 1) {
                value(
                    run(actor) {
                        exports
                            .find(company, account, id, true)
                            .flatMap {
                                exports.accept(it!!, ReportExportPart(upload, stored.etag), now())
                            }
                            .map { assertTrue(it) }
                    }
                )
            }
        }
        value(
            run(actor) {
                exports.find(company, account, id, true).flatMap { current ->
                    exports.parts(company, account, id).flatMap { parts ->
                        val manifest = value(reportExportManifest(snapshot, parts))
                        exports.publish(current!!, manifest.tag, now()).flatMap { published ->
                            assertTrue(published)
                            jobs.checkpoint(lease, JobProgress(4, emptyMap())).flatMap {
                                jobs.complete(lease, JobStatus.SUCCEEDED)
                            }
                        }
                    }
                }
            }
        )
        val ready = value(run(actor) { exports.find(company, account, id) })!!
        assertEquals(ReportExportStatus.READY, ready.status)
        assertEquals(attempts, ready.reservationCount)
        assertEquals(1, ready.partCount)
        // Revocation cannot prevent storage/retention cleanup; no grant is restored later.
        database().update("update accounts set active=false where id=?", account)
        return Fixture(actor, request, snapshot, keys.toList(), bytes, ready)
    }

    private fun record(phase: String, f: Fixture, extra: Map<String, Any> = emptyMap()) {
        Files.createDirectories(evidence.parent)
        val data =
            mapOf(
                "phase" to phase,
                "observedAt" to now().toString(),
                "expiresAt" to f.request.expiresAt.toString(),
                "eligibleAt" to f.request.expiresAt.plus(REPORT_EXPORT_DELETE_GRACE).toString(),
                "elapsedMillis" to (System.nanoTime() - started) / 1_000_000,
            ) + extra
        val temporary = evidence.resolveSibling("elapsed-retention.tmp")
        Files.writeString(temporary, json.writeValueAsString(data))
        Files.move(
            temporary,
            evidence,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun awaitUntil(target: Instant, f: Fixture, phase: String, inspect: () -> Unit = {}) {
        var nextInspection = Instant.MIN
        while (clock.instant() < target) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (clock.instant() >= nextInspection) {
                inspect()
                record(phase, f)
                nextInspection = clock.instant().plusSeconds(60)
            }
            Thread.sleep(Duration.between(clock.instant(), target).toMillis().coerceIn(1, 20_000))
        }
    }

    @Test
    @Timeout(value = 75, unit = TimeUnit.MINUTES)
    fun elapsedExpiryPreservesGraceAndQuotaUntilRealObjectsAndTheirLostDeletionReceiptAreRetired() {
        val expired = fixture(180, abandonedAttempt = true)
        val live = fixture(10_800, abandonedAttempt = false)
        assertEquals(
            ReportExportCleanupResult(0, 0, 0),
            value(maintain(Clock.offset(clock, Duration.ofDays(1))).execute()),
        )
        assertEquals(0, value(collect().execute(UUID.randomUUID(), 4)))
        assertEquals(2, rowCount(expired))
        assertEquals(1, retained(expired))
        awaitUntil(expired.request.expiresAt.plusSeconds(1), expired, "awaiting_expiry")
        assertEquals(ReportExportCleanupResult(1, 2, 0), value(maintain().execute()))
        assertEquals(0, rowCount(expired))
        assertEquals(2, rowCount(live))
        assertNull(find(expired).retiredAt)
        val graceEnd = expired.request.expiresAt.plus(REPORT_EXPORT_DELETE_GRACE)
        awaitUntil(graceEnd.plusSeconds(1), expired, "awaiting_physical_grace") {
            assertEquals(0, value(collect().execute(UUID.randomUUID(), 4)))
            for (key in expired.keys + live.keys) assertNotNull(value(storage.metadata(key)))
            assertEquals(1, retained(expired))
            assertEquals(expired.request, find(expired).request)
        }
        assertTrue(
            Duration.between(expired.request.expiresAt, clock.instant()) >=
                REPORT_EXPORT_DELETE_GRACE
        )
        var dropped = false
        val loseReceipt =
            object : ObjectStorageRepository by storage {
                override fun delete(key: String): Result<Unit> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
                    val deleted = storage.delete(key)
                    if (!dropped && deleted is Result.Success && key in expired.keys) {
                        dropped = true
                        return Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "fixture_delete_response_lost")
                        )
                    }
                    return deleted
                }
            }
        assertEquals(1, value(collect(loseReceipt).execute(UUID.randomUUID(), 4)))
        assertTrue(dropped)
        for (key in expired.keys) assertNull(value(storage.metadata(key)))
        assertEquals(1, retained(expired))
        assertEquals(ReportExportCleanupResult(1, 0, 0), value(maintain().execute()))
        assertNull(find(expired).retiredAt)
        record("delete_receipt_pending", expired)
        awaitUntil(now().plusSeconds(6), expired, "awaiting_delete_retry")
        assertEquals(1, value(collect().execute(UUID.randomUUID(), 4)))
        // A pending object postponed the original queue; wait for that actual schedule as well.
        awaitUntil(now().plusSeconds(61), expired, "awaiting_retirement_schedule")
        assertEquals(ReportExportCleanupResult(1, 0, 1), value(maintain().execute()))
        val retired = find(expired)
        assertNotNull(retired.retiredAt)
        assertEquals(expired.ready.request, retired.request)
        assertEquals(expired.ready.manifestTag, retired.manifestTag)
        assertEquals(expired.ready.reservationCount, retired.reservationCount)
        assertEquals(0, retained(expired))
        assertEquals(
            expired.snapshot,
            value(
                run(expired.actor) {
                    snapshots.find(
                        expired.request.companyId,
                        expired.request.accountId,
                        expired.request.id,
                    )
                }
            ),
        )
        assertEquals(live.ready, find(live))
        assertEquals(1, retained(live))
        assertEquals(2, rowCount(live))
        for (key in live.keys) {
            val meta = value(storage.metadata(key))!!
            assertArrayEquals(live.bytes, value(storage.read(key, 0, live.bytes.size, meta.etag)))
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from report_export_expiry_queue where export_id=?",
                    Int::class.java,
                    expired.request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='reporting.export_retired'",
                    Int::class.java,
                    expired.request.id,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from accounts where id in (?,?) and active=false",
                    Int::class.java,
                    expired.request.accountId,
                    live.request.accountId,
                ),
        )
        assertEquals(ReportExportCleanupResult(0, 0, 0), value(maintain().execute()))
        record(
            "passed",
            expired,
            mapOf(
                "expiredObjects" to 2,
                "liveObjects" to 1,
                "deletedSnapshotRows" to 2,
                "retainedLiveRows" to 2,
                "lostDeletionReceiptRecovered" to true,
                "retiredQuota" to 0,
                "liveQuota" to 1,
                "retirementAudits" to 1,
            ),
        )
    }
}
