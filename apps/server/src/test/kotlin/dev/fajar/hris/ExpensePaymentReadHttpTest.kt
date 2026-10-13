package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneId
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
    fun payablePreparationHintsPreservePagingAndDoNotExposeInternalActorEvidence() {
        val original = expenseFixture()
        expenseTemplate(original)
        approvedExpense(original)
        val other =
            original.copy(
                employee =
                    employee(
                        original.admin,
                        original.adminCsrf,
                        original.company,
                        manager = original.manager,
                    ),
                claim = UUID.randomUUID(),
                line = UUID.randomUUID(),
            )
        val second = pendingExpense(other, asAdmin = true)
        assertEquals(200, reviewExpense(other, second, reviewer(other)).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.pay')",
                original.company,
                original.account,
            )
        val beneficiary = reviewer(original, original.account)
        val reader = payer(original)
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val path =
            "/api/v1/companies/${original.company}/expenses/payments/payables?from=$today&until=$today"
        val independent = get(reader.browser, path)
        assertEquals(200, independent.statusCode(), independent.body())
        val rows = json.readTree(independent.body())["items"]
        assertEquals(2, rows.size())
        for (row in rows) {
            assertEquals(
                listOf("PREPARE"),
                row["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
            )
            for (privateField in
                listOf("makerIds", "requesterId", "currentAccountId", "accountNumber")) assertFalse(
                row.has(privateField)
            )
        }
        val seen = mutableSetOf<UUID>()
        var cursor: String? = null
        repeat(2) {
            val response =
                get(beneficiary.browser, "$path&limit=1" + (cursor?.let { "&after=$it" } ?: ""))
            assertEquals(200, response.statusCode(), response.body())
            val page = json.readTree(response.body())
            assertEquals(1, page["items"].size())
            val row = page["items"][0]
            val claim = UUID.fromString(row["claimId"].asString())
            assertTrue(seen.add(claim))
            assertEquals(
                if (claim == original.claim) emptyList<String>() else listOf("PREPARE"),
                row["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
            )
            cursor = page["nextCursor"].takeUnless { it.isNull }?.asString()
        }
        assertEquals(setOf(original.claim, other.claim), seen)
        assertNull(cursor)
    }

    @Test
    fun bindingAnAccountWithdrawsItsPreparationHintAndTheCommandStillRechecksIndependence() {
        val original = expenseFixture()
        expenseTemplate(original)
        val f =
            original.copy(
                employee =
                    employee(
                        original.admin,
                        original.adminCsrf,
                        original.company,
                        manager = original.manager,
                    ),
                claim = UUID.randomUUID(),
                line = UUID.randomUUID(),
            )
        val submission = pendingExpense(f, asAdmin = true)
        assertEquals(200, reviewExpense(f, submission, reviewer(f)).statusCode())
        val operator = payer(f)
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val path =
            "/api/v1/companies/${f.company}/expenses/payments/payables?from=$today&until=$today"
        val before = get(operator.browser, path)
        assertEquals(200, before.statusCode(), before.body())
        assertEquals(
            listOf("PREPARE"),
            json
                .readTree(before.body())["items"][0]["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        val linked =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to operator.account,
                        "expectedVersion" to 0,
                        "reason" to "Verified employee account",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, linked.statusCode(), linked.body())
        val after = get(operator.browser, path)
        assertEquals(200, after.statusCode(), after.body())
        assertEquals(0, json.readTree(after.body())["items"][0]["availableActions"].size())
        val rejected =
            preparePayment(f, operator, UUID.randomUUID(), listOf(paymentInstruction(submission)))
        assertEquals(403, rejected.statusCode(), rejected.body())
        assertEquals(
            "independent_expense_payment_required",
            json.readTree(rejected.body())["code"].asString(),
        )
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
