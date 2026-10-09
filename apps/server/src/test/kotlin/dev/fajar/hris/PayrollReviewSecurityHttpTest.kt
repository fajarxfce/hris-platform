package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollReviewSecurityHttpTest : PayrollReviewApiFixture() {
    @Test
    fun revokedMutationPermissionsBlockNewCommandsAndTheirCommittedReplays() {
        for (action in listOf("submit", "decide", "withdraw")) for (replay in listOf(false, true)) {
            val f = calculationFixture()
            val run = calculated(f)
            reviewTemplate(f)
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            if (action != "submit") payrollBody(submitReview(f, run, id))
            val member = if (action == "decide") f.people.reviewer else f.people.preparer
            val command = {
                when (action) {
                    "submit" -> submitReview(f, run, id, key = key)
                    "decide" -> decideReview(f, id, key = key)
                    else -> withdrawReview(f, id, key = key)
                }
            }
            if (replay) payrollBody(command())
            val tables =
                listOf(
                    "payroll_reviews",
                    "payroll_review_changes",
                    "approval_requests",
                    "approval_decisions",
                    "operation_receipts",
                    "audit_entries",
                )
            val before = tables.associateWith { count(f.people.payroll.company, it) }
            val barrier = AccountLockProbe.Barrier(member.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<java.net.http.HttpResponse<String>> { command() }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.people.payroll.company,
                            member.account,
                            if (action == "decide") "payroll.review" else "payroll.calculate",
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, tables.associateWith { count(f.people.payroll.company, it) })
        }
    }

    @Test
    fun decisionRechecksCredentialsAndRecentAuthenticationAfterWaiting() {
        for (credentials in listOf(true, false)) {
            val f = calculationFixture()
            val id = pendingReview(f)
            val barrier = AccountLockProbe.Barrier(f.people.reviewer.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> { decideReview(f, id) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (credentials)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.people.reviewer.account,
                            )
                    else clock.set(clock.instant().plusSeconds(601))
                    barrier.release.countDown()
                    payrollError(
                        pending.get(10, TimeUnit.SECONDS),
                        if (credentials) 401 else 403,
                        if (credentials) "session_revoked" else "recent_authentication_required",
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                    clock.set(Instant.parse("2026-10-01T00:00:00Z"))
                }
            }
            assertEquals(0, count(f.people.payroll.company, "approval_decisions"))
            assertEquals(
                "PENDING",
                reviewView(f, id, member = f.people.preparer)["review"]["status"].asString(),
            )
        }
    }

    @Test
    fun companyReadDoesNotExposeSalariesAndInFlightReadsCannotAcquireANewPermission() {
        val f = calculationFixture()
        val run = calculated(f)
        val id = pendingReview(f, run)
        payrollError(
            get(f.people.payroll.owner.client, reviewsPath(f) + "/$id"),
            403,
            "access_denied",
        )
        payrollError(get(f.people.payroll.admin, reviewsPath(f) + "/$id"), 403, "access_denied")
        for (suffix in listOf(reviewsPath(f) + "/$id", runPath(f, run) + "/reviews")) {
            val reader =
                payrollMember(f.people.payroll.company, setOf("company.read", "payroll.calculate"))
            val barrier = AccountLockProbe.Barrier(reader.account)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> { get(reader.client, suffix) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.calculate'",
                            f.people.payroll.company,
                            reader.account,
                        )
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                            f.people.payroll.company,
                            reader.account,
                        )
                    barrier.release.countDown()
                    payrollError(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            payrollBody(get(reader.client, suffix))
        }
    }

    @Test
    fun delegationRequiresCurrentSourceAuthorityAndExpiresBeforeReceiptReplay() {
        val f = calculationFixture()
        val id = pendingReview(f)
        val delegate =
            payrollMember(
                f.people.payroll.company,
                setOf("company.read", "payroll.review", "approvals.read"),
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'approvals.read')",
                f.people.payroll.company,
                f.people.reviewer.account,
            )
        val delegation = UUID.randomUUID()
        val expires = clock.instant().plusSeconds(60)
        payrollBody(
            command(
                f.people.reviewer.client,
                "/api/v1/companies/${f.people.payroll.company}/approvals/delegations/$delegation",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "PAYROLL",
                        "fromAccount" to f.people.reviewer.account,
                        "toAccount" to delegate.account,
                        "validFrom" to clock.instant().minusSeconds(60).toString(),
                        "validUntil" to expires.toString(),
                        "reason" to "Reviewer coverage",
                    )
                ),
                f.people.reviewer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val key = UUID.randomUUID()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.review'",
                f.people.payroll.company,
                f.people.reviewer.account,
            )
        payrollError(
            decideReview(f, id, member = delegate, key = key),
            403,
            "not_assigned_approver",
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                f.people.payroll.company,
                f.people.reviewer.account,
            )
        payrollBody(decideReview(f, id, member = delegate, key = key))
        payrollBody(decideReview(f, id, member = delegate, key = key))
        try {
            clock.set(expires)
            payrollError(
                decideReview(f, id, member = delegate, key = key),
                403,
                "not_assigned_approver",
            )
        } finally {
            clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        }
        val changes = reviewView(f, id)["changes"]
        assertEquals(delegate.account.toString(), changes[1]["actorId"].asString())
        assertEquals(f.people.reviewer.account.toString(), changes[1]["decidingFor"].asString())
    }
}
