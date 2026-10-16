package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.usecases.ListRoleTemplates
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RoleTemplateAssuranceTest : RoleTemplateApiFixture() {
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @ParameterizedTest
    @ValueSource(strings = ["review", "catalog"])
    fun acquiredRolesAreNotReleasedWithAnExpiredMfaProof(operation: String) {
        val f = roleFixture()
        val actor = actor(f, assurance = true)
        roleProbe.afterHistory = { company, _ ->
            if (company == f.company) clock.set(clock.instant().plusSeconds(4))
        }
        roleProbe.afterList = { company ->
            if (company == f.company) clock.set(clock.instant().plusSeconds(4))
        }
        val result =
            if (operation == "review") review(f, actor, security)
            else
                ListRoleTemplates(roles, identities, members, transactions, security, clock)
                    .execute(actor, null, 25)
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        roleProbe.afterHistory = null
        roleProbe.afterList = null
        assertTrue(
            review(f, actor.copy(mfaVerifiedAt = clock.instant()), security) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["expire", "revoke"])
    fun reviewRechecksAssuranceAndCurrentGrantsAfterItsAccessWait(mode: String) {
        val f = roleFixture()
        val actor = actor(f, assurance = true)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { review(f, actor, security) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (mode == "expire") clock.set(clock.instant().plusSeconds(4))
                else
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                            f.company,
                            f.operator,
                        )
                barrier.release.countDown()
                assertEquals(
                    if (mode == "expire") "mfa_required" else "access_denied",
                    (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session", "failure", "interrupt"])
    fun lateSaveFailuresRollBackDefinitionRevisionAuditAndReceiptTogether(mode: String) {
        val f = roleFixture()
        val actor = actor(f, assurance = true)
        val key = UUID.randomUUID()
        val late =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).flatMap {
                        when (mode) {
                            "recent" -> {
                                clock.set(clock.instant().plusSeconds(2))
                                Result.Success(Unit)
                            }
                            "session" -> {
                                clock.set(clock.instant().plusSeconds(4))
                                Result.Success(Unit)
                            }
                            "failure" ->
                                Result.Failed(
                                    Failure(FailureKind.CONFLICT, "fixture_journal_failed")
                                )
                            else -> throw InterruptedException("Owned role test cancellation")
                        }
                    }
            }
        val execute = {
            saving(late, security)
                .execute(
                    actor,
                    key,
                    f.id,
                    0,
                    "REVIEWER",
                    "Changed reviewer",
                    setOf("people.read"),
                    true,
                    "Reviewed change",
                )
        }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else
            assertEquals(
                when (mode) {
                    "recent" -> "recent_authentication_required"
                    "session" -> "mfa_required"
                    else -> "fixture_journal_failed"
                },
                (execute() as? Result.Failed)?.failure?.code,
            )
        assertUnchanged(f, key)
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val restored =
            saving(security = security)
                .execute(
                    renewed,
                    key,
                    f.id,
                    0,
                    "REVIEWER",
                    "Changed reviewer",
                    setOf("people.read"),
                    true,
                    "Reviewed change",
                )
        assertTrue(restored is Result.Success, restored.toString())
        val replay =
            saving(security = security)
                .execute(
                    renewed,
                    key,
                    f.id,
                    0,
                    "REVIEWER",
                    "Changed reviewer",
                    setOf("people.read"),
                    true,
                    "Reviewed change",
                )
        assertEquals(
            Result.Success((restored as Result.Success).value.copy(replayed = true)),
            replay,
        )
        assertEquals(2, body(get(f.browser, "${f.path}/${f.id}"))["history"]["items"].size())
    }

    @Test
    fun anAdmittedRoleCommandRetainsItsPermissionSnapshotAcrossWaitsAndReceiptRecovery() {
        val f = roleFixture()
        val actor = actor(f)
        val selected = mutableSetOf("people.read")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val key = UUID.randomUUID()
        roleProbe.beforeLock = { company, shared ->
            if (company == f.company && !shared) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    saving()
                        .execute(
                            actor,
                            key,
                            f.id,
                            0,
                            "REVIEWER",
                            "Changed reviewer",
                            selected,
                            true,
                            "Reviewed change",
                        )
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                selected.clear()
                selected.add("payroll.finalize")
                roleProbe.beforeLock = null
                release.countDown()
                val saved = pending.get(5, TimeUnit.SECONDS)
                assertTrue(saved is Result.Success, saved.toString())
                val review = body(get(f.browser, "${f.path}/${f.id}"))
                assertEquals(
                    listOf("people.read"),
                    review["current"]["permissions"]
                        .iterator()
                        .asSequence()
                        .map { it.asString() }
                        .toList(),
                )
                val recovered =
                    saving()
                        .execute(
                            actor,
                            key,
                            f.id,
                            0,
                            "REVIEWER",
                            "Changed reviewer",
                            setOf("people.read"),
                            true,
                            "Reviewed change",
                        )
                assertEquals(
                    Result.Success((saved as Result.Success).value.copy(replayed = true)),
                    recovered,
                )
            } finally {
                release.countDown()
            }
        }
    }
}
