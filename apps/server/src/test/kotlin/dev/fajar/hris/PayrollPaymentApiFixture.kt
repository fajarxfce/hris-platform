package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.usecases.ReconcilePayrollPaymentBatch
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@Import(PayrollPaymentProbeConfiguration::class)
@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
abstract class PayrollPaymentApiFixture : PayrollFinalizationApiFixture() {
    @Autowired protected lateinit var paymentProbe: PayrollPaymentProbe
    @Autowired protected lateinit var reconcilePayroll: ReconcilePayrollPaymentBatch

    @AfterEach
    fun clearPaymentProbe() {
        paymentProbe.clear()
    }

    protected data class PaymentFixture(
        val publication: PublicationFixture,
        val maker: PayrollMember,
        val checker: PayrollMember,
        val assessments: List<UUID>,
        val ownedAssessment: UUID,
    ) {
        val payroll
            get() = publication.calculation.people.payroll

        val path
            get() = "/api/v1/companies/${payroll.company}/payroll/payments"

        val progressPath
            get() =
                "/api/v1/companies/${payroll.company}/payroll/payslips/$ownedAssessment/payments"
    }

    protected fun paymentFixture(twoEmployees: Boolean = false): PaymentFixture {
        val c = calculationFixture(if (twoEmployees) 1 else 0)
        val f = c.people
        if (twoEmployees) {
            val extra =
                database()
                    .queryForObject(
                        "select employment_id from payroll_period_members where company_id=? and period_id=? and employment_id<>?",
                        UUID::class.java,
                        f.payroll.company,
                        c.period,
                        f.payroll.employee,
                    )!!
            payrollBody(
                compensation(
                    f.payroll,
                    compensationBody(termChanges = mapOf("payBasis" to runPayBasis())),
                    employee = extra,
                )
            )
            payrollBody(
                inputSave(
                    f,
                    c.work,
                    inputBody(c.work, termChanges = mapOf("dayResolutions" to runPaidDays())),
                    employee = extra,
                )
            )
            payrollBody(inputVerify(f, employee = extra))
            val other = f.copy(payroll = f.payroll.copy(employee = extra))
            payrollBody(saveRunOpening(other))
            payrollBody(verifyRunOpening(other))
        }
        val publication = approved(c)
        assertTrue(stepFinalization(publication, beginFinalization(publication)) is Result.Success)
        val ids =
            database()
                .query(
                    "select id from payroll_assessments where company_id=? order by id",
                    { rs, _ -> UUID.fromString(rs.getString(1)) },
                    f.payroll.company,
                )
        val own =
            database()
                .queryForObject(
                    "select id from payroll_assessments where company_id=? and employment_id=?",
                    UUID::class.java,
                    f.payroll.company,
                    f.payroll.employee,
                )!!
        return PaymentFixture(
            publication,
            payrollMember(f.payroll.company, setOf("company.read", "payroll.pay")),
            payrollMember(f.payroll.company, setOf("company.read", "payroll.pay")),
            ids,
            own,
        )
    }

    protected fun instruction(
        assessment: UUID,
        id: UUID = UUID.randomUUID(),
        accountName: String = "Verified recipient",
    ) =
        mapOf(
            "id" to id,
            "assessmentId" to assessment,
            "destination" to
                mapOf(
                    "bankCode" to "014",
                    "accountNumber" to "001234567890",
                    "accountName" to accountName,
                ),
        )

    protected fun preparePayrollPayment(
        f: PaymentFixture,
        id: UUID,
        items: List<Map<String, Any>>,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.maker,
    ) =
        command(
            member.client,
            "${f.path}/$id",
            json.writeValueAsString(
                mapOf(
                    "title" to "Monthly payroll",
                    "items" to items,
                    "reason" to "Verified payment destinations",
                )
            ),
            member.csrf,
            key,
            "PUT",
        )

    protected fun payrollPaymentAction(
        f: PaymentFixture,
        id: UUID,
        action: String,
        version: Long,
        member: PayrollMember = f.checker,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            member.client,
            "${f.path}/$id/$action",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Independent finance review")
            ),
            member.csrf,
            key,
        )

    protected fun result(
        item: UUID,
        success: Boolean = true,
        reference: String? = if (success) "BANK:${UUID.randomUUID()}" else null,
        confirmed: Boolean = !success,
        at: Instant = clock.instant().plusMillis(1).also { clock.set(it) },
    ): Map<String, Any?> =
        mapOf(
            "itemId" to item,
            "status" to if (success) "SUCCEEDED" else "FAILED",
            "transactionReference" to reference,
            "occurredAt" to at.toString(),
            "reason" to "Confirmed bank outcome",
            "confirmedNoTransfer" to confirmed,
        )

    protected fun reconcilePayment(
        f: PaymentFixture,
        id: UUID,
        version: Long,
        results: List<Map<String, Any?>>,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.checker,
    ) =
        command(
            member.client,
            "${f.path}/$id/reconcile",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "results" to results,
                    "reason" to "Statement reconciled",
                )
            ),
            member.csrf,
            key,
        )

    protected fun paymentView(f: PaymentFixture, id: UUID, member: PayrollMember = f.maker) =
        payrollBody(get(member.client, "${f.path}/$id"))

    protected fun progress(f: PaymentFixture) =
        payrollBody(get(f.payroll.owner.client, f.progressPath))

    protected fun dateRange() =
        LocalDate.now(clock.withZone(ZoneId.of("Asia/Jakarta"))).let { "from=$it&until=$it" }
}
