package dev.fajar.hris

import dev.fajar.hris.payroll.delivery.mappers.toTerms
import dev.fajar.hris.payroll.delivery.requests.PayrollTaxOpeningRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollTaxOpeningAccessHttpTest : PayrollTaxOpeningApiFixture() {
    @Test
    fun permissionRemovalWhileWaitingPreventsDraftAndVerificationWritesIncludingReplay() {
        for (review in listOf(false, true)) {
            val f = openingFixture()
            val member = if (review) f.reviewer else f.preparer
            if (review) payrollBody(opening(f))
            val permission = if (review) "payroll.review" else "payroll.calculate"
            val key = UUID.randomUUID()
            val barrier = AccountLockProbe.Barrier(member.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> {
                        if (review) verify(f, key = key) else opening(f, key = key)
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
            assertEquals(
                if (review) 1 else 0,
                count(f.payroll.company, "payroll_tax_opening_revisions"),
            )
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.payroll.company,
                    member.account,
                    permission,
                )
            val original = payrollBody(if (review) verify(f, key = key) else opening(f, key = key))
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                    f.payroll.company,
                    member.account,
                    permission,
                )
            payrollError(
                if (review) verify(f, key = key) else opening(f, key = key),
                403,
                "access_denied",
            )
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.payroll.company,
                    member.account,
                    permission,
                )
            assertEquals(
                original,
                payrollBody(if (review) verify(f, key = key) else opening(f, key = key)),
            )
        }
    }

    @Test
    fun credentialsAndRecentAuthenticationAreRecheckedBeforeVerificationCommits() {
        for (revoke in listOf(true, false)) {
            val f = openingFixture()
            payrollBody(opening(f))
            val barrier = AccountLockProbe.Barrier(f.reviewer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<java.net.http.HttpResponse<String>> { verify(f) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (revoke)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.reviewer.account,
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
            assertEquals(1, count(f.payroll.company, "payroll_tax_opening_revisions"))
            clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        }
    }

    @Test
    fun aNewlyGrantedReadCapabilityCannotExpandAnAlreadyResolvedRequest() {
        val f = openingFixture()
        payrollBody(opening(f))
        val reader = payrollMember(f.payroll.company, setOf("company.read", "payroll.calculate"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<java.net.http.HttpResponse<String>> {
                    get(reader.client, openingPath(f))
                }
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
        payrollBody(get(reader.client, openingPath(f)))
    }

    @Test
    fun cancellationAfterWritingOpeningHistoryRollsBackAndReleasesTheThread() {
        val f = openingFixture()
        val key = UUID.randomUUID()
        val request = json.readValue(openingBody(), PayrollTaxOpeningRequest::class.java)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.tax_opening_saved") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                Thread.currentThread().interrupt()
                throw InterruptedException("Cancelled fixture")
            }
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit {
                    try {
                        saveOpening.execute(
                            payrollActor(f.payroll, f.preparer),
                            key,
                            f.payroll.employee,
                            2026,
                            request.terms.toTerms(),
                            null,
                            0,
                            request.reason,
                        )
                    } finally {
                        exited.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                release.countDown()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failure.cause)
                assertTrue(exited.await(5, TimeUnit.SECONDS))
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
        assertEquals(0, count(f.payroll.company, "payroll_tax_openings"))
        assertEquals(0, count(f.payroll.company, "payroll_tax_opening_revisions"))
        payrollBody(opening(f, key = key))
    }
}
