package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.validateShift
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.util.UUID

class SaveShift(
    private val schedules: ScheduleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        input: ShiftDetails,
        active: Boolean,
        version: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("workforce.manage")
        if (access is Result.Failed) return access
        val details = input.copy(code = input.code.trim().uppercase(), name = input.name.trim())
        val valid = validateShift(details)
        if (valid is Result.Failed) return valid
        if ((version ?: 0) < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_shift_change"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "workforce.shift_save",
                operationId,
                listOf(
                    id.toString(),
                    version?.toString(),
                    active.toString(),
                    reason,
                    details.code,
                    details.name,
                    details.startsAt.toString(),
                    details.endsAt.toString(),
                    details.breakMinutes.toString(),
                    details.timezone,
                    details.mode.name,
                    details.locationRequired.toString(),
                    details.maxAccuracyMeters.toString(),
                    details.fence?.latitude?.toString(),
                    details.fence?.longitude?.toString(),
                    details.fence?.radiusMeters?.toString(),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = schedules.lock(company)
            if (lock is Result.Failed) return@run lock
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            val permission = live.requirePermission("workforce.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            schedules.saveShift(actor, id, details, active, version, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord("shift", id, "workforce.shift_saved", reason = reason),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
