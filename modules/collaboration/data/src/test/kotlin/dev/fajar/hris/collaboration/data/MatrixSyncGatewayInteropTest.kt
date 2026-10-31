package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationRoomScope
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixSyncGatewayInteropTest {
    @Test
    fun actualSyncFiltersEveryRoomBucketHydratesCreationAndReusesOwnedProviderFilter() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val own = f.login()
            val peerAccount = UUID.randomUUID()
            val peerDevice = UUID.randomUUID()
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            accountId = peerAccount,
                            deviceId = peerDevice,
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            val rooms =
                InternalCollaborationMatrixRoomRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    f.config.serverName,
                )
            fun create(): CollaborationOwnedRoom {
                val conversation = UUID.randomUUID()
                val room =
                    ((rooms.create(
                                CollaborationMatrixDirectRoom(
                                    f.request.companyId,
                                    conversation,
                                    f.request.accountId,
                                    peerAccount,
                                ),
                                own.accessToken,
                                f.clock.instant().plusSeconds(20),
                            ) as Result.Success)
                            .value as CollaborationMatrixRoomCreation.Created)
                        .roomId
                return CollaborationOwnedRoom(
                    f.request.companyId,
                    conversation,
                    f.request.accountId,
                    peerAccount,
                    f.request.accountId,
                    f.config.serverName,
                    room,
                )
            }
            val first = create()
            val second = create()
            HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { source ->
                val repository = InternalCollaborationMatrixSyncRepository(source)
                for ((account, device, bearer, bucket) in
                    listOf(
                        listOf(f.request.accountId, f.request.deviceId, own.accessToken, "join"),
                        listOf(peerAccount, peerDevice, peer.accessToken, "invite"),
                    )) {
                    val owner =
                        CollaborationSyncOwner(
                            f.request.companyId,
                            account as UUID,
                            device as UUID,
                            f.config.serverName,
                        )
                    fun scope(values: List<CollaborationOwnedRoom>) =
                        (snapshotCollaborationRoomScope(
                                owner.companyId,
                                owner.accountId,
                                owner.serverName,
                                values,
                            )
                                as Result.Success)
                            .value
                    fun sync(
                        values: List<CollaborationOwnedRoom>,
                        position: CollaborationSyncPosition? = null,
                    ): CollaborationMatrixSyncBatch {
                        val result =
                            repository.sync(
                                owner,
                                scope(values),
                                position,
                                0,
                                false,
                                bearer as String,
                                f.clock.instant().plusSeconds(30),
                            )
                        assertInstanceOf(Result.Success::class.java, result)
                        return (result as Result.Success).value
                    }
                    fun tree(batch: CollaborationMatrixSyncBatch) =
                        f.json.readTree(f.json.writeValueAsBytes(batch.payload))
                    val initial = sync(listOf(first))
                    assertEquals(
                        setOf(first.roomId),
                        tree(initial).path("rooms").path(bucket as String).propertyNames(),
                    )
                    assertFalse(tree(initial).toString().contains(second.roomId))
                    assertFalse(initial.payload.containsKey("next_batch"))
                    val repeated = sync(listOf(first))
                    assertEquals(initial.providerFilterId, repeated.providerFilterId)
                    val incremental =
                        sync(
                            listOf(first),
                            CollaborationSyncPosition(
                                scope(listOf(first)).digest,
                                initial.providerSince,
                                initial.providerFilterId,
                            ),
                        )
                    assertEquals(initial.providerFilterId, incremental.providerFilterId)
                    val expanded = sync(listOf(first, second))
                    assertEquals(initial.providerFilterId, expanded.providerFilterId)
                    assertEquals(
                        setOf(first.roomId, second.roomId),
                        tree(expanded).path("rooms").path(bucket).propertyNames(),
                    )
                    if (bucket == "join") {
                        val events =
                            tree(initial)
                                .path("rooms")
                                .path("join")
                                .path(first.roomId)
                                .path("state")
                                .path("events")
                        val completeState =
                            events.toList() +
                                tree(initial)
                                    .path("rooms")
                                    .path("join")
                                    .path(first.roomId)
                                    .path("timeline")
                                    .path("events")
                                    .toList()
                        val creation =
                            completeState.single { it.path("type").asString() == "m.room.create" }
                        assertEquals("12", creation.path("content").path("room_version").asString())
                        assertTrue(creation.path("event_id").asString().startsWith('$'))
                    }
                }
            }
        }
    }
}
