package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementPublicationHttpTest : AnnouncementPublicationApiFixture() {
    @Test
    fun publicationFreezesContentAndCommitsRecipientsWithTheJobExactlyOnce() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        val key = UUID.randomUUID()
        val queued = publish(f, id, key = key)
        ok(queued)
        assertEquals(queued.body(), publish(f, id, key = key).body())
        assertEquals(1, count(f, "background_jobs"))
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
        assertEquals("SUCCEEDED", job(f, lease.job.request.id)["status"].asString())
        assertEquals(1, job(f, lease.job.request.id)["completedItems"].asInt())
        val announcement = view(f, id)
        assertEquals("PUBLISHED", announcement["status"].asString())
        assertEquals(2, announcement["version"].asInt())
        assertEquals(1, announcement["recipientCount"].asInt())
        assertEquals(1, count(f, "announcement_publications"))
        assertEquals(1, count(f, "inbox_items"))
        assertEquals(queued.body(), publish(f, id, key = key).body())
        failure(advance.execute(f.actor, lease), "job_lease_lost")
        assertEquals(1, count(f, "inbox_items"))
        val summary = inbox(f, recipient).single()
        assertFalse(summary.has("body"))
        val item = ok(get(recipient.browser, "${f.inbox}/${summary["id"].asString()}"))
        assertEquals("Office information for the team.", item["body"].asString())
        assertTrue(item["readAt"].isNull)
        assertEquals(0, item["version"].asInt())
        val frozen = ok(get(f.browser, "${f.path}/$id/revisions/1"))
        assertEquals("QUEUED", frozen["status"].asString())
        assertEquals(item["body"], frozen["body"])
    }

    @Test
    fun aGroupUsesItsPublicationTimeMembershipAndDoesNotGrantHistoricMailToLaterMembers() {
        val f = fixture()
        val previous = member(f)
        val current = member(f)
        val next = member(f)
        val group = group(f, members = listOf(previous.employment))
        val id = draft(f, "GROUP", listOf(group))
        ok(publish(f, id))
        group(f, group, listOf(current.employment), 0)
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
        assertEquals(0, inbox(f, previous).size())
        assertEquals(1, inbox(f, current).size())
        assertEquals(
            "1",
            database()
                .queryForObject(
                    "select audience_versions->>? from announcement_publications where company_id=?",
                    String::class.java,
                    group.toString(),
                    f.company,
                ),
        )
        group(f, group, listOf(next.employment), 1)
        assertEquals(1, inbox(f, current).size())
        assertEquals(0, inbox(f, next).size())
    }

    @Test
    fun inactiveAccountsMembershipsAndMissingReadGrantsNeverReceivePublication() {
        val f = fixture()
        val eligible = member(f)
        val inactiveAccount = member(f)
        val inactiveMembership = member(f)
        val unprivileged = member(f, emptyList())
        val ended = member(f)
        database().update("update accounts set active=false where id=?", inactiveAccount.account)
        database()
            .update(
                "update company_memberships set active=false where company_id=? and account_id=?",
                f.company,
                inactiveMembership.account,
            )
        ok(
            revise(
                f.browser,
                f.csrf,
                f.company,
                ended.employment,
                0,
                terms("2026-09-01", status = "ENDED") + mapOf("endDate" to "2026-09-30"),
            )
        )
        published(f, draft(f))
        assertEquals(1, count(f, "inbox_items"))
        assertEquals(
            eligible.account,
            database()
                .queryForObject(
                    "select owner_account_id from inbox_items where company_id=?",
                    UUID::class.java,
                    f.company,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where company_id=? and owner_account_id=?",
                    Int::class.java,
                    f.company,
                    unprivileged.account,
                ),
        )
    }

    @Test
    fun emptyOrArchivedAudiencesFailWithoutPublishingAndRequireExplicitRecovery() {
        val f = fixture()
        val group = group(f, members = emptyList())
        val id = draft(f, "GROUP", listOf(group))
        ok(publish(f, id))
        val emptyLease = claim().single()
        val empty = advance.execute(f.actor, emptyLease)
        failure(empty, "announcement_audience_empty")
        assertEquals(
            Result.Success(Unit),
            abort.execute(emptyLease, (empty as Result.Failed).failure),
        )
        assertEquals("FAILED", job(f, emptyLease.job.request.id)["status"].asString())
        assertEquals("QUEUED", view(f, id)["status"].asString())
        val restored = ok(action(f, id, "return-to-draft", 1))
        assertEquals(2, restored["version"].asInt())
        val recipient = member(f)
        group(f, group, listOf(recipient.employment), 0)
        ok(publish(f, id, 2))
        group(f, group, listOf(recipient.employment), 1, active = false)
        val archived = advance.execute(f.actor, claim().single())
        assertTrue(archived is Result.Failed)
        assertEquals(0, count(f, "announcement_publications"))
        assertEquals(0, count(f, "inbox_items"))
    }

    @Test
    fun futurePublicationIsNotLeasedUntilDueAndCancellationWakesCleanup() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val f = fixture()
        member(f)
        val id = draft(f)
        val scheduledFor = clock.instant().plusSeconds(3600)
        val key = UUID.randomUUID()
        val queued = publish(f, id, scheduledFor = scheduledFor, key = key)
        ok(queued)
        val jobId = UUID.fromString(view(f, id)["publicationJobId"].asString())
        assertEquals(scheduledFor, Instant.parse(job(f, jobId)["scheduledFor"].asString()))
        assertEquals(emptyList<Any>(), claim())
        error(action(f, id, "return-to-draft", 1), 409, "announcement_publication_not_stopped")
        error(action(f, id, "archive", 1), 409, "announcement_publication_active")
        cancel(f, jobId)
        val lease = claim().single()
        failure(advance.execute(f.actor, lease), "job_cancellation_requested")
        assertEquals(
            Result.Success(Unit),
            abort.execute(lease, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
        assertEquals("CANCELLED", job(f, jobId)["status"].asString())
        ok(action(f, id, "return-to-draft", 1))
        ok(publish(f, id, 2))
        val replacement = claim().single()
        assertNotEquals(lease.job.request.id, replacement.job.request.id)
        assertEquals(
            Result.Success(Unit),
            abort.execute(lease, Failure(FailureKind.UNEXPECTED, "fixture_late_failure")),
        )
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, replacement))
        clock.set(scheduledFor.plusSeconds(1))
        assertEquals(
            Result.Success(MutationReceipt(id, 1, replayed = true)),
            queue.execute(f.actor, key, id, 0, scheduledFor, "Publish office information"),
        )
        assertEquals(1, count(f, "announcement_publications"))
    }

    @Test
    fun competingPublicationCommandsCreateOnlyOneJob() {
        val f = fixture()
        member(f)
        val id = draft(f)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val actions =
                (1..2).map {
                    executor.submit<Int> {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        publish(f, id).statusCode()
                    }
                }
            start.countDown()
            assertEquals(listOf(200, 409), actions.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(1, count(f, "background_jobs"))
        assertEquals(2, count(f, "announcement_revisions"))
    }

    @Test
    fun accessIsRecheckedAfterAPendingAccountGuardAndOriginalCredentialsAreRequired() {
        val f = fixture()
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        failure(
            advance.execute(
                f.actor.copy(credentialVersion = f.actor.credentialVersion!! + 1),
                lease,
            ),
            "job_scope_mismatch",
        )
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<JobStep>> { advance.execute(f.actor, lease) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.manage'",
                        f.company,
                        f.actor.accountId,
                    )
            } finally {
                barrier.release.countDown()
            }
            failure(pending.get(15, TimeUnit.SECONDS), "access_denied")
        }
        assertEquals(0, count(f, "inbox_items"))
        assertEquals(0, count(f, "announcement_publications"))
    }

    @Test
    fun overlappingEmploymentMatchesProduceOneInboxPerAccount() {
        val f = fixture()
        val recipient = member(f)
        val second = UUID.randomUUID()
        // A historical/imported second employment must not duplicate account delivery.
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) select company_id,?,person_id,? from employments where company_id=? and id=?",
                second,
                "D${second.toString().take(8)}",
                f.company,
                recipient.employment,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE',?,'Second historical employment')",
                f.company,
                second,
                f.actor.accountId,
            )
        published(f, draft(f))
        assertEquals(1, count(f, "inbox_items"))
        val representative = listOf(recipient.employment, second).minBy { it.toString() }
        assertEquals(
            representative,
            database()
                .queryForObject(
                    "select employment_id from inbox_items where company_id=?",
                    UUID::class.java,
                    f.company,
                ),
        )
    }
}
