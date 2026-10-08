package dev.fajar.hris.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresIsolationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private val companyA = UUID.randomUUID()
    private val companyB = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private lateinit var pool: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var admin: JdbcTemplate
    private lateinit var transactions: PostgresTransactionRunner

    @BeforeAll
    fun setup() {
        val privileged =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(privileged).load().migrate()
        admin = JdbcTemplate(privileged)
        admin.execute("create role hris_runtime login password 'runtime-test'")
        admin.execute("grant usage on schema public to hris_runtime")
        admin.execute(
            "grant select, insert, update, delete on all tables in schema public to hris_runtime"
        )
        admin.update(
            "insert into companies(id,code,name,timezone) values (?,?,?,?)",
            companyA,
            "A",
            "Company A",
            "Asia/Jakarta",
        )
        admin.update(
            "insert into companies(id,code,name,timezone) values (?,?,?,?)",
            companyB,
            "B",
            "Company B",
            "Asia/Makassar",
        )
        pool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = postgres.jdbcUrl
                    username = "hris_runtime"
                    password = "runtime-test"
                    maximumPoolSize = 1
                    connectionTimeout = 2000
                }
            )
        jdbc = JdbcTemplate(pool)
        transactions = PostgresTransactionRunner(JdbcTransactionManager(pool), jdbc)
    }

    @AfterAll fun close() = pool.close()

    private fun actor(company: UUID?) =
        Actor(actorId, company, emptySet(), Instant.now(), UUID.randomUUID())

    @Test
    fun companyScopeDoesNotLeakThroughReusedConnection() {
        val first =
            transactions.run(actor(companyA)) {
                Result.Success(jdbc.queryForList("select code from companies", String::class.java))
            }
        val second =
            transactions.run(actor(companyB)) {
                Result.Success(jdbc.queryForList("select code from companies", String::class.java))
            }
        assertEquals(Result.Success(listOf("A")), first)
        assertEquals(Result.Success(listOf("B")), second)
        assertEquals(0, jdbc.queryForObject("select count(*) from companies", Int::class.java))
    }

    @Test
    fun writesOutsideTheCompanyScopeAreDenied() {
        val result =
            transactions.run(actor(companyA)) {
                safeDatabaseCall {
                    jdbc.update(
                        "insert into companies(id,code,name,timezone) values (?,?,?,?)",
                        UUID.randomUUID(),
                        "OTHER",
                        "Other",
                        "UTC",
                    )
                }
            }
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from companies where code='OTHER'",
                Int::class.java,
            ),
        )
    }

    @Test
    fun domainFailureRollsBackEarlierWritesAndKeepsItsClassification() {
        val id = UUID.randomUUID()
        val expected = Result.Failed(Failure(FailureKind.VALIDATION, "policy_rejected"))
        val result =
            transactions.run(actor(companyA)) {
                jdbc.update("update companies set name='Changed' where id=?", companyA)
                jdbc.update(
                    "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,correlation_id) values(?,?,?,'company',?,'changed',?)",
                    id,
                    companyA,
                    actorId,
                    companyA,
                    UUID.randomUUID(),
                )
                expected
            }
        assertEquals(expected, result)
        assertEquals(
            "Company A",
            admin.queryForObject(
                "select name from companies where id=?",
                String::class.java,
                companyA,
            ),
        )
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from audit_entries where id=?",
                Int::class.java,
                id,
            ),
        )
    }

    @Test
    fun interruptionRollsBackAndIsNotConvertedIntoBusinessFailure() {
        assertThrows(CancellationException::class.java) {
            transactions.run<Unit>(actor(companyB)) {
                jdbc.update("update companies set name='Cancelled' where id=?", companyB)
                throw CancellationException("cancelled")
            }
        }
        assertEquals(
            "Company B",
            admin.queryForObject(
                "select name from companies where id=?",
                String::class.java,
                companyB,
            ),
        )
    }

    @Test
    fun auditRecordsCannotBeUpdatedOrDeletedByRuntime() {
        val id = UUID.randomUUID()
        admin.update(
            "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,correlation_id) values(?,?,?,'company',?,'created',?)",
            id,
            companyA,
            actorId,
            companyA,
            UUID.randomUUID(),
        )
        val result =
            transactions.run(actor(companyA)) {
                safeDatabaseCall { jdbc.update("delete from audit_entries where id=?", id) }
            }
        assertInstanceOf(Result.Failed::class.java, result)
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from audit_entries where id=?",
                Int::class.java,
                id,
            ),
        )
    }

    @Test
    fun rawJooqExceptionsRemainInsideTheDataBoundary() {
        val sql =
            org.jooq.impl.DSL.using(
                org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(pool),
                org.jooq.SQLDialect.POSTGRES,
            )
        val result =
            transactions.run(actor(companyA)) {
                safeDatabaseCall {
                    sql.execute(
                        "insert into companies(id,code,name,timezone) values(?,?,?,?)",
                        UUID.randomUUID(),
                        "DENIED",
                        "Other",
                        "UTC",
                    )
                }
            }
        assertEquals(Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied")), result)
    }

    @Test
    fun queryTimeoutRollsBackAndTransactionSettingsDoNotLeak() {
        val bounded =
            PostgresTransactionRunner(
                JdbcTransactionManager(pool),
                jdbc,
                java.time.Duration.ofMillis(150),
                java.time.Duration.ofMillis(100),
            )
        val result =
            bounded.run(actor(companyA)) {
                safeDatabaseCall {
                    jdbc.update("update companies set name='Timed out' where id=?", companyA)
                    jdbc.queryForObject("select pg_sleep(1)", String::class.java)
                }
            }
        assertEquals(Result.Failed(Failure(FailureKind.UNAVAILABLE, "database_busy")), result)
        assertEquals(
            "Company A",
            admin.queryForObject(
                "select name from companies where id=?",
                String::class.java,
                companyA,
            ),
        )
        assertEquals("0", jdbc.queryForObject("show statement_timeout", String::class.java))
        assertEquals("0", jdbc.queryForObject("show lock_timeout", String::class.java))
        assertEquals(0, jdbc.queryForObject("select count(*) from companies", Int::class.java))
    }

    @Test
    fun lateInterruptionRollsBackEvenWhenTheOperationReturnsSuccess() {
        try {
            assertThrows(InterruptedException::class.java) {
                transactions.run(actor(companyB)) {
                    jdbc.update("update companies set name='Late success' where id=?", companyB)
                    Thread.currentThread().interrupt()
                    Result.Success(Unit)
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(
            "Company B",
            admin.queryForObject(
                "select name from companies where id=?",
                String::class.java,
                companyB,
            ),
        )
    }

    @Test
    fun explicitSecondaryScopeIsLimitedToRequiredTablesAndClearsOnRollbackAndReuse() {
        val third = UUID.randomUUID()
        val firstEvent = UUID.randomUUID()
        val secondEvent = UUID.randomUUID()
        admin.update(
            "insert into companies(id,code,name,timezone) values(?,?,'Third company','UTC')",
            third,
            "C${third.toString().take(8)}",
        )
        val expected = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
        val outcome =
            transactions.run(actor(companyA), companyB) {
                assertEquals(
                    listOf("A", "B"),
                    jdbc.queryForList(
                        "select code from companies order by code",
                        String::class.java,
                    ),
                )
                // Secondary company metadata is readable; this scope grants no metadata update.
                assertEquals(
                    0,
                    jdbc.update("update companies set name='Not permitted' where id=?", companyB),
                )
                for ((company, id) in listOf(companyA to firstEvent, companyB to secondEvent)) {
                    jdbc.update(
                        "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,correlation_id) values(?,?,?,'fixture',?,'transfer_scope',?)",
                        id,
                        company,
                        actorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                    )
                }
                expected
            }
        assertEquals(expected, outcome)
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from audit_entries where id in (?,?)",
                Int::class.java,
                firstEvent,
                secondEvent,
            ),
        )
        assertEquals(
            Result.Success(listOf("A")),
            transactions.run(actor(companyA)) {
                Result.Success(jdbc.queryForList("select code from companies", String::class.java))
            },
        )
        assertTrue(
            jdbc
                .queryForObject(
                    "select current_setting('hris.secondary_company_id',true)",
                    String::class.java,
                )
                .isNullOrEmpty()
        )
        val denied =
            transactions.run(actor(companyA), companyB) {
                safeDatabaseCall {
                    jdbc.update(
                        "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,correlation_id) values(?,?,?,'fixture',?,'forbidden',?)",
                        UUID.randomUUID(),
                        third,
                        actorId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                    )
                }
            }
        assertInstanceOf(Result.Failed::class.java, denied)
        assertEquals(0, jdbc.queryForObject("select count(*) from companies", Int::class.java))
        assertThrows(IllegalArgumentException::class.java) {
            transactions.run(actor(null), companyB) { Result.Success(Unit) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            transactions.run(actor(companyA), companyA) { Result.Success(Unit) }
        }
    }
}
