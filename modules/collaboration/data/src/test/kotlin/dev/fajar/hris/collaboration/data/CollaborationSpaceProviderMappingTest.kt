package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceProviderSessionDataSource
import dev.fajar.hris.collaboration.data.mappers.spaceProviderRow
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSpaceProviderSessionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProviderSessionsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

class CollaborationSpaceProviderMappingTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val at = Instant.parse("2026-11-03T21:00:00.123456Z")
    private val state =
        CollaborationSpaceProviderState(
            space,
            account,
            device,
            parent,
            "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test",
            "HRISG_${compact(device)}",
            0,
            at,
            at,
            at.plusSeconds(600),
        )
    private val session = CollaborationSpaceProviderSession(state, "fictional-private-token")
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 27 })
    private val crypto = JceIdentityTokenDataSource(parseIdentityKeyring("fixture", "fixture:$key"))

    @Test
    fun changingEachAuthenticatedOwnerProviderGenerationOrDeadlineCannotReinterpretCiphertext() {
        val source = Source()
        val repo = EncryptedCollaborationSpaceProviderSessionRepository(source, crypto)
        success(repo.create(session))
        val encrypted = requireNotNull(source.row?.tokenEncrypted)
        assertEquals(session, success(repo.credential(state)))
        assertFalse(encrypted.contains(session.accessToken))
        assertTrue(session.toString().contains("redacted"))
        assertFalse(state.toString().contains(state.matrixUserId))
        val otherAccount = UUID.randomUUID()
        val otherDevice = UUID.randomUUID()
        val otherSpace = UUID.randomUUID()
        for (changed in
            listOf(
                state.copy(
                    spaceId = otherSpace,
                    matrixUserId =
                        "@hrisg_${compact(otherSpace)}_${compact(account)}:matrix.example.test",
                ),
                state.copy(
                    accountId = otherAccount,
                    matrixUserId =
                        "@hrisg_${compact(space)}_${compact(otherAccount)}:matrix.example.test",
                ),
                state.copy(
                    deviceId = otherDevice,
                    matrixDeviceId = "HRISG_${compact(otherDevice)}",
                ),
                state.copy(nativeSessionId = UUID.randomUUID()),
                state.copy(
                    matrixUserId =
                        state.matrixUserId.replace("matrix.example.test", "other.example.test")
                ),
                state.copy(matrixDeviceId = "HRIS_${compact(UUID.randomUUID())}"),
                state.copy(generation = 1),
                state.copy(createdAt = at.minusNanos(1000)),
                state.copy(updatedAt = at.plusNanos(1000)),
                state.copy(expiresAt = state.expiresAt.minusNanos(1000)),
            )) {
            source.row = spaceProviderRow(changed).apply { tokenEncrypted = encrypted }
            assertEquals(
                changed,
                success(repo.metadata(changed.spaceId, changed.accountId, changed.deviceId)),
            )
            assertInstanceOf(Result.Failed::class.java, repo.credential(changed))
        }
    }

    @Test
    fun persistedMicrosecondPrecisionAndRetiredStateAreExplicitWithoutDecryptingMetadata() {
        val source = Source()
        val repo = EncryptedCollaborationSpaceProviderSessionRepository(source, crypto)
        val imprecise =
            state.copy(
                createdAt = at.plusNanos(999),
                updatedAt = at.plusNanos(999),
                expiresAt = state.expiresAt.plusNanos(999),
            )
        success(repo.create(session.copy(state = imprecise)))
        assertEquals(state, success(repo.metadata(space, account, device)))
        assertEquals(session, success(repo.credential(state)))
        assertInstanceOf(Result.Failed::class.java, repo.credential(imprecise))
        val retired = state.copy(generation = 1000000, clearedAt = at)
        source.row = spaceProviderRow(retired)
        assertEquals(retired, success(repo.metadata(space, account, device)))
        assertInstanceOf(Result.Failed::class.java, repo.credential(retired))
        assertInstanceOf(Result.Failed::class.java, repo.create(session.copy(state = retired)))
        source.row = null
        assertEquals(Result.Success(null), repo.metadata(space, account, device))
        assertInstanceOf(Result.Failed::class.java, repo.credential(state))
    }

    @Test
    fun invalidRowsAndTechnicalFailuresAreContainedWhileCancellationAndInterruptionEscape() {
        val source = Source()
        val repo = EncryptedCollaborationSpaceProviderSessionRepository(source, crypto)
        for (change in
            listOf<(CollaborationSpaceProviderSessionsRecord) -> Unit>(
                { it.accountId = UUID.randomUUID() },
                { it.deviceId = UUID.randomUUID() },
                { it.matrixUserId = "@foreign:matrix.example.test" },
                { it.matrixDeviceId = "HRISG_wrong" },
                { it.generation = 1000000 },
                { it.tokenExpiresAt = it.updatedAt },
                { it.updatedAt = it.createdAt.minusNanos(1000) },
                { it.tokenEncrypted = null },
                { it.clearedAt = it.updatedAt },
                { it.nativeSessionId = null },
            )) {
            source.row =
                spaceProviderRow(state).apply { tokenEncrypted = "encrypted-fixture" }.also(change)
            assertInstanceOf(Result.Failed::class.java, repo.metadata(space, account, device))
        }
        fun failed(error: Exception): Result<CollaborationSpaceProviderState?> =
            EncryptedCollaborationSpaceProviderSessionRepository(
                    object : Source() {
                        override fun find(
                            spaceId: UUID,
                            accountId: UUID,
                            deviceId: UUID,
                        ): CollaborationSpaceProviderSessionsRecord? = throw error
                    },
                    crypto,
                )
                .metadata(space, account, device)
        assertInstanceOf(Result.Failed::class.java, failed(QueryTimeoutException("fixture")))
        assertThrows(CancellationException::class.java) { failed(CancellationException("fixture")) }
        assertThrows(InterruptedException::class.java) { failed(InterruptedException("fixture")) }
        for (token in listOf("", "x".repeat(2049), "line\nbreak", "with space")) assertInstanceOf(
            Result.Failed::class.java,
            repo.create(session.copy(accessToken = token)),
        )
    }

    private open class Source : CollaborationSpaceProviderSessionDataSource {
        var row: CollaborationSpaceProviderSessionsRecord? = null

        override fun find(spaceId: UUID, accountId: UUID, deviceId: UUID) = row

        override fun insert(row: CollaborationSpaceProviderSessionsRecord) {
            this.row = row
        }

        override fun replace(
            row: CollaborationSpaceProviderSessionsRecord,
            expectedGeneration: Long,
        ): Boolean = error("Unexpected rotation")

        override fun clear(
            spaceId: UUID,
            accountId: UUID,
            deviceId: UUID,
            expectedGeneration: Long,
            at: OffsetDateTime,
        ): Boolean = error("Unexpected cleanup")
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
