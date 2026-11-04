package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.crypto.collaborationSpaceGatewayPurpose
import dev.fajar.hris.collaboration.data.mappers.toSpaceGatewaySession
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceGatewayMappingTest {
    @Test
    fun opaqueGroupPrefixesAndOwnedMetadataRemainDistinctFromLegacyAndTokenAge() {
        val f = SpaceGatewayMappingFixture()
        val tokens = f.create()
        assertTrue(validCollaborationSpaceGatewayAccess(tokens.accessToken))
        assertTrue(validCollaborationSpaceGatewayRefresh(tokens.refreshToken))
        assertFalse(validCollaborationGatewayAccess(tokens.accessToken))
        assertFalse(validCollaborationGatewayRefresh(tokens.refreshToken))
        assertFalse(validCollaborationSpaceGatewayAccess("hris_mx_a_" + "a".repeat(43)))
        assertFalse(tokens.toString().contains(tokens.accessToken))
        assertEquals(f.session, f.success(f.read()))
        assertEquals(f.session, f.success(f.repo.findAccess(tokens.accessToken)))
        assertFalse(f.success(f.repo.findRefresh(tokens.refreshToken))!!.previous)
        val encrypted = f.source.row!!.replayEncrypted
        assertFalse(encrypted.contains(tokens.accessToken))
        val replay = f.success(f.repo.replayBootstrap(f.session, f.operation))!!
        assertEquals(tokens.accessToken, replay.accessToken)
        assertEquals(tokens.refreshToken, replay.refreshToken)
        assertNull(f.success(f.repo.replayBootstrap(f.session, UUID.randomUUID())))
        assertEquals(1, f.source.writes)
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.find(UUID.randomUUID(), f.session.accountId, f.session.deviceId),
        )
        f.source.forgedLookup = true
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.findAccess("hris_mxg_a_" + "z".repeat(43)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.findRefresh("hris_mxg_r_" + "z".repeat(43)),
        )
    }

    @Test
    fun everyAuthenticatedCoordinateAndLegacyNamespaceRejectCiphertextSubstitution() {
        val f = SpaceGatewayMappingFixture()
        f.create()
        val original = copySpaceGateway(f.source.row!!)
        val mutations =
            listOf<(CollaborationSpaceGatewaySessionsRecord) -> Unit>(
                { it.spaceId = UUID.randomUUID() },
                { it.accountId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.id = UUID.randomUUID() },
                { it.nativeSessionId = UUID.randomUUID() },
                { it.platform = "IOS" },
                { it.clientBuild = 6 },
                { it.version = 1 },
                {
                    it.createdAt = it.createdAt.minusNanos(1000)
                    it.version = 1
                },
                {
                    it.rotatedAt = it.rotatedAt.plusNanos(1000)
                    it.version = 1
                },
                { it.expiresAt = it.expiresAt.minusNanos(1000) },
                { it.accessExpiresAt = it.accessExpiresAt.minusNanos(1000) },
                { it.accessHash = "a".repeat(64) },
                { it.refreshHash = "b".repeat(64) },
                { it.bootstrapOperationId = UUID.randomUUID() },
                { it.replayUntil = it.replayUntil.plusNanos(1000) },
            )
        for (change in mutations) {
            val altered = copySpaceGateway(original).also(change)
            f.source.row = altered
            val expected = altered.toSpaceGatewaySession()
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.replayBootstrap(expected, altered.bootstrapOperationId),
            )
        }
        f.source.row = original
        assertThrows(Exception::class.java) {
            f.crypto.decrypt(
                collaborationSpaceGatewayPurpose(original)
                    .replace("matrix-space-gateway", "matrix-gateway"),
                original.replayEncrypted,
            )
        }
        assertNotNull(f.success(f.repo.replayBootstrap(f.session, f.operation)))
    }

    @Test
    fun refreshAndBootstrapReplayReturnTheCommittedPairWithoutRenewingItsLifetime() {
        val f = SpaceGatewayMappingFixture()
        val original = f.create()
        val next =
            f.session.copy(
                version = 1,
                rotatedAt = f.at.plusSeconds(1),
                accessExpiresAt = f.at.plusSeconds(901),
            )
        val renewed =
            f.success(f.repo.rotateRefresh(next, original.refreshToken, f.at.plusSeconds(31)))
        assertNotEquals(original.refreshToken, renewed.refreshToken)
        assertNull(f.success(f.repo.findAccess(original.accessToken)))
        assertTrue(f.success(f.repo.findRefresh(original.refreshToken))!!.previous)
        assertFalse(f.success(f.repo.findRefresh(renewed.refreshToken))!!.previous)
        val replay = f.success(f.repo.replayRefresh(next, original.refreshToken))!!
        assertEquals(renewed.accessToken, replay.accessToken)
        assertEquals(renewed.refreshToken, replay.refreshToken)
        assertEquals(renewed.replayUntil, replay.replayUntil)
        assertEquals(f.session.expiresAt, replay.expiresAt)
        assertNull(f.success(f.repo.replayRefresh(next, renewed.refreshToken)))
        assertNull(f.success(f.repo.replayBootstrap(next, f.operation)))
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.rotateRefresh(next, original.refreshToken, f.at.plusSeconds(32)),
        )
        assertEquals(2, f.source.writes)

        val again = next.copy(version = 2, rotatedAt = f.at.plusSeconds(2), clientBuild = 6)
        val operation = UUID.randomUUID()
        val bootstrap = f.success(f.repo.rotateBootstrap(again, operation, f.at.plusSeconds(32)))
        assertNull(f.success(f.repo.findRefresh(original.refreshToken)))
        assertNull(f.success(f.repo.findRefresh(renewed.refreshToken)))
        assertEquals(
            bootstrap.accessToken,
            f.success(f.repo.replayBootstrap(again, operation))!!.accessToken,
        )
        assertEquals(3, f.source.writes)
    }

    @Test
    fun invalidPhaseCoordinatesAndSubMicrosecondDatabaseValuesFailInsideTheMappingBoundary() {
        val f = SpaceGatewayMappingFixture()
        f.create()
        val original = copySpaceGateway(f.source.row!!)
        val mutations =
            listOf<(CollaborationSpaceGatewaySessionsRecord) -> Unit>(
                { it.platform = "WEB" },
                { it.clientBuild = 0 },
                { it.version = 1000000 },
                { it.createdAt = it.createdAt.plusNanos(1) },
                { it.rotatedAt = it.createdAt.minusSeconds(1) },
                { it.expiresAt = it.createdAt.plusDays(32) },
                { it.accessExpiresAt = it.rotatedAt.plusSeconds(901) },
                { it.accessHash = "INVALID" },
                { it.refreshHash = it.accessHash },
                { it.replayUntil = it.rotatedAt.plusSeconds(61) },
                { it.replayUntil = null },
                { it.replayEncrypted = "" },
                { it.replayEncrypted = "x".repeat(4097) },
                { it.replayKind = "REFRESH" },
                { it.bootstrapOperationId = null },
                { it.previousRefreshHash = "c".repeat(64) },
                { it.revokedAt = it.rotatedAt },
            )
        for (change in mutations) {
            f.source.row = copySpaceGateway(original).also(change)
            assertInstanceOf(Result.Failed::class.java, f.read())
        }
        assertEquals(1, f.source.writes)
        f.source.row =
            copySpaceGateway(original).apply {
                replayEncrypted = null
                replayKind = null
                bootstrapOperationId = null
            }
        assertEquals(f.session, f.success(f.read()))
        assertNull(f.success(f.repo.replayBootstrap(f.session, f.operation)))
    }

    @Test
    fun rotationCannotChangeParentHardDeadlineOrRefreshBuildEvenBeforeDatabaseChecks() {
        val f = SpaceGatewayMappingFixture()
        val pair = f.create()
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        for (altered in
            listOf(
                next.copy(nativeSessionId = UUID.randomUUID()),
                next.copy(expiresAt = next.expiresAt.plusSeconds(1)),
                next.copy(createdAt = next.createdAt.minusSeconds(1)),
                next.copy(clientBuild = 6),
                next.copy(platform = CollaborationPlatform.IOS),
                next.copy(rotatedAt = f.at.minusSeconds(1)),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.rotateRefresh(altered, pair.refreshToken, f.at.plusSeconds(31)),
            )
        }
        assertEquals(1, f.source.writes)
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.rotateRefresh(next, "hris_mxg_r_" + "z".repeat(43), f.at.plusSeconds(31)),
        )
        assertEquals(1, f.source.writes)
    }

    @Test
    fun writeReadbackFaultsNeverReturnAnUnverifiedTokenOrRetryTheWrite() {
        for (fault in
            listOf<(SpaceGatewayMappingFixture) -> Unit>(
                { it.source.row = null },
                { it.source.row!!.deviceId = UUID.randomUUID() },
                { it.source.row!!.replayEncrypted = "different-ciphertext" },
                { it.source.failure = IOException("fixture readback failure") },
            )) {
            val f = SpaceGatewayMappingFixture()
            f.source.afterWrite = { fault(f) }
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.create(f.session, f.operation, f.at.plusSeconds(30)),
            )
            assertEquals(1, f.source.writes)
            assertEquals(1, f.source.reads)
        }
        val f = SpaceGatewayMappingFixture()
        val pair = f.create()
        f.source.afterWrite = {
            f.source.row!!.replayUntil = f.source.row!!.replayUntil.minusNanos(1000)
        }
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.rotateRefresh(next, pair.refreshToken, f.at.plusSeconds(31)),
        )
        assertEquals(2, f.source.writes)
    }

    @Test
    fun cancellationAndInterruptionEscapeReadbackWithoutBeingMappedOrRetried() {
        for (failure in
            listOf(
                CancellationException("fixture cancellation"),
                InterruptedException("fixture interrupt"),
            )) {
            val f = SpaceGatewayMappingFixture()
            f.source.afterWrite = { f.source.failure = failure }
            try {
                val thrown =
                    assertThrows(failure.javaClass) {
                        f.repo.create(f.session, f.operation, f.at.plusSeconds(30))
                    }
                assertSame(failure, thrown)
                assertEquals(1, f.source.writes)
                assertEquals(1, f.source.reads)
            } finally {
                Thread.interrupted()
            }
        }
        val f = SpaceGatewayMappingFixture()
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) {
                f.repo.create(f.session, f.operation, f.at.plusSeconds(30))
            }
            assertEquals(0, f.source.writes)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun retirementRetainsOwnedMetadataAndRequiresVerifiedClearedSecrets() {
        val f = SpaceGatewayMappingFixture()
        val pair = f.create()
        val retiredAt = f.at.plusSeconds(4000)
        f.success(
            f.repo.retire(f.session.spaceId, f.session.accountId, f.session.deviceId, 0, retiredAt)
        )
        assertEquals(f.session.copy(version = 1, revokedAt = retiredAt), f.success(f.read()))
        assertNull(f.success(f.repo.findAccess(pair.accessToken)))
        assertNull(f.success(f.repo.findRefresh(pair.refreshToken)))
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.retire(f.session.spaceId, f.session.accountId, f.session.deviceId, 0, retiredAt),
        )
        assertEquals(2, f.source.writes)
        val broken = SpaceGatewayMappingFixture()
        broken.create()
        broken.source.afterWrite = { broken.source.row!!.accessHash = "a".repeat(64) }
        assertInstanceOf(
            Result.Failed::class.java,
            broken.repo.retire(
                broken.session.spaceId,
                broken.session.accountId,
                broken.session.deviceId,
                0,
                retiredAt,
            ),
        )
        assertEquals(2, broken.source.writes)
        assertInstanceOf(Result.Failed::class.java, f.repo.clearExpiredReplays(f.at, 101))
        f.source.cleanupCount = 2
        assertInstanceOf(Result.Failed::class.java, f.repo.clearExpiredReplays(f.at, 1))
        f.source.cleanupCount = 1
        assertEquals(1, f.success(f.repo.clearExpiredReplays(f.at, 1)))
    }
}
