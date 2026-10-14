package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.DirectoryRecord
import dev.fajar.hris.people.domain.repositories.DirectoryProjectionRepository
import java.time.Clock
import java.util.UUID

class GetDirectoryRecord(
    private val directory: DirectoryProjectionRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<DirectoryRecord> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (actor.permissions.none { it in setOf("people.directory.read", "people.read") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, company)
            if (access is Result.Failed) return@run access
            val identity = (access as Result.Success).value
            val checked = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if (
                (checked as Result.Success).value.permissions.none {
                    it in setOf("people.directory.read", "people.read")
                }
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = directory.find(company, id)
            if (found is Result.Failed) return@run found
            val timely = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (timely is Result.Failed) return@run timely
            val record = (found as Result.Success).value
            if (record?.employee == null)
                Result.Failed(Failure(FailureKind.NOT_FOUND, "directory_employee_not_found"))
            else Result.Success(record)
        }
    }
}
