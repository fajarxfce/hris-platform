package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixRoomMessageInteropTest {
    @Test
    fun actualCiphertextTransactionDeduplicatesAcrossSameDeviceBearerRotationAndPeerHistory() {
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

            val messages = InternalCollaborationMatrixRoomMessageRepository(source)
            val key =
                java.util.Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
            val message =
                CollaborationRoomMessage(
                    "fixture-transaction",
                    "Y2lwaGVydGV4dA",
                    key,
                    key,
                    creator.deviceId,
                )
            fun send(bearer: String): String {
                val result = messages.send(id, message, bearer, f.clock.instant().plusSeconds(20))
                assertInstanceOf(Result.Success::class.java, result)
                return (result as Result.Success).value
            }
            val first = send(creator.accessToken)
            assertEquals(first, send(creator.accessToken))
            val reply = message.copy(transactionId = "fixture-reply", replyToEventId = first)
            fun sendReply(bearer: String): String {
                val result = messages.send(id, reply, bearer, f.clock.instant().plusSeconds(20))
                assertInstanceOf(Result.Success::class.java, result)
                return (result as Result.Success).value
            }
            val replyId = sendReply(creator.accessToken)
            assertNotEquals(first, replyId)
            assertEquals(replyId, sendReply(creator.accessToken))
            val atRefresh = f.clock.instant()
            val freshResult =
                f.repository.login(
                    f.request.copy(issuedAt = atRefresh, expiresAt = atRefresh.plusSeconds(60))
                )
            assertInstanceOf(Result.Success::class.java, freshResult)
            val fresh = (freshResult as Result.Success).value
            assertNotEquals(creator.accessToken, fresh.accessToken)
            assertEquals(first, send(fresh.accessToken))
            assertEquals(replyId, sendReply(fresh.accessToken))
            assertInstanceOf(
                Result.Success::class.java,
                messages.send(
                    id,
                    message.copy(
                        transactionId = "fixture-maximum",
                        ciphertext = "A".repeat(60_000),
                    ),
                    fresh.accessToken,
                    f.clock.instant().plusSeconds(20),
                ),
            )
            val access = InternalCollaborationMatrixRoomAccessRepository(source)
            assertEquals(
                Result.Success(id),
                access.join(room, peer.accessToken, f.clock.instant().plusSeconds(20)),
            )
            val history =
                InternalCollaborationMatrixRoomHistoryRepository(
                    source,
                    collaborationCursorFixture(),
                )
            val peerOwner = owner.copy(accountId = account, deviceId = device)
            val observed =
                history.messages(
                    peerOwner,
                    room,
                    CollaborationRoomPageRequest(null, null, "b", 100),
                    peer.accessToken,
                    f.clock.instant().plusSeconds(20),
                )
            assertInstanceOf(Result.Success::class.java, observed)
            val events = (observed as Result.Success).value.payload["chunk"] as List<*>
            val received = events.map { it as Map<*, *> }.filter { it["event_id"] == first }
            assertEquals(1, received.size)
            assertEquals(
                message.ciphertext,
                (received.single()["content"] as Map<*, *>)["ciphertext"],
            )
            assertFalse(received.single().containsKey("unsigned"))
            val receivedReplies =
                events.map { it as Map<*, *> }.filter { it["event_id"] == replyId }
            assertEquals(1, receivedReplies.size)
            val replyContent = receivedReplies.single()["content"] as Map<*, *>
            assertEquals(message.ciphertext, replyContent["ciphertext"])
            assertEquals(
                mapOf("m.in_reply_to" to mapOf("event_id" to first)),
                replyContent["m.relates_to"],
            )
            assertFalse(replyContent.containsKey("body"))
        }
    }
}
