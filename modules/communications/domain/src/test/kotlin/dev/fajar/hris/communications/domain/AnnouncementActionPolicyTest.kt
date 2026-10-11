package dev.fajar.hris.communications.domain

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.availableAnnouncementActions
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnouncementActionPolicyTest {
    private val now = Instant.parse("2026-10-11T00:00:00Z")
    private val company = UUID.randomUUID()
    private val actor =
        Actor(UUID.randomUUID(), company, setOf("announcements.manage"), now, UUID.randomUUID())
    private val announcement =
        Announcement(
            UUID.randomUUID(),
            0,
            "Office hours",
            "Updated office hours.",
            AnnouncementAudience(AudienceKind.COMPANY),
            false,
            AnnouncementStatus.DRAFT,
            now,
            actor.accountId,
            "Office update",
        )

    private fun job(status: JobStatus) =
        BackgroundJob(
            JobRequest(
                UUID.randomUUID(),
                company,
                actor.accountId,
                JobKind.ANNOUNCEMENT_PUBLISH,
                UUID.randomUUID(),
                mapOf("announcementId" to announcement.id.toString()),
                now,
                0,
                UUID.randomUUID(),
                now,
                1,
            ),
            status,
            0,
            false,
            0,
            emptyMap(),
            null,
            null,
            0,
        )

    @Test
    fun draftsReserveRevisionAndAttemptCapacity() {
        assertEquals(
            setOf(
                AnnouncementAction.EDIT,
                AnnouncementAction.PREVIEW,
                AnnouncementAction.PUBLISH,
                AnnouncementAction.ARCHIVE,
            ),
            availableAnnouncementActions(actor, announcement, null),
        )
        val boundary = availableAnnouncementActions(actor, announcement.copy(version = 996), null)
        assertFalse(AnnouncementAction.EDIT in boundary)
        assertTrue(AnnouncementAction.PUBLISH in boundary)
        assertFalse(
            AnnouncementAction.PUBLISH in
                availableAnnouncementActions(actor, announcement.copy(version = 997), null)
        )
        assertFalse(
            AnnouncementAction.PUBLISH in
                availableAnnouncementActions(
                    actor,
                    announcement.copy(publicationAttempts = 8),
                    null,
                )
        )
    }

    @Test
    fun activeJobsCannotBeArchivedOrReturnedToDraft() {
        for (status in listOf(JobStatus.QUEUED, JobStatus.RUNNING)) {
            val job = job(status).copy(cancellationRequested = true)
            val queued =
                announcement.copy(
                    status = AnnouncementStatus.QUEUED,
                    publicationJobId = job.request.id,
                )
            assertEquals(
                setOf(AnnouncementAction.PREVIEW, AnnouncementAction.VIEW_JOB),
                availableAnnouncementActions(actor, queued, job),
            )
        }
    }

    @Test
    fun stoppedPublicationsAllowRecoveryWithoutRemovingRevisionLimits() {
        for (status in listOf(JobStatus.FAILED, JobStatus.CANCELLED)) {
            val job = job(status)
            val queued =
                announcement.copy(
                    version = 995,
                    status = AnnouncementStatus.QUEUED,
                    publicationJobId = job.request.id,
                )
            val actions = availableAnnouncementActions(actor, queued, job)
            assertTrue(AnnouncementAction.RETURN_TO_DRAFT in actions)
            assertTrue(AnnouncementAction.ARCHIVE in actions)
            assertFalse(
                AnnouncementAction.RETURN_TO_DRAFT in
                    availableAnnouncementActions(actor, queued.copy(version = 996), job)
            )
            assertFalse(
                AnnouncementAction.ARCHIVE in
                    availableAnnouncementActions(actor, queued.copy(version = 999), job)
            )
        }
    }

    @Test
    fun publishedAndArchivedCorrespondenceCannotBeEditedOrRepublished() {
        val job = job(JobStatus.SUCCEEDED)
        val published =
            announcement.copy(
                status = AnnouncementStatus.PUBLISHED,
                publicationJobId = job.request.id,
            )
        assertEquals(
            setOf(AnnouncementAction.ARCHIVE, AnnouncementAction.VIEW_JOB),
            availableAnnouncementActions(actor, published, job),
        )
        assertEquals(
            setOf(AnnouncementAction.VIEW_JOB),
            availableAnnouncementActions(
                actor,
                published.copy(status = AnnouncementStatus.ARCHIVED),
                job,
            ),
        )
    }

    @Test
    fun moduleManagementDoesNotImplyGeneralJobAccess() {
        val job = job(JobStatus.QUEUED)
        val manager = actor.copy(accountId = UUID.randomUUID())
        assertFalse(
            AnnouncementAction.VIEW_JOB in availableAnnouncementActions(manager, announcement, job)
        )
        assertTrue(
            AnnouncementAction.VIEW_JOB in
                availableAnnouncementActions(
                    manager.copy(permissions = setOf("announcements.manage", "jobs.read")),
                    announcement,
                    job,
                )
        )
        assertTrue(
            availableAnnouncementActions(actor.copy(permissions = emptySet()), announcement, job)
                .isEmpty()
        )
    }
}
