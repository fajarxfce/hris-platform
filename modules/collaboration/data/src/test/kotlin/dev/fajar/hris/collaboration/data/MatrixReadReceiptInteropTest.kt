package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationRoomScope
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixReadReceiptInteropTest {
    @Test
    fun actualPrivateReceiptStaysOwnAndPublicReceiptReplicatesWithoutBackwardMovement() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val creator = f.login()
            val account = UUID.randomUUID()
            val device = UUID.randomUUID()
            val at = f.clock.instant()
            val peer =
                success(
                    f.repository.login(
                        f.request.copy(
                            accountId = account,
                            deviceId = device,
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    )
                )
            val source = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            fun deadline() = f.clock.instant().plusSeconds(20)
            val conversation = UUID.randomUUID()
            val id =
                (success(
                        InternalCollaborationMatrixRoomRepository(source, f.config.serverName)
                            .create(
                                CollaborationMatrixDirectRoom(
                                    f.request.companyId,
                                    conversation,
                                    f.request.accountId,
                                    account,
                                ),
                                creator.accessToken,
                                deadline(),
                            )
                    )
                        as CollaborationMatrixRoomCreation.Created)
                    .roomId
            val room =
                CollaborationOwnedRoom(
                    f.request.companyId,
                    conversation,
                    f.request.accountId,
                    account,
                    f.request.accountId,
                    f.config.serverName,
                    id,
                )
            val owner =
                CollaborationSyncOwner(
                    f.request.companyId,
                    f.request.accountId,
                    f.request.deviceId,
                    f.config.serverName,
                )
            val peerOwner = owner.copy(accountId = account, deviceId = device)
            val access = InternalCollaborationMatrixRoomAccessRepository(source)
            success(access.join(room, peer.accessToken, deadline()))
            val messages = InternalCollaborationMatrixRoomMessageRepository(source)
            val key =
                java.util.Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
            fun send(transaction: String) =
                success(
                    messages.send(
                        id,
                        CollaborationRoomMessage(
                            transaction,
                            "Y2lwaGVydGV4dA",
                            key,
                            key,
                            creator.deviceId,
                        ),
                        creator.accessToken,
                        deadline(),
                    )
                )
            val first = send("receipt-first")
            val latest = send("receipt-latest")
            HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { transport ->
                val sync =
                    InternalCollaborationMatrixSyncRepository(
                        transport,
                        collaborationCursorFixture(),
                    )
                fun read(
                    who: CollaborationSyncOwner,
                    bearer: String,
                    before: CollaborationMatrixSyncBatch? = null,
                ): CollaborationMatrixSyncBatch {
                    val scope =
                        success(
                            snapshotCollaborationRoomScope(
                                who.companyId,
                                who.accountId,
                                who.serverName,
                                listOf(room),
                            )
                        )
                    val position =
                        before?.let {
                            CollaborationSyncPosition(
                                scope.digest,
                                it.providerSince,
                                it.providerFilterId,
                            )
                        }
                    return success(sync.sync(who, scope, position, 0, false, bearer, deadline()))
                }
                val creatorBefore = read(owner, creator.accessToken)
                val peerBefore = read(peerOwner, peer.accessToken)
                repeat(2) {
                    success(
                        access.receipt(room, "m.read.private", first, peer.accessToken, deadline())
                    )
                }
                val ownPrivate = read(peerOwner, peer.accessToken, peerBefore)
                assertReceipt(ownPrivate, id, first, "m.read.private", peer.userId)
                val hidden = read(owner, creator.accessToken, creatorBefore)
                assertFalse(
                    JsonMapper.builder()
                        .build()
                        .writeValueAsString(hidden.payload)
                        .contains("m.read.private")
                )
                repeat(2) {
                    success(access.receipt(room, "m.read", first, peer.accessToken, deadline()))
                }
                val public = read(owner, creator.accessToken, hidden)
                assertReceipt(public, id, first, "m.read", peer.userId)
                success(access.receipt(room, "m.read", latest, peer.accessToken, deadline()))
                success(access.receipt(room, "m.read", first, peer.accessToken, deadline()))
                val advanced = read(owner, creator.accessToken, public)
                assertReceipt(advanced, id, latest, "m.read", peer.userId)
                assertFalse(
                    JsonMapper.builder()
                        .build()
                        .writeValueAsString(advanced.payload)
                        .contains("m.read.private")
                )
            }
        }
    }

    private fun assertReceipt(
        batch: CollaborationMatrixSyncBatch,
        roomId: String,
        eventId: String,
        type: String,
        userId: String,
    ) {
        val json = JsonMapper.builder().build()
        val node = json.readTree(json.writeValueAsBytes(batch.payload))
        val receipts = node.path("rooms").path("join").path(roomId).path("ephemeral").path("events")
        assertTrue(receipts.isArray)
        assertTrue(
            receipts.any {
                it.path("type").asString() == "m.receipt" &&
                    it.path("content").path(eventId).path(type).path(userId).isObject
            }
        )
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
