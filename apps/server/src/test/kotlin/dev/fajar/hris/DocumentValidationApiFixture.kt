package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager

@Import(DocumentScanProbeConfiguration::class)
abstract class DocumentValidationApiFixture : DocumentApiFixture() {
    @Autowired protected lateinit var scan: DocumentScanProbe
    @Autowired protected lateinit var advance: AdvanceDocumentValidation
    @Autowired protected lateinit var abort: AbortDocumentValidation
    protected val pdf = "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".toByteArray()

    @AfterEach
    fun closeDocumentJobs() {
        scan.beforeFinish = null
        scan.clean = true
        scan.open.set(0)
        scan.closed.set(0)
        database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where kind='DOCUMENT_VALIDATE' and status in ('QUEUED','RUNNING')"
            )
    }

    protected fun filled(
        f: Fixture,
        bytes: ByteArray = pdf,
        document: UUID = UUID.randomUUID(),
        version: Long = 0,
    ): UUID {
        val id = begin(f, bytes, document, version = version)
        for (offset in bytes.indices step 1048576) {
            val response =
                upload(
                    f,
                    id,
                    bytes.copyOfRange(offset, minOf(bytes.size, offset + 1048576)),
                    offset.toLong(),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        return id
    }

    protected fun validate(
        f: Fixture,
        id: UUID,
        version: Long = revision(f, id).get("version").asLong(),
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/revisions/$id/validate",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Content review")
            ),
            f.csrf,
            key,
        )

    protected fun beginValidation(f: Fixture, id: UUID): JobLease {
        val response = validate(f, id)
        assertEquals(200, response.statusCode(), response.body())
        return claimDocuments().single { it.job.request.values["revisionId"] == id.toString() }
    }

    protected fun run(f: Fixture, lease: JobLease) =
        advance.execute(
            f.actor.copy(credentialVersion = lease.job.request.credentialVersion),
            lease,
        )

    protected fun jobStatus(lease: JobLease) =
        database()
            .queryForObject(
                "select status from background_jobs where id=?",
                String::class.java,
                lease.job.request.id,
            )

    protected fun garbage(id: UUID) =
        database()
            .queryForObject(
                "select count(*) from object_cleanup_queue where resource_id=?",
                Int::class.java,
                id,
            )

    protected fun claimDocuments(): List<JobLease> {
        database()
            .execute(
                """DO ${'$'}${'$'} BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_document_worker') THEN
            CREATE ROLE hris_document_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END ${'$'}${'$'}"""
            )
        database().execute("GRANT hris_worker_capability TO hris_document_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_document_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_document_worker"
            )
        val source =
            DriverManagerDataSource(postgres.jdbcUrl, "hris_document_worker", "fixture-worker-only")
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source))
        val result =
            LeaseJobs(
                    jobs,
                    transactions,
                    PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                    clock,
                    JobRetryPolicy(),
                )
                .execute(UUID.randomUUID(), 2, 120, setOf(JobKind.DOCUMENT_VALIDATE))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }
}
