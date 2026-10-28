package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.IntegrationConfiguration
import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.administration.domain.repositories.IntegrationConfigurationRepository
import dev.fajar.hris.administration.domain.usecases.GetIntegrationConfiguration
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

@Import(TestClockConfiguration::class)
class IntegrationConfigurationHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var clock: MutableTestClock
    private val path = "/api/v1/administration/integrations/configuration"
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )
    private val flags = IntegrationKind.entries.map { IntegrationConfiguration(it, false) }

    @BeforeEach
    fun resetClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Test
    fun publicReviewDisclosesOnlyTheApiRoleTimeAndSixEnablementFlags() {
        val admin = client()
        login(admin)
        val response = get(admin, path)
        assertEquals(200, response.statusCode(), response.body())
        val root = json.readTree(response.body())
        assertEquals(setOf("runtime", "observedAt", "integrations"), root.propertyNames().toSet())
        assertEquals("API", root["runtime"].asString())
        assertEquals(clock.instant(), Instant.parse(root["observedAt"].asString()))
        assertEquals(6, root["integrations"].size())
        assertEquals(
            IntegrationKind.entries.map { it.name },
            root["integrations"].iterator().asSequence().map { it["kind"].asString() }.toList(),
        )
        root["integrations"].forEach {
            assertEquals(setOf("kind", "enabled"), it.propertyNames().toSet())
            assertTrue(it["enabled"].isBoolean)
        }
        assertEquals(401, get(client(), path).statusCode())
        val reader = client()
        login(reader, "${account(administrator = false)}@example.test")
        assertEquals(403, get(reader, path).statusCode())
    }

    @Test
    fun companyScopeOrMissingOriginalGrantCannotReadConfiguration() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val review =
            reviewing(
                IntegrationConfigurationRepository {
                    reads.incrementAndGet()
                    Result.Success(flags)
                }
            )
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
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        assertEquals("session_revoked", (review.execute(actor) as Result.Failed).failure.code)
        assertEquals(1, reads.get())
    }

    @Test
    fun permissionRevocationWhileWaitingPreventsConfigurationAcquisition() {
        val actor = actor(account())
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val reads = AtomicInteger()
        val guarded =
            object : IdentityRepository by identities {
                override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                    entered.countDown()
                    check(released.await(5, TimeUnit.SECONDS))
                    return identities.lockAccount(accountId, shared)
                }
            }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending =
                executor.submit<Result<*>> {
                    reviewing(
                            IntegrationConfigurationRepository {
                                reads.incrementAndGet()
                                Result.Success(flags)
                            },
                            guarded,
                        )
                        .execute(actor)
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            database()
                .update("delete from platform_permissions where account_id=?", actor.accountId)
            released.countDown()
            assertEquals(
                "platform_administrator_required",
                (pending.get(5, TimeUnit.SECONDS) as Result.Failed).failure.code,
            )
            assertEquals(0, reads.get())
        } finally {
            released.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun mfaIsRequiredBeforeAndAfterAcquisitionAndFreshProofAllowsRecovery() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val review =
            reviewing(
                IntegrationConfigurationRepository {
                    reads.incrementAndGet()
                    clock.set(clock.instant().plusSeconds(4))
                    Result.Success(flags)
                }
            )
        assertEquals(
            "mfa_required",
            (review.execute(actor.copy(mfaVerifiedAt = null)) as Result.Failed).failure.code,
        )
        assertEquals(0, reads.get())
        assertEquals("mfa_required", (review.execute(actor) as Result.Failed).failure.code)
        assertEquals(1, reads.get())
        val recovered = reviewing().execute(actor.copy(mfaVerifiedAt = clock.instant()))
        assertEquals(flags, (recovered as Result.Success).value.integrations)
    }

    @Test
    fun acquisitionFailuresAndCancellationNeverReturnOldConfiguration() {
        val actor = actor(account())
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "owned_fixture_unavailable"))
        assertEquals(
            failure,
            reviewing(IntegrationConfigurationRepository { failure }).execute(actor),
        )
        for (error in listOf(CancellationException(), InterruptedException())) {
            assertThrows(error.javaClass) {
                reviewing(IntegrationConfigurationRepository { throw error }).execute(actor)
            }
        }
        try {
            assertThrows(InterruptedException::class.java) {
                reviewing(
                        IntegrationConfigurationRepository {
                            Thread.currentThread().interrupt()
                            Result.Success(flags)
                        }
                    )
                    .execute(actor)
            }
        } finally {
            Thread.interrupted()
        }
        assertTrue(reviewing().execute(actor) is Result.Success)
    }

    private fun reviewing(
        source: IntegrationConfigurationRepository = IntegrationConfigurationRepository {
            Result.Success(flags)
        },
        identity: IdentityRepository = identities,
    ) = GetIntegrationConfiguration(source, identity, transactions, security, clock)

    private fun account(administrator: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned integration reviewer',password_hash,'owned-fixture-only' from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        if (administrator)
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
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
