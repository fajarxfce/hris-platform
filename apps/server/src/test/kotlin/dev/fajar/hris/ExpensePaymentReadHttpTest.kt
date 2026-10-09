package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class ExpensePaymentReadHttpTest : ExpensePaymentApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private fun whileWaiting(
        account: UUID,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(
                    barrier.entered.await(5, TimeUnit.SECONDS),
                    "Read must guard live account access",
                )
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
    fun financeReadsRecheckRevocationUnderSharedGuards() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val batch = UUID.randomUUID()
        val submission = approvedExpense(f)
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(submission))).statusCode(),
        )
        assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
        val today = LocalDate.now(clock)
        val range = "from=${today.minusDays(1)}&until=${today.plusDays(1)}"
        for (suffix in
            listOf(
                "?$range",
                "/payables?$range",
                "/$batch",
                "/$batch/history",
                "/$batch/results",
                "/$batch/export",
            )) {
            val reader = payer(f)
            val response =
                whileWaiting(
                    reader.account,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.pay'",
                                f.company,
                                reader.account,
                            )
                    },
                ) {
                    get(reader.browser, "/api/v1/companies/${f.company}/expenses/payments$suffix")
                }
            assertEquals(403, response.statusCode(), response.body())
            assertEquals("access_denied", json.readTree(response.body()).get("code").asString())
        }
    }

    @Test
    fun financeReadsRecheckMembershipCredentialsAndRecentAuthentication() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f))))
                .statusCode(),
        )
        for (mode in listOf("membership", "credential", "recent")) {
            val reader = payer(f)
            val now = clock.instant()
            try {
                val response =
                    whileWaiting(
                        reader.account,
                        {
                            when (mode) {
                                "membership" ->
                                    database()
                                        .update(
                                            "update company_memberships set active=false where company_id=? and account_id=?",
                                            f.company,
                                            reader.account,
                                        )
                                "credential" ->
                                    database()
                                        .update(
                                            "update accounts set security_version=security_version+1 where id=?",
                                            reader.account,
                                        )
                                else -> clock.set(now.plusSeconds(601))
                            }
                        },
                    ) {
                        get(
                            reader.browser,
                            "/api/v1/companies/${f.company}/expenses/payments/$batch",
                        )
                    }
                assertEquals(
                    if (mode == "credential") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
            } finally {
                clock.set(now)
            }
        }
    }

    @Test
    fun employeeProgressRechecksPermissionAndCredentialsWithoutBankDetails() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f))))
                .statusCode(),
        )
        val path = "${f.path}/${f.claim}/payments"
        val before = get(f.worker, path)
        assertEquals(200, before.statusCode(), before.body())
        assertFalse(before.body().contains("accountNumber"))
        val denied =
            whileWaiting(
                f.account,
                {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                            f.company,
                            f.account,
                        )
                },
            ) {
                get(f.worker, path)
            }
        assertEquals(404, denied.statusCode(), denied.body())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.self.manage')",
                f.company,
                f.account,
            )
        val revoked =
            whileWaiting(
                f.account,
                {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.account,
                        )
                },
            ) {
                get(f.worker, path)
            }
        assertEquals(401, revoked.statusCode(), revoked.body())
    }

    @Test
    fun sharedReadDoesNotSerializeUnrelatedFinanceReaders() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val reader = payer(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f))))
                .statusCode(),
        )
        val response =
            whileWaiting(
                reader.account,
                {
                    val concurrent =
                        get(
                            maker.browser,
                            "/api/v1/companies/${f.company}/expenses/payments/$batch",
                        )
                    assertEquals(200, concurrent.statusCode(), concurrent.body())
                },
            ) {
                get(reader.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch")
            }
        assertEquals(200, response.statusCode(), response.body())
    }
}
