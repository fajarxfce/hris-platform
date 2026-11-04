package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceGatewayRefreshTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun refreshReplaySurvivesNativeRefreshButCannotRenewTheReplayWindow() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val first = f.storage.success(f.bootstrap())
        f.clock.at = f.clock.at.plusSeconds(10)
        val next = f.storage.success(f.refresh(first.refreshToken))
        assertEquals(1L, next.version)
        assertEquals(first.expiresAt, next.expiresAt)
        f.clock.at = f.clock.at.plusSeconds(1)
        f.storage.owner.update(
            "update native_sessions set version=version+1,rotated_at=?::timestamptz where id=?",
            f.clock.at.toString(),
            f.storage.parent,
        )
        val replay = f.storage.success(f.refresh(first.refreshToken))
        assertEquals(next.accessToken, replay.accessToken)
        assertEquals(next.refreshToken, replay.refreshToken)
        assertEquals(next.replayUntil, replay.replayUntil)
        assertEquals(2, f.counts()["audit_entries"])
        f.clock.at = next.replayUntil
        assertInstanceOf(Result.Failed::class.java, f.refresh(first.refreshToken))
        assertEquals(2, f.counts()["audit_entries"])
        val renewed = f.storage.success(f.refresh(next.refreshToken))
        assertEquals(2L, renewed.version)
        assertEquals(first.expiresAt, renewed.expiresAt)
        assertInstanceOf(Result.Failed::class.java, f.refresh(first.refreshToken))
        assertEquals(3, f.counts()["audit_entries"])
    }

    @Test
    fun simultaneousRefreshesShareOneWinnerWithoutDuplicateJournalWrites() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val first = f.storage.success(f.bootstrap())
        f.clock.at = f.clock.at.plusSeconds(10)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs =
                (1..2).map {
                    pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        f.refresh(first.refreshToken)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = jobs.map { f.storage.success(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(results[0].accessToken, results[1].accessToken)
            assertEquals(results[0].refreshToken, results[1].refreshToken)
            assertEquals(1L, f.storage.raw().version)
            assertEquals(2, f.counts()["audit_entries"])
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aLookupThatLosesTheRaceRecoversTheInterveningWinnerInsteadOfOverwritingIt() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val first = f.storage.success(f.bootstrap())
        f.clock.at = f.clock.at.plusSeconds(10)
        val pool = Executors.newSingleThreadExecutor()
        var winner: CollaborationSpaceGatewayTokens? = null
        val gateway =
            object : CollaborationSpaceGatewaySessionRepository by f.storage.repo {
                override fun findRefresh(token: String): Result<CollaborationSpaceGatewayRefresh?> =
                    f.storage.repo.findRefresh(token).map { observed ->
                        winner =
                            f.storage.success(
                                pool
                                    .submit<Result<CollaborationSpaceGatewayTokens>> {
                                        f.refresh(token)
                                    }
                                    .get(10, TimeUnit.SECONDS)
                            )
                        observed
                    }
            }
        try {
            val recovered = f.storage.success(f.refresh(first.refreshToken, gateway = gateway))
            val committed = requireNotNull(winner)
            assertEquals(committed.accessToken, recovered.accessToken)
            assertEquals(committed.refreshToken, recovered.refreshToken)
            assertEquals(1L, f.storage.raw().version)
            assertEquals(2, f.counts()["audit_entries"])
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun journalAndLateAdmissionFailuresRollBackTheRotatedHeadAndEncryptedResponse() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val first = f.storage.success(f.bootstrap())
        val original = f.storage.raw()
        val counts = f.counts()
        val at = f.clock.at.plusSeconds(10)
        for (mode in
            listOf("journal", "group", "provider", "budget", "cancel", "interrupt", "flag")) {
            f.clock.at = at
            var recorded = false
            val journal =
                object : ChangeJournalRepository {
                    override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                        f.journal.record(actor, change).flatMap {
                            recorded = true
                            when (mode) {
                                "journal" ->
                                    Result.Failed(
                                        Failure(FailureKind.UNAVAILABLE, "fixture_write_failed")
                                    )
                                "cancel" -> throw CancellationException("fixture cancellation")
                                "interrupt" -> throw InterruptedException("fixture interrupt")
                                else -> {
                                    if (mode == "budget") f.clock.at = at.plusSeconds(30)
                                    if (mode == "flag") Thread.currentThread().interrupt()
                                    Result.Success(Unit)
                                }
                            }
                        }
                }
            val spaces =
                object : CollaborationSpaceRepository by f.spaces {
                    override fun current(): Result<UUID?> =
                        if (recorded && mode == "group") Result.Success(null)
                        else f.spaces.current()
                }
            val providers =
                object : CollaborationSpaceProviderSessionRepository by f.providers {
                    override fun metadata(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): Result<CollaborationSpaceProviderState?> =
                        if (recorded && mode == "provider") Result.Success(null)
                        else f.providers.metadata(spaceId, accountId, deviceId)
                }
            fun run() =
                f.refresh(
                    first.refreshToken,
                    journal = journal,
                    spaces = spaces,
                    providers = providers,
                )
            try {
                when (mode) {
                    "cancel" -> assertThrows(CancellationException::class.java) { run() }
                    "interrupt",
                    "flag" -> assertThrows(InterruptedException::class.java) { run() }
                    else -> assertInstanceOf(Result.Failed::class.java, run())
                }
            } finally {
                Thread.interrupted()
            }
            assertTrue(recorded, mode)
            assertEquals(original, f.storage.raw(), mode)
            assertEquals(counts, f.counts(), mode)
        }
    }

    @Test
    fun previousAndCurrentTokensBothRequireCurrentProviderClientAndNativeAdmission() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val first = f.storage.success(f.bootstrap())
        f.clock.at = f.clock.at.plusSeconds(10)
        val current = f.storage.success(f.refresh(first.refreshToken))
        val snapshot = f.storage.raw()
        val providers =
            object : CollaborationSpaceProviderSessionRepository by f.providers {
                override fun metadata(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): Result<CollaborationSpaceProviderState?> = Result.Success(null)
            }
        for (token in listOf(first.refreshToken, current.refreshToken)) {
            assertInstanceOf(Result.Failed::class.java, f.refresh(token, providers = providers))
            assertInstanceOf(
                Result.Failed::class.java,
                f.refresh(token, policy = CollaborationSpacePolicy(true, 6, 6)),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.refresh(token, policy = CollaborationSpacePolicy()),
            )
        }
        f.storage.owner.update(
            "update native_sessions set revoked_at=?::timestamptz where id=?",
            f.clock.at.toString(),
            f.storage.parent,
        )
        for (token in listOf(first.refreshToken, current.refreshToken)) assertInstanceOf(
            Result.Failed::class.java,
            f.refresh(token),
        )
        assertEquals(snapshot, f.storage.raw())
        assertEquals(2, f.counts()["audit_entries"])
    }

    @Test
    fun expiredAccessCanRefreshWithinTheCurrentNativeProviderAndHardDeadlines() {
        val f = SpaceGatewayBootstrapFixture(postgres)
        val at = f.clock.at
        val old =
            f.storage.session.copy(
                createdAt = at,
                rotatedAt = at,
                accessExpiresAt = at.plusSeconds(10),
            )
        val issued =
            f.storage.success(
                f.storage.tx { f.storage.repo.create(old, UUID.randomUUID(), at.plusSeconds(5)) }
            )
        f.clock.at = at.plusSeconds(11)
        val refreshed = f.storage.success(f.refresh(issued.refreshToken))
        assertEquals(1L, refreshed.version)
        assertTrue(refreshed.accessExpiresAt > f.clock.at)
        assertEquals(issued.expiresAt, refreshed.expiresAt)
        assertEquals(1, f.counts()["audit_entries"])
    }
}
