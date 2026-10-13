package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentClassification
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.LeaveEvidenceOption
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ListLeavePreparationEvidence(
    private val documents: DocumentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employmentId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveEvidenceOption>> {
        val permission = actor.requirePermission("leave.manage")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..50) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val documentGuard = documents.lock(company, shared = true)
            if (documentGuard is Result.Failed) return@run documentGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val permissionAfterWait =
                (checked as Result.Success).value.requirePermission("leave.manage")
            if (permissionAfterWait is Result.Failed) return@run permissionAfterWait
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val current =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(current.timezone))
            val employee = people.find(company, employmentId, today)
            if (employee is Result.Failed) return@run employee
            if ((employee as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val acquired =
                documents.published(
                    company,
                    employmentId,
                    DocumentClassification.PERSONAL,
                    after,
                    limit,
                )
            if (acquired is Result.Failed) return@run acquired
            val page = (acquired as Result.Success).value
            val result =
                Page(
                    page.items.map {
                        LeaveEvidenceOption(
                            it.documentId,
                            it.revisionId,
                            it.revisionNumber,
                            it.title,
                            it.fileName,
                            it.mediaType,
                            it.size,
                            it.validatedAt,
                        )
                    },
                    page.nextCursor,
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { result }
        }
    }
}
