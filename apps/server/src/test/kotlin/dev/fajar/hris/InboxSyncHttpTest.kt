package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.SyncRepository
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class InboxSyncHttpTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var sync: SyncRepository
    private lateinit var publisher: MobileSyncTestWorker

    @BeforeEach
    fun preparePublisher() {
        publisher = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(30) {
            val done = publisher.maintain.execute()
            assertTrue(done is Result.Success, done.toString())
            val count = (done as Result.Success).value
            if (count.published == 0 && count.pruned == 0) return
        }
        fail<Unit>("Fixture sync cleanup exceeded its finite budget")
    }

    private fun bootstrap(f: Fixture, recipient: Member, collections: String? = "INBOX") =
        get(
            recipient.browser,
            "/api/v1/companies/${f.company}/sync/bootstrap" +
                (collections?.let { "?collections=$it" } ?: ""),
        )

    private fun changes(
        f: Fixture,
        recipient: Member,
        token: String,
        collections: String? = "INBOX",
    ) =
        get(
            recipient.browser,
            "/api/v1/companies/${f.company}/sync/changes?cursor=${URLEncoder.encode(token, Charsets.UTF_8)}" +
                (collections?.let { "&collections=$it" } ?: ""),
        )

    private fun publishChanges() {
        val result = publisher.maintain.execute()
        assertTrue(result is Result.Success, result.toString())
    }

    private fun rows(page: JsonNode) = page["items"].iterator().asSequence().toList()

    @Test
    fun bootstrapAndChangesCarryVersionedReadStateAndArchivalTombstones() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        published(f, id)
        val itemId = inbox(f, recipient).single()["id"].asString()
        val initial = ok(bootstrap(f, recipient))
        assertEquals(
            listOf("INBOX"),
            initial["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(itemId, initial["items"].single()["id"].asString())
        val firstCursor = initial["changesCursor"].asString()
        val pending = ok(changes(f, recipient, firstCursor))
        assertEquals(0, pending["items"].size())
        assertTrue(pending["pendingPublication"].asBoolean())
        assertTrue(pending["pollAfterSeconds"].asInt() > 0)
        publishChanges()
        val delivered = ok(changes(f, recipient, firstCursor))
        assertEquals("UPSERT", delivered["items"].single()["operation"].asString())
        assertEquals(0, delivered["items"].single()["version"].asInt())
        assertFalse(delivered["items"].single().has("body"))
        assertFalse(delivered["items"].single().has("title"))
        val inboxId = UUID.fromString(itemId)
        ok(inboxAction(f, recipient, inboxId, "read", 0))
        publishChanges()
        val read = ok(changes(f, recipient, delivered["cursor"].asString()))
        assertEquals(1, read["items"].single()["version"].asInt())
        ok(inboxAction(f, recipient, inboxId, "acknowledge", 1))
        ok(action(f, id, "archive", 2))
        publishChanges()
        val archived = ok(changes(f, recipient, read["cursor"].asString()))
        assertEquals(listOf(2, 3), rows(archived).map { it["version"].asInt() })
        assertEquals(listOf("UPSERT", "DELETE"), rows(archived).map { it["operation"].asString() })
        assertEquals(rows(archived), rows(ok(changes(f, recipient, read["cursor"].asString()))))
        assertEquals(0, ok(bootstrap(f, recipient))["items"].size())
        error(get(recipient.browser, "${f.inbox}/$itemId"), 404, "inbox_item_not_found")
        assertEquals(
            4,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=? and collection='INBOX' and employment_id is null and owner_account_id=?",
                    Int::class.java,
                    f.company,
                    recipient.account,
                ),
        )
    }

    @Test
    fun omittedCollectionSelectionKeepsExistingCursorsAndProjectionsCompatible() {
        val f = fixture()
        val recipient = member(f, listOf("announcements.read", "expenses.self.manage"))
        val original = ok(bootstrap(f, recipient, null))
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            original["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val cursor = original["changesCursor"].asString()
        published(f, draft(f))
        val pending = ok(changes(f, recipient, cursor, null))
        assertFalse(pending["pendingPublication"].asBoolean())
        publishChanges()
        assertEquals(0, ok(changes(f, recipient, cursor, null))["items"].size())
        assertEquals(0, ok(changes(f, recipient, cursor, "EXPENSE_CLAIMS"))["items"].size())
        error(changes(f, recipient, cursor, "INBOX"), 409, "sync_scope_changed")
        assertEquals(1, ok(bootstrap(f, recipient))["items"].size())
        val unsupported = member(f, listOf("announcements.manage"))
        error(bootstrap(f, unsupported), 403, "sync_access_denied")
        val inboxOnly = member(f)
        error(bootstrap(f, inboxOnly, null), 403, "sync_access_denied")
    }

    @Test
    fun groupChangesDoNotTransferHistoricInboxAndRevocationStopsOldCursors() {
        val f = fixture()
        val original = member(f)
        val next = member(f)
        val group = group(f, members = listOf(original.employment))
        val initialNext = ok(bootstrap(f, next))["changesCursor"].asString()
        published(f, draft(f, "GROUP", listOf(group)))
        val cursor = ok(bootstrap(f, original))["changesCursor"].asString()
        group(f, group, listOf(next.employment), 0)
        publishChanges()
        assertEquals(1, ok(bootstrap(f, original))["items"].size())
        assertEquals(0, ok(bootstrap(f, next))["items"].size())
        assertEquals(0, ok(changes(f, next, initialNext))["items"].size())
        val otherCompany = fixture()
        error(changes(otherCompany, original, cursor), 403, "company_access_denied")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.read'",
                f.company,
                original.account,
            )
        database()
            .update(
                "update company_memberships set version=version+1 where company_id=? and account_id=?",
                f.company,
                original.account,
            )
        error(changes(f, original, cursor), 403, "sync_access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'announcements.read')",
                f.company,
                original.account,
            )
        error(changes(f, original, cursor), 409, "sync_scope_changed")
        assertEquals(1, ok(bootstrap(f, original))["items"].size())
    }

    @Test
    fun aLatePublicationFailureCannotLeavePendingSyncReferences() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        val initial = ok(bootstrap(f, recipient))["changesCursor"].asString()
        ok(publish(f, id))
        val lease = claim().single()
        publicationProbe.afterInbox = {
            runtimeDatabase.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        }
        failure(advance.execute(f.actor, lease), "job_lease_lost")
        assertEquals(0, count(f, "mobile_sync_changes"))
        assertEquals(0, ok(bootstrap(f, recipient))["items"].size())
        assertFalse(ok(changes(f, recipient, initial))["pendingPublication"].asBoolean())
        publicationProbe.clear()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
        assertEquals(1, count(f, "mobile_sync_changes"))
        publishChanges()
        assertEquals(1, ok(changes(f, recipient, initial))["items"].size())
    }

    @Test
    fun employmentSelectionCannotReadAnotherAccountsInboxMetadata() {
        val f = fixture()
        val owner = member(f)
        val other = member(f)
        val group = group(f, members = listOf(owner.employment))
        published(f, draft(f, "GROUP", listOf(group)))
        val scope =
            SyncScope(
                other.account,
                f.company,
                0,
                0,
                0,
                setOf("announcements.read"),
                setOf(owner.employment),
                setOf(SyncCollection.INBOX),
            )
        val actor =
            f.actor.copy(accountId = other.account, permissions = setOf("announcements.read"))
        assertEquals(
            Result.Success(emptyList<SyncResource>()),
            transactions.run(actor) { sync.snapshot(scope, null, 100) },
        )
        assertEquals(Result.Success(false), transactions.run(actor) { sync.pending(scope) })
        publishChanges()
        assertEquals(
            Result.Success(emptyList<SyncChange>()),
            transactions.run(actor) { sync.changes(scope, 0, Long.MAX_VALUE, 100) },
        )
    }

    @Test
    fun databaseRequiresAccountOwnershipForInboxAndPreservesOtherCollectionConstraints() {
        val f = fixture()
        val recipient = member(f)
        val id = UUID.randomUUID()
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,owner_account_id,resource_version,operation) values(?,'INBOX',?,?,?,0,'UPSERT')",
                    f.company,
                    id,
                    recipient.employment,
                    recipient.account,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into mobile_sync_changes(company_id,collection,resource_id,resource_version,operation) values(?,'INBOX',?,0,'UPSERT')",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into mobile_sync_changes(company_id,collection,resource_id,resource_version,operation) values(?,'EXPENSE_CLAIMS',?,0,'UPSERT')",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,owner_account_id,resource_version,operation) values(?,'EXPENSE_CLAIMS',?,?,?,0,'UPSERT')",
                    f.company,
                    id,
                    recipient.employment,
                    recipient.account,
                )
        }
        assertEquals(0, count(f, "mobile_sync_changes"))
    }
}
