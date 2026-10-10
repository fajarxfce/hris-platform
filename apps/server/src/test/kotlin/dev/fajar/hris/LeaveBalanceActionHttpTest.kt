package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveLedger
import dev.fajar.hris.leave.domain.usecases.GetLeaveLedger
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveBalanceActionHttpTest : LeaveAccountingApiFixture() {
    @Autowired private lateinit var read: GetLeaveLedger

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun actionAvailabilityIntersectsOriginalAndCurrentGrantsAfterWaiting(grant: Boolean) {
        val f = leaveFixture()
        val permissions = if (grant) setOf("leave.read") else setOf("leave.read", "leave.manage")
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                    f.company,
                    f.managerAccount,
                    it,
                )
        }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.managerAccount,
            )
        val actor =
            Actor(
                f.managerAccount,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant(),
            )
        val barrier = AccountLockProbe.Barrier(f.managerAccount)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<LeaveLedger>> {
                        read.execute(actor, f.employee, f.type, 2026, null, 20)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (grant)
                        database()
                            .update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                                f.company,
                                f.managerAccount,
                            )
                    else
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                                f.company,
                                f.managerAccount,
                            )
                    barrier.release.countDown()
                    val result = pending.get(5, TimeUnit.SECONDS)
                    assertTrue(result is Result.Success, result.toString())
                    assertTrue((result as Result.Success).value.availableActions.isEmpty())
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            if (grant) {
                val fresh =
                    read.execute(
                        actor.copy(permissions = permissions + "leave.manage"),
                        f.employee,
                        f.type,
                        2026,
                        null,
                        20,
                    )
                assertEquals(
                    setOf(dev.fajar.hris.leave.domain.entities.LeaveBalanceAction.ADJUST),
                    (fresh as Result.Success).value.availableActions,
                )
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @Test
    fun independentAdministratorsCanReviewAnAdjustmentBeforeTheFirstMovement() {
        val f = leaveFixture()
        val unfunded = accountingBody(ledger(f, f.admin))
        assertTrue(unfunded["balance"]["accountId"].isNull)
        assertEquals(
            listOf("ADJUST"),
            unfunded["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(0, accountingBody(ledger(f))["availableActions"].size())
        assertEquals(0, accountingBody(ledger(f, f.supervisor))["availableActions"].size())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                f.account,
            )
        assertEquals(0, accountingBody(ledger(f))["availableActions"].size())
        accountingBody(policy(f, 0, active = false))
        assertEquals("ADJUST", accountingBody(ledger(f, f.admin))["availableActions"][0].asString())
        accountingBody(adjust(f, "0.5"))
        assertEquals("ADJUST", accountingBody(ledger(f, f.admin))["availableActions"][0].asString())
    }

    @Test
    fun aTypeOutsideTheBalanceYearAndAClosedYearHaveNoAdjustmentAction() {
        val f = accountingFixture(at = Instant.parse("2027-01-02T03:00:00Z"))
        val future = f.copy(type = UUID.randomUUID())
        accountingBody(policy(future, from = "2027-01-01", code = "FUTURE"))
        assertEquals(0, accountingBody(ledger(future, f.admin))["availableActions"].size())
        accountingBody(closeYear(f, body = closingBody(sourceVersion = 0)))
        val closed = accountingBody(ledger(f, f.admin))
        assertTrue(closed["balance"]["closed"].asBoolean())
        assertEquals(0, closed["availableActions"].size())
    }
}
