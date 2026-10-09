package dev.fajar.hris.sync.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.policies.*
import dev.fajar.hris.sync.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit

class GetMobileSyncChanges(
    private val sync: SyncRepository,
    private val cursors: SyncCursorRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, token: String, limit: Int = 100): Result<SyncChangePage> {
        val companyId =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (selfSyncCollections(actor.permissions).isEmpty())
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "sync_access_denied"))
        if (limit !in 1..200)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_sync_limit",
                    parameters = mapOf("maximum" to "200"),
                )
            )
        val decoded = cursors.decode(token)
        if (decoded is Result.Failed) return decoded
        val cursor = (decoded as Result.Success).value
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(companyId, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(companyId, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(companyId, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, companyId)
            if (access is Result.Failed) return@run access
            val identity = (access as Result.Success).value
            val checked = validateCompanyCommandActor(actor, identity)
            if (checked is Result.Failed) return@run checked
            val current = (checked as Result.Success).value
            val collections = selfSyncCollections(current.permissions)
            if (collections.isEmpty())
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "sync_access_denied"))
            val companyResult = companies.find(companyId)
            if (companyResult is Result.Failed) return@run companyResult
            val company =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "company_access_denied")
                    )
            val memberResult = members.find(companyId, actor.accountId)
            if (memberResult is Result.Failed) return@run memberResult
            val member =
                (memberResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "company_access_denied")
                    )
            val owned = people.employeeIdsForAccount(companyId, actor.accountId, 201)
            if (owned is Result.Failed) return@run owned
            val employmentIds = (owned as Result.Success).value
            if (employmentIds.size > 200)
                return@run Result.Failed(
                    Failure(
                        FailureKind.VALIDATION,
                        "sync_scope_limit",
                        parameters = mapOf("maximumEmployments" to "200"),
                    )
                )
            val scope =
                SyncScope(
                    actor.accountId,
                    companyId,
                    requireNotNull(identity).account.securityVersion,
                    member.version,
                    company.version,
                    current.permissions,
                    employmentIds.toSet(),
                    collections,
                )
            val storedHead = sync.head(companyId)
            if (storedHead is Result.Failed) return@run storedHead
            val head = (storedHead as Result.Success).value
            val scopeHash = cursors.fingerprint(scope)
            if (scopeHash is Result.Failed) return@run scopeHash
            val fingerprint = (scopeHash as Result.Success).value

            val valid =
                validateSyncCursor(
                    cursor,
                    scope,
                    fingerprint,
                    head,
                    SyncCursorPhase.CHANGES,
                    clock.instant(),
                )
            if (valid is Result.Failed) return@run valid
            val upper = cursor.upperPosition ?: head.position
            val loaded = sync.changes(scope, cursor.position, upper, limit + 1)
            if (loaded is Result.Failed) return@run loaded
            val rows = (loaded as Result.Success).value
            val items = rows.take(limit)
            val more = rows.size > limit
            val pending = sync.pending(scope)
            if (pending is Result.Failed) return@run pending
            val timely = validateSyncCursorTime(cursor, clock.instant())
            if (timely is Result.Failed) return@run timely
            val now = clock.instant().truncatedTo(ChronoUnit.SECONDS)
            val next =
                if (more) cursor.copy(position = items.last().position, upperPosition = upper)
                else
                    cursor.copy(
                        position = upper,
                        upperPosition = null,
                        issuedAt = now,
                        expiresAt = now.plusSeconds(604800),
                    )
            cursors.encode(next).map { encoded ->
                SyncChangePage(
                    items,
                    encoded,
                    more,
                    (pending as Result.Success).value,
                    clock.instant(),
                )
            }
        }
    }
}
