package dev.fajar.hris

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["spring.flyway.target=101"])
@Import(LegacyAnnouncementStorageConfiguration::class)
class AnnouncementEmailMigrationHttpTest : AnnouncementPublicationApiFixture() {
    @Test
    fun upgradeDefaultsOldRevisionsToNoEmailAndNeverBackfillsAlreadyPublishedInbox() {
        val f = fixture()
        member(f)
        val id = draft(f)
        published(f, id)
        fun facts() =
            listOf(
                    "announcement_heads",
                    "announcement_revisions",
                    "announcement_publications",
                    "inbox_items",
                    "inbox_push_dispatches",
                )
                .associateWith { table ->
                    database()
                        .queryForList(
                            "select (to_jsonb(t)-'email_notification')::text as evidence from $table t order by (to_jsonb(t)-'email_notification')::text"
                        )
                }
        val before = facts()
        val migration =
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .target("102")
                .load()
        assertEquals(1, migration.migrate().migrationsExecuted)
        assertEquals(before, facts())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from announcement_revisions where email_notification",
                    Int::class.java,
                ),
        )
        assertEquals(0, count(f, "inbox_email_dispatches"))
        assertEquals(0, count(f, "announcement_email_delivery"))
        assertFalse(ok(get(f.browser, "${f.path}/$id"))["emailNotification"].asBoolean())
        assertTrue(ok(get(f.browser, "${f.path}/$id/delivery"))["email"].isNull)
        assertEquals(0, migration.migrate().migrationsExecuted)
        assertEquals(before, facts())
    }
}
