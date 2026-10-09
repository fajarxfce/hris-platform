package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class SubmitExpenseClaim(
    private val claims: ExpenseClaimRepository,
    private val policies: ExpensePolicyRepository,
    private val documents: DocumentRepository,
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
    private val approvals: ApprovalRepository,
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
        submissionId: UUID,
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
                "expenses.claim_submit",
                operationId,
                listOf(id.toString(), submissionId.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val claimLock = claims.lock(company)
            if (claimLock is Result.Failed) return@run claimLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            val structureLock = units.lockStructure(company)
            if (structureLock is Result.Failed) return@run structureLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
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
            val foundEmployee = people.findAtInstant(company, claim.employmentId, now)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (employee == null || !canManageExpenseClaim(live, employee))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_claim_not_found"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (claim.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (claim.status != ExpenseClaimStatus.DRAFT)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "expense_not_editable"))
            if (claim.submissionCount >= 20)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "expense_submission_limit"))
            val foundDraft = claims.draft(company, id, claim.draftRevision)
            if (foundDraft is Result.Failed) return@run foundDraft
            val draft =
                (foundDraft as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "expense_draft_missing")
                    )
            val validDraft = validateExpenseSubmissionDraft(draft, today)
            if (validDraft is Result.Failed) return@run validDraft
            val foundMakers = claims.contributors(company, id)
            if (foundMakers is Result.Failed) return@run foundMakers
            val contributors = (foundMakers as Result.Success).value
            if (contributors.size > 100)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_draft_revision_limit")
                )
            val makers = contributors + claim.createdBy + actor.accountId
            val lines = mutableListOf<ExpenseSubmittedLine>()
            for (line in draft.lines) {
                val foundCategory = policies.effective(company, line.categoryId, line.occurredOn)
                if (foundCategory is Result.Failed) return@run foundCategory
                val category =
                    (foundCategory as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "expense_category_unavailable")
                        )
                val foundTerms = people.find(company, claim.employmentId, line.occurredOn)
                if (foundTerms is Result.Failed) return@run foundTerms
                val terms =
                    (foundTerms as Result.Success).value?.terms
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "expense_employment_ineligible")
                        )
                val validLine = validateExpenseLinePolicy(line, category, terms, today)
                if (validLine is Result.Failed) return@run validLine
                val costCenter =
                    if (line.costCenterId == null) null
                    else {
                        val foundUnit = units.find(company, line.costCenterId)
                        if (foundUnit is Result.Failed) return@run foundUnit
                        val unit = (foundUnit as Result.Success).value
                        if (unit == null || !unit.active || unit.kind != UnitKind.COST_CENTER)
                            return@run Result.Failed(
                                Failure(FailureKind.VALIDATION, "expense_cost_center_unavailable")
                            )
                        ExpenseCostCenterSnapshot(unit.id, unit.code, unit.name, unit.version)
                    }
                val receipts = mutableListOf<ExpenseSubmittedReceipt>()
                for (revisionId in line.receiptRevisionIds) {
                    val foundRevision = documents.revision(company, revisionId)
                    if (foundRevision is Result.Failed) return@run foundRevision
                    val revision = (foundRevision as Result.Success).value
                    if (revision == null || revision.status != DocumentRevisionStatus.READY)
                        return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "expense_receipt_not_ready")
                        )
                    val foundDocument = documents.find(company, revision.documentId)
                    if (foundDocument is Result.Failed) return@run foundDocument
                    val document = (foundDocument as Result.Success).value
                    if (
                        document == null ||
                            document.employmentId != claim.employmentId ||
                            document.classification != DocumentClassification.RECEIPT
                    )
                        return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "expense_receipt_unavailable")
                        )
                    receipts +=
                        ExpenseSubmittedReceipt(
                            revision.id,
                            revision.fileName,
                            revision.mediaType,
                            revision.size,
                            revision.sha256,
                            false,
                        )
                }
                lines +=
                    ExpenseSubmittedLine(
                        line.id,
                        line.occurredOn,
                        line.amount,
                        line.description,
                        category,
                        costCenter,
                        receipts.toList(),
                    )
            }
            val validTotals = validateExpenseCategoryTotals(lines)
            if (validTotals is Result.Failed) return@run validTotals
            val digestCounts = lines.flatMap { it.receipts }.groupingBy { it.sha256 }.eachCount()
            val foundDuplicates = claims.duplicateReceiptDigests(company, id, digestCounts.keys)
            if (foundDuplicates is Result.Failed) return@run foundDuplicates
            val duplicates =
                (foundDuplicates as Result.Success).value +
                    digestCounts.filterValues { it > 1 }.keys
            val frozenLines =
                lines.map { line ->
                    line.copy(
                        receipts =
                            line.receipts.map {
                                it.copy(possibleDuplicate = it.sha256 in duplicates)
                            }
                    )
                }
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    submissionId,
                    ApprovalKind.EXPENSE,
                    actor.accountId,
                    employee.person.accountId,
                    if (employee.terms.isWorkingOn(today)) employee.managerAccountId else null,
                    today,
                    lines.first().category.code,
                    draft.totalAmount,
                    now,
                    excludedAccountIds = makers,
                )
            val foundTemplates = approvals.templates(company, ApprovalKind.EXPENSE, today)
            if (foundTemplates is Result.Failed) return@run foundTemplates
            val templates = (foundTemplates as Result.Success).value
            val selected = mutableListOf<ApprovalTemplate>()
            for (category in lines.map { it.category.code }.toSet()) {
                val policy = selectApprovalTemplate(templates, context.copy(category = category))
                if (policy is Result.Failed) return@run policy
                selected += (policy as Result.Success).value
            }
            if (selected.map { it.id }.toSet().size != 1)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "expense_approval_requires_split")
                )
            val template = selected.first()
            val foundCandidates =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (foundCandidates is Result.Failed) return@run foundCandidates
            val candidates = (foundCandidates as Result.Success).value
            if (candidates.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "approval_group_too_large")
                )
            val snapshot = snapshotApproval(template, context, candidates)
            if (snapshot is Result.Failed) return@run snapshot
            val approval = (snapshot as Result.Success).value
            val submission =
                ExpenseSubmission(
                    submissionId,
                    id,
                    claim.submissionCount + 1,
                    claim.draftRevision,
                    approval.id,
                    employee.person.accountId,
                    employee.employeeNumber,
                    employee.person.legalName,
                    draft.title,
                    draft.description,
                    draft.totalAmount,
                    frozenLines,
                    makers,
                    actor.accountId,
                    now,
                    reason,
                )
            approvals
                .create(company, approval)
                .flatMap { claims.submit(actor, claim, submission) }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "expense_claim",
                                    id,
                                    "expenses.claim_submitted",
                                    mapOf(
                                        "submissionId" to submissionId.toString(),
                                        "approvalId" to approval.id.toString(),
                                        "draftRevision" to claim.draftRevision.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
