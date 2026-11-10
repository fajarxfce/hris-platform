package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.MatrixResponseException
import dev.fajar.hris.collaboration.data.matrix.mapCollaborationDirectRoomEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixRoomRedactionMappingTest {
    private val json = JsonMapper.builder().build()
    private val own = "@sender:test"
    private val peer = "@peer:test"
    private val room = "!room:test"
    private val target = "\$original"
    private val redaction =
        mapOf(
            "type" to "m.room.redaction",
            "sender" to own,
            "event_id" to "\$redaction",
            "origin_server_ts" to 2L,
            "content" to mapOf("redacts" to target),
        )
    private val original =
        mapOf(
            "type" to "m.room.encrypted",
            "sender" to peer,
            "event_id" to target,
            "room_id" to room,
            "origin_server_ts" to 1L,
            "content" to emptyMap<String, Any>(),
            "unsigned" to mapOf("redacted_because" to redaction),
        )

    private fun mapped(value: Map<String, Any>) =
        requireNotNull(
            mapCollaborationDirectRoomEvent(
                json.readTree(json.writeValueAsBytes(value)),
                setOf(own, peer),
                own,
                room,
                own,
            )
        )

    @Test
    fun redactedEncryptedTargetKeepsOriginalAuthorAndMatchedRedaction() {
        val value = mapped(original)
        assertEquals("m.room.encrypted", value["type"])
        assertEquals(peer, value["sender"])
        assertEquals(target, value["event_id"])
        assertEquals(1L, value["origin_server_ts"])
        assertEquals(emptyMap<String, Any>(), value["content"])
        assertEquals(mapOf("redacted_because" to redaction), value["unsigned"])
    }

    @Test
    fun onlyTheOriginalSenderKeepsItsTransactionAlongsideRedactionMetadata() {
        for (sender in listOf(own, peer)) {
            val value =
                mapped(
                    original +
                        mapOf(
                            "sender" to sender,
                            "unsigned" to
                                mapOf(
                                    "transaction_id" to "original-transaction",
                                    "redacted_because" to redaction,
                                    "private" to "omit",
                                ),
                        )
                )
            val unsigned = value["unsigned"] as Map<*, *>
            assertEquals(redaction, unsigned["redacted_because"])
            assertEquals(sender == own, "transaction_id" in unsigned)
            assertFalse("private" in unsigned)
        }
    }

    @Test
    fun redactionMetadataMustMatchTheTargetAndRetainedRoomParticipants() {
        val invalid =
            listOf(
                redaction + ("content" to mapOf("redacts" to "\$another")),
                redaction + ("content" to emptyMap<String, Any>()),
                redaction + ("redacts" to "\$another"),
                redaction + ("event_id" to target),
                redaction + ("event_id" to "invalid"),
                redaction + ("sender" to "@foreign:test"),
                redaction + ("room_id" to "!foreign:test"),
                redaction + ("origin_server_ts" to -1),
                redaction + ("origin_server_ts" to "2"),
                redaction + ("type" to "m.room.message"),
            )
        for (because in invalid) {
            assertThrows(MatrixResponseException::class.java) {
                mapped(original + ("unsigned" to mapOf("redacted_because" to because)))
            }
        }
    }

    @Test
    fun emptyEncryptedContentWithoutValidRedactionCannotBecomeATombstone() {
        for (unsigned in listOf(emptyMap<String, Any>(), mapOf("redacted_because" to "invalid"))) {
            assertThrows(MatrixResponseException::class.java) {
                mapped(original + ("unsigned" to unsigned))
            }
        }
        assertThrows(MatrixResponseException::class.java) { mapped(original - "unsigned") }
    }

    @Test
    fun redactedEncryptedContentCannotRetainCiphertext() {
        assertThrows(MatrixResponseException::class.java) {
            mapped(
                original +
                    ("content" to
                        mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque"))
            )
        }
    }

    @Test
    fun originalRoomAndAuthorAdmissionAreStillRequired() {
        for (value in
            listOf(
                original + ("sender" to "@foreign:test"),
                original + ("room_id" to "!foreign:test"),
                original + ("type" to "m.room.message"),
            )) assertThrows(MatrixResponseException::class.java) { mapped(value) }
    }

    @Test
    fun nestedPrivateMetadataIsNotForwardedOrRecursivelyExpanded() {
        val withPrivate =
            redaction +
                mapOf(
                    "unsigned" to mapOf("redacted_because" to redaction, "private" to "omit"),
                    "unrelated" to "omit",
                )
        val value = mapped(original + ("unsigned" to mapOf("redacted_because" to withPrivate)))
        assertEquals(mapOf("redacted_because" to redaction), value["unsigned"])
    }

    @Test
    fun legacyAndModernRedactionTargetsRemainExplicitAndCannotDisagree() {
        val legacy = redaction + mapOf("content" to emptyMap<String, Any>(), "redacts" to target)
        for (event in listOf(redaction, legacy, redaction + ("redacts" to target))) {
            assertEquals(event, mapped(event))
        }
        for (invalid in
            listOf(
                redaction + ("redacts" to "\$other"),
                redaction + ("content" to mapOf("redacts" to "\$redaction")),
                legacy - "redacts",
            )) assertThrows(MatrixResponseException::class.java) { mapped(invalid) }
    }

    @Test
    fun standaloneRedactionRetainsOnlyItsOwnTransaction() {
        for (sender in listOf(own, peer)) {
            val value =
                mapped(
                    redaction +
                        mapOf(
                            "sender" to sender,
                            "unsigned" to
                                mapOf(
                                    "transaction_id" to "redaction-transaction",
                                    "private" to "omit",
                                ),
                        )
                )
            assertEquals(redaction["content"], value["content"])
            if (sender == own)
                assertEquals(mapOf("transaction_id" to "redaction-transaction"), value["unsigned"])
            else assertFalse("unsigned" in value)
        }
    }

    @Test
    fun unchangedEncryptedContentAndUnknownExtensionArePreserved() {
        val content =
            mapOf(
                "algorithm" to "m.megolm.v1.aes-sha2",
                "ciphertext" to "opaque",
                "org.hris.extension" to mapOf("marker" to "kept"),
            )
        assertEquals(content, mapped((original - "unsigned") + ("content" to content))["content"])
    }
}
