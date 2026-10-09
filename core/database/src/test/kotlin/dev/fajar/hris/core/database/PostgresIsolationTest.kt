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

    @Test
    fun reportReadScopeIsBoundedToSelectedEmploymentInputsAndNeverGrantsCompanyWrites() {
        admin.update(
            "insert into accounts(id,email,display_name) values(?,?,'Report scope fixture') on conflict do nothing",
            actorId,
            "$actorId@example.test",
        )
        val third = UUID.randomUUID()
        admin.update(
            "insert into companies(id,code,name,timezone) values(?,?,'Report third','UTC')",
            third,
            "R${third.toString().take(8)}",
        )
        for (company in listOf(companyA, companyB, third)) {
            val person = UUID.randomUUID()
            val employment = UUID.randomUUID()
            admin.update(
                "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Private profile','ID')",
                person,
                company,
            )
            admin.update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,?)",
                company,
                employment,
                person,
                "R${employment.toString().take(8)}",
            )
            admin.update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE',?,'Read scope fixture')",
                company,
                employment,
                actorId,
            )
        }
        val result =
            transactions.run(actor(null), setOf(companyB, companyA)) {
                for (table in listOf("employments", "employment_revisions")) {
                    assertEquals(
                        setOf(companyA, companyB),
                        jdbc
                            .queryForList(
                                "select distinct company_id from $table",
                                UUID::class.java,
                            )
                            .toSet(),
                    )
                }
                assertEquals(
                    0,
                    jdbc.queryForObject("select count(*) from persons", Int::class.java),
                )
                assertEquals(
                    0,
                    jdbc.queryForObject("select count(*) from companies", Int::class.java),
                )
                assertEquals(
                    0,
                    jdbc.update(
                        "update employments set employee_number='FORBIDDEN' where company_id=?",
                        companyA,
                    ),
                )
                Result.Success(Unit)
            }
        assertEquals(Result.Success(Unit), result)
        val denied =
            transactions.run(actor(null), setOf(companyA)) {
                safeDatabaseCall {
                    jdbc.update(
                        "insert into employments(company_id,id,person_id,employee_number) select company_id,?,person_id,'FORBIDDEN' from employments where company_id=? limit 1",
                        UUID.randomUUID(),
                        companyA,
                    )
                }
            }
        assertEquals(Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied")), denied)
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from employments where employee_number='FORBIDDEN'",
                Int::class.java,
            ),
        )
        assertEquals(0, jdbc.queryForObject("select count(*) from employments", Int::class.java))
    }

    @Test
    fun reportScopeClearsAfterFailureCancellationAndConnectionReuse() {
        val rejected = Result.Failed(Failure(FailureKind.CONFLICT, "report_rejected"))
        assertEquals(
            rejected,
            transactions.run(actor(null), setOf(companyA, companyB)) { rejected },
        )
        assertThrows(CancellationException::class.java) {
            transactions.run<Unit>(actor(null), setOf(companyA)) { throw CancellationException() }
        }
        assertEquals(
            0,
            jdbc.queryForObject("select cardinality(current_read_company_ids())", Int::class.java),
        )
        // Even a preexisting connection setting is replaced by every ordinary transaction owner.
        jdbc.queryForObject(
            "select set_config('hris.read_company_ids',?,false)",
            String::class.java,
            "{$companyB}",
        )
        try {
            assertEquals(
                Result.Success(0),
                transactions.run(actor(companyA)) {
                    Result.Success(
                        jdbc.queryForObject(
                            "select cardinality(current_read_company_ids())",
                            Int::class.java,
                        )
                    )
                },
            )
            assertEquals(
                Result.Success(0),
                transactions.run(actor(companyA), companyB) {
                    Result.Success(
                        jdbc.queryForObject(
                            "select cardinality(current_read_company_ids())",
                            Int::class.java,
                        )
                    )
                },
            )
        } finally {
            jdbc.queryForObject(
                "select set_config('hris.read_company_ids','',false)",
                String::class.java,
            )
        }
        assertEquals(
            0,
            jdbc.queryForObject("select cardinality(current_read_company_ids())", Int::class.java),
        )
    }

    @Test
    fun invalidReportScopesNeverStartAnOperationAndDatabaseContextRejectsMalformedArrays() {
        for (selection in listOf(emptySet(), List(33) { UUID.randomUUID() }.toSet())) {
            assertThrows(IllegalArgumentException::class.java) {
                transactions.run<Unit>(actor(null), selection) {
                    fail("invalid read scope entered")
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            transactions.run<Unit>(actor(companyA), setOf(companyB)) {
                fail("company actor entered group scope")
            }
        }
        assertEquals(
            Result.Success(32),
            transactions.run(actor(null), List(32) { UUID.randomUUID() }.toSet()) {
                Result.Success(
                    jdbc.queryForObject(
                        "select cardinality(current_read_company_ids())",
                        Int::class.java,
                    )
                )
            },
        )
        for (value in
            listOf(
                "{{$companyA}}",
                "{$companyA,NULL}",
                List(33) { UUID.randomUUID() }.joinToString(",", "{", "}"),
            )) {
            val result =
                transactions.run(actor(null)) {
                    safeDatabaseCall {
                        jdbc.queryForObject(
                            "select set_config('hris.read_company_ids',?,true)",
                            String::class.java,
                            value,
                        )
                        jdbc.queryForObject(
                            "select cardinality(current_read_company_ids())",
                            Int::class.java,
                        )
                    }
                }
            assertInstanceOf(Result.Failed::class.java, result)
        }
        assertEquals(
            0,
            jdbc.queryForObject("select cardinality(current_read_company_ids())", Int::class.java),
        )
    }
}
