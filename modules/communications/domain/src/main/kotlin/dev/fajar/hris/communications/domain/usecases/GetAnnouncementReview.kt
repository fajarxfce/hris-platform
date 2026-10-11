package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.availableAnnouncementActions
import dev.fajar.hris.communications.domain.repositories.AnnouncementRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class GetAnnouncementReview(
    private val announcements: AnnouncementRepository,
    private val jobs: JobRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<AnnouncementReview> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val guard = announcements.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val identity = identities.access(actor.accountId, company)
            if (identity is Result.Failed) return@run identity
            val resolved = (identity as Result.Success).value
            val authorized =
                validateCompanySessionActor(actor, resolved, clock.instant(), security).flatMap {
                    it.requirePermission("announcements.manage")
                }
            if (authorized is Result.Failed) return@run authorized
            val found = announcements.find(company, id)
            if (found is Result.Failed) return@run found
            val announcement =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "announcement_not_found")
                    )
            // A plain job read cannot reverse the worker's job-before-announcement lock order.
            val jobResult =
                announcement.publicationJobId?.let { jobs.find(company, it) }
                    ?: Result.Success(null)
            if (jobResult is Result.Failed) return@run jobResult
            val job = (jobResult as Result.Success).value
            val now = clock.instant()
            val checked = validateCompanySessionActor(actor, resolved, now, security)
            if (checked is Result.Failed) return@run checked
            val current = (checked as Result.Success).value
            if (
                announcement.publicationJobId != null &&
                    (job == null ||
                        job.request.id != announcement.publicationJobId ||
                        job.request.kind != JobKind.ANNOUNCEMENT_PUBLISH ||
                        job.request.companyId != company ||
                        job.request.values["announcementId"] != id.toString())
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "announcement_publication_unavailable")
                )
            Result.Success(
                AnnouncementReview(
                    announcement,
                    job?.let {
                        AnnouncementPublicationJob(
                            it.request.id,
                            it.status,
                            it.cancellationRequested,
                            it.version,
                            it.failureCode,
                        )
                    },
                    availableAnnouncementActions(current, announcement, job),
                    now,
                )
            )
        }
    }
}
