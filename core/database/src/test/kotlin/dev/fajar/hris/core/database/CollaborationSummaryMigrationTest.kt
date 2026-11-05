package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
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
class CollaborationSummaryMigrationTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun additiveUpgradeWaitsForPrecedingWriterAndCapturesEveryRetainedPair() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        Flyway.configure().dataSource(source).target("116").load().migrate()
        val company = UUID.randomUUID()
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val pair = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'SUMMARY','Fictional summary company','UTC')",
            company,
        )
        for (id in listOf(first, second)) {
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional summary participant')",
                id,
                "$id@example.test",
            )
        }
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val writer =
                pool.submit {
                    TransactionTemplate(JdbcTransactionManager(source)).execute {
                        sql.update(
                            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
                            company,
                            pair,
                            first,
                            second,
                            first,
                        )
                        written.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            val migration =
                pool.submit {
                    Flyway.configure().dataSource(source).target("116.1").load().migrate()
                }
            try {
                // Wait for the actual schema-lock waiter, not a guessed startup delay.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (System.nanoTime() < deadline) {
                    val waiting =
                        sql.queryForObject(
                            "select count(*) from pg_locks where relation='collaboration_direct_conversations'::regclass and not granted",
                            Int::class.java,
                        )!!
                    if (waiting > 0) break
                    Thread.sleep(20)
                }
                assertFalse(migration.isDone)
                assertTrue(
                    sql.queryForObject(
                        "select count(*) from pg_locks where relation='collaboration_direct_conversations'::regclass and not granted",
                        Int::class.java,
                    )!! > 0
                )
            } finally {
                release.countDown()
            }
            writer.get(15, TimeUnit.SECONDS)
            migration.get(30, TimeUnit.SECONDS)
        }
        assertEquals(
            "116.1",
            Flyway.configure().dataSource(source).load().info().current().version.version,
        )
        assertTrue(MigrationVersion.fromVersion("116.1") < MigrationVersion.fromVersion("117"))
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_summary_refresh_queue where company_id=? and conversation_id=? and due_at is not null",
                Int::class.java,
                company,
                pair,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from collaboration_direct_summary_records",
                Int::class.java,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from mobile_sync_changes where collection='COLLABORATION_DIRECT'",
                Int::class.java,
            ),
        )
        val canonical =
            sql.queryForMap(
                "select company_id,id,first_account_id,second_account_id,created_by,version from collaboration_direct_conversations where id=?",
                pair,
            )
        assertEquals(company, canonical["company_id"])
        assertEquals(first, canonical["first_account_id"])
        assertEquals(second, canonical["second_account_id"])
        assertEquals(first, canonical["created_by"])
        assertEquals(0L, canonical["version"])
        val immutable =
            sql.queryForObject(
                "select row_to_json(c)::text from collaboration_direct_conversations c where id=?",
                String::class.java,
                pair,
            )
        Flyway.configure().dataSource(source).target("116.1").load().migrate()
        assertEquals(
            immutable,
            sql.queryForObject(
                "select row_to_json(c)::text from collaboration_direct_conversations c where id=?",
                String::class.java,
                pair,
            ),
        )
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_summary_refresh_queue",
                Int::class.java,
            ),
        )
        verifyRestore(sql, company, pair, first, second)
    }

    private fun verifyRestore(
        sql: JdbcTemplate,
        company: UUID,
        pair: UUID,
        owner: UUID,
        peer: UUID,
    ) {
        for (role in listOf("summary_migration_worker", "summary_migration_reader")) {
            sql.execute(
                "create role $role login password 'summary-restore-fixture' nosuperuser nobypassrls"
            )
            sql.execute("grant usage on schema public to $role")
            sql.execute("grant select,insert,update,delete on all tables in schema public to $role")
        }
        sql.execute("grant hris_worker_capability to summary_migration_worker")
        val workerSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "summary_migration_worker",
                "summary-restore-fixture",
            )
        val worker = JdbcTemplate(workerSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(workerSource), worker)
        val actor = Actor(owner, company, emptySet(), Instant.now(), UUID.randomUUID())
        assertEquals(
            Result.Success(Unit),
            transactions.run(actor) {
                worker.update(
                    "insert into collaboration_direct_summary_records(company_id,conversation_id,owner_account_id,peer_account_id,version,peer_display_name) values(?,?,?,?,0,'Fictional summary participant')",
                    company,
                    pair,
                    owner,
                    peer,
                )
                worker.update(
                    "update collaboration_direct_summary_records set version=1,peer_display_name=null where company_id=?",
                    company,
                )
                worker.update(
                    "update collaboration_summary_refresh_queue set due_at=null where company_id=?",
                    company,
                )
                worker.queryForObject("select publish_mobile_sync_changes(200)", Int::class.java)
                Result.Success(Unit)
            },
        )
        sql.update("update accounts set display_name='Restorable summary fixture' where id=?", peer)
        fun snapshot(jdbc: JdbcTemplate) =
            listOf(
                    "collaboration_direct_conversations",
                    "collaboration_direct_summary_records",
                    "collaboration_summary_refresh_queue",
                    "collaboration_summary_account_queue",
                    "mobile_sync_heads",
                    "mobile_sync_changes",
                )
                .associateWith { table ->
                    jdbc.queryForList(
                        "select row_to_json(t)::text from $table t order by 1",
                        String::class.java,
                    )
                }
        val expected = snapshot(sql)
        fun command(vararg args: String) {
            val result = postgres.execInContainer(*args)
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
            "/tmp/summary.dump",
        )
        command("createdb", "-U", postgres.username, "summary_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "summary_restored",
            "--exit-on-error",
            "/tmp/summary.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/summary_restored"
        val restored =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        assertEquals(expected, snapshot(restored))
        assertEquals(
            3,
            restored.queryForObject(
                "select count(*) from pg_class where relname in ('collaboration_direct_summary_records','collaboration_summary_refresh_queue','collaboration_summary_account_queue') and relrowsecurity and relforcerowsecurity",
                Int::class.java,
            ),
        )
        assertEquals(
            "hris_collaboration_summary_capture",
            restored.queryForObject(
                "select pg_get_userbyid(proowner) from pg_proc where proname='collaboration_summary_account_targets'",
                String::class.java,
            ),
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            restored.update(
                "delete from collaboration_direct_summary_records where company_id=?",
                company,
            )
        }
        val readerSource =
            DriverManagerDataSource(url, "summary_migration_reader", "summary-restore-fixture")
        val reader = JdbcTemplate(readerSource)
        val reads = PostgresTransactionRunner(JdbcTransactionManager(readerSource), reader)
        for ((identity, expectedCount) in
            listOf(
                actor to 1,
                actor.copy(accountId = peer) to 0,
                actor.copy(companyId = UUID.randomUUID()) to 0,
            )) {
            assertEquals(
                Result.Success(expectedCount),
                reads.run(identity) {
                    Result.Success(
                        reader.queryForObject(
                            "select count(*) from collaboration_direct_summary_records",
                            Int::class.java,
                        )
                    )
                },
            )
        }
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            reader.queryForList(
                "select * from collaboration_summary_account_targets(?,null,null,201)",
                owner,
            )
        }
    }
}
