package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class DocumentMetadataMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeSerializesLegacyWritersAndEnforcesBoundedImmutableCompanyScopedHistory() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("102").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val otherActor = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        val document = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'META','Metadata upgrade','UTC'),(?,'OTHER','Other company','UTC')",
            company,
            other,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'metadata-upgrade@example.test','Fixture'),(?,'metadata-other@example.test','Other fixture')",
            actorId,
            otherActor,
        )
        sql.update(
            "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Fixture employee','AU')",
            person,
            company,
        )
        sql.update(
            "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'E001')",
            company,
            employee,
            person,
        )
        sql.update(
            "insert into documents(company_id,id,employment_id,title,classification,created_by) values(?,?,?,'Legacy passport','PERSONAL',?)",
            company,
            document,
            employee,
            actorId,
        )
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply {
                        setProperty("ApplicationName", "document-metadata-upgrade")
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer = JdbcTemplate(SingleConnectionDataSource(connection, true))
                    writer.update(
                        "update documents set version=1,revision_count=1 where company_id=? and id=?",
                        company,
                        document,
                    )
                    writer.update(
                        "insert into document_revisions(company_id,id,document_id,revision_no,file_name,media_type,expected_bytes,expected_sha256,created_by,created_at,expires_at,reason) values(?,?,?,1,'legacy.pdf','application/pdf',3,repeat('a',64),?,now(),now()+interval '1 day','Legacy upload')",
                        company,
                        UUID.randomUUID(),
                        document,
                        actorId,
                    )
                    val oldDocument =
                        writer.queryForMap(
                            "select * from documents where company_id=? and id=?",
                            company,
                            document,
                        )
                    val oldRevision =
                        writer.queryForList(
                            "select * from document_revisions where company_id=? and document_id=?",
                            company,
                            document,
                        )
                    val pid = writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("103")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        sql.queryForObject(
                            "select exists(select 1 from pg_stat_activity where application_name='document-metadata-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                            Boolean::class.java,
                            pid,
                        ) != true
                    ) {
                        check(!migration.isDone) { "Migration did not wait for the legacy writer" }
                        check(System.nanoTime() < deadline) {
                            "Migration did not acquire its guard"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(15, TimeUnit.SECONDS)
                    val migrated =
                        sql.queryForMap(
                            "select * from documents where company_id=? and id=?",
                            company,
                            document,
                        )
                    assertEquals(oldDocument, migrated.filterKeys { it in oldDocument.keys })
                    assertEquals(
                        oldRevision,
                        sql.queryForList(
                            "select * from document_revisions where company_id=? and document_id=?",
                            company,
                            document,
                        ),
                    )
                } finally {
                    connection.rollback()
                }
            }
        }
        val initial =
            sql.queryForMap(
                "select * from document_metadata_revisions where company_id=? and document_id=?",
                company,
                document,
            )
        assertEquals(0L, initial["version"])
        assertEquals(1L, initial["document_version"])
        assertEquals("OTHER", initial["category"])
        assertNull(initial["actor_id"])
        assertNull(initial["identity_number"])
        assertNull(initial["reason"])
        assertEquals(
            "AU",
            sql.queryForObject(
                "select nationality from persons where id=?",
                String::class.java,
                person,
            ),
        )
        sql.execute("create role metadata_runtime login password 'metadata-fixture-only'")
        sql.execute("grant usage on schema public to metadata_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to metadata_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "metadata_runtime", "metadata-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val actor = Actor(actorId, company, emptySet(), Instant.now(), UUID.randomUUID())
        fun <T> run(current: Actor = actor, body: () -> T) =
            transactions.run(current) { safeDatabaseCall(body) }
        fun reject(body: () -> Unit) {
            val result = run(body = body)
            assertTrue(result is Result.Failed, result.toString())
        }
        assertEquals(
            Result.Success(0),
            run(actor.copy(companyId = other)) {
                runtime.queryForObject(
                    "select count(*) from document_metadata_revisions",
                    Int::class.java,
                )
            },
        )
        assertEquals(
            Result.Success(1),
            run {
                runtime.queryForObject(
                    "select count(*) from document_metadata_revisions",
                    Int::class.java,
                )
            },
        )
        fun head(number: String = "A-123", version: Long = 1) {
            runtime.update(
                "update documents set version=version+1,metadata_version=?,category='PASSPORT',identity_number=? where company_id=? and id=?",
                version,
                number,
                company,
                document,
            )
        }
        fun history(who: UUID = actorId) {
            runtime.update(
                "insert into document_metadata_revisions(company_id,document_id,version,document_version,category,identity_number,actor_id,recorded_at,reason) select company_id,id,metadata_version,version,category,identity_number,?,now(),'Reviewed legacy evidence' from documents where company_id=? and id=?",
                who,
                company,
                document,
            )
        }
        reject { head() } // Deferred head reference cannot commit without history.
        reject {
            head()
            history(otherActor)
        } // Restrictive creator policy survives a valid FK.
        reject {
            runtime.update(
                "update documents set version=2,category='PASSPORT',revision_count=2 where company_id=? and id=?",
                company,
                document,
            )
        }
        reject {
            head()
            runtime.update(
                "insert into document_metadata_revisions(company_id,document_id,version,document_version,category,actor_id,recorded_at,reason) values(?,?,1,2,'CERTIFICATE',?,now(),'Mismatch')",
                company,
                document,
                actorId,
            )
        }
        assertTrue(
            run {
                head()
                history()
            }
                is Result.Success
        )
        reject {
            runtime.update(
                "update document_metadata_revisions set reason='Rewrite' where company_id=? and document_id=? and version=1",
                company,
                document,
            )
        }
        reject {
            runtime.update(
                "delete from document_metadata_revisions where company_id=? and document_id=? and version=1",
                company,
                document,
            )
        }
        reject {
            head(version = 2)
            history()
        } // No-op revisions cannot exhaust history.
        reject {
            head(number = "A-456", version = 3)
            history()
        } // Missing sequence.
        reject {
            runtime.update(
                "update documents set version=3,metadata_version=2,identity_number='A-456',issued_on='2030-01-01',valid_until='2020-01-01' where company_id=? and id=?",
                company,
                document,
            )
        }
        assertEquals(
            2,
            sql.queryForObject("select count(*) from document_metadata_revisions", Int::class.java),
        )
        val newDocument = UUID.randomUUID()
        assertTrue(
            run {
                // Old application SQL still omits all new columns; creation remains compatible.
                runtime.update(
                    "insert into documents(company_id,id,employment_id,title,classification,created_by) values(?,?,?,'New legacy request','PERSONAL',?)",
                    company,
                    newDocument,
                    employee,
                    actorId,
                )
            }
                is Result.Success
        )
        val newInitial =
            sql.queryForMap(
                "select actor_id,reason,document_version from document_metadata_revisions where company_id=? and document_id=?",
                company,
                newDocument,
            )
        assertEquals(actorId, newInitial["actor_id"])
        assertNull(newInitial["reason"])
        assertEquals(0L, newInitial["document_version"])
        // The finite history cap is enforced independently of application policy.
        val bounded = run {
            for (version in 2L..999L) {
                head("A-$version", version)
                history()
            }
        }
        assertTrue(bounded is Result.Success, bounded.toString())
        reject {
            head("overflow", 1000)
            history()
        }
        assertEquals(
            1000,
            sql.queryForObject(
                "select count(*) from document_metadata_revisions where company_id=? and document_id=?",
                Int::class.java,
                company,
                document,
            ),
        )
        assertEquals(
            999L,
            sql.queryForObject(
                "select metadata_version from documents where company_id=? and id=?",
                Long::class.java,
                company,
                document,
            ),
        )
    }
}
