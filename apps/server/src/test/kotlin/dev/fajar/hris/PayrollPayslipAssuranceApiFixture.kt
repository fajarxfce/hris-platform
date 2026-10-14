package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPayslipReadScope
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

/** Fixture setup does not require MFA; the direct interactive readers do. */
abstract class PayrollPayslipAssuranceApiFixture : PayrollFinalizationApiFixture() {
    @Autowired private lateinit var assessments: PayrollAssessmentRepository
    @Autowired private lateinit var payments: PayrollPaymentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    protected val security = IdentitySecurityPolicy()

    protected data class Scenario(val company: UUID, val id: UUID, val actor: Actor)

    protected fun prepare(): Scenario {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val page =
            payrollBody(get(p.owner.client, "/api/v1/companies/${p.company}/payroll/payslips"))
        val id = UUID.fromString(page["items"][0]["id"].asString())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                p.owner.account,
            )
        return Scenario(
            p.company,
            id,
            payrollActor(p, p.owner).copy(mfaVerifiedAt = clock.instant()),
        )
    }

    protected fun invoke(scene: Scenario, action: String, actor: Actor = scene.actor): Result<*> =
        when (action) {
            "list",
            "search" ->
                ListPayrollPayslips(
                        assessments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        YearMonth.of(2026, 9),
                        YearMonth.of(2026, 9),
                        null,
                        null,
                        50,
                        PayrollPayslipReadScope.SELF,
                        query = if (action == "search") "Example" else "",
                    )
            "details" ->
                GetPayrollPayslip(
                        assessments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.id, PayrollPayslipReadScope.SELF)
            "payments" ->
                GetPayrollPayslipPayments(
                        payments,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.id, PayrollPayslipReadScope.SELF)
            else -> error("Unknown payslip read")
        }
}
