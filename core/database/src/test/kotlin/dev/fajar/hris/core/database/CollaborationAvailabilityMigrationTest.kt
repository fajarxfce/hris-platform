package dev.fajar.hris.core.database

import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationAvailabilityMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun newModuleSurvivesRestoreWithoutRewritingExistingPolicyRevisions() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("104").load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'AVAIL','Availability fixture','UTC')",
            company,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'availability@example.test','Fixture')",
            account,
        )
        TransactionTemplate(JdbcTransactionManager(source)).executeWithoutResult {
            sql.update(
                "insert into company_client_policy_heads(company_id,version) values(?,0)",
                company,
            )
            sql.update(
                "insert into company_client_policy_revisions(company_id,version,activate_at,disabled_modules,minimum_android_build,minimum_ios_build,minimum_web_build,actor_id,recorded_at,reason) values(?,0,now(),ARRAY['COMMUNICATIONS']::varchar[],0,0,0,?,now(),'Fixture policy')",
                company,
                account,
            )
        }
        val original =
            sql.queryForList(
                "select row_to_json(p)::text as snapshot from company_client_policy_revisions p"
            )
        Flyway.configure().dataSource(source).target("105").load().migrate()
        assertEquals(
            original,
            sql.queryForList(
                "select row_to_json(p)::text as snapshot from company_client_policy_revisions p"
            ),
        )
        val all =
            "{PEOPLE,WORKFORCE,LEAVE,EXPENSES,PAYROLL,DOCUMENTS,COMMUNICATIONS,REPORTING,COLLABORATION}"
        assertEquals(
            true,
            sql.queryForObject(
                "select valid_client_modules(?::varchar[])",
                Boolean::class.java,
                all,
            ),
        )
        for (invalid in
            listOf(
                "{COLLABORATION,COLLABORATION}",
                "{UNKNOWN}",
                "{NULL}",
                all.dropLast(1) + ",EXTRA}",
            )) {
            assertEquals(
                false,
                sql.queryForObject(
                    "select valid_client_modules(?::varchar[])",
                    Boolean::class.java,
                    invalid,
                ),
            )
        }
        fun command(vararg arguments: String) {
            val result = postgres.execInContainer(*arguments)
            assertEquals(0, result.exitCode, result.stderr)
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/availability.dump",
        )
        command("createdb", "-U", postgres.username, "availability_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "availability_restored",
            "--exit-on-error",
            "--no-owner",
            "--clean",
            "--if-exists",
            "/tmp/availability.dump",
        )
        val restoredUrl =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') +
                "/availability_restored"
        val restored =
            JdbcTemplate(DriverManagerDataSource(restoredUrl, postgres.username, postgres.password))
        assertEquals(
            original,
            restored.queryForList(
                "select row_to_json(p)::text as snapshot from company_client_policy_revisions p"
            ),
        )
        assertEquals(
            true,
            restored.queryForObject(
                "select valid_client_modules(?::varchar[])",
                Boolean::class.java,
                all,
            ),
        )
        assertEquals(
            false,
            restored.queryForObject(
                "select valid_client_modules('{COLLABORATION,COLLABORATION}'::varchar[])",
                Boolean::class.java,
            ),
        )
    }
}
