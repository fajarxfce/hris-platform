package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixSpaceRoomHistoryInteropTest {
    @Test
    fun actualGroupRoomHistoryTerminatesAndKeepsOriginalEventsAcrossProviderRestart() = run(false)

    @Test fun actualOriginalRoomCursorContinuesUnderGroupOwnershipAfterProviderRestart() = run(true)

    private fun run(legacy: Boolean) {
        MatrixProviderFixture("10m", messageBurst = 100).use { provider ->
            provider.start()
            val f = SpaceRoomHistoryFixture(legacy)
            fun raw() = HttpMatrixClientDataSource(provider.client, provider.config, provider.clock)
            fun deadline() = Instant.now().plusSeconds(20)
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
            val creator = login(f.pair[1], UUID.randomUUID())
            assertEquals(f.owner.matrixUserId, own.userId)
            assertEquals(f.owner.matrixDeviceId, own.deviceId)
            val groupRooms = InternalCollaborationSpaceMatrixRoomRepository(raw(), f.server)
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
                                creator.accessToken,
                                deadline(),
                            )
                    )
                else
                    success(
                        groupRooms.create(
                            CollaborationSpaceMatrixDirectRoom(
                                f.space,
                                f.conversation,
                                f.pair[1],
                                f.pair[0],
                                creator.userId,
                                own.userId,
                            ),
                            creator.accessToken,
                            deadline(),
                        )
                    )
            val roomId = (created as CollaborationMatrixRoomCreation.Created).roomId
            success(groupRooms.join(roomId, own.accessToken, deadline()))
            val bound =
                f.room.copy(
                    intent =
                        f.room.intent.copy(
                            original = f.room.intent.original?.copy(roomId = roomId)
                        ),
                    binding = f.room.binding.copy(roomId = roomId),
                )
            val messages = InternalCollaborationMatrixRoomMessageRepository(raw())
            val sent =
                linkedMapOf<String, Pair<CollaborationMatrixCredential, CollaborationRoomMessage>>()
            for (index in 1..7) {
                val sender = if (index % 2 == 0) own else creator
                val message =
                    CollaborationRoomMessage(
                        "original-history-transaction-$index",
                        Base64.getEncoder()
                            .withoutPadding()
                            .encodeToString("fictional-ciphertext-$index".toByteArray()),
                        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }),
                        deviceId = sender.deviceId,
                    )
                val event = success(messages.send(roomId, message, sender.accessToken, deadline()))
                sent[event] = sender to message
            }
            val crypto = collaborationCursorFixture()
            fun history() = InternalCollaborationSpaceMatrixRoomHistoryRepository(raw(), crypto)
            val request = CollaborationRoomPageRequest(null, null, "b", 3)
            val first =
                if (legacy) {
                    val old =
                        CollaborationSyncOwner(f.company, f.pair[0], f.originalDevice, f.server)
                    val original =
                        CollaborationOwnedRoom(
                            f.company,
                            requireNotNull(bound.intent.original).conversationId,
                            f.pair[0],
                            f.pair[1],
                            f.pair[1],
                            f.server,
                            roomId,
                        )
                    success(
                        InternalCollaborationMatrixRoomHistoryRepository(raw(), crypto)
                            .messages(old, original, request, own.accessToken, deadline())
                    )
                } else
                    success(
                        history().messages(f.owner, bound, request, own.accessToken, deadline())
                    )
            val seen = linkedMapOf<String, Map<*, *>>()
            fun record(page: CollaborationRoomHistory) {
                val events = page.payload["chunk"] as List<*>
                for (value in events) {
                    val event = value as Map<*, *>
                    val id = event["event_id"] as String
                    assertNull(
                        seen.put(id, event),
                        "Provider history must not repeat an event across pages",
                    )
                }
            }
            record(first)
            var cursor = first.payload["end"] as? String
            assertNotNull(cursor)
            provider.restart()
            var pages = 1
            while (cursor != null && pages < 32) {
                val next =
                    success(
                        history()
                            .messages(
                                f.owner,
                                bound,
                                request.copy(from = cursor),
                                own.accessToken,
                                deadline(),
                            )
                    )
                record(next)
                cursor = next.payload["end"] as? String
                pages++
            }
            assertNull(
                cursor,
                "History must terminate within the owned fixture's finite page bound",
            )
            assertTrue(pages > 1)
            for ((id, original) in sent) {
                val observed = requireNotNull(seen[id])
                assertEquals(original.first.userId, observed["sender"])
                assertEquals(roomId, observed["room_id"])
                val content = observed["content"] as Map<*, *>
                assertEquals(original.second.ciphertext, content["ciphertext"])
                assertEquals(original.second.sessionId, content["session_id"])
                assertEquals(original.second.deviceId, content["device_id"])
            }
            val target = sent.keys.elementAt(3)
            val event =
                success(history().event(f.owner, bound, target, own.accessToken, deadline()))
                    .payload
            assertEquals(target, event["event_id"])
            val context =
                success(history().context(f.owner, bound, target, 4, own.accessToken, deadline()))
                    .payload
            assertEquals(target, (context["event"] as Map<*, *>)["event_id"])
            assertTrue(
                (context["events_before"] as List<*>).size +
                    (context["events_after"] as List<*>).size <= 4
            )
            val creation = seen.values.single { it["type"] == "m.room.create" }
            assertEquals(creator.userId, creation["sender"])
            // Opaque provider transport and original namespace proof, not SDK Megolm decryption.
        }
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            value,
            (value as? Result.Failed)?.failure?.code,
        )
        return (value as Result.Success).value
    }
}
