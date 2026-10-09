package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.administration.domain.usecases.SaveCompanyClientPolicy
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class ClientPolicyAccessHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var savePolicy: SaveCompanyClientPolicy
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate

    @Test
    fun pendingCommandsAndOriginalReceiptsLoseAccessAfterPermissionRevocation() {
        for (replay in listOf(false, true)) {
            val f = fixture()
            val key = UUID.randomUUID()
            if (replay) assertEquals(200, save(f, key = key).statusCode())
            val barrier = ClientPolicyProbe.Barrier(f.company, false)
            policyProbe.beforeLock.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<java.net.http.HttpResponse<String>> { save(f, key = key) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='settings.manage'",
                            f.company,
                            f.account,
                        )
                } finally {
                    barrier.release.countDown()
                }
                failure(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            }
            policyProbe.clear()
            assertEquals(if (replay) 1 else 0, versions(f))
            assertEquals(
                if (replay) 1 else 0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
            assertEquals(200, get(f.browser, "${f.root}/client-policy").statusCode())
            failure(get(f.browser, f.settings), 403, "access_denied")
        }
    }

    @Test
    fun policyReadsAndBusinessAdmissionRecheckCompanyAndCredentialStateAfterPendingGuards() {
        for (credential in listOf(false, true)) {
            val f = fixture()
            val barrier = ClientPolicyProbe.Barrier(f.company, true)
            policyProbe.beforeLock.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val path =
                    if (credential) "${f.root}/employees?asOf=2026-10-01"
                    else "${f.root}/client-policy"
                val pending =
                    pool.submit<java.net.http.HttpResponse<String>> { get(f.browser, path) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (credential)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    else
                        database().update("update companies set active=false where id=?", f.company)
                } finally {
                    barrier.release.countDown()
                }
                failure(
                    pending.get(10, TimeUnit.SECONDS),
                    if (credential) 401 else 403,
                    if (credential) "session_revoked" else "company_access_denied",
                )
            }
            policyProbe.clear()
        }
    }

    @Test
    fun recentAuthenticationIsRecheckedAfterWaitingAndCancellationRollsBackTheReceipt() {
        val f = fixture()
        val key = UUID.randomUUID()
        val barrier = ClientPolicyProbe.Barrier(f.company, false)
        policyProbe.beforeLock.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<java.net.http.HttpResponse<String>> { save(f, key = key) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(601))
            } finally {
                barrier.release.countDown()
            }
            failure(pending.get(10, TimeUnit.SECONDS), 403, "recent_authentication_required")
        }
        policyProbe.clear()
        assertEquals(0, versions(f))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )

        val other = fixture()
        val cancelledKey = UUID.randomUUID()
        val wait = ClientPolicyProbe.Barrier(other.company, false)
        val complete = CountDownLatch(1)
        policyProbe.beforeLock.set(wait)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    try {
                        savePolicy.execute(
                            other.actor,
                            cancelledKey,
                            SaveCompanyClientPolicyCommand(
                                null,
                                null,
                                ClientPolicy(),
                                "Configuration",
                            ),
                        )
                    } finally {
                        complete.countDown()
                    }
                }
            assertTrue(wait.entered.await(5, TimeUnit.SECONDS))
            pending.cancel(true)
            assertTrue(complete.await(5, TimeUnit.SECONDS))
        }
        policyProbe.clear()
        assertEquals(0, versions(other))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    cancelledKey,
                ),
        )
        assertEquals(200, save(other, key = cancelledKey).statusCode())
    }

    @Test
    fun effectiveReadsHoldASharedSnapshotGuardUntilNextActivationHasBeenRead() {
        val f = fixture()
        assertEquals(200, save(f).statusCode())
        val barrier = ClientPolicyProbe.Barrier(f.company, true)
        policyProbe.afterEffectiveRead.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val read =
                pool.submit<java.net.http.HttpResponse<String>> {
                    get(f.browser, "${f.root}/client-policy")
                }
            assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
            try {
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "hris:client-policy:${f.company}",
                        ),
                )
                assertEquals(
                    true,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock_shared(hashtextextended(?,0))",
                            Boolean::class.java,
                            "hris:client-policy:${f.company}",
                        ),
                )
            } finally {
                barrier.release.countDown()
            }
            assertEquals(0, json.readTree(read.get(10, TimeUnit.SECONDS).body())["version"].asInt())
        }
        policyProbe.clear()
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("PEOPLE"))).statusCode())
        assertEquals(
            1,
            json.readTree(get(f.browser, "${f.root}/client-policy").body())["version"].asInt(),
        )
    }

    @Test
    fun databaseRetainsCompanyIsolationImmutableRevisionsAndCompleteHeads() {
        val f = fixture()
        assertEquals(200, save(f).statusCode())
        val foreign = company(f.browser, f.csrf)
        val hidden =
            transactions.run(f.actor.copy(companyId = foreign)) { policies.find(f.company) }
        assertEquals(Result.Success(null), hidden)
        val immutable =
            transactions.run(f.actor) {
                runtime.update(
                    "update company_client_policy_revisions set minimum_android_build=999 where company_id=?",
                    f.company,
                )
                Result.Success(Unit)
            }
        assertTrue(immutable is Result.Failed)
        val incomplete =
            transactions.run(f.actor) {
                runtime.update(
                    "update company_client_policy_heads set version=version+1 where company_id=?",
                    f.company,
                )
                Result.Success(Unit)
            }
        assertTrue(incomplete is Result.Failed)
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from company_client_policy_heads where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        assertEquals(1, versions(f))
        val denied =
            transactions.run(f.actor.copy(companyId = foreign)) {
                runtime.update(
                    "insert into company_client_policy_heads(company_id) values(?)",
                    f.company,
                )
                Result.Success(Unit)
            }
        assertTrue(denied is Result.Failed)
    }
}
