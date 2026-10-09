package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class CloseEmployeeLeaveYear(
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val entitlements: LeaveEntitlementRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val requests: LeaveRequestRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        expectedPolicyVersion: Long,
        expectedBalanceVersion: Long,
        expectedDestinationVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("leave.year.close")
        if (permission is Result.Failed) return permission
        if (
            year !in 1900..2199 ||
                expectedPolicyVersion < 0 ||
                expectedBalanceVersion < 0 ||
                (expectedDestinationVersion ?: 0) < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_year_close"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.year_close",
                operationId,
                listOf(
                    id.toString(),
                    employeeId.toString(),
                    typeId.toString(),
                    year.toString(),
                    expectedPolicyVersion.toString(),
                    expectedBalanceVersion.toString(),
                    expectedDestinationVersion?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val employeeLock = ledger.lock(company, employeeId)
            if (employeeLock is Result.Failed) return@run employeeLock
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = live.requirePermission("leave.year.close")
            if (allowed is Result.Failed) return@run allowed
            val accountResult = people.accountForEmployee(company, employeeId)
            if (accountResult is Result.Failed) return@run accountResult
            if ((accountResult as Result.Success).value == actor.accountId)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_adjustment_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            if (year >= today.year)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_year_not_ended"))
            val employeeVersion = people.currentVersion(company, employeeId)
            if (employeeVersion is Result.Failed) return@run employeeVersion
            if ((employeeVersion as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val typeResult = policies.effective(company, typeId, LocalDate.of(year, 12, 31))
            if (typeResult is Result.Failed) return@run typeResult
            val type =
                (typeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            if (type.version != expectedPolicyVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_policy_version"))
            val balanceResult = ledger.balance(company, employeeId, typeId, year)
            if (balanceResult is Result.Failed) return@run balanceResult
            val balance = (balanceResult as Result.Success).value
            if (balance.closed)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_year_closed"))
            if (balance.version != expectedBalanceVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_balance_version"))
            val destinationResult = ledger.balance(company, employeeId, typeId, year + 1)
            if (destinationResult is Result.Failed) return@run destinationResult
            val destination = (destinationResult as Result.Success).value
            val unresolved = requests.unresolvedYear(company, employeeId, typeId, year)
            if (unresolved is Result.Failed) return@run unresolved
            val rolloverResult =
                calculateLeaveYearRollover(
                    balance,
                    type.policy.accrual?.carryLimitHalfDays ?: 0,
                    destination,
                    (unresolved as Result.Success).value,
                )
            if (rolloverResult is Result.Failed) return@run rolloverResult
            val rollover = (rolloverResult as Result.Success).value
            if (rollover.carryHalfDays > 0 && destination.version != expectedDestinationVersion)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "stale_destination_balance_version")
                )
            val closing =
                LeaveYearClosing(
                    id,
                    employeeId,
                    typeId,
                    year,
                    balance.version,
                    balance.availableHalfDays,
                    balance.consumedHalfDays,
                    destination.version,
                    rollover,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    actor.accountId,
                    now,
                    reason,
                )
            val entries = mutableListOf<LeaveLedgerEntry>()
            if (rollover.carryHalfDays > 0) {
                entries +=
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        typeId,
                        year,
                        LeaveLedgerKind.CARRY_OUT,
                        id,
                        null,
                        -rollover.carryHalfDays,
                        0,
                        0,
                        actor.accountId,
                        now,
                        reason,
                    )
                entries +=
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        typeId,
                        year + 1,
                        LeaveLedgerKind.CARRY_IN,
                        id,
                        null,
                        rollover.carryHalfDays,
                        0,
                        0,
                        actor.accountId,
                        now,
                        reason,
                    )
            }
            if (rollover.expireHalfDays > 0)
                entries +=
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        typeId,
                        year,
                        LeaveLedgerKind.EXPIRE,
                        id,
                        null,
                        -rollover.expireHalfDays,
                        0,
                        0,
                        actor.accountId,
                        now,
                        reason,
                    )
            val sourceMovements = entries.count { it.year == year }
            val receipt = MutationReceipt(id, 0)
            entitlements
                .createClosing(company, closing)
                .flatMap {
                    if (entries.isEmpty()) Result.Success(Unit) else ledger.append(company, entries)
                }
                .flatMap {
                    entitlements.closeAccount(
                        company,
                        employeeId,
                        typeId,
                        year,
                        balance.version + sourceMovements,
                        id,
                    )
                }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "leave_year_closing",
                            id,
                            "leave.year_closed",
                            mapOf(
                                "employeeId" to employeeId.toString(),
                                "typeId" to typeId.toString(),
                                "year" to year.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .map { receipt }
        }
    }
}
