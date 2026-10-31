package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSyncCursorRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.IdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSyncCursorTest {
    private val owner =
        CollaborationSyncOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "proof.invalid",
        )
    private val position = CollaborationSyncPosition("a".repeat(64), "s42_0_0_0", "123")
    private val old = SecretKeySpec(ByteArray(32) { 1 }, "AES")
    private val next = SecretKeySpec(ByteArray(32) { 2 }, "AES")

    private fun repository(active: String = "old", retainOld: Boolean = true) =
        EncryptedCollaborationSyncCursorRepository(
            JceIdentityTokenDataSource(
                IdentityKeyring(
                    active,
                    buildMap {
                        if (retainOld) put("old", old)
                        put("next", next)
                    },
                )
            )
        )

    @Test
    fun encryptedPositionSurvivesRefreshAndKeyRotationWithoutExposingProviderSince() {
        val repo = repository()
        val cursor = (repo.seal(owner, position) as Result.Success).value
        assertEquals(Result.Success(position), repo.open(owner.copy(), cursor))
        assertFalse(cursor.contains(position.providerSince))
        assertNotEquals(cursor, (repo.seal(owner, position) as Result.Success).value)
        val rotated = repository("next")
        assertEquals(Result.Success(position), rotated.open(owner, cursor))
        val renewed = (rotated.seal(owner, position) as Result.Success).value
        assertEquals(Result.Success(position), repository("next", false).open(owner, renewed))
        assertEquals(
            "collaboration_sync_reset_required",
            (repository("next", false).open(owner, cursor) as Result.Failed).failure.code,
        )
    }

    @Test
    fun foreignOwnerAndTamperedCiphertextNeverYieldAPosition() {
        val repo = repository()
        val cursor = (repo.seal(owner, position) as Result.Success).value
        for (other in
            listOf(
                owner.copy(companyId = UUID.randomUUID()),
                owner.copy(accountId = UUID.randomUUID()),
                owner.copy(deviceId = UUID.randomUUID()),
                owner.copy(serverName = "other.invalid"),
            )) {
            assertEquals(
                "invalid_collaboration_sync_cursor",
                (repo.open(other, cursor) as Result.Failed).failure.code,
            )
        }
        val pieces = cursor.split('.').toMutableList()
        pieces[2] = (if (pieces[2].first() == 'A') "B" else "A") + pieces[2].drop(1)
        assertEquals(
            "invalid_collaboration_sync_cursor",
            (repo.open(owner, pieces.joinToString(".")) as Result.Failed).failure.code,
        )
        for (bad in listOf("", cursor + "\n", "x".repeat(4097))) assertInstanceOf(
            Result.Failed::class.java,
            repo.open(owner, bad),
        )
    }

    @Test
    fun framingAndCancellationAreBoundedWithoutErasingAnything() {
        val repo = repository()
        for (bad in
            listOf(
                position.copy(scopeDigest = "short"),
                position.copy(providerSince = "a\nb"),
                position.copy(providerSince = "x".repeat(1025)),
                position.copy(providerFilterId = ""),
            )) assertInstanceOf(Result.Failed::class.java, repo.seal(owner, bad))
        val largest =
            position.copy(providerSince = "s".repeat(1024), providerFilterId = "f".repeat(128))
        assertEquals(
            Result.Success(largest),
            repo.open(owner, (repo.seal(owner, largest) as Result.Success).value),
        )
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) { repo.seal(owner, position) }
        } finally {
            Thread.interrupted()
        }
    }
}
