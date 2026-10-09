package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.validateLifecycleCompletion
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CompleteOffboarding(
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val roles: RoleTemplateRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        employmentVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val required = setOf("people.manage", "people.offboard", "people.lifecycle.manage")
        if (actor.companyId == null || !actor.permissions.containsAll(required))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "offboarding_access_required"))
        if (
            expectedVersion < 0 || employmentVersion < 0 || reason.isBlank() || reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_offboarding"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.offboarding_complete",
                operationId,
                listOf(
                    id.toString(),
                    expectedVersion.toString(),
                    employmentVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val structure = people.lockReportingLines(company)
            if (structure is Result.Failed) return@run structure
            val locked = lifecycle.lockCase(company, id)
            if (locked is Result.Failed) return@run locked
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            val actorLock = identities.lockAccount(actor.accountId)
            if (actorLock is Result.Failed) return@run actorLock
            val authorization = identities.access(actor.accountId, company)
            if (authorization is Result.Failed) return@run authorization
            val access = (authorization as Result.Success).value
            if (
                access == null ||
                    !access.account.active ||
                    (actor.credentialVersion != null &&
                        access.account.securityVersion != actor.credentialVersion)
            )
                return@run Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
            if (
                !access.membershipActive ||
                    !access.companyActive ||
                    !access.permissions.containsAll(required)
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "offboarding_access_required")
                )
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
            val found = lifecycle.case(company, id)
            if (found is Result.Failed) return@run found
            val case =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found")
                    )
            val valid =
                validateLifecycleCompletion(
                    case,
                    expectedVersion,
                    LifecycleKind.OFFBOARDING,
                    reason,
                )
            if (valid is Result.Failed) return@run valid
            val otherCases = lifecycle.hasOpenCases(company, case.employmentId, case.id)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "other_lifecycle_cases_pending")
                )
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val zone =
                (companyResult as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.now(clock.withZone(ZoneId.of(zone)))
            if (!case.targetDate.isBefore(today))
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "offboarding_date_not_reached")
                )
            val employeeResult = people.find(company, case.employmentId, today)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != employmentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            if (
                case.targetDate < employee.terms.startDate ||
                    (employee.terms.endDate != null && employee.terms.endDate != case.targetDate)
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "offboarding_terms_changed"))
            if (employee.person.accountId == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "cannot_complete_own_offboarding")
                )
            val scheduled = people.hasRevisionsAfter(company, employee.id, today)
            if (scheduled is Result.Failed) return@run scheduled
            if ((scheduled as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "scheduled_employment_changes_pending")
                )
            val reports = people.hasReportingDependentsAtOrAfter(company, employee.id, today)
            if (reports is Result.Failed) return@run reports
            if ((reports as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "reporting_reassignment_required")
                )
            val accountId = employee.person.accountId
            val departure =
                if (accountId == null) null
                else {
                    val remaining =
                        people.hasOpenEmploymentAtOrAfter(
                            company,
                            employee.person.id,
                            employee.id,
                            today,
                        )
                    if (remaining is Result.Failed) return@run remaining
                    val result = members.find(company, accountId)
                    if (result is Result.Failed) return@run result
                    val member = (result as Result.Success).value
                    if (
                        (remaining as Result.Success).value ||
                            member == null ||
                            !member.membershipActive
                    )
                        null
                    else {
                        if ("identity.manage" in member.permissions) {
                            val other =
                                members.hasOtherActiveMember(company, accountId, "identity.manage")
                            if (other is Result.Failed) return@run other
                            if (!(other as Result.Success).value)
                                return@run Result.Failed(
                                    Failure(FailureKind.CONFLICT, "last_company_administrator")
                                )
                        }
                        val applied = roles.application(company, accountId, member.version)
                        if (applied is Result.Failed) return@run applied
                        MembershipDeparture(
                            member,
                            (applied as Result.Success).value
                                ?: MembershipGrant(member.permissions, emptyList()),
                        )
                    }
                }
            if (employee.terms.status != EmploymentStatus.ENDED) {
                // Record the closure from today's effective boundary; prior snapshots are never
                // rewritten.
                val ended =
                    employee.terms.copy(
                        effectiveFrom = today,
                        endDate = case.targetDate,
                        status = EmploymentStatus.ENDED,
                        managerId = null,
                    )
                val revised = people.revise(actor, employee.id, employmentVersion, ended, reason)
                if (revised is Result.Failed) return@run revised
            }
            if (departure != null) {
                val member = departure.member
                val revoked =
                    members.save(company, member.id, member.version, false, member.permissions)
                if (revoked is Result.Failed) return@run revoked
                val history =
                    roles
                        .recordApplication(
                            actor,
                            member.id,
                            (revoked as Result.Success).value.version,
                            departure.grant,
                        )
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "company_membership",
                                    member.id,
                                    "people.departure_access_revoked",
                                    mapOf("employmentId" to employee.id.toString()),
                                    reason,
                                ),
                            )
                        }
                if (history is Result.Failed) return@run history
            }
            val event =
                LifecycleEvent(
                    case.version + 1,
                    null,
                    LifecycleAction.COMPLETED,
                    null,
                    actor.accountId,
                    reason,
                    clock.instant(),
                )
            lifecycle.finish(actor, id, case.version, LifecycleStatus.COMPLETED, event).flatMap {
                receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "employment",
                                employee.id,
                                "people.offboarding_completed",
                                mapOf(
                                    "caseId" to id.toString(),
                                    "lastWorkingDate" to case.targetDate.toString(),
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
