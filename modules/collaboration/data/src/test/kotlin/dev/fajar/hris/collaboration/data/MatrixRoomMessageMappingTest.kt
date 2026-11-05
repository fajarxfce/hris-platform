package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.MatrixRoomMessageDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomMessageRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixRoomMessageMappingTest {
    @Test
    fun receiptMustBeOneEventIdAndOnlyCiphertextFieldsReachTheFixedProviderOperation() {
        val json = JsonMapper.builder().build()
        var response = mapOf<String, Any>("event_id" to "\$fixture")
        var observed: Map<String, Any>? = null
        val repository =
            InternalCollaborationMatrixRoomMessageRepository(
                object : MatrixRoomMessageDataSource {
                    override fun sendRoomMessage(
                        roomId: String,
                        transactionId: String,
                        content: Map<String, Any>,
                        token: String,
                        deadline: Instant,
                    ): JsonNode {
                        assertEquals("!owned", roomId)
                        assertEquals("fixed-txn", transactionId)
                        observed = content
                        require(content["algorithm"] == "m.megolm.v1.aes-sha2")
                        require(content["ciphertext"] == "Y2lwaGVydGV4dA")
                        return json.readTree(json.writeValueAsBytes(response))
                    }
                }
            )
        val message = CollaborationRoomMessage("fixed-txn", "Y2lwaGVydGV4dA", "A".repeat(43))
        fun send() = repository.send("!owned", message, "fixture", Instant.now().plusSeconds(5))
        assertEquals(Result.Success("\$fixture"), send())
        assertEquals(setOf("algorithm", "ciphertext", "session_id"), requireNotNull(observed).keys)
        val reply = message.copy(replyToEventId = "\$original")
        assertEquals(
            Result.Success("\$fixture"),
            repository.send("!owned", reply, "fixture", Instant.now().plusSeconds(5)),
        )
        assertEquals(
            setOf("algorithm", "ciphertext", "session_id", "m.relates_to"),
            requireNotNull(observed).keys,
        )
        assertEquals(
            mapOf("m.in_reply_to" to mapOf("event_id" to "\$original")),
            requireNotNull(observed)["m.relates_to"],
        )
        assertEquals(
            Result.Success("\$fixture"),
            repository.send(
                "!owned",
                message.copy(replacesEventId = "\$original"),
                "fixture",
                Instant.now().plusSeconds(5),
            ),
        )
        assertEquals(
            setOf("algorithm", "ciphertext", "session_id", "m.relates_to"),
            requireNotNull(observed).keys,
        )
        assertEquals(
            mapOf("rel_type" to "m.replace", "event_id" to "\$original"),
            requireNotNull(observed)["m.relates_to"],
        )
        for (invalid in
            listOf(
                emptyMap(),
                mapOf("event_id" to "not-an-event"),
                mapOf("event_id" to "\$fixture", "access_token" to "private"),
                mapOf("event_id" to "\$bad\n"),
                mapOf("event_id" to 123),
            )) {
            response = invalid
            assertEquals(
                "collaboration_provider_invalid_response",
                (send() as Result.Failed).failure.code,
            )
        }
    }
}
