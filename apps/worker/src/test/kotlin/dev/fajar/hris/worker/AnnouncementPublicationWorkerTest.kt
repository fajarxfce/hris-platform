package dev.fajar.hris.worker

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementPublicationWorkerTest : CommunicationsWorkerFixture() {
    @Test
    fun springBatchPublishesOneImmutableInboxUsingRestrictedWorkerCredentials() {
        val actor = fixture()
        val lease = queue(actor)
        assertEquals(Result.Success(Unit), executor.execute(lease, task))
        val db = database()
        assertEquals(
            "SUCCEEDED",
            db.queryForObject(
                "select status from background_jobs where id=?",
                String::class.java,
                lease.job.request.id,
            ),
        )
        assertEquals(
            1,
            db.queryForObject(
                "select count(*) from inbox_items where company_id=?",
                Int::class.java,
                actor.companyId,
            ),
        )
        assertEquals(
            1,
            db.queryForObject(
                "select count(*) from announcement_publications where company_id=?",
                Int::class.java,
                actor.companyId,
            ),
        )
        val stale = task.advance(lease)
        assertEquals("job_lease_lost", (stale as Result.Failed).failure.code)
    }

    @Test
    fun credentialRevocationStopsExecutionAndCleanupDoesNotRequireRevokedPermission() {
        val actor = fixture()
        val lease = queue(actor)
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        val failed = executor.execute(lease, task)
        assertTrue(failed is Result.Failed, failed.toString())
        assertEquals(Result.Success(Unit), task.abort(lease, (failed as Result.Failed).failure))
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    lease.job.request.id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from announcement_publications where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
    }
}
