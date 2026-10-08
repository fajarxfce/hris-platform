package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.models.DocumentScanData
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.models.ObjectMetadataData
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.tasks.DocumentInventoryTask
import dev.fajar.hris.worker.tasks.DocumentValidationTask
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Import(DocumentWorkerProbeConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class DocumentValidationWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

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
        }
    }

    @Autowired private lateinit var start: StartDocumentUpload
    @Autowired private lateinit var upload: UploadDocumentChunk
    @Autowired private lateinit var validate: StartDocumentValidation
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var task: DocumentValidationTask
    @Autowired private lateinit var batch: BatchJobExecutor
    @Autowired private lateinit var probe: DocumentWorkerScanProbe
    @Autowired private lateinit var inventoryStart: StartDocumentInventory
    @Autowired private lateinit var inventoryTask: DocumentInventoryTask
    @Autowired private lateinit var inventoryProbe: DocumentInventoryWorkerProbe

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun fixture(): Pair<Actor, UUID> {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val person = UUID.randomUUID()
        val employee = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Document worker fixture','UTC')",
                company,
                "D${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Document operator')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        val permissions =
            setOf(
                "company.read",
                "documents.read",
                "documents.manage",
                "documents.inventory",
                "jobs.retry",
                "people.profile.read",
                "people.profile.manage",
            )
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        database()
            .update(
                "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Document employee','ID')",
                person,
                company,
            )
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'E001')",
                company,
                employee,
                person,
            )
        val actor =
            Actor(
                account,
                company,
                permissions,
                Instant.now(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val bytes = "%PDF-1.7\n%%EOF".toByteArray()
        val hash =
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        val id = UUID.randomUUID()
        val created =
            start.execute(
                actor,
                UUID.randomUUID(),
                StartDocumentUploadCommand(
                    UUID.randomUUID(),
                    id,
                    employee,
                    "Employment evidence",
                    DocumentClassification.PERSONAL,
                    0,
                    "evidence.pdf",
                    "application/pdf",
                    bytes.size.toLong(),
                    hash,
                    "Worker fixture",
                ),
            )
        assertTrue(created is Result.Success, created.toString())
        assertTrue(upload.execute(actor, UUID.randomUUID(), id, 0, hash, bytes) is Result.Success)
        assertTrue(
            validate.execute(actor, UUID.randomUUID(), id, 1, "Inspection requested")
                is Result.Success
        )
        return actor to id
    }

    private fun claim(kind: JobKind = JobKind.DOCUMENT_VALIDATE): JobLease {
        val claimed = lease.execute(UUID.randomUUID(), 1, 120, setOf(kind))
        assertTrue(claimed is Result.Success, claimed.toString())
        return (claimed as Result.Success).value.single()
    }

    @AfterEach
    fun settleJobs() {
        probe.beforeFinish = null
        inventoryProbe.beforeList = null
        database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where kind in ('DOCUMENT_VALIDATE','DOCUMENT_INVENTORY') and status in ('QUEUED','RUNNING')"
            )
    }

    @Test
    fun realBatchTaskScansOutsideTransactionsAndPublishesWithRestrictedCredentials() {
        val (_, id) = fixture()
        val owned = claim()
        assertEquals(Result.Success(Unit), batch.execute(owned, task))
        assertEquals(
            "READY",
            database()
                .queryForObject(
                    "select status from document_revisions where id=?",
                    String::class.java,
                    id,
                ),
        )
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    owned.job.request.id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where resource_id=?",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun interruptedInspectionClosesTheSessionAndANewLeaseCanRecover() {
        val (_, id) = fixture()
        val old = claim()
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val before = probe.closed.get()
        probe.beforeFinish = {
            entered.countDown()
            try {
                check(CountDownLatch(1).await(10, TimeUnit.SECONDS))
            } finally {
                closed.countDown()
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<Unit>> { batch.execute(old, task) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
            } finally {
                pending.cancel(true)
            }
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
        assertEquals(before + 1, probe.closed.get())
        assertEquals(
            "VALIDATING",
            database()
                .queryForObject(
                    "select status from document_revisions where id=?",
                    String::class.java,
                    id,
                ),
        )
        probe.beforeFinish = null
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                old.job.request.id,
            )
        val resumed = claim()
        assertNotEquals(old.token, resumed.token)
        assertEquals(Result.Success(Unit), batch.execute(resumed, task))
        assertEquals(
            "READY",
            database()
                .queryForObject(
                    "select status from document_revisions where id=?",
                    String::class.java,
                    id,
                ),
        )
    }

    @Test
    fun realBatchInventoryFinishesAnUnknownTotalWithoutWeakeningFixedJobs() {
        val (actor, _) = fixture()
        val id = UUID.randomUUID()
        assertTrue(
            inventoryStart.execute(
                actor.copy(mfaVerifiedAt = Instant.now()),
                UUID.randomUUID(),
                id,
                null,
                "Worker reconciliation",
            ) is Result.Success
        )
        val owned = claim(JobKind.DOCUMENT_INVENTORY)
        assertEquals(Result.Success(Unit), batch.execute(owned, inventoryTask))
        assertEquals(
            "COMPLETED",
            database()
                .queryForObject(
                    "select status from document_inventory_runs where id=?",
                    String::class.java,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select completed_items from background_jobs where id=?",
                    Int::class.java,
                    owned.job.request.id,
                ),
        )
        assertEquals(10000, owned.job.request.totalItems)
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    owned.job.request.id,
                ),
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            database()
                .update(
                    "update background_jobs set status='SUCCEEDED',finished_at=now() where company_id=? and kind='DOCUMENT_VALIDATE'",
                    actor.companyId,
                )
        }
    }

    @Test
    fun interruptedInventoryBatchDiscardsThePageAndAnotherLeaseResumes() {
        val (actor, _) = fixture()
        val id = UUID.randomUUID()
        assertTrue(
            inventoryStart.execute(
                actor.copy(mfaVerifiedAt = Instant.now()),
                UUID.randomUUID(),
                id,
                null,
                "Worker reconciliation",
            ) is Result.Success
        )
        val old = claim(JobKind.DOCUMENT_INVENTORY)
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        inventoryProbe.beforeList = {
            entered.countDown()
            try {
                check(CountDownLatch(1).await(10, TimeUnit.SECONDS))
            } finally {
                exited.countDown()
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<Unit>> { batch.execute(old, inventoryTask) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
            } finally {
                pending.cancel(true)
            }
            assertTrue(exited.await(5, TimeUnit.SECONDS))
        }
        inventoryProbe.beforeList = null
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select pages from document_inventory_runs where id=?",
                    Int::class.java,
                    id,
                ),
        )
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                old.job.request.id,
            )
        val resumed = claim(JobKind.DOCUMENT_INVENTORY)
        assertNotEquals(old.token, resumed.token)
        assertEquals(Result.Success(Unit), batch.execute(resumed, inventoryTask))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_inventory_pages where run_id=?",
                    Int::class.java,
                    id,
                ),
        )
    }
}

