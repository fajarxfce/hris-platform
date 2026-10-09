package dev.fajar.hris

import dev.fajar.hris.payroll.delivery.mappers.toTerms
import dev.fajar.hris.payroll.delivery.requests.PayrollInputRequest
import java.time.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPeriodAccessHttpTest : PayrollPeriodApiFixture() {
    @Test
    fun revocationDuringCreateOrCancelPreventsAnyPeriodMutation() {
        for (cancel in listOf(false, true)) {
            val f = processingFixture()
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            val body = periodBody(f, id)
            if (cancel) payrollBody(periodCreate(f, body))
            val barrier = AccountLockProbe.Barrier(f.preparer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> {
                        if (cancel) periodCancel(f, id, key = key) else periodCreate(f, body, key)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                            f.payroll.company,
                            f.preparer.account,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(if (cancel) 1 else 0, count(f.payroll.company, "payroll_period_changes"))
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.calculate')",
                    f.payroll.company,
                    f.preparer.account,
                )
            payrollBody(if (cancel) periodCancel(f, id, key = key) else periodCreate(f, body, key))
        }
    }

    @Test
    fun revocationDuringInputPreparationOrVerificationPreservesThePreviousRevision() {
        for (review in listOf(false, true)) {
            val f = processingFixture()
            val source = closeWork(f)
            if (review) payrollBody(inputSave(f, source))
            val member = if (review) f.reviewer else f.preparer
            val permission = if (review) "payroll.review" else "payroll.calculate"
            val key = UUID.randomUUID()
            val barrier = AccountLockProbe.Barrier(member.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> {
                        if (review) inputVerify(f, key = key) else inputSave(f, source, key = key)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.payroll.company,
                            member.account,
                            permission,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(if (review) 1 else 0, count(f.payroll.company, "payroll_input_revisions"))
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.payroll.company,
                    member.account,
                    permission,
                )
            payrollBody(if (review) inputVerify(f, key = key) else inputSave(f, source, key = key))
        }
    }

    @Test
    fun credentialAndRecentAuthenticationChangesAreCheckedBeforeFinancialCommands() {
        for (revoke in listOf(true, false)) {
            val f = processingFixture()
            val source = closeWork(f)
            payrollBody(inputSave(f, source))
            val member = if (revoke) f.preparer else f.reviewer
            val barrier = AccountLockProbe.Barrier(member.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> {
                        if (revoke) periodCreate(f) else inputVerify(f)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (revoke)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                member.account,
                            )
                    else clock.set(clock.instant().plusSeconds(601))
                    barrier.release.countDown()
                    payrollError(
                        pending.get(10, TimeUnit.SECONDS),
                        if (revoke) 401 else 403,
                        if (revoke) "session_revoked" else "recent_authentication_required",
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(0, count(f.payroll.company, "payroll_periods"))
            assertEquals(1, count(f.payroll.company, "payroll_input_revisions"))
            clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        }
    }

    @Test
    fun sourceReadsCannotGainNewPermissionsWhileWaitingForAnAccountGuard() {
        val f = processingFixture()
        closeWork(f)
        val reader = payrollMember(f.payroll.company, setOf("company.read", "payroll.calculate"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        val path =
            "/api/v1/companies/${f.payroll.company}/payroll/employees/${f.payroll.employee}/work-source?month=2026-09"
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<java.net.http.HttpResponse<String>> { get(reader.client, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                        f.payroll.company,
                        reader.account,
                    )
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                        f.payroll.company,
                        reader.account,
                    )
                barrier.release.countDown()
                payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        payrollBody(get(reader.client, path))
    }

    @Test
    fun interruptionAfterInputWriteRollsBackItsEvidenceAndAllowsThreadReuse() {
        val f = processingFixture()
        val source = closeWork(f)
        val key = UUID.randomUUID()
        val request = json.readValue(inputBody(source), PayrollInputRequest::class.java)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.input_saved") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Thread.currentThread().interrupt()
                throw InterruptedException("Cancelled fixture")
            }
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit {
                    saveInput.execute(
                        payrollActor(f.payroll, f.preparer),
                        key,
                        f.payroll.employee,
                        YearMonth.of(2026, 9),
                        source.job,
                        source.version,
                        0,
                        request.terms.toTerms(),
                        null,
                        request.reason,
                    )
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failure.cause)
                assertEquals(
                    42,
                    executor
                        .submit<Int> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            42
                        }
                        .get(5, TimeUnit.SECONDS),
                )
            } finally {
                release.countDown()
                payrollProbe.clear()
            }
        }
        assertEquals(0, count(f.payroll.company, "payroll_inputs"))
        assertEquals(0, count(f.payroll.company, "payroll_input_revisions"))
        payrollBody(inputSave(f, source, key = key))
    }
}
