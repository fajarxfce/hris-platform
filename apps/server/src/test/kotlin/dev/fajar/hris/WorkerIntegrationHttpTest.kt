package dev.fajar.hris

import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
import dev.fajar.hris.administration.domain.usecases.GetWorkerIntegrationRegistry
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

@Import(TestClockConfiguration::class)
class WorkerIntegrationHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var registry: WorkerIntegrationRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate
    @Autowired private lateinit var clock: MutableTestClock
    private val path = "/api/v1/administration/integrations/workers"
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        database().update("DELETE FROM worker_integration_observations")
    }

    @Test
    fun reviewShowsOnlyBoundedWorkerFlagsAndDatabaseTimesWithExplicitEmptyAndStaleStates() {
        val admin = client()
        login(admin)
        val empty = get(admin, path)
        assertEquals(200, empty.statusCode(), empty.body())
        assertEquals(0, json.readTree(empty.body())["workers"].size())
        val current = UUID.randomUUID()
        database()
            .update(
                """
                INSERT INTO worker_integration_observations(instance_id,first_seen_at,last_seen_at,mail_enabled,push_enabled,storage_enabled,scanner_enabled)
                VALUES(?,now()-interval '3 minutes',now(),false,true,true,true),
                      (?,now()-interval '3 minutes',now()-interval '91 seconds',true,false,false,false)
                """
                    .trimIndent(),
                current,
                UUID.randomUUID(),
            )
        val response = get(admin, path)
        assertEquals(200, response.statusCode(), response.body())
        val root = json.readTree(response.body())
        assertEquals(setOf("observedAt", "workers"), root.propertyNames().toSet())
        assertEquals(2, root["workers"].size())
        val statuses = mutableSetOf<String>()
        root["workers"].forEach { worker ->
            assertEquals(
                setOf("instanceId", "firstSeenAt", "lastSeenAt", "status", "integrations"),
                worker.propertyNames().toSet(),
            )
            statuses += worker["status"].asString()
            assertEquals(4, worker["integrations"].size())
            assertEquals(
                listOf("MAIL", "PUSH", "STORAGE", "DOCUMENT_SCANNER"),
                worker["integrations"]
                    .iterator()
                    .asSequence()
                    .map { it["kind"].asString() }
                    .toList(),
            )
            worker["integrations"].forEach {
                assertEquals(setOf("kind", "enabled"), it.propertyNames().toSet())
            }
            if (worker["instanceId"].asString() == current.toString()) {
                assertEquals("RECENT", worker["status"].asString())
                assertTrue(worker["integrations"][1]["enabled"].asBoolean())
            }
        }
        assertEquals(setOf("RECENT", "STALE"), statuses)
        assertEquals(401, get(client(), path).statusCode())
        val companyOnly = client()
        login(companyOnly, "${account(administrator = false)}@example.test")
        assertEquals(403, get(companyOnly, path).statusCode())
    }

    @Test
    fun apiDatabaseRoleCannotPublishOrReplaceWorkerReports() {
        assertThrows(DataAccessException::class.java) {
            runtime.update(
                "INSERT INTO worker_integration_observations(instance_id,mail_enabled,push_enabled,storage_enabled,scanner_enabled) VALUES(?,false,false,false,false)",
                UUID.randomUUID(),
            )
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "SELECT count(*) FROM worker_integration_observations",
                    Int::class.java,
                ),
        )
        val id = UUID.randomUUID()
        database()
            .update(
                "INSERT INTO worker_integration_observations(instance_id,mail_enabled,push_enabled,storage_enabled,scanner_enabled) VALUES(?,false,false,false,false)",
                id,
            )
        val attempt =
            transactions.run(actor(account())) {
                Result.Success(
                    runtime.update(
                        "UPDATE worker_integration_observations SET last_seen_at=clock_timestamp() WHERE instance_id=?",
                        id,
                    )
                )
            }
        assertEquals(0, (attempt as Result.Success).value)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "SELECT count(*) FROM worker_integration_observations",
                    Int::class.java,
                ),
        )
    }

    @Test
    fun missingOriginalGrantCompanyScopeAndCredentialRevocationDenyBeforeAcquisition() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val observed =
            object : WorkerIntegrationRepository by registry {
                override fun read() = registry.read().also { reads.incrementAndGet() }
            }
        val review = reviewing(observed)
        for (request in
            listOf(
                actor.copy(companyId = UUID.randomUUID()),
                actor.copy(permissions = emptySet()),
            )) {
            assertEquals(
                "platform_administrator_required",
                (review.execute(request) as Result.Failed).failure.code,
            )
        }
        assertEquals(0, reads.get())
        assertTrue(review.execute(actor) is Result.Success)
        database()
            .update(
                "UPDATE accounts SET security_version=security_version+1 WHERE id=?",
                actor.accountId,
            )
        assertEquals("session_revoked", (review.execute(actor) as Result.Failed).failure.code)
        assertEquals(1, reads.get())
    }

    @Test
    fun revokedPermissionWhileWaitingPreventsWorkerObservationAcquisition() {
        val actor = actor(account())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val guarded =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return identities.lockAccount(accountId, shared)
                }
            }
        val observed =
            object : WorkerIntegrationRepository by registry {
                override fun read() = registry.read().also { reads.incrementAndGet() }
            }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val pending = pool.submit<Result<*>> { reviewing(observed, guarded).execute(actor) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            database()
                .update("DELETE FROM platform_permissions WHERE account_id=?", actor.accountId)
            release.countDown()
            assertEquals(
                "platform_administrator_required",
                (pending.get(5, TimeUnit.SECONDS) as Result.Failed).failure.code,
            )
            assertEquals(0, reads.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun mfaExpiryDuringAcquisitionDoesNotReleaseAWorkerSnapshotAndFreshProofRecovers() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val observed =
            object : WorkerIntegrationRepository by registry {
                override fun read() =
                    registry.read().also {
                        reads.incrementAndGet()
                        clock.set(clock.instant().plusSeconds(4))
                    }
            }
        val review = reviewing(observed)
        assertEquals(
            "mfa_required",
            (review.execute(actor.copy(mfaVerifiedAt = null)) as Result.Failed).failure.code,
        )
        assertEquals(0, reads.get())
        assertEquals("mfa_required", (review.execute(actor) as Result.Failed).failure.code)
        assertEquals(1, reads.get())
        assertTrue(
            reviewing().execute(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @Test
    fun failuresAndCancellationNeverBecomeAnEmptyHealthyRegistry() {
        val actor = actor(account())
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "owned_unavailable"))
        val unavailable =
            object : WorkerIntegrationRepository by registry {
                override fun read() = failure
            }
        assertEquals(failure, reviewing(unavailable).execute(actor))
        for (error in listOf(CancellationException(), InterruptedException())) {
            val cancelled =
                object : WorkerIntegrationRepository by registry {
                    override fun read(): Nothing = throw error
                }
            assertThrows(error.javaClass) { reviewing(cancelled).execute(actor) }
        }
        assertTrue(reviewing().execute(actor) is Result.Success)
    }

    private fun reviewing(
        source: WorkerIntegrationRepository = registry,
        identity: IdentityRepository = identities,
    ) = GetWorkerIntegrationRegistry(source, identity, transactions, security, clock)

    private fun account(administrator: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "INSERT INTO accounts(id,email,display_name,password_hash,mfa_secret_encrypted) SELECT ?,?,'Owned worker reviewer',password_hash,'owned-fixture-only' FROM accounts WHERE email='admin@example.test'",
                id,
                "$id@example.test",
            )
        if (administrator)
            database()
                .update(
                    "INSERT INTO platform_permissions(account_id,permission) VALUES(?,'identity.manage')",
                    id,
                )
        return id
    }

    private fun actor(id: UUID) =
        Actor(
            id,
            null,
            setOf("identity.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )
}