class DocumentWorkerScanProbe : DocumentScanDataSource {
    @Volatile var beforeFinish: (() -> Unit)? = null
    val closed = AtomicInteger()

    override fun open(): DocumentScanSession {
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        return object : DocumentScanSession {
            override fun write(bytes: ByteArray) {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                check(bytes.isNotEmpty())
            }

            override fun finish(): DocumentScanData {
                beforeFinish?.invoke()
                return DocumentScanData(true, "ClamAV fixture/1")
            }

            override fun close() {
                closed.incrementAndGet()
            }
        }
    }
}

class DocumentInventoryWorkerProbe {
    @Volatile var beforeList: (() -> Unit)? = null
}

@TestConfiguration(proxyBeanMethods = false)
class DocumentWorkerProbeConfiguration {
    @Bean fun workerInventoryProbe() = DocumentInventoryWorkerProbe()

    @Bean @Primary fun workerDocumentScanner() = DocumentWorkerScanProbe()

    @Bean
    @Primary
    fun workerDocumentStorage(probe: DocumentInventoryWorkerProbe): ObjectStorageDataSource =
        object : ObjectStorageDataSource {
            val stored = ConcurrentHashMap<String, Pair<ByteArray, String>>()

            override fun list(
                prefix: String,
                afterKey: String?,
                limit: Int,
            ): dev.fajar.hris.storage.data.models.ObjectInventoryPageData {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                val values =
                    stored.entries
                        .filter {
                            it.key.startsWith(prefix) && (afterKey == null || it.key > afterKey)
                        }
                        .sortedBy { it.key }
                        .take(limit + 1)
                val page =
                    dev.fajar.hris.storage.data.models.ObjectInventoryPageData(
                        values.take(limit).map {
                            dev.fajar.hris.storage.data.models.ObjectInventoryEntryData(
                                it.key,
                                it.value.first.size.toLong(),
                                it.value.second,
                                Instant.now(),
                            )
                        },
                        values.size > limit,
                    )
                probe.beforeList?.invoke()
                return page
            }

            override fun put(key: String, bytes: ByteArray, sha256: String): ObjectMetadataData {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                stored[key] = bytes.copyOf() to sha256
                return ObjectMetadataData(bytes.size.toLong(), "\"$sha256\"", sha256)
            }

            override fun read(key: String, offset: Long, length: Int, etag: String): ByteArray {
                check(!TransactionSynchronizationManager.isActualTransactionActive())
                return stored
                    .getValue(key)
                    .first
                    .copyOfRange(offset.toInt(), offset.toInt() + length)
            }

            override fun metadata(key: String): ObjectMetadataData =
                stored.getValue(key).let {
                    ObjectMetadataData(it.first.size.toLong(), "\"${it.second}\"", it.second)
                }

            override fun delete(key: String) {
                stored.remove(key)
            }

            override fun close() {
                stored.clear()
            }
        }
}
