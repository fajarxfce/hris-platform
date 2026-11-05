package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.requests.matrixRoomMessageRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixRoomReplyRequestTest {
    private val json = JsonMapper.builder().build()
    private val content =
        mapOf<String, Any>(
            "algorithm" to "m.megolm.v1.aes-sha2",
            "ciphertext" to "Y2lwaGVydGV4dA",
            "session_id" to "A".repeat(43),
        )

    @Test
    fun legacyAndReplyCiphertextHaveExplicitDistinctShapes() {
        assertNull(
            matrixRoomMessageRequest("plain", json.readTree(json.writeValueAsString(content)))
                .replyToEventId
        )
        val target = "\$" + "x".repeat(254)
        val body =
            content + ("m.relates_to" to mapOf("m.in_reply_to" to mapOf("event_id" to target)))
        val reply = matrixRoomMessageRequest("reply", json.readTree(json.writeValueAsString(body)))
        assertEquals(target, reply.replyToEventId)
        assertEquals("Y2lwaGVydGV4dA", reply.ciphertext)
        assertFalse(reply.toString().contains(target))
    }

    @Test
    fun arbitraryRelationsNestedPayloadsAndPlaintextAreRejected() {
        val invalid: List<Any?> =
            listOf(
                null,
                emptyMap<String, Any>(),
                emptyList<Any>(),
                "\$original",
                mapOf("rel_type" to "m.annotation", "event_id" to "\$original"),
                mapOf("m.in_reply_to" to null),
                mapOf("m.in_reply_to" to emptyMap<String, Any>()),
                mapOf("m.in_reply_to" to mapOf("event_id" to 4)),
                mapOf("m.in_reply_to" to mapOf("event_id" to "\$original", "body" to "private")),
                mapOf("m.in_reply_to" to mapOf("event_id" to "\$original"), "extra" to "private"),
                mapOf("m.in_reply_to" to mapOf("event_id" to "\$" + "x".repeat(255))),
            )
        for (relation in invalid) {
            val body = content + ("m.relates_to" to relation)
            assertThrows(IllegalArgumentException::class.java) {
                matrixRoomMessageRequest("reply", json.readTree(json.writeValueAsString(body)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            matrixRoomMessageRequest(
                "reply",
                json.readTree(json.writeValueAsString(content + ("body" to "private"))),
            )
        }
    }

    @Test
    fun replacementCarriesOnlyItsOpaqueTargetAndCannotMixRelationKindsOrExposeEditedText() {
        val target = "\$" + "x".repeat(254)
        val relation = mapOf("rel_type" to "m.replace", "event_id" to target)
        val request =
            matrixRoomMessageRequest(
                "edit",
                json.readTree(json.writeValueAsString(content + ("m.relates_to" to relation))),
            )
        assertNull(request.replyToEventId)
        assertEquals(target, request.replacesEventId)
        assertFalse(request.toString().contains(target))
        for (invalid in
            listOf(
                relation + ("m.in_reply_to" to mapOf("event_id" to target)),
                relation + ("body" to "edited private text"),
                relation + ("m.new_content" to mapOf("body" to "edited private text")),
                relation + ("event_id" to 42),
                relation + ("rel_type" to null),
                relation + ("event_id" to "\$" + "x".repeat(255)),
                relation - "event_id",
            )) assertThrows(IllegalArgumentException::class.java) {
            matrixRoomMessageRequest(
                "edit",
                json.readTree(json.writeValueAsString(content + ("m.relates_to" to invalid))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            matrixRoomMessageRequest(
                "edit",
                json.readTree(
                    json.writeValueAsString(
                        content +
                            ("m.relates_to" to relation) +
                            ("m.new_content" to mapOf("body" to "private"))
                    )
                ),
            )
        }
    }
}
