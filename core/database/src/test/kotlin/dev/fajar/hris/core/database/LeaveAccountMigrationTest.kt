package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class LeaveAccountMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun existingLedgerIsBackfilledWithoutRewritingFactsOrPublishingFakeChanges() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("42").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val other = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        val type = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'UPGRADE','Upgrade fixture','Asia/Jakarta'),(?,'OTHER','Other fixture','UTC')",
            company,
            other,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'upgrade@example.test','Fixture operator')",
            actor,
        )
        sql.update(
            "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Fixture employee','ID')",
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
            "insert into leave_types(company_id,id,code) values(?,?,'ANNUAL')",
            company,
            type,
        )
        for ((kind, deltas) in
            listOf(
                "ADJUSTMENT" to listOf(8, 0, 0),
                "RESERVE" to listOf(-2, 2, 0),
                "CONSUME" to listOf(0, -1, 1),
            )) {
            sql.update(
                "insert into leave_ledger(company_id,id,employment_id,type_id,balance_year,kind,source_id,available_delta,reserved_delta,consumed_delta,actor_id,recorded_at,reason) values(?,?,?,?,2026,?,?,?,?,?,?,now(),'Existing fact')",
                company,
                UUID.randomUUID(),
                employee,
                type,
                kind,
                UUID.randomUUID(),
                deltas[0],
                deltas[1],
                deltas[2],
                actor,
            )
        }
        val before =
            sql.queryForList(
                "select id,available_delta,reserved_delta,consumed_delta from leave_ledger order by id"
            )
        Flyway.configure().dataSource(source).target("43").load().migrate()
        val balance = sql.queryForMap("select * from leave_accounts where company_id=?", company)
        assertEquals(6L, balance["available_half_days"])
        assertEquals(1L, balance["reserved_half_days"])
        assertEquals(1L, balance["consumed_half_days"])
        assertEquals(3L, balance["version"])
        assertEquals(
            before,
            sql.queryForList(
                "select id,available_delta,reserved_delta,consumed_delta from leave_ledger order by id"
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='LEAVE_BALANCES'",
                Int::class.java,
            ),
        )
        assertThrows(DataAccessException::class.java) {
            sql.update(
                "update leave_accounts set available_half_days=7,version=version+1 where company_id=?",
                company,
            )
        }
        sql.execute("create role upgrade_runtime login password 'upgrade-fixture-only'")
        sql.execute("grant usage on schema public to upgrade_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to upgrade_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "upgrade_runtime", "upgrade-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val foreign =
            transactions.run(Actor(actor, other, emptySet(), Instant.now(), UUID.randomUUID())) {
                safeDatabaseCall {
                    runtime.queryForObject("select count(*) from leave_accounts", Int::class.java)
                }
            }
        assertEquals(Result.Success(0), foreign)
        val appended =
            transactions.run(Actor(actor, company, emptySet(), Instant.now(), UUID.randomUUID())) {
                safeDatabaseCall {
                    runtime.update(
                        "insert into leave_ledger(company_id,id,employment_id,type_id,balance_year,kind,source_id,available_delta,reserved_delta,consumed_delta,actor_id,recorded_at,reason) values(?,?,?,?,2026,'ADJUSTMENT',?,1,0,0,?,now(),'New fact')",
                        company,
                        UUID.randomUUID(),
                        employee,
                        type,
                        UUID.randomUUID(),
                        actor,
                    )
                }
            }
        assertTrue(appended is Result.Success, appended.toString())
        val updated = sql.queryForMap("select * from leave_accounts where company_id=?", company)
        assertEquals(balance["id"], updated["id"])
        assertEquals(4L, updated["version"])
        assertEquals(7L, updated["available_half_days"])
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='LEAVE_BALANCES'",
                Int::class.java,
            ),
        )
    }
}
