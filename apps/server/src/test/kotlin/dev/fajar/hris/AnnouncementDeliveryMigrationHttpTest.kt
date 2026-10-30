package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["spring.flyway.target=100"])
@Import(LegacyAnnouncementStorageConfiguration::class)
class AnnouncementDeliveryMigrationHttpTest : AnnouncementPublicationApiFixture() {
    private fun publishFixture(f: Fixture): UUID {
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        assertTrue(advance.execute(f.actor, claim().single()) is Result.Success)
        return id
    }

    @Test
    fun backfillWaitsForAnExistingDispatchWriterAndPreservesPublicationAndRecipientEvidence() {
        val first = fixture()
        val firstId = publishFixture(first)
        val second = fixture()
        val secondId = publishFixture(second)
        val tables =
            listOf(
                "announcement_heads",
                "announcement_revisions",
                "announcement_publications",
                "inbox_items",
            )
        fun facts() =
            tables.associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text as evidence from $table t order by to_jsonb(t)::text"
                    )
            }
        val before = facts()
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val migrationSource =
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
                connectionProperties =
                    Properties().apply {
                        setProperty("ApplicationName", "announcement-delivery-upgrade")
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val writer =
                        JdbcTemplate(SingleConnectionDataSource(connection, true)).apply {
                            queryTimeout = 5
                        }
                    writer.update(
                        "update inbox_push_dispatches set state='FAILED',failure_code='push_delivery_expired',finished_at=clock_timestamp() where company_id=?",
                        first.company,
                    )
                    val writerPid =
                        writer.queryForObject("select pg_backend_pid()", Int::class.java)
                    val migration =
                        pool.submit {
                            Flyway.configure()
                                .dataSource(migrationSource)
                                .target("101")
                                .load()
                                .migrate()
                        }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (
                        database()
                            .queryForObject(
                                "select exists(select 1 from pg_stat_activity where application_name='announcement-delivery-upgrade' and wait_event_type='Lock' and ?=any(pg_blocking_pids(pid)))",
                                Boolean::class.java,
                                writerPid,
                            ) != true
                    ) {
                        check(!migration.isDone && System.nanoTime() < deadline) {
                            "Migration did not wait for its existing dispatch writer"
                        }
                        Thread.sleep(10)
                    }
                    connection.commit()
                    migration.get(20, TimeUnit.SECONDS)
                } finally {
                    connection.rollback()
                }
            }
        }
        assertEquals(before, facts())
        assertEquals(1, ok(get(first.browser, "${first.path}/$firstId/delivery"))["failed"].asInt())
        assertEquals(
            1,
            ok(get(second.browser, "${second.path}/$secondId/delivery"))["pending"].asInt(),
        )
        error(
            get(second.browser, "${second.path}/$firstId/delivery"),
            404,
            "announcement_not_found",
        )
        val expected =
            database()
                .queryForList(
                    "select d.company_id,i.announcement_id,d.inbox_id,d.state,d.processed_count,d.accepted_count,d.rejected_count from inbox_push_dispatches d join inbox_items i on i.company_id=d.company_id and i.id=d.inbox_id order by d.company_id,d.inbox_id"
                )
        assertEquals(
            expected,
            database()
                .queryForList(
                    "select company_id,announcement_id,inbox_id,state,processed_count,accepted_count,rejected_count from announcement_push_delivery order by company_id,inbox_id"
                ),
        )
        val third = fixture()
        val thirdId = publishFixture(third)
        assertEquals(
            1,
            ok(get(third.browser, "${third.path}/$thirdId/delivery"))["pending"].asInt(),
        )
        database().update("delete from inbox_push_dispatches where company_id=?", first.company)
        assertEquals(
            1,
            ok(get(first.browser, "${first.path}/$firstId/delivery"))["unavailableRecipients"]
                .asInt(),
        )
        assertEquals(
            1,
            ok(get(second.browser, "${second.path}/$secondId/delivery"))["pending"].asInt(),
        )
    }
}
