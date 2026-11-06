package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSummaryCursorRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.IdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSummaryCursorTest {
    private val owner =
        CollaborationSummaryOwner(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
    private val scope = CollaborationSummaryScope(owner, 2, 3, 4)
    private val cursor =
        CollaborationSummaryCursor(
            scope,
            UUID.randomUUID(),
            CollaborationSummaryPhase.CHANGES,
            17,
            Instant.parse("2026-11-05T00:00:00Z"),
            Instant.parse("2026-11-12T00:00:00Z"),
            25,
        )

    private fun repository(active: String = "old", retainOld: Boolean = true) =
        EncryptedCollaborationSummaryCursorRepository(
            JceIdentityTokenDataSource(
                IdentityKeyring(
                    active,
                    buildMap {
                        if (retainOld) put("old", SecretKeySpec(ByteArray(32) { 1 }, "AES"))
                        put("next", SecretKeySpec(ByteArray(32) { 2 }, "AES"))
                    },
                )
            )
        )

    private fun seal(value: CollaborationSummaryCursor = cursor) =
        (repository().seal(value) as Result.Success).value

    @Test
    fun nativeParentAndAllPartitionsAreAuthenticatedWithoutExposingPosition() {
        val token = seal()
        assertEquals(Result.Success(cursor), repository().open(owner.copy(), token))
        assertFalse(token.contains(cursor.epoch.toString()))
        assertNotEquals(token, seal())
        for (other in
            listOf(
                owner.copy(companyId = UUID.randomUUID()),
                owner.copy(accountId = UUID.randomUUID()),
                owner.copy(parentId = UUID.randomUUID()),
            )) {
            assertEquals(
                "invalid_collaboration_sync_cursor",
                (repository().open(other, token) as Result.Failed).failure.code,
            )
        }
        val fields = token.split('.').toMutableList()
        fields[2] = (if (fields[2].first() == 'A') "B" else "A") + fields[2].drop(1)
        assertEquals(
            "invalid_collaboration_sync_cursor",
            (repository().open(owner, fields.joinToString(".")) as Result.Failed).failure.code,
        )
    }

    @Test
    fun keyRotationAndBothPhasesRoundTripWithoutResettingTheParent() {
        val token = seal()
        assertEquals(Result.Success(cursor), repository("next").open(owner, token))
        assertEquals(
            "collaboration_sync_reset_required",
            (repository("next", false).open(owner, token) as Result.Failed).failure.code,
        )
        val renewed = (repository("next").seal(cursor) as Result.Success).value
        assertEquals(Result.Success(cursor), repository("next", false).open(owner, renewed))
        val bootstrap =
            cursor.copy(
                phase = CollaborationSummaryPhase.BOOTSTRAP,
                upperPosition = null,
                after = UUID.randomUUID(),
                page = 999,
                expiresAt = cursor.issuedAt.plusSeconds(900),
            )
        assertEquals(Result.Success(bootstrap), repository().open(owner, seal(bootstrap)))
    }

    @Test
    fun malformedBoundsAndCancellationFailWithoutReturningAPosition() {
        for (bad in listOf("", "x".repeat(2049), seal() + "\n")) assertInstanceOf(
            Result.Failed::class.java,
            repository().open(owner, bad),
        )
        for (bad in
            listOf(
                cursor.copy(position = -1),
                cursor.copy(upperPosition = 16),
                cursor.copy(page = 1),
                cursor.copy(after = UUID.randomUUID()),
                cursor.copy(issuedAt = cursor.issuedAt.plusNanos(1)),
                cursor.copy(scope = scope.copy(membershipVersion = -1)),
                cursor.copy(expiresAt = cursor.issuedAt),
            )) assertInstanceOf(Result.Failed::class.java, repository().seal(bad))
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) { repository().open(owner, "anything") }
        } finally {
            Thread.interrupted()
        }
    }
}
