package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.AudienceReferenceRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock

class ListAudienceReferences(
    private val references: AudienceReferenceRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, request: AudienceReferenceSearch): Result<Page<AudienceReference>> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val search = request.copy(query = request.query.trim(), ids = request.ids.toList())
        if (search.limit !in 1..200)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_page_size",
                    parameters = mapOf("maximum" to "200"),
                )
            )
        val fields = linkedMapOf<String, String>()
        if (search.query.length > 120 || search.query.any { Character.isISOControl(it) })
            fields["query"] = "invalid_text"
        if (search.ids.size > 50 || search.ids.distinct().size != search.ids.size)
            fields["ids"] = "invalid_selection"
        if (
            search.ids.isNotEmpty() &&
                (search.query.isNotEmpty() ||
                    search.after != null ||
                    search.ids.size > search.limit)
        )
            fields["ids"] = "incompatible_filters"
        if (fields.isNotEmpty())
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_audience_reference_search", fields)
            )
        return transactions.run(actor) {
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("announcements.manage") }
            if (access is Result.Failed) return@run access
            // A bounded lookup may label an inactive saved selection. Saves and publication
            // authorize and validate it again; reference discovery does not reserve access.
            references.list(company, search, activeOnly = search.ids.isEmpty())
        }
    }
}
