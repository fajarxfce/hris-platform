package dev.fajar.hris.core.database

import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class LeaveCarryMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradePreservesHistoricCarryLedgerVersionsAndSynchronizationWithoutRetroactiveExpiry() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("82").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        val type = UUID.randomUUID()
        val closing = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'CARRYUPGRADE','Carry upgrade fixture','Asia/Jakarta')",
            company,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'carry-upgrade@example.test','Fixture operator')",
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
        sql.update(
            """insert into leave_type_revisions(company_id,type_id,revision,effective_from,details,active,actor_id,reason)
                values(?,?,0,'2026-01-01',cast(? as jsonb),true,?,'Original policy')""",
            company,
            type,
            """{"name":"Annual leave","paid":true,"allowPartialDays":true,"minServiceMonths":0,"allowedContracts":[],"maxRequestDays":30,"attachmentRequired":false,"accrual":{"frequency":"MANUAL","halfDaysPerPeriod":0,"carryLimitHalfDays":4}}""",
            actor,
        )
        sql.update(
            """insert into leave_ledger(company_id,id,employment_id,type_id,balance_year,kind,source_id,available_delta,reserved_delta,consumed_delta,actor_id,recorded_at,reason)
                values(?,?,?,?,2026,'ADJUSTMENT',?,6,0,0,?,'2026-12-31T00:00:00Z','Original credit')""",
            company,
            UUID.randomUUID(),
            employee,
            type,
            UUID.randomUUID(),
            actor,
        )
        TransactionTemplate(JdbcTransactionManager(source)).executeWithoutResult {
            sql.update(
                """insert into leave_year_closings(company_id,id,employment_id,type_id,balance_year,source_version,destination_version,
                    available_half_days,consumed_half_days,carry_half_days,expire_half_days,policy_revision,policy_snapshot,actor_id,recorded_at,reason)
                    select ?,?,?,?,2026,1,0,6,0,4,2,0,jsonb_build_object('typeId',type_id,'code','ANNUAL','revision',0,'policy',details),?,'2027-01-05T00:00:00Z','Original closing'
                    from leave_type_revisions where company_id=? and type_id=? and revision=0""",
                company,
                closing,
                employee,
                type,
                actor,
                company,
                type,
            )
            for ((kind, year, delta) in
                listOf(
                    Triple("CARRY_OUT", 2026, -4),
                    Triple("CARRY_IN", 2027, 4),
                    Triple("EXPIRE", 2026, -2),
                )) {
                sql.update(
                    """insert into leave_ledger(company_id,id,employment_id,type_id,balance_year,kind,source_id,available_delta,reserved_delta,consumed_delta,actor_id,recorded_at,reason)
                        values(?,?,?,?,?,?,?,?,0,0,?,'2027-01-05T00:00:00Z','Original closing')""",
                    company,
                    UUID.randomUUID(),
                    employee,
                    type,
                    year,
                    kind,
                    closing,
                    delta,
                    actor,
                )
            }
            sql.update(
                "update leave_accounts set closing_id=?,version=version+1 where company_id=? and employment_id=? and type_id=? and balance_year=2026",
                closing,
                company,
                employee,
                type,
            )
        }
        val ledgerQuery =
            "select id,kind,source_id,year_closing_id,available_delta,reserved_delta,consumed_delta,recorded_at from leave_ledger order by id"
        val beforeLedger = sql.queryForList(ledgerQuery)
        val beforeAccounts = sql.queryForList("select * from leave_accounts order by id")
        val beforeSync = sql.queryForList("select * from mobile_sync_changes order by id")
        val beforeClosing =
            sql.queryForMap(
                "select policy_snapshot,recorded_at from leave_year_closings where company_id=?",
                company,
            )
        Flyway.configure().dataSource(source).target("83").load().migrate()
        assertEquals(beforeLedger, sql.queryForList(ledgerQuery))
        assertEquals(beforeAccounts, sql.queryForList("select * from leave_accounts order by id"))
        assertEquals(beforeSync, sql.queryForList("select * from mobile_sync_changes order by id"))
        assertEquals(
            beforeClosing,
            sql.queryForMap(
                "select policy_snapshot,recorded_at from leave_year_closings where company_id=?",
                company,
            ),
        )
        assertEquals(
            false,
            sql.queryForObject(
                "select carry_tracking from leave_year_closings where company_id=?",
                Boolean::class.java,
                company,
            ),
        )
        assertNull(
            sql.queryForObject(
                "select company_timezone from leave_year_closings where company_id=?",
                String::class.java,
                company,
            )
        )
        for (table in
            listOf("leave_carry_buckets", "leave_carry_allocations", "leave_carry_movements")) {
            assertEquals(0, sql.queryForObject("select count(*) from $table", Int::class.java))
            assertEquals(
                true,
                sql.queryForObject(
                    "select relrowsecurity and relforcerowsecurity from pg_class where oid=cast(? as regclass)",
                    Boolean::class.java,
                    table,
                ),
            )
        }
        assertThrows(DataAccessException::class.java) {
            sql.update(
                """insert into leave_carry_buckets(company_id,id,employment_id,type_id,balance_year,initial_half_days,available_half_days,company_timezone)
                values(?,?,?,?,2027,4,4,'Asia/Jakarta')""",
                company,
                closing,
                employee,
                type,
            )
        }
        assertEquals(beforeLedger, sql.queryForList(ledgerQuery))
        assertEquals(beforeAccounts, sql.queryForList("select * from leave_accounts order by id"))
    }
}
