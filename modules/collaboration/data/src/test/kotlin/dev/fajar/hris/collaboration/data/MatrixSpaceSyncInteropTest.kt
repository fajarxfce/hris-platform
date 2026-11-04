package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual opaque provider protocol; this does not prove SDK Olm/Megolm decryption. */
class MatrixSpaceSyncInteropTest {
    @Test fun freshGroupDeviceSyncRetainsOwnedEventsAndPendingFramesAcrossRestart() = run(false)

    @Test fun retainedOriginalDeviceSyncKeepsUnadoptedKeysAndHistoryDuringAdoption() = run(true)

    private fun run(legacy: Boolean) {
        MatrixProviderFixture("10m").use { provider ->
            provider.start()
            val f = SpaceRoomHistoryFixture(legacy)
            fun raw() = HttpMatrixClientDataSource(provider.client, provider.config, provider.clock)
            fun deadline() = Instant.now().plusSeconds(30)
            fun login(account: UUID, device: UUID): CollaborationMatrixCredential {
                val at = Instant.now()
                return if (legacy)
                    success(
                        provider.repository.login(
                            CollaborationMatrixLogin(
                                f.company,
                                account,
                                device,
                                at,
                                at.plusSeconds(30),
                            )
                        )
                    )
                else
                    success(
                        InternalCollaborationSpaceMatrixRepository(
                                HmacMatrixJwtDataSource(provider.config),
                                raw(),
                                provider.clock,
                            )
                            .login(
                                CollaborationSpaceMatrixLogin(
                                    f.space,
                                    account,
                                    device,
                                    null,
                                    null,
                                    at,
                                    at.plusSeconds(30),
                                )
                            )
                    )
            }
            val own = login(f.pair[0], if (legacy) f.originalDevice else f.device)
            val peer = login(f.pair[1], UUID.randomUUID())
            assertEquals(f.owner.matrixUserId, own.userId)
            assertEquals(f.owner.matrixDeviceId, own.deviceId)
            val created =
                if (legacy)
                    success(
                        InternalCollaborationMatrixRoomRepository(raw(), f.server)
                            .create(
                                CollaborationMatrixDirectRoom(
                                    f.company,
                                    requireNotNull(f.room.intent.original).conversationId,
                                    f.pair[1],
                                    f.pair[0],
                                ),
                                peer.accessToken,
                                deadline(),
                            )
                    )
                else
                    success(
                        InternalCollaborationSpaceMatrixRoomRepository(raw(), f.server)
                            .create(
                                CollaborationSpaceMatrixDirectRoom(
                                    f.space,
                                    f.conversation,
                                    f.pair[1],
                                    f.pair[0],
                                    peer.userId,
                                    own.userId,
                                ),
                                peer.accessToken,
                                deadline(),
                            )
                    )
            val roomId = (created as CollaborationMatrixRoomCreation.Created).roomId
            success(
                InternalCollaborationSpaceMatrixRoomRepository(raw(), f.server)
                    .join(roomId, own.accessToken, deadline())
            )
            val bound =
                f.room.copy(
                    intent =
                        f.room.intent.copy(
                            original = f.room.intent.original?.copy(roomId = roomId)
                        ),
                    binding = f.room.binding.copy(roomId = roomId),
                )
            val original = if (legacy) f.original(bound) else null
            fun scope(owner: CollaborationSpaceHistoryOwner = f.owner, adopted: Boolean = !legacy) =
                success(
                    snapshotCollaborationSpaceReadScope(
                        owner,
                        if (adopted) listOf(bound) else emptyList(),
                        listOfNotNull(original),
                        Instant.now(),
                    )
                )
            val crypto = collaborationCursorFixture()
            val encoder = Base64.getEncoder().withoutPadding()
            val message =
                CollaborationRoomMessage(
                    "original-sync-message",
                    encoder.encodeToString("opaque-original-history".toByteArray()),
                    encoder.encodeToString(ByteArray(32) { 3 }),
                    deviceId = peer.deviceId,
                )
            val eventId =
                success(
                    InternalCollaborationMatrixRoomMessageRepository(raw())
                        .send(roomId, message, peer.accessToken, deadline())
                )
            val envelope =
                CollaborationOlmEnvelope(
                    encoder.encodeToString(ByteArray(32) { 4 }),
                    encoder.encodeToString(ByteArray(32) { 5 }),
                    "opaque-original-room-key",
                    0,
                    "original-pending-key",
                )
            success(
                InternalCollaborationMatrixToDeviceRepository(raw())
                    .send(
                        CollaborationToDevice(
                            "original-sync-key",
                            mapOf(own.userId to mapOf(own.deviceId to envelope)),
                        ),
                        peer.accessToken,
                        deadline(),
                    )
            )
            provider.restart()
            HttpMatrixSyncDataSource(provider.client, provider.config, provider.clock).use { source
                ->
                val repository = InternalCollaborationSpaceMatrixSyncRepository(source, crypto)
                fun sync(
                    owner: CollaborationSpaceHistoryOwner = f.owner,
                    scope: CollaborationSpaceReadScope = scope(owner),
                    position: CollaborationSyncPosition? = null,
                    bearer: String = own.accessToken,
                ) = success(repository.sync(owner, scope, position, 0, false, bearer, deadline()))
                fun tree(batch: CollaborationMatrixSyncBatch) =
                    provider.json.readTree(provider.json.writeValueAsBytes(batch.payload))
                fun pending(batch: CollaborationMatrixSyncBatch) =
                    tree(batch).path("to_device").path("events").filter {
                        it.path("content").path("org.matrix.msgid").asString() ==
                            "original-pending-key"
                    }
                fun assertHistory(batch: CollaborationMatrixSyncBatch) {
                    val joined = tree(batch).path("rooms").path("join")
                    assertEquals(setOf(roomId), joined.propertyNames().toSet())
                    val room = joined.path(roomId)
                    val events =
                        room.path("state").path("events").toList() +
                            room.path("timeline").path("events").toList()
                    assertEquals(
                        peer.userId,
                        events
                            .single { it.path("type").asString() == "m.room.create" }
                            .path("sender")
                            .asString(),
                    )
                    val originalEvent = events.single { it.path("event_id").asString() == eventId }
                    assertEquals(peer.userId, originalEvent.path("sender").asString())
                    assertEquals(
                        message.ciphertext,
                        originalEvent.path("content").path("ciphertext").asString(),
                    )
                    assertEquals(
                        peer.deviceId,
                        originalEvent.path("content").path("device_id").asString(),
                    )
                    val cursor = room.path("timeline").path("prev_batch").asString()
                    assertTrue(
                        openMatrixSpaceRoomCursor(crypto, f.owner, bound, cursor).isNotBlank()
                    )
                    if (original != null)
                        assertEquals(
                            openMatrixSpaceRoomCursor(crypto, f.owner, bound, cursor),
                            openMatrixSpaceRoomCursor(crypto, f.owner, original, cursor),
                        )
                }
                val initial = sync()
                assertHistory(initial)
                val queued = pending(initial).single()
                assertEquals(peer.userId, queued.path("sender").asString())
                assertEquals(
                    envelope.body,
                    queued
                        .path("content")
                        .path("ciphertext")
                        .path(envelope.recipientKey)
                        .path("body")
                        .asString(),
                )
                // A full scope reinitialization has not acknowledged the pending original frame.
                val adoptedScope = scope(adopted = true)
                val adopted = sync(scope = adoptedScope)
                assertHistory(adopted)
                assertEquals(1, pending(adopted).size)
                assertEquals(initial.providerFilterId, adopted.providerFilterId)
                val advanced =
                    sync(
                        scope = adoptedScope,
                        position =
                            CollaborationSyncPosition(
                                adoptedScope.digest,
                                adopted.providerSince,
                                adopted.providerFilterId,
                            ),
                    )
                assertTrue(pending(advanced).isEmpty())
                assertEquals(adopted.providerFilterId, advanced.providerFilterId)
                if (legacy) {
                    val freshId = UUID.randomUUID()
                    val at = Instant.now()
                    val fresh =
                        success(
                            InternalCollaborationSpaceMatrixRepository(
                                    HmacMatrixJwtDataSource(provider.config),
                                    raw(),
                                    provider.clock,
                                )
                                .login(
                                    CollaborationSpaceMatrixLogin(
                                        f.space,
                                        f.pair[0],
                                        freshId,
                                        f.company,
                                        null,
                                        at,
                                        at.plusSeconds(30),
                                    )
                                )
                        )
                    val freshOwner =
                        f.owner.copy(
                            deviceId = freshId,
                            matrixDeviceId = "HRISG_${freshId.toString().replace("-", "")}",
                            legacy = null,
                        )
                    assertEquals(own.userId, fresh.userId)
                    assertNotEquals(own.deviceId, fresh.deviceId)
                    val freshBatch =
                        sync(
                            freshOwner,
                            scope(freshOwner, adopted = true),
                            bearer = fresh.accessToken,
                        )
                    val joined = tree(freshBatch).path("rooms").path("join").path(roomId)
                    assertTrue(
                        joined.path("timeline").path("events").any {
                            it.path("event_id").asString() == eventId
                        }
                    )
                    assertTrue(pending(freshBatch).isEmpty())
                    val freshCursor = joined.path("timeline").path("prev_batch").asString()
                    assertTrue(
                        openMatrixSpaceRoomCursor(
                                crypto,
                                freshOwner,
                                requireNotNull(original),
                                freshCursor,
                            )
                            .isNotBlank()
                    )
                    assertThrows(MatrixRoomCursorException::class.java) {
                        openMatrixSpaceRoomCursor(crypto, f.owner, bound, freshCursor)
                    }
                }
            }
        }
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
