package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentSyncHttpTest : PayrollPaymentApiFixture() {
    @Test
    fun paymentVersionsIncreaseAcrossFailedAndSuccessfulAttemptsWhilePayslipsStayImmutable() {
        val f = paymentFixture()
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        val path = "/api/v1/companies/${f.payroll.company}/sync"
        val bootstrap = payrollBody(get(f.payroll.owner.client, "$path/bootstrap"))
        assertEquals(1, bootstrap["items"].size())
        val original =
            payrollBody(
                get(
                    f.payroll.owner.client,
                    "/api/v1/companies/${f.payroll.company}/payroll/payslips/${f.ownedAssessment}",
                )
            )
        for (success in listOf(false, true)) {
            val id = UUID.randomUUID()
            val item = UUID.randomUUID()
            payrollBody(
                preparePayrollPayment(
                    f,
                    id,
                    listOf(instruction(f.ownedAssessment, item, "Private bank destination")),
                )
            )
            payrollBody(payrollPaymentAction(f, id, "release", 0))
            payrollBody(reconcilePayment(f, id, 1, listOf(result(item, success))))
        }
        assertTrue(worker.maintain.execute() is Result.Success)
        val changes =
            payrollBody(
                get(
                    f.payroll.owner.client,
                    "$path/changes?cursor=${URLEncoder.encode(bootstrap["changesCursor"].asString(),Charsets.UTF_8)}",
                )
            )
        val items =
            changes["items"]
                .iterator()
                .asSequence()
                .filter { it["collection"].asString() == "PAYROLL_PAYMENTS" }
                .toList()
        assertEquals((1L..6L).toList(), items.map { it["version"].asLong() })
        assertTrue(items.all { it["id"].asString() == f.ownedAssessment.toString() })
        assertEquals(6, progress(f)["version"].asLong())
        assertEquals(2, progress(f)["attempts"].size())
        assertFalse(progress(f).toString().contains("Private bank destination"))
        assertEquals(
            original,
            payrollBody(
                get(
                    f.payroll.owner.client,
                    "/api/v1/companies/${f.payroll.company}/payroll/payslips/${f.ownedAssessment}",
                )
            ),
        )
        val snapshot = payrollBody(get(f.payroll.owner.client, "$path/bootstrap"))
        assertEquals(
            setOf("PAYSLIPS", "PAYROLL_PAYMENTS"),
            snapshot["items"].iterator().asSequence().map { it["collection"].asString() }.toSet(),
        )
        val unbound = payrollMember(f.payroll.company, setOf("company.read", "payroll.self.read"))
        assertEquals(0, payrollBody(get(unbound.client, "$path/bootstrap"))["items"].size())
        payrollError(get(unbound.client, f.progressPath), 404, "payroll_payslip_not_found")
        payrollError(
            get(
                f.payroll.owner.client,
                "${f.path}/${progress(f)["attempts"][0]["batchId"].asString()}",
            ),
            403,
            "access_denied",
        )
    }
}
