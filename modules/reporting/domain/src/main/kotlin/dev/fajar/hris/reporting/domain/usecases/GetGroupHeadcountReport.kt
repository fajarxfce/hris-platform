package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.GroupHeadcountReport
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class GetGroupHeadcountReport(
    private val reports: HeadcountReportRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val reads: CompanyReadTransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        selected: List<UUID>,
        asOf: LocalDate,
        client: ClientRequest,
    ): Result<GroupHeadcountReport> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val date = validateHeadcountDate(asOf)
        if (date is Result.Failed) return date
        val selection = validateHeadcountCompanies(selected)
        if (selection is Result.Failed) return selection
        val ordered = (selection as Result.Success).value
        // Capture company grants for this request before waiting on report guards.
        val captured: Result<Map<UUID, Actor>> =
            transactions.run(actor) {
                val scopes = linkedMapOf<UUID, Actor>()
                for (company in ordered) {
                    val found = identities.access(actor.accountId, company)
                    if (found is Result.Failed) return@run found
                    val access =
                        (found as Result.Success).value
                            ?: return@run Result.Failed(
                                Failure(FailureKind.UNAUTHENTICATED, "session_revoked")
                            )
                    val scope =
                        validateCompanyCommandActor(
                            actor.copy(companyId = company, permissions = access.permissions),
                            access,
                        )
                    if (scope is Result.Failed) return@run scope
                    val value = (scope as Result.Success).value
                    val allowed = validateHeadcountAccess(value)
                    if (allowed is Result.Failed) return@run allowed
                    val assurance =
                        validateSessionAssurance(
                            access.account,
                            access.securityPermissions,
                            actor.mfaVerifiedAt,
                            clock.instant(),
                            security,
                        )
                    if (assurance is Result.Failed) return@run assurance
                    scopes[company] = value
                }
                Result.Success(scopes)
            }
        if (captured is Result.Failed) return captured
        val scopes = (captured as Result.Success).value
        return reads.run(actor, ordered.toSet()) {
            for (company in ordered) {
                val guard = policies.lock(company, shared = true)
                if (guard is Result.Failed) return@run guard
            }
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
            for (company in ordered) {
                val found = identities.access(actor.accountId, company)
                if (found is Result.Failed) return@run found
                val access = (found as Result.Success).value
                val live = validateCompanyCommandActor(scopes.getValue(company), access)
                if (live is Result.Failed) return@run live
                val permitted = validateHeadcountAccess((live as Result.Success).value)
                if (permitted is Result.Failed) return@run permitted
                val current = requireNotNull(access)
                val assurance =
                    validateSessionAssurance(
                        current.account,
                        current.securityPermissions,
                        actor.mfaVerifiedAt,
                        now,
                        security,
                    )
                if (assurance is Result.Failed) return@run assurance
                val available =
                    policies.effective(company, now).flatMap {
                        validateClientAvailability(
                            clientPolicyStatus(it, now),
                            setOf(CompanyModule.REPORTING),
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
            reports.countGroup(ordered.toSet(), asOf).map { GroupHeadcountReport(asOf, now, it) }
        }
    }
}
