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
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class ReturnAnnouncementToDraft(
    private val announcements: AnnouncementRepository,
    private val jobs: JobRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
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
                "communications.announcement_return_to_draft",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val observed = announcements.find(company, id)
            if (observed is Result.Failed) return@run observed
            val old = (observed as Result.Success).value
            val jobId = old?.publicationJobId
            val loadedJob =
                if (jobId == null) Result.Success(null) else jobs.find(company, jobId, lock = true)
            if (loadedJob is Result.Failed) return@run loadedJob
            val job = (loadedJob as Result.Success).value
            val guard = announcements.lock(company)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("announcements.manage") }
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
            if (announcement.version != expectedVersion || announcement.publicationJobId != jobId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                announcement.status != AnnouncementStatus.QUEUED ||
                    job == null ||
                    job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_publication_not_stopped")
                )
            if (announcement.version > 995)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "announcement_revision_limit")
                )
            val snapshot =
                announcement.copy(
                    version = announcement.version + 1,
                    status = AnnouncementStatus.DRAFT,
                    publicationJobId = null,
                    scheduledFor = null,
                    recordedAt = clock.instant(),
                    recordedBy = actor.accountId,
                    reason = reason,
                )
            val changed = announcements.save(company, snapshot, expectedVersion)
            if (changed is Result.Failed) return@run changed
            val receipt = (changed as Result.Success).value
            operations
                .record(actor, key, receipt)
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "announcement",
                            id,
                            "communications.announcement_returned_to_draft",
                            mapOf("version" to receipt.version.toString()),
                            reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}
