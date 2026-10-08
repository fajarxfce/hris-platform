package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class)
class ExpensePaymentSecurityHttpTest : ExpensePaymentApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @Test
    fun competingBatchesCannotReserveTheSameApprovedClaim() {
        val f = expenseFixture()
        expenseTemplate(f)
        val one = payer(f)
        val two = payer(f)
        val submission = approvedExpense(f)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val attempts =
                listOf(one, two).map { operator ->
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(go.await(5, TimeUnit.SECONDS))
                        preparePayment(
                            f,
                            operator,
                            UUID.randomUUID(),
                            listOf(paymentInstruction(submission)),
                        )
                    }
                }
            go.countDown()
            val results = attempts.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(
                listOf(200, 409),
                results.map { it.statusCode() }.sorted(),
                results.map { it.body() }.toString(),
            )
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_payment_batches where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_payment_items where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun cancellationAndReleaseCannotBothSucceedAndCompetingReconciliationsUseOneVersion() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        val instruction = paymentInstruction(submission)
        assertEquals(200, preparePayment(f, maker, batch, listOf(instruction)).statusCode())
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val release =
                pool.submit<java.net.http.HttpResponse<String>> {
                    check(go.await(5, TimeUnit.SECONDS))
                    paymentAction(f, checker, batch, "release", 0)
                }
            val cancel =
                pool.submit<java.net.http.HttpResponse<String>> {
                    check(go.await(5, TimeUnit.SECONDS))
                    paymentAction(f, maker, batch, "cancel", 0)
                }
            go.countDown()
            assertEquals(
                listOf(200, 409),
                listOf(
                        release.get(15, TimeUnit.SECONDS).statusCode(),
                        cancel.get(15, TimeUnit.SECONDS).statusCode(),
                    )
                    .sorted(),
            )
        }
        val payBatch =
            if (paymentDetails(f, maker, batch).get("status").asString() == "CANCELLED") {
                val replacement = UUID.randomUUID()
                assertEquals(
                    200,
                    preparePayment(f, maker, replacement, listOf(paymentInstruction(submission)))
                        .statusCode(),
                )
                assertEquals(200, paymentAction(f, checker, replacement, "release", 0).statusCode())
                replacement
            } else batch
        val item =
            UUID.fromString(
                paymentDetails(f, maker, payBatch).get("items").get(0).get("id").asString()
            )
        val settle = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val attempts =
                listOf(maker, checker).map { operator ->
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(settle.await(5, TimeUnit.SECONDS))
                        reconcilePayment(f, operator, payBatch, 1, listOf(paymentResult(item)))
                    }
                }
            settle.countDown()
            assertEquals(
                listOf(200, 409),
                attempts.map { it.get(15, TimeUnit.SECONDS).statusCode() }.sorted(),
            )
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_payment_results where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun makersBeneficiariesAndNewlyBoundAccountsCannotAuthorizeTheirOwnPayments() {
        val original = expenseFixture()
        expenseTemplate(original)
        val submission = approvedExpense(original)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.pay')",
                original.company,
                original.account,
            )
        val self = reviewer(original, original.account)
        assertEquals(
            403,
            preparePayment(
                    original,
                    self,
                    UUID.randomUUID(),
                    listOf(paymentInstruction(submission)),
                )
                .statusCode(),
        )
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
        val pending = pendingExpense(f, asAdmin = true)
        assertEquals(200, reviewExpense(f, pending, reviewer(f)).statusCode())
        val maker = payer(f)
        val checker = payer(f)
        val independent = payer(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(pending))).statusCode(),
        )
        val link =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to checker.account,
                        "expectedVersion" to 0,
                        "reason" to "Identity verified",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, link.statusCode(), link.body())
        assertEquals(403, paymentAction(f, checker, batch, "release", 0).statusCode())
        assertEquals(200, paymentAction(f, independent, batch, "release", 0).statusCode())
        val item =
            UUID.fromString(
                paymentDetails(f, maker, batch).get("items").get(0).get("id").asString()
            )
        assertEquals(
            403,
            reconcilePayment(f, checker, batch, 1, listOf(paymentResult(item))).statusCode(),
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.pay')",
                f.company,
                adminAccount(),
            )
        val admin = reviewer(f, adminAccount())
        assertEquals(
            403,
            reconcilePayment(f, admin, batch, 1, listOf(paymentResult(item))).statusCode(),
        )
    }

    @Test
    fun bankDetailsRequireProtectedFinanceAccessAndCurrentCompanyScope() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(submission))).statusCode(),
        )
        val path = "/api/v1/companies/${f.company}/expenses/payments/$batch"
        for (suffix in listOf("", "/history", "/results", "/export")) {
            assertEquals(403, get(f.admin, path + suffix).statusCode())
            assertEquals(403, get(f.worker, path + suffix).statusCode())
        }
        val otherCompany = company(f.admin, f.adminCsrf)
        val otherPayer = payer(f.copy(company = otherCompany))
        assertEquals(
            404,
            get(otherPayer.browser, "/api/v1/companies/$otherCompany/expenses/payments/$batch")
                .statusCode(),
        )
        val key = UUID.randomUUID()
        assertEquals(200, paymentAction(f, checker, batch, "release", 0, key).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.pay'",
                f.company,
                checker.account,
            )
        assertEquals(403, paymentAction(f, checker, batch, "release", 0, key).statusCode())
        assertEquals(403, get(checker.browser, path + "/export").statusCode())
    }

    @Test
    fun everyPaymentCommandRechecksAuthorityAfterWaitingAndDoesNotConsumeFailedKeys() {
        for (action in listOf("prepare", "release", "cancel", "reconcile")) {
            val f = expenseFixture()
            expenseTemplate(f)
            val maker = payer(f)
            val checker = payer(f)
            val submission = approvedExpense(f)
            val batch = UUID.randomUUID()
            val item = paymentInstruction(submission)
            if (action != "prepare")
                assertEquals(200, preparePayment(f, maker, batch, listOf(item)).statusCode())
            if (action == "reconcile")
                assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
            val operator = if (action == "release") checker else maker
            val key = UUID.randomUUID()
            val results = listOf(paymentResult(item["id"] as UUID))
            val command = {
                when (action) {
                    "prepare" -> preparePayment(f, operator, batch, listOf(item), key)
                    "reconcile" -> reconcilePayment(f, operator, batch, 1, results, key)
                    else -> paymentAction(f, operator, batch, action, 0, key)
                }
            }
            val barrier = AccountLockProbe.Barrier(operator.account)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending = pool.submit<java.net.http.HttpResponse<String>> { command() }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.pay'",
                            f.company,
                            operator.account,
                        )
                    barrier.release.countDown()
                    val failed = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(403, failed.statusCode(), "$action ${failed.body()}")
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.pay')",
                    f.company,
                    operator.account,
                )
            val retry = command()
            assertEquals(200, retry.statusCode(), "$action ${retry.body()}")
        }
    }

    @Test
    fun releaseRechecksCredentialsAndRecentAuthenticationAfterWaiting() {
        for (mode in listOf("credential", "recent")) {
            val f = expenseFixture()
            expenseTemplate(f)
            val maker = payer(f)
            val checker = payer(f)
            val submission = approvedExpense(f)
            val batch = UUID.randomUUID()
            assertEquals(
                200,
                preparePayment(f, maker, batch, listOf(paymentInstruction(submission))).statusCode(),
            )
            val barrier = AccountLockProbe.Barrier(checker.account)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<java.net.http.HttpResponse<String>> {
                            paymentAction(f, checker, batch, "release", 0)
                        }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (mode == "credential")
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                checker.account,
                            )
                    else clock.set(clock.instant().plusSeconds(601))
                    barrier.release.countDown()
                    assertEquals(
                        if (mode == "credential") 401 else 403,
                        pending.get(15, TimeUnit.SECONDS).statusCode(),
                    )
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
                clock.set(Instant.now())
            }
            assertEquals("PREPARED", paymentDetails(f, maker, batch).get("status").asString())
        }
    }

    @Test
    fun auditFailureRollsBackPreparationReleaseAndSettlementWithoutConsumingReceipts() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        val item = paymentInstruction(submission)
        for (action in listOf("prepared", "released", "reconciled")) {
            val key = UUID.randomUUID()
            val result = listOf(paymentResult(item["id"] as UUID))
            val command = {
                when (action) {
                    "prepared" -> preparePayment(f, maker, batch, listOf(item), key)
                    "released" -> paymentAction(f, checker, batch, "release", 0, key)
                    else -> reconcilePayment(f, maker, batch, 1, result, key)
                }
            }
            database()
                .execute(
                    """create function fail_expense_payment_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$batch'::uuid and new.action='expenses.payment_$action' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
                )
            database()
                .execute(
                    "create trigger expense_payment_audit_probe before insert on audit_entries for each row execute function fail_expense_payment_audit()"
                )
            try {
                val failed = command()
                assertEquals(409, failed.statusCode(), failed.body())
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from expense_payment_results where company_id=? and batch_id=?",
                            Int::class.java,
                            f.company,
                            batch,
                        ),
                )
                if (action == "prepared")
                    assertEquals(
                        0,
                        database()
                            .queryForObject(
                                "select count(*) from expense_payment_items where company_id=? and batch_id=?",
                                Int::class.java,
                                f.company,
                                batch,
                            ),
                    )
                else
                    assertEquals(
                        if (action == "released") "PREPARED" else "RELEASED",
                        paymentDetails(f, maker, batch).get("status").asString(),
                    )
            } finally {
                database().execute("drop trigger expense_payment_audit_probe on audit_entries")
                database().execute("drop function fail_expense_payment_audit()")
            }
            val retry = command()
            assertEquals(200, retry.statusCode(), retry.body())
            assertEquals(retry.body(), command().body())
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_payment_items set account_number='999999999' where company_id=? and batch_id=?",
                    f.company,
                    batch,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_payment_results set status='FAILED',transaction_reference=null where company_id=? and batch_id=?",
                    f.company,
                    batch,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "delete from expense_payment_batches where company_id=? and id=?",
                    f.company,
                    batch,
                )
        }
    }
}
