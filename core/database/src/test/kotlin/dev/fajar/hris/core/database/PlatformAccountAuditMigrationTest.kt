package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class PlatformAccountAuditMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeCapturesOnlySupportedFieldsAndRequiresBothCurrentGlobalGrants() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("99").load().migrate()
        val sql = JdbcTemplate(source)
        val author = UUID.randomUUID()
        val reader = UUID.randomUUID()
        val company = UUID.randomUUID()
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'author@example.test','Author'),(?,'reader@example.test','Reader')",
            author,
            reader,
        )
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'AUDITDETAIL','Detail fixture','UTC')",
            company,
        )
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'audit.read')",
            reader,
        )
        val original = seed(sql, author)
        val empty = seed(sql, author, permissions = "")
        val scoped = seed(sql, author, company = company)
        val malformed = seed(sql, author, permissions = "PRIVATE INVALID")
        val duplicated = seed(sql, author, permissions = "audit.read,audit.read")
        Flyway.configure().dataSource(source).target("100").load().migrate()
        assertEquals(
            setOf(original, empty),
            sql.queryForList("select id from platform_account_access_audit", UUID::class.java)
                .toSet(),
        )
        assertEquals(
            2,
            sql.queryForObject(
                "select count(*) from platform_account_access_audit where reason='Recorded reason'",
                Int::class.java,
            ),
        )
        assertEquals(
            6,
            sql.queryForObject(
                "select count(*) from information_schema.columns where table_name='platform_account_access_audit'",
                Int::class.java,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from platform_account_access_audit where id in (?, ?, ?)",
                Int::class.java,
                scoped,
                malformed,
                duplicated,
            ),
        )
        sql.execute(
            "create role account_audit_runtime login password 'fixture-only' nosuperuser nobypassrls"
        )
        sql.execute("grant usage on schema public to account_audit_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to account_audit_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "account_audit_runtime", "fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        fun actor(id: UUID, scope: UUID? = null) =
            Actor(id, scope, emptySet(), Instant.now(), UUID.randomUUID())
        fun count(who: Actor, table: String) =
            transactions.run(who) {
                safeDatabaseCall {
                    runtime.queryForObject("select count(*) from $table", Int::class.java)
                }
            }
        assertEquals(Result.Success(0), count(actor(reader), "platform_account_access_audit"))
        assertEquals(Result.Success(4), count(actor(reader), "platform_audit_metadata"))
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
            reader,
        )
        assertEquals(Result.Success(2), count(actor(reader), "platform_account_access_audit"))
        assertEquals(Result.Success(0), count(actor(reader), "audit_entries"))
        assertEquals(
            Result.Success(0),
            count(actor(reader, company), "platform_account_access_audit"),
        )
        val appended =
            transactions.run(actor(author)) { safeDatabaseCall { seed(runtime, author) } }
        assertTrue(appended is Result.Success, appended.toString())
        assertEquals(Result.Success(3), count(actor(reader), "platform_account_access_audit"))
        assertTrue(
            transactions.run(actor(author, company)) { safeDatabaseCall { seed(runtime, author) } }
                is Result.Success
        )
        assertEquals(Result.Success(4), count(actor(reader), "platform_account_access_audit"))
        for (encoded in
            listOf(
                "null",
                "true",
                "[]",
                "{}",
                "\"a,a\"",
                "\"${"a".repeat(81)}\"",
                "\"${(1..33).joinToString(",") { "p$it" }}\"",
                "\"${"a".repeat(4097)}\"",
            )) assertEquals(
            true,
            sql.queryForObject(
                "select platform_audit_permission_values(?::jsonb) is null",
                Boolean::class.java,
                encoded,
            ),
        )

        val rejected =
            transactions.run(actor(author)) {
                safeDatabaseCall {
                    runtime.update(
                        "insert into platform_account_access_audit values(?,?,?,true,array['identity.manage'],'Invented reason')",
                        malformed,
                        author,
                        UUID.randomUUID(),
                    )
                }
            }
        assertTrue(rejected is Result.Failed)
        val rollback = UUID.randomUUID()
        val rolledBack =
            transactions.run(actor(author)) {
                safeDatabaseCall { seed(runtime, author, id = rollback) }
                    .flatMap { Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback")) }
            }
        assertEquals("fixture_rollback", (rolledBack as Result.Failed).failure.code)
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from platform_account_access_audit where id=?",
                Int::class.java,
                rollback,
            ),
        )
        for (statement in
            listOf(
                "update platform_account_access_audit set active=false",
                "delete from platform_account_access_audit",
            )) {
            assertEquals(
                Result.Success(0),
                transactions.run(actor(reader)) { safeDatabaseCall { runtime.update(statement) } },
            )
            assertThrows(Exception::class.java) { sql.update(statement) }
        }
        sql.update(
            "delete from platform_permissions where account_id=? and permission='audit.read'",
            reader,
        )
        assertEquals(Result.Success(0), count(actor(reader), "platform_account_access_audit"))
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'audit.read')",
            reader,
        )
        sql.update(
            "delete from platform_permissions where account_id=? and permission='identity.manage'",
            reader,
        )
        assertEquals(Result.Success(0), count(actor(reader), "platform_account_access_audit"))
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
            reader,
        )
        sql.update("update accounts set active=false where id=?", reader)
        assertEquals(Result.Success(0), count(actor(reader), "platform_account_access_audit"))
    }

    private fun seed(
        sql: JdbcTemplate,
        actor: UUID,
        company: UUID? = null,
        permissions: String = "audit.read,identity.manage",
        id: UUID = UUID.randomUUID(),
    ): UUID {
        sql.update(
            "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,reason,details,correlation_id) values(?,?,?,'account',?,'identity.account_access_saved','Recorded reason',jsonb_build_object('active','true','permissions',?::text,'secret','PRIVATE_UNRELATED'),?)",
            id,
            company,
            actor,
            UUID.randomUUID(),
            permissions,
            UUID.randomUUID(),
        )
        return id
    }
}
