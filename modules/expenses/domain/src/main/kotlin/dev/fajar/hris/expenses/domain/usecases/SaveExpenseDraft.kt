package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentReferenceKind
import dev.fajar.hris.documents.domain.entities.DocumentReferenceOrigin
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class SaveExpenseDraft(
    private val claims: ExpenseClaimRepository,
    private val policies: ExpensePolicyRepository,
    private val documents: dev.fajar.hris.documents.domain.repositories.DocumentRepository,
    private val references: DocumentReferenceRepository,
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
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
        input: SaveExpenseDraftCommand,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val valid = validateExpenseDraft(input)
        if (valid is Result.Failed) return valid
        val receiptIds = input.lines.flatMap { it.receiptRevisionIds }.toSet()
        val key =
            OperationKey(
                "expenses.draft_save",
                operationId,
                listOf(
                    input.id.toString(),
                    input.employmentId.toString(),
                    input.expectedVersion?.toString(),
                    input.title,
                    input.description,
                    input.reason,
                ) +
                    input.lines.flatMap { line ->
                        listOf(
                            line.id.toString(),
                            line.categoryId.toString(),
                            line.occurredOn.toString(),
                            line.amount.stripTrailingZeros().toPlainString(),
                            line.description,
                            line.costCenterId?.toString(),
                            line.receiptRevisionIds.joinToString(","),
                        )
                    },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val claimLock = claims.lock(company)
            if (claimLock is Result.Failed) return@run claimLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            val unitLock = units.lockStructure(company)
            if (unitLock is Result.Failed) return@run unitLock

            if (receiptIds.isNotEmpty()) {
                val documentLock = documents.lock(company)
                if (documentLock is Result.Failed) return@run documentLock
            }
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

            val foundEmployee = people.find(company, input.employmentId, today)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee =
                (foundEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!canManageExpenseClaim(live, employee))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "expense_access_denied"))
            val found = claims.find(company, input.id)
            if (found is Result.Failed) return@run found
            val existing = (found as Result.Success).value
            if (existing != null && existing.employmentId != input.employmentId)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_identity_immutable")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (existing?.version != input.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                existing != null &&
                    existing.status !in setOf(ExpenseClaimStatus.DRAFT, ExpenseClaimStatus.RETURNED)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "expense_not_editable"))
            if (existing?.draftRevision == 99)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_draft_revision_limit")
                )
            if (existing == null) {
                val capacity = claims.capacity(company, actor.accountId)
                if (capacity is Result.Failed) return@run capacity
                val usage = (capacity as Result.Success).value
                if (usage.companyOpenClaims >= 1000 || usage.actorOpenClaims >= 50)
                    return@run Result.Failed(
                        Failure(FailureKind.RATE_LIMITED, "expense_draft_capacity")
                    )
            }
            for (categoryId in input.lines.map { it.categoryId }.toSet()) {
                val category = policies.find(company, categoryId)
                if (category is Result.Failed) return@run category
                if ((category as Result.Success).value == null)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "expense_category_unavailable")
                    )
            }
            for (costCenterId in input.lines.mapNotNull { it.costCenterId }.toSet()) {
                val costCenter = units.find(company, costCenterId)
                if (costCenter is Result.Failed) return@run costCenter
                if (
                    (costCenter as Result.Success).value?.kind !=
                        dev.fajar.hris.organization.domain.entities.UnitKind.COST_CENTER
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "expense_cost_center_unavailable")
                    )
            }
            for (revisionId in receiptIds) {
                val foundRevision = documents.revision(company, revisionId)
                if (foundRevision is Result.Failed) return@run foundRevision
                val revision =
                    (foundRevision as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "expense_receipt_unavailable")
                        )
                val foundDocument = documents.find(company, revision.documentId)
                if (foundDocument is Result.Failed) return@run foundDocument
                val document = (foundDocument as Result.Success).value
                if (
                    document == null ||
                        document.employmentId != employee.id ||
                        document.classification !=
                            dev.fajar.hris.documents.domain.entities.DocumentClassification.RECEIPT
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "expense_receipt_unavailable")
                    )
            }
            val claim =
                existing?.copy(
                    version = existing.version + 1,
                    draftRevision = existing.draftRevision + 1,
                    status = ExpenseClaimStatus.DRAFT,
                )
                    ?: ExpenseClaim(
                        input.id,
                        input.employmentId,
                        actor.accountId,
                        now,
                        0,
                        0,
                        ExpenseClaimStatus.DRAFT,
                    )
            val draft =
                ExpenseDraft(
                    input.id,
                    claim.draftRevision,
                    employee.employeeNumber,
                    employee.person.legalName,
                    input.title,
                    input.description,
                    input.lines.fold(java.math.BigDecimal.ZERO) { total, line ->
                        total + line.amount
                    },
                    input.lines,
                    actor.accountId,
                    input.reason,
                    now,
                )
            claims
                .saveDraft(actor, claim, draft, input.expectedVersion)
                .flatMap { receipt ->
                    references
                        .retain(
                            company,
                            DocumentReferenceOrigin(
                                DocumentReferenceKind.EXPENSE_DRAFT,
                                claim.id,
                                draft.revision,
                            ),
                            receiptIds,
                            actor.accountId,
                            now,
                        )
                        .map { receipt }
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "expense_claim",
                                    claim.id,
                                    "expenses.draft_saved",
                                    mapOf(
                                        "employmentId" to employee.id.toString(),
                                        "revision" to draft.revision.toString(),
                                    ),
                                    input.reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
