package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.AuditRepository
import dev.fajar.hris.administration.domain.usecases.SearchAuditEvents
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AuditAccessHttpTest : AuditApiFixture() {
    @Autowired private lateinit var search: SearchAuditEvents
    @Autowired private lateinit var audits: AuditRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun waiting(
        f: Fixture,
        useCase: SearchAuditEvents = search,
        actor: Actor = f.actor,
        change: () -> Unit,
    ): Result<AuditPage> {
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<AuditPage>> { useCase.execute(actor, AuditSearch()) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun pendingAuditReadsRejectRevokedPermissionScopeAccountAndCredentialBeforeQuerying() {
        for (mode in listOf("permission", "company", "membership", "account", "credential")) {
            val f = fixture()
            seed(f)
            val result =
                waiting(f) {
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='audit.read'",
                                    f.company,
                                    f.account,
                                )
                        "company" ->
                            database()
                                .update("update companies set active=false where id=?", f.company)
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    f.account,
                                )
                        "account" ->
                            database()
                                .update("update accounts set active=false where id=?", f.account)
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.account,
                                )
                    }
                }
            val code =
                when (mode) {
                    "permission" -> "access_denied"
                    "account",
                    "credential" -> "session_revoked"
                    else -> "company_access_denied"
                }
            assertEquals(code, (result as Result.Failed).failure.code, mode)
            assertEquals(0, auditProbe.reads.get())
        }
    }

    @Test
    fun auditMfaAssuranceIsRecheckedAfterItsAccessGuardsWerePending() {
        val f = fixture()
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val strict =
            SearchAuditEvents(audits, companies, members, identities, transactions, security, clock)
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val result = waiting(f, strict, actor) { clock.set(clock.instant().plusSeconds(2)) }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertEquals(0, auditProbe.reads.get())
    }

    @Test
    fun anAcquiredPageKeepsItsSnapshotWhileNewEventsCommit() {
        val f = fixture()
        val original = seed(f, clock.instant().minusSeconds(10))
        val barrier = AuditProbe.Barrier(f.company)
        auditProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<AuditPage>> {
                    search.execute(f.actor, AuditSearch(action = "fixture.created"))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                auditProbe.afterRead.set(null)
                val newer = seed(f)
                val current = page(f, mapOf("action" to "fixture.created"))
                assertEquals(
                    listOf(newer.toString(), original.toString()),
                    current["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
                )
                barrier.release.countDown()
                val held = pending.get(10, TimeUnit.SECONDS) as Result.Success
                assertEquals(listOf(original), held.value.items.map { it.id })
            } finally {
                barrier.release.countDown()
                auditProbe.afterRead.set(null)
            }
        }
    }

    @Test
    fun cancellationReleasesAccessGuardsAndCannotPublishALateAuditPage() {
        val f = fixture()
        seed(f)
        val barrier = AuditProbe.Barrier(f.company)
        val finished = CountDownLatch(1)
        auditProbe.afterRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit {
                    try {
                        search.execute(f.actor, AuditSearch())
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from (select id from accounts where id=? for update nowait) unlocked",
                            Int::class.java,
                            f.account,
                        ),
                )
            } finally {
                barrier.release.countDown()
                auditProbe.afterRead.set(null)
            }
        }
        assertEquals(1, page(f, mapOf("action" to "fixture.created"))["items"].size())
        val other = fixture()
        assertTrue(page(other)["items"].isEmpty)
    }
}
