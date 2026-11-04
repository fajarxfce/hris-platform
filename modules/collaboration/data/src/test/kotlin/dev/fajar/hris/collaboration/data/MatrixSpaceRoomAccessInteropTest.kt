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

/** Actual provider protocol; native event admission and SDK decryption have separate gates. */
class MatrixSpaceRoomAccessInteropTest {
    @Test fun freshGroupMetadataRetainsCreationMembershipPositionsAndReceiptPrivacy() = run(false)

    @Test fun originalRoomMetadataRetainsIdentityThroughAdoptionAndProviderRestart() = run(true)

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
            val peerDevice = UUID.randomUUID()
            val peer = login(f.pair[1], peerDevice)
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
            val bound =
                f.room.copy(
                    intent =
                        f.room.intent.copy(
                            original = f.room.intent.original?.copy(roomId = roomId)
                        ),
                    binding = f.room.binding.copy(roomId = roomId),
                )
            val original = if (legacy) f.original(bound) else null
            val creatorOwner =
                CollaborationSpaceHistoryOwner(
                    f.space,
                    f.pair[1],
                    if (legacy) UUID.randomUUID() else peerDevice,
                    f.server,
                    peer.userId,
                    peer.deviceId,
                    if (legacy) CollaborationLegacyDevicePosition(f.company, peerDevice) else null,
                )
            val published = CollaborationSpaceReadRoom.Published(bound, original)
            val initialRoom = original?.let { CollaborationSpaceReadRoom.Original(it) } ?: published
            // The owned fixture reconfigures its mapped endpoint after provider restart.
            fun access() = InternalCollaborationSpaceMatrixRoomAccessRepository(raw())
            fun state(room: CollaborationSpaceReadRoom = initialRoom) =
                success(access().state(creatorOwner, room, peer.accessToken, deadline()))
            fun membership(at: String?, room: CollaborationSpaceReadRoom = initialRoom): String {
                val rows =
                    success(access().members(creatorOwner, room, at, peer.accessToken, deadline()))
                        .events
                assertEquals(2, rows.size)
                return (rows.single { it["state_key"] == own.userId }.getValue("content")
                    as Map<*, *>)["membership"]
                    as String
            }
            assertEquals(
                peer.userId,
                state().events.single { it["type"] == "m.room.create" }["sender"],
            )
            assertEquals("invite", membership(null))
            val scope =
                success(
                    snapshotCollaborationSpaceReadScope(
                        creatorOwner,
                        if (legacy) emptyList() else listOf(bound),
                        listOfNotNull(original),
                        Instant.now(),
                    )
                )
            fun sync(position: CollaborationSyncPosition? = null): CollaborationMatrixSyncBatch =
                HttpMatrixSyncDataSource(provider.client, provider.config, provider.clock).use {
                    source ->
                    success(
                        InternalCollaborationSpaceMatrixSyncRepository(
                                source,
                                collaborationCursorFixture(),
                            )
                            .sync(
                                creatorOwner,
                                scope,
                                position,
                                0,
                                false,
                                peer.accessToken,
                                deadline(),
                            )
                    )
                }
            val beforeJoin = sync()
            repeat(2) {
                assertEquals(
                    roomId,
                    success(access().join(f.owner, initialRoom, own.accessToken, deadline())),
                )
            }
            assertEquals("join", membership(null))
            assertEquals("invite", membership(beforeJoin.providerSince))
            assertEquals(state().events.toSet(), state(published).events.toSet())
            val encoder = Base64.getEncoder().withoutPadding()
            val message =
                CollaborationRoomMessage(
                    "metadata-receipt",
                    encoder.encodeToString("opaque-metadata-message".toByteArray()),
                    encoder.encodeToString(ByteArray(32) { 7 }),
                    deviceId = peer.deviceId,
                )
            val eventId =
                success(
                    InternalCollaborationMatrixRoomMessageRepository(raw())
                        .send(roomId, message, peer.accessToken, deadline())
                )
            for (type in listOf("m.read.private", "m.read")) success(
                access().receipt(f.owner, initialRoom, type, eventId, own.accessToken, deadline())
            )
            provider.restart()
            assertEquals("invite", membership(beforeJoin.providerSince, published))
            assertEquals("join", membership(null, published))
            assertEquals(
                peer.userId,
                state(published).events.single { it["type"] == "m.room.create" }["sender"],
            )
            val batch =
                sync(
                    CollaborationSyncPosition(
                        scope.digest,
                        beforeJoin.providerSince,
                        beforeJoin.providerFilterId,
                    )
                )
            val output = provider.json.readTree(provider.json.writeValueAsBytes(batch.payload))
            val receipts =
                output.path("rooms").path("join").path(roomId).path("ephemeral").path("events")
            assertTrue(
                receipts.any {
                    it.path("type").asString() == "m.receipt" &&
                        it.path("content").path(eventId).path("m.read").has(own.userId)
                }
            )
            assertFalse(receipts.toString().contains("m.read.private"))
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
                    f.owner.copy(deviceId = freshId, matrixDeviceId = fresh.deviceId, legacy = null)
                assertEquals(own.userId, fresh.userId)
                assertNotEquals(own.deviceId, fresh.deviceId)
                assertEquals(
                    peer.userId,
                    success(access().state(freshOwner, initialRoom, fresh.accessToken, deadline()))
                        .events
                        .single { it["type"] == "m.room.create" }["sender"],
                )
                assertEquals(
                    roomId,
                    success(access().join(freshOwner, published, fresh.accessToken, deadline())),
                )
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
