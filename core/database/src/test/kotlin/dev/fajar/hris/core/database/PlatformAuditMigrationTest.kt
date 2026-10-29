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
class PlatformAuditMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun upgradeCapturesOnlyGlobalMetadataAndRuntimeCannotExpandOrRewriteIt() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).target("98").load().migrate()
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
            "insert into companies(id,code,name,timezone) values(?,'AUDIT','Audit fixture','UTC')",
            company,
        )
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'audit.read')",
            reader,
        )
        val global = seed(sql, author)
        val scoped = seed(sql, author, company)
        Flyway.configure().dataSource(source).target("99").load().migrate()
        assertEquals(
            listOf(global),
            sql.queryForList("select id from platform_audit_metadata", UUID::class.java),
        )
        assertEquals(
            7,
            sql.queryForObject(
                "select count(*) from information_schema.columns where table_name='platform_audit_metadata'",
                Int::class.java,
            ),
        )
        sql.execute(
            "create role audit_fixture_runtime login password 'fixture-only' nosuperuser nobypassrls"
        )
        sql.execute("grant usage on schema public to audit_fixture_runtime")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to audit_fixture_runtime"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "audit_fixture_runtime", "fixture-only")
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
        assertEquals(Result.Success(1), count(actor(reader), "platform_audit_metadata"))
        assertEquals(Result.Success(0), count(actor(reader), "audit_entries"))
        assertEquals(Result.Success(0), count(actor(author), "platform_audit_metadata"))
        assertEquals(Result.Success(0), count(actor(reader, company), "platform_audit_metadata"))
        // A writer needs no audit-read grant; the trigger captures its own source in the same
        // transaction.
        val appended =
            transactions.run(actor(author)) { safeDatabaseCall { seed(runtime, author) } }
        assertTrue(appended is Result.Success, appended.toString())
        val inCompany =
            transactions.run(actor(author, company)) { safeDatabaseCall { seed(runtime, author) } }
        assertTrue(inCompany is Result.Success, inCompany.toString())
        assertEquals(Result.Success(3), count(actor(reader), "platform_audit_metadata"))
        val rollbackId = UUID.randomUUID()
        assertTrue(
            transactions.run(actor(author)) {
                safeDatabaseCall { seed(runtime, author, id = rollbackId) }
                    .flatMap { Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback")) }
            } is Result.Failed
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from platform_audit_metadata where id=?",
                Int::class.java,
                rollbackId,
            ),
        )
        // Company sources, fabricated fields and another actor's source cannot enter the
        // projection.
        for (statement in
            listOf(
                "insert into platform_audit_metadata select id,actor_id,resource_type,resource_id,action,correlation_id,created_at from audit_entries where id='$scoped'",
                "insert into platform_audit_metadata values('${UUID.randomUUID()}','$author','account','${UUID.randomUUID()}','invented','${UUID.randomUUID()}',now())",
                "insert into platform_audit_metadata select id,actor_id,resource_type,resource_id,'invented',correlation_id,created_at from audit_entries where id='$global'",
            )) {
            assertTrue(
                transactions.run(actor(author, company)) {
                    safeDatabaseCall { runtime.update(statement) }
                } is Result.Failed
            )
        }
        for (statement in
            listOf(
                "update platform_audit_metadata set action='invented'",
                "delete from platform_audit_metadata",
            )) {
            assertEquals(
                Result.Success(0),
                transactions.run(actor(reader)) { safeDatabaseCall { runtime.update(statement) } },
            )
            assertThrows(Exception::class.java) { sql.update(statement) }
        }
        sql.update("delete from platform_permissions where account_id=?", reader)
        assertEquals(Result.Success(0), count(actor(reader), "platform_audit_metadata"))
        sql.update(
            "insert into platform_permissions(account_id,permission) values(?,'audit.read')",
            reader,
        )
        sql.update("update accounts set active=false where id=?", reader)
        assertEquals(Result.Success(0), count(actor(reader), "platform_audit_metadata"))
    }

    private fun seed(
        sql: JdbcTemplate,
        actor: UUID,
        company: UUID? = null,
        id: UUID = UUID.randomUUID(),
    ): UUID {
        sql.update(
            "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,reason,details,correlation_id) values(?,?,?,'account',?,'fixture.created','PRIVATE_REASON','{\"private\":\"PRIVATE_PAYLOAD\"}'::jsonb,?)",
            id,
            company,
            actor,
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
        return id
    }
}
