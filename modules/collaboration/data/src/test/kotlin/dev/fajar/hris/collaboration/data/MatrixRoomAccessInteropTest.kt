package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationRoomScope
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixRoomAccessInteropTest {
    @Test
    fun actualJoinIsRepeatableAndMembersAtSyncPositionDoNotImportLaterMembership() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val creator = f.login()
            val account = UUID.randomUUID()
            val device = UUID.randomUUID()
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            accountId = account,
                            deviceId = device,
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            val source = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val conversation = UUID.randomUUID()
            val id =
                ((InternalCollaborationMatrixRoomRepository(source, f.config.serverName)
                            .create(
                                CollaborationMatrixDirectRoom(
                                    f.request.companyId,
                                    conversation,
                                    f.request.accountId,
                                    account,
                                ),
                                creator.accessToken,
                                f.clock.instant().plusSeconds(20),
                            ) as Result.Success)
                        .value as CollaborationMatrixRoomCreation.Created)
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
            val access = InternalCollaborationMatrixRoomAccessRepository(source)
            fun deadline() = f.clock.instant().plusSeconds(20)
            val state =
                (access.state(owner, room, creator.accessToken, deadline()) as Result.Success).value
            assertTrue(state.events.any { it["type"] == "m.room.create" })
            assertTrue(state.events.any { it["type"] == "m.room.encryption" })
            assertTrue(state.events.all { it["room_id"] == id })
            fun membership(position: String?): String {
                val result = access.members(owner, room, position, creator.accessToken, deadline())
                assertInstanceOf(Result.Success::class.java, result)
                val events = (result as Result.Success).value.events
                assertEquals(2, events.size)
                val event = events.single { it["state_key"] == peer.userId }
                return (event["content"] as Map<*, *>)["membership"] as String
            }
            assertEquals("invite", membership(null))
            val position =
                HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { sync ->
                    val scope =
                        (snapshotCollaborationRoomScope(
                                owner.companyId,
                                owner.accountId,
                                owner.serverName,
                                listOf(room),
                            )
                                as Result.Success)
                            .value
                    (InternalCollaborationMatrixSyncRepository(sync, collaborationCursorFixture())
                            .sync(owner, scope, null, 0, false, creator.accessToken, deadline())
                            as Result.Success)
                        .value
                        .providerSince
                }
            repeat(2) {
                assertEquals(Result.Success(id), access.join(room, peer.accessToken, deadline()))
            }
            assertEquals("join", membership(null))
            assertEquals("invite", membership(position))
            val peerOwner = owner.copy(accountId = account, deviceId = device)
            val joinedState = access.state(peerOwner, room, peer.accessToken, deadline())
            assertInstanceOf(Result.Success::class.java, joinedState)
            assertTrue(
                (joinedState as Result.Success).value.events.any { it["type"] == "m.room.create" }
            )
        }
    }
}
