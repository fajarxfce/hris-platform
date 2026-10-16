package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.*
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MembershipCompletionAssuranceTest : MembershipCommandApiFixture() {
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @ParameterizedTest
    @ValueSource(strings = ["directory", "grant"])
    fun completedMemberAcquisitionStillRequiresCurrentSessionAssurance(mode: String) {
        val f = roleFixture()
        val target = member(f)
        val actor = actor(f, assurance = true)
        val lateMembers =
            object : MembershipRepository by members {
                override fun list(
                    companyId: UUID,
                    after: UUID?,
                    limit: Int,
                ): Result<Page<MemberAccount>> =
                    members.list(companyId, after, limit).also {
                        clock.set(clock.instant().plusSeconds(4))
                    }
            }
        val lateRoles =
            object : RoleTemplateRepository by roles {
                override fun application(
                    companyId: UUID,
                    accountId: UUID,
                    membershipVersion: Long,
                ): Result<MembershipGrant?> =
                    roles.application(companyId, accountId, membershipVersion).also {
                        clock.set(clock.instant().plusSeconds(4))
                    }
            }
        val result =
            if (mode == "directory")
                ListCompanyMembers(lateMembers, identities, transactions, security, clock)
                    .execute(actor, null, 25)
            else
                GetCompanyMemberGrant(members, lateRoles, identities, transactions, security, clock)
                    .execute(actor, target)
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val recovered =
            if (mode == "directory")
                ListCompanyMembers(members, identities, transactions, security, clock)
                    .execute(renewed, null, 25)
            else
                GetCompanyMemberGrant(members, roles, identities, transactions, security, clock)
                    .execute(renewed, target)
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["recent", "session", "failure", "interrupt"])
    fun lateFailureRollsBackMembershipSourcesAuditAndReceipt(mode: String) {
        val f = roleFixture()
        val target = member(f)
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
                            else -> throw InterruptedException("Owned membership test cancellation")
                        }
                    }
            }
        val selected = listOf(RoleTemplateSelection(f.id, 0, true))
        val execute = {
            memberSave(late, security)
                .execute(
                    actor,
                    key,
                    target,
                    0,
                    true,
                    setOf("people.read"),
                    "Reviewed access",
                    selected,
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
        assertMemberUnchanged(f, target, key)
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val restored =
            memberSave(security = security)
                .execute(
                    renewed,
                    key,
                    target,
                    0,
                    true,
                    setOf("people.read"),
                    "Reviewed access",
                    selected,
                )
        assertTrue(restored is Result.Success, restored.toString())
        val replay =
            memberSave(security = security)
                .execute(
                    renewed,
                    key,
                    target,
                    0,
                    true,
                    setOf("people.read"),
                    "Reviewed access",
                    selected,
                )
        assertEquals(
            Result.Success((restored as Result.Success).value.copy(replayed = true)),
            replay,
        )
        assertEquals(1L, grant(f, target)["member"]["version"].asLong())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from membership_role_applications where company_id=? and account_id=?",
                    Int::class.java,
                    f.company,
                    target,
                ),
        )
    }

    @Test
    fun admittedPermissionsAndRoleIntentCannotBeChangedWhileWaiting() {
        val f = roleFixture()
        val target = member(f)
        val actor = actor(f)
        val permissions = mutableSetOf("people.read")
        val selected = mutableListOf(RoleTemplateSelection(f.id, 0, true))
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        roleProbe.beforeLock = { company, shared ->
            if (company == f.company && shared) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    memberSave()
                        .execute(
                            actor,
                            key,
                            target,
                            0,
                            true,
                            permissions,
                            "Reviewed access",
                            selected,
                        )
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                permissions.clear()
                permissions.add("payroll.finalize")
                selected.clear()
                selected.add(RoleTemplateSelection(UUID.randomUUID(), 12))
                roleProbe.beforeLock = null
                release.countDown()
                val saved = pending.get(5, TimeUnit.SECONDS)
                assertTrue(saved is Result.Success, saved.toString())
                val current = grant(f, target)
                assertEquals(
                    setOf("company.read", "people.read"),
                    current["member"]["permissions"]
                        .iterator()
                        .asSequence()
                        .map { it.asString() }
                        .toSet(),
                )
                assertEquals(0L, current["roleTemplates"][0]["version"].asLong())
                val recovered =
                    memberSave()
                        .execute(
                            actor,
                            key,
                            target,
                            0,
                            true,
                            setOf("people.read"),
                            "Reviewed access",
                            listOf(RoleTemplateSelection(f.id, 0, true)),
                        )
                assertEquals(
                    Result.Success((saved as Result.Success).value.copy(replayed = true)),
                    recovered,
                )
            } finally {
                release.countDown()
                roleProbe.beforeLock = null
            }
        }
    }

    @Test
    fun aRecentProofCannotBypassCurrentMfaEnrollmentAfterWaiting() {
        val f = roleFixture()
        val target = member(f)
        val actor = actor(f, assurance = true)
        val key = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    memberSave(security = security)
                        .execute(
                            actor,
                            key,
                            target,
                            0,
                            true,
                            setOf("people.read"),
                            "Reviewed access",
                        )
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set mfa_secret_encrypted=null where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(
                    "mfa_setup_required",
                    (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertMemberUnchanged(f, target, key)
    }
}
