package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InboxPushCaptureHttpTest : AnnouncementPublicationApiFixture() {
    @Test
    fun captureIsAtomicAndReadUpdatesDoNotCreateAnotherDispatch() {
        val f = fixture()
        val first = member(f)
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        publicationProbe.afterInbox = {
            runtimeDatabase.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        }
        failure(advance.execute(f.actor, lease), "job_lease_lost")
        assertEquals(0, count(f, "inbox_push_dispatches"))
        assertEquals(0, count(f, "inbox_items"))
        publicationProbe.clear()
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertEquals(2, count(f, "inbox_push_dispatches"))
        val item = inbox(f, first).single()
        val inboxId = UUID.fromString(item["id"].asString())
        ok(inboxAction(f, first, inboxId, "read", 0))
        assertEquals(2, count(f, "inbox_push_dispatches"))
        assertEquals(
            "PENDING",
            database()
                .queryForObject(
                    "select state from inbox_push_dispatches where inbox_id=?",
                    String::class.java,
                    inboxId,
                ),
        )
    }

    @Test
    fun applicationCredentialsCannotClaimOrMutateWorkerQueueAndOwnersCannotReadOtherPartitions() {
        val f = fixture()
        val first = member(f)
        val second = member(f)
        published(f, draft(f))
        val firstActor =
            Actor(first.account, f.company, emptySet(), clock.instant(), UUID.randomUUID())
        assertEquals(
            Result.Success(1),
            transactions.run(firstActor) {
                Result.Success(
                    runtimeDatabase.queryForObject(
                        "select count(*) from inbox_push_dispatches",
                        Int::class.java,
                    )
                )
            },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(firstActor.copy(accountId = UUID.randomUUID())) {
                Result.Success(
                    runtimeDatabase.queryForObject(
                        "select count(*) from inbox_push_dispatches",
                        Int::class.java,
                    )
                )
            },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(firstActor.copy(companyId = UUID.randomUUID())) {
                Result.Success(
                    runtimeDatabase.queryForObject(
                        "select count(*) from inbox_push_dispatches",
                        Int::class.java,
                    )
                )
            },
        )
        val claimed =
            transactions.run(firstActor) {
                Result.Success(
                    runtimeDatabase.queryForList(
                        "select * from claim_inbox_push(?,1,120,8)",
                        UUID.randomUUID(),
                    )
                )
            }
        failure(claimed, "access_denied")
        assertEquals(
            Result.Success(0),
            transactions.run(firstActor) {
                Result.Success(
                    runtimeDatabase.update(
                        "update inbox_push_dispatches set state='FAILED',finished_at=now() where company_id=?",
                        f.company,
                    )
                )
            },
        )
        val own = UUID.fromString(inbox(f, first).single()["id"].asString())
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update inbox_push_dispatches set account_id=? where inbox_id=?",
                    second.account,
                    own,
                )
        }
        assertThrows(Exception::class.java) {
            database()
                .update("update inbox_push_dispatches set processed_count=1 where inbox_id=?", own)
        }
        assertEquals(2, count(f, "inbox_push_dispatches"))
    }
}
