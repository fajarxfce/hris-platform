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

class PostEmployeeLeaveAccrual(
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
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        employeeId: UUID,
        typeId: UUID,
        month: YearMonth,
        expectedEmploymentVersion: Long,
        expectedPolicyVersion: Long,
        expectedBalanceVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("leave.accrual.post")
        if (permission is Result.Failed) return permission
        if (
            month.year !in 1900..2199 ||
                expectedEmploymentVersion < 0 ||
                expectedPolicyVersion < 0 ||
                expectedBalanceVersion < 0 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_accrual"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "leave.accrual_post",
                operationId,
                listOf(
                    id.toString(),
                    employeeId.toString(),
                    typeId.toString(),
                    month.toString(),
                    expectedEmploymentVersion.toString(),
                    expectedPolicyVersion.toString(),
                    expectedBalanceVersion.toString(),
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
            val allowed = live.requirePermission("leave.accrual.post")
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
            val versionResult = people.currentVersion(company, employeeId)
            if (versionResult is Result.Failed) return@run versionResult
            val employeeVersion =
                (versionResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employeeVersion != expectedEmploymentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val typeResult = policies.effective(company, typeId, month.atDay(1))
            if (typeResult is Result.Failed) return@run typeResult
            val type =
                (typeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            if (!type.active)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            if (type.version != expectedPolicyVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_policy_version"))
            val balanceResult = ledger.balance(company, employeeId, typeId, month.year)
            if (balanceResult is Result.Failed) return@run balanceResult
            val balance = (balanceResult as Result.Success).value
            if (balance.closed)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_year_closed"))
            if (balance.version != expectedBalanceVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_balance_version"))
            val historyResult =
                people.effectiveRevisions(company, employeeId, month.atDay(1), month.atEndOfMonth())
            if (historyResult is Result.Failed) return@run historyResult
            val awardResult =
                calculateLeaveAccrual(
                    month,
                    today,
                    (historyResult as Result.Success).value,
                    type.policy,
                )
            if (awardResult is Result.Failed) return@run awardResult
            val award = (awardResult as Result.Success).value
            val frequency = requireNotNull(type.policy.accrual).frequency
            val previousFrequency = entitlements.frequency(company, employeeId, typeId, month.year)
            if (previousFrequency is Result.Failed) return@run previousFrequency
            val established = (previousFrequency as Result.Success).value
            if (established != null && established != frequency)
                return@run Result.Failed(
                    Failure(
                        FailureKind.CONFLICT,
                        "leave_accrual_frequency_changed",
                        parameters = mapOf("year" to month.year.toString()),
                    )
                )
            val existing = entitlements.posting(company, employeeId, typeId, award.period)
            if (existing is Result.Failed) return@run existing
            if ((existing as Result.Success).value != null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_accrual_already_posted")
                )
            if (balance.availableHalfDays.toLong() + award.halfDays > Int.MAX_VALUE)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_balance_limit"))
            val posting =
                LeaveAccrualPosting(
                    id,
                    employeeId,
                    typeId,
                    month,
                    award,
                    frequency,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    employeeVersion,
                    balance.version,
                    actor.accountId,
                    now,
                    reason,
                )
            val entry =
                LeaveLedgerEntry(
                    UUID.randomUUID(),
                    employeeId,
                    typeId,
                    month.year,
                    LeaveLedgerKind.GRANT,
                    id,
                    null,
                    award.halfDays,
                    0,
                    0,
                    actor.accountId,
                    now,
                    reason,
                )
            val receipt = MutationReceipt(id, 0)
            entitlements
                .createPosting(company, posting)
                .flatMap { ledger.append(company, listOf(entry)) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "leave_accrual",
                            id,
                            "leave.accrual_posted",
                            mapOf(
                                "employeeId" to employeeId.toString(),
                                "typeId" to typeId.toString(),
                                "month" to month.toString(),
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
