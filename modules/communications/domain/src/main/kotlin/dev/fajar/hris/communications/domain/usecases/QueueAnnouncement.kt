package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

class QueueAnnouncement(
    private val announcements: AnnouncementRepository,
    private val groups: AudienceGroupRepository,
    private val organization: OrganizationRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        scheduledFor: Instant?,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("announcements.manage")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val valid = validateAnnouncementPublicationInput(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "communications.announcement_queue",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), scheduledFor?.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = announcements.lock(company)
            if (guard is Result.Failed) return@run guard
            val groupGuard = groups.lock(company, shared = true)
            if (groupGuard is Result.Failed) return@run groupGuard
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
                validateCompanySessionActor(actor, identity, clock.instant(), security).flatMap {
                    it.requirePermission("announcements.manage")
                }
            if (checked is Result.Failed) return@run checked
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = announcements.find(company, id)
            if (found is Result.Failed) return@run found
            val announcement =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "announcement_not_found")
                    )
            if (announcement.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (announcement.status != AnnouncementStatus.DRAFT)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "announcement_not_draft"))
            if (announcement.version > 996)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_revision_limit")
                )
            if (announcement.publicationAttempts >= 8)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_attempt_limit")
                )
            when (announcement.audience.kind) {
                AudienceKind.COMPANY -> Unit
                AudienceKind.GROUP -> {
                    val loaded = groups.references(company, announcement.audience.targetIds.toSet())
                    if (loaded is Result.Failed) return@run loaded
                    val references = (loaded as Result.Success).value
                    val valid = validateAnnouncementGroups(announcement.audience, references)
                    if (valid is Result.Failed) return@run valid
                }
                AudienceKind.BRANCH,
                AudienceKind.DEPARTMENT -> {
                    for (id in announcement.audience.targetIds) {
                        val loaded = organization.find(company, id)
                        if (loaded is Result.Failed) return@run loaded
                        val reference = (loaded as Result.Success).value
                        val valid = validateAnnouncementUnit(announcement.audience.kind, reference)
                        if (valid is Result.Failed) return@run valid
                    }
                }
            }
            val queueGuard = jobs.lockQueue(company)
            if (queueGuard is Result.Failed) return@run queueGuard
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val checkedAt = clock.instant()
            val assurance = validateCompanySessionActor(actor, identity, checkedAt, security)
            if (assurance is Result.Failed) return@run assurance
            val now = checkedAt.truncatedTo(ChronoUnit.MICROS)
            val schedule = validateAnnouncementSchedule(scheduledFor, now)
            if (schedule is Result.Failed) return@run schedule
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.ANNOUNCEMENT_PUBLISH,
                    operationId,
                    mapOf(
                        "announcementId" to id.toString(),
                        "contentVersion" to (announcement.version + 1).toString(),
                    ),
                    actor.authenticatedAt,
                    requireNotNull(identity).account.securityVersion,
                    actor.correlationId,
                    now,
                    1,
                    scheduledFor = scheduledFor,
                )
            val queued = jobs.create(job)
            if (queued is Result.Failed) return@run queued
            val snapshot =
                announcement.copy(
                    version = announcement.version + 1,
                    status = AnnouncementStatus.QUEUED,
                    publicationJobId = job.id,
                    scheduledFor = scheduledFor,
                    publicationAttempts = announcement.publicationAttempts + 1,
                    recordedAt = now,
                    recordedBy = actor.accountId,
                    reason = reason,
                )
            announcements.save(company, snapshot, expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "announcement",
                                id,
                                "communications.announcement_queued",
                                mapOf(
                                    "jobId" to job.id.toString(),
                                    "version" to receipt.version.toString(),
                                ),
                                reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
