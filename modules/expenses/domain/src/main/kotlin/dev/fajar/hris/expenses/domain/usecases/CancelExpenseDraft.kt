package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class CancelExpenseDraft(
    private val claims: ExpenseClaimRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_action"))
        val key =
            OperationKey(
                "expenses.draft_cancel",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val claimLock = claims.lock(company)
            if (claimLock is Result.Failed) return@run claimLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock

            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val identityLock = identities.lockAccount(actor.accountId)
            if (identityLock is Result.Failed) return@run identityLock

            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()

            val found = claims.find(company, id)
            if (found is Result.Failed) return@run found
            val claim =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_claim_not_found")
                    )
            val foundEmployee = people.find(company, claim.employmentId, today)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canManageExpenseClaim(live, employee))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (claim.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (claim.status !in setOf(ExpenseClaimStatus.DRAFT, ExpenseClaimStatus.RETURNED))
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "expense_not_editable"))
            claims.cancel(actor, claim, reason, now).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "expense_claim",
                                id,
                                "expenses.draft_cancelled",
                                reason = reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
