package dev.fajar.hris

import dev.fajar.hris.communications.domain.usecases.GetAnnouncementReview
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AnnouncementReviewHttpTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var review: GetAnnouncementReview

    private fun read(f: Fixture, id: UUID) =
        get(f.browser, f.path + "/" + id + "/publication-review")

    @Test
    fun currentDraftReviewIsReadOnlyAndDoesNotCreateARecipientPreview() {
        val f = fixture()
        val id = draft(f)
        val audits = count(f, "audit_entries")
        val result = ok(read(f, id))
        assertEquals(id.toString(), result["announcement"]["id"].asString())
        assertEquals(0, result["announcement"]["version"].asInt())
        assertTrue(result["publicationJob"].isNull)
        assertEquals(
            setOf("EDIT", "PREVIEW", "PUBLISH", "ARCHIVE"),
            result["availableActions"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertFalse(result.has("recipients"))
        assertEquals(audits, count(f, "audit_entries"))
        assertEquals(0, count(f, "background_jobs"))
        assertEquals(1, count(f, "announcement_revisions"))
    }

    @Test
    fun managersSeeLinkedPublicationStateWithoutGeneralJobAuthorityOrPrivateParameters() {
        val f = fixture()
        val manager = member(f, listOf("announcements.manage"))
        val id = draft(f)
        ok(publish(f, id, scheduledFor = clock.instant().plusSeconds(900)))
        val result = ok(get(manager.browser, f.path + "/" + id + "/publication-review"))
        assertEquals(
            setOf("PREVIEW"),
            result["availableActions"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        val job = result["publicationJob"]
        assertEquals("QUEUED", job["status"].asString())
        assertEquals(
            setOf("id", "status", "cancellationRequested", "version", "failureCode"),
            job.properties().map { it.key }.toSet(),
        )
        error(
            get(
                manager.browser,
                "/api/v1/companies/" + f.company + "/jobs/" + job["id"].asString(),
            ),
            404,
            "job_not_found",
        )
        assertTrue(
            ok(read(f, id))["availableActions"].iterator().asSequence().any {
                it.asString() == "VIEW_JOB"
            }
        )
    }

    @Test
    fun failedPublicationExposesRecoveryAndArchivedHistoryCannotBeRepublished() {
        val f = fixture()
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val result = advance.execute(f.actor, lease)
        failure(result, "announcement_audience_empty")
        assertEquals(Result.Success(Unit), abort.execute(lease, (result as Result.Failed).failure))
        val stopped = ok(read(f, id))
        assertEquals("FAILED", stopped["publicationJob"]["status"].asString())
        assertEquals(
            "announcement_audience_empty",
            stopped["publicationJob"]["failureCode"].asString(),
        )
        assertTrue(
            stopped["availableActions"].iterator().asSequence().any {
                it.asString() == "RETURN_TO_DRAFT"
            }
        )
        ok(action(f, id, "return-to-draft", 1))
        val draft = ok(read(f, id))
        assertTrue(draft["publicationJob"].isNull)
        assertEquals(1, draft["announcement"]["publicationAttempts"].asInt())
        ok(action(f, id, "archive", 2))
        assertTrue(ok(read(f, id))["availableActions"].isEmpty)
    }

    @Test
    fun companyIsolationAndCurrentManagementPermissionApplyToTheReview() {
        val f = fixture()
        val id = draft(f)
        val recipient = member(f)
        error(
            get(recipient.browser, f.path + "/" + id + "/publication-review"),
            403,
            "access_denied",
        )
        error(read(fixture(), id), 404, "announcement_not_found")
    }

    @Test
    fun jobReadsDoNotWaitForAJobWriterWhileHoldingAnnouncementAndAccessGuards() {
        val f = fixture()
        val id = draft(f)
        ok(publish(f, id, scheduledFor = clock.instant().plusSeconds(900)))
        val job = UUID.fromString(ok(read(f, id))["publicationJob"]["id"].asString())
        requireNotNull(database().dataSource).connection.use { connection ->
            connection.autoCommit = false
            try {
                connection
                    .prepareStatement("select id from background_jobs where id=? for update")
                    .use { statement ->
                        statement.setObject(1, job)
                        statement.executeQuery().use { rows -> assertTrue(rows.next()) }
                    }
                Executors.newSingleThreadExecutor().use { executor ->
                    val response = executor.submit<Result<*>> { review.execute(f.actor, id) }
                    assertTrue(response.get(5, TimeUnit.SECONDS) is Result.Success)
                }
            } finally {
                connection.rollback()
            }
        }
    }

    @Test
    fun credentialsAreRevalidatedAfterAWaitingAccountGuard() {
        val f = fixture()
        val id = draft(f)
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<*>> { review.execute(f.actor, id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        f.actor.accountId,
                    )
            } finally {
                barrier.release.countDown()
            }
            failure(pending.get(15, TimeUnit.SECONDS), "session_revoked")
        }
    }
}
