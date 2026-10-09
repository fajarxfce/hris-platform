package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPayslip
import dev.fajar.hris.payroll.domain.policies.canReadPayrollPayslips
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID

class GetPayrollPayslip(
    private val assessments: PayrollAssessmentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<PayrollPayslip> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollPayslips(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val current = (checked as Result.Success).value
            if (!canReadPayrollPayslips(current))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val allowedEmployees =
                if ("payroll.read" in current.permissions) null
                else {
                    val owned = people.employeeIdsForAccount(company, actor.accountId, 201)
                    if (owned is Result.Failed) return@run owned
                    val ids = (owned as Result.Success).value
                    if (ids.size > 200)
                        return@run Result.Failed(
                            Failure(
                                FailureKind.VALIDATION,
                                "payroll_scope_limit",
                                parameters = mapOf("maximumEmployments" to "200"),
                            )
                        )
                    ids.toSet()
                }
            assessments.payslip(company, id, allowedEmployees).flatMap {
                if (it == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_payslip_not_found"))
                else Result.Success(it)
            }
        }
    }
}
