package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpResponse
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayslipAccessHttpTest : PayrollFinalizationApiFixture() {
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
    fun readsRejectPermissionMembershipAndCredentialRevocationDuringPendingWork() {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val id = payrollBody(get(p.owner.client, path))["items"][0]["id"].asString()
        for (suffix in listOf("", "/$id")) for (change in
            listOf("permission", "membership", "credentials")) {
            val reader = payrollMember(p.company, setOf("company.read", "payroll.read"))
            val response =
                whileWaiting(
                    reader,
                    {
                        when (change) {
                            "permission" ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.read'",
                                        p.company,
                                        reader.account,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        p.company,
                                        reader.account,
                                    )
                            else ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        reader.account,
                                    )
                        }
                    },
                ) {
                    get(reader.client, path + suffix)
                }
            payrollError(
                response,
                if (change == "credentials") 401 else 403,
                when (change) {
                    "permission" -> "access_denied"
                    "membership" -> "company_access_denied"
                    else -> "session_revoked"
                },
            )
        }
    }

    @Test
    fun anInflightSelfReadCannotGainNewFinanceAuthority() {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val id = payrollBody(get(p.owner.client, path))["items"][0]["id"].asString()
        for (suffix in listOf("", "/$id")) {
            val reader = payrollMember(p.company, setOf("company.read", "payroll.self.read"))
            val response =
                whileWaiting(
                    reader,
                    {
                        database()
                            .update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.read')",
                                p.company,
                                reader.account,
                            )
                    },
                ) {
                    get(reader.client, path + suffix)
                }
            if (suffix.isEmpty()) assertEquals(0, payrollBody(response)["items"].size())
            else payrollError(response, 404, "payroll_payslip_not_found")
            assertEquals(200, get(reader.client, path + suffix).statusCode())
        }
    }
}
