package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAccountingAccessHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun everyBalanceCommandRechecksRevocationBeforeMutationOrReplay() {
        for (action in listOf("accrue", "close", "adjust")) {
            val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
            if (action == "close") accountingBody(adjust(f, "3"))
            val actor = accountingActor(f)
            val key = UUID.randomUUID()
            val payload = if (action == "accrue") accrualBody() else closingBody()
            val permission =
                when (action) {
                    "accrue" -> "leave.accrual.post"
                    "close" -> "leave.year.close"
                    else -> "leave.manage"
                }
            val barrier = AccountLockProbe.Barrier(actor.accountId)
            accountProbe.current.set(barrier)
            val before = accountingRows(f, "operation_receipts")
            val command = {
                when (action) {
                    "accrue" -> accrue(f, payload, key)
                    "close" -> closeYear(f, payload, key)
                    else -> adjust(f, "1", key, expectedVersion = 0)
                }
            }
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<HttpResponse<String>> { command() }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            actor.accountId,
                            permission,
                        )
                    barrier.release.countDown()
                    accountingCode(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, accountingRows(f, "operation_receipts"))
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    actor.accountId,
                    permission,
                )
            accountingBody(command())
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                    f.company,
                    actor.accountId,
                    permission,
                )
            accountingCode(command(), 403, "access_denied")
        }
    }

    @Test
    fun readAccessCannotExpandWhileARequestWaitsAndRevocationHidesCanonicalBalances() {
        val f = accountingFixture()
        accountingBody(accrue(f))
        val id = balance(f)["accountId"].asString()
        val path = "/api/v1/companies/${f.company}/leave/balances/$id"
        val actor = accountingActor(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.read'",
                f.company,
                actor.accountId,
            )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.read')",
                        f.company,
                        actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(404, pending.get(10, TimeUnit.SECONDS).statusCode())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        accountingBody(get(f.admin, path))
        assertEquals(200, get(f.supervisor, path).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.self.manage'",
                f.company,
                f.account,
            )
        assertEquals(404, get(f.worker, path).statusCode())
        assertEquals(404, ledger(f).statusCode())
    }

    @Test
    fun grantsAndYearClosingRequireAnIndependentOperator() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "3"))
        for (permission in
            listOf("leave.accrual.post", "leave.year.close", "leave.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.account,
                permission,
            )
        accountingCode(
            accrue(f, accrualBody(balanceVersion = 1), client = f.worker, csrf = f.workerCsrf),
            403,
            "self_adjustment_denied",
        )
        accountingCode(
            closeYear(f, client = f.worker, csrf = f.workerCsrf),
            403,
            "self_adjustment_denied",
        )
        val own =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2026/adjustments",
                """{"days":"1","reason":"Balance correction","expectedVersion":1}""",
                f.workerCsrf,
                UUID.randomUUID(),
            )
        accountingCode(own, 403, "self_adjustment_denied")
        assertEquals("3", balance(f)["availableDays"].asString())
    }

    @Test
    fun credentialRevocationDuringAnAccrualWaitCannotPublishAnyBalance() {
        val f = accountingFixture()
        val actor = accountingActor(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { accrue(f) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                accountingCode(pending.get(10, TimeUnit.SECONDS), 401, "session_revoked")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, accountingRows(f, "leave_accounts"))
        assertEquals(0, accountingRows(f, "leave_accrual_postings"))
        assertEquals(0, accountingRows(f, "mobile_sync_changes"))
    }
}
