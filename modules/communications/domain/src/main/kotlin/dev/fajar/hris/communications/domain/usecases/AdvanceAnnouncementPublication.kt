package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

class AdvanceAnnouncementPublication(
    private val announcements: AnnouncementRepository,
    private val groups: AudienceGroupRepository,
    private val audience: AnnouncementAudienceRepository,
    private val publications: AnnouncementPublicationRepository,
    private val people: PeopleRepository,
    private val organization: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val permission = requireAnnouncementJobActor(actor, lease)
        if (permission is Result.Failed) return permission
        val request = lease.job.request
        val company = request.companyId
        return transactions.run(actor) {
            val leased = jobs.lockLease(lease)
            if (leased is Result.Failed) return@run leased
            val job =
                (leased as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )
            if (
                job.completedItems != 0 ||
                    request.totalItems != 1 ||
                    request.progressMode != JobProgressMode.FIXED_TOTAL
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_publication_obsolete")
                )
            val guard = announcements.lock(company)
            if (guard is Result.Failed) return@run guard
            val groupGuard = groups.lock(company, shared = true)
            if (groupGuard is Result.Failed) return@run groupGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val structureGuard = organization.lockStructure(company, shared = true)
            if (structureGuard is Result.Failed) return@run structureGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, company)
            if (access is Result.Failed) return@run access
            val identity = (access as Result.Success).value
            val checked =
                validateCompanyCommandActor(actor, identity).flatMap {
                    it.requirePermission("announcements.manage")
                }
            if (checked is Result.Failed) return@run checked
            val found = announcements.forJob(company, request.id)
            if (found is Result.Failed) return@run found
            val announcement =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "announcement_publication_obsolete")
                    )
            if (
                announcement.status != AnnouncementStatus.QUEUED ||
                    request.values["announcementId"] != announcement.id.toString() ||
                    request.values["contentVersion"] != announcement.version.toString()
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_publication_obsolete")
                )
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (announcement.scheduledFor?.isAfter(now) == true)
                return@run Result.Failed(Failure(FailureKind.UNAVAILABLE, "announcement_not_due"))
            val audienceVersions = linkedMapOf<UUID, Long>()
            when (announcement.audience.kind) {
                AudienceKind.COMPANY -> Unit
                AudienceKind.GROUP -> {
                    val loaded = groups.references(company, announcement.audience.targetIds.toSet())
                    if (loaded is Result.Failed) return@run loaded
                    val references = (loaded as Result.Success).value
                    val valid = validateAnnouncementGroups(announcement.audience, references)
                    if (valid is Result.Failed) return@run valid
                    references.forEach { audienceVersions[it.id] = it.version }
                }
                AudienceKind.BRANCH,
                AudienceKind.DEPARTMENT -> {
                    for (id in announcement.audience.targetIds) {
                        val loaded = organization.find(company, id)
                        if (loaded is Result.Failed) return@run loaded
                        val reference = (loaded as Result.Success).value
                        val valid = validateAnnouncementUnit(announcement.audience.kind, reference)
                        if (valid is Result.Failed) return@run valid
                        audienceVersions[id] = requireNotNull(reference).version
                    }
                }
            }
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "company_access_denied")
                    )
            val selection =
                announcementRecipientSelection(
                    announcement.audience,
                    now.atZone(ZoneId.of(settings.timezone)).toLocalDate(),
                )
            val selected = audience.recipients(company, selection, 5001)
            if (selected is Result.Failed) return@run selected
            val recipients = (selected as Result.Success).value
            if (recipients.isEmpty())
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "announcement_audience_empty")
                )
            if (recipients.size > 5000)
                return@run Result.Failed(
                    Failure(
                        FailureKind.CONFLICT,
                        "announcement_audience_limit",
                        parameters = mapOf("maximum" to "5000"),
                    )
                )
            val publication =
                AnnouncementPublication(
                    request.id,
                    announcement.id,
                    announcement.version,
                    now,
                    actor.accountId,
                    recipients,
                    audienceVersions,
                )
            val published = publications.publish(company, publication)
            if (published is Result.Failed) return@run published
            val changed =
                announcements.save(
                    company,
                    announcement.copy(
                        version = announcement.version + 1,
                        status = AnnouncementStatus.PUBLISHED,
                        publishedAt = now,
                        recipientCount = recipients.size,
                        recordedAt = now,
                        recordedBy = actor.accountId,
                        reason = "communications.publication_completed",
                    ),
                    announcement.version,
                )
            if (changed is Result.Failed) return@run changed
            val checkpoint =
                jobs.checkpoint(
                    lease,
                    JobProgress(1, mapOf("announcementId" to announcement.id.toString())),
                )
            if (checkpoint is Result.Failed) return@run checkpoint
            if (!(checkpoint as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val completed = jobs.complete(lease, JobStatus.SUCCEEDED)
            if (completed is Result.Failed) return@run completed
            if (!(completed as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "announcement",
                        announcement.id,
                        "communications.announcement_published",
                        mapOf(
                            "publicationId" to request.id.toString(),
                            "recipientCount" to recipients.size.toString(),
                        ),
                    ),
                )
                .map { JobStep(1, true) }
        }
    }
}
