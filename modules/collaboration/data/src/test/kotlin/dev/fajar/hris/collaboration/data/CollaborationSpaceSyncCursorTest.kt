package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.IdentityKeyUnavailableException
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceSyncCursorTest {
    private val f = SpaceRoomHistoryFixture(legacy = true)
    private val crypto = collaborationCursorFixture()
    private val group = EncryptedCollaborationSpaceSyncCursorRepository(crypto)
    private val company = EncryptedCollaborationSyncCursorRepository(crypto)
    private val position = CollaborationSyncPosition("a".repeat(64), "s25", "42")
    private val oldOwner = CollaborationSyncOwner(f.company, f.pair[0], f.originalDevice, f.server)
    private val freshId = UUID.randomUUID()
    private val fresh =
        f.owner.copy(
            deviceId = freshId,
            matrixDeviceId = "HRISG_${compact(freshId)}",
            legacy = null,
        )

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            value,
            (value as? Result.Failed)?.failure?.code,
        )
        return (value as Result.Success).value
    }

    private fun invalid(value: Result<*>) =
        assertEquals(
            Failure(FailureKind.VALIDATION, "invalid_collaboration_sync_cursor"),
            (value as Result.Failed).failure,
        )

    @Test
    fun authenticatedOriginalCursorForcesNewScopeWhileFreshDevicesCannotInheritIt() {
        val old = success(company.seal(oldOwner, position))
        assertNull(success(group.open(f.owner, old)))
        invalid(group.open(fresh, old))
        for (owner in listOf(f.owner, fresh, SpaceRoomHistoryFixture().owner)) {
            val cursor = success(group.seal(owner, position))
            assertEquals(position, success(group.open(owner, cursor)))
            invalid(company.open(oldOwner, cursor))
            invalid(group.open(if (owner == fresh) f.owner else fresh, cursor))
        }
        for (other in
            listOf(
                oldOwner.copy(companyId = UUID.randomUUID()),
                oldOwner.copy(accountId = f.pair[1]),
                oldOwner.copy(deviceId = UUID.randomUUID()),
            )) {
            invalid(group.open(f.owner, success(company.seal(other, position))))
        }
    }

    @Test
    fun groupCursorBindsEveryCanonicalOwnerCoordinateAndRejectsMalformedOwnersBeforeCrypto() {
        val cursor = success(group.seal(f.owner, position))
        val anotherDevice = UUID.randomUUID()
        val anotherOldDevice = UUID.randomUUID()
        val otherCompany = UUID.randomUUID()
        val otherServer = "other.example.test"
        val otherUser =
            "@${collaborationMatrixLocalpart(otherCompany, f.owner.accountId)}:${f.server}"
        for (owner in
            listOf(
                f.owner.copy(spaceId = UUID.randomUUID()),
                f.owner.copy(accountId = f.pair[1], matrixUserId = f.user(f.pair[1])),
                f.owner.copy(deviceId = anotherDevice),
                f.owner.copy(
                    serverName = otherServer,
                    matrixUserId = f.owner.matrixUserId.replace(f.server, otherServer),
                ),
                f.owner.copy(
                    matrixUserId = otherUser,
                    legacy = CollaborationLegacyDevicePosition(otherCompany, f.originalDevice),
                ),
                f.owner.copy(
                    matrixDeviceId = "HRIS_${compact(anotherOldDevice)}",
                    legacy = CollaborationLegacyDevicePosition(f.company, anotherOldDevice),
                ),
            )) {
            assertTrue(validCollaborationSpaceHistoryIdentity(owner))
            invalid(group.open(owner, cursor))
        }
        var decryptions = 0
        val tracked =
            object : IdentityTokenDataSource by crypto {
                override fun decrypt(purpose: String, value: String): String {
                    decryptions++
                    return crypto.decrypt(purpose, value)
                }
            }
        val repository = EncryptedCollaborationSpaceSyncCursorRepository(tracked)
        for (invalidOwner in
            listOf(
                f.owner.copy(spaceId = UUID(0, 0)),
                f.owner.copy(matrixDeviceId = fresh.matrixDeviceId),
                f.owner.copy(matrixUserId = f.user(f.pair[1])),
            )) {
            invalid(repository.open(invalidOwner, success(company.seal(oldOwner, position))))
        }
        assertEquals(0, decryptions)
    }

    @Test
    fun malformedPositionsKeyLossAndCancellationKeepClassificationWithoutLegacyRetry() {
        for (bad in
            listOf(
                position.copy(scopeDigest = "invalid"),
                position.copy(providerSince = "bad\nposition"),
                position.copy(providerSince = "x".repeat(1025)),
                position.copy(providerFilterId = ""),
                position.copy(providerFilterId = "x".repeat(129)),
            )) invalid(group.seal(f.owner, bad))
        for (cursor in listOf("", "bad\nwire", "x".repeat(4097))) invalid(
            group.open(f.owner, cursor)
        )
        var decryptions = 0
        var error: Exception = IdentityKeyUnavailableException()
        val unavailable =
            object : IdentityTokenDataSource by crypto {
                override fun decrypt(purpose: String, value: String): String {
                    decryptions++
                    throw error
                }

                override fun encrypt(purpose: String, value: String): String = throw error
            }
        val repository = EncryptedCollaborationSpaceSyncCursorRepository(unavailable)
        assertEquals(
            Failure(FailureKind.CONFLICT, "collaboration_sync_reset_required"),
            (repository.open(f.owner, "fixture") as Result.Failed).failure,
        )
        assertEquals(1, decryptions)
        assertEquals(
            Failure(FailureKind.CONFLICT, "collaboration_sync_reset_required"),
            (repository.seal(f.owner, position) as Result.Failed).failure,
        )
        error = CancellationException("fictional cursor cancellation")
        assertThrows(CancellationException::class.java) { repository.open(f.owner, "fixture") }
        assertEquals(2, decryptions)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) { repository.open(f.owner, "fixture") }
            assertEquals(2, decryptions)
        } finally {
            Thread.interrupted()
        }
        val malformed =
            object : IdentityTokenDataSource by crypto {
                override fun decrypt(purpose: String, value: String) = "one\ntwo\nthree\nfour"
            }
        invalid(EncryptedCollaborationSpaceSyncCursorRepository(malformed).open(f.owner, "fixture"))
    }
}
