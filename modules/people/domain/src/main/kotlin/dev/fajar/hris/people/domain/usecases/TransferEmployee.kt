package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.UnitKind
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class TransferEmployee(
    private val people: PeopleRepository,
    private val lifecycle: LifecycleRepository,
    private val transfers: EmploymentTransferRepository,
    private val companies: CompanyRepository,
    private val units: OrganizationRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val roles: RoleTemplateRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: CrossCompanyTransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        input: EmploymentTransferCommand,
    ): Result<MutationReceipt> {
        val sourceCompany =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!actor.permissions.containsAll(setOf("people.manage", "people.transfer")))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "transfer_access_required"))
        if (input.targetCompanyId == sourceCompany || input.expectedVersion < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employment_transfer"))
        val command = input.copy(employeeNumber = input.employeeNumber.trim().uppercase())
        val number = validateEmployeeNumber(command.employeeNumber)
        if (number is Result.Failed) return number
        val validTerms = validateEmployment(command.terms, command.reason)
        if (validTerms is Result.Failed) return validTerms
        val terms = command.terms
        val key =
            OperationKey(
                "people.employee_transfer",
                operationId,
                listOf(
                    id.toString(),
                    command.targetCompanyId.toString(),
                    command.targetEmploymentId.toString(),
                    command.expectedVersion.toString(),
                    command.employeeNumber,
                    terms.effectiveFrom.toString(),
                    terms.startDate.toString(),
                    terms.endDate?.toString(),
                    terms.contract.name,
                    terms.status.name,
                    terms.branchId?.toString(),
                    terms.departmentId?.toString(),
                    terms.positionId?.toString(),
                    terms.costCenterId?.toString(),
                    terms.managerId?.toString(),
                    command.reason,
                ),
            )
        val scopes = listOf(sourceCompany, command.targetCompanyId).sorted()
        return transactions.run(actor, command.targetCompanyId) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            for (company in scopes) {
                val locked = people.lockReportingLines(company)
                if (locked is Result.Failed) return@run locked
            }
            for (company in scopes) {
                val locked = companies.lock(company)
                if (locked is Result.Failed) return@run locked
            }
            val structure = units.lockStructure(command.targetCompanyId)
            if (structure is Result.Failed) return@run structure
            for (company in scopes) {
                val locked = members.lock(company)
                if (locked is Result.Failed) return@run locked
            }
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val sourceAccess = identities.access(actor.accountId, sourceCompany)
            if (sourceAccess is Result.Failed) return@run sourceAccess
            val targetAccess = identities.access(actor.accountId, command.targetCompanyId)
            if (targetAccess is Result.Failed) return@run targetAccess
            val allowed =
                validateTransferActor(
                    actor,
                    (sourceAccess as Result.Success).value,
                    (targetAccess as Result.Success).value,
                )
            if (allowed is Result.Failed) return@run allowed
            val recent =
                if (security.enforceMfa)
                    requireRecentMfa(actor, clock.instant(), security.recentAuthenticationAge)
                else
                    requireRecentAuthentication(
                        actor,
                        clock.instant(),
                        security.recentAuthenticationAge,
                    )
            if (recent is Result.Failed) return@run recent
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val source = companies.find(sourceCompany)
            if (source is Result.Failed) return@run source
            val destination = companies.find(command.targetCompanyId)
            if (destination is Result.Failed) return@run destination
            val sourceZone =
                (source as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val targetZone =
                (destination as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.now(clock.withZone(ZoneId.of(sourceZone)))
            val targetToday = LocalDate.now(clock.withZone(ZoneId.of(targetZone)))
            val found = people.find(sourceCompany, id, today)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != command.expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val valid = validateImmediateTransfer(employee, command, today, targetToday)
            if (valid is Result.Failed) return@run valid
            val openCases = lifecycle.hasOpenCases(sourceCompany, id)
            if (openCases is Result.Failed) return@run openCases
            if ((openCases as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "open_lifecycle_cases_pending")
                )
            val future = people.hasRevisionsAfter(sourceCompany, id, today)
            if (future is Result.Failed) return@run future
            if ((future as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "scheduled_employment_changes_pending")
                )
            val reports = people.hasReportingDependentsAtOrAfter(sourceCompany, id, today)
            if (reports is Result.Failed) return@run reports
            if ((reports as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "reporting_reassignment_required")
                )
            val occupied =
                people.hasOpenEmploymentAtOrAfter(
                    command.targetCompanyId,
                    employee.person.id,
                    null,
                    today,
                )
            if (occupied is Result.Failed) return@run occupied
            if ((occupied as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "destination_employment_exists")
                )
            for ((unitId, kind) in
                listOf(
                    terms.branchId to UnitKind.BRANCH,
                    terms.departmentId to UnitKind.DEPARTMENT,
                    terms.positionId to UnitKind.POSITION,
                    terms.costCenterId to UnitKind.COST_CENTER,
                )) {
                if (unitId == null) continue
                val result = units.find(command.targetCompanyId, unitId)
                if (result is Result.Failed) return@run result
                val unit = (result as Result.Success).value
                if (unit == null || !unit.active || unit.kind != kind)
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "organization_assignment_unavailable")
                    )
            }
            if (terms.managerId != null) {
                val manager = people.find(command.targetCompanyId, terms.managerId, today)
                if (manager is Result.Failed) return@run manager
                if ((manager as Result.Success).value?.terms?.isWorkingOn(today) != true)
                    return@run Result.Failed(Failure(FailureKind.VALIDATION, "manager_unavailable"))
            }
            val accountId = employee.person.accountId
            val departure =
                if (accountId == null) null
                else {
                    val targetMember = members.find(command.targetCompanyId, accountId)
                    if (targetMember is Result.Failed) return@run targetMember
                    if ((targetMember as Result.Success).value?.membershipActive != true)
                        return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "destination_membership_required")
                        )
                    val remaining =
                        people.hasOpenEmploymentAtOrAfter(
                            sourceCompany,
                            employee.person.id,
                            id,
                            today,
                        )
                    if (remaining is Result.Failed) return@run remaining
                    val member = members.find(sourceCompany, accountId)
                    if (member is Result.Failed) return@run member
                    val current = (member as Result.Success).value
                    if (
                        (remaining as Result.Success).value ||
                            current == null ||
                            !current.membershipActive
                    )
                        null
                    else {
                        if ("people.offboard" !in sourceAccess.value?.permissions.orEmpty())
                            return@run Result.Failed(
                                Failure(FailureKind.FORBIDDEN, "offboarding_access_required")
                            )
                        if (accountId == actor.accountId)
                            return@run Result.Failed(
                                Failure(FailureKind.CONFLICT, "cannot_remove_own_administration")
                            )
                        if ("identity.manage" in current.permissions) {
                            val other =
                                members.hasOtherActiveMember(
                                    sourceCompany,
                                    accountId,
                                    "identity.manage",
                                )
                            if (other is Result.Failed) return@run other
                            if (!(other as Result.Success).value)
                                return@run Result.Failed(
                                    Failure(FailureKind.CONFLICT, "last_company_administrator")
                                )
                        }
                        val application =
                            roles.application(sourceCompany, accountId, current.version)
                        if (application is Result.Failed) return@run application
                        MembershipDeparture(
                            current,
                            (application as Result.Success).value
                                ?: MembershipGrant(current.permissions, emptyList()),
                        )
                    }
                }
            val ended =
                employee.terms.copy(
                    effectiveFrom = today,
                    endDate = today.minusDays(1),
                    status = EmploymentStatus.ENDED,
                    managerId = null,
                )
            val revised = people.revise(actor, id, command.expectedVersion, ended, command.reason)
            if (revised is Result.Failed) return@run revised
            val targetActor = actor.copy(companyId = command.targetCompanyId)
            val created =
                people.createForPerson(
                    targetActor,
                    command.targetEmploymentId,
                    command.employeeNumber,
                    employee.person.id,
                    terms,
                    command.reason,
                )
            if (created is Result.Failed) return@run created
            if (departure != null) {
                val member = departure.member
                val deactivated =
                    members.save(
                        sourceCompany,
                        member.id,
                        member.version,
                        false,
                        member.permissions,
                    )
                if (deactivated is Result.Failed) return@run deactivated
                val recorded =
                    roles
                        .recordApplication(
                            actor,
                            member.id,
                            (deactivated as Result.Success).value.version,
                            departure.grant,
                        )
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "company_membership",
                                    member.id,
                                    "people.departure_access_revoked",
                                    mapOf("employmentId" to id.toString()),
                                    command.reason,
                                ),
                            )
                        }
                if (recorded is Result.Failed) return@run recorded
            }
            val transfer =
                EmploymentTransfer(
                    operationId,
                    sourceCompany,
                    id,
                    command.targetCompanyId,
                    command.targetEmploymentId,
                    employee.person.id,
                    (revised as Result.Success).value.version,
                    today,
                    actor.accountId,
                    command.reason,
                    clock.instant(),
                )
            val receipt = (created as Result.Success).value
            transfers
                .record(transfer)
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "employment",
                            id,
                            "people.employee_transferred",
                            mapOf(
                                "transferId" to operationId.toString(),
                                "targetCompanyId" to command.targetCompanyId.toString(),
                            ),
                            command.reason,
                        ),
                    )
                }
                .flatMap {
                    journal.record(
                        targetActor,
                        ChangeRecord(
                            "employment",
                            command.targetEmploymentId,
                            "people.employee_received",
                            mapOf(
                                "transferId" to operationId.toString(),
                                "sourceCompanyId" to sourceCompany.toString(),
                            ),
                            command.reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}
