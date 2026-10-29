package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.administration.domain.usecases.SearchPlatformAuditEvents
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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
class PlatformAuditHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var audits: PlatformAuditRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var clock: MutableTestClock
    private val path = "/api/v1/administration/audit-events"
    private val security =
        IdentitySecurityPolicy(
            maximumMfaAge = Duration.ofSeconds(4),
            recentAuthenticationAge = Duration.ofSeconds(2),
        )

    @BeforeEach
    fun resetClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Test
    fun globalReadersSeeOnlyMetadataAcrossActorsWithStableTiedPagination() {
        val reader = account()
        val other = account(false)
        val browser = client()
        login(browser, "$reader@example.test")
        val action = "fixture.${UUID.randomUUID()}"
        val tied = List(3) { seed(other, action) }.sortedByDescending { it.toString() }
        val company = UUID.randomUUID()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                company.toString().take(32),
                "Private company",
            )
        seed(other, action, company)
        val response = get(browser, "$path?action=$action&limit=2")
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertFalse(response.body().contains("PRIVATE_"))
        val first = json.readTree(response.body())
        assertEquals(
            setOf("from", "until", "evaluatedAt", "items", "nextCursor"),
            first.propertyNames().toSet(),
        )
        assertEquals(
            tied.take(2).map { it.toString() },
            first["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertEquals(
            setOf(
                "id",
                "actorId",
                "resourceType",
                "resourceId",
                "action",
                "correlationId",
                "recordedAt",
            ),
            first["items"][0].propertyNames().toSet(),
        )
        assertEquals(tied[1].toString(), first["nextCursor"].asString())
        val next =
            get(
                browser,
                "$path?action=$action&limit=2&cursor=${tied[1]}&from=${first["from"].asString()}&until=${first["until"].asString()}",
            )
        assertEquals(200, next.statusCode(), next.body())
        val last = json.readTree(next.body())
        assertEquals(
            listOf(tied.last().toString()),
            last["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertTrue(last["nextCursor"].isNull)
        val denied = client()
        login(denied, "$other@example.test")
        assertEquals(403, get(denied, path).statusCode())
        assertEquals(401, get(client(), path).statusCode())
    }

    @Test
    fun filtersAndCursorsCannotBroadenTheSelectedHistory() {
        val id = account()
        val browser = client()
        login(browser, "$id@example.test")
        val resource = UUID.randomUUID()
        val wanted = seed(id, "fixture.selected", resource = resource)
        seed(id, "fixture.other")
        val selected =
            get(
                browser,
                "$path?actorId=$id&resourceId=$resource&resourceType=account&action=fixture.selected",
            )
        assertEquals(200, selected.statusCode(), selected.body())
        assertEquals(
            listOf(wanted.toString()),
            json
                .readTree(selected.body())["items"]
                .iterator()
                .asSequence()
                .map { it["id"].asString() }
                .toList(),
        )
        val old = seed(id, "fixture.selected", at = clock.instant().minus(Duration.ofDays(31)))
        for (query in
            listOf(
                "cursor=${UUID.randomUUID()}",
                "cursor=$old",
                "cursor=$wanted&action=fixture.other",
                "cursor=$wanted&resourceId=${UUID.randomUUID()}",
            )) {
            val rejected = get(browser, "$path?$query")
            assertEquals(422, rejected.statusCode(), rejected.body())
            assertEquals("invalid_audit_cursor", json.readTree(rejected.body())["code"].asString())
        }
    }

    @Test
    fun originalScopeGrantAndQueryBoundsAreCheckedBeforeAcquisition() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val review =
            reviewing(
                probe {
                    reads.incrementAndGet()
                    Result.Success(Page(emptyList(), null))
                }
            )
        for (request in
            listOf(
                actor.copy(companyId = UUID.randomUUID()),
                actor.copy(permissions = emptySet()),
            )) assertTrue(review.execute(request, AuditSearch()) is Result.Failed)
        for (query in
            listOf(
                AuditSearch(limit = 201),
                AuditSearch(from = clock.instant().minus(Duration.ofDays(91))),
                AuditSearch(action = "bad filter"),
            )) assertTrue(review.execute(actor, query) is Result.Failed)
        assertEquals(0, reads.get())
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        assertEquals(
            "session_revoked",
            (review.execute(actor, AuditSearch()) as Result.Failed).failure.code,
        )
        assertEquals(0, reads.get())
    }

    @Test
    fun revocationDuringAccountWaitPreventsAnyRead() {
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
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending =
                executor.submit<Result<*>> {
                    reviewing(
                            probe {
                                reads.incrementAndGet()
                                Result.Success(Page(emptyList(), null))
                            },
                            guarded,
                        )
                        .execute(actor, AuditSearch())
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            database()
                .update("delete from platform_permissions where account_id=?", actor.accountId)
            release.countDown()
            assertEquals(
                "access_denied",
                (pending.get(5, TimeUnit.SECONDS) as Result.Failed).failure.code,
            )
            assertEquals(0, reads.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun mfaExpiryDuringPageAcquisitionDoesNotPublishMetadata() {
        val actor = actor(account())
        val reads = AtomicInteger()
        val review =
            reviewing(
                probe {
                    reads.incrementAndGet()
                    clock.set(clock.instant().plusSeconds(4))
                    Result.Success(Page(emptyList(), null))
                }
            )
        assertEquals(
            "mfa_required",
            (review.execute(actor.copy(mfaVerifiedAt = null), AuditSearch()) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(0, reads.get())
        assertEquals(
            "mfa_required",
            (review.execute(actor, AuditSearch()) as Result.Failed).failure.code,
        )
        assertEquals(1, reads.get())
        assertTrue(
            reviewing().execute(actor.copy(mfaVerifiedAt = clock.instant()), AuditSearch())
                is Result.Success
        )
    }

    @Test
    fun failuresAndCancellationDiscardThePage() {
        val actor = actor(account())
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_unavailable"))
        assertEquals(failure, reviewing(probe { failure }).execute(actor, AuditSearch()))
        for (error in listOf(CancellationException(), InterruptedException())) assertThrows(
            error.javaClass
        ) {
            reviewing(probe { throw error }).execute(actor, AuditSearch())
        }
    }

    private fun reviewing(
        source: PlatformAuditRepository = audits,
        identity: IdentityRepository = identities,
    ) = SearchPlatformAuditEvents(source, identity, transactions, security, clock)

    private fun probe(read: () -> Result<Page<PlatformAuditEvent>>) =
        object : PlatformAuditRepository {
            override fun find(id: UUID): Result<PlatformAuditEvent?> =
                error("Unexpected cursor read")

            override fun search(query: AuditQuery) = read()
        }

    private fun account(granted: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Owned audit reviewer',password_hash,'owned-fixture-only' from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        if (granted)
            database()
                .update(
                    "insert into platform_permissions(account_id,permission) values(?,'audit.read')",
                    id,
                )
        return id
    }

    private fun actor(id: UUID) =
        Actor(
            id,
            null,
            setOf("audit.read"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )

    private fun seed(
        actor: UUID,
        action: String,
        company: UUID? = null,
        resource: UUID = UUID.randomUUID(),
        at: Instant = clock.instant().minusSeconds(1),
    ): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,reason,details,correlation_id,created_at) values(?,?,?,'account',?,?,'PRIVATE_REASON','{\"private\":\"PRIVATE_PAYLOAD\"}'::jsonb,?,?)",
                id,
                company,
                actor,
                resource,
                action,
                UUID.randomUUID(),
                at.atOffset(ZoneOffset.UTC),
            )
        return id
    }
}
