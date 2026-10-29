package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.administration.domain.usecases.GetPlatformAccountAccessAudit
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
class PlatformAccountAuditHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var audits: PlatformAuditRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var clock: MutableTestClock
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )
    private val grants = setOf("audit.read", "identity.manage")

    @BeforeEach
    fun resetClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Test
    fun otherActorDetailRequiresBothGlobalPermissionsAndKeepsSavedValues() {
        val author = account(emptySet())
        val target = account(emptySet())
        val event = seed(author, target)
        val combined = client()
        login(combined, "${account()}@example.test")
        val response = get(combined, path(event))
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val data = json.readTree(response.body())
        assertEquals(
            setOf("id", "accountId", "active", "permissions", "reason"),
            data.propertyNames().toSet(),
        )
        assertEquals(event.toString(), data["id"].asString())
        assertEquals(target.toString(), data["accountId"].asString())
        assertEquals("Saved reason", data["reason"].asString())
        assertEquals(
            listOf("audit.read"),
            data["permissions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertFalse(response.body().contains("PRIVATE_UNRELATED"))
        database().update("update accounts set active=false where id=?", target)
        seed(author, target, active = "false", permissions = "", reason = "Replacement reason")
        assertEquals(data, json.readTree(get(combined, path(event)).body()))
        for (permissions in listOf(setOf("audit.read"), setOf("identity.manage"), emptySet())) {
            val limited = client()
            login(limited, "${account(permissions)}@example.test")
            val denied = get(limited, path(event))
            assertEquals(403, denied.statusCode(), denied.body())
            assertFalse(denied.body().contains("Saved reason"))
        }
        assertEquals(401, get(client(), path(event)).statusCode())
    }

    @Test
    fun missingUnsupportedAndMalformedEventsHaveTheSameSafeNotFoundResponse() {
        val author = account()
        val browser = client()
        login(browser, "$author@example.test")
        val ids =
            listOf(
                UUID.randomUUID(),
                seed(author, author, action = "identity.other"),
                seed(author, author, permissions = "BAD PRIVATE"),
            )
        for (id in ids) {
            val result = get(browser, path(id))
            assertEquals(404, result.statusCode(), result.body())
            assertEquals("audit_detail_not_found", json.readTree(result.body())["code"].asString())
            assertFalse(result.body().contains("PRIVATE"))
        }
        val empty = seed(author, author, active = "false", permissions = "", reason = null)
        val response = json.readTree(get(browser, path(empty)).body())
        assertFalse(response["active"].asBoolean())
        assertEquals(0, response["permissions"].size())
        assertTrue(response["reason"].isNull)
    }

    @Test
    fun originalCompanyScopeAndEitherMissingGrantFailBeforeAcquisition() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val review =
            reviewing(
                probe {
                    reads.incrementAndGet()
                    Result.Success(null)
                }
            )
        for (request in
            listOf(
                actor.copy(companyId = UUID.randomUUID()),
                actor.copy(permissions = setOf("audit.read")),
                actor.copy(permissions = setOf("identity.manage")),
            )) assertTrue(review.execute(request, UUID.randomUUID()) is Result.Failed)
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        assertEquals(
            "session_revoked",
            (review.execute(actor, UUID.randomUUID()) as Result.Failed).failure.code,
        )
        assertEquals(0, reads.get())
    }

    @Test
    fun revokingEitherGrantDuringAccountWaitPreventsDetailAcquisition() {
        for (permission in grants) {
            val actor = actor(account())
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val reads = AtomicInteger()
            val guarded =
                object : IdentityRepository by identities {
                    override fun lockAccount(accountId: UUID, shared: Boolean): Result<Unit> {
                        assertTrue(shared)
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        return identities.lockAccount(accountId, shared)
                    }
                }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val task =
                    executor.submit<Result<*>> {
                        reviewing(
                                probe {
                                    reads.incrementAndGet()
                                    Result.Success(null)
                                },
                                guarded,
                            )
                            .execute(actor, UUID.randomUUID())
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from platform_permissions where account_id=? and permission=?",
                        actor.accountId,
                        permission,
                    )
                release.countDown()
                assertEquals(
                    "access_denied",
                    (task.get(5, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
                assertEquals(0, reads.get())
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun mfaExpiryBeforeOrDuringReadDoesNotPublishHistoricalAccess() {
        val actor = actor(account())
        val id = UUID.randomUUID()
        val reads = AtomicInteger()
        val detail =
            PlatformAccountAccessAudit(
                id,
                UUID.randomUUID(),
                true,
                listOf("audit.read"),
                "Saved reason",
            )
        val review =
            reviewing(
                probe {
                    reads.incrementAndGet()
                    clock.set(clock.instant().plusSeconds(4))
                    Result.Success(detail)
                }
            )
        assertEquals(
            "mfa_required",
            (review.execute(actor.copy(mfaVerifiedAt = null), id) as Result.Failed).failure.code,
        )
        assertEquals(0, reads.get())
        assertEquals("mfa_required", (review.execute(actor, id) as Result.Failed).failure.code)
        assertEquals(1, reads.get())
    }

    @Test
    fun failuresAndCancellationNeverBecomeASuccessfulDetail() {
        val actor = actor(account())
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_unavailable"))
        assertEquals(failure, reviewing(probe { failure }).execute(actor, UUID.randomUUID()))
        for (error in listOf(CancellationException(), InterruptedException())) assertThrows(
            error.javaClass
        ) {
            reviewing(probe { throw error }).execute(actor, UUID.randomUUID())
        }
    }

    private fun path(id: UUID) = "/api/v1/administration/audit-events/$id/account-access"

    private fun reviewing(
        source: PlatformAuditRepository = audits,
        identity: IdentityRepository = identities,
    ) = GetPlatformAccountAccessAudit(source, identity, transactions, security, clock)

    private fun probe(read: () -> Result<PlatformAccountAccessAudit?>) =
        object : PlatformAuditRepository by audits {
            override fun accountAccess(id: UUID) = read()
        }

    private fun actor(id: UUID) =
        Actor(
            id,
            null,
            grants,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )

    private fun account(permissions: Set<String> = grants): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned detail reviewer',password_hash,'owned-fixture-only' from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        permissions.forEach {
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,?)",
                    id,
                    it,
                )
        }
        return id
    }

    private fun seed(
        author: UUID,
        target: UUID,
        active: String = "true",
        permissions: String = "audit.read",
        reason: String? = "Saved reason",
        action: String = "identity.account_access_saved",
    ): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into audit_entries(id,actor_id,resource_type,resource_id,action,reason,details,correlation_id) values(?,?,'account',?,?,?,jsonb_build_object('active',?::text,'permissions',?::text,'secret','PRIVATE_UNRELATED'),?)",
                id,
                author,
                target,
                action,
                reason,
                active,
                permissions,
                UUID.randomUUID(),
            )
        return id
    }
}
