package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
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
    private val security: IdentitySecurityPolicy,
    private val carry: LeaveCarryRepository,
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
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val currentAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, currentAccess, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = live.requirePermission("leave.year.close")
            if (allowed is Result.Failed) return@run allowed
            val accountResult = people.accountForEmployee(company, employeeId)
            if (accountResult is Result.Failed) return@run accountResult
            if ((accountResult as Result.Success).value == actor.accountId)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_adjustment_denied"))
            (replay as Result.Success).value?.let {
                return@run validateCompanySessionActor(
                        actor,
                        currentAccess,
                        clock.instant(),
                        security,
                    )
                    .map { _ -> it }
            }
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = clock.instant().atZone(ZoneId.of(settings.timezone)).toLocalDate()
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
            val acquiredCarry = carry.buckets(company, employeeId, typeId, setOf(year))
            if (acquiredCarry is Result.Failed) return@run acquiredCarry
            val bucket = (acquiredCarry as Result.Success).value.singleOrNull()
            val now = clock.instant()
            val planned =
                planLeaveYearClosing(
                    balance,
                    bucket,
                    type.policy.accrual,
                    destination,
                    (unresolved as Result.Success).value,
                    now,
                    settings.timezone,
                )
            if (planned is Result.Failed) return@run planned
            val plan = (planned as Result.Success).value
            val rollover = plan.rollover
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
                    plan.source.version,
                    plan.source.availableHalfDays,
                    plan.source.consumedHalfDays,
                    destination.version,
                    rollover,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    actor.accountId,
                    now,
                    reason,
                    true,
                    settings.timezone,
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
            val settlement =
                plan.settlement?.let {
                    LeaveCarryPosting(
                        UUID.randomUUID(),
                        it,
                        requireNotNull(bucket).version,
                        UUID.randomUUID(),
                    )
                }
            val settlementEntry =
                settlement?.let {
                    LeaveLedgerEntry(
                        it.ledgerEntryId,
                        employeeId,
                        typeId,
                        year,
                        LeaveLedgerKind.EXPIRE,
                        it.id,
                        null,
                        -it.movement.halfDays,
                        0,
                        0,
                        actor.accountId,
                        now,
                        reason,
                        it.id,
                    )
                }
            val postings =
                plan.movements.mapIndexed { index, movement ->
                    val kind =
                        if (movement.effect == LeaveCarryEffect.TRANSFER) LeaveLedgerKind.CARRY_OUT
                        else LeaveLedgerKind.EXPIRE
                    LeaveCarryPosting(
                        UUID.randomUUID(),
                        movement,
                        requireNotNull(bucket).version + index,
                        entries.single { it.year == year && it.kind == kind }.id,
                    )
                }
            val award =
                if (rollover.carryHalfDays == 0) null
                else
                    LeaveCarryAward(
                        LeaveCarryBucket(
                            id,
                            year + 1,
                            rollover.carryHalfDays,
                            rollover.carryHalfDays,
                            validity = plan.validity,
                        ),
                        employeeId,
                        typeId,
                        settings.timezone,
                        type.policy.accrual?.carryValidityMonths,
                    )
            val sourceMovements = entries.count { it.year == year }
            val receipt = MutationReceipt(id, 0)
            val settled =
                if (settlement == null) Result.Success(Unit)
                else
                    carry.append(company, listOf(settlement)).flatMap {
                        ledger.append(company, listOf(requireNotNull(settlementEntry)))
                    }
            settled
                .flatMap { entitlements.createClosing(company, closing) }
                .flatMap {
                    if (award == null) Result.Success(Unit) else carry.create(company, award)
                }
                .flatMap { carry.append(company, postings) }
                .flatMap {
                    if (entries.isEmpty()) Result.Success(Unit) else ledger.append(company, entries)
                }
                .flatMap {
                    entitlements.closeAccount(
                        company,
                        employeeId,
                        typeId,
                        year,
                        plan.source.version + sourceMovements,
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
                .flatMap {
                    validateCompanySessionActor(actor, currentAccess, clock.instant(), security)
                        .map { receipt }
                }
        }
    }
}
