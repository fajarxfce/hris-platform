package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.EmploymentTransferReview
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/** Current command context; transfer independently rechecks all mutation eligibility. */
class GetEmploymentTransferReview(
    private val people: PeopleRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: CrossCompanyTransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        targetCompany: UUID,
        client: ClientRequest,
    ): Result<EmploymentTransferReview> {
        val sourceCompany =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val read = actor.requirePermission("people.read")
        if (read is Result.Failed) return read
        val permission = validateTransferAccess(actor)
        if (permission is Result.Failed) return permission
        if (sourceCompany == targetCompany)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employment_transfer"))
        val ordered = listOf(sourceCompany, targetCompany).sorted()
        return transactions.run(actor, targetCompany) {
            val targetAccess = identities.access(actor.accountId, targetCompany)
            if (targetAccess is Result.Failed) return@run targetAccess
            val originalTarget = (targetAccess as Result.Success).value
            val captured =
                validateCompanySessionActor(
                    actor.copy(
                        companyId = targetCompany,
                        permissions = originalTarget?.permissions.orEmpty(),
                    ),
                    originalTarget,
                    clock.instant(),
                    security,
                )
            if (captured is Result.Failed)
                return@run Result.Failed(
                    captured.failure.copy(
                        parameters =
                            captured.failure.parameters + ("companyId" to targetCompany.toString())
                    )
                )
            val capturedTarget = (captured as Result.Success).value
            val allowedTarget = validateTransferAccess(capturedTarget)
            if (allowedTarget is Result.Failed) return@run allowedTarget
            for (company in ordered) {
                val guard = policies.lock(company, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val peopleGuard = people.lockReportingLines(sourceCompany, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            for (company in ordered) {
                val guard = companies.lock(company, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            for (company in ordered) {
                val guard = members.lock(company, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val now = clock.instant()
            val scopes = linkedMapOf<UUID, Actor>()
            val authorities = linkedMapOf<UUID, AccountAccess?>()
            for (company in ordered) {
                val original = if (company == sourceCompany) actor else capturedTarget
                val live =
                    identities.access(actor.accountId, company).flatMap {
                        authorities[company] = it
                        validateCompanySessionActor(original, it, clock.instant(), security)
                    }
                if (live is Result.Failed)
                    return@run Result.Failed(
                        live.failure.copy(
                            parameters =
                                live.failure.parameters + ("companyId" to company.toString())
                        )
                    )
                val current = (live as Result.Success).value
                val allowed = validateTransferAccess(current)
                if (allowed is Result.Failed) return@run allowed
                if (company == sourceCompany) {
                    val readable = current.requirePermission("people.read")
                    if (readable is Result.Failed) return@run readable
                }
                scopes[company] = current
                val available =
                    policies.effective(company, now).flatMap {
                        validateClientAvailability(
                            clientPolicyStatus(it, now),
                            setOf(CompanyModule.PEOPLE),
                            client.version,
                            client.native,
                        )
                    }
                if (available is Result.Failed)
                    return@run Result.Failed(
                        available.failure.copy(
                            parameters =
                                available.failure.parameters + ("companyId" to company.toString())
                        )
                    )
            }
            val source = companies.find(sourceCompany)
            if (source is Result.Failed) return@run source
            val destination = companies.find(targetCompany)
            if (destination is Result.Failed) return@run destination
            val first =
                (source as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val second =
                (destination as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val sourceDate = now.atZone(ZoneId.of(first.timezone)).toLocalDate()
            val targetDate = now.atZone(ZoneId.of(second.timezone)).toLocalDate()
            val found = people.find(sourceCompany, id, sourceDate)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            validateTransferSource(employee, sourceDate, targetDate)
                .map {
                    EmploymentTransferReview(
                        employee,
                        first,
                        second,
                        sourceDate,
                        targetDate,
                        now,
                        scopes.getValue(targetCompany).permissions.toSet(),
                    )
                }
                .flatMap { review ->
                    val completedAt = clock.instant()
                    for (company in ordered) {
                        val original = if (company == sourceCompany) actor else capturedTarget
                        val completed =
                            validateCompanySessionActor(
                                original,
                                authorities[company],
                                completedAt,
                                security,
                            )
                        if (completed is Result.Failed)
                            return@flatMap Result.Failed(
                                completed.failure.copy(
                                    parameters =
                                        completed.failure.parameters +
                                            ("companyId" to company.toString())
                                )
                            )
                    }
                    Result.Success(review)
                }
        }
    }
}
