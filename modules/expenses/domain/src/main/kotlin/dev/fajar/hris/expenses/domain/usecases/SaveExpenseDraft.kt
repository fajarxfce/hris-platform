package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentReferenceKind
import dev.fajar.hris.documents.domain.entities.DocumentReferenceOrigin
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
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
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        input: SaveExpenseDraftCommand,
    ): Result<MutationReceipt> {
        val command =
            input.copy(
                lines =
                    input.lines.take(21).map { line ->
                        line.copy(receiptRevisionIds = line.receiptRevisionIds.take(4))
                    }
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val valid = validateExpenseDraft(command)
        if (valid is Result.Failed) return valid
        val receiptIds = command.lines.flatMap { it.receiptRevisionIds }.toSet()
        val key =
            OperationKey(
                "expenses.draft_save",
                operationId,
                listOf(
                    command.id.toString(),
                    command.employmentId.toString(),
                    command.expectedVersion?.toString(),
                    command.title,
                    command.description,
                    command.reason,
                ) +
                    command.lines.flatMap { line ->
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

            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val current = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()

            val foundEmployee = people.find(company, command.employmentId, today)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee =
                (foundEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!canManageExpenseClaim(live, employee))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "expense_access_denied"))
            val found = claims.find(company, command.id)
            if (found is Result.Failed) return@run found
            val existing = (found as Result.Success).value
            if (existing != null && existing.employmentId != command.employmentId)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_identity_immutable")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .map { receipt }
            }
            if (existing?.version != command.expectedVersion)
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
            for (categoryId in command.lines.map { it.categoryId }.toSet()) {
                val category = policies.find(company, categoryId)
                if (category is Result.Failed) return@run category
                if ((category as Result.Success).value == null)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "expense_category_unavailable")
                    )
            }
            for (costCenterId in command.lines.mapNotNull { it.costCenterId }.toSet()) {
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
                        revision.status ==
                            dev.fajar.hris.documents.domain.entities.DocumentRevisionStatus
                                .RETIRED ||
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
                        command.id,
                        command.employmentId,
                        actor.accountId,
                        now,
                        0,
                        0,
                        ExpenseClaimStatus.DRAFT,
                    )
            val draft =
                ExpenseDraft(
                    command.id,
                    claim.draftRevision,
                    employee.employeeNumber,
                    employee.person.legalName,
                    command.title,
                    command.description,
                    command.lines.fold(java.math.BigDecimal.ZERO) { total, line ->
                        total + line.amount
                    },
                    command.lines,
                    actor.accountId,
                    command.reason,
                    now,
                )
            claims
                .saveDraft(actor, claim, draft, command.expectedVersion)
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
                                    command.reason,
                                ),
                            )
                        }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .map { receipt }
                }
        }
    }
}
