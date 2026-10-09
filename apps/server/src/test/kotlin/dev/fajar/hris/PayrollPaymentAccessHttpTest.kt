package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPaymentAccessHttpTest : PayrollPaymentApiFixture() {
    private fun whileWaiting(
        member: PayrollMember,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(member.account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun financeReadsRecheckRevocationUnderTheirResourceAndAccountGuards() {
        val f = paymentFixture()
        val id = UUID.randomUUID()
        payrollBody(preparePayrollPayment(f, id, listOf(instruction(f.ownedAssessment))))
        payrollBody(payrollPaymentAction(f, id, "release", 0))
        for (suffix in
            listOf(
                "?${dateRange()}",
                "/payables?${dateRange()}",
                "/$id",
                "/$id/history",
                "/$id/results",
                "/$id/export",
            )) {
            val reader = payrollMember(f.payroll.company, setOf("company.read", "payroll.pay"))
            val response =
                whileWaiting(
                    reader,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.pay'",
                                f.payroll.company,
                                reader.account,
                            )
                    },
                ) {
                    get(reader.client, f.path + suffix)
                }
            payrollError(response, 403, "access_denied")
        }
    }

    @Test
    fun lateCredentialRevocationAlsoBlocksCommandReceiptReplayAndEmployeeProgress() {
        val f = paymentFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val input = listOf(instruction(f.ownedAssessment))
        payrollBody(preparePayrollPayment(f, id, input, key))
        val replay =
            whileWaiting(
                f.maker,
                {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.maker.account,
                        )
                },
            ) {
                preparePayrollPayment(f, id, input, key)
            }
        payrollError(replay, 401, "session_revoked")
        val own =
            whileWaiting(
                f.payroll.owner,
                {
                    database()
                        .update(
                            "update company_memberships set active=false where company_id=? and account_id=?",
                            f.payroll.company,
                            f.payroll.owner.account,
                        )
                },
            ) {
                get(f.payroll.owner.client, f.progressPath)
            }
        payrollError(own, 403, "company_access_denied")
        assertEquals(1, count(f.payroll.company, "payroll_payment_actions"))
    }
}
