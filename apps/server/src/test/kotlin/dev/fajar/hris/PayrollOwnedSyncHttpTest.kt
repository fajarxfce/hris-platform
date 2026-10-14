package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollOwnedSyncHttpTest : PayrollPaymentApiFixture() {
    @Test
    fun anExplicitFinanceReadProjectionRetainsOnlyOwnedPayslipsAndPaymentChanges() {
        val f = paymentFixture(twoEmployees = true)
        val p = f.payroll
        val path = "/api/v1/companies/${p.company}/sync"
        val selected = "collections=PAYSLIPS,PAYROLL_PAYMENTS"
        database()
            .update(
                "update membership_permissions set permission='payroll.read' where company_id=? and account_id=? and permission='payroll.self.read'",
                p.company,
                p.owner.account,
            )
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        payrollError(get(p.owner.client, "$path/bootstrap"), 403, "sync_access_denied")
        val initial = payrollBody(get(p.owner.client, "$path/bootstrap?$selected"))
        assertEquals(
            setOf("PAYSLIPS", "PAYROLL_PAYMENTS"),
            initial["collections"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertEquals(1, initial["items"].size())
        assertEquals(f.ownedAssessment.toString(), initial["items"][0]["id"].asString())
        assertEquals("PAYSLIPS", initial["items"][0]["collection"].asString())

        val payslips = "/api/v1/companies/${p.company}/payroll/payslips"
        assertEquals(2, payrollBody(get(p.owner.client, payslips))["items"].size())
        assertEquals(1, payrollBody(get(p.owner.client, "$payslips?scope=SELF"))["items"].size())
        val other = f.assessments.single { it != f.ownedAssessment }
        for (suffix in listOf("", "/payments")) {
            payrollError(
                get(p.owner.client, "$payslips/$other$suffix?scope=SELF"),
                404,
                "payroll_payslip_not_found",
            )
        }

        payrollBody(
            preparePayrollPayment(f, UUID.randomUUID(), f.assessments.map { instruction(it) })
        )
        assertTrue(worker.maintain.execute() is Result.Success)
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val changes = "$path/changes?cursor=$cursor&$selected"
        val delta = payrollBody(get(p.owner.client, changes))
        assertEquals(1, delta["items"].size())
        assertEquals("PAYROLL_PAYMENTS", delta["items"][0]["collection"].asString())
        assertEquals(f.ownedAssessment.toString(), delta["items"][0]["id"].asString())
        assertEquals(1, delta["items"][0]["version"].asLong())
        val snapshot = payrollBody(get(p.owner.client, "$path/bootstrap?$selected"))
        assertEquals(2, snapshot["items"].size())
        assertTrue(
            snapshot["items"].iterator().asSequence().all {
                it["id"].asString() == f.ownedAssessment.toString()
            }
        )
        assertEquals(
            0,
            payrollBody(get(p.operator.client, "$path/bootstrap?$selected"))["items"].size(),
        )
        payrollError(
            get(
                p.owner.client,
                "/api/v1/companies/${company(p.admin, p.adminCsrf)}/sync/bootstrap?$selected",
            ),
            403,
            "company_access_denied",
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.read'",
                p.company,
                p.owner.account,
            )
        payrollError(get(p.owner.client, changes), 403, "sync_access_denied")
        payrollError(
            get(p.owner.client, "$payslips/${f.ownedAssessment}?scope=SELF"),
            403,
            "access_denied",
        )
    }
}
