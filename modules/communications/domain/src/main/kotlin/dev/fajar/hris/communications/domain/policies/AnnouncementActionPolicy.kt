package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.domain.entities.*

/** Read-time capabilities. Each command independently revalidates current scope and state. */
fun availableAnnouncementActions(
    actor: Actor,
    announcement: Announcement,
    job: BackgroundJob?,
): Set<AnnouncementAction> {
    if ("announcements.manage" !in actor.permissions) return emptySet()
    val stopped = job?.status in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
    return buildSet {
        if (announcement.status == AnnouncementStatus.DRAFT) {
            if (announcement.version < 996) add(AnnouncementAction.EDIT)
            if (announcement.version <= 996 && announcement.publicationAttempts < 8)
                add(AnnouncementAction.PUBLISH)
        }
        if (announcement.status in setOf(AnnouncementStatus.DRAFT, AnnouncementStatus.QUEUED))
            add(AnnouncementAction.PREVIEW)
        if (
            announcement.status == AnnouncementStatus.QUEUED &&
                stopped &&
                announcement.version <= 995
        )
            add(AnnouncementAction.RETURN_TO_DRAFT)
        if (
            announcement.status != AnnouncementStatus.ARCHIVED &&
                announcement.version < 999 &&
                (announcement.status != AnnouncementStatus.QUEUED || stopped)
        )
            add(AnnouncementAction.ARCHIVE)
        if (
            job != null &&
                (job.request.actorId == actor.accountId || "jobs.read" in actor.permissions)
        )
            add(AnnouncementAction.VIEW_JOB)
    }
}
