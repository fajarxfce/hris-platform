package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentClassification
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class GetExpenseDraftReferences(
    private val policies: ExpensePolicyRepository,
    private val people: PeopleRepository,
    private val units: OrganizationRepository,
    private val documents: DocumentRepository,
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
        query: ExpenseDraftReferenceQuery,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.SELF,
    ): Result<ExpenseDraftReferences> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val editingPermissions =
            if (scope == ExpenseClaimReadScope.SELF)
                setOf("expenses.self.manage", "expenses.manage")
            else setOf("expenses.manage")
        if (actor.permissions.none { it in editingPermissions })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            query.categoryIds.size > 20 ||
                query.costCenterIds.size > 20 ||
                query.receiptRevisionIds.size > 60
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_expense_references"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val structureGuard = units.lockStructure(company, shared = true)
            if (structureGuard is Result.Failed) return@run structureGuard
            val documentGuard = documents.lock(company, shared = true)
            if (documentGuard is Result.Failed) return@run documentGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in editingPermissions })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val current =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(current.timezone))
            val foundEmployee = people.find(company, employmentId, today)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            if (
                employee == null ||
                    (scope == ExpenseClaimReadScope.SELF &&
                        employee.person.accountId != live.accountId)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val acquired = acquireReferences(company, employmentId, query)
            if (acquired is Result.Failed) return@run acquired
            val publication = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (publication is Result.Failed) publication else acquired
        }
    }

    private fun acquireReferences(
        company: UUID,
        employmentId: UUID,
        query: ExpenseDraftReferenceQuery,
    ): Result<ExpenseDraftReferences> {
        val categories = mutableListOf<ExpenseDraftLabelReference>()
        for (id in query.categoryIds.sortedBy(UUID::toString)) {
            val response = policies.find(company, id)
            if (response is Result.Failed) return response
            val category = (response as Result.Success).value
            categories +=
                if (category == null) ExpenseDraftLabelReference(id)
                else
                    ExpenseDraftLabelReference(
                        id,
                        category.code,
                        category.policy.name,
                        category.active,
                    )
        }
        val costCenters = mutableListOf<ExpenseDraftLabelReference>()
        for (id in query.costCenterIds.sortedBy(UUID::toString)) {
            val response = units.find(company, id)
            if (response is Result.Failed) return response
            val unit =
                (response as Result.Success).value?.takeIf { it.kind == UnitKind.COST_CENTER }
            costCenters +=
                if (unit == null) ExpenseDraftLabelReference(id)
                else ExpenseDraftLabelReference(id, unit.code, unit.name, unit.active)
        }
        val receipts = mutableListOf<ExpenseDraftReceiptReference>()
        for (id in query.receiptRevisionIds.sortedBy(UUID::toString)) {
            val response = documents.revision(company, id)
            if (response is Result.Failed) return response
            val revision = (response as Result.Success).value
            if (revision == null) {
                receipts += ExpenseDraftReceiptReference(id)
                continue
            }
            val foundDocument = documents.find(company, revision.documentId)
            if (foundDocument is Result.Failed) return foundDocument
            val document = (foundDocument as Result.Success).value
            receipts +=
                if (
                    document == null ||
                        document.employmentId != employmentId ||
                        document.classification != DocumentClassification.RECEIPT
                )
                    ExpenseDraftReceiptReference(id)
                else
                    ExpenseDraftReceiptReference(
                        id,
                        document.id,
                        revision.fileName,
                        revision.mediaType,
                        revision.size,
                        revision.status,
                    )
        }
        return Result.Success(
            ExpenseDraftReferences(
                employmentId,
                categories.toList(),
                costCenters.toList(),
                receipts.toList(),
            )
        )
    }
}
