package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.CompanyClientPolicySettings
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.usecases.GetCompanyClientPolicySettings
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ClientPolicySettingsHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var settings: GetCompanyClientPolicySettings
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @Test
    fun configuredAndEffectiveRevisionsRemainDistinctAcrossScheduledActivation() {
        val f = fixture()
        val initial = get(f.browser, f.settings)
        assertEquals(200, initial.statusCode(), initial.body())
        assertTrue(initial.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val empty = json.readTree(initial.body())
        assertTrue(empty["latest"].isNull)
        assertTrue(empty["effective"]["version"].isNull)
        assertEquals(8, empty["effective"]["enabledModules"].size())
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        val starts = clock.instant().plusSeconds(30)
        assertEquals(
            200,
            save(
                    f,
                    0,
                    mapOf("activateAt" to starts.toString(), "disabledModules" to listOf("LEAVE")),
                )
                .statusCode(),
        )
        val scheduled = json.readTree(get(f.browser, f.settings).body())
        assertEquals(1, scheduled["latest"]["version"].asInt())
        assertEquals(0, scheduled["effective"]["version"].asInt())
        assertEquals(starts.toString(), scheduled["effective"]["validUntil"].asString())
        assertFalse(scheduled["effective"]["enabledModules"].toString().contains("PEOPLE"))
        assertTrue(scheduled["effective"]["enabledModules"].toString().contains("LEAVE"))
        clock.set(starts.plusSeconds(1))
        val active = json.readTree(get(f.browser, f.settings).body())
        assertEquals(1, active["effective"]["version"].asInt())
        assertTrue(active["effective"]["enabledModules"].toString().contains("PEOPLE"))
        assertFalse(active["effective"]["enabledModules"].toString().contains("LEAVE"))
        assertEquals(
            0,
            json.readTree(get(f.browser, "${f.settings}/revisions/0").body())["version"].asInt(),
        )
    }

    @Test
    fun settingsRecheckPermissionMembershipCompanyAccountAndCredentialsBeforeAcquiringConfiguration() {
        for (mode in listOf("permission", "membership", "company", "credential", "account")) {
            val f = fixture()
            val barrier = ClientPolicyProbe.Barrier(f.company, true)
            val unread = ClientPolicyProbe.Barrier(f.company, true)
            policyProbe.beforeLock.set(barrier)
            policyProbe.afterLatestRead.set(unread)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending = pool.submit<HttpResponse<String>> { get(f.browser, f.settings) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        when (mode) {
                            "permission" ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='settings.manage'",
                                        f.company,
                                        f.account,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.account,
                                    )
                            "company" ->
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                            "credential" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.account,
                                    )
                            else ->
                                database()
                                    .update(
                                        "update accounts set active=false where id=?",
                                        f.account,
                                    )
                        }
                    } finally {
                        barrier.release.countDown()
                    }
                    val code =
                        when (mode) {
                            "permission" -> "access_denied"
                            "account",
                            "credential" -> "session_revoked"
                            else -> "company_access_denied"
                        }
                    failure(
                        pending.get(10, TimeUnit.SECONDS),
                        if (mode in listOf("account", "credential")) 401 else 403,
                        code,
                    )
                    assertEquals(1L, unread.entered.count, mode)
                }
            } finally {
                policyProbe.clear()
                if (mode == "account")
                    database().update("update accounts set active=true where id=?", f.account)
            }
        }
    }

    @Test
    fun settingsMfaAssuranceCannotExpireWhileWaitingForThePolicyGuard() {
        val f = fixture()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val strict =
            GetCompanyClientPolicySettings(
                policies,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val barrier = ClientPolicyProbe.Barrier(f.company, true)
        val unread = ClientPolicyProbe.Barrier(f.company, true)
        policyProbe.beforeLock.set(barrier)
        policyProbe.afterLatestRead.set(unread)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<CompanyClientPolicySettings>> { strict.execute(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                } finally {
                    barrier.release.countDown()
                }
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
                assertEquals(1L, unread.entered.count)
            }
        } finally {
            policyProbe.clear()
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
        }
    }

    @Test
    fun concurrentChangesCannotMixTheConfiguredHeadWithANewerEffectivePolicy() {
        val f = fixture()
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        val reader = ClientPolicyProbe.Barrier(f.company, true)
        val writer = ClientPolicyProbe.Barrier(f.company, false)
        policyProbe.afterLatestRead.set(reader)
        policyProbe.beforeLock.set(writer)
        Executors.newFixedThreadPool(2).use { pool ->
            val read = pool.submit<HttpResponse<String>> { get(f.browser, f.settings) }
            assertTrue(reader.entered.await(5, TimeUnit.SECONDS))
            val write =
                pool.submit<HttpResponse<String>> {
                    save(f, 0, mapOf("disabledModules" to listOf("LEAVE")))
                }
            try {
                assertTrue(writer.entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "hris:client-policy:${f.company}",
                        ),
                )
            } finally {
                writer.release.countDown()
                reader.release.countDown()
            }
            val response = read.get(10, TimeUnit.SECONDS)
            assertEquals(200, response.statusCode(), response.body())
            val old = json.readTree(response.body())
            assertEquals(0, old["latest"]["version"].asInt())
            assertEquals(0, old["effective"]["version"].asInt())
            assertFalse(old["effective"]["enabledModules"].toString().contains("PEOPLE"))
            assertEquals(200, write.get(10, TimeUnit.SECONDS).statusCode())
        }
        policyProbe.clear()
        val latest = json.readTree(get(f.browser, f.settings).body())
        assertEquals(1, latest["latest"]["version"].asInt())
        assertEquals(1, latest["effective"]["version"].asInt())
        assertFalse(latest["effective"]["enabledModules"].toString().contains("LEAVE"))
    }

    @Test
    fun cancellingASnapshotReadReleasesThePolicyAndAccessGuards() {
        val f = fixture()
        val barrier = ClientPolicyProbe.Barrier(f.company, true)
        val finished = CountDownLatch(1)
        policyProbe.afterLatestRead.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val read =
                pool.submit<Result<CompanyClientPolicySettings>> {
                    try {
                        settings.execute(f.actor)
                    } finally {
                        finished.countDown()
                    }
                }
            assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
            read.cancel(true)
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
        policyProbe.clear()
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                    Boolean::class.java,
                    "hris:client-policy:${f.company}",
                ),
        )
        database().execute("select id from accounts where id='${f.account}' for update nowait")
        assertEquals(200, get(f.browser, f.settings).statusCode())
    }
}
