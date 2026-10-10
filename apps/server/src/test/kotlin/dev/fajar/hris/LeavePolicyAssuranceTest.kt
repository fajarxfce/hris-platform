package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LeavePolicyAssuranceTest : LeavePolicyApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["catalog", "review", "effective", "save"])
    fun policyReadsWritesAndOriginalReceiptsRequireCurrentAssuranceAfterWaiting(operation: String) {
        val f = leaveFixture()
        val actor = policyOperator(f, assurance = true)
        val invoke = policyInvocation(f, actor, operation, IdentitySecurityPolicy())
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from leave_types where company_id=? and id=?",
                        Long::class.java,
                        f.company,
                        f.type,
                    ),
            )
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val allowed = invoke(renewed)
            assertTrue(allowed is Result.Success, allowed.toString())
            if (operation == "save") {
                assertEquals("mfa_required", (invoke(actor) as? Result.Failed)?.failure?.code)
                val receipt = (allowed as Result.Success).value as MutationReceipt
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                assertEquals(
                    2,
                    database()
                        .queryForObject(
                            "select count(*) from leave_type_revisions where company_id=? and type_id=?",
                            Int::class.java,
                            f.company,
                            f.type,
                        ),
                )
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["catalog", "review", "effective", "save"])
    fun aResolvedPolicyGrantCannotSurviveRevocationDuringAcquisition(operation: String) {
        val f = leaveFixture()
        val actor = policyOperator(f)
        val invoke =
            policyInvocation(f, actor, operation, IdentitySecurityPolicy(enforceMfa = false))
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                            f.company,
                            f.account,
                        )
                    barrier.release.countDown()
                    assertEquals(
                        "access_denied",
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from leave_types where company_id=? and id=?",
                        Long::class.java,
                        f.company,
                        f.type,
                    ),
            )
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }
}
