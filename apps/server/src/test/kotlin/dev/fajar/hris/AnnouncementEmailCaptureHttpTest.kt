package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementEmailCaptureHttpTest : AnnouncementPublicationApiFixture() {
    private fun saveEmailDraft(
        f: Fixture,
        id: UUID,
        email: Boolean?,
        version: Long? = null,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id",
            json.writeValueAsString(
                mapOf(
                    "title" to "Office update",
                    "body" to "Office information for the team.",
                    "audience" to mapOf("kind" to "COMPANY", "targetIds" to emptyList<UUID>()),
                    "acknowledgementRequired" to true,
                    "expectedVersion" to version,
                    "reason" to "Company communication",
                ) + if (email == null) emptyMap() else mapOf("emailNotification" to email)
            ),
            f.csrf,
            key,
            "PUT",
        )

    @Test
    fun emailSelectionRetainsHistoryAndLegacyEditsPreserveTheReviewedChoice() {
        val f = fixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val first = saveEmailDraft(f, id, true, key = key)
        ok(first)
        val initial = ok(get(f.browser, "${f.path}/$id"))
        assertTrue(initial.has("emailNotification"), "Announcement must expose its email selection")
        assertTrue(initial["emailNotification"].asBoolean())
        ok(saveEmailDraft(f, id, null, 0))
        assertTrue(ok(get(f.browser, "${f.path}/$id"))["emailNotification"].asBoolean())
        ok(saveEmailDraft(f, id, false, 1))
        assertFalse(ok(get(f.browser, "${f.path}/$id"))["emailNotification"].asBoolean())
        assertTrue(ok(get(f.browser, "${f.path}/$id/revisions/0"))["emailNotification"].asBoolean())
        assertTrue(ok(get(f.browser, "${f.path}/$id/revisions/1"))["emailNotification"].asBoolean())
        assertEquals(first.body(), saveEmailDraft(f, id, true, key = key).body())
        assertEquals(409, saveEmailDraft(f, id, false, key = key).statusCode())
        assertEquals(2, ok(get(f.browser, "${f.path}/$id"))["version"].asInt())
    }

    @Test
    fun publicationAndEmailCaptureRollbackTogetherAndInboxActionsNeverRequeueMail() {
        val f = fixture()
        val first = member(f)
        member(f)
        val id = UUID.randomUUID()
        ok(saveEmailDraft(f, id, true))
        val publicationKey = UUID.randomUUID()
        val queued = publish(f, id, key = publicationKey)
        ok(queued)
        val lease = claim().single()
        publicationProbe.afterInbox = {
            runtimeDatabase.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        }
        failure(advance.execute(f.actor, lease), "job_lease_lost")
        assertEquals(0, count(f, "inbox_email_dispatches"))
        assertEquals(0, count(f, "inbox_items"))
        publicationProbe.clear()
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertEquals(2, count(f, "inbox_email_dispatches"))
        assertEquals(queued.body(), publish(f, id, key = publicationKey).body())
        val item = inbox(f, first).single()
        val inboxId = UUID.fromString(item["id"].asString())
        assertTrue(item["readAt"].isNull)
        assertTrue(item["acknowledgedAt"].isNull)
        ok(inboxAction(f, first, inboxId, "read", 0))
        ok(inboxAction(f, first, inboxId, "acknowledge", 1))
        assertEquals(2, count(f, "inbox_email_dispatches"))
        assertEquals(2, count(f, "inbox_push_dispatches"))
        assertEquals(2, count(f, "inbox_items"))
    }

    @Test
    fun existingClientsDefaultToNoEmailAndTheirOriginalReceiptsStillReplay() {
        val f = fixture()
        member(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val first = saveEmailDraft(f, id, null, key = key)
        ok(first)
        assertFalse(ok(get(f.browser, "${f.path}/$id"))["emailNotification"].asBoolean())
        ok(saveEmailDraft(f, id, true, 0))
        assertEquals(first.body(), saveEmailDraft(f, id, null, key = key).body())
        val other = draft(f)
        published(f, other)
        assertEquals(0, count(f, "inbox_email_dispatches"))
        assertEquals(1, count(f, "inbox_items"))
    }

    @Test
    fun emailQueueIsPrivateAndOnlyPublicationCanCaptureNewDeliveries() {
        val f = fixture()
        val first = member(f)
        val second = member(f)
        val id = UUID.randomUUID()
        ok(saveEmailDraft(f, id, true))
        published(f, id)
        val owner = Actor(first.account, f.company, emptySet(), clock.instant(), UUID.randomUUID())
        fun visible(actor: Actor, table: String) =
            transactions.run(actor) {
                Result.Success(
                    runtimeDatabase.queryForObject("select count(*) from $table", Int::class.java)
                )
            }
        assertEquals(Result.Success(1), visible(owner, "inbox_email_dispatches"))
        assertEquals(
            Result.Success(0),
            visible(owner.copy(companyId = UUID.randomUUID()), "inbox_email_dispatches"),
        )
        assertEquals(
            Result.Success(0),
            visible(owner.copy(accountId = UUID.randomUUID()), "inbox_email_dispatches"),
        )
        assertEquals(Result.Success(0), visible(owner, "announcement_email_delivery"))
        assertEquals(Result.Success(2), visible(f.actor, "announcement_email_delivery"))
        failure(
            transactions.run(owner) {
                Result.Success(
                    runtimeDatabase.queryForList(
                        "select * from claim_inbox_email(?,1,120,8)",
                        UUID.randomUUID(),
                    )
                )
            },
            "access_denied",
        )
        assertEquals(
            Result.Success(0),
            transactions.run(owner) {
                Result.Success(
                    runtimeDatabase.update(
                        "update inbox_email_dispatches set state='FAILED',finished_at=now()"
                    )
                )
            },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(f.actor) {
                Result.Success(
                    runtimeDatabase.update("update announcement_email_delivery set state='FAILED'")
                )
            },
        )
        val own = UUID.fromString(inbox(f, first).single()["id"].asString())
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update inbox_email_dispatches set account_id=? where inbox_id=?",
                    second.account,
                    own,
                )
        }
        assertThrows(Exception::class.java) {
            database().update("update inbox_email_dispatches set attempts=1 where inbox_id=?", own)
        }
        val legacy = draft(f)
        published(f, legacy)
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "insert into inbox_email_dispatches(company_id,inbox_id,account_id,published_at) select company_id,id,owner_account_id,delivered_at from inbox_items where announcement_id=?",
                    legacy,
                )
        }
        assertEquals(2, count(f, "inbox_email_dispatches"))
        val observations = ok(get(f.browser, "${f.path}/$id/delivery"))["email"]
        assertEquals(
            setOf(
                "pending",
                "processing",
                "accepted",
                "failed",
                "superseded",
                "unavailableRecipients",
            ),
            observations.propertyNames(),
        )
        assertEquals(2, observations["pending"].asInt())
        assertEquals(0, observations["accepted"].asInt())
        assertTrue(ok(get(f.browser, "${f.path}/$legacy/delivery"))["email"].isNull)
    }
}
