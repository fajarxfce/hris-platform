package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.*
import java.util.UUID

class ListExpensePaymentBatches(
    private val payments: ExpensePaymentRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        from: LocalDate,
        until: LocalDate,
        status: ExpensePaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpensePaymentSummary>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            limit !in 1..200 ||
                from.year !in 1900..2200 ||
                until.year !in 1900..2200 ||
                java.time.temporal.ChronoUnit.DAYS.between(from, until) !in 0..365
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val recent =
                validateExpensePaymentAccess(
                    (checked as Result.Success).value,
                    clock.instant(),
                    security,
                )
            if (recent is Result.Failed) return@run recent
            val found = companies.find(company)
            if (found is Result.Failed) return@run found
            val settings =
                (found as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val zone = ZoneId.of(settings.timezone)
            payments.list(
                company,
                from.atStartOfDay(zone).toInstant(),
                until.plusDays(1).atStartOfDay(zone).toInstant(),
                status,
                after,
                limit,
            )
        }
    }
}
