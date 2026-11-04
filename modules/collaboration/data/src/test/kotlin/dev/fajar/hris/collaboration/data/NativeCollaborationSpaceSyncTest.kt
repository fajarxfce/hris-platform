package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceRoomDataSource
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class NativeCollaborationSpaceSyncTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun emptyAndExpandedScopesRestartWhileUnchangedScopeResumesWithoutNewSendKeys() {
        val f = SpaceKeyExchangeFixture(postgres)
        val provider = Sync(f)
        val cursors = Cursors()
        val keys = f.provider()
        val command = f.command(keys, sync = provider, syncCursors = cursors)
        val empty =
            success(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 1234, true),
                )
            )
        assertTrue(provider.scopes.single().rooms.isEmpty())
        assertEquals(1234, provider.timeout)
        assertTrue(provider.full)
        f.bind()
        val expanded =
            success(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(empty.nextBatch, 0, false),
                )
            )
        assertNull(provider.positions.last())
        assertNotEquals(provider.scopes.first().digest, provider.scopes.last().digest)
        assertEquals(listOf(f.boundRoom), provider.scopes.last().rooms.map { it.roomId })
        val original = f.provenance()
        success(
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(expanded.nextBatch, 0, false),
            )
        )
        assertEquals(
            CollaborationSyncPosition(provider.scopes.last().digest, "s20", "42"),
            provider.positions.last(),
        )
        assertEquals(original, f.provenance())
        assertEquals(0, keys.ownReads.get())
        assertEquals(0, f.leases())
    }

    @Test
    fun originalMetadataChangeWithholdsBothProviderOutcomesAndTheirCursors() {
        val f = ready()
        val actual = rooms(f)
        var changed = false
        val replacement = UUID.randomUUID()
        val observed =
            object : CollaborationSpaceRoomRepository by actual {
                override fun ownedBoundRooms(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    limit: Int,
                ): Result<List<CollaborationSpaceBoundRoom>> =
                    actual.ownedBoundRooms(spaceId, accountId, serverName, limit).map { rows ->
                        if (!changed) rows
                        else
                            rows.map {
                                it.copy(
                                    intent = it.intent.copy(conversationId = replacement),
                                    binding = it.binding.copy(conversationId = replacement),
                                )
                            }
                    }
            }
        val provider = Sync(f)
        val cursors = Cursors()
        provider.after = { changed = true }
        val command =
            f.command(f.provider(), rooms = observed, sync = provider, syncCursors = cursors)
        for (fails in listOf(false, true)) {
            changed = false
            provider.failure =
                if (fails) Failure(FailureKind.UNAVAILABLE, "fixture_sync_failed") else null
            assertEquals(
                "collaboration_sync_scope_changed",
                failed(
                    command.sync(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        CollaborationSyncRequest(null, 0, false),
                    )
                ),
            )
            assertEquals(0, f.leases())
        }
        assertEquals(2, provider.scopes.size)
        assertEquals(0, cursors.seals)
    }

    @Test fun nativeRevocationWithholdsProviderSuccess() = revoked(false)

    @Test fun nativeRevocationWithholdsProviderFailure() = revoked(true)

    private fun revoked(fails: Boolean) {
        val f = ready()
        val provider = Sync(f)
        val cursors = Cursors()
        provider.after = { revoke(f) }
        provider.failure =
            if (fails) Failure(FailureKind.UNAVAILABLE, "fixture_sync_failed") else null
        val command = f.command(f.provider(), sync = provider, syncCursors = cursors)
        val result =
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(null, 0, false),
            ) as Result.Failed
        assertEquals(FailureKind.UNAUTHENTICATED, result.failure.kind)
        assertEquals(0, cursors.seals)
        assertEquals(0, f.leases())
        assertEquals(
            FailureKind.UNAUTHENTICATED,
            (command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 0, false),
                ) as Result.Failed)
                .failure
                .kind,
        )
        assertEquals(1, provider.scopes.size)
    }

    @Test fun nativeRevocationAfterCursorOpenPreventsProviderRead() = cryptoRevocation(true)

    @Test fun nativeRevocationAfterCursorSealWithholdsBatch() = cryptoRevocation(false)

    private fun cryptoRevocation(duringOpen: Boolean) {
        val f = ready()
        val provider = Sync(f)
        val cursors = Cursors()
        val command = f.command(f.provider(), sync = provider, syncCursors = cursors)
        val previous =
            success(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 0, false),
                )
            )
        if (duringOpen) cursors.afterOpen = { revoke(f) } else cursors.afterSeal = { revoke(f) }
        val result =
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(previous.nextBatch, 0, false),
            ) as Result.Failed
        assertEquals(FailureKind.UNAUTHENTICATED, result.failure.kind)
        assertEquals(if (duringOpen) 1 else 2, provider.scopes.size)
        assertEquals(0, f.leases())
    }

    @Test
    fun invalidInputFailedInventoryAndOverCapacityNeverAcquireProviderOrAdvancePosition() {
        val f = ready()
        val provider = Sync(f)
        val cursors = Cursors()
        val actual = rooms(f)
        var acquired = 0
        var mode = "normal"
        val inventory =
            object : CollaborationSpaceRoomRepository by actual {
                override fun ownedBoundRooms(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    limit: Int,
                ): Result<List<CollaborationSpaceBoundRoom>> {
                    acquired++
                    assertEquals(10001, limit)
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                    if (mode == "failure")
                        return Result.Failed(
                            Failure(FailureKind.UNAVAILABLE, "fixture_inventory_failed")
                        )
                    return actual.ownedBoundRooms(spaceId, accountId, serverName, limit).map { rows
                        ->
                        if (mode == "capacity") List(10001) { rows.single() } else rows
                    }
                }
            }
        val command =
            f.command(f.provider(), rooms = inventory, sync = provider, syncCursors = cursors)
        for (request in
            listOf(
                CollaborationSyncRequest(null, -1, false),
                CollaborationSyncRequest(null, 30001, false),
                CollaborationSyncRequest("", 0, false),
                CollaborationSyncRequest("bad\nposition", 0, false),
                CollaborationSyncRequest("x".repeat(4097), 0, false),
            )) {
            assertEquals(
                "invalid_collaboration_sync_request",
                failed(command.sync(f.tokens.accessToken, UUID.randomUUID(), request)),
            )
        }
        assertEquals(0, acquired)
        mode = "failure"
        assertEquals(
            "fixture_inventory_failed",
            failed(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 0, false),
                )
            ),
        )
        mode = "capacity"
        assertEquals(
            "collaboration_sync_scope_capacity",
            failed(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 0, false),
                )
            ),
        )
        mode = "normal"
        assertEquals(
            "invalid_collaboration_sync_cursor",
            failed(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest("not-a-valid-cursor", 0, false),
                )
            ),
        )
        assertTrue(provider.scopes.isEmpty())
        assertEquals(0, cursors.seals)
        assertEquals(0, f.leases())
    }

    @Test
    fun peerOffboardingAndParentPurgePreserveOwnedSyncHistory() {
        val f = ready()
        val provider = Sync(f)
        val command = f.command(f.provider(), sync = provider)
        provider.after = {
            f.revokePeer()
            f.s.owner.update("delete from native_sessions where id=?", f.peerParent)
        }
        success(
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(null, 0, false),
            )
        )
        provider.after = {}
        success(
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(null, 0, false),
            )
        )
        assertEquals(provider.scopes.first(), provider.scopes.last())
        assertEquals(listOf(f.boundRoom), provider.scopes.last().rooms.map { it.roomId })
        assertEquals(0, f.leases())
    }

    @Test
    fun cancellationInterruptionAndProviderFailureReleaseTheOwnedRequest() {
        val f = ready()
        val provider = Sync(f)
        val cursors = Cursors()
        val command = f.command(f.provider(), sync = provider, syncCursors = cursors)
        provider.after = { throw CancellationException("fictional sync cancellation") }
        assertThrows(CancellationException::class.java) {
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(null, 0, false),
            )
        }
        assertEquals(0, f.leases())
        provider.after = { throw InterruptedException("fictional sync interruption") }
        assertThrows(InterruptedException::class.java) {
            command.sync(
                f.tokens.accessToken,
                UUID.randomUUID(),
                CollaborationSyncRequest(null, 0, false),
            )
        }
        assertEquals(0, f.leases())
        provider.after = {}
        provider.failure = Failure(FailureKind.UNAVAILABLE, "fixture_sync_failed")
        assertEquals(
            "fixture_sync_failed",
            failed(
                command.sync(
                    f.tokens.accessToken,
                    UUID.randomUUID(),
                    CollaborationSyncRequest(null, 0, false),
                )
            ),
        )
        assertEquals(0, cursors.seals)
        assertEquals(0, f.leases())
    }

    @Test
    fun lateProviderAndLateCursorSealCannotExtendTheCapturedDeadline() {
        val f = ready()
        val provider = Sync(f)
        val cursors = Cursors()
        var offset = 0L
        val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant(): Instant = Instant.now().plusSeconds(offset)
            }
        val command = f.command(f.provider(), clock = clock, sync = provider, syncCursors = cursors)
        for (atSeal in listOf(false, true)) {
            offset = 0
            provider.after = if (atSeal) ({}) else ({ offset = 31 })
            cursors.afterSeal = if (atSeal) ({ offset = 31 }) else ({})
            assertEquals(
                "collaboration_admission_changed",
                failed(
                    command.sync(
                        f.tokens.accessToken,
                        UUID.randomUUID(),
                        CollaborationSyncRequest(null, 30000, false),
                    )
                ),
            )
            assertEquals(0, f.leases())
        }
        assertEquals(2, provider.scopes.size)
        assertEquals(1, cursors.seals)
    }

    private fun ready() = SpaceKeyExchangeFixture(postgres).also { it.bind() }

    private fun rooms(f: SpaceKeyExchangeFixture) =
        StoredCollaborationSpaceRoomRepository(PostgresCollaborationSpaceRoomDataSource(f.base.sql))

    private fun revoke(f: SpaceKeyExchangeFixture) {
        f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
            f.s.parent,
        )
    }

    private class Cursors : CollaborationSpaceSyncCursorRepository {
        private val actual =
            EncryptedCollaborationSpaceSyncCursorRepository(collaborationCursorFixture())
        var afterOpen: () -> Unit = {}
        var afterSeal: () -> Unit = {}
        var seals = 0

        override fun open(
            owner: CollaborationSpaceHistoryOwner,
            cursor: String,
        ): Result<CollaborationSyncPosition?> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            return actual.open(owner, cursor).also { afterOpen() }
        }

        override fun seal(
            owner: CollaborationSpaceHistoryOwner,
            position: CollaborationSyncPosition,
        ): Result<String> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            seals++
            return actual.seal(owner, position).also { afterSeal() }
        }
    }

    private class Sync(private val f: SpaceKeyExchangeFixture) :
        CollaborationSpaceMatrixSyncRepository {
        val scopes = mutableListOf<CollaborationSpaceReadScope>()
        val positions = mutableListOf<CollaborationSyncPosition?>()
        var timeout = -1
        var full = false
        var after: () -> Unit = {}
        var failure: Failure? = null

        override fun sync(
            owner: CollaborationSpaceHistoryOwner,
            scope: CollaborationSpaceReadScope,
            position: CollaborationSyncPosition?,
            timeoutMillis: Int,
            fullState: Boolean,
            bearer: String,
            deadline: Instant,
        ): Result<CollaborationMatrixSyncBatch> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            assertEquals(f.s.account, owner.accountId)
            assertEquals(f.s.space, owner.spaceId)
            assertEquals(f.s.device, owner.deviceId)
            assertEquals(f.s.user, owner.matrixUserId)
            assertEquals(f.ownKeys.deviceId, owner.matrixDeviceId)
            assertNull(owner.legacy)
            assertEquals("fixture-owned-query-token", bearer)
            assertTrue(deadline > Instant.now() && deadline <= Instant.now().plusSeconds(30))
            assertTrue(f.leases() in 1..7)
            scopes += scope
            positions += position
            timeout = timeoutMillis
            full = fullState
            after()
            return failure?.let { Result.Failed(it) }
                ?: Result.Success(
                    CollaborationMatrixSyncBatch(
                        "s20",
                        "42",
                        mapOf("rooms" to emptyMap<String, Any>()),
                    )
                )
        }
    }

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
