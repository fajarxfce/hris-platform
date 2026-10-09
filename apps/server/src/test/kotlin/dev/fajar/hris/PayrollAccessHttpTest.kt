package dev.fajar.hris

import dev.fajar.hris.payroll.delivery.mappers.toTerms
import dev.fajar.hris.payroll.delivery.requests.CompensationRequest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAccessHttpTest : PayrollApiFixture() {
    @Test
    fun permissionRemovedWhileWaitingPreventsBothPolicyAndSalaryWrites() {
        for (kind in listOf("policy", "compensation")) {
            val f = payrollFixture()
            val key = UUID.randomUUID()
            val permission = "payroll.$kind.manage"
            val barrier = AccountLockProbe.Barrier(f.operator.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> {
                        if (kind == "policy") policy(f, key = key) else compensation(f, key = key)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            f.operator.account,
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
                0,
                count(
                    f.company,
                    if (kind == "policy") "payroll_policies" else "employee_compensations",
                ),
            )
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    f.operator.account,
                    permission,
                )
            payrollBody(if (kind == "policy") policy(f, key = key) else compensation(f, key = key))
        }
    }

    @Test
    fun credentialsAndRecentAuthenticationAreRecheckedBeforeAnyMutation() {
        for (revoke in listOf(true, false)) {
            val f = payrollFixture()
            val barrier = AccountLockProbe.Barrier(f.operator.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<java.net.http.HttpResponse<String>> { compensation(f) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (revoke)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.operator.account,
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
            assertEquals(0, count(f.company, "employee_compensations"))
            clock.set(java.time.Instant.parse("2026-10-01T00:00:00Z"))
        }
    }

    @Test
    fun aNewPermissionCannotExpandAnAlreadyResolvedSalaryRead() {
        val f = payrollFixture()
        payrollBody(compensation(f))
        val reader = payrollMember(f.company, setOf("company.read", "payroll.policy.manage"))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        val path =
            "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation?asOf=2026-01"
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<java.net.http.HttpResponse<String>> { get(reader.client, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.compensation.manage')",
                        f.company,
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
    fun cancellationAfterSalaryInsertionRollsBackTheEntireTransactionAndReleasesTheWorker() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val request = json.readValue(compensationBody(), CompensationRequest::class.java)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        payrollProbe.beforeJournal = {
            if (it.action == "payroll.compensation_saved") {
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
                        saveCompensation.execute(
                            payrollActor(f),
                            key,
                            f.employee,
                            request.effectiveFrom,
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
            } finally {
                release.countDown()
                payrollProbe.clear()
            }
        }
        assertEquals(0, count(f.company, "employee_compensations"))
        assertEquals(0, count(f.company, "employee_compensation_revisions"))
        payrollBody(compensation(f, key = key))
    }
}
