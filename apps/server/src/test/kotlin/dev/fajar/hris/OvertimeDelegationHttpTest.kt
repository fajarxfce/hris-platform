package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeDelegationHttpTest : OvertimeApiFixture() {
    @Test
    fun aDelegateRequiresLiveSourceAuthorityButACompletedDecisionKeepsItsReceipt() {
        val f = overtimeFixture()
        val id = pending(f)
        val delegate = reviewer(f)
        val delegation = UUID.randomUUID()
        overtimeBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/delegations/$delegation",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "OVERTIME",
                        "fromAccount" to f.actor.accountId,
                        "toAccount" to delegate.id,
                        "validFrom" to clock.instant().minusSeconds(60).toString(),
                        "validUntil" to clock.instant().plusSeconds(60).toString(),
                        "reason" to "Review coverage",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(id.toString(), details(f, id, delegate.client)["request"]["id"].asString())
        val key = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(delegate.id)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> {
                    decide(f, id, key = key, by = delegate)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='overtime.approve'",
                        f.company,
                        f.actor.accountId,
                    )
                barrier.release.countDown()
                assertCode(pending.get(10, TimeUnit.SECONDS), 403, "not_assigned_approver")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, rows(f, "approval_decisions"))
        assertEquals(404, get(delegate.client, "${f.path}/overtime/$id").statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'overtime.approve')",
                f.company,
                f.actor.accountId,
            )
        val completed = overtimeBody(decide(f, id, key = key, by = delegate))
        clock.set(clock.instant().plusSeconds(61))
        assertEquals(completed, overtimeBody(decide(f, id, key = key, by = delegate)))
        assertEquals(1, rows(f, "approval_decisions"))
    }

    @Test
    fun delegationExpiryWhileWaitingPreventsAStaleDecision() {
        val f = overtimeFixture()
        val id = pending(f)
        val delegate = reviewer(f)
        overtimeBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/delegations/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "OVERTIME",
                        "fromAccount" to f.actor.accountId,
                        "toAccount" to delegate.id,
                        "validFrom" to clock.instant().minusSeconds(60).toString(),
                        "validUntil" to clock.instant().plusSeconds(60).toString(),
                        "reason" to "Review coverage",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val barrier = AccountLockProbe.Barrier(delegate.id)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<java.net.http.HttpResponse<String>> { decide(f, id, by = delegate) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(61))
                barrier.release.countDown()
                assertCode(pending.get(10, TimeUnit.SECONDS), 403, "not_assigned_approver")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(0, rows(f, "approval_decisions"))
        overtimeBody(decide(f, id))
    }
}
